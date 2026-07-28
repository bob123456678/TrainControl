# Fresh-perspective review - 2026-07-27

**Prefix for citing this document: `FP`.**

**Version reviewed:** the v2.8.0 working tree after the `RR` fixes. **Reviewed:** 2026-07-27, while the
author tested the application manually. `FP-B3` and `FP-C6` were added later the same day, and did not
come from this pass at all - both surfaced while writing `testInvalidInput`, which is consistent with
the cycle's own finding that using and testing located more than reading did.

**Scope:** angles the July 2026 cycle had not used. The six prior documents worked through correctness
of specific subsystems; this pass asked different questions instead - resource lifecycle, cost per
operation, unbounded growth, locale sensitivity, and what the *database keys actually are*. That last
question is what produced `FP-B1`, the most serious finding this pass was looking for.

Four findings here are B-severity. `FP-B1` and `FP-B2` were found by the questions above; `FP-C4` was
raised from C to B once its cause was understood, keeping its original identifier; `FP-B3` came from
test-writing afterwards.

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| FP-B1 | Rename-on-import proposes one Central Station name for every local locomotive sharing an address; acting on the proposals deletes one of them | B | **Fixed 2026-07-27** |
| FP-C1 | `parseFile` closed its reader outside any `finally`, leaking it whenever parsing threw | C | **Fixed 2026-07-27** |
| FP-C2 | `getLocomotivesToRenameFromImport` rebuilt the whole locomotive list once per parsed locomotive | C | **Fixed 2026-07-27** (with FP-B1) |
| FP-B2 | `pickPath` discarded every exception and then reported "no free paths" - a normal condition - so autonomy failures were undiagnosable | B | **Fixed 2026-07-27** |
| FP-C3 | 100 sites allocate a `Thread` purely to pass it as a `Runnable`; five are per-CAN-message | C | **Fixed 2026-07-27** - all 100 |
| FP-C4 | Accessory creation during autonomy JSON load fails silently, so a config that can never be actuated loads as valid | B | **Fixed 2026-07-27** - now fatal at load. Severity raised from C once the cause was understood |
| FP-C5 | Four empty catch blocks in the layout editor and graph menu swallowed failures with no message | C | **Fixed 2026-07-27** |
| FP-B3 | `importRoutes` deletes every route first, so a file that parses but re-adds fewer leaves the difference destroyed | B | **Open - deferred by author 2026-07-27**, judged unlikely in practice |
| FP-C6 | `Layout.getLastError()` shows the last invalidation rather than the first, so a config with several mistakes reports the least useful one | C | **Open - deferred by author 2026-07-27** |
| FP-D1 | Checks that came back clean | - | Recorded |

---

## FP-B1. Rename-on-import can delete a locomotive the user did not choose to delete

**This finding was raised, wrongly dismissed, and then re-derived.** It is recorded that way on
purpose - the dismissal is the more instructive half.

When `getLocomotivesToRenameFromImport` was first read (during the `FCR` cycle), the concern was that
it emits one candidate per matching local locomotive, and the UI acts on every candidate in turn while
each rename *deletes whatever already holds the target name*. Two candidates sharing a target name
would therefore destroy one locomotive.

That was dismissed on the following reasoning: `RemoteDeviceCollection` keys by id, `getItems()`
returns one item per key, so two locomotives with the same UID cannot both be in the database, so two
candidates cannot share a target. **The premise was false.**

```java
public String getUID()
{
    return this.getName() + '_' + Integer.toString(UID);
}
```

The key is name *and* address, not address. Two locomotives at MM2 76 named `A` and `B` have keys
`A_16460` and `B_16460`, both stored, both returned by `getItems()`. `getIntUID()` returns the same
value for both, and that is what the import matcher compares. The author's own comment on
`getDuplicateLocAddresses` says as much: *"the same physical locomotive can be duplicated in the UI for
convenience, or left behind by a stale sync, and both entries then drive the same decoder."*

So with `A` and `B` at one address and the Central Station reporting that address as `C`:

1. candidates are `[{A→C}, {B→C}]`
2. rename `A`→`C`
3. rename `B`→`C` - which finds `C`, deletes it, and `C` *is* `A`

`A` is destroyed, with its function mappings, notes and images. The user does confirm a deletion
dialog, but it names `C` - a locomotive that did not exist until the previous dialog.

**Severity.** B rather than A because it needs duplicate addresses, and a confirmation is shown. It is
data loss, so A is defensible; the dialog naming a locomotive the user has never heard of is what makes
the confirmation worth little.

**Fixed.** Candidate generation now indexes the local database by `getIntUID()` once, and refuses to
propose anything for an address held by more than one locomotive, logging
`loc.renameAmbiguousDuplicateAddress`. The Central Station has one name for that address; proposing it
for each of several locomotives is incoherent, and choosing one arbitrarily would be worse than
declining.

**Tests.** `testImportRename.testDuplicateAddressProducesNoRenameProposal`.

**A consequence worth recording.** The same false premise was written into that test class's own
javadoc - "only one locomotive can hold a given UID... so each test installs its own and does not
depend on what any other test left behind". Because it is false, each test was leaving its locomotive
at the reference address, and the fix would have made two previously-passing tests fail: the shared
address is now, correctly, ambiguous. A `@BeforeMethod` cleanup replaced the assumption, and the
javadoc now states what the key really is. A wrong belief had propagated into the thing that was
supposed to check the belief.

---

## FP-C1. `parseFile` leaked its reader on the error path

`CS2File.parseFile` ended with `in.close(); return items;` - the close being the last statement rather
than a `finally`, so any parse failure left the reader open. `parseJSONArray` and `parseJSONObject`,
directly alongside, both use `try (BufferedReader reader = in)`.

The leak matters more than a stray HTTP connection suggests: with a local layout folder configured,
these readers are `file:///` streams, and layouts are re-parsed on every diagram edit now that
`FCR-B3` made that refresh routine.

**Fixed** by the same shape used for `executePath`: the body was renamed to `parseFileContents` and a
thin `parseFile` wrapper holds the try-with-resources, so the 94-line body needed no re-indentation.

*First read wrongly:* the initial scan covered 67 lines of the 94-line method, missed the trailing
`in.close()`, and nearly produced a report claiming every sync leaks readers unconditionally. Checking
the whole method before writing it up turned an A-shaped claim into a C.

---

## FP-C2. A full list copy per parsed locomotive

`getLocomotivesToRenameFromImport` called `this.locDB.getItems()` - which builds a fresh `LinkedList`
of the entire locomotive database - inside a loop over every parsed locomotive. Against the 154-entry
CS3 fixture and a 100-locomotive local database that is 154 copies and 15,400 comparisons, for work
that a single index makes O(n).

**Fixed with FP-B1**, which needed the index anyway.

---

## FP-B2. A failure in path selection was reported as "no free paths"

`Layout.pickPath` - the method the autonomy engine calls continuously, for every running locomotive, to
choose where a train goes next - wrapped its path enumeration in `catch (Exception e) { }`. Completely
empty: no log, no rethrow, no comment.

Execution then falls through to:

```java
this.control.logf("autolayout.infoLocomotiveNoFreePaths", loc.getName());
loc.delay(minDelay, maxDelay);
return null;
```

So *any* failure inside path selection was reported to the operator as "no free paths" - a normal,
expected condition that happens whenever the layout is busy - with the exception discarded entirely.
`runLocomotive` then retries forever. A locomotive that never moves, with a benign message repeating in
the log and no other trace, is the whole diagnostic picture.

This is the same shape as `RR-C5`, where any monitor failure was logged as "condition not satisfied",
but worse in one respect: there the exception was still logged on the next line. Here nothing was.

Given how much of this cycle was spent diagnosing autonomy behaviour - `IND-T3`, `IND-D5`, `INT-A2` -
it is worth noting that a real fault in the middle of it would have produced no evidence at all.

