# 蝦拼輸入法 v0.1.19

## 下載

| 檔案 | 平台 |
|------|------|
| `xiapin-android-v0.1.19.apk` | Android |
| `xiapin-rime-v0.1.19.zip` | macOS |

https://github.com/Jakevin/xiapin-input-method/releases/latest

## 新功能：LLM 翻譯（OpenAI 相容）

### 設定（App 圖示「翻譯設定」或鍵盤「設定」）

- **引擎**：免費即時 gtx / **LLM**
- **API Base URL**（預設可一鍵填）  
  - OpenRouter：`https://openrouter.ai/api/v1`  
  - OpenCode Zen：`https://opencode.ai/zen/go/v1`
- **API Key**
- **模型名稱**（如 `openai/gpt-4o-mini`）

### 重要：LLM 不即時翻

- 打字時**不會**自動呼叫 API  
- 必須在翻譯面板按 **「翻譯」** 才會請求  
- 避免 API 費用爆掉  

免費 gtx 模式仍維持 debounce 自動翻譯；也可按「重翻」。

## 授權

MIT
