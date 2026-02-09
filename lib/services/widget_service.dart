import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

class WidgetService {
  static const platform = MethodChannel('com.example.aqualevel_/widget');
  
  /// Updates the home screen widget with the latest tank data
  static Future<void> updateWidget({
    required double percentage,
    required double liters,
  }) async {
    try {
      print('WidgetService: Saving percentage=$percentage, liters=$liters');
      
      // Save to SharedPreferences for widget to read
      final prefs = await SharedPreferences.getInstance();
      await prefs.setDouble('widget_last_percentage', percentage);
      await prefs.setDouble('widget_last_liters', liters);
      
      print('WidgetService: Data saved to SharedPreferences');
      
      // Trigger widget update via platform channel
      try {
        await platform.invokeMethod('updateWidget', {
          'percentage': percentage,
          'liters': liters,
        });
        print('WidgetService: Platform channel called successfully');
      } catch (e) {
        print('WidgetService: Platform channel error (widget may not exist): $e');
      }
    } catch (e) {
      // Widget update is non-critical, just log the error
      print('WidgetService: Failed to update widget: $e');
    }
  }
}
