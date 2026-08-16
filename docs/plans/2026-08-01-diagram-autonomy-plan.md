# ACTIVE ROUND: Fix the 2026-08-01 (UC) review findings — 12 red tests confirmed, user authorized all fixes

## Context

The UC review (docs/reviews/2026-08-01-untouched-code-review.md) produced 1 B + 15 C findings.
I verified each, added 12 red tests, the user ran the suite: **all 12 red for the predicted
reasons**, everything else green. The user then said "Proceed with all fixes." This plan is that
fix round. (The diagram-autonomy design further down this file is a separate, parked plan — not
part of this approval.)

## Fixes that flip the 12 red tests green

1. **UC-B1** — `MarklinLocomotive.setAddress` (after `numF` update): clear out-of-range functions
   via `if (getArrivalFunc() != null && getArrivalFunc() >= getNumF()) setArrivalFunc(null);`
   (same for departure). NOTE: must use `setArrivalFunc(null)` — the setter *refuses* out-of-range
   values, so it cannot be used to clamp. And `Locomotive`'s full-state constructor: assign
   `arrivalFunc`/`departureFunc` only when `>= 0 && < numF`, else null (fields are assigned after
   `this.numF = numF`, so the bound is available).
2. **UC-C1** — `Conversion.compareVersions`: parse each dotted component's leading digit run
   (empty → 0) instead of raw `Integer.parseInt`. `Util.parseReleaseVersion` unchanged.
3. **UC-C2** — `RouteCommand` feedback branch: `.trim()` the state token.
4. **UC-C3** — `RouteCommand` locdir branch: `forward` → FORWARD, `backward` → BACKWARD, anything
   else throws the checked friendly error (same `error.invalidLine` message `fromLine` uses).
5. **UC-C4** — `NodeExpression.toTextRepresentationHelper`: when serializing `NodeAnd`, wrap a
   child that is `NodeOr` in `(`/`)` lines; when serializing `NodeOr`, wrap a `NodeAnd` child.
   Match `NodeGroup`'s exact bracket emission (read it in situ). Cross-operator nesting is the
   only unstable case; same-operator chains re-associate harmlessly.
6. **UC-C5** — `Layout.renamePoint`: throw on duplicate target
   (`autolayout.errorPointAlreadyExists`, existing key) and while running
   (`autolayout.errorCannotEditWhileRunning`, existing key). No new bundle keys.
7. **UC-C6** — `MarklinControlStation.execRoute`: null-check `getByName`; log + return
   (reuse an existing route-not-found key if one exists, else `this.log(...)` plain).
8. **UC-C11a** — `Point` constructor: non-null s88 must parse as an integer, else throw
   (`autolayout.errorStationMustHaveValidS88Address`, existing key). JSON loads pass numeric
   strings built from Integers, so no load regression.
9. **UC-C11b** — `Point.setMaxTrainLength`: null or negative → 0 (no limit); drop the inert assert.
10. **UC-C12** — `Locomotive.setLocalImageURL` touches only `localImageURL`; `getImageURL()`
    returns `localImageURL != null ? localImageURL : imageURL`. Verified against all persistence
    and sync callers (`MarklinSimpleComponent:157`, restore `:2323`, sync guard `:1167`) —
    effective-URL semantics are preserved everywhere; the change is that clearing no longer
    destroys the CS URL in-session.
11. **Activation list** — `Layout.setActivateRouteIDs`: defensive copy
    (`new LinkedList<>(...)`, null → empty). Fixes the 8-month `testAutoLayout` teardown UOE.

## No-test batch (user authorized)

- **UC-C7**: `FullAutonomyExample:101` comment → real 5-arg `init`; `ProgrammaticControlExample:154`
  `mySignal.setSwitched(false)` → `mySwitch.setSwitched(false)`; "Error ocurred" → "Error occurred"
  (3 examples + the main class — locate main by grep, it is not at src/org/traincontrol/TrainControl.java);
  `System.exit(0)` → `System.exit(1)` on the 4 failure paths.
- **UC-C8**: delete `ImageUtil.textToImage` + `rotateImage` (zero callers, verified);
  **keep `MarklinRoute.equalsUnordered`** — the review's zero-caller claim is wrong, it has two
  callers in `testParseCS3Routes` (correct this in the review doc); remove `GraphLocAssign`'s
  inert `visibility` conditional (fold to the plain `setVisible(true)` calls); remove
  `RightClickFunctionMenu`'s unused `JToggleButton b` constructor param + update its caller.
- **UC-C9**: replace the post-dispose `edit.focusImages()` with the `AncestorListener` pattern
  from `GraphLocAssign:90` (focus when the panel is added to the dialog).
- **UC-C10**: `ImageUtil.getScaledImage`: destination type
  `image.getType() == 0 ? TYPE_INT_ARGB : image.getType()`.
- **UC-C13**: `UsageHistogram`: hoist `getTotalLocStats` + `setTitle` out of `paintComponent`
  into a refresh method called at construction and wherever `offset`/`perPage` change.
- **UC-C14**: `final` on the nine `RouteCommand.KEY_*` fields.
- **UC-C15**: `RightClickSelectorMenu`: when `getKeyForCurrentButton()` is null/-1, omit the
  assign-to-button item instead of rendering `(char) -1`.

## Closeout

- Mark all UC statuses Fixed in the review doc (one status, one location), with a resolution note
  recording: the `equalsUnordered` correction, the additional activation-list defect found while
  confirming red (pinned by `testDeletingAnActivationListedRouteSurvivesAnImmutableList`), and
  which fix shape each contested finding took (B1 model-level, C1 compareVersions-side).
- Readme changelog (non-technical): B1 (function-assignment dialog after decoder conversion),
  C1 (update notices survive suffixed release names), C2+C3 (hand-typed route lines: spaces no
  longer flip sensor state; typo'd directions are errors), C4 (conditions keep their meaning
  through the editor), C12 (clearing a custom icon offline no longer loses the CS image).
- `validate_all.py` after each batch; expected end state: all 12 tests green in NetBeans, no
  other test moves.

## Verification

Run in NetBeans: `testLocomotive`, `testInvalidInput`, `testLayoutRenameKeys`, `testRoutes`,
`testAdvancedRoutes`, `testAutoLayout` (teardown error gone), plus full suite for regressions.

---

# REVISED ARCHITECTURE (author, 2026-08-01): tile graph + reduction

**This section supersedes conflicting details below.** The design below it remains valid for the
persistence model, the editor-mode UX, the NetBeans rules, monitoring, and the port-map research —
but the *connectivity model* changes as follows.

## Two layers, not one

**Layer 1 — the tile graph (ground truth, user-confirmable).**
Every tile is a node. Every pair of adjacent tiles whose facing ports meet is a **candidate
connection**. Geometry proposes; the user disposes. In Autonomy mode the user sees these
connections drawn on the diagram and sets each one to **disallowed / one-way A→B / one-way B→A /
both**. Geometry supplies the default, so a correctly-read diagram needs no clicks at all.

This inverts the previous risk profile. The port map is no longer *authoritative* — it is a
**seed**. A tile the engine misreads shows a visibly wrong connection that the user fixes in
place, instead of silently producing a wrong edge. The old mitigation for that risk (the
paint-override tool and `SegmentFloodFill`) is **dropped**: editing connections directly is
strictly better than painting segments to compensate for a bad read.

**This also supersedes per-tile direction.** Direction lives on the *connection*, which has two
identified endpoints, so "forward" needs no convention at all — the arrow points from one visible
tile to another. The per-tile requirement is still met as a convenience gesture: selecting a tile
and choosing a direction applies it to all of that tile's connections at once.

**Special connections** (not derivable from adjacency):

- **LINK — jumps to another track diagram** (author, 2026-08-01). A link tile has **two** ports:
  - a **geometric** one, joining the adjacent track tile on the side **opposite the arrow head**.
    The art is a box containing an **east-pointing** arrow, so the connecting side is **W** at
    orientation 0 — the arrow points away from the attached track, toward the page it jumps to.
    It **rotates with the tile** (LINK has 4 orientations). This side behaves like any ordinary
    adjacency. (An earlier draft read the arrow as west-pointing; that was a misreading of the
    ASCII dump, corrected against the rendered art and confirmed by the author.)
  - a **portal** one, joining its partner link on another page.

  Today a link only stores the index of the diagram it points at, which is too weak for autonomy —
  several links can target the same page, leaving the destination *tile* ambiguous. Autonomy
  therefore requires, per link tile:
  - a **unique name**, and
  - an explicit statement of **which link it jumps to**.

  Rules:
  - **Unnamed links do not connect.** The graph simply has no portal edge there and no train ever
    passes over it. This is a silent, safe default: adding autonomy to an existing diagram cannot
    invent connections the user did not confirm.
  - **Pairing is strictly mutual and exclusive** — if A jumps to B then B jumps to A, and nothing
    else may target either. **No A-B-C sharing a common B.** The store validates this and reports
    a violation as a setup error naming both tiles; a half-pairing (A names B, B names something
    else or nothing) is likewise an error rather than a silently one-way jump.
  - Names are unique across the whole layout, not per page, since the pairing is cross-page.

- **TUNNEL** — connects normally on its one visible side (**S** at orientation 0, confirmed from
  the art), plus one user-paired portal connection to its partner tunnel, possibly on another page.
  Same mutual-and-exclusive pairing rule.

Portal connections are stored explicitly in the companion, never inferred, and are authored with
the same click-source-then-target gesture (page switching allowed between the two clicks).

**Layer 2 — reduction to the autonomy graph.**
The autonomy `Layout` is a *contraction* of the tile graph:
- **Significant nodes** = every s88 feedback tile (automatic, by definition) plus any additional
  user-designated station tiles. See the ruling section below.
- Everything else is collapsed: a chain of degree-2 tiles between two significant nodes becomes
  **one edge**.
- A **switch is not a node** — it is a branch point that is transparent to reduction. Walking a
  switch forks the path, and each fork records the accessory settings that select it; the settings
  gathered along a collapsed chain become that edge's `configCommands`.
