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
| [MT-004](#mt-004) | 2026-08-20 | A three-way point in a route | fixed unvalidated | TD-6 |
| [MT-011](#mt-011) | 2026-08-20 | A Central Station route is read-only | needs test | hands-on testing |
| [MT-022](#mt-022) | 2026-08-21 | A locomotive's settings from the tile menu | fixed unvalidated | LT-M1, LT-M2, LT-M3, LT-M4 |
| [MT-023](#mt-023) | 2026-08-21 | Two signals on one station | fixed unvalidated | LT-C1, LT-M5, LT-M6, LT-M7 |
| [MT-032](#mt-032) | 2026-08-21 | Two trains, one dispatched onto a long path | needs test | TR-A22 |
| [MT-035](#mt-035) | 2026-08-21 | The Central Station switched off mid-session | needs test | hands-on testing |
| [MT-043](#mt-043) | 2026-08-22 | A sensor nudged onto its own label | needs test | LT-A9 |
| [MT-060](#mt-060) | 2026-08-22 | testAutoDetect needs a Central Station | needs test | hands-on testing |
| [MT-080](#mt-080) | 2026-08-18 | Collect what the new model offers | needs test | 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most) |
| [MT-081](#mt-081) | 2026-08-18 | Collect what the old model offered | needs test | 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most) |
| [MT-082](#mt-082) | 2026-08-18 | Compare, and scrutinise the NEW-ONLY entries | needs test | 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most) |
| [MT-083](#mt-083) | 2026-08-18 | Run a new-only route in simulation | needs test | 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most) |
| [MT-084](#mt-084) | 2026-08-18 | Two trains, shared junction | needs test | 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains |
| [MT-085](#mt-085) | 2026-08-18 | Collision refusal | needs test | 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains |
| [MT-087](#mt-087) | 2026-08-18 | Long run | needs test | 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains |
| [MT-088](#mt-088) | 2026-08-18 | Path-integrity failure | needs test | 2026-08-18 manual test plan, Tier 6 - real hardware, optional |
| [MT-124](#mt-124) | 2026-08-23 | The train star, and the mark that says where the train is | fixed unvalidated | OB-037 |
| [MT-134](#mt-134) | 2026-08-23 | Four things the night review found | fixed unvalidated | NR-1, NR-2, NR-3, NR-4 (2026-08-23-night-review.md) |
| [MT-135](#mt-135) | 2026-08-23 | Renaming a page keeps its autonomy setup | fixed unvalidated | OB-049 |
| [MT-136](#mt-136) | 2026-08-23 | Two more of one shape, from the history review | fixed unvalidated | TD-1, TD-2 (2026-08-23-three-day-history.md), OB-046 |
| [MT-137](#mt-137) | 2026-08-23 | The round of bugs from Adam's last test pass | fixed unvalidated | OB-045, OB-046, OB-047, OB-048, OB-050, OB-051, OB-052 |
| [MT-139](#mt-139) | 2026-08-23 | A train dispatched by hand now counts as a run | fixed unvalidated | - |
| [MT-140](#mt-140) | 2026-08-23 | Bless the baseline once you are happy with the railway | needs decision | - |
| [MT-141](#mt-141) | 2026-08-23 | Editing a placement while trains are out puts the others back where they started | fixed unvalidated | - |
| [MT-142](#mt-142) | 2026-08-24 | A page keeps its identity when other pages change | fixed unvalidated | OB-059, OB-060, OB-061 |
| [MT-143](#mt-143) | 2026-08-24 | Four small ones from the same test pass | fixed unvalidated | OB-054, OB-055, OB-056, OB-057 |
| [MT-144](#mt-144) | 2026-08-24 | The editor window comes forward, and the info mark is an icon | fixed unvalidated | OB-058, OB-063 |
| [MT-145](#mt-145) | 2026-08-24 | A locomotive rename reaches a setup nothing has open | fixed unvalidated | OB-062 |
| [MT-146](#mt-146) | 2026-08-24 | A train may leave the point that holds a station back | fixed unvalidated | FR-001 |
| [MT-147](#mt-147) | 2026-08-24 | The restored layout is the one you had | needs test | the 23 August data loss |
| [MT-148](#mt-148) | 2026-08-24 | A page that will not load keeps its setup | fixed unvalidated | OB-068 |
| [MT-149](#mt-149) | 2026-08-24 | The timetable survives renaming a locomotive | fixed unvalidated | OB-069 |

Everything else - 113 of 149 - is **fixed validated** and needs nothing from you unless the
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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Could not run this.

make sure there is a test for this.  close out if so.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** "Make sure there is a test for this. close out if so." There is:
`testDataSafetyRoundTrips.testTheExportedBundleComesBackWholeAndChangesNothing`.

It exports a setup carrying a name, a station designation and a locomotive placement, checks the source
files are byte-for-byte unchanged by the export, then imports the bundle into a folder that has never
seen it and requires all three to arrive. Closed.

**Claude, 2026-08-24.** Adam asked to confirm a test exists before closing this. It does: the export/import round trip - `testDataSafetyRoundTrips.testTheExportedBundleComesBackWholeAndChangesNothing`. Closed.

---

<a id="mt-075"></a>

### MT-075 - 2026-08-18 - Page files

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Could not run this.

make sure there is a test for this.  close out if so

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** "Make sure there is a test for this. close out if so." There is:
`testDataSafetyRoundTrips` writes a page, then requires a `.bak` beside it - and it plants something no
save writes, so the backup being found proves it is the state this build replaced rather than a file
that happened to be lying there.

**And it does happen on your machine**, which is worth recording since your note was "I don't see the
.bak": while looking through `cs2_sample_layout/config/gleisbilder/` today I passed `1 - Main.cs2.bak`
and `1 - Main and neighbours.cs2.bak`. They are written beside the page, not in a backup folder, which
is probably why they were easy to miss. Closed.

**Claude, 2026-08-24.** Adam asked to confirm a test exists before closing this. It does: the page files themselves - `testDataSafetyRoundTrips.testSavingAPageLeavesOneBackupAndAWholeFile` and `testLayoutFolderRobustness`. Closed.

---

<a id="mt-076"></a>

### MT-076 - 2026-08-18 - Running path drawing

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-077"></a>

### MT-077 - 2026-08-18 - Caption direction arrow

**Disposition:** superseded
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
**Adam, 2026-08-23 (triage).** Works, with notes.

i got two MT tickets for the same test.  please avoid duplication and don't reopen tickets already validated with a was-red test case.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23 - superseded, and you were right to call it out.**

"I got two MT tickets for the same test. Please avoid duplication and don't reopen tickets already
validated with a was-red test case."

The duplicate is [MT-125](#mt-125), which I filed from OB-039 on 2026-08-23. It asks what this entry
asks - that the arrow on a caption is right, for a train placed by hand and for one autonomy drove
there - and you have since validated it. So this one is taken over rather than left as a second copy of
a question already answered.

**Why it happened, since that is the part worth fixing.** The SOP already says to check before filing:
"Before filing anything ... check whether it is already there", and it is explicit about the reasoning -
a round decides something is missing, files it, and a later round reaches the same conclusion because
nothing recorded the first. But it says that about the INBOX, and I was filing into the TESTS ledger
from a bug that had already been picked up. The check I skipped was the one the SOP had not thought to
ask for.

It asks now - see README.md.

**On the second half:** an entry you have validated stays validated. Where a later change touches it,
that shows as the reopened marker rather than as a disposition I have taken back.
---

<a id="mt-078"></a>

### MT-078 - 2026-08-18 - Barred arrival is honoured

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Could not run this.

make a test case for this on a contrive autonomy layout by changing the settings programmatically

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23 - I need a decision from you before writing this one, and here is why.**

"Make a test case for this on a contrived autonomy layout by changing the settings programmatically."
That much I can do. What I cannot settle from the code is WHAT the test should assert, because the
codebase already holds a deliberate answer that contradicts the report.

Your finding was that in MANUAL operation you could send a train from Tunnel to BottomMainA although
BottomMainA bars arrivals from the west. But manual dispatch and autonomy are filtered differently ON
PURPOSE - the tiering is deliberate, and a destination excluded from what autonomy would choose is not
refused when a person asks for it by hand. On that reading what you saw is the design working.

So there are two possible rules and they need different tests:

1. **Barred arrivals are advisory for a person.** Autonomy will not route into a barred side; a manual
   dispatch may, because the operator is looking at the railway and has said what they want. The test
   asserts autonomy never offers it, and says nothing about manual.
2. **Barred arrivals are a property of the TRACK.** Nothing may arrive that way, whoever asked - the
   restriction describes something about the approach rather than a preference. The test asserts both,
   and the manual path needs a fix.

I have not guessed, because the two differ in what the railway does with a train you dispatched by hand,
and that is your call rather than mine. Tell me which and the test follows in either case.

**Claude, 2026-08-23 - answered and done.** Adam: "the first one. We should let the user know a train
can't come in in any way (warning). If manual only, it's info."

So the rule is that a barred arrival is ADVISORY: autonomy will not route a train into a barred side, and
a person driving one by hand may. What you saw was the design working.

**What changed.** Your two severities turned out to belong to two conditions that both already existed,
one of them with the wrong severity:

- **Every way in barred** was an ERROR, which blocks the whole setup from starting. It is INFO now. The
  platform is still reachable - by hand - so refusing to run the railway over it was the wrong answer,
  and the message now says so: "You can still send one there by hand."
- **Nothing can come in by any means** - a square no track reaches at all - is `POINT_ISOLATED`, and it
  is a WARNING already. Nothing to change.

**The test you asked for**, on a contrived layout with the setting changed programmatically:
`testBarredArrivalIsNotADestination.testAStationWithEveryArrivalBarredIsOnlyInformation`. It bars both
sides of a two-ended platform and requires the finding to be INFO. Verified by mutation - putting ERROR
back fails it. The build half you already had is
`testTheBarredCopyIsNotAStation`.

**One thing your report turned up that was a real bug**, found while checking this: the barred copy of a
platform was emitted with no protecting signal, on the reasoning that it is not a station. But a train
CAN be standing there - that is what your ruling says - and the signal decides what to show by asking
every Point it protects, so it never asked that one. The signal showed green over an occupied platform.
Fixed, with a test (UR-6).

**Claude, 2026-08-24.** Adam asked to confirm a test exists before closing this. It does: a barred arrival - `testBarredArrivalIsNotADestination`, four tests including that unbarring restores it and that a barred copy keeps its protecting signal. Closed.

---

<a id="mt-079"></a>

### MT-079 - 2026-08-18 - Barred terminus loads

**Disposition:** fixed validated
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

**Claude, 2026-08-24.** Automated, so this no longer needs Adam's hands:
`testBarredArrivalIsNotADestination.testATerminusWithABarredSideStillLoads`.

Nobody had covered it, and it is the one combination the neighbouring test's comment singles out as
dangerous - "the `stops` variable exists for a case where the model DOES refuse - a terminus that is
not a destination - and answers a refusal by invalidating the whole layout". Barring a side is exactly
what stops a copy being a destination, so two settings that are each harmless are, together, the shape
that refuses the entire configuration.

The rule the builder states is the one asserted: no copy is emitted as a terminus unless it is a
destination. The test also asserts that the fixture actually produced a terminus, because without that
it would pass by asking the question about nothing - which is how the first version of it passed.

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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Claude, 2026-08-24.** Reopened. Adam's recorded result on this is **does not work**, and it was
sitting at *fixed validated* - closed on the strength of the automated tests while the hands-on check
had never passed. An entry whose owner has said it does not work is not validated, whatever the suite
says.

His way in: "Use DEBUG_SIMULATE_PACKETS = true to simulate that there is a station, and confirm that
way." That flag lets the route run without the ironwork being present, so the ORDER of the two commands
and the pause between them can be watched in the log rather than on the layout - which is the part that
was actually in doubt. The model-level cover is real (`testThreeWaySwitch` pins the pause on the first
command and requires each position to come back as itself), but it cannot answer "did the right motor
move first on the bench".

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
**Adam, 2026-08-23 (triage).** Does not work.

Use DEBUG_SIMULATE_PACKETS = true to simulate that there is a station, and confirm that way.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23 - partly done, and the rest needs one thing I do not have.**

"Use DEBUG_SIMULATE_PACKETS = true to simulate that there is a station, and confirm that way." That flag
does let a test drive accessories without hardware, and the first half of this entry - a three-way in a
route stays one row and stays LEFT across a save and reopen - is covered by
`testCommandRow.testEveryEditableKindRoundTrips` and `testTheKindsWithNoTargetSaySo`.

The second half is the interesting one and is not covered: "the motor that ends up straight should move
first, and the other should follow after a pause." That is an assertion about the ORDER of two commands
and the gap between them, and `Accessory.isThrow`'s own comment says why it matters - "a three-way
turnout is two drives, and its diverging drive has to be commanded after the other has been released,
never before".

To test it I have to observe the order in which the two motors were commanded, and nothing in the model
records that today: the accessories carry their state, not a history. The honest options are to add a
small recording seam - a list of (accessory, aspect, time) written only under DEBUG_SIMULATE_PACKETS -
or to leave this as a hands-on check with a real three-way in front of you.

I would rather add the seam than leave an ordering rule untested, but it is a change to production code
for a test's benefit, so I am asking first.

**Claude, 2026-08-23 - done, and I was wrong about needing a change to the app.**

Adam: "refer to the wizard code in the 2.8.1 route editor - it has the correct ordering."

I read it. The route editor decides nothing about the order itself - `RouteEditorFrame` hands the whole
question to `ThreeWaySwitch`, and says why in a comment: "nothing about the order or the pause is decided
here, or in two places, which is how the two would drift apart." So the wizard's ordering and the
ordering under test are the same rule, which is the answer to what I asked.

**And the seam I asked for is not needed.** I said the order in which the two motors were commanded was
not observable, so testing it would mean adding a recording hook to production code for a test's benefit.
That was wrong: a route executes its commands in the order they appear in its text, so the text IS the
record. The test reads them back through `RouteCommand.fromLine` and asserts the order off the PARSED
commands - the release first, carrying its pause, then the throw.

`testRouteEditorRoundTripCases.testEachPositionKeepsItsOrderThroughTheRouteText`. That test previously
could not fail: it built the route text from the same list, in the same order, that it read the two
commands from, so a reversed pair swapped all three together (TD-6). Reversing `expand` now fails it.

Also covered at the model level, which I had not credited: `testThreeWaySwitch` pins the pause on the
first command, requires each position to come back as itself, and states the rule for right - "settles
the first and then turns the second".

One thing writing it taught me, worth recording because it is easy to get backwards: STRAIGHT releases
BOTH motors. It is the position neither of them chooses, so "the second command is the throw" is true of
every position except that one.
---
<a id="mt-005"></a>

### MT-005 - 2026-08-20 - A signal address typed into a switch row

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works, with notes.

current behavior accepted

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** "Current behavior accepted" - closed on that. Three-ways keep the
behaviour you saw; nothing further is pending here.
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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
**From:** LT-B3, LT-F2  
**Written:** 2026-08-20

**What to do.** Pair a tunnel or a link. The diagram should highlight each candidate as you scroll the list,
not only after pressing OK.

#### Comments

OK.  Minor bug though: linked, active tile is greyed out.

Future feature request: make the autonomy editor and track diagram editor be on two tabs in one window.  Easy to flip between them if state is saved in one.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - "MT-019: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
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


**Claude, 2026-08-23 - tested, and the model does throw both.**

"Does not work - only first is set to red. Selection process is ok."

I read the whole chain before writing anything, and every link handles several signals: the store keeps
a list; `protectingSignalNames` maps all of them; the builder writes one as a bare string and several as
an array; `fromJSON` reads both shapes; and the aspect is memoised **per accessory** rather than per
Point - with a comment recording that keying it per Point was itself a bug once, because one copy of a
square wrote a memo while standing empty and the signal stayed green with a train at the platform.

What no test covered was the end of that chain actually being REACHED for more than one, which is
exactly what your report describes. So that is the test:
`testBothProtectingSignalsAreThrown.testAPlatformGuardedAtBothEndsThrowsBothSignals` builds a platform
guarded at each end, stands a locomotive on it, and requires **both** accessories red - then takes the
train off and requires both green, because a rule that only closes is half a rule.

**It passes.** And it is not vacuous: making `refreshAllProtectingSignals` collect only the first signal
of each platform fails it on the exact sentence you wrote.

So the loss is not in the model. Three things it could be, in the order I would check them:

1. **The build.** This was run against `4ba329ad`; the multi-signal work is what the comments in
   `Layout` describe as "since this change, a platform may have more than one signal".
2. **The two signals share an address.** They are then one accessory wearing two squares, and one
   aspect is all it can show - correct behaviour that looks exactly like your report.
3. **Below the model** - the second accessory not resolving by name, or not reaching the track.

If it recurs, the detail that separates them is what the **second** signal's address is and whether the
log shows it being commanded. The test proves the command is issued; what it cannot prove is that the
ironwork moved.
---
<a id="mt-024"></a>

### MT-024 - 2026-08-21 - Two signals on the railway

**Disposition:** fixed validated
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


**Claude, 2026-08-23.** Closed on your word - "MT-024: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
---
<a id="mt-025"></a>

### MT-025 - 2026-08-21 - A layout saved by the previous version

**Disposition:** fixed validated
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


**Claude, 2026-08-23 - already covered, and worth recording rather than re-testing.**

"Check this programmatically. I previously found an issue where locomotives do not transfer."

The compatibility half of this entry is
`testAutoLayout.testBothShapesOfProtectingSignalAreRead`, and it is exactly the case the instruction
describes: a station with ONE signal written as a bare string still arrives paired, a station with two
arrives with both, and each is written back out in the shape it came in - so a file this version has
merely opened is not silently upgraded into something the previous version cannot read.

Its own comment says why that matters: read the bare string wrongly and "a railway upgraded to this
version comes back with every platform unprotected and nothing saying so."

The "file must be unchanged until you edit something" half is covered by
`testDataSafetyRoundTrips.testTheExportedBundleComesBackWholeAndChangesNothing`, which compares the
source files byte for byte.

**Your note about locomotives not transferring is a different thing from the signal case this entry is
about**, and I have not found it. If it is still happening, it is worth its own item with the two builds
named - "opened by the previous version" is a wide net, and a locomotive that fails to transfer is
either a placement not read or a Point renamed underneath it.
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---
<a id="mt-026"></a>

### MT-026 - 2026-08-21 - Shift Up and Shift Left at the edges

**Disposition:** fixed validated
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


**Claude, 2026-08-23.** Closed on your word - "MT-026: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
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

**Disposition:** fixed validated
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


**Claude, 2026-08-23 - written, as you asked.** "Seems to work in the UI, but this should have a
programmatic test."

`testCommandTableMarks.testEachMarkActsOnExactlyOneRow`. Three rows, each identifiable by its accessory
address, then: move one down and check the other two are where they were; move it back and check it
stays where it was put rather than springing back; duplicate and check there is exactly ONE more row and
that the copy sits directly under its original; delete and check exactly one went, and the right one.

**"Exactly one" is the whole entry**, and it is not cosmetic: a route is a sequence of commands sent to
real hardware, so a doubled row is a second throw of the same accessory.

The marks are values the table PAINTS rather than buttons in a cell, so there is nothing to click. The
test goes through the same private methods the cell editor calls - via a `...ForTest` hook, the
convention this frame already uses for `commandRowForTest` and `setCommandKindForTest` - which keeps it
from becoming a second implementation of move and delete that could agree with itself while the real one
is wrong.

Mutation-checked: making duplicate insert twice fails it on the sentence about copies.
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---
<a id="mt-030"></a>

### MT-030 - 2026-08-21 - A route holding a signal command

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
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


**Claude, 2026-08-23 - written, as you asked.** "Add a test for this. Many simultaneous auto routes,
trigger them synthetically, short duration."

`testTriggerWaitsSayNothing`, in two halves.

**The behaviour.** The advisory quota is shrunk to a fraction of a second, five waits are put on sensors
that never fire, and they are left sitting for many times that quota. Nothing may be logged about any of
them. Ten minutes of watching is not a test; this is the same question asked in a second.

**The rule that keeps it.** Only the dispatch loop in `Layout` may call the advising, three-argument
`waitForOccupiedFeedback`. Everything else - route monitors included - comes in by the silent
two-argument door, and that door is SHARED: `MarklinRoute` builds a locomotive called "Dummy Loc"
purely to borrow these utilities, so an advisory added there is said once per route, for ever, about a
train that does not exist. The test reads the source and requires `Layout.java` to be the only file that
asks for one.

**Worth recording: the first version of this test passed while the bug was present.** I made the shared
door advise, and it stayed green - because I had looked for the word "dummy" in the log, and the
advisory names whichever locomotive is actually waiting. The test could only ever have caught a fault
in a locomotive genuinely called "Dummy Loc", which is the one string a test cannot produce. It looks
for the advisory's own words now, and for the sensors being waited on, and the same mutation fails it.

That is the second time this session a mutation check earned its keep by failing to fail.
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
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


**Claude, 2026-08-23 - already covered.**

`testLayoutFolderRobustness.testAPageNamedWithASlashSurvivesASave` is exactly this instruction, and its
own comment says why the case exists: "Up/Down" is an ordinary name for a page on a two-level railway
and an impossible one for a file.

Recorded rather than re-tested. It runs in the battery, which is green.
**Adam, 2026-08-23 (triage).** Could not run this.

Unsure if you added a test case or not.  make sure you have a test case.  the UI does not allow slashes, rightfully.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** "Unsure if you added a test case or not." I had:
`testLayoutFolderRobustness.testAPageNamedWithASlashSurvivesASave`, and its own comment says why the
case exists - "Up/Down" is an ordinary name for a page on a two-level railway and an impossible one for
a file.

Your other half - "the UI does not allow slashes, rightfully" - is the belt to that braces: the test
covers the case where one arrives anyway, from a file written elsewhere or by an older build. Closed.

**Claude, 2026-08-24.** Adam asked to confirm a test exists before closing this. It does: a page named with a slash - `testLayoutFolderRobustness` and `testInvalidInput`. The UI also refuses one, which Adam confirmed: "the UI does not allow slashes, rightfully." Closed.

---

<a id="mt-040"></a>

### MT-040 - 2026-08-21 - A page the folder does not hold

**Disposition:** fixed validated
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


**Claude, 2026-08-23 - already covered.**

`testLayoutFolderRobustness.testAMissingPageDoesNotStopTheOthers` is this instruction: every other page
loads, the missing one is named, and the folder is still the one being pointed at afterwards.

Recorded rather than re-tested. It runs in the battery, which is green.
**Adam, 2026-08-23 (triage).** Could not run this.

make sure there is a test case for this.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** "Make sure there is a test case for this." There is:
`testLayoutFolderRobustness.testAMissingPageDoesNotStopTheOthers` - every other page loads, the missing
one is named, and the folder is still the one being pointed at afterwards. Closed.

**Claude, 2026-08-24.** Adam asked to confirm a test exists before closing this. It does: a page the index promises and the folder does not hold - `testLayoutFolderRobustness.java:87`, "a missing page took the whole layout down". Closed.

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

**Disposition:** fixed validated
**From:** LT-A8, FR-A1  
**Written:** 2026-08-22

**What to do.** The same for a whole ROW, which is the same rule with the axes swapped.

#### Comments

*(none yet)*

**Adam, 2026-08-22 (triage).** Works, with notes.

Test is unclear.  If this is related to cutting a row and seeing if stations are there, it passes.

*Run against commit fc672631, build\classes, compiled 22 Aug 22:45 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23 - clarified, as you asked.** "Test is unclear. If this is related to cutting a row
and seeing if stations are there, it passes."

It is, and the instruction above is too terse to say so - it leans entirely on MT-044 one entry up.
Spelled out, and read this in place of the sentence above (the instruction itself is append-only):

**Cut and paste a whole ROW.** Do it twice: once with a row containing a paired LINK, and once with a
row containing NAMED STATIONS.

1. The pairing must survive, **checked from both pages** - go to the link's other end and confirm it
   still points back at the moved square. A pairing is two statements, and only one of them moves.
2. The stations must arrive with their **names, lengths and facings** intact.
3. **The row you pasted ONTO must not still carry what it had before.** That is the half people forget:
   the setup of the squares that were overwritten has to go, or the new row inherits a name, a length
   or an arrival restriction from track that is no longer there.

So your reading was right, and "it passes" is a pass on point 2. Points 1 and 3 are the ones worth a
second look.

**And it has a programmatic half already:** `testLayoutEditorBulkEdits.testAMovedRowTakesItsSetupWithIt`
covers the setup travelling with a moved row, and `testDeleteAndInsertKeepTheSetup` covers what the
overwritten squares lose. Neither can see the editor's menus, which is why this stays a hands-on test.
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
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

**Claude, 2026-08-23.** Already in, and before your run - the pairing list refuses a tunnel on another
page, and its comment names this ticket.

Like pairs with like, and then a tunnel pairs only within its own page. The reasoning is in the code:
the two portal kinds are the same thing to autonomy, "and that is exactly why they need different rules
about WHAT they may point at. A link is how a train leaves one page and arrives on another - crossing
pages is its whole purpose. A tunnel is a piece of track that goes behind the scenery and comes out
further along the SAME diagram."

Worth one look on a current build. If a cross-page tunnel is still offered there, the useful detail is
whether the two squares are really both TUNNEL - a link and a tunnel are refused by the like-with-like
test one line above, and telling which test let it through says where to look.
**Adam, 2026-08-23 (triage).** Works, with notes.

see other comment, it does not use the same confirmation dialog as the rest of the editor.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Your note - "it does not use the same confirmation dialog as the
rest of the editor" - became OB-046 and is fixed: going to a link's other end now asks the same
save/discard/cancel question the sidebar asks.

It was worse than a wording difference. That path asked its own YES/NO, and its "yes" neither saved nor
discarded - it simply left, and because the setup is shared the edits survived into the window that
opened next. Recorded at MT-136. Closed here.
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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

Filed from this test: OB-050 (bug - right click menu when autonomy is invalid).  They are in `issues.md` until they are picked up.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
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

**Claude, 2026-08-23.** Both halves of your note are already in: the strip shows **20** pages before it
scrolls, and it runs the height of the window down to the mode selector rather than stopping at a fixed
eight rows. Eight was right when the pages were toggle buttons 26px tall with their own borders; they
are 24px list rows now, and eight was leaving most of the column empty while scrolling a railway of
twelve pages for no reason.

Ready for your tick. (A leftover one-line javadoc above the constant, from when it was 8, is gone.)
**Adam, 2026-08-23 (triage).** Works.

Filed from this test: OB-049 (bug - page rename effectively deletes autonomy config.).  They are in `issues.md` until they are picked up.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---
<a id="mt-057"></a>

### MT-057 - 2026-08-22 - A train marker and its name

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works, with notes.

see prior note about incorrect * placement on curved sensors

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** "See prior note about incorrect * placement on curved sensors" - that is
[MT-124](#mt-124), and it is fixed there: the star follows the badge to the corner instead of asking the
track independently.

Nothing else on this entry is outstanding, so it closes with that pointer rather than waiting for a
second look at the same pixels.
---
<a id="mt-058"></a>

### MT-058 - 2026-08-22 - Show autonomy hides the names

**Disposition:** fixed validated
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

**Claude, 2026-08-23.** Done - the checkbox reads **Autonomy Controls** in all eight language bundles.
Ready for your tick.
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---
<a id="mt-059"></a>

### MT-059 - 2026-08-22 - Why is it not moving, on an addressed layout

**Disposition:** fixed validated
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


**Claude, 2026-08-23.** Closed on your word - "MT-059: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
---

<a id="mt-064"></a>

### MT-064 - 2026-08-22 - Highlight on Diagram, and Test Condition

**Disposition:** fixed validated
**From:** FR-009  
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


**Claude, 2026-08-23.** Closed on your word - "MT-064: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Could not run this.

make a test case for this.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** "Make a test case for this." Written on 2026-08-22 and recorded
above: `testDeleteAndInsertKeepTheSetup`. Deleting a square takes its own setup and nothing else;
inserting a line carries the setup of everything it pushes along; and a move reports both halves while a
copy reports only its landings. Verified by mutation - making `forgetTiles` tell the setup nothing fails
it. Closed.

**Claude, 2026-08-24.** Adam asked to confirm a test exists before closing this. It does: delete, shift and insert - `testDeleteAndInsertKeepTheSetup` and `testDiagramShiftKeepsSetup`, with `testDiscardedEditsDoNotDeleteSetup` for the Cancel path beside them. Closed.

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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-096"></a>

### MT-096 - 2026-08-22 - The editor opens at the size of its diagram

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-097"></a>

### MT-097 - 2026-08-22 - Sidebar: pages are a list, modes are radio buttons

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-098"></a>

### MT-098 - 2026-08-22 - One-Way Run is a button, and asks which way

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-099"></a>

### MT-099 - 2026-08-22 - A train mark shows on a station with nothing else on it

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
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

**Claude, 2026-08-23.** All three points are in, and were in before your run - this is one of several
verdicts recorded against an older build.

- The order is **... divider, Trains May Arrive..., Trains May Depart..., divider, protected by signal,
  Advanced Parameters...**, which is your spec.
- **Home for a locomotive** is the last item of the locomotive group. It used to sit beside the signal,
  on the reasoning that both are answers about a station; your order is the better reading, because a
  home is a fact about a LOCOMOTIVE - which one belongs here - so it belongs with the items about the
  train rather than with the ones about the platform.
- **Maximum Train Length** is inside Advanced Parameters.

Worth one look on a current build. If the order still comes out wrong there, the useful detail is which
two items are the wrong way round - the code adds them in one sequence, so a difference means something
is being added from somewhere else.
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-109"></a>

### MT-109 - 2026-08-22 - Track lengths: hotkey, focus, and size

**Disposition:** fixed validated
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


**Claude, 2026-08-23.** Adam: "Control+G works, but control +L does not in the autonomy editor."

Of course it did not - it was below the guard, exactly where Control+G had been before GC-B2 moved it.
Moving one key above a guard fixes the key somebody just tried and leaves its neighbours where they
were.

All three display toggles are above it now: Control+G for lengths, Control+L for text, Control+D for
addresses. Control+D was not reported; it is here because it is the third of the same three, and
finding out later that the sweep stopped at the two that were mentioned is worse than the original bug.

The guard's own sentence is the rule that decides this - "Every shortcut below places, cuts, rotates or
retextures a tile" - and none of these does. They show and hide something ABOUT the diagram without
changing it.
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-110"></a>

### MT-110 - 2026-08-22 - The autonomy column is narrower, and three labels changed

**Disposition:** fixed validated
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


**Claude, 2026-08-23.** Closed on your word - "MT-110: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
---

<a id="mt-111"></a>

### MT-111 - 2026-08-22 - Layouts menu: order, and one divider too many

**Disposition:** fixed validated
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


**Claude, 2026-08-23.** Adam: "Layout greys out all options while Autonomy hides them. Grey them out in
Autonomy instead."

Done, and the Layout menu was already the better of the two. A greyed item says "this exists, and not
now"; an empty menu says "this feature is gone" - which is a worse thing to be told by the surface you
have just opened in order to use it.

The Autonomy menu built nothing at all while an editor had the diagram, which is a real constraint
stated in the crudest way: every item there saves the setup or rebuilds the main window, and an open
editor makes both unsafe. It builds the whole menu and disables it now, with the way back to the editor
first in the list and clickable - the same shape as `guardLayoutMenu`, deliberately, so the two menus do
not read as two different rules.
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-112"></a>

### MT-112 - 2026-08-22 - Home assignments: the three rules that were unreachable

**Disposition:** fixed validated
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


**Claude, 2026-08-23 - written, as you asked.** "Make a test case for this, you should be able to
validate it."

`testHomeAssignmentRules`, and the shape of it is decided by what went wrong here. All three rules
existed, were commented, and were believed to work; what had gone was every caller that ran them. DD-A6
put it plainly: `HomeLocomotiveMenu` lost four of its five callers, "two safety warnings are now
unreachable and their tests still pass."

**So a test that only proves the rule proves nothing.** Two of the three are pure functions now -
`homeChoices` and `homeBrokenBy`, lifted out of the dialog code where they were tangled with a
JOptionPane - and each is tested directly. And there is a fourth test that asserts all three are still
WIRED: the dialog builds its list with `homeChoices`, the exclusion warning asks `homeBrokenBy` about
the station's actual home, and the unreachable-home warning still consults `HomeStaging.canBeHome`.

The rules themselves:

1. **A home naming a locomotive autonomy no longer runs stays in the list**, second, where the eye
   lands. A non-editable combo cannot preselect a value its model does not hold, so leaving it out made
   the existing assignment the one thing that could not be chosen - the dialog opened on "None" and OK
   cleared a home nobody asked to clear.
2. **A home the locomotive cannot reach** is `HomeStaging.canBeHome`, which has its own tests in
   `testHomeStaging`; what is checked here is that the panel still asks it.
3. **Excluding a locomotive from its own home** is reported by name. Not forbidden - somebody may mean
   it - but shown, rather than discovered when a train has nowhere to go at the end of a run.

The hands-on instruction above is still worth running: none of this can see a menu item that is never
built, and "is the warning actually shown" is a question about a window.
**Adam, 2026-08-23 (triage).** Works.

Filed from this test: FR-010 (feature request - home locomotive searching).  They are in `issues.md` until they are picked up.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-113"></a>

### MT-113 - 2026-08-22 - Tool labels, and a heading with nothing under it

**Disposition:** fixed validated
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


**Claude, 2026-08-23.** Closed on your word - "MT-113: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
---

<a id="mt-114"></a>

### MT-114 - 2026-08-22 - No findings while autonomy is unloaded

**Disposition:** fixed validated
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


**Claude, 2026-08-23.** Works, and the bug you filed from it - OB-038 - was answered: the export/import round trip was already covered by a test, which is recorded at MT-118.
---

<a id="mt-115"></a>

### MT-115 - 2026-08-22 - Both menus decline while an editor is open, and lead back to it

**Disposition:** fixed validated
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


**Claude, 2026-08-23.** Closed on your word - "MT-115: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
---

<a id="mt-116"></a>

### MT-116 - 2026-08-22 - Renaming a station keeps its label

**Disposition:** fixed validated
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


**Claude, 2026-08-23.** Adam: "Weird - the label moves around to adjacent cells on rename."

Found, and it is a side effect rather than a fault in the drawing.

`placeCaption` MOVES a station's caption when it already has one rather than refusing - deliberately,
and its comment argues the case: "Asking to show a name is asking for it to be here, and answering 'it
is already somewhere' left the user to find and delete the old one first." That is right when somebody
has asked for the name to be shown on a particular square.

It is wrong as a side effect of renaming, which is what it had become: the rename path placed a label
every time, so a station that already had one got it MOVED - and the search picks whichever
neighbouring square is free this time round. Hence a label that wanders.

**Nothing has to be re-placed for the text to change.** A caption points at the station's SQUARE and
looks its name up, so a rename is already visible wherever the label happens to sit. The rename only
places a label when the station has none, which is the case the original reasoning was about: "the
moment a station gets a name is the moment it has one worth writing on the diagram."

**There were two rename paths, and the fix went on one of them first.** The other is the "Name
Everything..." walk, which visits only unnamed squares - and `placeCaption` refuses to caption a
nameless station, so nothing there can already have a label. It asks the same question anyway: two
rename paths asking different questions is how one of them ends up wrong, and asking costs nothing.

`testEditorSurfaceRules.testARenameOnlyLabelsAStationThatHasNoLabel` walks every `setPointName` in the
file and requires the label it places to be guarded, so a third rename path cannot arrive without one.
Mutation-checked.
**Adam, 2026-08-23 (triage).** Works.

Filed from this test: OB-045 (bug - disable autonomy editing while running).  They are in `issues.md` until they are picked up.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-117"></a>

### MT-117 - 2026-08-22 - The train star is visible again, and five smaller fixes

**Disposition:** superseded
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


**Claude, 2026-08-23 - superseded, and it should never have been one entry.**

Adam: "031, 030, 036 - file under new MTs. One test per MT ticket." He is right, and the bundle cost
exactly what bundling costs: five fixes under one disposition, so a verdict on any of them was a verdict
on all of them. He could not mark the three that work as working without also blessing the two that do
not, so the whole thing sat at "does not work" and told nobody which two.

Taken over in full, one OB each:

| | |
|---|---|
| [MT-120](#mt-120) | OB-030, the Autonomy menu's tooltips |
| [MT-121](#mt-121) | OB-031, pairing a link switches both ends on |
| [MT-122](#mt-122) | OB-035, adding to autonomy from the track diagram - **not fixed** |
| [MT-123](#mt-123) | OB-036, how a warning names another page |
| [MT-124](#mt-124) | OB-037, the star, and the mark that says where the train is |

His verdicts travel with them rather than being restated here, and the receipts in `issues.md` now point
at the successor rather than at this.

The rule this broke is in the SOP already - "a bug becomes ... an entry in tests.md with a new MT-###
tag", singular, one per bug. Nothing needed changing except doing it.
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

**Disposition:** fixed validated
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
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-120"></a>

### MT-120 - 2026-08-23 - Tooltips on the Autonomy menu wrap

**Disposition:** fixed validated
**From:** OB-030  
**Written:** 2026-08-23

**What to do.** Hover every item on the Autonomy menu. A long tooltip must wrap onto several lines rather than run
off the side of the screen.

#### Comments

**Claude, 2026-08-23.** Split out of MT-117, which bundled five separate fixes into one ticket. Adam:
"031, 030, 036 - file under new MTs. One test per MT ticket."

`wrapped()` already existed and wraps at 320px; the Autonomy menu did not use it once. All eight
tooltips go through it now.

**Adam, 2026-08-23 (triage).** Works.

*Run against commit fb109619, build\classes, compiled 23 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - "MT-120: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
---

<a id="mt-121"></a>

### MT-121 - 2026-08-23 - Pairing a link switches both ends on

**Disposition:** fixed validated
**From:** OB-031  
**Written:** 2026-08-23

**What to do.** Switch a link off. Then pair another link to it. Both ends must come out switched ON.

#### Comments

**Claude, 2026-08-23.** Split out of MT-117, which bundled five separate fixes into one ticket. Adam:
"031, 030, 036 - file under new MTs. One test per MT ticket."

Somebody pairing two links is saying they are joined; a disabled far end is a setting made earlier
about a square that was not joined to anything, and the newer statement is the one they mean.

**Adam, 2026-08-23 (triage).** Works.

Filed from this test: OB-041 (bug - linked links turned off).  They are in `issues.md` until they are picked up.

*Run against commit fb109619, build\classes, compiled 23 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Works, and the bug you filed from it - OB-041, a paired link switched off leaving its partner on - is fixed and recorded at MT-131.
---

<a id="mt-122"></a>

### MT-122 - 2026-08-23 - Adding a locomotive to autonomy from the track diagram

**Disposition:** fixed validated
**From:** OB-035  
**Written:** 2026-08-23

**What to do.** Right-click a station on the TRACK DIAGRAM - not in the editor - and add a locomotive to autonomy.
The caption must update at once, without closing anything.

Then check the two halves separately, because Adam reports they behave differently: **placing** a
locomotive works, **adding one to autonomy** does not take effect.

#### Comments

**Claude, 2026-08-23.** Split out of MT-117, which bundled five separate fixes into one ticket. Adam:
"031, 030, 036 - file under new MTs. One test per MT ticket."

**Adam, triaging MT-117:** "035 - ADDING TO AUTONOMY here still doesn't take effect. placing is OK."

So the fix that went in for OB-035 covered placing and not adding, and this is not fixed. Split out of
MT-117 with the disposition it has actually earned rather than the one the bundle was carrying.

The shape to look at first is the one the original fix was about: the deep menu writes the SETUP, while
the caption being watched belongs to the main diagram, which draws from the RUNNING layout. Placing now
rebuilds the running layout; adding to autonomy evidently does not go through the same seam.

**Adam, 2026-08-23 (triage).** Does not work.

still not fully placed.  label doesn't update, loc isn't there on restart. loc not in list of autonomy locs.

*Run against commit fb109619, build\classes, compiled 23 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23 - traced, and worth a retest before I change anything.**

You ran this against `fb109619`, compiled 00:43. The change that most likely accounts for the first two
symptoms landed after that, in `8a5e1951`: `placementChanged` used to refresh the editor's grid where
there was one and rebuild the running layout only where there was not - either/or - so a change made
from the track diagram while Autonomy Setup was open went to the editor and left the diagram showing the
old answer. It does both now. That is exactly "label doesn't update".

**On "loc isn't there on restart" I followed the save path rather than guessing**, and it is present: a
placement runs `placementChanged` -> `refresh()` -> the panel's `onChanged`, which for the diagram's
menus is the callback in `TrainControlUI` that calls `session.save()` and refreshes the static layer.
Nothing about it is conditional on an editor being open.

So either that path was not reached on your build for a reason I have not found, or the missing
placement was a consequence of the refresh bug above - a setup that never rebuilt the running layout
also never listed the locomotive, which is your third symptom, "loc not in list of autonomy locs".

Worth one retest on a current build before I go further. If it persists, the thing that settles which
half is broken is whether the locomotive is in `configuration-*.json` after the placement.
**Adam, 2026-08-23 (triage).** Works.

Filed from this test: FR-011 (feature request - add to autonomy filtering).  They are in `issues.md` until they are picked up.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-123"></a>

### MT-123 - 2026-08-23 - How a warning names another page

**Disposition:** fixed validated
**From:** OB-036  
**Written:** 2026-08-23

**What to do.** A finding about a square on another page must read `(Page 2) ...` rather than `On 2 - ...`.

#### Comments

**Claude, 2026-08-23.** Split out of MT-117, which bundled five separate fixes into one ticket. Adam:
"031, 030, 036 - file under new MTs. One test per MT ticket."

Wording only.

**Adam, 2026-08-23 (triage).** Works.

*Run against commit fb109619, build\classes, compiled 23 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - "MT-123: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
---

<a id="mt-124"></a>

### MT-124 - 2026-08-23 - The train star, and the mark that says where the train is

**Disposition:** fixed unvalidated
**From:** OB-037  
**Written:** 2026-08-23

**What to do.** With a locomotive on a station, the white star must be clearly white - a mark ON the badge, not a
dark smudge. Check a 30px tile as well as a 60px one; the small size is where this went wrong.

Then do it again at a station on a CURVE, and look at where the marks sit relative to the badge. The
star, the badge and the dot that appears during a run must all be on the same spot - on the rail, which
on a curve is not the middle of the square.

#### Comments

**Claude, 2026-08-23.** Split out of MT-117, which bundled five separate fixes into one ticket. Adam:
"031, 030, 036 - file under new MTs. One test per MT ticket."

**Adam, triaging MT-117:** "037 - Stars work, but are offcenter on curve stations."

The star half is his word that OB-037 is fixed. The off-centre half was a second defect hiding behind
it, and it is the same one as OB-026: the star has been centred on `trackCentre` since MT-057, but the
RUNNING overlay draws its own mark - the dot saying which square of a claimed path holds the train - and
that was still centred on the tile. On a straight they agree. On a curve the badge and star sit on the
corner the rail cuts and the dot sits in the middle of the square, so the marks disagree with each other
about where the train is.

Fixed 2026-08-23, in the same method as OB-026 and now asking the same question through one helper:
`testTheTrainDotSitsOnTheRail` requires ink on the rail and none at the tile centre, and failed before
the change.

Unvalidated rather than validated because the second half has never been seen working - his "stars
work" was about visibility, which is not what this now asks you to look at.

**Adam, 2026-08-23 (triage).** Does not work.

Still off on a curve, for example s88 1015 and 1016

OK on regular tiles.

*Run against commit fb109619, build\classes, compiled 23 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Noted, and not yet answered.

The train DOT that the running overlay draws was still centred on the tile while the star and the badge
were on `trackCentre`, and that was fixed a few commits ago - after MT-124 was filed. So the first thing
to establish is whether you were looking at a build that has it.

If you were, then something else on a curved station is placed by the square's geometry rather than by
its track, and the picture harness can now show which: it renders a real page through the same grid the
window uses, so a locomotive on `TopMainR2Inter` - a curved station in the sample layout - can be looked
at directly rather than reasoned about.

That needs the CS2 port, which TrainControl has. Queued for the next time it is free rather than
guessed at, because guessing at this exact question is what made OB-037 take three rounds.
**Adam, 2026-08-23 (triage).** Does not work.

On a curve, the badge is perfectly over the sensor, but we decided to place those curved station dots offset on the other side when in the autonomy editor.  Move the * so it aligns with the offset placement.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23 - fixed.** "On a curve, the badge is perfectly over the sensor, but we decided to
place those curved station dots offset on the other side when in the autonomy editor. Move the * so it
aligns with the offset placement."

Done, and by making the star stop asking a separate question. It was centred on `trackCentre`, the
midpoint of the square's own two track sides - which is where the badge is too, for everything except
the case you are describing: in the EDITOR a curved station's badge is deliberately moved to the bottom
left corner, so it stops fighting the two direction arrows that sit at the middles of the same two sides
the chord joins.

The badge now records where it actually landed and the star is drawn there. Recorded rather than
recomputed on purpose: that placement is three rules deep - the track centre, then the corner, then the
clamp that keeps it inside the square - and a second copy of the arithmetic would be a second chance to
disagree, which is exactly how the two came apart in the first place.

A train on a plain square still gets its mark on the rails, because with no badge drawn the star falls
back to the track centre.

**Unvalidated because this is a question about pixels**, and two of the three drawing defects this month
were wrong on the first attempt. Worth a look at a curved station with a train on it, in the editor and
in the viewer.
---

<a id="mt-125"></a>

### MT-125 - 2026-08-23 - The arrow on a caption follows the orientation you chose

**Disposition:** fixed validated
**From:** OB-039  
**Written:** 2026-08-23

**What to do.** Right-click a station with a locomotive on it, on the TRACK DIAGRAM, and change which way the
locomotive is facing. The arrow on its caption must change with it, at once.

Then do the same from inside Autonomy Setup and check the caption again - the two menus used to be
separate pieces of code and are now one, so they should behave identically.

#### Comments

**Claude, 2026-08-23.** Adam: "when changing the orientation of a loc from the track diagram, the
direction on the label is not updated."

The facing was written to the setup and nothing repainted, so the caption went on stating the opposite
of what had just been chosen. Same seam as OB-034 and OB-035: the setup is written, and the caption is
drawn from the RUNNING layout, so `placementChanged()` is what carries one to the other.

**The reason this took a test.** The facing menu existed TWICE in `AutonomyEditorPanel` - once for the
deep menu inside Autonomy Setup, once handed to the track diagram - about forty lines apart and already
drifting. Adding the redraw to the copy in front of me would have fixed the surface I was looking at and
left the other one carrying the bug. So the deep menu now calls `buildFacingMenu` like everything else,
and `testEditorSurfaceRules` requires the setup to be told about a facing from exactly one place, and
that place to redraw. Mutation-checked by deleting the redraw.

**Adam, 2026-08-23 (triage).** Does not work.

Does not refresh in the viewer. Works in the autonomy editor.

Critical: also, feedback 1016/1015 offer south and west as facing directions, instead of north and east.

*Run against commit fb109619, build\classes, compiled 23 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Two separate faults, both fixed.

**"Does not refresh in the viewer. Works in the autonomy editor."** `placementChanged` refreshed the
editor's grid where there was one and rebuilt the running layout only where there was not - either/or,
on the reasoning that whoever made the change was looking at the surface it came from. They are not:
both are on screen at once, so a change made from the track diagram while Autonomy Setup is open went
to the editor and left the diagram under the pointer stating the old answer. It does both now. Asking
for the rebuild unconditionally is safe - it declines while autonomy is busy.

**"Critical: feedback 1016/1015 offer south and west as facing directions, instead of north and east."**
This one is worth reading twice, because the old rule was true by accident everywhere anybody looked.

The choices were "the side the train came in by, reversed" - enter by the west, point east. On a curve
joining north to east, a train entering by the north side leaves by the **east** side. It is pointing
east. South is a direction that square has no track in at all.

So the rule is not "the opposite compass point", it is "the other end of the piece of track I am
standing on" - which *happens to be* the opposite compass point on a straight. Every square anybody
checked was a straight.

Where a square offers a choice - a switch, a double curve - every road out of the arriving side is
offered now, because each is somewhere the train could genuinely be pointing and nothing there knows
which road it took.

`testFacingFollowsTheTrack` states the rule rather than a table of answers: every facing offered for a
square must be a side that square's own track actually uses. It refuses to pass unless it has checked a
curved square, so it cannot go quietly green on a layout of straights.

**Not yet run** - it needs the CS2 port and TrainControl has it. Compile-checked only, and it will be
run before this is asked of you again.
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-126"></a>

### MT-126 - 2026-08-23 - The diagram gets out of the way while you pick a signal

**Disposition:** fixed validated
**From:** OB-040  
**Written:** 2026-08-23

**What to do.** Right-click a station, ask which signals protect it, and leave that window open. While it is up,
the diagram behind must be quiet: no direction arrows, no tile lengths, and everything that is not a
pairable signal greyed out.

**Then close it every way there is** - Done, Escape, the window's close box, and "click it on the
diagram" - and check each time that the arrows, lengths and colour all come back.

#### Comments

**Claude, 2026-08-23.** Adam: "while the window is open, de-clutter the diagram as much as possible
so users can clearly see the signals. turn off arrows, labels, etc."

The greying already existed, but only for the click half of the gesture - so the one moment somebody is
being asked to FIND a particular square left the two things most likely to cover it, arrows and lengths,
switched on.

One predicate now, `isFocusedOnSignals()`, asked once in `annotationFor` and used by all three. Three
separate conditions that have to agree is the shape that produced OB-037, and it is not worth repeating.

**The risk here is not the feature, it is the flag.** An editor stuck grey and arrowless looks broken
and nothing on screen says why, so it is cleared in the `finally` around the window rather than in any
handler - the window has four ways out and only the finally sees all of them. The test asserts exactly
that, because moving it into a handler is the natural-looking edit that would break it.

**What I did not do:** the station captions are still drawn. They come from the grid rather than from
the annotation, so switching them off is a different piece of work; say the word if they are in the way.

**Adam, 2026-08-23 (triage).** Works.

Looks great!

*Run against commit fb109619, build\classes, compiled 23 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** "Looks great!" - closed.
---

<a id="mt-127"></a>

### MT-127 - 2026-08-23 - No grey grid in the autonomy editor

**Disposition:** fixed validated
**From:** OB-028  
**Written:** 2026-08-23

**What to do.** Open Autonomy Setup. The diagram must look like the viewer's - no grey line around every tile.

Then switch to the layout editor on the same page: the grid must be back.

**And hover a few tiles in autonomy mode.** The artwork must not shift or resize as the pointer crosses
them.

#### Comments

**Claude, 2026-08-23.** Adam: "in the autonomy editor, the gray grid is not needed. show the track
diagram as it appears in the viewer, without the tile borders. make sure the borders return in the
editor."

An EMPTY border of the same thickness rather than no border at all, and that is the whole of the care
needed. The grey lines go, which is what was asked; the tile keeps the insets it had, so hovering -
which swaps the resting border for a coloured line - does not move the artwork underneath. A null border
would have shifted every icon by a pixel the moment the pointer crossed it, which is the complaint
FR-006 makes about the layout editor's own grid.

The palette keeps its visible border in both modes: those tiles are a menu of things to place rather
than a picture of a railway, and the border is what separates one from the next.

Small enough to state as a function, so it is tested as one - `LayoutEditor.restingBorder(palette,
autonomy)` - including that the invisible border takes the same room as the grid it replaces.

**FR-006 is still open and still yours to call.** It asks for the layout editor's grid to become a
toggle in Visible Elements, persisted, which is a different piece of work from this.

**Adam, 2026-08-23 (triage).** Does not work.

The grid is correctly gone, but now there is a gap between tiles (essentially a white grid).

the BUG where the VIEWER gets a grid is still there!

*Run against commit fb109619, build\classes, compiled 23 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Both halves, and the second one was the older and worse bug.

**The white grid.** I used an EMPTY border of the same thickness, to keep the insets so a hover could
not shift the artwork underneath. An inset with nothing drawn in it shows the panel behind it, so the
grey grid became a white one - which is not what "as it appears in the viewer" means. It is no border at
all now. The shift I was guarding against cannot happen: `receiveMoveEvent` returns immediately in
autonomy mode - "hover previews what a diagram edit would place; in autonomy mode nothing is being
placed" - so nothing ever swaps that border for another one. The care was real and aimed at the wrong
mode; FR-006 still wants it for the layout editor.

**"The BUG where the VIEWER gets a grid is still there."** It was, and it was never about borders.
`LayoutGrid` asked `layout.getEdit()` in seven places, and that flag is the one the two editors SHARE
for their mutual exclusion - it says an editor is open, not that this grid is in one. So while either
editor was up, the viewer built its squares in edit mode: the editor's grey grid drawn round them, its
captions greyed, its hand cursors and tooltips dropped, and mouse listeners attached that cast their
parent to `LayoutEditor` - which the viewer is not.

One line of that constructor already asked the question properly, as a conjunction with the host, and
was right. The other six asked the short version. There is one answer now, worked out once, and
`testEditorSurfaceRules.testTheViewerIsNotToldItIsAnEditor` fails if the short version comes back.

This is the remaining half of the graph-and-clickability finding GC-A1 raised, closed here because you
found the same flag from the other end.
**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-128"></a>

### MT-128 - 2026-08-23 - Two guards that now reach every site

**Disposition:** fixed validated
**From:** OB-023  
**Written:** 2026-08-23

**What to do.** **The right-click menu.** Right-click in four places: a tile on the track diagram, a station's
caption, the main window's empty diagram area, and the pop-out diagram window. Each must open the
autonomy menu - and where there is genuinely nothing to offer (plain track, autonomy running, no setup
to reach) **no menu should appear at all**, rather than a one-item-high grey box.

**The grid.** Switch pages, resize the window, and open and close the editor a few times. No spinner
should ever appear in the middle of a drawn diagram, and no row of tiles should come out short.

#### Comments

**Claude, 2026-08-23.** Both from the duplication review, and both the same shape: a rule that had
to be remembered at four call sites and was remembered at three.

**DD-B5.** The four surfaces each wrote out hop-to-the-event-thread, build, show. Three also checked
whether the menu had come out empty; the fourth - `LayoutGrid`, added later by `a4651d20` - did not.
There is one entry point now, `LayoutRightclickAutonomyMenu.showFor`, and **the constructor is private**,
so the menu cannot be shown any other way. That the whole application still compiles is the proof there
were no other callers.

**DD-B3.** Building a grid over a panel now retires whatever grid was there, from a weak per-panel
table inside `LayoutGrid` itself. `174178c5` had to add the third `discard()` call and wrote the finding
into its own comment - "both other places that build a grid over an existing panel call this; this one
did not." Nothing to remember now: building IS retiring.

Covered by `testDiagramExport.testANewGridRetiresTheOneItReplaces`.

**Adam, 2026-08-23 (triage).** Works.

*Run against commit fb109619, build\classes, compiled 23 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - "MT-128: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
---

<a id="mt-129"></a>

### MT-129 - 2026-08-23 - Two names one letter apart, and a table that was kept in step by hand

**Disposition:** fixed validated
**From:** OB-024  
**Written:** 2026-08-23

**What to do.** Nothing to do by hand - this is covered by tests, and the entry exists so the change has a
recorded home. Worth a glance at the autonomy editor's arrows and at a three-way switch after the
next build, since both go through the code that moved.

#### Comments

**Claude, 2026-08-23.**

**DD-C9 - `sideTowards` and `sideToward`.** They were not duplicates, which is the interesting part:
one is grid arithmetic on coordinates, the other asks the graph what it counts as a neighbour. Two
different questions with names one letter apart is a defect waiting for a tired reader, so they are
`gridSideTowards` and `sideTowardNeighbour` now, and the instance method's javadoc - which claimed it
answered for paired portals, the opposite of what its own code does - says what it does.

**DD-C10 - the port table in three places.** `TilePorts.numOrientations` was a verbatim copy of
`LayoutDiagramComponent.getNumOrientations`; the rule is now static on the latter and the former asks
it. The two agreed only for as long as nobody added a tile type with an axis of symmetry, which is a
promise about the future rather than a property of the code.

The third copy was `docs/plans/portmap-verification.py`, and `TilePorts`'s javadoc instructed the reader
to keep it in step BY HAND. It had already gone stale - still marking the LINK side "UNCONFIRMED" long
after `d4d5b7ba` confirmed it. **A verification that must be hand-synchronised with the thing it
verifies is a second opinion with the same author.**

Its table - all twenty-eight tile types, the only complete statement of the port map anywhere - is now
`testAutonomyDiagramPorts.testTheWholePortTableIsWhatTheMapSaysItIs`, transcribed by hand from the
Python and then run against `TilePorts`. **It agreed on all twenty-eight**, which is the confirmation
the script was written to give and had stopped being able to give. Adding a tile type without stating
its ports now fails the build. The script is deleted; the PNG it drew is kept, because a picture checked
against the artwork by eye is a different kind of evidence from a test.

**Adam, 2026-08-23 (triage).** Works.

*Run against commit fb109619, build\classes, compiled 23 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - "MT-129: Works." with nothing else attached, so there is
nothing left to do and nothing left to check.
---

<a id="mt-130"></a>

### MT-130 - 2026-08-23 - A setting must not outlive its track

**Disposition:** fixed validated
**From:** OB-025  
**Written:** 2026-08-23

**What to do.** **By hand:** draw a link on a square, give it a name, switch it off, then delete that square from
the diagram and draw a NEW link on the same square. It must arrive unnamed and switched on.

Then check a rename, a move, a page snapshot and a discard still keep every setting they should - the
usual round: name a station, set a length, bar an arrival, pair a signal, switch a link off, exclude a
page, and put each through a page rename and a block move.

#### Comments

**Claude, 2026-08-23.** DD-A1, and it names two live defects as well as a shape.

**The defects, both fixed.** `reconcile` handled nine of the eleven kept collections and said nothing
about `linkNames` or `disabledPortals` - with no comment claiming that was deliberate, while there is
one for every other decision in that method. So a link's name and its switched-off flag stayed in the
file after its square was deleted, and **the next link drawn on that square inherited both**, arriving
pre-named and already disabled with nothing saying why. Covered by
`testADeletedTileTakesItsLinkNameAndItsDisabledFlag`, seen failing first.

And `forgetSquares` carried a dead line: `tileDirections.remove(key)`, written because the other ten
were there and unable ever to match, since those keys are suffixed with the route. It is gone, and the
loop that does the work now handles a bare key too, so nothing depends on every direction having been
written with a suffix.

**The shape, and what I did about it.** The review's recommendation is a registry of kept collections,
each knowing how to do the bookkeeping to itself - about 830 lines restructured. I have not done that,
and the reason is worth stating plainly rather than dressing up: it is the largest blast radius in the
file, it touches the code that reads and writes your setup, and I could not run the full battery while
it was in progress because the application had the CS2 port. Restructuring a store I cannot fully test
is how a data-loss bug gets written.

What I did instead closes the same defect class for a fraction of the risk.
`testStoreCollectionsAreHandledEverywhere` reads the store's source and fails the build when a kept
collection is missing from one of its twelve bookkeeping sites, naming the site. **Every omission DD-A1
lists would have been caught by it on the commit that introduced it** - the four that `disabledPortals`
took five days and five commits to finish, `clear()` missing `stationSignals` (which threw a cancelled
signal on real hardware), and `captions` missing from `KNOWN_SHARED` (which reverted every caption edit
on the next save). A new collection has to be classified as kept or explained away, and a kept one has
to be handled everywhere or exempted in writing.

**The exemptions are the other half.** DD-A1's complaint about `reconcile` was not only that two
collections were missing but that "there is no comment claiming this is deliberate." There are now
fifteen such statements - why `excludedPages` has nothing to say to a square-level site, why
`applyTo` is the one place where "handled everywhere" is the wrong rule (it populates the tile GRAPH,
which models track; a caption is not a property of track) - and a third test fails if one of them stops
being true. Mutation-checked: taking `disabledPortals` out of `forgetSquares` fails it.

**Still open, and yours to schedule:** the registry refactor itself. It is a better end state than a
test that reads source, and it wants a quiet tree and a full battery either side.

**Adam, 2026-08-23 (triage).** Works, with notes.

Make a test case for this.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** "Make a test case for this" - written when the fix went in, and named in the
comment above: `testAutonomyDiagramStore.testADeletedTileTakesItsLinkNameAndItsDisabledFlag`.

It gives a square a link name and switches the link off, deletes the square from the diagram, and
requires both to be gone - and requires them to be REPORTED, because a diagram edit that quietly takes a
link name should be visible rather than discovered later. Seen failing first.

The build-failing guard that stops the next collection being left out of a bookkeeping site is
`testStoreCollectionsAreHandledEverywhere`, and since OB-049 it also requires every rule the store
offers to have a caller.

**Claude, 2026-08-24.** Adam asked to confirm a test exists before closing this. It does: a setting outliving its track - `testAutonomyTileMove` (four tests) and `testAutonomyDiagramStore.java:1756`, "an unnamed station outlived its tile". Closed.

---

<a id="mt-131"></a>

### MT-131 - 2026-08-23 - Switching a paired link off switches its partner off

**Disposition:** fixed validated
**From:** OB-041  
**Written:** 2026-08-23

**What to do.** Pair two links. Switch one of them off. The other must go off with it - check both the menu tick and
the greying on the diagram, on both pages.

Then switch either one back on and check the pair comes back on together.

An UNPAIRED link must still switch on and off entirely by itself.

#### Comments

**Claude, 2026-08-23.** Adam: "if a linked link is turned off, its target isn't."

A pair of links is one doorway with an end in two places, and autonomy walks through it in both
directions. A doorway shut at one end and open at the other is not half shut - it is a route that exists
going one way and not the other, which nothing on the diagram says and no train can be told.

Same reasoning as OB-031, where pairing two links switches both ends ON rather than refusing because one
of them was off: once two squares are paired, a statement about one of them is a statement about the
pair. That rule went in and its mirror image did not, which is the half-a-rule shape.

Fixed in the STORE rather than beside the menu that reported it, because that is where the partner is
known - and a rule kept beside one caller is a rule the next caller does not get. Seen failing first;
the test also pins that an unpaired link is nobody else's business.

**Adam, 2026-08-23 (triage).** Works, with notes.

This works

Filed from this test: OB-046 (bug - go to the other end quirk).  They are in `issues.md` until they are picked up.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** "This works" - closed. The bug you filed from it, OB-046, is fixed
and recorded at MT-136.
---

<a id="mt-132"></a>

### MT-132 - 2026-08-23 - A station name can go on almost any square

**Disposition:** fixed validated
**From:** OB-042 and OB-044
**Written:** 2026-08-23

**What to do.** Right-click a CURVED piece of track, and then a BUMPER, in the autonomy editor. Both must offer
**Show Station Here...**, and the label must appear where you put it.

Then right-click a SWITCH and a SIGNAL. Neither should offer it - those already do something when
clicked, and a caption on one puts text over a control.

Try a few other shapes too: a crossing, an uncoupler, a tunnel mouth, an overpass.

#### Comments

**Claude, 2026-08-23.** Two reports, one bug. Adam: "the option to place a station label is not shown
in the curved track right click menu", and "bumpers don't allow station labels to be placed via the
right click menu. check other components that also don't."

The gate was `isStraightThrough` - one route joining two OPPOSITE sides - and its comment argued the
case: "Curves, switches, crossings and dead ends all fail it, and none of them has room beside the track
for a name anyway." That last clause was a guess about layouts, and it was wrong on two squares in one
evening: a bumper at the end of a siding and the curve at the top of a loop are often the only squares
near a station with nothing else to say.

His rule is in the code now, in his own words - "the only fair place to disallow them are clickable
elements like switches and signals" - and it is a rule about what a square DOES rather than about the
shape of its track, which is why it needs no list of types kept in step with the port map.

`isStraightThrough` is gone; nothing else used it. And the two menus that offer the caption items build
them from one method now, so they cannot drift into offering different things - the same reasoning that
has `buildTileMenu` handed to the main window rather than copied there.

**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-133"></a>

### MT-133 - 2026-08-23 - Segment length: blank means none, and only digits go in

**Disposition:** fixed validated
**From:** OB-043
**Written:** 2026-08-23

**What to do.** Open **Length...** on a square. Try typing letters and punctuation - nothing should appear.

Clear the field entirely and press OK. The length must become 0, with no error.

Then set a real number and check it sticks, and that Cancel still changes nothing.

#### Comments

**Claude, 2026-08-23.** Adam: "if the segment length is cleared and submitted, treat it as 0. force
the field to only allow numeric entry."

Both done, and the second is why the first is simple. The field refuses non-digits as they are typed, so
there is no longer a moment where it holds something the dialog will have to reject - which leaves the
empty field as the only special case, and clearing a field is how somebody says "I do not want this any
more". 0 is exactly what "no length" is stored as everywhere else here.

The error box for a non-number is unreachable now and the parse cannot throw, so both are gone.

**Adam, 2026-08-23 (triage).** Works.

Filed from this test: OB-047 (bug - layout editor openable while autonomy running), OB-048 (bug - uncapped segment lengths).  They are in `issues.md` until they are picked up.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**Claude, 2026-08-23.** Closed on your word - a clean "Works." with nothing held back. Any bug filed from this test is tracked as its own item and does not hold this one open.
---

<a id="mt-134"></a>

### MT-134 - 2026-08-23 - Four things the night review found

**Disposition:** fixed unvalidated  
**From:** NR-1, NR-2, NR-3, NR-4 (2026-08-23-night-review.md)  
**Written:** 2026-08-23

**What to do.** **1. The one that matters.** With Autonomy Setup OPEN, change a locomotive's facing from
the TRACK DIAGRAM. Then close everything and reopen: the facing must be the one you chose. Do the same
for placing a locomotive and for removing one. Then repeat with the editor CLOSED.

**2. Station labels.** Right-click the platform road itself - the sensor - and check **Show Station
Here...** is offered. It must also be offered on curves, bumpers, crossings and uncouplers, and must NOT
be offered on switches or signals.

**3.** Open and close the editor a dozen times on a big layout and watch memory. Nothing to see if this
is right; it is here because the fault would be invisible until it was not.

#### Comments

**Claude, 2026-08-23.** An independent reviewer read tonight's diff. Four of its nine findings were
real; two of them were mine from earlier tonight, and one of those was serious.

**NR-1 - a rebuild that undid the edit that asked for it.** MT-125 made `placementChanged` refresh both
surfaces instead of one. But `rebuildRunningLayoutFromSetup` goes through `AutonomyViewerPanel.load`,
which CAPTURES the running layout's state back into the configuration before replacing it - `loc`,
`home` and the facing are all keys it overwrites. The running layout is stale at exactly that moment,
because the edit that provoked the rebuild went to the setup. So the fresh answer was written, captured
away, and regenerated from the reverted file.

That capture is right when the running layout is newer, and exactly wrong when the setup is. Both
rebuild-from-setup callers now skip it. `captureFromLayout`'s own comment states the precondition that
was broken - the rule did not move, the call site did, which is the shape I have been writing tests
against all night and still walked into.

**And the reviewer found one site; there were two.** The other is `autonomyEditorClosed`, which had the
same latent revert since long before tonight.

**NR-2 - my caption rule took away the platform road.** OB-042/044 replaced `isStraightThrough` with
`!isClickable()`, which reads like your sentence - "the only fair place to disallow them are clickable
elements like switches and signals" - and is not it: `isClickable()` also counts feedback, uncouplers,
links and lamps. Feedback is the platform road, the square the surrounding comment recommends and the
old rule allowed. So a fix that widened the rule for curves and bumpers quietly removed the commonest
place of all. It refuses switches and signals now, which is what you actually said.

**NR-3 - the grid registry never released a page.** `WeakHashMap<JPanel, LayoutGrid>` collects nothing
when the value reaches the key, and a grid holds its own panel. One page retained per editor, popup or
export. The value is a `WeakReference` now.

**NR-4** was a test whose message claimed more than it checked; the message says what it checks.

**Five findings I have not acted on** - NR-5 to NR-9, mostly comments that now contradict their code,
an unguarded `parseInt` on an 11-digit length, and a fair point that several new tests pass silently
when their source file is not found. They are in the report and worth a pass, none of them urgent.

**Adam, 2026-08-23 (triage).** Works, with notes.

1. works, but because the track diagram right click control is rightly disabled
2. works
3. write a test case for this

Filed from this test: FR-012 (feature request - test case for memory usage).  They are in `issues.md` until they are picked up.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 11:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Adam: "1. is OK... 2/3 are OK but I still need to test autonomy over a link.
3. write a test case for this."

Item 3 is "open and close the editor a dozen times and watch memory", and the heap growth itself is a
hands-on observation - there is no harness here that opens a real editor. What IS testable is the
defect behind it, which is a rule about source and therefore the part that rots:
`testEditorSurfaceRules.testEmptyingThePageCacheHandsItsLabelsBack`.

Every page in the cache holds a grid, and every grid holds the caption labels it registered with the
window. `discard()` stands down when its container is still cached - and the reset is what makes it not
cached, so at the moment the cache is emptied nothing has handed those labels back and nothing ever
will. One page's worth of labels per reset, for the life of the session. The rule is that the
assignment which empties the cache is preceded by the hand-back; mutation-checked by removing the call
and watching it fail.

Item 1 and item 2 Adam has already run. What is left of this entry is "autonomy over a link", which he
flagged as still to do.

---

<a id="mt-135"></a>

### MT-135 - 2026-08-23 - Renaming a page keeps its autonomy setup

**Disposition:** fixed unvalidated  
**From:** OB-049  
**Written:** 2026-08-23

**What to do.** On a page with several named stations, at least one caption, a paired link and a
switched-off link: **rename the page**. Then check, without reopening anything, that every station is
still a station, every caption still reads the same, the pairing still points both ways, and the
switched-off link is still off.

Then rename it BACK and check the same list again.

Then close TrainControl, reopen it, and check once more - the first two passes are about memory, and
this one is about the file.

#### Comments

**Claude, 2026-08-23.** Adam: "CRITICAL: renaming a layout page disconnects its autonomy config.
stations and links are broken."

`AutonomyCompanionStore.renamePage` was not broken. It is one of the most careful methods in the file -
it rekeys all eleven collections, both halves of the captions and the portals, and the tile keys inside
every configuration, and its comments record two earlier defects it had been extended to cover.

**Nothing called it.** `grep -rn "renamePage" src/` returned the declaration and one comment; the only
callers in the repository were two tests, which call it directly and pass. So a rename left every key
pointing at a page that no longer existed, and the next reconcile did exactly what it should with a
square that has been deleted:

```java
pointNames.remove(key);
stations.remove(key);
```

Every station on the page, gone - and renaming back could not undo it, because they had already been
deleted on the save that happened while the new name was current.

`duplicateOrRenameCurrentLayout` calls it now, before `layoutEditingComplete` rebuilds the session,
because that rebuild is what reconciles.

**The part worth keeping.** This is the third time this shape has cost something - DD-A6 found
`HomeLocomotiveMenu` had lost four of its five callers, leaving two safety warnings unreachable with
their tests still green; MT-112 was the same. And my own guard was green throughout:
`testStoreCollectionsAreHandledEverywhere` asserts that `renamePage` HANDLES every collection, which it
does perfectly, and never asked whether anything CALLS it.

It asks now. `testEveryRuleTheStoreOffersIsCalledBySomething` requires every rule the store offers to
have a caller in the application, with a written exemption for the two read-only accessors that
legitimately have none. Mutation-checked: removing the call I just added fails it by name.

**Duplicating a page is not covered by this.** A duplicate should probably COPY the setup onto the new
page, and today it does not - worth its own item if you want it.

**Adam, 2026-08-23 (triage).** Does not work.

Does not work.  Immediately after rename, all stations are gone.  Error in the editor: loc at s88 is standing somewhere else, and can't be at 2 places at once.

I was also able to start autonomy while the editor was open.  This should not be possible.

Renaming the page back did not restore the stations, and added more errors to the config (says 13/46 errors, but the editor only shows 4 errors total).

*Run against commit 62af99e6, build\classes, compiled 23 Aug 13:28 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Adam was right and my fix was aimed at the wrong thing. Calling `renamePage`
was necessary and not sufficient: it carries the page's NAME across, and a rename was also changing
every page's ID.

`writeLayoutIndex` numbered pages by their position in the list it is given, and the rename did
`layoutList.remove(old)` then `layoutList.add(new)` - so the renamed page went to the END. The list also
arrives sorted (`MarklinControlStation.getLayoutList` sorts), so a page's id was really its place in the
alphabet, and renaming "1 - Main and neighbours" to "1 - Main" moved it from first to last.

The setup is keyed by page id on disk. So one rename reattached the whole setup to the wrong pages -
and because ids that shift by one round-trip unchanged (id 1 reads as the page now called 1 and writes
back as 1), the file looked perfectly consistent while meaning something else entirely. The
coordinates of one page's settings do not exist on the next page along, so the following save
reconciled them away as deleted squares. Adam lost 19 point names, 14 stations, 22 tile directions, 15
captions and 3 lengths.

The two errors he saw both follow from it. "All stations are gone" is the pruning. "Loc at s88 is
standing somewhere else, and can't be at 2 places at once" is the other half: configurations key by
page NAME, not id, so they orphaned under the OLD name instead - his `Autonomy 1h` was holding 34
points keyed `1 - Main2:x,y`, four of them carrying locomotives that also existed under `1 - Main`. And
"13/46 errors but the editor shows 4" is the count of those orphans, which can be counted but not
displayed, because they are on a page that does not exist.

Four things changed. A rename keeps its slot and is told to the index so the page keeps its id.
`fromStored` asks which of a rename and a renumber happened - the same question `pageIdConflicts` asks,
whether the name that id used to carry still exists - instead of trusting the current index. `readShared`
reads `pages` before anything is translated, having read it AFTER the one collection that translates
inline. And `AutonomySession.save` no longer prunes at all while the numbering is suspect, since
detection existed and only ever warned.

His data was rebuilt from the last pre-damage snapshot with every key re-keyed and verified against the
real tiles - 249 references, no orphans - and is in the repository.

**Still not covered by this:** duplicating a page does not copy the setup onto the copy. Same as before.

---

<a id="mt-136"></a>

### MT-136 - 2026-08-23 - Two more of one shape, from the history review

**Disposition:** fixed unvalidated  
**From:** TD-1, TD-2 (2026-08-23-three-day-history.md), OB-046  
**Written:** 2026-08-23

**What to do.** **1. From the TRACK DIAGRAM's right-click menu** - not the editor - make a square a
station, and separately change whether trains may turn round there. Each must show on the diagram at
once, without closing anything.

**2. A link switched off before today.** Open a setup you saved earlier in the week that has a
switched-off link, and check autonomy will not route through it **in either direction** - the old files
only recorded it at one end.

**3. Going to a link's other end** with unsaved work asks Save / Discard / Cancel, and each answer does
what it says.

**Also, since the fix:** put a locomotive on a square whose copies can none of them be driven away
from - the right-click **Place** item goes grey and says why. Now, with the same square under the
pointer, press **Ctrl+V**. Nothing must happen, and the log should say why. Then start autonomy and try
Ctrl+V again on any square: refused, for the other reason.

#### Comments

**Claude, 2026-08-23.** Three findings, and two of them are the same mistake I made twice.

**TD-1.** `radio()` - the helper that builds all three "what may a train do here" answers, and the
facing answer - called `refresh()` and nothing else. That redraws this panel and tells the other surface
nothing, so a station or a turning rule set from the track diagram was written and never appeared.

That is exactly OB-039, which was the FACING radio one submenu along. I fixed OB-039 in its own lambda
and left its two neighbours - in the same helper, reached by the same gesture - still calling
`refresh()`. The redraw is in the helper now, where every answer gets it, and the test that guards it
looks at the helper rather than at whichever write was reported.

**TD-2.** OB-041 made switching a paired link off mutual - on the WRITER. The readers went on asking
about the near end alone, and there is no migration, so every setup saved before today holds one-ended
disables and autonomy went on routing through a doorway you had excluded, in one direction.

A doorway is shut if EITHER end is shut now. That repairs the old files without anybody running
anything, which is why it is the reading half rather than a migration.

**Both were found by a reviewer reading three days of commits**, not by a test - and neither would have
been found by reading the end state, which is what the other two reviews did. A fix applied to one of
several call sites looks complete in the diff that contains it.

**Adam, 2026-08-23 (triage).** Does not work.

1. is OK.

BUT In the track diagram viewer: There is a condition where "place {locname}" is greyed out saying it can't possible leave so can be placed, but Place Locomotive still works.

2/3 are OK but I still need to test autonomy over a link.

**Claude, 2026-08-24.** The greyed item was not the one that worked. `Place {loc}` on the right-click
menu greys itself correctly when no copy of the square can be driven away from - it sets the item
disabled and attaches no listener, so it is inert. The door that ignored the rule is the KEYBOARD one:
Ctrl+V over the same square calls `moveLocomotive` directly, with no "can it leave" test and no busy
test either.

Two ways to reach one action with the rule on only one of them, which is this codebase's recurring
shape. The keyboard door now asks the "can it leave" question - of the POINT rather than of the square's
copies, because by then the copy is already chosen.

**Correction, same day.** I also added a busy check to that door and said it "had no check of any
kind". That was wrong: the handler already refuses all three keys while autonomy is busy, several lines
higher, before the square is even resolved. My check sat behind it and could never fire. Review caught
it; the dead branch is gone, and what is actually new here is the "no way out" rule alone.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 13:28 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-137"></a>

### MT-137 - 2026-08-23 - The round of bugs from Adam's last test pass

**Disposition:** fixed unvalidated  
**From:** OB-045, OB-046, OB-047, OB-048, OB-050, OB-051, OB-052  
**Written:** 2026-08-23

**What to do.** With trains RUNNING: the Autonomy menu's setup entry must be greyed, and pressing the
Edit button must open **neither** editor - an error instead. With a configuration in the fix-it state,
the diagram's right-click menu must show **Start Autonomy** greyed.

Then, with trains stopped: **Length...** must refuse anything but digits and refuse a fourth one; clear
it and press OK and the length becomes 0. **Import** and **Export** are in the Configuration submenu
under a divider - Export greyed with nothing loaded, Import never greyed. Going to a link's other end
with unsaved work asks Save / Discard / Cancel and each answer does what it says.

Finally, make a diagram edit that costs a station its square and save: the report must be headed and
explained, not a bare list of names.

#### Comments

**Claude, 2026-08-23.** Seven items from the same pass, all small, three of them the same shape - a rule
that was enforced only after the user had committed to the gesture.

**OB-047** was the one with teeth: `openLayoutEditor` asked whether autonomy was busy only for the
AUTONOMY editor and fell back to the track one, so pressing Edit during a run opened the diagram editor -
where a tile can be moved, retextured or deleted under a train on its way to it. Neither opens now, and
with no fallback: falling back means opening SOMETHING, and while trains are running the answer is
nothing at all.

**OB-045 and OB-050** are the greying half of the same idea - saying "not now" before the click rather
than after it. Only the setup entry is greyed, not the whole Autonomy menu: what somebody with trains
running most needs to reach is the way to stop them.

**OB-051 was not a bug.** Both Import controls were ungated and the menu's one is deliberately exempt
from the greying. Adam: "the option is just buried and I didn't see it" - which is the same outcome as
disabled, reached by a different route. They sit under a divider in the Configuration submenu now,
beside the list of configurations they act on. Export keeps its greying; Import never gets it, because
the moment it is most needed is when the current setup will not load.

**OB-052.** The tidy-up report ran two lists together that mean opposite things - stations FORGOTTEN and
stations KEPT because something still names them - with no title and no sentence. A reader could not tell
which of their stations they had just lost.

**Adam, 2026-08-23 (triage).** Does not work.

segment lengths are OK
fix it start greyout does not work (see other bug)
try not to bunch multiple bugs into a MT, this will reduce my accuracy.

**Claude, 2026-08-24.** Noted, and this entry is the example - seven items, one verdict, and the one
that failed could not be reported without reporting the six that passed. MT-142 and MT-143 below are
narrower; MT-143 is still four items, because they are four one-line UI faults from one pass, but
nothing in it is load-bearing.

"Fix it start greyout does not work" is OB-057, filed separately and fixed under MT-143. The greying
was the wrong mechanism for it: the button's enabled state is computed when the tabs are built and when
a configuration is loaded, so an error appearing later - a page renamed, a tile deleted - leaves the
affordance stale while the action stays live. There are three ways to press it as well. It is refused
at the ACTION now, which cannot go stale, and the dialog says how many errors there are.

diagram edit that costs the station- in the current state, I can't validate, but I see no error.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 13:28 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-138"></a>
### MT-138 - 2026-08-23 - A split copy's name and its facing disagree on a curve

**Disposition:** superseded

**Claude, 2026-08-23.** This one is a design decision rather than a fix, because fixing it renames
Points on your layout.

A square trains can reach from two sides is emitted as two Points, and each is named with the direction
its train is travelling - "Bahnsteig 4 (eastbound)". That word comes from `heading`, which answers "the
opposite of the side arrived by". `facingOf`, which decides which way the train on that copy is actually
pointing, answers by following the ROUTE the train took - and its own comment records why the simple
version is wrong: "A train entering an N-E curve by N leaves by E, and saying it faces S describes a
train sitting across the rails."

So on a curve or a diverging leg, the copy is called "(eastbound)" while the facing menu, the arrows and
the running log say the train faces south. One rule in two places, with only one of them corrected.

**Nothing routes on the text**, so this is a labelling defect - but the running log is where you go to
work out what a train is doing, and you have curved stations.

**Why I have not just changed it.** A Point's NAME is what every configuration refers to: placements,
homes, exclusions and timetable entries are all by name. Changing which word appears renames those
Points, and a configuration naming a Point that no longer exists is refused by `parseAuto`, which
invalidates the whole layout - the same failure UR-9 was about. Correcting it properly needs a migration
that rewrites the configurations at the same time, which is a change I would not make quietly the week
of a release.

**What I need from you:** whether to do the migration, or to leave the names as they are and treat the
word as "the side it came in by" rather than a heading. If the latter, the honest fix is to change the
word - "(from the west)" reads as what it is, and would need `StationIndex.withoutArrivalSuffix`
updating - and that is still a rename.

The divergence is now recorded in `heading`'s javadoc either way, so the next reader is not left to find
it again.

**Adam, 2026-08-23 (triage).** Does not work.

I don't understand the question.  Logically, a curved station accepts ingress and egress from two directions, just like straight track.

**Claude, 2026-08-24.** That answers it, and the answer is that the question was malformed - so this is
superseded rather than decided. A curve is not a special case: it takes trains in and out from two
directions exactly as straight track does, so a split copy needs no different naming and no different
facing rule. Nothing to build, and the migration I was going to ask about does not arise.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 13:28 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-139"></a>
### MT-139 - 2026-08-23 - A train dispatched by hand now counts as a run

**Disposition:** fixed unvalidated
**From:** Adam's ruling on the first signal question
**Written:** 2026-08-23

**What to do.** This entry had no steps until now - it was written as an explanation and never turned
into something to run. Ten minutes with one train:

1. From the track diagram, right-click a station and **dispatch a train by hand** to a platform that
   has a protecting signal. Watch the signal: it must go **RED as the route is set**, not when the
   train arrives, and back to green after the train leaves.
2. While that hand-dispatched train is running, check the same things are refused as during autonomy:
   neither editor opens, the Layouts menu refuses delete/rename/duplicate/combine, the locomotive
   database refuses edits, and Ctrl+X / Ctrl+V over a square do nothing but say why.
3. Let it arrive. Everything above must become available again.
4. Then do it once more with **two** trains dispatched by hand at the same time, and check the second
   is refused a path that crosses the first's.

#### Comments

**Claude, 2026-08-23.** Your ruling on the first signal question - "The same thing should happen in
manual operation vs auto - the same switches and signals set, and guards applied" - is in, and it
changes more than signals. Worth ten minutes with a train, because it is the widest behaviour change of
this round.

**What was wrong.** Dispatching a train from the diagram's right-click menu ran it on a bare thread, so
nothing counted it as running - and the route is locked BEFORE the locomotive is registered. For the
whole locking phase the railway believed nothing was happening, so protection stood down and the
destination platform's signal stayed GREEN for the entire approach. It only went red once the train was
standing at the platform, which is the one moment it no longer matters.

**What to check.**

1. Send a train by hand to a platform that has a protecting signal. The signal should go RED as the
   route is set, not when the train arrives - and back to green after it leaves.
2. While that hand-dispatched train is running, the same things should be refused as during autonomy:
   the track diagram editor and the autonomy editor should not open, the simulation toggle should be
   off-limits, and locomotives should not be editable or deletable. **This is the part to look at
   hardest** - it is new. Before this, all of those were allowed while a hand-driven train was moving.
3. When it arrives, everything should come back on its own. If anything stays greyed after the train
   has stopped, that is a real bug and worth filing - it would mean the count is not being given back.

**What has NOT changed:** placing or removing a train by hand, with nothing running, still moves no
signals. That was the defect the old guard existed for - "cutting a locomotive off a platform with
Control+X drove its protecting signals on the spot" - and it stays fixed.

**Adam, 2026-08-23 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 13:28 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-140"></a>
### MT-140 - 2026-08-23 - Bless the baseline once you are happy with the railway

**Disposition:** needs decision

**Claude, 2026-08-23.** This is the entry that keeps your own request from being forgotten: "Once we are
bug free, can you capture confirmed good state along with config files for validation? that is better
than guessing."

The machinery is built and committed - `testConfirmedGoodState` opens a copy of a blessed layout, derives
the graph, and compares it against what was recorded. **No baseline is captured yet**, deliberately:
blessing a railway nobody has confirmed would record whatever was wrong with it as the definition of
right.

Until one exists, both of its tests skip. That is the honest state, and it has a cost the history review
named (TD-5): the class is listed in `build.xml`, so it counts towards the "89 classes clean" figure
while being unable to fail. The number is slightly less true than it looks, and nothing on screen says
which classes contributed - so this entry is the record.

**What to do when you are ready.** With the railway in a state you have confirmed is right:

```
ant test -Dbaseline.capture=true
```

That copies `cs2_sample_layout/config` into `test/baseline/layout`, derives the graph, and writes both
down. Commit what it produces. From then on every run compares against it, and a change to the reduction
- an edge that stops existing, a station that stops being one - fails with a readable diff rather than a
changed byte in a blob.

**One thing that changed today.** "The baseline reduced to nothing" used to be a skip. It is a failure
now: a reduction that collapses to nothing is the loudest regression this test exists for, and as a skip
it was the one failure it could not report.

**Nothing to do until you say the railway is right.** I have not captured one and will not without you
saying so.

---

<a id="mt-141"></a>
### MT-141 - 2026-08-23 - Editing a placement while trains are out puts the others back where they started

**Disposition:** fixed unvalidated

**Claude, 2026-08-23.** A design decision, and I would rather you made it than have me pick.

**What happens.** Any setup edit that calls `placementChanged` - a facing, a home, a station
designation, a locomotive placed from the diagram - rebuilds the running layout from the configuration
file. That rebuild deliberately skips the "capture" step, which is what NR-1 was about: capturing first
folded the running layout's state back into the configuration and wrote the stale answer over the edit
that had just asked for the rebuild, so the edit was undone on its way to being redrawn.

Skipping it fixes that. But the capture was also the only thing folding run-derived state back in - and
nothing writes a train's live position to the configuration as autonomy drives it. So if trains have
been running, the configuration still says where they all STARTED, and a rebuild puts them back there
in the model. The trains do not move; what the app believes about them does.

**When it bites.** Only with autonomy actually running, and only for locomotives other than the one you
just edited. Stopped, or before a run, the configuration and the railway agree and there is nothing to
lose.

**Three ways out, and they are genuinely different:**

1. **Leave it.** Editing the setup mid-run is unusual, and the alternative costs more than it saves.
   The rebuild is already refused while autonomy is BUSY - this is the window where it is running but
   not busy.
2. **Update the running layout in place** rather than rebuilding it - which is what
   `LayoutRightclickAutonomyMenu.placeFacing` already does for its own edit. No rebuild, so nothing is
   put back; but every edit needs its own in-place equivalent, and one that gets missed is a setup and
   a railway that quietly disagree, which is the whole class of defect OB-034 and OB-035 came from.
3. **Capture only the positions**, then rebuild - fold back where the trains actually are without
   folding back anything else, so the edit survives and the placements do too. The most correct and the
   most code: it needs a capture that takes one field rather than everything.

**My reading**, for what it is worth: 3 is right and 1 is defensible until somebody is bitten. 2 I
would avoid - it is the shape that produced the seam we have spent this week repairing.

**What I have not done.** The reviewer also asked for the invariant to be pinned either way: place a
locomotive, call `captureFromLayout` with a layout built before that placement, and require the
placement to survive. That test is worth having whichever option you pick, and I will write it once you
have.

**Adam, 2026-08-23 (triage).** Does not work.

Never allow any modifications to a running layout.  This includes locomotive database, the track diagram, the autonomy config, or the locomotive placements.

*Run against commit 62af99e6, build\classes, compiled 23 Aug 13:28 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Ruled, so this stops being a decision and becomes a rule. Adam, separately:
"I'd like things to propagate correctly, but locking down the editors is the right call" - so the
propagation work stays as it is and the lock is added on top rather than instead.

Of the four things named, three were already refused: the track diagram and the autonomy config share
one door (`openLayoutEditor`, which refuses while autonomy is busy and deliberately does not fall back
to opening the other one), and placements were refused on the right-click menu. Two doors had no check
at all:

- **adding** a locomotive to the database. Editing, deleting, renaming and multi-units all refused;
  adding did not, and it is the same database.
- the **full locomotive state sync**, which calls `allFunctionsOff()` across the whole database before
  it re-reads anything - so during a run it strips the lights and sounds off moving trains and then
  fights the automation for control of them. The most destructive of the four and the one nobody had
  thought about.

- and the keyboard placement door, which is MT-136.

The check was written out longhand at each of the four places that had it, which is how two came to be
missed; it is one call now, so the next door added is one line rather than one more copy.

**And four more doors, found by a second review.** The lockdown covered the locomotive database and the
placements and stopped there. Adam's ruling also names the track diagram, and a PAGE is the track
diagram:

- **deleting a page** had no running check and no editor check at all. It deletes the page file,
  forgets that page's whole setup and rewrites the index, then rebuilds the autonomy session - stopping
  the diagram driver and nulling the session under moving trains.
- **renaming or duplicating a page** refused an open editor, which reads as though the question had
  been asked. An editor being closed does not mean the railway is standing still.
- **combining linked pages** likewise.
- **the database sync** - not the state sync, which was already guarded, but `doSync`, which adds
  locomotives and deletes and re-adds routes. It is the more invasive of the pair and had nothing. The
  model defers only the address update, and says so: "A rename and a manual address change are both
  refused while running; a sync had no such guard."

Six guarded doors now, all through the one helper.

**So this entry is worth running twice** - once for the locomotive doors it was written for, and once
with trains running to check that the Layouts menu refuses everything: delete, rename, duplicate,
combine.

---

<a id="mt-142"></a>

### MT-142 - 2026-08-24 - A page keeps its identity when other pages change

**Disposition:** fixed unvalidated
**From:** OB-059, OB-060, OB-061
**Written:** 2026-08-24

**What to do.** This is about page IDs, which are invisible - so the check is on what they carry.

Set up two pages so you can tell them apart: on **each**, name two stations, mark them stations, give
one a length and one a caption.

Then, one at a time, checking after each that BOTH pages still have everything above:

1. **Rename** the first page to something that sorts to the front of the list (put an `A` at the
   start). The list is alphabetical, so this moves it.
2. **Add a new page**, then delete it again.
3. **Delete** a page you do not care about, added for the purpose, that sits alphabetically BETWEEN
   your two.

After each step, close TrainControl and reopen it, then look again - the first look is about memory and
the second is about the file.

Finally, delete one of the two set-up pages on purpose. Its own stations and captions should be gone
and the OTHER page must be untouched. Check the log says how many squares it forgot.

#### Comments

**Claude, 2026-08-24.** Found by review while validating the MT-135 fix, which turned out to be one
door of three.

**OB-060 - ids were positions.** `writeLayoutIndex` numbered pages by their place in the list it is
handed, and `MarklinControlStation.getLayoutList` returns that list SORTED - so a page's id was its
place in the alphabet. The autonomy setup is keyed by page id, so anything that changed the set of
names renumbered other pages and silently reattached their settings to whatever track had taken the
number. An id is a page's identity now: read back from the index by name, kept for every page still
there, retired when a page goes, and issued fresh only for a page that has never had one. Written
explicitly for every page including the first, because an absent id is read as the page's POSITION and
a retired id leaves a gap.

**OB-059 - deleting a page told the setup nothing.** `renamePage` got a caller under OB-049; its
counterpart never had one. The file went, the index was rewritten, and everything the setup knew about
that page stayed behind keyed to a page that no longer existed - then got read through whatever page
had inherited the id. Where two pages shared coordinates, one page's names and stations landed on the
other's track and the rest was pruned by the next reconcile. Nothing warned, and nothing could: the
test for a renumber is whether the old NAME still exists, and after a delete it does not - which is
precisely what a rename looks like. `deletePage` forgets that page's squares through `forgetSquares`,
which also un-points the captions, signals and portal ends on OTHER pages that named them, and drops
its points from every configuration.

**OB-061 - a guard that promised more than it checked.** The source guard added for the home-dialog
defect had a message naming `JComboBox.setSelectedItem` and a test that required `getName().equals(` on
the line - so the one defect it was written for was invisible to it. Widened; then narrowed again after
it flagged `Layout.locomotivesToRun.remove(getCurrentLocomotive())`, which is a `Set<Locomotive>` where
removing by object is exactly right. `contains`, `indexOf` and `remove` are out: they take an Object, so
they are equally the correct call on a collection of locomotives, and a guard reading source text a line
at a time cannot tell which it is looking at. A guard that has to be argued with is one somebody
eventually adds an exemption list to. Verified by putting the real defect back and watching it fail.

**Three corrections from a second review, all of them mine.**

**The ids are not "above every id ever issued".** They are above the highest id still IN THE FILE, and
those are different numbers the moment the highest-id page is the one deleted - the index is the only
record, and a number gone from it cannot be told from one never used. My test proved the weaker claim
by deleting a MIDDLE page, which the mechanism does handle. What actually keeps a reused id safe is
`deletePage` having forgotten the old page's settings first, so there is nothing left for the number to
carry. Both tests were rewritten to say that: one pins that no new page takes a LIVE page's id, and one
pins that a page reusing a retired id inherits nothing - the second being the one that matters, and
mutation-checked against `deletePage` doing nothing.

**Renaming or deleting a page invented a setup on a layout that never had one.** Both paths called the
LAZY session getter and then saved, and `store.save()` does `mkdirs()` and writes unconditionally.
`repairAutonomyLocomotive` guards this exact hazard twice over and explains why; its two siblings,
written in the same series, had neither guard. They use the already-built session now, write only to a
setup that already exists, and otherwise repair the file directly - which writes nothing unless the
file is there. The delete also happens in a safer order: the page file goes first, so a failure to
delete it can no longer leave the setup forgotten for a page that still exists.

**`deletePage` gathered only squares that appear as KEYS.** A protecting signal, a blocker or the far
end of a portal can sit on the deleted page while nothing is recorded about it - only pointed at it -
so the pointer survived the page and dangled until the next reconcile. `renamePage` handles that half
explicitly and this method's own contract claimed to. It does now.

Six tests, each seen failing first, in `testPageIdsAreDurable`. The delete and the durable ids are
also why `AutonomySession.save`'s refusal to prune while the numbering is suspect is now a backstop
rather than the fix.

---

<a id="mt-143"></a>

### MT-143 - 2026-08-24 - Four small ones from the same test pass

**Disposition:** fixed unvalidated
**From:** OB-054, OB-055, OB-056, OB-057
**Written:** 2026-08-24

**What to do.** Four separate looks, none of them long.

1. **Right-click a page link.** There must be no "This Link" heading, and no band with nothing in it
   between two dividers - anywhere on that menu.
2. **In the track diagram editor, turn the grid on.** There must be no grid on an extra row below the
   diagram, and none on a part-width column to its right.
3. **In the autonomy editor, turn the grid on and off.** It must actually appear and disappear - and
   with it ON, the tiles must not shift or gain white gaps between them.
4. **Get the setup into a state with errors** (renaming a page no longer does it, so delete a sensor a
   station is on). **Start Autonomy** must refuse, and say how many errors there are. Then open an
   editor and try again: it must refuse for that reason too.

#### Comments

**Claude, 2026-08-24.** All four from Adam's 22:10-22:48 pass.

**OB-054.** The menu is assembled by a dozen independent blocks, each adding a divider and then
whatever it has for this square, so a block with nothing to offer leaves its divider behind. Rather
than teach twelve blocks to look ahead - and leave the thirteenth to get it wrong - the shape is
corrected once at the end: leading, trailing and doubled dividers go, and so does a heading left with
nothing under it. The "This Link" heading goes outright; the menu is already titled with what the
square is.

**OB-055.** Those two rows are `LayoutGrid`'s own spacers - "a dummy column at the end with nothing in
it to ensure long labels don't misalign things". They hold nothing, so GridBagLayout gives them
whatever height is left rather than a square's worth, which is exactly the half-height cells Adam
described. They are marked as spacers and no longer take the grid.

**OB-056.** The toggle had nothing to act on in that editor: `restingBorder` returned null for autonomy
mode whatever it said. What MT-127 required was that the tiles sit FLUSH - "there is now a gap between
tiles (essentially a white grid)" - which is a rule about ROOM, not about whether a line is drawn. So
the grid is drawn there by `overlayLine`, which paints and reserves nothing. The hover outline now
matches on insets rather than on "is the resting border null", because with an overlay in play those
stopped being the same question. The ratchet was asserting the implementation that happened to satisfy
MT-127; it asserts the room now.

**OB-057.** Two halves. The greying was the wrong mechanism - see MT-137 - so starting is refused at the
action, which cannot go stale, and the dialog gives the error count. Starting with an editor open is
refused for its own reason; the editor holds the pages the session is built from.

**The `[]` is not fixed here** - it is its own item, OB-063, under MT-144, since Adam has asked for
narrower entries.

---

<a id="mt-144"></a>

### MT-144 - 2026-08-24 - The editor window comes forward, and the info mark is an icon

**Disposition:** fixed unvalidated
**From:** OB-058, OB-063
**Written:** 2026-08-24

**What to do.**

1. Open the track diagram editor. Click on the main window so the editor goes behind it. Press
   **Edit** again: the editor should come to the front. No error dialog.
2. Get a locomotive into the state where its panel says **No available paths** - place one somewhere it
   cannot leave. Beside that text there should be a small blue **information icon**, not a box or a
   pair of brackets. Hover it: the reasons should still appear.
3. Then get the same panel into any other state (send the train somewhere, or pick it up). The icon
   must be gone - it belongs to that one message.

#### Comments

**Claude, 2026-08-24.**

**OB-058.** `showOpenEditor()` already existed and does exactly what Adam asked for - it was written for
the menus, which had the same problem. The Edit button was the one door still answering with a dialog.
It raises the window now, and keeps the dialog only for the case where the button is disabled and there
is no editor to raise, which is a real refusal rather than a misunderstanding.

**OB-063.** Adam: "the status page says No Available Paths [] - not sure what that [] is doing in the
UI." It was never a `[]`. The mark was written as U+24D8 CIRCLED LATIN SMALL LETTER I, and the UI font
on his machine has no glyph for it, so Swing drew the missing-glyph box.

My first fix asked the font whether it could display the character and fell back to `(i)`. Adam: "then
for the (i) icon we need to use an actual gif" - which is right, and for a better reason than the one I
had: asking the font makes what the user sees depend on which machine they are on, and the fallback is
uglier than either. It is a 14px icon now, drawn at its final size rather than resampled down - at that
size every pixel is a decision, and reducing a large drawing produced a rounded box with two blobs in
it. It sits beside the text as the label's icon, and is cleared alongside the tooltip it belongs to,
because one branch sets it and every other branch has to be the one that takes it away.

---

<a id="mt-145"></a>

### MT-145 - 2026-08-24 - A locomotive rename reaches a setup nothing has open

**Disposition:** fixed unvalidated
**From:** OB-062
**Written:** 2026-08-24

**What to do.** The point of this one is to rename a locomotive **before anything has touched autonomy
in that session**, which is the window where it went wrong.

1. Start TrainControl fresh on a layout that HAS an autonomy setup, with a configuration that places a
   locomotive and gives it a home.
2. Without opening the autonomy editor, without loading a configuration, and without going near the
   Auto tab - **rename that locomotive** in the locomotive database.
3. Now load the configuration. It must load cleanly, with the train placed and its home intact under
   the NEW name. Before this fix it would refuse the whole layout.
4. Then repeat the whole thing with a **delete** instead of a rename: the configuration must load, with
   that locomotive simply no longer placed.

**And the other half - nothing must be invented.** On a layout where autonomy has NEVER been set up (no
`config/autonomy` folder at all), rename a locomotive. Check the folder afterwards: there must still be
no autonomy setup. Renaming a locomotive is not a request for autonomy.

**And the page record.** After the rename in step 2, open `config/autonomy/setup.json` in a text
editor and find the `"pages"` entry near the top. It must still list your page names. If it reads
`"pages": {}` the repair has blanked the one record that lets the app tell a page RENAME from a page
RENUMBER - which is what cost you 19 point names on 23 August.

#### Comments

**Claude, 2026-08-24.** Found by review, filed as OB-062 and left unworked for Adam to place; he asked
for it next.

`repairAutonomyLocomotive` returned immediately when no session was built, and the comment explaining
why was half right. Its reasoning about the SESSION is sound and stays: `getAutonomySession()` opens
every page, runs the caption migration, can raise a dialog and then writes a `setup.json`, so building
one here would create autonomy out of nothing on a layout where nobody asked for it.

What was wrong was the sentence that followed - that the file "is read the next time it IS opened - by
which time this rename is already in the locomotive database". Nothing repairs locomotive names at
load. The file is read as it stands, so the old name survives in the placement, the home and the
exclusions until somebody chooses that configuration; `parseAuto` then answers a locomotive it cannot
resolve by invalidating the whole layout, days later, with nothing connecting it to the rename.

A bare `AutonomyCompanionStore` is not a session: it opens no pages, runs no migration, raises no
dialog, and `exists()` means it writes nothing at all unless the setup file is already there. So
`repairLocomotiveOnDisk` does the repair by the one route that cannot fabricate anything.

**The fix nearly caused the thing it was written to prevent.** Probing the new method rather than
reading it showed the repaired file coming back with `"pages": {}`. Nobody calls `setPageIds` on a bare
store - there is no session to tell it what the pages are called - so `sharedFields()` wrote that record
from an empty map. It is the only evidence a renumber ever happened, and blanking it would have
disarmed the detection for the whole setup: a LOCOMOTIVE rename quietly making the next PAGE renumber
undetectable. The repair now hands the store back the numbering the file was written under before it
saves, and a test asserts the file comes out carrying its page names, mutation-checked.

Four tests at the store, two of them seen failing first - the third is the "creates nothing" rule,
which a do-nothing implementation passes by definition and which is there to stop the fix being made
the wrong way. And a fourth in `testLocomotiveIdentityPropagates` asking the SOURCE whether the
null-session path actually calls it: testing the rule would otherwise leave the call as the only
uncovered part, which in this codebase is usually where the defect is - `renamePage` was faultless and
had no caller for weeks (MT-135), and `HomeLocomotiveMenu` lost four of its five callers with its tests
still green (DD-A6).

---

<a id="mt-146"></a>

### MT-146 - 2026-08-24 - A train may leave the point that holds a station back

**Disposition:** fixed unvalidated
**From:** FR-001
**Written:** 2026-08-24

**What to do.** You need a station **B** that you have held back by another point **Y**, and a way for a
train to run from Y to B.

1. Put a train on **Y** and nothing on B. Ask autonomy to run. It must be willing to send that train
   from Y to B - the train leaving Y is the one movement that clears the condition, so it is exempt.
2. Now put a **second** train on Y and a third somewhere else with a path to B. B must be refused to the
   third train while the second stands on Y.
3. Take the train off Y. B must become available again straight away - the restriction is a condition,
   not a one-way door.
4. If Y is a square reachable from two sides (it splits into two Points), stand a train on the OTHER
   copy and repeat step 1. It must still be exempt: it is the same piece of track.

#### Comments

**Claude, 2026-08-24.** FR-001 shipped counting any train on the watched point, including the one being
dispatched. Adam: "The condition should not apply to trains leaving - only departing."

Without the exemption the one movement that clears the condition is the movement it forbids: a
locomotive standing in the yard could never be sent to the platform the yard holds back, and while it
sat there the platform was shut to everybody else too. Autonomy had no way out of that at all - only a
person driving the train off by hand.

It is the same choice `Edge.isOccupied` makes, for the reason `isLockHeld` records: a train parked next
to a junction was a permanent roadblock for every route across it, and two of them could deadlock with
no way out for either.

Three tests in `testStationBlockedByAnotherPoint`, including the mutation check that the rule still
holds for everybody else - `standing == null || standing.equals(loc)` collapses to `true` if the second
clause is ever right about the wrong locomotive - and one for the twin copy, which is the subtle half.
They needed a fixture with a way OUT of the yard: the old one had no edge leaving it, so the exemption
had nothing to be asked about, which is exactly why nothing pinned this either way.

---

<a id="mt-147"></a>

### MT-147 - 2026-08-24 - The restored layout is the one you had

**Disposition:** needs test
**From:** the 23 August data loss
**Written:** 2026-08-24

**What to do.** This is a check on YOUR railway, not on a fixture, and it is the one that matters most.

Open the layout and go through each page:

1. **1 - Main** should carry 20 stations and 26 point names. **2 - Bottom** should carry 13 stations
   and 21 names. Nothing on the other three pages.
2. The station at **Main (20,13)** should be a sensor on address **10** again - a test had left it on 1.
3. Captions should read the same as they did, and each should point at the station it is about.
4. The portal between **Bottom (10,9)** and **Main (15,5)** should be paired, both ways.
5. Load **Autonomy 1h**: 59 points, four of them carrying locomotives. It must load without
   complaining that a locomotive is in two places at once.
6. Load **Autonomy 1**, **1j** and **1k** in turn. Each should have 58 points and load cleanly.
7. Then close TrainControl, reopen, and look once more - the first pass is about memory, this one is
   about the file.

Anything missing here is not a bug in the restore to be argued about: say what is missing and it can be
taken from the snapshots, which are kept.

#### Comments

**Claude, 2026-08-24.** The setup was rebuilt from the last pre-damage snapshot (12:07 on 23 August,
byte-identical to the 01:18 one) with every key re-keyed into the current page numbering, and every key
AND every square-valued value checked against the real tiles - 249 references, no orphans.

The file was MIXED, which is what made it delicate: entries that survived the renumber kept
old-numbering keys, which round-trip unchanged, while anything edited afterwards was written in the new
numbering. Each key was bracketed against the 12:49 file to tell which numbering it was written in.
Three things nearly went wrong and were caught by checking rather than by reasoning: `stationSignals`
values are squares too and were left untranslated on the first attempt, a portal existed twice under
the two numberings, and `disabledLinks` came back empty from a merge that had silently dropped them.

Also restored: `1 - Main.cs2` from the 22:17 copy, which differs from what a test had left by exactly
one element - the s88 address at (20,13). And 34 points orphaned under the old page name `1 - Main2`
were cleared from `Autonomy 1h`; every one of their coordinates already existed under the live name
carrying more attributes, so nothing was lost. Those duplicates are what "loc at s88 is standing
somewhere else, and can't be at 2 places at once" was reporting.

Backups, if any of this is wrong: `PRE-RESTORE-2213` (your state before the first restore) and
`PRE-RESTORE-2258` (what the app left after it pruned).

---

<a id="mt-148"></a>

### MT-148 - 2026-08-24 - A page that will not load keeps its setup

**Disposition:** fixed unvalidated
**From:** OB-068
**Written:** 2026-08-24

**What to do.** You are simulating the thing OneDrive does on its own: a page file that is briefly not
readable.

1. Set up two pages so you can tell them apart - name a station on each and mark both as stations.
2. Close TrainControl.
3. **Rename one page's `.cs2` file** in `config/gleisbilder` so it cannot be found - add `.hidden` to
   the end. Leave `gleisbild.cs2` alone: the index still promises the page.
4. Open TrainControl. That page is missing from the list, which is expected.
5. Now do something ordinary that saves - place a locomotive from the diagram, or rename a station on
   the page that DID load.
6. Close TrainControl, **put the file name back**, and reopen.

The hidden page must come back with its station name and its station flag intact. Before this fix they
were gone: pruned as deleted track by the save in step 5, silently.

Then check the index: `config/gleisbild.cs2` must still list that page. It used to be dropped from
there as well, orphaning the file.

#### Comments

**Claude, 2026-08-24.** Found by the independent seven-day review, as ISD-B1, and the highest-value
item on its list.

`CS2File.parseLayout` skips a page whose file will not parse or is not there - deliberately, so one bad
page does not take the layout down - and `readShared` is relaxed about the same absence for the same
reason: "Absent is fine - the page may simply not be loaded." Both halves agreed that a missing page was
survivable. Then `save()` reconciled the setup against the pages that DID load, and everything on the
missing one read as track that had been deleted.

Three of the four doors that reach that save discard the reconciliation report, so it happened without a
word.

The remedy is the one already written for a suspect page numbering, extended: save, but do not prune,
while the picture of the layout is incomplete. `store.pagesNotLoaded` answers that question, and the
test that pins it is `testAutonomyDiagramSession.testAPageThatDidNotLoadKeepsItsSetup` -
mutation-checked by removing the new half of the condition.

Worth knowing for reading the code: with the page absent, its entries stay in memory under RAW ID keys,
because `pageOf` has no name to resolve the id to. That is why they look like squares that do not exist
to anything working in page names - and it is exactly the state in which they must not be deleted.

---

<a id="mt-149"></a>

### MT-149 - 2026-08-24 - The timetable survives renaming a locomotive

**Disposition:** fixed unvalidated
**From:** OB-069
**Written:** 2026-08-24

**What to do.**

1. Capture a timetable with **several** entries, using at least two different locomotives.
2. **Rename one of those locomotives** in the locomotive database.
3. Check the timetable at once: every entry must still be there, and the renamed locomotive's entries
   must show its new name.
4. Close TrainControl and reopen. Check again - this is the half that used to fail.
5. Then **delete** one of the locomotives, and check that only ITS entries have gone.

Before this fix, step 4 lost the **entire** timetable, not just the affected entries - and the next
capture wrote the emptiness back permanently.

#### Comments

**Claude, 2026-08-24.** ISD-B2 from the seven-day review. Two defects, one on top of the other.

The rename repair's own note enumerates "three things in a configuration hold a locomotive by NAME - the
placement, the home assignment and the exclusion list", and all three live in `points`. The captured
timetable does not: it rides in `globals`, and every entry names its locomotive. Nothing repaired it.

That alone would have cost the affected entries. What made it cost everything is the loader: it built
the whole timetable in one loop and let a single entry's exception escape to a catch that discards the
list. One unresolvable name, and every entry went - with one line in the log.

Both are fixed. The repair carries a rename into the timetable and removes the legs of a deleted
locomotive; the loader now drops the one entry it cannot read and keeps the rest, which is what the
locomotive list a few lines below it has always done.

**Not fixed, and it is the same shape:** the entries also name POINTS, so renaming a STATION still
breaks the entries that cross it. That is survivable now rather than fatal - one entry is dropped
instead of the list - but it is a real gap, and `setPointName`'s comment still says "Nothing else has to
happen", which is not true.

---
