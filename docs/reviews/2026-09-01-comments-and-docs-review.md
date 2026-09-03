# Comments and documentation review (v3.0.0 pre-release fan-out)

**Status:** open

**Prefix:** CMT

Reviewed at `b00ac0c1` (working tree; substantively read at `828b1ff1`, HEAD moved once more during the
pass to a `docs/tools/battery.sh` change that touches nothing cited below), 2026-09-01, by reading and
grepping only - no build, no test run, no application launch. Two Python scripts were written and run
purely to parse text files (the eight `.properties` bundles, and every `.java` file in `src/` for a
`/** */` immediately followed by another `/** */`) - no JVM, `javac`, `ant` or `battery.sh` was invoked
at any point in this pass.

Scope per the assignment: comments, javadoc, user-facing strings, and `docs/**`, with
`Layout.java`, `HomeStaging.java`, `Point.java` and `automationui/*.java` prioritised as the files that
changed most this cycle.

A prior comments/docs pass, `docs/reviews/2026-08-31-comments-and-docs-review.md` (prefix `CDR`), covered
the branch as of `e4c94ac9`, one day and roughly 30 commits earlier. This pass does not repeat that one's
still-valid findings; it re-verifies anything CDR touched that this cycle's changes could have moved
(one had - see CMT-C3), and otherwise looks at what changed since, plus the files CDR did not prioritise
(`automationui/*.java`, `HomeStaging.java`, `Point.java`).

---

## Method

