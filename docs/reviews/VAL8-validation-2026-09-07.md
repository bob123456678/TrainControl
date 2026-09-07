# VAL8 - Validation of the 2-day window against docs/reference/behaviour.md

**Status:** open

**Prefix:** VAL8. Cite these findings as VAL8-A1, VAL8-B2, etc.

**Version reviewed:** branch `autonomy-diagram-r0` at `65bd3161` (2026-09-06), the 54 commits since
`fc836441~1`. Method: reading only - code, diffs and the spec. No tests were run, nothing was built,
and no file outside this report was changed. The spec is `docs/reference/behaviour.md` as committed in
`738ef56f`; where this report says "spec sX" it means that document's section X.

CONFIRMED means the claim was traced through the code end to end. PLAUSIBLE means the mechanism is
real in the code but the reachable consequence depends on state or timing this review could not
execute.

## Summary

| ID | Sev | One line | Confidence | Disposition |
|----|-----|----------|------------|-------------|
| VAL8-A1 | A | Tail blocking covers one directional edge per hop, and prefers the direction only the standing train itself can use; routes over the tail in the other direction pass `isPathClear` | CONFIRMED (trace) | Open |
| VAL8-A2 | A | `arrivedFrom` never crosses between setup and running layout: the builder does not emit it, capture does not carry it, and the placement prompt's answer is written to the setup only - so it is silently lost on every rebuild while the menu keeps showing it | CONFIRMED | Open |
| VAL8-B1 | B | The "Train arrived from" menu cannot appear in the autonomy editor - `setRunningLayoutSource` is only ever called from the track-diagram door; `setFacingAndMove` likewise degrades to setup-only until the diagram menu has been opened once per session | CONFIRMED | Open |
| VAL8-B2 | B | The deferred direction-follow keeps a pre-run baseline that outlives `captureFromLayout`; a capture between run end and the next echo makes the follow undo a reversal that was already reconciled, on the setup and the running layout both | PLAUSIBLE | Open |
| VAL8-B3 | B | The covered-track grey wash is baked into an icon-refresh path that nothing drives when coverage changes; plain track tiles get no icon refresh on arrival or departure, so the greying does not track the trains | CONFIRMED (trace) | Open |
| VAL8-B4 | B | Neither `moveLocomotive` nor the hand-placement door clears or sets the live Point's `arrivedFrom`, so a newly placed train inherits the previous occupant's recorded side and the walk blocks the wrong rail from stale data | CONFIRMED (trace) | Open |
| VAL8-C1 | C | Spec s4 "an unrecorded tail blocks nothing" is overstated: with `arrivedFrom` null the walk still blocks when geometry forces one way back - the code is defensible, the document is wrong | CONFIRMED | Open |
| VAL8-C2 | C | First-hop side matching can guess: two neighbours on one compass side take the first candidate; and without a page list the builder collapses all pages to one, so cross-page `sideTowards` is computed from meaningless deltas - a limit the spec does not state | PLAUSIBLE | Open |
| VAL8-C3 | C | Stale comment at `Layout.java:2290` still says a manual route "may still FINISH on" an inactive point, contradicted by the unfenced refusal 45 lines below it and by spec s2 | CONFIRMED | Open |
| VAL8-C4 | C | `isTrackCovered(String,int,int)` has no callers | CONFIRMED | Open |
| VAL8-C5 | C | `Point.arrivedFrom` is a plain field written cross-thread; the field directly above it is `volatile` with a comment explaining why for exactly this pattern | CONFIRMED (visibility gap; consequence PLAUSIBLE) | Open |
| VAL8-C6 | C | The add-to-autonomy door reads `facingOf(name)` setup-only while its sibling was fixed to ask the running layout first (CONF-B1); mostly moot for un-placed locomotives, but it is the unswept sibling | CONFIRMED (gap), PLAUSIBLE (consequence) | Open |
| VAL8-C7 | C | Three of the four placement doors never work out `arrivedFrom` at all; spec s4 says a hand placement works it out. Only the drag/paste door asks (and A2 makes even that answer inert) | CONFIRMED | Open |
| VAL8-C8 | C | `HomeStaging` applies the berth rule but not the tail rule, so a staging plan can be refused at execution by the covered-track check it never modelled | PLAUSIBLE | Open |
| VAL8-C9 | C | "Keep direction" honoured at an intermediate may-reverse TURNING copy leaves the train un-turned on a path whose onward edges assume the turn - the same geometry hazard s3 describes for compulsory turns; could not confirm manual path selection offers such a path | PLAUSIBLE | Open |
| VAL8-D1 | D | Spec s1 verified: `isAutoDestination` appears nowhere in HomeStaging; the inactive-start refusal is present and commented as deliberate | CONFIRMED clean | Open |
| VAL8-D2 | D | Bundles clean: 8 files, identical key sets, pure ASCII, no lone apostrophes in the touched MessageFormat patterns, every key the new code references exists | CONFIRMED clean | Open |
| VAL8-D3 | D | The berth length rules match spec 5a/5b at all four boundaries, in both `measuredRoomToReverseInto` and `GraphReducer.roomAfterTheLastSwitch` | CONFIRMED clean | Open |
| VAL8-D4 | D | The reversal prompt matches spec s3 on every stated point: departure-time, once per journey, destination named, Yes-keeps default, Escape/X/cannot-show keep, terminus journeys never asked, autonomy and Return Home never asked | CONFIRMED clean | Open |
| VAL8-D5 | D | Coordinates now always emitted: nothing outside `Point` consumes `coordinatesSet()`; the only runtime reader of x/y is `sideTowards`. No dependency on their absence found (C2 carries the page-collapse caveat) | CONFIRMED clean | Open |
| VAL8-D6 | D | Inactive-square rules match spec s2 at the runtime and in both editor walks; the start is exempt everywhere it should be | CONFIRMED clean | Open |
| VAL8-D7 | D | The tail walk stops at forks and unmeasured track and never blocks the train itself in `isPathClear`, as 5c requires - modulo A1 | CONFIRMED clean | Open |

