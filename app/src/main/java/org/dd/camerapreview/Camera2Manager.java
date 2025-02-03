package org.dd.camerapreview;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
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
}
