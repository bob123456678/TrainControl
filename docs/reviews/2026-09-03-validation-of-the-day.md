# Validation of 2026-09-03 — the eighteen commits from `abd377b3` to `fd7c4dd8`

**Status:** open

**Every finding in it is dispositioned as of 2026-09-03 evening** - all eight Bs and all twenty Cs
fixed, the thirteen Ds confirmations that needed nothing. It stays `open` until a second validation
pass has read those fixes, because three of round one's Bs were defects in *corrections*, and the
corrections to those are the least-reviewed code in the tree.

**Prefix:** `VD9`. Cite findings from here as `VD9-B1`, `VD9-C4`, and so on.
(`DAY DY3 IPR LE R28 RC REL RG3 RGN SG TSX SVN V31 V32 V33 MT` are in use elsewhere.)

**Reviewed:** branch `autonomy-diagram-r0` at `fd7c4dd8` (tagged `v3_0_0_rc9`), 2026-09-03.
Eighteen commits, `abd377b3..HEAD`. Nothing was changed by this pass; the only artefact is this file.

**Method.** Adversarial re-reading of each commit against the code at HEAD, asking one question of
each: *is the claim in the commit message and in the review disposition actually true of the code
now?* Four failure shapes were hunted specifically, all of which this repository has produced before:
a fix that does not fix on every path the finding named; a test that cannot tell the fix from its
absence; a fix worse than the defect, or a rule that lost its precondition on the way to a new site;
and prose — comment, javadoc, changelog, or disposition sentence — that the code refutes. Six of
today's commits are *about* the fourth shape, and they were held to their own standard.

**No test battery was run** (one had already run twice today) and no test class was run. Every
finding below was settled by reading, with two exceptions marked **needs execution**. Shell semantics
in `VD9-B2` were confirmed with throwaway scripts outside the harness, not with the harness.

**Nothing in this pass is graded A.** No finding here loses layout data or produces wrong behaviour on
the railway that the day's own commits did not already leave in that state. The eight B items are
fixes that do not reach a path their own prose claims, one test that cannot fail against the
alternative its disposition names, one shell trap that runs its body twice, one correction that
replaced a true-ish sentence with two false ones, one correction applied at the wrong site, and one
user-facing promise about a backup that the code does not make.

---

## Summary

| | | |
|---|---|---|
| **B1** | `REL-C16`'s comment fix left the retracted sentence's tail in place, above the line it was about | open |
| **B2** | Both runners' `INT`/`TERM` trap fires `on_the_way_out` twice: the reaper runs twice and the live-layout alarm double-prints | open |
| **B3** | `SVN-B8`'s "correct rather than lucky" is neither, and the half of the finding that actually holds is the unfixed one | open |
| **B4** | `TSX-C20`'s "cannot be set from `build.xml`" is false, and the false premise is now a comment in `build.xml` | open |
| **B5** | `SVN-B8`'s test passes against the very fix its disposition says it rules out | open |
| **B6** | `SVN-B1` replaced one wrong sentence with two: the guard *is* blind on an unmeasured reversal square, and an unmeasured stretch *earlier* does not blind it | open |
| **B7** | `SVN-B1`'s refuted sentence still stands, more strongly, at the sibling that actually builds the notice | open |
| **B8** | `RGN-B1` promises a `.bak` for every migrated page, in the Readme and in the log; `saveChanges` writes one only if there is not one already | open |
| **C1** | `IPR-B2`'s reachability screen looked for the wrong thing; Adam's `routes.json` *does* hold a `NodeGroup` | open |
| **C2** | `IPR-B2`'s central sentence is refuted by the counterexample printed two lines below it, in four places | open |
| **C3** | `REL-C12` corrected the two edge messages nothing reads and left the one everybody sees still naming the top row | open |
| **C4** | `IPR-B4`: `contentOf`'s javadoc now says the opposite of what `contentOf` does | open |
| **C5** | `IPR-B4`: two of the "three assertions" carrying the fix cannot fail | open |
| **C6** | `SVN-B11`: the second assertion of the second test cannot fail either way | open |
| **C7** | Both runners' trap comment claims safety by defaulting; the safety is actually by arming order | open |
| **C8** | `test/README.md`'s `ant test` paragraph was falsified by `TSX-C20` in the same round, and `TSX-C20` points the reader at it | open |
| **C9** | `SVN-B9` repairs the note through the one write primitive this class documents as failing under the lock it is written for, silently | open |
| **C10** | `TSX-C16` pasted `setup-env.sh`'s explanation into `run.sh`, where it describes nothing that is there | open |
| **C11** | `MT-265` step 1 asks for a `Ctrl+Z` after a gesture that pushes no undo point | open |
| **C12** | Stale line citations written into permanent source and docs, in four places | open |
| **C13** | `REL-C13`'s replacement javadoc says four kinds are absent; nine are | open |
| **C14** | `TSX-C17` is dispositioned fixed with its third clause unfixed | open |
| **C15** | `testEverySandboxIsClosedOnEveryPath` enforces "inside a `try`", which is not what its name or its message promises | open |
| **C16** | `REL-C15` orphaned `captionsOnPage`'s javadoc and left three documents asserting it has no caller | open |
| **C17** | `SVN-B8`'s disposition says the test walks the real pair from `setup.json`; the test loads no `setup.json` | open |
| **C18** | `RGN-B2` says "all three now say the same thing"; three code sites still say the s88 door stops | open |
| **C19** | `RGN-B1`'s test asserts a second half that cannot fail, and the disposition counts it as coverage | open |
| **C20** | `RGN-B1`'s caption count is incremented before the page write, and the page list is keyed by page name rather than filename | open |
| **D1** | `SVN-B11`'s two fixes do not interact badly — eight sequences traced | closed |
| **D2** | `IPR-B2`'s fix is correct on every shape the writer can produce | closed |
| **D3** | `REL-C15`'s two deletions are genuinely caller-free, `.form` and reflection included | closed |
| **D4** | The eight message bundles are ASCII and consistently changed | closed |
| **D5** | `SG-B5`'s `pathPreference` correction is true of the code | closed |
| **D6** | `TSX-C16`'s sweep is complete: no `$REPO/tools` path survives | closed |
| **D7** | A false example was asserted at 08:55 and retracted at 09:15 the same morning | closed |
| **D8** | The operator's two unpaired `disabledLinks` entries are legitimate, not the corrupt shape | closed |
| **D9** | `SVN-B9`'s test discriminates, and re-writing the note on a *deletion* is correct | closed |
| **D10** | The registry ordering `SVN-B8` relies on does hold | closed |
| **D11** | `IPR-B4`'s arithmetic is sound and costs no resolution | closed |
| **D12** | `RGN-B2`'s behavioural description is exactly right, in all four respects | closed |
| **D13** | `SVN-B1`'s change to `testJavadocsAreAttached` is a tightening, not a weakening | closed |

---

## B — medium

### B1 — `REL-C16` deleted two lines of a three-line sentence, and the retracted claim is now the last thing a reader sees before the code

| | |
|---|---|
| **Disposition** | fixed - the whole sentence restored, and the two lines that were left dangling with it |
| **Confidence** | confirmed by reading; the diff hunk is quoted below |

`src/org/traincontrol/automation/Layout.java:2974`:

```java
                // needs a throw INSIDE the lockEdges loop - a ConcurrentModificationException on
                // `this.lockEdges` mid-iteration, which nothing in the tree produces.
                // clear does nothing.
                edgesLocked++;
```

The sentence `REL-C16` set out to remove was *"setUnoccupied on an edge that is already clear does
nothing"*, spread over three source lines. The hunk in `0dc7263e` replaced the first two and left the
third. So a bare, unqualified `// clear does nothing.` now sits immediately above `edgesLocked++`, at
the exact site the whole finding was about — which is where a reader of that code lands.

The same hunk also truncated the sentence that was being *kept*: `:2957-2958` now reads
*"…leaves occupied and outside the release -"* followed by a new paragraph. The completion,
`// the single edge the recovery provably could not reach.`, was deleted with the retracted claim
either side of it.

This falsifies the `REL-C16` disposition at `docs/reviews/2026-09-03-release-review.md:1411`
(*"`Layout.configureAndLockPath`'s comment now says what `release()` does"*). It says that, and then
also still says the opposite, in three words, last.

Graded B rather than C because this is not a typo: it is a fix that does not fix, in the one commit
of the day whose entire subject is sentences that had stopped being true.

### B2 — both runners' `INT`/`TERM` trap runs `on_the_way_out` twice

