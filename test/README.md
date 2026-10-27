# How the tests are organized

Four folders, by what a test is FOR rather than by what it happens to import.

| Folder | What lives there | How many |
|---|---|---|
| `core/` | The model, the protocol, the files, the graph. What the program **is**. | 149 |
| `ui/` | Drives a window or a Swing component. Needs a display, or tests view logic. | 54 |
| `regression/` | Written for one named defect, to stop it coming back. | 140 |
| `support/` | Not tests. Fixtures the others use. | 14 |
| `examples/` | Not tests. Sample programs for the Java API, which `core/testTheApiExamplesRun` runs and AutomationAPI.md points to. | 7 files |
| `layouts/` | Not tests either. Checked-in **railways**, one folder per shape (see below). | 5 |

If you add a class, move its number here.

---

## Which folder does a new test go in?

Ask what would make it fail.

**`core/`**: a change to how the railway works (path finding, route parsing, deriving a graph from a diagram).  Most
tests are these.

**`ui/`**: a change to what is on screen.  If it builds a `JFrame`, opens a dialog, or asks whether a cell is grayed,
it goes here.  These skip themselves when there is no display, so a green run that skipped them is not the same as a
green run.

**`regression/`**: a specific defect, named in the class javadoc with the commit or finding that produced it
(`testLocomotiveAddressRules` exists because address 0 got past a dialog).  If you cannot name the defect, it probably
belongs in `core/`.

**The import is not the rule.**  `testLayoutEditorBulkEdits` imports `LayoutEditor` and lives in `regression/`,
because it tests the setup-follows-the-track rule.  A test is filed by what it is about.

---

## Three things that are easy to get wrong

**They are in packages.**  Every file starts `package core;`, `package ui;` or `package regression;`.  A class in a
named package **cannot see a class in the default package**, so fixtures live in `support/` and are imported.

**`build.xml` needs a line per class**, or the class does not run.  It runs one class per JVM, because `NetworkProxy`
binds a fixed UDP port and only one control station can exist per JVM.  The macro matches on `**/<class>.java`, so the
folders do not change those entries.

**The power state is set by an ECHO, not by `go()`.**  `model.go()` and `model.stop()` send a command and return; the
flag is written when the reply comes back, on another thread (`receiveMessage` hands it to `locMessageProcessor`).  So
`model.go(); assertTrue(model.getPowerState())` is a race that passes alone and fails in a battery.  Use
`model.waitForPowerState(true, ms)` in both directions, including before SAMPLING the power after a route that carries
a stop.

---

## Running them

**The gate is `docs/tools/battery.sh`, not `ant test`.**  `ant test` runs everything except `testAutoDetect` (which
probes the network for a real Central Station), one class per JVM; use it (or the scratchpad runners below) for a fast
loop on the classes you are changing.  It is weaker in two ways: a class that skips every test (a `SkipException`, or
a `@BeforeClass` failure) reads as green, where `battery.sh` classifies "0 passed, N skipped" separately and fails on
it; and `battery.sh` fingerprints `cs2_sample_layout` before and after the run and shouts if anything wrote to it.
Both set `-Dtraincontrol.anyReceivePort=true`, which lets tests share a machine without binding the same UDP port
(`ant test` through `build.xml`'s `test-sys-prop.traincontrol.anyReceivePort`, which the TestNG macro turns into a
`-D` on the fork).  What `ant test` cannot be given is `-Xmx` and the skipped-class rule; `build.xml` says why at the
property.

While iterating, the scratchpad holds two runners that run classes in their own JVMs and print one line each: a full
one and a fast one that excludes the slow classes.  The fast one is an **exclusion** list on purpose, so a new test is
picked up automatically.  Time a new class before assuming it is fast; anything over about ten seconds belongs on
that list.

**A one-off probe opens `support.LayoutSandbox` before it builds a model or a window**, exactly as a test does, or it
is run through `one.sh` where the fingerprint can see it.  There is no third safe option: the layout path is a
machine-global preference, and a probe that skips this reads and writes the operator's live railway.

**Before hunting for the class that wrote to `cs2_sample_layout`, look for a running TrainControl.**  A running
railway rewrites that folder as trains move (`config/autonomy/setup-before-edit.json` is its unfinished-edit
snapshot), and no fingerprint can tell it from a test.  A leftover application also holds the UDP port: that is what
a `BindException` in a tool that does not pass `-Dtraincontrol.anyReceivePort=true` means.

