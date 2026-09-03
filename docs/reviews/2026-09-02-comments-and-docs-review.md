# Comments and documentation review, day two (v3.0.0 pre-release fan-out)

**Status:** open

**Prefix:** CD3

Reviewed at `54a70c03` (branch `autonomy-diagram-r0`), 2026-09-02, by reading and grepping only — no
build, no test run, no application launch, no `javap`/`java`/`javac` invocation of any kind. One local
Python script was written to parse the class-file constant pool of a third-party jar already shipped in
`dist/lib/` (see CD3-C4); it opens the `.class` file as a zip entry and reads bytes, and starts no JVM.

The brief named five commits as the freshest, least-read work in the repository:
`1cfdf370`, `87b6c10a`, `975f157d`, `8d1c17ca`, `cf048f9b`. While this pass was under way, a sixth
commit landed on the branch — `54a70c03`, "Two disposition corrections" — touching only
`docs/reviews/2026-09-01-*.md`. It is documentation, squarely in scope, and it is now the single
freshest thing in the repository, so it is covered here too (CD3-C6, CD3-C7). HEAD moved from the
briefed `cf048f9b` to `54a70c03` with no other change in between.

A prior pass, `docs/reviews/2026-09-01-comments-and-docs-review.md` (prefix `CMT`), covered the branch
one day and five commits earlier. Its four B findings are fixed (confirmed independently below, not
just taken on the disposition table's word — see CD3-D1 through CD3-D4). `CMT-C1` and `CMT-C3` are
untouched by today's five commits (`Point.java` was not among the files any of them changed) and are
left as the CMT document recorded them. `CMT-C2`'s dead-key count is now stale by at least two keys —
noted at CD3-D9, not recomputed in full.

---

## Method

1. Read the full diff of each of the five named commits (`git show <hash>`), file by file, against the
   whole surrounding method rather than the diff hunk alone — the failure mode this brief warns about
   (a rule moved, the comment arguing for it left at the old site) is invisible from a hunk in isolation.
2. For every new or touched comment making a factual claim about the code beside it, current call
   graph, or another file's line number, checked the claim directly: opened the cited method, grepped
   for the cited symbol, or counted the cited line. Traced two multi-hop citations across files
   (`RightClickFunctionMenu.java` → `GraphLocAssign.java:253`; `AutonomyBuilder.java` → `AutonomyEditor-
   Panel.setUsage`/`setStation`) rather than trusting that the target exists.
3. For every `MUTATION:` claim in a test added by these five commits, read the described mutation
   against the actual assertions to check it would truly flip the result, rather than trusting the
   sentence — per the standing instruction that a false mutation claim is worse than none.
4. Checked message-bundle parity (key presence across all eight languages, placeholder-count agreement
   against the call site) for every key touched or added by the three commits that changed bundles.
5. Grepped `AutomationAPI.md`, `Automation.md`, and the two `docs/plans/*.md` files most likely to be
   affected (`autonomy-ui-feature-ledger.md`, the diagram-autonomy plan) for any sentence whose subject
   is a UI action, menu item, or rule that any of the five commits added, removed, or changed.
6. Independently re-derived one claim that required going outside `src/` and `docs/`: whether
   `org.json.JSONObject`'s key order is insertion order, by reading the constant pool of the shipped
   `dist/lib/json-20260814.jar` (CD3-C4). No JVM was started to do this.
7. Read the two markdown files `54a70c03` touched in full, and checked its citations the same way as
   any source comment — by opening the line and the document it points at.

## What this pass did not do

- **`Layout.java` (7900+ lines) and `TrainControlUI.java` (25,855 lines) were not read end to end.**
  Every diff hunk from the five commits was read in full whole-method context; a comment gone stale in
  an untouched part of either file, from an earlier round, would not surface here.
- **The four largest `automationui/*.java` files** (`AutonomyBuilder`, `AutonomySession`,
  `AutonomyCompanionStore`, `AutonomyChecks`) were read only at the sites the five commits touched or a
  citation pointed at, not cover to cover.
- **`CMT-C1` and `CMT-C3` were not re-verified** — `Point.java` was not among the files any of today's
  five commits changed, so there was no reason to expect either to have moved, and neither was.
- **`CMT-C2`'s exact dead-key count was not recomputed.** Two of its named keys
  (`menuClearAllHomeLocomotives`, `confirmClearAllHomeLocomotives`) are wired to a real caller as of
  `1cfdf370` and should drop out of that count; the other 227+ were not re-walked.
- **Non-English bundle translation *content* was not reviewed** — only key parity and placeholder
  counts, matching yesterday's `CMT-D1` method.
- **`docs/manual-tests/issues.md`'s new `OB-167` row was not checked against runtime behaviour** — it is
  a log-table entry, not a comment or a prose claim, and is tangential to comment/doc drift.
- **`cf048f9b`'s and `54a70c03`'s dispositions were spot-checked, not exhaustively re-verified against
  all six 2026-09-01 documents.** Every disposition line touching a claim this pass independently
  re-derived from the current source (SVN-A3, SVN-B6, SVN-B7, SVN-B10, SVN-B13, SVN-B14, SVN-B16,
  SVN-B4/TCX-A2) matched. Two, both added by the newest commit, did not (CD3-C6, CD3-C7).
- **The `org.json` HashMap finding (CD3-C4) rests on static bytecode inspection, not execution** — the
  no-JVM rule forbids the direct way to confirm it (printing a `JSONObject`'s iteration order at
  runtime), so confidence is stated as constant-pool evidence rather than as observed behaviour.

---

## Findings

| ID | Severity | Status |
|---|---|---|
| CD3-B1 | B | open |
| CD3-B2 | B | open |
| CD3-B3 | B | open |
| CD3-C1 | C | open |
| CD3-C2 | C | open |
| CD3-C3 | C | open |
| CD3-C4 | C | open |
| CD3-C5 | C | open |
| CD3-C6 | C | open |
| CD3-C7 | C | open |
| CD3-D1 | D | closed (not a defect) |
| CD3-D2 | D | closed (not a defect) |
| CD3-D3 | D | closed (not a defect) |
| CD3-D4 | D | closed (not a defect) |
| CD3-D5 | D | closed (not a defect) |
| CD3-D6 | D | closed (not a defect) |
| CD3-D7 | D | closed (not a defect) |
| CD3-D8 | D | closed (not a defect) |
| CD3-D9 | D | closed (not a defect, but a related count needs a refresh) |

---

### CD3-B1 — `layoutEditingCompleteThen`'s own javadoc argues for a `finally` its body no longer has, one paragraph before saying so

**File/lines:** `src/org/traincontrol/gui/TrainControlUI.java:19502-19524`.

```
    /**
     * The same, with the caller's continuation guaranteed to run.
     *
     * `after` is what an editor uses to finish a page switch, and it is the last statement of a dozen
     * - so anything throwing on the way skipped it, and the editor was left with its switch latch
     * raised, refusing every further page and mode change in silence for the life of the window.
     * Found by a threading sweep.
     *
     * A `finally`, which is what `BusyDialog.run` does with its own continuation and for the same
     * reason: the work can fail, and the thing that puts the window back together afterwards must not
     * fail with it.
     *
     * **The finally used to be HERE, and here it covered nothing** (SVN-A3).
     * `layoutEditingComplete` is asynchronous: it lowers the button, reads a field and starts a
     * thread.  Those three statements were the whole of what the try held, and the dozen the sentence
     * above describes are in `layoutRefreshComplete`, on the other side of a worker.  So the
     * continuation ran at once ... The guarantee now lives in `layoutRefreshComplete`, where those
     * statements are; what is left here is the case where there is no worker to reach it.
     *
     * @param after what to run when the refresh has finished, failed, or thrown
     */
    public void layoutEditingCompleteThen(Runnable after)
```

The method body, changed by `1cfdf370` today:

```java
        final java.util.concurrent.atomic.AtomicBoolean ran =
            new java.util.concurrent.atomic.AtomicBoolean(false);

        Runnable once = () ->
        {
            if (after != null && ran.compareAndSet(false, true)) after.run();
        };

        try
        {
            layoutEditingComplete(once);
        }
        catch (RuntimeException | Error thrownBeforeTheWorkerStarted)
        {
            once.run();

            throw thrownBeforeTheWorkerStarted;
        }
```

There is no `finally` anywhere in this method any more. The guarantee is a `try`/`catch` around an
idempotent `once` wrapper, passed down into `layoutEditingComplete` so the real `finally` — confirmed
present at `layoutRefreshComplete`, `TrainControlUI.java:19436` — fires it once the async refresh
actually completes; the `catch` here exists only for the case where `layoutEditingComplete` throws
*before* starting its worker thread, so there is no `finally` downstream left to reach.

The javadoc's own paragraph immediately above the SVN-A3 note still asserts the opposite: *"A `finally`,
which is what `BusyDialog.run` does with its own continuation and for the same reason ..."*, phrased as
a description of what this method does. It was true before today. The very next paragraph, added today,
opens with *"The finally used to be HERE"* — past tense, i.e. not any more — describing the same method.
Read top to bottom, paragraph one asserts the mechanism, paragraph two immediately retracts it without
either being edited to fit the other. A maintainer who trusts paragraph one and "simplifies" the
try/catch back into a plain `try { layoutEditingComplete(once); } finally { once.run(); }` to match it
would reintroduce exactly `SVN-A3`: `once` would fire the instant `layoutEditingComplete` returns from
kicking off its background thread, not when the refresh actually finishes, which is the original bug
this commit fixed. This is the "argues both ways" shape the brief calls out by name.

**Confidence:** confirmed by reading the current method body and its caller chain
(`layoutEditingComplete` → `layoutRefreshComplete`, `TrainControlUI.java:19405-19448`), and by comparing
against the commit's own stated intent in its message ("The guarantee moves to where those statements
are").

---

### CD3-B2 — `Layout.isPathClear`'s comment still calls the reversal-room guard "inert on his railway today"; the project's own record says otherwise, and today's commit extended the guard's reach without touching the sentence

**File/lines:** `src/org/traincontrol/automation/Layout.java:2389-2397`.

```
                // Left as it is on purpose.  It is inert on his railway today (six tiles carry lengths
                // at all), and a guard that is occasionally over-strict on a measured layout is a
                // nuisance, while changing what counts as measured would move tail-clearing on live
                // track.  What is not acceptable is a reader trusting this loop, so it says so here.
                // IN measuredRoomToReverseInto NOW, so the staging planner can ask the same
                // question (TCX-A2).  It had this rule and the planner did not, and the planner is
                // what decides where Return Home sends a train - so it offered berths this then
                // refused on the first move.  The counting is unchanged, including both of the
                // unsoundnesses above.
```

The first sentence — *"It is inert on his railway today (six tiles carry lengths at all)"* — was
already established as false by the project's own record a day before today's commits, and the
correction was never applied here:

- `docs/reviews/2026-09-01-fanout-index.md`, "FX2-3": *"Correction, and it makes this more urgent than
  first written ... Both halves were wrong ... TWO of the six ARE reversal squares ... It is live
  behaviour on your railway, not a dormant rule."*
- Commit `c9153aaf` ("The validator's corrections: two false claims of mine, and a guard that is not
  inert") fixed this exact sentence in `docs/reviews/2026-09-01-fanout-index.md`,
  `docs/manual-tests/tests.md`, and `AutomationAPI.md` — but its file list (`git show --stat c9153aaf`)
  does not include `Layout.java`. The source comment was never updated.

Today's commit, `975f157d`, edited the code immediately following this paragraph (extracting the
counting loop into `Layout.measuredRoomToReverseInto` and wiring the staging planner into the same
rule) and added four new lines right after it — without touching the "inert" sentence one paragraph
above the insertion point. The change makes the claim more wrong than it was yesterday, not less: the
rule that was already live on two of his reversal squares (`BottomMainB`, room 4; `BottomMainC`, room
2 — per the fanout index) is now asked by a *second* consumer (`HomeStaging`'s search), so calling it
"inert" undersells its reach further. A reader deciding whether the two recorded unsoundnesses a few
lines above (`getLength() > 0` not meaning "measured"; possibly summing the wrong segments) are worth
fixing soon, based on this comment, would conclude there is no live consequence — when the fanout
index's own numbers say up to 42 of 54 locomotives with a recorded train length exceed the two units of
room at `BottomMainC` alone.

**Confidence:** confirmed by reading the current comment, `git show --stat c9153aaf` (which fixed the
same sentence everywhere except here), and the fanout index's own corrected numbers.

---

### CD3-B3 — `AutomationAPI.md` tells the user no bulk "clear every home" action exists; one was added today

**File/line:** `AutomationAPI.md:531`.

```
There is no longer a single action that clears every assignment at once; clear them one station at a
time, or call `Layout.clearHomeLocomotives` programmatically.
```

This sentence was accurate as of yesterday (it is the corrected wording from `CMT-B3`'s fix). Today's
commit `1cfdf370` (`R28-C1`) adds exactly the action this sentence says does not exist:

```java
        // CLEARING EVERY HOME AT ONCE, which 2.8.1 had and this release lost (R28-C1).
        //
        // Adam, 2026-09-02: **"that option should be added back in to the autonomy editor, with a
        // confirmation."**  ...
        clearHomes = new JButton(I18n.t("autolayout.ui.menuClearAllHomeLocomotives"));
        clearHomes.addActionListener(e -> clearAllHomes());
        button(clearHomes);
```

(`src/org/traincontrol/gui/AutonomyEditorPanel.java:471-482`, with the confirmation dialog and the
actual clearing loop in `clearAllHomes()` at `:6431-6478`.) The button is live, labelled, and wired to a
confirmation exactly as the commit message describes. `AutomationAPI.md` was not touched by `1cfdf370`
and still tells the reader the opposite of what the application now does: that clearing every
assignment at once requires calling a Java method rather than pressing a button in the autonomy editor.
This is user-facing documentation, not an internal comment, and it is wrong about the same feature
`CMT-B3` was filed against two days ago — the capability that document said was missing is now present,
and the fix to `CMT-B3` was never revisited once the feature came back.

**Confidence:** confirmed by reading the current `AutomationAPI.md` section in full, the current
`AutonomyEditorPanel.java` diff and surrounding code, and the commit message's own quotation of Adam's
request.

---

### CD3-C1 — `MarklinRoute.heldReason`'s protecting-signal rationale is stranded above code it no longer describes, after `87b6c10a` moved the rule it explains onto `Layout`

**FIXED 2026-09-03.**  The paragraph says where the rule lives now - `Layout.protectsAnOccupiedSquare`, since `87b6c10a` - and that it used to be computed immediately below it, so "this one covers the platforms no active path happens to cross" points at something again.  Kept rather than deleted because it is the REASON the rule exists, which neither door repeats.

**File/lines:** `src/org/traincontrol/marklin/MarklinRoute.java:436-449`.

```
        // The signals protecting squares somebody is standing on, which getActiveAccs cannot see.
        //
        // It walks the config commands of active edges, and a protecting signal is usually not one -
        // it is driven separately, by occupancy. So a route could turn a platform's signal green with
        // a train standing at it, and nothing re-asserts it until the next occupancy change: a green
        // aspect inviting a hand-driven train into an occupied platform, for as long as the train
        // stays there.
        //
        // "Usually" rather than "never", which is how this comment first read. `refreshOneSignal` says
        // outright that TilePorts gives a SIGNAL tile a GREEN configuration command, so a path
        // configured across one drives it through getConfigCommands - the same Accessory. The two sets
        // overlap; this one covers the platforms no active path happens to cross.
        // By address AND protocol, which is how the route names it and how the station resolves it -
        // a bare address is ambiguous across decoder types on this railway.
        MarklinAccessory accessory =
            this.network.getAccessoryByAddressIfPresent(rc.getAddress(), rc.getProtocol());
```

Before `87b6c10a`, this comment sat directly above the `protecting` set this method built locally by
walking `this.network.getAutoLayout().getPoints()` — "this one covers the platforms no active path
happens to cross" referred to that computation, immediately below it. `87b6c10a` deleted the local
computation and moved it, essentially verbatim, into `Layout.protectsAnOccupiedSquare`, which carries
its own copy of the same rationale (`Layout.java:6122-6134`). The comment above was left in place,
directly above the unrelated address/protocol resolution line that now follows it, with "this one" no
longer pointing at anything on the page. A new, correctly-placed note was added at the real call site
seven lines below (*"ASKED OF THE LAYOUT (SVN-B16). It used to be worked out here ..."*, `:471-474`),
so the accurate account exists — the stale block is redundant rather than wrong about current behaviour,
but it dangles above code it does not describe, which is the `CMT-C1` shape from yesterday's pass
(a comment surviving the deletion of the code it was written for).

**Confidence:** confirmed by reading the current file and the `87b6c10a` diff, which shows the deleted
`protecting` computation sat immediately below this exact comment block.

---

### CD3-C2 — a test helper's javadoc documents one parameter of five, and no `@return`

**FIXED 2026-09-03.**  All five parameters and the `@return` are documented, like the sibling overload three lines above.

**File/lines:** `test/core/testAutonomyDiagramMonitor.java:1223-1237`.

```
    /**
     * The same, with parking said separately from shut (TCX-B6).
     *
     * They used to be the same argument: `shut` was passed as the Badge's `parking` AND as its `shut`,
     * so every fixture this built had them equal - and the two tests that vary only `shut` would have
     * passed just as well against `isImpassable() { return parking; }`.  The one thing they could not
     * do was tell the two apart, which is what they exist to do.
     *
     * They share a colour (`TileAnnotation:1531`), which is why separating them changes no ink here:
     * parking was true only where shut already was.
     *
     * @param parking whether autonomy leaves the square alone
     */
    private static java.awt.image.BufferedImage badgeAt(boolean station, boolean turns,
        boolean parking, boolean shut, int size) throws Exception
```

Added today by `1cfdf370` (`TCX-B6`) as a second overload of the existing four-argument `badgeAt`. The
new overload takes five parameters and returns a `BufferedImage`; the javadoc documents only `parking`
and has no `@return`, unlike the sibling overload three lines above it which documents all four of its
own parameters and its return value. Not misleading about behaviour — the prose explains the one thing
that changed — but it is an `@param`/`@return` mismatch against the signature, which the brief names as
its own category.

**Confidence:** confirmed by reading the current file; the four undocumented parameters and the missing
`@return` are a straight comparison against the method signature.

---

### CD3-C3 — the autonomy UI feature ledger still lists "Clear all home locomotives" as `TODO`, built today

**FIXED 2026-09-03.**  The ledger row says built, dates it, and records that it shipped in the editor's Bulk Tools submenu rather than on the right-click menu the row describes.

**File/line:** `docs/plans/autonomy-ui-feature-ledger.md:109`.

```
| Clear all home locomotives | general right-click -> HomeLocomotiveMenu | TODO |
```

`CMT-B3` (yesterday's pass) cited this row as *agreeing* with the code that the feature was missing.
Today's `1cfdf370` built it — as a button in `AutonomyEditorPanel`, not as a right-click item on
`HomeLocomotiveMenu` as the row's own "Where it was" column describes, but the capability itself, the
subject of the `TODO`, now exists. The row was not updated. `docs/reviews/README.md`'s own rule for
plans is that they close "when the work is done" — this is a ledger row rather than a whole document,
but the same principle applies: a reader consulting this file to decide what autonomy-UI work remains
would be told to (re-)build something that shipped today.

**Confidence:** confirmed by reading the current ledger row and the current `AutonomyEditorPanel.java`
(`clearHomes` button and `clearAllHomes()` method, both new in `1cfdf370`).

---

### CD3-C4 — `AutonomySession.tilesWithAHome()`'s `@return` promises an order the underlying `JSONObject` almost certainly does not keep

**FIXED 2026-09-03.**  The `@return` says "in no particular order", why (`JSONObject` is a `HashMap`), that it is harmless to today's callers, and what a future caller that showed these squares to a person would have to do.

**File/lines:** `src/org/traincontrol/automationui/AutonomySession.java:4074-4081`.

```
    /**
     * Every square the active configuration records a home locomotive on.
     * ...
     * @return the squares, in the configuration's own order, empty when nothing is homed anywhere
     */
    public java.util.List<TileKey> tilesWithAHome()
    {
        ...
        for (String key : points.keySet())
```

`points` is an `org.json.JSONObject`. Without running any code (per the hard rule against starting a
JVM against this project), I inspected the constant pool of the shipped `dist/lib/json-20260814.jar`'s
`org/json/JSONObject.class` directly as a zip entry. It contains constant-pool references to
`java/util/HashMap.<init>()V` and `<init>(I)V`, and **no reference to `LinkedHashMap` anywhere in the
class** (checked both as a literal string and by walking every `Methodref`/`Fieldref` constant whose
class name contains `Map`). That is consistent with `JSONObject`'s well-known backing store being a
plain `HashMap`, whose `keySet()` iterates in hash-bucket order — stable for a given key set and JVM,
but unrelated to the order keys were inserted or the order they appear in the source JSON file.

This project has hit exactly this confusion before, in a different `HashMap`:
`docs/reviews/2026-07-code-review.md:1024` ("`parseFile` flattens each `..key=value` group through a
`HashMap`, and for the pair `{kont, hi}` that map deterministically iterates `hi` first"). The new
javadoc's claim — "in the configuration's own order" — reads as exactly that assumption. It is currently
harmless: the sole caller, `clearAllHomes()` (`AutonomyEditorPanel.java:6431-6478`), only counts and
iterates the list to clear each home, and never displays or depends on its order. A future caller that
lists the cleared stations in a dialog, expecting them in a human-sensible order, would not get one.

**Confidence:** moderate-high. The constant-pool evidence (a `HashMap` constructor called, no
`LinkedHashMap` symbol anywhere in the class) is strong but was obtained statically, and I did not
execute the class to observe actual iteration order, per the no-JVM rule — see "What this pass did not
do."

---

### CD3-C5 — "`claimHome`, thirty lines up" undercounts the actual distance by roughly two to three times

**FIXED, by a later commit** (verified 2026-09-03).  It reads "further up this file" - no number to be wrong.

**File/lines:** `src/org/traincontrol/automation/Layout.java:1139-1146`.

```
            // AND THE SQUARE RULE, which was only on the other door (SVN-B13).
            //
            // The loop above asks whether this LOCOMOTIVE already has a home.  `claimHome`, thirty
            // lines up, asks the other question - whether this SQUARE is already somebody's home -
```

`claimHome` begins at `Layout.java:1049` — 90 lines above this comment, not thirty. Measured to the
specific `if (taken.isSamePlaceAs(p)) return;` line inside it that the new code mirrors
(`Layout.java:1090`), the distance is 51 lines. Either reading is two to three times the stated figure.
This is a navigational aid, not a claim about behaviour, and nobody following it to `claimHome` will
fail to find it — the method name is also given — so this is the smallest finding in the document.

**Confidence:** confirmed by direct line count against the current file.

---

### CD3-C6 — today's newest commit cites `Layout.java:2337` for a comment that is actually 39 lines away, in the very file state the commit was written against

**FIXED 2026-09-03.**  The citation names the comment's heading and gives today's line as a hint rather than as the address, with the reason: a line number in this repository is a moving target.

**File/line:** `docs/reviews/2026-09-01-week-of-commits-review.md:446`, added by commit `54a70c03`
(2026-09-02, the newest commit on the branch as this review was written).

```
**CLOSED by `FX2-3`.** ... It is still true that `GraphReducer.sumLength` adds `max(0, tileLength)`
over the tiles an edge spans, so one measured tile out of ten reports that tile's length and the guard
reads it as a complete measurement; the comment at `Layout.java:2337` says so.
```

At `54a70c03` (and at current HEAD, unchanged), `Layout.java:2337` reads:

```
        // there - and it is skipped entirely when that maximum is zero, which is most squares on a
```

— part of the unrelated `validateTrainLength` discussion two paragraphs above. The comment that
actually says what this disposition claims (*"length is `GraphReducer.sumLength`, which adds
`Math.max(0, tileLength)` over the tiles it spans"*) is at `Layout.java:2376`, 39 lines away. This is
not a claim about behaviour going stale — the underlying assertion (the guard reads a partially-measured
edge as fully measured) is correct and matches the code at 2376 — it is a line citation that was wrong
the moment it was written, most likely because the citing text was drafted against an earlier revision
of `Layout.java` (the block shifted by 32 lines earlier the same morning, when `8d1c17ca` inserted the
`SVN-B13` square-rule block at `:1139`) and not re-checked against the file as it stood when `54a70c03`
was committed.

**Confidence:** confirmed by reading line 2337 and line 2376 of the current file directly, and by
tracing the 32-line shift to `8d1c17ca`'s insertion (`git show <rev>:Layout.java | grep -n` at
`87b6c10a`, `975f157d`, and `8d1c17ca` in sequence).

---

### CD3-C7 — the same commit cites `D24-C7` as an open finding; no finding by that name exists anywhere in the document it cites

**FIXED 2026-09-03.**  The citation pointed at nothing, so it is replaced by what it was reaching for - `D24-B2`'s own prose - and by the manual test where an answer would actually come from, MT-248.

**File/line:** `docs/reviews/2026-09-01-test-suite-review.md:330`, added by commit `54a70c03`.

```
**STILL DEFERRED - needs Adam (corrected 2026-09-02).** ... `D24-C7` is the same question and is also
open.
```

`docs/reviews/2026-09-01-last-day-review.md` — the `D24`-prefixed document — contains the string
`D24-C7` exactly once in the whole file, at its own line 175, as a parenthetical inside a different
finding's prose: *"the comment's claim that '...' is not true of the squares that decide it (see
D24-C7)"*. There is no heading, table row, or numbered "open question" anywhere in that document for
`D24-C7` — its own "Open questions" list (`:532-547`) runs `D24-A1`, `D24-B1`, `D24-B2` (twice),
`D24-B5`, `D24-C8`, with no `C7` among them. `D24-C7` is a forward reference to a finding that was
apparently never given its own entry — plausibly an early draft number for what became `D24-C8`, or for
the editor-notice point that D24-B2's own prose raises but never separately headed. Whichever it is,
today's disposition cites it as independent corroboration that "the question is also open" — but there
is nothing behind the citation for a future reader to open and check, which defeats the reason to cite
it at all.

**Confidence:** confirmed by an exhaustive grep for `D24-C7` and `D24-C` across
`docs/reviews/2026-09-01-last-day-review.md`.

---

### CD3-D1 — `CMT-B1` is fixed, and its own fix's javadoc holds up

`HomeStaging.canRest`'s javadoc (`HomeStaging.java:1615-1621`) and body were both checked against
today's HEAD. Confirmed unchanged since the `9f1b80c8` fix cited in the fanout index: the summary no
longer mentions terminus/reversibility, and the inline "NOT THE TERMINUS RULE" comment and the `return`
clause agree. None of today's five commits touched `HomeStaging.canRest`. Not a defect.

### CD3-D2 — `CMT-B2` is fixed, and the new call sites agree with the corrected javadoc

`Layout.refreshAllProtectingSignals`'s javadoc was checked against `grep -rn
"refreshAllProtectingSignals" src/ test/` at current HEAD: the production call sites remain zero, and
the test references (`testAutoLayout.java`, `testBothProtectingSignalsAreThrown.java`) are unchanged by
today's commits. Not a defect.

### CD3-D3 — `AutonomyBuilder`'s `D24-B5` fix comment matches what `setUsage`/`setStation` actually do

The new comment at `AutonomyBuilder.java:936-951` claims the editor's "Out of service" writes only
`active` and touches no direction. Traced to `AutonomyEditorPanel.setUsage`
(`AutonomyEditorPanel.java:2777-2784`) and `setStation` (`:2786-2803`): `setUsage(target, isStation,
false)` re-asserts the square's *current* station-ness unchanged and sets `active` to `Boolean.FALSE`;
neither method touches a direction property anywhere in its body. Confirmed accurate.

### CD3-D4 — `AutonomySession`'s `worthABadge` fix comment matches the actual expression and its own colour citation resolves correctly

The `SVN-B6`/`D24-C9` comment at `AutonomySession.java:4696-4701` (leading into `boolean worthABadge =
store.isStation(tile) || isTurnAround(tile) || shut;` at `:4707`) cites `TileAnnotation:1531` for the
claim that parking and shut share a colour. `grep -n "badge.isParking() || badge.isImpassable()"
src/org/traincontrol/automationui/TileAnnotation.java` returns exactly line 1531. Confirmed accurate.

### CD3-D5 — `SVN-B7`'s "every door" claim and `SVN-B10`'s `hasErrors()` disjunction both hold

`executeRoute`'s new guard (`TrainControlUI.java:16100-16118`) and the removed duplicate check in
`RouteListMouseClicked` (`:19312-19319`, now delegating unconditionally to `executeRoute`) were read
together: the row click and the right-click menu both funnel through `executeRoute`, and only that
method now tests `routesExecuting`. Separately, `AutonomySession.hasErrors()` (`:3572-3575`) is exactly
`hasBlockingProblems() || errorCount() > 0`, matching the new comment at `TrainControlUI.java:5170-5182`
that says the error count shown can legitimately be zero while the method still refuses. Confirmed
accurate on both counts.

### CD3-D6 — `SVN-B16`'s move to `Layout.protectsAnOccupiedSquare` is complete and its cross-file citation resolves

`Layout.protectsAnOccupiedSquare` (`Layout.java:6122-6157`) is called from both
`MarklinRoute.heldReason` (`:475`) and `LayoutLabel.java`'s accessory-click handler (`:398-403`), and
`MarklinRoute`'s old private `isOneOf` helper and its local `protecting` computation are both gone.
`RightClickFunctionMenu.java`'s new Cancel-restore comment cites `GraphLocAssign:253` as the sibling
door that "applies at commit time instead" — `GraphLocAssign.java:253` is exactly
`setArrivalFunc(getArrivalFunc())` inside `commitChanges()`. Confirmed accurate.

### CD3-D7 — Both new `testEditorSurfaceRules.java` tests' `MUTATION:` claims hold

`testEveryDoorThatRunsARouteAsksIfItIsAlreadyRunning`'s claim (moving the check back into the play
button fails both halves) was traced against the actual method bodies: `executeRoute`'s body would lose
the `routesExecuting.contains(route)` string (failing the first assertion) and
`RouteListMouseClicked`'s body would regain it (failing the `assertFalse`). Confirmed both would fail.
`testSwitchingAnAccessoryByHandAsksAboutProtectingSignals`'s three `assertTrue(...contains(...))` checks
were confirmed against the current text of all three files named. Not a defect.

### CD3-D8 — Both new `testHomeStaging.java` tests' fixtures and `MUTATION:` claims hold

For `testTheAuditSeesTheReversalRoomRuleTheStagingPlannerDoesNotHave` (`TCX-A2`): traced that removing
either the edge's length or the locomotive's train length makes `measuredRoomToReverseInto` return
`null` on both the runtime and planner sides, which stops the refusal on both and drives the audit to
zero — matching the stated mutation exactly. For `testTwoHomesOnOneSquareDoNotBothSurviveTheLoader`
(`SVN-B13`): walked the fixture's assignment order by hand (`HS W1`→`LOC_A` claimed first, `HS
W2`→`LOC_B` dropped for sharing a block with `HS W1`, `LOC_B` then falls back to its positional home at
`HS B`) and confirmed it produces `homedThere == 1` and `stillNamed == 1` exactly as asserted. Not a
defect.

### CD3-D9 — Message-bundle parity holds for every key touched by today's five commits; `CMT-C2`'s count needs a refresh, not a re-file

All keys added or newly wired up today — `autosetup.ui.infoHomesCleared`,
`autosetup.ui.infoNoHomesToClear` (`1cfdf370`), `route.ui.infoAlreadyRunning`,
`layout.ui.confirmAccessoryProtecting` (`87b6c10a`), `autolayout.warnHomeSquareAssignedTwice`
(`8d1c17ca`) — are present in all eight `.properties` files with matching placeholder counts against
their call sites. `autolayout.ui.menuClearAllHomeLocomotives` and
`.confirmClearAllHomeLocomotives`, which `CMT-C2` listed among 229 keys with "zero textual occurrence
anywhere in `src/`", are now referenced from `AutonomyEditorPanel.java:480,6463,6464` — `CMT-C2`'s count
is accordingly stale by at least these two, though the underlying finding (a real cluster of dead
UI-text keys exists) is not withdrawn, only its specific number. Not itself a new defect; recorded here
because a stale count in an open finding is exactly the kind of drift this document exists to catch and
`CMT-C2`'s owner should update rather than re-derive from scratch.
