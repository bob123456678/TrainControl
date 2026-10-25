# Keyboard doors

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## The autonomy editor's keyboard doors

Five shortcuts act on **the square the pointer is over**, found by one question, `LayoutEditor.hoveredSquare()`, which
answers null when the remembered label is not on the grid asked about, so after stepping to another page none of them
names a square on the page before (OB-198).

| | |
|---|---|
| **Control+S** | Names the square: the right-click menu's **Rename**. Asks `canBeNamed`, so it does nothing on plain track (MT-313). |
| **Control+E** | Opens **Segment Length** on it (FR-066). Adam picked the key: *"let's do E"*, Control+D being taken twice over. |
| **Control+B** | Opens **Maximum Train Length** on it (OB-197's free letter, for the berth the maximum belongs to). Asks the station menu's own question, so it does nothing on a square that is not a station. |
| **Control+H** | Sets the home locomotive. |
| **Control+N** | **Show a Station Name Here** on it (FR-086), asking the menu's own `offersAStationName`. |

**Control+L steps the four caption options, and the text labels are a box of their own** (FR-133, OB-272; Adam:
*"make control+L cycle the options"*, and *"move the "labels only" setting currently in the dropdown to a Text Labels
checkbox below Show Trains. Rename the current "Text Labels" heading above the dropdown to be "Station Labels".  And
change "None" in the dropdown to "Hide""*). The dropdown, headed Station Labels, is Station Names, Standing Locs, Home
Locs and Hide; the `Text Labels` box under Show Trains draws the writing on the diagram, independently. Both are
remembered between opens; a remembered Labels Only opens as Station Names, the default, with the box ticked. The
dropdown is in the same font as the Track Directions one. In the track editor Control+L is still the text switch.
`regression.testARememberedNoneOpensWithTheCaptionsOff`.

**Control+E asks the menu's own question and writes where the menu writes.** `offersALength` is `buildTileMenu`'s three
early returns in one place (a page the session knows, not a text label, not an ignored square), and the menu adds its
item inside it, so the guard and the affordance are one expression. The length lands on `squareTheLengthWouldGoOn`, the
**run leader**: a run of plain track has one square that speaks for it, and both doors write there, so measuring a run
through both does not count it twice.

**Control plus any other letter does nothing in the main window** (OB-197).
`regression.testNoTwoShortcutsShareAKey` reads both windows' handlers on every run and prints what is free in both.

## The main window's key map reaches the whole window

Adam: *"any part of the app should respect the key mapping (locomotive selector) and all
related keyboard shortcuts."*

The map is a `KeyListener`, which hears only the component holding the keyboard. Moving the focus back (`OB-170`) is
right when nothing in particular holds it (a window just opened, or alt-tabbed back to), but not while the operator is
USING what holds the focus.

So `TrainControlUI.letTheWholeWindowDriveTrains` registers a **key event post-processor**, which the focus manager runs
only for keys the focused component left unconsumed, so the precedence is the toolkit's:

- a component that wants a key keeps it: arrows still move a table's selection, space still presses the focused button,
  a list keeps its type-ahead;
- what nothing claimed reaches the map;
- **typing is stepped around by name**, because a text component claims a letter at KEY_TYPED, after this runs;
- and only in this window: a dialog's components do not descend from the frame, so its keys stay its own.

The focus-moving of `OB-170` stays; the post-processor only ever sees what the focus owner did not want.
`regression.testTheKeyMapReachesTheWholeWindow` holds the three halves of that rule.
