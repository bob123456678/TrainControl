# The release review: four passes over 3.0.0 before it ships

**Status:** open

**Prefix for citing these findings elsewhere:** `REL`

**Reviewed:** branch `autonomy-diagram-r0`, on 2026-09-03, at the commit each pass names in its own
section. Four passes append here in order, each cross-checking the ones before it:

1. **The last day of commits**, read for errors.
2. **The last week of commits**, read for regressions and for anything the first pass missed.
3. **The model as a whole**, read cold with no context.
4. **Regressions against 2.8.1**, and everything left unaddressed by the reviews of 30 August to
   2 September.

**One document, four sections, one identifier space.** A finding keeps its number for life, and a later
pass that reaches the same conclusion as an earlier one says so under the earlier finding rather than
filing a second. That is the whole reason these four append here instead of writing four documents: the
2026-09-02 fan-out produced seven documents and the same defect appears in three of them under three
names.

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|
| A1 | A | Diagram tile silently refuses protecting-signal clicks whenever autonomy is loaded but NOT running - no dialog can ever appear in that state, and the log's "click it again" is false | `LayoutLabel.java:655`, `:1459` |
| B1 | B | The excluded-page filter on the SIGNAL side of `signalsThatAreGone` silences a true warning: a signal on an excluded page really is dropped from the build by `protectingSignalNames` | `AutonomySession.java:3434` |
| C1 | C | `connectingFailed()` / `connectingFinished()` are called off the EDT on all three failure paths out of `init` - the marshalling the old splash's `close()` had was dropped | `MarklinControlStation.java:3878, 3990, 4000` |
| C2 | C | `stationsWithoutMaxLength` gated on `measuresAnyTrack()`, a borrowed precondition: `maxTrainLength` is compared against the locomotive's own length and works with no tile measured | `AutonomySession.java:5369` |
| C3 | C | one.sh's reap comment says "again after the last one"; there is no post-loop reap - the exact V33-C1 gap battery.sh was fixed for | `docs/tools/one.sh:242` |
| C4 | C | `clearAllPlacements` pays 2N full station-index re-derives on the EDT - the DY3-C5 cost, un-swept in the twin bulk action added the same day | `AutonomyEditorPanel.java:6720` |
| C5 | C | `tilesWithALocomotive` javadoc claims "the configuration's own order" - the same false `JSONObject`-order claim CD3-C4 corrected on its twin one method up | `AutonomySession.java:4337` |
| C6 | C | `isPathClear`'s reversal-room comment stack is stale twice: "every segment ... added together" and "the counting is unchanged" both stopped being true when `roomAtTheEnd` landed | `Layout.java:2367, 2422` |
| C7 | C | `clearAllHomes` javadoc says the clearing goes "through `session.setHome(tile, null)`"; the body calls `clearEveryHome()` | `AutonomyEditorPanel.java:6665` |
| C8 | C | `toggleCoordinates`' cross-window rationale went stale when the viewer gate landed; the main-window `repaintLayout()` is now pure cost | `LayoutEditor.java:4875` |
| D1 | D | `refuseAutonomyStartWhileBroken` null-session concern - clean, guarded at line 5243 | `TrainControlUI.java:5243` |
| D2 | D | Start-up notice flow: every path out of `init` either replaces the notice or takes the window down; `display()` has exactly one caller | `MarklinControlStation.java:3835-4042` |
| D3 | D | The new terminus dash matches `barredFromAutonomy`'s own unconditional clause - clean | `AutoLocomotiveStatus.java:90` |
| D4 | D | Eleven `AutonomyChecks.run` overloads and `connected(Point, Point)` removed with no caller left behind - clean | `AutonomyChecks.java`, `HomeStaging.java` |
| D5 | D | Every new message key present in the base bundle, count plumbed as `{2}` - clean | `messages.properties` |
| D6 | D | The `writePointProperty` split: `dirty` stays per write, two callers only, one re-derive - clean | `AutonomySession.java:4394` |
| D7 | D | Staging room-check reorder and the audit's fifth exemption - both verified clean | `HomeStaging.java:1063, 699` |
| D8 | D | The healthy-square gate on the copy checks: compensation exists but by a different mechanism; no divergence constructed, data claim not re-run | `AutonomySession.java:2143` |
| D9 | D | What this pass did not cover | - |
| B2 | B | one.sh takes the SAME lock file as battery.sh and got none of `7acc9837`'s corrections: non-atomic take, bare-MSYS fallback pid, and it cannot read the new `msys:` format - so it can clear or overwrite a live battery's lock | `docs/tools/one.sh:110-138` |
| C9 | C | battery.sh's stale branch takes the lock in two steps after all - `rm -f` then noclobber create - so two shells racing past a stale lock can still both proceed; and the lock is created empty for an instant before its pid is written | `docs/tools/battery.sh:297-305` |
| C10 | C | `7acc9837` broke the unknown arm's own contract: "warns and proceeds" now dead-ends in the noclobber else-arm, which refuses for ever with a message describing a race that did not happen, and nothing ever clears that lock | `docs/tools/battery.sh:252, :307` |
| C11 | C | `checkReversalNeedsLengthRun` says "{2} squares on the way in" but the count includes the turnaround square itself | `messages.properties:1538`, `AutonomySession.java:2018` |
| D10 | D | Pass 2's checks that came back clean: bundle placeholder parity across all eight languages, the OB-166 sweep removal at all three doors, the two staging-busy flags, the C9 name-resolution sweep, the MT-149 self-eviction guard, and five more | below |
| D11 | D | What pass 2 did not cover | - |

