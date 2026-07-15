#!/usr/bin/env bash
# 發佈 GitHub Release：macOS Rime zip + Windows Rime zip + Android APK
# 用法：bash tools/publish_release.sh v0.1.12
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TAG="${1:-}"
if [[ -z "$TAG" || ! "$TAG" =~ ^v[0-9] ]]; then
  echo "用法: bash tools/publish_release.sh v0.1.12"
  exit 1
fi

REPO="${GITHUB_REPOSITORY:-Jakevin/xiapin-input-method}"
RIME_ZIP="xiapin-rime-${TAG}.zip"
WINDOWS_ZIP="xiapin-windows-${TAG}.zip"
APK_OUT="xiapin-android-${TAG}.apk"
APK_SRC_DEFAULT="android/app/build/outputs/apk/debug/app-debug.apk"
APK_SRC="${APK_SRC:-$APK_SRC_DEFAULT}"
if [[ ! -f "$APK_SRC" && -f "xiapin-debug-latest.apk" ]]; then
  APK_SRC="xiapin-debug-latest.apk"
fi

echo "==> Tag: $TAG"
echo "==> Repo: $REPO"

# --- Package Rime zip ---
PKG="xiapin-rime-${TAG}"
rm -rf "dist/${PKG}" "dist/${RIME_ZIP}"
mkdir -p "dist/${PKG}"
cp README.md RELEASE.md install.sh LICENSE NOTICE "dist/${PKG}/" 2>/dev/null || true
cp README.md RELEASE.md install.sh "dist/${PKG}/"
[[ -f LICENSE ]] && cp LICENSE "dist/${PKG}/"
[[ -f NOTICE ]] && cp NOTICE "dist/${PKG}/"
cp -R rime "dist/${PKG}/"
chmod +x "dist/${PKG}/install.sh"
(cd dist && zip -r "${RIME_ZIP}" "${PKG}" >/dev/null)
echo "==> Built dist/${RIME_ZIP}"

# --- Package Windows zip ---
WIN_PKG="xiapin-windows-${TAG}"
rm -rf "dist/${WIN_PKG}" "dist/${WINDOWS_ZIP}"
mkdir -p "dist/${WIN_PKG}/docs"
cp README.md RELEASE.md install-windows.ps1 install-windows.cmd "dist/${WIN_PKG}/"
[[ -f LICENSE ]] && cp LICENSE "dist/${WIN_PKG}/"
[[ -f NOTICE ]] && cp NOTICE "dist/${WIN_PKG}/"
cp docs/WINDOWS.md "dist/${WIN_PKG}/docs/"
cp -R rime "dist/${WIN_PKG}/"
(cd dist && zip -r "${WINDOWS_ZIP}" "${WIN_PKG}" >/dev/null)
echo "==> Built dist/${WINDOWS_ZIP}"

# --- APK ---
if [[ ! -f "$APK_SRC" ]]; then
  echo "找不到 APK: $APK_SRC"
  echo "請先: cd android && gradle assembleDebug"
  echo "或: APK_SRC=/path/to.apk bash tools/publish_release.sh $TAG"
  exit 1
fi
cp -f "$APK_SRC" "$APK_OUT"
echo "==> APK: $APK_OUT ($(du -h "$APK_OUT" | awk '{print $1}'))"

# --- Tag (optional if already exists) ---
if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "==> Tag $TAG 已存在，略過建立"
else
  git tag -a "$TAG" -m "Release $TAG"
  echo "==> Created tag $TAG"
  if git remote get-url origin >/dev/null 2>&1; then
    git push origin "$TAG"
    echo "==> Pushed tag"
  fi
fi

# --- GitHub Release ---
if ! command -v gh >/dev/null; then
  echo "需要 gh CLI。已備好檔案："
  echo "  dist/${RIME_ZIP}"
  echo "  dist/${WINDOWS_ZIP}"
  echo "  ${APK_OUT}"
  exit 0
fi

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  echo "==> Release 已存在，上傳／覆蓋 assets"
  gh release upload "$TAG" \
    "dist/${RIME_ZIP}" \
    "dist/${WINDOWS_ZIP}" \
    "$APK_OUT" \
    --repo "$REPO" \
    --clobber
else
  echo "==> 建立 Release $TAG"
  NOTES="RELEASE.md"
  [[ -f "$NOTES" ]] || NOTES="/dev/null"
  gh release create "$TAG" \
    "dist/${RIME_ZIP}" \
    "dist/${WINDOWS_ZIP}" \
    "$APK_OUT" \
    --repo "$REPO" \
    --title "蝦拼輸入法 ${TAG}" \
    --notes-file "$NOTES"
fi

echo ""
echo "完成: https://github.com/${REPO}/releases/tag/${TAG}"
echo "  - ${RIME_ZIP}"
echo "  - ${WINDOWS_ZIP}"
echo "  - ${APK_OUT}"
