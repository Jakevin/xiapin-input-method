#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RIME_DIR="${RIME_USER_DIR:-$HOME/Library/Rime}"

mkdir -p "$RIME_DIR"

copy_file() {
  local src="$1"
  local dst="$2"
  if [[ -f "$dst" ]]; then
    cp "$dst" "$dst.bak.$(date +%Y%m%d%H%M%S)"
  fi
  cp "$src" "$dst"
}

copy_file "$ROOT/rime/xiapin.schema.yaml" "$RIME_DIR/xiapin.schema.yaml"
copy_file "$ROOT/rime/xiapin_english.schema.yaml" "$RIME_DIR/xiapin_english.schema.yaml"
copy_file "$ROOT/rime/xiapin.extended.dict.yaml" "$RIME_DIR/xiapin.extended.dict.yaml"
copy_file "$ROOT/rime/xiapin_base.dict.yaml" "$RIME_DIR/xiapin_base.dict.yaml"
copy_file "$ROOT/rime/xiapin_custom.dict.yaml" "$RIME_DIR/xiapin_custom.dict.yaml"
copy_file "$ROOT/rime/xiapin_pinyin_liur.dict.yaml" "$RIME_DIR/xiapin_pinyin_liur.dict.yaml"
copy_file "$ROOT/rime/xiapin_English.dict.yaml" "$RIME_DIR/xiapin_English.dict.yaml"
copy_file "$ROOT/rime/xiapin.custom.yaml" "$RIME_DIR/xiapin.custom.yaml"

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

default_custom.write_text(text, encoding="utf-8")

liur_candidates = [
    root / "rime" / "liur_Trad.dict.yaml",
    root / "liur_Trad.dict.yaml",
]
liur_source = next((path for path in liur_candidates if path.exists()), None)
if liur_source is None:
    print("liur_Trad.dict.yaml not found; installing without optional Boshiamy table.")
    raise SystemExit(0)

lines = [
    "# Rime dictionary",
    "# encoding: utf-8",
    "# Local weighted import generated from liur_Trad.dict.yaml.",
    "# Keep local only unless source licensing is clarified.",
    "---",
    "name: xiapin_liur",
    'version: "1-local"',
    "sort: by_weight",
    "...",
]
data = False
for raw in liur_source.read_text(encoding="utf-8-sig").splitlines():
    if raw == "...":
        data = True
        continue
    if not data or not raw or raw.startswith("#") or "\t" not in raw:
        continue
    parts = raw.split("\t")
    if len(parts) < 2:
        continue
    text, code = parts[0].strip(), parts[1].strip()
    if any("\u3040" <= ch <= "\u30ff" for ch in text):
        continue
    if "," in code or "." in code:
        continue
    if text and code:
        normalized_code = code[1:] if code.startswith("~") else code
        weight = max(1, 10_000 - len(normalized_code) * 100)
        lines.append(f"{text}\t{code}\t{weight}")
(rime_dir / "xiapin_liur.dict.yaml").write_text("\n".join(lines) + "\n", encoding="utf-8")

extended = rime_dir / "xiapin.extended.dict.yaml"
extended_text = extended.read_text(encoding="utf-8")
if "- xiapin_liur" not in extended_text:
    extended_text = extended_text.replace("  - xiapin_pinyin_liur\n", "  - xiapin_pinyin_liur\n  - xiapin_liur\n")
    extended.write_text(extended_text, encoding="utf-8")
print("Optional liur_Trad.dict.yaml imported as filtered xiapin_liur.")
PY

cat <<EOF
Installed 蝦拼 Rime files to:
  $RIME_DIR

Next steps:
  1. Choose Squirrel / 鼠鬚管 from the macOS input menu.
  2. Click 重新部署.
  3. Switch schema with Control+\` and choose 蝦拼 or 蝦拼英文.
EOF
