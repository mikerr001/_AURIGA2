# Auriga Arael — competitive, usability and accessibility benchmark

**Review date:** 25 October 2026  
**Scope:** Android native app, browser prototype/site, and assistive-technology compatibility.  
**Status:** Research and acceptance criteria; not a certification or a claim of parity with competitors.

## Executive finding

Auriga should not compete by adding a larger font toggle to a website. It should compete on a reliable task flow: launch one clear task, start the right sensor only after permission, announce what is happening, recover from failures, and never present an unvalidated estimate as a fact. The native Android app and browser prototype must be distinct products with consistent language—not one hybrid-looking UI pretending to share the same sensing pipeline.

## Public competitor benchmark

This comparison is based on vendors' public product descriptions, not a hands-on test of their latest builds. Feature availability can vary by OS, region, hardware and version.

| Product | Publicly described strengths | UX pattern Auriga should learn from | Auriga's potential distinction (not yet a proven feature) |
|---|---|---|---|
| Google Lookout | Android camera-based modes include text, documents, Explore, food labels, currency, image descriptions and Find; Find can provide direction/distance on most phones. Google notes Explore is beta and less accurate. | **Task/mode-first UI.** Users choose the job they want done rather than navigating a general-purpose dashboard. Make uncertainty and mode limits visible. | A persistent, local-first spatial context and concise change-driven cues across a task, if validated on supported devices. Do not claim equivalent ranging today. |
| Microsoft Seeing AI | Narrates the world and supports everyday tasks such as reading, photo descriptions and product identification; its channel layout has evolved based on user feedback. | **Focused channels and user-informed iteration.** Group tasks by intent; test with blind and low-vision users before rearranging navigation. | One consistent interaction model for locating objects, reading and environmental context without conflating their confidence levels. |
| Be My Eyes | Connects users to volunteers for live visual assistance, offers Be My AI image/scene descriptions and a service directory. | **A clear help path.** When automation cannot resolve a task, tell the user what alternative is available and what it requires. | Prioritise on-device operation and explicit degraded states; do not imitate the volunteer network or imply an equivalent human fallback exists. |
| Auriga Arael (current project direction) | Native Android app plus separate browser prototype; camera, voice, reader and locator work is in progress. Metric navigation and hazard warning are not validated. | Keep native navigation native; put one primary action first; state permission, camera, microphone, detector and scale status separately. | Honest offline-first copilot with spatial context only when tracking and metric geometry have passed repeatable physical validation. |

Official product references:
- Google Lookout: https://support.google.com/accessibility/android/answer/9031274
- Microsoft Seeing AI: https://www.seeingai.com/
- Be My Eyes: https://www.bemyeyes.com/bme-app/

## Product and usability requirements

These are product acceptance criteria, not merely visual accessibility polish.

### Task design
- The first screen presents a small number of understandable tasks: **Find/describe**, **Read text**, **Identify colour**, and **Settings/help**. Experimental navigation is not presented as production-ready.
- One tap starts the selected task. Avoid a web-like dashboard inside the native Android app; use Android-native components, predictable Back behaviour, and a clear active-task state.
- Every long-running task exposes **Start / Pause or Stop / Retry** where applicable. The current state is announced to TalkBack and shown visually.
- Avoid interruptive narration. Prioritise urgent, verified events; suppress duplicate speech and provide a way to repeat the last message.
- The camera preview, detector state, and metric-scale state are distinct. Camera active does not mean detection works; detection does not mean distance is validated.
- If microphone recognition fails, preserve touch controls, explain the cause in plain language, and offer a retry. Never trap users in a silent or endless listening state.
- Permission prompts happen in context. Explain why camera/microphone access is needed before invoking the Android prompt; handle denial and “don't ask again” without loops.
- No essential action depends on colour alone, a gesture alone, speech alone, or precise timing.

### Native Android and assistive-tool compatibility
- TalkBack: meaningful labels, correct roles, logical traversal order, state announcements, no duplicate labels, no focus traps, and no focus stolen by camera frames or transcript updates.
- Voice Access: controls have stable spoken names; no action requires a hidden long-press without an equivalent visible/accessible action.
- Switch Access / external keyboard: all essential flows can be traversed and activated in a predictable order.
- Magnification and large font: layouts reflow without clipping, hidden controls or horizontal scrolling for primary tasks.
- Hearing/quiet environments: visible status and haptic alternatives for key feedback; speech output can be stopped/repeated.
- Android touch targets: target at least 48 × 48 dp for interactive elements, following Android guidance.
- Test on a real low/mid-range Android device, with TalkBack enabled, and with camera/microphone permissions separately granted, denied and revoked.

### Website and browser prototype
- Keep the website as a product/learning surface and the browser locator as a clearly labelled simulation; do not suggest the simulation uses the phone camera or native model.
- WCAG 2.2 AA is the web target: semantic headings/landmarks, keyboard operation, visible focus, reflow, contrast, text alternatives, form labels and errors, status announcements, reduced motion, and appropriately sized controls.
- Test with Android TalkBack + Chrome, desktop NVDA + Firefox/Chrome, keyboard only, 200% zoom/reflow, high contrast, and reduced-motion settings.
- A feature should not be called accessible merely because the site has a high-contrast toggle or text-size buttons. Test complete user journeys with assistive technology.

