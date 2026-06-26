# 蝦拼輸入法 Spec

## Concept

蝦拼輸入法 combines phonetic entry with optional original mnemonic root hints. A user can type a Pinyin-like syllable, a shape/root code, or a hybrid code that narrows candidates by combining both signals.

This iteration is a deterministic lookup prototype. It defines data validation, lookup, and ranking behavior only; it is not an operating-system input method.

## Input Grammar

Codes are ASCII and case-insensitive.

```text
code        = phonetic | shape | hybrid
phonetic    = syllable
shape       = "m:" root
hybrid      = syllable "+" root
syllable    = 1*(letter)
root        = 1*(letter | digit | "-")
```

Examples:

- `ni` phonetic lookup.
- `m:box` shape/root lookup.
- `ni+person` hybrid lookup.

Whitespace around CLI input is ignored. Empty input returns no candidates.

## Candidate Ranking

Lookup returns every dictionary entry whose `codes` array contains the normalized input code. Sorting is deterministic:

1. Exact normalized code match, reserved for future prefix matching.
2. Shorter matched code length.
3. Lower `rank` value.
4. Candidate text by Unicode code point order.
5. Candidate ID.

The current implementation only performs exact code matching, so rule 1 is always equal for matches.

## Dictionary Schema

The demo dictionary is JSON with this shape:

```json
{
  "version": 1,
  "provenance": {
    "source": "original-demo-data",
    "license": "CC0-1.0",
    "notes": "Hand-authored examples for this repository."
  },
  "entries": [
    {
      "id": "ni-you",
      "text": "你",
      "codes": ["ni", "m:person", "ni+person"],
      "category": "hybrid",
      "rank": 10,
      "license": "CC0-1.0",
      "provenance": "original-demo-data",
      "note": "Original demo mapping."
    }
  ]
}
```

Fields:

- `version`: integer schema version.
- `provenance`: optional dictionary-level object documenting source and license.
- `entries`: array of candidate objects.
- `id`: stable unique string.
- `text`: candidate string printed by the CLI.
- `codes`: non-empty list of lookup codes.
- `category`: `phonetic`, `shape`, or `hybrid`.
- `rank`: integer where lower values sort earlier.
- `license`: optional entry-level license string.
- `provenance`: optional entry-level source string.
- `note`: optional human-readable context.

Unknown fields are ignored by the prototype.

The validator fails with `ValueError` when the root is not an object, `version` is not integer `1`, `entries` is not a list, an entry is not an object, IDs are empty or duplicated, text is empty, codes are empty or do not match the input grammar, category is outside the allowed values, or rank is not an integer.

Validate the bundled dictionary:

```bash
python3 src/xiapin.py --validate
```

## Demo Examples

The included data intentionally uses mnemonic English roots such as `person`, `box`, and `sun`. They are original placeholders for testing mechanics and do not represent Boshiamy/無蝦米 compatibility.

- `ni` returns candidates such as `你`.
- `hao` returns candidates such as `好`.
- `m:box` returns shape/root examples such as `口`.
- `ri+sun` returns a hybrid example such as `日`.

## Legal and Licensing Notes

This repository must not copy Boshiamy/無蝦米 tables, proprietary dictionaries, proprietary root assignments, or copyrighted IME data. Contributions should use original mappings, public-domain data, permissively licensed data, or mappings created specifically for this project with clear provenance.

The phrase "無蝦米-like" describes a broad interaction style: fast shape/root entry. It does not authorize table copying, compatibility claims, or reuse of proprietary mapping choices.

## Future Work

- Add prefix search and ambiguity handling.
- Add frequency data from a permissive source.
- Add a real root design with documented authorship.
- Add conversion/export formats for IME frameworks.
- Add a small interactive REPL.
- Add richer validation tooling for dictionary provenance and style checks.
