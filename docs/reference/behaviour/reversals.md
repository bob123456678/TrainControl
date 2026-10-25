# Reversals

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 3. Reversals

### Where a train turns round

A square is several `Point`s, one per side a train can arrive by, and **which copy a train is on IS its direction**.
Three kinds of square matter:

- **A terminus.** The train has run out of track. It turns, and nobody is asked. A train that *cannot* reverse may
  still **back into** a terminus, when the route there turns it round (MT-245).
- **A compulsory turn** (`mustReverse`). Every copy turns trains, in every tier: a turning copy's only outgoing edges
  leave by the side the train arrived from, so *not* turning is not an available outcome: it drives the train forward
  off its reserved path.
- **A may-reverse square** (`canReverse` on the tile). The build splits it into a plain copy and a turning one.
  Autonomy turns only where the setup says. **A manual send asks**, but not unconditionally: *The question, and when it
  is asked* below is the authority, and a journey ending at a terminus is not asked about
  (`ManualReversalPrompt.forJourney` returns before the dialog).

**A terminus is where a line ends; a compulsory turn is a rule about what happens on arrival.** Only one of the two
says anything about who may send a train there:

| | autonomy sends trains to it | every arrival turns | `isDestination` | `isAutoDestination` |
|---|---|---|---|---|
| **Terminus** | yes, it is the end of a line and somewhere to go | yes | **necessarily** | yes |
| **Compulsory turn** (`mustReverse`) | **yes, unless the square is ALSO marked one autonomy may not choose** | yes | **yes** | **independently** |
| **May-reverse** | yes, if it is also a destination | only when chosen | independently | independently |

A compulsory turn is a destination: every compulsory-turn square on Adam's railway builds to `terminus=true`. They are
his parking berths; he sends trains to them by hand and homes locomotives there. **Autonomy chooses a compulsory turn
unless the square is also marked parking.** `AutonomyBuilder` writes `autoDestination:false` for the parking marking
(`manualOnly`) and nothing else, and `Layout.isSendableDestination`
(`isDestination && isActive && isAutoDestination && !isReversing`) admits a terminus. His ruling:

> Marking a square as a compulsory turn says what happens when a train ARRIVES, not who may send one
> there.

**Keeping the two separate is deliberate**: a turning square autonomy may use is one marking, a berth autonomy must
leave alone is the other, and a square wanting both gets both. On Adam's railway every square that turns trains has
both, except BottomMainC, a compulsory turn autonomy may still choose.

`isDestination` means "a place trains stop"; `isAutoDestination` means "a place autonomy may pick": they read as
synonyms and are not.

A terminus **must** be a destination: `setDestination(false)` clears `isTerminus`, and a copy trains may not arrive at
is emitted as a plain reversing point rather than as a terminus.

**The editor asks the same question as the runtime** (OB-195).
`AutonomySession.stationsAutonomyWillNotChoose`, which decides the magenta leg color and the Auto tier's "reachable and
never chosen" notice (section 7), asks `isStation && !isAutoDestination` and `isActive`: the clauses of the runtime
rule a SQUARE can answer. A compulsory turn counts as never chosen only when it is also parking.
`testACompulsoryTurnIsAnAutoDestinationExactlyWhenItIsNotParking` asks it of the real railway, in the runtime and in the
editor's set alike.

**Which matters for length** (§5): the track-room rule has no terminus or destination requirement, so a compulsory turn
a train does not physically fit into is still refused. The station-capacity rule gates on being a destination. Adam:
*"the terminus that isn’t a destination should fail on the track length check - the station length can safely be
ignored."*

**"The flag" is on the tile, not on the train.** `canReverse` says *may a train turn round here*, set by marking the
tile in the editor. `Locomotive.isReversible` says *can this locomotive run in either direction at all*; the two are
asked in different places. A non-reversible train may still back into a terminus (MT-245); a reversible train may
still be refused a turn at a may-reverse square, because the operator said no.

**On the two copies.** Adam: *"having two copies seems like unnecessary complexity, I wonder if it can
be done more easily by simply following the edges? Don’t implement until evaluating."* The evaluation is
`docs/reference/two-copies-evaluation.md`, in git history at 0a5dab2a, and nothing has been changed. On the wired
reduction, **25 of the 58 squares build to more than one Point and 9 of the 33 stations do**.
`core.testEverySquareBuildsToTheCopiesTheSetupImplies` is the census and the tripwire. Following the edges would move
the same state from the graph into the search: it removes the failure of picking the wrong copy but not the facing
property, and touches everything that reads the graph. **Whether that changes the recommendation is Adam's call.**

### The question, and when it is asked

