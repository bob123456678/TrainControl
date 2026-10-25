# Reversals

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 3. Reversals

### Where a train turns round

A square is several `Point`s — one per side a train can arrive by — and **which copy a train is on IS
its direction**. Three kinds of square matter:

- **A terminus.** The train has run out of track. It turns, and nobody is asked. A train that
  *cannot* reverse may still **back into** a terminus, when the route there turns it round
  (MT-245).
- **A compulsory turn** (`mustReverse`). Every copy turns trains. Not a question in any tier: a
  turning copy's only outgoing edges leave by the side the train arrived from, so *not* turning is
  not an available outcome — it drives the train forward off its reserved path.
- **A may-reverse square** (`canReverse` on the tile). The build splits it into a plain copy and a
  turning one. Autonomy turns only where the setup says. **A manual send asks** - but not
  unconditionally. *The question, and when it is asked* below is the authority: a
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

A compulsory turn is a destination (MON-C14): every compulsory-turn square on Adam's railway builds to
`terminus=true`, which makes it a destination. They are his parking berths. He sends trains to them by hand and homes
locomotives there. **Autonomy chooses a compulsory turn unless the square is also marked parking.**
`AutonomyBuilder` writes `autoDestination:false` for exactly one marking - the parking one (`manualOnly`) - and for
nothing else; a compulsory turn that stops is emitted as a terminus, and `Layout.isSendableDestination`
(`isDestination && isActive && isAutoDestination && !isReversing`) admits a terminus. So autonomy leaves Adam's
parking berths alone because **he marked them parking**, not because they turn trains. His ruling:

> Marking a square as a compulsory turn says what happens when a train ARRIVES, not who may send one
> there.

**Keeping the two separate is deliberate.** They answer different questions and a layout may want
either without the other: a turning square autonomy is welcome to use is one marking, and a berth
autonomy must leave alone is the other. Where both are wanted - which is every square on Adam's
railway that turns trains, except BottomMainC, a compulsory turn that autonomy may still choose - both markings are made.

`isDestination` means "a place trains stop"; `isAutoDestination` means "a place autonomy may pick" (MON-C5): they read
as synonyms and are not.

The code enforces the terminus row in both directions: a terminus **must** be a destination, so
`setDestination(false)` clears `isTerminus` and a copy trains may not arrive at is emitted as a plain
reversing point rather than as a terminus.

**The editor asks the same question as the runtime** (OB-195).
`AutonomySession.stationsAutonomyWillNotChoose` - which decides the magenta leg colour and the Auto
tier's "reachable and never chosen" notice, both in section 7 - asks `isStation && !isAutoDestination` and
`isActive` (E8-B1): the clauses of the runtime rule that a SQUARE can answer. A compulsory turn counts as one autonomy
will never choose only when it is also parking. The ruling is below, under *Turning round, and being chosen*.
`testACompulsoryTurnIsAnAutoDestinationExactlyWhenItIsNotParking` asks it of the real railway, which
has both kinds: every compulsory-turn copy is an autonomy destination exactly when its square is
not marked parking, in the runtime and in the editor's set alike.

**Which matters for length** (§5): the track-room rule has no terminus requirement and no
destination requirement, so a compulsory turn a train does not physically fit into is still refused.
The station-capacity rule gates on being a destination, and has nothing to say about a square nobody
calls a station. Adam: *"the terminus that isn’t a destination should fail on the track
length check - the station length can safely be ignored."*

**"The flag" is on the tile, not on the train.** `canReverse` is a property of the square in the
autonomy setup: *may a train turn round here.* It is set by marking the tile in the editor. It is not
`Locomotive.isReversible`, which is a property of the **train** - *is this a locomotive that can run
in either direction at all* - and the two are asked in different places for different reasons. A
non-reversible train may still back into a terminus (MT-245); a reversible train may still be refused
a turn at a may-reverse square, because the operator said no.

**On the two copies.** Adam: *"having two copies seems like unnecessary complexity, I wonder if it can
be done more easily by simply following the edges? Don’t implement until evaluating."* The evaluation
is written - `docs/reference/two-copies-evaluation.md`, in git history at 0a5dab2a - and nothing has been changed.
On the wired reduction, **25 of the 58 squares build to more than one Point and 9 of the 33 stations do**;
`BottomMainPost` and `RampDown` are four Points each. `core.testEverySquareBuildsToTheCopiesTheSetupImplies` is the
census and the tripwire.

Following the edges is a real option - it moves the same state from the graph into the search - but it would not
remove the facing property, only the specific failure of picking the wrong copy, and it touches everything that reads
the graph. **Whether the recommendation changes is Adam's call and nothing has been done about it.**

### The question, and when it is asked

> *"May reverse should always prompt in manual mode. It should be a yes/no keep direction, with no
> meaning change, and yes being default."* - Adam
>
> *"Make it be on departure itself, that way there is no dispatch prior to user input."* - Adam

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
> terminal."* - Adam

