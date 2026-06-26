# 蝦拼輸入法

蝦拼輸入法是一個給 Rime / 鼠鬚管使用的實驗性輸入方案。目標是把三種輸入習慣放在同一個方案裡：

- 拼音輸入：例如 `tai` 可以出現 `台`。
- 字根輸入：例如 `m:box` 可以出現 `口`。
- 英文候選：例如 `veri` 可以出現 `verify`、`verified`、`verification`。

這個專案不複製、不內建、不宣稱相容任何商業嘸蝦米 / Boshiamy 碼表。若使用者有自己的合法 `liur_Trad.dict.yaml`，安裝器可以在本機匯入作為蝦拼的低權重後備碼表。

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

或用指令下載 v0.1.1：

```bash
curl -L -o xiapin-rime-v0.1.1.zip \
  https://github.com/Jakevin/xiapin-input-method/releases/download/v0.1.1/xiapin-rime-v0.1.1.zip
unzip xiapin-rime-v0.1.1.zip
cd xiapin-rime-v0.1.1
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

目前方案：

```text
蝦拼
蝦拼英文
```

## 可選：匯入自己的嘸蝦米碼表

公開安裝包不包含 `liur_Trad.dict.yaml`，因為目前這類碼表常見檔案未附清楚公開授權。

如果你有自己的合法副本，把它放在 `install.sh` 旁邊再執行安裝：

```text
install.sh
liur_Trad.dict.yaml
rime/
```

安裝器會在本機產生：

```text
~/Library/Rime/xiapin_liur.dict.yaml
```

這個檔案只存在你的電腦，不會被放進本專案。

## 測試輸入

蝦拼方案：

```text
ni        -> 你 / 尼
tai       -> 台
m:box     -> 口
m:person  -> 人 / 你
veri      -> verify / verified / verification
thankyou  -> thank you
,         -> ，
.         -> 。
```

如果有匯入 `liur_Trad.dict.yaml`：

```text
a    -> 對
aaa  -> 鑫
bn   -> 人
ix   -> 我
oo   -> 口
```

蝦拼英文方案：

```text
veri -> verify / verified / verification / very
impl -> implement / implementation / implemented
conf -> confirm / confirmed / configuration
```

## 開發與驗證

查碼核心：

```bash
python3 src/xiapin.py ni
python3 src/xiapin.py m:box
python3 src/xiapin.py ni+person
python3 src/xiapin.py --validate
```

重新產生 Rime base 字典：

```bash
python3 tools/export_rime.py
```

跑測試：

```bash
python3 -m unittest discover -s tests
```

## 專案結構

```text
rime/xiapin.schema.yaml          # 蝦拼主方案
rime/xiapin_english.schema.yaml  # 蝦拼英文候選方案
rime/xiapin.extended.dict.yaml   # 匯入多個字典
rime/xiapin_base.dict.yaml       # 由 demo JSON 產生
rime/xiapin_custom.dict.yaml     # 使用者自訂詞
rime/xiapin_English.dict.yaml    # 英文候選詞庫
```

## 授權與資料來源

`data/demo_dictionary.json` 裡的 demo 資料是為本專案手工建立的 `CC0-1.0` 範例資料。

請不要把來源不明、授權不明或專有的輸入法碼表提交到本專案。
