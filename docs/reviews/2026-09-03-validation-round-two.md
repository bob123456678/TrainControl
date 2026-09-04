# Validation round two of 2026-09-03 — the fixes to `VD9`, and the six commits round one did not see

**Status:** open

**Prefix:** `VD10`. Cite findings from here as `VD10-A1`, `VD10-C4`, and so on.
(`DAY DY3 IPR LE R28 RC REL RG3 RGN SG TSX SVN VD9 V31 V32 V33 MT` are in use elsewhere.)

**Reviewed:** branch `autonomy-diagram-r0` at `fcc11670`, 2026-09-03 evening. Twenty-four commits,
`abd377b3..HEAD`; the six after `fd7c4dd8` are the ones
[`2026-09-03-validation-of-the-day.md`](2026-09-03-validation-of-the-day.md) (`VD9`) could not see.
Nothing was changed by this pass; the only artefact is this file.

**Method.** `VD9` found 0 A, 8 B, 20 C, 13 D, and all 28 B and C findings were then fixed in
`fb609d0e` and `f7bb363a`. Three of its Bs were defects in *corrections* — `VD9-B1` a mangled comment
edit, `VD9-B6`/`VD9-B7` a replacement sentence that was backwards — so the primary target of this pass
was the corrections to those corrections, hunting one shape above all others: **a fix that introduces
a new defect of the same kind it was fixing.** It found seven of them, listed at the end of this
paragraph so that a reader can judge the rest of the document by the hit rate: `VD9-B6`'s fix left a
mangled javadoc line (`C3`); `VD9-C13`'s fix invented a false history (`C5`); `VD9-C16`'s orphaned
javadoc came back three commits later and turned a test red (`B3`); `VD9-C12` deleted stale line
citations and the same round wrote seven more (`C1`, `C9`); `VD9-C9`'s in-place write is licensed by a
precondition that does not hold at the new site (`C7`, `B6`); `fcc11670`, whose whole subject is "one
status, one location", fixed one of the two locations (`C4`); and `MT-246`'s one-exit rule reproduces
the "fixed at the door that was reported" pattern it was written to end (`B2`).

