# Independent pass, 2026-08-31 - the upgrade path, and the corners nobody was sent to

**Status:** open

**Prefix for citing these findings elsewhere:** `IPR`

**Reviewed:** `e4c94ac9` (branch `autonomy-diagram-r0`, tagged `v3_0_0_rc4`), on 2026-08-31. Reading and
grepping only - nothing was compiled, no test was run, and nothing in the repository was written except
this file. `cs2_sample_layout/` was read and never written.

**This pass had no assigned scope.** Four other reviewers were running at the same time over the last
day of commits, the regression range since the last release, the test suite, and the comments. This one
was told to choose, and to say what it chose and why.

---

## What I chose, and why

I spent the first stretch orienting rather than reporting: `Readme.md` from `## Changelog` down,
`Automation.md`, `AutomationAPI.md`, the source tree, `docs/reviews/README.md` and
`docs/reviews/2026-07-cycle-summary.md`. Then I did something mechanical to decide where to go: for
every class in `src/`, I counted how many of the 45 documents in `docs/reviews/` and the 242 tests in
`docs/manual-tests/tests.md` mention it by name. That is a crude proxy for attention, and a better one
than my own instinct about where bugs live, because it measures where people have already been rather
than where I would like to look.

Three things came out of it, and they are what this pass is:

**1. The four suggested angles are all well-trodden; the upgrade path is not.** Concurrency, resource
lifetime, partial failure and atomic writes each have dedicated passes behind them - `FP`, `RR-C2`,
`PV-C1`, `FCR-B3`, `AU-D4`, the whole `2026-08-24-duplication-robustness`. What nobody has a pass on is
**what happens to a 2.x user's data when it crosses into 3.0.0**. That path runs once per user, it is
the only thing between an existing setup and re-entering it square by square, and
`docs/reviews/2026-08-24-test-suite-audit.md:439` had already noticed that `importLegacy` "is only ever
exercised on 1-2 point hand-built JSON". A one-shot, thinly-tested, irreversible data transform is the
highest-value place in this release to be reading.

**2. `cs2_sample_layout/` is not just a fixture - it is that transform's OUTPUT.** The folder holds
`config/autonomy_legacy/autonomy.json` (the 2.x graph: 62 points, 90 edges) *and*
`config/autonomy/setup.json` + `configuration-Main.json` (what 3.0.0 made of it), side by side. That
turns "this could happen" into "this did happen" for anything about the import, which is what the
discipline document asks for and what made `IPR-A1` findable at all. I read those files as data, diffed
them against each other, and then went to the code that must have produced the difference.

**3. The lowest-attention classes are exactly the new ones.** `StationCaption` (897 lines) is named in
three documents; `AutonomyReport` (104) in one; `RowIcons`, `RouteCapture`, `NodeRouteCommand`,
`LocomotiveMenuItems` in one or none. `docs/reviews/2026-08-28-week-review.md` names the 08-26/08-27
caption work and `LocIconCropDialog`'s clamp geometry under "What was sampled but not deep-read". Those
named gaps are where `IPR-A2` and the B and C findings came from.

Method note, for calibration: I read the import path, the caption path and the real data myself. Three
parallel audits were run under my direction over the new route editor, the crop dialog and the fourteen
least-mentioned classes. **Nothing they reported is in this document unless I re-derived it from the
source myself**, and where I could not, the entry says so in its own confidence line. Several of their
findings did not survive that and are not here.

---

## A - high

### A1 - a legacy import writes each station's LENGTH LIMIT into the track's LENGTH, and leaves the limit unset

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading, and confirmed against the real railway |

`AutonomySession.importLegacy`, `src/org/traincontrol/automationui/AutonomySession.java:751-753`:

```java
int length = point.optInt("maxTrainLength", 0);

if (length > 0) store.setTileLength(tile, length);
```

Those are two different quantities and 3.0.0 still holds both of them separately.

- **`maxTrainLength`** is a per-point capacity: *"Sets the maximum train length allowed at this point"*
  (`Point.java:855`). `Point.validateTrainLength` reads it (`:872-877`) - `0` means no limit - and that
  is what `Layout.java:2214` asks before accepting a path:
  `if (!path.get(path.size() - 1).getEnd().validateTrainLength(loc))`. The diagram design plan lists it
  in its own ruling table as *"max train length | **shared** | a physical capacity"*
  (`docs/plans/2026-08-01-diagram-autonomy-plan.md:485`). 3.0.0 keeps it as a point property:
  `POINT_OPERATIONAL_KEYS` (`AutonomySession.java:1779`) includes `"maxTrainLength"`, the editor prompts
  for it (`AutonomyEditorPanel.java:1118-1122`), and `AutonomyChecks` warns about a station without one
  (`NO_MAX_TRAIN_LENGTH`, `AutonomyChecks.java:826-831`).
- **`tileLength`** measures the track on a square. `GraphReducer.lengthOf` / `sumLength`
  (`GraphReducer.java:1047-1063`) add it along a traced path to give an edge its length, which feeds the
  train-length release accounting and the new `SHORTEST_LENGTH` / `LONGEST_LENGTH` preferences. The plan
  states it separately: *"Length is authored per tile, summed per edge"* (`:823`).

The import puts the first number into the second field, and never sets the first field at all.
`CARRIED_SETTINGS` (`AutonomySession.java:493-494`) is `priority, speedMultiplier, excludedLocs,
active` and does not include `maxTrainLength`; nothing else in `importLegacy` writes it; and the builder
does not derive it back (`grep maxTrainLength` over `AutonomyBuilder.java` and `GraphReducer.java`
returns nothing).

**This is not hypothetical - it is what happened to the sample railway.**

| legacy point | legacy `maxTrainLength` | `setup.json` `tileLengths` |
|---|---|---|
| `BottomInnerOtherside` | 3 | `5:14,3` = 3 |
| `BottomMainB` | 4 | `5:20,13` = 4 |
| `BottomMainC` | 2 | `5:20,14` = 2 |
| `TopMainR1` | 3 | `5:5,4` = 3 |
| `TopMainR1Inter` | 4 | `5:1,10` = 4 |
| `TopMainR2Inter` | 4 | `5:0,11` = 4 |
| `BottomMainCTerm` | 2 | *(no square - point unmatched)* |

