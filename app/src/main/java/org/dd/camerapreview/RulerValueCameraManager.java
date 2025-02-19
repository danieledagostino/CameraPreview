package org.dd.camerapreview;

import android.content.Context;
import android.content.SharedPreferences;

public class RulerValueCameraManager {

    private static final String PREFS_NAME = "CameraConfigPrefs";
    private static final String RULER_PREFIX = "ruler_value_";
    private static RulerValueCameraManager instance;
    private SharedPreferences sharedPreferences;

    public RulerValueCameraManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Inserire un valore selezionato dal DraggableRulerView
    public void insertRulerValue(int id, String selectedValue) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(RULER_PREFIX + id, selectedValue);
        editor.apply();
    }

    // Recuperare un valore selezionato dal DraggableRulerView
    public String getRulerValue(int id) {
        return sharedPreferences.getString(RULER_PREFIX + id, "");
    }

    public static synchronized RulerValueCameraManager getInstance(Context context) {
        if (instance == null) {
            instance = new RulerValueCameraManager(context.getApplicationContext());
        }
        return instance;
    }
}
