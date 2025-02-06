package org.dd.camerapreview;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

public class RulerValueCameraManager {

    private ConfigDatabaseHelper dbHelper;
    private static RulerValueCameraManager instance;

    public RulerValueCameraManager(Context context) {
        dbHelper = new ConfigDatabaseHelper(context);
    }

    // Inserire un valore selezionato dal DraggableRulerView
    public void insertRulerValue(int id,String selectedValue) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues contentValues = new ContentValues();
        contentValues.put(ConfigDatabaseHelper.RULER_COLUMN_ID, id);
        contentValues.put(ConfigDatabaseHelper.RULER_COLUMN_VALUE, selectedValue);

        db.insert(ConfigDatabaseHelper.RULER_TABLE_NAME, null, contentValues);
        db.close();
    }

    public static synchronized RulerValueCameraManager getInstance(Context context) {
        if (instance == null) {
            instance = new RulerValueCameraManager(context.getApplicationContext());
        }
        return instance;
    }
}
