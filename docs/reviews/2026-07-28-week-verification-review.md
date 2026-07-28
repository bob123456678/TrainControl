# Past-week verification and fresh pass - 2026-07-28

**Prefix for citing this document: `WR`.**

**Version reviewed:** commit `08610a8`, branch `master`, working tree clean. **Scope:** every commit of
the past week (`v2_7_2`-era fixes through the staging feature) checked for a second reader; the one
commit that had none (`08610a8`) verified hunk by hunk; a fresh pass over the staging feature and its
UI surfaces as they stand at HEAD, asked for with a focus on user-facing defects and regressions.
**Reviewed:** 2026-07-28. **No code was changed as part of this review, and no tests were run** - the
author builds and tests in NetBeans. Claims were verified by reading the enforcing method, or by
scripts that read the real data (bundles, call sites) rather than sampling it.
**Validation round:** the fixes landed as `6be3bda` and are validated in their own section below;
statuses in the table reflect that round.
**Third round:** the uncommitted button-consistency changes in the working tree above `6be3bda` are
reviewed in their own section; `WR-B2` and `WR-C6`-`C8` were filed there.
**Fourth round:** commits `178aa4c` (the fixes for `WR-B2`/`C6`-`C8`, validated) and `d1f7008` (the
assignable-home-stations feature, which the author had not yet tested) are reviewed in their own
section; `WR-B3` and `WR-C9` were filed there.

**Method note.** The instruction this review was commissioned with is the one the cycle's own record
supports: across three evaluation rounds, every defect that survived verification was found by writing
tests or reading the enforcing method, and every withdrawn claim came from inferring behaviour from a
name, a shape, or a memory. Accordingly, nothing below is claimed from a diff alone: each finding
names the enforcing method it was read in, and each clean check says what was actually measured. Two
suspicions raised during this pass died exactly the way the rule predicts, and are recorded in D1
rather than deleted.

**Coverage accounting, so the gap is not mistaken for review:** `RR` read `v2_7_4..5e80c41` hunk by
hunk; `PV` and its validation rounds covered `c8af58e..495030c`; `HS` and its three rounds covered
`8a1b77e..627b4a3`. The remaining commit, `08610a8`, is verified here (D1). The thrice-validated
`PV-B1` rename-ordering chain and the `clearLayouts` deadlock audit were *not* re-verified - three
prior validations each - only spot-checked for presence at HEAD.

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| WR-B1 | Declining the conditional-route warning leaves Execute Timetable disabled for the session - the one early return in the handler with no re-enable | B | **Fixed 2026-07-28** (`6be3bda`). Fix validated |
| WR-C1 | `deletePoint` does not release the home claim on the deleted station - the `locDeleted` twin on the value side of the map | C | **Fixed 2026-07-28** (`6be3bda`). Fix validated; the occupancy-guard observation stays recorded, not fixed |
| WR-C2 | With both delay sliders at 0, a staging run busy-spins a core for the whole of every train's drive - the sequential wait made an old zero-delay spin window minutes long | C | **Fixed 2026-07-28** (`6be3bda`). Fix validated - and see WR-C5 for what the insertion cost |
| WR-C3 | The Danish delete-entry confirmation lost its `{0} ({1})` placeholders - Danish users are not told which timetable entry they are deleting | C | **Fixed 2026-07-28** (`6be3bda`). Fix validated; audit re-run clean |
| WR-C4 | Execute Timetable still force-disables capture for the session, a guard the `HS-B6` fix made redundant - and the staging flow next to it now deliberately preserves the same toggle | C | **Fixed 2026-07-28** (`6be3bda`). Fix validated |
| WR-C5 | The `pacedWait` insertion stranded `executeTimetable`'s javadoc - the exact stacked-javadoc class the previous commit swept tree-wide | C | **Fixed 2026-07-28** (working tree) - `pacedWait` relocated above `executeTimetable`'s javadoc; the check is now part of the routine validator, which caught the next recurrence immediately |
| WR-B2 | The staging flow's planning phase reads as idle to every enablement surface, so Return Home can be re-entered while committed - a double entry can permanently replace the user's timetable with the staging plan | B | **Fixed 2026-07-28** (working tree) - a volatile `stagingFlowActive`, set on the EDT at the commit point and cleared in the worker's `finally`, consulted by the button, both menus and the entry guard |
| WR-C6 | The `requestReturnToHome` entry dialog still speaks the old text for LOCOMOTIVES_RUNNING - the one surface the working-tree consistency change missed | C | **Fixed 2026-07-28** (working tree) - the entry guard routes through `describeStagingOutcome` |
| WR-C7 | Four translations of the new message name a Graceful Stop button that does not exist under that name; the Polish adds a misspelling | C | **Fixed 2026-07-28** (working tree) - the message takes the button label as `{0}`, filled from each bundle's own `ui.main.gracefulStop`, so the two cannot drift; the Polish was reworded to cite the button rather than decline its name |
| WR-C8 | "Use Graceful Stop first" is the wrong instruction when the running state comes from a manually driven path - the control it names is greyed and does nothing for that case | C | **Fixed 2026-07-28** (working tree) - reworded to cover both states: wait for arrival, or use the button if autonomy is running |
| WR-B3 | The three new home-assignment mutation surfaces are gated on `isRunning()` alone - the `stagingFlowActive` window the *previous commit* closed for the run buttons is wide open for edits that rebuild the claim map under the planner | B | **Open** |
| WR-C9 | The assignment chooser offers locomotives the station can never hold, and the resulting permanent IMPOSSIBLE advises checking the track - the wrong remedy for an assignment | C | **Open** |
| WR-D1 | Commit `08610a8` verified hunk by hunk; clean checks, resolved suspicions, and traps recorded | - | Recorded |

No A findings. Nothing in the week's commits was found to have regressed: the one defect above that
is B-rated (`WR-B1`) predates the week, and the week's own changes verified clean everywhere they
were read.

---

## B. Medium

### WR-B1. The "No" that kills the Execute Timetable button

[TrainControlUI.java:12087](../../src/org/traincontrol/gui/TrainControlUI.java) (the disable on
entry), [TrainControlUI.java:12140](../../src/org/traincontrol/gui/TrainControlUI.java) (the return
with no re-enable), against the four early returns above it (12094, 12101, 12111, 12175), which all
re-enable.

