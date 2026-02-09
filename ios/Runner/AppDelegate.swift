import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)
    
    // Enable background fetch for WorkManager tasks
    // This allows periodic updates even when app is terminated/background
    if #available(iOS 10.0, *) {
      UNUserNotificationCenter.current().delegate = self as? UNUserNotificationCenterDelegate
    }
    
    application.setMinimumBackgroundFetchInterval(TimeInterval(60*15))
    
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
