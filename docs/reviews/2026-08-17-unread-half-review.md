# The unread half: TrainControlUI, the small GUI classes, and util/base spot checks

**Prefix for citing this document: `UH`.**

Reviewed at `cc57284` on `autonomy-diagram-r0`, 2026-08-17. Nothing was compiled or run. Every
finding below is open.

## Scope and method

[2026-08-17-whole-project-review.md](2026-08-17-whole-project-review.md) (`WP`) named its own coverage
gap: roughly 10,000 of `TrainControlUI.java`'s 18,000 lines unread, a dozen GUI classes only
grep-skimmed, and most of `util/`. This pass exists to close it, and was told not to re-report
anything already in `WP`, including its D section.

| Stream | Result |
|---|---|
| `TrainControlUI` 14083-18051 and the function-tab block 4442-4870, read line by line; `initComponents` scanned | the bulk of the findings |
| The sixteen small GUI classes, read line by line by a sub-reviewer | reported; two most consequential re-verified in source by the lead |
| `util/` and `base/` | **the sub-reviewer returned nothing** - what is recorded is targeted verification only |

**Provenance is marked per finding**, because a third of them are inherited rather than independently
confirmed, and a reader deciding what to act on needs to know which. Likewise each finding is tagged
**released** (present at `8cee6a3`, so live in v2.8.1) or **branch-only**, so a second backport can be
scoped without re-deriving it.

**No A findings. No suspected compile errors.**

---

## B - Medium

| | Finding | Provenance | Applies to |
|---|---|---|---|
| B1 | A comma in a locomotive name breaks the route text format; one door guards, two do not | Verified by lead | Released |
| B2 | A backward clock step silently drops s88 feedback transitions | Verified by lead | Released |
| B3 | Parentheses in a locomotive name make the condition parser shred it | Verified by lead | Released |
| B4 | `AutoLocomotiveStatus.updateState` mutates Swing on a raw background thread | Premise verified by lead, body inherited | Released |
| B5 | Double-click dispatch can execute a different path than the one clicked | Structural core verified by lead | Released |
| B6 | Double-clicking below a short path list dispatches the last path | Independently confirmed by lead | Released |
| B7 | `AutoJSONExport` realizes a file chooser on a raw thread beside a second modal dialog | Caller half verified by lead | Released |

### B1. A comma in a locomotive name

The serialized command format is `prefix,name,value`, parsed by a naive `split(",")`. The route
editor's **add-command** door refuses a comma-containing name with a dedicated message
(`route.ui.errorCommaLoc`) - so the hazard is known and was guarded once. Two doors are not guarded:
the **add-autoloc-condition** action inserts `autoloc,<name>,<s88>` with no check, and the
**locomotive rename** dialog validates only empty, length and duplicate before sweeping the new name
into every existing route command and condition. Names synced from the Central Station arrive
verbatim as well.

Rename "BR 103" to "BR 103, 001" while a route commands its speed, then edit that route: the text
round-trip re-parses every line on save, and `locspeed,BR 103, 001,47` silently becomes a command for
a different name at speed 1 with delay 47 - saved without complaint whenever the tail parses as an
integer. When it does not, the parse throws and the save is refused with a generic error, blocking
edits to that route until somebody hand-fixes it. Stored objects stay correct throughout; the JSON
persistence is structured, which is what bounds this to the text round-trip.

### B2. A backward clock step drops feedback

`base/Feedback.java` - `readyForUpdate` returns false when `time - lastEvent < IGNORE_SUB_INTERVAL`,
and that constant is **0**. The only condition under which the gate can ever reject anything is wall
clock time having moved backwards past the last event - and `MarklinFeedback` gates every incoming
s88 update on it using `System.currentTimeMillis()`, which is not monotonic.

