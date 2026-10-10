#include <Wire.h>
#include <Kalman.h>
#include <WiFi.h>
#include <AsyncTCP.h>
#include <ESPAsyncWebServer.h> // Async Web Server
#include <esp_arduino_version.h> 

#include "web_page.h" // Upgraded Web UI and Telemetry Dashboard 

// --- Wi-Fi & Web Server Settings ---
const char* ssid = "TeethutBOT_AP";
const char* password = "12345678";

AsyncWebServer server(80);
AsyncWebSocket ws("/ws"); // NEW: WebSocket endpoint

// --- MPU6500 Settings & Helpers ---
uint8_t mpu_addr = 0x68; // Auto-detected (0x68 or 0x69)
float gyroXBias = 0.0;

bool writeMPURegister(uint8_t reg, uint8_t val) {
  Wire.beginTransmission(mpu_addr);
  Wire.write(reg);
  Wire.write(val);
  return Wire.endTransmission() == 0;
}

bool initMPU6500() {
  uint8_t addrs[] = {0x68, 0x69};
  bool found = false;
  for (int i = 0; i < 2; i++) {
    Wire.beginTransmission(addrs[i]);
    Wire.write(0x75); // WHO_AM_I
    if (Wire.endTransmission(false) == 0) {
      Wire.requestFrom((uint16_t)addrs[i], (uint8_t)1, (uint8_t)true);
      if (Wire.available()) {
        uint8_t who = Wire.read();
        Serial.printf("Found IMU at 0x%02X (WHO_AM_I = 0x%02X)\n", addrs[i], who);
        mpu_addr = addrs[i];
        found = true;
        break;
      }
    }
  }

  if (!found) {
    Serial.println("MPU6500 not detected on I2C bus!");
    return false;
  }

  // Reset device
  writeMPURegister(0x6B, 0x80);
  delay(100);
  // Auto clock source (PLL with gyro X reference)
  writeMPURegister(0x6B, 0x01);
  // Enable all axes
  writeMPURegister(0x6C, 0x00);
  delay(50);
  // DLPF ~41 Hz
  writeMPURegister(0x1A, 0x03);
  // Gyro full scale ±2000 deg/s (16.4 LSB/deg/s)
  writeMPURegister(0x1B, 0x18);
  // Accel full scale ±2g (16384 LSB/g)
  writeMPURegister(0x1C, 0x00);
  // Accel DLPF ~41 Hz
  writeMPURegister(0x1D, 0x03);
  // Sample rate divider = 0 (1 kHz output)
  writeMPURegister(0x19, 0x00);
  delay(50);

  return true;
}

Kalman kalmanFilter;

// --- Web Remote Variables ---
volatile unsigned long lastWebCmdTime = 0;
const float MAX_WEB_SPEED = 4000.0;
const float MAX_WEB_STEER = 1500.0; 

// --- Stepper Motor Pins ---
const int STEP_PIN_L = 14; //14
const int DIR_PIN_L  = 27; //27
const int EN_PIN_L   = 16; //16
const int STEP_PIN_R = 26; //26
const int DIR_PIN_R  = 25; //25
const int EN_PIN_R   = 17; //17

// --- Motor Direction Configuration ---
// Adjust to true or false if either motor needs to be reversed
const bool LEFT_FORWARD_HIGH  = true; // Both set to true so both motors drive in the same forward direction
const bool RIGHT_FORWARD_HIGH = true;

// --- Stepper & System Constants ---
const float STEPS_PER_REV = 1600.0;

// MODIFIED: Removed 'const' so these can be updated via the web interface
float MAX_STEPS_PER_SEC = 20000.0; 
float MAX_ACCEL = 40000.0;

// --- DUAL Hardware Timers & Virtual Encoders ---
hw_timer_t * stepperTimerL = NULL;
hw_timer_t * stepperTimerR = NULL;

