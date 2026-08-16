# Autonomy on the Track Diagram

Design plan. Started 2026-07-31, substantially revised 2026-08-01. Not yet approved for
implementation.

Read the top three sections first - **Delivery**, **Revised architecture**, and the rulings that
follow them. The **PARKED** section further down is the original anchor-and-trace draft; it is kept
for the persistence model, the editor UX, the NetBeans rules, the monitoring semantics and the
verified port map, and anything in it that conflicts with the revised architecture is marked
superseded.

# DELIVERY: two phases, add-then-remove (author, 2026-08-01)

**Phase 1 — implement the new, disable the old.** Everything new is built and becomes the working
path; the graph window, the autonomy JSON form and import, and the legacy editing surfaces are
**disabled** (hidden/greyed, code left in place) rather than deleted.

**Phase 2 — remove the old.** Once Phase 1 has run against the real layout, delete the disabled
code: `GraphViewer`, `GraphEdgeEdit`, `GraphRightClick*`, `GraphLocAssign`, `GraphLocExclude`,
`graph.css`, `autonomyJSON` and its import path, the `autonomy.json` read/write paths, the
`GRAPH_*` preferences, and the two GraphStream jars from `nbproject/project.properties`.

**Startup behaviour changes at the Phase 1/2 boundary**, so state it explicitly in each phase:
Phase 1 still auto-loads `autonomy.json` when no companion configuration exists (so an upgrade
mid-phase never leaves a working layout unusable); Phase 2 drops that fallback and the active
companion configuration becomes the only thing that loads. The author's own layout must be
re-created on the diagram — with the harness-emitted starter file — **before** Phase 2 lands.

**Why this order:** a dependency that was only reachable through the old UI shows up as a *disabled
feature that turns out to be load-bearing*, not as a compile error or a silent data-loss bug. The
`autonomyJSON` save path is the known example — it is a persistence mechanism wearing a text
area's clothing — and the point of Phase 1 is to find the ones nobody has spotted yet.

Rules for Phase 1:
- Disabled code is **not** left half-wired: the old paths must be genuinely unreachable from the
  UI, so any remaining caller is a real dependency rather than a stale menu item.
- Nothing is deleted in Phase 1, including jars and preferences, so a problem is one toggle away
  from being reversible.
- Phase 2 starts only after the ground-truth diff and a period of real use on the author's layout.
- Each removal in Phase 2 is verified by compiling and running, not by grep alone — the
  `autonomyJSON` case shows that a field's *name* does not reveal what it does.

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

This retires Release 2 entirely (see its section below). A diagram-derived configuration cannot
have lock gaps by construction, and with legacy gone there is nothing hand-authored left to audit.

## What this simplifies

- `SegmentFloodFill` — **deleted**, no longer needed.
- Paint-overrides — **deleted** from the companion schema and the UI.
- `tileDirections` — **retained** and keyed by tile (2026-08-16 ruling); the briefly-considered
  `connectionOverrides` keyed by tile pair is dropped.
- The Trace & Review table stops being the primary instrument. Review happens on the diagram,
  where the connections are. The table survives only as a summary of the *reduced* graph
  (edges, lengths, config commands, derived locks) for a final read-through before load.
- `LockEdgeAnalyzer` and `LockEdgeReviewUI` are **deleted outright**; what survives is a lock
  *explainer* (why are these two edges exclusive - which tile do they share).
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
- Virtual points (a Point with no s88) can no longer be authored from the diagram at all, and
  with legacy autonomy removed nothing in the product produces them. They remain legal in the
  model, so the **ground-truth harness** reports any it finds in the author's old file as
  unanchorable - the evidence for whether they were load-bearing. This retires the earlier draft's
  "virtual points anchor on a plain track tile" idea.

**Naming (author directive, 2026-08-01).** Every derived Point is **user-nameable**, and stations
carry meaningful names such as "Track 14 entrance", so that logs, timetable output, the graph
inspector and autonomy debug messages are readable rather than coordinate soup.
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
  about. The ground-truth harness must therefore *match* legacy edges onto a **subset** of the
  derived graph rather than expect a 1:1 correspondence.

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

## Editing connections: click to cycle, bulk select, switches per branch

**Author rulings, 2026-08-01.**

- **Scale is a non-issue.** Most of a track diagram is blank, so there is no need to hide default
  connections behind hover. Draw them all.
- **Direction is a property OF THE TILE** (author, 2026-08-16): it specifies which way you may
  move *through that tile*. Clicking cycles both -> one way -> the other way -> none -> both, and
  the arrow drawn on the tile always shows the current meaning.
  - This settles the storage question: the companion holds **`tileDirections`**, keyed by
    `"<page>:<x>,<y>"` (plus a route index for multi-route tiles), **not** `connectionOverrides`
    keyed by tile pairs. A tile owns its own state, so two neighbours can never disagree about the
    link between them, and traversal simply requires every tile on a path to permit the direction
    (the AND rule already specified).
- **Bulk selection and editing**: rubber-band or shift-click a set of tiles and apply one state to
  all of them. This is how a one-way loop gets set in one gesture instead of forty clicks.
- **Switches: the user specifies allowed directions per branch** (author, 2026-08-16).
  **This supersedes the earlier "switches derive themselves from their neighbours" ruling.**
  Derivation could not answer what happens when a switch's neighbour is another switch - there is
  no non-switch authority anywhere in such a run - so a switch is authored directly instead.
  - A switch has one direction state **per branch route** (`SWITCH_LEFT`: the `{NS}` route and the
    `{SW}` route; `SWITCH_THREE`: three; `SWITCH_CROSSING`: four across its two states), each
    cycling the same both / one way / other way / none.
  - **Control is per branch, not per switch** (author, 2026-08-16). Each branch carries its own
    state, and **most branches will not be bidirectional** - opening a whole switch at once is not
    the common case, so there is no per-switch shortcut.
  - **A branch can be set to none**, exactly like a straight tile with both directions off: that
    branch is not traversable in either direction and no edge is derived through it. This is how a
    physically-present but operationally-unused route is excluded.
  - Clicking a switch selects the branch nearest the click; the panel also lists that switch's
    branches explicitly, since clicking accurately on a diagonal is fiddly, and each branch shows
    its state (both / one way / other way / none).
  - **Default is base -> forks** (author, 2026-08-16): flow runs from the base (the toe) out to
    the branches, and the opposite direction is off until the user enables it. This gives an
    implicit, deterministic reading of every switch on the diagram while leaving the user in
    control - nothing is guessed, and nothing silently permits a move the user did not intend.
    The toe is the **S** side at orientation 0 for every turnout (verified port map), rotated with
    the tile.
  - **Note the deliberate asymmetry with `CUSTOM_PERM_*`**: a broken, address-less switch permits
    *only* trailing moves (into the toe) because the blades cannot be commanded, whereas a working
    switch *defaults* to only facing moves (out of the toe). They are exact opposites, and both
    are correct for their own reason. Do not "harmonise" them.
  - **Practical consequence, priced deliberately**: a great many layouts need trailing moves - a
    passing siding is entered through a facing switch and rejoined through a trailing one, so with
    this default the rejoin is blocked until enabled. Expect enabling the opposite direction to be
    a routine part of setup, which makes the bulk-select affordance load-bearing rather than a
    convenience. It also gives the ground-truth diff a useful signal: legacy edges that appear as
    "only in legacy" will largely be the trailing moves not yet enabled.
  - The `CUSTOM_PERM_*` restriction still ANDs on top: it is broken hardware, not a user choice,
    and no per-branch setting can re-open a facing move.
  - **The base-to-forks default does not apply to a route the hardware already restricts** (found in
    implementation, 2026-08-16). On a defective turnout the default would say "out of the toe only"
    while the blades say "into the toe only"; ANDing those leaves the tile impassable in both
    directions, silently deleting track that is perfectly usable trailing. Being unable to choose a
    fork is precisely why driving out of the base is not a sensible default there, so such routes
    default to unconstrained and let the hardware restriction stand alone.
- Multi-route tiles (DOUBLE_CURVE, CROSSING, OVERPASS) hold one state per route; a click cycles
  the route nearer the click point, and bulk apply sets both.

## The autonomy editor replaces the graph window (author, 2026-08-01)

**"The autonomy JSON becomes a bolt-on to any track diagram."** The diagram becomes the single
place autonomy is defined, operated and watched, and the GraphStream window stops being required.

**What the graph UI does today, and where each part goes.** Taken from
`GraphRightClickPointMenu` (1249 lines), `GraphRightClickGeneralMenu`, `GraphEdgeEdit`,
`GraphLocAssign`, `GraphLocExclude`:

