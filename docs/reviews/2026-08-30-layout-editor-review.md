# Layout editor: what the diagram tells the autonomy setup

**Status:** open

**Prefix for citing these findings elsewhere:** `LE`

**Reviewed:** `LayoutEditor.java`, `LayoutEditorRightclickMenu.java` and their autonomy touchpoints in
`AutonomySession` / `AutonomyCompanionStore`, at commit `34653bbb` (2026-08-30).

**Method.** Two passes. The first, one agent over the editor's autonomy touchpoints, produced A1, B1,
B2, C1 and C2. The second, one agent over those fixes and the preceding two days of commits, produced
A4, A5, A6, B3, B4 and C3-C7 - **three of them holes in the first round's own fixes**, which is the
single most useful thing either pass did. Every finding was re-derived by hand before being fixed.

**The second pass is why this document is worth reading.** A1's fix introduced two ways to destroy more
setup than the defect ever did, and a third way for its flag to go stale. A round that had stopped at
"the tests are green" would have shipped them.

**Why this pass happened.** Adam hit a save problem while adding stations to the diagram: *"the first
few times it told me there was a conflict with page 2, and the changes didn't save, even though page 2
was off."* An earlier pass answered that specific report (see `AutonomySession.placedLocomotives`,
commit `34653bbb`); this one asks the wider question he raised with it - whether anything else the
editor does fails to tell the setup about it.

---

## A - high

### A1 - a group cut and paste left the setup on the squares it emptied