portMUX_TYPE timerMuxL = portMUX_INITIALIZER_UNLOCKED;
portMUX_TYPE timerMuxR = portMUX_INITIALIZER_UNLOCKED;

volatile long virtualEncoderCountL = 0;
volatile long virtualEncoderCountR = 0;

volatile uint32_t stepIntervalUsL = 0; 
volatile uint32_t stepIntervalUsR = 0; 

volatile bool dirIsForwardL = true;
volatile bool dirIsForwardR = true;

volatile bool motorsEnabledL = false;
volatile bool motorsEnabledR = false;

float currentStepperSpeed = 0.0;
float currentSteerOffset = 0.0;

// --- Inner Loop (Angle) Variables ---
float angKp = 40.0;
float angKi = 0.1;
float angKd = 0.0;
float angleSetpoint = 0;
float angError = 0.0, angLastError = 0.0, angIntegral = 0.0, angDerivative = 0.0;
float motorOutput = 0.0;
float angleOffset = 6;

// --- Outer Loop (Speed) Variables ---
float spdKp = -0.0015; 
float spdKi = -0.0000;
float spdKd = 0.00002;
float targetSpeed = 0;
float currentSpeed = 0;
float spdError = 0.0, spdIntegral = 0.0, spdDerivative = 0.0, spdOutput = 0.0;
float lastSpeedForDerivative = 0.0;

float pitchAngle = 0.0;

TaskHandle_t ControlLoopTask;

// Note: HTML/JS Web UI is now located in web_page.h

// --- Forward Declarations ---
void PID_Control_Task(void *pvParameters);
void driveMotors(float targetSpeedOutput, float loopDt);
void readBluetoothCommands();
void processWebInput();
void broadcastTelemetry();

// --- Real-time WebSocket Telemetry Broadcast (20 Hz) ---
void broadcastTelemetry() {
  if (ws.count() == 0) return;
  if (!ws.availableForWriteAll()) return;

  char buf[384];
  float pAngle = pitchAngle;
  float spd = currentSpeed;
  float tgtSpd = targetSpeed;
  float str = currentSteerOffset;
  float mOut = motorOutput;
  float aSet = angleSetpoint;
  long encL = virtualEncoderCountL;
  long encR = virtualEncoderCountR;
  bool isBalancing = (abs(pAngle) <= 45.0f);

  snprintf(buf, sizeof(buf),
    "{\"type\":\"telemetry\",\"ms\":%lu,\"state\":\"%s\","
    "\"pitch\":%.2f,\"setpoint\":%.2f,\"offset\":%.2f,"
    "\"motorOut\":%.1f,\"speed\":%.1f,\"targetSpeed\":%.1f,\"steer\":%.1f,"
    "\"encL\":%ld,\"encR\":%ld,"
    "\"heap\":%u}",
    millis(), isBalancing ? "BALANCING" : "FALLEN",
    pAngle, aSet, angleOffset,
    mOut, spd, tgtSpd, str,
    encL, encR,
    ESP.getFreeHeap()
  );
  ws.textAll(buf);
}

// --- WebSocket Event Handler ---
void onWsEvent(AsyncWebSocket * server, AsyncWebSocketClient * client, AwsEventType type, void * arg, uint8_t *data, size_t len){
  if(type == WS_EVT_DATA){
    AwsFrameInfo * info = (AwsFrameInfo*)arg;
    if(info->final && info->index == 0 && info->len == len){

      char buffer[len + 1];         
      memcpy(buffer, data, len);    
      buffer[len] = '\0';           
      String msg = String(buffer);  
      
      if(msg.startsWith("V:")){
        int commaIndex = msg.indexOf(',');
        if(commaIndex > 0){
          targetSpeed = msg.substring(2, commaIndex).toFloat();
          currentSteerOffset = msg.substring(commaIndex + 3).toFloat();
          
          targetSpeed = constrain(targetSpeed, -MAX_WEB_SPEED, MAX_WEB_SPEED);
          currentSteerOffset = constrain(currentSteerOffset, -MAX_WEB_STEER, MAX_WEB_STEER);
          
          lastWebCmdTime = millis();
        }
      }
    }
  }
}

