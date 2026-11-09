package com.car.temperatureoverlay;

import android.content.Context;

/**
 * Обёртка над Android Automotive API (android.car.Car / CarPropertyManager).
 *
 * Класс изолирует работу с автомобильными property-ID в одном месте и безопасно
 * деградирует (возвращает null), если:
 *  - устройство не является головным устройством автомобиля (нет car API);
 *  - нет прав на чтение соответствующих свойств.
 *
 * Используемые свойства VHAL:
 *  - HVAC_TEMPERATURE_CURRENT   — текущая температура в салоне (зона водителя);
 *  - AMBIENT_TEMP               — температура наружного воздуха;
 *  - ENGINE_OIL_TEMP            — температура двигателя (моторного масла).
 */
public class CarTemperatureSource {

    private final Context context;
    private Object carManager;          // android.car.Car (через reflection, чтобы APK ставился и на обычные устройства)
    private Object propertyManager;     // android.car.hardware.property.CarPropertyManager
    private boolean available = false;

    // Идентификаторы свойств VHAL (VehicleProperty IDs, стабильны с Android 10)
    private static final int HVAC_TEMPERATURE_CURRENT = 0x214000A4; // ID_HVAC_TEMPERATURE_CURRENT
    private static final int AMBIENT_TEMP             = 0x214000C3; // ID_AMBIENT_TEMP
    private static final int ENGINE_OIL_TEMP          = 0x21400106; // ID_ENGINE_OIL_TEMP

    public CarTemperatureSource(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Пытается подключиться к Car-сервису головного устройства. */
    public void connect() {
        if (available) return;
        try {
            // Class.forName бросит ClassNotFoundException на обычных Android-устройствах
            Class<?> carClass = Class.forName("android.car.Car");
            Object car = carClass.getMethod("createCar", Context.class)
                    .invoke(null, context);
            if (car == null) return;

            carClass.getMethod("connect").invoke(car);

            Object pm = carClass.getMethod("getCarManager", String.class)
                    .invoke(car, "property"); // Car.PROPERTY_SERVICE
            if (pm != null) {
                this.carManager = car;
                this.propertyManager = pm;
                this.available = true;
            }
        } catch (Throwable t) {
            // Car API недоступен — остаёмся в режиме симуляции
            available = false;
        }
    }

    public void disconnect() {
        try {
            if (carManager != null) {
                carManager.getClass().getMethod("disconnect").invoke(carManager);
            }
        } catch (Throwable ignored) {
        }
        carManager = null;
        propertyManager = null;
        available = false;
    }

    public Float readCabinTemperature()   { return readProperty(HVAC_TEMPERATURE_CURRENT); }
    public Float readOutsideTemperature() { return readProperty(AMBIENT_TEMP); }
    public Float readEngineTemperature()  { return readProperty(ENGINE_OIL_TEMP); }

    /**
     * Читает float-свойство VHAL через CarPropertyManager.getPropertyValue(int).
     * @return значение или null, если недоступно.
     */
    private Float readProperty(int propertyId) {
        if (!available || propertyManager == null) return null;
        try {
            Object value = propertyManager.getClass()
                    .getMethod("getPropertyValue", int.class)
                    .invoke(propertyManager, propertyId);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
        } catch (Throwable ignored) {
            // Нет доступа / свойство не поддерживается данным авто
        }
        return null;
    }

    public boolean isAvailable() {
        return available;
    }
}
