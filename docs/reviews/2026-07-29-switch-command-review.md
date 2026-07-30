# Switch-command review - 2026-07-29

**Prefix for citing this document: `SWC`.**

**Version reviewed:** commit `2447bb1`, branch `master`. The working tree carries one uncommitted
change (a Readme badge), which is unrelated and was not reviewed. **Scope:** the four commits of
2026-07-29 - `ad9b2bf`/`9c2eafb` ("Fix sequence") and `e8282de`/`2447bb1` ("3 way delay and
sequence") - which are the three-way-turnout round: pair ordering and a delay floor in the route
editor wizard, the same in the CS3 route importer, `sekunde` placement, release-before-throw ordering
of autonomy config commands, and tests. None of these commits had prior review coverage; this is
their first read. Per the commissioning answers, the review then audited **every** switch-command
path, touched or not: the track diagram, the keyboard capture flow, routes, both Central Station
route importers, the layout importer, and autonomy edge configuration.
**Reviewed:** 2026-07-29. **No code was changed as part of this review, and no tests were run** (see Resolution below) - the
author builds and tests in NetBeans. Claims below were verified by reading the enforcing method, or
by scripts run against the real fixture data, per [README.md](README.md).

**Ground truth, as stated by the author for this review:** a three-way turnout is driven by two
commands, and *the straight (releasing) command must always precede the turn (throwing) command*.
The delay between them is a safeguard against network latency; its exact figure is not critical.
Findings below are judged against that statement, not against the 350ms constant.

**The commissioned question** - what new errors did the changes themselves introduce - is answered
directly in the comparison section: **no functional regression was found in the changed code**; the
round's two new errors are both false claims in prose (`SWC-C3`, `SWC-C7`), and every functional
finding below predates the round and was reachable before it.

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| SWC-B1 | The CS2 flat-file route importer (`parseRoutes`) is the unfixed twin of everything this round fixed in the CS3 importer: a three-way at stellung 0 emits one command with no release of the sibling drive, the `setting >= 2` pair is emitted `id+1`-then-`id` with no gap floor, and its `sekunde` is applied through the first-match address search the CS3 fix explicitly abandoned | B | **Partly fixed.**  The first-match `setDelay(address, ms)` is fixed; the three-way semantics are closed by the author - `parseMags` keeps no `typ`, and a three-way reaches the user through the track diagram rather than the accessory record, so this half is metadata, not a defect |
| SWC-B2 | The same importer truncates `sekunde` to whole seconds before converting to ms (`Float.valueOf(kv[1]).intValue() * 1000`), so the fractional pauses present in the real fixtures (2.3, 3.2) lose their fraction and a pause under one second parses as no pause at all | B | Fixed |
| SWC-B3 | The capture-commands flow is a third builder of three-way pairs: captured pairs carry no delay floor, and `filterConfigCommands`' dedup (first-seen position, latest value) inverts a captured pair into throw-before-release when the user clicks a three-way through more than one position while capturing | B | Fixed - `filterConfigCommands` now keeps the last-written value at the last-written position, so the two rules agree |
| SWC-B4 | A three-way added on the route editor's condition tab can never parse: the wizard emits its two lines with no operator between them, and `NodeExpression.fromTextRepresentation` then fails the whole condition at save | B | Fixed - the pair joins with the operator in a condition, via the extracted `RouteEditor.threeWayEntry` |
| SWC-C1 | The layout importer seeds a three-way's first drive from `c.getState() != 1`, which is wrong for state 2: a three-way saved at "right" (a real fixture case) starts in the database with both drives believed thrown | C | Fixed - seeded through `LayoutDiagramComponent.getPrimaryDriveState`/`getSecondaryDriveState` |
| SWC-C2 | The diagram click handler runs `execSwitching` on the EDT via `invokeLater`, so every three-way click freezes the UI for the 350ms inter-drive sleep (and the power-on path sleeps a further full second) | C | Fixed - dialogs stay on the event thread, the sleeps and both sends move together to a single-thread worker (`LayoutLabel.submitSwitching`) |
| SWC-C3 | The changelog entry claims the fix covers routes "parsed via the CS2"; on CS2-firmware devices the unfixed `parseRoutes` (SWC-B1) is the parser that runs, so the claim is false for exactly the users who would read it as theirs | C | Fixed - the changelog no longer claims the CS2 parse path |
| SWC-C4 | `Accessory.isThrow`'s javadoc says "two callers, and they must agree", but the same predicate also lives inline in `validatePathActuation` and `handleMisconfiguredPath`; a future change to `isThrow` would silently diverge command ordering from actuation confirmation | C | Fixed - both inline copies now call `isThrow`, and the javadoc no longer says two callers |
| SWC-C5 | The release-before-throw sort is per-edge; a pair whose two commands are authored on different edges of the same path executes in edge order, so the guarantee does not hold across edges - a limitation the code comment does not state | C | Fixed - the edge boundary is stated at the sort |
| SWC-C6 | Every three-way pair execution now logs a `route.delay` line, because the 300ms floor exceeds the 150ms logging threshold in `execRoute` - log noise on every routine execution of a route containing a three-way | C | Won't fix - accepted by the author.  Any threshold that hides the floor also hides an operator's deliberate 200-300ms pause |
| SWC-C7 | The new `THREEWAY_ROUTE_DELAY_MS` comment states "Both places that build a three-way pair use this: the CS2 file importer and the route editor" - both halves are wrong: the importer that uses it is the CS3 one, and capture (SWC-B3) is a third builder that does not | C | Fixed - the comment names the CS3 importer and the two builders that do not use the constant |
| SWC-D1-D9 | Clean checks on the four commits and the surrounding machinery | D | Verified clean |

No A findings. The B findings are all pre-existing and all reachable only in specific configurations;
none is a regression from this round.

**Resolution - 2026-07-29, after this review.** Every finding was re-verified against the enforcing
code before being acted on, and all eleven held. Nine were fixed outright; `SWC-B1` was fixed in part
and otherwise closed, and `SWC-C6` was closed - the table above carries each disposition.

Two of the fixes were narrower or wider than the finding described, and the difference is worth
recording. `SWC-B3`'s cause is not the dedup's ordering as such but that it keeps *the latest value*
at *the earliest position*, two rules that disagree; making the position follow the value fixes the
inversion without special-casing three-ways. `SWC-B1`'s three-way half is not merely unfixed but
unfixable as the code stands: `parseMags` collapses `dreiwegweiche` to `accessoryType.SWITCH`, so the
CS2 route parser has no way to recognise a three-way at all.

Nine tests were added: `testAFractionalPauseKeepsItsFraction`, `testASubSecondPauseIsNotLostEntirely`
and `testAPauseLandsOnItsOwnItemNotAnEarlierOne` (`testParseCS2Routes`);
`testCapturingAThreeWayKeepsReleaseBeforeThrow`, `testAConditionalThreeWayCanBeParsed` and
`testTheRouteFormOfAPairHasNoOperator` (`testRouteRoundTrip`);
`testAThreeWayNeverImportsWithBothDrivesThrown` and `testAnOrdinaryTurnoutStillSeedsFromStateOne`
(`testParseCS2Layout`); and `testDiagramSwitchingRunsOffTheEventThreadOneAtATime` (`testLayoutTiles`).
The first four fail against the code as reviewed - verified by simulating the old expressions rather
than assumed. The other five exercise methods the fixes introduce, so no pre-fix code path existed for
them to fail on; each instead pins the old behaviour inside itself, and that limitation is stated here
rather than left for a reader to infer. This closes the testing gaps named at the end of this document.

The prose under each finding below is the original record of what was believed when the review was
written, and has not been edited.

---

## The four commits, verified

What the round did, checked claim by claim in the enforcing methods. These checks are the
substance behind "no new errors"; the details are in the D section.

**`Layout.configureEdge`** ([Layout.java:1394](../../src/org/traincontrol/automation/Layout.java))
now sorts an edge's config commands released-before-thrown, then by name. The comparator is a valid
total order (partition by `isThrow`, tie-break `compareTo`); both the preview pass and the execution
pass run the same sorted loop, so conflict detection still sees the order execution will use; values
in the map cannot be null (validated at load), so `isThrow` inside the comparator cannot throw. With
`CONFIGURE_SLEEP` = 150ms between commands, a pair on one edge now executes straight-then-turn,
150ms apart, and possibly further apart when other released accessories sort between them. Correct
against the ground truth; the residual is SWC-C5.

**`Accessory.isThrow`** ([Accessory.java:97](../../src/org/traincontrol/base/Accessory.java)) is a
pure extraction of the predicate `setState` already used; behaviour is identical. The residuals are
its javadoc (SWC-C4) and nothing else.

**`RouteEditor.addAcc`** ([RouteEditor.java:1284](../../src/org/traincontrol/gui/RouteEditor.java)):
all three wizard positions now emit the released drive first - the LEFT case was the one that
emitted throw-then-release before, and its swap is the changelog's "fail to turn left" fix. The
floor `max(typed delay, THREEWAY_ROUTE_DELAY_MS)` sits on the first line of the pair, which is where
`execRoute`'s sleep-after-each-command semantics need it. The emitted lines round-trip through
`RouteCommand.fromLine`/`toLine` with the delay intact. The operator's typed delay spaces the pair
itself rather than following it - the opposite placement from the importer's `sekunde` - which the
new comment endorses explicitly as a floor-not-default decision, so it is recorded as a decision in
D6, not a defect.

**`MarklinRoute.THREEWAY_ROUTE_DELAY_MS`**
([MarklinRoute.java:54](../../src/org/traincontrol/marklin/MarklinRoute.java)): the arithmetic was
checked against `execRoute`'s actual sleep, which is `SLEEP_INTERVAL + delay` when the command's
delay exceeds `DEFAULT_SLEEP_MS` - so 350 - 50 = 300 produces a true 350ms gap, and the comment's
claim that lowering the constant below `DEFAULT_SLEEP_MS` would make it silently inert is accurate
(D1).

**`CS2File.parseRoutesCS3`**
([CS2File.java:1226](../../src/org/traincontrol/marklin/file/CS2File.java)): all four stellung
branches were traced individually. Every dreiwegweiche position emits release before throw; the
floor sits on the first command of the pair in every branch; `sekunde` lands on the item's *last*
command in every branch, so it can never overwrite the pair gap; three-aspect signals
(`states == 3`, typ not `dreiwegweiche`) get the `sekunde` relocation but deliberately not the
floor; a stellung outside 0-3 leaves `lastForItem` null and skips the `sekunde` cleanly (D2). The
old `setDelay(address, ...)` first-match hazard - `CR-A1`'s subject - is avoided by construction,
holding the command object instead.

**The tests** (`testParseCS3Routes`, `testAccessory`): deterministic assertions, preconditions
asserted (the fixture must contain a plain turnout; exactly one parsed route), and the
both-thrown-forbidden property is asserted across *every* stellung rather than the two interesting
ones. The fixture-consistency question a reviewer should ask - why did `TC_routes.json` not need
updating when the parser's output changed? - has a verified answer in D3.

---

## B findings

### SWC-B1 - the CS2 flat-file importer is the unfixed twin

**Where:** `CS2File.parseRoutes` ([CS2File.java:794-832](../../src/org/traincontrol/marklin/file/CS2File.java)).
**Parser selection:** `MarklinControlStation.syncWithCS2` picks this parser whenever the device is
not a CS3 ([MarklinControlStation.java:1005-1012](../../src/org/traincontrol/marklin/MarklinControlStation.java)) -
so everything this round fixed in `parseRoutesCS3` is absent for CS2-firmware devices.

Three separate gaps, judged against the ground truth ("two commands, straight always preceding the
turn"):

1. **Stellung 0 emits one command.** `r.addAccessory(id, accType, true)` and nothing else - no
   release of `id + 1` at all. If the sibling drive is over (the turnout was at "right"), it stays
   over, and the turnout is left in the both-thrown state the whole round exists to prevent. This is
   the "fail to turn left" symptom, on the parser the changelog names.
2. **The `setting >= 2` pair is emitted `id + 1` first, then `id`, with no gap floor** - and per the
   in-code state table, stellung 2 means both drives red, so if a CS2 file ever encodes a
   dreiwegweiche this way the parse *commands the forbidden state deliberately*. The pair's
   `sekunde` is applied via `r.setDelay(id + 1, delay)` - the first-match address search whose
   wrongness the CS3 fix's own comment spells out.
3. **No `THREEWAY_ROUTE_DELAY_MS` anywhere**, so even a well-ordered pair fires 200ms apart.

**Reachability, checked against the real data rather than assumed:** the repo's two real
`fahrstrassen.cs2` files (the test fixture and the *Oles kreds* layout) contain stellung values 1
and 3 and absent-only; stellung 3 occurs solely on a `formsignal_HP012` pair, where both-released is
correct and ordering is immaterial; and **no route in either file references a dreiwegweiche at
all** (the one three-way in `test/magnetartikel.cs2`, address 5, appears in no route). So no file in
the repository triggers any of the three gaps today. It is also genuinely unknown how a real CS2
encodes a three-way route item - possibly as two independent one-drive items, in which case gap 1
never fires and correctness depends on the file's own item order. Severity B, not A, for exactly
that reason; the finding is that the CS2 side gives none of the round's guarantees, not that a
specific file misbehaves. Whether it matters in practice needs one real CS2 file with a three-way
route, which nobody has yet produced.

### SWC-B2 - `sekunde` truncated to whole seconds in the CS2 importer

**Where:** [CS2File.java:763-766](../../src/org/traincontrol/marklin/file/CS2File.java):
`delay = Float.valueOf(kv[1]).intValue() * 1000;` - the float is truncated to an int *before* the
multiply. `sekunde=2.3` parses as 2000ms; `sekunde=0.5` parses as 0, and the `delay > 0` guards then
skip setting any delay at all. The CS3 importer does it correctly
(`Float.valueOf(f * 1000).intValue()`), so the two parsers disagree about the same operator input.

**This does happen:** both real fixtures carry `sekunde=2.3` and `sekunde=3.2`, so the repository's
own data loses 300ms and 200ms of an operator's tuned pause on every CS2 import. Sub-second pauses -
plausible for exactly the accessory-spacing purpose this round was about - are lost entirely.
`testParseCS2Routes` asserts no delays anywhere, so nothing pins this. Pre-existing; first coverage.
Found by reading the importer for SWC-B1, which is the standing argument for auditing a fix's twin
rather than trusting it.

### SWC-B3 - capture is a third builder of three-way pairs, and dedup can invert one

**Where:** the capture hook in `TrainControlUI.repaintSwitch`
([TrainControlUI.java:2709-2737](../../src/org/traincontrol/gui/TrainControlUI.java)) →
`RouteEditor.appendCommand` → `filterConfigCommands`
([RouteEditor.java:232-236](../../src/org/traincontrol/gui/RouteEditor.java)).

With "capture commands" enabled, each `setSwitched` on a three-way's drives lands one line in the
route being edited (both lines arrive: the throttle only suppresses *identical* consecutive
strings). Two consequences:

1. **No floor.** Captured lines are `toAccessorySettingString()` output with no delay, so a captured
   pair executes 200ms apart - the exact gap the round raised to 350ms in the other two builders.
   The capture *order* is safe on a single click, because `execSwitching` itself releases first.
2. **Dedup inverts the pair on multi-click captures.** `filterConfigCommands` keys lines on the text
   before the first comma and keeps *the first occurrence's position with the latest value*. To
   capture "set to right" the user must click through left (the diagram cycles
   straight → left → right), producing `Switch 6,straight` / `Switch 5,turn` / `Switch 5,straight` /
   `Switch 6,turn` - which dedup collapses to `Switch 6,turn` then `Switch 5,straight`:
   **throw before release**, the forbidden transient, now stored in the route. This is not an exotic
   path; it is the natural way to capture a three-way at "right".

The edge editor's capture (`GraphEdgeEdit.appendCommand`) feeds the same dedup, but is rescued at
execution time by this round's `configureEdge` sort - recorded in D7. The route editor has no such
normalisation. Pre-existing; first coverage. The round's own comment claims two builders exist
(SWC-C7), which is how this one went unexamined.

### SWC-B4 - a conditional three-way can never parse

**Where:** `RouteEditor.addAcc(true)` (the "Add as Condition" button,
[RouteEditor.java:1525](../../src/org/traincontrol/gui/RouteEditor.java)) emits the pair as two
lines joined by a bare newline; the `AND` inserted at
[RouteEditor.java:1369](../../src/org/traincontrol/gui/RouteEditor.java) goes only *between
entries*, never between the pair's two lines. `NodeExpression.fromTextRepresentation`
([NodeExpression.java:166-240](../../src/org/traincontrol/base/NodeExpression.java)) pushes each
bare line as an operand and requires exactly one operand left at the end - two adjacent operands
with no operator leave the stack at size 2 and throw. So the first conditional three-way a user adds
makes the whole condition unsaveable, with the generic
`route.ui.errorParsingLogicEnsureParenthesesAndOrTokens` message and no hint that typing `AND`
between the two generated lines would fix it. Reachable in both the old and new pair order;
pre-existing; first coverage. B rather than C because a shipped wizard flow produces guaranteed
failure of the save, not an edge case of one.

---

## C findings

### SWC-C1 - layout import seeds a "right" three-way as both-thrown

**Where:** [MarklinControlStation.java:445-452](../../src/org/traincontrol/marklin/MarklinControlStation.java).
The first drive is seeded `c.getState() != 1`, the second `c.getState() == 2`. For state 2
("right"), the first drive should be straight - the method's own commented-out state table says so -
but `2 != 1` seeds it thrown, so the database opens with both drives believed over. **This does
happen:** `cs2_sample_layout/config/gleisbilder/1 - Main.cs2` element `0x203` is a `dreiwegweiche`
with `zustand=2`. Bounded impact, which is why it is C and not B: it is only the *assumed* state -
`isConfirmedAt` requires a CS echo before autonomy trusts anything, the keyboard and diagram merely
display wrongly until the first actuation, and one click of `execSwitching`'s both-thrown branch
recovers. Wrong is wrong, though, and the fix is one character of comparison.

### SWC-C2 - the three-way inter-drive sleep runs on the EDT

**Where:** [LayoutLabel.java:173-259](../../src/org/traincontrol/gui/LayoutLabel.java) wraps the
click in `SwingUtilities.invokeLater`, and `execSwitching`
([LayoutDiagramComponent.java:132-151](../../src/org/traincontrol/base/LayoutDiagramComponent.java))
calls `accessory.delay(350)` - `Thread.sleep` on the EDT - between a three-way's two commands. Every
three-way click freezes the UI for 350ms; the power-on branch above it sleeps a further 1000ms. The
sleep itself is load-bearing (it is the inter-drive gap), so moving this off the EDT must keep the
two sends and the sleep on one worker - the same care the July cycle's EDT work documented. Fits the
recorded post-v2.7.3 EDT roadmap; pre-existing; noted here because a full switch-command audit
should say where the delays actually elapse.

### SWC-C3 - the changelog claims the CS2 parse path was fixed

The new Readme entry reads "created via the route editing wizard **or parsed via the CS2** would
sometimes fail to turn left". The parse-side fix is in `parseRoutesCS3`, which runs only for
CS3-firmware devices; on an actual CS2 the running parser is `parseRoutes`, untouched and still
carrying SWC-B1. If "the CS2" means the Central Station colloquially (as `syncWithCS2` does), the
entry is merely loose; to a CS2 owner it is a false claim, the kind the README's changelog rule
exists for. Two-word fix ("via the Central Station" → or name the CS3), or fix SWC-B1 and make the
sentence true.

### SWC-C4 - `isThrow` has inline twins the javadoc denies

[Accessory.java:90](../../src/org/traincontrol/base/Accessory.java) says "Two callers, and they must
agree." True of *callers*, but the predicate itself is duplicated inline at
[Layout.java:1792](../../src/org/traincontrol/automation/Layout.java) (`validatePathActuation`) and
[Layout.java:1876](../../src/org/traincontrol/automation/Layout.java) (`handleMisconfiguredPath`) -
the two places that decide what "confirmed at the commanded state" means. They must agree with
`setState` just as hard as the sort must; today they do, and nothing but convention keeps it so.
Route both through `isThrow` and the javadoc becomes true as written.

### SWC-C5 - the ordering guarantee is per-edge only

`configureAndLockPath` ([Layout.java:1700-1735](../../src/org/traincontrol/automation/Layout.java))
configures edges in path order; the new sort orders commands *within* one edge. An operator who puts
"Switch 5 turn" on one edge and "Switch 6 straight" on a later edge of the same path gets
throw-before-release across the pair, 150ms apart, with nothing to object. The new comment says
pairs are unrecognisable to this method - true - but does not say the guarantee stops at the edge
boundary. Recorded so the limitation is a stated decision rather than a discovery; the practical
advice (author both commands of one turnout on one edge) belongs wherever config commands are
documented.

### SWC-C6 - every three-way pair execution logs a delay line

`execRoute` logs `route.delay` for any command whose delay exceeds `DEFAULT_SLEEP_MS`
([MarklinRoute.java:476-481](../../src/org/traincontrol/marklin/MarklinRoute.java)). The 300ms floor
is above that threshold by design, so every execution of every route containing a three-way now
emits a log line for what is routine spacing, indistinguishable from an operator's deliberate pause.
Cosmetic; worth either a lower log level for floor-valued delays or acceptance on purpose.

### SWC-C7 - the round's coverage comment states two false facts

[MarklinRoute.java:48](../../src/org/traincontrol/marklin/MarklinRoute.java): "Both places that
build a three-way pair use this: the CS2 file importer and the route editor." First, the importer
that uses it is `parseRoutesCS3`; the CS2 file importer (`parseRoutes`) does not (SWC-B1). Second,
capture builds pairs too and uses nothing (SWC-B3). This is the July cycle's documented shape - a
fix's own comment claiming coverage it does not have (cycle summary, signature-error instance 6) -
and it is one of the two genuinely *new* errors this round introduced. The comment should either
enumerate honestly (CS3 importer, wizard; capture and CS2 importer outstanding) or point at this
document.

---

## D - clean checks and decisions

**D1 - the delay arithmetic.** `execRoute` sleeps `SLEEP_INTERVAL + rc.getDelay()` when the delay
exceeds `DEFAULT_SLEEP_MS`, else `SLEEP_INTERVAL + DEFAULT_SLEEP_MS`
([MarklinRoute.java:474-492](../../src/org/traincontrol/marklin/MarklinRoute.java)). 350 - 50 = 300
therefore yields a true 350ms pair gap, and the constant's self-description - including the warning
that lowering it below 150 would silently disable it - matches the code it describes.

**D2 - all four CS3 stellung branches.** Traced individually for order, floor placement, and
`sekunde` interaction; release precedes throw in every dreiwegweiche position, the floor is never on
the item's last command, `sekunde` is always on the item's last command, so neither can clobber the
other. Signals with `states == 3` get the `sekunde` relocation but not the floor, per the comment's
stated reason. A stellung outside 0-3 emits nothing and skips the `sekunde` without the spurious
`route.keyNotFound` log the old address search would have produced.

**D3 - why the fixture comparisons still pass.** Checked against the real data: no route in
`CS3_automatics.json` references the fixture's one dreiwegweiche, and *no mag item in any of its 50
routes carries `sekunde` at all* (script over the JSON, 0 of 0). So the parser changes alter the
parse of no fixture route, which is why `TC_routes.json` needed no update and `testSameLength` /
`testCS2` / `testCS3` still pass. The new synthetic tests exist precisely because the fixtures
cannot reach the changed code - their header says so, and it is accurate.

**D4 - the address-295 test does not repeat the 291-293 mistake.** The July cycle recorded a test
that wrongly assumed addresses 291-293 free (`init` restores the real database). The new
`testWhichSettingsThrowAnAccessory` uses 295 without clearing it first - but this is safe, and was
verified rather than assumed: `newSwitch` → `newAccessory` → `RemoteDeviceCollection.add`
([RemoteDeviceCollection.java:41-60](../../src/org/traincontrol/base/RemoteDeviceCollection.java))
replaces any existing accessory at that UID cleanly, unstranding both maps, and the test's
assertions depend only on the fresh object it just created.

**D5 - preview and execution agree.** `configureEdge`'s validation pass and its execution pass are
the same sorted loop, so the conflict history is built in execution order; the comparator is
consistent (`aThrows == bThrows` falls through to name order) and cannot see a null setting because
`Layout.fromJSON` validates every config command at load.

**D6 - the operator-delay placement asymmetry is a decision, not a drift.** The wizard puts the
typed delay *between* the pair (floored); the CS3 importer puts `sekunde` *after* the pair. Both
comments state their reasoning; the wizard's placement also preserves its own pre-round behaviour.
Recorded so a future reader does not "fix" one to match the other without knowing both were chosen.

**D7 - the edge editor's capture is rescued by the sort.** `GraphEdgeEdit.appendCommand` feeds the
same `filterConfigCommands` dedup as SWC-B3, but edge config commands are executed only through
`configureEdge`, whose new sort normalises order regardless of text order - and the dedup semantics
(one setting per accessory) match what an edge means. The hazard is confined to the route editor.

**D8 - no other executors.** Grepped for consumers of `getConfigCommands`: `configureEdge` is the
sole executor; `validatePathActuation`, `handleMisconfiguredPath`, `getActiveAccs`, `HomeStaging`'s
planner, `Edge.toJSON` and the two editors are read-only over it. Route commands execute only
through `execRoute`; accessory sends go through `setAccessoryState`/`setSwitched` →
`MarklinControlStation.exec`, which transmits synchronously in call order
([MarklinControlStation.java:2042](../../src/org/traincontrol/marklin/MarklinControlStation.java)) -
no queue exists that could reorder a pair behind the sleeps.

**D9 - capture order on a single click is safe.** `execSwitching` releases before throwing in all
three cycle branches, the two `repaintSwitch` calls arrive in send order on the EDT queue, and the
capture throttle only suppresses identical consecutive strings - so a single click captures the pair
complete and correctly ordered. The inversion in SWC-B3 needs a second click, which reaching "right"
requires.

---

## Comparison with the existing record

The existing documents were searched for this ground: **no document in this folder mentions
three-way turnouts at all** (grep over `*.md`: zero hits for three-way/threeway/dreiweg), so every
finding above is first coverage of this machinery. The nearest prior work:

- **`CR-A1`** fixed `MarklinRoute.setDelay` skipping address-less commands; its writeup documents the
  same first-match-by-address limitation this round's CS3 fix now sidesteps by construction. The CS2
  importer still calls `setDelay` by address (part of SWC-B1) - `CR-A1` made that call not-crash,
  not correct.
- **`CR-A5`** and the `FCR` per-record catch work covered `parseRoutes`' condition handling and
  error containment, never its accessory-pair semantics. `CR-A5`'s downgrade note is also the
  precedent for how SWC-B1's reachability is stated: checked against the real files, found
  untriggered, recorded with the evidence.
- **`TCR`/`WR`** (2026-07-28) end at commits before this round; the four commits reviewed here had
  no prior reader.

**New errors introduced by this round - the commissioned question.** None functional. The changed
code was checked line by line against `execRoute`, the stellung table, the fixtures, and the
round-trip parser, and no regression was found (D1-D5, D9). The round introduced two errors in
prose: **SWC-C3** (a changelog claim that is false on CS2-firmware devices) and **SWC-C7** (a
coverage comment naming the wrong importer and denying the third pair-builder exists). Both are the
July cycle's signature shape - a fix whose own text claims one-more-entrance coverage than it has -
and both are one-line fixes. The pattern behind the B findings is the same signature error in its
older form: the fix is right everywhere it was applied, and the entrances it was not applied to
(CS2 importer, capture, the condition tab) are where everything in this report actually lives.

**Testing gaps, named so they are not mistaken for coverage:** `testParseCS2Routes` asserts nothing
about three-ways or delays (SWC-B1, SWC-B2 are unpinned); no test drives the capture flow
(SWC-B3); no test parses a wizard-emitted conditional pair (SWC-B4). If the B findings are fixed,
each fix wants its failing test first, per the README - and the CS2 ones can reuse
`testParseCS3Routes`' synthetic-route pattern, which exists because fixtures without three-way
routes cannot pin any of this.
