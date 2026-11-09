package com.car.temperatureoverlay;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Главный экран приложения: запрос разрешения на показ поверх других приложений
 * и кнопки запуска/остановки HUD-модуля температур.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.text_status);
        Button btnPermission = findViewById(R.id.btn_permission);
        Button btnStart = findViewById(R.id.btn_start);
        Button btnStop = findViewById(R.id.btn_stop);

        btnPermission.setOnClickListener(v -> requestOverlayPermission());
        btnStart.setOnClickListener(v -> startOverlayService());
        btnStop.setOnClickListener(v -> stopOverlayService());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean granted = Settings.canDrawOverlays(this);
        statusText.setText(granted
                ? R.string.status_permission_granted
                : R.string.status_permission_missing);
        findViewById(R.id.btn_start).setEnabled(granted);
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.status_permission_granted, Toast.LENGTH_SHORT).show();
            return;
        }
        // Открываем системный экран выдачи разрешения "Поверх других приложений"
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
    }

    private void startOverlayService() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        Intent serviceIntent = new Intent(this, TemperatureOverlayService.class)
                .setAction(TemperatureOverlayService.ACTION_START);
        startForegroundService(serviceIntent);
        Toast.makeText(this, R.string.overlay_started, Toast.LENGTH_SHORT).show();
    }

    private void stopOverlayService() {
        Intent serviceIntent = new Intent(this, TemperatureOverlayService.class)
                .setAction(TemperatureOverlayService.ACTION_STOP);
        startService(serviceIntent);
        Toast.makeText(this, R.string.overlay_stopped, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            updateStatus();
        }
    }
}
