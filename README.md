# Aqua Level 💧
**Smart Water Level Monitoring System using ESP32 & Android**

![Platform](https://img.shields.io/badge/platform-ESP32%20%7C%20Android-blue)
![Language](https://img.shields.io/badge/language-Kotlin%20%7C%20Arduino-green)
![Cloud](https://img.shields.io/badge/cloud-Firebase-orange)
![Status](https://img.shields.io/badge/status-Active-success)

---

## Overview
**Aqua Level** is an IoT-based water level monitoring system that measures water levels in real time and displays them on an Android application.  
It combines **embedded systems**, **cloud computing**, and **mobile development** to provide a complete end-to-end solution.

The system is designed to be **low-cost**, **scalable**, and suitable for real-world deployment.

---

## Features
- Real-time water level monitoring
- ESP32-to-Firebase cloud communication
- Android app with live & historical data
- Daily data storage (date-wise tracking)
- Prevents overflow & supports water management
- Works with battery or adapter power

---

## Tech Stack

### Hardware
- ESP32
- Ultrasonic Sensor (HC-SR04 or equivalent)
- Breadboard, jumper wires
- Resistors (voltage divider)
- External power source

### Software
- Android Studio
- Kotlin
- Firebase Realtime Database / Firestore
- Arduino IDE

---

## System Architecture
1. Sensor measures water level.
2. ESP32 processes the data.
3. Data is sent to Firebase via Wi-Fi.
4. Android app fetches and displays data.
5. Historical data is stored date-wise.

---

## Android App
- Live water level display
- Cloud-synced data
- Daily history tracking
- Simple and clean UI

---

## Hardware Connections (Summary)

| Component | ESP32 |
|--------|------|
| VCC | External Power |
| GND | GND |
| TRIG | GPIO Pin |
| ECHO | GPIO Pin (via voltage divider) |

ESP32 GPIO pins are **3.3V tolerant**, so a voltage divider is required for ECHO.

---

## Getting Started

### Hardware Setup
1. Connect components as per wiring.
2. Upload Arduino code to ESP32.
3. Configure Wi-Fi credentials.

### Firebase Setup
1. Create a Firebase project.
2. Enable Realtime Database / Firestore.
3. Connect Firebase with Android app.

### Android App
1. Open project in Android Studio.
2. Sync Gradle.
3. Run on emulator or physical device.

---

## Screenshots

> <img 
    src="https://github.com/user-attachments/assets/23458882-c76e-497d-b4bf-7cceb51b2d17"
  alt="Aqua Level App Screenshot"
  width="320"
/>



