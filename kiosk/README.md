# FaceFit kiosk

Android kiosk application for camera capture, face landmark detection, and facial feature extraction.

## Open the project

Open this `kiosk/` folder in Android Studio. The Gradle project starts here, with the application module in `app/`.

Use JDK 17 and Android SDK 34. Let Android Studio configure the SDK location in the local `local.properties` file, which is excluded from Git.

## Build and test

From the repository root in PowerShell:

```powershell
Set-Location kiosk
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Set `JAVA_HOME` to your JDK 17 installation when running Gradle from a terminal.

## Folders

- `app/`: Android source code, resources, face landmark model, and unit tests.
- `artwork/`: Original logo artwork and asset preparation notes.
- `gradle/`: Gradle wrapper files.
