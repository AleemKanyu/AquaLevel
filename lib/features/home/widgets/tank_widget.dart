import 'dart:math';
import 'package:flutter/material.dart';
import '../../../core/theme.dart';

class TankWidget extends StatefulWidget {
  final double percentage;
  final double liters;

  const TankWidget({
    super.key,
    required this.percentage,
    required this.liters,
  });

  @override
  State<TankWidget> createState() => _TankWidgetState();
}

class _TankWidgetState extends State<TankWidget> with TickerProviderStateMixin {
  late AnimationController _waveController;
  late AnimationController _bubbleController;
  final List<Bubble> _bubbles = [];
  final Random _random = Random();

  @override
  void initState() {
    super.initState();
    _waveController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 4),
    )..repeat();
    
    _bubbleController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 6),
    )..repeat();
    
    _generateBubbles();
  }

  void _generateBubbles() {
    _bubbles.clear();
    for (int i = 0; i < 5; i++) {
      _bubbles.add(Bubble(
        x: _random.nextDouble() * 0.8 + 0.1,
        size: _random.nextDouble() * 5 + 4,
        speed: _random.nextDouble() * 0.4 + 0.3,
        delay: _random.nextDouble(),
      ));
    }
  }

  @override
  void dispose() {
    _waveController.dispose();
    _bubbleController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        // Tank Container
        Container(
          width: 180,
          height: 220,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(30),
            border: Border.all(
              color: isDark ? Colors.white.withOpacity(0.15) : Colors.grey.withOpacity(0.3),
              width: 3,
            ),
            boxShadow: [
              BoxShadow(
                color: AppColors.waterBlue.withOpacity(0.12),
                blurRadius: 16,
                spreadRadius: 1,
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(27),
            child: Stack(
              children: [
                // Background
                Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: isDark 
                          ? [Colors.black.withOpacity(0.3), Colors.black.withOpacity(0.15)]
                          : [Colors.grey.withOpacity(0.1), Colors.grey.withOpacity(0.05)],
                    ),
                  ),
                ),
                
                // Water with single gentle wave
                AnimatedBuilder(
                  animation: Listenable.merge([_waveController, _bubbleController]),
                  builder: (context, child) => _buildRealisticWater(isDark),
                ),
                
                // Percentage Text
                Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        '${widget.percentage.toInt()}',
                        style: TextStyle(
                          fontSize: 52,
                          fontWeight: FontWeight.bold,
                          color: isDark ? Colors.white : AppColors.textPrimaryLight,
                          shadows: [
                            Shadow(
                              color: Colors.black.withOpacity(0.35),
                              blurRadius: 10,
                              offset: const Offset(0, 2),
                            ),
                          ],
                        ),
                      ),
                      Text(
                        '%',
                        style: TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.w500,
                          color: isDark ? Colors.white70 : AppColors.textSecondaryLight,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        
        // Capacity Label
        Text(
          'CAPACITY',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            letterSpacing: 2,
            color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          '${widget.liters.toStringAsFixed(0)} L',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w500,
            color: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
          ),
        ),
      ],
    );
  }

  Widget _buildRealisticWater(bool isDark) {
    final waterHeight = (widget.percentage / 100) * 220;
    
    return Positioned(
      left: 0,
      right: 0,
      bottom: 0,
      child: SizedBox(
        height: waterHeight.clamp(0, 220),
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            // Single realistic wave
            CustomPaint(
              painter: RealisticWavePainter(
                animation: _waveController.value,
              ),
              size: Size.infinite,
            ),
            
            // Bubbles rising from bottom
            ..._bubbles.map((bubble) => _buildRisingBubble(bubble, waterHeight)),
          ],
        ),
      ),
    );
  }

  Widget _buildRisingBubble(Bubble bubble, double waterHeight) {
    // Calculate bubble position - starts from bottom, rises up
    final animProgress = (_bubbleController.value + bubble.delay) % 1.0;
    final yPosition = animProgress * waterHeight;
    
    // Bubble wobbles slightly as it rises
    final wobble = sin(animProgress * pi * 4) * 3;
    
    // Fade out as bubble approaches surface
    final opacity = (1 - animProgress).clamp(0.3, 0.8);
    
    // Bubble grows slightly as it rises (pressure decrease)
    final scale = 1 + (animProgress * 0.3);
    
    return Positioned(
      left: (bubble.x * 160) + wobble,
      bottom: yPosition,
      child: Transform.scale(
        scale: scale,
        child: Container(
          width: bubble.size,
          height: bubble.size,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: RadialGradient(
              center: const Alignment(-0.3, -0.3),
              colors: [
                Colors.white.withOpacity(opacity * 0.9),
                AppColors.barBlue.withOpacity(opacity * 0.4),
                Colors.transparent,
              ],
              stops: const [0.0, 0.5, 1.0],
            ),
          ),
        ),
      ),
    );
  }
}

class Bubble {
  final double x;
  final double size;
  final double speed;
  final double delay;

  Bubble({
    required this.x,
    required this.size,
    required this.speed,
    required this.delay,
  });
}

class RealisticWavePainter extends CustomPainter {
  final double animation;

  RealisticWavePainter({required this.animation});

  @override
  void paint(Canvas canvas, Size size) {
    // Water gradient - deeper blue at bottom, lighter at top
    final paint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          AppColors.barBlue.withOpacity(0.75),
          AppColors.waterBlue.withOpacity(0.9),
          AppColors.waterBlue,
        ],
        stops: const [0.0, 0.4, 1.0],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));

    final path = Path();
    
    // Gentle, realistic wave
    path.moveTo(0, 8);
    
    for (double x = 0; x <= size.width; x += 1) {
      // Single smooth wave with slight variation
      final wave = 6 * sin(
        (x / size.width * 2 * pi) + (animation * 2 * pi)
      );
      
      // Tiny ripple for realism
      final ripple = 1.5 * sin(
        (x / size.width * 4 * pi) + (animation * 3 * pi)
      );
      
      path.lineTo(x, 8 + wave + ripple);
    }
    
    path.lineTo(size.width, size.height);
    path.lineTo(0, size.height);
    path.close();

    canvas.drawPath(path, paint);
    
    // Add subtle highlight at top of water
    final highlightPaint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          Colors.white.withOpacity(0.15),
          Colors.transparent,
        ],
      ).createShader(Rect.fromLTWH(0, 0, size.width, 20));
    
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, 20), highlightPaint);
  }

  @override
  bool shouldRepaint(RealisticWavePainter oldDelegate) => 
      animation != oldDelegate.animation;
}
