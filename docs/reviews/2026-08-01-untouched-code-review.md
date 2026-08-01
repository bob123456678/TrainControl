# Independent review focused on code untouched by the July 2026 cycle - 2026-08-01

**Prefix for citing this document: `UC`.**

**Version reviewed: `897c155`, working tree clean, on 2026-08-01.** No code was changed by the
original review; it was a report only. The validation round of the same date (see "Validation of
the resolution", run against fix commit `5c92967`) changed one comment: the `UC-C16` javadoc move.

## Scope and method

The brief: a full independent review of the codebase, weighted toward the ~30 source files that no
July 2026 commit touched, since six review documents already cover that month's changes. The review
was run blind - no prior review document was read before the findings below were drafted (this
folder's README excepted), so overlaps with earlier findings were not known while writing them. The
comparison section at the end was added afterward and says what turned out to be already known.

Verification was static (tracing the enforcing layer, callers, and compensating machinery in
source) plus throwaway Python models where a claim needed measurement; per standing practice no
javac/ant/JUnit was run from this session. One claim was checked against live external data (the
GitHub releases API). Findings that would need a runtime experiment to close are marked as such.

Depth was not uniform, deliberately:

- **Read in full**: every file untouched in July (base condition nodes, `Feedback`,
  `LocomotiveNotes`, `RemoteDevice`, `CANMessage`, `Conversion`, `ImageUtil`,
  `MarklinSimpleComponent`, `ModelListener`, `TrainControl.java`, the three examples, and the 14
  untouched GUI classes), plus the core of the touched set: `Locomotive`, `MarklinLocomotive`,
  `MarklinAccessory`, `MarklinFeedback`, `MarklinRoute`, `Accessory`, `Route`, `RouteCommand`,
  `NodeExpression`, `RemoteDeviceCollection`, `RenameProposals`, `CS2Message`, `NetworkProxy`,
  `CSDetect`, `Util`, `I18n`, `View`, `ViewListener`, `Edge`, `Point`, `TimetablePath`.
- **Read in targeted passes**: `Layout` (structure, BFS/pickPath, rename/delete/move, JSON load),
  `MarklinControlStation` (loc/route lifecycle, save/restore, sync, stats), `TrainControlUI`
  (validators, address change, icon handling, route editing entry points), `RouteEditor` (parse and
  save flow), `GraphViewer` (edit gating), `GraphRightClickPointMenu`.
- **Light or skipped, and why**: `CS2File`, `LayoutDiagram`/`LayoutDiagramComponent` (three
  route/switch-command passes in late July), `HomeStaging` and the home-staging UI (two dedicated
  passes ending 2026-07-31), `GraphViewer` internals beyond edit gating. A future pass wanting
  virgin territory should start at `CS2File`'s parsers with the real fixture files.

Findings are lettered by severity per the README. One B, fifteen C, ten D. No A: nothing found
puts wrong behaviour on the layout or silently loses data.

**Resolution - 2026-08-01.** Every finding was verified against the enforcing code, twelve red
tests were added first and confirmed red by a full suite run (all twelve failed for exactly the
predicted reasons, including UC-C4 failing on one of the two truth assignments this document
named), and then all sixteen findings were fixed - eleven flipping tests green, five (C7-C10,
C13-C15) as reviewed no-test changes.  Contested shapes went as follows: UC-B1 was fixed at the
model (both unguarded writers), not the GUI, so every present and future caller is covered;
UC-C1 in `compareVersions` rather than `parseReleaseVersion`, so the tolerance is not tied to one
caller.  One correction to this document: UC-C8's claim that `MarklinRoute.equalsUnordered` has
zero callers is wrong - `testParseCS3Routes` calls it twice - and it was therefore kept.

**Additional defect found while confirming red, same family as UC-C5 (the model trusting its
caller):** `Layout.setActivateRouteIDs` stored the caller's list verbatim and `deleteRoute`
mutates it; `testAutoLayout` passes `Collections.singletonList`, so that class's teardown has
thrown `UnsupportedOperationException` on every run since 2025-11-29 - invisible, because it is
a TestNG configuration error rather than a test failure, and consequential, because the fixture
route survived undeleted.  Fixed with a defensive copy; pinned by
`testDeletingAnActivationListedRouteSurvivesAnImmutableList` in `testRoutes`.

**Fix-round regression and a second latent defect, 2026-08-01.** The first UC-C4 fix parenthesized
cross-operator children in the serializer; `testRoutes.testExpressions` - which pins structural
identity across the text round trip for randomized text-origin expressions - caught that
text-origin trees themselves contain bare cross-operator nestings, so the serializer change
altered their rendering too.  Replaced as the status above records; the serializer is
byte-identical to before the round.  The same run surfaced `testJSONExportImport` failing on a
locdir command with DELAY=0: a pre-existing 1-in-2000 flake, not a regression - `setDelay(0)`
stored the key while the line round trip drops it, giving "no delay" two unequal
representations.  `setDelay` now treats zero and below as canonical absence; pinned by
`testRoutes.testAZeroDelayIsTheSameAsNoDelay`, red before the change.

---

## B - crashes or incorrect results in specific configurations

| ID | Finding | Status |
|---|---|---|
| UC-B1 | Stale arrival/departure function survives a decoder change and crashes the graph's locomotive-assignment dialog; the dialog that could fix the value is the one that crashes | Fixed at the model, both writers: `setAddress` clears out-of-range functions after the resize, and the full-state constructor validates like the setters.  Pinned by `testDecoderConversionClampsArrivalAndDepartureFunctions` and `testFullStateConstructorClampsArrivalAndDepartureFunctions` |

### UC-B1: stale arrival/departure function number crashes `GraphLocAssign`

`MarklinLocomotive.setAddress` (MarklinLocomotive.java:421) resizes every function array and
updates `numF` when the decoder type changes, but never clamps or clears `arrivalFunc` /
`departureFunc`. `MarklinControlStation.changeLocAddress` (its only caller for user-driven
changes) adds nothing either, and the full-state constructor restores the fields unvalidated
(Locomotive.java:340), so the stale value survives restarts via the locomotive database.

Concretely: an MFX locomotive (32 functions) with arrival function 20 is converted to MM2 (5
functions). `arrivalFunc` stays 20. `GraphLocAssign.updateValues` rebuilds its function combo boxes
with exactly `numF + 1` entries and then calls
`arrivalFunc.setSelectedIndex(loc.getArrivalFunc() + 1)` unguarded (GraphLocAssign.java:143;
same for departure at :162) - `setSelectedIndex(21)` against a 6-entry model throws
`IllegalArgumentException`. The `trainLength` selector four lines below (:151) *is* guarded
against exactly this shape, which is what makes the omission conspicuous.

Both entrances construct the panel *before* showing the dialog (GraphRightClickPointMenu.java:53
and :89), so the exception fires inside the menu action on the EDT: "Edit locomotive at..." and
"Add locomotive..." silently do nothing for any point whose selectable locomotive carries the stale
value, with only a console stack trace. The repair path is wedged - the assignment dialog is where
arrival/departure functions are edited, and it is the thing that crashes. (`setArrivalFunc`
silently ignores out-of-range input, so even reaching it would not warn; autonomy itself is safe,
since firing the stale function lands in `_setF`'s bounds check and no-ops.)

Twin check: the `getArrivalFunc() + 1` / `getDepartureFunc() + 1` pattern exists nowhere else;
`GraphEdgeEdit`'s length selector and `LayoutEditorAddressPopup`'s selector both guard.

Suggested shape of a fix, whichever layer is chosen: clamp in `setAddress` (keeps the invariant
where it is broken), or guard in `updateValues` the way `trainLength` already is. A failing test
first, per the README: set an arrival function valid for MFX, convert to MM2, assert the model
either clears it or `updateValues` survives. Would merit a changelog entry if fixed - a user who
has ever converted a decoder type downward can hit this.

---

## C - cosmetic, dead code, traps, or narrow edge cases

| ID | Finding | Status |
|---|---|---|
| UC-C1 | Update check cannot parse a release name with a non-numeric version component; failure is silent and mislabeled | Fixed in `compareVersions` - each component parses its leading digit run - so the fix covers any caller, not just the release-name path.  Pinned by `testUpdateCheckSurvivesASuffixedReleaseName` |
| UC-C2 | `Feedback N, 1` (space before the state) silently parses as state 0 | Fixed - the token is trimmed.  Pinned by `testFeedbackStateTokenIsTrimmed` |
| UC-C3 | `locdir` treats every direction string other than `forward` as backward, silently | Fixed - only forward/backward parse, case-insensitively; anything else raises the friendly invalid-line error.  Pinned by `testATypodDirectionIsRefusedNotReversed` |
| UC-C4 | Condition AND/OR precedence is nonstandard and undocumented; ungrouped JSON-origin trees change meaning on an edit round-trip | Fixed at the JSON door, second attempt: `NodeAnd`/`NodeOr.fromJSON` wrap a bare cross-operator LEFT child in a group on load - the one shape text parsing can never build, since LIFO stacking right-nests - so the serializer emits preserving parentheses without changing how any text-origin tree renders.  The first attempt (parenthesizing in the serializer) broke `testExpressions` structural round-trip identity and was reverted.  Pinned by `testAnUngroupedConditionTreeSurvivesTheEditorRoundTrip`, which builds the tree through hand-written structural JSON.  Validated 2026-08-01; one door remains open, filed as `UC-C20` |
| UC-C5 | `Layout.renamePoint` enforces neither precondition its sole caller does (unique name, autonomy idle) | Fixed - `renamePoint` refuses a taken target name (self-rename allowed) and refuses while autonomy runs, both with existing message keys.  Pinned by `testRenamingOntoAnExistingPointIsRefused`.  Validated 2026-08-01; the busy predicate chosen is the weakest of the three available, filed as `UC-C18` |
| UC-C6 | `MarklinControlStation.execRoute` NPEs on an unknown route name | Fixed - unknown names log and return.  Pinned by `testExecutingAnUnknownRouteNameIsANoOp` |
| UC-C7 | Example/`main` drift: nonexistent 4-arg `init` in a comment, a wrong "equivalent" line, `Error ocurred` x4, `System.exit(0)` on failure | Fixed - the commented `init` names the real 5-arg form, the switch example acts on the switch, occurred is spelled, and the four failure paths exit 1 |
| UC-C8 | Dead code: `ImageUtil.textToImage`, `ImageUtil.rotateImage`, `MarklinRoute.equalsUnordered`, an always-true visibility conditional, an unused constructor parameter | Fixed, with one correction: `textToImage` and `rotateImage` removed, the inert visibility conditional and the unused constructor parameter removed - but `equalsUnordered` is NOT dead; `testParseCS3Routes` calls it twice (fixture comparison), so it stays |
| UC-C9 | `RightClickFunctionMenu` calls `focusImages()` after the modal dialog is disposed - a no-op | Fixed - `focusImages` now fires from an `AncestorListener` when the dialog shows, the `GraphLocAssign` pattern |
| UC-C10 | `ImageUtil.getScaledImage` throws for `TYPE_CUSTOM` images; both current callers happen to pre-convert | Fixed - a `TYPE_CUSTOM` source falls back to `TYPE_INT_ARGB` |
| UC-C11 | `Point` traps: runtime-inert `assert` guards, a nullable `Integer` that NPEs later, `toJSON` throws on a non-numeric s88 | Fixed - the constructor rejects a non-numeric s88 where the mistake is made, and a null/negative max train length means no limit.  Pinned by `testAPointRejectsANonNumericS88AtConstruction` and `testANullMaxTrainLengthMeansNoLimit` |
| UC-C12 | Clearing a local locomotive icon also wipes the Central Station image URL; compensated online, not offline | Fixed - `setLocalImageURL` touches only the override and `getImageURL` falls back, so clearing no longer destroys the Central Station image.  Pinned by `testClearingTheLocalIconKeepsTheCentralStationImage`.  Validated 2026-08-01: covers the same-session case; the across-restart case persists through a sync guard conditioned on the old storage model, filed as `UC-C17` |
| UC-C13 | `UsageHistogram.paintComponent` mutates the window title and queries the model on every repaint | Fixed - the title and its model queries moved into `createHistogramPanel`, the refresh funnel all three buttons already use; paint only paints |
| UC-C14 | `RouteCommand.KEY_*` map keys are `public static` non-final | Fixed - all nine are final |
| UC-C15 | Locomotive-selector menu renders keycode -1 as a garbage glyph when no button is selected | Fixed - the assign-to-button item is omitted when no button is current |

### UC-C1: update check dies silently on a suffixed release name

`Util.parseReleaseVersion` (Util.java:169) takes the GitHub release **name** and returns everything
after the first `v`; `Conversion.compareVersions` (Conversion.java:113) then `Integer.parseInt`s
every dotted component. A component like `4c` or `0 (Beta)` throws `NumberFormatException`, which
the update thread's catch-all (TrainControlUI.java:1906) reports as "error fetching update info" -
so the update notice silently never appears, with a log line blaming the network.

Checked against real data, which is why this is C and not B: the live `/releases/latest` returns
"Marklin TrainControl v2.7.4", which parses cleanly, and every historical stable release name in
the repo does too - the letter suffixes (`v2_7_4f`) live in the *tags*, which this code never
reads. But the current beta is named "Marklin Train Control v2.8.0 (Beta)" - exactly the breaking
shape - and only its prerelease flag keeps it out of `/releases/latest`. One stable release
published with a suffix in its name and every installed copy stops announcing updates. No test
covers either function. A tolerant parse (strip a trailing non-numeric run, or compare only the
numeric prefix) plus a test with the current beta's literal name would close it.

### UC-C2: feedback state token is compared untrimmed

`RouteCommand.parseLine`'s feedback branch tests `"1".equals(line.split(",")[1])`
(RouteCommand.java:793) without trimming. A user typing `Feedback 3, 1` - one space after the
comma - in the route editor or a condition box gets state **clear**, silently, and a condition that
waits for the opposite sensor edge. The `locfunc` branch three cases up trims its state token; the
feedback branch is the odd one out. Reachable from any free-text route/condition edit
(`RouteEditor.RouteCallback` and `NodeExpression.parseLine` both funnel here). No test covers a
spaced feedback line.

### UC-C3: any typo'd direction is "backward"

The `locdir` branch maps every direction string that is not exactly `forward` to `DIR_BACKWARD`
(RouteCommand.java:731) - `forwards`, `fwd`, `Forward ` with a trailing tab, all reverse the
locomotive without a word of complaint. Every other malformed field in this parser produces the
friendly invalid-line error; a typo'd direction is the one mistake it executes instead. The wizard
generates correct text, so this needs hand-editing to hit - but hand-editing is what the text box
is for.

### UC-C4: condition operator precedence is nonstandard, and only text-origin trees round-trip

`NodeExpression.fromTextRepresentation` applies stacked operators LIFO with no precedence, so
`a AND b OR c` parses as `a AND (b OR c)` - conventional boolean precedence gives
`(a AND b) OR c`. Measured over all 8 assignments with a transcription of the parser: the two
disagree on 2 of 8 (both where `a` is false and `c` is true). `a OR b AND c` happens to match
convention, which makes the behaviour harder to predict, not easier. Neither the Readme (which
documents "parentheses, OR, implicit AND") nor the editor's help text states a precedence.

Related asymmetry, same model: `toTextRepresentation` emits parentheses only for `NodeGroup`
nodes. A text-origin expression always acquires groups at its parentheses and round-trips stably
(measured over the four mixed forms - all stable). But an ungrouped `Or(And(a,b),c)` tree - which
text parsing can never build, only JSON import of a hand-written or legacy file can - renders as
`a AND b OR c` and reparses as `And(a,Or(b,c))`: opening and saving such a route silently changes
what it means. Documenting the precedence and adding parentheses around OR-children of AND on
output would close both halves.

### UC-C5: `renamePoint` trusts its one dialog

`Layout.renamePoint` (Layout.java:1672) checks neither that the new name is unused - `points.put`
would overwrite an existing point, and the edge-key rebuild below it would then drop colliding
edges - nor that autonomy is idle, though a rename mid-BFS would mutate `Point.hashCode` under a
live `visited` set. Both preconditions are enforced today, but only in the sole caller
(GraphRightClickPointMenu.java:1125 checks the name; GraphViewer.java:333 gates the whole menu
behind `!isAutonomyBusy()`). `moveLocomotive`, ten methods away, guards `isRunning()` itself, and
`editRoute`'s own comment states the standard: the model should not depend on one dialog to
protect its data. A second caller - a future keyboard shortcut, a bulk-rename tool - inherits
graph corruption. Trap, not current wrong behaviour; hence C.

### UC-C6: `execRoute` dereferences an unknown route name

`MarklinControlStation.execRoute` (MarklinControlStation.java:2682) is
`routeDB.getByName(name).execRoute(false)` - null NPEs. UI callers pass names from live lists, but
the programmatic API reaches it directly (`Locomotive.execRoute` chains here), and
`ProgrammaticControlExample` literally calls `data.execRoute("SomeRoute")`. `getLocAddress` twenty
lines up carries a comment that it "used to throw on an unknown name" and was fixed; this is the
same defect one lookup over.

### UC-C7: example and `main` drift

- `FullAutonomyExample`'s commented alternative `init("192.168.1.10", false, false, true)` names a
  4-argument overload that does not exist; only the 0- and 5-argument forms do. Uncommenting the
  suggested line breaks the build.
- `ProgrammaticControlExample.java:154`: "These two are equivalent" pairs `mySwitch.straight()`
  with `mySignal.setSwitched(false)` - the second line acts on the signal from the previous
  section, not the switch.
- "Error ocurred" (sic) appears in all three examples and in `TrainControl.main`, user-visible in
  a dialog on startup failure.
- `TrainControl.main` and all three examples `System.exit(0)` on their failure paths - exit code 0
  for an error.

Checked against the current API while reading it: everything else the examples call
(`waitForOccupiedThenClear`, `waitForOccupiedFeedback`, `runLocomotive(loc, speed)`,
`newSignal`/`newSwitch` logical addressing, the `"Switch 400 DCC"` naming convention, the 5-arg
`init` forms) still exists and matches.

### UC-C8: dead code

`ImageUtil.textToImage` and `ImageUtil.rotateImage` have zero callers in src or test (and
`rotateImage` computes its rotation centre with integer division, off by half a pixel on odd
dimensions - academic while dead). `MarklinRoute.equalsUnordered` has zero callers (and compares
via `new HashSet(...)`, so a route with a duplicated command equals one without - worth knowing
before anyone resurrects it). `GraphLocAssign`'s constructor sets `boolean visibility = true` and
then `if (newOnly) visibility = true` (GraphLocAssign.java:69-78) - the conditional is inert and
reads as if visibility were ever false. `RightClickFunctionMenu`'s constructor takes a
`JToggleButton b` it never uses (the popup re-derives the button from the event source).

*Correction, 2026-08-01, found by the author during the fix round and confirmed:* the
`equalsUnordered` claim above is wrong. `testParseCS3Routes` calls it twice (lines 82 and 113),
comparing imported fixtures against expectations. The searches behind this finding were two: the
`textToImage`/`rotateImage` one genuinely covered src and test; the `equalsUnordered` one was
scoped to src only, and the sentence then asserted the wider coverage of its neighbour. Same error
class as the July tally's `FP-C3` entry - reporting a number as independently counted when it was
counted under different conditions. The method stays, and the `HashSet` deduplication note above
becomes live guidance for its two real callers rather than archaeology.

### UC-C9: focus request after the dialog is gone

`RightClickFunctionMenu.openEditDialog` calls `edit.focusImages()` (RightClickFunctionMenu.java:118)
*after* `JOptionPane.showOptionDialog` returns - the modal is disposed; `requestFocus` on a
non-displayable component does nothing. Whatever focus behaviour was intended (presumably focusing
the icon selector when the dialog opens, the way `GraphLocAssign` uses an `AncestorListener`)
never happens.

### UC-C10: `getScaledImage` trap for `TYPE_CUSTOM`

`ImageUtil.getScaledImage` builds its destination as
`new BufferedImage(width, height, image.getType())` (ImageUtil.java:130); for an image loaded via
`ImageIO` with `TYPE_CUSTOM` (type 0) that constructor throws. Unreachable today - both call sites
(TrainControlUI.java:2885, :2922) pre-convert through `toTransparentBufferedImage`, which pins
`TYPE_INT_ARGB` - so this is a trap for the next caller, in the same class as the July cycle's
guarded-but-real B3/C7/C15.

### UC-C11: `Point`'s inert guards

`Point.setMaxTrainLength(null)` passes its `assert` (inert at runtime), stores null, and
`validateTrainLength` later NPEs unboxing `getMaxTrainLength() == 0`. All three current callers
pass primitives, so unreachable today. Same file: `toJSON` does `Integer.valueOf(this.s88)` on a
field the *constructor* accepts as any string - `createPoint` validates the feedback exists (and
feedback names are always numeric), but the public constructor doesn't, so a programmatic user
with `new Point("x", true, "1a")` gets an unchecked `NumberFormatException` at save time, far from
the mistake. `Edge.setLength`'s `assert length >= 0` is likewise inert in production.

### UC-C12: clearing a local icon clears the Central Station image too

`Locomotive.setLocalImageURL` (Locomotive.java:1052) assigns `imageURL = u` before copying it to
`localImageURL`, so `clearLocIcon` passing null wipes both. The caller compensates by immediately
calling `syncWithCS2()` (TrainControlUI.java:14739), which restores `imageURL` from the Central
Station - but only when connected: offline, the locomotive shows no image for the rest of the
session. (That sync also runs on the EDT inside `invokeLater`, the known freeze pattern already
on the deferred-optimization list.) A setter that only touched `localImageURL`, with the display
falling back, would not need the compensating sync at all.

### UC-C13 - UC-C15: small ones

- `UsageHistogram.paintComponent` calls `setTitle(...)` and `getTotalLocStats(...)` on every
  repaint (UsageHistogram.java:141-153) - model queries and window mutation as paint side effects.
  Harmless at this scale; still the wrong place.
- `RouteCommand.KEY_NAME` through `KEY_ACCESSORY_TYPE` are `public static String` without `final`
  (RouteCommand.java:48-56) - mutable global keys for every command's config map.
- `RightClickSelectorMenu` renders the assign-to-button label with
  `(char) ui.getKeyForCurrentButton().intValue()`; when no button is current the method returns
  -1 and the menu shows U+FFFF garbage rather than a disabled item.

---

## D - things that looked wrong and are not, and checks that came back clean

| ID | Finding | Status |
|---|---|---|
| UC-D1 | `PositionAwareJFrame` save/load gate on "different" preference constants | Not a defect |
| UC-D2 | `addressFromUID` boundary arithmetic | Clean |
| UC-D3 | Autonomy-JSON load "missing" departure-function reset | Withdrawn - reset exists |
| UC-D4 | `EmptyStackException` escaping the condition parser | Handled by both callers - duplicates `FCR-D1` |
| UC-D5 | `UsageHistogram` border math for sparse data | Clean - stats pad every day |
| UC-D6 | `Feedback.isSet` unsynchronized read | Compensated |
| UC-D7 | `GraphLocAssign.commitChanges` with an empty selector | Unreachable - both entrances guard |
| UC-D8 | `LocomotiveNotes.fromJson(null)` NPE | Unreachable |
| UC-D9 | Condition text round-trip stability | Measured clean (text-origin) |
| UC-D10 | BFS dequeue-marking and `pickPath` enumeration | Re-verified consistent with documented design |

- **UC-D1**: `saveWindowBounds` gates on its own `REMEMBER_WINDOW_LOCATION`, `loadWindowBounds` on
  `TrainControlUI.REMEMBER_WINDOW_LOCATION` - which looked like two flags. `TrainControlUI` extends
  `PositionAwareJFrame` and defines no constant of that name; both resolve to the same inherited
  field. (The load path's catch block does print the *saving* error message key, and a maximized
  window saves its maximized size as its normal bounds - cosmetic, noted, not filed.)
- **UC-D2**: `MarklinLocomotive.addressFromUID` tests bases highest-first per its July fix; the
  interesting edge is a multi-unit at max address 5120, whose UID (0x2c00 + 0x1400 = 0x4000) sits
  exactly on `MFX_BASE`. The strict `>` comparisons classify it correctly as MULTI_UNIT 5120.
- **UC-D3**: withdrawn finding, recorded per the README. The autonomy-JSON locomotive loader
  appeared to reset `arrivalFunc` when the key is absent (Layout.java:4569) but not
  `departureFunc` - the departure reset exists, 40 lines earlier at Layout.java:4530, on the other
  side of the speed block. `trainLength` and `reversible` also reset-if-absent. Symmetric; the
  distance between the twins is what made it look broken. (I filed this mentally as a B for about
  four minutes.)
- **UC-D4**: `fromTextRepresentation` can throw unchecked `EmptyStackException` on inputs like a
  bare `AND` - but both callers sit in `RouteEditor` catch-alls, and the no-message branch shows a
  dedicated "check parentheses/AND/OR" dialog (RouteEditor.java:1974). Anticipated by the author.
  Found blind and later discovered to duplicate `FCR-D1`'s fourth bullet, which reached the same
  conclusion by the same route. Kept, because two independent readers clearing the same check is
  worth more than one.
- **UC-D6**: `Feedback.isSet` reads a non-volatile boolean written under `Locomotive.monitor`. Every
  consequential reader - the `waitFor*Feedback` loops, the route monitors that call them before
  evaluating - holds that same monitor, which supplies the happens-before edge. Remaining readers
  are display paths. Compensated; worth a `volatile` the next time the file is open, not a finding.
- **UC-D9/UC-D10**: measured with the parser transcription (all four mixed AND/OR text forms
  round-trip semantically stable) and re-read against the July design notes respectively.

---

## Coverage gaps noted in passing

No test exercises `Conversion.compareVersions` or `Util.parseReleaseVersion` (UC-C1), a
whitespace-bearing feedback line (UC-C2), or a `locdir` direction token other than exact
`forward`/`backward` (UC-C3). `testInvalidInput` is the natural home for the latter two.

---

## Comparison against the existing review documents

*Added after the blind draft above was complete; nothing above was edited afterward.*

Read for this comparison: [2026-07-cycle-summary.md](2026-07-cycle-summary.md) in full, then the
eight cycle documents plus the four later ones (`IR`, `SWC`, `HP`, `RE`) by targeted search on every
symbol and defect this document names.

**One finding duplicates a prior clean check.** `UC-D4` (unchecked `EmptyStackException` out of
`fromTextRepresentation`, handled by both callers) is `FCR-D1`'s fourth bullet, reached the same way
and closed the same way. Two independent readers agreeing on a clean check is worth something; it is
recorded rather than deleted, and `UC-D4` now cites it.

**Everything else is new.** `renamePoint`, `compareVersions`, `parseReleaseVersion`, `focusImages`,
`textToImage`, `rotateImage`, `equalsUnordered`, `getScaledImage`, `clearLocIcon`,
`setLocalImageURL`, the `KEY_*` constants, `getKeyForCurrentButton`, and the examples package appear
in no prior review document at all. Neither does operator precedence in route conditions.

**Three findings sit next to prior work and are worth reading together with it:**

- **`UC-B1` and `CR-C2`/`PC-P1`.** `CR-C2` tightened `setArrivalFunc`'s bound from `<= numF` to
  `< numF`; `PC-P1` then traced what that meant for persisted values and concluded the dead value is
  now dropped on load, noting - correctly - that `GraphLocAssign` would show "none" instead of an
  out-of-range selection. That closed the *autonomy-JSON* path: `Layout.fromJSON` goes through the
  setter, which refuses the stale value. `UC-B1` is the path neither pass had reason to look at,
  because it does not involve a stale *file*: `setAddress` shrinks `numF` on a live object without
  revisiting either function field, and the locomotive database restores it through the full-state
  constructor (Locomotive.java:340), which assigns without validating - the one arrival/departure
  write in the codebase that no bound guards. So the out-of-range selection `PC-P1` reasoned about
  is still reachable, by a different route. This is the cycle summary's "same defect, several
  entrances" pattern at one remove: the guard went onto the setter, and the two writers that bypass
  the setter kept the old behaviour.
- **`UC-C2`/`UC-C3` and `CR-B15`.** `CR-B15` fixed `RouteCommand.fromLine` throwing *unchecked*
  exceptions on malformed input, wrapping the whole parser so every branch produces the friendly
  invalid-line error. Both of mine are the complementary failure in the same method: input that does
  not throw at all and is silently misread. `CR-B15`'s own list of bad inputs (`locdir,MyLoc`,
  `locspeed,MyLoc,abc`) is about truncation and non-numerics; a *well-formed* line with a space
  before the state, or a plausible synonym for `forward`, parses successfully into the wrong command.
- **`UC-C13` and `FCR-D1`.** `FCR` checked `UsageHistogram` paging and the stats key format and found
  both clean; it did not comment on the model calls inside `paintComponent`. Different question, same
  file, and mine is the weaker of the two observations.

**Four findings are in files a prior pass listed as read in full, and are not in its report.**
`FCR`'s scope names `Conversion`, `PositionAwareJFrame`, `UsageHistogram`, `RouteCommand` and
`NodeExpression`; `UC-C1`, `UC-C2`, `UC-C3` and `UC-C4` are in those files, and `FCR`'s notes on
`Conversion` and `PositionAwareJFrame` record different observations (the millisecond-named-seconds
methods, the off-screen-restore guard). That cuts both ways and both are worth stating: it is
evidence the untouched-code brief was worth running, and evidence that "read in full" in any review
header - including this one - means "read for the questions that reader was asking".

**Coverage the cycle named as still open, which this pass does not close.** The summary's two
by-decision items (`FP-B3`, `FP-C6`) and its two named test gaps (editor flows, the charset round
trip) are untouched here. `CS2File`'s parsers, the largest single body of code I skimmed rather than
read, were covered by `FCR` and by three July passes on switch commands - a future untouched-code
pass should still start there, since none of those read it with this brief.

**One observation about the folder rather than the code.** [README.md](README.md) tells a reader to
start with [2026-07-cycle-summary.md](2026-07-cycle-summary.md) rather than with any single
document. That page indexes the eight documents of the July cycle and says so precisely - but four
later documents now sit beside it (`IR` 07-28, `SWC` 07-29, `HP` 07-31, and the reversing-edges UI
review 07-31), and this makes five. A reader following the README's instruction today reaches an
index that predates a third of the folder. Deliberately not fixed here: the summary is the record of
one pass's scope, and editing it to cover later work is the merge the README forbids. A short
post-cycle index, or a pointer line in the README, is the author's call.

**Calibration note.** Two candidate findings were withdrawn during verification rather than filed:
the autonomy-JSON departure-function reset (recorded as `UC-D3`, where the twin was 40 lines away in
the same method) and a `Layout.renamePoint` duplicate-name defect that turned out to be guarded by
its sole caller (retained at lower severity as `UC-C5`, a trap rather than a live defect). Both are
instances of the README's "verify the layer you are actually claiming about". One prediction in
`UC-C1` was checked against live data (the GitHub releases API) rather than reasoned about, and that
check is the only reason it is a C: the reasoning alone said B. One filed claim was later proved
wrong: `UC-C8`'s "zero callers" for `equalsUnordered` rested on a search that did not cover test/,
while the sentence claimed one that did - caught by the author during the fix round, corrected in
place under `UC-C8`.

---

## Validation of the resolution - 2026-08-01

The fix commit (`5c92967`) was read hunk by hunk against the findings it closes, the way `PV`
validated the July cycle's late fixes: each fix checked in its enforcing method, its twins
re-searched, the premises of the two extra fixes it carried verified, and the twelve new tests read
against what they claim to pin. No tests were run from this session, per standing practice; the
author's suite run is the record for red-then-green.

**All sixteen fixes do what their status entries say.** Worth singling out: `UC-B1` was fixed at
both unguarded writers rather than in the GUI, so the invariant now holds at the source -
`setAddress` clears through the validating setter after the resize (correctly using the *new*
`numF`, and only downward conversions are affected), and the full-state constructor validates
exactly like the setters, which retroactively heals a database that already carries a stale value.
`UC-C4`'s second-attempt shape - normalize at `fromJSON`, leave the serializer alone - was re-walked
against this document's parser transcription across ten nested shapes, including the ones the fix's
comment does not mention (bare cross-operator *right* children, same-operator left-nesting): the
right-nested shapes are parser-native and round-trip unchanged, the associative reshapes are
semantically inert, and the left-child wrap is applied recursively by construction. The
`setActivateRouteIDs` defensive copy's premise was verified too: `testAutoLayout.java:43` passes
`Collections.singletonList`, and `deleteRoute` mutates the stored list at
MarklinControlStation.java:2715.

The round also produced findings, most in the fixes themselves - the July cycle's expectation, met
again. Numbering continues this document's C series.

| ID | Finding | Status |
|---|---|---|
| UC-C16 | The `UC-C1` fix stranded `compareVersions`' javadoc inside `parseVersionComponent`'s body | **Fixed in this round** - comment moved back onto `compareVersions` |
| UC-C17 | The `UC-C12` fix missed its twin: the sync guard at MarklinControlStation.java:1167 still skips adopting the CS image while a local override exists, so the across-restart offline case persists | Fixed - the sync adopts the CS image unconditionally; with the override in its own field and `getImageURL` falling back, the guard only starved the fallback.  The guard itself has no headless seam, so the Locomotive-level restore ordering it relies on is pinned instead, on the extended `testClearingTheLocalIconKeepsTheCentralStationImage` |
| UC-C18 | The `UC-C5` fix guards with `isAutoRunning()`, the weakest of the three busy predicates - the staging planning window and the graceful-stop wind-down pass it | Fixed - the guard is now `isRunning() || isStagingInProgress()`, layout-local and the strongest available: it holds through the graceful-stop wind-down and the staging planning window.  Pinned by `testRenamingIsRefusedWhileStagingIsPlanning`, red against the first guard |
| UC-C19 | The `UC-C6` fix logs a hardcoded English string in an i18n'd codebase | Fixed - `route.warningRouteNotExistCalledFrom` with "execRoute" as the caller, the same key `MarklinRoute.execRoute` uses one layer down |
| UC-C20 | The `UC-C4` fix normalizes the JSON door only; conditions restored from the locomotive database bypass it | Fixed one level deeper than suggested: normalization became `NodeExpression.normalize`, applied at `fromJSON` and at the `MarklinRoute` constructor - the choke point every door shares, including database restore, which rebuilds routes through it.  The per-class inline wraps were removed so the rule has one home.  Pinned by `testALegacyDatabaseConditionTreeIsNormalizedOnRestore`, red against the fromJSON-only fix |
| UC-D11 | Fix-round spot checks that came back clean | Clean |

### UC-C16: the stranded javadoc (fixed in this round)

The `parseVersionComponent` insertion split itself around `compareVersions`' existing javadoc: the
new method's opening half landed above the doc block and its `return` and closing brace below it, so
the block - `@param version1` and all - sat *inside* `parseVersionComponent`'s body, between the
loop and the return. It compiled, because a block comment in a method body is legal, which is
exactly why nothing noticed: the same shape as the July cycle's `PV-C5` (a reformat that still
compiles) and the `SWC-C9`/`HP-C6`/`HP-C9` residue class, fourth round running. Fixed here by
moving the comment; no code changed, verified by re-reading the method pair.

### UC-C17: the sync guard is the `UC-C12` fix's missed twin

`setLocalImageURL` now touches only the override, and `getImageURL` falls back to `imageURL` - but
the fallback only helps if `imageURL` is ever populated while an override exists, and the sync
deliberately refuses to do that: MarklinControlStation.java:1167 adopts the CS image only when
`getLocalImageURL() == null`, a guard (its comment says so: "if a local icon is not set") whose
reason was protecting the override *when the override lived in `imageURL`*. The fix moved the
override out; the guard survived.

Consequence: for any locomotive whose custom icon is restored from the locomotive database - that
is, every custom icon across a restart - `imageURL` starts null and the guard keeps it null for as
long as the override exists. Clear the icon offline in that state and the fallback lands on null:
the exact scenario `UC-C12` described, one restart later. The fix genuinely covers the same-session
case (the CS image adopted *before* the override was set is now retained, where it used to be
overwritten), and the new test models exactly that case by calling `setImageURL` directly - which is
why it passes while the sync path keeps the gap. Dropping the `getLocalImageURL() == null`
condition (and its comment) closes it: adopting the CS URL into `imageURL` is now harmless while an
override exists, because display preference lives in `getImageURL`. A red test would stage the
restore ordering: build the locomotive with an override and no `imageURL`, run the sync merge,
clear the icon, assert `getImageURL` is the CS image.

### UC-C18: the rename guard's predicate is the weakest of three

The new guard in `renamePoint` tests `isAutoRunning()`, which is the bare `running` flag. Two
neighbouring predicates are stronger, and the difference is exactly the ground the July cycle
mapped: `Layout.isRunning()` also counts active locomotives (so it stays true through the
graceful-stop wind-down, when in-flight paths still walk the graph), and
`MarklinControlStation.isAutonomyRunning()` also counts `isStagingInProgress()` - added by `IR-B1`
precisely because "the planning phase of a staging run has nothing dispatched", and the staging
planner calls `getPossiblePaths`, which runs `bfs` over the structures a rename mutates. The UI
caller is still safe (`isAutonomyBusy` gates on the staging flag and `isRunning()`), so this is a
trap with no live path - the same status `UC-C5` itself had - but the guard was added to stop
trusting that one caller, and as written it re-trusts it for the two windows that matter most.
`this.control.isAutonomyRunning()` (or `isRunning() || isStagingInProgress()`) is the predicate the
fix's own comment describes.

### UC-C19: the unknown-route log line is not internationalized

The `execRoute` guard logs `"Route does not exist: " + name` as a bare English string. Every other
message in this class goes through `I18n`/`logf`, and a key for almost exactly this already exists -
`route.warningRouteNotExistCalledFrom`, used by `MarklinRoute.execRoute` for the same situation one
layer down. `testMessageBundles` cannot catch a hardcoded string, so nothing will flag it later.
One line plus no new keys if the existing key's two-argument shape fits ("execRoute" as the
caller).

