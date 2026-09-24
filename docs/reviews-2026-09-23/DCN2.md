# Documentation validation of round 1 (DCN)

**Status:** open

**Prefix:** `DCN2`

**Reviewed:** branch `autonomy-diagram-r0` at `08a47bdd`, 2026-09-23.  Baseline: the DCN lane's review at `281c79de`, and round 1's commits `281c79de..08a47bdd` - chiefly `4fb36b4b`, `fd6341dd`, `ff129a4d`, `d64023cb`, `e2223851`, `12ed2faa`, `b4b061e0`, `997ac6d9`, `08a47bdd`.

**Method:** Reading, `git show` / `git blame` / `git log -S`, `grep`, and read-only Python: over `git show HEAD:` copies of the eight message bundles (all 53 keys `ff129a4d` translated, decoded and read side by side in eight languages), over `tests.md` / `issues.md` (disposition and Inbox recounts), over the frozen legacy `autonomy.json`, a re-implementation of `testJavadocsAreAttached.orphansIn` over `git show HEAD:` of every `src` file, and `sqlite3` on `triage.db` opened `mode=ro&immutable=1`.  Each DCN finding's disposition was walked against its own "Where" list and suggested fix; every sentence the round-1 commits added or changed in docs and comments was checked against the enforcing code at HEAD; the same files were then searched for comments the round's code changes made false (`clearedEdges`, `None IS`, `Control+L`, "four options", caller counts).  **Nothing was built or run.**  One helper script was written to the review scratchpad (`dcn2_orphans.py`, read-only); nothing else was written but this report.

## A - high

None.

## B - medium

None.  Every finding below is in text - a guide, a reference page, a comment, a tracker entry or the catalogue - and none changes what the railway does.

## C - low

### DCN2-C1 - DCN-C4 is marked Fixed, but its fourth comment was not touched, and four more comments in the same two files still state FR-061's text switch

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `LayoutEditor.java:5185-5194` (`showTextLabels`), `:5203` (`hideTextLabels` summary), `:1608-1613`, `:1855`; `AutonomyEditorPanel.java:813-815`; `LayoutGrid.java:1452` |

