# Independent review focused on code untouched by the July 2026 cycle - 2026-08-01

**Prefix for citing this document: `UC`.**

**Version reviewed: `897c155`, working tree clean, on 2026-08-01.** No code was changed by this
review; it is a report only.

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
| UC-C4 | Condition AND/OR precedence is nonstandard and undocumented; ungrouped JSON-origin trees change meaning on an edit round-trip | Fixed at the JSON door, second attempt: `NodeAnd`/`NodeOr.fromJSON` wrap a bare cross-operator LEFT child in a group on load - the one shape text parsing can never build, since LIFO stacking right-nests - so the serializer emits preserving parentheses without changing how any text-origin tree renders.  The first attempt (parenthesizing in the serializer) broke `testExpressions` structural round-trip identity and was reverted.  Pinned by `testAnUngroupedConditionTreeSurvivesTheEditorRoundTrip`, which builds the tree through hand-written structural JSON |
| UC-C5 | `Layout.renamePoint` enforces neither precondition its sole caller does (unique name, autonomy idle) | Fixed - `renamePoint` refuses a taken target name (self-rename allowed) and refuses while autonomy runs, both with existing message keys.  Pinned by `testRenamingOntoAnExistingPointIsRefused` |
| UC-C6 | `MarklinControlStation.execRoute` NPEs on an unknown route name | Fixed - unknown names log and return.  Pinned by `testExecutingAnUnknownRouteNameIsANoOp` |
| UC-C7 | Example/`main` drift: nonexistent 4-arg `init` in a comment, a wrong "equivalent" line, `Error ocurred` x4, `System.exit(0)` on failure | Fixed - the commented `init` names the real 5-arg form, the switch example acts on the switch, occurred is spelled, and the four failure paths exit 1 |
| UC-C8 | Dead code: `ImageUtil.textToImage`, `ImageUtil.rotateImage`, `MarklinRoute.equalsUnordered`, an always-true visibility conditional, an unused constructor parameter | Fixed, with one correction: `textToImage` and `rotateImage` removed, the inert visibility conditional and the unused constructor parameter removed - but `equalsUnordered` is NOT dead; `testParseCS3Routes` calls it twice (fixture comparison), so it stays |
| UC-C9 | `RightClickFunctionMenu` calls `focusImages()` after the modal dialog is disposed - a no-op | Fixed - `focusImages` now fires from an `AncestorListener` when the dialog shows, the `GraphLocAssign` pattern |
| UC-C10 | `ImageUtil.getScaledImage` throws for `TYPE_CUSTOM` images; both current callers happen to pre-convert | Fixed - a `TYPE_CUSTOM` source falls back to `TYPE_INT_ARGB` |
| UC-C11 | `Point` traps: runtime-inert `assert` guards, a nullable `Integer` that NPEs later, `toJSON` throws on a non-numeric s88 | Fixed - the constructor rejects a non-numeric s88 where the mistake is made, and a null/negative max train length means no limit.  Pinned by `testAPointRejectsANonNumericS88AtConstruction` and `testANullMaxTrainLengthMeansNoLimit` |
| UC-C12 | Clearing a local locomotive icon also wipes the Central Station image URL; compensated online, not offline | Fixed - `setLocalImageURL` touches only the override and `getImageURL` falls back, so clearing no longer destroys the Central Station image.  Pinned by `testClearingTheLocalIconKeepsTheCentralStationImage` |
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
check is the only reason it is a C: the reasoning alone said B.
