#!/usr/bin/env bash
# 清 Mac 鼠鬚管垃圾 + 重載 Squirrel（不重裝碼表）
set -euo pipefail

RIME_DIR="${RIME_USER_DIR:-$HOME/Library/Rime}"

echo "==> Before: $(du -sh "$RIME_DIR" 2>/dev/null | awk '{print $1}')"

# 備份只留最新 1 份（依檔名前綴）
if compgen -G "$RIME_DIR"/*.bak.* > /dev/null 2>&1; then
  # 刪除「同一原檔」的舊備份，只留最新
  python3 - "$RIME_DIR" <<'PY'
import sys
from pathlib import Path
from collections import defaultdict
d = Path(sys.argv[1])
groups = defaultdict(list)
for p in d.glob("*.bak.*"):
    # foo.yaml.bak.20260101 -> foo.yaml
    name = p.name
    idx = name.rfind(".bak.")
    if idx < 0:
        continue
    base = name[:idx]
    groups[base].append(p)
removed = 0
for base, files in groups.items():
    files.sort(key=lambda x: x.stat().st_mtime, reverse=True)
    for old in files[1:]:
        old.unlink(missing_ok=True)
        removed += 1
print(f"removed {removed} old backups")
PY
fi

if [[ -d "$RIME_DIR/lua" ]] && compgen -G "$RIME_DIR/lua"/*.bak.* > /dev/null 2>&1; then
  python3 - "$RIME_DIR/lua" <<'PY'
import sys
from pathlib import Path
from collections import defaultdict
d = Path(sys.argv[1])
groups = defaultdict(list)
for p in d.glob("*.bak.*"):
    idx = p.name.rfind(".bak.")
    if idx < 0: continue
    groups[p.name[:idx]].append(p)
n=0
for files in groups.values():
    files.sort(key=lambda x: x.stat().st_mtime, reverse=True)
    for old in files[1:]:
        old.unlink(missing_ok=True); n+=1
print(f"removed {n} old lua backups")
PY
fi

# 編譯中間 txt
if [[ -d "$RIME_DIR/build" ]]; then
  rm -f "$RIME_DIR/build"/*.table.txt "$RIME_DIR/build"/*.prism.txt 2>/dev/null || true
  echo "removed build intermediate .txt"
fi

echo "==> After:  $(du -sh "$RIME_DIR" 2>/dev/null | awk '{print $1}')"

# 重載鼠鬚管
SQUIRREL="/Library/Input Methods/Squirrel.app/Contents/MacOS/Squirrel"
DEPLOYER="/Library/Input Methods/Squirrel.app/Contents/MacOS/rime_deployer"

SHARED="/Library/Input Methods/Squirrel.app/Contents/SharedSupport"
if [[ -x "$DEPLOYER" ]]; then
  echo "==> rime_deployer --build (with SharedSupport)"
  if [[ -d "$SHARED" ]]; then
    "$DEPLOYER" --build "$RIME_DIR" "$SHARED" 2>&1 | tail -30 || true
  else
    "$DEPLOYER" --build "$RIME_DIR" 2>&1 | tail -20 || true
  fi
fi

echo "==> restart Squirrel"
killall Squirrel 2>/dev/null || true
sleep 0.8
if [[ -x "$SQUIRREL" ]]; then
  open -a Squirrel 2>/dev/null || open "/Library/Input Methods/Squirrel.app" 2>/dev/null || true
fi
sleep 1
ps aux | grep -i '[S]quirrel.app/Contents/MacOS/Squirrel' | awk '{print "Squirrel PID",$2,"RSS_MB",int($6/1024)}'
echo "Done. Try typing in any app."