**Only the destination is asked about** (Adam: *"it is unnecessary to prompt on
intermediates - we care about the reversal if it’s the destination, since that dictates where the
train can go, and where it is facing"*). Intermediates turn as the path requires, exactly as they do
for autonomy.

**A compulsory turn on the way turns the train, whatever the answer** (OB-283; Adam: *"if
it's a compulsory turn, turn it in the forced direction."*). So *keep* and *reverse* are measured
against the way the train **arrives** at the destination, not the way it set off: a journey that
passes a compulsory-turn square is turned there, and "Yes - keep the current direction" then leaves it
facing the way it came in - which is the other way round from how it left (keep ends backward, reverse ends forward).

**No journey is refused on the operator’s answer, and none can be**: with nothing asked about intermediates there is no
declined turn for a journey to depend on.  Declining leaves the train
exactly as it drove in: at the *plain* copy no turn was needed to complete the journey, and at the
*turning* copy the train is re-stood on the copy it did not turn on (PRV-B2).

**Except where no copy faces on: there the train is turned, whatever was answered, and nobody is asked**
(RSA49-A1, RSA50-A1).  A journey ending on a turning copy no copy of whose square faces on from the way
the train came - the end of a line marked "may", which the build makes one turning copy, or a may-turn
square one of whose ways in has no way on - has no copy to re-stand a kept train on: kept, it would stand
facing back out with its decoder driving it forward, and its next journey would drive it into the end of the
line over track nothing held.  So `Layout.turnsOnArrival` turns it first of all, the hand doors do not
ask about it (`ManualReversalPrompt.destinationAskedAbout`), and a train that cannot reverse is not
offered such a copy by hand (MT-367's rule, asked of the copy's ways in).  The setup check's NOTICE -
*"every train turns round anyway"* - says what happens.

**A train the operator turned is stood at once on the copy that faces the way it came in** (RSA50-A3,
RSA50-B1): the copy the window's idle drain chooses (`AutonomySession.copyFacing` - one trains may
arrive at first, and of those one that does not turn trains), chosen by the arrival from the copies'
own facings, after the path is unlocked as the declined turn's re-stand is.  The drain waits for the railway
to be idle, so without this the train would stand on the copy for its old heading and a hand send in between would
set the route ahead while the decoder drove it back.  **Not a train a plan turned** (RSA51-B1) - autonomy, Return
Home, staging, a timetable recorded from autonomy:
a plan turns its train on the turning copy and plans the next leg from that copy, so the train stays
there.  **And a train already on a copy that faces its new way, one trains may arrive at, stays on it**
(RSA52-B1) - the arrival and the drain alike: the turning copy of the way it came in already faces back
out, and moving a plan's turned train from it onto the other side's plain copy at the next
idle moment would leave a timetable recorded from the turning copy unable to start its next entry.  **And a
timetable entry counts its train at its start on any copy of the start square facing the same way**
(RSA53-B1), and runs from the copy it stands on, its first edge swapped for that copy's own to the same
next Point: the setup keeps a standing train as its square and facing only, so the exit and the next
start, Autonomy > Load and every placement door stand it on the plain copy, and the entry, recorded
from the turning copy, must not be refused at a square the train already stood on facing its way.  The train
is not moved onto the start copy (RSA54-A1): a turning copy has rails on one side only, and a train
that came in from the other side, stood there, would claim none of the track its body lay over.

**A timetable entry recorded by hand plays back the answer its arrival was given** (RSA49-B1).  The
entry keeps it (`turnAtTheEnd`, saved with the timetable) and playback asks it at the destination only;
an entry recorded from autonomy, or before this was kept, plays back as autonomy.
Where the railway now turns every train - a square marked "must" since, or the end of a line - the
compulsory turn above is asked first, so a recorded "keep" cannot stand a train facing the end of the
line (RSA50-A2).  And where the square no longer turns trains - "Trains May Change Direction Here" taken off
since - a recorded turn is not made: the train keeps its direction, and an entry that needs it the other
way fails as any journey would (RSA52-C1; Adam: *"The path should organically fail in the
direction no longer allowed."*).

**Every copy of a may-reverse square is asked about, the turning one included** (UIX-C1),
apart from the copies no copy faces on from, above.
The door exempts a terminus only where the policy does not ask about it, and the manual policy asks about every copy of
a square the setup marks as may reverse, which is Adam's own ruling above: *"May reverse should always prompt
in manual mode."*  `core.testNonReversibleTrains.testEveryCopyOfAMayReverseSquareIsAskedAbout` and
`core.testTheArrivalHonoursTheAnswer.testKeepDirectionIsHonouredAtAMayTurnSquare` hold it.

### What the runtime cannot answer

`canReverse` never reaches the running layout — `AutonomyBuilder` expresses it by splitting the
square and says so. So the door supplies the "is this square one the operator has a say over"
question from the **setup**, and nothing in the automation layer can answer it. A policy that
answers it from `isReversing()` cannot tell a may-reverse square from a compulsory turn.

### Direction commands

- **Changing the direction on the graph does not command the track.**
- **A direction command from the track DOES update the graph**, if it disagrees — while nothing is
  running. One that arrives mid-run is ignored (next paragraph).
- A reversal at a may-reverse square emits exactly the same command a terminus does.

> *"The arrival writes the graph - but if a manual command is sent, ignore it, as this is likely
> corrective by the user."* - Adam

**A direction command arriving while a run is under way is ignored, not queued.** Reversals are
counted only when nothing is running, and there is no backlog.

How that is achieved: `putIfAbsent` gives a train nobody has seen before a
baseline and nothing more, so a locomotive first met mid-journey is not treated as having changed
direction. What stops the backlog is the other half - `reconcileFacingWhenIdle` brings the baseline
level with the live state once the railway is idle, so nothing arriving mid-run is left behind to be
replayed. Neither half works alone: without the baseline the first echo is skipped, and without the
levelling the first echo after the run re-follows a reversal the railway already made.

> *"It should be recorded at the destination. Otherwise, it’s just the same as always."* - Adam

**A turn the railway itself makes at the destination is the exception, and it records itself.** It is
not news arriving late — it is something this application did on purpose and knew about as it did it —
so the arrival writes it down and the window applies it to the graph the next time the railway is
idle. Without that the two rules above hide it between them: the echo lands inside the run’s own pause
while it is still running, and the levelling then wipes the evidence. The physical train would be
reversed, the graph would say otherwise, and the next dispatch would offer paths for the wrong
heading.

**What is recorded is the SQUARE the train turned at, and what is written is the way it came in** (OB-190). The
record is a set of names, and the window writes the train's own arrival side as an absolute answer rather than
**flipping** the facing the setup had stored for that square: §6a is the rule that the stored
facing is stale at exactly that moment, because a run moves trains and nothing writes where they ended up
back to the setup. A flip would either have no pivot, and the turn would be written nowhere, or take the
previous occupant’s answer, and the turn would be written **backwards** — with the train then stood on the
copy for a facing it does not have.

**A train that has just been turned round faces the way it came in** (§4): the arrival wrote that side onto that very
`Point` before turning it, and the window writes it as it stands rather than deriving it from a
record. An absolute answer applied twice says the same thing twice, so a shuttle that turns at both ends is simply
written from the end it is standing at.

Consequences:

- **Nothing is forgotten until it is written.** A turn the window could not apply — no setup open, a
  square the build has no copies for, a train the running layout no longer carries — stays owed and is
  tried again on the next refresh.
- **The record is dropped when that train next arrives somewhere without turning.** It means *this
  train is standing where it turned and the graph has not been told*; once it has run on, the arrival
  has already placed it on the copy its journey ended at.
- **The running layout is moved too, and the tail goes with it** — see §4.
- **And what is owed survives a setup rebuild** (D3-C5). The record lives on the `Layout`, and a
  rebuild replaces that object wholesale — so any right-click gesture on the diagram
  between a turn and its successful write (a home, a direction, a caption) would otherwise discard it silently,
  defeating the first bullet in exactly the case it is for. `rebuildRunningLayoutFromSetup` carries the owed turns
  across the same way it carries placements and `arrivedFrom`, and puts them
  onto the new layout only where it has no newer record of its own.

**"The next time the railway is idle" is a moment something has to bring about.** The graph is written by
`reconcileFacingWhenIdle`, which is reached only from a diagram refresh and deliberately refuses while anything is
moving - so a run's turns wait for a refresh made after it has stopped. The rule has one mechanism rather than a copy per
door: **a run announces that it has finished, and the window's single refresh callback tells the graph when it does.**
A hand dispatch announces when the last locomotive thread goes; a timetable (Return Home's run included, which backs
trains into their home berths and turns them) announces again once its completion wait has seen the railway stop,
because until then `running` is still set and every announcement it makes is one the levelling refuses.

---

### Turning round, and being chosen: two markings, two questions

Adam, settling OB-195:

> *"narrow the notice to match the runtime - autonomy should only allow a turn at a point if the
> 'allow in autonomy' option is checked, otherwise the train may only pass through in its current
> direction."*

| the marking | the question it answers |
|---|---|
| **Changing Direction - Never / May / Must** | what happens when a train ARRIVES here |
| **Can Be Chosen in Full Autonomy** | whether autonomy may choose it as a STOP - off, it is used only by a train being parked there, by Return Home or by hand |

They are independent, and only the second decides whether autonomy picks a square:

**Why the second reads as "may it stop here" (MT-348).** Full autonomy never
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