DCN-C4 listed six comments; `fd6341dd` rewrote five (the commit message counts GUI-C4's seven).  The one it skipped is DCN-C4's fourth item, unchanged at HEAD:

    LayoutEditor.java:5188  `toggleText` flips, which is the wrong verb for a caller that needs them ON: the autonomy
                            editor's caption switches decide what a caption SAYS, and with the text hidden they change
                            nothing the operator can see.

Since OB-272 captions are drawn by `isDrawingCaptions` whatever the text switch says (`AutonomyEditorPanel.java:6826-6835`, whose javadoc says the two are independent) - so the two javadocs still contradict each other, which is what DCN-C4 was about.  `hideTextLabels`' first line, left above its corrected body sentence, still says "**Hides the captions**, idempotently"; it hides the writing, and the captions stay.

Siblings neither lane named, all written before OB-272 and all false after it:

- `LayoutEditor.java:1608-1613` (hiding the Text Labels box in autonomy mode): "Its **None option IS this switch turned off**, so showing both would be two controls for one decision ... and **Control+L still reaches it in both**."  Since `359e5346` Labels Only is the switch turned on, and Control+L in the autonomy editor calls `cycleCaptionMode` (`:7199`), never `toggleText`.  This is the comment a reader finds first when asking why the box is hidden.
- `AutonomyEditorPanel.java:813-815`: "Seeded from what was restored, **so Control+L comes back to the mode** this window opened with".  `lastNamedCaptionMode` is read only by `textLabelsChanged` (`:6893`); Control+L steps `(index + 1) % count`.  `fd6341dd` corrected the field's own javadoc (`:6754`) to "for the text switch to come back to" and left this one two screens above it.
- "Four options that exclude one another" (`LayoutEditor.java:1855`) and "As one of four options that exclude each other" (`LayoutGrid.java:1452`) - five since OB-272; `fd6341dd` changed the same count in `applyCaptionMode`'s javadoc to five.

Also: the rewritten `textLabelsChanged` javadoc says the method is "the answer to anything else that flips" the switch.  In the autonomy editor nothing else does - the box is hidden, Control+L steps the dropdown, and `applyCaptionMode`'s own calls arrive with `shown == labelsOnly` and return (GUI-C4 said so) - so the sentence describes a caller that does not exist.  Reading and `git blame`; nothing to execute.  **Suggested fix:** reword `showTextLabels` and `hideTextLabels`' summary to OB-272's rule; rewrite `LayoutEditor.java:1608-1613` as "Labels Only IS this switch on; the dropdown shows its state" and drop the Control+L clause; `:813` "so the text switch comes back to ..."; five for four; say at `textLabelsChanged` that no caller in the autonomy editor reaches its re-selection today.

### DCN2-C2 - GUI-A1's round-1 repair made three comments in `Layout.java` false: they say `unlockPath` reads `clearedEdges` and has a "non-atomic branch"

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `Layout.java:7886-7893`, `:7979-7981`, `:8488-8492` |

Since `919e0dc8` (GUI-A1 second pass) `unlockPath` chooses how to give track back by `releasedEarly` - "WHAT WAS RELEASED, NOT WHAT THE TAIL CLEARED.  The first repair read `clearedEdges` ..." (`:4078-4082`) - and branches on `heldItAll`, not on the setting (`setAtomicRoutes` javadoc, `:10558-10563`).  `unlockPath` no longer reads `clearedEdges` anywhere (its reads are `:1022`, `getActiveAccs`, and `:8514`, the early-release loop).  Three comments from 2026-09-03 (`git blame`: `f3dc0aec`) still say the opposite:

    :7889  `unlockPath`'s non-atomic branch reads that map to know which edges the tail already gave up ...
    :7979  `unlockPath` consults `clearedEdges` for the edges the tail gave up early, so it has to run while that map
           still has them
    :8490  ... so the non-atomic branch of `unlockPath` - the one that reads this map - is the branch his railway
           actually takes.

The ordering they defend is still right, for `releasedEarly`, which both sites remove on the line after `clearedEdges` (`:8035-8036`, `:8855-8856`).  The risk is the usual one for this file: the comment is the authority, and a reader told the constraint is on `clearedEdges` may move `releasedEarly`'s removal ahead of the unlock - which would make every early-released edge look held and send the run down the "held it all" road, the double release GUI-A1 exists to prevent.  Not reachable today; reading only.  **Suggested fix:** name `releasedEarly` at all three, and drop "non-atomic branch" - the road is chosen by what the run did (`:4071-4077`).

### DCN2-C3 - DCN-C9 is marked Fixed, but five of the AutomationAPI.md sites it named are unchanged, and the graph window's colour legend sits above the new banner that was meant to fence it off

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `AutomationAPI.md:9`, `:228`, `:368-387`, `:415`, `:436`, `:447`, `:478`, `:491` |

`4fb36b4b` added two banners and rewrote `:19` and `:436`.  Still as DCN-C9 found them:

- `:228` "click on **'Validate Configuration & Open Graph UI'**" (the button is `Validate Configuration`).
- `:415` "**How much has to be measured is currently unreliable, and is being reviewed** ... Do not rely on either reading until this is settled" - settled 2026-09-06, behaviour.md §5b.  DCN-C9 asked for this paragraph to be replaced by a pointer.
- `:478` "All these settings can be changed by right-clicking on any point within the graph UI" and `:491` "right-clicking the station in the graph UI".
- `:9`, the top banner: "TrainControl still reads it, **and everything here still works**" - DCN-C9 quoted it; it is now contradicted by the file's own new banner at `:394`, which says the window instructions "no longer have anything to act on".

Not named by DCN-C9, same shape: `:368-387`, "When the 'Validate JSON' button is pressed ... a visual representation will also be shown", then the deleted window's edge and point colour legend.  The new banner (`:394`) says "The graph window described **below** was removed", and this is above it.

Two sentences the round wrote or left in the swept range: `:436`, new, "Everything described below is set in the autonomy editor on the track diagram since v3.0.0, **by right-clicking a square**" - the sections below include `atomicRoutes`, `minDelay`/`maxDelay`, `maxLocInactiveSeconds`, the function flags and timetables, which are Autonomy Settings and main-window controls, not a square's menu.  And `:447` "A length value of 0 for any edge disables this functionality and will result in instant unlocks" - since the Atomic Routes gate (behaviour.md §5d) an unmeasured edge keeps the setting on, so no instant unlock can happen.  Reading and `grep`.  **Suggested fix:** the sweep DCN-C9 asked for (`grep -n -i "graph ui\|open graph\|Validate JSON\|unreliable"`), move the `:394` banner above `:368`, "most of what is described below" at `:436`, and say at `:447` that Atomic Routes stays on while any edge is unmeasured.

### DCN2-C4 - REG-B1's documentation half: the reference page still promises a hand-picked route may finish on a switched-off station, and the new changelog line names a control that does not exist

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 (the control's name, :459) and 4132d260 |
| **Where** | `AutomationAPI.md:382`, `:459`, `:484`; `Readme.md:380` |

REG-B1 was that the only text a 2.8.1 user had read still promised switched-off stations stay reachable by hand.  `4fb36b4b` added a v3.0.0 changelog line; the same promise is still made three times in `AutomationAPI.md`, which its own banner calls "the reference for every setting the autonomy configuration holds":

    :382  A route you pick yourself ... may start from a disabled point, and may finish on one ... So disabling a
          station keeps autonomy away from it without putting it beyond your own reach
    :459  Automatically chosen paths will never include inactive points.  However, they can still be accessed in
          semi-autonomous (point-to-point) operation.
    :484  ... inactive points can be traversed in semi-autonomous mode, but not in timetable/fully autonomous mode.

At HEAD every door refuses an inactive destination and an inactive intermediate (`Layout.java:2561-2567`, `:2603-2612`); only the start is exempt.  `:484` was false even at v2.8.1 (intermediates were already refused).

The new changelog line (`Readme.md:380`) has two slips of its own:

- "leave it switched on and **untick Automatic Destination**" - there is no control by that name.  The item is **Station ▸ Can Be Chosen in Full Autonomy** (`autosetup.ui.menuAutoDestination`, and viewer-editability.md's table).  The readers are not technical, and this sentence is the remedy REG-B1 asked the changelog to give.
- "switched off now means **nothing uses it**" - a train standing on a switched-off station can still be driven out by hand, which is "the whole of the exception" in the code's own words (`Layout.java:2594-2597`).  A user with a train parked on one reads that it is stuck.

Reading only.  **Suggested fix:** correct the three AutomationAPI.md sentences to the 2026-09-06 rule; "untick Can Be Chosen in Full Autonomy (on the station's Station menu)"; "nothing may be sent to it or through it - a train already standing there can still be driven away".

### DCN2-C5 - REG-C3 is marked Fixed in `4fb36b4b` for Automation.md's prerequisite, and the prerequisite was not changed

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 (Automation.md:28) and 4132d260 (Readme.md:128) |
| **Where** | `Automation.md:28`; `Readme.md:128`; `docs/reviews-2026-09-23/REG.md:183` |

REG-C3's disposition: "Fixed - 4fb36b4b (Automation.md's prerequisite) and e2223851 (...)".  `git log -- Automation.md` shows `4fb36b4b` as the only round-1 commit to touch the file, and its ten changed lines are 166, 190, 200, 214, the routing table, 229 and 257 - not 28, which still reads "**A track diagram.** Either downloaded from your Central Station or drawn in TrainControl's own editor", the sentence REG-C3 said reads as if a Central Station diagram works.  `Readme.md:128` (also in REG-C3's Where) still offers "a JSON configuration file that can be built using the UI".  The code half (`canStartAutonomy` asks `isRemoteLayout`, `e2223851`) is in.  Outside DCN's lane but inside this validator's range (every documentation sentence of the round), and the store has REG-C3 Closed on it.  **Suggested fix:** REG-C3's own - "a copy on this computer (Layouts -> Download Central Station Layout Files)" at `:28` - and correct the disposition.

### DCN2-C6 - The round's new sentence about `Point:` labels says one typed today is shown as plain text; on a local layout it is taken over into a caption the next time the setup opens

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `Automation.md:200`; `AutomationAPI.md:511` |

Both new sentences: "A text label typed as `Point:StationName` in an older version is taken over the first time the setup opens; **one typed today is shown as plain text**."  `migrateStationLabels` runs on every `AutonomySession.open` (`AutonomySession.java:146-154`), and makes no distinction by age: every text label starting `Point:` whose name matches a station the setup knows becomes a caption and is emptied from the page (`:2630-2663`, `:2693-2713`).  `open` runs whenever the session is rebuilt - at start-up, and after every track-diagram edit (`TrainControlUI.layoutRefreshCompleteInternal`, `:23825`, `resetAutonomySession`).  So a `Point:Bahnhof` typed today in the track editor is drawn as "Bahnhof" (`LayoutGrid.java:1532-1545`) only until the editor closes; then it is a caption.  Plain text is permanent only for a name the setup does not know, or on a layout with no setup (a Central Station layout), which is what `LayoutGrid`'s own comment says that branch is for.  The behaviour is harmless - arguably what the user wanted - but the guide promises the opposite.  **Verification request (execution, optional - reading settles it):** on `test/layouts/live-snapshot` in a sandbox, add a TEXT tile labelled `Point:TunnelLongPark` to page `1 - Main`, save it, call `AutonomySession.open(pages)`; the finding is confirmed if the store has a caption at that square pointing at TunnelLongPark and the tile's label is empty, refuted if the label survives.  **Suggested fix:** "a `Point:` label naming a station is turned into a station name the next time the setup opens, whenever it was typed".

### DCN2-C7 - Automation.md's rewritten homes paragraph keeps "exactly what Return Locomotives Home would move", which the caption cannot promise for a home held to a facing

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `Automation.md:259`; `LayoutGrid.java:1421-1442`; `HomeStaging.java:2538-2550`; `TrainControlUI.java:5403-5422` |

The new text: "each station caption then names its home locomotive, in black when that locomotive is standing there and in white on dark grey when it is somewhere else.  **The white ones are exactly what `Return Locomotives Home` would move.**"  The caption's test is a name comparison, `home.equals(standing)` (`LayoutGrid.java:1436`).  Return Home's is `HomeStaging.atHome`, which since OB-282 (`74e2d8a1`, before the review) also requires the facing where the home was set facing a way: "A home set with no facing is the square, as before" - otherwise only a copy facing that way is home (`:2539-2549`).  So a train standing on its own home turned round is drawn **black** and **is** moved by Return Home, the case TDY-B1 fixed today.  Second, possibly larger gap: the caption's `standing` is `autonomyLocomotiveAt`, whose javadoc says it is "the locomotive the SETUP puts on a square, **which is not the same as the one standing there**" (`TrainControlUI.java:5403-5407`), while Return Home plans from the running layout.  The sentence was carried over from the v2.8.1 teal outline, which DCN-B1 item 2 replaced; "exactly" was the half not re-checked.  **Verification request (execution):** on `test/layouts/live-snapshot`, set BottomMainB's home to a locomotive with a facing (the TDY-B1 fixture in `core.testHomeStaging` does this), stand the train there on the other copy, and compare `LayoutGrid`'s caption colour for the square (black/white) with whether `HomeStaging` plans a move for it.  Confirmed if black and moved.  Second probe: run one autonomy journey, stop, and compare `autonomyLocomotiveAt` for the start and end squares with the running layout's `Point.getCurrentLocomotive`.  **Suggested fix:** "the white ones are away from home; Return Home also turns round a train standing at a home that was set facing the other way" - or drop "exactly".

### DCN2-C8 - DCN-C7 is marked Fixed, but the two identifiers it named are still in viewer-editability.md, and the new "Three things" is itself one short

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `docs/reference/viewer-editability.md:62`, `:64`, `:69-71`; `AutonomyEditorPanel.java:3880-3884` |

DCN-C7 found, besides the renamed menu item, "`portalDisabled` (`:62`; the store's key is `disabledLinks`) and `tileLength` (`:64`; `tileLengths`)", and its suggested fix named both.  `4fb36b4b` changed only `:69-71`; `grep portalDisabled\|tileLength` finds both identifiers at HEAD in the doc and in no `src` file.  The new sentence - "**Three** things the editor keeps to itself, because `menuOnly` gates them: Exit Guard Signal… and Entry Guard Signal… ..., and the caption items on a track square" - misses **Pick on the diagram...** in the tail-crossed menu, gated by the same flag for the reason its own comment gives: "CLICKING IS THE EDITOR'S: on the track diagram's menu this panel has no grid to be clicked on" (`AutonomyEditorPanel.java:3880-3884`, `autosetup.ui.menuPickTailCrossed`).  (`menuOnly` also withholds the locomotive items and the facing menu, but the viewer offers its own of those - section B - so "keeps to itself" is fair there.)  Reading and `grep`.  **Suggested fix:** `disabledLinks`, `tileLengths`, and "Four things ... and Pick on the diagram... for a tail".

### DCN2-C9 - DCN-C15 says MT-482 was held with a comment naming the later commits; MT-482 has no comment, and MT-293 was left fixed validated while its new comment says not to run it

| | |
|---|---|
| **Disposition** | Fixed - b53439dc |
| **Where** | `docs/manual-tests/tests.md` MT-482, MT-293, MT-023 |

1. **MT-482.**  DCN-C15's disposition: "MT-475, 481, **482**, 483 and 484 held with a comment naming the later commit on their path".  At HEAD MT-482's Comments end with "**Adam, 2026-09-23 (triage).** Works." and its run stamp; `997ac6d9` adds comments to 475, 481, 483 and 484 only.  So the ledger asks Adam for MT-482 a second time with nothing saying why - the exact failure DCN-C15 was about.  The hold itself is right: MT-482 is the grey, and the tail walk moved after 41bd1831 (`195aa1f1`, `6b7301fc`, `12ed2faa`, all descendants of `41bd1831`).
2. **MT-293** stays **fixed validated** with the new comment "this entry's Expected describes the old one, so it should not be re-run as written".  `docs/manual-tests/README.md` gives two honest states for a validated entry whose behaviour changed - back to **fixed unvalidated** with the reason, or **superseded** naming the entry that replaced it - and MT-478 (validated in the same commit) covers the same ground, Control+L and the dropdown.  "Fixed validated" is "the only state that means anything is finished", and this entry's own comment says its Expected is now wrong.
3. **MT-023**'s comment dates the rename "on your word of **2026-09-22**"; the ruling is 2026-09-23 (`eca7c42e`, and behaviour.md §7c: "Adam, 2026-09-23: *"rename it, but add Signal at the end"*").

The other two promotions check out (see D).  Reading; `git show 997ac6d9`.  **Suggested fix:** a dated comment on MT-482 naming `195aa1f1`/`6b7301fc`; MT-293 to superseded by MT-478 (the comment already says why); a one-line correction under MT-023's comment (append-only).

### DCN2-C10 - The catalogue took the round's findings from headings, so the 34 D findings three lanes wrote as bullets are not in the store

| | |
|---|---|
| **Disposition** | Fixed - the catalogue commit that follows 91daff27: every D finding written as a bullet is a heading, and catalogued |
| **Where** | `docs/manual-tests/triage.db` (`finding`); `docs/reviews-2026-09-23/DCN.md:244-259`, `TDY.md`, `AUT.md`; `docs/reference/behaviour.md:2221` |

`08a47bdd` says "64 findings in the store ... Every row has a status: Closed for fixed and for D".  The rows are right - 3,790 rows, 3,433 distinct refs, 64 from the five documents (read-only query) - but the documents hold 98 findings: GUI and REG wrote their D findings as `### X-D1` headings (6 and 10, all catalogued), while DCN (15), TDY (13) and AUT (6) wrote them as `- **X-D1 - ...**` bullets, which `catalog-findings.py`'s `HEADING` and table-row patterns do not match (`catalog-findings.py:52-53`).  `select count(*) from finding where ref like 'DCN-D%' or ref like 'TDY-D%' or ref like 'AUT-D%'` is 0.  Past rounds' D findings are in the store (1,138 rows at severity D, e.g. `DOC-D8` from a table row), so this is a format gap, not a policy.  Consequence: when the validators close this round and the lane documents are deleted, 34 checks that came back clean - the calibration data BRIEFING.md asks for - survive only in git, and behaviour.md's "All of them are in `triage.db`" stops being true for them.  None is cited from `src`, `test` or `docs` today (`grep`), so nothing fails.  **Verification request (read-only query, no execution):** the count above.  **Suggested fix:** convert the three D sections to headings or a `| **X-D1** | ... |` table before re-cataloguing, or teach the catalogue the bullet form.  (This report writes its D items as headings for that reason.)

### DCN2-C11 - Small inaccuracies in the round's other new text

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `Readme.md:379`; `docs/reference/behaviour.md:992-1003`; `Automation.md:226`; `TrainControlUI.java:9726-9741`; `PositionAwareJFrame.java:198` |

- **Changelog, Bulk Tools** (`Readme.md:379`): "can walk **a page** asking for each length that is missing - the track, each station's longest train, **and each train's own length**".  Mass Assign Train Lengths walks every train autonomy would run (`Layout.trainsWithNoLength` over `getLocomotivesToRun`), not a page's - DCN-C10's own note ("that it is not a page's question").
- **behaviour.md §5b** (DCN-C10's fix): FR-094 is described as walking "in the same prompt", but the paragraph's later sentence still reads "**The two WALKS** - lengths and maxima - share one prompt"; it is three (`massAssignTrainLengths` calls the same `askForWholeLength`).  DCN-C10's "pointer from §5d" was not added (`grep "Mass Assign Train"` finds only `:992`).
- **Automation.md routing table** (rewritten this round): nine of its ten "Setting" names are the dropdown's labels; "Whichever station has gone longest without a train" is **Least Recently Visited** in the dropdown (`messages.properties:1766`).
- **`resumesFromJsonAtStart` javadoc** (`e2223851`): "The legacy import refuses **those two keys** for exactly that reason" - the two are never named; they are `activateRoutes` and `activateRouteIDs` (`AutonomySession.java:983`).  A comment that needs another file to decode.
- **`PositionAwareJFrame.java:198`** (`fd6341dd`, removing `hasRememberedBounds`): the next member's `/**` was left at column 0.  Cosmetic; the javadoc still attaches.

Reading only.

## D - not defects (checks that came back clean)

### DCN2-D1 - DCN-B1 items 3, 4 and 5 verified; items 1 and 2 are fixed in substance (see C6 and C7 for the sentences added with them)
Item 3: the Routing Logic dropdown is `ui.main.routingLogic` on the tab `TrainControlUI.form:4255` names "Autonomy Settings".  Item 4: "saved with the autonomy configuration"; ten rows for ten enum values; the two priority exceptions match `Layout.java:4721` (RANDOM_ANY_STATION) and `:4761` (BALANCED_PRIORITY opens the band gate).  Item 5: the new sentence is the OB-278 rule; `12ed2faa` narrows spending the standing square to Points with a block, which every split square a diagram builds has (`AutonomyBuilder.blockFor`, `:507-531`) - it only changes hand-written graphs.

### DCN2-D2 - DCN-B2 verified in all five places, and no sibling is left
`lengthOf` floors an edge at 1 (`Layout.java:364-371`); behaviour.md §1a, Automation.md `:166`/`:221`, the enum javadoc and the SHORTEST/LONGEST tooltips in all eight bundles now say "a section with no length counts as one".  Each translation read decoded: same claim, `\u`-escaped.  `grep` for "measures 0 / every route measures / nothing to compare / with no lengths set" finds only `build/classes` (stale build output) and unrelated uses.

### DCN2-D3 - DCN-C1 verified
26 letter keys per page (`TrainControlUI.java:1975-2000`) x `MAX_LOC_MAPPINGS = 50` = 1,300.

### DCN2-D4 - DCN-C2 verified against the handler
Every listed key matches `LayoutEditor.formKeyPressed`: Control+G/L/D/K/H/S/E/N/B and Escape sit above the autonomy guard, Control+Y/Z/C/X/V/R/T/A/I below it (track editor only), and the autonomy-only marks are right.  Escape is `escapePressed` -> `letGoOfWhateverIsHeld` then `confirmExit` (`:6502-6507`).  Not listed, and not a defect: plus/minus step pages in both editors (FR-036), which the UI-shortcuts line covers for the main window only.

### DCN2-D5 - DCN-C5, DCN-C8 and DCN-C13 verified
behaviour.md's keyboard doors: five shortcuts, Control+N row naming `offersAStationName` (which exists, `AutonomyEditorPanel.java:5739`), dropdown order matches `CAPTIONS_NONE = 3`, `CAPTIONS_LABELS = 4`.  live-snapshot README: one "Not invariants" heading; the placements bullet survives in the second list.  behaviour.md §1a: `refreshRoutingLogicTooltip` exists (`TrainControlUI.java:11675`) and is called from the listener and the restore.

### DCN2-D6 - DCN-C12 verified, including the ratchet's numbers
One caller of `carriesAHome` (`AutonomySession.java:7655`); `writeIndexAndKeepLinksAimed` has two callers, both passing `renamed = null` (`TrainControlUI.java:26548`, `:27004`), and `LayoutPageEdit` writes the index itself after re-aiming (`:337-349`).  The two javadocs are now directly above `locomotiveInBlock` and `moveTiles`.  A read-only re-implementation of `orphansIn` over `git show HEAD:` of every `src` file gives **88**, with exactly the 21 per-file entries `ORPHANS_BY_FILE` lists (Layout 2, AutonomyCompanionStore 3).

### DCN2-D7 - DCN-C14 verified
OB-283 is the next free OB number and used nowhere else; it has Kind / Raised from / Filed; it says at the top that it is Adam's call and ends with the question he can answer.  The Inbox recount at HEAD is 65 = 38 OB + 27 FR, as open-questions.md now says.  The frozen legacy file has 24 inactive points, 18 stations, 16 reversing, as `AutonomySession`'s rewritten CARRIED_SETTINGS javadoc says.

### DCN2-D8 - DCN-C15's three promotions and DCN-C16 verified
MT-470, 476 and 478 each end with "Adam, 2026-09-23 (triage). Works." before the promoting comment; MT-470's earlier hold was for the reworded message, which is what he then saw.  Every commit the held entries name (`195aa1f1`, `6b7301fc`, `3492e38c`, `1c855483`, `59fdb67d`, `55959c9c`, `0be2bbe4`) descends from the run commit `41bd1831`.  MT-475's comment says which bullets MT-482 replaced without editing them.  Ledger arithmetic: 363 + 32 (trailing-space variant) = 395 validated, 51 superseded, 37 + 3 = 40 ledger rows, 446 of 486 - as the header says.

### DCN2-D9 - DCN-C3 is recorded as Adam's decision in the document and the store
DCN.md and `triage.db` both say "Open - Adam's decision".  Note for that decision: DCN-C3's fix said the three comments (`LayoutEditor.java:3626`, `:3747`, `AutonomyCompanionStore.java:2945`) should be reworded "either way"; they are unchanged, which is consistent with the whole finding being open, but the disposition could say the comment half does not have to wait.

### DCN2-D10 - The 53 translations of `ff129a4d` say what the English says
All 53 keys read decoded in eight languages: none adds or drops a claim; quoted control names match each bundle's own label (`ui.main.returnHome`, `ui.main.bulkEnable`, `route.ui.menuEnableAutoExecution`); the two stale ones (`infoPleaseAddLocomotivesToGraph`, `toggleVisibility`) now follow today's English.  No added bundle line in `ff129a4d`, `d64023cb` or `4fb36b4b` has a non-ASCII byte or an ASCII apostrophe.  The new `route.infoImportedArriveDisarmed` is the same sentence in all eight, with `{1}`/`{2}` in the order `MarklinControlStation.importRoutes` passes them.

### DCN2-D11 - The round's other new comments checked and accurate
`Layout.java:2546-2556` (the "every-edge rule above it" is the `isAutoRunning`-fenced loop at `:2429`); the `PathPreference.SHORTEST_LENGTH` javadoc; `FacingPrompt`'s catch comment (both callers return on null, `AutonomyEditorPanel.java:5107-5109`, `TrainControlUI.java:7231`); `AutonomyViewerPanel`'s dropped second argument (no bundle's `infoGraphExported` has `{1}`); `hasRememberedBounds` had no caller; `answersToAccessoryAddress` (`layout.switchThreeWayAddr` exists; `DEFAULT_IMPLICIT_PROTOCOL = MM2`); `RouteEditorFrame.unshaded` (its two callers are the early returns it names); `ViewListener.importRoutes` (every route is deleted, then the file's added); `isTheSquareOf`/`tileOf` (blocks are `page:x,y` or `page:x,y/Side`); `b4b061e0`'s comment (`executePath` writes both maps, `:8514`, `:8552`); `resumesFromJsonAtStart`'s "only the menu item writes this key" (one `putBoolean`, `:26327`).

### DCN2-D12 - The counts in behaviour.md and open-questions.md match the store
3,790 rows for 3,433 distinct refs, 64 of them from the five 2026-09-23 documents (read-only query) - see C10 for what those 64 leave out.

## What this pass did not cover

- **The code behind the other lanes' fixes.**  Only their comments, javadocs and documentation were checked here; whether REG-B2, REG-B3, the GUI-C fixes or AUT-C3's second pass behave is for their own validators.
- **`tests.md` in bulk.**  Only the entries the round touched, and a grep of instruction text for Control+L, Text Labels and the old menu name (MT-210, 282, 283, 292, 315, 316, 397 and 465 mention them and were not read in full except 292 and 397).
- **The two probes in C6 and C7** need execution; the facing half of C7 rests on reading `atHome` against the caption.
- **The ratchet test was not run**; its numbers were recomputed read-only (D6).
