# The day's seventy commits, read as one reviewer

**Status:** open

**Prefix for citing this document: `DW`.** Taken elsewhere and not reused here: `AD`, `BR`, `CP`,
`CR`, `DA`, `DD`, `DR`, `FBR`, `FP`, `FSR`, `FV`, `GC`, `GR`, `HP`, `HS`, `IAR`, `IP`, `IR`, `ISD`,
`LT`, `MB`, `NR`, `PV`, `RA`, `RE`, `RR`, `RS`, `RV`, `SA`, `SF`, `SV`, `SWC`, `TA`, `TCR`, `TD`,
`TR`, `TS`, `UC`, `UH`, `UR`, `WP`, `WR`.

**What was reviewed, and when.** The 69 commits of the last 24 hours on `autonomy-diagram-r0`,
`48d98f4c` through `65ff3fa9` (HEAD), read on 2026-08-24 against the tree at HEAD. Diffs were read,
not messages; the six changes named highest-risk by the commission got the deepest pass:
`9d864599` (stale-pages refusal), `31b31ff4`+`0ec8bea8` (refresh callback, rename repaints),
`e45d7241` (any receive port), `9e92032e` (LayoutPageEdit extraction), `1467f978` (CS3 backup),
`8ea781fe` (errorCount / Fix strip).

**Method.** Read-only: `git log`, `git show`, and source reading. Nothing in src or test was edited,
nothing was compiled, and no test was run - a battery is running and the harness notes say two JVMs
on one port produce false mass failures. Where a claim below rests on "X calls Y under lock Z", the
method bodies were read at HEAD, not inferred from names.

**What this pass leaned on rather than redid.** The morning stretch (through `1373bea5`) was already
worked over by four same-day passes - `FSR`, `SV`, `FV`, `IAR` - and their findings were acted on in
tree; those commits got a skim here, not a re-review. The FR-013 store conversion (`0c79bbe7`) has
its own dedicated review ([2026-08-24-conversion-review.md](2026-08-24-conversion-review.md)) and was
skimmed for the classic conversion risks only. The genuinely unreviewed stretch was everything from
`e9c134b0` to `65ff3fa9`, and that is where this pass spent its time.

**Severity scale, per the commission:** A - can corrupt the user's railway data, or move a train
wrongly. B - wrong behaviour the user would notice. C - a correctness or clarity issue that will
cause a defect later. D - minor, including checks that came back clean.

---

## Findings at a glance

| Tag | Severity | Where | One line | Status |
|---|---|---|---|---|
| DW-A1 | A | AutonomySession.captureFromLayout / LayoutPageEdit | The rename-path capture refusal discards everything the running layout learned since the last capture, not only the gap the commit reasoned about | open |
| DW-C1 | C | TrainControlUI delete-page path | Page DELETE has the same stale-naming capture the rename just fixed: forgotten settings are written back | open |
| DW-C2 | C | AutonomyMenu / downloadCSLayoutMenuItemActionPerformed | The download offer's guard is narrower than the handler's, so one reachable state clicks to nothing | open |
| DW-C3 | C | LayoutPageEdit.renameOrDuplicate | The parameter list still admits wrong-but-compiling calls: three adjacent booleans, three adjacent Strings | open |
| DW-C4 | C | testTheWindowAttachesItsRefreshCallback | The `attachAutonomyRefresh` count reads raw source with comments in, in the same test that was burned by exactly that | open |
| DW-C5 | C | AutonomySession.hasErrors / arePagesStale | Two public methods added today as "the definition" have no production caller | open |
| DW-D1..D9 | D | - | Checks that came back clean, itemised | n/a |

---

## A - can corrupt the user's railway data, or move a train wrongly

### DW-A1. The rename-path refusal loses what only the running layout knew

`src/org/traincontrol/automationui/AutonomySession.java` line ~2299 (`if (pagesStale) return;` in
`captureFromLayout`), set from `src/org/traincontrol/automationui/LayoutPageEdit.java` line ~193,
reached through `TrainControlUI.resetAutonomySession` (~line 2079). Introduced by `9d864599`.

