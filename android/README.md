# 蝦拼輸入法 — Android 版

從零寫的 Android 輸入法 App，自己 NDK 編譯 [librime](https://github.com/rime/librime)，不依賴 Trime APK。

屬於 monorepo：[Jakevin/xiapin-input-method](https://github.com/Jakevin/xiapin-input-method) 的 `android/` 目錄。

## 功能

- 拼音組詞 + 嘸蝦米字根（table_translator，字根 quality 高於拼音）
- 單碼／前綴字根：App 層 `extraRoot` 補候選，空白／點選上屏正確字
- 英文：`xiapin_english` schema 切換（左下「中／英」）
- 關聯字（essay）+ 個人使用頻率
- 候選區固定高度、無候選時數字列、Shift 三態（僅英文）
- **翻譯模式**：原文 `EditText`、目標 英／日／简、點譯文上屏，或 **送原文** 直接送中文

## 架構

```
android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/xiapin/ime/
│       │   ├── XiapinIME.java           # 主服務
│       │   ├── RimeJNI.java
│       │   ├── XiapinKeyboardView.java
│       │   ├── CandidateView.java
│       │   ├── AssetDeployer.java
│       │   ├── BoshiamyComment.java
│       │   ├── AssociationDict.java
│       │   ├── CharUsageFreq.java
│       │   ├── TranslateHelper.java
│       │   └── AssocEditorActivity.java
│       ├── cpp/
│       │   ├── rime_jni.cc
│       │   └── CMakeLists.txt
│       ├── assets/rime/                 # schema + 字典
│       └── res/
```

## 與桌面版的差異

- **字根提示在 App 層**：Android librime 未編 Lua 時，由 `BoshiamyComment` 讀表
- **schema 可去 Lua filter**
- **翻譯／關聯／頻率** 等 UX 為 Android 版加值

## 前置依賴

- Android SDK（platforms 34、build-tools、cmake、NDK）
- Gradle 7.5+（或 wrapper）
- **預先編好的 librime 靜態庫**（見下）

## 步驟 1：編譯 librime（NDK，靜態）

```bash
# 自建 android-rime-build 工作區（範例）
git clone --depth 1 --recurse-submodules https://github.com/rime/librime.git
# 依 NDK 交叉編譯 arm64-v8a / armeabi-v7a，產出 out/<abi>/*.a
export RIME_BUILD_DIR=/path/to/android-rime-build
```

也可參考 [osfans/trime](https://github.com/osfans/trime) 的 NDK 配方。

## 步驟 2：組建 App

```bash
export ANDROID_HOME=~/Library/Android/sdk
export RIME_BUILD_DIR=/path/to/android-rime-build

cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties   # 勿提交
gradle assembleDebug
# 產出 app/build/outputs/apk/debug/app-debug.apk
```

`app/src/main/cpp/CMakeLists.txt` 會連結 `RIME_BUILD_DIR` 下各 ABI 的 `.a` 成 `libxiapin_rime.so`。

## 安裝

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

系統設定 → 語言與輸入 → 啟用「蝦拼」→ 切換輸入法。

- 字母：拼音／字根  
- 左下「中／英」：schema 切換  
- 頂列「譯」：翻譯面板（**送原文** 在原文框右側；點譯文才送譯文）

## 授權

- App 與本目錄程式碼：與倉庫根目錄相同 **MIT**（見 `/LICENSE`）
- 字典與 librime：見根目錄 `/NOTICE`

## 注意

翻譯功能使用非官方 HTTP 翻譯端點，僅供實驗；正式產品請改用你自己申請的官方 API。