- Edge **length** = sum of the collapsed tiles' lengths. Edge **direction** = AND of the collapsed
  connections' directions; a chain containing a disallowed or contrary connection yields no edge
  in that direction.
- Edge **tile path** is retained — it is what monitoring lights up and what lock derivation
  intersects.

Consequence: the generated JSON is small and hand-readable even when the tile graph has thousands
of nodes, and it is exactly the shape `parseAuto` already consumes.

## Locks become automatic (moves from R2 into R1)

**Two reduced edges whose collapsed tile paths share a tile are mutually exclusive**, and the
builder emits the lock references without being asked. Switches and crossings are the common
cases and are handled by the general rule rather than as special cases:
- a **switch** shared by two edges means both routes need the same points — mutually exclusive;
- a **CROSSING** shared by two edges is one physical crossing — mutually exclusive, even though
  its two port groups never join;
- an **OVERPASS** is the sole exception, and it is a genuine physical one: its `{EW}` and `{NS}`
  routes are permanently connected with **one track running above the other** (author,
  2026-08-01), so two edges sharing the tile in *different* groups are **not** mutually exclusive.
  Sharing the *same* group still is. This is the one place where a shared tile does not imply a
  conflict, and getting it wrong in either direction is costly: treating it as a conflict
  needlessly serialises two independent routes, while treating a same-group share as safe would
  allow a genuine collision;
- a linked **portal pair** (tunnel/link) counts as one location for this purpose.

This retires most of Release 2. What remains there is the **exposure linter** for layouts still
using a legacy hand-written `autonomy.json`, where locks were authored by hand and may have gaps.
A diagram-derived configuration cannot have gaps by construction.

## What this simplifies

- `SegmentFloodFill` — **deleted**, no longer needed.
- Paint-overrides — **deleted** from the companion schema and the UI.
- `tileDirections` — **replaced** by `connectionOverrides`, keyed by the ordered tile pair.
- The Trace & Review table stops being the primary instrument. Review happens on the diagram,
  where the connections are. The table survives only as a summary of the *reduced* graph
  (edges, lengths, config commands, derived locks) for a final read-through before load.
- `LockEdgeReviewUI` (R2) shrinks to a linter report; no add/apply workflow is needed for
  diagram-derived configurations.
- The debug "show connectivity" overlay is no longer a debug aid — it **is** the editing surface.

## Significant nodes: every s88 is a Point (author ruling, 2026-08-01)

**Every feedback tile becomes a Point, by definition** — `FEEDBACK`, `FEEDBACK_CURVE`,
`FEEDBACK_DOUBLE_CURVE`. There is no other way for the autonomy model to capture a feedback
signal, so promotion is automatic and not a user decision. **A station is a Point the user has
additionally designated**; the two concepts are now cleanly separated (every station is a Point,
most Points are not stations).

**Only s88 tiles may be designated stations** (author ruling, 2026-08-01). This is not a new
constraint invented by the plan — it is the autonomy model's own invariant: `Point`'s constructor
throws `autolayout.errorDestinationPointMustHaveS88` when `isDestination` is set without an s88
(`Point.java:62`). Two useful consequences:
- Under this architecture **every Point the diagram derives has an s88** (non-feedback tiles are
  all collapsed away by reduction), so every derived Point is *eligible* to be a station and the
  "this tile cannot be a station" error state is unreachable from the UI. The designation is a
  simple toggle on a Point.
- Virtual points (a Point with no s88) can no longer be authored from the diagram at all. They
  remain legal in the model, so a **legacy `autonomy.json` containing one is a migration case**:
  it has no tile to anchor to and must be reported for the user to resolve, not silently dropped.
  This retires the earlier draft's "virtual points anchor on a plain track tile" idea.

**Naming (author directive, 2026-08-01).** Every derived Point is **user-nameable**, and stations
carry meaningful names such as "Track 14 entrance", so that logs, the graph window, timetable
output and autonomy debug messages are readable rather than coordinate soup.
- Default name is generated from the tile coordinate, which is unique by construction. An s88
  address is *not* a usable name source — station and Pre/Post guard tiles legitimately share
  sensors, so several Points carry the same s88.
- User names are stored in the companion **against the tile key**, so a rename survives every
  rebuild and any change to the surrounding track.
- Validation on entry: non-empty, and unique across the configuration — `Layout.renamePoint`
  already refuses a duplicate target (and refuses while autonomy is running); the setup UI must
  enforce the same rule at authoring time rather than surfacing it at build time. Note `Point`'s
  constructor strips `"` characters from names (`Point.java:53`), so the field should reject or
  visibly strip quotes rather than let the name change under the user.
- The points table shows generated vs user-assigned names distinguishably, so it is obvious which
  Points have been given real names and which are still coordinates.
- **Duplicate-tile pages.** A "Combined" page that redraws tiles from other pages would mint a
  second Point for the same physical sensor. This is what the per-page **exclude from autonomy**
  flag exists for (see its section) — such a page is excluded and never walked.