// --- LEFT Stepper Timer Interrupt ---
void IRAM_ATTR onStepperTimerL() {
  portENTER_CRITICAL_ISR(&timerMuxL);
  if (motorsEnabledL && stepIntervalUsL > 0) {
    digitalWrite(STEP_PIN_L, HIGH);
    ets_delay_us(2); 
    digitalWrite(STEP_PIN_L, LOW);

    if (dirIsForwardL) virtualEncoderCountL = virtualEncoderCountL + 1;
    else virtualEncoderCountL = virtualEncoderCountL - 1;

    #if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
      timerAlarm(stepperTimerL, stepIntervalUsL, true, 0); 
    #else
      timerAlarmWrite(stepperTimerL, stepIntervalUsL, true);
    #endif
  } else {
    #if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
      timerAlarm(stepperTimerL, 50000, true, 0); 
    #else
      timerAlarmWrite(stepperTimerL, 50000, true);
    #endif
  }
  portEXIT_CRITICAL_ISR(&timerMuxL);
}

// --- RIGHT Stepper Timer Interrupt ---
void IRAM_ATTR onStepperTimerR() {
  portENTER_CRITICAL_ISR(&timerMuxR);
  if (motorsEnabledR && stepIntervalUsR > 0) {
    digitalWrite(STEP_PIN_R, HIGH);
    ets_delay_us(2); 
    digitalWrite(STEP_PIN_R, LOW);

    if (dirIsForwardR) virtualEncoderCountR = virtualEncoderCountR + 1;
    else virtualEncoderCountR = virtualEncoderCountR - 1;

    #if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
      timerAlarm(stepperTimerR, stepIntervalUsR, true, 0);
    #else
      timerAlarmWrite(stepperTimerR, stepIntervalUsR, true);
    #endif
  } else {
    #if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
      timerAlarm(stepperTimerR, 50000, true, 0);
    #else
      timerAlarmWrite(stepperTimerR, 50000, true);
    #endif
  }
  portEXIT_CRITICAL_ISR(&timerMuxR);
}

