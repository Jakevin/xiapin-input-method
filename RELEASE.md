# 蝦拼輸入法 v0.1.16

## 下載

| 檔案 | 平台 |
|------|------|
| `xiapin-rime-v0.1.16.zip` | macOS |
| `xiapin-windows-v0.1.16.zip` | Windows |
| `xiapin-android-v0.1.16.apk` | Android |

https://github.com/Jakevin/xiapin-input-method/releases/latest

## 本版變更

### Android

- **候選亂碼過濾**：中文候選只顯示常用漢字 BMP（`U+4E00–U+9FFF`），過濾 ExtA/ExtB／方框／無用符號候補
- **部署重編**：assets 變動時回傳 redeployed，並清除 `rime_user/build`，避免殘留損壞的 prism／table 快取
- 部署 checksum 加 `v2|` 前綴，確保舊安裝會強制重部署一次

### 延續 v0.1.15

- 翻譯模式、原文框為空時：符號／數字／Del／Enter／空白直送 App
- meta + Enter 後空白不再冒中文
