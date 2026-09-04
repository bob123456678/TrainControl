# Validating the day's fixes: are they real, and did any of them break something

**Status:** open

**Prefix for citing these findings elsewhere:** `OPV`

**Reviewed:** branch `autonomy-diagram-r0`, on 2026-09-04, at `6c86c1f8`. The subject is the fix round
that closed [the acceptance review](2026-09-04-release-acceptance-review.md) - `7c562279` (ACC-A1),
`15a2878d` (OB-172 third attempt), `2a30c991` (ACC-B1/B2/B3), `0a9f9ffc` (the C findings and a javadoc
orphan), `6c86c1f8` (ACC-C2/C7/C9) - plus the same day's `489439fa` (the `mustBackIn` removal) and
`e2afe88c`/`15a2878d`'s `LayoutGrid` work. Every fix was checked by reading the code at HEAD, never
by reading its disposition. Nine test classes were run through `one.sh`: **310 tests, 0 failures, 0
skips**, and the live-layout guard did not fire (OPV-D9). `cs2_sample_layout/` was read and never
written; `battery.sh` was not run, per Adam's ruling. Nothing in the tree was edited except this file.

---

## Verdict

**The fixes are sound, and nothing on the layout is worse than it was before they were made.** The
release blocker is genuinely fixed: `hadItsPath` is captured in the right place - inside the same
`synchronized` block, on the line above the removal that consumes it - and the gate is correct for
every way into that handler I could enumerate, including the one the first attempt got wrong
(OPV-D1, OPV-D2). The `mustBackIn` removal leaves `firstClearRoute`'s `turned` flag genuinely inert
as a rule, its visited key strictly finer and therefore incapable of losing a route, and `connected`'s
state removal is safe for a reason that can be stated in one line (OPV-D3). The bundles are clean, the
ellipsis cannot double, and the `paint()` override erases nothing. What is worse is not the railway
but the *evidence*: eleven findings were closed and **one test was written between them**, so the
release blocker's gate can be deleted and all 310 tests still pass (OPV-B1) - and the one C fix that
shipped untested carries a defect a single test would have caught, asking a JSON file for `blocks`
where the key is `block` (OPV-C1). One disposition is also not true of the tree: ACC-C2 says the parity
report now explains its four missing-route rows, and the checked-in report is unchanged - the paragraph
went into the generator only (OPV-C2). Five smaller things the round left behind are listed under C.
None of them is a reason to hold the tag; OPV-B1 is a reason not to treat this round as tested.

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|
| B1 | B | Eleven findings closed, one test written: deleting ACC-A1's gate, or ACC-B3's whole flag, leaves 310 tests green - and the round's one untested C fix carries a defect a test would have caught | `Layout.java:5218`, `TrainControlUI.java:2279`, `test/` |
| C1 | C | ACC-C7's modern-file detector asks a point for `blocks`; the key `Point.toJSON` writes is `block`, so the strongest of its three markers can never fire | `AutonomySession.java:1025` vs `Point.java:1019` |
| C2 | C | ACC-C2 is dispositioned "fixed - the report now lists the four rules"; the paragraph went into the generator and the checked-in report does not contain it | `docs/tools/parity/compare.py:294-315`, `2026-09-03-parity-report.md` |
| C3 | C | ACC-C4's fix left the comment three lines above it still saying the facing is "chosen at random", and duplicated a paragraph verbatim | `AutonomySession.java:668, 676, 692` |
| C4 | C | ACC-C10's ellipsis is added before the More Destinations heading, so the empty-list case gets two separators and a "..." that heads nothing | `LayoutRightclickAutonomyMenu.java:436-478` |
| C5 | C | ACC-B3's exit message names a cause that may not be the reason, and the flag is set by a rebuild request that carries no edit | `TrainControlUI.java:2279, 5564, 5675` |
| C6 | C | Two in-code line citations written by these commits were already wrong when they were committed, and one is the pivot of ACC-A1's argument | `Layout.java:5103, 5212`, `TrainControlUI.java:3626` |
| C7 | C | OB-172's redraw moved to `paint()` and its comment stayed at the end of `paintChildren`, where there is now no code under it | `LayoutGrid.java:738-754` |
| D1 | D | ACC-A1's gate is correct for every way into the handler; the ways enumerated, and the one theoretical window named | `Layout.java:5111-5220, 5391-5414` |
| D2 | D | The capture is in the right place, and the mid-run release still happens - the regression that killed the first attempt is still pinned | `Layout.java:5115`, `testAutoLayout.java:1349` |
| D3 | D | HomeStaging after `mustBackIn`: `turned` is inert as a rule, its key is strictly finer, and `connected`'s state removal is provably safe | `HomeStaging.java:996, 1060, 1731-1805` |
| D4 | D | The three new bundle keys are in all eight languages, pure ASCII, one placeholder each, no straight apostrophe; `testMessageBundles` 13/0/0 | `src/org/traincontrol/resources/` |
| D5 | D | ACC-C10 cannot produce two ellipses, and its threshold mirrors the in-loop one exactly | `LayoutRightclickAutonomyMenu.java:403-440` |
| D6 | D | The `paint()` override erases nothing, costs two `drawString` loops bounded by the clip, and does not interact with `paintTrainOverCaptions` outside the gutter | `LayoutGrid.java:704-780`, `AxisRuler.java` |
| D7 | D | ACC-C9's three deleted constants are referenced by nothing that compiles | `TrainControlUI.java:227-263` |
| D8 | D | ACC-C7's other two markers are genuinely modern: master's `Point.toJSON` writes none of the three | `master:Point.java:493-581` |
| D9 | D | 310 tests across nine classes, 0 failures, 0 skips, no live-layout alarm | below |
| D10 | D | ACC-B1's counter, its guard against a non-array, and its test all hold | `AutonomySession.java:940-968` |
| D11 | D | The javadoc orphan really was repaired, and `testJavadocsAreAttached` is what would catch the next one | `TrainControlUI.java:569-590` |
| D12 | D | The acceptance review's status line correctly still says `open`, though its last commit message says closed | `2026-09-04-release-acceptance-review.md:3` |
| D13 | D | The new raw-English operator log lines follow an existing convention in the same files - checked, not filed | `LayoutEditor.java:435, 571, 595, 607` |
| D14 | D | The grid checkbox now rebuilds the whole diagram; the cost was checked and is bounded | `LayoutEditor.java:3961-3972, 5029` |
| D15 | D | What this validation did NOT cover | below |

