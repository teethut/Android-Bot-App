// ============================================================
// File: twip_v3.ino
// ============================================================
#include <Arduino.h>
#include <WiFi.h>
#include <Wire.h>
#include <AsyncTCP.h>
#include <ESPAsyncWebServer.h>
#include <ArduinoJson.h>
#include <Kalman.h>
#include <FastAccelStepper.h>

#include <esp_arduino_version.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <freertos/queue.h>
#include <freertos/semphr.h>

#include <atomic>
#include <math.h>
#include <stdint.h>
#include "web_page.h"

#if !defined(CONFIG_IDF_TARGET_ESP32)
#error "This sketch targets the classic ESP32, not C3/S3/etc."
#endif

#if ESP_ARDUINO_VERSION_MAJOR < 3
#error "Use Arduino-ESP32 3.x."
#endif

#if ARDUINOJSON_VERSION_MAJOR != 7
#error "Use ArduinoJson 7."
#endif

static_assert(configTICK_RATE_HZ == 1000,
              "Control timing assumes a 1 kHz FreeRTOS tick.");

// ============================================================
// CONFIGURATION
// ============================================================

constexpr char BUILD_ID[] = "twip-mpu6500-v3-fas";

constexpr char AP_SSID[] = "TWIP_Robot";
constexpr char AP_PASSWORD[] = "ChangeMe123!";   // Change before use.

constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;

constexpr int STEP_L   = 14;
constexpr int DIR_L    = 27;
constexpr int ENABLE_L = 16;  // Optional ACTIVE-LOW left driver enable. Leave -1 unless verified.

constexpr int STEP_R   = 26;
constexpr int DIR_R    = 25;
constexpr int ENABLE_R = 17;  // Optional ACTIVE-LOW right driver enable. Leave -1 unless verified.

// true  = DIR high means "forward" for that wheel.
constexpr bool LEFT_FORWARD_HIGH  = true;
constexpr bool RIGHT_FORWARD_HIGH = true;

constexpr uint8_t MPU_ADDR = 0x68;          // AD0 low
constexpr float ACCEL_SCALE = 16384.0f;     // ±2 g
constexpr float GYRO_SCALE  = 16.4f;        // ±2000 °/s

constexpr uint32_t CONTROL_MS = 5;          // 200 Hz
constexpr uint32_t CONTROL_US = CONTROL_MS * 1000;
constexpr uint32_t MAX_CONTROL_GAP_US = 15000;
constexpr uint32_t IMU_STALE_US = 15000;
constexpr uint32_t MOTOR_LEASE_US = 20000;  // Watchdog stop after this

constexpr float MAX_STEP_RATE = 10000.0f;
constexpr float MIN_STEP_RATE = 2.0f;       // Below this: decelerate to stop
constexpr float MAX_ACCEL = 55000.0f;       // Controller slew limit
constexpr uint32_t LIBRARY_ACCEL = uint32_t(MAX_ACCEL * 1.25f);

constexpr float MAX_DRIVE = 1800.0f;
constexpr float MAX_STEER = 1000.0f;

constexpr uint32_t DRIVE_TIMEOUT_MS = 500;
constexpr uint32_t COMMAND_MAX_AGE_MS = 500;

constexpr float FALL_ANGLE_DEG = 45.0f;
constexpr float ARM_ANGLE_DEG = 5.0f;
constexpr float ARM_RATE_DPS = 10.0f;
constexpr uint32_t UPRIGHT_HOLD_MS = 1000;

constexpr float MAX_LEAN_DEG = 8.0f;
constexpr float ANGLE_OUTPUT_SCALE = 20.0f;

// Original gains: rough starting point only. Retune.
float gains[6] = {
  50.0f, 0.5f, 0.4f,
  -0.002f, 0.0f, 0.00006f
};

constexpr float GAIN_LIMIT[6] = {
  300.0f, 100.0f, 100.0f,
  0.1f, 0.1f, 0.1f
};

constexpr float OFFSET_LIMIT_DEG = 15.0f;

float angleOffset = 0.0f;
bool speedLoopEnabled = false;

// ============================================================
// TYPES
// ============================================================

enum class RunState : uint8_t { DISARMED, ARMED, FAULT };

enum class Fault : uint8_t {
  NONE, IMU_READ, IMU_STALE, TILT, DEADLINE,
  INVALID_NUMBER, PULSE_TIMEOUT, MOTOR_LOCK
};

enum class Operation : uint8_t { GET_PID, SET_PID, SET_OFFSET, ARM, DISARM };

struct ImuSample {
  int16_t ax, ay, az;
  int16_t temperature;
  int16_t gx, gy, gz;
};

struct DriveRequest {
  uint32_t client;
  uint32_t receivedMs;
  float speed;
  float steer;
};

struct Command {
  uint32_t client;
  uint32_t id;
  uint32_t receivedMs;
  Operation operation;
  float values[6];
  bool speedLoop;
  float offset;
};

struct Reply {
  uint32_t client;
  uint32_t id;
  bool ok;
  char message[112];
  float values[6];
  bool speedLoop;
  float offset;
  RunState state;
  Fault fault;
};

struct Telemetry {
  uint32_t sequence, ms, imuMs;
  uint32_t periodUs, executionUs, maximumPeriodUs, latePeriods;
  uint32_t imuFailures, noNewSamples;
  uint32_t histogram[5];

  bool haveImu, imuReadOk, newImu, uprightReady, speedLoop;
  bool angleLimited, speedLimited, motorLeaseExpired;

  RunState state;
  Fault fault;

  float pitch, accelPitch, gyroRate, temperatureC;
  float angleTarget, speedTarget, speedEstimate;
  float motorRaw, motorApplied, steering;
  float leftRate, rightRate, leftActual, rightActual;
  float angleP, angleI, angleD, speedP, speedI, speedD;
  float values[6];
  float angleOffsetValue;
  int16_t raw[6];
};

