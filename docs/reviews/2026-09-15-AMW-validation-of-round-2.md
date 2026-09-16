# Round 2 of the autonomy round, validated

**Status:** open 2026-09-15 - round 3 fixed B1, C1, C2, C3, C4, C5 and the determinism of item 8 with claims seen red first; B2 and B3 are fixed on the strength of the code and were NOT reproduced by a red claim - each section says so (claims and fix `a6ac7396`)

**Prefix:** AMW (checked free, with AMX and AMY: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, every declaration spelling in `docs/reviews/`, and a grep of `src/`, `test/` and `docs/`)

**Covers** one Opus validation of round 2 - `c02f7000` (claims and fixes), `5eea46bb` (documents and the re-measured figures) and `ad17ba44` (MT-448 to MT-450) - against the five review documents, the AMV validation and behaviour.md sections 5c, 6 and 7.  Read-only, nothing run.

**What it confirmed.**  The copy rule in both searches, including that the destination stays reachable, the origin is exempt, a blockless configuration is unaffected and `excludePaths` still yields alternatives (item 1); that the new lap sentence cannot be given for another reason and cannot be given where the terminus is the route's end (item 2); that AMV-B1 leaves MT-245 and the 2026-08-31 backing-in ruling intact for a single move (item 3); AMV-C5 (item 5); both of AMV-C7's controls, each with the mutation it fails against (item 6); the census constants and that the `ON_THE_WAY` subset check still bites (item 7); and MT-448, MT-449 and MT-450 against the code (item 9).

**On the pending tests.**  B2 touches MT-450 and bears on MT-440.  B3 touches MT-449 and MT-445.  C4 corrects MT-443 again.

---

## A - wrong behaviour on the layout

None.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| AMW-B1 | Fixed | the new reason was in one bundle of eight, and `core.testMessageBundles` asserts against that |
| AMW-B2 | Fixed, not reproduced | `HomeStaging.blockedSensors` freed more than the tail that went |
| AMW-B3 | Fixed, not reproduced | the mid-move turn rule launders through a berth in two moves |

### AMW-B1 - one bundle of eight

| | |
|---|---|
| **Disposition** | Fixed |

`autolayout.why.onlyALapLeadsThere` went into `messages.properties` only, on the reasoning that a `ResourceBundle` falls back to the base bundle.  It does - and this project asserts against exactly that: `core.testMessageBundles.testTranslationsMatchEnglishKeySet` requires every translation to define the English key set, because a key that falls back renders in English in a German window without anything failing.  Every sibling reason is in all eight.

**Found by the battery**, not by reading: the class went red in the round-2 battery while the round reported itself green.  **Fixed:** the sentence is in all seven translations, in the house style those bundles already use - transliteration rather than escapes (`fuehrt`, `zaden`, `alli`), with `’` where a language needs an apostrophe.

### AMW-B2 - the tail rule freed more than the tail that went

| | |
|---|---|
| **Disposition** | Fixed |

Round 2 freed a sensor once the plan had moved the train whose tail lay over it (AMH-B2).  Two over-claims in the one line:

- `edgesCoveredByStandingTrains` claims an edge WHOLE or not at all - which is why `placesCoveredByStandingTrains` exists to narrow it - so freeing both ends of a covered edge frees a point the tail never reached;
- `explained` is one boolean per sensor, so one departed tail freed a sensor a second, unmoved tail still holds.

Either way the planner becomes looser than the railway, which refuses an edge whose end reads occupied: the OB-073 direction this class exists to avoid, and the direction the round-2 javadoc itself forbids.  Sensors reported by several Points are ordinary on Adam's railway.

**Reproduced by reading, NOT by a red claim, and that is worth saying plainly.**  Seven fixtures were built for it and every one passed for a reason unrelated to the finding.  The reason they could not work is the rule's own shape: a sensor counts as explained whenever a point reporting it holds a train, so in each shape the held section was accounted for by its own occupant rather than by a tail.  The last version asked `blockedSensors` directly and still stayed green under a mutation that broke the rule outright, which is the point at which the claim was removed rather than left in the battery reporting the rule as held.  A note in `testHomeStaging` says so where the claim was.

**Fixed, round 3, on the strength of the code.**  A sensor is freed only when EVERY tail accounting for it has moved, and only for the end the tail actually reached, read from `placesCoveredAtStart` - the same narrowing the runtime applies.  The two over-claims are errors of construction rather than of measurement: `edgesCoveredByStandingTrains` claims an edge whole, so freeing both of its ends frees a point no tail reached, and one boolean per sensor cannot represent two causes.  MT-450 is unchanged; MT-440's expectation is noted.

