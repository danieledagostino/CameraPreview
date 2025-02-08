package org.dd.camerapreview;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.LogCallback;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.Statistics;
import com.arthenica.ffmpegkit.StatisticsCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.media.MediaScannerConnection;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.annotation.TargetApi;
public class Camera2Manager {

    private final Activity activity;
    private CameraManager cameraManager;
    private String currentCameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private TextureView textureView;
    private boolean isBackCamera = true;
    private boolean isCapturing = false;

    public final static int EXPOSURE_TIME_RANGE = 1; //Acquisition time
    public final static List<Long> EXPOSURE_VALUES_NS = Arrays.asList(25_000_000L, 100_000_000L, 250_000_000L, 1_000_000_000L, 10_000_000_000L, 25_000_000_000L, 30_000_000_000L, 50_000_000_000L, 100_000_000_000L);
    public final static int SENSITIVITY_RANGE = 2; //ISO
    public final static List<Integer> SENSITIVITY_VALUES = Arrays.asList(100, 200, 400, 800, 1600, 3200, 6400);
    public final static int LENS_MINIMUM_FOCUS_DISTANCE = 3;

    public final static int AE_COMPENSATION_RANGE = 4;
    public final static List<Integer> AE_COMPENSATION_RANGE_VALUES = Arrays.asList(-6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6);
    public final static int LENS_AVAILABLE_FOCAL_LENGTHS = 5;
    public final static int SENSOR_MAX_FRAME_DURATION = 6;

    private List<String> capturedImagePaths = new ArrayList<>();

    private static Camera2Manager instance;

    //current settings
    long exposureTime = 0;
    int sensitivity = 0;
    float focusDistance = 0;
    int aeCompensation = 0;
    float focalLength = 0;
    long frameDuration = 0;

    private int imageCounter = 0;

    Map<Integer, String> currentSettings;

    private CaptureRequest previewRequest; // Aggiungi questa variabile a livello di classe

    private long startTime;
    private Handler handler = new Handler(Looper.getMainLooper());
    private TextView timeTextView; // TextView per visualizzare il tempo del video
    private int fps = 30; // Fotogrammi per secondo del video
    private ExecutorService captureExecutorService;

    public static Camera2Manager getInstance(Activity activity) {
        if (instance == null) {
            instance = new Camera2Manager(activity);
        }
        return instance;
    }

