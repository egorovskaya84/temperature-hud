package com.car.temperatureoverlay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Модуль отображения температуры (HUD-модуль).
 *
 * Хранит три параметра:
 *  1) температура в салоне;
 *  2) температура на улице;
 *  3) температура двигателя.
 *
 * Источник данных — CarTemperatureSource (реальные данные автомобиля через
 * android.car API). Если автомобильное API недоступно (например, приложение
 * запущено на обычном телефоне/эмуляторе), используется симуляция, чтобы
 * виджет всегда оставался работоспособным.
 */
public class TemperatureModule {

    /** Интерфейс слушателя изменений температур. */
    public interface OnTemperatureUpdateListener {
        void onTemperatureUpdate(float cabinTemp, float outsideTemp, float engineTemp);
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CarTemperatureSource carSource;

    private float cabinTemperature = Float.NaN;
    private float outsideTemperature = Float.NaN;
    private float engineTemperature = Float.NaN;

    private OnTemperatureUpdateListener listener;
    private boolean running = false;
    private long lastSimTick = 0L;

    /** Период обновления виджета, мс. */
    private static final long UPDATE_PERIOD_MS = 1000L;

    public TemperatureModule(Context context) {
        this.context = context.getApplicationContext();
        this.carSource = new CarTemperatureSource(this.context);
    }

    public void setOnTemperatureUpdateListener(OnTemperatureUpdateListener listener) {
        this.listener = listener;
    }

    /** Запускает цикл опроса температур. */
    public void start() {
        if (running) return;
        running = true;
        carSource.connect();
        handler.post(updateRunnable);
    }

    /** Останавливает цикл опроса и освобождает ресурсы. */
    public void stop() {
        running = false;
        handler.removeCallbacks(updateRunnable);
        carSource.disconnect();
    }

    public float getCabinTemperature()   { return cabinTemperature; }
    public float getOutsideTemperature() { return outsideTemperature; }
    public float getEngineTemperature()  { return engineTemperature; }

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            // 1. Пробуем получить реальные данные автомобиля
            Float cabin  = carSource.readCabinTemperature();
            Float outside = carSource.readOutsideTemperature();
            Float engine  = carSource.readEngineTemperature();

            // 2. Если данных нет — симулируем правдоподобные значения
            if (cabin == null)   cabin   = simulateCabin();
            if (outside == null) outside = simulateOutside();
            if (engine == null)  engine  = simulateEngine();

            cabinTemperature = cabin;
            outsideTemperature = outside;
            engineTemperature = engine;

            if (listener != null) {
                listener.onTemperatureUpdate(cabinTemperature, outsideTemperature, engineTemperature);
            }

            handler.postDelayed(this, UPDATE_PERIOD_MS);
        }
    };

    // ---------- Симуляция (fallback для устройств без Car API) ----------

    private float simulateCabin() {
        // Салон медленно сходится к ~22 °C с небольшими флуктуациями
        long t = System.currentTimeMillis() / 5000;
        return 22f + (float) Math.sin(t % 360 * Math.PI / 180.0) * 1.5f;
    }

    private float simulateOutside() {
        // Уличная температура "дрейфует" вокруг 5 °C
        long t = System.currentTimeMillis() / 10000;
        return 5f + (float) Math.cos(t % 360 * Math.PI / 180.0) * 4f;
    }

    private float simulateEngine() {
        // Двигатель прогревается от температуры улицы до рабочего ~90 °C
        if (lastSimTick == 0L) lastSimTick = System.currentTimeMillis();
        float warmupFraction = Math.min(1f,
                (System.currentTimeMillis() - lastSimTick) / 120000f); // 2 минуты прогрева
        float base = Float.isNaN(outsideTemperature) ? 5f : outsideTemperature;
        return base + (90f - base) * warmupFraction
                + (float) (Math.random() * 1.2 - 0.6);
    }
}