## Regulatory and standards map

This is an engineering compliance map, **not legal advice**. Applicability depends on how Auriga is distributed, who operates it, procurement context, and the applicable Kenyan and international obligations. Obtain qualified local review before making a legal-conformance claim.

1. **WCAG 2.2** — use Level AA as the website/browser-app engineering target. WCAG applies to web content on desktop and mobile. Conformance requires the whole relevant page/process to meet criteria; a toolbar alone does not establish conformance.  
   https://www.w3.org/TR/WCAG22/
2. **W3C mobile accessibility guidance** — mobile web and hybrid interfaces must account for touch, small screens and varied input modes; use WCAG guidance for the website and browser prototype.  
   https://www.w3.org/WAI/standards-guidelines/mobile/
3. **Android accessibility guidance** — implement semantic labels, large controls, non-colour cues and complete flows that work with Android accessibility services; use Accessibility Scanner and manual/automated testing.  
   https://developer.android.com/guide/topics/ui/accessibility  
   https://developer.android.com/guide/topics/ui/accessibility/views/testing-views
4. **Kenya Standard KS 2952-1:2022 and KS 2952-2:2022, Accessibility — ICT products and services** — relevant local standards to review. The standard covers functional accessibility needs including usage without vision, limited vision, without colour perception, without hearing, limited manipulation/reach, cognition/language, privacy, web, software, assistive-technology interoperability, and conformance testing. Confirm the authoritative current edition, licensing/access, and applicability with KEBS or qualified counsel.  
   Public reference copy: https://inable.org/wp-content/uploads/2023/08/Kenya-Accessibility-Standard-ICT-Products-and-Services_KS-2952-1-2-2022_May_2023_.pdf
5. **Kenya Persons with Disabilities Act, 2025 (Act No. 4 of 2025)** — section 30 includes accessibility rights relating to information and communications, appropriate technologies and systems, and minimum standards for facilities/services open to the public. Assess obligations for the actual product/service and distribution context with Kenyan counsel; do not infer blanket compliance from design changes.  
   https://new.kenyalaw.org/akn/ke/act/2025/4/eng%402025-05-13/source

## Validation plan and release gates

| Test area | Test method | Release gate |
|---|---|---|
| Camera start | Cold launch; permission allow/deny/revoke; another app holding camera; front/back availability; preview only; analyzer failure | Camera failure is reported clearly; preview works independently where possible; no false “detector ready” status |
| Voice/microphone | Permission allow/deny/revoke; offline speech service; network error; silence/no-match; phone call/audio focus; repeated start/stop | Touch controls remain usable; mic state is truthful; no retry loop; clear recovery action |
| TalkBack | Complete core tasks without looking at screen | Every task can be discovered, started, understood, stopped and recovered using speech feedback |
| Voice Access / Switch Access | Complete core tasks using each input mode | No essential action is gesture-only or visually discoverable only |
| Browser accessibility | Automated checks plus keyboard, screen reader, zoom/reflow and contrast checks | No known critical blocker in core flows; WCAG 2.2 AA review recorded with evidence |
| Object detection | Device/lighting/distance test matrix, including no model and low-confidence conditions | Never display object or range claims when detector/model/scale is unavailable |
| Spatial geometry | Repeatable physical measurements across devices, angles and surfaces | No metric navigation or hazard claim before pre-defined accuracy and false-negative gates pass |
| Usability | Moderated task tests with blind/low-vision participants using their own assistive settings | Record completion rate, time, recovery rate, speech interruptions, confidence/trust and qualitative issues; fix high-severity blockers before expanding features |

## Suggested scorecard

Measure the following in a side-by-side study against Lookout, Seeing AI and Be My Eyes where the same task is supported. Do not assign competitor scores from marketing pages.

- Time to first successful task
- Task completion without sighted assistance
- Number of taps / mode changes
- Speech interruption count and time to stop speech
- Correctness, omission and false-positive rate for object/text descriptions
- Camera/microphone failure recovery time
- Offline task completion rate
- Battery drain per 15-minute task
- User-reported confidence and perceived control
- Compatibility issues with TalkBack, Voice Access and Switch Access

## Immediate priorities for the next Android build

1. Fix and verify camera preview start separately from image analysis; add a diagnostic status for permission denied, provider failure, preview failure and detector unavailable.
2. Fix voice start/stop lifecycle and microphone permission flow; expose exact error and a retry path without restarting endlessly.
3. Simplify native home into task-first Android-native navigation; keep the browser prototype visually separate.
4. Add regression checks for TalkBack labels, touch targets, focus order and permission-denial states.
5. Keep distance, bearing, obstacle-height and hazard alerts disabled until physical validation demonstrates the defined thresholds.
6. Add the benchmark and compliance checklist to release review; publish no conformance or safety claim without test evidence.
