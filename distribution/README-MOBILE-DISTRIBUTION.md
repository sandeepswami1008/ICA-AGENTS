# ICA Agents — Mobile Distribution

This distribution kit applies **only to ICA-Agents**. It is unrelated to AI-One-Studio.

## Important: what “offline APK” means

The APK can be downloaded, copied by USB/WhatsApp/Drive, and installed without the Play Store.
The installed app is **not an offline data application**: login/OTP, leads, invoices, payments,
KYC uploads and profile data are retrieved from the DMS/EMS servers, so those functions require
an internet connection.

## Android testing without Play Store

1. Put this project in a GitHub repository.
2. Open **Actions → Build ICA Agents APK**.
3. Run the workflow manually, or push to `main`.
4. Download the `ICA-Agents-test-APK` artifact.
5. Share the APK by Drive, WhatsApp, USB, direct download link, etc.
6. On the Android phone, allow installation of apps from the source used to open the APK.
7. Install and test the agent login, OTP, registration/KYC, leads, invoices, payments and profile.

The test APK is a debug build and is suitable for controlled testing. For production distribution,
use a signed release APK/AAB with a permanent signing key.

## Production Android signing

Create a release keystore once and keep it backed up securely. Configure these GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The workflow will then build a signed release APK when manually dispatched.

**Never commit the keystore, passwords or signing secrets to the repository.**

## QR code / direct link

The file `distribution/install.html` is a small installation landing page. After you upload the APK to
your own HTTPS location, replace the APK URL in that file. The page shows the direct download link and
can generate a QR code for that URL.

Recommended flow:

`QR code → HTTPS download page → ICA-Agents APK → Android installation`

## iPhone / iPad

An Android APK cannot run on iOS. iPhone/iPad distribution requires a separate iOS build (IPA) and
Apple signing, normally through Xcode on macOS, TestFlight, or an approved ad-hoc/internal distribution
method.

The existing Android source does not create an iOS application automatically. The backend API can be
shared, but the iOS client must be built separately.

## Backend requirement

Before testing the app, deploy and test the backend additions in `backend-additions/PATCH-NOTES.md`.
The Android app currently points to:

- DMS: `https://dms.icgroup-india.com/api/`
- EMS: `https://ems.icgroup-india.com/api/`

Change these in `android/ICA-Agents/app/build.gradle.kts` only if your actual production hosts differ.

No DMS/EMS data is bundled inside the APK.
