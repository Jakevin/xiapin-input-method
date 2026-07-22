# 蝦拼 v0.1.21 — LLM 翻譯修復

## 問題

OpenRouter `openrouter/free` 等免費模型常回：
- `message.content = null`
- 真正內容在 `reasoning`，且先吐英文思考
- `max_tokens` 太小時 `finish_reason=length` 直接失敗

## 修正

- 安全解析 content（避免字串 `"null"`）
- content 空時改讀 `reasoning`
- 從引號／行內挑出日文中文譯文
- `max_tokens` 至少 1024

## 建議

免費路由不穩，正式用可改具體模型，例如：
- `openai/gpt-4o-mini`
- `google/gemini-2.0-flash-001`

仍須按「翻譯」手動觸發。

https://github.com/Jakevin/xiapin-input-method/releases/tag/v0.1.21
