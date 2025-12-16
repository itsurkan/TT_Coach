# TT Coach - AI Table Tennis Coaching App

A native Android application that provides real-time analysis of table tennis techniques, specifically focusing on the Forehand Drive (Накат справа) with instant voice feedback (<200ms latency).

## Features

- **Real-time Pose Tracking**: MediaPipe Pose estimation (33 key points)
- **Ball Detection & Tracking**: YOLO Nano model with Kalman filter for stable tracking
- **Instant Voice Feedback**: Native Android TTS with <200ms latency
- **Ball Analysis**: Speed, spin estimation, trajectory analysis, and In/Out detection
- **LLM-Powered Reports**: OpenAI integration for detailed training analysis

## Project Structure

```
app/src/main/java/com/ttcoach/
├── MainActivity.kt
├── camera/          # Camera2 API integration
├── cv/              # Computer vision modules
├── analysis/        # Analysis engine
├── ball/            # Ball analysis module
├── audio/           # Audio feedback
├── llm/             # LLM integration
├── ui/              # UI components (Jetpack Compose)
└── data/            # Data models and storage
```

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 24+ (minimum), 34 (target)
- Java 17
- Gradle 8.2+

## Setup

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build and run on an Android device or emulator

## Dependencies

- MediaPipe Tasks Vision (Pose estimation)
- TensorFlow Lite (YOLO Nano model)
- CameraX (Camera2 API wrapper)
- Room Database (Local storage)
- Retrofit (OpenAI API)
- Jetpack Compose (UI)

## Development Status

Currently in Week 1: Setting up project structure and implementing pose tracking.

## License

[To be determined]

