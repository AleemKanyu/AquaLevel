# AquaLevel

**Smart Water Level Monitoring System using ESP32 & Android**

![Platform](https://img.shields.io/badge/platform-ESP32%20%7C%20Android-blue)
![Language](https://img.shields.io/badge/language-Kotlin%20%7C%20Arduino-green)
![Cloud](https://img.shields.io/badge/cloud-Firebase-orange)
![Status](https://img.shields.io/badge/status-Active-success)

---

## Overview

**AquaLevel** is an IoT-based water level monitoring system that measures tank water level in real time and displays it in an Android app.
It combines **Embedded Systems, Cloud Sync, and Mobile App Development** into one complete working system.

This project is designed to be:

* **Low-cost**
* **Practical for real-world use**
* **Easy to extend with new features**

---

## Features (Current Release – v1.0.0)

### Core

* **Real-time water level display in Android app**
* **Automatic hourly uploads from ESP32 to Firebase**
* **Manual refresh from app for instant readings**
* **Cloud sync for backup and cross-session access**
* **Local data persistence using Room (data survives app restarts)**

### Analytics & Visualization

* **Hourly analytics**
* **Daily analytics**
* **Weekly analytics**
* **Advanced charts and trends**

### Alerts & Controls

* **Low water level alerts / notifications**
* **Custom threshold settings for alerts**

### UI & App Experience

* **Dark mode support**
* **Settings page for app configuration**
* **Clean and stable UI**
* **Works offline with last synced data**

---

## Tech Stack

### Hardware

* ESP32
* Ultrasonic Sensor (HC-SR04 or equivalent)
* Breadboard
* Jumper wires
* Resistors (voltage divider)
* External power source

### Software

* Android Studio
* Kotlin
* Firebase Realtime Database
* Arduino IDE
* Room Database (local persistence)

---

## System Architecture

1. Ultrasonic sensor measures water level.
2. ESP32 reads sensor data.
3. ESP32 uploads readings to Firebase every **1 hour**.
4. Android app fetches cloud data.
5. Data is stored locally using **Room Database**.
6. App shows **hourly, daily, and weekly analytics with charts**.

---

## Android App

* **Live water level display**
* **Manual refresh**
* **Hourly, daily, weekly analytics**
* **Advanced charts and trends**
* **Room database for offline access**
* **Dark mode**
* **Settings page**
---

## Hardware Connections (Summary)

| Component | ESP32                          |
| --------- | ------------------------------ |
| VCC       | External Power                 |
| GND       | GND                            |
| TRIG      | GPIO Pin                       |
| ECHO      | GPIO Pin (via voltage divider) |

ESP32 GPIO pins are **3.3V tolerant**.
Use a **voltage divider** on ECHO pin.

---

## Getting Started

### Hardware Setup

* Connect components as per wiring.
* Upload ESP32 firmware.
* Configure WiFi and Firebase credentials.

### Firebase Setup

* Create Firebase project.
* Enable Realtime Database.
* Add Android app to Firebase.
* Place `google-services.json` in the app module.

### Android Setup

* Open project in Android Studio.
* Sync Gradle.
* Run on emulator or physical device.

---

## ESP32 Firmware

* **Automatic upload every 1 hour**
* **Manual check supported from Android app**
* **WiFi reconnect logic**
* **Safe handling for network drops**

---

## Known Limitations

* **Single tank support only**

---

## Planned Features

* **Multi-tank support**

---

## Screenshots
## Screenshots

<p float="left">
  <img src="https://github.com/user-attachments/assets/c57c47b4-5ac2-42b5-8563-4da624cc2d87" width="220" />
  <img src="https://github.com/user-attachments/assets/a649e876-9993-478a-9d83-0005d7f0bee0" width="220" />
  <img src="https://github.com/user-attachments/assets/21b7884c-3ecf-4ab5-8a9a-3150c2a4d37a" width="220" />
</p>

<p float="left">
  <img src="https://github.com/user-attachments/assets/e182f17e-c5f2-4bfc-92cc-e4fc1b8aa3f2" width="220" />
  <img src="https://github.com/user-attachments/assets/664ed1b8-776e-4194-b3c0-7989da32c8c7" width="220" />
  <img src="https://github.com/user-attachments/assets/c3bdb1e4-3382-4ce2-97b0-69fd5d7f528d" width="220" />
</p>

<p float="left">
  <img src="https://github.com/user-attachments/assets/3e39e412-7bc0-471e-aadb-df381d965e37" width="220" />
  <img src="https://github.com/user-attachments/assets/731c58d6-6eb2-41c1-b60f-d4887c570d35" width="220" />
</p>


---
