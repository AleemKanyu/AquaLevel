#include <dummy.h>

#include <Firebase_ESP_Client.h>
#include <WiFi.h>
#include <time.h>

#include "secrets.h"

#define TRIG_PIN 5
#define ECHO_PIN 18

#define AUTO_UPLOAD_INTERVAL 3600000UL // 1 hour

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

unsigned long lastAutoUpload = 0;

String getHourId() {
  time_t now = time(nullptr);
  struct tm *tinfo = localtime(&now);
  char buffer[3];
  strftime(buffer, sizeof(buffer), "%H", tinfo);
  return String(buffer);
}

float getDistance() {

  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 60000);

  if (duration == 0) {
    Serial.println("Ultrasonic timeout");
    return -1;
  }

  float distance = duration * 0.0343 / 2;

  Serial.print("Measured Distance: ");
  Serial.println(distance);

  return distance;
}

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

void updateMainDoc(float distance) {

  FirebaseJson content;
  content.set("fields/distance/doubleValue", distance);
  content.set("fields/timestamp/integerValue", (int)time(nullptr));
  content.set("fields/status/stringValue", "online");

  patchDoc("sensorData/esp32_01", content, "distance,timestamp,status");
}

void updateHourlyDoc(float distance) {

  time_t now = time(nullptr);
  struct tm *tinfo = localtime(&now);

  static int currentDay = -1;
  if (currentDay != tinfo->tm_mday) {
    currentDay = tinfo->tm_mday;
    Serial.println(
        "New day/boot detected. Clearing stale 'future' hours for today...");
    for (int i = tinfo->tm_hour + 1; i < 24; i++) {
      char delDocPath[64];
      snprintf(delDocPath, sizeof(delDocPath),
               "sensorData/esp32_01/hourly_current/%02d", i);
      Firebase.Firestore.deleteDocument(&fbdo, FIREBASE_PROJECT_ID, "",
                                        delDocPath);
    }
  }

  FirebaseJson content;
  content.set("fields/distance/doubleValue", distance);
  content.set("fields/timestamp/integerValue", (int)now);

  String path = "sensorData/esp32_01/hourly_current/" + getHourId();

  patchDoc(path, content, "distance,timestamp");
}

void performMeasurement() {

  float distance = getDistance();
  if (distance < 0)
    return;

  updateMainDoc(distance);
  updateHourlyDoc(distance);
}

void checkManualRefresh() {

  if (!Firebase.ready())
    return;

  if (Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, "",
                                     "sensorCommands/esp32_01")) {

    FirebaseJsonData result;
    fbdo.jsonObject().get(result, "fields/refresh/booleanValue");

    if (result.success && result.boolValue == true) {

      Serial.println("MANUAL REFRESH TRIGGERED");

      performMeasurement();

      // Reset refresh flag
      FirebaseJson reset;
      reset.set("fields/refresh/booleanValue", false);

      patchDoc("sensorCommands/esp32_01", reset, "refresh");
    }
  }
}

void setup() {

  Serial.begin(115200);
  Serial.println("\n===== ESP32 START =====");

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.println("\nWiFi Connected");

  configTime(19800, 0, "pool.ntp.org", "time.nist.gov");

  time_t now;
  do {
    delay(500);
    now = time(nullptr);
  } while (now < 1700000000);

  Serial.println("Time Synced");

  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;

  if (!Firebase.signUp(&config, &auth, "", "")) {
    Serial.println("SignUp FAILED");
    Serial.println(config.signer.signupError.message.c_str());
  } else {
    Serial.println("SignUp SUCCESS");
  }

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  fbdo.setBSSLBufferSize(4096, 1024);

  while (!Firebase.ready()) {
    Serial.print(".");
    delay(500);
  }

  Serial.println("\nFirebase Ready");
}

void loop() {

  if (!Firebase.ready()) {
    delay(1000);
    return;
  }

  // Manual refresh check
  checkManualRefresh();

  // Automatic hourly update
  if (millis() - lastAutoUpload >= AUTO_UPLOAD_INTERVAL) {

    Serial.println("AUTO HOURLY UPDATE");

    lastAutoUpload = millis();

    performMeasurement();
  }

  delay(2000);
}