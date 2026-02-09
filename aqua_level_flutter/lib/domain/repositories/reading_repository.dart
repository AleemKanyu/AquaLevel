import '../entities/daily_usage.dart';
import '../entities/reading.dart';

abstract class ReadingRepository {
  Stream<List<Reading>> getAllReadings();
  Future<void> addReading(Reading reading);
  Future<List<DailyUsage>> getLast7DaysUsage();
}
