# Path to Production

Operational checklist for taking WeVid from closed testing to a public
Play Store release. Companion to [play-store-publishing.md](play-store-publishing.md).

## Current status (API-verified)

| Track | Release | Status |
| --- | --- | --- |
| Internal testing | 0.2.0 (2) | **completed** (live) |
| Closed testing (alpha) | 0.2.0 (2) | **completed** (live) |
| Production | — | not started (needs production access) |

Re-check anytime:

```bash
./scripts/play-status.sh
```

## 1. Closed testing opt-in link

The Play API does not expose the tester join URL. Get it from the Console:

1. [Play Console → WeVid](https://play.google.com/console)
2. **Test and release → Closed testing** (Alpha track)
3. **Testers** tab
4. Under **How testers join your test → Join on the web**, copy the link
   (`https://play.google.com/apps/internaltest/...` or similar)

Share that link — not an APK. Testers must opt in, then install from Play on a
**real phone**.

## 2. Recruit 12+ testers (14-day clock)

Google's rule for new personal developer accounts:

- ≥ **12** unique Google accounts opted into **closed** testing
- Continuously for the last **14 days** when you apply
- Real devices (emulators / same-device multi-accounts get flagged)

You already have ~8 people across "android friends" + "familie". Need **4+ more**.

### Invite template

```
Subject: Help test WeVid for 2 weeks?

Hi — I'm launching WeVid (nearby video clips + video messages) on Google Play
and need closed testers for 14 days.

1. Open this link on your Android phone (signed into your Google account):
   <PASTE CLOSED TESTING OPT-IN LINK>
2. Accept the invite
3. Install WeVid from the Play Store page that opens
4. Sign in with Google, claim a username, allow location, drop a short clip
   or open Nearby a couple of times this week

Please stay opted in for the full 2 weeks — if people leave, the clock resets.
Thanks!
```

### Console checklist

- [ ] Closed track **Countries/regions** includes where testers live
- [ ] Email lists contain ≥12 addresses and are ticked on the Testers tab
- [ ] Each person has **Accepted** (not just been emailed)
- [ ] Dashboard closed-test progress shows 12/12 opted in

Day 0 of the 14-day window = the day the **12th** tester opts in.

## 3. Monitor during the 14 days

### UGC reports (required by child-safety declaration)

```bash
./scripts/check-reports.sh
```

Or browse:
https://console.firebase.google.com/project/wevid-a43ef/firestore/data/~2Freports

Act immediately on `child_safety` reasons: remove content, ban the account
(delete/block in Firestore), report CSAM to authorities if confirmed.

### Crashlytics

https://console.firebase.google.com/project/wevid-a43ef/crashlytics

Ship fixes without resetting the clock:

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`
2. `./scripts/docker-build.sh bundleRelease`
3. `./scripts/play-release.sh alpha` (or `internal` for quick smoke tests)

## 4. Apply for production access (day 15+)

When Dashboard shows **Apply for production access**:

Answer specifically (thin answers are the main rejection reason). Draft:

| Topic | Suggested answer |
| --- | --- |
| What is the app? | Location-based video app: drop short clips pinned to your place; nearby feed; 1:1 video messages via Google Sign-In. |
| Who tested? | Closed testers: friends/family + invited Google accounts (≥12), real Android phones, opted in via Play closed-testing link for 14+ continuous days. |
| How did they test? | Sign-in, username claim, location permission, nearby feed, drop clip, send video message, report/block menus. |
| Feedback received | Summarize real notes (crashes fixed, UI issues, empty feed in sparse areas, etc.). |
| Changes from feedback | List version bumps (e.g. 0.2.0 added report/block for UGC policy). |
| Production ready? | Policies hosted, Data safety / child safety / ads declarations complete, Crashlytics on, report+block in-app, moderation via Firestore `reports`. |

Review typically takes 2–7 days.

## 5. Promote to production

After Google grants access:

1. In Console: select **Production** countries/regions if prompted.
2. Promote the already-uploaded 0.2.0 (or a newer build):

```bash
# Promote existing version code already in Play's library:
PROMOTE_ONLY=1 ./scripts/play-release.sh production

# Or ship a new build:
# bump versionCode in app/build.gradle.kts
./scripts/docker-build.sh bundleRelease
./scripts/play-release.sh production
```

3. First production release gets a full review (up to ~7 days). Optional staged
   rollout can be set in the Console after the release is created.

If the API returns `FAILED_PRECONDITION`, production access is not granted yet —
finish the Dashboard application and retry the same command.

## After production — shipping new features

Not hard; the pipeline is already scripted:

```bash
# 1. implement feature, bump versionCode in app/build.gradle.kts
./scripts/docker-build.sh bundleRelease
./scripts/play-release.sh internal      # smoke with testers (minutes, no review)
./scripts/play-release.sh production    # public update (usually hours)
```

Extra Console work only if you change collected data (Data safety), add
sensitive permissions, or change monetization.
