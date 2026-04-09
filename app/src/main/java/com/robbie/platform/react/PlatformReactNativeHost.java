package com.robbie.platform.react;

import android.app.Application;
import android.util.Log;

import com.robbie.BuildConfig;
import com.robbie.platform.react.modules.RNCSliderPackage;
import com.robbie.platform.react.modules.RNFSPackage;
import com.robbie.platform.react.modules.RNGestureHandlerPackage;
import com.robbie.platform.react.modules.AgentModule;
import com.robbie.platform.react.modules.RobotConfigModule;
import com.robbie.platform.react.modules.RobotSkillModule;
import com.robbie.platform.react.modules.CloudApiModule;
import com.robbie.platform.react.modules.ProductsModule;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.shell.MainReactPackage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PlatformReactNativeHost - Host principal del motor React Native.
 *
 * Gestiona el ciclo de vida del runtime React Native, la carga de bundles JS
 * y la comunicación entre código nativo y JavaScript.
 * Equivalente al host del xiabao original.
 */
public class PlatformReactNativeHost extends ReactNativeHost {

    private static final String TAG = "PlatformRNHost";

    public PlatformReactNativeHost(Application application) {
        super(application);
    }

    @Override
    public boolean getUseDeveloperSupport() {
        return BuildConfig.DEBUG;
    }

    @Override
    protected List<ReactPackage> getPackages() {
        List<ReactPackage> packages = new ArrayList<>();
        packages.add(new MainReactPackage());
        
        // Módulos nativos personalizados para compatibilidad con bundle 0.59.10
        packages.add(new RNCSliderPackage());
        packages.add(new RNFSPackage());
        packages.add(new RNGestureHandlerPackage());
        
        // Módulos nativos para aplicación retail
        packages.add(new ReactPackage() {
            @Override
            public List<com.facebook.react.bridge.NativeModule> createNativeModules(
                    com.facebook.react.bridge.ReactApplicationContext reactContext) {
                List<com.facebook.react.bridge.NativeModule> modules = new ArrayList<>();
                modules.add(new RobotConfigModule(reactContext));
                modules.add(new RobotSkillModule(reactContext));
                modules.add(new CloudApiModule(reactContext));
                modules.add(new ProductsModule(reactContext));
                modules.add(new AgentModule(reactContext));
                return modules;
            }
            
            @Override
            public List<com.facebook.react.uimanager.ViewManager> createViewManagers(
                    com.facebook.react.bridge.ReactApplicationContext reactContext) {
                return new ArrayList<>();
            }
        });
        
        return packages;
    }

    @Override
    protected String getJSMainModuleName() {
        return "index";
    }

    @Override
    protected String getBundleAssetName() {
        return "platform.android.bundle";
    }

    @Override
    protected String getJSBundleFile() {
        // Ruta al Core Bundle descargado
        String bundlePath = getApplication().getFilesDir()
                .getAbsolutePath() + "/core_bundle/platform.android.bundle";
        
        File bundleFile = new File(bundlePath);
        if (bundleFile.exists()) {
            Log.d(TAG, "Usando bundle descargado: " + bundlePath);
            return bundlePath;
        } else {
            Log.d(TAG, "Bundle no encontrado, usando assets o modo dev");
            // Retornar null para que React Native use el bundle de assets o modo dev
            return null;
        }
    }
}