Grade of the window overall: the length rules, the inactive rules, the reversal-prompt semantics and
the bundles are in genuinely good agreement with the spec - the disagreements cluster almost entirely
around `arrivedFrom` and the tail walk, which are the youngest features in the window (most were
written the same day the spec was).

---

## A findings

### VAL8-A1 (A, CONFIRMED by trace): tail blocking is direction-asymmetric, and prefers the useless direction

Spec 5c: *"The walk ... follows track regardless of direction - a tail fouls the rail whichever way
traffic runs."* The walk follows regardless of direction; what it **blocks** does not.

Mechanism, all in `Layout.java`:

- `edgesCoveredByStandingTrains()` covers exactly one `Edge` object per hop:
  `src/org/traincontrol/automation/Layout.java:5371` (first match wins, then `break`) in the
  `arrivedFrom` branch, and `:5403` (`if (neighbours.add(...)) segment = candidate` - one edge per
  distinct neighbour) in the deterministic branch, feeding one `covered.put(segment, loc)` at `:5413`.
- `Edge.equals`/`hashCode` are name-based and the name is directional
  (`src/org/traincontrol/automation/Edge.java:228-244`), and `isPathClear` looks path edges up by that
  identity (`src/org/traincontrol/automation/Layout.java:2418`). So the opposite-direction edge over
  the same physical rail - the doubled edge of spec s8, or the sibling-copy edge on a built graph - is
  not in the map and is not refused.
- Worse, the candidate list is `getNeighborsAndIncoming` (`Layout.java:2081`), which returns
  **outgoing edges first**. Where both directions exist toward the matched side, the walk covers the
  edge pointing *away from* the standing train - an edge only a train standing on that very square
  could ever use - and leaves the incoming edge, the one an approaching train actually uses, clear.
  The turned-train case (`arrivedFrom` was invented for the turned 2-8-4) is precisely a case where
  the standing copy has an outgoing edge on the arrival side, so the wrong pick is the live one.

Why the tests pass: every fixture in `test/core/testATrainCoversTheTrackBehindIt.java` builds
single-direction chains (`createEdge(far, near); createEdge(near, berth)` at :504-505), so the only
candidate is the incoming edge and the rule works. On one-way running - much of the real layout - the
same holds, which is why the real-layout proof (`52410cbe`) also passed.

The greying disagrees with the guard: `AutonomySession.tilesCoveredByStandingTrains`
(`src/org/traincontrol/automationui/AutonomySession.java:4806`) matches reduced edges in **both**
directions (`sameWay || otherWay`), so the diagram greys track that `isPathClear` will happily route
the other way over. That is the guard-and-affordance split this codebase keeps filing against itself,
between two features that shipped in the same commit window.