// ============================================================
// GLOBAL STATE
// ============================================================

RunState runState = RunState::DISARMED;   // Control-task owned
Fault faultCode = Fault::NONE;

QueueHandle_t telemetryQueue = nullptr;
QueueHandle_t driveQueue = nullptr;
QueueHandle_t commandQueue = nullptr;
QueueHandle_t replyQueue = nullptr;

TaskHandle_t controlTaskHandle = nullptr;
TaskHandle_t watchdogTaskHandle = nullptr;

AsyncWebServer server(80);
AsyncWebSocket socket("/ws");

portMUX_TYPE ownerMux = portMUX_INITIALIZER_UNLOCKED;
uint32_t operatorId = 0;

Kalman angleFilter;
float gyroBias[3] = {};
bool filterSeeded = false;

// Controller state (control task only)
float pitch = 0, accelPitch = 0, correctedGyro = 0, filteredGyro = 0;
float driveTarget = 0, steerTarget = 0;
float speedEstimate = 0, angleTarget = 0;
float angleIntegral = 0, speedIntegral = 0, lastSpeed = 0;
float motorRaw = 0, motorApplied = 0, leftRate = 0, rightRate = 0;
float angleP = 0, angleI = 0, angleD = 0;
float speedP = 0, speedI = 0, speedD = 0;
bool angleLimited = false, speedLimited = false;

uint32_t lastDriveMs = 0;
uint32_t lastGoodImuUs = 0, lastGoodImuMs = 0;
bool haveGoodImu = false;

uint32_t uprightSinceMs = 0;
bool uprightTracking = false, uprightReady = false;

float speedElapsed = 0;
int32_t previousPosL = 0, previousPosR = 0;

// ============================================================
// HELPERS
// ============================================================

float clampFloat(float x, float low, float high) {
  return fminf(high, fmaxf(low, x));
}

const char* stateName(RunState s) {
  switch (s) {
    case RunState::DISARMED: return "DISARMED";
    case RunState::ARMED:    return "ARMED";
    case RunState::FAULT:    return "FAULT";
  }
  return "UNKNOWN";
}

const char* faultName(Fault f) {
  switch (f) {
    case Fault::NONE:           return "NONE";
    case Fault::IMU_READ:       return "IMU read failed";
    case Fault::IMU_STALE:      return "IMU data stale";
    case Fault::TILT:           return "Excessive tilt";
    case Fault::DEADLINE:       return "Control deadline missed";
    case Fault::INVALID_NUMBER: return "Invalid numeric state";
    case Fault::PULSE_TIMEOUT:  return "Motor watchdog expired";
    case Fault::MOTOR_LOCK:     return "Motor interface busy";
  }
  return "UNKNOWN";
}

uint32_t currentOperator() {
  portENTER_CRITICAL(&ownerMux);
  uint32_t id = operatorId;
  portEXIT_CRITICAL(&ownerMux);
  return id;
}

void fatalStartup(const char* message) {
  Serial.println(message);
  if (ENABLE_L >= 0) digitalWrite(ENABLE_L, HIGH);
  if (ENABLE_R >= 0) digitalWrite(ENABLE_R, HIGH);
  while (true) delay(1000);   // Halts before the controller starts.
}

// ============================================================
// MOTORS: FastAccelStepper + independent watchdog
// ============================================================

FastAccelStepperEngine engine;
FastAccelStepper* motorL = nullptr;
FastAccelStepper* motorR = nullptr;
SemaphoreHandle_t motorMutex = nullptr;

std::atomic<bool> motorsLive{false};
std::atomic<bool> leaseExpired{false};
std::atomic<uint32_t> leaseRenewedUs{0};

bool beginMotors() {
  motorMutex = xSemaphoreCreateMutex();
  if (!motorMutex) return false;

  engine.init();

  motorL = engine.stepperConnectToPin(STEP_L);
  motorR = engine.stepperConnectToPin(STEP_R);
  if (!motorL || !motorR) return false;

  motorL->setDirectionPin(DIR_L, LEFT_FORWARD_HIGH);
  motorR->setDirectionPin(DIR_R, RIGHT_FORWARD_HIGH);

  motorL->setAcceleration(LIBRARY_ACCEL);
  motorR->setAcceleration(LIBRARY_ACCEL);
  return true;
}

void forceStopLocked() {
  motorL->forceStop();
  motorR->forceStop();
}

void motorsStop() {
  motorsLive = false;
  if (ENABLE_L >= 0) digitalWrite(ENABLE_L, HIGH);
  if (ENABLE_R >= 0) digitalWrite(ENABLE_R, HIGH);

  if (xSemaphoreTake(motorMutex, pdMS_TO_TICKS(10)) == pdTRUE) {
    forceStopLocked();
    xSemaphoreGive(motorMutex);
  }
}

void motorsArm() {
  leaseExpired = false;
  leaseRenewedUs = micros();
  if (ENABLE_L >= 0) digitalWrite(ENABLE_L, LOW);
  if (ENABLE_R >= 0) digitalWrite(ENABLE_R, LOW);
  motorsLive = true;
}

void driveOneLocked(FastAccelStepper* motor, float rate) {
  const float magnitude = fabsf(rate);
  if (magnitude < MIN_STEP_RATE) {
    motor->stopMove();
    return;
  }
  motor->setSpeedInHz(uint32_t(lroundf(magnitude)));
  if (rate > 0) motor->runForward();
  else          motor->runBackward();
}

bool motorsSetRates(float left, float right) {
  if (!motorsLive || leaseExpired) return false;

  if (xSemaphoreTake(motorMutex, pdMS_TO_TICKS(2)) != pdTRUE) {
    return false;
  }

  bool ok = motorsLive && !leaseExpired;
  if (ok) {
    driveOneLocked(motorL, clampFloat(left, -MAX_STEP_RATE, MAX_STEP_RATE));
    driveOneLocked(motorR, clampFloat(right, -MAX_STEP_RATE, MAX_STEP_RATE));
    leaseRenewedUs = micros();
  }

  xSemaphoreGive(motorMutex);
  return ok;
}