void setup() {
  Serial.begin(115200);
  //SerialBT.begin("ESP32_Balancing_Robot");

  // --- Setup Wi-Fi Access Point ---
  Serial.println("Starting Wi-Fi AP...");
  WiFi.softAP(ssid, password);
  IPAddress IP = WiFi.softAPIP();
  Serial.print("AP IP address: ");
  Serial.println(IP);

  // --- Setup Async Web Server Routes ---
  server.on("/", HTTP_GET, [](AsyncWebServerRequest *request){
    request->send(200, "text/html", index_html);
  });

  server.on("/get_pid", HTTP_GET, [](AsyncWebServerRequest *request) {
    String json = "{";
    json += "\"kp1\":" + String(angKp, 4) + ",";
    json += "\"ki1\":" + String(angKi, 4) + ",";
    json += "\"kd1\":" + String(angKd, 4) + ",";
    json += "\"kp2\":" + String(spdKp, 4) + ",";
    json += "\"ki2\":" + String(spdKi, 4) + ",";
    json += "\"kd2\":" + String(spdKd, 4) + ",";
    json += "\"ao\":" + String(angleOffset, 4) + ",";
    json += "\"ma\":" + String(MAX_ACCEL, 4) + ",";
    json += "\"msps\":" + String(MAX_STEPS_PER_SEC, 4);
    json += "}";
    request->send(200, "application/json", json);
  });

  server.on("/set_pid", HTTP_GET, [](AsyncWebServerRequest *request) {
    if (request->hasArg("kp1")) angKp = request->arg("kp1").toFloat();
    if (request->hasArg("ki1")) angKi = request->arg("ki1").toFloat();
    if (request->hasArg("kd1")) angKd = request->arg("kd1").toFloat();
    if (request->hasArg("kp2")) spdKp = request->arg("kp2").toFloat();
    if (request->hasArg("ki2")) spdKi = request->arg("ki2").toFloat();
    if (request->hasArg("kd2")) spdKd = request->arg("kd2").toFloat();
    if (request->hasArg("ao")) angleOffset = request->arg("ao").toFloat();
    if (request->hasArg("ma")) MAX_ACCEL = request->arg("ma").toFloat();
    if (request->hasArg("msps")) MAX_STEPS_PER_SEC = request->arg("msps").toFloat();

    Serial.println("\n--- Web: Settings Updated ---");
    Serial.print("Inner (Balance) -> Kp: "); Serial.print(angKp, 4);
    Serial.print(" | Ki: "); Serial.print(angKi, 4);
    Serial.print(" | Kd: "); Serial.println(angKd, 4);
    
    Serial.print("Outer (Speed)   -> Kp: "); Serial.print(spdKp, 4);
    Serial.print(" | Ki: "); Serial.print(spdKi, 4);
    Serial.print(" | Kd: "); Serial.println(spdKd, 4);

    Serial.print("System Config   -> Offset: "); Serial.print(angleOffset, 4);
    Serial.print(" | Max Accel: "); Serial.print(MAX_ACCEL, 4);
    Serial.print(" | Max Steps/Sec: "); Serial.println(MAX_STEPS_PER_SEC, 4);
    Serial.println("-----------------------------");

    request->send(200, "text/plain", "OK");
  });

  // Attach WebSocket
  ws.onEvent(onWsEvent);
  server.addHandler(&ws);

  server.begin();
  Serial.println("Async Web Server & WebSocket Started!");

  Wire.begin();
  Wire.setClock(400000); 

  if (!initMPU6500()) {
    Serial.println("MPU6500 init failed! Check wiring. Halting...");
    while (1) delay(100);
  }

  pinMode(STEP_PIN_L, OUTPUT); pinMode(DIR_PIN_L, OUTPUT);
  pinMode(STEP_PIN_R, OUTPUT); pinMode(DIR_PIN_R, OUTPUT);
  pinMode(EN_PIN_L, OUTPUT);   pinMode(EN_PIN_R, OUTPUT);
  digitalWrite(EN_PIN_L, LOW); // Active-LOW: LOW enables stepper drivers
  digitalWrite(EN_PIN_R, LOW); 

  // --- Initialize BOTH Hardware Timers ---
  #if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
    stepperTimerL = timerBegin(1000000);
    timerAttachInterrupt(stepperTimerL, &onStepperTimerL);
    timerAlarm(stepperTimerL, 50000, true, 0); 
    
    stepperTimerR = timerBegin(1000000); 
    timerAttachInterrupt(stepperTimerR, &onStepperTimerR);
    timerAlarm(stepperTimerR, 50000, true, 0);
  #else
    stepperTimerL = timerBegin(0, 80, true); 
    timerAttachInterrupt(stepperTimerL, &onStepperTimerL, true);
    timerAlarmWrite(stepperTimerL, 50000, true); 
    timerAlarmEnable(stepperTimerL);

    stepperTimerR = timerBegin(1, 80, true); 
    timerAttachInterrupt(stepperTimerR, &onStepperTimerR, true);
    timerAlarmWrite(stepperTimerR, 50000, true); 
    timerAlarmEnable(stepperTimerR);
  #endif

  // Calibrate Gyro X Bias (keep robot still during boot)
  Serial.println("Calibrating MPU6500 Gyro X bias (keep robot still)...");
  long gXSum = 0;
  const int CAL_SAMPLES = 200;
  for (int i = 0; i < CAL_SAMPLES; i++) {
    Wire.beginTransmission(mpu_addr); Wire.write(0x43); Wire.endTransmission(false);
    Wire.requestFrom((uint16_t)mpu_addr, (uint8_t)2, (uint8_t)true);
    if (Wire.available() >= 2) {
      gXSum += (int16_t)((Wire.read() << 8) | Wire.read());
    }
    delay(2);
  }
  gyroXBias = ((float)gXSum / CAL_SAMPLES) / 16.4;
  Serial.print("Gyro X Bias: "); Serial.println(gyroXBias);

  // Read initial accel to set starting angle for Kalman filter
  Wire.beginTransmission(mpu_addr); Wire.write(0x3D); Wire.endTransmission(false);
  Wire.requestFrom((uint16_t)mpu_addr, (uint8_t)4, (uint8_t)true);
  if (Wire.available() >= 4) {
    int16_t aY = (Wire.read() << 8) | Wire.read();
    int16_t aZ = (Wire.read() << 8) | Wire.read();
    kalmanFilter.setAngle(atan2(aY / 16384.0, aZ / 16384.0) * 180 / PI);
  }
  kalmanFilter.setQangle(0.0005); kalmanFilter.setQbias(0.0005); kalmanFilter.setRmeasure(3.0);

  xTaskCreatePinnedToCore(PID_Control_Task, "PID_Task", 8192, NULL, 2, &ControlLoopTask, 0);
}

