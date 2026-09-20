# The main interface, 2.7.4c to 3.0.0: a read-only review

**Status:** open 2026-09-19 - B1 and B2 fixed the same day (MT-461, MT-462); C1 fixed; C2 to C5 open

**Prefix:** UIX. Checked free before use: no row in `docs/manual-tests/triage.db`'s `finding` table
begins `UIX-`, and no document in `docs/reviews/` declares it.

**Covers:** `src/org/traincontrol/gui/` as it stands at `HEAD` of `autonomy-diagram-r0` on 2026-09-19
(`8095407a` is the newest commit touching this scope), read against `docs/reference/behaviour.md`,
`docs/UI-standards.md` and the eight `src/org/traincontrol/resources/messages*.properties` bundles.
Excluded, because another reviewer has them: `AutonomyEditorPanel`, `AutonomyViewerPanel`,
`LayoutRightclickAutonomyMenu`, and everything under `automation/`, `automationui/` and `marklin/`.
The delta `git diff v2_7_4c..HEAD -- src/org/traincontrol/gui` (78 files, 77,778 insertions) chose
where to read hardest; the code was read as it is now. One of four reviewers Adam asked for on the
2.7.x-to-3.0.0 delta. Read-only: nothing was run, built, or edited; no test was executed.

The mechanical half of the pass was a script over the bundles and every `I18n.t`/`I18n.f`/`logf`/
`bundle.getString` literal in `src/` - its results are in D, and the one thing it found in scope is
C4. Everything else is from reading and tracing.

---

## A - high

| id | status | where |
|---|---|---|
| - | - | none found |

Nothing in this pass reached A. The two places I looked for it hardest - a placement door writing
before the railway had accepted the move (W21-B3's shape), and an event-thread path taking the
Layout's monitor from inside a window-`synchronized` method (OB-192's shape) - both came back clean;
see D3 and D6.

---

## B - medium

| id | status | where |
|---|---|---|
| UIX-B1 | fixed - MT-461 | `TrainControlUI.WindowClosed` (`TrainControlUI.java:19125-19214`), against `RouteEditorFrame.closeIfThrowingNothingAway` (`RouteEditorFrame.java:452-468`) |
| UIX-B2 | fixed - MT-462 | `LayoutLabel.setImage`'s accessory highlight (`LayoutLabel.java:1017-1046`) against `LayoutLabel.flashHighlight` (`1124-1150`) |

### UIX-B1: closing the application discards an open route editor's unsaved edits without asking

**Where.** `TrainControlUI.WindowClosed`, which both the title bar's X and File > Exit reach
(`exitMenuItemActionPerformed`, `TrainControlUI.java:23459`, calls it directly). Its first act
(`19137`) asks the open *layout* editor whether it may settle - `openEditor.maySettleBeforeExit()` -
and returns, cancelling the exit, if that editor's Cancel is chosen. That is OB-070, and the comment
above it says why: *"One save/discard/cancel question, asked wherever a page is left"* was enforced
at every door out of the editor except the biggest.

The route editor is the sibling that did not get it. `RouteEditorFrame` is a non-modal `JFrame`
(`RouteEditorFrame.java:56`) with `DO_NOTHING_ON_CLOSE` (`218`) and its own discard prompt on its X
and on Escape (`closeIfThrowingNothingAway`, `452-468`), which compares `stateSignature()` against
what was loaded so that *"a prompt that does not appear is worse than none - it teaches the user that
closing is safe."* `WindowClosed` never consults it: the field `routeEditor` (`599`) is referenced
for capture and for reuse (`4553`, `10671`, `21148`, `22965`) and nowhere in the exit path. After
`saveState` the handler disposes the layout editor and calls `System.exit(0)` (`19214`); the route
editor's frame dies with the process, prompt and all.

**The gesture.** Routes tab > right-click a route > Edit (or the new-route door at `22978`). Change
anything - the name, a command row, a condition. Leave the window open. File > Exit, or close the main
window. If autonomy is not running there is no dialog at all; the application exits and the edits are
gone. If autonomy IS running the "Confirm Exit" question appears, but it is about trains, not about
the route editor, and answering Yes has the same effect.

