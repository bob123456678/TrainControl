# Three days of commits, second pass

**Status:** open

**Citation prefix:** `D3F`. Cite findings from this document as `D3F-B1`, `D3F-C2`, and so on.

**What was reviewed:** the 89 commits of the past three days, `e54c790b` (2026-08-30 04:07) to
`cf048f9b` (2026-09-02 03:43), on branch `autonomy-diagram-r0` (v3.0.0). One commit landed after this
review's assigned HEAD and before it finished: `54a70c03` (03:52), docs-only - two disposition
corrections. It is noted but was not part of the scope.

**Working tree at review time:** `Readme.md` carries uncommitted changelog edits, and the three
`cs2_sample_layout/config/` files differ from HEAD - the operator's own editing state, the same shape
`D24-D7` recorded. Neither is treated as a finding. Nothing in this review wrote to that folder, and
the one commit in the window that touches it (`a91a6495`, "New graph state") is the operator
committing his own diagram.

**Method:** reading only. No test was run, no JVM was started, nothing was built, and no file was
written except this one. Where a claim below would need a run to settle, it says so.

**Where the effort went, and what was sampled rather than read.** This is the second pass over ground
six reviewers covered on 2026-09-01 up to `b00ac0c1`, so the deep reading went where nothing had
looked yet: the five commits of 2026-09-02 03:17-03:43 (`1cfdf370`, `87b6c10a`, `975f157d`,
`8d1c17ca`, `cf048f9b`) were read diff-by-diff against the code at HEAD, as were the post-fan-out
commits `6f729027`, `9f1b80c8`, `f59fa45e`, `208b3ee1`, `934018f3`, `409d4ce8`, `56c6080e`,
`06516f38`, `434184d9`, `bec51e31`. The 2026-08-30/08-31 commits (the RC round, OB-155..167, the home
rulings, `17cad1fe`) were read at commit-message level only, on the grounds that the `SVN`, `D24`,
`R28`, `TCX`, `CMT` and `RTG` passes covered them line-by-line the following day; nothing here
re-audits `battery.sh` or `one.sh` (TV2 did, and rule 1 forbids exercising them). Test-only commits
were read only where a fix commit claimed a test could now fail. That is the corner-cutting; it is
stated rather than implied.

**Reviewed by:** Claude (Fable), 2026-09-02.

---

## Summary

| | Finding | Status |
|---|---|---|
| B1 | `firstClearRoute`'s new room check is defeated by the search's own memoisation: the longer route its comment promises to keep looking for is pruned before its room is ever measured | open |
| C1 | `checkBadCopies`'s javadoc still argues for the ERROR its body demoted six lines below - restoring what the javadoc says would stop Adam's railway starting | open |
| C2 | `Layout.isPathClear`'s comment still says the reversal-room guard "is inert on his railway today", which `FV2-B1`/`FV2-C2` proved false on 2026-09-01 - and `975f157d` edited the lines directly beneath it hours ago | open |
| C3 | `87b6c10a` widened the start gate to `hasErrors()` and left every offer-side reader on `errorCount()`, so the affordance asks a narrower question than the guard again, and three comments now describe a parity that no longer holds | open |
| C4 | The diagram tile's new protecting-signal warning fires whichever way the click will throw the signal; the route door it was copied from refuses only the green direction. Setting a protecting signal to red - the protective act - now asks "Switch it anyway?" | open |
| C5 | `cf048f9b`'s disposition line for `SVN-A3` cites the wrong commit: the fix is in `1cfdf370`, not `87b6c10a` | open |
| C6 | `check()` now builds the full configuration JSON three times per call, behind call sites whose own comments already describe "four full walks of the railway on the event thread" per right-click. Unmeasured | open |
| D1-D10 | Checks that came back clean, and one finding raised and withdrawn during this pass | - |

No A findings. Nothing read in this window looks like wrong behaviour on the layout or silent data
loss that is not already recorded and dispositioned elsewhere.

---

## B - incorrect results, or crashes in specific configurations

