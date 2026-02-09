import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/repositories/settings_repository_impl.dart';
import '../providers/settings_provider.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  final _formKey = GlobalKey<FormState>();
  late TextEditingController _nameController;
  late TextEditingController _fullDistController;
  late TextEditingController _emptyDistController;
  late TextEditingController _volumeController;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController();
    _fullDistController = TextEditingController();
    _emptyDistController = TextEditingController();
    _volumeController = TextEditingController();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _fullDistController.dispose();
    _emptyDistController.dispose();
    _volumeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final settingsAsync = ref.watch(settingsNotifierProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
      ),
      body: settingsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, stack) => Center(child: Text('Error: $err')),
        data: (settings) {
          // Initialize controllers with current values if not renamed
          if (_nameController.text.isEmpty) _nameController.text = settings.userName;
          if (_fullDistController.text.isEmpty) _fullDistController.text = settings.fullDistance.toString();
          if (_emptyDistController.text.isEmpty) _emptyDistController.text = settings.emptyDistance.toString();
          if (_volumeController.text.isEmpty) _volumeController.text = settings.tankVolume.toInt().toString();

          return SingleChildScrollView(
            padding: const EdgeInsets.all(16.0),
            child: Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildSectionHeader(context, 'User Profile'),
                  TextFormField(
                    controller: _nameController,
                    decoration: const InputDecoration(
                      labelText: 'User Name',
                      border: OutlineInputBorder(),
                    ),
                    validator: (value) => value == null || value.isEmpty ? 'Please enter a name' : null,
                  ),
                  const SizedBox(height: 24),
                  _buildSectionHeader(context, 'Calibration'),
                  TextFormField(
                    controller: _fullDistController,
                    decoration: const InputDecoration(
                      labelText: 'Full Distance (cm)',
                      helperText: 'Sensor reading when tank is FULL',
                      border: OutlineInputBorder(),
                    ),
                    keyboardType: TextInputType.number,
                    validator: (value) => double.tryParse(value ?? '') == null ? 'Invalid number' : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _emptyDistController,
                    decoration: const InputDecoration(
                      labelText: 'Empty Distance (cm)',
                      helperText: 'Sensor reading when tank is EMPTY',
                      border: OutlineInputBorder(),
                    ),
                     keyboardType: TextInputType.number,
                    validator: (value) => double.tryParse(value ?? '') == null ? 'Invalid number' : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _volumeController,
                    decoration: const InputDecoration(
                      labelText: 'Tank Volume (Litres)',
                      border: OutlineInputBorder(),
                    ),
                     keyboardType: TextInputType.number,
                    validator: (value) => double.tryParse(value ?? '') == null ? 'Invalid number' : null,
                  ),
                   const SizedBox(height: 24),
                  _buildSectionHeader(context, 'Appearance'),
                  SwitchListTile(
                    title: const Text('Dark Mode'),
                    value: settings.isDarkMode,
                    onChanged: (value) {
                      ref.read(settingsNotifierProvider.notifier).toggleTheme();
                    },
                  ),
                  const SizedBox(height: 32),
                  SizedBox(
                    width: double.infinity,
                    height: 50,
                    child: ElevatedButton(
                      onPressed: () {
                        if (_formKey.currentState!.validate()) {
                          ref.read(settingsNotifierProvider.notifier).updateUserName(_nameController.text);
                          ref.read(settingsNotifierProvider.notifier).updateCalibration(
                            full: double.parse(_fullDistController.text),
                            empty: double.parse(_emptyDistController.text),
                            volume: double.parse(_volumeController.text),
                          );
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Settings Saved')),
                          );
                          Navigator.pop(context);
                        }
                      },
                      child: const Text('Save Settings'),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildSectionHeader(BuildContext context, String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8.0),
      child: Text(
        title,
        style: Theme.of(context).textTheme.titleMedium?.copyWith(
          color: Theme.of(context).primaryColor,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}
