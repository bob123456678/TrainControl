# What the railway does, and why

This is the intended behavior of the features that decide where trains may go and what happens when they get there.
It is written to be **read against the code and disagreed with**: where this reference and the program differ, one of
them is a bug, and Adam decides which.

Every rule is Adam's, quoted where the wording is the rule. A rule states what is true, why, and the tracker id
(OB-, MT-, FR-###) it resolves to; how it was argued lives in that tracker row and in git. Where a rule has a known
limit, the limit is stated, because a limit nobody wrote down reads as a bug the day it is met.

**How this relates to the comments in the code.** Adam: *"we should make comments in the
code to strive to be authoritative and self-contained, with the behaviour document being the
documented intended functionality."* This reference holds the intent; a comment explains why the code in front of
you implements it the way it does, and stands on its own. The full rule is in
[`docs/reviews/README.md`](../reviews/README.md).

**One file per area, in `docs/reference/behaviour/`; this file is the index.** Sections keep the numbers and headings
they had when this was one file, so a citation such as "behaviour.md §5c" or "behaviour.md section 6a" (the tracker and
the old reviews use that form) resolves through the table below; code and docs cite the area file and section, as in
`behaviour/tail.md §5c`. The numbers are not in file order: 7c sits between 7a and 7b, as it always has.

| section | heading | file | main classes | main tests |
|---|---|---|---|---|
| 1 | The three tiers: autonomy, manual, Return Home | [tiers-and-choice.md](behaviour/tiers-and-choice.md) | `Layout` (`isPathClear`, `maxActiveTrains`), `HomeStaging` | `core.testHomeStaging`, `core.testMaxActiveTrains`, `regression.testStationBlockedByAnotherPoint` |
| 1a | How autonomy chooses: priority, the routing rule, the yield and the pauses | [tiers-and-choice.md](behaviour/tiers-and-choice.md) | `Layout` (`pickPath`, `PathPreference`, `isSendableDestination`), `Point.excludes` | `core.testAPausedTrainStaysPaused`, `ui.testTheActiveTickPausesATrain` |
| 2 | Inactive squares | [tiers-and-choice.md](behaviour/tiers-and-choice.md) | `Layout.isPathClear` | |
| 2a | A square that stops being a station keeps what it had | [tiers-and-choice.md](behaviour/tiers-and-choice.md) | `AutonomySession` | `core.testATrainIsPlacedOnceAndKeepsItsFacing`, `core.testAdvancedRoutes` |
| 3 | Reversals | [reversals.md](behaviour/reversals.md) | `AutonomyBuilder`, `ManualReversalPrompt`, `Layout.turnsOnArrival`, `AutonomySession.copyFacing` | `core.testNonReversibleTrains`, `core.testTheArrivalHonoursTheAnswer`, `core.testEverySquareBuildsToTheCopiesTheSetupImplies` |
| 4 | Which way a train is pointing, and where its tail is | [facing.md](behaviour/facing.md) | `Layout.moveLocomotive`, `ArrivalSidePrompt`, `GraphLocAssign`, `Point` | `regression.testTheRefusalsAreAskedAtTheDoors`, `core.testACurvedPlatformRecordsASideTheBuildUses`, `core.testWhyStuck` |
| 4a | Station captions: a readout, not a name plate | [captions-and-icons.md](behaviour/captions-and-icons.md) | `AutonomySession`, the setup check | `core.testACaptionStaysWithItsSquare` |
| 4b | A station's icon: what the shape says | [captions-and-icons.md](behaviour/captions-and-icons.md) | `TileAnnotation`, `AutonomySession.deadEndSide` | `core.testAutonomyDiagramMonitor`, `ui.testDiagramLooksRight` |
| 5 | Length: will the train fit? | [length.md](behaviour/length.md) | `Layout.whyTooLongForThisRoute`, `Point.validateTrainLength` | `core.testTheRoomRuleCensusOnTheRealLayout` |
| 5a | Room, where the train comes to rest | [length.md](behaviour/length.md) | `Layout` (`measuredRoomAtTheEndOf`, `theApproachItselfHoldsIt`) | `regression.testAPassingTrainMayStandAcrossThePoints`, `core.testABerthAndAPlatformJudgeAnOverhangDifferently` |
| 5b | What "unmeasured" means | [length.md](behaviour/length.md) | `Edge.isMeasured`, `AutonomySession` (Mass Assign Lengths) | `core.testMassAssignLengths`, `core.testAnAnsweredZeroIsNotMissing` |
| 5c | The tail: track a standing train is lying across | [tail.md](behaviour/tail.md) | `Layout` (`walkOneTail`, `edgesCoveredByStandingTrains`, `placesCoveredByStandingTrains`), `GraphReducer` | `core.testATrainDoesNotRunIntoItsOwnTail`, `core.testATailPastASwitchIsAskedAbout`, `ui.testTheGreyAppearsAtIdleToo` |
| 5d | Atomic Routes, and the track a train is handed back | [tail.md](behaviour/tail.md) | `Layout.tailHasProvablyPassed` | `core.testTrainTailClearsEdges`, `core.testATrainIsDispatchedOnce` |
| 5e | Permanently-set turnouts: fork to base only, and they still bound a berth | [permanent-turnouts.md](behaviour/permanent-turnouts.md) | `TilePorts`, `TileGraph` | `core.testAutonomyDiagramReducer`, `core.testAutonomyDiagramPorts`, `core.testTheArrowsTellTheWholeStory` |
| 5f | Two permanent turnouts drawn toe to toe are one crossing | [permanent-turnouts.md](behaviour/permanent-turnouts.md) | `TileGraph.crossingPartner` | `core.testTwoYsMakeACrossing` |
| - | The autonomy editor's keyboard doors | [keyboard.md](behaviour/keyboard.md) | `LayoutEditor.hoveredSquare` | `regression.testNoTwoShortcutsShareAKey` |
| - | The main window's key map reaches the whole window | [keyboard.md](behaviour/keyboard.md) | `TrainControlUI.letTheWholeWindowDriveTrains` | `regression.testTheKeyMapReachesTheWholeWindow` |
| 6 | Parking and Return Home | [return-home.md](behaviour/return-home.md) | `HomeStaging` | `core.testHomeStaging`, `core.testATrainComesHomeFacingTheWayItWasHomed`, `testNothingOnTheEventThreadTakesTheRailwaysMonitor` |
| 6a | Editing the setup while the railway is using it | [editing-while-running.md](behaviour/editing-while-running.md) | `TrainControlUI.rebuildRunningLayoutFromSetup`, `RunningLayoutSync.putTheTrainsBack` | `regression.testAnEditedPlacementSurvivesTheRebuild`, `core.testTheEditorWarnsOfARailwayThatCannotWork` |
| 7 | Routing checks in the editor | [editor-checks.md](behaviour/editor-checks.md) | `AutonomySession`, `Layout.explainDestinations` | `core.testARouteIsFoundPastATerminus`, `regression.testPathTypeRedrawsTheTestInTheEditor` |
| 7a | A route that meets a train | [routes.md](behaviour/routes.md) | `MarklinRoute.execRoute`, `TrainControlUI.askAboutRouteConflict` | `regression.testARouteDoesNotThrowSwitchesUnderATrain`, `core.testAStopRouteStandsAlone` |
| 7c | The signals a station commands: the exit guard and the entry guard | [routes.md](behaviour/routes.md) | `Layout` (`refreshProtectingSignal`, `throwEntryGuard`) | |
| 7b | Routes that come from a file, and the pause between their commands | [routes.md](behaviour/routes.md) | `MarklinRoute` | |
| 8 | Things that are true of the whole system | [whole-system.md](behaviour/whole-system.md) | `Layout.fromJSON`, `AutonomyBuilder`, the configuration doors | `regression.testNoSetupEditDuringARun`, `regression.testTheImportDoorReadsAnOldFile` |
| - | Where this document came from | [citations.md](behaviour/citations.md) | | |
| - | Looking up a citation | [citations.md](behaviour/citations.md) | `docs/manual-tests/triagedb.py` | `regression.testEveryCitationResolves`, `regression.testTheRecordsCountTheStore` |

`docs/tools/behaviour-split-proof.py` checks that the split kept every tracker id, every quote of Adam and every
section of the single file.
