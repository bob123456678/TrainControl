# What the layout testing found - 2026-08-21

**Prefix for citing this document: `LT`.**

Adam ran the 41 hands-on tests in `2026-08-20-tests-to-run.md` against `cb0074ec` and wrote his results
into that file. This is the work list drawn from them: what to inspect, what to change, and what each
one is waiting on.

Numbered by severity to the convention in [README.md](README.md). A is wrong behaviour or lost work, B
is wrong in one flow, C is cosmetic or a small refinement. Feature requests that are not defects are
listed separately at the end, because they are not findings.

Every entry names the test it came from, so his words can be found in context.

---

## Status

| # | What | From | Status |
|---|---|---|---|
| LT-A1 | Ctrl+X / Ctrl+V over a diagram square does nothing - the keys act on the locomotive buttons instead | 21 | Fixed - it was the station LABEL, which resolves to no Point |
| LT-A2 | A tile moved off the graph loses its station AND its locomotive, and cannot be made a station again | 1 | Fixed - the capture pruned by Point, not by tile |
| LT-A3 | Dragging a selection LEFT removes the locomotive | 2 | Fixed - same prune as A2; confirmed by Adam on a re-run |
| LT-A4 | A locomotive's direction changes when its tile is moved to valid connected track | 1 | Reported, not corrected - a new check names the square |
| LT-A5 | Feedback events do not capture into CONDITIONS; switches do | 10 | Fixed - sensors reached the view through nothing at all |
| LT-B1 | Editing a route teleports the user to the Track Diagram tab after the sync | 6 | Fixed - the tab is put back rather than the culprit hunted |
| LT-B2 | Signal auto-detection by address does not work in conditions, only in commands | 10 | Fixed |
| LT-B3 | A paired, in-use link is drawn greyed out as if autonomy ignored it | 19 | Fixed - Adam named it exactly: the fade is the inverse of the status |
| LT-B4 | An unnamed station is a warning; it should be an error | 24 | Fixed - the javadoc always said blocking |
| LT-B5 | The route editor still syncs with the Central Station on close even with no CS routes | 4 | Fixed |
| LT-B6 | No confirmation when closing the route editor with unsaved changes | 5 | Fixed |
| LT-C1 | The signal picker window closes and reopens when a signal is removed | 23 | Fixed |
| LT-C2 | The autonomy editor's banner has an odd border and a grey artifact on its right | 18 | Fixed |
| LT-C3 | The autonomy diagram needs about one more row of scrollable height | 14 | Fixed |
| LT-C4 | Boolean-operator rows in the conditions table are greyed, which reads as disabled | 3 | Fixed |
| LT-C5 | The drag-target group is light red; it should be blue, with the selection staying red | 13 | Fixed |
| LT-A6 | Cutting a locomotive threw its protecting signals - real ironwork moved from a setup gesture | 21 re-run | Fixed |
| LT-A7 | Pasting worked over the platform but not over the station's name | 21 re-run | Fixed - twice; see below |
| LT-A8 | Cut and paste a COLUMN and the links come unpaired - and stations, names, lengths and facings go with them | 25 | Fixed - a bulk edit told the setup nothing at all |
| LT-A9 | A sensor nudged DOWN one loses its station name; down one and right one keeps it | 26 | Fixed - it landed on its own label |

## Menu work, all from tests 22 and 23

