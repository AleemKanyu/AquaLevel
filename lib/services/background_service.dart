import 'package:workmanager/workmanager.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_core/firebase_core.dart';
import '../core/constants.dart';
import '../data/database/database_helper.dart';
import 'notification_service.dart';

@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    try {
      if (Firebase.apps.isEmpty) { 
          await Firebase.initializeApp();
      }
      
      final firestore = FirebaseFirestore.instance;
      final doc = await firestore.collection(AppConstants.collectionSensorData).doc(AppConstants.documentEsp32).get();

      if (!doc.exists) return Future.value(true);

      final data = doc.data();
      final distance = (data?['distance'] as num?)?.toDouble() ?? 0.0;
      final timestamp = DateTime.now().millisecondsSinceEpoch; // Or from firestore if available

      // Save to Local DB
      await DatabaseHelper.instance.insertReading(distance, timestamp);

      // Check for Notifications
      final prefs = await SharedPreferences.getInstance();
      final empty = prefs.getDouble(AppConstants.prefEmptyDistance) ?? AppConstants.defaultEmptyDistance;
      final full = prefs.getDouble(AppConstants.prefFullDistance) ?? AppConstants.defaultFullDistance;
      int lastNotified = prefs.getInt(AppConstants.prefLastNotifiedLevel) ?? -1;

      final clampedDistance = distance.clamp(full, empty);
      final percent = ((empty - clampedDistance) / (empty - full)) * 100.0;
      final safePercent = percent.clamp(0.0, 100.0).toInt();

      final notificationService = NotificationService();
      await notificationService.init();

      if (safePercent >= 100 && lastNotified != 100) {
        await notificationService.showNotification(
          id: 1001,
          title: 'Tank Full!',
          body: 'Your water tank is now 100% full.',
        );
        await prefs.setInt(AppConstants.prefLastNotifiedLevel, 100);
      } else if (safePercent <= 30 && lastNotified != 30) {
        await notificationService.showNotification(
          id: 1002,
          title: 'Low Water Level',
          body: 'Warning: Tank level is at $safePercent%.',
        );
        await prefs.setInt(AppConstants.prefLastNotifiedLevel, 30);
      } else if (safePercent > 30 && safePercent < 100) {
        // Reset notification state
         await prefs.setInt(AppConstants.prefLastNotifiedLevel, -1);
      }

      return Future.value(true);
    } catch (e) {
      print('Background Task Error: $e');
      return Future.value(false);
    }
  });
}

class BackgroundService {
  static Future<void> init() async {
    await Workmanager().initialize(
      callbackDispatcher,
      isInDebugMode: true, // TODO: Set to false in production
    );
    await Workmanager().registerPeriodicTask(
      "water_level_monitor",
      "water_level_monitor_task",
      frequency: const Duration(minutes: 15),
      constraints: Constraints(
        networkType: NetworkType.connected,
      ),
    );
  }
}
