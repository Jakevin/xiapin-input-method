"""Export the demo dictionary to Rime dictionary format."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.xiapin import DEFAULT_DICTIONARY, _load_entries  # noqa: E402


DEFAULT_OUTPUT = ROOT / "rime" / "xiapin_base.dict.yaml"


def _rime_weight(rank: int) -> int:
    return max(1, 10_000 - rank)


def export_rime_dictionary(
    dictionary_path: Path = DEFAULT_DICTIONARY,
    output_path: Path = DEFAULT_OUTPUT,
) -> None:
    entries = _load_entries(dictionary_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    lines = [
        "# Rime dictionary",
        "# encoding: utf-8",
        "# Generated from data/demo_dictionary.json. Do not add proprietary tables here.",
        "---",
        "name: xiapin_base",
        'version: "0.1.0"',
        "sort: by_weight",
        "...",
    ]

    seen_rows: set[tuple[str, str]] = set()
    for entry in entries:
        for code in entry["codes"]:
            row = (entry["text"], code)
            if row in seen_rows:
                continue
            seen_rows.add(row)
            lines.append(f"{entry['text']}\t{code}\t{_rime_weight(entry['rank'])}")

    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Export 蝦拼 demo data to Rime YAML.")
    parser.add_argument("--dictionary", type=Path, default=DEFAULT_DICTIONARY)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args(argv)

    export_rime_dictionary(args.dictionary, args.output)
    print(f"wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
