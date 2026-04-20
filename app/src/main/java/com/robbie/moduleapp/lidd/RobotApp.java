package com.robbie.moduleapp.lidd;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.robbie.BuildConfig;
import com.robbie.base.config.RemoteConfigManager;
import com.robbie.data.server.RobbieApiService;
import com.robbie.platform.react.PlatformReactNativeHost;
import com.robbie.platform.retail.RobbieConfig;
import com.robbie.platform.retail.Product;
import com.robbie.core.hardware.SensorManager;
import com.robbie.core.hardware.LedController;
import com.robbie.core.hardware.ActuatorManager;
import com.robbie.core.navigation.NavigationManager;
import com.robbie.core.modes.ModeManager;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactNativeHost;
import com.ainirobot.agent.AppAgent;
import com.ainirobot.agent.AgentCore;
import com.ainirobot.agent.action.Action;
import com.ainirobot.agent.action.Actions;

/**
 * Application principal de xiabao_lidd.
 *
 * Inicializa:
 * - RemoteConfigManager (configuracion remota)
 * - AppAgent (AgentOS SDK - persona + objetivo configurables)
 * - React Native host
 *
 * Las APIs de robot (RobotApi, PersonApi, AgentCore) se usan
 * directamente desde las Activities, como en ROBBI-GNC.
 */
public class RobotApp extends Application implements ReactApplication {

    private static final String TAG = "RobotApp";
    private static final String DEFAULT_CONFIG_API = "https://your-api-url.com/robbie/config";
    private static RobotApp sInstance;
    private final ReactNativeHost mReactNativeHost = new PlatformReactNativeHost(this);
    private RobbieConfig robbieConfig;
    private AppAgent appAgent;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        Log.i(TAG, "========================================");
        Log.i(TAG, "Iniciando robbie" + BuildConfig.VERSION_NAME);
        Log.i(TAG, "========================================");

        // 1. Configuracion remota (siempre primero)
        initializeRemoteConfig();

        // 2. Iniciar servidor API local para administración remota
        startApiServer();

        // 3. Inicializar subsistemas de hardware y navegacion
        initializeCoreManagers();

        // 4. Inicializar Agent SDK (AgentOS) con persona configurable
        initializeAgentOS();

        Log.i(TAG, "robbie inicializado correctamente");
    }

    private void initializeRemoteConfig() {
        RemoteConfigManager configManager = RemoteConfigManager.getInstance(this);
        configManager.logCurrentConfig();
        Log.i(TAG, "Region activa: " + configManager.getActiveRegion());
    }

    private void startApiServer() {
        try {
            Intent serviceIntent = new Intent(this, RobbieApiService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.i(TAG, "API Server iniciado - URL: " + RobbieApiService.getServerUrl(this));
        } catch (Exception e) {
            Log.e(TAG, "Error iniciando API Server", e);
        }
    }

    private void initializeCoreManagers() {
        try {
            SensorManager.getInstance(this).initialize();
            LedController.getInstance();
            ActuatorManager.getInstance();
            NavigationManager.getInstance(this);
            ModeManager.getInstance(this);
            Log.i(TAG, "Core managers initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing core managers", e);
        }
    }

    private void initializeAgentOS() {
        // Inicializar robbieConfig con defaults INMEDIATAMENTE para que nunca sea null
        // cuando EveActivity lea los productos en onCreate/onStart
        robbieConfig = new RobbieConfig();

        // Crear AppAgent inmediatamente (patron ROBBI-GNC: GncApplication)
        // Sin AppAgent, el AgentOS no muestra la barra de transcripcion del sistema
        // y las Actions no se despachan correctamente
        createAppAgent(robbieConfig);

        // Cargar config real desde API (async) y actualizar productos
        String configApiUrl = BuildConfig.ROBBIE_CONFIG_API_URL + "/robbie/config";
        Log.i(TAG, "Usando API URL desde BuildConfig: " + configApiUrl);

        RobbieConfig.loadFromApi(configApiUrl, new RobbieConfig.ConfigCallback() {
            @Override
            public void onSuccess(RobbieConfig config) {
                robbieConfig = config;
                uploadProductsToAgent(config);
                Log.i(TAG, "Config loaded from API - " + config.getProducts().size() + " products");
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Failed to load config from API: " + error + ", using defaults");
                // robbieConfig ya tiene defaults, no necesita reasignarse
            }
        });
    }

    /**
     * Crea AppAgent con persona y objetivo (patron ROBBI-GNC: GncApplication).
     * El AppAgent registra la app con el AgentOS, lo cual:
     * - Habilita la barra de transcripcion del sistema (ASR/TTS en pantalla)
     * - Permite que las Actions del PageAgent reciban despachos correctamente
     */
    private void createAppAgent(RobbieConfig config) {
        appAgent = new AppAgent(this) {
            @Override
            public void onCreate() {
                setPersona(config.getPersona());
                setObjective(config.getObjective());
                registerAction(Actions.SAY);
                Log.d(TAG, "AppAgent created with persona: " + config.getStoreName());
            }

            @Override
            public boolean onExecuteAction(Action action, Bundle params) {
                return false;
            }
        };
    }

    private void uploadProductsToAgent(RobbieConfig config) {
        try {
            if (config.getProducts() == null || config.getProducts().isEmpty()) {
                Log.w(TAG, "No hay productos para subir al agente");
                return;
            }

            // Limitar a 20 productos para no saturar al agente (patrón ROBBI-GNC)
            int maxProducts = Math.min(config.getProducts().size(), 20);
            StringBuilder productsInfo = new StringBuilder();
            productsInfo.append("Catálogo ").append(config.getStoreName())
                       .append(" - ").append(config.getProducts().size()).append(" productos:\n");
            
            for (int i = 0; i < maxProducts; i++) {
                Product product = config.getProducts().get(i);
                productsInfo.append("- ").append(product.getName());
                
                if (product.getPrice() > 0) {
                    productsInfo.append(" ($").append(String.format("%.0f", product.getPrice())).append(")");
                }
                
                if (product.getCategory() != null) {
                    productsInfo.append(" [").append(product.getCategory()).append("]");
                }
                
                productsInfo.append("\n");
            }
            
            if (config.getProducts().size() > maxProducts) {
                productsInfo.append("... y ").append(config.getProducts().size() - maxProducts)
                           .append(" productos más\n");
            }
            
            // Subir información al agente
            AgentCore.INSTANCE.uploadInterfaceInfo(productsInfo.toString());
            Log.i(TAG, "Información de " + maxProducts + "/" + config.getProducts().size() 
                     + " productos subida al agente");
            
        } catch (Exception e) {
            Log.e(TAG, "Error subiendo productos al agente", e);
        }
    }

    public RobbieConfig getRobbieConfig() {
        return robbieConfig;
    }

    public AppAgent getAppAgent() {
        return appAgent;
    }

    public static RobotApp getInstance() {
        return sInstance;
    }

    @Override
    public ReactNativeHost getReactNativeHost() {
        return mReactNativeHost;
    }
}