**Why B and not A.** What is lost is unsaved typing in a form, not a persisted record - the route on
disk is exactly what it was. It is the same class of loss OB-070 was raised for on the layout editor,
and it is silent, which is what makes it worth more than a C. I would not argue with A.

**Why B and not C.** Every caller does NOT guard: there are exactly two exit doors and both go through
this handler.

**How to prove it.** A test cannot run `WindowClosed` to the end without a `SecurityManager` to catch
`System.exit`. Two honest options:

1. Extract the exit-permission question - "is every open window willing to be closed?" - into one
   method that `WindowClosed` calls before `saveState`, and test THAT: open a `RouteEditorFrame` on
   the test window (`new RouteEditorFrame(ui, null, null)`, `setVisible(true)`), type into its name
   field so `stateSignature()` differs from `loadedSignature`, assert the method refuses or asks.
   Red today because there is no such method and nothing asks.
2. A manual test, tagged in `tests.md`: open a route for editing, change its name, File > Exit.
   Expected: the route editor's discard question. Observed today: the application exits.

**Siblings checked.** `LocIconCropDialog` is `APPLICATION_MODAL` (`LocIconCropDialog.java:138`), so
the exit cannot be reached while it is up. `GraphLocAssign` is a panel inside a modal `JOptionPane`.
`LayoutPopupUI` holds nothing unsaved. The route editor is the only non-modal window with unsaved
state that the exit does not ask.

### UIX-B2: a route-editor highlight that overlaps an accessory change leaves the tile drawn in the wrong position

**Where.** `LayoutLabel` has two independent "put the icon back later" mechanisms that do not know
about each other, and each restores an icon the other may have made stale.

- The **accessory highlight** (`setImage`, `LayoutLabel.java:1017-1046`): when a switch or signal
  changes state from anywhere but a click on this tile, the new image is set as `lastIcon`, a yellow
  overlay is laid over it (`1022`), and a one-shot `HIGHLIGHT_DURATION` (2250 ms, `61`) timer puts
  `lastIcon` back.
- The **flash** (`flashHighlight`, `1124-1150`): the route editor's *Highlight on Diagram* lights
  every tile carrying one of the route's addresses, on every page (`TrainControlUI.highlightAddresses`,
  `8425-8449`), for `HIGHLIGHT_HOLD_MS` = 5000 ms (`RouteEditorFrame.java:2655`). It captures the tile's
  CURRENT icon as `flashRestore` (`1141`) - once, and only when no flash is already running - and its
  own one-shot timer puts `flashRestore` back (`1148`).

Neither `setImage` nor the accessory restore touches `flashRestore` or `flashTimer`; neither flash
path touches `lastIcon`. So:

1. **Flash first, then the switch changes.** `flashRestore` holds the icon for the OLD position. The
   change runs `setImage`: `lastIcon` becomes the NEW image, the tile shows it under a yellow overlay,
   and 2250 ms later the accessory restore sets the new image. Up to 5000 ms after the flash began, the
   flash timer fires and sets `flashRestore` - the OLD position. If the change came within the first
   ~2.75 s of the flash, the accessory restore has already fired and the flash's restore is the last
   word: the tile now shows the switch the way it was before it was thrown.
2. **Switch changes first, then the flash within 2250 ms.** `flashRestore` captures the icon WITH the
   yellow overlay on it. The accessory restore fires and sets the plain image; the flash timer then
   fires and puts the overlaid icon back. The tile stays yellow.

Both persist. `updateImage(false)` returns early while `component.getImageName()` still equals
`this.imageName` (`LayoutLabel.updateImage`, and `imageName` was already advanced by the change), the
page cache re-attaches the same `LayoutLabel` objects on a page switch, and `paintCoveredMark` paints
over whatever icon is there. The wrong picture stays until that accessory changes state again.