### D3F-B1 - the room check's "keep looking" is defeated by the search's own `seen` bookkeeping

**Status: open.** Structurally real at HEAD; narrow on the operator's data today, and it widens with
every track length he records. Introduced by `975f157d` (TCX-A2), 2026-09-02 03:37.

`HomeStaging.firstClearRoute` records every arrival in `seen` *before* it judges the destination,
`HomeStaging.java:1017-1022`:

```java
                String key = next.getUniqueId() + (turned ? "/turned" : "/straight");

                if (alreadyReached(seen, key, commands)) continue;

                if (!seen.containsKey(key)) seen.put(key, new ArrayList<>());
                seen.get(key).add(commands);
```

The room check added by `975f157d` then rejects with a `continue` whose comment states the recovery it
is relying on, `HomeStaging.java:1045-1049`:

```java
                    // `continue` rather than a refusal: another route to the same berth may be longer,
                    // and a longer approach is more room.
                    Integer room = Layout.measuredRoomToReverseInto(route, loc);

                    if (room != null && loc.getTrainLength() > room) continue;
```

But the rejected arrival has already been recorded. `alreadyReached` (`HomeStaging.java:1140-1162`)
prunes any later arrival at the same key whose commands agree with a recorded one - "dominates" means
every command the earlier route set, the new route sets the same way. A longer approach that rejoins
the same final throat carries the short route's commands plus its own extras, so it is exactly the
kind of arrival domination prunes. The retry the comment promises is cut off by the line 28 rows
above it.

And the pruning does not only bite at the destination. The memoisation treats
*(square, turned, compatible commands)* as a complete state at every intermediate square, which was
sound while acceptance depended only on those three things: a shorter arrival with fewer command
commitments could do anything the longer one could. The room rule broke that equivalence - acceptance
now also depends on the summed length of the path so far, which is in no key - so a longer,
roomier prefix can be pruned at any shared intermediate square in favour of a shorter one whose
completion the room check will refuse.

The contrast that shows the mechanism matters is directly below, `HomeStaging.java:1051-1053`:

```java
                    if (!mustBackIn(loc, to) || turned) return route;

                    // Not this way round.  Keep looking: another route may turn it.
```

That older `continue` makes the identical promise and *keeps* it - because the thing its retry varies,
`turned`, is a term of the key (`/turned` vs `/straight`, and the comment at `:1010-1014` says exactly
why it was put there). The room retry varies a quantity the key does not carry. TCX-A2's rule was
added into a memoised search whose state key does not include the quantity the rule reads.

**Consequence.** `firstClearRoute` answers null for (locomotive, berth) pairs a fully-measured longer
approach serves. Its three callers (`HomeStaging.java:629, 752, 862`) turn that into a berth not
offered and moves not found - `NO_PLAN_FOUND` where a plan exists, which this file's own lock-edges
comment calls "the worst way round for it to be wrong... the vaguest message this can give, after the
longest wait". Not `IMPOSSIBLE`: `connected` deliberately lacks the room rule (the `975f157d` message
says so), so no false proof is generated. `auditAgainstRuntime` would *count* the disagreement -
`getPossiblePaths` enumerates every path, so the runtime side still offers the berth - but it is
"logged rather than enforced" (`HomeStaging.java:597-600`), so nothing repairs it.

**Reachability, honestly.** Every segment of *both* approaches must report a positive length -
an unmeasured segment makes `measuredRoomToReverseInto` answer null and the path is accepted - and the
train must be longer than the shorter approach's sum. On the operator's railway today six tiles carry
lengths, so most alternatives pass through unmeasured track and the fallback works *because* those
paths are exempt. The exposure grows exactly as fast as he follows the editor's own notice, which asks
him for lengths on roughly twenty squares. B rather than C because the failure class - the planner
refusing what the runtime would drive - is the one this file documents as its most expensive, and the
configuration that triggers it is the one the application is actively steering the operator toward.

**A fix should not be picked casually here** - putting the room outcome into the key, or recording
`seen` only on acceptance, each changes the search's cost and completeness in different ways. Flagged,
not fixed, per the briefing.

