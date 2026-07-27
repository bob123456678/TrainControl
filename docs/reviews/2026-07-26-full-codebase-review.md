# Full codebase review - 2026-07-26

**Version reviewed:** commit `b8aa54c` ("Locomotive hash fixes", 2026-07-26), branch `master`,
described as `v2_7_4c-49-gb8aa54c`, `RAW_VERSION` 2.8.0. **Reviewed:** 2026-07-26.
**No code was changed as part of this review, and no tests were run or added** - the author builds
and tests in NetBeans, so every claim below rests on reading the enforcing method and tracing its
callers, per [README.md](README.md). Where a claim depends on real data, the fixtures in `test/`
were checked.

**Scope:** a fresh pass over the parts of the codebase the July 2026 cycle did not deep-dive,
looking for *user-facing, reachable* bugs only. Areas read in full: `CS2File` (parsing/import/
download), `MarklinControlStation` (sync merge, accessory handling, save/restore, message
dispatch), `Locomotive` and `MarklinLocomotive`, the route subsystem (`Route`, `MarklinRoute`,
`RouteCommand`, `NodeExpression`, `RouteEditor` save flow), `MarklinSimpleComponent` /
`CustomObjectInputStream`, the track-diagram layer (`LayoutDiagram`, `LayoutGrid`,
`RemoteDeviceCollection`), stats display (`LocomotiveStats`, `UsageHistogram`, `Conversion`),
`PositionAwareJFrame`, and the keyboard-mapping save/restore in `TrainControlUI`. Not re-reviewed:
`Layout`/`executePath`/timetable internals, multi-unit fan-out, `receiveMessage`, and CS2Message -
all covered by the July documents, whose open items (D5, D3, the mutable-hash-identity root cause,
the LocDB migration matrix) remain open and are not re-litigated here.

Findings use the A/B/C/D convention in [README.md](README.md). Identifiers are local to this
document; findings from other documents are cited with their document name.

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| B1 | Local layout files are written in the platform-default charset but always read back as UTF-8; non-ASCII page names break local layouts and silently clear the override | B | Open |
| B2 | `RouteCommand.toLine` omits the newline for feedback commands; a route-editor round-trip then silently deletes the following command | B | Open |
| C1 | `changeRouteId` still uses `getAutoLayout() != null` - the always-true creating check C12 fixed in `deleteRoute` - and seven more UI sites share the pattern | C | Open |
| C2 | `MarklinRoute.toCSV` pretty-prints through `getAccessoryByAddress`, a creating lookup - opening the route editor can register phantom accessories | C | Open |
| C3 | The CS2 import parsers abort the entire sync on one malformed record; the CS3 parsers catch per-record | C | Open - no real file in evidence triggers it |
| C4 | `editRoute` deletes the route before knowing the re-add will succeed; a name collision would silently drop the route | C | Open - unreachable from current callers |
| D1 | Checks that came back clean (see section) | - | Recorded |

No A-level findings. The two B findings share a property worth stating: both are invisible in
ASCII-only, happy-path use, which is presumably how they have survived.

---

## B1. Local layout files: written in the platform charset, read back as UTF-8

**Writers using the platform default charset** (Cp1252 on the Windows/Java 8 configuration this
project targets - `javac.source=1.8`, and the message-bundle discipline in this repo exists
precisely because users run Java 8):

- [CS2File.java:1676](../../src/org/traincontrol/marklin/file/CS2File.java) `downloadCS2Layout` -
  all three `new FileWriter(...)` uses: the master `gleisbild.cs2`, every page file, and
  `magnetartikel.cs2`.
- [LayoutDiagram.java:564](../../src/org/traincontrol/base/LayoutDiagram.java) `writeLayoutIndex` -
  `new FileWriter(filePath)`, called from the layout editor's page create, rename and delete flows
  (TrainControlUI.java:13548, :13613).

**The one reader** for all of these is `CS2File.fetchURL`
([CS2File.java:391](../../src/org/traincontrol/marklin/file/CS2File.java)), which decodes
**UTF-8 unconditionally**, for local `file:///` paths as well as HTTP
(`MarklinControlStation.java:773` sets `file:///<overrideLayoutPath>/`). And the codebase already
knows the right answer: `LayoutDiagram.saveChanges` - the writer for page *content* edited in the
layout editor - uses `Files.newBufferedWriter`, which is UTF-8. The writers disagree with each
other, and half of them disagree with the only reader.

