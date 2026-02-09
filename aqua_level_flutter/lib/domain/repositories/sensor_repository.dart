abstract class SensorRepository {
  Stream<double> getSensorDistance();
  Future<void> triggerManualRefresh();
}
