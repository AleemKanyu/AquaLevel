class Reading {
  final int? id;
  final double level;
  final DateTime timestamp;

  const Reading({
    this.id,
    required this.level,
    required this.timestamp,
  });
}
