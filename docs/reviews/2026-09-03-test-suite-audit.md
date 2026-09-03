# The test suite, audited before the release

**Status:** open

**Prefix for citing these findings elsewhere:** `TSX`

**Reviewed:** branch `autonomy-diagram-r0`, on 2026-09-03. Three passes append here in order, each
cross-checking the ones before it, and each covering a different part of the suite:

1. **The GUI tests** - `test/ui/`, and the parts of `test/regression/` that build a window.
2. **The core tests** - `test/core/`, the model, the planner, the graph and the reduction.
3. **Everything else** - `test/regression/` proper, `test/support/`, the harness itself, and the rules
   that hold the suite together.

**What this audit is for.** Correctness, coverage and completeness of the TESTS, not of the code they
test: an assertion that cannot fail, a fixture that guarantees its own precondition, a mutation claimed
and not achievable, a rule enforced on one door of three, a class that is green because it skipped.
Where a test is wrong ABOUT the code, that is a finding here and the code defect it hides is a finding
in [the release review](2026-09-03-release-review.md).

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|
| A1 | High | `roomAtTheEnd` - the measurement the whole reverse-over-switch guard runs on - is written by two writers, read by one reader, and named by no test in the suite | Pass 2 |
| B1 | Medium | Eight GUI classes open a `LayoutSandbox` at a point a throw can skip the close, leaving the operator's machine-global layout preference pointing at a temp folder | Pass 1 |
| B2 | Medium | `testARenameReachesTheTimetableOnScreen` survives the mutation its own javadoc names - it drives `timetableSignature`, and nothing drives the guard in `repaintTimetable` | Pass 1 |
| B3 | Medium | Three tests write the operator's own preferences back without the "was it ever stored" guard the fourth was given, and one can leave the setting flipped | Pass 1 |
| B4 | Medium | `AutonomyMenu.refreshEnabled()` - what greys the autonomy menu and what its two tooltips say - has no test anywhere | Pass 1 |
| B5 | Medium | `unmeasuredAfterTheLastSwitch`'s stop at the last switch is unreachable by the one fixture that drives it, so deleting the line the rule consists of passes the suite | Pass 2 |
| B6 | Medium | `testNothingAsksForAKeyThatIsNotThere` scans `src/` with no floor, alone among the three scans in its own file, and passes having read nothing from the wrong directory | Pass 2 |
| C1 | Low | `testEveryLanguageFits`'s "the locale reached the text" control passes when both screenshots are missing | Pass 1 |
| C2 | Low | The same class's railway fingerprint is sampled at the instant the sandbox closes, and is skipped entirely when a measurement throws | Pass 1 |
| C3 | Low | `testEditorSwitchClearsPageState.methodSource` anchors `arriveAt` on a call site, not on the declaration, and is rescued only by there being no brace in between | Pass 1 |
| C4 | Low | `testTheActivePageDrawsTheSamePictureAsChoosingIt` still renders one page against itself; the real check is the assertion three lines above | Pass 1 |
| C5 | Low | `testSidebarIcons` regexes `TrainControlUI.java` without stripping comments, and a commented-out `setIconAt` already stands in that file | Pass 1 |
| C6 | Low | `testDiagramLooksRight` builds a `TrainControlUI` in `@BeforeClass` and never disposes it, alone among its siblings | Pass 1 |
| C7 | Low | `testANumberTooWideForItsSquareIsLeftOut` asserts an empty list with no floor on the reader that produces it | Pass 1 |
| C8 | Low | `testRoutePicking`'s `COVERED_HERE` is still an unchecked claim; delete a mirror test and the coverage index goes on reporting the rule covered (`TST-C4`'s remainder) | Pass 2 |
| C9 | Low | `testTrainTailClearsEdges` pins an LF inside a `src/` file that `.gitattributes` does not protect, on a repository with `core.autocrlf=true` - the hazard `.gitattributes` records hitting `test/baseline` the same day | Pass 2 |
| C10 | Low | `testStationPriorityDistribution` floors on one sample in four hundred where its own class javadoc promises a tenth, and its list of other prioritised stations is unchecked | Pass 2 |
| C11 | Low | `testTimetableOnDerivedGraph` counts every restorable locomotive where its message says "every one this test placed", and `TST-B11`'s silent configuration fall-through is still there | Pass 2 |
| C12 | Low | `testEveryKindOfferedAsACommandIsOneExecutionActsOn` scans from `execRoute` to end of file while its comment says "the dispatch only"; `toCSV` already matches | Pass 2 |
| C13 | Low | Two more "preconditions" that read back what the two lines above them set, in the file split out of the one that states the rule against it | Pass 2 |
| D1 | Not a defect | Where the `TST` / `TCX` / `TS3` findings in this scope stand at HEAD | Pass 1 |
| D2 | Not a defect | Five GUI classes read against their own stated mutations and found sound | Pass 1 |
| D3 | Not a defect | `testEveryLanguageFits` names `cs2_sample_layout`; that is the guard, not a leak | Pass 1 |
| D4 | Not a defect | No unseeded generator anywhere in the GUI tests | Pass 1 |
| D5 | Not a defect | What pass 1 did not cover | Pass 1 |
| D6 | Not a defect | Where the `TST` / `TCX` / `TS3` findings in `test/core` stand at HEAD - fourteen closed, nine confirmed open - and pass 1's `B3` handoff answered | Pass 2 |
| D7 | Not a defect | Six core classes read against their own stated mutations and found sound | Pass 2 |
| D8 | Not a defect | What pass 2 did not cover | Pass 2 |

---

## Pass 1 - the GUI tests

**Reviewed:** `test/ui/` and the window-building classes of `test/regression/`, on 2026-09-03. No tests were run; every claim below is from reading.

Scope in numbers: the 24 classes of `test/ui/` (10,106 lines, 125 `@Test` methods) read in full, and the
30 classes of `test/regression/` that build a window, a `LayoutEditor`, an `AutonomyEditorPanel`, a
`LayoutGrid` or a `TileAnnotation` - of which `testEditorSurfaceRules`, `testEveryWindowWearsTheIcon`,
`testEditorSwitchClearsPageState`, `testHomeAssignmentRules`, `testTrainMarkIsNotBlank`,
`testAutonomyLabelShowsLocomotiveName` and the preference-writing parts of
`testTheWindowTakesTheKeyboard` were read in full and the rest swept mechanically. `D5` says what that
leaves.

**Where the previous three passes left this corner.** `TS3` states outright that it did not look at
`test/ui` at all. `TCX` touched it only in its "whole-class skips" section. `TST` is the one that read it
properly, on 2026-08-28, and most of what it found there has since been fixed - `D1` is the ledger. So
the useful comparison for this pass is against `TST`, and the honest summary of it is that the GUI tests
are in markedly better shape than the rest of the suite: the mutation-survival defects `TST` filed are
closed, the controls it asked for are present, and all four of the classes it singled out as models
(`TST-D14` to `D17`) still are. What is left is a different class of problem - **not tests that cannot
fail, but tests that
change the operator's machine and cannot put it back**, which is `B1` and `B3` and is two thirds of the
weight of this pass.

**No A.** Nothing here is wrong behaviour on the layout or a silent loss of railway data. `B1` is the
closest, and the reason it is not an A is set out in its entry.

---

### B - medium

#### B1 - the sandbox is opened where a throw can skip the close, in eight GUI classes

**FIXED 2026-09-03.**  Every `@AfterClass` in the suite carries `alwaysRun = true` - fifty classes, not
only the eight that hold a sandbox, because a teardown that has been skipped is never what anybody
wanted and two of them put the operator's real signals back.

And the sharp variant is closed at the site: `testUiStateIsNotLostWhenUnreadable` opens its sandbox
INSIDE the `try` whose `finally` closes it, with the class's own rule quoted where it was broken - a
guard that runs after the thing it guards is not a guard.  Its teardown is null-guarded so a set-up that
never completed cannot throw a second time on the way out.

**Status: open.** Verified by reading. Severity: the operator's machine-global layout preference is left
pointing at a temporary folder, so the next time he starts TrainControl it opens the fixture railway - or
nothing - instead of his own.

`support.LayoutSandbox` writes a **machine-global java Preferences value** and puts it back on `close()`
(`test/support/LayoutSandbox.java:86`, `:100-108`). Its own javadoc says what happens if the close is
missed:

```java
    /**
     * Puts the preference back.
     *
     * Back to what it WAS, including back to unset - because a test that leaves a path behind has
     * changed which layout the application opens the next time the operator starts it, which is worse
     * than the churn this class exists to remove.
     */
```

**The churn it is talking about is OB-111**, which was significant enough to produce this whole support
class. So the file already grades leaving a path behind as *worse than* the incident it was built for.

**Shape one: `@BeforeClass` opens it, `@AfterClass` closes it, and nothing carries `alwaysRun`.**

```
$ grep -rn alwaysRun test/ --include=*.java
(no matches)
```

TestNG skips an `@AfterClass` when the class's `@BeforeClass` throws. Every one of these opens the
sandbox and then does more work in the same method that can throw:

| Class | Opens at | Then does, in the same method |
|---|---|---|
| `test/ui/testARenameReachesTheTimetableOnScreen.java` | `:64` | `init(...)`, `model.stop()`, `newMM2Locomotive` |
| `test/ui/testBusyDialogInteraction.java` | `:57` | `MarklinControlStation.init(...)`, `model.stop()` |
| `test/ui/testDiagramExport.java` | `:62` | `init(...)`, `new TrainControlUI()`, `setViewListener` |
| `test/ui/testDiagramLooksRight.java` | `:65` | `init(...)`, `new TrainControlUI()`, `setViewListener`, `OUT.mkdirs()` |
| `test/ui/testRenderingCost.java` | `:57` | `init(...)`, a `CS2File` parse, a `TileGraph`, a full `reduce()` |
| `test/ui/testRouteCapture.java` | `:49` | `init(...)`, `model.stop()` |
| `test/regression/testARunSurvivesADiagramEdit.java` | `:72` | `init(...)` |

**`init` failing here is not hypothetical, and this suite has it written down.** `docs/tools/battery.sh`
sets `-Dtraincontrol.anyReceivePort=true` precisely because a UDP bind failure comes out of
`@BeforeClass`, and `test/ui/testBusyDialogInteraction.java:36-39` records the same thing from the other
side:

```java
     * init() binds the Central Station's UDP port, and stop() does not give it back promptly - so three
     * tests each building their own model meant two of them failing on "Address already in use", which
     * looks exactly like a product fault and is not one.
```

`ant test` does not pass that flag (`TST-B1`), and `TS3-A1` established that for four days no battery
reaped the leftover JVMs that cause the bind to fail. So the precondition for this has been standing
open, repeatedly, in the runner most likely to be used.

**Shape two, and it is sharper because the failing line is the one the sandbox exists to protect.**
`test/ui/testUiStateIsNotLostWhenUnreadable.java:132-139`:

```java
        // BEFORE the window is built: it reads the layout preference in its constructor (OB-111).
        // Opened here rather than in a @BeforeClass because this is the only place that builds one,
        // and the finally below is already where this method puts things back.
        sandbox = support.LayoutSandbox.open();

        SwingUtilities.invokeAndWait(() -> window[0] = new TrainControlUI());

        try
        {
```

