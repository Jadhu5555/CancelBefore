# Installing CancelBefore on Android (Direct APK Sideloading)

This guide explains how to install and update **CancelBefore** on any physical Android device without needing a computer, USB cable, Android Studio, or ADB.

---

## Prerequisites
- **Android Version:** Android 8.0 (API 26) or newer (Android 10, 11, 12, 13, 14, 15+ supported).
- **Storage:** ~20 MB free storage.

---

## 1. Downloading the APK

You can obtain the APK in one of two ways from the GitHub repository:

### Option A: From GitHub Releases (Recommended for Phones)
1. On your Android phone, open Chrome (or any mobile browser) and navigate to your GitHub repository's **Releases** page (`https://github.com/<your-username>/<repo-name>/releases`).
2. Tap on the latest release.
3. Under **Assets**, tap **`CancelBefore-debug.apk`** (or `CancelBefore-release.apk` if a signed build is attached).
4. Chrome may show a warning: *"File might be harmful. Do you want to download CancelBefore-debug.apk anyway?"*
   - Tap **Download anyway**.

### Option B: From GitHub Actions Artifacts
1. Go to the **Actions** tab in the GitHub repository.
2. Tap on the latest successful run of the **Android CI & APK Distribution** workflow.
3. Scroll down to the **Artifacts** section at the bottom.
4. Tap **`CancelBefore-APK`** to download the zip file.
5. Open your phone's **Files** app (e.g., *Files by Google*), open your *Downloads* folder, tap the downloaded `.zip` file, and tap **Extract**. You will see `CancelBefore-debug.apk`.

---

## 2. Installing the APK

1. Once downloaded, tap the notification or open your **Files** app and tap `CancelBefore-debug.apk`.
2. **"Install Unknown Apps" permission:**
   - If prompted: *"For your security, your phone is not allowed to install unknown apps from this source."*
   - Tap **Settings**.
   - Toggle **Allow from this source** to **ON**.
   - Tap the **Back** arrow at the top-left.
3. Tap **Install** when the system dialog asks *"Do you want to install this app?"*.
4. **Google Play Protect warning:**
   - Because the debug APK is signed with a local developer certificate (standard for non-Play Store builds), Play Protect may display: *"Blocked by Play Protect - Unrecognized app developer"*.
   - Tap **More details** (or the down arrow).
   - Tap **Install anyway**.
   - If Play Protect asks to scan the app, tap **Don't send** or **Send** as preferred.
5. Tap **Open** to launch CancelBefore!

---

## 3. Recommended Permissions for Full Functionality

CancelBefore runs completely locally on your device with no remote database, tracking, or cloud servers. To get full value from its deadline alerts and payment auto-detection, configure the following:

### A. Notifications (Android 13+)
- On first launch, when prompted with *"Allow CancelBefore to send you notifications?"*, tap **Allow**.
- This enables timely reminders before free trials, subscriptions, or warranties renew or expire.

### B. Exact Alarms (Android 12+)
- Go to: **Settings → Apps → CancelBefore → Alarms & reminders** (or Special app access).
- Ensure **Allow setting alarms and reminders** is **ON**.
- This allows CancelBefore to trigger exact cutoff reminders at your requested time.

### C. Notification Listener (Payment & Mandate Auto-Detection)
- CancelBefore can detect recurring payment confirmations, UPI AutoPay mandates, and debit alerts from apps like Google Pay, PhonePe, Paytm, and banking apps without reading SMS or requiring banking logins.
- When prompted in the app (or in the App Settings), tap **Enable Notification Detection**.
- In the system **Device & app notifications** screen, find **CancelBefore** and toggle to **Allow**.
- *Note:* Notification analysis is 100% on-device. Sensitive OTPs and PINs are scrubbed automatically.

---

## 4. Updating CancelBefore

To update CancelBefore in the future:
1. Download the new APK from GitHub.
2. Tap the APK and select **Update**. Your existing subscriptions, reminders, and categories will be safely preserved in the local Room database.

> **Important Note on Signatures:**
> Android requires subsequent updates to be signed with the same key.
> - If you previously installed a **Debug APK** (`CancelBefore-debug.apk`), update it with subsequent **Debug APKs**.
> - If switching between a Debug APK and a signed Release APK, Android will report *"App not installed / package conflicts with an existing package"*. Before switching, go to CancelBefore's **Settings → Backup & Export**, export a JSON/CSV backup of your subscriptions, uninstall the old version, install the new APK, and restore your data.

---

## 5. Troubleshooting Common Issues

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| **"Can't open file"** | The browser download manager cannot parse APK packages directly. | Open the **Files** or **Files by Google** app, go to **Downloads**, and tap the APK from there. |
| **"App not installed" / "Package conflicts"** | Signature mismatch between an existing debug build and a newly downloaded build. | Export a backup in app settings, uninstall the existing CancelBefore app from your device, and install the new APK. |
| **"Parse Error / There was a problem parsing the package"** | Download was incomplete, corrupted, or Android version is below 8.0 (API 26). | Re-download the APK over a stable connection and verify your Android OS version in *Settings → About Phone*. |
| **"Blocked by Play Protect"** | Standard security notice for sideloaded test APKs. | Tap **More details** → **Install anyway**. |
| **Reminders not ringing at exact time** | Battery optimization killing background alarms. | Go to *Settings → Apps → CancelBefore → Battery*, select **Unrestricted**. |