The fix itself is right about the bug it fixes: capturing with a stale naming duplicated every
placement, and the new test (`testRenameRoundTripThroughTheUIPath`, second test) genuinely fails
without it - I verified the mutation surface holds (see DW-D7). What does not hold is the commit's
justification for refusing rather than repairing: *"nothing is lost by refusing: the rename has
already written the store through saveWithoutReconciling, and a rename is refused while autonomy is
running, so there is nothing in the gap that only the running layout knows."*

That sentence is true about the gap **between the rename and the rebuild** and false about the gap
**between the last capture and the rename**. `captureFromLayout` is the only mechanism that moves a
run's outcomes back into the configuration, and it runs at exactly three places - configuration
switch (`AutonomyViewerPanel.load`), the exit save (`saveState`), and `resetAutonomySession`.
Stopping autonomy captures nothing (`announceRunFinished` fires repaints only; the Stop paths call
`stopLocomotives` and no capture). So after a run, everything the trains did - which block each
locomotive now stands on, the facings learned from where they ended up, the captured timetable
entries and changed globals, all of which ride in the same capture (`points` plus the `globals` copy
at the end of `captureFromLayout`) - lives only in the Layout object until one of those three points
fires.

The sequence that loses it: run autonomy, stop (allowed - `refuseWhileAutonomyRunning` gates the
rename only while trains are moving), rename a page. `resetAutonomySession` tries to capture and is
refused; the follow-on `load(wasRunning, false)` in `layoutRefreshComplete` skips its own capture
because `activeDiagramConfiguration` was nulled by the reset. The configuration then rebuilds from
the store's pre-run placements. The refusal is also page-wide while the staleness is not: only the
renamed page's keys are stale, but placements on every other page are dropped with them.

How it shows itself: after the rename, the diagram draws every locomotive where it stood **before**
the run, while the physical trains stand where the run left them. If the operator notices, it is a B.
If they do not and press Start, it is an A: occupancy is placement-derived (`Point.isOccupied` is
`currentLoc != null`; `isPathClear` walks edges asking exactly that, and nothing at path-choosing
time consults s88), so autonomy will happily route a second train into a block that is physically
occupied by a locomotive the configuration believes is elsewhere.

MT-174 shows Adam renaming pages while actively exercising autonomy, so the flow is not exotic.

**Confidence:** high on the mechanism - the three capture sites, the refusal's breadth, and the
placement-derived occupancy were all read at HEAD. Medium on how often the run-then-rename-without-
intervening-capture flow occurs in practice.

**The fix that preserves both properties:** capture BEFORE the rename rather than refusing after it.
In `duplicateOrRenameCurrentLayout`, a capture immediately before the `LayoutPageEdit` call runs
while naming and store still agree (both hold the old name); `renamePage` then rekeys the captured
entries along with everything else. The `pagesStale` refusal should stay as the backstop it is - it
correctly protects the exit-save path if the window is closed mid-rename-failure - but it should no
longer be the thing that decides whether a run's outcomes survive.

---

## B - wrong behaviour the user would notice

