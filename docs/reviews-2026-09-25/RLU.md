# RLU - the windows: every screen, dialog and menu, and the message bundles (TrainControl 3.0.0 release readiness)

**Status:** open

**Prefix:** RLU

**Reviewed:** branch `autonomy-diagram-r0` at `146b25eb` (2026-09-25 13:40), the range `de75f3d1..146b25eb`, on 2026-09-25.

## Method

Read-only: no test, no compile, no JVM, no git state change; nothing under `cs2_sample_layout/` was opened.  I read
`docs/reviews/README.md`, `docs/reviews/FANOUT.md`, and `git log`/`git diff de75f3d1..HEAD` for `src/org/traincontrol/gui/`
and the eight bundles: `aa2cb504` (MT-575, the tail list in front of the editor), `6d75bfd7` (Routes > Import names the
routes and counts only those armed), `fd3237ea` (an old autonomy.json into the configuration named), and `ef618c8e`'s two new
keys in eight languages (OB-299).  Around each change I read the whole door and its siblings: `TailCrossedPrompt.java`
(all of it), `GraphLocAssign.commitAndRecord`, `TrainControlUI.rememberPlacement`, the editor-open guards,
`importRoutesMenuItemActionPerformed`, `MarklinControlStation.importRoutes`/`routesSavedArmed`/`exportRoutes`,
`AutoJSONExport`'s save, the backup's `routes.json`, `MarklinRoute`'s command execution, `AutonomyViewerPanel`'s
`importConfiguration`/`importLegacyGraph`/`load`/`loadAfterImport`/`save`, `AutonomySession.importLegacy` and
`configurationToLoadAfterImport`, `AutonomyCompanionStore.createConfiguration`/`setActiveConfiguration`/`importConfiguration`,
`LayoutEditor`'s Cancel, `Layout.whyItReachesNoStation`/`canReachAnyDestination`/`isSendableDestination`, and the three
Why not Moving? doors (`AutoLocomotiveStatus`, `AutonomyEditorPanel.composeWhy`).  Of today's tests I read
`testTheRoutesImportDoorAsksByName`, `testTheImportDoorReadsAnOldFile`, the MT-575 claim in
`testThePlaceDoorsKeepTheHeading`, `testAnImportGoesIntoTheConfigurationNamed`, and `testMessageBundles`' new scan;
the other superseding tests I did not review.  The bundles were compared by an in-memory Python read (no file written): key
sets, placeholder sets per key, non-ASCII bytes, empty values.  Every finding was checked against
`docs/manual-tests/findings.tsv`; two open ones (IND9X-C4, IND9X-C5) are restated in RLU-B2 on purpose, and nothing here
repeats a closed finding.  **Everything below is from reading**; each finding that needs a run says so, with the
fixture and what proves or refutes it.  Note for the fixer: when I started, the working tree held an uncommitted
one-line change to `TailCrossedPrompt.whereTheAnswerGoes` (`return now;` in place of the `placementStillStands` check -
it looked like a mutation run in progress); it had gone by my later reads, and every claim here is about the committed
code.

---

### RLU-A1 - Routes > Import reads a UTF-8 file in the machine's own character set: on Java 8 under Windows every accented letter in a route comes back wrong, and its locomotive commands then name no locomotive

| | |
|---|---|
| **Disposition** | Fixed - Routes > Import reads its file as UTF-8.  Claim bb197b16 (red first on this machine's Java 8, Cp1252), fix ab3a37c2; mutation R2 red.  2.7.4 read it the same way; carried to 2.8.2. |
| **Grade** | A - a route's locomotive command is skipped on the railway; narrow precondition, stated below.  Needs execution. |
| **Where** | `TrainControlUI.java:25062` |

**What.**  Routes > Import reads the chosen file as `new String(Files.readAllBytes(path))` - no character set, so Java
uses the platform default.  Every door that writes the file writes UTF-8: Routes > Export's Save
(`AutoJSONExport.java:115,125`), and the backup's `routes.json` (`TrainControlUI.java:22392-22393`, whose comment says it
is written "so the file in the archive is one that Import Routes will read back").  Its sibling, Autonomy >
Configuration > Import..., reads UTF-8 (`AutonomyViewerPanel.java:1026-1029`).  This is the one file read in `src/` with
no character set.  `org.json` writes letters such as ä, ö, ü, ł, é into the file as they are (it escapes only control
characters and two small ranges), so they are multi-byte UTF-8 on disk.

