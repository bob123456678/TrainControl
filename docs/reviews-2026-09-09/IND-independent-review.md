# Independent acceptance review of v3.0.0 — the application as it stands

**Prefix for citing these findings elsewhere:** `IND9X`
(Checked against `docs/manual-tests/findings.tsv` 2026-09-09: `IND` and `IND9` are taken, `IND9X` is free.)

Reviewed 2026-09-09, branch `autonomy-diagram-r0`, by reading only. No tests were run, no builds made,
no file modified except this one, and `cs2_sample_layout/` was not touched. The two diff reviews
(last 2 days, last 7 days) are someone else's territory; nothing here is a review of a commit.

## Scope and method

Read first, as briefed: `docs/reference/behaviour.md` in full, `docs/reference/open-questions.md`,
`test/README.md`, the status column of `docs/manual-tests/findings.tsv` (32 rows still Open, listed
and skimmed), `docs/manual-tests/issues.md`, `docs/for-adam-2026-09-09.md`, `docs/UI-standards.md`.

Verified against source by me personally:

- the **persistence layer** end to end — `MarklinControlStation.saveState/restoreState`
  (MarklinControlStation.java:1628-1859), `Util.writeAtomically` (Util.java:519-546), the UI-state and
  autonomy saves on exit (TrainControlUI.java:2330-2574), `LayoutDiagram` and `CS2File` writers;
- the **FR-001 / `blockedBy`** rule and its tier fencing (Point.java:600-655, 728-755;
  Layout.java:2548-2601; HomeStaging.java:721, 1552) and the fact that both manual doors close while
  the running flag is up, which is what makes the fence coherent
  (LayoutRightclickAutonomyMenu.java:168, 284);
- the **isPathClear rule ladder** (Layout.java:2213-2379) against §§1-2 and §5 of behaviour.md;
- the §6a **edit-while-running guard** (Layout.java:2812, 2952, 2999; MarklinControlStation.java:3056);
- behaviour.md's own **load-bearing citations**: every named test exists where claimed; `build.xml`
  carries 177 test-class entries against 178 classes, the one absentee being `testAutoDetect`, which is
  deliberate; the terminus/destination invariant (Point.java:346-358, 369-399); the speed rounding
  (MarklinLocomotive.java:596); the accessory register-on-miss rule at its converted call sites
  (Route.java:325-336, TrainControlUI.java:9616-9624); the §7 Path Type tier note
  (AutonomyEditorPanel.java:6510-6530);
- the **MT-309 and MT-247 rulings of 2026-09-08** against what behaviour.md currently says
  (LayoutLabel.java:1001-1500; MarklinRoute.java:500-760);
- the cross-checks between `findings.tsv`, `issues.md` and `open-questions.md` reported under B3.

Four read-only sub-reviews were dispatched to cover the rest of the surface. **Two returned** — the
§§2/5/6/6a length-and-blocking verification, and the GUI trivial-bug sweep — and their load-bearing
claims were spot-checked by me before inclusion (one mechanism was corrected on re-reading: see C1).
**Two did not return**: the §§3/4 reversal-mechanics verification and the drawing/geometry blind-spot
audit. Those areas went unread, and the final section says exactly what that leaves unchecked.

**What held up well, stated so the findings below are in proportion:** every length, inactivity,
tail-blocking and edit-guard rule read against behaviour.md §§2, 5, 6 and 6a agrees with the code, and
almost all of it is tested — no case was found where a train is admitted where it does not fit or
refused where it fits. The persistence layer is unusually well defended: staged atomic writes
everywhere it matters, an unreadable database copied aside before it can be overwritten, and the
constructor-leak edge case handled and documented (MarklinControlStation.java:1807-1826). The
exit-save's decision tree for when the session may be captured back into the configuration
(TrainControlUI.java:2419-2501) refuses in exactly the cases where capturing would destroy authored
data. Nothing in this review found a way to lose a user's data.

---

## A - high

### A1 - behaviour.md §5c describes a covered-track indicator that no longer ships

