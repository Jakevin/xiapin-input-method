# macOS Rime 安裝

這份文件假設你已經完成鼠鬚管 Squirrel 安裝，並且能在 macOS 輸入法選單看到鼠鬚管。

## 產生 Rime 字典

從專案根目錄執行：

```bash
python3 tools/export_rime.py
```

這會根據 `data/demo_dictionary.json` 重新產生：

```text
rime/xiapin_base.dict.yaml
```

## 安裝方案

複製 Rime 方案檔到使用者設定目錄：

```bash
mkdir -p ~/Library/Rime
cp rime/xiapin.schema.yaml ~/Library/Rime/
cp rime/xiapin_english.schema.yaml ~/Library/Rime/
cp rime/xiapin.extended.dict.yaml ~/Library/Rime/
cp rime/xiapin_base.dict.yaml ~/Library/Rime/
cp rime/xiapin_custom.dict.yaml ~/Library/Rime/
cp rime/xiapin_English.dict.yaml ~/Library/Rime/
cp rime/xiapin.custom.yaml ~/Library/Rime/
cp rime/default.custom.yaml ~/Library/Rime/
```

如果你已經有自己的 `~/Library/Rime/default.custom.yaml`，不要直接覆蓋；只需要把下面這段合併進既有檔案：

```yaml
patch:
  schema_list:
    - schema: xiapin
    - schema: xiapin_english
```

## 參考 rime-liur 的檔案分層

本專案參考 `hsuanyi-chou/rime-liur` 的 Rime 檔案分層方式，但不複製其嘸蝦米碼表：

```text
xiapin.schema.yaml          # 輸入方案
xiapin_english.schema.yaml  # 英文候選方案
xiapin.extended.dict.yaml   # 匯入多個字典表
xiapin_base.dict.yaml       # 由 data/demo_dictionary.json 產生
xiapin_custom.dict.yaml     # 使用者自訂詞
xiapin_English.dict.yaml    # 常用英文單字和短句
xiapin.custom.yaml          # 方案覆寫設定
default.custom.yaml         # Rime 方案選單設定
```

之後要加自己的詞，優先加到 `xiapin_custom.dict.yaml`，不要直接改產生出來的 `xiapin_base.dict.yaml`。

英文常用詞可加到 `xiapin_English.dict.yaml`，例如：

```text
thank you<Tab>thankyou<Tab>120
no problem<Tab>noproblem<Tab>100
verify<Tab>veri<Tab>180
verification<Tab>veri<Tab>170
```

Rime 的西文模式通常是直通輸出，不跑候選窗。若要英文候選，請保持在「蝦拼」中文模式中輸入英文前綴，例如 `veri` 會出現 `verify`、`verified`、`verification` 等候選。

也可以切換到「蝦拼英文」方案。它不是西文直通模式，而是專門用 `xiapin_English.dict.yaml` 查英文候選。

## 重新部署

從 macOS 選單列的鼠鬚管選單選「重新部署」，或登出再登入。

重新部署後，在鼠鬚管方案選單裡選「蝦拼」。

在「蝦拼」中按 `Shift + Space` 可切到「蝦拼英文」；在「蝦拼英文」中按 `Shift + Space` 可切回「蝦拼」。

## 測試碼

目前字典只是 demo，可以先試：

```text
ni        -> 你 / 尼
hao       -> 好
m:box     -> 口
box       -> 口
ni+person -> 你
niperson  -> 你
ri+sun    -> 日
tai       -> 台（由 Rime 內建 luna_pinyin 後備拼音提供）
veri      -> verify / verified / verification / very
```

`xiapin.schema.yaml` 允許 `:`、`+`、`-` 作為編碼字元，也加了簡單的派生規則，所以部分碼可省略符號試打，例如 `box` 或 `niperson`。

一般拼音碼則交給 Rime 內建 `luna_pinyin` 作為後備，例如 `tai` 應可出現 `台`。

## 嘸蝦米式字根測試

目前這不是嘸蝦米相容表，而是原創 demo 字根。測試重點是確認「字根碼」能獨立出字：

```text
m:box        -> 口
m:person     -> 人 / 你
m:sun        -> 日
m:sun-moon   -> 明
m:roof-calm  -> 安
```

因為 schema 有 `derive/^m://`，重新部署後也可以測省略 `m:` 的碼：

```text
box        -> 口
person     -> 人 / 你
sun        -> 日
sun-moon   -> 明
roof-calm  -> 安
```
