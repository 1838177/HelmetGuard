#include <Arduino.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <NimBLEDevice.h>
#include <Preferences.h>
#include <Wire.h>
#include <esp_task_wdt.h>

#include <cmath>
#include <string>

#include "BoardConfig.h"
#include "HelmetProtocol.h"

namespace {

constexpr char kServiceUuid[] = "0000FFF0-0000-1000-8000-00805F9B34FB";
constexpr char kTelemetryUuid[] = "0000FFF1-0000-1000-8000-00805F9B34FB";
constexpr char kEventUuid[] = "0000FFF2-0000-1000-8000-00805F9B34FB";
constexpr char kCommandUuid[] = "0000FFF3-0000-1000-8000-00805F9B34FB";
constexpr float kGravity = 9.80665f;
constexpr float kRadToDeg = 57.2957795f;
constexpr uint32_t kSampleIntervalMs = 1000UL / HG_SAMPLE_RATE_HZ;
constexpr uint32_t kTelemetryIntervalMs = 1000UL / HG_TELEMETRY_RATE_HZ;

Adafruit_MPU6050 mpu;
Preferences preferences;
NimBLECharacteristic* telemetryCharacteristic = nullptr;
NimBLECharacteristic* eventCharacteristic = nullptr;
NimBLECharacteristic* commandCharacteristic = nullptr;

bool imuHealthy = false;
bool calibrated = false;
bool clientConnected = false;
uint8_t sequenceNumber = 0;
uint32_t deviceId = 0;
uint32_t eventId = 0;
uint32_t epochAtSync = 0;
uint32_t millisAtSync = 0;

float gyroOffsetX = 0;
float gyroOffsetY = 0;
float gyroOffsetZ = 0;
float axG = 0, ayG = 0, azG = 1;
float gxDps = 0, gyDps = 0, gzDps = 0;
float rollDeg = 0, pitchDeg = 0, yawDeg = 0;
float totalAccelerationG = 1;
float totalGyroDps = 0;
uint8_t batteryPercent = 0;
uint32_t lastSampleMs = 0;
uint32_t lastTelemetryMs = 0;
uint32_t lastBatteryMs = 0;
uint32_t findBuzzerUntilMs = 0;

bool alarmActive = false;
bool incidentAwaitingAck = false;
uint32_t lastIncidentNotifyMs = 0;
float incidentPeakG = 0;
float incidentPeakGyro = 0;
float incidentStillSeconds = 0;

enum class DetectorState { Idle, Observing, Verifying };
DetectorState detectorState = DetectorState::Idle;
uint32_t detectorStartedMs = 0;
uint32_t stillStartedMs = 0;
bool detectorSevere = false;
bool detectorDropSuspected = false;
float preImpactMotionEma = 0;
uint32_t freeFallStartedMs = 0;
uint32_t recentFreeFallDurationMs = 0;
uint32_t recentFreeFallEndedMs = 0;

bool rawButtonPressed = false;
bool stableButtonPressed = false;
uint32_t rawButtonChangedMs = 0;
uint32_t buttonPressedMs = 0;
uint32_t lastShortReleaseMs = 0;
uint8_t pendingClicks = 0;

inline uint8_t nextSequence() { return sequenceNumber++; }

void setBuzzer(bool enabled) {
    digitalWrite(HG_PIN_BUZZER, enabled == static_cast<bool>(HG_BUZZER_ACTIVE_HIGH) ? HIGH : LOW);
}

void updateBuzzer(uint32_t now) {
    bool enabled = false;
    if (alarmActive) {
        // A distinctive repeating SOS-like cadence without blocking sensor sampling.
        const uint32_t phase = now % 1800UL;
        enabled = (phase < 180) || (phase >= 340 && phase < 520) || (phase >= 680 && phase < 1180);
    } else if (static_cast<int32_t>(findBuzzerUntilMs - now) > 0) {
        enabled = (now / 150UL) % 2UL == 0;
    }
    setBuzzer(enabled);
}

bool notifyFrame(NimBLECharacteristic* characteristic, uint8_t type, const uint8_t* payload, uint8_t length) {
    if (!clientConnected || characteristic == nullptr) return false;
    uint8_t frame[helmetguard::kFrameOverhead + helmetguard::kMaximumPayload];
    const size_t frameLength = helmetguard::encodeFrame(type, nextSequence(), payload, length, frame, sizeof(frame));
    if (frameLength == 0) return false;
    characteristic->setValue(frame, frameLength);
    if (characteristic == eventCharacteristic) characteristic->indicate();
    else characteristic->notify();
    return true;
}

void sendButton(uint8_t code) {
    notifyFrame(eventCharacteristic, helmetguard::kButton, &code, 1);
}

uint8_t readBatteryPercent() {
#if HG_HAS_BATTERY_DIVIDER
    const uint32_t millivoltsAtAdc = analogReadMilliVolts(HG_PIN_BATTERY_ADC);
    const float batteryVolts = (millivoltsAtAdc / 1000.0f) * HG_BATTERY_DIVIDER_RATIO;
    const float normalized = (batteryVolts - HG_BATTERY_EMPTY_V) / (HG_BATTERY_FULL_V - HG_BATTERY_EMPTY_V);
    return static_cast<uint8_t>(constrain(normalized * 100.0f, 0.0f, 100.0f));
#else
    return 0;  // Status bit 2 remains clear: value unavailable, never pretend the cell is full.
#endif
}

void sendTelemetry(uint32_t now) {
    uint8_t payload[24] = {};
    helmetguard::putU32Le(payload, now);
    helmetguard::putI16Le(payload + 4, helmetguard::scaledI16(axG, 1000.0f));
    helmetguard::putI16Le(payload + 6, helmetguard::scaledI16(ayG, 1000.0f));
    helmetguard::putI16Le(payload + 8, helmetguard::scaledI16(azG, 1000.0f));
    helmetguard::putI16Le(payload + 10, helmetguard::scaledI16(gxDps, 10.0f));
    helmetguard::putI16Le(payload + 12, helmetguard::scaledI16(gyDps, 10.0f));
    helmetguard::putI16Le(payload + 14, helmetguard::scaledI16(gzDps, 10.0f));
    helmetguard::putI16Le(payload + 16, helmetguard::scaledI16(rollDeg, 10.0f));
    helmetguard::putI16Le(payload + 18, helmetguard::scaledI16(pitchDeg, 10.0f));
    helmetguard::putI16Le(payload + 20, helmetguard::scaledI16(yawDeg, 10.0f));
    payload[22] = batteryPercent;
    payload[23] = (imuHealthy ? 0x01 : 0x00) |
                  (calibrated ? 0x02 : 0x00) |
                  (HG_HAS_BATTERY_DIVIDER ? 0x04 : 0x00);
    notifyFrame(telemetryCharacteristic, helmetguard::kTelemetry, payload, sizeof(payload));
}

void sendIncident() {
    uint8_t payload[16] = {};
    helmetguard::putU32Le(payload, deviceId);
    helmetguard::putU32Le(payload + 4, eventId);
    helmetguard::putI16Le(payload + 8, helmetguard::scaledI16(incidentPeakG, 100.0f));
    helmetguard::putI16Le(payload + 10, helmetguard::scaledI16(incidentPeakGyro, 1.0f));
    helmetguard::putU16Le(payload + 12, static_cast<uint16_t>(constrain(incidentStillSeconds * 10.0f, 0.0f, 65535.0f)));
    payload[14] = HG_HARDWARE_COUNTDOWN_SECONDS;
    payload[15] = 0x01 | (alarmActive ? 0x02 : 0x00);
    notifyFrame(eventCharacteristic, helmetguard::kIncident, payload, sizeof(payload));
    lastIncidentNotifyMs = millis();
}

void startIncident(float peakG, float peakGyro, float stillSeconds) {
    eventId = preferences.getUInt("event", 0) + 1U;
    preferences.putUInt("event", eventId);  // Writes only once per event, never per sample.
    incidentPeakG = peakG;
    incidentPeakGyro = peakGyro;
    incidentStillSeconds = stillSeconds;
    incidentAwaitingAck = true;
    alarmActive = true;
    sendIncident();
    Serial.printf("INCIDENT id=%lu peak=%.2fg gyro=%.0f dps\n", static_cast<unsigned long>(eventId), peakG, peakGyro);
}

void resetDetector() {
    detectorState = DetectorState::Idle;
    detectorStartedMs = 0;
    stillStartedMs = 0;
    detectorSevere = false;
    detectorDropSuspected = false;
    incidentPeakG = 0;
    incidentPeakGyro = 0;
}

void processHardwareDetector(uint32_t now) {
    if (!imuHealthy || !calibrated || alarmActive) return;

    // Keep slow background evidence while excluding the immediate free-fall/impact interval.
    if (totalAccelerationG <= 0.35f) {
        if (freeFallStartedMs == 0) freeFallStartedMs = now;
    } else if (freeFallStartedMs != 0) {
        recentFreeFallDurationMs = now - freeFallStartedMs;
        recentFreeFallEndedMs = now;
        freeFallStartedMs = 0;
    }
    if (detectorState == DetectorState::Idle && totalAccelerationG > 0.5f && totalAccelerationG < 2.5f) {
        const float instantaneousMotion = fminf(1.0f, fabsf(totalAccelerationG - 1.0f) * 2.2f + totalGyroDps / 180.0f);
        preImpactMotionEma = preImpactMotionEma * 0.98f + instantaneousMotion * 0.02f;
    }

    switch (detectorState) {
        case DetectorState::Idle:
            if (totalAccelerationG >= HG_IMPACT_CANDIDATE_G) {
                detectorState = DetectorState::Observing;
                detectorStartedMs = now;
                incidentPeakG = totalAccelerationG;
                incidentPeakGyro = totalGyroDps;
                detectorSevere = totalAccelerationG >= HG_HARD_IMPACT_G;
                detectorDropSuspected = preImpactMotionEma < 0.10f &&
                    recentFreeFallDurationMs >= 80UL && now - recentFreeFallEndedMs <= 250UL;
            }
            break;
        case DetectorState::Observing:
            incidentPeakG = fmaxf(incidentPeakG, totalAccelerationG);
            incidentPeakGyro = fmaxf(incidentPeakGyro, totalGyroDps);
            detectorSevere = detectorSevere || totalAccelerationG >= HG_HARD_IMPACT_G;
            if (now - detectorStartedMs >= HG_OBSERVATION_MS) {
                const bool rotationEvidence = incidentPeakGyro >= HG_ROTATION_EVIDENCE_DPS;
                const bool extremeImpact = incidentPeakG >= 10.0f;
                if (rotationEvidence || extremeImpact) {
                    detectorState = DetectorState::Verifying;
                    detectorStartedMs = now;
                    stillStartedMs = 0;
                } else {
                    resetDetector();  // Typical speed-bump shape: vertical impulse, little rotation.
                }
            }
            break;
        case DetectorState::Verifying: {
            const bool still = fabsf(totalAccelerationG - 1.0f) <= HG_POST_STILL_ACC_DELTA_G &&
                               totalGyroDps <= HG_POST_STILL_GYRO_DPS;
            if (still) {
                if (stillStartedMs == 0) stillStartedMs = now;
                if (now - stillStartedMs >= HG_POST_STILL_REQUIRED_MS) {
                    if (detectorDropSuspected && incidentPeakG < HG_HARD_IMPACT_G) {
                        resetDetector();
                    } else {
                        startIncident(incidentPeakG, incidentPeakGyro, (now - stillStartedMs) / 1000.0f);
                        resetDetector();
                    }
                }
            } else {
                stillStartedMs = 0;
            }
            if (now - detectorStartedMs > 6000UL) {
                // With a connected phone, defer ambiguous moving severe events to the richer App
                // detector. Disconnected hardware still provides a conservative fail-safe alarm.
                if (detectorSevere && !clientConnected) startIncident(incidentPeakG, incidentPeakGyro, 0);
                resetDetector();
            }
            break;
        }
    }
}

bool calibrateGyroscope() {
    Serial.println("Keep helmet still: calibrating gyroscope...");
    constexpr int kSamples = 400;
    double sx = 0, sy = 0, sz = 0;
    sensors_event_t acceleration, gyro, temperature;
    for (int i = 0; i < kSamples; ++i) {
        if (!mpu.getEvent(&acceleration, &gyro, &temperature)) return false;
        sx += gyro.gyro.x * kRadToDeg;
        sy += gyro.gyro.y * kRadToDeg;
        sz += gyro.gyro.z * kRadToDeg;
        delay(3);
        esp_task_wdt_reset();
    }
    gyroOffsetX = static_cast<float>(sx / kSamples);
    gyroOffsetY = static_cast<float>(sy / kSamples);
    gyroOffsetZ = static_cast<float>(sz / kSamples);
    calibrated = true;
    yawDeg = 0;
    Serial.printf("Gyro offsets %.3f %.3f %.3f dps\n", gyroOffsetX, gyroOffsetY, gyroOffsetZ);
    return true;
}

void sampleImu(uint32_t now) {
    sensors_event_t acceleration, gyro, temperature;
    if (!mpu.getEvent(&acceleration, &gyro, &temperature)) {
        imuHealthy = false;
        return;
    }
    imuHealthy = true;
    const float dt = lastSampleMs == 0 ? 1.0f / HG_SAMPLE_RATE_HZ : constrain((now - lastSampleMs) / 1000.0f, 0.001f, 0.1f);
    lastSampleMs = now;
    axG = acceleration.acceleration.x / kGravity;
    ayG = acceleration.acceleration.y / kGravity;
    azG = acceleration.acceleration.z / kGravity;
    gxDps = gyro.gyro.x * kRadToDeg - gyroOffsetX;
    gyDps = gyro.gyro.y * kRadToDeg - gyroOffsetY;
    gzDps = gyro.gyro.z * kRadToDeg - gyroOffsetZ;
    totalAccelerationG = sqrtf(axG * axG + ayG * ayG + azG * azG);
    totalGyroDps = sqrtf(gxDps * gxDps + gyDps * gyDps + gzDps * gzDps);

    const float accelRoll = atan2f(ayG, azG) * kRadToDeg;
    const float accelPitch = atan2f(-axG, sqrtf(ayG * ayG + azG * azG)) * kRadToDeg;
    rollDeg = 0.98f * (rollDeg + gxDps * dt) + 0.02f * accelRoll;
    pitchDeg = 0.98f * (pitchDeg + gyDps * dt) + 0.02f * accelPitch;
    yawDeg += gzDps * dt;
    if (yawDeg > 180.0f) yawDeg -= 360.0f;
    if (yawDeg < -180.0f) yawDeg += 360.0f;
    processHardwareDetector(now);
}

void updateButton(uint32_t now) {
    const bool pressed = digitalRead(HG_PIN_BUTTON) == LOW;
    if (pressed != rawButtonPressed) {
        rawButtonPressed = pressed;
        rawButtonChangedMs = now;
    }
    if (pressed != stableButtonPressed && now - rawButtonChangedMs >= 30UL) {
        stableButtonPressed = pressed;
        if (pressed) {
            buttonPressedMs = now;
        } else {
            const uint32_t held = now - buttonPressedMs;
            if (held >= 1200UL) {
                pendingClicks = 0;
                alarmActive = false;
                incidentAwaitingAck = false;
                sendButton(3);
            } else {
                ++pendingClicks;
                lastShortReleaseMs = now;
            }
        }
    }
    if (pendingClicks > 0 && now - lastShortReleaseMs >= 350UL) {
        sendButton(pendingClicks >= 2 ? 2 : 1);
        pendingClicks = 0;
    }
}

void processCommand(const uint8_t* data, size_t length) {
    helmetguard::FrameView frame{};
    if (!helmetguard::decodeFrame(data, length, frame) || frame.type != helmetguard::kCommand || frame.payloadLength < 1) return;
    const uint8_t command = frame.payload[0];
    switch (command) {
        case helmetguard::kAckIncident:
            if (frame.payloadLength == 6 && helmetguard::readU32Le(frame.payload + 1) == eventId) incidentAwaitingAck = false;
            break;
        case helmetguard::kCancel:
            if (frame.payloadLength == 6 && helmetguard::readU32Le(frame.payload + 1) == eventId) {
                alarmActive = false;
                incidentAwaitingAck = false;
            }
            break;
        case helmetguard::kFind:
            if (frame.payloadLength == 2 && !alarmActive) findBuzzerUntilMs = millis() + constrain(frame.payload[1], 1, 30) * 1000UL;
            break;
        case helmetguard::kCalibrate:
            if (frame.payloadLength == 1 && !alarmActive) calibrateGyroscope();
            break;
        case helmetguard::kSyncTime:
            if (frame.payloadLength == 5) {
                epochAtSync = helmetguard::readU32Le(frame.payload + 1);
                millisAtSync = millis();
            }
            break;
        default:
            break;
    }
}

class ServerCallbacks final : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer*) override {
        clientConnected = true;
        Serial.println("BLE client connected");
    }
    void onDisconnect(NimBLEServer*) override {
        clientConnected = false;
        NimBLEDevice::startAdvertising();
        Serial.println("BLE client disconnected; advertising restarted");
    }
};