**behaviour.md:366-377** vs **LayoutLabel.java:1001-1007, 1350-1364, 1437-1500**.

The document still says covered squares are **"greyed on the diagram until the train moves"**, that
**"the wash survives a highlight"**, and — the load-bearing sentence — **"What is drawn is what is
refused."** On 2026-09-08, under MT-247/MT-309 triage, Adam ruled *"the orange line replaces the grey
wash entirely"*, and the code now draws an opaque orange line along each covered **road** of the tile
(`TRAIN_MARK`, `paintCoveredMark`), painted over the icon on every paint. The returned sub-review
confirmed the new behaviour is pinned by tests (`testTheMarkIsALineAndNotAWash`,
`testACoveredSquareIsMarkedInOrange`, `testTheLineFollowsTheTrack`).

The mechanism change also changed the **meaning**: the old wash claimed "this track is blocked" (the
routing guard's own extent); the new line claims "the train is here" — a strict subset of what the
railway refuses, as `docs/for-adam-2026-09-09.md` itself says ("it now claims **less** than the
railway enforces, which is the reverse of the old rule"). So all three quoted sentences in §5c are now
false, in the section of the acceptance document that covers blocking.

No train moves unsafely — the refusal logic is unchanged and verified — but this is graded A on the
document's own rule at behaviour.md:4-5: where document and program differ, one is a bug, *"the
document being wrong is worse, because it is what everyone reasons from."* Adam is being asked (in the
for-adam notes) to validate the new indicator against a document that describes the old one.

**Failure scenario:** Adam or a future reader validates coverage against §5c, sees an orange line
shorter than the track the railway actually refuses, and files the difference as a defect — or worse,
"fixes" the drawing back toward the document. Concretely: OB-180 was exactly this class of report.

**Remedy:** rewrite §5c's drawing paragraphs to the 2026-09-08 ruling (line not wash, per road not per
square, marks the train's extent while refusal keeps the lock-edge extent) and record the
claims-less-than-enforced asymmetry as a stated limit, per the document's own promise at line 13.

---

## B - medium

### B1 - FR-001 "Unavailable While Occupied" is not in behaviour.md at all, and its tier split is written nowhere

**Point.java:600-655 (the `blockedBy` field), 728-755 (`heldBackBy`); Layout.java:2548-2601;
HomeStaging.java:721, 1552; messages.properties:403-405.**

A station can be configured "Unavailable While Occupied": a list of watched squares whose occupancy
makes it unavailable. This is a rule that decides where trains may go — the exact scope behaviour.md
claims for itself at its head — and behaviour.md never mentions it. Neither does `Automation.md` or
`AutomationAPI.md`; the intent lives only in code comments and the findings database.

The undocumented part with teeth is the **tier split**. The runtime clause is fenced behind
`isAutoRunning()` (Layout.java:2567, with the comment "this shapes what AUTONOMY chooses, and a person
dispatching by hand is looking at the railway"), so a manual send ignores it — coherent today only
because both manual doors close while the running flag is up. But **Return Home enforces it**, twice:
the planner skips a held-back home at candidate time (HomeStaging.java:721) and re-asks with planned
occupancy (HomeStaging.java:1552). So the row behaviour.md §1's tier table would need is: autonomy
yes, manual no, Return Home yes — and §1 currently teaches that *"Return Home sits with Manual on the
question of where a train may be sent."* FR-001 is a documented-nowhere exception to that sentence.
The UI prompt (messages.properties:405, "Autonomy will not send a train to {0}...") also under-claims:
Return Home will not either.

The returned sub-review adds a second undocumented FR-001 limit: two stations each watching the other
are swept into the shares-metal refusal by coincidence of symmetry — an over-refusal recorded only at
Layout.java:2482-2485, absent from §5c's "Known limits, deliberately" list.

**Failure scenario:** a reader of behaviour.md concludes Return Home may stage a locomotive into a
platform whose yard is occupied (manual may, and §1 says Return Home sits with manual); the plan
refuses; the refusal is filed as a bug against a rule no document states. Or the inverse: someone
"fixes" HomeStaging to match §1 and removes an enforcement Adam wanted.

**Remedy:** an FR-001 section in behaviour.md — the rule, the leaving-train exemption
(Layout.java:2570-2586), the tier answer per tier, and both known limits.

### B2 - the MT-247 route-conflict rule is enforced and tested, and behaviour.md does not state it

**MarklinRoute.java:500-760.**

What a route does when one of its accessory commands would throw a switch under a train is a safety
rule: Adam ruled it on 2026-09-06 (quoted at MarklinRoute.java:603 and 725 — one command at a time,
skip only the conflicting switch, the rest of the route still runs; and the emergency-stop interaction
at :754, where refusing the whole route once discarded an emergency stop). It is implemented, and
regression-tested (`test/regression/testAConflictSkipsOnlyTheSwitchUnderTheTrain.java`,
`testARouteDoesNotThrowSwitchesUnderATrain.java`). behaviour.md contains no sentence about it — no
"route" behaviour at all beyond reservation vs occupancy in §8.

By Adam's acceptance criterion this is the same defect shape as B1: behaviour the code enforces, that
he ruled on personally, that the document he accepts against does not state. Being a rule about
commanding metal under a moving train, it belongs in the document more than most.

**Remedy:** a short behaviour.md section: what "conflicting" means (a switch under a train, per
MT-247), the per-command grain, and the emergency-stop precedence.

### B3 - the reference pair claims a clean slate the inbox does not show

**open-questions.md:36-38 and :50 vs issues.md:62-635.**

open-questions.md states: *"the railway defects live in `docs/manual-tests/issues.md`, and that inbox
is empty"*, and under Reversals: *"**Open:** none."* Both are wrong as of this reading:

- the Inbox section of issues.md holds **more than forty entries** (OB-155 through OB-190, FR-048
  through FR-065). Most have receipt rows and code citations — the work was done but the entries were
  never cleared out of the Inbox, which is what the file's own protocol (issues.md:3-7) says happens —
  so a reader cannot tell the open from the done without grepping the codebase, which is this review
  did;
- **OB-190 records an explicitly open reversal question**, marked *"ALSO STILL OPEN, and Adam's call
  rather than a defect"*: a journey that passes a compulsory-turn square flips the train once en
  route, so at a may-reverse destination the answer "Yes — keep direction" can end the train
  **net-reversed** (measured 2026-09-08: keep ends backward, reverse ends forward). behaviour.md:139
  promises *"Yes = keep the current direction"* with *"no meaning change"*. Until Adam rules, the
  dialog's headline promise is false on exactly those journeys — and open-questions.md says no
  reversal question is open;
- a run of 2026-09-08 receipts are marked **"fixed unvalidated"** (OB-184, OB-185, OB-186 among
  them) — honest labels, but "nothing open" and "fixed unvalidated" cannot both be the state.

This is the third stale index this month by the project's own count (open-questions.md:147-153 calls
the rate "the finding"), but this one is in the document that exists to be the answer.

**Failure scenario:** Adam reads open-questions.md before accepting, sees "Open: none" under
Reversals and "that inbox is empty", and accepts a build with a reversal-semantics question he himself
left open in OB-190 and a set of unvalidated fixes he has not yet driven.

**Remedy:** clear the receipted entries from the Inbox per its own protocol; move OB-190's open half
into open-questions.md's Reversals section; note the unvalidated set.

### B4 - clicking the findings list kills every autonomy-editor keyboard shortcut for the session

**AutonomyEditorPanel.java:898-982 (`buildFindings`) with :435/:445 and :7824;
LayoutEditor.java:1854-1886.** Found by the returned GUI sub-review; the mechanism re-verified by me.

The editor's shortcuts all hang off the frame's own KeyListener, so the design makes every control
non-focusable — `AutonomyViewerPanel.unfocusable(this)` sweeps the panel recursively at :445. The
findings `JList` escapes the sweep: `findingsPanel` is built at :435 but never added to the panel — the
window mounts it separately through the getter at :7824 — so the recursive sweep cannot reach it, and
`buildFindings` never calls `setFocusable(false)` itself (verified: no such call in :898-982). A
`JList` is focusable by default and takes focus on click.

**Failure scenario:** the user opens the autonomy editor and clicks a row in the Errors/Warnings
list — the designed way to jump to a tile. Focus moves to the list and never returns (tiles are
JLabels; nothing else can take it). From then on Ctrl+G/L/D/K, Ctrl+H, Ctrl+S and +/- page stepping
silently do nothing until the window is reopened. This is the bulk-vs-single drift shape: the sibling
list in `AutonomyViewerPanel` is inside its panel when the sweep runs and is correctly swept.

### B5 - Escape does not put an armed editor tool down

**AutonomyEditorPanel.java:891-896 (`installEscape`); LayoutEditor.java:6967 and :7059.** Found by
the returned GUI sub-review.

`putToolsDown()` has one trigger: a `WHEN_IN_FOCUSED_WINDOW` binding on the panel. LayoutEditor's own
comment (:6969-6977) documents that such bindings are dead in this window — the focus owner is the
frame, a non-JComponent, so `processKeyBindings` is never consulted. The frame's own KeyListener has
an Escape branch at :7059, but it sits below the `if (isAutonomyMode()) return;` guard at :6967, so in
autonomy mode the frame drops Escape too. Net: the binding fires only in the broken focus state B4
creates — precisely inverted from intended.

**Failure scenario:** user arms "Test a path" (or Why, or One-way), changes their mind, presses
Escape. The button stays pressed, the tool stays armed, and the next diagram click is swallowed by the
gesture — the exact hazard the panel's own javadoc at :860-864 names.

---

## C - low

### C1 - GraphReducer.findPath's javadoc contradicts its own body about closed destinations

**GraphReducer.java:489-516 (javadoc) vs :622-625 (body).**

The javadoc says `isPathClear` refuses a closed final point *"only `if (this.isAutoRunning())`"* and
argues from that fence that treating a closed square as a valid destination is a safe overstatement.
Both halves are stale: the fence came off the destination check on 2026-09-06 (Layout.java:2346-2370,
unfenced), and the body itself now refuses a closed destination — :622-625 carries the comment "NOT A
DESTINATION EITHER (Adam, 2026-09-06)" and skips any edge ending on a closed square. The code agrees
with behaviour.md §7 ("an inactive square stops both"); the javadoc describes a semantics and a
justification that are both gone. (The dispatched sub-review flagged this comment; on my re-read its
description of the *code* was wrong and the finding is the comment alone.)

**Failure scenario:** a maintainer trusts the javadoc, "restores" the described leniency, and
reintroduces the measured defect the comment itself records — the tool drawing a route the runtime
refuses.

### C2 - the dispatch-choice layer is documented only at user-manual level

**Layout.java:2215 (`maxActiveTrains`), :3897 (`maxLocInactiveSeconds`); Automation.md:216-224.**

The concurrency cap, station priority, the longest-idle preference, minimum/maximum delay and atomic
routes all decide which train goes where and when. behaviour.md — whose head claims "the features that
decide where trains may go" — says nothing about any of them; Automation.md describes them in user
prose that is not written to be read against the code. One consequence is unstated anywhere: the cap
at Layout.java:2215 is fenced on `isAutoRunning()`, and Return Home runs under that flag
(open-questions.md:137-139), so **the cap binds Return Home moves** — §6's list of what Return Home
obeys does not include it.

### C3 - a third accessory-reading door still creates on a miss

**Locomotive.java:613-619 (`waitForAccessoryState`) vs behaviour.md:467-469.**

§8's rule is "Reading one must still not create it", after C13 put 2048 phantom switches into the live
database from a paint loop. The two named lookers were converted (`Route.java:336`,
`TrainControlUI.java:9475/9624`), but `waitForAccessoryState` — a public API door used by automation
scripts — polls `getAccessoryState` in its wait loop, which registers on a miss. One phantom row is
harmless by Adam's own counter doctrine; the finding is the rule enforced at two doors of three, the
codebase's commonest defect shape. **Scenario:** a user script waits on a mistyped address; a phantom
accessory appears and the wait never returns, with nothing logged.

### C4 - the save-reconciliation warning can open unowned, behind the main window

**AutonomyViewerPanel.java:1393** (found by the GUI sub-review). `save()` parents
`AutonomyReport.show` on `this` — a panel that is built but never shown (TrainControlUI.java:3740-3742;
the panel's own field comment at :73-79 says every dialog must parent on the main window for exactly
this reason). Every other dialog in the file does; `save()` is the one exception, and it is reached
from page exclusion, import, initialize, duplicate, rename and delete. **Scenario:** the "setup was
not tidied" warning — the one notice that a page's settings are at risk — opens unattached to the
application and can fall behind it.

### C5 - right-click refusal dialogs parent on the already-dismissed popup

**LayoutRightclickAutonomyMenu.java:1055, 1081, 1132-1133** and the catch handlers at :231, :415,
:520, :726, :1140 (GUI sub-review). Menu actions fire after the popup is torn down, so `this` has no
window ancestor and the dialogs centre on the screen, unowned. The same dialogs in
`AutoLocomotiveStatus` (:1022, :1068, :1108) parent correctly. **Scenario:** send a train with track
power off from the diagram; "power on to start" appears mid-monitor, is covered by a stray click, and
the refusal reads as the command silently failing.

### C6 - the One-way tool is not greyed on an excluded page, unlike its two siblings

**AutonomyEditorPanel.java:7233-7234 vs :5603/:5617/:5759** (GUI sub-review). `refresh()` greys
`testButton` and `whyButton` on an excluded page under a comment claiming nothing in the column can do
anything there; `oneWayButton` is omitted, and its `tileClicked` branches run before the
`isIgnored(tile)` check. **Scenario:** on an excluded page the user completes the two-click one-way
gesture, answers the direction dialog, and is told "no path between the squares" — the wrong
explanation for a refusal whose real cause is the exclusion.

### C7 - coverage gaps against documented behaviour

From the returned §§2/5/6/6a verification, both confirmed as absences by search:

- **the `isRunning()` half of the §6a guard has no test.** All three refusals (rename point, delete
  point, delete edge) are tested against `isStagingInProgress` (testHomeStaging:1857-1952,
  testLayoutRenameKeys:337) and none against a live run — the other half of the OR at
  Layout.java:2812/2952/2999.
- **nothing asserts the covered mark survives an accessory highlight flash** — behaviour.md:371-373
  states the rule; the old wash-restore test went with the wash (see A1).
- one §4 limit is code-only: a recorded `arrivedFrom` naming a side no track leaves by (stale after an
  edit) stops the tail walk entirely (Layout.java:5662-5664) — blocking nothing even where geometry
  is unambiguous. §4:236-239 says the walk still follows a forced answer; a stale side defeats that,
  and no document states it.

---

## D - minor

### D1 - test/README.md's class counts are stale again

**test/README.md:5-13** says 81/29/54, "Counted 2026-09-08". Counted today: 82 in `core/`, 30 in
`ui/`, 66 in `regression/` — a drift of 14 classes in the table whose own caption says it "has to be
recounted to be believed" (MON-C20). `build.xml` itself is complete (177 entries; only the deliberate
`testAutoDetect` absent — verified by set difference), so nothing fails to run; only the document is
wrong.

