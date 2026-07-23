# WeVid

Location-based short video clips for Android — drop clips where you are, see what's nearby, and still send private clips to people.

**Stack:** Kotlin · Jetpack Compose · Firebase · Docker builds

## Status

- **Auth** — Google Sign-In → Firebase Auth, username claim
- **Feed (main)** — Nearby + recent location-dropped videos (geohash query, ~10 km)
- **Messaging** — 1:1 video chats by @username, realtime list, Media3 playback
- **Capture** — CameraX ≤30s clips, WorkManager upload queue with retry
- **Push** — FCM via Cloud Function in `functions/`, Crashlytics
- **Moderation** — In-app report/block; Firestore `reports` queue
- **Play Store** — 0.2.0 on internal + closed testing. Production path:
  [docs/path-to-production.md](docs/path-to-production.md)

Requires Firestore + Storage enabled and the rules from
[docs/firebase-rules.md](docs/firebase-rules.md).

## Prerequisites

- Docker (Docker Desktop on WSL)
- A Firebase project (for real sign-in / backend)

## Build with Docker

First image build downloads the Android SDK (several minutes).

```bash
chmod +x scripts/docker-build.sh
./scripts/docker-build.sh

# APK:
# app/build/outputs/apk/debug/app-debug.apk
```

Manual equivalent:

```bash
DOCKER_BUILDKIT=0 docker build -t wevid-build .
docker run --rm \
  -v "$PWD":/project \
  -v wevid-gradle:/root/.gradle \
  -e GRADLE_USER_HOME=/root/.gradle \
  wevid-build assembleDebug
```

Optional Google Web client ID:

```bash
GOOGLE_WEB_CLIENT_ID=your-id.apps.googleusercontent.com ./scripts/docker-build.sh
```

## Firebase setup

1. Create a Firebase project and add an Android app with package `com.scimsoft.wevid`.
2. Enable **Google** sign-in under Authentication.
3. Download `google-services.json` into `app/google-services.json` (gitignored).
4. Copy the **Web client** OAuth client ID → `GOOGLE_WEB_CLIENT_ID` when building.
5. Add your debug keystore SHA-1 in Firebase (see [docs/firebase-setup.md](docs/firebase-setup.md)).
6. Enable Firestore, Storage, and Cloud Messaging in Phases 2–4.

Without a real `google-services.json`, the Docker entrypoint copies `app/google-services.json.example` so compilation still works. Sign-in needs a real config + web client ID.

## Install on a device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

```
app/                 Android application
docker/              Build entrypoint
docs/                Setup notes
scripts/             docker-build.sh helper
Dockerfile           Android SDK build image
docker-compose.yml   optional compose services
```

## Data model

```
users/{uid}          displayName, username, photoUrl, createdAt, fcmTokens[]
usernames/{username} uid                    (reservation for atomic claims)
posts/{postId}       authorId, authorInfo{}, videoUrl, thumbUrl, durationMs,
                     createdAt, lat, lng, geohash
chats/{chatId}       members[], memberInfo{}, lastMessageAt, lastSenderId,
                     lastThumbUrl, lastMessageSeenBy[]
  messages/{id}      senderId, videoUrl, thumbUrl, durationMs, createdAt, seenBy[]
```

The main screen is a **nearby feed** of `posts` within ~10 km (geohash range
queries). Private 1:1 messaging remains under Chats.

`chatId` is `min(uidA,uidB)_max(uidA,uidB)` so both members resolve the same doc.
Chat videos live under `videos/{chatId}/{messageId}.mp4`; feed clips under
`posts/{postId}.mp4` (+ `.jpg` thumbs).

## Push notifications (Phase 4)

The Cloud Function in `functions/` sends a push to the recipient whenever a
message document is created. Deploying it requires the **Blaze** plan and the
Firebase CLI:

```bash
npm install -g firebase-tools
firebase login
cd functions && npm install && cd ..
firebase deploy --only functions --project wevid-a43ef
```

Device tokens are stored on `users/{uid}.fcmTokens` (registered on app start,
removed on sign-out). Invalid tokens are pruned automatically after a failed
send. Clips are uploaded via a WorkManager queue: they survive app restarts and
retry with backoff until the network is back.

## Next (Phase 5)

Play Store release: release signing, privacy policy, data safety form,
store listing, report/block for UGC compliance.
