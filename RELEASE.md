# 蝦拼輸入法 v0.1.17

## 下載

| 檔案 | 平台 |
|------|------|
| `xiapin-rime-v0.1.17.zip` | macOS |
| `xiapin-android-v0.1.17.apk` | Android |

https://github.com/Jakevin/xiapin-input-method/releases/latest

## 本版變更

### Android

- **候選過濾**：移除 Extension A（㐀…）、部首、假名等易顯示成亂碼的字，只留常用漢字 U+4E00–U+9FFF
- **拼音字典重建**：修正 `luna_pinyin_build.schema.yaml` 使用正確的 `script_translator`
- **部署改善**：assets 更新時自動清掉 `rime_user/build`，避免壞掉的 prism 殘留
- **翻譯模式空框**：符號、Delete、Enter、空白直接送 App（已在前版）

### 延續

- 拼音使用頻次排序
- Enter 送原文碼後不再殘留中文候選

## 授權

MIT
