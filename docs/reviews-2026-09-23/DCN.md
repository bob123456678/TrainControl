# Documentation review since v2.8.1

**Status:** open

**Open at close:** DCN-C3 - carried in the finding store.

**Prefix:** `DCN`

**Reviewed:** branch `autonomy-diagram-r0` at `281c79de`, 2026-09-23.  Baseline: `master` at `5f0a75e3` (v2.8.1), two-dot diffs.

**Method:** Reading, `grep`, `git log -S` / `git show` and read-only Python over the fixtures and `triage.db`; nothing was built or run. Sampled by risk rather than read end to end - the corpus is ~32,000 lines of markdown. Read whole: `docs/reviews/README.md`, `Automation.md`, `docs/UI-standards.md`, the three fixture READMEs, the v3.0.0 changelog diff and the Readme's shortcut and feature lists. Read in part: `behaviour.md` section 1a, 5b, 5c (the OB-278 paragraphs), 5d, 5e, the keyboard-doors section, 6, 7a-7c; `open-questions.md` Part 1; `AutomationAPI.md` from "Prettifying" to the end plus its intro; `docs/manual-tests/README.md` rules; `test/README.md`. Mechanical sweeps: every backticked test name in the reference docs and READMEs against `test/` (141); every backticked identifier in behaviour.md / open-questions.md / viewer-editability.md against `src/`; every method name and JSON key in AutomationAPI.md against `src/`; every UI label Automation.md quotes against `messages.properties`; the "N callers / only caller / one door" comments in `src/` (about 60 found, ten checked by counting callers); the fixture "Used by" lists against the classes that open them; the ledger totals and the Inbox count against the files. For the MT records: entries touching the areas changed on 2026-09-22/23 (Control+L, the exit/entry guard rename, orange/grey, OB-278) and every entry whose last comment is Adam's. The finding store was grepped before each finding was written (DOC-*, CDR-*, CMT-*, IND9X-*, R28-*, TD-14, RG3-D10, LT-A8 are the prior rows that bear on these). Where a finding rests on behaviour rather than text, it says so and carries a verification request.

## A - high

None. Nothing found here changes what the railway does or loses data; every finding is in the documents or comments that describe it.

## B - medium

### DCN-B1 - Automation.md, the user guide the Readme links for v3.0.0, teaches mechanisms that are gone, puts the routing rule in a menu that does not exist, and states today's standing-square rule backwards

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b |
| **Where** | `Automation.md:190`, `:200`, `:214`, `:216-229`, `:257` |

The Readme sends users here ("Set up on the track diagram ([user guide](Automation.md))", `Readme.md:128`), and its readers are not technical, so what it tells them to do is what they will do. Five passages describe the graph era, an earlier draft of this one, or a rule reversed today:

1. **`Automation.md:200`** - "**Station labels show what is standing there.** A square marked `Point:StationName` as a text label shows the name of any locomotive at that station." That was the old mechanism, and this release retires it: `AutonomySession.java:1905-1909` - "What a caption looked like when it lived in the layout file, and **the only thing that still reads it: the one-time migration**"; the changelog says the same (`Readme.md:377`). A `Point:` label a user types today is drawn by `LayoutGrid.java:1532-1545` as the bare name - "A station label on a diagram autonomy cannot act on" - and never shows a locomotive. The current way is **Show a Station Name Here...** / Control+N in the autonomy editor. A user following the guide gets a label that never changes.
2. **`Automation.md:257`** - "A station with a home locomotive is **outlined in teal on the diagram: solid** when that locomotive is standing there, **dotted** when it is somewhere else. The dotted ones are exactly what `Return Locomotives Home` would move." This is the v2.8.1 graph window's behaviour, carried over on 2026-08-19 (902c647c) with "graph" replaced by "diagram" (`git show 5f0a75e3:Automation.md` line 508). The diagram draws no teal and no dashed stroke anywhere (`COLOR_AT_HOME` is used only by the locomotive-list badge, `AutoLocomotiveStatus.java:290-292`; no `BasicStroke` with a dash array exists in `src/`). What the diagram does is the Homes caption mode in the autonomy editor - the home locomotive's name in the pill, black when it is home, white on dark grey when away (`LayoutGrid.java:1417-1442`, MT-261 ruling 2).
3. **`Automation.md:214`** - "Under the **Autonomy** menu, **Choose Routing Logic...**". There is no such item: the key `autolayout.ui.menuPathPreference=Choose Routing Logic...` was deleted as dead text in 49de68b7 (2026-09-19). The rule is the **Routing Logic** dropdown (`ui.main.routingLogic`) on the Autonomy Settings tab (`TrainControlUI.form:4247` `autoSettingsPanel` > `algorithmType`).
4. **`Automation.md:229`** - "This applies to every layout rather than to one configuration". Since 8edc1166 (2026-08-29, "The routing rule belongs to the configuration") it is stored per configuration - `persistPathPreference` -> `session.setGlobal("pathPreference", ...)` (`TrainControlUI.java:11851`) - and the changelog says so (`Readme.md:367`: "stored with the autonomy configuration, so two configurations can use different rules"). The two user documents contradict each other. The same table (`:216-225`) lists eight rules; the dropdown has ten (`Completely at Random` and `Weighing Station Priority Against Distance` are missing, `messages.properties:339, 341`), and line 229's "stations you have marked as higher priority are still chosen first either way" is false for **Completely at Random**, whose own tooltip says "station priority is ignored" (`messages.properties:346`).
5. **`Automation.md:190`** (written 2026-09-13, 825e7d91) - "**And the square a train is standing on is how much it can HOLD, not track that swallows the train.** A 2-unit train at a platform measured 10 still lies back over the track behind the platform, and that track is still blocked ... Measuring a station generously does not make the trains standing at it get out of the way." That is the 2026-09-13 reading which Adam reversed today. behaviour.md §5c (`:1074-1079`, OB-278): "**The square the train is standing on is track, and it is spent first** ... A train no longer than the square it stands on lies on that square and **blocks nothing behind it**", with "*'the 2 length tile with the s88 consumes 2 units of the train'*, and asked where, *'Everywhere'*". The code follows the ruling (`Layout.java:7325`, `:7391`, `:9609-9612`, `:9642`; `AutonomySession.java:6347`). The guide now tells the user the opposite of what the railway does, in the paragraph written to stop them being surprised by it.

