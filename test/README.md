# How the tests are organised

Four folders, by what a test is FOR rather than by what it happens to import.

| Folder | What lives there | How many |
|---|---|---|
| `core/` | The model, the protocol, the files, the graph. What the program **is**. | 63 |
| `ui/` | Drives a window or a Swing component. Needs a display, or tests view logic. | 16 |
| `regression/` | Written for one named defect, to stop it coming back. | 45 |
| `support/` | Not tests. Fixtures the others use. | 3 |

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

**The real gate is `docs/tools/battery.sh`, not `ant test`.** `ant test` runs everything except
`testAutoDetect` (which probes the network for a real Central Station), one class per JVM, and it is
useful for driving a single class while iterating. But it is weaker than `battery.sh` in two ways that
matter: a class that skips every test in it (via a `SkipException`, or a `@BeforeClass` failure) reads as
green to `ant test`, where `battery.sh` classifies "0 passed, N skipped" separately and fails on it; and
`battery.sh` fingerprints `cs2_sample_layout` before and after the whole run and shouts if anything wrote
to it, which `ant test` has no way to do. Both set the
`-Dtraincontrol.anyReceivePort=true` system property that lets tests share a machine without binding the
same UDP port - `ant test` through `build.xml`'s `test-sys-prop.traincontrol.anyReceivePort`, which
the TestNG macro turns into a `-D` on the fork. **This paragraph said `ant test` does not, and that
stopped being true on 2026-09-03** (`TSX-C20`, and `VD10-B7` for the fact that the correction was
dispositioned without being made). What `ant test` still cannot be given is `-Xmx` and the
skipped-class rule; `build.xml` says why at the property. Use `ant test` (or the scratchpad runners below) for a fast loop on the classes you are
changing, but treat `docs/tools/battery.sh` as the one that actually has to pass.

While iterating, the scratchpad holds two scripts that run classes in their own JVMs and print one line
each: a full runner and a fast one that excludes the handful of slow classes. The fast one is an
**exclusion** list on purpose - a new test is picked up automatically, so the only maintenance is moving
a class out when it turns out to be slow. Time a new class before assuming it is fast; anything over
about ten seconds belongs on that list.

**A one-off probe is not covered by any of this.** Both runners fingerprint `cs2_sample_layout`
before and after and refuse to stay quiet if it moved; a `java` command typed straight at a class,
which is how most probes get run, is guarded by nothing at all.

So: **a probe opens `support.LayoutSandbox` before it builds a model or a window**, exactly as a test
does, or it is run through `one.sh` where the fingerprint can see it. There is no third option that
is safe, because the layout path is a machine-global preference and every probe that skips this step
reads and writes the operator's live railway by default. What it costs when it goes wrong is on
record: facings, placements, priorities and an exclusion list, in `testEveryLanguageFits`'s own
header.

**And before hunting for the class that wrote to that folder, look for a running TrainControl.** On
2026-09-03 a `config/autonomy/setup-before-edit.json` appeared there and read as exactly this failure;
it was the *application*, launched from NetBeans at 02:19 and still open six hours later, leaving its
own unfinished-edit snapshot. `battery.sh`'s warning says to check this first, and it is right:
a running railway rewrites that folder as trains move, and no fingerprint can tell it from a test.
A leftover application also holds the UDP port, which is what a `BindException` in a tool that does
not pass `-Dtraincontrol.anyReceivePort=true` - the 2.8.1 jar in `docs/tools/parity/`, for one -
actually means.

---

## One thing that bites when a test moves

`getClass().getResource("LocDB2_5_16.data")` is **package-relative**. The moment a test moved into
`core/`, that call started looking for `/core/LocDB2_5_16.data` and returning null - which surfaces as a
configuration failure in `@BeforeClass`, not as a test failure, so it is easy to skim past.

The fixture files stay at the root of `test/`, because `ui/` and `regression/` want them too. Every
lookup is therefore **absolute**: `getResource("/LocDB2_5_16.data")`. Keep new ones that way.
