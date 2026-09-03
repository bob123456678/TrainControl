# Third validation: the corrections made in answer to `SV2`

**Status:** open

**Prefix for citing this document elsewhere:** `TV2`

**Reviewed:** branch `autonomy-diagram-r0` at `f008d9ac`, on 2026-09-01. Scope is `208b3ee1` and
`f008d9ac` — the corrections made after the second validation pass
(`docs/reviews/2026-09-01-second-validation.md`, prefix `SV2`) — plus the index they update, the two
user documents those commits' subject matter reaches, and `one.sh`, which is the sibling runner of the
file `208b3ee1` repaired. The working tree is clean apart from
`cs2_sample_layout/config/autonomy/configuration-Main.json` and `setup.json`, which are `FX2-1` and
were not touched.

**Method: reading, plus read-only process, environment and data probes.** No build, no test run, no
application, no `battery.sh`, no `one.sh`. The probes were `echo $TEMP` / `$TMP`, `mount`, `ls -di`, a
backslash-path round trip inside my own scratch directory, `ps -W`, one read-only
`Get-CimInstance Win32_Process` count, and `python3`/`cat` reads of `cs2_sample_layout/`'s two JSON
files and of every `cp.txt` under the agent temp tree. Nothing was written anywhere except this file
and one probe file in my own scratchpad, which was deleted. Where a claim needs execution I say so and
leave it in the open-questions list.

**The three findings I am most confident of are `TV2-A1`, `TV2-B1` and `TV2-C1`.** `TV2-A1` is the
briefing's suspicion confirmed: the guard was repaired in the file that is reviewed and left alone in
the file that is used all day. `TV2-B1` is the round's own "sweep the siblings" rule missed at a fifth
site that already has four written examples of itself. `TV2-C1` is the round's named pattern once more
— the clause added to catch this project's compiles cannot match them, and the compiles are caught by
an older clause nobody was thinking about.

**`HomeStaging` itself is clean.** I went looking for the A the briefing predicted and did not find
one; the invariant holds everywhere the two searches differ. That is `TV2-D1`, and the enumeration is
there so the next reader does not have to redo it.

---

## Verdict per correction

| Correction | Where | Fixes its finding? | Broke anything? | Test real? |
|---|---|---|---|---|
| `SV2-A1` — `firstClearRoute` reverted to `from.isReversing()` | `208b3ee1` | **Yes** | No — the invariant holds in both directions (`TV2-D1`) | Yes, and both mutations really do flip it (`TV2-D2`) |
| `SV2-A1` — `connected` keeps `\|\| from.isTerminus()` | `208b3ee1` | Yes | It makes `AutomationAPI.md:409` false and leaves the audit unexempted (`TV2-B2`, `TV2-B1`) | Yes |
| `SV2-B1` — the test rewritten to assert both halves | `208b3ee1` | Yes | No | Yes, but neither assertion pins the outcome it names (`TV2-C5`) |
| `SV2-A2` — the lock moves to `TEMP` | `208b3ee1` | **Yes**, measured (`TV2-D3`) | No | Shell, no test |
| `SV2-A2` — the probe learns `javac` | `208b3ee1` | Yes in effect, **no by the stated mechanism** (`TV2-C1`) | No; Adam's own application is still not matched (`TV2-D4`) | Shell, no test |
| `SV2-A2` — the same repair in `one.sh` | **nowhere** | **No. It was never made** (`TV2-A1`) | The cross-tool window is now one-directional | n/a |
| `SV2-C6` — the `ps -W` retraction | `208b3ee1` | Conclusion probably right, **evidence does not discriminate** (`TV2-C2`) | No | n/a |
| `SV2-C1` — `FV2-A1`'s commit reference | not fixed | **No**, and `f008d9ac` edited that table without touching it (`TV2-C4`) | — | n/a |
| The index header's "up to `2de95ad0`" | not fixed | No — seven commits stale (`TV2-C3`) | — | n/a |

---

## A — wrong behaviour on the layout, or data silently lost

| | Finding | Status |
|---|---|---|
| **TV2-A1** | `one.sh` never received any of the round's five corrections to the concurrency guard, has no lock at all, and its probe is blind to every compile window — so the overlap `SV2-A2` diagnosed is still fully open in the runner that is used far more often than the battery | open |

### TV2-A1 — the guard was fixed in the file that is reviewed and not in the file that is run

`208b3ee1` moved `battery.sh`'s lock to a user-wide path and taught its probe about `javac.exe`.
`one.sh` — which is not in the repository, and which I read at
`.../51b92044-34bd-4b4c-875d-48bf7a99935f/scratchpad/one.sh`, last modified **04:09**, i.e. before
`c9153aaf` (04:57), `f59fa45e` (05:21) and `208b3ee1` (06:36) — got none of it. Four separate defects,
all of them the ones this round has already fixed once:

1. **It takes no lock at all.** There is no `LOCK` in the file. `battery.sh`'s own comment at `:151-157`
   says why that matters: *"The JVM check above cannot see a battery that has not reached its JVMs yet,
   and this script compiles the whole tree first - a minute or more in which a second run looks past it
   and finds nothing... The lock is held from here, before the compile, so it is the only thing that
   covers that window."* `one.sh` compiles the whole tree too (`one.sh:69`,
   `javac.exe ... -d "$SP/build/one" ... @"$SP/one-files.txt"`) and has nothing covering that window.