The Readme tells users to install Java 8 and run `java -jar TrainControl.jar`.  Java 8 on Windows decodes with the
Windows code page (Cp1252 on a Western European machine, Cp1250 on a Polish one), so "Köf II" comes back as "KÃ¶f II" and
"Ausfahrt Süd" as "Ausfahrt SÃ¼d".  What that does:

- the route list and the new re-arm question show the broken names;
- a route's Locomotive speed, direction or function command names a locomotive by name, and at run time
  `MarklinRoute.java:934-990` looks it up with `getLocByName`, finds nothing, and skips the command with a log line
  (`route.warningLocomotiveNotExist`).  A route whose job is to stop a train when it reaches a sensor (speed -1, the
  instant stop) sets its switches and does not stop the train;
- an "Auto locomotive" command naming a station with an accented name is broken the same way.

**Why A:** the consequence is a stop command that silently does not happen on the layout.  **Mitigations:** only on
Java 17 or older (18 onwards defaults to UTF-8), only on Windows (macOS and Linux default to UTF-8), only for names with a
non-ASCII letter, only through Routes > Import - though that is the restore path the 3.0.0 backup names.  The broken
names are visible in the route list, and the log says so each time the route fires.  **Not a 3.0.0 regression**: v2.7.4c
read the file the same way (its `TrainControlUI.java:13117`) while its export already wrote UTF-8.  The fix is one
argument, `StandardCharsets.UTF_8`.

**Why no test caught it:** `testTheRoutesImportDoorAsksByName` writes its file as UTF-8 and imports Adam's routes, and
nothing in it has a non-ASCII letter, so it passes either way - and if the battery's JVM runs with UTF-8 as its default it
would pass even with such a name.

