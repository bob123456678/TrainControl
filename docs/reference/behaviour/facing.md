# Facing, arrival side and placement

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 4. Which way a train is pointing, and where its tail is

Two different properties.

- **`facing`** — the side the train's front points at. It is which copy of the square the train
  stands on, and it decides where it can go next.
- **`arrivedFrom`** — the side it came IN by. Its tail lies that way.

**Facing cannot answer where the tail is.** A train faces the way it will leave; once it has been
turned round the two point the same way while the carriages have not moved.

**Nothing about a placement is recorded until the railway has accepted it** (W21-B3). Every door that
puts a train down asks `Layout.moveLocomotive`, and that refuses in four cases: autonomy is running,
the locomotive is unknown, the point is unknown, and the target is not a destination. (The rebuild's
put-back, the paste and the Place Locomotive dialog may also stand a train on a copy of a station trains may
not arrive at, where no station copy of the square faces its way: the copy is its direction - TDY3-A1,
TDY4-B1, OB-284, section 7.) The refusal is the method's, and
the caller's job is to honour it: no placement, no facing, no arrival side, nothing saved, because the setup is the
half that survives a restart and the next build would emit the train on a square it was never put on. The log line
`moveLocomotive` already writes is what the operator sees; a second message would say the same thing
twice. `regression.testTheRefusalsAreAskedAtTheDoors.testEveryPlacementDoorUsesTheRailwaysAnswer` names
every door and says which one is allowed to discard the answer, and why.

**A station no track arrives at records which way a train placed on it faces** (RSA2-C7), as other stations do, so
only the routes that way are offered.  Such a square with more than one way out is one copy per way out; with one, or
none, it is still one Point.
`core.testATrainIsPlacedOnceAndKeepsItsFacing.testASquareNothingArrivesAtFacesItsPlacedTrain`.

### How `arrivedFrom` is set

- **Autonomy writes it on arrival**, which is the only moment anything knows it for certain — and
  before any reversal at the platform, because turning a train does not move its carriages.
- **A hand placement works it out**: a terminus forces it (one way in); an ordinary station assumes
  the train drove in forwards, so its tail is on the side it is **not** pointing at; **a may-reverse
  square asks**, because turning round is what those squares are for and the train is as likely to
  have backed in as driven in.
- **"Behind it" is chosen from the square's own sides, not from the compass** (`REV9-B3`). The compass opposite of
  the facing (facing N gives S) is the same answer on a straight and a different one on a curve, where the build
  enters a square by, say, N and E and no rail lies to the south at all: the side recorded would name no edge, the tail
  walk would match nothing on its first hop, and **nothing would be blocked** — a protection reporting itself as
  present while doing nothing. So the sides are given first and the facing picks between them:
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
  headings because they are opposite ends of one train, and because a second submenu is what would give the
  editor a setting the viewer did not have.
- **The sides offered are the ones the BUILD splits on**, not the compass direction of the neighbouring
  point. A `Point` is the far end of a reduced edge that can run several tiles and turn corners, so a
  rail leaving north and curving east reaches a neighbour lying east; asking the geometry would offer "E"
  where the metal leaves by "N". On a straight the two agree.
- Not knowing is a legitimate answer. It does **not** mean nothing is blocked: where the geometry
  leaves only one way back, the walk still follows it, because that answer is forced rather than
  guessed. `arrivedFrom` picks between candidates; it is not a switch that turns blocking on (VAL8-C1).

- **It survives a train being re-stood on a sibling copy.** When the window writes a destination turn
  it also moves the locomotive onto the copy for its new facing, and that is two changes of occupant —
  which is what `Point.setLocomotive` drops the side on. Right for a different train arriving, wrong
  here: this is the *same* train on another copy of *one* square, and turning it did not move its
  carriages. So the side is carried across the move, and exactly the train that turned does not lose
  the record of the track it is lying across.

**When would it not be known?**

- a setup saved before the field existed;
- a square whose **occupant changed** - `Point.setLocomotive` drops the side, because the new train
  did not arrive the way the old one did;
- a square with no named copies at all, where there is no side to record;
- a square with **three or more ways in** where the facing cannot separate them and the build does
  not enter by the compass opposite either - see the rule above. Deliberate: nothing is recorded
  rather than a side guessed.
