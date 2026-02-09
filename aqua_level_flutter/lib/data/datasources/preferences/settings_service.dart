import 'package:shared_preferences/shared_preferences.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'settings_service.g.dart';

class SettingsService {
  final SharedPreferences _prefs;

  SettingsService(this._prefs);

  static const _keyUserName = 'user_name';
  static const _keyFullDistance = 'full_distance';
  static const _keyEmptyDistance = 'empty_distance';
  static const _keyTankVolume = 'tank_volume';
  static const _keyIsDarkMode = 'is_dark_mode';

  String? getUserName() => _prefs.getString(_keyUserName);
  Future<void> setUserName(String name) => _prefs.setString(_keyUserName, name);

  double getFullDistance() => _prefs.getDouble(_keyFullDistance) ?? 20.0;
  Future<void> setFullDistance(double value) => _prefs.setDouble(_keyFullDistance, value);

  double getEmptyDistance() => _prefs.getDouble(_keyEmptyDistance) ?? 130.0;
  Future<void> setEmptyDistance(double value) => _prefs.setDouble(_keyEmptyDistance, value);

  double getTankVolume() => _prefs.getDouble(_keyTankVolume) ?? 2000.0;
  Future<void> setTankVolume(double value) => _prefs.setDouble(_keyTankVolume, value);

  bool getIsDarkMode() => _prefs.getBool(_keyIsDarkMode) ?? false;
  Future<void> setIsDarkMode(bool value) => _prefs.setBool(_keyIsDarkMode, value);
}

@riverpod
Future<SettingsService> settingsService(SettingsServiceRef ref) async {
  final prefs = await SharedPreferences.getInstance();
  return SettingsService(prefs);
}
