#!/usr/bin/env bash
set -euo pipefail

cd /project

# Point AGP at the SDK inside the image (host local.properties is often wrong in Docker).
echo "sdk.dir=${ANDROID_HOME}" > local.properties

if [[ ! -f app/google-services.json ]]; then
  echo "No app/google-services.json found — using example stub for build."
  cp app/google-services.json.example app/google-services.json
fi

if [[ ! -f gradlew ]]; then
  echo "ERROR: gradlew not found."
  echo "Generate it with: docker compose run --rm wrapper"
  exit 1
fi

chmod +x gradlew

TASK="${1:-assembleDebug}"
shift || true

GRADLE_ARGS=()
if [[ -n "${GOOGLE_WEB_CLIENT_ID:-}" ]]; then
  GRADLE_ARGS+=("-PGOOGLE_WEB_CLIENT_ID=${GOOGLE_WEB_CLIENT_ID}")
fi

echo "Running ./gradlew ${TASK} ${GRADLE_ARGS[*]:-} $*"
exec ./gradlew "${TASK}" "${GRADLE_ARGS[@]}" --no-daemon "$@"
