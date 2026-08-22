# How the tests are organised

Four folders, by what a test is FOR rather than by what it happens to import.

| Folder | What lives there | How many |
|---|---|---|
| `core/` | The model, the protocol, the files, the graph. What the program **is**. | 58 |
| `ui/` | Drives a window or a Swing component. Needs a display, or tests view logic. | 7 |
| `regression/` | Written for one named defect, to stop it coming back. | 12 |
| `support/` | Not tests. Fixtures the others use. | 2 |

Set 2026-08-22, at Adam's request, when 76 classes in one flat folder had stopped saying anything about
themselves.

---

## Which folder does a new test go in?

Ask what would make it fail.

**`core/`** — a change to how the railway works. These are the tests that would fail if somebody broke
path finding, or route parsing, or the way a graph is derived from a diagram. Most tests are these.

**`ui/`** — a change to what is on screen. If it builds a `JFrame`, opens a dialog, or asks whether a
cell is greyed, it goes here. These skip themselves when there is no display, which is why they are
worth keeping apart: a green run that skipped them is not the same as a green run.

**`regression/`** — a specific defect, named in the class javadoc, with the commit or finding that
produced it. `testStationLabelsFollowMoves` exists because a sensor nudged one square down lost its
name; `testLocomotiveAddressRules` exists because address 0 got past a dialog. If you cannot name the
defect, it probably belongs in `core/`.

**The import is not the rule.** `testLayoutEditorBulkEdits` imports `LayoutEditor` and lives in
`regression/`, because what it tests is the setup-follows-the-track rule and the editor is only where
that rule is called from. A test is filed by what it is about.

---

## Two things that are easy to get wrong

**They are in packages now.** Every file starts `package core;`, `package ui;` or `package regression;`.
A class in a named package **cannot see a class in the default package**, which is why the two fixtures
moved to `support/` and are imported.

**`build.xml` needs a line per class.** It runs one class per JVM, because `NetworkProxy` binds a fixed
UDP port and only one control station can exist per JVM. The macro matches on `**/<class>.java`, so the
folders do not change those entries - but a new test still has to be added there or it will not run.
That is not a formality: 35 of 76 classes were missing from that list until 2026-08-22, including the
matrix test written specifically to catch this project's commonest bug class.

---

## Running them

`ant test` runs everything except `testAutoDetect`, which probes the network for a real Central Station.

While iterating, the scratchpad holds two scripts that run classes in their own JVMs and print one line
each: a full runner and a fast one that excludes the handful of slow classes. The fast one is an
**exclusion** list on purpose - a new test is picked up automatically, so the only maintenance is moving
a class out when it turns out to be slow. Time a new class before assuming it is fast; anything over
about ten seconds belongs on that list.

---

## One thing that bites when a test moves

`getClass().getResource("LocDB2_5_16.data")` is **package-relative**. The moment a test moved into
`core/`, that call started looking for `/core/LocDB2_5_16.data` and returning null - which surfaces as a
configuration failure in `@BeforeClass`, not as a test failure, so it is easy to skim past.

The fixture files stay at the root of `test/`, because `ui/` and `regression/` want them too. Every
lookup is therefore **absolute**: `getResource("/LocDB2_5_16.data")`. Keep new ones that way.