**The gesture.** Open the route editor on a route that commands switch N. Press *Highlight on
Diagram*. Within about two seconds, throw switch N from the Keyboard tab, from another route, or on
the Central Station. Wait six seconds. The diagram draws N in the position it had BEFORE it was
thrown, and goes on drawing it that way. (The route editor is exactly where an operator is looking at
a route's switches while testing it, and `highlightAddresses` lights tiles on pages that are not
showing, so the stale tile may be on a page they turn to later.)

**Why B.** A turnout drawn in the wrong position on the operating diagram is an incorrect result the
operator acts on, in a specific configuration. It is narrow - a two-to-three second window - and it
heals on the next state change of that accessory, which is the argument for C; a stale picture of
the railway with no indication that it is stale is the argument against.

**How to prove it.** `flashHighlight(Color, int)` is public and takes its own hold, so a unit test on
one `LayoutLabel` can do it without waiting five seconds: build a viewer label for a switch on any
fixture diagram with a real icon (the tile registry the export uses builds them offscreen), call
`flashHighlight(HIGHLIGHT, 600)`, change the switch's state and call `updateImage(false)` so
`setImage` runs, sleep past both timers (the accessory restore is 2250 ms, so make the flash's hold
longer than that, or shorten `HIGHLIGHT_DURATION` behind a package-private setter), then assert
`getIcon() == lastIcon`. Today it is `flashRestore`. The fix has the same shape in both directions:
`setImage` should end a running flash (`endFlash()` already exists at `1167`, added for OB-231) before
it changes the icon, and `flashHighlight` should refuse to capture an icon while the accessory
highlight is up - or capture `lastIcon` rather than `getIcon()`.

---

## C - low

| id | status | where |
|---|---|---|
| UIX-C1 | fixed - behaviour.md section 3 corrected | `behaviour.md` section 3, the paragraph beginning *"No journey is refused on the operator's answer any more"* (lines 275-279), against `ManualReversalPrompt.forJourney` (`ManualReversalPrompt.java:97-100`) |
| UIX-C2 | fixed 2026-09-20 - the comment no longer claims the key map reaches the editor | `LayoutEditor.java:7320-7325`, the Control+B comment, against the post-processor's own guard at `TrainControlUI.java:9395` |
| UIX-C3 | fixed 2026-09-19 - the hand-written label takes the standard's blue; AutonomyBanner left for Adam | `GraphLocAssign.java:377`; `AutonomyBanner.java:211` |
| UIX-C4 | fixed 2026-09-19/20 - 237 keys out of all eight bundles (118 then 119, OP3-B1), guarded by testEveryMessageKeyIsAskedFor | the eight `messages*.properties` bundles: about 237 keys nothing asks for |
| UIX-C5 | fixed 2026-09-19 - the listeners read the modifiers, with GUX-C1 | `ui.main.tooltip.switchDir` / `switchDirFwd` (`messages.properties:1533-1534`) against `LeftArrowLetterButtonPressed` / `RightArrowLetterButtonPressed` (`TrainControlUI.java:23396-23403`) |

### UIX-C1: behaviour.md says the turning copy of a may-reverse square is never asked about; the code asks, and is tested asking

Section 3 of `behaviour.md`, under *The question, and when it is asked*, says: *"A journey ending at
the turning copy of a may-reverse station ends at a terminus, and a terminus is never asked about; a
journey ending at the plain copy needs no turn to complete, so there is nothing to decline."*

`ManualReversalPrompt.forJourney` (`97-100`) exempts a terminus destination only when the door's
policy does not ask about it - `last.getEnd().isTerminus() && !asking.asksAbout(last.getEnd())` - and
`forOperator`'s `asksAbout` answers true for every copy of a square in `session.mayTurnTiles()`,
including the turning copy, which `AutonomyBuilder` emits with `terminus: true`. So a hand-driven
journey ending on the turning copy of a may-reverse square IS asked, and the answer is honoured by
re-standing the train on the copy it did not turn on. `core.testNonReversibleTrains.
testEveryCopyOfAMayReverseSquareIsAskedAbout` (`:726`) and `core.testTheArrivalHonoursTheAnswer.
testKeepDirectionIsHonouredAtAMayTurnSquare` (`:202`) pin exactly that.

