# LowCalculator

[English](README.md) | [Русский](README.ru.md)

An ultra-light calculator for Android 4.0.3 – 4.4.4 (API 15–19). The whole UI (display and buttons) is drawn programmatically on a `Canvas` in the Holo Dark style, with no XML layouts. Target: an APK under 20 KB.

- Package: `com.m929.lowcalculator`
- Pure Java: no Kotlin, androidx or third-party libraries
- Logic and drawing in a single file: `app/src/main/java/com/m929/lowcalculator/MainActivity.java`
- Localized app name (`res/values-*/strings.xml`)

## Layout

```
[C] [√] [^] [÷]
[7] [8] [9] [×]
[4] [5] [6] [−]
[1] [2] [3] [+]
[0] [.] [  =  ]
```

Key presses play the standard system click sound (respects the "Touch sounds" setting). Division by zero shows `Error`, only one decimal point is allowed per number, and operation chains are evaluated left to right.

## Build

Requires JDK 17 and the Android SDK. Open the project in Android Studio, or run:

```
gradle wrapper      # once, generates gradlew
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. Build the **release** variant to check the size (R8 + shrinkResources); the debug build is noticeably larger. The release build is signed with the debug key, so use your own key for publishing. Only the v1 signature is used, since Android 4.x does not understand newer schemes.

GitHub Actions (`.github/workflows/build.yml`) builds the release APK on every push and uploads it as an artifact.

## Credits

- Architecture and icon: m929
- Code and the rest of the project (build setup, localization, documentation, CI): Claude (Anthropic)
