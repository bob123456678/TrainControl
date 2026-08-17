# Autonomy on the track diagram - review cycle

**Prefix for citing this document: `AD`.**

**Reviewed:** branch `autonomy-diagram-r0`, from the branch point `8cee6a3` through `ddff66e`
("Station captions become autonomy's, not the diagram's") and the fixes committed on top of it.
**Dates:** 2026-08-16 and 2026-08-17. **Code state:** every finding below marked fixed was fixed on the
branch before this document was written; the branch has not been merged.

The feature under review moves autonomy configuration off the GraphStream graph window and onto the
existing Swing track diagram, and in the same cycle changes two things about the model: every square is
split by the side trains arrive at, so the graph can express which way a train faces, and station
captions stop being text in the layout file and become part of the autonomy setup.

## Method

Eleven reviewer passes, run in three rounds, each pass scoped to one subsystem and asked for defects
with a concrete failure scenario rather than for opinions:

| Round | Passes | Scope |
|---|---|---|
| 1 | 5 | derivation core; model and persistence; setup UI; diagram integration; test audit |
| 2 | 2 | re-read of round 1's dispositions, model side and Swing side |
| 3 | 3 | the caption rework: model and file layer; Swing; test audit |

Round 2 exists because round 1's dispositions were written quickly and three of them were wrong -
`AD-A6`, `AD-B4` and `AD-C2` below are all defects introduced *by* a round 1 fix. That is the argument
for the round: a disposition is a change like any other and wants reading by somebody who did not write
it.

The test audit was asked a different question from the others - not "is there enough coverage" but
"does each test establish what its name claims". It found more than the coverage question would have.

---

## A - High. Wrong behaviour on the layout, or data silently lost

| | Finding | Status |
|---|---|---|
| A1 | Captions never persisted: `KNOWN_SHARED` omitted them, so every save wrote the stale copy over them | Fixed |
| A2 | Autonomy rewrote layout files and deleted everything the parser could not model | Fixed |
| A3 | Caption migration corrupted signal types and rotations | Fixed |
| A4 | `reconcile` deleted captions on squares that hold no component - which is most of them | Fixed |
| A5 | A failed store load emptied the setup, then a save wrote the empty one to disk | Fixed |
| A6 | Autonomy menu used while an editor was open committed its unsaved edits and broke the main diagram | Fixed |
| A7 | Capture deleted every configuration entry on a page that was temporarily excluded | Fixed |
| A8 | "Exit without saving" discarded nothing | Fixed |
| A9 | Migration destroyed labels naming stations on excluded pages | Fixed |

**A1.** `AutonomyCompanionStore.KNOWN_SHARED` lists the shared fields this version understands; anything
absent is treated as a newer version's work, kept aside on load and written back *last* on save so an
older TrainControl cannot delete it. `"captions"` was missing from that list. So every load copied the
captions into `unknownSharedFields`, and every save wrote the real captions and then overwrote them with
the copy read at load. The migration recorded the captions, saved, had them clobbered, and then stripped
the labels they came from - both copies gone, which is precisely the loss the migration's write ordering
was designed to prevent, defeated one layer down. Found by the round 3 test audit, not by the model
pass, because it is invisible unless you follow the data to disk.

**A2.** `LayoutDiagram.saveChanges` regenerates a page from the model, and `CS2File.getComponentType`
returns null for any `typ` it does not recognise - so an unrecognised element never entered the model
and was absent from the file afterwards. Autonomy called `saveChanges` whenever a station was named or
renamed. Fixed by keeping unmodelled elements, blocks and keys verbatim and re-emitting them; then made
moot for autonomy by A2's successor work, which took captions out of the layout file entirely.

**A3.** Even for elements it *does* model, the round trip was lossy: `getComponentType` is many-to-one
(fifteen signal words, four lamp words) while `getTypeString` writes one canonical word, and the parser
turns any type whose word contains `_f_` by a quarter to correct the artwork. Writing back `signal`
therefore both collapsed the variant and baked in the correction, so a semaphore signal moved a quarter
turn on every save - on the Central Station too. Components now record the file's own word and rotation
and write those back while the type and orientation are unedited.

**A4.** `reconcile` dropped any caption whose own square held no component. But captions are deliberately
placed on blank squares - that is the most readable place beside a platform - and the migration captions
a square whose label it then empties, which removes that square from the file. So the next save deleted
both the captions the user had just placed and every caption the migration had just created. A caption
now goes only when the station it is about is gone, or its page is.

**A5.** `load()` called `clear()` before reading. A read that failed - a sync lock on a OneDrive folder
is the ordinary case here - left a live, empty store, reported as though nothing had happened. One save
later the emptied setup was on disk. Now it reads and parses before clearing.