| # | What | Status |
|---|---|---|
| LT-M1 | Track diagram deep menu only: hide Show a Station Name Here, Clear This Square, the locomotive settings item, Signal Protecting This Station, and all three locomotive entries (Add to Autonomy, Move to This Station, Remove from This Square) | Fixed, then amended - see LT-M9 |
| LT-M2 | Home appears in both the track diagram's own menu and the deep menu - remove it from the top one | Fixed |
| LT-M3 | Move "{loc} Is Facing..." out of the deep menu and up to the track diagram's own menu | Fixed |
| LT-M4 | Hide "Make a One-Way Run from Here..." in the deep menu; it stays in the autonomy editor | Fixed |
| LT-M5 | Rename "Connections and Direction" to "Trains May Depart...", and move "Trains May Arrive" beside it | Fixed |
| LT-M6 | Move the link options out of Connections and into the menu itself | Fixed |
| LT-M7 | Give every right-click group of three or more a semantic heading | Fixed - station, turning, arrivals, departures and links all headed |
| LT-M8 | Selection menu: rename "Pick" to "Select", make the existing item a Deselect, and deselect automatically once a move completes | Fixed |
| LT-M9 | Put "Add a Locomotive to Autonomy..." back into the deep menu, against LT-M1 | Fixed - it is not the duplicate the other two were |
| LT-M10 | A rule under the link group, so it reads apart from the items about the square | Fixed |
| LT-M11 | Go to a link's other end from the menu, asking first when there is unsaved work | Fixed - see the note on what "save/exit checks" was taken to mean |

## LT-A7, and why it took two goes

The first fix was aimed at the wrong thing.  A caption is usually drawn on blank space beside its
platform, so the obvious explanation was that a blank square cannot report itself - which is true, and
now fixed: every label is told its own coordinates rather than reading them off whatever is drawn on it,
so a blank one answers like any other.

But it was not what Adam was hitting.  His caption sits on the station icon itself, and the name there
is painted by a JLabel of its OWN stacked on top of the square.  The square's listener is what the
keyboard reads, and it gets mouseExited the moment the pointer crosses onto the name - so the hovered
square went to null, and pasting had nothing to aim at.  The address overlay two hundred lines further
down already cascades mouseEntered for exactly this reason; the caption overlay never did.

It now reports the STATION rather than the square the text sits on, which covers both arrangements at
once - on the icon or beside it, pointing at a name means that station.

Both changes are kept.  The second is the defect Adam saw; the first is the same defect waiting on any
layout where the caption sits on blank space, which is where placeCaption puts it by default.

No test.  Every part of this is Swing listener wiring - which component receives an enter, and what it
reports - and the harness runs headless with no pointer to move.  It is confirmed by hovering.

## LT-B3: the fade was the inverse of the status, and here is why

Adam's own words were the diagnosis.  A link that is paired and in use was drawn faded; a link switched
off was drawn solid.

Neither is a decision anything made deliberately.  The wash comes from a legibility rule: before thin
direction arrows are drawn on a square, the tile art underneath is knocked back so the arrows read
against it.  A link in use carries arrows - the two-way door - so it got the wash.  A link switched off
carries none, so it did not.  The appearance therefore tracked "does this tile have arrows on it",
which on every other tile type is unrelated to status and on this one is exactly backwards from it.

Underneath that sat a second fault, which is why switching a link off appeared to do nothing at all.
annotationFor works out an `ignored` flag, refines it twice - once for a switched-off link, once for the
square-greying while a signal is being picked - and then handed the drawing `isDimmed(tile)`, which
computes the whole thing again from scratch and so threw both refinements away.  A disabled link never
greyed, and the signal-picking gesture, whose entire point is that everything which is not a signal goes
grey, greyed nothing.  Both now come from the answer the method actually worked out.

The three states now read in order: switched off is greyed and hatched, paired but unset is knocked
back, in use is solid with its door arrows on it.

## LT-A8: a bulk column or row edit told the setup nothing

The single-tile path carries a square's setup to wherever its track went - that was LT-A2's fix - but
only on a MOVE.  A whole-column or whole-row edit is not built out of single-tile moves: it copies the
line into place with the move flag off and deletes the source line afterwards.  So the call that carries
the setup was never made, for any square in the line.

Everything stayed on the column the track had walked away from: the stations, the names, the lengths,
the facings, the arrival restrictions, the link pairings and the switched-off links.  Reconcile then
found stations on squares with no sensors and dropped them for good.

Links are what shows first, which is why Adam saw it as links unpairing: a pairing is two entries, one
at each end, so moving one end leaves the FAR end pointing at a square that is now bare.  The pair looks
intact from the page you are on and is broken from the page you are not.

