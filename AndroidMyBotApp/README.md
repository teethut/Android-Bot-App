# TWIP Robot Android Controller (Jetpack Compose)

Modern, high-performance Android control dashboard built with **Jetpack Compose**, **Kotlin Coroutines & Flow**, **OkHttp WebSockets**, and **Clean Architecture** to drive and tune the ESP32 Two-Wheeled Inverted Pendulum (TWIP) balancing robot.

---

## 🚀 Key Features

1. **Isometric 3D Chassis Attitude Visualizer (`DriveScreen`)**:
   - Real-time mathematical 3D wireframe projection on Compose Canvas rotating dynamically with robot pitch.
   - Ground plane reference grid, axle, rotating wheels, vertical standoffs, and top brain beacon.
   - Dynamic alert coloring: Vivid Cyan when `BALANCING` vs. Red alert when `FALLEN`.

2. **Dual Drive Modes (`DriveScreen`)**:
   - **Virtual Touch Joystick**: 2D Canvas joystick pad with guide rings, sensitivity slider (10% to 100%), auto-return-to-center failsafe zeroing, and emergency STOP.
   - **Phone Tilt Driving (Gyroscopic/Motion Control)**: Steer and throttle the robot by tilting your phone forward/back and left/right. Secured with a **Hold-to-Drive Safety Button** (deadman switch) that halts motion the instant you let go.

3. **Phone-Mounted AI Vision Person Tracker Mini-App (`TrackerScreen`)**:
   - Mount the smartphone directly on the robot chassis with the front camera pointing forward.
   - Real-time offline **Google ML Kit Face & Person Tracking** pipeline (100% offline, requires zero internet).
   - Futuristic **HUD Cockpit Overlay** displaying target lock bounding brackets, crosshairs, steering vector arrow, pitch horizon guide, and distance category.
   - **Dual Tracking Modes**: *Pan Only (Turret)* (rotates on the spot to face person) vs. *Full Follow* (steers to center + throttles to maintain distance).
   - **Safety Failsafes**: Auto-stop on target loss (>500ms), immediate emergency halt on robot fall/tilt (`state == "FALLEN"`), Keep Screen On flag, and high-visibility E-STOP.

4. **Telemetry Session CSV Recorder & Exporter (`TelemetryScreen`)**:
   - Dedicated session recorder card with "Start Record" / "Stop" button, live elapsed duration timer (`MM:SS`), and recorded sample point counter.
   - One-tap "Export CSV" creating standard CSV files (`twip_telemetry_<timestamp>.csv`) shared directly via the native Android Share Sheet (`FileProvider` + `Intent.ACTION_SEND`) to Google Drive, Downloads, Gmail, or Slack.

4. **Robot Fall Alarm Haptic Feedback**:
   - Monitors state transitions and triggers a distinct double-pulse vibration alarm on the phone the moment the robot tips over (`BALANCING` → `FALLEN`).

4. **Local Data Persistence (`HistoryScreen`)**:
   - **Saved PID Profiles**: Persist tuning profiles per floor surface (e.g. Carpet, Hardwood, Concrete) in local SQLite database via reactive Kotlin `Flow`s. Includes pre-seeded defaults, custom profile creation, and one-tap "Apply to Robot".
   - **Run History Sessions**: Persists telemetry run metadata (run duration, sample count, max speed achieved, and CSV payload) with direct Share Sheet export.

5. **Real-Time Oscilloscope Waveform Scopes (`TelemetryScreen`)**:
   - Hardware-accelerated Compose Canvas rolling scope waveforms.
   - **Scope 1**: Pitch Angle (°) vs. Target Angle Setpoint (°).
   - **Scope 2**: Estimated Speed (steps/s) vs. Target Speed (steps/s).
   - **Scope 3**: Motor Output (steps/s).
   - **Scope 4**: Virtual Encoders (Left vs. Right counts).
   - Pause / Resume & Clear chart history.

6. **Live PID & System Tuning (`TuningScreen`)**:
   - **Inner Loop (Balance)**: `Kp1`, `Ki1`, `Kd1`.
   - **Outer Loop (Speed)**: `Kp2`, `Ki2`, `Kd2`.
   - **System Limits**: Angle Offset (`°`), Max Acceleration, Max Steps/Sec.
   - **Read Current**: Live fetch from ESP32 RAM via HTTP GET `/get_pid`.
   - **Update Settings**: Writes new parameters live to ESP32 RAM via HTTP GET `/set_pid?...`.