class CommandCallbacks final : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* characteristic) override {
        const std::string value = characteristic->getValue();
        processCommand(reinterpret_cast<const uint8_t*>(value.data()), value.size());
    }
};

ServerCallbacks serverCallbacks;
CommandCallbacks commandCallbacks;

void initializeBle() {
    char name[32];
    snprintf(name, sizeof(name), "%s-%04lX", HG_DEVICE_NAME_PREFIX, static_cast<unsigned long>(deviceId & 0xffffU));
    NimBLEDevice::init(name);
    NimBLEDevice::setMTU(64);
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);
    NimBLEServer* server = NimBLEDevice::createServer();
    server->setCallbacks(&serverCallbacks);
    NimBLEService* service = server->createService(kServiceUuid);
    telemetryCharacteristic = service->createCharacteristic(kTelemetryUuid, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    eventCharacteristic = service->createCharacteristic(kEventUuid, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::INDICATE);
    commandCharacteristic = service->createCharacteristic(kCommandUuid, NIMBLE_PROPERTY::WRITE);
    commandCharacteristic->setCallbacks(&commandCallbacks);
    service->start();
    NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
    advertising->setName(name);
    // Advertise the Bluetooth-base UUID in its 16-bit form. This leaves enough of the
    // 31-byte primary advertising packet for flags and avoids pushing the discoverability
    // data/name out on ROMs that do not reliably merge the scan response.
    advertising->addServiceUUID(NimBLEUUID(static_cast<uint16_t>(0xFFF0)));
    advertising->setScanResponse(true);
    advertising->start();
    Serial.printf("BLE advertising as %s\n", name);
}

}  // namespace

