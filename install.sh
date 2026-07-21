#!/usr/bin/env bash
# 安裝蝦拼到 macOS 鼠鬚管，並做效能向清理
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RIME_DIR="${RIME_USER_DIR:-$HOME/Library/Rime}"

mkdir -p "$RIME_DIR"
mkdir -p "$RIME_DIR/lua"

# 只保留 1 份備份；安裝後清舊 bak / build txt
copy_file() {
  local src="$1"
  local dst="$2"
  if [[ -f "$dst" ]]; then
    cp "$dst" "$dst.bak.$(date +%Y%m%d%H%M%S)"
  fi
  cp "$src" "$dst"
}

cleanup_rime_dir() {
  local dir="$1"
  # 舊備份：每組 basename 只留最新 1 個
  if compgen -G "$dir"/*.bak.* > /dev/null 2>&1; then
    # shellcheck disable=SC2012
    ls -1t "$dir"/*.bak.* 2>/dev/null | awk -F'.bak.' '
      {
        base=$1
        for(i=2;i<NF;i++) base=base ".bak." $i
        # key = path without timestamp
        n=split($0,a,"/")
        file=a[n]
        sub(/\.bak\.[0-9]+$/,"",file)
        count[file]++
        if (count[file] > 1) print $0
      }' | while read -r f; do
        rm -f "$f"
      done
  fi
  # lua 備份
  if compgen -G "$dir/lua"/*.bak.* > /dev/null 2>&1; then
    ls -1t "$dir/lua"/*.bak.* 2>/dev/null | awk '
      {
        f=$0
        sub(/\.bak\.[0-9]+$/,"",f)
        c[f]++
        if (c[f] > 1) print $0
      }' | while read -r f; do rm -f "$f"; done
  fi
  # 編譯中間檔（runtime 不需要）
  if [[ -d "$dir/build" ]]; then
    rm -f "$dir/build"/*.table.txt "$dir/build"/*.prism.txt 2>/dev/null || true
  fi
}

copy_file "$ROOT/rime/xiapin.schema.yaml" "$RIME_DIR/xiapin.schema.yaml"
copy_file "$ROOT/rime/xiapin_english.schema.yaml" "$RIME_DIR/xiapin_english.schema.yaml"
copy_file "$ROOT/rime/xiapin.extended.dict.yaml" "$RIME_DIR/xiapin.extended.dict.yaml"
copy_file "$ROOT/rime/xiapin_custom.dict.yaml" "$RIME_DIR/xiapin_custom.dict.yaml"
copy_file "$ROOT/rime/xiapin_pinyin_liur.dict.yaml" "$RIME_DIR/xiapin_pinyin_liur.dict.yaml"
copy_file "$ROOT/rime/easy_en.dict.yaml" "$RIME_DIR/easy_en.dict.yaml"
copy_file "$ROOT/rime/xiapin_English.dict.yaml" "$RIME_DIR/xiapin_English.dict.yaml"
copy_file "$ROOT/rime/xiapin.custom.yaml" "$RIME_DIR/xiapin.custom.yaml"
copy_file "$ROOT/rime/lua/boshiamy_comment.lua" "$RIME_DIR/lua/boshiamy_comment.lua"

python3 - "$ROOT" "$RIME_DIR" <<'PY'
from __future__ import annotations

import sys
from pathlib import Path


root = Path(sys.argv[1])
rime_dir = Path(sys.argv[2])
default_custom = rime_dir / "default.custom.yaml"

if default_custom.exists():
    text = default_custom.read_text(encoding="utf-8")
else:
    text = "patch:\n  schema_list:\n"

if "schema_list:" not in text:
    if not text.endswith("\n"):
        text += "\n"
    text += "  schema_list:\n"

for schema in ("xiapin", "xiapin_english"):
    marker = f"- schema: {schema}"
    if marker not in text:
        if not text.endswith("\n"):
            text += "\n"
        text += f"    - schema: {schema}\n"

# 效能：page_size 7
if "page_size:" not in text:
    if "patch:" not in text:
        text = "patch:\n" + text
    text = text.replace("patch:\n", "patch:\n  menu:\n    page_size: 7\n", 1)

default_custom.write_text(text, encoding="utf-8")

openxiami_sources = [
    path
    for path in (
        root / "rime" / "openxiami_TCJP.dict.yaml",
        root / "rime" / "openxiami_TradExt.dict.yaml",
    )
    if path.exists()
]
if not openxiami_sources:
    print("openxiami dictionaries not found; installing without optional root table.")
    raise SystemExit(0)

lines = [
    "# Rime dictionary",
    "# encoding: utf-8",
    "# Local weighted import generated from openxiami dictionaries.",
    "# Source: https://github.com/ryanwuson/rime-liur",
    "---",
    "name: xiapin_liur",
    'version: "1-local"',
    "sort: by_weight",
    "...",
]
seen = set()


def is_supported_cjk_text(text: str) -> bool:
    if len(text) != 1:
        return False
    codepoint = ord(text)
    # 常用漢字區（略過 ExtA，候選更乾淨、表更小）
    return 0x4E00 <= codepoint <= 0x9FFF


for source in openxiami_sources:
    data = False
    for raw in source.read_text(encoding="utf-8-sig").splitlines():
        if raw == "...":
            data = True
            continue
        if not data or not raw or raw.startswith("#") or "\t" not in raw:
            continue
        parts = raw.split("\t")
        if len(parts) < 2:
            continue
        text, code = parts[0].strip(), parts[1].strip()
        if not is_supported_cjk_text(text):
            continue
        if "," in code or "." in code:
            continue
        if text and code and (text, code) not in seen:
            seen.add((text, code))
            normalized_code = code[1:] if code.startswith("~") else code
            weight = max(1, 10_000 - len(normalized_code) * 100)
            lines.append(f"{text}\t{code}\t{weight}")
(rime_dir / "xiapin_liur.dict.yaml").write_text("\n".join(lines) + "\n", encoding="utf-8")

extended = rime_dir / "xiapin.extended.dict.yaml"
extended_text = extended.read_text(encoding="utf-8")
if "- xiapin_liur" not in extended_text:
    extended_text = extended_text.replace("  - xiapin_pinyin_liur\n", "  - xiapin_pinyin_liur\n  - xiapin_liur\n")
    extended.write_text(extended_text, encoding="utf-8")
print(f"Optional openxiami imported as filtered xiapin_liur ({len(seen)} entries, BMP only).")
PY

# 過濾拼音單字表 ExtA（縮小 table、少亂碼）
python3 - "$RIME_DIR/xiapin_pinyin_liur.dict.yaml" <<'PYF'
from pathlib import Path
import sys
path = Path(sys.argv[1])
if not path.exists():
    raise SystemExit(0)
out = []
kept = dropped = 0
in_data = False
for raw in path.read_text(encoding="utf-8").splitlines():
    if raw == "...":
        in_data = True
        out.append(raw)
        continue
    if not in_data or not raw or raw.startswith("#") or "\t" not in raw:
        out.append(raw)
        continue
    text = raw.split("\t", 1)[0]
    ok = True
    for ch in text:
        cp = ord(ch)
        if not (0x4E00 <= cp <= 0x9FFF) and ch not in " ":
            ok = False
            break
    if ok:
        out.append(raw)
        kept += 1
    else:
        dropped += 1
path.write_text("\n".join(out) + "\n", encoding="utf-8")
print(f"pinyin_liur filtered: kept={kept} dropped_extA={dropped}")
PYF

cleanup_rime_dir "$RIME_DIR"


cat <<EOF
Installed 蝦拼 Rime files to:
  $RIME_DIR

Cleanup: old *.bak.* trimmed, build/*.txt removed.

Next steps:
  1. 鼠鬚管選單 → 重新部署
  2. 或執行: bash tools/reload_squirrel.sh
  3. Control+\` 選 蝦拼 / 蝦拼英文
EOF