| | |
|---|---|
| **Disposition** | fixed, `regression.testTheEditorTellsAutonomy` |
| **Manual test** | [MT-225](../manual-tests/tests.md#mt-225) |

`deleteSelection` calls `delete(label)`, which tells autonomy `forgetCaptionsAt` and nothing else;
`pasteSelection` called `forgetBuiltOver`, which only clears the landing squares. Nothing on that path
called `moveTile` or `moveTiles`, so the station flag, point name, maximum length, facings, barred
arrivals, protecting-signal pairing, portal partner and placed locomotive all stayed keyed to the
vacated squares. The next reconciling save prunes setup for squares with no tile, so they were then
lost permanently and reported as "squares that no longer exist".

Every sibling gesture already carried it: single-tile cut and paste through `execCopy(..., move=true)`
→ `moveTile`, a selection drag through `moveSelection` → `moveTiles`, a bulk row or column move through
`applyBulkPlan` → `moveTiles`. This is the bulk-vs-single drift that keeps producing defects in this
codebase - the same operation implemented twice, and only one copy doing all of it.

**The clipboard could not have carried it.** `CarriedTile` holds an offset within the block and the
component, and no origin - so paste had nothing to move the setup FROM. The fix adds the origins and a
flag saying whether the clipboard was cut or copied, and the flag is cleared once a paste has used it,
because cut-then-paste-twice is a move followed by a copy.

### A4 - pasting a cut block back where it came from destroyed the whole block's setup

| | |
|---|---|
| **Disposition** | fixed, `regression.testTheEditorTellsAutonomy` |
| **Manual test** | [MT-225](../manual-tests/tests.md#mt-225) |

`cutMoves` skipped a square that maps to itself, and since every square in a block shares one offset
that is all-or-nothing: pasting at the cut's own top-left produced an **empty** moves map. The empty map
fell to the `else` branch, which called `forgetBuiltOver` on what in that case are exactly the origin
squares - wiping the station flag, name, maximum length, facings, barred arrivals, protecting signal,
portal partner and placed locomotive for the entire rectangle.

Cut a set-up yard, think better of it, paste it back: the diagram is byte-identical to before and the
setup is gone, with nothing on screen to say so, so nobody presses Ctrl+Z.

`cutMoves`'s javadoc had called the identity case "at best wasted work". It was not wasted work - it
routed the gesture into the branch that forgets. **The one case the method reasoned about explicitly is
the one whose consequence it got wrong**, which is worth more than the fix: an argument for skipping
something is not an argument about what happens next.

Fixed by taking the block's own squares out of what counts as built over. Where a paste overlaps where
the block came from, those squares are the ones whose setup is being carried, and forgetting them
destroys what the move is delivering.

### A5 - a non-rectangular cut moved the setup off squares that were never cut

| | |
|---|---|
| **Disposition** | fixed, `regression.testTheEditorTellsAutonomy` |
| **Manual test** | [MT-225](../manual-tests/tests.md#mt-225) |

`copySelection` walks the bounding **box** - its own javadoc says "INCLUDING squares inside it that were
not picked" - while `deleteSelection` empties only the squares that were **picked**. A1 filled
`clipboardOrigins` from the former and assumed the latter.

Shift-click two opposite corners of a 4x4 box: two squares are emptied, fourteen are not, and all
sixteen origins were mapped. The setup came off fourteen squares that still visibly held their track.

The asymmetry is pre-existing and deliberate - copying only the picked squares would paste a shape with
holes in it - and until A1 it only duplicated track. A1 turned it into setup destruction. The cut now
records which squares it actually emptied, and only those may move.

That javadoc, the one sentence that explains the whole asymmetry, was **orphaned** (C7) - two javadoc
blocks in a row, so Java attached it to the wrong method. It is now on `bounds()`, where anybody
pairing it with a delete will meet it.

### A6 - the cut flag went stale through doors A3 did not sweep

| | |
|---|---|
| **Disposition** | fixed, `regression.testTheEditorTellsAutonomy` |
| **Manual test** | [MT-225](../manual-tests/tests.md#mt-225) |

A3 identified the invariant correctly - `clipboardWasCut` asserts "the origin squares are empty now" -
and then stood the flag down on undo and redo only. Two other doors falsify it:

- **A page or mode switch answered with Discard.** The cut pushed a snapshot, so `settleUnsavedWork`
  offers Save/Discard/Stay; Discard re-reads the pages from disk and restores the setup as at
  window-open. The cut track is back and the flag still says otherwise - the exact scenario A3's own
  comment describes, reached without touching Ctrl+Z.
- **Any later edit that refills the vacated squares.** Cut a block at (5,5), drag a set-up station onto
  (5,5), paste the block at (30,30): the station's setup is taken off (5,5). Two ordinary gestures.

**Fixed as the invariant rather than as a list of doors.** Every edit that can change the diagram calls
`snapshotLayout` first, so the flag is stood down there - which covers the doors nobody has thought of
yet. Listing them was what produced A6 in the first place.

### A2 - withdrawn, was never a defect

| | |
|---|---|
| **Disposition** | withdrawn - see *What the pass got wrong* |

The pass reported that `LayoutEditor` had no `cutSelection` / `deleteSelection` / `pasteSelection`
methods under those names, which would have made A1's whole chain unverifiable. It was raised here as a
possible fabrication and is withdrawn: the methods exist as `synchronized public boolean`, and the
grep that "disproved" them searched for `private void`. Recorded because a withdrawn finding is
calibration data, and because the mistake was the reviewer-of-the-reviewer's, not the agent's.
### A3 - the LE-A1 fix could strip the setup off squares that still held track

| | |
|---|---|
| **Disposition** | fixed, `regression.testTheEditorTellsAutonomy` |
| **Manual test** | [MT-225](../manual-tests/tests.md#mt-225) |

Found by attacking the fix rather than the defect, which is a pass every fix in this round now owes.

`clipboardWasCut` means "the squares these tiles came from are empty now, so the setup should follow
the paste". **Undo makes that false and nothing was telling it.** Cut a block, press Ctrl+Z - the track
and its setup come back - then paste anywhere, and the paste moved the setup off the restored
originals onto the copy. The squares still visibly held track and their station, name, length, facing
and locomotive were gone.

That is worse than the defect A1 fixed: there the setup was orphaned on squares that really were
empty, and a reconciling save eventually said so. Here it is taken off squares that are plainly still
there.

Cleared on undo and on redo. Not cleared on a page switch, deliberately - cutting on one page and
pasting on another is a cross-page move, and carrying the setup is the right answer there, the same
answer the single-tile move gives.


---

## B - medium

### B1 - shrinking the page stranded station captions outside it

| | |
|---|---|
| **Disposition** | fixed, `regression.testTheEditorTellsAutonomy` |
| **Manual test** | [MT-225](../manual-tests/tests.md#mt-225) |

`shrinkEdges` refuses only when the edge holds TRACK - `edgesAreEmpty` looks at grid components alone.
A caption legitimately lives on a blank square: `placeCaption` prefers "an empty square next to it",
and the bottom row and right column are blank squares like any other. After `trimEdges` the caption's
coordinates are off the page, and nothing removes it - `reconcileCaptions` only checks that the page
still has track and the station still exists, both true.

The result is the exact state `placeCaption`'s own comment records as the bug it was fixed for: shown
in the editor, which pads the grid, never shown on the diagram, and the "not shown anywhere" warning
silent because a caption does exist.

Fixed by dropping those captions before the trim rather than refusing the shrink. A caption is where a
name is DRAWN, not the name: the station keeps its name, the existing check then notices it has
nowhere to show it, and the user is offered it again. Refusing would have blocked a tidy-up over a
label that is one click to replace.

### B2 - shift down and shift right recorded an undo step for an edit they refused

| | |
|---|---|
| **Disposition** | fixed, `regression.testTheEditorTellsAutonomy` |

Both called `snapshotLayout()` before checking they had a hovered square, so invoking them with none
pushed an undo entry for an edit that never happened - which also clears the redo stack and makes the
editor ask about saving work nobody did. `shiftUp` and `shiftLeft` guarded first, which is what makes
this a drift between siblings rather than a design.

### B3 - the routing menu always ticked RANDOM

| | |
|---|---|
| **Disposition** | fixed |
| **Manual test** | [MT-226](../manual-tests/tests.md#mt-226) |

`buildPathPreferenceMenu` reads the current rule from `model.getAutoLayout()` and is called from the
`TrainControlUI` **constructor**, where `model` is still null - it is assigned afterwards, in
`setViewListener`. The menu is never rebuilt, so the tick was unconditionally RANDOM.

Set SHORTEST_LENGTH, save, restart: the railway routes by SHORTEST_LENGTH, and the one control that
reports the rule says RANDOM.

**The static version could not have had this defect.** It read the preference and pushed it, so the
tick and the value came from the same place; splitting them into "the configuration knows" and "the
menu shows" created a second answer, and the second answer was wrong. Now ticked when the menu opens,
which is the only moment the answer is both known and wanted, and which cannot go stale when a
different configuration is loaded.

### B4 - the routing choice was silently lost on upgrade

| | |
|---|---|
| **Disposition** | fixed |
| **Manual test** | [MT-226](../manual-tests/tests.md#mt-226) |

`fromJSON`'s comment claimed an absent `pathPreference` left "the behaviour those railways already
had". It did not: the old build read the choice from a java Preference at startup and applied it. That
key is gone and nothing migrated it, so anyone who had chosen a rule got RANDOM after upgrading, with
no message - and, with B3, a menu that agreed.

Carried across once, into the configuration where it now lives, and the old key removed so it cannot
come back later and overrule a choice made since.

---

## C - low

### C1 - shift up and shift left were offered where they refuse in silence

| | |
|---|---|
| **Disposition** | fixed, `regression.testTheEditorTellsAutonomy` |
| **Manual test** | [MT-225](../manual-tests/tests.md#mt-225) |

`shiftUp` returns without a word when the hovered row is the last one, `shiftLeft` likewise on the last
column, and the menu offered them regardless. Right-clicking the bottom row - the natural gesture for
"take this empty row away" - chose an item that did nothing and said nothing.

The shape was already in the same submenu: Shrink is greyed with its own refusal sentence as a tooltip
(UXR-C12). The fix greys these the same way, and does it by asking the editor's predicate rather than
restating the condition in the menu - two copies of one rule is how this happened.

### C2 - clearing a page told the setup nothing

| | |
|---|---|
| **Disposition** | fixed, `regression.testTheEditorTellsAutonomy` |

`clear()` emptied the page without telling autonomy anything - not even the `forgetCaptionsAt` that
deleting a single square does. Every station, name, length, facing, signal pairing, portal, block and
placement stayed keyed to squares that now hold nothing.

Bounded rather than severe: a reconciling save prunes them. But the non-reconciling writes on the way
out - the window captures the running layout and saves without reconciling when it closes - commit the
inconsistent state to disk first, so the page's setup outlives the diagram it describes until somebody
opens the autonomy editor and presses Save.

### C3 - a test that could not fail for the reason it named

| | |
|---|---|
| **Disposition** | fixed |

`testAGroupCutCarriesItsSetupToWhereItIsPasted` asserted that `cutSelection`'s body **contains the
identifier** `clipboardWasCut`. Setting it to `false` there - precisely "the paste cannot tell a move
from a copy", the failure its own message describes - left the test green. Its three sibling assertions
all pin an assignment; this one pinned a mention.

Worth more than its severity suggests: it was written in the same commit as the fix it guards, by
someone who had just read the code, and it still could not fail. The rule it breaks is one this folder
already records - *prove the guard actually guards*.

### C4 - two of the four shifts kept the rule the other two stopped restating

| | |
|---|---|
| **Disposition** | fixed |

C1's own principle - ask the editor's predicate rather than restate the condition - was applied to
Shift Up and Shift Left, while Shift Down and Shift Right were passed a literal `true`. B2 had just
given those two a brand-new **silent refusal**, which is the exact condition C1 exists to grey out.

Not reachable from the menu today: the popup only opens over a square, and that sets the hovered
position. A trap for the next caller rather than a live defect - but two fixes in one commit left one
submenu expressing one rule two different ways, which is what C1 was about.

### C5 - battery.sh's heap message never matched

| | |
|---|---|
| **Disposition** | fixed |

The grep was for the literal `Could not reserve enough space for object heap`. On the JDK the script
actually uses, an unsatisfiable `-Xmx` reports `Unable to allocate ... for the requested ...KB heap`,
and the other reservation message embeds the size. So the branch was dead for the very incident it was
written for - a "no heap" report that could never fire, which is the same shape as a guard that always
passes.

### C6 - buildPathPreferenceMenu's javadoc said the opposite of its body

| | |
|---|---|
| **Disposition** | fixed |

The surviving javadoc still described an application preference that "has to outlive a configuration
being reloaded", while the body's own comment said the opposite in capitals. This folder's README is
explicit that the documentation is part of the method, and a javadoc is the form a reader trusts
without checking.

### C7 - `TileSelection.bounds()`'s javadoc was attached to the wrong method

| | |
|---|---|
| **Disposition** | fixed |

Two javadoc blocks in a row, so Java attaches only the second: `handle()` got `bounds()`'s
documentation and `bounds()` had none. Fixed for more than tidiness - the orphaned paragraph is the one
sentence explaining the bounding-box-versus-picked-squares asymmetry behind **A5**, and it is now
where somebody pairing `bounds()` with a delete will read it.

---

## D - not defects

### D1 - `placedLocomotives` was the only unfiltered reader, and it is now filtered

The pass that preceded this one found `AutonomySession.placedLocomotives` reading raw configuration
JSON without dropping excluded pages, and it was fixed in `34653bbb`. This pass swept for others with
that shape and found none: every other check reads from the reducer or the graph, both built with
excluded pages already dropped.

### D2 - the twins sweep for A1, B1 and C2 came back clean

Every method in `LayoutEditor` that mutates the diagram was checked for whether it tells the setup.
`rotate` and `rotateSelection` do not, correctly - a rotation does not move a tile, so nothing is
re-keyed, and a recorded facing that the new connections cannot hold is already reported by
`facingsThatCannotBeHeld`. `growEdges` appends without shifting existing coordinates. `undo` and `redo`
restore the setup through `snapshotPage` / `restorePage`, and all three fixes here snapshot BEFORE they
forget anything, so undo restores what they dropped.

### D3 - the original save complaint was not reproduced, and its "didn't save" half is unexplained

No save path in the codebase is gated by check findings: `save`, `saveWithoutReconciling` and
`saveQuietly` were traced with all their callers, and only a genuine `IOException` can fail a write. So
an ERROR cannot refuse a save. The most likely reading of Adam's report is the OB-150 over-strict
unpaired-link rule, which was live in the 19:59 build he was editing with and fixed in `794d56b9` - but
that explains the message, not the failure to save. The layout folder is under OneDrive, whose file
locking produces exactly the "first few times" pattern; if it recurs, the log will carry the exception.

Left open deliberately rather than closed as fixed.

---

## What the pass got wrong

**One fabricated-looking citation that was not.** A1's method chain was checked by grepping for
`private void cutSelection` and finding nothing, which briefly looked like the agent inventing method
names to support a finding. The methods are `synchronized public boolean`. The lesson is the one this
folder's README already states - *verify the layer you are actually claiming about* - applied to
verifying a reviewer rather than the code: a grep that fails is evidence about the grep first.

**The pass did not distinguish "does not tell autonomy" from "need not tell autonomy".** It reported
`clear()` as PLAUSIBLE and did not mention `rotate`, which has the same shape and is correct. The
sweep in D2 is what separates them, and it is one command.

**Nothing here was exercised by running the editor.** All five fixes are asserted by bounded source
scans, because nothing in this suite constructs a `LayoutEditor`. Those scans assert that the CALL is
in the right place, not that it does the right thing - what the calls do is covered directly against
`AutonomySession`. [MT-225](../manual-tests/tests.md#mt-225) exists because that gap can only be closed
by hand.
