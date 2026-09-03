# The C sweep: what was fixed, what was dismissed, and what needs Adam

**Status:** open — the four questions at the foot of this document are Adam's, and nothing else here is
waiting on anybody.

**What this is.** Adam asked for a report on the low-severity findings that had accumulated across the
review documents: which are fixed, which were dismissed and why, and which need him. This is that
report. It covers the **September rounds** — the reviews of 1 and 2 September, which are about the code
in this release candidate — and says at the end what is left in the July and August backlog.

**Counts, as of 2026-09-03 (rc8).** 477 C findings exist across every review document ever written.
Before this sweep, none of the September ones carried a settled verdict where a reader is told to look
for it. Now:

| | September rounds |
|---|---|
| **Fixed** | 96 |
| **Dismissed** — real, and deliberately not changed | 8 |
| **Open** — 4 of them Adam's to answer, 8 deferred past 3.0.0 with the reason recorded | 12 |
| **Still to settle** in the September rounds | 36 |

---

## What changed behaviour

These are the ones an operator could notice. Everything else in the 96 is a comment, a dead method, a
citation, or a test that was not testing what it claimed.

**All three "why can't I start" messages now name the reason a count cannot see.** The guard refuses
autonomy on `hasErrors()`, which covers a graph that will not build at all — and in that case the error
count is zero, so the API's exception and the greyed Start item's tooltip both fell through to "wait for
the trains to stop". They were telling you to wait for trains that are not running and never will be.
Pinned as a set, so the next widening cannot leave a twin behind (`V31-C1`, `V32-C1`).

**Thirty station-capacity warnings leave the notice list** on a railway that records no track lengths.
That is your own condition for the reversal notice — *"if any other track length is set anywhere"* — and
its six-day-older sibling never got it. They come back the moment you record one length (`SVN-C3`).

**A page may be called "3" again.** The rename dialog asked the page-name map for a duplicate, and that
map also carries the active page number and the active button's key code under negative keys. After any
start from a saved state, naming a page the digits of the last-saved page, or "81" for Q, was refused as
a duplicate of a page that does not exist (`SVN-C13`).

**Clearing every home is one station-index rebuild instead of sixty-two.** Each `setHome` re-derived the
index, which is a full builder construction on the event thread — for the gesture that exists precisely
because clearing them one at a time is too many right-clicks (`DY3-C5`).

**The start door counts its blocking problems** instead of always saying "one thing has to be dealt
with" (`DY3-C7`), and **the run list's "autonomy will not choose this" dash** knows about a terminus a
non-reversible train cannot back into — a rule that moved on 1 September and left this fourth copy
behind (`SVN-C4`).

**The staging audit stopped crying wolf.** Your 1 September ruling took the terminus rule out of
`isPathClear` and left it in the planner, which is a deliberate divergence — and the audit logged "the
planner is stricter than the railway" for every such pair on any debug-mode Return Home (`SVN-C5`).

**And the harness:** `one.sh` counts a skipped class apart from a failed one and exits 2 rather than 1,
which is what `battery.sh` has always done and what its own comment claimed (`V34-C3`).

---

## What was dismissed, and why

Eight findings are real and deliberately unchanged. None is a judgement that the reviewer was wrong;
each is a judgement that the change costs more than the defect, this week.