bool motorsSnapshot(int32_t& posL, int32_t& posR,
                    float& actualL, float& actualR) {
  if (xSemaphoreTake(motorMutex, pdMS_TO_TICKS(2)) != pdTRUE) {
    return false;
  }

  posL = motorL->getCurrentPosition();
  posR = motorR->getCurrentPosition();
  actualL = motorL->getCurrentSpeedInMilliHz() / 1000.0f;
  actualR = motorR->getCurrentSpeedInMilliHz() / 1000.0f;

  xSemaphoreGive(motorMutex);
  return true;
}

void motorWatchdogTask(void*) {
  TickType_t wake = xTaskGetTickCount();

  for (;;) {
    vTaskDelayUntil(&wake, pdMS_TO_TICKS(5));
    if (!motorsLive) continue;

    if (xSemaphoreTake(motorMutex, pdMS_TO_TICKS(5)) == pdTRUE) {
      if (motorsLive &&
          uint32_t(micros() - leaseRenewedUs) > MOTOR_LEASE_US) {
        leaseExpired = true;
        motorsLive = false;
        if (ENABLE_L >= 0) digitalWrite(ENABLE_L, HIGH);
        if (ENABLE_R >= 0) digitalWrite(ENABLE_R, HIGH);
        forceStopLocked();
      }
      xSemaphoreGive(motorMutex);
    } else {
      leaseExpired = true;
      motorsLive = false;
      if (ENABLE_L >= 0) digitalWrite(ENABLE_L, HIGH);
      if (ENABLE_R >= 0) digitalWrite(ENABLE_R, HIGH);
    }
  }
}

// ============================================================
// MPU6500
// ============================================================

bool writeRegister(uint8_t reg, uint8_t value) {
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(reg);
  Wire.write(value);
  return Wire.endTransmission(true) == 0;
}

bool readRegisters(uint8_t reg, uint8_t* data, uint8_t length) {
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;

  size_t received = Wire.requestFrom(uint8_t(MPU_ADDR), size_t(length), true);
  if (received != length) {
    while (Wire.available()) Wire.read();
    return false;
  }

  for (uint8_t i = 0; i < length; i++) {
    int value = Wire.read();
    if (value < 0) return false;
    data[i] = uint8_t(value);
  }
  return true;
}

int16_t signedBE(const uint8_t* bytes) {
  const uint16_t value = (uint16_t(bytes[0]) << 8) | bytes[1];
  return int16_t(value < 0x8000u ? int32_t(value) : int32_t(value) - 65536);
}

int readFreshImu(ImuSample& sample) {
  uint8_t status = 0;
  if (!readRegisters(0x3A, &status, 1)) return -1;
  if ((status & 0x01) == 0) return 0;

  uint8_t bytes[14];
  if (!readRegisters(0x3B, bytes, sizeof(bytes))) return -1;

  sample.ax = signedBE(bytes + 0);
  sample.ay = signedBE(bytes + 2);
  sample.az = signedBE(bytes + 4);
  sample.temperature = signedBE(bytes + 6);
  sample.gx = signedBE(bytes + 8);
  sample.gy = signedBE(bytes + 10);
  sample.gz = signedBE(bytes + 12);
  return 1;
}

bool beginImu() {
  uint8_t identity = 0;
  if (!readRegisters(0x75, &identity, 1)) return false;

  Serial.printf("WHO_AM_I = 0x%02X\n", identity);
  if (identity != 0x70) return false;

  if (!writeRegister(0x6B, 0x80)) return false;   // Reset
  delay(100);
  if (!writeRegister(0x6B, 0x01)) return false;   // Auto clock
  if (!writeRegister(0x6C, 0x00)) return false;   // All axes on
  delay(100);

  if (!writeRegister(0x6A, 0x00)) return false;   // FIFO/master off
  if (!writeRegister(0x23, 0x00)) return false;   // FIFO sources off
  if (!writeRegister(0x37, 0x00)) return false;
  if (!writeRegister(0x38, 0x01)) return false;   // Data-ready status
  if (!writeRegister(0x1A, 0x03)) return false;   // Gyro DLPF ~41 Hz
  if (!writeRegister(0x1B, 0x18)) return false;   // ±2000 °/s
  if (!writeRegister(0x1C, 0x00)) return false;   // ±2 g
  if (!writeRegister(0x1D, 0x03)) return false;   // Accel DLPF ~41 Hz
  if (!writeRegister(0x19, 0x00)) return false;   // 1 kHz output

  delay(100);
  return true;
}

bool calibrateImu() {
  constexpr unsigned N = 500;
  double sum[3] = {}, squareSum[3] = {};
  double accelSum[3] = {}, accelSquareSum[3] = {};
  unsigned count = 0;
  uint32_t started = millis();

  Serial.println("Keep robot completely stationary for calibration.");

  while (count < N && uint32_t(millis() - started) < 6000) {
    ImuSample s{};
    int result = readFreshImu(s);
    if (result < 0) return false;

    if (result == 1) {
      float gyro[3]  = { s.gx / GYRO_SCALE, s.gy / GYRO_SCALE, s.gz / GYRO_SCALE };
      float accel[3] = { s.ax / ACCEL_SCALE, s.ay / ACCEL_SCALE, s.az / ACCEL_SCALE };

      float magnitude = sqrtf(accel[0]*accel[0] + accel[1]*accel[1] + accel[2]*accel[2]);
      if (fabsf(magnitude - 1.0f) > 0.15f) return false;

      for (unsigned a = 0; a < 3; a++) {
        sum[a] += gyro[a];
        squareSum[a] += gyro[a] * gyro[a];
        accelSum[a] += accel[a];
        accelSquareSum[a] += accel[a] * accel[a];
      }
      count++;
    }
    delay(4);
  }

  if (count != N) return false;

  for (unsigned a = 0; a < 3; a++) {
    double mean = sum[a] / N;
    double variance = squareSum[a] / N - mean * mean;
    double accelMean = accelSum[a] / N;
    double accelVariance = accelSquareSum[a] / N - accelMean * accelMean;

    if (sqrt(fmax(variance, 0.0)) > 1.5 || fabs(mean) > 10.0 ||
        sqrt(fmax(accelVariance, 0.0)) > 0.03) {
      return false;
    }
    gyroBias[a] = float(mean);
  }

  Serial.printf("Gyro biases: %.4f %.4f %.4f deg/s\n",
                gyroBias[0], gyroBias[1], gyroBias[2]);
  return true;
}

