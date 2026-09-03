# Validating the 2026-09-01 fan-out's fixes, withdrawal and deferrals

**Status:** open

**Prefix for citing this document elsewhere:** `FV2`

**Reviewed:** branch `autonomy-diagram-r0` at `f82a693f`, on 2026-09-01. The working tree is clean apart
from `cs2_sample_layout/config/autonomy/configuration-Main.json` and `setup.json`, which are `FX2-1` and
have not been touched. Scope is the five commits `a33b9ae1`, `9f1b80c8`, `828b1ff1`, `e9435bfc`,
`f82a693f`, the index at `docs/reviews/2026-09-01-fanout-index.md`, and the six reports it indexes.

**Method: reading, plus four read-only shell probes.** No build, no test run, no application. The probes
were `ps`, `kill -0`, `/proc/<pid>/winpid` and `Get-Process`, run to settle one factual question about
pid namespaces (`FV2-A1`); none of them touches the repository, the layout folder, or a JVM. Everything
else in this document is read off the source, the two JSON files in `cs2_sample_layout/`, and the crash
dumps already in the repository root. Where a claim needs execution I say so and leave it as an open
question.

---

## Verdict per fix

| Fix | Where | Fixes its finding? | Broke anything? | Test real? |
|---|---|---|---|---|
| `SVN-A2` (a) probe failure ≠ "none" | `a33b9ae1` | Yes, for a blank answer | No | No test exists |
| `SVN-A2` (b) blind to `ant`/NetBeans | `a33b9ae1` | **Yes** — verified against `nbproject/project.properties` | No | No test exists |
| `SVN-A2` the lock's liveness test | `a33b9ae1` | **No — it is now strictly worse than what it replaced** (`FV2-A1`) | Yes: it clears a live lock | Branch was exercised with a value the script never writes |
| `D24-B1` `connected`'s start state | `9f1b80c8` | Half of it. The terminus limb of the same finding is left, and it is the reachable half on the derived graph (`FV2-B2`) | No — single caller, and the looser answer is safe there (`FV2-D1`) | Yes — mutation (a) really flips it |
| `TCX-A3` vacuous string assertion | `9f1b80c8` | Yes | No | Yes — mutation (b) really flips it |
| `CMT-B1`, `CMT-B2` | `9f1b80c8` | Yes | No | Comments only |
| `CMT-B3` | `9f1b80c8` | Yes | No | Prose only |
| `CMT-B4` | `9f1b80c8` | Yes, and **introduces two new false claims in the same paragraphs** (`FV2-B3`) | — | Prose only |
| `OB-167` follow-up (cross vs station) | `e9435bfc` | Yes — real production change | No | Yes |
| The cross's colour | `828b1ff1` | **The clause cannot fire in production** (`FV2-C1`) | No | The mutation flips it, but over a `Badge` state neither production site builds |
| The cross's weight | `828b1ff1` | Yes | No | Yes — mutation (d) really flips it |
| The placeholder outline | `828b1ff1` | Yes | No — `testThePlaceholderLocomotive` measures filled shapes with generous tolerances (`FV2-D3`) | Not pinned by any assertion |
| The sandbox on the new class | `828b1ff1` | Yes — the same `open()`/`close()` pattern 29 other classes use | No | The two guards that caught it are the test |

---

## A — wrong behaviour on the layout, or data silently lost

| | Finding | Status |
|---|---|---|
| **FV2-A1** | `battery.sh`'s corrected lock asks Windows about an MSYS pid, so it reads every live battery as stale | open |

### FV2-A1 — the lock's new liveness test can only answer "dead", because the pid in the lock is not a Windows pid

**This is the one finding in this document I would stop the release for.** The lock is, by the commit's
own argument, the only thing covering the compile window in which the two concurrent batteries of
2026-09-01 overlapped. `a33b9ae1` replaced its liveness test with one that cannot work.

`docs/tools/battery.sh:181` writes the lock:

```sh
echo $$ > "$LOCK"
```

`docs/tools/battery.sh:150-154` reads it back and asks Windows about it:

```sh
    HELD=$(cat "$LOCK" 2>/dev/null | tr -d '\r\n ')

    ALIVE=$(powershell.exe -NoProfile -Command \
        "if (Get-Process -Id $HELD -ErrorAction SilentlyContinue) { 'yes' } else { 'no' }" \
```

`$$` in Git Bash is the **MSYS** pid, not the Windows pid. MSYS keeps its own pid space; that is why
`ps` in this shell prints `PID` and `WINPID` as separate columns. Measured in one shell:

```
msys pid=40422  winpid=31184
Get-Process -Id 40422 -> no
Get-Process -Id 31184 -> yes: bash
```

So `ALIVE` comes back `no` for a battery that is running, `STALE` is set, the script prints
*"(clearing a stale lock from pid N, which is no longer running)"* and starts a second run — with the
first still in `javac`, which is exactly the window the lock exists to cover.