**Which is wrong: the document.** The code is Adam's ruling as quoted three paragraphs earlier in
the same section - *"May reverse should always prompt in manual mode"* - and the sentence in question
was written on 2026-09-07, before PRV-B2 (2026-09-12) made "declined on the turning copy" a coherent
outcome by re-standing the train. The paragraph's conclusion, that no journey is refused on the answer,
is still true; its reasoning about the turning copy is not.

**How to prove it.** Nothing to run: the two tests above are already green and say the opposite of
the sentence. The fix is to the paragraph.

### UIX-C2: the Control+B comment says the main window's key map reaches the editor; the map's own guard says it does not

`LayoutEditor.java:7320-7325` records why Control+M was not used for the station maximum: *"the main
window toggles the menu bar with it, and since 2026-09-11 a `KeyEventPostProcessor` delivers the main
window's shortcuts from anywhere in it that is not a text box, so that reaches this editor too."*

It does not. The post-processor (`letTheWholeWindowDriveTrains`, `TrainControlUI.java:9375-9432`) is
JVM-wide, and its third line is `if (owner == null || !SwingUtilities.isDescendingFrom(owner, this))
return false;` (`9395`) - *"THIS WINDOW, not any window"*. `isDescendingFrom` walks `getParent()`, and a
top-level frame's parent is null, so a key pressed with the focus in `LayoutEditor` (where the frame
itself owns the focus) is refused before the map is consulted. Control+M in the editor does nothing.

**What it costs.** Nothing today - B was chosen and B is free in both windows. It is a comment whose
premise is false, in a file whose comments are meant to be authoritative, and it will send the next
person who wants M looking for a collision that is not there. `regression.testNoTwoShortcutsShareAKey`
does read both handlers together, but its own javadoc says it *"deliberately does NOT require the two
windows to agree with each other"*, so it is not the reason either.

**How to prove it.** In `regression.testTheKeyMapReachesTheWholeWindow`'s harness, open a
`LayoutEditor`, give it the focus, post Control+M, and assert the main window's `toggleMenuBar`
selection is unchanged. Green today, which is the point: the comment claims it would toggle.

### UIX-C3: two hand-written 3.0.0 controls are off the interface standard, one of them in the exact colour the standard warns about

`docs/UI-standards.md` (set 2026-08-19) gives section headings and labels `0,0,155` and buttons
`Segoe UI Bold 12`, and says of the route editor that it *"was built to `Font.BOLD, 11f` and a blue
of `0,0,115` before these were written down. Both were close enough to look deliberate, and neither
was right."* Two hand-written 3.0.0 sites:

- `GraphLocAssign.java:377` (blamed 2026-09-11, three weeks AFTER the standard - the arrival-side
  combo added for REV9-B2): `arrivedFromLabel.setForeground(new Color(0, 0, 115))` - the retired
  blue, on a new label in a column whose other labels are the form's own. This is the clear one.
- `AutonomyBanner.java:211` (blamed 2026-08-17, two days BEFORE the standard was set, and not
  brought to it since - the offer button on the strip): `Segoe UI PLAIN 12` on a `JButton`; the
  standard is bold. Hand-written 3.0.0 work is what the standard's *"new work is hand-written
  panels, which is what this is for"* covers, but it was not wrong when written, so Adam may not
  want it counted.

