# 蝦拼輸入法 Rime v0.1.10

## 更新內容

- 改善候選窗字根提示效能，純英文候選會直接跳過，不再逐字拆碼。
- 字根提示加入候選結果快取，重複候選不再重算。
- 長詞候選限制為 4 個中文字內才顯示字根，避免大量候選拖慢輸入。
- 安裝時產生的 `xiapin_liur` 只保留 U+3400–U+9FFF 的單字 CJK 候選，先排除 macOS 候選窗常顯示成方框的擴展字。
- 主方案移除完整 `easy_en` 大詞庫，完整英文候選集中到「蝦拼英文」方案，降低主方案候選負擔。
- 主方案新增候選窗字根提示，拼音候選會在右側顯示對應的嘸蝦米字根。
- 新增 `rime/lua/boshiamy_comment.lua`，透過 `librime-lua` filter 讀取 openxiami 字根表並補上 candidate comment。
- `install.sh` 會同步安裝 Lua filter 到使用者 Rime 目錄的 `lua/` 子目錄。
- 英文候選改用 [ryanwuson/rime-liur](https://github.com/ryanwuson/rime-liur) 的 `easy_en.dict.yaml`。
- `蝦拼英文` 方案直接使用 `easy_en` 字典，支援更完整的英文詞庫與 completion。
- 主方案 `xiapin.extended` 保留 `xiapin_English` 作為少量補充詞庫；完整 `easy_en` 放在「蝦拼英文」方案。
- 延續 v0.1.6 的 openxiami 字根：
  - `openxiami_TCJP.dict.yaml`
  - `openxiami_TradExt.dict.yaml`

## 安裝

### 1. 安裝鼠鬚管

如果你有 Homebrew：

```bash
brew install --cask squirrel
```

安裝後到 macOS：

```text
系統設定 -> 鍵盤 -> 文字輸入 -> 編輯 -> 加入 鼠鬚管 / Squirrel
```

如果沒有看到鼠鬚管，請登出再登入一次 macOS。

### 2. 安裝蝦拼

下載並解壓縮 release zip 後：

```bash
bash install.sh
```

接著從 macOS 右上角鼠鬚管選單按「重新部署」。

## 切換方式

```text
Shift -> 中文 / 西文
```

如果要進入英文候選方案：

```text
蝦拼 -> Shift + Space -> 蝦拼英文
蝦拼英文 -> Shift + Space -> 蝦拼
```

## 從舊版更新

下載新版 release zip 後重新執行：

```bash
bash install.sh
```

安裝完成後，從鼠鬚管選單按「重新部署」。

## 測試碼

```text
tai      -> 台
mofa     -> 魔法
a        -> 對
aaa      -> 鑫
bn       -> 人
ix       -> 我
hu       -> 悄 / 胡
veri     -> verify / verified / verification
impl     -> implement / implementation
conf     -> confirm / configuration
```

## 資料來源

openxiami 與 easy_en 字典來源：[ryanwuson/rime-liur](https://github.com/ryanwuson/rime-liur)。

注意：截至本版整理時，該 repo 的 GitHub metadata 沒有標準 license 欄位，README 只描述「基於開源授權」。本 release 保留來源標註；若上游補上明確授權，應同步更新說明。
