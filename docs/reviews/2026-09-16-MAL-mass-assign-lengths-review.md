# Mass Assign Lengths and the Unmeasured Track display, reviewed and validated

**Status:** open 2026-09-16 - nothing fixed yet; B1 waits on Adam's ruling on how far "every square a rule reads" reaches, and the rest wait on that

**Prefix:** MAL (checked free: no MAL- id in `docs/manual-tests/findings.tsv`, and no MAL-[A-D]n spelling in `docs/reviews/`, `src/` or `test/`)

**Covers** commit `92cff9e8` (FR-089) - `AutonomySession`'s stretch model, the editor's walk and display choice, `TileAnnotation`'s mark, the tests, behaviour.md, MT-454, MT-455 and the eight bundles.  Adam asked for an Opus review fanned out and validated.  Three Opus reviewers read it in parallel - the model against the rules, the editor and paint path, the tests, docs and translations - and an Opus validator was asked to refute every finding.  **B1, B2 and B3 were then confirmed by running**, on a probe that built the review's own fixture into a real `Layout` (not committed).

**On the pending tests.**  MT-454 and MT-455 both describe the narrow definition B1 is about, and MT-455's step 3 passes through the menu but would fail through Control+E (B3).  Neither should be run until B1 is ruled and fixed.

---

## A - wrong behaviour on the layout

None.  Every defect below errs towards refusing a train or asking for too much, never towards two trains on one piece of metal - which is why B1 is not an A.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| MAL-B1 | Open - needs Adam's ruling | `AutonomySession.stretchesALengthRuleReads` - stops at the last switch; the FR-087 allowance and the tail and berth walks read past it |
| MAL-B2 | Open | `AutonomySession.assignStretchLength` - the unit over goes the admitting way for the resting square |
| MAL-B3 | Open | `AutonomyEditorPanel` - Control+E leaves the Unmeasured Track highlight stale |
| MAL-B4 | Open | `AutonomyEditorPanel.askForWholeLength` - Escape writes the typed number instead of stopping |

### MAL-B1 - "every square a rule reads" is only the room rule's squares

| | |
|---|---|
| **Disposition** | Open - confirmed by running; how far it should reach is Adam's call |

The walk stops at the last switch on a leg, and continues back through the sensor only where a leg has no switch.  The commit, the javadoc, the test javadoc, behaviour.md, FR-089, MT-455 and the English tooltip all say the rules stop there.  **Only the room rule does.**  All three reviewers' lines of reading converged on it, and the validator confirmed it:

- **`Layout.measuredRouteIn`** (the FR-087 allowance at a station autonomy may choose) adds each leg's whole `getLength()` - which the reducer builds as the path's tiles plus the end square, the switch tile and everything before it included - and goes on leg by leg, stopping only at a reversal, a wholly unmeasured leg, or the start of the route.  It never asks `crossesASwitch`.  It also sets the number the refusal quotes, and the staging planner asks it.
- **`Layout.walkOneTail`** spends and claims every place on each leg, switch tiles included, and a driven train follows the road it came along through junctions (MT-335), so a tail can read several legs back.
- **`Layout.whyABerthCannotHoldIt`** (parking berths) spends over every place on the approach, the switch and the track before it included, and never spends the berth's own square - so any train with (room after the switch minus the berth square) < length <= room passes the room rule and then reaches past the switch.
- Turn-round squares are judged by the room rule only, so the narrow set is right there, tails apart.

**Confirmed by running.**  The review's berth-behind-a-switch fixture as a station autonomy MAY choose, its stretch given 7 through `assignStretchLength` exactly as Mass Assign does:

| 2,1 (before the switch) | still highlighted | edge length | room | `measuredRouteIn` | nine-unit train held by the approach |
|---|---|---|---|---|---|
| unmeasured | nothing | 7 | 7 | 7 | **no** |
| 5 | nothing | 12 | 7 | 12 | **yes** |

So a person can measure everything the display highlights, be told there is nothing left, and still have a station refuse a train his FR-087 ruling says it takes - and the square that changes the answer was never asked for.

**Why it needs a ruling rather than a fix.**  `measuredRouteIn` is bounded only by reversals, unmeasured legs and where the route starts, so on a railway with loops "every square a rule reads" is close to every leg a route into a station can come along.  **Measured on a sandbox copy of Adam's layout** (probes, not committed):

| reading | legs or pieces | squares |
|---|---|---|
| as built - past the last switch, on through sensors | 46 stretches | 183 |
| the whole leg into each resting square, nothing further | 54 legs | 316 |
| FR-087's reach - leg by leg back until a turn-round square | 75 legs | 391 |
| every leg on the railway | 75 legs | 391 |