### D2 - the "two known unsoundnesses" are pointed at from two places and stated in neither

**Layout.java:2536-2538** says the two known limits of the room sum "are all in
`measuredRoomAtTheBerth`"; the javadoc there (**Layout.java:7429-7430**) says they are "recorded at
the call site in `isPathClear`". The pointers are mutually dangling; the substantive text lives only
in behaviour.md §5c (which neither cites). Against the project's own self-contained-comment rule
(behaviour.md:15-21), one of the two should state them or cite §5c explicitly.

### D3 - the One-way direction dialog centres over the tools strip

**AutonomyEditorPanel.java:5651** passes `this` to `showOptionDialog` where every sibling goes through
`owner()` — whose javadoc (:2824-2841) exists precisely to stop dialogs appearing hard against the
window edge. (GUI sub-review.)

### D4 - demo-layout extraction handles resources loosely

**TrainControlUI.java:19861-19876** (`copyResource`): no try-with-resources, so both streams leak if
the read throws; **:19933-19940** (`unzipFile`) copies without `REPLACE_EXISTING` and logs-and-
continues per entry, so a half-existing target folder yields a silently partial demo layout. First-run
demo path only; no user data is at risk.

### D5 - a latent unboxing NPE at the capacity check

**Point.java:980** unboxes `loc.getTrainLength()` before `whyTooLongForTheBerth`'s own null check at
Layout.java:7399. Every production writer passes a primitive today; this bites only a future API
caller handing a locomotive with a null train length to a capacity-limited destination. (Sub-review
finding, noted for the record.)