`sandbox.close()` is at `:177`, in the `finally` of a `try` that opens at `:139` - **after** the window
construction. An `InvocationTargetException` out of `new TrainControlUI()` therefore leaves the
preference pointed at the temp copy, and the comment three lines above says the `finally` "is already
where this method puts things back". It is not, for the one statement that can fail. This class's own
javadoc states the rule it has broken here, at `:46-47`: *"a guard that runs after the thing it guards is
not a guard."*

**Why B and not A.** The operator's railway files are not touched - the sandbox is a copy and the
original is only read. What is lost is his configured layout path, and he can set it again. What makes it
worse than a C is that it is silent, it is machine-global, it survives the JVM, and the folder it points
at is in `%TEMP%` and is never deleted by `LayoutSandbox` - so the application comes up showing the
fixture railway looking like a working installation rather than failing in a way that names the cause.

**The fix is two words and one brace.** `@AfterClass(alwaysRun = true)` on all seven of the first shape,
and moving `sandbox = LayoutSandbox.open()` to the line above the `try` in
`testUiStateIsNotLostWhenUnreadable`. The classes that already do this correctly - by holding the sandbox
in a local and closing it in a `finally` around everything - are `testThePaletteStillPlacesTiles.java:47-54`,
`testTheRoutingChoiceSurvivesTheUpgrade.java:119-125` (five times), `testDiagramShiftKeepsSetup.java:312-321`,
`testLayoutEditorBulkEdits.java:530`/`:695`, and the three `build()` helpers in `testRoutingRuleTooltips`,
`testStagingOutcomeMessages` and `testTimetableColumnHeadings`. The pattern is already in the file; it is
the class-scoped sites that miss it.

**Not verified by running.** The claim rests on TestNG's documented behaviour for a failed `@BeforeClass`
and on the absence of `alwaysRun` anywhere in `test/`, which `grep` settles. I have not watched an `init`
fail.

**Out of scope but the same defect**, listed so pass 2 and pass 3 do not have to re-derive it: the twelve
`test/core` classes that hold the sandbox in a static and close it in an `@AfterClass` -
`testALocomotiveDoesNotEvictItself:57`, `testAutonomyPathValidation:51`, `testLayoutTiles:69`,
`testLockEdgesSurviveTheFile:56`, `testLocomotiveExclusions:56`, `testReturnHomeSequencesAReversal:64`,
`testRouteTilePlacement:57`, `testStagingSkipsALegWithNoSpeed:63`, `testStationPriorityDistribution:91`,
`testTheStationGoingAwayDoesNotJamSwitching:48`, `testTrainsComeHomeToTheirPlatforms:121`,
`testTrainTailClearsEdges:72`.

---

#### B2 - `testARenameReachesTheTimetableOnScreen` survives the mutation its own javadoc names

**FIXED 2026-09-03.**  The test drives `repaintTimetable` itself now and reads the state the guard
keeps, so the mutation it is named for - keying that guard on `hashCode()` - fails it.  Confirmed by
making exactly that mutation: 1 test, 1 failure.

The finding's framing is the right one and is worth keeping: the rule was lifted out, tested, and the
call site left uncovered, which is this repository's most repeated shape.

**Status: open.** Verified by reading. This is the repository's signature shape: the rule is lifted out,
tested, and the call site left uncovered.

`test/ui/testARenameReachesTheTimetableOnScreen.java:46` states the claim:

```
 * MUTATION this catches: keying the guard on `timeTable.hashCode()` again.
```

What the test actually drives is `timetableSignature`, by reflection (`:125-130`, `:152`):

```java
            java.lang.reflect.Method signature = TrainControlUI.class.getDeclaredMethod(
                "timetableSignature", List.class);
            ...
            String after = (String) signature.invoke(ui[0], layout.getTimetableSnapshot());

            assertNotEquals(after, before,
                "the key repaintTimetable skips its redraw on is unchanged by a rename, so the table
                ...
```

The guard is not in `timetableSignature`. It is one line in `repaintTimetable`, at
`src/org/traincontrol/gui/TrainControlUI.java:24822-24824`:

```java
            String showing = timetableSignature(timeTable);

            if (showing.equals(lastTimetableState)) return;
```

**The mutation the javadoc names is an edit to that line, not to the method the test calls.** Replace
`timetableSignature(timeTable)` with `String.valueOf(timeTable.hashCode())` and `timetableSignature` is
untouched, every assertion in this test still holds, and MT-149 is back: a locomotive hashes by identity,
so a rename changes every row's text and no hash, `repaintTimetable` returns at that line, and the table
goes on naming a locomotive that no longer exists.

**Nothing else covers it.**

```
$ grep -rn "timetableSignature\|repaintTimetable" test/
test/ui/testARenameReachesTheTimetableOnScreen.java:27,35,155   (prose and the message above)
test/ui/testTimetableColumnHeadings.java:15,20                  (prose)
```

Three prose mentions and one reflective call on the helper. `repaintTimetable` is driven by no test in
the suite.

**The assertion that is missing** is a source-text one in the idiom this suite already uses everywhere
else - `withoutComments(bodyOf(ui, "private void repaintTimetable()"))` asserted to contain
`"timetableSignature("` and not to contain `"hashCode()"` - or, better, a behavioural one: the class
already builds a real `TrainControlUI` at `:121`, so calling `repaintTimetable` by reflection before and
after the rename and reading `timetable.getModel()` back would exercise the guard itself. The second is
worth the extra ten lines, because the first is the shape `TCX-A3` went stale in.

Severity: the defect that gets back through is MT-149, which Adam filed critical. It is a display fault
rather than a railway one, and the data half of the test - `assertSame(renamed, loc)`, the placement, the
timetable entry's own name - is sound and does bite. B.

---

#### B3 - three tests hand the operator's own preferences back without the guard the fourth was given

**FIXED 2026-09-03**, all three sites.

`testDiagramExport` and `testTheWindowTakesTheKeyboard` ask whether the preference was ever stored and
REMOVE it when it was not, rather than writing the accessor's default back.  `testDiagramLooksRight`'s
own site is the sharp one - the click itself writes the key - so it remembers whether the panel's node
held it and removes it afterwards if it did not.

The rule this finding names, stated in the sweep that produced it, is now true of every site the sweep
was about.

**Status: open.** Verified by reading.

`test/ui/testDiagramLooksRight.java:758-763` states the rule, and names the sweep that produced it:

```java
        // WHETHER IT WAS STORED, not what the accessor answers.
        //
        // Capturing the accessor captures its DEFAULT when nothing is stored, and writing that back
        // materialises the preference on a machine that never set it. Two sibling tests were fixed for
        // exactly this earlier today; this was the third, and nobody swept it (reviewer, 2026-08-28).
        boolean had = TrainControlUI.getPrefs().get(TrainControlUI.STATION_LABELS_GREY, null) != null;
```

Three sites do not have that `had`:

| Site | Reads | Writes back |
|---|---|---|
| `test/ui/testDiagramExport.java:365-366` | `getPrefs().getBoolean(SHOW_COORDINATES_PREF, false)` | `:419`, unconditionally |
| `test/regression/testTheWindowTakesTheKeyboard.java:523-524` | `getPrefs().getBoolean(ONTOP_SETTING_PREF, false)` | `:580-581`, unconditionally |
| `test/ui/testDiagramLooksRight.java:1250` | `panel[0].isShowingParkedTrains()` | `:1273-1276`, by clicking the box back |

The third is the worst of them, twice over.

**It is a real preference in a real node.** `AutonomyEditorPanel.java:207-208` and `:224`:

```java
    private static final java.util.prefs.Preferences VIEW_PREFS =
        java.util.prefs.Preferences.userNodeForPackage(AutonomyEditorPanel.class);
    ...
    private static final String PREF_CAPTION_TRAINS = "autonomyEditorCaptionTrains";
```

and the checkbox's listener writes it (`:571-573`):

```java
        showParkedTrains.addActionListener(e ->
        {
            VIEW_PREFS.putBoolean(PREF_CAPTION_TRAINS, showParkedTrains.isSelected());
```

`getShowParkedTrains()` returns the box itself - `control(...)` at `:157` is a font setter that hands the
component straight back - so `doClick()` fires that listener. On a machine that never set it, a green run
of this test leaves it explicitly stored. That is the latent half, and it happens on **every** run.

**And the restore is not in a `finally`.** `test/ui/testDiagramLooksRight.java:1271-1276`:

```java
        // Put it back before asserting, so a failure here does not leave the operator's own setting
        // flipped.
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            if (panel[0].isShowingParkedTrains() != was) panel[0].getShowParkedTrains().doClick();
        });
```

The comment is true of the assertion below it and false of the two above it. `:1252` clicks the box;
`:1257` is `assertTrue(rebuilds[0] > 0, ...)`. A regression that dropped `onDiagramChanged.run()` from the
listener - which is exactly one of the two mutations this test's own javadoc names - fails at `:1257`
with the setting left switched over, and the operator's autonomy editor then names parked trains instead
of stations until he notices and changes it back.

**Why this is graded B.** The suite has already paid for this rule twice and written it down twice: the
comment above, and `test/regression/testDiagramDrawingSettings.java:38-43`, which records that a stored
value from a previous run *answered an assertion about a default* and that "these are the real
preferences of whoever is running the suite and a test has no business changing what their application
does tomorrow." The materialising half is what makes a later default change untestable; the flipped half
is a visible change to the operator's window. The fix is `had`-guarded restores at all three sites and a
`try`/`finally` around the middle of `testTheCaptionSwitchRemembersItselfAndRebuilds`.

`test/core/testAutonomyDiagramSession.java:1713-1832` writes `getPrefs()` at six places and is pass 2's
to check against the same rule.

---

#### B4 - what greys the autonomy menu, and what its tooltips say, is tested nowhere

**FIXED 2026-09-03.**  `testTheAutonomyMenuSaysWhyItIsGrey` drives `refreshEnabled()` through the menu
and asserts both halves: that it greys with no layout, and that the tooltip gives the no-layout reason
rather than the remote-layout one - two reasons that are not interchangeable, because a menu that greys
for the second and blames the first sends somebody looking for a layout they already have.

Mutation-confirmed by swapping the two tooltip arms.

The `local` half is not driven, and that is recorded rather than claimed: reaching it needs a model
holding a Central Station layout, which this class has no fixture for.  What is pinned is the shape and
the first reason; `canUseAutonomy`'s own behaviour is `RGN-A2`'s ground and is Adam's to rule on.

**Status: open.** Missing test.

`src/org/traincontrol/gui/AutonomyMenu.java:77-96` is a GUI rule of exactly the kind this audit is asked
to look for - what is disabled, and what the tooltip says instead:

```java
    public final void refreshEnabled()
    {
        ...
        boolean hasLayout = ui.getModel() != null && ui.isLayoutLoaded();
        ...
        boolean local = ui.canUseAutonomy();

        setEnabled(hasLayout && local);

        setToolTipText(AutonomyEditorPanel.wrapped(!hasLayout ? I18n.t("autosetup.ui.tooltipNoLayout")
            : !local ? I18n.t("autosetup.ui.tooltipNeedsLocalLayout") : null));
    }
```

None of it is reached by any test:

```
$ grep -rn "refreshEnabled\|tooltipNeedsLocalLayout\|tooltipNoLayout\|canUseAutonomy" test/
(no matches)
```

`canUseAutonomy()` (`TrainControlUI.java:2566-2571`) has exactly one caller in `src/`, which is the line
above, so the predicate and its only consumer are both uncovered.

