# 蝦拼輸入法 v0.1.11

macOS（Rime）+ Android 雙平台 release。

## 下載

到本頁 **Assets** 下載：

| 檔案 | 平台 | 說明 |
|------|------|------|
| `xiapin-rime-v0.1.11.zip` | macOS 鼠鬚管 | 解壓後執行 `bash install.sh` |
| `xiapin-android-v0.1.11.apk` | Android | 直接安裝（需允許未知來源） |

Release 頁面：

```text
https://github.com/Jakevin/xiapin-input-method/releases
```

## Android 安裝

1. 下載 `xiapin-android-v0.1.11.apk`
2. 傳到手機後點開安裝（設定 → 允許此來源安裝）
3. **設定 → 系統 → 語言與輸入 → 螢幕鍵盤 → 管理鍵盤** → 啟用「蝦拼」
4. 在輸入框切換成「蝦拼」

### 功能摘要（Android）

- 拼音 + 嘸蝦米字根（字根優先）
- 英文方案切換（左下「中／英」）
- 關聯字、使用頻率
- 翻譯模式：原文 EditText；**送原文** / 點譯文上屏
- 英文 Shift 三態（全小寫 / 首字大寫 ⇪ / 全大寫藍底）

> 此 APK 為 debug 簽名，供測試；正式上架需自行 release 簽名。

## macOS 安裝

### 1. 安裝鼠鬚管

```bash
brew install --cask squirrel
```

系統設定 → 鍵盤 → 文字輸入 → 加入鼠鬚管。

### 2. 安裝蝦拼

```bash
unzip xiapin-rime-v0.1.11.zip
cd xiapin-rime-v0.1.11
bash install.sh
```

鼠鬚管選單 → 重新部署。

## 本版變更

### Android（首發進 Release）

- 開源完整 Android IME 原始碼（`android/`）
- 翻譯 UI：原文 EditText、送原文按鈕（框右側）、譯文點選上屏
- 空白鍵修正：避免雙重上屏候選
- 游標位置插入原文（不強制跳到最後）
- 關閉翻譯時 flush 原文，避免文字消失

### 桌面 Rime

- 延續 v0.1.10 功能與碼表

## 授權

- 程式碼：MIT
- 字典與第三方：見倉庫 `NOTICE`