Every other non-standard font or colour outside a `GEN-BEGIN` block in my scope predates 2.7.4c
(`AutoLocomotiveStatus`'s five `0,0,115` uses are 2023-2024; `LocomotiveSelectorItem` and
`UsageHistogram` are Tahoma from 2024) or is not text styling (`LayoutEditor`'s drag-landing border
colour, the page badge's 10pt paint, `StationCaption.PILL`, which is the diagram oval Adam chose).

**How to prove it.** Read the two lines. A test that greps hand-written code for `0, 0, 115` would
hold the line the standard drew, and would have to exempt the pre-standard files by name.

### UIX-C4: about 237 message keys, in all eight bundles, that nothing asks for

A script that collects every string literal in `src/` and `test/` and subtracts the five prefixes the
code builds keys from (`route.kind.`, `autosetup.ui.side`, `autosetup.ui.facing`,
`autolayout.ui.pathPreference`, `autolayout.ui.tooltip.pathPreference`) leaves 237 of the 2022 keys in
`messages.properties` referenced nowhere. The first hundred I read are the deleted windows' text:
`GraphViewer`'s and `GraphRightClickPointMenu`'s (`autolayout.ui.menuCreatePoint`,
`menuConnectToNode`, `menuEditEdge`, `promptSelectEdgeToDelete`, ...), `GraphEdgeEdit`'s
(`edgeConfigEdit`, `lockEdges`, `captureCommands`, ...), `GraphLocExclude`'s (`excludedLocs`,
`allowedLocsDefault`, ...) and the old text `RouteEditor`'s (`route.ui.routeCommands`,
`addSpecialCommand`, `trigS88`, ...). All eight bundles carry each of them, translated.

**What it costs.** Nothing at runtime. It is dead text in eight files that `testEveryLanguageFits`
and the translators maintain, and it hides the live keys among the dead when somebody reads the bundle
to find out what a screen says.

**How to prove it.** `core.testMessageBundles.testNothingAsksForAKeyThatIsNotThere` checks the
direction "every asked key exists"; the reverse test does not exist. Writing it will have to allow the
keys built by concatenation (the audit's dynamic-key list is in D2) - which is also why the number is
"about" 237 rather than exactly.

### UIX-C5: the direction buttons promise a Control-click behaviour they do not have

`ui.main.tooltip.switchDir` = *"Switch Direction (+Control to force reverse)"* and `switchDirFwd` =
*"... (+Control to force forward)"* are the tooltips on the `LeftArrow` and `RightArrow` buttons
(`TrainControlUI.java:14503`, `14513`). Their action listeners call
`LeftArrowLetterButtonPressed` / `RightArrowLetterButtonPressed` (`23396-23403`), both of which call
`switchDirection()` and never read the event's modifiers. The behaviour the tooltip describes exists -
on the keyboard: Control+Left is `backwardLoc()` and Control+Right is `forwardLoc()` (`18830-18850`).
A Control-click on the button toggles, like a plain click.

Pre-existing text (the same two lines are in 2.7.4c at `1212-1213`), so this is not a regression;
it is in scope as text that says something the code does not do. Either make the action listener read
`getModifiers()` the way the key handler does, or reword the tooltips to name the keys.

**How to prove it.** Manual: hold Control, click the direction button twice; the locomotive reverses
and then reverses back, where "force forward" would leave it forward.

---

## D - not defects, and checks that came back clean

| id | status | where |
|---|---|---|
| UIX-D1 | clean | the eight bundles: key sets, ASCII, placeholders, apostrophes |
| UIX-D2 | clean | every literal and dynamic key asked for in scope resolves |
| UIX-D3 | clean | no window-`synchronized` event-thread path takes the Layout's monitor (OB-192's AB-BA shape) |
| UIX-D4 | clean | timers and global listeners: all one-shot, stopped, or removed |
| UIX-D5 | clean | the whole-window key map: text components, focus, the page combo, double delivery |
| UIX-D6 | clean | the diagram's keyboard placement door honours the railway's answer and the prompts' dismissals |
| UIX-D7 | clean | `ManualReversalPrompt` against every sentence in behaviour.md section 3 that names it |
| UIX-D8 | clean | state surviving a reopen: no mutable statics but test hooks; the hovered square is cleared on rebuild |
| UIX-D9 | not a finding | `WindowClosed` starts a graceful stop before asking whether to exit |
| UIX-D10 | not a finding | the main window's bare `Delete` and `Enter` now fire from any non-text component |