**The repository contains its own proof.** The run ids in the crash dumps left by the 2026-09-01
incident are `battery-32945`, `battery-35302` and `battery-40080` (`hs_err_pid*.log`). Windows process
ids are always multiples of four; 32945 and 35302 are not. Those numbers are MSYS pids, and they are
what `echo $$` puts in the lock.

**The premise the change rests on is also wrong.** The comment at `docs/tools/battery.sh:138-140` says:

> `kill -0` was the wrong test. MSYS resolves pids within its own process tree, so a lock written by a
> shell in another session reads as dead…

MSYS pids are global to the MSYS runtime, not per process tree. From this shell, `kill -0` succeeded
against a bash from a different session started two days earlier, and against another agent's live
shell. `kill -0` was answering the question correctly; `Get-Process -Id $$` is not.

**Why the commit's testing did not catch it.** The message says *"a lock holding a live cross-session
pid refuses, a lock holding a dead one prints 'clearing a stale lock' and proceeds"*. Both branches
answer correctly when the lock holds a **Windows** pid, which is what a hand-written test value is and
what `echo $$` never produces. This is the project's own rule about testing the rule and not the call
site, arriving one more time.

**The two directions it fails in.** Usually `no` — a live battery reads as stale and a second one
starts. Occasionally `yes`, when Windows happens to have a process with that number: then the lock
cannot be cleared by finishing the run, and the first thing anybody does is delete the lock file, which
is the "guard that has to be cleared by hand" the file's own comments warn against twice.

**The remedy is one line and is available here.** MSYS exposes the Windows pid: `cat /proc/$$/winpid`
returns it (verified: msys 42343 → winpid 17328). Write that to the lock, or keep `kill -0` as the
first test and use Windows only as a second opinion. Either way `RUN_ID="battery-$$"` and
`BUILD="$S/build/battery-$$"` are fine as they stand — they use `$$` as a label matched as a string by
`tools/reap.ps1`, not as a number handed to Windows, and that is the distinction the change missed.

**Not verified by me:** that the repaired script runs. It needs a run, and I have not made one.

---

## B — incorrect results, or crashes in specific configurations

| | Finding | Status |
|---|---|---|
| **FV2-B1** | "The guard is inert on his railway" is the reason `FX2-3` was deferred, and it is not established — the two squares that carry both a length and a reversal flag are the two the guard governs | open |
| **FV2-B2** | `D24-B1` is fixed for `isReversing` and left for `isTerminus`, which is the half that is reachable on the diagram-derived graph | open |
| **FV2-B3** | The `CMT-B4` fix puts two rules into `AutomationAPI.md` that no door enforces | open |

### FV2-B1 — the reversal-room guard is not inert, and the deferral's stated reason is the thing that is wrong

`FX2-3` and the comment now standing at `Layout.java:2337-2360` both justify leaving the rule alone on
the ground that it cannot fire:

> Left as it is on purpose. It is inert on his railway today (six tiles carry lengths at all)…

Six tiles is right. Inert does not follow, and the arithmetic is a few lines of JSON away.

`cs2_sample_layout/config/autonomy/setup.json` records exactly six lengths:

```
"tileLengths": {"5:20,13": 4, "5:0,11": 4, "5:20,14": 2, "5:1,10": 4, "5:14,3": 3, "5:5,4": 3}
```

An edge's length is not the track strictly between two sensors — `GraphReducer.java:945-946` builds it
as `sumLength(path) + lengthOf(tile)`, **including the tile the edge lands on**, and
`GraphReducer.java:1036-1046` says why. So every edge that ends on one of those six squares has a
positive length and is read as "measured" by `Layout.java:2365-2374`.

Two of the six are exactly the squares the guard is about. From
`cs2_sample_layout/config/autonomy/configuration-Main.json`:

```
'1 - Main:20,14' {"loc": {...}, "maxTrainLength": 0, "facing": "W", "priority": 2, "home": "EN57-947", "canReverse": true}
'1 - Main:20,13' {"maxTrainLength": 0, "facing": "E", "canReverse": true}
```

