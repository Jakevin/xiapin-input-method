# 蝦拼輸入法 Rime v0.1.1

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

## 可選 liur_Trad.dict.yaml

本安裝包不包含 `liur_Trad.dict.yaml`，因為該檔案未附清楚公開授權資訊。

如果你有自己的合法副本，請把它放在 `install.sh` 旁邊再執行安裝：

```text
install.sh
liur_Trad.dict.yaml
rime/
```

安裝器會在本機匯入成 `xiapin_liur`。

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
```
