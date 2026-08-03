# Final pre-release pass: new questions against an old tree - 2026-08-02

**Prefix for citing this document: `FV`.**

**Version reviewed: `ae7c371` (HEAD), working tree clean, on 2026-08-02.** This pass deliberately
did not repeat the cycle's correctness sweeps. Following the July `FP` review's example - which
found its best defect by asking what the database keys actually are - it asked four questions no
round in this cycle had asked: what happens to persisted state when the process dies at the wrong
moment; what grows without bound across a days-long operating session; what breaks in a
non-English locale; and what the exit path actually runs. Verification static, per standing
practice.

Findings use the A/B/C/D convention in [README.md](README.md). One B, one C, three D.

| ID | Finding | Status |
|---|---|---|
| FV-B1 | Every exit-time persistence writer truncates its target before writing - `trains.dat`, the UI state file, and `autonomy.json` - so a crash or power loss during the exit save silently corrupts the file, and the next launch falls back to an empty default with only a log line.  No automatic backup exists; the backup path is a manual menu item | Fixed - `Util.writeAtomically` stages and moves, used by all three writers; pinned by `testAtomicWrite` (5 tests).  The startup-backup option is declined - see the disposition |
| FV-C1 | The UI log inserts every message at position 0 of an uncapped `JTextArea` on the EDT - O(document) per message, unbounded memory, so a long session with logging activity degrades the whole UI progressively | Fixed - the document is trimmed to `DEBUG_LOG_MAX_CHARS` after each insert, preserving newest-first |
| FV-D1 | Locale sensitivity audit (the Turkish-i class) | Clean |
| FV-D2 | Unbounded-growth audit of long-session collections and thread creation | Clean, except FV-C1 |
| FV-D3 | Exit-path ordering: saves complete before dispose and `System.exit` | Clean |

---

## FV-B1: the exit save is the only save, and none of its writers are atomic

Three facts compose, each harmless alone:

1. **The only automatic save of session state is the one in the window-close handler**
   (TrainControlUI.java:9807-9811): `model.saveState(false)` writes `trains.dat` - every
   locomotive's function mappings, preferred speeds, icons, notes, multi-units and operating
   history - then `this.saveState(false)` writes the UI state file and, with autosave on (the
   default), `autonomy.json`. Nothing saves periodically during a session.
2. **All three writers truncate the target in place.** `trains.dat` and the UI state file go
   through `new ObjectOutputStream(new FileOutputStream(path))` (MarklinControlStation.java:1294,
   TrainControlUI.java:1115); `autonomy.json` through `Files.write` (TrainControlUI.java:1179).
   From the first byte written until the last is flushed, the only copy of the data is incomplete.
3. **An unreadable `trains.dat` is treated as first launch.** `restoreState` catches the
   `IOException`, logs `databaseInitDefault`, and continues with an empty database
   (MarklinControlStation.java:1386-1399). No dialog. And the loss is then *masked*: the next
   Central Station sync repopulates the locomotive list, so the operator sees their locomotives -
   with every customization, preference and statistic silently gone - and nothing points at the
   truncated file as the reason.

So: kill the process - power loss, OS shutdown timeout, a hang harvested by the user - inside the
exit save's write window, and the database is not merely stale but destroyed, discovered (if at
all) as mysteriously reset preferences. The `tc_backup` folder cannot be assumed to help:
`saveState(true)` is reachable only from the Backup Data menu item, so backups exist exactly for
users who have pressed it.

**Severity.** The consequence class is A - data silently lost - but the trigger requires abnormal
termination inside a sub-second window, which no operator action produces. B, with the A-shape
named so the judgment is inspectable. What tips it to worth-fixing-before-release rather than
recording: the fix pattern already exists in this codebase, twice - `Util.downloadFile` writes to
`.part` and moves into place precisely so "an interrupted download never looks like a finished
one", and the `RS-C2` disposition declined a folder cleanup in favour of naming stage-and-move as
the right shape. This finding is that named shape, applied to the three writers that matter most.

**Fix shape**: one helper - write to `<target>.part` (or a temp sibling), flush, close,
`Files.move` with `REPLACE_EXISTING` - used by all three writers, exactly as `downloadFile` does.
`ATOMIC_MOVE` where the filesystem supports it is a refinement, not a requirement; the rename
window is nanoseconds against the write window's milliseconds-to-seconds. **Red test shape**,
since a crash cannot be staged in a unit test: serialize a list containing an object whose
`writeObject` throws mid-stream, and assert the previous file's contents survive. Against the
current writers that test is red - the target is already truncated when the exception fires -
and green with the temp-and-move helper, for the precise reason the helper exists. A second test
pins the same property for `autonomy.json` with a write to a full or forbidden path.

An automatic backup - copying the current `trains.dat` into `tc_backup` once per startup, before
anything writes - would be belt and braces on top, and turns the manual-only backup gap into at
most a one-session loss. Recorded as the option; the atomic write is the fix.

## FV-C1: the log that grows forever, from the front