Fix direction (for Adam to weigh): cover the edge AND its opposite (`getEdge(e.getOppositeName())` is
already how `isPathClear` handles occupancy symmetry at `Layout.java:2211`), or make the `isPathClear`
lookup symmetric. Note sibling-copy edges on built graphs are a harder case than the doubled-edge one.

### VAL8-A2 (A, CONFIRMED): `arrivedFrom` never crosses between the setup and the running layout

There are two records of the tail's side and no path between them:

- **The builder does not emit it.** `arrivedFrom` appears in
  `src/org/traincontrol/automationui/AutonomyBuilder.java` only inside a comment (line 905); the
  emitted point JSON never carries the key, so `parseAuto`'s reader
  (`src/org/traincontrol/automation/Layout.java:8238`) has nothing to read on any built layout.
- **Capture does not carry it.** `captureFromLayout` writes placements and facings back to the setup;
  it never touches `arrivedFrom` (no occurrence in `AutonomySession.java` beyond the property
  accessors at :5185/:5194).
- **The placement prompt writes the setup only.** `TrainControlUI.rememberPlacement` records the
  operator's answer with `session.setArrivedFrom(...)`
  (`src/org/traincontrol/gui/TrainControlUI.java:6198`) and never sets the live `Point` - so the
  answer the dialog just collected blocks nothing, ever. The only live-Point writers are the arrival
  hook (`Layout.java:6438`) and the right-click menu
  (`src/org/traincontrol/gui/AutonomyEditorPanel.java:2925-2946`, which writes both).

Consequences: (1) a hand placement on a may-reverse square asks the question and discards the answer -
exactly the defect shape SPEC-B1 was about a dialog whose answer is thrown away; (2) every rebuild of
the running layout (configuration load, editor close) silently drops the sides autonomy recorded on
arrival and the sides the operator set from the menu, while `buildArrivedFromMenu` goes on showing the
setup's value as ticked - the UI reports a protection that is off; (3) nothing survives a restart
except the setup copy, which nothing reads back into the railway.

Spec s4 says the property is settable, assumable and asked-for; nothing in it licenses a value that
displays but does not block. The session's own javadoc ("it travels with them through a save and a
load", `AutonomySession.java:5176-5182`) is true of the JSON and false of the railway.

---

## B findings

### VAL8-B1 (B, CONFIRMED): the "Train arrived from" menu cannot appear in the autonomy editor

Spec s4: *"It can be set or cleared afterwards from Train arrived from in the right-click menu, on
both the editor and the track diagram."*

`buildArrivedFromMenu` returns null when it has no running layout
(`src/org/traincontrol/gui/AutonomyEditorPanel.java:2903-2907`), and the panel's `runningLayout`
supplier is only ever set from `TrainControlUI.buildAutonomyFacingMenu`
(`src/org/traincontrol/gui/TrainControlUI.java:4235` and `:4242`) - a method reached only from the
track diagram's right-click (`LayoutRightclickAutonomyMenu.java:694`). The editor window's own
`AutonomyEditorPanel` instance never receives a supplier, so its popup (`buildTileMenu`, which adds
the tail menu at `AutonomyEditorPanel.java:1176`) silently omits the menu every time.

Same mechanism, second symptom: the **session's** supplier is set in the same two places, so
`setFacingAndMove` (`AutonomySession.java:5156`) degrades to a setup-only `setFacing` - the exact
SPEC-B4 defect it was written to close - for any session in which the diagram's facing menu has not
yet been opened once (e.g. load a configuration, go straight to the editor, change a facing).

Fix direction: set both suppliers where the session is created or swapped, not inside one menu door.

### VAL8-B2 (B, PLAUSIBLE): the deferred direction-follow can undo a reversal that was already captured

`followDirectionChanges` (`src/org/traincontrol/gui/TrainControlUI.java:10031`) deliberately keeps the
pre-run baseline while anything runs (`putIfAbsent` at :10062) so that a reversal made during a run is
followed once the run ends - spec s3, and commit `ca0265f4`. The follow fires at the **first
locomotive message after the run**, which can be arbitrarily later, and nothing clears the baseline in
between. Interleaving that corrupts:

1. A run ends with a net reversal (terminus turn, or a may-reverse "No"). The running layout is
   correct - the run itself moved the train to the turned copy. `lastSeenDirection` still holds the
   pre-run direction.
2. `captureFromLayout` runs first (editor open, `captureRunningLayout` at `TrainControlUI.java:2835`,
   or the shutdown save at :2317) and writes the correct post-run facing into the setup.
