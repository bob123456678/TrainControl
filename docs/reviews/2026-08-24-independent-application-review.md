# Independent application review — 2026-08-24

**Status:** open

**Prefix:** `IAR` — cite findings from here as `IAR-A1`, `IAR-B2`, and so on. Taken elsewhere and not
reused here: `DD`, `FR`, `FV`, `GC`, `IR`, `ISD`, `LT`, `AR`, `FBR`, `FSR`.

**What was reviewed, and when.** The application as it stands at `f1befe72` ("The second Fable round"),
branch `autonomy-diagram-r0`, v3.0.0 Beta, read on 2026-08-24. Weighted towards the last week's work,
but not organised around it: no list of claims, no prior findings to confirm, no fixes to validate.

**What I chose to look at, and why.**

The brief's ordering is silent data loss first, wrong movement second, concurrency third. The three
things that decided where I went:

1. **The writers, not the readers.** Everything that writes `setup.json`, `configuration-*.json`,
   `UIState.data`, `LocDB.data`, `gleisbild.cs2` or a page file — and in particular everything that
   *decides which page a setting belongs to*. That is the mechanism behind both of the losses Adam has
   already suffered, and it is where the first finding is.
2. **The parameter that stopped meaning anything.** `FR-015` changed the Backup Data menu item from
   `saveState(true)` to `saveState(false)`. That is a one-word change to a call whose callee branches
   on the word seven times. Nothing else in the tree passes `true` any more, so every `if (!backup …)`
   guard in both `saveState` implementations is now vacuously true — including one whose comment says
   in as many words what it is protecting against. That is the second finding.
3. **Where a claim is written down and can be tested.** `FR-018` and `testPageIdsAreDurable` both
   state, in prose, the invariant that makes the page-id scheme safe. I tried to break it by running,
   not by reading.

**What I deliberately did not look at.** The CS2/CS3 file parsers beyond `parseLayout`'s page loop
(`IP` covered them); `HomeStaging` (`FBR`/`FSR` covered it two commits ago and mutated it to check);
`GraphReducer`, `TileGraph`, `TilePorts`, `StationIndex` (`UR` read them in full); `TileAnnotation`'s
painting; `NodeExpression`; the route editor's tables; the CAN message handlers; the whole `test/`
tree except the four classes named below. The dispatcher (`configureAndLockPath`, `unlockPath`,
`validatePathActuation`) was read but is recorded under D rather than attacked further — it has been
worked over hard and I found nothing to add.

**Method.** Read-only on `src/`, `test/` and `docs/`; **no source or test file was edited**. This
document is the only file created in the repository. Two things were run, both from the scratchpad and
neither added to the tree:

- `ProbeIAR1`, a scratch class compiled outside the repository against the built classes, which drives
  `LayoutDiagram.writeLayoutIndex` and `AutonomyCompanionStore` through the page-absence sequence in
  `IAR-A1`. Its output is quoted verbatim below.
- `regression.testPageIdsAreDurable` singly — 8 tests, 8 green at `f1befe72`. That matters: the suite
  that exists to hold this exact invariant passes while `IAR-A1` is live, and D1 says why.

Every finding says how it was verified. Where a claim rests on reading alone, it says so.

---

## A — high. Wrong behaviour on the layout, or data silently lost.

| | Finding | Disposition |
|---|---|---|
| **IAR-A1** | A page whose file is merely absent has its id retired, and the next new page inherits its entire setup | **Open** |
| **IAR-A2** | "Backup Data" now commits the session — writing the open editor's unsaved edits to disk, from a worker thread | **Open** |

### IAR-A1 — a new page collects an absent page's stations, names, lengths and exclusion

**Where.** `src/org/traincontrol/base/LayoutDiagram.java:948-971` (the id allocator inside
`writeLayoutIndex`), against `src/org/traincontrol/automationui/AutonomyCompanionStore.java:3239-3281`
(`withoutAbsentPages`) and `:3466-3488` (the renumber detection in `readShared`). Reached from all
three index writers: `TrainControlUI.java:18338` (combine), `:18702` (add/duplicate/rename) and
`:18840` (delete).

**What is wrong.** The id allocator is

```java
Map<String, Integer> existing = readLayoutIndexIds(path);
int next = 1;
for (Integer taken : existing.values()) { if (taken != null && taken >= next) next = taken + 1; }
```