7. **Connection Management**:
   - Configurable IP (default `192.168.4.1`) & Port (default `80`).
   - Live Connection Badge (`ONLINE`, `CONNECTING`, `OFFLINE`).
   - Balancing Status Badge (`BALANCING` vs. `FALLEN`).
   - Real-time ESP32 Free Heap memory monitor.

---

## 🎓 Course Requirements Mapping (Android Basics with Compose)

1. **Multiple Screens**:
   - Implemented using Jetpack Compose Navigation (`androidx.navigation.compose.NavHost`) with 4 distinct top-level destinations: `Drive`, `Scopes`, `Tuning`, and `History`.
   - Persistent Bottom Navigation Bar with deep state preservation across tab switches.

2. **Data Persistence**:
   - Implemented using Android's native SQLite storage engine (`RobotDatabaseHelper`) adhering to Clean Architecture repository pattern (`PresetHistoryRepository`).
   - Stores custom PID surface profiles and recorded telemetry runs with reactive Kotlin `Flow` notifications.

3. **External Connectivity**:
   - Bidirectional high-speed asynchronous network communication using **OkHttp WebSockets** (`/ws`) at 20 Hz and **OkHttp REST API** (`/get_pid`, `/set_pid`).
   - Executed using **Kotlin Coroutines** (`viewModelScope`, `Dispatchers.IO`) with structured concurrency and error handling.

---

## 🛠️ Architecture

The app follows **Clean Architecture** principles and **Unidirectional Data Flow (UDF)**:

```
app/src/main/java/com/teethut/twiprobot/
├── domain/
│   ├── model/
│   │   ├── RobotTelemetry.kt
│   │   ├── TelemetryLogEntry.kt
│   │   ├── DriveMode.kt               # JOYSTICK vs. PHONE_TILT
│   │   ├── PidSettings.kt
│   │   └── ConnectionState.kt
│   └── repository/
│       ├── RobotRepository.kt
│       └── PresetHistoryRepository.kt
├── data/
│   ├── local/
│   │   ├── database/RobotDatabaseHelper.kt   # SQLite persistence engine
│   │   └── entity/
│   │       ├── PidPresetEntity.kt
│   │       └── TelemetrySessionEntity.kt
│   ├── remote/
│   │   ├── RobotWebSocketClient.kt    # OkHttp WebSocket client & telemetry parser
│   │   └── RobotRestApi.kt            # HTTP GET endpoints (/get_pid, /set_pid)
│   ├── sensor/
│   │   └── PhoneOrientationSensor.kt  # Accelerometer/RotationVector tilt detector
│   ├── haptic/
│   │   └── HapticManager.kt           # Fall alarm vibration manager
│   ├── logging/
│   │   └── TelemetryCsvExporter.kt    # FileProvider CSV Share Intent generator
│   └── repository/
│       ├── RobotRepositoryImpl.kt
│       └── PresetHistoryRepositoryImpl.kt
└── presentation/
    ├── components/
    │   ├── IsometricChassisVisualizer.kt # 3D wireframe canvas attitude visualizer
    │   ├── VirtualJoystick.kt         # Custom 2D Canvas Joystick
    │   ├── TiltDriveControl.kt        # Hold-to-Drive safety deadman switch
    │   ├── WaveformChart.kt           # Real-time oscilloscope plot
    │   ├── TelemetryCard.kt           # Numeric metric cards
    │   └── TelemetryBadge.kt          # Status and balancing pills
    ├── drive/
    │   ├── DriveScreen.kt
    │   └── DriveViewModel.kt
    ├── telemetry/
    │   ├── TelemetryScreen.kt
    │   └── TelemetryViewModel.kt
    ├── tuning/
    │   ├── TuningScreen.kt
    │   └── TuningViewModel.kt
    ├── history/
    │   ├── HistoryScreen.kt           # Local database viewer & manager
    │   └── HistoryViewModel.kt
    ├── settings/
    │   └── ConnectionDialog.kt
    ├── MainScreen.kt                  # Root scaffold with bottom navigation
    └── MainActivity.kt
```

---

## 📶 Connecting to the Robot

1. Power on the TWIP robot.
2. On your Android phone, connect to the robot's Wi-Fi network:
   - **SSID**: `TeethutBOT_AP`
   - **Password**: `12345678`
   - **Default IP**: `192.168.4.1`
3. Open the app and tap the Wi-Fi icon in the top bar to connect.

---

## 📦 Building & Testing

- **Run Unit Tests**:
  ```bash
  ./gradlew test
  ```
- **Compile Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Install to connected device via adb**:
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
- The built APK is ready at:
  `app/build/outputs/apk/debug/app-debug.apk`