### UC-C20: the third door for `UC-C4`'s trees

`fromJSON` now normalizes, and text parsing never built the shape - but conditions stored in the
locomotive database (`MarklinSimpleComponent` Java-serializes the `NodeExpression` tree) are
restored without passing through either door. A cross-operator tree imported from hand-written
JSON *before* this fix and saved into `trains.dat` comes back un-normalized, and the editor round
trip still silently rewrites its meaning. Narrow by construction - it requires exactly that
pre-fix import history, and no real data was available to check whether any such database exists -
which is why this is a C and not the reopening of `UC-C4`. The shape of a fix, if wanted: apply the
same left-child wrap when `MarklinRoute` receives conditions from a `MarklinSimpleComponent`, or
normalize in `getConditions()` so every door shares it. This is `PC-P1`'s lesson verbatim: the
change was assessed on the live call path, and the persistence path is the one that keeps the old
shape alive.

### UC-D11: fix-round spot checks that came back clean

- `RightClickFunctionMenu`'s signature change (the unused `JToggleButton` dropped) has exactly one
  constructor call site, and it was updated.
- The `AncestorListener` focus fix matches the working `GraphLocAssign` pattern, and the stale
  post-dialog call is gone.
- `UsageHistogram`'s title computation moved to `createHistogramPanel`, which the constructor and
  all three paging buttons already funnel through - title behaviour is unchanged, paint is now pure.
