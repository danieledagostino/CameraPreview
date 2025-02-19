package org.dd.camerapreview;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public class ConfigDatabaseHelper {

    private static final String DATABASE_NAME = "CameraConfig.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NAME = "camera_config";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_CAMERA_ID = "cameraId";
    public static final String COLUMN_RESOLUTION = "resolution";
    public static final String COLUMN_FRAME_RATE = "frameRate";
    public static final String COLUMN_FOCUS_MODE = "focusMode";
    public static final String COLUMN_EXPOSURE_MODE = "exposureMode";

    public static final String RULER_TABLE_NAME = "ruler_config";
    public static final String RULER_COLUMN_ID = "id";
    public static final String RULER_COLUMN_VALUE = "selectedValue";

    private static final String PREF_NAME = "CameraConfigPrefs";
    private static final String CAMERA_CONFIG_PREFIX = "camera_config_";
    private static final String RULER_CONFIG_PREFIX = "ruler_config_";
    private SharedPreferences sharedPreferences;

    public ConfigDatabaseHelper(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveCameraConfig(int configKey, String configValues) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(CAMERA_CONFIG_PREFIX + configKey, configValues);
        editor.apply();
    }

    public String getCameraConfig(int configKey) {
        return sharedPreferences.getString(CAMERA_CONFIG_PREFIX + configKey, "");
    }

    public void saveRulerConfig(int id, String selectedValue) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(RULER_CONFIG_PREFIX + id, selectedValue);
        editor.apply();
    }

    public String getRulerConfig(int id) {
        return sharedPreferences.getString(RULER_CONFIG_PREFIX + id, "");
    }

    public Map<Integer, String> getAllCameraConfigs() {
        Map<Integer, String> configMap = new HashMap<>();
        for (Map.Entry<String, ?> entry : sharedPreferences.getAll().entrySet()) {
            if (entry.getKey().startsWith(CAMERA_CONFIG_PREFIX)) {
                int key = Integer.parseInt(entry.getKey().replace(CAMERA_CONFIG_PREFIX, ""));
                configMap.put(key, (String) entry.getValue());
            }
        }
        return configMap;
    }

    public Map<Integer, String> getAllRulerConfigs() {
        Map<Integer, String> configMap = new HashMap<>();
        for (Map.Entry<String, ?> entry : sharedPreferences.getAll().entrySet()) {
            if (entry.getKey().startsWith(RULER_CONFIG_PREFIX)) {
                int key = Integer.parseInt(entry.getKey().replace(RULER_CONFIG_PREFIX, ""));
                configMap.put(key, (String) entry.getValue());
            }
        }
        return configMap;
    }

    public void clearAllConfigs() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }
}