**Fixed.** Both empty catches - `pickPath` and `debugPath`, which share the enumeration shape - now log
`autolayout.errorPathSelectionFailed` and the exception. The control flow is unchanged: the method still
returns null and the caller still retries. Only the silence is gone.

**Severity.** B. Nothing is corrupted and no train misbehaves; the cost is that a class of failure
cannot be diagnosed at all, and presents as normal operation.

---

## FP-C4. Accessory creation that fails silently - and a finding that was wrong about why

`Layout.fromJSON`, in the edge-command validation loop:

```java
if (null == control.getAccessoryByName(accessory))
{
    try
    {
        Edge.validateConfigCommand(accessory, Accessory.accessorySetting.GREEN.toString(), control);
    }
    catch (Exception e)
    {

    }
}
```

**The finding above was wrong, and the author's instinct was right.** It was written on the premise
that `validateConfigCommand` is a pure validator with no side effect on the model, so that discarding
both its return value and its exception left a block that does nothing. The premise is false. The
method's own first line of javadoc says otherwise - *"Validates that a command is valid.Creates
accessories in DB if needed."* - and its body calls `control.newSignal(...)` or `control.newSwitch(...)`
for an accessory that does not yet exist. The call is there **for** that side effect. The author said as
much when the finding was raised.

So the block is not dead. What was actually wrong with it is narrower and still worth fixing: when
creation fails, nothing was said.

**Fixed by making it fatal.** The failure now throws, and the enclosing edge handler converts it into
`autolayout.errorInvalidEdgeWithMessage` wrapping `autolayout.errorEdgeAccessoryCouldNotBeAdded` - so
the config is rejected at load, naming the accessory and the reason.

Two intermediate wordings were written and discarded before this, and the sequence is the point:

1. *"Paths using this edge will not be available"* - accurate about the runtime, but it described a
   consequence rather than a fault, implying the operator should expect degraded operation.
2. *"ignoring invalid accessory"* - which would have been a false statement. The command is stored on
   the edge a few lines below regardless, via `addConfigCommand`, whose own javadoc says it *expects* a
   successful `validateConfigCommand` first. Nothing ignores anything.

Writing the message is what forced the question of what the code actually does, and the answer settled
it: `configureEdge` already treats an unresolvable accessory as fatal, refusing the path during preview
and invalidating the layout during actuation. Failing at load is the same verdict delivered where the
cause is still visible, instead of several steps downstream.

**This rejects configurations that used to load.** That is the intended change, not a side effect: such
a configuration was never operable, it merely failed later and less clearly. The alternative - dropping
the command and running the path anyway - would send a train across an accessory whose position was
never commanded, which on a real layout is a switch left wherever it happened to be.

**Tests.** `testInvalidInput.testUnusableEdgesAreRejected`, case *"command naming an accessory that
cannot be added"* - which loaded as **valid** before this fix.

*Recorded as a reviewer error.* Two of this cycle's earlier mistakes were "believing a method does what
its name suggests"; this one is the same family but worse, because the method's documentation stated
the behaviour plainly in its first sentence and the finding contradicted it anyway. Reading a method's
body without its javadoc is how a side effect becomes invisible.

---

## FP-C5. Silent catches in the editor UI

Four `catch` blocks with an entirely empty body: three in `LayoutEditor` around
`layout.addComponent(...)` - placing, rotating and editing a tile - and one in
`GraphRightClickPointMenu`. A failed tile edit simply did not appear, with nothing said anywhere.

`LayoutEditor` already logs via `this.parent.getModel().log(ex)` in three other places, so these were
inconsistent rather than deliberate.

**Fixed** - all four now log the exception. Logged rather than shown in a dialog on purpose: the editor
calls `addComponent` per placement, and a dialog per failed tile would be worse than the silence.

**Deliberately not changed:** `Util.openURL`'s two `catch (...) {return false;}` blocks, which an
automated scan flagged alongside these. Returning false *is* the handling - the caller decides what to
tell the user - and they were flagged only because the body sits on the same line as the `catch`.

