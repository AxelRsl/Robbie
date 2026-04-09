package com.robbie.platform.react.modules;

import android.widget.SeekBar;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;

public class RNCSliderManager extends SimpleViewManager<SeekBar> {

    public static final String REACT_CLASS = "RNCSlider";

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected SeekBar createViewInstance(ThemedReactContext reactContext) {
        SeekBar seekBar = new SeekBar(reactContext);
        seekBar.setMax(100);
        return seekBar;
    }

    @ReactProp(name = "value", defaultFloat = 0f)
    public void setValue(SeekBar view, float value) {
        view.setProgress((int) value);
    }

    @ReactProp(name = "minimumValue", defaultFloat = 0f)
    public void setMinimumValue(SeekBar view, float min) {
        // SeekBar nativo no soporta min, solo max
    }

    @ReactProp(name = "maximumValue", defaultFloat = 100f)
    public void setMaximumValue(SeekBar view, float max) {
        view.setMax((int) max);
    }

    @ReactProp(name = "disabled", defaultBoolean = false)
    public void setDisabled(SeekBar view, boolean disabled) {
        view.setEnabled(!disabled);
    }
}
