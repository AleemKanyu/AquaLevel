class AppConstants {
  // Firebase Collections
  static const String collectionSensorData = 'sensorData';
  static const String documentEsp32 = 'esp32_01';
  static const String collectionSensorCommands = 'sensorCommands';

  // Shared Preferences Keys
  static const String prefEmptyDistance = 'empty_distance';
  static const String prefFullDistance = 'full_distance';
  static const String prefTankVolume = 'tank_volume';
  static const String prefLastNotifiedLevel = 'last_notified_level';
  static const String prefIsDarkMode = 'is_dark_mode';
  static const String prefUserName = 'user_name';
  static const String prefReduceAnimations = 'reduce_animations';
  static const String prefEnableGlassEffect = 'enable_glass_effect';

  // Default Calibration Values
  static const double defaultEmptyDistance = 130.0;
  static const double defaultFullDistance = 20.0;
  static const int defaultTankVolume = 2000;
}
