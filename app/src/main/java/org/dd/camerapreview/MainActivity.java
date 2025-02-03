package org.dd.camerapreview;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Camera2Manager camera2Manager;
    private boolean isCapturing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        camera2Manager = new Camera2Manager(this);

        // Initialize the camera preview
        camera2Manager.openCamera();

        // Switch camera button
        ImageButton switchCameraButton = findViewById(R.id.btn_switch_camera);
        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera2Manager.switchCamera();
            }
        });

        // Capture cycle button
        ImageButton captureButton = findViewById(R.id.btn_capture_cycle);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isCapturing) {
                    isCapturing = true;
                    camera2Manager.startCaptureCycle();
                    Toast.makeText(MainActivity.this, "Started capturing images.", Toast.LENGTH_SHORT).show();
                } else {
                    isCapturing = false;
                    camera2Manager.stopCaptureCycle();
                    Toast.makeText(MainActivity.this, "Stopped capturing. Creating video.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        camera2Manager.closeCamera();
    }
}
