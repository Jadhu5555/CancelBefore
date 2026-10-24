# Release Signing Guide for CancelBefore

This document explains how to set up cryptographically signed production/release APK builds using GitHub Actions Secrets.

---

## 🔒 Security Principles

- **Never commit `.jks` or `.keystore` files directly to Git.**
- **Never commit keystore passwords, key aliases, or private secrets to code or Gradle files.**
- If signing secrets are **not** present in your GitHub repository, the GitHub Actions workflow will automatically and safely fall back to building **`CancelBefore-debug.apk`**. Debug APKs are fully functional and can be installed on any physical Android test device without issues.

---

## 🛠️ Step 1: Generate Your Upload Keystore Locally

Run the following command in a terminal on your computer (using the standard Java `keytool` command):

```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass YOUR_SECURE_STORE_PASSWORD \
  -keypass YOUR_SECURE_KEY_PASSWORD
```

When prompted, fill in your organization or developer details. Keep `my-upload-key.jks` in a secure location on your private machine.

---

## 🔑 Step 2: Convert Keystore to Base64

To safely store the binary keystore as a GitHub secret:

**On macOS / Linux:**
```bash
base64 -w 0 my-upload-key.jks > keystore_base64.txt
# (On macOS, use: base64 -i my-upload-key.jks -o keystore_base64.txt)
```

**On Windows (PowerShell):**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks")) | Out-File -Encoding utf8 keystore_base64.txt
```

---

## ⚙️ Step 3: Configure GitHub Secrets

1. Navigate to your repository on GitHub.
2. Go to **Settings** → **Secrets and variables** → **Actions**.
3. Under **Repository secrets**, click **New repository secret** and add the following three secrets:

| Secret Name | Value | Purpose |
| :--- | :--- | :--- |
| `KEYSTORE_BASE64` | The entire base64 string from `keystore_base64.txt` | Binary upload keystore |
| `STORE_PASSWORD` | The password you chose for `-storepass` | Unlocks the keystore |
| `KEY_PASSWORD` | The password you chose for `-keypass` | Unlocks the private key |

---

## 🚀 Step 4: Building Signed Releases

Once those secrets are saved:
- When you create and publish a new GitHub Release or trigger the workflow manually with `build_release_if_signed: true`:
  - The workflow decodes `KEYSTORE_BASE64` in a temporary step.
  - Runs `./gradlew :app:assembleRelease`.
  - Produces **`CancelBefore-release.apk`**.
  - Attaches both the release APK and debug APK to the GitHub Release.
  - Deletes the temporary keystore file immediately upon build completion.
