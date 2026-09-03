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
| A2 | A | The diagram flow silently wipes every placed locomotive's train length, reversibility and function slots on each configuration load: capture strips placements to name-only, the builder emits them verbatim, and `parseAuto` resets what a placement omits - the tail-clear, reversal-room and platform-length guards then run on zeroes the operator did not choose | `AutonomySession.java:3051`, `Layout.java:7718/7744/7779/7818` |
| D12 | D | Pass 3's checks that came back clean: edge-occupancy count balance, the timetable's sequential ordering, `firstClearRoute`'s turned/straight key on the mustBackIn refusal, the builder's portal/exit-side cases, the UI lock-order discipline, and five more | below |
| D13 | D | What pass 3 did not cover | - |
| B3 | B | `AUTO_LOAD_AUTONOMY` defaulted from **false** to **true**, so a 2.8.1 user who never ticked the box now loads a configuration on every start - and the comment that justifies the flip ("nothing here for the default to put at risk") is refuted by A1 and A2 of this document, which are both defects of the loaded-and-idle state | `TrainControlUI.java:923-928` vs `master:704` |
| B4 | B | `R28-B2` ("Export Current Graph" unreachable) was withdrawn against `AutonomyMenu.java:326`, which is the Export **Configuration** item - a different artefact the finding had already excluded. The derived-graph export is still behind `isDebug()`, which needs a two-argument command line. The finding stands | `AutonomyMenu.java:326, :445`, `TrainControl.java:23` |
| C12 | C | RG3-C5's correction landed on the Readme and not on its twin: `layout.ui.tooltipGrowDiagram` / `tooltipShrinkDiagram` still say "a row at the top and bottom" and "the same three", in all eight bundles | `messages.properties:1916-1917` |
| C13 | C | `CommandRow.canBeACondition`'s javadoc says `AUTO_LOCOMOTIVE` is not offered and "the editor cannot yet build the row its documentation illustrates"; the body offers it and the editor builds it. `defaultSettingFor`'s comment for the same kind is contradicted by the same lines | `CommandRow.java:239-273`, `RouteEditorFrame.java:3415-3419` |
| C14 | C | Two prefix typos in the 2026-09-02 first validation put an open B and an open C on record as settled: `RGN-B2` and `RGN-C1` are written where `R28-B2` and `R28-C1` are meant | `2026-09-02-first-validation.md:576, :579` |
| C15 | C | Eighteen methods in the autonomy, diagram and route UI have no caller anywhere in `src/` or `test/` - and the scan that found them settles `RG3-D2`'s stated blind spot: none is the only door to a capability | `AutonomyEditorPanel.java:2084`, `TrainControlUI.java:24593`, and sixteen more |
| C16 | C | DAY-C4's false claim has been copied into `Edge.release`'s own javadoc - which is the authority the `Layout` comment cites - so the finding now has two sites and the newer one endorses the older | `Edge.java:452-458`, `Layout.java:2947-2949` |
| D14 | D | Pass 4's checks that came back clean: the menu-builder key diff, the keyboard-shortcut diff, the twelve autonomy settings, the eighteen deleted bundle keys, `locomotiveRenamed`'s fold into the base class, `CS2File`'s API, `edgesAreEmpty`, the uncalled-method scan against RG3-D2's stated blind spot, and the triage of the 75 keys new in 3.0.0 that nothing reads | below |
| D15 | D | What pass 4 did not cover | - |

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

**FIXED 2026-09-03.**  Confirmed by reading before fixing: the dialog is gated
`hasAutoLayout() && isAutonomyRunning()` and `aboutToClearProtection` asked `hasAutoLayout()` alone, so
in the loaded-and-idle state - the ordinary one - no dialog could be shown, `warnedAboutProtection` was
always false, and the worker refused every such click for ever.

`aboutToClearProtection` asks `isAutonomyRunning()` now, which is what the route door and the switch
keyboard both ask, and the reason is written at the return: the race this guard exists for only happens
while autonomy is processing occupancy at all.

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

**FIXED 2026-09-03.**  The signal-side filter is gone and the asymmetry is written where it was made: a
pairing whose STATION is on an excluded page is not in play, and a signal on one really is dropped from
the build, so the warning was true.

`testASignalOnAnExcludedPageIsStillReportedGone` covers it - a station in play, its signal on a page
that is then switched off.  Mutation-confirmed, at the second attempt: the first mutation matched three
identical lines in `protectingSignalNames` and proved nothing, which is worth recording because a
mutation that lands in the wrong method reads exactly like a test that does not discriminate.

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

**FIXED 2026-09-03.**  `connectingFailed()` marshals itself onto the event thread - the way
`StartupSplash.close()` did before the rewrite - rather than each of the three call sites doing it, so a
fourth caller cannot forget.

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

**FIXED 2026-09-03**, and the finding's reasoning is the fix's: the gate was a precondition that lost
its subject on the way over from the sibling notice.

`modelsAnyLength()` asks the question this check is about - any track measured, any station with a
maximum, or any locomotive with a train length - so somebody who authors train lengths and station
maxima and has never measured track gets the notices about the feature they are using.

`testTheStationCapacityNoticeDoesNotNeedAMeasuredTile` covers it, mutation-confirmed against putting
`measuresAnyTrack()` back.

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

**Pass 4 - still open after B2's fix, which is worth saying explicitly.** `one.sh` received the whole
lock protocol on 2026-09-03 (B2 above, verified landed: `lock_holder_state` at `docs/tools/one.sh:132`,
`take_the_lock` with the `noclobber` create and the `mv` of `$LOCK.mine.$$` at `:177-188`). The reap was
not part of that edit. `reap` is still defined once (`:259`) and called once, at the top of the class
loop (`:331`), and the comment above that call still says "and again after the last one (V33-C1,
V33-C2)". `battery.sh` has the post-loop copy, at `:584-588`, under a comment that spells out the cost -
"a run that ends on a class which left a JVM behind therefore leaves it behind for good". So the sibling
pair has now drifted apart, been brought back together on the lock, and drifted apart again on the reap,
in the same file, within one day. This is the fourth instance of the shape B2 was filed for.

**FIXED 2026-09-03.**  `one.sh` reaps after the last class, which is what its comment had been claiming.  Pass 4's note is the one worth keeping: the sibling pair drifted, was brought back together on the lock, and had drifted again on the reap - in the same file, within a day.

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

**FIXED 2026-09-03.**  `AutonomySession.clearEveryPlacement()` writes both properties through the same writer the single-square door uses and re-derives the station index once - 2N full builder constructions become one.  Covered by `testEveryPlacementGoesAtOnce`, which also pins the facing going with the placement, and mutation-confirmed by leaving the facing behind.

### C5

**`tilesWithALocomotive`'s javadoc makes the claim its twin was corrected for, one method up.**

AutonomySession.java:4337: `@return the squares, in the configuration's own order` -
`tilesWithAHome` directly above carries the CD3-C4 correction spelling out why that is false: the
points live in a `JSONObject`, which is a `HashMap`, so the order "is stable for a given set of keys
and unrelated to the order they were written". The same walk over the same map cannot have the order
one javadoc denies and the other claims. Harmless to today's callers (count and clear), hence C;
the fix is the sentence the twin already has.

**FIXED 2026-09-03.**  `tilesWithALocomotive`'s `@return` carries its twin's correction.

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

**FIXED 2026-09-03.**  Both sentences.  The guard says the sum stops at the last switch on a builder-emitted configuration and that the whole-path sum survives only for a switchless route, and the paragraph that said the counting was unchanged says which of the two unsoundnesses the ruling removed.

### C7

**`clearAllHomes`'s javadoc names a mechanism the body does not use.**

AutonomyEditorPanel.java:6665's javadoc: "Through `session.setHome(tile, null)` rather than by
writing the property, because that is the door the per-square menu uses". The body calls
`session.clearEveryHome()`, which writes through `writePointProperty` directly - the bulk door DY3-C5
added, argued for by the body's own comment thirty lines down. The two paragraphs of one method
disagree about which door is used; the second is right. Behaviour is unaffected (`setHome(tile,
null)` and the bulk write differ only in the per-write re-derive, and the elsewhere-sweep is a no-op
for null) - the first paragraph needed updating when the bulk door landed. C, comment-only.

**FIXED 2026-09-03.**  The javadoc names the door the body uses - `clearEveryHome()` - and says what it does differently from `setHome(tile, null)`, which is the re-derive and nothing else.

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

**FIXED 2026-09-03.**  The `repaintLayout()` is gone with the sentence that bought it: the main window has not drawn the numbers since the viewer gate landed, so there is no other answer for it to be left showing.  Verified by running the coordinate tests and the editor surface rules.

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

**FIXED 2026-09-03.**  Confirmed - the count includes the turnaround square, because that square's own
length is one of the things the guard needs.  The sentence says so in all eight bundles: "{2} squares
there and on the way in".  The singular key is untouched and was already right.

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

---

## Pass 3 - the model, read cold

**Reviewed:** `src/org/traincontrol/` at `7acc9837`, on 2026-09-03, with no commit history read.
No tests were run.

**Method.** The whole of `automation/` was read line by line (`Layout`, `Point`, `Edge`,
`HomeStaging`); `automationui/` was read whole for `GraphReducer`, `AutonomyBuilder`, `TileGraph`
and `AutonomyChecks` and at the load-bearing seams of `AutonomySession` (capture, placement, homes,
the check wiring) and `AutonomyCompanionStore` (save, rename, delete); `marklin/` at `MarklinRoute`
whole and `MarklinControlStation` / `MarklinLocomotive` at the message, feedback and stop paths;
`base/Locomotive` at the wait machinery. The question asked throughout was the cold one - not "what
did this change break" but "do the pieces still agree about the contract between them" - and the one
A below is exactly a contract between three pieces that are each correct alone. Every candidate was
grepped against `docs/reviews/` before filing; the ones that turned out already argued are in D12
with their prior identifiers.

