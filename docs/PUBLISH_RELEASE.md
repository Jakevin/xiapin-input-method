# 如何發佈 Release（含 Android APK）

GitHub Actions 在 **打 tag `v*`** 時會自動打包 **macOS Rime zip** 與 **Windows Rime zip** 並建立 Release。
**Android APK** 因需要本機預編的 librime（`RIME_BUILD_DIR`），目前採 **本機組建後上傳**。

## 方式 A：本機一鍵（推薦）

```bash
cd /path/to/xiapin-input-method

# 1) 已組好 APK（或先 build）
export ANDROID_HOME=~/Library/Android/sdk
export RIME_BUILD_DIR=/path/to/android-rime-build
cd android && gradle assembleDebug && cd ..

# 2) 發佈（會建 tag + rime zip + 上傳 apk）
bash tools/publish_release.sh v0.1.12
```

腳本會：

1. 確認工作目錄乾淨（或提示你先 commit）
2. 打包 `xiapin-rime-<tag>.zip` 與 `xiapin-windows-<tag>.zip`
3. 複製 `xiapin-android-<tag>.apk`
4. `git tag` + `git push --tags`（觸發 CI 亦可）
5. `gh release create` 並附上 zip + apk

若 CI 已因 tag 建過 release，腳本會改為 **upload 補上傳 APK**。

## 方式 B：只補上傳 APK 到現有 Release

```bash
# 組 APK
cd android && gradle assembleDebug && cd ..

cp android/app/build/outputs/apk/debug/app-debug.apk \
   xiapin-android-v0.1.11.apk

# 上傳到已存在的 tag
gh release upload v0.1.11 xiapin-android-v0.1.11.apk \
  --repo Jakevin/xiapin-input-method \
  --clobber
```

## 方式 C：手動（網頁）

1. 本機組好 APK，重新命名為 `xiapin-android-vX.Y.Z.apk`
2. GitHub → Releases → 目標版本 → Edit  
3. 拖曳 APK 到 Assets → Update release

## 使用者下載位置

```text
https://github.com/Jakevin/xiapin-input-method/releases/latest
```

Assets 應包含：

- `xiapin-rime-vX.Y.Z.zip` — macOS
- `xiapin-windows-vX.Y.Z.zip` — Windows 小狼毫
- `xiapin-android-vX.Y.Z.apk` — Android

## 注意

- 勿把 `local.properties`、keystore、簽名密碼 commit 進 git
- Debug APK 僅供測試；上架 Play 需 release 簽名與隱私政策
- 翻譯功能為實驗性網路 API，見 `NOTICE`