2. **Its probe is the pre-`208b3ee1` one**, `one.sh:44`:

   ```sh
   RUNNING_JVMS=$(powershell.exe -NoProfile -Command "(Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | ...
   ```

   `Name='java.exe'` only. So a `one.sh` started while a battery — or another `one.sh` — is in `javac`
   sees nothing and proceeds. That is precisely the window `SV2-A2` identified as the one both
   overlapping runs of 2026-09-01 were in.

3. **Its numeric arm is the pre-`f59fa45e` one**, `one.sh:47-49`:

   ```sh
   case "$RUNNING_JVMS" in
       [0-9]*)
           if [ "$RUNNING_JVMS" -gt 0 ] 2>/dev/null
   ```

   This is `FV2-C5` verbatim, unfixed: `12abc` matches `[0-9]*`, the `-gt` fails, its diagnostic goes to
   `/dev/null`, and the run proceeds with **no warning at all** — the malformed case the warning exists
   for.

4. **Per-session by construction.** `one.sh:13` hard-codes
   `SP="C:/Users/adamo/AppData/Local/Temp/claude/.../51b92044-.../scratchpad"`, so each session's copy
   is a different file with a different scratch tree. That is the same fact `SV2-A2` established about
   the lock, applied to the whole runner.

**The resulting asymmetry, which is what makes this concrete.** The `-cp` `one.sh` passes to `javac` is
`one.sh:65`, `"src;...;resources_test/testng-6.14.3.jar;test"`, so `battery.sh`'s widened probe now
matches a running `one.sh` compile (see `TV2-C1` — via the `*testng*` clause). The reverse does not
hold: `one.sh` cannot see a battery's `javac`, cannot see another `one.sh`'s `javac`, and takes no lock
that either could contend for. So after `208b3ee1`:

| second run started during... | battery compile | battery JVMs | one.sh compile | one.sh JVMs |
|---|---|---|---|---|
| **battery.sh** | refuses (lock) | refuses (probe) | refuses (probe, `TV2-C1`) | refuses (probe) |
| **one.sh** | **PROCEEDS** | refuses (probe) | **PROCEEDS** | refuses (probe) |

Two of the four cells are open, both in the runner whose own header says it *"is what gets used all
day, far more often than the battery"* and that *"a guard that only runs on the slow path is a guard
that is not running."*

**Why A rather than B.** The hazard is not a wrong test result: it is two test JVMs redirecting the
Java Preferences store at once, which is how the real railway configuration was damaged on 2026-08-30
and is why `cs2_sample_layout/config/autonomy/configuration-Main.json` differs from `HEAD` at 14 of 71
points today (`FX2-1`, still unresolved). `SV2-A2` and `FV2-A1` were both filed at A for the same
mechanism in `battery.sh`. This is that mechanism, unrepaired, in the more frequently used tool.

**And the file itself is the meta-defect.** `battery.sh:5-6` records exactly this lesson: *"Lived in a
session scratch directory until 2026-08-25, which is why the bug below could be made twice: a harness
nobody can read is a harness nobody can review. It is here now."* `one.sh` is still in that state —
`find . -name one.sh` over the repository returns nothing — which is why five corrections in one day
went past it without anyone noticing.

**Remedy, and it is small:** move `one.sh` into `docs/tools/` beside `battery.sh`, take `TC_SCRATCH`
from the environment instead of hard-coding it, and share the probe and the lock. Until then, at
minimum copy `battery.sh:115` and `:122-149` and `:79-231` across verbatim.

---

## B — incorrect results, or crashes in specific configurations

| | Finding | Status |
|---|---|---|
| **TV2-B1** | `auditAgainstRuntime` has no exemption for the terminus back-in rule, so the one instrument that finds real planner/runtime divergence reports the planner as defective for applying the rule `208b3ee1` just confirmed it must apply | open |
| **TV2-B2** | `AutomationAPI.md:409` promises that homing "reports that locomotive as unable to reach its home"; with `connected`'s terminus seed kept, it does not, for any train standing in a berth — which is all twelve of Adam's | open |
| **TV2-B3** | `AutomationAPI.md:411` tells the reader "setting one length is not enough to arm it"; an edge's length is a sum over its tiles, so one measured tile does arm it, and the code says so sixty lines above the loop | open |

### TV2-B1 — the fifth correct divergence, and the fifth exemption was not added

`208b3ee1` settled that `HomeStaging`'s search is deliberately stricter than the runtime about a
non-reversible train reaching a terminus. `Layout.java:2295-2297` says the same thing in the runtime's
own words:

> Staging does not lose the rule by this: HomeStaging carries its own since 2026-08-31, and it is
> stricter - a non-reversible train may only be sent home to a terminus by a route that turns it round
> on the way.

`auditAgainstRuntime` (`HomeStaging.java:602-695`) exists to find places where the two disagree, and it
carries **four** hand-written exemptions for divergences that are correct rather than defects —
inactive points (`:634-638`), exclusions (`:640-645`), and FR-001 (`:647-675`). The terminus rule is a
fifth and has no exemption.

Trace it:

- `runtimeSays` is built from `getPossiblePaths(loc, true)` (`:620-623`). That method filters only on
  `!end.equals(start) && end.getBlockLocomotive() == null && end.isDestination()` plus `isPathClear`
  (`Layout.java:4338`, `:4350`), and `isPathClear` has had no terminus rule since Adam's ruling
  (`Layout.java:2280-2297`). So a terminus is in `runtimeSays` for **any** locomotive.
- `plannerSays` is built from `firstClearRoute` (`:627-630`), which refuses a terminus to a
  non-reversible locomotive unless a reversing point lies on the route (`:1035-1041`, `:1557-1560`).
- None of the four exemptions catches it: the berth is active, is not excluded, and is not held back.
- So `disagreements++` and `logStagingAudit(loc, p, true)` fire (`:677-681`), printing
  `autolayout.warnStagingPlannerTooStrict` = *"planner refuses {0} -> {1}, but the layout would allow
  it"* (`messages.properties:203`).

**Reachable on Adam's railway.** All 12 squares carrying `"parking": true` in
`configuration-Main.json` also carry `"mustReverse": true` (I read the file: `parking count: 12`,
`parking&mustReverse: 12`), and `AutonomyBuilder.java:826`/`:970` emit a `mustReverse` station copy as
`terminus`. `testNonReversibleTrains.java:201-205` records two non-reversible locomotives on his
layout, and `Layout.java:2287` names one of them, `2-8-4 3505 SP`. Every one of those berths reachable
from where such a locomotive stands produces one false line.

**Severity.** `planReturnToHome` calls the audit only under `this.control.isDebug()`
(`Layout.java:6464-6468`), which is why this is B and not higher. It is the same severity and the same
shape as `DR-B1`, whose own test javadoc at `testHomeStaging.java:3712-3720` states the cost in terms
that apply here word for word: *"the instrument that exists to find real mis-copies reported the rule
working as a defect... in a debug channel that is only read when something else is already being
chased."*

**Not introduced by `208b3ee1`** — the rule landed in `firstClearRoute` on 2026-08-31 and the exemption
was missing from that moment; `f59fa45e`'s terminus seed happened to mask it for trains standing on
termini, and the revert unmasks it. It belongs to this commit because this is the commit that decided,
wrote down and tested the asymmetry, and the round's own rule is *"when you fix a call site, grep for
its twins before closing the finding."*

**Remedy:** one line beside the other four, `if (mustBackIn(loc, p)) continue;`, with a test in the
shape of `testTheParityAuditIsSilentAboutAStationHeldBackByAnOccupiedSquare`.

### TV2-B2 — the documented refusal is not the refusal the user gets

`AutomationAPI.md:409`, the third tier:

> **Returning home:** a non-reversible locomotive may be homed at a terminus, and the planner insists
> the way there turns it round first - so it backs in and can leave forwards. **Where no such route
> exists, homing reports that locomotive as unable to reach its home** rather than setting off and
> failing.

The first half is true again after the revert. The second is false wherever the train starts on a
terminus, and that is the case `SV2-A1` measured as essentially every Return Home Adam will run.

- The report the sentence describes is `Outcome.IMPOSSIBLE`, which is the only outcome carrying a
  locomotive name: `TrainControlUI.java:20409-20410` maps it to `errorCannotReachHome` = *"These
  locomotives cannot reach their home station at all: {0}. Check the track between them and where they
  started."*
- `IMPOSSIBLE` is reached only through `plan()`'s `unreachable` scan (`HomeStaging.java:443-445`), which
  asks `canGetHome` → `connected(from, copy, mustBackIn(loc, copy))`.
- `connected` seeds `boolean startsTurned = from.isReversing() || from.isTerminus();`
  (`HomeStaging.java:1733`), kept deliberately by `208b3ee1`. With `startsTurned` true, every reachable
  arrival satisfies `(!mustReverse || now)` at `:1754`, so the turning requirement **can never fail** in
  the proof for a train standing on a terminus.
- The search then refuses the same journey at `firstClearRoute:1037`, `search()` returns null, and
  `plan()` answers `NO_PLAN_FOUND` (`:582`) with `noLocs()`. `describeStagingOutcome`'s `default` arm
  (`TrainControlUI.java:20421-20422`) gives `errorNoReturnPlanFound` = *"No way to arrange this was
  found. It may still be possible - try moving one locomotive out of the way by hand first."*
  **No locomotive is named.**

This is exactly what the new test observes and asserts: `testALeavingTerminusDoesNotCountAsHavingBackedIn`
asserts the outcome is *not* `IMPOSSIBLE` and *not* possible — i.e. `NO_PLAN_FOUND` with nobody named.
The test and the document contradict each other, and the test is right.

**`SV2-B2` predicted this finding would disappear with the revert and it did not.** Its closing
paragraph says *"If `SV2-A1` is fixed by reverting the `firstClearRoute` seed, this finding disappears
with it and the paragraph needs no change."* Half of it disappeared — the planner does insist again.
The other half is a consequence of the half `208b3ee1` deliberately **kept**, and no commit has looked
at the paragraph since.

**Same for the manual test.** `docs/manual-tests/tests.md` `MT-246` step 7 — *"A terminus with no
reversing point on the way to it should now be reported as impossible for that locomotive"* — is now
conditional on where the train is standing when Return Home is pressed: true from ordinary track, false
from a berth or a reversing point. Its disposition is still `needs test`, so the tester will meet the
ambiguity rather than a stated precondition.