| | |
|---|---|
| **Disposition** | fixed - `DONE` guard, re-measured with a throwaway script: one call, not two |
| **Confidence** | confirmed by reading, and the shell semantics confirmed by executing the same eight-line shape in this Git-Bash (not by running the harness) |

`docs/tools/one.sh:329-330` and `docs/tools/battery.sh:413-414`:

```sh
trap 'on_the_way_out; exit 130' INT TERM
trap 'on_the_way_out' EXIT
```

`exit 130` inside the signal handler runs the `EXIT` trap, which calls `on_the_way_out` a second
time. Nothing disarms the `EXIT` trap on that path and `REPORTED` is still empty, so:

- `powershell.exe … reap.ps1` is launched **twice** (three times in `battery.sh`, with `VD9-C…`'s
  duplicate post-loop reap — see below);
- the `*** THIS RUN WAS STOPPED, AND … CHANGED WHILE IT RAN ***` block prints **twice**, which is the
  most alarming message the harness has and the one whose credibility matters most;
- `release_the_lock` and `rm -rf "$BUILD"` run twice, harmlessly.

`TSX-C14`'s disposition records the trap as exercised — *"a run sent `TERM` mid-class leaves zero
`java.exe` with `one-` in its command line"*. That experiment cannot detect this: a double reap still
leaves zero.

Either `trap - EXIT` as the first statement of the `INT`/`TERM` body, or a `RAN=1` re-entry guard at
the top of `on_the_way_out`.

Related, same commit, lower cost: `battery.sh` now reaps at `:647-657` *and* at `:667-680`. `TSX-C14`
said *"the fix is to swap two statements"*; a duplicate block was inserted before
`live_after=$(fingerprint)` and the original left after it. The net behaviour is right; the comment at
`:667-675` (*"the last one is not cleaned up at all"*) now justifies work the block above it has
already done.

### B3 — `SVN-B8`'s "correct rather than lucky" is neither, and the half of the finding that actually holds is the half left unfixed

| | |
|---|---|
| **Disposition** | fixed - the disposition now says the checkbox pushes no undo point, and MT-265 step 1 has the order right |
| **Confidence** | confirmed by reading; the undo-point enumeration is exhaustive |

`AutonomyCompanionStore.putPairedMembersBack`, `:3049-3052`:

> *"The pairing is read as it stands NOW, and that is correct rather than lucky: `portals` is restored
> before `disabledLinks` in the registry order below, so by the time this runs the pairing is already
> the one the snapshot was taken under."*

The ordering claim is true (`kept()` at `:4481-4505` is a plain `ArrayList`, `portals` at index 7,
`disabledLinks` at index 11; no sort, no stream, no `HashMap` anywhere in the file). What is wrong is
that this is the ordering the removal pass *does not* want. The removal pass walks the **live**
post-gesture set and has to find the far end of each member; restoring `portals` first replaces the
live pairing with the snapshot's. Where the pairing is younger than the snapshot, the far end is
missed:

1. any diagram gesture on page 1 → `snapshotLayout()` pushes `snapshotPage("1")`; `portals` empty;
2. right-click → Pair link (`AutonomyEditorPanel.java:4318`) → `here@1 ↔ there@5`;
3. right-click → untick Use link (`AutonomyEditorPanel.java:1548`) → `disabledPortals = {here, there}`;
4. `Ctrl+Z` → `restorePage` restores `portals` to empty *first*, so `getPortalPartner(there)` is null,
   `there` is not removed, and `disabledLinks` reaches disk as `["5:15,5"]` — the one-ended shape
   `TileGraph.portalClosed:495-499` says has no migration.

Not a regression: the pre-fix code did the same thing here. But the sentence claims the ordering makes
the fix correct, and on this path it is what makes it wrong.

Compounding it, the disposition
(`docs/reviews/2026-09-01-week-of-commits-review.md`) opens *"**fixed, and it fires on the checkbox
itself — that half of the finding is settled by the test**"*. The finding's open half was: *"Not
confirmed whether the checkbox itself pushes an undo point; if it does not, the asymmetry still fires
on any `Ctrl+Z` whose snapshot predates the toggle."* Reading settles it the other way. `previousCaptions`
— the stack `restorePage` is driven from — is pushed only by `snapshotLayout()` (`LayoutEditor:5129`)
and by `redo` (`:5246`), and all eighteen `snapshotLayout()` call sites are `LayoutEditor` diagram
gestures. **The checkbox pushes no undo point, and neither does pairing.** So the case that holds is
the one the finding left open, the test does not touch it (it drives `store.snapshotPage`/`restorePage`
directly and never opens an editor), and it remains unaddressed.

### B4 — `TSX-C20` decided `-Xmx` cannot be set from `build.xml` on a premise `nbproject/build-impl.xml` refutes, and wrote the premise into `build.xml`

| | |
|---|---|
| **Disposition** | fixed - `-Xmx` IS reachable via `run.jvmargs`; not set because that property also feeds Run and Debug |
| **Confidence** | confirmed by reading `nbproject/build-impl.xml` directly |

`build.xml:116-120`:

> *"Through `test-sys-prop.`, which the TestNG macro in nbproject/build-impl.xml maps onto system
> properties of the forked JVM. That indirection is the reason it can be done here at all: **the macro
> takes exactly one jvmarg** and NetBeans owns that file, so -Xmx and the skipped-class rule … still
> cannot be set from build.xml."*

The macro does not take exactly one jvmarg:

- `nbproject/build-impl.xml:631-632` — the `testng` macro has `<jvmarg line="${endorsed.classpath.cmd.line.arg}"/>`
  **and `<customize/>`**;
- `:663-675` — the TestNG `test-impl` forwards its implicit `customize` element straight into
  `<j2seproject3:testng><customize/></j2seproject3:testng>`;
- `:678-693` — `j2seproject3:test` already fills that `customize` with
  `<jvmarg line="${run.jvmargs}"/>` and `<jvmarg line="${run.jvmargs.ide}"/>`;
- `:203` — `run.jvmargs` is defaulted to `""` inside the `-do-init` target, so a top-level
  `<property name="run.jvmargs" value="-Xmx512m"/>` in `build.xml` is set at parse time and wins on
  Ant's property immutability.

Cleaner still, `build.xml` owns its own `test-one-class` macrodef (`:126-133`) and can call
`j2seproject3:test-impl` with a nested `<jvmarg value="-Xmx512m"/>`, which reaches the fork and leaves
`ant run` alone.

So leg 2 of `TSX-C20` was closed as unreachable when it is reachable, and the false reason is now a
permanent comment telling the next reader not to look. This is the shape the day's own `REL-C12`–`C16`
commit exists to remove, arriving in the commit two hours later.

*(Leg 1 checks out: `build-impl.xml:624-627` really does carry the `<propertyset>` with the
`test-sys-prop.` mapper, so `anyReceivePort` genuinely reaches the fork.)*

### B5 — `SVN-B8`'s test passes against the fix its own disposition says it exists to rule out

| | |
|---|---|
| **Disposition** | fixed - a link on unrelated pages must now survive, and the clear-the-set fix fails it |
| **Confidence** | confirmed by reading; traced by hand against the alternative implementation |

`test/core/testAutonomyDiagramStore.java:2213-2243`, and its javadoc:

> *"Both directions are asserted, because a fix that simply cleared the set would pass the first."*

Replace `putPairedMembersBack`'s body with `into.clear(); if (was != null) into.addAll(was);` — the
"fix that simply cleared the set" — and trace the test: `pushed` captures `disabledLinks = {}`;
`setPortalDisabled(here, true)` makes the live set `{here, there}`; `restorePage` clears it and adds
back nothing. `isPortalDisabled(here)` and `isPortalDisabled(there)` are both false. **Both**
assertions pass. That alternative wipes every other page's disabled links, which is the thing the
paired walk exists to avoid, and nothing in the test, in `testAutonomyStoreSettingsMatrix:343-350`, or
anywhere else asserts that an unrelated third-page disabled link survives a `restorePage`.

Separately, the second assertion cannot fail while the first passes. The `portals` snapshot is taken
*after* `pairPortals`, so the pairing survives the restore; with the pair intact,
`isPortalDisabled(here)` and `isPortalDisabled(there)` are the same expression
(`contains(here) || contains(there)`, `:1096-1115`). It is the control, not the variable — and the
stated reason for its existence is the claim above, which is false.

The mutation the disposition names *is* real: constructing `disabledLinks` unpaired does fail the
first assertion. So the test discriminates the fix from *doing nothing*. It does not discriminate it
from *the wrong fix*, which is what its javadoc claims.