void seedAngleFilter(float initialAngle) {
  angleFilter = Kalman();
  angleFilter.setQangle(0.0005f);
  angleFilter.setQbias(0.0005f);
  angleFilter.setRmeasure(3.0f);
  angleFilter.setAngle(initialAngle);
  pitch = initialAngle;
  filteredGyro = correctedGyro;
  filterSeeded = true;
}

// ============================================================
// CONTROLLER / FAULTS
// ============================================================

void resetController() {
  driveTarget = steerTarget = 0;
  speedEstimate = angleTarget = 0;
  angleIntegral = speedIntegral = 0;
  lastSpeed = speedElapsed = 0;
  motorRaw = motorApplied = 0;
  leftRate = rightRate = 0;
  angleP = angleI = angleD = 0;
  speedP = speedI = speedD = 0;
  angleLimited = speedLimited = false;
  filteredGyro = correctedGyro;

  int32_t l, r;
  float al, ar;
  if (motorsSnapshot(l, r, al, ar)) {
    previousPosL = l;
    previousPosR = r;
  }
}

void enterFault(Fault reason) {
  if (runState != RunState::FAULT) faultCode = reason;
  runState = RunState::FAULT;
  motorsStop();
  resetController();
}

void disarmRobot() {
  motorsStop();
  resetController();
  if (runState != RunState::FAULT) {
    runState = RunState::DISARMED;
    faultCode = Fault::NONE;
  }
}

int32_t positionDelta(int32_t now, int32_t previous) {
  return int32_t(uint32_t(now) - uint32_t(previous));
}

void updateController(float dt) {
  speedElapsed += dt;

  if (speedElapsed >= 0.05f) {
    int32_t posL, posR;
    float actualL, actualR;

    if (!motorsSnapshot(posL, posR, actualL, actualR)) {
      enterFault(Fault::MOTOR_LOCK);
      return;
    }

    const float generatedSteps = 0.5f * (
        float(positionDelta(posL, previousPosL)) +
        float(positionDelta(posR, previousPosR)));

    previousPosL = posL;
    previousPosR = posR;

    const float rawSpeed = generatedSteps / speedElapsed;
    const float alpha = speedElapsed / (0.08f + speedElapsed);
    speedEstimate += alpha * (rawSpeed - speedEstimate);

    if (speedLoopEnabled) {
      const float error = driveTarget - speedEstimate;
      const float derivative = -(speedEstimate - lastSpeed) / speedElapsed;
      const float candidate = clampFloat(speedIntegral + error * speedElapsed, -200, 200);

      speedP = gains[3] * error;
      speedD = gains[5] * derivative;

      const float trial = speedP + gains[4] * candidate + speedD;
      const float limited = clampFloat(trial, -MAX_LEAN_DEG, MAX_LEAN_DEG);
      const float change = gains[4] * (candidate - speedIntegral);

      if ((trial - limited) * change <= 0) speedIntegral = candidate;

      speedI = gains[4] * speedIntegral;
      const float requested = speedP + speedI + speedD;
      angleTarget = clampFloat(requested, -MAX_LEAN_DEG, MAX_LEAN_DEG);
      speedLimited = fabsf(requested - angleTarget) > 0.0001f;
    } else {
      speedIntegral = 0;
      angleTarget = 0;
      speedP = speedI = speedD = 0;
      speedLimited = false;
    }

    lastSpeed = speedEstimate;
    speedElapsed = 0;
  }

  const float error = angleTarget + angleOffset - pitch;
  const float candidate = clampFloat(angleIntegral + error * dt, -200, 200);

  angleP = ANGLE_OUTPUT_SCALE * gains[0] * error;
  angleD = -ANGLE_OUTPUT_SCALE * gains[2] * filteredGyro;

  const float trial = angleP + ANGLE_OUTPUT_SCALE * gains[1] * candidate + angleD;

  const float delta = MAX_ACCEL * dt;
  const float low  = fmaxf(-MAX_STEP_RATE, motorApplied - delta);
  const float high = fminf( MAX_STEP_RATE, motorApplied + delta);

  const float trialApplied = clampFloat(trial, low, high);
  const float change = ANGLE_OUTPUT_SCALE * gains[1] * (candidate - angleIntegral);

  if ((trial - trialApplied) * change <= 0) angleIntegral = candidate;

  angleI = ANGLE_OUTPUT_SCALE * gains[1] * angleIntegral;
  motorRaw = angleP + angleI + angleD;
  motorApplied = clampFloat(motorRaw, low, high);
  angleLimited = fabsf(motorRaw - motorApplied) > 0.01f;

  const float headroom = fmaxf(0, MAX_STEP_RATE - fabsf(motorApplied));
  const float steering = clampFloat(steerTarget, -headroom, headroom);

  leftRate  = motorApplied + steering;
  rightRate = motorApplied - steering;

  if (!isfinite(motorRaw) || !isfinite(motorApplied) ||
      !isfinite(leftRate) || !isfinite(rightRate) ||
      !isfinite(angleTarget) || !isfinite(speedEstimate)) {
    enterFault(Fault::INVALID_NUMBER);
    return;
  }

  if (!motorsSetRates(leftRate, rightRate)) {
    enterFault(leaseExpired ? Fault::PULSE_TIMEOUT : Fault::MOTOR_LOCK);
  }
}

