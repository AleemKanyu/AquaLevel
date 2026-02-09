import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../domain/entities/daily_usage.dart';
import '../../domain/entities/reading.dart';
import '../providers/readings_provider.dart';

class AnalyticsScreen extends ConsumerWidget {
  const AnalyticsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // We need providers for historical data.
    // Assuming we add a provider for Last7DaysUsage in readings_provider or similar.
    final last7DaysAsync = ref.watch(last7DaysUsageProvider);
    // For hourly graph, we might need a specific provider that aggregates data for today
    final hourlyUsageAsync = ref.watch(hourlyUsageProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Analytics')),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            children: [
              const Text('Last 7 Days Usage', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              SizedBox(
                height: 200,
                child: last7DaysAsync.when(
                  data: (data) => _buildLast7DaysChart(context, data),
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (e, s) => Center(child: Text('Error: $e')),
                ),
              ),
              const SizedBox(height: 32),
              const Text('Hourly Usage (Today)', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
               const SizedBox(height: 16),
              SizedBox(
                 height: 200,
                 child: hourlyUsageAsync.when(
                    data: (data) => _buildHourlyChart(context, data),
                    loading: () => const Center(child: CircularProgressIndicator()),
                    error: (e, s) => Center(child: Text('Error: $e')),
                 ),
              )
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLast7DaysChart(BuildContext context, List<DailyUsage> data) {
    if (data.isEmpty) return const Center(child: Text("No data available"));

    return BarChart(
      BarChartData(
        alignment: BarChartAlignment.spaceAround,
        maxY: data.map((e) => e.maxLevel - e.minLevel).reduce((a, b) => a > b ? a : b) * 1.2, // Rough scaling
        barTouchData: BarTouchData(enabled: false),
        titlesData: FlTitlesData(
          show: true,
          bottomTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              getTitlesWidget: (double value, TitleMeta meta) {
                if (value.toInt() >= 0 && value.toInt() < data.length) {
                   final date = data[value.toInt()].day;
                   return SideTitleWidget(
                     axisSide: meta.axisSide,
                     child: Text("${date.day}/${date.month}", style: const TextStyle(fontSize: 10)),
                   );
                }
                return const SizedBox.shrink();
              },
            ),
          ),
          leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
        ),
        gridData: const FlGridData(show: false),
        borderData: FlBorderData(show: false),
        barGroups: data.asMap().entries.map((entry) {
          final index = entry.key;
          final usage = entry.value;
          final used = (usage.maxLevel - usage.minLevel).clamp(0.0, double.infinity);
          
          return BarChartGroupData(
            x: index,
            barRods: [
              BarChartRodData(
                toY: used,
                color: Colors.blueAccent,
                width: 16,
                borderRadius: BorderRadius.circular(4),
              )
            ],
          );
        }).toList(),
      ),
    );
  }

   Widget _buildHourlyChart(BuildContext context, List<double> data) {
     // Expecting data to be a list of 24 doubles representing volume/level per hour
    return LineChart(
      LineChartData(
        gridData: const FlGridData(show: true),
        titlesData: const FlTitlesData(show: false),
        borderData: FlBorderData(show: true, border: Border.all(color: Colors.white12)),
        minX: 0,
        maxX: 23,
        minY: 0,
        lineBarsData: [
          LineChartBarData(
            spots: data.asMap().entries.map((e) => FlSpot(e.key.toDouble(), e.value)).toList(),
            isCurved: true,
            color: Colors.greenAccent,
            barWidth: 3,
            dotData: const FlDotData(show: false),
            belowBarData: BarAreaData(show: true, color: Colors.greenAccent.withOpacity(0.2)),
          ),
        ],
      ),
    );
  }
}