Nothing compensates: the in-app text is right in each case, but the guide is where a new user starts. Reading, `grep` and `git log -S`; nothing needs execution.  **Suggested fix:** replace 200 with the caption workflow (Show a Station Name Here... / Control+N, and the caption dropdown); replace 257 with the Homes caption mode; point 214 at Autonomy Settings > Routing Logic; say "stored with the configuration" at 229; add the two missing rows; rewrite 190 to the OB-278 rule (a train no longer than its measured square blocks nothing behind it). **Verification request for item 5 (execution, optional - the code is already pinned by `core.testAStationsSizeIsAnAllowance`):** on `test/layouts/live-snapshot`, stand a 2-unit train on TunnelLongPark (2 on its square per behaviour.md `:1086`) and ask `edgesCoveredByStandingTrains`; the guide is wrong if nothing behind the square is covered. `DOC-D8` (2026-08-28) certified "its 17 menu labels all resolve" - these were written or went stale after it.

### DCN-B2 - behaviour.md §1a, the tooltip in all eight languages, the enum javadoc and Automation.md all say an unmeasured railway makes Shortest/Longest Track a coin toss; Adam ruled an unmeasured edge counts one, and the code does that

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b (behaviour.md, Automation.md, the tooltips in eight bundles) and fd6341dd (the enum javadoc) |
| **Where** | `docs/reference/behaviour.md:144`; `messages.properties:343, 351` and the seven translations; `Layout.java:110-114`; `Automation.md:166, 221` - against `Layout.lengthOf`, `Layout.java:342-368` |

The one place the rule is written as intended functionality contradicts the code, so by the project's own rule one of them is a bug - and here the code is the one carrying Adam's ruling:

- **behaviour.md §1a** (written today, ab5dc191), the routing table: "**Shortest / longest track** | the route over the least, or most, measured track - only as good as the lengths: **with none set every route measures 0 and the first found wins**".
- **Tooltip** (`messages.properties:343`, `:351` for longest): "The route over the shortest track.  **With no lengths set, every route measures the same and this is At Random.**" Same sentence in every translation (de: "Ohne Laengen sind alle Routen gleich lang und dies wird Zufaellig"). Last reworded 2026-09-19 (4e2d02b5), which shortened it and kept the claim.
- **`PathPreference.SHORTEST_LENGTH` javadoc** (`Layout.java:112-113`): "With none set **every route measures zero** and this falls back to whichever was found first".
- **Automation.md**: `:166` "Both are measured in your lengths; **with none set they have nothing to compare**"; `:221` "By measured length, if you have set lengths".

**The code** (`Layout.java:342-368`, 8edc1166, 2026-08-29): "**AN EDGE WITH NO LENGTH COUNTS ONE**, and without that this whole ranking was a tie at zero ... Adam: *'a min length option that tries to minimize total track length, where we count each s88 as length 1 by default.'*" - `total += e.getLength() > 0 ? e.getLength() : 1`. On an unmeasured railway Shortest Track therefore picks the route over the fewest sensor-to-sensor sections and Longest the most: deterministic and different, which is what the **changelog** says (`Readme.md:368`: "Track with no recorded length now counts as one sensor's worth, so the shortest-track and longest-track rules give different answers").

Why B rather than C: behaviour.md is declared the authority when code and comment disagree (`docs/reviews/README.md:288-291`), so the next person to notice the disagreement is directed to "fix" `lengthOf` back to the tie Adam asked to be rid of. Four documents against one line of code and one changelog entry is the shape in which the wrong side wins. The tooltip is also what a user reads when choosing the rule, in every language.  **Verification request (execution):** on `test/layouts/single-switch` with every tile length cleared (it ships with none), place one train at `WestEnd`, pick a destination reachable by two routes with different section counts if the fixture has one (otherwise a hand-built layout with a short and a long road to one station), set `SHORTEST_LENGTH`, and record the route chosen over 50 dispatch decisions; repeat with `LONGEST_LENGTH`. The finding is confirmed if each rule picks the same route all 50 times and the two rules differ; refuted if either varies. **Suggested fix:** Adam to confirm the 2026-08-29 ruling stands; then behaviour.md row 144, the enum javadoc, the tooltip (re-translated with `\uXXXX` escapes rather than stripped accents) and Automation.md to say "an unmeasured section counts as one, so with nothing measured this is the route over the fewest (most) sections".

## C - low

### DCN-C1 - Readme feature list: "50 different key mappings for up to 260 locomotives" - the 260 is the old ten-page arithmetic

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b |
| **Where** | `Readme.md:154` |

The line was edited from "up to 10 different key mappings for up to 260 locomotives" (v2.8.1) to "up to 50 different key mappings for up to 260 locomotives". 260 was 10 pages x 26 letter keys; a page is still A-Z (`TrainControlUI.java:1973-1998`, `buttonMapping.put(KeyEvent.VK_A ... VK_Z)`), and `MAX_LOC_MAPPINGS = 50` (`TrainControlUI.java:391`), so the ceiling is now 50 x 26 = 1,300. The half that was changed made the other half wrong. Reading alone; nothing to execute.  **Suggested fix:** "up to 50 keyboard pages for up to 1,300 locomotives" (or drop the count).

### DCN-C2 - Readme "Layout editor" shortcut list describes 2.8.1's Escape and Control+L, and omits eight keys added since

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b |
| **Where** | `Readme.md:231-246` against `LayoutEditor.java:7160-7497`, `6499-6558` |

