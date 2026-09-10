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
rule that a SQUARE can answer. On his railway the two could not disagree, because he has no compulsory
turn that is not also parking, which is exactly why it went unnoticed. The ruling is below, under
*Turning round, and being chosen*.

`testACompulsoryTurnStationIsNotEmittedAsADestination` measures that last fact against the real
railway: every compulsory-turn copy on it has `isAutoDestination` false. It is a statement about how
Adam has marked his berths, not about what the flag implies.

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
in pieces** (corrected 2026-09-08). On the wired reduction, **30 of the 58 squares build to more than
one Point and 13 of the 33 stations do**; `BottomMainB`, `BottomMainC`, `BottomMainPost` and `RampDown`
are four Points each. `core.testEverySquareBuildsToTheCopiesTheSetupImplies` is the census and the
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
stood here until 2026-09-07 and described a case that cannot arise. A journey ending at the *turning*
copy of a may-reverse station ends at a terminus, and a terminus is never asked about; a journey
ending at the *plain* copy needs no turn to complete, so there is nothing to decline. Declining leaves
the train exactly as it drove in.

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
| **Can Be Chosen in Full Autonomy** | who may SEND a train here |

They are independent, and only the second decides whether autonomy picks a square:

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
- and one that is **not** narrow and is a defect rather than a design: the **right-click "Place
  locomotive"** item and the **graph window's assign** both place a train without working the side
  out at all (`REV9-B2`, open). Only the diagram drag/paste door implements the rules above. A train
  put down by either of the other two lands with no tail recorded, so nothing behind it is blocked,
  while the identical placement by drag asks the question and blocks it.

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

**A paste onto a square no train could leave is refused**, naming the square, from both doors. The
right-click menu had always greyed the item; the diagram drop said nothing and did it anyway
(Adam, MT-136). The question is asked of the square rather than of one arbitrary copy, since the paste
itself walks to a copy that can depart.

---

## 5. Length: will the train fit?

Two separate rules, both about length, both easy to mistake for each other.

### 5a. Room, at every square on the route

**Two rules, not one, and they are asked in different places.**

| | what it measures | which squares it judges |
|---|---|---|
| **Track room** | the rail behind each square, from the last switch to it | **every square the route runs through**, not only the destination |
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

- **The question is asked at EVERY square on the route, not only at the destination.** Adam,
  2026-09-09, having been asked which of the two it should be and told what the second costs:

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
  that does the refusing measures ONE unit**.

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

- **A square the train passes THROUGH is judged only where a switch is behind it.** The walk has two
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
- **It stops at unmeasured track.** Only positive lengths are determinate.
- A train never blocks itself — pulling forward off its own tail is how it leaves.
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
- **WHAT IS DRAWN IS WHAT IS REFUSED, once both marks are counted.** The grey is the covered EDGES -
  the whole hop between two sensors, which is what `Layout.edgesCoveredByStandingTrains` makes
  unavailable - so blocked track that no train is drawn on is visible as blocked track.

  This bullet said the opposite between 2026-09-08 and 2026-09-09, in bold, and the reversal is the
  point of the change. Replacing the wash with the line answered the double-curve complaint and made
  the picture a strict subset of the refusal: a square the railway would not let a train onto looked
  exactly like free track. Adam: *"that is the whole point."* Two marks answer both questions at
  once, where narrowing one mark to answer the second question could only ever lose the first.

  **What is still not drawn**, and is explained in the "why not moving" view rather than on the
  diagram:

  - the **endpoint squares** of a covered edge, which are deliberately not greyed - *"edges, because
    the points are technically unoccupied"*, and a train standing at a sensor is already shown
    standing there;
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
track, is not blocked. Both under-claim. Blocking on a guess is still a refusal, and it stops trains
that could have run.

**And two the room sum is known to get wrong**, both found the day it was written, both left because
fixing either changes what the railway does and that is Adam's to decide. They lived only in a comment
at `Layout.isPathClear` until 2026-09-08, against this document's own promise at the top that a known
limit is stated here (MON-C13):

1. **A positive length does not mean a measured one.** On a diagram-built graph an edge's length is the
   sum of `max(0, tileLength)` over the tiles it spans, so one measured tile out of five gives a
   positive number and the sum reads it as a measured segment. The total then under-counts and refuses
   trains that fit - the same failure the total-of-what-is-measured ruling removed, one layer further
   down.
2. **It may be summing the wrong segments.** The walk runs backwards from the berth and stops at the
   last SWITCH; it does not stop at a REVERSAL. Where a train backs in after turning part way along,
   the track it comes to rest on is only the part after the reversal - so on a route with no switch
   between the turn and the berth, a 10 + 1 + 2 path admits an eight-unit train into three units of
   room. Adam's words were "sum the track segments leading up to it"; whether *it* means the reversal
   or the berth is the question that has to go back to him.

   This bullet said "It adds the whole path" until 2026-09-08, which is only true of a switch-free
   route: `Layout.measuredRoomAtTheEndOf` returns at the first edge it meets, walking back, whose
   `crossesASwitch()` is true. The defect is the missing stop at the turn, not a missing stop
   altogether - which matters, because the two would be fixed in different places.

