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
| B1 | Medium | Eight GUI classes open a `LayoutSandbox` at a point a throw can skip the close, leaving the operator's machine-global layout preference pointing at a temp folder | Pass 1 |
| B2 | Medium | `testARenameReachesTheTimetableOnScreen` survives the mutation its own javadoc names - it drives `timetableSignature`, and nothing drives the guard in `repaintTimetable` | Pass 1 |
| B3 | Medium | Three tests write the operator's own preferences back without the "was it ever stored" guard the fourth was given, and one can leave the setting flipped | Pass 1 |
| B4 | Medium | `AutonomyMenu.refreshEnabled()` - what greys the autonomy menu and what its two tooltips say - has no test anywhere | Pass 1 |
| C1 | Low | `testEveryLanguageFits`'s "the locale reached the text" control passes when both screenshots are missing | Pass 1 |
| C2 | Low | The same class's railway fingerprint is sampled at the instant the sandbox closes, and is skipped entirely when a measurement throws | Pass 1 |
| C3 | Low | `testEditorSwitchClearsPageState.methodSource` anchors `arriveAt` on a call site, not on the declaration, and is rescued only by there being no brace in between | Pass 1 |
| C4 | Low | `testTheActivePageDrawsTheSamePictureAsChoosingIt` still renders one page against itself; the real check is the assertion three lines above | Pass 1 |
| C5 | Low | `testSidebarIcons` regexes `TrainControlUI.java` without stripping comments, and a commented-out `setIconAt` already stands in that file | Pass 1 |
| C6 | Low | `testDiagramLooksRight` builds a `TrainControlUI` in `@BeforeClass` and never disposes it, alone among its siblings | Pass 1 |
| C7 | Low | `testANumberTooWideForItsSquareIsLeftOut` asserts an empty list with no floor on the reader that produces it | Pass 1 |
| D1 | Not a defect | Where the `TST` / `TCX` / `TS3` findings in this scope stand at HEAD | Pass 1 |
| D2 | Not a defect | Five GUI classes read against their own stated mutations and found sound | Pass 1 |
| D3 | Not a defect | `testEveryLanguageFits` names `cs2_sample_layout`; that is the guard, not a leak | Pass 1 |
| D4 | Not a defect | No unseeded generator anywhere in the GUI tests | Pass 1 |
| D5 | Not a defect | What pass 1 did not cover | Pass 1 |

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
