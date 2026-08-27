# LocalPhoto360

Android Studio app for **on-device** still capture and **360 photosphere** viewing. Photos stay on the phone (`files/photos/`). Source is **Java** (no Kotlin). Minimum SDK is **23** (Android 6).

## Build & run

Open this folder in **Android Studio** (Ladybug / AGP 8.7) or:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A debug APK is also copied to **`LocalPhoto360-debug.apk`** at the repo root after a successful Gradle assemble (run `./gradlew assembleDebug` locally).

Requires **JDK 17+**. Camera needs a device or emulator with a camera.

## Offline Windows PC (`C:\Users\1-PYC\.gradle`)

This repo does not need Kotlin or Compose. A typical `%USERPROFILE%\.gradle` folder is full of **daemons, native binaries, old Gradle versions, and AAR transform caches** that this app does not need.

A trimmed Gradle user home is in **`offline/gradle-user-home/`** as split zip parts (`gradle-user-home.zip.00` … `.12`). It contains only:

- `wrapper\dists\gradle-8.9-bin\` — Gradle **8.9** (the wrapper zip)
- `caches\modules-2\` — Maven artifacts for this Java app (CameraX, AppCompat, Material, AGP 8.7.3)

**Install (replace the bloated folder):**

1. Close Android Studio.
2. Clone or copy this repo (the zip parts are already under `offline/gradle-user-home/`).
3. From PowerShell in the repo root:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\install-offline-gradle.ps1
```

That renames `C:\Users\1-PYC\.gradle` to a timestamped backup and extracts the trimmed cache.

**Or only delete junk, keep your existing caches:**

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\install-offline-gradle.ps1 -CleanOnly
```

That removes `daemon`, `native`, `notifications`, `workers`, `kotlin-profile`, `caches\8.*` transforms, and unused Gradle distributions. It keeps `caches\modules-2` and `gradle-8.9-bin`.

Then build with no network:

```bat
gradlew --offline assembleDebug
```

You still need **JDK 17+** and **Android SDK Platform 35** on that PC. The Gradle cache is not a substitute for the SDK.

## What you can do

- Gallery of local photos (bundled sample photosphere plus anything you capture or import)
- **Capture photo** — CameraX still JPEG
- **Capture 360** — guided yaw/pitch capture; frames are projected onto an equirectangular canvas (no OpenCV stitch)
- **Viewer** — OpenGL ES 2.0 sphere; drag, pinch zoom, optional gyro
- **Import** — 2:1 images are treated as photospheres

## 360 capture on a physical phone

1. Grant camera permission.
2. Hold the phone **portrait**.
3. Sweep through the **row of targets** (yaw), then **tilt** for the next pitch band.
4. When **Locked on target** appears, stay still until the frame is taken.
5. **Auto-capture** is on by default.

## 360 capture on the Android Emulator (AVD)

The virtual camera **does not rotate** with device sensors. Auto-capture is **off**.

1. **Extended controls → Camera** — virtual scene or webcam.
2. **Next target** — highlight the next yaw/pitch slot.
3. **◀ 45° / 45° ▶** — pan the **virtual heading** (this is what is written into the pano, not the AVD gyro).
4. **Tilt** — next pitch band.
5. **Capture this view** — stamp the current preview onto the canvas.

## Project layout

- `app/src/main/java/com/localphoto360/app/` — Java activities, CameraX binder, OpenGL viewer
- `app/src/main/res/layout/` — XML layouts (View Binding)
- `app/src/main/assets/sample_sphere.jpg` — bundled equirectangular sample
- Gradle Kotlin DSL (`.kts`) is build config only; **app code is Java**.

## SDK

| | |
|---|---|
| `minSdk` | 23 |
| `compileSdk` / `targetSdk` | 35 |
| Java | 17 |
