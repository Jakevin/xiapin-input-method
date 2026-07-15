# 蝦拼輸入法 — Windows 版

Windows 版使用 [Rime 小狼毫 Weasel](https://github.com/rime/weasel) 作為系統輸入法前端，與 macOS 版共用同一套 schema、字典與 Lua 字根提示。安裝蝦拼本身不需管理員權限。

## 安裝

1. 先安裝小狼毫。
2. 下載並解壓 `xiapin-windows-v*.zip`。
3. 雙擊 `install-windows.cmd`。
4. 安裝器會複製檔案到小狼毫的使用者資料夾，並自動執行「重新部署」。

預設目錄是 `%APPDATA%\Rime`。如果你在小狼毫設定過自訂資料夾，安裝器會從 `HKCU\Software\Rime\Weasel\RimeUserDir` 自動取得。同名舊檔會先備份為 `*.bak.<時間>`。

## PowerShell 選項

```powershell
.\install-windows.ps1 -DryRun
.\install-windows.ps1 -RimeUserDir C:\Temp\XiapinRime -NoDeploy
.\install-windows.ps1 -DeployerPath "C:\Program Files\Rime\weasel-0.17.4\WeaselDeployer.exe"
```

PowerShell 安裝器不依賴 Python，會直接從附帶的 openxiami 碼表產生過濾後的 `xiapin_liur.dict.yaml`。

## 使用

- `Control + grave`（Control + 反引號）：開啟方案選單。
- `Shift + Space`：在「蝦拼」與「蝦拼英文」之間切換。
- 單按 `Shift`：切換中文與西文輸入。

## 驗證邊界

專案在 macOS/Linux 檢查 Rime 檔案、字典產生邏輯與 ZIP 內容，CI 會在 Windows runner 上用 PowerShell 實際執行 `-NoDeploy` 安裝。TSF 輸入、候選窗與 `WeaselDeployer.exe` 仍需在已安裝小狼毫的 Windows 上做最後驗收。
