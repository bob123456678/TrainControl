# live-snapshot

**A frozen copy of Adam's real railway**, for the tests that need geometry no hand-authored fixture is
going to have: complex pathing, random-movement checks, and the base for mutations.

Adam: *"make sure you still have a copy of the live layout for ones that require complex pathing and
random movement checks. I'd recommend using these complex setups as the base for mutations."*

## Where it came from

**Refrozen from commit `e36df979`** (branch `autonomy-diagram-r0`, 2026-09-23) - Adam's fully measured
layout, which he asked to have blessed: *"perhaps it's time that I finalize the layout so that we can bless
it"*, and *"OK, my fully measured layout is now in."*  First refrozen from `2958fcf3` the same morning; `e36df979`
is his follow-up edit of that day, which bars arrivals from the east at BottomMainA again, and only
`setup.json` changed between the two.  Only the two autonomy files differ from the first freeze; the five pages
are byte-identical to it.  First taken from **commit
`e6f4649ceea0258e5fa6a378616b0158d04c6289`** (2026-09-09).  Both via `git show HEAD:cs2_sample_layout/...` —
**not** from the working tree. That matters:
his tree carried uncommitted operating changes to `setup.json`, `configuration-Main.json` and
`1 - Main.cs2` when this was frozen, and a fixture taken from a working tree is a fixture nobody can
reproduce.

All ten tracked files were copied byte for byte:

    config/autonomy/configuration-Main.json
    config/autonomy/setup.json
    config/autonomy_legacy/autonomy.json
    config/gleisbild.cs2
    config/gleisbilder/1 - Main.cs2
    config/gleisbilder/2 - Bottom.cs2
    config/gleisbilder/3 - Top Parking.cs2
    config/gleisbilder/4 - Combined.cs2
    config/gleisbilder/5 - Test.cs2
    config/gleisbilder/routes.json

To refresh it, re-run the same `git show HEAD:` copy from a clean commit and update the hash above —
never copy from the working tree, and never write to `cs2_sample_layout`.

## Why a copy rather than the original

Eighteen test classes open `cs2_sample_layout` today. That is Adam's live railway: it changes as he
operates it, so their ground truth moves underneath them. On 2026-09-09 three broke in one morning —
one had picked its subject out of the live railway by a property he had since changed, and two had
borrowed a real locomotive's length and were refused when the length rule widened.

This folder is the same railway with the clock stopped. `support.Scenario` still copies it to a sandbox
before anything reads it, so the checked-in fixture is never written to either.

## Invariants — a test may rely on these

- **The five pages**, in index order: `1 - Main`, `2 - Bottom`, `3 - Top Parking`, `4 - Combined`,
  `5 - Test`.
- **Everything in the files** — station names, s88 addresses, portals, priorities, homes, lock edges,
  the whole authored setup as it stood at that commit. It is a snapshot; that is the point.
- **What it builds to**: 122 reduced edges, 87 Points, 125 edges since the refreeze of 2026-09-23 (128, 96 and
  149 before it). Pinned exactly in
  `testTheFrozenRailwayIsStillTheRailway`, because far below that is the five-edge skeleton
  `support.LayoutSandbox.wiredPages` describes — a railway whose switches have no accessories — and a
  test standing on it passes by asserting about null.

## Not invariants — set these in code

A test that picks a subject out of this railway *by a property* — "the first station with a length", "a
locomotive long enough" — is the failure mode the whole library exists to end, and freezing the folder does
not fix it. Name the square.

- **Lengths.** Since the refreeze of 2026-09-23 these are Adam's own measurements, and the station sizes he
  gave. A test about the railway as he runs it reads them; a test about a rule with lengths of its own clears
  his first (`clearEveryTileLength`, `clearEveryMaxTrainLength`) and says why - as the length guards and the
  two censuses do. Until then the snapshot measured nothing: Adam, 2026-09-10, *"We put the lengths of 1 in
  there for testing. Actual tracks are much longer."*
- **Train placements and locomotive properties.** A test creates its own locomotives rather than
  borrowing one from the real database.

## Used by