---

## B findings

### OPV-B1 - Eleven findings were closed and one test was written; the release blocker's gate is pinned by nothing

| | |
|---|---|
| **Severity** | B |
| **Disposition** | open - reported for Adam's decision on whether it gates the tag |
| **Confidence** | Measured, not argued: `grep` over `test/` for every symbol the round introduced, and the two tests that do reach the changed handler were read line by line to establish which branch each takes. The claim "deleting the gate leaves 310 green" is derived from those two tests taking the true branch, not from executing a mutation - I did not edit the tree. |

The round's commits list impressive test runs. All but one of those runs are of **pre-existing**
classes that pass identically with or without the change under them.

**What actually gained a test:** ACC-B1, and only ACC-B1 -
`testALegacyImportSaysItIsLeavingHandWrittenLocksBehind` (`testAutonomyDiagramSession.java:5755`),
which carries its own control and its own mutation note. It is a good test.

**What did not.** Searched by symbol over the whole `test/` tree:

- **ACC-A1**, the release blocker. `hadItsPath` appears nowhere in `test/`. The two tests that drive
  `executePath`'s `RuntimeException` handler are `testAFailedPathStopsTheRunAndGivesTheTrackBack`
  (`testAutoLayout.java:1349`) and the VD10-A1 ordering test at `:1532`. Both inject their failure
  through `CB_ROUTE_START`, which fires long **after** `configureAndLockPath` has returned - so in both
  the locomotive is in `activeLocomotives`, `hadItsPath` is `true`, and `unlockPath` runs. **Neither
  test ever takes the false branch.** Delete `if (hadItsPath)` at `Layout.java:5218` and the defect
  ACC-A1 describes is back, with 310 tests still green. Nothing in `test/` drives a throw out of
  `configureAndLockPath`'s lock loop *through* `executePath`: `testAutonomyPathValidation` calls
  `configureAndLockPath` directly (`:377, 430, 470, 1020`), which is the half that was already
  correct. The acceptance review said this itself - ACC-D13: *"A red test before the A1 fix is the
  codebase's own rule and still needs writing"* - and the fix landed without it.
- **ACC-B3**, which changes what the application writes to disk on the way out.
  `setupEditDeclinedDuringRun` appears at exactly three lines in `src/` and **zero** in `test/`.
  `testDiscardedEditsDoNotDeleteSetup`, `testCancelRestoresPlacements` and `testARunSurvivesADiagramEdit`
  are named in the commit message; none of them mentions the flag.
- **ACC-C4, C5, C6, C7, C9, C10** - the two commits after `2a30c991` added no test file at all
  (`git show --stat`), and `testAutonomyDiagramSession` reports 111 in all three commit messages,
  which is the count *after* ACC-B1's test. So the count itself says nothing was added.

