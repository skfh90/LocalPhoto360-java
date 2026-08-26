# LocalPhoto360

Android Studio project for capturing photos on-device and viewing them in an immersive **360° photosphere**, the same idea as a local Photo360 / Photo Sphere app: nothing is uploaded, and the sphere is rendered from files on the phone.

## What you can do

- **Open a 360 viewer** for any local equirectangular image. Drag to look around, pinch to zoom, or follow the phone with the gyroscope.
- **Capture a still** with CameraX and keep it in the on-device library.
- **Capture a 360 photosphere** by turning in place. The app guides you to overlapping headings, projects each frame onto an equirectangular canvas using the rotation vector, then opens the result in the sphere viewer.
- **Import** an existing 360 / panorama JPEG or PNG from the system photo picker.
- A **sample photosphere** is bundled so you can try the viewer immediately, without a camera.

Photos are stored under the app’s private files (`files/photos`). Sharing uses `FileProvider`.

## Open in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (Koala / Ladybug or newer is fine).
2. **File → Open** and select this folder (the one that contains `settings.gradle.kts`).
3. Let Gradle sync. Android Studio will create `local.properties` with your SDK path.
4. Connect a phone or start an emulator with **API 26+** and a camera (a real device is much better for 360 capture and gyro look).
5. Run the **app** configuration.

Command line, from this directory:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## How 360 capture works

This is a **guided photosphere**, not a full OpenCV stitcher.

1. The back camera preview runs through CameraX.
2. `TYPE_GAME_ROTATION_VECTOR` (falling back to `TYPE_ROTATION_VECTOR`) gives a device-to-world rotation matrix.
3. A ring of heading / pitch targets is shown as glowing markers. When you hold the phone on a target, a frame is captured.
4. Each frame is projected into a 2048×1024 equirectangular image using the camera field of view and that rotation matrix.
5. After at least four views you can stitch early; filling every target gives fuller coverage. The JPEG is saved locally and opened in the OpenGL sphere viewer.

For the cleanest sphere, pivot around a single point, keep the phone upright, and overlap neighboring targets. A dedicated 360 camera is not required.

Imported images that are roughly **2:1** (typical equirectangular) are tagged as photospheres. Other photos still open in the viewer, stretched across the sphere.

## Project layout

```
app/src/main/java/com/localphoto360/app/
  gallery/     Library grid
  capture/     CameraX stills + photosphere session
  viewer/      OpenGL ES 2.0 sphere (touch + gyro)
  data/        Local JPEG + JSON metadata
  util/        Equirectangular math and orientation
```

- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Camera: CameraX
- Viewer: custom `GLSurfaceView` sphere with an equirectangular texture
- minSdk 26, targetSdk 35

## Tests

```bash
./gradlew test
```

`EquirectangularMathTest` checks look-direction ↔ UV mapping used by both capture and the viewer.
