# ICA Agents — One-Click Test APK

This repository is prepared to build the Android Agent application on GitHub's hosted runner.
It produces a real file named `ICA-Agents-test.apk` as a downloadable workflow artifact.

## What you need

- A free GitHub account.
- No Android Studio.
- No Android SDK installation on your computer.
- No Google Play Store publication.

## Step 1 — Create a GitHub repository

1. Open GitHub and sign in.
2. Create a **new repository**.
3. Give it a name such as `ica-agents-mobile`.
4. Keep it Private if the source code should not be public.
5. Create the repository.

## Step 2 — Upload this package

Extract this ZIP on your Windows computer.

Open the extracted folder. Upload **all files and folders inside it** to the root of your new GitHub repository. Do not upload the outer ZIP as a single file.

The repository root should directly contain folders such as:

- `.github`
- `android`
- `backend-additions`
- `distribution`
- `README.md`

## Step 3 — Start the APK build

After the upload finishes:

1. Open the repository on GitHub.
2. Click **Actions**.
3. Select **Build ICA Agents Test APK** on the left.
4. Click **Run workflow**.
5. Select the `main` branch.
6. Click the green **Run workflow** button.

The workflow installs Java, Android SDK and Gradle on GitHub's build server and compiles the actual APK.

## Step 4 — Download the APK

Wait for the workflow to show a green check mark.

1. Open the completed workflow run.
2. Scroll to **Artifacts** at the bottom.
3. Click **ICA-Agents-test-APK**.
4. GitHub downloads a small ZIP containing:

`ICA-Agents-test.apk`

Extract that ZIP and transfer `ICA-Agents-test.apk` to your Android phone.

## Step 5 — Install on Android

On the Android phone:

1. Open the APK file.
2. If Android asks for permission, allow installation from that source.
3. Install **ICA Agents**.
4. Open the application.
5. Test login, registration, leads, invoices, payments and profile functions.

The test build is a debug build and is intended for testing, not final public distribution.

## Important: server connection

The APK is an installable Android application, but the Agent system itself is not an offline database. It connects to the configured DMS/EMS APIs.

The current Android build configuration contains:

- DMS API: `https://dms.icgroup-india.com/api/`
- EMS API: `https://ems.icgroup-india.com/api/`

If these are not the correct live API addresses for your Agent package, update them in:

`android/ICA-Agents/app/build.gradle.kts`

Specifically change the `DMS_BASE_URL` and `EMS_BASE_URL` values before building.

## If the build fails

Open the failed workflow run and open the **Build debug APK** step. Copy the error message and send it to ChatGPT. The source package is structured so the build error can be corrected without changing your DMS/EMS database.

## iPhone / iPad

`ICA-Agents-test.apk` is Android-only. iOS requires a separate signed iOS application (`.ipa`) or a web/PWA version. The same DMS/EMS backend can be used by both applications.

## Security note

Keep the GitHub repository **Private** while testing if the source contains proprietary business logic or API information.
