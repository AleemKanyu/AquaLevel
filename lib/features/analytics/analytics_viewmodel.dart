import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import '../../data/database/database_helper.dart';
import '../../core/constants.dart';

/// Model for a single reading data point
class ReadingDataPoint {
  final DateTime timestamp;
  final double level;
  final double distance;

  ReadingDataPoint({
    required this.timestamp,
    required this.level,
    required this.distance,
  });
}

/// Model for hourly aggregated data
class HourlyUsage {
  final int hour;
  final double avgLevel;
  final int readingCount;

  HourlyUsage({
    required this.hour,
    required this.avgLevel,
    required this.readingCount,
  });
}

/// Model for daily aggregated data
class DailyUsage {
  final String dayName;
  final DateTime date;
  final double avgLevel;
  final double usageLiters;

  DailyUsage({
    required this.dayName,
    required this.date,
    required this.avgLevel,
    required this.usageLiters,
  });
}

/// Provider for real-time sensor readings from Firestore
final sensorReadingsStreamProvider = StreamProvider<List<ReadingDataPoint>>((ref) {
  final firestore = FirebaseFirestore.instance;
  
  // Get readings from the last 24 hours
  final dayAgo = DateTime.now().subtract(const Duration(hours: 24));
  
  return firestore
      .collection(AppConstants.collectionSensorData)
      .doc(AppConstants.documentEsp32)
      .snapshots()
      .map((snapshot) {
        if (!snapshot.exists) return <ReadingDataPoint>[];
        
        final data = snapshot.data()!;
        final distance = (data['distance'] as num?)?.toDouble() ?? 0;
        final timestamp = data['timestamp'] as int? ?? DateTime.now().millisecondsSinceEpoch ~/ 1000;
        
        // Convert distance to level percentage (inverted - closer = fuller)
        // Assuming emptyDistance=130, fullDistance=20
        final emptyDist = 130.0;
        final fullDist = 20.0;
        final level = ((emptyDist - distance) / (emptyDist - fullDist) * 100).clamp(0.0, 100.0);
        
        return [
          ReadingDataPoint(
            timestamp: DateTime.fromMillisecondsSinceEpoch(timestamp * 1000),
            level: level,
            distance: distance,
          )
        ];
      });
});

/// Provider for hourly usage data (last 24 hours)
final hourlyUsageProvider = Provider<List<HourlyUsage>>((ref) {
  // Generate sample hourly data for the last 24 hours
  // In production, this would aggregate from stored readings
  final now = DateTime.now();
  final hourlyData = <HourlyUsage>[];
  
  for (int i = 23; i >= 0; i--) {
    final hour = (now.hour - i) % 24;
    // Simulate realistic water usage pattern
    double usage;
    if (hour >= 6 && hour <= 9) {
      usage = 60 + (hour - 6) * 15; // Morning peak
    } else if (hour >= 18 && hour <= 21) {
      usage = 55 + (hour - 18) * 12; // Evening peak
    } else if (hour >= 0 && hour <= 5) {
      usage = 10 + hour * 2; // Night low
    } else {
      usage = 30 + (hour % 6) * 8; // Daytime moderate
    }
    
    hourlyData.add(HourlyUsage(
      hour: hour,
      avgLevel: usage.clamp(0, 100),
      readingCount: 4,
    ));
  }
  
  return hourlyData;
});

/// Provider for daily usage data (last 7 days)
final dailyUsageProvider = Provider<List<DailyUsage>>((ref) {
  final now = DateTime.now();
  final dayNames = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  final dailyData = <DailyUsage>[];
  
  for (int i = 6; i >= 0; i--) {
    final date = now.subtract(Duration(days: i));
    final dayName = dayNames[date.weekday % 7];
    
    // Simulate realistic daily usage - higher on weekends
    double usage;
    if (date.weekday == 6 || date.weekday == 7) {
      usage = 180 + (date.day % 30) * 2; // Weekend higher
    } else {
      usage = 120 + (date.day % 20) * 3; // Weekday moderate
    }
    
    dailyData.add(DailyUsage(
      dayName: dayName,
      date: date,
      avgLevel: (usage / 2).clamp(0, 100),
      usageLiters: usage,
    ));
  }
  
  return dailyData;
});

/// Provider for real-time current level (from Firestore stream)
final currentLevelProvider = StreamProvider<double>((ref) {
  final firestore = FirebaseFirestore.instance;
  
  return firestore
      .collection(AppConstants.collectionSensorData)
      .doc(AppConstants.documentEsp32)
      .snapshots()
      .map((snapshot) {
        if (!snapshot.exists) return 0.0;
        
        final data = snapshot.data()!;
        final distance = (data['distance'] as num?)?.toDouble() ?? 0;
        
        // Convert distance to level percentage
        final emptyDist = 130.0;
        final fullDist = 20.0;
        final level = ((emptyDist - distance) / (emptyDist - fullDist) * 100).clamp(0.0, 100.0);
        
        return level;
      });
});

/// Weekly history from local database
final weeklyHistoryProvider = FutureProvider<List<Map<String, dynamic>>>((ref) async {
  final db = DatabaseHelper.instance;
  return await db.getLast7DaysReadings();
});
