import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/constants.dart';
import '../database/database_helper.dart';

final readingsRepositoryProvider = Provider((ref) => ReadingsRepository());

class ReadingsRepository {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  final DatabaseHelper _localDb = DatabaseHelper.instance;

  Stream<DocumentSnapshot> getSensorStream() {
    return _firestore
        .collection(AppConstants.collectionSensorData)
        .doc(AppConstants.documentEsp32)
        .snapshots();
  }

  Future<void> triggerRefresh() async {
    await _firestore
        .collection(AppConstants.collectionSensorCommands)
        .doc(AppConstants.documentEsp32)
        .update({'refresh': true});
  }

  Future<void> saveLocalReading(double level) async {
    await _localDb.insertReading(level, DateTime.now().millisecondsSinceEpoch);
  }

  Future<List<Map<String, dynamic>>> getWeeklyHistory() async {
    return await _localDb.getLast7DaysReadings();
  }
}
