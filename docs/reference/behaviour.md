# What the railway does, and why

This is the intended behaviour of the features that decide where trains may go and what happens when
they get there. It is written to be **read against the code and disagreed with**: where this document
and the program differ, one of them is a bug, and Adam decides which.

Every rule below is Adam's, quoted where the wording matters. Rules that were argued over carry the
date and the case that settled them, because several were reversed once and a rule without its reason
gets reversed again by the next person who finds it inconvenient.

**What this is not.** It is not a description of the code's structure, and it is not a changelog. It
says what the railway should do. Where a rule has a known limit — something it deliberately does not
catch — the limit is stated, because a limit nobody wrote down reads as a bug the day it is met.

**How this relates to the comments in the code.** Adam, 2026-09-07: *"we should make comments in the
code to strive to be authoritative and self-contained, with the behaviour document being the
documented intended functionality."* The division: **this document holds the intent**, and a comment
explains why the code in front of you implements it the way it does. A comment stands on its own - a
reader who has never opened a review document should be able to understand it - and where it cites a
finding id, that is provenance rather than the explanation. The full rule is in
[`docs/reviews/README.md`](../reviews/README.md).

---

## 1. The three tiers: autonomy, manual, Return Home

Three things move trains, and they do **not** obey the same rules. Most of the confusion this
document exists to prevent comes from assuming they do.

| | chooses its own destinations | may go where autonomy is told not to | asks the operator anything |
|---|---|---|---|
| **Autonomy** | yes | no | never |
| **Manual** (right-click, Locomotive tab) | no — the operator chooses | yes | yes, about reversals |
| **Return Home** | no — the planner chooses | **yes** | never |

The middle column is about the **parking marking** — *Can Be Chosen in Full Autonomy* — and nothing
else. The occupancy restriction below is not a tier question at all any more.

**Return Home sits with Manual on the question of where a train may be sent.** This was got wrong
once and corrected on 2026-09-06: `isAutoDestination` appears nowhere in `HomeStaging`, and Adam's
earlier ruling stands — *"Return Home still fills it; only full autonomy leaves it alone."* The Path
Type control in the editor says Manual for this reason.

**Occupancy restrictions bind every tier** (Adam, 2026-09-10). A station can be marked unavailable
while some other named square has a train standing on it, and **autonomy, a hand-driven send and
Return Home all obey it**. His ruling:

> *"If it's cleaner to go with consistency across the board, then let's enforce the occupancy ruling
> in all modes and then rely on isPathClear. Revert the prior lax ruling."*

**It was fenced twice before, and this is the record of both.** It stood behind `isAutoRunning` until
2026-09-09 and behind `isFullAutonomyRunning` after it, on his earlier ruling that the restriction is
*"for modifying pathing prioritization"* while the length checks are *"our primary anti collision
mechanism."* Both fences are gone. One rule, one answer, asked once — in `Layout.isPathClear`.

**The planner reads it again, and has to.** `HomeStaging` stopped applying it when the fence narrowed,
precisely so it would not offer a leg the runtime refuses; with the fence gone it would offer exactly
that, and a plan whose first move the railway refuses is OB-073 — the run retries until it gives up
and stops with the fleet half-staged. Its copy asks the occupancy the **plan** has reached rather than
the live railway (`HomeStaging.plannedOccupancy`), and `auditAgainstRuntime` is what proves the two
agree.

**Two consequences worth knowing.**

- **The train leaving the watched square is still exempt** — Adam: *"the condition should not apply to
  trains leaving, only departing"* — and that exemption is what keeps the rule from being a trap: a
  locomotive parked on the yard could otherwise never be sent to the platform the yard holds back,
  and while it sat there the platform would be shut to everybody else too.
- **Two stations that hold each other back are now a real deadlock.** Whichever train arrives last
  finds its station closed by one already parked, and no order of moves avoids it. Return Home reports
  `NO_PLAN_FOUND` — not `IMPOSSIBLE`, which names locomotives and asserts that no arrangement exists.
  The scan that used to produce that verdict was wrong three separate times and was removed rather
  than repaired, so the weaker true answer is the one that stands.
  `core.testHomeStaging.testTwoHomesThatHoldEachOtherBackAreADeadlock` pins it.

**"Full autonomy" is still narrower than "autonomy is running", and the distinction survives this
change.** Executing a timetable sets the same running flag, and a Return Home run *is* a timetable, so
a rule fenced behind that flag applies to Return Home as well. `Layout.isFullAutonomyRunning` — running,
with no timetable driving it — exists for that; nothing asks it today, and it is kept because the next
rule that needs the distinction will need it spelled correctly.

**How many trains may be out at once is a cap, and it binds only while the railway is running
itself** (Adam, 2026-09-09: it *"stays enforced only under full autonomy"* — no change asked for).
The Autonomy tab has a slider, `Layout.maxActiveTrains`, from 0 to 20; **0 means no cap**, which is
the default. A path is refused with `errorMaxActiveTrainsExceeded` when the number of distinct
locomotives underway has already reached it — counted as the UNION of the registered ones and the
ones that have claimed a path but not yet set off, because the gap between claiming and being
registered is a per-accessory wait seconds wide and two trains crossed it together.

- **A hand dispatch is exempt.** Right-clicking a destination sets no running flag, so it is neither
  counted nor refused, however many trains a run already has out. That is the tiering above: the cap
  is a preference about how much railway the operator wants moving at once — what a booster will
  carry, or how much they want to watch — rather than a fact about what the track will hold. The
  anti-collision guarantees are the edge locks and the length rules of §5, which every tier obeys.
- **The fence is `isAutoRunning`, which is wider than "full autonomy".** `executeTimetableInternal`
  sets the same flag, and a Return Home run *is* a timetable, so **a timetable and Return Home ARE
  capped**. If Adam's *"only under full autonomy"* is meant literally, the fence is the thing to
  change, and that is a behaviour change nobody has asked for.

  This bullet used to contrast the cap with the occupancy restrictions, which asked
  `isFullAutonomyRunning`. There is nothing to contrast it with any more: those bind every tier since
  2026-09-10, and **the cap is now the only rule in this document with a tier fence on it**.
- `core.testMaxActiveTrains` is the class, and
  `core.testMaxActiveTrains.testTheCapDoesNotBindAHandDispatch` is the one that goes red when the
  fence is removed; the other three all start by running the railway and would not notice.

**One difference remains between Return Home and a manual send**, and it is deliberate (confirmed
2026-09-07): Return Home refuses to start a train standing on an **inactive** square, where a manual
send allows it. The exemption exists for a person who has looked at the railway and decided to move
that train now; Return Home is a plan made for every staged locomotive at once, and quietly driving
out of a square somebody switched off is the opposite of what switching it off meant.

---

## 1a. How autonomy chooses: priority, the routing rule, the yield and the pauses

Everything in this section is about **full autonomy**. A hand dispatch and Return Home do not choose -
the operator or the planner does (section 1) - so none of it applies to them.

Each locomotive on the run list has a loop of its own: choose a journey, drive it, pause, choose again.
**What follows only ORDERS the journeys the rules of this document already admit.** A candidate is a
station autonomy may send a train to (`Layout.isSendableDestination`: active, a destination, choosable
in full autonomy, not a reversing point), with nothing standing in its block, not a terminus for a
train that cannot reverse, and not a station that excludes this train; and a route to it is used only
if it turns the train nowhere on the way and `isPathClear` passes it. No routing rule can admit a
journey any of that refuses.

**Station priority is absolute, and it is the default behaviour.** Every station has a whole-number
priority, 0 unless set - clearing the box means 0. The candidates are shuffled and then sorted highest
priority first, and the sort keeps the shuffle within a priority, so equals are chosen between at
random. A lower priority is looked at only when nothing of a higher one has a clear route. So *"send
it to the highest-priority station it can reach"* needs no setting of its own: it is what happens.