Two of today's commits were read hardest, on instruction: `416e34c2` (`RC` carried #3, which releases
track on a live railway after a mid-run failure) and `c892ec03` (`MT-246`, the operator's own report).
Both carry an A.

**Executed, not only read.** `regression.testJavadocsAreAttached` and
`regression.testEditorSurfaceRules` were each run alone through `docs/tools/one.sh`. The javadoc
ratchet **fails at HEAD** (`B3`). The full battery was NOT run — it had been run and reported green,
and that green predates `c892ec03`. `compare-conditions.py`'s logic was exercised on constructed
inputs outside the repo. `cs2_sample_layout/` was read and never written.

---

## Summary

| | | |
|---|---|---|
| **A1** | `RC` #3 releases the path *after* `clearedEdges` has been dropped, so with `atomicRoutes` off every edge the tail already gave up is released a **second** time - taking away a claim another train made in between. Not "the same order" as the ordinary path, and the operator's live configuration is the mode it happens in | open |
| **A2** | `MT-246`'s persistence half rests on a premise the wiring refutes: `onDiagramChanged` is `repaintLayout()`, not `session.save()`. The save has been on `refresh()`'s `onChanged` all along, and `item()` calls `refresh()` after every menu action - including `promptHome` | open |
| **B1** | The release happens before the only statement that stops the failed locomotive, and it waits on a monitor this file documents as held for seconds | open |
| **B2** | Five more doors on the same shared tile menu write the setup and never tell the running layout; the new surface rule can see none of them | open |
| **B3** | `c892ec03` orphaned `refresh()`'s javadoc, and `regression.testJavadocsAreAttached` **fails at HEAD** - 93 orphans against `ALLOWED = 92`, `AutonomyEditorPanel` 19 against a pinned 18 | open |
| **B4** | Seven of the eight translations of `autolayout.errorRunStoppedByFailure` lost their diacritics; the lines they replaced had them | open |
| **B5** | The condition-parity harness reports success on **zero** conditions compared; the guard against exactly that is in the Java test and was not carried across | open |
| **B6** | `VD9-C9`'s in-place write can leave a truncated or zero-byte note, silently, where the atomic move it replaced could never destroy one | open |
| **B7** | `VD9-C8` is dispositioned fixed; no commit in the round touched `test/README.md`, the sentence is still false, and `build.xml` now points the reader at it | open |
| **C1** | `RC` #3's "same order (`:5701`)" citation was wrong when it was written - the site is `:5728` - and the `VD9-C12` sweep three hours later did not reach it | open |
| **C2** | One click on the station radio now runs `setupChanged()` **three** times: three full configuration loads on the event thread, where there was one | open |
| **C3** | `VD9-B6`'s fix left `*     * **The condition is his…` - a javadoc line mangled by the hunk, in the fix for a finding about a mangled hunk | open |
| **C4** | `fcc11670` wrote the dispositions into the per-finding tables and left all 28 summary rows reading `open`; `a4f5990e`, eleven hours earlier, was about that exact drift | open |
| **C5** | `VD9-C13`'s replacement invents a period in which "four" was right. When `Kind` had seven members the predicate admitted two, so five were absent - never four | open |
| **C6** | `VD9-C20`'s per-page count now understates the clause it is grammatically bound to, and the filename half was reworded rather than fixed | open |
| **C7** | `VD9-C9` cites `forgetBeforeEdit`'s licence for torn writes at a site where that method's own javadoc says it does not apply | open |
| **C8** | `VD9-C15` renamed the test and left the sentence it was about standing three lines under the new disclaimer | open |
| **C9** | `VD9-B4`'s fix wrote six fresh line citations into `nbproject/build-impl.xml` - a NetBeans-regenerated file - in the round that deleted line citations for outliving their targets | open |
| **C10** | `compare-conditions.py` advertises a `[report.md]` argument it does not implement, so the condition result reaches no report; the parity report contains the word "condition" zero times | open |
| **C11** | The parity harness defaults to the live `cs2_sample_layout` routes file that its own sibling test refuses to read; a missing file is a silent pass; `set -e` makes a driver crash leave a stale `report.md` | open |
| **C12** | Nothing in the repo can fail the condition comparison. All 39 came out byte-identical, so the truth table, the reshape handling and the 2^20 cap were dead code in the run that produced the number | open |
| **C13** | `atom_of` renders a leaf's state with Python `repr`, which is key-order sensitive while the identity check beside it is not - so canonically identical trees can be reported NOT equivalent | open |
| **C14** | `promptNumber` and `promptPercent` announce a setup change after a parse that failed and wrote nothing | open |
| **C15** | `Layout.java:5512` says `atomicRoutes` on is "what Adam runs". His active configuration says otherwise, and `A1` is the cost | open |
| **C16** | The parity output pasted into the independent review omits the two lines that say whether anything was skipped | open |
| **D1** | `RC` #3's central premise is true: `getActiveAccs` does read exactly the three maps the handler had already given up | closed |
| **D2** | `RC` #3 introduces no lock inversion - the failure path takes the two monitors in the same order as the ordinary path, and nothing takes them the other way | closed |
| **D3** | `testAFailedPathStopsTheRunAndGivesTheTrackBack` discriminates on both halves | closed |
| **D4** | `VD9-B1`'s comment repair is complete, and agrees with `Edge.release`'s javadoc | closed |
| **D5** | `VD9-B6`'s replacement account of the reversal guard is true of both methods it describes | closed |
| **D6** | `VD9-B7`'s cross-reference resolves to a real method | closed |
| **D7** | `VD9-C3`'s `errorEdgesNotEmpty` correction matches what `edgesAreEmpty` inspects, in all eight bundles | closed |
| **D8** | `VD9-B2`'s `DONE` re-entry guard is in both runners and is armed before the traps | closed |
| **D9** | The new surface rule's brace-walk finds the true extent of all seven methods it examines | closed |
| **D10** | `NodeGroup` is AND in both engines, and the Python matches; the 2^20 bail-out is loud and exits non-zero | closed |
| **D11** | `atom_of` does not collide on any leaf `ConditionParityDriver` can emit; the 39-condition count is right | closed |
| **D12** | `regression.testEditorSurfaceRules` passes: 37 tests, 0 failures, 0 skips | closed |
| **D13** | `VD9-C15`'s rename left no stale references anywhere in the tree | closed |
| **D14** | `VD9-C20`'s counting change itself is right: per page, after the write, no double count, no off-by-one | closed |

---

## A — high

### A1 — the release runs after `clearedEdges` has been dropped, so it gives every early-released edge back a second time

| | |
|---|---|
| **Disposition** | fixed - the clear follows the release, as it does on the ordinary path; test seeds clearedEdges from inside the run and the mutation fails |
| **Confidence** | confirmed by reading, and the mode confirmed from the operator's own configuration file |

`src/org/traincontrol/automation/Layout.java:5099-5104` says of the new release:

```java
                // Released the same way a finished path is released, under the same lock and in the
                // same order (`:5701`), so the failure path and the ordinary path end in one state
                // rather than two.
                synchronized (this.activeLocomotives)
                {
                    this.unlockPath(path, loc);
                }
```

**The order is not the same, and the difference is the whole of this finding.** The ordinary path
(`:5726-5732`) unlocks FIRST and clears the bookkeeping afterwards:

```java
        synchronized (this.activeLocomotives)
        {
            this.unlockPath(path, loc);

            this.activeLocomotives.remove(loc);
            this.locomotiveMilestones.remove(loc);
            this.clearedEdges.remove(loc);
```

The failure path does it the other way round: `:5048-5053` removes `activeLocomotives`,
`locomotiveMilestones` and `clearedEdges` fifty lines *before* `unlockPath` is reached.

`unlockPath`'s non-atomic branch is driven by `clearedEdges`. At `:3336`:

```java
                    Set<Edge> alreadyGivenUp = this.clearedEdges.get(loc);

                    if (alreadyGivenUp != null && alreadyGivenUp.contains(e))
```

On the failure path that lookup is **always null**, because the entry was removed at `:5052`. So the
`else` at `:3358` runs for every edge, including every edge the tail already released early at
`:5568`, and `e.setUnoccupied()` is called a second time. The same is true of the other arm: `:3390`'s
`givenUp == null` releases the lock edges again.

What that costs is written down at the site, twelve lines above the null lookup, `:3341-3355`:

> *"With atomicRoutes off this path gives each edge up twice … Now that it counts, the second release
> would take away a claim somebody else made in between: this path releases the edge early; another
> path locks an edge that NAMES this one, so it is protected again; this path finishes, reaches the
> edge here, and frees it under that train."*

`Edge.release()` floors at zero for the edge itself, so an over-release is harmless **only when nobody
else holds a claim**. When somebody does — which is the case the comment describes — the decrement
comes out of their claim. `Edge.setUnoccupied` then cascades `setLockedEdgeUnoccupied()` to every lock
edge, each decrementing with no knowledge of whose claim it is.

`stopLocomotives()` does not close this. It sets `running = false`, which stops autonomy choosing a
new path. It does not stop `MarklinRoute.heldReason`, which asks `getActiveAccs`, which asks
`Edge.isLockHeld`. An over-released lock edge reads as free there, so **an s88-fired route can throw a
turnout on track a different, still-moving train is standing on** — which is precisely what
`getActiveAccs`'s own comment at `:5510-5516` says the bookkeeping exists to prevent, and the s88 door
has no human in it.

**This is the operator's mode.** `cs2_sample_layout/config/autonomy/setup.json:43` names `Main` as the
active configuration, and `cs2_sample_layout/config/autonomy/configuration-Main.json:5` reads
`"atomicRoutes": false` in the working tree. (The committed value is `true`; the live file, which is
what the application reads, has been changed to `false` and is uncommitted.) Either way the mode is
reachable and it is the one `OB-164` came from — Adam's own report, *"in non atomic mode, locks aren't
getting released"*.

The remedy is the sentence the comment already claims: do it in the same order. Leave
`clearedEdges.remove(loc)` until after `unlockPath`, so that the release skips what the tail already
gave back — exactly as the ordinary path does. Nothing else about the ruling changes.

Graded A rather than B because it is wrong behaviour on the railway in the configuration the operator
is running, and because the state it produces — a claim silently taken off another train's path — is
the one the whole occupancy count was converted from a boolean to prevent.

### A2 — `MT-246`'s persistence half is dispositioned fixed on a premise the wiring refutes

| | |
|---|---|
| **Disposition** | fixed - the premise was false and the real mechanism is the capture on the way out; MT-246 says so now |
| **Confidence** | confirmed by reading; the two runnables were traced from both construction sites to their call sites |

`AutonomyEditorPanel.setupChanged`'s javadoc, `src/org/traincontrol/gui/AutonomyEditorPanel.java:6340-6343`:

> *"`onDiagramChanged` is wired by `TrainControlUI` to `session.save()`, so it is what makes an edit
> outlive the process - the panel that supplies the DIAGRAM's tile menus has no Save button and no
> close, so without it the write lives and dies in memory."*

**It is not.** `TrainControlUI.java:4168-4169`:

```java
            autonomyTileMenus.setOnDiagramChanged(() ->
                javax.swing.SwingUtilities.invokeLater(() -> repaintLayout()));
```

`onDiagramChanged` on the diagram surface is a **repaint**. The `session.save()` is the panel's *other*
runnable — the constructor's third argument, stored as `onChanged` (`:364`) — wired at
`TrainControlUI.java:4119-4139`, and run at exactly one place: the last statement of `refresh()`,
`AutonomyEditorPanel.java:6529`.

And `refresh()` is called after every menu action, by the wrapper the item is built with.
`item(String, Runnable)`, `:1952-1982`, runs the action and then calls `refresh()` at `:1979`;
`radio(...)` at `:2905` and `toggle(...)` at `:2073` call `placementChanged()`, which ends in
`refresh()`. `promptHome` is wired with `item(...)` at `:1113`.

So on the track diagram, **`promptHome` reached `session.save()` before this commit and reaches it
now, by the same route** — `item()`'s `refresh()` → `onChanged` → `noteIfTheSetupWasNotTidied(session.save())`.
The half of `MT-246` that genuinely was broken and genuinely is fixed is the other one: nothing called
`rebuildRunningLayoutFromSetup`, so `triageReturnToHome` asked a running layout that had never been
told, and Return Home stayed greyed. That half is correct and well argued.

The consequence is that **the operator's reported symptom "it is not persisted when closing the app"
has been dispositioned fixed without its cause being found.** `docs/manual-tests/tests.md`'s `MT-246`
entry and the commit message both say both halves are one omission. If Adam saw what he says he saw,
something else is eating the write — `session.save()` throwing and being logged at
`TrainControlUI.java:4131-4135`, a second session overwriting on close, or `writePointProperty`'s
`getActiveConfiguration() == null` early return at `AutonomySession.java:4478-4480` — and none of
those has been looked at.

Graded A because the finding it closes is an operator report of lost data, closed on an argument the
code contradicts; the test written for it (`testEveryDoorThatWritesTheSetupAnnouncesIt`) cannot detect
the difference, since it reads the source for a line rather than driving the save.

---

## B — medium

### B1 — the track is given back before the failed locomotive is stopped, and the release waits on a monitor held for seconds

| | |
|---|---|
| **Disposition** | fixed - the failed locomotive is stopped before the track is given back |
| **Confidence** | confirmed by reading; the monitor's hold time is documented in this same file |

Adam's ruling is *"force a graceful stop, alert the user, then unlock"*. The handler's order,
`Layout.java:5048-5119`, is:

1. drop the bookkeeping;
2. `stopLocomotives()` — sets `running = false` and nothing else (`:1681-1684`);
3. log the message;
4. **`unlockPath(path, loc)`** — the new call;
5. `updatePendingS88(loc, null)`;
6. `loc.setSpeed(0)`.

`stopLocomotives()` stops *autonomy*. The only thing that stops **the locomotive that just failed** is
step 6, and it now runs after the release rather than before it. The train is under power for the
whole of steps 4 and 5, on track the model has just declared free.

That window is not necessarily short. `unlockPath` is `synchronized` on the layout monitor
(`:3299`), and `pendingS88Monitor`'s javadoc at `:576-578` says why that matters:

> *"configureAndLockPath holds the layout monitor across its whole lock loop - deliberately, because
> claiming a path has to be atomic - and that loop sleeps CONFIGURE_SLEEP per edge and again per
> accessory inside configureEdge, so it is held for seconds on a long path."*

So a hand dispatch or an in-flight lock elsewhere can hold the emergency stop off for seconds, and the
commit's own reasoning is that a person still can dispatch by hand at exactly this moment.

Moving `loc.setSpeed(0)` above the release costs nothing and matches the ruling word for word. This is
the "a fix can be worse than the defect" shape: the correction discarded an emergency stop's position
in the sequence in order to add the release.

### B2 — five more doors on the same menu write the setup and never tell the running layout

| | |
|---|---|
| **Disposition** | fixed - and it was nine doors, not five; the rule now names every writer |
| **Confidence** | confirmed by reading; each door traced from its menu item to its wrapper |

`buildTileMenu` is one method serving both surfaces, and says so at `:903-908`: *"The items are
identical - one menu, built once, so the two places can never drift into offering different things."*
`MT-246` fixed nine doors. These five are on the same menu, write the setup, and end in neither
`setupChanged()` nor `placementChanged()` — so they persist (through `item()`'s `refresh()`) and the
running layout is never rebuilt, which is the half of `MT-246` that was really broken:

| door | menu item | writes |
|---|---|---|
| `applyLength` `:4869` | "Length…" `:1602` | `session.setTileLength`, for the tile or the whole selection |
| `clearAllHomes` `:6717` | Bulk tools → Clear All Home Locomotives `:1876` | the `home` property on every square |
| `nameEverything` `:6640` | the name-everything walk | `session.setPointName`, once per unnamed square |
| `setAllBranches` `:2998` | Connections → All branches → Both / None `:1484-1486` | `session.setDirection` |
| `promptLinkName` `:4231` | "Set Name" on a link or tunnel `:1592` | `session.setLinkName` |

Two of them reproduce a defect that has already been reported and already been fixed once:

- **`clearAllHomes` is `MT-246`'s own property.** Clear every home from the diagram and the setup is
  saved while the running layout keeps them, so `triageReturnToHome` still offers Return Home and
  still sends every train to a home the operator just cleared. Its javadoc at `:6714-6716` still says
  *"Nothing is written to disk here - like every other decision in this window it waits for Save"* —
  the premise `MT-246` was filed to refute, since on the diagram there is no Save. Its sibling
  `clearAllPlacements` ends in `placementChanged()` and is fine.
- **`nameEverything` is `OB-034`.** `promptName`'s comment at `:4211-4221` spells out what a rename
  that does not rebuild costs — *"the caption looks up its station and finds nothing, so the label
  goes blank"* — and the bulk walk calls the same `session.setPointName` and ends in `refresh()`.

The new rule cannot see any of them. `testEveryDoorThatWritesTheSetupAnnouncesIt` matches source lines
containing `session.setPointProperty(` or `session.setHome(` only, so it covers two of the roughly
seventeen setup-writing methods `AutonomySession` exposes. That is the same shape the commit message
condemns: *"the two doors that worked are the two that had been reported before and fixed one at a
time."*

Two smaller consequences of the same scan, worth recording where the rule is:

- the javadoc's **MUTATION** claim — *"removing `setupChanged();` from any one of the nine fails
  this"* — is false for two of the nine. `placementChanged` and `promptName` contain neither matched
  string, so the scan never reaches them.
- `checked` counts matching **lines**, not methods, and comes to exactly 7 against a floor of
  `checked >= 7`. The floor is satisfied at its boundary by a coincidence of line counts.

### B3 — `regression.testJavadocsAreAttached` fails at HEAD

| | |
|---|---|
| **Disposition** | fixed - setupChanged sits above refresh's javadoc |
| **Confidence** | confirmed by execution, and the numbers reproduced independently |

`c892ec03` inserted `setupChanged`'s javadoc and body **between `refresh()`'s javadoc and `refresh()`**,
`AutonomyEditorPanel.java:6326-6360`:

```java
    /**
     * Re-reads the setup and shows what it says.
     * …
     */
    /**
     * Says that the setup changed: persist it, and rebuild the railway it describes.
     * …
     */
    private void setupChanged()
    {
        …
    }
    public final void refresh()
```

Only the last doc comment before a declaration attaches, so `refresh()` — a `public final` method — now
has no javadoc, and the paragraph written for it documents a private helper it does not describe. That
is `VD9-C16` exactly, three commits later.

`testJavadocsAreAttached` is the ratchet that exists to catch it, and it is now red:

```
--- regression.testJavadocsAreAttached
Total tests run: 1, Failures: 1, Skips: 0

*** 1 of the classes above did not come back clean ***
```

Reproducing its counter independently gives 93 orphans against `ALLOWED = 92`, and
`AutonomyEditorPanel.java (19)` against the pinned `AutonomyEditorPanel.java (18)` — so both the total
assertion and the per-file assertion `VAL-C8` added fail, and they name the file correctly.

**The green battery does not cover HEAD.** 148 classes with 0 failures and 0 skips cannot have
included this class after `c892ec03`. Whatever else the battery cleared, it cleared it at an earlier
commit.

The fix is to move `setupChanged` and its javadoc away from `refresh()`'s, not to raise `ALLOWED` —
raising it is the one thing that test's own javadoc forbids.

### B4 — seven of the eight translations of the failure message lost their diacritics

| | |
|---|---|
| **Disposition** | fixed - all seven translations have their letters back, as escapes |
| **Confidence** | confirmed by reading all eight bundles and the lines they replaced |

`416e34c2` rewrote `autolayout.errorRunStoppedByFailure` in all eight bundles. The English is right.
Seven of the other seven are transliterated where their predecessors were properly escaped:

| | now | was |
|---|---|---|
| `de` | `Fahrstrasse`, `aufgeraeumt`, `Pruefen` | `aufgeräumt` |
| `pl` | `ulegla`, `zostala`, `zatrzymala sie`, `mozna bylo uporzadkowac`, `Sprawdz` | `uległa`, `została`, `zatrzymała się`, … |
| `da` | `paa`, `saa`, `staar`, `foer` | — |
| `es` | `fallo`, `via`, `autonomia`, `donde esta` | — |
| `fr` | `tombee`, `arretee`, `meme`, `Verifiez`, `ou` | — |
| `it` | `si e guastata`, `meta`, `cosi` | — |

`nl` is correct because Dutch needs nothing here.

The escapes were available and are used **on the same line**: the French and Italian versions carry
`’` for the apostrophe. The bundles are not transliterated as a rule either — the neighbouring
keys carry between 66 and 1240 escaped lines each, and the two lines immediately above and below this
key in `messages_pl.properties` are fully accented. So this is not a house style, it is seven
regressions in the most alarming message autonomy can print, in the round whose `VD9-D4` recorded
*"the eight message bundles are ASCII and consistently changed"* — true, and it is the wrong question:
ASCII was achieved by dropping the letters rather than by escaping them.

(The `edgesAreEmpty` message changed in `fb609d0e` is a different case and is fine: the whole
`layout.ui.*` block around it was already transliterated, so those replacements match their
neighbours. See `D7`.)

### B5 — the condition-parity harness reports success on zero conditions compared

| | |
|---|---|
| **Disposition** | fixed - a floor of 20, TC_CONDITION_FLOOR to override, exit 2 when it bites |
| **Confidence** | confirmed by executing the committed module on constructed inputs |

`ConditionParityDriver` skips any route with no `"conditions"` key
(`docs/tools/parity/ConditionParityDriver.java:84-87`), writes its TSV unconditionally even when that
is zero lines (`:118-124`), and **exits 0 regardless of its own `failed` count** (`:126`).
`compare-conditions.py` then prints `conditions compared: 0` / `NOT equivalent: 0` and returns 0, and
`run.sh` reports the section as passed.

So if `TC_ROUTES` ever points at a file in a different shape, or the key is renamed, the harness
prints a clean bill of health having compared nothing.

The author guarded this exact hazard on the Java side and did not carry it across:
`test/core/testConditionOutline.java:678-682` asserts `withConditions >= 20` with a note that *"a green
result here would mean nothing"*. That floor belongs in the harness too — it is the harness whose
number was quoted into the review.

### B6 — `VD9-C9`'s in-place write can destroy the note the atomic move could not

| | |
|---|---|
| **Disposition** | fixed - serialised before the stream is opened, and a failure is recorded |
| **Confidence** | confirmed by reading |

`VD9-C9` was right that `Files.move` was the wrong primitive under the lock this repair exists for, and
the replacement at `AutonomyCompanionStore.java:1437-1441` is byte-for-byte the primitive
`forgetBeforeEdit` measured: `FileOutputStream` in try-with-resources, explicit `StandardCharsets.UTF_8`,
`flush()`. That part is right.

What is new is the failure mode. `new FileOutputStream(beforeEditFile())` **truncates at open**, and
`note.toString(2)` is evaluated after the stream is open — `org.json`'s `toString(int)` throws
`JSONException`, a `RuntimeException`, and the catch at `:1443` swallows it. So a failure in
serialise, write or flush leaves a **truncated or zero-byte note**. The `Files.move(REPLACE_EXISTING)`
it replaced could not do that: it either replaced the note or left the old one intact.

The note is the operator's pre-edit snapshot, read back by `unfinishedEdit()` and applied on the next
start. A truncated one fails to parse and the revert is gone.

Compounding it, the catch is silent and the method returns `void`, so `repairLocomotive` at `:1382`
cannot see the failure either. `VD9-C9`'s stated minimum remedy — *"at minimum the failed write
deserves the one-line log that `dispose()` gives the failed delete"* — was not done, and
`forgetBeforeEdit`, the precedent the new comment cites, does `return false`.

Not graded A because it needs an IO or serialisation failure to fire and the note is a recovery aid
rather than the setup itself. The remedy is small: serialise to a `String` before opening the stream,
and log in the catch.

### B7 — `VD9-C8` is dispositioned fixed, and no commit in the round touched the file

| | |
|---|---|
| **Disposition** | fixed - the README says what ant test does, and build.xml no longer points at the false sentence |
| **Confidence** | confirmed by reading, and by `git log` over the file |

`docs/reviews/2026-09-03-validation-of-the-day.md:567` records `VD9-C8` as *"fixed with `C20` - the
`ant test` paragraph and the property comment agree now"*.

`git log 2b913cdf..HEAD -- test/README.md` returns nothing. Neither `fb609d0e` nor `f7bb363a` lists it.
`test/README.md:61-64` still says:

> *"`battery.sh` also sets the `-Dtraincontrol.anyReceivePort=true` system property … `ant test` does
> not, so a class whose `@BeforeClass` fails to bind can silently test nothing."*

That is false at HEAD. `build.xml:133` sets `test-sys-prop.traincontrol.anyReceivePort=true`, and
`nbproject/build-impl.xml:624-626`'s `propertyset` with a glob mapper turns every `test-sys-prop.*`
into a `-D` on the fork.

And `build.xml:129` now ends *"battery.sh remains the gate, and test/README.md says why"* — so the
comment `VD9-B4` rewrote points the next reader straight at the false sentence, which is the pointer
`VD9-C8` objected to, preserved verbatim through the rewrite.

Graded B rather than C because a false disposition is the one thing a validation document cannot
afford, and this one is in the document whose subject is false dispositions.

---

## C — low

### C1 — `RC` #3's line citation was wrong when it was written, and the sweep for exactly that did not reach it

| | |
|---|---|
| **Disposition** | fixed as a side effect of A1, which replaced the block the citation was in |
| **Confidence** | confirmed by reading the file at the commit that wrote it |

`Layout.java:5100` cites the ordinary release site as `` `:5701` ``. At `416e34c2` itself, `:5701` was
a closing brace inside the callback loop; `unlockPath` was at `:5728`. It is `:5728` at HEAD too.

`VD9-C12` — *"stale line citations written into permanent source and docs, in four places"* — was fixed
in `f7bb363a` at 19:18 by citing by method name instead. `416e34c2` landed at 15:31, and `f7bb363a`
does not touch `Layout.java`. So the sweep for the defect did not include the instance written three
hours earlier the same day, and that instance was never accurate.

Related, and the same round: see `C9`.

### C2 — one station radio click now runs `setupChanged()` three times

| | |
|---|---|
| **Disposition** | fixed - one rebuild per gesture, coalesced onto the event queue |
| **Confidence** | confirmed by reading the call chain |

Picking "Can stop", "Can pass through" or "Neither" on the station submenu (`:1144-1154`) runs:

1. `setUsage` → `setStation` → **`setupChanged()`** (`:2995`);
2. back in `setUsage`, `setPointProperty(tile, "active", …)` → **`setupChanged()`** (`:2973`);
3. `radio(...)`'s listener → `placementChanged()` → **`setupChanged()`** (`:3964`), then `refresh()`.

Each `setupChanged()` calls `rebuildRunningLayoutFromSetup`, which is
`getAutonomyViewerPanel().load(activeDiagramConfiguration, false, false)` — a full configuration load
and layout regeneration, on the event thread (`TrainControlUI.java:5420-5450`). Three of them per
click, where before the commit there was one.

The first of the three also runs with the edit half applied: the station flag is written and `active`
is not, so the running layout is rebuilt once from a state the user never asked for before being
rebuilt again from the one they did.

This is the cost `DY3-C5` and `REL-C4` spent two findings removing from the bulk doors — *"every
`setHome` re-derives the station index, which is a full builder construction on the event thread"* —
reintroduced at the single doors from the other direction. `setUsage` calling `setStation` is why:
adding the exit to both members of a nested pair counts the pair twice.

### C3 — `VD9-B6`'s fix mangled a javadoc line, in the fix for a finding about a mangled hunk

| | |
|---|---|
| **Disposition** | fixed - the paragraph break is back |
| **Confidence** | confirmed by reading |

`src/org/traincontrol/automationui/AutonomySession.java:2028`:

```java
     *     * **The condition is his, and it is what stops this being a nag.** A railway that records no
```

The replacement block was appended onto the front of the line that was being kept, so the surviving
sentence carries a stray `* ` in the middle of it and lost its paragraph break — the "Neither is worth
chasing" paragraph now runs straight into it.

`VD9-B1` was *"`REL-C16` deleted two lines of a three-line sentence"* — a hunk that damaged the comment
around what it replaced. Its own fix commit did the same thing one file away.

### C4 — the dispositions were written into one of the two places that hold them

| | |
|---|---|
| **Disposition** | fixed - and the document now says the summary column is a second place by construction |
| **Confidence** | confirmed by counting the table at HEAD |

`fcc11670`'s message: *"Twenty-eight findings were fixed in the tree and left reading "Disposition |
open" … One status, one location - broken by me, in a validation report."*

At HEAD the summary table of `2026-09-03-validation-of-the-day.md` has **28 rows reading `open`** and 13
reading `closed` — the 28 being exactly the B and C findings whose per-finding tables now read
`fixed`. The commit repaired the per-finding tables and left the summary.

`a4f5990e`, at 10:49 the same morning, was filed against precisely this: *"every one of its seventeen B
rows read "open" while nine of the bodies said fixed."* The `README` rule it cites — one status, one
location, in the per-finding table — arguably means the summary column should not exist at all; that
is the durable fix.

### C5 — `VD9-C13`'s replacement invents a period in which "four" was right

| | |
|---|---|
| **Disposition** | fixed - there was no version in which four was right, and it says so |
| **Confidence** | confirmed by walking every commit that touched the file |

The arithmetic is right: `Kind` has 13 values (`src/org/traincontrol/base/CommandRow.java:31-68`),
`canBeACondition` admits 4 (`:292-296`), so 9 are absent, and `RouteEditorFrame.java:3500-3502` builds
the dropdown from `Kind.values()` filtered by that predicate, so the universe really is 13.

The history attached to it is not. `CommandRow.java:289` reads:

> *"(This said four, which was right when `Kind` had seven members - it has thirteen, and this
> predicate admits four of them.)"*

When `Kind` had seven members the predicate admitted **two** — ACCESSORY and FEEDBACK — so five were
absent, never four. Before that there was no predicate at all. `git log -S"The other four kinds"`
returns only the commit that wrote the sentence and the commit that corrected it, and the enum already
had thirteen values on both days. "Four" was never right; the correction supplies it with a past.

Same paragraph, `:275`, still reads *"the condition editor offered all seven kinds"* — true
historically, and now fourteen lines above "it has thirteen", with a new parenthetical inviting the
reader to connect the two sevens. `VD9-C13` named that line as part of the finding.

The second half of `VD9-C13` — the arm about a command row being refused until a sensor is typed —
checks out: AUTO_LOCOMOTIVE cannot reach `defaultSettingFor` on any of the four paths into it.
`docs/reviews/2026-09-03-release-review.md:1244` and `:1248-1249` still carry the refuted sentences,
which may be deliberate if review documents are append-only.

### C6 — `VD9-C20`'s number now understates the clause it is attached to

| | |
|---|---|
| **Disposition** | fixed - the number is attached to the clause it counts |
| **Confidence** | confirmed by reading |

The counting change is correct and is recorded as `D14`. What it does not do is make the sentence true.

`TrainControlUI.java:2663-2665` says the captions *"have been taken into the autonomy setup (N of them)
and removed from these pages"*. `store.save()` runs at `AutonomySession.java:1884`, **before** any page
is written. So when a page write throws, that page's captions **are** in the setup — they were saved —
and are not removed from the page. The old count matched "taken into the setup" and mismatched
"removed from"; the new count matches "removed from" and understates "taken into the autonomy setup",
which is the clause `N` is grammatically bound to. The two halves still describe different sets.

The filename half was not fixed in code. `migratedPages.add(entry.getKey().getName())` is unchanged at
`:1927`; `fb609d0e` reworded the message from "page files" to "pages" instead. The sentence is now
accurate, and `VD9-C20`'s actual point — *"the one thing the operator needs in order to find the
.bak"* — is still missing, because `LayoutDiagram.getFilePath()` (`:414-417`) derives the path from the
url, independently of the name, and the `.bak` is written at `newFilePath + ".bak"` (`:469`).

### C7 — `VD9-C9` cites a licence for torn writes at the one site its author excluded

| | |
|---|---|
| **Disposition** | fixed - the licence cited is the one that applies here |
| **Confidence** | confirmed by reading both javadocs |

`AutonomyCompanionStore.java:1434-1436` says torn writes are acceptable at the repair site *"for
`forgetBeforeEdit`'s reason"*. `forgetBeforeEdit`'s own javadoc, `:4645-4648`, says:

> *"Torn writes are acceptable here **and nowhere else in this class** … There is nothing to protect,
> because the content being overwritten is the thing being disposed of."*

At the repair site the content is not being disposed of: `unfinishedEdit()` at `:1415` has already
proved the note well-formed, and it is about to be applied on the next start. The precondition that
made torn writes free is the one thing that is absent. This is the mechanism behind `B6`, and it is
the "lifted rules lose their precondition" shape — a rule copied from where it holds to where it does
not, which is what `VD9-C9` itself was about.

### C8 — `VD9-C15` renamed the test and left the sentence it was about

| | |
|---|---|
| **Disposition** | fixed at both sites - the claim is the weaker true one |
| **Confidence** | confirmed by reading |

The rename is right and thorough: `testEverySandboxIsOpenedInsideATry`
(`test/regression/testSwitchingToACentralStationLayout.java:1145`) matches its predicate `insideATry`
at `:1178`, and the assertion message at `:1192-1200` was corrected too, so it no longer promises
closure. No stale reference to the old name survives anywhere in the tree (`D13`).

What survives is the claim. `:1128-1130`:

> *"This is the other half: that having opened one, nothing can get out of the method without closing
> it."*

That is the property the paragraph four lines above has just said the test does not check. The audit
document repeats it verbatim at `docs/reviews/2026-09-03-test-suite-audit.md:1772-1773`, in the same
paragraph that was edited for `VD9-C15`.

### C9 — the round that deleted line citations wrote six more, into a generated file

| | |
|---|---|
| **Disposition** | fixed - by macro name, and the route not taken is named |
| **Confidence** | confirmed by reading; all six citations verified accurate today |

`f7bb363a` removed a line citation from `CommandRow.java:284-286` for `VD9-C12`, on the reasoning that
*"a stale line number in a javadoc outlives every edit above it with nothing able to notice"*.

`fb609d0e`, the same round, wrote six fresh ones into `build.xml:118-129` — `:684-688`, `:663-675`,
`:632`, `:203`, `:915`, `:948` — all pointing into `nbproject/build-impl.xml`. Every one is accurate
today. None can survive NetBeans regenerating the file, which the same comment says NetBeans owns, and
nothing checks them. With `C1`, that is seven line citations written on the day the rule against them
was adopted.

The substance of `VD9-B4` is sound: `run.jvmargs` does reach the test fork through
`build-impl.xml:686-687`, it is empty by default at `:203` so a top-level property in `build.xml` wins,
and it does also feed Run (`:948`) and Debug. One qualification: `build.xml` owns its own
`test-one-class` macrodef at `:137-143` and could pass a nested `jvmarg` through `test-impl`'s
`customize` element, reaching only the fork — so the reason given, *"bounding it would bound
TrainControl itself"*, is true of the `run.jvmargs` route and not of the question. The comment reads as
closed, which is the shape `VD9-B4` was filed about, at reduced strength.

### C10 — the condition comparison reaches no report

| | |
|---|---|
| **Disposition** | fixed - it writes the report its usage line always advertised, and run.sh asks for it |
| **Confidence** | confirmed by reading; the absence in the report confirmed by search |

`docs/tools/parity/compare-conditions.py:17` advertises
`Usage: compare-conditions.py <old.tsv> <new.tsv> [report.md]`. `main` (`:119-186`) reads `argv[1]` and
`argv[2]` and writes no file. Its sibling `compare.py:367` does write its report.

So the condition result exists only in terminal scrollback. `docs/reviews/2026-09-03-parity-report.md`
contains the word "condition" zero times, and `ea8b11e0` does not touch it. `run.sh:156` names
`report.md` as *the* report. The commit's *"so this is a section of the parity report rather than
something done once"* is not true of any artefact.

### C11 — the harness reads the live file its sibling test refuses to read, and two ways it passes quietly

| | |
|---|---|
| **Disposition** | fixed - the frozen copy by default, and a skipped section exits 2 |
| **Confidence** | confirmed by reading; `set -e` behaviour verified with a throwaway script |

Three things, all in `docs/tools/parity/run.sh`:

- `:108` defaults `ROUTES` to `$REPO/cs2_sample_layout/…/routes.json` — the operator's live file.
  `test/core/testConditionOutline.java:672` deliberately reads `test/operator_layout/…` instead,
  because *"`cs2_sample_layout` is the live one and moves under a test's feet"*. The two are identical
  today, so the report's 39 and the test's floor happen to agree; the harness's number comes from the
  file the same commit argues is unstable. (`testConditionOutline.java:674-677` also throws
  `SkipException` when its snapshot is missing, which reads as green.)
- `:112-133` — a missing `routes.json` prints *"skipped"*, leaves `CONDITION_STATUS=0`, and `run.sh`
  exits 0. Nothing downstream records that the section did not run.
- `:94-133` sits **before** `compare.py` at `:142`, under `set -e` (`:15`). A driver crash — bad jar,
  `ClassNotFoundException` — now aborts the whole run before the autonomy report is regenerated,
  leaving a stale `report.md` on disk with no staleness marker. Loud in the exit code, silent in the
  artefact.

### C12 — nothing in the repo can fail the condition comparison

| | |
|---|---|
| **Disposition** | fixed - the three pairs are a --self-test rather than a sentence |
| **Confidence** | confirmed by search, and the three pairs re-run against the committed module |

The commit says the comparison *"was checked against three constructed pairs first: reshaped reads
equivalent, identical reads byte-identical, and the actual `IPR-B2` corruption reads NOT equivalent."*
Re-running those three against the committed module confirms all three answers, so the comparison does
discriminate.

But there is no fixture, no `--self-test`, no test file and no reference to those pairs anywhere in
the repo except the prose. They were run once and thrown away. That matters more than usual here
because **all 39 conditions came out byte-identical**, so the run that produced the reported number
exercised only the string compare at `:160`: the truth-table engine, the reshape handling, the
`NodeGroup` handling and the 2^20 cap were all dead code in it. The standard is the one the same
review document states at `2026-08-31-independent-review.md:456` — *"A comparison that cannot fail is
not evidence"*.

The same gap covers the load-bearing assumption underneath: the conclusion "`normalize` reshaped
nothing" is only meaningful if the released 2.8.1 jar lacks `normalize`. It does — `normalize` landed
in `dff8e5a1` on 2026-08-01 and the v2.8.1 backport `4adc7afb` does not touch `NodeExpression.java` —
but the harness never checks. `setup-env.sh:114-116` reasons carefully about exactly this hazard for
`ParityDriver` and no equivalent check exists on the condition path.

Minor, same file: `ConditionParityDriver.java:108` catches `Exception | Error`, so an
`OutOfMemoryError` becomes a `REFUSED` row; `setup-env.sh:131` prints "compiled ConditionParityDriver"
before `:133` prints "compiled ParityDriver", though ParityDriver was compiled first.

### C13 — `atom_of` is key-order sensitive where the identity check beside it is not

| | |
|---|---|
| **Disposition** | fixed - atom names are canonical |
| **Confidence** | confirmed by executing the committed module |

`compare-conditions.py:27-44` builds a leaf's variable name by formatting the whole state dictionary
with `%s`, i.e. Python's `repr`, which preserves insertion order:

```
type=NodeRouteCommand|rc.type=TYPE_FEEDBACK|rc.state={'ADDRESS': '9', 'SETTING': 'false'}
```

The byte-identity check at `:160` uses `json.dumps(..., sort_keys=True)` and is not order sensitive. So
if the two engines' `LinkedHashMap` insertion order for `commandConfig` ever differed, the same leaf
would get two atom names and the tool would print **NOT equivalent** for two trees it also considers
canonically identical — a self-contradictory output.

This fails safe, which is why it is a C: it produces a false alarm, never a hidden difference. The
collision direction was tested and does not reach: no leaf `ConditionParityDriver` can emit collides
with another (`D11`). Sorting the keys before formatting closes it.

### C14 — two doors announce a setup change after a parse that wrote nothing

| | |
|---|---|
| **Disposition** | fixed - both failure paths return before announcing |
| **Confidence** | confirmed by reading |

`promptNumber` (`AutonomyEditorPanel.java:3006-3034`) and `promptPercent` (`:3078-3108`) both put
`setupChanged()` after the `try`/`catch`, so typing "abc" into Station Priority shows an error dialog
and then saves the setup and rebuilds the running layout, having changed nothing. `promptPercent` is
inconsistent with itself about it: an out-of-range value `return`s before the announce, an unparseable
one falls through to it.

Harmless in effect. Recorded because "announce after the last statement" is how the rule was applied
in seven places, and the cancel and failure paths were not looked at when it was.

### C15 — the comment says Adam runs `atomicRoutes` on; his configuration says otherwise

| | |
|---|---|
| **Disposition** | fixed - his configuration has atomicRoutes false, which is A1's premise |
| **Confidence** | confirmed by reading the operator's configuration |

`Layout.java:5510-5513`:

> *"`clearedEdges` is read by `getActiveAccs` … and with atomicRoutes on, which is what Adam runs, the
> lock is held for the whole run by design"*

`cs2_sample_layout/config/autonomy/setup.json:43` names `Main` as the active configuration, and
`configuration-Main.json:5` reads `"atomicRoutes": false` in the working tree (`true` as committed; the
live file has been changed and not committed). `docs/reviews/2026-09-01-test-suite-review.md:266`
quotes the same sentence.

This predates the day's commits, so it is outside the window strictly. It is here because it is the
premise `A1` turns on: the branch of `unlockPath` that `RC` #3 now reaches on the failure path is the
one the comment says nobody runs.

### C16 — the parity output quoted into the review omits the two lines that say whether anything was skipped

| | |
|---|---|
| **Disposition** | fixed - the quotation carries the two lines that say whether anything was skipped |
| **Confidence** | confirmed by comparing the quoted block with the program's output |

`docs/reviews/2026-08-31-independent-review.md:442-448` quotes five lines of
`compare-conditions.py`'s output. The program prints seven (`:166-172`); the two omitted are
`could not be compared:` and `present in only one:`. Since `conditions compared:` at `:166` is
`len(same) + len(different)` and **excludes** both, those two lines are exactly what a reader needs in
order to know that 39 is the whole population. A reader of the report cannot tell whether anything was
skipped.

---

## D — not defects

### D1 — `RC` #3's central premise is true

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading `getActiveAccs` end to end |

The argument that overturned the old "leave it locked" reasoning is that the handler had already given
up the protection. `getActiveAccs` (`Layout.java:874-963`) reads `activeLocomotives`, unions
`takingPath`, and consults `clearedEdges` per locomotive. The handler removes the locomotive from all
of `activeLocomotives`, `locomotiveMilestones`, `clearedEdges` (`:5048-5053`) and `takingPath`
(`:5056`). So the path really was held by nobody and protected by nothing, and only a graph reload
undid it. The premise is exactly right; `A1` is about how the release is sequenced, not about whether
it should happen.

### D2 — no lock inversion was introduced

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by enumerating every `synchronized (activeLocomotives)` block |

`unlockPath` is `synchronized` on the layout monitor, so the new call takes `activeLocomotives` then
the layout monitor. That is the same order as the ordinary release at `:5726-5728`. All twelve
`synchronized (this.activeLocomotives)` blocks in the file sit inside methods that are not themselves
`synchronized` on the layout (`runLocomotives`, `executeTimetableInternal`, `executePathInternal`,
`executePath`), so nothing takes the pair the other way. `isRunning`, `getActiveLocomotives` and
`getActiveAccs` are lock-free reads over concurrent collections. `B1` is about how long the wait can
be, not about a cycle.

### D3 — the new test discriminates on both halves

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading; not re-run |

`testAFailedPathStopsTheRunAndGivesTheTrackBack` throws from `CB_ROUTE_START`, after
`configureAndLockPath` has locked the path. `assertFalse(e.isOccupied(loc))` reaches
`Edge.isOccupied`'s `occupancy > 0` arm (the end point still holds `loc`, so the endLocomotive arm
does not short-circuit), and occupancy is only zero because `unlockPath` ran — so removing the call
fails it. `assertFalse(layout.isRunning())` fails if `stopLocomotives()` is removed, since `running`
would still be true. Both MUTATION claims hold.

