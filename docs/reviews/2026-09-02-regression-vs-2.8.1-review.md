# Regression review: what a v2.8.1 user loses on the way to v3.0.0 - second pass

**Status:** open

**Prefix for citing these findings elsewhere:** `RG3`

**Reviewed:** branch `autonomy-diagram-r0` at **`cf048f9b`** (`RAW_VERSION = "3.0.0"`), on 2026-09-02.
The five commits of 2026-09-02 (`1cfdf370`, `87b6c10a`, `975f157d`, `8d1c17ca`, `cf048f9b`) are included.

**Baseline:** branch `master` at **`5f0a75e3`**, which is `RAW_VERSION = "2.8.1"` - the last shipped
version. Every comparison below uses `git diff master HEAD` (two dots) or `git show master:<path>`, for
the reason [`R28`](2026-09-01-regression-vs-2.8.1-review.md) gives: `master` carries seven commits the
3.0.0 line never had, and three-dot diffs hide them.

**Nothing was compiled, executed, or written.** No `ant`, no `javac`, no `java`, no TestNG, no
`battery.sh`, no `one.sh`. Every command run for this pass was `git`, `grep`, `sed`, `comm`, `wc` or a
short read-only `python` that parsed `test/operator_layout/config/autonomy_legacy/autonomy.json`.
Nothing under `cs2_sample_layout/` was read or written; Adam's real data was read from the frozen copy
at `test/operator_layout/`.

**One caveat about line numbers.** The working tree carries an uncommitted edit to `Readme.md` (library
version notes, and one autonomy changelog line) that is unrelated to everything below but shifts the
changelog by one line. **All `Readme.md` citations here are against `cf048f9b`**, and each of the three
sentences quoted is present, character for character, in the working tree as well.

**The question this pass asked.** The same one `R28` asked, at the same baseline: what could a 2.8.1
user do that they cannot do now, or what now happens to their data without them being told. Not whether
3.0.0 is good.

---

## What this pass did differently

`R28` found three of its five C findings with the sweep "key present in the bundle, referenced by no
`.java`". That sweep has a false-positive problem it did not correct for: **eight of the 199 keys it
flags were already orphans at 2.8.1**, so they say nothing about this release. This pass ran the sweep
at *both* revisions and subtracted, which gives the set that actually matters - **191 keys that were
live at 2.8.1 and are dead at HEAD** - and it also counted `.form` references, which `R28` did not. That
is `RG3-D2`, and it is what produced `RG3-C2`, `C4` and `C6`.

The sweep is blind in two directions, and both blind spots produced a finding of their own. It cannot
see a control whose keys were **deleted from the bundle** along with the code - that is `RG3-C1`, found
instead by diffing the 2.8.1 menu builders item by item against their successors. And it cannot see a
key that IS referenced, by a method with **no callers** - that is `RG3-C2`.

Where a claim rested on real data it was measured against `test/operator_layout/config/`, and twice the
measurement changed the outcome: `RG3-C1` is a C rather than a B because Adam has four placements, not
sixty-two, and the withdrawn finding in `RG3-D7` died on reading the code it was about.

| | |
|---|---|
| **A** | none - three candidates were raised and each resolved to a D, a B, or an already-open finding; the A section says which |
| **B1** | open - the v3.0.0 changelog tells the user the older text route editor is still there; it is deleted, and so is the tooltip that pointed at it |
| **B2** | open - the legacy import dialog counts what it brought in and never names the four things it left behind |
| **C1** | open - "Clear Locomotives" is gone; its sibling "Clear All Home Locomotives" was restored on 2026-09-02 and this one was not |
| **C2** | open - "Place Autonomy Station Label" has no door, no key and no signpost, and three places still say it does |
| **C3** | open - "Open Legacy Track Diagram Editor" is withdrawn, but the item is taken off the menu on only one of the two branches, and its handler now returns silently |
| **C4** | open - "Test Connection" between two points has no successor that works without a train standing on the square |
| **C5** | open - the changelog says the size controls add a row at the top and the bottom; `growEdges` adds one row, at the bottom |
| **C6** | open - four bundle keys in all eight languages describe an overlay-layer switch that was never built, including the replacement for `SHOW_HOME_LOCOMOTIVES` |
| **D1-D13** | verification of `R28`'s open findings, and checks that came back clean |

---

## A - high

**None.** Stated rather than omitted, because an empty A section is a claim and should be a checkable
one. Three candidates were raised during the pass and each resolved elsewhere:

- **A 2.8.1 route's bracketed conditions become uneditable.** Withdrawn on reading the load path - see
  `RG3-D7`, which records it with its original severity.
- **The legacy import destroys the operator's edge commands, lengths, timetable and route
  activations.** It does not: `autonomy.json` is left untouched and readable in every case
  (`AutonomySession.java:824`, `:841`, `:855`). What is wrong is that nobody is told, which is `RG3-B2`.
- **`migrateStationLabels` rewrites the user's own `.cs2` pages.** True, already filed as `RGN-B1` and
  still open; narrower at HEAD than as filed, which is `RG3-D11`. Not re-filed.

The one A-severity path this baseline had - `R28-A1`, a locomotive delete gutting every route that named
it - is closed by `FX2-5`.

---

## B - medium

### B1 - the changelog tells a 2.8.1 user the older route editor is still there, and it is deleted

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading; the deletion is in `git diff --diff-filter=D`, the sentence is in the shipped Readme |

The v3.0.0 changelog says, of the new route editor:

    Readme.md:387
        Capturing commands by working the railway still works exactly as before, and can now be
        pointed at the conditions instead ... The older text editor is still there, and routes that
        came from the Central Station open read-only in both.

There is no "both". `git diff master..HEAD --diff-filter=D --name-only -- src` lists

    src/org/traincontrol/gui/RouteEditor.form
    src/org/traincontrol/gui/RouteEditor.java

and there is exactly one route editor at HEAD. `TrainControlUI` holds one field and says so:

    src/org/traincontrol/gui/TrainControlUI.java:529-532
        * Still called routeEditor because that is what it is: the old text-based one it replaces has
        ...
        private RouteEditorFrame routeEditor;

