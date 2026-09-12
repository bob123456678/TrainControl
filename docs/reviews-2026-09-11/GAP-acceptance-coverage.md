# GAP - what holds each rule the specification states?

**Status:** closed 2026-09-11 - one real gap closed (`GAP-B1`), one half-real and closed with a correction (`GAP-C1`)

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
| Two things the room sum is known to get wrong | `MON-C13` | **not a gap - both ruled on and closed the same day**, see below |
| An emergency stop is obeyed whatever else is true | `SVN-A4` | **half a gap, closed** - the screening was held all along and this finding missed it; the execution was not - see below |

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

*"An emergency stop is obeyed whatever else is true"* is as load-bearing as any rule in the document,
and it has two halves: such a route is never refused at a human door, and its stop is never skipped.

**This finding was half wrong, and the correction is the interesting part.** It said nothing in the
suite held the rule. The first half was held all along, in the very file this finding named:
`testARouteThatCutsThePowerIsNeverHeldUpByTheQuestion` asks the screening question of a route with a
stop and one without, inside a dispatch, and `testBothDoorsCarveOutTheEmergencyStop` pins the carve-out
at both doors by shape. Removing `if (this.hasEmergencyStop()) return null;` fails both.

What the pass did was search for the word *"emergency"*, find one file, and read its result as "what it
pins is which switches a conflict skips" - which is true of the tests it looked at and false of the two
it did not. A citation search is a lower bound on coverage, and this is what that costs when the
hand-check behind it is hurried.

**The half that really was missing: nothing ran such a route and looked at the power.** Being excused
the question is worth nothing if the command is then dropped, and no test executed a route carrying a
stop with a conflict present and asserted the railway went dead.

**Closed 2026-09-11.** `MT-247` was not an open question either - Adam ruled on 2026-09-06 (*"don't
run the conflicting switch commands, but do run the power off and others. make test cases for this"*)
and it was built on 2026-09-08 in `c22c9d90`.

`testARouteWithAnEmergencyStopIsNeverHeldBack` now runs a route carrying a stop with the conflicting
turnout reserved by a live dispatch, and asserts three things: it is not screened (with the same route
minus the stop as the control), the power really goes off, and the conflicting turnout is still not
thrown - because obeying the stop by running the whole route would be worse than the defect.

### GAP-D1 - the room sum's two known-wrong cases

`MON-C13` is not a gap. Both were found the day the rule was written, both are stated in the document,
and both were left because fixing either changes what the railway does. They belong with `MT-260`'s six
questions: work waiting on a ruling, not on a test.

**Both were ruled on the same day and are closed** (2026-09-11). A positive length counts, which needed no
code; and a reversal splits the run in, which needed two bounds rather than the one a first reading built
and a test then reverted. `behaviour.md` section 5a carries both, `core.testNonReversibleTrains` holds the
second, and `core.testTheRoomRuleCensusOnTheRealLayout` measures what it costs on his railway - thirty-five
journeys back, none taken away. `MT-363` is the hands-on half.

## What I did not cover

- The 59 rules that cite nothing, above.
- Anything the specification does not state. A behaviour that is in neither `behaviour.md` nor the
  manual-test list is invisible to this pass by construction, and that set is unbounded - the honest
  answer to "how much is there" is that nobody knows, and that the way to shrink it is to keep writing
  rulings down where this pass can see them.
- The manual-test list's own coverage. 39 entries are outstanding; whether each still describes
  something true was not re-derived here, only their dispositions were brought up to date.