The wider count: 252 catch blocks in `src/`, of which 9 were empty. Two were `FP-B2`, four are these,
one was `FP-C4`, and two were those false positives. All but the two are now fixed.

---

## FP-C3. `new Thread(...)` used as a `Runnable`

100 sites across 13 files write `submit(new Thread(() -> ...))`, `invokeLater(new Thread(() -> ...))`
or `execute(new Thread(() -> ...))`. The executor or the EDT calls `run()` on the object, so the
lambda runs on the pool or event thread and the behaviour is correct - but a `java.lang.Thread` is
allocated and never started. Thread construction is not free: it walks the security manager, inherits
thread-locals, names itself and registers with a thread group.

Five of them are in `receiveMessage`, so one such object is allocated per incoming CAN message, of
which there are many during operation.

**Fixed - all 100 sites**, at the author's request.

*The performance argument is weak and should not be the reason.* A `Thread` object costs perhaps a
microsecond; on the five per-message sites that is invisible against the network I/O around them, and
on the other 95 - one-off user actions - it is meaningless. The real argument is that
`submit(new Thread(...))` *reads* as though a thread is being spawned inside an executor. It is not:
`Thread` implements `Runnable`, so the pool simply calls `run()` on it. A reader who believed the code
meant what it appears to mean would be wrong about the concurrency - a correctness hazard, even though
the code behaves correctly.

*Why it was safe to do mechanically.* The transformation was scripted with a paren matcher that skips
string literals and comments, and every file was verified **before** being written: brace counts
unchanged, paren counts down by exactly the number of transformations, method set identical.
`new Thread(` occurrences went from 169 to 69.

**That 69 was described here as the independently pre-counted number of genuine, started thread
creations. It was not - it was a count of `new Thread(` occurrences, and the property was asserted
rather than checked.** One of the 69 was an unstarted Thread the matcher could not see, because
`invokeLater(` and `new Thread(` sat on separate lines and it fired only on immediate adjacency. Found
later as `PV-C1`, fixed, and the count is now 68. Recorded because the arithmetic was doing the work of
a verification and could not have caught this: a site that is neither transformed nor counted as a
candidate is absorbed silently into the residue.

---

## FP-D1. Checks that came back clean

- **Locale-sensitive case folding.** 28 `toLowerCase()`/`toUpperCase()` calls, none with an explicit
  `Locale`. Every comparison lowercases *both* sides, so the Turkish dotless-i trap does not bite -
  `"STRAIGHT".toLowerCase()` and `setting.toLowerCase()` transform identically. The one
  `valueOf(type.toUpperCase())` is over `accessoryDecoderType`, whose constants contain no `i`.
- **The feedback hot path.** `receiveMessage`'s feedback branch is two hash lookups, and
  `MarklinFeedback.updateTiles` iterates only the tiles registered to that sensor, pruning invisible
  ones as it goes. No full scans per message.
- **`saveState`'s four loops** over the locomotive, accessory, route and feedback databases are
  sequential, not nested - an automated scan flagged them as nested and reading disproved it.
- **Popup window lifecycle.** `popups` is iterated with a `ListIterator` in two places that prune
  closed windows, so it is not an unbounded collection.
- **`layoutCache`** is keyed by page name and size, so it is bounded by the number of pages times the
  number of display sizes.
- **The mutable-hash-key problem does not generalise to `Point` or `Edge`.** Both hash on their name,
  and point names are mutable (`Layout.renamePoint`), so the shape is identical to the standing item -
  but `renamePoint` re-keys `points` and `adjacency`, re-inserts every edge under its new name, and
  then prunes stale keys by comparing each map key against its value's current name. `Edge.lockEdges`
  is a `LinkedList`, so its `remove` uses `equals` and does not care about hash drift. Checked
  precisely because this is the defect class the cycle kept finding elsewhere.