**FR-087's reach IS every leg on his railway.**  So the question is only how to cut 75 legs into things to measure.  64 of them cross a switch, 51 cross two or more.  Cut at every switch, they make 121 pieces; cut only at each leg's first and last switch - what each direction's room rule needs - 120.  The cut matters because an even share must never move units across a switch: the room rule counts only the track past the last one.

The tests encode the narrow definition (they require 2,1 and 3,1 NOT to be highlighted), so none would have caught this.

### MAL-B2 - the unit over goes the admitting way for the resting square

| | |
|---|---|
| **Disposition** | Open - confirmed by running |

`assignStretchLength` gives any remainder to the squares farthest from where the train rests, on the reasoning that a smaller share near the train makes a tail reach further.  That holds among squares the walks SPEND, but the resting square's own measurement is an allowance they never spend (`spendableAllowance`; `whyABerthCannotHoldIt`'s `onTheAllowance`).  So in a stretch that contains it, a unit given away from it is a unit more of spendable rail - the admitting direction.  The room rule and `measuredRouteIn` do not care how the total is split; only the tail and berth walks do.

**Confirmed by running**, the same fixture as a parking berth, seven units, a four-unit train:

| berth 5,1 | 4,1 | the tail claims |
|---|---|---|
| 3 | 4 (what the code writes) | 5,1 and 4,1 |
| 4 | 3 | 5,1, 4,1, **3,1 and 2,1** - the switch, shared with the 1,1 - 3,0 road |

`testTheWholeLengthIsSharedOverTheUnmeasuredSquares` locks in the admitting split, and its javadoc argues it is the refusing one.  In a stretch merged from both directions the far end's station square is last in the list, so it gets the unit over - admitting for that station too.  At most one unit per square.

### MAL-B3 - Control+E leaves the highlight stale

| | |
|---|---|
| **Disposition** | Open - confirmed by running |

The squares the display highlights are cached and forgotten only in the panel's `refresh()`.  Menu actions reach it through `item()`; Control+E does not - `LayoutEditor` binds it to `promptLengthFor` -> `applyLength` -> `setupChanged()`, which redraws the grid from the cache.  The length number reads the store directly, so the same square shows its new number and its old amber.

**Confirmed by running:** with the display on, 4,1 marked; after `setTileLength` and `setupChanged` - Control+E's own path - still marked; after `refresh()`, not.  Found by the editor reviewer; no test checks the highlight after an edit.

### MAL-B4 - Escape writes the number instead of stopping

| | |
|---|---|
| **Disposition** | Open - confirmed against the JDK source, not run |

`askForWholeLength` treats `null` or Cancel as Stop and the Skip button as Skip.  In JDK 8 `BasicOptionPaneUI` binds Escape to `setValue(Integer.valueOf(CLOSED_OPTION))`, and FlatLaf's option pane inherits it; only the window's close button sets `null`.  So Escape falls through to reading the field: a typed number is written and the walk goes on, and an empty field skips.  **The sibling it was copied from has the same flaw:** `askForName`, so Escape in Name Everything names the square with whatever was typed - older than this commit, and the same fix.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| MAL-C1 | Open | barred sides and stations out of service are asked for |
| MAL-C2 | Open | a resting square sits in every stretch that arrives at it; the comment saying otherwise is false |
| MAL-C3 | Open | the prompt names a sensor where no train rests; the too-large catch is dead |
| MAL-C4 | Open | test gaps - none of the nine claims would catch B1 to B4 |
| MAL-C5 | Open | behaviour.md: wrong subsection, and it repeats B1 |
| MAL-C6 | Open | MT-454 and MT-455 steps that do not match the app |
| MAL-C7 | Open | translations: terms that drift from their bundles, and two meanings |
| MAL-C8 | Open | the greyed item's tooltip on a page left out of autonomy |

### MAL-C1 - barred sides and out-of-service stations

The walk seeds from every leg ending at a tile that is a station or a turn-round square, by tile rather than by the copy the builder emits.  A side where arriving is barred emits no arrival copy, so no length rule judges an arrival there; and a station switched out of service is not a destination.  Both are asked for.  Over-asking only - the same track can be another square's approach, and a bar can be lifted.

### MAL-C2 - squares shared between stretches

Every stretch starts with the square it arrives at, so a through station, or any square with two approaches, is in two stretches.  The editor's comment "No stretch shares a square with another" is false (the re-check after it copes).  Two consequences: the walk's "Stretch i of N" jumps when an earlier answer filled a later stretch whole, and the station square's own length - which the tail and berth walks read as its size allowance - is whatever share the approach answered first gave it.

