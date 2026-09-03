# Comments and documentation review

**Status:** open

**Prefix:** CDR

Reviewed at `e4c94ac9` (`v3_0_0_rc4`), 2026-08-31, by reading and grepping only - no build, no test run, no
application launch, per the fan-out brief's hard constraints. Scope: comment/javadoc accuracy in the nine
files named in the task (`Layout.java`, `HomeStaging.java`, `Point.java`, `Edge.java`, `TrainControlUI.java`,
`LayoutEditor.java`, `LayoutGrid.java`, `LayoutLabel.java`, `automationui/TileOverlay.java` - the last one's
real path has no `gui/` segment, unlike the brief's spelling), the `Readme.md` v3.0.0 changelog,
`Automation.md`, `AutomationAPI.md`, `docs/reviews/README.md`, `docs/manual-tests/README.md`, TODO/FIXME/XXX
markers, commented-out code, and the eight message bundles' ASCII encoding.

Adam's own words - quoted in code comments and everything attributed to him in `docs/manual-tests/tests.md`
- were read but not edited or second-guessed, per the brief.

---

## Method

1. `git log --since="2026-08-30"` on the nine named files to find what actually moved, then read every
   diff hunk in the two commits the brief flagged as prime suspects (`66c96736` - MT-149/MT-165 - and the
   surrounding rounds `3f507b4d`, `6afe6390`, `d8d1ea50`, `204a13ca`, `7fc1961b`) in full, not just the
   messages.
2. For each rule that changed, grepped the whole `src/` tree for comments elsewhere stating the OLD rule,
   to catch a copy the fixing commit didn't sweep.
3. Read `Readme.md`'s v3.0.0 changelog block bullet by bullet against the commit messages for the round
   that produced each fix, checking both directions: a false claim, and a real user-hit fix with no
   bullet at all.
4. Read `Automation.md` and all of `AutomationAPI.md` end to end for a reference to `autonomy.json` as
   the primary format, a graph editor, or a node/edge vocabulary the diagram-based system no longer
   surfaces.
5. Read `docs/reviews/README.md` (already loaded as house rules) and `docs/manual-tests/README.md`
   against what actually exists on disk (`docs/tools/`, `triage.py`, the archive folder).
6. Grepped for `TODO|FIXME|XXX` across `src/`, and for commented-out statements in the nine files.
7. Checked the eight `.properties` bundles byte-by-byte for anything above `0x7F`.

---

## Findings

| ID | Severity | Status |
|---|---|---|
| CDR-B1 | B | open |
| CDR-B2 | B | open |
| CDR-B3 | B | open |
| CDR-B4 | B | MOOT 2026-09-03 - the rule it was about was removed the same day (`CMT-C3`) |
| CDR-C1 | C | open |
| CDR-C2 | C | open |
| CDR-D1 | D | closed (not a defect) |
| CDR-D2 | D | closed (not a defect) |
| CDR-D3 | D | closed (not a defect) |
| CDR-D4 | D | closed (not a defect) |

### CDR-B1 - `LayoutLabel.java` still describes a z-order dance that no longer decides what's on screen

**File/lines:** `src/org/traincontrol/gui/LayoutLabel.java:1058-1080` (`liftAboveLabels` javadoc) and
`:1164-1177` (the note above `paintComponent`).

Before OB-159 (`6afe6390`), a running train was drawn *inside* `TileOverlay.paint()`, which ran during
this tile's own `paintComponent`. Because of that, whether the train ended up visually above or below a
sibling component (a station caption, or the S88 address-number `JLabel`) depended entirely on Swing
component z-order - which is what `liftAboveLabels`/`keepCaptionsInFront` exist to arrange, and what
`testTheTrainIconDoesNotPaintOutACaption` tests directly against plain components.

OB-159 moved the train drawing out of `paint()` entirely into a new `paintTrain()`/`paintTrainOverCaptions()`
pass that `LayoutGrid.newDiagramContainer()` runs *after* `super.paintChildren()` has already painted every
child - tiles, captions, **and** the address labels (added to the same container, confirmed at
`LayoutGrid.java:1418-1419`, `setComponentZOrder(text, 0)`). That final pass runs once per `LayoutLabel`
unconditionally of any component's z-order, so a train now paints over every sibling in the container
regardless of whether `liftAboveLabels` ran.

The two comment blocks were not updated for this. `liftAboveLabels`'s own javadoc still says "a tile with a
train on it claims [the front] for as long as the train is running and gives it back afterwards" as if that
lift is still what puts the train above the address labels. The note above `paintComponent` still says
"Address labels get no such rescue... a lifted tile paints over their text for as long as a train sits on
the square" - but `paintComponent` (read immediately below that comment) no longer paints a train at all;
that block was moved out under OB-159.

This is exactly the kind of trap the review discipline document warns about: a future reader debugging a
*different* overlap on this same square (a third component, a new badge) would read these two comments and
conclude the address-label case is still handled by z-order, and could "fix" it by touching
`liftAboveLabels`/`keepCaptionsInFront` - work that no longer has any effect on what actually paints last.

**Confidence:** confirmed by reading - traced both the removed `if (train)` block in `TileOverlay.paint()`
(gone since `6afe6390`) and the unconditional final loop in `LayoutGrid.newDiagramContainer()`
(`paintChildren` override, `LayoutGrid.java:657-75`), and confirmed address labels share the same container
as tiles and captions.

**To confirm by execution:** build a container the way `testTheTrainIconDoesNotPaintOutACaption` does, but
through `LayoutGrid.newDiagramContainer()` and real `LayoutLabel`/address-`JLabel` instances instead of
plain components; give the tile a moving-train `TileOverlay` and do **not** call `liftAboveLabels` (or stub
it to a no-op); paint the container and check the address label's pixels are still fully covered by the
train icon. If they are - which the code above predicts - the z-order lift is provably no longer load-bearing
for this case, confirming the comment is stale rather than merely imprecise. This does not require the
railway, only a headless Swing paint, so it should be safe to run without touching `cs2_sample_layout/`.

---

### CDR-B2 - `Readme.md`: MT-149's timetable-redraw fix (real, pre-3.0.0, user-hit) has no changelog bullet

**File:** `Readme.md`, v3.0.0 "Bug Fixes" > "Autonomy" (around line 426).

Commit `3f507b4d` fixed a real defect Adam reported and filed critical: after renaming a locomotive, the
Timetable tab kept showing the old name until something unrelated forced a redraw, because
`repaintTimetable`'s redraw guard was keyed on `timeTable.hashCode()`, and `MarklinLocomotive` hashes by
identity - so a rename changed every row's text and no hash. That mechanism (`lastTimetableState`) dates
to 2024-04-15 (`a33fb60d`), long before 3.0.0 development, so this is not a bug introduced and fixed
within 3.0.0's own cycle - it is exactly the class of defect Adam's rule says belongs in the changelog.

Line 426 covers the *other* half of the same rename report (the train vanishing from its station on the
diagram, fixed by the same commit family) but says nothing about the timetable text itself failing to
update, which was a separate code path (`TrainControlUI.java`, not `Layout.java`) and a separate fix.

**Confidence:** confirmed by reading - `grep -in "timetable"` over the v3.0.0 changelog block finds only
two unrelated timetable bullets (a save/reload ordering bug, and a bad-entry-drops-everything bug); neither
describes "the display doesn't refresh after a rename."

**To confirm:** none needed beyond the grep already run; this is a textual fact about `Readme.md` versus
the commit history, not a runtime claim.

---

### CDR-B3 - `Readme.md`: OB-164's non-atomic lock/throat-release fix (real, pre-3.0.0, user-hit) has no changelog bullet

**File:** `Readme.md`, v3.0.0 "Bug Fixes" > "Autonomy" (around line 413).

Commit `7fc1961b` fixed a defect Adam hit and described precisely on MT-087: "WORKS FINE in atomic mode. In
non atomic mode, locks aren't getting released... no movement is allowed at all." `executePath` released an
edge as soon as the train's tail cleared it, but kept every lock edge (a crossing/throat) that edge had
taken until the whole path finished - so on a layout where routes cross, one train passing through
permanently blocked every route crossing its path for the rest of the run in non-atomic mode. `Edge.java`'s
history goes back to the project's first commits, so the lock-edge mechanism this bug lived in is not new to
3.0.0 either.

Line 413 already documents a *different*, narrower train-length/track-release bug from the same area
("Track behind the locomotive was released as soon as the edges waiting to be released added up to the
train's length..."), which is a distinct defect with a distinct trigger (track lengths + train lengths both
recorded) from OB-164 (throats never released in non-atomic mode, independent of any lengths being set).
Neither the existing bullet nor any other in the block covers OB-164.

**Confidence:** confirmed by reading - `grep -in "lock|throat|crossing|non-atomic|atomic"` over the v3.0.0
block matches only line 413's train-length bullet.

**To confirm:** none needed beyond the grep already run.

---

### CDR-B4 - `Automation.md` / `AutomationAPI.md` don't mention the split-square refusal on Home Locomotive assignment

**MOOT 2026-09-03 (`CMT-C3`).**  The refusal this asked the two guides to document was removed hours
after this was written, on Adam's ruling - *"the home should just be the logical point, and the
direction is wherever the locomotive was facing when it started moving"* (`7616d2a6`, `09777d4c`).
`whyNotAHome` carries no split-square check and the message key it named,
`autolayout.errorHomeSquareIsSeveralPoints`, is in no bundle.  There is nothing left to document, so
this closes as moot rather than fixed - the doc gap went with the rule.

**Files:** `Automation.md` "Sending everything home" (lines 198-207); `AutomationAPI.md` "Returning
locomotives home" (lines 511-535, especially 525).

`HomeStaging.whyNotAHome` and `Layout.setHomeLocomotive` refuse to let a station drawn as more than one
graph `Point` (a "block" - `Point.getBlock() != null`) be **assigned** as a locomotive's home, throwing
`autolayout.errorHomeSquareIsSeveralPoints`. This rule (Adam's 2026-08-25 ruling, LD-8) predates last
night's MT-165 fix and is still in force at the assignment door - only the *positional* default changed.
On Adam's own railway, ten of thirty-six station squares carry a block, including the main-line platforms
he actually parks trains on.

Neither user-facing doc mentions this refusal. `AutomationAPI.md:525` lists three reasons a `Home
locomotive` assignment can be refused with a confirmable warning - too long, excluded, can't reverse at a
terminus - and omits the fourth, unconditional one (a split-square station can never be assigned, no
confirmation offered, only a thrown exception). `Automation.md`'s "Sending everything home" section
doesn't mention the restriction at all. A user trying to right-click one of those ten platforms and pick
`Home locomotive` gets an error neither guide explains or even hints exists.

**Confidence:** confirmed by reading for the code side (`HomeStaging.java:1217-1238`,
`Layout.java:1147-1194`) and for the docs (full read of `Automation.md`; grep of `AutomationAPI.md` for
`block|split|could never hold` found nothing beyond the three listed reasons). Whether this doc gap has
actually confused Adam in practice needs his own account - the code-side claim needs no execution to
believe.

**To confirm:** none needed for the code/doc mismatch itself. If a live check is wanted, in a sandboxed
layout (never `cs2_sample_layout/`) attempt to assign a home on a station whose `getBlock() != null` and
observe the thrown message names `autolayout.errorHomeSquareIsSeveralPoints` - a key absent from both docs.

---

### CDR-C1 - `isSimultaneousMultiUnitCompatible`'s own doc doesn't warn about the self-comparison result

**File:** `src/org/traincontrol/marklin/MarklinLocomotive.java:1051-1057` (javadoc),
implementation ending at `:1114` (`return !this.hasEquivalentAddress((MarklinLocomotive) l);`).

MT-149's actual defect (66c96736) was that comparing a locomotive with itself through this method returns
`false` ("incompatible"), because an object always has an equivalent address to itself. The fix was applied
at the one call site (`Layout.sanitizeMultiUnits`, which now short-circuits with
`if (l.equals(p.getCurrentLocomotive())) continue;`) rather than in this method or its javadoc. The method's
own contract still says nothing about this behaviour one way or the other, so the next caller that doesn't
already know the MT-149 story has no documented warning that `x.isSimultaneousMultiUnitCompatible(x)` is
`false`.

Currently harmless - `sanitizeMultiUnits` is the method's only caller in `src/` - so this is a trap for the
next caller rather than a live defect, per the review discipline's "wrong code is not wrong behaviour" rule.

**Confidence:** confirmed by reading; caller list confirmed with
`grep -rn "isSimultaneousMultiUnitCompatible" src/`, one call site.

**To confirm:** re-run the same grep after any future change to check no second caller was added
without the same guard.

---

### CDR-C2 - commented-out code and TODOs found (list)

Not individually stale in a way that misleads about current behaviour (unlike CDR-B1), but present and
worth a swept list, per the brief.

**Commented-out code**, in the nine target files:
- `Layout.java:3267` - `//this.control.log("Path: " + this.pathToString(path));` (debug leftover)
- `Layout.java:4164` - `//Collections.shuffle(ends);` (disabled shuffle; harmless, `ends` is used unshuffled below)
- `Layout.java:4404` - `// this.control.log("Starting fresh timetable execution.");` (debug leftover)
- `TrainControlUI.java:583` - `//FlatIntelliJLaf.setup();` (old look-and-feel, superseded)
- `TrainControlUI.java:6556` - `// this.model.log("Loading mapping for page "...` (debug leftover)
- `TrainControlUI.java:6738-6739` - two commented `KeyboardTab.setIconAt`/`setToolTipTextAt` calls
- `TrainControlUI.java:7904` and `:7941` - both are the same commented-out `img.getScaledInstance(...)`
  line, superseded in both places by `ImageUtil.getScaledImage` right below it - a genuine "fix one site,
  sweep the siblings" duplicate, though both copies already agree, so nothing is inconsistent, just doubled.
- `LayoutEditor.java:944` - `//label.setBackground(Color.red);` (debug leftover)
- `LayoutGrid.java:773-774` - `// if (width * size < parent.getWidth() || height * size < parent.getHeight() || popup)\n// {`, wrapping what is now `newDiagramContainer()` - the branch has been dead since before OB-159 (OB-159 only changed what's inside it)
- `LayoutLabel.java:890-893` - a commented-out `if (!tcUI.showLayoutAddresses())` guard around a
  `setToolTipText` call

None of these assert anything false about current behaviour; they are inert. Severity C (cosmetic/dead
code) throughout.

**TODO/FIXME/XXX**, project-wide (`grep -rn "TODO|FIXME|XXX" src/`), 13 hits, none in the nine target files
except the two below:
- `LayoutLabel.java:1277` - "TODO improve the way highlighting is done, delete global variables" - read in
  context, still an open, forward-looking note; not stale.
- `TrainControlUI.java:23072-23073` - references "the TODO at the foot of this method" as describing "this
  same bug" - the referenced TODO has since been resolved and its marker deliberately deleted (see the note
  at `TrainControlUI.java:23132-23134`, "The note that stood here... is done, and is deleted rather than
  quoted"), so this is a correctly self-documented resolution, not a stale pointer to a dangling TODO.
- The other 11 (`Locomotive.java:41`, `LayoutDiagramComponent.java:372-373`, `Route.java:300`,
  `LayoutRightclickAutonomyMenu.java:303`, `MarklinControlStation.java:2511`, `MarklinLocomotive.java:906`,
  `CS2File.java:1580,2182,2218`, `PositionAwareJFrame.java:24`) are outside the nine target files, spot-
  checked rather than exhaustively verified (see "What this pass did not do" below); none read as
  obviously stale.

**Confidence:** confirmed by reading for every item listed.

---

### CDR-D1 - MT-165/MT-149 fix commits swept their own stale comments; no leftover copies found elsewhere

Checked whether the OLD "a square drawn as more than one graph Point cannot be a home" rule, or the OLD
"sanitizeMultiUnits has no self-exclusion" behaviour, is still asserted anywhere outside the two commits
that fixed them. Grepped `cannot be a home|more than one graph Point|split square|derived home` and
`sanitizeMultiUnits|isSimultaneousMultiUnitCompatible` across all of `src/`. Every hit found
(`HomeStaging.whyNotAHome`'s javadoc, `Layout.setHomeLocomotive`'s comment, `AutonomyEditorPanel.java:3399`,
`LayoutRightclickAutonomyMenu.java:725`, `AutonomyBuilder.java`, `AutonomySession.java`) correctly describes
the *current*, split rule (refused at the assignment door only) rather than the pre-fix absolute rule. Not
a defect.

### CDR-D2 - `Edge.occupancy`'s "flag vs. count" comment is not stale

The brief's domain note flags this as a recurring trap. Commit `d8d1ea50` (RC-A9) already rewrote
`Edge`'s class javadoc and every method comment when `occupied` became `occupancy`, and commit `7fc1961b`
(OB-164) found and fixed the one remaining stale copy in `Layout.java` ("Occupancy is still a flag rather
than a count..."). `grep -rn "flag rather than a count|occupied state"` across `src/` now returns nothing.
Not a defect as of this commit.

### CDR-D3 - `Automation.md`, `AutomationAPI.md`'s deprecation framing, `docs/reviews/README.md`,
`docs/manual-tests/README.md` are accurate

`Automation.md` describes only the diagram-based system and never mentions `autonomy.json`, a separate
graph editor, or node/edge vocabulary - consistent with automation having moved onto the track diagram
this cycle. `AutomationAPI.md` still documents the JSON graph in full, but says so explicitly and
correctly at the top ("The JSON graph described below is **deprecated** as an authoring format... a
layout built today is built on the diagram - the graph is derived from it"), which is honest framing
rather than a stale claim. `docs/reviews/README.md` and `docs/manual-tests/README.md` were checked against
what is actually on disk: `triage.py`/`triagedb.py` exist where named, `docs/reviews/archive/README.md`
exists, and neither README references `docs/tools/` (which moved this cycle) at all, so there is nothing
in either to go stale over that move. Not a defect.

### CDR-D4 - message bundles are pure ASCII

Read all eight `.properties` files
(`src/org/traincontrol/resources/messages*.properties` for en/de/fr/es/it/nl/da/pl) byte by byte in Python;
no byte above `0x7F` in any of them. Not a defect.

---

## What this pass missed

- **`Layout.java` and `TrainControlUI.java` were not read end to end.** Both are enormous (`Layout.java`
  is over 7,000 lines; `TrainControlUI.java` over 20,000). I read every diff hunk in the commits the brief
  flagged, plus targeted greps for terms the fix commits used, but did not read either file's full body
  looking for unrelated stale comments untouched by any recent commit. A comment that went stale two or
  three rounds ago, in a part of either file nothing since has touched, would not have surfaced.
- **The other six files' full bodies got a similar targeted-not-exhaustive treatment** - I read the diffs
  for their recent commits in full and grepped for related vocabulary, but did not read
  `LayoutEditor.java`, `LayoutGrid.java`, `LayoutLabel.java`, or `TileOverlay.java` cover to cover.
- **`docs/manual-tests/tests.md`'s 242 entries were spot-checked, not audited.** I confirmed the MT-149/
  MT-165 ledger rows match their commits' dispositions, and did not read the other ~240 entries for
  internal contradictions or claims a later fix has quietly outdated.
- **`docs/manual-tests/issues.md` was read opportunistically (chasing the OB-164 numbering question), not
  reviewed as a document in its own right** - it is not one of the six items the task named.
- **The eleven TODOs outside the nine target files** were read in isolation, not against the surrounding
  method's current behaviour the way the two in-scope ones were - I cannot rule out one of them describing
  a condition that no longer holds.
- **CDR-B1's execution check was not run**, per the hard constraint against building or running anything;
  the finding rests entirely on reading `TileOverlay.java`'s and `LayoutGrid.java`'s current bodies against
  their pre-OB-159 versions, which I am confident in, but it is marked "confirm by execution" rather than
  treated as certain.
- **Non-English bundle *content* (translation quality/correctness) was not reviewed** - only the ASCII/
  `\uXXXX` encoding constraint, which is what the brief asked this pass to spot-check.
- **`docs/reviews/2026-08-30-*` (SG, RC, LE) were read for context on what is already known, not audited
  for their own comment/doc accuracy** - they are historical records of what was believed at the time and
  out of this pass's scope by the shared brief's own rule.