---

## C - cosmetic, dead code, narrow edge cases

### D3F-C1 - `checkBadCopies` documents the ERROR its own body removed

**Status: open.** One method, javadoc and body in direct contradiction, six lines apart.

`AutonomyChecks.java:749-752` (javadoc, written by `409d4ce8`):

```java
     * An ERROR rather than a warning for the no-way-out case: a train sent there is stuck, and nothing
     * downstream refuses the trip.  The no-way-in case is a WARNING - it costs nothing until somebody
     * leaves a train there, ...
```

`AutonomyChecks.java:763-771` (body, rewritten by `06516f38` two hours later):

```java
        // A WARNING, all three, and the reason is what an error DOES: errorCount() > 0 refuses to
        // start autonomy at all.  Adam asked for "a warning for instances like the previous version of
        // this", and a trapped arrival on a square he has been running for months would have stopped
        // his railway starting the first time he opened this version.
        ...
        Severity severity = Severity.WARNING;
```

`06516f38` demoted the severity - correctly, with Adam's words and the reason recorded - and left the
javadoc arguing the opposite. In this project the comment is the design record: a maintainer who
trusts the javadoc "restores" `Severity.ERROR` for the no-way-out case, and the body's own comment
says what that does - it stops the operator's railway starting over berths he has run for months. The
fix is one paragraph of javadoc.

### D3F-C2 - "It is inert on his railway today" survived being proven false, through a commit that edited the lines beneath it

**Status: open.**

`Layout.java:2386-2391`, inside `isPathClear`'s reversal-room guard:

```java
                // Left as it is on purpose.  It is inert on his railway today (six tiles carry lengths
                // at all), and a guard that is occasionally over-strict on a measured layout is a
                // nuisance, ...
```

`FV2-B1`/`FV2-C2` (2026-09-01, fixed in `c9153aaf` under the title "a guard that is not inert")
established the opposite and the fan-out index records it: `setup.json` maps page id 5 to `1 - Main`,
all six measured tiles are on the main page, two of them (`BottomMainB`, room 4; `BottomMainC`, room
2) are reversal squares, and 42 of the 54 locomotives with a recorded train length exceed room 2. The
guard is live behaviour on his railway. `c9153aaf` corrected the index, `AutomationAPI.md` and
`MT-248` - and not this comment, which is the one at the guard itself. Then `975f157d`
(2026-09-02 03:37) rewrote the lines immediately below it (`Layout.java:2393-2398`, the
`measuredRoomToReverseInto` extraction) and carried the false sentence forward untouched.

A reader trusting this line concludes the guard is dormant and defers thinking about it - which is
precisely the reasoning error the deferral itself made and the validator caught. Same fix class as
C1: one clause of comment.

### D3F-C3 - the start gate widened to `hasErrors()`; every offer-side reader still asks `errorCount()`

**Status: open.** The narrow limb of the very shape `87b6c10a` fixed three instances of.

The gate, `TrainControlUI.java:5183` (changed by `87b6c10a`):

```java
        if (!getAutonomySession().hasErrors()) return false;
```

where `hasErrors()` is `hasBlockingProblems() || errorCount() > 0` (`AutonomySession.java:3572-3576`),
and the gate's own comment says the disjunction is live: "It can legitimately be zero while this
refuses: a graph that cannot be BUILT is a blocking problem" (`TrainControlUI.java:5180-5182`).

The offers were not widened with it:

- `TrainControlUI.java:20156-20159`:

```java
    public boolean canStartAutonomy()
    {
        return this.startAutonomy != null && this.startAutonomy.isEnabled()
            && autonomyErrorCount() == 0;
```