**What is therefore still unproven:** that this fix changes any plan on a real railway.  It is the refusing direction, so it cannot make the planner looser than the runtime, and that is the property that mattered.

### AMW-B3 - the berth exemption launders a turn in two moves

| | |
|---|---|
| **Disposition** | Fixed |

AMV-B1 allows a route to turn a train that cannot reverse where the move ends at its home, at a berth, or at a square that turns it.  A berth that is neither a terminus nor a reversing point does not set `turnedByThePlan` on the next expansion, so the plan could turn the train into a berth and then run it out of the berth to an ordinary station, arriving the wrong way round - which is what AMV-B1 was opened for.

**And the oracle could not see it**, because it was written from the fix rather than from the ruling: `turnedAndNotSentHome` carried `end.isAutoDestination()` exactly as the fix does.  That is the second time in this round a test was written to match the code it was checking; the AMV document says so of its own round.  Rewriting it to follow the ruling - a train the plan has turned owes its next move to its home - is what made the round-two claims meaningful again, and it caught a stale control in one of them immediately.

**Reproduced by reading, NOT by a red claim.**  Five fixtures were built for it and every one was legitimately compliant; a probe printed the plan and showed why - in each of them the train's last move ended at its OWN home, which the ruling permits, so the laundering never occurred.  The shape that would force it needs the home to be unreachable from the berth's second exit, which is a sixth railway, and the finding was not worth a sixth.  `core.testHomeStaging.testATurnIsNotLaunderedThroughABerth` is kept as the nearest reachable shape - it exercises a turn into a berth followed by a further move - and it passes.

**Fixed, round 3, on the strength of the code.**  `turnedByThePlan` asked whether the train is STANDING on a square that turns trains, and a plain berth is not one, so the rule stopped following the train exactly where the validation said it did.  It now asks the route that brought the train here: turned and not home means the next move owes itself to the home.  MT-449.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| AMW-C1 | Fixed | behaviour.md section 7 over-claimed the lap sentence |
| AMW-C2 | Fixed | the census nominated a guard that cannot hold the pass-through rule |
| AMW-C3 | Fixed | the figures were cited to section 5b; they are in 5c |
| AMW-C4 | Fixed | MT-443's corrected expectation cannot be carried out |
| AMW-C5 | Fixed | the homeless-train control had no fixture precondition |

### AMW-C1 - the precedence

Where a route passes both a terminus and a copy the terminus sentence wins, and only a code comment said so.  **Fixed** in section 7.

### AMW-C2 - what still holds the pass-through rule

With the floor at 0 and `ON_THE_WAY` empty, no assertion in the census fails if the 2026-09-09 rule is removed - and the comment nominated `refusedAtATurn`, which is counted by the class's own replica loop and never through the production rule.  **Fixed:** the comment and behaviour.md name `core.testATrainIsJudgedOnlyWhereItStops` and `core.testNonReversibleTrains`, which go through the real door, and PTR-C2's vacuous line is replaced by one that bites.

### AMW-C3 - 5b or 5c

The census text and every one of these figures live in section 5c; 5b is *What "unmeasured" means*.  **Fixed** in behaviour.md, the AMR document and the census's own comments.

### AMW-C4 - MT-443 again

The replacement expectation asked the operator to send a train TO the demoted square - which is not a destination once demoted, so it is not offered and has no Why Not Moving? line at all.  AMV-C4 found an expectation that could not fail; its replacement could not be performed.  **Fixed:** the step keeps what is observable - the restriction gone when the square is promoted again - and says why the rest is not.

### AMW-C5 - the control's precondition

`testATrainWithNoHomeThatCannotReverseIsNeverTurnedAside` is satisfied by an empty plan for any reason.  **Fixed:** it asserts first, with a reversible train, that this fixture does step a train aside onto the terminus.

---

## D - looked wrong and is not

| id | what |
|---|---|
| AMW-D1 | The greedy pass generates moves without the turn rules; sound, because every greedy move ends at the train's own home and `atHome` exempts it |
| AMW-D2 | `bfs` is now stricter than `isPathClear`, which still exempts an intermediate copy of the train's own square as occupied by itself.  Deliberate, and the refusing direction |

---

## What the round cost Return Home, and Adam's second ruling on the copy rule