The section was edited in this release (Shift+R/Shift+C removed, Control+S reworded), so it reads as current, but:

- "Escape (clear clipboard & reset tool)" - Escape now lets go of whatever is held and, if nothing is, **closes the editor** (`escapePressed`, `LayoutEditor.java:6499-6504` -> `confirmExit`, which asks about unsaved changes and disposes the window). A user pressing Escape twice to be sure the tool is reset gets the editor closed.
- "Control+L (show text labels)" - in the autonomy editor it now cycles the caption modes (`LayoutEditor.java:7196`, OB-272, 2026-09-23).
- Not listed at all: Control+Y (redo, `:7482`), Control+G (track lengths, `:7172`), Control+K (grid, `:7223`), Control+H (home locomotive, `:7239`), Control+E (square length, `:7299`), Control+N (show station name, `:7314`), Control+B (station maximum train length, `:7345`), and Shift-click to pick squares (which the changelog at `Readme.md:394` does mention).

Also: Control+I is described as "increase diagram by 1 row and 1 column", which matches `growEdges` (`:7476`); no issue there. Reading alone.  **Suggested fix:** bring the list up to the handler; mark the autonomy-editor-only keys as such, as the Control+S line already does.

### DCN-C3 - The whole-column / whole-row move has had no caller since 2026-08-19, and four javadocs and one test still describe it as a live gesture

| | |
|---|---|
| **Disposition** | Open - Adam's decision: removing about 180 lines of unreachable whole-column/row move code, or keeping it |
| **Where** | `LayoutEditor.java:2246-2420` (the `bulk.COL` / `bulk.ROW` branches of `executeTool`), `:2447-2476`, `:2516-2528`, `:3626`, `:3745-3752`; `AutonomyCompanionStore.java:2966-2974`; `test/regression/testLayoutEditorBulkEdits.java:907-911` |

6f60b118 (2026-08-19, "Multi-select in the diagram editor, and the odd editing options it replaces") removed the only production callers: `LayoutEditorRightclickMenu` at v2.8.1 had `edit.executeTool(label, LayoutEditor.bulk.COL)` (line 56) and `bulk.ROW` (line 77). Today every production call passes `null` (`LayoutEditor.java:945, 1564, 2216, 7426`; `LayoutEditorRightclickMenu.java:55`) and the only `bulk.COL` anywhere outside `executeTool` itself is `testLayoutEditorBulkEdits.java:911`. The Readme changelog (`Readme.md:396`) says the options were removed, and RG3-D10 recorded that as deliberate.