3. The next echo for that locomotive arrives. `was != forward`, so `flipFacing` flips the
   just-captured facing to wrong AND `moveOntoFacingCopy` moves the train onto the wrong copy of the
   running layout (`AutonomySession.java:1498`, :1571-1574). Both records now agree and both are
   wrong; the log says the facing "was updated to match".

When no capture intervenes the same late flip is the intended reconciliation and is correct - which is
why this is PLAUSIBLE, not CONFIRMED: the defect needs a capture (or any future writer of the facing)
to win the race. The narrow fix is to reconcile or clear the affected `lastSeenDirection` entries
whenever `captureFromLayout` writes facings, or to resync the baseline at run end.

### VAL8-B3 (B, CONFIRMED by trace): the grey wash does not follow the trains

Spec 5c: *"Covered squares are greyed on the diagram until the train moves."*

The wash is applied only inside `LayoutLabel.setImageOnEDT`
(`src/org/traincontrol/gui/LayoutLabel.java:1009-1013`), i.e. only when a tile's **icon** is rebuilt.
`refreshCoveredTrack` (`TrainControlUI.java:6125`, called from `updateVisiblePoints` at :25339)
replaces the cached set and repaints nothing. The only drivers of an icon rebuild are accessory,
feedback and route state changes (`MarklinAccessory.java:138`, `MarklinFeedback.java:73`,
`MarklinRoute.java:259`) - and the tiles a tail covers are, in the ordinary case, plain track between
sensors, whose state never changes. The autonomy overlay path (`setAutonomyOverlay` -> `repaint()`,
`LayoutLabel.java:1159-1172`) does trigger `paintComponent`, but the wash is not painted there - it is
baked into the icon that `paintComponent` merely draws.

Net: the wash reflects the covered set as of whenever each tile last happened to rebuild its icon -
correct at grid build, then frozen until an unrelated event. It neither appears when a train arrives
nor clears when it leaves, on exactly the tiles the feature is about. (A covered tile that happens to
hold a signal or sensor will refresh with that device and mask the problem in spots.)

Fix direction: draw the wash in `paintComponent` beside the autonomy overlay (where a `repaint()` is
enough and already happens), or have `refreshCoveredTrack` diff old/new sets and repaint the changed
tiles.

### VAL8-B4 (B, CONFIRMED by trace): stale live-Point `arrivedFrom` is applied to the wrong train

`Point.setLocomotive` and `Layout.moveLocomotive` neither clear nor set `arrivedFrom`; the only
run-time clear is the departure clear inside `executePathInternal`
(`src/org/traincontrol/automation/Layout.java:6446`), which fires only for dispatched journeys. A
train **hand-moved** off a square leaves its recorded side on the Point; hand-place a different train
there later (the drag door sets the session only - see A2) and the walk reads the old train's side for
the new train (`Layout.java:5364-5375`) and blocks a rail chosen from stale data. The walk's own
guard for staleness (:5378-5381) only catches a side with no track, not a wrong side with track.

