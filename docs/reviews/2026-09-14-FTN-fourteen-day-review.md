# Fourteen days of commits, reviewed while the MT sweep of 2026-09-14 waits for its run

**Status:** open 2026-09-14 - fixes in progress

**Prefix:** FTN (checked free: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`)

**Covers** the commits of 2026-08-31 to 2026-09-14 (`git log --since=2026-08-31`, 439 commits), weighted towards
the newest code: `73bc2be8`, `b966e73e`, `dbac2f08`, `785d913a`, `6c36d262`, `f40920ee` and `d4f09f5d`. One
reviewer read them, told not to repeat [the TDR review](2026-09-14-TDR-ten-day-review.md) unless a fix there was
itself wrong, and to mark any finding that touches a hands-on test Adam is about to run. Adam, 2026-09-14:
*"iterate on any of its findings that don't conflict on a pending test"* - so a finding that does is recorded
and held, not changed underneath his run.

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| (none) | | |

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| FTN-B1 | Open | `regression.testTheFunctionButtonsFollowTheConsist.testTheLastLocomotiveAskedForIsTheOneDrawn` - never runs the deferred render it pins |
| FTN-B2 | Open | `TrainControlUI.followDirectionChanges` - builds the autonomy session on the message thread inside the window monitor |

### FTN-B1 - the TDR-B5 claim never runs the deferral it was written to pin

| | |
|---|---|
| **Disposition** | Open |

The hook `afterARenderIsPosted` runs on the renderer thread AFTER the painting has been handed to the event
thread.  The claim waits for that painting to be drawn - its precondition asserts the all-MM2 consist's buttons
are showing - and the painting's `finally` has by then cleared `locRenderInFlight`.  So the second request takes
the ordinary path and starts a render of its own.  The claim went red against the old guard, which looked at the
render's FUTURE, which the hook held open; it cannot see the new mechanism at all.  Delete the pending request's
re-run from `renderFinished`, or never set `locRenderPending`, and it stays green - while every request arriving
between submission and the end of the painting, the commonest window in the program, is dropped as before.

Touches MT-403 only in that it is the test of the behaviour MT-403 checks by hand; the repair is to the claim and
to where the test hook sits, and changes nothing a person can see.

### FTN-B2 - the direction follower builds the autonomy session on the message thread, inside the window's monitor

| | |
|---|---|
| **Disposition** | Open |

`followDirectionChanges`, called from the `synchronized` `repaintLoc`, guards on `getAutonomySession() == null`.
That getter is the lazy builder - it parses every page, can rewrite `.cs2` files and raise a dialog - and the rule
written for exactly this is at `repaintTimetable`: *"never getAutonomySession() ... (SV-B2)"*.  After
`initializeTrackDiagram` resets the session without clearing the layout, the next locomotive message from the
Central Station builds the session on `locMessageProcessor` while holding the window's monitor.  Since TDR-B5
(`73bc2be8`) the event thread takes that monitor at the end of every locomotive render, so it queues behind the
whole parse; and a message-thread build can race an event-thread build of the same session.  No deadlock traced.
Introduced `03f58b29`, 2026-09-06.

---

## C - documentation, tests and minor

| id | status | where |
|---|---|---|
| FTN-C1 | Open | `StationIndex.endsWithAnArrivalHeading` - refuses more than its documentation and claim say |
| FTN-C2 | Held for MT-376 | `AutonomyEditorPanel` - the blocker list and the click still differ on an unnamed station |

### FTN-C1 - the name refusal is wider than it says

| | |
|---|---|
| **Disposition** | Open |

`withoutArrivalSuffix` reads the heading up to the first comma and ignores the rest, so "Depot (eastbound, old)"
is refused.  That is CONSISTENT - `placeNameOf` strips it to "Depot" too, so refusing it is right - but the
javadoc, `whyNotAPointName`, the TDR review and `core.testANameCannotEndInAHeading` all say "optionally with
`, reverse`" and nothing wider, which invites somebody to loosen it.

### FTN-C2 - the Unavailable While Occupied list and the click differ on a station with no name

| | |
|---|---|
| **Disposition** | Held |

The list offers named squares that are stations; the click accepts any station.  A station square not yet named
is not offered by the list and is accepted by a click - after which it appears in the list through the stored-entry
path, drawn by its sensor or coordinates.  Harmless, but the comment that the two doors "must refuse the same
squares", and `ui.testOnlyAStationHoldsAnotherBack`, whose plain square is both unnamed and not a station, overstate
it.  **Held, not changed:** it touches MT-376, which Adam has not run since that behaviour last changed.

---

## Checked and found sound by the reviewer

`renderActiveLoc`/`renderFinished` themselves (flag cleared in a `finally`, the merge correct, the direction follow
once per request, `LocRenderer` never shut down); MT-359's consist functions; the MT-335 tail walk and the TDR-B1/B2
re-stand fixes; the import translation (the modelled collections and the held fields match); FR-078's reasons and
masking; FR-077's spinner; the MT-394 paste; both C11 naming doors; the C5/C8/C9 masking sites; `worthSaying`;
OB-216's per-rail nudge; the eight bundles' new keys.