`canReverse` is `AutonomyBuilder.CAN_REVERSE`, and `AutonomyBuilder.java:970` emits the turning copy as
`terminus` or `reversing` — which is `Layout.java:2335`'s trigger, `ending.isTerminus() ||
ending.isReversing()`.

So a **single-edge** approach to either square is a fully measured path, and the guard runs with
`room` = 2 or 4. Whether it then refuses depends only on `loc.getTrainLength()`, which lives in the
preferences store and is not in the repository — but the train lengths this suite uses are 1, 4, 8 and
10 (`testNonReversibleTrains.java:281-351`), the same order of magnitude as the tile lengths Adam typed.

`1 - Main:20,13` is `BottomMainB`, which is `FX2-4`'s destination-with-no-way-out. The one berth the
round has been chasing all day is also one of the six measured squares.

**What is wrong here is the deferral's reasoning, not the decision.** Deferring the two unsoundnesses is
right — both change what the railway does. But the round told Adam the guard cannot fire while it
decides what to do, and that is not established. If a locomotive in his database is longer than two
units, `isPathClear` is refusing a hand-dispatched move into `1 - Main:20,14` today, at every door,
with `autolayout.errorTrainTooLongToReverse` — and problem 1 in the comment (a partial sum reading as
measured) is precisely the mechanism that arms it.

**Open question for the orchestrator, needs execution:** print `getTrainLength()` for every locomotive
in Adam's database. If any exceeds 4, `FX2-3` stops being a deferred design question and becomes a live
refusal on the main page.

**Is the comment at the guard accurate about the two problems it admits to?** Its two numbered
paragraphs are, and I checked both rather than reading them:

- **Problem 1 is exactly right.** `GraphReducer.java:1052-1062` is
  `total += Math.max(0, authored.getTileLength(step.getTile()))`, and `:1047-1049` is the same clamp for
  the landing tile, so one measured tile out of five does yield a positive length and
  `segment.getLength() <= 0` does not mean "unmeasured". The comment names the method correctly.
- **Problem 2's arithmetic is right too.** The loop adds every edge of `path`, so a 10 + 1 + 2 run-in
  gives `room = 13` and admits an eight-unit train into the three units that lie beyond the reversal.

Two things it does not say. First, `FX2-3` states a **third** limb that the comment drops: *"a path that
reverses mid-way but ends at an ordinary station gets no check at all"* — true, because the trigger at
`:2335` is `ending.isTerminus() || ending.isReversing()` and says nothing about intermediate reversing
points, which `executePathInternal` also stops and reverses at. A reader who trusts the comment to be
the whole of what is unsound will miss it. Second, the closing paragraph's "inert on his railway today"
is the part that is wrong, and it is the sentence a future reader is most likely to act on.

### FV2-B2 — `D24-B1` had two limbs; the fix took the one that is unreachable on the graph Adam actually runs

`9f1b80c8` aligned `HomeStaging.connected` with `firstClearRoute` on `from.isReversing()`
(`HomeStaging.java:1699`). That is correct and I have checked it end to end (`FV2-D1`).

`D24-B1` also said, in its own words:

> There is a second, smaller disagreement inside the same expression: the runtime flips direction on
> arrival at a **terminus or** a reversing point (`Layout.java:5575`), but `firstClearRoute` only asks
> `from.isReversing()`, never `from.isTerminus()`.

That limb is untouched, and the index lists `D24-B1` as fixed without qualifying it.

The runtime is unambiguous (`Layout.java:5598-5604`):

```java
        // Reverse at terminus station
        if (path.get(path.size() - 1).getEnd().isTerminus() || path.get(path.size() - 1).getEnd().isReversing())
        {
            ...
            loc.delay(...).switchDirection().delay(1000);
```

A train standing on a terminus has already been turned round, by exactly the same mechanism and in the
same statement as one standing on a reversing point. The argument written into `HomeStaging.java:1687`
— *"a train already standing on a reversing point sets off turned"* — applies to a terminus word for
word.

**And this limb is the reachable one.** `D24-B1` says so about the limb that was fixed: on the derived
graph a reversing Point is not a destination, so `plan()`'s own `!locationOf(...).isDestination()`
clause at `HomeStaging.java:443-444` catches the locomotive first and `connected` is never consulted.
A terminus copy **is** a destination — `AutonomyBuilder.java:966-970` emits it as one — and parking
berths are termini. So on Adam's own railway:

- non-reversible train parked at a terminus berth,
- homed at another terminus,
- with no reversing point on the route between them,

is proved IMPOSSIBLE by `connected` and would have been routed by `firstClearRoute`, which starts the
same journey with `turned` false as well. Both searches are wrong together here, so the two agree and
`auditAgainstRuntime` cannot see it either.

**Fix, if Adam wants it:** `boolean startsTurned = from.isReversing() || from.isTerminus();` in
`connected`, and the same in `firstClearRoute`'s seed at `HomeStaging.java:948-949`. Red first: the
existing `testATrainStandingOnAReversingPointHasAlreadyTurned` fixture with `setTerminus(true)` on
`RH pad` instead of `setReversing(true)`.

**Minimum, if he does not:** the index should not carry `D24-B1` as fixed without saying which half.

### FV2-B3 — the fix for `CMT-B4` writes two rules into `AutomationAPI.md` that nothing enforces

`CMT-B4` was "both user documents still say only a reversible locomotive can reach a terminus". The
replacement paragraph is accurate in its first sentence and then over-claims twice:

> **Full autonomy only chooses a terminus for a reversible locomotive. You can still send a
> non-reversible one there yourself, and Return Home will still bring one home to one** … Such a train
> has to arrive already turned, so the route to the terminus must pass a reversing point on the way; **if
> the graph offers no such route, the destination is simply not offered.** … **If any track lengths are
> set,** a train longer than the room leading up to the reversal is refused as well…

**(a) The destination is offered.** The turning requirement is `HomeStaging.mustBackIn` — homing only.
The manual door is `getPossiblePaths` + `isPathClear`, and `isPathClear` has had no terminus rule since
Adam's ruling recorded at `Layout.java:2280-2298`, in the same file:

> NO TERMINUS RULE HERE (Adam, 2026-09-01). … The terminus clause is on that footing now - pickPath
> will not CHOOSE a terminus for a locomotive that cannot get out of one, and **nothing refuses the
> operator who asks for it.**

The only other place the clause lives is `barredFromAutonomy` (`Layout.java:4025`), which is autonomy's
choosing and the "why is it not moving" window — not the right-click menu. So a user reading this
document believes the application will keep a non-reversible train out of a terminus it cannot leave,
and it will not. That is the reverse of the tier doctrine, written into the document that explains the
tiers.

**(b) "If any track lengths are set" is not the trigger.** The guard needs **every** segment of the path
measured (`Layout.java:2363-2374`, the `measured` flag and its `break`), which is the whole of `FX2-3`'s
second half and of `MT-248`'s step 3. The sentence was added in the same commit that added the KNOWN
UNSOUND comment eighty lines away.

Filed at B because that is where `CMT` lettered the same class of defect in the same two documents; if
`CMT-B3`/`CMT-B4` were B, a new false claim introduced while fixing them is not less.

---

## C — low

| | Finding | Status |
|---|---|---|
| **FV2-C1** | The cross's colour clause cannot fire in production; both `Badge` sites make `shut` imply `parking` | open |
| **FV2-C2** | The index says the six measured tiles are "all on the Test page". They are all on `1 - Main` | open |
| **FV2-C3** | `MT-248`'s step 3 predicts an outcome the single-edge case contradicts | open |
| **FV2-C4** | The withdrawal of `TCX-B7` states a reason the finding refutes in its own text | open |
| **FV2-C5** | `battery.sh` still classifies the probe's answer by its first character | open |
| **FV2-C6** | `HomeStaging.connected(Point, Point)` has had no caller since 2026-08-31 | open |
| **FV2-C7** | `Badge.isImpassable()` is now identical to `Badge.isShut()`, which nothing calls | open |
| **FV2-C8** | Three of the four deferrals record nothing at the code; only `FX2-3` does | open |
| **FV2-C9** | Two deferrals have an unambiguous half that prejudges nothing and was not done | open |
| **FV2-C10** | "Each code fix was seen failing first and mutation-confirmed" is not true of the `battery.sh` fix | open |

### FV2-C1 — the colour half of `828b1ff1` is unreachable

**RECORDED, no change** (2026-09-03).  Confirmed: both Badge doors compute `parking` as `inactive || !isAutoDestination`, so `shut` implies `parking` and the new disjunct cannot change a production answer.  The clause is kept because it says what it means rather than leaning on `parking` to cover it, and `SVN-C11` carries the same observation about the two names for one field.  Nothing to fix; what was wrong is a commit message, and that is now written here.

`TileAnnotation.java:1528`:

```java
        Color colour = badge.isParking() || badge.isImpassable() ? POINT_INACTIVE : POINT_ACTIVE;
```

`isImpassable()` is now `return shut;`. There are exactly two places in `src/` that build a `Badge`, and
both compute the two flags from the same expression:

`AutonomySession.java:4360` and `:4365`

```java
                Boolean.FALSE.equals(getPointProperty(tile, "active")) || !isAutoDestination(tile),   // parking
                ...
                Boolean.FALSE.equals(getPointProperty(tile, "active")))                               // shut