- and three comments now describe a parity that no longer holds:
  - `TrainControlUI.java:20165-20166` (`autonomyErrorCount` javadoc): "`refuseAutonomyStartWhileBroken`
    reads exactly this and refuses when it is not zero" - it reads `hasErrors()` now, and the
    `errors == 0` message branch added in the same commit exists precisely because it can refuse at
    zero.
  - `LayoutRightclickAutonomyMenu.java:181-183`: "canStartAutonomy asks
    refuseAutonomyStartWhileBroken's own number now... the control that OFFERS an action asks the
    predicate the guard asks." True when written; not since 03:27.
  - The right-click tooltip (`LayoutRightclickAutonomyMenu.java:203-208`) falls back to
    "waiting for trains" whenever `errorCount()` is zero - in the divergent state it would name the
    wrong reason.

**Reachability is narrow and should be said plainly:** the divergent state needs
`hasBlockingProblems()` true while `errorCount()` is zero, which per `errorCount()`'s own javadoc
(`AutonomySession.java:3540-3542`) happens only when `check()` answers empty because "the graph has
not been derived yet" (`check()` bails on `graph == null || reducer == null`, while
`hasBlockingProblems()` needs `graph != null`). The gate handles it with a correct message; the harm
is a menu item offered live whose press explains a refusal, which is the OB-050 pattern the codebase
spent three findings eliminating, plus three comments a reader will now be misled by. C, not B.

### D3F-C4 - the tile door warns about a protecting signal in both directions; the route door refuses only green

**Status: open.**

The rule `87b6c10a` unified has an aspect condition at one door and not the other. The route door,
`MarklinRoute.java:475`:

```java
        if (!rc.getSetting() && this.network.getAutoLayout().protectsAnOccupiedSquare(accessory))
```

`getSetting()` is false for GREEN (the comment above it says so), so a route is refused only when it
would turn the protecting signal *green* - the inviting direction. The diagram's accessory tile,
`LayoutLabel.java:400-411`, asks with no aspect term at all:

```java
                                        boolean protecting =
                                            tcUI.getModel().getAutoLayout().protectsAnOccupiedSquare(
                                                c.getAccessory())
                                            || (c.getAccessory2() != null
                                                && ... protectsAnOccupiedSquare(c.getAccessory2()));
```

A tile click toggles the accessory (`component.execSwitching()`, `LayoutLabel.java:570`). Clicking a
protecting signal that currently shows green - to put it back to red, which is the protective act
itself, and the recovery gesture after the exact hazard this guard describes - now raises
"This signal is protecting a platform a train is standing at. Switch it anyway?". The direction the
toggle will take is knowable at the check site from the accessory's current state, exactly as the
route door knows it from `rc.getSetting()`.

It is a confirmation with a way past, not a refusal, so C - but it is an over-strict question at a
door the operator uses while recovering, which is the class Adam has pushed back on before
(the guards-need-a-way-past rulings), and the new comment's claim that "this and
MarklinRoute.heldReason cannot drift apart" (`LayoutLabel.java:398-399`) is only true of the
occupancy half; the aspect half drifted in the same commit that wrote the sentence. Note the Keyboard
tab (`SVN-B17`) stays outside this family entirely and is already an open finding - not re-filed here.

### D3F-C5 - SVN-A3's disposition cites the wrong commit

**Status: open.** One-line fix in the week-of-commits document.

`docs/reviews/2026-09-01-week-of-commits-review.md:194` (written by `cf048f9b`):

> **FIXED 2026-09-02 (`87b6c10a`).** Confirmed on both limbs by reading the three methods end to end...

The SVN-A3 fix - `layoutRefreshComplete` / `layoutRefreshCompleteInternal`, the worker's finally, the
once-wrapper in `layoutEditingCompleteThen` - is in `1cfdf370`, not `87b6c10a`; `git log -S
layoutRefreshCompleteInternal` names exactly one commit. `87b6c10a` is the guards commit and touches
none of those methods. Every other disposition line spot-checked (17 of them) names the right commit;
this one sends the next auditor to the wrong diff, and the README's whole receipts discipline exists
because references like this get followed.

### D3F-C6 - `check()` now builds the configuration three times per call, on call paths already documented as expensive

**Status: open, unmeasured.** Filed as a cost observation for the deferred-optimizations list, not as
a defect with a demonstrated symptom.

