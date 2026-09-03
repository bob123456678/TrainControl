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
| [MT-141](#mt-141) | 2026-08-23 | Editing a placement while trains are out puts the others back where they started | fixed unvalidated | NR-1 |
| [MT-149](#mt-149) | 2026-08-24 | The timetable survives renaming a locomotive | fixed unvalidated | OB-069 |
| [MT-165](#mt-165) | 2026-08-24 | Return Home stages a blocker out of the way instead of refusing | fixed unvalidated | OB-073, FBR-B1, FBR-B2 |
| [MT-170](#mt-170) | 2026-08-24 | Backing up a layout that lives on the Central Station | needs test | FR-020 |
| [MT-243](#mt-243) | 2026-08-31 | Upgrading a 2.7.4c autonomy.json keeps its settings and leaves the routes alone | needs test | RGN-A1, IPR-A1 |
| [MT-244](#mt-244) | 2026-08-31 | The Auto tab on a layout that has only an autonomy.json | needs test | RGN-A2 |
| [MT-245](#mt-245) | 2026-08-31 | A home on a main-line platform, and it is still there next start | needs test | DAY-B3 |
| [MT-246](#mt-246) | 2026-08-31 | Signals stay put, and a non-reversing train backs into its home | needs test | OB-166 |
| [MT-247](#mt-247) | 2026-09-01 | A refused route still cuts the power at the two human doors | needs test | FX2-2, SVN-A4 |
| [MT-248](#mt-248) | 2026-09-01 | The length notice and the guard it is meant to arm | needs test | FX2-3, TCX-B2 |
| [MT-249](#mt-249) | 2026-09-01 | A parking berth a train cannot get out of | needs test | FX2-4, RTG-A1 |
| [MT-250](#mt-250) | 2026-09-01 | Homing a train that is already standing on a reversing point | needs test | D24-B1 |
| [MT-251](#mt-251) | 2026-09-02 | The letter keys work the moment the window appears | fixed unvalidated | OB-168 |
| [MT-252](#mt-252) | 2026-09-02 | Placing tile after tile from the palette, over track that is already there | fixed unvalidated | OB-169 |
| [MT-254](#mt-254) | 2026-09-02 | Clearing every home locomotive at once | needs test | R28-C1 |
| [MT-255](#mt-255) | 2026-09-02 | Out of service, on a square that is not a station | fixed unvalidated | D24-B5, SVN-B6 |
| [MT-256](#mt-256) | 2026-09-02 | Switching a signal by hand while a train stands at its platform | fixed unvalidated | SVN-B16, WK3-B1 |
| [MT-257](#mt-257) | 2026-09-02 | Five things the review round wants you to rule on | needs test | RG3, DY3 |
| [MT-258](#mt-258) | 2026-09-02 | Bulk tools, the import's log, and Control+S | needs test | MT-257 |
| [MT-259](#mt-259) | 2026-09-02 | The keyboard, when the window comes to the front | needs test | OB-170 |
| [MT-260](#mt-260) | 2026-09-02 | Six rulings the review rounds are holding | needs test | RTG-B2, TCX-B2, D24-C7, D24-C8, R28-A1, SV2-A1, DY3-C8 |

Everything else - 231 of 258 - is **fixed validated** and needs nothing from you unless the
area changes again.  (8 superseded, 3 fixed but not yet validated.)

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

**Disposition:** fixed validated  
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

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 22:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-081"></a>

### MT-081 - 2026-08-18 - Collect what the old model offered

**Disposition:** fixed validated  
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

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 22:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-082"></a>

### MT-082 - 2026-08-18 - Compare, and scrutinise the NEW-ONLY entries

**Disposition:** fixed validated  
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
**Claude, 2026-08-30.** The comparison has been made, by `docs/tools/parity/`, and here is what it says.

**Step 18b, your known-bad journey: `BottomMainA -> BottomSecondary` IS offered.** Its route runs
through `TopMainR1` without stopping there. But 2.8.1 offers it too, over its own route, so it is a
standing gap in both engines rather than something the derivation introduced - which is the opposite of
what this step expected to find, and worth saying plainly.

**Journeys 3.0.0 offers and 2.8.1 did not**, which is the direction you called dangerous - four, and
[MT-083](#mt-083) is where they get looked at:

| Train stands at | Now offered |
|---|---|
| BottomMainA | BottomInnerOtherside |
| BottomMainC | BottomInnerOtherside |
| BottomInner | LowerBack |
| BottomInner | LowerFront |

**Journeys 2.8.1 offered and 3.0.0 does not** - three, the same three from every station:
`TopMainR0Park`, `TopR1ParkLong`, `TopR1ParkShort`. They are `isReversing` in 2.8.1 and `isTerminus`
in 3.0.0, and autonomy stopped choosing reversing stations on its own in 2.8.0 at your request. So
this is the classification changing rather than capability being lost - the same roads are now reached
as termini, which only a reversible locomotive may enter.

**Concurrency did not regress**: no pair that could run at once in 2.8.1, and still exists, has stopped.

Left as **needs test** rather than answered: the harness collects the lists, and the judgement this
entry actually asks for - is a new journey physically possible - is the part it cannot make.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 22:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-083"></a>

### MT-083 - 2026-08-18 - Run a new-only route in simulation

**Disposition:** fixed validated  
**From:** 2026-08-18 manual test plan, Tier 4 - the routing comparison (the one that matters most)  
**Written:** 2026-08-18

**What to do.** Run a new-only route in simulation. Pick one and execute it. Watch the train: does it do
    anything physically impossible? This is the strongest single test in the plan.

#### Comments

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Claude, 2026-08-30.** The four to pick from, from the parity run - these are the journeys 3.0.0
offers that 2.8.1 did not:

| Train stands at | Route to run |
|---|---|
| BottomMainA | BottomInnerOtherside |
| BottomMainC | BottomInnerOtherside |
| BottomInner | LowerBack |
| BottomInner | LowerFront |

Two of them end at `BottomInnerOtherside` from the two Bottom Main roads, and the other two leave
`BottomInner` for the Lower roads, so running one of each pair covers both shapes.

What to watch for is in MT-082: a reversal at a square where a train cannot reverse, and a change of
track mid-square at a double curve.

**Claude, 2026-08-30.** That closes the parity question.

The four journeys 3.0.0 offers and 2.8.1 did not are the only direction the comparison called
dangerous, and running one is the only way to answer it. With this and the harness together: no
journey lost that was not a deliberate 2.8.0 change, no concurrency pair lost, and the new journeys
are ones the railway can actually make.

---

<a id="mt-084"></a>

### MT-084 - 2026-08-18 - Two trains, shared junction

**Disposition:** fixed validated  
**From:** 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains  
**Written:** 2026-08-18

**What to do.** Two trains, shared junction. Run two trains whose routes cross a junction. They never receive
    conflicting routes through it; the second waits.

#### Comments

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 21:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-31 (triage).** Works.

*Run against commit 302d7a11, build\classes, compiled 31 Aug 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-085"></a>

### MT-085 - 2026-08-18 - Collision refusal

**Disposition:** fixed validated  
**From:** 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains  
**Written:** 2026-08-18

**What to do.** Collision refusal. Try to get autonomy to send a second train to an occupied platform,
    including when the occupant arrived from the other direction (the split-copy case, which was
    broken).

#### Comments

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 22:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Disposition:** fixed validated  
**From:** 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains  
**Written:** 2026-08-18

**What to do.** Long run. Three or four trains for twenty minutes or more. No train silently stops and stays
    stopped; no gradual gridlock; CPU stays low when a train is boxed in.

#### Comments

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-30 (triage).** Does not work.

WORKS FINE in atomic mode.

In non atomic mode, locks aren't getting released.  Example: in the current live setup, EN57-203 is started from BottomSecondary to TopMainR2Inter.  After it passes Tunnel, EN57-947 should be able to go from TopMainR2 to BottomSecondary, but no movement is allowed at all.

*Run against commit c386be96, build\classes, compiled 30 Aug 22:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30.** Reproduced, and it is the one thing non-atomic mode exists to do.

`executePath` gives the EDGE back the moment `tailHasProvablyPassed` says the train is clear of it -
and it kept every LOCK EDGE that edge had taken until the whole path finished. So the rails came free
and the throats did not. On a railway where routes cross, a throat held is a route refused, so every
square the train had been through went on blocking everything that crossed it for the rest of the run.
That is "no movement is allowed at all", and it is why atomic mode looks fine: there, nothing is given
back early anyway.

**The proof is the one the release already stands on.** If the train is clear of the edge, it is clear
of the throat that edge needed, and there is nothing left for the lock to protect. So the early release
now gives up what it took - one release to the edge and one to each of its lock edges, matching the one
the lock took - and the two places in `unlockPath` that used to do that half at the end now skip an
edge that has already been given up, or they would take away a claim this path no longer holds.

Also corrected while there: a comment in `unlockPath` still said "occupancy is still a flag rather than
a count". It stopped being a flag in the round that wrote it.

`core.testAutonomyPathValidation.testNonAtomicGivesBackTheLocksOfTrackThePassedTrainHasCleared` is the
reproduction. **It passed on its first version for the wrong reason** - a two-edge run in simulation is
over in a second, so "the crossing came free" was true of a run that had already finished, which is
also true of atomic mode. It runs four edges now and asserts the crossing comes free *while the run's
own thread is still alive*, which is the only thing that separates the two modes.

1. **Your case, again.** Non-atomic. Start EN57-203 from BottomSecondary to TopMainR2Inter, and once it
   is past Tunnel, send EN57-947 from TopMainR2 to BottomSecondary. It should go.
2. **Atomic mode is unchanged** - nothing is given back until the run ends, which is the point of it.
3. **A long run with several trains**, which is what this entry is really for.

*Run against v3_0_0_rc3 or later.*

**Adam, 2026-08-31 (triage).** Works, with notes.

Works from the autonomy commands panel, but right-clicking on the track diagram does not show available options in non-atomic mode.

*Run against commit c386be96, build\classes, compiled 31 Aug 00:07 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-31.** Thank you - and the note is a different defect, so it has an entry of its own
rather than sitting under one you have just closed. Filed as OB-164.

What I can say about it already: the two surfaces do NOT ask the same question. The commands panel
gates on `layout.isAutoRunning()`, which is about the whole layout; the diagram's right-click menu
gates on `getActiveLocomotives().containsKey(locomotive)`, which is about that one train. Both then
call the same `getPossiblePaths(loc, true)` through the same filter, and neither that method nor
`isPathClear` branches on `atomicRoutes` anywhere - so the difference is in state a non-atomic run
leaves behind, not in the question being asked.

I have not found it yet, and I would rather say so than guess: three plausible mechanisms did not
survive reading. It needs a reproduction with two trains, which is the next thing I will do.

---

<a id="mt-088"></a>

### MT-088 - 2026-08-18 - Path-integrity failure

**Disposition:** fixed validated  
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

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 22:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Disposition:** fixed validated
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

**Adam, 2026-08-24 (triage).** Works.

Use DEBUG_SIMULATE_PACKETS = true to simulate that there is a station, and confirm that way.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-20

**What to do.** A Central Station route. Everything greyed, no marks in any row, nothing typeable, no field
that takes the caret, and Escape closes it.

#### Comments

We tested this synthetically earlier.  I can retest if you changed anything.

**Adam, 2026-08-25 (triage).** Works, with notes.

Works, but below the last command, there is a long greyed out line where the + icon would normally be.  make this be white.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-31 (triage).** Works.

*Run against commit 302d7a11, build\classes, compiled 31 Aug 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Disposition:** fixed validated
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

**Adam, 2026-08-24 (triage).** Works.

*Run against commit b1e22b5b.*

---
<a id="mt-023"></a>

### MT-023 - 2026-08-21 - Two signals on one station

**Disposition:** fixed validated
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

**Adam, 2026-08-24 (triage).** Works.

*Run against commit b1e22b5b.*

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

**Disposition:** fixed validated  
**From:** TR-A22  
**Written:** 2026-08-21

**What to do.** Two trains running, one dispatched onto a long path. TR-A22 in the flesh: while one locomotive
is being sent off over several edges, a train already under way has to reach and stop at its next
sensor normally. What it must NOT do is run past it. Worth doing in simulation first, then for real.

#### Comments

Defer for later once other bugs are fixed.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 22:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-21

**What to do.** Switch the Central Station off, leave TrainControl open, press Stop, then click a switch on the
diagram. It should pause about two seconds, say the power was not confirmed, and throw the switch
anyway - and then the NEXT click should behave the same way rather than doing nothing. Before this,
the first such click stopped every tile in the application from ever responding again.

#### Comments

Defer for later.

**Adam, 2026-08-30 (triage).** Could not run this.

make a simulated test case for this

*Run against commit c386be96, build\classes, compiled 30 Aug 22:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30.** Accepted - a simulated case for this is on the list.

What it needs is a mock station that answers and then stops answering mid-session, which
`support.CS3TestServer` and `core.testMockCentralStation` already have most of; what nothing does yet
is drop the connection while autonomy is running and assert what the application does about it. Left
as **needs test** here because the hands-on question - what the operator sees, and whether Stop and
the power state agree afterwards - is not the same question as the automated one, the same distinction
drawn on MT-088.

**Claude, 2026-08-30 (later).** Done - `core.testTheStationGoingAwayDoesNotJamSwitching`.

Three tests, and the third is the one this entry is really about. The wait for the power gives up on
time and says so; a second wait behaves exactly like the first, so nothing is latched on the way out;
and **the one switching thread is still there afterwards**. That pool has a single thread shared by
every tile on every page, and the click handler waits for the power on it - so a wait that never
returned took every tile in the application with it, silently, until a restart. That is the shape you
described.

The power is put down by writing the flag rather than by pressing Stop: `stop()` sends a stop and waits
for the echo to write it, and an echo is exactly what a station that is not there never sends - so on a
simulated model the flag stays up and every wait would return at once, asking nothing.

Confirmed by mutation: making the wait claim success on timeout fails it.

**Still needs you** for the half no simulation can answer - that the switch is actually thrown, and
that the message names the tile.

**Adam, 2026-08-31 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 31 Aug 00:07 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Disposition:** fixed validated  
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

**Disposition:** fixed validated  
**From:** hands-on testing  
**Written:** 2026-08-22

**What to do.** Run `testAutoDetect` with a Central Station answering on the network. It scans for one at
192.168.50.25 and asserts it finds it, so it is red on any machine that has none - which is not a
defect, and is why it is excluded from the fast battery.

#### Comments

**Claude, 2026-08-22.** Carried over from the "Still untested, and known" list at the bottom of
the old file. It has been red in every run this week for this reason.

**Adam, 2026-08-27 (triage).** Does not work.

it works 9 out of 10 times.  sometimes I see this without a positive detection: 192.168.50.25 is reachable
.......... and then it just goes on.  if I try again, it gets redetected.

*Run against commit 309b984f, build\classes, compiled 27 Aug 18:42 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-27.**

Fixed 2026-08-27. The message Adam quoted - '192.168.50.25 is reachable' - is printed the instant a ping succeeds, so the host answered and was then dropped by the web check that follows. The asymmetry was sitting in CSDetect: isReachable retries PING_RETRY times, checkWebServer got a single 500ms connect-and-read, swallowed the exception and returned false. One transient timeout therefore threw away a station that had just proved it was there, and the scan walked past it - which is exactly the one-in-ten Adam measured, and exactly why retrying the whole detect finds it. WEB_RETRY = 3 now, and the retry lives INSIDE the two-argument checkWebServer that both callers already use, so there is no new call site to remember. Costs nothing on the ~250 dead addresses: the web check only runs on hosts that already answered a ping. Covered by core.testCentralStationDetection, which stands up a real HTTP server that is slow once and fine afterwards; all three mutations bite, including WEB_RETRY=1, which reproduces the original bug deterministically. Still needs Adam's hands: the fix cannot be proved on the real railway from here.

**Adam, 2026-08-28 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 28 Aug 01:24 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Disposition:** fixed validated
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

**Claude, 2026-08-24.** Adam: "I did not see MT-124 in the queue." He was right, and the reason is a
tooling defect rather than anything about this entry.

`triage.py` hides an entry that carries a session mark of `done`. He submitted a verdict on this one
last round, which set that mark - and the mark persists across rounds, because the session state is
local scratch that nothing clears. So once he had judged it, it never came back in front of him, no
matter what happened to it afterwards. That applies to every entry he has ever submitted on, not just
this one.

Fixed in `triage.py`: a mark is ignored when the entry is **reopened**, which is exactly "he judged it
and something has changed since". Within a session nothing changes - an entry he has just submitted has
no newer comment yet, so the mark still hides it, which is what the mark is for.

**On the finding itself: it was already addressed.** His note was "on a curve, the badge is perfectly
over the sensor, but we decided to place those curved station dots offset on the other side when in the
autonomy editor. Move the * so it aligns with the offset placement." The star follows `badgeDrawnAt` -
the position the badge actually landed at, recorded rather than recomputed, because that placement is
three rules deep and a second copy of the arithmetic would be a second chance to disagree. His words
are quoted at the code site.

So this wants his eyes rather than work: a curved station with a train on it, in the editor and in the
viewer.

**Adam, 2026-08-24 (triage).** Works.

Previous: On a curve, the badge is perfectly over the sensor, but we decided to place those curved station dots offset on the other side when in the autonomy editor.  Move the * so it aligns with the offset placement.

Now:
Looks good!

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

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

**Disposition:** fixed validated  
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

**Adam, 2026-08-24 (triage).** Works.

1. works, but because the track diagram right click control is rightly disabled
2. works
3. write a test case for this

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-135"></a>

### MT-135 - 2026-08-23 - Renaming a page keeps its autonomy setup

**Disposition:** fixed validated  
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

**Adam, 2026-08-24 (triage).** Does not work.

Renaming the page results in the following errors on the new page:    TopMainR2Inter holds a locomotive that is also recorded as standing somewhere else.  A locomotive can only be in one place, and autonomy refuses the whole setup while it is in two - so take it off whichever square it is not on.
   (Page 1 - Main) 6,4 holds a locomotive that is also recorded as standing somewhere else.  A locomotive can only be in one place, and autonomy refuses the whole setup while it is in two - so take it off whichever square it is not on.
   (Page 1 - Main) 14,3 holds a locomotive that is also recorded as standing somewhere else.  A locomotive can only be in one place, and autonomy refuses the whole setup while it is in two - so take it off whichever square it is not on.
   (Page 1 - Main) 13,11 holds a locomotive that is also recorded as standing somewhere else.  A locomotive can only be in one place, and autonomy refuses the whole setup while it is in two - so take it off whichever square it is not on.


Then, renaming it back adds the following:

   TunnelPre holds a locomotive that is also recorded as standing somewhere else.  A locomotive can only be in one place, and autonomy refuses the whole setup while it is in two - so take it off whichever square it is not on.
   BottomInnerOtherside holds a locomotive that is also recorded as standing somewhere else.  A locomotive can only be in one place, and autonomy refuses the whole setup while it is in two - so take it off whichever square it is not on.
   s88 1009 holds a locomotive that is also recorded as standing somewhere else.  A locomotive can only be in one place, and autonomy refuses the whole setup while it is in two - so take it off whichever square it is not on.
   (Page 1 - Main2) 0,11 holds a locomotive that is also recorded as standing somewhere else.  A locomotive can only be in one place, and autonomy refuses the whole setup while it is in two - so take it off whichever square it is not on.


So it looks like there is some station loss and locomotive mapping loss too.

**Claude, 2026-08-24.** Found and fixed, and it is one cause for MT-135, MT-174 and half of MT-171.

**The rename was never the problem. What runs after it was.** `LayoutPageEdit.renameOrDuplicate`
rekeys the store and writes it, and that part is correct - the test written for MT-174 passes and
passed while this was happening. What the window does NEXT is `layoutEditingComplete`, which re-reads
the diagrams and then calls `resetAutonomySession`, and that captures the running Layout's state back
into the configuration before letting the session go.

`captureFromLayout` works its tile keys out from the SESSION'S naming - the graph, the reducer and the
page objects it was opened with. A rename does not touch any of those. `LayoutDiagram.saveChanges`
writes a new file; it never renames the object, and the graph was derived before the rename happened.
So the store said `1 - Main2` and everything derived from the pages still said `1 - Main`, and the
capture wrote every placement back a second time under the old name, beside the correctly renamed one.

A locomotive in two places fails the whole setup, which is what Adam saw:

> TopMainR2Inter holds a locomotive that is also recorded as standing somewhere else. A locomotive can
> only be in one place, and autonomy refuses the whole setup while it is in two.

Renaming back did the same thing under the other name, which is why undoing did not undo it - his
second list of errors names `1 - Main2` squares where the first named `1 - Main` ones.

**Reproduced on the sample layout, on the same squares he reported**: four locomotives each in two
places, at 6,4 and 14,3 and 13,11. Errors went from 79 to 83, all four new ones
`checkDuplicateLocomotive`.

**Fixed** by having the rename say what it has done - `session.markPagesStale()` - and having
`captureFromLayout` refuse while that is true. Refused rather than repaired, and nothing is lost:
the rename has already written the store, and a rename is refused while autonomy is running, so there
is nothing the running layout knows that the store does not. The session is discarded and rebuilt from
the renamed pages moments later in any case.

**Why the MT-174 test missed it**, since that test was written two hours earlier for exactly this
subject. Two reasons, either sufficient. It stopped at the rename and never ran the capture that
follows. And it compared stored settings without ever running the CHECKS - and this damage does not
look like data going missing, it looks like twice as much data, which no equality on the settings I
was comparing would notice. What the operator meets is not a missing station; it is the check refusing
to build. The new test asserts on the checks.

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-136"></a>

### MT-136 - 2026-08-23 - Two more of one shape, from the history review

**Disposition:** fixed validated  
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

**Adam, 2026-08-24 (triage).** Could not run this.

1. is OK.

BUT In the track diagram viewer: There is a condition where "place {locname}" is greyed out saying it can't possible leave so can be placed, but Place Locomotive still works.

2/3 are OK but I still need to test autonomy over a link.
----
Update: I don't understand the steps to reproduce.

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-31 (triage).** Works.

*Run against commit 302d7a11, build\classes, compiled 31 Aug 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-137"></a>

### MT-137 - 2026-08-23 - The round of bugs from Adam's last test pass

**Disposition:** fixed validated  
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

**Adam, 2026-08-24 (triage).** Works.

segment lengths are OK
fix it start greyout does not work (see other bug)
try not to bunch multiple bugs into a MT, this will reduce my accuracy.

diagram edit that costs the station- in the current state, I can't validate, but I see no error.

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-138"></a>
### MT-138 - 2026-08-23 - A split copy's name and its facing disagree on a curve

**Disposition:** superseded
**From:** UR-16

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

**Disposition:** fixed validated
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

**Claude, 2026-08-24.** Adam, 2026-08-24: **works**. A hand-dispatched train is a run, with the same guards and the same signalling as autonomy.

---

<a id="mt-140"></a>
### MT-140 - 2026-08-23 - Bless the baseline once you are happy with the railway

**Disposition:** fixed validated
**From:** Adam's request to capture a confirmed-good baseline

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

**Claude, 2026-08-24.** Disposition corrected from "needs decision" to **needs test**.

"needs decision" is not one of the four words README documents, and I introduced it - on this entry and
two others - without adding it to the vocabulary. `verify-ledger` now checks the disposition against
that list and was flagging this one every run.

"needs test" already covers it: its definition is "nobody has run it since it was written, or it was
run and deferred, or it is waiting on something that does not exist yet", and this entry is waiting on
Adam. A fifth word would have to earn its place by saying something the four cannot, and this one does
not.

**Adam, 2026-08-26 (triage).** Works, with notes.

You now have the materials- use the current page 1.

*Run against commit 309b984f, build\classes, compiled 26 Aug 00:29 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-31 (triage).** Works.

*Run against commit 302d7a11, build\classes, compiled 31 Aug 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-141"></a>
### MT-141 - 2026-08-23 - Editing a placement while trains are out puts the others back where they started

**Disposition:** fixed unvalidated
**From:** NR-1

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

**Adam, 2026-08-24 (triage).** Could not run this.

the requirements are too long.  I can't cleanly view the testing requirements for this in the triage app.  Need to mark what tests needs to be run cleanly, and support syntax like bullet lists.

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-142"></a>

### MT-142 - 2026-08-24 - A page keeps its identity when other pages change

**Disposition:** fixed validated
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

**Adam, 2026-08-24 (triage).** Works, with notes.

This seems to work, but add a thorough test case for it since you already know what should happen.

**Claude, 2026-08-24.** There is a thorough one: `testPageIdentitySurvivesASequenceOfOperations`, in
`test/regression/testPageIdsAreDurable.java`, which is the class this entry is about - twelve tests
between them, covering a retired id not being handed out two writes later, a save while a page is
absent, an index in the platform encoding, and renaming a page to another page's id.

The entry named the class but not that test, which is why the request still looked open.

*Run against commit b1e22b5b.*

**Claude, 2026-08-24.** Adam: **works with notes** - "This seems to work, but add a thorough test
case for it since you already know what should happen."

Added: `testPageIdentitySurvivesASequenceOfOperations`. The existing tests each isolate one operation;
this one does what a person does - rename, add, delete, add again - and after EVERY step asserts that
the two pages the sequence is not about still carry their names, stations, lengths and placements, and
that their ids have not moved.

Written as a sequence deliberately. None of this month's defects were in a single operation: they were
in what one operation did to a page it was not about - a rename renumbering others, a delete handing a
retired id to a newcomer, a page that failed to load pruned by a save meant for something else. A
sequence is the only shape that catches those.

---

<a id="mt-143"></a>

### MT-143 - 2026-08-24 - Four small ones from the same test pass

**Disposition:** fixed validated
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

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 24 Aug 01:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Adam, 2026-08-24: **works**. All four - the page-link menu, the grid on the editor spacers, the grid toggle in the autonomy editor, and Start Autonomy refusing while the setup has errors.

---

<a id="mt-144"></a>

### MT-144 - 2026-08-24 - The editor window comes forward, and the info mark is an icon

**Disposition:** fixed validated
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

**Adam, 2026-08-24 (triage).** Works, with notes.

Works, but let's also make it clickable and show the notes in a popup with a scrollable text area with the whole list of stations.  Order them by ones that can be chosen autonomously and ones that cannot, with the autonomous ones first.

**Claude, 2026-08-24.** Adam asked whether this was already done. It is - all of it - and it was in the
build he tested, so this is a note about discoverability rather than a missing feature. Checked
against the code rather than remembered, because "already done" has been wrong twice tonight:

- **Clickable.** `locDest` carries the icon and its `mouseClicked` calls `showWhyNot()` whenever the
  panel is in the no-paths state.
- **A hand cursor** appears on hover in that state, which is the affordance saying it can be pressed.
- **A popup with a scrollable text area.** `showWhyNot` builds a `JTextPane` inside a `JScrollPane`
  sized 580 by 380.
- **The whole list.** It reports `explainDestinationsGrouped`, where the HOVER version deliberately
  stops at twelve stations and an ellipsis - the code comment beside it says the interesting station is
  often below that line, which is very likely what prompted this note.
- **Autonomous ones first.** `whyHeaderCandidates` is appended before `whyHeaderBarred`.

It landed at 04:58 on 24 August; the build tested here was compiled from a 14:12 commit. So the likely
story is that the hover tooltip was read, the truncation noticed, and the icon never clicked.

No code change. If the intent was that the popup should be easier to FIND - a hint in the tooltip
saying it can be clicked, say - that is a different and reasonable request; say so and it is a small
change.

*Run against commit 62af99e6, build\classes, compiled 24 Aug 01:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Adam: **works with notes** - "Works, but let's also make it clickable and
show the notes in a popup with a scrollable text area with the whole list of stations. Order them by
ones that can be chosen autonomously and ones that cannot, with the autonomous ones first."

The fix is validated, so this closes; the note is a new piece of work rather than a defect in it, and
is filed as **FR-017**. Today the reasons appear only on hover, one tooltip's worth, which is the
limitation he is describing rather than a fault.

---

<a id="mt-145"></a>

### MT-145 - 2026-08-24 - A locomotive rename reaches a setup nothing has open

**Disposition:** fixed validated
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

**Adam, 2026-08-24 (triage).** Works.

Filed from this test: OB-081 (bug - renaming locomotives while autonomy is loaded).  They are in `issues.md` until they are picked up.

*Run against commit 62af99e6, build\classes, compiled 24 Aug 01:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Adam, 2026-08-24: **works**. A locomotive rename reaches a setup nothing has open.

---

<a id="mt-146"></a>

### MT-146 - 2026-08-24 - A train may leave the point that holds a station back

**Disposition:** fixed validated
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

**Adam, 2026-08-24 (triage).** Could not run this.

Make a test case for this since you already list the steps.  Reopen only if test case fails.

**Claude, 2026-08-24.** Covered, and it does not fail. `test/regression/testStationBlockedByAnotherPoint.java`
carries sixteen tests on this rule, including the three that matter for the steps listed here:
`testATrainStandingOnTheWatchedPointHoldsTheStationBack`,
`testATrainLeavingTheWatchedPointMayStillBeSentThere` and
`testTheExemptionIsOnlyForTheTrainThatIsLeaving`.

Per the instruction, not reopened.

*Run against commit 62af99e6, build\classes, compiled 24 Aug 01:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Adam: "Make a test case for this since you already list the steps. Reopen
only if test case fails."

The tests were already written when the exemption was - three in
`testStationBlockedByAnotherPoint`: the train leaving the watched point may be sent to the station it
holds back, somebody ELSE standing there still blocks it, and leaving the other copy of a split square
is exempt too. The middle one is the mutation check: `standing == null || standing.equals(loc)`
collapses to `true` if the second clause is ever right about the wrong locomotive.

All three pass. Closed on that, per his instruction.

---

<a id="mt-147"></a>

### MT-147 - 2026-08-24 - The restored layout is the one you had

**Disposition:** fixed validated
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

**Adam, 2026-08-24 (triage).** Works.

all looks good.

*Run against commit 62af99e6, build\classes, compiled 24 Aug 01:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Adam, 2026-08-24: **works** - "all looks good." The restored railway is his, verified page by page. That was the entry everything else was gated on.

---

<a id="mt-148"></a>

### MT-148 - 2026-08-24 - A page that will not load keeps its setup

**Disposition:** fixed validated
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

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 62af99e6, build\classes, compiled 24 Aug 01:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Adam, 2026-08-24: **works**. A page that will not load keeps its setup.

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

**Adam, 2026-08-24 (triage).** Does not work.

Was not unable to test this directly because "capture locomotive commands" is capturing neither manual locomotive commands nor full autonomy commands into the timetable.  Regression.  Fix and then reopen this bug to test the original intent.

*Run against commit 62af99e6, build\classes, compiled 24 Aug 01:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-24 (triage).** Does not work.

Was not able to test this directly because "capture locomotive commands" is capturing neither manual
locomotive commands nor full autonomy commands into the timetable. Regression. Fix and then reopen this
bug to test the original intent.

**Claude, 2026-08-24.** Found, fixed, and it was not where either of us would have looked.

The engine was never at fault. Probing `addTimetableEntry` directly captured a dispatch first time, with
capture on - so the capture path, including the change made for this entry, works.

What was broken is that the operator's CHOICE did not survive. The capture flag lives on the `Layout`
object, because that is where capture happens. `MarklinControlStation.parseAuto` replaces that object
wholesale and carries nothing across, so every rebuild started a fresh Layout with capture **off** -
while the toggle button, which is not repainted at that moment, stayed lit. A rebuild happens far more
often than it reads: applying a diagram edit, placing a locomotive and loading a configuration all
arrive there. So he pressed a button, did something ordinary, and recorded nothing for the rest of the
session with no indication why.

`parseAuto` now carries the flag across. Everything else it replaces is a property of the configuration
and is rightly replaced with it; this is a button somebody pressed a moment ago.

**And the test that should have existed.** Adam: "I am surprised we don't have a timetable capture test
yet." There was none at all. `testTimetableCapture` has three: a dispatch is captured with capture on, a
dispatch is NOT captured with it off - the mutation check, without which the first would pass whatever
the code did - and the flag survives a rebuild, which is the one that would have caught this and is
about a piece of state surviving something that looks unrelated to it. Mutation-checked.

**This entry stays open**, per his instruction: the original intent - that the timetable survives
renaming a locomotive - still needs testing now that capture works.

**Claude, 2026-08-24.** Found, and it is one cause for two of Adam's bug reports.

**The capture was never broken.** A real autonomy run, with capture on, puts entries in the timetable
and always has - `testTimetableCaptureThroughARealRun` now runs the sanity configuration through
`parseAuto`, switches capture on where the window switches it, and lets autonomy pick its own paths;
nine entries landed the first time I probed it. What had stopped was the REDRAW. The timetable is a
description of path starts and ends and of nothing else, so it repaints when the layout announces one
and at no other time.

**The announcement was deleted with the graph window.** `d8db4879` - "The graph window is gone, and
GraphStream with it" - removed the method that built the GraphStream view. Inside it, four lines
registered a `Layout` callback named `GraphCallback` which fired at the beginning and end of every
path and called `repaintTimetable()` and `repaintAutoLocListLite()`. That window wanted the
notification for its own drawing and had registered it on everyone's behalf. Deleting the window took
the registration with it. Nothing failed to compile and no test went red; two panels simply stopped
being true.

**Which also explains [OB-097](issues.md#ob-097)** - "a route finished, but the loc status panel under
Locomotive Commands still indicated an active route". Same deleted callback, the other of its two
calls, reported three days later as an unrelated bug.

**Fixed** by giving the registration a name of its own -
`AutonomyRefreshCallback.attach(layout, onPathEvent)` - and calling it from the window after every
`parseAuto`, which is where the diagram monitor is already re-bound for exactly the same reason:
callbacks live on the Layout object and `parseAuto` replaces that object. The work is posted to the
event thread rather than done in the callback, because a layout fires these from the thread driving
the trains while holding a lock, and taking a UI lock there is the DR-B7 deadlock.

**Why the existing test was green throughout.** `testTimetableCapture` builds its own two-point Layout
with `Layout.fromJSON`, sets the flag on that object, hands `executePath` a path it assembled itself,
and asks whether an entry appeared. Every one of those differs from what the application does, and the
one that matters is that it can only see the model - so it could not have seen this if it had been
written perfectly. It is kept: it is a good, fast unit test of the flag. It just cannot stand in for a
real run. [MT-175](#mt-175).

**Adam, 2026-08-24 (triage).** Does not work.

Was not unable to test this directly because "capture locomotive commands" is capturing neither manual locomotive commands nor full autonomy commands into the timetable.  Regression.  Fix and then reopen this bug to test the original intent.

still an issue after testing- nothing gets captured.

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-31 (triage).** Does not work.

Fails.  Critical: On Rename, loc vanishes from the station / autonomy setup, and the timetable is not updated.  Loc panels show ??? status.

*Run against commit 302d7a11, build\classes, compiled 31 Aug 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-31.** One of the three is fixed and proven; the other two I have not reproduced, and
I would rather say that than guess.

**The timetable is fixed, and the data was never the problem.** A probe renamed a locomotive with a
timetable entry naming it and printed this:

    timetable hash before: -1733089858   name: PR before
    timetable hash after:  -1733089858   name: PR after

The entry FOLLOWED the rename - a locomotive is renamed in place and the entry holds the object - and
the placement survived, and `getLocomotiveLocation` still found it. What did not change is the number
`repaintTimetable` keys its redraw on. `MarklinLocomotive` hashes by IDENTITY, deliberately, because
that is what stops a rename moving a locomotive out of the consists and run lists holding it - and
`TimetablePath.hashCode` is built from it. So every row's TEXT changed and no hash did, the repaint
returned at its first line, and the table went on naming a locomotive that no longer exists.

Two things were wrong and both are fixed: the guard is keyed on the text of the rows now, and the
rename repair asks for a timetable repaint at all, which it never did.

**What I have NOT reproduced** is the locomotive vanishing from the station, or the panels reading
"?????". The probe says the model is right on both counts straight after a rename - the placement is
where it was, and the object is the same one - so either it happens on a path the probe does not take,
or it is a redraw on a surface the repair already refreshes.

**One question, and it settles which.** Does the locomotive come back if you switch pages, or reopen
the autonomy editor, or restart TrainControl? If it comes back, everything left is drawing. If it is
still gone after a restart, the setup on disk was not repaired - which is a different fault with a
different fix, and one the tests would have caught, so I would want to know that before looking
anywhere else.

Stays **fixed unvalidated** rather than validated: one symptom of three.

1. **Rename a locomotive that appears in a captured timetable.** The table should show the new name at
   once.
2. **Then the two I could not reproduce** - watch whether it leaves the station label and whether the
   panel reads "?????", and note whether either comes back on a page switch or a restart.

*Run against v3_0_0_rc3 or later.*

**Claude, 2026-08-31 (second round).** Found it, and you were right that it is not cosmetic.

**`sanitizeMultiUnits` evicts the locomotive it is asked about.** It walks every Point and clears any
holding a locomotive that is not `isSimultaneousMultiUnitCompatible` with the one passed in - and that
method ends `return !this.hasEquivalentAddress(l)`. A locomotive compared with ITSELF has an equivalent
address, so it is declared incompatible with itself, and the sweep takes it off the square it is
standing on.

Both rename doors call that sweep immediately after `renameLoc`. So the rename repairs the setup
correctly, and then the sweep takes the train off the railway - and renaming back does not bring it
back because nothing restores a placement: the second rename finds nothing left to evict. The "?????"
is the same fact from the other end, `getLocomotiveLocation` returning null.

**Why it has been there unnoticed:** it bites on rename and not on placement, because `moveLocomotive`
calls the same sweep BEFORE putting the locomotive down, so there is nothing of its own to find.

A locomotive cannot conflict with itself, and the sweep skips it now.
`core.testALocomotiveDoesNotEvictItself` holds both halves - the sweep on its own, and the rename
followed by the sweep in the order the window does them.

The timetable half from the round before stands: that was the redraw, and this is why the placement
itself was gone.

1. **Your steps exactly.** Place a locomotive on a station, rename it. It should stay where it is, on
   the diagram and in the panel.
2. **Rename it back**, and it should still be there.
3. **A locomotive that IS in a multi-unit with another** - rename one of them and check the sweep still
   does its job on the other.

*Run against v3_0_0_rc4 or later.*

---

<a id="mt-150"></a>

### MT-150 - 2026-08-24 - A page name with a colon keeps its own setup

**Disposition:** fixed validated
**From:** OB-071
**Written:** 2026-08-24

**What to do.** Rename a page so its name contains a colon - "Yard: Upper" is the example the code
uses. Name a station on it, mark it a station, give it a length.

Then rename a DIFFERENT page - any other one. Check "Yard: Upper" still has its station, its name and
its length, before and after closing and reopening TrainControl.

#### Comments

**Claude, 2026-08-24.** Every square on a page whose name contained a colon was stored under the id belonging to a
different page, because `toStored` and `fromStored` split the key on the FIRST colon while
`parseTileKey`, `isOnPage` and `rekeyOne` all split on the last. So renaming an unrelated page orphaned
this one's whole setup - MT-135-class loss, triggered by touching something else.

Those two were the last sites still splitting on the first colon; the other three were fixed when the
hazard was found and nobody came back for these. Covered by
`testAPageNameContainingAColonKeepsItsOwnSetup`, mutation-checked.

**Adam, 2026-08-24 (triage).** Could not run this.

can't test colons in names because the UI disallows colons.  test in a test case instead.

**Claude, 2026-08-24.** Already done, and this entry already said so a few lines above - the test is
`testAPageNameContainingAColonKeepsItsOwnSetup`, in `test/core/testAutonomyDiagramStore.java`, and it
was mutation-checked when written.

Worth keeping the observation, though: the UI disallowing colons does not make the rule untestable, it
makes it untestable BY HAND. A page name with a colon can still arrive from a layout authored
elsewhere or an older file, which is why the code has to keep splitting on the last one.

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-151"></a>

**Claude, 2026-08-24.** Marked **fixed validated** on Adam\u2019s own instruction rather than on a run of
his: he could not test this by hand and asked for a test case instead, the test exists and passes, and
leaving it at "fixed unvalidated" was asking him again for something he had already decided how to
settle. He noticed: "things like MT-150 not verified as fixed even though I added a comment earlier."

The named test is in the comment above. If it ever fails, this goes back to fixed unvalidated and the
entry reopens - which is the whole reason the test is named rather than merely promised.


### MT-151 - 2026-08-24 - A legacy import leaves one home per locomotive

**Disposition:** fixed validated
**From:** OB-075
**Written:** 2026-08-24

**What to do.** You need an old `autonomy.json` in which one locomotive is named as the home of TWO
stations - which was legal before the one-home rule.

Import it. Exactly one of those stations should end up as that locomotive's home, and the import report
should say that a duplicate was cleared.

#### Comments

**Claude, 2026-08-24.** `setHome` sweeps duplicates and its comment names the reason - "a rule enforced at one door of two
is the shape this defect came from" (TD-8). The import is that second door: it writes "home" straight
into the configuration and never goes near `setHome`.

Both were imported; `rebuildHomeStations` then dropped one by iteration order with a log line, and the
next capture wrote that arbitrary choice back permanently - so the user kept a home they never chose,
with nothing to say which had been theirs.

Done the way the same method already handles PLACEMENTS: a set beside `placedAlready`, first one wins,
and the rest counted so the choice is reported rather than made silently.

**Adam, 2026-08-24 (triage).** Could not run this.

make a test case for this using a generated json file

**Claude, 2026-08-24.** There is one already: `testAnImportLeavesOneHomePerLocomotive`, in
`test/core/testAutonomyDiagramSession.java`, and it does exactly what this asks - it builds a legacy
`autonomy.json` in which one locomotive is named as the home of two stations, imports it, and checks
both halves of the rule.

It asserts that exactly ONE of the two squares comes out as that locomotive's home, and separately
that `duplicateHomes` counted the one it dropped - because a choice made on the user's behalf that is
not reported is the part of this that was actually wrong. The entry never named it, which is why this
still read as outstanding.

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-152"></a>

**Claude, 2026-08-24.** Marked **fixed validated** on Adam\u2019s own instruction rather than on a run of
his: he could not test this by hand and asked for a test case instead, the test exists and passes, and
leaving it at "fixed unvalidated" was asking him again for something he had already decided how to
settle. He noticed: "things like MT-150 not verified as fixed even though I added a comment earlier."

The named test is in the comment above. If it ever fails, this goes back to fixed unvalidated and the
entry reopens - which is the whole reason the test is named rather than merely promised.


### MT-152 - 2026-08-24 - Deleting a point clears it out of everything that watched it

**Disposition:** fixed validated
**From:** OB-080
**Written:** 2026-08-24

**What to do.** Hold one station back with another (the "unavailable while occupied" menu), then
delete the watched square from the diagram and save.

**Claude, 2026-08-24.** There are two already, both in `test/regression/testStationBlockedByAnotherPoint.java`:

- `testDeletingAPointClearsItFromEveryBlockedByList` - the rule this entry is about.
- `testDeletingTheWatchedSquareDropsTheRestriction` - the same thing from the other end, checking the
  station it was holding back is freely available again.

`testTheRestrictionSurvivesTheFile` covers the half the entry calls out as the one that used to
differ, a save and a reload. The entry never named any of them.

The station it was holding back must be freely available again - and must still be after closing and
reopening TrainControl, which is the half that used to differ.

#### Comments

**Claude, 2026-08-24.** `deletePoint` released the home claim on a deleted station and nothing else. `blockedBy` holds
Points, so a deleted station stayed in the lists of every station it held back - a ghost blocker on a
point nobody can see or clear.

It fails closed, which is why it was never reported: a station that will not be chosen is quieter than
one chosen wrongly. And it vanishes across a save and load, because the list is written by name and the
name then resolves to nothing - so the symptom was a railway that behaved differently after a restart.

The test for it found a bug in the fix on its first run: `getBlockedBy` hands back an unmodifiable
view, so `removeIf` threw.

**Also in this fix, and not separately testable:** `validatePathActuation` returned TRUE - "everything
actuated" - when interrupted, without looking. That is the answer that lets a train onto turnouts
nothing confirmed. It asks `allConfirmed` now. Unreachable today; fixed because a fail-safe pointing
the wrong way is a thing you find out about once.

**Adam, 2026-08-24 (triage).** Could not run this.

write a test case for this

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-153"></a>

**Claude, 2026-08-24.** Marked **fixed validated** on Adam\u2019s own instruction rather than on a run of
his: he could not test this by hand and asked for a test case instead, the test exists and passes, and
leaving it at "fixed unvalidated" was asking him again for something he had already decided how to
settle. He noticed: "things like MT-150 not verified as fixed even though I added a comment earlier."

The named test is in the comment above. If it ever fails, this goes back to fixed unvalidated and the
entry reopens - which is the whole reason the test is named rather than merely promised.


### MT-153 - 2026-08-24 - A locomotive rename reaches the diagram labels

**Disposition:** fixed validated
**From:** OB-081, OB-074
**Written:** 2026-08-24

**What to do.** With autonomy loaded and a locomotive standing at a station, **rename that
locomotive**. The label beside it on the track diagram must change at once, without touching anything
else.

Do it twice: once from the locomotive edit dialog, and once by accepting a rename the Central Station
proposes (Tools > check for renamed locomotives).

Then, for OB-074: if your Central Station offers a name containing brackets or a comma - "SBB 460 (2)"
is the shape - accepting it must be REFUSED with a message, not applied.

#### Comments

**Claude, 2026-08-24.** The refresh after a rename redrew the locomotive buttons, the mappings, the route list, the
selector and the layout's callbacks - and not the labels beside the stations, which are written by
`updateStationLabels`. Every other door that changes which locomotive stands where already called it,
each carrying the same note: "The label still says the locomotive's name until something rewrites it."

The Central Station path is a near-copy of the dialog's, so it had the same gap - and it also applied
the proposed name with no `isNameUsable` check, while `RouteCommand`'s own rule says "three separate
doors have to agree". `renameLoc` writes the new name into every route, so accepting a proposal could
rewrite every route naming that locomotive into something that does not parse.

The guard reads EVERY `renameLoc` call site rather than the first, because a rule that stopped at the
first match would have reported the pair as covered after fixing one.

**Adam, 2026-08-24 (triage).** Does not work.

I renamed MY 1106 to MY Y1106.  It vanished from autonomy, with MY 1106 still placed and at location ???? in the UI.

**Claude, 2026-08-24.** Fixed, and the data was never the broken half.

I drove a real locomotive rename through a real setup - the sample layout has MY 1106 placed on it,
which made this straightforward. The placement follows the new name, survives a save and a reload,
and builds into a valid layout with no new errors. `repairAutonomyLocomotive` does its job.

What it does not do is tell anything to redraw. The station labels on the diagram are written by
`updateVisiblePoints` and the locomotive panel by `repaintAutoLocListLite`, and neither runs on its
own - they run when something calls them. Nothing did. So the diagram went on showing MY 1106 over a
locomotive that no longer answers to that name, and the panel went on showing a location it could no
longer resolve. That is "still placed" and "location ????" exactly.

The rename now posts both refreshes. Posted rather than called: this runs on the event thread inside
a rename dialog, and `updateVisiblePoints` is synchronized on the window and reaches into the layout,
which is the DR-B7 deadlock shape if both locks are taken from here.

**Three of these now** - the timetable that captured perfectly into a table nobody repainted, OB-097,
and this - all from the same habit of repairing state and stopping there.
`testTheWindowAttachesItsRefreshCallback` now guards this site too.

**And a note on that guard, because it nearly did not work.** Its first version asked whether the
method body contained the string "updateVisiblePoints" anywhere. It did - in the comment I had just
written explaining why the call was there - so deleting the call left the test green. It reads the
code with comments stripped now, and looks for the call with its parentheses. Caught only because it
was mutation-checked before being trusted.

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-154"></a>

### MT-154 - 2026-08-24 - The editor's Cancel cannot revert work done elsewhere

**Disposition:** fixed validated
**From:** OB-076
**Written:** 2026-08-24

**What to do.** Open the layout editor. Now, in the MAIN window, try to right-click a square for the
autonomy menu and try Ctrl+X / Ctrl+V over a station.

Both should decline - the menu should not appear, and the keys should do nothing but say why in the
log. Close the editor and check both work again.

#### Comments

**Claude, 2026-08-24.** The editor's Cancel restores the setup as it was when that window opened, and saves it. The main
window's tile menu and keyboard placements stayed live while the editor was open, and their edits are
not in that snapshot - so naming a station from the diagram and then pressing Cancel in a window about
something else silently reverted the name and wrote the reversion to disk.

Refused rather than repaired. The snapshot IS repaired for locomotive renames; doing the same for every
setup edit would mean keeping two live editors in step, which is the trap that made the editors
mutually exclusive in the first place.

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-155"></a>

### MT-155 - 2026-08-24 - Closing the application asks about unsaved editor work

**Disposition:** fixed validated
**From:** OB-070
**Written:** 2026-08-24

**What to do.** Open the editor, change something, and close the MAIN window without saving.

You should get the same Save / Discard / Cancel question every other way out of the editor gives.
Cancel must leave the application open. Check Save and Discard each do what they say.

#### Comments

**Claude, 2026-08-24.** "One save/discard/cancel question, asked wherever a page is left" was enforced at every door out of
the editor except the biggest: `WindowClosed` never consulted it and went on to `System.exit`.

In autonomy mode it was worse than a silent discard - the exit capture SAVES the setup, so work the
user was about to Cancel could be committed on the way out.

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-156"></a>

### MT-156 - 2026-08-24 - A timetable run that fails says so

**Disposition:** fixed validated
**From:** OB-072
**Written:** 2026-08-24

**What to do.** Start a timetable and make one of its legs fail - the simplest way is to put another
train in the way of a later entry so its path cannot be configured.

Every train should stop, AND you should get the "stopped at entry N" dialog. Before this fix the trains
stopped and the run reported success.

#### Comments

**Claude, 2026-08-24.** The dispatcher's `catch (Throwable)` stopped every train and never set `abandoned`, so
`return !abandoned.get()` answered true - reproducing the exact symptom the comment beside that dialog
says was fixed. A leg that threw is the clearest abandonment there is.

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-157"></a>

### MT-157 - 2026-08-24 - Return Home refuses a plan it cannot carry out

**Disposition:** superseded
**From:** OB-073
**Written:** 2026-08-24

**What to do.** Hold one locomotive's home station back by another square (the "unavailable while
occupied" menu), and stand a DIFFERENT locomotive on that square.

Press Return Home. It must refuse up front, not start moving trains and then give up.

#### Comments

**Claude, 2026-08-24.** FR-001 holds a station back while another named square is occupied, and `isPathClear` enforces it
on a path's destination - which is every move staging makes. The planner never read `getBlockedBy`, so
the plan reported READY, execution refused the leg, the run retried until it gave up, and it stopped
everything with the fleet half-staged.

It fails safe - no train moves wrongly - but partial execution is the thing staging exists to avoid.

Mutation-checking showed the impossibility SCAN is what catches this case; the search-side check covers
a blocker arriving partway through a multi-move plan, which the test does not reach. Recorded because
the first mutation run would have supported the wrong conclusion.

**Claude, 2026-08-24 - and that paragraph above was wrong, twice over.** Two review passes took it
apart, and the correction matters more than the original entry.

The scan it praises is the impossibility scan, whose whole job is to prove that "no move can ever end
there". Putting an occupancy test in it was the mistake: staging can move whatever is standing in the
way, and `connected` - four lines below it - says so outright: "A route blocked merely by another train
is not impossible, moving that train is exactly what the planner is for."

So the planner reported IMPOSSIBLE, named the locomotive as blocked, and skipped the search for
arrangements the search solves in three moves. It was narrowed once, to blockers "staging will never
move" (FBR-B1), and that was wrong too: `astar` moves locomotives off their own homes freely, and a
hand-placed locomotive gets a positional home where it stands, so the narrowed rule still fired on the
ordinary case (FBR-B2). The occupancy test is gone from the scan entirely.

What OB-073 was actually about survives untouched, and it is the check inside `firstClearRoute`, which
is asked of the evolving state as the search takes moves. That is what makes the plan executable, which
is the property the automated test now asserts by replaying the plan move by move.

The mutation note above is also why this went unnoticed: it concluded the scan was doing the work
because reverting the scan alone turned the test red. It did - but only because the test asserted "not
READY", which cannot tell a proof from a refusal. A test that pins the wrong property will confirm
whatever you mutate.

**Claude, 2026-08-24, superseded by [MT-165](#mt-165).** I first corrected this by rewriting the "What
to do" above, which rule 5 of the README forbids: an entry's instructions are fixed once written, and
only the Disposition and Comments change. The reason is exactly this case - a result recorded against
instructions nobody can read afterwards means nothing, and the title of this entry now contradicts what
the application does.

So the instructions are back as they were, this entry is superseded, and MT-165 asks for the behaviour
that is actually correct. Nothing here needs running.

---

<a id="mt-158"></a>

### MT-158 - 2026-08-24 - The autonomy editor's title, and the blocked-points window

**Disposition:** fixed validated
**From:** OB-082, OB-083
**Written:** 2026-08-24

**What to do.** Two looks.

1. Open the autonomy editor. Its title bar should read **Autonomy Editor: {page}**, matching the layout
   editor's "Layout Editor: {page}" rather than using a dash.
2. Open "unavailable while occupied" on a station with plenty of named squares. The list should be
   white, wide enough to read the message above it, and a wheel notch should move about a row. The
   station you opened it ON must not appear in the list - nor should a caption square that is about
   that same station.

#### Comments

**Claude, 2026-08-24.** Four cosmetics and one correctness item. The station itself was already excluded, but a caption
square points AT the station and carries a name, so it appeared in the list as though it were somewhere
else - choosing it would have held the station back with itself through the back door the existing
check closes at the front. Adam asked for exactly that: "ensure self-selection is impossible".

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-159"></a>

### MT-159 - 2026-08-24 - The backup is one archive holding all the state

**Disposition:** fixed validated
**From:** FR-015
**Written:** 2026-08-24

**What to do.** File > Backup TrainControl Data.

The dialog should name a **.zip** file. Open it and check it holds: `UIState.data`, `LocDB.data`,
`config/gleisbild.cs2`, every page under `config/gleisbilder/`, and `config/autonomy/setup.json` plus
every `configuration-*.json`.

Then the point of the exercise: unpack it somewhere else and check the layout is complete - pages, page
index, and autonomy setup together.

#### Comments

**Claude, 2026-08-24.** Adam: "the backup menu option should export a zip file with the locdb and uistate files, track
diagram files, and autonomy files - effectively, all state."

The argument for one archive rather than a folder of copies is the 23 August restore. The autonomy
setup is keyed by PAGE ID, and those ids are defined by `config/gleisbild.cs2` - so `setup.json` alone
means nothing, and a `gleisbild.cs2` from a different day silently reattaches every station to the
wrong page. These files are only a backup together.

The state is written live first so the archive holds it as it is now. A source that is not there is
skipped - a Central Station layout has no local config folder - and a file that cannot be read is named
individually, because a file held open by a sync client is the everyday case here.

**Adam, 2026-08-24 (triage).** Works, with notes.

Content is good, but we are missing the folder name of the active layout (i.e., zip file contains "config" instead of "cs2_sample_layout").

**Claude, 2026-08-24.** This one needs a second look from you rather than another change from me, and
here is why.

The fix you are asking for was already in the build you tested. It landed in `c830704f` at 10:21,
from an earlier note of yours; the build you ran was compiled from `8db330da`, made at 14:12. The code
puts the layout folder into the archive under its own name - `cs2_sample_layout/config/...` - falling
back to `layout` only if the folder name cannot be read at all.

So either the fix does not work or the archive you opened was made before 10:21. Nothing in the report
tells those apart, and guessing between them is how a working fix gets "fixed" again into something
worse.

I have settled the half that can be settled here. `testBackupArchiveNamesTheLayout` builds a layout
folder of the real shape, hands it to `Util.zipInto` under a prefixed key exactly as the backup does,
and checks the archive: `cs2_sample_layout/config/gleisbild.cs2` and
`cs2_sample_layout/config/autonomy/setup.json` are both there, nothing is filed under a bare
`config/`, and `UIState.data` stays at the top where it belongs. The mechanism does what it says.

**Please take a FRESH backup and look at that one.** If it still says `config`, say so and I will dig
properly - it would mean something between the key and the archive that this test does not cover.

**Your second question, answered rather than changed:** a Central Station layout is not skipped. When
there is no local layout path, the backup ASKS whether to include the station's files, downloads them
into a temporary folder, and puts them in the archive under `central-station-layout`. If the download
fails it is listed in the incomplete-backup dialog and the rest of the archive is still written. So it
already does the option you said you would prefer.  

Also, what happens when a CS2 layout is being used- do we skip this step, or do we download the files from the CS2 and then same them?  The latter would be preferred if serviceable.

*Run against commit b1e22b5b, build\classes, compiled 24 Aug 08:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Adam ran this and said "works, with notes" - so the behaviour is validated and
the disposition says so. His notes are follow-up work rather than a failure of this test, and rewriting
these instructions to cover them is not allowed and would not be right anyway: what they ask for is a
different thing to check. They are carried into [MT-169](#mt-169).

Recorded because the verdict sat in this entry for hours without moving the disposition, which is
exactly what the disposition is for (RA-C4).

---

<a id="mt-160"></a>

### MT-160 - 2026-08-24 - The application starts, and the window does not freeze

**Disposition:** fixed validated
**From:** OB-077, OB-078, OB-079
**Written:** 2026-08-24

**What to do.** Three quick ones; none should show anything unusual, which is the point.

1. Start TrainControl normally. It should open as always. (The fix is to a failure path: if the window
   ever fails to build, the application now exits with a message instead of hanging for ever with
   nothing on screen.)
2. With the layout editor open, use the route editor's Add and Edit buttons. The "close the editor
   first" message should appear normally, not late and not behind the window.
3. Dispatch a train by hand, and while it is being set up, hover the "No available paths" mark on
   another locomotive's panel. The window must stay responsive; the explanation may take a moment to
   appear.

#### Comments

**Claude, 2026-08-24.** Three internal robustness fixes with little to see.

The start-up latch had one `countDown`, as the last statement of the window build, and no timeout on
the wait - so anything that stopped it reaching that line hung the application for ever with no window
and no message. It is in a `finally` now and catches `Throwable`.

`refuseWhileEditorOpen` built and showed a modal dialog, and two callers asked it from a raw worker
thread. It posts to the event thread now.

`explainDestinations` walks every candidate route to every station and takes the Layout monitor, which
a dispatch holds across its per-command sleeps - so hovering that label during a dispatch froze the
window. It runs on a thread now, guarded so a slow answer cannot land on a locomotive it is not about.

**Two callers left as they are**, and recorded: the right-click menu's `getPossiblePaths` and the
synchronized `moveLocomotive` on paste. Both are gestures that already imply a wait, and neither runs
while the monitor is held for seconds.

**Adam, 2026-08-24 (triage).** Works, with notes.

Looks good for for the window content in 3, increase the font size to match the standard size of all other windows.  Make the headings (could choose / never choose) bold.

*Run against commit b1e22b5b, build\classes, compiled 24 Aug 08:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Adam ran this and said "works, with notes" - so the behaviour is validated and
the disposition says so. His notes are follow-up work rather than a failure of this test, and rewriting
these instructions to cover them is not allowed and would not be right anyway: what they ask for is a
different thing to check. They are carried into [MT-169](#mt-169).

Recorded because the verdict sat in this entry for hours without moving the disposition, which is
exactly what the disposition is for (RA-C4).

---

<a id="mt-161"></a>

### MT-161 - 2026-08-24 - A page may be called "2" without stealing page 2's settings

**Disposition:** fixed validated
**From:** OB-067, FR-013
**Written:** 2026-08-24

**What to do.** This one needs a page whose file is temporarily missing, which on your railway is the
OneDrive case rather than something to arrange by hand. If you would rather not, say so and it can be
left to the automated test that covers it.

1. Note the id of one of your pages from `config/gleisbild.cs2` - say page 3 is "Upper Yard".
2. Rename some OTHER page to **3**. A page called "3" is legal and stays legal.
3. Move `Upper Yard.cs2` out of `config/gleisbilder/` and start TrainControl.
4. The page called "3" must NOT be showing Upper Yard's stations, names or captions.
5. Close TrainControl, put the file back, and start again. Upper Yard must have everything it had.

**What matters is step 5 as much as step 4.** The old behaviour lost the settings in both directions at
once: the live page collected them, and the absent page's copy was overwritten on the next save.

#### Comments

**Claude, 2026-08-24.** The keys are `"page:x,y"` strings. On disk the page part is an ID and in memory
it is a NAME, and both halves of the translation are string lookups - so each rests on being handed the
kind of string it expects. The code said as much: "ids are numeric and names are not, so the two never
collide". `validateLayoutName` allows digits, so they do, and Adam ruled the name stays legal - "A page
should be allowed to be named 2 - let FR-013 dissolve it."

The reachable way in is a page that is not loaded, which is ordinary here: `pagesNotLoaded` exists
because a OneDrive placeholder or a file held by the sync client is enough for CS2File to skip a page.
An entry belonging to an absent page could not be translated, so it kept the file's id as its page part
- and an id sitting where a name goes is indistinguishable from a page actually called that.

The first fix was to leave such keys alone on the way OUT, and the test written for it showed that is
not enough. The damage does not need a save: `getPointName(new TileKey("1", 3, 3))` already returned
the absent page 1's station, in memory, because "1" is what both of them look like. The pun is in the
representation, so no care at the boundary can unmake it.

So nothing whose page is unknown enters memory at all. It waits as the exact JSON it arrived as and is
merged back on save, and the file's record of what that id was called is kept too - otherwise one save
during the absence would drop the only evidence a renumber can be told from a rename by.

Two things the fix's own tests found:

- The first rule held back RENAMED pages as well, which is the MT-135 loss caused by the mechanism
  written to prevent it. `pageIsHere` now mirrors `pageOf` exactly rather than asking its own version
  of the question.
- A page whose file disappears has its id RETIRED by the index, so a page that goes away and comes
  back is a new page with a new id, and its old settings stay behind under the old one. That is the id
  system working - it is what stops a later page inheriting them - but it means the return in step 5
  above depends on the index not having been rewritten while the file was away. Worth knowing; filed
  as its own question rather than changed here.

**Adam, 2026-08-24 (triage).** Does not work.

When I renamed "5 - Test" to 5, the main page (1 - Main, id 5) became excluded from autonomy and lost all its train placement.

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-31 (triage).** Works.

*Run against commit 302d7a11, build\classes, compiled 31 Aug 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-162"></a>

**From [OB-092](issues.md), filed 2026-08-24 - "renaming a page to "5" excluded the page whose id is 5, and emptied it".** The entry as it stood when it was picked up:

**Kind:** bug  
**Adam, 2026-08-24, testing MT-161 on commit 8db330da:** "When I renamed '5 - Test' to 5, the main page (1 - Main,
id 5) became excluded from autonomy and lost all its train placement."

**Reproduced before anything was touched**, with a probe on a six-page fixture: the file held
`"excludedPages": ["6"]` before the rename - correctly, the id of the page being renamed - and
`["5"]` after it. On reload the exclusion had moved to "1 - Main".

**Cause.** `renamePage` rekeys eleven collections and then leaves the store's OWN numbering stale. It
never told `pageNameToId` that the page it knows as "5 - Test" now answers to "5", so the next save
asked about the new name, got nothing, and took `translatePages`' fallback: write the bare page NAME.
`untranslatePages` reads every value in that array as an ID. A page called "5" comes back as whichever
page holds id 5.

The exclusion is only the visible half. An excluded page is not in the graph, so every placement on it
goes with it - which is the "lost all its train placement" half of the report.

**Fixed** in `renamePage`: the rename moves the page in `pageNameToId` and `pageIdToName` as well,
leaving the id alone, since a rename is the one thing ids exist to survive. Plus a defence at the read
side - a value the file never recorded as an id is read as a name, using the file's own `pages` record
as the discriminator.

**Test:** `testRenamingAPageToAnotherPagesIdMovesOnlyThatPage`, seen red for exactly Adam's symptom.

**Why the existing rename tests missed it, which is the part worth keeping.** There are several and
they are thorough about what a rename must CARRY. Not one renamed a page to a string that is also a
live id, because no fixture anywhere had a page named like a number - so the collision could not
arise. Same shape as TA-A1 and CR-C3: the fixture decided the answer before the assertions did. The new
test checks every collection, and checks the OTHER page as hard as the renamed one.

### MT-162 - 2026-08-24 - The caption menu says which station a square is showing

**Disposition:** fixed validated
**From:** FR-014
**Written:** 2026-08-24

**What to do.** Find a square that is showing a station's name and has no train standing at that
station, so it reads as dashes.

Right-click it, in the autonomy editor AND from the track diagram's own menu. Both should now offer
**Show a Different Station Here... (now showing <name>)** and **Stop Showing <name>**, naming the
station rather than saying "Clear This Square".

On a square with no caption the first item should read as before - "Show a Station Name Here..." - and
there should be no clear item at all.

#### Comments

**Claude, 2026-08-24.** Adam: "the show station name here right click menu option in the autonomy
editor should clearly indicate the current station being shown, in cases where the user just sees
[---] on the diagram."

A caption draws the station's OCCUPANT, and an empty station draws as three dashes - so on most of the
railway most of the time, a captioned square says nothing about which station it is about. The menu did
not say either.

Written into `addCaptionItems`, which both menus build their caption items through, rather than at
either call site. The deep menu already carries a title naming the station and the editor's own menu
has none - so a fix written where the reader happens to be looking could have landed on the menu that
was already fine. The test asserts the un-named key is used NOWHERE, which is the form a second copy of
the menu cannot get past.

**Adam, 2026-08-24 (triage).** Works, with notes.

They both appear, but in show a different (currently <name>), you and remove the currently part since we have the "Stop showing <name>" already.

**Claude, 2026-08-24.** Done. The item read "Show a Different Station Here... (now showing Platform
3)" directly above one reading "Stop Showing Platform 3", so the menu named the same station twice in
two lines and the second one was the line that needed it.

The two menu keys still differ, and that difference is the part worth keeping: "a Station Name" when
the square shows none, "a Different Station" when it already shows one. That says which situation you
are in without spending a line on which station it is.

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-163"></a>

### MT-163 - 2026-08-24 - The "no available paths" reasons, in a window

**Disposition:** fixed validated
**From:** FR-017
**Written:** 2026-08-24

**What to do.** With autonomy loaded and a locomotive showing **No available paths**, click the
information mark beside that text.

1. A window opens with a scrollable list of every station and why each is unavailable.
2. The stations autonomy could choose - occupied, route blocked - come FIRST, under their own heading.
   The ones it will never choose - switched off, a reversing point, not marked as a destination, or
   excluding this locomotive - come after.
3. Read a station in the second group and check the reason is the standing one. A station that is both
   barred AND has a train on it must say it is barred, not that it is occupied - that is the half of
   this that changed behaviour.
4. The window may take a moment to fill in and says so while it does. The rest of the application must
   stay responsive throughout - try it while a train is being dispatched.

#### Comments

**Claude, 2026-08-24.** Adam: "let’s also make it clickable and show the notes in a popup with a
scrollable text area with the whole list of stations. Order them by ones that can be chosen
autonomously and ones that cannot, with the autonomous ones first."

The hover tooltip stays; it now has a window behind it. A tooltip cannot be scrolled, goes away while
it is being read, and this one showed twelve stations and then an ellipsis.

The grouping is answered by the Layout rather than by matching the reason strings - two of the standing
bars are translated sentences that would have to be compared by value, and this window would then be a
second answer to "can autonomy pick this station".

Asking that question surfaced step 3's change. `explainDestinations` tested "occupied" first, so a
station autonomy will never choose reported "occupied by X" whenever a train happened to be sitting on
it. The train will leave; the bar will not.

Computed off the event thread, which this file's comments already insisted on twice. The window opens
saying it is working and fills in when the answer arrives - a modal dialog runs its own event pump,
which is what lets that work.

**Adam, 2026-08-24 (triage).** Works, with notes.

looks good- just increase the font size within that window to the standard

*Run against commit b1e22b5b, build\classes, compiled 24 Aug 08:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-24.** Adam ran this and said "works, with notes" - so the behaviour is validated and
the disposition says so. His notes are follow-up work rather than a failure of this test, and rewriting
these instructions to cover them is not allowed and would not be right anyway: what they ask for is a
different thing to check. They are carried into [MT-169](#mt-169).

Recorded because the verdict sat in this entry for hours without moving the disposition, which is
exactly what the disposition is for (RA-C4).

---

<a id="mt-164"></a>

### MT-164 - 2026-08-24 - Return Home works around a train that is in the way but has somewhere to go

**Disposition:** superseded
**From:** FBR-B1
**Written:** 2026-08-24

**What to do.** This is the other half of [MT-157](#mt-157), and the two want running together.

1. Pick a station **S** with a home locomotive **A**, and make S unavailable while some other square
   **X** is occupied ("unavailable while occupied").
2. Stand **A** somewhere away from S.
3. Stand a second locomotive **B** on **X**, and give **B** a home of its own that is NOT X.
4. Press Return Home.

It should stage both: **B off X first, then A into S.** It must NOT report that staging is impossible.

**Then change one thing and run it again:** take B's home away, so B has nowhere to go. Now it SHOULD
refuse up front - that is MT-157, and it is still the right answer, because nothing will ever move B
off X.

The difference between the two runs is the whole of this entry. A train in the way is not the same as a
train that is stuck.

#### Comments

**Claude, 2026-08-24.** Found by the Fable review of my own OB-073 fix, and it is the third time in this
session that a rule was moved somewhere its precondition did not hold.

The impossibility scan proves one thing - "no move can ever end there" - and every test in it was
state-independent for that reason. IMPOSSIBLE is shown to the operator as a proof, with the blocked
locomotives named, and it skips the search entirely. The OB-073 fix put the state-AWARE check in there,
so a locomotive merely standing in the way counted as proof, including one whose departure is the
plan's own first move.

`connected`, four lines below the scan, states the rule that was broken: "A route blocked merely by
another train is not impossible - moving that train is exactly what the planner is for."

The scan now counts an occupant only when staging will never move it - no home, or already standing on
its home. Everything else is left to the search, which vacates squares as it takes moves.

The fixture MT-157 shipped with could not have caught this: its blocker has no home, so IMPOSSIBLE was
the right answer for it. That is why this entry asks for both runs.

**Claude, 2026-08-24, superseded a few hours later.** Both halves of that last sentence are wrong, and
the entry asks for a distinction that does not exist.

The blocker in MT-157's fixture is not homeless: `moveLocomotive` runs `claimHome`, so standing a
locomotive anywhere by hand gives it a home right there. And IMPOSSIBLE was not the right answer for it
either - that same arrangement has an executable three-move plan, which a reviewer produced by running
the search directly.

There is no "in the way versus stuck" distinction to test, because staging can move anything that is
not on a launch pad. The occupancy test has been taken out of the impossibility scan altogether, and
[MT-157](#mt-157) now asks for the behaviour that is actually correct. Nothing here needs running;
running it would give a result about a rule that no longer exists.

Kept rather than deleted, because its tag is cited from `docs/reviews/2026-08-24-fable-round-review.md`
and from two commits.

---

<a id="mt-165"></a>

### MT-165 - 2026-08-24 - Return Home stages a blocker out of the way instead of refusing

**Disposition:** fixed unvalidated
**From:** OB-073, FBR-B1, FBR-B2
**Written:** 2026-08-24

Replaces [MT-157](#mt-157), whose instructions asked for a refusal that turned out to be the wrong
answer. Do not run that one.

**What to do.** Hold one locomotive's home station back by another square (the "unavailable while
occupied" menu), and stand a DIFFERENT locomotive on that square.

Press Return Home. It should produce a plan that **moves the blocker out of the way first** and then
brings the home locomotive in, and the run should finish with everyone home.

The failure to watch for is the original bug: trains start moving, one leg is refused, the run retries
until it gives up, and everything stops with the fleet half-staged. Refusing before anything moves is
also wrong, and is what two attempts at this fix did instead.

#### Comments

**Claude, 2026-08-24.** OB-073 was real: the planner did not read `getBlockedBy` at all, so it produced
plans whose arrival `isPathClear` then refused. The fix for that is a check inside `firstClearRoute`,
asked of the state as the search takes moves, and it has been in place throughout.

What went wrong twice was the OTHER thing that fix did - putting an occupancy test into the
impossibility scan, whose whole job is to prove that no arrangement can work. Staging can move whatever
is standing in the way, so no occupancy proves anything, and the scan was reporting IMPOSSIBLE for
arrangements the search solves in three moves. Narrowing it to blockers "staging will never move" was
wrong for the same reason, because a hand-placed locomotive gets a home where it stands and the search
moves locomotives off their homes freely. The test is gone from the scan entirely.

The automated test replays the plan move by move and asserts, at each move, that the destination is
free AND that nothing occupies a square holding it back - and it was mutation-checked to confirm that
the FR-001 assertion is the one that fires, rather than a move-count check standing in front of it.

**Adam, 2026-08-24 (triage).** Could not run this.

test this in a test case

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-31 (triage).** Does not work.

This part works, but what doesn't work is the snapshotting- if I semi-autonomously move a train away from the station where I opened traincontrol, the return home button doesn't light up.

*Run against commit 302d7a11, build\classes, compiled 31 Aug 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-31.** Understood, and that is a different claim from this entry's subject.

What MT-165 is about - staging a blocker out of the way rather than refusing - you say works. What you
have found is upstream of it: the home CLAIM. `Layout.claimHome` gives a hand-placed locomotive a
positional home where it is put, so opening TrainControl with a train at a station makes that station
its home; move it away semi-autonomously and Return Home should light up, because it now has somewhere
to go and is not there.

It does not, which means the button's own check - `triageReturnToHome()` - is still answering
ALREADY_HOME or NO_HOMES after the move. Either the claim never happened, or it followed the train.

Filed on its own rather than folded in here, so this entry can close on what it actually tested.

**Claude, 2026-08-31 (second round).** You were right that the home followed the train - or rather,
that it was never claimed at all.

`claimHome` refused any square drawn as more than one graph Point. That was LD-8 carrying your ruling
of 2026-08-25 - "any home with two graph points should be refused" - from the assignment door to the
positional one.

**Measured on your own graph: ten of your thirty-six station squares carry a block** - BottomMainA,
BottomMainB, BottomMainC, BottomInner, TopMainR1, TopMainR2 and Tunnel among them, which are the
main-line platforms trains actually stand on. A probe confirmed it end to end: a train on a split
square at startup gets 0 homes and `NO_HOMES`; the same train on a plain square gets its home, and
moving it away lights the button. So the default you describe has never happened for most of your
railway, and full autonomy lit it because a train eventually parked on one of the twenty unblocked
squares.

**I have reversed half of your 2026-08-25 ruling, and I want you to know which half.** The ASSIGNMENT
door still refuses a split square: a person naming a station gives no way to know which copy they
meant. The POSITIONAL default does not: the copy is the one under the wheels. If you would rather it
were refused everywhere, say so and I will put it back - but then Return Home cannot work for a train
standing on any of those ten squares, and that is the trade.

**The ambiguity does not vanish, it moves**, and the second half of the fix is what holds it: a train
coming back on the FAR copy of its own platform is now judged home, because the copies of a square are
one piece of track. Without that the planner would have tried to move it onto one particular arrival
side of the platform it was already standing on, which can be impossible.

1. **Your case.** Open TrainControl with a train on BottomMainC, drive it away semi-autonomously, and
   Return Home should light.
2. **Send it home**, and check it is content wherever it lands on that platform - either arrival side.
3. **An explicit home still wins**, and assigning one to a split square should still be refused.

*Run against v3_0_0_rc4 or later.*

**Claude, 2026-08-31 (third round).** The other half went too, on your ruling: "so the home should
just be the logical point, and the direction is wherever the locomotive was facing when it started
moving."

So the question I left open above - whether to refuse a split square everywhere instead - is answered
the other way. The assignment door accepts one now, and so does the loader, which was silently
dropping homes the editor had accepted. See [MT-245](#mt-245).

---

<a id="mt-166"></a>

### MT-166 - 2026-08-24 - The backup dialog offers to show the file

**Disposition:** fixed validated
**From:** FR-019
**Written:** 2026-08-24

**What to do.** File > Backup TrainControl Data.

The dialog should now name just the **file** - `...-TrainControl.zip` - rather than the whole path, and
carry two buttons: **OK** and **Show Files**.

1. Press **Show Files**. Explorer should open on the backup folder with that archive already selected.
2. Do it again and press **OK**. Nothing should happen but the dialog closing.
3. Check the log: the full path should be there, once, for the backup you just made.

Also worth one run with something wrong - the easiest is to open the .zip from a previous backup in
another program so it cannot be overwritten. The dialog should say what could not be saved, and Show
Files should still work.

#### Comments

**Claude, 2026-08-24.** Adam: "after a backup finishes, in the window that prints the path, instead of
printing the path in the popup, add a button to 'show files'. That way, the user can dismiss (OK) or
Show Files in the OS file explorer. You no longer need to print the path - just the filename in the
popup, leaving the full path in the log."

The file is SELECTED rather than the folder merely opened, which needs `explorer /select,` on Windows -
worth the special case, because the backup folder accumulates one archive per backup and opening it
plain leaves you hunting for the newest. Other platforms open the folder.

Opening the file manager happens off the event thread: a cold Explorer takes seconds to answer, and by
then this dialog is gone, so the freeze would have had nothing on screen to explain it. Failing to open
it is silent beyond the log - the backup succeeded, and a second dialog saying the file manager would
not start is noise on top of the one just dismissed.

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-167"></a>

### MT-167 - 2026-08-24 - The application does not freeze while trains are running

**Disposition:** fixed validated
**From:** OB-087
**Written:** 2026-08-24

**What to do.** With full autonomy running and at least two trains moving, use the window while a path
is being set up - that is the window where switches are being thrown, half a second to two seconds.

1. Open the autonomy editor and close it again.
2. Open the autonomy viewer.
3. Right-click a turnout on the track diagram and throw it by hand.
4. Hover the information mark beside a locomotive showing "No available paths", then click it.
5. Press Stop.

Nothing should pause for more than an instant, and **Stop must respond immediately**, every time. If
anything hangs, that is the thing to report - and if the whole application stops responding, a thread
dump says in one step what no amount of reading can: `jstack <pid>` from the JDK's `bin` folder, with
the output pasted into the ticket.

#### Comments

**Claude, 2026-08-24.** Adam reported a deadlock during an autonomy run (OB-087) on a build 28 commits
old, at a moment when Claude Code went down as well - so a machine-wide stall is the more economical
explanation, and nothing in that build had changed locking.

Checking it found one that I had introduced hours later, in the current tree. A review finding asked
for `Layout.getPoints()` and `getEdges()` to copy under the monitor, as `getHomeStations` does. Both
are called from five places in the UI, and a dispatch holds that monitor across its per-command sleeps
- so synchronizing them put the event thread behind exactly the wait that OB-079 and IAR-B2 exist to
keep it out of. Reverted to the live views, which is what has been running for weeks.

This entry exists because that class of fault is invisible to the automated battery: every test in it
drives the model directly, and none of them has an event thread to freeze.

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-168"></a>

### MT-168 - 2026-08-24 - Capture records what full autonomy does, not only hand-driven moves

**Disposition:** fixed validated
**From:** OB-088
**Written:** 2026-08-24

**Rebuild before running this.** The build the bug was reported from predates the fix by 41 minutes,
and on that build it will fail exactly as reported.

**What to do.**

1. Turn **Capture Locomotive Commands** on, with autonomy stopped.
2. Start full autonomy and let two or three moves happen.
3. The timetable list should fill with them as they go.
4. Stop, and check the captured entries are still there and name the right locomotives and stations.

**Then the case that broke it**, which is the one worth a second run:

5. With capture still on, do something that rebuilds the setup - apply a diagram edit, place a
   locomotive on the diagram, or load a configuration.
6. Check the **Capture** button is still lit, and then dispatch a train by hand.
7. That move must be captured too.

Step 6 is the whole bug: the button stayed lit over a layout that had stopped capturing, so it looked
switched on and did nothing.

#### Comments

**Claude, 2026-08-24.** Adam, twice: "capture locomotive commands is capturing neither manual
locomotive commands nor full autonomy commands into the timetable", and then "nothing gets added to
the list while full autonomy is running".

The capture flag lives on the `Layout` object and `parseAuto` replaces that object wholesale, so a
fresh Layout started with capture off - and rebuilds happen far more often than they read. Applying a
diagram edit, placing a locomotive and loading a configuration all come through there, and **starting
full autonomy loads a configuration**, which is why it looked like an autonomy-specific fault.

The flag is read before the old layout is discarded and applied after the new one exists.

`test/regression/testTimetableCapture.java` covers it in three tests, and
`testCaptureSurvivesTheLayoutBeingRebuilt` is about this case specifically - the gap that let the
regression through, since nothing had tested capture across a rebuild.

**Adam, 2026-08-24 (triage).** Does not work.

I don't see any commands.  And the table headings are "Title 1", "Title 2", "Title 3", "Title 4", instead of what it should be.  Regression.

**Claude, 2026-08-24.** Both halves of this are one cause, and the headings are the proof of it.

"Title 1" to "Title 4" are the GUI Builder’s default column names, sitting in the generated
model exactly as the designer left them. `repaintTimetable()` replaces that model the first time it
runs - it checks `getColumnCount() != 5`, and four is not five - so seeing those headings means the
method had never run once in the whole session.

That is a stronger statement than "the table was empty". An empty table has two explanations: nothing
captured, or nothing repainted. The designer headings have only one. Nothing was repainting it,
because the callback that announced a path start or end was deleted with the GraphStream window in
`d8db4879`. The entries were there the whole time.

Fixed with [MT-149](#mt-149); check it with [MT-175](#mt-175).

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-169"></a>

### MT-169 - 2026-08-24 - The three things Adam asked for after running MT-159, MT-160 and MT-163

**Disposition:** fixed validated
**From:** MT-159, MT-160, MT-163 (Adam's triage notes)
**Written:** 2026-08-24

Three small things, from three tests that otherwise passed.

**1. The backup archive names the layout.** File > Backup TrainControl Data, then open the zip. The
track diagram and autonomy files should now sit under the layout folder's own name -
`cs2_sample_layout/config/...` rather than a bare `config/...`. `UIState.data` and `LocDB.data` stay at
the top, because they live beside the application rather than inside the layout.

**2. The "no available paths" window uses the standard font.** Click the information mark. The text
should be the same size as every other window in the application, not smaller.

**3. Its two headings are bold.** In the same window, "Stations autonomy could choose..." and "Stations
autonomy will never choose..." should stand out from the station lines under them.

#### Comments

**Claude, 2026-08-24.** Adam, on MT-159: "we are missing the folder name of the active layout (i.e.,
zip file contains 'config' instead of 'cs2_sample_layout')." On MT-160 and MT-163: "increase the font
size to match the standard size of all other windows. Make the headings (could choose / never choose)
bold."

The folder name matters for what the archive is for. Restoring means putting these files back beside a
`gleisbild.cs2` of the same vintage, and a bare `config` says nothing about which layout that was - so
two backups of two layouts are indistinguishable once they are off this machine, which is exactly when
somebody needs to tell them apart.

The font is taken from the look and feel rather than matched by hand. It was "Segoe UI" at 12, which is
both smaller than its neighbours and frozen there if the application's font ever changes; asking
`UIManager` makes "the standard size of all other windows" true by construction.

The headings are bold because the window is now a styled pane rather than a plain text area. A heading
is recognised by position - not indented, not blank - rather than by matching its text, because
matching the words would work in English and quietly do nothing in the other seven bundles.

**Adam's fourth note is a question, not a fix,** and is filed as [FR-020](issues.md): what should the
backup do when the layout is being read from the Central Station rather than from disk? Today it
silently has nothing to put in the archive.

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-170"></a>

### MT-170 - 2026-08-24 - Backing up a layout that lives on the Central Station

**Disposition:** needs test
**From:** FR-020
**Written:** 2026-08-24

**What to do.** You need TrainControl reading its layout from the Central Station rather than from a
local folder - that is, with no local layout path set.

1. File > Backup TrainControl Data.
2. It should ASK: the track diagram is on the Central Station, download it and include it?
3. Answer **yes**. The backup should take a little longer, and the archive should contain a
   `central-station-layout/` folder with the diagram files in it.
4. Run it again and answer **no**. The archive should be written without them, and the completion
   dialog should not claim anything is missing - declining is not a failure.
5. Then the unhappy one: switch the Central Station off, or unplug it, and answer yes. The backup
   should still complete, and the dialog should name the download as the thing that could not be
   saved rather than failing the whole archive.

**And check nothing is left behind.** The download goes to a temporary folder and should be removed
afterwards - in all three cases above, including the failure.

#### Comments

**Claude, 2026-08-24.** Adam, answering the question in FR-020: "for cs2 files, we have a top menu
option to download a layout from the station. so a backup run in this mode could ask the user if they
want to include this data."

It reuses that menu's own path - `model.downloadLayout(folder)` - into a temporary directory, adds it
to the archive, and removes it in a `finally` whatever happened. A stale copy of the diagram left in a
temporary folder is the sort of second copy this application keeps being bitten by.

Asked rather than assumed: it is a network fetch of every page, which is quick on a small layout, not
free, and pointless if the station is off. The question is put on the event thread and waited for,
because the backup runs on its own thread and can neither show a dialog itself nor sensibly carry on
without the answer.

**One thing this settled.** FR-020 asked where a Central Station layout's autonomy setup lives, and
whether the archive would hold half of a pair. It does not: `AutonomyCompanionStore` refuses a layout
that is not local - "autonomy needs a local copy of the track diagram, because its settings are stored
alongside the diagram files" - so there is no setup to pair these pages with, and an archive holding
only them is complete rather than half of something.

**Adam, 2026-08-24 (triage).** Works, with notes.

Works.  But if the user confirms the CS download, we should also download CS3 data files if using a CS3.

**Claude, 2026-08-24.** Done, and deliberately narrowly.

A CS3 serves the CS2-format `/config/` export, which is what the download already fetched, and this
application deliberately uses those offline files for the track DIAGRAM rather than the CS3 native
layout API - `getCS3LayoutUrl` says so itself, and records that parsing it is not supported.

But the application does NOT trust that same export for accessories and routes on a CS3: the sync path
and `getRenameProposals` both branch on `isCS3()` to read the JSON API instead. That is the
application admitting `magnetartikel.cs2` and `fahrstrassen.cs2` are not authoritative there - so a
CS3 backup was missing the good copy of exactly the two things it had just fetched the doubtful copy
of. Those two are now fetched as well, from the endpoints that already existed for them.

Each is tolerated separately, like `fahrstrassen.cs2` above it: an empty accessory or route list is a
real state of a station, not a fetch failure, and must not fail a download that has already succeeded.
A plain CS2 takes none of this path, and neither does a station that never answered the device probe.

**Not done, on purpose:** the CS3 native layout API, because what it would duplicate is already
fetched in a format this application can actually read; and the CS3 locomotive database, because this
download never fetched `lokomotive.cs2` for a CS2 either - adding only the CS3 side would put the two
out of step in a new direction.

Needs your confirmation against real hardware: I have no CS3 here, so what is verified is that the
code compiles, guards a null station, uses endpoints that already existed, and does not disturb the
CS2 path.

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-24 (triage).** Does not work.

CS3 dedicated files are still mising, see other bug

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-171"></a>

**From [OB-099](issues.md) - "cs3 files still not backed up".** The entry as filed:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:46  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

these are examples of files that should be downloaded when connected to a CS3 only (not CS2) (IP depends on what's being used) http://192.168.50.25/app/api/locos http://192.168.50.25/app/api/automatics http://192.168.50.25/app/api/mags

### MT-171 - 2026-08-24 - Nothing about the railway changed when the store started holding objects

**Disposition:** fixed validated
**From:** FR-013
**Written:** 2026-08-24

**Back up first.** File > Backup TrainControl Data, and keep the zip somewhere you can find it. This
touches the class that holds your whole autonomy setup, and while the file format is unchanged, that
is a claim to be checked rather than trusted.

**What to do.** The whole point is that you should not be able to tell. So this is a tour of the
things that read or write the setup, looking for anything that has quietly stopped working.

1. **Open the app and look.** Every station name, caption, direction arrow, length and restriction
   should be exactly where it was. Compare against the backup if anything looks thin.
2. **Rename a page.** Everything on it must still be there afterwards, and everything on the other
   pages untouched. Close and reopen, and check again - the reload is the half that used to differ.
3. **Move a tile** in the layout editor, one carrying a station name, a length and a direction. All
   three should travel with it. Undo, and all three should come back.
4. **Build over a tile** that had settings, then reconcile. Its settings should be reported as dropped
   rather than silently kept.
5. **Caption a station at both ends of a platform,** then ask for its captions - both should be found.
   That one was broken by the conversion and caught by a test; it is worth seeing on the railway.
6. **Delete a page** with settings on it, and check the reconciliation report names what went.
7. **Run autonomy** briefly, and use Return Home. Nothing here should be different, but the planner
   reads the setup through the same doors.

**What a failure looks like.** Not a crash - a silence. A setting that does not follow its tile, a
caption that reports none, a name that vanishes without appearing in the report. If anything is
missing rather than wrong, that is the shape.

#### Comments

**Claude, 2026-08-24.** Adam: "Ideally: string keys only matter at import/export. Internally, we
should always use objects."

Nine of the ten square-keyed collections now hold `TileKey` rather than a `"page:x,y"` string.
`tileDirections` is stage two - its key carries a `#dx,dy` route suffix and wants a compound key.

The file format is unchanged: strings stay on disk and the id/name translation stays at the boundary,
which is why the backup should restore cleanly whatever happens.

**Why this entry is a tour rather than one check.** The conversion compiled clean and then failed nine
automated tests, and every failure was the same thing: `Map.get`, `containsKey`, `Set.contains`,
`remove` and `String.equals` all take `Object`, so passing the printed form of a key compiles and
silently answers false. Five such no-ops were found that way - configuration points not following
their tile, directions surviving a tile being built over, a platform captioned at both ends reporting
none, a name neither forgotten nor reported, an orphaned station never removed.

They were all caught by tests written over the past week. But the compiler could not see any of them,
before or after the change, so the honest position is that the automated suite found the ones it
covers and this entry is for the ones it does not.

**Adam, 2026-08-24 (triage).** Does not work.

stations do not travel with a moved tile. they vanish until the tile is moved back, at which point the loc is vanished.

renaming a page MOVES locomotives to othre stations, not just deleted them.  make a COMPREHENSIVE AND REALISTIC test case to reproduce these bugs, then fix them.

**Claude, 2026-08-24.** Two separate faults in this entry. One is fixed; the other is not, and this
entry stays open for it.

**The rename half is fixed.** "Renaming a page MOVES locomotives to other stations" is the duplicate
placement written up on [MT-135](#mt-135): the capture that runs after a rename recorded every
locomotive a second time under the page's old name. A train that appears at two squares reads as
having moved to one of them. Reproduced on the sample layout on the same squares Adam reported, and
covered by `testARenameDoesNotLeaveLocomotivesInTwoPlaces`, which fails without the fix.

**The moved-tile half is NOT fixed and is not yet understood**: "stations do not travel with a moved
tile. they vanish until the tile is moved back, at which point the loc is vanished." That is
`moveTiles`, a different operation from a rename - dragging a tile in the editor - and nothing in this
round touched it. It is not a consequence of the rename fault and will not have been fixed by fixing
it.

Left open deliberately rather than closed on the strength of the half that is done.

**Claude, 2026-08-24, later.** I drove the moved-tile half the way the rename half was driven, and
**it does not reproduce.** Saying so plainly, because a green test on this entry could otherwise be
read as the bug being fixed, and it is not - it is unreproduced, which is a different thing.

`testAMovedTileCarriesItsSetup` makes the editor's own gesture - clear the source square, set the
component's coordinates, add it at the destination - then `moveTiles`, then the capture that runs when
the editor closes, then the save, then re-reads everything from disk. Four variants, all correct in
both directions with the error count unchanged:

- to an isolated empty square, which disconnects the station from the run
- one square along onto occupied track, which is the ordinary nudge
- each move in its own sitting, as a restart between them would give
- both moves in one sitting with a single capture at the end

The station travels, its name travels, the locomotive travels, and everything comes back. Mutation
checked - commenting out the line that moves the stations fails it - so it is not a test that cannot
fail.

**The lead I would follow next is the display, not the data.** Nothing is told to redraw when
`moveTiles` rebuilds the graph: `touched()` rebuilds and notifies nobody. A station whose marker is
not repainted looks exactly like a station that did not travel - which is word for word what this
entry reports - and that is the same shape as the timetable fault, where the data was perfect
throughout and the panel was simply never repainted.

**What would settle it, Adam:** which gesture, exactly? A single tile dragged, or a multi-square
selection dragged? And did the station reappear if you closed and reopened the editor without moving
the tile back - because if it did, the data was there all along and this is a repaint.

**Adam, 2026-08-24.** "It seems like it works now!"

**Claude, 2026-08-24.** Recorded, and with a caveat, because I never reproduced this half and so cannot
say I fixed it.

The likeliest explanation is that it was never independent: it was the rename corruption seen from
another angle. While placements were being duplicated, a locomotive was recorded in two places, which
makes autonomy refuse the ENTIRE setup - and a refused setup has no running Layout, so the labels have
no locomotive to read. That is consistent with everything observed: with the duplicates gone the
labels have something true to show, and it explains why four realistic reproductions of the move
itself came back clean. It also matches this entry's own other sentence - "renaming a page MOVES
locomotives to other stations" is the same duplication described from the other side.

**The caveat.** Data already corrupted by a rename made BEFORE the fix stays corrupted; the fix stops
new duplicates, it does not remove old ones. If a locomotive ever reads as being in two places again,
the autonomy editor's list will say so in as many words, and the count on the diagram strip will show
it as an error rather than a warning.

Marked fixed unvalidated rather than validated: what Adam confirmed is that the symptom is gone, which
is not the same as this entry having a test that would catch its return. The move is covered by
`testAMovedTileCarriesItsSetup`; the label path it turns on still is not, and cannot be without a
window.

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-172"></a>

### MT-172 - 2026-08-24 - The autonomy editor's grid, its hover outline, and the routes in a backup

**Disposition:** fixed validated
**From:** OB-091, FR-021
**Written:** 2026-08-24

**The grid, in both editors.** Open the layout editor and the autonomy editor in turn, and toggle the
grid in each.

1. The diagram should widen by the same amount in both when the grid goes on. It always did in the
   layout editor; the autonomy editor used to draw its line without making room for it, which is what
   was truncating the tile art.
2. With the grid OFF, tiles must sit flush in both - no white gap between them. That is MT-127 and it
   must still hold.

**The hover outline.** In the autonomy editor, move the pointer across the diagram. Each square should
take a blue outline as you cross it, the way it does in the layout editor - and **nothing should move**
while you do it, with the grid on or off.

**The routes in a backup.** Two different things, and the first applies to every layout.

3. File > Backup TrainControl Data, and open the zip. It should contain **`routes.json`** at the top
   level - that is TrainControl's OWN route database, exported through the same door the Export Routes
   menu item uses. Open it and check your routes are in it; File > Import Routes should read it back.
4. If your layout is on the Central Station: say yes to including it, and the archive should also carry
   `central-station-layout/config/fahrstrassen.cs2` alongside the track diagram and
   `magnetartikel.cs2`.
5. Also check "Download Central Station Layout Files" from the menu: the folder it writes should have
   the route file in it too.

For a LOCAL layout the station's own `fahrstrassen.cs2` was already in the archive - the whole `config`
folder is copied - but that is the STATION's copy, not the database this application runs, which is
why step 3 exists.

#### Comments

**Claude, 2026-08-24.** Adam, OB-091: "enabling the grid widens the track diagram in the layout editor
(how it always was, because there is a double line in between cells), but not in the autonomy editor.
Make the behavior of the autonomy editor match so that there are no tile truncations. also, add the
blue outline hover effect to the autonomy editor."

The autonomy editor drew its grid with a border that paints and reserves nothing, on the reasoning that
MT-127 requires its tiles to sit flush. That read one rule across two states: MT-127 is about the grid
being OFF, and it is answered by the branch that returns no border at all. With the grid ON, reserving
nothing means the cell is sized as though there were no line and then has one painted over it.

The hover was not weakened, it was absent - `receiveMoveEvent` returned immediately in autonomy mode,
on the reasoning that the hover previews a placement and nothing is placed here. The outline is not a
preview; it is the answer to "which square am I about to right-click", and this editor's menus act on
exactly that square. The tooltip preview stays off.

FR-021, Adam: "validate that the current route config is exported when a backup is requested", and
then "OB 90 should run an export of our route db."

Two separate gaps, and his second message is the one that matters. `fahrstrassen.cs2` is the CENTRAL
STATION's copy of the routes; TrainControl has its own route database, edited in this application, and
that is what actually runs. A backup holding only the station's file would restore whatever the station
last knew rather than what the operator built. So the archive now carries `routes.json`, written
through the same method the Export Routes menu item uses, which means Import Routes will read it back.

The station's file was missing too, on one path. `downloadCS2Layout` - used by the backup for a Central
Station layout, and by the download menu to make a local copy - fetched the track diagram, the pages
and the accessories and left the routes behind. It is tolerated when absent, unlike the other three: a
station with no routes defined is ordinary, and the whole download should not fail over it.

**One thing this turned up that is worth knowing.** `testEditorSurfaceRules` asked
`LayoutEditor.restingBorder(...)` in its two-argument form, which reads the live grid preference out of
the Java Preferences store - so the test asserted whatever was last clicked on this computer. It passed
for weeks because the preference defaults to on, and failed the moment you turned the grid off while
testing this. It passes the state explicitly now and checks both.

**Adam, 2026-08-24 (triage).** Works, with notes.

1/2. Confirmed works.
3. Confirmed routes are present, but I also see autonomy.json in the root, and the "autorun" folder which I believe is a copy from the cs2_sample_layout folder.  Is the root autonomy.json a new file?

4/5. will check later

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.**

Your question from item 3, answered late - sorry it sat. Neither file is new.

autonomy.json in the root is the LEGACY autonomy configuration: hand-authored, and still the fallback the application auto-loads when the running layout did not come from a track diagram. It predates the diagram feature rather than being produced by it. TrainControlUI.AUTONOMY_FILE_NAME names it, and the autosave that writes it is explicitly gated on activeDiagramConfiguration == null - the comment there says why, which is that writing a diagram-derived graph into it once replaced named points with generated coordinates on every clean exit and took the ground-truth baseline with it. So while you are on a diagram configuration it should not be rewritten; the companion store under config/autonomy is the source of truth then, and autonomy.json is left alone. It is no longer tracked in git, which is why it shows up as a loose file.

The autorun folder is a layout folder of the Central Station's, not ours - it appears under test/test_layout/config/autorun and inside tc_backup, and nothing in src/ refers to it by that name at all. So yes, it is carried along as part of a copied layout, exactly as you guessed; it is not something the application creates.

One thing worth your eye rather than mine: the root autonomy.json carries a modified time of 28 Aug 20:58. On the reading above it should only be written when no diagram configuration is active, so if you have been on the diagram throughout, that timestamp is worth a second look - it would mean the gate is not holding. I have not been able to attribute it, and I would rather say so than guess.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 697417f9, build\classes, compiled 30 Aug 04:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-173"></a>

### MT-173 - 2026-08-24 - The diagram strip offers Fix, not Start, when the setup has errors

**Disposition:** fixed validated
**From:** OB-090
**Written:** 2026-08-24

You need a setup with at least one ERROR in it. The quickest is to take a station's name away in the
autonomy editor - an unnamed station is an error - or use the four you already have.

1. Load the configuration. With errors present, the button at the right of the diagram strip should
   read **Fix it**, in amber, where it used to read Start Autonomous Operation in green.
2. Press it. The autonomy editor should open at the first thing in the list.
3. Fix the errors. The button should go back to Start on its own, without reloading anything.
4. Start autonomy. While it is RUNNING, the button must show Graceful Stop as it always did - and if
   an error appears while trains are moving, it must STILL show Graceful Stop. Losing the brake in
   that moment would be much worse than the bug being fixed here.
5. With warnings but no errors, the button must say Start and starting must work. Warnings never
   block.
6. The count beside it should read "N errors, M warnings - K on this page".

#### Comments

**Claude, 2026-08-24.** Adam, OB-090: "the autonomy error count is 4 but shows as 8.  also, the fix it
button is not shown, rather just start autonomy when the config had worked before." And then, when I
had written the wrong thing into the ticket: "wrong, the setup refuses to run when there are errors.
it runs on warnings, but on errors it should say fix it."

He was right and my ticket was wrong. `refuseAutonomyStartWhileBroken` has refused to start on any
ERROR finding since OB-057, and all three ways of pressing Start go through it. What had never
happened is that the things which OFFER to start it were told: the strip mirrored the Start button
whenever it was enabled, and enabled says nothing about the checks.

There were two questions where there should be one. `hasBlockingProblems()` asks whether the GRAPH
can be built; the checks ask whether the SETUP can be run. Four unnamed stations are four of the
second and none of the first. `AutonomySession.errorCount()` is now the single answer both ask.

**Adam, 2026-08-24 (triage).** Works, with notes.

works, but I would like a shaded background in light yellow when fix it is active.  then change the color of the fix it button back to white.

**Claude, 2026-08-24.** Done, and it is the better call. Colouring the button left the one warm thing
on the panel inside a control the eye reads as a button first and a warning second; shading the band
says the same thing about the whole strip, which is what it is actually about.

The band is a light yellow rather than the amber the button had - it runs the full width of the
diagram, so it has to sit under black text without becoming the loudest thing on screen. The findings
count keeps its own stronger amber and red, which still read against a tint.

The strip is repainted when the error count changes as well as when the page does, since errors appear
and go without anything else about the strip moving.

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-174"></a>

**From [OB-090](issues.md), filed 2026-08-24 - "autonomy error count inconsistent, fix it button doesn't show".** The entry as it stood when it was picked up:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 18:08  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 17:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

in the current config, the autonomy error count is 4 but shows as 8.  also, the fix it button is not shown, rather just "start autonomy" when the config had worked before.

**Claude, 2026-08-24. Two halves, and only one was arithmetic.**

**The count was right and the label was wrong.** The strip shows ONE number, deliberately -
`totalErrors + totalWarnings` - under a red foreground when any of them are errors. With four of each
it says 8, and there is nothing in the words to stop that being read as the error count. The comment
beside it argued that splitting "says nothing a reader can act on differently"; you read it as errors,
and you wrote the application, which settles that. It now reads `4 errors, 4 warnings - 3 on this
page`.

**The Fix button is a design question, so it is left for you.** It appears when
`session.hasBlockingProblems()`, and that asks the GRAPH only - scissors crossings, unaddressed
switches, unpaired links. Your four errors come from the CHECKS - unreachable stations, closed runs,
unnamed points - which are ERROR severity in the lists but do not block a build. So the setup really
does still load and run, which is why you were offered "start autonomy", and it is also why the
config "had worked before".

Three ways to go, and I did not pick one because each trades something real:

1. **Leave it.** The errors are listed, and clicking one opens the editor at that spot - which is what
   the hint under the list says. Nothing is hidden; it just is not a button.
2. **Offer Fix instead of Start whenever there are ERROR findings.** Matches what you expected. Costs
   you the ability to start a setup that has an error you have decided to live with - and you have
   run this one with them.
3. **Offer both.** Truthful, and one more control on a panel that already has several.

My inclination is 2 if you consider an ERROR finding something that should always be dealt with before
running, and 1 if some of them are ones you knowingly ignore. That is a question about your railway
rather than about the code.

**Adam, 2026-08-24.** "090- wrong, the setup refuses to run when there are errors.  it runs on
warnings, but on errors it should say fix it."

**Claude, 2026-08-24.** He is right and the three options above were built on a false premise of mine.

I wrote that these errors "do not block a build, so the setup really does still load and run". It does
load. It does not RUN: `refuseAutonomyStartWhileBroken` has refused to start autonomy on any ERROR
finding since OB-057, and all three ways of pressing Start - the button, the strip's mirror, the
station right-click - go through it. I had read `hasBlockingProblems()`, seen that the check errors do
not appear in it, and stopped there without looking at the press path. The question was never open;
option 2 was already the law of the application, and asking Adam to choose it was asking him to
re-decide something he had decided in OB-057.

**What was actually broken** is that the things which OFFER to start it were never told. Two questions
existed where there should be one:

- `hasBlockingProblems()` - can the GRAPH be built? Scissors crossings, unaddressed switches, unpaired
  links.
- the checks - can the SETUP be run? Unnamed stations, unlabelled stations, duplicate locomotives, no
  stations at all.

Four unnamed stations are four of the second and none of the first, so the strip showed a live green
Start over a setup that answered every press with a dialog. That is the OB-057 shape at a third site.

**Fixed in `8ea781fe`.** `AutonomySession.errorCount()` is now the one definition, and both the refusal
and the strip ask it. The strip offers "Fix it" in amber, going to the editor at the first finding.
Stop still wins - an error appearing while trains are running is the moment stopping matters most.
Warnings do nothing, per the second half of Adam's sentence. [MT-173](tests.md#mt-173).

### MT-174 - 2026-08-24 - A page renamed and renamed back, through the menu

**Disposition:** fixed validated
**From:** OB-092, and Adam's request for a real round-trip test
**Written:** 2026-08-24

This one is mostly automated now - see the note below - but the automated version cannot press a menu
item, so the gesture itself is still worth doing once.

1. Pick the page with the most autonomy settings on it. Note a few of them: a station name, a length,
   a protecting signal, a train placement.
2. Right-click the page tab, Rename, and give it a new name.
3. Everything you noted should still be there, on the renamed page. Nothing on any OTHER page should
   have changed - particularly not train placements, which is how OB-092 announced itself.
4. Rename it back.
5. Everything should still be there again. MT-135 was reported as "renaming the page back did not
   restore the stations", so the way back is where a loss becomes visible.
6. Also rename a page to a name that is just a number, like `5`, while another page holds id 5. That
   is OB-092 exactly.

#### Comments

**Claude, 2026-08-24.** Adam: "Do you have tests that try to load an entire config, and then trigger a
rename via the same function that the UI calls, and then rename it back and test along the way?  This
is the only way to catch bugs across these complex types of features."

We did not, and he is right about why it matters. The rename tests called
`AutonomyCompanionStore.renamePage` and `LayoutDiagram.writeLayoutIndex` themselves, one layer below
the sequence, in an order the test author chose - which reads as coverage and is not, because a test
that supplies its own order agrees with itself no matter what the application does.

The sequence now lives in `LayoutPageEdit.renameOrDuplicate`, which is what the menu item calls, and
`testRenameRoundTripThroughTheUIPath` calls the same thing with the same arguments. It compares every
square on the page and every square on every other page, before, after, and after the way back.

**It earns its place by mutation.** Putting each historical defect back one at a time - not telling
the store about the rename (OB-049), not telling the index (MT-135), reconciling on the way out
instead of saving without - fails it every time, and the clean code passes.

**What it does not cover** is the four refusals above that call: an open editor, running trains, a
remote layout, a name already taken. Those raise dialogs and stay in the window. They are decisions
about whether to ask; everything about what then happens is covered.

**Adam, 2026-08-24 (triage).** Does not work.

Renaming to an ID no longer causes issues.  However, renaming a page with autonomy cofig still wipes it and adds errors.  Refer to the current state.

**Claude, 2026-08-24.** Fixed - the full account is on [MT-135](#mt-135), which reported the same
thing from the other end.

Short version: the rename is correct and the capture that runs after it was not. `resetAutonomySession`
writes the running layout's state back into the configuration using page names the rename has just made
stale, so every placement was recorded twice - once under each name - and a locomotive in two places
makes autonomy refuse the whole setup.

The test on this entry passed throughout, which is the part worth keeping. It stopped at the rename
and never ran what the window does next, and it compared stored settings rather than running the
checks. It now does both.

*Run against commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-175"></a>

### MT-175 - 2026-08-24 - Capture fills the timetable, and the table shows it filling

**Disposition:** fixed validated
**From:** MT-149, OB-097
**Written:** 2026-08-24

1. Load a configuration. Press **Capture Loc Commands** so it is lit, BEFORE starting - it is refused
   while trains are running, deliberately.
2. Start autonomy and watch the Timetable tab. Rows should appear as trains are dispatched, without
   you clicking anything. That is the part that was broken: the rows were being recorded all along
   and the table was never repainted, so it looked as though capture did nothing.
3. Watch the locomotive status panel under Locomotive Commands as a route finishes. It should stop
   saying the route is active on its own. That is OB-097, and it is the same fix.
4. Switch capture off and run again: no new rows.
5. Load a different configuration, then repeat step 2. Callbacks live on the layout object and a
   configuration load replaces it, so this is the case that would break if only one of the two
   attachment points were wired.

#### Comments

**Claude, 2026-08-24.** Adam: "Make sure you validate via REALISTIC tests for the timetable issue that
still persists." The root cause and why the old test could not see it are written up in
[MT-149](#mt-149).

Two automated tests, and they cover different halves on purpose.
`testTimetableCaptureThroughARealRun` runs the sanity configuration under real autonomy and asserts
both that entries appear AND that something was told - the second assertion is the one no model-level
test could make. `testTheWindowAttachesItsRefreshCallback` reads the source and fails the build if
TrainControlUI stops calling `attach`, which is crude and is the only thing that would have caught the
original deletion: the mechanism was fine, the caller was gone, and a behavioural test can always
attach the callback itself and end up testing its own wiring.

Mutation-checked both ways. Deleting the two call sites - replaying `d8db4879` exactly - fails the
guard. Disabling the mechanism fails the run test on "nothing was told" while the capture assertion
still passes, which is the evidence that the two halves are genuinely independent.

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-176"></a>

**From [OB-097](issues.md), filed 2026-08-24 - "synchronization issue with autonomy UI".** The entry as it stood when it was picked up:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 21:11  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

a route finished, but the loc status panel under Locomotive Commands still indicated an active route.

**Claude, 2026-08-24.** Not a synchronisation problem, and the same cause as the timetable one.

The locomotive status panel redraws when the layout announces that a path has started or finished. The
registration that made it do so lived inside the method that built the GraphStream graph window, which
wanted the same notification, and was deleted with that window in `d8db4879`. So the panel showed
whatever it last showed - a route that had finished went on reading as active until something else
happened to repaint it.

The timetable lost the other of that callback's two calls, which Adam reported separately as capture
not working at all. Fixed together: `AutonomyRefreshCallback.attach`, called from the window after
every `parseAuto`. See [MT-149](tests.md#mt-149) for the full account and
[MT-175](tests.md#mt-175) to check it.

### MT-176 - 2026-08-24 - What a run did survives renaming or deleting a page

**Disposition:** fixed validated
**From:** DW-A1, DW-C1 (the day review)
**Written:** 2026-08-24

This one matters more than most on this list: the defect it covers could put a train into an occupied
block, and I introduced it myself a few hours before it was found.

1. Load a configuration and start autonomy. Let at least one train complete a run, so it ends up
   somewhere other than where it started.
2. Stop autonomy. Note where the trains are now.
3. Rename any page.
4. The trains must still be where step 2 left them - on the diagram, and after closing and reopening
   TrainControl. If any train is back at its pre-run position, this has regressed.
5. Repeat steps 1-3 but DELETE a page instead of renaming one. The deleted page's settings must stay
   gone; the other pages' trains must stay where the run left them.

#### Comments

**Claude, 2026-08-24.** DW-A1, and it is worth being plain that this was my own defect, found by the
day review a few hours after I wrote it.

The rename fix marked the session's pages stale and had `captureFromLayout` refuse while that held. My
reasoning was "a rename is refused while autonomy is running, so there is nothing in that gap the
running layout knows that the store does not". The gap is not DURING a run - it is after one.

`captureFromLayout` is the only thing that folds a run's outcome into the configuration, and it has
exactly three callers: loading another configuration, the save on the way out, and the reset after a
diagram edit. **Stopping autonomy captures nothing.** So between the end of a run and one of those
three, where the trains ended up lives only in the running Layout - and a rename triggers the third
one, which is precisely the capture I had taught the session to refuse. Run, stop, rename, and every
placement the run produced is discarded, on every page rather than only the renamed one.

**Why that is worse than losing settings.** Occupancy is derived from placements - `Point.isOccupied`
is `currentLoc != null`, and `isPathClear` never consults the s88 - so the rebuilt configuration puts
the trains back where they were before the run, and pressing Start can route one into a block that is
physically occupied.

**Fixed by an order, not a guard.** The capture now happens BEFORE the page changes, while the naming
and the store still agree, and `renamePage` carries the captured entries across with everything else.
The stale flag stays as a backstop for the capture that follows.

**DW-C1, the unswept sibling.** Deleting a page had the same shape in reverse: `deletePage` forgets the
settings, and the capture that ran afterwards wrote them straight back from the old session's naming -
so a delete looked as though it half worked. It captures first now too.

`testARunSurvivesAPageRename` covers both orders in one test on purpose: capturing first keeps the
run, capturing afterwards loses it. Either assertion alone is a statement about one arrangement;
together they say the difference is the ORDER and nothing else, which is the whole claim.

**Adam, 2026-08-24 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-177"></a>

### MT-177 - 2026-08-24 - Four menu and window details from the testing round

**Disposition:** fixed validated
**From:** OB-093, OB-094, OB-095, OB-096
**Written:** 2026-08-24

1. **The autonomy checkbox on a Central Station layout.** Switch to a Central Station layout so the
   Auto tab greys out. The "Autonomy Controls" checkbox on the track diagram strip must go too - it
   used to stay behind, because switching greyed the tab and never refreshed the strip.
2. **The same checkbox with a setup available but not loaded.** It should be hidden then as well.
   Same one-line cause.
3. **Switch to Central Station Layout, when already on one.** The menu item must be greyed. It was
   enabled for as long as the diagram grid took to rebuild - which is exactly when you would look at
   it - because the code that greys it was queued behind that rebuild.
4. **"Autonomy needs a layout on this computer".** On a Central Station layout, open the autonomy
   menu: that line should now be a live item offering to download the layout, and pressing it should
   start the same download the Layouts menu does. On a LOCAL layout with no setup, it must stay the
   old greyed sentence - an offer that cannot help is worse than none.
5. **The "no available paths" window.** Its text area should be white, and its text the same size as
   other windows rather than smaller.

#### Comments

**Claude, 2026-08-24.** Four entries from Adam's testing round, grouped because they are all one-line
UI corrections and he has asked before not to bunch unrelated bugs into one test - these are related
by being the same kind of thing in the same sitting, and each step above names its own OB.

Points 1 and 2 are one fault: `initializeTrackDiagram` called `resetAutonomySession`, which greys the
Auto tab, and never called `refreshAutonomyPrompt`, which is what the strip's checkbox follows. Which
of the two symptoms appeared depended only on what state the checkbox happened to be in.

Point 5's font is worth a note. It had been "fixed" once already by asking the look and feel for its
label font, on the reasoning that this makes "the standard size of all other windows" true by
construction. It does not - FlatLaf's label font is smaller than what this application sets on its own
components - so the window came out smaller than its neighbours and Adam asked twice. Ninety-eight
components in the gui package use size 14 against one at 12, so 14 is the standard here and that is
what it uses now, keeping the family from the look and feel.

**Adam, 2026-08-24 (triage).** Works.

Filed from this test: OB-104 (bug - autonomy tab not greyed out when using CS layout).  They are in `issues.md` until they are picked up.

*Run against commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 25 Aug 01:25 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 25 Aug 01:40 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 25 Aug 01:40 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 25 Aug 01:40 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**From [OB-093](issues.md), filed 2026-08-24 - "autonomy checkbox visible when it shouldn't be".** The entry as it stood when it was picked up:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 21:05  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when using a CS2 layout and the autonomy tab is greyed out, the autonomy checkbox is still visible on the track diagram page.

**Claude, 2026-08-24.** Fixed, together with [OB-095](#ob-095) - they are one fault seen from two
states.

The Autonomy Controls checkbox on the diagram strip is shown or hidden by `AutonomyOverlayToggle`'s
`setLoaded`, and every legitimate caller reaches that through `refreshAutonomyPrompt`.
`initializeTrackDiagram` - which runs for "Switch to Central Station Layout", "Choose Local Data
Folder" and "Download CS Layout" - called `resetAutonomySession`, which correctly greys the Auto tab,
and never called `refreshAutonomyPrompt`. So the checkbox kept whatever state it had before the
switch. Which of the two bugs you saw depended only on what that state happened to be: visible beside
a greyed tab is this one, visible with a setup available but not loaded is OB-095.
`refreshAutonomyPrompt` already answers both correctly; it simply was not being asked.

**Claude, 2026-08-24, later.** The second half is done too.

That notice was a true sentence and a greyed one, which leaves the reader knowing what is wrong and
not what to do - and the thing to do lives in a different menu, under a name that says nothing about
autonomy. Where somebody reads the sentence is where the offer belongs, so the item is now live and
runs the same download the Layouts menu does.

**Only when a download would actually answer it.** That branch is taken for other reasons as well - no
viewer panel yet, a session that failed to open - and an offer that cannot help is worse than the
greyed line it replaced, because pressing it teaches the reader that the notice lies. So it asks
whether the layout is the station’s copy first, and falls back to the old dead sentence otherwise.

also, the "autonomy needs a layout stored on this computer" notice should be clickable and allow the user to download one.

**From [OB-094](issues.md), filed 2026-08-24 - "switch to central station layout remains selectable".** The entry as it stood when it was picked up:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 21:07  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

switch to central station layout remains selectable even when already using a central station layout.  it should be greyed out.

**Claude, 2026-08-24.** Fixed. The enabling rule was already right and never ran in time.

`repaintPathLabel` greys this item whenever the layout is not local, which is exactly what this asks
for. It was being called from inside a submission to `LayoutGridRenderer`, a single-thread executor
whose job is rebuilding the diagram grid - so it queued behind that rebuild. Switching to a Central
Station layout is itself the biggest producer of that work, so the item stayed at its designer default
of enabled for as long as the grid took, which is precisely when somebody would look at it.

Called directly now. Safe from any thread: `repaintPathLabel` wraps its whole body in `invokeLater`,
so it still touches Swing only on the event thread - it simply no longer waits for the grid.

**From [OB-095](issues.md), filed 2026-08-24 - "show autonomy controls checkbox visibility".** The entry as it stood when it was picked up:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 21:08  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when autonomy is available but not loaded, the show autonomy controls checkbox should be hidden.

**Claude, 2026-08-24.** Fixed by the same one-line change as [OB-093](#ob-093), where the reasoning is
written out. Same missing call, different starting state.

**From [OB-096](issues.md), filed 2026-08-24 - "background of no available paths window".** The entry as it stood when it was picked up:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 21:10  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 20:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

make the text area of "no available paths window" with the text be white.

**Claude, 2026-08-24.** Fixed, and with it the font size that [MT-163](tests.md#mt-163) and
[MT-160](tests.md#mt-160) asked for twice.

FlatLaf derives a non-editable text pane's background from the panel behind it, which is a grey; every
other text area in this application sets white explicitly rather than trusting the look and feel.

The font is the more interesting half. It had already been "fixed" once, by taking UIManager's
`Label.font` whole, on the reasoning that asking the look and feel makes "the standard size of all
other windows" true by construction. It does not: FlatLaf's label font is SMALLER than the size this
application sets on its own components, so that change made this window smaller than its neighbours
rather than the same as them - which is why the same request arrived twice. Measured instead:
ninety-eight components in the gui package use Segoe UI at 14 against one at 12, so 14 is the standard
here. The family still comes from the look and feel; only the size is pinned.

---

<a id="mt-178"></a>

### MT-178 - 2026-08-25 - Importing a legacy autonomy file onto a layout with none

**Disposition:** fixed validated
**From:** OB-106
**Written:** 2026-08-25

The critical one from the golden-data load. Do this on a layout that has NEVER had autonomy set up.

1. Autonomy menu, Import, choose an old `autonomy.json`. When it asks for a name, give one.
2. The import summary should appear as before.
3. **The setup should then be open and usable** - the autonomy editor available, the controls live,
   the configuration you named present and selected. Before this fix there was nothing to view and
   everything was greyed.
4. Check a placement and a home came across, not just the station names. That is the half that was
   being dropped.
5. Then repeat on a layout that ALREADY has autonomy: the import must still land in the configuration
   in use, not make a new one.

#### Comments

**Claude, 2026-08-25.** Adam, OB-106: "when a track diagram has no autonomy, and we import from a
legacy file, there is no autonomy setup to view.  one should be created so that it's properly loaded
into.  autonomy editor, controls are all greyed out."

`importConfiguration` asks for a configuration name for BOTH kinds of file, and then the legacy branch
returned without ever using it. The import writes the shared half - names, stations, lengths - into
the store, which needs no configuration; but the per-point half - placements, homes, termini, facings
- goes through `setPointProperty`, which addresses the ACTIVE configuration. On a layout that has
never had autonomy there is no active configuration and none to make active, so that half went
nowhere and `loadAfterImport` was handed null.

The dialog still reported everything it had matched, because matching is what it counts. So it looked
like a successful import of a setup that then could not be opened.

The name is passed through now, and a configuration is created when - and only when - there is not
one already. A layout that has configurations keeps importing into the one in use, which is what
importing onto an existing railway means.

**One thing this nearly shipped with.** My first fallback for an empty name called
`I18n.t("autosetup.ui.defaultConfigurationName")`, whose value is `Autonomy {0}` - so a configuration
would have been created literally named "Autonomy {0}". There is already a `suggestedConfigurationName()`
that fills in the next free number and avoids one already in use, which is what it uses.

**Not yet run.** Adam is loading golden data into `cs2_sample_layout`, which is both his live railway
and the test fixture, so nothing that builds a model has been run against this. It compiles; it has
not been exercised.

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 25 Aug 01:25 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-179"></a>

**From [OB-106](issues.md) - "importing autonomy from legacy file doesnt work".** The entry as filed:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-25 00:30  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when a track diagram has no autonomy, and we import from a legacy file, there is no autonomy setup to view.  one should be created so that it's properly loaded into.  autonomy editor, controls are all greyed out.

### MT-179 - 2026-08-25 - Nothing that needs a Central Station is offered without one

**Disposition:** fixed validated
**From:** OB-098, OB-100, OB-101, OB-104
**Written:** 2026-08-25

Start TrainControl with NO Central Station reachable, on a local layout.

1. **Layouts menu:** "Switch to Central Station Layout" must be greyed. It fetches the moment it is
   pressed, and pressing it with nothing answering replaces the layout in use with one it cannot read.
2. **"Download Central Station Layout Files"** must be greyed too, for the same reason.
3. **Backup TrainControl Data** on a Central Station layout must NOT ask whether to include the
   station's files when the station is unreachable - asking is a promise to fetch.
4. Then connect a station and check all three come back.

**The serious one.** On a layout read from the Central Station:

5. The **Auto tab must be greyed**, and Start Autonomy must refuse even if you reach it another way.
   Adam: "I was able to start autonomy, even though no trains on graph and even though layout was
   being read from CS.  a defunct train started running, and switches were set."

6. **Return Home**, then while trains are moving: the **Capture Loc Commands** button must be greyed,
   not merely refuse when pressed.

#### Comments

**Claude, 2026-08-25.** Four of Adam's entries, all the same shape, which is why they are one test.

OB-104 is the one that matters. `refreshAutonomyTabState` computed `loaded` as
`getAutonomySession() == null || activeDiagramConfiguration != null` - and on a Central Station layout
there IS no session, so the first half was true and the tab opened on whatever auto layout happened to
be in memory: a blank default, or one left over from a local layout. The comment that stood there
argued the JSON window is still how autonomy is set up where there is no local copy, so validity was
the only answer available. That reasoning is what let a defunct configuration reach real switches.

It is also not what the application says anywhere else - the autonomy menu's own notice reads
"Autonomy needs a layout on this computer". The tab agrees with that sentence now, and the START
refuses independently, because greying a tab is an affordance and this is the action.

OB-098, OB-100 and OB-101 are the affordance-versus-guard distinction again, which is now the fourth,
fifth and sixth time it has come up in two days. The capture toggle DID refuse while trains were
returning home; a live button that answers with a dialog reads as a control you may use and used
wrongly, where a greyed one says the railway is busy.

**Not yet run**, for the same reason as [MT-178](#mt-178).

**Adam, 2026-08-25 (triage).** Does not work.

switch to central station layout is NOT greyed out in debug/simulate mode.

**Claude, 2026-08-25.** Fixed, and my first attempt was right about everything except what the
question meant.

I asked `getNetworkCommState()`. That reports whether the last SYNC succeeded - and a sync reads its
files through `CS2File`, which reads a local layout folder perfectly happily. So on a machine with a
local layout a simulated session syncs successfully, sets that flag, and calls itself connected to a
station that is not there.

The model now records that it is simulating, and the menus ask both halves. Worth the extra field
rather than inferring it: "the sync worked" and "there is a station" are different facts, and this is
the second time treating them as one has produced a control that offers something impossible.

His other half - "the unreachable failure error works" - is [OB-103](#mt-180), which is unaffected.  

the unreachable failure error works.

*Run against commit 8db330da, build\classes, compiled 25 Aug 01:25 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**From [OB-098](issues.md) - "switch to cs layout is possible even when not connected to a cs".** The entry as filed:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:44  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

switch to cs layout is possible even when not connected to a cs.  also, backup up files from cs should not be offered in this case.

**From [OB-100](issues.md) - "cs download layout menu item toggle".** The entry as filed:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:48  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

download central station layout files should also be greyed out when not connected to a cs

**From [OB-101](issues.md) - "capture locomotive commands toggle".** The entry as filed:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:50  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

capture locomotive commands button can still be toggled while trains are returning home

**From [OB-104](issues.md) - "autonomy tab not greyed out when using CS layout".** The entry as filed:

**Kind:** bug  
**Raised from:** MT-177 (Four menu and window details from the testing round)  
**Filed:** 2026-08-24 23:53  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

I was able to start autonomy, even though no trains on graph and even though layout was being read from CS.  a defunct train started running, and switches were set.

**Adam, 2026-08-28 (triage).** Does not work.

1. is still not greyed when not connected / in debug mode
2. is still not greyed when not connected / in debug mode

rest not yet tested

*Run against commit 309b984f, build\classes, compiled 28 Aug 01:24 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.**

The two items you found still ungreyed - Switch to Central Station Layout, and Download Central Station Layout Files - were reworked on 2026-08-29 under OB-126 and OB-128, after this run. guardLayoutMenu now leaves both out of the re-enable pass rather than switching them back on, and openCS3App got a predicate of its own. So this result predates the change and the two items want looking at again; items 3 onward were never reached and still want a first look.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 697417f9, build\classes, compiled 30 Aug 04:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-180"></a>

### MT-180 - 2026-08-25 - Three things the interface was not saying

**Disposition:** fixed validated
**From:** OB-102, OB-103, OB-105
**Written:** 2026-08-25

1. **The timetable names stations, not compass bearings.** Capture a few moves that involve a square
   where trains can turn round. The Start and Destination columns should read "Platform 3", not
   "Platform 3 (northbound)".
2. **A layout that will not read says so.** Switch to a Central Station layout, or download one, in a
   state where nothing comes back. A dialog should explain that no diagram could be read and what to
   check. Before, it only reached the log.
3. **The IP prompt wears the application icon.** Start TrainControl with no saved IP so the address
   prompt appears: its title bar should carry the TrainControl icon, not the default Java one.

#### Comments

**Claude, 2026-08-25.** Three of Adam's entries, grouped because each is the interface failing to say
something rather than doing something wrong.

OB-102: the timetable printed `Point.getName()`, and a square where trains may turn round is emitted
as several Points named "Platform 3 (northbound)" and so on. That distinction is machinery - it
matters in a running log, where which copy a train is on decides what it can do next - and it is noise
in a list whose job is "this train went from here to there". `baseNameOf` already existed for exactly
this. The suffix is untouched everywhere else.

OB-103: the end of switching to a Central Station layout, or downloading one, when nothing comes back.
It logged and returned. The log is for afterwards; the person who pressed the button is still sitting
there watching nothing happen. Worded for that moment rather than reusing the log-s own sentence -
"no layout loaded" is a statement about the model, and what is needed is which of the two likely
reasons it is.

OB-105: the address prompt is the FIRST thing shown, before the main window exists, so it has no owner
to inherit an icon from and Swing gives it the default coffee cup. Set quietly - a missing icon
resource must not stop the application asking for an IP address.

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 25 Aug 01:40 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*


**From [OB-102](issues.md) - "directions shown in timetable stations".** The entry as filed:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:51  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

timetable stations show (northbound) and (southbound) etc.

**From [OB-103](issues.md) - "failing to read layout from CS shows no error in UI".** The entry as filed:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:51  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

if we get 2026-08-24 23:51:15.936 Model error: no layout loaded., there is no UI error notice shown.

**From [OB-105](issues.md) - "no traincontrol window icon in IP prompt".** The entry as filed:

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-25 00:11  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

no traincontrol window icon in IP prompt

---

<a id="mt-181"></a>

### MT-181 - 2026-08-25 - Show Inactive Labels

**Disposition:** fixed validated
**From:** FR-023
**Written:** 2026-08-25

1. **Autonomy menu**: there should be a **Show Inactive Labels** item, ticked.
2. Load a configuration and show the autonomy overlay. Untick it. Only the stations autonomy can
   actually send a train to should keep their names; everything else loses its caption, and nothing
   else about the diagram changes - the tiles, the trains and the overlay stay exactly as they were.
3. Tick it again: every name comes back, immediately, without touching the diagram.
4. Restart TrainControl. The setting should be where you left it.
5. With it OFF, load a DIFFERENT configuration - one where a different set of stations is reachable.
   The names should follow that, not stay as they were.
6. Turn the autonomy overlay off entirely: all captions go, as before. This setting only decides
   which ones come back when it is on.

#### Comments

**Claude, 2026-08-25.** Two things worth knowing about how it decides.

**"Cannot be chosen" is asked of the RUNNING layout, not the setup.** A square can be a station in
the setup and still be somewhere no train will ever be sent - not a destination, or on a page left out
of autonomy - and it is the running answer that says what the diagram is about to do. With no running
layout nothing is inactive yet, so everything is shown.

**A square split into several Points shows its name if ANY of them is a destination.** Such a square is
one place to the person looking at it, and hiding its name because one of its directions is a dead end
would be a lie about the other.

**Adam, 2026-08-25.** "Flipping the new setting has no effect- labels on inactive terminuses do not
go away in the track diagram"

**Claude, 2026-08-25.** Two faults, and the second is the one that matters.

**The predicate was wrong.** I used `Point.isDestination()`, which is a different flag from the
question being asked. "Can autonomy choose this station" is answered by the standing bars in
`barredFromAutonomy`: inactive, reversing, not an auto destination. An inactive terminus passes
`isDestination` - so the very stations Adam wanted gone were the ones being kept. It asks
`Layout.isChoosableByAutonomy` now, which is that same method, made public rather than restated:
`barredFromAutonomy`'s own comment says two copies of its list would be two answers to one question,
and the diagram was about to become the third caller with its own.

**And a third place decided visibility.** `showStaticAutonomyLayer` set every caption to its own
`show` parameter wholesale, ran whenever the overlay was drawn, and overwrote both other sites without
asking anything about the station - so the setting genuinely had no effect. All three sites go through
the one rule now, and `testEveryCaptionVisibilityDecisionAsksTheRule` fails if a fourth appears or if
that one reverts. Mutation-checked by putting the blanket setter back.

The visibility rule lives in one method that both the overlay switch and this setting ask, so the two
cannot disagree - the overlay still wins, and this only decides which captions come back when it is
on. Applied to labels already drawn rather than left for the next rebuild: the grid rebuilds for a
dozen reasons and none of them is this one, so otherwise the setting would appear to do nothing until
something unrelated redrew the diagram.

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 8db330da, build\classes, compiled 25 Aug 00:50 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-182"></a>

### MT-182 - 2026-08-25 - The signal window opens beside the diagram, not on it

**Disposition:** fixed validated
**From:** OB-107
**Written:** 2026-08-25

1. Open the autonomy editor and pick **the signal protecting this station** on a station square.
2. The window should open up and to the LEFT of centre, not over the middle of the diagram - the
   signals it is describing are outlined on the diagram behind it, and that is the point.
3. It should still be plainly over the TrainControl window rather than off in a corner of the screen.
4. Drag its bottom edge upwards. It should stop at the size it opened at: the buttons that finish the
   pairing must never be draggable out of sight.
5. Do the same on a maximised window and on a small one.

#### Comments

**Claude, 2026-08-25.** Adam: "the signal protecting this station pops up over the middle of the
diagram. see if you can offset it. also, the window can be resized below its minimum height."

Both, and the first was worse than a nuisance - the window's own text says the signals "are outlined
on the diagram behind this window", so centring hid the very thing it was telling the reader to look
at.

The placement is a public static function of two rectangles, `AutonomyEditorPanel.besideOwner`, so it
could be tested without opening a modal dialog. Two automated tests cover it, both mutation-proven:
returning the centred point fails them, and so does a dialog larger than the window it belongs to
being pushed off the top-left, which is the half of the clamp the ordinary case never exercises.

**Adam, 2026-08-25 (triage).** Does not work.

Still opens smack in the middle.

*Run against commit 96dd12d6.*

**Adam, 2026-08-26 (triage).** Does not work.

*Run against commit 309b984f, build\classes, compiled 26 Aug 00:29 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.**

Moved back to needs test. It was sitting at fixed unvalidated, which claims a fix is in place awaiting a check - you have now checked it twice, on 25 and 26 August, and it opens in the middle both times. A fix that has been disproved is not an unvalidated fix, and leaving it in that state kept it out of the ledger of outstanding work.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 697417f9, build\classes, compiled 30 Aug 04:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-183"></a>

### MT-183 - 2026-08-25 - The wait mark is a grey hourglass

**Disposition:** fixed validated
**From:** FR-024
**Written:** 2026-08-25

1. Switch to a track diagram page that has not been shown yet this session, so the tiles have to be
   decoded. While it loads there should be a large grey HOURGLASS where the turning arc used to be.
2. Watch it: the sand should run from the top bulb into the bottom over about three seconds, and the
   glass should then turn over and start again. There should be no frame where the sand jumps back up.
3. The same mark appears, smaller, in the busy dialog - the one shown while something long is running.
   Check it looks right at that size too.
4. Nothing should be drawn outside the space the mark is given, including while it is turning.

#### Comments

**Claude, 2026-08-25.** Adam: "change the very large spinner shown on top of loading track diagrams
to a large gray hourglass icon instead. animate if possible."

Drawn rather than shipped as a GIF, for the same reason the arc was: one less asset to scale for a
high-DPI screen and keep in step with the theme.

Two attempts at the shape were wrong and both were only visible in a picture - straight triangles read
as a bow tie, and adding a frame to settle it drew a rectangle with an X in it. The third has curved
sides. Five automated tests render it and measure the pixels, because a source-reading guard would
have passed on both bow ties.

Worth knowing what those tests do NOT cover, since it is the part you are best placed to judge: the
curve the sand level follows as it falls. Both bulbs read the same number, so the areas sum to the
same constant whatever curve it is, and no assertion here can see it. If the fall looks wrong to you,
that is a real report and nothing will have caught it.

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 96dd12d6.*

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 96dd12d6.*

**Adam, 2026-08-26 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 26 Aug 00:29 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-184"></a>

### MT-184 - 2026-08-25 - Cropping a locomotive icon

**Disposition:** fixed validated
**From:** FR-022
**Written:** 2026-08-25

**Adam, 2026-08-25, asked whether the tick box should be remembered between uses or default to on:**
"ok as is for now." So it stays unticked every time, and step 1 below is the current behaviour rather
than an open question.

**One step to add, from the review.** Give a locomotive a cropped icon. Then set its icon again,
tick Crop, and press **Cancel** in the crop window - and again with **Escape**. Nothing should change:
the locomotive keeps the crop it had, and the file behind it is still there.

Until this was fixed, cancelling set the full uncropped photograph as the icon and deleted the crop it
replaced. `cropLocIcon` returned null for "cancelled" and for "could not read the picture" alike, and
the caller fell back to the uncropped file for both - while the crop dialog's own javadoc said null
meant "leave everything exactly as it was, not a failure". The two were written the same day.

1. Right-click a locomotive and choose to set its icon. The file chooser should now carry a
   **Crop / pan the image** tick box, and it should be UNTICKED the first time.
2. Leave it unticked and pick a picture. Everything should behave exactly as it did before.
3. Now do it again with the box ticked. A crop window should open: the picture moves under a fixed
   frame, the frame is locked to the shape the icon is actually drawn at, and the slider or the mouse
   wheel zooms in up to eight times.
4. Drag the picture as far as it will go in each direction. The frame must stay completely covered -
   no blank edge, ever. Zoom right in, drag into a corner, then zoom all the way back out and check
   the same thing.
5. Try a TALL photograph, which is the case this exists for.
6. **Reset** returns to a centre crop. **Cancel** leaves the whole picture in use, as before.
7. OK, and check the icon on the main window. Then set another icon for the same locomotive and check
   the first crop file was cleaned up rather than left behind.
8. Your original photograph must be untouched - check its size and timestamp.

#### Comments

**Claude, 2026-08-25.** Adam: "add a crop / pan function to local locomotive icons at the time of
image selection."

The crop is written as a new PNG beside the locomotive database, never over your own file.

**A judgement call to confirm or overturn.** The tick box is OFF until you turn it on, which keeps an
untouched flow byte-for-byte what it was, at the cost of discoverability - you would not know the
feature existed unless you noticed the box. Making the dialog always open instead, with Cancel meaning
today's behaviour, is a one-line change. Say which you would rather.

Four automated tests cover the arithmetic - the output size and the clamp - because a view dragged off
the edge does not throw and does not leave a blank: it produces a smaller rectangle which is then
stretched, so the only symptom is a locomotive that looks slightly wrong. Both clamp mutations fail
them.

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 96dd12d6.*

---

<a id="mt-185"></a>

### MT-185 - 2026-08-25 - A page the layout cannot see, when you edit another one

**Disposition:** fixed validated
**From:** FR-018
**Written:** 2026-08-25

This one needs setting up deliberately, and it is worth doing once: it is the case that has cost you a
page's settings before.

1. With TrainControl closed, make one page of your layout unreadable - the simplest honest way is to
   set its `.cs2` file offline in OneDrive so it becomes a placeholder, or open it in another program
   that holds it.
2. Start TrainControl. That page will not load, and the log should say so.
3. Now rename, delete or duplicate a DIFFERENT page. Before anything happens you should be asked about
   the page that is missing, BY NAME, with three answers.
4. Choose **Keep them**. Finish the edit. Then make the file readable again and restart: the page
   should come back as ITSELF, with its stations, names and captions still attached - not as a new
   page with a blank setup.
5. Repeat, and this time choose **Cancel**. Nothing at all should change: the page you were editing
   should still be there under its old name, and the layout untouched.
6. Repeat once more with a page you really have deleted, and answer **They were deleted**. The log
   should say how many held settings were discarded.
7. Then the ordinary case, which is the one that matters most: with every page loading normally, do a
   rename, a delete, a duplicate and a combine. **You should never see this question.** If it appears
   when nothing is missing, that is a defect.

#### Comments

**Claude, 2026-08-25.** Adam, on the three options I offered: "if we are talking about orphaned data,
why not warn the user and then prune? maybe I am misunderstanding the distinction"

He was not misunderstanding it - he dissolved it. All three of my options assumed the application had
to guess whether a page was deleted or merely unreadable, and ranked themselves by how well each
guessed. It is the only participant that cannot tell. The person who just renamed a page knows.

So it asks, and the second answer does the half none of my options addressed: a genuinely deleted
page's settings were held under a retired id for ever, growing setup.json with data that could never
attach to anything again.

The question is asked BEFORE anything is destroyed, at all three doors. Offered at the index write it
would have come after the page file was already deleted, which is not a cancel at all.

Four automated tests, seven mutations. Two of them were rewritten after the first version of each
passed under its own stated mutation - a single absent page cannot tell a selective prune from an
emptying one.

**Adam, 2026-08-25 (triage).** Could not run this.

make a test case for this.

**Claude, 2026-08-25. Tried, and it is already covered - by two things at once.**

I wrote one: hold a page back, then rename and delete OTHER pages, then bring it back and check its
settings. It passed. Then it passed under the mutation its javadoc claimed - `withoutAbsentPages`
returning everything, so nothing is held - which killed four existing tests and not mine. A green test
whose stated mutation does not kill it is the defect this suite keeps finding, so I took it out rather
than leave it in the file looking like cover.

What the probe showed is why. Entries for a page that is not loaded are protected TWICE over, and each
protection alone is enough:

- they are lifted out of the store before any edit runs, and put back verbatim - which is what
  `testASaveWhileAPageIsAbsentLosesNothingOfIt` guards, and what the mutation above breaks;
- and their keys are stored by page ID, which cannot be translated to a name while that page is out of
  the index - so even unheld they do not answer to the square an edit is working on.

The edit itself adds no risk beyond the save that follows it, and that save is covered. Steps 1 to 3 of
this test - the dialog naming the missing page, with its three answers - still need you, because they
need a file that will not open and a restart.

**Disposition unchanged: still needs your run for the dialog.**

*Run against commit 11b9ded6, build\classes, compiled 25 Aug 18:03 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 12:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-186"></a>

### MT-186 - 2026-08-25 - Nothing about the railway changed when the last string key went

**Disposition:** fixed validated
**From:** FR-013
**Written:** 2026-08-25

The same shape as MT-171, for the second and last stage. Nothing here should LOOK different; the
point is to confirm it is not.

1. Open a layout with a real setup and check the diagram: station names, captions, lengths, portals,
   the lot.
2. **Directions especially.** Find a square where you have set which way trains may run - a crossing
   or a switch carrying more than one route - and check each route still shows the direction you gave
   it. This is the collection that changed.
3. Rename a page. Everything above should follow it.
4. Move a tile that has a direction on it in the layout editor. The direction should move with it.
5. Delete a page. Only that page's settings should go.
6. Restart, and check the whole thing again.

#### Comments

**Claude, 2026-08-25.** `tileDirections` was the last collection keyed by a string - "page:x,y#state,
index" - parsed by hand at every site that touched it. It holds a typed key now, and the six
string-keyed duplicate helpers that existed only to be deleted are deleted.

The suffix had been got wrong twice while it was a string: a removal that could never match, and a
loop that had to grow its own copy. Neither is expressible now.

Its test was found by breaking it: swapping the two route numbers on the way in passed all 64 tests in
the store's own class and all 9 in the settings matrix, because every fixture in the repository used
route 0,0 - a pair that reads the same either way round. Step 2 above is the hands-on version of the
test that closed that hole.

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 11b9ded6, build\classes, compiled 25 Aug 18:03 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-187"></a>

### MT-187 - 2026-08-25 - Return Home, after FR-001 became one rule

**Disposition:** fixed validated
**From:** OB-085, OB-086
**Written:** 2026-08-25

FR-001 - a station held back while another square is occupied - existed in three inequivalent forms.
It is one rule now, and one real defect fell out of consolidating them, so this wants driving on the
railway rather than reading.

1. Set up at least one station with a **blocked by** restriction naming a square that a train can
   actually be standing on. Run Return Home with that square clear: the plan should use the station.
2. Now with a train standing on the watched square: the plan should not send anything to that station
   until the train has moved.
3. **The one that changed.** Find a watched square that is drawn as more than one Point - a square the
   builder emitted several copies of - and that has NO feedback address. Stand a train on one copy
   that the restriction does not name. Return Home should now refuse the station; before this it
   planned the arrival and the railway then refused it, which is a fleet stopping half-staged.
4. Give two stations restrictions that name each other's square, and make them the homes of two
   different locomotives. Return Home should say **impossible** and name both locomotives, rather than
   grinding and reporting "no arrangement found".
5. Then remove ONE of the two restrictions and try again. It should now find a plan - park at the
   restricted one first, then the other. A refusal here would be wrong.
6. Finally, an ordinary Return Home on your railway with nothing unusual set up, to confirm none of
   this changed the normal case.

#### Comments

**Claude, 2026-08-25.** Three forms became two, and the third was simply wrong - the test oracle had
no departing-train exemption, so it would have failed a legal plan whose move IS the train leaving the
watched square.

The real defect is step 3. The planner used a shared sensor as a stand-in for the block and had no
block index at all, so on a watched square with no feedback it could not see a train on another copy
while the runtime could see it plainly. The planner was the LOOSER half, which is the dangerous
direction.

Step 4 is new: two homes each held back by the other are impossible from the graph alone, whoever is
standing where, and the search used to spend its whole budget to say "maybe". Step 5 is the control,
and it is the assertion that matters most - the last two things put into that scan were both wrong and
both looked obviously right.

**A decision for you, and one line of code either way.** The staging planner still treats two Points
sharing a feedback address as one piece of track for this rule; the runtime does not. They answer
differently on purpose and I left it alone, because collapsing it changes which stations Return Home
offers on a real railway. For collapsing: the runtime is the authority, since it is the copy that
actually refuses the leg. Against: the sensor rule is the planner's only protection against putting
two trains on one detection section, and watched squares - approach guards, yard throats - are exactly
where sensors get shared. A test pins it in both directions, so it cannot drift while you decide.

**Adam, 2026-08-25 (triage).** Could not run this.

Make a test case for this.  Also, make a test case for the route executed during autonomy, by having a long route and manually triggering autonomy to a desgination that has the same switches.

**Claude, 2026-08-25. Both asked for, both now measured rather than assumed.**

The SECOND one is written, and is the more important of the two: a long route, fired while nothing is
locked, with autonomy taking a path over one of its switches part way through. It is
`testARouteAlreadyRunningDoesNotThrowTheSwitchEither`. It exists because an independent reviewer found
the defect it catches a day after you asked for it - the guard was being asked once, before a command
loop that takes seconds. That is the wrong order of events and it is worth recording: the test you
asked for would have found it first.

The FIRST one is covered already, and I checked rather than saying so:

- **Steps 1 and 2**, the plain rule - a station held back while a watched square is occupied - are
  guarded three times over. Making the planner ignore restrictions entirely fails three tests in
  `testHomeStaging`.
- **Step 3**, the one that changed, is `testThePlannerSeesATrainOnAnotherCopyOfTheWatchedSquare`,
  mutation-checked against the exact clause the FR-001 consolidation added.

So there is no new test to write here. What still needs you is driving it on the railway: the planner
agreeing with the runtime is a property of the model, and the train actually stopping is not.

*Run against commit 11b9ded6, build\classes, compiled 25 Aug 18:03 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 697417f9, build\classes, compiled 30 Aug 04:16 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-188"></a>

### MT-188 - 2026-08-25 - The blocked-points picker names what it is offering

**Disposition:** fixed validated
**From:** OB-086
**Written:** 2026-08-25

1. Give a station a **blocked by** restriction naming a square.
2. Now take that square's NAME away - clear it in the autonomy editor, or delete the point name.
3. Open the blocked-points picker for that station again. The restriction should still be listed and
   still ticked, and it should be LABELLED - with the square's coordinates if it has nothing better -
   rather than appearing as a ticked box with nothing beside it.
4. Untick it and confirm it goes; tick it again and confirm it comes back.

#### Comments

**Claude, 2026-08-25.** The picker carries an entry it no longer offers, precisely so that a
restriction you set cannot vanish because the square lost its name - and then labelled it with the
name it had just established was absent, which renders as a blank tick box.

A source rule now pins all three of the picker's surviving filters, so this cannot be undone quietly.
Mutation-checked: putting the old label back fails it.

**Adam, 2026-08-25 (triage).** Works.

Filed from this test: FR-025 (feature request - visually choose station exclusion).  They are in `issues.md` until they are picked up.

*Run against commit 11b9ded6, build\classes, compiled 25 Aug 18:03 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-189"></a>

### MT-189 - 2026-08-25 - A route while a train is running

**Disposition:** fixed validated
**From:** the whole-application review pass of 2026-08-25 (AU-A2)
**Written:** 2026-08-25

The most serious thing found this round, and it wants driving on the railway because the fix refuses
something that used to happen.

1. Start autonomy and let a train take a path that sets some switches - any ordinary run.
2. While it is under way, go to the **Routes** tab and execute a route that throws one of the switches
   **in front of** that train. You should get a dialog naming the route and the accessory and asking
   whether to run it anyway. Say **Cancel**: nothing should move.
3. Run it again and say **OK**. It should run, and the accessory should move. This is the recovery
   path and it is the reason a plain refusal was wrong - see the comments.
4. Now execute a route that touches nothing on any active path - lights on, or a switch elsewhere. **It
   should run with no dialog at all.**
5. And a route that throws a switch the train has already gone PAST. **No dialog** - the train has
   finished with that track, and asking about it every time would be the guard crying wolf.
5b. Then, with a train simply STANDING at a platform and nothing routed over it, run a route that sets
   that platform's protecting signal to **red**. **No dialog, and it should just work** - red is what
   protection itself commands, so there is nothing to warn about. Now run one that sets the same
   signal **green**: that one should ask, because it is turning protection off with a train there.
6. Do steps 2 to 5 again from the route TILE on the track diagram, which is a separate door and asks
   the same question the same way.
7. Then the automatic door, which is the one that made this worth fixing. If you have an s88 trigger
   route left over from manual operation whose trigger sensor an autonomy train will cross, enable it
   and let a run go past. There is nobody to ask, so it should be **refused and logged** rather than
   throwing the switch - and the log line should name the accessory.
8. **The mid-route case, which is the one a review found and you ruled on.** Take a route with
   several commands and a delay on an early one - long enough to give you a few seconds. Start it from
   the Routes tab while nothing is running, so it begins with a clear railway and no dialog. Then,
   while it is still working through its commands, start an autonomy run that takes a path over one of
   that route's turnouts. You should get the SAME dialog, naming the route and the accessory, part way
   through. Say Cancel: the turnout must not move. Do it again and say OK: it should move, and the rest
   of the route should finish without asking again.
9. And the same thing with the route fired by an **s88 trigger** instead. There is nobody to ask, so it
   should stop setting accessories and log - no dialog at all.
10. Finally, with autonomy STOPPED, run all of those routes again. Every one should work exactly as it
   always has, with no dialogs.

#### Comments

**Claude, 2026-08-25.** Route execution and autonomy path locking each worked exactly as designed and
neither consulted the other. `configureAndLockPath` reserves every accessory on a path, commands it
and validates it - and a route then set the same accessory back, with no refusal and nothing said. The
train is routed off the path that was protecting it.

The diagram's route tile looked guarded and was not: that guard asks whether the tile's own accessory
is on an active path, and a route tile has no accessory, so it was checking nothing.

**Adam, later the same day: "conflicting routes should still be executable in case of a transient
accessory failure. Add a confirmation dialog to the UI similar to how individual clicks currently work
when an accessory has an active route."**

He is right, and the case is not obvious until it is said out loud: **a turnout that did not take its
command is exactly when somebody needs to set it, and exactly when it will be on a locked path** -
because the path is what commanded it. A guard with no way past it takes the recovery away at the
moment it is wanted.

So the shape is now: the two doors with a person at them ask, using the same dialog and the same
wording as clicking an accessory on an active route has always used; the s88 trigger door still
refuses, because nobody is there to ask. One method serves both human doors so they cannot drift.

Note what a refusal skips and what it does not. Only the ACCESSORY commands are held back - the
emergency stop, functions off, lights, locomotive speeds and any chained route still run. Refusing the
whole thing suppressed the stop, which is the one command in a route you least want silently dropped.

**And then: "be careful with auto disallowed routes to avoid regression. once a train passes, signals
on the route, but behind the train, should still be allowed to be changed by auto routes."**

Also right, and the first version was over-strict for exactly that reason. It asked whether the edge's
LOCK was still held - and with `atomicRoutes` on, which is what your configuration uses, the lock is
held for the whole path until the run ends, by design. So every accessory on the path was refused for
the whole run, including the ones the train cleared in the first thirty seconds. You have 39
s88-triggered routes.

Locking and clearance are different questions. The lock asks "may another train be routed here". The
guard asks "is there a train on top of this", and the railway already computed that: it is what decides
when an edge may be released when atomic routes are off. It simply was not computed when nothing was
going to be released. It is now, in both modes, by the same code.

**Claude, 2026-08-25, after the review.** Step 5b is there because the first version of the
protecting-signal half ignored the aspect entirely. It refused any route touching any protecting
signal of any platform with a train parked at it - with nothing locked anywhere - and because
accessories are skipped as a group, one such signal took every turnout in that route with it. On a
railway with 39 s88-triggered routes and trains standing at platforms, which is all the time, that is
most of your routes quietly not working. Review reproduced it.

**Adam, 2026-08-25, on what should happen when the conflict appears part way through a route:** "ask
me, at the two human doors."

An independent reviewer found that the guard was asked once, before the command loop - and that loop
sleeps between every pair of commands, so a dispatch that locked a path while a route was already
running was invisible to it. Their probe: the route committed at 24.209, autonomy locked the turnout at
24.711, and the route set it against the locked path at 26.764.

The guard is asked again before each accessory now, and when it trips the two human doors get the same
question they were asked before the route started. The answer holds for the rest of that route rather
than being asked per command. The s88 door stops without asking. Steps 8 and 9 are that.

**The exact logic, since you asked for it.** Restated in full, because it has moved three times since
you were first given it and a stale answer to "tell me the rule so I can check it is not too strict"
is worse than none.

An accessory command is refused when **either** of these is true at the moment it is about to be sent:

1. **It is on track a train is crossing.** The accessory is commanded by an edge that (a) is on a path
   a running locomotive holds, and (b) that locomotive's train has not yet cleared. Cleared means the
   train's LENGTH has gone past it, not its front - so a turnout under the middle of a train is still
   guarded. Where no tile lengths are configured the railway treats an edge as clear once the front
   passes, which is the same trade it has always made for unlocking.
2. **It would take protection off a platform with a train at it.** The accessory is a protecting signal
   of a square a locomotive is currently standing on, AND the command would set it to something other
   than red. Setting such a signal red is what protection itself commands, so it is never refused.

Asked **again immediately before every accessory command**, not once before the route starts - a route
takes seconds and a dispatch can lock a path while it is part way through.

What happens when it trips depends on who is there. At the routes tab and the diagram's route tile you
are asked, and your answer holds for the rest of that route. At the s88 trigger door there is nobody to
ask, so it stops setting accessories for the rest of that route and logs. Either way, everything in the
route that is not an accessory - the emergency stop, functions off, lights, locomotive speeds, chained
routes - still runs.

Steps 4, 5, 5b and 10 are the controls. A guard that simply refused routes during autonomy would pass
steps 2, 6, 7, 8 and 9 and be useless.

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 11b9ded6, build\classes, compiled 25 Aug 18:03 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-190"></a>

### MT-190 - 2026-08-25 - Signals when a train is dispatched by hand

**Disposition:** fixed validated
**From:** the three-day review pass of 2026-08-25 (AU-B7)
**Written:** 2026-08-25

1. With nothing running, place a train by hand at a platform that has a protecting signal. The signal
   should NOT move - that silence is deliberate, and is what stops a setup gesture driving real
   signals.
2. Now right-click a DIFFERENT train somewhere else and dispatch it by hand to somewhere else again.
3. The moment that dispatch starts, the first platform's signal should go red. Before this it stayed
   green for the whole dispatch, with a train standing at it.
4. Check the same thing happens when you Start Autonomy instead, and when you Execute Timetable -
   those two already did it, and should still.
5. And check the ordinary case is unchanged: dispatch a train by hand with nothing else standing
   anywhere protected, and no signal should move except the one for its own destination.

#### Comments

**Claude, 2026-08-25.** `runLocomotives` and `executeTimetableInternal` both sweep every protecting
signal the moment a run starts, and both say why: while nothing is running the refresh is silent, so a
train placed by hand produces no occupancy change and nothing will ever command its platform's signal.

The right-click dispatch became a full run in the MT-139 work - it counts its thread, engages every
guard and throws its own destination's signal - and did not inherit the sweep. Two doors of three.

Your own rule, quoted inside that method: "The same thing should happen in manual operation vs auto -
the same switches and signals set, and guards applied."

---

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 11b9ded6, build\classes, compiled 25 Aug 18:03 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-191"></a>

### MT-191 - 2026-08-25 - An edit that never finished

**Disposition:** fixed validated
**From:** OB-108, on your ruling "revert to pre save state"
**Written:** 2026-08-25

This one needs you to kill the application on purpose, so it is worth backing up `config/` first -
the whole folder, because a setup is keyed by page ID and half of it is no use.

1. Open the track diagram editor on a page that has stations, names, lengths or facings on it.
2. **Move some of that track** - drag a station square somewhere else. Do not save.
3. Kill TrainControl from Task Manager. Not File > Exit; end the process.
4. Start it again. The log should say the last layout edit did not finish and that the setup has been
   put back. **The page should look exactly as it did before step 2** - the track where it was, and
   every station, name, length and facing still on it.
5. Now the control, which matters more than step 4. Open the editor, move something, and **Save**
   properly. Restart normally. The edit should still be there and there should be no message about an
   unfinished edit.
6. And the other ending: open the editor, move something, and **Cancel**. Restart normally. The move
   should be gone - as it always was - and again no message.
7. **Now the same three things in the AUTONOMY editor**, which is the half that was wrong when this
   was first written and is worth doing carefully. Open the setup editor, name a station or place a
   locomotive, press **Save**, and restart normally. **Your edit must still be there.** Then do it
   again and **Cancel**, and restart: the setup should be as it was before you opened it, with no
   message about an unfinished edit either time.
8. And one that needs the process killed again: open the AUTONOMY editor, change something, and kill
   TrainControl from Task Manager. On restart the change should be gone and the log should say the
   last edit did not finish.

#### Comments

**Claude, 2026-08-25, after the review.** Steps 7 and 8 exist because the first version of this was
wired only into the track editor. `beginEditSession` is called from the constructor, which serves both
modes, and all three `endEditSession` calls sat on the track-editor side of an `if (isAutonomyMode())`.

So every autonomy setup session left its note behind and the next start reverted the setup to before
that editor opened. **Spend an evening naming stations and press Save, and the evening would be gone
the next day** - which is worse than the defect being fixed, because it destroys saved work rather
than unsaved work. Found independently by two reviewers, one of which reproduced it on a copy of your
real configuration.

Step 7 is therefore the most important thing on this page: it is the control, and it is the one that
was broken.

**Claude, 2026-08-25.** The editor writes the setup after every gesture, deliberately, because the
autonomy session is rebuilt from disk after each one; the diagram is only written at Save. So an
abnormal exit left disk holding a setup keyed to the squares as moved and page files with the track
where it was, and the first reconciling save afterwards pruned the difference as settings for track
that does not exist. Silent, and it is the loss the whole reconcile guard exists to prevent, arriving
through a door none of its enforcement sites can see.

The editor already kept a snapshot for Cancel. But a snapshot that lives in memory is lost by exactly
the event it exists to survive, so the same snapshot now also goes to disk as
`config/autonomy/setup-before-edit.json` and is put back at startup if it is still there - which can
only mean the last session did not finish.

Steps 5 and 6 are the half that decides whether this is safe rather than merely useful. A note left
behind by a clean Save would throw away the edit you just made, which is a worse failure than the one
being fixed. Both are covered by tests as well; they are here because the tests drive the store
directly and cannot restart an application.

One more case, which is not separately testable without a display and so is listed here rather than
covered: the revert deliberately does nothing while an editor is OPEN. `resetAutonomySession` throws
the session away and the next caller rebuilds it - a page-set change, a diagram re-download - and if
that happened with an editor open, the note that editor wrote on the way in would be found and acted
on mid-edit. The revert would then undo the work in progress, which is the loss this exists to
prevent, caused by the thing preventing it. If you can contrive that - open the editor, move
something, then do whatever re-reads the pages wholesale - your edit should still be there.

**Adam, 2026-08-25 (triage).** Works.

*Run against commit 11b9ded6, build\classes, compiled 25 Aug 18:03 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-192"></a>

### MT-192 - 2026-08-26 - The diagram menu says which square it is about, and opens the editor

**Disposition:** fixed validated
**From:** OB-112 and FR-026, both about the right-click menu on the track diagram
**Written:** 2026-08-26

Two changes to one menu, so they are checked together. Both are on the **track diagram in the main
window**, with a setup loaded - not in the editor.

1. Load your setup and right-click **LowerBack** on page 2. The menu should now open with the
   square's name at the top, in bold and greyed. It is a label, not an item: it should not respond to
   the pointer.
1. **And the same in the autonomy editor**, which is where you reported it missing. The heading is
   written there and always was - it was being deleted a line later by the tidy pass that takes empty
   sections out of the menu, so no menu in either window has had one since 16 August.
2. Right-click a square you have never named - a plain sensor. The heading should read `s88 <address>`
   rather than a name. On a piece of track with no sensor it should read the coordinates.
3. Right-click somewhere the menu has nothing at all to offer. **No menu should appear.** A grey box
   containing only a name would be the new way to get this wrong, so it is worth one deliberate try.
4. Now with **no setup loaded**, right-click a square and open **Autonomy Setup**. At the foot of that
   submenu there should be a new **Open the Full Editor...** item. Press it: the editor should open in
   autonomy mode, on that page, with the square you clicked found and flashing.
5. The greying. With trains running, the whole menu is replaced by a note that the editor is open or
   busy - that is the old behaviour and is fine. What to look for instead is a case where the item is
   live and pressing it gets you an error box: if that happens, say which error, because the item is
   supposed to be greyed with that same reason on its tooltip.

#### Comments

**Claude, 2026-08-26, after your screenshot.** The heading was never missing from the code. `tidy()`
removes "a heading with nothing under it", and asked that question by looking at whether a divider
followed the heading - and `title()` writes the heading and its divider together, so the answer was yes
for every heading there has ever been. It now looks PAST the divider, which is where the emptiness
actually shows.

Worth saying plainly: I read that code twice and reported it as working, because the call that adds the
heading is unconditional and three lines from the top. What settled it was writing a test that asks the
menu what its first item is. Reading found nothing three times; running found it once.

**Claude, 2026-08-26.** Step 5 is the one worth your attention. The item asks the main window a single
question - "would the editor open, and if not, why" - and that method is the four refusals
`openLayoutEditor` already makes, in its order, handing back the same message key each one shows. A
test reads both and fails if a refusal exists there with no answer here. What it cannot check is
whether the four are still the right four, which is what step 5 is for.

**Adam, 2026-08-26 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 26 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-193"></a>

### MT-193 - 2026-08-26 - A reversing point that leads nowhere

**Disposition:** fixed validated
**From:** OB-113, on your note "we need to add a warning if a reversing point leads to nothing else"
**Written:** 2026-08-26

1. In the autonomy editor, open the findings list on your real setup. **Nothing new should appear.**
   Your reversing points all reach stations, so a notice about any of them would be a false alarm -
   and this check has been written specifically not to fire on a working switchback, where a train
   runs in, turns, and comes back out to take another branch.
2. Now make one fire on purpose. Take a piece of track that is not joined to the rest of your railway
   - a siding you have drawn but not connected, or draw two squares somewhere empty - put a sensor on
   it, and set that square to **trains may change direction here**.
3. The list should gain a **notice** naming that square: trains turn round there, and no station can
   be reached from it.
4. Join it back to the railway, or make some square on it a station. The notice should go.

#### Comments

**Claude, 2026-08-26.** Why it is a notice rather than a warning: "reaches no station" is also true of
a spur somebody is halfway through drawing, and this list stops being read the moment ordinary things
are listed beside real problems.

The gap it fills is one the application itself opens. `Arrival trapped` fires when a train could reach
a square and then not leave, and its wording says: "Either set 'trains may change direction here', or
open the way ahead." Taking the first half of that advice silences it - whether or not the way ahead
was ever opened. So a loud problem could be turned quiet by following the app's own suggestion, and
nothing was watching the result.

**Adam, 2026-08-26 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 26 Aug 00:29 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-194"></a>

### MT-194 - 2026-08-26 - Placing tiles without the diagram blinking

**Disposition:** fixed validated
**From:** OB-109
**Written:** 2026-08-26

1. Open the track diagram editor on a page with a good deal of track on it, and place a run of tiles -
   a dozen or so, of a type already on the page. **The diagram should not blink at all** between
   placements.
2. Now place the first tile of a type nothing on that page uses, at this tile size. There is a real
   decode behind that one, so it is the case that used to blink hardest. It should still not blink;
   if the machine is slow enough, you may see the spinner instead, which is the intended ending.
3. Change the **tile size** while the editor is open. Every image has to be decoded again, so this one
   is allowed to show the spinner - what it must not do is show you a page arriving square by square.
4. And the control: open a page you have not looked at since starting the application. That is a page
   ARRIVING, and it should behave exactly as it always has - held back behind the spinner until its
   track is drawn, rather than labels floating on empty squares.

#### Comments

**Claude, 2026-08-26.** Every placement rebuilds the whole grid, and a new grid used to hide itself
until its tiles had decoded. On the second and every later build each tile is a cache hit, so it hid
the diagram and got it back on the next pass of the event thread - a hide and a show a frame apart.
Whether an empty paint landed in between depended on where that thread happened to be, which is your
"sometimes".

Two rules now: a grid with nothing outstanding is never hidden, and a grid REPLACING one that was
already drawn waits with the spinner rather than blanking first. Step 4 is the control, because giving
up the hold-back altogether would trade this report for the one it was written to fix.

**Adam, 2026-08-26 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 26 Aug 00:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-195"></a>

### MT-195 - 2026-08-26 - Picking what holds a station back by clicking it

**Disposition:** fixed validated
**From:** FR-025
**Written:** 2026-08-26

1. In the autonomy editor, right-click a station and open **Advanced Parameters > Unavailable while
   occupied**. The checklist is as it was, with a new **Pick on the Diagram...** button beside OK.
2. Tick one or two entries and then press **Pick on the Diagram...**. The window closes and the banner
   asks you to click a square. **What you ticked must still be ticked when the list comes back** - it
   is applied before the click is waited for, on purpose.
3. Click a sensor somewhere on the layout. The list should reopen with that square ticked, named by
   its own name where it has one and by its s88 address where it does not.
4. Click something autonomy does not route over - a lamp, a route button, blank space. It should
   refuse and say so, **and stay waiting**, so a mis-aimed click costs you one more click and not the
   whole menu again.
5. Click the station itself, and then its caption if it has one somewhere else. Both should be refused:
   a station held back by itself is a station nothing can ever be sent to.
6. Right-click anywhere while it is waiting. That cancels, as it does for every other gesture in this
   editor.
7. And the case that used to be a dead end: find a station on a page where nothing else has been
   named. The old message said there was nothing to block with and stopped; it should now offer
   **Pick on the Diagram...** as well.

#### Comments

**Claude, 2026-08-26.** The click rule is deliberately looser than the checklist's. The list offers
named points only, and says why: "a square with no name is one the operator cannot recognise in a
list." That is a reason about reading a list, not about the railway - a square you have just clicked
needs no name to be recognised, and a blocker is resolved by tile rather than by name, so it is not
dropped from the built configuration. What survives is what is about the railway: autonomy has to route
over the square, and a station cannot hold itself back.

**Adam, 2026-08-28 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 28 Aug 01:24 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-196"></a>

### MT-196 - 2026-08-26 - A locomotive where a train is running

**Disposition:** fixed validated
**From:** FR-027
**Written:** 2026-08-26

1. Start autonomy and watch the diagram. A train that is **moving** should carry a small locomotive on
   the sensor it has most recently reached, instead of the black dot.
2. Watch one arrive and stop. As it comes to a stand the locomotive should go back to being a dot -
   the train is still there and the diagram still says so, but it is no longer running.
3. Two or three trains out at once is the case this is for: at a glance, which of them are moving?
4. Try it at your smallest tile size. The icon is scaled to the tile and floored so it never comes out
   smaller than the dot it replaces; if it is a smudge at that size, say so and it can be floored
   higher.
1. **On top of the address labels.** Turn s88 addresses on, and watch a train run over squares whose
   number is drawn on them. The locomotive should be over the number every time, not sometimes - and
   when the train stops and moves on, the number should come back out from under it.
1. **Which way it faces.** Watch a train run east and then one running west: the locomotive should be
   pointing the way it is going, mirrored rather than turned upside down. On a vertical run it stands
   on end, nose towards where it is heading - there is no way to draw a side view running up the page
   that does not, and it at least says which end is the front. If standing on end reads badly to you,
   say so: the alternative is to leave vertical runs facing east, which says nothing but looks calm.
5. **Now change it.** Put any PNG over `src/org/traincontrol/gui/resources/running_train.png`, rebuild,
   and that is the new icon - it is scaled to the tile, so only its shape and its transparency matter.
   Try one and tell me whether the size and position look right for the icons you would actually use.
6. And the safety net: rename that file, rebuild, and start autonomy. The diagram should fall back to
   the dot rather than throwing anything or drawing nothing.

#### Comments

**Claude, 2026-08-26.** Two things are deliberately one line each, because you asked for it to be easy
to change:

- `ICON_ONLY_WHILE_MOVING` in `TileOverlay`. True is what step 2 describes. **False gives every train
  autonomy is holding a path for the locomotive, moving or not** - which is the other reading of
  "while autonomy is running (not while stationary)", and if that is what you meant, that is the whole
  change.
- `ICON_FOLLOWS_TRAVEL`, whether it is turned to face the way the train is going. The direction comes
  off the line already drawn through the square - the same geometry - so the locomotive and its path
  cannot disagree about which way it points.
- `ICON_SCALE`, how much of the tile it takes - 0.76, at your reading of the first version. Not the
  whole square on purpose: the line showing which way the path runs is drawn underneath, and an icon
  that covers the tile answers "where is it" by hiding "where is it going".

**Claude, 2026-08-26, on "it's a coin toss".** It was not a toss, it was fixed and wrong, and it looked
like chance because it depended on where the address number happened to fall on the tile. The overlay is
painted after the tile draws itself, so it is reliably over the tile's OWN icon - but the address and
station labels are SEPARATE components that LayoutGrid z-orders to the front, and no painting order
inside one component reaches over a sibling drawn after it. A square now comes to the front while a
train is running on it and goes back when it stops. Step 5 is the half worth watching: without the
release, every square that ever held a moving train would end up permanently over its own address.

"Moving" is asked of the locomotive's speed rather than of its path, because a train with a path can be
standing - waiting at a platform, or held while another route clears - and those are the ones step 2 is
about.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 697417f9.*

---

<a id="mt-197"></a>

### MT-197 - 2026-08-26 - Station captions as blue ovals

**Disposition:** fixed validated
**From:** FR-028
**Written:** 2026-08-26

1. Start autonomy and look at a page with stations on it. Every caption should be a navy oval with
   white text - the same blue as the station badge on an autonomy locomotive panel - and the tile art
   should show through it.
2. **Where they land.** On east-west track the oval sits just below the rail; on north-south track it
   lies across it, centred on the square rather than starting at it. Switches, crossings and curves
   have no single direction and are left where captions have always been. Look for one that reads
   badly.
1. **A caption at the left edge of a page** cannot be centred - there is no column to the left to move
   into - so it starts at its own square as it always did. Find one and check it looks deliberate
   rather than broken.
1. **The rest of the diagram must not have moved** (OB-115). Compare a page against the same page in
   the previous build if you still have it: route names like Reset and Inner Loop, the s88 address
   numbers, and your own writing should all be exactly where they were.
3. **At both tile sizes.** Switch between your sizes, including 60px. The oval and its offset are
   computed from the tile, so nothing should need adjusting - if something does at one size and not
   the other, that is the interesting case.
4. **The arrows.** A train facing each of the four ways should show a solid triangle, not a box.
   A box means the font has no glyph for it, which is the thing that went wrong first.
4. **A station with no name yet** shows a dash, and it should be visibly DIMMER than a station that has
   one - that is the editor saying "this is a placeholder, not the answer". Check it on the grey pill
   as well as the blue: the two dim in opposite directions, and a rule that only worked one way would
   look right to whoever wrote it. It should still be readable, not faded into the oval.
4. **Two trains on one platform.** If you can arrange it, the caption shows both names with a bar
   between them and no square brackets - the same as every other caption. The brackets were removed
   from the single-train caption when these became ovals and were left on this one.
5. **The states, which are still doing their old jobs in a new shape.** The destination is a yellow
   oval with black text; where the train started is grey; a station on the route it has not reached
   yet has red text; one it is passing through shows three dots; an empty station shows a dash.
6. Captions are still clickable - left-click activates the locomotive standing there, double-click
   opens the setup, right-click opens the menu - and hovering one still lets Control+V work over it.
7. **Your own writing on the diagram must be untouched.** Yard names, notes, anything you typed on a
   square is text and stays text. If any of it turned into a blue oval, that is a bug.
8. The track diagram editor and the autonomy editor draw the same captions from the same place, so
   check one of them too.

#### Comments

**Claude, 2026-08-26.** Three things worth knowing.

**The arrows are U+25BA and U+25C4, not U+25B6 and U+25C0.** Segoe UI has no glyph for the second pair,
so the first rendering drew proper triangles for trains facing north and south and a tofu box for every
train facing east or west. Found by rendering the labels to a picture and looking at them; a test now
asserts all four can be drawn, and also that the two rejected ones are still missing, so the reasoning
cannot quietly stop being true.

**The first placement attempt was wrong in an instructive way.** The caption's grid cell is declared
REMAINDER - it runs from its own row to the bottom of the diagram - so anchoring it SOUTH put captions
below the layout entirely and CENTER stacked them in mid-air. The offset lives inside the label now,
which is what the old `<html><br>` hack was doing all along.

**Centring, and what it cost.** A caption is wider than the square it names, so centring it means
starting LEFT of that square - which no border can express, because insets cannot be negative. The
caption's cell is moved back one column when the grid is built and the label pays the difference back
as a left inset, recomputed every time its text changes. One column is enough at every tile size this
draws at, and at the left edge of a page there is no column to move into, so a caption there starts at
its own square exactly as it did before.

**OB-115, which this caused and which is the more useful story.** Text labels are added with
`BASELINE_LEADING`, and GridBagLayout does what that says: it works out a baseline for the row from
every component anchored that way and lines them all up on it. A caption is one of those components,
so giving it a pill and a smaller font moved the row's baseline and every other label in that row went
with it - three pixels, on labels nobody had touched. Captions are anchored NORTHWEST now, which is
both where they want to be and out of that ballot.

It was found by measuring rather than by looking: `docs/tools/README-bounds.md` describes the harness, which
dumps every component's bounds for both builds and diffs them. Its verdict on the fix is "0 tile
placements differ" on all ten page-and-size combinations, with every difference a caption. The control
that made it trustworthy was running the same build twice - which disagreed with itself about forty
tiles until it learnt to wait for the tile images.

**Adam, 2026-08-26 (triage).** Works, with notes.

Looks ok, but:
move ovals 5px down
change gray option to be default, blue to be via the setting
move setting under the autonomy option category

*Run against commit 309b984f, build\classes, compiled 26 Aug 22:08 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-27.**

Two changes on 2026-08-27 that show on this screen. Station captions sit lower - nine pixels at the sixty-pixel view, written as three twentieths of a tile so it holds at every size - because centred exactly on a rail they read as sitting on top of the track rather than labelling it. And an empty station's dash is now centred on its square; the old arithmetic only ever subtracted, so a caption wider than its square was centred and a narrower one was left hard against the left edge, which meant the same caption moved as a train arrived and left.

**Adam, 2026-08-27 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 27 Aug 18:42 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-198"></a>

### MT-198 - 2026-08-26 - The sidebar icons

**Disposition:** fixed validated
**From:** FR-029
**Written:** 2026-08-26

Redrawn twice: once for FR-029, and once for your notes on the first set.

1. Look at the seven tabs down the side. All seven should be dark grey, the same weight as each other,
   and legible at the size they are actually drawn.
2. **The locomotive**, which is the one that carries a number. Switch between keyboard pages 1 to 10.
   The page number should be readable at every page - white with a shadow, sitting on the body of the
   engine rather than half off it. Two-digit "10" is the hard case.
3. **Autonomy** is a play symbol now, not the old graph.
4. **Routes** is a path with an arrow at the end, not a set of points. The old one drew a turnout,
   which is the thing a route *sets* and reads as "switch" everywhere else in the application.
5. **The signal** should have two clean round lamps. The first version had stepped edges on them.
6. **Track** should look even - a bar at each end, equal rail showing top and bottom.
7. **The log** lines should sit centred in their box, equal air above and below.
8. Anything that still looks wrong is worth saying: these are quick to redraw.

#### Comments

**Claude, 2026-08-26.** All seven are generated by `docs/tools/tab-icons.py` and written into
`src/org/traincontrol/gui/resources/tabs/`. Redrawing one means editing that file and running it, not
editing a PNG.

Two things worth knowing if they are changed again.

They are drawn at four times the output size and reduced, because PIL does not antialias shape edges -
a lamp punched out of a signal head came out with a visible staircase on it, which is what "poorly
traced" was looking at. The reduction keeps only the alpha and repaints flat dark grey, so the icons
stay one exact colour rather than picking up dark fringes from the transparent surround.

And the locomotive is not free to be any shape it likes. The page number is merged over the CENTRE of
that icon in white; the old drawing left the middle empty, so the number was white on the tab strip.
`testTheLocomotiveIsSolidWhereThePageNumberSits` is what stops that coming back.

Dark grey is safe here only because the application has one look and feel - `FlatLightLaf`, with no
dark variant. If a dark theme is ever added, these need revisiting.

**Adam, 2026-08-26 (triage).** Does not work.

Still don't like them. on order from first to last:
1. locomotive icon needs a plow at the front
2. track needs to be symmetrical.  no line on top or bottom, some track should protrude on both sides
3. OK, maybe add a white inner outline for a nicer look
4. signal still not symmetrical.  convert to old fashioned style wing signal icon
5. good idea, make it summetrical by stretching the top arm
6. good!
7. good!

*Run against commit 309b984f, build\classes, compiled 26 Aug 22:08 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-26.**

Fifth pass, against the ruling of 2026-08-26. (1) The locomotive has a pilot now - a wedge hanging from the underside of the footplate at the chimney end, deepest at the front, level with the bottom of the wheels. It took the pony truck's place: three round wheels and a blade inside thirty pixels is more than the silhouette holds. (2) Track has no bar at either end and 40 of bare rail showing past the outermost sleeper at both, worked out from one margin so the two cannot drift apart - the previous version answered the lopsidedness by adding end bars, which made it even and also made it a ladder. (3) The play triangle carries a keyline. It is punched rather than painted white, because these files are one flat colour on transparency and a second colour would be wrong on any background but this theme's. Play only, by your ruling. (4) The signal is a semaphore: post, finial, base, and a horizontal arm - danger, the aspect worth drawing. The fishtail notch is gone; at thirty pixels a tapering end is a pennant, which is why it read as a flag twice. It ends square with a stripe near the tip and the spectacle punched out at the post. (5) Route arms are equal, both 156 from the turn, written as one number. Every icon now sits within a pixel or two of centre in both axes except stats, which is 96/56 because those are bars on a baseline, and the play triangle, which is 110/90 on purpose - a triangle's weight sits behind its point.

**Adam, 2026-08-27 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 27 Aug 01:24 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-27.**

Settled 2026-08-27 after eight passes. The locomotive is now ONE closed path walked around the outline, not a stack of primitives - that change is what fixed it, and it came from Adam's diagnosis rather than from any choice of parts. The plow took four passes on its own, all four lost to ambiguous wording on my side; drawing the candidates and having him name one settled it in two rounds, first the shape then how much flat bottom sits between its angles. Worth reusing. Final set: locomotive with a raked plow and filled wheels; track with rail showing past the sleepers at both ends; a play triangle with a punched keyline; an upper-quadrant semaphore raised 40 degrees with an arrow on the arc its tip travels; a symmetrical S for routes; stats and log untouched. Every icon sits within a pixel or two of centre in both axes except stats (bars on a baseline) and the play triangle (a deliberate 124/105 lean - a triangle's weight sits behind its point). The routes and keyboard tabs are also swapped, which is worth a look in its own right: the order is now control, diagram, autonomy, ROUTES, KEYBOARD, stats, log, and each tab should wear its own icon and tooltip.

**Adam, 2026-08-27 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 27 Aug 18:42 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-199"></a>

### MT-199 - 2026-08-26 - What the captions say in each editor

**Disposition:** fixed validated
**From:** FR-030
**Written:** 2026-08-26

1. Open the **track diagram** editor. There should be **no station captions at all** - no ovals, no
   names, no dashes. Your own text labels stay exactly as they were.
2. Move a station square around in that editor and save. Nothing about the captions should come back
   while you are in there, and the setup should be unharmed when you look at it afterwards.
3. Open the **autonomy** editor. Captions should name the **station**, not the train parked there.
   That is the change: this is the window where the railway is named.
4. In the visibility box on the right, tick **Show Parked Trains**. The captions should switch to
   naming whichever locomotive is standing there - which is what this editor showed before - and the
   diagram should redraw as you tick it, not on the next thing you happen to do.
5. Close the editor and open it again. The switch should be where you left it.
6. The **running diagram** is untouched by all of this: captions there still show the train, with the
   destination in yellow and the rest as before.

#### Comments

**Claude, 2026-08-26.** The rebuild in step 4 is the part worth watching. A caption's text is decided
when the grid is BUILT rather than when it is painted - it is part of the tile art - so a switch that
changed the setting and repainted would appear to do nothing until something else rebuilt the diagram.

Step 1 is enforced by one rule in one place rather than at the four points that draw a caption; a rule
applied at the point of use is a rule with four chances to be forgotten, and this file has form. What
that leaves is whether the rule is asked the right question, so a second test reads the call itself -
it must ask whether this grid is in an editor and whether that editor is the autonomy one, because
`layout.getEdit()` is true in BOTH and has been wrong here before.

**Adam, 2026-08-27 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 27 Aug 18:42 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-203"></a>

### MT-203 - 2026-08-27 - Cropping an icon again without reselecting it

**Disposition:** fixed validated
**From:** FR-032
**Written:** 2026-08-27

**Take a copy of `tc_loc_icons` before this one.** It is where your crops live and several steps here
delete files in it.

1. Pick a locomotive and set a local icon from a photograph, with the crop box ticked. Crop it.
2. **Hover the big locomotive picture.** A small crop mark appears in the MIDDLE of it. It is in the
   middle because there is nowhere else - a cropped icon fills the whole label - so it is deliberate
   rather than a corner badge that slipped.
3. Click it. The crop window opens on **the original photograph**, not on the crop - so you can pan
   back out to parts of the picture the first crop cut off. That is the whole point of the ticket.
4. Adjust and accept. The icon changes; the old crop file disappears from `tc_loc_icons`.
5. **Right-click the mark.** That should open "set local icon" - the same as right-clicking anywhere
   else on the picture. It is a separate control sitting on top, so it has to hand the click on.
6. **Switch to a locomotive with no local icon.** The mark should not appear over its picture. Switch
   by KEYBOARD as well as by clicking, which is the case that used to leave it stranded.
7. Page 1 of the keyboard should show a plain locomotive on its tab; pages 2 to 10 a blue capsule with
   the number in white. Check 10 in particular - it is the only two-digit one.

8. **The case worth going out of your way for.** Put a photograph on a USB drive or in a folder that
   OneDrive can take offline. Crop an icon from it. Now make the photograph unreachable - unplug the
   drive - and re-crop the icon: it should still work, cropping the icon itself, and say in the log
   that it could not reach the original. Plug the drive back in and re-crop again: it should be back to
   cropping the PHOTOGRAPH.

   That last step is a data-safety check. The first version of this lost the path to the original
   permanently the moment you re-cropped with the drive out, and there was no way to get it back.

#### Comments

**Claude, 2026-08-27.** A crop keeps a small note beside it - `<crop>.png.source` - holding the path of
the photograph it came from, which is what lets step 3 work. Delete an icon and the note goes with it.

Three things that are deliberate rather than accidental, in case they look wrong:

The mark is in the middle of the picture, per step 2. A cropped icon is exactly 296 by 114 in a 296 by
116 label, so every position inside it is over the photograph; a badge clipping a corner reads as a
mistake, the same badge centred reads as a button.

Re-cropping a crop can only ever go tighter - the picture outside the last crop is gone - which is why
step 8 checks the log message rather than expecting to pan out.

And an icon cropped before this feature existed has no note, so it re-crops from itself and says so.
That is not a failure; it is the honest answer, and it is why the message does not claim the original
"could not be found" when there was never a record of one.

**Adam, 2026-08-27 (triage).** Does not work.

You can't "un crop" again later- it only shows the cropped image post crop.  The rest works.

*Run against commit 309b984f, build\classes, compiled 27 Aug 21:44 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-28.**

Diagnosed 2026-08-28, not yet fixed. Adam's icon file was inspected directly: the crop in tc_loc_icons has no .source note beside it, and FR-032 landed at 02:15 on 2026-08-27 - so the ORIGINAL crop was made before the feature existed and never got one. Re-cropping a note-less crop deliberately writes no note (see the comment in cropLocIcon: a note naming the crop itself would be believed forever and is worse than none), so every subsequent re-crop produces another note-less crop and the icon can never regain its original. That is why panning out is impossible for this icon and would be for any icon cropped before 2026-08-27. THE DEFECT IS THAT IT FAILS SILENTLY: the code logs infoRecroppedFromCrop rather than infoRecroppedFromOriginal, but a log line is not something anybody sees, so asking to pan out simply does the only thing available and says nothing. Proposed fix, not yet built: when there is no original to go back to, say so and offer to choose one - the right-click path already picks a source file, so the pieces exist. Adam said 'MT-203 looks good' on 2026-08-28, which I read as accepting this diagnosis rather than as the feature working; the verdict is left at Does not work until he says otherwise.

**Claude, 2026-08-28.**

OB-125 is fixed: the note now carries the view as well as the source path, and the crop panel opens on it. Five numbers - where in the source picture the middle of the crop window sits, the zoom, and the shape and size of the window - written on a second line of the same sidecar, so every note written before this still reads as a bare path. Restored through startAtCover rather than when it arrives, because the panel has no width until it is laid out and all five numbers are interpreted against a window derived from that width. Handed to the dialog only when the re-crop works from the remembered photograph: the fallback crops the crop, where the same coordinates mean somewhere else. Three tests and three mutations, plus the full battery at 122 green. Still needs your eyes: crop an icon, close, and re-open the crop editor - it should come up framed as you left it.

**Adam, 2026-08-28 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 28 Aug 18:58 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.** Filing note, not a result: this entry (written 2026-08-27) sits above MT-201,
MT-202 and MT-200 (all written 2026-08-26), against rule 1's append-at-the-bottom order. Rule 5 forbids
reordering existing entries, so it stays here rather than being moved; flagged so the gap between tag
order and date order in this stretch is not mistaken for a second, undocumented one.

---

<a id="mt-201"></a>

### MT-201 - 2026-08-26 - Closing TrainControl with the track editor open, and Discard

**Disposition:** fixed validated
**From:** LR-1 (2026-08-26 last-reviewer pass)
**Written:** 2026-08-26

**Take a copy of your `config` folder before this one.** It is a data-safety test and the failing
version loses autonomy settings; a copy makes the difference between checking a fix and finding out.

1. Load a setup that has station captions on it. Note where a few of them are.
2. Open the **track diagram** editor - the one for moving rails about, not the autonomy editor.
3. **Drag a captioned square one square across.** The caption follows the rails; that much is the
   feature working, and the setup behind it is written the moment you do it.
4. Now close **TrainControl itself** by the window's X - not the editor's X, the application's.
5. Answer **Discard** to the question about unsaved work.
6. Start TrainControl again and load the same setup.

The diagram should be as it was before step 3, **and so should the caption** - both halves of the edit
gone, which is what Discard means. The failing version put the diagram back and left the caption on the
square you dragged it to, so the two described different railways; the next save then quietly removed
the entries that no longer matched anything.

7. **The control, which matters more than the test.** Do the same thing again and answer **Save**. The
   move should be kept - diagram and caption both, on the square you dragged it to. A fix for step 6
   that also undid a Save would be worse than the defect.
8. And once more with the **autonomy** editor open instead, answering Discard. Unchanged from before:
   that path always completed its own discard.

#### Comments

**Claude, 2026-08-26.** Worth knowing where this came from, because it is a fix that created a defect.

Before application exit started disposing the editor, this case worked by accident: the discard was
half done, a pre-edit note was left behind on disk, and the NEXT start finished the job by picking that
note up. Disposing the editor on exit was right - it is what closes the session properly - and it
deleted the note, so the accident that had been covering this stopped. The half-discard had been there
the whole time.

The exit path now does what the editor's own X has always done. Nothing in the test suite touched that
method at all, which is why a review found it and not a test.

**Adam, 2026-08-31 (triage).** Works.

*Run against commit 302d7a11, build\classes, compiled 31 Aug 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-202"></a>

### MT-202 - 2026-08-26 - Which conflict the route question is about

**Disposition:** fixed validated
**From:** LR-2 (2026-08-26 last-reviewer pass)
**Written:** 2026-08-26

Two different situations stop a route, and until now the question on screen described the first one in
both cases.

1. Set up a station with a **protecting signal** paired to it, and a route that turns that signal
   **green**.
2. Start autonomy. Get a train to **stand at that station** - parked, not moving. Nothing else needs to
   be running.
3. Run the route from the **Routes tab**.

You should be asked about a signal **protecting a platform where a train is standing**. The failing
version said the accessory "is on track a train is running over right now", about a train sitting
still on a platform nothing was routed over.

4. Answer **Cancel**. The signal stays as it was. Nothing is written to the log, which is what has
   always happened at this door - you were asked, you decided, and the railway did nothing.
5. Run it again and answer **OK**. The signal goes green, and the log should carry **no** refusal line
   for it. This is the half that changed: the failing version wrote "the route did not switch it"
   before asking, so a route you went on to allow was permanently recorded as refused.
6. **The other door, which is where a line does get written.** Start the route while nothing is
   standing at the station, and get a train to arrive at it while the route is part way through - a
   route with long waits between its commands makes this easier to arrange. You are asked the same
   question mid-route; answer **Cancel** and the log carries one line, once, naming the signal.
7. **The other situation, which must still read the way it did.** With a train actually running over a
   stretch of track, run a route that would throw a switch on it. That question should still say a
   train is running over it, because there it is true.
8. **The sensor door is unchanged.** A route fired by an s88 trigger with either conflict present still
   stops, whole, without asking - there is nobody there to ask.

#### Comments

**Claude, 2026-08-26.** The two reasons have been told apart since the log learned the difference; the
reason simply stopped at the route class and never reached the window, so the dialog had one sentence
and used it for both.

It matters in both directions. Told a train is running over a signal when none is, the careful answer
is Cancel - and a route that was perfectly safe to force is lost, which is the over-strictness Adam has
twice said he would rather not have. Told it often enough about parked trains, the answer becomes a
reflex OK, and one day the train really is moving.

The log line moved at the same time, from before the question to the branch where the answer was no.
Both of those messages end by saying the route did not switch the accessory, which was a claim about a
decision nobody had made yet.

One thing that did NOT change, and is worth a ruling: cancelling at the door BEFORE a route starts
writes nothing to the log at all. That is how it has always been, and there is an argument for it - you
were asked, you decided, and the railway did nothing worth recording. There is also an argument the
other way, since your log is the record of what the railway was asked to do and not only of what it
did. Left alone rather than changed quietly; say the word either way.

**Adam, 2026-08-27 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 27 Aug 21:44 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-200"></a>

### MT-200 - 2026-08-26 - Grey station labels

**Disposition:** fixed validated
**From:** FR-031
**Written:** 2026-08-26

1. **Interface > Layouts > Grey Station Labels.** Tick it. The station captions on the diagram should
   turn light grey with black text, and the diagram should redraw as you tick it.
2. Untick it. They go back to the theme blue with white text.
3. Restart the application. The setting should be where you left it.
4. **With trains running.** The destination is still yellow and the originating station still grey -
   those colours say something and are unchanged. What the switch changes is the RESTING colour, which
   is what most captions are most of the time.
5. Look at a busy page with the grey on. That is what the setting is for: thirty blue ovals is a lot
   of one colour, and grey captions stop competing with the signals and the running path.
6. And the autonomy editor, which draws the same captions from the same place.

#### Comments

**Claude, 2026-08-26.** The colour is asked for at the moment a caption is coloured rather than read
once into a constant, which is what lets the switch work without restarting.

Worth knowing: the text colour is derived from the fill by perceived brightness rather than being
paired with it by hand. That is why the grey takes black text and the blue takes white without anybody
choosing, and it is also why the grey cannot be darkened much without the text following it - which a
test says out loud, because a grey a few shades darker would leave black text on a dark ground and
nobody would notice until they looked at a diagram.

**Adam, 2026-08-26 (triage).** Works.

*Run against commit 309b984f, build\classes, compiled 26 Aug 22:08 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-27 (triage).** Works.


---

<a id="mt-204"></a>

### MT-204 - 2026-08-29 - The words in the seven translated bundles

**Disposition:** fixed validated
**From:** FR-042
**Written:** 2026-08-29

All 309 `autosetup.*` keys that were shipping as English are now translated in every bundle. The
mechanics are verified and are not what this test is for: the files are pure ASCII, the key sets match
the English exactly, every `{0}` is preserved, there are no straight apostrophes, and "Central Station"
is left untranslated as a product name.

**Nothing about the WORDING is verified, and it cannot be from here.** That is what you are checking.

1. Switch the language and open the **Autonomy** menu, then **Manage Configurations**. Read the item
   labels rather than clicking them. Anything that reads as machine-translated or uses a word the rest
   of the application does not is worth noting.
2. Open the **autonomy settings** and read the setting names and their explanations. These are the
   longest strings in the namespace and the ones most likely to have gone stiff.
3. **The two irreversible confirmations** - deleting a setup, and discarding unsaved autonomy edits.
   Read them in each language you can judge. An unclear warning on an irreversible action is the worst
   place for a bad translation, so these matter more than the rest put together.
4. **The off-page connector.** One term had no precedent anywhere in any bundle and was coined:
   Verbindung, liaison, enlace, collegamento, verbinding, forbindelse, polaczenie. If any of those is
   wrong for a model railway rather than merely for software, say so - this is the single most likely
   thing in the round to be wrong.
5. German and Polish generally, if you can judge them.

Say which languages you actually read. A note that says "German looks right" is worth more than a pass
on all seven, because it says what was covered.

**Adam, 2026-08-29 (triage).** Works.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 18:27 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-205"></a>

### MT-205 - 2026-08-29 - Graceful stop, and when Start comes back

**Disposition:** fixed validated
**From:** OB-131
**Written:** 2026-08-29

Start used to be offered again the moment Graceful Stop was pressed, while trains were still coasting
to their next station - the buttons were the one surface still claiming autonomy had finished. It now
waits for the last train to berth.

The wait is unbounded, which matches the rule in that file that a wait for a railway EVENT has no
deadline while a wait for an acknowledgement does. The question is whether that is right here.

1. Start autonomy and let several trains get moving.
2. Press **Graceful Stop**. Watch the Start button and the diagram strip.
3. **While trains are still coasting**, neither surface should offer Start, and the diagram strip
   should still read Graceful Stop rather than flipping to "Start Autonomous Operation".
4. When the last train berths, Start should come back on both surfaces at the same moment.
5. **The case this is really asking about:** if a train ever fails to reach its station - stalled,
   derailed, a sensor that never fires - Start does not come back at all, and the only way out is
   restarting the application. If that has ever happened to you, say so, and whether you would rather
   it gave up after some time and re-enabled Start with a warning.

**Adam, 2026-08-29 (triage).** Works, with notes.

Works, but I'd rather have the start button reappear greyed out, and then reactivate, rather than disappearing.

*Run against commit eac0e392, build\classes, compiled 29 Aug 01:59 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.**

Settled, 2026-08-29: "stop button greyed is OK as is." So the note above is answered and no longer open - the diagram strip shows the GRACEFUL STOP greyed while the trains finish, and it is then replaced by Start. The alternative recorded here, a greyed Start appearing in that window, is not being built.

---

<a id="mt-206"></a>

### MT-206 - 2026-08-29 - Which surfaces show the wait mark moving

**Disposition:** fixed validated
**From:** OB-135
**Written:** 2026-08-29

OB-129 asked for the wait mark to animate. It now does on the overlay and does NOT on the track
diagram, and the two have different causes - the diagram build occupies the event thread as a single
task, so nothing can repaint while it runs.

This test is to confirm which surfaces are which before anything is changed about it, because the
repair options differ a lot in risk.

1. Do something that puts the **overlay** up - loading a layout from a folder, or a Central Station
   sync. The hourglass there should run: sand falling, and a flip when it empties.
2. **Watch the flip.** The sand should keep running downwards afterwards, and the glass should not
   jump at the moment it turns. Both were wrong earlier today and were fixed separately.
3. Now switch to a **track diagram page large enough to take a moment to draw**. The mark that appears
   over the diagram is expected to be STILL - one frame, held until the page appears.
4. Say whether the still mark reads as broken or merely as a static "please wait". That is the whole
   question: if a static mark is acceptable, OB-135 closes as documentation. If it is not, the fix is
   either chunking the diagram build or moving it off the event thread, and the second removes
   exclusions that were never written down.
5. Also check the mark is **half the size it used to be** and sits over the **middle** of the page,
   both of which were part of OB-129.

*Run against commit 309b984f, build\classes, compiled 27 Aug 01:24 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-29 (triage).** Works.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 17:39 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-207"></a>

### MT-207 - 2026-08-29 - simulate: true, and what a save does to it

**Disposition:** fixed validated
**From:** OB-136
**Written:** 2026-08-29

`simulate: true` disappeared from the live autonomy configuration. That is a setting you had on, and it
came back off, which means something wrote the file without carrying it across.

What is being checked is not that the value is right today - it is set correctly now - but that it
SURVIVES the round trip, because a setting that is silently dropped on save is wrong again the moment
you touch anything.

1. Confirm simulation is on, in whatever way you normally read it.
2. Change something unrelated in the autonomy configuration and save.
3. Read the setting back. It must still be on.
4. Now restart the application and read it a third time. A value that survives the save but not the
   reload is the same bug wearing a different coat.
5. If you can, repeat with the setting turned OFF. A default that quietly reasserts itself is only
   visible from the side it defaults to.

**Adam, 2026-08-29 (triage).** Works.

it survives a restart in sumulate mode, but should be automatically turned off when connected to a CS. this is why you saw it flip during my testing.

*Run against commit eac0e392, build\classes, compiled 29 Aug 02:30 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.**

That explains OB-136, and I have checked the mechanism rather than taking it on trust: the connection flag is decided ONCE during startup - false at line 319 of MarklinControlStation, true at 372 after a successful sync - and nothing changes it for the rest of the session. So there is no moment where you connect and simulation needs switching off; the only moment that matters is the load, and the load already refuses it. Layout.fromJSON sets simulate false, then tries to set it from the file, and setSimulate throws when a station is connected. That refusal is the flip you saw, and it is working as you describe.

ONE WART, which I am flagging rather than changing. The refused value is not remembered: the next capture writes simulate back to the file as false, so connecting to a station once ERASES the preference permanently. Run without a station afterwards and simulation is off, with nothing to say it used to be on. If you would rather the file kept your choice and the load simply declined to honour it while a station is present, that is a small change to Layout.fromJSON and the capture - say the word. I have not made it because it changes what the file means, which is your call not mine.

---

<a id="mt-208"></a>

### MT-208 - 2026-08-29 - Importing routes, and the table behind the dialog

**Disposition:** fixed validated
**From:** OB-137
**Written:** 2026-08-29

You reported that importing routes from JSON made the route table freeze up and look wrong. The cause
was a modal file chooser opened from a background thread, so the chooser and the window behind it were
being laid out by two threads at once.

The chooser and everything Swing now run on the event thread; only reading and decoding the file does
not. A test asserts that shape, and a widened version of it now covers every dialog in the application
- it found six more methods with the same fault, which are fixed here too. **None of that says it looks
right to you**, which is the whole of this test.

1. **Import routes from JSON.** Watch the route table while the chooser is open and as it closes. No
   half-drawn rows, no stale paint, no freeze.
2. **Cancel** an import rather than completing one. The table should be untouched.
3. **Import a deliberately broken file.** You should get one clear error, and the routes you had should
   be exactly as they were.
4. The same five other places, briefly, since they were fixed the same way and none of them was
   reported by anyone - each was found by widening the guard:
   - **Autonomy -> export JSON**, and **export routes**, and **export locomotives to CSV**. Each opens a
     window with the data in it and copies to the clipboard. Check the window draws properly and the
     clipboard has the same content.
   - **Save to file** from inside any of those windows.
   - A **locomotive function icon**: set a custom one, and use **Reset** on the function editor.
5. On Reset, read the confirmation before answering. Its default answer was Yes and is now No, because
   Yes there throws away every custom function and icon on that locomotive.

**Adam, 2026-08-29 (triage).** Works.

*Run against commit eac0e392, build\classes, compiled 29 Aug 02:30 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-209"></a>

### MT-209 - 2026-08-29 - Double-clicking a station on the running diagram

**Disposition:** fixed validated
**From:** OB-138
**Written:** 2026-08-29

You asked: "double clicking station label in track viewer should activate that locomotive (as if it was
selected on the key mappings) if it is mapped, not open the editor."

A double-click used to open the autonomy setup at that station - a deliberate feature from an earlier
round, and you have ruled the other way. The branch is gone rather than given another condition, so a
double-click is simply two ordinary clicks again and each one activates.

1. On the **running diagram** (not the editor), double-click a station label whose locomotive **is**
   mapped to a key. It should jump to that locomotive's mapping page and select its button, and it
   should NOT open the setup.
2. Double-click one whose locomotive is **not** mapped. Nothing should happen - no dialog, no error.
3. **The setup is meant to still be reachable**: right-click the same square. The full editor is there.
   Confirm that, because if it is not, this fix took away a door instead of moving it.
4. Try both while **autonomy is running**. The right-click menu explains itself when it has to refuse;
   the removed branch used to carry a copy of that reasoning and no longer needs to.

**Adam, 2026-08-29 (triage).** Works.

*Run against commit eac0e392, build\classes, compiled 29 Aug 02:30 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-210"></a>

### MT-210 - 2026-08-29 - The move cursor over labels that are not drawn

**Disposition:** fixed validated
**From:** OB-139
**Written:** 2026-08-29

You asked: "when show text labels is OFF in the autonomy editor, the cursor over a tile should never
show the move pointer."

The drag was installed on both the label and the square under it - which you asked for in FR-035,
because a pill is a small target - but the guard tested whether the caption OBJECT existed, and that is
true whether or not the text layer is drawn. So with the box unticked every square that had a label went
on offering to move something invisible.

The drag is not installed at all now, rather than merely losing its cursor: the cursor IS the diagram
saying a thing can be picked up, and the two must not disagree.

1. In the **autonomy editor**, untick **Text Labels**. Move the pointer over squares that had labels.
   No move cursor anywhere.
2. Try to **drag** one of those squares. Nothing should move.
3. **Tick the box back on.** The move cursor and the drag must both return - the grid is rebuilt on that
   toggle, which is what this depends on. If they do not come back, this is worse than the bug.
4. With labels **on**, confirm FR-035 still holds: dragging works from the label AND from the tile under
   it, not only from the pill.

**Adam, 2026-08-29 (triage).** Works.

*Run against commit eac0e392, build\classes, compiled 29 Aug 02:30 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-211"></a>

### MT-211 - 2026-08-29 - Trains stay where the run left them

**Disposition:** fixed validated
**From:** OB-144
**Written:** 2026-08-29

You filed this as critical: "run EN57-203 from BottomSecondary to BottomMainC. Then, switch to track
diagram page 2, click edit and save. EN57-203 is now back at BottomSecondary."

Where a train ended up lives in the running layout and nowhere else - nothing folds it into the setup
when a run stops. So the editor opened on a setup that still said BottomSecondary, Save wrote that back,
and closing the editor rebuilt every placement from the file. This is the same defect as the page-rename
one (DW-A1) through a different door, and it is fixed the same way: the running layout is now captured
into the setup **before** the editor is constructed, which is the last moment it can happen, because the
editor takes its undo point in its own constructor.

**Why it mattered more than a lost setting.** Occupancy is derived from placements, and `isPathClear`
never consults the s88 - so after the teleport the model believed BottomMainC was empty with a train
standing in it, and Start could route another one into it.

1. **Your repro, exactly.** Run EN57-203 from BottomSecondary to BottomMainC. Switch to page 2, click
   Edit, and Save without changing anything. EN57-203 must still be at BottomMainC.
2. The same again, but press **Cancel** instead of Save. Also must stay at BottomMainC - Cancel restores
   the setup as it was when the window opened, and what that snapshot holds is the thing this fix
   changed.
3. The same again, but **close the window with the X**.
4. **Restart the application** after step 1 and check once more. The capture writes to disk, so if this
   is right after a restart it is right for good; if it is not, the write is not landing.
5. **Now the thing a fix of this shape would most likely break.** Open the setup editor and deliberately
   **move a locomotive to a different point**, then Save. Your placement must win - it must NOT be
   overwritten by wherever the train was standing before. If step 1 works and this one does not, I have
   traded one bug for a worse one.
6. And **the track editor**, not just the autonomy one: run a train, open the *track diagram* editor,
   move a tile, save. The train should be where the run left it, and your tile edit should have stuck.

**Adam, 2026-08-29 (triage).** Works.

*Run against commit eac0e392, build\classes, compiled 29 Aug 02:30 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-29 (triage).** Works.

Works.

---

<a id="mt-212"></a>

### MT-212 - 2026-08-29 - The facing choice on a square trains turn round on

**Disposition:** fixed validated
**From:** OB-145
**Written:** 2026-08-29

You: "on bottommainc, I don't see a 'locomotive is facing' choice even though there is a path out both
ways" - and the clue that found it: "the menu disappears when switch 70 disallows outbound travel to the
west, which shouldn't affect leaving trains."

BottomMainC is marked **can reverse**, so the build emits a turning copy there, and a turning copy points
back the way it came in. Its facing is therefore an ARRIVAL side - your recorded facing is W - and the
build holds it perfectly well. What did not know that was `facingChoices`, which decides whether the
menu appears: it offered only where a train could be sent ONWARD. Once the one-way run cut the square to
a single arrival side there was one onward facing left, and the menu is not shown below two. So the
question disappeared instead of being answered.

The frozen fixture had the same defect on the same square, which is how it is now pinned.

1. **Your case.** Right-click BottomMainC with EN57-203 standing on it. The "**EN57-203 Is Facing...**"
   submenu must be there, offering **both** E and W, with W ticked.
2. **Choose the other one**, then look at the label on the diagram - it should turn - and press Start.
   The train must set off the way it is now pointing, not the way it was.
3. **Put it back to W** and confirm the setup reports no problem about it. A facing the square cannot
   hold is meant to be reported; W here is one it can, and it must not be flagged.
4. **The squares that should NOT have gained the choice.** On an ordinary through station - one not
   marked can-reverse or terminus - the submenu should still offer only the onward facings. If a plain
   through platform has started offering you the side trains arrive by, the fix has gone too wide and
   the build will quietly turn those trains round.
5. **The parking berths.** TunnelLeftPark, TunnelCenterPark, TunnelRightPark and TunnelLongPark were in
   the same position as BottomMainC on the fixture. Check one of them offers both ways too.

**Adam, 2026-08-29 (triage).** Works.

Works.

---

<a id="mt-213"></a>

### MT-213 - 2026-08-29 - The hourglass on a Central Station sync

**Disposition:** fixed validated
**From:** OB-140
**Written:** 2026-08-29

You: "the hourglass flyover isn't shown when syncing the CS database from the Locomotives JMenu,
locomotive database popup, or the Functions Jmenu.  Do we still need the greyout of the Functions menu?"

Those three doors were not special. `syncWithCS2` already put the spinner up - but only when it was
called ON the event thread, and off it it fetched the whole database in silence. Whether you were told
that something slow was happening depended on which thread the door happened to use, which is not a
distinction anyone can see. Sixteen doors go through that method.

**On the greyout: it was doing a real job and doing it three ways wrong.** The Functions menu's own
sync does not go through `syncWithCS2` at all - it walks the locomotive list one at a time - so it had
no spinner to fall back on. But it re-enabled both menus from inside its own background thread, which is
Swing off the event thread; it had no `finally`, so one locomotive throwing left the Functions menu dead
until a restart; and greying two menus does not stop the rest of the window being clicked. It now runs
behind the modal spinner instead, which shuts every door, has its own `finally`, and says what is
happening. So the greyout is gone - replaced, not just removed.

1. **Locomotives menu -> Sync**, and the same from the **locomotive database popup**. The hourglass
   should appear for as long as the fetch takes.
2. **Functions menu -> sync full locomotive state.** Same, and note this one is the slow one.
3. While it runs, try to click elsewhere in the window. It should be blocked, not merely greyed.
4. **Then check the Functions menu is usable again afterwards** - that is the failure mode the missing
   `finally` used to cause, and the new arrangement should make it impossible.
5. Anywhere else you know of that syncs. Every door through `syncWithCS2` should now show it; tell me
   if you find one that does not.

**Adam, 2026-08-29 (triage).** Works.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 17:39 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-214"></a>

### MT-214 - 2026-08-29 - No s88 in the route command list

**Disposition:** fixed validated
**From:** OB-141
**Written:** 2026-08-29

You: "a route command should not be able to contain s88 sensors. these should only be in the conditions
only. remove s88 from the 'Kind' dropdown. advise what happens in the model when this is selected (if
anything)."

**Nothing happened.** `MarklinRoute.execRoute` dispatches on a chain of isAccessory / isStop /
isFunctionsOff / isAutonomyLightsOn / isLightsOn / isLocomotiveSpeed / isLocomotiveDirection / isFunction
/ isRoute, and there is no `isFeedback` branch and no final `else`. A feedback row fell through all of
it: nothing sent, nothing logged. Its only effect was the pause the loop takes for every row, so an s88
command was a delay wearing the name of an instruction - and it was still saved, re-read and exported,
so the route kept a sentence in it that could never be obeyed. The model had always agreed with you:
`isConditionCommand()` returns true for feedback. Only the dropdown disagreed.

1. **Open a route for editing and drop the Kind dropdown in the COMMAND table.** S88 Sensor should be
   gone.
2. **Open the Kind dropdown in the CONDITIONS table.** S88 Sensor should still be there - that is where
   it belongs and the whole point of the change.
3. **If you have a route that already contains an s88 command**, open it. The row should still be there
   and still readable; what you cannot do is add another. If such a row has disappeared or the editor
   refuses to open the route, that is worse than the bug and I want to know.
4. Build and save a route with a feedback CONDITION and check it still fires on the sensor.

**Adam, 2026-08-29 (triage).** Works.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 17:39 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-215"></a>

### MT-215 - 2026-08-29 - Capitalisation in the Kind dropdown

**Disposition:** fixed validated
**From:** OB-142
**Written:** 2026-08-29

You: "the Kind dropdown in the route editor has inconsistent capitalization for its items. make all
words start with capitals."

Nine were sentence case beside four that were not - "Locomotive Function" next to "Locomotive speed" -
and they sit in one list, which is where a mixture shows most.

**English only, and that is deliberate.** French, Spanish, Italian, Dutch, Danish and Polish use
sentence case for interface labels, and German capitalises nouns on its own rules; applying "every word"
to those would be wrong rather than merely unnecessary.

1. Open the Kind dropdown in both tables and read down it. Every word should start with a capital.
2. **One to rule on: "Train At A Sensor".** I took you literally. Ordinary title case would be "Train
   at a Sensor" - lower case for the short joining words. Say which you want and I will change it.
3. If you read any of the other languages, check I have not disturbed them - they should be exactly as
   they were.

**Adam, 2026-08-29 (triage).** Works.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 17:39 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-216"></a>

### MT-216 - 2026-08-29 - The stop button greys before it becomes Start

**Disposition:** fixed validated
**From:** OB-143
**Written:** 2026-08-29

You: "the button disappears rather than being greyed out prior to replacement by the start button. make
it get greyed out and then replaced."

The strip mirrors whichever of the two real buttons is enabled. Pressing Graceful Stop disables it at
once while autonomy keeps running - `stopLocomotives` returns immediately and the trains coast on to
their next station - so for that whole window neither was enabled, and the strip's answer to "neither"
was to hide. The control vanished from under the hand that had just pressed it.

1. Start autonomy, then press **Graceful Stop on the diagram strip**. The button must stay where it is,
   with the same words, and go grey.
2. **Watch it through to the end.** When the last train berths it should become Start Autonomous
   Operation, in the same place. What should never happen is a gap with nothing there.
3. Try **clicking it while it is grey**. Nothing should happen - pressing it again is the one gesture
   the window has no answer to.
4. The same from the **main window's own Graceful Stop**, watching the diagram strip while it runs.
5. **The case that must NOT have changed**: with a setup loaded but nothing running and Start
   unavailable for some other reason, the strip should show nothing at all - not a greyed stop.

**Adam, 2026-08-29 (triage).** Works.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 17:39 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.**

Your note on MT-205 asks for something slightly different from what OB-143 asked, and I have followed OB-143 - flagging it rather than choosing silently.

MT-205 (01:59): "I'd rather have the start button reappear greyed out, and then reactivate, rather than disappearing."
OB-143 (14:27): "the button disappears rather than being greyed out prior to replacement by the start button. make it get greyed out and then replaced."

Both are about the same window, and they name different buttons. OB-143 says the STOP greys and is then replaced by Start; MT-205 says the START appears greyed and then activates. I implemented OB-143, because it is the later and the more explicit of the two, and because during that window the true statement is "the stop you asked for is being carried out" rather than "start is nearly available".

If you would rather see a greyed START there instead, it is a one-line change to the rule - say which.

**Claude, 2026-08-29.**

Settled, 2026-08-29: "stop button greyed is OK as is, please update the notes." The tension I raised between this and MT-205 is closed in favour of what is built - the stop greys and is then replaced. Nothing to change.

---

<a id="mt-217"></a>

### MT-217 - 2026-08-29 - The play button on each route

**Disposition:** fixed validated
**From:** FR-043
**Written:** 2026-08-29

Your whole request, and almost none of it can be checked by a test - what a test holds is that the
button is drawn where it can be pressed, which is the one way this fails invisibly. Everything below is
the part only you can answer.

1. **Look at the table.** Every route cell should carry a green play triangle at its right-hand end,
   about the height of the text. Is it the right size, and is it clear of the name on a long one?
2. **Press one.** It should run immediately - no confirmation - and the triangle should become a
   turning spinner until the route finishes, then go back to a triangle.
3. **Click a cell anywhere else, with either button.** The right-click menu should open. This replaces
   the old "execute this route?" dialog entirely.
4. **Run a route from that menu.** Its spinner should start too.
5. **Run two or three routes at once** and watch that each spinner belongs to its own route. This is the
   case I got wrong first time: the reset was wired to the route table's refresh, so the first route to
   finish wiped every other spinner. It is now wired to your three triggers only.
6. **The three resets**: do a Central Station sync, an import, and a route edit while something is
   spinning. Each should put every button back.
7. **A spinner is a claim that something is running** - if you can find a way to leave one turning on a
   route that has finished, that is the bug worth having.
8. On the spinner itself: I drew a turning arc rather than the hourglass the rest of the application
   waits behind, because you asked for "a spinner animation" and the hourglass is the mark for a modal
   wait with nothing else to do. Say if you would rather it matched.

**Adam, 2026-08-29 (triage).** Does not work.

Play buttons not clickable.  Make them clickable.

Change behavior from my prior request: if play button is hit, route executes without confirming.  If route is left-clicked elsewhere, pop up the confirmation first.  If right-clicked, then show the right click menu.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 17:39 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.**

Reworked, and OB-146 is folded in here because it is the same strip of pixels.

WHY IT WAS NOT CLICKABLE, measured rather than guessed. I rebuilt the same JTable headless and asked whether a click at the pixel the renderer draws lands inside the box the click test uses. It does, in every cell, off by one pixel - so the button was not in the wrong place. It was thirteen pixels square, eight in from the right edge, in a row thirty pixels tall: about one and a half percent of the cell, hard against its border. Missing it opened the right-click menu, which is what "not clickable" looked like from the outside.

So the target is no longer the glyph. The pressable part is a strip the full height of the row and thirty wide, with the triangle drawn in the middle of it - the mark stays the size you asked for, the target is the size a target has to be.

That strip is also the answer to OB-146. The label now reserves it as a border inset, so a long route name runs out of room before the icon rather than under it.

NOT WRAPPED, which is the letter of what OB-146 asked, and here is why. A JLabel wraps only as HTML, and these rows are a fixed thirty pixels; two lines of Segoe UI 14 need about thirty-eight, so wrapping would hide the second line rather than show it. Real wrapping wants a taller row, which changes the look of the whole table. Say if you want that and I will raise the row height; for now a long name ellipsises before the icon.

The three click rules are yours: play button runs it with no confirmation, left click elsewhere asks first, right click opens the menu. The confirmation coming back is not a step backwards - it went away because a left click ANYWHERE ran the route, and what makes it unnecessary is having to aim, which only the button now does.

Retest wanted: the strip should be easy to hit anywhere down the right-hand end of a cell, and no route name should touch the icon.

**Claude, 2026-08-29.**

OB-147 is folded in here too - it is the same strip. The pointer now becomes a hand over the play button and goes back to an arrow everywhere else in the table, and it asks isOverTheRoutePlayButton to decide, which is the same method the click asks. Working that out separately is how a hand ends up over pixels that do nothing, which is the affordance and the guard disagreeing - the shape of OB-057 and OB-090. Leaving the table restores the cursor, because a cursor belongs to the component rather than to a pixel and the hand would otherwise be left behind.

Worth checking when you retest: the hand should appear over the whole right-hand strip, not just over the triangle - if it appears over a smaller area than you can click, one of the two has grown its own arithmetic again.

**Adam, 2026-08-29 (triage).** Does not work.

The button itself works, but there is no animation / change in state while it is running.  It should gray out and then become reenabled once the route finishes.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 18:27 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.**

Reworked to what you asked for: the play button GREYS while its route runs and goes green again when it finishes. The spinner is gone, and the timer that drove it went with it - a disabled button needs two repaints in its whole life rather than sixteen a second, and a state is either true or false rather than something you have to catch happening. That last part matters here, because a route with a handful of accessories in it can finish faster than an animation reads.

Two things I added that you did not ask for, because a greyed button that is not actually disabled is a lie: while a route is running its strip REFUSES the click - silently, since the button already says why - and it shows an arrow rather than a hand. Otherwise the same route could be started twice, which is the thing the greying is there to say cannot happen. The renderer, the click and the cursor all ask one question, and a test fails if any of the three stops asking it.

Retest: press a play button and watch it grey and come back. Press it again while it is grey - nothing should happen. The pointer over a greyed button should be an arrow.

**Claude, 2026-08-29.**

Third attempt, and this time with a way to find out rather than another guess.

I cannot find the fault by reading, and I have now been wrong about it once. The chain is intact - the click calls executeRoute, executeRoute marks the route BEFORE it starts the thread, the renderer asks the same set, and a route with three accessories in it takes about 600ms, which is not too fast to see. So either the mark is not being set, or the change was too small to notice. I have addressed both rather than picking one, because guessing wrong costs another round trip:

TOO SMALL - the WHOLE CELL now washes grey while its route runs, not just the thirteen-pixel triangle at the end of it. A background is noticed without being looked for; a small shape changing hue is not.

NOT SET - two lines in the log, behind the debug flag, saying when a route is marked as running and when it stops, with the milliseconds between them.

**If it still does not change, please run with debug on, press one play button, and send me those two lines.** They answer the question I cannot answer from here: whether the mark happens at all, and if it does, how long it lasted. 'Nothing happens visually' and 'it happened and was over in 40ms' look identical from where you are sitting and need opposite fixes.

**Claude, 2026-08-29.**

Your experiment, done - and you were right to propose it. Two things could be wrong and I had not been able to tell them apart from here:

- THE DRAWING: the cell never changes appearance, whatever the state says.
- THE WIRING: the appearance follows the state correctly, and the state is cleared again before anyone could see it.

Tying the clear to a fixed second removes the second possibility, so what you see now answers the question rather than raising another one. The button greys the moment you press it and goes back exactly one second later, WHATEVER the route does - the route finishing no longer clears it, which is what makes this deterministic rather than 'usually about a second'.

What each outcome means:

- It greys for a second and goes back -> the drawing works, and the fault was always the timing of the clear. The fix is then a minimum visible duration, or clearing on the later of the two.
- It still does nothing at all -> the drawing never worked, and everything I have written about repaints is beside the point. That would send me looking at the renderer itself rather than at when it is asked to run.

The debug log still measures the route's REAL duration, separately, because that number decides what this should settle into permanently. If a route genuinely takes two seconds, a one-second hold is the wrong answer even if it looks right.

This is a diagnostic arrangement and I am not proposing to leave it as the final behaviour.

**Adam, 2026-08-29 (triage).** Works.

Confirmed it works.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 19:21 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-218"></a>

### MT-218 - 2026-08-29 - Find Route

**Disposition:** fixed validated
**From:** FR-044
**Written:** 2026-08-29

You: "addition to the route jmenu: 'Find Route' popup that asks the user for the route name.  system
jumps to the route page and highlights the matched cell (scrolling to it if needed), otherwise shows a
notice that it doesn't exist."

It is the first item on the **Routes** menu, above Export and Import - those two are about the route
list as a file, and this is about finding your way around it, which is far more likely to be what
somebody opening that menu wants.

What a test can hold is which route a search picks, because that has no appearance and fails silently -
it would confidently highlight the wrong one. The jumping, the scrolling and the highlight need your
eye.

1. **Routes -> Find Route.** Type a route's name exactly. It should switch to the routes tab, scroll to
   that route if it is out of view, and wash the cell pale yellow.
2. **Type it in the wrong case.** Same result - matching ignores case.
3. **Type a fragment that fits one route only**, say part of a name. It should find it.
4. **Type a fragment that fits several.** You should get the "no route called..." notice rather than
   being landed on one of them. Guessing between two would put you in front of a route you did not ask
   for with nothing on screen saying which it chose.
5. **A name that fits nothing at all**, and **Cancel**, and **an empty box**. The first should say so;
   the last two should do nothing quietly.
6. **The highlight should not outstay its welcome**: it clears when you click anywhere in the route
   table, and when the table is rebuilt by a sync, an import or an edit.
7. **One case worth trying deliberately**: if you have a route whose whole name is also part of another
   route's name, type the short one in full. It must find that route rather than calling it ambiguous -
   typing a name in full is the most definite thing you can do and the worst thing to refuse.

---

**Adam, 2026-08-29 (triage).** Works, with notes.

Works, but let's support partial matching if there is no exact match as entered.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 18:27 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.**

Partial matching was there; what it did when your fragment fitted MORE than one route was give up and tell you there was no such route, which is what you will have hit. The reasoning was that landing silently on one of two candidates puts you in front of a route you did not ask for - that still holds, and refusing was still the wrong conclusion, because the third option is to ask.

So a fragment matching several now offers them in a dropdown. An exact name still comes back alone and is never offered as a choice: with 'Yard' and 'Yard Bypass' on a layout, typing 'Yard' in full must find that route rather than asking you to confirm the name you just typed.

The prompt it asks with, route.ui.promptWhichRoute - 'Which route?' - was already in all eight bundles and used by nothing at all. It was written for this and never wired up, so no new string was needed.

**Adam, 2026-08-29 (triage).** Works, with notes.

Works, but have a hover over the highlight turn it off.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 19:21 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.**

Works, and the note is done: hovering over the highlight now clears it. Find Route's wash exists to say 'it is this one', and once the pointer is on it, it has been read.

---

<a id="mt-219"></a>

### MT-219 - 2026-08-29 - The autonomy strip above the track diagram

**Disposition:** fixed validated
**From:** OB-148
**Written:** 2026-08-29

You: "if a track diagram is wide, the 'show autonomy controls ... start' header above a track diagram
flickers while scrolling sideways" - and, asked which part: "It's the whole strip."

It was the scroll pane's **column header**. A column header is as wide as the view and scrolls
horizontally with it, which is right for column labels and wrong for a strip of controls: every
sideways scroll step repainted a panel as wide as the whole diagram, and the checkbox and Start button
slid away from under the hand reaching for them. You chose to take it out of the scroll pane, so it now
sits above the viewport and neither scrolls nor repaints on scroll.

The container's layout comes from the form and must not be hand-edited, so the swap is made at runtime
through `GroupLayout.replace`, which exists for exactly this.

1. **A wide diagram, scrolled sideways.** No flicker in the strip.
2. **The controls should stay put** while the diagram moves under them - that is the part you did not
   ask for and get anyway, and it is worth confirming you want it.
3. **The strip should still be the full width of the panel**, sitting directly above the diagram with
   no gap or step between them.
4. **Vertical scrolling**, and **resizing the window**, and **switching between pages** - the strip has
   a new parent, so the ways it could now be laid out wrongly are the ordinary ones.
5. **Everything on it still works**: the Show Autonomy checkbox, the findings count and its click
   through to the editor, and Start / Graceful Stop.
6. The **autonomy banner** sits above the strip in the same stack - check the two still read as one
   thing rather than two panels that happen to be adjacent.

---

**Adam, 2026-08-29 (triage).** Works, with notes.

Looks good, but: 
1. remove the gap below the gray bar and the track diagram when there is autonomy but not loaded yet.  the red "this page is excluded" banner already does this correctly.

*Run against commit 3f8e9691, build\classes, compiled 29 Aug 19:21 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-29.**

The base works, so this is validated. Your note - the gap below the grey bar when autonomy exists but is not loaded - is fixed and has moved to MT-220 with OB-149 and OB-151, because all three move the same pixels and are best looked at in one pass.

The gap was the strip itself: with a setup that exists and is not loaded, every child of it hides, so what remained was an empty panel with its own border. AutonomyBanner had already solved exactly this for itself one band higher, in those words.

---

<a id="mt-220"></a>

### MT-220 - 2026-08-29 - The autonomy notices above the diagram

**Disposition:** fixed validated
**From:** OB-149, OB-151, MT-219
**Written:** 2026-08-29

Three notes about the same band, done together because they move the same pixels.

- **OB-149:** "there is slightly more top padding than bottom. reduce the top to match."
- **OB-151:** "increase font size... slightly, to match the standard size" and "center them vertically
  within their shaded backgrounds."
- **MT-219:** "remove the gap below the gray bar and the track diagram when there is autonomy but not
  loaded yet."

The gap was the strip itself. With a setup that exists and is not loaded, every child of it is hidden -
the checkbox needs a loaded setup, so does the Start button, and the findings count stands down under a
banner already saying the same thing - so what was left was an empty panel with its own border. The
banner had already solved this for itself, in those words; the strip does it now too.

The font was 13 where `docs/UI-standards.md` calls regular text 14. Close enough to look deliberate and
not right, which is the exact failure that document was written after.

1. **A setup that exists and is NOT loaded.** The banner offers to load it, and there should be no
   empty band between that banner and the diagram.
2. **Load it.** The strip appears with the checkbox and Start, still flush with the diagram.
3. **A page excluded from autonomy** - the red notice. That case was already right; check it still is.
4. **Read both notices.** They should be the same size as ordinary text elsewhere, and sit centred in
   their coloured bands rather than resting on the top edge.
5. **The Start button's padding**: even above and below.
6. My first attempt at OB-149 took two pixels off the top only, which fixed the padding and broke the
   centring. Both insets came down together instead. If anything now looks *too* tight, say so - that
   is the direction I moved.

**Adam, 2026-08-30 (triage).** Works, with notes.

Works, but if a page is excluded, instruct the user to click on edit to modify.

*Run against commit 794d56b9, build\classes, compiled 30 Aug 01:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30.** Addressed: the strip said only that the page was left out, with the remedy
buried in a tooltip. It now reads "This page is left out of autonomy - click Edit to change that", in
all eight languages.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 697417f9.*

---

<a id="mt-221"></a>

### MT-221 - 2026-08-29 - Warnings when a length is missing

**Disposition:** fixed validated
**From:** FR-046
**Written:** 2026-08-29

You: "add a warning to the autonomy list if any train placed on that page does not have a length set,
and also add a warning if any station doesn't have a maximum length set. also, move the Maximum Train
Length right click setting into the Station submenu, rather than Advanced, and hide it if the point is
not a station."

The two warnings are the two halves of one sum. Autonomy refuses a platform to a train too long for it,
and a missing number at either end does not fail that rule - it opts out of it, so a railway carefully
set up with maximums runs as though they were not there. Warnings and not errors: a layout where
nothing is ever close to too long is a perfectly good layout.

**The menu move reverses your own MT-104 decision**, where you put it back into Advanced because it is
"a number you set once and rarely look at". I have rewritten the comment that recorded that rather than
leave it saying the opposite, and the new reason is different in kind: a maximum length is a fact about
a platform, and Advanced is shown on squares that are not platforms.

1. **Right-click a station.** Maximum Train Length is in the Station submenu with the usage choices,
   carrying its current value, and is NOT in Advanced any more.
2. **Right-click something that is not a station.** It should not be there at all.
3. **Place a train with no length** and look at the findings. One warning, naming the train.
4. **A station with no maximum**, likewise.
5. **Set both** and watch the warnings go.
6. Judgement call for you: on a layout where you have never used lengths, this will produce a warning
   per station and per train at once. If that reads as noise rather than as a to-do list, tell me and
   I will make it one finding per kind rather than per square.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 794d56b9, build\classes, compiled 30 Aug 01:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-222"></a>

### MT-222 - 2026-08-29 - Setting a train's length, and naming the autonomy functions

**Disposition:** fixed validated
**From:** FR-045, FR-047
**Written:** 2026-08-29

Two features that share a rule: **both only appear when autonomy is actually LOADED**, not merely
available. My first attempt guarded on "a graph exists", which is true on a layout whose setup nobody
has loaded - you caught it, and all three sites now ask one predicate on the window.

**FR-047 - the train length.** Settable from the right-click menu on a key mapping and from the
right-click menu in the locomotive database, both named **Autonomy Train Length** and both showing the
current value. It uses the same 0-20 dropdown the autonomy editor's Edit Locomotive view uses.

**FR-045 - the two function slots.** Ticks on the function's own right-click menu, and the same two on
the full edit popup. A slot holds one function, so ticking takes it from wherever it was; the label
names the current holder first. Both slots may be the same function.

1. **With no autonomy loaded**: neither the length item nor the function ticks should be anywhere.
2. **Load a setup.** Both appear.
3. **Set a length from the key mapping**, then check the locomotive database menu shows the same value.
   Then set it from there and check the first agrees.
4. **The dropdown should offer 0 to 20** and open on the current value.
5. **Tick a departure function.** Reopen: the tick is there. Tick a DIFFERENT function as departure -
   the first should give up the slot, and the menu on the old one should now name the new one.
6. **Tick the same function as both** departure and arrival. That is allowed.
7. **Untick one** - the slot should clear, which the autonomy editor's dropdowns cannot express.
8. **Cross-check against the autonomy editor's Edit Locomotive view.** Its dropdowns and these ticks
   are the same two fields and must agree in both directions.
9. **The consolidated menus.** Set/clear icon, customise function icons, edit name and address, edit
   notes, find similar, delete - these are now one implementation shared by both menus. Their order and
   their keyboard tooltips are unchanged, but please try each one from BOTH menus.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 794d56b9, build\classes, compiled 30 Aug 01:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-223"></a>

### MT-223 - 2026-08-29 - Two configurations that should refuse to run

**Disposition:** fixed validated
**From:** OB-150
**Written:** 2026-08-29

You: "when turning ON 'page 3 - top parking', there is no autonomy error telling me that its s88
sensors are duplicative with ones on page 1. This config should not be possible to run. there should
also be an error if there exist ANY active (not excluded/disabled) links not linked to anything."

**Duplicate sensors** are now an error. The automatic shutting only happens when a configuration is
created or a legacy graph is imported; a page switched back on by hand afterwards was silent. One s88
on two squares becomes two Points and nothing downstream can say which one a train is on, so this is
not a preference - the railway cannot be modelled in that state.

**Unpaired links** were already reported, as warnings, and the comment beside them argued against
blocking: an imported Central Station diagram carries page-jump arrows nobody has paired, and refusing
to run until each is dealt with would be unusable.

**There turned out to be three kinds, not two, and the suite found the third before you could.** My
first cut split them by whether track runs into the link, and `testTheDiagramIsNotRefused` - the test
that reduces a real diagram and insists it still runs - went red. What refused it were the two arrows
on Main pointing at "3 - Top Parking", a page deliberately excluded from autonomy.

An arrow to an excluded page **cannot be paired**: there is no tile on the far side to pair it to,
because that page is not in the graph at all. So an error there names a fault whose only remedy is to
switch each arrow off by hand - and excluding the page was already the deliberate act saying autonomy
does not go there. Worth noting that your own `setup.json` disables exactly those two squares, so you
met this and worked around it manually; the rule is meant to catch a hole you did not know about, not
to bill you for one you already decided on.

So, in full:

| The link | Reported as | What it tells you to do |
|---|---|---|
| Nothing joined to it | Warning | pair it, or leave it out |
| Track runs into it, destination page is **excluded** | Warning | include that page, or switch the link off |
| Track runs into it, destination page is **in autonomy** | **Error** | pair it, or switch it off |
| Disabled, or its partner is | Not reported | - |

The middle row has its **own message** now, added after you asked for one. It was borrowing the error's
wording, so two findings read identically and differed only in severity - and that wording tells you to
"right-click it to pair it", which is the single thing that cannot work here: the page on the far side
is not in autonomy, so there is nothing to pair it with.

1. **Switch "3 - Top Parking" back on.** You should get an error naming the page, and the setup should
   refuse to run.
2. **Switch it off again** - the error goes.
3. **Draw a link on a page in autonomy, join track to it, point it at another page in autonomy, and
   leave it unpaired.** An error, and no start.
4. **Draw a link with nothing joined to it.** A warning only, and the setup still runs - this is the
   case the old comment was protecting and I have deliberately kept it.
5. **Draw a link with track running into it that points at an EXCLUDED page.** A warning, and the setup
   still runs. This is the third kind, and it is the one that was wrong for a while. **Read the message
   itself**: it should talk about the page not being part of autonomy and offer to include the page or
   switch the link off - not tell you to pair it. If it reads the same as step 3's error, the new
   message is not reaching this case.
6. **Disable a link** that is unpaired. Neither should be reported.
7. **The case still worth trying hardest**: a diagram imported from the Central Station with several
   unpaired arrows on it. That is what this whole distinction exists to keep runnable, and one real
   diagram agreeing is not the same as yours agreeing.

**Adam, 2026-08-30 (triage).** Does not work.

Mostly works, but the error about a duplicate s88 does not specify which one is the duplicate.

Also, the error about duple curved tiles doesn't make sense.  Only one track has the s88, the other one is a static connector track between other tiles.  It should work just as normal within the model, and the user will know where the train is because only one track has a possible station.

*Run against commit 794d56b9, build\classes, compiled 30 Aug 01:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30.** What changed after your run:

**The duplicate now names the sensor.** It said only which page repeated something; it now reports one
finding per repeated s88, naming the address and BOTH pages it appears on - the one it is on and the
one it was first seen on. That is what "does not specify which one is the duplicate" was asking for.

**The double-curve warning is gone.** You asked for a specific failure scenario and I could not give
one.

The hazard it described is real in the abstract: such a square draws two curves, the reduction makes
ONE Point of it, and that Point carries one s88 - so if the contact is on only one of the two tracks, a
train routed over the other passes without triggering it. What makes it unreportable is that **which
track carries the contact is not in the diagram**. The .cs2 records one tile, one type, one article
number; the wiring is in your layout and nowhere the program can read. So the check could only ever say
"two curves and one sensor, and I do not know whether that matters" - to every such square, every time.

For the record, there is exactly one on your railway: (5,16) on "3 - Top Parking", article 1015, track
on all four sides - which is why it fired. It is on a page normally excluded from autonomy.

What would bring it back: a way to record which curve carries the contact, at which point the check
becomes "a route uses the curve without the sensor" and has a remedy. The reasoning is in
GraphReducer.buildPoints where the check used to be, so it is not lost.

1. **Switch "3 - Top Parking" back on** and confirm the double-curve notice is gone.
2. **The duplicate-sensor error should still be there** and should now name the sensor - it is the same
   page, so the two were easy to confuse.

**Adam, 2026-08-30 (triage).** Does not work.

clicking on the sensor notice still does not highlight the sensor on the current page (i.e. page 3)

*Run against commit 697417f9.*

**Claude, 2026-08-30.** Fixed: the finding now carries the square, so clicking it goes there.

It was built with a null tile, and the note beside it explained why - "the fault is that two squares
somewhere carry one address, and naming one of them would send the reader to an innocent-looking
sensor". That was true while the message said only "this page repeats something". **It stopped being
true when I made it name a specific s88**, and I did not notice: the finding was about one square, the
check already knew which, and it was throwing it away. A finding that names a square and cannot take
you to it is the worst of both.

It carries the copy on the REPEATING page - the one you are being asked to do something about - rather
than the page that had the address first, which is innocent and is named in the text instead.

One thing that came with it and is worth knowing, because it nearly shipped broken: a finding's message
renders `{0}` as its subject while it has no tile, and as the SQUARE'S DESCRIPTION once it has one. So
giving this finding a tile would have replaced the sentence naming the sensor and both pages with
"3 - Top Parking 5,16". The sentence moved to `{1}`, and there is now a test that says so.

1. **Switch "3 - Top Parking" on** and click the duplicate-sensor error. It should take you to the
   repeating sensor on that page and highlight it.
2. **Read the message while you are there** - it should still name the s88 and both pages, not just
   the square.

**Adam, 2026-08-30 (triage).** Works, with notes.

I only see 2015 reported as a conflict, but there are many other sensors that conflict between page 1 and page 3, since page 3 is basically a zoomed view of page 1.  all conflicting sensors should be listed.

*Run against commit 697417f9, build\classes, compiled 30 Aug 04:30 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30.** Fixed, and it was my own over-correction from one round earlier.

A reviewer had pointed out that a page which repeats a sensor must not go on feeding its OTHER sensors
into what counts as already seen - otherwise a third page sharing one of those is reported too, blamed
against the page that should itself have been switched off. That is a rule about the seen set, and one
flag is the whole of it.

I also added a `break`, which stopped the scan at the first clash. So a page that duplicates forty
sensors reported one. **The comment directly above it says "three repeats on one page are three things
to fix, and rolled into one line, fixing two of them changes nothing about the message"** - I wrote the
reason for reporting them all and then broke out of the loop that would have. A zoomed-in copy of
another page is exactly the case that makes it obvious, and it is the case you have.

Both rules are now pinned by one test, because satisfying either by breaking the other is easy: a page
clashing on TWO sensors must produce two findings, and a third page sharing only what the repeating
page brought must produce none. Checked failing with the `break` back in.

1. **Switch "3 - Top Parking" on.** Every sensor it shares with page 1 should now be listed, not just
   2015 - so expect a lot of them, one per clashing sensor.
2. **Click a few** and confirm each lands on its own sensor on page 3.
3. **Tell me if that is now too much noise.** Forty errors for one zoomed page is honest but it may
   read as a wall; if so the right shape is probably one finding per page with the sensors listed
   inside it, and that is a small change from here.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 697417f9, build\classes, compiled 30 Aug 04:36 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-224"></a>

### MT-224 - 2026-08-29 - The two train-length warnings, and what they name

**Disposition:** fixed validated
**From:** OB-153, OB-154
**Written:** 2026-08-29

You, OB-153: "autosetup.ui.checkNoTrainLength is prefilled with the station name, not the train at that
station name. state both ({train} and {station} has no...)"  OB-154: "autosetup.ui.checkNoMaxTrainLength
does not specify the station name".

**One line caused OB-153, and it is written twice.** Both the viewer and the editor build a finding's
text as `subject = tile == null ? finding.getSubject() : describeTile(tile)` - so a finding that has a
tile has its own subject thrown away and replaced by the tile's description. That is right for every
finding whose subject IS the point it is about: an unnamed Point's name is its coordinate, and the
tile's description is far more use. FR-046's train-length warning is the first whose subject is a
**locomotive**, and that preference silently swallowed it, so the warning named the station the train
was standing at instead of the train.

Fixed by giving the message **both** rather than by changing which one wins: `{0}` still means exactly
what it meant everywhere, so no other message in the list moved, and `{1}` is new and carries the
finding's own subject. OB-154 needed no code at all - the station's name was always being passed, the
sentence just never used it, and said "This station has..." where it could say which.

1. **A train with no length, standing at a named station.** The warning should name **both** - the
   train, and where it is standing.
2. **The same train at an unnamed square.** The place becomes the square's description rather than a
   name; the train should still be named.
3. **A station with no maximum length.** It should name the station, not say "This station".
4. **Both warnings at once**, on the same page. They should agree about what the station is called.
5. **Look for a literal `{1}`** anywhere in the findings list. That is what an unfilled placeholder
   renders as, and it is how this fix fails if only half of it is present.
6. **The rest of the findings list.** This is the change most likely to have side effects: every
   finding goes through the line I touched. Nothing else should have changed wording at all - if
   another message has gained or lost a name, that is a regression from this.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 794d56b9, build\classes, compiled 30 Aug 01:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-225"></a>

### MT-225 - 2026-08-30 - What the diagram editor tells the autonomy setup

**Disposition:** fixed validated
**From:** LE-A1, LE-A4, LE-A5, LE-A6, LE-A7, LE-B1, LE-B6, LE-C1, LE-C2
**Written:** 2026-08-30

Five fixes from [the layout editor review](../reviews/2026-08-30-layout-editor-review.md), all of the
same shape: you change the track, and something has to carry - or drop - the autonomy setup that was
keyed to it.

**Why this needs you.** Nothing in the suite builds a `LayoutEditor`, so all five are pinned by reading
the source and checking the call is in the right place. That is not the same as checking it does the
right thing, and the difference is what these steps are.

1. **The one that lost work (LE-A1).** Set a platform up properly - station, name, a maximum length, a
   facing, and stand a locomotive on it. Select it **as part of a group** (drag a box round it and at
   least one neighbour), **Ctrl+X**, move a couple of squares away, **Ctrl+V**. Everything should
   arrive with the track: the station, the name, the length, the facing, the locomotive.
2. **The same move by dragging** the selection, and **the same move one tile at a time**. Both always
   worked; check they still do. If the group cut now works and one of these has broken, I traded one
   for the other.
3. **Cut once, paste TWICE.** The first paste is the move and should carry everything. The second is a
   copy: it should NOT bring the station, the name or the locomotive with it, or one train ends up
   recorded on two squares.
4. **Copy - not cut - and paste.** The original must keep everything. This is the case I could most
   easily have broken while fixing 1.
5. **Undo after each of those.** All three fixes snapshot before they change anything, so Ctrl+Z should
   put both the track and the setup back.
6. **The stranded name (LE-B1).** Name a station on the bottom row so its caption lands on the blank
   row beneath it, then press **-** to shrink the page. The name should disappear from the diagram and
   the autonomy list should start saying that station is not shown anywhere - which is the prompt to
   place it again. What must NOT happen is the old behaviour: the name silently gone and nothing
   reporting it.
7. **Clear Diagram (LE-C2)** on a page with a setup, then look at the autonomy findings. They should be
   about a page with no track, not about squares that still claim stations and locomotives. Undo, and
   the setup should come back.
8. **Right-click the bottom row** and open the diagram submenu. **Shift Up** should be greyed, with a
   tooltip saying why. Same for **Shift Left** on the rightmost column. Anywhere else both should be
   available and work (LE-C1).
9. **Cut it and paste it straight back** where it came from, same top-left square. The diagram ends
   up identical, and everything - station, name, length, facing, locomotive - must still be there. This
   one used to wipe the whole block's setup with nothing on screen to say so (LE-A4).
10. **A non-rectangular cut.** Shift-click two squares that are NOT adjacent - say opposite corners of
   a 4x4 area - so the selection has holes in it. Cut, then paste somewhere else. The squares you did
   NOT pick must keep everything: they still hold their track, and their stations, names and
   locomotives must not have moved (LE-A5).
11. **Cut, then leave the page and answer Discard** when it asks about unsaved work. The track comes
   back. Now paste somewhere: it must behave as a COPY - the restored squares keep their setup
   (LE-A6).
12. **Cut a block, drag a different set-up station onto one of the squares you emptied, then paste the
   block elsewhere.** The station you dragged must keep its setup (LE-A6).
13. **Cut on one page and paste on another.** The setup stays on the source page - it does NOT follow
   across. That is deliberate: a cross-page move could not be undone, because the paste only snapshots
   the destination page and leaving the source page throws its history away. Tell me if you would
   rather have the carry and lose the undo.
14. **The one that was actually broken in what I pushed** (LE-A7): steps 1 and 9 are the two that
   matter most, because for two commits the whole carry-the-setup fix was unreachable - it was there,
   it compiled, the tests passed, and it never ran. If either of those two behaves like the old bug,
   that is what has happened again.
15. **The routing-rule warning** (LE-B5): if you ever pick a routing rule and see a log line saying it
   could not be stored, that is working as intended - it means there was no autonomy configuration to
   put it in. Tell me if you see it when a configuration IS loaded.
16. **Judgement call for you**, on step 6: I chose to DROP the caption rather than refuse the shrink,
   on the grounds that a caption is where a name is drawn and not the name itself, so nothing is really
   lost and the checker will ask you to place it again. If you would rather the shrink refused and kept
   the caption where it was, say so - it is a small change in the other direction.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 794d56b9, build\classes, compiled 30 Aug 01:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-226"></a>

### MT-226 - 2026-08-30 - The routing-logic menu says what the railway is doing

**Disposition:** fixed validated
**From:** LE-B3, LE-B4, LE-B5
**Written:** 2026-08-30

Two findings about the menu that reports and sets the routing rule, both from the second review pass.

**It always ticked "At Random" (LE-B3).** The menu is built in the window's constructor, and reads the
current rule from a model that is not assigned until afterwards - so it read nothing, ticked the
default, and was never rebuilt. Your railway would route by whatever the configuration said while the
menu insisted it was random. It is now ticked when you open it.

**A choice made before this moved would have been lost (LE-B4).** The rule used to live in the
application's own preferences; it lives in the autonomy configuration now, and nothing carried the old
value across. It is carried once, into the configuration, and the old setting removed.

1. **Open the autonomy menu and look at Choose Routing Logic.** Whatever is ticked should be what the
   railway is actually using.
2. **Pick a different rule, close the menu, reopen it.** The new one is ticked.
3. **Restart TrainControl and open it again.** Still the one you picked - it is in the configuration
   now, not in the window.
4. **Load a DIFFERENT autonomy configuration**, if you have one, and open the menu. It should show that
   configuration's rule, not the one you set on the other.
5. **The migration (LE-B4)** only happens once and only if you had an old preference saved, so it may
   already have run silently by the time you read this. If the rule you find ticked on first opening is
   one you remember choosing months ago, that is it working.
6. **Worth watching for**, since I could not test this end to end: pick a rule, then check the autonomy
   findings and a route actually taken. "Over the Shortest Track" and "Over the Longest Track" should
   now genuinely differ - most of your edges carry no length, and until this week both of those scored
   every route as zero and picked the same one.

**Adam, 2026-08-30 (triage).** Does not work.

When switching from one config to the other, the option stays put.

Since it is now tied to the autonomy config, move the preference to a DROPDOWN below the "maximum active trains" slider in the autonomy settings tab on the autonomy sidebar page.  Be sure it fits within the current panel, not outside of it where the "edit..." button is.

*Run against commit 794d56b9, build\classes, compiled 30 Aug 01:43 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30.** What changed after your run:

**Moved, as you asked.** The routing rule is now a dropdown in the autonomy settings, directly beneath
the maximum-active-trains slider and inside that bordered panel - not out where the Edit button is.

**That is also the fix for "the option stays put".** Every other setting in that panel is pulled from
the loaded layout by one method, which runs when a configuration is loaded; the rule was the only one
living in a menu that read the layout whenever it happened to be opened. Beside its siblings it follows
a configuration switch for the same reason they do.

The menu entry is gone. Two places to set one value is what produced the always-says-RANDOM bug in the
first place.

**Claude, 2026-08-30.** Second attempt: my version of the dropdown was built in code, taking the departure
checkbox's slot with GroupLayout.replace because the slider's slot is pinned at 55 pixels - and it
looked wrong, which you said. It is gone, along with the layout surgery.

The control is now the `algorithmType` combo you added in the designer, beside your "Path Selection
Logic" label, so nothing in the code touches that panel's layout at all. What the code does is fill it
in, translate the label at runtime (the designer wrote the English straight into the form, and the form
is yours), and keep the selection in step with the loaded configuration.

Also new since your run: an eighth rule, **Weighing Station Priority Against Distance**. It is the only one
that looks past the highest priority band - the others settle that first, which is why "highest
priority available station" needed no rule of its own. With no priorities set it behaves like Over the
Shortest Track.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit 697417f9.*

---

<a id="mt-227"></a>

### MT-227 - 2026-08-30 - Undo after shrinking a page does not strand a station name

**Disposition:** fixed validated
**From:** RC-B1
**Written:** 2026-08-30

Shrinking a page drops the captions on the row and column it removes - that is LE-B1, which you have
already tested. What it did not cover is undoing the shrink: the caption comes back, and the row it
stood on does not, because the page SIZE is not part of an undo entry. The name was then present in the
setup, never drawn, and with no square left to click to remove it.

A caption outside the page is now dropped wherever it came from, rather than only where the shrink put
it.

1. **Put a station name on the last row or last column of a page**, on a blank square next to the
   platform.
2. **Press "-" to shrink the page.** The name goes, as it did before.
3. **Press Ctrl+Z.** The track comes back. **The name must not** - it belonged to a square the page no
   longer has, and it would have nowhere to be clicked.
4. **Do the same with a caption well inside the page** and shrink from an edge that does not touch it.
   That one must survive the undo untouched.

*Run against commit 204a13ca or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 12:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-228"></a>

### MT-228 - 2026-08-30 - A cut keeps its setup when the paste is not the very next thing you do

**Disposition:** fixed validated
**From:** RC-A1
**Written:** 2026-08-30

This is the one worth your time. A group cut carries everything the setup knows about the squares it
empties - station flags, names, lengths, facings, barred arrivals, signal pairings, portal partners,
placed locomotives - and it carries them on the PASTE, because that is the other half of the gesture.

Any edit in between used to cancel that. Not because the edit had anything to do with it: the rule was
"any edit stands a pending cut down", and every edit in the editor goes through the same place. So cut a
yard, press "+" to make room, paste it, and the move had silently become a copy with the whole setup
abandoned on the empty squares - where the next save prunes it.

It now asks, per square, whether that square is still empty and on this page, which is what the flag was
standing in for.

1. **Set up a small block of squares properly** - two or three stations with names, lengths, and a
   locomotive placed on one.
2. **Select them and Cut.**
3. **Do something unrelated:** press "+" to grow the diagram, or rotate a tile somewhere else, or drop a
   tile from the palette.
4. **Paste the block somewhere empty.** Everything should have travelled: names, lengths, the placed
   locomotive, the lot. Check the autonomy editor, not just the diagram.
5. **The other direction.** Cut a block, press **Ctrl+Z** so the track comes back, then paste. The setup
   must have STAYED on the restored squares and must NOT be on the copy - the originals still hold
   track, and stripping them would be worse than the bug this fixes.
6. **And across pages.** Cut on one page, switch to another, paste. The setup stays on the source page
   (deliberately - a cross-page move could not be undone). Then switch BACK to the source page and paste
   there: that one SHOULD carry it, which it never used to.

*Run against commit 204a13ca or later.*

**Adam, 2026-08-30 (triage).** Could not run this.

When the size is changed, the selection vanishes, so I cannot cut/move it.  Cut only applies to a single square.  This is probably OK as is, though I'd prefer less flickering when making the selection.

*Run against commit c386be96, build\classes, compiled 30 Aug 12:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30.** The group cut IS there - it is the ORDER that defeated it.

Cut selected is on the right-click menu, and there is a keyboard shortcut, but both only appear while
something is selected - and changing the diagram size clears the selection. So selecting, then resizing,
then looking for Cut leaves only the single-square one.

**The steps below already said to cut first** - step 2, before the resize in step 3 - and I wrote here
that I had reworded them, which was not true and is the sort of thing this round has spent all day
fixing. They are unchanged. What was missing is the warning, which is now step 0: resizing the diagram
clears the selection, so anything you meant to cut has to be cut before you touch the size.

**The flickering is real and I found the cause, but have not fixed it.** Dragging out a selection calls
refreshSelectionBorders on every mouse-motion event, and that calls clearBordersFromChildren, which
calls setBorder on EVERY tile in the grid before re-applying the outline to the picked ones. Several
hundred setBorder calls per mouse move, each one a repaint.

Fixing it properly means remembering which border each tile currently carries so that only the
difference is touched - and four separate places set a tile's border (the hover reset, the highlight,
the clear, and the drag grip), so all four have to keep that record or a tile is left with a stale
outline. That is a worse failure than the flicker, and this is the same code RC-C10 was about, so I
have left it rather than rush it. Filed as OB-157.


**Adam, 2026-08-30 (triage).** Does not work.

can't we fix it by remembering which tiles were just added/removed to/from the selection area, and then only repaining those?

*Run against commit c386be96, build\classes, compiled 30 Aug 14:05 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30 (second round).** Done, the way you described - only the tiles whose outline
changed are repainted.

It is done one level below the selection, which covers more than the selection alone.
`clearBordersFromChildren` has eight callers and `highlightLabel` several more, so changing what either
of them MEANS would change all of them. Instead each records the border it just applied, as a short
key, and skips the call when the key has not changed. Every caller keeps its contract and the repaints
go away.

**The key has to name everything the border depends on** - palette or diagram, autonomy mode or not,
grey grid on or off - because all three can change while the same tiles stay on screen, and a key that
left one out would skip a repaint that was needed. A stale outline is worse than the flicker.

Two other places set a tile's border directly, the caption drag mark and the drag grip, and both now
drop their record - otherwise the next refresh would trust a key that no longer describes the tile.

1. **Drag a selection box** across a busy page. It should follow the mouse without the whole diagram
   flashing.
2. **Watch a tile that is already picked** as the box passes over it - it should not flicker at all.
3. **Toggle the grey grid** (and switch in and out of the autonomy editor) while a selection is live.
   The outlines must survive: this is the case a careless version of this fix breaks.
4. **Drag a caption onto a tile and off again**, then re-select - the outline must come back.

**Adam, 2026-08-30 (triage).** Does not work.

the flicker is gone, but when dragging the selected tiles to the bottom of the diagram, a phantom row gets permanently highlighted in blue.  the rest seems to work OK.

*Run against commit c386be96, build\classes, compiled 30 Aug 18:06 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30 (third round).** The phantom row is the grid's padding, and nothing could ever
have taken the outline off it.

The grid is built one row taller and one column wider than the diagram - blank labels that hold the
GridBagLayout together, and the reason the grey grid used to draw an extra row at the bottom (OB-055).
`getValueAt` hands them out like any other square, so a group dragged onto the LAST row had its landing
outline - the pale blue one - painted straight onto the padding underneath. And the routine that takes
outlines off deliberately leaves spacers alone, being the grid's own furniture rather than squares. So
it went on and never came off.

It is refused at the one door every outline goes through, asked of what the label IS - which is how the
clearing side already asks it. The same fault was reachable in red, by dragging a selection box onto
that row, and that goes too.

**Also fixed while there:** a move that is refused for not fitting on the page now repaints. It emptied
the landing set, showed its dialog and returned without redrawing, so the outlines it was asking about
stayed on screen behind the dialog until something else moved.

Filed as OB-161.

1. **Drag a selected group onto the bottom row** and let go. Nothing should be left highlighted below
   the diagram, whether the move was accepted or refused.
2. **The same off the right-hand edge** - the padding column is the same trap.
3. **Drag a group somewhere it does not fit** and dismiss the "would leave the diagram" message. The
   diagram should be clean, not still showing where the group would have gone.
4. **The flicker must stay gone** - drag a selection box across a busy page as before.

*Run against v3_0_0_rc3 or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 21:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-229"></a>

### MT-229 - 2026-08-30 - The routing rule survives the upgrade even when it cannot be stored yet

**Disposition:** fixed validated
**From:** RC-A2
**Written:** 2026-08-30

MT-226 covered the migration working. This is the case where it cannot work yet - no configuration
loaded, or autonomy coming from `autonomy.json` rather than a diagram configuration - and has to wait.

It did not wait. The first attempt kept the old setting correctly and stored nothing; the second attempt
looked at the running layout, saw the value the FIRST attempt had put there in memory, decided the
configuration had answered, and deleted the old setting. The rule was then gone for good and everything
routed at random. The second attempt is one autonomy checkbox away.

This is hard to see unless you still have the old preference, so it may already have migrated cleanly
for you. Worth a look anyway:

1. **Open the autonomy settings and tick something - anything - two or three times**, then check Choose
   Routing Logic. It must still show your rule.
2. **Restart and look again.** Still yours.
3. **If you have a configuration with no routing rule set**, load it, tick a settings checkbox twice,
   restart, and confirm the rule did not fall back to At Random.

*Run against commit eac4f73c or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 12:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-230"></a>

### MT-230 - 2026-08-30 - A layout page that will not read does not cost you its sensors

**Disposition:** fixed validated
**From:** RC-A3, RC-A4, RC-A5
**Written:** 2026-08-30

**Take a backup of your layout folder before running this one.** It is about what happens when a page
is damaged, and the point of it is that nothing is lost - but it is a test about losing things.

TrainControl tolerates a page it cannot read: the others load and the failure is logged. What was not
tolerated is what came after. The sensor cleanup deletes every s88 the LOADED pages do not mention, so a
folder with one damaged page loaded the rest and then permanently deleted every sensor that only
appeared on the damaged one - with the autonomy points watching them. And the warning that would have
explained it was debug-only, so nothing said why.

1. **Copy your layout folder somewhere safe first.**
2. **In the copy, damage one page** - open a `.cs2` file under `gleisbilder` and delete half of it, or
   rename it so the index cannot find it. Pick a page that has sensors of its own.
3. **Point TrainControl at the copy and start it.** You should see a warning naming the page, in plain
   view, not only in debug mode.
4. **Check your feedback list.** The sensors from the damaged page must still be there.
5. **Check the autonomy setup** for the pages that DID load - it should be untouched.
6. **Then damage every page** and start again. This time it should give up on the folder and fall back
   to the Central Station's layout, telling you so - rather than showing you an empty diagram in
   silence, which is what it did.
7. **Point it back at your real folder** and confirm everything is as it was.

*Run against commit 10694670 or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 12:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-231"></a>

### MT-231 - 2026-08-30 - A station you push down is not visited by the longest route

**Disposition:** fixed validated
**From:** RC-B2
**Written:** 2026-08-30

Only relevant if you use **Weighing Station Priority Against Distance** and set a station's priority
below zero.

The rule scores a station as priority-per-unit-of-track. It was written for priorities of zero and up,
and negatives go through the same arithmetic: at -1 every route scored the same, so distance stopped
mattering, and at -2 and below the score went negative - where a LONGER route scores higher. So a
station you had pushed down became the most attractive on the layout, reached by the longest way round.

A de-prioritised station is now worth less than an ordinary one and still ordered among its peers, and
nothing at zero or above is changed at all.

1. **Choose Weighing Station Priority Against Distance.**
2. **Set one station's priority to -2** and leave the others alone.
3. **Run for a while.** That station should be visited LESS than the others, not more, and when it is
   visited it should be by a sensible route rather than the longest available.
4. **Set another station to -5.** It should be visited less often than the -2 one.
5. **Set everything back to 0 and run again** - the behaviour should be exactly what you are used to,
   since nothing at or above zero was touched.

*Run against commit 10694670 or later.*

**Adam, 2026-08-30 (triage).** Could not run this.

make an automated test case for this aganst the current layout, by varying the priority dynamically at test time. use bottommainA/B/C, as these are all reachable by a train arriving from tunnel. try at least 10 combinations of priorities. for test execution speed, set loc delays to 0-1s.  as validation, make sure the distribution is as expected.

*Run against commit c386be96, build\classes, compiled 30 Aug 12:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30.** Automated, as asked - `core.testStationPriorityDistribution`.

Twenty-two priority combinations over BottomMainA/B/C on the frozen snapshot of your railway, with the
train starting at Tunnel, counting where 400 decisions land in each case. It counts DECISIONS rather
than journeys: pickPath is the whole of the rule, so running the trains would add minutes and change
nothing about the answer. The fixture delays are 0 and 1 either way.

**Two things about your railway came out of building it**, and both are why this was confusing by hand:

- **BottomMainC takes a train of 2, and the locomotive parked at Tunnel is 4.** So every route to C was
  refused on length, whatever its priority - C simply never appeared. The test gives its train a length
  of 1 so all three platforms are eligible.
- **The snapshot has trains parked all over it**, and one standing anywhere along a route makes it
  unavailable. The test takes the others off the railway first, so the counts measure the rule rather
  than where things happened to be parked.

There is nothing to run by hand here now.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 14:05 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-232"></a>

### MT-232 - 2026-08-30 - Add Route offers a name, and the editor stays where you can see it

**Disposition:** fixed validated
**From:** RC-B3, RC-B4
**Written:** 2026-08-30

Two things the route editor lost against 2.7.4c.

**It stopped proposing a name.** The next free name is still worked out and was then thrown away, so the
box opened empty and the first Save was refused for not having a name.

**It does not follow the main window's always-on-top.** Every other child window does. It matters during
capture: you tick "capture commands" here and then throw the switches on the layout window, and the
window you are watching went behind the one you were clicking.

1. **Press Add Route.** The name box should already say "Route 4", or whatever the next free number is.
2. **Add one command and press Save.** It should save, without asking you to invent a name.
3. **Press Add Route again** and close it immediately. It should close without asking whether to discard
   anything - the offered name is not your typing.
4. **Turn on always-on-top for the main window**, open the route editor, tick capture, and click over to
   the layout window to throw a switch. The editor must stay visible.
5. **Turn always-on-top off** and confirm the editor behaves like an ordinary window again.

*Run against commit ed33fbdc or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 12:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-233"></a>

### MT-233 - 2026-08-30 - Start with nothing that can start leaves the railway alone

**Disposition:** fixed validated
**From:** RC-B5
**Written:** 2026-08-30

Start marks the layout as running before it looks at a single locomotive, and it skips any locomotive
whose start point is inactive or whose preferred speed has never been set. Skip every one of them and
the layout was left "running" with nothing running - and nothing could ever clear it, because the flag
is cleared by the last locomotive thread finishing and there were none.

While it was in that state, moving a locomotive by hand, renaming a point and switching simulation all
refused, every protecting signal had been commanded, and hand dispatches were judged by the autonomy
rules. Only Stop got you out, and nothing said so.

1. **Pick a locomotive that has never had a preferred speed set** - or set one to 0 - and make it the
   only locomotive in the run list.
2. **Press Start.** It should refuse to run and say that nothing could be started, and the railway
   should be left alone: you should be able to move a locomotive by hand, rename a point, and toggle
   simulation immediately, without pressing Stop first.
3. **The control.** Give it a real speed and press Start again. It should run normally.
4. **Mixed.** One locomotive with a speed and one without: the good one runs, the other is skipped by
   name in the log, and Stop works as usual.

*Run against commit ed33fbdc or later.*

**Adam, 2026-08-30 (triage).** Works, with notes.

The UI won't allow a preferred speed of 0.  If none is set, it should default to 35.  I thought that was already the behavior, verify it.

*Run against commit c386be96, build\classes, compiled 30 Aug 12:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30.** Verified - you remembered it right, and my note was wrong.

`Layout.parseAuto` applies `defaultLocSpeed` to any locomotive whose preferred speed is 0 as it loads,
and says so in the log. So a zero has never reached the railway, and my comment claiming a zero is "the
state of any locomotive placed on the graph without the speed dialog ever being opened" was false for
anything loaded from a file. Corrected to name the case that really is left: a locomotive placed AFTER
the load, which has not been through that path.

**And the write side is closed** - `Point.toJSON` wrote the speed whatever it was, so a locomotive
placed after a load was saved with `"speed": 0`. Nothing broke on the way back in, but the file then
carried a value that means "no value". It is now omitted entirely when it is not a speed, which is what
`arrivalFunc` and `departureFunc` beside it have always done. No config in the repository carries a zero.

1. **Place a locomotive on the diagram** without ever opening its speed dialog, and save.
2. **Look at the configuration file** - the locomotive should have no `speed` at all, rather than 0.
3. **Reload and start autonomy.** It should run at the default speed, and the log should say it applied
   one.

**Adam, 2026-08-30 (triage).** Does not work.

Fails.  added MT-233 Test Loc.  After initial placement, error: Invalid speed specified

Instead of default speed.

It was added via contorl+V on the track diagram viewer.

*Run against commit c386be96, build\classes, compiled 30 Aug 14:05 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30 (second round).** Fixed - and you found the exact case I had named and left open.

`parseAuto` applies the default as it LOADS, so anything from a file has a speed. A locomotive placed by
hand has not been through that path, which is why Ctrl+V on the diagram and then Start gave you "Invalid
speed specified" - that is `runLocomotive`'s own guard, doing its job on a locomotive nothing had given
a speed to.

`moveLocomotive` is the single door onto the graph - the diagram paste, the right-click menu and the
autonomy editor all arrive there - so the same rule the load path applies now runs there too.

Two tests: a placed locomotive gets the default, and one that already has a speed keeps it.

1. **Add a locomotive to the diagram with Ctrl+V**, without ever opening its speed dialog.
2. **Press Start.** It should run at the default speed, and the log should say it applied one - not
   "Invalid speed specified".
3. **Set a locomotive's speed to something of your own**, place it, and check that placing it did not
   overwrite your choice.

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 18:06 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-234"></a>

### MT-234 - 2026-08-30 - A train passing through a station says which way it is going

**Disposition:** fixed validated
**From:** OB-158
**Written:** 2026-08-30

Adam: "previously blocked stations with an active train would get [xxx].  Now I see ... on the label
instead, which is fine.  Those also turn red." - and: "make the ... not be bold, and change it to one
arrow that correctly shows the direction of travel."

The "..." is three bullet characters, put on every station a running route passes through that is
neither its start nor its destination. Nothing sets a bold font on these captions - the bullets are
simply heavier glyphs than the names and arrows beside them, which is what reads as bold. They are now
one arrow.

**The direction needed no working out.** The graph is split by facing, so the copy a route runs through
IS the direction of travel through that square - the same helper the standing train's caption uses,
asked about a station the train has not reached yet. That also settles a complaint its own comment
makes a few lines above: the arrow used to come from the square's stored facing, which is only written
when a train is placed by hand, so it appeared for a train you had placed and vanished for one autonomy
had driven there.

The bullets remain as the fallback, for a graph whose stations are not split and so have no direction
to draw. The red is unchanged - it is the pending-milestone colour every unreached part of a route is
drawn in, and you said it is fine.

1. **Start a run** and watch a train cross a station it does not stop at. The label should show one
   arrow, pointing the way it is travelling, rather than three dots.
2. **Check the arrow is right** - compare it against which way the train actually goes through that
   platform, on a station where the route runs the "wrong" way round.
3. **Watch the same platform on a later run** where the train goes the other way through it. The arrow
   should follow the route, not the platform.
4. **The start and the destination are unchanged** - the origin stays white-on-grey, the destination
   stays yellow with the train's name.

*Run against commit c386be96 or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 14:57 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-235"></a>

### MT-235 - 2026-08-30 - A route button that would conduct track it was not drawn to conduct

**Disposition:** fixed validated
**From:** OB-160
**Written:** 2026-08-30

A route button carries no track of its own - what it conducts is decided entirely by what is beside it.
Two placements are now refused, and both are blocking errors, so it is worth checking they do not fire
on anything you have drawn.

**Two buttons side by side that reach real track at BOTH ends.** Your rule. The pair conducts a route
across squares that have no rails on them. One end is not enough and is not reported - which is the
case on your own layout at `1 - Main:3,7`, where past the far button is a straight lying north-south
that presents no face, so the pair splices nothing.

**Track running into a button from three sides.** The through-pair wins and the third arm is dropped in
silence - the only drop in the reduction that said nothing. Four sides is a fixed crossing, both pairs
are carried, and that is not reported: your "static crossing under the hood".

An automated test runs both checks over the frozen snapshot of your railway and fails if either fires
on it, so a rule that is too strict should be caught before you see it. That test found two mistakes of
mine before this shipped.

1. **Draw two route buttons side by side with track running into both ends.** The setup checks should
   refuse it, naming the square.
2. **Take the track off one end.** The error should go - a pair reaching track at one end conducts
   nothing.
3. **Draw track into a button from three sides.** Refused, naming the square.
4. **Add the fourth side.** The error should go: that is a crossing.
5. **Check your own layout still starts.** Nothing on it should be refused by either rule.

*Run against commit fb3722f5 or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 18:06 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---
<a id="mt-236"></a>

### MT-236 - 2026-08-30 - Nothing is left highlighted below the diagram

**Disposition:** fixed validated
**From:** OB-161
**Written:** 2026-08-30

The grid keeps a blank row along the bottom and a blank column down the right, to hold its layout
together. They are not squares, and nothing that takes outlines off ever touches them - so an outline
put on one stayed there for the rest of the session.

A group dragged onto the last row put its landing outline there; a selection box released on it put the
picked outline there. Both are refused now, at the one place every outline goes through.

1. **Drag a selected group onto the bottom row.** Nothing below the diagram should stay coloured.
2. **The same off the right-hand edge.**
3. **Drag a selection box down past the last row** and release. Same.
4. **Ordinary selection and moving must be unaffected** - the squares you pick still outline, and a
   group still moves where you drop it.

*Run against v3_0_0_rc3 or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 21:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-237"></a>

### MT-237 - 2026-08-30 - The timetable is named before there is a timetable

**Disposition:** fixed validated
**From:** asked for directly - Adam, 2026-08-30
**Written:** 2026-08-30

Adam: "the timetable entry has default table heading when blank.  make sure these are always set."

The table is built by the form designer, which starts every table off with four columns called "Title
1" through "Title 4" and four blank rows. The real headings were only installed when the timetable was
redrawn, and that cannot happen before an autonomy configuration exists - so a fresh installation showed
the placeholder for exactly as long as it took to set autonomy up.

They are set when the window is built now, which needs nothing behind them.

1. **Open the Timetable tab on a fresh start**, before doing anything with autonomy. The columns should
   read Index, Locomotive, Start, Destination, Time - and there should be no rows at all.
2. **Capture something and look again** - the headings are unchanged and the rows fill in as before.
3. **In another language**, if you want to check the translations came through.

*Run against v3_0_0_rc3 or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 21:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30.** You are right, and the premise of this entry was wrong.

The release-candidate review said "on your graph BottomMainC / BottomMainCTerm share feedback 4". It
had read `autonomy.json` in the project ROOT - the old hand-authored graph - where BottomMainCTerm
exists. Your 3.0.0 diagram derives a graph in which it does not, and I repeated the claim into these
steps without checking it against the layout you actually run.

**Checked properly now.** In the derived graph, sixteen sensors carry more than one Point, and every
group of them that holds two active stations is the SAME square emitted once per arrival side -
BottomMainC four times, BottomMainB three times, LowerFront twice, BottomInner four times. There is no
pair of DIFFERENT station squares on one sensor anywhere on it. And there cannot be a home on any of
those squares in the first place: `whyNotAHome` refuses a square that is more than one graph Point,
which was your own ruling on 2026-08-25 - "any home with two graph points should be refused".

So the state this test asks you to set up cannot be reached on your railway, and the entry should
never have been written. **The defect and its fix are real** - the scan proved two homes on one
detection section impossible without the exemption its sibling scan twelve lines below carries, and a
layout where a station and, say, its approach guard are two separate squares on one feedback does hit
it. That is what `core.testHomeStaging.testTwoTrainsAlreadyHomeOnOneSensorAreNotRefused` covers, with
`testTwoActivePointsSharingASensorAreNeverBothOccupied` as the control that keeps the rule from
over-claiming.

Superseded rather than left open, under the first half of the rule in README.md: this should never
have been an entry. Nothing on your layout can run it, and nothing about the fix is unverified.

---

<a id="mt-238"></a>

### MT-238 - 2026-08-30 - Return Home with two trains already parked on one sensor

**Disposition:** superseded
**From:** SG-A1
**Written:** 2026-08-30

**This is the one from the release-candidate review most likely to have bitten you.** On your railway
`BottomMainC` and `BottomMainCTerm` share feedback 4 - a platform and its terminus stub - and if both
are homes with their own trains standing on them, Return Home refused the WHOLE run and named those two.

It proved that two homes on one detection section cannot both be occupied, which is true of an ARRIVAL -
and an arrival is the one thing that does not happen when the train is already there. The scan twelve
lines below it in the same method carries exactly that exemption; this one did not.

1. **Park two trains on their own homes where the two homes share a sensor**, and leave a third train
   away from its home. Press Return Home. It should plan the third train's journey rather than refuse.
2. **Now move ONE of the pair away** and press Return Home again. If its home is genuinely unreachable
   while the other train is parked on the shared section, refusing is right - what matters is that it
   says so about a train that actually has somewhere to go.
3. **An ordinary Return Home** with no shared-sensor homes must be unchanged.

*Run against v3_0_0_rc3 or later.*

**Adam, 2026-08-30 (triage).** Does not work.

BottomMainCTerm is not an active station in the 3.0.0 layout.  It should only be present in the old files, or possibly on page 4, which is inactive.

*Run against commit c386be96, build\classes, compiled 30 Aug 21:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---
<a id="mt-239"></a>

### MT-239 - 2026-08-30 - Editing your own routes does not talk to the Central Station

**Disposition:** fixed validated
**From:** OB-155
**Written:** 2026-08-30

Adam: "the route page should not have to sync with the cs2 after edits/deletions for routes >= ID 1000."

Deleting a route, and renumbering one, synced the whole database back from the Central Station
afterwards - behind a modal spinner, and at twice the connect timeout when the station is off. For a
route numbered 1000 or above there is nothing to fetch: those are allocated here and the station has
never heard of them.

Both doors now ask first, and the renumber asks about BOTH numbers - moving a route out of the local
range is a change the station does need to hear about.

1. **Delete a route numbered 1000 or above.** It should go immediately, with no spinner and no pause.
2. **Do the same with the Central Station switched off.** Same - this is where the old behaviour cost
   the most.
3. **Delete a route numbered below 1000.** The sync must still happen: that one the station knows.
4. **Renumber a route from 1000-something to a low number, and back.** Both directions should sync,
   because one end of the change is a number the station keeps.

*Run against v3_0_0_rc3 or later.*

**Adam, 2026-08-30 (triage).** Does not work.

I don't see any syncs at all, even under 1000, when in debug mode (no station).

*Run against commit c386be96, build\classes, compiled 30 Aug 21:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Claude, 2026-08-30.** Nothing to see is the right answer in that mode, and these steps should have
said so.

`syncWithCS2` re-reads the whole database FROM the Central Station. In debug mode with no station
there is nothing to read, so the call returns having done nothing visible - which means the run you
did cannot tell a skipped sync from a performed one, in either direction. That is my fault in the
steps, not a fault in the change.

**What the change does.** Deleting a route, and renumbering one, used to call `syncWithCS2`
unconditionally. For a route numbered 1000 or above there is nothing to fetch - those are allocated
here and the station has never heard of them - so the call could only cost the round trip, which is
the whole database behind a modal spinner, and twice the connect timeout when the station is off.
The renumber asks about BOTH numbers, because moving a route out of the local range IS a change the
station needs to hear about.

**With the Central Station connected**, which is where this is observable:

1. **Delete a route numbered 1000 or above.** It should go at once - no spinner, no pause.
2. **Delete a route numbered below 1000.** The spinner should appear: that one the station knows about
   and the sync still has to happen.
3. **Renumber a route from 1000-something down to a low number, and back.** Both directions should
   sync, because one end of the change is a number the station keeps.

*Run against v3_0_0_rc3 or later, with the Central Station connected.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 21:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Adam, 2026-08-31 (triage).** Works.

*Run against commit 302d7a11, build\classes, compiled 31 Aug 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-240"></a>

### MT-240 - 2026-08-30 - Two kinds of random in the routing rules

**Disposition:** fixed validated
**From:** OB-156
**Written:** 2026-08-30

Adam: "there should also be a 'by station priority' option that simply uses the station priority and
randomly chooses from the highest available", and then: "let's have one completely random, and one that
respects priority."

There are two now. **At Random, Respecting Priority** is what "At Random" always actually did - the
candidate list is shuffled and then sorted by priority with a stable sort, so it picks at random from
the highest band that has anything free. It was only ever named wrongly. **Completely at Random**
ignores priority altogether, which is the one that did not exist.

1. **Give two stations different priorities** and run with **At Random, Respecting Priority** for a
   while. Trains should keep choosing the higher one while it is free.
2. **Switch to Completely at Random** and run again. Now both should come up, priority or not.
3. **Check the rule survives a restart** - it is stored with the autonomy configuration.

*Run against v3_0_0_rc3 or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 21:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---
<a id="mt-241"></a>

### MT-241 - 2026-08-30 - The routing rules explain themselves

**Disposition:** fixed validated
**From:** OB-163
**Written:** 2026-08-30

Every routing rule has an explanation written for it and translated into all eight languages, and until
now nothing read a single one of them: the dropdown was built from the rule NAMES and carried only the
general sentence about what the control is for. Seventy-two written sentences, on the one control where
ten similarly-worded options have to be told apart.

That is also how "Completely at Random" - added yesterday - came to be the only rule with no
explanation written at all. It has one now, and "At Random, Respecting Priority" has been corrected: it
still described itself as it did when it was simply called "At Random".

1. **Hover the routing rule dropdown** in the autonomy settings. It should say what the control is
   for, and then what the rule currently chosen actually does.
2. **Change the rule and hover again.** The second paragraph should have changed with it.
3. **Try to change it while trains are running.** The dropdown goes back to the rule in force - and the
   tooltip must go back with it, not keep describing the rule that was refused.
4. **In another language**, if you want to check the two new sentences read properly.

*Run against v3_0_0_rc3 or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 21:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---
<a id="mt-242"></a>

### MT-242 - 2026-08-30 - The locomotive is drawn over the station name, not under it

**Disposition:** fixed validated
**From:** OB-159
**Written:** 2026-08-30

Adam: "the locomotive icon sometimes appears BELOW stations while running" - and, asked which sense of
below: "it is a z order issue.  The stations paint over the locomotives.  Placement is OK."

**This and OB-117 pull opposite ways and both are right.** A station caption and the tile under it are
separate components. Give the caption the front and the locomotive standing on that platform is painted
over, which is what you saw; give the TILE the front and the name is painted out and replaced by the
tile's own background, which is what OB-117 was about. Swing has one ordering and no layers, so no
arrangement of the two satisfies both.

So the trains are not an ordering any more. The diagram paints its children exactly as it did - tiles,
then captions - and then walks them once more asking each tile for its train, which lands above both.
The name survives everywhere the locomotive is not.

1. **Run a train through a station whose caption sits over the platform.** The locomotive should be
   whole, on top of the name.
2. **Watch the name.** It should still be readable either side of the locomotive, and come back intact
   the moment the train leaves.
3. **The sensor numbers are unchanged** - a running tile still covers those, which is what you asked
   for when the lift was added.
4. **A stationary train** shows the dot rather than the icon; that should be on top as well.
5. **Export a diagram** while a train is running and check the picture matches the screen.

*Run against v3_0_0_rc3 or later.*

**Adam, 2026-08-30 (triage).** Works.

*Run against commit c386be96, build\classes, compiled 30 Aug 21:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-243"></a>

### MT-243 - 2026-08-31 - Upgrading a 2.7.4c autonomy.json keeps its settings and leaves the routes alone

**Disposition:** needs test
**From:** RGN-A1, IPR-A1
**Written:** 2026-08-31

**Back up first.** File > Backup TrainControl Data, and keep the zip. This is about the import that
reads your old `autonomy.json`, and the point of the test is what it writes.

**What to do.** You need a layout whose autonomy came from 2.7.4c - `cs2_sample_layout` has one at
`config/autonomy_legacy/autonomy.json`, which is your own.

1. Import it from the autonomy menu, into a fresh configuration.
2. **Open the autonomy settings.** Pace, default speed, how many trains may run, the arrival and
   departure function switches, the pre-arrival slowing - they should all match what the old file
   said, not the defaults. Ten settings come across.
3. **Look at a station that had a length limit.** Right-click it: its maximum train length should be
   the number your old file gave it, and the square's own track length should be untouched.
4. **Then the one that matters most: look at your routes.** Open the Routes tab and check that every
   route that was enabled is still enabled. Nothing about importing a graph should switch a route
   off.
5. **Run the setup**, and check the routes again - the disabling used to happen on load rather than
   on import, so it has to be checked after autonomy has actually parsed the configuration.
6. **Import the same file a second time**, having changed one setting by hand in between. Your change
   must survive: this fills gaps, it does not overwrite.

**What will NOT come across, on purpose:** your timetable and your route activations. Both are still
in `autonomy.json`, untouched. Nothing tells you that in the app, which is the open question below.

#### Comments

**Claude, 2026-08-31.** Two reviewers found this independently, from opposite directions.

`importLegacy` read the `points` array and nothing else, so an upgrading user kept their station names
and lost every rule about how their railway runs - ten settings on your own file. And the one thing it
did read besides names, `maxTrainLength`, it wrote into the wrong place: that is a STATION's capacity,
and it went into the SQUARE's track length, which is what the shortest-track and longest-track routing
rules are computed from. So stations lost their limits and six squares gained lengths nobody measured.

**What I nearly shipped instead was worse**, and I would rather you knew. The first fix copied every
key that was not points or edges. Two of those - `activateRoutes` and `activateRouteIDs` - do not stay
inside the configuration: `parseAuto` ends in `applyAutonomyRouteActivations`, which walks your live
Central Station route database and disables every route whose id is not listed. Your legacy file says
`activateRoutes: true` with an EMPTY list. So importing it would have switched off every route you
have, and done it again on every diagram edit. A validator pass found it before it went anywhere near
you. Step 4 is that case.

The timetable is left behind for a measured reason rather than an aesthetic one: its 36 legs name 25
points, three of which carry no s88 at all and nine more of which sit on sensors shared by several old
points, where the name that lands on a square is whichever came first. The loader throws on the first
edge it cannot resolve and drops that leg. Two of the thirty-six could survive - and the next capture
would write the remains back over your settings for good. Leaving it in the file loses less.

*Run against a build after commit 875edb92.*

**Claude, 2026-08-31 (later).** Two more steps, from your note that "the reversal happens on arrival".

That is what makes the terminus rule right - a train turns as it ARRIVES, so whether it may end there
is a question about that locomotive - and it is why step 6 exists. `whyNotAHome` had learned to ask
the whole square; the planner's own reachability scan had not, so the editor accepted a home that
Return Home then called impossible, for a train that would have arrived on the through road and never
turned. Both questions are asked of the same copy now.

Step 7 is a gap this morning's change opened rather than one it found. One home per platform used to
be enforced by the field itself, because a Point holds one home; two copies are two fields.

---

<a id="mt-244"></a>

### MT-244 - 2026-08-31 - The Auto tab on a layout that has only an autonomy.json

**Disposition:** needs test
**From:** RGN-A2
**Written:** 2026-08-31

**This one is a question for you as much as a test.** Nothing has been changed; I want your ruling
before I change anything.

**What to do.** You need a local layout folder with an old `autonomy.json` beside it and NO
`config/autonomy/setup.json` - that is, a 2.7.4c user who has just installed 3.0.0 and not imported
anything yet.

1. Start TrainControl with auto-load on.
2. **Look at the Auto tab.** I expect it to be greyed.
3. **Check the log.** I expect it to say the autonomy layout loaded and is valid.

If both are true, that user's autonomy is loaded and live, and the tab holding the run list, the
timetable, the settings and Return Home is greyed - and the old JSON window has been removed too,
because the layout can hold a diagram setup.

#### Comments

**Claude, 2026-08-31.** Read end to end and confirmed, but not changed, because which half is wrong is
your call.

`getAutonomySession()` returns a session for ANY local layout folder - it needs a path and a folder
that exists, nothing more. Startup then takes the legacy branch, because no diagram configuration is
active, and parses `autonomy.json` into a live layout. `refreshAutonomyTabState` computes
`loaded = getAutonomySession() == null || activeDiagramConfiguration != null`, and for this user both
halves are false, so the tab is disabled. The comment above that line says "on the diagram path the
question is whether a configuration is RUNNING" - which is the right question for the diagram path,
and is being asked on both.

**Two ways to settle it, and they are different products:**

- **The tab should open.** A legacy layout is loaded and running; the operator should be able to reach
  it. That means asking the question differently for the JSON path.
- **The tab should stay shut until they import.** Then startup should NOT parse and activate the
  legacy file first - because as it stands trains can be running with no interface to reach them,
  which is OB-104 turned around.

Either is defensible. What is not defensible is today, where the two halves disagree.

*Run against a build after commit 875edb92.*

**Adam, 2026-08-31 (triage).** Could not run this.

make a test case for this. in my testing, it loaded OK.

*Run against commit 302d7a11, build\classes, compiled 31 Aug 18:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-245"></a>

### MT-245 - 2026-08-31 - A home on a main-line platform, and it is still there next start

**Disposition:** needs test
**From:** DAY-B3

**Written:** 2026-08-31

**What to do.** On your own railway, with a configuration you do not mind editing.

1. **Assign a home** to a locomotive on **BottomMainC** - one of the ten squares the setup draws as
   more than one arrival side. The menu should accept it, as it always did.
2. **Save, close TrainControl, and open it again.** The home should still be there. Check the log
   while you are at it: there should be no line about the square being drawn as more than one place.
3. **Drive the train away** and press Return Home. It should go back to that platform, arriving from
   whichever side the route takes - the direction is not part of the home.
4. **Send it home a second time from the other direction**, if your layout lets you, and it should be
   just as content.
5. **A platform where every copy is a turning berth** - a train has to back out - should still be
   refused to a locomotive that is not reversible. That refusal is real and stays.
6. **A platform with a turning berth on one side and a through road on the other**, if you have one:
   a locomotive that cannot reverse should be able to live there, and Return Home should send it to
   the through road rather than saying it is impossible.
7. **Assign a second locomotive to the other side of a platform that already has a home.** The first
   one should give it up - one platform, one home - rather than both being homed on one piece of
   track, which nothing could ever satisfy.

#### Comments

**Claude, 2026-08-31.** Your ruling, and it settles something that had been answered three different
ways: "so the home should just be the logical point, and the direction is wherever the locomotive was
facing when it started moving."

**Step 2 is the one worth doing carefully, because it is what was broken.** The editor accepted a home
on a split square and the LOADER dropped it, with a log line as the only notice - so a home could be
set, look right, and be gone at the next start. That was LD-8 carrying your 2026-08-25 ruling to a
door a person cannot be warned at. And `AutonomyBuilder.homeCopy` exists for no purpose other than
choosing which copy of such a square should carry the home; the loader threw that answer away every
time.

All three refusals are gone - the menu, the model's setter, and the loader.

**One thing I extended beyond what you said, and you should know which part is mine.** Whether a train
can rest at its home is now asked of the SQUARE rather than of one copy. A platform whose two copies
are a turning berth and a through road is a home for a locomotive that cannot reverse, because it can
stand on the through road; before, it was refused whenever the copy carrying the home happened to be
the turning one - which is a choice the builder made, not one you made. Step 5 is the control: where
EVERY copy must be backed out of, it is still refused.

*Run against a build after commit 7616d2a6.*

**Adam, 2026-08-31 (triage).** Does not work.

mainline stations work, but parking terminuses do not.  consider TunnelLeftPark - EN57-203 cannot even be SENT there, and EN57-947 cannot be homed here.

non-auto stations should still be manually selectable.  and trains should be allowed to back into terminuses if they are not reversible (that's why we have the reversing point at feedback 2013).

*Run against commit 302d7a11, build\classes, compiled 31 Aug 18:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-246"></a>

### MT-246 - 2026-08-31 - Signals stay put, and a non-reversing train backs into its home

**Disposition:** needs test
**From:** OB-166

**Written:** 2026-08-31

**Two rulings in one test, because the second one is the reason the first is safe.**

**The signals.**

1. **Set signal 64 red by hand**, with nothing standing at TopMainR2 and nothing running.
2. **Send a train from BottomInnerOtherside to BottomInner.** Signal 64 must still be red afterwards.
   That is the whole of OB-166.
3. **Start full autonomy** with a train standing somewhere protected. No signal should move until a
   route actually activates over it.
4. **Then watch a route activate over a protected platform** - a train arriving should still turn its
   signal red, and leaving should turn it green. That half must still work.

**What this deliberately gives up**, and the thing to look out for: a train you place BY HAND at a
protected platform while nothing is running now keeps whatever aspect its signal already showed. It is
no longer protected the moment a run starts. If that turns out to matter more than the unnecessary
commands did, say so and I will put the sweep back for occupied platforms only.

**The homing.**

5. **Take a locomotive that is not reversible** and give it a home at a parking terminus -
   TunnelLeftPark, or whichever you tried on MT-245.
6. **Drive it away and press Return Home.** It should go, and it should arrive having been turned
   round on the way, at the reversing point - backing in, not nose-first.
7. **A terminus with no reversing point on the way to it** should now be reported as impossible for
   that locomotive, rather than offered and then failing on the first move.

#### Comments

**Claude, 2026-08-31.** Measured before either was changed, against a copy of your layout rather than
the folder itself.

**OB-166.** Signal 64 protects TopMainR2's two copies and nothing else; both were empty; and the only
route from BottomInnerOtherside to BottomInner does not command it. What moved it was
`refreshAllProtectingSignals` - a sweep that re-commands EVERY protecting signal on the layout from
current occupancy, fired the first time anything is dispatched by hand. It protects and it also
releases, so an empty protected platform gets commanded green.

On your ruling - "Signals should only be touched when a route activates" - the sweep is gone from all
three doors that had it, so manual and automatic still behave the same way, which is your earlier rule.

**The homing.** The runtime already insisted a non-reversible train may only reach a terminus by a
route that turns it. The PLANNER did not know that rule: its reachability check was a plain search, so
a home reachable only by a route with no reversing point read as reachable, and Return Home would hand
you a plan the runtime refuses on its first move. The search carries the requirement now.

The part that made it more than a flag, in case it matters later: a square reached with a reversal
behind it and the same square reached without one are two different states. A turning route may be
longer, so arriving somewhere the short way must not close off the long one.

*Run against a build after commit fbc19cb9.*

---


<a id="mt-247"></a>

### MT-247 - 2026-09-01 - A refused route still cuts the power at the two human doors

**Disposition:** needs test
**From:** FX2-2, SVN-A4

**Written:** 2026-09-01

**This is a question for you as much as a test.** When you decline the conflict dialog, the code has to
decide what you meant, and today the answer differs depending on which door the route came through.

1. **Make a route** that does two things: switches an accessory, and carries an emergency stop.
2. **Get that accessory onto a locked path** - start autonomy and let a train take a route over it, or
   park a train at a protected platform.
3. **Fire the route from the route list**, and again by clicking its tile on the track diagram. Each
   should ask "...would switch X, which is on track a train is running over. Run it anyway?"
4. **Click Cancel.** Today the whole route is discarded, so **the power stays on**. The same route fired
   by an s88 trigger skips only the switch and still cuts the power.

**The question: does Cancel mean "don't throw that switch" or "don't run this route at all"?** If the
first, I will make both doors skip the accessories and run the rest, matching the s88 door and the model
layer, and reword the dialog to say so. If the second, the s88 door is the odd one out and I will say so
in its comment instead.

---

<a id="mt-248"></a>

### MT-248 - 2026-09-01 - The length notice and the guard it is meant to arm

**Disposition:** needs test
**From:** FX2-3, TCX-B2, SVN-B1

**Written:** 2026-09-01

**First, the thing to check before anything else.** Two of the six tiles that carry a recorded length
are reversal squares on `1 - Main`, so this guard is already live on your railway:

| Tile | Name | Room | |
|---|---|---|---|
| `5:20,13` | `BottomMainB` | 4 | `canReverse` |
| `5:20,14` | `BottomMainC` | 2 | `canReverse`, and **EN57-947's home** |

**EN57-947 itself is exempt** - measured against the locomotive database, it has no train length set,
and the guard only applies to a locomotive that has one. So nothing is being refused into its home
today.

**But 42 of the 54 locomotives that do carry a length are longer than 2**, and `BottomMainC` has two
units of room. Send one of those into it - by hand, or by giving it that home and pressing Return
Home - and it should be refused with a message about the train being too long. Is that the behaviour
you want on this berth, or is two units simply the wrong number for it?

(Two earlier versions of this test were wrong about this: the first said the guard was inert because
the six measured tiles were "all on the Test page" - `setup.json` maps page id 5 to `1 - Main` - and
the second said EN57-947 was being refused, which the database disproves.)

**Then open the autonomy editor and count the notices.** There are about **23 squares where trains turn
round** and six measured tiles in total, so expect roughly 20 notices asking for track lengths.

1. **Is that a useful prompt or a wall of noise?** If it is noise, say so and I will scope it - to one
   page, to squares with a home on them, or to nothing until you ask for it.
2. **Set the length on one square the notice names.** The notice should go.
3. **Now try to back a long train into that berth.** It will not be refused, and that is the defect:
   the notice asks for the reversing square's length, and the guard needs every segment of the run-in
   measured before it will judge anything. Following the notice as written does not arm the guard.

**And the question behind it:** when you said *"sum the track segments leading up to it"*, did **it**
mean the reversal, or the berth the train ends up standing in? The two give different answers as soon as
the train turns round part way along - a 10 + 1 + 2 approach admits an eight-unit train into three units
of real room under the first reading. I have not changed the rule; the guard now carries a comment
saying it is unsound until you rule.

---

<a id="mt-249"></a>

### MT-249 - 2026-09-01 - A parking berth a train cannot get out of

**Disposition:** needs test
**From:** FX2-4, RTG-A1

**Written:** 2026-09-01

**BottomMainB (eastbound, reverse) is a destination with no outgoing edges**, on your layout as it
stands. The builder emits a may-turn station's turning copy as a terminus destination, and its edge loop
can trace no way back against the main line's one-way arrows. Autonomy has no way-out check, so it will
choose that berth for a reversible train.

1. **Start full autonomy** and let it run until something is sent to BottomMainB. Does the train get
   stuck there?
2. **Try the same by hand** from the right-click menu, then try to send it anywhere else.
3. **Compare with BottomMainC**, whose equivalent copy does have an exit - that is the difference.

**The question: should the builder refuse to emit a turning copy it cannot trace a way out of, or should
the editor warn about one?** You have already ruled that "it should be easily possible to get back"; this
is about which layer enforces it. This is also why `testTheParkingBerthsGetTheirTrainsBack` is still
failing and still excluded from the battery.

**Adam, 2026-09-02 (triage).** Works.

addressed offline

*Run against commit 409d4ce8.*

---

<a id="mt-250"></a>

### MT-250 - 2026-09-01 - Homing a train that is already standing on a reversing point

**Disposition:** needs test
**From:** D24-B1

**Written:** 2026-09-01

Return Home used to declare this **impossible** rather than simply doing it. Two searches inside the
planner disagreed about whether a train standing on a reversing point has already turned; the one that
said no was the one used to prove impossibility, so it refused journeys the other would have routed and
blamed the track.

1. **Put a non-reversible locomotive on a reversing point** - a headshunt or a shunting neck.
2. **Give it a home at a parking terminus** reachable from there.
3. **Press Return Home.** It should plan and go. Before the fix it reported that the locomotive could not
   reach its home at all.
4. **Check the ordinary case still behaves**: the same locomotive standing on a plain platform, homed at
   a terminus with no reversing point anywhere on the way, should still be reported as impossible.

*Run against a build after commit 9f1b80c8.*

---

<a id="mt-251"></a>

### MT-251 - 2026-09-02 - The letter keys work the moment the window appears

**Disposition:** fixed unvalidated
**From:** OB-168

**Written:** 2026-09-02

You reported this as **"ensure the UI is focused once the window is rendered so that keystrokes are
registered on the main traincontrol window (locomotive letters, etc.)"**, and it needs your hands
because nothing here can tell whether a window has the keyboard.

The window never asked for the keyboard at all.  Asking the frame for it would not have worked either -
it is built non-focusable, so `requestFocus()` on it does nothing whatsoever, which is why this looks
like something that was never there rather than something that broke.  The letter keys are read by
listeners on the tabbed pane and on the locomotive panel, and one of those has to be holding the focus.

1. **Start TrainControl and do not touch the mouse.**  Press a letter mapped to a locomotive.  It should
   respond.  Before this, nothing happened until you clicked the window first.
2. **Start it with another window already in front** - a browser, the editor, anything.  TrainControl
   should come to the front and take the keys.
3. **The same again with "always on top" turned off**, since that setting is applied in the same breath
   as the focus request and is the likeliest thing to interfere.
4. **Then click into the layout tab, or the keyboard tab, and back.**  The letters should keep working;
   this only sets the starting point, and nothing else about focus was touched.

If it comes up focused but a *particular* letter does nothing, that is a different fault - say which.

*Run against a build after commit bec51e31.*

---

<a id="mt-252"></a>

### MT-252 - 2026-09-02 - Placing tile after tile from the palette, over track that is already there

**Disposition:** fixed unvalidated
**From:** OB-169

**Written:** 2026-09-02

**It only failed over squares that already had track on them**, which is worth knowing before you test
it: on an empty square the same gesture always worked, and that is why this went unnoticed.  Pressing
the mouse on track is how a tile is picked up to be dragged, so the press threw away the palette tile
you had just chosen and armed a move instead; the release then decided a press and a release on one
square was a click rather than a drag, cleared the clipboard, and left the click with nothing to do.

There is an automated test for it now, driving the three mouse handlers in the order a mouse makes them.
What that test cannot judge is whether the whole gesture *feels* right, and whether anything else that
uses the mouse on the diagram was disturbed.

1. **Click a tile in "new components", then click a square that already has track on it.**  The tile
   should land, replacing what was there.
2. **Keep clicking squares.**  It should stay in place mode - "until escape is pressed or another action
   taken" - and each click should place another one, over occupied and empty squares alike.
3. **Press Escape.**  Place mode should end.
4. **Then check the gestures that share those handlers**, because this changed what a press on the
   diagram does while a palette tile is held:
   - **drag an existing tile** from one square to another, with nothing selected in the palette
   - **Ctrl+C a tile, then press on other track** - that should still pick the track up, not paste over
     it, which is deliberate and is the one case left alone
   - **drag a selection** of several tiles
   - **double-click a switch or signal** to edit its address
5. **Undo after each.**  A plain click on a tile, placing nothing, should still not leave an undo entry
   behind - that is what the code being changed here was originally added for.

*Run against a build after commit bec51e31.*

---

<a id="mt-253"></a>

### MT-253 - 2026-09-02 - What the editor now says about your own diagram

**Disposition:** superseded
**From:** the copy-check sweep of 2026-09-02

**Written:** 2026-09-02

Two things came out of sweeping the new arrival checks over your frozen diagram, and both are about
your railway rather than about the code.  **Neither is something I can settle.**

**One: RampDown, arriving southbound, can get nowhere.**  It is the only thing the new warning reports on
your whole layout.  The square itself is fine - trains arriving the other way carry on normally - but a
train that arrives at it southbound can leave and still never reach another station.  The model's own
`canReachAnyDestination` agrees, so this is not the check being clever.

- Is that deliberate?  If it is, the warning is noise on your railway and I should say so in the message
  or drop the check to a quieter place.
- If it is not, the fix is in the diagram: open a way on from that side, or bar the arrival.

**Two: the redundant tunnel has left five link errors, and errors stop autonomy starting.**  These are
not from the new checks - they are the existing link rules, and they are correct:

| Where | What is drawn there | Paired with |
|---|---|---|
| `2 - Bottom:10,9` | a link | `1 - Main:15,5`, which is a **signal** |
| `1 - Main:12,1` | plain straight track | `1 - Main:16,1` |
| `1 - Main:16,1` | plain straight track | `1 - Main:12,1` |
| `1 - Main:14,5` | a link | nothing at all |

The pairings themselves are mutual and intact; what has changed is what is drawn at the far ends.  Three
of the four squares a pairing points at are no longer links, and one real link has no partner.  The two
squares that ARE still links - `2 - Bottom:10,9` and `1 - Main:14,5` - look like the pair you meant.

1. **Open the editor and re-pair those two**, and clear the stale pairing between the two plain squares.
2. **Then try to start autonomy.**  It should stop refusing.
3. **Send a train through the tunnel both ways** - which was the point of adding a second one.

*Run against a build after commit 06516f38.*

**Adam, 2026-09-02 (triage).** Does not work.

I don't see any issue with rampdown, trains exit south just fine.

**WITHDRAWN IN FULL, and he is right on both halves (2026-09-02).**

Re-measured on his own configuration, with the sandbox opened BEFORE the model: the setup reports
**zero errors**, and all three copy checks - no way out, no way in, reaches no other station - come
back **empty**. There is nothing wrong with RampDown and nothing wrong with the tunnel.

**What produced the findings was a defective probe, and the defect is worth writing down because it
is a rule this project already has.** `probeSweepCopies` called `MarklinControlStation.init()` and
*then* `LayoutSandbox.open()`. That is the wrong order - OB-111, and `testLayoutEditorBulkEdits` says
so at its own setup: *"Before the model, not just before the window (OB-111) - constructing a
TrainControlUI reads the layout-path preference, and without the sandbox it is Adam's own railway."*
So the model parsed one layout, the session was built over another, and every page-id-keyed thing in
the setup - which is what the portals are - resolved against the wrong pages. The tunnel pair came out
as "a link paired to a signal"; the arrival checks came out with squares that do not exist as
described.

Corrected readings, for the record: `1 - Main:12,1` and `1 - Main:16,1` are both TUNNEL and are
mutually paired; `1 - Main:15,5` and `2 - Bottom:10,9` are both LINK and are mutually paired. That is
the redundant tunnel he added, working.

**The lesson is the one already in the file and not a new one:** a probe is a test, and the ordering
rules that apply to tests apply to it. This one skipped the rule and then produced two confident
findings about his railway, which cost him the time to check them.

*Run against commit 409d4ce8, build\classes, compiled 02 Sep 08:07 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-254"></a>

### MT-254 - 2026-09-02 - Clearing every home locomotive at once

**Disposition:** needs test
**From:** R28-C1

**Written:** 2026-09-02

You asked for it back: **"that option should be added back in to the autonomy editor, with a
confirmation."**  It is a button in the editor's tool column, under Name Everything, using the same
confirmation wording 2.8.1 had - which was still sitting in all eight languages with nothing referring
to it.

1. **Open the autonomy editor on a page and look at the column.**  With homes set anywhere in the
   configuration the button is live; with none it is greyed and its tooltip says why.  That greying is
   the affordance and the button's own check is the guard, and both are meant to be there.
2. **Press it.**  The confirmation should say what clearing does - Return Home will send each
   locomotive back to wherever it was standing when autonomy loaded - and offer No by default.
3. **Say yes.**  Every home goes, on every page, not just the one you are looking at.  The hint line
   says how many.
4. **Press Cancel on the editor.**  The homes should all come back: nothing is written to disk until
   you Save, like every other decision in that window.
5. **Then do it again and Save**, and reopen.  They should stay gone.

*Run against a build after commit 2e83b737.*

**Adam, 2026-09-02 (triage).** Works, with notes.

works, but move to right-click menu as noted in other comment

**Re-opened as `needs test` (2026-09-02).** The button works and is staying in behaviour; where it
LIVES is changing, so the steps above are about a control that will have moved by the time they are
run. It goes into a **Bulk tools** submenu on the autonomy editor's right-click menu, beside Clear
Locomotives - see MT-257 item 1, which is where he asked for both.

*Run against commit 409d4ce8, build\classes, compiled 02 Sep 08:07 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-255"></a>

### MT-255 - 2026-09-02 - Out of service, on a square that is not a station

**Disposition:** fixed unvalidated
**From:** D24-B5, SVN-B6

**Written:** 2026-09-02

**Marking a plain square out of service used to do nothing at all**, in two places at once, while the
editor drew a cross for it.  The builder dropped the setting for anything that is not a station, so the
running graph never learned about it; and the running diagram had no term for "switched off" in the
rule that decides which squares get a badge, so the cross appeared while you were setting the railway
up and vanished the moment you started it.

Both are fixed, and the second half is the one that changes what trains do.

1. **Pick a plain sensor** - not a station, not somewhere trains turn round - on a run you can watch.
2. **Mark it Out of service** in the editor.  It should draw a cross.
3. **Save, load the configuration and start autonomy.**  The cross should still be there on the running
   diagram.  Before this it was not.
4. **Watch what routes get chosen.**  Nothing should be routed through that square any more, by full
   autonomy OR by a hand dispatch - `isPathClear` refuses a path through an inactive point whichever
   tier asked.
5. **Switch it back on** and check trains use it again.

**On your own layout this changes nothing today**, measured: you have exactly one square out of
service and it is a station, which always worked.  It is a 2.8.1 upgrade that meets the new behaviour -
see MT-257.

*Run against a build after commit 1cfdf370.*

---

<a id="mt-256"></a>

### MT-256 - 2026-09-02 - Switching a signal by hand while a train stands at its platform

**Disposition:** fixed unvalidated
**From:** SVN-B16, WK3-B1

**Written:** 2026-09-02

A route that would turn a platform's protecting signal green with a train standing there is refused and
says so.  Clicking the same signal green **by hand, on the diagram**, was not - the rule lived inside
the route and the diagram's own tile never got it.

It is on `Layout` now and both doors ask it.  What needs your hands is that it asks about the right
direction: turning protection ON is doing what the protection would do anyway, and an earlier review
removed exactly that over-strictness from the route door.

1. **With autonomy running**, stand a train at a platform that has a protecting signal paired to it.
2. **Click that signal to RED.**  Nothing should ask you anything - that is the protective direction.
3. **Click it to GREEN.**  You should be asked whether to switch it anyway, and Cancel should leave it
   alone.
4. **Do the same with a three-way turnout** if you have one paired, and with the train moved away - no
   question should be asked in either case with the platform empty.
5. **And check a route** that sets the same signal green is still refused as before.

*Run against a build after commit e6791631.*

---

<a id="mt-257"></a>

### MT-257 - 2026-09-02 - Five things the review round wants you to rule on

**Disposition:** needs test
**From:** RG3, DY3

**Written:** 2026-09-02

None of these is a defect with an obvious fix; each is a decision about what the railway should do, and
they are collected here rather than guessed at.

**1. "Clear Locomotives" is still gone.**  Its sibling "Clear All Home Locomotives" came back today
(MT-254) and this one did not.  It unplaced every locomotive at once.  You have four placements rather
than sixty-two, so doing it by hand is four right-clicks - do you want it back as well?

**2. A 2.8.1 autonomy.json now blocks paths it did not block yesterday.**  The builder carries
`active: false` on plain squares now, and your own frozen legacy file has twenty-four such points of
which six are not stations.  On import, each becomes a square no path may pass through - manual routes
included.  This is arguably a RESTORATION: at 2.8.1 the raw file went straight into the model and those
points blocked paths then too.  Worth knowing before somebody upgrades.

**3. The legacy import says what it brought and not what it left.**  It shows six counts of what
arrived and never names the four things it drops: per-edge accessory commands, edge lengths, the
timetable, and route activations.  Measured on your own file: 69 of 90 edges carry commands naming 15
signals, 30 edges carry a length, 36 timetable entries.  Should the dialog list them?

**4. "Place Autonomy Station Label" has no door.**  The menu item is gone, Ctrl+S is gone from the key
handler, and the method kept to explain where it went has no callers - while `Readme.md` still
documents the key.  Removed on purpose, or lost with the graph window?

**5. "Test Connection" has no successor that works without a train on the square.**  The old
point-to-point path diagnostic answered "can a train get from here to there" with nothing standing
anywhere.  Nothing in the editor does that now.

*Run against a build after commit 2e83b737.*

**Adam, 2026-09-02 (triage).** Does not work.

1. Yes, I want it back.  Put both Clear Locomotives and Clear All Home Locomotives into a "bulk tools" category in the autonomy edit right-click menu.
2. good
3. yes, but list them in the log and mention that in the dialog.
4. add the keyboard shortcut back to the autonomy editor, we already have the function in the right click menu there
5. why can't it keep working without a train,  between stations?

**Taken as five rulings (2026-09-02).**

| | Ruling | What was done |
|---|---|---|
| 1 | Clear Locomotives comes back, and both bulk actions move to a **Bulk tools** category on the autonomy editor's right-click menu | done - see MT-258 |
| 2 | The legacy import's new blocking behaviour is accepted as it stands | closed, no work |
| 3 | List what the import dropped in the log, and say in the dialog that it is there | done - see MT-258 |
| 4 | The station-label keyboard shortcut comes back to the autonomy editor, the menu item already being there | done - see MT-258 |
| 5 | **A question, answered below rather than acted on** | open - needs his word |

**On 5 - "why can't it keep working without a train, between stations?"**

It can, and nothing about the diagnostic needs a train. What was lost with the graph window is the
DOOR, not the capability: the old Test Connection took two points and asked whether a path existed
between them, and `Layout.bfs` still answers exactly that with nothing standing anywhere.

What the editor has instead is **Test a path**, which starts from the square you clicked - so it needs
somewhere to start from rather than a train, and it answers about one end at a time.

So the question is what shape you want it back in, and that is the part I have not guessed at:

- **two clicks** - pick a square, pick another, get the answer for that pair; or
- **one click, all answers** - pick a square and list every station reachable from it, which is nearer
  what Test a path already does and needs no second gesture.

Say which and it is a small addition.

*Run against commit 409d4ce8, build\classes, compiled 02 Sep 02:26 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-258"></a>

### MT-258 - 2026-09-02 - Bulk tools, the import's log, and Control+S

**Disposition:** needs test
**From:** MT-257 items 1, 3 and 4

**Written:** 2026-09-02

Three of your five rulings, built.  Item 2 needed no work and item 5 is a question waiting on you.

**1. Bulk Tools.**  Both actions are on the autonomy editor's right-click menu now, under a **Bulk
Tools** heading at the foot of it, and the button has left the tool column - one place for both rather
than one each in two surfaces.

1. **Right-click any square** in the autonomy editor.  Bulk Tools is under Set Length, after a rule.
2. **Clear All Locomotives** takes every train off the setup and keeps the home assignments.  Each item
   carries its own count and greys itself when that count is zero, so a setup with homes and no
   placements shows one live and one grey.
3. **Both confirm first**, and neither writes to disk - Cancel on the editor puts everything back.
4. **Check the counts are right** before you press: they should match what the diagram shows.

**3. The import says what it left.**  Importing a 2.8.1 `autonomy.json` counted the six things it
brought and said nothing about the four it drops.

5. **Import your own `autonomy_legacy/autonomy.json`** into a spare configuration.  The summary should
   end with a line saying how many things were deliberately not imported.
6. **Open the log.**  It should name each one with a count and a reason - on your file that is 69
   connections with accessory commands, 30 with a length, a 36-entry timetable, and the route
   activations.

**4. Control+S.**  It names the square under the pointer, in the autonomy editor - the same thing
`Rename` on the right-click menu does.

7. **Hover a square in the autonomy editor and press Control+S.**  The naming prompt should open on
   that square.
8. **Press it over an empty square**, and over a page you have excluded, and check it does something
   sensible rather than nothing at all.
9. **Check it does nothing unexpected in the TRACK editor**, where Control+S is not bound.

*Run against a build after this commit.*

**Adam, 2026-09-02 (triage).** Does not work.

control+s is not firing in the autonomy editor

*Run against commit 409d4ce8, build\classes, compiled 02 Sep 08:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

**Item 4 fixed (2026-09-02).  It was firing.**  The key reached its branch - Control+G and Control+L
sit two branches above it and both work - and then asked the wrong question.  It asked
`getLastHoveredLabel()`, which reads the two variables that say where a PASTE would land, and the
hover handler deliberately does not set those in autonomy mode.  Its own comment says why, and the
comment is right: nothing is pasted in that mode, and setting them there would teach the placement
code a position it has no business acting on.  So the key fired, asked which square, and was told
none.

Autonomy mode now records the square under the pointer in a field of its own and Control+S asks that,
leaving the placement variables exactly as untouched as they were.  Three tests, one of them for the
half that could easily have been broken by the obvious fix - that hovering in the TRACK editor still
feeds those variables, because drags and pastes read them.

**Items 1 and 3 are still open on this ticket**, since your note was only about item 4.  Steps 1-6
above have not been reported on either way.

*Item 4 to be run again against a build after this commit.*

---

<a id="mt-259"></a>

### MT-259 - 2026-09-02 - The keyboard, when the window comes to the front

**Disposition:** needs test
**From:** OB-170

**Written:** 2026-09-02

You reported this twice, and the second time against a build that already carried the first fix:
**"when I start the app, the main window is not in focus and keystrokes don't go to it.  could it be
related to the startup loading notice?"**

**Not the loading notice, but you had the right shape.**  `StartupSplash` closes on the event thread
before the window is told to show itself, so it cannot be an ordering problem - but an always-on-top
window that held the focus and then vanished leaves the focus wherever Windows decides, which need not
be here.  That is the same class of thing, and so is an update prompt, or any dialog you dismiss while
the application is starting.

**What was actually wrong.**  The first fix asked for the keyboard once, at the end of start-up.
`requestFocusInWindow` does nothing at all while the window is not the focused window, and `toFront()`
from a process that is starting does not reliably make it one - so on a machine where anything else
held the focus, the request was made at a moment it could not be granted and then never made again.

Two changes.  The window now asks again every time it GAINS focus, so alt-tabbing to it, or dismissing
whatever was in front of it, puts the keyboard back.  And the condition on that is about typing rather
than about focus: it stands aside only for the log pane and the JSON pane, the two places in this
window you can actually type into.  Deferring to anything focusable was the first version and it is
too generous by the width of the bug - focus resting on a label or a button consumes no letters, so
leaving it there is the window having no keyboard while looking polite about it.

There are three automated tests, including one that a keystroke straight after start-up moves the
active key, which is what you asked for.  What they cannot judge is your machine.

1. **Start TrainControl with something else in front of it** - a browser, NetBeans, anything - and do
   not touch the mouse.  Press a letter mapped to a locomotive once the window is up.
2. **Start it from a cold boot**, where the update check and the loading notice are most likely to be
   in the way.
3. **Alt-tab away and back**, several times.  The letters should keep working every time.
4. **Then click into the log pane at the bottom, type something, alt-tab away and come back.**  The
   caret must still be in the log pane - this is the case the condition exists for, and getting it
   wrong would be worse than the original fault.
5. **Click the layout tab, then back to the keyboard tab.**  The letters should be unaffected.

If it comes up focused but a *particular* letter does nothing, that is a different fault - say which.

*Run against a build after this commit.*

---

<a id="mt-260"></a>

### MT-260 - 2026-09-02 - Six rulings the review rounds are holding

**Disposition:** needs test
**From:** RTG-B2, TCX-B2, D24-C7, D24-C8, R28-A1, SV2-A1, DY3-C8

**Written:** 2026-09-02

You asked what is still open from the B's and C's that needs you.  This is it: six questions, each
one sitting under a finding that is otherwise understood and could be built either way.  None of them
is a bug report - each is a choice about how your railway should behave, and I have not guessed at any
of them.

Answer as briefly as you like; a word each is enough.  Everything else in those documents is mine to
either do or withdraw, and none of it is waiting on you.

**1. A train that backs into a berth over a switch.**  (`RTG-B2`, and `SVN-B3` is the same question.)
The guard adds up every segment on the route to a berth and refuses the train when it is longer than
that total.  For a train that drives straight in that is exactly the rule you gave -
*"do you sum the track segments leading up to it?  if they are long enough, then we are good"*.  But
for a train that passes a reversing point and **backs** in, only the track between the reversal and
the berth is behind it; the segments before the reversal cannot hold any of it.

> When the run-in is long enough overall, may a train longer than berth-plus-switch still come to rest
> across the switch behind its berth, or should the berth and switch lengths alone bound it?

Today it is the first.  On your railway the guard is inert either way until more lengths are measured,
which is the next question.

**2. Which squares the length notice asks about, and 20 warnings on first open.**  (`TCX-B2`,
`D24-C7`, and the comments pass raised it a third time.)  The editor asks you to measure the square a
train turns round on.  The guard above needs **every** segment on the run-in to that square, and
returns "unmeasured" if any one of them is missing - so measuring only what the notice asks for leaves
the guard never firing.  Measured on your live configuration: 22 turnaround squares, 6 tiles with a
length, of which 2 are turnarounds.  Extending the notice to the run-in raises **20 new warnings** the
first time you open the editor.

> Should the notice ask for the approach segments too - and is a 20-item list what you want to be met
> with, given that without them the guard cannot fire?

**3. A signal left red over a platform you emptied by hand.**  (`D24-C8`.)  Your ruling on OB-166 was
*"signals should only be touched when a route activates"*, and the start-of-run sweep went with it.
MT-246 records one side of what that gives up - a platform stays **green** after you put a train on it
by hand.  The other side is the mirror: a platform a route left **red**, which you then empty by hand
while nothing is running, stays red into the next run.  It corrects itself only after a train has
arrived there and left again.

> On a railway where a protecting signal is wired to a braking section, a signal stuck red over an
> empty platform holds a train up.  Acceptable alongside the green one, or should the sweep come back
> for **occupied** platforms only?

**4. Deleting a locomotive edits your routes without saying so.**  (`R28-A1`.)
Deleting a locomotive from the database removes every `locspeed` command naming it from every route,
writes the routes to disk, and says nothing.  2.8.1 did not do this.  The confirmation you see is about
the locomotive.

> Should the confirmation say how many route commands will go and from which routes, or should the
> removal be dropped in favour of the 2.8.1 behaviour?

**5. A non-reversible train standing in a parking berth.**  (`SV2-A1`, and `TV2-C7` for the fact that
this was answered in code without being asked.)  The planner now assumes such a train **backed in**,
so it has to be turned again on the way to its next terminus.  That is the conservative reading and it
matches the documentation - but it was applied rather than decided, and it is pinned in three places
that would move together.

> Is that right, or was it driven in nose-first and already facing out?

**6. `ParkingTrack12`, on the frozen test fixture.**  (`DY3-C8`.)  In
`test/operator_layout/config/autonomy/configuration-Main.json` that square is the only one in the file
carrying **both** `autoDestination: false` and `active: false` - a parking berth that is also out of
service.  Twenty other squares carry the first alone.  The fixture is a copy of your railway as it was
at `409d4ce8`.

> Deliberate - a berth you have taken out of use - or a stray click that the fixture then froze?

---

**And one still open on [MT-257](#mt-257).**  Item 5, Test Connection: you asked *"why can't it keep
working without a train, between stations?"* and the answer is a choice between two shapes - two
clicks to name both ends of the run to test, or one click that answers every question it can about the
square without needing a second one.  That one is still waiting on you too.

*No build needed to answer these.*

**Adam, 2026-09-02 (triage).** Answered, all six.

> 1. it depends on the direction.  if the train crosses the fork through the base, then the track
>    after the switch has to be long enough to accommodate it.  in other words, between the switch and
>    the station, the length must be >= length of the train.  if the train enters from one of the
>    branches, then it shouldn't matter.
> 2. 20 warnings sounds OK
> 3. OK, because it will be set correctly later
> 4. what do you mean by deleting- from the stations, or from the database?
> 5. most likely, but can't assume.  trains can also back out and reverse on their way out.
> 6. yes, for testing.

**What each one becomes.**

| | Ruling | What follows |
|---|---|---|
| 1 | The room is measured **between the switch and the station**, not over the whole route, and only where the path crosses the turnout **through its base** | `Layout.measuredRoomToReverseInto` sums every segment today.  It has to find the last switch the path crosses base-side and sum from there; a path that only ever enters from a branch is unconstrained.  Not yet built - see the note below |
| 2 | Twenty warnings is acceptable | The notice extends past the reversal square.  **Which** squares it asks for now follows from ruling 1 rather than from "the whole run-in", so the count may not be twenty - it will be whatever the switch-to-station stretches need |
| 3 | Accepted: a signal left red over a platform emptied by hand is not worth a sweep, "because it will be set correctly later" | `D24-C8` closed.  MT-246's "what this deliberately gives up" gains the second direction, so the record holds both halves rather than one |
| 4 | A question back | Answered below |
| 5 | "most likely, but can't assume.  trains can also back out and reverse on their way out" | The planner stops **assuming** either way at a berth and searches both - the train may have backed in, or been driven in and able to back out and reverse later.  That also settles `RTG-C3`, which is the same seed asked of the impossibility proof |
| 6 | `ParkingTrack12` is deliberate, for testing | `DY3-C8` closed |

**Ruling 4, answered: from the database.**  `R28-A1` is about **Locomotives -> delete** in the
locomotive database - the menu item that removes the locomotive itself, with its own confirmation.
Nothing about stations or home assignments.  What it does silently is edit your **routes**: every
`locspeed` command naming that locomotive is removed from every route that has one, and the routes are
then written to disk.  2.8.1 left the routes alone and the commands simply referred to a locomotive
that was gone.

To see it without touching your own data: add a locomotive `X`, put `locspeed,X,40` in a route, delete
`X` from the database, and look at the route - the line is gone, and nothing in the log mentions it.

So the question stands, narrowed: should the delete confirmation say how many route commands will go
and from which routes, or should it stop editing routes at all?

**Ruling 1 needs one thing confirmed before it is built, and it is a real ambiguity rather than a
formality.**

On a simple turnout, *every* route through it touches the toe - that is what makes it the toe, and it
is how the model derives it (`TilePorts.deriveToe`: "the side every route of this type touches in
every position").  So "crosses the fork through the base" cannot mean *which ports the path uses*,
because on a turnout that is always true and the rule would never be narrowed at all.

Which leaves the direction of travel, and there the two cases are:

- **base -> branch.**  The train comes up the common leg and goes out on one fork.  If it does not
  clear the points, its tail stands on them.
- **branch -> base.**  The train comes in on a fork and goes out on the common leg.  If it does not
  clear the points, its tail also stands on them.

Both foul the turnout, which is why I want to check rather than guess: physically the second case
looks the same as the first to me, so "if the train enters from one of the branches, then it shouldn't
matter" must be saying something I am not seeing - either about how your layout is arranged, or about
which movements you care about being blocked.

**The question, in one sentence:** is the distinction the direction of travel over the points
(base-first versus branch-first), and if so what makes a tail left on the points harmless in the
second case?

Everything else about the ruling is clear and is what will be built: the measurement is the stretch
from the turnout to the station, not the whole route.

---