`executeTimetableActionPerformed` disables its own button first and re-enables it on every exit -
except one. When conditional routes are enabled and the user answers **No** to the "proceed anyway?"
warning, the handler returns with the button still disabled. Nothing else in the normal UI path
touches it: `refreshReturnHomeButton` manages only the Return Home button, and the worker whose
completion re-enables it (line 12202) was never spawned.

So the user who is told "conditional routes are active and make timetables unpredictable" and
prudently declines is punished for it: Execute Timetable is dead for the session. The recoveries that
exist are accidental - running Return Home (its `finally` re-enables the button at
[TrainControlUI.java:13086](../../src/org/traincontrol/gui/TrainControlUI.java)), reloading the
autonomy graph (line 12895), or restarting.

Reachable by any user with at least one enabled conditional route who presses Execute Timetable once
and answers No - answering No is the *expected* response to that warning. Pre-existing, not a
regression: the disable-on-entry predates the week, and the warning block arrived with the i18n
additions, both before this cycle. B rather than C on the same reasoning the `HS-B5` write-up used
for its stuck buttons: a primary operation becomes unavailable with no stated way back.

Worth noting for the fix: the sibling YES path falls through to a `break` and works; only the NO arm
was missed, and it is the single return in the handler added without the re-enable idiom the other
four follow. One line. The graceful-stop enable at 12212 is correctly skipped by the same return, so
the fix is genuinely just the one line.

---

## C. Low

### WR-C1. Deleting a station does not release its home claim

[Layout.java:1291](../../src/org/traincontrol/automation/Layout.java) (`deletePoint` - removes from
`points`, touches nothing else), [Layout.java:368](../../src/org/traincontrol/automation/Layout.java)
(`locDeleted` - releases the claim, with a comment explaining why claims must not outlive their
locomotive), [Layout.java:388](../../src/org/traincontrol/automation/Layout.java) (`claimHome` - the
injectivity rules), [HomeStaging.java:280](../../src/org/traincontrol/automation/HomeStaging.java)
(where the stale claim surfaces: `canRest` passes on the stale object's own flags, `connected` then
fails).

`homeStations` maps locomotives to points, and the week's fixes wired release into the *key* side:
`locDeleted` removes the claim, and identity hashing keeps renames and address changes from drifting
it (see D1). The value side has no twin. `deletePoint` removes the station from the graph and leaves
every claim on it in place.

What the user then sees, traced through the enforcing methods: the locomotive still counts as
misplaced (`misplaced` compares by name-equality against a point no longer in the graph), so the
Return Home button stays lit and the right-click items stay offered. Pressing them runs the planner:
the pre-search check reads the stale Point's own `isDestination`/`isActive` flags (still true),
`connected` BFSes the *current* graph for a point with the deleted name, finds none, and the outcome
is IMPOSSIBLE - "These locomotives cannot reach their home station at all: L. Check the track between
them and where they started." The station it names as unreachable does not exist, the advice cannot
help, and the state is stable: every subsequent triage re-offers the button and every press re-reports
impossibility.

Self-healing exits, checked rather than assumed: re-creating a station with the same name heals the
claim (Point equality is name-based - `Point.java:289` - so `connected` matches the recreated point);
reloading the autonomy file re-derives every home from current placements (`fromJSON` claims at
placement, `Layout.java:4194`), which silently assigns a *different* home. Deleting the locomotive
releases the claim. C rather than B: it needs a graph edit that deletes a claimed station, moves no
train wrongly, and loses no data.

