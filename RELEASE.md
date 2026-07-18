# 蝦拼輸入法 v0.1.14

## 下載

| 檔案 | 平台 |
|------|------|
| `xiapin-rime-v0.1.14.zip` | macOS |
| `xiapin-android-v0.1.14.apk` | Android |

https://github.com/Jakevin/xiapin-input-method/releases/latest

## 本版變更

### Android

- **修 bug**：中文模式打 `meta` + Enter 送出英文後，再按空白不再突然冒出中文
  - Enter 組字中改為上屏「原文碼」並徹底清空 Rime/候選狀態
  - 空白鍵僅在有 preedit 時才選候選（避免殘留候選誤選）

### 延續

- Del 空原文刪 App、長按連續刪
- 拼音使用頻次排序
