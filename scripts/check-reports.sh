#!/usr/bin/env bash
# List recent UGC reports from Firestore (operator moderation queue).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
PROJECT=wevid-a43ef

echo "==> Recent reports in $PROJECT (newest first, up to 25)"

python3 - "$PROJECT" <<'PY'
import json, os, sys, urllib.request, urllib.parse

project = sys.argv[1]
cfg = json.load(open(os.path.expanduser("~/.config/configstore/firebase-tools.json")))
rt = cfg["tokens"]["refresh_token"]
token_body = urllib.parse.urlencode({
    "grant_type": "refresh_token",
    "refresh_token": rt,
    "client_id": "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com",
    "client_secret": "j9iVZfS8kkCEFUPaAeJV0sAi",
}).encode()
token = json.load(urllib.request.urlopen("https://oauth2.googleapis.com/token", token_body))["access_token"]

query = {
    "structuredQuery": {
        "from": [{"collectionId": "reports"}],
        "orderBy": [{"field": {"fieldPath": "createdAt"}, "direction": "DESCENDING"}],
        "limit": 25,
    }
}
req = urllib.request.Request(
    f"https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents:runQuery",
    data=json.dumps(query).encode(),
    headers={
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    },
    method="POST",
)
rows = json.load(urllib.request.urlopen(req))

def field(fields, key):
    f = fields.get(key, {})
    return f.get("stringValue") or f.get("integerValue") or f.get("timestampValue") or ""

docs = [r for r in rows if "document" in r]
if not docs:
    if rows and "error" in rows[0]:
        print("ERROR:", rows[0]["error"])
        sys.exit(1)
    print("(no reports yet — good)")
else:
    print(f"{'when':20} {'type':8} {'reason':16} reporter / target / post")
    print("-" * 72)
    for row in docs:
        fields = row["document"].get("fields", {})
        when = field(fields, "createdAt")[:19].replace("T", " ")
        typ = field(fields, "type")
        reason = field(fields, "reason")
        reporter = field(fields, "reporterId")[:8]
        target = field(fields, "targetUserId")[:8]
        post = field(fields, "postId")[:12]
        print(f"{when:20} {typ:8} {reason:16} {reporter}… → {target}…  post={post}")

print()
print("Review in console:")
print(f"  https://console.firebase.google.com/project/{project}/firestore/data/~2Freports")
print("Crashlytics:")
print(f"  https://console.firebase.google.com/project/{project}/crashlytics")
print("Act on child_safety reports immediately.")
PY
