# ICA-Agents — Android app + backend additions

This package has three parts:

```
backend-additions/   PHP + SQL to add to your existing DMS/EMS (see PATCH-NOTES.md)
android/ICA-Agents/  Full Kotlin + Jetpack Compose Android app source
.github/workflows/   CI that builds & signs the .aab on GitHub's runners
```

## Order of operations

1. **Deploy the backend first.** Follow `backend-additions/PATCH-NOTES.md`
   exactly — it's written against your real file layout (line numbers,
   existing function names). Test `mobile.php` with curl/Postman before
   touching the app:
   ```
   curl -X POST https://dms.icgroup-india.com/api/mobile.php?r=register \
     -H "Content-Type: application/json" \
     -d '{"name":"Test Agent","mobile":"9876543210","email":"you@example.com","pan":"ABCDE1234F","aadhaar":"123456789012","password":"testpass123"}'
   ```

2. **Update the two base URLs** in `android/ICA-Agents/app/build.gradle.kts`
   (`DMS_BASE_URL`, `EMS_BASE_URL`) if they differ from what's there.

3. **Push this to a GitHub repo**, then add the four signing secrets under
   *Settings → Secrets and variables → Actions* (see the comment block at
   the top of `.github/workflows/build-release.yml` for exactly what's
   needed and how to generate them). If you don't have a release keystore
   yet:
   ```
   keytool -genkey -v -keystore ica-agents-release.jks -keyalg RSA \
     -keysize 2048 -validity 10000 -alias ica-agents
   base64 -w0 ica-agents-release.jks   # paste this as ANDROID_KEYSTORE_BASE64
   ```
   **Keep this keystore file safe outside of git** — if you lose it, you can
   never update the app on Play Store under the same listing again.

4. Push to `main` (or run the workflow manually from the Actions tab). It
   produces `app-release.aab` as a downloadable build artifact — that's the
   file you upload to Play Console.

## What's real vs. what needs your review

**Verified against your actual code** (not assumed): DMS/EMS routing style,
session/cookie names, the `users` table schema, and that EMS's
`agent-invoices`, `agent-invoices/{id}/approve`, `agent-payments`, and
role-scoped `leads` endpoints already exist and work — the app calls them
as-is.

**Written new, not yet tested against your live server**: `mobile.php`,
`kyc-doc.php`, the crypto/token/registration helper files, and the entire
Android app. Please:
- Run the SQL migration and test `mobile.php` on a staging copy first.
- Do a real build via the GitHub Action (or Android Studio) before
  submitting to Play Store — this environment has no Android SDK, so the
  Kotlin has been written carefully but not compiler-verified.
- Review `PATCH-NOTES.md`'s security section, especially the KYC storage
  path and encryption key setup — those two steps are manual on your end
  because they touch your server's filesystem/secrets, which I can't do
  from here.

## Play Store listing checklist (not included here)

You'll still need, directly in Play Console: app category, content rating
questionnaire, privacy policy URL (required — you're collecting PAN/Aadhaar,
so this needs to explicitly cover that), data safety form, and screenshots.
None of that is a code change, so it's not part of this package.

## Mobile testing / APK distribution

See `distribution/README-MOBILE-DISTRIBUTION.md` for Play-Store-free Android testing,
signed release setup, QR/direct-link distribution, and the iOS limitation.
