# 蝦拼輸入法 Rime v0.1.5

## 更新內容

- 新增 `xiapin_pinyin_liur.dict.yaml`，由 Squirrel 內建拼音表與 `liur_Trad.dict.yaml` 交集產生。
- 修正短碼排序：例如 `hu` 會讓兩碼嘸蝦米命中與兩碼拼音單字排在三碼補全前面。
- 新增 `tools/export_pinyin_liur.py`，之後可重新產生拼音與嘸蝦米交集字典。
- `install.sh` 會優先讀取 `rime/liur_Trad.dict.yaml`，再回退到 `install.sh` 旁邊的 `liur_Trad.dict.yaml`。
- 新增 `Shift + Space` 在「蝦拼」與「蝦拼英文」之間切換。
- README 補上從舊版更新流程。

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

## 方案

- `蝦拼`：拼音後備、原創 demo 字根、英文候選，以及可選的本機嘸蝦米式碼表。
- `蝦拼英文`：英文前綴候選模式，例如 `veri`。

切換方式：

```text
蝦拼 -> Shift + Space -> 蝦拼英文
蝦拼英文 -> Shift + Space -> 蝦拼
```

## 從舊版更新

下載新版 release zip 後重新執行：

```bash
bash install.sh
```

更新時，安裝器會優先使用 `rime/liur_Trad.dict.yaml`。如果該檔不存在，就會改找 `install.sh` 旁邊的 `liur_Trad.dict.yaml`。

安裝完成後，從鼠鬚管選單按「重新部署」。

## 可選 liur_Trad.dict.yaml

安裝器會優先讀取 `rime/liur_Trad.dict.yaml`，並在本機匯入成過濾後的 `xiapin_liur`。

如果安裝包裡沒有 `rime/liur_Trad.dict.yaml`，也可以把自己的合法副本放進 `rime/` 再執行安裝：

```text
install.sh
rime/
  liur_Trad.dict.yaml
```

也相容舊方式：把 `liur_Trad.dict.yaml` 放在 `install.sh` 旁邊也可以。

安裝器會在本機匯入成過濾後的 `xiapin_liur`，並移除平假名、片假名，以及使用 `,`、`.` 的日文假名碼。

v0.1.2 起，匯入可選 `liur_Trad.dict.yaml` 時會自動過濾日文假名相關碼。

## 測試碼

```text
ni       -> 你 / 尼
tai      -> 台
m:box    -> 口
veri     -> verify / verified / verification
thankyou -> thank you
```

如果有匯入 `liur_Trad.dict.yaml`：

```text
a   -> 對
aaa -> 鑫
bn  -> 人
ix  -> 我
oo  -> 口
hu  -> 悄 / 胡
```
