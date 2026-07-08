# VS Code Loader

VS Code Loader is an Android application that attempts to bring a lightweight local development environment onto a phone or tablet. The app bootstraps a Termux-like Linux userspace inside the app’s private storage, installs code-server when needed, and exposes either a local sandbox editor or the running code-server web UI inside a WebView.

This project is best understood as a mobile-first local IDE launcher. It is not a full desktop IDE; instead, it packages a small Linux environment, a foreground service, and a web-based editor experience so the app can run code-server on localhost and present it inside the app.

## 1. What this project does

At a high level, the app provides four connected workflows:

1. Bootstrap a Linux runtime environment using bundled Termux-native bootstrap assets.
2. Install and verify code-server inside that environment.
3. Start a foreground service that runs code-server on localhost port 8080.
4. Open the IDE either in a built-in local sandbox or through the live code-server UI.

The app also exposes a terminal screen for manual shell interaction with the embedded Linux environment.

## 2. Project purpose and intended use

This repository is intended for:

- Running a minimal VS Code-style experience on Android.
- Testing local development tooling on mobile devices.
- Demonstrating how a mobile app can host an embedded Linux environment and web-based editor.
- Serving as a starting point for experimentation with Android + Termux + code-server integration.

## 3. Core features

- Android UI built with Jetpack Compose and Material 3.
- Embedded Termux bootstrap flow that extracts bundled assets into app-private storage.
- Automatic detection of whether the environment is already prepared.
- Package installation flow for code-server via a fast precompiled tarball or an NPM fallback.
- Foreground service that keeps code-server alive while the app is backgrounded.
- Local WebView-based IDE experience.
- Built-in offline sandbox editor using a static HTML/JavaScript editor page.
- Terminal activity for running commands inside the embedded Linux environment.
- Logging panel and clipboard export for troubleshooting.
- Notification permission handling for foreground service behavior.

## 4. Technical stack

- Language: Kotlin
- UI framework: Jetpack Compose
- Android target: API 36
- Minimum SDK: 24
- Build system: Gradle with Kotlin DSL
- Android Gradle Plugin: 9.1.1
- Kotlin: 2.2.10
- Testing: JUnit, Robolectric, Compose UI testing, Robolectric screenshot tests
- Networking/serialization: OkHttp, Retrofit, Moshi
- Coroutines: Kotlin coroutines
- Dependency injection style: none; the app uses direct object construction and state flows
- Firebase/AI integration: present in dependencies, but the current UI flow appears centered around local runtime setup rather than AI features

## 5. Application architecture

The app is organized around a small set of top-level responsibilities.

### 5.1 Entry points

- MainActivity
  - Startup screen for the app.
  - Hosts the main control panel UI.
  - Launches the WebView, the Termux terminal, and the server service.

- WebViewActivity
  - Loads either the local offline sandbox or the running code-server instance.
  - Provides a browser-like shell for the editor experience.
  - Handles file chooser callbacks for uploads.

- ServerForegroundService
  - Runs as a foreground service.
  - Starts and stops code-server process execution.
  - Publishes notification actions for opening the IDE or stopping the server.

### 5.2 Environment and bootstrap layer

- TermuxBootstrapper
  - Extracts a bundled Termux bootstrap payload from native libraries into app-private storage.
  - Creates the expected directory layout and symlinks.
  - Patches hardcoded Termux paths to point at the app’s extracted filesystem.

- TermuxEnvironment
  - Represents the extracted Linux environment layout.
  - Resolves likely binary locations for shell executables.
  - Builds the environment variables needed for subprocess execution.

- TermuxCommandRunner
  - Executes shell commands inside the prepared environment.
  - Streams command output back to the UI and logging layer.
  - Supports stopping an active command process.

### 5.3 Installation layer

- PackageInstaller
  - Installs base OS dependencies such as git, curl, ca-certificates, and bash.
  - Attempts a fast installation path using a precompiled code-server tarball.
  - Falls back to npm-based installation when the tarball method fails.

### 5.4 Utility and support layer

- AppLogger
  - Central logging utility for the app UI and runtime components.
  - Stores log history in a state flow for display and clipboard export.

- PermissionHelper
  - Handles notification permission checks and request payloads.

## 6. Runtime flow

A typical user flow is:

1. Launch the app.
2. Grant notifications permission if prompted.
3. Bootstrap the embedded Linux environment.
4. Install code-server inside that environment.
5. Start the foreground service.
6. Open the IDE through the WebView.

The workflow is intentionally simple, but it depends on the app having a working Termux/bootstrap payload and a functioning shell environment.

## 7. Important implementation details

The current implementation has several details worth knowing:

- The app uses the bundled native bootstrap payload under the Android JNI libraries directory. If that payload is missing, bootstrap will fail.
- The UI exposes Alpine vs Debian selection and a custom rootfs URL field, but the current bootstrap implementation ignores the selected distro and custom URL and uses the bundled payload directly.
- The service starts code-server with the command:
  - code-server --bind-addr 127.0.0.1:8080 --auth none --cert false