### B6 — `SVN-B1` replaced one wrong sentence with two wrong sentences

| | |
|---|---|
| **Disposition** | fixed - traced against the two methods; both replacement sentences were wrong and the second was backwards |
| **Confidence** | confirmed by reading the guard and the reducer; the deciding lines are quoted |

`SVN-B1` was right that *"These are the squares where that guard is blind"* was wrong. Both
replacements are also wrong, and in the case the rule was built around.

`src/org/traincontrol/automationui/AutonomySession.java:2025-2027`:

> *"The guard is blind on a PATH, not on a square: `isPathClear` walks every edge of the route and
> gives up the moment one has no length, so an unmeasured stretch anywhere earlier blinds it on every
> path through there."*

It does not walk every edge. `Layout.measuredRoomToReverseInto` (`Layout.java:6311-6327`) walks the
route **backwards and stops at the first edge that crosses a switch**, and the comment eight lines
above it says so in as many words: *"Only the edges it actually counts are asked, so a route whose
earlier half is unmeasured is now judgeable where it was not."* An unmeasured stretch *before* the last
switch does not blind it. The same javadoc says the rule is "the stretch after the last switch"
correctly, eight lines further down, so it now contradicts itself as well as the code.

`AutonomySession.java:2031-2035`:

> *"a reversal square with no number of its own, arrived at over measured track, still has a positive
> edge — the guard is not blind there, it is UNDER-COUNTING by exactly the room the train comes to rest
> in."*

For the case the whole rule exists for — reversing across a switch — it is blind, not under-counting.
`GraphReducer.roomAfterTheLastSwitch:1101-1119` sets `boolean measured = atTheEnd > 0` from the
reversal tile's **own** length and returns `-1` when it is unmeasured; `measuredRoomToReverseInto:6320`
turns any `getRoomAtTheEnd() < 0` into `return null`. So a reversal square with no number of its own,
reached over fully measured track through a switch, makes the guard decline. The `sumLength(path) +
lengthOf(tile)` under-counting story holds only for an edge that crosses **no** switch
(`Integer.MIN_VALUE`), which is the minority case and not the one the notice is about.

