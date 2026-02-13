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

## Features (Latest Release – v1.3.0)

### Core & Physics
* **Realistic Water Physics Engine**: Custom column-based simulation with free flow, sloshing, and splash effects.
* **Gyroscope Integration**: Water surface reacts dynamically to device tilt (toggleable in Settings).
* **Real-time water level display**: Fluid animations with particle effects.
* **Automatic hourly uploads** from ESP32 to Firebase.
* **Offline Support**: Local data persistence using Room Database.

### Analytics & Visualization
* **Interactive Charts**: "Draw path" animations for hourly, daily, and weekly usage.
* **Smart Insights**: Trend indicators and average usage calculations.
* **History Tracking**: Comprehensive logs of water consumption.

###  Controls & Customization
* **Unit Conversion**: Toggle between **Liters** and **Gallons** instantly.
* **Smart Alerts**: Customizable low-level thresholds with notification support.
* **Tank Calibration**: Configure custom "Full" and "Empty" sensor distances.
* **Haptic Feedback**: Tactile enhancements for interactive elements.

###  UI & Design
* **Duolingo-Inspired Aesthetic**: Vibrant cards, bold typography, and rounded geometry.
* **Dark Mode**: High-contrast, battery-friendly dark theme (default).
* **Smooth Animations**: Entry transitions and fluid interface updates.

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

* Android Studio (Koala Feature Drop)
* Kotlin (Coroutines, Flow)
* Android Jetpack (ViewModel, LiveData, Room)
* Firebase Realtime Database
* Arduino IDE (for ESP32)

---

## System Architecture

1. Ultrasonic sensor measures water level.
2. ESP32 reads sensor data.
3. ESP32 uploads readings to Firebase every **1 hour**.
4. Android app fetches cloud data.
5. Data is stored locally using **Room Database**.
6. App shows **hourly, daily, and weekly analytics with charts**.

---


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

<p float="left">
  <img src="https://github.com/user-attachments/assets/67b030db-9597-466b-9cd1-493c068d9543" width="220" />
  <img src="https://github.com/user-attachments/assets/3bacc9a8-2c8b-46ce-b030-5f4cf0498b02" width="220" />
  <img src="https://github.com/user-attachments/assets/a174fa4a-0a81-4f54-a0db-aab81f3a3288" width="220" />
</p>

<p float="left">
  <img src="https://github.com/user-attachments/assets/7b329a58-1077-41e9-b34e-b08aa014051f" width="220" />
  <img src="https://github.com/user-attachments/assets/ca8a4230-5a7f-4e76-821d-632cc7338ffe" width="220" />
  <img src="https://github.com/user-attachments/assets/fe41f239-1e74-40c2-be97-9c4012e9453f" width="220" />
</p>



---