| Today (graph) | Fate |
|---|---|
| create point, delete point | **gone** — every s88 tile is a Point |
| connect to point, add / delete / copy edge | **gone** — edges are derived from the tile graph |
| edit edge (length, lock edges, config commands) | **gone** — lengths are per-tile, locks automatic, commands derived |
| node drag / x-y layout | **gone** — position *is* the tile position |
| rename point; mark station / terminus / reversing / active | **rehomes** to the Points tool |
| max train length, speed multiplier, advanced parameters, excluded locomotives | **rehomes** to the Points tool |
| place locomotive at node, clear locomotives | **rehomes** to the Locomotives tool |
| home locomotives | **rehomes** to the Locomotives tool |
| start / stop autonomy gracefully | **rehomes** to the autonomy toolbar |
| hide inactive points, hide reversing edges/stations, show home locomotives, show lengths/exclusions | **rehome** as diagram overlay toggles |
| **test connection** (valid/invalid paths from a point, with the reason each is invalid) | **must be rebuilt** — the one genuinely graph-shaped feature |

**Two surfaces, not one** (author, 2026-08-01). Autonomy work happens on the track diagram in
*both* places it appears, split by what the user is doing:

| Surface | Purpose | Carries |
|---|---|---|
| **Viewer** — the normal track diagram tab | day-to-day operation and watching | configuration dropdown (select / save / duplicate), layer-visibility toggles, locomotive placement and homes, point/station labels, start/stop autonomy, connectivity and configuration tests, live monitoring |
| **Editor** — `LayoutEditor` in Autonomy mode | structural setup, done once and revisited rarely | connections, point properties, portals, per-tile lengths |

Point properties and locomotive placement are reachable from **either** surface — the viewer for
"put this loco here right now", the editor for "set this point up properly".

**Configurations are tied to diagrams.** A dropdown in the viewer selects, saves and duplicates
named configurations, which live in the companion file beside the diagram they belong to. Their
primary use is **placing locomotives in different places** — the same physical layout, different
starting arrangements. Structural data (connections, lengths, point properties, portals) is shared
across all configurations; only placements, homes, exclusions, globals and the timetable vary.

**Layer visibility toggles (viewer).** Rather than fixed decoration, the user turns components on
and off. At minimum: point/station **labels**, **locomotive positions**, **home locations**,
connection **directions**, per-tile **lengths**, **exclusions**, and the live **monitoring**
states. These replace the graph's `hideInactivePoints` / `hideReversingEdges` /
`hideReversingStations` / `showHomeLocomotives` / `showLengthsExclusions` options, which map onto
the same mechanism.

**Locomotive overview.** The user needs to see at a glance *where locomotives live*. On the
diagram that is the locomotive layer: each occupied point badged with its locomotive, homes shown
distinctly from current positions. Pair it with a compact list beside the diagram (locomotive ->
point, home, excluded-from count) so the answer is available both spatially and as a roster —
the roster is what survives when locomotives sit on pages the user is not currently looking at.

**Testing (replaces "capture commands").** `GraphEdgeEdit`'s capture-commands feature is **not**
carried over — derivation makes it unnecessary. Two test affordances replace it:
- **Test connectivity between two points**: pick A and B, get the path(s) A->B with the switch
  settings each requires, or the reason none exists. Directional, so A->B and B->A are asked
  separately.
- **Test a configuration**: validate the whole thing — every point reachable, no orphaned
  placements, no contradictions — and report as a list rather than a single pass/fail.

**The editor, concretely.** `LayoutEditor` in Autonomy mode: the diagram rendered as it looks at
runtime, with an overlay layer and a tool selector.

1. **Connections** (default) — click a tile to cycle, rubber-band for bulk; switch branches
   authored per branch (default base -> forks); portals drawn as linked pairs.
2. **Points** — s88 tiles are highlighted automatically since they are Points by definition.
   Click one to name it, designate it a station, and set terminus / reversing / active,
   max train length, speed multiplier and excluded locomotives.
3. **Portals** — name link tiles and pair them; pair tunnels.
4. **Lengths** — per-tile length, with the totals overlay.
5. **Locomotives** — place, clear, and set homes, per named configuration.

Standing UI beside the diagram: the configuration selector, the live validity banner
(`validateScratch`), the warnings list, and the **reduced-graph inspector** (points, edges,
lengths, config commands, derived locks) - a readable view, **not** JSON.

**Path tester, rebuilt on the diagram.** Select a point, and every point reachable from it tints
green while unreachable ones tint red; hovering an unreachable point gives the reason, reusing
today's `labelValidPaths` / `labelInvalidPaths` / `labelReason` vocabulary. Selecting a second
point narrows it to the A->B case above, showing the actual tile path lit on the diagram. This is
strictly more readable than the graph version because the answer lands on the real track geometry.

**The graph window is REMOVED** (author ruling, 2026-08-01). The track diagram view shows the same
information, so the GraphStream window is not kept as a legacy editor or as a read-only view.
- Scope of the deletion: `GraphViewer`, `GraphEdgeEdit`, `GraphRightClickPointMenu` (1249 lines),
  `GraphRightClickGeneralMenu`, `GraphLocAssign`, `GraphLocExclude`, `graph.css`, and the graph
  plumbing in `TrainControlUI` (`renderAutoLayoutGraph`, the `"GraphCallback"` registration, the
  graph-window gates).
- **The GraphStream dependency goes with it.** `org.graphstream` appears in only three files
  (`GraphViewer`, `GraphEdgeEdit`, `TrainControlUI`), and two jars leave the classpath:
  **three** jars leave the classpath: `resources/gs-core-2.0.jar`, `resources/gs-algo-2.0.jar`
  and `resources/gs-ui-swing-2.0.jar` (`nbproject/project.properties:35-37, 47-49`; the Readme's
  dependency list names all three). Smaller distribution, three fewer libraries to keep current.
- **The JSON form and import go too** (author, 2026-08-01). The autonomy tab becomes **control and
  settings only**. Verified against the actual widget tree (`locCommandTab` and its panels):
  - **Stays**: `startAutonomy`, `gracefulStop`, `returnHomeButton`, the whole `timetablePanel`
    (`timetableCapture` + `executeTimetable` - the timetable's capture button is a *control*
    feature, unrelated to `GraphEdgeEdit`'s retired capture-commands), `autoLocPanel` (semi-
    autonomous locomotive commands), `autoSettingsPanel` (the per-configuration globals), and the
    `autosave` checkbox (still gates saving locomotive state on exit, now to the companion).
  - **Stays, retargeted to files** (author correction, 2026-08-16): **import and export of a
    graph via `autonomy.json` files**, for users who set up their autonomy in pure code. Export
    dumps the currently loaded `Layout.toJSON()` to a chosen file; import reads a chosen file
    through `parseAuto` exactly as pasting JSON used to. Import is **session-only** (author,
    2026-08-16): code-only users do not use the UI, so nothing is remembered across restarts and
    no "external configuration" concept exists - the file is the code-first user's own artifact.
    The `autosave` checkbox goes - **autosave is implicit** (the author will adjust the Matisse
    form if needed).
  - **Goes**: `autonomyJSON` (the `JTextArea` at `:3643`/`:5729`) and its scroll pane,
    `validateButton`, `loadDefaultBlankGraph`, `jsonDocumentationButton` (repoint or drop with the
    docs rewrite), and `reopenGraphButton` (no graph window).
  - **Added**: an **Initialize autonomy** button - the entry point that creates the autonomy files
    and first configuration for the current diagram and opens the editor in Autonomy mode. It
    replaces `loadDefaultBlankGraph` as onboarding.
  There is no JSON *editing* in the UI - the configuration is the track diagram; the JSON file
  import/export exists for the code-first workflow, not as an editing surface.
- **Persistence must move first.** `autonomyJSON` is not just a view: `saveState` populates it from
  `getAutoLayout().toJSON()` (`:1161`) and writes that text to `autonomy.json` (`:1177-1197`), with
  `:1784` reading it back. Deleting the text area without first rerouting save/load through
  `AutonomyCompanionStore` silently breaks saving. This is the clearest instance of the hazard the
  two-phase plan below exists to catch.
- **Legacy autonomy goes away entirely** (author ruling, 2026-08-01). `autonomy.json` is no longer
  read or written by the application. There is no legacy mode, no dual path, no import, and no
  migration feature — which removes a large amount of conditional complexity the earlier draft
  carried (mode detection, trace-and-match adoption, legacy-vs-derived gating throughout).
- **What auto-loads instead** (refined 2026-08-16): the UI lists every configuration the user
  has; **active = the last one the user used**, updated automatically whenever a configuration is
  loaded, and recorded in the layout folder's global autonomy file. On startup, if configurations
  exist, the active one is compiled and loaded through the normal build -> `parseAuto` -> validate
  flow (so placements and station labels populate, as today); **turning autonomy on runs the
  last-used configuration** without further ceremony - if the user switched configurations in the
  dropdown, that switch already loaded it and made it the active one. If no configurations exist,
  autonomy is simply not configured - the same state a fresh install is in today - and the start
  button points the user at Initialize autonomy.
