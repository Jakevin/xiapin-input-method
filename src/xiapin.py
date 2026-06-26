"""Deterministic lookup prototype for 蝦拼輸入法."""

from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


DEFAULT_DICTIONARY = Path(__file__).resolve().parents[1] / "data" / "demo_dictionary.json"
CODE_RE = re.compile(r"^(?:[a-z]+|m:[a-z0-9-]+|[a-z]+\+[a-z0-9-]+)$")
VALID_CATEGORIES = {"phonetic", "shape", "hybrid"}


@dataclass(frozen=True)
class Candidate:
    """A lookup result from the demo dictionary."""

    id: str
    text: str
    code: str
    category: str
    rank: int


def normalize_code(code: str) -> str:
    return code.strip().lower()


def _is_plain_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _validate_code(code: Any, entry_id: str, index: int) -> str:
    if not isinstance(code, str):
        raise ValueError(f"Entry {entry_id!r} code {index} must be a string")

    normalized = normalize_code(code)
    if not normalized:
        raise ValueError(f"Entry {entry_id!r} code {index} must be non-empty")
    if normalized != code:
        raise ValueError(f"Entry {entry_id!r} code {index} must be normalized lowercase ASCII")
    if not CODE_RE.fullmatch(normalized):
        raise ValueError(f"Entry {entry_id!r} code {index} does not match input grammar")
    return normalized


def validate_dictionary(payload: Any) -> list[dict[str, Any]]:
    """Validate dictionary schema and return entries.

    Raises ValueError with a specific message when the dictionary is malformed.
    """

    if not isinstance(payload, dict):
        raise ValueError("Dictionary root must be an object")
    if payload.get("version") != 1 or not _is_plain_int(payload.get("version")):
        raise ValueError("Dictionary field 'version' must be integer 1")

    entries = payload.get("entries")
    if not isinstance(entries, list):
        raise ValueError("Dictionary field 'entries' must be a list")

    seen_ids: set[str] = set()
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise ValueError(f"Entry {index} must be an object")

        entry_id = entry.get("id")
        if not isinstance(entry_id, str) or not entry_id:
            raise ValueError(f"Entry {index} field 'id' must be a non-empty string")
        if entry_id in seen_ids:
            raise ValueError(f"Entry {entry_id!r} has a duplicate id")
        seen_ids.add(entry_id)

        text = entry.get("text")
        if not isinstance(text, str) or not text:
            raise ValueError(f"Entry {entry_id!r} field 'text' must be a non-empty string")

        codes = entry.get("codes")
        if not isinstance(codes, list) or not codes:
            raise ValueError(f"Entry {entry_id!r} field 'codes' must be a non-empty list")
        for code_index, code in enumerate(codes):
            _validate_code(code, entry_id, code_index)

        category = entry.get("category")
        if category not in VALID_CATEGORIES:
            raise ValueError(
                f"Entry {entry_id!r} field 'category' must be one of: "
                f"{', '.join(sorted(VALID_CATEGORIES))}"
            )

        if not _is_plain_int(entry.get("rank")):
            raise ValueError(f"Entry {entry_id!r} field 'rank' must be an integer")

    return entries


def _load_entries(dictionary_path: Path = DEFAULT_DICTIONARY) -> list[dict[str, Any]]:
    with dictionary_path.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)
    return validate_dictionary(payload)


def _candidate_from_entry(entry: dict[str, Any], matched_code: str) -> Candidate:
    return Candidate(
        id=entry["id"],
        text=entry["text"],
        code=matched_code,
        category=entry["category"],
        rank=entry["rank"],
    )


def _matching_candidates(entries: Iterable[dict[str, Any]], code: str) -> list[Candidate]:
    candidates: list[Candidate] = []
    for entry in entries:
        for raw_code in entry["codes"]:
            normalized = normalize_code(raw_code)
            if normalized == code:
                candidates.append(_candidate_from_entry(entry, normalized))
                break
    return candidates


def lookup(code: str, dictionary_path: Path = DEFAULT_DICTIONARY) -> list[Candidate]:
    """Return deterministically sorted candidates for a normalized exact code."""

    normalized = normalize_code(code)
    if not normalized:
        return []

    candidates = _matching_candidates(_load_entries(dictionary_path), normalized)
    return sorted(candidates, key=lambda item: (len(item.code), item.rank, item.text, item.id))


def format_candidates(candidates: Iterable[Candidate]) -> str:
    return "\n".join(candidate.text for candidate in candidates)


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv
    if args == ["--validate"]:
        _load_entries()
        print(f"ok: {DEFAULT_DICTIONARY}")
        return 0

    if len(args) != 1:
        print("usage: python3 src/xiapin.py <code>|--validate", file=sys.stderr)
        return 2

    output = format_candidates(lookup(args[0]))
    if output:
        print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