// ============================================================
// COMMAND PROCESSING (control task)
// ============================================================

void makeReply(const Command& command, bool ok, const char* message) {
  Reply reply{};
  reply.client = command.client;
  reply.id = command.id;
  reply.ok = ok;
  snprintf(reply.message, sizeof(reply.message), "%s", message);
  for (unsigned i = 0; i < 6; i++) reply.values[i] = gains[i];
  reply.speedLoop = speedLoopEnabled;
  reply.offset = angleOffset;
  reply.state = runState;
  reply.fault = faultCode;
  xQueueSend(replyQueue, &reply, 0);
}

void applyCommands(bool imuHealthy, bool timingHealthy) {
  DriveRequest drive{};

  if (xQueueReceive(driveQueue, &drive, 0) == pdTRUE) {
    if (drive.client == currentOperator() &&
        uint32_t(millis() - drive.receivedMs) <= DRIVE_TIMEOUT_MS) {
      lastDriveMs = drive.receivedMs;
      if (runState == RunState::ARMED && speedLoopEnabled) {
        driveTarget = drive.speed;
        steerTarget = drive.steer;
      }
    } else if (drive.client == 0) {
      driveTarget = steerTarget = 0;
    }
  }

  if (uint32_t(millis() - lastDriveMs) > DRIVE_TIMEOUT_MS ||
      runState != RunState::ARMED || !speedLoopEnabled) {
    driveTarget = 0;
    steerTarget = 0;
  }

  if (uxQueueSpacesAvailable(replyQueue) == 0) return;

  Command command{};
  if (xQueueReceive(commandQueue, &command, 0) != pdTRUE) return;

  if (command.operation != Operation::GET_PID &&
      command.client != currentOperator()) {
    makeReply(command, false, "Control ownership changed.");
    return;
  }

  if (uint32_t(millis() - command.receivedMs) > COMMAND_MAX_AGE_MS) {
    makeReply(command, false, "Request expired; resend.");
    return;
  }

  switch (command.operation) {
    case Operation::GET_PID:
      makeReply(command, true, "Applied gains and offset read.");
      break;

    case Operation::SET_PID:
      if (runState == RunState::ARMED) {
        makeReply(command, false, "Disarm before changing gains.");
        break;
      }
      for (unsigned i = 0; i < 6; i++) gains[i] = command.values[i];
      speedLoopEnabled = command.speedLoop;
      resetController();
      makeReply(command, true, "Gains applied in RAM.");
      break;

    case Operation::SET_OFFSET:
      if (runState == RunState::ARMED) {
        makeReply(command, false, "Disarm before changing the angle offset.");
        break;
      }
      if (!isfinite(command.offset) ||
          fabsf(command.offset) > OFFSET_LIMIT_DEG) {
        makeReply(command, false, "Offset outside configured numeric bounds.");
        break;
      }
      angleOffset = command.offset;
      resetController();
      makeReply(command, true, "Angle offset applied in RAM.");
      break;

    case Operation::DISARM:
      disarmRobot();
      makeReply(command, true, "Disarmed. Motors stopped.");
      break;

    case Operation::ARM:
      if (runState == RunState::ARMED) {
        makeReply(command, true, "Already armed.");
        break;
      }
      if (!imuHealthy || !timingHealthy || !uprightReady ||
          !isfinite(pitch) ||
          fabsf(pitch - angleOffset) > ARM_ANGLE_DEG ||
          fabsf(correctedGyro) > ARM_RATE_DPS) {
        makeReply(command, false,
                  "Need fresh IMU and stable upright position for 1 s.");
        break;
      }
      resetController();
      xQueueReset(driveQueue);
      lastDriveMs = millis();
      faultCode = Fault::NONE;
      motorsArm();
      runState = RunState::ARMED;
      makeReply(command, true, "Armed with zero motion requests.");
      break;
  }
}

// ============================================================
// CONTROL TASK
// ============================================================

