# Where we stopped — 2026-09-09

Everything is committed on `autonomy-diagram-r0`. `git status` is clean apart from the files under
`cs2_sample_layout/` that your own running TrainControl rewrites.

**Battery: 195 classes green, 0 failures, 0 skips** (one documented skip needs a Central Station).

| | |
|---|---|
| manual tests awaiting you | 29 |
| open issues in the Inbox | 21 |
| open review findings | 30 |

## What you answered, and where each answer went

The four questions on this page have all been answered. What is left of each is the ruling and what it
cost; the questions themselves are in `git log` if anybody wants them.

### 1. The concurrency cap — leave it

Your ruling: **"OK as is for reasons noted in the for-adam file."** The `maxActiveTrains` cap stays
fenced behind `isAutoRunning()`, so a timetable and a Return Home run are capped along with full
autonomy, and the occupancy restrictions keep the narrower fence. `behaviour.md` §1 says both, and says
that the two flags are different on purpose rather than by oversight.

### 2. The length-guard test — settled, and it was the test rather than the guard

Your analysis was that `testALongTrainIsOfferedNoBerthWhenEverySectionIsOneUnit` was *"a poor test, and
an even worse layout config to test with, since we only gave 3 tracks artificially low lengths."* That
is exactly what it turned out to be, and the measurement is worth having:

- **A section is not a tile.** The test set every TILE to one unit, and the run into RampDown is
  eighteen tiles, so "every section is one unit" produced eighteen-unit sections. A nine-unit train
  fits in eighteen and was correctly offered.
- **It was start-dependent.** It asked whatever train happened to be standing for a destination, so it
  asserted about one square while reading as an assertion about the whole railway.

**RampDown is not 22,7's business, and this is why.** RampDown is `1 - Main:21,6` and BottomMainPost is
`22,6` - adjacent on the drawing, with **no edge between them**. So the route from BottomMainB reaches
RampDown the long way round: nine edges, up the 22 column, west along row 1, down the ramp through
TopMainR2 and TopMainPost. `22,7` is on the FIRST of those nine, three switch-crossing edges away from
the berth, and it measures the room at **BottomMainPost** - a square the train passes through and does
not stop at. The room rule walks back from the berth and stops at the last switch, which is your ruling
of 2026-09-02, so it stops seven edges short of that tile and could not reach it.

**Where the guard does bind, it binds exactly as you asked.** With `22,7` at one unit a nine-unit train
is refused BottomMainPost; with it at nine the same train is admitted, exactly-fits; with nothing
measured anywhere it is admitted, *"generally, allow it"*. Both of the cases you asked for are pinned,
with the refusal between them.

The every-section claim moved to `single-switch`, where a section really is two to four tiles, and is
asserted from every one of the five squares a train may stand on.

**Nothing was changed in the guard.** Extending the room rule to the points a route merely passes
through would refuse legitimate through moves everywhere on your railway, and `configureAndLockPath`
locks the whole route before the train moves, so nothing stops it at BottomMainPost as things stand.
`testWhyRampDownIsOffered` is the test that goes red if anybody decides otherwise, and its message says
which claim was traded for which.

### 3. The viewer editability assessment — accepted, and these are the four

You asked: **"OK, which four to remove?"** From `docs/reference/viewer-editability.md`, and all four
are gestures where the viewer's missing capture is a hazard rather than a nuisance:

1. **Changing Direction ▸ Never / May / Must**
2. **Trains May Arrive… ▸ From the N/E/S/W**

   Both change how many copies a square becomes and what those copies are called. From that moment the
   running layout holds names the setup no longer knows: captions go blank, and the right-click menu
   finds no Point to place a locomotive on. That is what `autonomyEditorClosed` rebuilds on close to
   avoid. The editor is where you are when you are thinking about the shape of a station; the viewer is
   where you are while trains are standing on it.

3. **Bulk Tools ▸ Clear All Locomotives**
4. **Bulk Tools ▸ Clear All Home Locomotives**

   Neither is about the square under the pointer. They act on the whole setup from a menu opened by
   right-clicking one tile, and they are reachable even on a page autonomy ignores, where that menu is
   otherwise a single item.

Plus the two to fix rather than remove: **Place / Edit Locomotive…** does not call `setupChanged()`
where its editor twin does, and **Remove {loc}** and **Place {loc}** never reach disk - the exact shape
of MT-246, at doors nobody swept.

**Not done yet** - this is work rather than a record, and it is in the list below.

### 4. The four filed issues

- **FR-066**, the set-length shortcut. **Control+D is taken twice**: the editor uses it to toggle
  addresses, the main window to open the locomotive adder. No key has been picked - that is still
  yours - but the list to choose from is on the issue now, measured from the two key handlers:
  **B E J O P Q U W** are free in both windows, and of those **U** (units) and **E** (the e in length)
  are mnemonic. **M** for measure is free in the editor and taken in the main window, which is exactly
  the trap worth knowing about.
- **OB-193**, TopMainR2's two labels. **Closed.** You were right that it was fixed: `05c7f48e` on the
  08th, and `1 - Main:6,4` is TopMainR2, the very square that fix was made for. The suite now names it.
- **OB-194**, clearing locomotives. **Closed** with the warning you asked for, tested three ways.
- **FR-068**, the bracket that is not the first term. **Closed** - it is already representable, and the
  gesture that builds it is now a test.

## Still to do

1. **The manual-only wash** (your ruling on the orange arrows): *"make the tiles transparent just like
   when we block edges."* Nothing draws it today - the grey is computed for track a train covers, and
   neither `LayoutGrid` nor `LayoutLabel` asks about the parking marking.
2. **The viewer's missing capture**, and then the four removals and two fixes above. One change against
   five findings (OB-144, OB-183, D2-A1, D3-C5, REV-B2).
3. **OB-195** - the editor says autonomy will never choose a compulsory turn and the runtime would.
   Narrow the editor or widen the builder; it costs nothing on your railway either way.
4. **Question 1 on the for-adam page** - your answer (a) disagrees with the ruling that shipped an hour
   after that question was written. Worth two minutes of your time before anybody acts on either.
5. **The remaining live-railway tests onto the snapshot.**

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
back. The length-guard class joined them today - it has its own train and stands it alone on
BottomMainB, so it no longer depends on where your engines happen to be.

**The fixture library exists** — `test/layouts/` with `single-switch`, `curve-into-platform` and
`live-snapshot`, plus `support.Scenario.open(name)`. Its first use found a real defect on a curve, and
the switch fixture is what finally made the anti-collision rule at a switch testable: it had been
green under mutation in all three classes that were supposed to cover it. It is now also where the
one-unit-railway claim lives, because it is the only fixture that can express it.

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
