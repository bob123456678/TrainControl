# The week reviewed — 2026-08-28

**Status:** open

**Prefix:** `WK`. Cite findings from here as `WK-B1`, `WK-C2` and so on.

**What was reviewed:** the last seven days of commits, `d2628526..eac0e392` (HEAD `eac0e392`,
branch `autonomy-diagram-r0`) — 312 commits, roughly 26,000 insertions across 77 source files.
Reviewed 2026-08-28.

**How the scope was cut.** A week this size cannot be re-read line by line, and most of it has
already been reviewed several times: the 08-21 through 08-25 stretch is covered by the eleven
documents dated 2026-08-22 to 2026-08-25 in this folder, and the 08-26/08-27 stretch carries five
in-stream reviewer commits (`2ec8ef5d`, `caec4916`, `ca0270f3`, `3a52ec14`'s findings half,
`7026c803`) plus `dd87f6bf` and `ff6368bb`, which are reviewer passes over the repairs themselves.
What has had **no** independent eyes at all is the tail: `7210d390`, `ff6368bb`, `c7d46a89`,
`720e62e9` and `eac0e392`, all of 2026-08-28. Those five were read in full, with their call graphs
traced; the 08-26/08-27 feature commits were sampled (the routing-semantics ones in depth, the
drawing-only ones lightly); the earlier week was spot-checked only where the newest commits touch it.

**What this pass could not do.** This was a read-only review: no tests were run, no builds made, and
nothing was executed. This folder's own calibration record says execution finds what reading clears
(2026-08-25: nine of nine defects needed running something), so the findings below that say "needs
hands-on verification" mean exactly that — and the absence of an A finding here is weaker evidence
than it would be from a pass that ran the battery. Hands-on steps are suggested inline rather than
added to `../manual-tests/tests.md`, because this pass was constrained to create only this document.

---

## Summary

The week's discipline is visible: nearly every feature commit is followed within hours by a reviewer
commit, and three of the four repair-rounds on 08-28 were themselves found broken within the hour.
The two findings that matter both live in the last, unreviewed day:

- **WK-B1**: the tail-clearing "guess" that `ff6368bb` deliberately kept for *clearing* weakens the
  AU-A2 route-accessory guard for **turnouts**, not just signals — the commit's own safety argument
  ("being early costs a signal") describes only half of what `cleared` gates.
- **WK-B2**: the new start-up splash (`FR-041`) is shown before the two constructors most likely to
  throw at start-up, is always-on-top, and nothing closes it on that path — so the one start-up
  failure with a plain-English message (`error.alreadyRunning`) now gets that message shown
  underneath a splash that covers it.

---

## A — high

Wrong behaviour on the layout, or data silently lost.

None recorded. WK-B1 below is the candidate: I have kept it at B because the harmful event needs a
route conflict to actually occur in a window, not merely the guard to be down — but if Adam reads
the exposure as routine on his railway, it should be re-severitied in place per this folder's rules.

---

## B — medium

Incorrect results, or crashes in specific configurations.

| | Finding | Disposition |
|---|---|---|
| **B1** | The tail-clear guess drops turnouts out of the route-refusal guard while the train may still be on them | open |
| **B2** | The start-up splash is leaked over, and obscures, the start-up failure dialog | open |

### WK-B1 — the tail-clear guess drops turnouts out of the route-refusal guard

`Layout.java` ~4892–4915 (the clear on the guess), `Layout.getActiveAccs()` 729–783,
`MarklinRoute.heldReason()` 393–452. Introduced by the `453a3ef4` → `dd87f6bf` → `ff6368bb` tail
chain; the specific split is `ff6368bb` (2026-08-28).

`ff6368bb` split the tail bookkeeping into two standards of proof, correctly refusing to **unlock**
on the guess about unmeasured track. Its justification for still **clearing** on the guess — in the
commit message and in the `tailHasProvablyPassed` javadoc at `Layout.java:3395` — is that clearing
"may only decide whether an edge is reported CLEAR - a signal moving behind a train that has gone
by... which costs nothing when it is early."

That is not all `cleared` decides. `clearedEdges` has exactly one reader, `getActiveAccs()`
(line 771: `if (cleared != null && cleared.contains(e)) continue;`), and `getActiveAccs()` is the
set `MarklinRoute.heldReason` consults **per command, immediately before sending it** — the AU-A2
guard, whose own comment says it covers "all three doors including the s88 trigger with nobody
present", and whose refusal branch is `route.refusedAccessoryOnActivePath`: *turnouts* on active
paths, not signals. The diagram's tile-click warning (`LayoutLabel.java:386`) reads the same set.

So on the commit's own worked example — edges `[100, 100, 0]`, train of 250 — the moment the head
finishes the unmeasured third edge, edge 0 is `cleared` with up to 150 of the train still standing
on it. From that moment a route (hand-fired, or s88-triggered with nobody present) whose commands
include a turnout on edge 0 is **not refused and not warned about**, in atomic mode too — atomic
mode holds the lock, but the `cleared` test skips the edge before `isLockHeld` matters. The comment
inside `getActiveAccs()` (line 757–761) still asserts "a turnout under the middle of a train is
still refused", with the only stated exception being a wholly unmeasured railway; the guess case is
a third exception that sentence does not admit. This is the change-contradicts-its-own-comment
shape, one commit after the same comment was corrected for the previous version of the same fault.