**A6.** The Autonomy menu stayed operable while an editor held the shared `LayoutDiagram` with its edit
flag set. Ticking a page there called `session.save()`, committing the editor's unsaved edits so its
Cancel had nothing to take back, and `repaintLayout()`, which rebuilt the *main* grid in edit mode - so
every main-window tile cast its parent to `LayoutEditor` and the first click threw. Introduced by the
round 1 fix that moved page exclusion into the menu.

**A7.** `captureFromLayout` pruned point data by asking the reduction which squares still exist, and the
reduction is built without excluded pages. Excluding a page and running autonomy therefore deleted every
placement, marking and caption on it, permanently. `save()` documents and defends the opposite invariant
twenty lines away.

**A8.** Edits go straight into the live configuration; there was no copy to go back to. The dialog asked,
the user answered, and the edits stayed - drawn on the diagram and written by the next save from
anywhere. Now `discardEdits()` re-reads the store.

**A9.** The migration matched label names against the reduction, which omits excluded pages - so a label
naming a station on one resolved to nothing, and was stripped anyway. Now matched against every named
square, and a label that resolves to nothing is left where it is.

---

## B - Medium. Incorrect results, or crashes in specific configurations

| | Finding | Status |
|---|---|---|
| B1 | The graph could not say which way a train faced, so journeys reversed where no train can | Fixed |
| B2 | The reducer walk dropped any run crossing one square twice | Fixed |
| B3 | `findPath` compared sides, not routes, so it returned non-contiguous paths at a double curve | Fixed |
| B4 | Findings count and editor list disagreed: graph problems listed twice, notices counted as warnings | Fixed |
| B5 | A locomotive was emitted on every copy of a split square | Fixed |
| B6 | Split stations overwrote their own label with `[---]` | Fixed |
| B7 | Right-clicking a sensor in a popup window resolved the page from the main window | Fixed |
| B8 | Two editors could open on one diagram and unset each other's edit flag | Fixed |
| B9 | Jump-to-square from a finding was a guaranteed no-op | Fixed |
| B10 | The banner stayed on "cannot run yet" after the problem was fixed | Fixed |
| B11 | A stale portal pairing severed track with no diagnostic | Fixed |
| B12 | `deleteConfiguration` ignored a failed delete, so the configuration returned next session | Fixed |
| B13 | Backup covered neither the track diagrams nor the autonomy setup | Fixed |

**B1** is the cycle's largest change. The running model records which Point a locomotive stands on and
never which way it faces, so nothing stopped a journey taking the edge straight back where it came from.
The hand-written configurations solved this by hand - one-way edges and two Points on one s88 - and that
shape is the direction written down. `AutonomyBuilder` now splits every square by arrival side.

**B2.** `continueWalk` keyed its visited set on the tile alone, so a run legitimately crossing one square
twice on its two separate tracks looked like a circle and was dropped, with nothing reported. Keyed on
tile and entry side it still terminates - four sides, and a real circle re-enters by the side it used
before.

**B4.** Two defects with opposite signs, which is why the numbers could never be reconciled: the editor
gathered the graph's problems separately *and* through the checks, listing each twice, while the diagram
counted every non-error as a warning, including dozens of notices the user had deliberately demoted.

**B9.** `editor.render()` only queues the grid build, so `reveal(tile)` ran with `grid == null` and
returned. Every jump from the findings count or an "Elsewhere" row landed on the right page and left the
user to find the square - the exact task the feature exists to remove.

---

## C - Low. Cosmetic, dead code, or narrow edge cases

| | Finding | Status |
|---|---|---|
| C1 | `AutonomyOverlayToggle.isShowing()` overrode `Component.isShowing()`, freezing the strip's repaints | Fixed |
| C2 | The registry prune reached across windows and orphaned the main window's cached pages | Fixed |
| C3 | `DiagramTileRegistry` never pruned main-window labels; it grew without bound | Fixed |
| C4 | Every IOException from creating a configuration was reported as "name already in use" | Fixed |
| C5 | `promptNumber` showed the length error for a station's priority | Fixed |
| C6 | A caption could be filed outside the area the diagram draws | Fixed |
| C7 | Split names could collide with an authored name and invalidate the whole configuration | Fixed |
| C8 | "May turn round here" was silently promoted to "must" on a square reached only by a link | Fixed |
| C9 | The `baseNames` cache was not dropped when the flags it derives from changed | Fixed |
| C10 | Backup had no cycle guard and reported bare file names | Fixed |
| C11 | CS2 array syntax was re-emitted as a single brace-string | Fixed |
| C12 | The trapped-arrival check was not track-aware, missing the case the builder delegates to it | Fixed |
| C13 | Two strings promised captions were saved immediately, after they became deferred | Fixed |
| C14 | `showPages()` kept a stale submenu reference after a rebuild | Fixed |
| C15 | "Export raw graph as JSON" appeared with no graph to export | Fixed |
| C16 | The name-everything button was hidden rather than greyed, unlike its neighbour | Fixed |
| C17 | An unpaired link was a blocking error, refusing any imported diagram carrying a page-jump arrow | Fixed |

