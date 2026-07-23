#!/usr/bin/env bash
# Show release status for all Play tracks.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TOKEN="$("$ROOT/scripts/play-token.sh")"
PKG=com.scimsoft.wevid
BASE="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG"

EDIT=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" "$BASE/edits" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")

cleanup() {
  curl -s -X DELETE -H "Authorization: Bearer $TOKEN" "$BASE/edits/$EDIT" >/dev/null || true
}
trap cleanup EXIT

python3 - "$TOKEN" "$BASE" "$EDIT" <<'PY'
import json, os, sys, urllib.request

token, base, edit = sys.argv[1:4]

def get(path):
    req = urllib.request.Request(
        f"{base}/edits/{edit}/{path}",
        headers={"Authorization": f"Bearer {token}"},
    )
    with urllib.request.urlopen(req) as r:
        return json.load(r)

print("WeVid Play track status")
print("=" * 40)
for track in ("internal", "alpha", "beta", "production"):
    try:
        data = get(f"tracks/{track}")
    except Exception as e:
        print(f"{track:12} (unavailable: {e})")
        continue
    releases = data.get("releases") or []
    if not releases:
        print(f"{track:12} no release")
        continue
    for rel in releases:
        codes = ",".join(rel.get("versionCodes") or [])
        print(f"{track:12} {rel.get('name','?')}  vc={codes}  status={rel.get('status')}")

print()
print("Closed testing (alpha) is the gate for production access.")
print("Opt-in link (Console only):")
print("  Test and release → Closed testing → Testers → Join on the web")
print()
print("Production unlock checklist:")
print("  [ ] ≥12 testers opted into closed testing")
print("  [ ] Those 12 stayed opted in for 14 continuous days")
print("  [ ] Dashboard → Apply for production access (questionnaire)")
print("  [ ] Then: ./scripts/play-release.sh production")
PY
