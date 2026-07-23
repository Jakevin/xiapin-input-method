# 蝦注拼 / 蝦拼 v0.3.0

## 鍵盤拆開：拼 / 注 / 英（不再混打）

混打會互相搶候選、邏輯難維護。本版改為**三套獨立輸入**：

| 模式鍵 | Schema | 鍵盤 | 說明 |
|--------|--------|------|------|
| **拼** | `xiapin` | `qwerty_root` | 嘸蝦米字根 only |
| **注** | `xiapin_zhuyin` | `qwerty_zh`（大千+注音標） | 注音 only |
| **英** | `xiapin_english` | `qwerty` | 英文 + ⇧ |
| **符** | （沿用上一 schema） | `symbols` | 符號數字 |

### 模式鍵輪流

```
拼 → 注 → 英 → 符 → 拼 → …
```

### 變更重點

- `xiapin.schema.yaml` 拿掉拼音/注音 translator，只留字根 table
- 新增 `xiapin_zhuyin.schema.yaml`（terra_pinyin + bopomofo prism）
- 新增 `qwerty_root.xml` 蝦拼專用字母鍵盤
- 注音標**只在注音模式**繪製

首次開啟會重部署 Rime 並重建 build（稍等幾秒）。

https://github.com/Jakevin/xiapin-input-method/releases/tag/v0.3.0
