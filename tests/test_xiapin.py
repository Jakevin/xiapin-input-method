import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class XiapinRimeTest(unittest.TestCase):
    def test_main_schema_has_ascii_mode_and_no_demo_root_grammar(self) -> None:
        schema = (ROOT / "rime" / "xiapin.schema.yaml").read_text(encoding="utf-8")

        self.assertIn("alphabet: \"abcdefghijklmnopqrstuvwxyz[];'\"", schema)
        alphabet_line = next(line for line in schema.splitlines() if "alphabet:" in line)
        alphabet_value = alphabet_line.split(":", 1)[1]
        self.assertNotIn(",", alphabet_value)
        self.assertNotIn(".", alphabet_value)
        self.assertNotIn(":", alphabet_value)
        self.assertNotIn("+", alphabet_value)
        self.assertNotIn("- derive/^m://", schema)
        self.assertNotIn("- derive/\\+//", schema)
        self.assertIn("- script_translator@pinyin", schema)
        self.assertIn("- lua_filter@*boshiamy_comment", schema)
        self.assertIn("dictionary: luna_pinyin", schema)
        self.assertIn("name: ascii_mode", schema)
        self.assertIn("- ascii_composer", schema)
        self.assertIn("Shift_L: commit_code", schema)
        self.assertIn("Shift_R: commit_code", schema)

    def test_extended_dictionary_uses_openxiami_layers_without_demo_base(self) -> None:
        extended = (ROOT / "rime" / "xiapin.extended.dict.yaml").read_text(encoding="utf-8")
        custom = (ROOT / "rime" / "xiapin_custom.dict.yaml").read_text(encoding="utf-8")
        english = (ROOT / "rime" / "xiapin_English.dict.yaml").read_text(encoding="utf-8")

        self.assertNotIn("- xiapin_base", extended)
        self.assertIn("- xiapin_custom", extended)
        self.assertIn("- xiapin_pinyin_liur", extended)
        self.assertNotIn("- easy_en", extended)
        self.assertIn("- xiapin_English", extended)
        self.assertIn("name: xiapin_custom", custom)
        self.assertIn("thank you\tthankyou\t120", english)

    def test_boshiamy_comment_filter_is_installed(self) -> None:
        lua = (ROOT / "rime" / "lua" / "boshiamy_comment.lua").read_text(encoding="utf-8")
        install = (ROOT / "install.sh").read_text(encoding="utf-8")

        self.assertIn("function M.init(env)", lua)
        self.assertIn("MAX_PHRASE_CHARS", lua)
        self.assertIn("comment_cache", lua)
        self.assertIn("MAX_CACHE", lua)
        self.assertIn("^[%z\\1-\\127]+$", lua)
        self.assertIn("../xiapin_liur.dict.yaml", lua)
        self.assertIn("openxiami_TCJP.dict.yaml", lua)
        self.assertIn("openxiami_TradExt.dict.yaml", lua)
        self.assertIn("mkdir -p \"$RIME_DIR/lua\"", install)
        self.assertIn("copy_file \"$ROOT/rime/lua/boshiamy_comment.lua\"", install)

    def test_windows_installer_contains_user_scoped_weasel_flow(self) -> None:
        installer = (ROOT / "install-windows.ps1").read_text(encoding="utf-8")
        launcher = (ROOT / "install-windows.cmd").read_text(encoding="utf-8")

        self.assertIn('"HKCU:\\Software\\Rime\\Weasel"', installer)
        self.assertIn('Join-Path $env:APPDATA "Rime"', installer)
        self.assertIn("WeaselDeployer.exe", installer)
        self.assertIn("& $deployer /deploy", installer)
        self.assertIn("Build-XiapinLiurDictionary", installer)
        self.assertIn("System.Text.UTF8Encoding($false)", installer)
        self.assertNotIn("Start-Process", installer)
        self.assertIn("-ExecutionPolicy Bypass", launcher)

    def test_windows_installer_runs_when_pwsh_is_available(self) -> None:
        pwsh = shutil.which("pwsh")
        if not pwsh:
            self.skipTest("PowerShell is not available on this host")

        with tempfile.TemporaryDirectory() as temp_dir:
            default_custom_path = Path(temp_dir) / "default.custom.yaml"
            default_custom_path.write_text(
                "patch:\n"
                "  menu:\n"
                "    page_size: 9\n"
                "  schema_list:\n"
                "    - schema: luna_pinyin\n"
                "  switcher:\n"
                "    caption: keep-me\n",
                encoding="utf-8",
            )
            subprocess.run(
                [
                    pwsh,
                    "-NoLogo",
                    "-NoProfile",
                    "-File",
                    str(ROOT / "install-windows.ps1"),
                    "-RimeUserDir",
                    temp_dir,
                    "-NoDeploy",
                ],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
            generated = (Path(temp_dir) / "xiapin_liur.dict.yaml").read_text(encoding="utf-8")
            default_custom = (Path(temp_dir) / "default.custom.yaml").read_text(encoding="utf-8")
            self.assertIn("全\tbke\t9700", generated)
            self.assertNotIn("𠷯\tbke;", generated)
            self.assertIn("- schema: luna_pinyin", default_custom)
            self.assertIn("- schema: xiapin", default_custom)
            self.assertIn("- schema: xiapin_english", default_custom)
            self.assertIn("caption: keep-me", default_custom)
            self.assertTrue(list(Path(temp_dir).glob("default.custom.yaml.bak.*")))

    def test_install_places_lua_filter_next_to_generated_lookup(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            env = os.environ.copy()
            env["RIME_USER_DIR"] = temp_dir
            subprocess.run(
                ["bash", str(ROOT / "install.sh")],
                cwd=ROOT,
                env=env,
                check=True,
                capture_output=True,
                text=True,
            )

            rime_dir = Path(temp_dir)
            self.assertTrue((rime_dir / "lua" / "boshiamy_comment.lua").exists())
            self.assertTrue((rime_dir / "xiapin_liur.dict.yaml").exists())
            generated = (rime_dir / "xiapin_liur.dict.yaml").read_text(encoding="utf-8")
            self.assertIn("全\tbke\t9700", generated)
            self.assertNotIn("𠇯\tbke;", generated)

    def test_openxiami_sources_are_bundled(self) -> None:
        tcjp = (ROOT / "rime" / "openxiami_TCJP.dict.yaml").read_text(encoding="utf-8-sig")
        trad_ext = (ROOT / "rime" / "openxiami_TradExt.dict.yaml").read_text(encoding="utf-8-sig")

        self.assertIn("name: openxiami_TCJP", tcjp)
        self.assertIn("對\ta", tcjp)
        self.assertIn("悄\thu", tcjp)
        self.assertIn("name: openxiami_TradExt", trad_ext)

    def test_pinyin_openxiami_dictionary_is_reproducible(self) -> None:
        default_shared_support = Path("/Library/Input Methods/Squirrel.app/Contents/SharedSupport")
        luna_pinyin = default_shared_support / "luna_pinyin.dict.yaml"
        essay = default_shared_support / "essay.txt"
        if not luna_pinyin.exists() or not essay.exists():
            self.skipTest("Squirrel shared support dictionaries are not available")

        with tempfile.TemporaryDirectory() as temp_dir:
            output_path = Path(temp_dir) / "xiapin_pinyin_liur.dict.yaml"
            subprocess.run(
                [sys.executable, str(ROOT / "tools" / "export_pinyin_liur.py"), "--output", str(output_path)],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )

            generated = output_path.read_text(encoding="utf-8")
            committed = (ROOT / "rime" / "xiapin_pinyin_liur.dict.yaml").read_text(encoding="utf-8")
            self.assertEqual(generated, committed)
            self.assertIn("胡\thu\t9796", generated)

    def test_english_candidate_schema(self) -> None:
        schema = (ROOT / "rime" / "xiapin_english.schema.yaml").read_text(encoding="utf-8")
        default = (ROOT / "rime" / "default.custom.yaml").read_text(encoding="utf-8")
        easy_en = (ROOT / "rime" / "easy_en.dict.yaml").read_text(encoding="utf-8-sig")

        self.assertIn("schema_id: xiapin_english", schema)
        self.assertIn("name: \"蝦拼英文\"", schema)
        self.assertIn("dictionary: easy_en", schema)
        self.assertIn("enable_sentence: false", schema)
        self.assertNotIn("name: ascii_mode", schema)
        self.assertNotIn("toggle: ascii_mode", schema)
        self.assertIn("- schema: xiapin_english", default)
        self.assertIn("name: easy_en", easy_en)
        self.assertIn("verify\tverify", easy_en)
        self.assertIn("implementation\timplementation", easy_en)


if __name__ == "__main__":
    unittest.main()
