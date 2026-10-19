# RideWise v1.3 validation

Date: 19 October 2026 (Asia/Colombo). No real third-party ride request has been used as a test fixture.

Build: JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin/Compose compiler 2.2.20, KSP 2.2.20-2.0.3, compile/target API 36, min API 26, Compose BOM 2025.08.00, Room 2.7.2, bundled ML Kit 16.0.1.

## Executed checks

| Check | Result |
|---|---|
| Debug APK and UI-test APK | PASS |
| Domain JUnit | PASS; 69 tests: 26 calculator/aggregation, 26 OCR parser/formatting, 17 gross-rate/notification parser |
| App JVM tests | PASS; 4 capture-state and source-filter tests |
| Native device suite | PASS; 18 tests, 0 failures, 0 skips; 34.888 seconds |
| Lint | PASS; 0 errors, 20 advisory warnings |
| Install/launch | PASS on Android 17/API 37 x86_64 emulator |
| Notification-listener special access | Android settings and consent dialog exercised for RideWise |
| Display-over-apps access | Android settings exercised and permission granted |
| Notification runtime permission | Android Allow dialog exercised without starting screen capture |
| Explicit Start and system listener connection | PASS; in-app confirmation, ON status and Android system service binding verified |
| Persistent notification and Stop | Visible ON notification and Stop action inspected; Stop tested from notification |
| Floating GOOD badge over another surface | PASS; synthetic LKR 600 / (2 + 8 km) = LKR 60/km shown over Android launcher |
| Floating BAD badge | PASS; synthetic LKR 350 / (2 + 8 km) = LKR 35/km; red overlay visually inspected |
| Overlay lifecycle tests | PASS; GOOD/BAD, stop/clear, permission gating and disabling sources |
| Original screen capture | Android consent to explicit one frame, bundled OCR and editable review verified on RideWise's own screen in v1.0; implementation remains available |
| Merged manifest | Capture foreground-service permissions, notifications, optional overlay and signature-protected internal receiver. Listener non-exported and protected by Android's binding permission. No Internet, accessibility, camera or storage permission |
| Layout | Light/dark base flows and floating GOOD screenshot inspected |

The 18 device tests comprise six Compose flows, three Room/snapshot tests, four ViewModel/session tests, one bundled OCR test and four Live-assistant tests. All requests are synthetic. The runner was invoked directly through ADB; connectedDebugAndroidTest is available but was not used for this record.

Evidence: [device output](test-results/device-tests.txt), [build output](test-results/build.txt), [floating GOOD](screenshots/floating-good.png). Earlier screenshots show v1.0; v1.1 adds Live navigation, a dashboard shortcut and gross-rate card. v1.2 adds two supplied offer-card OCR parser fixtures; v1.3 accepts an explicitly shared screenshot and processes it locally without retaining the image. The updated debug build and all 73 JVM tests pass.

The build ran assembleDebug, assembleDebugAndroidTest, domain:test, app:testDebugUnitTest and lintDebug. The device suite ran through lk.ridewise.test/androidx.test.runner.AndroidJUnitRunner.

Lint warnings are version/target suggestions and optional KTX style suggestions. No permission or API-guard errors remain. The installed SDK emits an XML-version compatibility warning. Its incomplete optional NDK prevents stripping two bundled native libraries, which are packaged unchanged; the APK installs and runs. RideWise has no native source requiring an NDK.

## Live rule and notification scope

The driver confirmed pickup distance plus passenger trip distance as the denominator. Exact decimal multiplication is used before rounding: strictly above LKR 40/km is GOOD; exactly 40 or less is BAD. Missing or invalid inputs and ambiguity are CHECK DETAILS. This quick rule does not deduct fuel, commission or other costs. The net-earnings calculator remains separate.

Only com.ubercab.driver and com.pickme.driver.byod are selectable sources. Package identifiers were checked against the official Uber listing and PickMe driver-download page; that confirms source identifiers, not real notification compatibility. The listener checks the package before reading extras, processes only while explicitly enabled, never invokes notification actions and never queries old active notifications. It cannot detect driver online/offline state or read a popup that was never posted as a notification.

Raw messages and passenger data are not persisted. Only opt-in and source switches persist in private preferences. Numeric results/source/time remain in memory. Screenshots in this documentation are development QA artifacts, not screenshots saved by the app's capture feature.

No real incoming provider notification, provider API or authorized live integration was available. Live provider extraction has NOT been verified. Two OCR parser fixtures were added from the supplied PickMe/Uber offer-card layouts: LKR amount, an `away` pickup leg, then the passenger-trip leg. They verify field extraction from equivalent text, not an end-to-end capture of a third-party app on a physical phone. Android may redact or omit notification fields; providers may send no useful notification; OEM policies may limit delivery or overlays. Use manual entry or consent-based capture when fields are missing.

## Remaining physical-device checks

- Test permitted, anonymized notifications from the actual installed driver app versions and validate the one-shot capture flow against a visible third-party offer card without guessing fare or distance semantics.
- Check incoming, updated, cancelled, expired and removed requests; duplicate posts; both providers posting together; and Android redaction.
- Check Stop from the persistent notification, permission revocation, listener reconnection and OEM battery controls.
- Check overlay placement against driver-app controls. It is draggable/dismissible and expires after 12 seconds, but no universal position can guarantee it never covers a control.
- Check smaller screens, large fonts, TalkBack, rotation, lock screen and physical-device screen capture. Review only when safely parked.
- A provider-supported integration still needs documented authorization/API semantics and physical-device validation before it can be described as working.

References: [notification listener](https://developer.android.com/reference/android/service/notification/NotificationListenerService), [overlay access](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION), [restricted settings](https://support.google.com/android/answer/12623953), [Uber Driver](https://play.google.com/store/apps/details?id=com.ubercab.driver), [PickMe Driver](https://pickme.lk/drive/).
