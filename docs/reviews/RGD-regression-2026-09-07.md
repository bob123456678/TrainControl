# RGD - Regression review: did today's 28 commits collide?

**Status:** open

**Version reviewed:** commit `19521289` ("MT-274: the four days of work nobody has driven"), branch
`autonomy-diagram-r0`, 2026-09-07. **Window:** the 28 commits `a915b0ad` .. `19521289` (02:25 to
21:32 on 2026-09-07). **Reviewed:** 2026-09-07.

**Prefix for citing this document: `RGD`.**

**Method.** Read-only, per the brief: no build, no test run, no `ant`, nothing under
`cs2_sample_layout/` opened, no file changed except this one. The question is narrow - not "are the
new features right" but "did two of today's changes answer one rule differently, did a later commit
undo an earlier one, and did anything get past a test by weakening it". Every claim below is a read
of the enforcing line at HEAD, with the diff used only to date it. Where a conclusion needs an
execution I could not do, the Confidence row says so and names the run.

There are no A findings. The two B findings are both cases where a rule fixed at one door today is
contradicted by a door that was not swept - which is this codebase's most-repeated defect shape, and
both are recorded as such in the code's own comments one screen away from the gap.

---

## Status

| # | Finding | Severity | Confidence | Disposition |
|---|---------|----------|------------|-------------|
| RGD-B1 | The `parseAuto` arrival-side fix's own invariant ("nothing the file says about the tail can be stale") is false for files built after a `captureFromLayout`, which changes a square's occupant without touching its recorded tail | B | High (trace) | Open |
| RGD-B2 | The covered-track refusal walks ALL lock edges, but lock edges also carry FR-001 restriction pairs that share no metal - the code comment, `behaviour.md` and the error message all state the shared-tiles-only version | B | High on mechanism, medium on intent | Open |
| RGD-C1 | `behaviour.md` §3 still asserts, in bold present tense, the manual-turn refusal that two paragraphs later it says "cannot arise" - left standing by the commit titled "the documents stop arguing both sides" | C | High | Open |
| RGD-C2 | The status tables of `IND9`, `REG8` and `VAL9` are stale at HEAD: at least eight findings fixed today, each fix citing the id, with every row still saying Open - the `REG8-C6` failure recurring in the three newest documents | C | High | Open |
| RGD-C3 | FR-061's caption dropdown and the text-labels state can disagree through two doors `applyCaptionMode` does not guard: a restart with "None" remembered, and Control+L in autonomy mode | C | High (trace; needs one manual check) | Open |
| RGD-C4 | `buildArrivedFromMenu` lacks the OB-177 rule its sibling thirty lines up carries: a recorded side the build no longer offers is invisible, and clearing was removed the same day | C | High | Open |
| RGD-C5 | `nameOfPlacedLocomotive` reads only the JSONObject placement shape; the reader above it handles the bare-string shape by design - so re-placing the same locomotive over a hand-edited placement discards a still-true arrival side | C | High, narrow reach | Open |
| RGD-C6 | `LayoutGrid` carries two comments that contradict each other about `getEdit()` in autonomy mode; today's OB-179 edit rewrote the block containing the false one and kept it, and today's wash fix silently depends on the true one | C | High | Open |
| RGD-C7 | `takeReversalsOnArrival` drains before the write is durable: a null session or an exception mid-loop loses recorded reversals for good | C | High, narrow reach | Open |
| RGD-D | Collision pairs checked and found compatible (see section) | - | - | Recorded |

---

## RGD-B1 - The arrival-side fix and `captureFromLayout` answer "whose tail is this?" differently

**The two things that disagree.** The comment justifying today's `REG8-A1` fix, and the capture key
list that has not heard of the field.

- `src/org/traincontrol/automation/Layout.java:9249-9258` (commit `95c09ac4`): arrival sides are now
  applied AFTER locomotives are placed, with the last word given to the file, on this stated
  invariant: *"a saved configuration describes a state that was consistent when it was written - this
  train, on this square, having come in from that side - so nothing it says about the tail can be
  stale with respect to anything else it says."*