**What the absence costs is written in the method's own comment**, at `:86-88`: *"A setup lives in files
beside the diagram, so a diagram read straight from the Central Station has nowhere to keep one - which
was true before and simply not said, so every autonomy gesture came back having done nothing and the
captions never appeared."* Drop the `&& local` and that state returns: a live autonomy menu on a Central
Station layout, every item silently doing nothing. Drop the tooltip and the menu is dead with no reason
on it, which is the state Adam found the delete side in.

**The test that is missing already exists, one class over, for the neighbouring rule.**
`test/ui/testLocMappingPages.java:83-116`, `testTheMenuWillNotOfferAFiftyFirstPage`, builds the real menu,
asserts the item is enabled below the ceiling and greyed at it, asserts the tooltip is non-null, and
asserts the tooltip names the number. The same four assertions over `AutonomyMenu` with
`getLocalLayoutPath()` empty and non-empty is the whole of what is wanted. This is the guard-and-affordance
pairing the repository has three findings about (OB-057, OB-090, `TS3-B6`), on a door nobody has looked
at.

**And it is one rule of several in the same file.** `AutonomyMenu` greys-with-a-reason in five more
places - `:381` (settings, on whether a configuration is loaded), `:420` (edit), `:434` (pages), `:501`,
`:606` (`tooltipNeedsLoaded`), `:623`, `:639`. None of those message keys appears in `test/` either. The
menu is the single most rule-bearing surface in the application and the suite reaches it only through
`testEditorSurfaceRules`' ordering checks on where its items *sit*.

**Two smaller relatives, recorded rather than filed separately.** `LayoutEditor.canUndo()` and
`canRedo()` are named by no test either. `canRedo` only greys a right-click item; `canUndo` is load-bearing
- `LayoutEditor.java:5535`, `boolean unsaved = isAutonomyMode() ? autonomyPanel.isDirty() : canUndo();`
is what decides whether the unsaved-work prompt appears on exit. `testEditorSurfaceRules` pins the
*ordering* of the exit calls (`:1501-1516`) and not the predicate that decides whether there is anything
to settle.

---

### C - low

#### C1 - the "did the locale reach the text" control passes when both screenshots are missing

**FIXED 2026-09-03.**  The two screenshots must exist before the control compares them, so an unwritable output directory fails loudly instead of satisfying the one assertion that proves eight measurements are of eight languages.

`test/ui/testEveryLanguageFits.java:173-175`:

```java
        assertFalse(sameBytes(new File(OUT, "window-en.png"), new File(OUT, "window-de.png")),
            "the English and German windows are byte-identical, so the locale is not reaching the "
            + "text and all eight measurements are of the same language");
```

`sameBytes` (`:449-455`) opens with `if (!a.exists() || !b.exists()) return false;`, and `shoot`
(`:438-445`) swallows the write failure by design:

```java
        catch (java.io.IOException ignored)
        {
            // A missing picture is not a reason to fail the measurement it illustrates
        }
```

So if `OUT` is unwritable, or ImageIO has no PNG writer, or `OUT.mkdirs()` at `:93` failed, both files are
absent, `sameBytes` answers false and the `assertFalse` passes having compared nothing. The control is
the one thing in this test that proves eight measurements are of eight languages rather than of one, and
it is satisfied by there being no measurements to compare.

