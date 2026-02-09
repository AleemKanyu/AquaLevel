import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/theme.dart';
import '../../features/settings/settings_controller.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  late TextEditingController _userNameController;
  late TextEditingController _emptyDistController;
  late TextEditingController _fullDistController;
  late TextEditingController _volumeController;

  @override
  void initState() {
    super.initState();
    final settings = ref.read(settingsControllerProvider);
    _userNameController = TextEditingController(text: settings.userName);
    _emptyDistController = TextEditingController(text: settings.emptyDistance.toString());
    _fullDistController = TextEditingController(text: settings.fullDistance.toString());
    _volumeController = TextEditingController(text: settings.tankVolume.toString());
  }

  @override
  void dispose() {
    _userNameController.dispose();
    _emptyDistController.dispose();
    _fullDistController.dispose();
    _volumeController.dispose();
    super.dispose();
  }

  void _saveSettings() {
    final controller = ref.read(settingsControllerProvider.notifier);
    controller.updateUserName(_userNameController.text);
    controller.updateEmptyDistance(double.tryParse(_emptyDistController.text) ?? 130);
    controller.updateFullDistance(double.tryParse(_fullDistController.text) ?? 20);
    controller.updateTankVolume(int.tryParse(_volumeController.text) ?? 1000);

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text('Settings saved!'),
        backgroundColor: AppColors.waterBlue,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        margin: const EdgeInsets.all(16),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final settings = ref.watch(settingsControllerProvider);
    final isDark = settings.isDarkMode;

    return Scaffold(
      backgroundColor: isDark ? AppColors.backgroundDark : AppColors.backgroundLight,
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: isDark
                ? [const Color(0xFF0A0A1A), const Color(0xFF000000)]
                : [const Color(0xFFE8F4FD), const Color(0xFFF2F6FC)],
          ),
        ),
        child: SafeArea(
          child: Column(
            children: [
              // Top Bar
              _buildTopBar(context, ref, isDark),

              // Content
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Profile Section
                      _buildSectionLabel('Profile', isDark),
                      const SizedBox(height: 12),
                      GlassCard(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _buildLabel('User Name', isDark),
                            _buildTextField(_userNameController, 'Enter your name', isDark),
                          ],
                        ),
                      ),
                      const SizedBox(height: 28),

                      // Calibration Section
                      _buildSectionLabel('Tank Calibration', isDark),
                      const SizedBox(height: 12),
                      GlassCard(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _buildLabel('Full Distance (cm)', isDark),
                            _buildTextField(_fullDistController, 'e.g. 20', isDark, isNumber: true),
                            _buildDivider(isDark),
                            _buildLabel('Empty Distance (cm)', isDark),
                            _buildTextField(_emptyDistController, 'e.g. 130', isDark, isNumber: true),
                            _buildDivider(isDark),
                            _buildLabel('Tank Volume (L)', isDark),
                            _buildTextField(_volumeController, 'e.g. 1000', isDark, isNumber: true),
                          ],
                        ),
                      ),
                      const SizedBox(height: 28),

                      // Performance Section
                      _buildSectionLabel('Performance', isDark),
                      const SizedBox(height: 12),
                      GlassCard(
                        child: Column(
                          children: [
                            _buildToggleRow(
                              'Reduce Animations',
                              'Turn off animations for better performance',
                              settings.reduceAnimations,
                              () => ref.read(settingsControllerProvider.notifier).toggleReduceAnimations(),
                              isDark,
                            ),
                            _buildDivider(isDark),
                            _buildToggleRow(
                              'Glass Effect',
                              'Enable blur effects (uses more resources)',
                              settings.enableGlassEffect,
                              () => ref.read(settingsControllerProvider.notifier).toggleGlassEffect(),
                              isDark,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 28),

                      // Save Button
                      GestureDetector(
                        onTap: _saveSettings,
                        child: Container(
                          width: double.infinity,
                          height: 56,
                          decoration: BoxDecoration(
                            gradient: const LinearGradient(
                              colors: [AppColors.fabGradientStart, AppColors.fabGradientEnd],
                            ),
                            borderRadius: BorderRadius.circular(16),
                            boxShadow: [
                              BoxShadow(
                                color: AppColors.waterBlue.withOpacity(0.4),
                                blurRadius: 16,
                                offset: const Offset(0, 6),
                              ),
                            ],
                          ),
                          child: const Center(
                            child: Text(
                              'Save Settings',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w600,
                                color: Colors.white,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTopBar(BuildContext context, WidgetRef ref, bool isDark) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          GlassButton(
            onTap: () => Navigator.pop(context),
            child: Icon(
              Icons.arrow_back_ios_new,
              size: 18,
              color: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Text(
              'Settings',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.w600,
                color: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
              ),
            ),
          ),
          GlassButton(
            onTap: () => ref.read(settingsControllerProvider.notifier).toggleTheme(),
            child: Icon(
              isDark ? Icons.wb_sunny_rounded : Icons.nightlight_round,
              size: 22,
              color: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionLabel(String text, bool isDark) {
    return Text(
      text,
      style: const TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.5,
        color: AppColors.waterBlue,
      ),
    );
  }

  Widget _buildLabel(String text, bool isDark) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Text(
        text,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w500,
          color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
        ),
      ),
    );
  }

  Widget _buildTextField(TextEditingController controller, String hint, bool isDark, {bool isNumber = false}) {
    return TextField(
      controller: controller,
      keyboardType: isNumber ? TextInputType.number : TextInputType.text,
      style: TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w500,
        color: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
      ),
      decoration: InputDecoration(
        hintText: hint,
        hintStyle: TextStyle(
          color: isDark ? AppColors.textSecondaryDark.withOpacity(0.5) : AppColors.textSecondaryLight.withOpacity(0.5),
        ),
        border: InputBorder.none,
        contentPadding: const EdgeInsets.symmetric(vertical: 8),
      ),
    );
  }

  Widget _buildDivider(bool isDark) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 12),
      height: 1,
      color: isDark ? Colors.white.withOpacity(0.08) : Colors.black.withOpacity(0.06),
    );
  }

  Widget _buildToggleRow(String title, String subtitle, bool value, VoidCallback onTap, bool isDark) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w500,
                  color: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                subtitle,
                style: TextStyle(
                  fontSize: 12,
                  color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
                ),
              ),
            ],
          ),
        ),
        GestureDetector(
          onTap: onTap,
          child: Container(
            width: 52,
            height: 32,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              color: value ? AppColors.waterBlue : (isDark ? Colors.white.withOpacity(0.1) : Colors.grey.withOpacity(0.3)),
            ),
            child: AnimatedAlign(
              duration: const Duration(milliseconds: 200),
              alignment: value ? Alignment.centerRight : Alignment.centerLeft,
              child: Container(
                width: 28,
                height: 28,
                margin: const EdgeInsets.symmetric(horizontal: 2),
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  color: Colors.white,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
