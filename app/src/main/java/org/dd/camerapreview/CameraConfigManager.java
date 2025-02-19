package org.dd.camerapreview;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CameraConfigManager {

    private static final String PREF_NAME = "CameraConfigPrefs";
    private static final String CONFIG_KEY_PREFIX = "config_";
    private static CameraConfigManager instance;
    private SharedPreferences sharedPreferences;

    private CameraConfigManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized CameraConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new CameraConfigManager(context.getApplicationContext());
        }
        return instance;
    }

    // Salva la configurazione in SharedPreferences
    public void insertConfig(Map<Integer, List<String>> configMap) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (Map.Entry<Integer, List<String>> entry : configMap.entrySet()) {
            String key = CONFIG_KEY_PREFIX + entry.getKey();
            String value = String.join(",", entry.getValue());
            editor.putString(key, value);
        }
        editor.apply();
    }

    // Legge la configurazione da SharedPreferences
    public Map<Integer, List<String>> readCameraConfig() {
        Map<Integer, List<String>> configMap = new HashMap<>();
        Map<String, ?> allEntries = sharedPreferences.getAll();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().startsWith(CONFIG_KEY_PREFIX)) {
                int configKey = Integer.parseInt(entry.getKey().replace(CONFIG_KEY_PREFIX, ""));
                List<String> values = Arrays.asList(((String) entry.getValue()).split(","));
                configMap.put(configKey, values);
            }
        }
        return configMap;
    }

    // Cancella tutte le configurazioni salvate
    public void clearConfig() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }
}
