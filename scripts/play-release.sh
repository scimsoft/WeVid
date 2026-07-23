#!/usr/bin/env bash
# Upload a signed AAB and/or assign an existing version code to a Play track.
#
# Usage:
#   ./scripts/play-release.sh              # upload + internal
#   ./scripts/play-release.sh internal
#   ./scripts/play-release.sh alpha        # closed testing
#   ./scripts/play-release.sh production   # requires production access
#
# Optional env:
#   AAB=path/to.aab
#   RELEASE_NAME="0.2.0 (2)"
#   VERSION_CODE=2
#   STATUS=completed|draft
#   FORCE_DRAFT=1          # force draft status on alpha
#   PROMOTE_ONLY=1         # skip upload; assign existing VERSION_CODE to track
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TRACK="${1:-internal}"
AAB="${AAB:-$ROOT/app/build/outputs/bundle/release/app-release.aab}"
PKG=com.scimsoft.wevid
PROMOTE_ONLY="${PROMOTE_ONLY:-0}"

# Read version from Gradle if not provided.
if [[ -z "${VERSION_CODE:-}" ]]; then
  VERSION_CODE=$(python3 - <<'PY'
import re
text = open("app/build.gradle.kts").read()
m = re.search(r"versionCode\s*=\s*(\d+)", text)
print(m.group(1) if m else "")
PY
)
fi
if [[ -z "${RELEASE_NAME:-}" ]]; then
  VERSION_NAME=$(python3 - <<'PY'
import re
text = open("app/build.gradle.kts").read()
m = re.search(r'versionName\s*=\s*"([^"]+)"', text)
print(m.group(1) if m else "release")
PY
)
  RELEASE_NAME="${VERSION_NAME} (${VERSION_CODE})"
fi

STATUS="${STATUS:-completed}"
if [[ "$TRACK" == "alpha" && "${FORCE_DRAFT:-}" == "1" ]]; then
  STATUS=draft
fi

TOKEN="$("$ROOT/scripts/play-token.sh")"
API="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG"
UPLOAD_API="https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$PKG"

echo "==> Creating edit"
EDIT=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" "$API/edits" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
echo "    edit=$EDIT"

abort_edit() {
  curl -s -X DELETE -H "Authorization: Bearer $TOKEN" "$API/edits/$EDIT" >/dev/null || true
}

if [[ "$PROMOTE_ONLY" != "1" ]]; then
  if [[ ! -f "$AAB" ]]; then
    echo "Missing AAB at $AAB"
    echo "Build first: ./scripts/docker-build.sh bundleRelease"
    echo "Or promote an existing upload: PROMOTE_ONLY=1 VERSION_CODE=$VERSION_CODE ./scripts/play-release.sh $TRACK"
    abort_edit
    exit 1
  fi

  echo "==> Uploading $AAB"
  UPLOAD=$(curl -s -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/octet-stream" \
    --data-binary @"$AAB" \
    "$UPLOAD_API/edits/$EDIT/bundles?uploadType=media")
  echo "$UPLOAD" | python3 -c "import json,sys; d=json.load(sys.stdin); print('    ', d.get('versionCode', d))"

  if echo "$UPLOAD" | python3 -c "import json,sys; d=json.load(sys.stdin); raise SystemExit(0 if 'versionCode' in d else 1)"; then
    VERSION_CODE=$(echo "$UPLOAD" | python3 -c "import json,sys; print(json.load(sys.stdin)['versionCode'])")
    RELEASE_NAME="${RELEASE_NAME%% (*} (${VERSION_CODE})"
  else
    # Same versionCode already in Play library — promote that instead of failing.
    if echo "$UPLOAD" | python3 -c "import json,sys; d=json.load(sys.stdin); raise SystemExit(0 if 'already been used' in str(d) else 1)"; then
      echo "    Version code $VERSION_CODE already in library — promoting existing bundle"
    else
      echo "ERROR: bundle upload failed"
      abort_edit
      exit 1
    fi
  fi
else
  echo "==> PROMOTE_ONLY: using existing versionCode $VERSION_CODE"
fi

echo "==> Assigning $RELEASE_NAME (vc $VERSION_CODE) → track=$TRACK status=$STATUS"
ASSIGN=$(curl -s -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"track\":\"$TRACK\",\"releases\":[{\"name\":\"$RELEASE_NAME\",\"versionCodes\":[\"$VERSION_CODE\"],\"status\":\"$STATUS\"}]}" \
  "$API/edits/$EDIT/tracks/$TRACK")
echo "$ASSIGN" | python3 -c "import json,sys; d=json.load(sys.stdin); print(json.dumps(d, indent=2)[:800])"
if ! echo "$ASSIGN" | python3 -c "import json,sys; d=json.load(sys.stdin); raise SystemExit(0 if 'track' in d else 1)"; then
  echo "ERROR: track assign failed"
  echo "If this is production: wait until Dashboard → Apply for production access is approved,"
  echo "then re-run: PROMOTE_ONLY=1 ./scripts/play-release.sh production"
  abort_edit
  exit 1
fi

echo "==> Committing edit"
COMMIT=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" "$API/edits/$EDIT:commit")
echo "$COMMIT" | python3 -c "import json,sys; d=json.load(sys.stdin); print(json.dumps(d, indent=2)[:600])"
if echo "$COMMIT" | python3 -c "import json,sys; d=json.load(sys.stdin); raise SystemExit(1 if 'error' in d else 0)"; then
  echo "==> Done. Track $TRACK now has $RELEASE_NAME"
else
  echo "ERROR: commit failed (often a Console declaration is incomplete, or production access is not granted yet)"
  exit 1
fi
