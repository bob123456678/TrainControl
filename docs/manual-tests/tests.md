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
| [MT-003](#mt-003) | 2026-08-20 | A route round-trips unchanged | fixed unvalidated | LT-C4 |
| [MT-004](#mt-004) | 2026-08-20 | A three-way point in a route | fixed unvalidated | LT-B5 |
| [MT-005](#mt-005) | 2026-08-20 | A signal address typed into a switch row | fixed unvalidated | LT-B6 |
| [MT-011](#mt-011) | 2026-08-20 | A Central Station route is read-only | needs test | hands-on testing |
| [MT-014](#mt-014) | 2026-08-20 | Growing the diagram | fixed unvalidated | LT-C3 |
| [MT-019](#mt-019) | 2026-08-20 | Pairing a tunnel or a link | fixed unvalidated | LT-B3, LT-F2 |
| [MT-022](#mt-022) | 2026-08-21 | A locomotive's settings from the tile menu | fixed unvalidated | LT-M1, LT-M2, LT-M3, LT-M4 |
| [MT-023](#mt-023) | 2026-08-21 | Two signals on one station | fixed unvalidated | LT-C1, LT-M5, LT-M6, LT-M7 |
| [MT-024](#mt-024) | 2026-08-21 | Two signals on the railway | fixed unvalidated | LT-B4 |
| [MT-025](#mt-025) | 2026-08-21 | A layout saved by the previous version | needs test | hands-on testing |
| [MT-026](#mt-026) | 2026-08-21 | Shift Up and Shift Left at the edges | needs test | AR-17 |
| [MT-029](#mt-029) | 2026-08-21 | The command table's marks | fixed unvalidated | AR-18 |
| [MT-030](#mt-030) | 2026-08-21 | A route holding a signal command | fixed unvalidated | AR-19 |
| [MT-032](#mt-032) | 2026-08-21 | Two trains, one dispatched onto a long path | needs test | TR-A22 |
| [MT-035](#mt-035) | 2026-08-21 | The Central Station switched off mid-session | needs test | hands-on testing |
| [MT-037](#mt-037) | 2026-08-21 | An automatic route says nothing about its trigger | fixed unvalidated | AR-20 |
| [MT-039](#mt-039) | 2026-08-21 | A page named with a slash | fixed unvalidated | AR-22 |
| [MT-040](#mt-040) | 2026-08-21 | A page the folder does not hold | fixed unvalidated | AR-23 |
| [MT-043](#mt-043) | 2026-08-22 | A sensor nudged onto its own label | needs test | LT-A9 |
| [MT-045](#mt-045) | 2026-08-22 | The same for a whole row | needs test | LT-A8, FR-A1 |
| [MT-047](#mt-047) | 2026-08-22 | Go to a link's other end | fixed unvalidated | LT-M11 |
| [MT-051](#mt-051) | 2026-08-22 | The sidebar with nothing to offer | needs test | LT-F2 |
| [MT-053](#mt-053) | 2026-08-22 | Edit Locomotive opens its dialog | fixed unvalidated | AR-1, AR-2 |
| [MT-056](#mt-056) | 2026-08-22 | The sidebar with a long page name | fixed unvalidated | AR-6, AR-7, AR-8 |
| [MT-057](#mt-057) | 2026-08-22 | A train marker and its name | fixed unvalidated | AR-13, AR-14 |
| [MT-058](#mt-058) | 2026-08-22 | Show autonomy hides the names | fixed unvalidated | AR-15 |
| [MT-059](#mt-059) | 2026-08-22 | Why is it not moving, on an addressed layout | needs test | AR-12 |
| [MT-060](#mt-060) | 2026-08-22 | testAutoDetect needs a Central Station | needs test | hands-on testing |
| [MT-062](#mt-062) | 2026-08-22 | Delete, shift and insert have not had the move audit | fixed unvalidated | hands-on testing |
| [MT-064](#mt-064) | 2026-08-22 | Highlight on Diagram, and Test Condition | needs test | feature request |
| [MT-069](#mt-069) | 2026-08-18 | Remove a locomotive from a non-station | fixed unvalidated | 2026-08-18 manual test plan, Tier 1 - diagram and editor, autonomy not running |
| [MT-074](#mt-074) | 2026-08-18 | Export / import round trip | fixed unvalidated | 2026-08-18 manual test plan, Tier 2 - data safety |
| [MT-075](#mt-075) | 2026-08-18 | Page files | fixed unvalidated | 2026-08-18 manual test plan, Tier 2 - data safety |
| [MT-076](#mt-076) | 2026-08-18 | Running path drawing | fixed unvalidated | 2026-08-18 manual test plan, Tier 3 - autonomy in simulation, one train |
| [MT-077](#mt-077) | 2026-08-18 | Caption direction arrow | needs test | 2026-08-18 manual test plan, Tier 3 - autonomy in simulation, one train |
| [MT-078](#mt-078) | 2026-08-18 | Barred arrival is honoured | needs test | 2026-08-18 manual test plan, Tier 3 - autonomy in simulation, one train; **reopened** 2026-08-22 |
| [MT-079](#mt-079) | 2026-08-18 | Barred terminus loads | fixed unvalidated | 2026-08-18 manual test plan, Tier 3 - autonomy in simulation, one train |
| [MT-080](#mt-080) | 2026-08-18 | Collect what the new model offers | needs test | 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most) |
| [MT-081](#mt-081) | 2026-08-18 | Collect what the old model offered | needs test | 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most) |
| [MT-082](#mt-082) | 2026-08-18 | Compare, and scrutinise the NEW-ONLY entries | needs test | 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most) |
| [MT-083](#mt-083) | 2026-08-18 | Run a new-only route in simulation | needs test | 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most) |
| [MT-084](#mt-084) | 2026-08-18 | Two trains, shared junction | needs test | 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains |
| [MT-085](#mt-085) | 2026-08-18 | Collision refusal | needs test | 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains |
| [MT-086](#mt-086) | 2026-08-18 | Manual displacement still works | fixed unvalidated | 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains |
| [MT-087](#mt-087) | 2026-08-18 | Long run | needs test | 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains |
| [MT-088](#mt-088) | 2026-08-18 | Path-integrity failure | needs test | 2026-08-18 manual test plan, Tier 6 - real hardware, optional |
| [MT-095](#mt-095) | 2026-08-22 | The editor stays open when you switch page or mode | fixed unvalidated | OB-005 |
| [MT-096](#mt-096) | 2026-08-22 | The editor opens at the size of its diagram | fixed unvalidated | OB-003 |
| [MT-097](#mt-097) | 2026-08-22 | Sidebar: pages are a list, modes are radio buttons | fixed unvalidated | FR-003 |
| [MT-098](#mt-098) | 2026-08-22 | One-Way Run is a button, and asks which way | fixed unvalidated | FR-004 |
| [MT-099](#mt-099) | 2026-08-22 | A train mark shows on a station with nothing else on it | fixed unvalidated | FR-005 |
| [MT-101](#mt-101) | 2026-08-22 | Placing a locomotive updates the labels, and there is one way to do it | fixed unvalidated | OB-009 |
| [MT-104](#mt-104) | 2026-08-22 | The tile menu, reordered | fixed unvalidated | OB-013 |
| [MT-106](#mt-106) | 2026-08-22 | The viewer keeps its own appearance while an editor is open | fixed unvalidated | OB-016 |
| [MT-108](#mt-108) | 2026-08-22 | Route editor: Save in the corner, and Cancel asks | fixed unvalidated | OB-018 |
| [MT-109](#mt-109) | 2026-08-22 | Track lengths: hotkey, focus, and size | fixed unvalidated | OB-019 |
| [MT-110](#mt-110) | 2026-08-22 | The autonomy column is narrower, and three labels changed | fixed unvalidated | OB-020 |
| [MT-111](#mt-111) | 2026-08-22 | Layouts menu: order, and one divider too many | fixed unvalidated | OB-021 |
| [MT-112](#mt-112) | 2026-08-22 | Home assignments: the three rules that were unreachable | fixed unvalidated | OB-022 (DD-A6) |
| [MT-113](#mt-113) | 2026-08-22 | Tool labels, and a heading with nothing under it | fixed unvalidated | OB-027, OB-032 |
| [MT-114](#mt-114) | 2026-08-22 | No findings while autonomy is unloaded | fixed unvalidated | OB-029 |
| [MT-115](#mt-115) | 2026-08-22 | Both menus decline while an editor is open, and lead back to it | fixed unvalidated | OB-033 |
| [MT-116](#mt-116) | 2026-08-22 | Renaming a station keeps its label | fixed unvalidated | OB-034 |
| [MT-117](#mt-117) | 2026-08-22 | The train star is visible again, and five smaller fixes | fixed unvalidated | OB-030, OB-031, OB-035, OB-036, OB-037 |
| [MT-119](#mt-119) | 2026-08-22 | The trace arriving at a curved station | fixed unvalidated | OB-026 |

Everything else - 53 of 119 - is **fixed validated** and needs nothing from you unless the
area changes again.

---

## The tests


<a id="mt-065"></a>

### MT-065 - 2026-08-18 - Arrival marks look right

**Disposition:** fixed validated  
**From:** 2026-08-18 manual test plan, Tier 1 - diagram and editor, autonomy not running  
**Written:** 2026-08-18

**What to do.** Arrival marks look right. Autonomy editor, visibility dropdown set to **Station Arrivals**.
   Every station with two or more ways in shows small yellow inward chevrons at its edges. Are they
   legible at your tile size, and clearly not overlapping the red/green direction arrows?

#### Comments

Icons are OK but the offset is odd.  Also, [---] station labels are propagating into some of the stations in the editor, which overlaps.  

Idea: make station shapes semantic.  A triangle that points in the way it accepts arrivals.  We just need a way to differentiate "can reverse" and "must reverse" then.


Bug: clicking on the arrows to cycle in the editor affects an unrelated tile.  Changing in menu works.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works, with notes.

They look good for now, but the overall appearance of the stations and incoming/outgoing arrows can be improved for better clarity.

Filed from this test: OB-002 (feature request - Appearance of stations and incoming arrows).  They are in `issues.md` until they are picked up.

*Run against commit cd27e285.*

---

<a id="mt-066"></a>

### MT-066 - 2026-08-18 - Arrivals menu placement

**Disposition:** fixed validated
**From:** 2026-08-18 manual test plan, Tier 1 - diagram and editor, autonomy not running  
**Written:** 2026-08-18

**What to do.** Arrivals menu placement. Right-click a station with two ways in. **"Trains may arrive…"** is on
   the top level of the menu, beside the usage choice, not inside it. Untick one side; the last
   remaining side should refuse to be unticked.

#### Comments

Works

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works.

List itself seems OK.

*Run against commit 058d2385.*

---

<a id="mt-067"></a>

### MT-067 - 2026-08-18 - Arrival marks in the viewer

**Disposition:** fixed validated
**From:** 2026-08-18 manual test plan, Tier 1 - diagram and editor, autonomy not running  
**Written:** 2026-08-18

**What to do.** Arrival marks in the viewer. Close the editor. A restricted station shows its marks on the
   running diagram; an unrestricted one shows nothing. (Deliberate - no clutter for the default.)

#### Comments

Works, but overlap with the labels makes it suboptimal.  station icon may fix this.  Side requirement: left clicking a station icon should propagate the click to the s88 and back.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---

<a id="mt-068"></a>

### MT-068 - 2026-08-18 - Switched-off link

**Disposition:** fixed validated
**From:** 2026-08-18 manual test plan, Tier 1 - diagram and editor, autonomy not running  
**Written:** 2026-08-18

**What to do.** Switched-off link. Switch a link off. It is greyed on the main diagram, not only in the editor.

#### Comments

Looks right in the track diagram.  But not greyed out in the editor.  Also, move the "use this link" option out of the submenu into the top level.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---

<a id="mt-069"></a>

### MT-069 - 2026-08-18 - Remove a locomotive from a non-station

**Disposition:** fixed unvalidated
**From:** 2026-08-18 manual test plan, Tier 1 - diagram and editor, autonomy not running  
**Written:** 2026-08-18

**What to do.** Remove a locomotive from a non-station. Right-click a point holding a loco that is not a
   station. **Remove** is present.

#### Comments

Works.  For the 3 type options (trains can stop, trains can pass through, neither, prefix with "Yes, No, No".  Out of service -> nothing can pass.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Does not work.

Present in the autonomy editor but not in the track diagram.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Found, and it is the same trap the menu already had a comment about - one level
further up.

The track diagram's menu is built with two squares: `station`, from `autonomyStationAt`, and `here`,
the square itself. **`autonomyStationAt` returns null unless the square has been DESIGNATED a station.**
The whole locomotive block - Remove, the paths, the facing - hung off `station`, so on a pass-through
Point with a train standing on it there was nothing at all. The autonomy editor works from the square,
which is why it had them and the diagram did not.

Fifteen lines below the line I changed, this comment was already there:

> "Gated on being a destination alone, a locomotive on a copy that is not one had no menu at all - no
> remove, no paths, nothing ... It is the same trap the autonomy editor had, where the remove item
> hung off the designation rather than off the locomotive."

That fix was applied to the inner test and not to where the square comes from, so the designation was
still deciding, one step earlier. The block reads from the square now when there is no station.
---

<a id="mt-070"></a>

### MT-070 - 2026-08-18 - Page switching keeps captions live

**Disposition:** fixed validated
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Page switching keeps captions live. Note a caption on page A. Go to page B, then C, then back to
   A. A's captions still update.

#### Comments

They do- but I didn't test running with autonomy.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-071"></a>

### MT-071 - 2026-08-18 - Popup diagram captions

**Disposition:** fixed validated
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Popup diagram captions. Pop out a page window, then repaint the main window. The popup's
   captions still update.

#### Comments

Works, but I noticed that some locomotives get a V > suffix, not just V or >.  Also, when moving a locomotive from one point to the other, it would be ideal if its natural direction could be preserved, compatible with the entrance direction to the station.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-072"></a>

### MT-072 - 2026-08-18 - Cancel in the track diagram editor

**Disposition:** fixed validated
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Cancel in the track diagram editor. Delete two sensor squares that carry names, lengths or
   arrival settings, then press **Cancel**. The track comes back AND those squares keep their autonomy
   settings.

#### Comments

Labels disappear, stations stay.  Bug!  Confirmed the labels stay gone after reload.

Also, the confirm dialog in the diagram editor says 'are you sure you want to exit without saving', but the autonomy is 'save before existing?'  make the latter consistent.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Does not work.

stations stay, locomotives are removed and no longer shown in the labels.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Confirmed as a real defect, and I have narrowed it without closing it. Saying
where I got to rather than guessing, because the honest answer is that I could not reproduce it.

**What I proved does work.** `testCancelRestoresPlacements` drives the session the way the editor does -
name a square, make it a station, place a locomotive, take the snapshot the window takes when it opens,
call the same `forgetTiles` the editor calls to delete a square, then restore the snapshot. Both the
station and the locomotive come back, and they are still there when a second session reads the folder
off disk. So `snapshotSetup`, `restoreSetup` and `save` are not where this is.

That test is worth having either way - it pins the half of Cancel that is correct, so whatever the
cause turns out to be, the fix cannot quietly break it.

**What I eliminated by reading.** `snapshotSetup` deep-copies every configuration, so the placements
are in the snapshot. `reconcile` never touches a configuration's points, so it is not pruning them.
`save` writes setup.json and every configuration file, so a restore does reach disk. `rememberAutonomy`
only saves - it does not re-take the snapshot.

**What is left, and what would settle it.** The snapshot is taken once, when the window opens, and
three places set it to null: `saveBeforeLeaving`, and two others. If it is null when Cancel runs, Cancel
restores nothing - and stations would then "stay" only because they were already written.

Two questions I cannot answer from here:

1. Did you press **Save** at any point in that editor session before pressing Cancel? If so this is
   explained, and the fix is that Save must re-take the snapshot rather than discard it - Save means
   "keep what I have done so far", not "there is nothing to undo from here on".
2. Was the locomotive placed **before** you opened the editor, or during that session?

If the answer to 1 is no, I will build the editor-level test rather than reason about it further.

**Claude, 2026-08-22, later.** I built the tests instead of waiting, and they all pass - so I still
cannot reproduce this below the window, and I am going to stop guessing at it.

`testCancelRestoresPlacements` now covers three orderings:

1. snapshot, delete the square through `forgetTiles`, restore - both come back;
2. the same, then read a SECOND session off disk - both are in the files;
3. the same, but with the page the session is holding **emptied** first, which is the state the editor
   really leaves it in. That third one is the case `testDiscardedEditsDoNotDeleteSetup` documents as
   the hazard - "Cancel reverts by re-reading the pages from disk into NEW objects, so the session is
   left holding the discarded version" - and the placement survives it too.

So `snapshotSetup`, `restoreSetup`, `save`, `saveWithoutReconciling` and `reconcile` are all
eliminated, in the order the editor uses them. The tests are worth keeping either way: they pin the
half that is correct, so whatever the cause turns out to be, the fix cannot quietly break it.

**What I need from you, and it is one look rather than another run.** Do the test again, and before
touching anything else open `cs2_sample_layout/config/autonomy/configuration-<name>.json` and search it
for the locomotive's name.

- **It is there** - the data is fine and this is a stale VIEW. The label reads the running layout
  rather than the file, and the track-editor close path rebuilds that differently from the autonomy
  one.
- **It is not there** - the data really is being lost, above every layer I have tested, and I will
  instrument the editor itself.

Those two need completely different fixes, and I have now spent three passes reasoning about which one
it is. One look at that file decides it.

**Claude, 2026-08-22, resolved.** Adam ran it: the placement is still in the configuration file after
Cancel, and the label reads correctly now. So **nothing was ever lost** - which is what the three
tests were saying all along, and why none of them could be made to fail.

**What was almost certainly happening.** The white star that marks "a train is set up to be standing
here" was not being drawn, and that is a defect I found and fixed independently on the same day, filed
as `FR-005` and covered by [MT-099](#mt-099): `TileAnnotation.isBlank()` listed every field that counts
as content except `occupied`, so `paint()` returned before drawing anything on a square whose only
annotation was a train. It appeared on stations, which carry a badge and so were never blank, and was
missing on exactly the squares with nothing else on them.

Both reports were made against the same build, `058d2385`, about the same squares, within minutes of
each other. "Locomotives are removed and no longer shown in the labels" and "the mark is not there" are
the same observation of the same missing pixels.

**Stated as the likeliest explanation rather than a proven one.** I did not watch this fail and then
watch the fix cure it, so what I have is a defect with the right shape, in the right place, at the
right time, and a symptom that went away when it was fixed. The one thing that IS proven is the part
that matters most: the placement was never deleted, because the file still holds it and three
orderings of the Cancel path say it survives.

**Why it took so long to get here, which is the lesson.** Three passes of reading code looking for
where the data went, when the data had not gone anywhere. The report said "removed", I took "removed"
to mean "deleted", and every hypothesis after that was about deletion. What would have shortened it is
the check that finally ended it - look at the file first, and find out whether you are debugging a
data loss or a drawing bug, because they share no code at all.

`where-are-the-trains.py` exists for exactly that, and is the first thing to reach for next time
something has "gone".
---

<a id="mt-073"></a>

### MT-073 - 2026-08-18 - Undo covers captions

**Disposition:** fixed validated
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Undo covers captions. Delete a captioned sensor, Ctrl+Z: tile and name both return. Drag a
   captioned tile, Ctrl+Z: the caption follows it back.

#### Comments

Bug- caption says, but content changes from the name itself to [---].  

Also: still don't see a way to move labels in the layout editor.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-074"></a>

### MT-074 - 2026-08-18 - Export / import round trip

**Disposition:** fixed unvalidated
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Export / import round trip. Export the autonomy JSON, re-import it. It loads, and Tier 4 step
    19 still holds afterwards. (This was broken until 18 August - the block field was not written.)

#### Comments

Seems fine.  Not sure what the block field is.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Could not run this.

Write a test for this yourself and validate that the source files are unchanged afterwards.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Taking this one on, as you asked. Not written yet - it is the next thing after
the three feature requests and MT-072 - and it is recorded here so it is not lost between rounds.

What the test has to do that a hands-on run would: export, re-import, and then compare the FILES rather
than the screen, because "the source files are unchanged afterwards" is the actual requirement and the
only way to check it honestly is byte-for-byte against a copy taken before.

**Claude, 2026-08-22.** Written, in `testDataSafetyRoundTrips`. Export the setup, check the files are
**byte-for-byte unchanged** by the export - your requirement, and the one a person at the screen cannot
check - then import the bundle into a folder that has never seen it and confirm the name, the station
designation and the placement all arrive.

The named regression is covered by asserting the square key is in the bundle: an export that drops it
drops the Point's identity, and the way that fails is silent - the file is valid JSON and imports
without complaint.
---

<a id="mt-075"></a>

### MT-075 - 2026-08-18 - Page files

**Disposition:** fixed unvalidated
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Page files. After a save, `config/gleisbilder/` holds a one-time `.bak` beside a rewritten
    page, and nothing is corrupted.

#### Comments

I don't see the .bak, but check on your end.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Could not run this.

Write a test for this yourself and validate.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Taking this one on, as you asked. Not written yet - it is the next thing after
the three feature requests and MT-072 - and it is recorded here so it is not lost between rounds.

What the test has to do that a hands-on run would: export, re-import, and then compare the FILES rather
than the screen, because "the source files are unchanged afterwards" is the actual requirement and the
only way to check it honestly is byte-for-byte against a copy taken before.

**Claude, 2026-08-22.** Written, in `testDataSafetyRoundTrips`. A page with a marker in it that no
save writes, saved twice; the `.bak` must still hold that marker afterwards, and no `.tmp` may be left
beside it.

**Two passes, because the first was vacuous.** It compared the backup's bytes against a copy taken
after the first save, and passed against a mutant that rewrote the backup every time. Rewritten to
assert on the marker.

Then the mutant passed again - and that turned out to be a fact about the code rather than the test:
`Files.copy` without `REPLACE_EXISTING` **refuses** to overwrite, and the failure is swallowed by the
catch below it. So the `!Files.exists(backup)` guard is belt-and-braces; the copy would refuse anyway.
Confirmed by mutating the copy itself to replace, which the test does catch.

Worth knowing if that catch is ever narrowed: it is currently the thing actually enforcing "only the
first".
---

<a id="mt-076"></a>

### MT-076 - 2026-08-18 - Running path drawing

**Disposition:** fixed unvalidated
**From:** 2026-08-18 manual test plan, Tier 3 - autonomy in simulation, one train  
**Written:** 2026-08-18

**What to do.** Running path drawing. The route is a line along the track - red ahead of the train, green
    behind - with black arrowheads for direction. The train marker sits on the tile it has actually
    reached, not one ahead.

#### Comments

Looks OK for now, couldn't test much.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works, with notes.

Looks good, but the intermediate stations overlap above just when reached, and then are under the green line afer.  I like being able to see progress- keep them on top after being reached.  Also, why not put a little train icon (simple black steam engine with a white outlined) on top of the current location?

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** The station badges are drawn again on top of the run line.

The order was deliberate and its comment says why: the annotation is painted first and the path over
it, because "the path a train is actually taking is the more urgent of the two: the arrows say what is
permitted, which is worth reading while a layout is being set up rather than while it is running."

That reasoning is right about the ARROWS and wrong about the badges, which is what you noticed. A
station is where the train is GOING; the line is how it gets there. Burying the landmark under the
route loses the thing being watched.

So the arrows stay under the line and the badge - with the train star on it - comes back over. Only
where there is a line to have covered it, so an ordinary diagram still paints its badge once.

**The train icon you suggested is not in this.** "Why not put a little train icon (simple black steam
engine)" is a different and larger idea than fixing an overlap, and it wants deciding rather than
slipping in beside a fix. Say the word and I will file it as a feature request through the inbox.
---

<a id="mt-077"></a>

### MT-077 - 2026-08-18 - Caption direction arrow

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 3 - autonomy in simulation, one train  
**Written:** 2026-08-18

**What to do.** Caption direction arrow. The `>` `<` `^` `v` arrow appears consistently, both for a train you
    placed by hand and for one autonomy drove there.

#### Comments

No, see above.  The arrow is sometimes duplicated.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works, with notes.

Loc name and icon at the end may be truncated in the autonomy view.  Make sure the same truncation logic is applied to the locomotive in the autonomy editor.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** I need one more detail before touching this, because the truncation you asked
for is already there and I would rather ask than change the wrong label.

The autonomy EDITOR's caption goes through `LayoutGrid.stationCaption`, the same method the running
diagram uses, and has since `c0c9055d` - bracketed, cut to `LAYOUT_STATION_MAX_LENGTH`, facing arrow
inside the brackets. That was the MT-093 fix, and your build for this run included it.

So the label I think you mean is already truncated identically. **Which view is cutting off the name
and icon?** The candidates are the Auto tab's locomotive list, the findings list at the foot of the
editor, and the graph window's own labels - all of which draw a locomotive name and none of which
shares that method. Name the one you saw and it is a small change.
---

<a id="mt-078"></a>

### MT-078 - 2026-08-18 - Barred arrival is honoured

**Disposition:** needs test
**From:** 2026-08-18 manual test plan, Tier 3 - autonomy in simulation, one train; **reopened** 2026-08-22  
**Written:** 2026-08-18

**What to do.** Barred arrival is honoured. Bar one side of a two-ended station, reload, run. Trains only pull
    in from the allowed side, and the station is still reachable.

#### Comments

Honored.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Does not work.

Not always honored.  In manual operation, I was able to send a train from Tunnel to BottomMainA in the current track digram setup.  BottomMainA had barred arrivals from the west.

*Run against commit 058d2385, build\classes, compiled 22 Aug 18:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** I could not reproduce this, and rather than guess a fourth time today I
tested the rule and am bringing you the one question that settles it.

**What I proved.** `testBarredArrivalIsNotADestination` bars one side of a two-ended platform, builds,
and reads the emitted Points: the barred copy comes out **not a station**, and the other copy still
stops. Verified by mutation - removing `arrivalAllowed` from the build fails the test - so this is a
real check, not a passing one.

That is the half that decides. A square trains can reach from two sides is emitted as two Points, which
is how the model records which way a train faces, and barring a side makes THAT copy a non-station.
`getPossiblePaths` - which builds the manual list as well as the automatic one - filters on
`end.isDestination()`, so a copy that is not a station cannot be the end of any path, offered or taken.

**So what did you see?** The most likely answer is that the train arrived at the OTHER copy, legally.
The two copies share a base name, so "sent a train to BottomMainA" cannot tell them apart by name -
and if Tunnel can reach BottomMainA's eastern end, that journey is correct and barring the west has
worked exactly as intended.

**The one thing that settles it:** when it happens again, look at the caption on BottomMainA once the
train is standing there. It carries a facing arrow - `[V 200 150 >]` - and that arrow says which way the
train is pointing, which tells you which end it came in by. If the arrow says it came from the west,
this is a real defect and I will have somewhere to start. If it came from the east, the restriction is
working and the entry can be closed.

**Claude, 2026-08-22.** Marked **reopened** at Adam's instruction and left at **needs test**, which is
the honest state: nobody has run it since it was answered, and what I did was test the rule rather than
reproduce the report.

Recording the distinction so a later reader does not mistake one for the other. The BUILD is proven -
`testBarredArrivalIsNotADestination`, verified by mutation. What is unproven is the railway, and the
railway is what the report was about. A passing unit test is not an answer to "I watched a train do
this", and this entry stays open until somebody watches one not do it.
---

<a id="mt-079"></a>

### MT-079 - 2026-08-18 - Barred terminus loads

**Disposition:** fixed unvalidated
**From:** 2026-08-18 manual test plan, Tier 3 - autonomy in simulation, one train  
**Written:** 2026-08-18

**What to do.** Barred terminus loads. Mark a terminus "trains may turn round here", bar one of its sides,
    reload. It loads - no "configuration is invalid and must be reloaded".

#### Comments

Correct. And reversible locomotives are enforced.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works, with notes.

No errors.  For BottomInner, I don't even see the Trains May Arrive menu (only depart).  But this is OK because it's implicit since it's not connected to anything else, but it would be clearer to show it as greyed out.  That said, no error.  Make it be a warning instead.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:20 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** You are right about BottomInner, and the reason is worth stating: an absent
menu and a menu with nothing to offer look identical from outside. A reader who knows the menu exists
and does not find it has to work out whether the square is special or the application is broken.

**Trains May Arrive** is now shown greyed on a square with only one way in, with a tooltip naming that
side and pointing at the choice that does exist - making the square a pass-through, which is the real
way to stop trains being sent there.

**On "make it be a warning instead" - I have not done this, because I am not sure which thing you
mean, and both readings change behaviour.** The only ERROR that fits the situation is
`NO_ARRIVALS_LEFT`, raised when every arrival side of a station is barred. Downgrading it to a warning
would stop it blocking a configuration from loading, which is a real change and not one to guess at.

Two possibilities:

1. **You saw that error on BottomInner** and think it should be a warning, since a station with one
   way in that nobody bars is fine. Say so and I will downgrade `NO_ARRIVALS_LEFT`.
2. **You meant something else was an error** - a dialog, a log line, a red row in the findings. Tell me
   what it said and I will find it.

You wrote "no error" in the same breath, which is why I would rather ask than pick.
---

<a id="mt-080"></a>

### MT-080 - 2026-08-18 - Collect what the new model offers

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most)  
**Written:** 2026-08-18

**What to do.** Collect what the new model offers. Load the derived configuration. For each of a sample of
    stations - pick ones with a reversing point, a double curve, a one-way section, and a busy junction
    - place a locomotive there and write down every destination offered (the locomotive panel's path
    list, or the station's right-click menu).

#### Comments

Help me collect this programmatically.  You can add code and run 3.0.0 and 2.8.1.  I will then validate.

Sample 5 locs, some reversing, and connect only stations to each other.  Activate all points except reversing points in the sim.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Could not run this.

Defer- we will do an end to end test later.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:20 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Deferred on Adam's word - "we will do an end to end test later". Left at
**needs test** rather than anything softer: a deferred test is one nobody has run, which is exactly
what that state means.
---

<a id="mt-081"></a>

### MT-081 - 2026-08-18 - Collect what the old model offered

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most)  
**Written:** 2026-08-18

**What to do.** Collect what the old model offered. Load the v2.8.1 hand-authored `autonomy.json`. Place the
    same locomotive at the same station. Write down the destinations offered.

#### Comments

Help me collect this programmatically.  You can add code and run 3.0.0 and 2.8.1.  I will then validate.

Sample 5 locs, some reversing, and connect only stations to each other.  Activate all points except reversing points in the sim.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Could not run this.

Likely no longer needed, defer and revisit.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:20 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Deferred - "likely no longer needed, defer and revisit". Not retired, because
"likely" is not "certainly" and retiring it would take it off the ledger where the revisit would have
to be remembered from.
---

<a id="mt-082"></a>

### MT-082 - 2026-08-18 - Compare, and scrutinise the NEW-ONLY entries

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most)  
**Written:** 2026-08-18

**What to do.** Compare, and scrutinise the NEW-ONLY entries. A destination the new model offers and the old
    one did not is the dangerous direction - it may be a journey no train can physically make. For each
    one, ask: does the route reverse at a square where a train cannot reverse? Does it change track
    mid-square at a double curve? If yes, that is a routing bug and the most valuable thing you can
    report.
    - Old-only entries (offered before, not now) matter less, but note them: they are lost capability
      rather than an unsafe move.
18b. **The known-bad journey.** Specifically check whether the new model offers
    **BottomMainA -> BottomSecondary** directly. Adam: it should NOT - a red signal after the end
    requires a stop at TopMainR1 or TopMainR2, a constraint that lived in the hand-authored edge
    config commands and that the derivation cannot currently express.  If it is offered, that is the
    clearest example of the gap, and worth reporting first.

#### Comments

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Could not run this.

Defer

*Run against commit fc672631, build\classes, compiled 22 Aug 19:20 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Deferred on Adam's word. It reads the output of MT-080 and MT-081, so it could
not run before them in any case.
---

<a id="mt-083"></a>

### MT-083 - 2026-08-18 - Run a new-only route in simulation

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most)  
**Written:** 2026-08-18

**What to do.** Run a new-only route in simulation. Pick one and execute it. Watch the train: does it do
    anything physically impossible? This is the strongest single test in the plan.

#### Comments

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-084"></a>

### MT-084 - 2026-08-18 - Two trains, shared junction

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains  
**Written:** 2026-08-18

**What to do.** Two trains, shared junction. Run two trains whose routes cross a junction. They never receive
    conflicting routes through it; the second waits.

#### Comments

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-085"></a>

### MT-085 - 2026-08-18 - Collision refusal

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains  
**Written:** 2026-08-18

**What to do.** Collision refusal. Try to get autonomy to send a second train to an occupied platform,
    including when the occupant arrived from the other direction (the split-copy case, which was
    broken).

#### Comments

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-086"></a>

### MT-086 - 2026-08-18 - Manual displacement still works

**Disposition:** fixed unvalidated
**From:** 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains  
**Written:** 2026-08-18

**What to do.** Manual displacement still works. Right-click-place a train onto an occupied station: it
    displaces the previous occupant. This is intended - you are telling the model where a train is.

#### Comments

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works, with notes.

Works, but there are two facing menus in the track diagram viewer: "Facing" and "{name} is facing".  I like the second one more, but ensure this doesn't affect any other menus.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:20 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** The plain **Facing** menu is gone from the track diagram's right-click; the one
that names the train - **"{name} Is Facing..."** - stays. They were built in different places and both
added to the same menu.

The kept one is the better of the two for a reason beyond preference: it is the same menu the setup
editor offers, built by the same method, so the two surfaces cannot drift into disagreeing about one
square. The one that went was a second implementation of the same question.

**What it did that the other does not,** since you asked me to check for knock-on effects: it also
chose which COPY of a split square the train stands on. That is the same decision from the other end -
a copy is a side to arrive from - so setting the facing reaches it. Nothing a user could say before is
unsayable now.

Two message keys went with it, and the local that fed it. `placeFacing` itself stays: it is still what
places a train from the menu above.

**Nothing else was touched.** The setup editor's own facing menu is the same object and is unchanged;
the only other caller of the removed code path was the placement item above it, which still works.
---

<a id="mt-087"></a>

### MT-087 - 2026-08-18 - Long run

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains  
**Written:** 2026-08-18

**What to do.** Long run. Three or four trains for twenty minutes or more. No train silently stops and stays
    stopped; no gradual gridlock; CPU stays low when a train is boxed in.

#### Comments

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-088"></a>

### MT-088 - 2026-08-18 - Path-integrity failure

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 6 - real hardware, optional  
**Written:** 2026-08-18

**What to do.** Path-integrity failure. Let an accessory fail to confirm. The train stops, its track is
    released, and it resumes on the next cycle rather than dropping out of autonomy until a reload.

#### Comments


**Claude, 2026-08-22.** Adam: "We have a test case for this already, the long running one." That is
`testReturnHomeOnRealLayout`, which is on the slow list and runs in the full battery rather than the
fast one.

Left at **needs test** rather than closed, and the distinction matters: the automated test covers the
PATH INTEGRITY check itself, and this entry is about what the application does when that check fails
in front of a user - the message, and whether the run stops safely. An automated test of the rule does
not answer the question about the surface. See the entry two above this one for the same distinction
drawn about address validation.
---

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Could not run this.

We have a test case for this already, the long running one.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:20 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-001"></a>

### MT-001 - 2026-08-20 - A station moved with its tile

**Disposition:** fixed validated
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

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:46 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
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

**Adam, 2026-08-22 (triage).** Could not run this.

Add a test case for this into the suite.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:46 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Written, in `testRouteEditorRoundTripCases`.

It builds `(A OR B) AND C` from objects, renders it the way the editor does, parses it back, and
requires all three terms to survive - then renders and parses once more, which is what saving actually
does. A second test does the same with the bracket in the MIDDLE, which always worked: if both fail the
parser is broken generally, and if only the first fails it is the leading bracket specifically, which
is what your note describes.

Built from objects rather than typed as text on purpose. The text form is the editor's business, and a
hand-written string would pin my spelling of it rather than its behaviour - the first draft of this
test failed for exactly that reason, on syntax I had invented.
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

**Adam, 2026-08-22 (triage).** Could not run this.

Make a test case for this, all 3 possible directions.  Enable the echo packets option in the main class, and then see if the accessory status is correctly set, and the icon matches.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Half written, and the half that is missing is the half you described.

`testRouteEditorRoundTripCases` covers all three positions: each expands to a PAIR of commands, the
three pairs are genuinely different - two positions expanding alike would send a train to the wrong
road - and each keeps its order through the route text, since the release has to precede the throw or
the point settles wherever the hardware happens to finish.

**What it cannot cover.** "Enable the echo packets option in the main class, and then see if the
accessory status is correctly set, and the icon matches" is a round trip through a Central Station:
commands out, echo back, model updated, icon redrawn. There is no station on this machine, and the one
test that talks to real hardware - `testAutoDetect` - is excluded from the battery for that reason and
recorded as the single deliberate omission in `testEveryTestIsInTheBattery`.

So this entry stays open for the echo half. The instruction above is still the right thing to do; what
has changed is that the model half underneath it is now pinned, so if the icon is wrong you will know
the commands were right.
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

**Adam, 2026-08-22 (triage).** Does not work.

Works for signals and switches.  Does not work for 3-ways though.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Found. `asShown` returned any row that was not an `ACCESSORY` untouched, so a
THREE_WAY row given a signal's address stayed a three-way pointing at a signal - a row standing for a
PAIR of commands aimed at something that has no pair. It re-infers for three-ways now.

**And the setting, which is where this could have gone quietly wrong.** A three-way's words are not
"straight" and "turn", so the existing mapping had nothing to translate. A signal row carrying a word
its own dropdown does not contain is exactly the MT-089 defect - the combo falls back to its first
entry and one click in and out commits it. Anything unrecognised becomes **red**: this is a guess about
your railway, and the guess that stops a train is the one that cannot cause a collision.

**The other direction does not exist, and is worth knowing so nobody hunts for it.** The model has no
`isThreeWay` - a three-way is two accessories this editor draws as one row - so nothing at an address
can announce itself as a three-way the way `isSignal()` announces a signal. An ACCESSORY row can only
become THREE_WAY by being chosen, never by typing.
---
<a id="mt-006"></a>

### MT-006 - 2026-08-20 - Duplicating a command row

**Disposition:** fixed validated
**From:** LT-B1  
**Written:** 2026-08-20

**What to do.** Duplicate a row with the mark beside the trash. The copy lands directly under it. Change its
address, save, reopen.

#### Comments

Editing a route, after sync, teleports the user to the track diagram tab.  Don't do this.  This may be due to the autonomy load, etc.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
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

**Disposition:** fixed validated
**From:** LT-A5, LT-B2  
**Written:** 2026-08-20

**What to do.** Capture, into the commands and into the conditions.

#### Comments

Feedback events do not capture into CONDITIONS.  Switches do.

Signal auto-update does not work on conditions.

Rest works.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
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

**Disposition:** fixed validated
**From:** LT-C5  
**Written:** 2026-08-20

**What to do.** The orange grip at the top right of a selection. Drag the group by it, with picking still on.

#### Comments

OK.  Highlight the "move to' group in blue, not light red, for better clarity.  The selection itself should remain red.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
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

**Adam, 2026-08-22 (triage).** Works, with notes.

Works, but I am getting poor performance while using the UI editor.  This could just be an artifact of the testing framework, but make sure there are no race conditions etc.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Looked into rather than changed, and here is what I found - including the one
thing I would fix if it persists.

**The repainting is already guarded.** `LayoutLabel.setAutonomyAnnotation` compares the new annotation
with the old and returns without repainting when they are equal, which is what `TileAnnotation.equals`
exists for. So the per-edit walk over every tile does not repaint tiles that did not change.

**The candidate is the checker.** `refresh()` runs after every edit and rebuilds the findings list,
which runs `AutonomyChecks` over the whole setup - so every click on a square re-checks the entire
railway. On a large layout that is the expensive thing in this loop, and it is not obviously necessary
after an edit that changed one tile's direction.

**On races specifically,** which is what you asked about: the annotation walk and `refresh()` both run
on the event thread, so there is no cross-thread hazard in that path. The asynchronous one is
`repaintLayout`, which submits to a single-thread executor - and that IS a real ordering problem, found
independently as `GC-A1` and still open. It is about which of two repaints wins rather than about
speed, but it is the only genuine race in this area and it is written down.

I have not debounced the checker, because that is a behaviour change - findings would lag an edit - and
it wants deciding rather than doing quietly. Say the word if the editor still feels slow and I will
put a proposal in front of you.
---
<a id="mt-015"></a>

### MT-015 - 2026-08-20 - Shift Down and Shift Right, then undo

**Disposition:** fixed validated
**From:** hands-on testing  
**Written:** 2026-08-20

**What to do.** Shift Down and Shift Right from the right-click menu, then Control+Z.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
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

**Disposition:** fixed validated
**From:** LT-C2, AR-11  
**Written:** 2026-08-20

**What to do.** "Why is it not moving" on a train with several blocked destinations. The whole answer has to
be readable - it wraps now, and scrolls past a few lines.

#### Comments

OK.  But the bar at the top of the editor has an odd border- give it a light gray background instead.  And there is an odd gray artifact on the right side of it.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
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

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Disposition:** fixed validated
**From:** LT-A1, LT-A6, LT-A7  
**Written:** 2026-08-21

**What to do.** Control+X, Control+V and Delete on the track diagram. Point at a station with a train on it
and press Control+X; point at another and press Control+V. Then check the same two squares in the
autonomy editor - the placement has to have moved there as well, or the next build puts the train
back where it was. With the pointer NOT over the diagram, the same keys must still cut and paste
locomotive buttons exactly as before.

#### Comments

Doesn't work- only goes to the default route.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
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

**Adam, 2026-08-22 (triage).** Does not work.

This vanished, I reported it in another item too.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Same cause as [MT-101](#mt-101), and you were right that it was reported
twice - the edit-locomotive item was suppressed by a gate whose precondition OB-009 had removed. Fixed
there; this entry is worth running again because it goes further than MT-101 does: it asks you to set
an arrival function and then actually hear it fire.
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

**Adam, 2026-08-22 (triage).** Does not work.

Does not work- only first is set to red.  Selection process is ok.

*Run against commit 4ba329ad, build\classes, compiled 22 Aug 23:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Adam, 2026-08-22 (triage).** Works.

*Run against commit 4ba329ad, build\classes, compiled 22 Aug 23:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Adam, 2026-08-22 (triage).** Could not run this.

Check this programmatically.  I previously found an issue where locomotives do not transfer.

*Run against commit 4ba329ad, build\classes, compiled 22 Aug 23:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Adam, 2026-08-22 (triage).** Works.

*Run against commit 4ba329ad, build\classes, compiled 22 Aug 23:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Adam, 2026-08-22 (triage).** Could not run this.

seems to work in the UI, but this should have a programmatic test.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Claude, 2026-08-22, correcting the above: that fix did not take, and I told you it had.** The
duplication review found it. `greyWhatCannotBeEdited(this)` wraps whatever renderer the table has and
installs its own - and six lines later the constructor calls `setDefaultRenderer(Object.class, ...)`
again for the kept-command greying, which replaces it outright. So the command table still shades a
kept ROW and still shades no cell by kind.

Verified by reading `RouteEditorFrame.java:2844` against `:2850`, not taken on the reviewer's word.

**Fixed 2026-08-22**, and the diagnosis changed on the way.

`greyWhatCannotBeEdited(this)` now runs AFTER the table's own renderer rather than before it, so it
wraps that renderer instead of being replaced by it.

But the AR-19 note above was wrong about what was missing. **This table has greyed by kind all along** -
its own renderer has always consulted `model.isCellEditable`, which knows `hasTarget`, `isFunction`,
`hasSetting`, `hasProtocol` and `hasDelay`. I checked the commit before mine and it was there.

What it did NOT do is the background. Most unusable cells are EMPTY - a function number on a signal
command, a protocol on a stop - and grey text in an empty cell looks exactly like black text in an empty
cell. That is what you were asking for, and it is what the wrap adds, along with the exemption that
keeps the + row unshaded.

**What to look for:** open a route with a mixed list. On a signal command, the function-number cell
should carry a faint grey wash rather than just being empty; the same for the protocol cell on a stop.
The + row at the bottom must not be washed at all.

**Adam, 2026-08-22 (triage).** Could not run this.

I don't know what "at danger" means.  Clarify and reopen.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Clarified, as you asked. "At danger" is signalling usage for a signal showing
**red** - stop. The opposite is "clear", which is green.

So the sentence in the instruction means: a route that sets a signal to RED must not quietly become one
that sets it to GREEN. That is the whole risk in this entry, and it is not a small one - it is the
difference between a route that holds a train and one that waves it through.

**Reworded here rather than in the instruction above,** which is append-only: read every "at danger" in
that instruction as "showing red", and "cleared" as "showing green".
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

**Disposition:** fixed validated
**From:** hands-on testing  
**Written:** 2026-08-21

**What to do.** Start a train and stop it by hand before it reaches its next sensor - lift it off, or turn its
power off at the loco. After five minutes the log should name it, name the sensor, and say how long.
Nothing else should change: the train stays waiting, and autonomy carries on around it.

#### Comments

Defer for later.

**Claude, 2026-08-22.** Automated as `testStuckTrainAdvisory`, to the procedure written
under MT-037 - you noted afterwards that it belonged here, and it does: this is the train that stops
between sensors.

Three stations in a line, autonomy simulation OFF so nothing triggers the sensors for us, the quota
shrunk to a second, the train dispatched from one end, the first sensor made by hand, and the second
never made. The log then names the train and the sensor it is waiting for, once.

The quota is a second rather than your ten. Nothing asserted depends on the number, and
`FEEDBACK_ADVISORY_MS` is a public volatile field precisely so a test can shrink it - ten seconds is
the right number for watching a real railway do nothing.

There is no popup, by design: the advisory is a log line and nothing acts on it. If you would rather it
raised something on screen, say so and it is a small change.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---
<a id="mt-037"></a>

### MT-037 - 2026-08-21 - An automatic route says nothing about its trigger

**Disposition:** fixed unvalidated  
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

**Claude, 2026-08-22.** Automated as the second half of `testStuckTrainAdvisory`: a
locomotive called "Dummy Loc" waiting on a sensor through the two-argument wait - which is exactly what
a route's trigger monitor does - and nothing said about it over four quotas. Neither "has not reached"
nor the name "Dummy Loc" may appear at all.

Verified by mutation: putting the advisory back inside the shared wait, which is where it started, fails
this test. That is the leak it exists for.

Your procedure above has moved to MT-036, which is the test it describes; this entry keeps the negative
half, which is the regression guard.

**Adam, 2026-08-22 (triage).** Could not run this.

Add a test for this. many simultaneous auto routes, trigger them synthetically, short duration.

*Run against commit 4ba329ad, build\classes, compiled 22 Aug 23:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---
<a id="mt-038"></a>

### MT-038 - 2026-08-21 - An unreadable UIState.data is kept

**Disposition:** fixed validated
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

**Claude, 2026-08-22.** Run, and it passes: `testUiStateIsNotLostWhenUnreadable` writes
an unreadable file, opens a session, saves, and finds `unreadable<timestamp>UIState.data` in `tc_backup`
holding exactly what the bad file held.

**Your file was backed up first, as you asked, and it needed to be.** The first version of the test took
its copy inside the test method, after the headless check - so the headless pass threw the skip before
the copy was taken, and the teardown, finding no copy and a file on disk, deleted your real
`UIState.data`. It was restored byte for byte from a safety copy I had taken by hand a few minutes
earlier, and I checked the hash before and after: `d500f6b9...`, 1997 bytes, unchanged.

The test now reads the file in `@BeforeClass`, before anything can skip, keeps the bytes in memory,
writes them back unconditionally and asserts that what is on disk afterwards matches. It also only
deletes a file when it knows it created one. The two stray `unreadable...` artefacts my runs left in
`tc_backup` have been removed - they held the test's own rubbish, nothing of yours.

**Adam, 2026-08-22 (triage).** Could not run this.

make a test case for this if you haven't already, or validate that the test case passes if so.  only reopen if it does not.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Checked as you asked - "validate that the test case passes if so, only reopen
if it does not" - and it passes, so this is closed rather than reopened.

`testUiStateIsNotLostWhenUnreadable.testAnUnreadableStateFileIsKept` does exactly what the instruction
above describes: writes something unreadable over `UIState.data`, builds the main window, lets it fail
to read the file, saves, and requires a new `unreadable<timestamp>UIState.data` in the backup folder.
It ran green in the full battery of 2026-08-22 - 83 classes clean, no failures.

Validated on your conditional word rather than on my own say-so, and the run is named so a later reader
can check the claim.

**One thing worth knowing about that test:** it writes to your REAL `UIState.data`, which is why it is
on the slow list and out of the fast battery. It takes a byte-for-byte copy first and restores it, and
that safeguard exists because an earlier version of it destroyed the file for real.
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

**Adam, 2026-08-22 (triage).** Could not run this.

The UI does not allow slashes to be entered.  Make a programmatic test to validate safety.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Adam, 2026-08-22 (triage).** Could not run this.

make a test for this.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Disposition:** fixed validated
**From:** LT-A7  
**Written:** 2026-08-22

**What to do.** Hover a station's NAME on the track diagram and press Control+V.  Not the platform - the name
beside it, or drawn over it.  The train must land on that station.  Then hover a blank square that
carries no name and press it: nothing should happen and the log should say why.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 21:54 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word. Nothing outstanding on this entry.
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

**Adam, 2026-08-22 (triage).** Works, with notes.

Works-ish.  The label persists, but when I move it back and when it was on a cell that was disconnected, the station name briefly changes to [---].  Changes back once connected.

*Run against commit fc672631, build\classes, compiled 22 Aug 21:54 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** "The station name briefly changes to [---]" while the square is disconnected
is correct, and worth explaining rather than changing.

`[---]` is what a caption says when the setup has a station here and nothing is standing on it. While a
sensor is off the track - mid-move, or on a square joined to nothing - there is no Point behind the
caption for a locomotive to be standing on, so the honest answer is the placeholder. It comes back the
moment the square is connected again, which is what you saw.

The alternative would be to keep drawing the last train we saw there, which is the one thing a caption
must never do: it would assert that a locomotive is on track that is not connected to anything.

Left as it is deliberately. If the flicker itself is the annoyance rather than the wording, say so and
I will look at holding the previous caption for the duration of a drag.
---
<a id="mt-044"></a>

### MT-044 - 2026-08-22 - Cut and paste a whole column

**Disposition:** fixed validated
**From:** LT-A8, FR-A1  
**Written:** 2026-08-22

**What to do.** Cut and paste a whole COLUMN that contains a paired link, and one that contains named stations.
The pairing must survive, from BOTH pages - go to the other end and check it still points back.  The
stations must arrive with their names, lengths and facings.  Then check the column you pasted ONTO: it
must not still be carrying the names it had before.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word. Nothing outstanding on this entry.
---
<a id="mt-045"></a>

### MT-045 - 2026-08-22 - The same for a whole row

**Disposition:** needs test  
**From:** LT-A8, FR-A1  
**Written:** 2026-08-22

**What to do.** The same for a whole ROW, which is the same rule with the axes swapped.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works, with notes.

Test is unclear.  If this is related to cutting a row and seeing if stations are there, it passes.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---
<a id="mt-046"></a>

### MT-046 - 2026-08-22 - A link switched off goes grey

**Disposition:** fixed validated
**From:** LT-B3  
**Written:** 2026-08-22

**What to do.** Switch a link off in the autonomy editor.  It must go grey and hatched.  A link that is paired
and in use must be solid and carry its two arrows.  Before this round it was the other way round.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---
<a id="mt-047"></a>

### MT-047 - 2026-08-22 - Go to a link's other end

**Disposition:** fixed unvalidated
**From:** LT-M11  
**Written:** 2026-08-22

**What to do.** Right-click a paired link and choose "Go to the Other End".  It must close and reopen on that
page, at that square, flashing it.  With unsaved work, it must ask first - and answering yes must NOT
lose the pairing you just made.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works.

Works.  Ensure that tunnels can only be paired with other tunnels on the same page (not with links or tunnels on other pages).

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Tunnels now pair only within their own page.

Like-with-like was already enforced - a link could not pair to a tunnel - and the same-page rule for
tunnels was not. The reason the two kinds need different rules is the reason they look identical to
autonomy: both are portals, both traverse the same way. A LINK exists to take a train from one page to
another; that is its whole purpose. A TUNNEL is track that goes behind the scenery and comes out
further along the SAME diagram, so a cross-page tunnel would draw a train entering a hillside on one
page and leaving one on another.

Links are unchanged and may still pair across pages.
---
<a id="mt-048"></a>

### MT-048 - 2026-08-22 - Double-click a train's name

**Disposition:** fixed validated
**From:** LT-F1  
**Written:** 2026-08-22

**What to do.** Double-click a train's name on the running track diagram with autonomy stopped: the placement
view must open at that station.  With autonomy RUNNING, it must do nothing at all.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---
<a id="mt-049"></a>

### MT-049 - 2026-08-22 - The Edit button no longer asks

**Disposition:** fixed validated
**From:** LT-F2  
**Written:** 2026-08-22

**What to do.** Press Edit twice.  The second press must open the same editor as the first, on the page the main
window is showing, without asking anything.  Then use the Autonomy menu's own edit item: that must take
you to the setup editor whatever you used last.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word. Nothing outstanding on this entry.
---
<a id="mt-050"></a>

### MT-050 - 2026-08-22 - The sidebar

**Disposition:** fixed validated
**From:** LT-F2  
**Written:** 2026-08-22

**What to do.** The sidebar.  With more than one page, switch pages from it: same as closing and reopening, and
it must ask about unsaved work first.  Say no: the sidebar must go back to showing the page you are
actually on.  Switch modes the same way.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word. Nothing outstanding on this entry.
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

**Adam, 2026-08-22 (triage).** Works, with notes.

The position of the window still moves around when changing pages.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** "The position of the window still moves around when changing pages" was fixed
after the build you ran this against - your run was 20:32 and the change landed later.

The cause was one remembered window entry PER PAGE, so clicking a tab restored the position that page
had last been left at. There is one entry for the whole window now and it does not move on a switch;
only the size changes, and only upwards. Worth re-running rather than taking my word for it - see
MT-095, which is the entry that carries the change.
---
<a id="mt-052"></a>

### MT-052 - 2026-08-22 - A remembered window size with a sidebar

**Disposition:** fixed validated
**From:** FR-D2  
**Written:** 2026-08-22

**What to do.** Open the editor on a page, resize the window, close and reopen it.  The remembered size must not
have squeezed the diagram now that there is a sidebar taking width from it.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word. Nothing outstanding on this entry.
---
<a id="mt-053"></a>

### MT-053 - 2026-08-22 - Edit Locomotive opens its dialog

**Disposition:** fixed unvalidated
**From:** AR-1, AR-2  
**Written:** 2026-08-22

**What to do.** Right-click a station with a train on it in the autonomy editor.  "Edit Locomotive..." must open
the assignment dialog, not a popup saying "null".  On an EMPTY station there must be no Place Locomotive
item at all - "Add a Locomotive to Autonomy..." is what places one.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works, with notes.

works, but rearrange the menu options as follows:
-add loc to autonomy
-edit loc
-remove loc
-loc is facing...
-home for a locomotive

Also, rename home for a locomotive to "Set Home Locomotive...."

*Run against commit fc672631, build\classes, compiled 22 Aug 22:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Reordered to your list - Add, Edit, Remove, facing, home - and "Home for a
Locomotive" is now **Set Home Locomotive...**, naming the locomotive when there is one.

The group reads as the life of a locomotive on the square now: put one here, edit what is here, take it
away, then which way round it faces and which station it belongs to.
---
<a id="mt-054"></a>

### MT-054 - 2026-08-22 - Combine Linked Pages appears once

**Disposition:** fixed validated
**From:** AR-3, AR-4  
**Written:** 2026-08-22

**What to do.** Open and close the autonomy editor five times, then look at the Layouts menu.  "Combine Linked
Pages..." must appear once.  Its tooltip must wrap instead of running off the screen.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word. Nothing outstanding on this entry.
---
<a id="mt-055"></a>

### MT-055 - 2026-08-22 - Manage Pages and Edit Layout Page

**Disposition:** fixed validated
**From:** AR-5  
**Written:** 2026-08-22

**What to do.** Layouts menu.  "Manage Pages" holds add, rename, duplicate, combine and delete.  "Edit Layout
Page" lists every page and opens the one you pick, in whichever editor you used last.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word. Nothing outstanding on this entry.
---
<a id="mt-056"></a>

### MT-056 - 2026-08-22 - The sidebar with a long page name

**Disposition:** fixed unvalidated
**From:** AR-6, AR-7, AR-8  
**Written:** 2026-08-22

**What to do.** The sidebar with a very long page name.  The buttons must stay one width and show the whole name
in a tooltip.  With more than eight pages the tabs must scroll.  The headings must be blue semibold and
the buttons bold black.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works, with notes.

There is room for many more than 8 pages- make the limit be 20.  Stretch the list down to the editor mode selector.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Twenty before it scrolls, and the list runs down to the mode selector.

Eight was right when the pages were toggle BUTTONS at 26px with their own borders. They are list rows
at 24px now and the strip is the height of the window, so eight was leaving most of the column empty
and scrolling a railway of twelve pages for no reason.
---
<a id="mt-057"></a>

### MT-057 - 2026-08-22 - A train marker and its name

**Disposition:** fixed unvalidated
**From:** AR-13, AR-14  
**Written:** 2026-08-22

**What to do.** In the autonomy editor, place a train on a station.  A white star must appear in the middle of
that square, and the station's label must show the train's NAME in black on white rather than [---].
Regular text labels must stay grey.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works, with notes.

The white star is too big and slightly off center relative to the midpoint of the station.  Be careful with curved stations.  Loc names look OK.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Both halves, and "be careful with curved stations" is what found the real
cause.

The star is smaller - an eighth of the tile rather than a sixth. It was sized when it sat under the
badge; on top of one it read as a shape in its own right rather than as a mark ON it.

**And it was centred on the wrong thing.** The badge is centred on `trackCentre` - "the midpoint of the
route's own two sides, which is the tile centre for a straight and lands on the rails for anything
else" - and the star was centred on the TILE. On a straight they agree, which is why it looked only
slightly off. On a bend the track leaves the middle of the square and they part company completely.

It is on the track now, so it sits on the badge it is drawn over whatever shape the tile is - which is
also the right answer on its own terms, since the star marks a train standing on the RAILS.
---
<a id="mt-058"></a>

### MT-058 - 2026-08-22 - Show autonomy hides the names

**Disposition:** fixed unvalidated
**From:** AR-15  
**Written:** 2026-08-22

**What to do.** On the main diagram, untick "Show autonomy".  The station names must go with the badges.  Change
page and come back: still gone.  Tick it again and they return.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works, with notes.

Works, but rename "Show Autonomy" to "Autonomy Controls"

*Run against commit fc672631, build\classes, compiled 22 Aug 22:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Renamed to **Autonomy Controls**.
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

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-064"></a>

### MT-064 - 2026-08-22 - Highlight on Diagram, and Test Condition

**Disposition:** needs test  
**From:** feature request  
**Written:** 2026-08-22

**What to do.** Open a route that has both commands and conditions, and press **Highlight on Diagram**.
Every square the route COMMANDS - its switches, signals and uncouplers - must light in the diagram's
ordinary yellow, and every square its CONDITIONS name must light in orange, both for five seconds and
then go back to normal by themselves. A route with no conditions lights only in yellow.

The button beside it, which used to say **Test**, must now say **Test Condition** - it evaluates the
conditions, which is not what "test" suggests when there is a whole route on screen.

#### Comments

**Claude, 2026-08-22.** Filed through `feature-requests.md`, and built in the same round - Adam gave
the whole specification with the request, so there was nothing to wait for.

Two colours because they answer different questions: yellow is "this is what the route DOES", orange is
"this is what it LOOKS AT before doing it". Five seconds and self-clearing, so it can be pressed while
reading the route without leaving the diagram marked up afterwards.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Disposition:** fixed validated
**From:** hands-on testing  
**Written:** 2026-08-22

**What to do.** Stop autonomy gracefully and judge how long it feels. It now takes a moment longer than it used
to and is telling the truth for the first time - it waits for trains to reach a station rather than
reporting success immediately. Whether that reads as "a moment" or as "slow" is a judgement only you
can make.

#### Comments

**Claude, 2026-08-22.** Carried over from the "Still untested, and known" list at the bottom of
the old file.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word. Nothing outstanding on this entry.
---

<a id="mt-062"></a>

### MT-062 - 2026-08-22 - Delete, shift and insert have not had the move audit

**Disposition:** fixed unvalidated
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

**Adam, 2026-08-22 (triage).** Could not run this.

Make a test for this.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Written, in `testDeleteAndInsertKeepTheSetup`.

Three checks. Deleting a square takes its own setup and **nothing else** - both halves matter, since a
delete that leaves the setup behind puts a station on track that is not there, and one that takes more
than its square is straightforward data loss. Inserting a line carries the setup of everything it
pushes along. And `planBulkLine` reports both halves of a move while a COPY reports only its landings,
because two squares cannot both be one station.

The rules themselves are pinned by `testAutonomyStoreSettingsMatrix`; what was missing was that delete
and insert produce the right moves and losses for those rules to act on - the half between the editor
and the store. Verified by mutation: making `forgetTiles` tell the setup nothing fails it.

The hands-on instruction above is still worth running, because the test drives the session rather than
the window - it cannot see a menu item that calls the wrong thing.
---

<a id="mt-063"></a>

### MT-063 - 2026-08-22 - A second copy of TrainControl says so

**Disposition:** fixed validated
**From:** AR-16  
**Written:** 2026-08-22

**What to do.** Start TrainControl twice. The second one must say that TrainControl is already running and that
only one copy can run at a time, rather than "Error occurred: Address already in use: Cannot bind".
The detailed error should still be in the console and the stack trace should still print.

#### Comments
Works OK. But it currently says "close the other window". There could also be a test process or CLI version running, so just say window or background process.

**Claude, 2026-08-22.** Reworded: "Close the other window - or the background process, if you have one
running from a script or a test - and try again." Left as **fixed unvalidated** rather than validated,
because the sentence you read is not the sentence that is there now.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---

<a id="mt-089"></a>

### MT-089 - 2026-08-22 - A signal CONDITION offers red and green

**Disposition:** fixed validated
**From:** DD - live defect  
**Written:** 2026-08-22

**What to do.** Build a route whose CONDITION names a signal's address - not a command, a condition. The cell shows
"red" or "green" (it always has). Now click into that setting cell: the dropdown must offer **red and
green**, not "straight" and "turn".

Then click into it and straight back out without choosing anything, and save. The condition must still
say what it said before.

That last step is the defect: the dropdown was built from the stored kind while the cell displayed the
signal wording, so the list did not contain the value on screen, and a combo whose model lacks its
current value falls back to its first entry. One click in and out committed it - a condition testing a
signal at danger quietly became one testing it clear.

#### Comments

**Claude, 2026-08-22.** `asShown` was applied in the conditions table's display path only. It is now
applied in all four - display, editability, edit, and the cell editor - which is what the commands table
has always done by storing the shown row.

Nothing stored changes: `toCommand` treats SIGNAL and ACCESSORY identically and has always accepted all
four words.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---

<a id="mt-090"></a>

### MT-090 - 2026-08-22 - Add Locomotive refuses address 0

**Disposition:** fixed validated
**From:** DD appendix A3.3 - verified  
**Written:** 2026-08-22

**What to do.** Open Add Locomotive, type **0** as the address, and try each decoder type. It must be refused with a
message naming the type. Then try the maximum for that type (it must be accepted) and one past it (it
must be refused).

#### Comments

**Claude, 2026-08-22.** Verified from the source before fixing: the dialog tested the UPPER bound
only, in three separate `if` blocks, with no branch for MULTI_UNIT and an `abs()` above them - so zero
passed all three, because zero is not greater than any maximum, and made a locomotive the model itself
would have refused.

It now asks `MarklinLocomotive.validateNewAddress`, which is the rule, and picks the message from the
type. `testLocomotiveAddressRules` pins the rule - including that zero and negatives are refused for
every decoder type - but note it pins the RULE, not the dialog: the dialog is a Swing screen and this
hands-on check is what covers the delegation.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---

<a id="mt-091"></a>

### MT-091 - 2026-08-22 - ant test runs the whole suite

**Disposition:** fixed validated
**From:** DD-A2 - verified  
**Written:** 2026-08-22

**What to do.** Run `ant test` and count what it runs. It should now run **75 classes** - every test class on disk
except `testAutoDetect`, which probes the network for a real Central Station and is excluded on purpose.

It will take noticeably longer than it used to.

#### Comments

**Claude, 2026-08-22.** Verified by counting rather than by reading: 76 classes carry `@Test`, and
`build.xml` listed 41. **Thirty-five were never run by `ant test`** - among them
`testAutonomyStoreSettingsMatrix`, which exists specifically to catch the setup-collection bug class
that has produced five defects this month, the whole of the route editor's suite, and every test written
in the week to 2026-08-22.

All 34 real ones are added. `TestStationAddress` is not, because it is a helper with no `@Test`, and
`testAutoDetect` stays out for the reason already documented there.

**If you have been treating a green `ant test` as the gate, it has been narrower than the battery I run
from the scratchpad.** That is the whole finding.

**Also 2026-08-22:** the test classes moved into `test/core`, `test/ui`, `test/regression` and
`test/support` - see [test/README.md](../../test/README.md) for which folder a new one belongs in.
`build.xml` needed no change, because it matches on a file pattern that already spans subfolders. If
you have the test folder open in NetBeans it will look rearranged; nothing was deleted.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---

<a id="mt-092"></a>

### MT-092 - 2026-08-22 - The triage app

**Disposition:** fixed validated
**From:** feature request  
**Written:** 2026-08-22

**What to do.** Run `py -3 docs\manual-tests\triage.py` beside TrainControl. Confirm:

- The ledger of open entries loads and matches what is at the top of this file.
- Pick an entry, choose a result, write something, click **Submit and next** - the comment appears
  under that entry's `#### Comments` here, and nothing else in the file moves.
- Add a bug and a feature request from an entry's screen, submit, and confirm they land in
  `issues.md` as `OB-###` items referencing the test.
- Use **New issue** to file something not tied to any test on screen, and confirm it lands in
  `issues.md` the same way, with no test referenced.
- From a terminal, run `py -3 docs\manual-tests\triage.py stats` and `... tests --open` and confirm
  the JSON matches what the app itself shows - this is the API other rounds call instead of reading
  the file by hand.
- The **Launch TrainControl** button starts it with Simulate + Debug (a train can be dispatched in
  simulation without a real Central Station) and the **Output…** window shows its console.
- Close and reopen the app; the entries you already answered are marked, and unanswered drafts (typed
  but not submitted) are still there.

#### Comments

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---

<a id="mt-093"></a>

### MT-093 - 2026-08-22 - A placed locomotive is named, not JSON

**Disposition:** fixed validated
**From:** Adam, screenshot  
**Written:** 2026-08-22

**What to do.** Open the **autonomy editor** on a page where a configuration places locomotives on platforms. Each
captioned square must show the locomotive's **name** - "EN57-203" - inside the caption, in the same
black-on-translucent-white the running track diagram uses, sitting within its tile.

It must NOT show `{"name":"EN57-203"}` in a box wide enough to cover the neighbouring tiles.

Check both an occupied platform and an empty one: the empty one still shows the grey `[---]`
placeholder, and a square with nothing placed must not show the word "null".

#### Comments

**Claude, 2026-08-22.** Your screenshot, and the whole bug is in one line.

A placement is stored as an OBJECT - `{"name": ..., "speed": ..., "arrivalFunc": ...}` - because
`parseAuto` resets whatever a placement omits, so a train's length and functions have to travel with
its name rather than beside it. The label asked for the property and called `String.valueOf` on what
came back. For a JSONObject that is its JSON.

The styling was never wrong: the editor sets the same black-on-translucent-white the running diagram
sets, and both views mutate the same `JLabel` objects, so they size identically. The box looked
different because the text was three times longer, not because it was drawn differently.

**What made it possible.** The unwrapping already existed twice and correctly -
`AutonomyEditorPanel.locomotiveAt` did it, and `Layout.parseAuto` does it with an extra type guard.
The label was a third copy, written separately, that got it wrong. Two right answers written down
separately are what let a third be written that was wrong.

So the fix is not the one line: `AutonomySession.getLocomotiveNameAt` is now the only place that knows
the shape, and both the label and the editor panel ask it. `testAutonomyLabelShowsLocomotiveName` pins
it - seen failing first with exactly the string from your screenshot - and covers the bare-string shape
a hand-edited file can carry, the empty square, and that reading a name does not disturb the settings
stored with it.

I checked every other reader of a point property for the same mistake. They are all shape-aware
(`instanceof Number`, `instanceof String`, an array branch); this was the only one.

**Claude, 2026-08-22, second pass.** You are right, and my first answer was half of it.

The JSON is gone, but the label was still wrong, and I said in my report that the sizing could not be
the problem because "both views mutate the same JLabel objects, so they size identically". They do -
and that was the wrong conclusion, because the two views do not put the same STRING in them.

The running diagram sets a **caption**:

```java
j.setText("[" + name.substring(0, min(len, LAYOUT_STATION_MAX_LENGTH)).trim() + facingArrow + "]");
```

Bracketed, and cut to ten characters. The brackets are the point: a platform is the same width
whatever is standing on it, which is why `[---]` and `[EN57-203 >]` sit on the same tile. The autonomy
editor set the bare, untruncated **name** - so the label was as wide as the name and covered its
neighbours. That is your "rather than seeing its name inside [---]", exactly.

**And a second, separate cause of the white rectangle.** My branch turned `setOpaque(true)` on, then
set a translucent white background - but thirty lines further down, past the end of that branch,
`text.setBackground(Color.WHITE)` runs for every label and overwrote it with fully opaque white. Every
other label survives that because none of them is opaque, so the background never paints. Mine was the
only opaque label on the diagram, so mine was the only one that painted a solid white block. The
translucent background is now applied after that line rather than before it.

Both are fixed together, and `LayoutGrid.stationCaption` is now the one place that spells a caption -
the running diagram asks it too, so the two cannot drift apart again. Verified by mutation: reverting
it to the bare name fails the new tests.

**On your other question - no, I did not add any markers to the station icons.** The only mark I added
is the facing arrow inside the brackets, which the running diagram has always drawn and the editor did
not. The circles, squares, diamonds and arrows on the icons are the editor's existing arrival marks,
which is what your MT-065 note is about - filed as [MT-094](#mt-094).

**Adam, 2026-08-22 (triage).** Does not work.

I still see a large white rectangle overlay with the name of the locomotive, rather than seeing its name inside [---]. This covers other tiles. Also, not sure if you added markers on top of the staiton icons.

*Run against commit cd27e285, build\classes, compiled 22 Aug 17:21 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22, third pass - and this is what fixed it.**

Adam, in conversation: *"Labels look great in the main track diagram, all I'm asking for is for the
autonomy editor [---] labels to follow the same appearance in the editor"*, and afterwards
*"Finally works."* Recording his word here because it arrived in conversation rather than through the
app; moving to **fixed validated** on it.

The third cause. It is the same `JLabel`, with the same styling, in the same grid - what differed was
the STRING.

On the running diagram the caption is registered EMPTY and its text set afterwards by
`updateStationLabels`, so it never reaches the on-tile "multiline hack" thirty lines below, which
rewrites a label as `"<html><br>" + text` with spaces as `&nbsp;`. The editor has no run to wait for,
so it sets the text at construction and picked the wrap up on the way past. The leading `<br>` makes
the label two lines tall and the `&nbsp;` stops it wrapping - and this is the **only opaque label on
the diagram**, so it was the only one whose background painted that area as a solid block. Every other
label gets the same wrap and nobody has ever noticed, because none of them paints a background.

**Why this took three rounds, which is the part worth keeping.** Twice I established that both views
share the same `JLabel` objects and concluded from it that the appearance could not be the difference.
Sharing the object made the difference *invisible, not impossible* - the two views put different
strings in it, and all three causes lived in the string. "It looks right over there" was the question
that solved it, and I should have asked it first: when one surface is correct and another is not, the
cheap move is to diff the two paths, not to reason about the one that is broken.
---

<a id="mt-094"></a>

### MT-094 - 2026-08-22 - Station marks and arrows say what they mean

**Disposition:** superseded
**From:** FR-002 (feature request, filed as OB-001 / OB-002), raised from MT-065  
**Written:** 2026-08-22

**What to do.** Not a test yet - a design decision that has to be made first.

Right now the autonomy editor draws circles, squares and diamonds on station icons, and incoming and
outgoing arrows around them. The shapes are not semantic: which shape you get does not reliably tell
you what the square IS, and the arrows are visually noisy where several meet.

**What is wanted:** a proposal for a better appearance, then the change, then a look at it. Not
started - filing is not a request to build it.

#### Comments

**Claude, 2026-08-22.** Picked up from the Inbox: filed as `OB-001` and `OB-002`, which are the same
request submitted twice two minutes apart, against commits 3a2106ab and cd27e285. Recorded once here.

Raised from [MT-065](#mt-065), which you passed - the marks are correct, they just do not read well.
So this is an appearance change with nothing broken behind it, and MT-065 is **fixed validated**
rather than being held open by it.

Per the SOP, this is on the ledger and not being worked. Say the word and I will propose a scheme
before changing anything, since "propose a better appearance" is the actual request and the drawing
is the easy half.

**Adam, 2026-08-22.** This entry is the thing that was wrong: a feature request, promoted to an
`MT-###` tag the moment it was picked up, sitting in the Tests ledger indistinguishable from an
actual hands-on regression test - "not a test yet" is right there in this entry's own instruction.

**Claude, 2026-08-22 - retired.** `MT-094` is **superseded**, not deleted - the tag stays valid
(rule 2: never reused, never renumbered) and this entry is not touched further, because it is
already cited by tag in a real commit (`cd27e285`) and because "leave the entry where it is" is what
README.md says to do with an obsolete test rather than removing it. Its disposition stays **needs
test** for the same reason: there is no fourth state for "will never be tested" and inventing one
was not part of what was asked.

**The live record is `FR-002` in [issues.md](issues.md), tracked directly rather than promoted.**
Feature requests get a **State** field of their own now - the same three words tests.md's
disposition uses, set by Claude the same way, but living in `issues.md` and never becoming an
`MT-###` tag unless the work genuinely needs a repeatable hands-on test the way a bug fix does. See
`issues.md`'s "What has been picked up" table and the **Feature requests** tab in `triage.py`.

**Adam, 2026-08-22 (triage).** Does not work.

This should not be a MT.  Re-file correctly as a feature request.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Agreed, and it took a new state to say so honestly.

This was a feature request promoted to a test entry by a rule that has since been retired. The live
tracking moved to the `OB-001`/`OB-002` receipt in [issues.md](issues.md), which carries its own State -
so this entry has been asking you to run something nobody would ever run.

The three dispositions had no honest answer for that: it is not waiting to be tested, it is not fixed,
and marking it validated would have been a lie told to get it off a list. So there is a fourth,
**superseded**, defined in the README with a deliberately narrow rule - an entry is superseded when it
is the WRONG SHAPE, never because it is unwelcome, and the thing that took it over has to be named.

The entry stays, because its tag is cited by two commits and by the receipt table, and deleting it
would leave those pointing at nothing.
---

<a id="mt-095"></a>

### MT-095 - 2026-08-22 - The editor stays open when you switch page or mode

**Disposition:** fixed unvalidated
**From:** OB-005  
**Written:** 2026-08-22

**What to do.** In the editor, click through every page tab in the sidebar, and toggle between track and autonomy.
The window must **stay on screen** the whole time - no flash, no disappearing and coming back.

Then check the save/discard logic still holds, because that is the half that must not have changed:

- Make an edit, click another tab, and choose **Save** - the edit is written and the new page opens.
- Make an edit, click another tab, and choose **Discard** - the edit is gone and the new page opens.
- Make an edit, click another tab, and choose **Cancel** - nothing moves, and the sidebar goes back to
  showing the page you are actually on.

Then three things that are new hazards because the window survives:

- Edit page A, switch to page B, press **Ctrl+Z**. It must not undo anything - and in particular must
  not put page A's track onto page B.
- Save on page A, switch to page B, make an edit, and **Cancel** out of the window. Page A's saved work
  must still be there.
- Switch to page B and make an edit, then Cancel. That edit must be undone.

#### Comments

**Claude, 2026-08-22.** Switching no longer disposes the window; it re-points it. The teardown is
unchanged and still runs in full - the diagram is re-read from disk, the setup is put back as it was
found, and the main window is told - so a switch is still an exit as far as the rest of the
application is concerned. Only the frame survives.

**The three checks at the bottom are the ones I would fail.** Making the window survive turns a set of
per-WINDOW fields into per-PAGE fields in one stroke, and every one of them was correct before:

- The undo history is a stack of snapshots of a page's components with **nothing in it naming the
  page** - it never needed one. Left alone, one Ctrl+Z after a switch writes the old page's track over
  the new one, from the user's own undo key.
- `autonomyAsOpened` is what Cancel restores, taken when the window opened. Arriving from the setup
  editor it survived untouched, so Cancel would have undone the setup past work the user was asked
  about and chose to save on the way there. Arriving from the track editor the teardown had already
  consumed it, so Cancel would have had nothing to put back.
- The three tile selections name squares by page, so a selection carried across named squares on a
  page no longer on screen.

All four are cleared or re-taken in `arriveAt`, and `testEditorSwitchClearsPageState` pins that -
verified by mutation, not just by passing.

The one behaviour I could not preserve exactly: `LayoutEditor.layout` had to stop being `final`. Every
read of it is live, so nothing else changes, but it does mean the window is no longer built around one
diagram - which was the assumption the old close-and-reopen was resting on.

**Adam, 2026-08-22 (triage).** Works, with notes.

Looks good, but the window location memory is messing with the single window view now.  Have a single memory entry for the whole window, with only the size being variable.

*Run against commit fc672631, build\classes, compiled 22 Aug 18:46 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** There is one remembered entry for the editor window now, whatever page it is
showing.

It was the page name and the tile size, so every page had its own remembered position AND its own
remembered size. That was defensible while a page change meant a new window. Once the window stopped
closing, clicking a tab picked the window up and moved it, because the page you clicked remembered
somewhere else - which is exactly "the window location memory is messing with the single window view".

The window no longer moves on a switch. Only the size changes, and only upwards - see MT-096.
---

<a id="mt-096"></a>

### MT-096 - 2026-08-22 - The editor opens at the size of its diagram

**Disposition:** fixed unvalidated
**From:** OB-003  
**Written:** 2026-08-22

**What to do.** With **remember window location** switched OFF, open the editor on several pages in turn - a big
diagram and a small one. Each should come up sized to fit its own diagram, not to whatever the last
page needed.

On a diagram bigger than your screen, the window must stop at the screen edge - the right-hand side
and the bottom must both be reachable, and the scrollbars must be on screen.

With **remember window location** switched ON: resize the editor on one page, close it, and open it
there again. Your size must come back. Then switch to a page you have never sized by hand - that one
should be fitted to its diagram, not given the size you chose for the other page.

#### Comments

**Claude, 2026-08-22.** Both halves of your report were the same cause. Window bounds are
remembered **per page** - the key is the page name and the tile size - so every page came up at
whatever size it was last left at, which for a page opened once on a smaller diagram is too small for
what is on it now.

That is right when the user chose that size and wrong when nobody ever did, and the two were
indistinguishable, because the fit-to-diagram was only ever computed for a **brand new window**. It is
now computed for any page with no remembered bounds, on a switch as well as on opening.

And capped: a diagram wider than the screen produced a window wider than the screen, with its
right-hand edge and the scrollbar that would have reached it both off the side. The window is also
nudged back on screen if the new size pushed it off an edge it was already sitting near.

**Adam, 2026-08-22 (triage).** Does not work.

It is still too small- but I think the window persistence is getting in the way.  Would probably be OK on a fresh layout- test offline.

*Run against commit fc672631, build\classes, compiled 22 Aug 18:46 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22, second pass.** You were right that the persistence was in the way, and it was
the per-page part of it.

Two changes together. There is one remembered entry for the whole window rather than one per page
(MT-095), and the fit now runs on **every** arrival rather than only on a brand-new window - so a page
that needs more room gets it whenever you switch to it.

**And the fit only ever grows.** Running it on every arrival means it has to be able to leave a window
alone as well as enlarge it, or switching from a big page to a small one would shrink the window under
somebody who had just made it bigger on purpose. So a size you chose is a floor, and a diagram that
needs more than that raises it, capped at the screen.

Worth testing on a fresh layout as you suggested, and also on your own: the old per-page entries are
still in the preferences and are simply no longer read.
---

<a id="mt-097"></a>

### MT-097 - 2026-08-22 - Sidebar: pages are a list, modes are radio buttons

**Disposition:** fixed unvalidated  
**From:** FR-003  
**Written:** 2026-08-22

**What to do.** In the editor sidebar, the pages are now a **list** - click a row to go to that page. The mode is a
pair of **radio buttons**, Track and Autonomy.

Check: hovering a row whose name is cut off shows the whole name; the current page is the selected row
and stays selected if you cancel a switch; arrow keys do NOT switch pages (only a click does); and with
one page the sidebar is still hidden entirely.

#### Comments

**Claude, 2026-08-22.** The comment on the old control said the type "lives here and nowhere else -
swap the body and the rest of the window does not notice", which turned out to be true: both changes
are confined to `buildPageControl` and `modeTab`.

One decision worth knowing. The list listens for a **click**, not for a selection change. A
`ListSelectionListener` also fires when the selection is set in code - which `syncSidebar` does on
every cancelled switch - and the arrow keys would then start a switch for every row travelled through.
A click is the gesture that means "go here".

**Adam, 2026-08-22 (triage).** Works, with notes.

Looks good.  Reduce padding to the right of the table.

*Run against commit fc672631, build\classes, compiled 22 Aug 18:46 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** The padding was reduced after the build you ran this against - yours was 18:46
and the change landed later. The strip's right inset went from eight to two, and the list is wider by
the same amount: a list has its own inset before the text and a selection bar to its edge, so eight on
the right read as sixteen.
---

<a id="mt-098"></a>

### MT-098 - 2026-08-22 - One-Way Run is a button, and asks which way

**Disposition:** fixed unvalidated
**From:** FR-004  
**Written:** 2026-08-22

**What to do.** The right-click menu should no longer offer **Make a One-Way Run from Here**, and the divider that
sat above it should be gone with it - check the menu does not end with a stray separator.

In the autonomy editor there is a **One-Way Run** button beside Test Condition and Why. Press it, click
one end of a run, then the other. It must then ASK which way trains may run, naming both squares, with
a Cancel. Check:

- Choosing the first option closes the run one way; choosing the second closes it the other way. Look
  at the arrows to confirm it did what you picked.
- Cancel leaves the track alone and puts you back at "click one end".
- Clicking the same square twice says so and waits, rather than doing something odd.
- After one run is set the button stays armed, so you can do a yard without pressing it each time.
- Pressing another tool button, or right-clicking, disarms it.

#### Comments

**Claude, 2026-08-22.** Moved rather than rebuilt: the two-click machinery was already there, and
this puts it where the other two-click gesture lives. The Tool enum's own comment said a mode "genuinely
needs a mode, because it takes two clicks to say one thing. Everything else names one tile and belongs
on that tile's menu" - which is precisely why this one did not belong on the menu.

**The direction question is new, and is the part I would look at hardest.** Two squares describe a run;
they do not describe a direction. The old menu version took the direction from the order you happened to
click in, which is a guess, and a wrong guess closes a stretch of railway the wrong way round and leaves
nothing on the diagram saying which way you meant. It asks now.

**Adam, 2026-08-22 (triage).** Does not work.

I don't see such a button.

*Run against commit fc672631, build\classes, compiled 22 Aug 18:46 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** You are right and the cause is embarrassing: the button was built, given a
tooltip, wired into the `Tool` enum and into the disarm path - and never added to anything.

Nothing failed and nothing warned. An unmounted Swing component is just a live object with no parent,
so every test of the machinery around it passed. It is on the panel now, sized to the column like the
two above it.
---

<a id="mt-099"></a>

### MT-099 - 2026-08-22 - A train mark shows on a station with nothing else on it

**Disposition:** fixed unvalidated
**From:** FR-005  
**Written:** 2026-08-22

**What to do.** In the autonomy editor, look at a square where the setup places a locomotive. It carries a small
white star.

The case to check is a square with **nothing else** on it - no badge, no arrival marks, no length, not
selected. Set the visibility dropdown to a mode that draws no marks if you need to. The star must still
be there.

#### Comments

**Claude, 2026-08-22.** The mark you asked for already existed - `paintTrainMark` draws exactly a
small white six-armed star with a dark edge under it, and has for a while. It was invisible on precisely
the squares your request is about.

`paint()` opens with `if (isBlank()) return;`, and `isBlank()` lists every field that counts as
content: marks, length, selected, badge, ignored, traces, arrivals. It did not list `occupied`. So an
annotation carrying nothing but a train was blank and the star was never painted - and a station with a
badge was never blank, so it appeared there and the gap looked like it did not exist.

The field had been added to `equals` and to `hashCode`. The method that decides whether the object is
worth drawing at all is the one that gets missed, because it is not one anybody is looking at while
adding a field. `testTrainMarkIsNotBlank` pins it.

**Adam, 2026-08-22 (triage).** Does not work.

I can't see it- but it should overlay on top of the middle of the sensor, not somewhere else.

*Run against commit fc672631, build\classes, compiled 22 Aug 18:46 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Found, and your instinct about where it should be is what located it.

The star WAS being drawn, in the middle of the tile - underneath the badge. Both are centred, and a
station's badge is half the tile across while the star's arms are a sixth, so on exactly the squares
this is about the badge covered it completely.

It is drawn after the badge now, which is also the right reading: the badge says what the square IS,
which does not change; the star says a train is standing on it now, which does. The changing fact
belongs on top.
---

<a id="mt-100"></a>

### MT-100 - 2026-08-22 - An invisible edit turns the arrows back on

**Disposition:** fixed validated
**From:** OB-008  
**Written:** 2026-08-22

**What to do.** In the autonomy editor set the visibility control to **None**, then click a square to change its
direction.

The control must jump to **Restrictions** so you can see what just happened, and the hint line should
say it did. Check that it only does this from **None** - set it to **All** or **Arrivals**, click a
square, and it must leave your choice alone.

Also check it fires for the other two ways of shutting a direction: a click on a switch or crossing,
and the One-Way Run button.

#### Comments

**Claude, 2026-08-22.** With directions hidden, the click worked, the hint line said so, and the
diagram - which is where you were looking - was identical before and after. That reads as a broken
control, and the natural response to a control that does nothing is to click it again, which cycles the
square on to a state nobody asked for.

Restrictions rather than All, because that is the default for the same reason: open track is most of a
layout and its arrows say what a reader can already assume.

Only from None, and only on an edit that actually changed something. Somebody who chose All or Arrivals
has chosen a view that already shows their edit, and moving them off it would be the same rudeness in
the other direction.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---

<a id="mt-101"></a>

### MT-101 - 2026-08-22 - Placing a locomotive updates the labels, and there is one way to do it

**Disposition:** fixed unvalidated
**From:** OB-009  
**Written:** 2026-08-22

**What to do.** In the autonomy editor, right-click a station and use **Add a Locomotive to Autonomy...**.

- The caption must update **immediately** - no closing and reopening, no page switch.
- **Remove** must update it immediately too.
- The list should now offer the whole roster. Locomotives autonomy already runs appear first, marked
  *(already in autonomy - will be moved here)*; choosing one of those must move it, and it must
  disappear from where it was.
- **Move a Locomotive to This Station...** should no longer be on the menu.

Then re-check the first part of your report - whether a locomotive lands at the station it belongs to.
See the note below: I think that was this same bug, but I want your eyes on it.

#### Comments

**Claude, 2026-08-22.** Your OB-009 had three parts; two are definitely fixed and the third I think
is the same bug wearing a different description.

**The labels.** `placeLocomotive` ended with `session.placeLocomotive(tile, name)` and no `refresh()`.
Every other edit on that menu refreshes. So the placement was written and nothing redrew - and a
placement is the one kind of edit whose entire visible result IS the label. Remove had the same gap.

**Move, retired.** Three items on that menu asked which locomotive: Add, Move, and the edit dialog
underneath them - which does the same job and more, since it can also change what the train does once
it is there. The one that went is the one whose only advantage was a shorter list. Add now offers the
whole roster, marking the trains already in autonomy, so it can still do what Move did.

While rewriting it I removed a second lift-off loop that walked `getReducer().getPoints()`. That is not
the same set as the configuration - **the reduction omits excluded pages** - so a train standing on an
excluded page was not lifted, the build emitted it at two Points, and `fromJSON` answers that by
invalidating the whole layout. The session's own `placeLocomotive` already does this over the
configuration, which is complete.

**"Doesn't correctly place it at the station where it belongs" - please re-check.** I could not find a
placement going to the wrong square, and I want to say what I did NOT change rather than quietly leave
it. Placing from the **track diagram** menu picks among the copies of that square at random, on
purpose: a split square has several Points, the first is whichever side the build walked in by, and
always taking it would face every train on a platform the same way. It is the same square either way,
so the caption should be right.

My guess is that this was the missing refresh - a placement that does not appear looks exactly like a
placement that went somewhere else. If it still happens with the labels updating, tell me the station
and which copy it landed on, and that is a different bug.

**Adam, 2026-08-22 (triage).** Does not work.

Placement works, but label is not updated.

Critical: I no longer see the option to edit the locomotive.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Both halves, and both were mine.

**The label.** I added `refresh()` and that was the wrong tool. `applyCaption`, twenty lines further up
the same file, already says why: "the editor's own grid has to be REBUILT, not repainted: the caption
is part of the tile art, and the annotation refresh that follows every other edit does not touch it."
A placement changes the caption, so it needs what a caption change needs - `onDiagramChanged`, which
already existed. Placing and removing both use it now.

**The edit option, which you rightly called critical.** Its gate was `point.getCurrentLocomotive() ==
null`, and the comment above that gate named the precondition that made it safe: the item was
suppressed when empty because "Add a Locomotive to Autonomy..." and "Move a Locomotive to This
Station..." were both directly above it. OB-009 retired Move, so two doors became one, and the gate
closed the one that was left.

Worse, the gate asks the RUNNING layout what is standing there while the editor writes to the SETUP -
so a train placed from that very menu is not on the running layout yet, and the item vanished at
exactly the moment somebody had placed a train and wanted to set its arrival function.

The gate is gone. `menuLabelFor` already says the right thing either way - "Place Locomotive At..." or
"Edit Locomotive At..." - so nothing needed deciding.

This is the second time today a rule outlived the precondition that made it safe. Both times the
comment above it said what that precondition was.
---

<a id="mt-102"></a>

### MT-102 - 2026-08-22 - Two labels renamed

**Disposition:** fixed validated
**From:** OB-010, OB-011  
**Written:** 2026-08-22

**What to do.** Autonomy editor: the lengths checkbox reads **Track Lengths** rather than "Show track lengths".

Autonomy menu: **Choose Routing Logic...** rather than "Route Choice".

#### Comments

**Claude, 2026-08-22.** Both changed in all eight message bundles, ASCII-escaped as they have to be.
The English is the only one that was actually translated - the others carried the English string
already.

**Adam, 2026-08-22 (triage).** Works, with notes.

Works.  Make sure the chosen routing logic is saved in a preference and persisted across sessions.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** The routing logic already persists, and has since before this entry - so
there is nothing to build and nothing to re-run.

Choosing an option calls `prefs.put(PATH_PREFERENCE_PREF, option.name())`, and `buildPathPreferenceMenu`
reads it back at startup and applies it with `Layout.setPathPreference` before the menu is shown. If it
ever appears not to stick, that is a defect rather than a missing feature, and worth its own entry.

Validated on your "Works" for the labels themselves; the note attached to it needed an answer rather
than a change.
---

<a id="mt-103"></a>

### MT-103 - 2026-08-22 - Starting autonomy leaves you where you were

**Disposition:** fixed validated
**From:** OB-012  
**Written:** 2026-08-22

**What to do.** Right-click a station on the **track diagram** and start autonomy. You must stay on the track
diagram - no jump to the autonomy tab.

Then check the thing that jump was there for: while autonomy is running, right-click again and confirm
**Stop Autonomy Gracefully** is on that same menu, so there is still a visible way to stop it from
where you are.

#### Comments

**Claude, 2026-08-22.** Removed, and the reason it was added is worth recording since it was not a
whim: started from the track diagram, autonomy began "with nothing on screen to say so and no visible
way to stop it again".

Both halves of that turned out to be wrong about this surface. The diagram itself shows the trains
moving, which is a better answer than a list of them; and the same right-click menu that started
autonomy carries Stop Autonomy Gracefully the moment it is running. What was left was the cost - being
moved off the page you were looking at, by a menu you opened on that page.

The second half of the test is there because if that Stop item ever stops appearing, this becomes the
wrong decision again.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---

<a id="mt-104"></a>

### MT-104 - 2026-08-22 - The tile menu, reordered

**Disposition:** fixed unvalidated
**From:** OB-013  
**Written:** 2026-08-22

**What to do.** Right-click a station in the autonomy editor. Top to bottom, the station part should now read:

- what the square is, and which way trains turn on it (unchanged)
- **protected by signal**
- **home for a locomotive**
- **Maximum Train Length** - now on the menu itself, not inside Advanced Parameters
- *divider*
- **Trains May Arrive...**
- **Trains May Depart...**
- **Advanced Parameters...** - last

Inside **Advanced Parameters** there should now be **Segment Length...** (renamed from "Length...")
where Maximum Train Length used to be, plus station priority, speed multiplier and excluded
locomotives.

Then right-click a piece of **plain track** - not a station. It should also have an **Advanced
Parameters** submenu, holding only **Segment Length...**. See the note below: this is the one part I
would push back on.

#### Comments

**Claude, 2026-08-22.** All five moves are in.

**One consequence to look at, because it is a judgement call I made on your behalf.** You asked for
Length to move into Advanced Parameters. Advanced Parameters only existed for STATIONS, and a length
applies to any square - so if I had moved it there and left it there, setting a length on plain track
would have become impossible.

So Advanced Parameters is now built for every square. On a station it holds four settings; on plain
track it holds one, and reaching Segment Length costs an extra click it did not cost before.

The alternative is to leave Segment Length loose on the menu for plain track and inside Advanced for a
station - which nothing becomes unreachable under, but which puts the same item in two different places
depending on the square. I judged one consistent home worth the extra click, but it is your menu: say
the word and I will make it conditional instead.

**Adam chose neither, and the third answer is better than both.** Segment Length is loose on the menu
again, for every square, with the new name kept - the rename was the part that was worth having, since
"Length..." did not say length of what. Advanced Parameters goes back to being a station's own drawer
and is null on anything else.

So the instruction above is now: **Segment Length... is on the menu itself, one click away, on a
station and on plain track alike.** Advanced Parameters holds the other three station settings and
does not appear on plain track at all.

**Adam, 2026-08-22 (triage).** Does not work.

Should be:

...
- *divider*
- **Trains May Arrive...**
- **Trains May Depart...**
- *divider*
- **protected by signal**
- **Advanced Parameters...**
...
Move - **home for a locomotive** to the bottom of the locomotive area
Put - **Maximum Train Length** back into the advanced parameters.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Reordered to your list. Three moves from what I had:

- **home for a locomotive** to the bottom of the locomotive group. Your order is the better reading: a
  home is a fact about a LOCOMOTIVE, so it belongs with the items about the train rather than with the
  ones about the platform.
- **Maximum Train Length** back inside Advanced Parameters.
- **protected by signal** down to the foot, after a divider, beside Advanced Parameters.

So the tail now reads: divider, Trains May Arrive, Trains May Depart, divider, protected by signal,
Advanced Parameters.
---

<a id="mt-105"></a>

### MT-105 - 2026-08-22 - Sidebar text at the standard size

**Disposition:** fixed validated
**From:** OB-014, OB-015  
**Written:** 2026-08-22

**What to do.** In the editor sidebar, the page list and the **Track Diagram** / **Autonomy Setup** buttons should
be at the same size as ordinary text elsewhere in the window, and not bold.

Check a page name that is too long for the strip still shows in full as a tooltip, and that the list
still shows a whole number of rows before it starts scrolling.

#### Comments

**Claude, 2026-08-22.** Both were Segoe UI Bold 12, which is `docs/UI-standards.md`'s rule for
BUTTONS - correct for what they used to be, and carried over unnoticed when one became a list and the
other a pair of radio buttons. Both are Plain 14 now, the rule for text.

The list's row height went from 22 to 24 to match, and the scroll pane's height with it, so the strip
still shows whole rows. The `tab()` helper that built the old toggle buttons had no callers left and
is gone.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---

<a id="mt-106"></a>

### MT-106 - 2026-08-22 - The viewer keeps its own appearance while an editor is open

**Disposition:** fixed unvalidated
**From:** OB-016  
**Written:** 2026-08-22

**What to do.** Open the **track diagram editor**, then switch to **autonomy** using the sidebar. Now look at the
main window's track diagram behind it.

It must look exactly as it did before you opened the editor - no grid lines, no greying, and clicking a
tile behaves as it always does. It must not change until the editor is CLOSED.

Try it in both directions, and more than once: this was a race, so a single pass proving nothing is a
real possibility.

#### Comments

**Claude, 2026-08-22.** Mine, from the change that stopped the editor closing to switch.

`repaintLayout` does not repaint - it POSTS the work and builds the main window's grid inside that
task. The main window shares the `LayoutDiagram` with the editor, so an `arriveAt` that runs before
that task sets `layout.setEdit(true)` first, and the viewer is then built in edit mode.

The old close-and-reopen never hit it: `openLayoutEditor` posted, and its `render()` posted again, so
the flag landed two events after the repaint. That ordering was an accident of the indirection. Both
directions post now, deliberately - and it is covered by a test, because it otherwise reads as a
redundant `invokeLater` that somebody would tidy away.

**Worth doing more than one pass on.** It is a race, and the fix is an ordering, so "it looked right
once" is weaker evidence here than usual.

**Adam, 2026-08-22 (triage).** Does not work.

When switching to autonomy, the autonomy grid view still appears in the viewer.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** My first fix was half of it, and this is the other half.

The ordering fix stopped `arriveAt` jumping ahead of the queued repaint, which was real - but the flag
it sets is on the DIAGRAM, and the main window shares that diagram. So any later repaint of the viewer,
from any cause, still re-laid it the editor's way.

The flag was answering two questions at once. "Being edited" should stop the VIEWER'S TILES routing
clicks - that is right, and unchanged. It should not change how the viewer is LAID OUT, which is a fact
about one grid rather than about the diagram. That decision now asks whether this particular grid is
inside an editor, which is the question it was always trying to ask.

**Claude, 2026-08-22, a correction to the note above.** The same reviewer showed that my first
explanation was wrong in an important way, and since I wrote it here as fact it needs saying plainly.

I said the ordering fix - an extra `invokeLater` on each side - put the flag behind the queued repaint.
**It cannot.** `repaintLayout` submits to a single-thread `ExecutorService` and only calls
`invokeLater` from inside that, so its EDT task is not queued when mine is; an `invokeLater` cannot
order against a task that has not been posted yet.

What fixes the symptom is the LayoutGrid change described above, which holds whoever wins the race. The
misleading comment is corrected in the source, and the test that pinned the false ordering has been
replaced by one that pins the real thing.

Still open, and honestly outstanding rather than quietly dropped: the same shared flag still decides
whether the VIEWER'S TILES accept clicks. That is a smaller hazard - a brief window during a switch -
and it is recorded as GC-A1 rather than fixed in the same breath as the thing it sits beside.
---

<a id="mt-107"></a>

### MT-107 - 2026-08-22 - The track palette comes back when you leave autonomy mode

**Disposition:** fixed validated
**From:** OB-017  
**Written:** 2026-08-22

**What to do.** Open the **track diagram editor**, switch to **autonomy**, then switch **back to track**.

The **New Components** panel must be full of track pieces again, and its heading must read what it
always read - not "Autonomy Tools". Place a piece from it to confirm the palette is live and not just
drawn.

#### Comments

**Claude, 2026-08-22.** Also mine, and the same cause as MT-106: a fresh window was hiding a
teardown that had never run.

Autonomy mode EMPTIES the palette panel and gives it a different layout to hold the setup column.
Leaving removed the column and put nothing back - which was invisible for as long as coming back meant
a new window, because a new window ran the constructor and the constructor built the palette.

It is rebuilt rather than hidden, because the panel's LAYOUT changes too, and a hidden component in the
wrong layout is not the same as one that is not there. The heading is read off the form at construction
and put back from that copy: it lives in generated code that cannot be edited by hand.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22.** Validated on Adam's word in the round of 22 August. Nothing outstanding on this entry.
---

<a id="mt-108"></a>

### MT-108 - 2026-08-22 - Route editor: Save in the corner, and Cancel asks

**Disposition:** fixed unvalidated  
**From:** OB-018  
**Written:** 2026-08-22

**What to do.** Open the route editor. **Save** is in the bottom right corner with **Cancel** immediately to its
left.

Then the part that is not just a move. Type something - change a name, add a command - and press
**Cancel**. It must ask whether to throw the changes away, the same as pressing Escape does.

Press Cancel with nothing changed and it must close without asking.

#### Comments

**Claude, 2026-08-22.** The move is what you asked for. The second half is something I found while
making it, and it is worth a look rather than a mention.

Escape has always run `closeIfThrowingNothingAway`, whose own javadoc says why: "closing one by
accident - Escape, or the window's own X - threw away everything typed since it opened with no warning
at all". The **Cancel button** went straight to `dispose`.

So the obvious way out was the one that did not ask, and the keyboard shortcut was the safe one -
exactly backwards. Both go through the same question now.

I have changed this without being asked, on the grounds that a button labelled Cancel silently
destroying work is a defect rather than a design. If you actually want Cancel to be the no-questions
exit, say so and I will put it back - but then Escape should match it, because the two doing different
things is the part that cannot be right either way.

**Adam, 2026-08-22 (triage).** Works, with notes.

Good, but remove the horizontal rule just above the cancel and save buttons.  Stretch the condition window to end right above the test condition button.

*Run against commit fc672631, build\classes, compiled 22 Aug 19:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22, second pass.** Both notes done, and the second one needed a decision you may
want to reverse.

The rule above the buttons is gone. It was drawn to separate the buttons that finish with the window
from the ones that act on a row, and those are at opposite ends of the window now - so it was
separating two things that no longer touch.

**"Stretch the condition window to end right above the test condition button"** could not be done by
stretching alone: the plain-English reading sat between the table and the buttons and took a line plus
two gaps out of the middle. It is above the table now, under the section heading, so the table runs all
the way down to the buttons that act on it. It reads as a subtitle to the section rather than a
footnote to the table - which is what it is, since it restates the whole condition rather than the
selected row. If you would rather have it back underneath, say so and the table keeps whatever is
left.
---

<a id="mt-109"></a>

### MT-109 - 2026-08-22 - Track lengths: hotkey, focus, and size

**Disposition:** fixed unvalidated  
**From:** OB-019  
**Written:** 2026-08-22

**What to do.** In the **autonomy editor**:

- **Ctrl+G** shows and hides the track lengths, and the checkbox moves with it. Press it again to
  confirm it toggles rather than only turning on.
- Close the editor and reopen it: the setting must have been remembered, so the shortcut has to write
  the preference the same way a click does.
- Tick the **Track Lengths** box by clicking it, then press **Ctrl+Z** or **Delete**. Those must still
  work - before this, clicking the box took the focus and killed the window's shortcuts until you
  clicked something else.
- The length numbers should now read at the same size as the address labels, not smaller.

In the **track diagram editor** (not autonomy), Ctrl+G must do nothing at all - no dialog, no error.

#### Comments

**Claude, 2026-08-22.** Three things, and the middle one was not on your list.

**The focus.** `showLengths` went through `control()`, which sets the font and nothing else, while
`excludePage` beside it sets `setFocusable(false)` explicitly. That matters more than it sounds: the
editor's shortcuts are bound to the FRAME, and the code that binds them says so - "every control is
setFocusable(false) and tiles are JLabels that never take focus, so the FRAME is the focus owner". A
focusable control in that column takes the focus away and the shortcuts stop firing until something
else is clicked. So ticking the box quietly disabled Ctrl+Z.

**The key is Ctrl+G**, because the letters that mean anything were taken - Ctrl+D is aDdresses, Ctrl+L
is Labels, and lengths are the third number this diagram writes on a tile. lenGth is the best of what
was left. Say if you would rather have another.

It goes through `doClick()` rather than setting the field, so the checkbox's own listener writes the
preference and redraws - a shortcut that set the field directly would toggle the display without
remembering it, which is the sort of difference nobody finds until they wonder why the setting keeps
resetting.

**The size** is a third of the tile now, which is what an address label gets. The two sit in opposite
corners of the same square in the same colour, so the smaller one read as a footnote to the larger
rather than as the same kind of fact.


**Claude, 2026-08-22, before you tried it.** An independent reviewer caught this and it is worth
knowing before you spend a keystroke on it: **Ctrl+G could not fire in either mode.**

The dispatcher returns early on `isAutonomyMode()` - every shortcut below that line places, cuts,
rotates or retextures a tile, and none of those mean anything while setting autonomy up. My branch was
below the return, so it never ran in autonomy mode. And `toggleTrackLengths` returns when there is no
autonomy panel, so it did nothing in track mode either. Two guards written a fortnight apart, each
correct, mutually exclusive.

It is handled above the guard now, which is where it belongs: it is the one shortcut that is FOR
autonomy mode. The rest of MT-109 - the focus and the font - was unaffected.
**Adam, 2026-08-22 (triage).** Works, with notes.

Control+G works, but control +L does not in the autonomy editor.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-110"></a>

### MT-110 - 2026-08-22 - The autonomy column is narrower, and three labels changed

**Disposition:** fixed unvalidated  
**From:** OB-020  
**Written:** 2026-08-22

**What to do.** Open the autonomy editor. The tools column down the left should be noticeably narrower than it was,
and closer to the width of the New Components palette in the track editor.

**Look at what is in it.** The buttons are sized to the widest of them, so if anything is now cut off
or cramped, that is the thing to report - the number is a guess and easy to change.

The visibility control should read **Track Directions**, and its options **Show All**, **Restrictions
only**, **Hide All**, **Station Arrivals**.

#### Comments

**Claude, 2026-08-22.** The column is 150 rather than 170, matching `SIDEBAR_WIDTH` - the other
fixed strip in that window, and the nearest thing to a right answer available.

**Why it is not an exact match.** The New Components palette you asked it to match has no fixed width
at all: it is three columns of tile icons, so it is as wide as the tile size makes it, and at 60px
tiles it is wider than the autonomy column now is. "Match the track diagram editor" is a number that
changes with the zoom, so I picked the fixed strip instead and am telling you rather than implying it
is exact.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-111"></a>

### MT-111 - 2026-08-22 - Layouts menu: order, and one divider too many

**Disposition:** fixed unvalidated  
**From:** OB-021  
**Written:** 2026-08-22

**What to do.** Open the **Layouts** menu. **Edit Layout Page** should sit directly below **Manage Pages**, not at
the bottom of the menu.

Open **Manage Pages**. There should be no double gap in it - no two dividers next to each other, and
none at the very top or bottom.

#### Comments

**Claude, 2026-08-22.** The placement was a plain `add()`, which appends - so "beside Manage Pages
rather than inside it" put it beside nothing, at the end of the menu. It is inserted after Manage
Pages by looking for it, so it stays put if the menu grows.

**The dividers are swept rather than removed by index.** Two of them ended up adjacent because two
items were taken off a menu the form generates, and the dividers that framed them stayed. An index
would be a fact about the generated form - it moves the next time somebody adds an item in the
designer, and a hard-coded one would then delete a divider that was doing its job. The sweep asks the
menu what it is holding and drops any separator that no longer separates anything, including leading
and trailing ones.

**Adam, 2026-08-22 (triage).** Works, with notes.

Works, but Layout greys out all options while Autonomy hides them.  Grey them out in Autonomy instead.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-112"></a>

### MT-112 - 2026-08-22 - Home assignments: the three rules that were unreachable

**Disposition:** fixed unvalidated  
**From:** OB-022 (DD-A6)  
**Written:** 2026-08-22

**What to do.** Three checks, all in the autonomy editor's tile menu.

**1. A home naming a locomotive autonomy no longer runs.** Set a station's home to some locomotive,
then take that locomotive out of autonomy. Open **home for a locomotive** on that station again: it
must still show the old name, not "None". Press **Cancel** and the home must be unchanged.

Before this, the name was missing from the list, the combo fell back to "None", and pressing **OK**
cleared the station's home without anyone asking for it.

**2. A home the locomotive cannot reach.** Assign a home to a station that locomotive cannot rest at -
a platform it is too long for, or one it cannot reach. It must **warn**, defaulting to No, and let you
say yes anyway.

**3. Excluding a locomotive from its own home.** Set a station's home to a locomotive, then use
**Advanced Parameters -> excluded locomotives** on that same station and exclude it. It must warn,
defaulting to No.

#### Comments

**Claude, 2026-08-22.** `OB-022`, from `DD-A6`. The three rules existed and had no production caller
between them - the graph window was deleted and took four of `HomeLocomotiveMenu`'s five callers with
it, leaving the RULES in the abandoned copy and the USE in the surviving 24-line one. And the tests
passed the whole time, because they call the dead code directly.

**The report's preferred fix does not quite fit, and it is worth saying why.** It suggested re-wiring
the live paths through `editHomeLocomotive` and `confirmExclusion`. Those write the RUNNING LAYOUT -
`setHomeLocomotive` on the Layout - while this editor writes the SETUP. They are not two doors to one
room; they are two rooms. So the rules came to the live door instead of the door being re-pointed.

**What that means for each.** `HomeStaging.canBeHome` now has a production caller for the first time
since the graph window went. The preselect rule is implemented where the list is built. The third I
reimplemented rather than called: `homeBrokenByExcluding` takes a running `Point` and `Locomotive`
objects, and the editor has a square and some names, and the rule itself is one comparison - so calling
it would have meant building objects to ask a question about strings. Said out loud because it is a
duplication, small and deliberate.

**Still outstanding from DD-A6:** roughly 200 unreachable lines in `HomeLocomotiveMenu`. They are no
longer MISLEADING - the live path has the rules now - but they are still dead, and the report is right
that leaving them is not free. Deferred rather than done while Adam is testing.

**Adam, 2026-08-22 (triage).** Could not run this.

Make a test case for this, you should be able to validate it.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-113"></a>

### MT-113 - 2026-08-22 - Tool labels, and a heading with nothing under it

**Disposition:** fixed unvalidated  
**From:** OB-027, OB-032  
**Written:** 2026-08-22

**What to do.** In the autonomy editor's tools column: **Why not Moving?**, **Name Everything...**,
**Exclude Page**.

Then right-click a square connected to nothing - an unlinked link, or a piece of track with no route
through it. **Trains May Depart...** must not appear at all. On an ordinary square it must still be
there with its usual contents.

#### Comments

**Claude, 2026-08-22.** The three labels are straightforward.

The heading is worth a word. A submenu built by `title()` starts with one disabled item, so "is it
empty" asked as `getItemCount() == 0` is never true - the check has to ignore the heading, which is
what it does now. A heading is a promise that something follows it; alone it reads as a list that
failed to load rather than as a square with no choices.

Same reasoning as the popup guard in `LayoutLabel`, where an empty menu "reads as a fault".

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-114"></a>

### MT-114 - 2026-08-22 - No findings while autonomy is unloaded

**Disposition:** fixed unvalidated  
**From:** OB-029  
**Written:** 2026-08-22

**What to do.** With a setup on disk but **unloaded**, open the diagram editor and close it again. The
strip above the track diagram must show no error or warning counts at all.

Then load a configuration and confirm the counts come back.

#### Comments

**Claude, 2026-08-22.** The guard was there and asked the wrong question. It skipped the findings when
there was no setup **on disk** - `session.exists()` - and Unload does not delete anything. Its own
tooltip says so: "unloads the running configuration without deleting anything, so the diagram stops
showing stations and trains until you load one again".

So the diagram stopped showing stations and trains, and went on counting errors about a configuration
nobody was running - and closing the editor put the count back every time, which is why it looked like
the editor causing it.

It now also requires a configuration to be loaded. A finding is a statement about the configuration
being run; with none chosen, there is nothing to make a statement about.

**Adam, 2026-08-22 (triage).** Works.

Filed from this test: OB-038 (bug - locomotive placement on export).  They are in `issues.md` until they are picked up.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-115"></a>

### MT-115 - 2026-08-22 - Both menus decline while an editor is open, and lead back to it

**Disposition:** fixed unvalidated  
**From:** OB-033  
**Written:** 2026-08-22

**What to do.** Open the diagram editor, then put the main window in front of it.

Open the **Layouts** menu: everything in it must be greyed, with one item at the top saying an editor
has the diagram. **Click that item** - the editor must come to the front.

Do the same from the **Autonomy** menu, whose equivalent item was already there but did nothing when
clicked.

Close the editor and confirm the Layouts menu is fully usable again and the item is gone.

#### Comments

**Claude, 2026-08-22.** The Autonomy menu already declined, for reasons its own comment gives: every
item saves the setup or rebuilds the main window, and an open editor makes both unsafe - saving commits
edits that editor has not saved, so its Cancel then has nothing to take back. The Layouts menu does the
same work and had no such guard.

**And both now lead back.** A disabled item states the problem and offers nothing, and the window it is
talking about may well be behind this one - which is exactly why somebody reached for the menu.

The guard runs when the menu is OPENED rather than when an editor opens. There is no single moment
"an editor opened" is known to that menu, and asking at the moment somebody looks is both simpler and
always right.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-116"></a>

### MT-116 - 2026-08-22 - Renaming a station keeps its label

**Disposition:** fixed unvalidated  
**From:** OB-034  
**Written:** 2026-08-22

**What to do.** With a configuration loaded, rename a station from the **track diagram's** right-click
menu - not from inside the editor. Its label must still be on the diagram afterwards, showing the new
name.

Then do it while trains are running: the rename must still be accepted, and the label may lag until the
run stops. That is deliberate - see below.

#### Comments

**Claude, 2026-08-22.** Found, and the codebase had already written down what would happen.

A rename rebuilds the SETUP's graph, so the station index now maps that square to the new name. The
RUNNING layout was built from the configuration as it was and still holds the old one - so everything
that goes through those names looks up a Point the running layout has never heard of, the caption finds
nothing, and the label goes blank. Rename it back and it matches again, which is exactly what you saw.

`autonomyEditorClosed` has rebuilt the running layout all along, which is why the editor never showed
this: closing was the only door to that rebuild, and a station can be renamed from the diagram's own
menu without opening an editor at all. The rename asks for the same rebuild now.

The failure was described in advance, in that method's own comment: *"from that moment the running
layout holds names the setup no longer knows ... The caption looks up its station and finds nothing, so
the label goes blank."*

**Not while trains are moving**, for the reason the original rebuild gives: rebuilding underneath a
running railway is what `prepareAutonomyReload` exists to refuse. So during a run the rename is stored
and the label catches up when the run ends.

**Adam, 2026-08-22 (triage).** Does not work.

Weird- the label moves around to adjacent cells on rename.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-117"></a>

### MT-117 - 2026-08-22 - The train star is visible again, and five smaller fixes

**Disposition:** fixed unvalidated  
**From:** OB-030, OB-031, OB-035, OB-036, OB-037  
**Written:** 2026-08-22

**What to do.** **The star (OB-037).** With a locomotive on a station, the white star must be clearly white - a mark
ON the badge, not a dark smudge. Check a 30px tile as well as a 60px one; the small size is where this
went wrong.

**Placing from the VIEWER (OB-035).** Right-click a station on the track diagram - not in the editor -
and add a locomotive to autonomy. The caption must update at once, without closing anything.

**Link pairing (OB-031).** Switch a link off, then pair another link to it. Both ends must come out
switched on.

**Tooltips (OB-030).** Hover the items on the Autonomy menu. Long tooltips must wrap rather than run
off the screen.

**Warnings (OB-036).** A finding about another page reads `(Page 2) ...` rather than `On 2 - ...`.

#### Comments

**Claude, 2026-08-22.**

**OB-037 was mine, and twice over.** MT-057 shrank the star from a sixth of the tile to an eighth - but
the two stroke widths that draw it are floored at 3.0 and 1.6, and those floors were chosen when the arm
was a sixth. At a 30px tile an eighth gives an arm of 3.75, so a 3px dark outline was drawn around a
1.6px white core: the outline swallowed the star and left a dark smudge.

Three numbers that have to agree. The arm is a seventh now and both floors came down with it, and the
comment says so, because shrinking one of the three is exactly what I did.

**OB-035** is the same shape as MT-101 on a different surface. Placing from the deep menu writes the
setup, and the caption being looked at belongs to the main diagram, which draws from the RUNNING
layout - so the setup gained a locomotive and nothing on screen changed, while the facing menu, which
reads the setup, listed it happily. That is the inconsistency you spotted, and it is the two surfaces
disagreeing about where the truth lives. The placement now rebuilds the running layout when there is no
editor grid to redraw - the same seam OB-034 uses.

**OB-031.** Pairing switches both ends on rather than refusing. Somebody pairing two links is saying
they are joined; a disabled far end is a setting made earlier about a square that was not joined to
anything, and the newer statement is the one they mean.

**OB-030.** `wrapped()` already existed and wraps at 320px; the Autonomy menu did not use it once. All
eight tooltips go through it now.

**Adam, 2026-08-22 (triage).** Does not work.

037- Stars work, but are offcenter on curve stations.
035- ADDING TO AUTONOMY here still doesn't take effect.  placing is OK.
031, 030, 036 - file under new MTs.  One test per MT ticket.

*Run against commit 4ba329ad, build\classes, compiled 22 Aug 23:18 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-118"></a>

### MT-118 - 2026-08-22 - Export and import restore a locomotive placement

**Disposition:** fixed validated
**From:** OB-038
**Written:** 2026-08-22

**What to do.** Nothing by hand - this is covered by a test, and the entry exists so the question has a
recorded answer rather than being asked again.

#### Comments

**Claude, 2026-08-22.** Already covered, by `testDataSafetyRoundTrips.testTheExportedBundleComesBackWholeAndChangesNothing`
- written for MT-074 on the same day you asked this.

It exports a setup carrying a name, a station designation **and a locomotive placement**, checks the
files are byte-for-byte unchanged by the export, then imports the bundle into a folder that has never
seen it and requires all three to arrive - the placement by name, asserted directly.

Validated on the test rather than on my word: it ran green in the full battery of 2026-08-22, 83
classes clean.

If you have a bundle that does NOT restore a placement, that is a different defect from the one this
covers and worth its own entry with the file attached - the round trip through the code is proven, so
the interesting case would be a bundle written by an older build.

---

<a id="mt-119"></a>

### MT-119 - 2026-08-22 - The trace arriving at a curved station

**Disposition:** fixed unvalidated
**From:** OB-026  
**Written:** 2026-08-22

**What to do.** Not fixed - **diagnosed**, and filed here because the fix touches geometry that has
already been got wrong once and wants your eye before I change it.

When you next run a train to a curved station, look at the LAST tile of the trace and at a curve the
train merely passes THROUGH. The reported difference should be visible: the one it stops on cuts across
the tile, the one it runs through follows the rail.

#### Comments

**Claude, 2026-08-22.** Your "running through curves looks OK" is the half of the report that located
this.

A run is drawn one segment per square, from the midpoint of the edge it came in by to the midpoint of
the edge it leaves by. On the LAST square there is no edge it leaves by, so the line runs to the
tile's geometric **centre**:

```java
int[] b = segment.getTo() == null ? centre : midpoint(segment.getTo(), width, height);
```

On a straight, the centre is on the rail and the stub looks right. On a curve the rail hugs the corner
and never passes through the centre, so the stub cuts across the tile - which is exactly "draws a
straight line on the tile, rather than following the shape of the station". Running THROUGH a curve
uses two edge midpoints and matches the art, which is why that case looks fine.

**Why I have not just fixed it.** The straight chord is deliberate, and the comment beside it records
what happened when somebody changed this geometry before:

> "Bending the run line through the tile centre instead put it at forty-five degrees to the track under
> it - two strokes cutting across the corner the rail cuts through - so on every turn of a route the
> highlight and the railway disagreed about where the train was going."

So the through-case must not be touched. What needs to change is only the END stub, and to end it on
the rail the overlay has to know which sides this tile's track actually uses - which it does not: it
knows the segment it was given, and at the end of a run that is one side and a null.

**What I would do.** Give the end stub the tile's own track sides, the way `TileAnnotation.trackCentre`
already does - "the midpoint of the route's own two sides, which is the tile centre for a straight and
lands on the rails for anything else". That sentence is the fix, and it is already written and already
used for the badges; the work is getting the component to the overlay.

**And there is now a way to check it.** `testDiagramLooksRight` renders a claimed run across a curved
tile to a PNG, so this can be looked at before and after rather than reasoned about - which is what the
last three drawing defects needed and did not have.

**Adam, 2026-08-22 (triage).** Does not work.

not fixed.  test the current config from BottomMainB to TopMainR2Inter

*Run against commit 4ba329ad, build\classes, compiled 22 Aug 23:27 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-22, later - confirmed by picture rather than by reading.**

`testDiagramLooksRight.testARealPathToACurvedStationIsDrawn` now renders it. A locomotive is placed and
asked for a real destination, and the run that comes back is laid and drawn exactly as the running
overlay would draw it: `02 0314-1 DDR` from BottomMainC to `TopMainR2Inter (southbound)`, 48 contiguous
squares, ending on the FEEDBACK_CURVE at `1 - Main:0,11`.

Both halves of your report are in that one picture:

- **The arrival.** The trace comes down the vertical rail, runs straight PAST the point where the rail
  turns right, and stops with a rounded cap in the middle of the square. The black track is plainly
  heading off to the right underneath it. That is the stub ending at the tile centre, and the centre of
  a curve is not on the rail.
- **A curve it runs through** (the corner at `1 - Main:0,1`) hugs the rail round the elbow and matches
  the art exactly - so the through-case really must not be touched, as the comment above says.

The diagnosis in the preceding comment stands, and the fix stays scoped to the end stub alone.

Adam supplied the destinations - "TopMainR2Inter or TopMainR1Inter", then "Place at BottomMainB or A" -
which is the only reason this got off the ground; see the note below about what I got wrong first.

**Claude, 2026-08-23 - fixed.** Adam: "Fix the curved line - I confirmed the bug is still there per your
0,11 screenshot."

Three lines, in the three places that each knew one third of it:

- `TileAnnotation.trackCentre` is public now. It was already computing exactly the right point - "the
  midpoint of the route's own two sides, which is the tile centre for a straight and lands on the rails
  for anything else" - and had been placing the station badges by it since MT-057.
- `TileOverlay.paint` takes that point and uses it wherever a segment has no side to leave by.
- `LayoutLabel` hands it over, because the label is the only thing holding both the annotation and the
  overlay for a square.

**The through-case is untouched, and that was the constraint.** `centre` is read at exactly four places
in `paintRun`, and every one of them is guarded by a null side - which only happens at the two ends of a
run. A curve the run passes through has two real sides and never reads it. The picture confirms it: the
elbow at `1 - Main:0,1` is pixel-for-pixel what it was.

Red before green. `testAutonomyDiagramMonitor.testTheStubAtTheEndOfARunStopsOnTheRail` paints a stub
into an image on a tile whose track runs N-E and requires the ink to reach (45,15), the rail's midpoint,
and to stop well short of (30,30), the tile centre. It failed with "nothing was drawn where the rail
actually runs" before the change. It is painted rather than computed on purpose: the last three drawing
defects were all missed by reasoning about the code.

Lite battery green afterwards - 76 classes, no failures.

**Still worth your eye**, which is why this is unvalidated: on a curve the stub now ends underneath the
station badge, so what you should see is the trace stopping cleanly AT the badge rather than shooting
past it. On a straight nothing should have changed at all.
---