What it does not cover: `atomicRoutes` is left at its default `true`, so the branch `A1` is about is
not exercised; and nothing asserts where the locomotive is recorded afterwards. On the atomic branch
`unlockPath` clears the first edge's start unconditionally (`:3316`) and leaves the last edge's end,
so after a mid-path failure the model records the train at the destination it never reached — which
is a change from the old behaviour, where it was recorded at the start as well. `getLocomotiveLocation`
(`:3722-3733`) returns the first match over a `HashMap`, so both states are unsatisfactory; the new
one is deterministically wrong rather than ambiguous. Not raised as a finding because the message now
tells the operator to look before starting, which is the mitigation Adam asked for.

### D4 — `VD9-B1`'s comment repair is complete

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading |

`Layout.java:2955-2977` now carries the whole retraction — *"and releasing one is NOT free, which the
rest of this sentence used to claim"* — followed by why the over-release is nevertheless unreachable,
and ends at `edgesLocked++` with no dangling fragment. The truncated sentence above it is complete
again: *"the single edge the recovery provably could not reach."* It agrees with `Edge.release`'s own
javadoc at `Edge.java:450-470`, which quotes and retracts the same claim. Nothing here still says the
opposite.

### D5 — `VD9-B6`'s replacement account of the reversal guard is true

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading both methods |

