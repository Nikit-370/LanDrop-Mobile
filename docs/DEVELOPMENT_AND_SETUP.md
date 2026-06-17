# Development, Build & Test Pipeline 🔧

This document provides guidelines on how developers can set up, build, test, and contribute to the LAN Drop codebase.

---

## 🛠️ Project Structure

The project is structured as a standard modern Android Gradle project:

```
├── app
│   ├── src
│   │   ├── main
│   │   │   ├── java/com/landrop
│   │   │   │   ├── data              # Database: Entities, DAOs, Room database configurations
│   │   │   │   ├── server            # Core sockets engine, Web Portal HTML, file transfer models
│   │   │   │   ├── service           # Background service tracking (FileServerService)
│   │   │   │   └── ui                # Jetpack Compose dashboards, Settings Bento UI, theme components
│   │   │   └── res                   # Standard assets, XML values, strings representation
│   │   └── test                      # Robolectric unit and visual regression tests
│   └── build.gradle.kts              # Application-level build script (plugins, dependencies)
├── gradle
├── build.gradle.kts                  # Project-level dependencies definition
└── settings.gradle.kts                # Subprojects declaration
```

---

## ⚙️ Requirements & Environment Setup

- **IDE**: Android Studio Koala / Ladybug or newer.
- **JDK**: Java JDK 17 (recommended) or higher.
- **Android SDK**: minimum targeting Android SDK API level 26 (`minSdk`), compiled with API level 34 (`compileSdk`).

---

## 🔨 Build & Compilation Commands

This project uses standard Gradle DSL. Always run standard tasks to test and build:

```bash
# Clean project build files (Warning: increases compilation time)
gradle clean

# Compile and package standard Debug APK
gradle assembleDebug

# Build and compile Release APK (with optimized shrinking if enabled)
gradle assembleRelease
```

---

## 🧪 Testing Strategy

Since Android development relies heavily on functional verification, we configure optimized local JVM testing to verify database consistency and logic routing without requiring physical devices.

### Running Headless JVM / Robolectric Tests
We run tests natively on the JVM using **Robolectric**:

```bash
# Run all unit tests inside the JVM
gradle :app:testDebugUnitTest
```

#### What We Test:
- **Port Safeguard Verification**: Validates that bad port configurations (negative values, system-reserved values) are blocked in pre-flight.
- **Database CRUD Tracking**: Tests that the Room Dao records and queries historical Transfer telemetry successfully.
- **HTML Assets Compilation**: Assures that the built-in `WebPortalHtml.kt` payload compiles cleanly without syntax anomalies.

---

## 🎨 Jetpack Compose Design Guidelines

When adding new screens or modifying existing dashboards in LAN Drop, make sure to follow our clean Material 3 design directives:

1. **Touch Guidelines**: Ensure all interactive icons, buttons, and switches maintain a touch layout boundary of `48.dp` or more to comply with Android Accessibility standards (`minimumInteractiveComponentSize`).
2. **Flexible Canvas Scaling**: Avoid static height values tailored to standard mobile aspect ratios. Test layouts under tablet landscapes using split-pane or list-detail styles on Expanded screens.
3. **No Hardcoded Hex Colors**: Always fetch styles and colors directly from active themes:
   ```kotlin
   Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
   ```
4. **Semantics & TestTags**: Always add semantic `contentDescription` attributes to vector drawables and bind a unique test identifier using `Modifier.testTag("submit_button_key")`. This keeps the layout perfectly compatible with automated UI testers and screen readers.