**UIX-D1.** All eight bundles hold the same 2022 keys, with no duplicates, no non-ASCII byte, and the
same `{n}` set for every key in every language. No English value with a placeholder contains a lone
apostrophe. (`core.testMessageBundles` guards most of this already; the placeholder parity across
languages it does not, and that came back clean too.)

**UIX-D2.** Every literal key handed to `I18n.t`, `I18n.f`, `model.logf` and `bundle.getString`
anywhere in `src/` exists in the English bundle. Every `I18n.f` in scope passes exactly as many
arguments as its message has placeholders. The one `I18n.t`/`getString` on a message carrying `{0}` -
`About.java:64`, `ui.versionAboutString` - is formatted by hand at `About.java:19`. Of the 41 calls
whose key is built at runtime, every one in scope resolves to keys that exist (`reasonLayoutIsNotEditable`,
`whyLayoutCannotBeEdited`, `ArrivalSidePrompt.keyFor`, `AddLocomotive`'s four address-range keys,
`LayoutEditorRightclickMenu.addShift`, `AutonomyMenu`'s `editRefusal`, the two
`confirmRoute...` questions at `TrainControlUI.java:19472`, and the `pathPreference` families).

**UIX-D3.** `repaintSwitch` (`TrainControlUI.java:10636`) is `synchronized` on the window and is
called from `MarklinAccessory.setSwitched` on a driving thread that holds the Layout's monitor for the
whole of `configureAndLockPath`. So any event-thread path that takes the Layout's monitor from inside
a window-`synchronized` method is the deadlock Adam reported as OB-192. I walked the window's
`synchronized` methods (`initializeTrackDiagram`, `repaintMappings`, `repaintSwitch`, `repaintLoc`,
`renderActiveLoc`, `renderFinished`, `sliderClickedSynced`, `updateVisiblePoints`,
`repaintAutoLocList*`, `repaintLayout*`): every graph walk is on `AutonomyRenderer` or
`CoveredTrackRenderer` with only the drawing marshalled back; `repaintSwitch`'s body is one
`invokeLater`. The one that still does real work under the window monitor, `updateVisiblePoints`
(`28187`), calls `refreshCoveredTrack` (worker), `reconcileFacingWhenIdle` and `updateStationLabels`.
The latter two touch only unsynchronized `Layout` getters (`getDestination`, `getStart`,
`getReachedMilestones`, `takeReversalsOnArrival`, `getPoint`, `getPoints` - none is `synchronized`),
and `reconcileFacingWhenIdle` returns first while `Layout.isRunning()`, which counts active
locomotives and locomotive threads and so is true during a hand dispatch as well as a run. The
census in `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor` lists no `AutonomySession`
call under `faceTheWayItCameIn`, which agrees. What is left is a check-then-act gap of microseconds
between `isRunning()` and the session's write; I could not construct a driving thread that takes the
monitor inside it, and I am recording rather than claiming it.

**UIX-D4.** `raiseOnce`'s settle timer, both 50 ms focus timers, the route play button's `rest`
timer, `LayoutLabel`'s two restore timers and `LayoutGrid`'s `failsafe` and `grace` are all
`setRepeats(false)` or stopped in `discard()`; the Return Home spinner is stopped and nulled by
`showReturnHomeWorking(false)`; the ping timer is a daemon `java.util.Timer` and the comment says why.
The key-event post-processor is removed in `windowClosed` and by the public
`stopDrivingTrainsFromTheWholeWindow` for windows disposed without closing. The four
`addPropertyChangeListener` calls at `4105-4108` are on the window's own buttons.

**UIX-D5.** `letTheWholeWindowDriveTrains` refuses `JTextComponent` owners by name, refuses owners
outside this window, and refuses the event `LocControlPanelKeyPressed` has just seen. The one
component I could find where a KEY_TYPED type-ahead would double up with the map - the page combo
`LayoutList` - is `setFocusable(false)` (`14842`), as are all 26 letter buttons, the sliders and the
page arrows. `regression.testTheKeyMapReachesTheWholeWindow` holds the text-box and double-delivery
halves.