- **The cost this carries, which should be priced deliberately**: with no import, an existing setup
  is not transferred — every point name, length, home, exclusion and per-point property must be
  re-entered on the diagram. For the author's own layout that is ~104 edges, 212 lock references,
  plus names and homes. Connections and locks are re-derived automatically, so the geometry is
  free; what is lost is the **authored** data.
  *Recommendation*: have the ground-truth comparison harness (which already parses the real
  `autonomy.json` to diff against) also **emit a starter companion file** — matched point names,
  lengths distributed onto tiles, homes and exclusions carried over. This is a **developer-run
  one-off**, not a shipped feature or a UI: it produces a file the author reviews and keeps, and
  it disappears with the harness. That preserves the clean cutover in the product while avoiding
  a day of retyping.
- Retire `GRAPH_*` preferences and the always-on-top coordination between the graph window and the
  main window as part of the same removal.

**Consequence for monitoring.** `updateStationLabels` no longer depends on the graph window being
open — already planned — and now that is not an enhancement but a requirement, since the graph may
never be opened at all.

## Rulings, 2026-08-16

### Model APIs do not change (invariant)

The author expects **no material change to `Layout`, `Point` or `Edge`**. Verified against the
source: everything this design needs is already public - `Layout.getActiveLocomotives()`,
`getPointsInActivePath()`, `getEdges()`, `getPoints()`, `getLocomotiveLocation()`, `isRunning()`,
`setCallback()`, `renamePoint()`; `Edge.getName()`, `getStart()`, `getEnd()`, `getLength()`/
`setLength()`, `getConfigCommands()`, `getLockEdges()`, `addLockEdge()`; and `Point`'s name,
`isDestination`, terminus/reversing/active, s88 and max-train-length accessors. Construction runs
entirely through `Layout.fromJSON`, so nothing new is required there either.

Three invariants keep it that way - without them the implementation will drift into the model:

1. **The edge -> tile-path map lives outside `Edge`.** Each reduced edge retains the tiles it
   covers, for monitoring and lock derivation. That is a **side index owned by the builder**, keyed
   by `Edge.getName()` (which already exists and is stable) - never a new field on `Edge`.
2. **Warnings live in the build result, not on `Layout`.** `Layout` has `invalidate(msg)` for
   blocking errors and nothing for non-blocking warnings. Adding a warnings channel to `Layout`
   would be a model change; returning them from the builder is not.
3. **One-way is the absence of the reverse edge.** The model's edges are already directed, so
   directionality needs no new field, flag or API.

### What is a node (author, 2026-08-16)

**Only s88 tiles are nodes in the reduced graph.** A run of straight/curve/feedback-free tiles
collapses into one continuous segment, and **a switch is not a node either** - it is a branch point
that forks the walk and contributes `configCommands`. So the reduced graph's vertices are exactly
the s88 Points, which is what the autonomy model wants.

The **tile graph** still deals in individual tiles, because direction and length are per-tile
properties and monitoring lights individual tiles - but nothing needs a materialised node object
per straight tile. It is computed from the diagram plus the companion's per-tile overrides, and
runs of tiles are walked, not stored.

### Point properties: shared vs per-configuration

| Property | Where | Why |
|---|---|---|
| name | **shared** | describes the physical location |
| station designation (`isDestination`) | **shared** | ditto |
| terminus, reversing | **shared** | physical characteristics of the track there |
| max train length | **shared** | a physical capacity |
| **speed multiplier** | **per configuration** | author ruling |
| **priority** | **per configuration** | author ruling, 2026-08-16 |
| active | **per configuration** | operational - a point may be taken out of use for a session |
| excluded locomotives | **per configuration** | operational |
| placements, homes | **per configuration** | the reason configurations exist |

**New configuration = copy of everything** (author, 2026-08-16): every per-configuration field -
point properties, priority, speed multipliers, placements, homes, exclusions, globals, timetable -
is copied when a configuration is created from an existing one. With one file per configuration,
that is a file copy.

**Locomotive autonomy properties stay editable in the UI** (author, 2026-08-16): `reversible`,
`arrivalFunc`, `departureFunc`, `trainLength` and preferred speed are stored on the `Locomotive`
(LocDB), not in the autonomy files - but the placement UI (viewer and editor Points tool) must
still expose editing them, as the graph right-click and the diagram edit-locomotive-properties
shortcut do today. Placements themselves store only the locomotive name.

### Saving

- **The editor saves explicitly when closed.** Structural edits (connections, lengths, points,
  portals) are written to the companion on close, not continuously.
- **Locomotive location state auto-saves on exit**, and a warning is shown if autonomy is still
  running - **matching today's behaviour exactly**, not a new pattern.

### Selecting a configuration loads it

Choosing a configuration in the viewer's dropdown **compiles and loads** it - directly analogous to
reading `autonomy.json` today. It is therefore **refused while autonomy is running**, the same gate
that already guards structural changes.

### Tile properties move with the tile

Direction and length are **properties of the tile** and **move with it** when the diagram editor
moves it. If the tile is **deleted**, its properties go with it - start over for that tile. No
orphan-and-report machinery for tile properties (unlike point references, where orphans are
preserved).

### Overlays: excluded pages and popup windows

- **Not on excluded pages.** An excluded page is outside autonomy entirely - no derivation and no
  overlays.
- **Yes in popup windows.** `LayoutPopupUI` popups must show **the same controls as the main
  window and stay in sync** with it.
- **This needs extensive parity test cases.** A popup is a second live view of the same tiles, so
  every layer toggle, overlay state and interaction has to behave identically and update together.
  Treat popup/main-window parity as its own test area rather than a detail of the monitor tests.

### Renaming a point propagates

A rename **must propagate** everywhere the name is referenced - timetable entries, homes,
exclusions, placements and every configuration in the companion - not merely be refused when
references exist. The known wrinkle is the `Point:` diagram label, which embeds the name inside the
diagram file: resolve leniently and report a mismatch rather than rewriting the diagram file.

### Reversing points and termini get no special casing

Direction is uniform: a reversing point or terminus is subject to exactly the same per-tile
direction rules as anything else. A reversal traverses tiles in the opposite direction, so **the
user must set those tiles bidirectional**; if a reversal is blocked by a one-way tile, that is the
user's to fix, not the reducer's to work around. No exemption, no implicit widening, no special
path in `GraphReducer`.

What the build does instead is **tell the user**: a **terminus with no path to any other station**
raises an alert naming it. That is nearly always a direction mistake rather than an intended
configuration, and it is cheap to detect once the reduced graph exists. The same check generalises
usefully - any station that can reach no other station, or that nothing can reach, is worth
reporting - and it belongs in the in-app check suite below alongside the static configuration
check.

### The reduced graph is inspectable, in the UI

The derived graph must be **visible and clear to the user**, not a black box - a readable view of
points, edges, lengths, config commands and derived locks. This is a normal part of the UI, not a
developer dump, and it replaces the read-only generated-JSON idea (no JSON editing in the UI; the
file-based import/export for code-first users is separate).

**Plus an in-app test suite**: user-runnable checks that validate the configuration is wired
correctly, reporting a list of findings rather than a pass/fail. This is the home for the static
configuration check and the A->B connectivity test, and the place to add more checks as failure
modes are discovered. Starting set:
- **terminus with no path to any other station** (author, 2026-08-16) - almost always a direction
  mistake;
- any station that can reach nothing, or that nothing can reach;
- points with no allowed connections (the isolated-feedback case);
- direction contradictions - a run made impossible in both directions;
- orphaned placements, homes and exclusions in the active configuration;
- unpaired, half-paired or excluded-page-targeting portals.

## IMPLEMENTATION STATUS (living, 2026-08-16)

**Done and tested headlessly.** `TilePorts` (verified port map), `TileGraph` (tile adjacency, portals,
direction, disqualification), `GraphReducer` (Points, edges, commands, lengths, derived locks),
`AutonomyBuilder` (generated JSON + inspection export), `AutonomyCompanionStore` (persistence, page
ids, reconciliation), `TileOverlay` + `DiagramMonitor` (what each tile should show).
Gate cleared against `cs2_sample_layout`.

**Also done.** `AutonomyChecks` (the user-runnable checks), `DiagramTileRegistry` and the
`LayoutLabel` paint hook.

**Still to build.** The editor's autonomy panel; the viewer's autonomy panel; the path tester; the
reduced-graph inspector; then Phase 2 removal and the documentation rewrite.

### Integration constraints, verified in the code (2026-08-16)

Investigated before writing any UI, because several plan assumptions turned out to be wrong:

- **There are no hand-written `JPanel` subclasses in `gui/` at all.** Every one has a matching `.form`.
  The plan's "18 of 37 are hand-written" counted popup menus and plain classes; the two autonomy panels
  will be the first hand-written panels in the project. The style to follow is `LayoutGrid`
  (constructor-built widgets, `GridBagLayout`, `TrainControlUI` passed in) rather than any panel.
