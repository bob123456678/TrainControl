# Facing, arrival side and placement

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 4. Which way a train is pointing, and where its tail is

Two different properties.

- **`facing`**: the side the train's front points at. It is which copy of the square the train stands on, and it
  decides where it can go next.
- **`arrivedFrom`**: the side it came IN by. Its tail lies that way.

**Facing cannot answer where the tail is.** Once a train has been turned round the two point the same way while the
carriages have not moved.

**Nothing about a placement is recorded until the railway has accepted it.** Every door that puts a train down asks
`Layout.moveLocomotive`, which refuses when autonomy is running, the locomotive or the point is unknown, or the target
is not a destination. (The rebuild's put-back, the paste and the Place Locomotive dialog may also stand a train on a
copy of a station trains may not arrive at, where no station copy of the square faces its way: the copy is its
direction, OB-284, section 7.) The caller honors the refusal: no placement, no facing, no arrival side, nothing saved,
because the setup survives a restart and the next build would emit the train on a square it was never put on. The log
line `moveLocomotive` writes is what the operator sees.
`regression.testTheRefusalsAreAskedAtTheDoors.testEveryPlacementDoorUsesTheRailwaysAnswer` names every door and the one
allowed to discard the answer.

**A station no track arrives at records which way a train placed on it faces**, so only the routes that way are
offered. Such a square with more than one way out is one copy per way out; with one, or none, it is one Point.
`core.testATrainIsPlacedOnceAndKeepsItsFacing.testASquareNothingArrivesAtFacesItsPlacedTrain`.

### How `arrivedFrom` is set

- **Autonomy writes it on arrival**, the only moment anything knows it for certain, and before any reversal at the
  platform, because turning a train does not move its carriages.
- **A hand placement works it out**: a terminus forces it (one way in); an ordinary station assumes the train drove in
  forwards, so its tail is on the side it is **not** pointing at; **a may-reverse square asks**, because the train is as
  likely to have backed in as driven in.
- **"Behind it" is chosen from the square's own sides, not from the compass.** The compass opposite of the facing is
  right on a straight and wrong on a curve, where no rail may lie that way: the side recorded would name no edge and
  **nothing would be blocked**. So:
  - one side left once the facing's own is taken out: that side (on a straight, still the compass opposite);
  - several left (three ways in): the compass assumption picks, but only where the build really enters by that side;
  - otherwise **nothing is recorded**. A missing side narrows the walk; a wrong one sends it down track the train is
    not on.

  `core.testACurvedPlatformRecordsASideTheBuildUses`, on the `curve-into-platform` scenario with a straight platform
  as the control.
- It can be **set** afterwards from the **"<locomotive> is facing"** menu, in the editor and on the track diagram, under
  its own heading in the same menu, because the two are opposite ends of one train, and because a second submenu is
  what would give the editor a setting the viewer did not have.
- **The sides offered are the ones the BUILD splits on**, not the compass direction of the neighboring point: a reduced
  edge can run several tiles and turn corners, so a rail leaving north and curving east reaches a neighbor lying east.
- Not knowing is a legitimate answer, and it does **not** mean nothing is blocked: where the geometry leaves only one
  way back, the walk still follows it. `arrivedFrom` picks between candidates; it is not a switch that turns blocking
  on.
- **It survives a train being re-stood on a sibling copy.** `Point.setLocomotive` drops the side on a change of
  occupant, right for a different train arriving; when the window re-stands the same train on another copy of one
  square after a turn, the side is carried across.

**When would it not be known?**

- a setup saved before the field existed;
- a square whose **occupant changed** (`Point.setLocomotive` drops the side);
- a square with no named copies at all;
- a square with **three or more ways in** where neither the facing nor the compass opposite separates them;
- and a dismissed placement prompt, which `placeFacing` treats as "do not place the train".

**Every hand-placement door works it out**: the right-click "Place locomotive" item and the assign dialog too, so the
same placement blocks the same track whichever menu it is made from. Adam: *"the missing arrival side should be set -
either from the data, by the user, or randomly."*

The doors differ only in **where the question is put**; the rule is one method:

| door | how it asks |
|---|---|
| diagram drag and paste | `ArrivalSidePrompt.forPlacement`: a dialog, before anything moves, so a dismissal leaves the railway as it was |
| right-click "Place ... facing" | the same dialog, on a placement only: turning a train already standing there does not change which way it came in |
| the assign dialog | a combo in its form, started on the answer and correctable before OK |

`GraphLocAssign` uses a combo because it already HAS a dialog. A value in a combo is seen and can be changed, so it may
show the assumption the popup declines to make on a may-reverse square. `ArrivalSidePrompt.suggestedFor` is the rule
without the dialog and `forPlacement` is it plus the question, so the two cannot answer differently. The combo offers
**sides and nothing else**: recorded, else derived, else the first side the square has (the "randomly" above).

**Clearing is not offered.** Adam: *"clearing should not be possible, only setting."* The menu lists every side the
geometry offers, so a wrong answer is corrected by choosing the right one; a "Not known" option would switch the tail
blocking off. The automatic clear above stays: the railway dropping a fact it no longer holds is not a person declaring
ignorance.

### Pasting a train

> *"Calculate the simple BFS path from the current station to the paste target using the current
> direction. Paste with the direction where the train ends up at the destination. If no path, pick
> randomly from the allowed departure destinations."* — Adam

**The railway is walked**: which way a train ends up facing is decided by the track between the two platforms and by
which way it set off: one that leaves southbound round a loop is northbound one square away. A square with one copy
has one answer, which at a terminus is the reversal.