NTP steps the clock back thirty seconds while autonomy runs, and every sensor that fired inside that
window ignores its next transitions until the clock catches up. `_setState` records only changes, so
the model holds the wrong occupancy and a driving thread waiting on an arrival misses its trigger.
The consequence on the layout could be severe; the trigger is environmental and rare, which is the
only reason this is B. `WP` recorded the same constant as "currently a no-op, so no events can be
swallowed" - true for the forward case, and this is the case it did not ask about.

### B3. Parentheses shred a condition

`base/NodeExpression.java` - `preprocessText` unconditionally rewrites `(` and `)` into line breaks.
The comment directly above it records fixing precisely this class of bug for AND and OR, with `\b`
anchors, after locomotive names like NORD and MOTOR were cut in half. Parentheses were not part of the
question being asked.

`test/lokomotive.cs2` contains `.name=SBB 460 (2)`. Add an autoloc condition for it - through the
unguarded door in `B1` - and press save: the line is shredded into four tokens, the parse throws, and
a generic "invalid expression" dialog appears with no hint why. The feature is simply unusable for
such locomotives. Loud rather than silent, hence B.

### B4-B7, in brief

`repaintAutoLocListLite` wraps its per-panel `updateState` loop in a raw `new Thread` inside an
`invokeLater`, so every arrival and departure callback runs `setText`/`setModel`/`setVisible` and
reassigns the panel's `paths` list off the EDT (**B4**). That reassignment is what makes **B5** reachable:
the dispatch index is computed on the EDT and `paths.get(index)` re-read later on a spawned thread, so
a list that shrank in between dispatches a real, locked movement to a destination the user did not
choose. **B6** needs no race at all - the path list has no cell-bounds guard on `locationToIndex`, while
its sibling `GraphLocExclude` implements exactly that guard with a comment naming the trap, so a
double-click in the empty space below a short list executes the last row. **B7** is `WP-B9`'s defect
class again, with a new wrinkle: a file chooser and a message dialog realized from two different raw
threads can strand the popup open.

B4 partially contradicts `WP`'s D-section claim that model-callback paths all marshal to the EDT.

---

## C - Low

**C1.** `getAutoLayout()` creates a `Layout` as a side effect, inside three call sites written to
avoid exactly that - `refreshReturnHomeButton`'s own `layout == null` branch is therefore dead, and
the rule is stated explicitly twenty lines above it. Every current caller is gated, so this is a trap
for the next one rather than behaviour today. [Verified by lead. Released.]

**C2.** The graph UI callback mutates Swing and GraphStream attribute maps from driving threads under
`synchronized(graph)`, while the EDT mutates the same maps under the UI-instance monitor - two locks,
no mutual exclusion. The escalation that keeps it worth fixing: `callback.apply` has no try/catch, and
it fires after `activeLocomotives.put`, so an exception there kills the driving thread and strands the
entry in the wedged state that only a reload clears. A defensive try/catch is cheap. [Verified by
lead. Released.]

**C3.** `repaintLoc` drops repaint requests while a previous one is pending, and the surviving repaint
filters on its own locomotive set - so in a burst, a dropped locomotive's sliders and function lamps
stay stale until its next message. Display only; model state is correct and it self-heals. [Verified
by lead. Released.]

**C4.** Further off-EDT Swing, beyond `B7`: the conditional-route confirmation inside
`startAutonomyActionPerformed` is unmarshalled while every other dialog in the same method is wrapped;
`backwardLoc`/`forwardLoc` set selection state inside raw threads; the icon pipeline sets icons from
its executor; two file choosers open on worker threads. This whole class is already on the project's
deferred-optimisations list, and the decision is probably better taken for the class than per site.
[Verified by lead. Released.]

**C5.** Cosmetics: a hardcoded English "Pending Start +…s"; a literal `F20-F31` tab title; the
duplicate-page default name mixing a hardcoded `" copy"` with the localized suffix in its collision
loop; and `validateLayoutName` filtering only on key events, so a paste generating no key event
bypasses it. [Verified by lead. Released.]

