// Battery telemetry firmware, stage 2: real readings over Bluetooth.
//
// Sends the 11-byte telemetry message from docs/protocol.md as BLE notifications,
// and accepts the 2-byte sample-interval config message. Current comes from an
// ACS711 (OUT on GPIO34) and voltage from a 20k/10k divider (midpoint on GPIO35).

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLE2902.h>

// From docs/protocol.md
static const char* DEVICE_NAME = "BatteryTelemetry";
static const char* SERVICE_UUID = "19fbab80-3f3c-4c26-849f-bd5d6d909e73";
static const char* TELEMETRY_UUID = "f43e5ab0-61e2-4a31-8ba7-aa81c8d9102c";
static const char* CONFIG_UUID = "05eb3787-17e0-485a-8a51-b3d07c842208";
static const uint8_t PROTOCOL_VERSION = 1;
static const size_t MESSAGE_LENGTH = 11;
static const uint16_t MIN_INTERVAL_MS = 100;
static const uint16_t MAX_INTERVAL_MS = 5000;

// Hardware
static const int CURRENT_PIN = 34;            // ACS711 OUT
static const int VOLTAGE_PIN = 35;            // divider midpoint
static const float ZERO_CURRENT_MV = 1650.0f; // ACS711 output at 0 A (VCC / 2 at 3.3 V)
static const float SENSITIVITY_MV_PER_A = 110.0f;  // ±12.5 A version; use 55 for ±25 A
static const float DIVIDER_RATIO = 3.0f;      // (20k + 10k) / 10k
static const int SAMPLES = 16;                // averaged per reading to smooth ADC noise

BLECharacteristic* telemetryChar = nullptr;
// Written from the Bluetooth task, read in loop(), so volatile.
volatile bool clientConnected = false;
volatile uint16_t intervalMs = 1000;  // default from docs/protocol.md

uint16_t sequence = 0;  // wraps from 65535 to 0 on its own
uint32_t lastSendMs = 0;

// Average several calibrated ADC readings, in millivolts at the pin.
float readPinMillivolts(int pin) {
  uint32_t total = 0;
  for (int i = 0; i < SAMPLES; i++) total += analogReadMilliVolts(pin);
  return (float)total / SAMPLES;
}

// version(1) seq(2) time(4) mV(2) mA(2), little-endian. The ESP32 is little-endian,
// so copying each value's bytes gives the right order.
void packReading(uint8_t* out, uint16_t seq, uint32_t timeMs, uint16_t mV, int16_t mA) {
  out[0] = PROTOCOL_VERSION;
  memcpy(out + 1, &seq, 2);
  memcpy(out + 3, &timeMs, 4);
  memcpy(out + 7, &mV, 2);
  memcpy(out + 9, &mA, 2);
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server) override {
    clientConnected = true;
    Serial.println("client connected");
  }

  void onDisconnect(BLEServer* server) override {
    clientConnected = false;
    Serial.println("client disconnected, advertising again");
    BLEDevice::startAdvertising();  // without this, nobody could reconnect
  }
};

class ConfigCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    auto value = characteristic->getValue();  // String on ESP32 core 3.x, std::string on 2.x
    if (value.length() != 2) {
      Serial.printf("ignored config: expected 2 bytes, got %u\n", (unsigned)value.length());
      return;
    }
    uint16_t ms = (uint8_t)value[0] | ((uint8_t)value[1] << 8);  // little-endian
    if (ms < MIN_INTERVAL_MS || ms > MAX_INTERVAL_MS) {
      Serial.printf("ignored config: %u ms is outside %u-%u\n", ms, MIN_INTERVAL_MS, MAX_INTERVAL_MS);
      return;  // keep the current interval, as docs/protocol.md says
    }
    intervalMs = ms;
    Serial.printf("interval set to %u ms\n", ms);
  }
};

void setup() {
  Serial.begin(115200);
  analogSetPinAttenuation(CURRENT_PIN, ADC_11db);  // full 0-3.1 V range
  analogSetPinAttenuation(VOLTAGE_PIN, ADC_11db);

  BLEDevice::init(DEVICE_NAME);
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService* service = server->createService(SERVICE_UUID);
  telemetryChar = service->createCharacteristic(TELEMETRY_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  telemetryChar->addDescriptor(new BLE2902());  // the switch a phone flips to turn notifications on
  BLECharacteristic* configChar =
      service->createCharacteristic(CONFIG_UUID, BLECharacteristic::PROPERTY_WRITE);
  configChar->setCallbacks(new ConfigCallbacks());
  service->start();

  // A 128-bit UUID and the name don't both fit in one 31-byte advertising packet,
  // so the UUID goes in the advertisement and the name in the scan response.
  BLEAdvertisementData advertisement;
  advertisement.setFlags(0x06);  // general discoverable, Bluetooth Low Energy only
  advertisement.setCompleteServices(BLEUUID(SERVICE_UUID));
  BLEAdvertisementData scanResponse;
  scanResponse.setName(DEVICE_NAME);

  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->setAdvertisementData(advertisement);
  advertising->setScanResponseData(scanResponse);
  BLEDevice::startAdvertising();
  Serial.println("advertising as BatteryTelemetry");
}

void loop() {
  uint32_t now = millis();
  if (now - lastSendMs < intervalMs) return;
  lastSendMs = now;

  uint16_t mV = (uint16_t)(readPinMillivolts(VOLTAGE_PIN) * DIVIDER_RATIO);
  int16_t mA = (int16_t)((readPinMillivolts(CURRENT_PIN) - ZERO_CURRENT_MV) * 1000.0f / SENSITIVITY_MV_PER_A);

  if (!clientConnected) {
    Serial.printf("no client: %u mV, %d mA\n", mV, mA);
    return;  // count only messages actually sent, so gaps mean real losses
  }

  uint8_t message[MESSAGE_LENGTH];
  packReading(message, sequence, now, mV, mA);
  telemetryChar->setValue(message, MESSAGE_LENGTH);
  telemetryChar->notify();
  Serial.printf("sent seq %u: %u mV, %d mA\n", sequence, mV, mA);
  sequence++;
}
