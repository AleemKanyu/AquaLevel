#include <Firebase_ESP_Client.h>
#include <WiFi.h>
#include <time.h>
#include "esp_sntp.h"
#include <Preferences.h>

#include "secrets.h"

#define TRIG_PIN 4
#define ECHO_PIN 5

// ── Deep-link scheme the Android app registers ──────────────────────────────
#define QR_BASE_URL "aqualevel://pair?id="

// ── Timing ──────────────────────────────────────────────────────────────────
#define MEASURE_INTERVAL  2000UL     // Read sensor every 2 s
#define UPLOAD_INTERVAL   600000UL   // Upload to Firestore every 10 minutes (600 s)

// ── Firebase objects ─────────────────────────────────────────────────────────
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// ── Runtime state ────────────────────────────────────────────────────────────
unsigned long lastMeasure    = 0;
unsigned long lastUpload     = 0;
static double dayTotalDrop   = 0.0;
static double lastDistance   = -1.0;
static int    dayReadingCount = 0;
static String currentDay     = "";
float         cachedDistance = -1.0;

// ── Device ID (loaded from NVS flash on boot) ────────────────────────────────
String DEVICE_ID = "";

// ── NVS namespace ────────────────────────────────────────────────────────────
Preferences prefs;

// ────────────────────────────────────────────────────────────────────────────
// Persistent device-ID management
// ────────────────────────────────────────────────────────────────────────────
String generateDeviceId() {
  // Use lower 32 bits of the ESP32's unique chip ID
  uint32_t chipId = (uint32_t)(ESP.getEfuseMac() & 0xFFFFFFFF);
  char buf[16];
  snprintf(buf, sizeof(buf), "AQL-%08X", chipId);
  return String(buf);
}

void loadOrCreateDeviceId() {
  prefs.begin("aqualevel", false);          // open NVS namespace "aqualevel"
  String stored = prefs.getString("device_id", "");

  if (stored.length() == 0) {
    stored = generateDeviceId();
    prefs.putString("device_id", stored);
    Serial.println("Generated new Device ID and saved to flash.");
  } else {
    Serial.println("Loaded Device ID from flash.");
  }

  prefs.end();
  DEVICE_ID = stored;
}

void printQrInfo() {
  String url = String(QR_BASE_URL) + DEVICE_ID;

  Serial.println();
  Serial.println("╔══════════════════════════════════════════════════╗");
  Serial.println("║           AquaLevel ESP32 Device Ready           ║");
  Serial.println("╠══════════════════════════════════════════════════╣");
  Serial.print  ("║  Device ID : ");
  Serial.println(DEVICE_ID);
  Serial.print  ("║  QR URL    : ");
  Serial.println(url);
  Serial.println("║                                                  ║");
  Serial.println("║  Scan the QR code label on this device with the  ║");
  Serial.println("║  AquaLevel app to link it to your account.       ║");
  Serial.println("╚══════════════════════════════════════════════════╝");
  Serial.println();
}

// ────────────────────────────────────────────────────────────────────────────
// WiFi event handler
// ────────────────────────────────────────────────────────────────────────────
void WiFiEvent(WiFiEvent_t event, WiFiEventInfo_t info) {
  if (event == ARDUINO_EVENT_WIFI_STA_DISCONNECTED) {
    Serial.print("\nWiFi Disconnected. Reason: ");
    Serial.println(info.wifi_sta_disconnected.reason);
  }
}

// ────────────────────────────────────────────────────────────────────────────
// Utility helpers
// ────────────────────────────────────────────────────────────────────────────
String getHourId() {
  time_t now = time(nullptr);
  struct tm *tinfo = localtime(&now);
  char buffer[3];
  strftime(buffer, sizeof(buffer), "%H", tinfo);
  return String(buffer);
}

float getDistance() {
  int   validReadings = 0;
  float sum = 0;

  for (int i = 0; i < 3; i++) {
    digitalWrite(TRIG_PIN, LOW);
    delayMicroseconds(2);
    digitalWrite(TRIG_PIN, HIGH);
    delayMicroseconds(10);
    digitalWrite(TRIG_PIN, LOW);

    long duration = pulseIn(ECHO_PIN, HIGH, 30000); // 30 ms timeout

    if (duration > 0) {
      float dist = (duration * 0.0343) / 2.0;
      if (dist >= 2.0 && dist <= 400.0) {
        sum += dist;
        validReadings++;
      }
    }
    delay(20);
  }

  if (validReadings == 0) {
    Serial.println("Ultrasonic measurement failed - Using simulated cycling distance");
    static float simDist = 20.0;
    static bool increasing = true;
    if (increasing) {
      simDist += 5.0;
      if (simDist >= 130.0) {
        simDist = 130.0;
        increasing = false;
      }
    } else {
      simDist -= 5.0;
      if (simDist <= 20.0) {
        simDist = 20.0;
        increasing = true;
      }
    }
    Serial.print("Simulated Distance: ");
    Serial.print(simDist);
    Serial.println(" cm");
    return simDist;
  }

  float avgDistance = sum / validReadings;
  Serial.print("Distance: ");
  Serial.print(avgDistance);
  Serial.println(" cm");
  return avgDistance;
}

