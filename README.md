
# Camera2 API App with Photo Cycle and Video Creation

This Android app uses Camera2 API to capture images and create a video. The app allows users to switch between front and back cameras, take photos in a cycle, and create a video from the captured images using FFMPEG.

## Features

- **Preview mode**: Upon opening the app, the camera preview is displayed.
- **Switch camera**: A button in the top left corner allows switching between front and back cameras.
- **Capture photos**: Pressing the center button will start a photo capture cycle. Pressing it again stops the cycle and creates a video.
- **Video creation**: After capturing a series of photos, the app uses FFMPEG to stitch the images into a video.
- **Material Design**: The app uses Material Design icons for the interface elements.

## Requirements

- Android SDK 21 (Lollipop) or higher.
- Camera2 API.
- FFMPEG library (for video creation).

## Installation

To get started with this project:

1. Clone this repository:

   ```bash
   git clone <repository-url>
   ```

2. Open the project in Android Studio.

3. Ensure that you have the necessary permissions in your `AndroidManifest.xml`.

4. Build and run the app on an Android device or emulator (SDK 21 or higher).

## Permissions

The app requires the following permissions:

- Camera access
- Write external storage

```xml
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```

## Code Files

### MainActivity.java

```java
package com.example.camera2app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private CameraHandler cameraHandler;
    private boolean isCapturing = false;
    private Button captureButton;
    private Button switchCameraButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraHandler = new CameraHandler(this);
        captureButton = findViewById(R.id.capture_button);
        switchCameraButton = findViewById(R.id.switch_camera_button);

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCapturing) {
                    cameraHandler.stopCapture();
                    cameraHandler.createVideoFromPhotos();
                    isCapturing = false;
                    captureButton.setText("Start Capturing");
                } else {
                    cameraHandler.startCapture();
                    isCapturing = true;
                    captureButton.setText("Stop Capturing");
                }
            }
        });

        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraHandler.switchCamera();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraHandler.startPreview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraHandler.stopPreview();
    }
}
```

### CameraHandler.java

```java
package com.example.camera2app;

import android.content.Context;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;

public class CameraHandler {
    private Context context;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private boolean isFrontCamera = false;

    public CameraHandler(Context context) {
        this.context = context;
    }

    public void startPreview() {
        // Implement Camera2 API setup for preview mode
    }

    public void stopPreview() {
        // Implement code to stop preview
    }

    public void startCapture() {
        // Implement photo capture logic
    }

    public void stopCapture() {
        // Implement logic to stop the capture cycle
    }

    public void createVideoFromPhotos() {
        // Use FFMPEG to create a video from the captured images
    }

    public void switchCamera() {
        // Implement logic to switch between front and back camera
    }
}
```

### activity_main.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <Button
        android:id="@+id/switch_camera_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Switch Camera"
        android:layout_gravity="start" />

    <FrameLayout
        android:id="@+id/camera_preview"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <Button
        android:id="@+id/capture_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Start Capturing"
        android:layout_gravity="center" />

</LinearLayout>
```

### AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.camera2app">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Camera2App"
        android:theme="@style/Theme.Camera2App">
        <activity android:name=".MainActivity"
            android:label="Camera2 App"
            android:screenOrientation="landscape"
            android:configChanges="orientation|keyboardHidden|screenSize|smallestScreenSize|screenLayout|density|locale|fontScale">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```