**The routing rule decides between the stations of that one priority** (`Layout.PathPreference`,
the Autonomy tab's routing dropdown). It is stored in the configuration rather than in the program's
preferences, so it travels with the railway it was chosen for (Adam: *"that way it travels with the
config, not the UI."*).

| rule | chooses, within the highest priority that has a clear route |
|---|---|
| **At random, respecting priority** (`RANDOM`) - the default | the first clear route found; the shuffle makes that random. The default because it is what every earlier version did |
| **Completely at random** (`RANDOM_ANY_STATION`) | the first clear route found, **with priority ignored** - a station at -5 is visited as often as one at 9 (OB-156, Adam: *"one completely random, and one that respects priority"*) |
| **Fewest / most stations** | the route passing the fewest, or most, OTHER stations |
| **Shortest / longest track** | the route over the least, or most, measured track - and a section with no length counts ONE, so with nothing measured it is the route over the fewest, or most, sections (Adam: *"a min length option that tries to minimize total track length, where we count each s88 as length 1 by default"*; `Layout.lengthOf`, DCN-B2) |
| **Fewest / most sensors** | the route over the fewest, or most, distinct s88s - sensors, not hops of the graph, because a square is several Points |
| **Least recently visited** | the station that has gone longest without a train arriving; one never visited wins outright |
| **Balanced priority** (`BALANCED_PRIORITY`) | the most priority per unit of track - **the one rule that crosses priorities**, so a nearer, less important station can beat a far important one (Adam: *"one that balances priority vs distance as a ratio"*). With every priority left at 0 it is the shortest route with extra arithmetic |

The "most" rules are not a joke: a railway somebody is watching wants its trains taking the long way
round, where a timetable wants them going straight there.

**A train that has gone quiet is given a turn** (`maxLocInactiveSeconds` on the Autonomy tab; 0, the
default, switches it off). After each attempt, while full autonomy is running, a train checks whether
another train on the run list - not paused, and with somewhere autonomy could send it - has gone that
many seconds longer than itself without a journey. If one has, it waits up to thirty seconds
(`Layout.YIELD_SECONDS`) for that train to move before choosing its own next journey. It is a courtesy
and not a queue: nothing stops the waiting train from setting off at the end of the thirty seconds.

**The pauses are two numbers in seconds, a minimum and a maximum**, and neither may be negative or
cross the other - the setter refuses both. They are used two ways:

- **after every attempt**, found or not, a train waits the MINIMUM before choosing again;
- **where a pause is meant to look like a person did it** - before a departure's functions, either side
  of an arrival's functions, before the change of direction at a terminus or a reversing point on the
  way, and when there was nowhere to go - it waits a random whole number of seconds between the two.

So a train that finds nothing waits a random pause and then the minimum. With the minimum at 0 a train
with nowhere to go still waits a quarter of a second before searching again (`NO_PATH_IDLE_MS`), or it
would search the whole graph as fast as the processor allows. And a pause is skipped once the run has
been asked to stop, because after a stop there is no next journey for it to space out.

**How many trains may be out at once is section 1's cap**, and it binds here as well as there.

*(OB-265 recorded that this section did not exist. The per-rule explanations are the dropdown's tooltip, for the rule chosen (OB-163, fixed 2026-08-30).)*

---

## 2. Inactive squares

> *"Inactive really means nothing can pass."* — Adam, 2026-09-06

An inactive square is refused as a **destination** and as an **intermediate** point, in every tier.

**The square a train is already standing on is exempt.** Switching a square off around a train is how
you take it out of service; the train then has to be driven off it by hand, and a rule that refused
that would strand it.

---

## 3. Reversals

### Where a train turns round

A square is several `Point`s — one per side a train can arrive by — and **which copy a train is on IS
its direction**. Three kinds of square matter:

- **A terminus.** The train has run out of track. It turns, and nobody is asked. A train that
  *cannot* reverse may still **back into** a terminus, when the route there turns it round
  (Adam, MT-245).
- **A compulsory turn** (`mustReverse`). Every copy turns trains. Not a question in any tier: a
  turning copy's only outgoing edges leave by the side the train arrived from, so *not* turning is
  not an available outcome — it drives the train forward off its reserved path.
- **A may-reverse square** (`canReverse` on the tile). The build splits it into a plain copy and a
  turning one. Autonomy turns only where the setup says. **A manual send asks** - but not
  unconditionally, and this bullet read "Manual always asks" until 2026-09-08. *The question, and when
  it is asked* below is the authority, and it has carried at least one exception since 2026-09-07: a
  journey ending at a terminus is not asked about at all, and `ManualReversalPrompt.forJourney`
  returns before the dialog for one.

**A terminus is where a line ends; a compulsory turn is a rule about what happens on arrival.** The
distinction is what each is *for*, and only one of the two says anything about who may send a train
there:

| | autonomy sends trains to it | every arrival turns | `isDestination` | `isAutoDestination` |
|---|---|---|---|---|
| **Terminus** | yes - it is the end of a line, and somewhere to go | yes | **necessarily** | yes |
| **Compulsory turn** (`mustReverse`) | **yes, unless the square is ALSO marked one autonomy may not choose** | yes | **yes** | **independently** |
| **May-reverse** | yes, if it is also a destination | only when chosen | independently | independently |

**This row has been wrong twice, in opposite directions, and Adam settled it on 2026-09-09.**

It said "not a destination" until 2026-09-08, which named the wrong one of two flags that sound alike
(MON-C14). Measured on Adam's railway: every compulsory-turn square he has builds to `terminus=true`,
which makes it a destination, and that is right. They are his parking berths. He sends trains to them
by hand and homes locomotives there.

It then said a compulsory turn is never somewhere **autonomy** chooses. **The code does not say that
and was never asked to.** `AutonomyBuilder` writes `autoDestination:false` for exactly one marking -
the parking one (`manualOnly`) - and for nothing else; a compulsory turn that stops is emitted as a
terminus, and `Layout.isSendableDestination` (`isDestination && isActive && isAutoDestination &&
!isReversing`) admits a terminus. So autonomy leaves Adam's parking berths alone because **he marked
them parking**, not because they turn trains. His ruling:

> Marking a square as a compulsory turn says what happens when a train ARRIVES, not who may send one
> there.

**Keeping the two separate is deliberate.** They answer different questions and a layout may want
either without the other: a turning square autonomy is welcome to use is one marking, and a berth
autonomy must leave alone is the other. Where both are wanted - which is every square on Adam's
railway that turns trains - both markings are made, and that is why the ledger has looked as though
one implied the other.

`isDestination` means "a place trains stop"; `isAutoDestination` means "a place autonomy may pick".
The same two flags were confused in code one finding earlier on 2026-09-08 (MON-C5), which is a fair
warning about how easily they read as synonyms.

The code enforces the terminus row in both directions: a terminus **must** be a destination, so
`setDestination(false)` clears `isTerminus` and a copy trains may not arrive at is emitted as a plain
reversing point rather than as a terminus.

**One surface was wider than this, and OB-195 narrowed it on 2026-09-09.**
`AutonomySession.stationsAutonomyWillNotChoose` - which decides the magenta leg colour and the Auto
tier's "reachable and never chosen" notice, both in section 7 - counted a compulsory turn as one
autonomy will never choose whether or not it was also parking, and its own comment claimed to BE
`isSendableDestination`. Adam ruled that the editor should narrow, and it did: the set asks
`isStation && !isAutoDestination` and, since E8-B1, `isActive` as well - the two clauses of the runtime
rule that a SQUARE can answer. Until 2026-09-23 the two could not disagree on his railway, because every
compulsory turn on it was also parking, which is exactly why it went unnoticed; that day he made
BottomMainC a compulsory turn that autonomy may still choose. The ruling is below, under *Turning
round, and being chosen*.

`testACompulsoryTurnIsAnAutoDestinationExactlyWhenItIsNotParking` asks it of the real railway, which
now has both kinds: every compulsory-turn copy is an autonomy destination exactly when its square is
not marked parking, in the runtime and in the editor's set alike.

**Which matters for length** (§5): the track-room rule has no terminus requirement and no
destination requirement, so a compulsory turn a train does not physically fit into is still refused.
The station-capacity rule gates on being a destination, and has nothing to say about a square nobody
calls a station. Adam, 2026-09-07: *"the terminus that isn’t a destination should fail on the track
length check - the station length can safely be ignored."*

**"The flag" is on the tile, not on the train.** `canReverse` is a property of the square in the
autonomy setup: *may a train turn round here.* It is set by marking the tile in the editor. It is not
`Locomotive.isReversible`, which is a property of the **train** - *is this a locomotive that can run
in either direction at all* - and the two are asked in different places for different reasons. A
non-reversible train may still back into a terminus (MT-245); a reversible train may still be refused
a turn at a may-reverse square, because the operator said no.

**On the two copies.** Adam: *"having two copies seems like unnecessary complexity, I wonder if it can
be done more easily by simply following the edges? Don’t implement until evaluating."* The evaluation
is written, in [`two-copies-evaluation.md`](two-copies-evaluation.md), and nothing has been changed.

Its headline said *"on this railway the split does not fire"* - every named square building to exactly
one copy - **and that was measured on a diagram whose switches had no accessories, so the railway was
in pieces** (corrected 2026-09-08). On the wired reduction, **25 of the 58 squares build to more than
one Point and 9 of the 33 stations do** (recounted 2026-09-23; 30 and 13 before Adam's one-way marks
and BottomMainC's compulsory turn); `BottomMainPost` and `RampDown` are four Points each. `core.testEverySquareBuildsToTheCopiesTheSetupImplies` is the census and the
tripwire.

What survives the correction: following the edges is a real option - it moves the same state from the
graph into the search - but it would not remove the facing property, only the specific failure of
picking the wrong copy, and it touches everything that reads the graph. What does not survive is the
evaluation's first reason for waiting, *"it buys nothing on your railway today"*. **Whether that
changes the recommendation is Adam's call and nothing has been done about it.**

**The tripwire was cited four times before it was written** (found 2026-09-08).
`testEverySquareOnThisLayoutBuildsToOneCopy` was named as the guard here, twice in
`docs/reference/two-copies-evaluation.md`, and once in
`test/core/testTheAutoTierScopeMatchesTheRuntime.java`, and there was no such class or method anywhere
under `test/`. `regression.testEveryCitationResolves` resolves review-finding ids and not test names,
so nothing caught it - and the first thing the real test did when it was written was disagree with the
number all four sentences were repeating.

### The question, and when it is asked

> *"May reverse should always prompt in manual mode. It should be a yes/no keep direction, with no
> meaning change, and yes being default."* — 2026-09-06
>
> *"Make it be on departure itself, that way there is no dispatch prior to user input."* — 2026-09-06

- Asked **once per journey**, **before the train moves**, from both manual doors (the track diagram
  and the Locomotive commands tab).
- The dialog names the **destination** when that is one of the squares in question, because *"send a
  train to a may-reverse point"* is the case the feature exists for.
- **Yes = keep the current direction**, and it is the default. Escape, the window's X, and a dialog
  that cannot be shown all mean keep. Only an explicit **No** turns the train.
- **A journey ending at a terminus is not asked about at all**, because the answer would be
  discarded: the turn on the way in is how a train backs into a terminus.
- Autonomy and Return Home are never asked.

> *"Manual only reverses if the user explicitly said it via the popup, unless you're going to a
> terminal."* — Adam, 2026-09-07

**A manual journey that needs a turn the operator declined is refused before it starts**, naming the
square. A manual path may route through the turning copy of a may-reverse square, and a turning copy
leaves only by the side the train came in at — so a train that does not turn there runs on onto track
its path does not hold. The answer is always honoured; the journey that depends on a different answer
is not started. A terminus is exempt.

**Only the destination is asked about** (Adam, 2026-09-07: *"it is unnecessary to prompt on
intermediates - we care about the reversal if it’s the destination, since that dictates where the
train can go, and where it is facing"*). Intermediates turn as the path requires, exactly as they do
for autonomy.

That ruling **dissolved** the refusal described above rather than qualifying it: with nothing asked
about intermediates there is no declined turn for a journey to depend on, and the stranding refusal,
its helper, both door checks and its message in all eight bundles were removed the same day.

**No journey is refused on the operator’s answer any more, and none can be** — a claim to the contrary
stood here until 2026-09-07 and described a case that cannot arise.  Declining leaves the train
exactly as it drove in: at the *plain* copy no turn was needed to complete the journey, and at the
*turning* copy the train is re-stood on the copy it did not turn on (PRV-B2).

**Every copy of a may-reverse square is asked about, the turning one included** (UIX-C1, 2026-09-19).
The sentence here used to say the opposite - that a journey ending on the turning copy ends at a
terminus and a terminus is never asked about - and the code has not worked that way since PRV-B2
made "declined on the turning copy" a coherent outcome.  The door exempts a terminus only where the
policy does not ask about it, and the manual policy asks about every copy of a square the setup marks
as may reverse, which is Adam's own ruling three paragraphs above: *"May reverse should always prompt
in manual mode."*  `core.testNonReversibleTrains.testEveryCopyOfAMayReverseSquareIsAskedAbout` and
`core.testTheArrivalHonoursTheAnswer.testKeepDirectionIsHonouredAtAMayTurnSquare` hold it.

### What the runtime cannot answer

`canReverse` never reaches the running layout — `AutonomyBuilder` expresses it by splitting the
square and says so. So the door supplies the "is this square one the operator has a say over"
question from the **setup**, and nothing in the automation layer can answer it. A policy that
answers it from `isReversing()` cannot tell a may-reverse square from a compulsory turn, and that
mistake has been made in both directions.

### Direction commands

- **Changing the direction on the graph does not command the track.**
- **A direction command from the track DOES update the graph**, if it disagrees — while nothing is
  running. What happens to one that arrives mid-run is the next paragraph, and it is the opposite of
  what this bullet said until 2026-09-07.
- A reversal at a may-reverse square emits exactly the same command a terminus does.

> *"The arrival writes the graph - but if a manual command is sent, ignore it, as this is likely
> corrective by the user."* - Adam, 2026-09-07

**A direction command arriving while a run is under way is ignored, not queued.** Reversals are
counted only when nothing is running, and there is no backlog.

How that is achieved is worth stating exactly, because an earlier version of this paragraph got it
backwards. `putIfAbsent` is still there and is still right: it gives a train nobody has seen before a
baseline and nothing more, so a locomotive first met mid-journey is not treated as having changed
direction. What stops the backlog is the other half - `reconcileFacingWhenIdle` brings the baseline
level with the live state once the railway is idle, so nothing arriving mid-run is left behind to be
replayed. Neither half works alone: without the baseline the first echo is skipped, and without the
levelling the first echo after the run re-follows a reversal the railway already made.

> *"It should be recorded at the destination. Otherwise, it’s just the same as always."* — Adam,
> 2026-09-07

**A turn the railway itself makes at the destination is the exception, and it records itself.** It is
not news arriving late — it is something this application did on purpose and knew about as it did it —
so the arrival writes it down and the window applies it to the graph the next time the railway is
idle. Without that the two rules above hide it between them: the echo lands inside the run’s own pause
while it is still running, and the levelling then wipes the evidence. The physical train would be
reversed, the graph would say otherwise, and the next dispatch would offer paths for the wrong
heading.

**What is recorded is the SQUARE the train turned at, and what is written is the way it came in.**
Both halves were different until 2026-09-09, and between them they are OB-190. The record was a set of
names whose membership was a *parity* — two turns cancelling — because the window applied each one by
**flipping** the facing the setup had stored for that square. §6a says as a rule that the stored
facing is stale at exactly that moment: a run moves trains, and nothing writes where they ended up
back to the setup. So the pivot was either missing, and the turn was written nowhere, or it was the
previous occupant’s answer, and the turn was written **backwards** — with the train then stood on the
copy for a facing it does not have.

The answer that needs nothing to be in sync first is the train’s own arrival side. **A train that has
just been turned round faces the way it came in** (§4), the arrival wrote that side onto that very
`Point` before turning it, and the window now writes it as it stands rather than deriving it from a
record. An absolute answer applied twice says the same thing twice, so there is nothing left for a
parity to protect: a shuttle that turns at both ends is simply written from the end it is standing at.

Three consequences worth stating, because each was a defect:

- **Nothing is forgotten until it is written.** A turn the window could not apply — no setup open, a
  square the build has no copies for, a train the running layout no longer carries — stays owed and is
  tried again on the next refresh. It used to be dropped either way, which made the turn unrecoverable
  rather than merely late.
- **The record is dropped when that train next arrives somewhere without turning.** It means *this
  train is standing where it turned and the graph has not been told*; once it has run on, the arrival
  has already placed it on the copy its journey ended at.
- **The running layout is moved too, and the tail goes with it** — see §4.
- **And what is owed survives a setup rebuild** (D3-C5). The record lives on the `Layout`, and a
  rebuild replaces that object wholesale — so before this, any right-click gesture on the diagram
  between a turn and its successful write discarded it silently: a home, a direction, a caption.
  That defeated the first bullet in exactly the case it was written for, because a turn that has
  already declined to write once has nothing but its retries. `rebuildRunningLayoutFromSetup` now
  carries the owed turns across the same way it carries placements and `arrivedFrom`, and puts them
  onto the new layout only where it has no newer record of its own.

**"The next time the railway is idle" is a moment something has to bring about, and for two of the
four doors nothing did.** The graph is written by `reconcileFacingWhenIdle`, which is reached only
from a diagram refresh and deliberately refuses while anything is moving - so a run's turns wait for
a refresh made after it has stopped. Autonomy refreshes constantly, and the two hand-driven doors
were given a refresh when their journey returned; the timetable button and Return Home were not, and
Return Home is the one that backs trains into their home berths and turns them. So a Return Home run
ended with every returned train still drawn facing the way it set off, a dispatch made before any
unrelated repaint was offered paths for the wrong heading, and exiting wrote the un-reconciled facing
to disk. It self-healed on the next refresh that happened for some other reason, which is what made
it intermittent.

The rule now has one mechanism rather than a copy per door: **a run announces that it has finished,
and the window's single refresh callback tells the graph when it does.** A hand dispatch announces
when the last locomotive thread goes; a timetable announces again once its completion wait has seen
the railway stop, because until then `running` is still set and every announcement it makes is one
the levelling refuses.

---

### Turning round, and being chosen: two markings, two questions

Adam, 2026-09-09, settling OB-195 - the editor claimed autonomy would never choose a compulsory turn,
and the runtime chose one quite happily:

> *"narrow the notice to match the runtime - autonomy should only allow a turn at a point if the
> 'allow in autonomy' option is checked, otherwise the train may only pass through in its current
> direction."*

| the marking | the question it answers |
|---|---|
| **Changing Direction - Never / May / Must** | what happens when a train ARRIVES here |
| **Can Be Chosen in Full Autonomy** | whether autonomy may choose it as a STOP - off, it is used only by a train being parked there, by Return Home or by hand |

They are independent, and only the second decides whether autonomy picks a square:

**Why the second reads as "may it stop here" (Adam, MT-348, 2026-09-13).** Full autonomy never
turns a train part-way through a run - `pickPath` refuses a route that `reversesAlongTheWay`, and
`testFullAutonomyDoesNotDriveThroughAReversingPoint` pins it - so a may-turn square is never a place
autonomy reverses at on its way somewhere else. What the switch decides in practice is whether a train
may END a run there in autonomy, or only while it is being parked, by Return Home or by hand. The
editor's hint says it that way.

- A **compulsory turn** with the switch on is a station autonomy may choose, and the train turns round
  when it gets there. `Layout.isSendableDestination` accepts it - a must-turn station is built as a
  terminus, and a terminus has `isReversing()` false. Not quite "like any other": `getPossiblePaths`
  adds `!end.isTerminus() || loc.isReversible()`, so autonomy never sends a locomotive that cannot
  change direction to one.
- With the switch **off**, autonomy leaves the square alone. You can still send a train there by hand,
  and Return Home still uses it.

  **Nothing passes through such a square in either case**, and Adam's *"the train may only pass through
  in its current direction"* is about the marking rather than about that square: a compulsory turn is
  built as a terminus, and `Layout.isPathClear` refuses any route whose terminus is not its endpoint.
  What the sentence rules is that turning round is not part of who may be SENT somewhere - that is the
  switch, and only the switch.

**The editor said otherwise until this ruling**, on the strength of a comment claiming to mirror the
runtime. It cost nothing on Adam's railway, because every compulsory turn he has is also marked
manual-only - which is exactly why nobody noticed.

## 4. Which way a train is pointing, and where its tail is

Two different properties, and confusing them was the cause of a day's worth of defects.

- **`facing`** — the side the train's front points at. It is which copy of the square the train
  stands on, and it decides where it can go next.
- **`arrivedFrom`** — the side it came IN by. Its tail lies that way.

**Facing cannot answer where the tail is.** A train faces the way it will leave; once it has been
turned round the two point the same way while the carriages have not moved.

**Nothing about a placement is recorded until the railway has accepted it** (W21-B3). Every door that
puts a train down asks `Layout.moveLocomotive`, and that refuses in four cases: autonomy is running,
the locomotive is unknown, the point is unknown, and the target is not a destination. Its answer used
to be discarded at the diagram's own menu, so the placement and the facing were written into the setup
AND SAVED for a move the railway had just declined - and the setup is the half that survives a restart,
so the next build emitted the train on a square it was never put on. The refusal is the method's, and
the caller's job is to honour it: no placement, no facing, no arrival side, nothing saved. The log line
`moveLocomotive` already writes is what the operator sees; a second message would say the same thing
twice. `regression.testTheRefusalsAreAskedAtTheDoors.testEveryPlacementDoorUsesTheRailwaysAnswer` names
every door and says which one is allowed to discard the answer, and why.

### How `arrivedFrom` is set

- **Autonomy writes it on arrival**, which is the only moment anything knows it for certain — and
  before any reversal at the platform, because turning a train does not move its carriages.
- **A hand placement works it out**: a terminus forces it (one way in); an ordinary station assumes
  the train drove in forwards, so its tail is on the side it is **not** pointing at; **a may-reverse
  square asks**, because turning round is what those squares are for and the train is as likely to
  have backed in as driven in.
- **"Behind it" is chosen from the square's own sides, not from the compass** (`REV9-B3`, corrected
  2026-09-08). This rule used to be written as "the opposite of the facing", and it was computed that
  way: facing N gave S, whatever track the square had. That is the same answer on a straight and a
  different one on a curve, where the build enters a square by, say, N and E and no rail lies to the
  south at all. The side recorded then named no edge, the tail walk matched nothing on its first hop,
  and **nothing was blocked** — a protection reporting itself as present while doing nothing. The
  sides are now given first and the facing picks between them:
  - one side left once the facing's own is taken out — the whole answer on a two-sided square,
    straight or curved. On a straight it is still the compass opposite; on a curve it is the side
    that exists.
  - several left — three ways in — and the compass assumption picks between them, but only where the
    build really does enter by that side.
  - otherwise **nothing is recorded**. A missing arrival side narrows the walk; a wrong one sends it
    down track the train is not on. Claiming least is the same answer a dismissed dialog gives.

  Tested by `core.testACurvedPlatformRecordsASideTheBuildUses` on the `curve-into-platform` scenario,
  with a straight platform in the same fixture as the control.
- It can be **set** afterwards from inside the **"<locomotive> is facing"** menu, on both the editor
  and the track diagram. Not cleared - see below. The two questions live in one menu under separate
  headings because they are opposite ends of one train, and because a second submenu is what gave the
  editor a setting the viewer did not have.
- **The sides offered are the ones the BUILD splits on**, not the compass direction of the neighbouring
  point. A `Point` is the far end of a reduced edge that can run several tiles and turn corners, so a
  rail leaving north and curving east reaches a neighbour lying east; asking the geometry offered "E"
  where the metal leaves by "N". On a straight the two agree, which is why it went unnoticed.
- Not knowing is a legitimate answer. It does **not** mean nothing is blocked: where the geometry
  leaves only one way back, the walk still follows it, because that answer is forced rather than
  guessed. `arrivedFrom` picks between candidates; it is not a switch that turns blocking on.
  (Corrected 2026-09-07 after `VAL8-C1` — the earlier wording claimed less than the code does.)

- **It survives a train being re-stood on a sibling copy.** When the window writes a destination turn
  it also moves the locomotive onto the copy for its new facing, and that is two changes of occupant —
  which is what `Point.setLocomotive` drops the side on. Right for a different train arriving, wrong
  here: this is the *same* train on another copy of *one* square, and turning it did not move its
  carriages. So the side is carried across the move, and exactly the train that turned no longer loses
  the record of the track it is lying across.

**When would it not be known?** Adam asked, and it is a fair question given the two rules above.

- a setup saved before the field existed;
- a square whose **occupant changed** - `Point.setLocomotive` drops the side, because the new train
  did not arrive the way the old one did;
- a square with no named copies at all, where there is no side to record;
- a square with **three or more ways in** where the facing cannot separate them and the build does
  not enter by the compass opposite either - see the rule above. Deliberate: nothing is recorded
  rather than a side guessed.
- and a square whose occupant a **placement door could not ask about**, which since 2026-09-11 is
  only the dismissed prompt: `placeFacing` treats that as "do not place the train", so nothing lands
  and nothing is recorded.

**Every hand-placement door works it out, as of 2026-09-11** (`REV9-B2`, closed). This was the one
entry in the list above that was a defect rather than a design: the right-click "Place locomotive"
item and the assign dialog both placed a train without working the side out at all, so the same
placement blocked the track behind it or did not depending on which menu it was made from. Adam
settled it: *"the missing arrival side should be set - either from the data, by the user, or
randomly."*

The three doors now differ only in **where the question is put**, because the rule behind them is one
method:

| door | how it asks |
|---|---|
| diagram drag and paste | `ArrivalSidePrompt.forPlacement` - a dialog, before anything moves, so a dismissal leaves the railway as it was |
| right-click "Place ... facing" | the same dialog, on a placement only: turning a train that is already standing there does not change which way it came in |
| the assign dialog | a combo in the form it is already showing, started on the answer and correctable before OK |

`GraphLocAssign` uses a combo rather than a second popup because it HAS a dialog, and a dialog on top
of a dialog is worse than either. That also makes the assumption safe to show where the popup declines
to make it: on a may-reverse square the popup asks, because a guess it made would be invisible, while a
value sitting in a combo is seen and can be changed. `ArrivalSidePrompt.suggestedFor` is the rule with
the dialog taken out, and `forPlacement` is that same method plus the question - so the two surfaces
cannot answer differently.

The combo offers **sides and nothing else**, for the reason clearing is not offered on the menu below:
recorded, else derived, else the first side the square has, which is the "randomly" above.

**Clearing is not offered.** Adam, 2026-09-07: *"clearing should not be possible, only setting."* The
menu used to carry a "Not known" option, on the argument that a mistaken answer would otherwise be
permanent. It would not: the menu lists every side the geometry offers, so a wrong answer is corrected
by choosing the right one. What clearing offered was a way to switch the tail blocking off, which is a
preference about the check rather than a fact about the railway. The automatic clear above stays - the
railway dropping a fact it no longer holds is not a person declaring ignorance.

### Pasting a train

> *"Calculate the simple BFS path from the current station to the paste target using the current
> direction. Paste with the direction where the train ends up at the destination. If no path, pick
> randomly from the allowed departure destinations."* — Adam, 2026-09-06

The heading is not carried and not inferred from the two squares: **the railway is walked**. Which
way a train ends up facing is decided by the track between the two platforms and by which way it set
off — one that leaves southbound round a loop is northbound one square away. A square with one copy
has one answer, which at a terminus is the reversal.

**The walk answers with the nearest copy a train could be STANDING on**, and the same paste twice
gives the same railway. Both halves of that were untrue until 2026-09-12, and the two compounded:
`Layout.getNeighbors` shuffles on purpose - *"Randomize order to allow for variation in paths"*, which
is right for autonomy picking a journey - and the walk answered with the first copy it touched, so on
a square trains may turn round at, where the plain copy and the turning copy are always the same
distance away and face **opposite** ways, the shuffle chose the direction. Measured on the frozen
snapshot: 2-8-4 3505 SP put down on BottomMainB faced east 21 times out of 40 and west the other 19
(Adam, MT-377: *"When 2-8-4 is pasted, it should always face east. Does it?"*).

So the walk runs to the end and the distances decide - breadth-first measures those correctly through
any expansion order - and the tie at the nearest distance goes to the copy a train would simply be
standing on. **Putting a train down is not a decision to reverse it**; the turning copy IS that
decision, and the facing menu is where an operator makes it. A compulsory turn emits no plain copy, so
there is nothing to prefer and its turning copy is still the answer, which is Adam's *"for terminuses,
they must reverse on paste"* falling out of the same rule rather than bolted beside it.

This is the fourth site of one confusion: a may-turn square's turning copy is emitted with
`terminus: true`, so `isTerminus()` cannot tell it from a real terminus (OB-205 claims 1-3, MT-368).

**And the train is PUT on the copy the walk names, not only recorded as facing that way** (Adam,
2026-09-22, OB-270: *"Trains should not inadvertently change direction when pasted, so a loc going west
from bottomsecondary should always face east when pasted on bottommaina."*). A paste writes two things -
the heading in the setup and the copy the train stands on in the running layout - and the copy IS the
direction (section 3). Where a square has two copies a train may stop at, facing opposite ways, the copy
used to be whichever `StationIndex.speakerAt` met first, so the record said east while the train stood
westbound, and its arrival side and tail were then worked out for the wrong copy. **The rule: the copy
taken is the operator's chosen heading where one was asked, and the walked heading otherwise**; a copy no
train may be placed on is still refused (`copyFacing`), and the heading is chosen and recorded over the
copies a train may stand on, so no impossible facing is saved. Built 2026-09-23.

**A cut train is walked from the square it was cut from** (Adam, 2026-09-23, OB-270: *"it should be east.
no train should inadvertently change direction when pasted."*). Control+X takes the train off the railway,
so the walk had nowhere to start and the paste kept the compass heading it was cut with (MT-368) - west, for
a train cut going west at BottomSecondary, where the route to BottomMainA loops round and arrives facing
east. The cut remembers its square, and the paste walks from there; the cut heading stands only where no
route reaches the landing. `ui.testACutTrainArrivesTheWayItWouldDrive`.

**Where there is no path**, the heading the train already has is kept if the landing can hold it, and
otherwise the first copy it could depart from is taken. Adam's 2026-09-06 wording for that arm was
*"pick randomly from the allowed departure destinations"*, on the reasoning that nothing in the
situation determines an answer; it is superseded by his ruling of 2026-09-12 - *"as long as the
direction isnt flipped (which it was before)"* - because something does determine one, and a
placement that answers differently each time it is repeated is drift (*"in all your simulations, state
should never drift"*, 2026-09-07).

> *"Simply don’t place the train, leave it on the clipboard as if no paste had been done."* — Adam,
> 2026-09-07, on a dismissed prompt

**A dismissed arrival-side question abandons the paste.** The question is asked *before* the train
moves, which is what makes declining it possible at all: it used to be asked after the drop, by which
point the train had been lifted, put down, and the clipboard emptied, so closing the dialog placed the
train anyway and merely declined to record which way round it was. Nothing moves, the clipboard still
holds the train, and the next square accepts the same paste.

Only a **may-reverse** square can be dismissed. Everywhere else the side is worked out rather than
asked, so no answer means "there was nothing to record" rather than "the operator said no" — treating
the two alike would refuse every paste onto plain track.

**And a may-reverse square asks a second question: which way the train should face.**

> *"If pasting at a 'may reverse' square, ask what direction the train should face."* - Adam,
> 2026-09-13

The two questions are about different things and only that square needs both. **Where the tail lies**
is where the train came in from; **which way it faces** is where it will go next. On any other square
the second follows from the first, and the walk answers it: a train is driven from where it stands to
where it is being put down, and the copy it arrives on says which way it points. At a may-reverse
square turning round is the point of the place, so both headings are reachable and the walk's answer is
whichever copy it happened to land on.

The headings offered are the ones the square can hold, deduplicated - a square with three copies facing
east offers east once - and a square that can hold only one heading asks nothing. The walk's own answer
is the default. A dismissed question abandons the paste exactly as the first one does, and the answer
given is what is written: the choices offered were the square's own, so there is nothing left to
filter.

### What counts as a parking berth

> *"For now, we consider anything with autodestination=false and only one way in/out as a parking
> square. We can revisit dedicated marking if this doesn't work out with clean logic, or if it gets
> too confusing to the user to manage."* - Adam, 2026-09-13, ruling on FR-060

Two facts, and neither is enough alone. **Autonomy will not choose it** says the operator is keeping
the square for himself, which is true of a berth and of a platform he wants to dispatch by hand.
**One way in and out** says the track is a dead end, which is true of a berth and of a headshunt.
Together they are what FR-060 wanted a fourth designation for, so there is nothing to mark and nothing
to migrate. Measured on his railway the day of the ruling: 20 stations carry `autoDestination: false`
and 12 of them are berths by this test.

Counted in **squares**, not copies: a square split into a northbound and a southbound Point is one
place with one way out, and a neighbour reached by two copies is one neighbour.

**This is not the berth rule's gate.** "A berth may not block another road" (5b) applies wherever
autonomy will not choose the square, which is deliberately wider - the example Adam ruled it on has
three ways in and is not a parking berth by the test above. The two questions stay separate.

**A paste onto a square no train could leave is refused**, naming the square, from both doors. The
right-click menu had always greyed the item; the diagram drop said nothing and did it anyway
(Adam, MT-136). The question is asked of the square rather than of one arbitrary copy, since the paste
itself walks to a copy that can depart.

---

## 4a. Station captions: a readout, not a name plate

Adam, 2026-09-13: *"nothing seems to happen when I say 'show a different station here', is it meant to
reset the label?  we should make it clear what this does."*  He had been using the feature for weeks;
that the question could be asked at all is the reason this section exists.

**A caption is a square that shows the live state of a station somewhere else on the diagram.**  It is
not a label naming the square it sits on.  What it draws changes as the railway runs:

| what the pill shows | what it means |
|---|---|
| a locomotive's name | that train is standing at the station |
| an em dash | the station is empty |
| an arrow | a train is passing through without stopping, pointing the way it is going |
| three bullets | passing through, and the graph cannot say which way - an unsplit station, or a copy with no recorded facing |
| a dark fill behind the name | a home locomotive that is NOT at its home |

**A station square always shows its own station**, and the menu says so rather than offering a choice.
Putting another platform's name on a platform is a mistake with no upside, and a list with the
square's own name buried in it is a question whose answer is already known.  The item on such a square
is present but disabled, with the reason on its tooltip - present rather than hidden, because a menu
item that vanishes answers "what does this do" by refusing to say.

**One station, one caption.**  Pointing a new square at a station clears the old one, so the diagram
never names the same station twice with nothing to say which is current.

**A caption may sit on blank space, and it may not sit under your own text.**  Text you have written
on a square wins, so a caption there would be invisible; the editor says so and offers to replace it
rather than doing so silently.  Deleting the square a caption sits on takes the caption with it, and
deleting the STATION takes every caption naming it - text pointing at track that no longer exists is
the orphan this design removed.

**Why a station with no train reads as an em dash and not as its name**: the caption is about the
station's state, and "empty" is a state.  That is also why an unlabelled square captioning an empty
station tells you nothing about which station it is, which is what FR-014 was raised about - the menu
names it in the "Stop Showing" item, and the tooltip explains the rest.

## 5. Length: will the train fit?

Two separate rules, both about length, both easy to mistake for each other.

### 5a. Room, where the train comes to rest

**Two rules, not one, and they are asked in different places.**

| | what it measures | which squares it judges |
|---|---|---|
| **Track room** | the rail behind the square, from the last switch to it | **where the train comes to rest**: the destination, and a square it turns round at - **not a square it only passes** (Adam, 2026-09-14) |
| **Station capacity** | the length the station says it accepts | **the destination**, when it states one |

Adam, annotating this section: *"the train should be refused any destination it does not fit in, i.e.
where the train length exceeds the accepted length."* It is - by the second rule, and in all three
tiers: `Point.validateTrainLength` is asked from `Layout.isPathClear`, which every autonomy and manual
path goes through, and again from `HomeStaging`. A destination with no stated capacity is not judged
on capacity; that is what leaving the field at zero means.

The rest of this section is about the **first** rule.

- **It judges every destination, and used to judge only reversals** (MT-262). Adam, 2026-09-05:
  *"'75 407 DB' (length 4) is allowed to manually be sent from bottommainpost to bottomlongpark,
  even though track segments between the current position and there are 1+1 = 2."* The rule was
  fenced behind terminus-or-reversing, so a plain through platform was never judged on the track
  leading into it. A train comes to rest with its head at the destination sensor wherever it
  stops, so its tail lies back over the run in either way - which is what §5c's covered-track rule
  has always assumed, on Adam's own through-platform example.

  **In every tier**, autonomy included. A berth that cannot physically hold a train is not a
  preference manual may overrule, and §1 makes autonomy the stricter tier - a length rule that
  refused only the operator would invert it.

- The room is measured **from the last switch** to the square. A train that fits there fits behind any
  earlier switch too; one that does not comes to rest standing on the switch.

  **"Fit" here means the surrounding track**, not the length of the berth - that is the station
  capacity rule above, and it is asked separately. A train can fit the platform and still be refused
  because it would be left standing on the switch behind it.

- **It is asked where the train comes to rest, and not at a square it only passes** (Adam, MT-333,
  2026-09-14). Two different mechanisms are about a train overhanging the points, and only the second is
  this rule:

  1. **A train already standing still blocks other roads** - stopped at a station or parked - wherever the
     segments under it are measured. Its tail claims the places it lies on and no route is cleared over them
     (§5c; `OB-207`; a driven train's tail follows the road it came in on, a placed one stops at the fork), and
     a parking berth refuses a train that would foul a road that is not its own (below). **Unchanged.**
  2. **Whether a train being SENT fits** is asked where it will halt: the destination, and a square it turns
     round at, because it stops and reverses at every such square on its way. A square it only drives past is
     not asked - it stands nowhere there, and a moving train holds the track it runs on, so nothing is routed
     across it meanwhile.

  This is the ruling of 2026-09-09 below read as it was meant, not reversed: *"edges are already locked as
  trains pass through ... it's really about implementing the same mechanic"* - the mechanic of point 1, which
  exists only where a train stands. From 2026-09-10 (`5948a88a`) to 2026-09-14 the rule applied it at squares a
  train only passes too, wherever a switch lay behind them, and the platform relaxation of 2026-09-12 was built
  at the destination only; so 75 407 DB, two units, was refused Tunnel to BottomMainA at BottomMainAPre once
  one unit was measured on the run before it - *"this SHOULD be allowed per the standing rule that this switch
  blocking should only affect berthes."* Adam, confirming both points: *"This is all correct as stated ... No
  separate rule."* So a route is no longer refused for passing a short measured stretch after a switch, his
  RampDown example below included. `regression.testAPassingTrainMayStandAcrossThePoints` is the test, each
  scenario on its own diagram with his measurements, point 1 included; `core.testATrainIsJudgedOnlyWhereItStops`
  is the same on a fixture.

- **(The reading from 2026-09-09 to 2026-09-14, kept for the record; the figures in it are from then.)** The
  question was asked at EVERY square on the route, not only at the destination. Adam, 2026-09-09, having been
  asked which of the two it should be and told what the second costs:

  > *"For 1, it's b. This should only apply if lengths are specified - and edges are already locked as
  > trains pass through in non-dynamic mode. So it's really about implementing the same mechanic."*

  What he reported it against was a nine-unit train being offered `RampDown`: *"technically incorrect
  to say there is a path since we pass the track of length 1 at 22,7 to get there, then nothing."* The
  route crosses that tile seven edges before the berth, and the berth-only walk could not see it.

  **The same mechanic, not a second one.** The walk answers about the square a path ENDS at, so it is
  asked of every prefix of the route; the destination is the last of those rather than a rule of its
  own. Nothing new is measured.

  **What it costs, on a railway measured to make it cost something.** Over 1848 routable station
  pairs and six train lengths - 11088 journeys - the berth rule refuses 1760 and this ruling refuses
  about 1655 more, so roughly one journey in three is refused for want of room, and **every square
  that does the refusing measures ONE unit**. (Those are the figures of 2026-09-09; since OB-229 the census walks
  no route through a terminus, and section 5c's table gives today's.)

  **Those three one-unit tiles are a test configuration, not a survey of his track.** Adam,
  2026-09-10: *"A/B/C are distinct pieces of track. We put the lengths of 1 in there for testing.
  Actual tracks are much longer."* `5:22,7`, `5:19,12` and `5:14,13` were the only lengths anywhere on
  the snapshot, and both censuses now set them deliberately and say so, rather than reading them out
  of a fixture and reporting the answer as a fact about his railway. The figures are what a rule about
  room does on a railway with no room; the real one has more.

  `core.testTheRoomRuleCensusOnTheRealLayout` re-measures it on every run, against the frozen
  `test/layouts/live-snapshot` rather than the railway Adam is operating, and reports a band rather
  than a figure because which route a search finds first is not reproducible between JVMs.

  **A journey in three is not a train in three**, and that is the number to read first. Journeys are
  counted over every ordered pair of stations, so refusals that fall unevenly close whole squares
  rather than thinning the timetable — and here they do: with those three tiles at one unit, **seven
  squares offer a train of two units or more nowhere at all**, where the berth-only rule offered each
  of them thirty-odd destinations. `core.testWhichSquaresTheRoomRuleClosesOff` measures and pins it.
  Whenever this rule is changed, that census is the one to read, not this one.

  **The refusal names the square that is short**, and says a different sentence for one on the way
  than for the berth. Naming the destination when the destination has room sends the operator to
  measure the one stretch that was already long enough.

- **(Same period, same record.) A square the train passed THROUGH was judged only where a switch was behind it.** Since 2026-09-14 such a square is not judged at all, and the censuses report none refused on the way. The walk has two
  stopping conditions and only one of them is a measurement: it stops at the last switch, which is the
  rule, and at the start of the route, which is the walk running out of track to look at.

  At the destination the second is harmless and long-standing - the train comes to rest there and lies
  back over the route it came along. At a pass-through square it means a two-edge prefix answers "two
  edges of room" when the honest answer is that the track behind where the train started has not been
  looked at **and the train is standing on it**.

  The first cut of this ruling had no such condition, and the battery came back with a four-unit train
  refused four units of room, an eight-unit train refused a nine-unit run in, and the staging planner
  giving up on berths it could reach. On Adam's railway the condition costs nothing - every one of the
  1645 refusals the ruling adds is bounded by a switch - which is what the census reports on every run
  under *"refusals the unbounded walk would have added"*.

- **Exactly-fits is admitted.** Four units of room takes a four-unit train — otherwise every berth
  measured to the train that lives in it becomes unusable.
- **The total across the stretch is what counts**, not any single tile: 2 + 2 admits a four-unit
  train, 1 + 2 does not.

#### A train may overhang the points at a station, and may not at a parking berth

> *"we need a clear rule to govern that this is OK, or simply make a rule that parking berths cant
> block any other edges, but not make that check for active stations."* — Adam, 2026-09-12

The rule above measures the room **past the last switch**, so a train longer than that stretch is
refused because it would come to rest across the points. Adam's worked example: an approach measuring
6 with a switch in the middle and 3 either side. A six-unit train there necessarily stands on the
points — no distribution of the measurements changes that — and he rules it acceptable.

**What decides is how long the train stays.**

- At a **station autonomy may choose**, a train is PASSING. It fits if it fits past the last switch,
  as above, **or** within the measured track of the route it drives in on (FR-087, Adam on MT-431,
  2026-09-15: *"BottomMainA should be allowed at length 3, since it is not a parking spot, the station
  allows the length, and the length would be tracked"*). The route is counted back from the destination,
  leg by leg, for as long as each leg is measured and never back past a square the train turns at (the
  berth's own bound, 2026-09-11); a train that ends inside that stretch is accepted, and
  its tail lies on the route and blocks it (5c). A leg with no length stops the count, so a train longer
  than the measured track in is still refused. `Layout.theApproachItselfHoldsIt`;
  `regression.testAPassingTrainMayStandAcrossThePoints.testTheRouteInHoldsAThreeUnitTrainAndNotAFourUnitOne`.
- At a **parking berth** — a square with *Can Be Chosen In Full Autonomy* off — a train is STAYING,
  and a berth is not worth a road. On top of the room rule it must foul no track that is not a way in
  or out of its own square. Its own roads are excluded because the train being there blocks them
  anyway, and *"its own"* means the SQUARE: a split platform is several Points and one piece of track.

**What this spends, said plainly.** A train standing at a platform across the points closes the roads
through them while it is there. Measured on Adam's railway, a train at BottomMainA long enough to
reach the switch closes both ways to the lower level and 33 ordered pairs of stations stop being
reachable from one another. That is a STRANDING cost, not a collision one: since `OB-207` a standing
train's tail claims the places it lies on and no route is cleared over them, so nothing can be sent
into the overhang. The berth half is where the cost is refused, because a parked train pays it all
evening.

**A train that turns round at the destination is not excluded** from the relaxation. It stands there
for the same reason and for the same time; what it does on departure is another journey.

**Both halves are only as good as the measurements.** An unmeasured tile costs a tail nothing, so on
unmeasured track a short train is modelled as lying across many tiles. Where an approach carries no
places, or no length, both halves fall back to the answer 5a already gives.

`core.testABerthAndAPlatformJudgeAnOverhangDifferently` is the test, on Adam's own two squares.

### 5b. What "unmeasured" means

> *"You need to measure total distance between points, not validate that every edge has a length > 0.
> It is only indeterminate if the entire logical segment has length 0."* — Adam, 2026-09-06
>
> *"Generally, allow it. But: the example ... has measured segments before (that prevent it) ... Make
> sure you are actually enforcing this."* — Adam, 2026-09-06

- Unmeasured track contributes **no room**. It does not follow that it contributes **no answer**.
- A stretch where **nothing at all** is measured is not judged — the train is allowed. Refusing on no
  information would make an unmeasured layout unusable.
- Where part is measured, **the measured part still binds**: an unmeasured segment must not cancel a
  refusal the measured ones already earned.
- **A stretch is bounded by the segment it lies in.** If the room after the last switch is unmeasured
  but the segment measures one unit, a four-unit train does not fit — a part cannot be longer than
  the whole. This over-states the real room, so it only ever refuses more; nothing new is admitted.

**Mass Assign Lengths and Unmeasured Track: every leg, cut at switches** (FR-089; Adam, 2026-09-16, on review
MAL-B1). The length rules between them read every leg on the railway: the room rule the track past the last switch,
but the FR-087 allowance (5a) whole legs back until a reversal, and the tail and berth walks (5c) the standing square,
the switch square and the track before it. Measured on Adam's railway, that reach is every one of its 75 legs. So the tools cut every leg into
**pieces** between two fixed points - a sensor or a switch - and ask for each piece's whole length once; each square is
in exactly one piece. **A switch square is in no piece**: a share of a piece's length landing on it would sit where the
room rule does not count it, so switches are asked for together, one turnout length for all of a page's switches with
none (*"One length for all switches"*). Two switches back to back have no piece between them - there is no square of
track there to measure. **A piece needs a length only while its whole total is 0**, by the ruling above - a square
inside a measured piece may rightly hold 0 - so the least a piece could be given was 1, until the ruling below. The share is even; any unit left
over goes first to a square a train stands on, whose length its own tail never spends, which is the refusing direction
(MAL-B2).

**A route tile is in no piece, and is never asked for a length** (Adam, 2026-09-23, OB-273: *"a route
tile should not need or accept a length.  it just implicitly connects things as if it were a
crossing."*). It carries no rails of its own - section 8 - so no length rule reads it, the tools do not
ask for it, and a piece's length is shared over the track squares either side of it. His example: three
plain squares and a route tile with 4 typed is 2, 1, 1 by the even share, where today the fourth unit
lands on the route tile and nothing draws it. **The share stays even** (OB-275, Adam: *"let's stick to a
then, since that is more visually pleasing"*) - the other option he offered, the whole length on one
square, would also have made the tail depend on which square a train happened to stop on (5c spends
length square by square). Built 2026-09-23 (MT-476): `assignStretchLength` shares over the track squares only.

**A length a route tile already holds is folded into the track beside it** (Adam, 2026-09-23, OB-281: *"Fold them,
they were likely auto set during the mass assignment run."*). Five of his route tiles held 1 each from before the
ruling, so each piece they sat in measured a unit less than he gave it. Opening a setup moves each onto the square
beside it along the road it carries - plain track before a switch - so every total is what it was.
`AutonomySession.foldRouteTileLengths`, `core.testMassAssignLengths.testHisFiveRouteTilesAreFoldedWhenHisRailwayIsOpened`.

**A deliberate 0 is an answer** (Adam, 2026-09-23, OB-274: *"we need to allow a length of 0 as a length
that is set deliberately, i.e. for adjacent tracks.  same meaning to the model, but this will allow
everything to get assigned without what appears to be a skip."*). So the walks accept 0, the piece is
recorded as answered and is not asked about again, and **every length rule reads it exactly as it reads a
piece nobody has measured** - 5b is unchanged. Confirmed by Adam when asked the question directly: a deliberate 0 is not a
measurement of nothing, and a stretch whose answers are all 0 is still not judged. What changes is only whether the tools keep asking.
Built 2026-09-23 (MT-476).

**Segment Length's 0 is the same answer, and Clear is its own button** (Adam, 2026-09-23, FR-097: *"no, add a clear
button"*). A 0 typed there records the run - or each selected square - as answered 0; **Clear**, or an emptied field,
removes the length and the answer. And **an answered 0 is not listed as missing** (*"stop listing answered zeros as
missing"*): the half-measured berth notice, the reversal notice and the berth refusal's "N squares still have no
length" leave answered squares out, while the rules go on reading them as nothing. The refusal is worded on the
running layout, so the build marks an answered place and the runtime keeps the mark.
`core.testMassAssignLengths.testSegmentLengthZeroIsAnAnswerAndClearTakesItAway`, `core.testAnAnsweredZeroIsNotMissing`.

**A square two roads cross is in no piece either** (Adam, 2026-09-19, on review SET-B2): *"For crossings: if its
length is set, count that length once in each direction."*  A crossing - or a double curve with track on both of its
roads - is an ordinary square to the room walk, which counts through it, and it lies on two legs at once.  Inside one
leg's piece its share would be shared out by that leg's answer and then counted again on the other road, so both roads
are cut at it and it is asked for on its own, one length for all such squares on a page.  The reduction adds that
length to each road, which is the ruling.  A square whose geometry carries two roads but which only one leg runs over
is ordinary track and stays in its piece.

**The bulk doors on Bulk Tools.**  **Mass Assign Train Lengths** (FR-094; Adam, 2026-09-23: *"add a bulk tool to
the autonomy editor to set missing train lengths, similar to how the station lengths are set"*) walks every train
autonomy would run that has no length, in the same prompt, writing each answer to the locomotive itself - so Cancel in
the editor does not take it back, and the prompt says so; a length is 1 to 20.  **Mass Assign Lengths** (FR-089) walks the pieces of the page, then its
switches, then its crossings.  **Mass Assign Max Train Lengths** (FR-091; Adam, 2026-09-17: *"add a similar feature to
walk stations that don't have a max length set up, so I can enter it"*) walks the stations on the page that will take
a train of any length, row by row, and asks each one's maximum - the walk refuses 0, because 0 IS "any length" and the station already has it.  A
NEGATIVE is refused at the single door as well (SET-B1): `Layout.fromJSON` will not load a configuration
carrying one, and the bulk clear counts any non-zero maximum so that one already stored can be taken off.  **Clear All Track Lengths** (FR-069)
and **Clear All Max Train Lengths** (FR-092) each take their setting off every page after a confirmation that says how
many.  The two WALKS - lengths and maxima - share one prompt: the number box has the keyboard focus, Enter submits,
Skip leaves the square as it was, Cancel or Escape stops, and the prompt opens where the last one was left
until a new round is started.  The number box is asked for by name whenever the prompt gains the keyboard (Adam,
2026-09-23, MT-474: *"make sure the text field is focused by default"*).  The two clears ask once, in a confirmation.

**Bulk Tools is on every square's menu** - an empty square's and a text square's as well as track's (Adam, 2026-09-23,
MT-474: *"when right clicking an empty square, show the bulk tools menu option"*).  Its items are about the whole setup
rather than the square under the pointer, so they sit at the bottom, behind a divider.
`ui.testBulkToolsHoldsTheWholeLayoutTools.testAnEmptySquaresMenuHasBulkTools`.

**Segment Length speaks for the whole run** (SET-B3).  A run of plain track has one square that speaks for it, and
that has to stay true now that Mass Assign Lengths shares a piece's length over every square it covers: the dialog
opens on what the RUN measures and writes the typed total to the run, leaving the squares that follow the leader at
nothing.  A shift-click selection is squares rather than runs, and gives each selected square the number typed.

The editor notice about turn-round squares with no length is a different question, said unprompted and so asking less
(MT-305).

### 5c. The tail: track a standing train is lying across

> *"Those edges it reaches need to be considered blocked ... bottommainc should currently be blocked
> since a train of length 4 is standing at bottommainb, which has a length of 1 leading up to its
> switch."* — Adam, 2026-09-06

- **Edges, not points** — *"because the points are technically unoccupied. But the blocked edges
  should prevent routing to the covered points."* A station reachable only across covered track
  becomes unreachable as a consequence, rather than by a second rule that could disagree.
- The walk starts at the side recorded in `arrivedFrom` and follows track **regardless of direction**
  — a tail fouls the rail whichever way traffic runs.
- **It stops at a fork.** *"If entering a switch from the fork direction where it splits, just end
  locking at the switch and call it a day."* One way back means the tail certainly lies there;
  several means the graph cannot say which.
  - **Unless the train was driven there** (Adam, MT-333/MT-335, 2026-09-13: *"Follow its last route"*).
    A train that arrived by a run remembers the route (`Point.arrivedAlong`), and past a junction the
    walk takes the road that route came in on. `core.testATailFollowsTheRouteItCameIn` drives a real run
    through a junction and has a placed train with no road as its control.
  - **The road is kept** (WK7-B1, Adam 2026-09-14: *"Then state will always be fully consistent."*). It is
    saved with the point (`arrivedAlong`, as start/end name pairs), read back on load, carried across every
    rebuild with the train, and captured into the setup with the placement and the side - so a restart,
    closing the autonomy editor or any setup gesture no longer drops it. A road naming a rail the layout no
    longer has is dropped whole, and the fork rule applies again. `core.testATailRouteIsKept`,
    `regression.testAPassingTrainMayStandAcrossThePoints`.
  - **A train placed by hand is asked** (Adam, 2026-09-14: *"pick from a list and select the farthest sensor
    the tail of the train recently crossed.  Also, ideally in the autonomy editor, it should allow the user to
    click to select as well."*). The whole train lies between the farthest sensor its tail has crossed and the
    square it stands on, so that sensor names the road. The question is put - when a train is pasted, placed
    from the right-click menu, or set in the locomotive dialog - only where the tail lies past a junction with two
    roads back and the roads put it on different track - two roads being two pieces of METAL, compared by the
    places the rail runs over (OB-276: RampDown's southbound lane and its northbound turning copy both leave south
    by one rail, and were offered as two "RampDown"s; a balloon's two ends are still two); elsewhere every answer
    describes the same track. **A tail that has passed the switch the roads part at is asked about whether or not
    it has reached a sensor** (Adam, 2026-09-23, MT-477: *"when set to lenth 3, the tail always follows switch 51
    turned, rather than facing straight toward rampdown ... technically that length should qualify for the
    prompt"*): the last square two rails share holds the points, and a tail reaching into it lies on one leg or the
    other. Each road it can lie on is offered - by its sensor where the tail reached it, and as **towards X (not
    reached)** where it did not - so at BottomSecondary three units are offered towards RampDown and towards
    BottomCrossover, and four units RampDown and towards BottomCrossover, where the list once offered RampDown
    alone. A tail that ends before the points covers the same squares on every road, and is not asked.
    `core.testATailPastASwitchIsAskedAbout`. Not asked, or closed without an answer, the road the train
    had on the railway is kept where it stays on the same square with the same side; **Not known** forgets it. The
    list starts on the road it has; with none, on the one sensor nearest the back of the train where exactly one
    qualifies - no other offered sensor lies further back on the same road - and on nothing otherwise (FR-088,
    Adam on MT-435, 2026-09-15: *"so the user can just click OK if appropriate"*); a way towards a sensor the tail
    has not reached is no sensor crossed, and is never where the list starts (MT-477). The list
    offers each such sensor, nearest first, and **Not known**, which keeps the fork rule. A sensor exactly the
    train's length back is offered: the tail has reached it (OB-226). Only roads a train can drive in on are
    offered - the walk back takes rails that run towards the train - so a road it could only have reversed along is
    not one (OB-227, Adam: *"that isn't a realistic path"*). The same list is in
    the right-click menu under **Farthest sensor the tail crossed**, and in the autonomy editor **Pick on the
    diagram...** outlines the sensors to click instead. Which sensors are offered is worked out from the
    measured lengths of the roads back and is a suggestion: what blocks track is still this walk, reading the
    road chosen. A road given this way runs along rails laid towards the train, as a run's road does. `core.testTheTailCrossedQuestion`,
    `regression.testTheTailCanBeGivenInTheEditor`.
- **It stops at unmeasured track.** Only positive lengths are determinate.
- **The square the train is standing on is track, and it is spent first** (Adam, 2026-09-23, OB-278:
  *"the 2 length tile with the s88 consumes 2 units of the train"*, and asked where, *"Everywhere"*).
  A train no longer than the square it stands on lies on that square and blocks nothing behind it;
  a longer one spends the square and lies back over the rest. Every walk that works out where a body
  lies does this alike - the tail walk here (places budget and hop budget), the berth rule, and the
  orange line (§5c below) - and so does the room rule, which always counted the square.
  **The allowance is the station's SIZE** - the maximum train length typed on it, which says how much
  train it may hold and is what `whyTooLongForThisRoute` asks first. Adam's ruling of 2026-09-13,
  *"if the segment length is shorter, more should be blocked. The station size is an allowance, not a
  length"*, says exactly that; from 2026-09-13 to 2026-09-23 it was read as meaning the length measured
  on the station's square, and all three walks left that square unspent. On his measured railway,
  where each berth's length is written on its sensor square, that put every standing train's tail over
  the switch behind: TunnelLongPark (2 on its square, 1 behind, holds 3) took a one-unit train and no
  longer, and TunnelCenterPark and TopR1ParkShort took none. `core.testAStationsSizeIsAnAllowance`.
- **A train part-way along a run is walked the same way from its last MILESTONE** (OB-244, MT-438):
  a milestone is ordinary block and the body lies over it. That used to be the exception to the
  misreading above; with the square spent everywhere there is no exception left to make.
  `core.testARunningTrainHasOneTail.testAMidRunMilestoneIsNotAnAllowance`.
- **The first hop takes the copy of the rail the train ARRIVED along** (SVZ-B1). A piece of rail is two
  edges, one per direction, and at a berth both can report the same way in. An edge's places are the
  path plus the square it arrives at, so only the arriving copy carries the square the train is on -
  and taking the other one left that square claimed by nobody, at some stations and not at others.
  Where there is no arriving copy - a square a train has been turned on - the other is still used.
- A train never blocks itself — pulling forward off its own tail is how it leaves.
- **One tail per train, and a running train's starts at its head** (Adam, 2026-09-21, OB-243: *"the
  tail is certain at departure and shouldn't change.  You also know which way the train went ... Just
  unlock the rest of the diagram once the tail by length is far enough away"*). A locked path reserves
  every Point on it, so during a run a locomotive is the occupant of several Points at once - and the walk
  used to start from each of them, drawing a tail at a destination the train had not reached and another
  at a square it had long left (OB-242). It now walks once per locomotive. A running train is anchored at
  its last reported milestone and spends its length back along the road it has already driven; a standing
  train is anchored where it stands. `core.testARunningTrainHasOneTail`.
- **And the track it shares metal with is closed too, which is the anti-collision rule at a switch.**
  Adam, 2026-09-07: EN57-203 *"is allowed to traverse a blocked/shaded switch (60) to get from
  TunnelLeftPark to BottomMainC, even though it should not be possible."* Coverage is recorded per
  EDGE; a train lying across one pair of a switch's arms fouls the other pair, and the other pair is a
  different Edge that was never in the covered set. So a path is refused over any edge that shares
  metal with a covered one — the lock-edge relation `GraphReducer` derives from shared tiles. Told
  apart from an FR-001 occupancy restriction, which uses the same list, by **symmetry**: sharing a
  tile is mutual and the reducer records both directions, while a restriction is one-directional.
  Known limit, written down rather than left to be rediscovered: two stations each holding the other
  back are symmetric by coincidence, and this over-refuses there.
  `core.testACoveredSwitchClosesTheOtherRoad` is the test that goes red when the rule is removed —
  it needs a turnout to express, so it runs on `test/layouts/single-switch`; the two older
  covered-track classes stayed fully green with the rule disabled (AUT9-B2).
- **The copies of one square are one piece of metal, except where the square is two.** Occupancy is
  recorded per Point and a square is emitted as one Point per side a train can arrive by, so the build
  groups those copies under a `block`: a train on the northbound copy makes the southbound copy read
  occupied, which is right, because they are the same rail. The TILE says which copies those are, not
  the s88 - genuinely different places share a sensor on a real layout. Adam, 2026-09-22 (OB-238), on
  the three types where that is wrong: *"it is two pieces of metal. imagine two parallel tracks simple
  appearing on one tile for visual convenience. two distinct, not connected paths."* A double curve is
  two arcs in opposite corners that never touch and an overpass is two tracks at different heights, so
  DOUBLE_CURVE, FEEDBACK_DOUBLE_CURVE and OVERPASS are grouped per ROAD instead - the grain the
  reduction has used for them since AUR-B1 - and a train on one road leaves the other free. Grouping
  them by the tile refused a second train track that is physically free, which is the refusing
  direction. `core.testAutonomyDiagramSession.testEachArcOfADoubleCurveIsItsOwnPieceOfMetal`.
- **But only over the part of it the train is actually lying on** (Adam, OB-207, 2026-09-12: *"75 407
  DB cannot go from Tunnel to BottomMainA even though it should be able to"*, at a train length of
  one). An edge was covered whole or not at all, so a one-unit train parked at the end of a
  twelve-tile run fouled every road sharing any tile of it, and a station beyond it became
  unreachable from anywhere. The build now writes each edge's **places** — the location identifiers
  the lock relation is itself derived from, in order, each with what it measures — the tail walk
  spends the train's length across them, and the shared-metal rule asks whether the tail lies on
  metal *this* edge runs over. Adam proposed it as extra nodes at the switches; the places are the
  same information without inventing a Point that has no sensor.
  - The **direct** case stays whole-edge on purpose: a path uses all of its own edges, so a tail
    anywhere on one is in the way. Only a **shared** edge can be touched at one end and no further.
  - An edge with no places — a hand-written configuration, or one written before 3.0.0 — keeps the
    whole-edge answer.
  - `core.testAShortTrainDoesNotBlockTheWholeRun` is the test, with a control that a train long
    enough to reach the shared metal still blocks it.
- **Two marks, and they say different things** (Adam, 2026-09-09: *"'train is here' should also mean
  'track is blocked' - that is the whole point. it's the same as greying out edges, just in a
  different way."*). A square with **orange** on it is where a train IS. A square that is **grey and
  not orange** is track that train's presence has made unusable. A square with neither is free.
- A train is drawn as an **orange line along the track it is standing on**, **on the track diagram
  viewer only**, until it moves. An editor is where the railway is arranged, and what happens to be
  standing on it while you arrange it is a fact about right now rather than about the drawing - the
  same reasoning that makes station names the default caption there.
- Blocked track is drawn by **fading the square itself to 40%**, **whether or not anything is
  running** — the tile's own art, drawn faintly, so a blocked curve is still legibly a curve. Adam,
  2026-09-10: *"I want the shading to instead be the same tile with more transparency exactly like
  what happens when edges are locked in autonomy mode."* It was a grey wash painted over the square
  until then.

  The fade is applied **to the icon as it is painted** — a composite set before `super.paintComponent`
  — because nothing drawn afterwards can make what is underneath transparent. Everything autonomy
  draws on top, the train line included, is painted at full strength, so a square that is both still
  reads as both. `LayoutLabel.BLOCKED_ALPHA` is the number.

  **And it is applied once.** A square blocked by a standing train and also locked by a route a running
  train holds is drawn at the same 40%, not faded twice (Adam, 2026-09-12, OB-212: *"keep one level of
  opacity, dont stack."*). Two reasons to refuse a square are still one refused square. Built and
  validated (MT-375, `ui.testTheWashDoesNotStack`): the route's and the annotation's washes are told the
  region the tile has already faded and do not lay a second one over it.

  **This said "only while autonomy is running" between 2026-09-09 and 2026-09-09, and the reversal is
  the point of the change** (W7B-B1). The bound was Adam's own sentence - *"can we just grey out the
  tiles just like blocked edges while autonomy is running?"* - and the reason written down for it was
  that blocked track is a fact about routing, and nothing is routing when nothing is running.

  That reason is wrong about this railway, and the way to see it is that **the REFUSAL was never
  fenced**. `Layout.isPathClear` sweeps the covered edges in every tier at every time, correctly,
  because a tail lying across the rail is physical rather than a preference. So at idle a right-click
  manual send across a parked train's tail was refused - `errorTrackCoveredByStandingTrain` - over
  track the diagram painted as free. That is the same complaint that brought the grey back for the
  running case (*"a square the railway would not let a train onto looked exactly like free track"*),
  arriving through the other door, and the guard-and-affordance doctrine says the two must ask one
  question (OB-057/OB-090).

  Adam, asked about the idle case directly, 2026-09-09: *"yes, this greyout should appear at idle and
  be regenerated if a placement or train/track length is changed."*

  `ui.testTheGreyAppearsAtIdleToo` is the class; `ui.testBlockedTrackIsGreyWhileAutonomyRuns` keeps
  its name and its subject, having lost the word ONLY.

- **And it is regenerated by every operator gesture that changes it with no train moving.** Adam
  asked first for three - a locomotive placed or removed, a train's length changed, a tile's length
  changed - and then, on 2026-09-10, for all of them: *"make sure the shading and orange repaints on
  any autonomy or train/track length edit."* A station flag, an arrival side, a one-way direction and
  a reversal marking all change which edges exist, and therefore what a standing train covers.

  **One door carries the general case**: `rebuildRunningLayoutFromSetup` ends in
  `blockedTrackChanged()`, and every setup change goes through it. The refresh diffs the two sets and
  repaints only the squares whose mark changed, so a rebuild that alters nothing costs a comparison.

  The three original gestures still reach it by their own routes, and which route is not arbitrary:

  - a **placement** through `updateVisiblePoints`, at each of the diagram's placement doors - the
    keyboard's Control+X / Control+V / Delete, the right-click Place and Remove items, and
    `GraphLocAssign`;
  - a **tile length** through the setup rebuild it needs anyway, because an edge's length is baked
    into the built `Layout` by `GraphReducer` and cannot change without one - which is the general
    door above, reached first by this one;
  - a **train length** through `TrainControlUI.blockedTrackChanged`, and this is the one that had
    nothing at all. `Layout.edgesCoveredByStandingTrains` reads `getTrainLength()` off the locomotive
    every time it is asked, so a length typed into the dialog changes what the railway refuses
    immediately, with no rebuild in between and therefore nothing telling the drawing.
- **The line follows the road, not the square.** It runs along the route the train occupies through
  each tile, which is why a wash was the wrong way to say *"a train is here"*. Adam, 2026-09-08:
  *"instead of shading the entire tiles, we need to draw a line (let's say in orange) to show that
  the train is there. graying makes it look confusing on double curve tiles."* A double-curve tile
  carries two roads, and a train on one of them was indistinguishable from a train on the other. The
  grey that came back on 2026-09-09 does not reopen this: a square that is grey and not orange is not
  claiming a train is on either road, only that the track is unavailable.
- **The line is drawn on every square the train lies over, the sensor squares included.** The square a
  train stands on is always a sensor, so a line missing there is missing from the one square that matters
  most. Adam, 2026-09-23: *"when we draw orange lines, they don't overlap with sensors"* - on every sensor,
  occupied or not. They had been left out on purpose - *"a train standing there would be shown standing
  there"* - and his ruling is that the orange shows where the train is. Built 2026-09-23 (OB-277): the
  standing square is drawn and, since OB-278, spent first like every square behind it, and each sensor the body
  reaches is drawn along the road facing the track just walked, which names one arc even on a double
  curve. `regression.testTheWashIsNoLongerThanTheTrain.testTheSquareTheTrainStandsOnIsOrange` and
  `testASensorTheTailLiesAcrossIsOrange`, the second with the train one unit too short as its control.
  **A route tile is drawn too** (OB-279): it carries whatever the track beside it carries, and its road is
  named by the sides it joins rather than by the port map, so the orange and the grey were skipped there
  until 2026-09-23. `core.testRouteTilePlacement.testARouteTilesRoadCanBeDrawn`.
- **The line is as long as the train; the grey is as long as the edge.** That difference is what
  separates the two marks. The line walks square by square from where the train stands, each square
  paying its own length, and stops when the train is used up - drawing the whole edge instead washed
  seventeen squares for a train of length one (MT-309). The grey is the whole edge on purpose,
  because the whole edge is what routing refuses.
- **The marks survive a highlight.** An accessory change flashes the square it commands; when the
  flash ends the square is asked again rather than remembered, because the train may have moved while
  the highlight was showing. The train line is painted over the tile's icon; the refusal is the icon itself, drawn faintly. Neither is baked into the image - both are applied on every paint, which is what keeps an accessory highlight from taking either away with it.
- **Both are refreshed together, and only where they changed.** One pass recomputes both answers and
  repaints exactly the squares whose line or wash differs from what it was. The whole diagram is
  never rebuilt for either: that is MT-334, and what it looks like is the page flickering.
- **And never on the event thread** (OB-192). Both answers come from
  `Layout.edgesCoveredByStandingTrains`, which is `synchronized` on the `Layout` - the same monitor a
  dispatch holds for the whole of `configureAndLockPath`, a sleep per accessory of the path. Asked
  from the event thread that is not a slow refresh but a deadlock: a driving thread inside that
  monitor commands an accessory and `MarklinAccessory.setSwitched` calls `repaintSwitch`, which is
  `synchronized` on the window. Adam, 2026-09-09: *"starting autonomous operation ... makes the UI
  unresponsive. Trains still run, but nothing is repainted, and controls are stuck."* So the pass runs
  on a worker and the marks land a beat after they are asked for. Nothing about **what** is drawn
  changes; only where the work happens.
- **WHAT IS DRAWN IS WHAT IS REFUSED, once both marks are counted.** The grey is the squares
  standing trains CLAIM - `Layout.placesCoveredByStandingTrains`, which is what `isPathClear`'s
  shared-metal sweep refuses a path over - so blocked track is visible as blocked track, and track a
  train does not lie on is not grey (OB-280, below).

  This bullet said the opposite between 2026-09-08 and 2026-09-09, in bold, and the reversal is the
  point of the change. Replacing the wash with the line answered the double-curve complaint and made
  the picture a strict subset of the refusal: a square the railway would not let a train onto looked
  exactly like free track. Adam: *"that is the whole point."* Two marks answer both questions at
  once, where narrowing one mark to answer the second question could only ever lose the first.

  **Settled again on 2026-09-23 (OB-208), and it is this bullet that stands.** Adam, asked whether the
  grey should cover only where the train is or the whole stretch it blocks: *the whole stretch* - *"orange
  shows where the train is, gray shows what's blocked."* The narrowing of OB-207 was made when the grey
  was the only mark; with the orange line saying where the train is, the grey says what routing refuses.
  Built 2026-09-23: the grey is every tile of every covered edge again, per road so a double curve fades only
  the arc the edge runs over (`AutonomySession.routesBlockedByStandingTrains`), and the fade follows the grey's
  roads rather than the orange's. This reverses what MT-373 validated on 2026-09-12, on his ruling.

  **And settled a third time the same day (OB-280), and it is this that stands.** Adam: *"grey what routing
  actually refuses.  if the train doesn't protrude past the switch, there should be nothing else to gray."*
  A path driving along a covered edge ends at or runs through the square the train stands on, which is
  claimed; a path crossing only the far end of that edge is NOT refused (OB-207) - and the whole-edge grey said
  it was. So the grey is the squares the railway claims under standing trains: the whole square where the
  place is the square (a train on one leg of a turnout blocks the other), one road where the place names a road
  (a double curve or an overpass). With OB-278, a train that fits on its berth greys that square and nothing
  behind it. `AutonomySession.routesBlockedByStandingTrains`,
  `ui.testTheGreyAppearsAtIdleToo.testTheGreyIsWhatTheRailwayClaims`.

  **What is still not drawn**, and is explained in the "why not moving" view rather than on the
  diagram:

  - a covered edge's squares the train does not lie on, which routing does not refuse (OB-280);
  - a path refused because an edge it uses **shares metal** with a covered one - the lock-edge
    relation `GraphReducer` derives from shared tiles - is refused over a square that is itself
    greyed, but the *path* is not marked as refused anywhere on the diagram.

  **And the answer has to be readable, which is a rule of its own** (OB-191). The "why not moving"
  answer is a train, a dozen stations and the reason each one was refused, and it is shown in the
  strip across the top of the editor. That strip has a height floor so that messages coming and going
  do not move the diagram, and a height CAP so that a long answer does not eat the page - and an
  answer longer than the cap must be given the whole strip, because a viewport smaller than its
  contents is the only thing that makes the strip's scrollbar appear. Laid out at its preferred height
  instead, it was shrunk toward its minimum - measured at five pixels of a two-hundred-and-sixty pixel
  answer - and the strip stood open at full height with nothing in it. Adam: *"the banner expands, but
  I see no text."* A short message is still centred in the strip (OB-151); the two rules are one
  statement in `AutonomyBanner.CentredButNeverTaller`, because settings that have to be read together
  are settings that come apart.

  **And it is worked out on a worker, not on the click** (D3-C1). The answer comes from
  `Layout.explainDestinations`, which takes the railway's monitor and walks the graph, and then a
  reduced-path search for every station the railway offers - so asking for it on the event thread is
  the rule below being broken by the one tool a stuck railway sends people to. The click captures the
  layout and the station index, says *"Working out the reasons..."*, and the report and its lines are
  painted when they land. Two clicks in a row are two searches and the later one wins: an answer to a
  question the user has moved on from is discarded rather than painted over the one they are waiting
  for. It was never a live freeze - the editor cannot be open while autonomy is running (OB-047) - and
  it is moved anyway. Adam: *"I would rather take it off EDT. It's not critical now, but we want to
  avoid these pitfalls."*

**Known limits, deliberately.** A tail that really does reach past a fork, or across unmeasured
track, is not blocked. Nor is the tail of a train whose recorded arrival side names a side no track now
leaves its square by - a value left stale by an edit to the diagram: the walk stops before its first
hop and claims nothing, even where the geometry leaves only one way back (`Layout.walkOneTail`; this was
stated only in the code until OB-264). All three under-claim. Blocking on a guess is still a refusal, and it stops trains
that could have run.

**Two things about the room sum were open, and Adam settled both on 2026-09-11** (MON-C13). They had
lived only in a comment at `Layout.isPathClear` until 2026-09-08, against this document's own promise at
the top that a known limit is stated here. The second took two rulings and one reverted commit.

1. **A positive length counts, and that is deliberate.** On a diagram-built graph an edge's length is
   the sum of `max(0, tileLength)` over the tiles it spans, so one measured tile out of five gives a
   positive number and the sum reads it as a measured segment - which under-counts and can refuse a
   train that fits. Asked whether an edge should count as measured only when every tile in it is:
   *"no, any nonzero length on a logical edge between 2 stations will count."*

   So this is the rule rather than a limitation. It errs towards refusing, which is the safe direction,
   and the remedy in the operator's hands is to measure the rest of the tiles.

2. **A REVERSAL SPLITS THE RUN IN, AND THE TRAIN HAS TO FIT IN BOTH HALVES** (Adam, 2026-09-11).

   Asked whether *"the track segments leading up to it"* means up to the reversal or up to the berth:
   *"it can be either the reversal or the berth, depending on where switches are.  both need to be long
   enough."*  That was read as one bound - stop the walk at whichever is met first - and two tests
   refused it, so it was put to him again with the figures, and he gave them:

   > four-unit train in a three-unit berth with a two-unit approach: refused both because of the berth
   > (3<4) and the track length that potentially couldn't fit the train while reversing.  if the train
   > isn't reversing, then it should be accepted as long as the berth is long enough.

   > 8 unit train on a 9-unit runin would just be refused because 3<8.  if the max train length at the
   > berth was set to 8, we would be OK.  in short, your tests should consider both.

   So there are **two stretches and two comparisons**, and a train has to fit in each:

   | Where the train stands | The stretch it has to fit in |
   | --- | --- |
   | the berth, after turning | back from the berth to the reversal, or to the last switch, whichever is met first |
   | the reversing point, while it changes direction | back from the reversal to the last switch, or to the start of the route |

   The first is the walk's own stop (`Layout.measuredRoomAtTheEndOf`); the second is a square the train
   **comes to rest on**, so `whyTooLongForThisRoute` judges it the way it judges a berth rather than the
   way it judges a square the train rolls over. Where nothing on the route turns the train neither
   stretch exists and the sum is the whole run in, which is his other sentence - *"if the train isn't
   reversing, then it should be accepted as long as the berth is long enough."*

   `core.testNonReversibleTrains` holds both, each isolated by making the other stretch long enough that
   it cannot be the one objecting, and reading the refusal's sentence rather than its yes-or-no.

   **His 3 is neither of the numbers the rule produces on that fixture** - the berth beyond the turn is
   4 and the approach behind it 5 - and both refuse an eight-unit train, so the verdict is his and the
   arithmetic is the rule's. Recorded rather than rounded off, because the next person to read the
   ruling will do the same sum.

   **The first attempt was reverted on the strength of a test that was a guess.** Those two tests
   asserted that a four-unit train fits a three-unit berth with a two-unit approach, and that an
   eight-unit train fits a nine-unit run in split by a reversal - both because the figures add up end to
   end. Nobody had ruled on either. A test that encodes a reading of an unstated rule is indistinguishable
   from a test that encodes the rule, and reverting working code to keep one green cost half a day.

   **What it does to his railway: thirty-five journeys BACK, and none taken away.** The census measured
   1670 journeys refused on the way before the two bounds and 1635 after, with the berth count unmoved -
   because a shorter stretch with nothing measured in it is no information rather than no room, which is
   bullet 1's doctrine again. Those thirty-five have a long measured approach and an unmeasured berth
   beyond the turn; the way to a real answer about them is to measure that berth, and
   `autosetup.ui.checkRunInShorterThanTheBerth` is the notice that asks.

**A STATION TOLD IT HOLDS MORE TRAIN THAN ITS TRACK MEASURES GETS A NOTICE** (Adam, 2026-09-11):
*"let's add an autonomy editor notice that alerts the user if a run-in is shorter than the berth length,
that way they can decide if it makes sense or not.  for example, a station of length 4 may have two
segments of tracks on either side of a switch, of length 2.  that is acceptable and its limitations are
understood."*

Two numbers can refuse a train a platform and different hands set them: `maxTrainLength` is what somebody
typed, and the measured room is what the track says. Where the measured room is smaller the typed maximum
never binds, and a train inside it is refused by a rule quoting a number nobody typed.

- It fires per STATION, on the SMALLEST room any of its approaches has, and the sentence carries both
  numbers - the maximum first, the room second.
- The room is `ReducedEdge.getRoomAtTheEnd()`: the track from the last switch on the arriving edge to the
  platform, which is what `Layout.measuredRoomAtTheEndOf` counts. **A notice quoting a number the refusal
  would not quote sends the reader to measure the wrong stretch.**
- An arriving edge crossing no switch is skipped unless trains turn round where it starts - there the
  guard walks on back through earlier edges, so that edge bounds nothing.
- Silent where either side is missing: no typed maximum is `checkNoMaxTrainLength`'s sentence, an
  unmeasured stretch is `checkReversalNeedsLength`'s, and a railway measuring no track at all has decided
  not to model lengths.
- A **NOTICE**, not a warning, and that is his ruling rather than a grading: his own example is a railway
  with nothing wrong with it, and a setup where that is understood should not carry a warning for ever.

`core.testAutonomyDiagramSession.testTheEditorSaysWhenAPlatformHoldsLessTrackThanItClaims` holds it, on
his own 2 + 2 example and on the reversal branch.

**Both were written while the sum ran only at termini and reversing berths, and that fence is gone**
(MT-262, 2026-09-08; D2-C2). The rule now runs at every destination and in every tier, so whatever
either bullet gets wrong, it gets wrong everywhere rather than at a handful of reversal squares.

**Measured on the operator's own railway**, because "everywhere" is not a number. The census is
`core.testTheRoomRuleCensusOnTheRealLayout`, which runs in the battery: for every ordered pair of
station Points it searches a path, then asks each of six trains of its own, one to six units long, in
turn whether the widened rule refuses it and the fence it replaced - `isTerminus() || isReversing()` -
would not have.

**Those six lengths were read out of his locomotive database until 2026-09-09**, and are the class's
own now (Adam: *"generate trains programmatically in the tests, and give them semantic names"*). The
set is the same, so every number below is unchanged - and it is now a set this file chose. Measuring
one of his trains, or buying one, used to move the population the census is over and could change
the count below with nothing saying why.

| | |
|---|---|
| ordered station pairs | 1332 (37 destination Points, each against the other 36; 1980 before the refreeze of 2026-09-23) |
| of those, routable | 956 (1301 before the refreeze, 1354 after OB-229, 1848 before it) |
| train lengths asked, the census's own | 1, 2, 3, 4, 5, 6 |
| journeys the widening NEWLY refuses | **505** (610 before the refreeze, 880 before OB-229) |
| every one of them arriving at | BottomMainA (eastbound), BottomMainB (eastbound), BottomMainPost (northbound) - each measured at **ONE** unit of room |

**Re-measured 2026-09-23, on the railway refrozen from Adam's measured layout.** His own lengths and station sizes are
taken off in the census's copy first, so this is still the three-tile experiment; what moved is the railway under it.
His one-way marks, barred sides and the compulsory turn at BottomMainC leave 37 destination Points rather than 45.
BottomMainC is off the list of berths because it is a terminus now - one copy, every train turned - and a refusal at a
terminus is not new. No route turns at a measured square under these three tiles any more, so the census no longer
reaches the turn bound; `core.testNonReversibleTrains` and `core.testATrainIsJudgedOnlyWhereItStops` hold it.
`core.testWhichSquaresTheRoomRuleClosesOff`, on the same three tiles, still finds no square that offers a longer train
nothing at all.

The room rule declines to judge almost everything else: the railway carries only a few measurements.

**Re-measured 2026-09-15, after OB-229.** The census walks the railway's own route search, and that search no longer
extends a route through a terminus that is not its end - the route check refuses every such route (section 7). So
the pairs whose only routes ran through a terminus are no longer counted as routable, and the journeys over those
routes - every one of them refused before length ever arose - no longer count as refused for room: 1354 routable pairs
rather than 1848, 610 newly refused rather than 880, 1030 refused at the berth rather than 1760. A probe put every route
both searches could yield to the route check: none of the dropped pairs was drivable, and 153 pairs the old search
never found became drivable. The four berths and their one unit of room are unchanged, and they are what this section
reasons from. **One number rose, and its old zero was hiding it:** about 60 journeys counted as refused on the way -
the berth has room, and every route to it turns at a square too short for the train. Each of them used to have a route
through a terminus as well, which the room rules alone admit and the route check always refused, so none was counted and
none was ever drivable. The square refusing them was `BottomMainB (westbound, reverse)`, a turn copy of one of the four
berths below - the turn bound of 2026-09-11 at one of Adam's own one-unit squares, not a refusal somewhere new.

**Re-measured again the same day, after the copy ruling** (AMR-B1, below in section 7). Every one of those 60 journeys
was over a route that doubled back through another copy of its own start or end, and Adam ruled those out - *"We need
to refuse both. A copy makes a cycle."* They are not walked at all now, so **routable pairs are 1301** and the
journeys refused on the way are back to **0**: the pass-through rule of 2026-09-09 costs nothing on this railway
today, and `BottomMainB (westbound, reverse)` no longer refuses anything. The turn bound still bites, at 15
route-journeys rather than the 155 it reached after OB-229 - the same reason, the long routes being the laps. What
holds those rules now that two of the census's numbers are zero or near it: the turn band of 5 to 40 in
`core.testTheRoomRuleCensusOnTheRealLayout`, and `core.testATrainIsJudgedOnlyWhereItStops` on fixtures of its own.
14 of the railway's 572 reachable station pairs lose their last route, every one of them from `BottomMainPost`.

Those four are the berths Adam named himself, with the number he gave: *"bottommainb, which has a
length of 1 leading up to its switch"*, and MT-262's own report of `BottomMainA (eastbound)` offered
*"with ONE measured unit of room behind it"*. So the widening is refusing the journeys he asked to have
refused, at the squares he was looking at, and not - on this railway, today - a wider set that bullet 1
has under-counted. A layout with lengths scattered over more of its track would be a different answer,
and the way to find out is to run that census again rather than to reason about it. **Now it can be
run.**

**This paragraph said 332 of 3488 until 2026-09-08**, from a probe that was never committed. When the
census was written as something that could be re-run it found 1980 pairs and 880 refusals, and the
earlier probe's population cannot be recovered to say where the difference came from - which is the
whole argument for committing it. The four berths and the one unit of room reproduced exactly, and
they are what this section reasons from.

---

### 5d. Atomic Routes, and the track a train is handed back

**A path is held whole until the run ends - unless Atomic Routes is off, and then each edge is given
back as the tail clears it.**  Adam runs with `"atomicRoutes": false`, so this is his railway's
ordinary behaviour, not a corner of it.

Giving an edge back early is what lets a second train follow a first down the same road instead of
waiting for it to arrive.  The question is only ever *has the tail passed this edge*, and
`Layout.tailHasProvablyPassed` answers it from two facts: how far the head has travelled since the end
of the edge, and how long the train is.

**It is wrongly true in exactly two states, and both of them are ordinary.**

- **A path with no measured edge anywhere on it.**  With nothing measured the honest answer to "how
  far has the head gone" is "no idea", and the rule reads that as clear.  A railway with lengths on
  its platforms and nowhere else is in this state for most of its paths.
- **A train whose length is 0**, which is what `Locomotive.trainLength` holds until somebody sets it.
  `behind >= trainLength` is then true the first time every edge is asked about, so the whole railway
  is handed back under the train however well the track is measured.

In either state the edge behind a moving train is released while the train is still lying across it,
and the next dispatch is routed onto occupied track.  **So the railway is not allowed to run
non-atomic while either state holds**, and that rule is asked at every door that could start a train
or load a setting:

- the **Atomic Routes checkbox** refuses the gesture and says what is unmeasured - somebody is there,
  and one gesture from fixing it;
- the **two load doors** (Validate on the autonomy tab, and the editor's apply) force the setting back
  ON and write the reason to the log, because a file has nobody at it and refusing the load would make
  a configuration the operator already has unopenable;
- the **five dispatch doors** - Start, Execute Timetable, Return Home, and the two hand dispatches -
  do the same, because a TRAIN's length is written on the live layout long after any file was parsed.

**What counts is whole paths and trains, not squares.**  An unmeasured switch square does not count,
because an edge's length is the sum of its squares and one measured square gives the edge a length.  A
rail with measured track between it and every destination does not count either, because a path ends
at a destination and so can never be unmeasured end to end - which is enforced where paths are BUILT,
in `Layout.bfs` and `HomeStaging.firstClearRoute`, not where they are checked.  And a rail is counted
once, not once per direction.

So the Unmeasured Track display in the editor is **not** the list to work from: it answers a different
question, about how far a tail reaches.  The refusal's own list is the one to measure, and it comes
back in alphabetical order because the message shows only the first three of it.

*(`VD12-R4`, `VD13-B1/B2/B3`, `VD14-B1/C6`, `VD16-B2`, `GS-B1`.  `MT-470` is the hands-on test.)*

### 5e. Permanently-set turnouts: fork to base only, and they still bound a berth

> *"They are trailable, so it's about intent and documentation.  They just can't go from the base to
> the fork since we don't know which way they will end up.  Fork to base is OK."* - Adam, 2026-09-22

A `CUSTOM_PERM_*` tile is the operator's declaration that a turnout has no address and cannot be
thrown.  It is a supported thing to draw, not an error: the setup loads with a warning, and `TilePorts`
gives each of the four a port map of its own.

**Autonomy never makes a facing move over one.**  With the blades stuck in a position nobody has
recorded, a move from the base out to the fork cannot choose a leg - so that move does not exist in the
port map at all.  A move the other way does: the turnouts are trailable, so a train running fork to
base merges safely whichever way the blades lie, and both legs may trail in.  That restriction is a
property of the hardware and lives in `TilePorts`; a direction the operator authors on the tile ANDs
with it and can only narrow it further.

**A scissors crossing is different and is refused outright** - `CUSTOM_SCISSORS` and
`CUSTOM_PERM_SCISSORS` both - because it is a drawing convention, two tiles depicting one double slip,
whose topology cannot be expressed per tile.  And an **undeclared** address-less switch is refused too:
routing over one would mean trusting it to be lying the right way, which is exactly what
`CUSTOM_PERM_*` exists to declare and what nobody declared there.

**It bounds a berth like any other switch** (Adam, 2026-09-22: *"treat them the same as regular
switches for the purposes of the check"*, reversing half of his answer of 2026-09-15).  The room walk
of 5a stops at a permanent turnout, so a berth beyond one is measured from the points rather than from
wherever the run began.  The two questions are not the same question, which is why the answers differ:

- **routing** asks which road a train takes, and over a permanent turnout it takes the only one there
  is - so it is not a switch for that purpose, and never was;
- **the berth check** asks where a train comes to REST.  A train too long for the berth hangs back
  across the toe and blocks the other leg's merge, and it does that whichever way the blades lie.

**A crossing still does not stop the walk** (his 2026-09-15 answer, unchanged).  Nothing merges at a
crossing, so a train standing across one fouls another route's track rather than its own road - and
that is the tail walk's business in 5c, not the room walk's.

**And the editor does not offer an arrow for the road that is not there.**  A direction the operator
authors ANDs with the hardware's restriction, so on one of these tiles "toward the fork" and "both
ways" restrict nothing - no train could take that road either way - and drawing a green arrow for it
says something untrue about the railway.  The menu offers what `TileGraph.directionIsPossible` allows.
  That is **not** the same question the walk asks - `directionAllows` and it disagree about `NONE`,
which is always offerable and never passable, and whether a train MOVES is `isPassable` (VD18-R4).
  Where only one way is left,
that is the one ticked, because the stored answer for these tiles is `BOTH` by default and `BOTH` is
not on offer.  Shutting a route is always offered: that is a real answer whichever way it could run.

*(`IND9X-A2` / `OB-233`, and Adam's instruction of 2026-09-22 that *"green arrows in the direction that
can't be chosen shouldn't be offered"*.  Held by
`core.testAutonomyDiagramReducer.testTheRoomWalkStopsAtASwitchAndAPermanentTurnoutButNotACrossing` and
`core.testAutonomyDiagramPorts.testOnlyThePossibleDirectionsAreOffered`.)*

### The autonomy editor's keyboard doors

Five shortcuts act on **the square the pointer is over**, and they ask one question to find it -
`LayoutEditor.hoveredSquare()`, which answers null when the remembered label is not on the grid it is
asked about, and forgets it while it is there. They read a field nothing cleared until 2026-09-10, so
after stepping to another page each of them named a square on the page before (OB-198).

| | |
|---|---|
| **Control+S** | Names the square — the right-click menu's **Rename**. Asks `canBeNamed`, so it does nothing on plain track (MT-313). |
| **Control+E** | Opens **Segment Length** on it (FR-066). Adam picked the key: *"let's do E"*, Control+D being taken twice over. |
| **Control+B** | Opens **Maximum Train Length** on it (OB-197's free letter, picked for the berth the maximum belongs to). Asks the station menu's own question, so it does nothing on a square that is not a station. |
| **Control+H** | Sets the home locomotive. |
| **Control+N** | **Show a Station Name Here** on it (FR-086), asking the menu's own `offersAStationName`. |

**Control+L steps through the caption options, and text labels are one of them** (Adam, 2026-09-23,
OB-272: *"make text labels be a dedicated setting, and hide the text labels unless it is selected.  Also,
make control+L cycle the options"*, and asked which options: *"the dropdown's 4, plus add an option to
the dropdown that shows the labels only"*). So the dropdown is Stations, Parked Locs, Homes, None and
Labels only, in that order, and the key moves to the next of the five. The text written on squares is shown under
**Labels only** and nowhere else: choosing a caption no longer turns it on, which it has done since FR-061
read *None* as the text switch turned off. Built 2026-09-23: Labels Only is appended after None so a remembered
choice keeps its meaning, the grid draws a caption under the three caption modes and the writing under Labels
Only, and in the track editor Control+L is still the text switch.
`regression.testARememberedNoneOpensWithTheCaptionsOff`.

**Control+E asks the menu's own question and writes where the menu writes.** `offersALength` is
`buildTileMenu`'s three early returns in one place — a page the session knows, not a text label, not an
ignored square — and the menu adds its item inside it, so the guard and the affordance are one
expression rather than two that agree today. The length lands on `squareTheLengthWouldGoOn`, which is
the **run leader**: a run of plain track has one square that speaks for it, and both doors write there,
so measuring a run through both does not count it twice.

**Control plus any other letter does nothing in the main window**, since OB-197. Its key chain ended in
an arm that took every letter whether or not Control was held, so Control+B and six others selected a
locomotive button; `regression.testNoTwoShortcutsShareAKey` reads both windows' handlers on every run
and prints what is free in both.

### The main window's key map reaches the whole window

Adam, 2026-09-11: *"any part of the app should respect the key mapping (locomotive selector) and all
related keyboard shortcuts."*

The map is a `KeyListener`, and a listener hears only the component holding the keyboard. It is
attached to the tabbed pane and a few panels, so the letters drove trains while one of those had the
focus and did nothing from anywhere else - click a row in the route table or a button in the autonomy
editor and the railway stopped answering the keyboard, with nothing on screen to say why.

**Four rounds of `OB-170` answered that by moving the focus back**, and that is right when nothing in
particular holds it: a window that has just opened, or one being alt-tabbed back to. It cannot be the
answer when the operator is USING what holds the focus, because the fix would be to take the keyboard
off them.

So `TrainControlUI.letTheWholeWindowDriveTrains` registers a **key event post-processor**. The focus
manager runs those after the focused component has been offered a key, and only for keys that came
back unconsumed - so the precedence rule is the toolkit's rather than a list kept here:

- a component that wants a key keeps it: arrows still move a table's selection, space still presses the
  focused button, a list keeps its type-ahead;
- what nothing claimed reaches the map;
- **typing is stepped around by name**, because a text component claims a letter at KEY_TYPED, which is
  after this runs, and taking a letter out of a half-typed line is worse than the fault being fixed;
- and only in this window: a dialog's components do not descend from the frame, so its keys stay its
  own.

The focus-moving of `OB-170` stays as it was. It is no longer what makes the keys work, and the two do
not fight: the post-processor only ever sees what the focus owner did not want.

`regression.testTheKeyMapReachesTheWholeWindow` holds the three halves of that rule.

---

## 6. Parking and Return Home

- Return Home stages every locomotive that has a home, as one plan.
- One locomotive has one home; assigning a home takes it away from wherever it was.
- **A home has a facing, and Return Home brings the train back in it** (Adam, 2026-09-23, OB-282: *"yes, it should
  accomplish the facing.  but it's also reasonable to expect that the input facings are ones realistic on the
  layout.  we shouldn't allow an impossible facing to be saved."*). The facing is **the way the train is facing
  when the home is set** (*"Direction it is facing when home is set"*); a home given to a train standing elsewhere
  **asks** which way it should face (*"prompt the user for the direction"*), offering only the facings of copies
  trains may arrive at. The setup keeps it as `homeFacing`, the build puts the home on the copy facing that way and
  marks it, and a home set on the running diagram is held to the copy it was set on. Return Home then counts a train
  home only on a copy FACING that way - where MT-165 counted any copy of the square, which on a square with two
  platforms facing opposite ways brought trains home turned round. Not by arrival side: the home copy's turning twin
  shares its arrival and points the other way, so on a square trains may turn at (BottomMainB, EN57-947's home) a
  train left turned round there was reported already home (TDY-B1, AUT-B2);
  `core.testATrainComesHomeFacingTheWayItWasHomed.testATrainTurnedRoundOnItsHomeIsNotHome`. A home with no facing -
  an old setup, or a copy no train may arrive at - is still the square, either way.
  `core.testATrainComesHomeFacingTheWayItWasHomed`,
  `core.testAutonomyDiagramSession.testAHomeRemembersTheWayItsTrainWasFacing`.
- It refuses an inactive **start** (see §1) and obeys every length rule in §5.
- It **does** read the occupancy restrictions of §1, in both halves, since Adam's ruling of
  2026-09-10 that they bind every tier. **Planning** asks them against the occupancy the plan has
  reached (`HomeStaging.plannedOccupancy`), and **execution** asks them because every leg goes through
  `Layout.isPathClear`, which has no tier fence on that rule at all.

  The two have to move together: a planner that offered a leg the runtime refuses is OB-073 — the run
  retries until it gives up and stops with the fleet half-staged — and `auditAgainstRuntime` is what
  proves the planner offers nothing the runtime would refuse.

  **So two homes that hold each other back are a deadlock.** Whichever train arrives last finds its
  station closed by one already parked, and Return Home answers `NO_PLAN_FOUND` — not `IMPOSSIBLE`,
  which names locomotives and asserts no arrangement exists. See §1.
- It never asks the operator anything: the operator's decision was made when the homes were set.
- **A train that cannot reverse is turned on the way only to go home** (Adam, 2026-09-15, AMH-B1: *"this should
  only be allowed if the train is going to reverse into its berth on the next turn"*). Every leg of a plan ends
  with the train turned round if it stops at a terminus or a reversing point, so a plan may stop such a train on one
  only when the train has a home and its next move takes it there; a train the railway already had standing on one
  moves as before. Where the only arrangement needs anything else, the answer is `NO_PLAN_FOUND`.
  `core.testHomeStaging.testATrainThatCannotReverseIsTurnedOnTheWayOnlyToGoHome`.
- **And not turned in the middle of a move either** (Adam, 2026-09-15, AMV-B1). A route may turn a train at a
  reversing point on the way and carry on, which for a train that cannot reverse means running on backwards. That is
  allowed only where the move ends somewhere the turn was for: its home, a berth, or a square it comes to rest facing
  out of - which is how a train backs into a berth past a reversing point (MT-245). Adam, asked whether the ruling
  covers a turn mid-move: *"It should be the first option, but the locking mechanism will refuse it. That's why we
  started the 2 step process for parking, which is OK in my opinion."*
  `core.testHomeStaging.testATrainThatCannotReverseIsNotTurnedMidMoveAndSentOn`.
- **It knows where the tails of the trains it moves will lie** (OB-228, Adam on MT-335, 2026-09-15: *"the path
  stayed blocked"*). A train the plan has moved stands at the end of the route the plan gave it, having come in by
  that route's last rail and along that route - what an arrival records - so its tail is walked by the runtime's own
  code (`Layout.walkOneTail`, asked through `edgesATailWouldCover`) and no later move is routed over it. A train
  that has not moved is judged by the tail it has on the railway, as before (OB-184). Making every move it can, in
  the order it meets the trains, can now leave a tail across the run another train needs, so when the search from
  that arrangement finds nothing it searches again from the start.
  `core.testReturnHomeKeepsClearOfTheTailsItLeaves`.

**Whether it is on offer is asked once, off the event thread** (OB-192, second round). "Is anything
away from home" is cheap but not free of a lock: it builds a `HomeStaging.snapshot`, which calls
`Layout.getHomeStations` - `synchronized` on the `Layout`, the same monitor a dispatch holds for the
whole of `configureAndLockPath` and a path search holds for the whole of `getPossiblePaths`. Asked
from the event thread, as the button refresh, the diagram's right-click menu and the click handler
each did, that is the freeze of §5c by a second door. There is one asker now, on a worker; the button
it paints is what every other surface reads, so no two surfaces can describe the situation
differently and none of them can wait on the railway to find out.

**THE RULE IS GENERAL, AND IT IS NOW A CENSUS RATHER THAN A HABIT** (D3-B2). The two paragraphs above
are the same sentence about two doors, and the freeze has arrived through seven of them in two rounds
— each time found by reading, each time one more caller than the last sweep knew about. The rule
itself is one line:

> **Nothing on the event thread may call a `synchronized` method of `Layout`.**

They all take one monitor. A dispatch holds it for the whole of `configureAndLockPath` — a sleep per
accessory of the path — and `AutoLocomotiveStatus.findPaths` holds it for a search of the whole graph
on every panel refresh with nothing running at all. Waiting for it on the event thread is a window
that does not repaint and controls that do not answer while the trains go on running, which is what
Adam reported twice as OB-192.

`testNothingOnTheEventThreadTakesTheRailwaysMonitor` reads the list of `synchronized` methods out of
`Layout.java` and requires **every** call to one of them from `gui/` or `automationui/` to be written
down with the thread it is on. The way past is a line in that list, and it must say either
`OFF THE EVENT THREAD:` and name the thread, or `ON THE EVENT THREAD:` and give the reason — a menu
item the operator has just clicked and a placement made from a modal dialog are both legitimate, and
a guard with no way past is one people delete. What it cannot do is prove a thread, so
`testTheDiagramRefreshDoesNotWaitOnTheRailway` still *measures* the doors that matter with the monitor
actually held. The census says which doors exist; the measurement says two of them are shut.

The three most recent to be shut are the diagram's **right-click menu**, which gathers its path list
on a worker before the menu is built (D3-A1); the **station captions**, whose "can autonomy choose
this square" answer is worked out on `CoveredTrackRenderer` and read from a field — so a page change
or a Show Inactive Labels toggle during a run costs a set lookup rather than the rest of somebody's
departure (D3-B1); and the editor's **"why is this train not moving"** tool, which asks the railway on
its own worker and paints the report when it lands (D3-C1).

**There is no `ON THE EVENT THREAD` allowance left for a graph walk.** Every one that remains is a
change of state the operator has just confirmed, or a read made when nothing else can be holding the
monitor; the last search was the why tool's, and it was moved because a rule with one standing
exception is a rule with a way in.

---

## 6a. Editing the setup while the railway is using it

**Escape lets go of what the editor is holding, and closes it when there is nothing left to let go
of** (FR-065). Adam: *"escape closes autonomy/track editor - same as closing via button, with warning
shown as needed."* One press with nothing armed closes the window down the same path its Cancel button
takes, unsaved-work prompt included - and that is the state an editor is in almost all the time. One
press with something armed drops that instead: an armed tool or a half-made two-click gesture in the
autonomy editor, and the picked squares, the copied group and the picking mode in the track editor.
A second press then closes it.

The order is what makes both requests answerable with one key. Closing outright would take a
half-finished gesture with it, and take it through the prompt that asks whether to throw the
afternoon's edits away - so a user who armed the wrong tool and pressed Escape to think again would be
asked to discard their work. Pressing Escape twice costs nothing; there is no way back from a window
that closed.

It is handled in the editor frame's own key listener, and nowhere else. The autonomy column used to
bind Escape as well, `WHEN_IN_FOCUSED_WINDOW`, which never fired: every control in that window is made
unfocusable so the frame keeps the keyboard, and a top-level frame has no parent chain for the focus
manager to walk. Two handlers could not be kept, either - the frame's runs its work deferred, so a
binding would have disarmed the tool first and left the frame's branch finding nothing armed and
closing the window: one press doing both.

**Nothing that changes the shape of the graph may run while trains are moving or a plan is being
made.** Renaming a point, deleting a point and deleting an edge all refuse while `isRunning()` or
`isStagingInProgress()`. A rename mutates a `Point` under live visited sets; a delete removes the
object those sets and already-issued paths hold.

**Staging counts as running.** The Return Home planner walks these structures with nothing dispatched
at all, which is exactly the window a bare "is autonomy running" flag waves through.

**The greying on the menu is not this guard.** Menu items are greyed when the popup *opens* and the
action fires when it is *clicked*; starting autonomy from another window in between leaves a live item
over a running railway. The refusal has to be in the method, and the menu asks the same question so
the two agree.

> *"Where a train IS is a fact, and where the file thinks it is is a record."* - Adam, 2026-09-08,
> ruling on OB-183

**Every setup edit regenerates the running layout from the setup, and where the two disagree about a
train the railway wins.** A run moves trains, where they ended up lives only in the running layout,
and nothing writes it back to the setup when the run ends - so a setup that has not been captured
since is stale about exactly those trains. **That applies to the recorded facing as well as to the
placement**, and it is why nothing that runs when a run ends may work out an answer by *changing* what
the setup records - see §3. A recorded facing is also never cleared, so an empty square still carries
the last occupant's, which makes "there is a value here" no evidence that it is this train's.

Regenerating from it would put each one back where it set off, in the model and then on disk, and
occupancy is the model's own record rather than the sensors: the next dispatch could then be routed
into a block that is physically occupied.

**The one exception is a train whose placement the edit was about**, and the doors that make such an
edit say which train it is. Placing a locomotive from the autonomy editor, taking one off a square,
and clearing every placement all write the new answer into the setup and nowhere else, so for those
trains the setup IS the newer answer and the rebuild's placement stands. Every other door that moves
a train - the track diagram's own place, facing and assign items - writes the running layout too, so
it needs no exception: the record and the setup already agree.

Neither half is inferred from the fact that a rebuild is happening. That inference is what made the
rule wrong in both directions within one day: an edit undone on the way to being redrawn when the
railway always won, and a train teleported back to its pre-run square when the setup always did. The
surface that reaches this most often has no editor in it at all - the track diagram viewer's
right-click autonomy menu rebuilds after every gesture, and nothing on that path ever captures where
the trains are.

---

## 7. Routing checks in the editor

**Path Type (Auto / Manual)** decides which tier the check answers for. The two differ **only** in
where a train may be sent: an inactive square stops both, a barred arrival stops both, a compulsory
turn turns both.

**A BARRED ARRIVAL STOPS BEING SENT THERE AND NOTHING ELSE** (Adam, 2026-09-23, of RampDown and
BottomMainPost: *"may need station restrictions but not pass through restrictions, since they are meant
for shunting only"*, with *"make sure trains can still go through there"*). The restriction is spent
entirely on one flag: `AutonomyBuilder.arrivalAllowed` makes that copy of the square `station: false`.
The copy is still built, still carries its edges and is still crossed by any route that runs over it -
so a square can be closed as a destination and remain open as track, which is exactly the shunting
siding he describes. The tile-wide copy a never-split square is emitted as is never barred at all,
because there is no arrival side to bar and refusing it would make the station unreachable rather than
restricted.

**The flag is decided once**, and that is a fix rather than a tidy: it is emitted as both `station` and
the terminus, and read twice the turn-round copy of a barred side came out as a terminus that is not a
destination - a pair `Point.setTerminus` refuses and `parseAuto` answers by invalidating the whole
layout. Restricting a terminus platform, the most natural use this setting has, once made a setup
unloadable. Both of his squares are `canReverse`, so both are that shape.

**Barring every way in is a different thing and is reported, not enforced.** `AutonomySession.shutStations`
names a station whose every arrival side is barred, because such a square is no longer a destination by
any road and that is worth saying out loud rather than discovering from an empty menu.

On **Auto**, a station autonomy is told to leave alone is reported as reachable **and never chosen** —
which is the state somebody opens the panel to explain. The route is still drawn, because the track
is passable and reporting "no path" would be a lie about the railway to make a point about the
settings.

**Why Not Moving? follows Path Type as well** (MT-434, Adam 2026-09-15, asked which tool: *"Why Not
Moving?"*). On **Auto** it answers for autonomy: the stations it could choose but cannot right now, and the
stations it will never choose, each with autonomy's reason. On **Manual** it answers for a train sent by hand, the
way the right-click menu decides what to offer: autonomy's standing bars - not to be chosen, a reversing square on
the way or at the end - are not reasons there, so those stations are listed as reachable when a route is clear, and
every refusal is under *Stations the train cannot be sent to right now*.  **What the menu itself leaves out is still
a reason by hand** (AMR-B2): a station that excludes the train, and a terminus autonomy may choose for a train that
cannot reverse (OB-205) - `Layout.isOfferableToOperator`'s rule, asked through the same method, so the list and
its explanation cannot disagree.  This paragraph used to list "a train excluded" among the bars that do not count,
while the menu left the station out. A switched-off station
is still refused by hand, as the first paragraph of this section says of both tiers: nothing may be sent to one,
and the menu's rule is asked first, so it is listed there saying it is switched off (MFV-C5; the sentence said the
reason came from the route check, which refuses it too - AMV-C6). Switching the radio asks the
last square again. `Layout.explainDestinations(Locomotive, boolean)`;
`regression.testPathTypeRedrawsTheTestInTheEditor.testWhyNotMovingFollowsPathType`.

**And where the only way there is a lap, it says so** (Adam, 2026-09-15, AMR-B1). No route in any tier passes
another copy of the square it starts at or the square it ends at: a square drawn as several Points is one piece of
track, so such a route goes round a loop to where the train already was, or drives through its destination to reach
it. Adam, shown the 22 such routes on his railway and the 14 the right-click menu was offering: *"We need to refuse
both. A copy makes a cycle."* **The railway's search applies it; Return Home's does not** — shown that applying it
there cost Return Home plans it used to find, and reminded that Return Home is manual operation (§1, his ruling of
2026-09-04), he chose *"menu and autonomy only"*. So a lap is available to the planner when it is the only way a train
can get home, and `HomeStaging.auditAgainstRuntime` exempts such a destination as a deliberate tier difference rather
than a planner defect — the runtime agrees with the looser answer, because `isPathClear` exempts an intermediate copy
of the train's own square as occupied by that train. A station left unreachable by
it is reported with a reason of its own - *"The only track route there doubles back through a square this journey
already uses."* - rather than as missing track, which is the same distinction PTR-B1 drew
for the terminus: the question the window puts to the TRACK may walk a lap, so the sentence is chosen from the route
that question found. `core.testARouteIsFoundPastATerminus.testNoRoutePassesAnotherCopyOfTheTrainsOwnSquare`,
`testNoRoutePassesAnotherCopyOfItsDestination` and `testWhyNotMovingSaysTheOnlyWayThereIsALap`.

**And the drawn route says it too, in its colour** (Adam, 2026-09-09). A tested path is drawn yellow
on the way out and orange on the way back; a leg whose **destination** is a station autonomy will
never choose **and a person still may** — a parking berth — is drawn in magenta instead, whichever
direction it runs.

**Two squares are deliberately not magenta.** A **compulsory turn** is chosen like any other station
since OB-195, when *Can Be Chosen in Full Autonomy* is on. And a square **switched out of service** is
not a manual-only point at all: nothing may be sent there by any tier, so colouring the leg as though
a hand-driven run were the remedy would point at a shut door. The routing check says so in its own
sentence instead — see §7. The colour asks `AutonomySession.manualOnlyStations`, which is the runtime
rule less the shut squares. Adam: *"just use a different color going to manual-only points.
yellow is currently forward, and orange is backwards — path, not the chevron arrows."* The colour is
about the destination, so every square of that leg carries it; the chevrons still say the direction.

**A route is found past a terminus, never through one** (OB-229, Adam 2026-09-15: *"Search past termini"*). A route may
end at a terminus and may not pass through one - the route check refuses that on every tier. The route search used to
extend a route through a terminus anyway, and because it spends each square on the first way it reaches it, a shorter
way through a terminus hid a longer clear way past it: from Tunnel, 75 407 DB was offered neither RampDown nor
BottomSecondary, though the loop over the top level reaches both and Return Home's planner found it. The right-click
menu, autonomy's choice and Why Not Moving? all use that search. `core.testARouteIsFoundPastATerminus`.

**No tier sends a train round a loop to another copy of its own square** (Adam, 2026-09-15, reading the routes the
terminus fix opens: *"we should never do a round trip just to change direction"*). The right-click menu and autonomy
never offered one - a copy of the square a train stands on reads as occupied by that train - and Why Not Moving? does not
list the train's own square; Return Home's planner now refuses it too. A train turns round at a reversing square or a
terminus on its way, never by lapping the railway back onto where it started.
`core.testARouteIsFoundPastATerminus.testNoRoundTripBackToTheTrainsOwnSquare`.

---

**Known limit: in non-atomic mode the diagram's right-click menu can offer a train no destinations**
where the Locomotive commands panel offers several (OB-164, MT-087). The two ask different questions -
the menu gates on the one locomotive being on a run, the panel on the whole railway running - and the
state that separates them was never reproduced. Adam, 2026-08-31: *"The user can rely on full autonomy
or the panels to send trains more clearly."* Accepted as it stands, and written here so the next reader
who notices the two surfaces disagree finds the answer rather than reopening it.

---

**A sensor announces itself however it changed** (W21-B1). A module changes state two ways - a
message arriving over the wire, and `setState`, which is everything else: clicking an s88 tile on the
track diagram, the simulation's own announce and clear, the restore at start-up. Only the first told
anything, so the route editor's capture could not see a sensor the operator had just clicked. The
capture cannot tell a clicked sensor from a wired one and should not: a route being recorded is about
what the railway did, not about which code path said so. The announcement is outside the debug check -
a capture that depends on a logging setting is the same defect reachable by a preference.

---

## 7a. A route that meets a train

A route is a list of commands - accessories, functions, locomotive speeds, the power. Any of its
switches may sit on track a train is standing on or has reserved, and throwing one there moves metal
under a train.

**Only the switch under the train is refused. Everything else in the route runs.** Adam, 2026-09-08:
*"don't run the conflicting switch commands, but do run the power off and others."* Each accessory is
asked about immediately before it goes out, so one held turnout costs that turnout and nothing else -
the power still cuts, the functions still fire, and every other switch is thrown. Until MT-247 the
question was asked once, before the loop, about the FIRST held command, and every accessory in the
route was then skipped: one turnout under a train silently dropped all the others.

**Who is asked depends on who started it.**

- **A person started it.** The dialog stands. *"Cancel should cancel everything. OK should fire
  everything."* OK fires the whole route, including the switch under the train, because the operator
  has looked at the railway and said so. Cancel abandons **the rest** of the route, and how much that
  is depends on which of the two questions was answered:

  - the screen asked **before** the route starts refuses it outright - `askAboutRouteConflict` returns
    `REFUSED` and neither caller then calls `execRoute`, so not one command goes out;
  - the question raised by a conflict that **appears while the route runs** stops it where it stands.
    `MarklinRoute.execRoute` returns from inside its own command loop, so every command earlier in the
    list has already gone out and stands - a stop already obeyed, an accessory already thrown. The
    code says so where it happens: *"once a command is refused the ones already sent stand, because
    they went out before the conflict existed."*

  This bullet read "Cancel abandons the whole route - not one command goes out" until 2026-09-08. That
  is true of the first door and false of the second, which is the door the paragraph above is about.
- **A trigger started it** - an s88 or a condition, with nobody watching. No dialog: there is nobody
  to answer it, and a modal dialog raised by the railway is a dialog nobody sees. A line is written to
  the log instead, and the route runs without the held switches.

**An emergency stop is obeyed whatever else is true.** A route carrying a stop is never refused at a
human door and never has its stop skipped (SVN-A4). Suppressing a stop is the one refusal that can
make things worse, and a route that contains one is a route somebody wants to happen now.

**Conditions hold back the route's own firing, not a route somebody runs by hand (X8-B6).** A route's
conditions - "S88 1 is on, switch 3 is turned" - are read in exactly two places: the s88 monitor thread
that fires the route on its trigger, and the editor's Test button, which reports what that thread would
decide. `execRoute` does not read them, so the play button, the right-click Execute item and a route
tile on the diagram all run the route whatever its conditions say.

That is deliberate, and it is the same rule as the tiered destination lists in §1 and the same reason
§7a gives for letting a person answer OK: the operator running a route by hand can see the railway and
the condition cannot. Every one of these doors is a person deciding to do this now.

The help text used to say only *"The route is held until these are true"*, with nothing about which
firing, so a user who added a condition, pressed Test, was told the route would not fire, and then
pressed Play got every switch in it thrown. It now says which firing it means.

## 7c. The signals a station commands: the exit guard and the entry guard

A station can be paired with signals of two kinds, each set up from the station's right-click menu in the autonomy
editor through the same dialog - the list, **Click It on the Diagram**, **Enter Its Address...** (several at once,
comma-separated), **Remove Selected**, **Done** - and each may hold several signals, because a platform reachable from
two ends needs one on each approach.

**The exit guard** - *Exit Guard Signal...* on the menu (Adam, 2026-09-23: *"rename it, but add Signal at the end
(Exit Guard Signal, Entry Guard Signal)"*; it was *Signal Protecting This Station*).  Its signals are RED while the platform is claimed - a train
standing there, or a locked path that has reserved it - and GREEN when it is free.  An aspect DERIVED from the
platform, asked again on every change of occupancy (`Layout.refreshProtectingSignal`), and asked per SIGNAL: one paired
to two platforms stays red while either is claimed.  Only while trains are being run, so arranging the railway by hand
moves no hardware.

**The entry guard** - *Entry Guard Signal...* (Adam, 2026-09-23, FR-096: *"a signal that turns red after arrival at the
final designation.  Same UI to set it as the current linked signal exit guard, and multiple selections are
possible"*).  Its signals are thrown RED when a train ARRIVES at the station as the end of its journey - in every tier,
because every run records its arrival in the same place (`Layout.executePath`).  A train that only passes the station
throws nothing.  **Nothing turns it green**: *"The next route sets it green, so that is out of scope."*  So it is a
command on an event rather than an aspect, and nothing is remembered or undone.

The two lists are separate and thrown at different moments; one signal may be on both.  Both are dropped with the
station when it stops being one, and a pairing whose signal tile has gone is dropped when the setup is reconciled and
reported by the editor's gone-signal notice.

## 7b. Routes that come from a file, and the pause between their commands

**A route read from a file arrives switched off.** Adam, 2026-09-10: *"they should not be armed.  The
user can choose to do this when they are ready."*  So Routes then Import reads each route's automatic
flag and then overrides it: nothing in an imported file starts watching a sensor until the operator
turns it on.  That is the one thing about an import which is deliberately not a faithful restoration of
what the file says.

It is also what makes the import safe.  Building a route ARMS it - a route with a sensor and its flag
set parks a thread on that sensor as soon as it exists - so before this, a file that failed to parse
half way through left the routes it had already built running for the rest of the session: in no
database, on no list, reachable by nothing, and throwing switches for a route the operator could not
see (S14-A1).  And on an import that succeeded, the route being replaced and its replacement both
watched the same sensor until the old one was deleted, so a trigger in that window fired both.

**A delay below 150 ms is the same as a delay of 150 ms.**  A command may carry its own delay, and the
executor pauses for a fixed interval PLUS the larger of that delay and 150 - so the floor is on the
delay, not on the whole wait, and the number in the editor is a floored delay rather than the time
between two commands.  The editor shows the floored number in both directions: a delay that arrived from
an earlier build, from a station or from a JSON file is shown raised too, not only one typed in
(X8-C2, X8V-C7).

Zero is left alone, and it is the one number in that column which is not what the railway will do: it
means no pause was asked for, which is what an untouched command holds, and the executor then waits the
same 150 as it would for a delay of 150.  Raising it in the editor would put a pause on every command in
every route, which is the worse of the two inaccuracies.

`THREEWAY_ROUTE_DELAY_MS` is defined as sitting above the floor, which is what makes the gap a three-way
switch needs between its two commands real rather than inert.

**A speed in a route is between 0 and 100, and a negative speed means an instant stop.**  Any number
outside that range is clamped where the command is BUILT, so a route that arrives from a JSON file or
from a Central Station import cannot hold one (S14-B2).  The editor is different and stays different: it
range-checks a typed speed and refuses to save, which tells the operator rather than quietly changing
what they wrote.  Before that an imported route could
ask for 150, which the head of a multi-unit discarded while every member was sent 100: the two engines
of one consist pulling against each other.

## 8. Things that are true of the whole system

**Pages are ordered by name, not by the number the Central Station orders them with.** Adam,
2026-09-10: *"the `.id` field is what the Central Station uses to order the pages. We still order by
name, which is the simpler behavior."* `getLayoutList` sorts, and that sort is what the window and
every menu show.

An id is still an identity - the autonomy setup is keyed by it, which is why `writeLayoutIndex` keeps
each page's id rather than renumbering by position - but it is not a position. The same goes for the
`page=N` line at the top of a page file: it is preserved exactly as the station wrote it (X8-A1), and
if a reissued id later disagrees with it, neither number decides where the page appears.

**A page that states no `.id` has id 0.** Adam, 2026-09-10: *"no .id means id 0 implicitly."* That is
the ordinary CS2 convention - a key the station omits carries the zero value - and it is what the files
say: of the index files here, four have a first page with no `.id`, none states `.id=0`, and in each of
them the stated ids run consecutively from 1 or 2. On the genuine station export the pages are named `0 stationer` .. `7 autonom
annotated` against stated ids 1..7, so the absent one is 0 and the ids line up with the names.

Reading it as the page's POSITION instead is what made two pages hold one id, which keyed both of them
into one space in the autonomy setup (S14-A2).

**A PAGE LINK IS STORED AS A POSITION, and it is the only thing that is.** Adam, 2026-09-10: *"right
now, let's make artikel be the sorted index of the page - which i believe is already how it worked."*
So an arrow tile holds the destination's place in the name-sorted list, not its id.

Three things follow, and they are the behaviour rather than the implementation:

- **Every operation that changes the set of page names re-aims the arrows.** Adding, renaming,
  duplicating, deleting and combining all move the alphabet, and an arrow follows the page it pointed
  at. Before this it silently came to mean another page and the new number was written to the file -
  measured on the five pages of the sample layout, adding one page repointed four of seven arrows
  (N8-A1). **Combine is covered too**, though not obviously: its copy is written before the re-aim, but
  the page is then refilled square by square from the corrected sources and saved again, so what ends up
  on disk is right. A claim that it was the one exception stood here briefly and was wrong (T10-C2).
- **An arrow whose page is deleted points at nothing.** Adam: *"set the ID to -1. This shouldn't throw
  any errors, and simply resolve to nothing when clicked. Then, the user can set it to the right page
  on their next edit."* Clicking it does nothing and its tooltip says so.
- **An arrow may not point at the page it is drawn on.** Adam: *"make sure links can only go to other
  pages, not themselves."* The page being edited is not offered in the list.

**A page whose file will not read keeps its place in the list.** It comes back blank with a text tile
at 1,1 saying it could not be loaded - Adam, 2026-09-10, and the reason the page cannot simply be
dropped is the rule above it: the list is what every arrow indexes into, so a missing page silently
re-aimed every arrow after it, with nobody having edited anything (NSV-B3). An unhydrated cloud file is
enough to cause that. Such a page is never saved, because writing a blank page over the file somebody
is trying to recover is worse than not showing it.

**And its autonomy settings are kept, always.** Adam, 2026-09-11, asked whether the old "these pages
are absent - keep their settings, or were they deleted?" question should be pointed at stand-in pages
instead: *drop it.* Nothing is pruned automatically. A page whose file will not read today is a file
that is missing right now, which is not the same as a page that is gone, and the answer that cannot
lose anything is to keep. The question could not fire anyway once every page named in the index came
back as something (T10-C3): there were no absences left for it to be about.
`core.testAutonomyDiagramSession.testAPageThatWouldNotReadIsNotJudged` is what holds this - a stand-in
must not let a save judge, and therefore prune, the settings of a page nobody can see.

The message the operator gets says this too. It used to say the page *"could not be read and was
skipped"*, which was the opposite of what they would see (T10-C4); it now says the page is shown blank,
that the page says so itself, and that it will not be written over until the file reads again.

- **A square is several Points.** Anything reasoning about "the station" must say which copy it
  means, or it is asking a question the graph does not answer.
- **`canReverse` is not in the running layout.** Only the setup knows it. **It is a property of the
  TILE**, not of the train: *may a train turn round on this square.* `AutonomyBuilder` expresses it by
  splitting the square into a plain copy and a turning one and never emits the flag itself, so nothing
  in the automation layer can ask the question - which is why the manual doors read it from the setup.
  The train’s own ability to run in either direction is `Locomotive.isReversible`, a different fact
  asked in different places (§3).

- **A route tile carries no track of its own; it conducts what is beside it, like a fixed crossing.**
  Adam, 2026-08-30 (OB-160): *"I am inclined to treat it as a static crossing under the hood"*, and
  2026-09-23: *"it just implicitly connects things as if it were a crossing."* Track facing it from two
  sides is joined through it - a straight or a corner; from all four sides it is a crossing, two roads
  that do not meet. **Two situations are errors on the setup's findings list**, because the tile would
  otherwise conduct something nobody drew: track facing it from THREE sides, where one arm would be
  dropped in silence and there is no honest guess, and two route tiles side by side whose run reaches real
  track, which would splice two lines together across diagram with no rails on it. Route tiles with no
  track beside them are a control panel and conduct nothing. **And it takes no length** - 5a.
- **Facing is encoded as one-way edges.** There is no direction field on a train's route; the sparse
  and doubled edges *are* the direction.
- **A sensor stays occupied while a train is standing on it.**  Adam, 2026-09-19, asked directly: *"the sensor
  will remain on while a train is standing there."*  The detection latches; it does not pulse as the train
  arrives and then clear.  Two things in the model follow from it and are therefore right to bite: `isPathClear`
  refuses an edge whose end reports a set sensor, and the staging planner refuses a point whose sensor sibling
  holds a train - and because one s88 address is shared by more than one Point here (a sensor is not a place
  key), both refuse the SIBLING square of a standing train as well.  That is the refusing direction and it is
  wanted.  **The simulation is the odd one out**: `Layout.simAnnounce` and `simClearBehind` pulse the feedback a simulated run reports, which is why an
  earlier review read the railway as clearing under a standing train (AMR-D1, corrected here by RTX-C4).

- **Signals and switches are the same device to the protocol, and different things on the railway.**
  They are commanded identically and share one address space - a signal and a switch at one address
  are one accessory, and `accessoryType` only decides which icon is drawn. Adam: *"they play very
  different roles in what they control; they are just commanded via the same protocol."* So code that
  looks one up must not key on the type, and a person reading the diagram must not be told the two are
  interchangeable. Both halves are true and neither implies the other.

  **Known limitation, accepted 2026-09-19 (CS3-B2).** One address drawn as a switch on one page and as
  a signal on another is held as one accessory in the database, and the pages are wired in order, so
  the page wired FIRST keeps a tile bound to the object the later page replaced: it stops following the
  railway and shows the position it had when its page was last wired. On Adam's layout that is address
  131, a turnout on 3 - Top Parking and a signal on 5 - Test. Nothing is commanded wrongly - every door
  commands by address - but each wiring pass also re-creates the accessory twice and seeds its remembered state
  from what the page file says, so after any diagram save the keyboard shows 131 as the file has it rather than
  as the railway does, until the next echo.  Adam's ruling is to leave it: *"Let's leave 5 as is and document
  the limitation."* Changing a tile's type ON one page is a different thing and does update the database,
  because that is the same re-creation seen from the other side; red is the turnout's turn and the
  signal's red, green is the opposite.

- **Occupancy and reservation are different facts.** A route holds track it intends to use; a
  standing train covers track it is lying on. Neither is the other.
- **An accessory record is a counter, not a claim.** Adam, 2026-09-07: *"there’s no reason to delete
  an accessory, since we just track their actuations; them being in the database doesn’t otherwise
  harm anything."* A row does not assert that anything is wired to that address. **Reading one must
  still not create it**: `getAccessoryState` registers a switch on a miss, which is right on a path
  about to command the thing and wrong on one that is only looking, so route conditions and the
  keyboard paint use `getAccessoryStateIfPresent`. Creation belongs to the path that commands.
- **A speed the railway reports is rounded, not truncated.** What the model believes about a train is
  what the length and blocking rules run on, so a fine step set from another controller must not be
  quietly lost on the way in.

**A configuration that names something the database no longer has loses that one thing, not the
layout.** Adam, 2026-09-16, on the Load JSON door refusing a whole configuration over one placed
train it could not find: *"drop the train and keep the rest."* A sold or renamed locomotive is the
file outliving the fleet, and taking the railway out of service over it is a much worse answer than
forgetting the one entry. So each of these is dropped and named in the log, and everything else in
the file loads as written - the next save writes it back without the dangling name:

- a placed train (AMR-C3);
- a home assignment;
- an exclusion from a station;
- a station restriction naming a square (`blockedBy`);
- a locomotive in the run list;
- **a lock edge naming track the file does not contain - and this one loudly.** Adam, 2026-09-16:
  *"drop the lock edge with a loud log line too."* A lock says two roads are one piece of metal, so
  the drop has a dangerous reading: if the name is misspelt for track that IS there, two trains may
  now be let onto it. The log line names the track that owns the lock and the name that matched
  nothing, says what that could mean, and says to check the file. The locks beside it in the same
  list are still applied.

The legacy importer behaves the same way and names what it left out, so the two doors agree about the
same file.

A MALFORMED entry is still refused - a lock entry the loader cannot read (`errorLockEdgeGeneric`), or a
placement with no locomotive name at all (`errorLocomotiveConfigMissingName`) - because that is a broken
file rather than one that has outlived its fleet or its track.

---

## Where this document came from

Written 2026-09-07 at Adam's request, after a run of sessions in which the same rules were
re-litigated because nothing stated them in one place. Several were reversed during that work — the
Return Home tier, the meaning of an unmeasured segment, and whether a compulsory turn is a question —
and each reversal is recorded above with the case that forced it.

**Revised 2026-09-07** against Adam's annotations on the first draft. Six of them were requests to
say something more precisely - terminus against station, what "the flag" is, whether `canReverse`
describes a train or a square, what signals and switches share and what they do not, and which of the
two length rules the word "fit" belongs to - and those are now written out rather than assumed. Two
were rulings: intermediates are not asked about, and the arrival side may be set but not cleared. One
is still owed: the written evaluation of whether the two copies could be replaced by following the
edges. Nothing has been built on that one.

The review documents were the working record of how these rules were arrived at. This is the answer
they were working towards, and they were deleted in its favour: 143 on 2026-09-08 - documents that
could no longer say who needed to do what, against one that says what is true - and the last 65 on
2026-09-21, on Adam's *"I don't want more reviews living in the repo."*

---

## Looking up a citation

Comments in this codebase cite review findings constantly - `RGD-B2`, `MON-C6`, `EN57-203` is a
locomotive but `DY3-C7` is a finding - because that is how a comment says *why* rather than *what*.
The documents those ids came from are gone. **The findings are not.**

All of them are in `docs/manual-tests/triage.db`, in the `finding` table - **3,726 rows for 3,369
findings**, because a finding written up in two documents has a row for each, and reading the row count
as a finding count is a mistake three documents have made (VD15-T5) - with the document they
came from, the line in it, the severity, what it was about, the file and line of the evidence, the
commit that fixed it where one is named, and the source files that cite it. `docs/manual-tests/findings.tsv` is a plain-text
mirror of the same rows, rendered from the database, for the two readers that cannot open one: a person
with a citation and a `grep`, and `regression.testEveryCitationResolves`, which resolves every citation
in `src/` and `test/` against it and fails if a new comment names a finding that does not exist.

So, having found `DY3-C7` in a comment:

```
grep "^DY3-C7" docs/manual-tests/findings.tsv
```

or, for everything the database holds about it:

```
python -c "import sys; sys.path.insert(0, 'docs/manual-tests'); import triagedb; \
           print(triagedb.findings(triagedb.connect(), ref='DY3-C7'))"
```

The questions the review folder could not answer, which is why it was replaced:

```sql
-- what does this comment's citation refer to
SELECT * FROM finding WHERE ref = 'DY3-C7';

-- which findings is the code still leaning on
SELECT ref, title, cited_by FROM finding WHERE cited_by IS NOT NULL ORDER BY ref;

-- everything one document found
SELECT ref, severity, title FROM finding WHERE document LIKE '%test-suite%' ORDER BY ref;
```

**`disposition` is a quotation, `status` is the answer to it.** The disposition column is what a
document said on the day it was written, and nothing ever updated one: 63 A-severity findings still
read `open` on 2026-09-08, and of the thirteen checked against the code that day, all thirteen had
been fixed. Every row therefore carries a `status`, set deliberately and with its evidence in
`status_note` - the commit that named it, the comment that cites it, or the check that was made and
when. **Read a row to find out what a citation referred to, never whether it is still true.** For
that, read the code - or write the test.

The 2026-09-21 round of the same work gave a status to the 996 rows of the last 65 documents that had
none:

| how it was decided | |
|---|---|
| a D finding - a clean check by the review convention, nothing to do | 415 |
| cited from `src/` or `test/`, where a fix landed and left a comment naming it | 416 |
| its own document's closing word | 101 |
| named by a commit message | 5 |
| checked by hand against the code that day | 49 |
| **`Open - unverified`** - no evidence either way | 10 |

That table is what the 2026-09-21 sweep decided, not a live count, and the numbers in it do not move.
The last ten were all C-severity and all but one from 2026-09-09. Saying so is the point: a status
invented to tidy a row is worse than a row that admits nobody has looked.

**Since then** (2026-09-22): the general sweep's audit settled three of those ten against the code, and
two findings joined them for a different reason - the VD12 and VD15 documents were written in a
scratchpad and never committed, so their own evidence is gone, and `git` cannot bring them back the way
it brings back the deleted reviews. 18 rows for nine findings read `Open - unverified` today - rows and findings, because a finding written up in two documents has a row for each, which is the distinction this file draws sixty lines above and then collapsed here (VD18-R1).

**And one status was added that day**, `Open - deferred until the MT retests`: work that is real, that
is understood, and that must not land until Adam has re-run the manual tests it would change under.
It is not a parking space - each one names the test it waits on. Nothing reads status except
`LIKE 'Open%'` and `= 'Closed'`, so a new word costs nothing but has to be written down here.

Of what is open now: four are **verified still true** on the day their document was
deleted and each is in the Inbox as an OB, which is where open work belongs; four are Adam's ruling
to make; three wait on the manual tests; one is deferred past 3.0.0 by his own word. The whole
list, at any time:

```sql
SELECT ref, severity, status, status_note FROM finding WHERE status LIKE 'Open%';
```

Forty-five citations resolve to no finding at all; they are rolled in the `dead_citation` table and at
the foot of the mirror.  The CITING FILES are in the table, not in the mirror - the mirror's roll is `ref` and four dashes, and `SELECT * FROM dead_citation` is where to read who cites what (VD12-R11).  They cluster into whole prefixes whose
declaring document never existed - `RC` above A5, all of `LE2` and `LD` - so they were dead ends before
the deletion, not because of it.

[`docs/reviews/README.md`](../reviews/README.md) survives, and is now the convention rather than an
index: how a round is run, and the rule that its documents are catalogued and deleted in the same
round rather than left to go stale.

Regenerate the mirror after any change to the store:

```
python -c "import sys; sys.path.insert(0, 'docs/manual-tests'); import triagedb; \
           triagedb.render_findings(triagedb.connect())"
```
