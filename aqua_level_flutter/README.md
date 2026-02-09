# AquaLevel Flutter

This is the Flutter version of the AquaLevel Android app, designed to work on both Android and iOS.

## Project Setup

The project structure was created manually because the `flutter` command was not available in the environment. All source code is located in `lib/`.

To complete the setup and generate the necessary platform folders (android, ios, web, etc.), run the following command in this directory:

```bash
flutter create .
```

This will inspect `pubspec.yaml` and generate the platform-specific runners without overwriting the `lib/` folder (unless you force it, but don't force it).

## Dependencies

After creating the platform folders, fetch the dependencies:

```bash
flutter pub get
```

## Running the App

To run the app:

```bash
flutter run
```

## Architecture

This project uses a Clean Architecture approach:

- **Presentation**: `lib/presentation` (UI, ViewModels/Providers)
- **Domain**: `lib/domain` (Entities, UseCases, Repository Interfaces)
- **Data**: `lib/data` (Repositories, Data Sources, Models)
- **Core**: `lib/core` (Constants, Utils)

## State Management

We use **Riverpod** for state management and dependency injection.

## Database

- **Local**: Drift (SQLite)
- **Remote**: Cloud Firestore