```

`AutonomyEditorPanel.java:5955-5961` is the same pair. So `shut` implies `parking` at both sites, and
`isParking() || isImpassable()` is `isParking()`. The symptom the commit describes —

> the same switched-off square drew blue or orange depending on a setting that means nothing while it is
> switched off

— cannot occur on either diagram. A switched-off square was already orange.

The new test is honest about needing a `Badge` that the existing `badgeAt` helper cannot make, and says
so; what it does not say is that production cannot make one either. So mutation (c) does flip the
assertion, and the assertion is about a state that only the test constructs. The change is harmless and
arguably tidier — the clause now says what it means rather than relying on `parking` to cover it — but
it fixed nothing visible, and the commit message and the test javadoc both describe it as fixing
something Adam was looking at.

The weight change beside it is real, and so is the `e9435bfc` rule it depends on: `shut && !station`
→ `shut` genuinely changes which squares draw a cross, and `worthABadge` at `AutonomySession.java:4342`
lets a switched-off station through.

### FV2-C2 — the six measured tiles are on the main page, not the Test page

**FIXED.**  `MT-248` carries the correction and names the trap - `setup.json` maps page id 5 to `1 - Main` - so Adam is sent to the page his trains run on.  Verified 2026-09-03.

`docs/reviews/2026-09-01-fanout-index.md:154`:

> on your railway only **6 tiles carry a length**, all on the Test page

and `docs/manual-tests/tests.md:13305` repeats it. All six keys are on page `5`, and
`setup.json`'s own page table says what that is:

```
"pages": {"1": "2 - Bottom", "2": "3 - Top Parking", "3": "4 - Combined", "4": "5 - Test", "5": "1 - Main"}
```

Page id `5` is **`1 - Main`**; `5 - Test` is page id `4`. This is the `setup.json`-is-keyed-by-page-id
trap. `MT-248` sends Adam to the wrong page to look for the lengths he set, and the mistake is load
bearing for `FV2-B1`: they are on the page his trains actually run on.

### FV2-C3 — `MT-248` step 3 tells Adam what he will see, and it may be the opposite

**FIXED 2026-09-03.**  Step 3 gives both outcomes and says which shape produces which: more than one edge in and the guard is not armed, one edge in and it very likely refuses, because that edge's length includes the square just measured.  Which he sees tells me the shape of his berths, so it is asked as a question rather than predicted.

> **Set the length on one square the notice names.** The notice should go.
> **Now try to back a long train into that berth. It will not be refused, and that is the defect.**

For a run-in of more than one edge, right. For a **single** edge ending on the square he has just
measured, the whole path is measured (that edge's length includes the end tile — `GraphReducer.java:946`)
and the guard runs, with `room` equal to the one number he typed. He will very likely be refused, and
the test's conclusion — "following the notice as written does not arm the guard" — is then false in the
case in front of him.

### FV2-C4 — the withdrawal of `TCX-B7` misstates the finding

**FIXED 2026-09-03.**  The withdrawal row now says what `TCX-B7` actually claimed - a vacuous assertion left standing beside the armed one - and records that the withdrawal misstated it, rather than being quietly amended.  A withdrawal is the most calibration-relevant entry in a review.

The index:

> `TCX-B7` … but a prior review found exactly this (`TST-A4`) and added
> `testPathValidationCanActuallyFireOutsideSimulateMode` 200 lines below in the same file … **The
> compensation was missed.**

It was not missed. `TCX-B7`'s own third paragraph reads:

> The file knows: `testPathValidationCanActuallyFireOutsideSimulateMode` (`:450`) is the armed
> replacement and its comment says why. The soak's headline assertion was left in place beside it, so
> the class still reads as covering the mechanism twice when it covers it once.

Both citations check out: `testAutonomySimulationSanity.java:205` and `:257` carry the assertion,
`:450` is the armed test, `:424-431` is the comment. The finding's claim is that a vacuous assertion was
left standing beside the armed one, not that the mechanism is uncovered. That is a C at most — but a
withdrawal is the most calibration-relevant entry in a review, and this one records the reviewer making
a mistake he did not make.

### FV2-C5 — `battery.sh`'s numeric arm is chosen by the first character

**FIXED 2026-09-03** - see the note under `SV2-C2`'s neighbours; the arms are `''|*[!0-9]*)` to warn and `*)` to compare, so a malformed probe answer warns instead of reading as "none running".

`docs/tools/battery.sh:101-103`:

```sh
case "$RUNNING_JVMS" in
    [0-9]*)
        if [ "$RUNNING_JVMS" -gt 0 ] 2>/dev/null