The comments were then written or kept as though the gesture existed - several of them after its removal (LT-A8's fix, 2026-08-21, is two days later):

- `LayoutEditor.java:3626` (javadoc of `deleteSelection`): "The four bulk row and column movers pass false for the same reason." The four are `:2270, :2296, :2344, :2368`, all inside the dead branches.
- `LayoutEditor.java:3747` (javadoc of `delete(label, tellAutonomy)`): "A bulk column or row move deletes the line it is vacating ... it is why this parameter exists". The parameter's one live `false` caller is `deleteSelection` (`:3663`); the reason given is the dead one.
- `LayoutEditor.java:2518-2527` (javadoc of the 3-argument `execCopy`): "it is called from the same two bulk loops" - true, and those are its only `false` callers (`:2287, :2360`), so the whole overload exists for dead code.
- `AutonomyCompanionStore.java:2968-2970` (javadoc of `forgetTiles`): "A column or a row is bulk-replaced instead: ... the same thing happens to twenty squares at once". The live callers are selection delete, paste and the shift operations.
- `testLayoutEditorBulkEdits.java:907-909`: "The real gesture, in the real order: pick the column up, then drop it on another one - see LayoutEditor's mouse handling, which calls these two the same way." LayoutEditor's mouse handling calls `executeTool(label, null)` (`:2216`); no mouse handling passes `bulk.COL`. The test proves a path no user can take, and reads as protection for one they can.

Consequence: none on the railway - it is unreachable. The cost is ~180 lines of `synchronized` editor code, its `BulkPlan`/`planBulkLine`/`applyBulkPlan` apparatus, and tests, all maintained (three separate fixes: LT-A8, the caption loss in `:2518-2527`, and the "third route") for a gesture Adam removed; and a reader of `delete`'s javadoc is told the wrong reason its parameter exists. Reading plus `grep`; nothing to execute.  **Suggested fix:** Adam's call - either delete the two branches, `planBulkLine`/`BulkPlan`/`applyBulkPlan`, the 3-argument `execCopy` and the tests that drive them, or leave the code and say at `executeTool` that the branches have no caller since 6f60b118. Either way reword `:3626`, `:3747` and `AutonomyCompanionStore.java:2968` to name the live callers.

### DCN-C4 - OB-272 (this morning) changed what the text switch means; six comments on both sides of it still state the old rule

| | |
|---|---|
| **Disposition** | Fixed - fd6341dd, and the comment it missed in 4132d260 (DCN2-C1) |
| **Where** | `AutonomyEditorPanel.java:6856-6873`, `:6888-6893`, `:6899-6907`; `LayoutEditor.java:5186-5191`, `:5204-5209`, `:5230-5233` |

359e5346 (2026-09-23, OB-272) made text labels a caption option of their own: the switch is ON for Labels Only and OFF for every other option (`applyCaptionMode`, `AutonomyEditorPanel.java:6812-6813`), captions are drawn by `isDrawingCaptions` independently of the switch (`:6817-6827`), and Control+L in the autonomy editor calls `cycleCaptionMode` instead of `toggleText` (`LayoutEditor.java:7196`). The method bodies were changed; their javadocs and the comments beside the calls were not:

- `AutonomyEditorPanel.textLabelsChanged` javadoc (`:6858-6869`): "The Text Labels checkbox is hidden in autonomy mode because None IS that switch turned off - but Control+L still reaches it ... **Turning the text back on restores the last mode that said something**". The body (`:6881-6885`) does the opposite: turning it on selects `CAPTIONS_LABELS`, and turning it OFF restores `lastNamedCaptionMode`. Control+L no longer reaches the switch in that editor.
- `turnTextLabelsOff` javadoc (`:6889`): "The way to say None, which is the editor's own text switch turned off (FR-061)." It is now called for Stations, Parked Locs, Homes and None alike.
- `turnTextLabelsOn` javadoc (`:6901-6903`): "Whether captions are drawn at all is the editor's own 'text labels' box". No longer - `isDrawingCaptions` decides, and its own javadoc (`:6820-6821`) says the two are independent. The two javadocs in one file now contradict each other.
- `LayoutEditor.showTextLabels` javadoc (`:5188-5190`): "the autonomy editor's caption switches decide what a caption SAYS, and with the text hidden they change nothing the operator can see." False after OB-272 - captions are drawn with the text hidden.
- `LayoutEditor.hideTextLabels` javadoc (`:5206`): "The autonomy editor's caption choice includes None, and None is this."
- `LayoutEditor.toggleText` comment (`:5230-5232`): "The checkbox above is hidden there because None IS this switch off, so without this Control+L emptied the diagram under a control still naming a caption." Control+L does not call `toggleText` in autonomy mode any more.

Consequence: none at runtime. The comments are the authority by the rule in `docs/reviews/README.md`, and the one on `textLabelsChanged` states the reverse of what its body does - a reader "fixing" the body to match would undo OB-272. Reading alone.  **Suggested fix:** rewrite the six to the OB-272 rule (switch = Labels Only; captions independent of it); drop the "None IS this switch" sentences.

### DCN-C5 - behaviour.md "The autonomy editor's keyboard doors": "Four shortcuts" is five, "all three" is neither, and the caption order is given two ways

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b |
| **Where** | `docs/reference/behaviour.md:1579-1600` |

- Line 1579: "**Four** shortcuts act on the square the pointer is over, and they ask one question to find it - `LayoutEditor.hoveredSquare()`"; line 1582: "after stepping to another page **all three** named a square on the page before". The table lists four (S, E, B, H). The code has five: Control+N (`LayoutEditor.java:7314-7321`, FR-086, 2026-09-15) also takes `hoveredSquare()` and calls `showStationNameFor`. Control+N is missing from the table and from the count.
- Line 1594-1595: "the dropdown is Stations, Parked Locs, Homes, **Labels only and None**"; line 1597, same paragraph: "Labels Only is **appended after None**". The code agrees with the second: `CAPTIONS_NONE = 3`, `CAPTIONS_LABELS = 4` (`AutonomyEditorPanel.java:255, 265`), so Control+L steps Stations, Parked, Homes, None, Labels Only.

Reading alone.  **Suggested fix:** "Five shortcuts", add the Control+N row ("Show a Station Name Here", asks `offersAStationName`), make 1582 say "all of them", and list the dropdown in its real order.

### DCN-C6 - MT-293 (fixed validated) expects Control+L to go Parked Locs -> None -> Parked Locs; since OB-272 it goes Parked Locs -> Homes, and nothing on the entry says so

| | |
|---|---|
| **Disposition** | Fixed - 997ac6d9: dated comments on MT-293 and MT-023 |
| **Where** | `docs/manual-tests/tests.md:16485-16532` (MT-293) against `LayoutEditor.java:7196`, `AutonomyEditorPanel.java:6842-6847` |

MT-293's Expected: "The captions vanish and the dropdown moves to **None**; the second press brings back **Parked Locs**, not Station Names. The Text Labels tick box is hidden in autonomy mode - the dropdown's None is that switch - but Control+L still reaches it". Adam validated it on 2026-09-08. Since 359e5346 (2026-09-23) Control+L in the autonomy editor calls `cycleCaptionMode`, which selects `(index + 1) % count`: from Parked Locs (1) the first press gives Homes (2), the second None (3). MT-478 was written for the new behaviour, but MT-293's Comments end on 2026-09-09 and MT-478 does not name it, so a re-run of MT-293 now fails as written - and it is "fixed validated", the state the README says is "the only state that means anything is finished". The same is true, less sharply, of the RGD-C3 paragraph of MT-275 (`tests.md:15842-15849`), which MT-293 was split from and which is superseded.

MT-023 (`tests.md:2676`, fixed validated) likewise tells the operator to open "Signal Protecting This Station", renamed today to "Exit Guard Signal..." (`messages.properties:1692`); a menu rename is lighter, but the same rule applies. Reading alone.  **Suggested fix:** a dated Claude comment on MT-293 (and MT-023) naming OB-272 / the rename and pointing at MT-478 / MT-479.

### DCN-C7 - viewer-editability.md: "Two things the editor keeps to itself ... Signal Protecting This Station" - it is three things and the item has a new name

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b, and the two identifiers it missed in 4132d260 (DCN2-C8) |
| **Where** | `docs/reference/viewer-editability.md:69-70` against `AutonomyEditorPanel.java:1674-1699` |

The document says the `menuOnly` gate keeps two things out of the viewer: "**Signal Protecting This Station…**, and the caption items on a *track* square." Since FR-096 (a737360e) the same `if (isStation && !menuOnly)` block builds both the exit guard (`autosetup.ui.menuPairSignal` = "Exit Guard Signal...") and the new entry guard (`menuPairEntrySignal` = "Entry Guard Signal..."), so it is three, and the first no longer has the name quoted. The same table names two stores by identifiers that do not exist anywhere in `src/`: `portalDisabled` (`:62`; the store's key is `disabledLinks`) and `tileLength` (`:64`; `tileLengths`). A sweep of every backticked identifier in behaviour.md, open-questions.md and viewer-editability.md against `src/` found only these two (plus `SkipException`, a TestNG class, and the word `disposition`). Reading alone.  **Suggested fix:** "Three things ... Exit Guard Signal..., Entry Guard Signal..., and the caption items on a track square."; `disabledLinks`, `tileLengths`.

### DCN-C8 - live-snapshot README has two "Not invariants" sections that give opposite advice about reading the lengths

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b |
| **Where** | `test/layouts/live-snapshot/README.md:59-76` |

The first `## Not invariants — set these in code` (`:59-66`) says of lengths: "The snapshot *does* carry the placements and lengths ... **a test that cares about either should set them rather than read them**". The second, with the identical heading (`:68-76`), written at the 2026-09-23 refreeze, says: "Since the refreeze ... these are Adam's own measurements ... **A test about the railway as he runs it reads them**". Both are current text under the same heading; a reader choosing a pattern for a new test gets both answers. The second is plainly the newer (it dates itself to the refreeze, and its "Until then the snapshot measured nothing" is what made the first one's advice safe). Reading alone.  **Suggested fix:** delete the first section's lengths bullet (the second supersedes it) and merge the two headings.

### DCN-C9 - AutomationAPI.md: CMT-B3 took the deleted graph window out of one section; six siblings still send the reader to it, and one teaches the retired `Point:` label

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b, and the five sites it missed in 4132d260 (DCN2-C3) |
| **Where** | `AutomationAPI.md:19, 228, 392-396, 413-415, 436, 476, 489, 507-511` |

CMT-B3 (2026-09-01, fixed in 9f1b80c8) was "`AutomationAPI.md`'s 'Returning locomotives home' section instructs the reader to use a graph window that was deleted". That section is clean now; the rest of the file was not swept, and the banner at the top promises "TrainControl still reads it, and **everything here still works**":

- `:392-396` "Prettifying the Graph Visualizaton" - "maximize it, and simply use your mouse to move points around ... or you can use the 'Export Current Graph' button". The GraphStream window is gone (the libraries were dropped, `Readme.md:444`); there is no "Export Current Graph" string in `messages.properties`.
- `:228` "click on **'Validate Configuration & Open Graph UI'**" - the button is `ui.main.validateConfigOpenGraphUI=Validate Configuration`.
- `:19` "Using TrainControl's UI to represent your layout as a graph model, make visual edits, and monitor operation (best)".
- `:436` "Remember that everything described below can now be fully edited via TrainControl's **graph UI**!"
- `:476` "All these settings can be changed by right-clicking on any point within the **graph UI**."
- `:489` "This can also be set by right-clicking the station in the **graph UI**."
- `:507-511` "Displaying locomotive locations on track diagrams (v2.4.9+) - ... create a text label with the value `"Point:StationName"` ... If a locomotive is present at that point, its name will be shown in the label." Retired in this release - see DCN-B1 item 1; `LayoutGrid.java:1532-1545` draws such a label as the bare name, and with a legacy JSON configuration there is no setup session to turn it into a caption, so for the JSON user this page addresses the label never shows a locomotive.

Also `:413-415`, in the terminus section: "**How much has to be measured is currently unreliable, and is being reviewed** ... Do not rely on either reading until this is settled." It was settled on 2026-09-06 and is written down as the rule in `behaviour.md` §5b (lines 925-938: "It is only indeterminate if the entire logical segment has length 0"). Reading alone.  **Suggested fix:** one sweep of the file for "graph UI", "Graph Visualizaton", "Open Graph UI" and `Point:`; replace the terminus paragraph with a pointer to behaviour.md §5b.

### DCN-C10 - behaviour.md "The four bulk doors on Bulk Tools" misses the fifth length door, Mass Assign Train Lengths (FR-094), which behaviour.md does not mention anywhere

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b |
| **Where** | `docs/reference/behaviour.md:993-1003` against `AutonomyEditorPanel.java:2312-2327` |

§5b lists Mass Assign Lengths, Mass Assign Max Train Lengths, Clear All Track Lengths and Clear All Max Train Lengths as "the four bulk doors", and says "The two WALKS - lengths and maxima - share one prompt". FR-094 (bc71e6ac / its build, 2026-09-23) added `Mass Assign Train Lengths...` to the same submenu (`autosetup.ui.menuMassAssignTrainLengths`, greyed on `trainLengthDoor().trainsWithoutALength()`), and `grep -n "FR-094\|Mass Assign Train" docs/reference/behaviour.md` finds nothing. It is the walk that fills the other half of the §5d non-atomic gate (`trainsWithoutALength` is the same list `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack` reads, `TrainControlUI.java:6150`), so its rule belongs beside that section's. Reading alone.  **Suggested fix:** "five bulk doors", one sentence for FR-094 (what it walks, whether it shares the prompt, that it is not a page's question), and a pointer from §5d.

### DCN-C11 - The v3.0.0 changelog has no line for five user-visible features built since it was last written

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b |
| **Where** | `Readme.md:362-445` |

`grep -i "mass assign\|bulk tools\|entry guard\|guard signal"` over `Readme.md` finds nothing, yet all of these are things a non-technical user will meet on a menu: the **entry guard** (FR-096, a737360e - signals thrown red when a train arrives), **Mass Assign Lengths** (FR-089), **Mass Assign Max Train Lengths** (FR-091), **Mass Assign Train Lengths** (FR-094) and **Clear All Max Train Lengths** (FR-092), plus the autonomy editor's keys (Control+E, Control+B, Control+N, Control+H - see DCN-C2). Adam's standing preference is that the changelog stays short and user-visible and that completeness is not a release blocker, so this is low; but these are exactly the user-visible kind, and the exit guard's line (`Readme.md:369`) now describes only one of the two signal pairings the station menu offers. Reading alone.

### DCN-C12 - Three comments whose counts or "every" no longer hold: the page-index writer, `carriesAHome`, and the orphan-javadoc test's "sat"

| | |
|---|---|
| **Disposition** | Fixed - fd6341dd: the two counts corrected, and the two orphaned javadocs reattached (ratchet 90 -> 88) |
| **Where** | `TrainControlUI.java:19795-19797`; `AutonomySession.java:5351`; `test/regression/testJavadocsAreAttached.java:19-25` |

1. **`TrainControlUI.writeIndexAndKeepLinksAimed`** javadoc: "**Every page operation goes through here**, which is the point of the method.  **The three callers** each used to write the index themselves". It has two callers - `combineLinkedPages` (`:26473`) and `deleteLayoutMenuItemActionPerformed` (`:26929`), both passing `renamed = null`. Since abbed984 (2026-09-11) Add, Duplicate and Rename write the index through `LayoutPageEdit` (`LayoutPageEdit.java:348`, `LayoutDiagram.writeLayoutIndex`) after re-aiming the arrows themselves, and `LayoutDiagram.java:567-568` says so correctly: "`writeIndexAndKeepLinksAimed` is still the whole job for a caller that writes no page of its own, **which is Delete and Combine**." Two comments on one rule now disagree about who uses it; the `renamed` parameter documented on the UI method is never non-null.
2. **`AutonomySession.carriesAHome`** javadoc: "One spelling of 'carries a home', so **the three callers** cannot drift about what a blank means." One caller (`:7543`), and it had one when it was written (6fc243f9). Two other reads of the same key do their own spelling: `:710-719` (`point.optString("home", "")` then `.trim().isEmpty()` - the same answer) and `:7524` (`locomotive.equals(point.optString("home", null))` - a different question). The sentence claims a consolidation that did not happen.
3. **`testJavadocsAreAttached`** header: "The three the review names are the ones that matter: `Layout`'s `locomotiveInBlock` parameters **sat** above `refreshProtectingSignal` ...; `AutonomyCompanionStore`'s ... rationale **sat** above `forgetTiles`, leaving `moveTiles` undocumented; and `Util`'s ... sat above `sanitizeFilename`". Only the `Util` one was moved (TD-14 is closed on it; `Util.java:498-519` is attached to `writeAtomically`). The other two are still orphaned today: `Layout.java:9214-9224` (the `locomotiveInBlock` block, sitting directly above `refreshProtectingSignal`'s own javadoc, while `locomotiveInBlock` at `:10089` has none) and `AutonomyCompanionStore.java:2955-2964` (the `moveTiles` block above `forgetTiles`' javadoc; `moveTiles` at `:2994` has none). The ratchet counts them, so nothing fails - but the past tense tells a reader they were fixed.

Reading and `grep`; nothing to execute.  **Suggested fix:** (1) "Delete and Combine go through here; Add, Duplicate and Rename through `LayoutPageEdit`, which re-aims before it writes the page"; (2) "one caller"; (3) move the two javadocs onto `locomotiveInBlock` and `moveTiles` and lower the ratchet by two, or say "sit".

### DCN-C13 - behaviour.md §1a, written today, says the per-rule routing explanations "are still not shown anywhere, which is OB-163"; OB-163 was fixed on 2026-08-30 and validated

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b |
| **Where** | `docs/reference/behaviour.md:174-175` against `TrainControlUI.java:11600-11634` |

behaviour.md: "*(OB-265 recorded that this section did not exist. **The per-rule explanations written for the dropdown are still not shown anywhere, which is OB-163.**)*" - added by ab5dc191 ("behaviour.md: a pass against the open OBs", 2026-09-23). `refreshRoutingLogicTooltip` (`TrainControlUI.java:11615-11634`, from fd31d2b2, 2026-08-30) puts `autolayout.ui.tooltip.pathPreference<RULE>` onto the dropdown's tooltip for the selected rule, and is called from the build, the listener and `restoreRoutingLogicSelection`; its javadoc begins "Puts what the chosen routing rule DOES onto the control that chooses it (OB-163)". OB-163's receipt (`issues.md:1256`) points at MT-241, which is **fixed validated**. A pass "against the open OBs" treated a closed one as open. Reading alone.  **Suggested fix:** "The per-rule explanations are the dropdown's tooltip (OB-163)."

### DCN-C14 - open-questions.md says the one open reversal question is recorded on OB-190 in the Inbox; today's Inbox clear-out removed OB-190's body, and its receipt row does not carry the question

| | |
|---|---|
| **Disposition** | Fixed - 997ac6d9: filed as its own Inbox entry, open-questions.md repointed |
| **Where** | `docs/reference/open-questions.md:65-70`; `docs/manual-tests/issues.md:1222` |

open-questions.md, Part 1, Reversals: "**Open: one, and it is Adam's call rather than a defect.** A journey that passes a compulsory-turn square is turned there ... *'Yes - keep the current direction'* can leave the train **net-reversed** ... Section 3 of `behaviour.md` promises the opposite in as many words. **Recorded on OB-190 in the Inbox, where it has said 'ALSO STILL OPEN' since 2026-09-08**". 91f27dfb ("Fifty certified entries out of the Inbox, on Adam's word", 2026-09-23 10:42) deleted OB-190's Inbox entry because its test MT-412 is validated - and that entry's body was the only place carrying "ALSO STILL OPEN, and Adam's call rather than a defect: a journey that passes a reversing square gets a compulsory turn there ..." (visible in `git show 91f27dfb -- docs/manual-tests/issues.md`, removed line 478). The surviving receipt row (`issues.md:1222`) is about the facing redraw only. `grep -i "net-revers\|keep-direction ends"` over behaviour.md, issues.md and tests.md now finds nothing; open-questions.md is the only live record, and it points at an entry that is gone. The same commit edited open-questions.md (the Inbox count, `:42-48`) and did not touch this paragraph. Nothing is lost (it is in git and in this paragraph), but the question Adam still owes an answer to has no entry on any list he works from. Reading and `git show`.  **Suggested fix:** file it as its own Inbox item (or an MT question entry, which the manual-tests README allows: "A question for Adam to rule on is an entry too") and repoint open-questions.md at it.

### DCN-C15 - Eight MT entries carry Adam's "Works." of 2026-09-23 and are still "fixed unvalidated", so the ledger asks him for them again

| | |
|---|---|
| **Disposition** | Fixed - 997ac6d9, and MT-482's comment, which it missed, in b53439dc (DCN2-C9) |
| **Where** | `docs/manual-tests/tests.md` ledger (`:28-72`); MT-470, 475, 476, 478, 481, 482, 483, 484 |

Each of these ends with "**Adam, 2026-09-23 (triage).** Works." against commit 41bd1831, and nothing after it; each is still `fixed unvalidated` and on the generated ledger "where your attention is needed". The README's rule is that a test moves to fixed validated "only on Adam's word in the Comments", and it is there. b16eead2 recorded the feedback and 281c79de replied to the entries that came back with notes (467, 474, 477, 479, 480, 485) without touching the ones that came back clean. The only honest reason to hold one back is code changed after 41bd1831 in its area, and then the README wants that said in the entry: 195aa1f1 / 31aabbba (MT-477's tail change, after the run) plausibly bear on MT-475 and MT-482 (what the orange and the grey cover), but neither entry says so. The ledger's own history (`docs/manual-tests/README.md:109-112`: "31 rows that were no longer open and 8 whose disposition had drifted") is this failure. May simply be the coordinator's next step; recorded so it is not missed.  **Suggested fix:** promote the six whose area did not move after 41bd1831; for MT-475 and MT-482, either promote or add a dated comment naming 195aa1f1 and asking for a re-run.

### DCN-C16 - MT-475's Expected was rewritten in place when MT-482 replaced its grey step, against the append-only rule, and its last bullet still states the replaced rule

| | |
|---|---|
| **Disposition** | Fixed - 997ac6d9: a dated comment on MT-475 saying which bullets MT-482 replaced |
| **Where** | `docs/manual-tests/tests.md` MT-475, Expected (`:24464`, `:24468`) |

1cd72c3f (2026-09-23 10:09) changed MT-475's step-3 line from "the orange stops where the train's length runs out; the **grey carries on** to the next sensor, ..." to "the orange stops where the train's length runs out.  *(Superseded the same day by MT-482: the grey no longer carries on to the next sensor - it covers only the squares the train lies on.)*" (`git show 1cd72c3f -- docs/manual-tests/tests.md`, lines 31-33 of the diff). `docs/manual-tests/README.md` rule 5: "Entries are never deleted, never reordered, and **their instructions are never rewritten**. Two things may change on an existing entry: its Disposition line, and its Comments section". The entry's title ("the grey is what it blocks") and its last Expected bullet - "Shortening the train takes the orange back at once; **the grey covers the stretches it still reaches**" - were left in the whole-stretch wording MT-482 replaced, so the entry now says both things. Adam ran it afterwards (41bd1831) and said "Works", which reads as validating the stretch rule too. Reading and `git show`.  **Suggested fix:** leave the text alone from here on and add a dated comment saying which bullets MT-482 replaced; restore the original step-3 wording if the append-only rule is to be kept literal.

## D - not defects

### DCN-D1 - docs/reviews/README.md deletion counts reproduce

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`git log --diff-filter=D` over `docs/reviews` plus the three dated `docs/reviews-2026-09-*` folders gives 145 on 2026-09-08 and 44 + 7 + 8 + 6 = 65 on 2026-09-21, total 210, as README.md:13-14 says. The 11 deletions on 2026-08-22 are harness dumps (`1-derived-active.json` etc.) and `findings.md`, not reviews, so excluding them is right.


### DCN-D2 - Every backticked test name in the reference docs, the fixture READMEs and `test/README.md` resolves

| | |
|---|---|
| **Disposition** | Closed - checked clean |

141 names extracted from `behaviour.md`, `open-questions.md`, `UI-standards.md`, both READMEs under `docs/`, `Automation.md`, `AutomationAPI.md`, `test/README.md` and the four fixture READMEs; each `pkg.testClass` checked as a file and each `pkg.testClass.method` / bare method checked as a `void name(` definition. The one miss, `testEverySquareOnThisLayoutBuildsToOneCopy`, is cited by behaviour.md `:296-302` and two-copies-evaluation.md precisely to record that it never existed.

### DCN-D3 - behaviour.md §5d's door count holds

| | |
|---|---|
| **Disposition** | Closed - checked clean |

"the **five dispatch doors** - Start, Execute Timetable, Return Home, and the two hand dispatches" plus "the **two load doors**": `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack` has exactly seven production callers - `startAutonomyActionPerformed`, `executeTimetableActionPerformed`, `requestReturnToHome`, `validateButtonActionPerformed` (TrainControlUI `:25561, :23100, :24618, :24297`), `AutoLocomotiveStatus:1159`, `LayoutRightclickAutonomyMenu:1376`, `AutonomyViewerPanel:822` - matching its own "SEVEN CALLERS" javadoc.

### DCN-D4 - behaviour.md §7c (FR-096, written today) matches the code

| | |
|---|---|
| **Disposition** | Closed - checked clean |

The entry guard is thrown in `Layout.executePath` on the path's last Point only (`:8565-8570`, `throwEntryGuard`), with no `isRunning` gate but reachable only from a run; the exit guard is gated on `isRunning()` (`:9250`) and evaluated per signal (`refreshOneSignal`). Both lists are dropped when a square stops being a station (`AutonomySession.java:5744-5747`) and both are moved/forgotten by the same `ListMapKept` registry (`AutonomyCompanionStore.java:5053-5054`), so the "one signal on both lists" and "dropped with the station" sentences hold.

### DCN-D5 - Readme facts checked and right:

| | |
|---|---|
| **Disposition** | Closed - checked clean |

eight languages (`messages.properties` + seven); seven sidebar icons (`TAB_ICON_*`, `TrainControlUI.java:162-168`); `json-20260814.jar` and `flatlaf-3.7.2.jar` in `nbproject/project.properties`; `src/org/traincontrol/gui/resources/running_train.png` exists; the 50-page cap (`MAX_LOC_MAPPINGS`, applied to adding only - loading is uncapped, as the changelog says); "v2.8.1 reads only the first ten" (`NUM_LOC_MAPPINGS = 10` at `5f0a75e3`).

### DCN-D6 - Changelog fixes sampled and present:

| | |
|---|---|
| **Disposition** | Closed - checked clean |

"Pressing Stop while TrainControl is not connected now says so in the log" (`MarklinControlStation.exec`, `:2812-2820`); "a short message ... was read as an emergency stop" (`CS2Message.getSubCommand` returns -1 below five bytes, `:431-445`). Both are defects a user could hit.

### DCN-D7 - Two v2.7.4 changelog lines moved into v2.8.0 are deliberate

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(b93cbf51, RGN-B3: "two changes were credited to a release that never had them").

### DCN-D8 - The generated ledger's totals are right:

| | |
|---|---|
| **Disposition** | Closed - checked clean |

486 entries; 392 fixed validated, 51 superseded, 3 needs test, 40 fixed unvalidated, as `tests.md:74-75` says (but see DCN-C15 for eight of the 40).

### DCN-D9 - open-questions.md's Inbox count is right:

| | |
|---|---|
| **Disposition** | Closed - checked clean |

64 = 37 OB + 27 FR in `issues.md`'s Inbox at HEAD. Its warning that many Inbox entries are already done ("Read the receipts, not the presence of an entry") is stated deliberately, so FR-061, FR-085, FR-089 etc. still sitting there is not a finding.

### DCN-D10 - The fixture READMEs' "Used by" lists match

| | |
|---|---|
| **Disposition** | Closed - checked clean |

For `live-snapshot`, `single-switch` and `curve-into-platform`, every listed class uses the fixture, and every class that names the fixture outside a comment is listed (`regression.testEveryScenarioIsUsedAndSaysSo` guards this). `live-snapshot`'s pinned counts (122 reduced edges, 87 Points, 125 edges) match `testTheFrozenRailwayIsStillTheRailway:99-109`.

### DCN-D11 - AutomationAPI.md's API surface resolves:

| | |
|---|---|
| **Disposition** | Closed - checked clean |

every `name(` in it exists in `src/`, and every JSON key in its examples is read by the automation code. Its defects are the UI references in DCN-C9, not the API.

### DCN-D12 - UI-standards.md matches its reference screen:

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`RouteEditorFrame` uses Segoe UI Semibold 13 / Segoe UI 14 / Segoe UI Bold 12 and `HEADING_BLUE = (0,0,155)` (`:869, :1227, :1236, :1260`).

### DCN-D13 - behaviour.md §1a's constants are right:

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`YIELD_SECONDS = 30`, `NO_PATH_IDLE_MS = 250` (`Layout.java:79, 88`); the routing rule is stored with the configuration (`session.setGlobal`), as §1a says - the contradiction is in Automation.md (DCN-B1 item 4), not here.

### DCN-D14 - `test/README.md`'s per-folder counts are stale again (88/31/70/5 against 128/45/110/7), and that is already on record

| | |
|---|---|
| **Disposition** | Closed - checked clean |

as DOC-C1, MON-C20 and IND9X-D1. Not re-raised; the evidence of three recounts going stale suggests dropping the column rather than recounting it a fourth time.

### DCN-D15 - `Layout.lengthOf`'s "the floor only works because the units are small" still holds on the refrozen railway:

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`live-snapshot`'s 158 tile lengths are 1 (140), 2 (14), 3 (3) and 4 (1), so a floor of 1 is the size of the smallest real square. Its "Only 18 of the 132 edges carry a recorded length" is history rather than a claim about today.


## What this pass did not cover

- **`tests.md` in bulk.** 486 entries, ~25,000 lines. Only the entries touching areas changed in the last two days were read against the code; an older fixed-validated entry whose Expected was overtaken by a change made a week ago would not have been found. The highest-yield next sweep: take each behaviour change that reversed an earlier ruling (behaviour.md marks them - "reversing half of his answer", "settled a third time", "This reverses what MT-373 accepted") and grep tests.md for the MTs that validated the earlier answer.
- **`behaviour.md` sections 1, 2, 3, 4, 4a, 5a, most of 5c, 6a, 7, 8.** Not read against the enforcing methods. Section 3 (reversals) and 5c (the tail) are the densest rule text and the most edited, and the tail rules cannot be settled by reading - they need the walks run on `live-snapshot`.
- **`issues.md` Inbox bodies and receipts**, other than OB-190 and the counts; **`docs/reference/package-sweeps.md`, `translations-2026-09-13.md`, `two-copies-evaluation.md`, `docs/plans/*`, `docs/tools/*/README*`, `assets/automation/README.md`.**
- **The translations.** Noticed in passing, not reviewed: the routing tooltips in the seven non-English bundles transliterate rather than escape (`Laengen`, `Zufaellig`, `najkrotszym`, `mas corta`), where the project's rule is backslash-u escapes (the French bundle does escape its apostrophe). Whether that is a deliberate choice recorded in `translations-2026-09-13.md` was not checked.
- **Code comments outside the areas named above.** The comment rule was checked only where a comment names a count, a caller, another place, or a behaviour changed on 2026-09-22/23; the few thousand finding-id citations were not audited for self-containment (`regression.testEveryCitationResolves` checks only that they resolve).
- **Anything that needs execution**: DCN-B2's routing behaviour and DCN-B1 item 5 carry verification requests; nothing else here depends on behaviour.
