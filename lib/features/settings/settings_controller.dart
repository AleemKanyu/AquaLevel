import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../core/constants.dart';

final sharedPreferencesProvider = Provider<SharedPreferences>((ref) {
  throw UnimplementedError();
});

final settingsControllerProvider = NotifierProvider<SettingsController, SettingsState>(SettingsController.new);

class SettingsState {
  final double emptyDistance;
  final double fullDistance;
  final int tankVolume;
  final bool isDarkMode;
  final String userName;
  final bool reduceAnimations;
  final bool enableGlassEffect;

  SettingsState({
    required this.emptyDistance,
    required this.fullDistance,
    required this.tankVolume,
    required this.isDarkMode,
    required this.userName,
    required this.reduceAnimations,
    required this.enableGlassEffect,
  });

  SettingsState copyWith({
    double? emptyDistance,
    double? fullDistance,
    int? tankVolume,
    bool? isDarkMode,
    String? userName,
    bool? reduceAnimations,
    bool? enableGlassEffect,
  }) {
    return SettingsState(
      emptyDistance: emptyDistance ?? this.emptyDistance,
      fullDistance: fullDistance ?? this.fullDistance,
      tankVolume: tankVolume ?? this.tankVolume,
      isDarkMode: isDarkMode ?? this.isDarkMode,
      userName: userName ?? this.userName,
      reduceAnimations: reduceAnimations ?? this.reduceAnimations,
      enableGlassEffect: enableGlassEffect ?? this.enableGlassEffect,
    );
  }
}

class SettingsController extends Notifier<SettingsState> {
  @override
  SettingsState build() {
    final prefs = ref.watch(sharedPreferencesProvider);
    return SettingsState(
      emptyDistance: prefs.getDouble(AppConstants.prefEmptyDistance) ?? AppConstants.defaultEmptyDistance,
      fullDistance: prefs.getDouble(AppConstants.prefFullDistance) ?? AppConstants.defaultFullDistance,
      tankVolume: prefs.getInt(AppConstants.prefTankVolume) ?? AppConstants.defaultTankVolume,
      isDarkMode: prefs.getBool(AppConstants.prefIsDarkMode) ?? false,
      userName: prefs.getString(AppConstants.prefUserName) ?? 'User',
      reduceAnimations: prefs.getBool(AppConstants.prefReduceAnimations) ?? false,
      enableGlassEffect: prefs.getBool(AppConstants.prefEnableGlassEffect) ?? true,
    );
  }

  void updateEmptyDistance(double value) {
    ref.read(sharedPreferencesProvider).setDouble(AppConstants.prefEmptyDistance, value);
    state = state.copyWith(emptyDistance: value);
  }

  void updateFullDistance(double value) {
    ref.read(sharedPreferencesProvider).setDouble(AppConstants.prefFullDistance, value);
    state = state.copyWith(fullDistance: value);
  }

  void updateTankVolume(int value) {
    ref.read(sharedPreferencesProvider).setInt(AppConstants.prefTankVolume, value);
    state = state.copyWith(tankVolume: value);
  }

  void toggleTheme() {
    final newValue = !state.isDarkMode;
    ref.read(sharedPreferencesProvider).setBool(AppConstants.prefIsDarkMode, newValue);
    state = state.copyWith(isDarkMode: newValue);
  }

  void updateUserName(String name) {
    ref.read(sharedPreferencesProvider).setString(AppConstants.prefUserName, name);
    state = state.copyWith(userName: name);
  }

  void toggleReduceAnimations() {
    final newValue = !state.reduceAnimations;
    ref.read(sharedPreferencesProvider).setBool(AppConstants.prefReduceAnimations, newValue);
    state = state.copyWith(reduceAnimations: newValue);
  }

  void toggleGlassEffect() {
    final newValue = !state.enableGlassEffect;
    ref.read(sharedPreferencesProvider).setBool(AppConstants.prefEnableGlassEffect, newValue);
    state = state.copyWith(enableGlassEffect: newValue);
  }
}