1. `git diff 5f0a75e3 HEAD` (the v2.8.1/master baseline) for the file-level shape of the cycle, then
   `git diff e4c94ac9 HEAD` (yesterday's rc4) on `Layout.java`, `HomeStaging.java` and `Point.java` to
   find exactly what moved since the last comments pass - a single day carried five ruling reversals on
   home/terminus behaviour (commits `7616d2a6` through `828b1ff1`), which is exactly the condition that
   produces a comment describing a rule that no longer holds.
2. Read `Point.java` and `HomeStaging.java` in full (1133 and 1854 lines). Read `Layout.java`'s diff
   since `e4c94ac9` in full, plus the methods it touched in their current, whole-file context.
   `Layout.java` itself (7900+ lines) was not read end to end - see "What this pass did not do."
3. For every comment asserting a rule, greped the method it was attached to for whether the code still
   does what the comment says, and greped the rest of `src/` for the same rule stated elsewhere that a
   fixing commit might not have swept (the project's own recorded failure mode).
4. Read `Automation.md` and all of `AutomationAPI.md`, this time checking every sentence naming a
   concrete UI action (a menu item, a keystroke, a button) against whether that action currently exists
   in `src/`, rather than only checking the topic-level framing CDR-D3 checked yesterday.
5. Parsed all eight message bundles in Python: key-set equality across languages, byte-range (pure
   ASCII), duplicate key definitions within one file, `{n}`-placeholder count agreement per key across
   languages, and - not done in the prior pass - whether each key has any textual occurrence anywhere in
   `src/**/*.java` or `*.form`, cross-checked against every file this cycle deleted.
6. Re-implemented `test/regression/testJavadocsAreAttached.java`'s own detector (`/**...*/` immediately
   followed by another `/**...*/`) in Python and ran it over all of `src/`, to verify its pinned ledger
   (`ALLOWED = 93`, `ORPHANS_BY_FILE`) is still exactly accurate rather than trusting the number.
7. Spot-checked `docs/reference/README.md` and `docs/plans/*.md` against what files actually exist on
   disk and against the git history of what was deleted and when.

---

## Findings

| ID | Severity | Status |
|---|---|---|
| CMT-B1 | B | open |
| CMT-B2 | B | open |
| CMT-B3 | B | open |
| CMT-B4 | B | open |
| CMT-C1 | C | open |
| CMT-C2 | C | open |
| CMT-C3 | C | open |
| CMT-D1 | D | closed (not a defect) |
| CMT-D2 | D | closed (not a defect) |
| CMT-D3 | D | closed (not a defect) |
| CMT-D4 | D | closed (not a defect) |

---

### CMT-B1 - `HomeStaging.canRest`'s own javadoc still lists the terminus rule its body was just told to drop

**File/lines:** `src/org/traincontrol/automation/HomeStaging.java:1615-1637`.

The javadoc, unchanged:

```
    /**
     * Whether a locomotive may come to rest on a station - length, exclusions, and the reversibility a
     * terminus demands.
     */
    private static boolean canRest(Locomotive loc, Point at)
    {
```

Immediately below it, the method's own inline comment (added the same day, 2026-08-31):

```
        // NOT THE TERMINUS RULE (Adam, 2026-08-31).
        //
        // This ended `&& (!at.isTerminus() || loc.isReversible())`, which refused a parking berth as a
        // home to any train that cannot reverse - and most parking berths are terminuses, so on his
        // railway it refused most of the places a train is actually parked.  EN57-947 could not be
        // homed at TunnelLeftPark for this reason and no other.
        ...
        return at.isDestination()
            && at.isActive()
            && !at.getExcludedLocs().contains(loc)
            && at.validateTrainLength(loc);
```

The `return` has no `isTerminus`/`isReversible` term at all. The one-line summary above the method
("length, exclusions, and the reversibility a terminus demands") describes exactly the clause the
paragraph directly beneath it explains was deliberately removed, in the same commit. A reader who reads
only the javadoc - which is the normal way to trust a private helper without re-deriving its body - would
believe `canRest` still refuses a non-reversible locomotive a terminus home, and could "fix" what looks
like a missing check by putting back precisely the line Adam's 2026-08-31 ruling ("I would also like
non-reversing trains to have to back in") took out. This is the same shape as `CDR-B1` in yesterday's
pass: a comment that, if believed, leads a future change in the wrong direction.

**Confidence:** confirmed by reading; the contradiction is between two comments eleven lines apart in the
same method, not something requiring external verification.

---

### CMT-B2 - `Layout.refreshAllProtectingSignals`'s javadoc says it is "called when a run begins"; nothing in `src/` calls it any more

**File/lines:** `src/org/traincontrol/automation/Layout.java:6073-6089` (javadoc and signature).

```
    /**
     * Brings every protecting signal into line with where the trains actually are.
     *
     * Called when a run begins, and forgetting the memo is not enough on its own. ...
     *
     * The cost is one command per signal at the start of a run.  Set against a platform standing green
     * with a train in it, that is nothing.
     */
    public void refreshAllProtectingSignals()
```

Today's commits removed every one of the three call sites this javadoc describes, on Adam's ruling
(`OB-166`, quoted at each removed site): `"Signals should only be touched when a route activates."` The
diff leaves a comment at each former call site explaining the removal - e.g. `Layout.java` around line
1646: `"NO SIGNAL SWEEP HERE ANY MORE (Adam, 2026-08-31: ... ) ... Removed from all three doors that had
it..."` - but the method's own header, which is the first and often only thing a future reader of *this*
method sees, was not touched.

`grep -rn "refreshAllProtectingSignals" src/ test/` finds exactly one production reference (the
declaration itself) and five test references (`test/core/testAutoLayout.java:966` and four in
`test/regression/testBothProtectingSignalsAreThrown.java`, including a `MUTATION` comment: *"putting any
of the three `refreshAllProtectingSignals()` calls back fails this."*). So the method is now dead in
production, called only from tests that exist specifically to prove it stays that way - and its own
javadoc still asserts the calling contract those tests were written to forbid. The trap is exact: a
maintainer who trusts the javadoc and re-adds a call at run start would reintroduce OB-166 (a route's red
signal going green because an unrelated train started moving), and the guarding tests would catch it -
but only if they are run, and the whole reason to fix the comment is so nobody has to find out the hard
way first.

**Confidence:** confirmed by reading and by exhaustive grep of both `src/` and `test/` for the method
name.

---

### CMT-B3 - `AutomationAPI.md`'s "Returning locomotives home" section instructs the reader to use a graph window that was deleted, and to a menu item whose implementation was deleted as dead code

**File:** `AutomationAPI.md`, "Returning locomotives home" (lines 511-535).

Three sentences in this section describe an alternate UI surface called "the graph":

- Line 513: *"The same command is available by right-clicking either the graph or a station on the track
  diagram."*
- Line 517: *"Right-click a station - on the graph or on the track diagram - and pick `Home locomotive`
  ... on the graph, `Control+H` over a station opens the same chooser."*
- Line 519: *"The graph says which stations are spoken for without your having to open a menu..."*

`GraphViewer` - the window "the graph" refers to throughout this codebase's own docs (see
`docs/reference/README.md`, "The graph window (removed 2026-08-21)") - is deleted outright, along with
`GraphEdgeEdit`, `GraphLocExclude`, `GraphRightClickPointMenu` and `GraphRightClickGeneralMenu`. There is
no UI surface left called "the graph"; there is only the track diagram. Every "on the graph" clause in
this section describes a way of doing something that has not existed since 2026-08-21.

The same paragraph (line 517) makes a stronger, checkable claim:

> *"To undo the lot, `Clear all home locomotives` on the graph's background menu drops every assignment
> at once and returns things to exactly the behaviour described above; it appears only once something
> has been assigned."*

This menu item does not exist anywhere in the current application. `src/org/traincontrol/gui/
HomeLocomotiveMenu.java:8-21`, the class this action lived in, says so directly:

```
 * UXR-C19: this class used to hold the whole home-locomotive editor - assigning and clearing a
 * single station's home, the "clear every home" bulk action, and the exclusion-conflict warning -
 * reached from three right-click menus. The editor moved to `AutonomyEditorPanel` ...  and every one
 * of `addStationItem`, `addClearAllItem`, `editHomeLocomotive`, `confirmExclusion`, `apply`,
 * `refuseWhileBusy` and `shortName` was left with no caller anywhere in `src/` or `test/` - verified
 * by grep, not assumed. Removed rather than left behind ...
 * Only `addReturnHomeItem` survives, with its one caller in `LayoutRightclickAutonomyMenu`.
```

`grep -rn "clearAllHome\|ClearAllHome\|clearHomeLocomotives\|Clear all home" src/org/traincontrol/gui/
*.java src/org/traincontrol/automation/Layout.java` finds exactly one hit: the model-level
`Layout.clearHomeLocomotives()` (which line 535 of the same doc correctly cites as the programmatic
equivalent) - nothing in `gui/` calls it. The message-bundle key that would label such a menu item,
`autolayout.ui.menuClearAllHomeLocomotives`, exists in all eight `.properties` files but has zero
occurrences anywhere in `src/` (see CMT-C2) - independent confirmation from a second angle that no menu
currently wires it up.

This is corroborated by the project's own tracking document, `docs/plans/autonomy-ui-feature-ledger.md`
(last updated 2026-08-24), which already lists this exact gap:

> `| Clear all home locomotives | general right-click -> HomeLocomotiveMenu | TODO |`

So two documents disagree about whether this feature exists - one says it works today, the other says it
is not yet built - and the code sides with the second one. A user following `AutomationAPI.md`'s
instructions to look for this item on a right-click background menu will not find it under any name.

**Confidence:** confirmed by reading the deleted-class inventory in `docs/reference/README.md`, the
removal comment in `HomeLocomotiveMenu.java`, an exhaustive grep for any surviving caller, the dead
message key (CMT-C2), and the independent, dated confirmation in the feature ledger.

---

### CMT-B4 - `AutomationAPI.md` and `Automation.md` still say only a reversible locomotive can reach a terminus; that stopped being true today

**Files:** `AutomationAPI.md:388, 401, 525`; `Automation.md:119` (softer wording, same claim).

`AutomationAPI.md:401`: *"For the corresponding locomotive, set `"reversible" : true`. **Only such
reversible locomotives can travel to a terminus** and they will automatically change direction after
arrival."* Line 388 makes the same claim as a table entry ("Only reversible locomotives can do this").
Line 525 lists it as one of three reasons the home-assignment dialog warns and asks for confirmation:
*"one longer than its length limit, one it excludes, or **one that cannot reverse at a terminus**."*

`Automation.md:119` states the same rule more softly, as something the reader is told rather than a
system that enforces it: *"a train that arrives here will need to change direction before it can leave -
so the locomotive must actually be able to do that."*

As of today's commits (`20c30781`, `fbc19cb9`, `280ff08b`, `17cad1fe`), none of this is enforced as an
absolute rule any more:

- `Layout.isPathClear` (the tier every manual dispatch and the staging planner both pass through) no
  longer refuses a terminus to a non-reversible locomotive at all - see the "NO TERMINUS RULE HERE (Adam,
  2026-09-01)" comment at `Layout.java:2277` and following, quoting Adam directly: *"non reversing trains
  must be able to back into a terminus if the graph makes that possible."*