The user-facing notice string is unaffected and correct
(`autosetup.ui.checkReversalNeedsLength`, *"nothing can tell whether a long train would stand across
the switch behind it"*). It is the correction that is wrong. Both paragraphs are repeated verbatim in
the disposition prose added to `docs/reviews/2026-09-01-week-of-commits-review.md` (the *"does leave the
guard sighted … Not blind, and not right either"* paragraph).

This is the shape where a correction is worse than what it corrected: the removed sentence was true of
the switch-crossing case, which is most of the population the notice names.

### B7 — `SVN-B1`'s refuted sentence still stands, more strongly, at the site that builds the notice

| | |
|---|---|
| **Disposition** | fixed - `AutonomyChecks` points at the one account instead of carrying a second copy |
| **Confidence** | confirmed by reading |

`src/org/traincontrol/automationui/AutonomyChecks.java:597-600`:

> *"So the squares this names are **exactly** the ones where the guard cannot do its job: somewhere a
> train turns round, with nothing recorded about how much room it has."*

That is the claim `SVN-B1` exists to retract, stated with an "exactly" the retracted copy did not have,
in the class that actually raises the notice. `0997379e` corrected the `AutonomySession` copy and its
commit message says *"Both are written at the method now"* — meaning the two new paragraphs, not the
two sites. The finding is dispositioned closed.

This is the repository's most-repeated mistake and it has its own rule in
[README.md](README.md): *"When you fix a call site, grep for its twins before closing the finding."*
One `grep` for "where the guard" finds it.

(Whether the corrected text is itself right is `VD9-B6`; that is a separate question from whether it
reached both copies.)

### B8 — `RGN-B1` promises a `.bak` for every migrated page; the code writes one only if there is not one already

| | |
|---|---|
| **Disposition** | fixed - the log line and the changelog say "the first time this version rewrote it" |
| **Confidence** | confirmed by reading, and by the two `.bak` files already sitting in the operator's own layout |

`Readme.md:377`, and the same sentence emitted to the log for every migrated page at
`src/org/traincontrol/gui/TrainControlUI.java:2660-2665`:

> *"A copy of each page as it was is kept beside it with a `.bak` extension"*

`LayoutDiagram.saveChanges` (`src/org/traincontrol/base/LayoutDiagram.java:470-483`):

```java
Path backup = newFilePath.resolveSibling(newFilePath.getFileName() + ".bak");

if (Files.exists(newFilePath) && !Files.exists(backup))
```

— and a failed `Files.copy` is swallowed. The internal javadoc directly above hedges correctly
(*"A copy of what was there is kept beside it **the first time a page is rewritten** … Only the first:
the point is the state before this build touched it, not before the last save"*), and
`AutonomySession.java:249-251` hedges the same way. Only the two user-facing sentences drop the
qualifier — the two `RGN-B1` was written to add.

It is not hypothetical. Five other call sites rewrite pages before autonomy is ever opened
(`LayoutEditor.java:5988`, `:6904`, `LayoutPageEdit.java:158`, `TrainControlUI.java:22346`, `:22526`),
and `cs2_sample_layout/config/gleisbilder/` already holds `1 - Main.cs2.bak` and `2 - Bottom.cs2.bak`.
Any page with a `.bak` beside it from an earlier save is rewritten by the migration with no backup of
its pre-migration state, while the log tells the operator one was taken. The whole point of `RGN-B1`
was that a one-way edit to the user's own files has to say what it did; on the recoverability half it
says more than it does.

The smaller fix is the honest sentence — *"kept the first time this build rewrites a page"* — in both
places, since the design decision itself is deliberate and defensible.

---

## C — low

### C1 — `IPR-B2`'s reachability was screened on the wrong property, and the screen's factual claim is false

| | |
|---|---|
| **Disposition** | fixed - re-screened by parsing under both engines, which is a better screen than either earlier one |
| **Confidence** | confirmed by reading `cs2_sample_layout/config/gleisbilder/routes.json` (read-only) |

`docs/reviews/2026-08-31-independent-review.md`, `IPR-B2`'s disposition:

> *"Adam has no `NodeGroup` in `routes.json`, and no production caller of `fromTextRepresentation`
> remains, so no new tree of this shape can be made — only a stored one met."*

He has two. `cs2_sample_layout/config/gleisbilder/routes.json` contains 32 `NodeAnd`, 1 `NodeOr`, 72
`NodeRouteCommand` **and 2 `NodeGroup`** — all in one route, *Auto Emergency Stop Main BC*, whose
condition tree is `Or(Group[And(C,C)], Group[And(C,C)])`. The same file is copied verbatim into four
test fixtures.

The conclusion survives, but not for the stated reason. `NodeGroup` is not the property that triggers
the defect. The rows that break the old reader are produced by a **cross-operator left nesting two
levels deep**, and `write`/`writeChild` reach that with or without a group. `Auto Emergency Stop Main
BC` writes as `A@1 AND@1 B@1 OR@0 C@1 AND@1 D@1` — a jump of exactly one, which the old and new
readers handle identically. So: not reachable on this data, correctly, by a screen that was never run.

The screen that *should* be re-run, if this is ever revisited, is: does any stored condition have an
`And` whose left child is an `Or` (or vice versa) which itself has a cross-operator left child?
Nothing in `routes.json` does; the deepest shape there is `And(And(And(C,C),C),C)`, one operator
throughout, which writes flat at depth 0.

*(Checked and true in the same disposition: `fromTextRepresentation` has no caller in `src/` — only
`test/core/testAdvancedRoutes`, `testRouteRoundTrip` and `testRoutes`.)*

### C2 — `IPR-B2`'s central sentence is refuted by the example printed two lines below it, in four places

| | |
|---|---|
| **Disposition** | fixed - the sentence is narrower, the family is wider, and the bracket-free case is a test |
| **Confidence** | confirmed by reading; a second counterexample constructed |

`src/org/traincontrol/base/ConditionOutline.java:236-241`, the commit message of `2e96769f`, the
`IPR-B2` disposition, and `test/core/testConditionOutline.java:449` all carry the same sentence:

> *"The two are the same number for every outline that steps down one level at a time, which is every
> outline this editor writes."*

and then immediately give `3 or ((1 or 2) and 4)`, which this editor's own `write` produces as
`0, 0, 2, 2, 2, 1, 1` — a step of two, not one. If it were every outline the editor writes, `IPR-B2`
would not exist. Whatever reading is given to "steps down one level at a time", the example on the
next line satisfies the qualifier and contradicts the conclusion.

The author asked for a second shape where the two differ. `((1 or 2) and 4) or 3` —
`NodeOr(NodeAnd(NodeOr(1,2), 4), 3)` — writes as `1@2 OR@2 2@2 AND@1 4@1 OR@0 3@0`, and the old reader
turns it into `((1 or 2) or (4)) or 3`, dropping the AND, exactly as in the named case. The family is
"a cross-operator child that is itself the left child of a cross-operator parent", and it needs no
`NodeGroup` in the tree.

The sentence that survives checking is narrower: *the two are the same number wherever the else-branch
meets a row exactly one level deeper, which is every outline in which no level's own rows come out
after a deeper run.*

### C3 — `REL-C12` corrected the two edge messages nothing reads and left the one everybody sees still naming the top row

| | |
|---|---|
| **Disposition** | fixed - the refusal names the rows `edgesAreEmpty` actually checks, in eight languages |
| **Confidence** | confirmed by reading; the caller is quoted |

`src/org/traincontrol/resources/messages.properties:1852` and the same key in all seven other bundles:

```
layout.ui.errorEdgesNotEmpty=The rightmost column or the top or bottom row still holds track, so the diagram cannot be made smaller without losing it.
```

`LayoutDiagram.edgesAreEmpty()` (`src/org/traincontrol/base/LayoutDiagram.java:529-543`) checks column
`sx-1` and row `sy-1`, and nothing else. The top row is never inspected, so the message names a remedy
— clear the top row — that cannot lift the refusal, and names a cause that never produces it.

Unlike the two tooltip keys `REL-C12` corrected, which no `.java` or `.form` reads, this one is shown:
`LayoutEditor.java:4730-4734` puts it in a `JOptionPane` and `LayoutEditorRightclickMenu.java:470`
uses it as the greyed-item tooltip. Same eight files, same edit, same wrong-count family — the sibling
that was not swept is the one that reaches the user.

### C4 — `contentOf`'s javadoc now states the opposite of what `contentOf` does

| | |
|---|---|
| **Disposition** | fixed - the javadoc describes the branch it is attached to |
| **Confidence** | confirmed by reading |

`src/org/traincontrol/gui/LocIconCropDialog.java:1367` — *"What the frame actually covers, **at the
picture's own resolution**"* — and `:1377` — *"`@return` an image of **exactly that rectangle**"*.
After `IPR-B4` both are false for the overhang branch: the returned image is `region * shrink`, at most
`outWidth x outHeight`. The forty lines of new comment explaining the bound sit *below* the javadoc
that contradicts them.

Two neighbours in the same shape:

- `:1470-1472` (`getCroppedImage`): *"…scaled once, through the same helpers `getLocImage` uses, so a
  cropped icon and a whole-picture icon are resampled identically"*. The overhang branch is now
  resampled by `Graphics2D`, not by `ImageUtil.getScaledImage`; the subsequent `getScaledImage` call is
  an identity scale because `fit` is exactly 1.0.
- `:1404-1405`: *"The branch above needs no such bound. A rectangle wholly inside the source can only
  be as large as the source, which is linear in it and **is the picture the user opened**."* It is not:
  that branch returns `ImageUtil.toTransparentBufferedImage(getSubimage(...))`, and
  `ImageUtil.java:63-79` allocates a fresh `TYPE_INT_ARGB` `BufferedImage` at region size and draws
  into it. Peak is source **plus a second full-size copy in a different pixel format**. Still linear,
  so `IPR-B4`'s quadratic 502 MB is genuinely gone; but at 8000 x 6000 the default opening view —
  `startAtCover` puts the frame wholly inside — costs about 183 MiB on top of the source, on the EDT,
  inside the OK listener. The sentence should say "linear, and a second copy of it".

### C5 — two of `IPR-B4`'s "three assertions" cannot fail

| | |
|---|---|
| **Disposition** | fixed - it asserts a pixel now, mutation-confirmed against an unscaled offset |
| **Confidence** | confirmed by reading both return paths |

`test/ui/testLocIconCrop.java:565-568`:

```java
java.awt.image.BufferedImage icon = panel.getCroppedImage();
assertEquals(icon.getWidth(), OUT_WIDTH, ...);
```

Both return paths of `getCroppedImage` are *constructed* at the icon size —
`getScaledImage(cut, this.outWidth, this.outHeight)` (`:1490`) and
`new BufferedImage(this.outWidth, this.outHeight, …)` (`:1507`). No value of `cut` can make this
assertion fail. The aspect assertion at `:560` also passes with the fix reverted (`cut == region`,
aspect exact). Only the first assertion, at `:546`, is load-bearing; the disposition counts three.

Second, smaller: the `MUTATION` line at `:509` says reverting *"fails the size assertion below"*. Both
runners cap the heap at `-Xmx512m` (`battery.sh:482`, `one.sh:374`) and the reverted allocation is
18506 x 7120 x 4 = 502 MiB alongside an 8000 x 6000 x 4 = 183 MiB fixture. Under the gate the mutation
throws `OutOfMemoryError` inside `contentOf.invoke` — still red, so the mutation *is* detected, but by
a different mechanism, and the named assertion never evaluates. The red run quoted in the disposition
came from a larger heap than the gate uses. (`battery.sh:477` still says *"the heaviest class here
peaks nowhere near that"*; this class now holds a 183 MiB contiguous `int[]` beside a `TrainControlUI`.)

Third: nothing asserts a pixel. The one mistake the new comment explicitly names —
*"the scale goes on first"* (`:1428-1430`) — is unguarded: swapping `g.scale` and `g.translate` would
put the photograph in the wrong place and produce an image of identical dimensions, so all four
assertions stay green.

### C6 — `SVN-B11`'s second test carries an assertion that cannot fail either way

| | |
|---|---|
| **Disposition** | fixed - the assertion no implementation could fail is gone, with its reason |
| **Confidence** | confirmed by reading; both variants traced |

`test/regression/testLayoutEditorBulkEdits.testAnUndoneCutIsNotStillOutstandingOnItsEmptySquares`:

```java
assertFalse(session.getStore().isStation(at(page, 9, 3)),
    "the station was carried to the paste target even though undo had put the cut track back…");
```

With the fix, `emptyCutOrigins` returns empty and no move is made, so nothing lands on `(9,3)`. With
the fix reverted, `(5,3)` is full again, so it is excluded from `vacated` and skipped by `cutMoves` —
so nothing lands on `(9,3)` then either. There is no implementation under test for which this
assertion is false. The first assertion in the same method does discriminate, and cleanly.

### C7 — the trap comment claims safety by defaulting; the safety is by arming order

| | |
|---|---|
| **Disposition** | fixed - `$LOCK`, `$REPORTED` and `$DONE` are defaulted, so the claim is true |
| **Confidence** | confirmed by reading both scripts |

`docs/tools/one.sh:303-305` (and the same text in `battery.sh`):

> *"Written out rather than calling `reap()`, and **every variable defaulted**: this can fire before
> the line that sets any of them, and a trap that fails under `set -u` takes the tidy-up with it."*

Three references in that body are not defaulted: `rm -f "$LOCK"` (`one.sh:293`, `battery.sh:377`),
`[ -z "$REPORTED" ]` (`one.sh:311`, `battery.sh:395`). Both scripts set `set -u` (`one.sh:32`,
`battery.sh:21`), so an unbound `$LOCK` would abort the trap at that line and take `rm -rf "$BUILD"`
with it — precisely the failure the comment says it is preventing. It cannot happen today only because
`LOCK`, `LOCK_PID` and `REPORTED` are all set *before* the traps are armed. The stated invariant is
not the one the code has, which is the shape `TSX-C14` was written against.

`docs/reviews/2026-09-03-test-suite-audit.md:1798-1800` repeats the claim.

Two adjacent notes, both C and both narrow: the ownership predicate
`[ "$(cat "${LOCK:-}" 2>/dev/null | tr -d '\r\n ')" = "${LOCK_PID:-}" ]` is true when *both* sides are
empty, so it degenerates to "delete it" if `LOCK_PID` were ever empty (it cannot be, by the `case` at
`one.sh:128-130`). And in both scripts the lock is taken (`one.sh:219`, `battery.sh:332`) before the
trap is armed (`:329`, `:413`), so a `Ctrl+C` in that window leaves a lock nobody releases; the window
contains no blocking command.

### C8 — `test/README.md`'s `ant test` paragraph was falsified by `TSX-C20`, and `TSX-C20` points the reader at it

| | |
|---|---|
| **Disposition** | fixed with `C20` - the `ant test` paragraph and the property comment agree now |
| **Confidence** | confirmed by reading |

`test/README.md:61-63`:

> *"`battery.sh` also sets the `-Dtraincontrol.anyReceivePort=true` system property … `ant test` does
> not, so a class whose `@BeforeClass` fails to bind can silently test nothing."*

`2b913cdf` added exactly that property at `build.xml:122`. The `TSX-C20` disposition ends *"docs/tools/
battery.sh remains the gate, and test/README.md says why"* — sending the reader to the sentence the
same commit invalidated. The rest of that paragraph (the skipped-class difference, the fingerprint) is
still true and is reason enough for the conclusion; only the third clause has to go.

### C9 — `SVN-B9` repairs the note through the one write primitive this class documents as failing under the lock it is written for

| | |
|---|---|
| **Disposition** | fixed - written in place, which is the primitive this class measured as surviving the lock |
| **Confidence** | confirmed by reading |

`AutonomyCompanionStore.repairTheUnfinishedEditNote:1421` ends `rememberBeforeEdit(note)`, which writes
through `writeJson` (`:4569`) — `Files.move(REPLACE_EXISTING)`. Ninety lines below, `forgetBeforeEdit`
spends a fourteen-line comment on exactly this:

> *"**Written IN PLACE, not through `writeAtomically`, and that distinction is the whole fix.** … on
> Windows replacing a file needs the same DELETE access the delete above just failed to get. So the
> fallback failed in exactly the case it was written for … An independent review found it and measured
> the asymmetry that makes it fixable: in that same lock state `delete()` fails and the atomic move
> fails, but a plain truncating write succeeds."*

`rememberBeforeEdit` catches the `IOException` and returns `false`;
`repairTheUnfinishedEditNote` ignores the return and logs nothing. So under the OneDrive lock this
class treats as an ordinary Tuesday, the rename silently does not reach the note and `SVN-B9`'s defect
is back with nothing saying so. The fix is best-effort where the class's own precedent is
best-effort-then-say-so; at minimum the failed write deserves the one-line log that `dispose()` gives
the failed delete.

Not graded higher because the outcome is the pre-fix behaviour rather than a new loss, and the window
is narrow (a rename or deletion made while an edit note exists *and* the folder is locked).

### C10 — `TSX-C16` pasted `setup-env.sh`'s explanation into `run.sh`, where it describes nothing

| | |
|---|---|
| **Disposition** | fixed - `run.sh` explains `run.sh`, including what moved for `TARGET` |
| **Confidence** | confirmed by reading both files |

`docs/tools/parity/run.sh:20-26` is a verbatim copy of the comment written for `setup-env.sh`. It says
*"the jar at `$REPO/dist`, the sample layout, **the four copied files**"* and *"**the three driver
paths below** were the exception"*. `run.sh` copies nothing, downloads nothing, and has no driver
paths: its only uses of `$REPO` are `TARGET=${1:-"$REPO/../traincontrol-parity"}` (`:28`) and
`cd "$REPO"` (`:90`). The next reader looking for "the three driver paths below" will not find them.

Second, worth one line rather than a finding of its own: the correction moves `TARGET`'s default. With
`REPO` = `docs/`, `$REPO/../traincontrol-parity` resolved *inside* the repository; with `REPO` = the
repository, it resolves beside it. That is almost certainly the intent, but any existing parity
workspace at the old location is now orphaned and nothing says so.

### C11 — `MT-265` step 1 asks for a `Ctrl+Z` after a gesture that pushes no undo point

| | |
|---|---|
| **Disposition** | fixed with `B3` - the diagram edit comes first, because the checkbox pushes no undo point |
| **Confidence** | confirmed by reading; the undo-point enumeration is the same one as `VD9-B3` |

`docs/manual-tests/tests.md`, `MT-265` step 1:

> *"…open the autonomy editor on the page holding the near half, switch the link off with the
> checkbox, then Ctrl+Z. The link should be **open** again at both ends."*

The checkbox pushes no undo point (see `VD9-B3`): `previousCaptions` is pushed only by
`snapshotLayout()`, whose eighteen call sites are all diagram gestures. Performed literally, with
nothing on the undo stack, `Ctrl+Z` does nothing at all and the operator sees the link stay shut —
which reads as a failure of the fix — or, with a stack, undoes an unrelated diagram edit.

The step needs one sentence before it: *make any diagram edit first, so there is something to undo.*
With that, the sequence does exercise `SVN-B8`'s fix, because `1:10,9 ↔ 5:15,5` is already paired on
his railway and so is present in the snapshot's `portals`.

*(Checked and true: `cs2_sample_layout/config/autonomy/setup.json` does pair `1:10,9` with `5:15,5`.)*

### C12 — stale line citations written into permanent source and docs

| | |
|---|---|
| **Disposition** | fixed - cited by method name, which is the form that does not go stale |
| **Confidence** | confirmed by reading; each cited line opened |

Five, all from today:

- `src/org/traincontrol/base/CommandRow.java:240` and `:276` cite `RouteEditorFrame:3419` and
  `:3415-3419`. The code is at `RouteEditorFrame.java:3428-3432`; line 3419 is a comment about the
  commands table. `RouteEditorFrame.java` has not moved since well before the commit, so the citation
  was wrong when it was copied out of the review and into permanent javadoc.
- `docs/tools/parity/README.md` cites `Layout.java:7157-7161` for `fromJSON` reading
  `pathPreference`. It is at `:7170-7174`.
- `docs/reviews/2026-09-03-release-review.md:1372-1387`, the post-3.0.0 deletion list, cites
  `TrainControlUI` line numbers that do not resolve — `getNumLocMappings` at `:1346` (actual `:1360`),
  `withNewFirst` at `:9343` (actual `:9401`), `routeNamed` at `:25479` (actual `:25530`). The method
  names are all correct and all still uncalled, so the list's conclusion holds.
- The `RGN-B2` disposition says *"It is the rule at `:585` that generalised"*. The
  "REFUSED rather than confirmed, and refused WHOLE" rule is at `MarklinRoute.java:547`; `:585` is
  `if (conflict != null && auto)`. Its companion cite, `:618-626`, is accurate.

A line number in a review goes stale harmlessly. One copied into a javadoc does not: it outlives every
edit above it and there is no test that can notice.

### C13 — `REL-C13`'s replacement javadoc says four kinds are absent; nine are

| | |
|---|---|
| **Disposition** | fixed - nine, not four; and the unreachable arm says it is unreachable |
| **Confidence** | confirmed by counting the enum |

`src/org/traincontrol/base/CommandRow.java:280`: *"The other four kinds are still absent"*. `Kind` has
thirteen values (`:31-68`) and `canBeACondition` (`:284-288`) admits four, so nine are absent. The
dropdown is built straight from `Kind.values()` filtered by that predicate
(`RouteEditorFrame.java:3500-3502`), so there is no smaller universe being counted. "Four" was right
when the enum had seven members — the same paragraph's other number, at `:271`.
`docs/reviews/2026-09-03-release-review.md:1244` repeats it.

Two smaller ones in the same javadoc: `:242` says *"a COMMAND row of this kind is still refused until a
sensor is typed"*, and there is no such state — `canBeACommand` excludes `AUTO_LOCOMOTIVE` outright
(`:209-211`), the commands dropdown is built from that predicate, and the Kind cell is read-only for a
pre-existing row, so `case AUTO_LOCOMOTIVE: return "";` in `defaultSettingFor` is dead and identical to
`default`.

### C14 — `TSX-C17` is dispositioned fixed with its third clause unfixed

| | |
|---|---|
| **Disposition** | fixed - the fixture server is stopped in a `finally` |
| **Confidence** | confirmed by reading |

`TSX-C17` is three-part: a hardcoded 8080, `getPort()` reading a constant back, *and* *"neither of the
two classes that start it stops it in a `finally`"*. The first two are fixed —
`CS3TestServer.java:63-66` binds port 0 and reads the real port back, and both callers ask `getPort()`.
The third is not: `test/core/testParseWebServer.java:103` and `:124` still call `server.stopServer()`
as the last statement of the test body. The harm is smaller than it was (an ephemeral port rather than
8080), but an assertion above it still leaks an `HttpServer` and its port for the life of the JVM.

The audit's own table at `:60` and the disposition at `:1953` both say fixed.

### C15 — `testEverySandboxIsClosedOnEveryPath` enforces "inside a `try`"

| | |
|---|---|
| **Disposition** | fixed - renamed `testEverySandboxIsOpenedInsideATry`, which is what it checks |
| **Confidence** | confirmed by reading |

`test/regression/testSwitchingToACentralStationLayout.java:1140-1189`. The predicate is
"`LayoutSandbox.open(` is lexically inside some enclosing `try`". That is not "closed on every path":
an open inside `try { … } catch (X) { }` with no `close()` anywhere passes. The name and the failure
message both promise the stronger property.

It is a good guard and I would keep it — the brace-stack walk is right, the `checked >= 40` floor is a
real control (56 sites today), and `LayoutSandbox.open` is genuinely the class's only door (`open()`
and `open(File)` are the only public statics). The finding is that the name should say what it checks,
so that the next person does not read it as coverage it does not give.

### C16 — `REL-C15` orphaned `captionsOnPage`'s javadoc and left three documents asserting it has no caller

| | |
|---|---|
| **Disposition** | fixed - `captionsOnPage` describes its own caller, and the three assertions agree with the tree |
| **Confidence** | confirmed by reading |

Deleting `restoreCaptionsOnPage` left its partner's javadoc describing a job that partner does not do:
`src/org/traincontrol/automationui/AutonomySession.java:150-161` still opens *"For the track diagram
editor's undo… restoring captions for squares on pages nobody was editing would undo somebody else's
work"*. Its only `src/` caller at HEAD is `LayoutEditor.forgetCaptionsOutsideThePage()` (`:4676`), an
`RC-B1` shrink cleanup; the undo path uses `snapshotPage`/`restorePage`.

Three places now assert the opposite of that: `test/core/testAutonomyDiagramSession.java:4650-4652`
(*"no caller anywhere in `src/`"*, and it names the deleted method), `:4723`, and
`docs/reviews/2026-08-28-test-suite-review.md:230-238` (*"deleting both `AutonomySession` methods would
change nothing in the application"* — deleting `captionsOnPage` would now break
`forgetCaptionsOutsideThePage`).

`REL-C15` removed the half whose name matched its javadoc and left the half whose javadoc no longer
matches its name, which is the shape `REL-C15` was filed about.

### C17 — `SVN-B8`'s disposition says the test walks the real pair from `setup.json`; the test loads no `setup.json`

| | |
|---|---|
| **Disposition** | fixed - the coordinates are named as decorative |
| **Confidence** | confirmed by reading the fixture |

> *"`testUndoReopensALinkWhoseHalvesAreOnDifferentPages` walks the finding's sequence **on the real
> pair from `setup.json`** — `1:10,9` and `5:15,5`"*

The fixture (`test/core/testAutonomyDiagramStore.java:35-39`) is a fresh
`Files.createTempDirectory` with no `setup.json` in it; the test calls `store.pairPortals(here, there)`
itself. The coordinates *are* the real ones — `cs2_sample_layout/config/autonomy/setup.json` does pair
them — but they are decorative in the test, and any two page-distinct keys give the identical run. Read
literally, the sentence promises the test would notice if that pair stopped existing on his railway.
It would not.

### C18 — `RGN-B2` says "all three now say the same thing"; three code sites still say the s88 door stops

| | |
|---|---|
| **Disposition** | fixed - three more sites swept |
| **Confidence** | confirmed by grep |

The changelog sentence and the two log messages were corrected. These were not:

- `src/org/traincontrol/model/View.java:98` — *"The s88 trigger door has nobody to ask and **stops on
  its own**"*;
- `src/org/traincontrol/marklin/MarklinRoute.java:882` — quotes the `View` sentence verbatim, so the
  wrong text propagates;
- `src/org/traincontrol/gui/TrainControlUI.java:25144` — *"the s88 trigger door **stops without
  asking**"*;
- and `test/regression/testARouteDoesNotThrowSwitchesUnderATrain.java:229` carries it too.

The route does not stop. It skips its accessory commands as a group and goes on running its speeds,
functions, stop commands and any route it chains to — which is what the corrected changelog now says
and what `MarklinRoute.java:631-640` does. Same sweep, three more sites.

### C19 — `RGN-B1`'s test asserts a second half that cannot fail

| | |
|---|---|
| **Disposition** | fixed - the second open is on the same session, and fails with the reset removed |
| **Confidence** | confirmed by reading |

`test/core/testAutonomyDiagramSession.java:1290-1301` builds `AutonomySession again = new
AutonomySession(layout)` and asserts its `migratedPages` is empty and `migratedCaptions` is 0. A
brand-new session's counters are empty and zero before `open()` has run at all, so reverting the reset
at `AutonomySession.java:139-140` leaves this green. The test's own comment (*"The counters are
per-open, not cumulative"*) and the disposition (*"in its second half, that a second open reports
nothing — a cumulative counter would put the notice in front of a user…"*) both describe coverage that
is not there. The discriminating call is a second `open(...)` on the **same** session.

The first half is a real check: dropping `migratedPages.add` or `migratedCaptions++` fails it, and the
orphaned `GhostSiding` label makes the `== 1` assertion discriminate rather than count everything.

### C20 — `RGN-B1`'s caption count is taken before the page write, and the page list is keyed by page name

| | |
|---|---|
| **Disposition** | fixed - counted per page, added on the write; the sentence names pages rather than files |
| **Confidence** | confirmed by reading |

Two small ways the new counters can disagree with the sentence they are printed in.

`migratedCaptions` is incremented at `store.setCaption` time (`AutonomySession.java:1852`), in the
first loop, before `store.save()` and before any page is written. A `store.save()` failure returns
early and the log is gated off, so there is no false log from that. But if page B's `saveChanges`
throws (`:1913`), B's captions are already inside the count while B is absent from the "removed from
these page files" list and its labels are still on disk — and the log sentence binds the two: *"(N of
them) and removed from these page files: …"*.

`migratedPages.add` stores `getName()`, the page name, while `saveChanges` sanitises the filename. A
page name containing a slash or colon — the case the 2.8.0 changelog says was fixed for downloads — is
therefore reported under a name that is not the file's, which is the one thing the operator needs in
order to find the `.bak`.

Neither is a data loss. Both are the same class as `RGN-B1` itself: the report says slightly more, or
slightly other, than what happened.

---

## D — not defects

### D1 — `SVN-B11`'s two fixes do not combine badly

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by hand-tracing eight sequences; not run |

The specific worry — that consuming `clipboardWasCut` only when `!moves.isEmpty()` and returning
nothing outstanding when any tile-bearing cut square is refilled could leave a cut permanently
outstanding, or move setup that should have stayed — does not materialise. Traced: cut-then-paste-back;
paste-back-then-paste-elsewhere; cut-undo-paste; cut-cross-page-paste-return-paste-back;
cut-cross-page-paste-return-paste-elsewhere; cut-hand-redraw-one-square-paste;
caption-only cut; cut-undo-redo-paste. In every case where the flag stays `true`, `moves` is empty on
every subsequent paste, so the flag being stuck is inert. And `moveTiles(emptyMoves, overwritten)` is
*exactly* `forgetTiles(overwritten)` — `AutonomySession.forgetTiles:1537-1540` delegates to it — so the
new branch is not a behaviour change against the old `else` except in the one respect intended: a
landing square that received its own tile back is spared.

The caption-only cut is the one asymmetry, and it is defensible. If no cut square held a tile,
`clipboardCutHadTiles` is empty, the new guard cannot detect an undo, and a later paste still carries
the captions. But a cut that removed no tile is a cut `undo` has nothing to restore, so "still
outstanding" is the right answer.

Two pre-existing observations, neither today's work and neither a finding here: `resetClipboard()` and
`initCopy()` null `groupClipboard` but leave `clipboardWasCut`, `clipboardCutSquares` and
`clipboardCutHadTiles` set — harmless, because `hasGroupClipboard()` gates `pasteSelection` and
`execCopy` consults none of them. And pasting a *copy* exactly over its own origin still calls
`forgetBuiltOver` on the origin squares, wiping their setup; that is the documented pre-`LE-A1`
behaviour and outside today's scope.

Both new tests discriminate, other than the assertion in `VD9-C6`. The first mutation
(clearing `clipboardWasCut` unconditionally) makes the second paste take the `else` and call
`forgetBuiltOver` on `(5,3)`; the second (dropping the `clipboardCutHadTiles` check) makes the middle
paste a fifteen-entry move that spends the cut. Both traced end to end.

### D2 — `IPR-B2`'s fix is correct on every shape the writer can produce

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by hand-tracing three shapes both ways |

`read(rows, at, depth + 1)` is right. It always consumes at least one row (the row that sent it there
is at depth > `depth`, so it satisfies `>= depth + 1`), so there is no non-termination. Traced:
`3 or ((1 or 2) and 4)` (the named case), `((1 or 2) and 4) or 3` (`VD9-C2`'s second case), and
`And(Group([And(Or(A,B), C)]), D)` — new gives the writer's meaning in all three, old drops the inner
operator in all three. Traced also the shapes that do *not* differ: `(A or B) and C`,
`Group([Group([A,B]), C])`, `Or(Group[And], Group[And])` (the shape on Adam's railway) — new and old
agree, so nothing else moved, which is what the disposition claims and what the nine re-run classes
were checking.

One theoretical difference: an outline with a level entirely missing (rows at `d`, then `d+2`, with no
`d+1` row anywhere) gets one extra `NodeGroup` wrapper from the new reader. `write` cannot produce
that, and the extra group is semantically inert. Not a finding.

The test discriminates: reverting to `row.getDepth()` makes `meaning(toExpression(of(original)))`
differ from `meaning(original)`.

### D3 — `REL-C15`'s two deletions are genuinely caller-free

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by whole-repo search, `.form` and reflection included |

`TrainControlUI.greyOutAutonomy` and `AutonomySession.restoreCaptionsOnPage` have no caller anywhere
in tracked files. All fifteen `.form` files were checked — `TrainControlUI.form` binds
`gracefulStopActionPerformed` directly, not through the deleted wrapper — and every
`getDeclaredMethod`/`getMethod`/`invoke` site in the suite was enumerated; neither name appears.
Both were already caller-free at the parent commit, so no call site was removed alongside them and no
behaviour changed. No orphaned fields or imports: `gracefulStop` has four other live uses, `touched()`
twenty-two, and every import is still needed. The sixteen deferred methods are all still uncalled at
HEAD.

### D4 — the eight message bundles are ASCII and consistently changed

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by reading the bytes |

Zero bytes above 127 in any of the eight files; the changed lines use `\uXXXX` escapes throughout.
Exactly the same two keys changed in each file, one occurrence each, none deleted, no language left
behind, and each translation carries the count word down correctly (`drei→beiden`, `trois→deux`,
`tres→dos`, `tre→due`, `drie→twee`, `trzy→dwa`, `tre→to`). The new tooltip text matches the code:
`growEdges` → `addRowsAndColumns(1,1)` adds one column and one row.

### D5 — `SG-B5`'s `pathPreference` correction is true

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by reading |

`Layout.pathPreference` is `private volatile` at `Layout.java:223`, not static, and `fromJSON` reads it
back out of the configuration at `:7170-7174` (the README's `:7157-7161` is the citation in `VD9-C12`).
`toJSON` writes it at `:7070`. A configuration with no `pathPreference` key keeps the field's own
`RANDOM` default, as the README says.

### D6 — `TSX-C16`'s sweep is complete

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by grep over the whole folder |

No `$REPO/tools` path survives in `docs/tools/parity/` outside the two explanatory comments. `run.sh`'s
remaining `$REPO` uses (`TARGET`, `cd "$REPO"`) are both correct at three levels, and `setup-env.sh`'s
three driver compiles now read `$REPO/docs/tools/parity/`. The commit's own claim — that fixing only
the three driver paths would have left the harness broken while looking repaired — is right: they
resolved correctly *because* `REPO` was wrong.

### D7 — a false example was asserted at 08:55 and retracted at 09:15 the same morning

| | |
|---|---|
| **Disposition** | closed — the discipline working |
| **Confidence** | confirmed by reading both commits |

`5fd0e5b3` wrote up `config/autonomy/setup-before-edit.json` appearing in `cs2_sample_layout` as an
unguarded probe run. `79506af8` retracted it twenty minutes later: it was a TrainControl launched from
NetBeans at 02:19:14 and still running, writing its own unfinished-edit snapshot at 02:19:22. The rule
was kept, the false example replaced with what a leftover application actually looks like — including
the `BindException` it causes in a tool that cannot ask for another port — and the retraction is
recorded in the commit message rather than quietly amended. This is the "withdrawn findings stay in
the record" rule applied to a claim that had been in the tree for twenty minutes, and it is worth
noting because a validation pass that reports only problems cannot show it.

### D8 — the operator's two unpaired `disabledLinks` entries are legitimate

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by reading the data and the two readers |

`cs2_sample_layout/config/autonomy/setup.json` holds `disabledLinks: ["5:0,0", "5:4,6"]`, and `portals`
names neither. Given `SVN-B8`'s narrative — a one-ended `disabledLinks` is the corrupt shape
`TileGraph.portalClosed` has no migration for — this looked at first like the corruption already having
happened. It is not. Both `isPortalDisabled` (`:1096-1116`) and `TileGraph.portalClosed` (`:504-514`)
answer `true` from the direct membership test before they ever ask for a partner, and
`setPortalDisabled` writes only the one end when `getPortalPartner` is null. These are link tiles that
have not been paired to anything and have been switched out of the railway, which is a thing the editor
offers. No repair needed and no question for Adam.

### D9 — `SVN-B9`'s test discriminates, and re-writing the note on a deletion is correct

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by reading; the deletion path traced |

`testARenameReachesTheNoteOnDisk` asserts its precondition (`getLocomotiveNameAt(sensor)` is `"BR 218"`
before the rename) and that `revertUnfinishedEdit` returned true, so it cannot pass by exercising
nothing; dropping `repairTheUnfinishedEditNote` from `repairLocomotive` leaves `"BR 218"` in the note
and the final `assertEquals` fails.

The author's question was whether re-writing the note on a *deletion* does something unwanted. It does
not. `repairLocomotiveInSetup(note, from, null)` removes the placement, the home, the exclusion and the
timetable entry, which is what a revert should restore to after the locomotive is gone — leaving them
would give `parseAuto` a name it cannot resolve, which is the whole failure being prevented. The
guards are right in both directions: `!beforeEditFile().isFile()` means a deletion with no edit in
progress touches nothing and cannot *create* a note; `unfinishedEdit()` returning null (unreadable, or
a version this build refuses) means a note this build cannot trust is left exactly as it is; and
`from.equals(to)` is false for `to == null`, so the deletion is not short-circuited. The funnel really
is the right site: `repairLocomotiveOnDisk` reaches it too, so a rename made with no session open also
repairs the note. The only complaint is `VD9-C9`, about how the write is made rather than whether it
should be made.

### D10 — the registry ordering `SVN-B8` relies on does hold

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by reading |

`kept()` (`AutonomyCompanionStore.java:4481-4505`) builds a plain `ArrayList` by successive `add`,
caches it, and every consumer iterates it with an enhanced `for`. `portals` is index 7,
`disabledLinks` index 11. There is no `sort`, no `Collections.sort`, no `stream()`, no `Comparator`
and no parallel iteration anywhere in the file. The ordering is exactly what the javadoc says it is —
see `VD9-B3` for why that is the wrong ordering to want.

`snapshotOf` and `restoreTo` are symmetric: `membersOnPageEitherEnd` and `putPairedMembersBack` use the
identical predicate, so capture and removal match; the snapshot is a fresh `LinkedHashSet` rather than
a shared reference; `isOnPage(TileKey, String)` is null-guarded, so an unpaired member cannot NPE; and
a second `restoreTo` is idempotent. The new `paired` flag needs no other site — `SquareSetKept` has no
copy constructor, no `equals`/`hashCode`/`toString`, is never serialised (the flag never reaches JSON),
and the two registry objects are constructed once each and cached for the store's life.

### D11 — `IPR-B4`'s arithmetic is sound and costs no resolution

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by reading; the worked case recomputed |

`Math.min(1.0, Math.min((double) outWidth / region.width, (double) outHeight / region.height))` —
the casts bind to the numerators, so both are double divisions with no integer truncation. One scale
factor, not two, so the aspect is preserved up to two independent `Math.round` calls; the binding axis
rounds exactly, so only the free axis carries a ±0.5 px error, and that same rounding was already
being paid one step later before the fix. `Math.max(1, …)` on both dimensions, so `BufferedImage`
cannot be handed a zero. `min(1.0, …)` means a region smaller than the icon is never upscaled here.
Division by zero is unreachable from the production call site (`sourceRect` floors both at 1).

The `wholelyInside` branch is byte-identical to the pre-commit version and returns before any new
statement, so its pixels and dimensions are untouched — see `VD9-C4` for the comment about it that is
not.

No resolution regression, provably: `shrink` is the same `min` the caller uses for `fit`, so the
binding axis of `cut` lands exactly on `outWidth` or `outHeight`, the caller's recomputed `fit` is
exactly 1.0, and the drawn size is unchanged. The finding's own case: region 18506 x 7120 →
`shrink` 0.0159948 → cut 296 x 114 → fit 1.0 → drawn 296 x 114; before the fix, cut 18506 x 7120 →
fit 0.0159948 → drawn 296 x 114. Same image, 502 MiB less. The region arithmetic in the finding checks
out by hand too (panel 600x420 → available 512x332 → `fitScale` 0.055333, `minScale` 0.027667, window
512x197, region 18506 x 7120).

### D12 — `RGN-B2`'s behavioural description is exactly right

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by reading `MarklinRoute.execRoute` |

The rewritten changelog sentence — *"a route fired by a sensor sets none of its switches and signals
instead … the rest of it, such as speeds and functions, still runs"* — holds in all four respects:

- **All** accessory commands are skipped, not only the conflicting ones:
  `MarklinRoute.java:631` `boolean skipAccessories = auto && conflict != null;` and `:640`
  `if (skipAccessories) continue;` inside the `if (rc.isAccessory())` branch.
- Speeds, directions, functions, functions-off and lights all still run — none is inside that branch.
- A chained route still runs, and correctly propagates `auto` (`:883`,
  `r.execRoute(auto, recursionLimit - 1, false)`), so the chained route does not raise a dialog in an
  empty room either.
- `isStop()` still runs (`:730-756`), ungated by `skipAccessories`, and the pre-loop check calls
  `accessoryHeldByAutonomy()` rather than the emergency-stop-exempting
  `conflictingAccessoryAndReason()`, so an emergency stop is never skipped by it. That is the
  behaviour the commit message says was deliberately left alone, and it is what is there.
- The group semantic is right: it is decided once, before the loop.

The eight `messages*.properties` bundles carry the matching correction
(`route.refusedAccessoryOnActivePath`, `route.refusedSignalProtectingOccupiedPlatform`:
*"No further accessory in this route was switched; its speeds and functions still ran."*). The old
wording survives only in `build/classes/`, which is untracked build output.

One residual imprecision, recorded rather than filed: the Readme's "sets none of its switches and
signals" is true for a conflict present when the route starts. A conflict appearing **mid-route** at
the s88 door leaves the accessories already set (`:656-733`), which the code comment at `:628-630`
states and the changelog does not. That is a genuinely hard sentence to write for a non-technical
reader and is probably not worth changing; it is here so that the next person to read the changelog
against the code does not file it.

Also checked and true in `RGN-B1`: a label naming no known station is left exactly where it is
(`AutonomySession.java:1889-1891` skips it in both loops, `changed` stays false, and a page holding
only orphans is not rewritten at all, `:1904`); `forgetCaptionsOfNonStations` (`:222-231`) touches the
store and never the diagram; and a page cannot be reported as changed when nothing changed, because
`migratedPages.add` runs after a successful `saveChanges` and only for `changed` pages.

### D13 — `SVN-B1`'s change to `testJavadocsAreAttached` is a tightening

| | |
|---|---|
| **Disposition** | closed — checked clean |
| **Confidence** | confirmed by reading |

The question asked of it was whether the two-line edit weakened the guard. It did not.
`ALLOWED` went **down**, 93 → 92, and the test asserts `found <= ALLOWED` *and* `assertEquals(found,
ALLOWED)` (`:128`, `:135`), plus `assertEquals(sortedWorst, pinned)` (`:148`) over an exactly-pinned
per-file list in which `AutonomySession.java (10)` became `(9)`. No exemption was added, no regex
relaxed, nothing removed from what is enumerated. The one-orphan reduction matches the diff, which
merged an orphaned `/** */` pair into the block below it. This is the ratchet working as designed.

*(Needs execution only in the trivial sense: I did not run the class, so I have not seen that the
repository's actual orphan count is 92.)*

---

## What this pass did NOT look at

Said plainly, because the value of a validation pass is in its boundary.

**Not run.** No test class was executed and no battery. Every "this test discriminates" and "this test
cannot fail" statement above was reached by reading the assertion against both implementations, not by
reverting anything. Two claims are marked **needs execution** and are listed below.

**Not covered at all:**

- `docs/reviews/2026-09-03-questions-for-adam.md` (commit `0d4e3be4`, 142 lines). I did not check that
  each of its rows still matches the finding it names, nor that the consolidated backlog table in
  `2026-09-03-release-review.md` agrees with the per-document dispositions it summarises. That
  cross-document consistency check is the most likely place for a stale row and I did not do it.
- `MT-265`'s steps 2, 3 and 4, and the 19 other rows of `docs/manual-tests/tests.md`. Only step 1 was
  checked, and only for whether the gesture reaches the code.
- `test/regression/testTheWindowTakesTheKeyboard`, `testTheAutonomyEditorKnowsWhichSquare`,
  `testTheRoutingChoiceSurvivesTheUpgrade`, `test/ui/testLocMappingPages`, `testRoutingRuleTooltips`,
  `testStagingOutcomeMessages`, `testTimetableColumnHeadings` and `testEveryLanguageFits` — the
  mechanical `TSX-B8` sandbox moves in `224909e7`. I checked the *guard* that pins the rule
  (`VD9-C15`) and `testLocIconCrop`'s two, and read none of the other six diffs. They are one-statement
  moves and the guard now enforces them, which is why I stopped there.
- Whether the reversal notice is raised on the right **set** of squares. `VD9-B6` is about the
  javadoc describing the guard, and `VD9-B7` about which copy of it was fixed; I did not audit
  `AutonomyChecks`'s own predicate for which squares it lists against `measuredRoomToReverseInto`'s
  actual failure set, which is a larger question and would need the graph.
- The `2026-08-30-staging-planner-round.md` document beyond `SG-B5`, and
  `2026-09-02-first-validation.md` beyond the two `REL-C14` prefix corrections.
- Concurrency. Nothing here was examined for thread safety, with one exception noted below.
- Anything in `cs2_sample_layout/` was opened read-only, twice (`routes.json`, `autonomy/setup.json`),
  and nothing was written. `config/autonomy/setup-before-edit.json` was not opened at all.

**Needs execution:**

- Whether `CS3TestServer`'s stop-then-restart onto the *kept* ephemeral port re-binds reliably on
  Windows. `sun.net.httpserver` does not set `SO_REUSEADDR` and Windows refuses a bind over a socket in
  `TIME_WAIT`; `testParseWebServer:103-105` stops and immediately restarts. Not a regression — fixed
  8080 had the identical exposure — but it is the one way the `TSX-C17` fix could flake.
- Whether a `ConcurrentModificationException` on `Edge.lockEdges` is genuinely unreachable, which
  `REL-C16`'s new comment asserts. `Edge.setOccupied` is `synchronized`, but `addLockEdge`,
  `removeLockEdge` and `clearLockEdges` (`Edge.java:269-296`) are not, and `addLockEdge` has no null
  guard (`FullAutonomyExample.java:53-56` passes `layout.getEdge(...)` unchecked, which would throw an
  NPE mid-loop with no CME involved). The JSON loader does guard and `copyEdge` copies an existing
  list, so the conclusion is probably right; the stated mechanism is narrower than the code enforces.
  This is a whole-tree claim across the routes-vs-autonomy thread boundary and static reading cannot
  close it.
- Whether `testLocIconCrop` is now close to the runners' 512 MiB ceiling (`VD9-C5`), and whether
  reverting `IPR-B4` under `one.sh` produces the named assertion message or an `OutOfMemoryError`.
  Arithmetic says the latter; both are red.
- Whether `ant test` exits `BUILD SUCCESSFUL` over a class reporting `Skips: N`. The audit flags this
  as read-not-run and I did not change that. (`TestNGAntTask` does expose `setSkippedProperty`, so the
  leg is reachable in principle — which bears on `VD9-B4`.)
- `VD9-C19`'s mutation: that reverting `migratedPages.clear(); migratedCaptions = 0;` really does leave
  `testTheSessionSaysWhichPagesTheMigrationRewrote` green. Confident by reading — the second half
  inspects a freshly constructed object — but it is a one-line experiment.
- `VD9-B8`'s live consequence: rewriting a page that already has a `.cs2.bak` and watching the log
  claim a backup that was not taken. Two such pages are sitting in `cs2_sample_layout` now, so this is
  a five-minute check on a copy of that folder — **not** on that folder.
- Whether `getAutonomySession()` is reached on every launch, which is what makes `RGN-B1`'s "the
  start-up log says both" true rather than "the log at the first diagram repaint". It is lazy
  (`TrainControlUI.java:2587-2706`) and I did not trace every start-up path into it.
