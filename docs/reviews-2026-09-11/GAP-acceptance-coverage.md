# GAP - what holds each rule the specification states?

**Status:** open 2026-09-11 - one real gap found and closed (`GAP-B1`), one open and unblocked (`GAP-C1`)

**Prefix:** `GAP`

Adam, 2026-09-11: *"Do the focused pass from 3"* - the question neither the `REG9` review nor its
validation asked. Those two asked whether the battery is green and whether its tests can fail. This asks
the inverse: **of the behaviour this project has written down as its specification, what does nothing
hold?**

That is the question an acceptance decision actually rests on. A green battery says every test passes;
it says nothing about the rules no test was ever written for.

## Method

`docs/reference/behaviour.md` is the specification - it is where every ruling of Adam's is recorded and
where the code's comments point. Its rules are written as paragraphs opening with a bolded statement,
and nearly all of them cite the finding ids they came from.

So: extract every such rule, take the finding ids it rests on, and ask whether **any test in `test/`
cites any of them**. A rule no test mentions is a rule that may still be covered - a test can hold a
behaviour without naming its id - so every hit was then checked by hand against the suite. That
hand-check is where four of the six went.

**85 rules** were extracted. **6** had no test citing any of their ids.

**What this method cannot see, stated plainly.** 59 of the bolded statements cite no finding id at all -
most are sub-clauses of a rule above them (*"Two consequences worth knowing"*, *"In every tier"*) rather
than rules in their own right, but some are real rules stated without provenance, and this pass is blind
to those. The result is a lower bound on the gap, not a survey of it. A rule with no id is also a rule
no comment in the code can point at, which is its own small finding.

## The six, and what each turned out to be

| rule | rests on | verdict |
|---|---|---|
| Nothing about a placement is recorded until the railway has accepted it | `W21-B3` | **REAL GAP - closed today** |
| A paste onto a square no train could leave is refused | `MT-136` | on the manual list, where it belongs - it needs a diagram and a pointer |
| Nothing on the event thread may call a synchronized Layout method | `D3-B2` | covered, under a sibling id - `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor` cites `D3-A1` |
| An unreadable page's autonomy settings are kept | `T10-C3` | covered - `core.testAutonomyDiagramSession.testAPageThatWouldNotReadIsNotJudged`; behaviour.md now names it |
| Two things the room sum is known to get wrong | `MON-C13` | **not a gap - a disclosed limitation waiting on Adam**, see below |
| An emergency stop is obeyed whatever else is true | `SVN-A4` | **REAL GAP - open**, and not blocked: `MT-247` was ruled on 2026-09-06 and built - see below |

### GAP-B1 - the placement rule had nothing holding it

`docs/reference/behaviour.md` section 4 states it as a rule, and `W21-B3` is cited by no test in the
suite. `grep -rn "W21-B3\|placeFacing" test/` finds one file, and that one is about the event thread.

The behaviour is the one that lost data: `Layout.moveLocomotive` refuses in four cases and returns false
rather than throwing, and the diagram's menu discarded that answer - so a placement and a facing were
written into the setup AND SAVED for a move the railway had just declined. The setup is the half that
survives a restart, so the next build emitted the train on a square it was never put on.

`TWV-C5` had already reported this fix as shipping untested; what this pass adds is that it was still
untested after that round, and that the specification states it as a rule.

**Closed today.** `regression.testTheRefusalsAreAskedAtTheDoors.testEveryPlacementDoorUsesTheRailwaysAnswer`
names all three doors, asserts the two that must use the answer do, and declares the one that is allowed
to discard it - `GraphLocAssign.commitChanges` - with the reason that makes it safe: `commitAndRecord`
afterwards asks the POINT what is standing there rather than assuming the move took. Putting `W21-B3`
back at either guarded door fails it.

### GAP-C1 - the emergency stop rests on a manual test that is itself a decision

*"An emergency stop is obeyed whatever else is true"* is as load-bearing as any rule in the document, and
the only thing holding it is `MT-247` - which opens by saying it is *"a question for you as much as a
test"*, because declining the conflict dialog means different things at the two human doors.

One test file mentions an emergency stop at all
(`regression.testARouteDoesNotThrowSwitchesUnderATrain`), and what it pins is which switches a conflict
skips, not that the stop is obeyed.

**Corrected the same day.** I wrote the paragraph above believing `MT-247` was still an open question.
It is not: Adam ruled on 2026-09-06 - *"cancel should cancel everything. OK should fire everything...
don't run the conflicting switch commands, but do run the power off and others. make test cases for
this"* - and it was built on 2026-09-08 in `c22c9d90`, with the rule in `behaviour.md` section 7a.

So this gap is real and has no blocker: the behaviour is settled, the code is written, and the test
cases Adam asked for in that same sentence are the part still missing. **Open, and mine to do**, not
his to decide.

### GAP-D1 - the room sum's two known-wrong cases

`MON-C13` is not a gap. Both were found the day the rule was written, both are stated in the document,
and both were left because fixing either changes what the railway does. They belong with `MT-260`'s six
questions: work waiting on a ruling, not on a test.

## What I did not cover

- The 59 rules that cite nothing, above.
- Anything the specification does not state. A behaviour that is in neither `behaviour.md` nor the
  manual-test list is invisible to this pass by construction, and that set is unbounded - the honest
  answer to "how much is there" is that nobody knows, and that the way to shrink it is to keep writing
  rulings down where this pass can see them.
- The manual-test list's own coverage. 39 entries are outstanding; whether each still describes
  something true was not re-derived here, only their dispositions were brought up to date.