> *"May reverse should always prompt in manual mode. It should be a yes/no keep direction, with no
> meaning change, and yes being default."* - Adam
>
> *"Make it be on departure itself, that way there is no dispatch prior to user input."* - Adam

- Asked **once per journey**, **before the train moves**, from both manual doors (the track diagram and the
  Locomotive commands tab).
- The dialog names the **destination** when that is one of the squares in question, because *"send a train to a
  may-reverse point"* is the case the feature exists for.
- **Yes = keep the current direction**, the default. Escape, the window's X and a dialog that cannot be shown all mean
  keep. Only an explicit **No** turns the train.
- **A journey ending at a terminus is not asked about**: the turn on the way in is how a train backs into a terminus.
- Autonomy and Return Home are never asked.

> *"Manual only reverses if the user explicitly said it via the popup, unless you're going to a
> terminal."* - Adam

**Only the destination is asked about** (Adam: *"it is unnecessary to prompt on
intermediates - we care about the reversal if it’s the destination, since that dictates where the
train can go, and where it is facing"*). Intermediates turn as the path requires, as they do for autonomy.

**A compulsory turn on the way turns the train, whatever the answer** (OB-283; Adam: *"if
it's a compulsory turn, turn it in the forced direction."*). So *keep* and *reverse* are measured against the way the
train **arrives** at the destination: after a compulsory turn on the way, "Yes - keep the current direction" leaves it
facing the way it came in, the other way round from how it left (keep ends backward, reverse ends forward).

**No journey is refused on the operator’s answer**: nothing is asked about intermediates, so no journey depends on a
declined turn. Declining leaves the train exactly as it drove in: at the *plain* copy no turn was needed, and at the
*turning* copy the train is re-stood on the copy it did not turn on.

**Except where no copy faces on: there the train is turned, whatever was answered, and nobody is asked.** A journey
ending on a turning copy no copy of whose square faces on from the way the train came (the end of a line marked "may",
which the build makes one turning copy, or a may-turn square one of whose ways in has no way on) has no copy to
re-stand a kept train on; kept, it would face back out with its decoder driving it forward into the end of the line.
So `Layout.turnsOnArrival` turns it first of all, the hand doors do not ask (`ManualReversalPrompt.destinationAskedAbout`),
and a train that cannot reverse is not offered such a copy by hand (MT-367's rule, asked of the copy's ways in). The
setup check's NOTICE, *"every train turns round anyway"*, says what happens.

**A train the operator turned is stood at once on the copy that faces the way it came in**: the copy the window's idle
drain chooses (`AutonomySession.copyFacing`: one trains may arrive at first, and of those one that does not turn
trains), after the path is unlocked as the declined turn's re-stand is. Otherwise it would stand on the copy for its
old heading until the railway was idle, and a hand send in between would set the route ahead while the decoder drove it
back.

- **Not a train a plan turned** (autonomy, Return Home, staging, a timetable recorded from autonomy): a plan turns its
  train on the turning copy and plans the next leg from that copy, so the train stays there.
- **A train already on a copy that faces its new way, one trains may arrive at, stays on it**, at the arrival and in
  the drain alike; moving a plan's turned train onto the other side's plain copy would leave a timetable recorded from
  the turning copy unable to start its next entry.
- **A timetable entry counts its train at its start on any copy of the start square facing the same way**, and runs
  from the copy it stands on, its first edge swapped for that copy's own to the same next Point. The setup keeps a
  standing train as its square and facing only, so most doors stand it on the plain copy, and an entry recorded from the
  turning copy must not be refused there. The train is not moved onto the start copy: a turning copy has rails on one
  side only, and a train that came in from the other side would claim none of the track its body lay over.

**A timetable entry recorded by hand plays back the answer its arrival was given.** The entry keeps it (`turnAtTheEnd`,
saved with the timetable) and playback asks it at the destination only; an entry recorded from autonomy, or before this
was kept, plays back as autonomy. Where the railway now turns every train (a square marked "must" since, or the end of
a line) the compulsory turn is asked first, so a recorded "keep" cannot stand a train facing the end of the line. Where
the square no longer turns trains ("Trains May Change Direction Here" taken off since), a recorded turn is not made: the train keeps its direction, and an entry that needs
it the other way fails as any journey would (Adam: *"The path should organically fail in the
direction no longer allowed."*).

**Every copy of a may-reverse square is asked about, the turning one included**, apart from the copies no copy faces on
from (above). The manual policy asks about every copy of a square the setup marks as may reverse, which is Adam's
ruling: *"May reverse should always prompt in manual mode."*
`core.testNonReversibleTrains.testEveryCopyOfAMayReverseSquareIsAskedAbout`,
`core.testTheArrivalHonoursTheAnswer.testKeepDirectionIsHonouredAtAMayTurnSquare`.

### What the runtime cannot answer

`canReverse` never reaches the running layout: `AutonomyBuilder` expresses it by splitting the square. So the door
supplies "is this a square the operator has a say over" from the **setup**; a policy answering it from `isReversing()`
cannot tell a may-reverse square from a compulsory turn.

### Direction commands

- **Changing the direction on the graph does not command the track.**
- **A direction command from the track DOES update the graph**, if it disagrees, while nothing is running.
- A reversal at a may-reverse square emits exactly the same command a terminus does.

> *"The arrival writes the graph - but if a manual command is sent, ignore it, as this is likely
> corrective by the user."* - Adam

**A direction command arriving while a run is under way is ignored, not queued.** `putIfAbsent` gives a train nobody
has seen a baseline and nothing more, so a locomotive first met mid-journey is not taken as having changed direction;
`reconcileFacingWhenIdle` brings the baseline level with the live state once the railway is idle, so nothing from the
run is replayed. Each half needs the other.

> *"It should be recorded at the destination. Otherwise, it’s just the same as always."* - Adam

**A turn the railway itself makes at the destination is the exception, and it records itself.** The arrival writes it
down and the window applies it to the graph the next time the railway is idle. Without that the two rules above hide
it: the echo lands inside the run's own pause, and the levelling then wipes it, so the graph would offer paths for the
wrong heading.

**What is recorded is the SQUARE the train turned at, and what is written is the way it came in** (OB-190): the train's
own arrival side, as an absolute answer, never a **flip** of the facing the setup had stored for that square. §6a: the
stored facing is stale at exactly that moment, so a flip would have no pivot, or take the previous occupant's answer and
write the turn backwards.

**A train that has just been turned round faces the way it came in** (§4): the arrival wrote that side onto that
`Point` before turning it, and the window writes it as it stands. An absolute answer applied twice says the same thing
twice, so a shuttle that turns at both ends is written from the end it stands at.

- **Nothing is forgotten until it is written.** A turn the window could not apply (no setup open, a square the build
  has no copies for, a train the running layout no longer carries) stays owed and is tried again on the next refresh.
- **The record is dropped when that train next arrives somewhere without turning**: the arrival has then placed it.
- **The running layout is moved too, and the tail goes with it** (§4).
- **What is owed survives a setup rebuild.** `rebuildRunningLayoutFromSetup` carries the owed turns across as it
  carries placements and `arrivedFrom`, and puts them on the new layout only where it has no newer record of its own;
  otherwise any right-click gesture between a turn and its write would discard it.

**"The next time the railway is idle" is a moment something has to bring about.** `reconcileFacingWhenIdle` is reached
only from a diagram refresh and refuses while anything moves, so **a run announces that it has finished, and the
window's single refresh callback tells the graph when it does.** A hand dispatch announces when its last locomotive
thread goes; a timetable (Return Home's run included) announces again once its completion wait has seen the railway
stop, because until then `running` is still set.

### Turning round, and being chosen: two markings, two questions

Adam, settling OB-195:

> *"narrow the notice to match the runtime - autonomy should only allow a turn at a point if the
> 'allow in autonomy' option is checked, otherwise the train may only pass through in its current
> direction."*

| the marking | the question it answers |
|---|---|
| **Changing Direction - Never / May / Must** | what happens when a train ARRIVES here |
| **Can Be Chosen in Full Autonomy** | whether autonomy may choose it as a STOP; off, it is used only by a train being parked there, by Return Home or by hand |

They are independent, and only the second decides whether autonomy picks a square.

**Why the second reads as "may it stop here" (MT-348).** Full autonomy never turns a train part-way through a run
(`pickPath` refuses a route that `reversesAlongTheWay`; `testFullAutonomyDoesNotDriveThroughAReversingPoint`), so the
switch decides whether a train may END a run there in autonomy, or only while being parked, by Return Home or by hand.
The editor's hint says it that way.

- A **compulsory turn** with the switch on is a station autonomy may choose, and the train turns when it gets there: a
  must-turn station is built as a terminus, and a terminus has `isReversing()` false. `getPossiblePaths` adds
  `!end.isTerminus() || loc.isReversible()`, so autonomy never sends a locomotive that cannot change direction to one.
- With the switch **off**, autonomy leaves the square alone; a hand send and Return Home still use it.

  **Nothing passes through such a square in either case**: a compulsory turn is built as a terminus, and
  `Layout.isPathClear` refuses any route whose terminus is not its endpoint. Adam's *"the train may only pass through
  in its current direction"* rules that turning round is not part of who may be SENT somewhere: that is the switch, and
  only the switch.