- and a square whose occupant a **placement door could not ask about**, which is
  only the dismissed prompt: `placeFacing` treats that as "do not place the train", so nothing lands
  and nothing is recorded.

**Every hand-placement door works it out** (`REV9-B2`): the right-click "Place locomotive" item and the assign
dialog place a train with the side worked out, so the same placement blocks the track behind it whichever menu it is
made from. Adam: *"the missing arrival side should be set - either from the data, by the user, or
randomly."*

The three doors differ only in **where the question is put**, because the rule behind them is one
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

**Clearing is not offered.** Adam: *"clearing should not be possible, only setting."* A mistaken answer is not
permanent: the menu lists every side the geometry offers, so a wrong answer is corrected
by choosing the right one. A "Not known" option would be a way to switch the tail blocking off, which is a
preference about the check rather than a fact about the railway. The automatic clear above stays - the
railway dropping a fact it no longer holds is not a person declaring ignorance.

### Pasting a train

> *"Calculate the simple BFS path from the current station to the paste target using the current
> direction. Paste with the direction where the train ends up at the destination. If no path, pick
> randomly from the allowed departure destinations."* — Adam

The heading is not carried and not inferred from the two squares: **the railway is walked**. Which
way a train ends up facing is decided by the track between the two platforms and by which way it set
off — one that leaves southbound round a loop is northbound one square away. A square with one copy
has one answer, which at a terminus is the reversal.