### D6 - a test name says the opposite flag from the one it asserts

**test/core/testAPastedTrainKeepsItsDirection.java:328**:
`testACompulsoryTurnStationIsNotEmittedAsADestination` asserts `isAutoDestination()` — correctly, per
the corrected 2026-09-08 table — while its name says "Destination". The body's own comment (:347)
calls the two flags reading as synonyms the trap (MON-C14, MON-C5); the name is now an instance of it.
behaviour.md:98 cites the test by this name, so a rename must touch both.

---

## Is this acceptable?

**Close, and not today.** The core of what this railway does — where a train may be sent, whether it
fits, what its tail blocks, what may be edited while it runs, and whether the user's data survives an
exit — held up under adversarial reading better than most codebases I have reviewed: rules match the
document, the tests genuinely pin them, and the persistence layer is defended in depth. Nothing found
here loses data or moves a train unsafely.

What blocks acceptance is Adam's own criterion: *the behaviour needs to be documented and tested.*

I would insist on, before calling v3.0.0 accepted:

1. **Re-sync behaviour.md with the 2026-09-08 rulings and the unwritten rules** — §5c's covered-track
   description (A1), an FR-001 section with its tier answer (B1), and the MT-247 route-conflict rule
   (B2). These are hours of writing, not code.
2. **Make open-questions.md and the issues Inbox tell the truth** (B3), and put OB-190's
   net-reversal question in front of Adam explicitly — it is the one open item that makes a documented
   promise ("Yes = keep the current direction") false in a case an operator will hit.
