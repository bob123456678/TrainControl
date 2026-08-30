# Release candidate: the comments, and one thing undo gave back

**Status:** open

**Prefix for citing these findings elsewhere:** `RC`

**Reviewed:** every file touched between `3017f719^` and `0af7bab5` - the week of work behind
`v3_0_0_rc1` - read for comments that no longer describe the code beneath them, plus the undo path in
`LayoutEditor`. Five agents, one scope each; every finding re-derived by hand before it was touched.

**Why this pass happened.** Adam tagged the commit as a release candidate and asked for in-depth review
before manual testing. Comments were given their own pass because this round moved a lot of code that
older comments were written against - and because a comment that states the opposite of the code is
read as fact by whoever fixes the next defect there.

**Adam's own comments were left alone**, on his instruction. Every attributed quote in the files below
was read as context and never edited; the checker that applies these edits refuses any anchor
containing `Adam`.

**The finding that matters most is C3.** `Layout.fromJSON` said blockedBy names are never resolved,
three lines above a comment saying they are resolved after the loop, and forty lines above the code
that resolves them. A reader trusting the first one writes a lookup that already exists, or "fixes" a
name-matching rule that was never the mechanism.

**Nine of the twelve comments were written or last edited during the week under review**, and three of
those (C10, C11, and half of C9) were written by this round's own fixes and were false the day they
landed. That is the pattern worth carrying forward: a comment describing a guard is written while the
guard is being added, and the next commit in the same round removes the guard.

---

## B - medium

### B1 - undo after shrinking a page put a station's name back outside it