- The nine `KEY_*` constants are `final`; nothing assigned them anywhere.
- The selector menu omits (not disables) the assign item at keycode -1, per its comment's rationale.
- `setDelay(0)` canonical-absence change: `toLine` only emits positive delays and `getDelay`
  defaults to 0 when the key is absent, so removal is the representation the round trip already
  produced; equality now agrees.
- The examples compile against the real API after their edits (5-arg `init`, `mySwitch` acting on
  the switch); all four failure paths exit 1.
- The new tests assert their preconditions (the `UC-B1` pair asserts validity *before* conversion),
  clean up by name in `finally`, and place their feedback addresses (468xx) clear of the ranges the
  other suites document.
- Two cosmetic notes, recorded rather than filed: the fix commit rewrote `NodeAnd`, `NodeOr`,
  `TrainControl.java` and two examples to CRLF line endings, which will pollute future diffs and
  blame; and `Point`'s new s88 validation parses the trimmed string but stores the raw one, so a
  programmatic `" 1"` or `"-1"` is accepted yet matches no feedback name (`setS88`, by contrast,
  normalizes through `Math.abs`). Neither changes behaviour reachable from the UI.

### Validation of the second fix round - 2026-08-01

The fixes for `UC-C17`..`UC-C20` (in the working tree at validation time, uncommitted) were read
against their findings the same way. All four are correct, and their status entries above say
what was done. What the reading added beyond the entries:

- **`UC-C20`'s door coverage was traced exhaustively.** Every `MarklinRoute` construction site was
  enumerated: both `newRoute` overloads and `MarklinRoute.fromJSON` reach the normalizing
  constructor, and `CS2File`'s two sites use the simple constructor with conditions added later
  via `addConditionS88`/`addConditionAccessory`. Those two assign the field directly, bypassing
  the constructor - and are safe anyway, because `fromList` builds left-nested *same-operator*
  AND chains, the one nesting `normalize` deliberately leaves alone.
- **The rebuild cannot perturb the sync merge.** `syncWithCS2` deletes a route whose conditions
  compare unequal, and its two sides now take different paths: the incoming CS-parsed tree skips
  the constructor while the stored tree was normalized by it. Modelled: `fromList`-shaped chains
  (2, 3 and 4 deep) pass `normalize` unchanged and the wrap is idempotent, so both sides stay
  equal and no route is spuriously deleted as "changed".
- **`UC-C18`'s new predicate is equivalent to the model's `isAutonomyRunning`** (`isRunning()`
  plus the staging flag) while staying layout-local, and its test leaves the graph verifiably
  untouched after the refusal.
- **`UC-C17`'s test comment is honest about its seam** - the sync guard has no headless test path,
  so the test pins the `Locomotive`-level restore ordering (override first, CS image second,
  clear reveals it) and the guard removal itself was verified by reading its consumers: display
  preference lives in `getImageURL`, and nothing persists `imageURL`, so adopting unconditionally
  has no other reader to disturb.
