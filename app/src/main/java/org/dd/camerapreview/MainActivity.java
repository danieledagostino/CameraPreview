package org.dd.camerapreview;

import android.hardware.camera2.CameraAccessException;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Camera2Manager camera2Manager;
    private CameraConfigManager cameraConfigManager;
    private boolean isCapturing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            camera2Manager = new Camera2Manager(this);
            cameraConfigManager = new CameraConfigManager(this);

            Map<Integer, List> parameters = camera2Manager.getMinMaxCameraParameters();
            cameraConfigManager.insertConfig(parameters);
            RulesManager rulesManager = new RulesManager(this, parameters);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }


        // Switch camera button
        ImageButton switchCameraButton = findViewById(R.id.btn_switch_camera);
        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera2Manager.switchCamera();
            }
        });

        // Wide camera selection button
        ImageButton wideCameraButton = findViewById(R.id.btn_wide_camera);
        wideCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera2Manager.selectCamera("wide");
            }
        });

        // Standard camera selection button
        ImageButton standardCameraButton = findViewById(R.id.btn_standard_camera);
        standardCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera2Manager.selectCamera("standard");
            }
        });

        // Zoom camera selection button
        ImageButton zoomCameraButton = findViewById(R.id.btn_zoom_camera);
        zoomCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera2Manager.selectCamera("zoom");
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
    protected void onResume() {
        super.onResume();
        if (camera2Manager != null) {
            camera2Manager.closeCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera2Manager != null) {
            camera2Manager.closeCamera();
            camera2Manager.closeCamera();
        }
    }
}
