# FaceFit kiosk

Android kiosk application for camera capture, face landmark detection, and facial feature extraction.

## Open the project

Open this `kiosk/` folder in Android Studio. The Gradle project starts here, with the application module in `app/`.

Use JDK 17 and Android SDK 34. Let Android Studio configure the SDK location in the local `local.properties` file, which is excluded from Git.

## Run on an Android phone

An emulator and a second drive are not required. Keep the project and Android SDK on your available drive, and use a phone running Android 8.0 or newer.

1. In Android Studio, choose **File > Open** and select this `kiosk/` folder. Wait for Gradle sync to finish.
2. Under **Settings > Build, Execution, Deployment > Build Tools > Gradle**, select a JDK 17 installation for **Gradle JDK**.
3. Enable **Developer options > USB debugging** on the phone, connect it with a USB data cable, and accept the debugging authorization prompt on the unlocked phone.
4. Select the **app** run configuration and your physical phone in the device selector, then click **Run**.
5. Sign in with `demo@facefit.local` and any nonempty password. The kiosk supports portrait and landscape orientation. Enable Auto rotate on the tablet to switch between them. Panels stack below 1000dp of window width and appear side by side on wider windows. Allow camera access to try the scan flow.

If sync reports **"does not contain a Gradle build"**, the repository's parent folder is open. Open `kiosk/`, which contains `settings.gradle` and `gradlew.bat`.

If the phone is missing from the device selector, use **Tools > Troubleshoot Device Connections**. Creating an emulator is not necessary.

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

## Camera image coordinates

The front-camera preview behaves like a mirror. Captures are rotated upright using the camera's EXIF orientation, then mirrored horizontally before being stored. The JPEG contains those final pixels with no pending EXIF rotation or reflection. Capture review and face analysis read that same file; future VTO overlays should use its landmark coordinates without applying another horizontal flip.