Those are the **only** six entries in `tileLengths`, and all six sit on station squares - a genuine
track measurement would land on plain track too. Meanwhile every one of the 30 points in
`configuration-Main.json` that carries `maxTrainLength` carries it as `0`. The seven limits Adam set in
2.x are gone, and six of them are now pretending to be track measurements.

**What it costs on the layout.**

1. **The capacity check stops working.** `validateTrainLength` returns true unconditionally at 0, so
   `Layout.java:2214` refuses nothing and autonomy will send a train longer than a platform to that
   platform - the exact behaviour the feature was added for (`Readme.md:1383`).
2. **Six squares acquire a fabricated track length.** They feed the edge lengths the release accounting
   uses, and the changelog says that accounting *"only bit layouts that have both track lengths and
   train lengths recorded"* - a condition this import manufactures out of nothing. They also feed
   `SHORTEST_LENGTH`/`LONGEST_LENGTH`, where an unmeasured square counts 1 (`Layout.java:356`) and these
   six count 2-4, so the shortest-track rule would systematically route *around* the six platforms.
   Adam's configuration uses `RANDOM_ANY_STATION`, so this half is latent on his railway rather than
   active. Say that rather than overstate it.
3. **The legacy `edges[].length` values are dropped entirely.** `importLegacy` reads only
   `legacy.optJSONArray("points")` (`:530`); `"edges"` never appears in it. Adam's file has 30 edges with
   a non-zero length (values 1-6). The plan's own migration note asked for the opposite - *"matched point
   names, **lengths distributed onto tiles**, homes and exclusions carried over"* (`:432`) - and an edge
   length is derivable onto a tile, because the reducer already counts an edge's END tile exactly once
   (`GraphReducer.java:1041-1049`).

**Not entirely silent, and this entry should say so.** `AutonomyChecks` raises one WARNING per station:
*"{0} has no maximum train length, so no train is ever too long for it."* A user who runs the checks
after importing is told the limits are absent. They are not told they were present, nor where the
numbers went, and the import dialog (`autosetup.ui.infoLegacyImported`) reports named / placed /
reversing / other-settings / skipped / unmatched and mentions neither quantity.

**Why it survived.** The test that pins this agrees with it:
`test/core/testAutonomyDiagramSession.java:1680` puts `maxTrainLength: 240` on a legacy point and
asserts `getTileLength(tile) == 240`, with the message *"the length did not come across"* - the word
"length" doing the same double duty the code does. `2026-08-24-test-suite-audit.md:439-444` had already
flagged that `importLegacy` is only run on hand-built two-point JSON and asked for a run against a real
legacy graph; that is exactly the run that would have caught this. The introducing commit (`ed1c2cf0`)
says the import hands back *"every station name, station flag and length it was holding"* - one word
"length" where there are two quantities.

**How to confirm or refute.**

1. *Against the shipped data, no execution at all.* A short script over
   `cs2_sample_layout/config/autonomy_legacy/autonomy.json` and
   `cs2_sample_layout/config/autonomy/setup.json`: print every legacy point with `maxTrainLength > 0`,
   every `tileLengths` entry, and every `configuration-Main.json` point with a non-zero
   `maxTrainLength`. If this finding is right the first two lists are the same six numbers and the third
   is empty. That is what I got. **Run this first - it settles the finding in a minute or refutes it.**
2. *By running.* Add two asserts to
   `testAutonomyDiagramSession.testLegacyNamesLandOnTheSquaresCarryingTheirSensors` beside the existing
   one: `session.getPointProperty(tile, "maxTrainLength")` is `240`, and
   `session.getStore().getTileLength(tile)` is `0`. Both fail at HEAD.

**Shape of a fix**, not applied here, and the second half is Adam's call:

- *Certain:* carry `maxTrainLength` as a point property. Adding `"maxTrainLength"` to `CARRIED_SETTINGS`
  puts it where `POINT_OPERATIONAL_KEYS` already expects it and where the editor reads it, and it gets
  counted in `result.settings` so the dialog says it happened.
- *Judged:* whether `setTileLength` should be dropped from that block, or replaced by distributing
  `edges[].length` onto the end tile of each matched edge as the plan asked. Dropping alone is the
  smaller change and removes fabricated data; distributing recovers 30 real measurements. **Prefer the
  smaller fix unless Adam wants the lengths back.** Either way, whether to repair
  `cs2_sample_layout/config/autonomy/` in place is a separate decision and NOT one to take without him.