Traced against the code rather than the summary:
`Layout.measuredRoomToReverseInto` (`:6308-6361`) walks the path backwards, stops at the first edge
where `crossesASwitch()`, requires `getLength() > 0` on each earlier edge and `getRoomAtTheEnd() >= 0`
at the switch edge — so an unmeasured stretch beyond the last switch blinds nothing.
`GraphReducer.roomAfterTheLastSwitch` (`:1101-1126`) seeds `measured` from `getTileLength(end)` and
returns `-1` if any tile in the stretch is unmeasured — so without the reversal square's own length
the guard returns null, which is blind, not under-counting. `unmeasuredAfterTheLastSwitch`
(`:1145-1167`) is the same walk returning the tiles. Every claim in the replacement paragraph holds,
including the two remaining directions of disagreement it names. Only the formatting is damaged
(`C3`).

### D6 — `VD9-B7`'s cross-reference resolves

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by grep |

`AutonomyChecks.java:602` now points at `AutonomySession.reversalsWithoutLength` instead of carrying a
second copy of the claim. That method exists at `AutonomySession.java:2043` and is the one whose
javadoc carries the corrected account. The duplicate claim is gone from `AutonomyChecks`.

### D7 — `VD9-C3`'s message correction matches the code, in all eight bundles

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading `edgesAreEmpty` and all eight bundles |

