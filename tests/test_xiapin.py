import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.xiapin import Candidate, lookup, validate_dictionary  # noqa: E402
from tools.export_rime import export_rime_dictionary  # noqa: E402


class XiapinLookupTest(unittest.TestCase):
    def test_phonetic_lookup(self) -> None:
        self.assertEqual([candidate.text for candidate in lookup("hao")], ["好"])

    def test_shape_lookup(self) -> None:
        self.assertEqual([candidate.text for candidate in lookup("m:box")], ["口"])

    def test_hybrid_lookup(self) -> None:
        self.assertEqual([candidate.text for candidate in lookup("ni+person")], ["你"])

    def test_sorting_and_ranking_are_deterministic(self) -> None:
        candidates = lookup("ni")
        self.assertEqual([candidate.text for candidate in candidates], ["你", "尼"])
        self.assertEqual(
            candidates,
            [
                Candidate(id="ni-you", text="你", code="ni", category="hybrid", rank=10),
                Candidate(id="ni-near", text="尼", code="ni", category="hybrid", rank=30),
            ],
        )

    def test_no_match_returns_empty_list(self) -> None:
        self.assertEqual(lookup("zz"), [])

    def test_cli_prints_candidates(self) -> None:
        result = subprocess.run(
            [sys.executable, str(ROOT / "src" / "xiapin.py"), "ri+sun"],
            check=True,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.stdout, "日\n")

    def test_validate_dictionary_accepts_demo_dictionary(self) -> None:
        candidates = lookup("ren")
        self.assertEqual([candidate.text for candidate in candidates], ["人"])

    def test_validate_dictionary_rejects_bad_root(self) -> None:
        with self.assertRaisesRegex(ValueError, "root"):
            validate_dictionary([])

    def test_validate_dictionary_rejects_duplicate_ids(self) -> None:
        payload = {
            "version": 1,
            "entries": [
                {
                    "id": "dup",
                    "text": "甲",
                    "codes": ["jia"],
                    "category": "phonetic",
                    "rank": 1,
                },
                {
                    "id": "dup",
                    "text": "乙",
                    "codes": ["yi"],
                    "category": "phonetic",
                    "rank": 2,
                },
            ],
        }
        with self.assertRaisesRegex(ValueError, "duplicate"):
            validate_dictionary(payload)

    def test_validate_dictionary_rejects_bad_code_grammar(self) -> None:
        payload = {
            "version": 1,
            "entries": [
                {
                    "id": "bad-code",
                    "text": "甲",
                    "codes": ["jia!"],
                    "category": "phonetic",
                    "rank": 1,
                }
            ],
        }
        with self.assertRaisesRegex(ValueError, "grammar"):
            validate_dictionary(payload)

    def test_validate_dictionary_rejects_bad_category(self) -> None:
        payload = {
            "version": 1,
            "entries": [
                {
                    "id": "bad-category",
                    "text": "甲",
                    "codes": ["jia"],
                    "category": "sound",
                    "rank": 1,
                }
            ],
        }
        with self.assertRaisesRegex(ValueError, "category"):
            validate_dictionary(payload)

    def test_lookup_fails_loudly_for_malformed_dictionary_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            bad_dictionary = Path(temp_dir) / "bad.json"
            bad_dictionary.write_text('{"version": 1, "entries": {}}', encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "entries"):
                lookup("ni", bad_dictionary)

    def test_cli_validate_mode(self) -> None:
        result = subprocess.run(
            [sys.executable, str(ROOT / "src" / "xiapin.py"), "--validate"],
            check=True,
            capture_output=True,
            text=True,
        )
        self.assertIn("ok:", result.stdout)

    def test_export_rime_dictionary(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_path = Path(temp_dir) / "xiapin_base.dict.yaml"
            export_rime_dictionary(output_path=output_path)

            content = output_path.read_text(encoding="utf-8")
            self.assertIn("name: xiapin_base", content)
            self.assertIn("你\tni\t9990", content)
            self.assertIn("口\tm:box\t9995", content)

    def test_export_rime_dictionary_contains_shape_root_codes(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_path = Path(temp_dir) / "xiapin_base.dict.yaml"
            export_rime_dictionary(output_path=output_path)

            content = output_path.read_text(encoding="utf-8")
            self.assertIn("人\tm:person\t9995", content)
            self.assertIn("日\tm:sun\t9995", content)
            self.assertIn("明\tm:sun-moon\t9985", content)
            self.assertIn("安\tm:roof-calm\t9980", content)

    def test_rime_schema_derives_plain_root_shortcuts(self) -> None:
        schema = (ROOT / "rime" / "xiapin.schema.yaml").read_text(encoding="utf-8")

        self.assertIn("alphabet: \"abcdefghijklmnopqrstuvwxyz[];':+-\"", schema)
        alphabet_line = next(line for line in schema.splitlines() if "alphabet:" in line)
        self.assertNotIn(",", alphabet_line)
        self.assertNotIn(".", alphabet_line)
        self.assertIn("- derive/^m://", schema)
        self.assertIn("- derive/\\+//", schema)
        self.assertIn("- script_translator@pinyin", schema)
        self.assertIn("dictionary: luna_pinyin", schema)
        self.assertIn("name: ascii_mode", schema)
        self.assertIn("- ascii_composer", schema)
        self.assertIn("Shift_L: commit_code", schema)
        self.assertIn("Shift_R: commit_code", schema)

    def test_rime_schema_uses_extended_dictionary_layer(self) -> None:
        schema = (ROOT / "rime" / "xiapin.schema.yaml").read_text(encoding="utf-8")
        extended = (ROOT / "rime" / "xiapin.extended.dict.yaml").read_text(encoding="utf-8")
        custom = (ROOT / "rime" / "xiapin_custom.dict.yaml").read_text(encoding="utf-8")
        english = (ROOT / "rime" / "xiapin_English.dict.yaml").read_text(encoding="utf-8")

        self.assertIn("dictionary: xiapin.extended", schema)
        self.assertIn("- xiapin_base", extended)
        self.assertIn("- xiapin_custom", extended)
        self.assertIn("- xiapin_English", extended)
        self.assertIn("name: xiapin_custom", custom)
        self.assertIn("thank you\tthankyou\t120", english)

    def test_english_candidate_schema(self) -> None:
        schema = (ROOT / "rime" / "xiapin_english.schema.yaml").read_text(encoding="utf-8")
        default = (ROOT / "rime" / "default.custom.yaml").read_text(encoding="utf-8")

        self.assertIn("schema_id: xiapin_english", schema)
        self.assertIn("name: \"蝦拼英文\"", schema)
        self.assertIn("dictionary: xiapin_English", schema)
        self.assertNotIn("name: ascii_mode", schema)
        self.assertNotIn("toggle: ascii_mode", schema)
        self.assertIn("- schema: xiapin_english", default)


if __name__ == "__main__":
    unittest.main()
