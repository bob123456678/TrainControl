# What can be edited from the track diagram viewer

> *"to reduce complexity, would it help to remove some options from being editable from the track
> diagram viewer? assess, but leave options as is for now."* — Adam, 2026-09-09

**An assessment. Nothing here has been changed.**

**The short answer: no, removing options would not reduce the complexity that has been costing us.**
Twenty-three of the twenty-five setup gestures reachable from the viewer are not copies of the
editor's — they *are* the editor's, the same objects built by the same method, so removing them from
the viewer removes menu entries and not one line of logic. The complexity is in a different place: it
is that a gesture made from the viewer takes a path the editor's version does not, and **that path
has no capture and no consistent exit**. Four gestures are worth removing and two are worth making
identical to their editor twins; the recommendation is at the foot.

---

## Why the viewer can edit the setup at all

The viewer's right-click menu (`LayoutRightclickAutonomyMenu`) does not build its own settings. It
asks `TrainControlUI.buildAutonomyTileMenu`, which constructs an `AutonomyEditorPanel` with
`setMenuOnly(true)` and takes the menu that panel builds for the square, then hangs it under one
submenu labelled **Autonomy Setup** (`LayoutRightclickAutonomyMenu.addSetupMenu`, line 892).

That was a deliberate decision and a good one — `OB-039` was the facing menu existing **twice**, and
a redraw added to one copy left the other reporting the bug. One menu means one rule. What comes with
it is that every setting the editor offers on a square is offered on the diagram, whether or not
anybody meant it to be.

The submenu is suppressed entirely while `ui.isAutonomyBusy()`, and `buildAutonomyTileMenu` returns
null while the layout editor is open (OB-076 — the editor's Cancel restores the setup as it was when
that window opened, so an edit made elsewhere in the meantime is reverted by a Cancel in a window
about something else).

---

## Every setup-changing gesture reachable from the viewer

### A. The **Autonomy Setup** submenu — the editor's own menu, borrowed

For every row in this table the editor door is the **same object**, reached in the editor through
`AutonomyEditorPanel.tileRightClicked` with `menuOnly == false`. All of them exit through
`setupChanged()` → `rebuildRunningLayoutSoon()` → `TrainControlUI.rebuildRunningLayoutFromSetup`,
which is **a full configuration load and layout regeneration**, coalesced to one per gesture. **None
of them captures the running layout first** — see "What it costs", below.

| Menu item | What it changes |
|---|---|
| Add a Locomotive to Autonomy… | a placement, and the facing that goes with it |
| Set Home Locomotive… | the square's home locomotive |
| Rename… | the point's name |
| Station ▸ Yes / No — Only Pass Through / No — Nothing Can Pass | station designation and `active` |
| Station ▸ Can Be Chosen in Full Autonomy | `autoDestination` |
| Station ▸ Maximum Train Length | `maxTrainLength` |
| Changing Direction ▸ Never / May / Must | `canReverse`, `mustReverse` — **changes how the square SPLITS** |
| Trains May Arrive… ▸ From the N/E/S/W | barred arrival sides — **changes the split and the Point names** |
| Advanced ▸ Station Priority | `priority` |
| Advanced ▸ Speed Multiplier | `speed` |
| Advanced ▸ Excluded Locomotives | `excludedLocs` |
| Advanced ▸ Unavailable While Occupied | `blockedByPoints` (FR-001) |
| Trains May Depart… ▸ side checkboxes, per-branch radios, Every Branch | arm open/shut; exits through `annotationsChanged`, which rebuilds the railway but redraws only the arrows (OB-185) |
| Autonomy Uses This Link | `portalDisabled` — a missing edge in the graph |
| Pair with a Link… / Unpair This Link / Name… | portal pairing and naming |
| **Segment Length…** | `tileLength` — feeds the berth-length and reversal guards |
| Bulk Tools ▸ Clear All Locomotives | **every** placement in the setup |
| Bulk Tools ▸ Clear All Home Locomotives | every `home` |
| Show a Station Name Here… / Stop Showing *station* | a caption (on a text square only; the editor also offers it on a track square) |

Two things the editor keeps to itself, because `menuOnly` gates them: **Signal Protecting This
Station…**, and the caption items on a *track* square.

### B. Items outside that submenu — the viewer's own

