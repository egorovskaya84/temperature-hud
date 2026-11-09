# Температура HUD — модуль температур поверх всех приложений

Android-приложение для **головного устройства автомобиля** (Android Automotive / любой
Android-девайс в машине), которое отображает прозрачный HUD-виджет **поверх всех других
приложений** с тремя параметрами:

1. Температура в салоне
2. Температура на улице
3. Температура двигателя

## Как это работает

| Компонент | Назначение |
|---|---|
| `MainActivity` | Экран управления: запрос разрешения `SYSTEM_ALERT_WINDOW`, кнопки «Запустить/Остановить виджет» |
| `TemperatureOverlayService` | Foreground-сервис, создающий окно `TYPE_APPLICATION_OVERLAY` через `WindowManager` — оно рисуется поверх всех приложений. Виджет можно перетаскивать пальцем, позиция сохраняется |
| `TemperatureModule` | Сам модуль: цикл опроса (1 сек), хранит 3 температуры, отдаёт их слушателю для отрисовки |
| `CarTemperatureSource` | Чтение реальных данных автомобиля через Android Automotive API (`android.car.Car` → `CarPropertyManager`): `HVAC_TEMPERATURE_CURRENT` (салон), `AMBIENT_TEMP` (улица), `ENGINE_OIL_TEMP` (двигатель). Доступ идёт через reflection, поэтому APK устанавливается и на обычные устройства; если Car API нет — включается симуляция правдоподобных значений |

## Разрешения

- `android.permission.SYSTEM_ALERT_WINDOW` — показ поверх других приложений
  (выдаётся пользователем на экране настроек приложения);
- `android.car.permission.CAR_TEMPERATURE`, `CAR_EXTERIOR_ENVIRONMENT`,
  `CAR_ENGINE_DETAILED_STATUS`, `CAR_POWERTRAIN` — чтение климатических и
  силовых свойств VHAL (только на Android Automotive, игнорируются на обычных устройствах).

## Сборка

Требования: JDK 17, Android SDK 34.

```bash
./gradlew assembleDebug        # если есть gradle-wrapper
# либо откройте проект в Android Studio и нажмите Run
```

Установка на головное устройство:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

После запуска приложения:
1. Нажмите «Разрешить показ поверх приложений» → включите разрешение в системных настройках;
2. Нажмите «Запустить виджет» — модуль температур появится поверх навигации/музыки/любого другого приложения.

> Примечание: на не-автомобильных устройствах (телефон, эмулятор) вместо реальных
> данных отображается симуляция: салон ≈22 °C, улица дрейфует вокруг 5 °C,
> двигатель прогревается до ~90 °C за 2 минуты.

## Структура проекта

```
app/src/main/
├── AndroidManifest.xml
├── java/com/car/temperatureoverlay/
│   ├── MainActivity.java               # экран управления
│   ├── TemperatureOverlayService.java  # оверлей-сервис (окно поверх всех)
│   ├── TemperatureModule.java          # модуль трёх температур
│   └── CarTemperatureSource.java       # источник данных авто (VHAL) / fallback
└── res/
    ├── layout/activity_main.xml
    ├── layout/overlay_temperature.xml  # разметка HUD-виджета
    ├── drawable/ ...                   # иконки и фон
    └── values/ strings, colors, themes
```
