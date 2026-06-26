# 蝦拼輸入法 Rime v0.1.6

## 更新內容

- 字根碼表改用 [ryanwuson/rime-liur](https://github.com/ryanwuson/rime-liur) 的 openxiami：
  - `openxiami_TCJP.dict.yaml`
  - `openxiami_TradExt.dict.yaml`
- 移除舊的原創 demo 字根層。
- 移除舊 demo 字典、demo 查碼 CLI 與舊 demo 文件。
- 重新產生 `xiapin_pinyin_liur.dict.yaml`，改用 openxiami 字集與 Squirrel 內建拼音表交集。
- `install.sh` 會從 openxiami 產生過濾後的 `xiapin_liur`，並移除日文假名與 `,`、`.` 符號碼。
- 保留 `Shift` 切換中文/西文，`Shift + Space` 切換蝦拼英文候選方案。

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
thankyou -> thank you
```

## 資料來源

openxiami 字典來源：[ryanwuson/rime-liur](https://github.com/ryanwuson/rime-liur)。

注意：截至本版整理時，該 repo 的 GitHub metadata 沒有標準 license 欄位，README 只描述「基於開源授權」。本 release 保留來源標註；若上游補上明確授權，應同步更新說明。