- **Isolated feedback tiles** (author: "it depends on whether or not it is connected to
  anything"): a feedback tile with **no** allowed connections is not emitted as a Point at all —
  an unreachable node would only fail validation. A feedback tile with exactly one connection is
  emitted normally (a legitimate dead-end/terminus). The build logs how many isolated feedback
  tiles were skipped, so a wrongly-disconnected sensor is noticeable rather than silent.
- **Graph size.** This produces a materially larger graph than the author's hand-built ~104-edge
  config, which is expected and correct: hand-authoring omitted sensors that the diagram knows
  about. Migration must therefore *match* legacy edges onto a subset of the derived graph rather
  than expect a 1:1 correspondence.

## Per-diagram "exclude from autonomy" (author directive, 2026-08-01)

Users legitimately **duplicate a track diagram** — for example to show more or less of the layout
in one view. A duplicated page would otherwise mint a second Point for every s88 it redraws and a
parallel set of edges, corrupting the graph.

Each diagram page therefore carries an **exclude from autonomy** flag. An excluded page is not
walked at all: no tile graph nodes, no Points, no edges, no warnings about its contents. It
remains fully usable as a display and control surface — excluding it changes nothing about how it
renders or how its tiles behave at runtime.

- Stored in the companion, **shared per layout** (it describes the diagram, not a configuration),
  as a set of excluded page names; default is *included*, so behavior is unsurprising.
- Toggled from the autonomy mode's page selector and from the page's right-click menu.
- **This supersedes the earlier "participates in tracing" idea** and is the mechanism that solves
  the "Combined" page problem recorded in the exploration notes (a page that redraws tiles from
  other pages). That page is simply excluded.
- Interaction with links and tunnels: a portal pairing that targets a tile on an **excluded** page
  is a setup error, not a silent dead end — otherwise excluding a page would quietly sever routes
  elsewhere. The error names both tiles so the user can either re-pair or re-include.
- Excluding a page that contributes Points to the **active** configuration invalidates that
  configuration's placements/homes referring to them; the store reports these as orphans under the
  existing orphan policy rather than deleting them, so re-including the page restores them.

## Ground-truth gate: the generated graph must be diffed against the real `autonomy.json`

**Author directive, 2026-08-01.** Virtual points are expected to be unnecessary *provided the
mutual-exclusion derivation is right* — and that assumption is not something to take on faith.
The existing hand-built autonomy configuration is the **ground truth**, and the generated graph is
compared against it.

This is a **gate, not a test**: it runs before the setup UI is built, on the author's real diagram
and real `autonomy.json`, because its result determines whether the reduction and lock derivation
are correct enough to build a UI on top of.

The comparison harness reports, per category:
- **Points**: present in both / only in legacy / only in generated. The generated set is expected
  to be a **superset** (every s88 becomes a Point, whereas hand-authoring omitted some), so
  "only in generated" is informational, while **"only in legacy" is a defect** — the diagram
  failed to derive a point the author relies on. Legacy virtual points (no s88) are listed
  separately as unanchorable.
- **Edges**: matched by endpoint pair and direction. "Only in legacy" is a defect. "Only in
  generated" is expected in bulk and reviewed by sampling.
- **Config commands** per matched edge: the derived accessory settings must agree with the
  hand-authored ones. A disagreement is a **port-map defect** and is the highest-value signal the
  harness produces.
- **Lock references**: the derived mutual exclusions vs the 212 hand-authored refs. Legacy refs
  with no derived counterpart are the ones to examine — either the derivation missed a conflict
  (defect) or the author authored a conservative lock the geometry does not require (fine).
  This category is the direct evidence for the "no virtual points needed" assumption.
- **Lengths** on matched edges, once per-tile lengths are assigned.

Run it as a headless comparison producing a written report, so the result is reviewable rather
than a pass/fail. Keep the report; it is the acceptance record for the whole architecture.

## Connection rendering (author ruling, 2026-08-01)

Connections draw as **thin bidirectional lines in a colour clearly distinguishable from the black
track**, with **chevron arrows** marking direction. Specifics:
- One line per connection, drawn between adjacent tile centres, thin enough not to obscure the
  track art beneath.
- Chevrons at both ends for `both`; chevrons at one end only for a one-way connection; a
  disallowed connection renders as the line struck through (or omitted, with a marker) so it is
  distinguishable from "no connection exists here at all".
- Colour is a single accent distinct from black track and from the monitoring palette already
  claimed by `graph.css` semantics (amber RESERVED, red CURRENT, green COMPLETED, grey LOCKED) —
  so setup rendering can never be confused with running-state rendering.
- All connections render at once; this is the instrument for confirming a continuous one-way
  segment reads correctly, so it must stay legible at full-layout zoom.

# PARKED (superseded in part by the section above): Autonomy on the Track Diagram: Setup by Anchoring + Traced Connectivity, Monitoring, Lock Derivation

## Context

The autonomy graph (GraphStream window) and the track diagram (Swing tile grid) are separate UIs.
The author wants the track diagram — which already has an editor and interactivity — to become the
place where autonomy is **set up** and **monitored** ("track segments could light up in the middle
when there is a train"), **without changing the autonomy model** (`automation/Layout`, `Point`,
`Edge`).

**Author's directives (2026-07-31/08-01):**
- Connections must be **calculated from the diagram's own geometry**: tile type + rotation
  determine which sides connect, exactly as a human reads the plan. Not painted, not guessed from
  addresses.
- No new tile types: autonomy roles are **assignments onto existing tiles**, not diagram-format
  changes.
- Tunnel tiles behave as straight track connecting on one visible end; their continuation is a
  user-paired portal tile, **possibly on another page**.
- Directed lock references suffice (no same-direction-following semantics).
- First release = monitoring + setup together (continuous lit segments from day one).
- **No user-facing autonomy.json in the new setup**: the autonomy configuration is part of the
  track diagram. Validation and autonomy on/off must keep working exactly as they do today.
- **Multiple named configurations per diagram**: saved with the track diagram, independently
  selectable (e.g. different locomotive starting arrangements), each editable in a dedicated
  view, nameable/renamable by the user.

**Persistence architecture (follows from the last directive):**
- **Source of truth** = diagram pages + a companion file **owned by TrainControl**, never by the
  Central Station. It is written to TrainControl's working directory as
  `autonomy-setup.json`, exactly the precedent `autonomy.json` already sets
  (`AUTONOMY_FILE_NAME`, `TrainControlUI.saveState` :1163-1188), and it backs up through the same
  `Util.getBackupPath` path.
- **Writing it is NOT gated on `isLocalLayout()`.** `isLocalLayout()` only means "a local layout
  override path is set" (`TrainControlUI.java:16302`); when false the diagram is read from the
  Central Station and the *diagram editor* is disabled. Autonomy setup must keep working there:
  a user running a CS-sourced diagram must still be able to assign stations, because station
  definitions are TrainControl's data about the CS's tiles, not a modification of them. Nothing in
  the setup flow writes to `gleisbilder/` or to the Central Station.
  - Optional convenience, not a requirement: when `isLocalLayout()` **is** true, also mirror the
    companion to `config/autonomy-setup.json` in the layout folder so the setup can travel with
    a shared diagram. The working-directory copy stays authoritative; the mirror is written on
    save and read only if the working-directory copy is absent.
  - Because the diagram may be re-downloaded from the CS, anchors must survive a diagram refresh:
    they are keyed by `(page, x, y)` and re-resolved on load. A key that no longer names a
    suitable tile becomes an **orphan** — surfaced in the setup UI for re-anchoring, never
    silently dropped (this is the same orphan policy the store already applies to renamed points).
- The companion holds only what geometry cannot express, split two ways:
  **shared per layout** (one copy, describes the physical diagram): anchors (tile → point name),
  portal pairs, paint-overrides, **tile lengths**, **tile directions**; **per named configuration**: point flags and
  properties, per-edge overrides (manual lock additions), locomotive placements, homes, exclusions,
  global autonomy settings, timetable. Schema top level:
  `{version, shared: {pointNames, stations, portals, linkNames, excludedPages, tileLengths,
  connectionOverrides}, configurations: {"<name>": {...}}, activeConfiguration}`. `tileLengths` is a sparse map keyed
  `"<page>:<x>,<y>"` → int — only non-zero tiles are written. `tileDirections` is sparse the same
  way, keyed `"<page>:<x>,<y>"` (or `"<page>:<x>,<y>#<group>"` for two-group tiles) →
  `"forward" | "back" | "none"`; the default `both` is never written. Both maps are absent
  entirely on a diagram that uses neither, so the file stays small. New configuration = copy of an existing one (or empty); rename/duplicate/
  delete in the selector UI; migration from a legacy `autonomy.json` lands in a configuration
  named by the user (default "Default").
- **Configuration selection**: a combo on the autonomy tab beside Validate, plus the same in the
  setup window and `LayoutRightclickAutonomyMenu`. Selecting a configuration compiles it through
  the normal build → `parseAuto` → validate flow; switching is refused while autonomy is running
  (same gate as other structural changes). Runtime edits save back to the **active**
  configuration only. Editing a non-active configuration in the setup view touches only the
  companion — the live Layout is undisturbed until that configuration is loaded.
- **Derived on every build, never persisted**: edges, config commands, lit segments, graph x/y
  (from anchor tile positions — node dragging becomes unnecessary).
- **Length is authored per tile, summed per edge.** Every tile in the diagram carries a
  user-settable integer length, **0 and up**, default **0**. An edge's length is the sum of the
  lengths of the tiles on its traced path (endpoints excluded — the anchors themselves are points,
  not track between them). This is the same arithmetic `Layout.getPathLength` already does one
  level up (`Layout.java:1474`), and `Edge.setLength` asserts `>= 0` (`Edge.java:285`), so every
  reachable sum is legal.
  - Default 0 is deliberate: it is `Edge`'s own field default and what `autonomy_sanity.json`
    uses throughout, so a freshly traced diagram reproduces today's behavior exactly and
    `maxTrainLength` accounting stays inert until the user opts in by assigning lengths.
  - Units are the user's own (whatever they compare against `maxTrainLength`) — the builder never
    invents a unit, and in particular does **not** default to tile count.
  - Tile lengths are **shared per layout**, not per configuration: a piece of track is the same
    length whichever locomotives are placed on it.
  - Consequence: the per-edge length override is **dropped**. Two ways to set one number is
    ambiguity for no gain — a stylised diagram where a short drawn run is a long real run is
    expressed by giving one of its tiles a large length. `edgeOverrides` keeps manual lock
    additions only.

**Per-tile direction (author directive, 2026-08-01).** Every tile carries a user-settable
traversal constraint: **forward / back / both / none**, default **both** (so an untouched diagram
behaves exactly as it does now).

- *Meaning*: a traced edge exists in a given direction only if **every tile on its path permits
  that direction**. Constraints AND along the path, which is what makes a run of consistently
  marked tiles a genuine one-way segment. `none` makes a tile impassable — the trace stops there,
  which doubles as a way to deliberately cut the diagram.
- *Composition with permanent turnouts*: the `CUSTOM_PERM_*` rule is the same kind of constraint
  arrived at from the hardware side. They simply AND — a permanent turnout is permanently
  trailing-only, and a user direction cannot re-open its facing direction.
- *Reference frame — the load-bearing UX decision*: "forward" is meaningless in the abstract, so
  **the user is never asked to hold a convention in their head**. Storage is canonical (an
  ordered side pair, sides in compass order N,E,S,W), but the UI always **draws the arrow it
  means** on the tile. Clicking cycles `both → forward → back → none → both`, and the glyph
  updates: `↔` / `▶` along the track / `◀` / `⨯`. If the arrow points the wrong way, the user
  clicks again — no documentation required.
- *Multi-route tiles*:
  - two-port tiles (STRAIGHT, CURVE, FEEDBACK, FEEDBACK_CURVE, SIGNAL, UNCOUPLER, TUNNEL) have one
    axis; the arrow is unambiguous;
  - tiles with two independent groups (DOUBLE_CURVE, FEEDBACK_DOUBLE_CURVE, CROSSING, OVERPASS)
    hold **one value per group**, and a click cycles whichever group is nearer the click point;
    both arrows render;
  - **turnouts** express direction as **facing vs trailing relative to the toe**: `forward` =
    facing (toe → branch), `back` = trailing (branch → toe). This is the same axis the permanent
    turnouts already use, so one concept covers both.
- *Stored shared per layout*, alongside tile lengths — one-way running is a property of the track,
  not of where locomotives happen to start. Sparse map keyed `"<page>:<x>,<y>"` (plus group index
  where a tile has two), default `both` omitted entirely. *If the author later wants per-scheme
  one-way running (a "clockwise" and an "anticlockwise" configuration), this is the one field that
  would move to per-configuration — it is deliberately isolated so that change stays cheap.*
- *Visual confirmation for continuous segments* is a first-class requirement, not a debug aid:
  a **Show directions** toggle renders every tile's arrow at once, so a run reads as a continuous
  chain of arrows and a single wrongly-flipped tile is obvious at a glance. The same overlay
  greys `none` tiles. Additionally the trace reports **direction contradictions** — a run whose
  constraints make a connection impossible in both directions is listed as a warning with its
  coordinates, since that is almost always a mis-click rather than an intent.

**What is inferred vs. what is declared.** The graph is derived from the UI as far as the diagram
can actually answer, and no further:
- *Inferred* (never stored): which points connect to which, in what direction; the accessory
  settings that select each branch (configCommands); graph node coordinates; the tile set each
  edge covers (used for both monitoring and lock derivation); and edge length **as a sum** — the
  summation is derived, the per-tile numbers it sums are authored.
- *Declared* (stored in the companion): which tiles **are** points, their names and flags,
  portal pairings, per-tile lengths, and per-tile direction constraints. Point identity cannot be inferred — s88↔tile matching is many-to-many (29%
  unique at best; station and Pre/Post guards share sensors), which is the finding that forces
  anchoring to be an explicit user assignment. Everything downstream of an anchor is automatic.
- **Build = compile**: diagram + companion → generated JSON → the existing `parseAuto` →
  the existing Validate semantics → start/stop untouched. The autonomy tab shows the generated
  JSON read-only in diagram mode. Setup errors (unanchored point, unpaired portal, untraceable
  connection) surface through the same validate flow as today's errors.
- **Continuous inference (scratch build)**: the setup UI re-derives the whole graph after every
  edit, not only on commit. `Layout.fromJSON` (`Layout.java:4088`) is a *pure factory* — it
  constructs a fresh `Layout`, records failures via `layout.invalidate(msg)`, and returns it;
  `parseAuto` (`MarklinControlStation.java:608`) is the only thing that installs it as
  `model.getAutoLayout()` and calls `stopLocomotives()`. So the UI validates by calling
  `Layout.fromJSON(generated, model)` into a **throwaway** instance, reading `isValid()` /
  the error text, and discarding it: live validity feedback with the live layout and any running
  trains untouched. Only "Load this configuration" goes through `parseAuto`.
  *Known side effect*: `fromJSON` calls `control.newFeedback(s88, null)` for any s88 not already
  set (`Layout.java` ~:4298, guarded by `!control.isFeedbackSet`). Anchors come from real feedback
  tiles, so this is a no-op in the diagram flow and is identical to today's `parseAuto` behavior —
  but scratch builds must not be run on speculative/unassigned s88 values.
  Scratch build is debounced (share the monitor's 100 ms pattern) and runs off the EDT.
- **Runtime edits** (homes, exclusions, placements from either window) keep mutating the live
  Layout; save writes the authored subset back to the companion instead of regenerating
  `autonomy.json`.
- **Legacy mode**: an existing `autonomy.json` loads exactly as today. Migration = the anchor
  pass + trace-and-match, importing lengths/locks/homes/placements from the old file into the
  companion. Mode is decided by the presence of the companion file.

**Hard facts from exploration (verified in source):**
- `Layout.fromJSON` ignores unknown keys, `toJSON` re-emits only known ones → display/anchor
  metadata lives in a **sidecar file**, never in `autonomy.json`.
- s88↔tile matching is many-to-many (29% unique at best; station + Pre/Post guards share sensors;
  a "Combined" page redraws others) → **anchoring is explicit user assignment**; only
  *connectivity between tiles* is automatic.
- Monitoring is additive: `Layout.setCallback(name, fn)` is a `ConcurrentHashMap` with one
  registrant today ("GraphCallback", `TrainControlUI.java:15316`); fires at path start / each
  milestone / path end / refresh / manual move. Fire sites can hold
  `synchronized(activeLocomotives)` — listeners must enqueue only.
- `isPathClear` also checks the candidate path's **own** lock list for occupancy
  (`Layout.java:1330-1340`) → lock safety is per-ordered-direction **exposure**, not naive
  reciprocity (author's layout: 212 refs, 11% reciprocal — the linter measures what actually
  matters).
- Tile rendering: static shared `imageCache`; `updateImage` short-circuits on unchanged icon name;
  transient yellow highlight uses a one-slot `lastIcon` — a persistent overlay must go through
  `paintComponent`, not icon composition. `LayoutLabel` has no `paintComponent` today.
- Tiles have no coordinate index; devices hold `ConcurrentHashMap.newKeySet()` of labels pruned by
  `isParentVisible()` — the pattern to copy. Popups don't retain their grids.

Standing constraints: Java 8; NetBeans builds/tests only; Swing via `invokeLater`, never the
Layout monitor on the EDT; 8 message bundles, ASCII `\uXXXX`, no straight apostrophes; TestNG
one-class-per-JVM with a `build.xml` line each; non-technical changelog.

**NetBeans GUI Builder compatibility (rule for all new UI in this plan).** The project splits
cleanly today: 19 of 37 `gui/` classes have a Matisse `.form` (static dialogs and frames), and 18
are hand-written with none — including `LayoutGrid` (437), `LayoutLabel` (555),
`GraphRightClickPointMenu` (1249), `HomeLocomotiveMenu` (416). This work follows the same split,
with the dividing line set by the author (2026-08-01):

**The main UI must stay editable in the NetBeans designer. Trivial popups need not be.**

| Piece | Form? | Who builds it |
|---|---|---|
| `AutonomySetupPanel` (the autonomy mode's main panel) | **yes** — `.form` + `.java` | **author scaffolds in the designer**, logic written into the non-guarded parts |
| `LayoutEditor` additions (mode toggle + host container) | **yes** — existing `LayoutEditor.form` | **author adds the widgets in the designer**, behavior written into the handler bodies |
| `LockEdgeReviewUI` (R2) | **yes** — `.form` + `.java` | author scaffolds when R2 starts |
| Right-click menu items, tile-length prompt, small confirmations | no | hand-written, `LayoutRightclickAutonomyMenu` / `JOptionPane` style |
| All engine classes (`TilePorts`, `DiagramTopology`, `AutonomyCompanionStore`, `AutonomyBuilder`, `DiagramMonitor`, `DiagramTileRegistry`, `TileOverlay`, `SegmentFloodFill`) | n/a — headless, no Swing | written outright |

**Working rule, either way:** never hand-author or hand-edit `.form` XML, and never edit a
regenerated block. In a Matisse class the regenerated regions are `//GEN-BEGIN:initComponents` …
`//GEN-END:initComponents` and `//GEN-BEGIN:variables` … `//GEN-END:variables` (in `LayoutEditor`,
`:1505-1654`). The `//GEN-FIRST:event_x` … `//GEN-LAST:event_x` handler **bodies are the intended
place for hand-written code** and may be filled freely, as may everything outside the guarded
regions (`LayoutEditor` lines 1-1504).

Consequence for review: a diff that edits `.form` XML directly, or edits a line inside
`GEN-BEGIN:initComponents`/`GEN-BEGIN:variables`, is a defect. Widgets appearing there must have
arrived through the designer.

**Scaffolding handoff.** Because the author builds the scaffolding first, the widget inventory
below is a contract: field names are what the logic binds to. Fields land in the `variables`
block as `private` (project convention, cf. `GraphEdgeEdit.java:555`). The author may lay them out
however reads best and may add/rename freely — the logic adapts to the delivered form, the
inventory just states what must exist.

---

## Scaffolding inventory (author builds these in the NetBeans designer, first step)

### `LayoutEditor.form` — additions to the existing form

| Field | Type | Purpose |
|---|---|---|
| `modeDiagramButton` | `JToggleButton` | selects today's editor; one `ButtonGroup` with the next |
| `modeAutonomyButton` | `JToggleButton` | selects autonomy mode |
| `modeCardPanel` | `JPanel` (CardLayout) | holds the existing edit grid as one card, `autonomyCard` as the other |
| `autonomyCard` | `JPanel` (empty, BorderLayout) | `AutonomySetupPanel` is added here at runtime |
| `localOnlyNotice` | `JLabel` | shown when `!isLocalLayout()`: diagram editing needs a local copy |
| `downloadCSLayoutButton` | `JButton` | beside the notice; triggers the existing download flow |

Existing edit controls keep their names. The mode toggles need action handlers generated
(empty `GEN-FIRST/GEN-LAST` bodies are enough — the logic goes in them).

### `AutonomySetupPanel.form` / `.java` — new, `extends javax.swing.JPanel`

| Field | Type | Purpose |
|---|---|---|
| `configurationCombo` | `JComboBox<String>` | active named configuration |
| `newConfigButton` / `renameConfigButton` / `deleteConfigButton` | `JButton` | configuration CRUD |
| `loadConfigurationButton` | `JButton` | compile + validate + install this configuration |
| `toolAnchorButton` / `toolPortalButton` / `toolLengthButton` / `toolConnectionButton` / `toolPlacementButton` / `toolPropertiesButton` | `JToggleButton` | one `ButtonGroup`; the active tool. (Revised architecture: `toolConnectionButton` replaces the former Direction and Paint tools — it edits tile-to-tile connections, which is where direction now lives) |
| `pageCombo` | `JComboBox<String>` | which diagram page is shown |
| `diagramHostPanel` | `JPanel` (empty, BorderLayout) | the `JLayer`-wrapped `LayoutGrid` is inserted here at runtime; **give this the resizable weight** |
| `pointsTable` + `pointsScrollPane` | `JTable` | points: anchored / unanchored / orphaned |
| `orphanLabel` | `JLabel` | orphan count and warning |
| `reviewTable` + `reviewScrollPane` | `JTable` | Trace & Review: connection, status, summed length |
| `traceButton` | `JButton` | run the trace |
| `showConnectivityCheckbox` | `JCheckBox` | debug port/adjacency overlay |
| `showLengthsCheckbox` | `JCheckBox` | overlay per-tile lengths and edge totals |
| `showDirectionsCheckbox` | `JCheckBox` | overlay every connection's arrows at once — the instrument for confirming a continuous one-way segment reads correctly |
| `showReducedGraphCheckbox` | `JCheckBox` | highlight the reduced graph over the tile graph: significant nodes marked, each reduced edge's tile path tinted as one segment |
| `validityBanner` | `JLabel` | live scratch-build result; opaque, colored at runtime |
| `applyButton` / `undoButton` | `JButton` | write to companion / step back |

No table models, renderers, listeners or data need to exist in the scaffolding — empty tables and
inert buttons are exactly right. Both `JTable`s must sit inside their scroll panes.

## Where autonomy editing lives: a mode of the existing diagram editor

**Decision (2026-08-01): no separate setup window.** Autonomy setup is a *mode* of `LayoutEditor`,
the window the user already knows for working on the diagram. Same window, same grid, same tile
interaction, same undo affordance.

**The editor gains a mode switch** (toolbar toggle at the top, persisted in prefs):

| Mode | What it does | Requires `isLocalLayout()` |
|---|---|---|
| **Diagram** | today's editor exactly — place/rotate/erase tiles, addresses, text, row/col tools | **yes** (it writes `gleisbilder/`) |
| **Autonomy** | Anchor / Portals / Length / Paint-override, Trace & Review, live validity banner | **no** — writes only TrainControl's companion |

**This is what unblocks Central Station layouts.** The restriction was never about the editor
window; it is about writing the diagram files. Autonomy mode writes only
`autonomy-setup.json`, so it is available for every layout, CS-sourced or local.
Concretely, `editLayoutButtonActionPerformed` (`TrainControlUI.java:12461`) stops being a
dead end: instead of refusing with `errorEditingOnlySupportedForLocalFiles`, a non-local layout
**opens the editor in Autonomy mode** with the Diagram-mode toggle disabled and an inline
explanation — "diagram editing needs a local copy" plus a button wired to the existing
*Download CS layout* flow (`:14219`). A local layout opens in whichever mode was last used, both
enabled. The error dialog is deleted, not merely bypassed.

**Interaction plumbing (the load-bearing detail).** `LayoutLabel`'s listener block is binary: the
`edit` flag hard-casts `parent` to `LayoutEditor` and routes clicks/drag there, otherwise clicks
throw switches (`LayoutLabel.java:99-141`). Autonomy mode needs a *third* behavior, and it must
not call `layout.setEdit()` — that flag suppresses the `Point:` station labels
(`LayoutGrid.java:162` guards on `!layout.getEdit()`) and hides text, so edit mode does not look
like the layout the user reasons about. Resolution:
- The grid is built in **non-edit** mode, so autonomy mode shows the diagram exactly as the
  runtime view does (station labels present, tiles rendered normally).
- A `JLayer` over the grid consumes all mouse events and dispatches them to the active autonomy
  tool, so a click can never throw a switch or drive an accessory while setting up.
- `LayoutLabel` is left alone apart from the overlay work already planned. The JLayer approach
  needs no third branch in its listener block and no change to the `edit` cast.

**Undo** mirrors the editor's existing `snapshotLayout()` idiom (`LayoutEditor.java:1321`) with a
companion-state deque, so undo feels the same in both modes.

**The two surfaces, stated plainly.** Structural setup (what points exist, where they are, how
they connect, lengths) happens in the editor's Autonomy mode. Operational tweaks during a session
(homes, exclusions, placements, per-locomotive settings) stay exactly where they are today — the
runtime right-click menus, `LayoutRightclickAutonomyMenu` on a station label and the graph's point
menu — and continue to mutate the live `Layout`, saving back to the active configuration. Nothing
about running autonomy moves.

## The user-facing setup workflow (what ships)

1. **Anchor points (stations)** — new "Autonomy setup" mode over the diagram: right-click **any
   tile at any coordinate** → "Assign autonomy point" → name it and set flags
   (station/terminus/reversing/active) and any per-point properties.

   **Superseded by the revised architecture**: Points are no longer anchored by hand — every s88
   feedback tile *is* a Point automatically, and this step reduces to **naming** Points and
   **designating** which of them are stations. Only s88 tiles are eligible, matching the model's
   own invariant. What follows describes the interaction that survives (naming, flags, the
   relationship to the legacy `Point:` label).

   Stations are **always authored in TrainControl and never come from the Central Station**: no CS
   file defines them, no CS sync creates or removes them, and a CS diagram re-download cannot
   change one (only orphan it, if its tile is gone). The name is free text typed by the user, not
   chosen from a CS-derived list.

   Anchors are 1:1 (a tile carries at most one point; a point at most one anchor). Everything
   lands in the companion file — there is no autonomy.json to hand-edit.

   *Relationship to today's `Point:` label*: the existing mechanism stores the station name **in
   the diagram file** as a component label (`LayoutGrid.LAYOUT_STATION_PREFIX` :162,
   `LayoutEditor` :872), so it requires a local editable layout and is lost on a CS re-download.
   Anchors supersede it as the source of truth. The label is downgraded to optional display:
   assignment offers to auto-place it when the layout is local, and an existing `Point:` label is
   read once at migration to pre-seed anchors. Diagrams that already use the label keep rendering
   exactly as they do today.
2. **Pair portals** — click a TUNNEL (or page-edge LINK) tile, then its continuation tile,
   page-switching allowed between clicks.
3. **Trace & review** — the connectivity engine walks tile ports from every anchor to the next
   anchors, following every switch branch; each traced connection carries the tile path **and the
   switch settings that select it** (derived configCommands). The review table shows every
   connection; the user picks direction (one/both) per connection and sets per-edge overrides
   (extra locks). Lengths are authored per tile in Length mode, not per edge; the review table
   shows each connection's summed length so the effect of tile assignments is visible. In
   migration mode, traces are matched to the legacy file's edges and their locks/homes are
   imported; a legacy edge length is **distributed** onto its traced tiles (whole-division
   remainder onto the first tile, so the sum is exact) and reported in the review table as
   imported-and-distributed, since the old file had no per-tile data to recover.
4. **Validate & run — unchanged.** The setup compiles to generated JSON behind the existing
   Validate button; errors surface in the same flow; start/stop autonomy works exactly as today.
   The autonomy tab shows the generated JSON read-only.
5. **Derive locks** — traced paths sharing tiles conflict (CROSSING yes, OVERPASS no, linked
   portal pair = one location); review dialog proposes minimal additions; exposure linter reports
   uncovered directions of already-declared conflicts.
6. **Monitor** — segments light by state; a centered dot marks the train's current block.
   Manual segment painting remains as an override for stylised diagrams.

---

## Release 1 — Connectivity engine, anchoring/setup UI, monitoring

### New classes (`org.traincontrol.gui` unless noted; engine classes are pure/headless)

| Class | Responsibility | ~Size |
|---|---|---|
| `base/TilePorts.java` | The port map for **all 28 `componentType` values** — see the derived table below. Uniformly state-indexed: `ports(type, orient, state)` returns the set of connected side pairs in that state (unswitched / switched, or the three-way's three states; one state for everything else). No common/branch/toe concepts. Honors `getNumOrientations()` (2 / 1 / 4 by type) rather than assuming 4; encodes the directed restriction for `CUSTOM_PERM_*` (into S only) | 300 |
| `base/DiagramTopology.java` | Builds the **directed** adjacency for a set of `LayoutDiagram` pages + portal pairs: nodes = (page,x,y) tiles with ports; `trace(anchorTile)` walks to neighbouring anchors, forking per switch branch, recording tile path + required accessory settings; respects one-way traversal (a facing entry into a `CUSTOM_PERM_*` turnout yields no exits, so that direction produces no connection at all); collects warnings (permanent turnouts encountered) alongside results; cycle-guarded, bounded | 340 |
| `base/TraceResult.java` | Value type: endpoint anchors, ordered tile path, `Map<address, setting>` requirements | 60 |
| `AutonomyCompanionStore.java` | Owns `autonomy-setup.json` in TrainControl's working directory (never the Central Station; **not** gated on `isLocalLayout()`), optionally mirrored to `config/autonomy-setup.json` when the layout is local: `{version, shared: {anchors, portals, paintOverrides}, configurations: {"<name>": {pointProperties, edgeOverrides, placements, homes, exclusions, globals, timetable}}, activeConfiguration}`; pages by name; unknown top-level fields preserved; `version>1` refuses load; configuration CRUD (create-as-copy, rename, delete — never the last one); save on setup Apply + the exit save path (authored subset read back from the live Layout into the **active** configuration), alongside `autonomy.json` in `saveState` and backed up the same way; `renamePoint` rewrites shared anchors and every configuration's override keys; orphans kept, never silently dropped | 380 |
| `AutonomyBuilder.java` | The compile step: companion + `DiagramTopology` traces → generated autonomy JSON string fed to the existing `parseAuto` (reusing the whole validate pipeline unchanged); deterministic ordering so output is diffable; emits setup errors (unanchored point, unpaired portal, untraceable connection) as validate-visible failures, and **warnings** (permanent turnout present, with coordinates) that surface in the banner/review table and the log **without blocking the build**; graph x/y derived from anchor tile positions; edge length = sum of the traced path's per-tile lengths, endpoints excluded (0 when unassigned). Exposes `build()` (JSON string) and `validateScratch()` — `Layout.fromJSON(build(), model)` into a throwaway, returning validity + message **without** `parseAuto`, so the setup UI can re-infer the graph live after every edit | 340 |
| `TileOverlay.java` | `enum SegmentState {RESERVED, CURRENT, COMPLETED, LOCKED}` + marker kind (WASH, DOT, WASH_AND_DOT) + colors + static `paint(Graphics2D,w,h,state,kind)` | 80 |
| `DiagramTileRegistry.java` | `(page,absX,absY) → Set<LayoutLabel>` plus accessory and s88 indexes; `ConcurrentHashMap`/`newKeySet`; prunes `!isParentVisible()` on iteration | 150 |
| `DiagramMonitor.java` | Owns `"DiagramCallback"`: fire = try-catch'd `markDirty()` (AtomicBoolean+semaphore) only; single daemon worker drains, 100 ms debounce, full idempotent recompute, diff-publish to EDT in one `invokeLater`; `republish()` after grid rebuilds; constructor `(Supplier<Layout>, registry, publisher)` for headless tests | 350 |
| `AutonomySetupPanel.java` (+ `.form`, **author-scaffolded**) | The Autonomy-mode **panel inside `LayoutEditor`** (not a window): configuration selector at the top (choose which named configuration is being edited; manage = new-as-copy/rename/delete); non-edit `LayoutGrid` under a `JLayer` that consumes all mouse events (clicks can't throw switches); tools Anchor / Portals / Length / **Direction** / Paint-override (shared) and Placements / Properties (per-configuration); Direction tool: click cycles a tile `both -> forward -> back -> none`, drag or shift-click applies the same value along a run, and the arrow drawn on the tile always shows exactly what the value means (no convention to memorize); Length tool: click a tile to type its integer length (0+, blank = 0), drag or shift-click a run to set many at once, and a toggle overlays every non-zero tile's number plus each traced edge's running total so the sum is visible where it is authored; side panel lists points (anchored/unanchored) and orphans; **Trace & Review** table (adopted / new-edge candidates / mismatches) with per-row "show on diagram", each row showing its summed length; Apply writes to the companion; "Load this configuration" compiles it through the normal validate flow (refused while `layout.isRunning()`); per-tool undo deque mirroring `snapshotLayout()`; **live validity banner** driven by `AutonomyBuilder.validateScratch()` after every edit (debounced, off-EDT), so the inferred graph is continuously checked against the real `Layout` semantics before anything is committed | 800 |
| ~~`SegmentFloodFill.java`~~ | **Deleted by the revised architecture** — paint-overrides no longer exist | — |
| `base/TileGraph.java` (new, revised architecture) | Layer 1: nodes = `(page,x,y)` tiles, candidate connections from facing ports, plus explicit LINK/TUNNEL portal connections; each connection carries a state (disallowed / one-way either way / both) defaulted from geometry and overridable by the user; `neighbors(tile, direction)` honors those states | 260 |
| `base/GraphReducer.java` (new, revised architecture) | Layer 2: contracts the tile graph to the autonomy graph — significant nodes survive, degree-2 chains collapse to one edge, switches fork transparently and contribute `configCommands`, lengths sum, directions AND, tile paths retained; then derives mutual exclusion from shared tiles (OVERPASS cross-group excepted, portal pair = one location) and emits the lock references | 340 |

### Monitoring semantics (unchanged from prior draft)

- `LayoutLabel`: `volatile TileOverlay overlay` + `setOverlay()` + `paintComponent` override
  (paint after `super`; coexists with the transient yellow highlight). ~25 lines.
- Colors from `graph.css` semantics: RESERVED amber (255,102,0), CURRENT red (196,0,0) wash+dot,
  COMPLETED green (0,196,33), LOCKED grey (160,160,160); precedence CURRENT > COMPLETED >
  RESERVED > LOCKED.
- Recompute: snapshot `activeLocomotives` briefly under its own monitor; milestones are COW lists;
  edge → COMPLETED if its end is in milestones, first non-completed = CURRENT, rest RESERVED,
  lock-edge union → LOCKED; resolve via segments (coord index) + config-command accessories +
  point s88s; publish diff; then `updateStationLabels(p)` — station labels no longer require the
  graph window to be open (behind `DIAGRAM_OVERLAYS_PREF`, default true).

### Touches to existing files (exhaustive)

1. `gui/LayoutLabel.java` — overlay field/setter/`paintComponent` (~25 lines).
2. `gui/LayoutGrid.java` — register every non-edit tile in the registry with absolute coords
   (`x + offsetX, y + offsetY`, page = `layout.getName()`) inside the device-registration block
   (:318-341); pass master container to `addLayoutStation` for pruning.
3. `gui/TrainControlUI.java` — fields+getters (registry, monitor, store); attach all three at the
   validate-success funnel before `renderAutoLayoutGraph()` (~:12940; per-Layout callbacks →
   re-attach per `parseAuto`); extract `updatePoint` tail (:15109-15197) into
   `updateStationLabels(Point)` minus the graph-window gate; `layoutStations` pruning (:389,
   :852, :893); `repaintLayout` tail (:16526) → `republish()`; store save in `saveState`
   (:1163-1188).
4. `gui/LayoutPopupUI.java` — `drawGrid()` tail → `republish()`.
5. `gui/LayoutRightclickAutonomyMenu.java` — "Autonomy setup..." menu item (opens the editor in
   Autonomy mode, scrolled to the clicked tile).
5b. `gui/LayoutEditor.java` — the mode switch: a Diagram/Autonomy toggle in the toolbar; hosting
   `AutonomySetupPanel` in a card layout beside the existing edit grid; **Autonomy mode must not
   call `layout.setEdit()`** (`:1409`) so station labels and text keep rendering as in the runtime
   view; Diagram-mode controls disabled and visibly explained when `!isLocalLayout()`; the
   `snapshotLayout()` undo idiom (`:1321`) extended with a companion-state deque. The widgets
   arrive via the author's designer pass (inventory above); this work fills the handler bodies and
   the hand-written regions only — no direct edits to `initComponents`, `variables`, or the
   `.form` XML.
5c. `gui/TrainControlUI.java` (entry point) — `editLayoutButtonActionPerformed` (`:12461`) no
   longer refuses non-local layouts; it opens the editor in Autonomy mode with Diagram mode
   disabled and a *Download CS layout* affordance pointing at the existing flow (`:14219`). The
   `errorEditingOnlySupportedForLocalFiles` call at `:12465` is removed. **The bundle key stays** —
   verified 2026-08-01, it has a second caller at `:14106` (the legacy external editor path), which
   is unaffected. No bundle deletions in any of the 8 files.
6. `gui/GraphRightClickPointMenu.java` — after `renamePoint` (:1134) → `store.renamePoint(...)`.
7. `build.xml` — new test lines (below).
8. All 8 bundles — keys (below).

No change to `automation/` model classes; `base/TilePorts` etc. are new read-only classes beside
the diagram model, not changes to it.

### The port map, derived from the actual art (2026-08-01)

`componentType` has **28 values** (`LayoutDiagramComponent.java:20-29`) — the earlier draft of this
plan accounted for roughly eight of them. Curves, double curves, feedback curves, uncouplers,
turntables and the six `CUSTOM_*` permanent/scissors types were all missing, and they are not
edge cases: a layout without curves does not exist.

**Orientation semantics.** `getImage` rotates by `(4 - orientation) * 90°` clockwise
(`:391`; the call is guarded by `orientation > 0`, so orientation 0 is identity). So orientation
`o` applies `(4 - o)` quarter-turns clockwise, i.e. `N→E→S→W→N` applied `(4-o)` times.
`getNumOrientations()` (`:864`) restricts the domain: **2** for STRAIGHT, FEEDBACK, ROUTE,
SWITCH_CROSSING, OVERPASS (y-axis symmetric); **1** for TURNTABLE and CROSSING; **4** otherwise.
`TilePorts` must respect this — asking for orientation 3 of a STRAIGHT is a bug, not a rotation.
*Check*: STRAIGHT `{E,W}` at o=0 → o=1 applies 3 CW turns → `{N,S}`, a vertical straight. Correct.

**The port map is uniformly state-indexed.** Every switch confirmed by the author (2026-08-01)
turns out to *replace* its connection set when thrown rather than add to it — `switch_left` is
`{NS}` unswitched and `{SW}` switched, not `{NS}` plus a branch. `SWITCH_CROSSING` behaves the
same way (`{NS}{EW}` unswitched, `{NW}{SE}` switched), and `SWITCH_THREE` is the three-state
version of the identical idea. So `TilePorts` needs no notion of "common leg", "branch" or
"toe" at all:

> **`ports(type, orientation, state) -> set of connected side pairs`**

where `state` is unswitched / switched (or the three-way's three states), and non-switch tiles
have a single state. This is simpler than the earlier common-plus-branches model, removes the
toe convention as a source of error, and makes `configCommands` generation fall out directly —
traversing a pair that only exists in a given state emits exactly the command for that state.

**The base icon is the default, unswitched position** (author, 2026-08-01); `_active` /
`_active2` art is the switched state. This is a general rule across the icon set, not per-tile
trivia, and it is what makes the table below readable directly off the art.

**STATUS: VERIFIED BY THE AUTHOR, 2026-08-01.** The whole table below was rendered over the icon
art — every connection drawn on its tile, green for unswitched and red for switched — and reviewed
tile by tile. See `docs/plans/portmap-verification.png`, generated by
`docs/plans/portmap-verification.py`. **The generator holds the port map as a data table at the
top**, so it is the executable copy of this table: correct one, re-run it, and the sheet
regenerates. Any future change to the port map must update both, and `testTilePorts` must agree
with both.

This removes the port map from the project's risk list. What remains is not "is the table right"
but "does the engine apply it correctly to real pages", which the connection-editing UI makes
visible and correctable anyway.

**Ports at orientation 0, measured from `icons60/` by border-occupancy and intra-tile flood fill.**
Confidence column: **M** = measured from the art and trustworthy; **D** = must be *declared* in the
table because pixels cannot express it.

| componentType | icon | open sides | port groups | conf |
|---|---|---|---|---|
| STRAIGHT | `straight` | E W | `{EW}` | M |
| CURVE | `curve` | E S | `{ES}` | M |
| DOUBLE_CURVE | `curve_parallel` | N E S W | `{NW}` `{ES}` — two independent curves | M |
| FEEDBACK | `s88` | E W | `{EW}` | M |
| FEEDBACK_CURVE | `s88_curve` | E S | `{ES}` | M |
| FEEDBACK_DOUBLE_CURVE | `s88_double_curve` | N E S W | `{NW}` `{ES}` | M |
| SIGNAL | `signal` | E W | `{EW}` — topologically a straight | M |
| UNCOUPLER | `decouple` | E W | `{EW}` | M |
| END | `end` | N | `{N}` — terminates a trace | M |
| TUNNEL | `tunnel` | S | `{S}` + one **portal** port, user-paired | M |
| CROSSING | `cross` | N E S W | `{NS}` `{EW}` — two independent straights | **D** |
| OVERPASS | `overpass` | N E S W | `{EW}` `{NS}` permanently connected, **one track physically above the other** (author, 2026-08-01) — never meet, no accessory | author |
| SWITCH_LEFT | `switch_left` | N S W | unswitched `{NS}`; switched `{SW}` (author) | author |
| SWITCH_RIGHT | `switch_right` | N E S | unswitched `{NS}`; switched `{SE}` (author) | author |
| SWITCH_Y | `switch_y` | E S W | unswitched `{SW}`; switched `{SE}` (author) — no straight route | author |
| SWITCH_THREE | `threeway` | N E S W | **three routes from a single toe at S** (author, 2026-08-01): S-N, S-E, S-W. Two addresses; see mapping below | author |
| SWITCH_CROSSING | `crossswitch` | N E S W | **double slip** (author, 2026-08-01): unswitched `{NS}` `{EW}`; switched `{NW}` `{SE}`. State-dependent, 2 orientations | author |
| CUSTOM_PERM_LEFT | `custom_perm_left` | N S W | **trailing only** (branch→toe); no accessory | M / **D** |
| CUSTOM_PERM_RIGHT | `custom_perm_right` | N E S | **trailing only**; no accessory | M / **D** |
| CUSTOM_PERM_Y | `custom_perm_y` | E S W | **trailing only**; no accessory | M / **D** |
| CUSTOM_PERM_THREEWAY | `custom_perm_threeway` | N E S W | **trailing only**; no accessory | M / **D** |
| CUSTOM_PERM_SCISSORS | `custom_perm_scissors` | — | **DISQUALIFIED** (confirmed) — drawing convention, blocking error | ruling |
| CUSTOM_SCISSORS | `custom_scissors` | — | **DISQUALIFIED** — a drawing convention for a double slip across two tiles, not a routing element; blocking setup error | ruling |
| TURNTABLE | `turntable` | — | **NOT SUPPORTED** (author, 2026-08-01) — trace terminator, nothing routes across it | author |
| LINK | `link` | **W** at o=0, 4 orientations | connects the adjacent track on the side **opposite the arrow head** (the arrow points E, away from the track) **plus** one named portal connection to another page. Verified 2026-08-01 | author |
| LAMP, ROUTE, TEXT | `lamp`/`route`/`text` | none | decorative, no ports | M |

**What the pixels could not answer, and why (this is the honest limit).**
1. **Crossing-type grouping.** Flood fill returns `{NESW}` for CROSSING, OVERPASS, SWITCH_CROSSING
   and TURNTABLE because their routes *share pixels at the intersection*. Openness is measured;
   the split into independent groups must be declared. This is not a defect in the method — no
   pixel analysis can distinguish "these routes cross" from "these routes join".
2. **Branch → accessory setting.** Diffing each `_active` variant against its base shows the
   changed leg (e.g. `switch_left`'s change centroid sits toward W, `switch_right`'s toward E),
   which identifies *that* a leg is highlighted but not reliably which `accessorySetting`
   (STRAIGHT/TURN, GREEN/RED) selects it. For two-address `SWITCH_THREE` the mapping **is**
   settled — see below. For the two-leg switches the branch/straight assignment is declared and
   should be confirmed on real hardware.

   **`SWITCH_THREE` address mapping (resolved 2026-08-01).** `getImageName`
   (`LayoutDiagramComponent.java:321-327`) selects `_active` when `getAccessory()` is switched and
   `_active2` when `getAccessory2()` is switched, base art when neither is. Cross-checked against
   the pixel diff, which puts `_active`'s changed leg toward **W** and `_active2`'s toward **E**.
   Therefore:
   - **S-N (straight)**: address 1 *not* switched **and** address 2 *not* switched;
   - **S-W**: address 1 switched;
   - **S-E**: address 2 switched.

   **Rule for `configCommands` generation (author, 2026-08-01): straight vs throw always
   differentiate.** Every command states a position explicitly — `accessorySetting` is
   `{GREEN, RED, STRAIGHT, TURN}`, there is no "leave as-is" value — so a two-address turnout is
   simply two independent, fully-specified commands. Each of the three routes therefore commands
   **both** addresses:
   - S-N: address 1 straight, address 2 straight;
   - S-W: address 1 thrown, address 2 straight;
   - S-E: address 1 straight, address 2 thrown.

   There is no invalid combination to guard against and no precedence rule to encode. An earlier
   draft claimed both-thrown was a physically invalid state the builder had to refuse; that was
   wrong and is retracted. Specifying every address on every route makes the question moot and
   costs one extra command.
3. **`CUSTOM_PERM_*` semantics — resolved by the author (2026-08-01).** These represent
   **defective switches that lack an address and cannot be thrown**. Traversal is therefore
   **directional**:
   - **Facing (toe → branches): forbidden.** The switch position cannot be commanded, so which
     branch a train would take is not knowable. No trace may leave a permanent turnout by a
     branch leg after entering at the toe.
   - **Trailing (branch → toe): permitted.** Merging into the common leg works regardless of the
     blade position, so this direction is safe and traceable.
   - **No config command is ever emitted** for these tiles — there is no accessory to command.
   - This applies to `CUSTOM_PERM_LEFT/RIGHT/Y/THREEWAY/SCISSORS`. `CUSTOM_SCISSORS` is
     switchable and is **not** affected.

   **Consequence for the port model:** port groups alone cannot express this — `TilePorts` must
   support **directed** traversal, `exits(type, orient, entrySide, setting)` returning an empty
   set for a facing entry into a permanent turnout. `DiagramTopology` traces are therefore
   directed, and a connection whose path crosses a permanent turnout in the facing direction
   simply does not exist. The Trace & Review UI must not offer "both directions" for such a
   connection — the direction choice is constrained by the trace, not free.

   **Warning on build:** whenever a permanent turnout is present on a participating page and
   autonomy is generated, `AutonomyBuilder` emits a **warning** (not an error — the build still
   succeeds) naming the tile coordinates, so the author is reminded the diagram contains a switch
   that cannot be commanded. This requires a warning channel alongside the existing setup errors:
   errors block the build, warnings are surfaced in the validity banner and the review table and
   are logged, and the build proceeds.

   *Toe convention — **confirmed** by the author (2026-08-01), no longer an inference:* at
   orientation 0 the toe is the **S** side for every turnout — `SWITCH_LEFT`, `SWITCH_RIGHT`,
   `SWITCH_Y`, `SWITCH_THREE` and their `CUSTOM_PERM_*` counterparts, which share the same open
   sides. Every confirmed connection set contains S, which is what makes S the toe.

   For the permanent variants this fixes the direction concretely: **S -> anything is forbidden**
   (facing), **anything -> S is permitted** (trailing). A `CUSTOM_PERM_LEFT` therefore allows
   `N->S` and `W->S` and nothing else. Note this admits *both* legs in the trailing direction
   even though the blades are stuck in one position — which is the author's ruling that a trailing
   move is safe regardless of blade position.
   **`CUSTOM_SCISSORS` is DISQUALIFIED from autonomy-enabled diagrams** (author ruling,
   2026-08-01). It is not a routing element at all: it is a **drawing convention** that lets two
   tiles depict one double slip switch. Its topology therefore cannot be expressed per-tile, and
   the plan does not try.
   - `AutonomyBuilder` raises a **setup error** (blocking, not a warning) when a `CUSTOM_SCISSORS`
     is found on a page participating in autonomy, naming the coordinates and telling the user to
     replace it with real switch tiles. It is not silently ignored, because ignoring it would
     leave a hole in the track that traces would route around.
   - No port map entry is written for it; no reading of its art is needed. This retires the
     conflict between the art (N-S bar plus two diagonals both reaching E, no W connection) and
     the "bottom to left/right" description — neither reading is implemented.
   - **`CUSTOM_PERM_SCISSORS` falls under the same ruling** (confirmed by the author, 2026-08-01):
     same art, same drawing convention, same blocking error.

   Confirmed by the same rendering pass, and **not** affected by the above:
   - `switch_right`: a full-height vertical bar plus one diagonal entering from the E edge at
     mid-height and merging into the vertical near the bottom. Toe S, straight N, branch E.
   - `switch_y`: no vertical bar; two diagonals from the W and E edges converging at bottom
     centre. Toe S, branches W and E.
4. **TURNTABLE — not supported** (author ruling, 2026-08-01). A turntable connects any radial to
   any other under manual control, has one orientation, and cannot be expressed as a static port
   map. It is a **trace terminator**: a trace that reaches one stops there and nothing routes
   across it. No port map entry, no accessory handling, no paint-override workaround (the
   paint-override tool no longer exists under the revised architecture).
   The build emits an informational **warning** naming the coordinates when a turntable sits on a
   participating page, so it is clear that autonomy stops there by design rather than by a
   misread. Unlike `CUSTOM_SCISSORS` this is **not** a blocking error — a turntable is a
   legitimate part of a layout, it simply is not routable.

`s88_active` shows 2 stray border pixels from its highlight box; the extraction threshold ignores
runs of `<= 6` px, which cleanly separates real track from state decoration. `overpass` and
`turntable` have dark backgrounds that defeat most-common-color background detection — both are
declared anyway, so this does not matter, but a future re-run of the extraction must not treat
their measured openness as authoritative.

The extraction scripts live in the scratchpad; if the icon set changes, re-run them rather than
re-reading the art by eye.

### Port-map correctness strategy (risk retired 2026-08-01 — kept for the reasoning)

- The table is data, reviewed against the actual GIF art (icons30/) type by type; orientation
  semantics follow `getImage`'s rotation `(4 - orientation) * 90°`.
- `testTilePorts` asserts internal consistency (every port group side is reciprocal-capable;
  switch branch settings are exhaustive and disjoint; rotation of a port map = port map of the
  rotated orientation).
- **Debug overlay** in setup mode: "Show connectivity" draws each tile's computed ports as short
  edge ticks and each traced adjacency as a connecting stroke — a misread tile is visible on the
  author's real diagram at a glance. This is the primary acceptance instrument.
- The paint-override tool covers any diagram the engine misreads, so a port-map gap degrades to
  the old manual plan, never to a dead end.

---

## Release 2 — Lock-edge derivation + exposure linter

| Class | Responsibility | ~Size |
|---|---|---|
| `automation/LockEdgeAnalyzer.java` (read-only) | `lintExposure(Layout)` — needs no geometry; `computeConflicts(traced segments, portals)` — shared tile ⇒ conflict, OVERPASS groups don't meet, CROSSING's two groups DO conflict (one physical crossing), linked portal pair = one location; `suggest(...)` minimal canonical additions per uncovered ordered direction (candidate-own-list rule: covered iff `c.lock ∩ ({h} ∪ h.lock) ≠ ∅` or `h.lock ∩ {c, opp(c)} ≠ ∅`); dedupe before `Edge.addLockEdge` (bare LinkedList add) | 300 |
| `LockEdgeReviewUI.java` (+ `.form`, **author-scaffolded** at R2 start) | Review table (conflict pair, shared tiles, existing refs, proposed additions pre-checked; "show on diagram" flashes the intersection); add-only apply + `refreshUI()`; "possibly stale" informational rows for refs whose traced paths don't intersect — never deletes | 400 |

Entry points: buttons in `AutonomySetupUI` + "Check lock-edge coverage..." in
`LayoutRightclickAutonomyMenu`. Run the linter against the real 104-edge layout **before**
building the review UI — its report calibrates the derivation work.

---

## Explicit deferrals

- `LAYOUT_STATION_PREFIX` hardcoded "Point:" vs localized key — pre-existing TODO, untouched.
  (Anchors supersede the label as the source of truth, but the label itself and its rendering path
  are left exactly as they are; setup mode never edits the diagram file.)
- Duplicate page names alias keys (parity with `layoutCache`), main-tab label accumulation
  (parity with device sets) — accepted.
- Creating/editing the *diagram itself* stays in `LayoutEditor`; setup mode never mutates pages.

## i18n keys (all 8 bundles, one commit)

`layout.ui.menuAutonomySetup`, `layout.ui.menuToggleOverlays`, `autosetup.ui.title`,
`.modeDiagram`, `.modeAutonomy`, `.infoDiagramEditingNeedsLocalCopy`, `.btnDownloadCSLayout`,
`.modeAnchor`, `.modePortals`, `.modePaint`, `.modeLength`, `.promptTileLength`,
`.errorNegativeLength`, `.btnShowLengths`, `.colLength`, `.statusLengthImported`,
`.warnPermanentTurnout`, `.warnPermanentTurnoutCount`, `.colWarnings`,
`.modeConnection`, `.btnShowDirections`, `.btnShowReducedGraph`, `.dirBoth`, `.dirForward`,
`.dirBack`, `.dirNone`, `.warnDirectionContradiction`, `.errorScissorsNotSupported`,
`.infoIsolatedFeedbackSkipped`, `.labelPointNotStation`, `.menuDesignateStation`,
`.menuExcludePageFromAutonomy`, `.labelPageExcluded`, `.promptLinkName`,
`.errorDuplicateLinkName`, `.errorLinkNotMutuallyPaired`, `.errorLinkTargetShared`,
`.errorPortalTargetsExcludedPage`, `.infoUnnamedLinkNotConnected`, `.warnTurntableNotRoutable`,
`.promptPointName`, `.errorDuplicatePointName`,
`.errorEmptyPointName`, `.warnQuotesStrippedFromName`, `.labelGeneratedName`,
`.warnLegacyVirtualPoint`, `.assignPoint`, `.createPoint`, `.clearAnchor`,
`.labelPoints`, `.statusAnchored`, `.statusUnanchored`, `.orphanCount`, `.adoptOrphan`,
`.btnTrace`, `.reviewTitle`, `.colConnection`, `.colStatus`, `.statusAdopted`, `.statusNewEdge`,
`.statusMismatch`, `.btnApply`, `.btnUndo`, `.btnShowConnectivity`, `.confirmDiscardChanges`,
`.errorAutonomyRunning`, `.tooltipGestures`, `.labelConfiguration`, `.btnLoadConfiguration`,
`.menuNewConfiguration`, `.menuRenameConfiguration`, `.menuDeleteConfiguration`,
`.errorLastConfiguration`, `.promptConfigurationName`, `autolayout.infoDisplayStoreLoaded`,
`.infoDisplayStoreOrphans`, `.errorDisplayStoreVersion`; R2: `lockassist.ui.title`, `.menuSuggest`,
`.menuLint`, `.colTrackPair`, `.colSharedTiles`, `.colExisting`, `.colProposed`,
`.btnApplySelected`, `.btnShowOnDiagram`, `.infoNoConflicts`, `.infoPossiblyStale`, `.lintClean`,
`.lintExposedPair`.

Preference: `DIAGRAM_OVERLAYS_PREF = "DiagramOverlays"` (default true).

## Tests (one class = one JVM = one `build.xml` line)

| Class | Proves, headlessly |
|---|---|
| `testTilePorts` | port-map consistency: reciprocity, rotation coherence; **every switch's states are confirmed pairs, not supersets** — `SWITCH_LEFT` yields `{NS}` unswitched and `{SW}` switched with N unreachable when thrown, `SWITCH_Y` yields `{SW}` unswitched and `{SE}` switched with no straight route in either, `SWITCH_CROSSING` swaps throughs for diagonals; every confirmed connection pair contains S for turnouts (the toe invariant); `CUSTOM_PERM_*` permits only pairs entering S; base art maps to the unswitched state; **every one of the 28 `componentType` values has an entry** (loop the enum — a new type must fail the test, not silently trace as impassable); orientation domain matches `getNumOrientations()`; STRAIGHT `{EW}` at o=1 yields `{NS}` and CURVE `{ES}` at o=1 yields `{NE}`, pinning the `(4-o)` quarter-turn convention; CROSSING/OVERPASS expose two disjoint groups, never one |
| `testDiagramTopology` | programmatic pages (no files): straight runs connect; rotation breaks adjacency as the art says; switch fork yields per-branch traces **with correct settings**; CROSSING two independent throughs; OVERPASS no interaction; unlinked TUNNEL stops, linked portal continues cross-page; anchors terminate traces; cycles bounded; **permanent turnouts**: a trace entering a `CUSTOM_PERM_*` at the toe produces no connection past it, the same pair traced from a branch toward the toe does connect, the resulting edge is one-directional, no config command is emitted for the tile, and a warning naming its coordinates is collected; `CUSTOM_SCISSORS` (switchable) is unaffected; **per-tile direction**: an all-`both` run traces both ways (parity with an unconfigured diagram), one `forward` tile mid-run suppresses exactly the opposing edge, `none` stops the trace at that tile, constraints AND along a path rather than the last one winning, a user direction cannot re-open a permanent turnout's facing direction, a two-group tile constrains only the group traversed, and a run made impossible in both directions is reported as a contradiction warning with coordinates |
| `testDiagramMonitor` | dispatch→RESERVED, milestone→COMPLETED/CURRENT, completion fire (maps already cleared)→empty publish, 50-fire burst coalesces, throwing publisher never reaches the firing thread — simulated model + fake publisher + visible/hidden parents (the `testLayoutTiles` trick) |
| `testAutonomyCompanionStore` | shared + per-configuration round-trip; `tileLengths` and `tileDirections` sparsity (zeros and `both` are never written, a cleared value round-trips as absent, and both survive configuration switches because they are shared); a two-group tile's per-group direction keys round-trip independently; configuration CRUD (copy-on-create, rename, refuse deleting the last); version gate; unknown-field preservation; orphan policy; `renamePoint` rewrites shared anchors and every configuration; save-back targets only the active configuration; **CS-sourced layouts**: anchors save and reload with no local layout path set (the `isLocalLayout()`-false case must NOT refuse), a station assigned on a non-feedback tile round-trips as a virtual point with no s88, and an anchor whose `(page,x,y)` no longer resolves after a simulated diagram re-download becomes a reportable orphan rather than a deletion |
| `testAutonomyBuilder` | companion + programmatic pages compile to JSON that `parseAuto` accepts and validates; deterministic output (two builds byte-identical); setup errors (unanchored point, unpaired portal) invalidate through the normal flow; **migration**: legacy `autonomy.json` fixture + matching diagram → traced edges adopt the legacy lengths/locks/homes; generated-then-parsed Layout's `toJSON` is stable across a second build; **scratch build isolation**: `validateScratch()` on a deliberately broken companion reports invalid while `model.getAutoLayout()` remains the untouched previous instance (identity assert) and no locomotive was stopped; **lengths**: unassigned tiles yield length 0 on every edge (parity with today), assigned tiles sum along the traced path with endpoints excluded, a 0 on an intermediate tile contributes nothing without breaking the edge, and a legacy length distributed over N tiles sums back to exactly the original |
| `testTileGraph` (revised) | **links**: an unnamed link forms no portal connection and no train can cross it; a named, mutually-paired link joins its partner's tile across pages; a half-pairing and an A-B-C collision are both setup errors naming the tiles; the geometric side of a LINK is W at orientation 0 and rotates with the tile; a pairing targeting an excluded page is an error, not a silent dead end. **Excluded pages**: an excluded page contributes no nodes, Points, edges or warnings, and re-including it restores the orphaned placements rather than having deleted them. geometry seeds candidate connections both ways; a user override survives a re-trace and is not overwritten by geometry; disallowed connections are absent in both directions; one-way connections are absent in exactly one; LINK and TUNNEL portal connections join the named tiles across pages and are never inferred from adjacency; a permanent turnout's facing connections are seeded disallowed and cannot be user-enabled |
| `testGraphReducer` (revised) | **every** feedback tile becomes a Point without user action, while a station is a Point plus a designation; two feedback tiles sharing one s88 address both become Points with distinct generated names, and a user rename survives a rebuild and a change to the surrounding track; a duplicate name is refused at authoring time, not at build time; a name containing a quote character does not silently change on the way into `Point`; designating a station sets `isDestination` and never produces the model's no-s88 exception because every derived Point has one; a legacy virtual point (no s88) is reported as an unanchorable migration case rather than dropped; a feedback tile with no allowed connections is skipped (and counted) rather than emitted as an unreachable node, while one with a single connection is emitted; a `CUSTOM_SCISSORS` on a participating page fails the build with a coordinate-naming error; a degree-2 chain between two significant nodes collapses to exactly one edge with summed length and ANDed direction; a switch in the chain forks into one edge per branch carrying the correct `configCommands`; a `SWITCH_THREE` yields exactly three routes from its toe and **every** route commands both of its addresses with an explicit straight/throw (no address is ever left unspecified); a `SWITCH_CROSSING` contributes commands in **both** states — unswitched for its N-S/E-W throughs and switched for its N-W/S-E diagonals; an unpromoted s88 tile collapses away; **mutual exclusion**: two edges sharing a switch tile are locked against each other, two edges sharing a CROSSING are locked, two edges crossing an OVERPASS in different groups are **not** locked but in the same group are, a portal pair counts as one location; reduction is deterministic across two runs |
| `testLockEdgeAnalyzer` (R2) | exposure invariant: asymmetric-but-covered pair → zero suggestions; hand-built unsafe config → exactly the uncovered ordered pairs; portal-pair conflicts; pinned run against `test/autonomy_sanity.json` |

## Verification

1. NetBeans compile; new test classes + `testLayoutTiles`, `testHomeStaging`, `testAutoLayout`.
1b. **CS-layout acceptance** (the point of the editor-mode work): with no local layout path set,
   the Edit button must open the editor in Autonomy mode rather than showing an error; anchors,
   lengths and portals must be assignable and must persist across a restart; the Diagram-mode
   toggle must be disabled with the download affordance visible; and no file under `gleisbilder/`
   may be written (check timestamps).
2. **Connectivity acceptance**: open setup mode on the author's real pages, enable "Show
   connectivity", and visually confirm the engine's reading of the diagram (the debug overlay is
   the point of contact between port maps and reality).
3. Anchor a handful of points on one page → Trace → confirm the review matches known edges,
   adopted segments light correctly in simulate mode (graph window closed), dot follows the train,
   popups/page switches survive via `republish()`.
4. R2: linter report on the real layout first; then confirm suggestions at one known crossing
   match what was authored by hand.

## Changelog drafts (non-technical)

- R1: "Autonomy can now be set up directly on the track diagram: assign your stations to sensor
  tiles, and TrainControl reads the track itself to find the connections between them — switches,
  crossings and all. Running trains light up the actual track on the diagram, with the train's
  position marked."
- R2: "TrainControl can now propose crossing and shared-track locks from the diagram, and check
  existing locks for gaps."
