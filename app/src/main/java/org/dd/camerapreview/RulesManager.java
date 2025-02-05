package org.dd.camerapreview;

import android.app.Activity;
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
    private Map<Integer, List> paramters = null;
    private Map<Integer, Boolean> imageButtonsVisibility = new HashMap<>();

    public RulesManager(Activity activity, Map<Integer, List> paramters) {
        this.mainActivity = activity;
        this.paramters = paramters;

        setupIconAndDraggableRuler(R.id.isoButton, R.id.ruler_iso, R.id.isoMeterViewContainer, R.id.isoLabel, paramters.get(Camera2Manager.SENSITIVITY_RANGE), value -> isoValue = value, "ISO");
        setupIconAndDraggableRuler(R.id.shutterButton, R.id.ruler_shutter, R.id.shutterMeterViewContainer, R.id.shutterLabel, paramters.get(Camera2Manager.EXPOSURE_TIME_RANGE), value -> shutterSpeed = value, "Shutter");
        setupIconAndDraggableRuler(R.id.focusButton, R.id.ruler_focus, R.id.focusMeterViewContainer, R.id.focusLabel, paramters.get(Camera2Manager.LENS_AVAILABLE_FOCAL_LENGTHS), value -> focusValue = value, "Focus");
        setupIconAndDraggableRuler(R.id.exposureButton, R.id.ruler_exposure, R.id.exposureMeterViewContainer, R.id.exposureLabel, paramters.get(Camera2Manager.EXPOSURE_TIME_RANGE), value -> exposureValue = value, "Exposure");
        setupIconAndDraggableRuler(R.id.intervalButton, R.id.ruler_interval, R.id.intervalMeterViewContainer, R.id.intervalLabel, paramters.get(Camera2Manager.SENSOR_MAX_FRAME_DURATION), value -> intervalValue = value, "Interval");
    }

    private void setupIconAndDraggableRuler(int buttonId, int draggableMeterId, int meterViewContainerId, int labelId, List<String> values, ValueChangeListener listener, String label) {
        AppCompatImageButton button = mainActivity.findViewById(buttonId);
        TextView labelView = mainActivity.findViewById(labelId);
        DraggableRulerView meterView = mainActivity.findViewById(draggableMeterId);
        FrameLayout meterViewContainer = mainActivity.findViewById(meterViewContainerId);

        meterView.setCustomValues(values);
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
            labelView.setVisibility(imageButtonsVisibility.get(buttonId) ? View.VISIBLE : View.GONE);
            meterView.setVisibility(imageButtonsVisibility.get(buttonId) ? View.VISIBLE : View.GONE);

            // Check if all values are false and hide the container if they are
            if (allValuesAreFalse(imageButtonsVisibility)) {
                meterViewContainer.setVisibility(View.GONE);
            } else {
                meterViewContainer.setVisibility(View.VISIBLE);
            }
        });

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

}