| Finding | What it says | Why it stands |
|---|---|---|
| `FV2-C1`, `FV2-C7`, `SVN-C11` | `isImpassable()` and `isShut()` are one field with two javadocs, and a colour clause that can never change a production answer | Merging them is a change to the class that draws every mark on every tile, with the diagram under manual test |
| `SVN-C9` | The impassable cross ignores the placement the code above it just computed, so a shut station on a bend draws its cross and its train star in different places | Same class, same reason. Narrow: needs the editor, a curved feedback station, and shut |
| `SVN-C10` | `docs/tools/parity/` takes no lock, and `battery.sh` fingerprints the live layout after the run has been allowed to start | Harness, and neither can damage the railway. Worth doing next time the harness is opened |
| `SVN-C12` | A station held back by two squares writes a version-2 shape into a file stamped version 1 | Latent — the field arrived after the version bump, so no released build reads it with a string-only reader. The fix is a change to the file format |
| `SVN-C15` | `accessoryHeldByAutonomy()` is computed and thrown away at every human door | Pure cost, nobody has reported feeling it, and the fix reorders the route-throwing path |
| `DY3-C4` | `check()` performs three configuration builds per call, and the start door calls it twice | A cache over the method every guard and every affordance consults is not a release-candidate change. It would close `LD-C6` and `V36-C5` too, and they should be done together |
| `CMT-C2` | 229 message-bundle keys are unused | Noise. **The direction that costs you something is the other one** — a key renamed without its call site puts the raw key on screen — and that had no test at all. It does now, and it is clean |
| `SVN-C14` (part) | The home badge in the run list asks the Point rather than the square, so a train on the far copy of its own home reads as not-at-home | Ten of your thirty-six station squares are split, so this is real. The honest fix needs the layout's square-aware answer, and every way in to it is `synchronized` — on the event thread, which is the freeze that class was rewritten to remove |

---

## What needs Adam

Four questions. Nothing is blocked on them; each is a decision about what you want rather than about
what is correct.

### 1. Should the editor's "reaches nothing" warning match the runtime's rule? (`V36-C4`)

The editor counts a reached square as somewhere to go when it is a **station**. The runtime requires
`isDestination() && isActive() && isAutoDestination() && !isReversing()`. So the editor's warning is the
looser of the two and under-reports: a copy the runtime considers dead can pass the editor in silence.

**The trade.** Matching them makes the warning fire on more squares. Twenty of your stations carry
`autoDestination: false`, and MT-253 records that this warning fires on exactly one square of your whole
railway today — RampDown southbound. Tightening it could put a warning on a large part of your layout,
and whether those are faults or ordinary is yours to say.

### 2. What shape should "Test Connection" come back in? (`RG3-C4`, MT-257 item 5)

2.8.1 could ask "is there a legal path from this station to that one" with no train involved. The Why
tool is richer but starts from a locomotive, so the question cannot be asked while the railway is empty
— which is when somebody building a setup asks it. You asked the better question back: *"why can't it
keep working without a train, between stations?"* The answer is a choice of shape — two clicks to name
both ends, or one click that answers everything it can about the square it is on — and it is yours.

### 3. Should a route-command deletion say what it removed? (`FV2-C9`, `R28-A1`)

Deleting a locomotive silently removes the route commands that name it. A condition that names it
already logs a line; the command removal, which is authored data, logs nothing. A log line is
behaviour-neutral whichever way you rule, and the wider option `R28-A1` offered — "say how many route
commands will be removed", or revert — is still open.

### 4. Is `ParkingTrack12` in the frozen fixture meant to be both a parking berth and out of service?

You answered this on MT-260 ruling 6 — *"yes, for testing"* — and it is recorded as closed. Listed here
only because the fixture is the one place where two flags say opposite things, and if that ever stops
being deliberate, this is where to look (`DY3-C8`).

---

## What is left

**36 September findings still to settle**, mostly in the 1 September test-suite review (`TCX`) and the
last-day review (`D24`) — test-quality items: floors that pin the wrong quantity, assertions a fixture
guarantees, mutations a fixture cannot tell apart. They are being handled by the test-suite audit
running now.

**The July and August backlog — roughly 190 findings across some twenty-five documents.** They describe
code that has since been largely rewritten, and a good proportion are already fixed without their
document saying so. Nothing in this release depends on them. They are worth a sweep after 3.0.0, and
`traincontrol-c-sweep-state` in my notes records the four disposition conventions a tool has to read to
count them.