**The walk answers with the nearest copy a train could be STANDING on**, and the same paste twice
gives the same railway. `Layout.getNeighbors` shuffles on purpose - *"Randomize order to allow for variation in paths"*,
which is right for autonomy picking a journey - so the walk must not answer with the first copy it touched: on
a square trains may turn round at, the plain copy and the turning copy are always the same
distance away and face **opposite** ways, and the shuffle would choose the direction (on the frozen snapshot,
2-8-4 3505 SP put down on BottomMainB faced east 21 times out of 40 and west the other 19; Adam, MT-377: *"When 2-8-4
is pasted, it should always face east. Does it?"*).

So the walk runs to the end and the distances decide - breadth-first measures those correctly through
any expansion order - and the tie at the nearest distance goes to the copy a train would simply be
standing on. **Putting a train down is not a decision to reverse it**; the turning copy IS that
decision, and the facing menu is where an operator makes it. A compulsory turn emits no plain copy, so
there is nothing to prefer and its turning copy is still the answer, which is Adam's *"for terminuses,
they must reverse on paste"* falling out of the same rule rather than bolted beside it.

A may-turn square's turning copy is emitted with `terminus: true`, so `isTerminus()` cannot tell it from a real
terminus (OB-205 claims 1-3, MT-368).

**And the train is PUT on the copy the walk names, not only recorded as facing that way** (OB-270; Adam:
*"Trains should not inadvertently change direction when pasted, so a loc going west
from bottomsecondary should always face east when pasted on bottommaina."*). A paste writes two things -
the heading in the setup and the copy the train stands on in the running layout - and the copy IS the
direction (section 3). Where a square has two copies a train may stop at, facing opposite ways, the copy
taken is not whichever `StationIndex.speakerAt` met first (the record would say east while the train stood
westbound, and its arrival side and tail would be worked out for the wrong copy). **The rule: the copy
taken is the operator's chosen heading where one was asked, and the walked heading otherwise**; the heading
is chosen and recorded over the copies a train could leave, so no impossible facing is saved. **Barred arrivals**
(OB-284; Adam: *"this should check for barred
departure directions, not arrival ones.  for barred arrival directions, keep the direction.  for barred
departure directions, turn it to a way trains may arrive in"*): a train facing the way trains may not
arrive at the landing keeps its heading - as a reversal on the throttle or the Facing menu keeps it (the
copy is the direction, section 3) - and stands where autonomy will not start it, told why; a heading no
copy can leave by is turned to one trains may arrive in.  The same rule at the editor's Place and the
Place Locomotive dialog (`AutonomySession.departableFacingsFor`).

**A cut train is walked from the square it was cut from** (OB-270; Adam: *"it should be east.
no train should inadvertently change direction when pasted."*). Control+X takes the train off the railway,
so the walk would have nowhere to start and the paste would keep the compass heading it was cut with (MT-368) - west, for
a train cut going west at BottomSecondary, where the route to BottomMainA loops round and arrives facing
east. The cut remembers its square, and the paste walks from there; the cut heading stands only where no
route reaches the landing. `ui.testACutTrainArrivesTheWayItWouldDrive`.

**Where there is no path**, the heading the train already has is kept if the landing can hold it, and
otherwise the first copy it could depart from is taken. Adam: *"as long as the
direction isnt flipped (which it was before)"* - because something does determine an answer, and a
placement that answers differently each time it is repeated is drift (*"in all your simulations, state
should never drift"*). **The diagram's right-click Place follows the same rule** (OB-296; Adam: *"Yes, keep the
train's heading."*) - the heading over every copy the train can
leave by, a copy trains may not arrive at included, and the copy the paste would take (`copyFacing`);
otherwise the first copy the menu offers.  The item is offered wherever a copy has a way out, as the action
chooses (ADU2-C2); among station copies only, it would turn a train facing the barred way round at a square with a
barred side (ADU-B1).  A copy with a way out that reaches no
station autonomy may choose is kept too, and Why not Moving? says why autonomy never starts a train there - turn it round where another copy of the square reaches a station and a train may be started from that copy (RLA-C5); where that copy is barred, open its side and turn it round - but where every train turns round at the square, drive it off by hand, since opening that side there is the terminus error (RLU3-C2, RLU4-C6, GUI4-C3); where autonomy starts no train from that copy for another reason, drive it off by hand (RLU3-C2); and where no copy reaches one, drive it off by hand or let autonomy choose a station it can reach - switched on, Can Be Chosen in Full Autonomy ticked, and the side a train would arrive on open under Trains May Arrive... unless every train turns round there (RLU-C4, RLU2-C10, RLU3-C1, RLU4-C4) (OB-299; by hand it is no reason, since a hand send may go where autonomy never does).  `core.testWhyStuck.testACopyThatReachesNoStationSaysSo`, `core.testWhyStuck.testTurningRoundIsOfferedOnlyOntoACopyATrainMayStartFrom`, `core.testWhyStuck.testTurningRoundIsNotOfferedOntoACopyThatIsNoStation`, `core.testWhyStuck.testOpeningASideIsNotOfferedWhereEveryTrainTurns`, `core.testATrainIsPutOnlyWhereItCanStart.testTheRightClickPlaceKeepsTheTrainsHeading`.

> *"Simply don’t place the train, leave it on the clipboard as if no paste had been done."* — Adam,
> on a dismissed prompt

**A dismissed arrival-side question abandons the paste.** The question is asked *before* the train
moves, which is what makes declining it possible at all. Nothing moves, the clipboard still
holds the train, and the next square accepts the same paste.

Only a **may-reverse** square can be dismissed. Everywhere else the side is worked out rather than
asked, so no answer means "there was nothing to record" rather than "the operator said no" — treating
the two alike would refuse every paste onto plain track.

**And a may-reverse square asks a second question: which way the train should face.**

> *"If pasting at a 'may reverse' square, ask what direction the train should face."* - Adam

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
> too confusing to the user to manage."* - Adam (FR-060)

Two facts, and neither is enough alone. **Autonomy will not choose it** says the operator is keeping
the square for himself, which is true of a berth and of a platform he wants to dispatch by hand.
**One way in and out** says the track is a dead end, which is true of a berth and of a headshunt.
Together they are what FR-060 wanted a fourth designation for, so there is nothing to mark and nothing
to migrate.

Counted in **squares**, not copies: a square split into a northbound and a southbound Point is one
place with one way out, and a neighbour reached by two copies is one neighbour.

**This is not the berth rule's gate.** "A berth may not block another road" (5a) applies wherever
autonomy will not choose the square, which is deliberately wider - the example Adam ruled it on has
three ways in and is not a parking berth by the test above. The two questions stay separate.

**A paste onto a square no train could leave is refused**, naming the square, from both doors
(MT-136). The question is asked of the square rather than of one arbitrary copy, since the paste
itself walks to a copy that can depart.
