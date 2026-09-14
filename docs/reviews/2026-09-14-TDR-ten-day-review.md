# Ten days of commits, reviewed after the MT sweep of 2026-09-13

**Status:** closed 2026-09-14 - the three fix and validation rounds, then TDR-B5 and TDR-C9 to C12 on Adam's instruction

**Prefix:** TDR (checked free: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`)

**Covers** the commits of 2026-09-04 to 2026-09-14 (`git log --since=2026-09-04`, 283 commits), weighted
towards the two newest code commits: `d4f09f5d` (MT sweep fixes) and `f40920ee` (MT sweep, second pass).
One reviewer read them; an independent validator then tried to refute every finding by reading the code
paths, and refuted none. Each fix below was seen red first - a claim written before the change, run, and
failing for the reason stated - unless it says otherwise.

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| (none) | | |

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| TDR-B1 | fixed `785d913a` | `Layout.standOnTheCopyItDidNotTurnOn` - the route was copied after the move had cleared it |
| TDR-B2 | fixed `785d913a` | `AutonomySession.moveOntoFacingCopy` - carried the arrival side across a re-stand, not the route |
| TDR-B3 | fixed `785d913a` | `HomeStaging.whyNotHome` - a barred copy hid "too short" and said "no route" |
| TDR-B4 | fixed `785d913a`, completed by TDR-B6 | `AutonomyCompanionStore.importBundle` - a page with an id in common was merged, then reported as left out |
| TDR-B5 | Fixed | `TrainControlUI.repaintLoc` - a repaint asked for while the last is still rendering was dropped |
| TDR-A1 | fixed (round 2) | `AutonomyCompanionStore.importBundle` - the exporter's page record re-labelled this layout's own ids |
| TDR-B7 | fixed (round 2) | `AutonomyCompanionStore.withoutPages` - filtered whole entries, and took a station named "2" for a page |

### TDR-B1 - a train that declines the turn at a may-turn square loses the route its tail follows

`standOnTheCopyItDidNotTurnOn` read the tail side before moving the train, on purpose, and read the
route after: `sibling.setLocomotive(loc)` sweeps the train off the other copies through
`Layout.clearLocomotiveExcept`, and that change of occupant clears `arrived.arrivedAlong` before
`sibling.setArrivedAlong(arrived.getArrivedAlong())` read it.

**Scenario:** a train driven A -> J -> S where S is a square trains may turn at, and the operator keeps
direction. It was re-stood on the plain copy with no route, so the next tail walk stopped at the junction
J and the road A -> J was not claimed - the MT-335 symptom, at every may-turn destination where the turn
is declined.

**Validated:** confirmed - the only other `setArrivedAlong` call is the arrival itself.
**Fixed:** the route is read beside the side, before the move. `core.testTheArrivalHonoursTheAnswer.testTheRouteComesWithIt`.

### TDR-B2 - the facing re-stand dropped the route as well

`AutonomySession.moveOntoFacingCopy` took `arrivedFrom` before clearing the train's copies and wrote it
onto the copy it moves the train to; `arrivedAlong` was cleared by the same `setLocomotive(null)` and never
carried. Its callers are the idle reconciliation after a train turned, `flipFacing` when a direction
command turns a train, and the facing menu.

**Validated:** confirmed, reachable through all three. The validator also found the guard
`onto.getArrivedFrom() == null` always true - the move has just cleared it - so the comment describing a
"newer value" it protected described a case that cannot happen.
**Fixed:** the route is saved and put back with the side, and the dead guard is gone.
`regression.testTheTurnAtTheDestinationReachesTheDiagram` asserts the route on the re-stood copy of a train
that really drove there.

### TDR-B3 - a home too short for the train was explained as "no route" when the square had a barred side

`whyNotHome` asked whether ANY copy of the home was long enough, and `Point.validateTrainLength` answers
true for a copy that is not a destination. The builder emits such a copy for a platform whose arrival from
one side is barred, so the length sentence was suppressed and the operator was told to check connections
and one-way runs - the wrong remedy for the case FR-078 was filed about.

**Validated:** confirmed; only the length check leaked, since exclusions and active are written on every copy.
**Fixed:** the home rules are judged over the copies a train can stop at; a home with none says only that
it is not a station. `core.testReturnHomeSaysWhy.testAHomeWithABarredCopyStillSaysItIsTooShort`.

### TDR-B4 - an import whose page id matched a local page under another name was merged, then called left out

The page record merges by id, and a stored key resolves by id when its recorded name is unknown - so the
exporter's "Yard", id 1, was read onto this layout's "Main", id 1. The pruning added on 2026-09-13 ran
AFTER that read: it reported Yard as left out while its settings stood on Main's squares, and
`forgetHeldPages` dropped every held entry mentioning id 1.

**Validated:** confirmed with a correction - worse than reported. The merge onto the wrong page is older
than the window; the false report is new, and so is the data loss: `forgetHeldPages` matches keys and
values, so held settings of this layout's own unloaded pages that point at Main could go with it.
**Fixed:** which of the file's pages this layout has is decided BY NAME before anything is merged, and
everything naming the others is taken out of the incoming fields; the post-read pruning and its
`forgetHeldPages` call are gone. `core.testAnImportKeepsOnlyThisLayoutsPages` gains the collision and a
control that a page this layout has lands on it under any id.

**Fix validation, round 1: incomplete.** Only the drop decision was made by name; where the remaining
entries LANDED was still by id, because their page record was still merged "theirs wins per id". That is
TDR-A1, below, and the claim "decided BY NAME" in the first fix's comment overstated it.

### TDR-B5 - switching locomotives quickly can leave the previous one's function buttons on screen

| | |
|---|---|
| **Disposition** | Fixed |

Found while running the battery, not by the reviewer. `repaintLoc` returns without doing anything while an
earlier render is still in flight, and a render goes to a worker and back through `invokeLater` - so a
forced repaint for a DIFFERENT locomotive, asked for inside that window, is dropped, and the buttons stay
as the last locomotive left them. `regression.testTheFunctionButtonsFollowTheConsist` went red on exactly
this after this round made the window's work slightly slower, reading f6 greyed for a consist that can
drive it.

**Not fixed in the product.** The test now waits for the window's own render before and after; the
render path itself is the main window's busiest code and is left for Adam to prioritise. The shape of a
repair is the one `askForReturnHomeTriage` already uses: coalesce, and let the last ask always land.


**Fixed on Adam's instruction, 2026-09-14** ("Fix B5"). The render is an explicit in-flight flag rather than
a check of the last future: a request made while a render is in flight is kept - forced if any was, for every
locomotive if any asked for every one - and run when the painting finishes, which clears the flag in a
`finally`. Direction changes are still followed once per request, not again when the deferred render runs.
The window in which a request was dropped depends on the scheduler, so the claim holds the renderer there
through a hook it calls after posting (`TrainControlUI.afterARenderIsPosted`, null in the program):
`regression.testTheFunctionButtonsFollowTheConsist.testTheLastLocomotiveAskedForIsTheOneDrawn`, seen red
against the old guard with only the hook added.

### TDR-A1 - an import from a layout numbered differently re-labels this layout's own pages

| | |
|---|---|
| **Disposition** | Fixed |

Found by the first fix validation; older than the window (`09704f9c`, the "theirs wins per id" merge).
An export keys every square by the exporter's page ids, and the merge adopted their page record over mine
for every id both knew. Three things followed, each silent:

- **Renumbered, both pages loaded.** Mine Main = 2, Yard = 3; theirs Yard = 2. The merged record said
  2 and 3 were both Yard, so every setting on MY Main was read back as Yard's and saved that way.
  `testAutonomyDiagramStore` had this exact fixture and asserted only that a conflict was reported.
- **A page still downloading.** Mine Main = 1 loaded and Away = 7 known but absent; theirs Main = 7. Their
  record overwrote "7 is Away", Away's held settings resolved to Main and were read onto it, and Away
  stopped being reported as not loaded - so nothing protected it any more.
- **The reverse.** Theirs Away = 1 against my Main = 1 put their Away's settings on Main.

**Fixed:** each of their ids is translated to the id this layout uses for the same page NAME - a loaded
page's, or the one this setup recorded for a page it knows and cannot load - before anything merges, and
their page record is never adopted. A name this layout does not have translates to nothing and is
reported as left out. The UR-10 claim now asserts where the settings land; two new claims in
`core.testAnImportKeepsOnlyThisLayoutsPages` cover the unloaded page both ways. All three seen red.

### TDR-B7 - the foreign-page filter was too coarse

| | |
|---|---|
| **Disposition** | Fixed |

Found by the first fix validation. `withoutPages` dropped an entry when its key OR its value mentioned a
foreign id, through `mentionsAny` - which takes any string without a colon for a page id. So a hold-back list
lost its local members along with one foreign square, and a station named "2" was dropped whenever a foreign
page had id 2. Import only, so nothing local was lost; the cost was gaps an import should have filled.

**Fixed:** the incoming fields are rewritten by each field's declared shape in `HELD_FIELDS` - keys and
square values translated, list members one at a time, page lists element by element - so only the foreign
pieces go. `testOnlyTheForeignPiecesAreLeftOut`, seen red.

---

## C - documentation, tests and minor

| id | status | where |
|---|---|---|
| TDR-C1 | fixed `785d913a` | `autosetup.ui.infoNoOtherPointsToBlockWith` - said "points" where the list is stations |
| TDR-C2 | fixed `785d913a` (in part) | `core.testReturnHomeSaysWhy` - most FR-078 sentences had no claim |
| TDR-C3 | fixed `785d913a` | `ui.testTheEditorNamesItsShortcuts` - raw NUL characters made git treat it as binary |
| TDR-C4 | fixed `785d913a` | three javadocs orphaned by members inserted beneath them |
| TDR-C5 | fixed `6c36d262`, completed in round 2 | path refusals printed the builder's direction copies, not squares |
| TDR-C6 | fixed (round 3) | `AutonomyCompanionStore.importBundle` - with no page loaded, the exporter's page record was still adopted |
| TDR-C7 | fixed (round 3) | `AutonomyCompanionStore.importBundle` - a field this version does not model was saved in the exporter's ids |
| TDR-C8 | fixed (round 3) | `HomeStaging` - Return Home's reasons named the builder's copies |
| TDR-C9 | Fixed | `HomeStaging.Move.toString`, `logStagingAudit` - the plan's log lines still name copies |
| TDR-C10 | Fixed | `AutonomyCompanionStore.importBundle` and neighbours - comments describing the merge before round 2 |
| TDR-C11 | Fixed | `Layout.placeNameOf` - a heading typed into a name by hand makes two squares read alike |
| TDR-C12 | Fixed | `core.testReturnHomeSaysWhy.testTheReasonsNameSquaresNotCopies` - pins one of eleven sites |

### TDR-C1 - the "nothing to choose" message named points, and the list is stations

**Fixed** in eight languages, and the panel's javadoc with it. Not seen red: text only.

### TDR-C2 - FR-078 was claimed as ten sentences and tested as two

**Validated with a correction:** ten keys, eight untested. **Fixed in part:** claims added for a home out
of service, a home excluding the train, a start out of service, two homes on one detection section, and
the occupied home a search that ran out of room names. Still without a claim: a start that is not a
station, a home that is not a station, and "no arrangement found". These five passed when written - they
pin sentences that already worked; the gap was coverage, not behaviour.

### TDR-C3 - a test source file carried literal NUL characters

**Fixed:** written as `\u0000`; the committed blob has no NUL bytes.

### TDR-C4 - three javadocs sat on the wrong member

Found by the validator. `canGetHome`, `disableReturnHome` and `armBlockerPick` each had a new member
inserted between them and their javadoc, so each described its new neighbour. `regression.testJavadocsAreAttached`
went red in the battery on it. **Fixed:** each javadoc moved back above its own declaration.

### TDR-C5 - the path refusals named the builder's copies

Seen on Adam's railway while reproducing MT-335: *"Disallowed because 75 407 DB is standing across Tunnel
(southbound) -> BottomMainAPre (eastbound)"*. Seven refusals `isPathClear` logs printed `getName()` of an
edge or point; the headings are how the builder tells a square's directions apart, and his ruling is that
the copy machinery is masked from the user. **Fixed:** they name squares through `placeNameOf`, and an edge
as its two squares. `core.testARefusalNamesTheSquare`, with a control that a name like "Yard (old)" keeps
its parenthesis.

**Fix validation, round 1: incomplete.** Six more refusals printed copy names - the opposite-direction edge,
the manual send's held-back explanation, and the four length refusals - and an edge within one square read
"X -> X". **Completed in round 2:** all six name squares, an edge within one square is named once, and a
length refusal has a claim of its own. No code parses these messages; the cost, accepted with the ruling, is
that the log no longer says which copy refused.

---

### TDR-C6 - an import made while no page is loaded still adopted the exporter's page record

| | |
|---|---|
| **Disposition** | Fixed |

Found by the second fix validation. Round 2 translated ids only when the store had an INDEX, and a OneDrive
start with every page still a placeholder has none - `AutonomySession.open` skips pages that will not read.
The store still knows its pages then, by the record it was written with, but the import fell back to
"theirs wins per id": their "7 is Main" was saved over "7 is Away", and on the next ordinary open Away's
settings were read onto Main. **Fixed:** the translation runs whenever this setup knows its pages by any
record, loaded or written; only a store that knows no page by any number merges the old way.
`testAnImportWithNoPageLoadedStillKeepsEachPagesSettings`, seen red.

### TDR-C7 - a field this version does not model was saved in the exporter's numbering

| | |
|---|---|
| **Disposition** | Fixed |

Found by the second fix validation. The translation is by each field's declared shape, and a field from a
newer version has none - so it was merged as it came, kept verbatim, and written into this layout's file
keyed by the exporter's page ids. **Fixed:** while translating, a field this version does not model is left
out; fields it does model that name no square merge as before. `testAnUnmodelledFieldIsNotSavedInTheirNumbering`,
seen red.

### TDR-C8 - Return Home's reasons named the builder's copies

| | |
|---|---|
| **Disposition** | Fixed |

Found by the second fix validation. FR-078's sentences named the square a train stands on and its home by
`getName()`, and both are copies - so the log read "its home X (eastbound, reverse) is switched out of
service". Adam's ruling that the copy machinery is masked from the user covers the log. **Fixed:** every
square in those sentences goes through `Layout.placeNameOf`, now visible to the package.
`testTheReasonsNameSquaresNotCopies`, seen red. The two refusal shapes the C5 claims had not reached - track
held the other way, and track within one square - have claims of their own now; both passed when written,
round 2 having already fixed them.

### TDR-C9 - Return Home's plan lines still name the builder's copies

| | |
|---|---|
| **Disposition** | Fixed |

Found by the third fix validation, the sibling TDR-C8 missed. `HomeStaging.Move.toString` is
`loc.getName() + " -> " + getEnd().getName()`, and `Layout.planReturnToHome` logs it as "planned: ..." in the
same block as the masked reasons - so the plan's own lines still read "X (eastbound, reverse)". The staging
audit's `logStagingAudit` does the same. The repair is `Layout.placeNameOf(getEnd())` in both; a claim would
plan a train home onto a copy-named square and read the logged line. Left open because the three rounds were
spent.


**Fixed on Adam's instruction, 2026-09-14** ("Fix ... C9"). A planned move and both staging-audit lines name
the square through `Layout.placeNameOf`. `core.testReturnHomeSaysWhy.testAPlannedMoveNamesTheSquare`, seen red.

### TDR-C10 - comments around the import still describe the merge before round 2

| | |
|---|---|
| **Disposition** | Fixed |

Found by the third fix validation. The comment on the `pages` branch of `importBundle` still presents "theirs
wins per id" as the live rule - it is reachable now only for a store that knows no page by any number - and
three javadocs say things that stopped being true: `importBundle`'s "importing onto a fresh setup restores
everything", `knownShared`'s "kept verbatim and written back untouched" (true for a load, not an import), and
`intoThisLayoutsPages`'s "so is a field this store does not model", which is unreachable while translating.
Round 3's own comment that "fields this version does model and that name no square are merged exactly as
before" describes an empty set: every modelled collection is a held field. Text only; nothing pins it.

A decision rides on it and is Adam's: a field from a NEWER version is now dropped from an import silently.
It could be named beside the pages left out.


**Fixed on Adam's instruction, 2026-09-14** ("C10- no need to notify"). A newer build's field is left out of an
import without a word, as ruled, and every comment now says what is true: `importBundle`'s javadoc names what an
import keeps and what it leaves out; the pages branch says it is reachable only for a store that knows no page
by any number; the unmodelled-field comment no longer describes an empty set; `intoThisLayoutsPages` and
`knownShared` say that a load keeps an unknown field and a translating import does not. Text only.

### TDR-C11 - a heading typed into a station's name by hand makes two squares read alike

| | |
|---|---|
| **Disposition** | Fixed |

Found by the third fix validation; wider than this round, since `placeNameOf` came in round 1.
`AutonomyBuilder.nodeName` renames a split copy that collides with a hand-named square to "Main (eastbound)
(2)", and `placeNameOf` strips a heading only when it is the last parenthesis. So a square somebody named
"Main (eastbound)" by hand reads as "Main" - the same as the split square's copies - while the renamed copy
keeps its heading. It needs a heading word typed into a name deliberately. Worth a ruling rather than a fix:
refusing the four heading words in names would close it, and Adam has preferred allowing names before
(OB-092).


**Fixed on Adam's instruction, 2026-09-14** ("refuse and close, but make sure the words are uncommon"). A name
ending in the builder's exact heading - a lowercase `(northbound)`, `(southbound)`, `(eastbound)` or
`(westbound)` in brackets at the very end, with anything after a comma inside the bracket (`, reverse` is the
builder's own; see FTN-C1) - is refused, and nothing wider: it is precisely the form `placeNameOf` strips, so it
is precisely the form that makes two squares read alike.
"Eastbound Platform", "Main Line (Northbound)", "Main (eastbound track)" and "Yard (old)" are all still
allowed. The rule is `StationIndex.endsWithAnArrivalHeading`; `AutonomySession.whyNotAPointName` says why in
eight languages; `setPointName` refuses it as the guard, and both doors a person types a name through - the
rename prompt and Name Everything, which asks again for the same square - ask first. Names already in a setup,
or brought in by an import, are left alone. `core.testANameCannotEndInAHeading`, with the allowed names as the
control.

### TDR-C12 - the masking claim for Return Home's reasons pins one of eleven sites

| | |
|---|---|
| **Disposition** | Fixed |

Found by the third fix validation. `testTheReasonsNameSquaresNotCopies` reaches only the out-of-service home
sentence; reverting any of the other ten sites, the two start-square sentences included, leaves it green.
A start standing on an inactive copy-named square, and a home with no route, would reach two more.


**Fixed on Adam's instruction, 2026-09-14** ("C12 expand the test").
`testEveryReachableReasonNamesSquaresNotCopies` checks, on copy-named squares, seven sentences - start out of
service, home excluding the train, home out of service, too short, no route, two homes on one section, and an
occupied home - each for its own sentence as well as for the absence of a heading. Still not reached, and said
in the test: "start not a station", "home not a station", "no arrangement found", and `whyNotHome`'s fallback,
which these small railways decide before a reason is written.

---

## Stale decisions closed

Adam, 2026-09-14: *"Clean up the stale decisions that are ruled."* Eight catalogued findings still read
"Open - needs your decision" in `triage.db` although each had been ruled on or overtaken: RGN-C3 (overturned
2026-09-04; MT-270 validated), SV2-A1 in two documents (ruled 2026-09-02, moot since 2026-09-04), D24-C7 and
TCX-B2 ("20 warnings sounds OK", built), D24-C8 ("OK, because it will be set correctly later"), SVN-B3 (the
room rule, built and pinned), and SVN-B5 (moot since the reversal state came out of `connected` on
2026-09-04). Each is closed with its own note citing the ruling; what the documents said is left as it was.
None remains waiting on a decision.

---

## Checked and found sound

By the reviewer, and not disputed by the validator: the message bundles; the consist function change and
its test in both directions; the tail walk's route selection past a junction; the paste facing re-target;
the Return Home spinner's threading; `AutonomyReport.worthSaying`; OB-217's menu; the per-rail nudge and its
pixel test; the pairwise shared-section scan; the `exec` observer; the shortcut tooltips against the key
handler. Noted, not a defect: a rebuild forgets `arrivedAlong`, which behaviour.md records as the ruling.
