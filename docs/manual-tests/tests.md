# Manual tests

The single source of truth for everything that has to be checked on the real railway, or with a
display, or both. Nothing here can be settled from a unit test; that is what puts it here.

**How this file works is in [README.md](README.md).** The short version: entries are append-only and
keep their tag for life, the ledger below lists everything not yet validated, and the disposition on
each entry is set by Claude, never by the reader.

Consolidated 2026-08-22 from `docs/reviews/2026-08-20-tests-to-run.md`, which held tests 1 to 59 and
every comment on them. Nothing was dropped: each entry below carries its original wording, and Adam's
replies are under **Comments** rather than mixed into the instruction.

---

## Ledger - where your attention is needed

Everything NOT in **fixed validated**. This is the whole of the outstanding work, in tag order.

| Tag | Date | What | Disposition | From |
|---|---|---|---|---|
| [MT-001](#mt-001) | 2026-08-20 | A station moved with its tile | fixed unvalidated | LT-A2, LT-A3, LT-A4, LT-F1 |
| [MT-003](#mt-003) | 2026-08-20 | A route round-trips unchanged | fixed unvalidated | LT-C4 |
| [MT-004](#mt-004) | 2026-08-20 | A three-way point in a route | fixed unvalidated | LT-B5 |
| [MT-005](#mt-005) | 2026-08-20 | A signal address typed into a switch row | fixed unvalidated | LT-B6 |
| [MT-006](#mt-006) | 2026-08-20 | Duplicating a command row | fixed unvalidated | LT-B1 |
| [MT-010](#mt-010) | 2026-08-20 | Capture into commands and conditions | fixed unvalidated | LT-A5, LT-B2 |
| [MT-011](#mt-011) | 2026-08-20 | A Central Station route is read-only | needs test | - |
| [MT-013](#mt-013) | 2026-08-20 | The orange grip and group drag | fixed unvalidated | LT-C5 |
| [MT-014](#mt-014) | 2026-08-20 | Growing the diagram | fixed unvalidated | LT-C3 |
| [MT-015](#mt-015) | 2026-08-20 | Shift Down and Shift Right, then undo | needs test | - |
| [MT-018](#mt-018) | 2026-08-20 | Why is it not moving - readability | fixed unvalidated | LT-C2, AR-11 |
| [MT-019](#mt-019) | 2026-08-20 | Pairing a tunnel or a link | fixed unvalidated | LT-B3, LT-F2 |
| [MT-021](#mt-021) | 2026-08-21 | Control+X and Control+V on the diagram | fixed unvalidated | LT-A1, LT-A6, LT-A7 |
| [MT-022](#mt-022) | 2026-08-21 | A locomotive's settings from the tile menu | fixed unvalidated | LT-M1, LT-M2, LT-M3, LT-M4 |
| [MT-023](#mt-023) | 2026-08-21 | Two signals on one station | fixed unvalidated | LT-C1, LT-M5, LT-M6, LT-M7 |
| [MT-024](#mt-024) | 2026-08-21 | Two signals on the railway | fixed unvalidated | LT-B4 |
| [MT-025](#mt-025) | 2026-08-21 | A layout saved by the previous version | needs test | - |
| [MT-026](#mt-026) | 2026-08-21 | Shift Up and Shift Left at the edges | needs test | AR-17 |
| [MT-029](#mt-029) | 2026-08-21 | The command table's marks | fixed unvalidated | AR-18 |
| [MT-030](#mt-030) | 2026-08-21 | A route holding a signal command | fixed unvalidated | AR-19 |
| [MT-032](#mt-032) | 2026-08-21 | Two trains, one dispatched onto a long path | needs test | TR-A22 |
| [MT-035](#mt-035) | 2026-08-21 | The Central Station switched off mid-session | needs test | - |
| [MT-036](#mt-036) | 2026-08-21 | A train stopped by hand | needs test | - |
| [MT-037](#mt-037) | 2026-08-21 | An automatic route says nothing about its trigger | needs test | AR-20 |
| [MT-038](#mt-038) | 2026-08-21 | An unreadable UIState.data is kept | needs test | IP-*, AR-21 |
| [MT-039](#mt-039) | 2026-08-21 | A page named with a slash | fixed unvalidated | AR-22 |
| [MT-040](#mt-040) | 2026-08-21 | A page the folder does not hold | fixed unvalidated | AR-23 |
| [MT-042](#mt-042) | 2026-08-22 | Hovering a station's name to paste | needs test | LT-A7 |
| [MT-043](#mt-043) | 2026-08-22 | A sensor nudged onto its own label | needs test | LT-A9 |
| [MT-044](#mt-044) | 2026-08-22 | Cut and paste a whole column | needs test | LT-A8, FR-A1 |
| [MT-045](#mt-045) | 2026-08-22 | The same for a whole row | needs test | LT-A8, FR-A1 |
| [MT-046](#mt-046) | 2026-08-22 | A link switched off goes grey | needs test | LT-B3 |
| [MT-047](#mt-047) | 2026-08-22 | Go to a link's other end | needs test | LT-M11 |
| [MT-048](#mt-048) | 2026-08-22 | Double-click a train's name | needs test | LT-F1 |
| [MT-049](#mt-049) | 2026-08-22 | The Edit button no longer asks | needs test | LT-F2 |
| [MT-050](#mt-050) | 2026-08-22 | The sidebar | needs test | LT-F2 |
| [MT-051](#mt-051) | 2026-08-22 | The sidebar with nothing to offer | needs test | LT-F2 |
| [MT-052](#mt-052) | 2026-08-22 | A remembered window size with a sidebar | needs test | FR-D2 |
| [MT-053](#mt-053) | 2026-08-22 | Edit Locomotive opens its dialog | needs test | AR-1, AR-2 |
| [MT-054](#mt-054) | 2026-08-22 | Combine Linked Pages appears once | needs test | AR-3, AR-4 |
| [MT-055](#mt-055) | 2026-08-22 | Manage Pages and Edit Layout Page | needs test | AR-5 |
| [MT-056](#mt-056) | 2026-08-22 | The sidebar with a long page name | needs test | AR-6, AR-7, AR-8 |
| [MT-057](#mt-057) | 2026-08-22 | A train marker and its name | needs test | AR-13, AR-14 |
| [MT-058](#mt-058) | 2026-08-22 | Show autonomy hides the names | needs test | AR-15 |
| [MT-059](#mt-059) | 2026-08-22 | Why is it not moving, on an addressed layout | needs test | AR-12 |
| [MT-060](#mt-060) | 2026-08-22 | testAutoDetect needs a Central Station | needs test | hands-on testing |
| [MT-061](#mt-061) | 2026-08-22 | Graceful stop timing | needs test | hands-on testing |
| [MT-062](#mt-062) | 2026-08-22 | Delete, shift and insert have not had the move audit | needs test | hands-on testing |
| [MT-063](#mt-063) | 2026-08-22 | A second copy of TrainControl says so | needs test | AR-16 |

Everything else - 14 of 63 - is **fixed validated** and needs nothing from you unless the
area changes again.

---

## The tests


<a id="mt-001"></a>

### MT-001 - 2026-08-20 - A station moved with its tile

**Disposition:** fixed unvalidated  
**From:** LT-A2, LT-A3, LT-A4, LT-F1  
**Written:** 2026-08-20

**What to do.** Move an S88 tile that has a station on it. One square, any direction. Then open the autonomy
editor and look at that square: station designation, point name, facing, arrival restrictions, tile
length, and any locomotive placed there. All still present, all on the new square, none left on the
old one.

#### Comments

Moved back: station name and everything is restored, locomotive removed.

If moved so it's disconnected from the graph: everything disappears.  No longer a station (and can't be made one), no locomotive.  At least keep the locomotive on graph but not placed when this happens, but ideally we should just keep the locomotive there and in an invalid state.

If moved to a valid connected track: everything else is OK, except that the locomotive direction suddently changed.

Make double clicking a locomotive label in the track diagram open the loc placement view IF autonomy isn't running.

---
<a id="mt-002"></a>

### MT-002 - 2026-08-20 - A group dragged right, and left

**Disposition:** fixed validated  
**From:** LT-A3, LT-M8  
**Written:** 2026-08-20

**What to do.** The same with a group. Pick several squares including at least two stations, drag them one
square RIGHT. Right specifically — a group dragged right has every source square landing on another
source square, which is the case that used to eat itself. Dragging left happened to work, which is
what made the same bug in the captions look intermittent.

#### Comments

Works right, but dragging left removed the locomotive.  If loc removed from graph, don't remove them from autonomy though unless we reload or explicitly delete.

Also, when selecting, add a deselect option to the right click menu (just change the one that's already there).  Change "pick" to Select.  And auto deselect once a move is complete.

---
<a id="mt-003"></a>

### MT-003 - 2026-08-20 - A route round-trips unchanged

**Disposition:** fixed unvalidated  
**From:** LT-C4  
**Written:** 2026-08-20

**What to do.** Open an existing route, save it unchanged, reopen it. Nothing may have changed. If you have a
route whose conditions contain brackets, use that one: a condition beginning with a bracket -
`(A or B) and C` - used to come back as `A or B`, silently, with the "reads as" line showing the
short version and nothing flagged red.

#### Comments

Looks OK.  But don't grey out cells on boolean operators, since it makes it look a bit confusing.

---
<a id="mt-004"></a>

### MT-004 - 2026-08-20 - A three-way point in a route

**Disposition:** fixed unvalidated  
**From:** LT-B5  
**Written:** 2026-08-20

**What to do.** A three-way point. Build a route with one, set it left, save, reopen. Still one row, still
left. Then run the route and watch the ironwork: the motor that ends up straight should move first,
and the other should follow after a pause.

#### Comments

Works.  But it still syncs with CS2 on close- is that sync still needed for consistency?  Perhaps only sync if there exist central station sourced routes.

---
<a id="mt-005"></a>

### MT-005 - 2026-08-20 - A signal address typed into a switch row

**Disposition:** fixed unvalidated  
**From:** LT-B6  
**Written:** 2026-08-20

**What to do.** Type a signal's address into a switch row. The kind should become Signal by itself, and the
setting box should offer red and green rather than straight and turn.

#### Comments

Works.  But add a "discard unsaved changes" confirmation to the new route window.

---
<a id="mt-006"></a>

### MT-006 - 2026-08-20 - Duplicating a command row

**Disposition:** fixed unvalidated  
**From:** LT-B1  
**Written:** 2026-08-20

**What to do.** Duplicate a row with the mark beside the trash. The copy lands directly under it. Change its
address, save, reopen.

#### Comments

Editing a route, after sync, teleports the user to the track diagram tab.  Don't do this.  This may be due to the autonomy load, etc.

---
<a id="mt-007"></a>

### MT-007 - 2026-08-20 - Changing a row kind clears the rest

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-20

**What to do.** Change a row's kind. Every other field clears - it should not be possible to end up with a
locomotive named `3` because an accessory address stayed behind.

#### Comments

Confirmed

---
<a id="mt-008"></a>

### MT-008 - 2026-08-20 - Three faults reported in one dialog

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-20

**What to do.** Save a route with three things wrong in it. One dialog listing all three, numbered. "Go back
and fix" leaves the window open on the cells it named; "Discard and close" closes it.

#### Comments

Works.

---
<a id="mt-009"></a>

### MT-009 - 2026-08-20 - The route Test button

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-20

**What to do.** The Test button, against a sensor you can occupy by hand. It should agree with the railway.

#### Comments

Works.

---
<a id="mt-010"></a>

### MT-010 - 2026-08-20 - Capture into commands and conditions

**Disposition:** fixed unvalidated  
**From:** LT-A5, LT-B2  
**Written:** 2026-08-20

**What to do.** Capture, into the commands and into the conditions.

#### Comments

Feedback events do not capture into CONDITIONS.  Switches do.

Signal auto-update does not work on conditions.

Rest works.

---
<a id="mt-011"></a>

### MT-011 - 2026-08-20 - A Central Station route is read-only

**Disposition:** needs test  
**From:** hands-on testing  
**Written:** 2026-08-20

**What to do.** A Central Station route. Everything greyed, no marks in any row, nothing typeable, no field
that takes the caret, and Escape closes it.

#### Comments

We tested this synthetically earlier.  I can retest if you changed anything.

---
<a id="mt-012"></a>

### MT-012 - 2026-08-20 - Keyboard works with Pick Several on

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-20

**What to do.** Pick Several on, then Delete, Control+C, Control+X and Escape. All four have to work while
the mode is on - the button used to take the keyboard focus with it.

#### Comments

OK

---
<a id="mt-013"></a>

### MT-013 - 2026-08-20 - The orange grip and group drag

**Disposition:** fixed unvalidated  
**From:** LT-C5  
**Written:** 2026-08-20

**What to do.** The orange grip at the top right of a selection. Drag the group by it, with picking still on.

#### Comments

OK.  Highlight the "move to' group in blue, not light red, for better clarity.  The selection itself should remain red.

---
<a id="mt-014"></a>

### MT-014 - 2026-08-20 - Growing the diagram

**Disposition:** fixed unvalidated  
**From:** LT-C3  
**Written:** 2026-08-20

**What to do.** `+` and `−`. After growing, look at the new row: it should be drawn whole, with no fragment
or stray gap a moment later.

#### Comments

OK.  In the autonomy diagram editor, we need to force the scrollable height of the diagram to be about 1 row more.  Sometimes it hides unless the window is stretched.

---
<a id="mt-015"></a>

### MT-015 - 2026-08-20 - Shift Down and Shift Right, then undo

**Disposition:** needs test  
**From:** hands-on testing  
**Written:** 2026-08-20

**What to do.** Shift Down and Shift Right from the right-click menu, then Control+Z.

#### Comments

*(none yet)*

---
<a id="mt-016"></a>

### MT-016 - 2026-08-20 - Show station name here

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-20

**What to do.** "Show station name here" on a blank square beside a station you have just clicked. The
station you were looking at should already be selected rather than whichever sorts first.

#### Comments

OK

---
<a id="mt-017"></a>

### MT-017 - 2026-08-20 - Naming a square that already has text

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-20

**What to do.** The same over a square that already has text of your own on it. It should ask whether to
replace it, naming the text, rather than refusing.

#### Comments

OK

---
<a id="mt-018"></a>

### MT-018 - 2026-08-20 - Why is it not moving - readability

**Disposition:** fixed unvalidated  
**From:** LT-C2, AR-11  
**Written:** 2026-08-20

**What to do.** "Why is it not moving" on a train with several blocked destinations. The whole answer has to
be readable - it wraps now, and scrolls past a few lines.

#### Comments

OK.  But the bar at the top of the editor has an odd border- give it a light gray background instead.  And there is an odd gray artifact on the right side of it.

---
<a id="mt-019"></a>

### MT-019 - 2026-08-20 - Pairing a tunnel or a link

**Disposition:** fixed unvalidated  
**From:** LT-B3, LT-F2  
**Written:** 2026-08-20

**What to do.** Pair a tunnel or a link. The diagram should highlight each candidate as you scroll the list,
not only after pressing OK.

#### Comments

OK.  Minor bug though: linked, active tile is greyed out.

Future feature request: make the autonomy editor and track diagram editor be on two tabs in one window.  Easy to flip between them if state is saved in one.

---
<a id="mt-020"></a>

### MT-020 - 2026-08-21 - Combine Linked Pages

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-21

**What to do.** Combine Linked Pages, on the Layouts menu, from a page that links somewhere. The new page
should show the current page and every page its links lead to, one under another with a blank row
between. Then check the autonomy editor's page list: it must be EXCLUDED, and the findings must not
have grown - if it were included, every sensor on it would become a second Point for a sensor that
already has one.

#### Comments

OK

---
<a id="mt-021"></a>

### MT-021 - 2026-08-21 - Control+X and Control+V on the diagram

**Disposition:** fixed unvalidated  
**From:** LT-A1, LT-A6, LT-A7  
**Written:** 2026-08-21

**What to do.** Control+X, Control+V and Delete on the track diagram. Point at a station with a train on it
and press Control+X; point at another and press Control+V. Then check the same two squares in the
autonomy editor - the placement has to have moved there as well, or the next build puts the train
back where it was. With the pointer NOT over the diagram, the same keys must still cut and paste
locomotive buttons exactly as before.

#### Comments

Doesn't work- only goes to the default route.

---
<a id="mt-022"></a>

### MT-022 - 2026-08-21 - A locomotive's settings from the tile menu

**Disposition:** fixed unvalidated  
**From:** LT-M1, LT-M2, LT-M3, LT-M4  
**Written:** 2026-08-21

**What to do.** A locomotive's settings from the autonomy editor's tile menu - the same dialog the track
diagram opens. Set an arrival or departure function, then run autonomy and listen for it.

#### Comments

Works.  

In autonomy editor: Remove one-way run from the right-click menu and put it into the autonomy editor.

In track diagram right click autonomy deep menu only: Hide entries that manipulate the diagram, such as "show a station name here".  Hide edit locomotive, since it's already in the top menu. Hide home locomotive appears twice, remove the one in the top menu. Hide signal protecting this station. Hide clear this square. Hide place locomotive, hide place locomotive. 

In track diagram right click autonomy deep menu only: add the loc is facing menu to the parent level, and hide it in the deep menu.

---
<a id="mt-023"></a>

### MT-023 - 2026-08-21 - Two signals on one station

**Disposition:** fixed unvalidated  
**From:** LT-C1, LT-M5, LT-M6, LT-M7  
**Written:** 2026-08-21

**What to do.** Two signals on one station. On a station with an approach at each end, open "Signal Protecting
This Station", add one signal by clicking it and a second by typing its address, and check the list
shows both with the diagram outlining both behind the window. Then remove one and add it back. Save,
reopen the editor, and check both are still listed.

#### Comments

Seems to work. Minor- window closes and reopens on removal.

In the right click menu, change "Connections and direction" to "Trains may depart...".  Move "trains may arrive" next to it.

Move all the link options out of this submenu, into the main one. 

Give the each of the right click menu groups semantic labels where there are 3+ options.

---
<a id="mt-024"></a>

### MT-024 - 2026-08-21 - Two signals on the railway

**Disposition:** fixed unvalidated  
**From:** LT-B4  
**Written:** 2026-08-21

**What to do.** The same station on the railway. Run autonomy and let a train stand there: BOTH signals have to
go red, and both back to green when it leaves. This is the half that cannot be tested from here - the
list can be right in the editor and only one signal actually wired into the built configuration.

#### Comments

Works.

We need unnamed stations (default) to throw an error, not a warning.

---
<a id="mt-025"></a>

### MT-025 - 2026-08-21 - A layout saved by the previous version

**Disposition:** needs test  
**From:** hands-on testing  
**Written:** 2026-08-21

**What to do.** A layout saved by the previous version. Open it, look at a station that already had a signal
paired: it must still be paired, and the file must be unchanged until you edit something. That is the
compatibility case - one signal is still written as a bare string, and only a station with two gets an
array.

#### Comments

No need to test, not deployed.

---
<a id="mt-026"></a>

### MT-026 - 2026-08-21 - Shift Up and Shift Left at the edges

**Disposition:** needs test  
**From:** AR-17  
**Written:** 2026-08-21

**What to do.** Shift Up with the pointer on the bottom row, and Shift Left on the last column. Both should now
do nothing at all. Then the same one row up and one column in, which should shift normally — and check
the autonomy editor afterwards to see the stations went with the track.

#### Comments

Mostly OK after shifting, but links still got unlinked.  Seems the coordinate mapping there may be an issue.


Confirmed- I tested this already.

**Claude, 2026-08-22.** I could not reproduce the unpairing. `testDiagramShiftKeepsSetup`
now covers it five ways - a link on the shifted page, both ends moving, a shift that writes over a
link, everything else on a shifted square, and the whole thing again through a live session with the
page actually shifted underneath it - and the pairing survives all of them. The one case where a
pairing IS destroyed is a link whose square gets written over by the row moving onto it, which is
correct and is pinned by its own test.

So either the links you saw were the ones written over, or the setup was already in the state the
earlier rounds left it in. **If it happens again, the useful details are: which direction, where the
pointer was, and the two squares the link joined** - with those I can reproduce it exactly.

---
<a id="mt-027"></a>

### MT-027 - 2026-08-21 - A tile dragged onto a station square

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-21

**What to do.** Drag a tile onto a station square. The station has to be gone from the autonomy editor
afterwards, not left on a square holding plain track.

#### Comments

OK

---
<a id="mt-028"></a>

### MT-028 - 2026-08-21 - Cancelling the diagram editor

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-21

**What to do.** Cancel the diagram editor after moving a set-up station. Both the diagram and the autonomy
setup have to be back where they started. This is the one that used to lose the station quietly.

#### Comments

OK

---
<a id="mt-029"></a>

### MT-029 - 2026-08-21 - The command table's marks

**Disposition:** fixed unvalidated  
**From:** AR-18  
**Written:** 2026-08-21

**What to do.** The command table's marks. Delete removes exactly one row; the arrows move a row and leave it
moved; duplicate makes one copy.

#### Comments

OK - but don't grey out cells in the + row in the condition editor.

**Claude, 2026-08-22.** Fixed: the "+" row is no longer shaded. It was being greyed
because nothing in it can be edited - which is true, and is not what the shading means: it is not a
row yet, it is the control that makes one.

---
<a id="mt-030"></a>

### MT-030 - 2026-08-21 - A route holding a signal command

**Disposition:** fixed unvalidated  
**From:** AR-19  
**Written:** 2026-08-21

**What to do.** A route holding a signal command. Open it, click the Setting cell, click away without choosing
anything, and save. The signal must still be at danger.

#### Comments

OK.  But grey out irrelevant settings' cells (like number) for commands that don't use it.

**Claude, 2026-08-22.** Fixed, and it was the same defect from the other side: the
shading was only ever wired to the CONDITIONS table, so no command cell was ever greyed whatever its
kind. The command table now shades by kind - the function number on a signal, the protocol on a stop,
the delay on a command that has none.

---
<a id="mt-031"></a>

### MT-031 - 2026-08-21 - Exporting a diagram as a picture

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-21

**What to do.** Export a diagram as a picture, then throw a switch on that page. The tile has to keep updating.

#### Comments

OK

---
<a id="mt-032"></a>

### MT-032 - 2026-08-21 - Two trains, one dispatched onto a long path

**Disposition:** needs test  
**From:** TR-A22  
**Written:** 2026-08-21

**What to do.** Two trains running, one dispatched onto a long path. TR-A22 in the flesh: while one locomotive
is being sent off over several edges, a train already under way has to reach and stop at its next
sensor normally. What it must NOT do is run past it. Worth doing in simulation first, then for real.

#### Comments

Defer for later once other bugs are fixed.

---
<a id="mt-033"></a>

### MT-033 - 2026-08-21 - Labels survive a dozen page switches

**Disposition:** fixed validated  
**From:** TR-A23  
**Written:** 2026-08-21

**What to do.** Switch pages, change tile size, and toggle addresses a dozen times, then throw a switch on the
first page. TR-A23: the tile still has to respond. If it does, the pruning is not throwing away
labels it should have kept - which is the risk of that change, not the leak it fixes.

#### Comments

OK.

---
<a id="mt-034"></a>

### MT-034 - 2026-08-21 - A popup window must not evict the main labels

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-21

**What to do.** Open a popup diagram window on the page the main window is showing, close it, then throw a
switch on that page. The same risk from the other side: a popup rebuilding a page must not evict the
main window's labels for it.

#### Comments

OK.

---
<a id="mt-035"></a>

### MT-035 - 2026-08-21 - The Central Station switched off mid-session

**Disposition:** needs test  
**From:** hands-on testing  
**Written:** 2026-08-21

**What to do.** Switch the Central Station off, leave TrainControl open, press Stop, then click a switch on the
diagram. It should pause about two seconds, say the power was not confirmed, and throw the switch
anyway - and then the NEXT click should behave the same way rather than doing nothing. Before this,
the first such click stopped every tile in the application from ever responding again.

#### Comments

Defer for later.

---
<a id="mt-036"></a>

### MT-036 - 2026-08-21 - A train stopped by hand

**Disposition:** needs test  
**From:** hands-on testing  
**Written:** 2026-08-21

**What to do.** Start a train and stop it by hand before it reaches its next sensor - lift it off, or turn its
power off at the loco. After five minutes the log should name it, name the sensor, and say how long.
Nothing else should change: the train stays waiting, and autonomy carries on around it.

#### Comments

Defer for later.

---
<a id="mt-037"></a>

### MT-037 - 2026-08-21 - An automatic route says nothing about its trigger

**Disposition:** needs test  
**From:** AR-20  
**Written:** 2026-08-21

**What to do.** Leave an automatic route enabled and watch the log for ten minutes. It must say NOTHING about
its trigger sensor. A route waiting on its sensor is a route doing its job, and it does so on a
locomotive called "Dummy Loc" - if that name ever appears in the log, the advisory has leaked out of
the dispatch path into the shared wait.

#### Comments

You can make a test for this.  Reduce the quota to 10 seconds.  Put a train on a station with 2+ s88's between it and the target.  Manually trigger the first s88, and then wait the timeout duration plus the startup delay as configured, plus a small buffer.  Then, check the log and popup activation.  

The app should be in debug/simulate mode, but the autonomy should not be, as otherwise the s88's get auto triggered.

Normally, I would test this just by clicking on s88's in the track diagram, not by running a real train.

**Claude, 2026-08-22.** Noted and not started - you said you would tell me when to pick
this one up. The specification above is enough to write it: quota down to 10 seconds, a train on a
station with two or more sensors between it and its target, trigger the first by hand, wait the
timeout plus the startup delay plus a buffer, then check the log and whether the popup fired. Debug
and simulate on, autonomy simulation OFF so the sensors are not triggered for us.

---
<a id="mt-038"></a>

### MT-038 - 2026-08-21 - An unreadable UIState.data is kept

**Disposition:** needs test  
**From:** IP-*, AR-21  
**Written:** 2026-08-21

**What to do.** Make `UIState.data` unreadable - copy any other file over it - then start TrainControl, close it,
and look in `tc_backup`.  There should be a copy named `unreadable<timestamp>UIState.data`, and the log
should say where it went rather than "no data file found".

#### Comments

Make a test for this yourself in the suite, and run this test for me after backing up the current file first.

Also, make a similar test to ensure autonomy is unloaded (or regenerated, if the app is running) gracefully if its config files are corrupt or manually removed.

**Claude, 2026-08-22.** The automated half is written: `testLayoutFolderRobustness`
covers the setup file being corrupt and the setup folder being deleted. The `UIState.data` half is
not automated yet - it lives in a JFrame and reads the file by a RELATIVE path, so a test for it runs
against whatever `UIState.data` is in the working directory, which is yours. That is why you asked
for a backup first, and it is why I have not run it unasked. **Say the word and I will back the file
up, run it, and put it back.**

---
<a id="mt-039"></a>

### MT-039 - 2026-08-21 - A page named with a slash

**Disposition:** fixed unvalidated  
**From:** AR-22  
**Written:** 2026-08-21

**What to do.** Rename a track diagram page to something with a slash in it, "Up/Down".  Close TrainControl and
reopen it: the page must still be there.

#### Comments

You should write a test for this.

Also, when the user sees this error: "Error occurred: Address already in use: Cannot bind", tell them that two copies of TrainControl cannot run at the same time.  The detailed error can stay in the log, but not the popup.

**Claude, 2026-08-22.** Automated as `testLayoutFolderRobustness.testAPageNamedWith
ASlashSurvivesASave`. The separate request in your comment - the "Address already in use" popup - is
now MT-063.

---
<a id="mt-040"></a>

### MT-040 - 2026-08-21 - A page the folder does not hold

**Disposition:** fixed unvalidated  
**From:** AR-23  
**Written:** 2026-08-21

**What to do.** Put a page in `gleisbild.cs2` that the folder does not hold, then open the layout.  Every other
page has to load, the missing one has to be named in the log, and the Layouts menu must still be
pointing at your folder afterwards.

#### Comments

You should write a test for this.

**Claude, 2026-08-22.** Automated as `testLayoutFolderRobustness.testAMissingPage
DoesNotStopTheOthers`.

---
<a id="mt-041"></a>

### MT-041 - 2026-08-21 - One accessory drawn on several squares

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-21

**What to do.** A page that draws one signal or switch on several squares - "2 - Bottom" has Signal 116 on three
- and throw that accessory.  EVERY one of those squares has to change.  Two of the three stopped
updating for a day, from a change meant to stop a memory leak, and the hands-on test written for that
change could not see it because the third still worked.

#### Comments

All OK.

---
<a id="mt-042"></a>

### MT-042 - 2026-08-22 - Hovering a station's name to paste

**Disposition:** needs test  
**From:** LT-A7  
**Written:** 2026-08-22

**What to do.** Hover a station's NAME on the track diagram and press Control+V.  Not the platform - the name
beside it, or drawn over it.  The train must land on that station.  Then hover a blank square that
carries no name and press it: nothing should happen and the log should say why.

#### Comments

*(none yet)*

---
<a id="mt-043"></a>

### MT-043 - 2026-08-22 - A sensor nudged onto its own label

**Disposition:** needs test  
**From:** LT-A9  
**Written:** 2026-08-22

**What to do.** Move a sensor that has a name DOWN one square, so it lands on the square its own name is written
on.  The name must survive, and be drawn over the tile it has landed on.  Then move it down one and
right one, and down one and right two: the name must survive all three.  This is the [---] bug.

#### Comments

*(none yet)*

---
<a id="mt-044"></a>

### MT-044 - 2026-08-22 - Cut and paste a whole column

**Disposition:** needs test  
**From:** LT-A8, FR-A1  
**Written:** 2026-08-22

**What to do.** Cut and paste a whole COLUMN that contains a paired link, and one that contains named stations.
The pairing must survive, from BOTH pages - go to the other end and check it still points back.  The
stations must arrive with their names, lengths and facings.  Then check the column you pasted ONTO: it
must not still be carrying the names it had before.

#### Comments

*(none yet)*

---
<a id="mt-045"></a>

### MT-045 - 2026-08-22 - The same for a whole row

**Disposition:** needs test  
**From:** LT-A8, FR-A1  
**Written:** 2026-08-22

**What to do.** The same for a whole ROW, which is the same rule with the axes swapped.

#### Comments

*(none yet)*

---
<a id="mt-046"></a>

### MT-046 - 2026-08-22 - A link switched off goes grey

**Disposition:** needs test  
**From:** LT-B3  
**Written:** 2026-08-22

**What to do.** Switch a link off in the autonomy editor.  It must go grey and hatched.  A link that is paired
and in use must be solid and carry its two arrows.  Before this round it was the other way round.

#### Comments

*(none yet)*

---
<a id="mt-047"></a>

### MT-047 - 2026-08-22 - Go to a link's other end

**Disposition:** needs test  
**From:** LT-M11  
**Written:** 2026-08-22

**What to do.** Right-click a paired link and choose "Go to the Other End".  It must close and reopen on that
page, at that square, flashing it.  With unsaved work, it must ask first - and answering yes must NOT
lose the pairing you just made.

#### Comments

*(none yet)*

---
<a id="mt-048"></a>

### MT-048 - 2026-08-22 - Double-click a train's name

**Disposition:** needs test  
**From:** LT-F1  
**Written:** 2026-08-22

**What to do.** Double-click a train's name on the running track diagram with autonomy stopped: the placement
view must open at that station.  With autonomy RUNNING, it must do nothing at all.

#### Comments

*(none yet)*

---
<a id="mt-049"></a>

### MT-049 - 2026-08-22 - The Edit button no longer asks

**Disposition:** needs test  
**From:** LT-F2  
**Written:** 2026-08-22

**What to do.** Press Edit twice.  The second press must open the same editor as the first, on the page the main
window is showing, without asking anything.  Then use the Autonomy menu's own edit item: that must take
you to the setup editor whatever you used last.

#### Comments

*(none yet)*

---
<a id="mt-050"></a>

### MT-050 - 2026-08-22 - The sidebar

**Disposition:** needs test  
**From:** LT-F2  
**Written:** 2026-08-22

**What to do.** The sidebar.  With more than one page, switch pages from it: same as closing and reopening, and
it must ask about unsaved work first.  Say no: the sidebar must go back to showing the page you are
actually on.  Switch modes the same way.

#### Comments

*(none yet)*

---
<a id="mt-051"></a>

### MT-051 - 2026-08-22 - The sidebar with nothing to offer

**Disposition:** needs test  
**From:** LT-F2  
**Written:** 2026-08-22

**What to do.** The sidebar with nothing to offer.  Unload the autonomy configuration: the Autonomy Setup tab
must be greyed with a tooltip saying what to load.  Start trains: it must grey for that reason instead.
On a single-page layout, the page tabs must be gone.

#### Comments

*(none yet)*

---
<a id="mt-052"></a>

### MT-052 - 2026-08-22 - A remembered window size with a sidebar

**Disposition:** needs test  
**From:** FR-D2  
**Written:** 2026-08-22

**What to do.** Open the editor on a page, resize the window, close and reopen it.  The remembered size must not
have squeezed the diagram now that there is a sidebar taking width from it.

#### Comments

*(none yet)*

---
<a id="mt-053"></a>

### MT-053 - 2026-08-22 - Edit Locomotive opens its dialog

**Disposition:** needs test  
**From:** AR-1, AR-2  
**Written:** 2026-08-22

**What to do.** Right-click a station with a train on it in the autonomy editor.  "Edit Locomotive..." must open
the assignment dialog, not a popup saying "null".  On an EMPTY station there must be no Place Locomotive
item at all - "Add a Locomotive to Autonomy..." is what places one.

#### Comments

*(none yet)*

---
<a id="mt-054"></a>

### MT-054 - 2026-08-22 - Combine Linked Pages appears once

**Disposition:** needs test  
**From:** AR-3, AR-4  
**Written:** 2026-08-22

**What to do.** Open and close the autonomy editor five times, then look at the Layouts menu.  "Combine Linked
Pages..." must appear once.  Its tooltip must wrap instead of running off the screen.

#### Comments

*(none yet)*

---
<a id="mt-055"></a>

### MT-055 - 2026-08-22 - Manage Pages and Edit Layout Page

**Disposition:** needs test  
**From:** AR-5  
**Written:** 2026-08-22

**What to do.** Layouts menu.  "Manage Pages" holds add, rename, duplicate, combine and delete.  "Edit Layout
Page" lists every page and opens the one you pick, in whichever editor you used last.

#### Comments

*(none yet)*

---
<a id="mt-056"></a>

### MT-056 - 2026-08-22 - The sidebar with a long page name

**Disposition:** needs test  
**From:** AR-6, AR-7, AR-8  
**Written:** 2026-08-22

**What to do.** The sidebar with a very long page name.  The buttons must stay one width and show the whole name
in a tooltip.  With more than eight pages the tabs must scroll.  The headings must be blue semibold and
the buttons bold black.

#### Comments

*(none yet)*

---
<a id="mt-057"></a>

### MT-057 - 2026-08-22 - A train marker and its name

**Disposition:** needs test  
**From:** AR-13, AR-14  
**Written:** 2026-08-22

**What to do.** In the autonomy editor, place a train on a station.  A white star must appear in the middle of
that square, and the station's label must show the train's NAME in black on white rather than [---].
Regular text labels must stay grey.

#### Comments

*(none yet)*

---
<a id="mt-058"></a>

### MT-058 - 2026-08-22 - Show autonomy hides the names

**Disposition:** needs test  
**From:** AR-15  
**Written:** 2026-08-22

**What to do.** On the main diagram, untick "Show autonomy".  The station names must go with the badges.  Change
page and come back: still gone.  Tick it again and they return.

#### Comments

*(none yet)*

---
<a id="mt-059"></a>

### MT-059 - 2026-08-22 - Why is it not moving, on an addressed layout

**Disposition:** needs test  
**From:** AR-12  
**Written:** 2026-08-22

**What to do.** Ask "why is it not moving" on a layout whose switches and signals HAVE addresses.  The sample
layout does not, which is why it answers "nowhere to go" - see 2026-08-22-adam-round.md.  On a real one
the report should name the stations it can reach.  With blocking findings present it must lead with how
many there are.

#### Comments

*(none yet)*

---

<a id="mt-060"></a>

### MT-060 - 2026-08-22 - testAutoDetect needs a Central Station

**Disposition:** needs test  
**From:** hands-on testing  
**Written:** 2026-08-22

**What to do.** Run `testAutoDetect` with a Central Station answering on the network. It scans for one at
192.168.50.25 and asserts it finds it, so it is red on any machine that has none - which is not a
defect, and is why it is excluded from the fast battery.

#### Comments

**Claude, 2026-08-22.** Carried over from the "Still untested, and known" list at the bottom of
the old file. It has been red in every run this week for this reason.

---

<a id="mt-061"></a>

### MT-061 - 2026-08-22 - Graceful stop timing

**Disposition:** needs test  
**From:** hands-on testing  
**Written:** 2026-08-22

**What to do.** Stop autonomy gracefully and judge how long it feels. It now takes a moment longer than it used
to and is telling the truth for the first time - it waits for trains to reach a station rather than
reporting success immediately. Whether that reads as "a moment" or as "slow" is a judgement only you
can make.

#### Comments

**Claude, 2026-08-22.** Carried over from the "Still untested, and known" list at the bottom of
the old file.

---

<a id="mt-062"></a>

### MT-062 - 2026-08-22 - Delete, shift and insert have not had the move audit

**Disposition:** needs test  
**From:** hands-on testing  
**Written:** 2026-08-22

**What to do.** Delete a set-up tile, and use the row and column insert operations, then check the autonomy
editor. All three relocate or destroy track, and everything the setup holds is keyed by square.

#### Comments

**Claude, 2026-08-22.** Carried over from the "Still untested, and known" list at the bottom of
the old file, where it read "worth a pass before 3.0.0". Since then the shifts HAVE been audited and
covered by `testDiagramShiftKeepsSetup`, and delete is covered by the landing rules in
`testAutonomyStoreSettingsMatrix`. What is still not covered anywhere is the editor CALLING them,
which is the gap that produced LT-A8 and FR-A1 - see MT-044.

---

<a id="mt-063"></a>

### MT-063 - 2026-08-22 - A second copy of TrainControl says so

**Disposition:** needs test  
**From:** AR-16  
**Written:** 2026-08-22

**What to do.** Start TrainControl twice. The second one must say that TrainControl is already running and that
only one copy can run at a time, rather than "Error occurred: Address already in use: Cannot bind".
The detailed error should still be in the console and the stack trace should still print.

#### Comments

*(none yet)*

---