**Why this is a B rather than a note.** The codebase's own rule is *"prove the guard actually
guards"*, and the reason it exists is written in this folder's README: a guard that reads as
protection and is not is worse than none. ACC-A1 is the single most safety-critical line changed this
week, it is a **one-line gate in an exception handler**, it was itself introduced by a fix, and its
first repair was wrong. That is precisely the shape that gets deleted by a future reader who cannot
see why it is there - and the only thing that would tell him is a test.

**And the round produced its own proof.** OPV-C1 below: ACC-C7's new report line asks a JSON object
for a key that does not exist, so the fix cannot fire on the field its own finding named. A test with
one modern-format fixture in it would have failed on the first run. It shipped instead.

*What would settle it: one test per gate. For ACC-A1, a path whose second edge's accessory cannot be
configured, dispatched through `executePath`, asserting that the start point still holds the
locomotive and that a lock edge shared with a second dispatch still reads occupied - the mutation
being the removal of `if (hadItsPath)`. For ACC-B3, the flag is private GUI state, so the honest
answer may be an `MT-` entry rather than a test; that is a decision, but it should be made rather than
skipped.*

---

## C findings

### OPV-C1 - ACC-C7's modern-file detector asks for `blocks`; the key is `block`

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Fully verified. `grep -rn '"blocks"' src/ test/` returns exactly one hit, the new check itself; `"block"` is written at `Point.java:1019` and read at `Layout.java:7533`. Master's `Point.toJSON` was read to confirm all three markers are modern. Not executed. |

`whatALegacyImportLeaves` gained a count of squares carrying keys the 2.8.1 format never wrote, so a
modern export re-imported through the legacy door is reported rather than silently stripped
(`AutonomySession.java:1012-1029`):

```java
if (p.has(AutonomyBuilder.AUTO_DESTINATION) || p.has("protectingSignal")
    || p.has("blocks"))
{
    modern++;
}
```

`Point.toJSON` writes the field in the singular:

```java
if (this.block != null)
{
    jsonObj.put("block", this.block);
}
```

`"blocks"` occurs **once in the entire tree**, on the line above. So the third clause is dead: it can
never be true for a file this version produced.

That matters more than an ordinary typo because of which field it is. The other two markers are
conditional - `autoDestination` is written *"only when it differs from the default"* and
`protectingSignal` only where a station has one - so a modern export from a railway where every
station is an automatic destination and none is signal-protected has exactly one marker, `block`, and
that is the one the check cannot see. `Point.toJSON`'s own comment says what losing it costs:
*"every square split into independent Points again, so two trains could once more be routed onto one
platform. The operator would have had no way to tell."*

Graded C, matching ACC-C7, because the trap is the round trip rather than the supported migration -
and because `protectingSignal` does fire on Adam's own layout, so his file would be caught. The fix
is one character.

The acceptance review's own text spelled it "blocks" in prose (`ACC-C7`: *"`autoDestination`,
`protectingSignal`, blocks"*), and the fix implemented the prose rather than checking the writer. That
is this folder's *"verify the layer you are actually claiming about"* rule, arriving from the
document side.

### OPV-C2 - ACC-C2 says the parity report now lists the four rules; the report does not

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Fully verified. `grep -c "Before treating a row as a regression" docs/reviews/2026-09-03-parity-report.md` returns 0; the same string is at `docs/tools/parity/compare.py:302`. The generator's own scoping was read and is correct. |

ACC-C2's disposition reads: *"fixed - the report now lists the four rules that make a row expected, so
a reader can tell a regression from a rule working."*

The four rules were added to `compare.py`, correctly placed inside the `else:` arm that runs only when
there are missing routes (`:294-315`), and they will appear the next time somebody regenerates the
report. **The checked-in report was not regenerated.**
`docs/reviews/2026-09-03-parity-report.md` still ends its route table at the four rows and moves
straight to `## 3. Concurrency`. A reader opening it today is in exactly the position the finding
described: told that *"3.0.0 should offer at least what 2.8.1 does"*, shown four rows where it does
not, and left to conclude the wrong thing.

