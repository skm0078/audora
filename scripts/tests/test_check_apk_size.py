# File: scripts/tests/test_check_apk_size.py
# Purpose: Proves the APK size gate fails an APK over its budget, warns on a big growth, reads the budget file
#          strictly, and that the repo's own budget file loads.

import json
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "perf"))

import check_apk_size as gate  # noqa: E402

REPO_BUDGET = Path(__file__).resolve().parent.parent / "perf" / "apk-budget.json"


class CheckApkSizeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)

    def budget_file(self, data) -> Path:
        path = self.dir / "budget.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        return path

    def apk(self, size: int) -> Path:
        path = self.dir / "app.apk"
        path.write_bytes(b"\0" * size)
        return path

    def run_gate(self, *args) -> int:
        with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
            return gate.main([str(a) for a in args])

    def test_the_repo_budget_has_every_variant_ci_checks(self):
        for variant in ("dev", "release"):
            self.assertGreater(gate.load_budget(REPO_BUDGET, variant).max_bytes, 0)

    def test_an_apk_inside_its_budget_passes(self):
        budget = self.budget_file({"warnGrowthMb": 1, "variants": {"dev": {"maxMb": 1}}})
        self.assertEqual(0, self.run_gate("--budget", budget, "--variant", "dev", self.apk(900_000)))

    def test_an_apk_over_its_budget_fails(self):
        budget = self.budget_file({"warnGrowthMb": 1, "variants": {"dev": {"maxMb": 1}}})
        self.assertEqual(1, self.run_gate("--budget", budget, "--variant", "dev", self.apk(1_000_001)))

    def test_growth_past_the_warning_line_warns_but_passes(self):
        verdict = gate.judge("dev", 3_000_000, gate.Budget(10_000_000, 1_000_000), baseline_bytes=1_500_000)
        self.assertFalse(verdict.over_budget)
        self.assertTrue(verdict.grew_too_much)
        self.assertIn("+1.50", verdict.row)

    def test_without_a_baseline_the_change_is_not_known(self):
        verdict = gate.judge("dev", 3_000_000, gate.Budget(10_000_000, 1_000_000), baseline_bytes=None)
        self.assertFalse(verdict.grew_too_much)
        self.assertIn("n/a", verdict.row)

    def test_a_missing_variant_or_a_bad_number_is_an_error(self):
        missing = self.budget_file({"warnGrowthMb": 1, "variants": {}})
        with self.assertRaises(ValueError):
            gate.load_budget(missing, "dev")
        bad = self.budget_file({"warnGrowthMb": 1, "variants": {"dev": {"maxMb": "lots"}}})
        with self.assertRaises(ValueError):
            gate.load_budget(bad, "dev")
        self.assertEqual(1, self.run_gate("--budget", bad, "--variant", "dev", self.apk(10)))

    def test_a_missing_apk_fails(self):
        budget = self.budget_file({"warnGrowthMb": 1, "variants": {"dev": {"maxMb": 1}}})
        self.assertEqual(1, self.run_gate("--budget", budget, "--variant", "dev", self.dir / "none.apk"))


if __name__ == "__main__":
    unittest.main()