The other half was never handled at all: the line being written ONTO.  Its tiles are deleted and other
tiles put in their place, so what the setup says about those squares describes track that is gone - and
reconcile cannot catch this one, because it drops setup from squares that are EMPTY and these are
occupied, just by something else.  A copied column arrived carrying the station names of the column it
landed on.

Both halves now go through one rule, `planBulkLine`, which says what a line replacement moves and what it
forgets.  The four shift operations - insert a row, insert a column, and their two mirrors - were already
doing this correctly and are untouched.

### The test, and what it does not cover

`test/testLayoutEditorBulkEdits.java` - ten tests over the rule and the store: what travels, what is
forgotten, a copy versus a move, a row versus a column, the far end of a pairing, captions that move and
captions that merely point, empty squares on the line, and a square that is both vacated and landed on.
Eight of the ten fail against a build with the rule removed.

What it does not cover is the one line in `executeTool` that calls the rule - which is the shape this bug
actually took, since the rule was not wrong, it was never consulted.  Covering that means driving a real
`LayoutEditor`, which is a JFrame that wants a running `TrainControlUI`, the model, and a layout folder
behind it; the harness can run tests with a display, so it is possible, but it would also point the
session at whatever layout the preferences name, which is Adam's own.  Worth doing on a temporary copy of
the layout if this class of bug shows up again.

## LT-A9: why one direction and not the others, and why the earlier tests missed it

A platform's name is written on a separate square beside it, and "beside" is usually the square below,
because that is where there is room.  So nudging the platform down one square lands it exactly on its own
label.  Everything on a square being built over is dropped - correctly, it described track that is gone -
and a caption went with the rest.

A caption is the one thing stored per square that is not a fact ABOUT that square: it is a reference to
another square.  When the thing it refers to is what has just built over it, the reference is not stale.
It was the only copy of a name that the same gesture was carrying to safety.

It is kept where it is rather than moved somewhere clever, because a caption may sit on its own station's
square - that is how a name comes to be drawn over a platform instead of beside it.

### Why the tests that existed did not catch it

Both halves of the rule were already tested, and each fixture was built so that the other half did not
apply:

  - `testAReferenceToAMovedSquareIsRepointed` uses a platform at 14,3 with its label at 14,4 - the exact
    arrangement - and then moves the platform SIDEWAYS, to 15,3.  One square in a different direction and
    it would have failed the day it was written.
  - `testASquareLandedOnLetsGoOfWhatItKnew` covers the landing rule, but what gets landed on is a
    station, never a label.
  - `testASquareThatIsBothLandedOnAndMovingIsNotForgotten` covers sparing a square that is on its way
    somewhere - its own setup, not a reference pointing at it.

Which is the general shape: two rules, each with a test, meeting in a case neither fixture reached.  The
new tests are a matrix rather than a case for that reason - the same platform and label moved to every
neighbouring square, from every side the label might be on - because a rule that holds for seven of the
eight neighbours is exactly what was shipped.

### And "shouldn't it all be happening in one place?"

It was, and it had just stopped being.  Every editor path - single drag, group drag, the four shifts, and
now the bulk line - goes through `AutonomyCompanionStore.moveTiles`, which works out which squares are
being landed on and lets go of them itself.  But a bulk edit also clears squares that are NOT the target
of any move, so LT-A8 added a second call beside it, and the caller then had to pass the moving set by
hand so that the forgetting would spare the arrivals.  A rule restated at a call site is a rule that will
eventually be restated wrongly - which is how the last three of these started.

So it is one call again: `moveTiles(moves, builtOver)` takes both halves, derives the landing set itself,
and does them in the only order that works.  `forgetTiles` is that call with no moves.  There is now no
way for a caller to get the order or the sparing wrong, because neither is theirs to decide.

## Where else a matrix belongs

The question Adam asked after LT-A9, and the answer is that this project has exactly one shape of
settings bug and a matrix is the thing that finds it.