**Reachability, honestly.** For harm, three things must coincide: a mixed-length path where the
just-finished edge is unmeasured while `behind` is still short of the train; a route whose
accessory list includes a turnout on the cleared edge; and that route firing in the window before
the tail actually passes. On Adam's railway (per `Layout.java:3455`: sixty unmeasured edges, thirty
measured, trains of two to four units) the window is short whenever measured edge lengths dwarf
train lengths — one measured edge behind the entry gives proof and closes it. I could not run
anything to measure how often the guess branch fires on his real graph. This does happen at the
guard level; whether it ever becomes a thrown turnout under a tail depends on route/path overlap.

**What a fix should probably look like.** The looser rule is right for what Adam asked for
(signals behind the train); the guard is wrong to treat "cleared" as one bit. Either `heldReason`
distinguishes proof-cleared from guess-cleared, or `clearedEdges` records which standard admitted
the edge. Note `proxy-gains-a-second-job` is this folder's own name for the pattern.

**Suggested hands-on check:** on the test layout, a path over a measured edge followed by an
unmeasured one, a train longer than the measured edge, and a route that throws the first edge's
turnout — fired while the train straddles it. The refusal dialog should appear and today will not.

### WK-B2 — the start-up splash is leaked over, and obscures, the start-up failure dialog

`MarklinControlStation.java:3745` (the show), `3749–3753` (the gap), `3834`/`3843` (the only
closes), `StartupSplash.java` (`setAlwaysOnTop(true)`, `setLocationRelativeTo(null)`),
`TrainControl.java` main's catch. Introduced by `eac0e392` (FR-041).

The splash is shown, then `new NetworkProxy(InetAddress.getByName(initIP))` and
`new MarklinControlStation(...)` run, and only then does the window-build block containing both
`closeIfShown` calls begin. Both constructors throw on precisely the failures FR-041 exists to
cover: an unknown host, and — per `TrainControl.isPortInUse`'s own javadoc — "the bind happens
inside the network proxy's constructor", which is the second-copy-of-TrainControl case. The
exception propagates out of `init` past both closes; `TrainControl.main` catches it and shows a
`JOptionPane` centred on the screen — where an always-on-top splash saying "Connecting..." is
already standing, also centred. The error the catch was specifically taught to say in plain English
is now shown underneath a window that covers it and cannot be dismissed; `System.exit(1)` waits on
a dialog the user may not be able to see, and the process looks hung mid-connect, which is the
exact symptom the splash was built to remove.

Not verified by running (needs a screen and a failing start); the geometry argument is that both
windows are centred via `setLocationRelativeTo(null)` and comparable in size, and always-on-top
wins regardless of z-order games. Even a partial overlap shows "Connecting..." over a failure,
which is a wrong statement on screen.

**Fix shape:** wrap from the show to the end of `init` in try/finally closing the splash, or move
the show below the two constructors (losing coverage of the connect stretch), or both — close in a
finally *and* keep the show where it is. The `latch.await()` `InterruptedException` path leaks it
the same way and the same finally covers it.

---

## C — low

Cosmetic, dead code, or narrow edge cases.

| | Finding | Disposition |
|---|---|---|
| **C1** | First-launch detection can silently re-point the layout preference at the demo layout | open |
| **C2** | `refreshDataSourceLabel` asks the model directly, against the same commit's stored-answer doctrine | open |
| **C3** | The page-sized spinner may centre its hourglass outside the visible viewport | open, needs hands-on verification |

### WK-C1 — first-launch detection can silently re-point the layout preference

`TrainControlUI.isFirstLaunch()` (working-directory relative files, consistent with where
`restoreState`/`saveState` actually keep the databases) and the
`createAndApplyEmptyLayout(DEMO_LAYOUT_OUTPUT_PATH, !isFirstLaunch())` call, `eac0e392`.

The two database files are per-working-directory; `LAYOUT_OVERRIDE_PATH_PREF` is per-user. Launch
the jar from a fresh folder — which is how a non-technical user installs a new version — and it is
a "first launch" even though the user's preference still names their real layout folder. If that
folder loads, the list is non-empty and nothing happens. If it does not load (drive unplugged,
share offline, folder moved), the layout list is empty, the old code **asked** before creating a
demo layout — showing the path it would use — and the new code creates it silently and overwrites
`LAYOUT_OVERRIDE_PATH_PREF` with the demo path inside `createAndApplyEmptyLayout`. Nothing on disk
is destroyed and Choose Local Data Folder recovers it, so this is C: but the prompt that was
removed was the one moment that combination of accidents was visible.

### WK-C2 — `refreshDataSourceLabel` asks the model directly