- **Silent catch blocks.** 252 catch blocks; 9 completely empty. Seven are in UI code where the
  swallowed failure is a cosmetic operation. The two that were not - `FP-B2` - are fixed, as is the
  remaining case, `FP-C4`. No empty catch block is left in `src/` outside UI cosmetics.

---

## Validation of this cycle's changes

Re-validated after the `FP-C3` unwrap, which touched 100 sites across 13 files - by far the largest
mechanical edit of the cycle. A scripted check covers every changed Java file and the bundles:

- quote parity per line, brace balance, and brace delta against `HEAD`
- no method present at `HEAD` missing now
- eight bundles, equal key sets, no duplicates, ASCII-only
- **every message key referenced from `src/` exists in the bundles** - `I18n.t`/`f` call
  `bundle.getString` directly, so a mistyped key is a `MissingResourceException` at runtime, not a
  fallback
- every test class registered in `build.xml`
- no references to the symbols deleted this cycle

All passed. The key-existence check produced one false positive worth recording: two keys appeared to
be used but absent, and both turned out to be usage examples in `I18n`'s own javadoc. The checker now
strips comments.

The unwrap itself was verified beyond the structural checks: every one of the 100 transformed sites was
a lambda (two used `()->` without a space, which an initial grep missed), and `new Thread(` occurrences
fell from 169 to 69.

The residual 69 was reported as started threads. **It was not verified to be** - see the correction
under `FP-C3`. One was not started, and the same near-miss shape the note above congratulates itself on
catching (lambda spacing) recurred as a newline instead. The lesson generalises past this fix: a
mechanical matcher and the count used to check it share a blind spot, because the count is over the
same pattern the matcher keys on.

---

## New coverage added alongside this review

`testAdvancedRoutes` - six tests for two combinations the suite had never exercised together, and which
the author's manual testing also did not reach:

- **An autoloc condition driving a function command.** "Fire function 3 when this locomotive reaches
  this sensor." `testRoutes` covers autoloc conditions through `Route.evaluate` in isolation, and
  covers function commands nowhere. The pair is tested through a real s88 trigger, including the
  negative case where the locomotive is on the graph but standing at a *different* sensor - without
  which a route that fired merely because the locomotive existed would pass.
- **Multi-units reached through a route.** `setF` and `setSpeed` fan out over `linkedLocomotives`, so a
  route naming a consist head commands every member. Nothing asserted that, and this cycle rebuilt that
  fan-out map three times (`INT-A1`, `INT-A2`, `RR-C1`). Also pinned: an autoloc condition resolves
  against the head only - a member is not separately on the graph - and a head and its own member are
  never simultaneously compatible.

`testInvalidInput` - eleven tests for input the user entered incorrectly. The suite covered one such
surface well (`RouteCommand.fromLine`, eight malformed command lines) and three not at all:

- **The autonomy configuration file.** `Layout.fromJSON` has around forty distinct rejection paths and
  not one was asserted - no test in the suite ever called `isValid()`. It is also the only input the
  user hand-edits as text, and the only parser here that *never throws*: it returns an invalidated
  `Layout` either way, so a lost check would not fail loudly, it would quietly load a broken layout.
  Covered: malformed JSON, each required key removed in turn, quoted numbers, non-numeric settings,
  unusable points, unknown and duplicated locomotives, and edges naming points that do not exist.
- **`Edge.validateConfigCommand`** - the accessory command box in the graph edge editor, previously
  called only with valid input. Six malformed commands, each asserted to produce a *checked* exception
  with a message, plus the property that a rejected command leaves no half-created accessory behind.
- **Route file import.** `importRoutes` deletes every existing route before adding the parsed ones;
  that is safe only because parsing happens first, in a separate statement. Nothing pinned the
  ordering, and the comment describing it sits *below* the deletion. Three shapes of bad file, each
  failing at a different depth, with the route count asserted unchanged after each.

Each group carries a control case asserting valid input is still accepted. Without them a validator
that rejected everything would satisfy every other assertion in the file.