`badCopies()` (`AutonomySession.java:1993`) and `destinationCopiesReachingNoStation()`
(`AutonomySession.java:2113`) each call `builtForInspection()` - a full
`buildConfigurationForInspection()` parse - plus `builder(null).tilesByName()`, and the reaches-no-
station check runs a BFS over the built edge set per station copy. `check()` calls all three
(`AutonomySession.java:3521-3525`), so one `check()` is now three full graph builds plus a
reachability sweep. The call paths multiply it: `LayoutRightclickAutonomyMenu.java:184-189` already
says of the *pre-existing* cost, "four full walks of the railway on the event thread, every time
somebody right-clicks a station", and `canStartAutonomy()`/`autonomyErrorCount()` both bottom out in
`check()`; the overlay strip asks `autonomyErrorCount()` on every findings refresh
(`AutonomyOverlayToggle.java:342`); and `87b6c10a`'s gate calls `hasErrors()` then `errorCount()`,
two `check()`s per refused start. On a 62-square graph this is probably tolerable; the trajectory -
each new copy-level check adding a full build to every EDT walk - is the thing worth a line in the
deferred list. No caching is suggested here without measurement.

---

## D - not defects: things that look wrong and are not, withdrawn findings, and clean checks

### D3F-D1 - WITHDRAWN: "the emergency-stop gate disarmed the s88 door's whole-route refusal" (raised at B, wrong)

Raised while reading `6f729027`: `conflictingAccessoryAndReason()` now answers null for any
stop-carrying route, and I believed the s88 door's whole-route accessory skip consumed that method -
which would have turned "refused whole" into "set every accessory ahead of the conflict and drop the
rest" for precisely the safety routes that carry stops, contradicting the rule at
`MarklinRoute.java:532-536`. It does not: the s88 door computes its conflict from
`accessoryHeldByAutonomy()` directly (`MarklinRoute.java:567`), which has no stop gate, so
`skipAccessories = auto && conflict != null` behaves exactly as before. The gate lives only on the
pre-flight question (`:362`) and the midway human question (`:664`), which are the two places Adam's
ruling is about. Withdrawn after reading the layer the claim was actually about - kept here with its
original severity because it is the calibration the README asks for.

### D3F-D2 - `6f729027` (emergency stop, delete warning): clean at every door

All three `askAboutRouteConflict` callers traced (`LayoutLabel.java:550`,
`TrainControlUI.java:16150, 24411`); the midway path's else-branch logs, sets `skipAccessories`, and
continues, so a stop route skips its conflicting ironwork and the stop still fires; the `known`
parameter is only ever non-null from `confirmRouteConflictMidway`, which is itself gated by
`!hasEmergencyStop()`. The delete-confirmation counts commands only, before deletion, and both
message keys are present with matching placeholders in all eight bundles. The second delete door
(rename-over-existing, `TrainControlUI.java:22367`) funnels through the same confirmation.

### D3F-D3 - `8d1c17ca` (SVN-B13): clean

The new square rule mirrors `claimHome`'s, uses the same `isSamePlaceAs` predicate (defined once, on
`Point`, for exactly the reason its javadoc gives), drops the loser with its own warning, and the
message key is in all eight bundles, `\uXXXX`-escaped, three placeholders in the right order. The
other `setHomeLoc` writers were swept: the assignment door displaces (`Layout.java:1260-1268`),
`parseAuto` feeds `rebuildHomeStations` which now guards, `clearHomeLocomotives` clears everything
anyway.

### D3F-D4 - `1cfdf370`'s SVN-A3 rework: clean end to end

The once-wrapper cannot run the continuation twice (`AtomicBoolean`, and the catch rethrows); the
no-worker path is covered; `layoutEditingComplete(Runnable)`'s direct callers
(`TrainControlUI.java:21631, 21859`, plus the no-arg form) keep their old ordering - the continuation
always did run after the dozen statements, it is now guaranteed by the finally rather than by luck.
The autonomy-mode branch (`LayoutEditor.java:5588-5605`) has its own finally and
`autonomyEditorClosed()` is synchronous, so the sibling door does not have the fault this commit fixed
on the track-mode one.

