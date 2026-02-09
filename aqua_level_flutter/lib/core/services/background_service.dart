import 'package:workmanager/workmanager.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_core/firebase_core.dart';
// We need to initialize Firebase manually in background isolate
// and use direct dependencies as Riverpod might not be easily accessible in pure background isolate without setup.
// For simplicity, we'll implement the logic directly here.

const taskName = 'water_level_monitor';

@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    if (task == taskName) {
      try {
        await Firebase.initializeApp();
        final firestore = FirebaseFirestore.instance;
        final prefs = await SharedPreferences.getInstance();

        // 1. Fetch sensor data
        final snapshot = await firestore.collection('sensorData').document('esp32_01').get();
        
        if (snapshot.exists && snapshot.data() != null) {
          final distance = (snapshot.data()!['distance'] as num?)?.toDouble() ?? 0.0;
           // timestamp from firestore or current time? 
           // Android worker used calculation: now - timestamp.
           // We'll use current time for the local DB entry for simplicity, or we can use FS timestamp.
           // For notification, we check thresholds.
           
           final fullDist = prefs.getDouble('full_distance') ?? 20.0;
           final emptyDist = prefs.getDouble('empty_distance') ?? 130.0;
           final tankVolume = prefs.getDouble('tank_volume') ?? 2000.0;

           final clampedDist = distance.clamp(fullDist, emptyDist);
           if (emptyDist == fullDist) return true;

           final percent = ((emptyDist - clampedDist) / (emptyDist - fullDist)) * 100;
           final safePercent = percent.clamp(0.0, 100.0);

           // 2. Notifications Logic
           final lastNotified = prefs.getInt('last_notified_level') ?? -1;
           final flutterLocalNotificationsPlugin = FlutterLocalNotificationsPlugin();

           // Initialize notifications if needed (might be needed in isolate)
           const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
           const iosSettings = DarwinInitializationSettings();
           const initSettings = InitializationSettings(android: androidSettings, iOS: iosSettings);
           await flutterLocalNotificationsPlugin.initialize(initSettings);

           if (safePercent >= 100 && lastNotified != 100) {
             await _showNotification(flutterLocalNotificationsPlugin, "Tank Full!", "Your water tank is now 100% full.", 1001);
             await prefs.setInt('last_notified_level', 100);
           } else if (safePercent <= 30 && lastNotified != 30) {
             await _showNotification(flutterLocalNotificationsPlugin, "Low Water Level", "Warning: Tank level is at ${safePercent.toInt()}%.", 1002);
             await prefs.setInt('last_notified_level', 30);
           } else if (safePercent > 30 && safePercent < 100) {
              await prefs.setInt('last_notified_level', -1);
           }
           
           // 3. Save to Local DB (Drift)
           // Setting up Drift in background isolate requires opening the database again.
           // See: https://drift.simonbinder.eu/docs/advanced-features/isolates/
           // For MVP migration, we might skip saving to local DB in background if it's complex, 
           // OR we can just use the provided database class if it works (it creates a new connection).
           // Let's defer strict local DB background save for now to ensure stability, 
           // or use a direct SQLite insertion if Drift setup is too verbose here.
           // Given the scope, notifications are the critical part of the background worker.
        }
      } catch (e) {
        print("Background Task Error: $e");
        return Future.value(false);
      }
    }
    return Future.value(true);
  });
}

Future<void> _showNotification(FlutterLocalNotificationsPlugin plugin, String title, String body, int id) async {
  const androidDetails = AndroidNotificationDetails(
    'water_level_notifications',
    'Water Level Alerts',
    importance: Importance.max,
    priority: Priority.high,
  );
  const iosDetails = DarwinNotificationDetails();
  const details = NotificationDetails(android: androidDetails, iOS: iosDetails);
  
  await plugin.show(id, title, body, details);
}
