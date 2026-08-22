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

Moved back: station name and everything is restored, locomotive removed.

If moved so it's disconnected from the graph: everything disappears.  No longer a station (and can't be made one), no locomotive.  At least keep the locomotive on graph but not placed when this happens, but ideally we should just keep the locomotive there and in an invalid state.

If moved to a valid connected track: everything else is OK, except that the locomotive direction suddently changed.

Make double clicking a locomotive label in the track diagram open the loc placement view IF autonomy isn't running.

**2. The same with a group.** Pick several squares including at least two stations, drag them one
square RIGHT. Right specifically — a group dragged right has every source square landing on another
source square, which is the case that used to eat itself. Dragging left happened to work, which is
what made the same bug in the captions look intermittent.

Works right, but dragging left removed the locomotive.  If loc removed from graph, don't remove them from autonomy though unless we reload or explicitly delete.

Also, when selecting, add a deselect option to the right click menu (just change the one that's already there).  Change "pick" to Select.  And auto deselect once a move is complete.

---

## Route editor — the new one

**3. Open an existing route, save it unchanged, reopen it.** Nothing may have changed. If you have a
route whose conditions contain brackets, use that one: a condition beginning with a bracket -
`(A or B) and C` - used to come back as `A or B`, silently, with the "reads as" line showing the
short version and nothing flagged red.

Looks OK.  But don't grey out cells on boolean operators, since it makes it look a bit confusing.

**4. A three-way point.** Build a route with one, set it left, save, reopen. Still one row, still
left. Then run the route and watch the ironwork: the motor that ends up straight should move first,
and the other should follow after a pause.

Works.  But it still syncs with CS2 on close- is that sync still needed for consistency?  Perhaps only sync if there exist central station sourced routes.

**5. Type a signal's address into a switch row.** The kind should become Signal by itself, and the
setting box should offer red and green rather than straight and turn.

Works.  But add a "discard unsaved changes" confirmation to the new route window.

**6. Duplicate a row** with the mark beside the trash. The copy lands directly under it. Change its
address, save, reopen.

Editing a route, after sync, teleports the user to the track diagram tab.  Don't do this.  This may be due to the autonomy load, etc.

**7. Change a row's kind.** Every other field clears - it should not be possible to end up with a
locomotive named `3` because an accessory address stayed behind.

Confirmed

**8. Save a route with three things wrong in it.** One dialog listing all three, numbered. "Go back
and fix" leaves the window open on the cells it named; "Discard and close" closes it.

Works.

**9. The Test button**, against a sensor you can occupy by hand. It should agree with the railway.

Works.

**10. Capture**, into the commands and into the conditions.

Feedback events do not capture into CONDITIONS.  Switches do.

Signal auto-update does not work on conditions.

Rest works.

**11. A Central Station route.** Everything greyed, no marks in any row, nothing typeable, no field
that takes the caret, and Escape closes it.

We tested this synthetically earlier.  I can retest if you changed anything.

---

## Diagram editor

**12. Pick Several on, then Delete, Control+C, Control+X and Escape.** All four have to work while
the mode is on - the button used to take the keyboard focus with it.

OK

**13. The orange grip** at the top right of a selection. Drag the group by it, with picking still on.

OK.  Highlight the "move to' group in blue, not light red, for better clarity.  The selection itself should remain red.

**14. `+` and `−`.** After growing, look at the new row: it should be drawn whole, with no fragment
or stray gap a moment later.

OK.  In the autonomy diagram editor, we need to force the scrollable height of the diagram to be about 1 row more.  Sometimes it hides unless the window is stretched.

**15. Shift Down and Shift Right** from the right-click menu, then Control+Z.

---

## Autonomy editor

**16. "Show station name here"** on a blank square beside a station you have just clicked. The
station you were looking at should already be selected rather than whichever sorts first.

OK

**17. The same over a square that already has text of your own on it.** It should ask whether to
replace it, naming the text, rather than refusing.

OK

**18. "Why is it not moving"** on a train with several blocked destinations. The whole answer has to
be readable - it wraps now, and scrolls past a few lines.

OK.  But the bar at the top of the editor has an odd border- give it a light gray background instead.  And there is an odd gray artifact on the right side of it.

**19. Pair a tunnel or a link.** The diagram should highlight each candidate as you scroll the list,
not only after pressing OK.

OK.  Minor bug though: linked, active tile is greyed out.

Future feature request: make the autonomy editor and track diagram editor be on two tabs in one window.  Easy to flip between them if state is saved in one.


---

## Added 2026-08-21

**20. Combine Linked Pages**, on the Layouts menu, from a page that links somewhere. The new page
should show the current page and every page its links lead to, one under another with a blank row
between. Then check the autonomy editor's page list: it must be EXCLUDED, and the findings must not
have grown - if it were included, every sensor on it would become a second Point for a sensor that
already has one.

OK

**21. Control+X, Control+V and Delete on the track diagram.** Point at a station with a train on it
and press Control+X; point at another and press Control+V. Then check the same two squares in the
autonomy editor - the placement has to have moved there as well, or the next build puts the train
back where it was. With the pointer NOT over the diagram, the same keys must still cut and paste
locomotive buttons exactly as before.  

Doesn't work- only goes to the default route.

**22. A locomotive's settings from the autonomy editor's tile menu** - the same dialog the track
diagram opens. Set an arrival or departure function, then run autonomy and listen for it.

Works.  

In autonomy editor: Remove one-way run from the right-click menu and put it into the autonomy editor.

In track diagram right click autonomy deep menu only: Hide entries that manipulate the diagram, such as "show a station name here".  Hide edit locomotive, since it's already in the top menu. Hide home locomotive appears twice, remove the one in the top menu. Hide signal protecting this station. Hide clear this square. Hide place locomotive, hide place locomotive. 

In track diagram right click autonomy deep menu only: add the loc is facing menu to the parent level, and hide it in the deep menu.

---

## Added 2026-08-21, second round

**23. Two signals on one station.** On a station with an approach at each end, open "Signal Protecting
This Station", add one signal by clicking it and a second by typing its address, and check the list
shows both with the diagram outlining both behind the window. Then remove one and add it back. Save,
reopen the editor, and check both are still listed.

Seems to work. Minor- window closes and reopens on removal.

In the right click menu, change "Connections and direction" to "Trains may depart...".  Move "trains may arrive" next to it.

Move all the link options out of this submenu, into the main one. 

Give the each of the right click menu groups semantic labels where there are 3+ options.

**24. The same station on the railway.** Run autonomy and let a train stand there: BOTH signals have to
go red, and both back to green when it leaves. This is the half that cannot be tested from here - the
list can be right in the editor and only one signal actually wired into the built configuration.

Works.

We need unnamed stations (default) to throw an error, not a warning.

**25. A layout saved by the previous version.** Open it, look at a station that already had a signal
paired: it must still be paired, and the file must be unchanged until you edit something. That is the
compatibility case - one signal is still written as a bare string, and only a station with two gets an
array.

No need to test, not deployed.

---

## Added 2026-08-21, from the two reviews

Written up beside the findings they belong to in `2026-08-21-review-dispositions.md`; the tests
themselves are here, so that one file can be carried to the layout.

**26. Shift Up with the pointer on the bottom row, and Shift Left on the last column.** Both should now
do nothing at all. Then the same one row up and one column in, which should shift normally — and check
the autonomy editor afterwards to see the stations went with the track.

**27. Drag a tile onto a station square.** The station has to be gone from the autonomy editor
afterwards, not left on a square holding plain track.

**28. Cancel the diagram editor after moving a set-up station.** Both the diagram and the autonomy
setup have to be back where they started. This is the one that used to lose the station quietly.

**29. The command table's marks.** Delete removes exactly one row; the arrows move a row and leave it
moved; duplicate makes one copy.

**30. A route holding a signal command.** Open it, click the Setting cell, click away without choosing
anything, and save. The signal must still be at danger.

**31. Export a diagram as a picture, then throw a switch on that page.** The tile has to keep updating.

## Added 2026-08-21, second round

**32. Two trains running, one dispatched onto a long path.** TR-A22 in the flesh: while one locomotive
is being sent off over several edges, a train already under way has to reach and stop at its next
sensor normally. What it must NOT do is run past it. Worth doing in simulation first, then for real.

**33. Switch pages, change tile size, and toggle addresses a dozen times, then throw a switch on the
first page.** TR-A23: the tile still has to respond. If it does, the pruning is not throwing away
labels it should have kept - which is the risk of that change, not the leak it fixes.

**34. Open a popup diagram window on the page the main window is showing, close it, then throw a
switch on that page.** The same risk from the other side: a popup rebuilding a page must not evict the
main window's labels for it.

**35. Switch the Central Station off, leave TrainControl open, press Stop, then click a switch on the
diagram.** It should pause about two seconds, say the power was not confirmed, and throw the switch
anyway - and then the NEXT click should behave the same way rather than doing nothing. Before this,
the first such click stopped every tile in the application from ever responding again.

**36. Start a train and stop it by hand before it reaches its next sensor** - lift it off, or turn its
power off at the loco. After five minutes the log should name it, name the sensor, and say how long.
Nothing else should change: the train stays waiting, and autonomy carries on around it.

**37. Leave an automatic route enabled and watch the log for ten minutes.** It must say NOTHING about
its trigger sensor. A route waiting on its sensor is a route doing its job, and it does so on a
locomotive called "Dummy Loc" - if that name ever appears in the log, the advisory has leaked out of
the dispatch path into the shared wait.

---

## Added 2026-08-21, from the independent pass

Beside their findings in `2026-08-21-independent-pass.md`.

**38. Make `UIState.data` unreadable** - copy any other file over it - then start TrainControl, close it,
and look in `tc_backup`.  There should be a copy named `unreadable<timestamp>UIState.data`, and the log
should say where it went rather than "no data file found".

**39. Rename a track diagram page to something with a slash in it**, "Up/Down".  Close TrainControl and
reopen it: the page must still be there.

**40. Put a page in `gleisbild.cs2` that the folder does not hold**, then open the layout.  Every other
page has to load, the missing one has to be named in the log, and the Layouts menu must still be
pointing at your folder afterwards.

**41. A page that draws one signal or switch on several squares** - "2 - Bottom" has Signal 116 on three
- and throw that accessory.  EVERY one of those squares has to change.  Two of the three stopped
updating for a day, from a change meant to stop a memory leak, and the hands-on test written for that
change could not see it because the third still worked.

All OK.

---

## Added 2026-08-22, the layout-test round and the two features

These are the ones with the least test coverage behind them, because most of what changed is Swing
wiring - which component receives a mouse event, and what a window puts on screen - and the harness runs
headless with no pointer to move.

**42. Hover a station's NAME on the track diagram and press Control+V.**  Not the platform - the name
beside it, or drawn over it.  The train must land on that station.  Then hover a blank square that
carries no name and press it: nothing should happen and the log should say why.

**43. Move a sensor that has a name DOWN one square**, so it lands on the square its own name is written
on.  The name must survive, and be drawn over the tile it has landed on.  Then move it down one and
right one, and down one and right two: the name must survive all three.  This is the [---] bug.

**44. Cut and paste a whole COLUMN that contains a paired link**, and one that contains named stations.
The pairing must survive, from BOTH pages - go to the other end and check it still points back.  The
stations must arrive with their names, lengths and facings.  Then check the column you pasted ONTO: it
must not still be carrying the names it had before.

**45. The same for a whole ROW**, which is the same rule with the axes swapped.

**46. Switch a link off in the autonomy editor.**  It must go grey and hatched.  A link that is paired
and in use must be solid and carry its two arrows.  Before this round it was the other way round.

**47. Right-click a paired link and choose "Go to the Other End".**  It must close and reopen on that
page, at that square, flashing it.  With unsaved work, it must ask first - and answering yes must NOT
lose the pairing you just made.

**48. Double-click a train's name on the running track diagram** with autonomy stopped: the placement
view must open at that station.  With autonomy RUNNING, it must do nothing at all.

**49. Press Edit twice.**  The second press must open the same editor as the first, on the page the main
window is showing, without asking anything.  Then use the Autonomy menu's own edit item: that must take
you to the setup editor whatever you used last.

**50. The sidebar.**  With more than one page, switch pages from it: same as closing and reopening, and
it must ask about unsaved work first.  Say no: the sidebar must go back to showing the page you are
actually on.  Switch modes the same way.

**51. The sidebar with nothing to offer.**  Unload the autonomy configuration: the Autonomy Setup tab
must be greyed with a tooltip saying what to load.  Start trains: it must grey for that reason instead.
On a single-page layout, the page tabs must be gone.

**52. Open the editor on a page, resize the window, close and reopen it.**  The remembered size must not
have squeezed the diagram now that there is a sidebar taking width from it.

---

## Still untested, and known

- **`testAutoDetect`** needs a Central Station answering on the network. It is red here and is not a
  defect.
- **Graceful stop** now takes a moment longer and is telling the truth for the first time. Whether it
  feels like a moment or like slow is a judgement only you can make.
- **Delete, and the restored shift/insert-row operations**, have NOT had the audit that the move path
  got. All three relocate or destroy track, and everything the autonomy setup holds is keyed by
  square. Worth a pass before 3.0.0.