`existing` is read from `gleisbild.cs2` as it stands. A page that is not in `layoutList` is simply
dropped from the file — its id is "retired". But the retirement is not *recorded* anywhere: on the
next write, `existing` no longer holds that number, so if the retired id was the highest one, `next`
starts at it again and the next page created gets it.

For a page that was genuinely **deleted** that is safe, and `testPageIdsAreDurable` says exactly why:
`deletePage` forgets the page's settings before its number becomes available again. For a page that is
merely **absent** there is no such call. `CS2File.parseLayout:2230-2241` skips a page whose file will
not parse or is not there — "a page the index names and the folder does not hold" — deliberately and
quietly, and on a layout living in OneDrive an unhydrated placeholder is enough. `getLayoutList()`
therefore does not contain it, every index writer drops it, and its settings stay in `setup.json`
under the old id, *held* (`heldForAbsentPages`, OB-067).

When a later page takes that id, the entries stop being held: `withoutAbsentPages` keeps any entry
whose page part is in `pageIdToName`, and it now is. `untranslate` then resolves the id to the **new**
page's name. Nothing warns, because the renumber test at `:3484` asks whether the old name still
exists in the index — and after the drop it does not, which is indistinguishable from a rename.

**How I verified it.** `ProbeIAR1`, compiled outside the repository against the built classes and run
against a temporary layout folder. Verbatim output:

```
initial ids: {Alpha=1, Ghost=2}
after Ghost dropped: {Alpha2=1}
after adding Zulu: {Alpha2=1, Zulu=2}

Zulu 3,3 point name  = Ghost Platform
Zulu 3,3 is station  = true
Zulu 3,3 tile length = 42
Alpha2 4,4 name      = Alpha Platform
conflicts reported   = false {}
excluded pages       = [Zulu]

Ghost returns, ids: {Alpha2=1, Ghost=3, Zulu=2}
Ghost 3,3 point name = null
Zulu  3,3 point name = Ghost Platform
excluded pages       = [Zulu]
```

The sequence is four ordinary gestures: Ghost's file is absent at start-up; a page is renamed (any
index write that does not itself allocate a number above Ghost's is enough — a rename or a delete);
a save happens while Ghost is away, which is what every session does; a new page is added.

Note the last three lines. Once Zulu has saved, `sharedFields` writes `"pages": {"1":"Alpha2",
"2":"Zulu"}` — Ghost's name is gone from the file, so the record that those settings were ever Ghost's
is destroyed. When Ghost's file comes back it is page 3 with nothing on it, and its old setup belongs
to Zulu permanently.

**Why it matters on a real railway.** Square 3,3 of a brand-new page is now a station, 42 cm long,
carrying a platform name the operator never typed, and the new page starts out excluded from autonomy
because Ghost was. Autonomy will route trains to a platform that does not exist, and the operator's
first sight of it is a train sent somewhere there is no track. This is the same class as MT-135 and the
23 August loss — a setting silently reattached to the wrong page — arriving through absence rather than
through a rename.

**What the record currently says, and why it is wrong.** Three places state the opposite:

- `FR-018` (issues.md): "Its settings are safe … but they no longer attach to anything … **Nothing is
  lost and nothing is found**", and it ranks eager retirement as the *safer* of the two errors.
- `testPageIdsAreDurable.testAPageNamedAfterAnotherPagesIdCollectsNothing`, in its closing comment:
  "writeLayoutIndex retires the id of a page that is not in the list … That is the id system working
  as designed — **it is what stops a later page inheriting them**."
- `AutonomyCompanionStore.deletePage`'s javadoc: "Ids are durable now … so the inheriting-page half of
  that is gone."

All three are true for a deleted page and false for an absent one. FR-018's risk ranking is inverted
by this: retiring eagerly does not merely orphan the settings, it hands them away.

**What I would do.** The smallest fix that closes it is to stop reissuing at all: carry the high-water
mark in the index rather than deriving it from the surviving rows — one extra line in `gleisbild.cs2`
(`.nextId=`), read in `readLayoutIndexIds`, and `next = max(highWater, max(existing)+1)`. That removes
the reuse for the deleted case as well, which is currently protected only by `deletePage` being
called, and it does not require the index to distinguish "deleted" from "unreadable" — which is what
FR-018 says is expensive. FR-018's option 2 (warn before writing an index while a page the setup knows
about is not loaded) is worth having on top, but on its own it leaves the door open, because the
sequence begins with a save the user did not initiate.

