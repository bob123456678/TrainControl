# Where we stopped — 2026-09-09

Everything is committed and pushed on `autonomy-diagram-r0`, HEAD `f26cb43b`. `git status` is clean
apart from the three files under `cs2_sample_layout/` that your own running TrainControl rewrites.

**Battery: 195 classes green, 0 failures, 0 skips** (one documented skip needs a Central Station).

| | |
|---|---|
| manual tests awaiting you | 29 |
| open issues in the Inbox | 23 |
| open review findings | 30 |

## Answer these first when we resume

### 1. The concurrency cap does not match your words

You ruled the `maxActiveTrains` cap applies **"only under full autonomy"**. The fence in the code is
`isAutoRunning()`, and `executeTimetableInternal` sets that same flag — so **a timetable and Return
Home are capped too**. Meanwhile the occupancy restrictions you ruled on earlier use a *narrower*
test that excludes them. Two rules, two meanings of "autonomy is running".

Either the cap should move to the narrow test to match your sentence, or your sentence meant the
broad one. Nothing was changed; it is documented as a question.

### 2. A length-guard test that is right about the rule and wrong about the railway

`testALongTrainIsOfferedNoBerthWhenEverySectionIsOneUnit` claims a nine-unit train is offered no
reversing berth anywhere. It turns out to depend on **where the train starts**:

- from `1 - Main:14,3` it is offered `BottomMainC (eastbound, reverse)`
- from `1 - Main:20,13` it is offered `RampDown (southbound, reverse)`

and in both cases the sibling assertion reports the room at that berth as **one unit**. So either the
guard refuses from some start squares and not others, or the helper that reports the room disagrees
with the rule that enforces it — which walks back along the path and so is start-dependent by
construction.

This was **reverted rather than resolved**, because turning a green claim red without knowing which
half is wrong would hide it. It needs a real investigation, and it touches the anti-collision guard.

### 3. The viewer editability assessment is written and waiting

`docs/reference/viewer-editability.md`. The short answer to your question is **no** — removing options
would not reduce complexity, because 23 of the 25 gestures are not copies of the editor's menu, they
*are* it, one object. What actually costs is that **the viewer path never captures the running
layout**: OB-144, OB-183, D2-A1, D3-C5 and REV-B2 all landed there, and three separate mechanisms
exist only to stand in for the missing capture.

Recommendation: give the viewer path the capture (one change against five findings), remove four
gestures that do not belong in a one-tile menu, and fix two asymmetries. Your call.

### 4. Four filed issues that need a decision, not work

- **FR-066** — a shortcut to set a square's length. **Control+T is taken** (it edits a square's text)
  and so is Control+G. Which key?
- **OB-193** — TopMainR2 shows two labels. May already be fixed by the 09-08 label work; worth
  checking before anyone spends time on it.
- **OB-194** — clearing locomotives cannot be undone by Cancel. Do you want a warning before the
  action, or a real undo?
- **FR-068** — whether that condition shape can be represented at all, or should be refused explicitly.

## What changed today

**The freeze.** OB-192 was the event thread waiting on the railway's monitor while a driving thread
held it — a frozen UI with trains still running, exactly as you described. Fixed at **seven doors**
across three rounds. The third round stopped fixing doors and built the guard instead: it parses the
24 `synchronized` methods out of `Layout`, scans every UI call site, and fails naming any that reaches
one on the event thread. 27 sites inventoried, and there is now **no allowance for a graph walk on the
event thread at all** — the why-panel was the last one and moved to a worker on your ruling.

**Ten reported defects fixed**, each seen red first: Control+S naming plain track, the touching
buttons, the menu bar settling in stages, the home locomotive drawn twice, the wash seventeen squares
long for a one-square train, the whole diagram redrawing for one arrow, the orange line, the berth too
short, the route dropping every switch instead of one, and the blank why-banner.

**The grey is back and means what you said it means.** Both marks: the orange line is where the train
is, the grey is what the railway refuses — at idle as well as while running, and it regenerates when a
placement, a train length or a tile length changes.

**Your railway is out of the test path.** Nine classes moved to a frozen snapshot; several had been
*skipping* rather than failing, which reads as green. Tests build their own locomotives with semantic
names now, and three classes that were writing lengths onto your engines and keeping them now put them
back.

**The fixture library exists** — `test/layouts/` with `single-switch`, `curve-into-platform` and
`live-snapshot`, plus `support.Scenario.open(name)`. Its first use found a real defect on a curve, and
the switch fixture is what finally made the anti-collision rule at a switch testable: it had been
green under mutation in all three classes that were supposed to cover it.

**Three reviewers ran** — three days, the week, and autonomy — this time able to execute tests rather
than only read, which is why they found a deadlock the previous read-only round missed. Both A-grade
findings were closed while their authors were still writing.

## On your convergence worry

Roughly twenty fixes today produced four new defects, every one correct at the door it was made and
wrong at a door nobody swept. What actually stopped the bleeding was not more care but removing the
census: a required parameter so the compiler enumerates callers; one predicate instead of two copies;
one funnel instead of four; and a guard that pins the rule rather than the instances.

The two structural fixes still worth making are the viewer's missing capture (question 3) and getting
the remaining live-railway tests onto the snapshot.