- `src/org/traincontrol/automationui/AutonomySession.java:2451-2453`: `POINT_OPERATIONAL_KEYS` is
  `loc, active, maxTrainLength, speedMultiplier, priority, home, excludedLocs`. No `arrivedFrom`. So
  `captureFromLayout` (`:3623` onward) updates a square's **occupant** in the setup - `loc` is
  captured, and even the facing is captured by its own special case at `:3755` - while the square's
  recorded **tail** is left exactly as it was, and the true tail sitting on the running `Point`
  (written by autonomy on arrival, `Layout.java:6585`) is discarded.

A configuration written after a capture can therefore say "train B, on square X, having come in from
the side train A came in from". The invariant the fix rests on is false for precisely the files the
editor writes after a run.

**Why this is a collision and not just the known-open half of `REG8-A1`.** `REG8-A1`'s body
(`docs/reviews/REG8-regressions-2026-09-07.md:89-97`) warned about this in terms: *"The ordering fix
and the capture-key fix have to land together."* They did not land together - `95c09ac4` landed the
ordering fix alone. Before today, the stale side could not reach a train: `setLocomotive`'s
occupant-change clear wiped every file-side answer during the build (wrongly wiping the fresh ones
too - that was `REG8-A1`). Today's fix deliberately overrides that clear for file values. So the fix
that makes fresh answers survive is the same edit that makes stale ones survive; the clear that used
to mask this gap was the thing removed from its path. The three doors that DO clear on an occupant
change (`Point.setLocomotive` at `Point.java:534`, and both halves of
`AutonomySession.placeLocomotive` at `:4657` and `:4692`, the second added today for `IND9-A1`) now
all agree, and the fourth door does not - which is the exact sentence `IND9-A1`'s fix comment uses
to describe the defect it fixed: *"track behind train B is refused on the strength of where train A
came in, silently, and it survives a save."*

Note also `VAL9`'s verdict table (`VAL9-validation-2026-09-07.md:19`) reads "**Correct.** Both
halves" for `IND9-A1`/`REG8-A1`. Read charitably, "both halves" means the ordering and the clearing
sweep, which are right as far as they go; but nothing anywhere records that the fix's justifying
invariant is broken by the capture path, and `REG8-A1`'s row still says Open with no note of what
landed.

**A user sequence that exposes it.** Paste train A onto square X on the track diagram and answer the
arrival-side question (side W is now in the setup). Run autonomy so A departs and train B arrives at
X (the running Point now holds B with its true side, say N). Open the autonomy editor - the capture
writes `loc: B` into the setup and leaves `arrivedFrom: W`. Save, restart, load the configuration.
B now stands at X with its tail recorded on side W: the walk greys and refuses the rail behind where
A used to be, and leaves the rail B actually lies across open.

**What I would run.** `LayoutSandbox` on the test fixture: place A with a side, simulate B arriving
(or just `placeLocomotive` B through the door and then write `loc` via a capture), rebuild, and
assert the parsed Point's `getArrivedFrom()` for B. By the trace it answers A's side today.