`TrainControlUI.log` runs `debugArea.insert(message + "\n", 0)` on the EDT with no cap
(TrainControlUI.java:1381-1388). Two compounding costs: the document grows without bound for the
life of the session - with debug on, every CAN message appends - and *front* insertion shifts the
entire accumulated document each time, so the per-message cost rises linearly with session age, on
the thread that paints everything. A weekend operating session with a chatty layout degrades the
whole UI, and nothing names the log as the cause. C: no wrong behaviour, recoverable by restart,
but it is the one finding of the growth audit and the shape users report as "it gets slow after a
while". The fix is a cap - trim the document's tail beyond N characters after each insert - which
preserves the newest-first presentation the front-insert exists for.

## FV-D1: locale audit - clean

The Turkish-i class was checked across every case-transformed comparison on the parse paths:
`stringToAccessorySetting`, `stringToAccessoryType` and the route-prefix checks lower-case *both*
sides, so a locale-altered dotted-i alters both identically; the direction, protocol and decoder
literals (`forward`, `backward`, `mm2`, `dcc`, `mfx`, `weiche`) contain no i/I, so
`toUpperCase()`-into-`valueOf` cannot be bent by locale; and enum round trips through JSON store
`name()` verbatim with no transform. No finding.

## FV-D2: growth audit - clean, except the log

Everything else that accumulates is bounded or pruned: the per-sensor simulation epoch map is
bounded by the sensor count and dies with its `Layout`; tile sets prune invisible entries on every
update; `historicalOperatingTime` is keyed by day; timetable capture is bounded by operator
activity and skips replay; the per-event threads (route execution, simulation clears, repaints)
are short-lived and bounded by activity. `Layout.layoutVersion` and `Point`'s static id counter
are ints that increment per construction - beyond any realistic session.

## FV-D3: exit path - clean

The close handler confirms when autonomy is running, then saves model state, saves UI state, then
`dispose()` and `System.exit(0)`. The ordering is right - both saves complete before anything is
torn down - and `System.exit` makes thread accounting moot. The one refinement `FV-B1` implies:
with atomic writers, this path needs no other change.

---

## Disposition of `FV-B1` and `FV-C1` - 2026-08-02

Both fixed, in the shapes the findings named.

**`FV-B1`.** `Util.writeAtomically(File, StreamWriter)` stages into a `.part` sibling and
`Files.move`s it into place, deleting the staging file if the write throws - generalising what
`downloadFile` already did inline, and reusing its `PARTIAL_DOWNLOAD_SUFFIX` so there is one
convention rather than two. All three writers go through it: `trains.dat`
(`MarklinControlStation.saveState`), the UI state file and `autonomy.json` (both in
`TrainControlUI.saveState`). The inner try-with-resources on each `ObjectOutputStream` is kept and
still matters for the reason its original comment gives - without `close()` the last buffered block
never reaches the staging file, and a truncated file would then be moved into place, which is worse
than the disease.

`testAtomicWrite` pins it with five tests. Three provoke the failure window the way the finding
proposed - a stream write that throws after 64KB, an `ObjectOutputStream` whose list contains an
unserializable member, and an immediate throw - and assert the previous contents are still there.
The other two are the guards that stop the first three from passing vacuously: a completed write
must actually replace the file, and a first-run write to a file that does not exist yet must
create it. A helper that quietly did nothing would satisfy the failure tests and fail those.

**Declined: the once-per-startup automatic backup.** It was offered as belt and braces, and it
changes a different thing than it appears to. `trains.dat` is copied into `tc_backup` at launch,
which means the newest backup is always the state at the last successful start - so the operator
who loses work between two sessions gets a file that looks current and is not. Worse, it makes
`tc_backup` grow without operator action, and the review immediately above this one closed a
finding about unbounded growth. The atomic write removes the failure it was insuring against; the
manual Backup Data item remains for the case it does not cover, which is the operator wanting a
snapshot they chose the moment of.

**`FV-C1`.** The debug document is trimmed to `DEBUG_LOG_MAX_CHARS` (100,000 - roughly a thousand
lines) after each insert. Newest-first is preserved, which is the whole point of the front
insertion, and the cost of an insert stops rising with session age because the document no longer
does. No test: the behaviour is a `JTextArea` document mutation on the EDT, and the existing suite
has no Swing harness to hang one on - stated here rather than left to be inferred from its absence.

---

## Release verdict, revising `RS`'s

`RS` closed its round with "no document in this folder carries an open code finding", which was
true when written. This pass opens two. `FV-C1` is quality-of-life and can ship. `FV-B1` is the
author's call to make with eyes open: the trigger is rare, but the blast radius is the user's
entire accumulated database, the failure is silent, the recovery path (manual backups) cannot be
assumed to exist, and the fix is a pattern this codebase has already written twice and named once
as the right shape. Everything else across UC, SF, CP, RV, RS and this document is fixed,
withdrawn, declined with reasoning, or informational - plus the two standing non-code items (the
`UC` detector record note, the `RV` parking-area activation).
