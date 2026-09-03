# Second validation: the corrections made in answer to `FV2`

**Status:** open

**Prefix for citing this document elsewhere:** `SV2`

**Reviewed:** branch `autonomy-diagram-r0` at `d15a7951`, on 2026-09-01. Scope is the three commits
`f59fa45e`, `c9153aaf` and `d15a7951` — the corrections made after the first validation pass
(`docs/reviews/2026-09-01-fix-validation.md`, prefix `FV2`) — plus the index they update. The working
tree is clean apart from `cs2_sample_layout/config/autonomy/configuration-Main.json` and `setup.json`,
which are `FX2-1` and were not touched.

**Method: reading, plus read-only process and data probes.** No build, no test run, no application, no
`battery.sh`. The probes were `$$`, `/proc/<pid>/winpid`, `kill -0`, `Get-Process`, one
`Get-CimInstance Win32_Process` count, `ps`/`ps -W`, and `python3` reads of the two JSON files in
`cs2_sample_layout/` and of the `hs_err_pid*.log` dumps in the repository root. Nothing was written
anywhere except this file. Where a claim needs execution I say so and leave it in the open-questions
list.

**The two findings I am most confident of are `SV2-A1` and `SV2-A2`,** and both are cases of the
pattern the briefing named: a correction verified against something other than the thing it changes.

---

## Verdict per correction

| Correction | Where | Fixes its finding? | Broke anything? | Test real? |
|---|---|---|---|---|
| `FV2-A1` — the lock holds `/proc/$$/winpid` | **`c9153aaf`**, not `f59fa45e` (`SV2-C1`) | Yes for the value it writes; the winpid is genuinely the lock holder's (`SV2-D1`) | No | No test; and the lock it repairs is not the lock the actual incident needed (`SV2-A2`) |
| `FV2-A1` — `kill -0` as a second opinion | `c9153aaf` | Only for the fallback and for pre-`c9153aaf` locks; it cannot resolve the value the script now writes (`SV2-C2`) | No | Not exercised |
| `FV2-C5` — the numeric arm tests the whole answer | `f59fa45e` | **Yes**, all six inputs route correctly and the warning is still reachable (`SV2-D2`) | No — dropping `2>/dev/null` is safe now | Shell, no test |
| `FV2-B2` — `connected` seeds from `isTerminus` too | `f59fa45e` | Yes, and it is safe: it can only weaken a proof (`SV2-D3`) | No | Yes — the new test really does flip on this half |
| `FV2-B2` — `firstClearRoute` seeds from `isTerminus` too | `f59fa45e` | **No. This half is wrong and it drives trains** (`SV2-A1`) | Yes: Return Home will now take a non-reversible train nose-first into a terminus | **No — the new test passes with this half reverted** (`SV2-B1`) |
| `FV2-C1` — the comment at the colour clause | `f59fa45e` | **Yes**, and its claim is exactly right at both `Badge` sites (`SV2-D4`) | No | The existing test still pins the clause |
| `FV2-C7` — `isShut()` deleted | `f59fa45e` | **Yes**, no caller anywhere in `src/` or `test/` (`SV2-D4`) | No | n/a |
| `FV2-C10` — the mutation-confirmation sentence | `f59fa45e` | Yes, but it now sits under a table with three Java fixes, not two (`SV2-C5`) | No | n/a |
| `FV2-B3` — the `AutomationAPI.md` rewrite | `c9153aaf` | Yes when written; the third bullet was made false 24 minutes later by `f59fa45e` (`SV2-B2`) | — | Prose |
| `FV2-B1`/`FV2-C2` — the guard is not inert | `c9153aaf` | **Yes**, every number in it checks out against the two JSON files (`SV2-D5`) | No | Prose |
| `d15a7951` — the commit references | `d15a7951` | No — `FV2-A1`'s row cites a commit that does not contain the fix (`SV2-C1`) | — | n/a |

---

## A — wrong behaviour on the layout, or data silently lost

| | Finding | Status |
|---|---|---|
| **SV2-A1** | `firstClearRoute`'s new terminus seed makes the back-in rule vacuous for any train standing in a berth, so Return Home will drive a non-reversible train nose-first into its home terminus | open — the remedy needs Adam's ruling, stated below in one sentence |
| **SV2-A2** | `battery.sh`'s lock lives inside `TC_SCRATCH`, which is per-session; the two batteries that actually overlapped had two different lock files, so no version of the liveness test would have stopped them | open |