### A2

**Loading a diagram configuration silently wipes every placed locomotive's train length,
reversibility and function slots - authored data, lost to defaults, and the guards that depend on
it stand down without a word.**

Three mechanisms, each individually correct, each individually reviewed, that no longer agree:

1. **Capture strips a placement to its name.** `captureFromLayout`
   (`AutonomySession.java:3047-3057`) replaces the stored `loc` object with
   `new JSONObject().put("name", ...)`, and its merge phase (`:3110-3114`) `put`s that over
   whatever richer object the configuration held. The reasoning is written at the site: "capturing
   its length, reversibility and functions made loading a configuration silently revert changes made
   in the locomotive UI since. Those live in LocDB" (`466d864e`, 2026-08-16).

2. **The builder emits the stored placement verbatim.** `AutonomyBuilder.build()` puts
   `extras.get(LOCOMOTIVE)` onto the placed copy unchanged (`AutonomyBuilder.java:918-921`), so the
   configuration `parseAuto` receives carries `{"loc": {"name": X}}` and nothing else.

3. **`parseAuto` resets what a placement omits.** For a `loc` block with no `trainLength` key it
   runs `l.setTrainLength(0)` (`Layout.java:7716-7719`); no `reversible`, `l.setReversible(false)`
   (`:7742-7745`); and it unconditionally clears both function slots before re-reading them -
   `l.setDepartureFunc(null)` (`:7779`), `l.setArrivalFunc(null)` (`:7818`) - so absent keys leave
   them null. These are writes into the LIVE locomotive objects, which `MarklinSimpleComponent`
   persists (`MarklinSimpleComponent.java:151-154`), so the wipe reaches LocDB on exit and the loss
   is permanent, not per-session. (`speed` survives, alone of the five: `parseAuto` only applies a
   stored speed, never resets an absent one - the exact idiom `Point.toJSON`'s MT-233 comment
   documents, "Absence says the same thing and cannot be misread".)

The strip is the half that lost its precondition. When it landed, its premise - "those live in
LocDB" - was only half true: LocDB *stores* them, but `parseAuto` *overwrites* them from the
placement on every load, and nothing was changed there. So the fix for "loading reverts UI edits
to stale captured values" produced "loading reverts UI edits to *defaults*" - strictly worse, and
quieter, because a default does not look like a leftover.

**This does happen, not could.** The load path is `parseAuto(session().buildConfiguration())`
(`AutonomyViewerPanel.java:815`), reached by the startup resume, every configuration switch, and
every rebuild after a diagram edit. And the operator's own frozen data shows the strip has already
run: every placement in `test/operator_layout/config/autonomy/configuration-Main.json` is
name-only - `{"name": "EN57-947"}`, `{"name": "2-8-4 3505 SP"}`, `{"name": "75 407 DB"}`,
`{"name": "EN57-203"}` - while the legacy `autonomy_legacy/autonomy.json` beside it holds what was
authored for those same trains: `EN57-203` with `"reversible": true, "arrivalFunc": 2,
"departureFunc": 15, "trainLength": 2`. The current configuration cannot restore any of it.

**What runs differently on the layout once the wipe has happened:**

- **The tail-clear guard runs on length zero.** `tailHasProvablyPassed(_, behind, 0)` is true the
  moment the head passes an edge's end (`Layout.java:3934-3937`), so `clearedEdges` fills as if the
  train had no length - and with atomicRoutes on, being in that set is "the ONLY thing that drops
  an edge's protection" (`Layout.java:5455-5462`): a route may throw a turnout under the middle of
  a long train whose length the operator had recorded precisely to prevent that. This is WK-B1's
  hazard reopened through data rather than code.
- **The reverse-over-switch guard goes dead.** `measuredRoomToReverseInto` answers null at
  `getTrainLength() <= 0` (`Layout.java:6257`), so the rule Adam accepted as `FX2-3` - and that
  FV2-B1 measured live on his main page - never fires.
- **`validateTrainLength` never refuses.** `0 <= maxTrainLength` always (`Point.java:915`), so the
  station maxima of FR-046 are un-enforced however carefully both halves were typed.
- **Reversible trains stop being reversible.** `pickPath` stops offering them termini
  (`Layout.java:3809`), `barredFromAutonomy` bars every terminus for them (`:4077`), and
  `HomeStaging.mustBackIn` demands a turning route home (`HomeStaging.java:1611`) - EMUs behave
  like steam locomotives until somebody re-ticks a box that will be wiped again on the next load.
- **Departure and arrival functions stop firing** - the audible half of every dispatch.

The failures are restrictive or silent, which is why nothing has been filed that names the cause -
but the symptoms are on the record: FR-046 ("warning if train length is not set") and FR-047
("train length easier configuration") are requests from an operator whose lengths keep needing
setting, and MT-222's validation steps check the two new length doors agree with each other in one
session, never that a value survives a reload.

The half-fixed state is also internally documented as a contradiction: `getLocomotiveNameAt`'s
javadoc still states the OLD invariant - "a train's length and functions have to travel with its
name rather than beside it" (`AutonomySession.java:3892-3897`) - twenty lines below the capture
that stopped them travelling; and `testALegacyImportPutsTheLocomotivesBack` pins the import half
carrying `arrivalFunc` across with the failure message "the placement was rebuilt rather than
carried over, so its settings were lost" (`testAutonomyDiagramSession.java:2448-2449`) - the exact
loss the first capture then inflicts on the imported data. Nothing pins the round trip.

**Fix directions, both small; which one is Adam's call:**

- *Stop the reset:* `parseAuto` treats an absent `trainLength`/`reversible`/function key as "no
  opinion" instead of "reset to default", the way it already treats `speed`. One method, and the
  legacy flow is unaffected in practice - legacy files written by `Point.toJSON` carry `reversible`
  always and `trainLength` whenever it is nonzero.
- *Or stop the strip:* the builder injects the live locomotive's current values into the emitted
  `loc` block at build time (the staleness that motivated `466d864e` cannot occur when the source
  is the live object at the moment of the build, rather than a capture from an earlier one).

Confidence: high; every link in the chain is a read of the current tree plus the frozen operator
data, and no compensating mechanism was found after searching for one (no post-load re-application,
no session-side store of these fields, no test asserting survival). What I would have run: a
headless fixture - import a legacy placement with length/reversible/functions, load, capture, load
again, assert the four fields on the locomotive - expecting the second load to zero them.

**Pass 4 - the reach is wider than "every configuration load", because the load is now the default.**
At 2.8.1, `AUTO_LOAD_AUTONOMY` defaulted to **false** (`git show
master:src/org/traincontrol/gui/TrainControlUI.java:704`, `prefs.getBoolean(AUTO_LOAD_AUTONOMY,
false)`); at HEAD it defaults to **true** (`TrainControlUI.java:928`). The preference key is the same
string at both revisions (`"AutoLoadAutonomy" + Conversion.getFolderHash(10)`,
`TrainControlUI.java:291` and `master:200`), so a 2.8.1 user who never opened that menu has no stored
value and gets the new default. `init` then posts `getAutonomyViewerPanel().loadActive()` on every
start (`TrainControlUI.java:7055-7066`), which reaches `parseAuto` by the path A2 traces. So the wipe
A2 describes is not something the operator opts into: on the ordinary upgrade it happens on the first
start after installing, and again on every start after that. Independently confirmed on the frozen
data: of the 71 points in `test/operator_layout/config/autonomy/configuration-Main.json`, not one
carries a non-zero `maxTrainLength` (every value is `0` or the key is absent), and all four `loc`
blocks are name-only - `{"name": "EN57-947"}`, `{"name": "2-8-4 3505 SP"}`, `{"name": "75 407 DB"}`,
`{"name": "EN57-203"}`. This is the same fact B3 files from the
regression side; it is recorded here because it changes A2's exposure, not A2's mechanism.

**FIXED 2026-09-03**, and this was the most valuable finding of the four passes.

Confirmed end to end before fixing: `captureFromLayout` writes `{"name": X}`, `parseAuto` treats an
absent key as an instruction to clear, and `l` is `control.getLocByName(...)` - the live locomotive the
model holds and saves at exit.  So every start-up of 3.0.0 on the diagram path wiped the train length,
the reversibility and both function slots of every placed locomotive.

The four `else` clauses are gone.  **Absence means "not stated"**, which is what `speed` in the same
block has always done and what `MT-233` settled in those words.  Clearing a length is still possible by
writing the key as 0, and a legacy `autonomy.json`, which carries these keys, still applies them.

Two tests, both in `testHomeStaging`: `testLoadingDoesNotClearWhatThePlacementDoesNotCarry` (the fixture
is the ordinary `station()` helper, which is why every test in that class was standing on this defect
without seeing it) and `testAPlacementThatCarriesAValueStillSetsIt`.  Mutation-confirmed by restoring
`setTrainLength(0)`.

### D12

**Checks that came back clean, and candidates that were already argued.** Each of these is a
specific way the model could have been internally inconsistent, checked in the final tree:

- **Edge occupancy counting balances (RC-A9).** Every raise has exactly one release across both
  route modes: atomic releases once in `unlockPath`; non-atomic releases early via
  `tailHasProvablyPassed` and the given-up set keeps `unlockPath` from releasing twice
  (`Layout.java:3310-3372`). A lock-edge list carrying the same edge twice - reachable when the
  FR-001 emission and the shared-tile derivation both name it (`AutonomyBuilder.java:1089-1157`) -
  stays balanced, because `setOccupied` and `setUnoccupied` walk the same list.
- **The timetable's sequential ordering has no gap.** `executionTime` is stamped in the same
  `synchronized (activeLocomotives)` block that registers the locomotive
  (`Layout.java:5244-5267`), so the dispatcher's two waits - "previous started" then "previous
  arrived" - cannot read a started-but-unregistered state, and the last entry cannot be dispatched
  while an earlier one still retries (its `executionTime` stays 0 until it locks a path).