`TrainControlUI.refreshDataSourceLabel()`, `eac0e392`. The same commit's `layoutLoaded` javadoc
argues at length that "working it out on demand is the defect" and routes sixteen former
`getLayoutList().isEmpty()` reads through the stored answer — and then this new method, added in
the same commit, reads `this.model.getLayoutList().isEmpty()` directly. Behaviourally near-harmless
(it runs at menu-open on the EDT; a mid-sync swap could transiently label the source None while the
items beside it enable per the stored flag), but it is the seventeenth site of the pattern the
commit says it was eliminating, and the next OB-127-shaped hunt will have to notice it separately.

### WK-C3 — the page-sized spinner may centre its hourglass outside the viewport

`LayoutGrid.java:1494` (`setPreferredSize(maxWidth, maxHeight)`), `LoadingSpinner.paintComponent`
(glass centred at `getWidth()/2, getHeight()/2`), `eac0e392` (OB-129). The diagram lives in the
`LayoutArea` JScrollPane. The old 400-cap put the spinner — however badly aligned — inside the
top-left screenful; the new component is the size of the whole page, and the glass is drawn at the
component's centre. On a page taller or wider than the viewport, scrolled to the top, that centre
is partly or wholly outside the visible area, and the wait shows as a blank page again — the
original OB-129 complaint by another route. Whether Adam's pages exceed the viewport at his usual
zoom I cannot know from here; if they all fit, this is a non-finding. **Suggested hands-on check:**
open a page at a zoom where it needs scrolling, force a cold rebuild (change tile size), and watch
where the hourglass lands.

---

## D — not defects

Things that looked wrong and are not, and checks that came back clean.

| | Check | Result |
|---|---|---|
| **D1** | OB-127's five unguarded `getLocalLayoutPath` readers | clean — unreachable as claimed |
| **D2** | `findPath` vs `reachableTiles` agreement after `ff6368bb` | clean |
| **D3** | The `cancelled[]` two-slot contract | clean at both call sites |
| **D4** | New message keys, eight bundles | clean — ASCII only, key parity via existing tests |
| **D5** | `tailHasProvablyPassed` degenerate cases | clean |
| **D6** | `LoadingSpinner` timer lifecycle | clean |
| **D7** | `isFirstLaunch` file locations | clean — matches where the databases actually live |

- **D1** — `720e62e9` claims the five callers that pass `getLocalLayoutPath()` into
  `writeLayoutIndex`/`new File(...)` unguarded are unreachable when the layout is not local. Traced:
  all five sit behind `settleAbsentPages(...)` returning non-null, and its new `!isLocalLayout()`
  refusal returns null first. The three hand-guarded callers (`canUseAutonomy`,
  `getAutonomySession`, the rename repair) all handle the new null. Holds.
- **D2** — the `ff6368bb` rework of `GraphReducer` was checked for a re-opened disagreement: both
  walks now key state by (tile, entry-side), both refuse only the *stopping*, and a destination
  first reached by a barred side is reachable again by an open side under a different key. They
  agree, including on a destination whose every arrival side is barred.
- **D3** — `ff6368bb` removed the `cancelled.length > 1` silencer; both call sites now allocate
  `{false, false}` and the write at `TrainControlUI.java:22038` is unconditional. Verified at HEAD.
- **D4** — all eight `messages*.properties` at HEAD contain no bytes outside printable ASCII, so the
  Java 8 ISO-8859-1 reader cannot mojibake the week's new strings.
- **D5** — a measured path with `trainLength` null unlocks immediately (length 0, `behind >= 0`),
  which is the pre-existing behaviour, and `behind >= length` remains a valid proof across
  interleaved unmeasured edges because unmeasured edges only under-count `behind`.
- **D6** — the elapsed-time rework keeps `timer.stop()` in `removeNotify` and resets `startedAt` in
  `addNotify`; no leak, no mid-cycle jump on re-show.
- **D7** — `isFirstLaunch` reads `LocDB.data`/`UIState.data` as working-directory-relative files;
  so does everything else (`restoreState(MarklinControlStation.DATA_FILE_NAME)` etc.). The check
  asks `exists()` and not readability, which is the right side of the line `restoreState` draws —
  see WK-C1 for the working-directory caveat that remains.

---

## What was sampled but not deep-read

For the record of what this pass does *not* vouch for: the 08-26/08-27 drawing and caption work
(`afcf4cdc`, `95ec0a24`, `4ed53461`, `bda5914d`, `831ddcd8`, the caption rotation in `42d66b31`),
FR-033's fifty-page cap, FR-036's page walking, and the crop-view arithmetic inside
`LocIconCropDialog.startAtCover` (the restore path was traced for contract and lifecycle — clone-in,
copy-out only on OK, no view stored against the crop-the-crop branch — but the clamp geometry was
not re-derived). The travel-restrictions default (`98481418`) was read and is drawing-only with its
editor-isolation pinned by test. `4d315aae` is Adam's own railway data and was not touched or
evaluated beyond confirming this review wrote nothing anywhere near it.