3. **Fix B4 and B5** — one `setFocusable(false)` and one relocated Escape branch. Small fixes, but a
   shortcut layer that dies on the first click of the findings list is exactly the "trivial bug" class
   Adam spent a day reporting.
4. **Adam's hands-on pass over the "fixed unvalidated" receipts** (OB-184/185/186 et al.) and the
   19-test manual checklist — several September fixes have never been driven on the real railway.

The C and D items can ride behind the release without endangering it.

## What this pass could not check

- **behaviour.md §§3-4 against the code** — the may-reverse prompt at both doors, the dialog's
  default-and-dismiss semantics, the `putIfAbsent`/`reconcileFacingWhenIdle` mid-run direction
  handling, `arrivedFrom` capture order, and the paste walk. The sub-review dispatched for it did not
  return. B3's OB-190 finding touches this area from the documents side only; the code went unread.
- **The unexamined layer named in test/README.md** — what the Swing components draw, orientation
  arithmetic on curves, the lock-edge derivation at real switches and crossings, and page-portal
  contraction. The sub-review dispatched for it did not return. This is the layer test/README.md says
  defects hide in by construction, and this review adds no assurance about it.
- **Anything about the last two and seven days of commits as changes** — deliberately out of scope;
  the diff reviewers own it.
- **Any runtime behaviour.** Nothing was executed: no tests, no application launch, no probes. Every
  finding above is from reading, and the project's own record ("review by running, not reading",
  docs/manual-tests) says reading passes miss what execution finds.
- **The 30-odd Open rows in findings.tsv** were listed and skimmed, not re-verified against the code.
