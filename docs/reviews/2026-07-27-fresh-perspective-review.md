# Fresh-perspective review - 2026-07-27

**Prefix for citing this document: `FP`.**

**Version reviewed:** the v2.8.0 working tree after the `RR` fixes. **Reviewed:** 2026-07-27, while the
author tested the application manually.

**Scope:** angles the July 2026 cycle had not used. The six prior documents worked through correctness
of specific subsystems; this pass asked different questions instead - resource lifecycle, cost per
operation, unbounded growth, locale sensitivity, and what the *database keys actually are*. That last
question is what produced the only serious finding.

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| FP-B1 | Rename-on-import proposes one Central Station name for every local locomotive sharing an address; acting on the proposals deletes one of them | B | **Fixed 2026-07-27** |
| FP-C1 | `parseFile` closed its reader outside any `finally`, leaking it whenever parsing threw | C | **Fixed 2026-07-27** |
| FP-C2 | `getLocomotivesToRenameFromImport` rebuilt the whole locomotive list once per parsed locomotive | C | **Fixed 2026-07-27** (with FP-B1) |
| FP-B2 | `pickPath` discarded every exception and then reported "no free paths" - a normal condition - so autonomy failures were undiagnosable | B | **Fixed 2026-07-27** |
| FP-C3 | 100 sites allocate a `Thread` purely to pass it as a `Runnable`; five are per-CAN-message | C | Open - not attempted |
| FP-C4 | Accessory validation during autonomy JSON load is dead code: the validator is called and its exception discarded | C | Open - report only |
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

## FP-C4. Autonomy JSON validation that validates nothing - open

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

`validateConfigCommand` is a pure validator - it returns a setting and throws on invalid input, with no
side effect on the model. Its return value is discarded and its exception is swallowed, so the block
does nothing whatever the input. The sibling `else` branch, for a command with no `acc` key at all,
calls `layout.invalidate(...)`, which suggests the intent here was to invalidate on an unresolvable
accessory too.

**Not fixed, deliberately.** Making it invalidate would start rejecting autonomy files that load today,
and the runtime already handles missing accessories properly - a path whose accessory is absent is no
longer used, which was an A-level fix earlier in this cycle. So the safe change is to delete the dead
block rather than activate it, and that is a judgement about intent that belongs with the author.

---

## FP-C3. `new Thread(...)` used as a `Runnable` - open

100 sites across 13 files write `submit(new Thread(() -> ...))`, `invokeLater(new Thread(() -> ...))`
or `execute(new Thread(() -> ...))`. The executor or the EDT calls `run()` on the object, so the
lambda runs on the pool or event thread and the behaviour is correct - but a `java.lang.Thread` is
allocated and never started. Thread construction is not free: it walks the security manager, inherits
thread-locals, names itself and registers with a thread group.

Five of them are in `receiveMessage`, so one such object is allocated per incoming CAN message, of
which there are many during operation.

**Not attempted.** The change is mechanical - delete `new Thread(` and its closing paren - but it is
100 sites for a per-message saving that is probably microseconds, and this cycle has twice shown that
large mechanical edits are where files get damaged. If taken, the five in `receiveMessage` are the ones
worth doing, and they can be done alone.

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
  swallowed failure is a cosmetic operation. The two that were not - `FP-B2` - are fixed; one further
  case is `FP-C4`.

---

## Remaining, for a later pass

Nothing above is open except **FP-C3**. Two areas were noticed but not reviewed, and are recorded so
the gap is not mistaken for a clean bill:

- **Rendering cost for large diagrams.** `LayoutGrid` builds a component per tile, and the cache is
  invalidated wholesale (`layoutCache = new HashMap<>()`) whenever a repaint runs without `useCache`.
  How that scales on a large layout was not measured.
- **`pickPath` enumeration cost.** It was established during the July cycle that `pickPath` enumerates
  exhaustively, which is what makes the BFS ordering irrelevant. Its cost on a large graph, with many
  locomotives running, was never measured - only its correctness.

Both are performance questions that reading cannot settle. They need timing against a real layout.