- **`firstClearRoute`'s mustBackIn refusal cannot re-create the WK3-B2 shape.** The
  arrival-refused-for-facing `continue` (`HomeStaging.java:1089-1093`) does record the arrival in
  `seen` - but the key carries `/turned` vs `/straight` (`:1070`), so what it closes off is only
  routes that would be refused for the same reason. The room refusal, which has no such split, is
  checked before recording, as WK3-B2 required.
- **The builder's null-side cases are unreachable where they would bite.** A `ReducedEdge` leaving
  a Point always has a real exit side - feedback tiles are never portal tiles, and
  `validatePortals` refuses a pairing whose end is not a link (`TileGraph.java:936-941`) - so
  `Node.leavesBy`'s refusal of a null exit (`AutonomyBuilder.java:114-126`) cannot silently drop a
  cross-page route; and `splitSides` already declines to split a square arrived at through a link.
- **A feedback tile cannot walk one physical run twice.** The three feedback port shapes
  (`TilePorts.java:253-255`) give each entry side at most one route and no two routes a shared
  side, so `walkEdges`'s entry-times-exit fan-out cannot emit duplicate identical edges and the
  parallel-route warning cannot fire spuriously from that direction.
- **The lock-order discipline at `getEdges` held everywhere I opened.** Every UI-synchronized
  method reached from the Layout-monitor side only posts to the EDT while holding the UI monitor
  (`repaintSwitch` at `TrainControlUI.java:8220`, `repaintAutoLocListLite/Full` at
  `:24895/:24970`), and `updateVisiblePoints` (`:24610`) takes no Layout-synchronized method - so
  the AB-BA pair the `getEdges` javadoc warns about has no second half today.
- **The staging-window guard on hand placement is at the UI tier by design.** `moveLocomotive`
  checks `isRunning()` only, unlike `renamePoint`'s `|| isStagingInProgress()` - but all four call
  sites into it sit behind `isAutonomyBusy()` (which `stagingFlowActive` feeds), and a placement
  slipped through would only stale the plan's snapshot, which execution then refuses at
  "locomotive not at path start" - fails safe.
- **Candidates re-found and already on file, not refiled:** `blockedSensors(Map state)` ignoring
  its parameter (RTG-C2, with WK3's note that the body is correct); `Point.validateTrainLength`'s
  unguarded unbox (TCX-D6, unreachable - all constructors initialise 0 and no caller passes null);
  and `loadReturnToHomeTimetable`'s comment naming `timetableSequential` where the mechanism is
  `timetableExecuting` (`Layout.java:6800-6803`) - filed as D9 of `2026-08-21-independent-pass.md`
  and still uncorrected in the tree, three weeks on, in a project that treats a stale comment as a
  defect.
- **Pass 1's D9 splice check, confirmed by a different route.** Reading `GraphReducer.sumLength`,
  `lengthOf` and `roomAfterTheLastSwitch` (`GraphReducer.java:1077-1126`) against
  `measuredRoomToReverseInto`'s walk (`Layout.java:6288-6307`): an edge's length includes its end
  tile and not its start, `roomAtTheEnd` includes the end tile and not the switch, so the splice
  counts each square exactly once - same conclusion, from the emitting side.

### D13

**What this pass did not cover.**

- **Nothing was executed** (rule 1). The two findings that most want a fixture - A2 above, and the
  A1/B1 pair from pass 1 - each say what the run would be.
- **`TrainControlUI` was read only at targeted sites** (the autonomy doors, the lock-order
  question, the placement keys, the load hook) - perhaps a tenth of its 26,700 lines. The
  rendering classes (`LayoutGrid`, `StationCaption`, `LayoutLabel` beyond pass 1's A1 sites,
  `LocIconCropDialog`, `TileOverlay`, `TileAnnotation`) were not read at all.
- **`AutonomySession` was read at its seams, not whole** - roughly a third of 5,466 lines; the
  reconcile/migration machinery and the station index were trusted to their own reviews.
  `AutonomyCompanionStore` likewise: save/rename/delete were read, the eleven collections' held
  entries and page bookkeeping were not.
- **`TilePorts`' port tables were consulted, not audited** - the feedback shapes for D12, nothing
  else. A wrong entry there mis-models a tile everywhere at once and no pass has line-checked it.
- **`CS2File`, the UDP layer, and `NodeExpression`** were not read. `MarklinAccessory`'s actuation
  confirmation was taken on trust from `Layout.validatePathActuation`'s javadoc.
- **The test suite was consulted only as evidence** (the legacy-import pin in A2); it was not
  reviewed.
- **`cs2_sample_layout/` was not opened** (rule 3); A2's data claims rest on the frozen
  `test/operator_layout/` copy.

---

## Pass 4 - regressions against 2.8.1, and what the 30 August to 2 September rounds left

**Reviewed:** branch `autonomy-diagram-r0` at `58ef26c5`, on 2026-09-03, against `master` at 2.8.1
read with `git show master:<path>`. **No tests were run** - no `ant`, no `javac`, no `java`, no
TestNG, no `one.sh`, no `battery.sh`, no application. Every command was `git`, `grep`, `sed`, `comm`,
`wc`, `find`, or a short read-only `python` over JSON. **Nothing under `cs2_sample_layout/` was read
or written**; the operator's data was read from the frozen copy at `test/operator_layout/`.

**Two halves, and what each did that its predecessors could not.**

*The regression half.* `R28` (2026-09-01) and `RG3` (2026-09-02) both asked the 2.8.1 question and
both are thorough; `RG3-D2` in particular ran the orphan-key sweep at **both** revisions and
subtracted, which is the right method and left little behind. So this pass spent its time on the
three blind spots those two named, plus two they did not:

1. **Keys deleted from the bundle** rather than orphaned - `RG3-C1`'s route in. There are exactly
   **eighteen** (`comm -23`), and every one belongs to the deleted graph window or is `R28-C4`'s
   orphan; each was traced to a successor. That is `D14`.
2. **A key referenced only from a method with no callers** - the blind spot `RG3-D2` states in
   words and `RG3-C2` was found around. I ran that scan mechanically over every method in `gui/`
   and `automationui/`, counting call sites and `::` method references across `src/` and `test/`.
   Eighteen methods came back with no caller (`C15`) - and **not one of them is the only door to a
   capability**, which settles that blind spot rather than leaving it stated.
3. **The menu builders, item by item** - `RG3-C1`'s other route in. Diffed at both revisions for
   `LayoutEditorRightclickMenu`, `LayoutPopupUI`, `RightClickPageMenu`, `LayoutRightclickAutonomyMenu`,
   `RightClickRouteMenu`, `RightClickTimetableMenu`, `LayoutEditor` and `TrainControlUI` (`.java`
   and `.form` together). Every difference is already filed. `D14`.
4. **The preference DEFAULTS, not only the preference keys.** `RG3-D5` swept which preference names
   round-trip and found five dead constants. It did not compare the second argument to
   `prefs.getBoolean`. One of them has changed, and it changes what happens on every start: `B3`.
5. **The keys new in 3.0.0 that nothing reads** - 75 of them, the counterpart to `RG3-C6`'s four.
   Most are composed at run time and are false positives; four are not, and three of those four turn
   out to be legitimate (the affordance carries the rule the message would have spoken). The fourth
   is `C12`.

*The unaddressed half.* Thirty-four documents carry dates from 2026-08-30 to 2026-09-02. The
September ones are already indexed by `docs/reviews/2026-09-03-c-sweep-report.md`, which settles 96 C
findings, dismisses 8 with reasons, and puts four questions to Adam - so this pass did not re-do that
work. **The 30 and 31 August rounds are not in that sweep**, and they hold the largest pool of
findings that are still open with nothing since: `IPR` (11), `RGN` (9), `DAY` (9) and `RC`'s carried-
forward section. Every one of those was re-derived from the current tree - the cited file opened at
HEAD, not the disposition believed - and the result is the consolidated list at the foot of this
document, which is the first time it exists in one place.

**What the two halves found in each other.** `B4` is a regression finding that only exists because
the unaddressed-half work read a validation document line by line: `R28-B2` was withdrawn against a
menu item that is not the one the finding is about, and the miscitation and the withdrawal are the
same sentence. `C14` is the same sentence's other half.

### B3

**`AUTO_LOAD_AUTONOMY` changed default from false to true, so a 2.8.1 user who never asked for
autonomy now loads a configuration on every start - and the comment that argues for the flip says
the loaded-and-idle state carries no risk, which is what `A1` and `A2` of this document are both
about.**

The key is the same string at both revisions, so an existing installation's stored answer carries
over and an installation that never answered gets the new default:

```java
// TrainControlUI.java:291  (and master:200 - identical)
public static final String AUTO_LOAD_AUTONOMY = "AutoLoadAutonomy" + Conversion.getFolderHash(10);
```

```java
// git show master:src/org/traincontrol/gui/TrainControlUI.java:704
this.AutoLoadAutonomyMenuItem.setSelected(prefs.getBoolean(AUTO_LOAD_AUTONOMY, false));

// TrainControlUI.java:928
this.AutoLoadAutonomyMenuItem.setSelected(prefs.getBoolean(AUTO_LOAD_AUTONOMY, true));
```

A 2.8.1 user who never opened Startup Options has no value stored under that name. At 2.8.1 the box
read unticked and nothing was loaded; at HEAD it reads ticked, and `init` posts the load
(`TrainControlUI.java:7055-7066`, `getAutonomyViewerPanel().loadActive()` when there is an active
configuration, `validateButtonActionPerformed` otherwise).

**The flip is deliberate and its reasoning is written at the site. The last sentence of that
reasoning is the problem:**

```java
// TrainControlUI.java:923-927
// Loading is not running - it builds the graph and draws it, and starting trains is still a
// separate press - so there is nothing here for the default to put at risk.
```

Two findings in this document are defects **of the loaded-and-not-running state specifically**:

- **`A1`** - the tile's protecting-signal refusal has no way past it in exactly that state, because
  the dialog that would clear it is gated on `isAutonomyRunning()` and the worker check is not. `A1`
  already calls that state "the default start-up state of the operator's own railway"; this is the
  line that makes it so.
- **`A2`** - the load is what strips every placed locomotive's train length, reversibility and
  function slots. Before the flip, a user who left the box alone never reached it.

So the sentence is not merely stale: it is the justification for making both of them universal.
Neither existed when the default was chosen, which is why this is filed against the default rather
than against the person who flipped it.

**Severity.** B. It changes what happens on the layout without the operator choosing it, and the
data loss it delivers is `A2`'s - but the change itself is defensible (Adam's own argument for it,
about a setup that is present and invisible, is a good one), and the right fix is almost certainly to
close `A1` and `A2` rather than to put the default back. Filed so that the decision is made knowing
that the premise it rested on is no longer true.