---

## One thing that bites when a test moves

`getClass().getResource("LocDB2_5_16.data")` is **package-relative**: from `core/` it looks for
`/core/LocDB2_5_16.data`, returns null, and surfaces as a configuration failure in `@BeforeClass`, easy to skim past.
The fixture files stay at the root of `test/` for every folder, so every lookup is **absolute**:
`getResource("/LocDB2_5_16.data")`.  Keep new ones that way.

---

## What the fixtures cannot see

A survey of every fixture in the suite (MON-C17 to C21) asked what the tests are shaped like rather than what they
assert.  A blind spot nobody has written down is found the expensive way, by a defect walking through it.

### What now exists: `test/layouts/`

Adam's design: *"hand-authored for topology, lengths in code."*  One folder per SHAPE, each with a `README.md` stating
what a test may rely on and what it may not, opened in one call by `support.Scenario`:

    scenario = support.Scenario.open("single-switch");   // copies to a sandbox, builds model + session
    Layout built = scenario.build();                     // and again after any change
    scenario.close();

| Scenario | What shape it is | What it made possible |
|---|---|---|
| `single-switch/` | A turnout with two routes through it, and a square trains arrive at from both sides | Lock edges and shared metal, and a **split square** |
| `curve-into-platform/` | A station approached round a curve, with a straight station beside it as the control | The side a rail LEAVES by differs from the compass direction of the neighbor |
| `live-snapshot/` | Adam's whole railway, taken from `git show HEAD:` and frozen | Complex pathing and random movement, against ground truth that cannot move.  The base for mutations |
| `one-page/` | The sample layout cut down to its first page, with nothing set up | The setup moving onto a one-page layout (`testASetupMovesToAOnePageLayout`) |
| `autonomy-bundles/` | Not a railway: three setups Adam exported, imported over another fixture's pages | `testRouteInventory`'s bundle claims, which read his gitignored `tc_backup/` and skipped everywhere else |

**Lengths, train placements and locomotive properties are never in a scenario.**  A test sets them itself, so no two
tests inherit one author's numbers.  Every scenario README says so under its own heading.

**`testEveryScenarioIsUsedAndSaysSo` keeps the library from rotting:** it requires that heading, fails a folder under
`test/layouts` that no test names, and compares each README's "Used by" list against the classes that reference the
scenario (that list changes when a *different* file changes).

### The survey, and what is left of it

A "real layout" test builds the wired railway: `support.LayoutSandbox.wired` runs the application's own wiring loop,
and the railway reduces to 128 edges and builds to 149.  A second `CS2File` that never wires the accessories gives a
five-edge skeleton; a guard in `testEveryTestIsInTheBattery` stops that recipe coming back.

C20 and C17 are answered by `test/layouts/`: two hand-drawn railways between them use orientations 1, 2 and 3 on
straights, curves, feedback curves and a switch, so the `(4 - orientation)` convention is exercised by a railway a
test reasons about.  C18, C19 and C21 stay open only because nobody has written their scenarios; `single-switch` is
already the metal C21's fixture would contend over.

| | the gap | what would close it |
|---|---|---|
| **C18** | The reducer never crosses a page.  `testAutonomyDiagramReducer` passes one page in all ~28 fixtures and mentions no portal; the only place the REDUCER contracts across one is a report-not-assert class | one two-page fixture with a paired link, asserting the contracted edge's endpoints, length and lock set |
| **C21** | Nothing hand-built runs more than two locomotives, and the concurrency cap's own fixture is two DISJOINT routes: it tests the counter, not the contention | a fixture where two trains want the same metal |
| **C19** | Every test locomotive is non-reversible unless a test says so: the short constructors never assign `reversible` and no checked-in JSON carries the key.  The reversible-only rules (the may-reverse prompt, turning-copy selection, `flipFacing`) are the thin side | awareness, plus reversible variants where reversal is the subject |

**`blockedPoints` is empty in all four `setup.json` files**, so the `blockedBy` restriction is reached only through
hand-built fixtures, never through a loaded layout.  One entry in a checked-in setup would close that; it is
deliberately not done, because changing a shared fixture while Adam is testing against it makes a green suite lie
about a railway he is looking at.
