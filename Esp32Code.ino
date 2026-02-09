#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <time.h>

// ================= WIFI =================
#define WIFI_SSID "Your wifi ssid"
#define WIFI_PASSWORD "Your wifi password"

// ================= FIREBASE =================
#define API_KEY "API key of your firebase database project"
#define FIREBASE_PROJECT_ID "project id "
#define DATABASE_URL "your firebase databse url "

// ================= ULTRASONIC =================
#define TRIG_PIN 5
#define ECHO_PIN 18

// ================= INTERVALS =================
#define AUTO_UPLOAD_INTERVAL 3600000UL   // 1 hour
#define MANUAL_CHECK_INTERVAL 2000UL     // 2 seconds

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

unsigned long lastSend = 0;
unsigned long lastCommandCheck = 0;

// WIFI CHECK 
void ensureWiFi() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi lost, reconnecting...");
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    while (WiFi.status() != WL_CONNECTED) {
      delay(300);
      Serial.print(".");
    }
    Serial.println("\nWiFi reconnected");
    Serial.print("IP: ");
    Serial.println(WiFi.localIP());
  }
}

// TIME CHECK 
void ensureTimeIsValid() {
  time_t now = time(nullptr);
  if (now < 1700000000) {
    Serial.println("Time invalid, resyncing...");
    configTime(0, 0, "pool.ntp.org", "time.nist.gov");
    delay(1000);
  }
}

// DISTANCE 
float getDistanceCM() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 60000);
  if (duration == 0) return -1;

  return duration * 0.0343 / 2;
}

// MANUAL REFRESH 
void checkManualRefresh() {
  ensureWiFi();
  ensureTimeIsValid();

  if (!Firebase.ready()) return;

  if (!Firebase.Firestore.getDocument(
        &fbdo,
        FIREBASE_PROJECT_ID,
        "",
        "sensorCommands/esp32_01")) {
    return;
  }

  FirebaseJson payload;
  payload.setJsonData(fbdo.payload().c_str());

  FirebaseJsonData refreshFlag;
  payload.get(refreshFlag, "fields/refresh/booleanValue");

  if (refreshFlag.success && refreshFlag.to<bool>()) {
    Serial.println("MANUAL refresh triggered");

    float distance = getDistanceCM();
    if (distance >= 0) {
      Serial.print("MANUAL distance sent: ");
      Serial.print(distance);
      Serial.println(" cm");

      FirebaseJson content;
      content.set("fields/distance/doubleValue", distance);
      content.set("fields/timestamp/integerValue", (int)time(nullptr));

      Firebase.Firestore.patchDocument(
        &fbdo,
        FIREBASE_PROJECT_ID,
        "",
        "sensorData/esp32_01",
        content.raw(),
        "distance,timestamp"
      );
    }

    FirebaseJson clearCmd;
    clearCmd.set("fields/refresh/booleanValue", false);

    Firebase.Firestore.patchDocument(
      &fbdo,
      FIREBASE_PROJECT_ID,
      "",
      "sensorCommands/esp32_01",
      clearCmd.raw(),
      "refresh"
    );
  }
}

// SETUP 
void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("ESP32 booting...");

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  Serial.print("Connecting to WiFi");
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());

  Serial.println("Syncing time...");
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  time_t now;
  do {
    delay(500);
    now = time(nullptr);
  } while (now < 1700000000);
  Serial.println("Time synced");

  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;

  fbdo.setResponseSize(4096);
  fbdo.setBSSLBufferSize(2048, 512);

  config.tcp_data_sending_retry = 2;
  config.timeout.serverResponse = 10000;

  if (!Firebase.signUp(&config, &auth, "", "")) {
    Serial.println("Firebase auth failed");
    return;
  }

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  Serial.println("Firebase initialized");
}

// LOOP 
void loop() {
  if (!Firebase.ready()) {
    delay(50);
    return;
  }

  if (millis() - lastCommandCheck >= MANUAL_CHECK_INTERVAL) {
    lastCommandCheck = millis();
    checkManualRefresh();
  }

  if (millis() - lastSend >= AUTO_UPLOAD_INTERVAL) {
    lastSend = millis();

    ensureWiFi();
    ensureTimeIsValid();

    float distance = getDistanceCM();
    if (distance < 0) return;

    Serial.print("AUTO upload distance: ");
    Serial.print(distance);
    Serial.println(" cm");

    FirebaseJson content;
    content.set("fields/distance/doubleValue", distance);
    content.set("fields/timestamp/integerValue", (int)time(nullptr));

    Firebase.Firestore.patchDocument(
      &fbdo,
      FIREBASE_PROJECT_ID,
      "",
      "sensorData/esp32_01",
      content.raw(),
      "distance,timestamp"
    );
  }

  delay(20);
}