`LayoutDiagram.edgesAreEmpty` (`:529-543`) scans column `sx - 1` and row `sy - 1` and nothing else, and
`trimEdges` (`:555-575`) removes exactly those two. All eight bundles now name the rightmost column and
the bottom row and none mentions the top row. Both readers of the key — `LayoutEditor.java:4732` and
`LayoutEditorRightclickMenu.java:470` — are the guard and the affordance for the same question, and
both now say the same thing.

### D8 — `VD9-B2`'s trap guard is in place in both runners

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading; the shell semantics were confirmed by round one |

`docs/tools/one.sh:305-311` and `docs/tools/battery.sh:389-395` both set `DONE=""` and open
`on_the_way_out` with `if [ -n "${DONE:-}" ]; then return; fi`, and both are armed before the traps at
`:346`/`:430`. The `exit 130` path therefore runs the body once. The `set -u` concern the fix records
is real and is closed by the same ordering.

### D9 — the new surface rule's brace-walk finds the real extent of every method it examines

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by re-implementing the algorithm and printing its extents |

The question was whether counting `{` and `}` over raw lines — braces in strings and comments included
— could truncate a body (a false failure) or over-extend it (swallowing a neighbour's
`setupChanged()`, which would hide a missing call). Re-running the exact algorithm over
`AutonomyEditorPanel.java` at HEAD gives seven matches in seven distinct methods, each with the correct
declaration line and the correct closing brace:

