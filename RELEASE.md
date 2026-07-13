# 蝦拼輸入法 v0.1.12

macOS（Rime）+ Android 雙平台 release。

## 下載

到本頁 **Assets** 下載：

| 檔案 | 平台 | 說明 |
|------|------|------|
| `xiapin-rime-v0.1.12.zip` | macOS 鼠鬚管 | 解壓後 `bash install.sh` |
| `xiapin-android-v0.1.12.apk` | Android | 直接安裝（允許未知來源） |

```text
https://github.com/Jakevin/xiapin-input-method/releases/latest
```

## Android 安裝

1. 下載 `xiapin-android-v0.1.12.apk` 並安裝
2. **設定 → 系統 → 語言與輸入 → 管理鍵盤** → 啟用「蝦拼」
3. 輸入框切換成「蝦拼」

## 本版變更（v0.1.12）

### Android

- **拼音候選依使用頻次自動排序**（同一輸入碼優先）：例如常選 `guan`→罐，之後「罐」會排前
- 頻率同時記錄：碼層（`@code`）+ 全域字/詞
- 翻譯面板 UX：原文 **EditText**、右側 **送原文**、點譯文才上屏；關翻譯不丟原文
- 空白鍵修正：避免選字後又多吐一個候選
- 原文游標：插入位置尊重游標，不再強制跳到最後
- README 新增截圖（字根 / 英文 / 關聯 / 翻譯）

### 桌面 Rime

- 延續既有 schema 與碼表

## 授權

- 程式碼：MIT
- 第三方：見倉庫 `NOTICE`