**UIX-D6.** `locomotiveGestureOnDiagram` (`TrainControlUI.java:6702-...`) refuses while autonomy is
busy or an editor holds the setup, resolves a caption's square to its station, asks
`canDepartFrom` of the square rather than of one copy (SPEC-B5), asks `ArrivalSidePrompt.forPlacement`
and `FacingPrompt.forPlacement` BEFORE moving anything and treats a dismissal of either as "do not
place" (`return true` with nothing moved), and its `moveLocomotive` is in the census with a reason. The
paste, the cut and the clear all reach `updateVisiblePoints` afterwards, which is the door
behaviour.md names for the grey and orange marks.

**UIX-D7.** `ManualReversalPrompt.ask`: Yes is `YES_NO_OPTS[0]` and the default; `reverseFor` turns
the train only on `NO` (1), so `CLOSED_OPTION` (-1), Escape and a dialog that throws all keep;
`invokeAndWait` when off the event thread. `forJourney` returns `KEEP_DIRECTION` for a
non-reversible locomotive before asking, asks only about the destination, and exempts a terminus that
is not a may-reverse square. All as section 3 states - except the one sentence in UIX-C1.

**UIX-D8.** The only non-final statics in scope are three `answeredByATest` hooks, two test hooks on
`TrainControlUI` (`unattended`, `beforeARenderIsPosted`) and the update-check strings. `LayoutEditor`
and `RouteEditorFrame` are constructed fresh on each open. The viewer's `hoveredDiagramTile` - the
OB-198 shape on the viewer side - is cleared at the top of `repaintLayout(boolean, boolean)`
(`30190`) with a comment naming exactly that defect, and by every label's `mouseExited`.

**UIX-D9.** `WindowClosed` calls `gracefulStopActionPerformed(null)` and THEN asks "Autonomy logic is
still running ... Are you sure you want to quit?", so answering No has already begun a graceful stop.
The order is 2023 code (`git blame`: `65cb156b`), not part of this delta, and the message's second
sentence - *"State will not be auto-saved unless all trains are gracefully stopped"* - reads as
describing that. Recorded because a reader of the handler will wonder; not raised.

**UIX-D10.** Because the map now reaches the whole window, a bare `Delete` with the focus on, say,
the route table clears the current locomotive button's mapping, and `Enter` there is the emergency
stop. Both are the map's own meanings and both are what Adam asked for on 2026-09-11 (*"any part of
the app should respect the key mapping"*); the tooltip `ui.main.tooltip.enterButton` says what Enter
does. Noted so nobody files it as a regression.

---

## What this pass did not cover

- Nothing was executed. Every "how to prove it" above is a test or a manual step for the next person.
- `LayoutEditor`'s selection, group clipboard, undo/redo and page operations (rename, duplicate,
  delete, combine, and the arrow re-aim) were read for the key handler and the close paths only; the
  editing logic itself, and the N8/T10 findings on it, were not re-verified.
- `RouteEditorFrame`'s condition outline, capture and validation were read for the save and close
  paths only.
- `LocIconCropDialog` beyond its modality; IPR-C1..C4 stand as they are.
- `StationCaption`, `LayoutGrid`'s caption registry and `DiagramTileRegistry` internals.
- The `GEN-BEGIN` blocks of the form-built screens, which the UI standard exempts.
- The translated text in the seven non-English bundles: the audit checked keys and placeholders, not
  meaning.
- `AutoLocomotiveStatus` beyond its manual-dispatch path and its styling.
- `PositionAwareJFrame`'s handling of a maximised window (it saves the maximised size as the normal
  size); the class predates the delta and I did not confirm the symptom.

## Seen outside my scope

- `AutonomyViewerPanel.java:1432`: `I18n.f("autosetup.ui.infoGraphExported", out.getName(),
  out.getParent())` hands two arguments to a message with one placeholder; the folder is silently
  dropped. Harmless - the sentence says the folder is about to open - and
  `testEveryFormattedMessageHasAPlaceholder` cannot see it because it checks only that `{0}` exists.
  For the autonomy-editor reviewer.