```
setTurning        2920 -> 2927     promptPercent   3078 -> 3108
setUsage          2964 -> 2974     promptHome      3564 -> 3627
setStation        2976 -> 2996     promptLocomotives 3997 -> 4075
promptNumber      3006 -> 3034
```

No truncation and no over-extension. The scan's weakness is its *reach* (`B2`), not its walk.

### D10 — `NodeGroup` is AND in both engines, and the 2^20 bail-out is loud

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by executing the Python against the Java semantics |

`NodeGroup.evaluate` (`src/org/traincontrol/base/NodeGroup.java:29-40`) returns false on the first
false child and true otherwise — AND, and true for an empty list. `compare-conditions.py:69-81` does
the same, including the empty case. A two-element group's truth table matches `NodeAnd`'s and differs
from `NodeOr`'s. Single-element groups are the identity in both, which is what `normalize`
(`NodeExpression.java:86-125`) produces, and a bare `(a OR b) AND c` compares equivalent to its
normalized form — the case the truth-table approach exists for.

The over-20 path is not silent: `:155-157` records the condition, `:171` prints the count, `:180-181`
prints `UNCOMPARED: <name> (too many atoms (21))`, `:186` returns 1, `run.sh:128` captures it and
`:148-151` forces the run's exit status to 1. Verified with a constructed 21-atom chain. Real
conditions max out at four distinct leaves, so the cap never bites today.