The regression test should be the probe above, with the precondition asserted (`ids().get("Zulu") == 2`
— otherwise it tests nothing) in the style `testAPageReusingARetiredIdInheritsNothing` already uses.

### IAR-A2 — "Backup Data" writes the open editor's unsaved edits to disk, from a worker thread

**Where.** `src/org/traincontrol/gui/TrainControlUI.java:15742-15743` (the backup menu item) against
`:1554-1593` (the guarded block in `saveState`), and `AutonomySession.java:273-280` (`discardEdits`).

**What is wrong.** `saveState(boolean backup)` ends with a block that lifts the running layout back
into the active configuration and writes it:

```java
// Not on backup: backups run this method from their own thread, and the session is an event
// thread object - and "Backup data" silently rewriting the active configuration would surprise
// anybody who pressed it to get a copy, not to commit their session.
if (!backup && this.activeDiagramConfiguration != null && this.model.hasAutoLayout()
        && this.model.getAutoLayout().isValid()
        && !this.model.getAutoLayout().isRunning())
{
    …
    session.captureFromLayout(this.model.getAutoLayout().toJSON(), this.activeDiagramConfiguration);
    session.saveWithoutReconciling();
}
```

`FR-015` changed the backup menu item from `saveState(true)` to `saveState(false)` so the archive
would hold live state. The guard is `!backup`. It is now true on the backup path, and `saveState(true)`
has no callers left anywhere in `src/` or `test/` — so the exclusion the comment describes protects
nothing, on the one path it was written for, and both of its stated reasons still hold.

**Why it matters, deterministically.** `backupDataMenuItemActionPerformed` calls neither
`refuseWhileEditorOpen()` nor `refuseWhileAutonomyRunning()`. `getAutonomySession()` returns the cached
`autonomySession` — the same object the autonomy editor is editing. So pressing Backup Data while the
editor holds unsaved edits writes those edits to `setup.json` and to every `configuration-*.json`.

That is not recoverable by Cancel, because Cancel does not undo — `AutonomySession.discardEdits()` is
`store.load()`, and its own javadoc says why: "what is on disk is by definition the last state the
user agreed to". The edits are on disk now, so Cancel reads them back in. The javadoc two lines above
it describes precisely this failure — "'exit without saving' was a promise nothing kept … still
written out by the next save from anywhere at all" — and `OB-070` closed the exit door by asking the
editor about unsaved work on the way out. The backup door was not swept.

**The second half, which is a race rather than a certainty.** The comment's other reason — "the
session is an event thread object" — is also live again. `captureFromLayout` rebuilds an
`AutonomyBuilder` and rewrites the configuration's `points`; `store.save()` iterates the shared
`LinkedHashMap`s and writes thirteen fields plus one file per configuration. All of that now runs on
the backup thread while the EDT is free to edit the same collections. A `ConcurrentModificationException`
inside `sharedFields()` is caught at `:1589` and only logged — after `setup.json` may already have been
replaced and before the configuration files are written, leaving the two halves of the setup out of
step and the archive built from that state, under a dialog that says the backup completed. And if
`autonomySession` is null, `getAutonomySession()` builds one on that thread, which opens every page and
runs `migrateStationLabels()` — rewriting page `.cs2` files from a worker.

**How I verified it.** By reading, end to end: `backupDataMenuItemActionPerformed:15714` starts a raw
thread and calls `this.saveState(false)` at `:15742` with no editor or running check; `saveState`'s
guard at `:1557` is `!backup`; `getAutonomySession()` returns the cached session;
`AutonomySession.discardEdits()` is `store.load()`. `grep -rn "saveState(true)" src/ test/` returns
nothing, which is what makes every `!backup` guard in both implementations vacuous. I did **not**
execute this path — it needs a live layout, a session and a window — so the sequencing is verified by
reading and the runtime effect is inferred from `store.save()`'s body.

**What I would do.** Two changes, not one:

1. Restore the exclusion explicitly rather than through `backup`: give the block its own condition
   (`isEventDispatchThread()`, or an argument that says "this is the exit save"), so it cannot be
   switched off again by a caller changing one word. The comment should move onto that condition.
