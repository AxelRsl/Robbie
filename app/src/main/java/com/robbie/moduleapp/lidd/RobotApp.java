package com.robbie.moduleapp.lidd;

import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import com.robbie.BuildConfig;
import com.robbie.base.config.RemoteConfigManager;
import com.robbie.platform.react.PlatformReactNativeHost;
import com.robbie.platform.retail.RobbieConfig;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactNativeHost;
import com.ainirobot.agent.AppAgent;
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
        Log.i(TAG, "Iniciando xiabao_lidd v" + BuildConfig.VERSION_NAME);
        Log.i(TAG, "========================================");

        // 1. Configuracion remota (siempre primero)
        initializeRemoteConfig();

        // 2. Inicializar Agent SDK (AgentOS) con persona configurable
        initializeAgentOS();

        Log.i(TAG, "xiabao_lidd inicializado correctamente");
    }

    private void initializeRemoteConfig() {
        RemoteConfigManager configManager = RemoteConfigManager.getInstance(this);
        configManager.logCurrentConfig();
        Log.i(TAG, "Region activa: " + configManager.getActiveRegion());
    }

    private void initializeAgentOS() {
        String configApiUrl = getSharedPreferences("robbie_prefs", MODE_PRIVATE)
                .getString("config_api_url", DEFAULT_CONFIG_API);

        RobbieConfig.loadFromApi(configApiUrl, new RobbieConfig.ConfigCallback() {
            @Override
            public void onSuccess(RobbieConfig config) {
                robbieConfig = config;
                createAppAgent(config);
                Log.i(TAG, "Config loaded from API - " + config.getProducts().size() + " products");
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Failed to load config from API: " + error + ", using defaults");
                robbieConfig = new RobbieConfig();
                createAppAgent(robbieConfig);
            }
        });
    }

    private void createAppAgent(RobbieConfig config) {
        try {
            appAgent = new AppAgent(this) {
                @Override
                public void onCreate() {
                    setPersona(config.getPersona());
                    setObjective(config.getObjective());
                    registerAction(Actions.SAY);
                    Log.i(TAG, "AppAgent creado con configuracion dinamica");
                }

                @Override
                public boolean onExecuteAction(Action action, Bundle params) {
                    Log.i(TAG, "AppAgent.onExecuteAction: " + action.getName());
                    return false;
                }
            };
            Log.i(TAG, "AgentOS inicializado con AppAgent configurable");
        } catch (Exception e) {
            Log.e(TAG, "Error inicializando AgentOS: " + e.getMessage(), e);
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
