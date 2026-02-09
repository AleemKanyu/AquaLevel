import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/readings_provider.dart';
import '../providers/settings_provider.dart';
import '../widgets/tank_widget.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final waterLevelAsync = ref.watch(currentWaterLevelProvider);
    final settingsAsync = ref.watch(settingsNotifierProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('AquaLevel'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () => context.push('/settings'),
          ),
          IconButton(
            icon: const Icon(Icons.bar_chart),
            onPressed: () => context.push('/analytics'),
          ),
        ],
      ),
      body: Center(
        child: SingleChildScrollView(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              settingsAsync.when(
                data: (settings) => Text(
                  'Welcome, ${settings.userName}',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                loading: () => const SizedBox.shrink(),
                error: (_, __) => const SizedBox.shrink(),
              ),
              const SizedBox(height: 20),
              waterLevelAsync.when(
                data: (state) => Column(
                  children: [
                    TankWidget(percentage: state.percentage),
                    const SizedBox(height: 20),
                    Text(
                      'Volume: ${state.volume.toInt()} Litres',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    Text(
                      'Distance: ${state.distance.toInt()} cm',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
                loading: () => const CircularProgressIndicator(),
                error: (err, stack) => Text('Error: $err'),
              ),
              const SizedBox(height: 40),
              ElevatedButton.icon(
                onPressed: () {
                   ref.read(sensorRepositoryProvider).triggerManualRefresh();
                },
                icon: const Icon(Icons.refresh),
                label: const Text('Refresh Now'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