void controlTask(void*) {
  TickType_t wake = xTaskGetTickCount();
  uint32_t previousLoopUs = micros();
  uint32_t sequence = 0, maximumPeriodUs = 0, latePeriods = 0;
  uint32_t imuFailures = 0, noNewSamples = 0;
  uint32_t histogram[5] = {};
  ImuSample lastSample{};

  for (;;) {
    vTaskDelayUntil(&wake, pdMS_TO_TICKS(CONTROL_MS));

    const uint32_t startedUs = micros();
    const uint32_t periodUs = uint32_t(startedUs - previousLoopUs);
    previousLoopUs = startedUs;

    maximumPeriodUs = max(maximumPeriodUs, periodUs);
    if (periodUs > 6000) latePeriods++;

    histogram[periodUs <= 5500  ? 0 :
              periodUs <= 6000  ? 1 :
              periodUs <= 10000 ? 2 :
              periodUs <= 15000 ? 3 : 4]++;

    const bool timingHealthy = periodUs <= MAX_CONTROL_GAP_US;

    if (runState == RunState::ARMED && leaseExpired) {
      enterFault(Fault::PULSE_TIMEOUT);
    }
    if (runState == RunState::ARMED && !timingHealthy) {
      enterFault(Fault::DEADLINE);
    }

    ImuSample sample{};
    const int readResult = readFreshImu(sample);
    float sampleDt = 0;

    if (readResult < 0) {
      imuFailures++;
      filterSeeded = false;
      if (runState == RunState::ARMED) enterFault(Fault::IMU_READ);
    } else if (readResult == 0) {
      noNewSamples++;
    } else {
      const uint32_t sampleUs = micros();
      const uint32_t gapUs = haveGoodImu ? uint32_t(sampleUs - lastGoodImuUs) : CONTROL_US;
      sampleDt = gapUs * 1.0e-6f;

      lastSample = sample;
      lastGoodImuUs = sampleUs;
      lastGoodImuMs = millis();
      haveGoodImu = true;

      accelPitch = atan2f(float(sample.ay), float(sample.az)) * 180.0f / PI;
      correctedGyro = sample.gx / GYRO_SCALE - gyroBias[0];

      if (gapUs > IMU_STALE_US || !timingHealthy || !filterSeeded) {
        if (runState == RunState::ARMED) enterFault(Fault::IMU_STALE);
        seedAngleFilter(accelPitch);
      } else {
        pitch = angleFilter.getAngle(accelPitch, correctedGyro, sampleDt);
        const float alpha = sampleDt / (0.005f + sampleDt);
        filteredGyro += alpha * (correctedGyro - filteredGyro);
      }

      if (!isfinite(pitch) || !isfinite(correctedGyro) || !isfinite(filteredGyro)) {
        filterSeeded = false;
        enterFault(Fault::INVALID_NUMBER);
      }
    }

    const uint32_t nowMs = millis();

    const bool imuHealthy =
        readResult >= 0 && haveGoodImu && filterSeeded &&
        uint32_t(micros() - lastGoodImuUs) <= IMU_STALE_US &&
        isfinite(pitch) && isfinite(correctedGyro);

    if (!imuHealthy && runState == RunState::ARMED) {
      enterFault(Fault::IMU_STALE);
    }

    if (runState == RunState::ARMED &&
        fabsf(pitch - angleOffset) > FALL_ANGLE_DEG) {
      enterFault(Fault::TILT);
    }

    const bool stable =
        imuHealthy && timingHealthy &&
        fabsf(pitch - angleOffset) <= ARM_ANGLE_DEG &&
        fabsf(correctedGyro) <= ARM_RATE_DPS;

    if (!stable) {
      uprightTracking = false;
      uprightReady = false;
    } else {
      if (!uprightTracking) {
        uprightTracking = true;
        uprightSinceMs = nowMs;
      }
      uprightReady = uint32_t(nowMs - uprightSinceMs) >= UPRIGHT_HOLD_MS;
    }

    applyCommands(imuHealthy, timingHealthy);

    if (runState == RunState::ARMED && readResult == 1 &&
        imuHealthy && timingHealthy) {
      updateController(sampleDt);
    } else if (runState != RunState::ARMED) {
      resetController();
    }

    Telemetry t{};
    t.sequence = ++sequence;
    t.ms = millis();
    t.imuMs = lastGoodImuMs;
    t.periodUs = periodUs;
    t.maximumPeriodUs = maximumPeriodUs;
    t.latePeriods = latePeriods;
    t.imuFailures = imuFailures;
    t.noNewSamples = noNewSamples;
    for (unsigned i = 0; i < 5; i++) t.histogram[i] = histogram[i];

    t.haveImu = haveGoodImu;
    t.imuReadOk = readResult >= 0;
    t.newImu = readResult == 1;
    t.uprightReady = uprightReady;
    t.speedLoop = speedLoopEnabled;
    t.state = runState;
    t.fault = faultCode;

    t.pitch = pitch;
    t.accelPitch = accelPitch;
    t.gyroRate = correctedGyro;
    t.temperatureC = lastSample.temperature / 333.87f + 21.0f;
    t.angleTarget = angleTarget + angleOffset;
    t.speedTarget = driveTarget;
    t.speedEstimate = speedEstimate;
    t.motorRaw = motorRaw;
    t.motorApplied = motorApplied;
    t.steering = steerTarget;
    t.leftRate = leftRate;
    t.rightRate = rightRate;
    t.angleP = angleP; t.angleI = angleI; t.angleD = angleD;
    t.speedP = speedP; t.speedI = speedI; t.speedD = speedD;
    t.angleLimited = angleLimited;
    t.speedLimited = speedLimited;
    t.motorLeaseExpired = leaseExpired;
    t.angleOffsetValue = angleOffset;

    int32_t pl, pr;
    float al = NAN, ar = NAN;
    motorsSnapshot(pl, pr, al, ar);
    t.leftActual = al;
    t.rightActual = ar;

    t.raw[0] = lastSample.gx; t.raw[1] = lastSample.gy; t.raw[2] = lastSample.gz;
    t.raw[3] = lastSample.ax; t.raw[4] = lastSample.ay; t.raw[5] = lastSample.az;
    for (unsigned i = 0; i < 6; i++) t.values[i] = gains[i];

    t.executionUs = micros() - startedUs;
    xQueueOverwrite(telemetryQueue, &t);
  }
}

// ============================================================
// NETWORK
// ============================================================

void sendSimple(AsyncWebSocketClient* client, uint32_t id,
                bool ok, const char* message) {
  JsonDocument doc;
  doc["type"] = "reply";
  doc["id"] = id;
  doc["ok"] = ok;
  doc["message"] = message;
  String text;
  serializeJson(doc, text);
  client->text(text);
}

void broadcastOwner() {
  JsonDocument doc;
  doc["type"] = "owner";
  doc["owner"] = currentOperator();
  String text;
  serializeJson(doc, text);
  socket.textAll(text);
}