**How to confirm.** Read-only: the two `getBoolean` lines above, and
`git log -S "AUTO_LOAD_AUTONOMY, true" -- src/org/traincontrol/gui/TrainControlUI.java`. To see it:
on a machine that has never run 3.0.0, open Startup Options and look at the tick.

**ANSWERED 2026-09-03 - the default stays, and the sentence that argued for it is corrected.**

The flip is deliberate and it is announced: the changelog says "the one you were last using is loaded
when TrainControl starts" (`Readme.md:366`).  What this finding is right about is the reasoning, and it
is right in the strongest way available - the two defects it points at, `A1` and `A2`, are both defects
of the loaded-and-idle state, and this default is what made them universal rather than opt-in.

Both are now fixed.  The comment at the preference records what the state cost, and the lesson the
sentence should have carried: "loading is not running" is a statement about TRAINS, not about the rest
of the application - a loaded configuration puts locomotives onto Points, and several rules ask whether
a Point holds one.

Reverting the default would be a product decision, and it is Adam's; with `A1` and `A2` fixed there is
nothing left to argue it from.

### B4

**`R28-B2` was withdrawn against `AutonomyMenu.java:326`. That line is the Export Configuration
item - a different artefact, which the finding had already read and excluded by name. The capability it is about - writing
out the derived graph in the JSON form 2.8.1 reads - is still behind `isDebug()`, and `isDebug()`
still needs a command line.**

The withdrawal:

```
docs/reviews/2026-09-01-regression-vs-2.8.1-review.md:56
    **WITHDRAWN IN FULL.** Adam: "isn't that available via the advanced Json export in the autonomy
    menu?" It is - `AutonomyMenu.java:326`.
```

recorded again, under the wrong prefix, in the validation that checked it:

```
docs/reviews/2026-09-02-first-validation.md:579
    - `RGN-B2` withdrawn - `AutonomyMenu.java:326` carries the ungated Export item.
```

`AutonomyMenu.java:326` is:

```java
JMenuItem exportItem = item(I18n.t("autosetup.ui.btnExportConfiguration"), new Runnable()
{ ... actions.exportConfiguration(); ... });
```

and `exportConfiguration` writes `session().getStore().exportBundle(name)` through a `JFileChooser`
(`AutonomyViewerPanel.java:1222-1248`) - the companion **store's** authored setup, which is the good
backup `R28-B2` itself pointed at and said was not the thing: *"it exports the companion-store
configuration, not the built graph - a good backup, and not the thing you can hand-edit or feed back
to a 2.8.1 installation."* The withdrawal cites the sentence the finding wrote to pre-empt it.

If Adam meant the other item - "Export Raw Graph as JSON (Advanced Users)", which is what "advanced
Json export" most naturally names - then it exists and he cannot reach it. Both its doors are gated
the same way:

```java
// AutonomyMenu.java:445-449
boolean inspectable = ui.getModel() != null && ui.getModel().isDebug()
    && !session.getStore().getConfigurationNames().isEmpty()
    && session.getStore().getActiveConfiguration() != null
    && session.getReducer() != null
    && !session.hasBlockingProblems();

// AutonomyViewerPanel.java:443-444, the second copy, same flag
if (ui.getModel() != null && ui.getModel().isDebug()
    && session().exists() && !session().hasBlockingProblems())
```

and `isDebug()` is set once, out of `main`, from the argument count:

```java
// src/TrainControl.java:23
boolean debug = (args.length >= 2);
```

`grep -rn "setDebug" src/` returns nothing. Somebody running the shipped application normally cannot
turn it on.

**And the 2.8.1 door is still shut in both configurations, exactly as `R28-B2` traced it.** On a
local layout `mountAutonomyControls` hides the button (`TrainControlUI.java:3483`,
`this.exportJSON.setVisible(false)`); on a Central Station layout it comes back
(`TrainControlUI.java:3449`) but lives on the Auto tab, and

```java
// TrainControlUI.java:3730
setAutoTabEnabled(valid && loaded && isLocalLayout());
```

greys that tab and steps off it (`:3733-3741`) when the layout is not local. Both lines are unchanged
from what `R28` quoted, at a HEAD twenty-plus commits later.

**Severity.** B, the severity `R28` gave it, restored. No train moves wrongly; what is gone is the
only door to the graph in the format 2.8.1 reads, which is also the only downgrade path.

**What is actually owed here is one sentence from Adam**, and it is a narrower question than the one
he was asked: *not* "is there an export" - there are two - but "should the raw-graph export be
reachable without a command-line debug launch". If the answer is no, `R28-B2` closes as DECLINED with
the reason recorded, which is a different outcome from WITHDRAWN and leaves the right trace.

**How to confirm.** Read-only, above. To see it: start the jar normally, open the Autonomy menu, and
look for "Export Raw Graph as JSON".

**OPEN - Adam's, and the finding is right that the record is wrong** (2026-09-03).

Confirmed: the withdrawal was written against the Export **Configuration** item, which `R28-B2` had
already read and excluded, so the derived-graph export is still behind `isDebug()` at both doors and the
2.8.1 button is still gone from an ordinary session.  What is owed is one sentence from Adam: is the
derived graph something a person should be able to export, or is it a debugging artefact?

Carried into the questions list at the foot of `docs/reviews/2026-09-03-c-sweep-report.md`.  The
identifier keeps its life: `R28-B2` is DECLINED-pending-Adam, not withdrawn, and its own entry says so.

### C12

**`RG3-C5`'s correction landed on the Readme and not on its twin. The two bundle tooltips still carry
the sentence it corrected, in all eight languages.**

**Disposition: corrected rather than deleted, in all eight bundles.**

`growEdges` calls `addRowsAndColumns(1, 1)` and its own javadoc says *"growing at the right and the
bottom"* - two edges. The English now reads *"Add a column on the right and a row at the bottom"* and
*"Take the same two away, if neither holds track"*, and the seven translations follow, with the count
word changed in each (`drei`/`trois`/`tres`/`tre`/`drie`/`tre`/`trzy` to the two-form each language
wants). Every bundle still parses as ASCII and `testMessageBundles` is green.

Corrected rather than deleted because the finding's own reason is the better one: nothing reads these
keys today, and the next person to wire a tooltip onto those two buttons would wire the wrong sentence
onto them.

`RG3-C5` was closed on 2026-09-03 - *"the changelog says a column on the right and a row at the
bottom, which is what the two buttons do"* - and the Readme is right. The strings are not:

```
messages.properties:1916-1917
    layout.ui.tooltipGrowDiagram=Add a column on the right and a row at the top and bottom
    layout.ui.tooltipShrinkDiagram=Take the same three away, if none of them holds track
```

Two edges, not three, and `LayoutEditor.growEdges` calls `layout.addRowsAndColumns(1, 1)`
(`LayoutEditor.java:4576`) - the exact discrepancy `RG3-C5` filed. Both keys are present in all eight
bundles (`grep -c` returns 2 in each of the eight files) and translated: the German reads *"Eine
Spalte rechts und je eine Zeile oben und unten hinzufuegen"*, which carries the error faithfully.

**Nobody sees them, which is why this is a C and not a repeat of `RG3-C5` at B.** Neither key is
referenced by any `.java` or `.form` in `src/` or `test/`; the two menu items use `Control+I` and a
refusal reason instead (`LayoutEditorRightclickMenu.java:446-447`, `:470`). They are among the 75
keys new in 3.0.0 that nothing reads.

The reason to file it anyway is the one `docs/reviews/README.md` gives for the sweep: the sentence is
translated eight times, and the next person to wire a tooltip onto those two buttons will wire the
wrong sentence onto them. The fix is the same four words, in eight files, or deleting the two keys -
`RG3-C6` deleted four keys in exactly that situation, on the same day, and these two were beside them.

### C13

**`CommandRow.canBeACondition`'s javadoc says the editor does not offer `AUTO_LOCOMOTIVE` and cannot
build the row its own documentation illustrates. It does, and it can - since `ef33f4a8`, which fixed
precisely that.**

**Disposition: fixed, both sentences.**

`canBeACondition`'s javadoc now says `AUTO_LOCOMOTIVE` **is** offered, names `ef33f4a8` as the commit
that added it, and points at `RouteEditorFrame:3415-3419` for the two halves the old sentence said the
row could not have. The four kinds that really are absent keep their reason.

`defaultSettingFor`'s comment no longer argues for a decision that was reversed. What is left there is
a genuinely empty default rather than a policy, and it says why the two sides differ: a **command** row
of that kind is still refused until a sensor is typed; the **condition** side is given sensor 1 by
name, at the editor.

```java
// CommandRow.java:262-271 (javadoc)
* AUTO_LOCOMOTIVE is not offered here even though evaluate handles it, because CommandRow has no
* controls for a kind that needs a locomotive AND a sensor.  One already in a route is preserved
* read-only, the way every other unsupported kind is.  Worth knowing: it is the condition
* ConditionRows' own header uses as its example, so the editor cannot yet build the row its
* documentation illustrates.
*/
public static boolean canBeACondition(Kind kind)
{
    return kind == Kind.ACCESSORY || kind == Kind.SIGNAL
        || kind == Kind.FEEDBACK || kind == Kind.AUTO_LOCOMOTIVE;
}
```