2. Put `refuseWhileEditorOpen()` at the top of `backupDataMenuItemActionPerformed`, beside the other
   twelve doors that have it. A backup taken over a half-finished diagram is not a backup anybody
   wants.

Then sweep the rest of `backup`: with no caller passing `true`, the parameter, the `prefix`
machinery and `Util.backupFolder` are all dead (see `IAR-C1`), and dead branches that read as guards
are how this happened.

---

## B — medium. Incorrect results, or crashes in specific configurations.

| | Finding | Disposition |
|---|---|---|
| **IAR-B1** | `autonomy.json` is no longer in the backup, and used to be | **Open** |
| **IAR-B2** | A fourth event-thread caller of a synchronized `Layout` method, and the only one gated on autonomy running | **Open** |

### IAR-B1 — the legacy autonomy graph fell out of the backup

**Where.** `src/org/traincontrol/gui/TrainControlUI.java:15755-15772` (the archive's source map) against
`:1634-1663` (the `autonomy.json` write) and `:149` (`AUTONOMY_FILE_NAME = "autonomy.json"`).

**What is wrong.** Before `FR-015`, Backup Data called `saveState(true)`, which wrote three timestamped
copies into `tc_backup/`: `backup<ts>UIState.data`, `backup<ts>LocDB.data` and — at `:1637-1639` —
`backup<ts>autonomy.json`. `FR-015` replaced that with one archive whose source map is

```java
state.put(TrainControlUI.DATA_FILE_NAME, new File(TrainControlUI.DATA_FILE_NAME));   // UIState.data
state.put(MarklinControlStation.DATA_FILE_NAME, new File(MarklinControlStation.DATA_FILE_NAME)); // LocDB.data
state.put("config", new File(localLayout, "config"));
```

`autonomy.json` is not in it. It lives in the working directory beside the other two, not under
`config/`, so nothing else in the archive covers it. And because `backup` is now `false`, the copy that
used to go into `tc_backup/` is not made either — `:1637` writes the live path instead. So the file is
backed up nowhere.

**Why it matters.** `autonomy.json` is not vestigial: `:1595-1600` calls it "the LEGACY configuration —
hand-authored, and still the fallback this application auto-loads", and `:1552` calls it a readable
backup of the graph. For any operator who has not moved to a diagram-derived configuration —
`activeDiagramConfiguration == null` — it *is* their autonomy setup, and Backup Data now produces an
archive containing their locomotives and their window layout and none of their railway, under a dialog
that says the backup completed. The feature request that prompted this asked for "autonomy files —
effectively, all state".

**How I verified it.** By reading, plus `grep -rn "saveState(true)" src/ test/` (no results) to confirm
the timestamped copy is no longer made by anything.

**What I would do.** One line: add `state.put(AUTONOMY_FILE_NAME, new File(AUTONOMY_FILE_NAME))` to
the map. `zipInto` already skips a source that does not exist, so a layout that has never used
`autonomy.json` is unaffected. Worth also asking whether the archive should carry the preferences
(local layout path, IP) — a `config/` restored to a machine that does not know where the layout lives
is only half a restore — but that is a question, not a defect.

### IAR-B2 — clicking a turnout during a run blocks the event thread on the Layout monitor

**Where.** `src/org/traincontrol/gui/LayoutLabel.java:384-389`, inside the `mouseClicked` →
`invokeLater` body that begins at `:326`. It calls
`tcUI.getModel().getAutoLayout().getActiveAccs()` — `Layout.java:694`, `synchronized public`.

**What is wrong.** This is the OB-079 class: a `synchronized` `Layout` method called on the event
thread while `configureAndLockPath` holds the same monitor across `loc.delay(CONFIGURE_SLEEP)` for
every edge of the path it is configuring (`Layout.java:2386-2441`, `CONFIGURE_SLEEP = 150`).

It is a *fourth* site. OB-079's entry names three — `explainDestinations` from the hover tooltip
(fixed in `bd028172`), `getPossiblePaths` in the diagram right-click menu, and the synchronized
`moveLocomotive` on paste — and the commit that fixed the first argued the other two could wait
because "neither runs while the monitor is held for seconds". That argument does not transfer here,
and the reason is the guard on the line above it: this branch is reached **only** when
`tcUI.getModel().isAutonomyRunning()` is true. It cannot run at any other time.

**Why it matters on a real railway.** The freeze is the length of the configuration phase of whatever
dispatch is in flight — 150 ms per edge plus the accessory sends, so of the order of one to two seconds
on an ordinary path and longer on a long one. During that window the whole window is unresponsive,
including Stop. The user gesture that triggers it is throwing a turnout by hand while trains are
running, which is exactly when a delayed stop matters most. The dialog this code is on its way to show
— "this accessory is part of an active route, proceed?" — also arrives late, so the operator sees a
frozen window rather than the question.

**How I verified it.** By reading. `mouseClicked` at `:326` posts to `invokeLater`, so `:386` is on the
EDT; `getActiveAccs` at `Layout.java:694` is an instance `synchronized` method, the same monitor
`configureAndLockPath` takes at `:2386`; the branch is under `isAutonomyRunning()` at `:384`. I did not
measure the freeze — that needs the hardware, and it belongs in `docs/manual-tests/tests.md` rather
than here.

**What I would do.** The same shape `bd028172` used for the hover: take the answer on a worker and
apply it when it arrives. This one is easier than the hover was, because the whole tail of the handler
is already destined for a worker (`submitSwitching` at `:434`) — the set can be fetched there and the
dialog raised back on the EDT if it turns out to be needed. Add it to OB-079 rather than filing a new
item, so the three-site list stops being wrong.

---

## C — low. Cosmetic, dead code, or narrow edge cases.

| | Finding | Disposition |
|---|---|---|
| **IAR-C1** | The `backup` parameter and `Util.backupFolder` are dead, and the dead branches read as guards | **Open** |
| **IAR-C2** | The backup thread enables and disables a menu item directly | **Open** |

### IAR-C1 — a parameter no caller sets, and four branches that look like protection

`grep -rn "saveState(true)" src/ test/` returns nothing. Both implementations —
`TrainControlUI.java:1451` and `MarklinControlStation.java:1446` — still branch on it in four places
between them (`prefix`, the path choice, the `unreadable*` copy-aside, and the block in `IAR-A2`), and
`Util.backupFolder` (`Util.java:195-206`) has no caller at all now that `FR-015` archives the folder
instead of copying it.

This is not only tidying. `IAR-A2` happened because a guard written as `!backup` reads like a decision
and is now a constant, and the next reader of `saveState` has no way to tell which of those four
branches are live. Either give `backup` a caller again or take it out and let each branch state its own
condition. `Util.backupFolder` should go with it — its javadoc still describes the folder-of-copies
design `FR-015` replaced, so it is a worked example of the wrong thing.

Two of the now-live branches were checked and are harmless or better: the `unreadable*` copy-aside in
both implementations now fires from the backup path, which keeps a copy of an unreadable database
slightly earlier than before and still only once.

### IAR-C2 — Swing touched from the backup worker

`TrainControlUI.java:15731` and `:15784` call `this.backupDataMenuItem.setEnabled(false)` / `(true)`
directly from the thread started at `:15715`. `:1616` (`this.autonomyJSON.setText(...)`) and `:1634`
(`getText()`) are reached from the same thread. Cosmetic in practice — a menu item's enabled flag is
not read while the menu is closed — but the file's own comments elsewhere ("Swing, so on the EDT. These
two were being set from the worker thread directly", `:17682-17684`) hold the opposite rule three
screens away, and `turnOnLightsMenuItemActionPerformed:15682` has the same shape. Worth fixing all
three together rather than one at a time.

---

## D — not defects. Checked and clean, and one finding withdrawn.

| | Item | Outcome |
|---|---|---|
| **IAR-D1** | `AutonomyEditorPanel.applyWhy` calls `explainDestinations` on the EDT | **Withdrawn** — would have been a B |
| **IAR-D2** | `Util.writeAtomically` and every caller | Clean |
| **IAR-D3** | `AutonomyCompanionStore.load()` against the "empty store saved over a good file" class | Clean |
| **IAR-D4** | `refuseWhileAutonomyRunning` — all eight call sites | Clean |
| **IAR-D5** | The start-up latch in `MarklinControlStation` | Clean |
| **IAR-D6** | `holdEntries` / `mergeHeld` — the value side of a held entry | Clean |
| **IAR-D7** | `renamePageOnDisk` writing name-form keys into an id-keyed file | Clean |
| **IAR-D8** | `configureAndLockPath`'s three exits | Clean |
| **IAR-D9** | `refreshOneSignal` and the `signalAspects` memo | Clean |
| **IAR-D10** | Configurations are name-keyed, so `IAR-A1` does not transfer placements | Scope, not a finding |

### IAR-D1 — withdrawn: the WHY tool cannot run while the monitor is held

`AutonomyEditorPanel.java:4541` calls `layout.explainDestinations(standing)` — a `synchronized`
`Layout` method — from `applyWhy`, which is reached from a tile click handler on the EDT
(`:4114`). That is the same shape as `IAR-B2` and as OB-079, and OB-079's entry does not name it, so I
raised it.

It does not hold. The surrounding machinery already excludes it, in both directions:
`LayoutEditor.arriveAt:4632` refuses the autonomy surface when `parent.isAutonomyBusy()`, and
`startAutonomyActionPerformed:17602` refuses a start when `refuseWhileEditorOpen()`. So while the WHY
tool is reachable, nothing is dispatching, and the monitor is not held across sleeps. `isAutonomyBusy`
counts a hand dispatch as running (UR-2), so that arm is covered too.

Recording it because the difference between this and `IAR-B2` is the entire finding: `IAR-B2`'s site
is *gated on* autonomy running and this one is gated *against* it, and both look identical at the call.

### IAR-D2 — the atomic write

Read in full (`Util.java:349-372`) with every caller: `AutonomyCompanionStore:3594`,
`LayoutDiagram:486` and `:985`, `TrainControlUI:1528` and `:1653`, `CS2File:1926`,
`MarklinControlStation:1520`. Staging file, `body.write`, flush, close, then
`Files.move(REPLACE_EXISTING)`; a throw of either kind deletes the staging file and leaves the target
untouched. The `.part` suffix is shared with `downloadFile`, but no target is written by both. The
only raw writers left in `src/` are user-chosen exports (`AutoJSONExport:111`,
`AutonomyViewerPanel:1164` and `:1314`, `LocomotiveStats:444`), where a truncated file is the user's
own copy and not state.

`MarklinControlStation:228` states the limit correctly and I checked it holds: atomic writing is no
protection against writing *nothing* successfully, and the `databaseLoadFailed` /
`uiStateLoadFailed` copy-aside is what covers that. Both are present and both fire once.

### IAR-D3 — a bad file cannot empty the store

`AutonomyCompanionStore.load()` (`:574-692`) was attacked specifically for the shape that destroyed
the locomotive database: parse first, clear second. `setup.json` is parsed before anything is
discarded; every `configuration-*.json` is read and parsed into a local map before `clear()`; a
`RuntimeException` out of `readShared` restores the snapshot taken at `:664` and rethrows. The
version check refuses a newer file rather than reading it partially. I could not find a path that
leaves a live blank store one Save away from disk.

### IAR-D4 — the modal-off-the-EDT sweep

`refuseWhileEditorOpen` was fixed for this (OB-078) by marshalling its dialog. Its sibling
`refuseWhileAutonomyRunning` (`TrainControlUI.java:3697`) shows a modal `JOptionPane` with no such
check, which is the exact "fix one site, sweep the siblings" shape. I opened all eight call sites —
`:13774`, `:15619`, `:15818`, `:15844`, `:15915`, `:18284`, `:18532`, `:18736` — and every one is on
the EDT: seven are generated action handlers, and `doSync`'s two other callers
(`LocomotiveSelector:376`, `TrainControlUI:19215`) are inside an action handler and an `invokeLater`
respectively. No finding. Worth an assertion in the method rather than eight readings next time.

### IAR-D5 — the start-up latch

Re-read `MarklinControlStation:3703-3788` because a latch with no timeout is on the brief's list. There
is exactly one `countDown`, in a `finally` that catches `Throwable`, and `built.set(true)` happens
before it on the success path — so the `await()` cannot be released ahead of the flag it is checked
against, which is what `FBR-A1` was. Nothing else can leave the latch at one. Clean.

### IAR-D6 — held entries and their values

`holdEntries` (`:3291-3332`) tests the value side as well as the key: a caption whose station is on an
absent page, a portal whose far end is, a station whose signals are. `holdElements` does the same for
the two bare lists, and `excludedPages` is held by page rather than by square. `mergeHeld` puts them
back and never over a live entry. `AutonomySession.save():3912-3931` suppresses reconciliation entirely
while any page is absent, so nothing prunes them. This is the half of OB-067 that works, and it is why
`IAR-A1` is a *re-owning* rather than a deletion.

### IAR-D7 — the on-disk rename

`repairOnDisk` (`:1066-1118`) gives the store the numbering the *file* was written under, so a rename
performed with no session leaves the renamed page's keys in NAME form in a file that is otherwise
id-keyed. I traced whether that loses them: `pageIsHere` (`:3702-3727`) falls through to `return true`
for a string that is neither a known id nor a recorded name, `pageOf` hands the string back unchanged,
and the next save from a real session translates it to the id. It round trips and self-heals.
`testAutonomyDiagramStore:2311-2360` covers the same ground. Not a defect.

### IAR-D8 — the lock-and-configure path

`configureAndLockPath` (`Layout.java:2360-2496`) has three exits and all three release what they took:
`edgesLocked` is incremented *before* `setOccupied`, so the recovery slice can never be short; the
`RuntimeException` path releases outside the monitor and rethrows rather than reporting "occupied";
and `takingPath.remove(loc)` is on all three. `validatePathActuation` waits outside the monitor with a
deadline that scales with the path. The `InterruptedException` arm no longer returns `true` (OB-080).
Nothing to add.

### IAR-D9 — the protecting signals

`refreshOneSignal` (`:5005-5078`) asks the question of the *signal* rather than of the square, so a
platform emitted as several Points and two stations sharing one signal are both handled without a
special case; `signalAspects` is a `ConcurrentHashMap` keyed by accessory; the memo is cross-checked
against `acc.isRed()` so a signal driven green by a path configuration is corrected on the next
occupancy change rather than agreeing with a stale note. `refreshProtectingSignal` gates on
`isRunning()` so setup gestures do not drive hardware, and `refreshAllProtectingSignals` clears the
memo *and* re-asks every signal at the start of a run, which is the case clearing alone missed. Clean.

### IAR-D10 — scope of IAR-A1

Worth writing down because it bounds the damage. `AutonomyCompanionStore.save():715-721` writes each
configuration's JSON verbatim, with no key translation — configuration point keys are page **names**,
while the shared half is page **ids**. So the page that inherits a retired id inherits the shared
half (names, stations, lengths, directions, barred arrivals, station signals, blocked points, portals,
captions, link names, page exclusion, disabled links) and **not** the placements, homes or
per-configuration exclusions. That is also why `renamePage` has to rekey configurations explicitly
while the shared half needs nothing.

---

## Dispositions

**Claude, 2026-08-24.** All four fixed. Two of them were regressions I introduced this week.

| | What was done |
|---|---|
| **IAR-A1** | Fixed. The retirement lasted exactly one write, reproduced with a probe before anything was touched: a new page took the absent page's id, and its settings with it. `writeLayoutIndex` takes a floor now, supplied by the autonomy setup - the only thing that remembers ids belonging to pages that are not loaded. The index cannot remember on its own without a new field in a file real Maerklin hardware reads. Test seen red with the floor ignored. |
| **IAR-A2** | Fixed. FR-015 made Backup Data call `saveState(false)`, which turned on a block whose own comment forbids exactly that - so pressing Backup with the editor open committed the unsaved edits to disk, from the wrong thread, and Cancel could not take them back. One flag was doing two things; they are two flags now. |
| **IAR-B1** | Fixed. The legacy `autonomy.json` is in the archive. It was in neither half of the backup before: not in the zip, and no longer copied aside either, because that copy only happens on a timestamped save. |
| **IAR-B2** | Fixed. `getActiveAccs` no longer takes the Layout monitor, so throwing a turnout by hand during a run cannot freeze the window. It is safe without it by the pattern this class already documents for UI reads, and what is given up - compound atomicity across one locomotive's path being replaced - is the right thing to give up for a warning. |

**IAR-D1 is worth reading before the next pass.** A finding that looked identical to B2 was withdrawn,
because two refusals elsewhere make the monitor unreachable from that tool. That is the calibration
this folder's README asks for, and it is the difference between three sites and four.