---

## Pass 1 - the last day of commits

**Reviewed:** the 43 commits `56c6080e~1..0eda3843`, on 2026-09-03. No tests were run; every claim
below is from reading. Where a claim depends on the operator's real data it says so;
`cs2_sample_layout/` was not opened.

### A1

**The diagram tile silently refuses a protecting-signal click whenever an autonomy layout is loaded
but not running - a state in which no dialog can ever be shown, so the refusal has no way past it at
that door.**

`87b6c10a` gave the tile click the protecting-signal half of the route guard, and the same round
added the worker-side re-check (V32-C5). The re-check runs on the switching worker, for every
accessory click:

```java
// LayoutLabel.java:655
if (!warnedAboutProtection
    && (aboutToClearProtection(tcUI, c.getAccessory())
        || aboutToClearProtection(tcUI, c.getAccessory2())))
{
    tcUI.getModel().logf("layout.warnProtectionArrived", component.getAddress());
    return;
}
```

`aboutToClearProtection` (LayoutLabel.java:1459) gates on `hasAutoLayout()` only. The dialog that
sets `warnedAboutProtection` is inside `if (tcUI.getModel().hasAutoLayout() &&
tcUI.getModel().isAutonomyRunning())` (LayoutLabel.java:413). So in the state *autonomy configured
but idle* - which is ordinary manual operation on this railway, since the active diagram
configuration loads at start-up and `parseAuto` places locomotives onto Points
(`Layout.java:7776`) - the two halves disagree: the dialog can never be shown, `warnedAboutProtection`
is always false, and the worker refuses every click that would set a protecting signal green over a
square the MODEL believes occupied.

Three things make this an A rather than a C:

1. **There is no way past.** The log line says "Click it again if you still want to"
   (`layout.warnProtectionArrived`), and clicking again reaches the same ungated check with the same
   answer, forever, until the placement is removed or autonomy is started. The remedy the message
   promises does not exist in exactly the state that produces the message.
2. **The model's occupancy is stale in exactly this state.** With autonomy idle, driving a train away
   by hand does not clear `Point.getCurrentLocomotive()` - so the refusal fires for trains that are
   no longer there, and the operator's one recovery gesture at the diagram does nothing.
3. **Every twin door gates on running.** The route door: `if (!this.network.hasAutoLayout() ||
   !this.network.isAutonomyRunning()) return null;` (`MarklinRoute.java:439`, "Silent when autonomy
   is not running, which is when routes are most of what this application is for"). The new switch
   keyboard door: `if (!this.model.hasAutoLayout() || !this.model.isAutonomyRunning()) return true;`
   (`TrainControlUI.java:19750`). So the same command is waved through at the keyboard, asked about
   politely by a route, and silently swallowed at the tile - the one-door-of-three shape this very
   round spent several findings removing, reintroduced by one of them.

The fix direction that matches the design: gate the worker re-check on `isAutonomyRunning()` too. The
V32-C5 race it exists for - `refreshOneSignal` driving the signal from an occupancy change in the gap
between dialog and command - only exists while autonomy is processing occupancy at all.

Confidence: high from reading; the state (loaded, idle, placements present) is the default start-up
state of the operator's own railway. What I would have run: a headless fixture with a placed
locomotive, autonomy not started, a click on the protecting signal's tile - expecting the command to
be dropped with only the log line.

**Pass 2, 2026-09-03 - confirmed by a second route.** Rather than compare the two gates side by side,
I read the refusal chain end to end: the worker re-check (`LayoutLabel.java:655-657`) reaches
`aboutToClearProtection` (`:1459`), which reaches `Layout.clearsProtection` (`Layout.java:6229`) and
`protectsAnOccupiedSquare` (`:6184`) - and no method on that chain asks `isRunning()` or
`isAutonomyRunning()` anywhere. The only `isAutonomyRunning()` in `LayoutLabel` is the dialog gate at
line 413, and `askedAboutProtection` is initialised false above it - so in the loaded-idle state the
flag the worker trusts can never have been set. Two independent readings, same conclusion.

### B1

**The signal-side excluded-page filter added to `signalsThatAreGone` (SVN-C6) silences a warning that
was telling the truth.**

```java
// AutonomySession.java:3432
for (TileKey tile : pair.getValue())
{
    if (store.getExcludedPages().contains(tile.getPage())) continue;
```

The commit's reasoning - "a pairing whose station sits on an excluded page finds no tile in the graph
... and reports the station's protecting signal as GONE" - is right for the STATION side (the outer
filter, line 3430): a station on an excluded page is not in the build at all, so nothing is
unprotected and the warning was noise.

It is wrong for the SIGNAL side. `protectingSignalNames()` (AutonomySession.java:2491) resolves each
signal tile through `graph.getTiles()`, and `TileGraph.getTiles()` documents itself as "every tile in
the graph, excluded pages already left out" (TileGraph.java:1029). So a pairing whose signal sits on
an excluded page - station included - is dropped from the built configuration: the running layout
never hears of that signal, and the platform genuinely is unprotected on that approach. The warning
the inner filter now suppresses was reporting exactly that state. It also contradicts the sentence
four lines above it: "a station paired to two signals of which one has gone is protected on one
approach and not on the other, which is exactly the state worth warning about."