One line: `assertTrue(new File(OUT, "window-en.png").isFile() && new File(OUT, "window-de.png").isFile(),
...)` before it. The class already has this instinct everywhere else - `:166-171` insists at least forty
components were measured per language, with the reason ("the first version of this test inspected nothing
and reported nothing wrong").

#### C2 - the railway fingerprint is taken at the instant the sandbox closes, and is skipped when a measurement throws

**FIXED 2026-09-03**, both halves: the event queue is drained before the sandbox closes, and the railway fingerprint is compared inside the `finally` - the run most likely to have written is the one that threw.

Same file. The class javadoc, `:40-47`, is unusually direct about why the guard is there:

```
 * **This test damaged the operator's railway once, and the guard below is why it may run again.**
 * The first version opened and closed a sandbox around each of the eight windows; something a
 * disposed window had already scheduled then wrote after the sandbox had put the layout preference
 * back ... So: ONE sandbox around the whole class, and this test fingerprints that folder itself and
 * fails if a single byte of it moved.
```

Two gaps in that, neither of which makes the fix wrong - one sandbox instead of eight removes seven
eighths of the exposure - but both of which mean the fingerprint cannot see the thing it names.

1. **It is sampled too early.** `:124` closes the sandbox and `:128` compares. The failure mode described
   is *work scheduled by a disposed window landing after the preference has been put back*, which by
   construction happens some milliseconds later. There is no drain - no `invokeAndWait(() -> {})`, no
   `whenTilesSettled` - between the last `dispose()` at `:264` and the `close()` at `:124`.
2. **It does not run at all on the failure path.** The `finally` at `:119-125` closes the sandbox and
   restores the locale; the `assertEquals(fingerprint(LIVE), railwayBefore, ...)` is at `:128`, outside
   it. If `measure` throws - an `InvocationTargetException` out of a window constructor, an
   `OutOfMemoryError` laying out the eighth language - the sandbox is restored and the folder is never
   compared. That is the run most likely to have written.

Both are cheap: a `SwingUtilities.invokeAndWait(() -> { })` before `sandbox.close()`, and moving the
fingerprint comparison into the `finally` after it (or into an `@AfterClass(alwaysRun = true)`, which is
where `B1` wants it anyway).

Graded C rather than B because `battery.sh` fingerprints the same folder around the whole run, so a write
is not invisible - it is only unattributable, which is `TCX-A4`'s standing complaint rather than a new
one.

#### C3 - `methodSource` anchors on a call site, and its guard cannot tell the difference

**FIXED 2026-09-03.**  `methodSource` matches a DECLARATION - modifiers, return type, name, open bracket, at the start of a line - rather than the first mention of the name anywhere in the file.  The accident it was relying on (no `{` between `arriveAt`'s first call site and its declaration) is gone.

`test/regression/testEditorSwitchClearsPageState.java:272-299`:

```java
        String all = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");

        int at = all.indexOf(" " + name + "(");

        assertTrue(at > 0, "no method called " + name);

        int open = all.indexOf('{', at);
```

The search is over the **raw** file - comments are stripped at `:293`, after the body has been carved out
- and it takes the first occurrence anywhere. For one of the two names this helper is asked for, that is
not the declaration:

```
$ grep -n " arriveAt(" src/org/traincontrol/gui/LayoutEditor.java
5684:                    javax.swing.SwingUtilities.invokeLater(() -> arriveAt(page, autonomy));
5704:                javax.swing.SwingUtilities.invokeLater(() -> arriveAt(page, autonomy))));
5719:    private void arriveAt(String page, boolean wanted)
```

`arriveAtSource()` (`:261-264`) anchors on line 5684, a call inside `leaveFor`. It reads the right body
today only because there is no `{` anywhere in the thirty-five lines between that call and `arriveAt`'s
own opening brace - the intervening statements are all single-line lambdas and the javadoc has no braces
in it. Add an `if` block, an array initialiser, or a comment containing a brace, and
`testTheSwitchClearsEverythingThatNamesASquare` and `testTheSetupUndoPointIsRetaken` start reading some
other block.

`assertTrue(at > 0, "no method called " + name)` does not catch this: a call site satisfies it exactly as
well as a declaration does.

Mostly a false-*failure* risk, because the two `arriveAt` tests are positive `contains` assertions - which
is why this is a C and not a B. The one place a drift would pass silently is
`testTheSwitchDoesNotCloseTheWindow` at `:136-141`, which is `assertFalse(methodSource("leaveFor").contains("dispose()"))`;
`leaveFor`'s declaration happens to be the first ` leaveFor(` in the file today, so that one is sound.

Fix: anchor on a declaration rather than on a name - `"private void " + name + "("` and the two other
modifiers, as `testLocIconCrop.bodyOf` and `testEditorSurfaceRules.bodyOf` are both given.

#### C4 - the active-page export test still renders one page against itself

**ANSWERED 2026-09-03 - the comparison stays, and says what it is.**  The finding is right that after the equality assertion the two lookups are the same object; what the tail measures is that `render` is deterministic, which every picture comparison in the class rests on.  The comment says so, so a reader counting assertions is not told there are three checks on the shortcut when there is one.

`test/ui/testDiagramExport.java:237-240` records `TST-B9` - the original body "rendered `page` against
itself twice - byte-identical arguments, so all it measured was that `DiagramExport.render` is
deterministic" - and the rebuild is a good one. The rebuilt test's real assertion is at `:288`:

```java
            assertEquals(reportedActive, onScreen,
                "activeLayoutPage() reported " + reportedActive + " while the selector was showing "
                + onScreen + " - the shortcut would export the wrong page");
```

That drives `activeLayoutPage()` through the real selector and is exactly the check that was missing.

What was left behind is the old comparison, and after `:288` it is provably a comparison of one object
with itself. `:292-293`:

```java
            LayoutDiagram chosenByName = model.getLayout(onScreen);
            LayoutDiagram viaShortcut = model.getLayout((String) reportedActive);
```

`reportedActive.equals(onScreen)` has just been asserted, so `chosenByName == viaShortcut`. The control at
`:305` (`imagesDiffer(byName, otherPage)`) and the assertion at `:309`
(`assertFalse(imagesDiffer(byName, asActive))`) are therefore a determinism check on `render`, which is
what `TST-B9` said the whole test was. Nothing is unprotected - `:288` carries it - but a reader counting
assertions is told there are three checks on the shortcut and there is one. Deleting `:299-311`, or
re-pointing `viaShortcut` at the selector's answer *before* the equality assertion, would say what is
meant.

#### C5 - `testSidebarIcons` reads commented-out code, and there is some

**FIXED 2026-09-03.**  `testSidebarIcons` strips comments, with the class's own copy of the helper two siblings already carry - the file it reads holds a commented-out `setIconAt`, so the route this check could not see is real.

`test/regression/testEveryWindowWearsTheIcon` strips generated blocks, `testLocMappingPages` and
`testDiagramLooksRight` strip comments, `testLocIconCrop` does not, and `testSidebarIcons` does not
either. `test/ui/testSidebarIcons.java:139-140` and `:185-190`:

```java
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")), java.nio.charset.StandardCharsets.UTF_8);
        ...
        java.util.regex.Matcher at = java.util.regex.Pattern
            .compile("setIconAt\\((\\d+), " + icon + "\\)").matcher(source);
```

The target file already holds a commented-out one, `src/org/traincontrol/gui/TrainControlUI.java:6870`:

```java
        // this.KeyboardTab.setIconAt(0, TAB_ICON_CONTROL);
```

`TAB_ICON_CONTROL` is not one of the four constants this test asks about, so nothing is vacuous today.
What the file demonstrates is that commenting a `setIconAt` line out is a thing that happens here: comment
out `:6879` (`setIconAt(3, TAB_ICON_ROUTES)`) and the routes tab loses its icon while `iconIndex` still
answers 3 and the test still passes - which is the fault OB-124 and this class exist for, arrived at by
the one route the check cannot see.

This is `TST-C10`'s class with a new site; the fix is the `withoutComments` helper that already exists in
two files in this package.

#### C6 - `testDiagramLooksRight` never disposes the window it builds

**FIXED 2026-09-03.**  The window built in the set-up is disposed in the teardown, like both its siblings.

`test/ui/testDiagramLooksRight.java:69` builds a `TrainControlUI` in `@BeforeClass` and `:76-82` is the
whole teardown:

```java
    @AfterClass
    public static void tearDownClass()
    {
        if (model != null) model.stop();

        if (sandbox != null) sandbox.close();
    }
```

Its two closest siblings both dispose, and both say why - `test/ui/testDiagramExport.java:31-32`,
*"Disposed rather than closed at the end: the window-closing handler saves state, which would write the
operator's own locomotive database"*, with the call at `:80`; and
`testUiStateIsNotLostWhenUnreadable.java:173-175`. `testBusyDialogInteraction` has `closeQuietly` for the
same reason. This class is the exception, and it also builds `AutonomyEditorPanel`s at `:1246` and `:1267`
that nothing releases. `test/ui/testLocMappingPages` is a milder instance: `build()` is called by three
tests and only `testASentinelIsNotAPageName` disposes (`:247`).

What it costs I could not settle by reading, and it is the reason this is a C rather than left out
entirely: an undisposed displayable `Frame` keeps AWT's non-daemon event thread alive, which is the
orphaned-JVM condition `battery.sh:273-275` describes ("an orphaned JVM poisoned every class after it")
and which `TS3-A1` established was going unreaped for four days. Whether `new TrainControlUI()` plus
`setViewListener` makes the frame displayable without a `setVisible` is the question, and answering it
needs a run. **What I would run:** this class alone through `docs/tools/one.sh` and watch whether the JVM
exits, against `testDiagramExport` as the control. Until then the finding is the inconsistency, which is
certain, rather than the leak, which is not.

#### C7 - an empty-list assertion with no floor on the reader that produces it

**FIXED 2026-09-03.**  The reader is proved to read - digits found on a thirty-pixel ruler - before its silence on a four-pixel one is allowed to mean anything.  The rescue two methods up was incidental, which is what made this worth an edit.

`test/ui/testTheDiagramPrintsItsCoordinates.java:89-97`:

```java
        String tiny = paint(AxisRuler.uniform(4, 100, 100, 6, 6));

        assertEquals(digitsIn(tiny), java.util.Collections.emptyList(),
            "three-digit numbers were printed into four-pixel squares, so each one overhangs its
```

`digitsIn` (`:230-240`) works by re-rendering each candidate number with a font it chooses itself -
`new javax.swing.JPanel().getFont().deriveFont(java.awt.Font.PLAIN, 10f)` at `:257` - and sliding the
stamp over the painted ink. If that font ever stops matching the one `AxisRuler` draws with, `appears`
answers false for everything and this assertion passes having recognised nothing.

The sibling two methods up rescues it - `testTheNumbersAreTheDiagramsOwn` asserts
`digitsIn(zeroBased).contains("3")` and would fail loudly - so nothing is unguarded today. Recorded
because the rescue is incidental (`TST-C8`'s class), and because the one-line fix is in the file's own
idiom: assert that `digitsIn` finds something on a ruler at a size where it should, in the same method.

---

### D - not defects

#### D1 - where the earlier passes' GUI findings stand at HEAD

Re-checked one by one against the tree. The GUI corner is in good order; most of `TST`'s findings here are
genuinely closed.

**Fixed, and well:**

| Finding | What I checked at HEAD |
|---|---|
| `TST-A8` - the route editor's four row-action guards uncovered | `testRouteEditorLocked` now has four paired tests, each with the ordinary-route control running first: `testALockedRouteRefusesToMoveARow:303`, `...ToDeleteARow:344`, `...ToDuplicateARow:384`, `...ToAddARowEvenCalledDirectly:433`. The fourth reaches `addTo` by reflection and says why a click cannot. |
| `TST-B7` - the exit-discard ordering passed with the settle call deleted | `testEditorSurfaceRules.java:1499-1519` proves both `indexOf`s are `>= 0` before ordering them, with the reason in a comment naming the finding |
| `TST-B8` - the drag put-down anchored on the wrong branch | `testStationLabelDrag.java:277-296` anchors on `boolean moved = dragging[0];`; `:330-338` scopes the second half to the wrong-button branch. Both halves fixed, both cite the finding |
| `TST-B9` - the active-page export rendered the same call twice | Rebuilt through `activeLayoutPage()` by reflection. Residue only, filed as `C4` |
| `TST-B10` - the leaked-dialog class never mentioned a dialog | `testWorkThatThrowsStillDismissesTheDialog:168-232` now captures the live `BusyDialog` and asserts `isDisplayable()` is false. The `getLocList().isEmpty()` tautology at `:256`/`:355` is gone too, replaced by removing a named fixture locomotive first (`:314-319`, `:415-420`) |
| `TST-B19` - `nearestStation` untested | `testStationLabelPrefill.testNearestStationOnlyOffersRealStations:167-246` builds a session and reaches the private method, with the nearest tile deliberately not a station |
| `TST-C9` bullets 1 and 2 | `testDiagramLooksRight.java:1857-1863` now compares the heading against a name read independently, and says so; `testDiagramExport.testAnAbsurdSizeIsCapped:134-166` uses three renders and no arithmetic |
| `TST-C14` last bullet - the decode loop's swallowed exceptions | `testRenderingCost.java:310-314` has the `decoded > 0` floor, citing the finding |

**Still open, confirmed at HEAD:**

- `TST-C10` (body scans that read prose as code) is half done. `testDiagramLooksRight:967-1002` and
  `testLocMappingPages:342-377` both have `withoutComments`; `testStationLabelDrag`, `testLocIconCrop`,
  `testHomeAssignmentRules` and `testSidebarIcons` do not. I re-derived every one of their anchors against
  the file it names and **none is vacuous today** - `refuseCaptionDrop(from, to, onTarget)` really does
  occur exactly twice in `AutonomyEditorPanel.java` (`:2239`, `:2312`, the declaration at `:2257` not
  matching the argument list), `dragCaption(` really does occur exactly three times in `LayoutGrid.java`
  (`:150`, `:1475`, `:1476`), and `recropLocIcon`'s comments carry none of the four literals asserted
  against them. The one place the hazard is already realised in the target file is `C5`.
- `TST-C13`, fourth bullet: `testUiStateIsNotLostWhenUnreadable.java:165`, `assertTrue(copy.length() > 0)`,
  is still subsumed by the `assertEquals` two lines below it.
- `TCX-C3`'s GUI entries stand: `testHomeAssignmentRules.java:129-177` is still seven whole-file
  `contains` over `AutonomyEditorPanel.java`, and it still asserts `PANEL.isFile()` first, which is the
  right handling of the usual trap.
- `TST-C14`'s headless inventory is unchanged in shape: whole-class skips in `testBusyDialogInteraction`,
  `testDiagramExport`, `testDiagramLooksRight`, `testRenderingCost`; per-method in `testCommandTableMarks`,
  `testRouteEditorValidation`, `testRouteEditorShading`, `testRouteEditorLocked`,
  `testUiStateIsNotLostWhenUnreadable`, `testStagingOutcomeMessages`, `testTimetableColumnHeadings`,
  `testRoutingRuleTooltips`, `testTheMenuShowsWhereALinkGoes`, `testStationLabelPrefill`; and the classes
  that would **error** rather than skip - `testLocMappingPages`, `testLocIconCrop`, and two methods of
  `testTheWaitMarkIsAnHourglass` - are still in that state, which `TST-C14` argues is the better outcome
  and I agree.

**A note on the display-dependence question the briefing asks.** Every class in `test/ui` that needs a
display says so, and the four that do not - `testSidebarIcons`, `testStationLabelDrag`,
`testTheDiagramPrintsItsCoordinates`, `testThePlaceholderLocomotive` - genuinely do not need one: they
read source, decode PNGs, or paint into a `BufferedImage`, and the last three say so in their javadocs.
There is no class in this scope that needs a display and is silent about it.

#### D2 - five GUI classes read against their own stated mutations and found sound

Recorded so the absence of findings against them is a result rather than an omission.

- **`testRouteEditorLocked`** - twelve tests, every locked assertion paired with an unlocked control that
  runs first, and two of them carrying a measured mutation result ("Run 2026-08-25 against a mutant
  compiled outside the repository: 1 of 8 fails, this test"). `testNoCellOfALockedRouteCanBeEdited:172`
  asks the table MODEL rather than the JTable, with the reason. The strongest class in `test/ui`.
- **`testTheWaitMarkIsAnHourglass`** - sixteen tests, the load-bearing one (`testTheSandIsConserved:157`)
  asserting a conservation property the drawing code nowhere states, and its own javadoc naming what it
  cannot see (the square root in `remaining`, which both bulbs read). `testTheStartupNoticeIsNotAWindow:722`
  searches for `"new JWindow("` rather than `"JWindow"` specifically so the class javadoc's own history of
  OB-170 does not answer it - I checked, and `StartupSplash.java:25` does carry `JWindow` in prose.
- **`testLocMappingPages`** - the predicate, the affordance and the guard behind it, in three tests, with
  comments stripped for the third. `testLoadingIsNotCapped:171` is the rare thing this suite does well: a
  test whose whole purpose is to say that two rules must *never* agree.
- **`testStationLabelDrag`** - counts rather than presence at both scanning sites, and states plainly what
  a source scan cannot see (`:149-154`: *"a mark that still CALLS canDropCaption inside an expression that
  has been neutered ... passes, because the name is still in the file"*).
- **`testAutonomyLabelShowsLocomotiveName`** - `placedAt` asserts the placement really was stored as a
  `JSONObject` before the reading test runs (`:210-211`), which is the precondition that makes the whole
  class mean anything; and `testACaptionIsJustTheNameCutToLength:148` uses a 21-character name against a
  `LAYOUT_STATION_MAX_LENGTH` of 10, so the cut it asserts a ceiling on really does fire.

`testEveryWindowWearsTheIcon` deserves naming too: all three of its tests carry a floor
(`windows >= 5`, `namesIt >= 1`, `sites >= 1`), each with the reason, and the third asks per *construction
site* rather than per file after the first version's per-file question was found to excuse two of the four
windows OB-124 was about.

#### D3 - `testEveryLanguageFits` names the operator's railway, and that is the guard

`test/ui/testEveryLanguageFits.java:67` is `private static final File LIVE = new File("cs2_sample_layout");`,
which reads like `TCX-B11`'s complaint about `testTheGoldenLayoutHoldsTogether` reproduced. It is not the
same thing. `LIVE` is only ever passed to `fingerprint` (`:465-488`), which walks the tree read-only and
builds a `path:size:mtime` string; the layout the windows actually open is the sandbox copy. Naming the
folder is the point - this class is the one that damaged it, and the fingerprint is its apology. `C2` is
about *when* that fingerprint is taken, not about its existence.

#### D4 - no unseeded generator anywhere in the GUI tests

`TCX-B10` is the suite's one violation of the fixed-seed rule and it is in `test/core`. Swept across the
whole of this scope:

```
$ grep -rn "new Random(\|Math.random()\|ThreadLocalRandom" test/ui test/regression
(no matches)
```

The only randomness is two `UUID.randomUUID()` calls in `testLocIconCrop.java:65` and `:286`, which name a
scratch file in the real `tc_loc_icons` folder and are deliberate - the comment at `:61-63` says why a
fixed name would have two concurrent runs failing each other. No result depends on the value. Nothing in
this scope needs a seed.

Timing dependence, for completeness, since it is the neighbouring question: ten classes in scope use
`Thread.sleep`, and every one I read pairs it with a bounded wait on a condition rather than sampling
after a fixed delay - `testBusyDialogInteraction:370-375`, `testTheWaitMarkIsAnHourglass:452-461`,
`testTheWindowTakesTheKeyboard:566-572`. The exception is
`testBusyDialogInteraction.testASecondSyncIsTurnedAway:535`, a bare `Thread.sleep(3000)` to let the first
sync get inside the reconciliation; it is safe because the mock station blocks on a latch until the
assertion has been made, so the window it is opening cannot close early.

#### D5 - what pass 1 did not cover

Said plainly.

- **Read in full:** all 24 classes of `test/ui/`; and in `test/regression/`,
  `testEditorSwitchClearsPageState`, `testEveryWindowWearsTheIcon`, `testHomeAssignmentRules`,
  `testTrainMarkIsNotBlank`, `testAutonomyLabelShowsLocomotiveName`, and the parts of
  `testEditorSurfaceRules` and `testTheWindowTakesTheKeyboard` that the sweeps below pointed at.
- **Swept but not read line by line:** `testEditorSurfaceRules` (2,741 lines, 60-odd tests - I read every
  `indexOf` ordering and every `assertFalse(...contains(...))` in it, and the `bodyOf`/`withoutComments`
  helpers, but not the majority of its method bodies), `testLayoutEditorBulkEdits`,
  `testTheWindowTakesTheKeyboard` (973 lines), `testSwitchingToACentralStationLayout` (1,111 lines),
  `testARouteDoesNotThrowSwitchesUnderATrain`, `testThePaletteStillPlacesTiles`,
  `testTheAutonomyEditorKnowsWhichSquare`, `testTheEditorTellsAutonomy`, `testDiagramShiftKeepsSetup`,
  `testStationLabelsFollowMoves`, `testDeleteAndInsertKeepTheSetup`, `testAMovedTileCarriesItsSetup`,
  `testARunSurvivesADiagramEdit`, `testARunSurvivesAPageRename`, `testDiagramDrawingSettings`,
  `testFindRoute`, `testTheRoutePlayButton`, `testTheRoutingChoiceSurvivesTheUpgrade`,
  `testJavadocsAreAttached`, `testAutoLayoutRace`, `testErrorsStopTheSetupRunning`,
  `testLocomotiveIdentityPropagates`, `testNoSelfRecursiveWrappers`, `testTheWindowAttachesItsRefreshCallback`.
  The mechanical sweeps I did run across all of them: every `indexOf`-based ordering assertion; every
  `assertFalse(x.contains(...))` and whether a positive assertion or an `isEmpty` guard precedes it on the
  same variable; every `@Test` method containing no `assert` or `fail`; every `Random`, `Math.random` and
  `ThreadLocalRandom`; every `LayoutSandbox` site against its close; every `Preferences` write against its
  restore; every `MarklinControlStation.init` against a matching `stop`.
- **Not looked at at all:** `TST-B7`'s and `TS3-B3`'s neighbourhood in `testEditorSurfaceRules` beyond
  what is quoted above; the `@Test`-level content of `testPageIdsAreDurable`, `testBothProtectingSignalsAreThrown`,
  `testStationBlockedByAnotherPoint`, `testBarredArrivalIsNotADestination`, `testCancelRestoresPlacements`,
  `testDiscardedEditsDoNotDeleteSetup`, `testAutonomyStoreSettingsMatrix`, `testAutonomyTileMove` - all of
  which build no window and belong to pass 3.
- **Two questions I could not settle by reading**, both named at the finding: whether an undisposed
  `TrainControlUI` keeps its test JVM alive (`C6`), and whether the deferred work `testEveryLanguageFits`
  describes can still land after `sandbox.close()` (`C2`). Both are one run each and both are stated as
  readings rather than as measurements.
- **The GUI rules I checked for coverage and found covered**, so the absence of findings is a result: the
  route editor's lock (four guards plus the drawing rule), the loc-mapping ceiling (predicate, affordance
  and guard), the caption drag (what it attaches to, what the mark asks, what the spacers refuse), the
  caption colour and placeholder rules, `hidesStationCaptions`' four-way table, the axis ruler's toggle in
  both windows and its absence from the viewer, the tunnel highlight's three cases, the home-assignment
  trio, and every window's icon. The one I checked and found uncovered is `B4`.
- **Nothing was run.** No `javac`, no `ant`, no `java`, no TestNG, no `one.sh`, no `battery.sh`, no agent
  under me. `cs2_sample_layout` was never read from or written to; the only thing this pass did to it was
  observe that `testEveryLanguageFits` names it, which `D3` explains.

---

## Pass 2 - the core tests

**Reviewed:** `test/core/`, on 2026-09-03. No tests were run; every claim below is from reading.

Scope in numbers: the 72 classes of `test/core/` (48,348 lines, 900 `@Test` methods). Read in full:
`testMessageBundles`, `testRoutePicking`, `testRouteCommandParity`, `testLayoutBfs`,
`testTimetableOnDerivedGraph`, `testStationPriorityDistribution`, `testStagingSkipsALegWithNoSpeed`,
`testTrainTailClearsEdges`, `testLockEdgesSurviveTheFile`, `testNonReversibleTrains`, and the parts of
`testHomeStaging` (4,639 lines), `testAutonomyDiagramSession` (5,563), `testAutonomyDiagramStore`,
`testAutonomyDiagramReducer`, `testAutonomyDiagramReversal`, `testAutonomyDiagramMonitor`,
`testAutonomyDiagramSampleLayout`, `testAutoLayout` and `testAutonomySimulationSanity` that the sweeps
below pointed at. Every javadoc `MUTATION` claim in the package (100 of them) was listed and eighteen
were re-derived against the production file they name. `D8` says what that leaves.

**Where the previous three passes left this corner.** All three read `test/core`, so most of what is
here is either a confirmation or a residue. `D6` is the ledger: fourteen of the earlier findings in this
scope are genuinely fixed, several of them very well - `TST-A1`, `TST-A2`, `TST-A3`, `TST-A11`,
`TST-C5`, `TCX-A3`, `TCX-B5`, `TCX-B8`, `TCX-C9`, `TS3-B2` and `TS3-B7` are all closed at HEAD and I
could not find a way through any of them. Nine are still open, confirmed line by line.

**The new material is one shape, and it is the repository's own.** A rule is derived correctly in one
class, tested there, and consumed correctly in another class, tested there - and the wire between them
is tested nowhere. `A1` and `B5` are both that, both on the reverse-over-switch guard that this release
added, and `A1` is the more serious of the two.

**One A**, and it is the first in this document. Its entry says why it is not a B.

---

### A - high

#### A1 - `roomAtTheEnd` - the whole input to the reverse-over-switch guard - is named by no test at all

**Status: open.** Missing test. Verified by reading and by `grep`. Severity: the defect that would go
unnoticed is a train backed into a berth it does not fit, standing across the points behind it, which is
the hazard Adam raised on 2026-09-01 and the reason `Layout.measuredRoomToReverseInto` was narrowed on
2026-09-02.

**The chain, and where the suite touches it.**

| Step | Where | Tested? |
|---|---|---|
| 1. The reducer measures the stretch after the last switch | `GraphReducer.roomAfterTheLastSwitch`, `:1101-1125` | **Yes** - `testAutonomyDiagramReducer.testTheRoomAfterTheLastSwitchIsMeasuredSeparately:350-415`, a switch fixture asserting 9, `crossesASwitch()`, and `-1` for bounded-but-unmeasured. One of the best tests in the package |
| 2. The builder writes it into the configuration | `AutonomyBuilder.java:1067` | **No** |
| 3. `parseAuto` reads it back onto the runtime `Edge` | `Layout.java:7998-8001` | **No** |
| 4. `Edge.toJSON` writes it on export | `Edge.java:577` | **No** |
| 5. The guard consumes it | `Layout.measuredRoomToReverseInto:6304-6309` | **Yes** - `testNonReversibleTrains.testTheRoomIsMeasuredFromTheLastSwitch:405-468`, four assertions and a control |

Steps 2, 3 and 4 are the whole of the persistence, and:

```
$ grep -rn "roomAtTheEnd" test/
(no matches)

$ grep -rn "setRoomAtTheEnd\|getRoomAtTheEnd" test/
test/core/testNonReversibleTrains.java:425,446,454   (setRoomAtTheEnd, by hand)
test/core/testAutonomyDiagramReducer.java:388,412    (getRoomAtTheEnd, on GraphReducer.ReducedEdge)
```

The two hits are the two ends. `testNonReversibleTrains` sets the value by hand and says so, at
`:422-424`:

```java
            // The last edge crosses a switch, and four of its units lie beyond it.  This is what the
            // reducer records from the diagram; here it is set by hand, because this test is about
            // what the guard does with it.
```

That is the right decision for that test and it is the sentence that names the gap: nothing anywhere
checks that what the reducer records from the diagram ever *arrives*.

**The mutation, and it is one line.** Delete `if (edge.crossesASwitch()) json.put("roomAtTheEnd",
edge.getRoomAtTheEnd());` at `AutonomyBuilder.java:1067`, or the two-line read at `Layout.java:7998-8001`.
Every edge of every configuration then answers `crossesASwitch()` false - the field's default is
`Integer.MIN_VALUE` (`Edge.java:59`, and `crossesASwitch()` is `roomAtTheEnd != Integer.MIN_VALUE` at
`:320-323`) - so `measuredRoomToReverseInto` never takes its switch branch and falls through to
`return room;` at `Layout.java:6319`, summing every segment of the route. That is precisely the rule
Adam replaced, and `Layout.java:6290-6294` says what it costs:

```java
        // What this replaces summed every segment of the route, which was too permissive by exactly
        // the track before that switch - and a train longer than the remainder comes to rest standing
        // on the points, where it blocks every route through them while the model records it only at
        // the berth.
```

The whole suite stays green. `testAutonomyDiagramReducer` still measures 9 on the `ReducedEdge`;
`testNonReversibleTrains` still sets 4 by hand on the runtime `Edge`; neither ever asks the loader.

**Nothing else covers it, and I checked the two places that might have.** `test/baseline/configuration.json`
contains no `roomAtTheEnd` at any of its five edges, so `testConfirmedGoodState`'s byte comparison cannot
see the write appear or disappear. `testAutonomyDiagramReversal.testAnUnmarkedLayoutIsUntouched:361-386`
compares two builder outputs against each other, so both lose the key together.

**The test that is missing already exists for the sibling key, and its javadoc is this finding.**
`test/core/testLockEdgesSurviveTheFile.java:19-34`:

```
 * adds the lock itself, by calling `addLockEdge` on a graph it built. Nothing asked whether
 * `Layout.fromJSON` produces any. Delete the `addLockEdge` call in its loader and the whole suite stays
 * green while every file-loaded configuration comes up with no crossing protection whatsoever: the
 * builder writes `lockedges` into the file, the file is read, and the locks are silently dropped.
 *
 * That is the worst shape a defect can have on this railway - protection that is absent rather than
 * wrong
```

Substitute `roomAtTheEnd` for `lockedges` and `setRoomAtTheEnd` for `addLockEdge` and the paragraph is
unchanged. `lockedges` was closed on 2026-08-31; `roomAtTheEnd`, the newer key on the newer safety rule,
was not given the same treatment.

**The smallest fixture.** The switch page `testAutonomyDiagramReducer:355-372` already builds -
sensor, track, `SWITCH_LEFT`, track, track, sensor, with tile lengths 5/7/3/4/2 - run through
`new AutonomyBuilder(reducer, null).build()` and then `model.parseAuto(...)`, with three assertions on
the runtime `Edge`:

```java
        Edge arriving = layout.getEdge(<start>, <end>);

        assertTrue(arriving.crossesASwitch(),
            "an edge that crosses a switch came back from the file saying it does not, so "
            + "measuredRoomToReverseInto walks straight past it and bounds the train by the whole "
            + "route - the rule Adam narrowed on 2026-09-02, silently un-narrowed by the loader");

        assertEquals(arriving.getRoomAtTheEnd(), 9,
            "the room beyond the last switch did not survive being written down and read back");
```

plus the negative on the switchless page at `:393-399`, whose edge must come back with
`crossesASwitch()` false - because an absent key meaning "unbounded" is the half the comment at
`AutonomyBuilder.java:1064-1066` says is load-bearing, and a loader that defaulted it to `0` instead of
`MIN_VALUE` would refuse every train on the layout.

**Why A and not B.** The README grades a missing test by the defect that would go unnoticed. `TCX-A1`
graded the same rule's other gap - the mid-path reversal - an A on the same reasoning, and this one is
wider: `TCX-A1` is a case the guard does not cover, this is every case the guard does cover, on every
configuration that is loaded from a file rather than built in memory, which is all of them.

**Not verified by running.** I have not watched a configuration round-trip. The claim rests on reading
the three sites, on the `grep` above, and on `Edge.java:59`/`:320-323` for what the default answers.

---

### B - medium

#### B5 - `unmeasuredAfterTheLastSwitch` stops at the last switch, and no fixture has a switch

**Status: open.** Verified by reading. This is `A1`'s sibling: the *other* half of Adam's 2026-09-02
ruling, tested by a fixture that cannot tell the ruling from what it replaced.

`GraphReducer.unmeasuredAfterTheLastSwitch` (`:1145-1168`) is the editor's side of the room rule - it
names the squares the operator still has to measure before the guard can judge anything. Its whole
narrowing is one line, `:1161`:

```java
            if (component != null && component.isSwitch()) return out;
```

Its own javadoc, `:1131-1136`, says that is the point:

```
     * The mirror of `roomAfterTheLastSwitch`: that one adds the lengths up, this one names the tiles
     * that have none, so the editor can ask for exactly what the guard needs and no more.
```

**One test reaches it, through one fixture, and the fixture is a straight run.** The only caller is
`AutonomySession.reversalsWithoutLength` (`:2035`), and:

```
$ grep -rn "reversalsWithoutLength" test/
test/core/testAutonomyDiagramSession.java:2281, 2288, 2298, 2300, 2308, 2316, 2318
```

all inside `testTheEditorAsksForLengthsWhereTrainsReverse:2266-2319`, which opens `runOfTrack()`.
`runOfTrack()` is `testAutonomyDiagramSession.java:1064-1076`:

```java
        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
```

There is no switch on it, so `:1161` is never taken. The method's documented behaviour with no switch is
to walk the whole edge (`:1138-1141`, *"An edge that crosses no switch has its WHOLE length asked for"*),
which is exactly what deleting `:1161` would make it do everywhere.

**Mutation that survives:** delete `GraphReducer.java:1161`. Every assertion in
`testTheEditorAsksForLengthsWhereTrainsReverse` still holds - the fixture's edge has no switch either
way - and every other test in the suite is untouched, because nothing else calls the method. On a real
layout the notice then asks for lengths on the track *before* the switch as well, which is the "nag
rather than a notice" the production comment at `AutonomySession.java:2028-2032` cites Adam's ruling
against, and worse: because the guard never reads those squares, the operator can measure everything the
notice asks for and the list still will not clear the way the test at `:2316` says it must.

The asymmetry is the tell. `roomAfterTheLastSwitch`'s identical stop at `GraphReducer.java:1115` **is**
covered, by a switch fixture, with a failure message that names the number the mutation produces
(`testAutonomyDiagramReducer:388`, *"Counting the switch tile gives 16, and finding the wrong switch or
none gives the whole 21"*). The mirror got the sentence and not the fixture.

**The smallest fixture** is the one `testAutonomyDiagramReducer:355-363` already writes, opened in a
session instead: make the far sensor a turn-around, record one length somewhere to open the
`measuresAnyTrack()` gate, and assert the map's *value* - which is already a count of unmeasured squares
(`AutonomySession.java:2040`) - is 3 and not 4. The value being a count is what makes this one assertion
enough.

---

#### B6 - the message-bundle scan that has no floor is the newest of the three

**Status: open.** Verified by reading. This is what is left of `TST-B16`, and the site that is left is
the one added after the finding.

`test/core/testMessageBundles.java` scans `src/**.java` in three tests. Two of them assert they scanned
something, with the reason written out - `:430-434`:

```java
        // javaSources returns empty, not a failure, when listFiles() is null - run from anywhere but
        // the project root and this scans zero files, indistinguishable from "no offenders".
        // testJavadocsAreAttached and testNoSelfRecursiveWrappers guard the same hazard the same way.
        assertFalse(sources.isEmpty(),
            "precondition: nothing was scanned under src/ - run from the project root");
```

and `:586-587`, identically. The third does not. `testNothingAsksForAKeyThatIsNotThere:760-804`:

```java
        for (File source : javaSources(new File("src")))
        {
            ...
        }

        assertTrue(missing.isEmpty(),
            "something asks for a message key that is not in the bundle, so it would show the operator "
            + "the raw key instead of a sentence: " + missing);
```

`javaSources` returns an empty list rather than throwing when `listFiles()` is null
(`:545-560`), so with the working directory anywhere but the project root the loop runs zero times,
`missing` is empty, and the assertion passes having read no Java at all.

**It is not floorless by accident of style - the same method has two floors already**, on the other
side of the comparison: `assertTrue(output.size() >= 2, ...)` in `bundles()` at `:81-82` (*"A lint that
silently finds nothing is worse than no lint at all"*) and `assertTrue(english.size() > 1000, "the
bundle did not parse: ...")` at `:771`. Both guard the bundle. Neither guards the scan, and the scan is
the half that can go to zero.

**What it costs.** This is the only test in the suite that catches a message key renamed in the bundle
and not at its call site, and its javadoc says why that direction is the one that matters
(`:745-757`): *"a key renamed in the bundle and not at its call site puts the raw key on screen, in the
one place a person is being told what went wrong."* A green run that scanned nothing reads as that
protection being present.

Graded B to match `TST-B16`, which named this file and is otherwise closed. The fix is the line its two
siblings already carry, copied to `:782`.

---

### C - low

#### C8 - `COVERED_HERE` is the half of the coverage index that nothing checks

`test/core/testRoutePicking.testEveryRuleIsCoveredSomewhere:202-241` is the guard that keeps the
path-preference rules honest as the enum grows, and half of it was hardened and half was not.

The `COVERED_ELSEWHERE` half got a reflective check on 2026-09-01, with the reason at `:78-81`:

```java
     * Naming a rule in COVERED_ELSEWHERE used to be nothing more than a comment - true today, but
     * nothing would notice if the file it points at were deleted or the method renamed. This map is
     * what turns that into a check: the covering method has to still exist, and still be a @Test,
     * or testEveryRuleIsCoveredSomewhere fails instead of continuing to claim the coverage is there.
```

Every word of that is true of `COVERED_HERE` (`:47-55`), which is a bare `EnumSet.of` of eight rules and
is tested only by `assertTrue(COVERED_HERE.contains(rule) || COVERED_ELSEWHERE.contains(rule), ...)` at
`:206`.

**Mutation that survives:** delete `testTheSensorRulesAreMirrors` (`:110-125`). `FEWEST_POINTS` and
`MOST_POINTS` are then tested by nothing anywhere in the suite - I checked, they appear in no other test
class - and `testEveryRuleIsCoveredSomewhere` goes on reporting both as covered, because they are still
in the set. The same holds for `testTheStationRulesAreMirrors` and `BALANCED_PRIORITY`'s three tests.

This is what is left of `TST-C4` (*"`testEveryRuleIsCoveredSomewhere` is a coverage index, not
coverage"*). The near half needs the same treatment the far half was given: a
`Map<PathPreference, String>` naming the method in this class, checked by reflection in the same loop.

Two smaller things in the same class, recorded here rather than filed:

- `TCX-C1` stands unchanged. The three mirror tests at `:110-166` each run one trial while their
  unmeasured sibling at `:272` runs twenty, and no `testRoutePicking` fixture sets a block on any point,
  so `Layout.sensorsOn`'s de-duplication is still uncovered.
- `TCX-C9` is **fixed**: the arithmetic in the failure messages at `:634`, `:635` and `:736-737` now
  reads `(9+1)*1000/18 = 555` and `(0+1)*1000/2 = 500`, which is what integer division gives.

#### C9 - a source scan that pins a newline, on a repository checked out with `core.autocrlf=true`

`test/core/testTrainTailClearsEdges.java:221-225`, the assertion `TCX-A3` was fixed into:

```java
        assertTrue(source.contains("tailHasProvablyPassed(pathIsUnmeasured, waiting[1],\n"
            + "                                loc.getTrainLength())"),
            "the clearing loop no longer passes the locomotive's length to tailHasProvablyPassed, so "
            + "the rule compares against nothing and every edge is handed back the moment the head "
            + "leaves it");
```

The anchor spans a line break and pins the wrapped line's 32 spaces of indentation. `source` is
`Layout.java` read off disk, and:

```
$ git config --get core.autocrlf
true

$ cat .gitattributes
*.sh text eol=lf
test/baseline/** text eol=lf
```

Nothing under `src/` is pinned, so a fresh clone on a machine with Git's Windows default converts
`Layout.java` to CRLF on checkout and the file then contains `waiting[1],\r\n`, which this `contains`
does not match. The working tree here is LF today (`Layout.java`: 8,242 LF, 0 CRLF), so it passes now;
what makes this worth writing down is that **the same hazard was realised in this repository on the day
of this audit**, and `.gitattributes` records it:

```
# The blessed baseline is compared byte for byte against text this application builds in memory, which
# joins its lines with LF.  Checked out as CRLF it failed at line 1 with two lines that look identical
# (found by the battery, 2026-09-03).
```

The fix was scoped to `test/baseline/**` and the siblings were not swept. There are two others:
`test/regression/testEditorSurfaceRules.java:2264` (`"new javax.swing.JCheckBox(\n    describeTile(tile)"`)
and `test/regression/testSwitchingToACentralStationLayout.java:1108` (`"\nui.splashConnecting="`, over a
`.properties` file); both are pass 3's, listed here so they do not have to be re-derived.

C rather than B because the failure is loud: the suite goes red on a fresh clone. What makes it more
than nothing is *what it says when it does* - "the rule compares against nothing and every edge is
handed back the moment the head leaves it" accuses the code of the exact safety defect
`testTrainTailClearsEdges` exists to prevent, on a tree where nothing is wrong. The one-line fix is
`* text=auto eol=lf` in `.gitattributes`; the local one is to normalise whitespace out of the anchor.

#### C10 - a floor of one sample in four hundred, under a javadoc that promises a tenth

`TCX-C2`, confirmed at HEAD, with the part `TCX` did not say: the class javadoc states the stronger rule
and the assertion does not implement it. `test/core/testStationPriorityDistribution.java:41-46`:

```
 * ON RANDOMNESS.  `pickPath` shuffles its destinations with a Random this test cannot seed, so these
 * assertions are statistical.  They are written to be decisive rather than tight: "this station is
 * never chosen" and "each of these is chosen at least a tenth of the time" over 400 samples.
```

`testTheCompletelyRandomRuleIgnoresPriority:206` does implement it - `>= SAMPLES / 10`. Its sibling
`testTheHighestPriorityBandIsTheOnlyOneUsed:154` does not:

```java
                assertTrue(seen.getOrDefault(station, 0) > 0,
                    station + " shares the highest priority and was never chosen - so the band is not "
                    + "being picked from at random, it is being picked from in some fixed order.  "
```

One count in four hundred satisfies it, and the message claims the rule picks from the band *at random* -
a 399-to-1 split is a fixed order that passes. Twelve cases run through that assertion, seven of them
with two or three stations sharing the top band, so the "at random" claim is the one this test is for.
`SAMPLES / 10` is the number its own sibling uses and the number the javadoc promises.

Second, in the same class: `prioritise` (`:358-368`) flattens a hand-written list of four other
prioritised stations, `OTHER_PRIORITISED` at `:67-70`, and nothing checks the list is complete. Its own
javadoc says what an omission costs - *"a banded rule would settle one of those bands before it ever
looked at these three"* - and the frozen snapshot is a real railway that could gain one. One assertion
after the flattening, that no point outside A/B/C carries a non-zero priority, closes it.

#### C11 - two residues in `testTimetableOnDerivedGraph`, both from half-applied fixes

**The count is over the wrong set.** `:213-216`:

```java
        assertTrue(startedAt.size() >= locomotives.size(),
            "every locomotive this test placed stands on a station, so all of them should have been "
            + "recorded as restorable - found " + startedAt.size() + " for " + locomotives.size()
            + " placed" + andTheSeed());
```

`startedAt` is filled at `:197-211` by walking **`layout.getLocomotivesToRun()`** - every locomotive on
the graph - and keeping each one standing on a destination. The comment eleven lines above
(`:172-181`) says why that is deliberate: *"The configuration carries its own placements, so the graph
holds more locomotives than were put there here."* So the assertion compares a count of *all* restorable
trains against a count of *placed* ones, and on a snapshot that parks trains all over the railway it is
satisfied by the configuration's own placements. A placed locomotive that failed to land is invisible to
it. The assertion the message describes is
`assertTrue(startedAt.keySet().containsAll(locomotives), ...)`.

**And `TST-B11`'s guard still falls through silently.** `:456-464`:

```java
        if (session.getStore().getConfigurationNames().contains(CONFIGURATION))
        {
            session.getStore().setActiveConfiguration(CONFIGURATION);
        }
```

with the comment two lines above stating the rule: *"Deriving whichever happened to be active last would
make this test describe a different railway from one machine to the next."* `TST-B11` was that the
constant named a configuration the fixture does not have; the fix corrected the constant to `"Main"` and
left the `if`. The class's own javadoc at `:104-109` explains that the old bug *"only ever worked by
coincidence, because Main is also the only configuration the fixture has"* - which is still the reason it
works. I checked: `test/test_layout_snapshot/config/autonomy/` holds one file,
`configuration-Main.json`. An `assertTrue(names.contains(CONFIGURATION), ...)` is the difference between
a guard and a preference.

#### C12 - "the dispatch only" is the whole file from `execRoute` down

`test/core/testRouteCommandParity.testEveryKindOfferedAsACommandIsOneExecutionActsOn:126-199` carves out
what it calls the dispatch, at `:130-136`:

```java
        // The dispatch only, so a mention in a comment or in the s88 TRIGGER machinery - which is a
        // different thing entirely and does use feedback - cannot be read as a branch.
        int from = source.indexOf("private void execRoute(boolean auto, int recursionLimit,");
        ...
        String dispatch = source.substring(from);
```

`substring(from)` runs to the end of `MarklinRoute.java`. `execRoute` ends at `:927`; the file ends at
`:1293`. Everything between is in scope, and one of the predicates the table asks about is already there:
`MarklinRoute.java:1220`, inside `toCSV`, is `if (r.isAccessory())`.

Nothing is vacuous today - `ACCESSORY` is both offered and dispatched, so the extra match changes no
answer, and both `assertEquals(..., "")` assertions are honest for every one of the thirteen kinds. What
the finding is, is that the bound the comment claims is not the bound the code takes, and the two kinds
that are *not* offered (`FEEDBACK`, `AUTO_LOCOMOTIVE`, per `CommandRow.canBeACommand:209-212`) are
protected by nothing but the absence of their predicate from 363 lines of unrelated methods. A second
`indexOf` for the method's closing brace, or an anchor on the next declaration, is the fix. The class
already knows the shape - the sentence quoted above was written about exactly this hazard one line
earlier.

`TST-C6` is confirmed open in the same class: `testRubbishIsRefused:81-94` wraps its call in
`catch (Exception expected) { }`, so any checked or unchecked exception whatsoever counts as a refusal -
a `NullPointerException` or a `NumberFormatException` out of a half-read line satisfies it exactly as
well as `fromLine`'s own deliberate rejection does, and those are the outcome the test's javadoc says is
*"worse than one that fails"*. (`AssertionError` is the one thing it does not swallow, being an `Error`,
so the `assertNull` inside the `try` is at least still live.) The fix is the one
`testLayoutBfs.testPointFromAnotherLayoutIsRejected:292-313` already uses next door: assert the message
is the guard's own, with the reason written out.

#### C13 - two more assertions the fixture guarantees, in a file whose neighbour states the rule

New sites for `TCX-C3`. `test/core/testStagingSkipsALegWithNoSpeed.java:132-140`:

```java
        loc(LOC_MOVING).setPreferredSpeed(35);
        loc(LOC_STUCK).setPreferredSpeed(0);

        assertTrue(loc(LOC_STUCK).getPreferredSpeed() < 1,
            "precondition: the first entry's locomotive has no usable speed");

        assertTrue(loc(LOC_MOVING).getPreferredSpeed() >= 1,
            "precondition: the second entry's locomotive does, or this test cannot tell a skipped "
            + "leg from a broken fixture");
```

Both read back what the two lines above them set; they can fail only if `setPreferredSpeed` is a no-op.
`:128` is the same shape - `setTimetableSequential(true)` at `:126`, read back at `:128` - and its
message claims something the ordering does not test (*"the sequential flag survived setTimetable"*: the
`setTimetable` call is at `:122`, before the setter, not after it).

Recorded because the rule is written down in the class next door, in the file this one was split out of.
`test/core/testHomeStaging.java:306-308`:

```java
        // NOT a precondition on the two lines above - those set these values, and asserting them back
        // proves only that a setter sets (V33-C8).  What is worth checking is the thing the fixture
        // has to be true for the assertions to mean anything, and neither of these is set here.
```

Harmless on their own. What they cost is that this class's *real* precondition - that
`pathTo(layout, LOC_STUCK, "SG B")` and `pathTo(layout, LOC_MOVING, "SG D")` returned genuinely
different paths for genuinely different trains - is asserted only by `fail()` inside the helper
(`:251`), while three assertions labelled "precondition" check setters.

---

### D - not defects

#### D6 - where the earlier passes' `test/core` findings stand at HEAD

Re-checked one by one against the tree, with the production file each names opened.

**Fixed, and several of them very well:**

| Finding | What I checked at HEAD |
|---|---|
| `TST-A1` - the point-key parity list omitted five keys | `testAutoLayout.testEveryKeyParseAutoReadsIsAlsoWritten:1117-1231` now lists fifteen keys and checks `terminus`, `reversing` and `loc` separately on a standalone `Point`, with the reason. I re-derived the reader's list from `Layout.fromJSON` (`:7089` onward) - name, station, s88, x, y, block, protectingSignal, blockedBy, home, excludedLocs, maxTrainLength, terminus, active, autoDestination, reversing, speedMultiplier, priority, loc - and the test covers all eighteen. It also restores the four fields `TST-B20` named, citing it |
| `TST-A2` - the legacy-sensor gate compared a set with itself | `testAutonomyDiagramSampleLayout.derivableSensors:252-273` now reads `graph.getFeedbackTiles()` and never touches `reducer.getPoints()`, with a nine-line comment naming the finding and the mechanism |
| `TST-A3` - the tail-clearing gate was proved present, not effective | `testTrainTailClearsEdges.testAnEdgeTheRuleRefusesToClearStaysHeldWhileARealPathRuns:320-368` drives a real three-edge path through `executePath` in simulate mode and reads `getActiveAccs()` per leg, **with a control** (`:353-362`) proving the observation can see a clear as well as a hold. This is the strongest fix in the round |
| `TST-A4` - the soak's actuation floor had no baseline | `testAutonomySimulationSanity:240` subtracts `baselineActuations.get(name)`, with the mutation written above it |
| `TST-A11` - `invalidate()` called by no test | `testAutonomyDiagramMonitor.testInvalidateForcesTheNextRefreshToRepublish:432-494`, three counts with a no-change control between them |
| `TST-C5` - the parity corpus omitted four of eleven kinds | `testRouteCommandParity:41-54` now exercises all eleven `RouteCommand.RouteCommandXxx` factories - I listed them from `RouteCommand.java:86-218` and they match |
| `TCX-A3` - the second assertion went vacuous | Anchored on the whole call now (`testTrainTailClearsEdges:221`), with the finding named. `C9` is about the anchor's *form*, not its correctness |
| `TCX-B5` - a builder compared against an identical builder | `testAutonomyDiagramReversal:361-386` now marks a real tile first as the control, citing the finding |
| `TCX-B7` / `TST-A4` first half - the headline assertion unfalsifiable in simulate mode | Answered by a companion rather than by changing the soak: `testPathValidationCanActuallyFireOutsideSimulateMode:452-524` builds a non-simulate Layout and corrupts an accessory so the guard must fire. Its javadoc is candid that the soak's own three assertions remain unfalsifiable and says why that is the right split |
| `TCX-B8` - `testNothingIsLoadedWhenAlreadyHome` survived its mutation | `testHomeStaging:1094-1112` now asserts the SEQUENTIAL flag both before and after, with the finding named and the reason the empty timetable could not catch it |
| `TCX-C9` - arithmetic wrong by 1000 | Corrected at all four sites in `testRoutePicking` |
| `TS3-B2` - `HS alpha` left 40 long and reversible | `testHomeStaging:284-292` captures both and restores them in a `finally`, citing the finding |
| `TS3-B7` - the "a longer approach is more room" rule untested | `testHomeStaging:3482` onwards, twenty rounds, and the production comment at `HomeStaging.java:1043-1060` records that three reviewers found the dominance interaction independently |
| **Pass 1's `B3` handoff** - `testAutonomyDiagramSession:1713-1832` | Both preference sites are correct. `:1705-1706` and `:1798-1799` take `had` from `prefs.get(..., null) != null`, and both `finally` blocks `remove()` rather than write when it was unset (`:1738-1747`, `:1830-1838`). The rule pass 1 is asking about is not broken here - it is stated here, at `:1693-1702`, better than anywhere else in the suite |

**Still open, confirmed at HEAD:**

- **`TCX-C7`, all three bullets.**
  `testHomeStaging:2526-2529` still says *"the terminus is unusable, so there is nowhere to step aside
  and no plan exists. isPathClear refuses a terminus to a non-reversible locomotive"* above an assertion
  that reads `assertTrue(plan.isPossible(), ...)` at `:2564`. The method *name* was changed to
  `testANonReversibleLocomotiveMayBeParkedAtATerminus` and the javadoc was not, so the two halves of the
  same comment now disagree with each other.
  `:2668-2670` still says *"Neither the planner nor isPathClear has any rule about reversing points, so
  this pins the absence"*; `HomeStaging.mustBackIn` is at `:1609`, `connected(from, to, mustReverse)` at
  `:1748`, `startsTurned = from.isReversing() || from.isTerminus()` at `:1783`, and `isPathClear`'s
  length clause fires on `ending.isReversing()`.
  `:3017` still gives the BottomMainC/BottomMainCTerm example that `HomeStaging.java:1868-1875` records
  as measured and false on the derived graph, in a paragraph written specifically to correct it.
- **`TCX-C4`, both bullets.** `testAutonomyDiagramReducer:631-633` still floors on
  `reducer.getEdges()`, which the fixture's main line satisfies whether or not the passing loop joins up
  - a floor on an edge through `key("main", 3, 0)` is what would close it.
  `testAutonomyDiagramReversal.testASplitCopyNeverCollidesWithAnAuthoredName:723-754` still asserts
  uniqueness over the emitted points with nothing asserting the middle square split, and no floor on the
  loop at `:748-753`.
- **`TCX-C2`.** Restated with the javadoc contradiction as `C10`.
- **`TCX-C1`.** Restated under `C8`.
- **`TCX-C3`.** Both `test/core` entries I re-checked are unchanged: `testAutonomyDiagramPorts:173-174`
  is still preceded by `assertEquals(..., pairs("SW"))` on the same expression at `:171-172`, and
  `testLayoutPickPath:500` still asserts flags set eight lines above it. `C13` adds two sites.
- **`TCX-C6`.** `test/core/testNetworkProxy.java:204` and `:225` are still the suite's only
  `dependsOnMethods`, still chained off a test that waits 15s on a latch.
- **`TCX-C8`.** `testAutonomySimulationSanity:402` and `:413` still assert the identical condition,
  precondition and result, with no control showing a live layout's clear-behind actually clearing.
- **`TCX-B10`.** `testRoutes` still has three unseeded generators: `:49`
  (`private static final Random RANDOM = new Random()`), `:58` inside `generateRandomRoute`, and `:932`
  (`while (newRoutes.size() < (new Random()).nextInt(40) + 1)`). It is the only unseeded randomness in
  `test/core`; every other generator in the package is seeded and puts the seed in the message
  (`testLayoutBfs:364`, `testLayoutBfsEquivalence:205`, `testReturnHomeOnRealLayout:72`,
  `testTimetableOnDerivedGraph:97-99` with `andTheSeed()` on every message).
- **`TST-C6`.** Restated under `C12`.

#### D7 - classes read against their own stated mutations and found sound

Recorded so the absence of findings against them is a result rather than an omission.

- **`testLayoutBfs`** - the model for property testing in this suite. Both generator-driven tests carry
  floors with the reason (`reachable > 300` at `:523`, `exhausted > 50` at `:584`), the reference
  shortest-path at `:418-448` is written independently of `Layout.bfs`, the generator mirrors only
  `createEdge` calls that succeeded so the reference cannot disagree about which edges exist (`:399-408`),
  and `testExcludedPathFallsBackToAnAlternativeViaASharedPoint:182-211` repeats twenty times with the
  measurement that justified it (*"247 of 500 runs"*) in the javadoc. The one weak spot is
  `assertTrue(alternativesFound > 0, ...)` at `:588` - a floor of one across 150 seeds for the property
  the whole file is named after - and it is genuinely latent, since the same loop's `exhausted > 50`
  cannot be met by graphs with no alternatives to find.
- **`testNonReversibleTrains.testTheRoomIsMeasuredFromTheLastSwitch:405-468`** - four assertions on one
  fixture that each fail for a different reason (too long, exactly fits, one over, bounded-but-unmeasured),
  and the fifth (`:454-461`) is a *widening* asserted deliberately. `TS3-C3`'s complaint about the second
  stated mutation was acted on: the javadoc at `:262-267` now explains why that mutation was withdrawn and
  names the test that covers the distinction properly.
- **`testAutonomyDiagramReducer.testTheRoomAfterTheLastSwitchIsMeasuredSeparately:350-415`** - three
  states asserted (`crossesASwitch` false, a number, `-1`), the switch tile deliberately given a length
  of 7 so that counting it produces a different, named number, and a second page built for the negative.
- **`testAutonomyDiagramStore.testTwoRoutesAcrossOneSquareKeepTheirOwnDirections:83-118`** - the rare
  case of a test whose javadoc records the fixture that could not have caught the bug (*"every fixture in
  the repository used `RouteId(0, 0)` - a pair that reads the same either way round"*), and whose fixture
  is two routes that are each other's mirror, plus a third that was never set as the control.
- **`testAutonomyDiagramSession.testTheEditorWarnsAboutACopyThatReachesNoOtherStation:818-880`** - runs
  the control first (the stub marked reversible, nothing reported), then removes exactly one flag, then
  asserts both what is named and what must not be, and finally that the *square-level* check stays silent
  so the two checks are not two ways of saying one thing.
- **`testLockEdgesSurviveTheFile`** - one test, both halves (the list is populated, and a held lock
  actually refuses a path), with a control at `:126-128` proving the refusal is not the fixture refusing
  everything. It is the template `A1` asks for.

#### D8 - what pass 2 did not cover

Said plainly.

- **Read in full:** `testMessageBundles`, `testRoutePicking`, `testRouteCommandParity`, `testLayoutBfs`,
  `testTimetableOnDerivedGraph`, `testStationPriorityDistribution`, `testStagingSkipsALegWithNoSpeed`,
  `testTrainTailClearsEdges`, `testLockEdgesSurviveTheFile`, and `testNonReversibleTrains` from `:250`
  down.
- **Read in part, guided by the sweeps:** `testHomeStaging` (the first 400 lines, the reversing and
  terminus sections, `:2500-2600`, `:2660-2720`, `:3000-3060`, `:3480-3520`, and the `MUTATION` javadocs
  of all 88 tests), `testAutonomyDiagramSession` (`:780-880`, `:1660-1840`, `:2240-2340`, `:1064-1088`,
  and all 23 `MUTATION` javadocs), `testAutoLayout` (`:1100-1324`), `testAutonomySimulationSanity` (in
  full apart from `:340-400`), `testAutonomyDiagramReducer` (`:325-700`),
  `testAutonomyDiagramReversal` (`:340-400`, `:700-800`), `testAutonomyDiagramMonitor` (`:400-500`),
  `testAutonomyDiagramSampleLayout` (`:225-290`, `:630-760`), `testAutonomyDiagramStore` (`:1-120` and
  every `assertFalse(...contains(...))` in it).
- **Swept mechanically, not read:** all 72 classes, for - every `@Test` whose body contains no `assert`
  or `fail` (one hit, a false positive: `testTimetableOnDerivedGraph:134` delegates to a helper that
  asserts); every `new Random` / `Math.random` / `ThreadLocalRandom` / `Collections.shuffle`; every
  `assertEquals(x, x)` and `assertNotNull(SomeClass.class)`; every `assertFalse(....contains(...))`
  against whether a positive assertion precedes it on the same expression (44 sites); every `contains`
  literal carrying an embedded `\n`; every assertion whose message says "precondition" against whether a
  matching setter appears in the five lines above it (15 sites, of which `C13` names the two new ones);
  every `MarklinControlStation.init` against the class's teardown; every `LayoutSandbox` site against its
  close; every `getPrefs()` write against its restore.
- **Not looked at at all:** `testAutonomyDiagramTiles`, `testAutonomyDiagramPorts` beyond `TCX-C3`'s
  site, `testConditionOutline`, `testConditionRows`, `testCommandRow`, `testTileSelection`,
  `testDiagramResize`, `testThreeWaySwitch`, `testAtomicWrite`, `testCS2Message`, `testAccessory`,
  `testFeedback`, `testLoadData`, `testLocDB`, `testMockCentralStation`, `testUdpMessagesReachTheWire`,
  `testCentralStationDetection`, `testCS3NotFoundDetection`, `testControlStationFaults`,
  `testAutoDetect`, `testParseWebServer`, `testParseCS2Layout`, `testParseCS2Routes`,
  `testParseCS3Loks`, `testParseCS3Routes`, `testMultiUnitMembership`, `testImportRename`,
  `testInvalidInput`, `testAdvancedRoutes`, `testRouteInventory`, `testRouteRoundTrip`,
  `testRouteReachesTheRails`, `testRouteTilePlacement`, `testWhyStuck`, `testLayoutTiles`,
  `testLayoutTimetable`, `testLayoutRenameKeys`, `testMaxActiveTrains`, `testTracedPathIsContinuous`,
  `testTheStationGoingAwayDoesNotJamSwitching`, `testALocomotiveDoesNotEvictItself`,
  `testLocomotiveExclusions`, `testReturnHomeOnRealLayout`, `testReturnHomeSequencesAReversal`,
  `testTrainsComeHomeToTheirPlatforms`, `testTimetableCaptureThroughARealRun`, `testLocomotive`,
  `testAutonomyGroundTruth` beyond its header, and `testLayoutBfsEquivalence`. The mechanical sweeps
  above ran across every one of them; nothing beyond the sweeps was read.
- **The coverage question, answered where I answered it.** I enumerated every method of
  `HomeStaging`, `GraphReducer`, `AutonomyBuilder` and `Point` whose name appears nowhere under `test/`
  (8, 13, 13 and 8 respectively) and followed the ones that carry a rule.
  `unmeasuredAfterTheLastSwitch` is `B5`; `roomAtTheEnd`'s three doors are `A1`. The remainder are
  private helpers reached through a tested caller, and I did not chase each one to its call site - that
  is the largest single thing left undone here, and the list is cheap to regenerate.
- **Two claims I could not settle by reading.** Whether `testTheHighestPriorityBandIsTheOnlyOneUsed`
  would in fact catch a fixed-order band pick at 400 samples (`C10` - it is a statistical claim and I
  measured nothing), and whether a CRLF checkout does in fact fail `testTrainTailClearsEdges:221` (`C9` -
  I read the byte counts of `Layout.java` and `.gitattributes`, and did not perform a clone).
  **What I would run for the second:** `git -c core.autocrlf=true clone` into a scratch directory and
  `grep -c $'\r' src/org/traincontrol/automation/Layout.java`, expecting 8,242.
- **Nothing was run.** No `javac`, no `ant`, no `java`, no TestNG, no `one.sh`, no `battery.sh`, no
  agent under me; I spawned no subagents. `cs2_sample_layout` was never read from or written to. I did
  observe, from `git status` alone, that two files under it are modified in the working tree again at
  the time of writing, which is `TCX-A4`'s standing complaint and not a new finding.

---
