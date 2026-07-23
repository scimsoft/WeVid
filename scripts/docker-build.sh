#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

IMAGE="${WEVID_IMAGE:-wevid-build}"
TASK="${1:-assembleDebug}"

echo "==> Building image ${IMAGE}"
DOCKER_BUILDKIT=0 docker build -t "${IMAGE}" .

echo "==> Running ./gradlew ${TASK}"
# Reuse the host debug keystore so the APK signature (SHA-1) stays stable across builds.
mkdir -p "${HOME}/.android"

docker run --rm \
  -v "${ROOT}:/project" \
  -v wevid-gradle:/root/.gradle \
  -v "${HOME}/.android:/root/.android" \
  -e GRADLE_USER_HOME=/root/.gradle \
  -e "GOOGLE_WEB_CLIENT_ID=${GOOGLE_WEB_CLIENT_ID:-}" \
  "${IMAGE}" \
  "${TASK}"

echo "==> Done. Debug APK (if built): app/build/outputs/apk/debug/app-debug.apk"
