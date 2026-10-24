# CancelBefore Release Notes — v{VERSION}

**Release Date:** {DATE}  
**Artifact Type:** {RELEASE_TYPE} (e.g., Debug Testing APK / Signed Release APK)  
**Package ID:** `com.aistudio.cancelbefore.rsmkqa`  
**Minimum Android Version:** Android 8.0 (API 26)  
**Target Android Version:** Android 14 / 15 (API 36)  

---

## 🚀 What's New & Highlights

CancelBefore helps you track subscriptions, free trials, warranties, and bill renewal deadlines so you cancel unwanted charges before you are billed.

### Core Capabilities
- **Deadline Tracking & Countdown:** Track recurring subscriptions (monthly, quarterly, annual) and one-time trial periods with live countdowns to cutoff times.
- **Punctual Cutoff Reminders:** Configurable advance notifications (e.g. 24 hours, 3 days, 1 week before renewal) scheduled via Android `AlarmManager`.
- **On-Device Payment & Mandate Auto-Detection:**
  - Detects recurring UPI AutoPay mandates, bank standing instructions, card recurring payment notices, and subscription renewal alerts from payment apps.
  - Normalizes merchants (e.g. "Netflix India", "Spotify AB") and classifies events across 10 payment event categories.
  - Event fingerprinting and SHA-256 deduplication prevent duplicate reminders.
  - Reviews pending detections before adding them to your active list.
- **Privacy-First Architecture:**
  - 100% on-device processing. No external tracking, remote telemetry, or third-party servers.
  - Automatic scrubbing of sensitive numbers (OTPs, PINs, card CVVs, full account numbers).
  - Local SQLite Room database with validated migrations preserving existing records across updates.
- **Export & Backup:** Export your records to CSV or create local JSON backup archives for device migration.
- **Developer Diagnostics:** Built-in diagnostics view to inspect notification access status, alarm scheduling health, and database versioning.

---

## 🧪 Automated Quality Gate & Test Status

- **Unit & Robolectric Test Suite:** 100% PASSING (all test classes including detection corpus, reminder math, and database safety).
- **Android Lint:** 0 Compilation Errors.
- **Architecture:** Kotlin, Jetpack Compose, Material 3, Room, MVVM, StateFlow, Coroutines.

---

## ⚠️ Known Limitations & Operational Notes

1. **Notification Detection Requires Explicit Permission:**  
   CancelBefore does not read private SMS or access bank APIs directly. It analyzes notification banners on-device only after you grant "Notification access" in system settings.
2. **One-Time Payments Are Not Subscriptions:**  
   Ordinary one-time retail purchases (e.g. dining, groceries, peer-to-peer transfers) are intentionally ignored to prevent false-positive subscriptions.
3. **Email/Gmail Integration:**  
   The Gmail detection architecture is an offline foundation interface. It is disabled by default and does not communicate with external Google APIs without user-configured OAuth client credentials.
4. **OEM Background Battery Optimizations:**  
   Certain aggressive device skins (MIUI, ColorOS, EMUI) may defer alarms if battery optimization is enabled. For guaranteed exact cutoff alerts, set CancelBefore's battery usage to **Unrestricted** in Android App Info.

---

## 📲 How to Install

Download the attached **`CancelBefore-debug.apk`** (or `CancelBefore-release.apk`) directly to your Android device.  
For step-by-step sideloading instructions, refer to [`docs/INSTALL_APK.md`](docs/INSTALL_APK.md).

Report bugs, feedback, or merchant detection requests via the GitHub repository's **Issues** tab.