### MAL-C3 - prompt details

For a leg reached through a sensor, the prompt's "leading to" names that sensor, where no train need rest.  The `NumberFormatException` catch in `askForWholeLength` is dead: `digitsOnly` caps the field at three digits, so a whole stretch cannot be typed above 999 (harmless in Adam's units).  `firstGapHere` can be null only after C2's sharing has filled this page's gaps, and then nothing is scrolled to.

### MAL-C4 - test gaps

None of the nine claims would have caught B1 to B4: they encode B1's narrow definition, assert B2's split, never write a length after reading the highlight (B3), and never touch the dialog (B4).  Also unpinned: a stretch spanning pages (`testTheWalkAsksOnlyAboutThisPage` compares against a page with nothing on it, so filtering by `restsAt`'s page would pass), a merged stretch's order and remainder (compared as a set), `hashCode`, paint, and the menu's greying.  The validator refuted one claimed gap: the reverse-leg exclusion IS pinned, indirectly, by the 6 in `testAMeasuredSquareIsKeptAndATooShortLengthIsRefused`.

### MAL-C5 - behaviour.md

The new paragraph landed at the end of "The main window's key map reaches the whole window" rather than in 5a or 5b, and its "back to the nearest switch" contradicts 5a's own FR-087 text, which already says the route in is counted leg by leg for as long as each leg is measured.

### MAL-C6 - MT-454 and MT-455

MT-455 step 3 names **Set Length...**; the item is **Segment Length...**.  "Right-click any square" - an empty or text square opens a menu with no Bulk Tools.  MT-454 step 5 needs a partly measured stretch and nothing in the steps leads there.  "The end away from the station" is ambiguous for a run between two stations.  MT-455's "neither is track on the far side of it" is false wherever the far side is another station's approach - and, with B1, is the definition in question.  The Unmeasured Track tooltip and FR-089 make B1's promise too.

### MAL-C7 - translations

Italian says "deviatoio" where 34 keys say "scambio"; Danish says "parkeringsplads" where the bundle's berth sentence says "opstillingsspor".  The Spanish "que lee una regla de longitud" reads naturally as the square reading the rule (German and Dutch are ambiguous the same way, the subject reading usual).  The French and Italian Mass Assign tooltips say the stretches have no length yet, where partly measured ones are included.  "1 stretches" and the Polish counts follow the bundles' existing convention of no `choice` formats.  The validator refuted the German and Dutch berth terms: both already appear in neighbouring keys.

### MAL-C8 - the tooltip on a page left out

On a page left out of autonomy the count is zero because the graph has no such page, and the greyed item says every stretch on the page already has a length - vacuously true, and misleading.  Name Everything greys on the same question with a sentence of its own.

---

## D - looked wrong and is not

| id | what |
|---|---|
| MAL-D1 | "Rebuilt once at the end" - the session rebuilds on every write, as `setPointName` does for Name Everything; the running-layout rebuild the comment means does happen once |
| MAL-D2 | A tile twice in one path (a bridge crossed twice) is counted twice - but so do the reducer's own length, room and places, so the totals agree with the rules; only a remainder unit can be lost to the overwrite |
| MAL-D3 | The amber wash is close in hue to the orange selection outline, but a fill and a 2px outline stay distinguishable; the javadoc's "none of those" overstates it |
| MAL-D4 | An exception part-way through the walk skips `setupChanged`, but the writes are in the store and the running layout catches up on the next change or when the editor closes - the same shape as `nameEverything` |
| MAL-D5 | Pages sort as strings, so "10" before "2" - cosmetic |
| MAL-D6 | A reversing square met part-way back is not a stop, but every such square seeds its own walk, so the union is the same |
| MAL-D7 | No leak onto the main diagram: only the editor's `annotationFor` calls `needsALength`, and the overlay paint path does not need it |

---

## What the passes missed

- **A rule summarised is a rule re-derived wrongly.**  The design read `measuredRoomAtTheEndOf` and the tail walk, and wrote "and the FR-087 allowance stops at the same square" from memory of behaviour.md 5a's MFR-C6 note - which is about reversals, not switches.  The allowance's own loop, eleven lines long, says otherwise.
- **An argument about direction needs the allowance in it.**  B2's reasoning was right for every square the walks spend and wrong for the one they do not, and the test was written to the reasoning.
- **The sibling was copied with its defect.**  B4 came in with `askForName`'s dialog, Escape and all.