**The fix direction the code already names:** either add `arrivedFrom` to the capture the way
`FACING` is special-cased at `:3755` (capturing the running Point's value, which is the true one),
or have the capture clear it when the occupant it writes differs from the occupant recorded - the
same predicate `placeLocomotive:4692` uses.

| | |
|---|---|
| **Severity** | B - wrong tail-blocking on the layout in a specific but ordinary sequence (run, then open editor, then reload). Argued down from A because it needs the capture step and the wrong value is at worst one square's blocking; argued up from C because the failure is silent and survives every save. |
| **Confidence** | High. Every hop is a read of the enforcing line; the only unexecuted step is the composition. |
| **Disposition** | Open |

---

## RGD-B2 - "Shares metal" is the claim; "shares a lock edge" is the code

**The two things that disagree.** The new covered-partner refusal, and every description of it -
including its own.

- `src/org/traincontrol/automation/Layout.java:2446-2473` (commit `f93d84ce`): `isPathClear` now
  refuses a path when any edge on it has a **lock partner** in the covered set. The comment says:
  *"Lock edges are exactly the relation needed and already exist: GraphReducer derives them from
  SHARED TILES."* `docs/reference/behaviour.md:346-350` says the same: refused when an edge *"shares
  metal with a covered one - the lock-edge relation, which `GraphReducer` derives from shared
  tiles."*
- `src/org/traincontrol/automationui/AutonomyBuilder.java:1138-1170`: the `lockedges` array the
  runtime parses (`Layout.java:9098` -> `addLockEdge`) carries a **second population**: *"FR-001, on
  top of the locks the reduction derived: an edge arriving at a station somebody has held back gains
  every edge that ENDS at the square being watched."* Those pairs share no tiles at all - FR-001 is
  "station unavailable while another point is in use" (issues log, 2026-08-23), an operational
  exclusion between possibly-distant pieces of track.

`getLockEdges()` cannot tell the two populations apart, so the new walk treats an FR-001 pair as if
it were shared metal. Consequence: let station S be `blockedBy` square W, and let a train longer
than its lead-in stand just beyond W so that its tail covers an edge ending at W. Every path into S
now carries a lock partner that is covered, and is refused - over metal that is physically clear -
with `autolayout.errorTrackCoveredByStandingTrain` naming that train as "standing across" an edge of
the path (`Layout.java:2478-2484` formats `e.getName()`, the path's own edge, which the train is not
on). FR-001's own semantics ("in use", i.e. a route holding the lock) say nothing about a standing
train's tail near the watched square.

This refuses MORE, never less, so no train is endangered - but `traincontrol-guards-need-a-way-past`
is the standing rule here, the refusal is unexplainable from the message, and the two authorities
that should describe it (`behaviour.md`, the comment at the check) both describe a narrower rule
than the one enforced. If Adam decides the wider refusal is actually right for FR-001 pairs, the
finding inverts into two doc corrections; either way the two must stop disagreeing.

**A user sequence that exposes it.** On a layout with an FR-001 restriction (S blockedBy W): set a
train length longer than the measured lead-in of the square one hop past W, stand the train there
with its tail toward W, then right-click any train and ask for a route to S. Expected under FR-001:
allowed (nothing is en route to W). Actual by trace: refused, blaming the standing train.

**What I would run.** A fixture with a blockedBy pair and a long train past the watched square,
asserting `isPathClear` on a route into the held-back station - plus the control: the same route
with the FR-001 restriction removed, which by trace is allowed.

| | |
|---|---|
| **Severity** | B - incorrect refusals in a specific configuration (FR-001 + measured lengths + a long train), with a misleading message. Not A: it fails safe. |
| **Confidence** | High that both lock populations flow into the one list the walk reads (reads of builder emission and `parseAuto`). Medium on intent - only Adam can say whether FR-001 should extend to standing tails; the doc as written says it does not. |
| **Disposition** | Open |

---

## RGD-C1 - behaviour.md argues both sides of the manual-turn refusal

**The two things that disagree.** Two paragraphs of `docs/reference/behaviour.md`, fifteen lines
apart, at HEAD:

- `:136`: *"**A manual journey that needs a turn the operator declined is refused before it
  starts**, naming the square."* - present tense, bold, stated as the rule. Added by `5f492d9f`
  (2026-09-06, just before the window).
- `:147-155` (added in-window by `8b4531ec`, "A turn made at the destination records itself; the
  documents stop arguing both sides"): *"That ruling **dissolved** the refusal described above rather
  than qualifying it"* and *"**No journey is refused on the operator's answer any more, and none can
  be** - a claim to the contrary stood here until 2026-09-07."*

The claim to the contrary does not "stood here until 2026-09-07" - it stands there now, three
paragraphs up, and it is the more prominent of the two. The code agrees with the second paragraph
(`d45d7951` removed the refusal mechanism, its helper, both door checks and the message in all eight
bundles; `REG8-C2` verified nothing enforces it). A reader who stops at `:136` - and the bold rule
is formatted to be the stopping point - takes away the opposite of what the railway does. This is
the `REG8-C1` defect shape ("behaviour.md states both the old and new rules") recurring in the same
file on the same day it was fixed for §4's clearing text, by the commit whose title is the fix.

**Fix:** rewrite `:136-140` in past tense as the history it is, or delete it and let `:147` carry
the story.

| | |
|---|---|
| **Severity** | C - document only, but this is the document the review discipline names as the arbiter when code and intent disagree, so it arguing with itself is worth more than an ordinary comment drift. |
| **Confidence** | High - both paragraphs quoted from HEAD. |
| **Disposition** | Open |

---

## RGD-C2 - Today's fixes landed; the three newest status tables did not move

**The two things that disagree.** The dispositions in today's review documents, and today's commits.
All three tables were written once and never updated, while the fixes landed the same day citing the
finding ids by name:

- `IND9-independent-2026-09-07.md` - every one of its 12+ dispositions reads **Open** at HEAD.
  Fixed today with the id cited at the fix: `IND9-A1` (the clears in `placeLocomotive:4657,4692`,
  commit `95c09ac4`), `IND9-B4` (`reversedOnArrival`, `8b4531ec`), `IND9-B5` (`wouldAsk`,
  `073ab12a`/`1bd81f53` and its test in `512a6049`), `IND9-B3` (behaviour.md §3's mid-run text,
  `8b4531ec`), `IND9-C2` (`parseReleaseVersion`, `1bd81f53` then `512a6049`).
- `REG8-regressions-2026-09-07.md:29-37` - all rows **Open** at HEAD. Fixed today citing the id:
  `REG8-B2` (`commitAndRecord`, `51a6a971`, save added `512a6049`), `REG8-C1` (§4 clearing text,
  `8ea1ff15`), `REG8-C3` (the call-site comment rewritten - `Layout.java:2488` now cites REG8-C3 as
  fixed), `REG8-C4` (`testADirectionChangeIsNotSwallowed` rewritten citing it), `REG8-C5` (tooltips
  attached in `dd3fb5d4`, citing it), `REG8-C6` (REG7's table corrected in `512a6049`). `REG8-A1` is
  genuinely half-open (see RGD-B1) but the row does not say which half landed.
- `VAL9-validation-2026-09-07.md` - created by `512a6049`, the same commit that fixed `VAL9-A1`
  (the toggle), `VAL9-B1` (the save), `VAL9-B3` (the regex) and added `VAL9-B4/B5`'s tests; its
  rows say **Open** for all of them, stale on arrival.

This is `audit-the-bodies-not-the-index` - the failure `REG8-C6` was filed about, recurring in the
three documents written since, one of which is the document that filed it. The README's rule is that
the table is the one status location; by that rule, anybody triaging tomorrow re-investigates eight
finished fixes.

**Fix:** one editing pass over the three tables against `git log --grep` for each id, the same
audit `cab0bb64` performed for open-questions §2b.

| | |
|---|---|
| **Severity** | C - documents only, but it compounds: each stale table is the input to the next audit. |
| **Confidence** | High - every "fixed" claim above was verified against the code or doc at HEAD, not against commit messages. |
| **Disposition** | Open |

---

## RGD-C3 - The caption dropdown can disagree with the captions, at exactly the moments its guard does not run

**The two things that disagree.** FR-061's premise, and the two doors `applyCaptionMode` leaves open.

`AutonomyEditorPanel:239` remembers the caption mode (`PREF_CAPTION_MODE`, a preference);
"None"'s effect is the editor's text switch, which is `LayoutDiagram.editHideText` - a plain field,
default `false`, that survives nothing (`LayoutDiagram.java:77`). `applyCaptionMode`
(`AutonomyEditorPanel:5304`) deliberately skips the text switch when not interactive (`:5311`,
*"opening a setup never overrides a choice somebody made in the plain editor"*). FR-061's stated
premise (`:223-236`): *"Four mutually exclusive options cannot be in a state that needs
correcting."* Two sequences put them in one:

1. **Restart with None remembered.** Select None (text goes off, mode stored). Quit; reopen; open
   the autonomy editor. `editHideText` is back to `false`, the dropdown restores to None, and
   `applyCaptionMode(false)` does not touch the text - so station names are drawn under a control
   that says None. The remembered setting Adam asked for ("with the setting remembered between
   open") is remembered as a word and not as an effect.
2. **Control+L in autonomy mode.** The Text Labels checkbox is hidden in autonomy mode
   (`LayoutEditor.java:1529`) *"because its None option IS this switch turned off"* - but the
   comment on the same lines notes Control+L still reaches `toggleText` in both modes. Press it with
   the dropdown on "Parked Locs": the captions vanish, the dropdown still says Parked - OB-174's
   reported symptom ("indistinguishable from a control that does not work"), reachable through the
   shortcut the tidy-up left wired.

**Fix direction:** either reconcile on open (mode != None implies text on - the restart case only,
leaving the in-session courtesy alone), or have the dropdown listen to the text state and show None
when it goes off.

| | |
|---|---|
| **Severity** | C - display state only, self-corrects on the next dropdown touch. |
| **Confidence** | High on the traces; the restart case deserves one manual check because whether the autonomy editor draws captions with text off depends on the `else if (!layout.getEditHideText())` gate at `LayoutGrid.java:1347`, which I read but did not run. |
| **Disposition** | Open |

---

## RGD-C4 - The arrived-from menu did not inherit its sibling's OB-177 rule

**The two things that disagree.** Two halves of the same submenu, thirty lines apart, built the same
day.

- `AutonomyEditorPanel:3040` (`buildFacingMenu`): a recorded facing the square cannot hold is
  **added to the list** so the menu can show it ticked - OB-177, *"the menu opened with every choice
  blank, which reads as 'this train has no facing' when in fact it has one this square cannot
  hold."*
- `AutonomyEditorPanel:2907-2980` (`buildArrivedFromMenu`, created `95c09ac4`, switched to the
  build's sides by `f1ce9681`): radios are ticked by `side.equals(recorded)` against
  the build's side list only. A recorded side not in that list - recorded before `f1ce9681` under
  the geometric naming, which the same commit establishes differs on curves ("asks if the train
  arrived from the south or from the west, rather than the north or the south"), or recorded before
  a track edit re-plumbed the square - ticks nothing and appears nowhere.

The menu then reads as "no tail recorded" while a wrong side is silently steering the tail walk -
and `8ea1ff15` removed the clearing option the same day, so the only remedy (pick a listed side) is
available but nothing tells the operator it is needed. The facing menu solved exactly this and the
sibling was not swept - `fix-one-site-sweep-the-siblings`, inside one method's fold.

**A user sequence:** answer the arrival prompt on a curved square before updating (or hand-edit a
setup with `arrivedFrom: "E"` where the build splits N/S); open the "<loc> is facing" menu: the
Arrived-from section shows N and S, neither ticked.

| | |
|---|---|
| **Severity** | C - the stale value's blocking consequence is bounded (arrivedFrom picks between candidates), and the state needs a pre-`f1ce9681` answer or a track edit. |
| **Confidence** | High - both code paths read at HEAD. |
| **Disposition** | Open |

---

## RGD-C5 - Two readers of one placement shape, and the new one knows less

**The two things that disagree.** `AutonomySession:4600-4627` (the placement reader feeding
`locomotiveAt`) explicitly handles a placement stored as a **bare string** - *"an older
autonomy.json may hold one - and drawing nothing on an occupied platform is the one wrong answer a
label must not give."* `nameOfPlacedLocomotive` (`:4745`, added by `95c09ac4` for the IND9-A1
occupant-change guard at `:4692`) returns null for anything that is not a JSONObject.

So on a hand-edited or older setup holding `"loc": "BR 218"`, re-placing BR 218 on its own square -
which any door does when it writes the placement back unchanged - reads as an occupant change and
clears `arrivedFrom`, which is precisely the case the method's javadoc says it exists to avoid
("does not read as a change of occupant and throw away an arrival side that is still true"). The
loss is a true arrival side, i.e. tail blocking quietly off for that square until the next arrival.

**Fix:** route both readers through one name-extraction (the `:4600` logic is already the complete
one).

| | |
|---|---|
| **Severity** | C - needs the legacy string shape, which this program never writes. |
| **Confidence** | High - both readers quoted from HEAD. |
| **Disposition** | Open |

---

## RGD-C6 - One file, two opposite claims about `getEdit()` in autonomy mode - and today's wash fix leans on one of them

`LayoutGrid.java:892-900` derives `inEditor = layout.getEdit() && master instanceof LayoutEditor`
and `:924` states *"layout.getEdit() is true in BOTH - the autonomy editor borrows the diagram
editor's edit flag."* `LayoutGrid.java:1037-1039` states the opposite: *"autonomy mode deliberately
does not set that flag, so `inEditor` is false in the one editor where coordinates matter most."*
Today's OB-179 commit (`eca98e6a`) rewrote the block those lines sit in and kept the sentence.

Tracing the window code says `:924` is the true one: `LayoutEditor.render()` calls
`layout.setEdit()` unconditionally (`LayoutEditor.java:5491`), autonomy mode included
(`setAutonomyMode` runs between the constructor and `render()`, per the comment at the call), and
`LayoutEditor.java:5876` says outright that the flag is set. The confusion has a source:
`setAutonomyMode`'s javadoc (`:1505`) says it "does not call layout.setEdit()" - true of that method
and false of the window it runs in.

Why it matters today: `f93d84ce`'s wash rule is `boolean covered = !edit` in `LayoutLabel`, where
`edit` is `inEditor` (`LayoutGrid:1184`). Adam's ruling is "not the autonomy or diagram editor, only
the track diagram viewer" - which holds only if `getEdit()` IS true in autonomy mode, i.e. only if
the retained comment is wrong. It is, so the behaviour is right; but the file now documents the
premise under which today's fix would be broken, and
`testEditorSurfaceRules.testTheCoveredWashIsDrawnWhereItShouldBe` pins only that the text `!edit`
exists, not which editors it covers. One manual glance at the autonomy editor with a long train
standing (MT-worthy, one line) would settle it permanently.

| | |
|---|---|
| **Severity** | C - comment defect; behaviour verified correct by trace. |
| **Confidence** | High on the trace; the render-order claim (`setAutonomyMode` before `render()`) is from the code's own comment plus the call at `TrainControlUI:4799`, not from an execution. |
| **Disposition** | Open |

---

## RGD-C7 - The reversal drain can lose what it drained

`TrainControlUI.reconcileFacingWhenIdle` (`:6158`) iterates `built.takeReversalsOnArrival()` -
which **removes** the names from the layout's record (`Layout.java:3067`, drained by design so one
reversal is written once) - and only then asks whether there is a session to write them to:

- `if (session != null) session.flipFacing(...)` - with a null session (no local layout folder, or
  an unusable store) the names are drained and dropped. Mostly moot (no setup exists to be stale),
  but a transiently unusable store loses them for good.
- The whole loop sits in the method's one `try`; if `flipFacing` throws on the second of three
  names, the third was already drained and is never written, and the first may have been.
- A configuration reload between run-end and the next `updateVisiblePoints` swaps the `Layout`
  object; pending reversals go with the old one.

All three are narrow, but the field's own comment (`Layout.java:643-668`) presents the record as
the mechanism that makes a destination turn durable - "recorded here, where it is certain". Drain
after the write succeeds (remove per-name after `flipFacing` returns), and the first two vanish.

Related and already filed: the late-echo race is `IND9-B6`/`REG8-B1` (Open, correctly). One new
wrinkle worth adding to that finding rather than a new one: now that `8b4531ec` records the turn,
the race's outcome changes shape - a post-run echo that beats the reconcile flips once via
`followDirectionChanges` AND once via the drain, and the toggle means the two cancel: the graph ends
up NOT turned for a train the railway turned once. Same probability as before, worse direction.

| | |
|---|---|
| **Severity** | C - each path needs an unusual state (no store, a throwing flip, a reload in the gap, a >1s echo). |
| **Confidence** | High on the reads; the race half needs a run and says so in REG8-B1 already. |
| **Disposition** | Open |

---

## RGD-D - Collisions hunted and NOT found

The most useful part of a regression pass. Each pair below was traced end to end and is compatible
at HEAD.

**D1 - Message bundles.** All eight bundles took identical key edits (verified by diffing the
added/removed key sets per file): removed `layout.ui.menuShowCoordinates`,
`layout.ui.tooltipShowCoordinates`, `autosetup.ui.arrivedFromUnknown`; added the four caption-mode
options, the two picker buttons, the two menu headings; reworded the four side labels and
`tooltipGrid`. Full key-set parity holds across all eight (not only the deltas), zero non-ASCII
bytes in any bundle, and no Java in `src/` or `test/` references any removed key.

**D2 - No `.form` file was touched and no `//GEN-` block was edited** anywhere in the window. The
FR-061 sidebar control and the two size-button tooltips were added from hand-written code beside the
generated form, per the standing rule.

**D3 - `facingChoices`: the old author is actually gone.** `AutonomySession.onwardFrom` was deleted
(DR-B6), `facingChoices:4852` reads `facingsFor` -> `StationIndex` -> the builder's `facingByName`,
and `facingsThatCannotBeHeld` (`:4937`) reads the same door. The remaining `onwardFrom` in
`AutonomyBuilder` (`:456`) is the single author itself, not a surviving copy. The two test files
that still name `AutonomySession.onwardFrom` do so in historical javadoc only, updated in-window to
say the circularity is gone.

**D4 - The trapped-arrival walk: the old copy is gone and the fold cannot eat the question.** The
diagram-walk in `check()` was replaced by `tilesWithATrappedArrival` over `builtForInspection()`
(DD-A7), and no second derivation of "arrived and cannot leave" survives in the session -
`badCopies` reads the same build. Folding the arrived-from items into the facing menu cannot hide
the tail question either: `buildFacingMenu` returns null only when `facingChoices` is empty, which
(both now being reads of the builder's copy list) coincides with `arrivalSides` being empty, which
empties the tail menu too. And on a half-drawn diagram the new derivation reports the same things
the old walk did - both operate on whatever reduction exists - so DD-A7 did not import DD-B9's
"incomplete build" hazard; the DD-B9 attempt itself was verified backed out with no code residue
(`b06e043b` is docs-only; `reachableTiles` callers unchanged).

**D5 - `forPlacement`/`wouldAsk`: no caller was lost, and the two cannot drift.** Exactly one
production caller of each (`TrainControlUI:6037,6043`), both passing the same
`getAutonomySession().arrivalSides(aimed)` expression; the geometric fallback inside
`ArrivalSidePrompt` is reachable in the application only with no session at all, where there is no
build to prefer. `testWouldAskAgreesWithWhatThePromptDoes` pins both halves and names the
one-line mutation (`return mayReverse;`) that would silently restore IND9-B5.

**D6 - `pickLocomotive`: both call sites got the new parameter deliberately.** The home door passes
`driving, parked` (FR-010's two shortcuts); the add-to-autonomy door passes `null, null` with the
reason recorded (FR-011: the driven locomotive is usually already in autonomy). The parked shortcut
reads the SETUP, matching what the editor draws. No third caller exists.

**D7 - OB-179 left nothing dangling.** `SHOW_COORDINATES_PREF`, `toggleCoordinates` and
`showingCoordinates` have zero remaining readers; the retired key is documented at
`TrainControlUI:244` per the house convention; Control+K now routes to `setShowGrid`, which also
sets the checkbox (`LayoutEditor:4041` - and `JCheckBox.setSelected` fires no ActionListener, so
the checkbox's own listener at `:3978` cannot recurse); the ruler still draws only in editors
(`master instanceof LayoutEditor`), so the viewer regression OB-172 fixed cannot return.

**D8 - `parseAuto`'s new ordering is not undone downstream.** After the arrival sides are applied
(`Layout.java:9260`), the only remaining step is `rebuildHomeStations`, which touches
`homeStations` and `setHomeLoc` and never `setLocomotive` - so no later pass re-triggers the
occupant-change clear on the freshly applied sides. The locomotive placement (`:8833`) and the
assignment application both precede it.

**D9 - `commitAndRecord`: the extraction lost nothing and both doors call it.** The editor door
(`AutonomyEditorPanel:4173`) and the diagram door (`LayoutRightclickAutonomyMenu:680`) both go
through it; the heading is read before the commit, from the arriving train; the save VAL9-B1 found
missing is inside the shared door with its failure logged. The old eight lines exist nowhere else -
grep finds no third `commitChanges()` caller that writes placements.

**D10 - The wash changes are mutually consistent.** Viewer-only (`!edit`), survives a highlight by
re-asking (`stillCovered`), lighter constant confined to `ImageUtil.COVERED` - and the greying and
the refusal now derive from the same covered set plus the same lock-edge relation, which is what
behaviour.md §5c's "what is drawn is what is refused" asks for (RGD-B2 is about that relation
carrying more than the sentence says, not about the two surfaces disagreeing).

**D11 - The reversal record and the mid-run-ignore rule divide the cases cleanly** in the normal
timing: an arrival turn's echo lands inside the driver thread's own 1s pause, while `isRunning()`
(which counts locomotive threads, not just autonomy - verified at `Layout.java:1689`) is still true,
so `followDirectionChanges` takes the baseline-only branch and the recorded toggle is the single
writer; the reconcile writes the flips before levelling, and the source-order test pins that
ordering. The residual race is REG8-B1's, noted under RGD-C7.

**D12 - The test work strengthened; nothing passed by weakening.** `TA-C1` went from
`assertNotEquals(IMPOSSIBLE)` to `assertEquals(READY)` plus named moves for both locomotives;
`TS-C3`'s bare returns became `SkipException`, which the battery's Failures:0-AND-Skips:0 bar
counts; `testDiagramLooksRight` now drives the dropdown - the control that exists - and
`JComboBox.setSelectedIndex` fires the ActionListener, so the pref-write and rebuild assertions
still exercise the listener rather than a field; the keyboard-drag and paste-direction suites are
new coverage. The C13-consequence rewrite in `testAdvancedRoutes` still requires a genuinely free
address and still fails loudly when none exists.

**D13 - `cs2_sample_layout/` was not touched by anything in the window** (no commit in the range
adds, modifies or reads it in code paths changed today), and the new tests build their fixtures by
hand or under `test/test_layout`.

---

## Limits of this review

Read-only, so: nothing was compiled (a signature mismatch that javac would catch is outside what
reading proves, though every changed signature's callers were enumerated by grep); no test was run
(claims about what a test would catch are reads of its assertions); and the three findings that
turn on runtime timing or operator intent say so in their Confidence rows. The single most valuable
execution this document cannot perform is the RGD-B1 composition - capture, rebuild, ask the Point -
followed by the RGD-B2 fixture with an FR-001 pair; both are specified above precisely enough to
write from the sandbox.
