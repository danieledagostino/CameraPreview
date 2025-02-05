package org.dd.camerapreview;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.util.List;
import java.util.Map;

public class CameraConfigManager {

    private CameraConfigDatabaseHelper dbHelper;

    public CameraConfigManager(Context context) {
        dbHelper = new CameraConfigDatabaseHelper(context);
    }

    // Inserire configurazione da una mappa
    public void insertConfig(Map<Integer, List> configMap) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        for (Map.Entry<Integer, List> entry : configMap.entrySet()) {
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
            contentValues.put("configKey", key);
            contentValues.put("configValues", valueString.toString());

            // Inserisci il record
            db.insert(CameraConfigDatabaseHelper.TABLE_NAME, null, contentValues);
        }

        db.close(); // Chiudi il database
    }
}