### D3F-D5 - the five "tests that could not fail" (TCX-B5/B6/B8/B9/B13): each repair read, each now discriminates

By reading, not by running - stated per the method. TCX-B5 gained the marked-tile control with the
vacuity named in its message; TCX-B6 split `parking` from `shut` in `badgeAt` and asserts their ink
differs (`inkOf` counts non-transparent pixels, so a shared colour does not defeat it); TCX-B8 asserts
the sequential flag with a precondition assert; TCX-B9 demands `copies.size() >= 2` on a fixture that
genuinely splits; TCX-B13's mutation note documents the control that catches refuse-everything.

### D3F-D6 - the bundle sweep: clean, and the Italian lesson was not re-learned the hard way

No line added to any of the eight bundles in the whole three-day window carries a straight apostrophe
together with a `{n}` placeholder (checked over the full `e54c790b..HEAD` resources diff). The keys
added *after* `56c6080e`'s Italian fix (`checkCopyReachesNothing`, `infoNoHomesToClear`,
`infoHomesCleared`, `warnHomeSquareAssignedTwice`, `confirmAccessoryProtecting`,
`infoAlreadyRunning`) all use `’`. Everything is ASCII with `\uXXXX` escapes.

### D3F-D7 - `434184d9` and `bec51e31`: clean

The "Can Be Chosen in Full Autonomy" enablement drops only the `isOpen` term and keeps `isStation`,
matching the ruling quoted at the site. OB-168's `takeTheKeyboard()` is reached only from
`display()`, which has exactly one caller (`MarklinControlStation.java:3957`, startup), so the
`toFront()` cannot steal focus during ordinary use. OB-169's guard skips only the press-side pick-up,
only while `placingFromPalette()`, only on a grid square; a Ctrl+C copy from the diagram reports real
coordinates and is deliberately outside it.

### D3F-D8 - SVN-B14's twin sweep: no unswept sibling

`RightClickFunctionMenu.java:154-155` also writes the slots immediately, but it is the direct
menu-tick door - an action item with no OK/Cancel to honour - and `GraphLocAssign.java:253-254`
applies at commit, as the fix's comment says. The restore covers close-box and Escape as well as
Cancel (plain `else` on `doApply`).

### D3F-D9 - D24-B5's claim verified at the enforcing layer

`isPathClear` really does refuse an inactive *intermediate* point unfenced by `isAutoRunning`
(`Layout.java:2278-2287`, with the comment explaining why passage is the absolute case), and
`HomeStaging.canEnter`'s first line refuses inactive points, so the newly-emitted `active:false`
reaches both the runtime and the planner. The badge fix reuses one `shut` local for both Badge
arguments (`AutonomySession.java:4705-4707`), so the running diagram and the flag cannot disagree.

### D3F-D10 - `975f157d`'s extraction preserves `isPathClear`'s behaviour

`measuredRoomToReverseInto` re-checks inside itself exactly the conditions its call site already
guards (`loc`/length null-or-zero, empty path, terminus-or-reversing ending), and the loop is the old
loop verbatim - unmeasured means null, not zero. The `isPathClear` side of TCX-A2 is
behaviour-preserving; the planner side is D3F-B1.

---

## What this pass did not settle

- Whether D3F-B1 is demonstrable on a fixture is a question for a test written by whoever validates
  it: a berth with two fully-measured approaches sharing their final throat command, a train longer
  than the short approach, shorter than the long one. This review claims the mechanism from the code,
  not a reproduction.
- D3F-C6 is a cost trajectory, not a measured regression. It belongs on the deferred-optimizations
  list unless a right-click on a large layout is already felt.
- The 2026-08-30/31 commits were sampled, not re-read; the 2026-09-01 fan-out documents are the
  line-by-line record for them, and nothing found here contradicts their dispositions except the one
  receipt in D3F-C5.
