# File: scripts/tests/test_check_hotspots.py
# Purpose: Proves the hotspot check fails each shared-file edit that made parallel PRs conflict, and passes the
#          one-file-per-change way of doing the same thing.

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import check_hotspots as hotspots  # noqa: E402

STRINGS = "app/src/main/res/values/strings.xml"


def files(contents: dict[str, str]):
    return lambda path: contents.get(path, "")


NOTHING = files({})


class FindProblemsTest(unittest.TestCase):
    def problems(self, changes, base=NOTHING, head=NOTHING):
        return hotspots.find_problems(changes, base, head)

    def test_editing_progress_md_fails(self):
        [problem] = self.problems([("M", "docs/PROGRESS.md")])
        self.assertIn("docs/progress/", problem)

    def test_a_progress_entry_file_passes(self):
        self.assertEqual([], self.problems([("A", "docs/progress/2026-10-26-faster-start.md")]))

    def test_a_numbered_adr_fails_and_a_dated_one_passes(self):
        self.assertEqual(1, len(self.problems([("A", "docs/adr/078-faster-start.md")])))
        self.assertEqual([], self.problems([("A", "docs/adr/2026-10-26-faster-start.md")]))

    def test_editing_an_old_numbered_adr_passes(self):
        self.assertEqual([], self.problems([("M", "docs/adr/070-small-touches-testers-notice.md")]))

    def test_whats_new_names_are_dated_txt_files(self):
        self.assertEqual([], self.problems([("A", "app/src/main/assets/whatsnew/2026-10-26-faster-start.txt")]))
        self.assertEqual(1, len(self.problems([("A", "app/src/main/assets/whatsnew/10.txt")])))
        self.assertEqual(1, len(self.problems([("A", "app/src/main/assets/whatsnew/2026-10-26-Fast.txt")])))

    def test_a_folder_readme_passes(self):
        self.assertEqual([], self.problems([("A", "docs/progress/README.md")]))

    def test_a_new_string_in_the_shared_file_fails(self):
        base = files({STRINGS: '<string name="a">A</string>'})
        head = files({STRINGS: '<string name="a">A</string>\n<string name="b">B</string>'})
        [problem] = self.problems([("M", STRINGS)], base, head)
        self.assertIn("b", problem)
        self.assertIn("strings_<feature>.xml", problem)

    def test_changing_an_existing_string_passes(self):
        base = files({STRINGS: '<string name="a">A</string>'})
        head = files({STRINGS: '<string name="a">Better A</string>'})
        self.assertEqual([], self.problems([("M", STRINGS)], base, head))

    def test_bringing_back_the_numbered_whats_new_fails(self):
        path = "app/src/main/kotlin/io/github/skm0078/audora/ui/whatsnew/WhatsNew.kt"
        head = files({path: "const val WHATS_NEW_ID = 11"})
        [problem] = self.problems([("M", path)], NOTHING, head)
        self.assertIn("assets/whatsnew", problem)

    def test_a_change_to_the_check_itself_skips_it(self):
        self.assertEqual([], self.problems([("M", hotspots.SELF), ("M", "docs/PROGRESS.md")]))

    def test_deleting_is_never_a_problem(self):
        self.assertEqual([], self.problems([("D", "docs/PROGRESS.md"), ("D", "docs/adr/070-x.md")]))


class ParseNameStatusTest(unittest.TestCase):
    def test_a_rename_is_a_delete_and_an_add(self):
        output = "M\tdocs/README.md\nR100\tdocs/adr/078-x.md\tdocs/adr/2026-10-26-x.md\nA\tnew.txt\n"
        self.assertEqual(
            [("M", "docs/README.md"), ("D", "docs/adr/078-x.md"), ("A", "docs/adr/2026-10-26-x.md"),
             ("A", "new.txt")],
            hotspots.parse_name_status(output),
        )


if __name__ == "__main__":
    unittest.main()