Blocking on a wrong guess is the outcome both 5b and 5c say must not happen ("a guess that refuses is
still a refusal"). Fix direction: clear `arrivedFrom` in `setLocomotive`/`moveLocomotive` whenever the
occupant changes, and let the placement flows re-set it (which requires A2's bridge to exist).

---

## C findings

### VAL8-C1 (C, CONFIRMED): the document overstates "an unrecorded tail blocks nothing"

Spec s4 says exactly that; the code walks the deterministic branch when `arrivedFrom` is null and
blocks whenever exactly one distinct way back exists (`Layout.java:5386-5407`) - a terminus, or any
dead-end chain. The code's behaviour is the defensible one (geometry forces the answer; nothing is
guessed), so this is a **document** bug: s4 should say an unrecorded tail blocks nothing *except where
the track leaves only one possibility*.

### VAL8-C2 (C, PLAUSIBLE): two ways the first hop can guess

(a) Two neighbours can resolve to the same compass side (`sideTowards` collapses geometry to N/S/E/W,
`Layout.java:5443-5462`); the `arrivedFrom` branch takes the first candidate that matches
(:5369-5373), which is an arbitrary pick between two rails. (b) Without a page list the builder now
emits every page at `page = 0` (`src/org/traincontrol/automationui/AutonomyBuilder.java:929-133`
region), so pages overlap and `sideTowards` across a portal is computed from unrelated deltas. The
in-code comment acknowledges (b); the spec's known-limits paragraph (5c) lists forks and unmeasured
track but neither of these. Self-consistency (the recorded side and the walk's test use the same
function on the same pairs) saves the common case; it does not save two same-side neighbours.

### VAL8-C3 (C, CONFIRMED): stale comment contradicts the code below it and spec s2

`src/org/traincontrol/automation/Layout.java:2287-2293`: "a manually chosen route ... may still FINISH
on one, which is how a route to a parked-up berth is picked." The unfenced destination refusal at
:2335-2344 (this window's own change) forbids exactly that, per spec s2. The comment predates the
change and now teaches the pre-spec rule at the one place a reader checks it.

### VAL8-C4 (C, CONFIRMED): dead overload

`TrainControlUI.isTrackCovered(String page, int x, int y)` (`TrainControlUI.java:6100`) has no callers
anywhere in `src/`; only the `TileKey` overload (:6114) is used (`LayoutLabel.java:1010`).

### VAL8-C5 (C, CONFIRMED gap / PLAUSIBLE consequence): `arrivedFrom` is not volatile

`src/org/traincontrol/automation/Point.java:39`. It is written by the automation thread on arrival
(`Layout.java:6438`) and by the EDT from the menu, and read by whichever thread runs the walk. The
adjacent `currentLoc` (:41-43) is `volatile` with a comment explaining that exact cross-thread
pattern. A stale read here means a tail that briefly blocks nothing (the safe direction), so the
consequence is mild; the inconsistency with the file's own stated standard is the finding.

### VAL8-C6 (C): the unswept `facingOf` sibling

`AutonomyEditorPanel.java:4263` reads `session.facingOf(name)` (setup-only) where the door at :4090
was changed the same day to `facingOf(arriving, layout)` for the reason written on the two-argument
overload (CONF-B1, `AutonomySession.java:5079-5100`). This door lists locomotives autonomy does not
already have, so the stale-after-a-run case is mostly unreachable - but "mostly unreachable" is what
the sibling-sweep rule exists for.

### VAL8-C7 (C, CONFIRMED): three placement doors never work out `arrivedFrom`

Spec s4: "A hand placement works it out." Only the drag/paste door does
(`TrainControlUI.java:6198`). The editor's occupant-edit door (`AutonomyEditorPanel.java:4073-4110`),
the add-to-autonomy door (:4255-4270), and the diagram right-click `placeFacing` door
(`LayoutRightclickAutonomyMenu.java:961-1000`) each record who and which way and never the tail. Under
A2 the asked answer is currently inert anyway, so this is C until A2 is fixed - at which point it
becomes the classic one-door-of-four.

### VAL8-C8 (C, PLAUSIBLE): the staging planner does not model tails

`HomeStaging` asks the berth rule (`HomeStaging.java:1071` calls `measuredRoomToReverseInto`) but
never `edgesCoveredByStandingTrains`; the covered-track refusal lives only in `isPathClear`
(`Layout.java:2414-2431`), which the plan meets at execution. Spec s6 says Return Home "obeys every
length rule in s5"; it obeys 5c only by being refused at run time, so a plan can be built that stalls
on its first move. Whether the planner's hypothetical-future modelling makes this acceptable is
Adam's call; the spec as written does not draw the distinction.

### VAL8-C9 (C, PLAUSIBLE): "keep" at an intermediate may-reverse turning copy

`shouldReverseAt` now honours the operator's answer at intermediate may-reverse squares
(`Layout.java:5272-5281`, the SPEC-B1 repair). A turning copy's outgoing edges leave by the side the
train arrived from - the geometry spec s3 states for compulsory turns holds for the turning copy of a
split square too - so a manual path routed **through** such a copy, answered "keep", continues
un-turned onto track that assumed the turn. This is only reachable if manual path selection offers
paths that transit a may-reverse turning copy mid-journey (autonomy's `pickPath` refuses them; whether
`getPossiblePaths` does for manual sends was not established by this review). If such paths are never
offered, this is dead; if they are, the answer that should have influenced path choice instead
breaks the chosen path.

---

## D findings - checks that came back clean

### VAL8-D1: spec s1's Return Home claims verified

`isAutoDestination` appears zero times in `src/org/traincontrol/automation/HomeStaging.java`; the
inactive-start refusal is at :465 with the deliberate-difference comment (:443-464) matching spec s1's
last paragraph word for word.

### VAL8-D2: bundles clean

All 8 bundles (`en/da/de/es/fr/it/nl/pl`) carry identical key sets; every byte is ASCII; none of the
keys touched in this window that carry `{0}` contains an unescaped single quote; every key the new
code references (`autolayout.ui.side*`, `confirmManualReversal*`, `askArrivalSide*`,
`menuArrivedFrom`, `arrivedFromUnknown`, `facingOnlyOne`, `labelPathType`, `pathType*`,
`errorTrackCoveredByStandingTrain`, `errorInactiveStation`, `route.cancelledByOperator`,
`infoFacingFollowedDirection`, `loc.ui.errorLocomotiveNameUnusable`) exists. The rename of
`errorInactiveStationInAutoRun` to `errorInactiveStation` left no dangling references.

### VAL8-D3: the length rules match 5a/5b

`measuredRoomToReverseInto` (`Layout.java:7047-7139`): exact fit admitted (the caller refuses on
`>` only, :2488); the total counts, not any single tile; an unmeasured non-switch segment ends the
count without cancelling it (:7131, `room > 0 ? room : null`); the segment-bound rule at the last
switch (:7101-7122, `bound = room + segment.getLength()`) refuses more and admits nothing new, exactly
as 5b's last bullet states; nothing-measured-at-all is still not judged. Aligned on the editor side:
`GraphReducer.roomAfterTheLastSwitch` (`GraphReducer.java:1192-1231`) now sums measured tiles and
answers -1 only when nothing at all is measured - the same doctrine, previously the all-or-nothing
`measured` flag the spec quotes Adam correcting.

### VAL8-D4: the reversal prompt matches s3

`ManualReversalPrompt` (`src/org/traincontrol/gui/ManualReversalPrompt.java`): asked at departure from
both doors (`AutoLocomotiveStatus.java:1043`, `LayoutRightclickAutonomyMenu.java:1040` area), on the
EDT, once per journey; names the destination when it is a may-reverse square (:167-186); a journey
ending at a terminus returns `KEEP_DIRECTION` unasked (:144-163); Yes is index 0 and default, and only
an explicit No reverses - `reverseFor(chose) == NO` correctly treats `CLOSED_OPTION` (-1) as keep
(:328-347, the ACC4-1 repair holds); a failed/headless dialog keeps (:439-444). Autonomy and Return
Home flow through `ALWAYS_REVERSE`, which `shouldReverseAt` maps to the bare flag (`Layout.java:5246`)
- never a question. `asksAbout` is answered from the setup (`mayTurnTiles`), never from
`isReversing()`, per s3's "what the runtime cannot answer".

### VAL8-D5: coordinate emission has no absence-dependents

`coordinatesSet()` has no callers outside `Point` itself (grep over `src/`); the graph window that
once consumed x/y is gone; the only runtime reader is `sideTowards` (`Layout.java:5447`), which the
emission exists to feed. The `Point.toJSON` round trip (:1168-1171) was already conditional on
coordinates being set, so older captures without them still parse.

### VAL8-D6: inactive squares match s2

Runtime: destination refused at every door (`Layout.java:2335`, the fence removed this window),
intermediates refused unfenced (:2297-2306), the stricter any-endpoint rule kept behind
`isAutoRunning` (:2243) so the start stays exempt; autonomy also skips inactive-start locomotives at
`runLocomotives` (:1722). Editor: `findPath` and `reachableTiles` both refuse closed squares as
destination and intermediate with the start seeded exempt
(`GraphReducer.java:614-620`, :792-812), and the findings pass `shutTiles()` through
(`AutonomySession.java:4281` region, `AutonomyChecks.java` throughout). Spec s7's Auto-tier note is
implemented as a note beside a drawn route, not a refusal (`AutonomyEditorPanel.java:6204-6237`),
which is what s7 prescribes; `stationsAutonomyWillNotChoose` now carries both halves of the runtime
rule (`AutonomySession.java:3355-3394`, the CONF-B6 repair).

### VAL8-D7: tail-walk stop rules and self-exemption

Stops at a fork counted over distinct unwalked neighbours (`Layout.java:5407`), stops at unmeasured
track (:5411), refuses to guess when the recorded side names no rail (:5378-5381), and the standing
train is exempted at the consumer (`:2420`, `lyingAcross.equals(loc)`). All per 5c - subject to A1's
direction asymmetry, and note the greying deliberately (and correctly, per Adam's "edges, not
points") excludes the endpoint squares (`AutonomySession.java:4836-4840`).

---

*Written by a validation agent, 2026-09-07, from reading only. Dispositions are all "Open" - they are
the main agent's and Adam's to set, not this reviewer's.*
