#!/usr/bin/env bash
#
# Downloads a fresh statically-linked sqlite3 ARM/aarch64 binary and replaces
# the copy bundled in the app's res/raw directory. The bundled binary is copied
# to app data and executed at runtime (see SplashActivity.copyAssets()).
#
# Intended to be run by CI at build time, but is safe to run locally too.
set -euo pipefail

SQLITE3_URL="https://raw.githubusercontent.com/rojenzaman/sqlite3-arm-aarch64/main/sqlite3"

# Resolve repo root relative to this script so it works from any cwd.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEST="${REPO_ROOT}/app/src/main/res/raw/sqlite3"

TMP_FILE="$(mktemp)"
trap 'rm -f "${TMP_FILE}"' EXIT

echo "Downloading sqlite3 binary from ${SQLITE3_URL} ..."
curl -fSL --retry 3 --retry-delay 2 -o "${TMP_FILE}" "${SQLITE3_URL}"

# Verify the download is non-empty.
if [ ! -s "${TMP_FILE}" ]; then
    echo "ERROR: downloaded sqlite3 binary is empty." >&2
    exit 1
fi

# Guard against an HTML error page being saved instead of the binary.
FIRST_BYTES="$(head -c 16 "${TMP_FILE}" | tr -d '\0')"
case "${FIRST_BYTES}" in
    *"<!DOCTYPE"* | *"<html"* | *"<HTML"* | *"Not Found"* | *"404"*)
        echo "ERROR: downloaded content looks like an HTML/error page, not a binary." >&2
        exit 1
        ;;
esac

DEST_DIR="$(dirname "${DEST}")"
mkdir -p "${DEST_DIR}"
mv "${TMP_FILE}" "${DEST}"
trap - EXIT

SIZE="$(wc -c < "${DEST}")"
echo "Updated ${DEST} (${SIZE} bytes)."