```

`12abc` matches `[0-9]*`, `[ -gt ]` then fails, its diagnostic goes to `/dev/null`, and the script
proceeds without printing the warning the same commit added for exactly this case. The half of `SVN-A2`
about "a probe that failed reads as a probe that said none" is closed for an empty answer and still open
for a malformed one. Narrow — PowerShell writes errors to stderr, which is already discarded — but the
arms should be `''|*[!0-9]*)` warn, `*)` compare.

### FV2-C6 — `connected(Point, Point)` is dead

**FIXED 2026-09-03.**  The two-argument `connected` is gone, with a note where it stood saying why a convenience overload that defaults `mustReverse` is worth removing: the whole of `SV2-A1` was about which value that argument should take.

`HomeStaging.java:1651-1654`. Its last caller went when `canGetHome` moved to the three-argument form on
2026-08-31 (the comment at `:436` records the change). Nothing in `src/` or `test/` calls it, by name or
by reflection. Worth saying because the briefing's question was whether the looser start state is safe
at both overloads: it is safe at the two-argument one for the trivial reason that nothing reaches it —
and, had anything reached it, `mustReverse` is false there, so `startsTurned` only shrinks the `seen`
key space and cannot change the answer.

### FV2-C7 — `isImpassable()` and `isShut()` are now the same method

**RECORDED, no change** (2026-09-03).  `isImpassable()` and `isShut()` are one field with two javadocs, and `isShut()` has no caller.  Merging them is a change to the class that draws every mark on every tile, which is `SVN-C9`'s neighbourhood and is deferred past 3.0.0 with it.

`TileAnnotation.java:268` and `:291`. `isShut()` has no caller anywhere. Two identical predicates on one
class is an invitation for a later change to land on one of them; either delete `isShut` or make
`isImpassable` delegate to it so there is one place to edit.

### FV2-C8 — only one of the four code-level deferrals says so at the code

**FIXED 2026-09-03** for the three that were missing it: the reasoning for each deferral is now at the code as well as in the review - `FX2-2` at both refusal sites, `FX2-5` at `Route.locomotiveDeleted`, and `FX2-6` beside the line that skips `edges`.  That is the README's rule, and `R28-B1` is what it costs when it is not followed.

`FX2-3` got the treatment the README asks for: *"leave the reasoning where the next person will trip
over it - in the code, not only in the review"*, at `Layout.java:2337-2360`. The other three did not:

- `FX2-2` — `TrainControlUI.java:16099-16104` and `LayoutLabel.java:537-540` both still just `return`
  on `RouteConflict.REFUSED`, with no note that the s88 door and `MarklinRoute` do the opposite and that
  the difference is open.
- `FX2-5` — `Route.locomotiveDeleted` carries its original javadoc argument and nothing about the
  delete-then-re-add case `R28-A1` raised.
- `FX2-6` — `AutonomySession.java:822-824` skips `edges` in one line. The comment above it explains at
  length why the lengths are left behind and does not mention `commands` at all, which is precisely how
  `R28-B1` came to be found rather than known.

### FV2-C9 — two deferrals have a half that could not have prejudged Adam's ruling

**OPEN - Adam's, and narrowed** (2026-09-03).  Both halves are still his to rule on, and this finding's point stands: the SILENCE is separable from the behaviour in each case, and a log line is behaviour-neutral whichever way he rules.  Carried in MT-260's tail with the other rulings; `R28-A1`'s wider option ("say how many route commands will be removed") is restored to the question.

The briefing asked whether any deferral had an unambiguous part. Two do, and both are the same part:
**the silence, not the behaviour.**

- `FX2-5`. `MarklinControlStation.java:2986-2996` already logs
  `route.warnConditionNamesDeletedLocomotive` when a *condition* still names the deleted locomotive. The
  *command* removal, which is the authored data that goes, logs nothing. A second log line is
  behaviour-neutral: whichever way Adam rules, a user is better off being told which routes were edited.
  Note also that the index states the question more narrowly than `R28-A1` did — `R28-A1` offered "say
  how many route commands will be removed" **or** revert; `FX2-5` offers only "remove silently" or
  "revert", which drops the option that needs no ruling at all.
- `FX2-6`, first limb. The import's own comment two lines below the skip already concedes the shape:
  *"NOT REPORTED YET, and that is a gap worth naming rather than papering over: the import dialog counts
  what it matched and what it skipped, and says nothing about these."* Counting the legacy file's
  `commands` blocks and saying so in the same dialog prejudges nothing about whether `protectingSignal`
  is meant to replace them.

`FX2-1`, `FX2-2` and `FX2-4` are, in my reading, correctly and wholly deferred. `FX2-1` is his data and
a blind revert would destroy his own work; `FX2-2` is a safety behaviour with two defensible readings
and the doors genuinely have to agree afterwards; `FX2-4` is a question about which layer enforces a
rule he has already stated. `FX2-3` is correctly deferred as to the **rule** and incorrectly justified
as to its reach — see `FV2-B1`.

One thing `FX2-1` could have carried and did not: `SVN-A1` observed that `battery.sh` takes
`live_before=$(fingerprint)` at the **start** of a run, so a folder already damaged before the run passes
every subsequent battery silently. That is a harness change, not a railway change, and it is why the
damage sat unnoticed for two days.

### FV2-C10 — "mutation-confirmed" is claimed over a fix that has no test

**FIXED.**  The index says the two JAVA fixes were seen failing first and mutation-confirmed, and the shell fix is listed as exercised rather than covered.  Verified 2026-09-03.

The index:

> Each code fix was seen failing first and mutation-confirmed.

`SVN-A2`'s fix is in the Fixed table and is a shell script with no test at all; `a33b9ae1` says its three
branches were "exercised", which is not the same claim and, per `FV2-A1`, was done with a value the
script never writes. The two Java fixes below it are genuinely mutation-confirmed and I have checked
both. The sentence should exclude the shell fix rather than cover it.

---

## D — not defects

| | Finding | Status |
|---|---|---|
| **FV2-D1** | The looser `connected` is safe at every path that reaches it | closed — checked clean |
| **FV2-D2** | All four claimed mutations really would flip their assertions | closed — checked clean |
| **FV2-D3** | The placeholder stroke change does not disturb `testThePlaceholderLocomotive` | closed — checked clean |
| **FV2-D4** | `SVN-A2`'s "blind to `ant`/NetBeans" half is genuinely fixed | closed — checked clean |
| **FV2-D5** | `CMT-B1`, `CMT-B2` and the ledger arithmetic are right | closed — checked clean |

### FV2-D1 — `connected`'s new start state, at every caller

The briefing asked for this specifically, so here is the whole path.

`connected(Point, Point, boolean)` has three call sites, all inside `canGetHome`
(`HomeStaging.java:1562`, `:1573`, `:1577`). `canGetHome` has exactly one caller,
`HomeStaging.java:445`, inside `plan()`'s `unreachable` scan. The two-argument overload has none
(`FV2-C6`). **`auditAgainstRuntime` does not reach it**: it calls `firstClearRoute`
(`HomeStaging.java:629`) and compares against `getPossiblePaths`, and `connected` appears nowhere in it.

So the entire effect of the change is on `plan()`'s IMPOSSIBLE shortcut, and it is in the safe
direction:

- `connected` is deliberately a **relaxation** of `firstClearRoute` — blind to occupancy, to
  `canEnter`, and to accessory-command conflicts. Before the fix it was not a relaxation in the one case
  `mustBackIn` is true, because `firstClearRoute` could start turned and `connected` could not. That is
  what made a false proof possible. It is a relaxation again now.
- A looser `connected` can only turn `IMPOSSIBLE` into `READY` or `NO_PLAN_FOUND`. It cannot produce a
  move: the moves come from `search()` → `firstClearRoute`, which applies the same seed
  (`HomeStaging.java:948-949`) and every state-aware rule besides. So no plan the runtime would refuse
  can be created by this change.
- The rest of the loop still agrees. `now = reversed || next.isReversing()` (`:1716`) matches
  `turned = current.turned || next.isReversing()` (`:961`); the arrival test
  `(!mustReverse || now)` (`:1720`) matches `if (!mustBackIn(loc, to) || turned)` (`:1016`); neither
  expands through a terminus.
- `plan()`'s other two clauses at `:443-444` (`isActive`, `isDestination`) are unchanged, so nothing
  that used to be caught by those is let through.

The residual disagreement is `isTerminus`, which is `FV2-B2` and is a disagreement with the **runtime**,
not between the two searches.

### FV2-D2 — the four mutations

**(a) `turned.add(false)` back in `connected` fails `testATrainStandingOnAReversingPointHasAlreadyTurned`.**
Yes. The fixture is `RH pad` (station, `setReversing(true)`) → `RH berth` (station, `setTerminus(true)`),
one edge, locomotive `setReversible(false)`, so `mustBackIn` is true. With `turned` seeded false,
`now = false || berth.isReversing()` is false, the arrival test `(!mustReverse || now)` fails, the berth
is a terminus so it is never expanded, the queue empties and `connected` returns false → `unreachable`
→ `IMPOSSIBLE` → `assertNotEquals(outcome, "IMPOSSIBLE")` fails. The stale `seen` entry left at
`"…/true"` by the partial mutation changes nothing, because nothing can reach that state.
The fixture also clears `plan()`'s `isDestination` guard, which is what makes it the case
`D24-B1` said is only reachable on a legacy graph — `createPoint(name, true, s88)` is the legacy shape.

**(b) replacing the clearing loop's argument with `0` fails `testTrainTailClearsEdges`.** Yes, and it is
byte-exact. `Layout.java:5403-5404` is

```
                            if (!tailHasProvablyPassed(pathIsUnmeasured, waiting[1],
                                loc.getTrainLength()))