### D11 — `atom_of` does not collide on any leaf the driver can emit, and the 39 is right

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by executing the committed module on leaves built from the real writers |

The hypothesis was that two different sensors could produce the same atom name. They cannot: the
lowercase key names `atom_of` looks for are not the ones the leaves carry, so the loop falls through to
`rc["state"]` and formats the entire state dictionary into the atom. Sensor 9 against sensor 10,
sensor 9 true against false, and `TYPE_FEEDBACK` against `TYPE_ACCESSORY` at one address all produce
distinct atoms. The 39 real conditions use `TYPE_FEEDBACK` (56 leaves), `TYPE_ACCESSORY` (14) and
`TYPE_AUTO_LOCOMOTIVE` (2), and none collide.

Two latent collisions exist and are unreachable, because `NodeExpression.fromJSON`'s `default:` throws
(`:142-146`) and the driver records `REFUSED` (`ConditionParityDriver.java:108-112`) which the Python
escalates: an unknown node type collapses to `type=<T>`, and an empty `command` object collapses to
`type=NodeRouteCommand` for every such leaf. Worth a comment; not a live defect.

The count checks out independently: `cs2_sample_layout/config/gleisbilder/routes.json` holds 85 routes
of which exactly 39 carry a `conditions` key, and `test/operator_layout/…` is identical. "Identical,
not merely equivalent" is canonical-form identity (`json.dumps(sort_keys=True)`) rather than byte
identity of the emitted JSON — a slight overclaim in wording that does not affect the conclusion.