void onSocketEvent(AsyncWebSocket*, AsyncWebSocketClient* client,
                   AwsEventType type, void* arg,
                   uint8_t* data, size_t length) {

  if (type == WS_EVT_CONNECT) {
    if (socket.count() > 4) {
      client->close();
      return;
    }

    bool becameOwner = false;
    portENTER_CRITICAL(&ownerMux);
    if (operatorId == 0) {
      operatorId = client->id();
      becameOwner = true;
    }
    portEXIT_CRITICAL(&ownerMux);

    JsonDocument doc;
    doc["type"] = "hello";
    doc["you"] = client->id();
    doc["owner"] = currentOperator();
    doc["build"] = BUILD_ID;
    doc["maxDrive"] = MAX_DRIVE;
    doc["maxSteer"] = MAX_STEER;
    String text;
    serializeJson(doc, text);
    client->text(text);

    if (becameOwner) broadcastOwner();
    return;
  }

  if (type == WS_EVT_DISCONNECT) {
    bool wasOperator = false;
    portENTER_CRITICAL(&ownerMux);
    if (operatorId == client->id()) {
      operatorId = 0;
      wasOperator = true;
    }
    portEXIT_CRITICAL(&ownerMux);

    if (wasOperator) {
      DriveRequest neutral{};
      neutral.client = 0;
      neutral.receivedMs = millis();
      xQueueOverwrite(driveQueue, &neutral);
      broadcastOwner();
    }
    return;
  }

  if (type != WS_EVT_DATA) return;

  AwsFrameInfo* frame = static_cast<AwsFrameInfo*>(arg);
  if (!frame->final || frame->index != 0 || frame->len != length ||
      frame->opcode != WS_TEXT || length == 0 || length > 512) {
    return;
  }

  JsonDocument doc;
  if (deserializeJson(doc, data, length)) {
    sendSimple(client, 0, false, "Invalid JSON.");
    return;
  }

  const String operation = doc["type"] | "";
  const uint32_t id = doc["id"].is<uint32_t>() ? doc["id"].as<uint32_t>() : 0;

  if (operation == "claim") {
    bool granted = false;
    portENTER_CRITICAL(&ownerMux);
    if (operatorId == 0) {
      operatorId = client->id();
      granted = true;
    }
    portEXIT_CRITICAL(&ownerMux);

    sendSimple(client, id, granted,
               granted ? "Control acquired." : "Control already owned.");
    if (granted) broadcastOwner();
    return;
  }

  if (operation != "getPid" && client->id() != currentOperator()) {
    sendSimple(client, id, false, "Read-only connection.");
    return;
  }

  if (operation == "drive") {
    if (!doc["v"].is<float>() || !doc["s"].is<float>()) return;

    const float speed = doc["v"].as<float>();
    const float steer = doc["s"].as<float>();
    if (!isfinite(speed) || !isfinite(steer)) return;

    DriveRequest request{};
    request.client = client->id();
    request.receivedMs = millis();
    request.speed = clampFloat(speed, -MAX_DRIVE, MAX_DRIVE);
    request.steer = clampFloat(steer, -MAX_STEER, MAX_STEER);
    xQueueOverwrite(driveQueue, &request);
    return;
  }

  if (!doc["id"].is<uint32_t>()) {
    sendSimple(client, 0, false, "Missing request ID.");
    return;
  }

  Command command{};
  command.client = client->id();
  command.id = id;
  command.receivedMs = millis();

  if (operation == "getPid") {
    command.operation = Operation::GET_PID;
  } else if (operation == "arm") {
    command.operation = Operation::ARM;
  } else if (operation == "disarm") {
    command.operation = Operation::DISARM;
  } else if (operation == "setPid") {
    command.operation = Operation::SET_PID;

    JsonArrayConst array = doc["gains"].as<JsonArrayConst>();
    if (array.isNull() || array.size() != 6 || !doc["speedLoop"].is<bool>()) {
      sendSimple(client, id, false, "Need six gains and speedLoop Boolean.");
      return;
    }

    for (unsigned i = 0; i < 6; i++) {
      if (!array[i].is<float>()) {
        sendSimple(client, id, false, "Gains must be numeric.");
        return;
      }
      command.values[i] = array[i].as<float>();
      if (!isfinite(command.values[i]) || fabsf(command.values[i]) > GAIN_LIMIT[i]) {
        sendSimple(client, id, false, "Gain outside configured numeric bounds.");
        return;
      }
    }
    command.speedLoop = doc["speedLoop"].as<bool>();
  } else if (operation == "setOffset") {
    command.operation = Operation::SET_OFFSET;

    if (!doc["offset"].is<float>()) {
      sendSimple(client, id, false, "Offset must be numeric.");
      return;
    }
    command.offset = doc["offset"].as<float>();
    if (!isfinite(command.offset) || fabsf(command.offset) > OFFSET_LIMIT_DEG) {
      sendSimple(client, id, false, "Offset outside configured numeric bounds.");
      return;
    }
  } else {
    sendSimple(client, id, false, "Unknown operation.");
    return;
  }

  const BaseType_t queued =
      command.operation == Operation::DISARM
          ? xQueueSendToFront(commandQueue, &command, 0)
          : xQueueSend(commandQueue, &command, 0);

  if (queued != pdTRUE) {
    sendSimple(client, id, false, "Command queue full; request not accepted.");
  }
}

void putFloat(JsonDocument& doc, const char* key, float value) {
  if (isfinite(value)) doc[key] = value;
  else doc[key] = nullptr;
}

