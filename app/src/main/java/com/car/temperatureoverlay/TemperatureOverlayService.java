package com.car.temperatureoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

/**
 * Сервис-оверлей: показывает прозрачный модуль температур поверх всех приложений
 * на экране головного устройства автомобиля.
 *
 * Окно создаётся с типом TYPE_APPLICATION_OVERLAY и флагами, которые делают его
 * некликабельным (кроме перетаскивания) и не перехватывающим фокус — другие
 * приложения продолжают работать под виджетом.
 */
public class TemperatureOverlayService extends Service
        implements TemperatureModule.OnTemperatureUpdateListener {

    public static final String ACTION_START = "com.car.temperatureoverlay.START";
    public static final String ACTION_STOP  = "com.car.temperatureoverlay.STOP";

    private static final String CHANNEL_ID = "temperature_overlay_channel";
    private static final int NOTIFICATION_ID = 100;

    private WindowManager windowManager;
    private View overlayView;
    private TemperatureModule temperatureModule;

    private TextView tvCabin;
    private TextView tvOutside;
    private TextView tvEngine;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        showOverlay();

        // Запуск модуля получения температур
        if (temperatureModule == null) {
            temperatureModule = new TemperatureModule(this);
            temperatureModule.setOnTemperatureUpdateListener(this);
        }
        temperatureModule.start();

        return START_STICKY;
    }

    // ---------- Оверлей-окно ----------

    private void showOverlay() {
        if (overlayView != null) return;

        overlayView = LayoutInflater.from(new android.view.ContextThemeWrapper(
                this, R.style.Theme_TemperatureOverlay_Transparent))
                .inflate(R.layout.overlay_temperature, null);

        tvCabin  = overlayView.findViewById(R.id.tv_cabin_value);
        tvOutside = overlayView.findViewById(R.id.outside_value);
        tvEngine = overlayView.findViewById(R.id.engine_value);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Не перехватываем фокус; сами обрабатываем только касания по виджету
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = getPrefInt("overlay_x", 24);
        params.y = getPrefInt("overlay_y", 120);

        // Разрешаем перетаскивание виджета пальцем
        overlayView.setOnTouchListener(new DragListener(params));

        windowManager.addView(overlayView, params);
    }

    /** Слушатель для перемещения окна-оверлея. */
    private class DragListener implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private int initialX, initialY;
        private float touchStartX, touchStartY;

        DragListener(WindowManager.LayoutParams params) {
            this.params = params;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    touchStartX = event.getRawX();
                    touchStartY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = initialX + (int) (event.getRawX() - touchStartX);
                    params.y = initialY + (int) (event.getRawY() - touchStartY);
                    windowManager.updateViewLayout(overlayView, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    savePrefInt("overlay_x", params.x);
                    savePrefInt("overlay_y", params.y);
                    return true;
            }
            return false;
        }
    }

    // ---------- Обновление значений модулем ----------

    @Override
    public void onTemperatureUpdate(float cabinTemp, float outsideTemp, float engineTemp) {
        if (overlayView == null) return;
        tvCabin.setText(formatTemp(cabinTemp));
        tvOutside.setText(formatTemp(outsideTemp));
        tvEngine.setText(formatTemp(engineTemp));
    }

    private String formatTemp(float t) {
        if (Float.isNaN(t)) return "--°";
        return String.format("%.0f°", t);
    }

    // ---------- Уведомление foreground-сервиса ----------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, TemperatureOverlayService.class)
                .setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_running))
                .setSmallIcon(R.drawable.ic_thermo)
                .addAction(0, getString(R.string.stop_action), pi)
                .setOngoing(true)
                .build();
    }

    // ---------- Preferences (позиция окна) ----------

    private int getPrefInt(String key, int def) {
        return getSharedPreferences("overlay_prefs", MODE_PRIVATE)
                .getInt(key, def);
    }

    private void savePrefInt(String key, int value) {
        getSharedPreferences("overlay_prefs", MODE_PRIVATE)
                .edit().putInt(key, value).apply();
    }

    // ---------- Остановка ----------

    @Override
    public void onDestroy() {
        if (temperatureModule != null) {
            temperatureModule.stop();
            temperatureModule = null;
        }
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        super.onDestroy();
    }
}
