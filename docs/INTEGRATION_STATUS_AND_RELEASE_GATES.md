# Auriga Arael integration status and release gates

Last updated: 2026-10-24
Working branch: `auriga-arael-1`

This document distinguishes code present in the repository from features that have been validated. A successful source commit is not evidence of a successful Android build or safe navigation performance.

## Current repository layout

- `android/`: native Android/Java application and Gradle build.
- `website/web_deploy/`: static website bundle.
- `website/netlify/functions/`: Netlify feedback function.
- `netlify.toml`: Netlify publish and function paths.
- `.github/workflows/auriga-android-ci.yml`: Android CI workflow.
- `android/app/src/test/`: JVM unit tests, including fail-closed smoothing tests.

The Netlify configuration now points to `website/web_deploy` and `website/netlify/functions`, which match the paths present on this branch. Deployment has not been confirmed, and a branch push will deploy only if the connected Netlify site is configured to deploy this branch.

## Feature / evidence matrix

| Area | Present in source | Current evidence / limitation |
|---|---|---|
| Android app | Java Android application, Camera2/CameraX and multiple product flavors | Source inspected; clean build not yet confirmed by CI |
| Relative bearing | Optical-axis-relative bearing using focal geometry | Source implementation exists; physical bearing error not measured |
| Metric ground range | API deliberately returns unavailable (`null`) | No validated ground-contact + ray/plane solver is integrated |
| Suspended-object height | API deliberately returns unavailable (`null`) | No validated nadir/ground-intersection height solver |
| Calibration profile | LUT parser validates physical-calibration metadata and requires at least 10 points | Bundled SM-A057F profile is explicitly unapproved; synthetic data must not authorize metric output |
| Visual motion estimate | Brightness-centroid shift and smoothing | Not robust feature tracking, visual odometry, relocalisation, or SLAM; blank/invalid frames now fail closed |
| Confidence/degraded behavior | Smoothing rejects non-finite readings and untrusted visual shift | Unit tests added; CI execution still pending |
| General object detection | Native detector integration expects TFLite model assets | Required YOLO model is not included in the repository; do not claim that native YOLO detection is ready |
| Persistent world map | Not complete | No validated persistent map with multi-anchor relocalisation and drift management |
| Multi-plane geometry | Not complete | No validated general multi-plane camera-only estimator |
| Dynamic tracking/collision reasoning | Partial scaffolding only | Must not be treated as validated collision prediction |
| Accessibility | Voice, haptic and screen/UI scaffolding exist | End-to-end screen-reader, interruption, duplicate-alert and user studies remain to be run |
| ESP32/IP-camera | Integration requirements remain | Protocol validation and frame/object-detection separation remain to be tested |
| Website | Static bundle and feedback function are present | Netlify live deployment, HTTPS, camera permissions and simulation require separate verification |
| Release APK | Not yet verified for this revision | Do not distribute as a mobility aid; await successful CI and device checks |

## CI behavior

The Android workflow is intended to:
1. Use JDK 17 and the checked-in Gradle wrapper.
2. Run `testNaviDebugUnitTest`.
3. Run `lintNaviDebug`.
4. Assemble `assembleNaviDebug`.
5. Upload the debug APK and Gradle test/lint reports.

A workflow file being committed does not mean any of these steps passed. Record the actual run URL, result, artifact name, and failing logs when available.

## Required release gates

- [ ] CI workflow completes successfully on the target branch.
- [ ] APK installs and launches on Samsung Galaxy A05s (SM-A057F).
- [ ] Verify app permission flows and offline startup.
- [ ] Confirm no metric distance/height is spoken or displayed without approved calibration and valid scene geometry.
- [ ] Run screen-reader, haptic, voice-priority, duplicate-alert, interruption, and quiet-mode tests.
- [ ] Test camera frame handling for null, stale, rotated, low-light, and disconnected sources.
- [ ] Validate ESP32/IP-camera protocol locally; raw frame transfer must not be represented as object detection.
- [ ] Measure physical range/bearing errors against independent ground truth.
- [ ] Record hazard recall, false alerts, warning lead time, latency, offline behavior, thermal behavior, and battery consumption.
- [ ] Verify local persistence, data deletion, network behavior, and privacy controls.
- [ ] Verify Netlify build settings, live HTTPS URL, camera flow, and simulation independently.
- [ ] Review release signing, permissions, dependencies, privacy disclosure, and Play policy requirements before store submission.

## Safety statement

Auriga Arael is an engineering prototype and is **not a validated mobility aid**. Do not use it as the sole means of navigation or hazard detection. Never replace unavailable metric output with guessed values. Build success, unit tests, and a working demo do not substitute for independent physical validation with appropriate participants and controlled safety procedures.
