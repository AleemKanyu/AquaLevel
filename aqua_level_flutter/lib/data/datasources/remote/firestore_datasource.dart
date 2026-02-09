import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'firestore_datasource.g.dart';

class FirestoreDataSource {
  final FirebaseFirestore _firestore;

  FirestoreDataSource(this._firestore);

  Stream<double> getSensorDistance() {
    return _firestore
        .collection('sensorData')
        .document('esp32_01')
        .snapshots()
        .map((snapshot) {
      if (snapshot.exists && snapshot.data() != null) {
        return (snapshot.data()!['distance'] as num?)?.toDouble() ?? 0.0;
      }
      return 0.0;
    });
  }

  Future<void> triggerManualRefresh() async {
    await _firestore
        .collection('sensorCommands')
        .document('esp32_01')
        .update({'refresh': true});
  }
}

@riverpod
FirestoreDataSource firestoreDataSource(FirestoreDataSourceRef ref) {
  return FirestoreDataSource(FirebaseFirestore.instance);
}
