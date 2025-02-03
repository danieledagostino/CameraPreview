package org.dd.camerapreview;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.*;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private List<String> capturedImagePaths = new ArrayList<>();

    public Camera2Manager(Activity activity) {
        this.activity = activity;
        textureView = activity.findViewById(R.id.camera_preview);
        cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    }

    public void openCamera() {
        startBackgroundThread();
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

    private void startPreview() {
        try {
            Surface surface = new Surface(textureView.getSurfaceTexture());
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        builder.addTarget(surface);
                        captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
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

    public void startCaptureCycle() {
        isCapturing = true;
        // Logic to start capturing images cyclically
        Toast.makeText(activity, "Capture cycle started.", Toast.LENGTH_SHORT).show();
    }

    public void stopCaptureCycle() {
        isCapturing = false;
        // Logic to stop capturing and process images into a video
        Toast.makeText(activity, "Capture cycle stopped.", Toast.LENGTH_SHORT).show();
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
            e.printStackTrace();
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
}