**C1** is worth reading twice. Overriding `isShowing()` to mean "is the overlay ticked" answered a
question Swing asks for something else: `JComponent.paintImmediately` begins `if (!isShowing()) return`,
and every child walks up through its parents' answers. Unticking the box stopped the whole strip
repainting. Two earlier fixes in the same file - making the strip opaque, then the checkbox opaque -
were treating the symptom.

**C2** was introduced by the round 1 fix for C3. The prune judged "nobody can see this any more" by
displayability, correctly, but applied it across windows: the main window caches a page's grid and
re-attaches it later, so its labels are detached and perfectly alive while another page shows. A popup
rebuilding that page threw them out, and switching back gave a page registered nowhere.

---

## D - Not defects

| | Item | Outcome |
|---|---|---|
| D1 | `testReturnHomeOnRealLayout` failing on every run | Not a code defect - data |
| D2 | Sample layout file modified in the working tree after every app run | Correct behaviour |
| D3 | Legacy JSON export "missing" the new may-turn/must-turn attributes | No such attributes exist |
| D4 | Deriving 22 reversible connections against the hand-built 7 | Not a defect; see B1 |
| D5 | Length and address labels sharing a colour and colliding | Cannot collide |
| D6 | `Collection` and `I18n` audits across the branch | Clean |

**D1.** The suite loads the operator's own `autonomy.json`, and that file had been overwritten by a
derived export with no locomotive placements in it. Nothing in the code was wrong. The suite now skips
rather than fails when the file cannot drive a train - it already skipped when the file was absent - and
the fixture moved to `test/autonomy.json` so a config in daily use is no longer also test input. That
double duty is how the file was lost in the first place.

**D2.** The migration rewriting `cs2_sample_layout` is the migration working. The fixture is restored
after each run deliberately: a page whose legacy labels have already been migrated cannot test migration
again, so the repository keeps the pre-migration state.

**D3.** Asked whether `Layout.toJSON` exports the new "sometimes reverses here" versus "always" state.
It does not, because there is no such attribute: the difference is how many Points the square becomes,
and both use the pre-existing `terminus` and `reversing` flags, which `toJSON` has always written. What
the JSON does not carry is the authoring intent - `canReverse` and `mustReverse` live in the setup and
are instructions to the builder. The export is a snapshot of the result, not of the design.

**D5.** Raised while changing length numbers to the same red as addresses. They cannot meet: lengths are
drawn only in the editor, which enforces lengths and addresses as mutually exclusive in both directions.

---

## What the passes missed

Worth recording, since it is the calibration data the README asks for.

**Round 1 missed A1, A4, A6 and C2 entirely, and introduced three of them.** The model pass read the
store's save path and did not follow a caption to disk and back; the omission from `KNOWN_SHARED` is
invisible from any single method. What found it was the round 3 test audit, by asking whether a passing
test could have failed - `testACaptionSurvivesASaveAndLoad` saves from a fresh store, which is the one
flow the bug cannot reach.

**A test that passes for the wrong reason cost more than a missing test.** Three of them:
`testTwoRoutesBetweenTheSameSensorsBecomeOneEdge` had a fixture producing zero edges, so its assertions
ran zero times for as long as it has existed; `testRenamingAStationTouchesNoPage` compared bytes of a
file nothing ever wrote; `testMayTurnRoundIsNotPromotedToMust` built a sensor with no connections, which
never becomes a Point. All three read as protection and were none.

**Two fixture traps account for most of the geometry errors**, and both are recorded in the code now:
tile orientation rotates by `(4 - orientation)` quarter turns, and switches default to base-to-forks. A
fixture with either wrong is *connected-looking* and carries no edges.

**The `\n` in a message value does not survive a shell heredoc.** Twice, splitting values across lines;
the second repair attempt then folded twenty comment lines into values across all eight bundles because
it split on CRLF in a file that had acquired bare LFs. Both repaired, verified against git rather than
by eye. Message bundles are now checked by a script rather than by reading them.