| Menu item | What it changes | Editor door | What it does on exit |
|---|---|---|---|
| **Remove {loc}** | takes the train off the running layout **and** off the setup | yes, `AutonomyEditorPanel:1193` | `updateVisiblePoints`, `repaintAutoLocList`. **No rebuild — and no save** |
| **Place Locomotive… / Edit Locomotive…** (`GraphLocAssign`) | placement, reversibility, arrival/departure functions, preferred speed, **train length** | yes, `AutonomyEditorPanel:4293` | `commitAndRecord` (which saves), then `updateVisiblePoints`. **No `setupChanged()` — its editor twin calls it** |
| **{loc} Is Facing… ▸ N/E/S/W** | recorded facing, and moves the train onto the copy that says it | yes, same method | `placementChanged` → rebuild |
| **{loc} Is Facing… ▸ Arrived from…** | `arrivedFrom` — drives the tail-blocking walk of §5c | yes, same method | `setupChanged` → rebuild |
| **Place {loc}** (the keyboard-selected one, one click) | placement + facing | **no** — the editor's item always asks which train | `moveLocomotive`, `placeLocomotive`, `setFacing`, and a `save()` **only when a facing was resolved** |
| Start / Graceful Stop / Return Locomotives Home / → *station* / More Destinations | run trains; change no setup | no (the Autonomy tab) | — |

### C. The keyboard, over a hovered square

| Chord | What it changes | Editor door | What it does on exit |
|---|---|---|---|
| **Control+X** | lifts the standing train off the layout and the setup, onto the clipboard | **no** | `rememberPlacement` → `session.save()`, `updateVisiblePoints`. No rebuild |
| **Control+V** | places it, asking the arrival side first (`ArrivalSidePrompt.forPlacement`) | **no** | as Control+X |
| **Delete** | clears the square without remembering | **no** | as Control+X |

All three refuse, with a log line, while autonomy is busy or an editor is open.

---

## What it costs to reach a gesture from the viewer

**One thing, and it explains most of this week's defects: the viewer path never captures.**

`captureRunningLayout()` folds the running railway back into the setup. Its five callers are
`resetAutonomySession`, `openLayoutEditor`, `autonomyEditorClosed`, page rename and page delete —
**none of them is on the viewer path**. `rebuildRunningLayoutFromSetup` says so in its own comment:
*"WITHOUT capturing the running layout's state first."*

That method's justification for not capturing is that *the setup is the newer of the two, because the
gesture that asked is a setup edit*. That is true and it does not follow: the gesture can be about
something else entirely. **Setting a home from the diagram after a run rebuilds every placement from a
file that is stale about where the trains are, and puts each moved train back where it started** —
in the model and then on disk. Occupancy is `currentLoc` rather than the s88, so the next dispatch
could route into an occupied block.

What holds it together instead is narrow: `whereTheTrainsAre()` and `takeThePendingTurns()` are read
before the load and reapplied after, with a per-train exception (`placementsJustEdited`) for the
trains this gesture actually placed. That machinery exists **entirely** because the viewer door does
not capture.

### The defects it has produced

| Finding | What happened |
|---|---|
| **OB-144** | *"Critical: trains teleport"* — the original rebuild-from-stale-setup defect |
| **OB-183** | *"Changing a home can teleport a current locomotive's location"* — Adam's report; his ruling, *"the railway wins"*, is what `putTheTrainsBack` implements |
| **D2-A1 / W7-A1** | MT-337's tiebreak made the setup win always, which is right for an editor placement and wrong for every other viewer gesture: *"the track diagram viewer's right-click autonomy menu is an `AutonomyEditorPanel`, so setting a home, a priority or a caption from the diagram ends in a rebuild - and nothing on that path ever captures"* |
| **D3-C5** | pending destination turns live on the `Layout`, which the rebuild replaces — so any viewer setup gesture between a turn and its write destroyed it, and OB-189 arrives through a third door |
| **D3-A1** | the viewer's menu took the railway's monitor on the event thread; `showFor` gathers off-thread now |
| **D3-C4** | `moveLocomotive` on the event thread at the viewer's placement doors |
| **REV-B2** | §4's hand-placement rule is enforced at one door of three: the viewer's **Place {loc}** and `GraphLocAssign` never write `arrivedFrom`, so a train placed by right-click blocks nothing behind it while the same placement by Control+V does |
| **OB-076** | the editor's Cancel reverted edits made from the main window — why the whole submenu stands down while an editor is open |
| **OB-180 / OB-181 / OB-182** | the Control+X/V door: stale grey left behind, the label and the menu disagreeing about direction, and the arrival-side question asked about the wrong two sides |
| **MT-246** | a home set from the viewer did not activate Send Home and was not persisted — the report `setupChanged()` was written for |
| **MT-125** | *"Does not refresh in the viewer. Works in the autonomy editor"* |

