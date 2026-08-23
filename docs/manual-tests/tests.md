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
| [MT-067](#mt-067) | 2026-08-18 | Arrival marks in the viewer | needs test | Tier 1 |
| [MT-068](#mt-068) | 2026-08-18 | Switched-off link | needs test | Tier 1 |
| [MT-069](#mt-069) | 2026-08-18 | Remove a locomotive from a non-station | needs test | Tier 1 |
| [MT-070](#mt-070) | 2026-08-18 | Page switching keeps captions live | needs test | Tier 2 |
| [MT-071](#mt-071) | 2026-08-18 | Popup diagram captions | needs test | Tier 2 |
| [MT-072](#mt-072) | 2026-08-18 | Cancel in the track diagram editor | needs test | Tier 2 |
| [MT-073](#mt-073) | 2026-08-18 | Undo covers captions | needs test | Tier 2 |
| [MT-074](#mt-074) | 2026-08-18 | Export / import round trip | needs test | Tier 2 |
| [MT-075](#mt-075) | 2026-08-18 | Page files | needs test | Tier 2 |
| [MT-076](#mt-076) | 2026-08-18 | Running path drawing | needs test | Tier 3 |
| [MT-077](#mt-077) | 2026-08-18 | Caption direction arrow | needs test | Tier 3 |
| [MT-078](#mt-078) | 2026-08-18 | Barred arrival is honoured | needs test | Tier 3 |
| [MT-079](#mt-079) | 2026-08-18 | Barred terminus loads | needs test | Tier 3 |
| [MT-080](#mt-080) | 2026-08-18 | Collect what the new model offers | needs test | Tier 4 |
| [MT-081](#mt-081) | 2026-08-18 | Collect what the old model offered | needs test | Tier 4 |
| [MT-082](#mt-082) | 2026-08-18 | Compare, and scrutinise the NEW-ONLY entries | needs test | Tier 4 |
| [MT-083](#mt-083) | 2026-08-18 | Run a new-only route in simulation | needs test | Tier 4 |
| [MT-084](#mt-084) | 2026-08-18 | Two trains, shared junction | needs test | Tier 5 |
| [MT-085](#mt-085) | 2026-08-18 | Collision refusal | needs test | Tier 5 |
| [MT-086](#mt-086) | 2026-08-18 | Manual displacement still works | needs test | Tier 5 |
| [MT-087](#mt-087) | 2026-08-18 | Long run | needs test | Tier 5 |
| [MT-088](#mt-088) | 2026-08-18 | Path-integrity failure | needs test | Tier 6 |
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
| [MT-030](#mt-030) | 2026-08-21 | A route holding a signal command | fixed unvalidated | AR-19, DD |
| [MT-032](#mt-032) | 2026-08-21 | Two trains, one dispatched onto a long path | needs test | TR-A22 |
| [MT-035](#mt-035) | 2026-08-21 | The Central Station switched off mid-session | needs test | - |
| [MT-036](#mt-036) | 2026-08-21 | A train stopped by hand | fixed unvalidated | hands-on testing |
| [MT-037](#mt-037) | 2026-08-21 | An automatic route says nothing about its trigger | fixed unvalidated | AR-20 |
| [MT-038](#mt-038) | 2026-08-21 | An unreadable UIState.data is kept | fixed unvalidated | IP-*, AR-21 |
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
| [MT-063](#mt-063) | 2026-08-22 | A second copy of TrainControl says so | fixed unvalidated | AR-16 |
| [MT-064](#mt-064) | 2026-08-22 | Highlight on Diagram, and Test Condition | needs test | feature request |
| [MT-089](#mt-089) | 2026-08-22 | A signal CONDITION offers red and green | needs test | DD - live defect |
| [MT-090](#mt-090) | 2026-08-22 | Add Locomotive refuses address 0 | needs test | DD appendix A3.3 - verified |
| [MT-091](#mt-091) | 2026-08-22 | ant test runs the whole suite | needs test | DD-A2 - verified |
| [MT-092](#mt-092) | 2026-08-22 | The triage app | needs test | feature request |
| [MT-094](#mt-094) | 2026-08-22 | **Superseded - tracked as OB-001 in issues.md, not a test.** | needs test | OB-001/OB-002 |
| [MT-095](#mt-095) | 2026-08-22 | The editor stays open when you switch page or mode | fixed unvalidated | OB-005 |
| [MT-096](#mt-096) | 2026-08-22 | The editor opens at the size of its diagram | fixed unvalidated | OB-003 |

Everything else - 17 of 96 - is **fixed validated** and needs nothing from you unless the
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

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 1 - diagram and editor, autonomy not running  
**Written:** 2026-08-18

**What to do.** Arrival marks in the viewer. Close the editor. A restricted station shows its marks on the
   running diagram; an unrestricted one shows nothing. (Deliberate - no clutter for the default.)

#### Comments

Works, but overlap with the labels makes it suboptimal.  station icon may fix this.  Side requirement: left clicking a station icon should propagate the click to the s88 and back.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-068"></a>

### MT-068 - 2026-08-18 - Switched-off link

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 1 - diagram and editor, autonomy not running  
**Written:** 2026-08-18

**What to do.** Switched-off link. Switch a link off. It is greyed on the main diagram, not only in the editor.

#### Comments

Looks right in the track diagram.  But not greyed out in the editor.  Also, move the "use this link" option out of the submenu into the top level.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

**Adam, 2026-08-22 (triage).** Works.

*Run against commit 058d2385, build\classes, compiled 22 Aug 17:49 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe.*

---

<a id="mt-069"></a>

### MT-069 - 2026-08-18 - Remove a locomotive from a non-station

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 1 - diagram and editor, autonomy not running  
**Written:** 2026-08-18

**What to do.** Remove a locomotive from a non-station. Right-click a point holding a loco that is not a
   station. **Remove** is present.

#### Comments

Works.  For the 3 type options (trains can stop, trains can pass through, neither, prefix with "Yes, No, No".  Out of service -> nothing can pass.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-070"></a>

### MT-070 - 2026-08-18 - Page switching keeps captions live

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Page switching keeps captions live. Note a caption on page A. Go to page B, then C, then back to
   A. A's captions still update.

#### Comments

They do- but I didn't test running with autonomy.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-071"></a>

### MT-071 - 2026-08-18 - Popup diagram captions

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Popup diagram captions. Pop out a page window, then repaint the main window. The popup's
   captions still update.

#### Comments

Works, but I noticed that some locomotives get a V > suffix, not just V or >.  Also, when moving a locomotive from one point to the other, it would be ideal if its natural direction could be preserved, compatible with the entrance direction to the station.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-072"></a>

### MT-072 - 2026-08-18 - Cancel in the track diagram editor

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Cancel in the track diagram editor. Delete two sensor squares that carry names, lengths or
   arrival settings, then press **Cancel**. The track comes back AND those squares keep their autonomy
   settings.

#### Comments

Labels disappear, stations stay.  Bug!  Confirmed the labels stay gone after reload.

Also, the confirm dialog in the diagram editor says 'are you sure you want to exit without saving', but the autonomy is 'save before existing?'  make the latter consistent.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-073"></a>

### MT-073 - 2026-08-18 - Undo covers captions

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Undo covers captions. Delete a captioned sensor, Ctrl+Z: tile and name both return. Drag a
   captioned tile, Ctrl+Z: the caption follows it back.

#### Comments

Bug- caption says, but content changes from the name itself to [---].  

Also: still don't see a way to move labels in the layout editor.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-074"></a>

### MT-074 - 2026-08-18 - Export / import round trip

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Export / import round trip. Export the autonomy JSON, re-import it. It loads, and Tier 4 step
    19 still holds afterwards. (This was broken until 18 August - the block field was not written.)

#### Comments

Seems fine.  Not sure what the block field is.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-075"></a>

### MT-075 - 2026-08-18 - Page files

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 2 - data safety  
**Written:** 2026-08-18

**What to do.** Page files. After a save, `config/gleisbilder/` holds a one-time `.bak` beside a rewritten
    page, and nothing is corrupted.

#### Comments

I don't see the .bak, but check on your end.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-076"></a>

### MT-076 - 2026-08-18 - Running path drawing

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 3 - autonomy in simulation, one train  
**Written:** 2026-08-18

**What to do.** Running path drawing. The route is a line along the track - red ahead of the train, green
    behind - with black arrowheads for direction. The train marker sits on the tile it has actually
    reached, not one ahead.

#### Comments

Looks OK for now, couldn't test much.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

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

---

<a id="mt-078"></a>

### MT-078 - 2026-08-18 - Barred arrival is honoured

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 3 - autonomy in simulation, one train  
**Written:** 2026-08-18

**What to do.** Barred arrival is honoured. Bar one side of a two-ended station, reload, run. Trains only pull
    in from the allowed side, and the station is still reachable.

#### Comments

Honored.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

<a id="mt-079"></a>

### MT-079 - 2026-08-18 - Barred terminus loads

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 3 - autonomy in simulation, one train  
**Written:** 2026-08-18

**What to do.** Barred terminus loads. Mark a terminus "trains may turn round here", bar one of its sides,
    reload. It loads - no "configuration is invalid and must be reloaded".

#### Comments

Correct. And reversible locomotives are enforced.

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

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

**Disposition:** needs test  
**From:** 2026-08-18 manual test plan, Tier 5 - autonomy in simulation, several trains  
**Written:** 2026-08-18

**What to do.** Manual displacement still works. Right-click-place a train onto an occupied station: it
    displaces the previous occupant. This is intended - you are telling the model where a train is.

#### Comments

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

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

---

**Claude, 2026-08-22.** Carried in from `docs/reviews/2026-08-18-manual-test-plan.md` when the manual tests were consolidated. Adam's answer above is from 18 August; the disposition is **needs test** rather than anything stronger because four hundred and fifty commits have landed since, including most of the autonomy editor. A result from before that is not a result about this build.

---

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

**Disposition:** fixed unvalidated  
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

---
<a id="mt-038"></a>

### MT-038 - 2026-08-21 - An unreadable UIState.data is kept

**Disposition:** fixed unvalidated  
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

**Disposition:** fixed unvalidated  
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

---

<a id="mt-089"></a>

### MT-089 - 2026-08-22 - A signal CONDITION offers red and green

**Disposition:** needs test  
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

---

<a id="mt-090"></a>

### MT-090 - 2026-08-22 - Add Locomotive refuses address 0

**Disposition:** needs test  
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

---

<a id="mt-091"></a>

### MT-091 - 2026-08-22 - ant test runs the whole suite

**Disposition:** needs test  
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

---

<a id="mt-092"></a>

### MT-092 - 2026-08-22 - The triage app

**Disposition:** needs test  
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

**Disposition:** needs test  
**From:** OB-001 / OB-002 (feature request), raised from MT-065  
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

**The live record is `OB-001` in [issues.md](issues.md), tracked directly rather than promoted.**
Feature requests get a **State** field of their own now - the same three words tests.md's
disposition uses, set by Claude the same way, but living in `issues.md` and never becoming an
`MT-###` tag unless the work genuinely needs a repeatable hands-on test the way a bug fix does. See
`issues.md`'s "What has been picked up" table and the **Feature requests** tab in `triage.py`.

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

---
