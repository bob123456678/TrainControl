# REG7 - Regression review of the last seven days

**Status:** open

**Version reviewed:** commit `65bd3161` ("The consolidated open-questions document", 2026-09-06),
branch `autonomy-diagram-r0`. **Reviewed:** 2026-09-07.
**No code was changed as part of this review, and no tests were run** - analysis only, per the
review brief. Every claim below rests on reading the enforcing method at HEAD and tracing its
callers; the diff was used to find the hunks and the current source to verify them.

**Prefix for citing this document: `REG7`.**

**Scope:** the ~245 commits of 2026-08-30 .. 2026-09-06, weighted as instructed toward the last two
days (2026-09-05 evening onward: the reversal-policy, length, tail-blocking and `arrivedFrom` work,
~4,600 diff lines, read hunk by hunk and verified against HEAD). The earlier five days (~8,900 diff
lines, largely the editor-flicker, placeholder, and audit-fix rounds that were themselves validated
by multiple in-week validator passes) were sampled by hunk map and targeted reads (`Edge`,
`HomeStaging`, `MarklinRoute`, `RouteCommand`), not read line by line. See Coverage at the end.

Nothing below re-reports what `docs/reference/open-questions.md` already lists as open or as a
deliberate limit. In particular: the one-answer-per-journey reversal prompt, the tail walk stopping
at forks and unmeasured track, room measured from the last switch, Return Home refusing an inactive
start, and "arrivedFrom must be set for blocking to happen" are all documented and are not findings.
REG7-A1 and REG7-B1 sit *beside* two of those limits and are distinct from them; each entry says how.

---

## Status

| # > **AUDITED 2026-09-07 (REG8-C6).** The table below had gone stale within three days - it showed every finding Open after three had been closed. `A1` was fixed on Adam's ruling (`5f492d9f`) and then **dissolved** by `d45d7951`, which removed the mechanism it described; `A2` was traced to something sharper and fixed (`82e10084`); `B1`'s prompt-side half closed in `8f006b3a` and its store-side half in `073ab12a`. `B2` is narrowed by the week's placement sweeps but not closed - see `REG8-B2`. The rest were checked against HEAD and are genuinely open. This is the `audit-the-bodies-not-the-index` failure the 2026-09-04 audit existed to end, recurring in the newest document in the folder, which is why the correction is written here rather than by quietly editing the rows.

| Finding | Severity | Confidence | Disposition |
|---|---------|----------|------------|-------------|
| REG7-A1 | The departure answer overrides the path's committed geometry: "keep" at a may-reverse **turning copy** the path routes through skips a turn the route depends on; "turn" at a **plain copy** reverses a train whose route continues forward | A | Plausible | Open |
| REG7-A2 | `followDirectionChanges` cannot tell the run's own reversal from an operator's: after any journey that turned the train, the first post-run echo spuriously flips a recorded facing and can move the train onto the wrong copy | A | Confirmed (trace) | Open |
| REG7-B1 | `arrivedFrom` has two stores and no reconciliation: the paste prompt's answer never reaches the running layout until a rebuild; a rebuild resurrects stale setup values onto new occupants; runtime arrivals are never captured | B | Confirmed | Open |
| REG7-B2 | Three of the four placement doors neither ask, assume, nor clear `arrivedFrom` - only the diagram paste implements Adam's placement rule | B | Confirmed | Open |
| REG7-B3 | The tail walk covers one directional `Edge` per hop while `checkPath` does an exact-edge lookup - the twin edge of a doubled rail, and every sibling-copy edge over the same physical track, stay uncovered while the diagram greys the whole square | B | Plausible | Open |
| REG7-B4 | `ArrivalSidePrompt` asks about ONE copy's sides; when the arbitrary landing copy is the turning copy its single side short-circuits the question - on exactly the square class Adam said to ask about | B | Plausible | Open |
| REG7-C1 | Running-layout coordinates drop the page stacking, so portal neighbours on different pages can resolve to a wrong or null `sideTowards` - acknowledged in a code comment, absent from open-questions.md | C | Confirmed | Open |
| REG7-C2 | `edgesCoveredByStandingTrains` is recomputed on every `isPathClear` call, `synchronized`, with an O(all-edges) scan per hop - inside `pickPath`'s exhaustive enumeration | C | Plausible | Open |
| REG7-C3 | `reachableTiles` lost its start-closure clause: a train standing on a closed square is now reported as reaching everything, while an autonomous run cannot move it at all | C | Confirmed | Open |
| REG7-D1 | Checks that came back clean (see section) | - | - | Recorded |