Both doors construct the same class - `TrainControlUI.java:17634` (`new RouteEditorFrame(this,
routeName, currentRoute)`) and `:19270` (`new RouteEditorFrame(this, null, null)`). At 2.8.1 the two
editors were separate and the menu offered a choice; the two keys that offered it are still in all eight
bundles and referenced by nothing:

    src/org/traincontrol/resources/messages.properties:804-805
        route.ui.menuNewEditor=Edit a Route (New Editor)…
        route.ui.tooltipNewEditor=The same routes, edited by picking from lists instead of typing.
          A command this editor has no controls for, or a condition with brackets, is kept exactly as
          it is and shown read-only - use the older editor for those.

**Why this is more than a stale sentence.** The tooltip above is the shipped text of a promise: when the
new editor refuses to touch something, go and use the old one. That refusal still exists - `CommandRow.of`
returns null for a kind it has no controls for, and `RouteEditorFrame` keeps such a row read-only
(`RouteEditorFrame.java:47-51`) - and the changelog confirms the escape hatch is available. A user who
follows it will look for a menu item that does not exist, in a release whose headline route change is
that the editor was replaced.

**Severity.** B. No data is lost and no train moves wrongly; the user is told something about their
tools that is not true, in the one document they read to find out what changed. Same class as `RGN-B3`,
which is a B for the same reason.

**Not a request for more changelog entries.** The rule that users are not technical stands. This is one
sentence that is false and one that is (see `RG3-C5`); deleting the four words is the whole fix.

**How to confirm.** Read-only, above. `grep -rn "RouteEditor\b" src/ --include=*.java` returns two hits,
neither of them a class.

### B2 - the legacy import dialog counts what it brought in and never names what it left behind

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading, and measured against `test/operator_layout/config/autonomy_legacy/autonomy.json` |
| **Relation to open findings** | this is the *reporting* half of `RGN-A1` and `R28-B1`; it may be better merged into `RGN-A1` than fixed on its own, and that is Adam's call |

Importing a 2.8.1 `autonomy.json` ends in one dialog:

    src/org/traincontrol/gui/AutonomyViewerPanel.java:1160-1162
        JOptionPane.showMessageDialog(ui, I18n.f("autosetup.ui.infoLegacyImported",
            result.matched, result.placed, result.reversing, result.settings,
            result.skipped, result.unmatched.size()) + unmatched);

    src/org/traincontrol/resources/messages.properties:1593
        autosetup.ui.infoLegacyImported=Named {0} squares, placed {1} locomotives, marked {2} as
          turning trains round and carried {3} other settings.  {4} squares already had a name and were
          left alone.  {5} points could not be matched to a sensor on this diagram.

Six counts, all of them about what arrived. **Four categories are deliberately not imported and none of
them is mentioned.** Three are excluded in one loop, each with its reason in a comment:

    src/org/traincontrol/automationui/AutonomySession.java:824
        if ("points".equals(key) || "edges".equals(key)) continue;
    src/org/traincontrol/automationui/AutonomySession.java:841
        if ("activateRoutes".equals(key) || "activateRouteIDs".equals(key)) continue;
    src/org/traincontrol/automationui/AutonomySession.java:855
        if ("timetable".equals(key)) continue;

and the fourth - the per-edge accessory commands - falls out of the `"edges"` skip, because
`CARRIED_SETTINGS` is a list of point properties only:

    src/org/traincontrol/automationui/AutonomySession.java:516-517
        Arrays.asList("priority", "speedMultiplier", "excludedLocs", "active", "maxTrainLength");

**The code already says this is a gap.** The comment above the route-activation skip:

    src/org/traincontrol/automationui/AutonomySession.java:837-840
        // NOT REPORTED YET, and that is a gap worth naming rather than papering over: the
        // import dialog counts what it matched and what it skipped, and says nothing about
        // these.  A user who really did have route activations keeps them in autonomy.json,
        // which is untouched and readable, but nothing tells them to look.

**It is real data, measured.** Adam's own legacy file (`test/operator_layout/config/autonomy_legacy/autonomy.json`,
a frozen copy of `cs2_sample_layout`) holds 62 points and 90 edges, and:

| left behind | how much of Adam's file |
|---|---|
| per-edge accessory commands | **69 of 90 edges**, naming 65 distinct accessories of which **15 are signals** (`Signal 37/38/39/40`, `61-64`, `81`, `86`, `87`, `94`, `107`, `108`, `116`) |
| per-edge lengths | **30 of 90 edges** carry a non-zero one (all 90 carry the key) |
| the timetable | **36 entries** |
| route activations | `activateRoutes: true` with an empty `activateRouteIDs` |

The switches among those 65 accessories are re-derived from tile geometry by `GraphReducer.collectCommands`
and re-emitted by `AutonomyBuilder.java:1046-1063`, so they come back. The fifteen signals depended on a
human having said so, and there is no gesture at HEAD that says it - `grep -rn "addConfigCommand" src/`
returns four hits, none of them a user interface (`Edge.java:79`, `Layout.java:2742`, `Layout.java:7827`,
and `examples/FullAutonomyExample.java`). That is `R28-B1`, re-verified here at `cf048f9b`.

**Why the dialog matters more than the mechanism.** Every one of the four is defensible as a decision -
the reasons in the comments are good ones, and the file is left untouched and readable in every case.
What makes it a finding is that the operator is shown a success dialog with six counts and no way to
learn that four things stayed behind. The route activations are the sharp end: Adam's file would have
disabled every route he has, so *not* importing them is right, and he has no way to find out that the
question was ever asked.

**Severity.** B. Nothing is destroyed - `autonomy.json` is not deleted by the import - but the user's
belief about what they now have is wrong, on the one path every upgrading 2.8.1 user takes.

**How to confirm.** Read-only, above. The measurement is one script over
`test/operator_layout/config/autonomy_legacy/autonomy.json`; it needs no JVM.

---

## C - low

