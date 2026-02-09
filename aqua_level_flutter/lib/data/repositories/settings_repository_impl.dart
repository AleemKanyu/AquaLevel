import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../domain/repositories/settings_repository.dart';
import '../datasources/preferences/settings_service.dart';

part 'settings_repository_impl.g.dart';

class SettingsRepositoryImpl implements SettingsRepository {
  final SettingsService _service;

  SettingsRepositoryImpl(this._service);

  @override
  String? getUserName() => _service.getUserName();

  @override
  Future<void> setUserName(String name) => _service.setUserName(name);

  @override
  double getFullDistance() => _service.getFullDistance();

  @override
  Future<void> setFullDistance(double value) => _service.setFullDistance(value);

  @override
  double getEmptyDistance() => _service.getEmptyDistance();

  @override
  Future<void> setEmptyDistance(double value) => _service.setEmptyDistance(value);

  @override
  double getTankVolume() => _service.getTankVolume();

  @override
  Future<void> setTankVolume(double value) => _service.setTankVolume(value);

  @override
  bool getIsDarkMode() => _service.getIsDarkMode();

  @override
  Future<void> setIsDarkMode(bool value) => _service.setIsDarkMode(value);
}

@riverpod
Future<SettingsRepository> settingsRepository(SettingsRepositoryRef ref) async {
  final service = await ref.watch(settingsServiceProvider.future);
  return SettingsRepositoryImpl(service);
}