Reachability: the commit's own comment concedes the shape is reachable ("reachable the moment one
does" - a pairing crossing an excluded page), and excluding a page AFTER pairing across it is one
menu action. Not reachable on the operator's data today, which is why this is a B and not an A. The
fix: delete the inner filter (keep the outer), or warn with a message naming the real cause - "this
signal's page is excluded, so the protection will not be built".

**Pass 2 - confirmed from the resolution side.** `protectingSignalNames` (`AutonomySession.java:2485`)
resolves every signal tile through `graph.getTiles().get(tile)` and drops it silently on a miss
(`if (signal == null ...) continue;`), and `TileGraph.getTiles()`'s contract is "every tile in the
graph, excluded pages already left out" (`TileGraph.java:1029`). So a pairing whose signal sits on an
excluded page really is absent from the built configuration, and the inner filter at
`AutonomySession.java:3434` suppresses the one warning that would have said so.

### C1

**`connectingFailed()` is called off the event thread on every failure path out of `init`.**

`showConnecting` is carefully marshalled - `SwingUtilities.invokeAndWait(() ->
showing.showConnecting(...))` (MarklinControlStation.java:3841). Its three teardown counterparts are
not:

- MarklinControlStation.java:3878 - constructor throw: `if (ui != null) ui.connectingFailed();` on
  the caller's thread;
- :3990 - `latch.await()` interrupted: same;
- :4000 - build failed: same.

`connectingFailed` runs `setContentPane`, `revalidate`, `repaint` and `setVisible(false)` on a
realized, visible frame from a non-EDT thread. The old `StartupSplash.close()` these replace did
`if (isEventDispatchThread) run(); else invokeLater(go);` - the marshalling was dropped in the
rewrite. Swing does not throw for this, and on these paths the process usually exits or shows an
error dialog next, so the practical exposure is a paint race on a window being torn down - hence C,
not B. A one-line `invokeLater` wrap at each site restores the old care.

**Pass 2 - confirmed, and the success path checked too.** All three `connectingFailed()` sites run on
`init`'s own thread, as filed. The happy path is clean: `display()` - the only caller of
`connectingFinished()` other than `connectingFailed` itself (`TrainControlUI.java:7290`) - is posted
with `SwingUtilities.invokeLater` (`MarklinControlStation.java:4021`), so C1's scope is exactly the
three failure paths and nothing wider.

### C2

**`stationsWithoutMaxLength` was gated on `measuresAnyTrack()` (SVN-C3), a precondition borrowed from
a different quantity.**

```java
// AutonomySession.java:5369
if (!store.measuresAnyTrack()) return out;
```

Adam's condition - "a railway that measures nothing has decided not to model lengths" - was given for
`reversalsWithoutLength`, where the notice and the gate are about the same data: tile lengths.
`maxTrainLength` is not that data. `Point.validateTrainLength` (Point.java:910) compares the
station's typed maximum against `loc.getTrainLength()`, the locomotive's own authored length; no
tile length enters into it, and the feature works on a layout with zero tiles measured. An operator
who authors train lengths and station maxima but has never measured track now gets no "this station
takes any length" notices at all - the feature they are actively using goes unaudited. This is the
lifted-precondition shape: the sentence was true where it was written and lost its subject on the
way over. Narrow (requires maxTrainLength-only usage) and advisory-only - C. A faithful gate would
be "any station has a maximum, or any train has a length".

**Pass 2 - confirmed, with one addition for whoever dispositions it.** The gate and its borrowed
javadoc are exactly as filed (`AutonomySession.java:5354-5370`), and `Point.validateTrainLength`
(`Point.java:910`) reads no tile length. The addition: the C-sweep report of 2026-09-03
(`2026-09-03-c-sweep-report.md`, "What changed behaviour") already put this change in front of Adam -
but in the sibling's terms only: "thirty station-capacity warnings leave the notice list on a railway
that records no track lengths. That is your own condition". The point C2 makes - that `maxTrainLength`
is compared against the locomotive's authored length and works with nothing measured - is not in that
report, so a ruling made from it would be made without the argument.

### C3

**one.sh claims a reap it does not have.**

```
# docs/tools/one.sh:242
# Only THIS RUN's leftovers, before the class and again after the last one (V33-C1, V33-C2).
reap
```

