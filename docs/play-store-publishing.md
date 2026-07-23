# Publishing WeVid to the Play Store

## Status (2026-07-23)

Done via the Play Developer API (service account key at
`~/.config/wevid-play/service-account.json`, token helper
`scripts/play-token.sh`):

- AAB **0.2.0 (versionCode 2)** with report/block is live on **Internal** and
  **Closed testing (alpha)**.
- Store listing (title, descriptions, icon, feature graphic, screenshots) published.
- All three certificate SHA-1s registered in Firebase (debug, upload key,
  Play App Signing key) and `app/google-services.json` refreshed.
- Policies hosted at https://wevid-a43ef.web.app (privacy, account deletion,
  child safety).

**Next:** follow [path-to-production.md](path-to-production.md) — recruit 12
closed testers for 14 days, apply for production access, then:

```bash
./scripts/play-release.sh production
```

Helpers:

```bash
./scripts/play-status.sh      # track status
./scripts/check-reports.sh    # UGC moderation queue
./scripts/play-release.sh internal|alpha|production
```

## What is already prepared

| Item | Location |
| --- | --- |
| Signed release bundle (AAB) | `app/build/outputs/bundle/release/app-release.aab` |
| Play Store app icon (512x512) | `store/play-icon-512.png` |
| Feature graphic (1024x500) | `store/play-feature-graphic-1024x500.png` |
| Release keystore | `~/.android/wevid-release.jks` (alias `wevid-upload`) |
| Keystore credentials | `~/.android/wevid-release-keystore.properties` |
| In-app launcher icon | Updated to the green pin + play adaptive icon |

Rebuild a signed release at any time with:

```bash
./scripts/docker-build.sh bundleRelease
```

**Back up the keystore and the properties file somewhere safe (password manager,
encrypted backup).** If you lose them you cannot update the app with the same
upload key. They must never be committed to version control.

Upload key SHA-1 (already the signature of the AAB):

```
A5:8F:CF:5B:68:47:AE:70:CB:FC:6D:10:B4:A3:D7:73:CB:37:22:0A
```

## Firebase: register the release keys (required for Google Sign-In)

1. Firebase Console -> Project settings -> Your apps -> Android app ->
   **Add fingerprint** -> paste the upload key SHA-1 above.
2. After the first upload to Play, go to Play Console ->
   **Test and release -> Setup -> App signing** and copy the
   **App signing key certificate SHA-1** (Google re-signs your app with this
   key). Add that fingerprint to Firebase too.
3. Re-download `google-services.json` into `app/` and rebuild.

Without step 2, Google Sign-In will fail for builds installed from the Play
Store even though your local release build works.

## Play Console steps

1. **Create the app** at https://play.google.com/console (one-time $25
   registration fee if you don't have a developer account yet).
   App name: WeVid, default language, App/Free.
2. **App signing**: accept Play App Signing (default). Your keystore becomes
   the upload key.
3. **Store listing**:
   - App icon: `store/play-icon-512.png`
   - Feature graphic: `store/play-feature-graphic-1024x500.png`
   - Short description (max 80 chars), e.g. "Drop videos where you are. Watch
     what's happening nearby."
   - Full description: describe the nearby feed, posting clips, and direct
     video messages.
   - Screenshots: at least 2 phone screenshots (16:9 or 9:16, min 320px).
     Take real ones from the app (`adb exec-out screencap -p > shot.png`) —
     the AI-mockup screenshots from the brand board are not suitable because
     the text in them is garbled.
4. **Privacy policy** (required — the app uses location, camera, mic, and
   accounts). Host a page describing what is collected and why, and link it in
   App content -> Privacy policy.
5. **Data safety form** (App content -> Data safety). Declare at minimum:
   - Location (approximate + precise): collected, used for app functionality
     (attaching posts to a place, showing nearby posts).
   - Photos and videos: collected, user-generated content.
   - Personal info: name, email (Google Sign-In), user IDs.
   - Messages: video messages between users.
   - Device IDs: FCM tokens for push notifications.
   - Crash logs / diagnostics: Crashlytics.
6. **Content rating questionnaire**: answer as an app with user-generated
   content and user interaction.
7. **Target audience**: 18+ or 13+ depending on your policy; UGC apps that
   allow minors face stricter review.
8. **Upload the AAB**: start with **Internal testing** (instant, up to 100
   testers by email), then move to Closed/Open testing and Production.

## Before a production release (not yet implemented)

Play's User Generated Content policy requires, for the public feed:

- A way to **report** a post/user.
- A way to **block** a user.
- Terms of service / community guidelines shown in-app.

Internal testing is fine without these, but expect rejection at production
review until they exist.

## Version bumps

For every new upload, increment `versionCode` (and usually `versionName`) in
`app/build.gradle.kts`. Play refuses an AAB with a `versionCode` it has seen
before.