void serviceNetwork() {
  socket.cleanupClients();

  Reply reply{};
  while (xQueueReceive(replyQueue, &reply, 0) == pdTRUE) {
    JsonDocument doc;
    doc["type"] = "reply";
    doc["id"] = reply.id;
    doc["ok"] = reply.ok;
    doc["message"] = reply.message;
    doc["state"] = stateName(reply.state);
    doc["fault"] = faultName(reply.fault);
    doc["speedLoop"] = reply.speedLoop;
    doc["offset"] = reply.offset;
    JsonArray values = doc["gains"].to<JsonArray>();
    for (float v : reply.values) values.add(v);
    String text;
    serializeJson(doc, text);
    socket.text(reply.client, text);
  }

  static Telemetry t{};
  static bool haveTelemetry = false;
  static uint32_t previousSendMs = 0;

  if (xQueueReceive(telemetryQueue, &t, 0) == pdTRUE) haveTelemetry = true;

  const uint32_t now = millis();
  if (uint32_t(now - previousSendMs) < 50) return;
  previousSendMs = now;

  if (!haveTelemetry || socket.count() == 0) return;
  if (!socket.availableForWriteAll()) return;

  JsonDocument doc;
  doc["type"] = "telemetry";
  doc["build"] = BUILD_ID;
  doc["seq"] = t.sequence;
  doc["ms"] = t.ms;
  doc["state"] = stateName(t.state);
  doc["fault"] = faultName(t.fault);
  doc["sampleAgeMs"] = uint32_t(now - t.ms);
  if (t.haveImu) doc["imuAgeMs"] = uint32_t(now - t.imuMs);
  else doc["imuAgeMs"] = nullptr;

  doc["imuReadOk"] = t.imuReadOk;
  doc["newImu"] = t.newImu;
  doc["uprightReady"] = t.uprightReady;
  doc["speedLoop"] = t.speedLoop;
  doc["angleLimited"] = t.angleLimited;
  doc["speedLimited"] = t.speedLimited;
  doc["motorLeaseExpired"] = t.motorLeaseExpired;

  doc["periodUs"] = t.periodUs;
  doc["executionUs"] = t.executionUs;
  doc["maxPeriodUs"] = t.maximumPeriodUs;
  doc["latePeriods"] = t.latePeriods;
  doc["imuFailures"] = t.imuFailures;
  doc["noNewSamples"] = t.noNewSamples;
  doc["periodLe5500"] = t.histogram[0];
  doc["period5501to6000"] = t.histogram[1];
  doc["period6001to10000"] = t.histogram[2];
  doc["period10001to15000"] = t.histogram[3];
  doc["periodOver15000"] = t.histogram[4];

  putFloat(doc, "pitch", t.pitch);
  putFloat(doc, "accelPitch", t.accelPitch);
  putFloat(doc, "gyroRate", t.gyroRate);
  putFloat(doc, "temperatureC", t.temperatureC);
  putFloat(doc, "angleTarget", t.angleTarget);
  putFloat(doc, "speedTarget", t.speedTarget);
  putFloat(doc, "speedEstimate", t.speedEstimate);
  putFloat(doc, "motorRaw", t.motorRaw);
  putFloat(doc, "motorApplied", t.motorApplied);
  putFloat(doc, "steering", t.steering);
  putFloat(doc, "leftRate", t.leftRate);
  putFloat(doc, "rightRate", t.rightRate);
  putFloat(doc, "leftActual", t.leftActual);
  putFloat(doc, "rightActual", t.rightActual);
  putFloat(doc, "angleP", t.angleP);
  putFloat(doc, "angleI", t.angleI);
  putFloat(doc, "angleD", t.angleD);
  putFloat(doc, "speedP", t.speedP);
  putFloat(doc, "speedI", t.speedI);
  putFloat(doc, "speedD", t.speedD);
  putFloat(doc, "angleOffset", t.angleOffsetValue);

  const char* rawKeys[6]  = { "gxRaw", "gyRaw", "gzRaw", "axRaw", "ayRaw", "azRaw" };
  const char* gainKeys[6] = { "angKp", "angKi", "angKd", "spdKp", "spdKi", "spdKd" };

  for (unsigned i = 0; i < 6; i++) {
    if (t.haveImu) doc[rawKeys[i]] = t.raw[i];
    else doc[rawKeys[i]] = nullptr;
    putFloat(doc, gainKeys[i], t.values[i]);
  }

  doc["heapBytes"] = ESP.getFreeHeap();
  doc["apClients"] = WiFi.softAPgetStationNum();

  String text;
  text.reserve(2400);
  serializeJson(doc, text);
  socket.textAll(text);
}

// ============================================================
// SETUP / LOOP
// ============================================================

void setup() {
  Serial.begin(115200);
  delay(300);

  if (ENABLE_L >= 0) {
    pinMode(ENABLE_L, OUTPUT);
    digitalWrite(ENABLE_L, HIGH);
  }
  if (ENABLE_R >= 0) {
    pinMode(ENABLE_R, OUTPUT);
    digitalWrite(ENABLE_R, HIGH);
  }

  telemetryQueue = xQueueCreate(1, sizeof(Telemetry));
  driveQueue     = xQueueCreate(1, sizeof(DriveRequest));
  commandQueue   = xQueueCreate(8, sizeof(Command));
  replyQueue     = xQueueCreate(8, sizeof(Reply));

  if (!telemetryQueue || !driveQueue || !commandQueue || !replyQueue) {
    fatalStartup("Queue allocation failed.");
  }

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(400000);
  Wire.setTimeOut(3);

  if (!beginImu()) {
    fatalStartup("MPU6500 initialization failed. Check wiring/identity.");
  }
  if (!calibrateImu()) {
    fatalStartup("Calibration failed. Keep robot still, then reset.");
  }

  if (!beginMotors()) {
    fatalStartup("FastAccelStepper initialization failed. Check STEP pins.");
  }

  WiFi.mode(WIFI_AP);
  if (!WiFi.softAP(AP_SSID, AP_PASSWORD)) {
    fatalStartup("Wi-Fi AP startup failed.");
  }

  socket.onEvent(onSocketEvent);
  server.addHandler(&socket);

  server.on("/", HTTP_GET, [](AsyncWebServerRequest* request) {
    request->send(200, "text/html", PAGE);
  });
  server.on("/debug", HTTP_GET, [](AsyncWebServerRequest* request) {
    request->redirect("/");
  });

  if (xTaskCreatePinnedToCore(controlTask, "balance", 8192, nullptr,
                              3, &controlTaskHandle, 1) != pdPASS) {
    fatalStartup("Control task creation failed.");
  }
  if (xTaskCreatePinnedToCore(motorWatchdogTask, "motorWdt", 3072, nullptr,
                              4, &watchdogTaskHandle, 1) != pdPASS) {
    fatalStartup("Watchdog task creation failed.");
  }

  server.begin();

  Serial.println("Startup complete: DISARMED.");
  Serial.printf("Connect to Wi-Fi \"%s\" and open http://", AP_SSID);
  Serial.println(WiFi.softAPIP());
}

void loop() {
  serviceNetwork();
  delay(2);
}