```

with 32 spaces of indent, and the assertion's literal carries 32 spaces. `Layout.java` is LF in the
working tree, so the embedded `\n` matches. The needle disappears under the mutation. The old assertion
really was vacuous: `loc.getTrainLength()` appears three times in the file now.
*Caveat, not a finding:* the assertion is now sensitive to reformatting, which is a false red rather
than a false green, and the comment above it says as much.

**(c) dropping `isImpassable` from the badge colour fails `testASwitchedOffSquareTakesTheColourAutonomySkips`.**
Yes. `plainBadge(false, true)` is `parking = false, shut = true`, so the mutation moves it from
`POINT_INACTIVE` to `POINT_ACTIVE` and `assertEquals(shut, parking)` fails. The helper had to exist:
`badgeAt` passes `shut` for **both** the `parking` and the `shut` parameters
(`testAutonomyDiagramMonitor.java:1204-1205`), so a fixture built with it would have been orange under
either rule. The test is well built. See `FV2-C1` for what it is a test of.

**(d) restoring the flat `2f` fails `testTheCrossKeepsItsWeightAsTheTileGrows`.** Yes. `mark` is
`Math.max(9, Math.min(width, height) / 2)`, so 20 at size 40 and 40 at size 80; the stroke is
`Math.max(2f, mark / 7f)`, so 2.86 and 5.71. Arms and thickness both double, so the ink goes up about
four-fold against a threshold of three; with a flat `2f` only the arms grow and it roughly doubles.
Counting the antialiased fringe (`inkOf` takes alpha > 8) narrows the real ratio somewhat because the
fringe scales with perimeter rather than area, but not below the threshold on my arithmetic. The
discrimination is real; the margin is not enormous, and the commit's measured 1.98 for the mutant is
consistent with it.

### FV2-D3 — the placeholder outline

`h / 24f` → `h / 32f` is 2-3 pixels at the sizes `testThePlaceholderLocomotive` renders (120-240). Every
assertion in that class measures either filled geometry or a symmetric difference:
`LocomotivePlaceholder.java:206-208` draws each wheel `fillOval` then `drawOval`, so the four-run count
at `:57` cannot change; `firstGap`/`lastGap` (`:70`) and the taper (`:169-173`) are differences in which
the stroke cancels; the roof-to-body ratio (`:125`) has a `body / 6` tolerance. Nothing reads the stroke
width. I have not run it, but I can find no assertion it can move.

### FV2-D4 — the `ant`/NetBeans half of `SVN-A2`

The new filter matches `*testng*` (PowerShell `-like` is case-insensitive, so the second `*TestNG*` term
is redundant, not wrong). `build.xml:118+` runs each class through the TestNG Ant task, which forks a
JVM per class; that JVM's command line carries both `org.testng.TestNG` and
`resources_test/testng-6.14.3.jar` from `javac.test.classpath` (`nbproject/project.properties:56-62`).
A NetBeans single-test run goes through `-do-test-run-single` and the same macro. So `ant test` and the
IDE are now visible.

Adam's own application still is not, which is what the comment claims: `run.classpath`
(`project.properties:100-101`) is `javac.classpath` plus `build.classes.dir`, and `javac.classpath` is
FlatLaf, the JSON jar and AbsoluteLayout — no TestNG. The `java_command` line in the crash dumps
(`org.testng.TestNG -testclass ui.testRenderingCost …`) confirms the shape for battery JVMs.

One consequence worth knowing rather than filing: `docs/tools/parity/run.sh` launches JVMs carrying
`-Dtraincontrol.anyReceivePort=true` against a *separate* checkout, and a battery started while it runs
will now refuse. That is over-strict rather than unsafe, and the message tells the reader what to do.

### FV2-D5 — the comment fixes and the ledger

`canRest`'s javadoc (`HomeStaging.java:1616-1622`) now describes what the body does, and the body is
`isDestination && isActive && !excluded && validateTrainLength` — no reversibility.
`refreshAllProtectingSignals`'s new header (`Layout.java:6098-6110`) is accurate: the method is public,
has no production caller, and the surviving callers are tests.

The `AutomationAPI.md` corrections I could check against code hold: `Set Home Locomotive` is the real
label (`autosetup.ui.menuHomeFor`), and `Layout.clearHomeLocomotives` exists and is public
(`Layout.java:1245`). The two over-claims in the same rewrite are `FV2-B3`.

The ledger arithmetic in `docs/manual-tests/tests.md` is right: 250 entries, 231 `fixed validated`
(199 + 32 with trailing whitespace), 7 `superseded`, 3 `fixed unvalidated`, 9 `needs test`.
`ParkingTrack6` is `2 - Bottom:17,6` and does carry `"active": false`, so `FX2-4`'s explanation of why
`testTheParkingBerthsGetTheirTrainsBack` will not go green on a backing-in fix alone is correct.

---

## Open questions that need execution

1. **`FV2-A1`.** The repaired `battery.sh` needs one run each way: a lock holding the winpid of a live
   shell must refuse; the same after that shell exits must clear.
2. **`FV2-B1`.** Print `getTrainLength()` for every locomotive in Adam's database. Anything above 4 makes
   the reversal-room guard live on `1 - Main` today.
3. **`FV2-B2`.** `testATrainStandingOnAReversingPointHasAlreadyTurned` with `setTerminus(true)` on
   `RH pad` instead of `setReversing(true)` should currently fail with `IMPOSSIBLE`.
4. Whether `testAutonomyDiagramMonitor` and `testReturnHomeSequencesAReversal` are green as committed. I
   have read them and can see nothing wrong; I have not run anything.