**The accessory tests failed first time, correctly.** They picked accessory addresses believed to be
unused and asserted one was absent; the author's database had a switch at 291. The assertion was
written as a stated precondition rather than a bare `assertNull`, so the failure said exactly what was
wrong instead of looking like a defect in the code under test. `testAccessory` had already solved this
and its helper says why - *a real database is not clean; the keyboard registers an accessory at every
address the operator has ever scrolled past* - which was read past when that helper's call sites were
checked but its javadoc was not. Both accessory tests now empty the address first, in memory only.

**A checker error worth recording.** Both new test files initially failed the structural validator -
unbalanced braces and quotes - and both reports were wrong. The validator counted braces and quotes on
raw source, so a deliberately truncated JSON fixture (`"{'points': ["`) read as an unclosed brace, and
the char literal `'"'` read as an unbalanced quote. It now strips comments and literals with a state
machine before counting, and was self-tested against eight cases - four benign shapes it must not flag,
and three real defects it must still catch - because a checker relaxed to stop crying wolf is worth
less than no checker at all. It also gained a paren-balance check, which the old line-based approach
could not support.

---

## FP-B3. A route import that parses can still destroy routes

`importRoutes` parses the whole file, then deletes every existing route, then adds the parsed ones.
The parse-first ordering is what makes an unreadable file safe, and `testInvalidInput` now pins it.

It does not make a *readable* file safe. `newRoute` refuses a route whose id or name is already taken:

```java
if (!this.routeDB.hasId(r.getId()) && !this.routeDB.hasName(r.getName().trim()))
{
    this.routeDB.add(r, r.getName().trim(), r.getId());
    return true;
}
else
{
    this.logf("route.alreadyImportedSkipping", r.getId(), r.getName().trim());
    return false;
}
```

So a file containing two routes that share an id or a name imports as: everything deleted, one of the
pair added, the other announced only in the log. The user chose a file and silently has fewer routes
than either the file or the database held.

**Where such a file comes from** is the reason this is not higher. TrainControl's own export cannot
produce one - `routeDB` is keyed by both id and name, so duplicates cannot exist to be exported. It
takes a hand-edited or hand-merged file. That is also exactly the file someone is most likely to import.

**Severity B, not C:** it is data loss with no dialog. Rated the same as `FP-B1` for the same reason -
real destruction, unusual precondition, and a user interface that does not say what happened.

**Deferred by the author**, 2026-07-27, as unlikely to be hit in practice. Recorded so that a later
change to `newRoute` or to the import flow is made knowing the delete already ran.

---

## FP-C6. The error the user is shown is the last one, not the first

`Layout.invalidate(String)` overwrites a static field on every call:

```java
public void invalidate(String message)
{
    this.isValid = false;
    Layout.lastError = message;
    this.control.log(message);
}
```

The point loop keeps parsing after invalidating - `points.forEach` continues to the next point rather
than returning - so a configuration with three mistakes calls this three times, and
`TrainControlUI` puts `Layout.getLastError()` in front of the user. The user sees the third.

**Nothing is lost:** `control.log(message)` runs on every call, so all of them are in the log. This is
which one gets promoted to the dialog. It matters because the first failure in a hand-edited file is
usually the cause and the rest are consequences, and because the dialog is what most users will read
instead of the log.

**Deferred by the author**, 2026-07-27.

---

## Remaining, for a later pass

**FP-B3** and **FP-C6** are open by the author's decision - recorded, not fixed. Beyond them, two areas
were noticed but not reviewed, and are recorded so the gap is not mistaken for a clean bill:

- **Rendering cost for large diagrams.** `LayoutGrid` builds a component per tile, and the cache is
  invalidated wholesale (`layoutCache = new HashMap<>()`) whenever a repaint runs without `useCache`.
  How that scales on a large layout was not measured.
- **`pickPath` enumeration cost.** It was established during the July cycle that `pickPath` enumerates
  exhaustively, which is what makes the BFS ordering irrelevant. Its cost on a large graph, with many
  locomotives running, was never measured - only its correctness.

Both are performance questions that reading cannot settle. They need timing against a real layout.
