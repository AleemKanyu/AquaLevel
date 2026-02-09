import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../domain/entities/daily_usage.dart';
import '../../domain/entities/reading.dart';
import '../../domain/repositories/reading_repository.dart';
import '../datasources/local/app_database.dart';

part 'reading_repository_impl.g.dart';

class ReadingRepositoryImpl implements ReadingRepository {
  final AppDatabase _db;

  ReadingRepositoryImpl(this._db);

  @override
  Stream<List<Reading>> getAllReadings() {
    return _db.getAllReadings().map((rows) {
      return rows.map((row) => Reading(
        id: row.id,
        level: row.level,
        timestamp: DateTime.fromMillisecondsSinceEpoch(row.timestamp),
      )).toList();
    });
  }

  @override
  Future<void> addReading(Reading reading) async {
    await _db.insertReading(
      ReadingsCompanion.insert(
        level: reading.level,
        timestamp: reading.timestamp.millisecondsSinceEpoch,
      ),
    );
  }

  @override
  Future<List<DailyUsage>> getLast7DaysUsage() async {
    final results = await _db.getLast7DaysUsage();
    return results.map((r) => DailyUsage(
      day: DateTime.parse(r.day),
      minLevel: r.minLevel,
      maxLevel: r.maxLevel,
    )).toList();
  }
}

@riverpod
ReadingRepository readingRepository(ReadingRepositoryRef ref) {
  final db = ref.watch(appDatabaseProvider);
  return ReadingRepositoryImpl(db);
}