The first refuses trains that would fit, which is safe and annoying. The second admits trains that do
not, which is neither - it is the one of the pair worth ruling on first.

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
| ordered station pairs | 1980 (45 destination Points, each against the other 44) |
| of those, routable | 1848 |
| train lengths asked, the census's own | 1, 2, 3, 4, 5, 6 |
| journeys the widening NEWLY refuses | **880** |
| every one of them arriving at | BottomMainA (eastbound), BottomMainB (eastbound), BottomMainC (westbound), BottomMainPost (northbound) - each measured at **ONE** unit of room |

The room rule declines to judge almost everything else: the railway carries only a few measurements.

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

### The autonomy editor's keyboard doors

Three shortcuts act on **the square the pointer is over**, and they ask one question to find it -
`LayoutEditor.hoveredSquare()`, which answers null when the remembered label is not on the grid it is
asked about, and forgets it while it is there. They read a field nothing cleared until 2026-09-10, so
after stepping to another page all three named a square on the page before (OB-198).

| | |
|---|---|
| **Control+S** | Names the square — the right-click menu's **Rename**. Asks `canBeNamed`, so it does nothing on plain track (MT-313). |
| **Control+E** | Opens **Segment Length** on it (FR-066). Adam picked the key: *"let's do E"*, Control+D being taken twice over. |
| **Control+H** | Sets the home locomotive. |

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

---

## 6. Parking and Return Home

- Return Home stages every locomotive that has a home, as one plan.
- One locomotive has one home; assigning a home takes it away from wherever it was.
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

On **Auto**, a station autonomy is told to leave alone is reported as reachable **and never chosen** —
which is the state somebody opens the panel to explain. The route is still drawn, because the track
is passable and reporting "no path" would be a lie about the railway to make a point about the
settings.

**And the drawn route says it too, in its colour** (Adam, 2026-09-09). A tested path is drawn yellow
on the way out and orange on the way back; a leg whose **destination** is a station autonomy will
never choose — a parking berth, or a station switched out of service — is drawn in magenta instead,
whichever direction it runs. **Not a compulsory turn**: since OB-195 a square that turns every train
it takes is chosen like any other station when *Can Be Chosen in Full Autonomy* is on, and is drawn
like one. Adam: *"just use a different color going to manual-only points.
yellow is currently forward, and orange is backwards — path, not the chevron arrows."* The colour is
about the destination, so every square of that leg carries it; the chevrons still say the direction.

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

## 8. Things that are true of the whole system

- **A square is several Points.** Anything reasoning about "the station" must say which copy it
  means, or it is asking a question the graph does not answer.
- **`canReverse` is not in the running layout.** Only the setup knows it. **It is a property of the
  TILE**, not of the train: *may a train turn round on this square.* `AutonomyBuilder` expresses it by
  splitting the square into a plain copy and a turning one and never emits the flag itself, so nothing
  in the automation layer can ask the question - which is why the manual doors read it from the setup.
  The train’s own ability to run in either direction is `Locomotive.isReversible`, a different fact
  asked in different places (§3).

- **Facing is encoded as one-way edges.** There is no direction field on a train's route; the sparse
  and doubled edges *are* the direction.
- **Signals and switches are the same device to the protocol, and different things on the railway.**
  They are commanded identically and share one address space - a signal and a switch at one address
  are one accessory, and `accessoryType` only decides which icon is drawn. Adam: *"they play very
  different roles in what they control; they are just commanded via the same protocol."* So code that
  looks one up must not key on the type, and a person reading the diagram must not be told the two are
  interchangeable. Both halves are true and neither implies the other.

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

The review documents in `docs/reviews/` were the working record of how these rules were arrived at.
This is the answer they were working towards, and on 2026-09-08 they were deleted in its favour: 143
documents that could no longer say who needed to do what, against one that says what is true.

---

## Looking up a citation

Comments in this codebase cite review findings constantly - `RGD-B2`, `MON-C6`, `EN57-203` is a
locomotive but `DY3-C7` is a finding - because that is how a comment says *why* rather than *what*.
The documents those ids came from are gone. **The findings are not.**

All 2,265 of them are in `docs/manual-tests/triage.db`, in the `finding` table, with the document they
came from, the line in it, the severity, the file and line of the evidence, the commit that fixed it
where one is named, and the source files that cite it. `docs/manual-tests/findings.tsv` is a plain-text
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
been fixed. Every row was therefore given a `status` of `Closed`, and eight that had been explicitly
deferred for Adam say so. **Read a row to find out what a citation referred to, never whether it is
still true.** For that, read the code - or write the test.

Forty-five citations resolve to no finding at all; they are rolled in the `dead_citation` table and at
the foot of the mirror, with the files that cite each one. They cluster into whole prefixes whose
declaring document never existed - `RC` above A5, all of `LE2` and `LD` - so they were dead ends before
the deletion, not because of it.

`docs/reviews/README.md` survives, and records the convention the folder used.

Regenerate the mirror after any change to the store:

```
python -c "import sys; sys.path.insert(0, 'docs/manual-tests'); import triagedb; \
           triagedb.render_findings(triagedb.connect())"
```
