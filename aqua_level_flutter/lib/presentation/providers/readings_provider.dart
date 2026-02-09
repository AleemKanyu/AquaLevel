import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../data/repositories/reading_repository_impl.dart';
import '../../data/repositories/sensor_repository_impl.dart';
import '../../domain/entities/reading.dart';
import '../../domain/usecases/calculate_volume_usecase.dart';
import 'settings_provider.dart';

part 'readings_provider.g.dart';

@riverpod
Stream<List<Reading>> allReadings(AllReadingsRef ref) {
  final repository = ref.watch(readingRepositoryProvider);
  return repository.getAllReadings();
}

@riverpod
Stream<double> sensorDistance(SensorDistanceRef ref) {
  final repository = ref.watch(sensorRepositoryProvider);
  return repository.getSensorDistance();
}

@riverpod
class CurrentWaterLevel extends _$CurrentWaterLevel {
  @override
  Stream<WaterLevelState> build() async* {
    final sensorDistance = ref.watch(sensorDistanceProvider);
    final settingsAsync = ref.watch(settingsNotifierProvider);
    final calculateUseCase = ref.watch(calculateVolumeUseCaseProvider);

    // Default state if waiting
    if (settingsAsync.isLoading || settingsAsync.hasError || !settingsAsync.hasValue) {
      yield WaterLevelState.initial();
      return;
    }

    final settings = settingsAsync.value!;
    
    await for (final distance in sensorDistance) {
       final volume = calculateUseCase.execute(
         currentDistance: distance,
         fullDistance: settings.fullDistance,
         emptyDistance: settings.emptyDistance,
         tankVolume: settings.tankVolume,
       );

       final percentage = (volume / settings.tankVolume) * 100;
       
       yield WaterLevelState(
         distance: distance,
         volume: volume,
         percentage: percentage,
         timestamp: DateTime.now(),
       );
    }
  }
}

class WaterLevelState {
  final double distance;
  final double volume;
  final double percentage;
  final DateTime timestamp;

  WaterLevelState({
    required this.distance,
    required this.volume,
    required this.percentage,
    required this.timestamp,
  });

  factory WaterLevelState.initial() {
    return WaterLevelState(
      distance: 0,
      volume: 0,
      percentage: 0,
      timestamp: DateTime.now(),
    );
  }
}

@riverpod
Future<List<DailyUsage>> last7DaysUsage(Last7DaysUsageRef ref) async {
   final repository = ref.watch(readingRepositoryProvider);
   return repository.getLast7DaysUsage();
}

@riverpod
Future<List<double>> hourlyUsage(HourlyUsageRef ref) async {
  // This is a simplified implementation. 
  // Ideally, we'd query the DB for today's readings and aggregate them.
  // For now, we return a mock or empty list to satisfy the UI.
  // Real implementation would involve filtering `allReadings` by today and grouping by hour.
  final allReadings = await ref.watch(allReadingsProvider.future);
  final now = DateTime.now();
  final todayReadings = allReadings.where((r) => 
    r.timestamp.year == now.year && 
    r.timestamp.month == now.month && 
    r.timestamp.day == now.day
  ).toList();

  final hourlyData = List<double>.filled(24, 0.0);
  
  // Group by hour
  for (var i = 0; i <= now.hour; i++) {
     final readingsInHour = todayReadings.where((r) => r.timestamp.hour == i).toList();
     if (readingsInHour.isNotEmpty) {
       // Just taking average level for the hour for simplicity in this migration step
       // The original app calculated usage (max - min) per hour.
       final min = readingsInHour.map((e) => e.level).reduce((a, b) => a < b ? a : b);
       final max = readingsInHour.map((e) => e.level).reduce((a, b) => a > b ? a : b);
       hourlyData[i] = max - min;
     }
  }
  return hourlyData;
}
