import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../domain/repositories/sensor_repository.dart';
import '../datasources/remote/firestore_datasource.dart';

part 'sensor_repository_impl.g.dart';

class SensorRepositoryImpl implements SensorRepository {
  final FirestoreDataSource _dataSource;

  SensorRepositoryImpl(this._dataSource);

  @override
  Stream<double> getSensorDistance() {
    return _dataSource.getSensorDistance();
  }

  @override
  Future<void> triggerManualRefresh() async {
    await _dataSource.triggerManualRefresh();
  }
}

@riverpod
SensorRepository sensorRepository(SensorRepositoryRef ref) {
  final dataSource = ref.watch(firestoreDataSourceProvider);
  return SensorRepositoryImpl(dataSource);
}