Two adjacent observations, recorded here rather than filed separately. First, `deletePoint` also has
no occupancy check - a locomotive standing on the point (possible: place one on an edge-less point,
or delete an occupied station's edges first) is silently removed from the graph with it; the
confirmation dialog does not mention it. Pre-existing, same method, worth one guard when the claim
release is added. Second, the fix should release by *name*, not by object equality alone - the claim
map's values are the same objects the points map holds, but going by name matches how every consumer
compares.

### WR-C2. Zero delays turn the sequential wait into a pegged core

[Layout.java:2387](../../src/org/traincontrol/automation/Layout.java) (the new sequential
wait-for-arrival branch), [Layout.java:2494](../../src/org/traincontrol/automation/Layout.java) (the
dispatch loop's pacing: `ttp.getLoc().delay(getMinDelay(), getMaxDelay())`),
[Locomotive.java:904](../../src/org/traincontrol/base/Locomotive.java) (`delay(0,0)` resolves to
`Thread.sleep(0)`), [MarklinControlStation.java:2097](../../src/org/traincontrol/marklin/MarklinControlStation.java)
(`log` deduplicates identical consecutive messages - which is why this spins silently instead of
flooding).

The dispatch loop paces its wait branches with the operator's min/max action delays. Both sliders
allow 0 (the JSlider minimum is the default 0; `setMinDelay`/`setMaxDelay` accept it), and
`delay(0,0)` sleeps for zero milliseconds. That spin loop is old; what the week changed is how long it
runs. Before, the wait branches held only until the previous entry *started* - a short window. The
sequential branch added for staging holds until the previous train *arrives*: minutes, per entry, for
the length of the run.

Each spin iteration formats the wait message (`logf` runs `MessageFormat` before the dedup check) and
calls `Thread.sleep(0)` - so with delays at 0, a staging run pins one core for essentially its entire
duration. The log stays quiet about it: the message has no varying arguments, so the dedup in `log`
suppresses every repeat after the first - which is also why nobody has seen it. On the operator's
likely settings (any nonzero delay) the branch sleeps properly; "no delays, just run it" is the
configuration that hits it, and it is a plausible one for a staging run where realism pacing serves no
purpose.

The retry loop above it already solved this exact problem for itself - `STAGING_RETRY_PAUSE` exists
because "the delay settings may be zero" (the comment at 2441 says so in those words). The same
floor, applied to the sequential wait branch (or to the dispatch loop's pacing when both delays are
zero), finishes the thought.

### WR-C3. The Danish delete confirmation does not say what it deletes

[messages_da.properties:454](../../src/org/traincontrol/resources/messages_da.properties) against
[messages.properties:453](../../src/org/traincontrol/resources/messages.properties) and the call site
[TrainControlUI.java:14450](../../src/org/traincontrol/gui/TrainControlUI.java).

Found by a placeholder audit across all eight bundles (D1): `timetable.ui.confirmRemoveEntryContinue`
is `This will remove entry #{0} ({1}). Continue?` in the base bundle and carries both placeholders in
six translations - but the Danish value is "Dette vil fjerne køreplanselementet. Fortsæt?",
with no placeholders at all. `MessageFormat` simply drops the two arguments, so a Danish user
confirming a timetable-entry deletion is never told which entry - the index and description every
other locale shows. The only such mismatch in 1,215 keys x 8 bundles; one line to fix, mind the
ASCII-escape convention this file follows.

### WR-C4. Two buttons, two capture philosophies

[TrainControlUI.java:12184](../../src/org/traincontrol/gui/TrainControlUI.java) (Execute Timetable:
`setTimetableCapture(false)`, never restored), against
[TrainControlUI.java:13038](../../src/org/traincontrol/gui/TrainControlUI.java) and
[Layout.java:3424](../../src/org/traincontrol/automation/Layout.java) (the staging flow: "Capture is
left exactly as the operator set it").

Execute Timetable force-disables timetable capture before running and never restores it - the
operator's toggle is permanently switched off by the act of running a timetable. That was protective
once: it predates `timetableExecuting`, and before that guard existed, a run with capture on appended
itself to the list being walked (`HS-B6`). The guard now lives where the defect lived, in
`addTimetableEntry` ([Layout.java:243](../../src/org/traincontrol/automation/Layout.java)), covers
both entrances, and was validated in `HS`'s second round - which makes the force-off redundant, and
the redundancy is not free: the same feature's other button deliberately preserves the toggle and says
so in a comment, so the two flows that share the machinery now teach the user opposite rules. An
operator who capture-records between timetable runs loses the toggle each time they execute, for a
reason that no longer exists.

Not filed as B because nothing wrong happens on the layout - the checkbox visibly updates (12185), so
the state is not even misrepresented. The fix is a deletion: the two lines at 12184-12185. The
staging flow's own `timetableCapture.setSelected(...)` sync at 13038 then becomes the only remaining
touch, and it is a no-op by inspection.

---

## Finding added by the fix validation

### WR-C5. The fix's insertion point strands the javadoc it was placed under

[Layout.java:2307](../../src/org/traincontrol/automation/Layout.java) (`executeTimetable`'s javadoc -
"Blocks until every train has arrived", the `@return` contract about abandonment),
[Layout.java:2315](../../src/org/traincontrol/automation/Layout.java) (`pacedWait`'s own javadoc,
stacked directly beneath it), [Layout.java:2350](../../src/org/traincontrol/automation/Layout.java)
(`executeTimetable`, now with no javadoc attached).

The new `pacedWait` method was inserted between `executeTimetable` and its javadoc. The result is two
javadoc blocks stacked back to back: `executeTimetable`'s - which carries the method's blocking
contract and the meaning of its return value, the most load-bearing documentation in the dispatch
machinery - now attaches to nothing, and `executeTimetable` itself is undocumented to any tool that
reads javadoc.

This is precisely the `HS-C4` defect class, and the timing is the recordable part: commit `08610a8`,
*one commit earlier*, swept the entire tree for stacked javadoc and relocated the last three
instances. The very next commit reintroduced the pattern - the cycle's familiar shape of a fix
planting the defect class its own round had just cleaned (`RR`'s five C findings, `PV-C2`,
`HS-B6` all have this shape). The fix is a relocation: move `pacedWait` and its javadoc above
`executeTimetable`'s javadoc block, so each block sits over its own method.

---

## Validation of the fixes (commit `6be3bda`)

Validated by the reviewer who filed the findings, reading each fix in the enforcing method. All five
are correct; the validation added `WR-C5` above.

- **`WR-B1`** - correct. The NO arm re-enables before returning, matching the idiom of the other four
  early returns; the graceful-stop enable at the bottom of the handler is still correctly skipped by
  the same return, so nothing else leaks. All five early returns in the handler now re-enable -
  re-counted, not assumed.
- **`WR-C1`** - correct, and the `removeIf` deserves a word given this repository's history with it:
  the July trap was `removeIf` on a *hash-keyed* collection failing to find a key whose hash had
  drifted. This one runs on the `values()` view of a `LinkedHashMap`, which removes entries through
  the iterator - a linear walk, no hashing anywhere - so the trap does not apply. Comparison is by
  name, matching every consumer of the map; the release sits after the guard exceptions and before
  `points.remove`, so it runs exactly when deletion proceeds; injectivity means at most one entry can
  match, and `removeIf` would be correct even if that changed. The new test pins the release and the
  `ALREADY_HOME` triage that was previously unreachable in this state - its assertions fail against
  the pre-fix behaviour by construction (the claim demonstrably survived; the run confirming
  red-before-green happens in NetBeans, per this project's practice). The occupancy-guard observation
  from the finding - `deletePoint` silently removes a locomotive standing on the point - remains
  recorded and unfixed, now a decision rather than an oversight.
- **`WR-C2`** - correct, and complete against its twins. The floor applies only when both delays are
  zero (`min == 0 && max == 0`), so operators with real pacing keep it; `COMPLETION_POLL` is a
  sensible floor and already the wait loop's own constant. Both spinning sites take it - the
  dispatch loop's pacing and the normal-mode retry - and the third wait, the staging retry, already
  had `STAGING_RETRY_PAUSE`. The remaining six `delay(getMinDelay(), getMaxDelay())` call sites were
  read rather than pattern-matched: all are one-shot pauses (simulation feedback pacing, the
  reversing-station realism pause), not condition-wait loops, so no twin was missed. One inherited
  nit, recorded not filed: `pacedWait` swallows-and-reinterrupts like `Locomotive.delay` always has,
  so a thread interrupted mid-wait spins through immediate `InterruptedException`s until `running`
  clears - unchanged from the old behaviour, and nothing in the codebase interrupts these threads.
- **`WR-C3`** - correct. The Danish value carries `#{0} ({1})` in the same shape as the German, the
  file stays ASCII-pure (checked byte-wise, per this project's properties convention), and the
  eight-bundle placeholder audit re-run reports zero mismatches tree-wide.
- **`WR-C4`** - correct. Both lines are gone, replaced by a comment stating the rule the two buttons
  now share. Checked for anything that depended on the removed force-off: the three tests touching
  capture around execution set the flag themselves at the model layer and assert the `HS-B6` guard,
  not the force-off - no test or caller pinned the old behaviour.

The re-sweep of the fix's own surface produced `WR-C5` and nothing else: no new bundle keys were
needed, the new test is in a registered class, and the handler's control flow around the two removed
lines is otherwise unchanged.

---

## Review of the uncommitted button-consistency changes (2026-07-28, working tree above `6be3bda`)

The working tree carries a change prompted by the author noticing inconsistencies in the Return Home
button: a `disableReturnHome(reason)` helper so the button never greys without saying why, a
dedicated LOCOMOTIVES_RUNNING message that prescribes Graceful Stop, a direct disable in the Start
Autonomy handler (with a comment correctly noting that `refreshReturnHomeButton` would re-enable the
button there, because `isRunning()` is not yet true), a new bundle key in all eight languages, and an
operator-documentation section in `Automation.md`.

**What was verified clean:** the tooltip fix is real - the old running-branch disable left whatever
tooltip was last set, so a button greyed by a run went on claiming everything was home. The new
branch, the helper, and the Start Autonomy direct-disable all do what they say. The key exists in all
eight bundles, ASCII-pure, no placeholders to mismatch. The `Automation.md` claims were each checked
against the enforcing code - the borrow-and-restore, the one-at-a-time execution, waiting for arrival
rather than departure, the `*`/`+` legend, the claim rules including the placed-afterwards case, and
the three programmatic entry points all match. The `describeStagingOutcome` change routes the menus
and the plan-refusal dialog through the same new text, so those three surfaces agree.

The findings below are what the change misses, in descending order of consequence. The pattern of the
first two is the cycle's signature error yet again: the change fixes the inconsistency at the
surfaces it looked at, and the identical inconsistency survives at the entrances it did not.

### WR-B2. Planning reads as idle, so the committed flow can be entered twice

[TrainControlUI.java:13018](../../src/org/traincontrol/gui/TrainControlUI.java) (the disable, with
"the run is committed from this point"), [Layout.java:2379](../../src/org/traincontrol/automation/Layout.java)
(`running` becomes true only here - *after* planning),
[TrainControlUI.java:13120](../../src/org/traincontrol/gui/TrainControlUI.java)
(`refreshReturnHomeButton`: `isRunning()` false -> triage -> misplaced -> **re-enabled**),
[LayoutRightclickAutonomyMenu.java:50](../../src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java)
(the menus compute the same answer at open),
[TrainControlUI.java:12981](../../src/org/traincontrol/gui/TrainControlUI.java) (the entry guard:
also only `isRunning()`).

The working-tree comment in the Start Autonomy handler names the exact mechanism: a refresh
re-enables the button it was meant to grey, because `isRunning()` lags the decision. For Start
Autonomy that lag is milliseconds. For the staging flow it is the **entire planning phase** - seconds
on a large layout, unbounded by anything the operator can see, with no cancel (the recorded `HS`
unknown) - and during it every enablement surface treats the layout as idle:

1. Press Return Home. The button and Execute Timetable grey; the worker starts planning.
2. Move a locomotive by hand (nothing forbids it - nothing is running). The repaint calls
   `refreshReturnHomeButton`, which asks `isRunning()` (false) and the triage (misplaced locomotives
   exist) and **re-enables the button**. No repaint is even needed for the menus: opening either
   right-click menu during planning runs the same computation and offers "Return Locomotives Home"
   enabled.
3. Press it again. The entry guard asks only `isRunning()` - false - so a second worker starts,
   borrows the current timetable, plans, and races the first through `setTimetable`,
   `timetableSequential`, and two concurrent `executeTimetable` dispatch loops over one list.

The interleavings vary; the two worst are concrete. If the second press lands after the first
worker's `setTimetable`, the second worker's "borrowed" copy *is the first worker's staging plan* -
and its `finally` faithfully restores that as though it were the operator's timetable. The user's
actual timetable is gone (permanently, if they save). Either way, two dispatch loops walk the same
entries: the loser's `executePath` validations fail, retry, abandon - `stopLocomotives`, and a run
that may have been proceeding correctly is reported stopped.

B rather than A on the July convention for narrow triggers, but note it clears the A bar's letter
("data silently lost") in its worst interleaving. The fix wants to be a state, not a wider disable:
the flow *knows* it is committed from line 13022 - a volatile `stagingFlowActive` set there (on the
EDT, before the worker spawns) and cleared in the worker's `finally`, consulted as
LOCOMOTIVES_RUNNING by `refreshReturnHomeButton`, both menus, and the entry guard, closes every
surface at once - including the milliseconds-wide Start Autonomy and Execute Timetable variants of
the same lag, which the direct-disable mitigates but does not close (an independently triggered
repaint in the lag window still re-enables).

### WR-C6. The entry dialog still speaks the old text

[TrainControlUI.java:12984](../../src/org/traincontrol/gui/TrainControlUI.java) against
[TrainControlUI.java:13187](../../src/org/traincontrol/gui/TrainControlUI.java).

`describeStagingOutcome` exists, per its own javadoc, "so the same situation cannot be described two
different ways depending on which one noticed it" - and the change routes LOCOMOTIVES_RUNNING
through a new text there. But `requestReturnToHome`'s entry guard shows
`errorWaitForActiveLocomotivesToStop` directly. So a user who clicks the menu item during a run (the
menus can race a run starting - they were computed at open) gets "Please wait for all active
locomotives to stop", while the button tooltip and the plan-refusal dialog for the *same state* say
"Use Graceful Stop first, then return them home". One line: route the guard through
`describeStagingOutcome(LOCOMOTIVES_RUNNING, null)`. (The Execute Timetable guard at 12100 correctly
keeps the old text - it is not about returning home.)

### WR-C7. Four translations name a button that is not on the screen

The new message tells the user which control to press, so its translations must name the control by
the label it actually wears - checked against `ui.main.gracefulStop` in each bundle:

| Locale | The button says | The new message says | |
|---|---|---|---|
| en | Graceful Stop | "Use Graceful Stop first" | match |
| fr | Arrêt en douceur | "Utilisez d'abord l'arrêt en douceur" | match |
| it | Arresto graduale | "Usa prima Arresto graduale" | match |
| de | Geordnetes Stoppen | "Erst sanft anhalten" | descriptive, names nothing - borderline |
| da | **Kontrolleret stop** | "Brug **Blid stop** først" | wrong name |
| es | **Parada gradual** | "Usa **Parada suave** primero" | wrong name |
| nl | **Geleidelijk stoppen** | "Gebruik eerst **Rustig stoppen**" | wrong name |
| pl | **Płynne zatrzymanie** | "użyj **łagodnego zatrzymania**" | wrong name |

A Danish user told to press "Blid stop" will not find it; the button is labelled "Kontrolleret
stop". Same for Spanish, Dutch and Polish. The Polish also misspells the verb: `odeslij` should be
`odeślij` (odeślij) - and as plain ASCII it is the one unescaped-looking word in the line,
which is how it was noticed. The fix is to reuse each bundle's own `ui.main.gracefulStop` value in
the sentence (or reference it), so the two cannot drift again - this table is what drift looks like
on day one.

### WR-C8. The prescribed remedy does not apply to every state that triggers it

[Layout.java:634](../../src/org/traincontrol/automation/Layout.java) (`isRunning`: `running ||
!activeLocomotives.isEmpty()`), the `HomeStaging.Outcome` javadoc ("Something is already moving -
not a conclusion about the layout, just the wrong moment").

LOCOMOTIVES_RUNNING covers two states: an autonomy/timetable run (`running` true - Graceful Stop is
enabled and is the right advice) and a **manually driven path** (`activeLocomotives` non-empty,
`running` false - a double-clicked path from the locomotive panel). In the second state the new
message's instruction is wrong twice: the Graceful Stop button is greyed (nothing enables it outside
the three run flows), and pressing it would do nothing for a manual path anyway - the path completes
on its own and the correct advice is the old text's "wait". So the tooltip names a control the user
cannot press, in exactly the state a user fiddling manually is most likely to see it. Cosmetic-C
since the state resolves itself when the train arrives; the honest fix is either to keep the advice
conditional (running vs merely active), or to word the message so it covers both ("wait for trains
to stop - use Graceful Stop if autonomy is running").

---

## Review of the last-4h commits: `178aa4c` and `d1f7008` (2026-07-28, fourth round)

Two commits. `178aa4c` fixes `WR-B2` and `WR-C6`-`C8` from the third round; `d1f7008` adds assignable
home stations - a new `HomeLocomotiveMenu` on three right-click menus, per-point `home` names in the
autonomy JSON, and thirteen new tests. The author notes the new UI and the JSON home tracking are
**not yet tested by hand**, so this round read those paths hardest.

### Validation of the `178aa4c` fixes

All four correct, each read in the enforcing method:

- **`WR-B2`** - `stagingFlowActive` is volatile, set on the EDT at the commit point (before the
  worker spawns, closing the window from the first instant), and cleared *first* in the worker's
  `finally` - before the buttons re-enable, so no surface can offer the action while the flow
  unwinds. All four surfaces consult it: the button refresh, both menus (via a new accessor), and
  the entry guard. The ordering is the part that had to be checked and it is right.
- **`WR-C6`** - the entry guard routes through `describeStagingOutcome`; one voice per state.
- **`WR-C7`** - solved better than filed: rather than fixing four translations, the message now takes
  the button label as `{0}`, filled from each bundle's own `ui.main.gracefulStop` - the name cannot
  drift again in any language. The superseded key is fully removed (checked in all eight bundles),
  all eight translations of the new key carry `{0}`, and no `I18n.f`-fetched value anywhere contains
  a bare apostrophe (re-swept, since the new French/Italian values are exactly where that
  MessageFormat trap would land - they use typographic apostrophes throughout).
- **`WR-C8`** - the wording covers both states: wait for arrival, or use the button if autonomy runs.
- **`WR-C5`** - `pacedWait` now sits above `executeTimetable`'s javadoc; each block attaches to its
  own method.

### The feature, verified clean where it could be read

The shape is sound: assignments are stored by name on the `Point` (so they survive the locomotive
being off the graph), `rebuildHomeStations` is the single derivation for both entrances (load and
edit), assignments win, the positional rule fills in behind them, and a file with no assignments
reproduces the old behaviour exactly. The three lifecycle repairs the commit message names were each
verified in the enforcing method, and each has a test: `renameLoc` repairs the name (and no
collision is constructible: a live assignment always names a real locomotive, and `renameLoc` refuses
a taken target); `locDeleted` clears the point's name, not just the map; `setHomeLoc` stores names
verbatim. The `deletePoint` release from `WR-C1` composes correctly with assignments - the claim
leaves the map and the point leaves the rebuild's source in the same act. The `fromJSON` ordering is
right: names are read verbatim during the point loop and resolved only once every point exists.
`changeLocAddress` needs no repair (the name does not change) - checked, not assumed. The
JSON round-trip test drives `toJSON` into a real reload and asserts both the stored name and the
derived map; the plan test drives an assigned layout through a full plan to arrival. Bundle audit:
1,227 keys x 8, parity exact, placeholders exact, ASCII-pure.

Two observations recorded, not filed: duplicate assignments (constructible only by hand-editing the
JSON) are warned on every load but never cleared - asymmetric with the dangling-name case, which is
dropped on the same reasoning; and the in-loop `claimHome` during `fromJSON` is now dead work, since
the rebuild at the end clears and re-derives everything - harmless, one loop to delete someday.

### WR-B3. The new mutation surfaces missed the flag the previous commit introduced

[HomeLocomotiveMenu.java:205](../../src/org/traincontrol/gui/HomeLocomotiveMenu.java)
(`refuseWhileRunning` - the enforcement, and it asks only `isRunning()`), lines 95, 117 and 174 (the
three enablement hints, same predicate), against
[TrainControlUI.java:290](../../src/org/traincontrol/gui/TrainControlUI.java) (`stagingFlowActive`,
introduced *one commit earlier* because `isRunning()` reads false for the whole planning phase),
[Layout.java:422](../../src/org/traincontrol/automation/Layout.java) (`rebuildHomeStations`:
`homeStations.clear()` and repopulate, under the Layout monitor),
[HomeStaging.java:137](../../src/org/traincontrol/automation/HomeStaging.java) (the planner's copy:
`new LinkedHashMap<>(layout.getHomeStations())`, under no lock at all).

`178aa4c` established that the staging flow's planning phase is invisible to `isRunning()` and wired
`stagingFlowActive` into every surface that could start a second run. `d1f7008`, the next commit,
added three surfaces that *mutate the claim map the planner is reading* - assign, clear, clear-all -
and gated them on `isRunning()` alone. During planning, the submenu is enabled, the click-time
re-check passes, and `setHomeLocomotive` runs `rebuildHomeStations`: a clear-and-repopulate of
`homeStations` on the EDT while the staging worker's `snapshot` may be iterating that exact map for
its copy, holding no lock. A `ConcurrentModificationException` lands in the worker - which has no
catch, so the run dies with no dialog (the `finally` does restore the timetable and buttons); the
subtler outcome is a torn copy, a plan derived from half the homes, and trains driven to stations
that are no longer theirs. Even with no interleaving at all, an edit that lands between triage and
load changes what the committed run means.

Everything the fix needs already exists: `ui.isStagingFlowActive()` was added for the menus in the
same session. One `|| ui.isStagingFlowActive()` in `refuseWhileRunning` (the enforcement) and the
three hints, and `Automation.md`'s sentence "Assignments cannot be changed while trains are moving"
becomes true for the one window where it currently is not. This is the same-mistake family's next
instance, and its purest form yet: the guard was invented, named, documented and wired into four
surfaces in the morning, and the afternoon's feature added a fifth, sixth and seventh surface
without it.

### WR-C9. The chooser offers what the station can never hold

[HomeLocomotiveMenu.java:225](../../src/org/traincontrol/gui/HomeLocomotiveMenu.java) (the list:
every locomotive in the run list, unfiltered),
[HomeStaging.java:761](../../src/org/traincontrol/automation/HomeStaging.java) (`canRest`: length,
exclusions, reversibility at a terminus, activity),
[HomeStaging.java:280](../../src/org/traincontrol/automation/HomeStaging.java) (the pre-search check
that turns a `canRest` failure into IMPOSSIBLE), the `errorCannotReachHome` message ("Check the
track between them and where they started").

The chooser lists every locomotive in the run list, including ones `canRest` will refuse at this
station forever: a locomotive longer than the station allows, a non-reversible one at a terminus,
one the station explicitly excludes. Assigning any of these produces a *permanent* IMPOSSIBLE on
every Return Home press - correctly detected before any search burn, thanks to `HS-B3` - but the
dialog's advice is "Check the track between them and where they started", which is the wrong remedy:
nothing about the track is at fault, and the way out is the assignment the user just made. Before
this feature, reaching such a state required editing station properties out from under a derived
claim; the chooser now offers it as a menu pick.

Two fix shapes, compatible: screen the chooser with `canRest` (grey the unassignable entries with
the reason as tooltip - the data is one call away), and/or have the IMPOSSIBLE dialog name the
reason as well as the locomotive - which is what the `HS-B3` fix-shape originally asked for
("home station X is inactive" being actionable in a way the current text is not). The second helps
every path into IMPOSSIBLE, not only assignments.

---

## WR-D1. The verified commit, the clean checks, and two suspicions that died correctly

**Commit `08610a8`, hunk by hunk** - the only source commit of the week with no second reader before
this pass. Four files: the three stranded-javadoc relocations the `HS` pre-release addendum called
for (each block now sits over the method it describes - `invalidate`, `saveState` - and the
superseded `MarklinControlStation` block is deleted rather than moved, matching the addendum's
prescription); and two apostrophes in `messages.properties` changed from `'` to `’`. The
apostrophe change looked like a `MessageFormat` quoting fix - in a format string, a bare `'` is the
escape character - but both keys are fetched via `I18n.t`, which returns the raw string
([I18n.java:38](../../src/org/traincontrol/util/I18n.java)), so the change is typographic only.
Suspicion one, resolved by reading the enforcing method.

**The `HS` third-round claims, located.** The round-three write-up (added in `08610a8`) describes
code changes the commit does not contain - the `gracefulStopRequested` arming moved after the reload
confirmation, the `HS-C2` refresh in both terminal repaint paths, the vacuous capture assertion
removed. All three are real and live in `627b4a3`, verified in its diff; the document's round
boundaries and the commit boundaries simply do not line up. Suspicion two, resolved by reading the
diff it actually landed in. (A third, smaller one: a search-tool rendering glitch showed two comment
lines in `MarklinControlStation` beginning with `\` instead of `//`; the file itself is correct.)

**MessageFormat hazards, swept with real data across all eight bundles:**

- No key fetched via `I18n.f` has a bare `'` in any bundle's value (the doubled `''` escape excluded)
  - the hazard the `08610a8` apostrophe change gestured at does not exist anywhere it would matter.
- No key fetched only via `I18n.t` contains `''` (which `t` would render doubled) or a literal `{n}`
  (which `t` would show raw).
- Placeholder sets per key match the base bundle in every translation, with exactly one exception -
  `WR-C3`.
- Key parity independently confirmed: 1,215 keys in each of the eight bundles, no missing, no extra -
  the `HS` addendum's number reproduced from scratch. Every key referenced from `src/` is defined;
  the two apparent misses (`error.invalidLogin`, `log.userLogin`) are `I18n`'s own javadoc usage
  examples, not code.

**The home-claim lifecycle against the cycle's signature error.** The week's most-repeated mistake
was maintaining a keyed structure on one re-keying path and missing its twins, so the new
`homeStations` map was traced across every mutation path rather than assumed: `renameLoc` mutates the
locomotive in place (`l.rename`, [MarklinControlStation.java:2592](../../src/org/traincontrol/marklin/MarklinControlStation.java))
and `changeLocAddress` likewise (`l.setAddress`, line 2438) - both re-key only the name-keyed `locDB`,
and identity `equals`/`hashCode` (confirmed present at HEAD,
[MarklinLocomotive.java:949](../../src/org/traincontrol/marklin/MarklinLocomotive.java)) means the
claim map cannot drift. `locDeleted` releases the claim. `renamePoint` mutates the Point in place, so
claims follow station renames. The one path that maintains nothing is `deletePoint` - filed as
`WR-C1`.

**Capture guard entrances, counted.** `addTimetableEntry` has exactly one production caller
(`executePathInternal`, [Layout.java:2716](../../src/org/traincontrol/automation/Layout.java)) plus
its own overload - grepped, not assumed - so the `timetableExecuting` guard has no uncovered twin.
The wrapper sets and clears the flag in try/finally ([Layout.java:2310](../../src/org/traincontrol/automation/Layout.java)).

**Threading of the triage on its new hot paths.** `triageReturnToHome` snapshots the layout on every
autonomy-list repaint and menu open. Every structural mutation of what it iterates happens on the
EDT: all nine `moveLocomotive` call sites are GUI classes, `locDeleted` is called from the UI, and
`fromJSON` claims only on the fresh Layout it is building - so the EDT-side triage cannot see a
concurrent structural modification. The staging worker's own snapshot has a theoretical window
against an EDT hand-placement, but it is milliseconds wide and ends in the `finally`-protected
recovery below. Not filed.

**The staging flow's exit paths, re-read at HEAD.** The `requestReturnToHome` worker restores the
borrowed timetable and re-enables its buttons in a `finally`, so even the theoretical snapshot
exception above recovers. One asymmetry recorded as a trap, not a defect: on an exception from
`executeTimetable`, `startAutonomy` stays disabled and `gracefulStop` enabled - those two are reset
only in the normal path ([TrainControlUI.java:13056](../../src/org/traincontrol/gui/TrainControlUI.java))
- but no current path in `executeTimetable` throws, and the Execute Timetable handler has the same
shape. It becomes real the day someone adds a throw; the cost of moving the resets into the `finally`
is two lines.

**Dispatch-loop details checked while reading for `WR-C2`:** `secondsToNext` holds milliseconds
everywhere despite its name (capture stores a millisecond difference, the edit dialog multiplies its
seconds by 1000, the display divides - consistent, if unfortunately named); the three wait branches
are all skipped for the first dispatched entry (`i > startIndex`); a graceful stop drains the
remaining entries without dispatching and the completion wait covers whatever is still mid-path; the
staged entries' `executionTime` of 0 means the fresh-run reset is a no-op for staging runs.

**Spot-checks for regressions in the week's fixes, at HEAD:** identity `equals`/`hashCode` present;
both right-click menus short-circuit on `isRunning()` before triage
([GraphRightClickGeneralMenu.java:114](../../src/org/traincontrol/gui/GraphRightClickGeneralMenu.java));
`testHomeStaging` and `testReturnHomeOnRealLayout` registered in `build.xml`; the completion wait
reads `isRunning() && isCurrentLayout()`; the `HS-B4` hardware inference is computed in the
`HomeStaging` constructor from the snapshot, conservative in the mixed case exactly as recorded. The
`AutoLocomotiveStatus` legend code cannot NPE on an unplaced locomotive: the branch that dereferences
the location is guarded by a non-empty path list, and `Point.equals(null)` is false.

**Known-open items, unchanged and deliberately not re-litigated:** the `HS-B3` A* exhaustion-vs-limit
conflation (author-deferred pending a planning-cost measurement), the `HS-B4` mixed-hardware
conservatism (recorded limitation), the duplicated menu blocks (extraction candidate), `FP-B3` and
`FP-C6` (author-deferred). The editor flows and the charset round trip remain untested, as the cycle
summary already records.


## Addressed 2026-07-28 (second round)

`WR-B2`, `WR-C6`, `WR-C7` and `WR-C8` fixed; `WR-C5` was already fixed in the working tree the report
was written against.  Each finding was re-read in its enforcing method before being acted on, and all
four held.

- **WR-B2** - the diagnosis was exact: `isRunning()` cannot express "committed but not yet dispatched",
  and the planning phase lives entirely inside that gap.  A volatile `stagingFlowActive` is set on the
  EDT at the commit point - before the worker spawns, so no window - and cleared in the worker's
  `finally` ahead of the button re-enables.  The button, both right-click menus and the entry guard all
  consult it, which closes the double-entry and with it the interleaving that restored a staging plan
  as though it were the operator's timetable.
- **WR-C6** - the entry guard now routes through `describeStagingOutcome` like every other surface.
- **WR-C7** - the message takes `{0}` and the call site fills it with `I18n.t("ui.main.gracefulStop")`,
  so each locale names the button it actually shows.  Four bundles had invented a different name within
  a day of the string being written; interpolation removes the possibility rather than correcting the
  instances.  Checked afterwards: the key is fetched only via `I18n.f` (it now carries a placeholder),
  is not fetched via `I18n.t` anywhere, and no bundle's value contains a bare apostrophe.
- **WR-C8** - reworded to cover both states `isRunning()` reports.  It now asks the user to wait for
  the trains to arrive, and names the button only as the conditional remedy for an autonomy run - which
  is the only state in which that button is enabled.
- **Polish grammar**, found while verifying the interpolation renders correctly: `użyj` governs the
  genitive, so an interpolated nominative label was ungrammatical.  Reworded to cite the button
  ("press the button X"), the usual way to quote a UI label without declining it.

**Unchanged and still open:** the `HS-B3` A* exhaustion-vs-limit half, the `HS-B4` mixed-hardware
conservatism, the duplicated menu blocks, the `deletePoint` occupancy-guard observation, and the
`startAutonomy`/`gracefulStop` reset asymmetry recorded as a trap in D1.

## Addressed 2026-07-28 (fourth round)

`WR-B3` and `WR-C9` fixed, both re-read in their enforcing methods first, and both held.  One thing
this round did not contain was found while verifying it, and it mattered more than either finding.

- **The tree did not compile, and had not for two commits.**  `178aa4c` calls
  `ui.isStagingFlowActive()` from both autonomy right-click menus, and the accessor was never added:
  `stagingFlowActive` is a private field with no getter, and `isStagingFlowActive` is declared nowhere
  in the codebase.  `d1f7008` was built on top of that.  This report states the fix was wired into
  "both menus (via a new accessor)" - the call sites were read and the accessor inferred from them.
  Nothing in the author's validation compiled Java either, so every check passed on a tree that did
  not build.  The check now exists: methods declared by `TrainControlUI` against every call on a
  `TrainControlUI`-typed variable in the gui package, verified with `git show HEAD` to flag exactly
  the two broken sites and to pass on the fixed tree.
- **WR-B3** - the diagnosis held exactly, including the unlocked read: `getHomeStations` was not
  synchronized and returned `Collections.unmodifiableMap` over the live field, so
  `HomeStaging.snapshot` walked the real map on a worker.  But the finding was narrower than the bug.
  Gating the three assignment surfaces leaves two other writers reaching that map in the same window,
  both predating the feature: `claimHome` via `moveLocomotive` for hand placement, and `deletePoint`
  with its `values().removeIf` - both reachable from the graph menus for the very reason the finding
  names, since those menus gate on `isRunning()` and planning has nothing moving.  Fixed at the
  accessor instead: `getHomeStations` copies under the monitor, and `locDeleted` - the one writer of
  that map without `synchronized`, where `deletePoint` and `moveLocomotive` both have it - now takes
  it, without which the reader's monitor means nothing.  Every reader is safe without each writer
  having to know a reader exists.
- **The predicate, rather than the flag.**  The prescribed fix was one `|| ui.isStagingFlowActive()`
  in four places.  That disjunction had already been hand-rolled in four places before this feature
  added three more surfaces asking half of it, which is the shape the finding itself names.  It is now
  one method - `TrainControlUI.isAutonomyBusy()` - and all seven surfaces ask it.  That also repairs
  the build, since the menus now call something that exists.  `refuseWhileRunning` became
  `refuseWhileBusy`: the old name was the next instance of this same mistake waiting to happen.
- **WR-C9** - held.  `HomeStaging.canBeHome` now exposes the planner's own rest rule, delegating to
  `canRest` rather than restating it, and the chooser says so before the assignment is made, naming the
  locomotive, the station and the four things to check.  Two tests pin it, one asserting that what the
  chooser warns about is exactly what the planner reports as IMPOSSIBLE - the whole reason for
  delegating rather than restating.  It warns rather than refuses, for the reason below.

**Decided, not fixed:** the second `WR-C9` fix shape - naming the reason in the IMPOSSIBLE dialog - is
not being pursued, and the residual path it would close is accepted.  Editing a station's length
limit, terminus flag or exclusions out from under an existing assignment can still produce a permanent
IMPOSSIBLE, and that is allowed to happen: the operator made the state and the operator can audit it,
since the point menu already reports per-path reasons for every route into a station.  The planner
naming the locomotive is enough to start from.

That decision then applied backwards to the fix itself.  The chooser was first written to *refuse* an
impossible pick, which would have blocked one door while leaving the other deliberately open - and
would have told an operator who wanted to assign homes first and configure the stations afterwards
that they were doing it wrong.  It now warns, names the same four things, and defaults to No, but the
assignment is the operator's to make.  What was ever actually wrong was learning about it later from a
dialog that blames the track.  This closes `WR-C9` rather than leaving it half-open.

**Still open:** the `HS-B3` exhaustion-vs-limit half, the `HS-B4` mixed-hardware conservatism, `FP-B3`
and `FP-C6`.  Each is author-deferred pending a measurement or a design decision rather than pending
an edit, so none is being settled here.  The editor flows and the charset round trip remain untested,
as the cycle summary already records.


## Addressed 2026-07-28 (open items)

Everything on the open list that was an edit rather than a decision, closed in one pass.

- **The two observations from the fourth round.**  Duplicate assignments are dropped rather than
  ignored: only a hand-edited file produces two stations naming one locomotive, the loser can never be
  honoured, and keeping it re-warned on every load and was written back out on every save.  The
  warning now says removed, in all eight bundles.  The in-loop `claimHome` during `fromJSON` is gone -
  checked first that the only earlier return from that method is an invalidation path firing before
  any point exists, so nothing reaches the end without the rebuild.
- **The `deletePoint` occupancy observation**, which `WR-C1` said was worth acting on once the claim
  release existed.  It exists, so the confirmation now names the locomotive that will be taken off the
  graph with the station.  Told rather than refused, on the same reasoning that settled `WR-C9`: the
  operator is allowed to do this, and the complaint was never that it happened but that nothing said
  so.
- **The reset asymmetry recorded as a trap in D1.**  `startAutonomy` and `gracefulStop` were reset
  only on the normal path, so an exception out of `executeTimetable` left Start Autonomy dead and
  Graceful Stop live for a run that had ended.  Both now reset in the `finally`, guarded by whether
  this flow was the one that disabled them - so the impossible path, which never touches them, still
  does not.
- **The duplicated menu blocks**, named as an extraction candidate since the first round.  Return Home
  is now `HomeLocomotiveMenu.addReturnHomeItem`, called from both menus.  That block is precisely
  where the drift this report chased twice occurred: a flag added to the button reached one surface
  and not the others.

Two notes on the checking rather than the code.  The validator gained an unused-import check, which
the extraction immediately needed - and its first version silently reported every import in
`TrainControlUI` as unused, having been mangled by shell quoting on the way into the file; it was
rewritten and then verified on a synthetic file, flagging an import mentioned only in a comment and a
string literal while leaving a used one alone.  Separately, a suspicion that scripted edits had
flipped eight files from LF to CRLF died on inspection: `core.autocrlf` is `true` and every blob is
stored LF, so the working tree is doing exactly what it should.

`testHomeStaging` 33 -> 36.  Committed as `f14bddf`, `4f9eb09` and `e5b252b`; none of it has been
compiled or run, and `f14bddf` is the first of the three that can be.