void loop() {
  ws.cleanupClients(); // Keep WebSockets clean
  processWebInput();
  
  static unsigned long lastTelemetryTime = 0;
  if (millis() - lastTelemetryTime >= 50) {
    lastTelemetryTime = millis();
    broadcastTelemetry();
  }
  
  vTaskDelay(2); 
  delay(10);
}

// --- Safety Failsafe ---
void processWebInput() {
  // If no ping from the web joystick in 500ms (browser closed/lost connection)
  if (millis() - lastWebCmdTime > 500) {
    targetSpeed = 0;
    currentSteerOffset = 0;
  }
}

// --- CORE 0: Strictly Timed Control Loop ---
void PID_Control_Task(void *pvParameters) {
  const unsigned long LOOP_TIME_MICROS = 5000;
  unsigned long lastLoopTime = micros();
  const double dt = 0.005; 
  long lastEncoderCountForSpeed = 0;
  float dtSum = 0;

  for (;;) {
    unsigned long currentMicros = micros();

    if (currentMicros - lastLoopTime >= LOOP_TIME_MICROS) {
      lastLoopTime = currentMicros;

      Wire.beginTransmission(mpu_addr); Wire.write(0x3B); Wire.endTransmission(false);
      Wire.requestFrom((uint16_t)mpu_addr, (uint8_t)14, (uint8_t) true);

      if (Wire.available() >= 14) {
        int16_t aX = (Wire.read() << 8) | Wire.read(); 
        int16_t aY = (Wire.read() << 8) | Wire.read(); 
        int16_t aZ = (Wire.read() << 8) | Wire.read();
        Wire.read(); Wire.read(); // Skip temperature
        int16_t gX = (Wire.read() << 8) | Wire.read(); 
        int16_t gY = (Wire.read() << 8) | Wire.read(); 
        int16_t gZ = (Wire.read() << 8) | Wire.read();

        float accelY = aY / 16384.0; float accelZ = aZ / 16384.0;
        float gyroX = (gX / 16.4) - gyroXBias;
        
        pitchAngle = kalmanFilter.getAngle(atan2(accelY, accelZ) * 180 / PI, gyroX, dt);

        dtSum += dt;

        static int EncoderloopCount = 0;
        EncoderloopCount++;

        if (EncoderloopCount >= 10) { 
          noInterrupts();
          // Forward distance is the AVERAGE of both wheels
          long currentEnc = (virtualEncoderCountL + virtualEncoderCountR) / 2;
          interrupts();

          float rawSpeed = (currentEnc - lastEncoderCountForSpeed) / dtSum;
          lastEncoderCountForSpeed = currentEnc;

          currentSpeed = (0.5 * rawSpeed) + (0.5 * currentSpeed); 

          spdError = targetSpeed - currentSpeed;
          spdIntegral = constrain(spdIntegral + (spdError * dtSum), -3000, 3000);
          
          spdDerivative = -(currentSpeed - lastSpeedForDerivative) / dtSum;
          lastSpeedForDerivative = currentSpeed;

          spdOutput = (spdKp * spdError) + constrain(spdKi * spdIntegral,-7.0,7.0) + (spdKd * spdDerivative);
          angleSetpoint = constrain(spdOutput, -15.0, 15.0);

          dtSum = 0; EncoderloopCount = 0; 
        }

        angError = angleSetpoint + angleOffset - pitchAngle;
        angError = (0.5 * angError) + (0.5 * angLastError); 

        angIntegral = constrain(angIntegral + (angError * dt), -200, 200);

        static float lastGx = 0;
        angDerivative = (0.3 * gyroX) + (0.7 * lastGx);
        lastGx = angDerivative;
        angLastError = angError;

        motorOutput = ((angKp * angError) + (angKi * angIntegral) + (angKd * angDerivative)) * 20.0;

        if (abs(pitchAngle) > 45) {
          motorOutput = 0;
          angIntegral = 0;
          spdIntegral = 0;
          digitalWrite(EN_PIN_L, HIGH); // Disable stepper drivers when fallen over
          digitalWrite(EN_PIN_R, HIGH);
        } else {
          digitalWrite(EN_PIN_L, LOW);  // Re-enable stepper drivers when upright
          digitalWrite(EN_PIN_R, LOW);
        }

        driveMotors(motorOutput, dt);
      }
    }
    vTaskDelay(1); 
  }
}