### D12 — `testEditorSurfaceRules` passes

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by execution |

`sh docs/tools/one.sh regression.testEditorSurfaceRules` → 37 tests run, 0 failures, 0 skips. The new
`testEveryDoorThatWritesTheSetupAnnouncesIt` is among them and is green — correctly, for the seven
methods it can see.

### D13 — `VD9-C15`'s rename left nothing stale

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by grep over the whole tree |

`testEverySandboxIsClosedOnEveryPath` appears only twice at HEAD, both in
`2026-09-03-validation-of-the-day.md` quoting the finding itself.
`docs/reviews/2026-09-03-test-suite-audit.md:1767` was updated to the new name in the same commit. No
code, comment, `.form` file or changelog entry still names it.

### D14 — `VD9-C20`'s counting change is mechanically right

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading |

`AutonomySession.java:1840` builds a `takenFrom` map, `:1864-1871` counts per page in the first loop,
and `:1927-1931` adds each page's captions **after** its `saveChanges` returns and inside the `try`, so
a page write that throws is excluded. No double count and no off-by-one: the write loop's guard
(`:1902-1907`) is identical to the count loop's, so `changed` and `takenFrom.get(page) > 0` agree, and
the `if (took != null)` is dead-defensive rather than a hole. `takenFrom` is keyed by `LayoutDiagram`
identity, so two pages with the same name cannot merge. Both counters reset on every open at
`:139-140`. The problem is the sentence the number is attached to (`C6`), not the number.

---

## What this pass did NOT examine

Said plainly, because a validation document that does not say where it stopped is not one:

- **The test battery was not run.** Two classes were run alone. Every other test in the tree is
  unexamined by this pass, and the last full green predates `c892ec03` — see `B3`.
- **`ConditionParityDriver` and `compare-conditions.py` were never run against the real jars.** The
  Python logic was exercised on constructed inputs; no 2.8.1 jar was downloaded, no `setup-env.sh` or
  `run.sh` invocation was made, and the claim that the released 2.8.1 jar lacks `normalize` rests on
  git history rather than on opening the jar.
- **Nothing was tested on a railway.** `A1`, `B1` and the placement question in `D3` are all read from
  the code. `A1` in particular describes a sequence that needs a mid-path failure in non-atomic mode
  with a second train claiming a lock edge in the window; nothing here demonstrates it happening.
- **`A2`'s alternative causes were not chased.** The finding establishes that the stated mechanism is
  wrong. It does not establish what actually ate Adam's home setting, if anything did.
- **`fcc11670`'s 28 disposition rewrites were not each read against their finding.** Only the summary
  table was counted (`C4`).
- **`docs/manual-tests/tests.md` and `issues.md`** were read only where `MT-246` touches them. The
  other entries added or amended today were not checked.
- **The eight-language bundles** were checked only for the two keys the day's commits changed, and
  only for escaping, apostrophes and key presence — not for meaning. `B4` reports letters, not
  translation quality.
- **`a4f5990e`'s "seventeen B rows / nine bodies" arithmetic** was not recounted.
- **The 13 `D` findings of `VD9`** were not re-derived. This pass assumed them and looked only at the
  eight B and twenty C fixes.
- **Everything before `abd377b3`.** Two findings here (`C15`, and `D3`'s note on recorded position)
  touch code older than the window, and are flagged as such.
