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
import android.hardware.camera2.params.InputConfiguration;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    public final static List<Long> EXPOSURE_VALUES_NS = Arrays.asList(25_000_000L, 100_000_000L, 250_000_000L, 1_000_000_000L, 10_000_000_000L, 25_000_000_000L);
    public final static int SENSITIVITY_RANGE = 2; //ISO
    public final static List<Integer> SENSITIVITY_VALUES = Arrays.asList(100, 200, 400, 800, 1600, 3200, 6400);
    public final static int LENS_MINIMUM_FOCUS_DISTANCE = 3;

    public final static int AE_COMPENSATION_RANGE = 4;
    public final static List<Integer> AE_COMPENSATION_RANGE_VALUES = Arrays.asList(-6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6);
    public final static int LENS_AVAILABLE_FOCAL_LENGTHS = 5;
    public final static int SENSOR_MAX_FRAME_DURATION = 6;
    /*
        tandard: 24 o 30 immagini al secondo (FPS) sono comuni per ottenere un risultato fluido e cinematografico.
        Più lento o artistico: Puoi scendere a 15 FPS se cerchi un effetto più "scattoso" o sperimentale.

        Scene veloci (nuvole in movimento, traffico): 1-3 secondi tra uno scatto e l'altro.
        Scene lente (alba, tramonto, crescita di piante): 10-60 secondi tra uno scatto e l'altro.
        Eventi estremamente lenti (costruzioni, crescita di alberi): ore o giorni tra uno scatto e l'altro.
    */
    public final static List<String> SENSOR_MAX_FRAME_DURATION_VALUES = Arrays.asList("Auto", "15fps", "24fps", "60fps", "90fps", "120fps", "150fps");
    public final static int DURATION = 7;
    public final static List<String> DURATION_VALUES = Arrays.asList("Auto", "10sec", "30sec", "1min", "3min", "5min", "10min");

    private List<String> capturedImagePaths = new ArrayList<>();

    private static Camera2Manager instance;

    //current settings
    long exposureTime = 0;
    int sensitivity = 0;
    float focusDistance = 0;
    int aeCompensation = 0;
    float focalLength = 0;
    long frameDuration = 1;
    long duration = 0;

    private int imageCounter = 0;

    Map<Integer, String> currentSettings;

    private CaptureRequest previewRequest; // Aggiungi questa variabile a livello di classe

    private Handler handler = new Handler(Looper.getMainLooper());
    private TextView timeTextView; // TextView per visualizzare il tempo del video
    private int fps = 30; // Fotogrammi per secondo del video

    Surface textureSurface;
    Surface reprocessableSurface;

    public static Camera2Manager getInstance(Activity activity) {
        if (instance == null) {
            try {
                instance = new Camera2Manager(activity);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return instance;
    }

    public Camera2Manager(Activity activity) throws CameraAccessException {
        this.activity = activity;
        textureView = activity.findViewById(R.id.camera_preview);
        timeTextView = activity.findViewById(R.id.timeTextView);
        cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        currentCameraId = getBackCameraId();

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

        getCameraCapabilities();

        openCamera();
    }

    /*
    BACKWARD_COMPATIBLE	La fotocamera è compatibile con le API delle vecchie versioni (Camera1).
    MANUAL_SENSOR	Supporta il controllo manuale su ISO, tempo di esposizione e bilanciamento del bianco.
    RAW	Può acquisire immagini in formato RAW (ad esempio DNG).
    BURST_CAPTURE	Supporta cattura in modalità burst ad alta velocità.
    DEPTH_OUTPUT	Supporta la generazione di mappe di profondità (utilizzabile per applicazioni AR o Bokeh).
    LOGICAL_MULTI_CAMERA	Fotocamera logica composta da più fotocamere fisiche (es. per cambiare tra grandangolo e tele).
    ULTRA_HIGH_RESOLUTION_SENSOR	Supporta sensori ad altissima risoluzione (es. > 48 MP).
    CONSTRAINED_HIGH_SPEED_VIDEO	Supporta registrazioni video ad alta velocità (es. 120fps o 240fps).
     */
    private void getCameraCapabilities() {
        CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

                // Ottieni le capacità disponibili
                int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

                if (capabilities != null) {
                    for (int capability : capabilities) {
                        switch (capability) {
                            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE:
                                Log.d("Camera2", "Camera ID: " + cameraId + " supporta BACKWARD_COMPATIBLE");
                                break;

                            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR:
                                Log.d("Camera2", "Camera ID: " + cameraId + " supporta MANUAL_SENSOR");
                                break;

                            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW:
                                Log.d("Camera2", "Camera ID: " + cameraId + " supporta RAW");
                                break;

                            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA:
                                Log.d("Camera2", "Camera ID: " + cameraId + " è LOGICAL_MULTI_CAMERA");
                                Set<String> physicalCameraIds = null;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    physicalCameraIds = characteristics.getPhysicalCameraIds();
                                }
                                for (String id : physicalCameraIds) {
                                    Log.d("Camera2", "ID fotocamera fisica: " + id);
                                }
                                break;

                            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR:
                                Log.d("Camera2", "Camera ID: " + cameraId + " supporta ULTRA HIGH RESOLUTION");
                                break;

                            default:
                                Log.d("Camera2", "Camera ID: " + cameraId + " capacità sconosciuta: " + capability);
                        }
                    }
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void openCamera() {
        startBackgroundThread();
        if (textureView.isAvailable()) {
            try {
                currentCameraId = isBackCamera ? getBackCameraId() : getFrontCameraId();
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, 1);
                    return;
                }

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

    private final MutableLiveData<Map<Integer, String>> parametersReady = new MutableLiveData<>();


    public LiveData<Map<Integer, String>> isPreviewConfigured() {
        return parametersReady;
    }

    public void switchCamera() {
        isBackCamera = !isBackCamera;
        closeCamera();
        openCamera();
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
                    break;

                case SENSITIVITY_RANGE:
                    sensitivity = Integer.parseInt(value);
                    break;

                case LENS_MINIMUM_FOCUS_DISTANCE:
                    focusDistance = Float.parseFloat(value);
                    break;

                case AE_COMPENSATION_RANGE:
                    aeCompensation = Integer.parseInt(value);
                    break;

                case LENS_AVAILABLE_FOCAL_LENGTHS:
                    focalLength = Float.parseFloat(value);
                    break;

                case SENSOR_MAX_FRAME_DURATION:
                    if ("Auto".equals(value)) {
                        frameDuration = convertFpsToMilliseconds("30fps");
                    }else {
                        frameDuration = convertFpsToMilliseconds(value);
                    }
                    break;

                case DURATION:
                    duration = convertSecondsStringToInt(value);
                    break;

                default:
                    // Configurazione non supportata
                    Toast.makeText(activity, "Unsupported camera configuration", Toast.LENGTH_SHORT).show();
                    return;
            }

            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance);
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeCompensation);
            builder.set(CaptureRequest.LENS_FOCAL_LENGTH, focalLength);
            //builder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration);
            // Applica la nuova configurazione alla sessione di cattura
            captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);

        } catch (CameraAccessException | NumberFormatException e) {
            Log.e("Camera", "Error updating camera preview: ", e);
            Toast.makeText(activity, "Error updating camera preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startPreview() {
        try {
            // Ottieni le superfici necessarie
            textureSurface = new Surface(textureView.getSurfaceTexture());
            imageReader = setupImageReader();
            reprocessableSurface = imageReader.getSurface();

            // Verifica se la fotocamera supporta YUV reprocessing
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(currentCameraId);
            int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean supportsReprocessing = false;

            if (capabilities != null) {
                for (int capability : capabilities) {
                    if (capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING) {
                        supportsReprocessing = true;
                        break;
                    }
                }
            }

            if (!supportsReprocessing) {
                Log.e("Camera", "The camera does not support YUV reprocessing.");
                return;
            }

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            InputConfiguration inputConfig = null;
            if (map != null) {
                int[] outputFormats = map.getOutputFormats();
                for (int format : outputFormats) {
                    Log.d("Camera", "Output format supported: " + format);
                }

                // Verifica i formati di input
                Size[] inputSizes = map.getInputSizes(ImageFormat.YUV_420_888); // Prova con YUV
                if (inputSizes != null) {
                    for (Size size : inputSizes) {
                        Log.d("Camera", "Supported input size for YUV: " + size.getWidth() + "x" + size.getHeight());
                    }
                } else {
                    Log.e("Camera", "No supported input sizes for YUV_420_888.");
                }

                // Crea la sessione di cattura riutilizzabile
                Size selectedSize = inputSizes[0]; // Dimensioni supportate
                inputConfig = new InputConfiguration(
                        selectedSize.getWidth(),
                        selectedSize.getHeight(),
                        ImageFormat.YUV_420_888
                );
            }

            cameraDevice.createReprocessableCaptureSession(
                    inputConfig,
                    Arrays.asList(textureSurface, reprocessableSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;

                            try {
                                // Configura la richiesta di anteprima
                                CaptureRequest.Builder previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                previewBuilder.addTarget(textureSurface);

                                previewRequest = previewBuilder.build();
                                captureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
                                setCurrentCameraSettings(previewRequest);
                                Log.d("Camera", "Reprocessable preview started successfully.");
                                parametersReady.postValue(currentSettings);
                            } catch (CameraAccessException e) {
                                Log.e("Camera", "Error starting reprocessable preview: ", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(activity, "Reprocessable preview configuration failed.", Toast.LENGTH_SHORT).show();
                        }
                    },
                    backgroundHandler
            );
        } catch (CameraAccessException e) {
            Log.e("Camera", "Error initializing reprocessable preview: ", e);
        }
    }

    private ScheduledExecutorService captureScheduler;

    private ExecutorService captureExecutorService;
    public void startCaptureCycle() {
        isCapturing = true;
        captureScheduler = Executors.newSingleThreadScheduledExecutor();

        // Ottieni la directory temporanea
        File tempDirectory = activity.getExternalFilesDir(null);

        // Pulisci la cartella temporanea prima di iniziare un nuovo ciclo
        if (tempDirectory != null) {
            clearTemporaryFolder(tempDirectory);
        }


        captureScheduler.scheduleWithFixedDelay(() -> {
            captureImage();
        }, 0, frameDuration, TimeUnit.MILLISECONDS);

        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Toast.makeText(activity, "Capture cycle started.", Toast.LENGTH_SHORT).show();

        // Avvia un timer per fermare l'acquisizione dopo il tempo specificato
        if (duration > 0) {
            new Handler().postDelayed(() -> stopCaptureCycle(), duration);
        }
    }

    private void captureImage() {
        if (captureSession == null || !isCapturing) {
            Log.d("Camera", "Capture session is not ready or isCapturing is false.");
            return;
        }

        try {
            // Configura la richiesta per l'acquisizione dell'immagine
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // Imposta l'orientamento dell'immagine
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            // Avvia l'acquisizione
            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    Log.d("Camera", "Image captured successfully in reprocessable session.");
                    handler.post(updateTimeRunnable);
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e("Camera", "Error capturing image in reprocessable session: ", e);
        }
    }

    public void stopCaptureCycle() {
        isCapturing = false;
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        captureScheduler.shutdownNow();
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.removeCallbacks(updateTimeRunnable);
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

        /*
        5,59 mm potrebbe essere l'obiettivo principale.
        1,8 mm potrebbe essere l'obiettivo ultra-grandangolare.
        10 mm potrebbe essere il teleobiettivo.
         */
        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if (focalLengths != null) {
            List<String> focalLengthsAsStrings = new ArrayList<>();

            for (float focalLength : focalLengths) {
                focalLengthsAsStrings.add(Float.toString(focalLength));
            }
            results.put(LENS_AVAILABLE_FOCAL_LENGTHS, focalLengthsAsStrings);
        }

        //Long maxFrameDuration = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
        //if (maxFrameDuration != null) {
            results.put(SENSOR_MAX_FRAME_DURATION, SENSOR_MAX_FRAME_DURATION_VALUES);
            //log.debug("Camera", "Max Frame Duration: " + maxFrameDuration + " ns");
        //}

        results.put(DURATION, DURATION_VALUES);


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


    private ImageReader setupImageReader() {
        // Crea un ImageReader per acquisire immagini in formato JPEG
        imageReader = ImageReader.newInstance(textureView.getWidth(), textureView.getHeight(), ImageFormat.JPEG, 2);
        imageReader.setOnImageAvailableListener(imageListener, backgroundHandler);

        return imageReader;
    }

    private final ImageReader.OnImageAvailableListener imageListener = reader -> {
        // Gestisci l'immagine catturata
        Image image = reader.acquireLatestImage();
        if (image != null) {
            // Salva l'immagine o esegui altre operazioni
            saveImage(image);
            image.close();
        }
    };

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

    private long convertToTimeLong(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Il valore fornito non può essere null o vuoto.");
        }

        value = value.trim().toLowerCase();

        if (value.endsWith("sec")) {
            // Rimuove "sec" e converte in secondi
            String secondsStr = value.replace("sec", "").trim();
            return Long.parseLong(secondsStr);
        } else if (value.endsWith("min")) {
            // Rimuove "min" e converte in minuti (moltiplicati per 60)
            String minutesStr = value.replace("min", "").trim();
            return Long.parseLong(minutesStr) * 60;
        } else {
            throw new IllegalArgumentException("Formato non valido. Usare 'Xsec' o 'Xmin'.");
        }
    }

    private int convertSecondsStringToInt(String timeString) {
        if ("Auto".equals(timeString)){
            return 0;
        }else if (timeString != null && timeString.endsWith("sec")) {
            try {
                // Rimuovi il suffisso "sec" e converte la parte numerica in intero
                String numericPart = timeString.replace("sec", "");
                return Integer.parseInt(numericPart);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Formato non valido: " + timeString);
            }
        } else {
            throw new IllegalArgumentException("La stringa deve terminare con 'sec': " + timeString);
        }
    }

    private long convertFpsToMilliseconds(String fpsValue) {
        try {
            // Extract numerical part of the fps value
            int fps = Integer.parseInt(fpsValue.replace("fps", ""));
            // Calculate and return milliseconds per frame as long
            return 1000L / fps;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid FPS value: " + fpsValue);
        }
    }

    public void invalidateCamera(){
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        if (textureSurface != null && textureSurface.isValid()) {
            textureSurface.release();
        }
        if (reprocessableSurface != null && reprocessableSurface.isValid()) {
            reprocessableSurface.release();
        }

        instance = null;
    }
}