- **Mount points that work without touching generated code**: the editor's `newComponents`, whose
  layout manager is already replaced from hand-written code (`LayoutEditor.java:126`), and the viewer's
  `LayoutArea.setRowHeaderView(...)`, whose row and column headers are unused. The precedent for
  mutating a form container by hand is `autoLocPanel` (`TrainControlUI.java:15657, :15691`).
- **Mount points that do NOT work**: `layoutPanel` has a populated generated `GroupLayout`;
  `InnerLayoutPanel` and `ExtLayoutPanel` are `removeAll()`ed and have their layout manager replaced on
  every grid rebuild (`LayoutGrid:77, :86-94`).
- **`LayoutLabel` is `final`**, so the paint hook had to go in the class itself - no decorator.
- **`updateImage` short-circuits on an unchanged icon name** (`LayoutLabel:509`), confirming that an
  overlay cannot ride the existing repaint path and must trigger its own.
- **The layout cache re-adds a grid without running `LayoutGrid`'s constructor**
  (`TrainControlUI:16519-16523`), so registration placed there does not re-run on a cache hit. Harmless
  because the cached labels are the same objects and keep their registrations - but a registry that is
  ever cleared wholesale would come back empty, which is why it prunes instead of resetting.
- **Overlays cannot cover station or address text**, which is z-ordered above tiles
  (`LayoutGrid:233, :315`). Acceptable: the wash is background, the text is information.

### Open questions, for review rather than blocking

Decided by best guess, easy to reverse, each isolated to one place:

1. **A route button is transparent** - it carries whatever line it sits on, inferred from its
   neighbours, and nothing when it sits beside the rails. Inferred from how layouts use them, not
   from the art, which shows no track at all. `TilePorts.isTransparent` / `TileGraph.transparentRoutes`.
2. **A signal on a path is commanded green.** Matches the hand-written configurations; the alternative
   is leaving signals entirely to conditional routes. One line in `TilePorts`.
3. **Overlay opacity and the train dot.** A 45% wash and a centred dot, chosen so a whole diagram reads
   at a glance without hiding the track. Nothing depends on the numbers; `TileOverlay`.
4. **Point names default to the coordinate** (`1 - Main 9,12`). Readable but ugly; the alternative is
   the s88, which is not unique. Visible in the exported graph until points are named.
5. **A page renumber is reported, not adopted.** The Central Station orders pages by the id we store
   against, so a renumber could reattach a page of settings to the wrong page. Reported via
   `getPageIdConflicts()`; nothing consumes that report yet - the UI must show it.

## BEFORE R1: what R0 left behind

**1. Message bundle keys do not exist yet.** The engine names keys - `errorScissorsNotSupported`,
`warnTurntableNotRoutable`, `errorTileHasNoAddress`, `errorLinkNotMutuallyPaired` and the rest - but
carries them as plain constants, so nothing looks them up and nothing fails today. `I18n.t()` is
`bundle.getString(key)` with **no fallback**, so the first UI that renders one throws
`MissingResourceException` at runtime rather than showing a blank. Every key must exist in all 8
bundles before any of it is displayed. *Open question for the author: put English text in all eight
pending translation (a German user reads English, but nothing crashes), or only in
`messages.properties`?*

**2. `AutonomyCompanionStore` is not written.** The reducer takes what it needs through an `Authored`
interface, and the harness supplies it directly, which is why R0 could finish without persistence.
The UI has nowhere to save to until this exists, so it is the next piece rather than an R1 one.

**3. A train icon on the diagram** is now possible and is wanted - author, 2026-08-16 - but comes
**last**, after the state overlay works. The overlay answers which track is claimed and reached; an
icon answers which locomotive is where, and is decoration until the first question is right.

## GATE CLEARED, 2026-08-16

Run against `cs2_sample_layout` (`testAutonomyFromDiagram`): **every hand-built connection is
reachable in the derived graph** once switch branches are open, and **every sensor** the
hand-built configuration relies on is derived. The architecture holds on a real diagram.

Four defects had to be fixed to get there, none of which any unit test could have found - each
needed a real layout with a hand-built graph beside it:

1. **A Point's s88 is the RAW address**, not the halved logical one. `CS2File` divides the CS2
   `artikel` value by two for accessories, but feedback is registered by `getRawAddress()`. Using
   the wrong one made 36 of 44 sensors unmatchable, and the 8 that matched were coincidences.
2. **Portals never continued.** `exits()` skipped stub routes and a link's only route is a stub,
   so a link offered no way out; every cross-page route was severed while looking exactly like a
   diagram that had none. A portal now has two ports, the pairing addressed as a null side.
3. **Route buttons sit ON the line.** Treating `fahrstrasse` as decoration severed every run
   containing one - 43 of them in this layout. They are now *transparent*: `TilePorts` declares
   them so, and `TileGraph` reads what they carry off the neighbours.
4. **Unaddressed switches** are refused rather than routed over as though already set right.

Two lessons worth carrying into R1, both about tests rather than code:
- Fixtures that set raw and logical addresses equal agreed with defect 1; a portal test that asked
  `landing()` for the partner rather than walking through agreed with defect 2. **A test that
  exercises the piece just written, rather than what a train needs, will agree with the bug.**
- Tolerating a missing link tile made a stale coordinate indistinguishable from a diagram with no
  cross-page routes, costing a whole round. **A portal that cannot be resolved must be loud.**

Remaining differences are not defects and are expected to persist:
- **52 connections unreachable as authored** vs 0 with branches open - the base-to-forks default,
  i.e. the trailing moves a user has still to enable. This is the authoring workload, and it is
  large: expect enabling trailing to be most of the setup effort on a real layout.
- **4 config-command disagreements**, all of them differences in authoring convention rather than
  wrong readings: the hand-built file commands signals beyond an edge's own extent (e.g. an exit
  signal ahead of the stopping point), and some derived paths take a shorter route between the
  same two sensors than the hand-built edge did.
- **Derived edges outnumber legacy ones** once branches are open (164 vs 92), and Points 58 vs 62,
  both expected: every s88 becomes a Point and every branch becomes an edge.

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
- **Source of truth** = diagram pages + autonomy files **owned by TrainControl**, never by the
  Central Station. **Location (superseded 2026-08-16): inside the track diagram folder** - one
  global file for general state, one file per user configuration. **Backups snapshot the entire
  track diagram folder** (author, 2026-08-16), replacing the old `Util.getBackupPath` single-file
  copy of `autonomy.json`: diagram pages and autonomy state are versioned together, so a restored
  backup is always internally consistent.
- **Autonomy is LOCAL-LAYOUT ONLY** (author ruling, 2026-08-16, superseding the earlier
  not-gated-on-`isLocalLayout()` decision). The autonomy files live inside the track diagram
  folder, so a folder must exist. A user on a Central-Station-sourced layout who tries to
  **Initialize autonomy** gets a warning explaining that autonomy needs a local copy of the
  diagram, with an offer to download it; **on OK, TrainControl runs the existing Download CS
  layout flow for them** (`:14219`), switches to the downloaded local layout, and proceeds with
  initialization. No working-directory fallback, no mirror - one storage rule.
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
  tileDirections}, configurations: {"<name>": {...}}, activeConfiguration}`. `tileLengths` is a sparse map keyed
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
- ~~**Legacy mode**~~ — **removed 2026-08-01.** `autonomy.json` is neither read nor written; there
  is no mode detection, no user-facing migration, and no legacy-vs-derived branching anywhere.
  The only thing that loads at startup is the active configuration from the companion file.

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

**SUPERSEDED 2026-08-01 — everything is confined to JPanels, hand-written, no designer.** The
author's ruling: *"confine everything to the track diagram and editor JPanels — that way I don't
need to step in and mess with the frames, and there won't be a NetBeans UI editor dependency."*

| Piece | Form? | Who builds it |
|---|---|---|
| Every new autonomy UI — the editor's autonomy panel, the viewer's autonomy panel, tool strips, tables, dialogs | **no `.form`** | written outright as hand-written `JPanel` subclasses, in the `LayoutGrid` / `GraphRightClickPointMenu` style |
| Right-click menu items, prompts, small confirmations | no | hand-written, `JOptionPane` style |
| All engine classes (`TilePorts`, `TileGraph`, `GraphReducer`, `AutonomyCompanionStore`, `AutonomyBuilder`, `DiagramMonitor`, `DiagramTileRegistry`, `TileOverlay`) | n/a — headless, no Swing | written outright |

Consequences that make this work:
- **No new `JFrame`.** New UI mounts into containers that already exist — the editor window and
  the main window's diagram tab — so no frame has to be reshaped in the designer and the author is
  never a blocker on UI work.
- **No `.form` is created or edited, and no generated block is touched.** Where a panel must be
  attached to a Matisse-built class (`LayoutEditor`, `TrainControlUI`), the attachment happens in
  hand-written code outside `//GEN-BEGIN:initComponents` and `//GEN-BEGIN:variables`, adding into
  an existing container. Both `.form` files stay byte-identical and their Design views keep
  working.
