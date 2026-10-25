# Auriga Arael native app redesign blueprint

Branch: `auriga-arael-1`. This is the implementation plan for a task-first redesign, not a claim that every screen is already finished.

## Product principles

1. **Task before technology.** Lead with “Find an object”, “Read text”, “Identify a colour”, and “Saved places”; hide model and calibration details under Device & setup.
2. **Separate class selection from instance focus.** The target picker selects COCO classes. The live locator now supports tapping a visible box to focus that particular instance, follows the nearest same-class detection, and reports target loss instead of silently switching objects. Tap the focused box again to return to auto-pick.
3. **Honest sensing states.** Camera preview, detector, tracking/focus and metric geometry are separate states. Never describe a detector as ready if inference is unavailable. Do not expose distance or height until those measurements pass physical validation.
4. **Assistive technology is a first-class input path.** Every action needs a meaningful label, adequate touch area, visible focus, predictable Back behavior and a non-gesture route. Canvas detections need an accessible textual summary. Avoid live-region announcements on every camera frame.
5. **Quiet by default, urgent by exception.** Spoken updates should be change-driven and user-controllable; avoid repeatedly announcing a stable target. Haptics supplement rather than replace text/speech.
6. **Camera screens stay visually calm.** Use deep slate surfaces, a restrained teal focus accent and amber for unavailable/uncertain states. Avoid decorative glow that competes with the scene.

## Screen-by-screen target design

| Screen | Intended redesign |
|---|---|
| Home | One dominant “Find an object” action; Read text and Identify colour as next-level tasks; Device & setup groups model, camera, calibration, map and help. Show experimental safety notice without making it the visual hero. |
| Object picker | Search first; fast picks for common objects; compact scene presets; selected targets always summarized; clear reset; explicit Start locating. All options must correspond to actual bundled model labels. |
| Live object locator | Large live preview; persistent task/status card; readable target filter; direct tap-to-focus on a box; tap focused box to resume auto-pick; clear “focused target lost” state; no invented range. |
| Reader | One obvious Capture/Read action, clear listening/reading/paused states, accessible recognized-text panel and predictable playback controls. |
| Colour | Centre sampling guidance, one large colour result, explicit “not sure” state and a manual capture option; no reliance on colour alone for safety. |
| Target management | Manage object classes separately from instance focus. Show what is saved and how to clear it. |
| Visual place map | Local-only by default, explicit teaching/review/deletion actions, disclose tracking confidence and drift. |
| Camera devices | Supported-device status, connection steps, retry and disconnect actions; never imply a camera is connected until frames arrive. |
| Model status | Separate model file availability, load success, frame analysis and inference freshness. |
| Calibration | Guided, reversible, non-safety-critical setup; show whether scale/geometry is actually validated. |
| Help/settings/support | Plain-language task instructions, voice/haptic/quiet controls, privacy/export controls and accessible contact paths. |

## Competitor inspiration, not copying

- **Google Lookout:** task modes such as text, documents, objects/scene exploration and finding items. Auriga should reduce setup friction and communicate uncertainty.
- **Microsoft Seeing AI:** focused channels and fast transitions between reading, identifying and describing. Auriga should make task selection obvious without overloading one screen.
- **Be My Eyes:** clear choice of task and help path. Auriga should remain offline-first and not silently substitute a remote helper/cloud path.

## Accessibility and usability release gates

- Test the real APK with TalkBack, Voice Access, Switch Access and large text/display scaling.
- Every important action must be reachable without a custom gesture; touch targets should aim for Android's 48 dp guidance.
- Test screen order, accessible names, state changes, focus restoration after returning from target selection, and camera permission denial/retry.
- Test web content against WCAG 2.2 AA; test the Android app as a native mobile app rather than assuming web conformance covers it.
- Run task-based studies with blind/low-vision participants. Track time-to-select, selection errors, target-switch errors, false detections, target-loss disclosure, offline completion and user trust.
- Keep a hard release block on any confident but unvalidated distance, height or collision warning.

## Current implementation in this branch

- Added one-tap fast picks for person, cell phone, chair, backpack, car and bottle.
- Added an accessible locator task-status card and queryable description of the current primary detection.
- Added tap-to-focus for a particular visible detection; the app attempts to follow that instance by same-class image position and exposes a target-lost state instead of silently switching.
- Updated shared Android palette tokens to a calmer teal/slate scheme.

These changes still require Android CI and physical-device testing. Tap-to-focus is a visual interaction; it is not a replacement for a TalkBack-accessible target list. Other screens remain scheduled for the same systematic redesign.