None found beyond the A above's B-grade manifestation. Two things that looked like Bs on first read
were run down and came back clean; they are DW-D8 (the timetable entries do survive rebuilds - the
capture's globals copy carries them) and DW-D4 (Stop genuinely still wins on the strip).

---

## C - correctness or clarity issues that will cause a defect later

### DW-C1. Page DELETE has the exact staleness the rename just fixed, unmarked

`src/org/traincontrol/gui/TrainControlUI.java`, `deleteLayoutMenuItemActionPerformed` (~line 19020).

`9d864599` taught the RENAME to say `session.markPagesStale()`. The delete path was written in the
same family and does not: it calls `session.getStore().deletePage(going)` (which forgets the page's
settings, and logs "forgot N settings"), saves, then finishes through `layoutEditingComplete` -
whose `resetAutonomySession` runs `captureFromLayout` on the OLD session. That session's naming
still contains the deleted page (the graph and pages predate the delete), the running layout's JSON
still contains its points, and the capture's own prune keeps them (`pagesInPlay` is built from the
session's pages, which still include the deleted one; `stillThere` from the old graph, ditto). So
every placement, facing and marking on the deleted page is written straight back into the
configuration the store just forgot it from, and `saveWithoutReconciling` puts it on disk.

How it shows: the "forgot N settings" log is false for the points-side data the moment the reset
runs; `setup.json` carries entries keyed to a page that no longer exists until the next explicit
save's reconcile silently prunes them. I did **not** establish a path by which these orphans reach
the checks or a rebuilt layout before that prune - the builder emits points only from the current
graph - which is why this is a C and not a B. But it is the same defect class as MT-135/OB-092
(stale keys surviving a page operation), sitting one method away from today's fix, and "fix one
site, sweep the siblings" is this project's most-repeated lesson.

Fix is one line - `session.markPagesStale()` beside the `deletePage` call - or the DW-A1 ordering
(capture before the operation), which covers both sites at once.

**Confidence:** high on the write-back happening (read end to end); low on user-visible harm before
the next reconcile.

### DW-C2. The autonomy menu's download offer can click to nothing

`src/org/traincontrol/gui/AutonomyMenu.java` (~line 200, from `65ff3fa9`) offers "download one from
the Central Station" whenever `ui.isRemoteLayout()` is true. The handler it invokes,
`downloadCSLayoutMenuItemActionPerformed` (TrainControlUI ~19195), guards on
`!isLocalLayout() && !this.model.getLayoutList().isEmpty()` - one condition more than the offer
asks. Remote layout with an empty page list (no station answering, nothing downloaded yet) shows a
live menu item whose press does nothing at all: no dialog, no log line. That is the OB-057/OB-090
shape the same day's work named twice - the affordance asking a narrower question than the guard.
Either the offer should also ask `getLayoutList().isEmpty()`, or the handler should say why it
declined.

**Confidence:** high on the code shape; medium on reachability (I did not establish whether the
layout list can be empty while `isRemoteLayout()` holds in a session that gets as far as opening
this menu - if it cannot, this drops to a D).

### DW-C3. `renameOrDuplicate` can still be called wrong and compile

`src/org/traincontrol/automationui/LayoutPageEdit.java`, `renameOrDuplicate(...)`. The commission
asked whether the new parameter list can be called in a wrong-but-compiling way. It can, two ways:
`rename, duplicate, blank` are three adjacent booleans, and `layoutPath, currentLayout,
newLayoutName` are three adjacent Strings - swapping within either group compiles and silently
performs a different operation (swapping the two names performs the rename BACKWARDS, which against
a live store rekeys everything to the name being retired). The commit closed the one trap that had
actually fired (path-vs-diagram disagreement) and closed it well; these two remain, and the callers
that will hit them are exactly the tests this class was extracted to make writable. An enum
(`RENAME/DUPLICATE/BLANK_COPY`) for the mode and/or a small parameter object for the names would
make both unrepresentable. Current callers (the one UI site and the two tests) were read and pass
arguments correctly.

### DW-C4. The refresh-callback guard's count can be satisfied by a comment

`test/regression/testTheWindowAttachesItsRefreshCallback.java`. The MT-153 half of this test was
hardened against exactly this - `0ec8bea8`'s commit message tells the story of the assertion its own
comment satisfied, and the `updateVisiblePoints` check now strips `//` comments first (verified
sound today: the stripped `repairAutonomyLocomotive` slice contains exactly one occurrence, and the
slice boundary lands correctly on the next `@Override`). But the OTHER assertion in the same test -
`source.split("attachAutonomyRefresh\\(", -1).length - 1 >= 3` - counts the RAW source. Today that
is exactly the declaration plus two calls and nothing else (verified), so it currently guards. The
first comment anyone writes containing `attachAutonomyRefresh(` re-opens the hole the test's own
javadoc warns about: delete a call, keep the comment, stay green. One line - count
`withoutComments(source)` instead - closes it.

### DW-C5. Today's "one definition" methods have no production caller

`src/org/traincontrol/automationui/AutonomySession.java`: `hasErrors()` (line ~2831, from
`8ea781fe`) and `arePagesStale()` (line ~119, from `9d864599`) are called by nothing in src -
`hasErrors` only by the new test, `arePagesStale` by nothing at all. The behaviour that shipped is
still consistent: the refusal calls `errorCount()` and the strip counts ERROR findings from the same
`check()` in `refreshDiagramFindings` (it needs the per-page split, so it cannot call `errorCount`
directly) - both sides derive from one source, which is what OB-090 needed. But the commit message's
"the refusal and the strip both ask it" oversells it, and `hasErrors`' extra
`hasBlockingProblems()` disjunction - the documented reason the method exists - is therefore dead in
production. The next person who "unifies" one caller onto `hasErrors` and not the other will
reintroduce the drift the method was written to end. Either wire it or delete it; the javadoc's
reasoning belongs on whichever survives.

---

## D - minor, and the checks that came back clean

### DW-D1. `e45d7241` (any receive port): clean

The default path is byte-identical - `openReceiveSocket()` returns `new DatagramSocket(RX_PORT)`
when the property is unset, at both sites (constructor and the `sendMessage` reopen), so the
one-method consolidation did what its comment claims. Grepped the whole of src for `15730`,
`RX_PORT` and `getLocalPort`: only `NetworkProxy` itself and two comments reference the number, and
nothing anywhere reads the socket's local port. No production code path sets the property.

### DW-D2. `9e92032e` (extraction): faithful

`renameOrDuplicate` was compared statement-by-statement against the pre-image
(`9e92032e^:TrainControlUI.duplicateOrRenameCurrentLayout`). Order is identical: slot capture,
list remove, blank clear, `saveChanges`, list re-insert, store `renamePage`,
`saveWithoutReconciling`/`renamePageOnDisk`, index write with the rename map and the floor. The ten
textual differences are all field-to-parameter rewrites with a counterpart (`this.model.getLayout
(currentLayout)` becomes the `page` parameter used twice, `pageIdFloor()` becomes
`pageIdFloor(session)` with the same session passed). The four refusals stayed in the window, as
documented. Nothing dropped, nothing reordered. (What the extraction did not close is DW-C3.)

### DW-D3. `31b31ff4` / `0ec8bea8` (refresh callback): no deadlock, bounded repaints

The DR-B7 shape was checked from both ends. The callback body is a bare
`SwingUtilities.invokeLater(onPathEvent)` - posting takes the EventQueue's internal lock only, never
a window or layout monitor, so firing from inside `synchronized (this.activeLocomotives)` on a
locomotive thread takes no second lock. When the posted work runs, `repaintTimetable` detects the
EDT and bounces the layout-monitor-taking snapshot onto a fresh thread (its own comment explains
why), and `repaintAutoLocListLite` takes the UI monitor briefly to post again - the EDT never waits
on the layout monitor through this path. Storm risk: each path start/end/milestone spawns one
short-lived thread and one full table rebuild, fanned out per active locomotive in non-atomic route
mode; that is the volume the deleted GraphStream registration produced for years, and events are
sensor-paced, so it is cost, not hazard. The events are not coalesced - worth remembering if a
50-train layout ever exists, not worth changing now. The MT-153 repaint posts (`0ec8bea8`) land on
the same shared method both for rename and delete (`repairAutonomyLocomotive` serves both), so the
sibling is covered there.

### DW-D4. `8ea781fe` (Fix it strip): Stop still wins

Verified structurally: `syncRun` picks `source` as pause-else-stop-else-start, and
`fixing = source == start && lastTotalErrors > 0` - so while anything offers Stop, `fixing` is
false whatever the checks say, and `setFindings` re-runs `syncRun` so an error appearing mid-run
cannot flip an already-painted button. The Fix action routes through `openAutonomyEditor` ->
`openLayoutEditor`, which is the single guarded door. `errorCount()` and the pre-existing refusal
count the same `check()` stream, so offer and guard agree (see DW-C5 for the part that is only
half-wired).

### DW-D5. `1467f978` (CS3 backup): clean, within its stated limits

`this.control != null` guards the test-fixture case, `isCS3()` defaults false for a station that
never answered the probe, the CS2 path is byte-identical, `configDir` exists by that point, both
fetches are tolerated separately, and both URLs (`getCS3MagDBUrl`/`getCS3RouteDBUrl`) predate the
commit and are used by the sync path already. Unverified against hardware, as the commit itself
says. One observation, not a defect: nothing reads `CS3_mags.json`/`CS3_automatics.json` back -
they are backup payload only, which matches MT-170's ask.

### DW-D6. `9bfb1d4d` (apostrophes): ASCII-clean

Byte-checked the diff: every added line in the two bundles is pure ASCII with `\uXXXX` escapes -
the Java-8 mojibake trap this project's notes warn about was not re-tripped.

### DW-D7. The new rename test can fail

`testRenameRoundTripThroughTheUIPath.testARenameDoesNotLeaveLocomotivesInTwoPlaces` asserts its own
preconditions (an active configuration exists, placements are non-zero before the mutation), drives
the UI's own sequence including the capture step that did the damage, and asserts on placement
count, a named duplicate list, and `errorCount` - the operator-visible layer. Removing either half
of the fix (the `markPagesStale` call or the `pagesStale` check) reintroduces the duplicates the
assertions compare against. This one is the shape the day's own lesson asked for. What it does not
cover is DW-A1's loss case, because its running layout agrees with the store, so the refusal costs
nothing in the fixture - a test for DW-A1 would place the fixture's trains somewhere new in the
running layout before the rename and assert the new positions survive into the reloaded
configuration.

### DW-D8. The timetable survives rebuilds - checked and sound

Chased the suspicion that `86acd990` preserved the capture FLAG across `parseAuto` while the
ENTRIES died with the replaced Layout object. They do not: `captureFromLayout` copies every
top-level key except `points`/`edges` into the configuration's `globals` - which includes
`timetable` - `Layout.toJSON` writes it, `fromJSON` restores it, and the store repairs its
locomotive names on rename/delete (`AutonomyCompanionStore` ~1285). The one hole in that chain is
DW-A1: a refused capture drops uncaptured timetable entries along with the placements.

### DW-D9. Scope honesty

Not re-reviewed here, deliberately: the FR-013 conversion internals beyond a skim (own review
exists, `8db330da`); the morning fixes through `1373bea5` (four same-day passes); `HomeStaging`,
`AutoLocomotiveStatus` and `MarklinControlStation` changes from that stretch (same). The
`getPoints`/`getEdges` live-view hazard is real and known - `67ce9f84`/`5ad62001` reverted the
locked copies for documented AB-BA and freeze reasons and filed the concurrent-map answer under
OB-086; nothing new to add beyond agreeing with the revert.

Answers to the commission's specific questions on `9d864599`, beyond DW-A1: the flag is never left
set on the normal path (it lives on a session object that is discarded moments later; `open()` on
the replacement starts false). On the failure path - `writeLayoutIndex` throwing after a successful
rename, the OneDrive case - `layoutEditingComplete` is skipped and the stale session stays live with
captures refused, which is the conservative right answer for that broken state (the error dialog has
already told the operator the save failed). Between the rename and the next `open`, the session is
read by: the exit-save capture (now correctly refused by the flag), the diagram monitor driver
(stopped in `resetAutonomySession`; its pre-reset ticks light tiles from the old graph against the
old on-screen pages, consistently), and `pageIdFloor` (id-based, name-independent). No reader was
found that acts on the stale naming in that window.