Round 2 applied his copy rule in both searches - `Layout.bfs` and `HomeStaging.firstClearRoute` - on the reasoning that the two must agree or the planner offers what the railway refuses.  The reasoning was wrong in one direction: the RUNTIME agrees with the looser answer, because `Layout.isPathClear` exempts an intermediate copy of the train's own square as occupied by that train (AMW-D2).  What refuses a lap is SELECTION, not the railway.

**Measured, after three failures in a row.**  `core.testTrainsComeHomeToTheirPlatforms` - five trains sent out from ordinary platforms and brought home - was green at round 1 (`64169b0b` records it), flickered during round 2, and then failed three consecutive runs from three different scatters.  Its arrangement comes from letting autonomy run, and autonomy's route choice shuffles by design, so no two runs start alike; that made it tempting to call chance.  A bisect settled it: with the planner's copy clause disabled the class passed twice, with it enabled it failed three times.

**Adam, shown that and reminded that Return Home is manual operation (his ruling of 2026-09-04):** *"Menu and autonomy only."*

So the planner keeps the looser search, and `auditAgainstRuntime` gains its fourth correct divergence - a destination only a lap reaches, where this oracle (`getPossiblePaths`) and the planner are meant to differ.  `Layout.anyTrackRouteBetween` is the track question that tells such a destination from a planner defect, and it is the same question `firstClearOrWhyNot` already asks to choose between its three sentences.  behaviour.md section 7, the AMR document and MT-448 say so.

**A measurement that did not survive contact, and is recorded so nobody repeats it.**  A probe written to price this ruling reported 676 of 1850 station pairs as "track exists but nothing offered", which is not the copy rule's cost at all - it compared the menu's filtered answer against a bare search, so it counted occupancy, reversing squares and berth rules too.  The figures that stand are the ones measured properly: 22 lapping routes on the frozen railway, 14 of them the one route the menu offered for a destination, and 14 of 572 reachable pairs losing their last route - all from `BottomMainPost`.

---

## The Return Home claim now comes in two forms (Adam, 2026-09-15)

Item 8's fix made a plan a function of the track for a GIVEN arrangement.  `core.testTrainsComeHomeToTheirPlatforms` does not have a given arrangement: it lets autonomy rearrange the railway for twenty seconds and then sends everybody home, so it starts somewhere different on every run - it failed once and passed once on identical code during this round, and a red from it cannot be told from chance without re-running.

Asked what it should do, Adam: *"make a pinned version and keep the current version, aiming for both to be true.  normal autonomy runs should always have a solution."*

So `core.testTrainsComeHomeFromAPinnedArrangement` displaces the same five trains to an arrangement derived from his frozen diagram rather than from a run, and makes the same claim - everybody back on the arrival they left.  A red there is always a regression; a red from the live one is an arrangement autonomy can reach and Return Home cannot solve, which by his ruling is also a defect, and needs the arrangement printed before anyone can act on it.  build.xml carries both, with the pair's reasoning beside them.

**What the pinned arrangement had to learn**, in two goes.  The first version shifted all five trains onto the next platform along - a five-way cycle with no spare square, where every home is held by a train that must move first.  Return Home answered NO_PLAN_FOUND, which may well be correct: shunting needs somewhere to shunt to, and pinning that would have asserted something the railway cannot do.  The second displaced four up the list and left the fifth at home, which puts the fourth train onto the fifth's platform - and `moveLocomotive` DISPLACES whoever is standing there, so the fifth was pushed off the railway and the claim failed with it "nowhere".  The arrangement now walks the trains DOWN the list, highest first, each onto the square its own occupant has just left, with an assertion in the loop that no target is occupied; the top platform ends free.

---

## The flake, and what was done about it (item 8)

`core.testTrainsComeHomeToTheirPlatforms` answered NO_PLAN_FOUND once and READY once on the same code during round 2, and round 2 recorded AMH-C1 as *"open, and seen"*.  The validation rejected that disposition: a capability claim that answers differently on each press spends the battery's authority, because the next red cannot be told from a regression.  Fixed in the SEARCH rather than in the test - `HomeStaging` orders the neighbours it expands by name for its own route search, so a plan is a function of the railway rather than of a shuffle, and the claim it makes is the same one.  `Layout.getNeighbors` still shuffles for everything else: that shuffle is what spreads autonomy's choices over a railway, and it is not what a planner wants.

---

## What the passes missed

- **A test written from the fix cannot see past the fix.**  Twice in one round: the AMV document says it of round 1's oracle, and AMW-B3 is the same fault in round 2's, with the same sentence copied into the oracle and the code.
- **A guard the code enforces is not the guard the comment nominates.**  AMW-C2's floor was defended by a number that cannot move when the rule is removed.
