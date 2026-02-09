abstract class SettingsRepository {
  String? getUserName();
  Future<void> setUserName(String name);

  double getFullDistance();
  Future<void> setFullDistance(double value);

  double getEmptyDistance();
  Future<void> setEmptyDistance(double value);

  double getTankVolume();
  Future<void> setTankVolume(double value);

  bool getIsDarkMode();
  Future<void> setIsDarkMode(bool value);
}