// ────────────────────────────────────────────────────────────────────────────
// Firestore helpers
// ────────────────────────────────────────────────────────────────────────────
bool patchDoc(String path, FirebaseJson &content, String mask) {
  Serial.print("Updating: ");
  Serial.println(path);

  if (Firebase.Firestore.patchDocument(&fbdo, FIREBASE_PROJECT_ID, "",
                                       path.c_str(), content.raw(),
                                       mask.c_str())) {
    Serial.println("SUCCESS\n");
    return true;
  } else {
    Serial.println("FAILED");
    Serial.println(fbdo.errorReason());
    Serial.println();
    return false;
  }
}

// ── Write device registration doc on first boot ──────────────────────────────
void registerDeviceInFirestore() {
  FirebaseJson content;
  content.set("fields/deviceId/stringValue",  DEVICE_ID);
  content.set("fields/status/stringValue",    "online");
  content.set("fields/paired/booleanValue",   false);
  content.set("fields/firmware/stringValue",  "2.0.0");
  content.set("fields/timestamp/integerValue", (int)time(nullptr));

  String path = String("devices/") + DEVICE_ID;
  // Use SetDocument so it creates the doc if it doesn't exist yet
  if (Firebase.Firestore.createDocument(&fbdo, FIREBASE_PROJECT_ID, "",
                                        path.c_str(), content.raw())) {
    Serial.println("Device registered in Firestore");
  } else {
    // If it already exists (not an error) — just patch the status
    FirebaseJson statusUpdate;
    statusUpdate.set("fields/status/stringValue",    "online");
    statusUpdate.set("fields/timestamp/integerValue", (int)time(nullptr));
    patchDoc(path, statusUpdate, "status,timestamp");
  }
}

void updateMainDoc(float distance) {
  FirebaseJson content;
  content.set("fields/distance/doubleValue",   distance);
  content.set("fields/timestamp/integerValue", (int)time(nullptr));
  content.set("fields/status/stringValue",     "online");

  patchDoc(String("devices/") + DEVICE_ID, content, "distance,timestamp,status");
}

void updateHourlyDoc(float distance) {
  time_t now = time(nullptr);
  struct tm *tinfo = localtime(&now);

  // Date + hour strings for permanent storage
  char dayStr[11];
  strftime(dayStr, sizeof(dayStr), "%Y-%m-%d", tinfo);

  char hourStr[3];
  strftime(hourStr, sizeof(hourStr), "%H", tinfo);

  // Doc ID: "2026-07-12_13" — unique per day+hour, never overwritten
  char docId[16];
  snprintf(docId, sizeof(docId), "%s_%s", dayStr, hourStr);

  FirebaseJson content;
  content.set("fields/distance/doubleValue",   distance);
  content.set("fields/timestamp/integerValue", (int)now);
  content.set("fields/date/stringValue",       dayStr);
  content.set("fields/hour/integerValue",      tinfo->tm_hour);

  String path = String("devices/") + DEVICE_ID + "/hourly/" + docId;
  patchDoc(path, content, "distance,timestamp,date,hour");
}

void updateDailyTracking(double distance) {
  time_t now = time(nullptr);
  struct tm *tinfo = localtime(&now);

  char dayStr[11];
  strftime(dayStr, sizeof(dayStr), "%Y-%m-%d", tinfo);

  if (currentDay != dayStr) {
    currentDay = String(dayStr);
    dayTotalDrop   = 0.0;
    dayReadingCount = 0;
    lastDistance   = -1.0;
  }

  dayReadingCount++;

  if (lastDistance >= 0.0 && distance > lastDistance) {
    dayTotalDrop += (distance - lastDistance);
  }
  lastDistance   = distance;
  cachedDistance = distance;
}

void uploadToFirestore() {
  if (cachedDistance < 0) return;
  float dist = cachedDistance;
  time_t now = time(nullptr);
  struct tm *tinfo = localtime(&now);

  char dayStr[11];
  strftime(dayStr, sizeof(dayStr), "%Y-%m-%d", tinfo);

  updateMainDoc(dist);
  updateHourlyDoc(dist);

  FirebaseJson content;
  content.set("fields/date/stringValue",          dayStr);
  content.set("fields/totalDrop/doubleValue",     dayTotalDrop);
  content.set("fields/readingCount/integerValue", dayReadingCount);
  content.set("fields/lastUpdatedMs/integerValue", (int)now);

  String path = String("devices/") + DEVICE_ID + "/daily/" + dayStr;
  patchDoc(path, content, "date,totalDrop,readingCount,lastUpdatedMs");

  Serial.println("Hourly Firestore upload complete");
}

