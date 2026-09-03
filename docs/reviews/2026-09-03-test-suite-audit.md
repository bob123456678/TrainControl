# The test suite, audited before the release

**Status:** open

**Prefix for citing these findings elsewhere:** `TSX`

**Reviewed:** branch `autonomy-diagram-r0`, on 2026-09-03. Three passes append here in order, each
cross-checking the ones before it, and each covering a different part of the suite:

1. **The GUI tests** - `test/ui/`, and the parts of `test/regression/` that build a window.
2. **The core tests** - `test/core/`, the model, the planner, the graph and the reduction.
3. **Everything else** - `test/regression/` proper, `test/support/`, the harness itself, and the rules
   that hold the suite together.

**What this audit is for.** Correctness, coverage and completeness of the TESTS, not of the code they
test: an assertion that cannot fail, a fixture that guarantees its own precondition, a mutation claimed
and not achievable, a rule enforced on one door of three, a class that is green because it skipped.
Where a test is wrong ABOUT the code, that is a finding here and the code defect it hides is a finding
in [the release review](2026-09-03-release-review.md).

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|

*(filled in by each pass as it appends)*

---