**C6.** `Conversion.getFolderHash` hashes the install path with the platform default charset, so for a
non-ASCII path a default-charset change moves every per-folder preference key and the app appears
factory-reset. Narrow and environmental. [Verified by lead. Released.]

**C7.** `RouteCommand.toJSON` reaches into org.json's private `map` field by reflection, twice, to
force ordered output. Works with the bundled library; breaks on any upgrade that renames the field.
[Verified as written by lead; consumers not exhaustively traced. Released.]

**C8-C13, inherited and not independently confirmed.** TOCTOU null dereferences in `updateState`,
reachable only because of `B4` and self-healing; `DiagramMonitorDriver.clear()`'s unsynchronized
`invalidate()` letting a concurrent tick overwrite a wipe so overlays stay blank until a train moves
(**the only branch-only finding in this document**); `LayoutEditorRightclickMenu` re-adding Copy when
Rotate is omitted, harmless today by a re-parenting accident; the create-point right-click action
missing the at-click-time running guard, twin of `WP-C3`; `About` leaving `Desktop.getDesktop()`
unguarded on unsupported platforms; and `AutoJSONExport` NPEing on a file saved at a filesystem root,
killing its thread with the Save button left disabled.

---

## D - Not defects, and checks that came back clean

- **`Util.downloadFile` stages through a `.part` file** and renames only on completion, so the update
  handler's `exists()` skip cannot mistake an interrupted download for a finished one. This was gone
  looking for specifically; it is guarded.
- **`TimetablePath.hashCode` includes `executionTime` and `secondsToNext`**, so the hash-based dirty
  check does see execution updates - it looks like the identity-hash staleness trap and is not. Its
  setter takes `Math.abs`, absorbing negative delay input.
- **The timetable delete, delay and restart handlers are all busy-guarded**, and sorting is disabled on
  every column so view and model indices agree.
- **`getRouteId` returns 0 rather than null** for an unknown name, so the hand-written renderer inside
  the generated block cannot NPE.
- **The legacy editor's `Runtime.exec`** with an embedded quoted path looks broken and reassembles
  correctly under the only platform the handler allows.
- **`initComponents` scanned:** 26 sliders wired symmetrically, uniform listener registration, and the
  only hand-supplied code is the renderer above. `WP-A2`'s fix is confirmed present.
- **Route and condition JSON persistence is structured**, which is what bounds `B1` and `B3` to text
  round-trips rather than data loss at rest.
- Inherited: the third dispatch-at-preferred-speed site is covered by the central fix; a suspected A in
  `LocomotiveSelectorItem` was closed by a startup fallback; `UsageHistogram`, `GraphLocExclude`, the
  four `RightClick*` menus and `HandScrollListener` came back clean.

---

## What this pass missed

**The `util/`-and-`base/` sub-reviewer returned nothing**, so coverage there is only the methods the
lead verified while chasing other findings: `Feedback.readyForUpdate`/`_setState`, `RouteCommand`'s
locomotive branches and `toJSON`, `NodeExpression.preprocessText`/`fromJSON`, `Util.downloadFile`,
`Conversion.getFolderHash`, `TimetablePath`. **Not read at all:** the rest of `Util.java` including
most of its branch-new lines, `ImageUtil`, `I18n`, `Conversion` beyond one method, `Feedback`'s
remaining body, `RouteCommand`'s other ~700 lines, `NodeExpression`'s parser body, the four node
subclasses, `LocomotiveNotes`, `RenameProposals`, `udp/CANMessage`, `CustomActionEvent`. **This is the
gap to close first.**

`TrainControlUI` 1400-2700 is branch-new diagram-autonomy machinery, excluded as separately audited;
`initComponents` was scanned rather than read. From the GUI sub-review's own list, `LocomotiveSelector`,
`GraphViewer`, `LayoutEditor`, `GraphRightClickPointMenu` and `HomeLocomotiveMenu` were read only for
reachability, and `DiagramMonitor.compute` was not checked against real tile geometry.

Nothing was compiled or run; every claim rests on reading.
