package org.dd.camerapreview;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.camera2.CameraAccessException;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.MobileAds;

import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Camera2Manager camera2Manager;
    private CameraConfigManager cameraConfigManager;
    private RulesManager rulesManager;
    private boolean isCapturing = false;
    private TextView timeTextView; // TextView per visualizzare il tempo del video

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timeTextView = findViewById(R.id.timeTextView);

        new Thread(
                () -> {
                    // Initialize the Google Mobile Ads SDK on a background thread.
                    MobileAds.initialize(this, initializationStatus -> {});
                })
                .start();

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

        // Riferimenti ai bottoni
        ImageButton switchCameraButton = findViewById(R.id.btn_switch_camera);
        ImageButton wideCameraButton = findViewById(R.id.btn_wide_camera);
        ImageButton standardCameraButton = findViewById(R.id.btn_standard_camera);
        ImageButton zoomCameraButton = findViewById(R.id.btn_zoom_camera);
        ImageButton captureButton = findViewById(R.id.btn_capture_cycle);

        // Listener per switch camera
        switchCameraButton.setOnClickListener(v -> camera2Manager.switchCamera());

        // Listener per wide camera
        wideCameraButton.setOnClickListener(v -> camera2Manager.selectCamera("wide"));

        // Listener per standard camera
        standardCameraButton.setOnClickListener(v -> camera2Manager.selectCamera("standard"));

        // Listener per zoom camera
        zoomCameraButton.setOnClickListener(v -> camera2Manager.selectCamera("zoom"));

        // Listener per il ciclo di cattura
        captureButton.setOnClickListener(v -> {
            if (!isCapturing) {
                isCapturing = true;
                camera2Manager.startCaptureCycle();
                captureButton.setImageResource(R.drawable.camera_on);
                rulesManager.hideAllRulers();
                timeTextView.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "Started capturing images.", Toast.LENGTH_SHORT).show();

                // Disabilita gli altri bottoni
                switchCameraButton.setEnabled(false);
                wideCameraButton.setEnabled(false);
                standardCameraButton.setEnabled(false);
                zoomCameraButton.setEnabled(false);

            } else {
                isCapturing = false;
                camera2Manager.stopCaptureCycle();
                captureButton.setImageResource(R.drawable.camera_off);
                Toast.makeText(MainActivity.this, "Stopped capturing. Creating video.", Toast.LENGTH_SHORT).show();

                timeTextView.setVisibility(View.GONE);
                // Riabilita gli altri bottoni
                switchCameraButton.setEnabled(true);
                wideCameraButton.setEnabled(true);
                standardCameraButton.setEnabled(true);
                zoomCameraButton.setEnabled(true);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        camera2Manager = Camera2Manager.getInstance(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera2Manager != null) {
            camera2Manager.invalidateCamera();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        camera2Manager.closeCamera();
        camera2Manager.openCameraAfterAD();
    }
}
