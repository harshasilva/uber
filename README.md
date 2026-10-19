# RideWise

A native, offline Android companion for reviewing **estimated** ride economics, with an optional notification-based floating GOOD/BAD rate badge. Kotlin, Jetpack Compose, Material 3, MVVM, Navigation Compose, Room and bundled ML Kit Latin text recognition. Android 8.0/API 26 or newer. No backend, account, analytics, accessibility service or automatic ride actions.

## Live GOOD / BAD assistant (v1.3)

The driver's rule is **displayed ride price ÷ (pickup distance + passenger trip distance)**. Strictly above **LKR 40/km = GOOD**; **LKR 40/km or below = BAD**. This is a gross rate threshold, separate from the fuel/operating-cost profit estimate. It does not establish net profit or equate a customer fare with a driver's payout.

1. Install the updated APK and open **Live** (or **Live GOOD / BAD assistant** on Home).
2. Choose Uber Driver, PickMe Driver or both.
3. Follow the three Android setup buttons: **notification access**, **display over other apps**, and **RideWise notifications**. Read the in-app access explanation first. If your sideloaded app is restricted, Android may expose **Allow restricted settings** in RideWise's App info; follow your device's own prompts. See [Android's explanation](https://support.google.com/android/answer/12623953).
4. Tap **Start live assistant**, then **Enable assistant**. A persistent RideWise notification shows that it is on and provides **Stop assistant**.
5. Open the driver app yourself. When that app posts a new eligible ride notification with a clearly labelled price and both distances, RideWise shows a compact floating **GOOD**, **BAD**, or **CHECK DETAILS** badge. Drag its title away from controls, tap × to dismiss, or wait 12 seconds. No driver-app control is invoked.

Use **Demo GOOD** and **Demo BAD** after granting display-over-apps access to test the badge immediately. They use synthetic LKR 600 / 10 km = 60 (GOOD) and LKR 350 / 10 km = 35 (BAD), and do not turn on notification listening. The same rule also appears on manual/demo/OCR profitability result screens, after review.

**Limits:** This reads Android notifications, not arbitrary in-app popups or driver online/offline state. The allowlist uses `com.ubercab.driver` and `com.pickme.driver.byod`, matching the official driver app listings. Other apps, rider apps, group summaries, old events, and recognizable completed/cancelled requests are ignored. Android's notification-access permission is broad, but the code checks the package before reading extras and processes only enabled sources. Only numeric results/source/time remain in memory; no raw messages or passenger data enter history. Only explicit opt-in/source switches persist.

Missing pickup/trip distances, unclear amounts, conflicting values and unsupported currency produce **CHECK DETAILS**, never an invented verdict. A total-distance label alone is insufficient to confirm pickup inclusion. If the provider omits fields, use the existing capture/manual workflow. Uber/PickMe live compatibility still requires real-phone tests with permitted notification samples; no official provider API integration or real ride-request validation is claimed. Notification text layouts and OEM policies can prevent automatic evaluation.

## Run on a phone

1. Open this directory in Android Studio, with JDK 17. Install Android SDK Platform 36 and Build Tools 35.0.0. Android Studio can generate `local.properties`; the checked-out build uses your own SDK location.
2. Enable developer options and USB debugging on your Android phone, connect it and approve your computer.
3. Select the `app` run configuration and press Run, or use:

```powershell
.\gradlew.bat :app:installDebug
adb shell am start -n lk.ridewise/.MainActivity
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. You can also copy this APK to a phone and open it, granting that file source permission to install apps when Android asks. A production release needs your own signing key and device testing.

## Try the complete demo

Home → **Try Demo Ride** → review/edit fields → tick the confirmation checkbox → **Calculate Profitability** → **Save analysis & return**. Open **History**, tap the saved ride to reopen the snapshot, or delete it with confirmation. Clear-all history also requires confirmation.

The synthetic fixture uses LKR 1,800 payout, 1.5 km pickup + 8.5 km passenger trip, 5 + 25 minutes, and no empty return. Sample costs are explicitly labelled: LKR 350/L, 14 km/L, maintenance LKR 8/km, depreciation LKR 5/km and LKR 0 other costs. Expected results:

| Metric | Demo estimate |
|---|---:|
| Distance | 10 km |
| Fuel cost | LKR 250.00 |
| Vehicle operating cost | LKR 130.00 |
| Net earnings | LKR 1,420.00 |
| Net / km | LKR 142.00 |
| Net / hour | LKR 2,840.00 |

Demo costs do **not** overwrite vehicle settings, and demo records are excluded from dashboard totals. No actual completed trips or actual income are inferred from analyses.

## Manual rides and vehicle costs

Use **Vehicle** to enter your fuel price, economy, maintenance, depreciation, default trip costs and optional empty return. All fields start unconfigured. Blank costs are unknown and visibly excluded; enter 0 explicitly for no cost. Reset clears the fields; press Save to apply. Settings persist locally in Room.

Use **Enter Ride Manually**, supply expected **driver payout**, LKR currency and both pickup and passenger trip distances. Time and general route labels are optional. Missing time disables the hourly estimate. Unreviewed fields or missing costs produce a **provisional partial net estimate** with warnings. Add only extra costs not already deducted from payout.

**Edit ride** on the results page edits the inputs. **Edit costs** changes assumptions for that analysis only and recalculates; save the revised analysis to retain it. Original saved analyses retain their original cost snapshots. Editing a saved ride creates a new analysis when recalculated.

Dashboard totals use complete, saved, non-demo analyses from the device’s current local day. Per-km and hourly aggregates use weighted totals over comparable complete analyses. An empty day shows unavailable metrics, rather than invented income. Provisional estimates remain visible in history.

## Optional screen capture and OCR

1. Open **Capture** and read the explanation. Tap **Start screen session**.
2. Allow capture notifications when Android asks. These provide visible **Capture once** and **Stop** controls; if notifications are disabled, use manual/demo entry or re-enable them in Android settings.
3. Grant Android’s MediaProjection consent for this session. On supported Android versions select one app or the entire screen. A floating popup outside the selected app may require entire-screen sharing, if you are permitted to capture it.
4. Open your permitted screen yourself. Pull down notifications, tap **Capture once**, then close the shade. RideWise captures one frame after two seconds, stops projection and runs OCR locally.
5. Return to RideWise to review and edit the extracted fields. No automatic calculation or ride interaction occurs.

You can also request the one frame from the Capture screen, useful for testing your own content. Stop from the app, the persistent notification or Android’s sharing indicator. Waiting sessions expire after two minutes. Each new session requires fresh Android consent; consent tokens and virtual displays are never reused. No virtual display is created while waiting for the explicit capture action. Process termination does not restart capture.

Capture cancellation, notification denial, Android revocation, timeouts, unavailable capture, blank/protected screens and OCR errors have recovery messages. Protected screens are never bypassed. No screenshot or complete OCR text is saved. Only selected, reviewed ride fields and analysis snapshots can enter history.

The one-shot OCR parser also recognises the supplied visible PickMe/Uber offer-card structure: an LKR offer amount, an `away` row for pickup, then a trip row. It shows the resulting gross GOOD/BAD rate at the review step. This is a driver-initiated capture; RideWise does not watch another app's screen continuously.

For a visible offer card, the quickest path is to take a normal Android screenshot and select **Share → RideWise**. The shared image is read locally once and discarded. This does not need screen-sharing consent or the Capture once notification action.

**Physical-device popup capture and third-party integration have not been verified or authorized.** Text fixtures based on supplied PickMe/Uber offer-card screenshots are covered by parser tests, but the implemented capture path still requires testing on the intended Android devices and permitted content before relying on it.

## Calculation rules

The independent `domain` module uses `BigDecimal`, retaining precision internally and rounding only for presentation. Division uses 12 fractional digits with HALF_UP rounding.

```text
distance = pickup km + passenger trip km + included empty-return km
fuel = distance × fuel price / economy
vehicle operating = distance × (maintenance/km + depreciation/km)
other = configured default trip costs + entered additional trip costs
net estimate = expected driver payout − included fuel − included operating − included other
net/km = net estimate / distance
net/hour = net estimate × 60 / (pickup minutes + passenger trip minutes)
```

All distance-based quantities use the same distance basis. A ride-specific return overrides the default, including explicit zero. Missing essential payout, currency or distances prevents a net calculation. Unknown costs remain null and are visibly excluded, making any available net result provisional. A partial operating-cost row includes only configured components, with separate warnings for exclusions. Missing times prevent hourly output. When empty return is included, hourly output explicitly excludes unprovided return time. Zero denominators, negative inputs and unsupported currency cannot produce a complete estimate. Negative net estimates remain negative. Customer fare never substitutes for driver payout; there is no currency conversion.

## Project layout

```text
domain/src/main/kotlin/lk/ridewise/domain/
  model/         Decimal-safe nullable requests, assumptions, confidence and results
  calculator/    Pure calculation engine and daily aggregate logic
  rate/          Strict >40 gross-rate rule and conservative notification parser
  parser/        RideTextParser contract and conservative label-based implementation
  capture/       RideRequestSource extension contract
  demo/          Synthetic request source
  util/          Decimal parsing and LKR display formatting
app/src/main/java/lk/ridewise/
  data/local/    Room database, DAOs, versioned decimal-safe snapshot codec
  data/repository/  Repository interface and Room implementation
  capture/ocr/   On-device OCR interface and ML Kit implementation
  capture/screencapture/  Consent-bound service, one-shot source, session state
  live/          Opt-in listener, source filter, state/controller, notification and overlay
  ui/            ViewModel, navigation, theme and all screen flows, including Live
  MainActivity.kt / RideWiseApplication.kt
```

ViewModels depend on repository and capture-state interfaces, not database or MediaProjection APIs. The Activity owns OS permission prompts; the foreground service owns projection and pixel resources. The parser and calculator run independently of Compose. Room stores exact decimal strings and original analysis snapshots. `SavedStateHandle` restores the active draft/result after recreation; edit forms use saveable state. Database schema version 1 is exported to `app/schemas`.

## Build and test

```powershell
.\gradlew.bat :domain:test :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
# Requires a booted emulator or connected phone:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Domain JUnit tests cover labels, currency, spacing, decimal values, supplied PickMe/Uber offer-card fixtures, missing/conflicting fields, total-distance ambiguity, costs, net/km, net/hour, invalid inputs, negative net, unknown assumptions and dashboard aggregation. App JVM tests cover capture denial and reset states. Device JUnit tests exercise actual Room round trips/deletion/settings, snapshot precision, ViewModel flows/restoration and bundled OCR. Compose instrumentation tests cover demo/manual calculations, extracted-field edits, save/reopen/delete, clear confirmation, settings validation and denial recovery using synthetic fixtures.

**Verified (v1.3):** the updated debug APK builds; 73 JVM tests pass. Earlier validation also covered UI-test APK builds, 18 Android device tests and lint (0 errors; 20 advisory warnings). Android permission setup and a synthetic GOOD overlay over the emulator launcher were checked. The original screen-consent/one-shot OCR flow was exercised using RideWise’s own screen. See [validation notes](docs/VALIDATION.md) for exact scope and remaining device-level work.

Actual emulator screenshots: [dashboard](docs/screenshots/dashboard.png), [review](docs/screenshots/review.png), [results](docs/screenshots/results.png), [dark dashboard](docs/screenshots/dashboard-dark.png).

Live floating badges: [GOOD over the Android launcher](docs/screenshots/floating-good.png), [BAD demo](docs/screenshots/floating-bad.png). Earlier screen screenshots show the v1.0 base flows; v1.1 adds the Live tab, dashboard shortcut and rate card, v1.2 adds the supplied PickMe/Uber offer-card OCR fixtures, and v1.3 accepts a user-shared screenshot.

## Privacy and permissions

- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION`: keep the consented screen session visible and comply with Android’s MediaProjection lifecycle.
- `POST_NOTIFICATIONS` on Android 13+: show user-controlled capture actions and the live assistant's visible Stop control.
- `SYSTEM_ALERT_WINDOW` special access: display the optional, explicitly enabled draggable rate badge over other apps.
- Android notification-listener special access: deliver new notifications to the system-bound listener. `BIND_NOTIFICATION_LISTENER_SERVICE` protects the non-exported service so only Android can bind it; this is not a general runtime permission grant.
- Android’s system MediaProjection consent: requested freshly per session, after the in-app explanation.

The merged app explicitly removes Internet/network-state permissions inherited from libraries. OCR’s Latin model is bundled and works without a cloud request or first-use download. Automatic cloud backup and device-transfer backup are disabled. App-private Room storage is not additionally encrypted by RideWise; Android’s device security protects it. Keep route labels general and omit passenger identities. History deletion is available individually and in bulk. There is no screenshot storage, raw-text logging or accessibility surveillance. Overlay and notification-listener access serve only the optional Live feature and can be revoked in Android settings.

## Limits and an authorized integration

The MVP parser recognizes explicitly labelled English/Latin fields, LKR or Rs notation, km/metres and minute/hour durations. It is not trained or tested against a third-party layout. Ambiguous or conflicting values remain null, with confidence explanations. Sinhala and Tamil recognition are not bundled. Screen content, OEM differences, rotated/app-only capture, rapid popup changes and protected surfaces require device testing. OCR can be wrong even when a label is recognized.

To add a real integration: obtain the provider’s documented API/SDK and explicit authorization, implement `RideRequestSource`, map **driver payout** and distance/time semantics without guessing, add synthetic contract/layout fixtures and lifecycle/error tests, then validate on authorized real devices and sources. Any new permissions need their own visible explanation. Only after this validation should that integration be described as working.

Implementation references: [Android MediaProjection lifecycle](https://developer.android.com/media/grow/media-projection), [bundled on-device ML Kit OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2/android), [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes).
