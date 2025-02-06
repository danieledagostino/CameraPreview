package org.dd.camerapreview;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CameraConfigManager {

    private ConfigDatabaseHelper dbHelper;
    private static CameraConfigManager instance;

    private static final String COLUMN_EXPOSURE_TIME = "exposure_time";
    private static final String COLUMN_SENSITIVITY = "sensitivity";
    private static final String COLUMN_FOCUS_DISTANCE = "focus_distance";
    private static final String COLUMN_AE_COMPENSATION = "ae_compensation";
    private static final String COLUMN_FOCAL_LENGTH = "focal_length";
    private static final String COLUMN_FRAME_DURATION = "frame_duration";

    private static final String COLUMN_CONFIG_KEY = "configKey";
    private static final String COLUMN_CONFIG_VALUES = "configValues";

    public CameraConfigManager(Context context) {
        dbHelper = new ConfigDatabaseHelper(context);
    }

    // Inserire configurazione da una mappa
    public void insertConfig(Map<Integer, List<String>> configMap) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        for (Map.Entry<Integer, List<String>> entry : configMap.entrySet()) {
            Integer key = entry.getKey(); // Chiave della configurazione (es. EXPOSURE_TIME_RANGE)
            List<String> values = entry.getValue(); // Lista di valori associati

            // Unisci i valori in una singola stringa separata da virgole
            StringBuilder valueString = new StringBuilder();
            for (String value : values) {
                if (valueString.length() > 0) valueString.append(","); // Aggiungi separatore
                valueString.append(value);
            }

            // Prepara i dati per l'inserimento
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_CONFIG_KEY, key);
            contentValues.put(COLUMN_CONFIG_VALUES, valueString.toString());

            // Inserisci il record
            db.insert(ConfigDatabaseHelper.TABLE_NAME, null, contentValues);
        }

        db.close(); // Chiudi il database
    }

    public Map<Integer, List<String>> readCameraConfig() {
        Map<Integer, List<String>> configMap = new HashMap<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Esegui una query per ottenere tutte le configurazioni
        Cursor cursor = db.query(ConfigDatabaseHelper.TABLE_NAME,
                new String[]{COLUMN_CONFIG_KEY, COLUMN_CONFIG_VALUES},
                null, null, null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int configKey = cursor.getInt(cursor.getColumnIndex(COLUMN_CONFIG_KEY));
                String configValues = cursor.getString(cursor.getColumnIndex(COLUMN_CONFIG_VALUES));

                // Dividi la stringa dei valori in una lista
                List<String> valuesList = List.of(configValues.split(","));

                // Aggiungi alla mappa
                configMap.put(configKey, valuesList);
            }
            cursor.close(); // Chiudi il cursore
        }

        db.close(); // Chiudi il database
        return configMap;
    }
    public static synchronized CameraConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new CameraConfigManager(context.getApplicationContext());
        }
        return instance;
    }
}