    public Camera2Manager(Activity activity) {
        this.activity = activity;
        textureView = activity.findViewById(R.id.camera_preview);
        timeTextView = activity.findViewById(R.id.timeTextView);
        cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        // Imposta il SurfaceTextureListener
        textureView.setSurfaceTextureListener(textureListener);

        Map<Integer, List<String>> cameraConfig = CameraConfigManager.getInstance(activity).readCameraConfig();

        if (cameraConfig.containsKey(EXPOSURE_TIME_RANGE)) {
            exposureTime = convertToNanoseconds(cameraConfig.get(EXPOSURE_TIME_RANGE).get(0)); // Leggi il primo valore
        }
        if (cameraConfig.containsKey(SENSITIVITY_RANGE)) {
            sensitivity = Integer.parseInt(cameraConfig.get(SENSITIVITY_RANGE).get(0));
        }
        if (cameraConfig.containsKey(LENS_MINIMUM_FOCUS_DISTANCE)) {
            String minFocusDistanceStr = cameraConfig.get(LENS_MINIMUM_FOCUS_DISTANCE).get(0);
            if (minFocusDistanceStr != null && !minFocusDistanceStr.isEmpty()) {
                focusDistance = Float.parseFloat(minFocusDistanceStr);
            }
        }
        if (cameraConfig.containsKey(AE_COMPENSATION_RANGE)) {
            aeCompensation = Integer.parseInt(cameraConfig.get(AE_COMPENSATION_RANGE).get(0));
        }
        if (cameraConfig.containsKey(LENS_AVAILABLE_FOCAL_LENGTHS)) {
            focalLength = Float.parseFloat(cameraConfig.get(LENS_AVAILABLE_FOCAL_LENGTHS).get(0));
        }
        if (cameraConfig.containsKey(SENSOR_MAX_FRAME_DURATION)) {
            frameDuration = Long.parseLong(cameraConfig.get(SENSOR_MAX_FRAME_DURATION).get(0));
        }

    }

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            // La superficie è pronta, apri la fotocamera
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            // Gestisci eventuali modifiche di dimensione, se necessario
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            // Rilascia risorse della fotocamera
            closeCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            // Non richiesto in questo caso
        }
    };

    public void openCamera() {
        startBackgroundThread();
        if (textureView.isAvailable()) {
            try {
                currentCameraId = isBackCamera ? getBackCameraId() : getFrontCameraId();
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, 1);
                    return;
                }
                // Configura ImageReader prima di aprire la fotocamera
                setupImageReader();
                cameraManager.openCamera(currentCameraId, stateCallback, backgroundHandler);
            } catch (Exception e) {
                Toast.makeText(activity, "Error opening camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getBackCameraId() throws CameraAccessException {
        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                return cameraId;
            }
        }
        return null;
    }

    private String getFrontCameraId() throws CameraAccessException {
        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            setTextureTransform(characteristics);
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                return cameraId;
            }
        }
        return null;
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private final MutableLiveData<Map<Integer, String>> parametersReady = new MutableLiveData<>();


    public LiveData<Map<Integer, String>> isPreviewConfigured() {
        return parametersReady;
    }

    public void switchCamera() {
        isBackCamera = !isBackCamera;
        closeCamera();
        openCamera();
    }

    public void selectWideCamera() {
        // Logic to switch to wide camera (if available)
        Toast.makeText(activity, "Wide camera selected.", Toast.LENGTH_SHORT).show();
    }

    public void selectStandardCamera() {
        // Logic to switch to standard camera
        Toast.makeText(activity, "Standard camera selected.", Toast.LENGTH_SHORT).show();
    }

    public void selectZoomCamera() {
        // Logic to switch to zoom camera (if available)
        Toast.makeText(activity, "Zoom camera selected.", Toast.LENGTH_SHORT).show();
    }

    public void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;

        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        stopBackgroundThread();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            // Calcola il tempo di video in secondi
            long videoTime = imageCounter / fps; // Tempo del video in secondi
            long seconds = videoTime % 60;
            long minutes = (videoTime / 60) % 60;
            long hours = (videoTime / 3600);

            // Formatta il tempo e aggiornalo nel TextView
            String time = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            timeTextView.setText(time);
        }
    };

    public void updatePreview(int cameraConf, String value) {
        if (captureSession == null || cameraDevice == null) {
            return; // Se la sessione o la fotocamera non sono inizializzate, esci
        }

        try {
            // Ottieni il builder per la richiesta di cattura
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface surface = new Surface(textureView.getSurfaceTexture());
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);

            builder.addTarget(surface);

            // Applica la configurazione in base al valore di cameraConf
            switch (cameraConf) {
                case EXPOSURE_TIME_RANGE:
                    exposureTime = convertToNanoseconds(value);
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime);
                    break;

                case SENSITIVITY_RANGE:
                    sensitivity = Integer.parseInt(value);
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity);
                    break;

                case LENS_MINIMUM_FOCUS_DISTANCE:
                    focusDistance = Float.parseFloat(value);
                    builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance);
                    break;

                case AE_COMPENSATION_RANGE:
                    aeCompensation = Integer.parseInt(value);
                    builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeCompensation);
                    break;

                case LENS_AVAILABLE_FOCAL_LENGTHS:
                    focalLength = Float.parseFloat(value);
                    builder.set(CaptureRequest.LENS_FOCAL_LENGTH, focalLength);
                    break;

                case SENSOR_MAX_FRAME_DURATION:
                    frameDuration = Long.parseLong(value);
                    builder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration);
                    break;

                default:
                    // Configurazione non supportata
                    Toast.makeText(activity, "Unsupported camera configuration", Toast.LENGTH_SHORT).show();
                    return;
            }

            // Applica la nuova configurazione alla sessione di cattura
            captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);

        } catch (CameraAccessException | NumberFormatException e) {
            Log.e("Camera", "Error updating camera preview: ", e);
            Toast.makeText(activity, "Error updating camera preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startPreview() {
        try {
            Surface textureSurface = new Surface(textureView.getSurfaceTexture());
            Surface imageReaderSurface = imageReader.getSurface();

            // Crea la sessione di cattura con entrambe le superfici
            cameraDevice.createCaptureSession(Arrays.asList(textureSurface, imageReaderSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        // Crea una richiesta di anteprima
                        CaptureRequest.Builder previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        previewBuilder.addTarget(textureSurface);

                        previewRequest = previewBuilder.build();
                        setCurrentCameraSettings(previewRequest);

                        // Imposta la richiesta di preview come ripetitiva
                        captureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);

                        parametersReady.postValue(currentSettings);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(activity, "Preview configuration failed.", Toast.LENGTH_SHORT).show();
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startCapture() {
        if (cameraDevice == null || !isCapturing || captureSession == null) return;

        // Crea un thread separato per gestire la cattura dell'immagine
        captureExecutorService = Executors.newSingleThreadExecutor();
        captureExecutorService.execute(() -> {
            try {
                // Crea una richiesta di cattura
                CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureBuilder.addTarget(imageReader.getSurface());
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

                // Imposta l'orientamento dell'immagine
                int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

                // Crea una sessione di acquisizione nel thread di background
                cameraDevice.createCaptureSession(Collections.singletonList(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        captureSession = session;
                        try {
                            // Avvia la cattura dell'immagine
                            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                                @Override
                                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                    // Aggiorna il tempo del video
                                    handler.post(updateTimeRunnable);
                                }
                            }, backgroundHandler);
                        } catch (CameraAccessException e) {
                            Log.e("Camera", "Error starting capture: ", e);
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e("Camera", "Camera capture session configuration failed");
                    }
                }, backgroundHandler);
            } catch (CameraAccessException e) {
                Log.e("Camera", "Error during capture setup: ", e);
            }
        });
    }

    public void startCaptureCycle() {
        isCapturing = true;
        setupImageReader();
        // Ottieni la directory temporanea
        File tempDirectory = activity.getExternalFilesDir(null);

        // Pulisci la cartella temporanea prima di iniziare un nuovo ciclo
        if (tempDirectory != null) {
            clearTemporaryFolder(tempDirectory);
        }
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startCapture();
        Toast.makeText(activity, "Capture cycle started.", Toast.LENGTH_SHORT).show();
    }

    public void stopCaptureCycle() {
        isCapturing = false;
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.removeCallbacks(updateTimeRunnable);
        captureExecutorService.shutdown();
        onCycleCompleted();
        Toast.makeText(activity, "Capture cycle stopped.", Toast.LENGTH_SHORT).show();
    }

    private int getOrientation(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0:
                return 90;
            case Surface.ROTATION_90:
                return 0;
            case Surface.ROTATION_180:
                return 270;
            case Surface.ROTATION_270:
                return 180;
            default:
                return 0;
        }
    }

    private void onCycleCompleted() {
        System.out.println("Ciclo completato.");

        if (capturedImagePaths.isEmpty()) {
            Toast.makeText(activity, "Nessuna immagine catturata per generare il video.", Toast.LENGTH_SHORT).show();
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File dcimDirectory = getOutputDirectory();

        if (dcimDirectory == null) {
            Toast.makeText(activity, "Errore: impossibile accedere alla directory.", Toast.LENGTH_SHORT).show();
            return;
        }

        String outputPath = new File(dcimDirectory, "NightLapse_" + timeStamp + ".mp4").getAbsolutePath();
        StringBuilder ffmpegCommand = new StringBuilder("-y -r 30 -i ")
                .append(activity.getExternalFilesDir(null)).append("/capturedImage_%03d.jpg ")
                .append("-vf \"transpose=1\" -c:v mpeg4 -pix_fmt yuv420p ").append(outputPath);

        // Esegui in un thread separato
        new Thread(() -> {
            // Mostra la barra di progresso nel thread principale
            activity.runOnUiThread(() -> {
                ProgressBar progressBar = activity.findViewById(R.id.progressBar);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
            });

            // Calcola la durata totale del video in secondi
            int totalFrames = capturedImagePaths.size();
            int frameRate = 30; // Frame rate definito nel comando FFmpeg
            int estimatedDuration = totalFrames / frameRate; // Durata stimata in secondi

            FFmpegKit.executeAsync(ffmpegCommand.toString(), session -> {
                activity.runOnUiThread(() -> {
                    ProgressBar progressBar = activity.findViewById(R.id.progressBar);
                    progressBar.setVisibility(View.GONE);
                });

                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    Log.i("Camera", "Video generato con successo: " + outputPath);
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Video salvato in: " + outputPath, Toast.LENGTH_LONG).show()
                    );

                    // Aggiungi il video al MediaStore per API 29+ o usa MediaScanner per versioni inferiori
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        addToMediaStore(outputPath, "video/mp4");
                    } else {
                        MediaScannerConnection.scanFile(activity, new String[]{outputPath}, null, (path, uri) -> {
                            Log.i("MediaScanner", "File aggiunto alla galleria: " + path);
                        });
                    }
                } else {
                    Log.e("Camera", "Errore nella generazione del video. Return code: " + session.getReturnCode());
                    Log.e("Camera", "Errore nella generazione del video: " + session.getFailStackTrace());
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Errore nella generazione del video.", Toast.LENGTH_SHORT).show()
                    );
                }
            }, new LogCallback() {
                @Override
                public void apply(com.arthenica.ffmpegkit.Log log) {
                    Log.i("FFmpeg", log.getMessage());
                }
            }, new StatisticsCallback() {
                @Override
                public void apply(Statistics statistics) {
                    long currentFrame = statistics.getVideoFrameNumber();

                    if (totalFrames > 0) {
                        // Calcola la percentuale e aggiorna la ProgressBar nel thread principale
                        int progress = (int) ((currentFrame * 100) / totalFrames);
                        activity.runOnUiThread(() -> {
                            ProgressBar progressBar = activity.findViewById(R.id.progressBar);
                            progressBar.setVisibility(View.VISIBLE); // Mostra la ProgressBar
                            progressBar.setProgress(progress);
                        });
                    }
                }
            });

            // Riavvia la fotocamera
            closeCamera();
            openCamera();
        }).start();
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private void addToMediaStore(String filePath, String mimeType) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, new File(filePath).getName()); // Nome del file
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType); // Tipo MIME
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES); // Cartella in cui salvare il file

        ContentResolver resolver = activity.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            try (OutputStream out = resolver.openOutputStream(uri)) {
                // Copia il contenuto del file esistente nel nuovo URI
                Files.copy(new File(filePath).toPath(), out);
                Log.i("MediaStore", "File aggiunto correttamente: " + filePath);
            } catch (IOException e) {
                Log.e("MediaStore", "Errore durante la copia del file: " + e.getMessage());
            }
        } else {
            Log.e("MediaStore", "Errore nell'aggiungere il file al MediaStore");
        }
    }

    private File getOutputDirectory() {
        File dcimDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "NightLapse");

        // Crea la directory se non esiste
        if (!dcimDirectory.exists()) {
            if (!dcimDirectory.mkdirs()) {
                Log.e("Camera", "Errore: impossibile creare la directory DCIM/NightLapse.");
                return null;
            }
        }

        return dcimDirectory;
    }


    public void selectCamera(String cameraType) {
        try {
            switch (cameraType) {
                case "wide":
                    // Logic to select wide camera
                    currentCameraId = getWideCameraId();
                    break;
                case "standard":
                    // Logic to select standard camera
                    currentCameraId = getStandardCameraId();
                    break;
                case "zoom":
                    // Logic to select zoom camera
                    currentCameraId = getZoomCameraId();
                    break;
                default:
                    currentCameraId = getBackCameraId(); // Default to back camera
                    break;
            }
            closeCamera();
            openCamera();
        } catch (CameraAccessException e) {
            Log.e("Camera", "Error selecting camera: " + e);
        }
    }

    private String getWideCameraId() throws CameraAccessException {
        // Add logic to find the wide camera (if supported by the device)
        return getCameraIdByLensFacing(CameraCharacteristics.LENS_FACING_BACK);
    }

    private String getStandardCameraId() throws CameraAccessException {
        // Add logic to find the standard camera (if supported by the device)
        return getCameraIdByLensFacing(CameraCharacteristics.LENS_FACING_BACK);
    }

    private String getZoomCameraId() throws CameraAccessException {
        // Add logic to find the zoom camera (if supported by the device)
        return getCameraIdByLensFacing(CameraCharacteristics.LENS_FACING_BACK);
    }

    private String getCameraIdByLensFacing(int lensFacing) throws CameraAccessException {
        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing) {
                return cameraId;
            }
        }
        return null;
    }

    void setTextureTransform(CameraCharacteristics characteristics) {
        Size previewSize = getPreviewSize(characteristics);
        int width = previewSize.getWidth();
        int height = previewSize.getHeight();
        int sensorOrientation = getCameraSensorOrientation(characteristics);
        // Indicate the size of the buffer the texture should expect
        textureView.getSurfaceTexture().setDefaultBufferSize(width, height);
        // Save the texture dimensions in a rectangle
        RectF viewRect = new RectF(0,0, textureView.getWidth(), textureView.getHeight());
        // Determine the rotation of the display
        float rotationDegrees = 0;
        try {
            rotationDegrees = (float)getDisplayRotation();
        } catch (Exception ignored) {
        }
        float w, h;
        if ((sensorOrientation - rotationDegrees) % 180 == 0) {
            w = width;
            h = height;
        } else {
            // Swap the width and height if the sensor orientation and display rotation don't match
            w = height;
            h = width;
        }
        float viewAspectRatio = viewRect.width()/viewRect.height();
        float imageAspectRatio = w/h;
        final PointF scale;
        // This will make the camera frame fill the texture view, if you'd like to fit it into the view swap the "<" sign for ">"
        if (viewAspectRatio < imageAspectRatio) {
            // If the view is "thinner" than the image constrain the height and calculate the scale for the texture width
            scale = new PointF((viewRect.height() / viewRect.width()) * ((float) height / (float) width), 1f);
        } else {
            scale = new PointF(1f, (viewRect.width() / viewRect.height()) * ((float) width / (float) height));
        }
        if (rotationDegrees % 180 != 0) {
            // If we need to rotate the texture 90º we need to adjust the scale
            float multiplier = viewAspectRatio < imageAspectRatio ? w/h : h/w;
            scale.x *= multiplier;
            scale.y *= multiplier;
        }

        Matrix matrix = new Matrix();
        // Set the scale
        matrix.setScale(scale.x, scale.y, viewRect.centerX(), viewRect.centerY());
        if (rotationDegrees != 0) {
            // Set rotation of the device isn't upright
            matrix.postRotate(0 - rotationDegrees, viewRect.centerX(), viewRect.centerY());
        }
        // Transform the texture
        textureView.setTransform(matrix);
    }

    int getDisplayRotation() {
        switch (textureView.getDisplay().getRotation()) {
            case Surface.ROTATION_0:
            default:
                return 0;
            case Surface.ROTATION_90:
                return  90;
            case Surface.ROTATION_180:
                return  180;
            case Surface.ROTATION_270:
                return 270;
        }
    }

    Size getPreviewSize(CameraCharacteristics characteristics) {
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
        // TODO: decide on which size fits your view size the best
        return previewSizes[0];
    }

    int getCameraSensorOrientation(CameraCharacteristics characteristics) {
        Integer cameraOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        return (360 - (cameraOrientation != null ? cameraOrientation : 0)) % 360;
    }

    public Map<Integer, List<String>> getMinMaxCameraParameters() throws CameraAccessException {

        Map<Integer, List<String>> results = new HashMap<>();
        /*
        Ecco alcuni valori comuni riscontrati su molti dispositivi:
        ISO: Da 100 a 3200 (alcuni arrivano fino a 6400 o più).
        Shutter Speed: Da 1/4000 secondi (~250,000 ns) a 30 secondi (~30,000,000,000 ns).
        Focus Distance: 0.0 (infinito) a un massimo variabile, spesso 10-20 (1/m).
        Compensazione Esposizione: Da -3 a +3 stop (con step di 1/3 stop).
        Focale: Dipende dalla lente, spesso tra 1.0 e 8.0 mm.
        */
        String cameraId = cameraManager.getCameraIdList()[0];
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

        Range<Integer> isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (isoRange != null) {
            Integer minISO = isoRange.getLower();
            Integer maxISO = isoRange.getUpper();
            List<String> list = new ArrayList<>();

            for (Integer val : SENSITIVITY_VALUES) {
                if (val.compareTo(minISO) >= 1 && val.compareTo(maxISO) <= 1) {
                    list.add(String.valueOf(val));
                }
            }
            results.put(SENSITIVITY_RANGE, list);
            //log.debug("Camera", "ISO Range: " + minISO + " - " + maxISO);
        }

        Range<Long> exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if (exposureRange != null) {
            long minExposureTime = exposureRange.getLower();
            long maxExposureTime = exposureRange.getUpper();
            int denominator = 1000; // Base (es. 1 secondo = 1000 ms)

            List<String> list = new ArrayList<>();

            for (Long val : EXPOSURE_VALUES_NS) {
                if (val.compareTo(minExposureTime) >= 1 && val.compareTo(maxExposureTime) <= 1) {
                    list.add(convertToFraction(val));
                }
            }
            results.put(EXPOSURE_TIME_RANGE, list);
            //log.debug("Camera", "Exposure Time Range: " + minExposureTime + " ns - " + maxExposureTime + " ns");
        }

        Float minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        if (minFocusDistance != null) {
            results.put(LENS_MINIMUM_FOCUS_DISTANCE, Arrays.asList(calculateLensType(minFocusDistance)));
            //log.debug("Camera", "Minimum Focus Distance: " + minFocusDistance + " (1/m)");
        }

        Range<Integer> aeCompRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        Rational aeStep = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        if (aeCompRange != null && aeStep != null) {
            int minAeComp = aeCompRange.getLower();
            int maxAeComp = aeCompRange.getUpper();

            List<String> list = new ArrayList<>();
            for (Integer val : AE_COMPENSATION_RANGE_VALUES) {
                if (val.compareTo(minAeComp) >= 1 && val.compareTo(maxAeComp) <= 1) {
                    list.add(String.valueOf(val));
                }
            }
            results.put(AE_COMPENSATION_RANGE, list);
            //log.debug("Camera", "AE Compensation Range: " + minAeComp + " - " + maxAeComp + ", Step: " + aeStep);
        }

        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if (focalLengths != null) {
            List<String> focalLengthsAsStrings = new ArrayList<>();

            for (float focalLength : focalLengths) {
                focalLengthsAsStrings.add(Float.toString(focalLength));
            }
            results.put(LENS_AVAILABLE_FOCAL_LENGTHS, focalLengthsAsStrings);
        }

        Long maxFrameDuration = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
        if (maxFrameDuration != null) {
            results.put(SENSOR_MAX_FRAME_DURATION, Arrays.asList(String.valueOf(maxFrameDuration)));
            //log.debug("Camera", "Max Frame Duration: " + maxFrameDuration + " ns");
        }


        return results;
    }

    private String convertToFraction(long nanoseconds) {

        double milliseconds = nanoseconds / 1_000_000.0;
        int denominator = 1000;
        int numerator = (int) Math.round(milliseconds * denominator);

        int gcd = findGCD(numerator, denominator);
        numerator /= gcd;
        denominator /= gcd;

        // Ritorna la rappresentazione "denominatore/numerator"
        return denominator + "/" + numerator;
    }

    private int findGCD(int a, int b) {
        if (b == 0) {
            return a;
        }
        return findGCD(b, a % b);
    }

    private long convertToNanoseconds(String fraction) {
        String[] parts = fraction.split("/");
        int denominator = Integer.parseInt(parts[0]);
        int numerator = Integer.parseInt(parts[1]);

        // Calcola il risultato in millisecondi
        double milliseconds = (double) numerator / denominator;

        // Converte in nanosecondi
        return (long) (milliseconds * 1_000_000);
    }

    private String calculateLensType(double minFocusDistance) {
        // Calcoliamo la potenza della lente (1/distanza)
        double lensPower = 1 / minFocusDistance;

        // Determiniamo il tipo di obiettivo
        if (lensPower >= 10) {
            return "Macro 0.1";
        } else if (lensPower >= 3.33) {
            return "Obiettivo medio 0.3";
        } else if (lensPower >= 1) {
            return "Obiettivo focale lunga 1m";
        } else {
            return "";
        }
    }

    public Map<Integer, String> getCurrentCameraSettings() {
        return currentSettings;
    }

    public void setCurrentCameraSettings(CaptureRequest previewRequest) {
        currentSettings = new HashMap<>();

        if (previewRequest == null) {
            throw new IllegalStateException("La richiesta di anteprima non è stata ancora configurata. Assicurati che la sessione sia avviata.");
        }

        // Ottieni il valore corrente di ISO
        Integer isoValue = previewRequest.get(CaptureRequest.SENSOR_SENSITIVITY);
        if (isoValue != null) {
            currentSettings.put(SENSITIVITY_RANGE, String.valueOf(isoValue));
        }

        // Ottieni il tempo di esposizione corrente
        Long exposureTime = previewRequest.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
        if (exposureTime != null) {
            currentSettings.put(EXPOSURE_TIME_RANGE, String.valueOf(exposureTime));
        }

        // Ottieni la distanza corrente di messa a fuoco
        Float focusDistance = previewRequest.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        if (focusDistance != null) {
            currentSettings.put(LENS_MINIMUM_FOCUS_DISTANCE, String.valueOf(focusDistance));
        }

        // Ottieni il valore corrente di compensazione dell'esposizione automatica (AE)
        Integer aeCompensation = previewRequest.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
        if (aeCompensation != null) {
            currentSettings.put(AE_COMPENSATION_RANGE, String.valueOf(aeCompensation));
        }

        // Ottieni la lunghezza focale corrente
        Float focalLength = previewRequest.get(CaptureRequest.LENS_FOCAL_LENGTH);
        if (focalLength != null) {
            currentSettings.put(LENS_AVAILABLE_FOCAL_LENGTHS, String.valueOf(focalLength));
        }

        // Ottieni la durata corrente del frame
        Long frameDuration = previewRequest.get(CaptureRequest.SENSOR_FRAME_DURATION);
        if (frameDuration != null) {
            currentSettings.put(SENSOR_MAX_FRAME_DURATION, String.valueOf(frameDuration));
        }
    }


    private void setupImageReader() {
        // Crea un ImageReader per acquisire immagini in formato JPEG
        imageReader = ImageReader.newInstance(textureView.getWidth(), textureView.getHeight(), ImageFormat.JPEG, 2);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                // Gestisci l'immagine catturata
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    // Salva l'immagine o esegui altre operazioni
                    saveImage(image);
                    image.close();

                    // Se l'acquisizione ciclica è attiva, avvia una nuova acquisizione
                    if (isCapturing) {
                        startCapture();
                    }
                }
            }
        }, backgroundHandler);
    }

    private void saveImage(Image image) {
        // Implementa la logica per salvare l'immagine
        // Esempio: salva l'immagine in un file
        // Nota: questa è una semplice implementazione, potrebbe essere necessario gestire i permessi e l'I/O su un thread separato
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        saveCapturedImage(bitmap);
        imageCounter++;
        String imageName = String.format("capturedImage_%03d.jpg", imageCounter);
        capturedImagePaths.add(imageName);
        File file = new File(activity.getExternalFilesDir(null), imageName);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);

            capturedImagePaths.add(file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveCapturedImage(Bitmap bitmap) {
        String imageName = String.format("capturedImage_%03d.jpg", imageCounter);
        String filePath = new File(activity.getExternalFilesDir(null), imageName).getAbsolutePath();

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            // Salva l'immagine nel file
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        } catch (IOException e) {
            Log.e("Camera", "Error saving image", e);
        }
    }

    private void clearTemporaryFolder(File tempDirectory) {
        if (tempDirectory != null && tempDirectory.isDirectory()) {
            File[] files = tempDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().startsWith("capturedImage_")) {
                        if (!file.delete()) {
                            Log.w("Camera", "Impossibile eliminare il file: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }
}
