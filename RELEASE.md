# 蝦拼 v0.3.7

## 修正注音模式出現字根碼

**問題**：注音組字時候選顯示 `給 sao`、`幾 gb / wba` 等嘸蝦米字根。

**原因**：
1. 候選列在非英文模式一律掛 Boshiamy 字根註解
2. 字根置頂邏輯在注音模式仍會跑

**修正**：
- 字根註解 / 字根置頂 → **只在「蝦」模式**
- 注音模式候選只顯示漢字（無 sao/gb）
- 加強 `xiapin_zhuyin` schema（speller/algebra 對齊大千）

首次開啟會重部署 Rime build。

https://github.com/Jakevin/xiapin-input-method/releases/tag/v0.3.7