**Verification request.**  Fixture: a sandbox LocDB with a locomotive named "Köf II" and a route "Ausfahrt Süd" with an
s88 and a Locomotive speed command for "Köf II"; `model.exportRoutes()` written as UTF-8 (as Export's Save writes it);
Routes > Import of that file driven as `testTheRoutesImportDoorAsksByName` drives it, **in a JVM started with
`-Dfile.encoding=Cp1252`** (Java 8's default on Western Windows).  **Proves:** the imported route is called "Ausfahrt
SÃ¼d", its command names "KÃ¶f II", and firing it logs `route.warningLocomotiveNotExist` with the locomotive's speed
unchanged.  **Refutes:** both names come back exactly as exported.

---

### RLU-B1 - "Replace it with the imported one?" answered Yes does not replace for an old autonomy.json: the file is gap-filled into that configuration, and a train moved since can end up in two places

| | |
|---|---|
| **Disposition** | Fixed as RLA-B2. |
| **Grade** | B - a configuration that will not load after a Yes to a question that promised something else.  Needs execution. |
| **Where** | `AutonomyViewerPanel.java:1013-1022` (the question), `:1129-1139` and `:1169` (today's `activateTheConfigurationNamed`), `AutonomySession.java:916-1100` (`importLegacy`) |

**What.**  Autonomy > Configuration (...) > Import... asks for a name and, when a configuration of that name exists, asks
"A configuration named {0} already exists.  Replace it with the imported one?" (`autosetup.ui.confirmImportOverwrites`).
For an export bundle Yes does replace it: `AutonomyCompanionStore.importConfiguration` puts the file's configuration over
it (`:2574`).  For an old autonomy.json, since `fd3237ea` the configuration named is chosen and `importLegacy` writes into
it - and `importLegacy` fills gaps and never overwrites.  That is the ruled behaviour: MT-298 ("A second import fills gaps
and does not overwrite") and its superseding test `testASecondImportFromTheMenuKeepsAHandMadeChange` answer **Yes** to
replacing and assert nothing was replaced.  So the door asks one thing and the rule does the other, and the two kinds of
file behind one Import menu item disagree about what Yes means.

**The consequence is more than wording.**  `importLegacy`'s one-train-one-place check (`placedAlready`, `:923`) is
per file and starts empty, and a placement goes in wherever the square has none (`!extras.has(LOCOMOTIVE)`, `:1052`).  A train
that has been moved in that configuration since the first import - by a run and the capture that follows, or by the
editor's Place - leaves its old square empty, so the second import stands it there too.  The configuration then holds
the train twice, which is `autosetup.ui.checkDuplicateLocomotive`, an ERROR: "autonomy refuses the whole setup while it is
in two".  Homes go the same way (`homedAlready` is per file too): a second home for a train that already has one.  The
import's message counts the doubled train as "placed".

**Why it is reached now.**  The Import prompt suggests the file's own name ("autonomy" for autonomy.json), so a second
import of the same file lands on the Replace question by default.  Before today the Yes was just as untrue, but the
import went into whichever configuration was in use.

**Mitigations:** when another configuration is running, the import does not load the one it wrote into
(`configurationToLoadAfterImport`), so the refusal appears only when the operator chooses it; the editor's list names the
doubled train.  **Options for Adam:** word the question by file kind (for an old file: "Add what the file has and this
configuration does not?"); or have Yes clear the configuration before an old file goes in (which reverses MT-298's rule);
or have `importLegacy` seed `placedAlready`/`homedAlready` from the configuration it writes into, so a train already
standing somewhere is reported as a duplicate rather than placed twice.  The last is a fix under either wording.

**Verification request.**  Fixture: the live-snapshot sandbox; Autonomy > Configuration > Import... of
`docs/manual-tests/files/MT-298-autonomy-2.7.4c.json` named "T"; in "T", Place one of its four trains on another station
(the editor's Place, or `session.placeLocomotive`); import the same file again as "T" and answer Yes.  **Proves:** "T"'s
placements name that locomotive on two squares and `AutonomyChecks` reports `DUPLICATE_LOCOMOTIVE`; choosing "T" is
refused.  **Refutes:** the train stands once (something sweeps it on the way in).

---

### RLU-B2 - with Preferences > Window Always on Top ticked, which is the default, the unowned modals of IND9X-C4 and IND9X-C5 can open beneath the main window - including the right-click send refusals met on an ordinary evening

| | |
|---|---|
| **Disposition** | Fixed - every refusal on the right-click menu, and the setup's tidy report, belong to the main window.  Claim e4f61822 (red first: owned by Swing's hidden frame), fix 693dd0a1; mutation R12 red.  IND9X-C4 and IND9X-C5 close with it. |
| **Grade** | B - the application looks frozen until the hidden dialog is dismissed.  Needs execution. |
| **Where** | `LayoutRightclickAutonomyMenu.java:1410, 1423, 1449, 1472, 1482` (and `:463, 581, 869, 1543`); `AutonomyViewerPanel.java:1465`; `TrainControlUI.java:307` |

**What.**  Both findings are catalogued: the right-click refusals parent on the popup menu, which has left its window
by the time its item's action runs (IND9X-C5), and `AutonomyViewerPanel.save()` parents the setup-tidied warning on a panel
that is "Built but not shown" (IND9X-C4; `TrainControlUI.java:3928`).  In both cases `JOptionPane` finds no window and
uses Swing's hidden shared frame as the owner, so the dialog is modal, centred on the screen, and belongs to no visible
window.

Two things the catalogue rows do not weigh:

1. **Window Always on Top is on by default** (`ONTOP_SETTING_DEFAULT = true`, `TrainControlUI.java:307`; the menu item
   under Preferences).  An always-on-top owner lifts the windows it owns; a dialog owned by the hidden frame is not
   lifted, and on Windows a normal window stays beneath a topmost one.  A modal dialog under a maximised main window blocks
   every window while nothing visible says so.
2. **The doors are everyday ones.**  The right-click "-> station" send item refuses on this route when the track power is
   off ("power on to start"), when the train is too long for the route, when a berth would foul a road, when a loop meets
   the train's own tail, and - added 2026-09-24 with the same parent (TDU-B1) - when the setup has errors.  And `save()` is
   reached from `load()` (`AutonomyViewerPanel.java:824`) - every choice under Autonomy > Configuration (...) - which
   IND9X-C4's list of doors does not name.  The warning there is the "setup was not tidied" notice, which a layout folder in
   OneDrive (a page not yet downloaded) produces.

**Siblings that do it right:** `AutoLocomotiveStatus`'s refusals of the same sends, and `AutonomyMenu` and the right-click
menu's own `AutonomyReport.show(ui, ...)` (`AutonomyMenu.java:780`, `LayoutRightclickAutonomyMenu.java:1357, 1593`).  The fix is
the same at every site: `ui` as the owner.  **Mitigations:** the hidden dialog holds the keyboard focus, so Enter or Esc
dismisses it; unticking Window Always on Top avoids it.  Neither is something an operator would guess.

**Verification request.**  Fixture: a real window, Window Always on Top ticked, maximised, track power off, a train on a
station.  Right-click the station and choose a destination.  **Proves:** the "power on to start" dialog is showing
(`Window.getWindows()`) and modal, its owner is not the main window, and a screenshot shows the main window above it.
**Refutes:** the dialog appears above the main window (the platform raises a modal above the topmost window it blocks),
in which case both catalogue rows stay C.

---

### RLU-C1 - an old-file import that fails part way leaves its new configuration chosen, and the next save makes it the one the next start resumes

| | |
|---|---|
| **Disposition** | Fixed as RLA-C3. |
| **Grade** | C - narrow: needs a file that makes `importLegacy` throw.  Needs execution. |
| **Where** | `AutonomyViewerPanel.java:1165-1180, 1296-1300` |

`importLegacyGraph` now chooses the configuration named (`activateTheConfigurationNamed`) before `importLegacy` writes.
The success path ends in `loadAfterImport`, which puts the running configuration back in the store
(`configurationToLoadAfterImport`).  The failure path - the `RuntimeException` catch, "That file could not be read as a
configuration" - returns without putting it back.  The store then says the new, half-filled configuration is chosen
while the window runs the old one.  `setActiveConfiguration` is in memory only, but the next door that writes the setup
(a Place from the diagram writes through the chosen configuration, then saves) writes into the new one and persists the
choice, and the next start resumes the half-filled configuration.  Before today a layout that had configurations
switched nothing, so a failure changed nothing.  **Mitigation:** `importLegacy` reads with `opt...` almost throughout, so
a file that gets that far and throws is rare; the Autonomy menu's heading names the configuration running.  **Fix:**
remember the chosen configuration before activating, and put it back in the catch.

**Verification request.**  Force a `RuntimeException` out of `importLegacy` (a mutation that throws at its first line
is enough) and import the MT-491 file from the menu over the live-snapshot sandbox with its configuration running.
**Proves:** after the error dialog, `getStore().getActiveConfiguration()` is the new name while
`getActiveDiagramConfiguration()` is the old.  **Refutes:** the store's choice is the old configuration.

---

### RLU-C2 - Routes > Import names a route that Yes will not arm, and the answer then says the routes armed were the ones saved with it on

| | |
|---|---|
| **Disposition** | Fixed - the question names only the routes Yes arms (saved armed, with a sensor).  Claim b1b653a0 (red first), fix babde2bb; mutation R13 red. |
| **Grade** | C - only a file with an armed route that has no sensor (UXR-B6's case). |
| **Where** | `TrainControlUI.java:25080`, `:25104`, `:25144`; `MarklinControlStation.java:4253` |

The question lists every route the file saved with automatic firing on (`routesSavedArmed`).  Yes arms only those with a
sensor (the model's `!route.hasS88()` skip), and since `6d75bfd7` the message counts those.  With A (sensor) and B (no
sensor) saved armed, the question reads "...automatic firing on: A, B.  Turn it on again for them?" and the answer "2
routes imported.  Automatic firing is on again for the 1 that were saved with it on; the others have it off."  The sentence
now says one route was saved armed, straight after a question naming two, and B's is left off with no word of why.  The
count was the defect and it is fixed; the sentence it was put into still describes the old count.  Either the question
names only the routes it can arm, or the answer names the one it could not ("B has no sensor to watch").

---

### RLU-C3 - Routes > Import's question puts every route's name and the question itself on one line, so a long list pushes "Turn it on again for them?" off the dialog

| | |
|---|---|
| **Disposition** | Fixed as RLA-C8. |
| **Grade** | C - cosmetic until the list is long.  Needs execution to find the count at which it breaks. |
| **Where** | `TrainControlUI.java:25080`; `route.ui.confirmRearmImported` in all eight bundles |

The names are joined with ", " into the middle of the first line - "Routes in this file saved with their automatic firing
on: {0}.  Turn it on again for them?" - and `JOptionPane` does not wrap a string message (nothing in `src/` sets
`OptionPane.maxCharactersPerLineCount` or overrides it).  At roughly 250 characters the line is wider than a 1920-pixel
screen; the window is clamped to the screen's left edge and the question at the end of the line is past the right edge.
The second paragraph ("No imports every route with it off...") stays visible, so the operator sees the consequence of No
without the question.  **Verification request:** an export with 15 armed routes named like "Ausfahrt Gleis 3 nach
Nord"; the question's dialog width against the screen width.  **Refutes:** the dialog fits, or wraps.

---

### RLU-C4 - Why not Moving?'s "whichever way" answer offers one remedy of the several a reachable station can need

| | |
|---|---|
| **Disposition** | Fixed - the either-way sentence names what a reachable station needs: switched on, Can Be Chosen ticked, not one where trains turn round; eight languages (3af66907). |
| **Grade** | C - a refusal whose remedy is incomplete. |
| **Where** | `Layout.java:5058`; `autolayout.why.startReachesNoStationEitherWay` in all eight bundles |

OB-299's second sentence ends "Drive it off by hand, or tick "Can Be Chosen in Full Autonomy" on a station that can be
reached from there."  Reachable means `isSendableDestination`: a station, switched on, can be chosen, and not a turning
point.  A siding whose only reachable stations are switched off, or marked as turning trains round, gets this sentence,
and the box it names is already ticked on them.  The sentence could name what `isSendableDestination` asks ("switched on,
and ticked...") or name the nearest reachable station and what it lacks.

---

### RLU-C5 - Spanish: "Déle la vuelta" in the new OB-299 sentence, where the bundle writes "dele" four times

| | |
|---|---|
| **Disposition** | Fixed - dele (3af66907). |
| **Grade** | C - spelling. |
| **Where** | `messages_es.properties:1791` |

`autolayout.why.startReachesNoStation` in Spanish ends "Déle la vuelta o sáquelo a mano."  The four other sentences that
say it (`autolayout.whyHomeStartFacingBarred`, `...MustTurn`, `autolayout.why.startFacingBarred`, `...MustTurn`) write
"dele", which is the current spelling (a one-syllable verb with a pronoun attached takes no accent).  The same slip as
GUI3-C3's single accent.

---

### RLU-C6 - a stray javadoc saying the old-file import is "Debug builds only" now heads `activateTheConfigurationNamed`

| | |
|---|---|
| **Disposition** | Fixed - the stray javadoc is gone (2fa033f3). |
| **Grade** | C - comment. |
| **Where** | `AutonomyViewerPanel.java:1105-1114` |

Two javadocs were already stacked above `importLegacyGraph`; the first ("Reads an old autonomy.json onto the squares
carrying the same sensors... Debug builds only - ... rather than a button on everybody's menu") was orphaned.  `fd3237ea`
inserted the new method between them, so the orphan now sits directly above `activateTheConfigurationNamed`'s own
javadoc, and it is untrue: the Import is on everybody's Autonomy menu and is never greyed (`AutonomyMenu.java:325-339`).
Delete it.  (The project's memory calls this "insert above the javadoc".)

---

### RLU-C7 - behaviour.md says an old file's configuration is "made the one in use"; with a configuration loaded, the door leaves that one in use

| | |
|---|---|
| **Disposition** | Fixed as RLA-C1. |
| **Grade** | C - records. |
| **Where** | `docs/reference/behaviour.md:2423-2427` |

The new paragraph: "created where there is none of that name, and made the one in use; the configuration in use before is
left as it was."  The door makes it the store's choice only while it writes; `loadAfterImport` then loads the running
configuration again, and `testAnOldFileFromTheMenuGoesIntoANewConfiguration` asserts the configuration in use is still in
use ("the import switched away from ...").  Only with nothing loaded does the imported one become the one in use.  One
clause: "chosen while the file is written, and loaded afterwards only when no configuration is".

---

### RLU-C8 - the new every-key-in-every-language test does not see the 59 message keys held in constants, so its mutation claim does not hold for Routes > Import's own messages

| | |
|---|---|
| **Disposition** | Fixed - the every-key check reads keys held in constants too, with a floor of 30 (26b18432); mutation R18 red (Danish route.infoImportedRearmed emptied). |
| **Grade** | C - a test that would not catch its own regression. |
| **Where** | `test/core/testMessageBundles.java:910` (`00a3166f`) |

`testEveryKeyAScreenAsksForIsInEveryLanguageWithAValue` finds keys by `I18n.[tf]("literal"`, the generated
`bundle.getString("literal")`, and `.form` entries.  A key passed through a constant is none of those: 59 are held as
`static final String X = "autosetup..."`/`"route..."`, among them `route.infoImportedArriveDisarmed` and
`route.infoImportedRearmed` - the two messages Routes > Import ends with - and every setup check's sentence
(`AutonomyChecks`).  Its javadoc says "give any asked-for key an empty value in one language, and this fails naming it";
an empty Danish `route.infoImportedRearmed` passes it.  (Missing keys are still caught by
`testTranslationsMatchEnglishKeySet`; only the empty-value half is blind.)  Adding a fourth road -
`static final String \w+ = "(key)"` for strings that are keys in the English bundle - closes it.  Today nothing is empty
but Italian's and Polish's plural suffix, which the test allows.

---

### RLU-C9 - a tail question waiting on the diagram can be answered while an editor is open, and the editor's Cancel then takes the answer back out of the setup

| | |
|---|---|
| **Disposition** | Fixed - the question tells its door whether an editor opened while it waited; all three doors then drop the answer, save nothing, and log why in a line of its own (eight languages).  Claim 11bf5b82 (red first: TunnelPre's road written into the setup), fix 459444f7; mutation R16 red. |
| **Grade** | C - narrow sequence; the consequence is the tail's protection.  Needs execution. |
| **Where** | `TrainControlUI.java:8163-8211` (the paste door after the question); `TailCrossedPrompt.java:660-691`; `LayoutEditor.java:603-644` |

Since FR-100 the tail question waits on the main window's diagram with the window live.  Nothing takes it down when the
layout or autonomy editor is opened, and opening one is allowed while it waits.  Every main-window door that writes the
setup refuses while an editor is open (OB-076: the editor's Cancel restores the setup as it opened), but the paste door's
continuation does not ask: a click on a lit sensor, or Not Known in the small window, writes `setArrivedAlong` into the
setup and saves (`:8185`, `:8204`) while the editor holds its snapshot.  The editor's Cancel then restores the snapshot,
which has no road, and the railway is rebuilt from it.  The road the operator gave is gone from the setup (and, depending
on what `putTheTrainsBack` carries, from the railway), so the tail walk stops at the fork again and the rail the train
actually lies on is not held.  **Mitigation:** the sequence is unlikely (answer a question left waiting under an open
editor, then Cancel the editor), and the held track is drawn in orange, so a change is visible.  **Fix options:** take a
waiting question down (as Cancel) when an editor opens, or have the door drop the late answer with its log line while an
editor is open, as TDU4-C2 drops one whose setup was replaced.

**Verification request.**  Fixture: the frozen railway; Control+V 75 407 DB (length 5) at Tunnel from the north, as
`testTheLateAnswersOfMT576ByTheirGestures` does, so the question waits; open the autonomy editor; answer by clicking
TunnelPre on the main window; Cancel the editor.  **Proves:** after the editor closes, Tunnel's setup `arrivedAlong` is
empty (and the running Point's road too) where TunnelPre's road was written.  **Refutes:** the road survives the Cancel.

---

### RLU-D1 - MT-575: with an editor open the tail question's list is owned by the editor, and no main-window door can reach the question then

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `TailCrossedPrompt.java:206-216`; `TrainControlUI.java:6724-6756` |

`openLayoutEditorWindow()` asks exactly what `isLayoutEditorOpen()` asks (`openEditor != null && isDisplayable()`), so
the diagram pick declines (TDU-C1) exactly when the list is hung from the editor - the two cannot disagree.  The only
caller that hands the question the main window from inside the editor is `GraphLocAssign.commitAndRecord` (the editor's
Place... / Edit Locomotive...); the editor's own click-to-answer list already uses `owner()` (`AutonomyEditorPanel.java:7196`).
The main-window callers (the paste door, the right-click Place) cannot run with an editor open - the tile menu is not
built (`TrainControlUI.java:4620`), the keys refuse (`:6982`), the right-click shows the editor-open item
(`LayoutRightclickAutonomyMenu.java:371`) - so moving the list to the editor cannot misplace a question asked from the
main window.  The claim `8a692f86` asserts `getOwner() == editor` and the pick not armed, which is the variable.

---

### RLU-D2 - Routes > Import's re-armed count is the model's count

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `TrainControlUI.java:25098-25105`; `MarklinControlStation.java:4250-4262` |

The door counts, per name the file saved armed, a route that exists and `isEnabled()` after a Yes.  The model arms a
route that is saved armed, has a sensor, and is the one the database holds by that name.  Every route is deleted first
and every imported one built disarmed, so `isEnabled()` after the import is exactly "armed by this import"; the two counts
differ only for a file naming one route twice, which no export produces.  The count is read on the event thread after
`BusyDialog` hands over, so it is visible there.  The question starts on No (`YES_NO_OPTS[1]`) in every language.

---

### RLU-D3 - the eight bundles agree: the same 1,831 keys, the same placeholders, ASCII only, and the two OB-299 sentences say the same thing in every language

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `src/org/traincontrol/resources/messages*.properties` |

Read in memory: all eight hold the same 1,831 keys with no duplicates; every key's placeholder set matches English in
every language; no byte above 127; no straight apostrophe in any value that has a placeholder (`MessageFormat` would eat
it); the only empty values are Italian's and Polish's `stats.ui.valuePluralSuffix`, which is allowed.  The two new
sentences carry the same two remedies in each language, with `{1}` in the second resolved to the box's own label
(`autosetup.ui.menuAutoDestination`, "Can Be Chosen in Full Autonomy").  The word for autonomy varies between the new
sentences and their neighbours in Danish, Dutch, Italian and Spanish, as it already did across those bundles; not a
finding.  `route.ui.confirmRearmImported` ends "...: {0}." in all eight, so today's switch from the count to the names reads
right everywhere.

---

### RLU-D4 - a successful old-file import leaves the configuration in use as it was

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:674-701, 746-778`; `TrainControlUI.java:2978-3014` |

The worry with choosing the named configuration before the import writes is a capture of the running railway landing in
the wrong configuration.  None does: `load()` and `captureRunningLayout()` capture into the configuration the window is
running, by name (`getActiveDiagramConfiguration()`), never into the store's choice, and `session().rebuild()` during
the import rebuilds the session's graph, not the running railway.  With a configuration running, `loadAfterImport` puts
it back as the store's choice before `load()`.  A name taken by a configuration file now says "A configuration called
... already exists", as the bundle branch does (GSP-B1).  What the success path leaves right, the failure path does not -
RLU-C1.

---

Counts: 1 A, 2 B, 9 C, 4 D.