- **`UC-C19`** reuses the two-argument key exactly as `MarklinRoute.execRoute` does one layer down.

**No new findings.** This is the first round of the chain to add none - the same terminating shape
the July cycle recorded for its `PV` fix-validate chain. One repeat of an already-noted cosmetic:
`MarklinRoute.java` flipped line endings this round, joining the five files from the last one.


### Disposition of UC-C17 to UC-C20 - 2026-08-01

All four fixed the same day.  `UC-C20` went one level deeper than the finding's sketch: rather
than normalizing at each door, the rule became `NodeExpression.normalize`, applied at `fromJSON`
and at the `MarklinRoute` constructor - which every door shares, including the database-restore
path the finding identified, because restore rebuilds routes through that constructor.  The
per-class inline wraps were removed so the rule cannot drift between copies.

`UC-C17` carries a disclosed limitation: the sync guard runs deep inside `syncWithCS2` against
parsed Central Station data, and no headless seam reaches it, so its test pins the
Locomotive-level ordering the fix relies on (override first, CS image adopted second, clear
reveals it) rather than the guard itself.

The `UC-C16` shape - a javadoc block stranded inside a method body, which compiles and which the
stacked-javadoc check cannot see - has a detector only in the assistant-side session tooling used
to prepare these rounds (a validation script outside the repository, in ephemeral session
storage), where it was proven by re-injecting the exact `compareVersions` stranding.  **That is
not repository coverage**: no test class, script or harness in the tree contains it, and nothing
a future reader can run reproduces the check.  The original wording here claimed "a detector in
the fix-round validation harness" without saying where that harness lives - the record equivalent
of the cycle's signature error, a claim of coverage the artifact does not have.  Corrected on the
third round's flag, which stands above this paragraph in the history of the mistake: fourth
instance of the residue class, still zero durable checks for it in the tree.

