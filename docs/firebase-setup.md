# Firebase setup (WeVid)

Package name: `com.scimsoft.wevid`

## Checklist

1. [ ] Create Firebase project
2. [ ] Add Android app with SHA-1 of your debug keystore (required for Google Sign-In)
3. [ ] Download `google-services.json` → `app/google-services.json`
4. [ ] Authentication → Sign-in method → enable **Google**
5. [ ] Copy **Web client ID** (OAuth 2.0 client of type Web) → `GOOGLE_WEB_CLIENT_ID`
6. [ ] (Phase 2+) Create Firestore database
7. [ ] (Phase 3+) Enable Storage
8. [ ] Apply the rules in [firebase-rules.md](firebase-rules.md) (includes `posts` for the nearby feed)
9. [ ] Upgrade to the Blaze plan (required for Cloud Functions)
10. [ ] Deploy the notification function: `firebase deploy --only functions`
11. [ ] Optional: enable Crashlytics in the console to see crash reports
12. [ ] Grant location permission on device to use the nearby feed

## Debug SHA-1 (inside Docker after a build)

```bash
docker compose run --rm build signingReport
```

Use the `Variant: debug` SHA-1 in Firebase Console → Project settings → Your apps.