- `HomeStaging.canRest` no longer checks it either (CMT-B1). The home-assignment confirmation dialog in
  `AutonomyEditorPanel.java` (`mayRestHere`, lines 3372-3406) says outright: *"the only reason it can give
  now is the resting one"* - meaning the "cannot reverse at a terminus" branch of line 525's three-reason
  list is gone. Assigning a non-reversible locomotive to a terminus home no longer produces any
  confirmation dialog for that reason.
- What replaced the hard refusal is narrower and route-dependent: a non-reversible locomotive may still
  reach or rest at a terminus, provided the path there passes a reversing point first and so arrives
  already turned (`HomeStaging.mustBackIn`, `Layout.java`'s new train-length-vs-reversal-room check). Full
  autonomy still will not *choose* a terminus for such a locomotive (`pickPath`,
  `hasAutonomousDestination`, both gated on `!end.isTerminus() || loc.isReversible()`), so the autonomy
  half of the old claim survives; the manual-dispatch and home-assignment halves do not.

Both docs state the old absolute rule with no such nuance, and the confirm-dialog claim in
`AutomationAPI.md:525` is now simply inaccurate rather than merely imprecise.

**Confidence:** confirmed by reading the current bodies of `isPathClear`, `canRest`,
`hasAutonomousDestination`, `pickPath`, and `mayRestHere`, against the two docs' text.

**To confirm:** none needed for the code/doc mismatch; a live check (never on `cs2_sample_layout/`) would
be to assign a non-reversible locomotive's home to a terminus square on a sandbox layout and observe that
no confirmation dialog naming reversibility appears.

---

### CMT-C1 - `Point.toJSON`: a leftover comment about the `block` field is stranded above the `protectingSignal` write, and has been since 2026-08-18

**FIXED 2026-09-03.**  `Point.toJSON` has one comment per field again: the protecting-signal paragraph stands over the protecting-signal write, and `block`'s own explanation - the one that had been usurped - is back over `block`.

**File/lines:** `src/org/traincontrol/automation/Point.java:991-1019`.

```
        // Which piece of track this Point is part of.
        //
        // Written because it is READ: parseAuto takes it back, and without this the export path lost
        // it silently - a graph exported and re-imported came back with every square split into
        // independent Points again, so two trains could once more be routed onto one platform.  The
        // operator would have had no way to tell: the file looks like a faithful copy of the graph.
        // The signal thrown to red while this platform is claimed.
        //
        // Written as well as read.  parseAuto has always taken this back in, and leaving it out of the
        // export made the configuration JSON quietly lossy: a setup exported and imported came back
        // with every station-signal pairing gone, and nothing said so.  Exactly what happened to the
        // block field before it, which is the line below.
        // One is written as a bare string, several as an array.  ...
        if (this.protectingSignals.size() == 1)
        { ... }

        if (this.block != null)
        {
            jsonObj.put("block", this.block);
        }
```

`git log -p -L 985,1020` shows how this happened: the first paragraph ("Which piece of track this Point
is part of... export path lost it silently") was written for `block`'s own write, directly above it
(commit `eb8ebe63`, 2026-08-18). A later commit the same day (`6be8999c1`) inserted the `protectingSignal`
write ABOVE `block`'s, and tacked its own explanation onto the end of `block`'s existing comment rather
than giving it a fresh one - leaving a single merged block whose first half describes the field the code
immediately below it is not writing, and ending with a stray, truncated echo of the field-level javadoc
("The signal thrown to red while this platform is claimed" - compare the real doc at line 523, "The
accessories thrown to red while this platform is claimed"). Touched again on 2026-08-21 (`08ddcace`) to
add array support, without correcting the placement.

Nothing here asserts anything false about current behaviour - both explanations are historically accurate
about the bugs they describe - but a reader landing on `if (this.block != null)` today finds no comment
at all (its own having been usurped), and a reader landing on the `protectingSignal` code finds a comment
that opens by describing a different field. This is not caught by `testJavadocsAreAttached`, which only
detects a `/** */` immediately followed by another `/** */`; these are plain `//` comments.

**Confidence:** confirmed by reading the current file and by `git log -p` across all three touches.

---

### CMT-C2 - 229 message-bundle keys, identical across all eight languages, are unused in the current source

**RECORDED, and the dangerous direction is now tested** (2026-09-03).  The 229 unused keys are noise and are left: removing them touches eight bundles for no behaviour, on the eve of a release.  What was worth doing is the other direction, which had no test at all - a key renamed in the bundle and not at its call site puts the raw key on screen.  Measured today: nothing in `src/` asks for a key the bundle does not have, apart from the five concatenation prefixes, and `testNothingAsksForAKeyThatIsNotThere` now keeps it that way.

Parsed all eight `.properties` files and cross-referenced every key (1865 in each, identical set - see
CMT-D1) against a full textual scan of every `.java`/`.form` file in `src/`. After excluding five
key-prefix families built by string concatenation rather than a literal (`route.kind.` +
`RouteCommand.CommandType.name()`; `autosetup.ui.side`/`autosetup.ui.facing` + a direction enum;
`autolayout.ui.pathPreference`/`autolayout.ui.tooltip.pathPreference` + a route-order enum - all verified
by grep of the actual concatenation sites), **230 keys have zero textual occurrence anywhere in `src/`**,
of which one (`autolayout.ui.errorAddEdge`) is referenced only from `test/`.

Of the remaining 229:

- **162 are explained** by five files this cycle deleted outright - `GraphViewer.java`,
  `GraphEdgeEdit.java`, `GraphLocExclude.java`, `GraphRightClickGeneralMenu.java`,
  `GraphRightClickPointMenu.java` (the old node/edge autonomy graph editor) and `RouteEditor.java` (the
  old route editor, replaced by `RouteEditorFrame.java`, which uses an entirely different key
  vocabulary - `route.ui.frameEditRoute` rather than `route.ui.routeName`, etc.). Verified by extracting
  every `I18n.t`/`I18n.f` key literal from each deleted file's last committed version
  (`git show 5f0a75e3:<path>`) and matching it against this list.
- **67 have no traceable deleted-file origin.** `ui.locDecoderType`, `ui.off`, `error.error` and
  `network.fatalError` look like long-standing orphans unrelated to this cycle. The rest cluster tightly
  around the newest, still-moving feature - the diagram autonomy editor - and several have a
  similarly-named key that IS in use, suggesting a rename left the old text behind rather than a feature
  never being built: `autosetup.ui.menuCanReverse`/`menuLinkHeading`/`menuRouteHeading`/`headingElsewhere`
  (the live equivalents are `hintCanReverse`, `menuStationHeading`, `menuArmsHeading`,
  `menuArrivalsHeading`, `menuDepartHeading`, `menuTurningHeading`); a whole family of home-locomotive
  strings (`autolayout.ui.menuHomeLocomotive`, `menuClearAllHomeLocomotives`,
  `confirmClearAllHomeLocomotives`, `errorSetHomeLocomotive`, `promptChooseHomeLocomotive`,
  `btnUseCurrentLocomotive`, `tooltip.HomeLocomotive`) that line up exactly with the deleted
  `HomeLocomotiveMenu` bulk-action code named in CMT-B3; a `ui.main.*` family (`showHomeLocomotives`,
  `hideReversingStations`, `hideReversingEdges`, `hideInactivePoints`, `showLengthsExclusions`,
  `tooltip.allLayouts`) matching the "Display options" rows the feature ledger marks `TODO`; and a
  `layout.ui.*`/`autosetup.ui.*` group (`tile`, `entireCol`, `entireRow`, `layerMonitoring`,
  `layerLabels`, `layerLocomotives`, `layerHomes`, `labelConfiguration`, `menuImportLegacy`,
  `errorAutonomyRunning`, `confirmJumpWithUnsavedEdits`) that reads like UI text prepared ahead of
  features not yet wired up.

None of this changes running behaviour - a dead resource key is inert - but it is real cleanup debt in
all eight bundles at once, and the home-locomotive cluster corroborates CMT-B3 from an independent
direction (dead code and dead doc both point at the same missing feature).

**Confidence:** confirmed by two independent Python parses (key extraction and full-source cross-
reference), plus `git show` against the pre-deletion versions of the six retired files, plus a targeted
grep confirming the five dynamic-concatenation exclusions are real and complete.

---

### CMT-C3 - `docs/reviews/2026-08-31-comments-and-docs-review.md`'s finding `CDR-B4` is now moot, but is still marked `open`

**FIXED 2026-09-03.**  `CDR-B4` is marked moot in its own document, with the ruling and the two commits that removed the rule it was about.

`CDR-B4` (yesterday's pass, at `e4c94ac9`) reported that `HomeStaging.whyNotAHome` and
`Layout.setHomeLocomotive` refused to let a split-square station be assigned as a home at all, and that
neither `Automation.md` nor `AutomationAPI.md` documented the refusal.

Later the same day, commits `7616d2a6` ("The home is the square, and direction is no part of it") and
`09777d4c` removed that refusal entirely, on Adam's ruling: *"the home should just be the logical point,
and the direction is wherever the locomotive was facing when it started moving."* `whyNotAHome`'s current
body (`HomeStaging.java:1268-1295`) no longer contains any split-square check, and the message key
`CDR-B4` named, `autolayout.errorHomeSquareIsSeveralPoints`, no longer exists in any bundle -
`grep -rn "errorHomeSquareIsSeveralPoints" src/` returns nothing; it survives only inside the historical
review documents that quote it. Since the rule `CDR-B4` was about no longer exists, the "doc gap" it
reported (neither guide documents the refusal) is moot along with it - there is nothing left to document.

Per `docs/reviews/README.md`'s own rule, a finding is closed when it is "fixed, withdrawn, or explicitly
declined" - not left open because a later document covers the same area. This one was overtaken by a
ruling made hours after it was filed, which is nobody's error, but the status table still reads `open`
and would send a future reader looking for a doc gap in a rule that no longer exists.

**Confidence:** confirmed by reading `HomeStaging.java`'s current `whyNotAHome`, the two commits that
changed it, and an exhaustive grep for the retired message key across `src/` and `docs/`.

---

### CMT-D1 - Message bundles: key-set parity, ASCII purity, no duplicate keys, no placeholder-count mismatches

Parsed all eight `.properties` files (`messages.properties` and `_da/_de/_es/_fr/_it/_nl/_pl`) in Python.
All eight hold exactly 1865 keys, and the key sets are identical - no key present in some bundles and
missing from others. No byte above `0x7F` in any of the eight files (matches `CDR-D4`, re-verified with a
byte-range scan rather than trusted from yesterday's result, since a bundle this large changes on nearly
every commit - `git diff --stat` shows 700+ line changes to each this cycle). No key is defined twice
within one file. Every `{n}`-style placeholder in `en`'s value for a key was compared against the same
key's value in all seven other languages; no count mismatch found.

**Confidence:** confirmed by full parse of all eight files; script and method described above.

---

### CMT-D2 - The orphaned-javadoc ratchet (`testJavadocsAreAttached`) is exactly accurate, including across every `automationui/*.java` file added this cycle

Independently re-implemented the test's own detector (a `/**...*/` block with only whitespace before the
next `/**...*/`) in Python and ran it over all of `src/`. Result: **93 total**, distributed across exactly
the 21 files the test's `ORPHANS_BY_FILE` pins, with exactly the same per-file counts - including all
seven `automationui/*.java` files the test names (`AutonomyBuilder.java` 6, `AutonomyChecks.java` 2,
`AutonomyCompanionStore.java` 4, `AutonomySession.java` 10, `GraphReducer.java` 2, `TileAnnotation.java`
3, `TileGraph.java` 1) and `Layout.java`'s 3. Neither `HomeStaging.java` nor `Point.java` carry any (both
absent from the pinned list, and the independent scan agrees).

Spot-checked one instance for content: `TileAnnotation.java:652-656`, a two-line stub javadoc ("Whether
this would paint anything at all") immediately followed by the real, detailed javadoc for the same method
(`isBlank()`, "Whether there is nothing here worth drawing" - OB-007's own explanation). This is exactly
the "harmless" case the test's own header describes - both blocks describe the same method accurately,
the first is simply superseded - not a case worth its own finding.

Since the working tree includes uncommitted and very recent changes (`TileAnnotation.java` among them,
per the fan-out brief), this confirms no new orphan has been introduced beyond what is already tracked and
ratcheted, in the files this pass was asked to prioritise.

**Confidence:** confirmed by independent re-implementation and full-`src/` run; the algorithm was
verified against the Java source's own `orphansIn` method line by line, with one acknowledged edge case
(a javadoc body containing the literal three characters `/**` would be walked slightly differently by the
two implementations) that does not occur in this codebase's actual comments.

---

### CMT-D3 - `docs/reference/README.md`'s inventory of kept-for-reference files matches disk exactly

Lists six files as kept: `GraphRightClickPointMenu.java.txt`, `GraphRightClickGeneralMenu.java.txt`,
`GraphEdgeEdit.java.txt`/`.form.txt`, `GraphLocExclude.java.txt`/`.form.txt`. `find docs/reference -type f`
returns exactly those six plus the README itself - no stray file, none missing. Its claim that
`GraphLocAssign` is "NOT here - it is still live" is confirmed: `GraphLocAssign.java`/`.form` still exist
in `src/org/traincontrol/gui/` uncompiled-away. Not a defect.

### CMT-D4 - `docs/plans/2026-08-01-diagram-autonomy-plan.md`'s Phase-2 status claim is accurate, and independently corroborates CMT-B3

Its status line claims `GraphViewer` and `GraphEdgeEdit` are gone from `src/` while `GraphLocAssign` is
still live - true on both counts (see CMT-D3). Its sibling ledger,
`docs/plans/autonomy-ui-feature-ledger.md` (dated 2026-08-24, not itself part of this task's named scope
but read while chasing CMT-B3), already and independently lists "Clear all home locomotives" as `TODO`
rather than done - agreeing with the code and disagreeing with `AutomationAPI.md`, which is the
substance of CMT-B3. Not a defect; cited here as corroboration.

---

## What this pass did not do

- **`Layout.java` (7920 lines) and `TrainControlUI.java` (25,855 lines) were not read end to end.**
  Every diff hunk since `e4c94ac9` was read in `Layout.java`, plus the surrounding whole methods, but a
  comment gone stale several rounds ago in an untouched part of either file would not surface here.
  `TrainControlUI.java` was not specifically in this pass's priority list and was not separately audited;
  yesterday's `CDR` pass covered it at `e4c94ac9`.
- **Ten of the seventeen `automationui/*.java` files got no more than a targeted read** -
  `AutonomyBuilder.java`, `AutonomyChecks.java`, `AutonomyCompanionStore.java`, `AutonomySession.java`
  (the four largest, several thousand lines each) were grepped for specific claims (home/terminus
  vocabulary, the orphaned-javadoc scan) rather than read cover to cover. `DiagramMonitor.java` (417
  lines) was read in full and no defect found. `TileAnnotation.java`, `TileGraph.java`, `TileOverlay.java`,
  `TilePorts.java`, `GraphReducer.java`, `StationIndex.java`, `LayoutPageEdit.java`,
  `AutonomyRefreshCallback.java` were sampled, not read whole.
- **`docs/manual-tests/tests.md`'s 240+ entries and `docs/manual-tests/issues.md` were not audited** -
  out of this task's named scope (`docs/reviews/README.md`, `Automation.md`, `AutomationAPI.md` and the
  code comments were).
- **Non-English bundle content (translation correctness) was not reviewed** - only the mechanical
  properties checked in CMT-D1.
- **CMT-B4's "to confirm" step was not executed**, per the hard constraint against running the
  application; it rests on reading `isPathClear`, `canRest`, `pickPath`, `hasAutonomousDestination` and
  `mayRestHere` against the two docs' text, which does not require execution to believe.
- **The 67 unexplained dead keys in CMT-C2 were grouped by inspection of their names**, not traced one by
  one to a specific commit that stopped using each - the home-locomotive cluster and the deleted-class
  matches are confirmed; the rest are a plausible but not individually verified grouping.