- The WebView opens the IDE at either the local sandbox page or localhost:8080.
- The app expects notifications permission for the foreground service experience on Android 13+.
- The app stores the Linux environment under app-private files storage rather than a full system-level Linux install.

## 8. Build and run instructions

### Prerequisites

- Android Studio
- JDK compatible with the project toolchain
- An Android emulator or physical device

### Setup

1. Open Android Studio.
2. Choose Open and select the repository root.
3. Let Android Studio import the Gradle project.
4. Create a .env file in the repository root based on .env.example.
5. Set GEMINI_API_KEY in .env if you want the project’s Gemini-related configuration paths to be available.
6. If you are running the app in a local debug setup, ensure the debug signing config is available. The project already defines a debug signing config and a release signing config.

### Build

From the project root, you can build with Gradle:

```bash
./gradlew assembleDebug
```

For a release build:

```bash
./gradlew assembleRelease
```

### Run

Run the app from Android Studio or with:

```bash
./gradlew installDebug
```

Then launch the app on an emulator or real device.

## 9. Configuration and environment variables

The project uses the Secrets Gradle Plugin and expects a local .env file.

Example:

```env
GEMINI_API_KEY=your_key_here
```

The file .env.example is the template used by the project.

Release signing is driven by environment variables:

- KEYSTORE_PATH
- STORE_PASSWORD
- KEY_PASSWORD

If these are not present, the release signing config points to a local my-upload-key.jks file at the repository root.

## 10. Project structure

```text
.
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   │   ├── androidTest/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── assets/
│   │   │   │   └── editor.html
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── WebViewActivity.kt
│   │   │   │   ├── install/PackageInstaller.kt
│   │   │   │   ├── service/ServerForegroundService.kt
│   │   │   │   ├── termux/
│   │   │   │   │   ├── TermuxBootstrapper.kt
│   │   │   │   │   ├── TermuxCommandRunner.kt
│   │   │   │   │   ├── TermuxEnvironment.kt
│   │   │   │   │   └── TermuxTerminalActivity.kt
│   │   │   │   └── util/
│   │   │   │       ├── Logger.kt
│   │   │   │       └── PermissionHelper.kt
│   │   │   └── jniLibs/
│   │   └── test/
│   └── proguard-rules.pro
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── metadata.json
├── settings.gradle.kts
├── .env.example
└── README.md
```

## 11. Key files to inspect first

If you are trying to understand the app quickly, read these files in order:

1. app/src/main/java/com/example/MainActivity.kt
   - Main UI and orchestration layer.

2. app/src/main/java/com/example/service/ServerForegroundService.kt
   - Background service and code-server lifecycle.

3. app/src/main/java/com/example/termux/TermuxBootstrapper.kt
   - Termux bootstrap extraction and setup logic.

4. app/src/main/java/com/example/install/PackageInstaller.kt
   - code-server installation flow.

5. app/src/main/java/com/example/WebViewActivity.kt
   - WebView-based editor experience.

6. app/src/main/AndroidManifest.xml
   - Permissions, activities, and service declarations.

7. app/build.gradle.kts
   - Dependencies and build configuration.

## 12. Testing

The project includes unit tests and Robolectric-based tests.

Run tests with:

```bash
./gradlew test
```

Relevant test areas:

- ExampleUnitTest
- ExampleRobolectricTest
- GreetingScreenshotTest

## 13. Known limitations and caveats

- The app is a proof-of-concept/mobile prototype rather than a polished production desktop IDE experience.
- The Linux setup is app-contained and may be fragile depending on device architecture and runtime conditions.
- The current bootstrap code uses a bundled native payload rather than a dynamic distro download.
- The UI exposes distro and custom rootfs options, but current bootstrap logic does not use them.
- The app depends on the availability of the bundled native library and a sufficiently capable Android runtime.
- Some dependencies are present but currently unused or commented out in Gradle, which suggests the project evolved over time.

## 14. Troubleshooting tips

- If bootstrap fails, verify that the native Termux payload exists in the JNI libraries directory for your ABI.
- If code-server fails to start, inspect the service logs and the app logger output from the UI.
- If the foreground service does not behave as expected, confirm that notification permission is granted on Android 13+.
- If WebView cannot load the IDE, verify that the service is actually running and listening on localhost port 8080.
- If builds fail, ensure Gradle and Android Studio are using the expected JDK and Android SDK versions.

## 15. Summary

VS Code Loader is a mobile Android application that packages a mini Linux environment, installs code-server, and exposes it through a WebView-based IDE experience. It is a focused experiment in bringing local coding workflows to Android devices and is most useful for developers who want to understand how embedded Linux, foreground services, and web-based developer tools can be combined inside a single mobile app.