Every one of them has been a COLLECTION that some OPERATION did not know about.  The store keeps eleven
sets of settings keyed by square; the diagram supports a dozen structural edits.  That is a grid, the
code fills it in one cell at a time as features arrive, and the tests were written the same way - one
setting, one operation, whichever pair the author had in mind.  So the bugs live in the cells nobody
paired up: directions were left behind by every move for a release, because their keys carry a suffix;
switched-off links had to be added to the mover separately; labels were dropped by a move that landed on
them.  Each time the other ten collections were handled correctly and a test for that operation existed.

`test/testAutonomyStoreSettingsMatrix.java` is that grid: eleven settings against move, build-over,
page restore, page rename, and save-and-load.  Delete `moveMembers(disabledPortals, byKey)` from the
mover - one line, the exact shape of a real past bug - and it fails twice, while testAutonomyTileMove and
testAutonomyDiagramStore both pass.

It also carries a guard that reflects over the store's own fields and fails if a collection is neither in
the matrix nor on the list of things not keyed by square.  That is the part that matters in a year: a
matrix that can silently stop being complete is a matrix that will, and adding a twelfth setting is
exactly when nobody is thinking about the other ten operations.

### The same treatment is worth having in three more places

  - **The editor's structural edits.**  The matrix above is about the STORE.  The editor has its own
    grid - single drag, group drag, the four shifts, insert row, insert column, bulk row, bulk column,
    delete, grow, shrink - and each has to tell the store what it did.  LT-A8 was a whole column of that
    grid being blank.  Covering it properly means driving the editor, which wants a running window; the
    cheaper version is to give each operation a plan object like `planBulkLine` and sweep those.
  - **Direction against entry side.**  `directionAllows` is four directions by two sides: eight cells,
    fully enumerable, currently sampled.
  - **Tile ports.**  Already done, and worth noticing WHY: `testEveryComponentTypeIsClassified` sweeps
    `componentType.values()` rather than listing the types somebody remembered.  It is the one area of
    this codebase that has not produced a bug of this shape.

The rule of thumb: wherever a small fixed set has to be handled uniformly by another small fixed set,
enumerate both and assert every cell - and add the guard that fails when the set grows.

## LT-M11: what "must trigger save/exit checks" was taken to mean

Going to a link's other end closes this window and opens it on that page - the editor is built around one
diagram, so there is no way to change pages in place.  It now asks before doing that, when there is
unsaved work.

It asks rather than discarding.  The existing exit question offers to throw the edits away, and that
would be wrong here: pairing a link is itself an unsaved edit, so answering yes would discard the very
pairing being followed, and land on a link that is no longer paired.  Nothing is lost by the jump - the
edits live in the shared session, which is what the window that opens is looking at - so the new question
says so and answering yes simply goes.

If the intent was the stronger thing - offer to SAVE first, or refuse to leave until saved - say so and
it is a small change.

## What LT-B3 needed from Adam

A link that is paired and in use should not be shaded, and reading the code says it is not: shading is
`isDimmed`, which is a component plus `isIgnored`, and `isIgnored` is false for a LINK or TUNNEL unless
its PAGE is excluded from autonomy.  Neither disqualified nor transparent covers them.

Two states would explain what was seen, and they are different bugs:

- the link's PARTNER is on a page excluded from autonomy, in which case shading the far end is correct
  and shading this one is not;
- the link was shaded while the pairing list was open, in which case something in that flow is greying
  what it should be highlighting.

Which one it was decides the fix, so it is left open rather than guessed at.

## Not defects - feature requests, recorded not started

| # | What | From |
|---|---|---|
| LT-F1 | Double-clicking a locomotive label on the track diagram opens the placement view, when autonomy is not running | 1 |
| LT-F2 | The autonomy editor and the track diagram editor as two tabs of one window | 19 |

## Documentation

| # | What |
|---|---|
| LT-D1 | Bring tests 26-41 into the manual list itself rather than pointing at two other documents - **done**, all 41 are in one file |

---

## Confirmed clean

Tests 7, 8, 9, 12, 16, 17, 20, 41 and the second half of 10 came back with no defect. Test 11 was
verified synthetically earlier and not re-run; test 25 needs no run, since nothing is deployed yet.
Tests 23 and 24 pass apart from the items above. Test 41 - the one written because an automated test
could not see the tile regression - passed.
