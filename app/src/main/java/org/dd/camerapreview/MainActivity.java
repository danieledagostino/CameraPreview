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
    private RulesManager rulesManager;
    private boolean isCapturing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            camera2Manager = Camera2Manager.getInstance(this);
            cameraConfigManager = CameraConfigManager.getInstance(this);

            Map<Integer, List<String>> parameters = camera2Manager.getMinMaxCameraParameters();
            cameraConfigManager.insertConfig(parameters);
            camera2Manager.isPreviewConfigured().observe(this, currentCameraConfigs -> {
                if (currentCameraConfigs != null) {
                    rulesManager = new RulesManager(this, parameters, currentCameraConfigs);
                }
            });


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
                    captureButton.setImageResource(R.drawable.camera_on);
                    rulesManager.hideAllRulers();
                    Toast.makeText(MainActivity.this, "Started capturing images.", Toast.LENGTH_SHORT).show();
                } else {
                    isCapturing = false;
                    camera2Manager.stopCaptureCycle();
                    captureButton.setImageResource(R.drawable.camera_off);
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

        camera2Manager = Camera2Manager.getInstance(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera2Manager != null) {
            camera2Manager.closeCamera();
        }

        camera2Manager = Camera2Manager.getInstance(this);
    }
}
