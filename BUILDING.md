# Building JaySoft ZX81 Fold

These instructions describe how to build the Android application
from the supplied source code.

## Requirements

- Java Development Kit (JDK) 17
- Gradle 8.13
- Android SDK platform 36
- Android SDK build tools 35.0.0
- Android SDK command-line tools

The project does not currently include a Gradle wrapper.
Install Gradle 8.13 separately.

Configure ANDROID_HOME to point to your Android SDK installation,
or configure the SDK path in a local.properties file.

## Build a debug APK

From the project directory, run:

    gradle --no-daemon :app:assembleDebug

The resulting APK should be located at:

    app/build/outputs/apk/debug/app-debug.apk

Android's debug signing is handled automatically by the build tools.

## Release signing

The official JaySoft release APK uses a separate private signing key.

Release signing is configured through environment variables in
app/build.gradle. No private signing keys or passwords are distributed
with this source code.

You do not need the official release signing credentials to compile
and test the application using the debug build.

A locally built APK will not necessarily install as an update over
the official release because the signing certificates differ.

Back up any saved ZX81 programs before uninstalling an existing version.

## Project details

Application ID: com.zx81fold.emulator
Minimum Android API: 26
Target Android API: 36
Release version: 1.0-rc1

The ZX81 emulator is hosted within the Android application.
Refer to README.md and COPYRIGHT-AND-LICENCES.md for details.

These build instructions reflect the original RC1 configuration.
Independent third-party builds have not yet been verified.
