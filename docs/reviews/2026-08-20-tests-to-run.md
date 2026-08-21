# Tests to run at the layout

Everything below needs the real railway, or a display, or both. None of it can be settled from here.

Written 2026-08-20, covering the work from `941070da` (the tile-move data-loss fix) through
`ef33f4a8` (closing the gaps between the two route editors).

---

## The one that matters most

Moving a tile used to take its track and leave the whole autonomy setup behind on the old square,
where the next reconcile dropped it. That is how the station on page 1 of the sample layout
disappeared. The fix carries everything with the tile; these two say whether it holds on your
railway rather than in a unit test.

**1. Move an S88 tile that has a station on it.** One square, any direction. Then open the autonomy
editor and look at that square: station designation, point name, facing, arrival restrictions, tile
length, and any locomotive placed there. All still present, all on the new square, none left on the
old one.

**2. The same with a group.** Pick several squares including at least two stations, drag them one
square RIGHT. Right specifically — a group dragged right has every source square landing on another
source square, which is the case that used to eat itself. Dragging left happened to work, which is
what made the same bug in the captions look intermittent.

---

## Route editor — the new one

**3. Open an existing route, save it unchanged, reopen it.** Nothing may have changed. If you have a
route whose conditions contain brackets, use that one: a condition beginning with a bracket -
`(A or B) and C` - used to come back as `A or B`, silently, with the "reads as" line showing the
short version and nothing flagged red.

**4. A three-way point.** Build a route with one, set it left, save, reopen. Still one row, still
left. Then run the route and watch the ironwork: the motor that ends up straight should move first,
and the other should follow after a pause.

**5. Type a signal's address into a switch row.** The kind should become Signal by itself, and the
setting box should offer red and green rather than straight and turn.

**6. Duplicate a row** with the mark beside the trash. The copy lands directly under it. Change its
address, save, reopen.

**7. Change a row's kind.** Every other field clears - it should not be possible to end up with a
locomotive named `3` because an accessory address stayed behind.

**8. Save a route with three things wrong in it.** One dialog listing all three, numbered. "Go back
and fix" leaves the window open on the cells it named; "Discard and close" closes it.

**9. The Test button**, against a sensor you can occupy by hand. It should agree with the railway.

**10. Capture**, into the commands and into the conditions.

**11. A Central Station route.** Everything greyed, no marks in any row, nothing typeable, no field
that takes the caret, and Escape closes it.

---

## Diagram editor

**12. Pick Several on, then Delete, Control+C, Control+X and Escape.** All four have to work while
the mode is on - the button used to take the keyboard focus with it.

**13. The orange grip** at the top right of a selection. Drag the group by it, with picking still on.

**14. `+` and `−`.** After growing, look at the new row: it should be drawn whole, with no fragment
or stray gap a moment later.

**15. Shift Down and Shift Right** from the right-click menu, then Control+Z.

---

## Autonomy editor

**16. "Show station name here"** on a blank square beside a station you have just clicked. The
station you were looking at should already be selected rather than whichever sorts first.

**17. The same over a square that already has text of your own on it.** It should ask whether to
replace it, naming the text, rather than refusing.

**18. "Why is it not moving"** on a train with several blocked destinations. The whole answer has to
be readable - it wraps now, and scrolls past a few lines.

**19. Pair a tunnel or a link.** The diagram should highlight each candidate as you scroll the list,
not only after pressing OK.

---

## Still untested, and known

- **`testAutoDetect`** needs a Central Station answering on the network. It is red here and is not a
  defect.
- **Graceful stop** now takes a moment longer and is telling the truth for the first time. Whether it
  feels like a moment or like slow is a judgement only you can make.
- **Delete, and the restored shift/insert-row operations**, have NOT had the audit that the move path
  got. All three relocate or destroy track, and everything the autonomy setup holds is keyed by
  square. Worth a pass before 3.0.0.