| | |
|---|---|
| **Disposition** | fixed, `regression.testLayoutEditorBulkEdits` |
| **Manual test** | [MT-227](../manual-tests/tests.md#mt-227) |

`shrinkEdges` snapshots for undo *before* it drops the captions on the row and column it is about to
remove - which is LE-B1's fix, and correct on its own path. Undo then restores the components and the
captions, but the page **size** is not part of an undo snapshot, and a shrink is only offered when the
trimmed edge holds no track, so no restored component pins the size back either.

So Ctrl+Z after "-" gives the caption back and not the row it stood on: a station name that is present
in the setup, never drawn, and with no square left to click to remove it. That is precisely the state
LE-B1 was raised for, reached through undo instead of through the shrink - LE-B1 guarded the shrink
only.

**Filtered on the way back in, rather than by teaching undo about page size.** Restoring the size would
mean snapshotting it, which changes what an undo entry *is*, on the path every edit in this editor goes
through. The invariant is narrower and provable: a caption outside the page is never valid, whatever
put it there. `forgetCaptionsOutsideThePage`, called where captions are restored, covers this and
anything else that ever restores one onto a page that has since shrunk.

---

## C - low

All twelve are comments, except C12, which is a line of code that disagreed with the comment above it.
None changes behaviour.

### C1 - "no second dialog results" was made false by OB-140

`TrainControlUI:18397` reasoned that `BusyDialog` runs its work off the event thread, and off it the
wrapper is a plain guarded call. OB-140 gave `syncWithCS2` its own `BusyDialog.showUntilClosed` on
exactly that branch, so the condition the comment rests on is now the one that guarantees the opposite.

The nesting is left as it is, deliberately, and the comment now says so: the two dialogs do not
deadlock, and suppressing a nested spinner would mean `BusyDialog` deciding not to show - a mistake in
which hides *every* spinner, a worse failure than seeing two.

### C2 - the overlay strip called itself the scroll pane's column header

Three places in `AutonomyOverlayToggle`. OB-148 took it out of the scroll pane; it is a sibling above
the diagram now, which is the point of having moved it. The height reasoning still holds; the reason
given for it did not.

### C3 - `fromJSON` said blockedBy names are never resolved, and they are

`Layout.java:6649` said the names are read verbatim and that "nothing resolves them at load - the rule
asks by name at the moment it is applied". Three lines below, inside the same `if`, a second comment
says "Kept as names for now and resolved after the loop." The second is right: `Layout.java:7433` calls
`held.setBlockedBy(watching)` after resolving each name through `layout.getPoint`, logging and dropping
what matches nothing.

The tolerance the first comment describes is real, so that half was kept - it just happens at the
resolution site, by dropping, not by never looking.

### C4 - `AutonomySession.save()` promised pruning that DR-B10 made conditional

The headline is two sentences and says the method "forgets what the diagram no longer has", full stop.
DR-B10 - written for a real data-loss incident - makes that conditional on `pagesSafeToJudge()`: with a
page unloaded or numbering caught mid-renumber, nothing is pruned and a declined `Reconciliation` comes
back instead. All of that reasoning is present inline, sixty lines down. The two-sentence summary at the
top is what a reader trusts.

### C5 - `sideTowardNeighbour` claimed it "asks the graph"

Contrasted against the static `gridSideTowards` as if the two answered different questions. It calls
the private static `neighbour`, which is the same coordinate arithmetic, so the two answer identically
for any pair of squares on one page. The next paragraph of the same javadoc - portals are not reachable
here - contradicted the claim on its own. The rename is still worth having; DD-C9's reason for it, the
one-letter collision, is the true one.

### C6 - the fourth copy of a zoom claim DOC-C24 fixed three times

`LocIconCropDialog.setZoomFraction`'s `@param` said fraction 0 is "whole window filled". `MIN_ZOOM` is
0.5 and `getMinScale()` is `fitScale() * MIN_ZOOM`, so 0 is half size with white on every side. DOC-C24
corrected this exact sentence in the `zoomFraction` field doc, in `ZoomObserver.zoomChanged` and in
`getMinScale` - and missed this one. Fix one site, sweep the siblings.

### C7 - the menu separator described an order Adam changed

`AutonomyMenu` split itself into "everything above chooses which setup is in force and everything below
is housekeeping on the file that holds it". Adam moved `manageMenu` - the housekeeping - above the
separator on 2026-08-28, to sit directly under the configuration it manages. Both halves of the
sentence became false at once.

### C8 - `homeLoc` is a reference, and its javadoc said "by name"

`getHomeLoc` and `setHomeLoc` both said the assignment is made by name; the field is a `Locomotive` and
the setter takes one. The name is what gets written out and matched back on load, which is why the
trimming rules in the body matter - so the distinction is the whole point of that paragraph, and the
first line contradicted it. `setBlockedBy`'s `@param pointNames` also named an argument the method does
not have.

### C9 - two comments in one file disagreed about whether an item is disabled

`RightClickFunctionMenu`'s constructor said the departure/arrival slot item "is disabled with a note
when a DIFFERENT function holds the slot, rather than silently taking it". `autonomySlot`'s own javadoc
says the item still shows, unticked, names the function that holds the slot, and moves it when chosen.
There is no `setEnabled(false)` anywhere in the method. The code follows the javadoc.

### C10 - a guard OB-091 removed, described as still there

`restingBorder` explained that no border swap can shift the artwork because `receiveMoveEvent` returns
immediately in autonomy mode. OB-091, in this same round, gave the blue outline to the autonomy editor,
so it does not return and the border *is* swapped. The conclusion survives for a different reason -
`overlayLine` is sized to the room the resting border takes - which the three-argument javadoc directly
below already stated correctly.

### C11 - "not reachable from the menu today", passed to `setEnabled` twice

`canShiftDown` and `canShiftRight` each said they were a trap for a future caller rather than a live
predicate. `LayoutEditorRightclickMenu:476` and `:479` pass both to `addShift`, which hands the value
straight to `item.setEnabled`. They are live, and nearly always true - which is not the same as never
asked, and is what made the wrong claim plausible.

### C12 - "resolved ONCE", asked twice more three lines later

`RightClickMenuListener` introduced `subject` with a paragraph explaining that the locomotive is
resolved once as the menu is built, rather than asked again inside every listener. The line testing for
a local icon then called `ui.getButtonLocomotive(source)` twice.

**Fixed in the code, not the comment.** The comment has the intent right and every other line in the
block already follows it; this one did not. No behaviour changes - all three calls happen at build time
and return the same locomotive - so this is a C.

---

## What was checked and found sound

Recorded because a later reader should not have to re-derive it:

- `AutonomyCompanionStore`'s "eleven kept collections" counts, traced through fully - consistent.
- `AutonomyReport`'s "six doors" javadoc, against all six real `session.save()` call sites.
- Page-name versus page-ID keying claims across `AutonomySession`, `AutonomyCompanionStore` and
  `LayoutPageEdit`.
- `CSDetect`'s "only reachable hosts ever get here" retry gating, at both call sites.
- `MarklinRoute.locomotiveRenamed`'s removal - the logic really did move into `Route.namesLocomotives`
  and `locomotiveDeleted`, covering both commands and conditions.
- `NetworkProxy.ANY_RECEIVE_PORT`'s two-call-site claim, the startup latch ordering in
  `setViewListener`, and `AutonomyEditorPanel.placementChanged`, which deletes two of its own sentences
  under NR-7 for this same reason.

## Not resolved here

`StationCaption.onPill` (~:581) says a comment in `LayoutGrid` asserts the opposite of what the code
draws, and that a review confirmed the code by running it. `LayoutGrid` was in a different agent's
scope and the claim was not chased down. It is a comment, so nothing is at risk in the release; it is
worth an hour whenever `LayoutGrid` is next opened.