---

## REG7-A1 - The reversal answer is applied at copies whose geometry contradicts it

**Severity A. PLAUSIBLE** - every link of the chain is confirmed by reading; the physical
consequence was not executed.

**This is not the documented limit.** `open-questions.md` records that a journey passing *several*
may-reverse squares gets one answer for all of them, deliberately. This finding needs only **one**
may-reverse square: the answer is applied at whatever copy the path happens to route through, and
the copy - not the answer - is what the reserved path physically requires.

The chain:

1. Manual paths may legitimately route *through* a reversing point.
   `Layout.reversesAlongTheWay` (src/org/traincontrol/automation/Layout.java:3600) bars that for
   full autonomy only, and its javadoc says so: *"a hand-driven move and the staging planner ...
   can still use a headshunt, which is what they are for."* So `getPossiblePaths` offers the manual
   doors journeys whose middle passes a turning copy.
2. For a may-reverse square, that turning copy is in the door's `asksAbout` set
   (`ManualReversalPrompt.forOperator`, src/org/traincontrol/gui/ManualReversalPrompt.java:163 via
   `mayTurnTiles()`), so `shouldReverseAt` falls through to the policy
   (src/org/traincontrol/automation/Layout.java:5272-5280) instead of turning unconditionally.
3. The policy is a constant fixed at departure (`forJourney`,
   src/org/traincontrol/gui/ManualReversalPrompt.java:64-136). **Yes/keep is the default, the
   Escape answer, and the cannot-ask answer.**
4. At the turning copy, "keep" answers false, so `executePathInternal` neither stops nor turns
   (src/org/traincontrol/automation/Layout.java:6111). A turning copy's only outgoing edges leave
   by the side the train arrived from - the code says this itself, repeatedly, about compulsory
   turns - so the train carries on at line speed onto track its path does not hold, while its
   milestones wait on sensors behind it.
5. The dual: at a **plain** copy of a may-turn square, `mayReverseAt` is true
   (src/org/traincontrol/automation/Layout.java:5470-5486, block-sibling test), `asksAbout` is
   true, and an operator who answered "turn" (because the *destination* was the square they meant)
   gets a stop-and-`switchDirection` at a copy whose route continues forward
   (Layout.java:5280, 6111-6130) - the train physically reverses off its reserved path.

**What used to happen:** before this week, `executePathInternal` reversed unconditionally at every
`isReversing()` point it reached (the old code the diff replaces at Layout.java:6072-6111). Both
hazards are new with the policy.

**Why journeys to terminuses are safe** (checked): `forJourney` returns `KEEP_DIRECTION` without a
prompt for a terminus-ending journey (ManualReversalPrompt.java:89), and `shouldReverseAt` answers
the flag, ignoring the policy, whenever the destination is a terminus (Layout.java:5239). Backing
into a terminus through a reversing point still turns. The exposed shape is: manual send **through**
a may-reverse square's turning copy to a **non-terminus** destination - e.g. through a headshunt to
a berth, which is an ordinary station.

**Fix direction to consider:** the copy the path routes through already states what the railway
needs (`current.isReversing()` per copy). The operator's answer is information about which *path*
they meant, not an override of the geometry - so either honour the answer during path selection, or
at execution time follow the copy and use the answer only where both copies were reachable.

## REG7-A2 - The window follows the run's own reversal, once the run ends

**Severity A. CONFIRMED by trace** (the comparison provably fires; the damage path is reasoned, not
executed).

`followDirectionChanges` (src/org/traincontrol/gui/TrainControlUI.java:10031-10110) exists to obey
Adam's rule that a track direction command updates the graph, deferred to the end of a run
(`ca0265f4`). The mechanism is a per-locomotive baseline (`lastSeenDirection`,
TrainControlUI.java:10008) compared against `loc.goingForward()` on every locomotive echo, with a
guard: while `isRunning()` the change is not followed, and the baseline is only seeded
(`putIfAbsent`, line 10062), never updated.

