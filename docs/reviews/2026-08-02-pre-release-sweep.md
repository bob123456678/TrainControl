# Pre-release review: last three commits plus a codebase sweep - 2026-08-02

**Prefix for citing this document: `RS`.**

**Version reviewed: `2cddfe9` (HEAD), working tree clean, on 2026-08-02.** Two parts, one document.

**Part 1** is a full review of the last three commits: `4e5fde8` and `e4eeed8` ("Fix allowed manual
paths through disabled intermediate points" - the model rule and a UI repaint) and `2cddfe9` ("Use
current option for home locs"). **Part 2** is a release sweep of the code no review in this folder
has read with a bug-hunting brief - principally `CS2File`, which the `UC` document named as the
virgin territory to start from - plus pattern sweeps over the unread GUI classes and a
coverage-gap inventory.

Verification was static: enforcing methods and call graphs read in source, claims about file
formats checked against the fixtures in `test/`. No javac/ant/JUnit was run from this session, per
standing practice.

Findings use the A/B/C/D convention in [README.md](README.md). Two C, five D. No A or B: nothing
found in either part puts wrong behaviour on the layout or loses data.

| ID | Finding | Status |
|---|---|---|
| RS-C1 | `CS2File.parseFileContents` splits array-syntax values on `=` without a limit - the twin of a fix applied to the adjacent non-array branch, which its own comment explains. A locomotive whose name contains `=` is silently dropped from its multi-unit on import | Fixed - `split("=", 2)`, matching its twin; pinned by `testAMultiUnitMemberNameContainingAnEqualsSignSurvivesParsing`, confirmed red first |
| RS-C2 | `downloadCS2Layout` builds local filenames from page names taken verbatim out of the fetched index, so a name carrying a path separator or a character Windows forbids fails the download mid-way, after some files have been written | Fixed - `sanitizeFilename` applied to the write AND to the local read, which the finding did not consider; pinned by `testAPageNameCarryingAPathSeparatorStaysInsideTheLayoutFolder`, confirmed red first.  The folder-deletion half is declined - see the disposition |
| RS-D1 | The unfenced inactive-intermediate rule (`4e5fde8`): tier placement, staging interaction, and the endpoint asymmetry | Clean |
| RS-D2 | The staging repaint (`e4eeed8`): ordering claim verified against the call sequence | Clean |
| RS-D3 | The home-locomotive "use current" button (`2cddfe9`): option-dialog conversion, cancel/close handling, and the rejected conditional-visibility design | Clean |
| RS-D4 | `CS2File` import robustness, charset handling, and connection lifetime re-read end to end | Clean |
| RS-D5 | Pattern sweeps over the unread GUI classes: input parsing, EDT sleeps, interrupt handling, hardcoded dialog strings | Clean |

---

# Part 1 - the last three commits

## RS-D1: the inactive-intermediate rule (`4e5fde8`)

`isPathClear` gains an unfenced refusal: an inactive point may never be an intermediate, whoever
asked for the route. Previously the only inactive-point gate was fenced behind `isAutoRunning()`,
so a manually picked route could drive a train across a point the operator had switched off.

- **The asymmetry is deliberate and coherent.** Passage is absolute; the two endpoint rules stay
  fenced, so a manual route may still start from a deactivated point (how a held train is driven
  out) and finish on one (how a parked-up berth is reached). The commit's comment argues exactly
  this, and `Automation.md` now states it for the user.
- **Staging is unaffected in the way that matters.** `HomeStaging` does not call `isPathClear` at
  all - it re-implements the rules against snapshot state, by design - so the new refusal cannot
  narrow a return-home plan. This is the same property that forced the reversing-station rule into
  `pickPath` (`RV-D1`), and it holds here for the opposite reason: the rule *should* apply to
  manual routes, and does.
- **The loop bounds are right.** `for (i = 0; i < path.size() - 1; i++)` over edge ENDs visits each
  intermediate exactly once and neither endpoint - the last edge's end is the destination, the
  first edge's start is the origin, and every other start is the previous end.
- **The test rewrite is honest.** The old test asserted the fence's *presence*; the new one asserts
  the refusal with autonomy stopped, states the precondition (`assertFalse(isAutoRunning())`), and
  keeps the running case as a second leg. The endpoint rules are pinned separately, so the
  asymmetry cannot be flattened by a later "simplification" without a red test.

## RS-D2: the staging repaint (`e4eeed8`)

One `repaintAutoLocList(false)` added at the end of the staging worker's `finally`. The comment's
claim - that the end-of-path callback fires from `executePathInternal` before the entry thread
calls `stopLocomotives()`, so the final repaint of a staging run sees `isAutoRunning()` still true
and hides the available-paths list until the operator starts and gracefully stops autonomy - is
consistent with the call sequence, and the placement is correct: inside the `finally`, after
`setStagingInProgress(false)` and `setTimetable`, so the repaint observes the settled state. `Full`
rather than `Lite` is right for the same reason the comment gives - the panels are rebuilt, not
just their labels. Off the EDT, matching the `repaintTimetable` above it.

## RS-D3: the home-locomotive "use current" button (`2cddfe9`)

`showInputDialog` becomes `showOptionDialog` with an explicit `JComboBox`, so a third button can
offer the locomotive already standing at the station - the common case when staging a layout.

- **Cancel and close are both handled.** `showOptionDialog` returns `CLOSED_OPTION` (-1) for the
  window close, and the `else` branch catches it alongside cancel; the subsequent `choice == null`
  guard is redundant but harmless.
- **The button's condition is the right one.** It appears whenever a locomotive is standing here,
  and the comment records that an earlier version also hid it when that locomotive was already the
  home - correctly rejected, because a control that comes and goes for invisible reasons is worse
  than one that occasionally does nothing.
- **The selector preselects the current assignment** (or `NONE`), preserving the old dialog's
  behaviour, and `NONE` still clears.

---

# Part 2 - release sweep

## RS-C1: the array-value split, the twin of an already-fixed one

`CS2File.parseFileContents` parses two line shapes. The nested "array" shape
(`  ..key=value`) splits on `=` with no limit:

```java
String[] parts = s.substring(3).split("=");
array.put(parts[0], parts[1]);
```

Thirty-eight lines below, the ordinary shape (` .key=value`) splits with a limit, and its comment
says why: *"Limit of 2, so that a route or locomotive name containing an equals sign is not
truncated."* The array branch is the same defect the same fix was written for, one shape over.

It is reachable with real data. The array shape carries `..lokname=<locomotive name>` inside a
`traktion` block - nine occurrences in `test/lokomotive.cs2` alone - and that name is what
`parseLocomotives` matches against the locomotive database to assemble a multi-unit. A locomotive
named `BR 50 = Ep.III` (TrainControl only refuses a *blank* name) is stored as `BR 50 `, matches
nothing, and is dropped from its multi-unit with a log line the operator will not be watching for.
The other array keys in the fixtures are numeric (`nr`, `typ`, `wert`, `magnetartikel`,
`stellung`, `dauer`, `kont`, `hi`, `lok`), so `lokname` is the only value that can carry the
character - which is why this survived: the shape is rare, and the one key that admits it is the
one nobody types twice.

C rather than B: it needs an unusual name, it degrades rather than corrupts (the member is absent,
not wrong), and it is recoverable by renaming. The fix is the same one-token change as its twin,
plus a fixture-shaped test - a `traktion` block whose `lokname` contains `=`, asserted to resolve.

## RS-C2: layout download trusts page names as filenames

`downloadCS2Layout` writes each page to `new File(layoutsDir, layoutName + ".cs2")`, where
`layoutName` comes from `parseLayoutList()` - the `name` field of the fetched `gleisbild.cs2`
index, unexamined. The remote URL is built with `sanitizeURL` for exactly this reason, but the
local write is not guarded at all: a page named `Yard / West` or `Level 1: Upper` produces an
invalid Windows path, and `Files.newBufferedWriter` throws out of `downloadCS2Layout` after the
master index and some pages have already been written - a partial local layout folder, with the
operator's next sync reading it as authoritative.

The `..` case is theoretical rather than adversarial (the index is the operator's own Central
Station), which is why this is C: the realistic failure is a page name with a slash or a colon,
not an attack. Sanitizing the leaf name - replacing anything outside a safe set, as the URL path
already does - plus deleting the partial folder on failure would close both halves.

## RS-D4: `CS2File`, otherwise clean

Read end to end, with the fixtures alongside. Everything else checked came back sound, and several
of the traps this sweep went looking for are already closed with the reasoning recorded at the
site: every reader is opened in try-with-resources (the CS3 three-reader block explicitly), UTF-8
is forced on both read and write with the mixed-charset defect written up where it was fixed,
per-record `catch` blocks stop one malformed locomotive or route from aborting an entire sync, the
sparse-function-list array sizing is correct and commented, three-way delay placement is held on
command objects rather than found by address search (the fix for a real defect, in both importers),
and unsupported layout components return null with the call site checking it. `parseMags` tolerates
missing `dectyp`; `describeCS3Field` degrades to `"?"` rather than throwing while reporting a throw.

## RS-D5: pattern sweeps over the unread GUI classes

- **Input parsing.** Every `Integer.parseInt` on user text in the GUI is inside a `try` that
  reports a friendly error - `AddLocomotive` (both entrances), `RouteEditor` (five sites),
  `GraphRightClickPointMenu`'s speed multiplier. The `GraphLocAssign` accessors that parse combo
  selections are fed from models the class builds itself.
- **EDT discipline.** The three `Thread.sleep` calls in GUI code are all inside spawned threads
  (`LayoutLabel`'s power-on wait, the CAN monitor, the route repainter), and each catch
  re-interrupts.
- **Hardcoded dialog strings.** One `JOptionPane.showMessageDialog(this, "Please first disable all
  automatic routes.")` in `AutoLocomotiveStatus` - inside a commented-out block, so not live. No
  other non-i18n dialog text in the GUI package.

## Coverage gaps, for the release notes rather than as findings

Thirty-odd GUI classes have no test that so much as names them - unsurprising for Swing code, and
consistent with what the July cycle recorded about the editor flows. Worth stating explicitly
before a release, in rough order of how much logic sits behind the UI: `LayoutEditor` and
`LayoutGrid`, `LocomotiveSelector`, `LocButtonTransferHandler` (drag-and-drop page moves),
`AutoJSONExport`, `LocomotiveFunctionAssign`, `AddLocomotive`, `HomeLocomotiveMenu` (touched by
`2cddfe9`), and the right-click menu family. Two model-layer paths are also untested and do write
to disk: `LayoutDiagram.saveChanges` / `exportToCS2TextFormat`, and `CS2File.downloadCS2Layout`
(where `RS-C2` lives). None of this is a defect; it is where a regression would go unnoticed, which
is what a release checklist wants to know.

---

## Disposition of `RS-C1` and `RS-C2` - 2026-08-02

Both fixed, both red first. Two things the findings did not say, and one recommendation declined.

**`RS-C1` is exactly the one-token change described**, and the sweep's scoping was checked rather
than assumed: the three other unlimited `split("=")` sites in the file (`:751`, `:918`, `:977`) all
parse numeric fields - `magnetartikel`, `stellung`, `nr` - so no third twin exists. The test feeds
`parseFile` a `traktion` block shaped like the one at `lokomotive.cs2:1548`, with a second, ordinary
member as a control so a fix that mangled the array shape in general could not pass.

**`RS-C2` needed sanitising on both sides, not just the write.** The finding names the write, but
`parseLayout` locates each page by the name in the index through the same `getLayoutURL` join, so
sanitising only the write would have produced a file the reader could then not find - trading a
loud failure for a silent one. `sanitizeFilename` is therefore applied to the local branch of
`getLayoutURL` as well; the remote branch keeps `sanitizeURL`, which answers a different question.
Only genuinely unusable characters are replaced, so ordinary names - spaces, dashes, accented
letters - are returned untouched and existing local layouts load exactly as before.

**The reproduction needed a separator rather than an illegal character**, and that is worth
recording because it looked at first like the defect was not testable end to end. A name like
`Yard / West` cannot be reproduced, because the SOURCE file would have to exist under that name -
the same constraint that makes the bug possible. A page named `Sub/Page` reproduces the identical
unguarded join with filenames that are legal on both sides: the fetch succeeds, and the write
resolves to a destination subdirectory that does not exist. The fixture carries the page under both
spellings so the test isolates the write path from the read-path change made alongside it.

**Declined: deleting the partial folder on failure.** The download target is any folder the operator
picks from a `JFileChooser`, and `downloadCS2Layout` writes into `<chosen>/config`. Re-downloading
into the folder used last time is the normal case, so deleting it on failure would destroy a working
layout to tidy up a failed copy - worse than the problem, and irreversible. The specific cause the
finding described is now gone, and a failed download does not become the active layout, because the
override preference is only written after `downloadLayout` returns. What remains is the general case
of a network failure part way through, and the safe shape for that is staging into a temporary
folder and moving into place on success - a larger change than this sweep, recorded here rather than
half-done.

---

## Standing items across the folder

- `RS-C1`, `RS-C2`: fixed, see the disposition above.
- The `UC` record note on the stranded-javadoc detector claimed in that document's disposition but
  absent from the repository.
- The parking-area activation described in `RV` - sixteen berths reachable by "return home" instead
  of two - an operational choice for the author, not a defect.