- `core.testACompulsoryTurnIsNotAQuestion`
- `core.testAnImportedFacingGuessCanStart`
- `core.testTheGreyNamesTheRoadOnADoubleCurve`
- `core.testATurnedTrainIsNotSentIntoAnotherTail`
- `core.testATrainDoesNotRunIntoItsOwnTail`
- `regression.testAHandSendIsRefusedWhileTheSetupIsBroken`
- `regression.testTheFacingMenuIsAboutTheTrainThere`
- `ui.testNonAtomicRoutesNeedTheirLengths`
- `core.testMassAssignLengths`
- `core.testASecondImportFillsGapsAndDoesNotOverwrite`
- `regression.testADeclinedSetupEditSaysSoAndSurvivesTheExit`
- `regression.testTheArrowsKeepTheirAim`
- `regression.testAPlacedTrainRecordsWhereItCameFrom`
- `core.testAPasteDoesNotTurnTheTrainRound`
- `core.testAStationsSizeIsAnAllowance`
- `regression.testTheOrangeIsDrawnWhereTheTailStopsAtTheSwitch`
- `regression.testAThrottleReversalIsFollowedFromItsEcho`
- `core.testAPastedTrainKeepsItsDirection`
- `core.testTheAutoTierScopeMatchesTheRuntime`
- `core.testTheFrozenRailwayIsStillTheRailway`
- `core.testTheLengthGuardsOnTheRealLayout`
- `core.testEverySquareBuildsToTheCopiesTheSetupImplies`
- `core.testTheRoomRuleCensusOnTheRealLayout`
- `core.testWhichSquaresTheRoomRuleClosesOff`
- `core.testTheShadingFollowsTheTrain`
- `regression.testAPendingTurnSurvivesTheRebuild`
- `regression.testClearAllTrackLengths`
- `regression.testCutWithNothingHovered`
- `regression.testDeleteForgetsTheWholeSquare`
- `regression.testTheHoveredSquareIsForgotten`
- `regression.testTheBulkClearSaysWhatCancelDoes`
- `regression.testControlSNamesOnlyASensor`
- `regression.testEscapeClosesTheEditor`
- `regression.testOneChangeSticks`
- `regression.testTheDiagramIsNotRebuiltForAnArrow`
- `regression.testTheDiagramRefreshDoesNotWaitOnTheRailway`
- `regression.testTheGreyDoesNotRebuildTheDiagram`
- `regression.testTheHomeLabelIsDrawnOnce`
- `regression.testTheTurnAtTheDestinationReachesTheDiagram`
- `regression.testTheWashIsNoLongerThanTheTrain`
- `ui.testBlockedTrackIsGreyWhileAutonomyRuns`
- `ui.testTheGreyAppearsAtIdleToo`
- `ui.testTheShadingIsRedrawnWhenATrainMoves`
- `ui.testTheTrainIsShownAsALine`
- `core.testAShortTrainDoesNotBlockTheWholeRun`
- `core.testALegacyImportMatchesTheFileItCameFrom`
- `core.testAnImportDoesNotSwitchYourRoutesOff`
- `regression.testARememberedNoneOpensWithTheCaptionsOff`
- `regression.testAWalkMovesTheFlashOn`
- `core.testABerthAndAPlatformJudgeAnOverhangDifferently`
- `core.testAMayTurnStationIsNotATerminus`
- `core.testWhatCountsAsAParkingSquare`
- `core.testAnAnsweredZeroIsNotMissing`
- `ui.testACutTrainArrivesTheWayItWouldDrive`
- `ui.testAPastedTrainFacesTheWayTheOperatorChose`
- `core.testALockReachesTheRailBeingRunOver`
- `ui.testBulkToolsHoldsTheWholeLayoutTools`
- `ui.testOnlyAStationHoldsAnotherBack`
- `ui.testReturnHomeShowsItIsWorking`
- `ui.testTheEditorNamesItsShortcuts`
- `regression.testAPassingTrainMayStandAcrossThePoints`
- `regression.testTheTurnRuleDoesNotChangeTheRealRailway`
- `regression.testCancelUndoesAutonomyEdits`
- `regression.testTheTailCanBeGivenInTheEditor`
- `regression.testPathTypeRedrawsTheTestInTheEditor`
- `ui.testYourOwnRoutesToggleWithoutASync`
- `ui.testTheLengthPromptHasTheKeyboard`
- `core.testATailPastASwitchIsAskedAbout`
- `core.testATrainIsPutOnlyWhereItCanStart`
- `regression.testTheTailIsPickedOnTheDiagram`
- `regression.testANewStraightJoinsTheTrackBesideIt`