The gap: **the run's own `switchDirection()` is indistinguishable from an operator's.** A journey
that ends at a terminus or a reversing destination physically turns the train
(src/org/traincontrol/automation/Layout.java:6451-6457), and its echo arrives while the locomotive
is still registered - `isRunning()` (Layout.java:1663-1667) counts `activeLocomotives`, and
deregistration happens a full second *after* the switch (Layout.java:6457, 6465-6469) - so the
baseline keeps the pre-run direction. The **first echo after the run ends** then reads
`was != forward` (TrainControlUI.java:10067-10070) and calls `flipFacing` - for a direction change
the run itself made and already expressed by leaving the train on the turned copy.

What that flip does (src/org/traincontrol/automationui/AutonomySession.java:1509-1588):

- If the arrival square is split and carries a recorded facing, `flipFacing` **flips the correct
  facing and `moveOntoFacingCopy` relocates the locomotive onto the other copy of the running
  layout** - after which `getPossiblePaths`, the destination menus, and the next dispatch answer
  for a direction the train does not have. That is wrong behaviour on the layout.
- If the arrival square is undecidable (a terminus has one copy; a mid-session square often has no
  recorded facing), the candidate loop *carries on* (the DIR-C3 rule) to the square the **setup**
  still places the train on - the platform it departed - and flips *that* square's remembered
  facing, then logs `infoFacingFollowedDirection` naming a correction that corresponds to nothing.
  `captureFromLayout` never clears a facing on an empty square ("never cleared - a better guess
  for the next one", AutonomySession.java:3662-3669), so the corruption persists.

The comment at TrainControlUI.java:10054 says *"a run turns its own trains at reversing points and
knows what it did"* - but nothing tells `lastSeenDirection` what the run did. During a whole
autonomy session `isRunning()` stays true, so after the session stops, every train that reversed an
odd number of times gets one spurious flip at an arbitrary later moment (the next echo that touches
it while nothing is running).

**Fix direction to consider:** have the run re-baseline the map when *it* commands a direction
change (one line beside `switchDirection()` in `executePathInternal`), or key the baseline update to
commands this program did not send.

## REG7-B1 - `arrivedFrom` is stored twice and reconciled in neither direction

**Severity B. CONFIRMED** (data flow read end to end; every claim has a code point).

The new tail property lives in two places: the running layout's `Point.arrivedFrom`
(src/org/traincontrol/automation/Point.java:39), which is the ONLY store the blocking walk reads
(`edgesCoveredByStandingTrains`, src/org/traincontrol/automation/Layout.java:5319, and through it
the diagram greying), and the setup's per-tile `"arrivedFrom"` property
(src/org/traincontrol/automationui/AutonomySession.java:5185). Three one-way gaps:

1. **The paste prompt's answer never reaches the railway it is about.**
   `TrainControlUI.rememberPlacement` (src/org/traincontrol/gui/TrainControlUI.java:6198-6201)
   writes the asked/assumed/forced side to the setup only; the live `Point` is in scope as `point`
   and is never told. The walk reads the live Point, so the answer the operator just gave in the
   dialog blocks nothing and greys nothing until the next full rebuild. (The editor's right-click
   menu door writes both - src/org/traincontrol/gui/AutonomyEditorPanel.java:2884-2914 - which is
   how the sibling shows what this door should do.) `open-questions.md` says a hand-placed train
   "gets it from the placement rules"; today it gets it only on paper.
2. **A rebuild resurrects stale values onto new occupants.** `AutonomyBuilder` passes unrecognized
   setup keys - `arrivedFrom` among them - into the emitted layout JSON on every copy
   (src/org/traincontrol/automationui/AutonomyBuilder.java:924-968; `DERIVED` at :197 does not
   exclude it), and `parseAuto` applies it (src/org/traincontrol/automation/Layout.java:8238-8241).
   But nothing maintains the setup: a run clears the live origin (Layout.java:6446) and writes the
   live arrival (Layout.java:6438) while the setup keeps whatever was last hand-recorded. After the
   next rebuild, a *different* train standing on that tile inherits the old occupant's tail side -
   which blocks the wrong rail and, in this codebase's own words about exactly this feature,
   "reports a protection that is not there."
3. **Runtime arrivals are never captured.** `captureFromLayout` handles neither direction:
   `"arrivedFrom"` is not in `POINT_OPERATIONAL_KEYS`
   (src/org/traincontrol/automationui/AutonomySession.java:2431-2433) and has no `FACING`-style
   special case (AutonomySession.java:3662-3669). So every rebuild also *loses* every tail the
   railway learned by watching trains arrive - the blocking quietly evaporates on the next editor
   apply.

This is the `new-field-needs-the-copy-constructor` shape: the field was added to `toJSON`/`parseAuto`
(the export round trip, which works) but not to the builder-capture round trip that the running
railway actually lives on. One reconciliation rule - capture it like `FACING`, clear it where the
layout no longer carries it, and write the live Point at the paste door - closes all three gaps.

## REG7-B2 - Three placement doors never touch `arrivedFrom`

**Severity B. CONFIRMED.**

Adam's rule (quoted in `ArrivalSidePrompt`'s javadoc): *"if trains are placed/pasted by the user on
a 'can reverse' station, ask ... Otherwise, assume 'arrived from' to be the opposite of their
facing ... if the station is a terminus, there is only one forced option."* Only the track-diagram
paste implements it (`rememberPlacement`; `ArrivalSidePrompt.forPlacement` has exactly one caller).
The other placement doors record who and which way, and nothing about the tail:

- the editor's edit-dialog door, src/org/traincontrol/gui/AutonomyEditorPanel.java:4087-4110;
- the editor's dropdown door, AutonomyEditorPanel.java:4255-4270;
- the diagram right-click place/turn door, `placeFacing`,
  src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:961-1001.

None of them *clears* a stale value either (`placeLocomotive` leaves point properties other than
`loc` alone - its own comment at AutonomyEditorPanel.java:4261 says the same about facing), so a
train placed through any of these doors under a tile that carries an old answer inherits the old
tail on the next rebuild (see REG7-B1 gap 2). This landed the same day as `af1aada8` "Sweep the
placement siblings", which swept these doors for *facing* and not for the new property - the
`fix-one-site-sweep-the-siblings` shape, one property behind.

## REG7-B3 - The walk covers one directional edge; the guard looks up exact edges

**Severity B. PLAUSIBLE** - the asymmetry is confirmed by reading; how often a real conflicting
path traverses an uncovered twin depends on graph shape and was not executed.

`edgesCoveredByStandingTrains` covers exactly one `Edge` object per hop
(src/org/traincontrol/automation/Layout.java:5413): the candidate list is
`getNeighborsAndIncoming` (Layout.java:2081-2113, outgoing sorted first), and whichever single edge
matches the side (first hop) or the lone neighbour (later hops) is `put`. The routing guard then
does an exact-object lookup per path edge (`coveredTrack.get(e)`, Layout.java:2418).

