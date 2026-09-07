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
  turning one. Autonomy turns only where the setup says. **Manual always asks.**

**A terminus is a station; a compulsory turn is not.** The distinction is what each is *for*, and it
decides whether trains are sent there:

| | trains are sent to it | every arrival turns | it is a destination |
|---|---|---|---|
| **Terminus** | yes - it is the end of a line, and somewhere to go | yes | **necessarily** |
| **Compulsory turn** (`mustReverse`) | no | yes | no |
| **May-reverse** | yes, if it is also a destination | only when chosen | independently |

The code enforces the first row in both directions: a terminus **must** be a destination, so
`setDestination(false)` clears `isTerminus` and a copy trains may not arrive at is emitted as a plain
reversing point rather than as a terminus. A compulsory turn is a place the track turns trains round -
a headshunt, a reversing loop - and nothing is ever routed *to* it.

**Which matters for length** (§5): the track-room rule gates on terminus-**or**-reversing and has no
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

Its headline: **on this railway the split does not fire.** Every named square builds to exactly one
copy, may-reverse ones included, because a may-reverse square here is a dead end and the plain copy of
a dead end is deliberately not emitted. The complexity is paid in the code and not in the graph.
Following the edges is a real option - it moves the same state from the graph into the search - but it
would not remove the facing property, only the specific failure of picking the wrong copy, and it
touches everything that reads the graph. Recommendation: not now, not ruled out, and
`testEverySquareOnThisLayoutBuildsToOneCopy` is the tripwire that says when to look again.

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

**A direction command arriving while a run is under way is ignored, not queued.** The baseline is
brought up to date as it arrives, so nothing is left behind to be replayed. It used to be recorded
with `putIfAbsent`, which kept the pre-run direction: the first echo after the run then read a change
that had already been acted on and re-followed a reversal the railway had made itself. Reversals are
counted only when nothing is running, and there is no backlog.

> *"It should be recorded at the destination. Otherwise, it’s just the same as always."* — Adam,
> 2026-09-07

**A turn the railway itself makes at the destination is the exception, and it records itself.** It is
not news arriving late — it is something this application did on purpose and knew about as it did it —
so the arrival writes it down and the window applies it to the graph the next time the railway is
idle. Without that the two rules above hide it between them: the echo lands inside the run’s own pause
while it is still running, and the levelling then wipes the evidence. The physical train would be
reversed, the graph would say otherwise, and the next dispatch would offer paths for the wrong
heading.

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
- It can be **set** afterwards from **Train arrived from** in the right-click menu, on both the editor
  and the track diagram. Not cleared — see below.
- Not knowing is a legitimate answer. It does **not** mean nothing is blocked: where the geometry
  leaves only one way back, the walk still follows it, because that answer is forced rather than
  guessed. `arrivedFrom` picks between candidates; it is not a switch that turns blocking on.
  (Corrected 2026-09-07 after `VAL8-C1` — the earlier wording claimed less than the code does.)

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
| **Track room** | the rail leading in, from the last switch to the berth | terminus **or** reversing, destination or not |
| **Station capacity** | the length the station says it accepts | **every destination** that states one |

Adam, annotating this section: *"the train should be refused any destination it does not fit in, i.e.
where the train length exceeds the accepted length."* It is - by the second rule, and in all three
tiers: `Point.validateTrainLength` is asked from `Layout.isPathClear`, which every autonomy and manual
path goes through, and again from `HomeStaging`. A destination with no stated capacity is not judged
on capacity; that is what leaving the field at zero means.

The rest of this section is about the **first** rule.

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
- Covered squares are **greyed on the diagram** until the train moves.

**Known limits, deliberately.** A tail that really does reach past a fork, or across unmeasured
track, is not blocked. Both under-claim. Blocking on a guess is still a refusal, and it stops trains
that could have run.

---

## 6. Parking and Return Home

- Return Home stages every locomotive that has a home, as one plan.
- One locomotive has one home; assigning a home takes it away from wherever it was.
- It refuses an inactive **start** (see §1) and obeys every length rule in §5.
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

---

## 7. Routing checks in the editor

**Path Type (Auto / Manual)** decides which tier the check answers for. The two differ **only** in
where a train may be sent: an inactive square stops both, a barred arrival stops both, a compulsory
turn turns both.

On **Auto**, a station autonomy is told to leave alone is reported as reachable **and never chosen** —
which is the state somebody opens the panel to explain. The route is still drawn, because the track
is passable and reporting "no path" would be a lie about the railway to make a point about the
settings.

---

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

The review documents in `docs/reviews/` are the working record of how these rules were arrived at.
This is the answer they were working towards.