### SV2-A1 — the terminus half of `FV2-B2` counts the arrival reversal twice, and it is the half that emits moves

`f59fa45e` changed two seeds. `HomeStaging.java:1719`, in `connected`:

```java
boolean startsTurned = from.isReversing() || from.isTerminus();
```

and `HomeStaging.java:954-956`, in `firstClearRoute`:

```java
        queue.add(new Candidate(from, new LinkedList<Edge>(),
            new HashMap<String, Accessory.accessorySetting>(),
            from.isReversing() || from.isTerminus()));
```

The argument for both, in the commit message and in the comment now standing at `HomeStaging.java:1706-1712`, is a symmetry: *"`executePath` turns the train on arrival at EITHER … one statement, one
switchDirection. So 'a train already standing on a reversing point sets off turned' is true of a
terminus word for word."* The statement about `switchDirection` is true. The symmetry it is used to
prove is not, and the asymmetry is the whole of what `turned` is for.

**What `turned` means.** It is not "has `switchDirection` been called"; it is "is the locomotive
trailing rather than leading". `mustBackIn`'s own javadoc says so (`HomeStaging.java:1535-1537`):

> a terminus is a place a train can only leave by reversing, so one that cannot reverse must arrive
> already turned - it backs in past a reversing point and **leaves forwards**.

and `testNonReversibleTrains.java:196-199` states the consequence outright:

> A train that passes a reversing point on the way arrives at the terminus already turned - it backs in
> - and leaves forwards, **so it never runs backwards out of anywhere** and the objection does not apply.

**Why the two squares are not the same.** Take the flips one at a time, with the locomotive at one end
of its train:

- **Standing on a reversing point.** It arrived leading (ordinary running) and `executePath` flipped it
  (`Layout.java:5599-5605` at the end of a path, `Layout.java:5320-5331` at an intermediate one). It is
  now trailing. It sets off trailing, and if it now backs into a terminus it arrives correctly.
  `from.isReversing()` ⇒ `turned` is **right**.
- **Standing on a terminus.** Under the rule this code is enforcing, it arrived *trailing* — that is
  what backing in is — and the same flip turned it back to leading, which is exactly what lets it leave
  the berth forwards. It sets off **leading**. `from.isTerminus()` ⇒ `turned` is **wrong**.