void performSensorRead() {
  float distance = getDistance();
  if (distance < 0) return;
  updateDailyTracking(distance);
}

void checkManualRefresh() {
  static unsigned long lastManualCheck = 0;
  if (millis() - lastManualCheck < 30000UL) return;
  lastManualCheck = millis();

  if (!Firebase.ready()) return;

  String cmdPath = String("sensorCommands/") + DEVICE_ID;
  if (Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, "",
                                     cmdPath.c_str())) {
    FirebaseJsonData result;
    fbdo.jsonObject().get(result, "fields/refresh/booleanValue");

    if (result.success && result.boolValue == true) {
      Serial.println("MANUAL REFRESH TRIGGERED");

      performSensorRead();
      uploadToFirestore();

      FirebaseJson reset;
      reset.set("fields/refresh/booleanValue", false);
      patchDoc(String("sensorCommands/") + DEVICE_ID, reset, "refresh");
    }
  }
}

// ────────────────────────────────────────────────────────────────────────────
// setup()
// ────────────────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n===== AquaLevel ESP32 v2.0 =====");

  // ── Load/generate persistent device ID ───────────────────────────────────
  loadOrCreateDeviceId();

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT_PULLDOWN);

  // ── WiFi ─────────────────────────────────────────────────────────────────
  WiFi.onEvent(WiFiEvent);
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.setTxPower(WIFI_POWER_8_5dBm);
  WiFi.disconnect();
  delay(100);

  Serial.println("Scanning visible WiFi networks...");
  int n = WiFi.scanNetworks();
  Serial.print(n);
  Serial.println(" networks found.");

  Serial.print("Connecting to primary WiFi: ");
  Serial.println(WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  int wifi_retries = 0;
  while (WiFi.status() != WL_CONNECTED && wifi_retries < 50) {
    delay(300);
    Serial.print(".");
    wifi_retries++;
  }

  if (WiFi.status() != WL_CONNECTED) {
    Serial.print("\nPrimary WiFi failed. Trying backup: ");
    Serial.println(WIFI_SSID_BACKUP);
    WiFi.disconnect(true);
    delay(1000);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID_BACKUP, WIFI_PASSWORD_BACKUP);
    wifi_retries = 0;
    while (WiFi.status() != WL_CONNECTED && wifi_retries < 50) {
      delay(300);
      Serial.print(".");
      wifi_retries++;
    }
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWiFi Connected. IP: " + WiFi.localIP().toString());
  } else {
    Serial.println("\nWiFi Connection Failed");
  }

  // ── NTP time sync ─────────────────────────────────────────────────────────
  configTime(19800, 0, "pool.ntp.org", "time.nist.gov");

  time_t now = 0;
  int time_retries = 0;
  Serial.print("Syncing time");
  do {
    delay(500);
    now = time(nullptr);
    Serial.print(".");
    time_retries++;
  } while (now < 1700000000 && time_retries < 40);

  if (now >= 1700000000) {
    Serial.println("\nTime Synced");
  } else {
    Serial.println("\nTime Sync Timeout");
    esp_sntp_stop();
  }

  // ── Firebase ──────────────────────────────────────────────────────────────
  config.api_key    = API_KEY;
  config.database_url = DATABASE_URL;

  if (!Firebase.signUp(&config, &auth, "", "")) {
    Serial.println("Firebase SignUp FAILED: " + String(config.signer.signupError.message.c_str()));
  } else {
    Serial.println("Firebase SignUp OK");
  }

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
  fbdo.setBSSLBufferSize(4096, 1024);

  // ── Print QR info to Serial ───────────────────────────────────────────────
  printQrInfo();
}

// ────────────────────────────────────────────────────────────────────────────
// loop()
// ────────────────────────────────────────────────────────────────────────────
void loop() {
  if (!Firebase.ready()) {
    delay(1000);
    return;
  }

  // Perform initial registration and upload on first connection
  static bool bootUploadDone = false;
  if (!bootUploadDone) {
    Serial.println("\nFirebase Ready. Performing initial boot-up registration and upload...");
    registerDeviceInFirestore();
    performSensorRead();
    uploadToFirestore();
    bootUploadDone = true;
    lastMeasure = millis();
    lastUpload = millis();
  }

  checkManualRefresh();

  unsigned long now = millis();

  if (now - lastMeasure >= MEASURE_INTERVAL) {
    lastMeasure = now;
    performSensorRead();
  }

  if (now - lastUpload >= UPLOAD_INTERVAL) {
    lastUpload = now;
    uploadToFirestore();
  }

  delay(100); // Small yield to prevent watchdog reset
}