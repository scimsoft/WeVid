#!/usr/bin/env bash
# Prints an OAuth2 access token for the Play Developer API, using the service
# account key at ~/.config/wevid-play/service-account.json (no SDKs required).
set -euo pipefail

KEY_JSON="${PLAY_SA_KEY:-$HOME/.config/wevid-play/service-account.json}"

CLIENT_EMAIL=$(python3 -c "import json,sys; print(json.load(open('$KEY_JSON'))['client_email'])")
PEM=$(mktemp)
trap 'rm -f "$PEM"' EXIT
python3 -c "import json; print(json.load(open('$KEY_JSON'))['private_key'], end='')" > "$PEM"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

NOW=$(date +%s)
HEADER=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
CLAIMS=$(printf '{"iss":"%s","scope":"https://www.googleapis.com/auth/androidpublisher","aud":"https://oauth2.googleapis.com/token","iat":%d,"exp":%d}' \
  "$CLIENT_EMAIL" "$NOW" $((NOW + 3600)) | b64url)
SIG=$(printf '%s.%s' "$HEADER" "$CLAIMS" | openssl dgst -sha256 -sign "$PEM" -binary | b64url)
JWT="$HEADER.$CLAIMS.$SIG"

curl -s -X POST https://oauth2.googleapis.com/token \
  -d "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$JWT" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('access_token') or sys.exit('token error: '+json.dumps(d)))"