- The sweep: `grep -n setTileLength src/` returns `AutonomyCompanionStore.java:1003` (the setter),
  `AutonomySession.java:3717` (the editor's door), and this line. There is no second entrance.

### A2 - a save that prunes tile properties reports nothing at all, and the display is the only thing missing

**FIXED 2026-09-03**, re-derived by pass 4 of the release review.

`isClean()` counts three lists and the dialog was built from two, so a save whose only casualty was
track lengths and directions passed `isClean()` false, produced an empty text and showed nothing at all -
the numbers somebody had typed went and the application said not a word.  `getDroppedTileProperties` had
no reader in `src/` at all, which is how it stayed missing.

The third list reaches the dialog, with its own sentence in all eight bundles.  And the words are a
function now - `AutonomyReport.describe(report)` - because nothing could have caught this while they
were built inside a method whose only observable effect is a modal dialog.
`testATileGoingAwayDropsWhatWasOnIt` asserts them, mutation-confirmed by taking the new block out again.

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

`AutonomyCompanionStore.Reconciliation` carries three lists. `isClean()` (`:3217`) is
`droppedTileProperties.isEmpty() && forgottenNames.isEmpty() && ...`, so it correctly reports "not
clean" when only tile properties were dropped. `AutonomyReport.show` (`AutonomyReport.java:64-102`) then
passes that gate, builds `text` from **only** `getForgottenNames()` and `getNamesStillReferenced()`, and
ends with:

```java
if (text.length() > 0)
{
    JOptionPane.showMessageDialog(owner, text.toString(), ...);
}
```

When the pruning was only tile properties, `text` is empty and **no dialog is shown**.
`grep -rn getDroppedTileProperties src/` returns the getter and nothing else - there is no reader
anywhere in the application, only four in `test/core/testAutonomyDiagramStore.java`. There is no message
bundle key for it either (`messages.properties` has `autosetup.ui.infoNamesForgotten` at 1791 and
`autosetup.ui.infoNamesStillReferenced` at 1792 and no third), so this was never written rather than
accidentally deleted.

**What goes silently.** `reconcile` fills that list at `:3250, 3251, 3252, 3260, 3261, 3275, 3295, 3311,
3315, 3320, 3377, 3394` - tile lengths, **tile directions**, **barred arrivals**, **station signals**,
blocked points, link names, **switched-off links**, captions, station markings and portal pairings. Each
site carries a comment saying it must be visible; `:3308-3310` says outright *"a diagram edit that
quietly costs a link name should be visible rather than discovered later."*

**It matters most for the entries with no name attached.** The name-based losses are reported. The ones
that are not are precisely the safety restrictions: Adam's real setup has 105 `tileDirections` and 2
`disabledLinks` on unnamed plain track, and 8 `barredArrivals` and 7 `stationSignals`. A one-way
restriction or a signal pairing disappearing without a word is the loss that shows up later as a train
running the wrong way, and the four call sites of `AutonomyReport.show`
(`AutonomyEditorPanel.java:6546`, `AutonomyMenu.java:754`, `AutonomyViewerPanel.java:1347`,
`LayoutRightclickAutonomyMenu.java:810`) are every save.

**Severity A rather than B** because the data is removed and the user is not told, and because the
mechanism to tell them exists, is populated, is tested, and is simply never read. This is the shape the
cycle summary calls a rule enforced at one door of two: the model's half is complete and the view's half
was never written.

**How to confirm or refute.** Print `report.isClean()` and `report.getDroppedTileProperties().size()`
at `AutonomyReport.java:64` on a save that deletes a square carrying a portal pairing, a protecting
signal or a tile direction but **no station name**. Expected: `false` and a non-zero size, and no
dialog. `testAutonomyDiagramStore.java:1882` already builds a fixture of that shape, so the fixture work
is done. Refuted if a dialog appears.

---

## B - medium

### B1 - "Highlight on Diagram" throws on any route containing a locomotive command

**FIXED 2026-09-03**, re-derived by pass 4 of the release review.  Both loops - the commands and the
conditions - ask `hasAddress()` before reading one, which is what that method exists for.  The
conditions loop this document says it did not settle is covered by the same edit.

**Honest about the test.**  `testHighlightSurvivesALocomotiveCommand` drives the button on a route
holding a locomotive command and asserts it does not throw, and it does NOT discriminate: with no parent
window there is no diagram to highlight on, so the method returns before walking the rows - measured, by
taking the guard out again.  What is pinned instead is the contract the fix rests on, a locomotive
command answering `hasAddress()` false and `getAddress()` throwing, and the gap is written at the test.

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

`RouteEditorFrame.highlightOnDiagram`, `src/org/traincontrol/gui/RouteEditorFrame.java:2399-2402`:

```java
org.traincontrol.base.RouteCommand command = entry.toCommand();

if (command != null && command.getAddress() > 0) commanded.add(command.getAddress());
```

`RouteCommand.getAddress()` is `Integer.parseInt(this.commandConfig.get(KEY_ADDRESS))`
(`RouteCommand.java:333`) and its own javadoc two lines above says *"Only valid when hasAddress() is
true - other command types have no address and this will throw."* Of the eleven factories, only
`RouteCommandAccessory` (`:86`), `RouteCommandFeedback` (`:103`) and `RouteCommandAutoLocomotive`
(`:183`) put `KEY_ADDRESS` in the map. Speed, direction, function, route, stop, autonomy-lights-on,
lights-on and functions-off do not, so this is `Integer.parseInt(null)` -> `NumberFormatException`.

**The sibling site already carries the guard and the reason.** `MarklinRoute.setDelay`
(`MarklinRoute.java:960-963`): *"Locomotive, function and route commands carry no address. Skipping
them matters: calling getAddress() on one throws"* - and it tests `rc.hasAddress()`. This one does not.
That is the cycle summary's signature error, one file over.

**Nothing catches it.** The button is built by `button(String, Runnable)` at `:1025-1034`, whose whole
body is `button.addActionListener(e -> action.run())` with no try/catch, and
`grep -rn setDefaultUncaughtExceptionHandler src/` returns nothing. So the button does nothing at all
and a stack trace goes to stderr, which on a packaged run is nowhere the user will look.

The conditions loop three lines below (`:2408`) is a separate question I did not settle; the commands
loop alone establishes the defect. MT-064 closed this feature as "Works" on 2026-08-22, but its
instruction is *"open a route that has both commands and conditions"*, which an accessory-only route
satisfies.

**How to confirm or refute.** Open any route with a `locspeed` row and press **Highlight on Diagram**;
watch stderr and watch the diagram stay unhighlighted. Headless: build a
`CommandRow(Kind.LOCOMOTIVE_SPEED, "<a loc>", "40")` and print `row.toCommand().getCommandConfig()`
(no `ADDRESS` key) and then `row.toCommand().getAddress()` (throws). The fix is one call:
`command.hasAddress() && command.getAddress() > 0`.

### B2 - a condition written with a bracket in a non-leading position is re-read with the wrong operator

| | |
|---|---|
| **Disposition** | fixed - one token in `read`, and the finding's own trace is the test |
| **Confidence** | confirmed by reading (hand-traced both directions); **not reachable on Adam's own data** |

`ConditionOutline.write` emits a `NodeGroup`'s contents at `depth + 1` (`:386`), and `writeChild`
(`:422`) may already have bumped the depth for a cross-operator child. When both fire, the outline skips
a level - and `read` (`:210-239`) cannot invert a skip, because it recurses with the *deeper* depth and
then picks the intervening level up as a separate item of the outer one.

Traced by hand on `Or(3, And(Group([Or(1,2)]), 4))`, which is `3 or ((1 or 2) and 4)`:

```
of()   ->  cond(0,3) join(0,OR) cond(2,1) join(2,OR) cond(2,2) join(1,AND) cond(1,4)
                                ^^^^^^^^^ depth jumps 0 -> 2
read() ->  Or(Or(3, Group(Or(1,2))), Group(4))          =  "3 or 1 or 2 or 4"
original                                                 =  "3 or ((1 or 2) and 4)"
```

**The AND becomes an OR.** `problems()` flags nothing (depth 0 is all OR, depth 1 all AND, depth 2 all
OR), so the editor shows no red, `updateReadsAs()` prints the wrong reading, the Test button evaluates
the wrong expression, and `onSave` writes it back. The route then fires on a condition nobody wrote.

**Reachability, honestly.** The new editor cannot *build* this shape: `read` wraps every deeper run in a
`NodeGroup`, and `write` un-indents a group by exactly one level, so editor-origin trees round-trip - I
traced `A and (B or C) and D` and `A or (B and (C or D))` and both are stable. The shape is a
**text-origin** one: `NodeExpression.normalize`'s own javadoc says *"the text parser applies stacked
operators LIFO, so text-origin trees are right-nested and their left children are always leaves or
groups"*, and `Or(3, And(Group, 4))` satisfies that exactly, so `normalize` leaves it alone on the way
in from `fromJSON`. So the population at risk is **routes authored in 2.x's text editor with a bracket
anywhere but at the start**, loaded into the new editor and saved.

**Adam does not have one.** I dumped every condition in
`cs2_sample_layout/config/gleisbilder/routes.json`: 19 bare `NodeRouteCommand`, 19 `NodeAnd` chains,
1 `NodeOr`, and **zero** `NodeGroup`. So this is "could happen", not "does happen", which is why it is
B and not A. `fromTextRepresentation` also has no production caller left in `src/` - only tests - so no
new tree of this shape can be created; only stored ones can be met.

**Why the tests miss it.** `testALeadingGroupSurvivesBeingShownAsAnOutline`
(`test/core/testConditionOutline.java:403`) uses a leading group at the OUTERMOST level, which is a
0->1 jump and round-trips. The defect needs a group leading a non-outermost level.

**How to confirm or refute.** Pure unit, no GUI, in `testConditionOutline.java` where `meaning` and
`sensor` already exist:

```java
NodeExpression original = new NodeOr(sensor(3),
    new NodeAnd(new NodeGroup(Arrays.<NodeExpression>asList(new NodeOr(sensor(1), sensor(2)))),
                sensor(4)));

System.out.println(meaning(original));
System.out.println(meaning(ConditionOutline.toExpression(ConditionOutline.of(original))));
```

Those two printed lines are the whole finding.

**Disposition: fixed. The finding is right about the symptom and about the reachability; it is wrong
about which side is at fault, and that is what made the fix one token.**

Tracing `Or(3, And(Group([Or(1,2)]), 4))` through `write` gives exactly the rows the finding prints,
but there is no skipped level in them - every depth is *justified*:

| row | depth | why |
|---|---|---|
| `cond(3)` | 0 | the outer OR's left |
| `join(OR)` | 0 | the outer OR |
| `cond(1)`, `join(OR)`, `cond(2)` | 2 | the AND is one level in (its word differs from OR); the bracket inside it is one further (its word differs from AND) |
| `join(AND)` | 1 | the AND |
| `cond(4)` | 1 | the AND's right |

0, 0, 2, 2, 2, 1, 1. The jump from 0 to 2 is not a level going missing - it is **the depth-1 rows
coming out after the depth-2 ones**, because the AND's left child is the bracket and a left child is
written first. `write` and `writeChild` are both correct; what they produce is an outline whose second
level opens with a run belonging to its third.

`read` is the half that cannot cope, and the reason is in one expression:

```java
NodeExpression inside = read(rows, at, row.getDepth());   // before
NodeExpression inside = read(rows, at, depth + 1);        // after
```

Those two are the same number wherever the run begins exactly one level deeper, which is why nothing
else moved. They differ when a level's own rows come out after a deeper run:
reading the run at 2 made `1 or 2` a **sibling of the 3** at depth 0, and then read the depth-1
remainder as a second sibling, a level holding one item whose AND had nowhere to go. Reading it at
`depth + 1` puts the run where the writer put it - the first item of the next level down - and the AND
that follows joins it.

The reading is now `3 or ((1 or 2) and 4)`, which is what went in.

`testABracketAfterTheStartSurvivesBeingShownAsAnOutline` is the finding's own trace, asserted rather
than printed. **It was written first and failed** (18 tests, 1 failure), and passes on the fix. It
also asserts `problems(shown).isEmpty()`, because "the editor shows no red" is half of why this was
dangerous - a flagged outline would at least have stopped the save.

Nine neighbouring classes re-run green: `testAdvancedRoutes`, `testConditionRows`, `testParseCS2Routes`,
`testRouteRoundTrip`, `testRoutes`, `testRouteEditorRoundTripCases`, `testRouteEditorValidation`,
`testRouteEditorShading`, `testCommandTableMarks`.

**The reachability conclusion stands; the screen behind it was wrong, and the right one is better
news** (`VD9-C1`).

The finding said Adam has **no** `NodeGroup` in `routes.json`. He has two, both in
*Auto Emergency Stop Main BC*, and I repeated the claim without checking it. Screened properly - by
walking the condition trees rather than counting types - that route is:

```
Or( Group(And(a,b)), Group(And(c,d)) )
```

Both groups are direct children of the root, so **both are outermost**, which is the shape that always
round-tripped. Run through `of()` and `toExpression()` it comes back byte-identical:
`or(and(1,2),and(3,4))` in and out, with `problems()` empty. `IPR-B2` does not reach his railway, and
neither does `IPR-B3` - which is worth knowing, because `IPR-B3` was filed wanting a ruling and its
cost was unknown. It is not refusing anything he owns today.

So the fix is still costless and still worth having, for the stored non-leading bracket nobody has
met yet - `fromTextRepresentation` has no production caller left, so no new tree of this shape can be
created either.

**And the shape is wider than a bracket** (`VD9-C2`). `Or(And(Or(1,2), 4), 3)` contains no
`NodeGroup` at all and came back as `or(or(or(1,2),4),3)` under the old reader - the same lost AND.
So the family is *a cross-operator child that is itself the left child of a cross-operator parent*:
two alternations in a row down the left spine, each bumped by `writeChild`, both written before the
outer joiner. One alternation is safe; two is not. A `NodeGroup` is one way to arrive there and was
never the requirement.

**Which means both earlier screens of Adam's routes asked the wrong question - and so did the third**
(`VD9-C2`, then Adam, 2026-09-03).

He asked the question that settles it: *"we would want to parse the JSON into a NodeExpression in the
old one, and see if 3.0.0 has logically equivalent expressions in those routes."* Every screen before
that - including my corrected one - walked the **file**, and the file is not what either engine holds:
`NodeExpression.fromJSON` runs `normalize`, which **inserts** a `NodeGroup` around a cross-operator
left child. A structural reading of `routes.json` is answering about a tree that no engine builds.

So it is measured now, under both jars, and it is part of the parity harness rather than a thing done
once: `ConditionParityDriver` runs under each engine and re-emits what that engine's `fromJSON`
actually built, and `compare-conditions.py` compares the two **by truth table** - because two trees
that bracket differently and mean the same thing must not read as a difference, which is the entire
reason the question is worth asking.

```
conditions compared:      39
logically equivalent:     39
  of which byte-identical:39
  of which reshaped:      0
NOT equivalent:           0
could not be compared:    0
present in only one:      0
```

The last two lines matter and were cut from the first version of this quotation (`VD10-C16`):
"conditions compared" is equivalent plus not-equivalent and **excludes** both of them, so without
them a reader cannot tell whether 39 is the whole population or merely the part that could be read.

**All 39 of his conditions come out of 2.8.1 and 3.0.0 identical, not merely equivalent** - so
`normalize` finds nothing to reshape in his file, which is the same conclusion the structural screens
reached, now reached by the right road.

The comparison was checked against three constructed pairs before it was believed: a reshaped-but-
equivalent pair reads as equivalent, an identical pair reads as byte-identical, and the actual
`IPR-B2` corruption reads as **not equivalent**. A comparison that cannot fail is not evidence.

### B3 - two bracketed groups at the same indent are flagged red and the save is refused, for an outline the reader parses correctly

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

`ConditionOutline.problems` (`:147-170`) keys its `settled` map on **depth alone, across the whole
list**. `read` does not: it consumes each indented run in its own recursive call, so two separate runs
at depth 1 are unambiguous to it. The check is therefore stricter than the code it protects.

`A and (B or C) and (D and E)` gives

```
cond(0,A) join(0,AND) cond(1,B) join(1,OR) cond(1,C) join(0,AND) cond(1,D) join(1,AND) cond(1,E)
```

`problems()` returns `{7}` - depth 1 saw OR first and then AND - so `everythingWrong()` adds
`route.ui.frameLogicDisagrees` and `onSave` refuses. `toExpression()` on the same rows is **correct**.
The editor is refusing to save an expression it reads perfectly, and the user's only ways out are
Discard or restructuring a condition they did not get wrong.

Reachable by ordinary gestures (indent B, set OR, add C, outdent the joiner, indent D, add E) and by
loading such a route.

**The counter-argument, stated fairly:** the class javadoc declares the rule flatly - *"EVERY WORD AT A
LEVEL MUST BE THE SAME WORD"* - so this may be a deliberate simplification. But the rationale it gives
is *"'and' and 'or' **side by side** at one level is a sentence with two meanings"*, and two runs
separated by an outer-level line are not side by side. This is also the shape Adam has ruled on before
(`docs/manual-tests` and the guards-need-a-way-past rule): an over-strict check that blocks a legitimate
gesture is worse than no check. **This wants a ruling from him more than it wants a patch.**

**How to confirm or refute.** Build those nine rows in `testConditionOutline.java` and print
`ConditionOutline.problems(rows)` (expect `[7]`, should be `[]`) beside
`describe(ConditionOutline.toExpression(rows))` (expect the correct tree). If both come back as I say,
the check and the reader disagree and the only question left is which one is right.

### B4 - pressing OK in the crop dialog at full zoom-out allocates an image proportional to the SQUARE of the source

| | |
|---|---|
| **Disposition** | fixed - reproduced at the finding's own numbers, 502 MB for a 296 x 114 icon |
| **Confidence** | the arithmetic is confirmed by reading; the OutOfMemoryError itself needs execution |

`LocIconCropDialog.CropPanel.contentOf` (`:1387`) allocates the whole overhanging region:

```java
BufferedImage out = new BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_ARGB);
```

`region` is `sourceRect()` = `cropWindow() / getScale()`. `getScale()` is
`fitScale() * MIN_ZOOM * (MAX_ZOOM/MIN_ZOOM)^zoomFraction` (`:931, 960`), `MIN_ZOOM = 0.5` (`:88`), and
`fitScale()` is `min(aW/srcW, aH/srcH)` over the panel inset by `WINDOW_MARGIN = 44` (`:910-917`). At
`zoomFraction = 0` - the left end of the slider, which `setZoomFraction` clamps to and which the 0.5x
allowance exists to permit - `scale = 0.5 * fitScale`, so the region is about twice the source in each
direction and its **area is quadratic in the source's dimensions and independent of the dialog's size**,
because window and `fitScale` both scale with the panel.

Worked through for the packed panel (`aW = 512, aH = 332`, `frameAspect = 296/114`):

| source | region | bytes at 4/px |
|---|---|---|
| 4032 x 3024 (phone) | 9326 x 3589 | ~134 MB |
| 8000 x 6000 | 18506 x 7121 | ~528 MB |
| 12000 x 9000 | - | ~1.2 GB |

At `zoomFraction = 0` the region always overhangs, so `wholelyInside` is false and the cheap
`getSubimage` branch (`:1381`) is never the one taken. `ImageIO.read` at `TrainControlUI.java:23207`
applies no size cap. The whole allocation is then thrown away - the output is 296 x 114.

The `OutOfMemoryError` lands in the OK `ActionListener` on the EDT, so the symptom is "OK does nothing,
the dialog stays open", not a visible crash.

**How to confirm or refute.** No OOM needed to establish it: headlessly build
`new CropPanel(new BufferedImage(8000, 6000, TYPE_INT_RGB), 296, 114)`, `setSize(600, 420)`,
`setZoomFraction(0.0)`, and print `sourceRect()` and `region.width * (long) region.height * 4`. If the
first line reads roughly `[x=-5253,y=-1766,width=18506,height=7121]` the finding stands.

**Disposition: fixed. The recipe was run and the finding is exact.**

`new CropPanel(new BufferedImage(8000, 6000, TYPE_INT_RGB), 296, 114)`, `setSize(600, 420)`,
`setZoomFraction(0.0)` gives a region of **18506 x 7120** - the predicted 18506 x 7121 to a pixel -
and `contentOf` allocated all of it, **502 MB**, to produce a 296 x 114 icon.

The overhang branch now builds the rectangle at the size the caller can use:

```java
double shrink = Math.min(1.0, Math.min((double) this.outWidth / region.width,
    (double) this.outHeight / region.height));
```

with the canvas at `region * shrink` and the same `drawImage` under a `g.scale(shrink, shrink)`, so the
offset stays in the rectangle's own coordinates. 502 MB becomes 133 kB, and nothing is lost: the
caller's own scale-down was already one bilinear step, and this is that step done before the memory is
spent rather than after.

**The wholly-inside branch is left alone deliberately.** A rectangle inside the source can only be as
large as the source - linear in it, and it is the picture the user opened. It is the *overhang* that
is quadratic, and only because the frame is allowed off the edge.

`testTheCropAtFullZoomOutDoesNotAllocateTheSquareOfTheSource` asserts on the allocation rather than on
the symptom: waiting for an `OutOfMemoryError` needs a JVM sized to fail, which is a test that passes
on a bigger machine for the wrong reason. It calls `contentOf` by reflection and asks what it built. It
was written first and failed with the message above (6 tests, 1 failure), and it carries three
assertions, not one - the allocation, the **aspect ratio** (a bound that changed the shape would
stretch the photograph into the icon), and that `getCroppedImage` still returns 296 x 114 all the way
through. Its precondition asserts the region really does overhang, so it cannot pass by exercising the
cheap branch.

`testEveryWindowWearsTheIcon` re-run green.

---

## C - low

### C1 - the label migration is a fourth door past "one station, one caption", and the sample railway has walked through it

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | mechanism confirmed by reading; the provenance of the sample entry is inference |

`AutonomySession.setCaption` (`:1092-1106`) carries the rule and its own comment names the doors:

> One station, one caption - decided HERE rather than at each door. There are three ways to caption a
> station: place it automatically, choose the square yourself in the autonomy editor, and now drag the
> square it sits on in the track diagram editor. Only the first knew to remove the old one, so choosing
> a new square left the station named twice on the diagram and nothing said which was current.

There is a fourth. `migrateStationLabels` (`:1663-1668`) writes straight to the store:

```java
if (station != null)
{
    store.setCaption(where, station);

    migrated = true;
}
```

`store.setCaption` is keyed by the caption square, so this adds rather than replaces, and
`AutonomyCompanionStore.java:5033-5035` says so: *"Several squares may name the same station."* The
store is right to allow it; the session is the layer that decided it should not happen, and this call is
below that layer. (The other direct `store.setCaption` writer, the legacy import at `:746`, IS guarded -
`captionsFor(tile).isEmpty()`. The rest are clears or the deliberate snapshot restore at `:190-204`.)

**The route is ordinary.** `migrateStationLabels` resolves a diagram label through `tileNamed`, which
searches `store.getPointNames()` - so on a setup with no names yet it resolves nothing and deliberately
**leaves the label in the `.cs2` file** (`:1699-1708`: *"A label naming a station this setup has never
heard of is left exactly where it is"*). So:

1. First open of a setup on a diagram carrying `Point:<name>` labels - nothing resolves, labels stay.
2. The user imports their 2.x `autonomy.json`. That sets the point names, and captions each station on
   its own square (`:744-746`).
3. Next launch, `AutonomySession.open` runs the migration again. The names resolve now, and
   `store.setCaption(where, station)` adds a **second** caption for a station that already has one.

The station's name is then drawn twice, on two squares, with nothing saying which is current - word for
word the state `setCaption`'s comment exists to prevent.

**It is in the sample railway.** `setup.json` has 31 caption entries for 30 stations; `5:6,4`
(`TopMainR2`, page `1 - Main`) has two:

```
"5:6,4": "5:6,4",     <- the import's self-caption
"5:6,5": "5:6,4",     <- a migrated Point: label
```

`1 - Main.cs2.bak` - the migration's own backup - carries `.text=Point:TopMainR2` at id `0x505` = (5,5),
and the current `1 - Main.cs2` is that diagram shifted one column right (252 of 260 tile coordinates
match at dx=+1, dy=0), putting the label at (6,5). Every other station has exactly one caption. I have
not reconstructed Adam's actual launch order, so treat "this entry came through this path" as inference;
the path is confirmed by reading.

**Nothing else complains.** `AutonomyChecks.checkStationLabels` (`:954`) takes the shown stations as a
`Set<TileKey>`, so two captions for one station collapse to one member and no finding is raised.

**Severity.** C, not B: nothing about movement, locking or routing reads a caption. Arguable at B
because the extra pill covers a neighbouring square, and `moveCaption` only ever moves one of the pair,
so the user cannot easily be rid of the other.

**How to confirm or refute.** For the state: group `captions` by value in `setup.json` and look for a
value with two keys. For the mechanism, a test beside the existing migration tests: open a session on a
page carrying `Point:Foo` where no square is named `Foo` yet (**assert `captionsFor` is empty** - that
precondition is the whole point of the test), import a legacy point naming that square `Foo` as a
station, call `session.open(...)` again, and assert `session.captionsFor(tile).size() == 1`. It will be
2 at HEAD. The smaller fix is one word - the session's `setCaption` rather than `store.setCaption` - but
check first whether `touched()` firing during `open()` is acceptable, because that is the whole
difference between the two.

### C2 - a saved crop view restores to a different rectangle when the dialog has been resized

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | the dependency is confirmed by reading; the magnitude is not re-derived by me |

The five saved numbers are `centerX, centerY, zoomFraction, frameAspect, frameSize`
(`copyViewInto` `:844-853`, restored in `startAtCover` `:985-1004`, written and read at
`TrainControlUI.java:23345` / `23386`). The centre is panel-independent, as the field javadoc at
`:339-346` says. **The crop SIZE is not.**

`sourceRect().width = cropWindow().width / getScale()`, and each half takes its own independent `min`
over the panel:

- `largestWindow` (`:682-700`) fits width-first and falls back to height,
- `fitScale` (`:910-917`) is `min(availableWidth/srcW, availableHeight/srcH)`.

They cancel only while both are limited by the same axis. When the panel's aspect crosses either the
source's aspect or `frameAspect`, the limiting term flips on one side and not the other, and the
restored rectangle is a different size from the one that was saved - at the same five numbers. The
dialog is resizable and the class says so itself: *"Recomputed on every use rather than cached, because
it depends on the panel size and the panel is resizable"* (`:657-658`).

The user path is the one the OB-125 manual test already asks Adam to run - crop an icon, close, reopen
the crop editor - with one extra step: resize the dialog first. The same mechanism means resizing the
dialog mid-session silently changes what will be cropped even though nobody touched the zoom.

`testTheCropPanelOpensOnARememberedView` (`test/ui/testLocIconCrop.java:361`) cannot catch it: it
round-trips the five **fields** at one fixed `setSize(600, 420)` and never asserts `sourceRect()`.

**I confirmed the dependency, not the size of it.** The worked example I was given (source 3500x2000, a
48 % tighter crop between a 600-wide and an 1100-wide dialog) I did not re-derive, and it is not load
bearing - the mechanism does not depend on the numbers.

**How to confirm or refute.** Two panels, same source and same five view numbers,
`setSize(600, 420)` against `setSize(1100, 420)`; print `sourceRect()` from each. Different
`width`/`height` confirms it; identical refutes it.

### C3 - the copy mark is filled white regardless of the ink it is given, so it disappears on a selected row

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

`RowIcons.java:117-118`, inside the `copy(int, Color)` overload:

```java
g.setColor(colour);
g.drawRect(1, 1, w, h);

g.setColor(Color.WHITE);              // <- not `colour`
g.fillRect(1 + gap, 1 + gap, w, h);

g.setColor(colour);
g.drawRect(1 + gap, 1 + gap, w, h);
```

The whole reason the `Color` overload exists is stated in `plus`'s javadoc (`:137-148`): *"a selected
row is painted in the look-and-feel's selection blue, against which a mid-green plus is very nearly
invisible."* `RouteEditorFrame.java:1623` sets `ink = which.getSelectionForeground()` - white in the
default look and feel - and `:1648` calls `RowIcons.copy(mark, ink)`. So on a selected row the front
sheet is filled white AND outlined white and the two-sheets glyph collapses into a white block.
`trash`, `plus`, `arrow` and `indent` are stroke-only and unaffected; `copy` is the only one of the five
that paints a fill. Unselected rendering is correct, white fill on a white table, which is why nobody
has seen it.

Not previously reported: D8 in `2026-08-21-review-findings.md` cleared `RowIcons` with "Nothing found",
and no review mentions `Color.WHITE`.

**How to confirm or refute.** Open the route editor, select a row that has a copy mark, and compare that
cell against the delete and add marks on the same row.

### C4 - the crop clamp's overlap guarantee is in panel pixels, and falls below one source pixel on a small photograph

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | **reported to me and NOT re-derived by me** - treat as a pointer, not an established finding |

`clampCenter` (`:1141-1156`) keeps `keep = min(24, min(Wpx,Hpx)/3) / scale` source pixels of the
photograph under the frame. That numerator is in *panel* pixels, so `keep` falls under one source pixel
once `scale > 24`, which happens for any source under roughly 682 x 442 at full zoom. The claim put to
me is that a full-zoom drag to the exact clamp bound on a 150 x 100 source then rounds
`sourceRect()` to `[x=-5, width=5]` - zero intersection with the source - so `contentOf` composes a
wholly white image while the panel still shows a sliver of photograph inside the frame, contradicting
`clampCenter`'s own stated purpose at `:1129-1133`.

I verified that `keep` is divided by `scale` and that `sourceRect` rounds four quantities
independently, which is enough to say the invariant is expressed in the wrong units. I did **not**
re-derive the rounding to zero. It needs a source smaller than the icon plus a drag to the exact bound,
so it is narrow either way.

**How to confirm or refute.** `CropPanel p = new CropPanel(new BufferedImage(150,100,TYPE_INT_RGB), 296,
114); p.setSize(600,420); p.setZoomFraction(1.0); p.panBy(100000,0);` then print `p.sourceRect()` and
`p.sourceRect().intersection(new Rectangle(0,0,150,100)).isEmpty()`.

### C5 - `getScale`'s "safe against recursion" comment names a reason that is no longer true

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

`LocIconCropDialog.java:956-957`:

> `// Safe against recursion: startAtCover asks cropWindow and getMinScale, and neither of those asks this.`

`startAtCover`'s remembered-view branch (added by OB-125) calls `clampCenter()` at `:1000`, which calls
`getScale()`, which calls `startAtCover()`. The re-entry is harmless - but for a different reason than
the comment gives: `viewStarted = true` is set at `:978` before the branch, and `pendingView = null` at
`:989` before the clamp. As written, the comment tells the next person that adding a call there is free,
and it is not. Distinct from DOC-B3, DOC-C2 and RC-C6, none of which touch this line.

---

## D - not defects

### D1 - the caption drag handler builds its key from grid indices, not diagram coordinates, and that is safe

`LayoutGrid.java:1296-1298` builds `new TileKey(layout.getName(), x, y)` from the loop counters while
the same loop's own square key builds `new TileKey(layout.getName(), x + offsetX, y + offsetY)`
(`:865-867`), with `offsetX = layout.getMinx()`. It reads like the missing-offset bug it resembles.

It is not one. The branch needs `autonomyEditor`, which needs `master instanceof LayoutEditor`;
`LayoutEditor.render()` calls `layout.setEdit()` before `drawGrid()` (`LayoutEditor.java:5163-5173`),
`setEdit` calls `checkBounds` (`LayoutDiagram.java:657-661`), and `checkBounds` pins `minx`/`miny` to 0
whenever `edit` is set (`:192-201`). `setAutonomyMode` runs after `render()` and does not clear the
flag. So `offsetX == offsetY == 0` at every reachable call. `LayoutEditor.java:1776` already records the
invariant - *"Grid indexes equal diagram coordinates here because edit mode pins minx and miny to 0"* -
and the value of writing this down is that the invariant lives in a different file from the code that
depends on it.

### D2 - the legacy import does NOT lose the per-point operational settings

The obvious companion worry to `IPR-A1`. `CARRIED_SETTINGS` (`AutonomySession.java:493-494`) carries
`priority`, `speedMultiplier`, `excludedLocs` and `active` verbatim, counted in `result.settings` and
reported by the dialog. Of the 13 keys the sample's legacy points use (`active, excludedLocs, loc,
maxTrainLength, name, priority, reversing, s88, speedMultiplier, station, terminus, x, y`), `loc` goes
through the placement and home logic, `name`/`station`/`s88`/`x`/`y` are matched or derived,
`terminus`/`reversing` are translated at `:705-723`, and only `maxTrainLength` is mishandled.

Worth noting for whoever fixes A1: the javadoc above `CARRIED_SETTINGS` enumerates "the rest of a legacy
point" as *"name, station, s88, terminus, reversing, x, y"* - omitting both `loc` and `maxTrainLength`.
An enumeration that does not close is how a key gets forgotten.

### D3 - the run-wide settings and the timetable are not brought over by a legacy import, and that looks deliberate

`importLegacy` reads only `legacy.optJSONArray("points")`, so `minDelay`, `maxDelay`,
`defaultLocSpeed`, `preArrivalSpeedReduction`, `maxLatency`, `atomicRoutes`, `maxActiveTrains`,
`maxLocInactiveSeconds`, `activateRoutes`, `activateRouteIDs` and the whole `timetable` stay behind.
Adam's legacy file holds `minDelay 3`, `maxDelay 13`, `maxLocInactiveSeconds 120`, `activateRoutes true`
and 36 timetable entries; `configuration-Main.json` holds 1, 2, 0, false and one entry.

**Not filed as a defect.** The method's javadoc scopes itself to *"station names, station flags and
lengths"*, and `importLegacyGraph` creates a fresh configuration when none exists, so the globals are
that configuration's defaults by construction rather than by loss. Recorded because the changelog's
promise - *"An autonomy.json from an older version can be imported from the same menu"* - is
unqualified, and because the timetable is 36 entries of somebody's work. If Adam thinks a user would
expect those to come across, this becomes a C and the fix is a sentence in the import dialog, not code.

### D4 - a caption on a blank square still gets a REMAINDER cell

`LayoutGrid.java:1193-1243` moved the pill placement out of the `c != null && !c.isText()` branch
(OB-115) and left `gbc.gridheight = 0` inside it. A flat caption's preferred height is its line height
plus its top inset, which exceeds a tile at every size, so a caption left in a `gridheight = 1` cell
would stretch its row and throw the grid out of square. Checked: the `else` at `:1273-1278` sets
`gbc.gridheight = 0` as well, so every text cell is REMAINDER either way. Clean.

### D5 - `stationCaption` and the arrow rotation are null-safe and rotate once

`StationCaption.paintComponent` calls `getText().isEmpty()` with no null check (`:862`), which would
throw on `setText(null)`. Every producer is guarded: `LayoutGrid.stationCaption` returns
`LAYOUT_STATION_EMPTY` for a null name (`:494`), `own` is coerced to `""` at `:917`, and the four
`setText` sites in `updateStationLabels` (`TrainControlUI.java:4992, 5004, 5057, 5085`) all pass
non-null. `drawnText`'s arrow substitution (`StationCaption.java:233-244`) builds a new string in one
pass, so N->E->S->W cannot cascade. Clean.

### D6 - the real setup file is internally consistent on every cross-reference I could check

Read as data, `cs2_sample_layout/config/autonomy/setup.json`: every `stationSignals`, `barredArrivals`
and `tileLengths` key is in `stations`; every station has a `pointNames` entry and at least one caption;
no two stations share a name; both `portals` entries are symmetric; no station sits on an excluded page;
no `configuration-Main.json` point names a page the setup does not know. The one anomaly the audit found
is `IPR-C1`. Recorded because it is the evidence that the store's rekeying machinery - which has taken a
great deal of repair - is holding on the only real dataset there is.

### D7 - findings raised to me that did not survive my own re-derivation

Recorded so the calibration is visible rather than quietly dropped. Three of the parallel audits'
candidates are not in this document: an `AffineTransformOp` concern in `ImageUtil` I could not connect
to a caller; a `LocButtonTransferHandler` page-switch timer whose own reporter said it needs execution
to distinguish from correct AWT behaviour, and which I could not settle by reading either; and a
`NodeExpression.toTextRepresentation` multi-element `NodeGroup` that emits no separator - structurally
wrong, and unreachable, because no producer in `src/` builds a group of size greater than one. That last
one is exactly the shape the README warns about: real code, no caller, not a defect.

---

## What this pass missed, and what it did not look at

Written properly, because the calibration is worth more than the coverage claim.

- **I did not look at the four suggested angles.** Concurrency, resource lifetime, partial failure and
  atomic writes got no dedicated attention from me. I decided against them on coverage counts, not on
  evidence that they are clean, and a coverage count is a proxy for attention, not for correctness. If
  the other four passes also skipped them, they are unexamined this round.
- **I read the migration in one direction only.** I traced what `importLegacy` and
  `migrateStationLabels` do to a legacy file. I did NOT trace the configuration import/export bundle
  (`exportBundle`, the `CONFIGURATION` branch of `detectImportFormat`), which is the other half of "your
  data crossing a boundary" and is equally new.
- **`IPR-A1`'s second-order effects are asserted from reading, not measured.** I did not follow a tile
  length through `GraphReducer` into an `Edge` and out into the release accounting step by step. The
  claim about `SHORTEST_LENGTH` follows from `Layout.java:356`'s own comment and is not independently
  checked; the entry says so.
- **`IPR-C1`'s provenance is a reconstruction** from a `.bak` file and a measured coordinate shift. I
  could be wrong about the order of events without being wrong about the code.
- **`IPR-C4` I did not re-derive at all** and it is labelled as such. It is a pointer.
- **`IPR-B1`'s conditions loop** (`RouteEditorFrame.java:2408`) I did not settle; I only established the
  defect in the commands loop three lines above.
- **I did not open `Layout.java` (7,783 lines) except at four line ranges**, nor
  `AutonomyCompanionStore.java` (5,501) except around captions, tile lengths and reconciliation, nor
  `TrainControlUI.java` except at the caption, editor-opening and crop-caller sites. The autonomy model
  on its own terms, the route/train interaction guard, the CAN layer and the entire Central Station side
  were not read.
- **Nothing here was executed.** Every "how to confirm" is a real instruction and none has been carried
  out. `IPR-A1`'s first route needs no JVM at all and should be run before anything else, because it
  either settles the largest finding in this document in a minute or refutes it.
- **The `.form`/`.java` pairs I checked were only the ones the parallel audit named**
  (`LocomotiveAddressChange`, `LayoutEditorAddressPopup`), and I took that result on trust rather than
  re-reading the GEN blocks myself. No systematic sweep for a hand-written field that has drifted inside
  a `GEN-BEGIN:variables` block was done anywhere in this pass.
