package org.dd.camerapreview;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;

import org.dd.camerapreview.draggableruler.DraggableRulerView;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RulesManager {

    private int isoValue = 100;
    private int shutterSpeed = 1; // in seconds
    private int focusValue = 50;
    private int exposureValue = 0;
    private int intervalValue = 60; // in seconds

    private Activity mainActivity;
    private Map<Integer, List<String>> paramters = null;
    private Map<Integer, Boolean> imageButtonsVisibility = new HashMap<>();
    Map<Integer, String> currentCameraConfigs;

    public RulesManager(Activity activity, Map<Integer, List<String>> paramters, Map<Integer, String> currentCameraConfigs) {
        this.mainActivity = activity;
        this.paramters = paramters;
        this.currentCameraConfigs = currentCameraConfigs;

        setupIconAndDraggableRuler(R.id.isoButton, R.id.ruler_iso, R.id.isoMeterViewContainer, R.id.isoLabel,
                paramters.get(Camera2Manager.SENSITIVITY_RANGE), Camera2Manager.SENSITIVITY_RANGE, "ISO", currentCameraConfigs.get(Camera2Manager.SENSITIVITY_RANGE));
        setupIconAndDraggableRuler(R.id.shutterButton, R.id.ruler_shutter, R.id.shutterMeterViewContainer, R.id.shutterLabel,
                paramters.get(Camera2Manager.EXPOSURE_TIME_RANGE), Camera2Manager.EXPOSURE_TIME_RANGE, "Shutter", currentCameraConfigs.get(Camera2Manager.EXPOSURE_TIME_RANGE));
        setupIconAndDraggableRuler(R.id.focusButton, R.id.ruler_focus, R.id.focusMeterViewContainer, R.id.focusLabel,
                paramters.get(Camera2Manager.LENS_AVAILABLE_FOCAL_LENGTHS), Camera2Manager.LENS_AVAILABLE_FOCAL_LENGTHS, "Focus", currentCameraConfigs.get(Camera2Manager.LENS_AVAILABLE_FOCAL_LENGTHS));
        setupIconAndDraggableRuler(R.id.exposureButton, R.id.ruler_exposure, R.id.exposureMeterViewContainer, R.id.exposureLabel,
                paramters.get(Camera2Manager.EXPOSURE_TIME_RANGE), Camera2Manager.EXPOSURE_TIME_RANGE, "Exposure", currentCameraConfigs.get(Camera2Manager.EXPOSURE_TIME_RANGE));
        setupIconAndDraggableRuler(R.id.intervalButton, R.id.ruler_interval, R.id.intervalMeterViewContainer, R.id.intervalLabel,
                paramters.get(Camera2Manager.SENSOR_MAX_FRAME_DURATION), Camera2Manager.SENSOR_MAX_FRAME_DURATION, "Interval", currentCameraConfigs.get(Camera2Manager.SENSOR_MAX_FRAME_DURATION));
        setupIconAndDraggableRuler(R.id.durationButton, R.id.ruler_duration, R.id.durationMeterViewContainer, R.id.durationLabel,
                paramters.get(Camera2Manager.DURATION), Camera2Manager.DURATION, "Movie duration", currentCameraConfigs.get(Camera2Manager.DURATION));
    }

    private void setupIconAndDraggableRuler(int buttonId, int draggableMeterId, int meterViewContainerId, int labelId, List<String> values, int cameraConf, String label, String currentConfVal) {
        AppCompatImageButton button = mainActivity.findViewById(buttonId);
        TextView labelView = mainActivity.findViewById(labelId);
        labelView.setText(label);
        FrameLayout meterViewContainer = mainActivity.findViewById(meterViewContainerId);
        DraggableRulerView meterView = mainActivity.findViewById(draggableMeterId);
        meterView.setCustomValues(values);
        meterView.setInitialValue(currentConfVal);
        meterView.setOnRulerPositionChangeListener(new DraggableRulerView.OnRulerPositionChangeListener() {
            @Override
            public void onPositionChanged(String value) {
                Log.d("RulesManager", "onPositionChanged: " + value + " on " + label);
                Camera2Manager.getInstance(mainActivity).updatePreview(cameraConf, value);
            }
        });

        // Initialize the visibility state for this button if it's not already in the map
        if (!imageButtonsVisibility.containsKey(buttonId)) {
            imageButtonsVisibility.put(buttonId, false);
        }

        button.setOnClickListener(v -> {
            // Toggle the visibility state for this button
            boolean currentVisibility = imageButtonsVisibility.get(buttonId);
            imageButtonsVisibility.put(buttonId, !currentVisibility);

            // Show/hide the seekbar associated with this button
            meterViewContainer.setVisibility(imageButtonsVisibility.get(buttonId) ? View.VISIBLE : View.GONE);
            meterViewContainer.setBackgroundResource(R.drawable.active_border);
            labelView.setVisibility(imageButtonsVisibility.get(buttonId) ? View.VISIBLE : View.GONE);
            meterView.setVisibility(imageButtonsVisibility.get(buttonId) ? View.VISIBLE : View.GONE);

            // Check if all values are false and hide the container if they are
            for (Integer key : imageButtonsVisibility.keySet()) {
                if (key != buttonId) {
                    FrameLayout otherContainer = mainActivity.findViewById(getContainerIdForButton(key));
                    otherContainer.setBackgroundResource(R.drawable.ruler_border); // Rimuovi evidenziazione
                }
            }
        });
    }

    private int getContainerIdForButton(int buttonId) {
        if (buttonId == R.id.isoButton) {
            return R.id.isoMeterViewContainer;
        } else if (buttonId == R.id.shutterButton) {
            return R.id.shutterMeterViewContainer;
        } else if (buttonId == R.id.focusButton) {
            return R.id.focusMeterViewContainer;
        } else if (buttonId == R.id.exposureButton) {
            return R.id.exposureMeterViewContainer;
        } else if (buttonId == R.id.intervalButton) {
            return R.id.intervalMeterViewContainer;
        } else if (buttonId == R.id.durationButton) {
            return R.id.intervalMeterViewContainer;
        } else {
            return -1;
        }
    }

    interface ValueChangeListener {
        void onValueChange(int value);
    }

    private boolean allValuesAreFalse(Map<Integer, Boolean> map) {
        Collection<Boolean> values = map.values();
        for (Boolean value : values) {
            if (value) {
                return false; // Found a true value, so not all are false
            }
        }
        return true; // All values were false
    }

    public void hideAllRulers() {
        for (Map.Entry<Integer, Boolean> entry : imageButtonsVisibility.entrySet()) {
            int buttonId = entry.getKey();
            imageButtonsVisibility.put(buttonId, false);

            // Dichiarazione delle variabili
            int meterViewContainerId = 0;
            int draggableMeterId = 0;
            int labelId = 0;

            // Identifica i relativi elementi con if-else
            if (buttonId == R.id.isoButton) {
                meterViewContainerId = R.id.isoMeterViewContainer;
                draggableMeterId = R.id.ruler_iso;
                labelId = R.id.isoLabel;
            } else if (buttonId == R.id.shutterButton) {
                meterViewContainerId = R.id.shutterMeterViewContainer;
                draggableMeterId = R.id.ruler_shutter;
                labelId = R.id.shutterLabel;
            } else if (buttonId == R.id.focusButton) {
                meterViewContainerId = R.id.focusMeterViewContainer;
                draggableMeterId = R.id.ruler_focus;
                labelId = R.id.focusLabel;
            } else if (buttonId == R.id.exposureButton) {
                meterViewContainerId = R.id.exposureMeterViewContainer;
                draggableMeterId = R.id.ruler_exposure;
                labelId = R.id.exposureLabel;
            } else if (buttonId == R.id.intervalButton) {
                meterViewContainerId = R.id.intervalMeterViewContainer;
                draggableMeterId = R.id.ruler_interval;
                labelId = R.id.intervalLabel;
            } else if (buttonId == R.id.durationButton) {
                meterViewContainerId = R.id.durationMeterViewContainer;
                draggableMeterId = R.id.ruler_duration;
                labelId = R.id.intervalLabel;
            } else {
                continue; // Se non corrisponde, passa al prossimo elemento
            }

            // Nascondi i componenti associati
            FrameLayout meterViewContainer = mainActivity.findViewById(meterViewContainerId);
            DraggableRulerView meterView = mainActivity.findViewById(draggableMeterId);
            TextView labelView = mainActivity.findViewById(labelId);

            if (meterViewContainer != null) {
                meterViewContainer.setVisibility(View.GONE);
            }
            if (meterView != null) {
                meterView.setVisibility(View.GONE);
            }
            if (labelView != null) {
                labelView.setVisibility(View.GONE);
            }
        }
    }
}