`reap` is called exactly once, at the top of the class loop (call sites: the definition at line 171
and this line 243 - nothing else). There is no post-loop reap, so the last class's leftover JVM is
never cleaned - the precise V33-C1 defect whose fix battery.sh received in this same range ("AND
AFTER THE LAST CLASS (V33-C1)", battery.sh line ~473). Because `reap.ps1` matches the run id whole
and the id embeds the shell's PID, no LATER run ever reaps it either; the next run's start-of-run
probe then refuses with the message the comment itself calls misleading. Harness only, so C - but
the comment asserts the fix is present, which is worse than its absence.

**Pass 2 - confirmed.** `reap` appears in `one.sh` exactly twice: the definition (`:171`) and the one
in-loop call (`:243`). There is no post-loop call, and the comment above line 243 still claims "again
after the last one". See also B2 below: this is the second time in the range that one.sh missed a
correction its sibling got, and `9fec3b71`'s own message ("one.sh had none of the five corrections,
because nobody could see it") names the pattern.

### C4

**`clearAllPlacements` pays the exact cost `clearEveryHome` was built to avoid, in the twin gesture
added by the same round.**

`AutonomyEditorPanel.clearAllPlacements` (AutonomyEditorPanel.java:6720) loops
`session.placeLocomotive(tile, null)` per placed square. That branch of `placeLocomotive`
(AutonomySession.java:3951) makes TWO `setPointProperty` calls (the placement, then the facing), and
each `setPointProperty` now ends in `deriveStationIndex()` - "a full builder construction, on the
event thread" (DY3-C5's wording). So clearing N placements is 2N full rebuilds on the EDT, for a
gesture that exists "precisely because clearing them one at a time is too many". The homes side got
`clearEveryHome()` with one re-derive; the placements side, added in the same commit series
(MT-257), did not. No wrong result - the derive is idempotent - so C for cost, plus the
fix-one-site-sweep-the-siblings note.

### C5

**`tilesWithALocomotive`'s javadoc makes the claim its twin was corrected for, one method up.**

AutonomySession.java:4337: `@return the squares, in the configuration's own order` -
`tilesWithAHome` directly above carries the CD3-C4 correction spelling out why that is false: the
points live in a `JSONObject`, which is a `HashMap`, so the order "is stable for a given set of keys
and unrelated to the order they were written". The same walk over the same map cannot have the order
one javadoc denies and the other claims. Harmless to today's callers (count and clear), hence C;
the fix is the sentence the twin already has.

### C6

**The reversal-room comment stack in `isPathClear` survived the change it describes.**

Layout.java:2367: "WHAT IT MEASURES. Every segment leading up to the reversal, added together." -
and Layout.java:2422: "The counting is unchanged, including both of the unsoundnesses above."

Both were true when `measuredRoomToReverseInto` was extracted (TCX-A2, `87b6c10a`) and stopped being
true later in the same range, when Adam's ruling landed `roomAtTheEnd`
(`GraphReducer.roomAfterTheLastSwitch`, `Edge.crossesASwitch`, the builder emitting the key): on any
builder-emitted configuration the sum now stops at the last switch and counts only the stretch after
it (Layout.java:6288-6303). Unsoundness 2 in the same block ("This adds the whole path ... a
10 + 1 + 2 path admits an eight-unit train into three units of room") is largely what the ruling
fixed; unsoundness 1 still holds only for switchless routes. This is the one call site the guard
has, in the method a reader meets first, saying the arithmetic is something it no longer is - the
same fault (a claim corrected everywhere but here) this very comment block records happening to its
own "inert" sentence. Comment-only, hence C.

### C7

**`clearAllHomes`'s javadoc names a mechanism the body does not use.**

AutonomyEditorPanel.java:6665's javadoc: "Through `session.setHome(tile, null)` rather than by
writing the property, because that is the door the per-square menu uses". The body calls
`session.clearEveryHome()`, which writes through `writePointProperty` directly - the bulk door DY3-C5
added, argued for by the body's own comment thirty lines down. The two paragraphs of one method
disagree about which door is used; the second is right. Behaviour is unaffected (`setHome(tile,
null)` and the bulk write differ only in the per-write re-derive, and the elsewhere-sweep is a no-op
for null) - the first paragraph needed updating when the bulk door landed. C, comment-only.

### C8

**`toggleCoordinates`'s cross-window rationale went stale when the viewer gate landed.**

LayoutEditor.java:4875: "The main window draws the same diagram, and its grid is built from the same
preference - so it is redrawn too rather than left showing the other answer until something else
rebuilds it" - followed by `this.parent.repaintLayout()`. Later in the same range, Adam's "they are
visible in the track diagram viewer when they shouldn't be" added the gate `master instanceof
LayoutEditor` to the ruler block in LayoutGrid, so the main window never draws the numbers and there
is no "other answer" for it to be left showing. The `repaintLayout()` is now a full rebuild of the
main window's diagram per Control+K, bought by a sentence that is no longer true. Cost and comment
only - C.

### D1

**Withdrawn concern: `refuseAutonomyStartWhileBroken` NPE on a null session.** The widened guard
calls `getAutonomySession().hasErrors()` bare (TrainControlUI.java:5257), which looked like an NPE
for a window with no session - but line 5243, `if (getAutonomySession() == null) return false;`,
stands three lines above it. Clean; recorded because the later `asked == null ? 0 : ...` at line
5275 implies a doubt the earlier line already answers.

### D2

**The start-up notice flow checked clean.** Every path out of `init` disposes of the notice: a
constructor throw and a failed build call `connectingFailed()` (window down, menus back), an
interrupted `await` does the same and rethrows, and the success path's `display()` calls
`connectingFinished()` as its first statement. `display()` has exactly one caller
(MarklinControlStation.java:4024), so `takeTheKeyboard`'s focus listener is added once, and
`showConnecting`/`connectingFinished` cannot double-run (both re-entry-guarded on
`contentBehindTheNotice`). The window is packed by `initComponents` before `showConnecting`, so the
notice shows on a full-sized frame even on a first run with no saved bounds. The
grey/ungrey-vs-state interplay (a menu state-disabled between grey and ungrey being wrongly
re-enabled) is theoretically present but unreachable: the only writers of top-level menu state run
either from user actions the greyed bar prevents, or inside `display()`'s own repaint after the
ungrey.

### D3

**The dash checked clean.** `AutoLocomotiveStatus.notChosenByAutonomy`'s new clause `(loc != null &&
p.isTerminus() && !loc.isReversible())` (AutoLocomotiveStatus.java:90) matches the rule it mirrors:
`Layout.barredFromAutonomy` refuses a terminus to a non-reversible locomotive unconditionally
(Layout.java:4086), with no turning-route escape - that escape exists only in staging's `mustBackIn`
search. The dash and the rule agree.

### D4

**The removals checked clean.** `AutonomyChecks.run` has exactly one caller left,
`AutonomySession.check()` (AutonomySession.java:3660), and no test calls it directly. The
two-argument `HomeStaging.connected` is gone with no caller remaining - all three call sites pass
`mustBackIn(...)` explicitly (HomeStaging.java:1635-1650).

### D5

**The new message keys checked clean.** Every key the range's code reads
(`route.warnCommandsRemovedForDeletedLocomotive`, the four `autosetup.ui.left*` keys,
`checkCopyReachesNothing`, `checkReversalNeedsLength` and its `Run` variant,
`layout.warnProtectionArrived`, `ui.warnCouldNotTakeTheForeground`,
`autolayout.warnHomeSquareAssignedTwice`, the Bulk Tools and coordinates keys,
`route.ui.infoAlreadyRunning`, `layout.ui.confirmAccessoryProtecting`,
`errorCannotBuildDetail`/`...One`, `ui.splashConnecting`) is present in the base bundle, and the
`checkReversalNeedsLengthRun` message's `{2}` matches `Finding.getCount`'s documented rendering
slot.

**Pass 2 - the translated-bundle gap closed (see D9).** Placeholder parity was checked
programmatically across all eight bundles: for every one of the 1,888 keys, the SET of `{N}`
placeholders in each translation equals the base bundle's. (Twelve values legitimately reorder
`{0}`/`{1}` for word order - `loc.settingFunction`, `loc.settingSpeed`,
`loc.ui.logSetCustomFunctionIcon` in five languages - which an ordered comparison flags and a set
comparison correctly does not.) Key sets are symmetric at 1,888 per bundle, every byte is ASCII, and
no value contains a straight apostrophe - so `2ca94bdd`'s and `e22ea093`'s shell-damage repairs held,
and `MessageFormat` has nothing to eat. `checkReversalNeedsLengthRun` is present in all eight.

### D6

**The `writePointProperty` split checked clean.** `dirty = true` stayed inside `writePointProperty`
(so a bulk clear still marks the configuration unsaved), only `deriveStationIndex()` moved out, and
the method has exactly two callers - `setPointProperty` (derives per write, as before) and
`clearEveryHome` (derives once at the end). Nothing that used to happen per write was lost.

### D7

**Two staging changes checked clean.** First, the room-check move (WK3-B2): the refusal `continue`
now sits before the `seen` write AND before the enqueue, exactly as the old post-`seen` refusal
skipped both, so no state is recorded or expanded for a refused arrival and the longer approach
survives to be tried - the fix does what its comment says and nothing more. Second, the audit's
fifth exemption (SVN-C5): `mustBackIn(loc, p)` (HomeStaging.java:1611 - terminus plus
non-reversible, no route knowledge) skips only pairs where the planner's own deliberate divergence
applies; agreement cases were never counted anyway, and the acknowledged cost (a planner refusing
such a pair for the WRONG reason is invisible) is stated at the site.

### D8

**The healthy-square gate on the copy checks - compensation verified as far as reading allows.**
`badCopies` and `destinationCopiesReachingNoStation` now stay silent where every copy of a square is
stuck, on the stated ground that the square-level checks say it already. The compensating checks run
on a different mechanism (the reducer's `(tile, side)` walk in `reachableTiles`, plus the
per-arrival `trapped` set) than the copies do (the BUILT graph). I could not construct a square that
is all-copies-stuck yet passes `STATION_UNREACHABLE`, `STATION_REACHES_NOTHING` and
`ARRIVAL_TRAPPED` together - the double-curve shape that first suggested one is per-arrival and
lands in `trapped` - but the equivalence is data-supported ("named nine of the ten squares" on the
operator's own railway), not structural, and was not re-run because running anything is out of scope
for this pass. If a later pass can execute: compare the three checks' union against `badCopies`
un-gated, on the frozen operator layout - one command.

### D9

**What this pass did not cover.**

- **Nothing was executed.** Every claim above is from reading; the two findings that most want a
  fixture (A1, B1) say what the run would be.
- **`cs2_sample_layout/` was not opened** (rule 3), so commit `ad33d1a6` ("Adam's layout") was
  reviewed only through its message and its `test/operator_layout` mirror.
- **The test-file diffs (about 3,800 added lines) were not line-audited.** They were consulted where
  a product claim depended on them (the battery wiring in build.xml, the reversal fixtures), not
  reviewed for defects of their own.
- **The translated bundles** were checked for key presence only, not for placeholder-count parity
  with the base bundle across all eight languages.
- **The `docs/reviews/*` documents added by the range** (some 8,000 lines of prior-review text) were
  treated as history, not re-verified.
- **The `roomAtTheEnd` arithmetic** (`GraphReducer.roomAfterTheLastSwitch` against
  `Layout.measuredRoomToReverseInto`'s edge-sum splice) was traced by hand for double-counting at
  edge boundaries and found consistent - each edge's length includes its end tile and not its start,
  and `roomAtTheEnd` includes the end tile, so the splice counts every square once - but this is
  exactly the kind of claim a property test settles, and mine is a reading.

---

## Pass 2 - the last week of commits, for regressions

**Reviewed:** the 192 commits `453a3ef4..7acc9837`, on 2026-09-03. No tests were run; every claim
below is from reading. (`git rev-list --count` reports 191 for that range; the header keeps the
briefing's number, this line records the measured one.)

**Method, and how this pass divided the week.** The range is unusually well covered from inside:
`WK3` (the 2026-09-02 week-of-commits review) read the same week closely up to `cf048f9b`, the
2026-08-30 to 2026-09-02 fan-outs (`LE`, `RC`, `SG`, `SVN`, `DY3`, `TCX`, `D24`, four-plus validation
rounds) covered their days, and Pass 1 read the last day. So this pass spent its time on three
things: (1) the four commits after Pass 1's end (`0eda3843..7acc9837`), which no reviewer had read;
(2) cross-commit regression shapes - a rule enforced at one door and not its sibling, a fix applied
to one of two twins - checked in the FINAL tree rather than per diff, because a sweep that was
complete on the day it landed can be un-swept by the next commit; (3) the gaps every earlier pass
declared: the translated bundles beyond key presence, and the harness scripts. Where an in-range
review already found something, its identifier is cited rather than a new one filed. Findings
continue Pass 1's numbering; confirmations of Pass 1's findings are recorded under those findings
above, not re-filed.

**Regression shapes checked and found clean are in D10**; they include the ones the briefing named
(deleted-class capabilities, guards made unreachable, one-door rules). The honest headline: the regressions
this pass found are all in the harness, in and around the range's final commit - the product code's
one-door defects of the week (`87b6c10a`'s tile door, `WK3-B1`'s aspect half) were already found by
Pass 1 and WK3, and this pass confirmed rather than extended them.

### B2

**one.sh takes the same lock file as battery.sh and got none of `7acc9837`'s corrections - so every
failure mode that commit closed at the battery door is still open at this one, on the shared lock.**

`7acc9837` (the range's final commit) hardened battery.sh's lock: taken atomically with `noclobber`
("which of two racing shells gets it is a question the filesystem answers rather than one this
script answers twice", battery.sh:290-303), and the no-`/proc` fallback pid written with its
namespace (`LOCK_PID="msys:$$"`, battery.sh:200) so a reader cannot mistake an unresolvable pid for
a dead one - "FV2-A1's failure mode surviving in the branch nobody exercised".

one.sh holds the SAME lock at the SAME path - its own comment says so and says why ("AND THE SAME
LOCK battery.sh takes, at the same user-wide path", one.sh:105-110) - and still has the old shape at
every point the commit fixed:

1. **The take is still test-then-write.** one.sh reads the lock, spends a `powershell.exe` start on
   the liveness question, and then writes with a plain overwrite:

   ```
   # one.sh:138
   echo "$LOCK_PID" > "$LOCK"
   ```

   That is the exact window `7acc9837`'s comment names ("between the old test and the old write sat
   a powershell start of a few hundred milliseconds, which is exactly the window two runs launched
   together spend in parallel"). Two one.sh runs launched together both pass; a one.sh and a
   battery.sh launched together both pass whenever the one.sh's write lands after the battery's
   atomic create - the plain `>` does not care that the file now exists. The JVM probe both scripts
   run does not close this: it sees test JVMs, and the overlap window is the compile, which is where
   the 2026-09-01 double-run actually overlapped ("both of them in javac", battery.sh:112) and which
   battery.sh's own comment says the lock exists to cover ("AND THE LOCK IS WHAT COVERS THE
   COMPILE", battery.sh:158).

2. **The fallback still writes a bare MSYS pid** (`''|*[!0-9]*) LOCK_PID=$$ ;;`, one.sh:115) - the
   FV2-A1 failure mode `7acc9837` fixed one file over: a lock one.sh writes from a shell with no
   `/proc` is a number `Get-Process` is guaranteed to call dead, so a battery started from a
   different MSYS runtime clears it while the one.sh is still compiling.

3. **one.sh cannot read the format battery.sh now writes.** A battery.sh lock written as `msys:NNN`
   reaches one.sh's checker as `Get-Process -Id msys:NNN` (a binding error, output empty, stderr
   dropped) and then `kill -0 "msys:NNN"` (invalid pid, fails), so `ALIVE` is never `yes`
   (one.sh:118-136) - and one.sh then treats a LIVE battery's lock as clear and overwrites it at
   line 138.

Legs 2 and 3 need a shell with no `/proc/$$/winpid` - `7acc9837`'s own words, "the branch nobody
exercised" - and are latent on the machine in use, where `/proc` works. Leg 1 is live everywhere and
is the un-swept half of the exact defect the commit fixed. B rather than C, despite being harness
code, because the failure it leaves open is the 2026-08-30 incident class - two runs redirecting the
per-user Preferences store at once, which is how the operator's real railway was damaged - and B
rather than A because it takes two runs started within the same few hundred milliseconds.

The range itself documents this sibling being missed before: `9fec3b71`, "TV2: one.sh had none of
the five corrections, because nobody could see it", and Pass 1's C3 (the reap one.sh did not get).
This is the third instance of the same shape in one week, on the same file. The fix is the same
treatment battery.sh got, or better: fold the lock take into one shared, sourced helper so the two
doors cannot drift again - two copies of a lock protocol on one lock file is how this class of
finding regenerates.

**FIXED 2026-09-03.**  Confirmed by reading, all three legs, and all three are closed by giving `one.sh`
the same three pieces `battery.sh` has: a holder-state function that reads `msys:NNN` as well as a bare
winpid and asks each of the tool that can answer it, a `mv` take-over for a lock nobody owns, and a
`noclobber` create with the pid written through a temp file.

The finding's own point is the receipt: this is the third instance this week of `one.sh` missing a sweep
its sibling got, and the third leg was CREATED by fixing the first one file over.  The two takes are now
word for word the same, which is the only arrangement that stops it happening a fourth time.

Exercised: a run with no lock takes it and releases it; a run against a live lock refuses and names the
pid; and every arm of the state function was driven in isolation - alive refuses, dead is taken over,
an unresolvable `msys:` lock and an empty lock both warn and proceed.

### C9

**battery.sh's stale branch takes the lock in two steps after all.**

```
# battery.sh:297-305
if [ -n "$STALE" ]
then
    rm -f "$LOCK"
fi

if ( set -o noclobber; : > "$LOCK" ) 2>/dev/null
then
    echo "$LOCK_PID" > "$LOCK"
```

`7acc9837`'s point was that "create it only if it does not exist" must be one operation the
filesystem decides. In the stale case it is two again: two shells racing past the same stale lock
(a battery killed with SIGKILL or a machine crash leaves one - the INT/TERM/EXIT traps cover
everything gentler) can interleave as rm-A, create-A, rm-B - deleting A's fresh lock - create-B, and
both proceed; A's EXIT trap then deletes B's lock behind it. The window is the sub-millisecond
between one shell's adjacent `rm` and `: >`, vastly narrower than the few-hundred-millisecond one
the commit closed, which is why this is a C and not a B. A second, smaller gap in the same block:
the lock exists EMPTY between the `: >` and the `echo` one line later, and a reader in that instant
gets `HELD=""`, which parses to `unknown` and proceeds with a warning. Both close the same way: take
the stale lock over atomically - `mv "$LOCK" "$LOCK.stale.$$"` and proceed only if the `mv`
succeeded - and write the pid into a temp file that is `mv`ed into place, so the lock never exists
without its content.

**FIXED 2026-09-03.**  Both halves.

The stale lock is taken OVER with `mv` rather than removed and re-created, so two shells racing past the
same stale lock cannot interleave: exactly one `mv` succeeds.  And the pid is written to `$LOCK.mine.$$`
and moved into place, so the lock is never present and empty for a reader to find - which was this
finding's second half and would have shown up as a spurious `unknown`.

### C10

**`7acc9837` broke the unknown arm's own contract, four lines below the comment stating it.**

```
# battery.sh:252 (comment)
# An MSYS lock that `kill -0` could not resolve is UNKNOWN rather than dead: this shell may simply
# be a different MSYS runtime.  The unknown arm warns and proceeds, which is what it is for.
```

The unknown arm does warn - and then falls through to the new noclobber create with `STALE` empty,
so nothing removes the file, the create finds it present and fails, and the else-arm exits 2 with:

```
# battery.sh:307
echo "*** ANOTHER BATTERY TOOK THE LOCK WHILE THIS ONE WAS CHECKING ***"
```

That message describes a sub-second race that did not happen; what actually happened is a lock
nobody can resolve, possibly days old. "Wait for the other one and run this again" is a remedy that
never works - the holder is unresolvable, so every retry takes the same arm, for ever, and the only
way past is deleting the lock file by hand: the exact learned behaviour the script's own probe
comment forbids ("nobody has to delete anything to get past it, so nobody learns to delete things to
get past it", battery.sh:96). Before `7acc9837`, the unknown arm's fall-through reached a plain
overwrite and genuinely proceeded; the commit changed the fall-through's meaning without re-reading
the arm that relied on it. Reachable only via a `msys:`-format or cross-runtime bare-pid lock (see
B2 leg 2), so narrow - C. The fix: in the unknown arm, either proceed by taking the lock over
atomically (C9's `mv`) after the warning, or refuse with a message that names the real situation
and the real remedy, including which file to delete.

**FIXED 2026-09-03**, and this one was mine to answer: the commit changed what the fall-through meant
without re-reading the arm that relied on it.

The unknown arm warns and takes the lock over, which is what its comment says it does.  The message that
described a race that did not happen is gone from that path; it survives only where a race really did
happen - the `noclobber` create failing - and says so in those words.

### C11

**The OB-171 run message counts the turnaround square among "squares on the way in".**

```
# messages.properties:1538
autosetup.ui.checkReversalNeedsLengthRun=Trains turn round at {0}, and {2} squares on the way in
have no length recorded...
```

The count it renders includes the reversal square itself: `reversalsWithoutLength` adds the
turnaround tile to the same set as the approach squares (`AutonomySession.java:2018`,
`if (store.getTileLength(tile) <= 0) missing.add(tile);`), and `unmeasuredAfterTheLastSwitch` adds
`edge.getEnd()` - the same square - as its first candidate (`GraphReducer.java:1151`). So a
turnaround with no length and one unmeasured approach square reads "2 squares on the way in", when
one of the two is the square at `{0}` itself. Cosmetic and only in the plural message - the
single-square case takes the singular key, which speaks of "its track" and is right. The fix is
wording ("this square and the way in"), in all eight bundles.

### D10

**Regression shapes checked and found clean.** Each of these is a specific way the week could have
broken something that worked before, checked in the final tree:

- **The deleted-capability check.** `HomeLocomotiveMenu` lost ~440 lines (`59b2db48`); the home
  editor is reachable in `AutonomyEditorPanel` (`homeChoices`, `:3553`, `session.setHome` `:3606`)
  and the surviving `addReturnHomeItem` has its caller (`LayoutRightclickAutonomyMenu.java:237`).
  The graph window's removal left the graceful stop available - `gracefulStop` is a bound button
  wired through `bindRunButtons` (`TrainControlUI.java:3557`) - so neither capability `c5b58dac`
  worried about is gone from the UI. (`confirmExclusion`'s "clear the home too" question is still
  nobody's; that is recorded in `c5b58dac`'s own message and left as the decision it called it.)
- **The OB-166 sweep removal is symmetric.** `fbc19cb9` removed `refreshAllProtectingSignals` from
  all three doors (`runLocomotives`, the timetable, `executePath`), not one; the method survives
  public solely for its regression tests, which pin all three removals by mutation
  (`testBothProtectingSignalsAreThrown.java:305`).
- **The C9-of-`c5b58dac` name-resolution fix was swept.** The route door (`MarklinRoute.isOneOf`)
  and the layout door (`Layout.protectsAnOccupiedSquare:6184`) both resolve protecting-signal names
  through `getAccessoryByName` rather than comparing strings; the tile and keyboard doors reach the
  layout door, so no consumer is left on the raw comparison.
- **The two staging-busy flags cannot disagree.** `TrainControlUI.stagingFlowActive` and
  `Layout.stagingInProgress` are set together before planning and cleared together in the worker's
  `finally` (`TrainControlUI.java:20966-20971`, `:21057-21060`); `isAutonomyBusy` and the model's
  `isAutonomyRunning` therefore answer about the same window from their two sides.
- **`getBarredArrivals`'s filter mutates a copy.** The `retainAll` at `AutonomySession.java:2517+`
  looked like it pruned the store in place; `AutonomyCompanionStore.getBarredArrivals(tile)` builds
  a fresh set per call (`AutonomyCompanionStore.java:101-126`), so no stored restriction is lost by
  being read.
- **The MT-149 guard holds under rename.** `sanitizeMultiUnits`'s self-skip uses object equality
  (`Layout.java:5739`), and a rename mutates the same identity-hashed `Locomotive` in place, so the
  skip matches after the rename - which is the case it was written for.
- **`049a3fc8`'s signature change is complete.** `reversalsWithoutLength()`'s move from `Set` to
  `Map<TileKey, Integer>` has its one caller updated, the `measuresAnyTrack()` gate survived the
  rewrite (`AutonomySession.java:2008`), and `describe(...)` takes varargs in both panels so the new
  count argument breaks neither.
- **The run-button listener cannot double-attach** - guarded by `listeningToRunButtons`
  (`TrainControlUI.java:3559`), so reloading a configuration re-hands the buttons over (the fix the
  comment describes) without stacking listeners.
- **`maxTrainLength` export/import is symmetric** after IPR-A1: written by `Point.toJSON` when
  nonzero (`Point.java:978`) and read back by `parseAuto` (`Layout.java:7508-7512`).

### D11

**What this pass did not cover.**

- **Nothing was executed** (rule 1). The bundle checks in D5's addendum were text comparisons over
  the `.properties` files, not a run of `testMessageBundles`.
- **The middle of the week was read through its own reviews, not re-diffed.** Commits between
  `c5b58dac` and `cf048f9b` were sampled where a regression shape pointed at them; the in-range
  reviews (`WK3`, the fan-outs, the validation rounds) were trusted for the rest. A defect those
  rounds and this pass's sampling both missed is still open ground - Pass 3's cold read is the
  right net for it.
- **The rendering commits** (`LayoutGrid`, `StationCaption`, `LocIconCropDialog`, the icon work -
  roughly fifteen commits) were checked only for structural regressions (the OB-167 cross rule, the
  caption store's keying); their pixels were not reviewed, and `WK3` declared the same gap. They
  are, however, the code Adam sees most and OB-files fastest.
- **`cs2_sample_layout/` was not opened** (rule 3); `4d315aae` and `1ea19359` were reviewed through
  their messages only.
- **The parity harness under `docs/tools/parity/`** was not reviewed (its lockless-ness is already
  `SVN-C10`, dismissed).
- **The triage app and `docs/manual-tests/` tooling** were not reviewed beyond the commits' own
  messages.
- **The test-file diffs were not line-audited** - consulted only where a claim depended on whether a
  rule was pinned (the OB-166 mutation tests, the MT-149 test, `testMessageBundles`' apostrophe
  rule).