- Layout inside the new panels uses ordinary hand-written managers (`BorderLayout`, `GridBagLayout`,
  `BoxLayout`), never generated `GroupLayout`.
- The author remains available to help with frame-level work *if it proves unavoidable* — but
  needing it is a signal the design has drifted out of the panel, and should be raised rather than
  worked around.

**Working rule, either way:** never hand-author or hand-edit `.form` XML, and never edit a
regenerated block. In a Matisse class the regenerated regions are `//GEN-BEGIN:initComponents` …
`//GEN-END:initComponents` and `//GEN-BEGIN:variables` … `//GEN-END:variables` (in `LayoutEditor`,
`:1505-1654`). The `//GEN-FIRST:event_x` … `//GEN-LAST:event_x` handler **bodies are the intended
place for hand-written code** and may be filled freely, as may everything outside the guarded
regions (`LayoutEditor` lines 1-1504).

Consequence for review: a diff that edits `.form` XML directly, or edits a line inside
`GEN-BEGIN:initComponents`/`GEN-BEGIN:variables`, is a defect. Widgets appearing there must have
arrived through the designer.

**No scaffolding handoff is needed.** The widget inventory that follows is retained as a
*specification of what the panels must contain*, not as a contract for an author-built form —
the panels are written directly, so names and layout are an implementation detail.

---

## ~~Scaffolding inventory~~ — OBSOLETE (2026-08-01)

Removed. All new UI is hand-written `JPanel`s inside existing frames, so there is no designer
handoff and no widget-name contract to agree in advance. Panel contents are specified in the class
table (`AutonomyEditorPanel`, `AutonomyViewerPanel`); field names are an implementation detail.

## Where autonomy editing lives: a mode of the existing diagram editor

**Decision (2026-08-01): no separate setup window.** Autonomy setup is a *mode* of `LayoutEditor`,
the window the user already knows for working on the diagram. Same window, same grid, same tile
interaction, same undo affordance.

**The editor gains a mode switch** (toolbar toggle at the top, persisted in prefs):

| Mode | What it does | Requires `isLocalLayout()` |
|---|---|---|
| **Diagram** | today's editor exactly — place/rotate/erase tiles, addresses, text, row/col tools | **yes** (it writes `gleisbilder/`) |
| **Autonomy** | Connections / Points / Portals / Lengths, live validity banner | **yes** (2026-08-16 ruling) — autonomy files live in the layout folder; a CS layout offers download-then-continue |

**Central Station layouts: download first** (author ruling, 2026-08-16, superseding the earlier
open-in-autonomy-mode-anyway design). Autonomy is local-layout only, because its files live in
the track diagram folder. `editLayoutButtonActionPerformed` (`TrainControlUI.java:12461`) and the
**Initialize autonomy** button behave consistently on a CS-sourced layout: a warning explains
that autonomy (and diagram editing) need a local copy, and offers to download it; on OK the
existing *Download CS layout* flow (`:14219`) runs, the local layout is activated, and the
original action continues. A local layout opens the editor in whichever mode was last used, both
modes enabled.

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
runtime right-click menus, `LayoutRightclickAutonomyMenu` on a station label, and the viewer's
autonomy panel — and continue to mutate the live `Layout`, saving back to the active
configuration. Nothing about running autonomy moves.

## The user-facing setup workflow (what ships)

Supersedes the anchor-based workflow this section used to describe.

1. **Open the diagram editor, switch to Autonomy mode.** Works for Central-Station-sourced layouts
   too; only Diagram mode needs a local copy.
2. **Points already exist.** Every s88 feedback tile is a Point automatically. Name the ones that
   matter, designate stations, and set properties (terminus / reversing / active, max train length,
   speed multiplier, exclusions, home).
3. **Check the connections.** Geometry has already proposed them. Click a tile to cycle
   `both -> one way -> other way -> none`; rubber-band to bulk-set a one-way run. Switches derive
   themselves from their neighbours and need no attention.
4. **Pair the portals.** Name each link tile and say which link it jumps to; pair tunnels likewise.
   Unnamed links simply do not connect.
5. **Set lengths** where train-length limits matter - per tile, 0 and up, summed per edge.
6. **Exclude duplicated pages** from autonomy.
7. **Validate as you go.** The banner re-derives the whole graph after every edit against the real
   `Layout` semantics; run the static configuration check and the A->B connectivity test to confirm
   anything specific.
8. **Save as a named configuration.** The one you used last is remembered and is what runs when
   autonomy is turned on. Duplicate it for variants that differ only in where locomotives start.
9. **Run and watch.** The same diagram lights up - reserved / current / completed / locked - with
   locomotive positions and homes on toggleable layers.

## Milestones (restructured 2026-08-01)

**R0 - the engine, headless, no UI.** `TilePorts`, `TileGraph`, `GraphReducer`,
`AutonomyCompanionStore`, `AutonomyBuilder`, plus the ground-truth comparison harness and its
starter-companion output. Ships nothing user-visible; its deliverable is the **diff report against
the real `autonomy.json`** and a companion file the author keeps. This proves the architecture
before a panel is written, and is the natural place to stop if the report shows something
structural.

**R1 - the UI.** The editor's autonomy mode, the viewer's autonomy panel and layers, monitoring,
and the two test affordances. Phase 1 of the delivery split (implement new, disable old).

> **Link naming and pairing is a migration gate** (author, 2026-08-16), not just one tool among
> several. A link tile today stores only the index of the diagram it points at, which cannot say
> *which tile* on that page continues the track - so until the editor can give each link a unique
> name and name the link it jumps to, a layout that uses links **cannot be expressed in the new
> format at all**. Any layout with links therefore cannot be converted, and the ground-truth diff
> will show its cross-page routes as missing for a reason that has nothing to do with the
> reduction being wrong. Build this early in R1 and confirm it against the author's real pages
> before reading anything into the diff.

**R2 - removal.** Phase 2 of the delivery split: the graph window, the JSON form, the
`autonomy.json` paths, `GRAPH_*` preferences and the GraphStream jars. Gated on the author's
layout being re-created on the diagram. *(Replaces the former "Release 2 - lock-edge derivation",
deleted outright - see below.)*

**R2 also carries the documentation.** `Automation.md` is today a JSON-authoring tutorial end to
end - sample JSON, "paste into the Autonomy tab", "Validate Graph", graph colors/shapes, graph
prettification - and most of it is obsoleted by this design; the Java-API sections stay valid
because the model does not change. `Readme.md` needs: the feature list and autonomy blurb
(:126-142, :164-175), the backup-file list (:333 - `autonomy.json` becomes `autonomy-setup.json`),
the dependency list (:346 - three GraphStream jars leave), and fresh screenshots (`graphview.png`,
`graph2b.png`, `ui_autonomy.png`, `easyauto.png`, `graph*.png` all show the graph window). The
non-technical changelog rule applies to the Readme changelog only, not to these docs.

### Component fates (author rulings, 2026-08-01)

| Component | Fate |
|---|---|
| `AutoLocomotiveStatus` | **Keep as-is.** It is for *watching operation*, not editing the graph. It coexists with the viewer's locomotive roster rather than being replaced by it. |
| `HomeLocomotiveMenu` | **Moves into the diagram** - home assignment happens while editing stations in autonomy editing mode, alongside the point's other properties. |
| `LayoutRightclickAutonomyMenu` | **Functionality stays**, largely unchanged - the quick operational menu on a station label at runtime. |
| Timetable | **Stays where it is**, unchanged; it references whatever points now exist, resolving by name as before. |
| Global autonomy settings | **Per configuration**, as today; edited on the slimmed autonomy tab against the *active* configuration. The full set the builder must emit (from `Automation.md` + `fromJSON`): `minDelay`, `maxDelay`, `defaultLocSpeed`, `preArrivalSpeedReduction`, `turnOffFunctionsOnArrival`, `turnOnFunctionsOnDeparture`, `atomicRoutes`, `maxLocInactiveSeconds`, `maxLatency`, `activateRoutes`, `activateRouteIDs`. (`maxActiveTrains` is a UI preference, not a JSON key.) |
| Layer visibility toggles | **User preferences**, not configuration data - a configuration describes the layout, not how someone likes to look at it. |
| `Point:` diagram labels | **Kept as-is.** They are a *placement* mechanism, not a naming one: they say where on the diagram a station's label and train location appear, which nothing else expresses. Naming lives in the companion; placement stays in the diagram file. **Known wrinkle**: the label embeds the name, so renaming leaves it stale - resolve leniently and report a mismatch rather than rewriting the diagram file. |
| Simulation | **Unchanged** - simulate mode keeps working exactly as it does now. |

### New classes (`org.traincontrol.gui` unless noted; engine classes are pure/headless)

