import 'dart:io';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'app_database.g.dart';

class Readings extends Table {
  IntColumn get id => integer().autoIncrement()();
  RealColumn get level => real()();
  // Store timestamp as integer (milliseconds since epoch) to match Android Room
  IntColumn get timestamp => integer()(); 
}

@DriftDatabase(tables: [Readings])
class AppDatabase extends _$AppDatabase {
  AppDatabase() : super(_openConnection());

  @override
  int get schemaVersion => 1;

  Stream<List<Reading>> getAllReadings() {
    return (select(readings)..orderBy([(t) => OrderingTerm.desc(t.timestamp)]))
        .watch();
  }

  Future<int> insertReading(ReadingsCompanion reading) {
    return into(readings).insert(reading);
  }

  // Get last 7 days usage
  // Note: Complex aggregation queries in Drift might need custom SQL or Dart processing.
  // We can do it in Dart for simplicity if data size isn't huge, or use custom SQL.
  // Given "SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') as day..."
  Future<List<DailyUsageResult>> getLast7DaysUsage() {
    // Implementing the custom query logic
    const sql = """
      SELECT 
        strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') as day,
        MIN(level) as minLevel,
        MAX(level) as maxLevel
      FROM readings
      GROUP BY day
      ORDER BY day DESC
      LIMIT 7
    """;
    
    return customSelect(sql).map((row) {
      return DailyUsageResult(
        day: row.read<String>('day'),
        minLevel: row.read<double>('minLevel'),
        maxLevel: row.read<double>('maxLevel'),
      );
    }).get();
  }
}

class DailyUsageResult {
  final String day;
  final double minLevel;
  final double maxLevel;

  DailyUsageResult({required this.day, required this.minLevel, required this.maxLevel});
}

LazyDatabase _openConnection() {
  return LazyDatabase(() async {
    final dbFolder = await getApplicationDocumentsDirectory();
    final file = File(p.join(dbFolder.path, 'readings.sqlite'));
    return NativeDatabase.createInBackground(file);
  });
}

// Provider definition
@riverpod
AppDatabase appDatabase(AppDatabaseRef ref) {
  return AppDatabase();
}
