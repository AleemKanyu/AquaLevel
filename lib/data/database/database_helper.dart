import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';

class DatabaseHelper {
  static final DatabaseHelper instance = DatabaseHelper._init();
  static Database? _database;

  DatabaseHelper._init();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDB('readings.db');
    return _database!;
  }

  Future<Database> _initDB(String filePath) async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, filePath);

    return await openDatabase(path, version: 1, onCreate: _createDB);
  }

  Future _createDB(Database db, int version) async {
    const idType = 'INTEGER PRIMARY KEY AUTOINCREMENT';
    const realType = 'REAL';
    const integerType = 'INTEGER';

    await db.execute('''
CREATE TABLE readings (
  id $idType,
  level $realType,
  timestamp $integerType
  )
''');
  }

  Future<void> insertReading(double level, int timestamp) async {
    final db = await instance.database;
    await db.insert('readings', {
      'level': level,
      'timestamp': timestamp,
    });
  }

  Future<List<Map<String, dynamic>>> getReadings() async {
    final db = await instance.database;
    return await db.query('readings', orderBy: 'timestamp DESC');
  }

  Future<List<Map<String, dynamic>>> getLast7DaysReadings() async {
    final db = await instance.database;
    final sevenDaysAgo = DateTime.now().subtract(const Duration(days: 7)).millisecondsSinceEpoch;
    return await db.query(
      'readings',
      where: 'timestamp > ?',
      whereArgs: [sevenDaysAgo],
      orderBy: 'timestamp ASC',
    );
  }
}