**The walk answers with the nearest copy a train could be STANDING on**, and the same paste twice gives the same
railway. `Layout.getNeighbors` shuffles on purpose (*"Randomize order to allow for variation in paths"*, right for
autonomy), and on a square trains may turn at the plain and turning copies are the same distance away and face
opposite ways, so the first copy touched would be chosen by the shuffle (Adam, MT-377: *"When 2-8-4
is pasted, it should always face east. Does it?"*). So the walk runs to the end, breadth-first distances decide, and the
tie goes to the copy a train would simply be standing on. **Putting a train down is not a decision to reverse it**; the
facing menu is where an operator makes that. A compulsory turn emits no plain copy, so its turning copy is the answer,
which is Adam's *"for terminuses, they must reverse on paste"* falling out of the same rule.

A may-turn square's turning copy is emitted with `terminus: true`, so `isTerminus()` cannot tell it from a real
terminus (OB-205 claims 1-3, MT-368).

**And the train is PUT on the copy the walk names, not only recorded as facing that way** (OB-270; Adam:
*"Trains should not inadvertently change direction when pasted, so a loc going west
from bottomsecondary should always face east when pasted on bottommaina."*). The copy IS the direction (section 3).
**The rule: the copy taken is the operator's chosen heading where one was asked, and the walked heading otherwise**;
the heading is chosen over the copies a train could leave, so no impossible facing is saved. **Barred arrivals**
(OB-284; Adam: *"this should check for barred
departure directions, not arrival ones.  for barred arrival directions, keep the direction.  for barred
departure directions, turn it to a way trains may arrive in"*): a train facing the way trains may not arrive at the
landing keeps its heading and stands where autonomy will not start it, told why; a heading no copy can leave by is
turned to one trains may arrive in. The same rule holds at the editor's Place and the Place Locomotive dialog
(`AutonomySession.departableFacingsFor`).

**A cut train is walked from the square it was cut from** (OB-270; Adam: *"it should be east.
no train should inadvertently change direction when pasted."*). Control+X takes the train off the railway, so the cut
remembers its square and the paste walks from there (MT-368); the cut heading stands only where no route reaches the
landing. `ui.testACutTrainArrivesTheWayItWouldDrive`.

**Where there is no path**, the heading the train has is kept if the landing can hold it, and otherwise the first copy
it could depart from is taken. Adam: *"as long as the
direction isnt flipped (which it was before)"*, and *"in all your simulations, state
should never drift"*. **The diagram's right-click Place follows the same rule** (OB-296; Adam: *"Yes, keep the
train's heading."*): the heading over every copy the train can leave by, a copy trains may not arrive at included, and
the copy the paste would take (`copyFacing`); otherwise the first copy the menu offers. The item is offered wherever a
copy has a way out.

**A copy with a way out that reaches no station autonomy may choose is kept too** (OB-299), and Why not Moving? says
why autonomy never starts a train there and what to do:
- where another copy of the square reaches a station and a train may be started from it: turn it round;
- where that copy is barred: open its side and turn it round, except where every train turns round at the square
  (opening that side there is the terminus error): drive it off by hand;
- where autonomy starts no train from that copy for another reason: drive it off by hand;
- where no copy reaches one: drive it off by hand, or let autonomy choose a station it can reach (switched on, Can Be
  Chosen in Full Autonomy ticked, and the arrival side open under Trains May Arrive... unless every train turns there).

By hand it is no reason, since a hand send may go where autonomy never does.
`core.testWhyStuck.testACopyThatReachesNoStationSaysSo`, `core.testWhyStuck.testTurningRoundIsOfferedOnlyOntoACopyATrainMayStartFrom`, `core.testWhyStuck.testTurningRoundIsNotOfferedOntoACopyThatIsNoStation`, `core.testWhyStuck.testOpeningASideIsNotOfferedWhereEveryTrainTurns`, `core.testATrainIsPutOnlyWhereItCanStart.testTheRightClickPlaceKeepsTheTrainsHeading`.

> *"Simply don’t place the train, leave it on the clipboard as if no paste had been done."* — Adam,
> on a dismissed prompt

**A dismissed arrival-side question abandons the paste.** It is asked *before* the train moves, so nothing moves, the
clipboard still holds the train, and the next square accepts the same paste. Only a **may-reverse** square can be
dismissed; everywhere else the side is worked out, so no answer means "nothing to record", not "the operator said no".

**And a may-reverse square asks a second question: which way the train should face.**

> *"If pasting at a 'may reverse' square, ask what direction the train should face."* - Adam

**Where the tail lies** is where the train came in from; **which way it faces** is where it will go next. Elsewhere the
second follows from the first and the walk answers it; at a may-reverse square both headings are reachable. The
headings offered are the ones the square can hold, deduplicated, and a square that can hold only one asks nothing. The
walk's answer is the default; a dismissal abandons the paste as the first question's does; the answer given is what is
written.

### What counts as a parking berth

> *"For now, we consider anything with autodestination=false and only one way in/out as a parking
> square. We can revisit dedicated marking if this doesn't work out with clean logic, or if it gets
> too confusing to the user to manage."* - Adam (FR-060)

Two facts, neither enough alone: **autonomy will not choose it** (true of a berth and of a platform kept for hand
dispatch) and **one way in and out** (true of a berth and of a headshunt). Together they are what FR-060 wanted a fourth
designation for, so there is nothing to mark and nothing to migrate. Counted in **squares**, not copies.

**This is not the berth rule's gate.** "A berth may not block another road" (5a) applies wherever autonomy will not
choose the square, which is deliberately wider: the example Adam ruled it on has three ways in.

**A paste onto a square no train could leave is refused**, naming the square, from both doors (MT-136). It is asked of
the square, since the paste itself walks to a copy that can depart.