**Consequence, traced end to end.** A layout page named or labelled with any non-ASCII character
(umlauts, å/ø/æ - this repo's own sample layouts are Danish):

1. `downloadCS2Layout` or `writeLayoutIndex` writes the name as Cp1252 bytes.
2. The download flow *immediately* re-reads it: TrainControlUI.java:13787-13791 sets
   `LAYOUT_OVERRIDE_PATH_PREF` and calls `syncWithCS2()`.
3. `parseLayoutList` decodes the bytes as UTF-8; the non-ASCII byte is invalid UTF-8 and becomes
   U+FFFD, so "Køln" reads as "K�ln".
4. `getLayoutURL` builds `file:///.../gleisbilder/K�ln.cs2`; the actual file is `Køln.cs2`, so the
   fetch throws `FileNotFoundException` and `parseLayout` aborts.
5. `syncWithCS2` catches it at MarklinControlStation.java:788-796, **silently clears
   `LAYOUT_OVERRIDE_PATH_PREF`** and reverts to reading layouts from the Central Station. The
   user's local-layout setup is gone with only a log line.

Text *labels* with non-ASCII take the milder branch: the page still loads and the label renders
with replacement characters.

Characters representable in Cp1252 (ö, ü, ø...) survive the write and corrupt on the read;
characters that are not (ł, č, €-adjacent symbols...) are already flattened to `?` at write time.
Either way the round trip is broken. On a JRE whose default charset is UTF-8 (Java 18+, or Linux)
nothing goes wrong - which, combined with ASCII page names, is why this has never been seen.

**Severity.** B, not A: nothing is destroyed permanently (page content files are written UTF-8 by
`saveChanges` and stay intact; a download can be repeated; the override can be re-selected), but
the failure is silent, the trigger is an ordinary page name, and the consequence is a feature that
un-configures itself.

**Suggested fix shape:** give every writer an explicit UTF-8 charset
(`new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)`, or switch to
`Files.newBufferedWriter` as `saveChanges` already does). A regression test can be pure-JVM: write
an index with a name like `"Køln"` through `writeLayoutIndex`, re-read it through `parseFile`, and
assert the name survives - it will fail today on any JRE whose default charset is not UTF-8, so it
should pin the charset rather than rely on the platform (e.g. by asserting bytes on disk are the
UTF-8 encoding).

## B2. Feedback commands break the route editor's round-trip and silently delete a neighbour

`RouteCommand.toLine`
([RouteCommand.java:571](../../src/org/traincontrol/base/RouteCommand.java)) ends every branch
with `"\n"` **except the feedback branch**. That is harmless where feedback lines normally live -
route *conditions* are rendered by `NodeExpression.toTextRepresentation`, which appends its own
newline - but `MarklinRoute.toCSV`
([MarklinRoute.java:779](../../src/org/traincontrol/marklin/MarklinRoute.java)) concatenates
`toLine` outputs with **no separator**, and the route editor is populated from exactly that string
(TrainControlUI.java:11137 → `RouteEditor` constructor → `routeContents.setText(routeContent)`).

A feedback command can get into a route's *command* list, not just its conditions: the editor's
save path (`RouteEditor.RouteCallback`, RouteEditor.java:1831-1848) parses each line with
`fromLine` and adds **any** non-null result - `Feedback 5,1` parses fine as `TYPE_FEEDBACK` and is
stored. It is an easy mistake to make, because the syntax is identical to the condition box one
tab over, and nothing warns. Execution then ignores it silently: `MarklinRoute.execRoute` has no
`isFeedback` branch at all.

The damage comes on the next edit. `toCSV` glues the feedback line to whatever command follows it:

```
Feedback 5,1locspeed,MyLoc,50
```

On save, `fromLine` sees a line starting with `Feedback`, strips the prefix, parses the address
from `split(",")[0]` = `"5"`, and evaluates the state as `"1".equals("1locspeed")` → false. **No
exception is thrown.** The merged line parses "successfully" as `Feedback 5,0` - the following
command (`locspeed,MyLoc,50` here, but any command type; I traced accessory, locspeed and Route
continuations and all collapse the same way) is silently deleted from the route, and the feedback
setting flips from 1 to 0. The user sees the merged text only if they scrutinise the box before
saving.

**Severity.** B. The consequence is "data silently lost" (a route command the user wrote), which
reads as A, but the trigger requires the user to have put a feedback line in the commands box in
the first place - a mistake, albeit one the editor accepts without a word and execution masks. The
combination "accepted silently + does nothing + corrupts the route on the next round-trip" is the
finding.

**Suggested fix shape:** append `"\n"` in the feedback branch of `toLine` -
`NodeExpression.toTextRepresentation` already collapses repeated newlines (`replaceAll("\n+", "\n")`),
so the conditions rendering is unaffected; verify the other two `toLine` display paths the same
way. Separately worth considering: have `RouteCallback` warn on (or reject) command types that
`execRoute` will never execute - that closes the "does nothing silently" half.

---

## C. Low

### C1. `changeRouteId` kept the check that C12 removed from `deleteRoute`

[MarklinControlStation.java:2532](../../src/org/traincontrol/marklin/MarklinControlStation.java):
`if (this.getAutoLayout() != null)` - `getAutoLayout()` *creates* a `Layout` when none exists (and
bumps the static `layoutVersion`), so the test is always true and the creation is a side effect.
This is verbatim the pattern C12 fixed in `deleteRoute` twenty lines up, where the fix comment
explains the hazard; the sibling was not converted. Reachable from the route right-click menu
("change ID", RightClickRouteMenu.java:110).

Traced for consequence and found benign today: the creation can only fire when `autoLayout` was
null, no locomotive threads can exist without a `Layout`, so the version bump trips no fence; and
the autosave-on-exit path requires a non-empty graph before it will write anything, so the stray
empty `Layout` cannot overwrite `autonomy.json`. It costs an unwanted object and makes
`hasAutoLayout()` true for the rest of the session.

The same always-true-check-with-side-effect appears in the GUI layer:
TrainControlUI.java:10642, :12608, :12637, :14043; LayoutGrid.java:188;
LayoutRightclickAutonomyMenu.java:24; LayoutLabel.java:222 (this last one *looks* guarded by the
`&& isAutonomyRunning()` that follows, but `getAutoLayout()` is the left operand, so the creation
fires before the guard is consulted). All were traced to the same benign end state. Worth one
sweep converting them to `hasAutoLayout()` - not because any is dangerous today, but because C12's
fix demonstrates the project already decided this pattern is a trap, and seven copies of it
survive.

### C2. `MarklinRoute.toCSV` invents accessories on a display path

[MarklinRoute.java:788](../../src/org/traincontrol/marklin/MarklinRoute.java): the pretty-printer
resolves each accessory command through `network.getAccessoryByAddress(...)`, which **creates a
switch on miss** (verified at MarklinControlStation.java:2625). Opening the route editor for a
route that references an address absent from the accessory DB (possible for CS-imported routes
whose accessory never appears in the layout or mag files) registers a phantom "Switch N", which
the next `saveState` persists into `LocDB.data`.

This is a leftover of July's B6/B17: the fix note on the sibling display path
(`NodeExpression.toTextRepresentationHelper`) states explicitly that it moved to
`getAccessoryByName` so that "this display path no longer invents an accessory". `toCSV` is the
one display path that did not get the same treatment. The execution paths (`execRoute`,
`setAccessoryState`) create deliberately and are not part of this finding. Fix shape: the same
name-based lookup the NodeExpression path now uses.

### C3. CS2 import: one malformed record still aborts the whole sync

The CS3 parsers (`parseLocomotivesCS3`, `parseRoutesCS3`) wrap each record in try/catch, log the
bad one and continue - a shape introduced when B8's escape "aborted the entire Central Station
sync for one bad locomotive". The CS2-format parsers did not get the per-record guard, and three
unchecked escapes remain, all propagating to `syncWithCS2`'s outer catch, which abandons the
entire import (`loc.dbSyncFailed`, return -1):

- `parseRoutes` ([CS2File.java:695](../../src/org/traincontrol/marklin/file/CS2File.java)):
  non-numeric route `id` → `NumberFormatException`. (The null-checks added earlier guard *missing*
  fields only.)
- `parseRoutes` ([CS2File.java:736](../../src/org/traincontrol/marklin/file/CS2File.java)): a
  key with an empty value (`kont=`) passes the `contains("=")` test, `split("=")` yields one
  element, and `kv[1]` throws `ArrayIndexOutOfBoundsException`. `parseLocomotiveFunctions` gained
  exactly this guard ("a key with no value would otherwise run off the end"); the route parser has
  the same shape unguarded.
- `parseLocomotives` ([CS2File.java:1576](../../src/org/traincontrol/marklin/file/CS2File.java)):
  a `traktion` block with no `.uid` → `Integer.decode(null)` → `NullPointerException`. The
  address/uid selection above it handles the missing-field case with a skip-and-log; the
  multi-unit branch re-reads `uid` unconditionally.

**Reachability, checked against real data:** every entry in `test/lokomotive.cs2` has a `.uid`
and numeric fields, no fixture contains an empty-valued key (`=$` matches nothing in any `.cs2`
fixture), and CS-generated files have no reason to produce any of these. So per the README's
"could happen" / "does happen" distinction this is a trap, not a live defect - rated C, kept out
of any changelog. If touched, the fix is the CS3 shape: per-record try/catch with the existing
`route.invalidCs2Route`-style log lines, not three point guards.

### C4. `editRoute` deletes before it knows the re-add will succeed

[MarklinControlStation.java:1213](../../src/org/traincontrol/marklin/MarklinControlStation.java):
disable → `deleteRoute(name)` → `newRoute(newName, ...)`. `newRoute` refuses when the new name
already belongs to another route - and at that point the original has already been deleted, so the
route would vanish.

Unreachable from current callers, verified individually: `RouteEditor.RouteCallback` refuses a
rename onto an existing route's name before calling `editRoute` (RouteEditor.java:1865), and both
`TrainControlUI` call sites (11237, 11260) pass `r.getName(), r.getName()` - no rename at all. So
this is a guard-for-the-next-caller item in the B3/C7/C15 tradition: the enforcing check lives
only in one UI dialog, and the model method trusts it. Fix shape: check the collision in
`editRoute` itself before deleting, and return a status.

---

## D1. Checks that came back clean

Recorded so the next reviewer does not redo them - each was verified in the enforcing method, not
assumed:

- **`Conversion.convertSecondsToHMmSs(long ms)` takes milliseconds despite its name** - it divides
  by 1000 first. All callers traced (stats table, CSV export, histogram) pass milliseconds. A
  naming trap of the `secondsToNext` kind, not a defect.
- **`RemoteDeviceCollection.getItems`/`getItemIds`/`getItemNames` return copies**, so
  `importRoutes`' delete-while-iterating over `getItems()` cannot throw
  `ConcurrentModificationException`.
- **The runtime-stats lock unification holds.** `notifyOfPowerStateChange` and `_setSpeed` both
  hold `speedMonitor` and are the only two writers of `historicalOperatingTime`'s read-modify-write;
  the power-off → speed-0 and power-on → speed sequences were walked and neither loses nor
  double-counts an interval. (A session crossing midnight attributes the whole interval to the
  stop date - visible only as a stats attribution quirk, and not new.)
- **`MarklinSimpleComponent` restore is defensively written** - null/instanceof checks on every
  getter that a schema change could affect; `CustomObjectInputStream`'s two enum relocations are
  both mapped in `resolveClass` *and* `readClassDescriptor`. No new issue found by reading. The
  round-trip matrix against real 2.6.x/2.7.x data files (July's recommended area 3) still has not
  been run and remains the open item there.
- **Keyboard-mapping save/restore round-trips.** Save keys by
  `getExtendedKeyCodeForChar(buttonText.charAt(0))` over the A-Z buttons; restore looks the code
  up in `buttonMapping` and skips unknown names/keys. The active-page and active-button sentinels
  (-1/-2 keys in `pageNames`) are parsed inside a try, and out-of-range page numbers are clamped
  by `switchLocMapping`.
- **`PositionAwareJFrame.loadWindowBounds` guards against off-screen restore** across
  multi-monitor changes (per-screen bounds check with tolerance), and only restores size/state for
  resizable windows.
- **`receiveMessage`'s duplicate-packet suppression** (any packet identical to the immediately
  previous one is dropped, not just CS3 doubles) is deliberate and commented; consequences for
  actuation counting are bounded by B3-of-the-independent-review's sticky-confirmation semantics.
  Not a defect, recorded because the shape looks accidental until the comment is read.
- **`NodeExpression.fromTextRepresentation` on malformed input** (leading `AND`, unbalanced
  parentheses) throws `EmptyStackException` - unchecked, but every caller was checked: the route
  editor wraps the call in `catch (Exception)` and shows an error dialog, and the JSON path does
  not use the text parser. Ugly message, no crash.
- **`UsageHistogram` paging** cannot go below offset 0, and the reverse-ordered `TreeMap` keys
  from the stats methods match `Locomotive.getDate`'s format (re-confirmed, same result as the
  independent review's date-key check).
- **`syncWithCS2`'s route merge** (delete-on-name-collision, delete-on-changed-content, re-add,
  re-lock) was walked against `newRoute`'s refusal conditions; the sequencing cannot drop a route
  that was not genuinely replaced, and CS routes are re-locked after every sync.

---

## Method note

Per the README's standing lesson, nothing in this report was inferred from a name, a shape, or a
memory of another codebase: every "creates on lookup", "always true", "no `\n`", "UTF-8
unconditionally" and "no `isFeedback` branch" claim was made by reading the named method, and
every reachability claim by enumerating callers (`editRoute`: three; `changeRouteId`: one;
`writeLayoutIndex`: two) or checking fixtures (`test/*.cs2` for C3). The two B findings would each
be pinned best by a test written before the fix - B1's charset test and B2's round-trip test are
both pure-JVM and need no Central Station.
