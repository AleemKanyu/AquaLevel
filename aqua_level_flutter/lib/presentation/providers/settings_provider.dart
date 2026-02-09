import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../data/repositories/settings_repository_impl.dart';
import '../../domain/repositories/settings_repository.dart';

part 'settings_provider.g.dart';

// We can expose the repository directly or a state class. 
// For simplicity in this migration, we'll access the repository via a provider 
// and creating a stream might be overkill if SharedPreferences doesn't support streams natively.
// But we can create a StateNotifier that reloads when changed.

@riverpod
class SettingsNotifier extends _$SettingsNotifier {
  late SettingsRepository _repository;
  
  @override
  Future<SettingsState> build() async {
    _repository = await ref.watch(settingsRepositoryProvider.future);
    return _loadState();
  }

  SettingsState _loadState() {
    return SettingsState(
      userName: _repository.getUserName() ?? 'User',
      fullDistance: _repository.getFullDistance(),
      emptyDistance: _repository.getEmptyDistance(),
      tankVolume: _repository.getTankVolume(),
      isDarkMode: _repository.getIsDarkMode(),
    );
  }

  Future<void> updateUserName(String name) async {
    await _repository.setUserName(name);
    state = AsyncValue.data(_loadState());
  }

  Future<void> updateCalibration({
    required double full,
    required double empty,
    required double volume,
  }) async {
    await _repository.setFullDistance(full);
    await _repository.setEmptyDistance(empty);
    await _repository.setTankVolume(volume);
    state = AsyncValue.data(_loadState());
  }

  Future<void> toggleTheme() async {
    final current = state.value?.isDarkMode ?? false;
    await _repository.setIsDarkMode(!current);
    state = AsyncValue.data(_loadState());
  }
}

class SettingsState {
  final String userName;
  final double fullDistance;
  final double emptyDistance;
  final double tankVolume;
  final bool isDarkMode;

  SettingsState({
    required this.userName,
    required this.fullDistance,
    required this.emptyDistance,
    required this.tankVolume,
    required this.isDarkMode,
  });
}
