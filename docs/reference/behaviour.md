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

**Return Home sits with Manual on the question of where a train may be sent.** This was got wrong
once and corrected on 2026-09-06: `isAutoDestination` appears nowhere in `HomeStaging`, and Adam's
earlier ruling stands — *"Return Home still fills it; only full autonomy leaves it alone."* The Path
Type control in the editor says Manual for this reason.

**Occupancy restrictions are autonomy's alone** (Adam, 2026-09-09). A station can be marked
unavailable while some other named square has a train standing on it. **Autonomy enforces it; manual
and Return Home do not.** Adam's ruling, asked which tier should: *"enforce only in full autonomy.
with the length checks, that is our primary anti collision mechanism, whereas the point exclusion is
for modifying pathing prioritization."* So it is a tool for shaping what autonomy picks, not a guard
against two trains meeting — that is the length rules in §5, which every tier obeys.

**"Full autonomy" is narrower than "autonomy is running."** Executing a timetable sets the same
running flag, and a Return Home run *is* a timetable, so a rule fenced behind that flag applied to
Return Home as well — which is how the planner and the railway came to disagree about this one. The
fence asks `Layout.isFullAutonomyRunning` now: running, with no timetable driving it.

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

**A terminus is a station; a compulsory turn is not.** The distinction is what each is *for*, and it
decides whether trains are sent there:

| | autonomy sends trains to it | every arrival turns | `isDestination` | `isAutoDestination` |
|---|---|---|---|---|
| **Terminus** | yes - it is the end of a line, and somewhere to go | yes | **necessarily** | yes |
| **Compulsory turn** (`mustReverse`) | **no** | yes | **yes** | **no** |
| **May-reverse** | yes, if it is also a destination | only when chosen | independently | independently |

**The compulsory-turn row said "not a destination" until 2026-09-08, and that was wrong** - it named
the wrong one of two flags that sound alike (MON-C14). Measured on Adam's railway: every
compulsory-turn square he has builds to `terminus=true`, which makes it a destination, and that is
right. They are his parking berths. He sends trains to them by hand and homes locomotives there.

What a compulsory turn must not be is somewhere **autonomy** chooses, and that is `isAutoDestination`.
`isDestination` means "a place trains stop". The same two flags were confused in code one finding
earlier the same day (MON-C5), which is a fair warning about how easily they read as synonyms.

The code enforces the terminus row in both directions: a terminus **must** be a destination, so
`setDestination(false)` clears `isTerminus` and a copy trains may not arrive at is emitted as a plain
reversing point rather than as a terminus. Nothing is ever routed *automatically* to a compulsory
turn; `testACompulsoryTurnStationIsNotEmittedAsADestination` holds that against the real railway.

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
  the opposite of the facing (a train that has not been turned drove in forwards); **a may-reverse
  square asks**, because turning round is what those squares are for and the train is as likely to
  have backed in as driven in.
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
Three cases, all narrow:

- a setup saved before the field existed;
- a square whose **occupant changed** - `Point.setLocomotive` drops the side, because the new train
  did not arrive the way the old one did;
- a square with no named copies at all, where there is no side to record.

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

### 5a. Room at the berth

**Two rules, not one, and they are asked in different places.**

| | what it measures | which squares it judges |
|---|---|---|
| **Track room** | the rail leading in, from the last switch to the berth | **every** destination, terminus or not |
| **Station capacity** | the length the station says it accepts | **every destination** that states one |

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

- The room is measured **from the last switch** to the berth. A train that fits there fits behind any
  earlier switch too; one that does not comes to rest standing on the switch.

  **"Fit" here means the surrounding track**, not the length of the berth - that is the station
  capacity rule above, and it is asked separately. A train can fit the platform and still be refused
  because it would be left standing on the switch behind it.

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
- **Two marks, and they say different things** (Adam, 2026-09-09: *"'train is here' should also mean
  'track is blocked' - that is the whole point. it's the same as greying out edges, just in a
  different way."*). A square with **orange** on it is where a train IS. A square that is **grey and
  not orange** is track that train's presence has made unusable. A square with neither is free.
- A train is drawn as an **orange line along the track it is standing on**, **on the track diagram
  viewer only**, until it moves. An editor is where the railway is arranged, and what happens to be
  standing on it while you arrange it is a fact about right now rather than about the drawing - the
  same reasoning that makes station names the default caption there.
- Blocked track is drawn as a **grey wash over the whole square**, **only while autonomy is running**
  - which is Adam's own bound: *"can we just grey out the tiles just like blocked edges while
  autonomy is running?"* Blocked track is a fact about routing, and nothing is routing when nothing
  is running, so a stopped railway shows the line alone. The wash goes **under** the line, so a
  square that is both still reads as both.
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
  the highlight was showing. Neither mark is part of the tile's icon - both are painted over it.
- **Both are refreshed together, and only where they changed.** One pass recomputes both answers and
  repaints exactly the squares whose line or wash differs from what it was. The whole diagram is
  never rebuilt for either: that is MT-334, and what it looks like is the page flickering.
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
   route: `Layout.measuredRoomAtTheBerth` returns at the first edge it meets, walking back, whose
   `crossesASwitch()` is true. The defect is the missing stop at the turn, not a missing stop
   altogether - which matters, because the two would be fixed in different places.

The first refuses trains that would fit, which is safe and annoying. The second admits trains that do
not, which is neither - it is the one of the pair worth ruling on first.

**Both were written while the sum ran only at termini and reversing berths, and that fence is gone**
(MT-262, 2026-09-08; D2-C2). The rule now runs at every destination and in every tier, so whatever
either bullet gets wrong, it gets wrong everywhere rather than at a handful of reversal squares.

**Measured on the operator's own railway**, because "everywhere" is not a number. The census is
`core.testTheRoomRuleCensusOnTheRealLayout`, which runs in the battery: for every ordered pair of
station Points it searches a path, then asks each of the train lengths in his database in turn whether
the widened rule refuses it and the fence it replaced - `isTerminus() || isReversing()` - would not
have.

| | |
|---|---|
| ordered station pairs | 1980 (45 destination Points, each against the other 44) |
| of those, routable | 1848 |
| train lengths in the database | 1, 2, 3, 4, 5, 6 |
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

## 6. Parking and Return Home

- Return Home stages every locomotive that has a home, as one plan.
- One locomotive has one home; assigning a home takes it away from wherever it was.
- It refuses an inactive **start** (see §1) and obeys every length rule in §5.
- It does **not** read the occupancy restrictions of §1 — neither when planning, nor when the run
  executes. A plan may therefore park a train at a station some other occupied square holds back, and
  the railway carries it out.
- It never asks the operator anything: the operator's decision was made when the homes were set.

---

## 6a. Editing the setup while the railway is using it

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
never choose — a parking berth, or a square that turns every train it takes — is drawn in magenta
instead, whichever direction it runs. Adam: *"just use a different color going to manual-only points.
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