**Read that list as a whole and the pattern is not "too many options on the viewer".** It is *one
door behaving differently from its twin*: no capture, no rebuild where the twin rebuilds, no save
where the twin saves. Removing options would not have prevented a single one of them, because every
one arrived through a gesture Adam actually uses.

---

## What cannot be removed

These have no editor door at all. Removing them loses the ability outright.

1. **Control+X / Control+V / Delete over a square.** The autonomy editor has no keyboard placement;
   the mechanism hangs off `hoveredDiagramTile`, which only the main window's tiles write. They are
   also the **only** placement doors that implement §4's hand-placement tail rule
   (`ArrivalSidePrompt.forPlacement` → `arrivedFrom`), which is what makes §5c's grey work at all.
2. **Place {loc}** — placing the keyboard-selected locomotive in one click. The editor's item always
   asks which train.
3. **Start / Graceful Stop / Return Locomotives Home / → *station* / More Destinations.** The editor
   cannot even be open while autonomy runs.
4. **Open the Full Editor…** — it exists to leave the viewer.

---

## Recommendation

**Remove four. Fix two. Keep the rest.**

### Remove — gestures that change how a square SPLITS, or that act on the whole railway

These are the ones where the viewer's lack of a capture is not a nuisance but a hazard, because the
rebuild they force does not merely regenerate the same Points — it renames them.

1. **Changing Direction ▸ Never / May / Must**
2. **Trains May Arrive… ▸ From the N/E/S/W**

   Both change how many copies a square becomes and what those copies are called. From that moment
   the running layout holds names the setup no longer knows: captions go blank, the right-click menu
   finds no Point to place a locomotive on. `autonomyEditorClosed` has a comment describing exactly
   this and it is the reason the editor rebuilds on close. The editor is where you would be if you
   were thinking about the shape of a station; the viewer is where you are while trains are standing
   on it.

3. **Bulk Tools ▸ Clear All Locomotives**
4. **Bulk Tools ▸ Clear All Home Locomotives**

   Neither is about the square under the pointer. They act on the whole setup from a menu opened by
   right-clicking one tile — and they are reachable even on a page autonomy ignores, where the tile
   menu is otherwise a single item. They also have no Cancel: MT-311 is Adam's own report that
   clearing locomotives cannot be undone.

That is four items out of twenty-five, and it removes the two categories where a viewer gesture can
leave the running railway describing a graph that no longer exists.

### Fix rather than remove — two doors that differ from their twins

5. **Place / Edit Locomotive… (`GraphLocAssign`)** does not call `setupChanged()`; its editor twin
   does. REG8-B2 fixed the *persistence* half of this asymmetry and left the rebuild half.
6. **Remove {loc}** writes `session.placeLocomotive(station, null)` and never saves —
   `AutonomySession.placeLocomotive` does not save, and the editor's copy reaches disk through
   `item()` → `refresh()`. **Place {loc}** has the same shape one step along: its `save()` sits
   inside `if (facing != null)`, so a square with no resolvable facing gets a placement in memory and
   nothing on disk.

   Both are the exact shape of MT-246 — *"it is not persisted when closing the app"* — at doors that
   were never swept.

### Keep, and this is the substantive answer to the question

Everything else. A priority, a speed multiplier, a maximum train length, an excluded locomotive, a
caption, a home, a segment length: these are things you want to change **while looking at the train
that made you want to change them**, which is the viewer. They change no Point names, so their
rebuild is a regeneration of the same graph. The complexity they add to the code is zero — they are
the same menu object either way.

**The change that would actually reduce risk is not a smaller menu.** It is giving the viewer path
the capture its editor twin gets on the way in: `openLayoutEditor` captures the running layout before
constructing the editor, and every editor gesture is therefore made against a setup that knows where
the trains are. The viewer has no such moment, and `whereTheTrainsAre` / `takeThePendingTurns` /
`placementsJustEdited` are three separate patches standing in for it. **That is one change against
five findings**, where removing options is a change against none of them.

---

*Written 2026-09-09 against `autonomy-diagram-r0`. Sources: `LayoutRightclickAutonomyMenu`,
`AutonomyEditorPanel.buildTileMenu`, `TrainControlUI.buildAutonomyTileMenu` /
`locomotiveGestureOnDiagram` / `rebuildRunningLayoutFromSetup`, `GraphLocAssign.commitAndRecord`, and
the findings named above in `docs/reviews-2026-09-09/` and `docs/manual-tests/issues.md`.*