The flip at a terminus is the one the arrival test at `HomeStaging.java:1023` has already spent:
`if (!mustBackIn(loc, to) || turned) return route;` accepts the arrival *because* the flip that follows
will leave the train able to depart. Using that same flip again as the next journey's starting state
counts it twice. This is the same class as `D24-B4` ("`turned` is a boolean where the runtime flips
direction once per reversing point"), still open and undispositioned, but now reachable at the seed as
well as along the route.

**The consequence, and it is not a refusal.** `isPathClear` has had no terminus rule since Adam's
ruling at `Layout.java:2280-2297`, so nothing downstream objects: `loadReturnToHomeTimetable`
(`Layout.java:6537-6566`) loads the plan into the timetable and it is driven. The train arrives
nose-first at its home berth and can then only leave by running backwards — the outcome the whole rule
exists to prevent, produced silently by the feature that is supposed to enforce it.

**Reachable on Adam's own railway, on his current configuration.**

- All **12** squares carrying `"parking": true` in `configuration-Main.json` also carry
  `"mustReverse": true`, and all 12 are in `setup.json`'s `stations` list. `AutonomyBuilder.java:826`
  is `boolean stops = point.isStation() && arrivalAllowed(node);` and `:970` is
  `json.put(stops ? "terminus" : "reversing", true)` — so every one of his parking berths emits its
  arrival copy as a **terminus**. A parked train is standing on a terminus, which is the origin of
  essentially every Return Home he will run.
- `Point`'s javadoc at `:189-192` says the parking flag keeps autonomy out and that "Routes the user
  picks, and **Return Home**, are unaffected", and `firstClearRoute`'s two origin guards are
  `isActive()` and `isDestination()` (`:928`, `:940`) — both true of a berth. Nothing else screens it
  out.
- `1 - Main:20,14` (`BottomMainC`, a station with `canReverse`) is `EN57-947`'s home, i.e. a terminus,
  and `testNonReversibleTrains.java:201-202` records that `EN57-947` is one of the two non-reversible
  locomotives on his layout.

**It also contradicts the manual test written for this rule.** `docs/manual-tests/tests.md:13235-13238`
(`MT-246`, disposition `needs test`):

> 6. Drive it away and press Return Home. It should go, and it should arrive having been turned round on
>    the way, at the reversing point - **backing in, not nose-first.**
> 7. **A terminus with no reversing point on the way to it** should now be reported as impossible for
>    that locomotive, rather than offered and then failing on the first move.

Step 7 is precisely the behaviour `f59fa45e` removes whenever the train starts in a berth, and step 6
is precisely the outcome it now fails to produce.

**The two halves are not the same change and must not be reverted together.**

- `connected` may be loose. It only ever weakens a proof: a looser `connected` turns `IMPOSSIBLE` into
  `NO_PLAN_FOUND` or `READY`, never into a move (`SV2-D3`). Keeping it there also keeps the invariant
  the fix's own comment states — `connected` must never call impossible a journey `firstClearRoute`
  would take. That invariant requires `connected` ⊇ `firstClearRoute`, not equality, so the loose
  `connected` and a strict `firstClearRoute` are consistent.
- `firstClearRoute` must not be. It is the method whose output is driven, and its own comment at
  `:1018-1020` says why: *"Refusing here rather than accepting and letting the runtime refuse is the
  difference between a plan that works and one that fails on its first move."* Here the runtime will
  not refuse at all, so refusing here is the only protection there is.

**Remedy:** drop `|| from.isTerminus()` from `HomeStaging.java:956` and leave `:1719` as it is. The new
test still passes with exactly that arrangement — which is `SV2-B1`, and is why the change was not
caught.

**The one sentence for Adam:** *when a non-reversible train is standing in a parking berth, should the
planner assume it backed in (so it must be turned again on the way to the next terminus — today's
documentation, today's manual test, and the behaviour before `f59fa45e`), or assume it was driven in
nose-first (so it is already turned — the behaviour now)?* The model records nothing about which,
which is why this cannot be settled from the code; the conservative answer is the first, because
guessing wrong that way costs a `NO_PLAN_FOUND` and guessing wrong the other way strands a train.

### SV2-A2 — the lock cannot see across sessions, because it lives in `TC_SCRATCH`

`docs/tools/battery.sh:65`:

```sh
LOCK="$S/battery.lock"
```

`$S` is `TC_SCRATCH` (`:23`), supplied by whoever starts the run. In this environment that is a
per-agent-session scratch directory, so **two agents running batteries hold two different lock files
and never see each other at all** — the liveness test is not reached, because the file is not there.

**This is not a hypothetical; it is what happened, and the repository holds the proof.** Every
`hs_err_pid*.log` in the repository root carries the `-d` argument battery.sh builds from `$S`
(`:338`), and `-Dtraincontrol.batteryRun` built from `$$` (`:263`):

| run | `TC_SCRATCH` implied by `-d` | dumps | window (file mtimes) |
|---|---|---|---|
| `battery-35302` | `…/claude/…/0362837d-8bf0-41e4-b752-1b0f380eca86/scratchpad/tc` | 4 | 20:22:25 – 20:24:00 |
| `battery-40080` | `…/claude/…/0362837d-8bf0-41e4-b752-1b0f380eca86/scratchpad/tc` | 12 | 20:25:40 – 20:27:41 |
| `battery-32945` | `…/claude/…/51b92044-34bd-4b4c-875d-48bf7a99935f/scratchpad` | 1 | 20:25:42 |

`battery-32945` crashed **two seconds after** `battery-40080`'s first crash and inside its window, from
a different session's scratch directory. Two batteries, two `$S`, two `battery.lock` paths, no
interaction possible between them.

Two corollaries:

1. **The `FV2-A1` thread is about a lock that could not have prevented the event it is written up as
   preventing.** The index at `:39-49` says the two mechanical facts behind the concurrency "are now
   fixed"; the mechanism the dumps show is a third one, untouched by `a33b9ae1`, `c9153aaf` or
   `f59fa45e`. The liveness fix is still worth having — a second battery from the *same* session is a
   real case, and it is the 2026-08-29 case the file's own comment records — but it is not this one.
2. **The compile window is completely unguarded across sessions.** The comment at `:133-139` is right
   that the lock is "the only thing that covers that window", and the lock is per-session; the JVM
   probe, which *is* machine-wide, matches `Name='java.exe'` only (`:97`) and a `javac` compile runs as
   `javac.exe`. So during the minute-plus compile of a cross-session battery there is nothing at all.

**Remedy, in two lines and independent of each other:** put the lock at a session-independent path
(anything derived from the repository or a fixed name under the user's temp directory, rather than
`$S`), and add `javac.exe` to the probe's process filter so the compile phase is visible to the one
check that is machine-wide. The second also covers `ant`, a hand-run `javac`, and `one.sh`, none of
which take the lock at all.

*(Side note, not a finding: `FV2-A1` cites these dumps as "left by the 2026-09-01 incident". Their
mtimes are 2026-08-29 20:22–20:36. The pid-namespace conclusion he drew from them is independently
confirmed — see `SV2-D1` — so nothing rests on the date.)*

---

## B — incorrect results, or crashes in specific configurations

| | Finding | Status |
|---|---|---|
| **SV2-B1** | The new test cannot fail for the `firstClearRoute` half of the fix it was written for; the assertion that would catch it is in the sibling test twenty lines above | open |
| **SV2-B2** | `AutomationAPI.md`'s new "Returning home" bullet, written to fix `FV2-B3`, was made false 24 minutes later by `f59fa45e` | open |

### SV2-B1 — `testATrainStandingOnATerminusHasAlreadyTurnedToo` passes with half the fix reverted

`test/core/testReturnHomeSequencesAReversal.java:405-443`. The fixture is the shape it claims: two
stations on separate sensors, both `setTerminus(true)`, one edge between them, nothing reversing
anywhere, a locomotive forced non-reversible, placed on `RH berth A` with the placement asserted, and
homed at `RH berth B`. `LayoutSandbox` is open for the class (`:59-64`). Traced against the code, it is
genuinely red before the fix: `plan()`'s `unreachable` scan reaches `canGetHome` → `connected(A, B,
true)`, the single edge cannot set `now`, the arrival test at `:1740` fails, `B` is a terminus so it is
never expanded, and the outcome is `IMPOSSIBLE`. So the `connected` half really is mutation-confirmed,
as the javadoc's MUTATION line says.

**What it does not cover is the other seed.** Its only assertion is

```java
            assertNotEquals(String.valueOf(plan.getOutcome()), "IMPOSSIBLE", …
```

With `connected` fixed and `firstClearRoute` reverted, `plan()` clears the `unreachable` scan, `search()`
finds no route (the greedy pass and `astar` both go through `firstClearRoute`), and the outcome is
`NO_PLAN_FOUND` — which is not `"IMPOSSIBLE"`, so **the test is green**. The commit message's claim
*"Mutation-confirmed - dropping the terminus half fails it"* is true of one of the two places the
terminus half was added and false of the other, and the one it misses is the one that produces moves.

The sibling test twenty lines above carries exactly the assertion that would have closed this
(`:378-380`):

```java
            assertTrue(plan.isPossible(),
                "and it should have a plan: one move, off the pad and into the berth.  Outcome "
                + plan.getOutcome());
```

Its absence in the new test is the whole gap. This is the third appearance in this round of the shape
`FV2-C10` named — a confirmation claim stated more broadly than the thing that was confirmed — and the
second time in two commits that a branch was verified with something the real caller does not produce.

**If `SV2-A1` is accepted**, the right test is the opposite one: assert that the outcome is *not*
`READY` (or that `plan.getMoves()` is empty) for this fixture, with `assertNotEquals(…, "IMPOSSIBLE")`
kept beside it — the two together are exactly the "loose proof, strict search" contract.

### SV2-B2 — the user documentation now describes protection that `f59fa45e` removed

`AutomationAPI.md:409`, added by `c9153aaf` at 04:57:

> **Returning home:** a non-reversible locomotive may be homed at a terminus, and **the planner insists
> the way there turns it round first** - so it backs in and can leave forwards. Where no such route
> exists, homing reports that locomotive as unable to reach its home rather than setting off and
> failing.

`f59fa45e`, at 05:21, made both sentences false for any train standing on a terminus — which, per
`SV2-A1`, is where a parked train stands. The planner no longer insists; and where no turning route
exists it now sets off rather than reporting.

Filed at B for the same reason `FV2-B3` was: this is the document that explains the tiers, and a rule
stated there that nothing enforces is worse than a stale one. Note that the correction and the
regression are 24 minutes apart in the same working session — the doc was accurate when written.

**If `SV2-A1` is fixed by reverting the `firstClearRoute` seed, this finding disappears with it** and
the paragraph needs no change. If Adam rules the other way, this paragraph and `MT-246` steps 6–7 both
have to be rewritten.

*(Cosmetic, in the same edit, not filed separately: the new "Separately, a train can be refused…"
line at `:411` is immediately followed by "Terminus stations must have a separate set of directed
outgoing edges…" with no blank line, so the two render as one paragraph. The same join existed before
the edit.)*

---

## C — low

| | Finding | Status |
|---|---|---|
| **SV2-C1** | The index cites `f59fa45e` for `FV2-A1`; the fix is in `c9153aaf` | open |
| **SV2-C2** | The `kill -0` "second opinion" cannot resolve the value the script now writes, and the fallback writes a pid with no namespace tag | open |
| **SV2-C3** | The lock is check-then-write, not created atomically | open |
| **SV2-C4** | Nine `FV2` findings the index lists as fixed still read `open` in their own status tables | open — systematic across the round, not new to these commits |
| **SV2-C5** | "The two Java fixes were seen failing first and mutation-confirmed" now sits under a table with three | open |
| **SV2-C6** | "`ps -W` in this Git Bash cannot see `java.exe` at all" is contradicted by the sentence after it and by measurement | open |
| **SV2-C7** | `battery.sh` points at `docs/manual-tests/README.md` for how `TC_SCRATCH` is built; that file never mentions it | open |

### SV2-C1 — `FV2-A1`'s row points at the wrong commit

**FIXED 2026-09-03.**  The row names `c9153aaf`, which is where the winpid write, the fallback and the second opinion are, and says what `f59fa45e` actually carries.

`docs/reviews/2026-09-01-fanout-index.md:74` says the fix is `f59fa45e`. `git show f59fa45e --
docs/tools/battery.sh` contains **only** the `FV2-C5` case-arm swap. The winpid write, the fallback and
the `kill -0` second opinion are all in `c9153aaf`. `d15a7951` is where the reference was filled in, and
it replaced `_pending_` with the later of the two commits for `FV2-A1` and `FV2-B2` together — correct
for `FV2-B2`, wrong for `FV2-A1`.

It matters because the README's reason for identifiers is that they are cited from commits: anybody who
runs the command in that row to see the repair finds a two-line `case` change and concludes the repair
never landed. It has already propagated once — the brief for this pass carries the same attribution.

### SV2-C2 — the second opinion asks the wrong pid space, and the fallback writes an untagged number

**FIXED 2026-09-03.**  The fallback writes `msys:NNN`, and the reader splits on the prefix: a winpid goes to `Get-Process`, an MSYS pid to `kill -0` only, and an MSYS lock this shell cannot resolve is UNKNOWN rather than dead - which warns and proceeds instead of clearing a live battery's lock.  The paragraph claiming the two tests were redundant is replaced by what they actually are: one can only ever ADD a yes.

The comment at `docs/tools/battery.sh:161-164` claims a redundancy:

> liveness is "either test says alive": Get-Process for the Windows number, and `kill -0` as well, which
> does work across MSYS sessions … Two tests that can only ever say "still running" cannot combine into
> a false "stale".

Measured here, in a live Git Bash: `$$` = 50523, `/proc/$$/winpid` = 6512, `kill -0 50523` succeeds,
**`kill -0 6512` fails**, `Get-Process -Id 50523` answers no, `Get-Process -Id 6512` answers yes. So in
the normal path — the one where the lock holds a winpid, which is now every lock this script writes —
`kill -0 "$HELD"` is a query in the MSYS namespace about a Windows number. It cannot confirm the actual
holder; the only thing it can do is coincidentally match an unrelated MSYS process and produce a false
"alive". Liveness therefore rests entirely on `Get-Process`, exactly as it did before the line was
added. The inline comment at `:181` is accurate about this ("for a lock written before this change or
by a shell with no /proc"); the paragraph above it is not.

The residual danger is in the fallback (`:167-169`):

```sh
case "$LOCK_PID" in
    ''|*[!0-9]*) LOCK_PID=$$ ;;
esac
```

That writes an MSYS pid, and a reader cannot tell which space a bare integer is in. `Get-Process` is
*guaranteed* to answer "no" about it (measured above), so such a lock is resolvable only by `kill -0`,
i.e. only by a reader in the same MSYS runtime and with permission to signal the process. Where it is
not — a different MSYS installation, WSL, or an `EPERM` — both readers answer no, `STALE` is set, and
a live lock is cleared. That is the `FV2-A1` failure mode surviving in the one branch nobody exercised.
Narrow, because `/proc/<pid>/winpid` exists in Git Bash, but it is narrow in the same way `FV2-A1` was.

**Remedy:** write the namespace with the number — e.g. `win:6512` or both values on one line — and test
each with the reader that can answer it. Then no answer is ever "no" for the wrong reason.

### SV2-C3 — the lock is not created atomically

**FIXED 2026-09-03.**  The lock is created with `( set -o noclobber; : > "$LOCK" )`, so which of two racing shells gets it is a question the filesystem answers rather than one this script answers twice.  A stale lock is removed immediately before the attempt, by the run that established its holder is gone.  Proven in isolation: first takes it, second refused, and after removal a third takes it.

`:173` tests `[ -f "$LOCK" ]` and `:213` writes it; when no lock exists the two are microseconds apart,
but both are preceded by a `powershell.exe` start-up of a few hundred milliseconds, so two batteries
launched together spend that time in parallel and can arrive at the test within the same instant. Both
find no file, both write, both proceed. `set -o noclobber` with `( : > "$LOCK" )`, or `mkdir
"$LOCK.d"`, makes creation atomic and is one line either way. Filed at C rather than higher because it
needs sub-second simultaneity — but note it is the same failure mode as `SV2-A2` and would be closed by
the same edit if the lock also moves to a fixed path.

### SV2-C4 — the dispositions live in two places and disagree

**FIXED 2026-09-03**, by the sweep this finding asked for: every C in the September rounds now carries its verdict in one place, and the four findings whose table said `open` over prose saying `FIXED` were corrected.

The index's Fixed table lists `FV2-A1`, `FV2-B1`, `FV2-B2`, `FV2-B3`, `FV2-C1`, `FV2-C2`, `FV2-C5`,
`FV2-C7` and `FV2-C10` as fixed. All nine still read `open` in their own status tables in
`2026-09-01-fix-validation.md`, which is where the README puts a finding's disposition ("One status, one
location"). The same is true of `D24-B1` and the other findings fixed earlier in the round, so this is
the round's habit rather than something these three commits introduced — but a reader who opens `FV2`
alone sees fourteen open findings, of which nine are done.

### SV2-C5 — the corrected mutation sentence undercounts again

**FIXED 2026-09-03.**  The sentence names `D24-B1` and `TCX-A3` rather than counting them - the count went stale twice - and says what `FV2-B2` and `FV2-C1` are.

`docs/reviews/2026-09-01-fanout-index.md:83`: *"The two Java fixes were seen failing first and
mutation-confirmed"*. When `FV2-C10` corrected that sentence there were two; `f59fa45e` then added a
third Java fix to the same table (`FV2-B2`, `HomeStaging`) and a fourth Java change beside it
(`FV2-C1`, comment only). Per `SV2-B1` the third is mutation-confirmed for one of its two halves. The
sentence should name the fixes it covers rather than count them.

### SV2-C6 — the `ps -W` claim is not supported, including by its own paragraph

**REFUTED 2026-09-03, with the measurement this finding said was missing.**  Taken during a live battery: `ps -W` listed the operator's own `java.exe` and the battery's own MSYS-launched test JVM at the same moment.  So `ps -W` sees both, the index's claim was false, and the index now says so and names the measurement.  This finding was right and is closed as CONFIRMED, not withdrawn.

`docs/reviews/2026-09-01-fanout-index.md:50`: *"`ps -W` in this Git Bash cannot see `java.exe` at all.
It reported zero while two batteries ran."* Measured here, `ps -W` enumerates non-MSYS Windows
processes perfectly well, including Java-family executables — `C:\Program Files (x86)\Common
Files\Java\Java Update\jusched.exe` and `jucheck.exe` are both listed, with pids `winpid + 65536`. And
the index's own next paragraph gives the alternative explanation: *"both were in `javac`, so neither
could see the other by its processes"* — during a compile the process is `javac.exe`, so a zero for
`java.exe` was the right answer, not a blind one. Nothing depends on this today (the probe uses WMI),
but it is a stated fact about the tooling that the next harness change would reason from. **Not
settled**: refuting it properly needs a `ps -W` taken while a test JVM is running, which I have not
done.

### SV2-C7 — the pointer to how `TC_SCRATCH` is built goes nowhere

**FIXED 2026-09-03.**  `docs/manual-tests/README.md` has the half-page: what `TC_SCRATCH` holds, that the battery lock lives inside it and why that is load-bearing, and that neither runner may be used while the other is - which is the rule the 2026-08-30 incident cost.

`docs/tools/battery.sh:18-19`: *"`TC_SCRATCH` holds `cp.txt` (the classpath, one line) and receives the
TestNG output. See `docs/manual-tests/README.md` for how it is built."* `docs/manual-tests/README.md`
contains no occurrence of `TC_SCRATCH`, `cp.txt`, `scratch` or `classpath`. Given `SV2-A2`, where that
directory's identity is now load-bearing for the concurrency guard, the missing half-page is worth more
than it looks.

---

## D — not defects

| | Finding | Status |
|---|---|---|
| **SV2-D1** | `/proc/$$/winpid` really is the lock holder's Windows pid, and the traps remove the right lock | closed — checked clean, measured |
| **SV2-D2** | The JVM probe's new `case` arms route every input correctly and the warning is reachable | closed — checked clean |
| **SV2-D3** | The looser `connected` is safe at every caller | closed — checked clean |
| **SV2-D4** | `isShut()` had no caller, and the comment left at the colour clause is exactly right | closed — checked clean |
| **SV2-D5** | `FV2-B1`/`FV2-C2`'s corrections check out number by number | closed — checked clean |
| **SV2-D6** | The false-"alive" direction is real and is the right direction to fail in | closed — not a defect |

### SV2-D1 — the winpid, `$$`, and the traps

The briefing asked this specifically, so here it is end to end.

- The script is run as `sh`/`bash docs/tools/battery.sh`, so the interpreting shell is a new process and
  `$$` is its pid. Inside a command substitution `$$` still expands to that shell, not the subshell —
  measured: `A=$$; C=$(cat /proc/$$/winpid); D=$(cat /proc/$A/winpid)` gives `shell=50980`, `C=D=27108`,
  and `cat /proc/50980/winpid` prints `27108` directly. So `LOCK_PID` at `:165` is the Windows pid of
  the process that holds the lock, holds the traps, and exits. **Correct.**
- `Get-Process -Id 27108`-style queries answer `yes` for a live shell's winpid and `no` for its MSYS pid
  (measured both ways). So the normal path answers the right question.
- The `EXIT` and `INT`/`TERM` traps (`:225-226`) remove `$LOCK`, the same fixed path the script wrote at
  `:213`. Both refusal exits — the JVM probe's `exit 2` at `:128` and the lock's `exit 2` at `:193` —
  happen **before** the traps are installed, so a battery that correctly refuses cannot delete the live
  holder's lock. That ordering is right and is worth not disturbing.
- Two small consequences, stated rather than filed: the window between `:213` and `:225` can leave a
  stale lock if the run is killed in it (the stale path then clears it), and both the "warn and proceed"
  and false-stale paths overwrite another battery's lock and then delete it at exit — but both of those
  already mean two batteries are running.
- An empty lock file is safe: the PowerShell command becomes a parse error, stdout is empty, `ALIVE` is
  `''`, `kill -0 ""` fails, and the `*)` arm warns rather than declaring stale (measured: the exact
  command with an empty id prints nothing on stdout).

### SV2-D2 — the JVM probe's arms

Simulated the exact `case` with six inputs: `''` → warn, `0` → compare (proceeds silently), `3` →
compare (refuses), `12abc` → warn, `12 3` → warn, an embedded newline → warn. Every input is routed as
intended and the warning text is reachable through both of its causes — a probe that returns nothing
(PowerShell missing, WMI failing; stderr is discarded at `:99`) and a malformed count. Removing the
`2>/dev/null` from `[ "$RUNNING_JVMS" -gt 0 ]` is safe now, because the compare arm can only receive an
all-digit string, and it is an improvement: a diagnostic there would no longer be swallowed.

### SV2-D3 — the looser `connected`, at every caller

`connected(Point, Point, boolean)` is called only from `canGetHome` (`:1569`, `:1580`, `:1584`), which
is called only from `plan()`'s `unreachable` scan (`:445`). The two-argument overload is still dead —
its only reference is its own body at `:1660`, and there is no other caller in `src/` or `test/`
(`FV2-C6`, still open). `auditAgainstRuntime` does not reach `connected`; it calls `firstClearRoute`
(`:629`).

So the whole effect of the `connected` seed is on the `IMPOSSIBLE` shortcut, and it is monotone in the
safe direction: `unreachable` can only shrink, so `IMPOSSIBLE` becomes `NO_PLAN_FOUND` or `READY`, and
the moves themselves come from `search()`. It also keeps `connected` a relaxation of `firstClearRoute`
under either resolution of `SV2-A1`, which is the invariant that matters.

For completeness on the other side: the `firstClearRoute` seed is also a pure relaxation of itself —
`turned` is monotone, so seeding it true only makes arrivals acceptable that were not, and never loses
a route. That is precisely why it can only add plans, and why `SV2-A1` is about the plans it adds. Its
one effect on `auditAgainstRuntime` is to move the planner *towards* `getPossiblePaths` (which applies
no terminus rule), so it reduces logged disagreements rather than inventing them.

### SV2-D4 — `isShut()`, and the comment at the colour clause

`isShut` has no reference anywhere in `src/` or `test/`, by name; the only hits in the whole repository
are the review documents themselves and a stale `build/classes/…/TileAnnotation$Badge.class`. `Badge`
is a UI value type, never serialised, so nothing reaches it reflectively. The deletion is safe.

The surviving comment at `TileAnnotation.java:1516-1520` claims that `shut` implies `parking` at both
`Badge` sites, so the colour clause cannot decide anything in production. Confirmed — there are exactly
two construction sites and both compute the pair identically:

- `AutonomySession.java:4360` / `:4365`: `Boolean.FALSE.equals(getPointProperty(tile, "active")) ||
  !isAutoDestination(tile)` for `parking`, `Boolean.FALSE.equals(getPointProperty(tile, "active"))` for
  `shut`.
- `AutonomyEditorPanel.java:5955-5956` / `:5961`: the same pair through `session.`.

`isParking() || isImpassable()` is therefore `isParking()` on both diagrams, exactly as the comment now
says.

### SV2-D5 — `FV2-B1` and `FV2-C2`'s corrections

Every number in `c9153aaf` checks out against the two JSON files, read directly:

- `setup.json`'s `pages` is `{"1": "2 - Bottom", "2": "3 - Top Parking", "3": "4 - Combined", "4": "5 -
  Test", "5": "1 - Main"}` — page id 5 is `1 - Main`, so all six `tileLengths` keys are on the main page.
- `tileLengths` is exactly `{"5:20,13": 4, "5:0,11": 4, "5:20,14": 2, "5:1,10": 4, "5:14,3": 3, "5:5,4":
  3}`, and `pointNames` gives `5:20,13` = `BottomMainB`, `5:20,14` = `BottomMainC`. Both are in
  `stations` and both carry `"canReverse": true` in `configuration-Main.json`; `20,14` carries
  `"home": "EN57-947"`. The `MT-248` table (room 4 and room 2) is right.

### SV2-D6 — the false-"alive" direction

A stale lock whose winpid Windows has since recycled reads as alive and cannot be cleared by finishing
the run, which is the "guard that has to be cleared by hand" the file warns about twice. The `kill -0`
line adds a second, much smaller chance of the same thing (an unrelated MSYS process with that number;
both pid spaces here sit in the same 10⁴–5×10⁴ band, and there are only a dozen or so live MSYS
processes, so it is negligible). Not filed: it is the annoying direction rather than the dangerous one,
the comment accepts it explicitly, and it only arises after a run that was killed without its trap.

---

## Open questions that need execution

1. **`SV2-A1`.** `testATrainStandingOnATerminusHasAlreadyTurnedToo` with `assertTrue(plan.isPossible())`
   added, and `|| from.isTerminus()` removed from `HomeStaging.java:956` only, should show the split:
   the assertion fails while `assertNotEquals(…, "IMPOSSIBLE")` still passes. That is the demonstration
   of `SV2-B1` and the confirmation of `SV2-A1` in one run.
2. **`SV2-A1`.** Confirm `EN57-947`'s `reversible` flag in the live locomotive database. The claim that
   it is non-reversible comes from the comment at `testNonReversibleTrains.java:203-205`, measured in
   August; the flag lives in the preferences store, not the repository.
3. **`SV2-A2`.** Two batteries started with different `TC_SCRATCH` values will both proceed. Worth one
   deliberate demonstration *with the compile step stubbed out* before the lock is moved — or simply
   accept the crash-dump evidence, which is already conclusive, and move the lock.
4. **`SV2-C6`.** `ps -W | grep -i java` taken while one test JVM is running would settle whether `ps -W`
   can see `java.exe` at all.
5. Whether `testReturnHomeSequencesAReversal` and `testHomeStaging` are green as committed. I have read
   them and can see nothing wrong beyond `SV2-B1`; I have run nothing.
