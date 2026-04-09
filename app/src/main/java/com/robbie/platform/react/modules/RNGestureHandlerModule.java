package com.robbie.platform.react.modules;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class RNGestureHandlerModule extends ReactContextBaseJavaModule {

    public RNGestureHandlerModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "RNGestureHandlerModule";
    }

    @ReactMethod
    public void attachGestureHandler(int tag, int handlerTag) {
    }

    @ReactMethod
    public void updateGestureHandler(int handlerTag, Object config) {
    }

    @ReactMethod
    public void dropGestureHandler(int handlerTag) {
    }

    @ReactMethod
    public void handleSetJSResponder(int tag, boolean blockNativeResponder) {
    }

    @ReactMethod
    public void handleClearJSResponder() {
    }
}
