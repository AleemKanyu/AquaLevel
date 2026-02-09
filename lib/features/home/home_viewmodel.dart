import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/repositories/readings_repository.dart';
import '../../services/widget_service.dart';
import '../settings/settings_controller.dart';
import '../../core/constants.dart';

final sensorStreamProvider = StreamProvider<DocumentSnapshot>((ref) {
  final repository = ref.watch(readingsRepositoryProvider);
  return repository.getSensorStream();
});

final waterLevelProvider = Provider.autoDispose<WaterLevelState>((ref) {
  final asyncValue = ref.watch(sensorStreamProvider);
  final settings = ref.watch(settingsControllerProvider);

  return asyncValue.when(
    data: (snapshot) {
      print('Firestore Snapshot: ${snapshot.exists} - Data: ${snapshot.data()}');
      if (!snapshot.exists) return WaterLevelState.initial();

      final data = snapshot.data() as Map<String, dynamic>?;
      final distance = (data?['distance'] as num?)?.toDouble() ?? 0.0;

      final empty = settings.emptyDistance;
      final full = settings.fullDistance;
      final volume = settings.tankVolume.toDouble();

      final clampedDistance = distance.clamp(full, empty);
      final percent = ((empty - clampedDistance) / (empty - full)) * 100.0;
      final safePercent = percent.clamp(0.0, 100.0);
      final liters = (safePercent / 100.0) * volume;

      // Update the home screen widget with latest data
      WidgetService.updateWidget(
        percentage: safePercent,
        liters: liters,
      );

      return WaterLevelState(
        distance: distance,
        percentage: safePercent,
        liters: liters,
        isLoading: false,
      );
    },
    loading: () => WaterLevelState.initial(isLoading: true),
    error: (e, st) => WaterLevelState.initial(),
  );
});

class WaterLevelState {
  final double distance;
  final double percentage;
  final double liters;
  final bool isLoading;

  WaterLevelState({
    required this.distance,
    required this.percentage,
    required this.liters,
    required this.isLoading,
  });

  factory WaterLevelState.initial({bool isLoading = false}) {
    return WaterLevelState(
      distance: 0.0,
      percentage: 0.0,
      liters: 0.0,
      isLoading: isLoading,
    );
  }
}
