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
| WR-C5 | The `pacedWait` insertion stranded `executeTimetable`'s javadoc - the exact stacked-javadoc class the previous commit swept tree-wide | C | **Open** |
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