**Remedy:** state which refusal the user gets, and when. Something like: *"Where no such route exists,
homing does not set off — it reports that no arrangement was found. It can only name the locomotive
outright when the train is not itself standing in a berth, because a train in a berth may have backed
in and the planner cannot prove otherwise."*

### TV2-B3 — "setting one length is not enough to arm it" is false at the granularity the user sets lengths

`AutomationAPI.md:411`, added by `c9153aaf`:

> Separately, a train can be refused for being too long to back in: **only when every segment of the
> approach has a recorded length**, and their total is shorter than the train. A single unmeasured
> segment on the way makes the total unknowable and nothing is judged - **so setting one length is not
> enough to arm it.**

The guard is `Layout.java:2361-2386`:

```java
for (Edge segment : path)
{
    if (segment.getLength() <= 0)
    {
        measured = false;
        break;
    }

    room += segment.getLength();
}
```

"Segment" there is an **`Edge`**. On a diagram-derived graph an edge's length is
`GraphReducer.sumLength` (`GraphReducer.java:1052-1062`):

```java
total += Math.max(0, authored.getTileLength(step.getTile()));
```

— a sum over the **tiles** the edge spans, with unmeasured tiles contributing zero. The user sets
lengths per tile (`setup.json`'s `tileLengths`, keyed `"5:20,13"` and so on). So one measured tile in a
five-tile edge gives that edge a positive length, `measured` stays true, and the guard fires on a total
that is missing four tiles — refusing a train that fits.

The code says this itself, 20 lines above the loop I quoted (`Layout.java:2343-2348`):

> 1. `getLength() > 0` DOES NOT MEAN "MEASURED". On a diagram-built graph an edge's length is
>    GraphReducer.sumLength, which adds `Math.max(0, tileLength)` over the tiles it spans - so one
>    measured tile out of five gives a positive length, and this loop reads that as a measured segment.

So the document was written against the loop and not against the comment directly above it, which is
the README's *"the documentation is part of the method"* in reverse. `FV2-B3` correctly removed "If any
track lengths are set" as the notice's trigger rather than the guard's; the replacement over-corrected
into the opposite falsehood, and it is the more consequential direction: a user reading `:411`
concludes the guard is dormant on a layout with six measured tiles, while `FX2-3` records that on that
very layout it is live at `BottomMainB` (room 4) and `BottomMainC` (room 2), with 42 of his 54
length-carrying locomotives longer than 2.

**Remedy:** say what is enforced. *"...only when every edge of the approach reports a length. On a
layout built from the track diagram an edge's length is the sum of the tile lengths along it, so one
measured tile is enough to make that edge count as measured — and the total will then be short by the
tiles you have not measured. Measure the whole run-in, or none of it."* The underlying unsoundness is
`FX2-3` and stays Adam's to rule on; the document should not describe a stricter arming condition than
the one in force.

---

## C — low

| | Finding | Status |
|---|---|---|
| **TV2-C1** | The `javac.exe ... TrainControl` clause cannot match this project's own compiles in half the sessions; the compiles are caught by the older `*testng*` clause, which also blocks on anything else compiled with TestNG. The comment claims the opposite in both directions | open |
| **TV2-C2** | The `ps -W` retraction's measurement is the no-JVM baseline: `ps -W \| grep -ci java` returns 2 with zero Java processes running | open |
| **TV2-C3** | The index header still says fixes were applied "up to `2de95ad0`", seven commits ago | open |
| **TV2-C4** | `FV2-A1`'s row still cites the wrong commit (`SV2-C1`, unfixed by the commit whose subject was that table), and `FV2-B2`'s row now describes a fix half of which was reverted | open |
| **TV2-C5** | Neither assertion in the new test pins the outcome it names; five of the seven outcomes satisfy both | open |
| **TV2-C6** | `connected` will not travel through a terminus, but the search can stop at one and go on, so the impossibility proof is tighter than the multi-leg search | open — structural, no instance found on the derived graph |
| **TV2-C7** | `SV2-A1`'s one-sentence question for Adam was dropped when the index listed it Fixed | **DEFERRED — needs Adam** |

### TV2-C1 — the clause added to catch this project's compiles cannot catch them

`battery.sh:111-115`:

```
# javac too (SV2-A2): ... Narrowed to this project so that compiling something
# else does not block a battery.
PROBE="(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javac.exe'\" | Where-Object { ... -or (\$_.Name -eq 'javac.exe' -and \$_.CommandLine -like '*TrainControl*') } | Measure-Object).Count"
```

The battery's own compile is `battery.sh:251-252`:

```sh
"${TC_JAVAC:-/c/Program Files/Java/jdk1.8.0_361/bin/javac}" -nowarn -encoding UTF-8 \
    -d "$BUILD" -cp "$CP" @"$S/battery-files.txt"
```

Every argument on that command line comes from the scratch tree or from `cp.txt`: `$BUILD` is
`$S/build/battery-$$`, the argfile is `$S/battery-files.txt`, and the source paths are **inside** the
argfile, which `Win32_Process.CommandLine` does not expand. So whether "TrainControl" appears at all
depends entirely on `cp.txt`. I read every `cp.txt` under the agent temp tree:

| file | contains `TrainControl`? |
|---|---|
| `.../51b92044-.../scratchpad/cp.txt` | **no** — `.../scratchpad/build/one;src;resources/flatlaf-3.7.2.jar;...;test` |
| `.../51b92044-.../scratchpad/battery-final/cp.txt` | **no** — relative paths again |
| `.../0362837d-.../scratchpad/tc/cp.txt` | yes — absolute `C:\...\TrainControl\...` |
| `.../claude/mut/cp.txt` | yes |

So in this session the new clause is dead for the very compile it was written for.

**The compile is nonetheless detected — by the clause nobody was thinking about.** Every one of those
classpaths ends `resources_test/testng-6.14.3.jar`, and PowerShell's `-like` is case-insensitive, so
the pre-existing `$_.CommandLine -like '*testng*'` matches a `javac.exe` as soon as `javac.exe` was
added to the `-Filter`. That is what "measured at 1 during a real compile" measured; the measurement
cannot tell the two clauses apart, which is the third time in this round a branch has been confirmed by
a value that does not exercise it.

Two consequences, opposite in direction:

- **It does not narrow.** Any `javac.exe` on this machine with "testng" anywhere on its command line —
  another project entirely — now blocks a battery. The comment says the narrowing prevents exactly
  that.
- **It is a trap.** A future reader who removes the `*testng*` clause for `javac`, reasoning that the
  `TrainControl` clause covers our own compiles, silently reopens the compile window for every session
  whose `cp.txt` is relative. That is the same "a check that cannot fire invites a future reader to make
  it fire" argument `firstClearRoute:998-1002` makes about lock edges.

Filed at C because the guard works today. **Remedy:** match on the JDK's own path or on `@`-argfile
name plus `-d`, or simply say in the comment that `*testng*` is what catches our compiles and the
`TrainControl` clause is a belt for absolute classpaths.

### TV2-C2 — the retraction's evidence is the baseline

`docs/reviews/2026-09-01-fanout-index.md:59-62`:

> **Retracted: `ps -W` sees java perfectly well.** With a test JVM running, `ps -W | grep -ci java`
> returns 2.

Measured here, now, with **nothing** Java running:

```
$ powershell "(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe' OR Name='javac.exe'\" | Measure-Object).Count"
0
$ ps -W | grep -i java
    78680 ... C:\Program Files (x86)\Common Files\Java\Java Update\jusched.exe
    99508 ... C:\Program Files (x86)\Common Files\Java\Java Update\jucheck.exe
$ ps -W | grep -ci java
2
```

Two is the idle baseline. `grep -ci java` counts lines containing "java" anywhere, and the path
`...\Common Files\Java\Java Update\...` contains it twice over — the two processes `SV2-C6` itself
enumerated. So the quoted measurement is consistent with a test JVM having been invisible, and cannot
distinguish the two hypotheses it was taken to settle.

The **conclusion** is probably still right, for a reason the measurement does not supply: `ps -W`
enumerates arbitrary non-MSYS Windows processes, and `jusched.exe` is proof of that much. But the index
now states as fact something it has not shown, which is the same objection `SV2-C6` raised against the
sentence it replaced — the fourth explanation of this incident asserted on evidence that does not
discriminate. Nothing depends on it today (the probe uses WMI). **Remedy:** either quote
`ps -W | grep -i java.exe` taken beside a running test JVM, or write "not established" and drop the
number.

### TV2-C3 — the index header is seven commits stale

`docs/reviews/2026-09-01-fanout-index.md:8`: *"Fixes and dispositions below were applied after them, up
to `2de95ad0`."* `git rev-list --count 2de95ad0..HEAD` is **7**, and the Fixed table below it
cites `33f5f61e`, `c9153aaf`, `f59fa45e` and `208b3ee1`, all later. The README's rule is explicit: *"A
review header that still claims 'no code was changed' after twenty commits is worse than no header."*

### TV2-C4 — the Fixed table's two remaining wrong rows

- **`:90`** still says `FV2-A1` was fixed in `f59fa45e`. It was not: `git show f59fa45e --
  docs/tools/battery.sh` is only the `FV2-C5` `case`-arm swap; the winpid write, the fallback and the
  `kill -0` second opinion are all in `c9153aaf` (which is 24 minutes *earlier*). This is `SV2-C1`,
  filed and unfixed — and `f008d9ac`, whose entire subject line is *"The index's SV2 commit references
  filled in"*, edited four rows of that table and left this one two rows above them.
- **`:91`** lists `FV2-B2` as fixed by `f59fa45e` with the description *"the terminus limb is the one
  that reaches Adam's berths"*. Half of that fix was reverted eight rows later by `SV2-A1`. The row is
  now true only of `connected`, and says nothing about it. Per the README's *"A finding keeps its
  identifier for life: if its severity is revised, say so in its entry"*, that row needs "in `connected`
  only; the `firstClearRoute` half was reverted — see `SV2-A1`".

Also still open from `SV2` and worth restating because `208b3ee1` added to the count rather than
correcting it: `:103`, *"The two Java fixes were seen failing first and mutation-confirmed"*, sits under
a table that now holds four Java changes (`D24-B1`, `TCX-A3`, `FV2-B2`, `SV2-A1`) plus a comment-only
one (`FV2-C1`). That is `SV2-C5`.

### TV2-C5 — the new test does not assert the outcome its javadoc says is the point

`test/core/testReturnHomeSequencesAReversal.java:444` and `:451`:

```java
assertNotEquals(String.valueOf(plan.getOutcome()), "IMPOSSIBLE", ...);
assertFalse(plan.isPossible(), ...);
```

`isPossible()` is `outcome == Outcome.READY` (`HomeStaging.java:314-317`). So the pair asserts "not
IMPOSSIBLE and not READY" — which is satisfied by **five** of the seven outcomes: `NO_PLAN_FOUND`,
`ALREADY_HOME`, `NO_LOCOMOTIVES`, `NO_HOMES`, `LOCOMOTIVES_RUNNING` and `POSITION_AMBIGUOUS`. The
javadoc at `:402` says the opposite: *"the right answer here is that no plan is found, and the point of
the test is WHICH refusal."* The outcome it names is the one thing it does not check.

It is **not** vacuous today — I traced `triage()` (`:709-725`) and `setHomeLocomotive`
(`Layout.java:1158-1203`, which *"NOTHING IS REFUSED HERE ANY MORE"*) and the fixture reaches
`NO_PLAN_FOUND` — and both mutations do flip it (`TV2-D2`). The exposure is latent: if the home
assignment ever stops taking, `triage()` returns `NO_HOMES` before a single line of the code under test
runs, and this test stays green while exercising nothing. That is the shape `battery.sh:421-431` calls
out ("GREEN IS NOT 'no failures'") and the README calls "assert the precondition that makes a test
meaningful".

**Remedy, one line replacing two:**
`assertEquals(String.valueOf(plan.getOutcome()), "NO_PLAN_FOUND", ...)`. It implies both existing
assertions, states the claim the javadoc makes, and both mutations still fail it — `IMPOSSIBLE` from
the `connected` mutation, `READY` from the `firstClearRoute` one.

### TV2-C6 — the proof is still tighter than the search, one layer up

`TV2-D1` establishes that `connected` accepts everything `firstClearRoute` would **for one leg**. The
planner's search is not one leg: `astar` (`:858-881`) plans a sequence, and it may park a locomotive at
a terminus and drive it out again on a later move. `connected` cannot represent that, because it never
expands a terminus (`:1760`) while `firstClearRoute` will happily end a leg at one and start the next
from it.

So a graph whose only route from a train's position to its home passes **through** a terminus square is
proved `IMPOSSIBLE` (`plan():443-445`) although a two-move plan exists. For a reversible locomotive,
where `mustBackIn` is false throughout, nothing else stands in the way.

Filed at C rather than higher because I could not produce an instance on a builder-derived graph:
`AutomationAPI.md:412` requires a terminus's outgoing edges to rejoin the main line only after a
reversing loop, and the main line runs over the *other* copies of a split square, so the
"only-route-is-through-a-berth" topology does not arise on the graph Adam runs. Structurally real,
pre-existing, and worth a comment at `:1760` so the next person to widen `connected` knows which
direction is safe.

### TV2-C7 — the question SV2 raised for Adam was closed rather than asked

`SV2-A1`'s status line reads *"open — the remedy needs Adam's ruling, stated below in one sentence"*,
and the sentence is:

> when a non-reversible train is standing in a parking berth, should the planner assume it backed in
> (so it must be turned again on the way to the next terminus), or assume it was driven in nose-first
> (so it is already turned)?

`208b3ee1` applied the conservative answer, which is defensible — it restores the behaviour that
predates `f59fa45e`, and it matches `MT-246` and the documentation. But the index now lists `SV2-A1` in
**Fixed** (`:98`) with no mention of the question, and the "Deferred — needs Adam" section
(`FX2-1`…`FX2-6`) does not carry it. The answer is now pinned in three places — the seed at
`HomeStaging.java:970`, the assertion at `testReturnHomeSequencesAReversal.java:451`, and
`AutomationAPI.md:409` — and if Adam rules the other way all three move together.

**The one sentence for Adam:** *the planner now assumes a train standing in a parking berth backed in
and must be turned again on the way to its next berth; is that right, or was it driven in nose-first
and already facing out?*

**RULED 2026-09-02, not yet built** ([MT-260](../manual-tests/tests.md#mt-260) ruling 5).  Adam: *"most likely, but can't assume.  trains can also back out and reverse on their way out."*  So the answer is neither of the two the question offered: the planner stops assuming at a berth and searches **both** states from one, because a train may have backed in or may back out and reverse later.  That also settles `RTG-C3`, which is the same seed asked of the impossibility proof - and it settles it in the direction that keeps the proof looser than the search, which is the invariant that rule has to hold.

**Put to Adam as [MT-260](../manual-tests/tests.md#mt-260) (2026-09-02).**  Still open - collecting it is not answering it.

---

## D — not defects

| | Finding | Status |
|---|---|---|
| **TV2-D1** | `connected` accepts everything `firstClearRoute` would, at every point the two differ — the invariant `208b3ee1` states really does hold | closed — checked clean |
| **TV2-D2** | Both of the new test's claimed mutations do fail it, in opposite directions | closed — checked clean, traced not run |
| **TV2-D3** | The lock's new path is genuinely user-wide, `TEMP` is set, and `/tmp` is the same directory | closed — checked clean, measured |
| **TV2-D4** | Widening the probe to `javac.exe` does not catch Adam's own running application | closed — checked clean |
| **TV2-D5** | The three-tier list is otherwise accurate, clause by clause | closed — checked clean |

### TV2-D1 — the invariant, enumerated

The briefing asked for this everywhere the two searches differ, not only at the seed. `connected(from,
to, mustBackIn(loc,to))` versus `firstClearRoute(state, blocked, loc, from, to) != null`, for one leg:

| where they differ | `firstClearRoute` | `connected` | looser |
|---|---|---|---|
| `from.equals(to)` | returns null (`:921`) | returns true (`:1701`) | `connected` |
| origin active | required (`:928`) | not asked | `connected` |
| origin is a destination | required (`:940`) | not asked | `connected` |
| the destination is restable | `canRest(loc,to,state)` (`:941`), which is `canRest(loc,to) && heldBackBy==null` (`:1375-1380`) | `canGetHome` asks the 2-arg `canRest(loc,copy)` (`:1594`) | `connected` |
| the destination is empty | `!state.containsKey(to)` (`:941`) | not asked | `connected` |
| **seed** | `from.isReversing()` (`:970`) | `from.isReversing() \|\| from.isTerminus()` (`:1733`) | `connected` |
| per-step entry | `canEnter` — active, occupancy, blocked sensors, shared-sensor siblings, non-station exclusions (`:984`, `:1157-1226`) | not asked | `connected` |
| accessory conflicts | edge skipped when `withCommandsOf` returns null (`:1007-1010`) | not asked | `connected` |
| expansion through `to` | never — `continue` after the arrival test (`:1040`) | expands unless `to` is a terminus (`:1760`) | `connected` |
| budget | `expansions++ < ROUTE_SEARCH_LIMIT` = 20000 (`:972`) | unbounded | `connected` |
| arrival test | `!mustBackIn(loc,to) \|\| turned` (`:1037`) | `!mustReverse \|\| now`, `mustReverse = mustBackIn(loc,to)` (`:1594`, `:1754`) | identical |
| terminus not expanded | `:1044` | `:1760` | identical |
| lock edges | not consulted (`:986-1005`) | not consulted | identical |
| visited set | `(point, turned)` plus the command set, so a point may be re-reached (`:1017-1022`) | `(point, turned)` (`:1756`) | equal reachability — expansion in `connected` is not command-gated, so its closure over `(point, turned)` is complete |

Two things make the seed asymmetry safe rather than merely different:

1. **`turned` is monotone towards acceptance.** Neither search gates *expansion* on it — `connected`
   gates only on `!next.isTerminus()`, and `firstClearRoute` on that plus `canEnter` — so the set of
   reachable *points* is unchanged by the seed. The flag is read in exactly one place, the arrival test,
   where more `turned` can only accept more. Seeding `true` therefore accepts a superset.
2. **The two flags cannot both be set.** `Point.setTerminus` throws when `isReversing`
   (`Point.java:341-346`) and `setReversing` throws when `isTerminus` (`:363-368`), so
   `isReversing() || isTerminus()` is a genuine disjunction of exclusive cases and there is no square
   where the reverted seed silently reintroduces the `SV2-A1` over-claim.

And the model behind the asymmetry is self-consistent. `turned` is only ever *read* through
`mustBackIn`, which requires `!loc.isReversible()` (`:1559`). For such a locomotive, arriving at a
terminus at all requires `turned` (`:1037`), i.e. it backed in; `executePath`'s flip
(`Layout.java:5599-5605`) then leaves it facing out; so it departs untured. Seeding `false` at a
terminus is right, and for a reversible locomotive the flag is never consulted. The commit's reasoning
is sound and I could not construct an input where `firstClearRoute` succeeds and `connected` fails.

The one place the *purpose* of the invariant is not met is multi-leg journeys, which is `TV2-C6`.

### TV2-D2 — the two mutations

Traced against the fixture at `:416-438` — two termini `RH berth A`/`RH berth B` on sensors 2600/2601,
one edge A→B, nothing reversing, a locomotive forced non-reversible and placed on A (asserted), homed
at B.

- **As committed:** `triage()` returns null; `canGetHome` → `connected(A,B,true)` seeds
  `startsTurned = true` (A is a terminus), the single edge arrives at B with `now = true`, `:1754`
  returns true, so nothing is unreachable. `search()`'s greedy pass calls `firstClearRoute(A,B)`, which
  seeds `turned = false`, reaches B with `turned = false`, fails `:1037` and `continue`s; `astar` finds
  the same and returns null. Outcome `NO_PLAN_FOUND`. Both assertions pass.
- **Mutation (a), drop `|| from.isTerminus()` from `connected`:** `startsTurned = false`, `now` stays
  false, `:1754` refuses, B is a terminus so it is never expanded (`:1760`), the queue empties and
  `connected` returns false → `unreachable` → `IMPOSSIBLE` → the **first** assertion fails. ✔
- **Mutation (b), restore it in `firstClearRoute`:** the seed is true, the arrival at `:1037` is
  accepted, the greedy pass returns a one-edge route, `misplaced` reaches 0, outcome `READY` → the
  **second** assertion fails. ✔

The two really do bite in opposite directions, which is what `SV2-B1` asked for and what the previous
version of the test could not do. Read, not run — see the open questions.

The assertion `assertFalse(plan.isPossible())` is also the **right railway behaviour** for this
fixture, on the assumption `TV2-C7` asks Adam to confirm: a train standing in a berth is assumed to
have backed in, so it leaves forwards, and there is nothing on the one edge to turn it — sending it
would strand it. There is no legitimate arrangement in which a plan should exist here: the only
alternative route is via a turning square, and the fixture has none.

### TV2-D3 — the lock path

Measured in this shell:

```
TEMP=[C:\Users\adamo\AppData\Local\Temp]
TMP=[C:\Users\adamo\AppData\Local\Temp]
resolved: C:\Users\adamo\AppData\Local\Temp/traincontrol-battery.lock
mount:    C:/Users/adamo/AppData/Local/Temp on /tmp type ntfs (binary,noacl,posix=0,usertemp)
ls -di /tmp  ->  3940649675262296
ls -di $TEMP ->  3940649675262296
```

- `TEMP` is set, is the Windows per-user temp directory, and is **not** session-derived — the agent
  scratch directories are `$TEMP/claude/<project>/<session>/scratchpad`, i.e. three levels below it. So
  the lock is one file for the whole user, which is the scope `SV2-A2` argued for and the scope the
  Preferences store has.
- The `/tmp` fallback is not merely sensible, it is the **same directory**: Git for Windows mounts
  `/tmp` with `usertemp`, and the two paths share an inode. A shell with neither `TEMP` nor `TMP` takes
  the identical lock file. (A non-default mount would break that; narrow, and worth one sentence in the
  comment.)
- The mixed separator `C:\...\Temp/traincontrol-battery.lock` is handled by MSYS — I round-tripped
  `echo >`, `cat`, `[ -f ]` and `rm -f` through a backslash path in my own scratch directory. So
  `:191`, `:193`, `:231` and the traps at `:243-244` all address the same file.
- No lock file exists right now, so nothing is left over from the incident.

Two consequences of the move that are not defects but are worth Adam knowing: a stale lock now blocks
**every** session rather than one, and `SV2-C3` (the lock is checked at `:191` and written at `:231`,
not created atomically) is unchanged, so two batteries launched within the same second still both
proceed. Neither was claimed fixed.

### TV2-D4 — Adam's own application is still not matched

The fourth clause is `(\$_.Name -eq 'javac.exe' -and \$_.CommandLine -like '*TrainControl*')`. The
`Name -eq 'javac.exe'` conjunct is what keeps it off the application: a `java -jar
...\TrainControl\dist\TrainControl.jar` carries "TrainControl" on its command line and is `java.exe`,
so it falls through. And the app's run classpath does not carry `resources_test/testng-*.jar`, so the
`*testng*` clause does not reach it either. `battery.sh:108-110`'s promise — *"Adam's own running
application is deliberately NOT matched"* — still holds after the widening.

### TV2-D5 — the rest of the terminus section

Read clause by clause against the code:

- *"Full autonomy only chooses a terminus for a reversible locomotive"* — `pickPath`'s filter at
  `Layout.java:3756-3757` and its mirror `hasAutonomousDestination` at `:3498-3499` both carry
  `(!end.isTerminus() || loc.isReversible())`, and `barredFromAutonomy` at `:4025-4028` returns the
  reason. **True**, and enforced in all three of the places that must agree.
- *"By hand, from the route menu: a terminus is offered to any locomotive, and running the route is not
  refused on reversibility grounds"* — `getPossiblePaths` filters only on destination-ness, block
  occupancy and `isPathClear` (`:4338`), and `isPathClear` has carried no terminus rule since
  `:2280-2297`. **True.**
- *"In full autonomy: a terminus is never chosen for a locomotive that cannot reverse"* — as above.
  **True.**
- *"a non-reversible locomotive may be homed at a terminus"* — `canRest` lost the clause
  (`HomeStaging.java:1645-1663`) and `setHomeLocomotive` refuses nothing. **True.**
- *"the planner insists the way there turns it round first - so it backs in and can leave forwards"* —
  restored by this commit. **True.**

Only the two clauses in `TV2-B2` and `TV2-B3` are wrong. The `SV2-B2` cosmetic note also still stands:
`:411` and `:412` have no blank line between them, so the length paragraph and the outgoing-edges
sentence render as one.

---

## Open questions that need execution

1. **`TV2-D2`.** `testReturnHomeSequencesAReversal` as committed, plus each of the two mutations, would
   confirm the discrimination I traced. I have run nothing.
2. **`TV2-B1`.** A one-line probe would settle it without a battery: build the ring fixture in
   `testHomeStaging`, make one locomotive non-reversible, `setTerminus(true)` on a station it can reach
   with no reversing point on the way, and assert `auditAgainstRuntime() == 1` — then add the exemption
   and assert 0. That is the same pair `testTheParityAudit*` already uses for FR-001.
3. **`TV2-C1`.** `Get-CimInstance Win32_Process -Filter "Name='javac.exe'" | Select CommandLine` taken
   during a battery compile in a session whose `cp.txt` is relative would show directly that
   "TrainControl" is absent and "testng" present. I read the four `cp.txt` files instead.
4. **`TV2-C2`.** `ps -W | grep -i "java\.exe"` taken beside one running test JVM is the measurement the
   index needs and does not have.
5. Whether `testReturnHomeSequencesAReversal` and `testHomeStaging` are green as committed. `208b3ee1`
   reports "142 classes green, one failure, the known deliberate exclusion"; I have not verified it.