But one physical rail is several edges in this graph: the doubled one-way pair, and - on split
squares - separate edges between different *copies* of the same two squares. A conflicting route
that traverses the same rail via the twin direction or a sibling copy's edge is not in the map and
is not refused. Meanwhile the diagram's greying deliberately matches **both** directions and joins
by **square** (`tilesCoveredByStandingTrains`,
src/org/traincontrol/automationui/AutonomySession.java:4805-4850: "Both directions of a reduced
edge are matched"), so the operator sees a square greyed as blocked that the router will happily
send another train across. That is `guard-and-affordance-same-question` inverted: the affordance
claims more than the guard enforces. The known limits (fork, unmeasured) do not cover this; they
are about how *far* the walk reaches, not about which representation of one rail it marks.

**Fix direction:** cover by rail, not by edge - e.g. also cover every edge whose endpoints' blocks
match the chosen segment's, or key the guard's lookup the way the greying already joins.

## REG7-B4 - The tail question is asked of one arbitrary copy

**Severity B. PLAUSIBLE** - depends on which copy `getAutonomyPointForTile` yields on a split
square, which follows `StationIndex.speakerAt`'s documented "any copy will do".

`ArrivalSidePrompt.forPlacement` derives its offered sides from ONE Point's edges
(`sidesOf(layout, at)`, src/org/traincontrol/gui/ArrivalSidePrompt.java:161-175), and
`rememberPlacement` passes the copy the paste handler happened to hold - on an empty square, copy 0
by the code's own admission (TrainControlUI.java:6168-6177 quotes it). On a split may-reverse
square, the turning copy's edges all touch **one** side, so if copy 0 is the turning copy,
`sides.size() == 1` short-circuits (`ArrivalSidePrompt.java:126-128: "a terminus has one way in"`)
and records that side **without asking** - on exactly the square class whose whole point is that
the answer cannot be assumed. A plain copy's two sides can likewise under-offer a square whose
other copies touch more sides.

This is the same one-copy defect the very same commit fixed in `canDepartFrom` ("the question is
about the SQUARE, and the guard that asked it looked at one copy", SPEC-B5,
src/org/traincontrol/automationui/AutonomySession.java:5019-5045). The sides on offer should be the
union over `facingsFor(tile)`'s copies, and the "one side = forced" shortcut should require the
square - not the copy - to have one way in.

## REG7-C1 - Overlapping pages make `sideTowards` lie across portals

**Severity C. CONFIRMED as written** (the code says it about itself; reachable only on multi-page
layouts with portals).

`AutonomyBuilder` now always emits x/y but drops the per-page 1800-unit stacking when no page list
is supplied - i.e. for the running layout (src/org/traincontrol/automationui/AutonomyBuilder.java:918-922).
Two tiles at the same grid position on different pages then share coordinates, and
`Layout.sideTowards` (Layout.java:5443-5457) between portal neighbours returns null (dx=dy=0) or a
side computed from meaningless deltas. Readers: the first hop of the tail walk (arrivedFrom
matching, Layout.java:5384), the arrival write (Layout.java:6438), and `ArrivalSidePrompt.sidesOf`.
The builder comment flags the portal case as the reason it is "written down"; it belongs in
`open-questions.md`'s limits so the next reviewer does not file it as a bug - or the page index
should be derived from the store's page list, which the builder has.

## REG7-C2 - The tail walk runs inside every path check

**Severity C. PLAUSIBLE** (performance, not correctness; not measured).

`checkPath` recomputes `edgesCoveredByStandingTrains()` on every call
(src/org/traincontrol/automation/Layout.java:2414), the method is `synchronized`, and each hop of
each standing train scans **all** edges (`getNeighborsAndIncoming`, Layout.java:2089). `pickPath`
enumerates every route to every destination until exhausted, calling `isPathClear` per candidate,
on each locomotive's own thread. On a layout with many measured trains this multiplies to thousands
of full-edge scans per dispatch decision under the layout lock. A per-decision (or
invalidate-on-move) cache would keep the one-statement-of-the-rule property without the cost.

## REG7-C3 - A train on a closed square now "reaches" everything

**Severity C. CONFIRMED behaviour change; the deliberateness is asserted only by its own comment.**

`GraphReducer.reachableTiles` used to return nothing for a start inside `closed` ("nothing can
leave it either"); commit `36734d33` removed that clause (the removal and its justification are at
src/org/traincontrol/automationui/GraphReducer.java:796-804: "The START is still exempt"). The start
exemption is Adam's rule *for the manual tier*. The findings panel and `AutonomyChecks` ask the
**auto** question - and while autonomy runs, `Layout.isPathClear` refuses any edge with an inactive
endpoint, start included (Layout.java:2243), so an autonomous run cannot move that train at all.
The checks previously said so; they now report full reachability. The sibling `findPath` overstatement
is argued in its javadoc as "the safe direction" (DIR-C10), and the same argument covers this - but
V31-C3 established the opposite behaviour a day earlier, and neither `behaviour.md` nor
`open-questions.md` records the reversal. One sentence in the limits section would close this.

---

## REG7-D1 - Checked and found clean

Recorded so the reader knows the coverage, per the README.

- **Terminus journeys and the reversal policy.** `forJourney` returns `KEEP_DIRECTION` unprompted
  for terminus-ending journeys (ManualReversalPrompt.java:80-90) and `shouldReverseAt` ignores the
  policy when the destination is a terminus (Layout.java:5239): MT-245 (backing into a terminus)
  is preserved at both layers. Consistent, no drift.
- **Autonomy and Return Home semantics through the new overload.** Both four-argument callers -
  the dispatch loop (Layout.java:3734) and the timetable that Return Home loads (Layout.java:4833) -
  resolve to `ALWAYS_REVERSE`, which `shouldReverseAt` short-circuits to the pre-policy
  `current.isReversing()` (Layout.java:5219). Neither tier can prompt; turning behaviour unchanged.
- **The emergency-stop carve-out** is present at both route-conflict doors again
  (`askable = !auto && !this.hasEmergencyStop() && ...`,
  src/org/traincontrol/marklin/MarklinRoute.java:3919 region), matching the pre-route screen -
  DIR-A3's repair held.
- **`CANCEL_ROUTE` cleanup.** The mid-route `return` is inside the `try` whose `finally` runs
  `stopExecuting` (MarklinRoute.java:578-581); a cancelled route cannot wedge the re-entrancy guard.
- **The reversal stop returns the point's own speed** (multiplier reapplied at Layout.java:6130
  region), matching the block above it - REG6-B3's repair holds.