// --- Independent Dual Motor Slew & Output ---
void driveMotors(float targetSpeedOutput, float loopDt) {
  // Slew the BASE forward/backward speed
  float maxChange = MAX_ACCEL * loopDt;

  if (targetSpeedOutput > currentStepperSpeed + maxChange) currentStepperSpeed += maxChange;
  else if (targetSpeedOutput < currentStepperSpeed - maxChange) currentStepperSpeed -= maxChange;
  else currentStepperSpeed = targetSpeedOutput;

  // Mix the Steering Offset (Left gets positive offset, Right gets negative)
  float speedL = currentStepperSpeed + currentSteerOffset;
  float speedR = currentStepperSpeed - currentSteerOffset;

  // --- Process LEFT Motor ---
  portENTER_CRITICAL(&timerMuxL);
  if (speedL == 0.0) { 
    motorsEnabledL = false; stepIntervalUsL = 0;
  } else {
    motorsEnabledL = true;
    bool forward = (speedL > 0);
    dirIsForwardL = forward;
    digitalWrite(DIR_PIN_L, (forward == LEFT_FORWARD_HIGH) ? HIGH : LOW);

    float sps_L = constrain(abs(speedL), 0, MAX_STEPS_PER_SEC);
    if (sps_L < 10.0) stepIntervalUsL = 100000;
    else stepIntervalUsL = 1000000.0 / sps_L;
  }
  portEXIT_CRITICAL(&timerMuxL);

  // --- Process RIGHT Motor ---
  portENTER_CRITICAL(&timerMuxR);
  if (speedR == 0.0) { 
    motorsEnabledR = false; stepIntervalUsR = 0;
  } else {
    motorsEnabledR = true;
    bool forward = (speedR > 0);
    dirIsForwardR = forward;
    digitalWrite(DIR_PIN_R, (forward == RIGHT_FORWARD_HIGH) ? HIGH : LOW);

    float sps_R = constrain(abs(speedR), 0, MAX_STEPS_PER_SEC);
    if (sps_R < 10.0) stepIntervalUsR = 100000;
    else stepIntervalUsR = 1000000.0 / sps_R;
  }
  portEXIT_CRITICAL(&timerMuxR);
}

void readBluetoothCommands() {
  // Stub: Controls are handled via WebSocket interface
}