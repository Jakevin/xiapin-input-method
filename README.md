# 蝦拼輸入法

蝦拼輸入法 is an experimental input-method prototype that combines Pinyin-style phonetic codes with original mnemonic shape/root hints. The goal is to explore a small, auditable lookup core, not to reproduce or claim compatibility with any existing commercial table.

## MVP Scope

- A deterministic Python lookup function and CLI.
- A small JSON demo dictionary with original phonetic, shape/root, and hybrid examples.
- Explicit dictionary validation for the documented schema.
- Candidate ranking that is predictable and easy to test.
- Documentation for the input grammar, dictionary schema, legal boundaries, and next steps.

## Non-Goals

- This is not a complete IME engine.
- This does not ship keyboard hooks, OS integration, learning, cloud sync, or UI.
- This does not copy Boshiamy/無蝦米 tables, implement compatibility, or include proprietary mappings.
- The demo dictionary is intentionally tiny and not suitable for production typing.

## Quick Start

Run a lookup:

```bash
python3 src/xiapin.py ni
python3 src/xiapin.py m:box
python3 src/xiapin.py ni+person
python3 src/xiapin.py --validate
```

Export the demo dictionary for Rime/Squirrel on macOS:

```bash
python3 tools/export_rime.py
```

Then follow [docs/macos-rime.md](docs/macos-rime.md) to copy the generated Rime files into `~/Library/Rime`.
The Rime layout follows the same broad layering pattern as `rime-liur`: schema, extended dictionary, generated base dictionary, custom dictionary, and custom patches. It does not copy `rime-liur` dictionary data.

Use as a module:

```python
from src.xiapin import lookup

print(lookup("ni"))
```

## Verification

```bash
python3 -m unittest discover -s tests
python3 src/xiapin.py --validate
python3 src/xiapin.py ni
python3 src/xiapin.py zz
python3 tools/export_rime.py
```

The demo dictionary declares `CC0-1.0` provenance metadata for hand-authored examples created for this repository.

## Install For Squirrel

Install Squirrel / 鼠鬚管 first, then run:

```bash
bash install.sh
```

The installer does not bundle `liur_Trad.dict.yaml`. If you have your own legal copy, place it beside `install.sh` before running the installer and it will be imported locally.