### C1 - "Clear Locomotives" is gone, and its sibling was restored today without it

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading; the count that sets the severity is measured against Adam's configuration |

At 2.8.1 the graph window's background menu had **two** bulk actions, one after the other:

    git show master:src/org/traincontrol/gui/GraphRightClickGeneralMenu.java:111
        menuItem = new JMenuItem(I18n.t("autolayout.ui.menuClearLocomotives"));
    git show master:src/org/traincontrol/gui/GraphRightClickGeneralMenu.java:121-122
        I18n.t("autolayout.ui.confirmClearLocomotives"),
        I18n.t("autolayout.ui.confirmDeletionTitle"),
    git show master:src/org/traincontrol/gui/GraphRightClickGeneralMenu.java:134-144
        List<Locomotive> locs = new ArrayList<>(ui.getModel().getAutoLayout().getLocomotivesToRun());
        for (Locomotive l : locs)
        {
            Point p = ui.getModel().getAutoLayout().getLocomotiveLocation(l);
            if (p != null && !p.isReversing() && p.isDestination())
            {
                ui.getModel().getAutoLayout().moveLocomotive(null, p.getName(), false);
                ...
    git show master:src/org/traincontrol/gui/GraphRightClickGeneralMenu.java:160-161
        HomeLocomotiveMenu.addClearAllItem(this, ui, (Component) parent.getSwingView(),
            ui::updateVisiblePoints);

The second of those is `R28-C1`, and it came back on 2026-09-02 in `1cfdf370`, beside Name Everything:

    src/org/traincontrol/gui/AutonomyEditorPanel.java:469-482
        // CLEARING EVERY HOME AT ONCE (the button itself is at :480), which 2.8.1 had and this release lost (R28-C1).
        //
        // Adam, 2026-09-02: **"that option should be added back in to the autonomy editor, with a
        // confirmation."** ...
        clearHomes = new JButton(I18n.t("autolayout.ui.menuClearAllHomeLocomotives"));
        clearHomes.addActionListener(e -> clearAllHomes());

The first did not. Both of its bundle keys were removed from all eight bundles rather than left orphaned
(`autolayout.ui.menuClearLocomotives` and `autolayout.ui.confirmClearLocomotives` are two of the
nineteen in `R28-D4`), so the "key present, referenced by nothing" sweep cannot see it - which is why
`R28` missed it while finding its neighbour. The only unplace at HEAD is per-square:

    src/org/traincontrol/gui/AutonomyEditorPanel.java:1006
        menu.add(item(I18n.f("autosetup.ui.menuRemoveLocomotive", standing), ...
    src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:473
        I18n.f("layout.ui.menuRemoveLocomotive", current.getCurrentLocomotive().getName())

**Severity, and why it is not a B.** `R28-C1`'s argument for its sibling was "on Adam's graph that is up
to 62" right-clicks. That number does not transfer. `test/operator_layout/config/autonomy/configuration-Main.json`
holds 71 points of which **4 carry a `loc`**, and the legacy file agrees (4 points with a standing
locomotive). Clearing them one at a time is four right-clicks, not sixty-two. C, and stated with the
measurement rather than by analogy.

**Note on the pattern.** This is the "fix one site, sweep the siblings" case from `docs/reviews/README.md`,
appearing in the fix for a finding rather than in the original code: the two items were adjacent in the
same menu builder, one was restored and the other was not.

### C2 - "Place Autonomy Station Label" has no door, no key and no signpost, and three places still say it does

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading; every one of the four sites was opened |

At 2.8.1 there were two doors to the same method:

    git show master:src/org/traincontrol/gui/LayoutEditorRightclickMenu.java:296-308
        menuItem = new JMenuItem(I18n.t("layout.ui.placeAutoStationLabel"));
        menuItem.addActionListener(event -> { ... edit.editTextWithDropdown(label); ... });
        menuItem.setToolTipText("Control+S");
    git show master:src/org/traincontrol/gui/LayoutEditor.java:1703-1706
        else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_S)
        {
            this.editTextWithDropdown(getLastHoveredLabel());
        }

At HEAD both are gone. The menu builder has no such item (`grep -n 'I18n.t(' LayoutEditorRightclickMenu.java`
runs from `layout.ui.editTextLabel` at `:355` straight to `ui.delete` at `:380`), and the key handler -
`LayoutEditor.java:6475-6657` - has no `VK_S` branch at all: its ladder is `V, X, C, R, T, A, I, Z, Y,
DELETE, ESCAPE`.

**The capability genuinely moved**, and the method was kept on purpose to say so:

    src/org/traincontrol/gui/LayoutEditor.java:3522-3538
        public void editTextWithDropdown(LayoutLabel label)
        {
            // Station captions are not text on the diagram any more.
            ...
            // A caption now belongs to the autonomy setup and points at the sensor SQUARE, so it is set
            // where the rest of autonomy is set.  Said plainly rather than removed from the menu, because
            // somebody who used to do it here needs to be told where it went.
            JOptionPane.showMessageDialog(this,
                I18n.t("layout.ui.infoStationLabelsMovedToAutonomy"));
        }

**"Said plainly rather than removed from the menu" is exactly what did not happen.** `grep -rn
"editTextWithDropdown" src/ test/` returns one hit at HEAD - the declaration above - against three at
2.8.1. The message is unreachable, and its bundle key is carried in all eight languages
(`messages.properties:1080`) for nobody.

**And the shipped documentation still promises the key:**

    Readme.md:242
        * Control+S (place autonomy station label)

That line is untouched by `git diff master..HEAD -- Readme.md`, which removed the Shift+R / Shift+C
entries and the whole Autonomy Graph UI block correctly (see `RG3-D13`) and left this one.

**Severity.** C. The capability exists - captions are set from the autonomy editor
(`autosetup.ui.menuShowStationHere`, `AutonomyEditorPanel.java`), and the new binding is better than the
old one because it points at a square rather than at a name. What is lost is every route a 2.8.1 user
knows to it, plus the sentence written to redirect them. Three sites disagree with the code: the
Readme, the method's own comment, and the message that was kept to explain the move.

**How to confirm.** Read-only. `grep -rn "editTextWithDropdown" src/ test/` and
`sed -n '6475,6657p' src/org/traincontrol/gui/LayoutEditor.java`.

### C3 - the legacy track diagram editor is removed on only one of the two branches, and now returns silently

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading; the reachability half is bounded by two predicates I could not execute, and the entry says so |

A 2.8.1 Windows user could open their diagram in the Märklin editor: TrainControl unpacked a bundled
executable into the layout folder and ran it. The resource is deleted -
`src/org/traincontrol/gui/resources/TrackDiagramEditor.zip` is in
`git diff master..HEAD --diff-filter=D` - and the handler now refuses before doing anything:

    src/org/traincontrol/gui/TrainControlUI.java:22051-22062
        private void openLegacyTrackDiagramEditorActionPerformed(java.awt.event.ActionEvent evt) {
            // WITHDRAWN.  The menu item is taken off the menu in the constructor and the executable it
            // unpacked is no longer shipped, so nothing can reach this - see removeLegacyEditorItem.
            ...
            if (true) return;

**"Taken off the menu in the constructor" is not where it happens.** The item is still built and added
by `initComponents` (`TrainControlUI.java:15049-15055`), and the removal lives in a method called from
one place:

    src/org/traincontrol/gui/TrainControlUI.java:3215-3222
        private void removeLegacyEditorItem()
        {
            if (modifyLocalLayoutMenu != null && openLegacyTrackDiagramEditor != null)
            {
                modifyLocalLayoutMenu.remove(openLegacyTrackDiagramEditor);
            }
        }

    src/org/traincontrol/gui/TrainControlUI.java:3411
        removeLegacyEditorItem();

`:3411` sits inside `mountAutonomyControls()`, **after** the early return for a layout with no local
folder:

    src/org/traincontrol/gui/TrainControlUI.java:3378-3398
        if (session == null)
        {
            ... this.exportJSON.setVisible(true); ...
            return;
        }

So on the `session == null` path the item is never removed. Two other guards narrow what that costs:
`openLegacyTrackDiagramEditor.setVisible(false)` on a non-Windows machine
(`TrainControlUI.java:7019-7022`), and the whole submenu is greyed unless a *local* layout is loaded:

    src/org/traincontrol/gui/TrainControlUI.java:4150-4155
        boolean live = this.noEditorOpen && layoutCanBeEdited();
        ...
        if (this.modifyLocalLayoutMenu != null) this.modifyLocalLayoutMenu.setEnabled(live);
    src/org/traincontrol/gui/TrainControlUI.java:4185-4188
        private boolean layoutCanBeEdited()
        {
            return isLayoutLoaded() && isLocalLayout();
        }

`layoutCanBeEdited()` and `AutonomyCompanionStore.isUsable()` ask nearly the same question, so on the
ordinary paths the item is removed exactly when it could be enabled. **Nearly, not exactly**: they are
two predicates, not one, and the gap between "a local layout is loaded" and "the layout folder is a
readable directory" is where an enabled item with a `if (true) return;` handler lives - a menu entry
that answers a click with nothing at all.

**Severity.** C. It is a capability a 2.8.1 Windows user had and does not have, the replacement is the
built-in editor on every platform, and the reachable-and-enabled case is narrow and could not be
demonstrated by reading. Filed as a C rather than a B for that reason, and with the unreachability
stated as the belief it is. The changelog does not mention the removal; the Readme's Requirements
section correctly dropped the GraphStream libraries but says nothing about this.

**How to confirm.** Needs execution: on Windows, start with a Central Station layout, look at Layout ->
Manage Pages before ever loading a local layout. Reading settles the removal path but not whether the
submenu can be live at that moment.

### C4 - "Test Connection" has no successor that works without a train on the square

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading both tools |

At 2.8.1 a point's right-click menu could ask about a *pair of points*, with no train involved:

    git show master:src/org/traincontrol/gui/GraphRightClickPointMenu.java:881
        I18n.t("autolayout.ui.testconnection")
    git show master:src/org/traincontrol/gui/GraphRightClickPointMenu.java:939-977
        sb.append(I18n.t("autolayout.ui.labelValidPaths")).append("\n");
        ...
        sb.append("\n").append(I18n.t("autolayout.ui.labelInvalidPaths")).append("\n");
        ...
        .append(I18n.t("autolayout.ui.labelReason"))

Six of its keys are among the 191 that went dead: `autolayout.ui.testconnection`,
`dialogPathDebugResults`, `labelValidPaths`, `labelInvalidPaths`, `labelReason`, `labelNone`. None is
referenced by any `.java` or `.form` at HEAD.

**There is a successor and it asks a different question.** The Why tool draws every route a train could
take and lists the refusals:

    src/org/traincontrol/resources/messages.properties:1879
        autosetup.ui.tooltipWhy=Click a square with a train on it.  Says why that train is not being
          sent anywhere, and draws every route it could take.
    src/org/traincontrol/gui/AutonomyEditorPanel.java:5264
        say(hint, I18n.t("autosetup.ui.whyNoTrainHere"));

and the per-locomotive report in the run list does the same from the other end
(`AutoLocomotiveStatus.java:692` `whyNotReport()`, keys `autolayout.ui.whyTitle` and the
`autolayout.why.*` family at `messages.properties:1867-1892`). Both are richer than what 2.8.1 had -
they name the refusal per station - and **both start from a locomotive**. "Is there a legal path from
this station to that one" is a question about the graph, and it can no longer be asked while the railway
is empty, which is when somebody building a setup asks it.

**Severity.** C. It is a diagnostic, not an operation; there is a near-successor; and `AutonomyChecks`
now reports several classes of unreachability the old tool would only have shown when asked
(`autosetup.ui.checkCopyNoWayIn`, `messages.properties:1535`).

### C5 - the changelog says the size controls add a row at the top and the bottom; they add one row, at the bottom

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading; the code comment and the Readme's own keyboard section both contradict the changelog |

    Readme.md:393
        The editor now has a matching pair of size controls: one adds a column on the right and a row
        at the top and bottom, the other takes the same three away.  Shrinking is refused if any of
        those edges still holds track.

Two edges, not three, and the code says so twice. The menu builder:

    src/org/traincontrol/gui/LayoutEditorRightclickMenu.java:405-410
        // "+" adds a column on the right and a row at the bottom; "-" takes the same two away.  Being
        // exact mirrors is the point ...
        // NOT a row at the top, which is what was asked for.  Inserting one moves every tile down, and
        // everything autonomy knows about a page is keyed by SQUARE - see LayoutEditor.growEdges.

and the method:

    src/org/traincontrol/gui/LayoutEditor.java:4520, :4533
        public void growEdges()
        ...
            layout.addRowsAndColumns(1, 1);

The Readme's own keyboard section agrees with the code and disagrees with the changelog:

    Readme.md:241
        * Control+I (increase diagram by 1 row and 1 column)

**Severity.** C. Cosmetic in effect - a user who presses it sees what it does - but it is a factual
claim about a headline feature in the release notes, and the decision *not* to insert at the top was
deliberate and is worth not describing away.

### C6 - four bundle keys describe an overlay-layer switch that was never built

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading; sharpens `R28-C3` rather than replacing it |

    src/org/traincontrol/resources/messages.properties:1571-1574
        autosetup.ui.layerMonitoring=Light up track as trains run
        autosetup.ui.layerLabels=Show station names
        autosetup.ui.layerLocomotives=Show where locomotives are
        autosetup.ui.layerHomes=Show where locomotives belong

All four are in all eight bundles and are referenced by no `.java` and no `.form`. What was built is one
checkbox:

    src/org/traincontrol/gui/AutonomyOverlayToggle.java:9-10, 25
        * The one autonomy control the track diagram tab has: whether the overlay is drawn.
        * Deliberately a single checkbox.
        private final JCheckBox show = new JCheckBox(I18n.t("autosetup.ui.chkShowAutonomy"), true);

**Why this matters for the regression question.** `autosetup.ui.layerHomes` - "Show where locomotives
belong" - is the exact 3.0.0 restatement of the 2.8.1 preference `SHOW_HOME_LOCOMOTIVES`, which drew the
home locomotive's name on the graph:

    git show master:src/org/traincontrol/gui/TrainControlUI.java:15136
        if (p.getHomeLoc() != null && prefs.getBoolean(SHOW_HOME_LOCOMOTIVES, true))

`R28-C3` filed the dead constant and left "is the home locomotive named anywhere on the diagram at
HEAD?" as its open question 4. **The answer is no**, and this pass settled it by search rather than by
reading the drawing in full: `grep -rn "[Hh]ome" src/org/traincontrol/gui/LayoutGrid.java
src/org/traincontrol/gui/LayoutLabel.java src/org/traincontrol/automationui/TileOverlay.java` returns
nothing. Home assignments are visible in the autonomy *editor* (`autosetup.ui.menuHomeFor`,
`autosetup.ui.labelHomeFor`) and as a per-locomotive badge in the run list
(`AutoLocomotiveStatus.java:240`), and nowhere on the running diagram.

**Severity.** C, the same as `R28-C3` and for the same reason: the assignment is still visible somewhere
and still settable. Filed separately because the four strings are evidence that the successor was
designed and not wired, which is a different fact from "the constant is dead".

---

## D - not defects

### D1 - `R28`'s five open findings, re-checked at `cf048f9b`

Each was re-derived from the current tree rather than taken on trust. **I agree with all five.**

| `R28` | verdict | evidence at `cf048f9b` |
|---|---|---|
| **B1** legacy edge commands dropped and not re-authorable | **agree, unchanged** | `AutonomySession.java:824` still skips `edges`; `CARRIED_SETTINGS` (`:516-517`) is point properties only; `grep -rn "addConfigCommand" src/` returns four hits, none a UI. Measured: 69 of Adam's 90 edges, 15 distinct signals. See `RG3-B2` for the reporting half. |
| **C2** "Copy Outgoing Edge" gone with no equivalent | **agree** | `autolayout.ui.menuCopyOutgoingEdge` referenced by nothing; the HEAD connections submenu offers `menuUseLink`, `menuPairLink`, `menuGoToLinkPartner`, `menuUnpairLink`, `menuSetName`, `menuSetLength`, `menuBranch`, `menuAllBranches`, `menuTurnNever/May/Must`, `menuRouteBoth/None/Toward` - nothing that copies one connection's settings onto another. |
| **C3** two dead preference constants, home no longer drawn | **agree, and settled** | `TrainControlUI.java:245-248` are declaration-only; the "home no longer drawn" half is confirmed by search - see `RG3-C6`, which also answers `R28`'s open question 4. |
| **C4** `route.ui.errorUnusableLocName` orphaned | **agree, and it is the only one of its kind** | I extracted the keys added on the 2.8.1 release line (`git show 8cee6a3c:` against `git show master:`): exactly two, `loc.ui.errorLocomotiveNameUnusable` and `route.ui.errorUnusableLocName`. The first is live (`TrainControlUI.java:16520`, `:22205`); the second is the orphan. No key added for 2.8.1 was dropped from the bundle. |
| **C5** graph-window keyboard routes now mouse-only | **agree, and it is five keys not two** | `git show master:.../GraphViewer.java:573-620`: Ctrl+E (exclude), Ctrl+U (unexclude), Ctrl+S (s88), Ctrl+H (home), plus the Ctrl+V/Ctrl+X/Delete set `R28-D2` correctly excluded. The Readme's Autonomy Graph UI block was deleted with them (`RG3-D13`), so this is documented rather than silent. |

`R28-A1` (`CLOSED by FX2-5`) and `R28-C1` (`FIXED 1cfdf370`) were read and not re-derived beyond
`RG3-C1`, which is about `C1`'s sibling rather than about `C1`. `R28-B2` is withdrawn and was not
revisited.

### D2 - the bundle sweep, done at both revisions

`R28-D4` says the "key in the bundle, referenced by no `.java`" sweep is the stronger one and that it
produced three of its five C findings. It has a false-positive class it did not correct for, and this
pass ran it symmetrically.

| | keys | referenced | orphans |
|---|---|---|---|
| 2.8.1 (`.java` + `.form`) | 1,237 | 1,228 | **9** |
| HEAD (`.java` + `.form`) | 1,874 | - | **278** |

Subtracting gives **191 keys live at 2.8.1 and dead at HEAD**, which is the set that says something
about this release. Eight of `R28`'s 199 were already orphans in 2.8.1 and carry no information about
this release: `layout.accessory`, `layout.switch`, `layout.switchThreeWay`,
`layout.ui.autonomyStationPrefix`, `network.fatalError`, `ui.locDecoderType`, `ui.main.allLayout`,
`ui.main.tooltip.allLayouts`. `ui.main.allLayout` is the clearest - a singular typo for the live
`ui.main.allLayouts`, dead in both revisions (`git show master:.../TrainControlUI.java:9093` uses the
plural; HEAD `:15078` still does). A ninth 2.8.1 orphan, `ui.main.graphUIOptions`, was deleted from the
bundle outright and so is not in either set.

By prefix: `autolayout.ui` 95 and `autolayout.info` 2 (the deleted graph window), `route.ui` 72 (the
deleted `RouteEditor`), `layout.ui` 9, `ui.main` 8, and five generic (`error.error`, `ui.on`, `ui.off`,
`ui.test`, `ui.highlight`). **The nine `layout.ui` and eight `ui.main` are where the findings were**: the
`ui.main` eight are the five dead preferences' menu items and their tooltips, and the `layout.ui` nine
gave `RG3-C2` (`placeAutoStationLabel`, `dialogSelectStation`, `errorNoStationsInGraph`,
`labelAutonomyStationInfo`, `errorAddStationComponent`) and `RG3-D10` (`entireRow`, `entireCol`,
`tile`). The ninth, `layout.ui.tooltipCentralStationRoutesCannotBeEditedDuplicateInstead`, is the old
route editor's locked-route tooltip and has a live successor in `route.ui.frameLockedExplains`.

Of the 79 keys new in 3.0.0 that the sweep flags, most are dynamically composed and are false positives
(`"autolayout.ui.pathPreference" + pref.name()`, `"route.kind." + kind.name()` at `CommandRow.java:153`,
`"autosetup.ui.facing" + side`). The sweep also **cannot see** a key referenced only from a method with
no callers, which is how `RG3-C2` had to be found instead by diffing the menu builders - the lesson
`R28-D4` states about `C1`/`C2`/`C3`, one layer further in.

### D3 - the locomotive menus were re-homed in full

`RightClickSelectorMenu` and `RightClickMenuListener` each lost the same eight keys between the
revisions, which reads as a removal and is not. Every one is in the new shared builder:

    grep -rl '"loc.ui.menuDeleteFromDatabase"' src/ --include=*.java
        src/org/traincontrol/gui/LocomotiveMenuItems.java

and the same for `menuEditNameAddressDecoder`, `menuEditNotes`, `menuFindSimilarLocomotives`,
`menuSetLocalLocomotiveIcon`, `menuClearLocalLocomotiveIcon`, `menuCustomizeFunctionIcons` and
`tooltip.findSimilarHint`. `RightClickFunctionMenu` gained two (`menuAutonomyArrivalFunction`,
`menuAutonomyDepartureFunction`) and lost none; `RightClickRouteMenu` is key-identical; `RightClickTimetableMenu`
gained one guard and lost nothing.

### D4 - every advanced point parameter is still authorable, and the runtime still honours it

The 2.8.1 point menu's Advanced Parameters submenu looked like a large loss - its labels are among the
191. Each was traced to `AutonomyEditorPanel`:

| 2.8.1 key | at HEAD |
|---|---|
| `menuEditAdvancedParameters` | `AutonomyEditorPanel.java` |
| `promptEnterMaxTrainLength`, `errorInvalidTrainLength` | `AutonomyEditorPanel.java:1158-1162`, `number(target, "maxTrainLength", 0)` |
| `promptEnterStationPriority`, `errorInvalidPriority` | `AutonomyEditorPanel.java` |
| `menuSpeedMultiplier`, `promptEnterSpeedMultiplier`, `errorInvalidSpeedMultiplier` | `AutonomyEditorPanel.java` |
| `menuExcludedLocomotives` | `AutonomyEditorPanel.java:1339-1340`, `promptLocomotives(target, "excludedLocs", allLocomotives())` |
| `markAsStation`, `checkboxMarkTerminusStation`, `checkboxMarkReversingPoint`, `checkboxActive` | `autosetup.ui.menuCanStop` / `menuTurnMust` / `menuTurnMay` / the out-of-service square |

**The excluded-locomotive case is the one worth writing out**, because at 2.8.1 the only UI setter in the
tree was `GraphRightClickPointMenu.java:314` (`p.setExcludedLocs(edit.getSelectedExcludeLocs())`) and
`GraphLocExclude.java` was deleted. The rule is still enforced at every site it was -
`Layout.java:2206`, `:3526`, `:3784`, `:4056`, `HomeStaging.java:645`, `:1239`, `:1675`,
`AutoLocomotiveStatus.java:139` - it is carried across the legacy import
(`AutonomySession.java:517`, `excludedLocs` is in `CARRIED_SETTINGS`), and it is settable again from the
editor. Four of Adam's 62 points carry exclusions; all four survive.

### D5 - the preference sweep: five dead constants, all of them already named

Extracted every `prefs.get*`/`prefs.put*` key at both revisions. Present at 2.8.1 and read nowhere at
HEAD:

    src/org/traincontrol/gui/TrainControlUI.java:230   HIDE_REVERSING_PREF     = "HideReversing"
    src/org/traincontrol/gui/TrainControlUI.java:245   HIDE_INACTIVE_PREF      = "HideInactive"
    src/org/traincontrol/gui/TrainControlUI.java:246   HIDE_REVERSING_EDGES_PREF = "HideReversingEdges"
    src/org/traincontrol/gui/TrainControlUI.java:247   SHOW_STATION_LENGTH     = "ShowStationLength"
    src/org/traincontrol/gui/TrainControlUI.java:248   SHOW_HOME_LOCOMOTIVES   = "ShowHomeLocomotives"

Each is the only occurrence of its name in the tree. Three are `RGN-D8`, two are `R28-C3`; **there is no
sixth.** Everything else round-trips: `AUTOSAVE_SETTING_PREF`, `MENUBAR_SETTING_PREF`,
`ONTOP_SETTING_PREF`, `SLIDER_SETTING_PREF`, `TABS_SETTING_PREF`, `ROUTE_SORT_PREF`,
`SHOW_KEYBOARD_HINTS_PREF`, `PREFERRED_KEYBOARD_MM2`, `IP_PREF`, `LAYOUT_OVERRIDE_PATH_PREF`,
`LAYOUT_SHOW_ADDRESSES`, `LAYOUT_TITLES_PREF`, `LAST_USED_FOLDER`, `LAST_USED_ICON_FOLDER`,
`ACTIVE_LOC_IN_TITLE`, `AUTO_POWER_ON`, `AUTO_LOAD_AUTONOMY`, `CHECK_FOR_UPDATES`,
`ENHANCED_PATH_VALIDATION`, `REMEMBER_WINDOW_LOCATION`, and the `PositionAwareJFrame` window-geometry
suffixes. Seven keys are new (`CROP_LOC_ICON_PREF`, `DIAGRAM_RESTRICTION_ARROWS`,
`LAST_EDITOR_AUTONOMY_PREF`, `LOC_MAPPING_PAGES_PREF`, `SHOW_INACTIVE_LABELS_PREF`,
`STATION_LABELS_GREY`, and the `legacy` sub-node), and a new key cannot orphan an old one.

### D6 - all eight bundles agree, and are pure ASCII

`R28` listed this under "what this pass did not look at". It is clean:

| | da | de | es | fr | it | nl | pl |
|---|---|---|---|---|---|---|---|
| keys | 1,874 | 1,874 | 1,874 | 1,874 | 1,874 | 1,874 | 1,874 |
| missing vs `messages.properties` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| extra | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

and `grep -c '[^ -~\t]'` is 0 on all eight, so nothing depends on Java 8 reading a non-ASCII properties
file. No user of any language sees a raw key.

### D7 - WITHDRAWN, originally B: "a route with bracketed conditions can no longer be edited"

This was raised, drafted as a B, and is wrong. The two documents that led me there both say so:

    src/org/traincontrol/gui/RouteEditorFrame.java:48-50
        WHAT IT REFUSES TO TOUCH ... A command of a kind with no controls yet, and a condition with a
        real bracket in it, are kept exactly as found and shown read-only.
    src/org/traincontrol/resources/messages.properties:805
        route.ui.tooltipNewEditor=... a condition with brackets, is kept exactly as it is and shown
        read-only - use the older editor for those.

Both describe a version of the editor that no longer exists. Opening the load path settles it:

    src/org/traincontrol/gui/RouteEditorFrame.java:1096-1105
        conditionsAsFound = route.getConditions();
        // A bracketed condition used to arrive here as "rows cannot say this" - the table was
        // disabled, the expression printed underneath, capture refused, and the whole thing written
        // back untouched on save.  An outline can say it, so it is editable for the first time.
        conditions.rows.addAll(ConditionOutline.of(conditionsAsFound));

`conditionsEditable` is set false in exactly one place, and it is not about brackets:

    src/org/traincontrol/gui/RouteEditorFrame.java:2009
        conditionsEditable = false;

which sits inside the block that greys a route belonging to the Central Station (`:1990`,
`setTitle(I18n.f("route.ui.frameLockedTitle", ...))`) - the same read-only rule 2.8.1 had
(`layout.ui.tooltipCentralStationRoutesCannotBeEditedDuplicateInstead`, `master:RouteEditor.java:172`).
`ConditionOutline` expresses grouping by indentation and round-trips it (`of` at `:272`, `toExpression`
at `:182`), and its `toExpression` comment records a real defect already found and fixed in exactly this
area - an outline beginning with a bracketed group silently losing everything after the first joiner.

**Why the mistake is worth recording.** The finding came from believing a class javadoc and a shipped
tooltip over the method body, which is the failure `docs/reviews/README.md` names first. Both texts were
true when written; the editor got better and neither was updated. The stale tooltip is not harmless -
it is half of `RG3-B1`.

### D8 - the s88 trigger types are a relabel, not a loss

2.8.1's route editor offered four strings that read like four trigger types - `route.ui.occupied1`,
`clear0`, `occupiedThenClear`, `clearThenOccupied` - and HEAD offers two, `route.ui.triggerArrives` and
`triggerLeaves`. The enum has two values at both revisions:

    src/org/traincontrol/base/Route.java:17
        public static enum s88Triggers {CLEAR_THEN_OCCUPIED, OCCUPIED_THEN_CLEAR};

and the mapping is one to one:

    src/org/traincontrol/gui/RouteEditorFrame.java:559-560, 574-575
        return trigger == Route.s88Triggers.OCCUPIED_THEN_CLEAR
            ? I18n.t("route.ui.triggerLeaves") : I18n.t("route.ui.triggerArrives");
        ...
        return I18n.t("route.ui.triggerLeaves").equals(label)
            ? Route.s88Triggers.OCCUPIED_THEN_CLEAR : Route.s88Triggers.CLEAR_THEN_OCCUPIED;

The other two were the *condition* state - whether a sensor must read occupied or clear - which is now a
Setting cell on a `FEEDBACK` row. Nothing narrowed. Defaults are unchanged on both sides
(`MarklinRoute.java:97`, `:124`, `:1129`).

### D9 - every route command kind has a row, including all five "special commands"

2.8.1's `route.ui.addSpecialCommand` / `dialogSpecialRouteCommands` opened a dialog for the commands
with no address. All five are ordinary rows now: `CommandRow.of` maps `isStop`, `isFunctionsOff`,
`isLightsOn`, `isAutonomyLightsOn`, `isRoute` and `isAutoLocomotive` (`CommandRow.java:369-381`), the
kind names come from the bundle by composition (`CommandRow.java:153`,
`I18n.t("route.kind." + kind.name())`, thirteen `route.kind.*` keys present in all eight bundles), and
the shape questions - `hasTarget`, `hasSetting`, `hasProtocol`, `hasDelay`, `canBeACommand`,
`canBeACondition` - are answered per kind at `:209-324`.

### D10 - paste row and paste column: removed deliberately, with a replacement and a changelog line

Unlike `RG3-C2`, this one was done properly at all three sites. The code says why:

    src/org/traincontrol/gui/LayoutEditorRightclickMenu.java:35-40
        // There used to be three of these - a tile, an entire row, an entire column - which is not
        // three ways to paste so much as three answers to a question the user was never asked.  The
        // row and column variants filled from the pasted tile to the edge of the diagram, so a
        // mis-aimed one wrote over a whole row of track and undo was the only way back.

the replacement is present and reachable - `layout.ui.menuSelectRow`, `menuSelectColumn`,
`menuSelectAll`, `menuFillSelection` at `:81-131`, plus a top-level way in
(`menuSelectByDragging`, `:164`) - the changelog carries a line (`Readme.md:394`), and the Readme's
keyboard section dropped `Shift+R` / `Shift+C`. Read and cleared.

### D11 - `RGN-B1` is narrower at HEAD than as filed

`RGN-B1` (still open) says `Point:` captions are stripped out of the user's `.cs2` files. At `cf048f9b`
that is true only of labels naming a station the setup already knows:

    src/org/traincontrol/automationui/AutonomySession.java:1826-1836
        // A label naming a station this setup has never heard of is left exactly where it is:
        // stripping it would delete the only record that it ever existed ...
        if (tileNamed(was.substring(STATION_LABEL_PREFIX.length())) == null) continue;    // :1831
        ...
        component.setLabel("");                                                           // :1836

and the migration does not run at all until something has been matched (`if (!migrated) return
failures;`, `:1802`), so a 2.8.1 folder opened before any import is not written to. The rewrite still
happens without notice - only failures are surfaced (`TrainControlUI.java:2591-2593`, from
`session.getMigrationFailures()`) - so `RGN-B1` stands. Recorded here so that whoever fixes it is
working from the current behaviour rather than the August one.

### D12 - `Util.backupFolder` has no caller; the archive covers the layout folder anyway

    src/org/traincontrol/util/Util.java:349-353
        * Added because the backup did not cover the track diagrams or the autonomy setup ...
    src/org/traincontrol/util/Util.java:365
        public static List<String> backupFolder(File source, String intoName)

`grep -rn "backupFolder" src/ --include=*.java` returns the declaration and nothing else. The job it was
written for is done by the archive path instead:

    src/org/traincontrol/gui/TrainControlUI.java:18505-18508
        state.put((named == null || named.isEmpty() ? "layout" : named) + "/config",
            new File(layoutFolder, "config"));

so this is dead code new in 3.0.0, not a regression against 2.8.1. Noted rather than filed, because the
scope of this pass is what a 2.8.1 user lost.

### D13 - the Readme's keyboard section was updated correctly, with one exception

`git diff master..HEAD -- Readme.md` removes the whole Autonomy Graph UI block (Ctrl+V, Delete, Ctrl+X,
Ctrl+E, Ctrl+U, Ctrl+S, Ctrl+H) and the `Shift+R` / `Shift+C` lines from the Layout editor block. That
is seven of `R28-C5`'s keys and both of `RG3-D10`'s, documented rather than silently dropped, and it is
the reason `R28-C5` is a C. The exception is `Readme.md:242`, `Control+S (place autonomy station label)`
- see `RG3-C2`.

---

## Open questions - things I could not settle by reading

1. **`RG3-C3`'s reachability.** Can `modifyLocalLayoutMenu` be enabled at a moment when
   `mountAutonomyControls` has not yet taken the legacy-editor item off it? `layoutCanBeEdited()` and
   `AutonomyCompanionStore.isUsable()` ask nearly the same question and I could not find a state where
   they disagree by reading. One run on Windows with a Central Station layout answers it.

2. **`RG3-C1`, on somebody else's railway.** Four placements makes it a C on Adam's layout. If a user
   with thirty trains upgrades, the same finding is a B for them. Worth one sentence from Adam on which
   number the severity should follow.

3. **`RG3-B2`'s shape.** Whether the four categories should be named in the import dialog, or the
   finding folded into `RGN-A1` and fixed when the edge lengths are. I have no view worth more than his.

## What this pass did not look at

- **Autonomy runtime parity.** Whether a train picks the same route and holds the same locks as at
  2.8.1. `RGN` and `R28` both left this gap and so do I; it needs a jar and a layout.
- **`HomeStaging`** (1,001 changed lines) and the timetable executor, beyond confirming that the
  settings that drive them round-trip and that `getExcludedLocs` is still consulted at all three sites.
- **`LayoutGrid` / `LayoutLabel` drawing** (2,170 changed lines), read only far enough to settle
  `R28`'s open question 4 - by searching for `getHomeLoc` and `home`, not by reading the paint code.
- **`CS2File`** (663 changed lines) and `MarklinControlStation` (679). I checked what the earlier passes
  checked and did not re-derive the file formats; `R28-D6` (`sanitizeFilename`) and `R28-D12` (route
  import/export) were taken on trust.
- **The five commits of 2026-09-02**, which the briefing calls the least-reviewed code. I read
  `1cfdf370`'s `clearAllHomes` restoration closely enough to find `RG3-C1`, and did not audit the other
  four against this baseline - they are fixes to 3.0.0-only code and mostly cannot regress 2.8.1.
- **Nothing was executed**, so every "confirmed by reading" is a claim about what the code says.
