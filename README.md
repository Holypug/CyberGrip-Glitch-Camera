# CyberGrip GLITCH Camera

Experimental Android camera app with real-time glitch effects, analog film looks and photo/video capture.

**Release:** v1.5.1 UI FIX  
**Android package:** `com.cybergrip.glitch`  
**Minimum Android:** 10 (API 29)  
**Recommended test device:** Samsung Galaxy S24 Ultra

## Features

- Live camera preview with stackable glitch effects
- Analog film looks and Film Lab controls
- Photo capture and MP4 video recording
- Three effect presets (P1–P3)
- Collapsible effects and Film Lab sections
- Landscape shutter/record button size adjustment in v1.5.1
- Video output settings: 1080p or 4K UHD container resolution; H.264 bitrates of 12, 20 or 30 Mbps

## Video limitations

**1080p and 4K output are upscaled from a 960×720 processing source, not native 1080p/4K capture.** The CPU-based pipeline can drop frames, particularly at 4K. The encoder frame-rate target does not guarantee actual 30 fps. **Video audio is not included.** See `README_VIDEO_LAB_PRO.md` for details.

## Install the app

1. Open the [Releases](../../releases) page.
2. Download the APK asset attached to the latest release (once published).
3. On a compatible Android phone, open the APK and allow installation from that source if prompted.

Only install APKs from a source you trust. Android may display an installation security warning for apps distributed outside Google Play.

## Build from source

1. Install Android Studio, Android SDK platform 35 and JDK 17.
2. Clone or download this repository.
3. Open the repository folder in Android Studio.
4. Let Android Studio generate `local.properties` with your own SDK path, then sync Gradle.
5. Build and run on an Android device with a camera.

The project source is included, but this repository package has **not been independently compiled or tested in this environment**.

## Build a signed release APK

In Android Studio, select **Build → Generate Signed App Bundle / APK → APK**. Create or select a private signing keystore and choose the release build. Keep the keystore and its passwords outside this repository; use the same key for future updates. Upload the resulting APK as an asset in a GitHub Release.

## Privacy and permissions

The app requests camera access. Check the source and your build before distribution. Do not publish signing keys, credentials or device-specific `local.properties`.

## License

No open-source license has been selected yet. The source is publicly viewable, but reuse and redistribution are **not automatically permitted** without a license. Add a `LICENSE` file after choosing terms appropriate for your project.

## Disclaimer

Independent experimental project, not affiliated with Samsung or Sony.