| Class | Responsibility | ~Size |
|---|---|---|
| `base/TilePorts.java` | The port map for **all 28 `componentType` values** — see the derived table below. Uniformly state-indexed: `ports(type, orient, state)` returns the set of connected side pairs in that state (unswitched / switched, or the three-way's three states; one state for everything else). No common/branch/toe concepts. Honors `getNumOrientations()` (2 / 1 / 4 by type) rather than assuming 4; encodes the directed restriction for `CUSTOM_PERM_*` (into S only) | 300 |
| ~~`base/DiagramTopology.java`~~ | **Folded into `TileGraph` + `GraphReducer`** (there is no separate trace step: the tile graph *is* the adjacency and reduction is what walks it). Former text: builds the directed adjacency for a set of `LayoutDiagram` pages + portal pairs: nodes = (page,x,y) tiles with ports; `trace(anchorTile)` walks to neighbouring anchors, forking per switch branch, recording tile path + required accessory settings; respects one-way traversal (a facing entry into a `CUSTOM_PERM_*` turnout yields no exits, so that direction produces no connection at all); collects warnings (permanent turnouts encountered) alongside results; cycle-guarded, bounded | 340 |
| `base/ReducedEdge.java` (was `TraceResult`) | Value type: endpoint Points, ordered tile path, accessory requirements, summed length, direction. **No name reverse-engineering** (author, 2026-08-16): the diagram tile knows its exact accessory address and its type (switch, signal), and the loaded tile already holds a live `Accessory` reference (`LayoutDiagramComponent.getAccessory()`), so the builder takes the requirement straight from the tile and emits that accessory's own name into the generated JSON's `commands` (which are name-keyed, as `parseAuto` expects) | 80 |
| `AutonomyCompanionStore.java` | Owns the autonomy files **inside the track diagram folder** (author ruling, 2026-08-16, superseding the working-directory location): one **global** file for general state - `{version, pointNames, stations, portals, linkNames, excludedPages, tileLengths, tileDirections, activeConfiguration}` - plus **one file per user configuration** holding `{pointProperties (active, excluded locomotives, speed multiplier, priority), placements, homes, exclusions, globals, timetable}`. Per-file benefits: a configuration is individually copyable, and new-configuration-as-copy is a file copy. Unknown fields preserved; `version>1` refuses load; configuration CRUD (create-as-copy copies **everything**, rename, delete - never the last one); save on editor close + the exit save path (locomotive state read back from the live Layout into the **active** configuration, implicitly - no autosave checkbox); **page rename propagates universally** - every tile key in the global file and in every configuration file is rewritten in one pass; point rename likewise; orphans kept, never silently dropped | 400 |
| `AutonomyBuilder.java` | The compile step: companion + `DiagramTopology` traces → generated autonomy JSON string fed to the existing `parseAuto` (reusing the whole validate pipeline unchanged); deterministic ordering so output is diffable; emits setup errors (unpaired or half-paired portal, portal targeting an excluded page, disqualified scissors tile) as validate-visible failures, and **warnings** (permanent turnout, turntable, isolated feedback tile, direction contradiction - each with coordinates) that surface in the banner and the log **without blocking the build**; graph x/y are **off by default** - the graph window is going and position is the tile position - but can be switched on (`withCoordinatesFromTiles`) to **export a derived graph for inspection**, laid out like the track it came from so it can be checked against the diagram beside it; edge length = sum of the traced path's per-tile lengths, endpoints excluded (0 when unassigned). Exposes `build()` (JSON string) and `validateScratch()` — `Layout.fromJSON(build(), model)` into a throwaway, returning validity + message **without** `parseAuto`, so the setup UI can re-infer the graph live after every edit | 340 |
| `TileOverlay.java` | `enum SegmentState {RESERVED, CURRENT, COMPLETED, LOCKED}` + marker kind (WASH, DOT, WASH_AND_DOT) + colors + static `paint(Graphics2D,w,h,state,kind)` | 80 |
| `DiagramTileRegistry.java` | `(page,absX,absY) → Set<LayoutLabel>` plus accessory and s88 indexes; `ConcurrentHashMap`/`newKeySet`; prunes `!isParentVisible()` on iteration | 150 |
| `DiagramMonitor.java` | Owns `"DiagramCallback"`: fire = try-catch'd `markDirty()` (AtomicBoolean+semaphore) only; single daemon worker drains, 100 ms debounce, full idempotent recompute, diff-publish to EDT in one `invokeLater`; `republish()` after grid rebuilds; constructor `(Supplier<Layout>, registry, publisher)` for headless tests | 350 |
| `AutonomyEditorPanel.java` (hand-written `JPanel`, no `.form`) | The Autonomy-mode panel inside `LayoutEditor`: non-edit `LayoutGrid` under a `JLayer` that consumes all mouse events (clicks can't throw switches); tools **Connections / Points / Portals / Lengths**; Connections: click a tile to cycle `both -> one way -> other way -> none`, rubber-band for bulk; switch branches authored individually (default base -> forks), with a per-branch list in the side panel since clicking a diagonal precisely is fiddly; Points: every s88 tile highlighted, click to name, designate station, set terminus/reversing/active, max train length, speed multiplier, exclusions **and home**; Portals: name and pair links/tunnels; Lengths: per-tile integer with a totals overlay; per-tool undo deque mirroring `snapshotLayout()`; **live validity banner** from `AutonomyBuilder.validateScratch()` after every edit (debounced, off-EDT) | 700 |
| `AutonomyViewerPanel.java` (hand-written `JPanel`, no `.form`) | The autonomy panel beside the main diagram tab: configuration dropdown listing every saved configuration (select = compile + load + becomes the remembered last-used; save / duplicate / rename / delete; selection refused while running), layer-visibility toggles (labels, locomotives, homes, directions, lengths, exclusions, monitoring), locomotive roster (locomotive -> point, home, exclusion count), start/stop autonomy, and the two test affordances (connectivity A->B, static configuration check) | 450 |
| ~~`SegmentFloodFill.java`~~ | **Deleted by the revised architecture** — paint-overrides no longer exist | — |
| `base/TileGraph.java` (new, revised architecture) | Layer 1: nodes = `(page,x,y)` tiles, candidate connections from facing ports, plus explicit LINK/TUNNEL portal connections; each connection carries a state (disallowed / one-way either way / both) defaulted from geometry and overridable by the user; `neighbors(tile, direction)` honors those states | 260 |
| `base/GraphReducer.java` (new, revised architecture) | Layer 2: contracts the tile graph to the autonomy graph — significant nodes survive, degree-2 chains collapse to one edge, switches fork transparently and contribute `configCommands`, lengths sum, directions AND, tile paths retained; then derives mutual exclusion from shared tiles (OVERPASS cross-group excepted, portal pair = one location) and emits the lock references | 340 |

### Monitoring: an overlay, in the graph's own colours (author, 2026-08-16)

**Requirement.** With autonomy running, the track diagram must show what the graph window shows: where
each train is, which track its path has already covered, and which it has not reached yet. Segments
light red while active and green once reached.

**Drawn as an overlay, not by recolouring the tile art** (author preference, and the code agrees -
this is not a close call):

1. `imageCache` is a **static map shared by every tile** (`TrainControlUI.getImageCache()`), keyed by
   icon name and size. Recolouring a tile's grey interior means either mutating an image every other
   tile of that type is also using, or minting a cache entry per state per orientation per size -
   defeating the cache to render four colours.
2. `updateImage` short-circuits on an unchanged icon name (`LayoutLabel.java:501-513`). Autonomy state
   does not change the icon name, so a recolour would not repaint at all without a forced refresh path
   built alongside it.
3. The transient yellow highlight already works by **replacing** the icon and restoring it from a
   one-slot `lastIcon` (`:435-455`). A second icon-replacing effect would fight it for that slot; an
   overlay painted after `super.paintComponent` simply coexists.

So: `LayoutLabel` gains `volatile TileOverlay overlay` plus a `paintComponent` override that paints
after the icon. Roughly 25 lines, no change to the cache, no interaction with the highlight.

**Colours are the graph's, exactly** - a user who has learned one has learned the other. Read from
`graph.css`:

| State | Colour | Meaning |
|---|---|---|
| active, not yet reached | `rgb(196,0,0)` red | the path is claimed and the train is still coming |
| reached | `rgb(0,196,33)` green | the train has passed this point |
| locked | `rgb(238,238,238)` pale grey | held to keep another path clear |
| idle | none | no wash at all, so a running layout reads at a glance |

Precedence where a tile qualifies for more than one: reached > active > locked.

**Where the train is** needs its own mark, since a wash says which track is claimed but not which
part of it holds the train: the current point is marked distinctly (a centred dot over the wash),
which is the diagram's equivalent of the graph labelling its node.

**Segments, not points.** The wash covers every tile of the edge, which is what the plan means by
segments lighting up - a reduced edge retains its tile path precisely so monitoring can paint it.

### Monitoring semantics (superseded above where they differ)

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
   validate-success funnel (~:12940, where `renderAutoLayoutGraph()` is called today — that call
   goes away; per-Layout callbacks → re-attach per `parseAuto`); extract the `updatePoint` tail
   (now ~:14985 — **re-verify the range**) into `updateStationLabels(Point)` **minus the
   graph-window gate, now mandatory since the graph may never exist**; `layoutStations` pruning
   (:389, :852, :893); `repaintLayout` tail → `republish()`; **replace** the `autonomyJSON`
   save/load path (:1161, :1177-1197, :1784) with `AutonomyCompanionStore` — this must land
   *before* the text area is disabled; host `AutonomyViewerPanel` in the diagram tab.
4. `gui/LayoutPopupUI.java` — `drawGrid()` tail → `republish()`.
5. `gui/LayoutRightclickAutonomyMenu.java` — "Autonomy setup..." menu item (opens the editor in
   Autonomy mode, scrolled to the clicked tile).
5b. `gui/LayoutEditor.java` — the mode switch: a Diagram/Autonomy toggle in the toolbar; hosting
   `AutonomySetupPanel` in a card layout beside the existing edit grid; **Autonomy mode must not
   call `layout.setEdit()`** (`:1409`) so station labels and text keep rendering as in the runtime
   view; Diagram-mode controls disabled and visibly explained when `!isLocalLayout()`; the
   `snapshotLayout()` undo idiom (`:1321`) extended with a companion-state deque. **All of it in
   hand-written code**, mounting `AutonomyEditorPanel` into an existing container — no `.form`
   edit, no `initComponents`/`variables` edit, no new frame.
5c. `gui/TrainControlUI.java` (entry point) — `editLayoutButtonActionPerformed` (`:12461`) and
   the new Initialize-autonomy path share one behaviour on a non-local layout (2026-08-16 ruling):
   warn that a local copy is needed, offer the download, and on OK run the existing flow
   (`:14219`), switch to the downloaded layout, and continue the original action. The plain
   `errorEditingOnlySupportedForLocalFiles` dialog at `:12465` is replaced by that warn-and-offer.
   **The bundle key stays** — verified 2026-08-01, it has a second caller at `:14106` (the legacy
   external editor path), which is unaffected. No bundle deletions in any of the 8 files.
6. `gui/AutoLocomotiveStatus.java` — untouched (kept as-is, per the component-fates table).
7. `gui/HomeLocomotiveMenu.java` — home assignment **moves** into the Points tool; its logic is
   reused from there rather than duplicated.
8. `gui/LayoutRightclickAutonomyMenu.java` — functionality retained; gains an "Autonomy setup..."
   item that opens the editor in Autonomy mode scrolled to the clicked tile.
9. `build.xml` — new test lines (below).
10. All 8 bundles — keys (below).

**Phase 2 only** (deletions): `GraphViewer`, `GraphEdgeEdit`, `GraphRightClickPointMenu`,
`GraphRightClickGeneralMenu`, `GraphLocAssign`, `GraphLocExclude`, `graph.css`, `autonomyJSON` and
its scroll pane, the `autonomy.json` read/write paths, `GRAPH_*` preferences, and the two
GraphStream jars in `nbproject/project.properties` (:36-37, :47-48).

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
| SIGNAL | `signal` | E W | `{EW}` — topologically a straight, but **crossing one commands it GREEN** (author, 2026-08-16), carried in the port map so the reducer gathers signal and switch commands uniformly. Setting other signals RED for safety stays with conditional routes. An UNCOUPLER keeps its address but commands nothing — firing it is something the user asks for | M / author |
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

   **A switch or signal with no address blocks the build** (author, 2026-08-16): routing over one
   means trusting it to already be lying the right way - the danger `CUSTOM_PERM_*` exists to declare,
   except undeclared. **It fires only when autonomy is built.** A diagram may carry address-less tiles
   and keep working as a diagram exactly as it does today; nothing on the display or control path
   builds a tile graph, and the application already skips such tiles when wiring accessories. A page
   that deliberately contains them can be excluded from autonomy. The check scans every tile rather
   than being raised while walking, since an unaddressed switch on a siding no route reaches would
   otherwise never be reported - a blocking error that depends on being stumbled across is not one.

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

## ~~Release 2 — Lock-edge derivation + exposure linter~~ — DELETED (author, 2026-08-01)

Both classes are dropped. `LockEdgeAnalyzer`'s exposure linter existed to audit **hand-authored**
locks in a legacy `autonomy.json`; with legacy gone and locks derived from shared tiles there are
no coverage gaps to find - a derived configuration is correct by construction. `LockEdgeReviewUI`
proposed additions for a user to approve, equally meaningless when nothing is hand-authored.

What survives is a **lock explainer**, not a linter: selecting two edges (or a locked pair during
monitoring) shows *why* they are mutually exclusive - the shared tile(s), highlighted on the
diagram. A small feature inside the diagram view, not a release.

---

## Deferred: a shippable migration tool (agreed 2026-08-01, later release)

Not in scope for either phase, but agreed as a plausible follow-up — recorded so the insight is not
rediscovered from scratch.

**Station names are the join key, and mapping them is the whole problem.** A legacy `autonomy.json`
keys everything it holds on point *names*: lengths, locks, homes, exclusions, placements,
per-point properties and the timetable all reference points by name. So once each legacy name is
matched to a diagram tile, **every other field transfers mechanically** — there is nothing else to
resolve. Connections and locks are re-derived from geometry regardless, so they never need
migrating at all.

That reduces a migration tool to one screen: a two-column mapping of legacy point names to s88
tiles, with obvious candidates pre-matched (identical names, or a name already assigned to a tile)
and the rest picked by clicking the tile on the diagram. Everything downstream is a mechanical
copy into the companion file.

This is also why the developer-run seeder described above is cheap: it is the same mapping step
performed once, by hand, for a single known layout.

## Explicit deferrals

- `LAYOUT_STATION_PREFIX` hardcoded "Point:" vs localized key — pre-existing TODO, untouched.
  (Anchors supersede the label as the source of truth, but the label itself and its rendering path
  are left exactly as they are; setup mode never edits the diagram file.)
- Duplicate page names alias keys (parity with `layoutCache`), main-tab label accumulation
  (parity with device sets) — accepted.
- Creating/editing the *diagram itself* stays in `LayoutEditor`; setup mode never mutates pages.

## i18n keys (all 8 bundles, one commit)

Pruned 2026-08-01: anchor / paint / trace-review / adopt / orphan and all `lockassist.*` keys are
gone with the features that needed them.

**Editor** - `layout.ui.menuAutonomySetup`, `.modeDiagram`, `.modeAutonomy`,
`.infoDiagramEditingNeedsLocalCopy`, `.btnDownloadCSLayout`; `autosetup.ui.title`,
`.toolConnections`, `.toolPoints`, `.toolPortals`, `.toolLengths`, `.btnUndo`,
`.confirmDiscardChanges`, `.errorAutonomyRunning`, `.tooltipGestures`.

**Connections** - `.dirBoth`, `.dirForward`, `.dirBack`, `.dirNone`, `.labelSwitchBranches`,
`.warnDirectionContradiction`.

**Autonomy tab** - `.btnInitializeAutonomy`, `.warnAutonomyNeedsLocalLayout`,
`.confirmDownloadCSLayout`, `.btnImportGraphFile`, `.btnExportGraphFile`,
`.infoNoConfigurations`.

**Points** - `.promptPointName`, `.errorDuplicatePointName`, `.errorEmptyPointName`,
`.warnQuotesStrippedFromName`, `.labelGeneratedName`, `.labelPointNotStation`,
`.menuDesignateStation`, `.infoIsolatedFeedbackSkipped`.

**Portals** - `.promptLinkName`, `.errorDuplicateLinkName`, `.errorLinkNotMutuallyPaired`,
`.errorLinkTargetShared`, `.errorPortalTargetsExcludedPage`, `.infoUnnamedLinkNotConnected`.

**Lengths** - `.promptTileLength`, `.errorNegativeLength`, `.btnShowLengths`, `.colLength`.

**Pages** - `.menuExcludePageFromAutonomy`, `.labelPageExcluded`.

**Build messages** - `.errorTileHasNoAddress`, `.errorScissorsNotSupported`, `.warnPermanentTurnout`,
`.warnPermanentTurnoutCount`, `.warnTurntableNotRoutable`, `.colWarnings`,
`autolayout.infoCompanionLoaded`, `.infoCompanionOrphans`, `.errorCompanionVersion`.

**Viewer** - `.labelConfiguration`, `.btnLoadConfiguration`, `.menuNewConfiguration`,
`.menuRenameConfiguration`, `.menuDeleteConfiguration`, `.errorLastConfiguration`,
`.promptConfigurationName`, `.layerLabels`, `.layerLocomotives`, `.layerHomes`, `.layerDirections`,
`.layerLengths`, `.layerExclusions`, `.layerMonitoring`, `.btnTestConnectivity`,
`.btnCheckConfiguration`, `.labelLocomotiveRoster`, `.explainLockedPair`.

Preference: `DIAGRAM_OVERLAYS_PREF = "DiagramOverlays"` (default true), plus one persisted key per
layer toggle - user preference, never configuration data.

## Tests (one class = one JVM = one `build.xml` line)

| Class | Proves, headlessly |
|---|---|
| `testTilePorts` | port-map consistency: reciprocity, rotation coherence; **every switch's states are confirmed pairs, not supersets** — `SWITCH_LEFT` yields `{NS}` unswitched and `{SW}` switched with N unreachable when thrown, `SWITCH_Y` yields `{SW}` unswitched and `{SE}` switched with no straight route in either, `SWITCH_CROSSING` swaps throughs for diagonals; every confirmed connection pair contains S for turnouts (the toe invariant); `CUSTOM_PERM_*` permits only pairs entering S; base art maps to the unswitched state; **every one of the 28 `componentType` values has an entry** (loop the enum — a new type must fail the test, not silently trace as impassable); orientation domain matches `getNumOrientations()`; STRAIGHT `{EW}` at o=1 yields `{NS}` and CURVE `{ES}` at o=1 yields `{NE}`, pinning the `(4-o)` quarter-turn convention; CROSSING/OVERPASS expose two disjoint groups, never one |
| `testTileGraphWalk` (was `testDiagramTopology`; the class was folded into `TileGraph`/`GraphReducer`, its coverage stays) | programmatic pages (no files): straight runs connect; rotation breaks adjacency as the art says; a switch fork yields per-branch walks **with correct settings**, and **switch branches default base -> forks** - the trailing direction appears only when enabled, a branch set to `none` yields no edge in either direction; CROSSING two independent throughs; OVERPASS no interaction; unlinked TUNNEL stops, linked portal continues cross-page; s88 tiles terminate walks; cycles bounded; **permanent turnouts**: facing entry yields no exits regardless of user settings, trailing connects, the resulting edge is one-directional, no config command is emitted, a warning naming coordinates is collected; **per-tile direction**: an all-`both` run of plain tiles traces both ways, one `forward` tile mid-run suppresses exactly the opposing edge, `none` stops the walk at that tile, constraints AND along a path rather than the last one winning, a two-group tile constrains only the group traversed, and a run made impossible in both directions is reported as a contradiction warning with coordinates |
| `testDiagramMonitor` | dispatch→RESERVED, milestone→COMPLETED/CURRENT, completion fire (maps already cleared)→empty publish, 50-fire burst coalesces, throwing publisher never reaches the firing thread — simulated model + fake publisher + visible/hidden parents (the `testLayoutTiles` trick) |
| `testAutonomyCompanionStore` | shared + per-configuration round-trip; `tileLengths` and `tileDirections` sparsity (zeros and `both` are never written, a cleared value round-trips as absent, and both survive configuration switches because they are shared); a two-group tile's per-group direction keys round-trip independently; configuration CRUD (copy-on-create, rename, refuse deleting the last); version gate; unknown-field preservation; orphan policy; `renamePoint` rewrites shared anchors and every configuration; save-back targets only the active configuration; **local-only**: the store refuses to initialize when no local layout folder exists (the UI layer owns the warn-then-download flow); **page rename** rewrites every tile key in the global file and every configuration file in one pass, and a rename plus rebuild loses nothing; a tile key whose tile no longer exists after an external diagram edit is dropped with a log line (author ruling: deleted tile = start over), while point-name references (placements, homes, timetable) orphan-and-report as before |
| `testAutonomyBuilder` | companion + programmatic pages compile to JSON that `parseAuto` accepts and validates; deterministic output (two builds byte-identical); setup errors (unpaired portal, disqualified tile) invalidate through the normal flow; **startup**: the active configuration loads when one is defined, nothing loads when none is, and no code path reads or writes `autonomy.json`; generated-then-parsed Layout's `toJSON` is stable across a second build; **scratch build isolation**: `validateScratch()` on a deliberately broken companion reports invalid while `model.getAutoLayout()` remains the untouched previous instance (identity assert) and no locomotive was stopped; **lengths**: unassigned tiles yield length 0 on every edge (parity with today), assigned tiles sum along the traced path with endpoints excluded, a 0 on an intermediate tile contributes nothing without breaking the edge, and a legacy length distributed over N tiles sums back to exactly the original |
| `testTileGraph` (revised) | **links**: an unnamed link forms no portal connection and no train can cross it; a named, mutually-paired link joins its partner's tile across pages; a half-pairing and an A-B-C collision are both setup errors naming the tiles; the geometric side of a LINK is W at orientation 0 and rotates with the tile; a pairing targeting an excluded page is an error, not a silent dead end. **Excluded pages**: an excluded page contributes no nodes, Points, edges or warnings, and re-including it restores the orphaned placements rather than having deleted them. geometry seeds candidate connections both ways; a user override survives a re-trace and is not overwritten by geometry; disallowed connections are absent in both directions; one-way connections are absent in exactly one; LINK and TUNNEL portal connections join the named tiles across pages and are never inferred from adjacency; a permanent turnout's facing connections are seeded disallowed and cannot be user-enabled |
| `testGraphReducer` (revised) | **every** feedback tile becomes a Point without user action, while a station is a Point plus a designation; two feedback tiles sharing one s88 address both become Points with distinct generated names, and a user rename survives a rebuild and a change to the surrounding track; a duplicate name is refused at authoring time, not at build time; a name containing a quote character does not silently change on the way into `Point`; designating a station sets `isDestination` and never produces the model's no-s88 exception because every derived Point has one; a legacy virtual point (no s88) is reported as an unanchorable migration case rather than dropped; a feedback tile with no allowed connections is skipped (and counted) rather than emitted as an unreachable node, while one with a single connection is emitted; a `CUSTOM_SCISSORS` on a participating page fails the build with a coordinate-naming error; a degree-2 chain between two significant nodes collapses to exactly one edge with summed length and ANDed direction; a switch in the chain forks into one edge per branch carrying the correct `configCommands`; a `SWITCH_THREE` yields exactly three routes from its toe and **every** route commands both of its addresses with an explicit straight/throw (no address is ever left unspecified); a `SWITCH_CROSSING` contributes commands in **both** states — unswitched for its N-S/E-W throughs and switched for its N-W/S-E diagonals; an unpromoted s88 tile collapses away; **mutual exclusion**: two edges sharing a switch tile are locked against each other, two edges sharing a CROSSING are locked, two edges crossing an OVERPASS in different groups are **not** locked but in the same group are, a portal pair counts as one location; reduction is deterministic across two runs |
| `testLockEdgeAnalyzer` (R2) | exposure invariant: asymmetric-but-covered pair → zero suggestions; hand-built unsafe config → exactly the uncovered ordered pairs; portal-pair conflicts; pinned run against `test/autonomy_sanity.json` |

## Verification

**R0 (engine, no UI)** - the milestone's whole point:
1. NetBeans compile; `testTilePorts`, `testTileGraph`, `testGraphReducer`,
   `testAutonomyCompanionStore`, `testAutonomyBuilder`.
2. **Ground-truth diff** against the author's real `autonomy.json`: points, edges, config commands,
   lock references, lengths. "Only in legacy" is a defect; config-command disagreements are
   port-map defects; legacy lock refs with no derived counterpart are the evidence on whether
   virtual points were load-bearing. Keep the report.
3. Harness emits a starter companion file for the author to review.

**R1 (UI)**:
4. `testDiagramMonitor`, plus `testLayoutTiles`, `testHomeStaging`, `testAutoLayout` for regression.
5. **CS-layout acceptance**: with no local layout path set, Initialize autonomy (and the Edit
   button) warn that a local copy is needed and offer the download; accepting runs the download,
   switches to the local layout, and continues into autonomy setup; declining changes nothing.
   After setup, points, lengths and portals persist across a restart, and autonomy files appear
   only inside the layout folder.
6. **Connectivity acceptance**: open Autonomy mode on the author's real pages and confirm the drawn
   connections match the track - this is the editing surface now, not a debug overlay.
7. Set up one page end to end, load it as a configuration, and confirm in simulate mode that
   segments light correctly **with no graph window in existence**, the dot follows the train, and
   popups / page switches survive via `republish()`.
8. Configuration switching: duplicate a configuration, move locomotives, switch between them, and
   confirm structural data is shared while placements are not.

**R2 (removal)**:
9. Author's layout re-created on the diagram and running **before** removal starts.
10. After each deletion, compile *and run* - not grep. `autonomyJSON` is the proof that a field's
    name does not reveal what it does.

## Changelog drafts (non-technical)

- R1: "Autonomy now lives on the track diagram. TrainControl reads the track itself - switches,
  crossings, curves and all - so your stations and the connections between them come from the
  diagram you already drew. Name your stations, check which way trains may travel, and you are
  done. Running trains light up the actual track, with each train's position marked, and you can
  save several setups for different starting arrangements."
- R2: "The separate autonomy graph window has been retired. Everything it did now happens on the
  track diagram."
