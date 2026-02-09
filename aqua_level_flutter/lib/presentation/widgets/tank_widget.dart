import 'package:flutter/material.dart';

class TankWidget extends StatelessWidget {
  final double percentage;
  
  const TankWidget({super.key, required this.percentage});

  @override
  Widget build(BuildContext context) {
    // Clamp percentage to 0-100
    final safePercentage = percentage.clamp(0.0, 100.0);
    
    return Container(
      width: 200,
      height: 300,
      decoration: BoxDecoration(
        border: Border.all(color: Colors.blueAccent, width: 4),
        borderRadius: BorderRadius.circular(16),
        color: Colors.white10,
      ),
      child: Stack(
        alignment: Alignment.bottomCenter,
        children: [
          LayoutBuilder(
            builder: (context, constraints) {
              final maxHeight = constraints.maxHeight;
              // Calculate height based on percentage
              final height = (maxHeight * safePercentage) / 100;
              
              return AnimatedContainer(
                duration: const Duration(milliseconds: 800),
                curve: Curves.easeInOut,
                width: constraints.maxWidth,
                height: height,
                decoration: BoxDecoration(
                  color: Colors.blue.withOpacity(0.7),
                  borderRadius: const BorderRadius.vertical(bottom: Radius.circular(12)),
                  gradient: LinearGradient(
                    colors: [Colors.blue.shade300, Colors.blue.shade800],
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                  ),
                ),
              );
            },
          ),
          Center(
            child: Text(
              "${safePercentage.toInt()}%",
              style: Theme.of(context).textTheme.displayMedium?.copyWith(
                fontWeight: FontWeight.bold,
                color: safePercentage > 50 ? Colors.white : Colors.blue,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