Two halves of the finding are also untouched and unmentioned by the disposition, and ACC-C2 raised
both: the report still has **no status line** (so by this folder's README it is open), and it is still
a **generated report living in `docs/reviews/`**, which the README says *"do not belong in this
folder."* The disposition speaks only to the explanatory half and says "fixed" for all of it.

*The cheapest closure is the one the finding already named: fold the four rules into a review, delete
the dump. If the report is kept, it has to be regenerated for the disposition to be true.*

### OPV-C3 - ACC-C4 removed the randomness and left the comment that describes it

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Fully verified by reading `AutonomySession.java:660-710`. The determinism of `ways.get(0)` was traced to `StationIndex.facingsAt` -> `pointNamesAt` -> `pointsBySquare`, a `Map<TileKey, List<String>>` in builder emission order; I did **not** prove that order stable across two rebuilds of the same file, which is what "reproducible" ultimately rests on. |

The fix replaced `ways.get(new Random().nextInt(ways.size()))` with `ways.get(0)` and added a counter,
which is right. What it left:

- **`AutonomySession.java:668`**, three lines above its own explanation, still reads: *"And which way
  it is pointing, chosen at random from this square's copies."* That is now false, and it is the first
  sentence a reader meets.
- **`:676` and `:692`** are the same paragraph, twice, verbatim - *"Not legality-checked, unlike
  placing one by hand on the diagram: that asks the RUNNING graph which copies can be left, and during
  an import there is no running graph to ask. A copy that cannot be left is reported by the checks,
  which is the same answer arrived at later."* The new block was inserted above the old one and
  carried a copy of it along.

This is the same class of defect as ACC-C1, which this round fixed in three other files on the day it
introduced these. Worth saying because the codebase's stated policy is that comments are the record -
*"Leave the reasoning where the next person will trip over it"* - and a lead sentence that contradicts
the code six lines down is the version of that which costs.

### OPV-C4 - ACC-C10's ellipsis is placed above the heading it belongs under

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Verified by reading the whole menu construction (`:288-500`). The resulting item order is derived from the `add`/`addSeparator` sequence, not observed on screen - this class is package-private GUI code with no test at the assembly layer (ACC-D8 says so). |

The fix is correct in the two things that could have gone wrong, and both are checked under OPV-D5: it
cannot produce two ellipses, and its threshold mirrors the in-loop one exactly. What it gets wrong is
where it sits.

`LayoutRightclickAutonomyMenu.java:436` adds the escape, and `:478` is the More Destinations block
that VD11-C3 gave its own separator and locomotive-name heading *for the empty-`paths` case*. Both now
run, in that order, so a train whose choosable destinations were all filtered and which also has
non-automatic ones gets:

```
------------------      <- the new block's addSeparator()
...                     <- the escape to the autonomy tab
------------------      <- VD11-C3's addSeparator()
EN57-203                <- disabled, the heading
More Destinations  >
```

Two separators, and the "..." floating between the previous menu section and the heading that says
whose menu this is. In the non-empty case the same escape sits *after* the destination list, under the
heading - which is where a reader looks for it. And when `otherPaths` is empty too - the case the
finding is actually about, everything filtered - the escape appears with no locomotive name above it
at all, because the name is only added by the two blocks that were skipped.

*The whole fix is one move: build the escape after the More Destinations block rather than before it,
and let the existing heading gate provide the separator.*

### OPV-C5 - The declined-edit flag is set by a rebuild that carries no edit, and the exit line can name the wrong cause

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | The three sites and both call sites of `rebuildRunningLayoutFromSetup` were read. **I could not establish a reachable sequence** in which `autonomyEditorClosed()` runs with `isAutonomyBusy()` true - the editor cannot be opened while autonomy is busy (`TrainControlUI.java:4621`, OB-047) and `refuseWhileEditorOpen()` guards every door that starts trains. So the second half below is structurally real and possibly unreachable, which is this folder's B3/C7/C15 shape. The log-line half is reachable on any exit. |

Two things about `ACC-B3`'s flag, neither of them a reason to change the behaviour it was written for -
that part is right, and the loss it prevents is authored data.

**The flag is set from a door that carries no edit.** `rebuildRunningLayoutFromSetup(boolean)` sets it
whenever the rebuild is declined (`TrainControlUI.java:5561-5565`), and it has two callers.
`AutonomyEditorPanel.setupChanged()` (`:6459`) reaches it *because the setup changed* - correct.
`autonomyEditorClosed()` (`TrainControlUI.java:5675`) calls it **unconditionally on every close**,
edit or no edit, and its own comment says so: *"this is a courtesy, not a reason to stop their
trains."* If that path can ever run while autonomy is busy, a session in which nothing was edited
loses its exit capture and is told an edit could not be applied. The field's javadoc argues the never-
clearing is safe because *"what it costs when it is stale is one session's train positions"* - that
argument is about a flag set by a real decline, and it does not cover a flag set by a close.

**The message can name a cause that is not the reason.** The new branch precedes the capture's own
four conditions:

```java
if (captureSession && setupEditDeclinedDuringRun)
{
    this.model.log("Where each locomotive finished has not been saved, because a setup edit ...");
}
else if (captureSession && this.activeDiagramConfiguration != null
        && this.model.hasAutoLayout()
        && this.model.getAutoLayout().isValid()
        && !this.model.getAutoLayout().isRunning())
```

So on an exit with autonomy still running, or with no active diagram configuration, or with an invalid
layout - all of which already skipped the capture, silently - the operator is now told the positions
were not saved *because of the declined edit*. The **loss** it reports is true in every one of those
cases; only the **reason** is wrong. That is the smaller half, but the message is the entire remedy
here, and a remedy that names the wrong cause sends somebody looking in the wrong place.

*Both are cheap: gate the flag on the caller that carries an edit (`sayIfDeclined`, or a parameter of
its own), and move the log line inside the branch that would otherwise have captured.*

### OPV-C6 - Two line citations were already wrong when they were committed, and one is ACC-A1's pivot

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Fully verified: each cited target was located by symbol and its current line read. `:3232` and `:2923`, the other two citations in the same comments, are correct. |

The ACC-A1 comment's load-bearing sentence is *"a locomotive joins `activeLocomotives` only after
`configureAndLockPath` has returned (`:5373`)"*, and it appears twice - `Layout.java:5103` and
`:5212`. The `put` is at **`Layout.java:5414`**. Line 5373 is now inside a comment about the removed
protecting-signal sweep, which is plausible enough to read past. The drift is the commit's own:
`7c562279` inserted 41 lines above the target in the same diff that wrote the citation, so it was
wrong the moment it was committed.

The same happened in `2a30c991`: `TrainControlUI.java:3626` cites *"`:20298` reloads after the TRACK
DIAGRAM EDITOR closes"*, and that call is at **`:20370`** - the commit added 72 lines above it. Line
20298 is a tab-restoration check.

Not filed for tidiness. ACC-A1's whole argument is that `activeLocomotives` membership is a valid
discriminator *because of where that one line sits*, and a reader sent to the wrong line has to
rediscover the argument before he can trust the gate - which, given OPV-B1, is the only check on it
there is. The other two citations in the same comments were right, which is what makes the two wrong
ones hard to spot.

*Citing a symbol rather than a number - "at the `activeLocomotives.put` in `executePathInternal`" -
does not drift.*

### OPV-C7 - OB-172's redraw moved to `paint()` and its comment stayed behind

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Verified by reading `LayoutGrid.java:704-780`. |

`15a2878d` moved the ruler redraw out of `paintChildren` and into `paint()`, correctly and for a
reason it writes down. The paragraph that introduced it did not move. `LayoutGrid.java:738-754` is
still headed *"AND THE AXIS NUMBERS ON TOP OF EVERYTHING (OB-172, Adam 2026-09-04)"* and still ends
*"Painted a second time rather than moved out of the border ... The same pixels twice cost nothing"* -
and there is no code under it: the method closes on the next line.

The explanation of *why* the numbers vanish (a caption is 38 pixels tall against a 30-pixel cell) is
worth keeping and belongs with the paint that fixes it, which is now seventeen lines further down and
has a javadoc of its own. As it stands a reader meets a heading with nothing under it and has to work
out that the two blocks are one argument split across two methods.

---

## D findings - checked and found sound, and coverage edges

### OPV-D1 - ACC-A1's gate is correct for every way into the handler

Enumerated rather than asserted. The handler catches everything thrown out of `executePathInternal`,
and the question is whether `hadItsPath` answers "is this path still locked" in each case.

- **A throw out of the lock loop** (`configureAndLockPath:3028`, `throw lockFailure`). The prefix was
  released by `handleMisconfiguredPath(path.subList(0, edgesLocked), loc)` and the locomotive
  re-reserved at `path.get(0).getStart()` (`:3232`). `activeLocomotives` has no entry - the `put` is
  at `:5414`, past the `return true` - so `hadItsPath` is `false` and nothing is released twice. This
  is the defect, and it is closed.
- **A throw out of `isPathClear`**, which sits inside `synchronized (this)` but **outside** the
  try/catch (`:2940`). Nothing has been locked and `takingPath` has not been claimed; `hadItsPath` is
  `false`; the handler's own `takingPath.remove(loc)` is a no-op. Correct.
- **A throw out of `handleMisconfiguredPath` itself**, past the recovery. `hadItsPath` is still
  `false`, so the partially-released prefix is left as it is rather than released again. Pre-existing
  and unchanged by this fix; the gate does not make it worse.
- **A mid-run throw**, which is what Adam's ruling is about. `hadItsPath` is `true` and the release
  happens - see OPV-D2.
- **A throw after the ordinary ending has begun** (`:5843-5847`). `unlockPath` runs, then
  `activeLocomotives.remove`, then the callbacks - all inside one `synchronized` block. A callback
  that throws is caught with the entry already gone, so `hadItsPath` is `false` and there is no second
  release. Correct, and it is the direction that matters.
- **The version fence** returns normally and deliberately leaves the entry in place (`:4941`); it does
  not reach the handler.
- **`locDeleted`** clears the entry (`:975`), but deleting a locomotive is refused while anything is
  running, so it cannot race a live dispatch.

**The one window I could not close, named rather than filed.** Between `configureAndLockPath`
returning `true` (`:5391`) and `activeLocomotives.put` (`:5414`) there are four statements - the
`if (!result)` test, entry to the `synchronized` block, and two `put`s into `locomotiveMilestones` and
`clearedEdges`. A `RuntimeException` from any of those leaves the path locked with `hadItsPath` false,
so the locks leak instead of being over-released. Only `OutOfMemoryError` (which is not a
`RuntimeException` and so is not caught at all) or a `ConcurrentHashMap` fault can produce one. Not
reachable in practice, and the failure mode is the safe one of the two.

### OPV-D2 - The capture is in the right place, and the mid-run release still happens

`Layout.java:5111-5119`:

```java
final boolean hadItsPath;

synchronized (this.activeLocomotives)
{
    hadItsPath = this.activeLocomotives.containsKey(loc);

    this.activeLocomotives.remove(loc);
    this.locomotiveMilestones.remove(loc);
}
```

The read is the statement above the removal, inside the same monitor, so no other thread can change
the answer between them and the recovery cannot destroy the fact it depends on. That is exactly the
failure the commit message describes as its own first attempt, and
`testAFailedPathStopsTheRunAndGivesTheTrackBack` (`testAutoLayout.java:1349`) is still what catches a
regression to it: it drives a mid-run failure through `CB_ROUTE_START`, then asserts
`assertFalse(e.isOccupied(loc))` for every edge. It ran green here. Its own MUTATION note - *"removing
the `unlockPath` call from the handler fails the occupancy assertion"* - is still true, because that
test takes the `true` branch. What it does not do is pin the branch the gate added; that is OPV-B1.

`clearedEdges.remove(loc)` still follows the release rather than preceding it (`:5223`), so VD10-A1's
ordering is intact; the gate was inserted above it without moving it.

### OPV-D3 - HomeStaging after `mustBackIn`: the `turned` flag is genuinely inert, and the searches stayed correct

Three separate questions, checked separately.

**Is `turned` read as a rule anywhere?** No. In `firstClearRoute` it is computed at `:996`
(`boolean turned = current.turned || next.isReversing();`), used at `:1060` to build the visited key,
and passed into the next `Candidate` at `:1097`. There is no other read: the arrival branch at
`:1067-1094` returns unconditionally now, and `Candidate.turned` (`:1132`) is read only by `:996`.
`grep` for `mustBackIn` in `src/` returns only past-tense prose. The flag is inert.

**Does the visited key still make the search correct?** Yes, and the direction is the safe one. The
key is `next.getUniqueId() + (turned ? "/turned" : "/straight")`, which is strictly **finer** than the
square alone. A finer key can only cause more states to be expanded, never fewer, so no route that
existed before the removal can now be pruned. What it costs is expansions against
`ROUTE_SEARCH_LIMIT`, and that cost is unchanged from before the removal - nothing about the budget
moved. The comment's stated reason for keeping it (*"collapsing the key would prune one of those two
and change which route this search returns first"*) is the right reason and is correct.

**Was it safe for `connected` to lose the same state?** Yes, and for a reason that can be stated
exactly. `connected` (`:1731-1805`) is a plain reachability BFS over `getNeighbors`, keyed on
`getUniqueId()` alone. Its only expansion restriction is
`if (!next.isTerminus() || (next.isDestination() && next.isActive()))` - a predicate over the
**destination node only**, with no dependence on the path taken to reach it. Where the expansion
predicate is a function of the node, a square-keyed BFS is complete for reachability, so collapsing
the two states cannot lose a "yes". That is the difference from `firstClearRoute`, which carries
`withCommandsOf` accessory state along the path and therefore genuinely needs a per-state key.
`connected`'s own comment claims exactly this and is right.

`testHomeStaging` 89/0/0 and `testReturnHomeSequencesAReversal` 10/0/0 here.

### OPV-D4 - The three new bundle keys, and the eight bundles

`leftEdgeLocks`, `facingsGuessed` and `leftModernFields` are present in all eight
`messages*.properties`, each carrying exactly one `{0}`. All eight files contain **zero bytes above
0x7F** (measured, `grep -c $'[\x80-\xff]'` = 0 per file); the accented text is `\uXXXX`-escaped
throughout, and the French, Italian and Dutch values use `’` where an apostrophe is wanted, so no
straight apostrophe reaches a `MessageFormat` string. `core.testMessageBundles` ran **13/0/0**, and
that class independently asserts ASCII-only, identical key sets, no straight apostrophe, one
placeholder per formatted key, no printf placeholders, no duplicate keys and no continuation lines -
which is a stronger guarantee than a reviewer reading eight files. Not checked: whether the
translations say what the English says.

### OPV-D5 - The new ellipsis cannot double, and its threshold mirrors the in-loop one

Two ways it could have gone wrong, both closed. The in-loop escape (`:403-420`) is inside
`for (List<Edge> path : paths)`, whose body cannot run when `paths` is empty; the new one (`:436`) is
gated on `paths.isEmpty()`. The two conditions are mutually exclusive, so no menu can carry both.

The thresholds agree. In the loop: `possible > shown + otherPaths.size()`. In the new block, with
`shown` necessarily 0: `possible > otherPaths.size()`. And `possible` is `paths.size()` taken before
the split (`:296`), so `possible - otherPaths.size()` is precisely the number of squares dropped by
`isOfferableToOperator` - which is what the escape exists to account for. When nothing was dropped, no
escape. Correct.

### OPV-D6 - The `paint()` override erases nothing and costs almost nothing

`AxisRuler.paintBorder` was read end to end: it saves the colour and font, sets a 10pt grey, and calls
`drawString` per column and per row. **It fills no background.** So painting it after
`super.paint(g)` overwrites nothing except the pixels of the digits themselves - the captions and
tiles that were rubbing them out keep everything they drew, and the numbers now land on top of the
overlap. Cost is two loops of `drawString` bounded by `g`'s clip; on Adam's `1 - Main` that is about
forty calls. `paintTrainOverCaptions` runs inside `paintChildren`, which `super.paint` invokes, so the
z-order is background, border, children, trains, ruler - and the only place the last one can cover a
train is the gutter, which holds no tiles. The border is now painted twice per repaint (once by
`JComponent.paint`, once here) at identical coordinates with no antialiasing hints set, so the second
pass is idempotent. `testRenderingCost` is named in the commit as having run 8/0/0; I did not run the
headful `ui` classes (OPV-D15).

### OPV-D7 - ACC-C9's deleted constants are referenced by nothing that compiles

`HIDE_REVERSING_PREF`, `HIDE_INACTIVE_PREF` and `SHOW_STATION_LENGTH`, and the literals
`"HideReversing"`, `"HideInactive"`, `"ShowStationLength"`, appear nowhere in `src/` or `test/`. The
only hits in the working tree are `docs/reference/GraphRightClickGeneralMenu.java.txt` - the deleted
window kept as a `.txt` reference, which is not compiled - and a stale `build/classes` artefact. The
decision to leave their stored preference values alone follows R28-C3's recorded ruling for their two
siblings.

### OPV-D8 - ACC-C7's other two markers really are modern

`git show master:src/org/traincontrol/automation/Point.java` writes exactly thirteen keys, and
`block`, `protectingSignal` and `autoDestination` are none of them. So a genuine 2.8.1 file cannot
carry any of the three by export, and the detector cannot false-positive on the supported migration -
which is the claim ACC-C7's fix rests on, and it holds for the two clauses that work.

### OPV-D9 - The tests that were run

Nine classes through `one.sh`, one JVM each: `core.testAutoLayout` (25),
`core.testAutonomyPathValidation` (15), `core.testHomeStaging` (89), `core.testMessageBundles` (13),
`regression.testEditorSurfaceRules` (37), `core.testAutonomyDiagramSession` (111),
`core.testReturnHomeSequencesAReversal` (10), `core.testNonReversibleTrains` (7),
`core.testMaxActiveTrains` (3). **310 tests, 0 failures, 0 skips**, exit 0. The live-layout guard
printed nothing, so nothing written to `cs2_sample_layout/` during the run. Two files in that folder
(`configuration-Main.json`, `setup.json`) were already modified in the working tree when this
validation started - that is Adam's application writing `loc`/`facing` as trains move, and it predates
the run rather than being caused by it.

### OPV-D10 - ACC-B1's counter holds

`whatALegacyImportLeaves` counts `lockedges` with the same guard shape its four siblings use
(`e.has(...) && !e.isNull(...) && getJSONArray(...).length() > 0`), reports through
`autosetup.ui.leftEdgeLocks`, and is pinned by a test with a control assertion (the one-line report
for a file whose only extra is a length) and a stated mutation. Counting rather than comparing against
the derived graph is argued at the count and is the safe direction. The decision not to measure Adam's
116 references against `deriveLocks` is still open by ACC-B1's own Confidence row and is unchanged
here.

### OPV-D11 - The javadoc orphan really was repaired

`TrainControlUI.java:569-590`: `setupEditDeclinedDuringRun` now carries its own javadoc and
`routeEditor` has its own back, in that order. `regression/testJavadocsAreAttached.java` exists and is
what would catch the next one; the commit message names the checkable rule (*"if the line above the
anchor is `*/`, insert above the javadoc"*), which is the right shape for a recurring error.

### OPV-D12 - The acceptance review's status line is right, and its last commit message is not

`6c86c1f8`'s subject says *"the acceptance review is closed"*; the document's status line still says
`open`, and that is the correct value - ACC-B2's disposition is *"comment fixed, behaviour left for
Adam"*, which is not a finished disposition, and ACC-D9's MT-250 departure check is named by the
review itself as a gate needing Adam's hands. The commit body is more careful (*"Every A, B and C
finding is now dispositioned"*), which is a different and true statement. The document is the status
location under this folder's rule, so nothing needs changing; noted so the next reader does not archive
it on the strength of a subject line.

### OPV-D13 - The new raw-English operator log lines follow an existing convention

`TrainControlUI.java:2281` (ACC-B3) and `LayoutEditor.java:435` (ACC-C6) are English string literals
passed to `model.log`, in an application whose eight bundles are audited by a test class. Checked
before filing: `LayoutEditor.java:571, 595, 607` and `TrainControlUI.java:2673, 2682, 2707, 4056` do
the same and all predate this round - the convention is that setup/editor diagnostics of this kind are
untranslated, and the new lines match their own siblings, including the one ACC-C6 was told to copy.
Consistency is what the fix was asked for. Not a finding; recorded so it is not refiled.

### OPV-D14 - The grid checkbox rebuilds the whole diagram, and the cost is bounded

`setShowGrid` now ends in `drawGrid()` (`LayoutEditor.java:3972`), which is not a border swap: it
discards the current `LayoutGrid` and constructs a new one, re-decoding every tile behind the grid's
own hide-and-reveal timers. That is a visible rebuild on a checkbox rather than a repaint, and the
comment at the call argues for it honestly (*"the ruler is built from the diagram's offsets and
dimensions, which live in `drawGrid`, and duplicating that here is how the two come to disagree"*).
Two things checked and clear: `drawGrid`'s opening `addRowsAndColumns` cannot grow a page here,
because the editor's own open path has already called `drawGrid` and satisfied the minimum; and the
`clearBordersFromChildren(this.grid.getContainer())` immediately above is now wasted work on a grid
about to be discarded, which is harmless. A decision with a cost, not a defect.

### OPV-D15 - What this validation did NOT cover

- **Nothing was executed against the gate.** OPV-D1 and OPV-D2 are established by reading. I did not
  write a test for the lock-phase case, because the instructions for this pass forbid editing any file
  but this one - which is also why OPV-B1's "310 stay green" is derived from reading the two existing
  tests' injection points rather than from running a mutation.
- **No headful test was run** (`ui.testTheDiagramPrintsItsCoordinates`, `testDiagramLooksRight`,
  `testRenderingCost`) - Adam's application holds the foreground, and a focus failure would be
  unattributable. The `paint()` fix's pixel evidence is the commit's, reported and not re-measured.
- **`battery.sh` was not run**, per Adam's ruling.
- **The application was not driven.** OPV-C4's menu order is derived from the `add` sequence, not seen;
  OPV-C5's second half could not be given a reachable sequence and says so.
- **ACC-B2 was read as a comment change only.** Its behaviour is deliberately unchanged and the
  question is Adam's; I checked that the census now names five callers and that
  `getAutonomyViewerPanel().load(wasRunning, false)` still arrives with `resumed = true`, and went no
  further.
- **ACC-C8** (the downgrade release note) and **ACC-C3** (the changelog sentence) are documentation
  changes I read but did not re-derive against `master`; the acceptance review verified both sites and
  I had no reason to doubt them.
- **ACC-C4's reproducibility** rests on `pointsBySquare` emitting its names in the same order for two
  imports of one file. I traced the type (a `List` in a `LinkedHashMap`) and the call chain; I did not
  run two imports and diff them.
- The `mustBackIn` removal's own **railway consequence** - MT-250, whether EN57-203 can leave
  TunnelLongPark nose-first - is untouched by this pass and remains the acceptance review's one open
  gate.