The last clause of the body is what the javadoc says is absent. `ef33f4a8` (2026-08-20, *"Close the
gaps between the two route editors"*) is the commit that added it, and its message says so: *"An
AUTONOMY CONDITION could be chosen and did nothing ... It starts on the first locomotive now, like
every other kind starts on something."* The editor honours it:

```java
// RouteEditorFrame.java:3415-3419
String starting = became == CommandRow.Kind.AUTO_LOCOMOTIVE
    ? firstLocomotive() : (CommandRow.hasTarget(became) ? "1" : "");

String settingFor = became == CommandRow.Kind.AUTO_LOCOMOTIVE
    ? "1" : CommandRow.defaultSettingFor(became);
```

**The same two lines refute a second comment, one method away.** `defaultSettingFor`
(`CommandRow.java:239-241`) still says of `AUTO_LOCOMOTIVE`: *"A sensor number, which has no sensible
default - the row is refused until one is typed, and refusing is better than offering sensor 1 to
somebody who did not choose it."* `RouteEditorFrame.java:3419` offers exactly sensor `1`, on the
condition side, by name. The decision was reversed and the sentence arguing for it was left standing
in the class the reversal routes around.

**Severity.** C, behaviour-neutral: the capability is present and correct, and both sentences are
comments. It is worth filing because this is the class a future author reads to find out which kinds
a condition may hold, and it currently answers that question twice, differently, in the same file -
which is the `C5`/`C7` shape this document already has two of. This is also the correct disposition
of `RG3-D9`'s claim that every command kind has a row: it holds, and the file says otherwise.

### C14

**Two prefix typos in the 2026-09-02 first validation record an open `B` and an open `C` as settled.**

**Disposition: fixed, and each correction carries what the real finding is.**

Both lines in `2026-09-02-first-validation.md` now read `R28`, with a note under each saying which
`RGN` finding was NOT dispositioned there - `RGN-C1`, auto-save forced on with its checkbox hidden,
still open; and `RGN-B2`, whose three descriptions were brought into agreement on 2026-09-03 and whose
behaviour is a question for Adam. A reader auditing `RGN` and landing on that file is no longer told
its B2 was withdrawn.

```
docs/reviews/2026-09-02-first-validation.md:576
    - `RGN-C1` - the button exists beside Name Everything with the 2.8.1 confirmation (D12).
docs/reviews/2026-09-02-first-validation.md:579
    - `RGN-B2` withdrawn - `AutonomyMenu.java:326` carries the ungated Export item.
```

Both mean `R28`. `R28-C1` is "Clear All Home Locomotives is gone" - which is the button beside Name
Everything, and is fixed. `R28-B2` is "Export Current Graph is unreachable" - which is what the
`AutonomyMenu` line is about (and is `B4` above). What the two identifiers actually name is different
work that is still open:

- **`RGN-C1`** - auto-save on exit is forced on and its checkbox hidden, so `autonomy.json` is
  rewritten for somebody who turned it off. Verified still present:
  `TrainControlUI.java:916-917` is `this.autosave.setSelected(true); this.autosave.setVisible(false);`
  and the legacy write is still gated on `this.autosave.isSelected()` at `:2278-2281`.
- **`RGN-B2`** - an s88-fired route that meets a conflict drops **all** of its accessory commands and
  runs every non-accessory command anyway. Verified still present: `MarklinRoute.java:631`,
  `boolean skipAccessories = auto && conflict != null;`, consulted only inside `if (rc.isAccessory())`
  at `:637-640`, while the `isStop`, `isFunctionsOff`, `isAutonomyLightsOn`, `isLightsOn`,
  `isLocomotiveSpeed`, `isLocomotiveDirection`, `isFunction` and `isRoute` branches are all ungated,
  and the chained route inherits `auto` at `:871`. This is a **B about what a route does on the
  railway**, and it is filed as still open in `2026-08-31-fanout-index.md:134`.

**Why this is more than a typo.** `docs/reviews/README.md` requires a prefix precisely because "see
B1" is ambiguous across documents, and the failure it was written against was one identifier naming
three things. This is the failure mode it predicted, one step further on: the identifier is
unambiguous, and it points at the wrong finding. A reader auditing what is left in `RGN` and reaching
`2026-09-02-first-validation.md` is told that its B2 was withdrawn.

**Severity.** C. Nothing on the layout changes and no code is wrong; two entries in the record are.
The fix is four characters, in two lines, plus a note under each of the two `RGN` findings saying
they were never dispositioned.

### C15

**Eighteen methods in the autonomy, diagram and route user interface have no caller anywhere in `src/`
or `test/`.**

**Disposition: the two traps removed; the other sixteen deferred past 3.0.0, with the reason.**

**Removed.** `TrainControlUI.greyOutAutonomy` - a public method whose javadoc says *"disables the start
autonomy button"* and whose body executes a graceful stop of the running railway. Its 2.8.1 caller was
`GraphViewer.formWindowClosing` and there is no graph window to close. And
`AutonomySession.restoreCaptionsOnPage`, whose javadoc says it is what the track diagram editor's undo
uses; the editor uses `captionSnapshot`/`restoreCaptions` through the store's page snapshots instead,
and has since that mechanism replaced this one. Both are the shape `receiveKeyEvent`'s own comment
warns about: a door that does something other than its name, waiting to be wired up.

**Deferred.** The other sixteen are accessors and wrappers whose only cost is tidiness. A sixteen-method
deletion inside a release candidate buys nothing a reader needs and is a diff nobody can review by eye,
which is the trade `docs/reviews/README.md` asks to be stated rather than made silently. They are listed
in the table above and none is a capability's only door - that negative result, which was the point of
the scan, stands.

Checked before removing: no `.java` in `src/` or `test/` names either method, including reflectively -
this suite reaches private members by name often enough that the question is a real one. Five classes
re-run green afterwards and the full battery is 148/148.

Found by counting every `name(` occurrence and every `::name` method reference across the whole tree
for each declared method in `gui/` and `automationui/`. Confirmed by hand for each:

| where | method | note |
|---|---|---|
| `AutonomyEditorPanel.java:2084` | `showTextMenu` | the whole show-the-menu wrapper; `buildTextMenu` beneath it is live (`:934`) |
| `AutonomyEditorPanel.java:5891` | `getTool` | |
| `AutonomyEditorPanel.java:5919` | `isShowingLengths` | |
| `TrainControlUI.java:24593` | `greyOutAutonomy` | 2.8.1's only caller was `GraphViewer.formWindowClosing` (`master:GraphViewer.java:631`) - closing the graph window did a graceful stop. There is no graph window to close |
| `TrainControlUI.java:9343` | `withNewFirst` | |
| `TrainControlUI.java:25479` | `routeNamed` | |
| `TrainControlUI.java:1346` | `getNumLocMappings` | |
| `LayoutEditor.java:864` | `addBoxHighlighted` | 2.8.1's callers were the two paste-row/paste-column menu items (`master:LayoutEditorRightclickMenu.java:65, :86`), removed deliberately per `RG3-D10` |
| `LayoutEditor.java:2641` | `isSelectMode` | |
| `LayoutEditor.java:3565` | `editTextWithDropdown` | already `RG3-C2`; not re-filed |
| `LayoutEditor.java:845` | `receiveKeyEvent` | already uncalled at 2.8.1, and says so in its own comment |
| `LayoutLabel.java:1256` | `getAutonomyOverlay` | |
| `TileAnnotation.java:629, :676` | `getMarks`, `getTraces` | |
| `AutonomyBanner.java:350` | `isSaying` | |
| `RouteEditorFrame.java:940` | `isSignalAt` | |
| `StationCaption.java:201` | `isRotated` | |
| `AutonomySession.java:184` | `restoreCaptionsOnPage` | |

(`LocButtonTransferHandler`'s four, `LocomotiveFunctionAssign.focusFno`, `LocomotiveSelector.getMainLocList`,
`PositionAwareJFrame.hasRememberedBounds`, `LayoutPopupUI.getPanel`, `CustomActionEvent.getCustomData`,
`LoadingSpinner.isAnimating` and `GraphLocAssign.getNumLocs` are framework overrides or accessors and
are not in the table.)

**The result that matters is the negative one, and it belongs in `D14` as much as here:** the scan was
run to close `RG3-D2`'s stated blind spot - "the sweep cannot see a key referenced only from a method
with no callers" - and **no capability was found whose only door is one of these**. `showTextMenu` and
`addBoxHighlighted` look like the shape and are not: the menu `showTextMenu` would show is reached
from `buildTileMenu` (`AutonomyEditorPanel.java:934`), and `addBoxHighlighted`'s two call sites went
with the feature `RG3-D10` cleared as a deliberate removal. `editTextWithDropdown` is the one real
instance and `RG3-C2` already has it.

**Severity.** C, dead code. Worth one commit because each of these is a trap of the kind
`receiveKeyEvent`'s own comment describes - "a key listener wired to it later would paste tiles in
autonomy mode without anyone noticing" - and `greyOutAutonomy` is the sharpest: a public method whose
name says it disables a button and whose body executes a graceful stop of the railway.

### C16

**`DAY-C4`'s stale claim now exists twice, and the newer copy is the authority the older one cites.**

**Disposition: fixed at both sites, which closes `DAY-C4` with it.**

`Layout.configureAndLockPath`'s comment now says what `release()` does - the floor holds for that edge
and then `setLockedEdgeUnoccupied()` cascades to every entry in `lockEdges`, each decrementing without
knowing whether this edge was taken - and says why the over-release is still unreachable, at the call
site where that argument belongs: `setOccupied` increments first, so only siblings a mid-loop throw
never reached could be released untaken, and nothing in the tree throws there.

`Edge.release`'s javadoc no longer quotes and endorses the sentence. That was the half that mattered:
a reader following the `Layout` comment to its source was told the claim had been checked, in the one
file that could have refuted it.

Comments only - the code is right, which is why this was a C and why `DAY-C4` was one too. Filing them
separately was correct: closing `DAY-C4` against its own site would have left the sentence standing in
the file it had been copied into.

`DAY-C4` (2026-08-31, open) is that `Layout.configureAndLockPath`'s comment stopped being true:

```java
// Layout.java:2947-2949
// Counting first can only ever release an edge that was never taken, and setUnoccupied on an
// edge that is already clear does nothing.
```

It does not do nothing. `Edge.setUnoccupied` (`Edge.java:503-511`) calls `release()`, which does floor
at zero for **this** edge (`:461-463`) - and then cascades `setLockedEdgeUnoccupied()` to every entry
in `lockEdges`, each of which decrements its own count with no knowledge of whether this edge was ever
taken.

**What is new since `DAY-C4` was filed:** `Edge.release`'s own javadoc now quotes the `Layout`
sentence and endorses it.

```java
// Edge.java:452-457
* The floor is not defensive tidiness, it is a contract something already depends on.
* configureAndLockPath counts an edge as taken BEFORE it takes it ... and its comment says the
* reason out loud: "setUnoccupied on an edge that is already clear does nothing".  It still does
* nothing.
```

"It still does nothing" is written in the class that implements the cascade, three methods above
`setUnoccupied`. So a reader who follows the `Layout` comment to its source is told the claim has been
checked, in the one file that could have refuted it.

**Behaviour, checked rather than assumed.** The over-release the two comments license is reachable
only if `setOccupied` throws **inside its own `lockEdges` loop** - `occupancy++` is its first
statement, so an edge counted by `edgesLocked++` and then thrown past has already been incremented,
and the release of that edge is correct. Only its locked siblings, the ones the throw did not reach,
are released without having been taken. That needs a `ConcurrentModificationException` on
`this.lockEdges` mid-loop, which nothing in the tree produces. **So this is a C for the same reason
`DAY-C4` is: the code is right and both descriptions of it are wrong** - and it is filed separately
from `DAY-C4` because `DAY-C4` names one site and closing it against that site alone would leave the
sentence standing in the file it was copied into.

### D14

**Pass 4's checks that came back clean.** Each is a specific way 3.0.0 could have taken something
away from a 2.8.1 user, checked in the final tree:

- **The eighteen deleted bundle keys.** `comm -23` over the two key sets gives exactly eighteen keys
  present at 2.8.1 and absent at HEAD: `app.ui.autonomyGraphTitle`, `...TitleLoc`,
  `autolayout.ui.confirmDeleteEdge`, `confirmDeletePoint`, `confirmDeletePointOccupied`,
  `errorLoadingGraphUi`, `infoAllLocomotivesPlaced`, `infoNoOtherPointsToConnect`,
  `infoPointHasNoCoordinateInfo`, `layoutGraph`, `menuAddLocomotiveAtNode`,
  `menuRemoveLocomotiveFromGraph`, `tooltip.reopenGraph`, `ui.main.graphUIOptions`,
  `ui.main.reopenGraph`, `ui.main.tooltip.hideInactivePoints`, `ui.main.tooltip.hideReversingStations`
  and `route.ui.errorUnusableLocName`. Seventeen are the graph window's own furniture, and the
  eighteenth is `R28-C4`, deleted on purpose on 2026-09-03. Place and remove have per-square
  successors (`autosetup.ui.menuAddToAutonomy`, `menuRemoveLocomotive`). **This is the door `RG3-C1`
  came in by, and there is nothing else behind it.**
- **The menu builders, item by item, at both revisions.** Comparing every bundle key in
  `LayoutEditorRightclickMenu`, `LayoutPopupUI`, `RightClickPageMenu`, `LayoutRightclickAutonomyMenu`,
  `RightClickRouteMenu`, `RightClickTimetableMenu`, `LayoutEditor` and `TrainControlUI` (`.java` plus
  `.form`): the only keys present at 2.8.1 and absent at HEAD are `RG3-C2`'s five station-label keys,
  `RG3-D10`'s `entireRow`/`entireCol`/`tile`, and the graph window's. **No unfiled menu item was
  lost.**
- **The keyboard.** `VK_*` constants in `LayoutEditor` and `TrainControlUI` at both revisions: HEAD's
  sets are strict supersets. `LayoutEditor` gained `VK_PLUS`, `VK_ADD`, `VK_EQUALS`, `VK_MINUS`,
  `VK_SUBTRACT` (the page step, FR-036), `VK_G`, `VK_H`, `VK_K`; `TrainControlUI`'s set is unchanged.
  The `+`/`-` handler sits **above** the `if (isAutonomyMode()) return;` line (`LayoutEditor.java:6686`
  against `:6704`), so it works in both editors, which is what the changelog claims and what MT-109
  was filed about.
- **The twelve autonomy settings all still have a control.** `minDelay`, `maxDelay`,
  `defaultLocSpeed`, `preArrivalSpeedReduction`, `maxLatency`, `atomicRoutes`, `maxActiveTrains`,
  `maxLocInactiveSeconds`, `turnOffFunctionsOnArrival`, `turnOnFunctionsOnDeparture`, `simulate` and
  `pathPreference` are each read into a widget by `loadAutoLayoutSettings`
  (`TrainControlUI.java:24557-24587`) and each is in the built configuration's `globals` on the
  operator's own frozen file. **Nothing that was tunable at 2.8.1 has become file-only.**
- **`MarklinRoute.locomotiveRenamed` was folded, not dropped.** The override is gone from
  `MarklinRoute` (it is the one method the API diff shows as removed), and the base
  `Route.locomotiveRenamed` (`Route.java:151-159`) now walks `namesLocomotives()`, which collects
  from `this.route` **and** from `NodeExpression.toList(getConditions())` (`:223-236`). The 2.8.1
  override existed only to add the conditions half. Renaming a locomotive still repairs the
  conditions that name it.
- **`CS2File`'s API is additive.** Method-signature diff at both revisions: three methods added
  (`parseLayoutIndex`, `copyAtomically`, `getPagesThatCouldNotBeRead`), none removed. Nothing a 2.8.1
  file could do is no longer read.
- **`shrinkEdges` refuses on either edge.** The changelog's *"Shrinking is refused if either of those
  edges still holds track"* is what `LayoutDiagram.edgesAreEmpty` (`:529-544`) does - it walks the
  rightmost column and the bottom row and returns false on the first occupied square in either.
- **The uncalled-method scan closes `RG3-D2`'s blind spot.** See `C15`: eighteen methods, and none is
  the only door to a capability.
- **Three of the four meaningful new-in-3.0.0 orphan keys are legitimate.** Of the 75 keys added for
  3.0.0 that no `.java` or `.form` reads, most are composed at run time (`"route.kind." + kind.name()`,
  `"autolayout.ui.pathPreference" + pref.name()`, `autosetup.ui.facing`/`side` plus a compass letter).
  Four read like removed guards and were opened: `autosetup.ui.errorNotAStation` is unnecessary because
  both placement doors only offer the gesture on a station (`AutonomyEditorPanel.java:1044-1047` builds
  the item inside `if (isStation)`; `LayoutRightclickAutonomyMenu.placeFacing` (`:830-845`) is reached
  only from a list of Point copies, which exist only for stations); `autosetup.ui.errorAutonomyRunning`
  is a duplicate of the live `autolayout.errorCannotEditWhileRunning`
  (`TrainControlUI.java:4401`); `autosetup.ui.confirmJumpWithUnsavedEdits` asks a question its own text
  answers ("Nothing is lost - everything you have changed is kept"). The fourth is `C12`.
- **The lock-edge question, as far as reading goes.** 2.8.1 let the user hand-pick which edges lock
  with which (`master:GraphEdgeEdit.java:140`, `e.setLockEdges(...)`); 50 of the 90 edges in
  `test/operator_layout/config/autonomy_legacy/autonomy.json` carry a hand-written list. At HEAD the
  list is derived - `reducer.getLocks()` plus the FR-001 restriction locks
  (`AutonomyBuilder.java:1089-1157`) - and the hand-written lists are inside the `"edges"` skip that
  `R28-B1` and `RG3-B2` are about. **I could not compare the two sets**: the built configuration is
  not stored at HEAD, so the derived list exists only in a running process, and running one is out of
  scope. Recorded rather than filed: if a later pass can execute, build the operator's configuration
  and compare its `lockedges` against the legacy file's, pair by pair. That is the one measurement
  that would say whether the geometry derivation is a successor to the hand lists or merely a
  replacement.
- **`REL-B2`, `C9`, `C10` and `C11`'s fixes were verified as landed**, not taken from their
  dispositions. `one.sh` now carries `lock_holder_state` (`:132-167`) and `take_the_lock`
  (`:177-188`) word for word with `battery.sh`'s, including the `noclobber` create and the
  `$LOCK.mine.$$` move; `checkReversalNeedsLengthRun` reads "{2} squares there and on the way in" in
  all eight bundles. `C3` is the one from that group that is still open, and the note under it says
  why that is now worse than when it was filed.

### D15

**What pass 4 did not cover.**

- **Nothing was executed** (rule 1). Every claim above is from reading `git`, the tree, and the frozen
  fixture. The one measurement this pass most wanted - the derived lock lists against the operator's
  hand-written ones - is stated in `D14` as the run it would be.
- **`cs2_sample_layout/` was not opened** (rule 3). Every data claim rests on
  `test/operator_layout/`.
- **The autonomy RUNTIME was not compared to 2.8.1.** `RGN`, `R28` and `RG3` all declared this gap and
  so do I: whether a train picks the same route and holds the same locks as at 2.8.1 needs a jar and a
  layout. The parity harness under `docs/tools/parity/` records one unresolved loss
  (`BottomInner -> Tunnel` losing its alternative via `BottomCrossover`/`TunnelPre`,
  `docs/tools/parity/README.md:93`) and that is still the only measurement anybody has.
- **`HomeStaging`, the timetable executor and the drawing classes** were not read for 2.8.1 parity.
  `RG3` named the same three; this pass added nothing there, and `LayoutGrid` / `LayoutLabel` /
  `StationCaption` / `LocIconCropDialog` remain the largest changed surface no regression pass has
  read. `IPR-B4` and `IPR-C2`/`C4` live in that last file and are all still open.
- **The 1 and 2 September documents were read through the C sweep**, not re-derived. The sweep's own
  "36 still to settle" - mostly `TCX` and `D24` test-quality items - was taken at its word, and the
  test-suite audit running separately is the right net for it. The list below carries them as a line
  rather than item by item.
- **The July and August backlog before 30 August** is out of scope by the briefing and was not
  touched; the C sweep estimates roughly 190 findings across some twenty-five documents.
- **Route execution against a real Central Station** - `RGN-B2`, re-verified above as still present,
  is a claim about what `execRoute` does with a conflict, and only the code was read.
- **I did not audit the fixes made TODAY beyond the four in `D14`.** `REL-A1`, `A2`, `B1` and `C1`-`C8`
  were open when this pass ran; if any is fixed after this line is written, this pass did not check it.

---

## Every open or Adam-needed finding in the 30 August to 2 September rounds

The briefing asked for this in one place, because it has never been in one place. Thirty-four
documents carry dates in that window. Everything below was **re-derived from the tree at HEAD**, not
taken from its disposition; where a later document has answered one, the answer is named. Identifiers
are cited, not re-filed.

**Verdict column:** *reproduces* = the cited code was opened at HEAD and the finding's mechanism is
there; *Adam* = it is a decision, not a defect; *unreadable* = it needs execution or the operator's
own data.

### 2026-08-30

| Finding | Sev | What is left | Verdict at HEAD |
|---|---|---|---|
| `LE-D3` | - | the "didn't save" half of Adam's original report was never reproduced or explained; OneDrive file locking is the standing hypothesis | unreadable |
| `RC` carried #3 | A-ish | a mid-run failure strands a train on locked track **and** drops that track's route protection in the same statement: the `RuntimeException` catch removes the locomotive from `activeLocomotives`, `locomotiveMilestones` and `clearedEdges` (`Layout.java:5022-5030`) and then `takingPath.remove(loc)` (`:5031`) - and those two maps are exactly what `RC-A10`'s widened `getActiveAccs` (`Layout.java:874-903`) reads. The path is deliberately left locked (`:5019-5021`) and there is no "abandoned but still locked" set anywhere | **FIXED on his ruling, 2026-09-03**: *"force a graceful stop, alert the user, then unlock."*  The stop and the alert were already there (`RC-A11`); the release is new, and it is what makes the handler's state consistent - it had already given up the protection those maps carry, so the old behaviour was held-and-unprotected rather than held-and-safe.  `Layout.java` releases the path the same way a finished one is released, and the message says to check where the locomotive is standing.  `testAFailedPathStopsTheRunAndGivesTheTrackBack`, both halves mutation-confirmed |
| `RC` - two stations may be given the same name, silently | C | `promptName` (`AutonomyEditorPanel.java:4149-4168`) has no uniqueness check and `AutonomyBuilder.uniqueNames()` (`:1294`) disambiguates to `X (2)` without warning. The javadoc that claims "a user who names two Points the same thing is told at authoring time" is at `AutonomyBuilder.java:1173-1178` and is **orphaned** - two javadoc blocks in a row, so Java attaches only the second, and `uniqueNames()` has none | reproduces; **Adam** |
| `RC` - `@Test(enabled = false)` is invisible to all three layers | C | one live instance, `test/core/testAutonomyDiagramSession.java:3075`, and nothing in `docs/tools/` or `build.xml` scans for the attribute. That disabled test documents a separate open production bug (`TST-B15`) | reproduces |
| `RC` - eight untranscribed test-suite gaps | - | five were closed as `SG-C1`-`C4` and `A101`; the other eight were never written down and no longer exist in any document | permanent loss |
| `RC` - `GraphReducer.hasAnyConnection` asks the neighbour's ports, not the sensor's own | C | `GraphReducer.java:850-857` iterates `graph.landing(tile, side)`, and `TileGraph.landing` (`:867-893`) tests only `hasPortOn(neighbourComponent, entrySide)` at `:890` | reproduces |
| `RC` - `AutonomyBuilder` emits `mustReverse` although `canReverse` is filtered out | C | skip list at `AutonomyBuilder.java:932-933` covers `CAN_REVERSE`/`PARKING`/`FACING`/`AUTO_DESTINATION`; `parseAuto` reads `"reversing"` (`Layout.java:7603-7609`) and never `mustReverse` | reproduces |
| `RC` - `sanitizeMultiUnits` re-reads the occupant after null-checking it | C | `Layout.java:5741-5749`, four dereferences after the check - **and the log line at `:5748` is still the only hard-coded English string in `automation`, typo included: `"because it confliced with "`** | reproduces |
| `RC` - should `BALANCED_PRIORITY` consider de-prioritised stations at all | - | the code implements `RC-B2`'s conservative answer (`Layout.java:334-339`) | **Adam** |
| `RC` - route editor forgets its position and size | C | `RouteEditorFrame.java:243` is a bare `setLocationRelativeTo(parent)`; no `PositionAwareJFrame`, no persisted geometry | reproduces |
| `RC` - `RouteEditorFrame`'s save-time refusals could lock somebody out of their own route | - | needs Adam's route corpus | unreadable |
| `SG-B5` | B | the 2.8.1 parity comparison. MT-083 confirmed the four new journeys, so the person-half is done; the parity report's own loss (`docs/tools/parity/README.md:93`) is still open, and nothing has been re-measured since the build it was taken from. Side note: that README's claim that `Layout.pathPreference` is `static` is stale - at HEAD it is a `private volatile` instance field (`Layout.java:223`) | reproduces |

### 2026-08-31

| Finding | Sev | What is left | Verdict at HEAD |
|---|---|---|---|
| `IPR-A2` | **A** | a save that prunes only tile properties shows no dialog at all. `AutonomyReport.show()` builds its text from `getForgottenNames()` and `getNamesStillReferenced()` only (`AutonomyReport.java:75, :85`) and the dialog is inside `if (text.length() > 0)` (`:98-102`), while `isClean()` counts a third list (`AutonomyCompanionStore.java:3235-3238`) that `reconcile` fills at ten sites. **`getDroppedTileProperties` (`:3205`) has no reader in `src/` - only four in `test/core/testAutonomyDiagramStore.java`** - and there is no third bundle key | reproduces |
| `IPR-B1` | B | "Highlight on Diagram" throws on any route holding a locomotive command: `RouteEditorFrame.java:2401` is `if (command != null && command.getAddress() > 0)`, and `RouteCommand.getAddress()` is an unguarded `Integer.parseInt` whose javadoc says it throws (`:331-334`). `hasAddress()` exists (`:321-324`) and is not asked | reproduces |
| `IPR-B2` | B | a bracket in a non-leading position round-trips with the wrong operator: `ConditionOutline.write` emits a `NodeGroup`'s contents at `depth + 1` (`:384-386`) while `writeChild` independently bumps a cross-operator child (`:422`), and `read` recurses at the deeper depth (`:210-239`) | **fixed** - `read` recurses at `depth + 1`, not the row's own depth |
| `IPR-B3` | B | two bracketed groups at one indent are flagged red and the save is refused: `problems()` keys `settled` on depth alone across the whole list (`ConditionOutline.java:156-167`) while `read` consumes each run in its own recursion (`:232-238`); `everythingWrong()` then adds `route.ui.frameLogicDisagrees` (`RouteEditorFrame.java:2158`) and `onSave` offers only Fix or Discard (`:2471-2491`). Filed as wanting a ruling more than a patch, and **no ruling is recorded anywhere** | reproduces; **Adam** |
| `IPR-B4` | B | the crop dialog's OK at full zoom-out allocates an image quadratic in the source: `contentOf` allocates the whole overhanging region (`LocIconCropDialog.java:1387-1388`) and the cheap `getSubimage` branch needs `wholelyInside` (`:1381`); at `zoomFraction = 0` the scale is half the fit (`MIN_ZOOM = 0.5`, `:88`), so `sourceRect()` is about twice the source each way. `sourceRect` is no longer clamped (`:1529-1540`) | **fixed** - reproduced at 18506 x 7120 / 502 MB; the overhang is built at the icon's size |
| `IPR-C1` | C | the label migration is a fourth door past "one station, one caption": `migrateStationLabels` writes `store.setCaption(where, station)` directly (`AutonomySession.java:1857`), bypassing the session's clear-the-old-one rule (`:1283-1298`), and `open()` calls it unconditionally (`:139`). **Measured on the frozen copy: `test/operator_layout/config/autonomy/setup.json` holds 34 captions for 33 stations, and `5:6,4` is named twice** | reproduces, with the bad state already in the data |
| `IPR-C2` | C | a saved crop view restores to a different rectangle after the dialog is resized - `largestWindow` fits width-first (`:682-700`) and `fitScale` takes a plain `min` (`:910-917`), and the two limiting terms can flip independently | reproduces |
| `IPR-C3` | C | the copy mark is filled white regardless of ink (`RowIcons.java:117-118`), so it vanishes on a selected row; the caller passes the selection foreground (`RouteEditorFrame.java:1623, :1648`) | reproduces |
| `IPR-C4` | C | the crop clamp's overlap guarantee is expressed in panel pixels (`LocIconCropDialog.java:1141`) while `sourceRect` rounds four quantities independently and no longer clamps | mechanism reproduces; the rounding-to-zero half is unreadable |
| `IPR-C5` | C | `getScale`'s "safe against recursion" comment (`LocIconCropDialog.java:956-957`) names a reason that is no longer the reason; the re-entry through `startAtCover` -> `clampCenter` -> `getScale` is harmless only because of `viewStarted` (`:976-978`) and `pendingView = null` (`:989`), neither of which it mentions | reproduces |
| `IPR-A1` | A | **fixed** (`CARRIED_SETTINGS` now carries `maxTrainLength`, `AutonomySession.java:527-528`, with the test inverted). Two residuals are Adam's: the **edge** lengths are still not migrated, deliberately (`:846-853`), and the damage already written is still in his file - six fabricated `tileLengths` in `setup.json` (`5:20,13`, `5:0,11`, `5:20,14`, `5:1,10`, `5:14,3`, `5:5,4`) | **Adam** |
| `RGN-A2` | **A** | the Auto tab is disabled for every user whose autonomy comes from `autonomy.json`: `boolean loaded = getAutonomySession() == null \|\| this.activeDiagramConfiguration != null;` (`TrainControlUI.java:3711`) and `setAutoTabEnabled(valid && loaded && isLocalLayout())` (`:3730`), with the contradicting comment still at `:3729`. `refreshAutonomyPrompt` needs a non-empty configuration list (`:6075-6077`), so a fresh upgrade gets no banner either. **No test exists.** Adam's own triage on MT-244 asks for one: *"Could not run this. make a test case for this. in my testing, it loaded OK."* | reproduces; **Adam**, and he asked for a test |
| `RGN-B1` | B | `Point:` captions are deleted out of the user's own `.cs2` files with no notice: `open()` calls `migrateStationLabels()` unconditionally (`AutonomySession.java:139`), the erase is `component.setLabel("")` (`:1905`) followed by `saveChanges(null, false)` (`:1917`), and only failures reach the user (`TrainControlUI.java:2637-2639`). Narrowed since filing (`RG3-D11`), still unannounced - no changelog line in `Readme.md:362-535` | **fixed** - announced in the changelog and in the start-up log; what it does is unchanged |
| `RGN-B2` | B | an s88-fired route that meets a conflict drops **all** its accessory commands and runs everything else - see `C14` above for the code and for why this reads as withdrawn when it is not | **descriptions fixed** - changelog and both log messages now say what the code does; the behaviour itself is **Adam's** |
| `RGN-B3` | B | the v2.7.4 changelog section was rewritten so two 3.0.0 changes read as already shipped: `Readme.md:536-544` puts "Added Path Integrity Validation features" under `v2.7.4`, and `git show v2_7_4c:Readme.md` has neither bullet and no `PATH_INTEGRITY_VALIDATION` in `src/` | **fixed** - both bullets moved to v2.8.0, which is where the commits landed |
| `RGN-C1` | C | auto-save on exit is forced on and its checkbox hidden (`TrainControlUI.java:916-917`), so `autonomy.json` is rewritten for somebody who turned it off (`:2278-2281`); `AUTOSAVE_SETTING_PREF` is still written on exit (`:21604`) and is unreachable. See `C14` - this reads as verified-and-closed when it is not | reproduces |
| `RGN-C3` | C | a locomotive whose name holds a bracket cannot be used in any route command (`RouteCommand.isNameUsable`, `:596-601`), and only three doors ask - `RouteEditorFrame.java:2227` and the two rename dialogs. Nothing in `marklin/` and nothing in `AddLocomotive` asks, so a bracketed name can arrive from a Central Station sync | reproduces; whether a real locomotive is affected needs **Adam**'s database |
| `RGN-C4` | C | a `UIState.data` written by 3.0.0 with a non-default page count loses its page names under 2.7.4c. The 2.7.4c side cannot change | reproduces, permanent by construction |
| `RGN-C2` | C | **effectively answered, and I would close it.** Its only actionable claim was "no changelog line"; there is one, and there was one at the reviewed commit - `Readme.md:509`, added by `062f7efa` on 2026-07-29 | not a defect |
| `RGN-A1` | A | **largely fixed** (globals carried, `AutonomySession.java:812-889`; what is left behind is now counted and reported by `whatALegacyImportLeaves`, `:906-962`). Open only for the **edge lengths** and the **timetable**, both deliberate with the reason at the code | **Adam** |
| `DAY-B1` | B | `repaintTimetable()` sits behind two early returns (`TrainControlUI.java:3891`, `:3913`) and neither rename door calls it - `:17088-17089` and `:23079-23080` both do only `updateVisiblePoints(); repaintAutoLocList(false);`. Re-found independently as `SVN-B12`, also open | **fixed** - `repaintTimetable()` at both rename doors (`97137c4b`) |
| `DAY-C1` | C | `liftAboveLabels` (`LayoutLabel.java:1198`, called at `:1135`) was made unnecessary by `LayoutGrid.paintTrainOverCaptions` (`:710`) and left in doing only its harm - the code states the residual cost itself at `LayoutLabel.java:1289-1293`. `CDR-B1` (open) raises the same area from the comment side and does not answer the behavioural half | reproduces |
| `DAY-C2` | C | `deleteLoc` did not get the redraws both rename doors have: `TrainControlUI.java:17919-17931` does `locDeleted(l); repaintAutoLocListFull(); refreshUI();` and neither `refreshRouteList()` nor `updateVisiblePoints()`. The rename door's own comment (`:17075-17086`, OB-081) says why the second matters - "Every other door that changes which locomotive stands where already does this" - and deleting a placed locomotive is such a door. **Nothing since; no later document mentions it** | reproduces |
| `DAY-C3` | C | `RC-A11`'s graceful stop fires from the two manual dispatch doors: `Layout.java:5049-5051` calls `stopLocomotives()` and logs `autolayout.errorRunStoppedByFailure` with no `this.running` test, and `executePath` has two manual callers (`AutoLocomotiveStatus.java:1041`, `LayoutRightclickAutonomyMenu.java:352`). The message says autonomy has stopped itself, when nothing was running | reproduces |
| `DAY-C4` | C | "setUnoccupied on an edge that is already clear does nothing" (`Layout.java:2947-2949`) stopped being true - and has since been copied into `Edge.release`'s javadoc. See `C16` | reproduces, at two sites |
| `DAY-C5` | C | `nameEverything` (`AutonomyEditorPanel.java:6591-6646`) ends `selection.clear(); refresh();` with no rebuild, while `promptName` ends with `rebuildRunningLayoutFromSetup()` under a fifteen-line comment explaining why (`:4191-4206`). Same `setPointName` call, same guard, one rebuild | reproduces (filed as out of `DAY`'s range) |

### 1 and 2 September

These are indexed by `docs/reviews/2026-09-03-c-sweep-report.md` and are listed here by reference so
the count is complete rather than to re-argue them.

| Group | Where | What is left |
|---|---|---|
| **Four questions for Adam** | c-sweep report, "What needs Adam" | `V36-C4` (should the editor's "reaches nothing" warning match the runtime's rule); `RG3-C4` / MT-257 item 5 (what shape should "Test Connection" come back in - his own question back); `FV2-C9` / `R28-A1` (should a route-command deletion say what it removed); `DY3-C8` (answered on MT-260, listed only as a pointer) |
| **Eight deferred past 3.0.0** | c-sweep report, "What was dismissed" | `FV2-C1`/`FV2-C7`/`SVN-C11`, `SVN-C9`, `SVN-C10`, `SVN-C12`, `SVN-C15`, `DY3-C4`, `CMT-C2`, `SVN-C14` (part) - each with the reason recorded |
| **Four open Bs, not two** | `2026-09-01-week-of-commits-review.md` | This row undercounted, and that document's own summary table overcounted in the other direction - every one of its seventeen B rows read `open` while nine of the bodies said fixed, with commit hashes in them. Read from the bodies on 2026-09-03: `SVN-B1`, `SVN-B8`, `SVN-B9` and `SVN-B11` were open, and `SVN-B12` is `DAY-B1`. **All four are now fixed** - and `SVN-B11` turned out to be two defects one gesture apart, the second found while writing the test for the first. The table there has been synced to its findings |
| **`TST-B15`** | test-suite round | `AutonomyViewerPanel`'s second `excludeRepeatedSensorPages()` re-excludes a page the operator turned back on; the test that would catch it is the one live `@Test(enabled = false)` |
| **36 test-quality findings** | mostly `TCX` and `D24` | floors that pin the wrong quantity, assertions a fixture guarantees, mutations a fixture cannot tell apart. Being handled by the test-suite audit; `2026-08-31-fanout-index.md` adds that 95 of 200 `MUTATION` claims are unchecked |

### Still open from the two regression rounds themselves

| Finding | Sev | What is left |
|---|---|---|
| `R28-B1` | B | a legacy `autonomy.json`'s per-edge accessory commands are dropped on import and cannot be authored anywhere in 3.0.0 - 69 of Adam's 90 edges, 15 of them naming signals. `grep -rn "addConfigCommand" src/` still returns four hits, none a user interface |
| `R28-B2` | B | **restored by `B4` above** |
| `R28-C2` | C | "Copy Outgoing Edge..." is gone with no equivalent; the HEAD connections submenu has nothing that copies one connection's settings onto another |
| `R28-C3` | C | `SHOW_HOME_LOCOMOTIVES` and `HIDE_REVERSING_EDGES_PREF` survive as dead constants and the home assignment is no longer drawn on the running diagram (settled by `RG3-C6`) |
| `R28-C5` | C | the graph window's Ctrl+S (s88 address) and Ctrl+H (home locomotive) have mouse-only successors; documented, since the Readme block went with them |
| `RG3-C3` | C | the legacy track diagram editor's menu item is removed on only one of the two branches (`TrainControlUI.java:3411` sits after `mountAutonomyControls`'s early return) and its handler is `if (true) return;`. Needs one run on Windows with a Central Station layout |
| `RG3-C4` | C | "Test Connection" - Adam's question, carried in MT-260's tail |
