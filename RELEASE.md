# 蝦注拼 v0.2.15

## 修正：翻完再輸入不重翻

**根因**：選字上屏當下 Rime `preedit` 尚未清空，`scheduleTranslate` 被 `hasPreedit()` 擋掉，且選字路徑沒有 `maybeResume`。

**修正**：
- 原文變更後 **force 排程**（不因 preedit 殘留而跳過）
- 選字 / commit 後 **clearComposition + maybeResume**
- 原文變了清舊譯文並重開 3 秒

實機驗證：`市`→譯文後再打 `恰`→`市恰` 會再翻。

https://github.com/Jakevin/xiapin-input-method/releases/tag/v0.2.15