*Record note, third round (the flag that prompted the correction above, kept per the
withdrawn-findings rule):* that detector is not in the repository - no test class, script or
harness file in the tree contains it, and the suite's only source-scanning test
(`testMessageBundles`) checks bundles, not comment structure. If it lives in author-side tooling,
this paragraph should say where, because a check a future reader cannot find reads as coverage the
record does not have - the `FP-C3` lesson applied to a document instead of a thread count.

### Validation of the third round - the launch-pad change, 2026-08-01

One behaviour change arrived with this round, prompted by live use rather than by a finding:
`HomeStaging.snapshot` now drops a POSITIONAL home claim whose station has zero incoming edges - a
one-way staging track, a launch pad - so a locomotive dispatched from one no longer makes the
all-or-nothing Return Home plan answer IMPOSSIBLE for the whole fleet (found in the wild: MV 1134
on St99, one of nineteen such tracks on the author's graph). An ASSIGNED home keeps the strict
contract and still answers IMPOSSIBLE with the locomotive named. Verified correct, and correctly
placed:

- **Snapshot-only, by construction.** The pruning edits a copy; the live `homeStations` map is
  untouched, so the claim is not data-lost - it resurfaces in the very next snapshot if the
  operator ever gives the pad an incoming edge. Pruning at the snapshot is also the only spot
  where both consumers inherit it for free: `triageReturnToHome` (the Return Home button's
  enable/tooltip predicate) and the planner build from the same snapshot, so the button and the
  plan cannot disagree about who counts as misplaced.
- **The assigned/positional discrimination is sound.** `getHomeLoc()` is null for a positional
  claim, so the by-name equality is false exactly there; `claimHome`'s injectivity means a station
  cannot simultaneously be one locomotive's assignment and another's claim, so the mixed case
  cannot arise.
- **The display already agreed.** `AutoLocomotiveStatus` deliberately badges only assigned homes
  (its comment explains why positional fallbacks made the badges lie), so the planner ignoring
  positional claims on unreachable stations aligns it with what the UI has shown since July -
  no new contradiction between the badge, the button and the plan.
- **The test covers both halves** - fleet freed with the launch-pad locomotive homeless and the
  genuinely homed one still moved; then the same station assigned, and IMPOSSIBLE again with the
  locomotive named - and asserts its preconditions (the pad really has no incoming edges, the
  dispatch really succeeded) before relying on them.

One finding, filed from the gap between the change and its changelog entry:

| ID | Finding | Status |
|---|---|---|
| UC-C21 | The changelog promises launch-pad locomotives "simply stay where they are", but they join the free-agent class, which the A* expansion is allowed to move | Fixed by the second offered remedy - the sentence is made true: the A* expansion skips a free agent still standing on a zero-incoming station, one check against the pad set snapshot already computes (the home filter and the exemption now share one definition of a pad).  An assigned-home locomotive is not exempt, and one already dispatched is an ordinary free agent.  The changelog restates the guarantee precisely.  A cornered search answers NO_PLAN_FOUND, which claims less and is true.  Pinned by `testTheSearchNeverMovesALocomotiveOffItsLaunchPad`, red against the unexempted expansion |

**Validation of the UC-C21 fix - 2026-08-01.** Verified correct, and the round is clean:

- **The exemption sits at the only site that can move a free agent.** The twin check that mattered:
  the greedy pass skips any locomotive without a home (`home == null ... continue`), so the A*
  expansion was the sole mover of free agents, and one guard there covers every path into a move.
- **The exceptions are scoped exactly right, and the code comment argues each one.** A locomotive
  with an assigned home is not exempt - moving it off a pad is the operator's stated wish - and a
  locomotive already dispatched from its pad is an ordinary free agent wherever it stands. A
  positional pad-home locomotive still on its pad is both pruned from `homes` and exempt from
  moves, the two rules sharing one pad-set definition computed once in `snapshot`.
- **The cornered search now answers `NO_PLAN_FOUND`**, which the Outcome javadoc defines as "may
  still be possible" - true, since the only way out required undoing hand-staging, which is the
  operator's to undo. Claims less, and is honest.
- **The test builds the cornering mechanism this finding predicted** - the pad sharing a detection
  section with a transit point - asserts both preconditions, and pins both halves: no plan move
  touches the pad locomotive, and the outcome is `NO_PLAN_FOUND`. Red against the unexempted
  expansion per the status row.
- **The reworded changelog now states the code's actual guarantee.** Its one simplification -
  "never moved off it" elides the assigned-home exception - is the user's own explicit
  instruction acting, not the planner meddling, and the entry's final clause covers that contract
  in as many words. Checked and acceptable.

The stranded-javadoc detector record note above remains open - unanswered as of this round.

**UC-C21.** The pruned locomotive becomes a free agent, and `misplaced`'s own comment states the
design: free agents "may end anywhere, which is what makes them useful for breaking a deadlock."
The A* expansion generates moves for *every* locomotive in the state, free agents included - so a
launch-pad locomotive still standing on its pad can be relocated by a Return Home plan when doing
so unblocks a homed one. The reachable route to that: the pad's s88 shared with a point on another
locomotive's path (shared addresses are routine on this layout - the `HS-B4` history), the sensor
genuinely held, and the search cornered enough to need it. And because the pad has no incoming
edges, a locomotive moved off it can never be planner-restored - the hand-staging the change
exists to respect is undone by the plan, permanently. The behaviour is the pre-existing free-agent
design, not a defect this round introduced; the defect is the changelog sentence claiming a
guarantee the planner does not make. Two closures, author's choice: soften the sentence
("...are left out of the plan"), or exempt locomotives standing on zero-incoming stations from
the A* move generation - which would make the sentence true and costs one containsKey-shaped test
in the expansion loop. C either way.
### Post-round repairs, suite-caught - 2026-08-01

Two errors in the launch-pad round itself, both found by the author's suite run and neither by
the assistant's simulations:

1. **`testALaunchPadPositionalHomeDoesNotBlockTheFleet` was never green.** Its fixture parked the
   dispatched pad locomotive on the homed locomotive's own station with no siding anywhere - so
   READY was impossible for any planner, the pad being un-re-enterable by definition.  The
   green-side simulation forgot occupancy.  A bidirectional siding was added, and the fixture now
   documents why it exists.
2. **The snapshot filter was too aggressive.** Dropping every positional pad-home turned a
   perfectly staged layout's ALREADY_HOME into NO_HOMES -
   `testAlreadyHomeIsReportedOnALayoutWithNoWayBack` caught it, on the very fixture whose javadoc
   tells the July war story about this exact shape.  Refined: the entry is dropped only once the
   locomotive has LEFT the pad; standing there, the claim is simply satisfied.  The A* exemption
   was re-keyed to match (pad plus own-home-here, not homelessness), or the kept entry would have
   re-opened UC-C21 for the on-pad case.

Three tests now triangulate the semantics: the no-way-back test pins kept-while-standing, the
fleet test pins dropped-once-dispatched, and the UC-C21 test pins never-moved-off.  The
assistant's per-change simulations missed both errors; the suite caught both - which is the
system working as the README intends, and the calibration note this section exists to record.
