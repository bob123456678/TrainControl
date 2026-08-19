# Autonomous work log

The running record of a long unattended session on `autonomy-diagram-r0`. Updated as work lands, so
that what was done, what was decided, and what is waiting on Adam can be read without reading the diff.

**How to read this.** Each item carries its own status. Anything marked **NEEDS ADAM** is a question or
a judgement I would not make alone; everything else is either done, in progress, or explicitly deferred
with a reason. Commit hashes are given so any single piece can be reverted on its own.

---

## Orientation, before starting

The branch had moved: four commits landed while I was away, three of them Adam's own review rounds
(`0e3e280`, `2a3f0a8`, `7ab50e2`) and two fixes (`4753f57` GR-B1, `f956946` the branch review's four).
Read before starting, so this session builds on them rather than beside them.

**Review documents audited for outstanding items.** All findings on the v3.0.0 branch review are
dispositioned. Six rows in the post-fix review still read `Open` though the work landed in `0f743bf`;
corrected. What genuinely remains:

| Where | Item | Why it is still open |
|---|---|---|
| post-fix review | B1 | Claim withdrawn, fix stands. Not work - a correction already recorded |
| post-fix review | B8 | Route-preference enumeration cost. Wants measuring before deciding |
| backport review | C1 | Case-only rename crash window. Deliberate: a recovery scan costs more than the risk |
| backport review | C3 | 0-speed locomotive retries in an unbounded paced loop |
| whole-project review | C1-C29 | Low tier, `automation/` `marklin/` `base/` `gui/`. Needs a verification sweep first - four of nine audit rows turned out already fixed, so this table is probably part stale |

---

## The plan, in the order it will be done

1. Full test battery; fix what it finds
2. The timetable test, to Adam's recipe
3. Features and remaining bugs, one commit each, tests red first where possible
   - `syncWithCS2` off the EDT, with a mock CS2 to read from
   - Route editor: ovals for commands and conditions, boolean expression editing
   - Multi-select in the diagram editor, deprecating the odd paste variants
4. Full battery again
5. Independent, regression, focused and behavioural reviewers (Opus); iterate
6. Fable regression and independent reviewer; iterate
7. UI consistency pass - proposal only, no changes
8. Feature completeness and 3.1.0 ideas, for review
9. Test profiling; a lite battery of everything under 10s
10. Readme pass: drop 3.0.0 bugfixes that only ever existed on 3.0.0
11. A new Automation.md as a user guide, with a track-diagram export to illustrate it

---

## Progress

### 1. Full test battery
*In progress.*

