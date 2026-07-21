# 蝦拼輸入法 v0.1.18

## 下載

| 檔案 | 平台 |
|------|------|
| `xiapin-rime-v0.1.18.zip` | macOS 鼠鬚管 |
| `xiapin-android-v0.1.18.apk` | Android（若有打包） |

https://github.com/Jakevin/xiapin-input-method/releases/latest

## 本版重點：Mac 輸入變慢改善

### 已做

1. **重啟鼠鬚管**：記憶體由 ~414MB 降到 ~63MB  
2. **清垃圾**：刪多餘 `.bak`、刪 `build/*.txt`（~60MB+）  
3. **Lua 字根提示加速**：只載 `xiapin_liur`、每字只存最短碼、每鍵最多處理 24 個候選 comment  
4. **英文方案**：`easy_en` 關閉 `enable_sentence`（大表組句很慢）  
5. **字典變瘦**：字根／拼音單字表只留常用漢字 U+4E00–9FFF（去掉 ExtA）  
6. **安裝腳本**：自動清舊備份 + 中間檔；`tools/reload_squirrel.sh` 一鍵重載  

### 使用

```bash
bash install.sh
bash tools/reload_squirrel.sh
```

或鼠鬚管選單 → 重新部署。

## 授權

MIT
