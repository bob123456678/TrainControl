# An independent third pass, in the parts the first two did not open

**Prefix for citing these findings elsewhere:** `X8`

**Status:** closed 2026-09-10.  All fifteen A/B/C findings fixed and tested; `X8-B6` was answered as a documentation defect and is the one Adam may want to overrule.

Reviewed 2026-09-10 on branch `autonomy-diagram-r0` at **`49c687d5`** ("The validation of that round: a
square out of service is not a manual-only one, and a claim about a divergence nothing pins"), which is
one commit past the tree `E8V-validation.md` was written against and two past `E8-eight-day-review.md`.

Scope: the eight-day window, `git log --since="8 days ago"`, deliberately entered where the two
earlier passes said they had not been. `E8-A*`/`E8-B*`/`E8-C*` and `E8V-*` are not re-litigated and
nothing below overlaps them: the route-wide length rule, `AutonomySession.stationsAutonomyWillNotChoose`,
the blocked-track mark, Control+E and the hovered square, and `behaviour.md` §1/§3/§5/§6/§7 were left
alone on purpose. What follows is the message bundles, the CS2 file format, route definition and
execution, multi-units, the timetable, and the layout editor's own editing gestures.

Prefix checked against `docs/reviews/`, `docs/reviews-2026-09-09/`, `docs/reviews-2026-09-10/` and
`docs/manual-tests/findings.tsv` before it was chosen. `X8` collides with nothing; the live prefixes in
this folder are `E8`, `E8V`, `D2`, `D3`, `IND9X`, `REV9`, `W7`, `W7B` and `AUT9`.

---

## Method

**What was executed.** Three test runs through `docs/tools/one.sh`, and two scripted scans of the
message bundles.

| Run | What | Result |
|---|---|---|
| `core.testTwoCentralStationMultiUnitsShareAMember` (written for this pass) | two Central Station multi-units holding one member locomotive | `Total tests run: 2, Failures: 2, Skips: 0` — both claims red, every precondition and the control green. Evidence for `X8-A2`. |
| `regression.testX8CutWithNothingHovered` (written for this pass) | Control+X with nothing under the pointer, then a paste | `Failures: 1` twice, once per half. Evidence for `X8-B1`. |
| `regression.testX8CutWithNothingHovered`, second shape | the same tool run through `executeTool` | `pasting the tool armed by Control+X over the palette threw java.lang.NullPointerException` |

Two scripted scans, run under `python3.9` against `src/org/traincontrol/resources/`:

- placeholder parity between `messages.properties` and each of the seven translations, over all 1,932
  keys — `TOTAL 0`;
- unbalanced braces, and translations carrying a `{n}` their English source does not — both empty.

**Both new test files were deleted after the runs.** `git status --porcelain -- test src docs
build.xml` is empty, so the working tree carries nothing from this pass. Their full source is in the
finding that used them, so either can be re-created and put in `build.xml` when the defect is fixed.
Nothing was mutated in place; `cs2_sample_layout/` was read and never written, and `one.sh`'s
before-and-after fingerprint of it did not fire on any of the three runs.

**What was read and not executed.** `CS2File.parseFileContents` and the page/index writers in
`LayoutDiagram`; `MarklinRoute.execRoute` and `editRoute`; `RouteCommand.toLine`/`fromLine`;
`Layout.executeTimetableInternal`; `LayoutEditor`'s gesture handlers; `MarklinLocomotive`'s multi-unit
predicates. The CS2 findings below are supported by the real Central Station exports in this
repository - `Oles kreds/config/*`, `sample_layout/`, `test/layout/` - read with `head` and `grep`
rather than by parsing them through the application.

**Three reviewers looked at this window today and none of us has run the battery.** `E8` ran 23
classes, `E8V` ran 11, this pass ran 3 and wrote 2 of them. Nothing here should be read as a statement
about the other 200.

---

## A — high

| | | |
|---|---|---|
| `X8-A1` | `page=N` is deleted from every diagram page the application saves | closed |
| `X8-A2` | two Central Station multi-units sharing a locomotive are reported as safe to run together | closed |

---

### X8-A1 — the `page=` line of every track-diagram page is silently deleted on save, and four of Adam's five pages have already lost it

**Status:** closed 2026-09-10.  `parseFileContents` has a fourth arm for a left-margin `key=value`, collected into an item typed `_bareKeys`; `LayoutDiagram.addBareKey` keeps them and `exportToCS2TextFormat` writes them back immediately after `[gleisbildseite]`.  Four claims in `core.testParseCS2Layout`, mutation-verified.  Adam's own pages are not repaired retroactively - the line comes back the next time each page is saved.

**What is wrong.** `CS2File.parseFileContents` recognises three shapes of line and `page=1` — the
second line of a genuine Central Station page file — is none of them, so it is dropped on read; the
writer regenerates the whole file and has no way to put it back.

`src/org/traincontrol/marklin/file/CS2File.java:527-552`

```java
if (s.matches("^[a-z]+$"))                    // a block name: no '='
else if (s.matches("^ \\.[a-z0-9A-Z]+=.+$"))  // " .key=value": needs the leading space and dot
else if (s.matches("^ \\.[a-z]+$"))           // " .key": array header
```

`page=1` is at the left margin **and** carries an `=`, so it matches neither the first nor the second,
and has no leading `" ."` for the third. It never becomes an item, so it never reaches
`LayoutDiagram.addUnmodelledBlock` either — that method returns immediately on a map with no `_type`
(`src/org/traincontrol/base/LayoutDiagram.java:362-364`), and its only caller is
`CS2File.java:2447`, which passes items the parser built.

`LayoutDiagram.exportToCS2TextFormat` (`src/org/traincontrol/base/LayoutDiagram.java:280-341`) then
writes `[gleisbildseite]`, the preserved unmodelled blocks, the elements, and the unmodelled elements.
`grep -rn "page=" src/` returns nothing. The line cannot come back.

**How I know.** Read the parser and the writer, then looked at the real files:

```
$ for f in "Oles kreds/config/gleisbilder/"*.cs2 sample_layout/config/gleisbilder/*.cs2 \
           cs2_sample_layout/config/gleisbilder/*.cs2 test/layout/config/gleisbilder/*.cs2; do
      printf '%-58s' "$f"; head -3 "$f" | tr -d '\r' | tr '\n' '|'; echo; done

Oles kreds/.../0 stationer.cs2          [gleisbildseite]|version| .major=1|
Oles kreds/.../1 gods.cs2               [gleisbildseite]|page=1|version|
Oles kreds/.../2 opstilling.cs2         [gleisbildseite]|page=2|version|
   ... 3..7 likewise ...
sample_layout/.../Page 1.cs2            [gleisbildseite]|page=1|version|
sample_layout/.../Page 2.cs2            [gleisbildseite]|page=1|version|
sample_layout/.../Page 3.cs2            [gleisbildseite]|page=2|version|
cs2_sample_layout/.../1 - Main.cs2      [gleisbildseite]|version| .major=1|
cs2_sample_layout/.../2 - Bottom.cs2    [gleisbildseite]|version| .major=1|
cs2_sample_layout/.../3 - Top Parking   [gleisbildseite]|version| .major=1|
cs2_sample_layout/.../4 - Combined.cs2  [gleisbildseite]|version| .major=1|
cs2_sample_layout/.../5 - Test.cs2      [gleisbildseite]|page=1|version|
test/layout/config/gleisbilder/test.cs2 [gleisbildseite]|page=1|version|
```

Every untouched export has the line. In the live railway folder, the four pages the application works
on have lost it and `5 - Test.cs2` — the page nothing edits — still has it. `0 stationer.cs2` in
`Oles kreds` is the one page in that folder without it, and it is also the one whose name appears in
that layout's `zuletztBenutzt` block. That is the pattern of a file that has been rewritten, not of a
Central Station that writes the line inconsistently.

The save does not need a Save button: `AutonomySession` writes a page out when a station on it is
named, so this happens on the ordinary course of setting autonomy up.

**Why this is A rather than C.** `LayoutDiagram`'s own comment, forty lines below the writer, states
the rule this breaks: *"Saving regenerates the whole page from this model, and an element whose type
TrainControl does not recognise never entered the model - so writing the file deleted it… Losing
scenery, or a component a later Central Station firmware added, because this program had not heard of
it is not a trade anybody agreed to."* The element-level machinery and the block-level machinery were
both built for exactly this; `page=` is neither an element nor a block, and fell between them.

I have **not** established what a Central Station does with a page whose `page=` is missing, and that
is the open question for Adam — if it is cosmetic there, this is a C. What is certain is that the
application deletes a line out of the user's file with nothing said, against a rule it wrote down.

**What I would change.** Give `parseFileContents` a fourth arm for a left-margin `key=value` line and
carry it into the page's preserved state, emitting it back immediately after `[gleisbildseite]`. Then
sweep: `X8-B2` is the same omission one file up.

---

### X8-A2 — two Central Station multi-units that drive the same locomotive are reported as safe to run as two trains

**Status:** closed 2026-09-10.  `MarklinLocomotive.commandedLocomotives` asks the question once and both loops of `isSimultaneousMultiUnitCompatible` use it.  Pinned by `core.testTwoCentralStationMultiUnitsShareAMember`, mutation-verified.

**What is wrong.** `MarklinLocomotive.isSimultaneousMultiUnitCompatible` chooses a multi-unit's
members by decoder type in its first loop and forgets to in its second, and the second loop is the one
that compares *my* members against *yours*.

`src/org/traincontrol/marklin/MarklinLocomotive.java:1082-1122`

```java
if (this.getDecoderType() == MarklinLocomotive.decoderType.MULTI_UNIT)
{
    otherLocs = this.getModelMultiUnitLocomotives();      // asked properly
}
else
{
    otherLocs = this.getLinkedLocomotives().keySet();
}

for (Locomotive other : otherLocs) { ... }

// Locomotives linked to the other locomotive have the same address as one of our linked locomotives
for (Locomotive other : this.getLinkedLocomotives().keySet())   // <-- not asked
{
    ...
    if (((MarklinLocomotive) l).getDecoderType() == MULTI_UNIT)   // the RIGHT side is asked
        other2Locs = l.getModelMultiUnitLocomotives();
    else
        other2Locs = l.getLinkedLocomotives().keySet();
    ...
}
```

The second loop is not merely likely to be empty when `this` is a Central Station multi-unit — it is
**provably** empty. `setLinkedLocomotives` (`MarklinLocomotive.java:1157-1167`) clears
`linkedLocomotives` and returns `-1` for any `MULTI_UNIT`, with the comment *"Multi-units defined in
the Central Station cannot be linked to other locomotives."* So the whole body of that loop is dead
code for exactly the case the branch above it was written for.

The caller compensates for asymmetry and cannot compensate for this. `Layout.sanitizeMultiUnits`
(`src/org/traincontrol/automation/Layout.java:6993-6995`) asks both directions:

```java
p.getCurrentLocomotive() != null && !p.getCurrentLocomotive().isSimultaneousMultiUnitCompatible(l)
|| p.getCurrentLocomotive() != null && !l.isSimultaneousMultiUnitCompatible(p.getCurrentLocomotive())
```

Both directions have the same gap when both sides are multi-units, so neither answers.

**How I know.** A test written for this pass, run at `49c687d5`:

```
$ TC_SCRATCH=... bash docs/tools/one.sh core.testTwoCentralStationMultiUnitsShareAMember
--- core.testTwoCentralStationMultiUnitsShareAMember
Total tests run: 2, Failures: 2, Skips: 0
```

and from `TEST-core.testTwoCentralStationMultiUnitsShareAMember.xml`:

```
message="two multi-units that drive the same locomotive must not be run as two trains
         expected [false] but found [true]"
message="and asked with the multi-unit on the left it must be found too
         expected [false] but found [true]"
```

Both failures are at the claim. Every precondition passed — both locomotives really are
`decoderType.MULTI_UNIT`, both really hold the member — and so did the control,
`assertFalse(muOne.isSimultaneousMultiUnitCompatible(member))`, which is the head-versus-its-own-member
case `core.testAdvancedRoutes.testConsistHeadAndMemberAreNotSimultaneouslyCompatible` already covers. A
predicate answering false to everything would have failed that control.

The second test is the discriminator and it is the more useful half. Asked with a TrainControl-side
linked head on the left, the shared member **is** found — that assertion passed. Asked with the
multi-unit on the left, it is not. That is the second loop's missing branch and nothing else.

The existing coverage never reaches it: `testAdvancedRoutes.consist()`
(`test/core/testAdvancedRoutes.java:148-157`) builds consists through `preSetLinkedLocomotives`, which
is the TrainControl-side link. No test in the battery constructs a `MULTI_UNIT` decoder type at all.

The test source, for re-creation:

```java
package core;

import java.util.HashMap;
import java.util.Map;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class testTwoCentralStationMultiUnitsShareAMember
{
    private static MarklinControlStation model;

    private static final String[] TEST_LOCS = { "X8 member", "X8 mu one", "X8 mu two", "X8 linked" };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        for (String name : TEST_LOCS)
        {
            if (model != null) model.deleteLoc(name);
        }
    }

    private static MarklinLocomotive loco(String name, int address) throws Exception
    {
        MarklinLocomotive l = model.getLocByName(name);

        if (l == null) l = model.newMM2Locomotive(name, address);

        return l;
    }

    /** A Central Station multi-unit holding the named members. */
    private static MarklinLocomotive centralStationMultiUnit(String name, int muAddress,
        String... members) throws Exception
    {
        MarklinLocomotive l = loco(name, 1);

        model.changeLocAddress(name, muAddress, Locomotive.decoderType.MULTI_UNIT);

        l = model.getLocByName(name);

        Map<String, Double> names = new HashMap<>();

        for (String m : members) names.put(m, 1.0);

        l.setModelMultiUnitLocomotives(names);

        return l;
    }

    @Test
    public void twoCentralStationMultiUnitsSharingAMemberAreSeenAsCompatible() throws Exception
    {
        MarklinLocomotive member = loco("X8 member", 60);

        MarklinLocomotive muOne = centralStationMultiUnit("X8 mu one", 4001, "X8 member");
        MarklinLocomotive muTwo = centralStationMultiUnit("X8 mu two", 4002, "X8 member");

        assertEquals(muOne.getDecoderType(), Locomotive.decoderType.MULTI_UNIT,
            "precondition: mu one is a Central Station multi-unit");
        assertEquals(muTwo.getDecoderType(), Locomotive.decoderType.MULTI_UNIT,
            "precondition: mu two is a Central Station multi-unit");

        assertTrue(muOne.getModelMultiUnitLocomotives().contains(member),
            "precondition: mu one holds the member");
        assertTrue(muTwo.getModelMultiUnitLocomotives().contains(member),
            "precondition: mu two holds the member");

        // Control: a predicate that refused everything would satisfy the two claims below.
        assertFalse(muOne.isSimultaneousMultiUnitCompatible(member),
            "control: a multi-unit and its own member are never simultaneously compatible");

        assertFalse(muOne.isSimultaneousMultiUnitCompatible(muTwo),
            "two multi-units that drive the same locomotive must not be run as two trains");
        assertFalse(muTwo.isSimultaneousMultiUnitCompatible(muOne),
            "and the same asked the other way round");
    }

    /**
     * The mixed case, which the caller's both-directions call already covers - here as the control
     * that says the gap above is specific to a MULTI_UNIT on the LEFT of the comparison.
     */
    @Test
    public void aLinkedHeadAndAMultiUnitSharingAMemberAreCaught() throws Exception
    {
        MarklinLocomotive member = loco("X8 member", 60);
        MarklinLocomotive linked = loco("X8 linked", 61);

        Map<String, Double> links = new HashMap<>();
        links.put(member.getName(), 1.0);
        linked.preSetLinkedLocomotives(links);
        linked.setLinkedLocomotives();

        assertTrue(linked.getLinkedLocomotives().containsKey(member),
            "precondition: the link was made");

        MarklinLocomotive mu = centralStationMultiUnit("X8 mu one", 4001, "X8 member");

        assertFalse(linked.isSimultaneousMultiUnitCompatible(mu),
            "asked with the linked head on the left, the shared member is found");

        assertFalse(mu.isSimultaneousMultiUnitCompatible(linked),
            "and asked with the multi-unit on the left it must be found too");
    }
}
```

**What I would change.** Give the second loop the same branch the first one has — walk
`getModelMultiUnitLocomotives()` when `this` is a `MULTI_UNIT` — or better, extract the one question
("which locomotives does this one command?") into a single method and call it on both sides. The
javadoc on `isSimultaneousMultiUnitCompatible` should then say that it is symmetric, because
`sanitizeMultiUnits` asking both ways is the only thing that currently makes the mixed case work.

**Consequence on the railway.** Both multi-units get placed on the graph, both get dispatched, and
every speed and direction command sent to one fans out to a decoder the other is also driving.

---

## B — medium

| | | |
|---|---|---|
| `X8-B1` | Control+X with nothing under the pointer empties the clipboard and arms a null paste | closed |
| `X8-B2` | `.xoffset`/`.yoffset` on a page are deleted from `gleisbild.cs2` | closed |
| `X8-B3` | ` .S88Flag` is not recognised as an array key; a conditional route first in the file is dropped | closed |
| `X8-B4` | deleting a square tells the setup about its caption and nothing else | closed |
| `X8-B5` | Enable/Disable auto-execution strips a Central Station route's lock | closed |
| `X8-B6` | the help says conditions hold a route back; manual execution never reads them | closed |

---

### X8-B1 — Control+X over the palette throws away the group clipboard and arms the paste tool with nothing in it

**Status:** closed 2026-09-10.  `initCopy` refuses when it has neither a label nor a component, which is the only shape the two key branches can produce.  NARROWER THAN THE FINDING PROPOSED, and deliberately: refusing on the component alone broke the column drag, which hands over a real label and no component for every blank square it carries - `regression.testLayoutEditorBulkEdits` caught it.  Three claims in `regression.testCutWithNothingHovered`, verified against both mutations.

**What is wrong.** `LayoutEditor`'s Control+V branch null-checks the hovered label; its Control+X and
Control+C branches do not.

`src/org/traincontrol/gui/LayoutEditor.java:7122-7157`

```java
if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_V)
{
    if (this.hasGroupClipboard() && getLastHoveredLabel() != null) { ... }
    else if (this.hasToolFlag() && getLastHoveredLabel() != null) { ... }
}
else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_X)
{
    if (!this.selection.isEmpty()) this.cutSelection();
    else this.initCopy(getLastHoveredLabel(), null, true);        // no null check
}
else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_C)
{
    if (!this.selection.isEmpty()) this.copySelection();
    else this.initCopy(getLastHoveredLabel(), null, false);       // no null check
}
```

`getLastHoveredLabel()` is `grid.getValueAt(lastHoveredX, lastHoveredY)` (`:6715-6718`), and
`LayoutGrid.getValueAt` returns null for a negative coordinate (`LayoutGrid.java:2064-2072`).
`lastHoveredX/Y` start at `-1` (`:72-73`) and are set back to `-1` every time the pointer crosses the
palette — `receiveMoveEvent` does `lastHoveredX = getX(label)` at `:1044` and `getX` of a label that is
not in the grid returns `-1` (`LayoutGrid.getCoordinates:2048-2061`). The same method's next branch,
`if (lastHoveredX == -1)`, is the palette tooltip, so this is a state the file already knows about.

`initCopy` (`:2608-2631`) then does three things with a null label:

```java
this.groupClipboard = null;                             // a group copy is discarded
this.lastX = getX(label);                               // -1
this.lastComponent = layout.getComponent(lastX, lastY); // null, getComponent is bounds-guarded
this.toolFlag = move ? tool.MOVE : tool.COPY;           // hasToolFlag() is now TRUE
```

`hasToolFlag()` is what enables the right-click **Paste** item
(`LayoutEditorRightclickMenu.java:65`) and what Control+V tests, so both then reach
`executeTool` → `execCopy` → `new LayoutDiagramComponent(lastComponent)` at `:2465`, whose copy
constructor dereferences `original.type` on its first line
(`src/org/traincontrol/base/LayoutDiagramComponent.java:106-107`). `execCopy` catches only
`IOException` (`:2551`). Through the menu, the NPE lands in
`catch (Exception e) { JOptionPane.showMessageDialog(this, e.getMessage()); }`
(`LayoutEditorRightclickMenu.java:58-61`) — an NPE's message is null, so the user gets a dialog with
nothing in it. `executeTool` has already called `snapshotLayout()` (`:2175`), so an undo entry is
pushed for an edit that never happened.

**How I know.** A test written for this pass, driving the editor's real handlers.

First run — the clipboard and the arming, with the control passing:

```
--- regression.testX8CutWithNothingHovered
Total tests run: 1, Failures: 1, Skips: 0

message="Control+X over the palette:
  [1] Control+X with nothing under the pointer threw away the group already on the clipboard.
  [2] Control+X with nothing under the pointer armed the paste tool with no component, so
      Control+V and the right-click Paste item are both offered and both reach
      new LayoutDiagramComponent(null)."
```

The control — `assertTrue(answers[2], "control: Control+X over an occupied square armed nothing…")` —
passed in that run, so `hasToolFlag()` is not a predicate that answers true regardless.

Second run, with the armed tool actually run through `executeTool`, which is the public door both
Control+V and the menu item reach:

```
message="pasting the tool armed by Control+X over the palette threw java.lang.NullPointerException
         expected [null] but found [java.lang.NullPointerException]"
```

Two things this test found on the way that are worth writing down, because they are how a test of this
window goes green while exercising nothing:

- `selectRow` leaves the selection populated, so Control+X takes the `cutSelection()` branch and never
  reaches `initCopy` at all. The gesture has to `clearSelection()` first.
- `formKeyPressed` puts its **whole body** inside `SwingUtilities.invokeLater`
  (`LayoutEditor.java:6916-6919`), so a test that presses a key and reads the result inside one
  `invokeAndWait` reads the state from before the handler ran. The reads have to be in a later block.

**What I would change.** Refuse in `initCopy`: a null label, or a null component after the lookup, is
not something to arm — return without touching `groupClipboard` or `toolFlag`. That is one guard rather
than two more null checks at the key handler, and it also closes the `receiveKeyEvent` door at `:889`,
which has no callers today and no null check either.

---

### X8-B2 — `.xoffset` and `.yoffset` on a page are deleted from `gleisbild.cs2` by any page add, rename or delete

**Status:** closed 2026-09-10.  `readLayoutIndexPageExtras` keeps the keys inside a `seite` block that the writer does not emit, keyed by page id so they survive a rename, and `writeLayoutIndex` writes them back with the page.  Two claims in `core.testParseCS2Layout`, mutation-verified.

**What is wrong.** `readLayoutIndexExtras` preserves whole unmodelled *blocks* and declares `seite`
modelled, so every key inside a `seite` block that the writer does not itself emit is dropped.

`src/org/traincontrol/base/LayoutDiagram.java:995-1030`

```java
final java.util.Set<String> modelled = new java.util.HashSet<>(
    java.util.Arrays.asList("version", "groesse", "seite"));
...
if (modelled.contains(trimmed.toLowerCase()))
{
    current = null;          // the following " .key" lines are dropped
}
```

and the writer, `writeLayoutIndex` (`src/org/traincontrol/base/LayoutDiagram.java:1345-1352`):

```java
contents.append("seite\n");
contents.append(" .id=").append(id).append("\n");
contents.append(" .name=").append(layout).append("\n");
```

**How I know.** Read both, then looked at the real index:

```
$ head -25 "Oles kreds/config/gleisbild.cs2"
[gleisbild]
version
 .major=1
groesse
zuletztBenutzt
 .name=0 stationer
seite
 .name=0 stationer
 .xoffset=1
 .yoffset=3
seite
 .id=1
 .name=1 gods
seite
 .id=2
 .name=2 opstilling
 .xoffset=5
 .yoffset=5
...

$ grep -rn "xoffset\|yoffset" src/ test/
(nothing)
```

Two of that layout's eight pages carry a scroll offset and nothing in the codebase has heard of it.
`zuletztBenutzt` is fine — it is not in the modelled set, so it is preserved whole.

This is the same omission as `X8-A1` one level up, and it is a **one question, two answers** case
within the file: `LayoutDiagramComponent.setUnmodelledKeys` / `exportToCS2TextFormat`
(`:786-798`, `:906-912`) keep unmodelled *keys inside a modelled element*; the index writer keeps
unmodelled *blocks* but not unmodelled keys inside a modelled block. The rule was applied at the
element level and not at the block level.

Live callers: `LayoutPageEdit.java:265`, `TrainControlUI.java:24795` and `:25224`.

**What I would change.** Keep the unmatched keys of a `seite` against its name and re-emit them after
`.name`, the way an element's unmodelled keys are already kept. This and `X8-A1` should be fixed
together, because either one alone leaves the sibling as the next reviewer's finding.

---

### X8-B3 — the array-key regex was widened in one branch and not its sibling, so ` .S88Flag` is not recognised

**Status:** closed 2026-09-10, in two halves - and the second was found by the test.  Widening the array-header regex alone made it WORSE: the conditions then arrived under their own `S88Flag` key, which `parseRoutes` never read, so every route kept its commands and silently lost every condition.  `testConditionsOnTheRealRouteFile` failed and said so.  `parseRoutes` now reads both keys.  Pinned by `core.testParseCS2Routes.testAConditionalRouteFirstInTheFileIsNotDropped`, which is the only test in the class that can see the original defect.

**What is wrong.** `parseFileContents` has two arms that match a ` .key` line, and only one of them
accepts uppercase and digits.

`src/org/traincontrol/marklin/file/CS2File.java:538` and `:549`

```java
else if (s.matches("^ \\.[a-z0-9A-Z]+=.+$"))   // " .key=value"   - widened
{ ... }
else if (s.matches("^ \\.[a-z]+$"))            // " .key"         - not widened
{
    lastKey = s.substring(2);
}
```

The Central Station's route file spells its condition blocks ` .S88Flag`. That line has no `=`, so it
takes the second arm and fails the character class, and `lastKey` is **not** updated. The `..kont=` /
`..hi=` group that follows is flushed at `:523` under whatever `lastKey` still holds from earlier in
the file — a global, initialised to `null`.

**How I know.** Read the two regexes, then confirmed the spelling and the ordering against the real
file:

```
$ grep -n "S88Flag" -B4 -A4 "Oles kreds/config/fahrstrassen.cs2" | head
2317-fahrstrasse
2318- .id=177
2319- .name=yi 14i
2320- .s88=14
2321: .S88Flag
2322- ..kont=15
2323: .S88Flag
2324- ..kont=3
2325- ..hi=0
2326- .item

$ grep -n "^ \.item$\|^ \.S88Flag$" "Oles kreds/config/fahrstrassen.cs2" | head -5
7: .item
```

**On this file it works by accident.** `lastKey` is already `"item"` by line 2321, because the first
route in the file has no conditions and opens with ` .item` at line 7. The conditions land in the
`item` string separated by `|`, and `parseRoutes` (`CS2File.java:809-865`) walks the pieces asking
each for `magnetartikel` or `kont`, so a piece that is a condition becomes a condition and one that is
a command becomes a command. I traced that through and it does produce the right route.

**The failure mode is one file-ordering away.** `.S88Flag` precedes `.item` *within* a route — visible
above on ids 177 and 179. If the first `fahrstrasse` in the file is a conditional one, the first flush
is `item.put(null, "{kont=…}")`, and then `parseRoutes` at `:776`:

```java
if (m.get("id") == null || m.get("item") == null || m.get("name") == null)
{
    control.logf("route.invalidCs2Route", ...);
    continue;
}
```

`m.get("item")` is null, and **the whole route is dropped** — its commands as well as its conditions —
with one log line. Route ids are not necessarily ascending in file order and the operator controls
which route is first.

I have **not** produced a file that triggers it; this is read from the parser plus the shape of the
real data. That is why it is B and not A.

**What I would change.** Widen the third regex to match the second: `^ \\.[a-z0-9A-Z]+$`. Then check
the rest of that method for the same asymmetry — the pair is nine lines apart and the character class
was clearly edited once.

---

### X8-B4 — deleting a square tells the setup about its caption and nothing else, and the method beside it says otherwise

**Status:** closed 2026-09-10.  `delete` calls `forgetTiles`, as `clear()`'s comment already claimed it did, and that comment now records that it did not.  `forgetTiles` is a superset of `forgetCaptionsAt` - `forgetSquares` drops what each collection knows about a square AND anything pointing at it - so the caption half is not lost, and the test asserts it.  `regression.testDeleteForgetsTheWholeSquare`, mutation-verified.

**What is wrong.** `LayoutEditor.delete` calls `AutonomySession.forgetCaptionsAt`; every sibling
gesture calls `forgetTiles`, which forgets the whole square.

`src/org/traincontrol/gui/LayoutEditor.java:3651-3661`

```java
// forgetCaptionsAt returns whether it changed anything, and this ignored it - so
// deleting a square that had no caption still wrote the whole setup to disk, every file of it.
if (autonomy != null && autonomy.forgetCaptionsAt(
        new org.traincontrol.automationui.TileGraph.TileKey(
            layout.getName(), getX(label), getY(label))))
{
    rememberAutonomy(autonomy);
}
```

`AutonomySession.forgetCaptionsAt` (`src/org/traincontrol/automationui/AutonomySession.java:1884-1900`)
touches the `captions` map and nothing else. So a deleted platform square keeps its `stations`
membership, `pointNames`, `tileLengths`, `tileDirections`, `barredArrivals`, `stationSignals`,
`blockedPoints`, `portals` and `linkNames`, and its `configurations.points` block.

Every other gesture forgets the lot: `execCopy` at `:2514`, `pasteSelection` at `:3150`,
`fillSelection` at `:3486` and `clear` at `:5183` all reach `forgetTiles`.

**The comment that makes this a finding rather than a design choice** is `clear()`'s own, at
`src/org/traincontrol/gui/LayoutEditor.java:5174-5182`:

> *"**Deleting one square tells the setup so**; emptying the whole page told it nothing at all… A
> reconciling save would eventually prune them, but the non-reconciling writes on the way out - the
> window captures the running layout and saves without reconciling when it closes - commit that state
> to disk first."*

The first clause is false of the method it is contrasting itself with, and the argument in the second
half applies to `delete` word for word.

**The honest caveat**, and it is why this is B rather than A: after a delete the square really *is*
empty, so a reconciling save can prune the orphans, which is not true of the paste case `execCopy`'s
comment describes. What `clear()`'s comment says is that the non-reconciling exit write gets there
first. I did not execute that sequence.

**How I know.** Read `delete`, `forgetCaptionsAt`, `forgetTiles` and the four sibling call sites.
Not executed.

**What I would change.** Call `forgetTiles` from `delete`, as `clear` already claims it does, and
correct `clear()`'s comment either way so the two agree.

---

### X8-B5 — turning auto-execution off on a Central Station route unlocks it, and the two menu items beside it guard on exactly that lock

**Status:** closed 2026-09-10.  `editRoute` carries `locked` across its delete-and-re-add, the way it already carried the id and the autonomy activation.  The menu is left alone: Enable/Disable Automatic Execution is ungated on purpose - a station route's auto-fire is TrainControl's to change - and what was wrong is that exercising it destroyed the lock.  `core.testRoutes.testEditingARouteKeepsItsLock`, mutation-verified.

**What is wrong.** `editRoute` re-creates the route and restores its id and its autonomy activation,
and does not restore `locked`.

`src/org/traincontrol/marklin/MarklinControlStation.java:1907-1930`

```java
Integer id = existing.getId();

// BEFORE THE DELETE STRIPS IT (AC2-A1).
final boolean wasActivated = this.isRouteActivatedByAutonomy(id);

existing.disable();
this.deleteRoute(name);

if (!this.newRoute(trimmedNewName, id, route, s88, s88Trigger, routeEnabled, conditions)) { ... }

this.restoreRouteActivation(id, wasActivated);
```

`newRoute` builds a fresh `MarklinRoute`, whose `locked` field defaults to false
(`src/org/traincontrol/marklin/MarklinRoute.java:71`). The only writer of `true` is the import loop
(`MarklinControlStation.java:1428`).

The affordance and the guard disagree. In `RightClickRouteMenu`, Change Route ID and Delete are behind
`if (!route.isLocked())` (`src/org/traincontrol/gui/RightClickRouteMenu.java:139`); Enable/Disable
Automatic Execution, twenty lines above at `:117-137`, has no such test — and it reaches
`enableOrDisableRoute` → `writeRouteEnabledState` → `editRoute`
(`src/org/traincontrol/gui/TrainControlUI.java:20560-20563`). `BulkEnableOrDisable` applies the same
write to every route matching a pattern (`TrainControlUI.java:20488-20502`).

**What compensates, and how far.** `enableOrDisableRoute` calls `syncWithCS2()` immediately afterwards
(`TrainControlUI.java:20530`), and the import re-locks every route it finds
(`MarklinControlStation.java:1424-1428`). That holds only if the station answers and still carries
that id. With the Central Station off — which is how the application is used in simulate mode, and how
it behaves when the sync times out, as it did in every run in this pass — the route stays unlocked for
the rest of the session, and the same menu then offers Delete and Change Route ID on it.

`MarklinRoute.toJSON`/`fromJSON` (`:1210`, `:1257`) do not carry `locked` either, so route export and
import lose it the same way. That half is arguably right, since a route imported from a file is not a
station route until the sync says so.

**How I know.** Read `editRoute`, `newRoute`, both menu items, `writeRouteEnabledState`, and the
`setLocked` call sites (`grep -rn "setLocked" src/` gives exactly two, at `:1373` and `:1428`, both
inside the sync). Not executed.

**What I would change.** Carry the lock across `editRoute` the way `wasActivated` already is — three
lines, in the method that already knows this is a re-key of a route that continues to exist. And ask
whether Enable/Disable should be behind `isLocked()` at all: if a station route's auto-fire is
TrainControl's to change, the lock should survive it; if it is not, the item should be greyed like its
two neighbours. Either answer is one predicate; today there are two.

---

### X8-B6 — the editor tells the user a route is "held until these are true", and the three manual doors fire it regardless

**Status:** closed 2026-09-10 as a documentation defect, WHICH IS ADAM'S TO OVERRULE.  The reading taken is that conditions belong to the trigger: a route run by hand is the case where the operator can see the railway and the condition cannot, which is the same rule the tiered destination lists follow and the same reason a person may answer OK to a switch under a train.  So `execRoute` is unchanged and the help text now says which firing the conditions hold back, in all eight languages; `behaviour.md` section 7a records it, and `core.testRoutes.testARouteRunByHandIgnoresItsConditions` pins it.  If Adam wants the other answer, `execRoute` needs the monitor's guard and the manual doors need a way past it.

**What is wrong.** Route conditions are evaluated in exactly two places — the s88 monitor thread and
the editor's Test button. `execRoute` never asks.

`src/org/traincontrol/marklin/MarklinRoute.java:199` (the monitor thread):

```java
if (this.hasConditions() && !this.conditions.evaluate(network))
{
    this.network.logf("route.s88ConditionFailed", this.getName());
    continue;
}
```

`execRoute(boolean auto, int recursionLimit, boolean overrideConflicts)` at `:556` contains no
`evaluate` call. I grepped every `.evaluate(` in `src/`; the only two hits are the line above and
`RouteEditorFrame.java:2575`.

So all three human doors run the whole route with its conditions false: the play button and the
right-click Execute item both reach `TrainControlUI.executeRoute` (`:18695`, from
`RightClickRouteMenu.java:88`) and thence `model.execRoute(route)` at `:18796`, and the diagram's
route tile calls `this.route.execRoute(false)` directly (`LayoutDiagramComponent.java:183`).
`MarklinControlStation.execRoute` (`:3306-3318`) ends in `r.execRoute(false)`.

The application says otherwise, in two places the user sees:

- `route.ui.frameHelp` (`src/org/traincontrol/resources/messages.properties:855`), under CONDITIONS:
  **"The route is held until these are true."** No qualification about which door.
- the Test button, `RouteEditorFrame.testAgainstTheRailway` at `:2565-2581`, reports "would fire" as
  `sensor && held` — so a user who adds "S88 1 is on", presses Test and is told the route would not
  fire, then presses Play, gets every switch in it thrown.

`behaviour.md` does not settle it. §7a and §8 are the sections about routes and neither says whether a
hand-run route reads its conditions; the only mention of route conditions in the whole document is
`docs/reference/behaviour.md:1115`, and it is about `getAccessoryStateIfPresent` not creating an
accessory row.

**How I know.** Read `execRoute` and the monitor loop, grepped for every `evaluate(` call site, read
the help string and the Test button. Not executed.

**What I would change.** This is Adam's to decide, and it is a `behaviour.md` sentence either way. If
conditions belong to the trigger — which is plausibly the CS2 semantics — the help text should say
"held until these are true **before it fires itself**", and Test should say which question it is
answering. If they belong to the route, `execRoute` needs the same guard the monitor has, and the
manual doors need a way past it, because a route the operator is running by hand is exactly the case
where he can see the railway and the condition cannot.

---

## C — low

| | | |
|---|---|---|
| `X8-C1` | five `File:LINE` citations point at unrelated code, against the codebase's own written rule | closed |
| `X8-C2` | a route command delay of 1-150 ms is accepted, stored, exported and ignored | closed |
| `X8-C3` | `CommandRow.hasDelay`'s javadoc names three kinds that cannot carry a delay; seven cannot | closed |
| `X8-C4` | `getSecondsToNext` is milliseconds and its field comment says seconds | closed |
| `X8-C5` | Shift Down and Shift Right grow the page past `MAX_SIZE`; Increase Size beside them is greyed there | closed |
| `X8-C6` | `LayoutDiagram:1347` quotes an expression `CS2File:2109` records as replaced | closed |
| `X8-C7` | `timetableSignature`'s javadoc says "nothing else is" and the code appends two more fields | closed |

---

### X8-C1 — five comments cite a file and a line number, and five of them land somewhere else

**Status:** closed 2026-09-10.  All seven citations replaced by the name of what they point at, including the two the finding says were correct - a rule with two exceptions is a rule no guard can enforce.  `regression.testEveryCitationResolves.testNoCommentCitesALineNumber` refuses the shape outright rather than resolving it, since a resolver would rot exactly as the citations did.  Two exemptions: the rule's own counter-example in `CommandRow`, and a `git show` revision reference.

**What is wrong.** `CommandRow.java:284-286` states the rule:

> **"By method name, not by line** (`VD9-C12`): the first version of this paragraph cited
> `RouteEditorFrame:3415-3419`, which was already wrong when it was copied here out of a review, and a
> stale line number in a javadoc outlives every edit above it with nothing able to notice."

The rule was written and not swept. Every `File:LINE` or `:LINE` citation in `src/`, checked:

| Citing comment | Cites | What it says is there | What is actually there | Where it really is |
|---|---|---|---|---|
| `automation/Layout.java:2404` | `:2243` | "the stricter form - any edge with an inactive endpoint" | `trainsUnderway()` | `Layout.java:2314` |
| `automation/Layout.java:6143` | `:3232` | "Provably at its start" | a paragraph about `Edge.setUnoccupied` | `Layout.java:3497` |
| `automation/Layout.java:6154` | `:2923` | "it went straight out to executePath's handler" | the `setBlockedBy` loop | `Layout.java:3192` |
| `gui/AutonomyEditorPanel.java:7710` | `TrainControlUI:2259` | "the save on the way out" | the `writeLayoutIndex` floor, IAR-A1 | `TrainControlUI.java:2491` |
| `gui/RightClickFunctionMenu.java:302` | `GraphLocAssign:253` | "applies at commit time instead" | the trainLength combo prefill | `GraphLocAssign.java:338-339` |
| `gui/TrainControlUI.java:18523` | `LocomotiveSelector.java:394` | "its only call site has been commented out" | `formWindowStateChanged` | `LocomotiveSelector.java:406` |

Two citations of this shape are correct and stay: `AutonomyBuilder:844` (cited from
`Layout.java:6501`) and `AutonomyViewerPanel:1156-1158` (cited from `AutonomySession.java:503`) both
land on the code they name. The `git show master:.../GraphRightClickGeneralMenu.java:111` at
`AutonomyEditorPanel.java:8301` is a git revision reference, not a pointer into the tree.

**How I know.**

```
$ grep -rn ':[0-9][0-9][0-9][0-9]*' src/ --include=*.java | grep -E '//|\*' \
    | grep -vE 'http|[0-9]:[0-9]{2}' | grep -oE '[^ ]*:[0-9]{2,5}[^ ]*' | sort | uniq -c
```

then `sed -n` on each cited line, and `grep -n` for the phrase each citing comment quotes. The table
above is that output.

`regression.testEveryCitationResolves` cannot see any of this. Its `CITATION` pattern is
`\b([A-Z][A-Z0-9]{1,7}-[A-Z]?\d+[a-z]?)\b` — finding ids only. This is a guard that knows only what it
lists, reporting clean about a class of citation it never heard of.

**What I would change.** Replace each with the method name, as `CommandRow` already says to. And if
the shape is to be kept anywhere, teach `testEveryCitationResolves` a second pattern that resolves
`File:LINE` by checking the quoted phrase is within a few lines of it — the citations that carry a
quoted phrase are the ones this table could check mechanically, and all six do.

---

### X8-C2 — a delay between 1 and 150 milliseconds is accepted, stored, exported and then ignored

**Status:** closed 2026-09-10.  The executor states the floor once - `max(delay, DEFAULT_SLEEP_MS)` - and logs what it actually used whenever a delay was asked for; `delayOf` raises a smaller number as it is typed, so the cell holds the number the railway will use.  The floor is kept because it is real: it is the gap between two route commands, and `THREEWAY_ROUTE_DELAY_MS` is defined as sitting above it.

`src/org/traincontrol/marklin/MarklinRoute.java:1003-1013`

```java
if (rc.getDelay() > MarklinRoute.DEFAULT_SLEEP_MS)          // 150
{
    this.network.logf("route.delay", rc.getDelay());
    Thread.sleep(MarklinControlStation.SLEEP_INTERVAL + rc.getDelay());
}
else
{
    Thread.sleep(MarklinControlStation.SLEEP_INTERVAL + MarklinRoute.DEFAULT_SLEEP_MS);
}
```

The editor's Delay column accepts any non-negative integer —
`RouteEditorFrame.delayOf` (`:2742-2755`) returns the parsed value and only rejects negatives and
rubbish — `CommandRow.build` keeps it (`CommandRow.java:467`), `RouteCommand.toLine` writes it
(`RouteCommand.java:643`, `:667`, `:671`, `:675`), `fromLine` reads it back, and the table redisplays
it. Somebody lowering a pause from 300 to 100 to speed a route up sees the number change and the
railway not, and does not even get the `route.delay` log line the other branch prints.

**How I know.** Read the executor, the editor's parse and the two constants
(`DEFAULT_SLEEP_MS = 150` at `MarklinRoute.java:42`, `SLEEP_INTERVAL = 50` at
`MarklinControlStation.java:234`). Not executed.

**What I would change.** Either honour it — `Thread.sleep(SLEEP_INTERVAL + max(delay,
DEFAULT_SLEEP_MS))` says the same thing without the silent branch — or refuse it in `delayOf` with the
floor named, so the number in the cell is a number the railway will use. This is the same shape as
`X8-B6` and `X8-C3`: an affordance offering a value the executor discards.

---

### X8-C3 — `hasDelay`'s javadoc names three kinds that cannot carry a delay, and the predicate excludes seven

**Status:** closed 2026-09-10.  The javadoc states the RULE - `RouteCommand.toLine` writes a delay in four branches and nowhere else - and then names all seven kinds, since the roll-call is what went stale.

`src/org/traincontrol/base/CommandRow.java:309-318`

```java
/**
 * Whether this kind can carry a delay.  Feedback, stop and functions-off cannot - RouteCommand
 * only writes a delay for accessory, speed, direction and function commands.
 */
public static boolean hasDelay(Kind kind)
{
    return kind == Kind.ACCESSORY || kind == Kind.SIGNAL || kind == Kind.LOCOMOTIVE_SPEED
        || kind == Kind.LOCOMOTIVE_DIRECTION || kind == Kind.FUNCTION
        || kind == Kind.THREE_WAY;
}
```

`Kind` has thirteen members. The predicate admits six, so seven cannot carry a delay: FEEDBACK, STOP,
FUNCTIONS_OFF, **LIGHTS_ON, AUTONOMY_LIGHTS_ON, ROUTE and AUTO_LOCOMOTIVE**. The second clause is
right — `RouteCommand.toLine` writes a delay only in the accessory, direction, speed and function
branches — and the first is a list that stopped being complete.

The class already carries the identical correction fifty lines above, on `canBeACondition`: *"This
said **four**, and there was no version in which four was right."* Same file, same kind of miscount,
different method.

**How I know.** Read the predicate against `CommandRow.Kind` and against every branch of
`RouteCommand.toLine` (`:623-683`). The two agree; only the sentence does not.

---

### X8-C4 — `getSecondsToNext` is in milliseconds

**Status:** closed 2026-09-10 as a comment, not a rename.  The field says milliseconds and says why the name does not; renaming it would touch nine call sites, every one of which is already right.

`src/org/traincontrol/automation/TimetablePath.java:22-23`

```java
// Seconds to the next execution.  Precalculated externally
private long secondsToNext = 0;
```

Every writer and reader uses milliseconds: `Layout.java:835` stores a difference of two
`System.currentTimeMillis()` stamps; `Layout.java:5124` compares it against
`System.currentTimeMillis() - startTime`; `TrainControlUI.java:25847` multiplies the user's typed
seconds by 1000 and `:25842` and `:27368` divide by 1000 to show it; `testLayoutTimetable.java:150`
asserts `10000L` for a ten-second gap. The name and the comment are the only two things in the chain
that say seconds.

**How I know.** `grep -rn "setSecondsToNext\|getSecondsToNext" src/ test/` and read all nine sites.
Nothing behaves wrongly; this is a trap for whoever writes the tenth.

---

### X8-C5 — Shift Down and Shift Right grow the page past `MAX_SIZE`, which the item beside them is disabled to prevent

**Status:** closed 2026-09-10.  `LayoutEditor.roomToGrow` is the one predicate, and `canShiftDown`, `canShiftRight`, `growEdges`, `addRowsAndColumns` and the right-click menu all ask it.  Three copies of the ceiling became one.

`src/org/traincontrol/gui/LayoutEditor.java:4444` and `:4464`

```java
public boolean canShiftDown()  { return lastHoveredY >= 0; }
public boolean canShiftRight() { return lastHoveredX >= 0; }
```

`shiftDown` (`:4562-4571`) calls `LayoutDiagram.shiftDown`, whose first act is
`addRowsAndColumns(1, 0)` (`src/org/traincontrol/base/LayoutDiagram.java:735`). Neither
`LayoutDiagram.shiftDown` nor `LayoutDiagram.addRowsAndColumns` (`:586`) has a `MAX_SIZE` check; the
ceiling lives only in `LayoutEditor.growEdges` (`:4808`).

On the same popup, `LayoutEditorRightclickMenu.java:443-447`:

```java
boolean canGrow = edit.getMarklinLayout().getSx() < LayoutEditor.MAX_SIZE
    && edit.getMarklinLayout().getSy() < LayoutEditor.MAX_SIZE;
menuItem.setEnabled(canGrow);
menuItem.setToolTipText(canGrow ? "Control+I"
    : I18n.f("layout.ui.errorMaxSizeExceeded", LayoutEditor.MAX_SIZE));
```

So at the ceiling, "Increase Size" is greyed with a tooltip giving the reason, while "Shift Down" two
items away grows the page by exactly one row and succeeds. `canShiftDown`'s own javadoc cites `LE-C1`,
which is the finding about one submenu expressing one rule two ways.

**How I know.** Read `canShiftDown`, `shiftDown`, `LayoutDiagram.shiftDown`,
`LayoutDiagram.addRowsAndColumns`, `growEdges` and the menu builder. Not executed.

**What I would change.** Ask `growEdges`'s predicate in `canShiftDown`/`canShiftRight` as well, or put
the ceiling into `LayoutDiagram.addRowsAndColumns` where both paths run through it. The second is the
smaller fix and it also covers `LayoutEditor.addRowsAndColumns` at `:4953`, which carries its own copy.

---

### X8-C6 — a comment quotes an expression the file it names records as replaced

**Status:** closed 2026-09-10.  The paragraph names `pageIdOrPosition` instead of quoting the expression it replaced.

`src/org/traincontrol/base/LayoutDiagram.java:1347-1350` explains why the id is always written:

> *"An absent id is read as the page's POSITION (CS2File: `m.get("id") != null ? m.get("id") :
> String.valueOf(position)`)…"*

`CS2File.java:2109-2116` says that expression is gone:

```java
// This said `m.get("id") != null ? m.get("id") : position`, which is the same rule for
// an ABSENT id and a different one for a corrupt id ...
page.put("id", String.valueOf(
    org.traincontrol.base.LayoutDiagram.pageIdOrPosition(m.get("id"), position)));
```

The conclusion the comment draws is still correct; the code it quotes as evidence is not. A reader
checking it finds a method that does more than the quoted line and has to work out whether the
paragraph still holds.

**How I know.** `grep -n 'm.get("id") != null' src/org/traincontrol/marklin/file/CS2File.java` — five
hits, none of them this expression, and `:2109` is the comment recording its removal.

---

### X8-C7 — `timetableSignature` says "nothing else is" and appends two more fields

**Status:** closed 2026-09-10.  The javadoc names all six fields and says which direction it is wrong in: a sub-second change moves the signature and not the table, so a repaint is asked for whenever one is needed and sometimes when it is not.

`src/org/traincontrol/gui/TrainControlUI.java:27273-27283`

> *"Everything the table draws is in here - the order, the locomotive, the two stations and whether the
> entry has run - and nothing else is, so a repaint is asked for exactly when one is needed."*

The code appends six things, the last two being `path.getExecutionTime()` and
`path.getSecondsToNext()` (`:27293-27295`). Both are drawn, so the code is right and the sentence is
incomplete — but `getSecondsToNext` is in milliseconds (`X8-C4`) and the cell shows
`getSecondsToNext() / 1000`, so a change smaller than a second changes the signature and not the
display. "Exactly when one is needed" is the half that is false.

**How I know.** Read the method against the row builder at `:27364-27371`. Harmless; listed because
the sentence is the thing a later reader checks against.

---

## D — not defects

| | | |
|---|---|---|
| `X8-D1` | placeholder parity across the eight bundles | clean, 0 of 13,524 |
| `X8-D2` | unbalanced braces and stray placeholders in translations | clean |
| `X8-D3` | the timetable wait loop busy-spinning at zero delay | compensated |
| `X8-D4` | `importRoutes` deleting before it has parsed | not so, by construction |
| `X8-D5` | `MarklinRoute.toJSON`/`fromJSON` field parity | complete but for `locked` |
| `X8-D6` | `TimetablePath` hash drift | cannot bite - the timetable is a `List` |
| `X8-D7` | `testRouteCommandParity.testRubbishIsRefused` as a test that cannot fail | it can |
| `X8-D8` | `sanitizeMultiUnits` asking one direction | it asks both, and that is what makes `X8-A2` narrow |

---

### X8-D1 — the seven translations use the same placeholders as the English, in all 1,932 keys

A translation carrying a `{2}` its source lacks renders it literally; one dropping a `{0}` loses the
value. Neither happens. A script comparing the `{n}` sets key by key, run against
`src/org/traincontrol/resources/`:

```
messages_da.properties 0
messages_de.properties 0
messages_es.properties 0
messages_fr.properties 0
messages_it.properties 0
messages_nl.properties 0
messages_pl.properties 0
TOTAL 0
```

All eight bundles have exactly 1,932 keys, which `core.testMessageBundles.testTranslationsMatchEnglishKeySet`
already holds. **Nothing guards the placeholders**, though — that class has thirteen tests and none of
them compares a translation's placeholders with its source. The data is clean today; the check is not
there. Adding it is four lines in a class that already loads every bundle.

### X8-D2 — no unbalanced brace, and no translation carrying a placeholder the English does not

The same script, two more passes over all eight bundles: values whose `{` count differs from their `}`
count (MessageFormat throws on an unmatched `{`), and translations with a `{n}` absent from the English
source. Both empty.

### X8-D3 — the timetable's wait loop does not busy-spin when the delay settings are zero

`Layout.executeTimetableInternal`'s wait loop ends in `this.pacedWait(ttp.getLoc())`
(`Layout.java:5328`), and `pacedWait` (`:5018-5035`) tests `getMinDelay() == 0 && getMaxDelay() == 0`
and falls back to `Thread.sleep(COMPLETION_POLL)`. The inner retry loop's comment — *"Paced
independently of the delay settings, which may be zero - that would busy-wait here rather than
pause"* — reads like a hazard the outer loop still has. It does not.

### X8-D4 — `importRoutes` cannot delete the user's routes and then fail to add the new ones

`MarklinControlStation.importRoutes` (`:3756-3775`) calls `parseRoutesFromJson` first and only then
deletes. `parseRoutesFromJson` wraps a malformed document in `IllegalArgumentException` (`:3736-3741`)
and `MarklinRoute.fromJSON` throws out of the loop, both before any delete. The order is the thing
that makes it safe and the method's own comment says so.

`newRoute` returning false inside the add loop still drops a route with only a `route.notAdded` log
line — that is real, but it is a logged refusal rather than a silent one, and I could not construct a
file that reaches it that the parse would not have refused first.

### X8-D5 — every persisted field of a route survives the JSON round trip, except the one in `X8-B5`

`MarklinRoute`'s persisted fields are `id`, `name`, `enabled`, `triggerType`, `s88`, `conditions`,
`route` and `locked` (`:30-71`). `toJSON` (`:1210-1248`) writes the first seven; `fromJSON`
(`:1257-1294`) reads all seven, and defaults `triggerType` to `CLEAR_THEN_OCCUPIED` when the key is
absent, which its own comment explains. `locked` is not carried, and that is `X8-B5`.

`RouteCommand`'s text form was checked branch by branch against `CommandRow.hasDelay` and they agree
about which kinds carry a delay — see `X8-C3` for the sentence that does not.

### X8-D6 — `TimetablePath`'s mutable hash cannot drift out of a collection

`hashCode` mixes `executionTime` and `secondsToNext`, both of which are written after construction
(`Layout.java:835`, `:5090`, `:8312`). That is the shape of the locomotive hash-drift defect this
project has already had. It cannot bite here: `Layout.timetable` is a `List<TimetablePath>`
(`Layout.java:593`) and no hash-based collection holds one. `grep -rn "TimetablePath" src/` gives 39
sites and none of them is a `Set` or a `Map` key.

### X8-D7 — `testRubbishIsRefused` is not a test that cannot fail

`test/core/testRouteCommandParity.java:80-95` wraps `assertNull` inside `catch (Exception expected)`,
which looks like a test that swallows its own verdict. It does not: TestNG's `assertNull` throws
`AssertionError`, which extends `Error` and not `Exception`, so a `fromLine` that returned a command
for `"this is not a command"` would still redden the class. Checked and withdrawn.

### X8-D8 — `sanitizeMultiUnits` does ask both directions, and that is why `X8-A2` is as narrow as it is

`Layout.java:6993-6995` calls `isSimultaneousMultiUnitCompatible` both ways round. That covers the
mixed case — a TrainControl-side consist and a Central Station multi-unit sharing a member — and the
second half of the `X8-A2` test measured it: `linked.isSimultaneousMultiUnitCompatible(mu)` correctly
answers false. The reverse call is the one that fails, and when both sides are multi-units both calls
fail, which is why asking twice does not save it.

---

## What I did not cover

- **The battery was not run.** Three classes were run, two of which I wrote. Everything below the level
  of the files named above is unmeasured by this pass.
- `docs/manual-tests/` — `tests.md`, `issues.md`, `findings.tsv` and `triage.db` were opened only far
  enough to check that `X8` was free as a prefix. The twenty-five rows `E8` added and the 38 tests
  awaiting Adam are still unaudited by anybody; both earlier passes said the same thing, so this is now
  three passes in a row that have declined it.
- **The network layer.** `CS2Message` and `NetworkProxy` were read only around `getSubCommand`'s
  payload-length guard, which was added for a real defect and whose siblings `extractUID` and
  `extractShortUID` do not have it. `NetworkProxy.java:255` drops any datagram that is not exactly 13
  bytes, so I could not find a frame that reaches them and did not file it.
- **Locomotive function handling** beyond the multi-unit predicates — icons, the function assign dialog
  and the CS3 function import — was read enough to notice that `parseLocomotivesCS3`
  (`CS2File.java:1657-1679`) indexes functions by array position while `parseLocomotiveFunctions`
  (`:973-1018`) indexes by `nr` and carries a comment explaining why position is wrong. All 136 locos in
  `test/CS3_loks.json` and all 154 in `test/CS3_loks_v260.json` have `nr == [0,1,2,…]`, so it cannot
  fire on any data I have. Not filed, on the "this could happen versus this does happen" rule; worth
  someone's time if a CS3 is ever seen writing a sparse array.
- **The layout editor's rotate gesture.** It tells the autonomy setup nothing, where every other
  mutating gesture does, and `tileDirections` is keyed by which route crosses the square while
  `barredArrivals` stores side names — both of which rotation could change the meaning of. I could not
  find code that re-derives either from orientation, but I also could not establish that the stale
  values are wrong rather than simply re-interpreted. Left unfiled rather than filed as a guess.
- **The uncommitted changes in `cs2_sample_layout/`** were not reviewed. `git status` shows three files
  modified there throughout this pass; `one.sh`'s fingerprint did not fire on any run, so they are the
  running application's, not this pass's.