void setup() {
    Serial.begin(115200);
    delay(300);
    pinMode(HG_PIN_BUTTON, INPUT_PULLUP);
    pinMode(HG_PIN_BUZZER, OUTPUT);
    setBuzzer(false);
#if HG_HAS_BATTERY_DIVIDER
    analogReadResolution(12);
    analogSetPinAttenuation(HG_PIN_BATTERY_ADC, ADC_11db);
#endif
    esp_task_wdt_init(8, true);
    esp_task_wdt_add(nullptr);

    const uint64_t mac = ESP.getEfuseMac();
    deviceId = static_cast<uint32_t>(mac ^ (mac >> 32U));
    preferences.begin("helmetguard", false);
    eventId = preferences.getUInt("event", 0);

    Wire.begin(HG_PIN_I2C_SDA, HG_PIN_I2C_SCL, 400000);
    imuHealthy = mpu.begin(0x68, &Wire);
    if (imuHealthy) {
        mpu.setAccelerometerRange(MPU6050_RANGE_16_G);
        mpu.setGyroRange(MPU6050_RANGE_2000_DEG);
        mpu.setFilterBandwidth(MPU6050_BAND_44_HZ);
        calibrateGyroscope();
    } else {
        Serial.println("ERROR: MPU6050 not found at 0x68");
    }
    batteryPercent = readBatteryPercent();
    initializeBle();
}

void loop() {
    const uint32_t now = millis();
    if (now - lastSampleMs >= kSampleIntervalMs) sampleImu(now);
    if (now - lastBatteryMs >= 10000UL) {
        lastBatteryMs = now;
        batteryPercent = readBatteryPercent();
    }
    if (now - lastTelemetryMs >= kTelemetryIntervalMs) {
        lastTelemetryMs = now;
        sendTelemetry(now);
    }
    if (incidentAwaitingAck && now - lastIncidentNotifyMs >= 1000UL) sendIncident();
    updateButton(now);
    updateBuzzer(now);
    esp_task_wdt_reset();
    delay(1);
}
