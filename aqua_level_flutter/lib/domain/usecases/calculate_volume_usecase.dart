import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'calculate_volume_usecase.g.dart';

class CalculateVolumeUseCase {
  double execute({
    required double currentDistance,
    required double fullDistance,
    required double emptyDistance,
    required double tankVolume,
  }) {
    final clampedDistance = currentDistance.clamp(fullDistance, emptyDistance);
    
    if (emptyDistance == fullDistance) return 0.0; // Avoid division by zero

    final percent = (emptyDistance - clampedDistance) / (emptyDistance - fullDistance);
    final safePercent = percent.clamp(0.0, 1.0);
    
    return safePercent * tankVolume;
  }
}

@riverpod
CalculateVolumeUseCase calculateVolumeUseCase(CalculateVolumeUseCaseRef ref) {
  return CalculateVolumeUseCase();
}