- **`mayReverseAt` uses the builder-emitted block**, which is the tile key
  (AutonomyBuilder.java:844: "The TILE, not the s88"), so block-sibling matching cannot cross
  squares that merely share a sensor.
- **`enteredS88` is one reader for validation and save** (RouteEditorFrame.java:2309 and 2597);
  blank-means-0 cannot diverge between the check and the save. Negative and non-numeric input still
  refuse.
- **Message key rename swept clean**: no reference to `autolayout.errorInactiveStationInAutoRun`
  survives; the new keys (`errorInactiveStation`, the reversal, arrival-side, path-type, covered
  track and arrived-from families) are present in all eight bundles with `\uXXXX` escapes (en, da,
  de, es, fr verified line-level; it, nl, pl verified by identical diff shape).
- **`lastSeenDirection` and the by-name repairs**: rename moves the entry, delete evicts it
  (TrainControlUI.java:4024-4048) - the by-name-state trap was closed at both View doors.
- **`writePointProperty(null)` removes the key** (AutonomySession.java:5437), so clearing
  `arrivedFrom` from the menu genuinely forgets it and the "Not known" tick reads correctly.
- **`roomAfterTheLastSwitch` and `measuredRoomToReverseInto`** changes refuse-only: the segment
  bound over-states real room and the measured-sum under-states it; on fully measured stretches the
  answers are unchanged. Matches Adam's 2026-09-06 rulings as quoted in `behaviour.md` 5b.
- **`AddLocomotive` name gate** uses the shared `RouteCommand.isNameUsable` predicate - the fifth
  door now asks the same question as the four rename doors.
- **`parseAuto`'s `arrivedFrom` read** is guarded by `has()` and resolves points created in the
  same parse; `optString(.., null)` handles JSON null.
- **The departure-side clear** (Layout.java:6443-6447) guards `start != arrived`, so a circular
  journey does not erase the arrival it just wrote.
- **Path Type radio**: reporting only - `stationsAutonomyWillNotChoose` now carries both halves of
  the runtime rule (`isMustTurnAround` added, AutonomySession.java:3355 region), and the note is
  appended to, not substituted for, the drawn route. Matches `behaviour.md` 7.
- **The route-conflict cancel log line** (`route.cancelledByOperator`) is emitted at both doors.

## Coverage

- **2026-09-05 19:00 .. 2026-09-06 HEAD** (the reversal / length / blocking / `arrivedFrom` work,
  commits `1babe733`..`65bd3161`): full diff read hunk by hunk; every finding above verified
  against HEAD source, not patch text. This is where all findings come from, which matches where
  the newest, least-validated behaviour lives.
- **2026-08-30 .. 2026-09-05**: hunk-mapped and sampled (`Edge` in full; `HomeStaging`,
  `MarklinRoute`, `RouteCommand`, `Layout` hunks around the shared-primitive changes). This window
  was covered in-week by its own validator rounds (`f976306f`, `875edb92`, `357cdc40`, `c1603ccb`
  et al.) and its findings are dispositioned in `open-questions.md`; I did not re-derive them. A
  defect in that window that those rounds also missed would survive this pass.
- **Not done, by instruction:** no build, no test run, no execution of any scenario. Confidence
  labels reflect that: CONFIRMED means every link of the claim was read in the enforcing code;
  PLAUSIBLE means at least one link (usually reachability on a real layout shape) rests on
  reasoning about graph topology rather than a trace.
