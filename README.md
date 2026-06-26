# 蝦拼輸入法

蝦拼輸入法是一個給 Rime / 鼠鬚管使用的實驗性輸入方案。目標是把三種輸入習慣放在同一個方案裡：

- 拼音輸入：例如 `tai` 可以出現 `台`。
- openxiami 嘸蝦米字根：例如 `a` 可以出現 `對`，`aaa` 可以出現 `鑫`。
- 英文候選：例如 `veri` 可以出現 `verify`、`verified`、`verification`。

字根資料改用 [ryanwuson/rime-liur](https://github.com/ryanwuson/rime-liur) 的 openxiami 碼表：

- `openxiami_TCJP.dict.yaml`
- `openxiami_TradExt.dict.yaml`

安裝時會產生過濾後的 `xiapin_liur.dict.yaml`，並移除平假名、片假名，以及使用 `,`、`.` 的日文假名碼。

## 快速安裝

### 1. 安裝鼠鬚管

如果你有 Homebrew：

```bash
brew install --cask squirrel
```

安裝後到 macOS：

```text
系統設定 -> 鍵盤 -> 文字輸入 -> 編輯 -> 加入 鼠鬚管 / Squirrel
```

如果安裝後沒有看到鼠鬚管，請登出再登入一次 macOS。

### 2. 下載蝦拼 release

到 GitHub Releases 下載最新版：

```text
https://github.com/Jakevin/xiapin-input-method/releases
```

或用指令下載 v0.1.6：

```bash
curl -L -o xiapin-rime-v0.1.6.zip \
  https://github.com/Jakevin/xiapin-input-method/releases/download/v0.1.6/xiapin-rime-v0.1.6.zip
unzip xiapin-rime-v0.1.6.zip
cd xiapin-rime-v0.1.6
```

### 3. 安裝蝦拼

```bash
bash install.sh
```

接著從 macOS 右上角鼠鬚管選單按「重新部署」。

方案選單可用：

```text
Control + `
```

單按 `Shift` 可以在「中文」與「西文」之間切換。

也可以用 `Shift + Space` 在「蝦拼」與「蝦拼英文候選」兩個方案之間切換：

```text
蝦拼 -> Shift + Space -> 蝦拼英文
蝦拼英文 -> Shift + Space -> 蝦拼
```

## 從舊版更新

下載新版 release zip 後，重新執行：

```bash
bash install.sh
```

安裝器會先備份同名舊檔，例如：

```text
xiapin.schema.yaml.bak.20260626123456
```

安裝完成後，從鼠鬚管選單按「重新部署」。

## 測試輸入

蝦拼方案：

```text
tai      -> 台
mofa     -> 魔法
a        -> 對
aaa      -> 鑫
bn       -> 人
ix       -> 我
hu       -> 悄 / 胡
veri     -> verify / verified / verification
thankyou -> thank you
,        -> ，
.        -> 。
```

蝦拼英文方案：

```text
veri -> verify / verified / verification / very
impl -> implement / implementation / implemented
conf -> confirm / confirmed / configuration
```

## 字典產生

安裝器會從 openxiami 碼表產生本機 root 字典：

```text
~/Library/Rime/xiapin_liur.dict.yaml
```

專案也提供 `xiapin_pinyin_liur.dict.yaml`。這是用 Squirrel 內建拼音表、`essay.txt` 字頻和 openxiami 字集交集產生的單字拼音表，讓短碼排序可以穩定控制。

排序規則會優先保留短碼命中。例如 `hu`：

```text
悄  hu   # 兩碼 openxiami
胡  hu   # 兩碼拼音單字
私  hua  # 三碼 openxiami 補全
青  hue
怪  hui
```

重新產生拼音與 openxiami 交集字典：

```bash
python3 tools/export_pinyin_liur.py
```

這個工具會讀取：

```text
/Library/Input Methods/Squirrel.app/Contents/SharedSupport/luna_pinyin.dict.yaml
/Library/Input Methods/Squirrel.app/Contents/SharedSupport/essay.txt
rime/openxiami_TCJP.dict.yaml
rime/openxiami_TradExt.dict.yaml
```

## 開發與驗證

跑測試：

```bash
python3 -m unittest discover -s tests
```

## 專案結構

```text
rime/xiapin.schema.yaml           # 蝦拼主方案
rime/xiapin_english.schema.yaml   # 蝦拼英文候選方案
rime/xiapin.extended.dict.yaml    # 匯入多個字典
rime/xiapin_custom.dict.yaml      # 使用者自訂詞
rime/xiapin_pinyin_liur.dict.yaml # 拼音表與 openxiami 字集交集產生的單字拼音表
rime/openxiami_TCJP.dict.yaml     # openxiami 主碼表，來源 ryanwuson/rime-liur
rime/openxiami_TradExt.dict.yaml  # openxiami 擴充碼表，來源 ryanwuson/rime-liur
rime/xiapin_English.dict.yaml     # 英文候選詞庫
tools/export_pinyin_liur.py       # 重新產生拼音交集字典
```

## 授權與資料來源

openxiami 字典來源：[ryanwuson/rime-liur](https://github.com/ryanwuson/rime-liur)。

注意：截至本版整理時，該 repo 的 GitHub metadata 沒有標準 license 欄位，README 只描述「基於開源授權」。本專案保留來源標註；若上游補上明確授權，應同步更新本段說明。
