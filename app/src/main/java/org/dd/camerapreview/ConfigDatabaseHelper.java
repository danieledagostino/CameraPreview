package org.dd.camerapreview;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ConfigDatabaseHelper extends SQLiteOpenHelper {

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

    private static final String CREATE_CAMERA_TABLE = "CREATE TABLE " + TABLE_NAME + " ("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "configKey INTEGER, " // Chiave (es. EXPOSURE_TIME_RANGE)
            + "configValues TEXT);"; // Valori associati alla chiave

    private static final String CREATE_RULER_TABLE = "CREATE TABLE " + RULER_TABLE_NAME + " ("
            + RULER_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + RULER_COLUMN_VALUE + " TEXT);"; // Valore selezionato dal DraggableRulerView

    public ConfigDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_CAMERA_TABLE);
        db.execSQL(CREATE_RULER_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + RULER_TABLE_NAME);
        onCreate(db);
    }
}