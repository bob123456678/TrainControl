# Documentation review: is any of it still true?

**Status:** open

**Prefix for citing this document: `DOC`.** Cite findings from here as `DOC-A1`, `DOC-B7`, and so on.
Taken elsewhere and not reused here: `CR`, `DD`, `DR`, `DW`, `FB`, `FBR`, `FR`, `FSR`, `FV`, `GC`,
`IAR`, `IP`, `IR`, `ISD`, `LD`, `LT`, `NR`, `RA`, `SV`, `TA`, `TD`, `TR`, `UR`.

**Reviewed:** `eac0e392` ("OB-129, FR-040, FR-041, menu grouping, and a field the designer kept
deleting"), at the head of `autonomy-diagram-r0`, on **2026-08-28**. No code, test or document was
changed by this pass; the only file it wrote is this one.

**Scope.** Documentation only, and only the question of whether it is TRUE of the code as it stands:
`docs/reviews/README.md`, `docs/manual-tests/README.md`, `docs/reference/README.md`,
`docs/UI-standards.md`, `docs/reviews/archive/README.md`, both files in `docs/plans/`, `test/README.md`,
the two documents in `docs/reviews/` still consulted as fact rather than as history, the changelog and
manuals (`Readme.md`, `Automation.md`, `AutomationAPI.md`), `docs/manual-tests/triage.py` and
`triagedb.py`, and the javadoc and comments in every source file touched in the last seven days -
`Layout.java`, `GraphReducer.java`, `AutonomySession.java`, `AutonomyChecks.java`,
`MarklinControlStation.java`, `CSDetect.java`, and the eleven `gui/` classes with churn.

**Method, and its limits.** Every claim below was settled by reading the code that implements it, per
[README.md](README.md)'s "verify the layer you are actually claiming about" and "the documentation is
part of the method - read it before contradicting it". **Nothing was run** - no build, no test, no
script. That limits three findings and it is stated where it bites. One finding raised to me was
**withdrawn** on my own reading and is recorded at `DOC-D4` with the reason.

**Severity, per [README.md](README.md).** For documentation I read A - "wrong behaviour on the layout,
or data silently lost" - as **what acting on the sentence would cost**: a comment that would send
somebody to make a wrong change to the running railway is A, however small the edit.

---

## What this pass actually found

The dangerous staleness is not spread evenly. Almost all of it is three mechanisms, and naming them is
worth more than the individual findings, because each one is greppable.

**1. A change split one thing in two, and only one half got its prose updated.** `ff6368bb` split the
tail-release rule into a clearing predicate and an unlocking predicate; four comments in `Layout.java`
still describe the single predicate that existed on the 27th, including the javadoc that now heads the
wrong one of the two with its `@return` inverted (`DOC-A1`, `DOC-A2`, `DOC-B4`, `DOC-B5`, `DOC-B6`).
`FR-037` split the diagram's annotation into a Point path and a track path, and the javadoc above it
still says "Points only" (`DOC-B7`).

**2. An insertion landed between a javadoc and the method it was written for.** Java attaches only the
last doc block before a declaration, so the older one documents nothing and the method it described is
left bare. Three landed this week (`DOC-A3`, `DOC-B12`, `DOC-B13`). This project already knows about
this failure mode, already has a regression test for it, and the test did not stop them - which is
`DOC-B1`, and is the most useful thing in this review.

**3. A rule was corrected in one paragraph and left standing in another paragraph of the same file.**
`issues.md` says a feature request gets an `MT-###` at line 6 and says it does not at line 30.
`LayoutEditor.java` says four methods were deleted 46 lines above the first of them. Five separate
documents say the issue State uses "three words" over a tool that has recognised four since
2026-08-22.

There is also a fourth thing, which is not a pattern but a fact: **the user manual still contains a
paragraph and a screenshot of a window that was deleted a week ago**, plus seven keyboard shortcuts
for it (`DOC-B8`, `DOC-B9`).

---

## A - acting on it changes the railway, or silently loses a setting

| | | |
|---|---|---|
| **DOC-A1** | `Layout.java:3387-3423` - the tail-release javadoc heads the wrong method, and its `@return` is inverted | Open |
| **DOC-A2** | `Layout.java:757-761` - "a turnout under the middle of a train is still refused" is false again, under a notice saying it was corrected | Open |
| **DOC-A3** | `LayoutEditor.java:3707-3728` - the only warning against growing the diagram at the top is orphaned, and `growEdges` is undocumented | Open |
| **DOC-A4** | `LayoutEditor.java:4818-4821` - `settleUnsavedWork` claims a restore that happens in a different method | Open |
| **DOC-A5** | `AutomationAPI.md:428` - names `trainLength` as the point's length key; the real key is `maxTrainLength`, so length restrictions silently never engage | Open |
| **DOC-A6** | `AutomationAPI.md:400-401` - `locReversible` does not exist, and `"terminus" : "true"` is rejected as a non-Boolean | Open |

### DOC-A1. The javadoc that would have you unlock track under a train

`src/org/traincontrol/automation/Layout.java:3387-3423`. The block opens:

> `Whether an edge the head has already passed must still be held for the train's tail.`

and closes:

> `@param edgesSince how many edges have been completed since it was queued`
> `@return whether to keep holding it`

It is attached to `tailHasProvablyPassed`, declared at **3424**:

```java
public static boolean tailHasProvablyPassed(int travelledOnThisPath, int behind,
    Integer trainLength)
```

Four contradictions, all in the same direction:

1. **The polarity is inverted.** `tailHasProvablyPassed` returns `true` when the tail HAS passed -
   when the edge need **not** be held. The body says so twice: `if (travelledOnThisPath <= 0) return
   true;` (3434) and `return behind >= length;` (3440). `@return whether to keep holding it` is the
   opposite of what it returns.
2. **`@param edgesSince` names a parameter this method does not have.** Nor does the doc mention
   `justTravelled`. Both belong to `tailMayStillBeOn` (3441).
3. **It refers to its own method in the third person** (3396-3397): *"`tailHasProvablyPassed` is what
   may release a LOCK... **This method** adds a guess on top of it"*. "This method" now resolves to
   `tailHasProvablyPassed`, which is the one that adds no guess.
4. **"Three ways an edge is let go" (3401)** - this method implements two. The third, which the
   paragraph calls *"the one that was lost"*, is at 3457, in the other method.

`tailMayStillBeOn` - which is what the block describes, and whose name matches its opening sentence -
has no javadoc at all.

**What it would cost.** The call site is `Layout.java:4911`:

```java
if (!tailHasProvablyPassed(travelledOnThisPath, waiting[1],
    loc.getTrainLength()))
{
    continue;
}
```

That `!` is correct against the body and backwards against the javadoc. A maintainer who trusts
`@return whether to keep holding it` reads the negation as a bug and removes it - and every pending
edge is released the moment the head leaves it. In non-atomic mode another train is then routed onto
track this one is standing on. That is precisely the collision `ff6368bb` was written to prevent.

This is the most dangerous sentence in the tree right now because everything around it is persuasive
and correct: 3395 says *"Two standards of proof"*, and 4904-4909 says *"UNLOCKED only on proof... the
guess about unmeasured track is not good enough"*. The `@return` line sits above both saying the
reverse.

**The fix is a move, not a rewrite.** The block belongs on `tailMayStillBeOn`, where every sentence in
it is true, with `justTravelled` added to the `@param` list. `tailHasProvablyPassed` then needs a new
and much shorter one.

### DOC-A2. The turnout guarantee is false again, and the correction notice hides it

`src/org/traincontrol/automation/Layout.java:757-761`, inside `getActiveAccs`:

> `// What this deliberately does NOT do is go by the train's position alone.  An edge is`
> `// only cleared once a train's LENGTH has gone past its END, so a turnout under the`
> `// middle of a train is still refused.`

It is not. `tailMayStillBeOn` clears an edge whenever the edge just traversed had no length, however
much train is behind - `Layout.java:3457`:

```java
if (justTravelled == 0 && edgesSince > 0) return false;
```

The counterexample is written down by the code itself, twenty lines above, at `Layout.java:3410-3411`:

> `Edges [100, 100, 0] and a train of 250 release edge 0`
> `with 150 of the train still on it, which is exactly why it may not decide a lock.`

So one comment asserts the guarantee and another supplies the case that breaks it. The code agrees
with the second.

**What makes this worse than an ordinary stale comment** is the paragraph immediately below, at
763-768:

> `// That sentence was false for a day and this comment asserted it anyway. The tail`
> `// bookkeeping released the whole batch of waiting edges as soon as THEY added up to`
> `// the train's length...`

A correction notice attached to a sentence that is false again reads as *freshly verified*. A reviewer
who sees "this was wrong once and has been fixed" stops checking. It was checked, and it is wrong
again, for a different reason.

**What it would cost.** `getActiveAccs` decides whether the UI warns before a hand-thrown accessory on
an active route. Adam's railway has lengths on the platforms and nowhere else - which is exactly the
shape (`justTravelled == 0`) where this fires. A reader is being told a switch cannot be thrown under a
train, on a railway where it can.

**Limit of this finding.** I did not run anything, so I cannot say how often the warning is actually
suppressed. The comment is false either way; the frequency is what decides whether there is also a
*code* finding underneath it, and somebody should look. That is the one item here I would not close
from a desk.

### DOC-A3. The only written warning against growing the diagram at the top documents nothing

`src/org/traincontrol/gui/LayoutEditor.java:3707-3728`:

> `Grows the diagram by one: a column on the right and a row at the bottom.`
>
> `NOT a row at the top, which is what was asked for and what the first version of this did.`
>
> `Inserting a row at the top moves every tile on the page down by one - and everything autonomy`
> `knows about that page is keyed by SQUARE. Stations, protecting signals, barred arrival sides,`
> `parking and reversing marks, home locomotives, station captions: all of them name a square, and`
> `none of them would move. A user with a set-up page who pressed "+" to make room would find`
> `every station one row above its platform, every signal pairing pointing at plain track, and`
> `every arrival restriction applied to the wrong square - silently, with the diagram still`
> `looking exactly right.`

That block is immediately followed by a second javadoc at **3729** and then `shiftUp()` at **3751**.
Java attaches only the last block, so this one attaches to nothing. `growEdges()` is at **4009**, 280
lines away, with **no javadoc at all**.

**What it would cost.** It is the whole argument against a change that a user has asked for and that
looks trivially symmetric - `shrinkEdges` takes away the same two edges, so adding at the top and
bottom reads as the obvious mirror. The paragraph explaining why that silently corrupts an entire
page's autonomy setup is not visible from the method, from an IDE tooltip, or from generated javadoc.
It ends *"FOR ADAM: the top row is deliberately not done. Say the word..."* - an open question to the
author, addressed to nobody who will find it.

**And the user manual already documents the forbidden behaviour as shipped.** `Readme.md:457`:

> `The editor now has a matching pair of size controls: one adds a column on the right and a row at`
> `the top and bottom, the other takes the same three away.`

`growEdges` calls `addRowsAndColumns(1, 1)` (`LayoutEditor.java:4022`), which appends one column right
and one row at the bottom (`base/LayoutDiagram.java:586-618`). Two edges, nothing at the top. So the
changelog describes the behaviour this javadoc exists to forbid, the javadoc forbidding it is
invisible, and anyone reconciling the two by making the code match the changelog does the exact damage
the paragraph describes. That pairing is why this is A rather than B.

### DOC-A4. `settleUnsavedWork` claims a restore that happens somewhere else

`src/org/traincontrol/gui/LayoutEditor.java:4818-4821`:

> `// Discarding the SETUP has to happen here, because the setup is shared: the window that`
> `// opens next is looking at the same session, so edits left in it would survive a discard.`
> `// Discarding the DIAGRAM happens by itself - layoutEditingComplete re-reads the pages from`
> `// disk, and undoAutonomyEdits below puts the setup back as it was found.`

There is no `undoAutonomyEdits` below that point in the method; the body ends at `return true;`
(4841). Every call to it is in another method - 4753, 4936, 5172. And `completeExitDiscard`'s javadoc
at **4728** says the opposite in as many words:

> `...\`undoAutonomyEdits\`, and that is the CALLER'S job.`

**What it would cost.** `settleUnsavedWork` is the shared gate every exit door goes through. Somebody
adding a new door reads this comment, sees the restore described as automatic, and does not call
`undoAutonomyEdits`. The result is a half-finished Discard: the diagram is rewound from disk and the
autonomy setup is not - so a station the user dragged, then discarded, stays dragged. Discarded edits
surviving a discard is data loss in the direction the user cannot see, which is the worst direction.

Nothing is broken today; all current doors call it. This is a trap laid for the next one, and the
comment is what lays it.

### DOC-A5. `AutomationAPI.md:428` names the wrong JSON key, and the failure is silent

> `You can specify the train length for any locomotive (via the optional trainLength integer JSON`
> `key), and the maximum allowed train length for a station (via the trainLength integer JSON key),`
> `for any locomotive entry within the points list.`

The point's key is **`maxTrainLength`** - `Layout.java:6651`:

```java
if (point.has("maxTrainLength"))
{
    if (point.get("maxTrainLength") instanceof Integer && point.getInt("maxTrainLength") >= 0)
```

`trainLength` at `Layout.java:6840` is the *locomotive* key. A point carrying `trainLength` matches
nothing: the block is skipped and nothing is logged. The station keeps the default of 0, which the
same paragraph correctly says "disables length restrictions".

**What it would cost.** Someone follows this section to stop long trains stopping at short platforms,
writes `trainLength` on every station, sees no error, and gets no protection - the exact failure the
section exists to prevent, silently. The wrong key is one clause inside an otherwise correct
paragraph; the very next sentence, and line 430, both name `maxTrainLength` properly.

### DOC-A6. `AutomationAPI.md:400-401` - a terminus configuration that will not load

> `For any Point that represents a terminus station (station must be true in the JSON),`
> `also specify "terminus" : "true".  For the corresponding point/locomotive, set`
> `"locReversible" : "true".`

- **`locReversible` does not exist.** `grep -rn "locReversible" src/` returns nothing. The key is
  `reversible` (`Layout.java:6864-6868`). A locomotive configured by this line is silently not
  reversible, and therefore cannot be sent to the terminus the same line just told you to build.
- **The quoted `"true"` is rejected.** `Layout.java:6687` gates on `point.get("terminus") instanceof
  Boolean`; the string is not, so it falls to the `else` at 6701 and calls
  `layout.invalidate(I18n.f("autolayout.errorTerminusInvalidValue", ...))`. The configuration will not
  load. The document's own worked example at line 255 uses `"reversible" : false` - unquoted, right
  key - so the file contradicts itself.

The terminus half fails loudly, which is a mercy. The `locReversible` half fails silently, which is
why this is A: the user fixes the loud error, the file loads, and the flag they think they set is not
set.

---

## B - incorrect results, or a reader seriously misled

| | | |
|---|---|---|
| **DOC-B1** | `testJavadocsAreAttached.java:33-35` - "a new one fails the build with the file named"; it pins a total, and three new ones landed this week | Open |
| **DOC-B2** | `LayoutEditor.java:3699-3705` - says four methods were removed; they are 46 lines below and on the menu | Open |
| **DOC-B3** | `LocIconCropDialog.java:1492` - `@return` guarantees exactly what the body renounces | Open |
| **DOC-B4** | `Layout.java:754-755` - "the same computation... not a second opinion" | Open |
| **DOC-B5** | `Layout.java:414` - `clearedEdges` promises "tail and all" | Open |
| **DOC-B6** | `Layout.java:4831` - "one answer... computed once" above a loop that computes two | Open |
| **DOC-B7** | `AutonomySession.java:3987` - "Points only - no direction arrows at all" heads the method that draws them | Open |
| **DOC-B8** | `Readme.md:132-134` - a paragraph and a screenshot of the deleted graph window | Open |
| **DOC-B9** | `Readme.md:228-235` - seven keyboard shortcuts for a window that no longer exists | Open |
| **DOC-B10** | `TrainControlUI.java:21607-21609` - "the mark was moved to the MIDDLE"; it was moved back the next day | Open |
| **DOC-B11** | `LayoutLabel.java:1166-1167` - "can never cover their text", which OB-117 was filed because it does | Open |
| **DOC-B12** | `TrainControlUI.java:2732-2741` - `mountEditPageMenu`'s javadoc now heads `guardLayoutMenu` | Open |
| **DOC-B13** | `AutonomyEditorPanel.java:1674-1685` - two menu-item javadocs orphaned onto a boolean predicate | Open |
| **DOC-B14** | `LoadingSpinner.java:334-338` - recommends the test practice that hid OB-129 | Open |
| **DOC-B15** | `AutonomyMenu.java:375-378` - the separator rule describes an order the same commit changed | Open |
| **DOC-B16** | `LayoutGrid.java:523-530` - `owner`'s javadoc endorses the rule found to be a bug, on a field nothing reads | Open |
| **DOC-B17** | `AutomationAPI.md:384-388` - the point-shape legend is inverted, and `TileAnnotation.java` says so | Open |
| **DOC-B18** | `AutomationAPI.md:472` - `Layout.addTimetableEntry` is private | Open |
| **DOC-B19** | `Readme.md:377`, `:174`, `Automation.md:163-173` - the route-choice list reads as exhaustive and omits an option | Open |
| **DOC-B20** | `issues.md:6` - the retired rule that a feature request gets an `MT-###` still stands in the header | Open |
| **DOC-B21** | `issues.md:81-82` - two rows use a State word nothing recognises, and count as open forever | Open |
| **DOC-B22** | `manual-tests/README.md:88-90` - "a bug... gets an entry in tests.md" is not what 24 receipts did | Open |
| **DOC-B23** | `manual-tests/README.md:110-112` - the attribution rule documented is not the rule enforced | Open |
| **DOC-B24** | `manual-tests/README.md` - silent on `triagedb.py`, which writes the Disposition field rule 4 governs | Open |
| **DOC-B25** | `tests.md:26`, `:57` - the ledger lists two entries that are `fixed validated` | Open |
| **DOC-B26** | `2026-08-22-f2-review.md:7-8` - claims to follow `README.md`'s severities, then restates them wrongly | Open |
| **DOC-B27** | Prefix collisions: `CR` names two documents, `FR` names three things | Open |

### DOC-B1. The guard against orphaned javadocs counts them; it does not identify them

`test/regression/testJavadocsAreAttached.java` exists because this project has had this defect
repeatedly, and its class javadoc is one of the best paragraphs in the tree (`:28`): *"In a codebase
where the comment IS the safety mechanism, a comment attached to the wrong member is the same kind of
defect as a guard on the wrong branch."* Then, `:30-35`:

> `**A ratchet, not a clean sheet.** There are over a hundred, most of them harmless... So this pins`
> `the number and requires it to go DOWN, never up: a new one fails the build with the file named,`
> `and every one that gets fixed can be banked by lowering the cap.`

**"a new one fails the build with the file named" is not true of the implementation.** The test sums a
count across `src/` and compares it to a constant - `:43`, `private static final int ALLOWED = 98;` -
with two asserts, `:72` (`found <= ALLOWED`) and `:78` (`assertEquals(found, ALLOWED)`). Nothing tracks
*which* blocks are orphaned. A change that fixes one orphan and creates another leaves `found` at 98
and passes, in silence, with no file named - `worst` is only interpolated into the first assert's
message, which does not fire.

**Three new orphans landed this week**, all from the same mechanism the test was written for:
`LayoutEditor.java:3707` (`DOC-A3`), `TrainControlUI.java:2732` (`DOC-B12`), and
`AutonomyEditorPanel.java:1674` (`DOC-B13`). All three are in the tree at `eac0e392`.

I did not run the suite, so I cannot say whether it is currently red or whether three offsetting fixes
kept the total at 98. **Both branches are findings, and they need opposite responses**, which is why
this is worth someone's five minutes:

- If it is **green**, the pin absorbed three new orphans and the class javadoc's promise is false as
  written - the guard cannot do the job its own best paragraph describes.
- If it is **red**, it has been red for some part of this week and the message names 100-odd files
  without saying which three are new, which is nearly as useless.

The second assert is also misdescribed: *"requires it to go DOWN, never up"* describes a one-sided
ratchet, and `assertEquals` forbids going down too without editing `ALLOWED`. That is deliberate and
the failure message at `:79-81` explains it - only the prose is wrong.

**The shape of a fix**, since the finding is not much use without one: record the orphans as a set of
`file:line`-anchored signatures rather than a count, and diff. Then a swap fails, and it fails naming
the new one. That is the same lesson as `README.md:200-206` - *"A structural check that cannot tell
'correct' from 'badly broken' is not a check"* - one level up: a check that cannot tell one orphan from
another cannot tell a fix from a regression.

### DOC-B2. Four methods described as removed, 46 lines above the first of them

`LayoutEditor.java:3699-3705`:

> `// The four "shift the whole diagram" wrappers used to live here.`
> ...
> `// Removed rather than left behind: a method with no caller is the half of a removal that gets`
> `// forgotten, and the next person to read this file would have to work out whether it was still`
> `// wanted.`

`shiftUp()` is at 3751, `shiftDown()` 3804, `shiftLeft()` 3854, `shiftRight()` 3899, all wired into
`LayoutEditorRightclickMenu.java:458-468`. The javadoc at 3729-3732 - the block that displaced
`DOC-A3` - even opens *"Restored after being taken out with the rest of the bulk operations when
multi-select arrived."*

So the file says, within 30 lines: these were removed / these were restored. A reader who takes the
first at its word reads the next 250 lines as dead code and may delete a menu item's implementation.

### DOC-B3. `LocIconCropDialog.java:1492` - the `@return` the body explicitly renounces

> `* @return a rectangle wholly inside the source picture, never empty`

`sourceRect()` (1494) computes four rounded quantities and returns them unbounded. Its own body
comment, at **1505**, is the refutation:

> `// NOT clamped into the picture any more.`
> ...
> `// Clamping here would defeat the point. The frame is allowed to hang off the photograph`

A caller trusting "wholly inside" hands the result to `BufferedImage.getSubimage` and gets a
`RasterFormatException` the first time a crop is dragged past an edge - which is why `getCroppedImage`
routes through `wholelyInside` / `contentOf` (1335, 1355) instead of calling it directly. The javadoc
is the contract; the contract is the one thing in that method that has not been updated.

### DOC-B4, B5, B6. Three more survivors of the tail-release split

**DOC-B4**, `Layout.java:754-755`, in `getActiveAccs`:

> `//                The tail bookkeeping in executePath is what`
> `//                says so, and it is the same computation that decides when an edge is`
> `//                safe to unlock - not a second opinion about the same thing.`

It is now exactly a second opinion: clearing calls `tailMayStillBeOn` (4874), unlocking calls
`tailHasProvablyPassed` (4911), and `Layout.java:3395` says so - *"Two standards of proof, because the
answer decides two different things"*. This sentence is the stated justification for `getActiveAccs`
trusting `clearedEdges` alone at 771, so it is load-bearing: somebody re-merging the two predicates
"since they're the same computation" restores the unsafe unlock of `DOC-A1`.

**DOC-B5**, `Layout.java:414`, the `clearedEdges` field doc:

> `Edges of a running path that the train has completely finished with - tail and all.`

An edge now enters this set on the looser guess (4893, gated only by `tailMayStillBeOn`), so "tail and
all" is not established over unmeasured stretches. This is the first thing anyone reads before using
the map, and it promises precisely the guarantee `ff6368bb` removed.

**DOC-B6**, `Layout.java:4831`:

> `// Two questions, one answer to when the tail has gone by, computed once.`

The loop below computes two answers. The two lines above it - *"cleared - ... Both modes / unlocked -
... Non-atomic only"* - are still exactly right, which makes the stale summary read as the invariant
those two bullets share.

### DOC-B7. "Points only - no direction arrows at all", above the method that draws them

`src/org/traincontrol/automationui/AutonomySession.java:3987-3990`, the javadoc of
`staticAnnotationFor` (declared 3995):

> `* Points only - no direction arrows at all.  The diagram tab is where trains are WATCHED, and the`
> `* question there is where they are and where they are heading next, which the running overlay`
> `* answers.  Directions belong to the editor, where they are being decided; drawn here they were`
> `* just a page of green arrows over a railway nobody was configuring.`

Both halves were reversed by `3a52ec14` (FR-037) and `98481418`. The body builds `directionMarks(tile)`
at 4021 and returns a non-null annotation for tiles that are **not** Points at 4028-4031. The gate,
`TrainControlUI.diagramShowsRestrictionArrows()`, now defaults to `true`, so this is the default
behaviour rather than an opt-in corner.

**What it would cost.** The inline comment at 4015-4018 records that an early
`if (!reducer.getPoints().containsKey(tile)) return null;` was the first attempt and was the bug:
*"a restriction is not a fact about sensors. It is a fact about TRACK... and almost none of those carry
a sensor, so the option appeared to do nothing at all."* The stale javadoc eight lines above tells a
reader that guard is correct. Reinstating it silently removes FR-037 for all track without a sensor,
which is nearly all track.

B rather than A because the blast radius is the diagram: a missing arrow misinforms an operator but
does not itself move a train.

### DOC-B8, B9. The manual still walks a new user into a window that was deleted

`GraphViewer` is gone from `src/`, and `TrainControlUI.java:3266-3282` hides the whole JSON tab
whenever a local layout exists, commenting *"The graph window is gone, so this reopens nothing."*

**DOC-B8**, `Readme.md:132`, in the Full Autonomy section:

> `The graph UI will show you which routes are active, which edges are locked, and where different`
> `trains are stationed.  This can also help you debug your graph as you build it.  While trains are`
> `not running, you can right-click any station to reassign a train and view possible routes.`

followed at `Readme.md:134` by a 500px screenshot of it (`assets/graphview.png`). Every capability
listed is real and all of them live on the track diagram now. This is the worst passage in the manual:
a whole paragraph plus a picture, in the section a new user reads first, of software that cannot be
opened.

**DOC-B9**, `Readme.md:228-235`, the `* Autonomy Graph UI` shortcut block - `Control+V`,
`Delete/Backspace`, `Control+X`, `Control+E`, `Control+U`, `Control+S`, `Control+H`. None exist;
`AutonomyEditorPanel` binds only `VK_ESCAPE` (741, 4228). There is no successor set, and the feature
ledger records the loss at `docs/plans/autonomy-ui-feature-ledger.md:107` - *"TODO - the whole
cut/paste move-a-locomotive idiom is gone"*. **Delete this block rather than rewriting it**: the
deletion is the honest signal that the idiom went with the window.

Related and lower: `Readme.md:171` *"Full UI for editing autonomy graph models"* is flatly false.
`Readme.md:38` and `:169` are misleading rather than false - the model still exists in
`automation/Layout.java`, but it is derived from the diagram rather than authored. `Readme.md:128`
deserves a caveat rather than a cut: `mountAutonomyControls` (`TrainControlUI.java:3197-3220`) does
restore the JSON tab when there is no local layout, its comment saying *"the JSON window it also hosts
is still the only way to set autonomy up"* in that case.

### DOC-B10. "The mark was moved to the MIDDLE" - it was moved back the next day

`TrainControlUI.java:21607-21609`:

> `And the hole is not in a corner any more. The mark was moved to the MIDDLE of the picture in the`
> `same round, which is exactly where a person aims when they right-click it.`

`cb1d61a0` (2026-08-27) put it back. Line **21621** reads *"UPPER RIGHT (Adam, 2026-08-27, having seen
it centred)"*, and the code builds `FlowLayout(RIGHT,0,0)` into `BorderLayout.NORTH`. This comment is
the stated justification for the `isRightMouseButton` → `setLocIcon(activeLoc)` passthrough three lines
below it; a reader concluding "it is a corner again, so the passthrough is moot" deletes it and
restores the dead zone that round was filed to remove.

### DOC-B11. "Can never cover their text", which is what OB-117 was filed because it does

`LayoutLabel.java:1166-1167`:

> `Note that station and address labels are z-ordered above tiles by LayoutGrid, so this can never`
> `cover their text.`

`liftAboveLabels` (1079) does `setComponentZOrder(this, 0)` while a train is on the square. OB-117's
`keepCaptionsInFront` (1042) hands the front back **only to `StationCaption`**; address labels are
plain `JLabel`s and stay behind a lifted tile, which the inline comment at 1100-1105 states outright.
The javadoc invites deleting `keepCaptionsInFront` as redundant, which reproduces OB-117 verbatim - a
locomotive icon painting a caption out with blank white. Same family: **line 1059**, *"Brings this tile
in front of the address and caption labels"*, when since OB-117 the captions are explicitly put back
in front of it.

### DOC-B12, B13. Two more javadocs orphaned by an insertion

**DOC-B12**, `TrainControlUI.java:2732-2741`, written for `mountEditPageMenu`:

> `"Edit Layout Page..." on the Layouts menu, listing the pages. ... Rebuilt whole on every mount,`
> `because the pages change under it.`

`5e45daaf` inserted `guardLayoutMenu` between the comment and its method. The block is now followed by
a second javadoc at 2742 and `guardLayoutMenu()` at 2754; `mountEditPageMenu()` is at **2944 with no
javadoc**. A reader of `guardLayoutMenu` is told it is rebuilt whole on every mount, which it is not.

**DOC-B13**, `AutonomyEditorPanel.java:1674-1685` - *"A menu item that runs something and then
redraws"* (1674) and *"A menu item that runs something, redraws, and flashes the square it changed"*
(1678) now stack above `hasItemsBesidesTitle(JMenu)` at **1694**, which creates no item, redraws
nothing and flashes nothing. `item(String, Runnable)` at **1707** is left undocumented - and it
swallows `RuntimeException`, shows `error.generic` and logs, which is the contract that actually
matters to a caller.

Both are `DOC-B1`'s mechanism. Neither is dangerous on its own; they are here because three in one
week is the finding.

### DOC-B14. The spinner javadoc recommends the practice that hid OB-129

`LoadingSpinner.java:334-338`:

> `Advances the animation by one frame without waiting for the timer. Public so that a test can step`
> `the cycle and photograph it. ... stepping is the same arithmetic with the clock taken out of it.`

It is no longer the same arithmetic. The timer (116) now does
`frame = frameAt(System.currentTimeMillis() - startedAt)` - an absolute clock read that **overwrites**
a stepped value on the next 60ms tick. Two comments added in the same commit say so, at 126-128
(*"every existing test of this class drives `advanceOneFrame` by hand, which is exactly why a broken
timer went unnoticed"*) and 351-353.

A test written from this javadoc reproduces the OB-129 blind spot exactly: green tests over a timer
that never fires. That is what makes it B rather than C - it is a comment that actively recruits the
next author into the defect the same commit fixed.

### DOC-B15. The menu's only placement rule describes an order the same commit changed

`AutonomyMenu.java:375-378`:

> `Everything above chooses which setup is in force and everything below is housekeeping on the file`
> `that holds it`

In `eac0e392` itself, the Manage submenu moved *up* to line 343, under a comment reading *"DIRECTLY
UNDER the configuration it manages"*. Above the separator now: Configurations, Import/Export, **Manage**
(duplicate / rename / delete / unload - the housekeeping), Global settings. Below it: **Edit** and
**Pages**, and page exclusion calls `reloadActiveDiagramConfiguration()`, so it changes the railway
too. Both halves of the sentence are now wrong.

This is the only stated rule for where a new item goes in that menu, so a new housekeeping item placed
"below" lands beside the editor - which is the grouping OB-051 and this very commit were undoing.

### DOC-B16. A javadoc endorsing the rule that was found to be a bug, on a field nothing reads

`LayoutGrid.java:523-530`, on `owner`:

> `Kept so that discard() can hand back the labels this grid registered. They are registered against`
> `the PANEL ... so "the labels this grid owns" and "the grid being replaced over this panel" are the`
> `same question.`

`owner` is assigned once (674) and never read. `discard()` (629) calls
`forgetLayoutStations(registeredCaptions, container)` - the explicit per-grid list. The
`registeredCaptions` javadoc eleven lines below (534-543) says the opposite and explains why: one panel
serves every page, so forgetting by panel blanks every page's captions permanently.

Two javadocs in one file, eleven lines apart, giving opposite answers to "how do we know which captions
are ours" - and the one that is wrong is the one describing the approach that was tried and failed.

### DOC-B17. The point-shape legend is inverted, and the source already says so

`AutomationAPI.md:384-388`:

> `* Circle - regular station.  Any train can stop here.`
> `* Square - terminus station...`
> `* Diamond - intermediate point that is not a station...`

The live legend is `automationui/TileAnnotation.java:139-155`: **shape** says whether trains turn,
**size** says whether it is a station. The source comment names this document and spells out the
consequence, at `TileAnnotation.java:148-153`:

> `Two of those four claims are now the`
> `wrong way round: a plain point is a small CIRCLE, and a diamond means trains MAY turn, so a`
> `reader interpreting a screenshot by the old legend would read a may-turn station as a plain`
> `point (TD-15).`

Found, written up in the code, and the document never changed. The point *colours* at `:378-382` are
still correct (`TileAnnotation.java:136-137`).

### DOC-B18. `AutomationAPI.md:472` documents a private method

> `Timetables can also be built programmatically via Layout.addTimetableEntry or Layout.setTimetable.`

`addTimetableEntry` is private at both overloads - `Layout.java:611` and `:622`. A script author
following it gets a compile error; `setTimetable` (`:5831`) is public and is the only route. Loud
failure, but it is in the scripting reference, where "does this compile" is the entire contract.

### DOC-B19. The route-choice list reads as complete and is not

`Readme.md:377`:

> `You can now choose how trains pick their route when more than one will do: at random, past the`
> `fewest or the most stations, over the shortest or the longest track, or across the fewest or the`
> `most sensors.  It is under Autonomy - Route Choice`

`Layout.PathPreference` has eight members: `LEAST_RECENTLY_VISITED` (`Layout.java:154`) is missing.
The same exhaustive-looking enumeration appears at `Readme.md:174` and `Automation.md:163-173`.

`Least Recently Visited` is the option that answers "trains never go to the far corner", so a user with
that complaint reads a complete-looking list, concludes the feature cannot do it, and stops. The menu
label is wrong in all three places too: it is `Choose Routing Logic...` (`messages.properties:335`).
The string "Route Choice" appears in no bundle.

### DOC-B20. `issues.md` states the retired rule and its replacement, 24 lines apart

`docs/manual-tests/issues.md:4-6`, the header:

> `Claude reads here, turns each item into a finding in docs/reviews/ (for a bug, under that round's`
> `prefix) or works it directly (for a feature request), opens an MT-### entry in tests.md to cover`
> `it, and clears the item out of the Inbox.`

"opens an `MT-###` entry... to cover it" is applied to both kinds. That is the rule that was retired,
and `issues.md:27-33` - the same file - says so: *"A feature request is tracked directly instead, by
default... It only gets promoted to an MT-### tag if the eventual work turns out to need a genuine
hands-on test."*

`manual-tests/README.md:92-98` agrees and names the cost: *"`MT-094` is what the default used to
produce: a feature nobody had even designed yet... sitting in the Tests ledger indistinguishable from
one."* A round that reads the header and stops - which is what a header is for - re-creates MT-094.

### DOC-B21. Two rows carry a State word nothing recognises

`docs/manual-tests/issues.md:81-82`:

> `| 2026-08-28 | FR-038 | feature request | Mis-filed: the crop editor quirk is a bug, re-filed as OB-125 | cancelled | - |`
> `| 2026-08-28 | FR-039 | feature request | The request to cancel FR-038, which is done - nothing of its own to work | cancelled | - |`

The documented fourth word is **`declined`** (`manual-tests/README.md:93` and `:176`, `issues.md:30-31`)
and it is what `triage.py` implements, at `:102`:

```python
ISSUE_STATE_COLORS["declined"] = "#8a3a3a"
```

`cancelled` is not in that map, so the rows render uncoloured; and `triage.py:1473` decides openness
with `is_open = state_text.lower() not in ("fixed validated", "declined")`, so **both rows count as
open, permanently**. No row anywhere in `issues.md` uses `declined`. The documented terminal state has
never been used, and the word used instead cannot terminate anything.

### DOC-B22. "A bug... gets an entry in tests.md" - 24 of them did not

`manual-tests/README.md:88-90`:

> `A bug becomes a finding in docs/reviews/ under that round's prefix, gets fixed there, and gets`
> `an entry in tests.md with a new MT-### tag and the disposition needs test - a bug fix needs a`
> `repeatable hands-on check that the regression stays fixed, which is exactly what tests.md is for.`

Unconditional, and `issues.md:27-29` repeats it. In the receipt table, **24 bug rows carry a State and
no Became** - 19 at `fixed unvalidated` and 5 at `fixed validated`, including everything filed on the
27th and 28th (`issues.md:75-79`). No amendment exists in either file; I grepped both.

`issues.md:69-70` shows this drifted rather than being changed on purpose - it contemplates only one
kind of item using State: *"Exactly one of State or Became is filled in for any row - **a feature
request** either gets its own tag, or it does not, never both."*

Two readings are possible and they want opposite fixes, which is why this is a finding rather than a
note: either the rule stands and 24 bug fixes are missing their hands-on check, or it has been
superseded in practice and both files should say what replaced it. That is Adam's to settle.

### DOC-B23. The attribution rule documented is not the rule enforced

`manual-tests/README.md:110-112`:

> `verify-ledger checks it: an Inbox entry whose body carries more than one paragraph-leading bold`
> `run must have a dated attribution on every one after the first.`

`undated_followups` (`triage.py:2952`, wired into `clean` at `:3137` and `:3161`) does something else,
at `:2984-2991`:

```python
for run in _re.finditer(r"\*\*([^*]{2,90}?)\*\*", entry.group(0)):
    label = run.group(1).strip()
    if not any(label.startswith(a) for a in authors):
        continue
    if not _re.search(r"20\d\d-\d\d-\d\d", label):
        out.append({"ref": ref, "attribution": label[:70]})
```

- **Not paragraph-leading** - the regex matches any bold run anywhere in the block.
- **No "more than one" precondition and no exemption for the first** - a single undated `**Adam.**` is
  flagged, which the README says it should not be.
- **Only `Adam` and `Claude` qualify** (`:2986`), so a paragraph-leading bold run under any other
  attribution is never checked; the rule as the README states it is not enforced at all.

The function's own docstring at `triage.py:2960-2962` is wrong in the same two ways, so **the README is
repeating a stale docstring rather than reading the loop below it**. Fixing the README without fixing
the docstring reintroduces this. Because `undated_followups` feeds `clean`, which gates the exit code,
a round can be handed exit 3 for a rule written down correctly nowhere.

### DOC-B24. The README documents one of the folder's three scripts

`manual-tests/README.md` has sections called "The triage app" and "The query API" and names `triage.py`
throughout. It never mentions `triagedb.py` or `where-are-the-trains.py`; grepping for either returns
nothing.

That matters for `triagedb.py`, which writes the field rule 4 reserves - `:472-474`:

```python
block, changed = re.subn(r"(?m)^(\*\*Disposition:\*\*[ \t]*).*?([ \t]*)$",
                         lambda m: m.group(1) + disposition + m.group(2),
                         row["block"], count=1)
```

exposed as the CLI verb `set` (`:786-788`) alongside two more writers, `comment` and `file`
(`:790-800`). None performs the changed-on-disk check `triage.py:436-440` performs before appending to
`tests.md`. So `README.md:283-285` -

> `If a tool other than triage.py needs to touch either file, it must follow the same shapes`

- is addressed to a hypothetical tool that already exists, sits in the same folder, and skips the
staleness check. A round running while triage.py is open in another window can lose a write.

Smaller, same section: `README.md:210-211`'s *"the app checks the file has not changed on disk since it
was loaded before writing to it"* is true of the `tests.md` comment path only; `IssuesDoc.file`
(`triage.py:716-728`) goes straight to `append_to_inbox` with no check. The backup-and-atomic-replace
half is true of every write (`:141-167`).

### DOC-B25. The ledger lists two entries that are finished

`manual-tests/README.md:68` defines the ledger as *"a table of every entry NOT in **fixed validated**
and not **superseded**"*; `tests.md:18` calls it *"the whole of the outstanding work"*. Two rows do not
belong:

> `tests.md:26` `| [MT-043](#mt-043) | 2026-08-22 | A sensor nudged onto its own label | needs test | LT-A9 |`
> `tests.md:57` `| [MT-177](#mt-177) | ... | fixed unvalidated | OB-093, OB-094, OB-095, OB-096 |`

Both entries read `**Disposition:** fixed validated` (`tests.md:2498`, `tests.md:8850`). I compared all
203 entries against all 67 ledger rows: these are the only two discrepancies in either direction, and
nothing open is missing from the ledger.

The cost is named in the README itself (`:138-139`), quoting Adam: *"i got two MT tickets for the same
test. please avoid duplication and don't reopen tickets already validated."* Two stale rows is two
tests he may run again. `triage.py verify-ledger` reports exactly this; I could not run it, so this was
done by hand and should be confirmed by running it.

### DOC-B26. A review that claims to follow the house severities, then restates them wrongly

`docs/reviews/2026-08-22-f2-review.md:7-8`:

> `Prefix: **FR** (F-round review). Severity follows docs/reviews/README.md: A is data loss or a`
> `crash, B is wrong behaviour a user will hit, C is a corner, D is cosmetic or maintainability.`

`README.md:90-93` says none of that. It puts crashes at **B** ("Medium. Incorrect results, or crashes
in specific configurations"), and reserves **D** for **not defects** - then spends a paragraph
(`:109-112`) on why: *"D is not a bin for things you didn't fix. It is for things that turned out not
to be defects."*

The document's own D block is what that paragraph forbids. `2026-08-22-f2-review.md:20-22` and `:107`
hold `FR-D1` to `FR-D4`: a dead method that half-initialised a label (Removed), a sidebar that took its
width out of the diagram (Fixed), four orphaned strings and a message naming the wrong cause (Removed
and reworded), and a dialog wording question. All four are real; three were fixed. Under the house
convention they are C.

Live rather than historical: `tests.md:2803` records an entry whose **From** is `FR-D2` - a hands-on
test earned by a finding filed as "not a defect". Any severity count across the folder is wrong by this
document, in both directions.

### DOC-B27. `CR` names two documents; `FR` names three things

`README.md:99-103` gives the rule and the reason: *"Identifiers are only unique within a document, and
a cycle of any size will collide."*

**`CR` is claimed twice.** `2026-07-cycle-summary.md:26` assigns it to `2026-07-code-review.md`, which
holds `C1-C20`. `2026-08-24-conversion-review.md:5` declares *"**Prefix:** CR. Cite these findings as
CR-C1 etc."* `CR-C1` now names two unrelated findings a month apart - the exact ambiguity the rule
exists to remove.

**`FR` names three things.** `2026-08-22-f2-review.md:7` declares it as a citation prefix;
`2026-07-cycle-summary.md:26` records `FR1-FR3` inside `2026-07-code-review.md`; and
`manual-tests/README.md:113` assigns `FR-###` to feature requests, which is how the commit log uses it
(`eac0e392`: "OB-129, FR-040, FR-041"). All three are live in one field of one file: `tests.md:2536`
and `:2560` cite `FR-A1`, `:2803` cites `FR-D2`, `:3069` cites `FR-009`. A reader tells them apart only
by whether a letter follows the hyphen, which nobody wrote down.

**The underlying gap.** `2026-07-cycle-summary.md` is the only prefix registry and it covers eight July
documents. The 55 August documents have none - which is why four of them
(`2026-08-24-day-review.md:5`, `-duplication-robustness.md:5`, `-independent-application-review.md:5`,
`-reopen-audit.md:5`) each carry a hand-kept "taken elsewhere" list: four partial copies of a registry
that does not exist. Both collisions sit in gaps those lists left. `README.md:105-107` says not to
merge documents and to *"Write an index instead"*; the index for August was never written.

---

## C - narrow, cosmetic, or a link that has rotted

| | | |
|---|---|---|
| **DOC-C1** | `test/README.md:7-10` - the per-folder test counts total 76 against an actual 127 | Open |
| **DOC-C2** | `LocIconCropDialog.java:63-69` - "Eight is generous on purpose" over `MAX_ZOOM = 32.0` | Open |
| **DOC-C3** | `StationCaption.java:120-122` - describes the arithmetic OB-118 deleted | Open |
| **DOC-C4** | `TrainControlUI.java:1866-1873` - `settleAbsentPages`'s `@return` lost two of null's three meanings | Open |
| **DOC-C5** | `LayoutGrid.java:85-94` - "the right two booleans" for a three-boolean method, and no `@param` for the third | Open |
| **DOC-C6** | `LayoutEditor.java:105` - the summary line names the wrong colour, and the rest of its own javadoc refutes it | Open |
| **DOC-C7** | `docs/plans/autonomy-ui-feature-ledger.md` - last updated five days before the deletion it was written to gate | Open |
| **DOC-C8** | `docs/plans/2026-08-01-diagram-autonomy-plan.md:3-4` - "Not yet approved for implementation" | Open |
| **DOC-C9** | `docs/UI-standards.md:20` - the reference screen it says to copy is no longer in the build | Open |
| **DOC-C10** | `2026-08-19-rendering-cost.md:5` - names `test/testRenderingCost.java`, which moved to `test/ui/` | Open |
| **DOC-C11** | `2026-08-19-test-suite-timings.md:3` - "Sixty classes" | Open |
| **DOC-C12** | `reviews/README.md:10-12` - "Start there" points at an index covering 8 of 70 documents | Open |
| **DOC-C13** | `2026-08-20-tests-to-run.md` - declares itself superseded, has no Status line, stays in the open folder | Open |
| **DOC-C14** | `tests.md:10389` - MT-203 sits above three older entries, against rules 1 and 5 | Open |
| **DOC-C15** | Five documents say the issue State uses "three words"; there are four | Open |
| **DOC-C16** | `manual-tests/README.md:201`, `:232`, `:236-242` - three small untruths about `triage.py` | Open |
| **DOC-C17** | `AutonomyChecks.java:452-455` - `checkReversingGoesSomewhere` gained a parameter the `@param` list omits | Open |
| **DOC-C18** | Five menu labels and two shortcuts in `Readme.md` / `Automation.md` no longer match the bundles | Open |
| **DOC-C19** | `Readme.md:462` - a changelog entry describing a menu item that was deliberately removed | Open |
| **DOC-C20** | `Readme.md:156` - "up to 10 key mappings"; the ceiling is 50 | Open |
| **DOC-C21** | `docs/reference/README.md:12` - "the three GraphStream jars are deleted outright"; they are on disk | Open |
| **DOC-C22** | `Automation.md` - eight broken image links | Open |
| **DOC-C23** | The changelog has no entry for six features shipped in the last 29 commits | Open |
| **DOC-C24** | Eight smaller comment drifts, listed together | Open |

**DOC-C1.** `test/README.md:7-10` gives `core/` 58, `ui/` 7, `regression/` 12, `support/` 2. Actual:
**63, 16, 45, 3** - 127 classes against a documented 76, with `regression/` nearly four times its
stated size. Everything else in that file is accurate and useful, including the package-relative
resource trap and the `build.xml`-needs-a-line rule. The *"35 of 76 classes were missing"* anecdote
later in the file is history and should stay as written.

**DOC-C2.** `LocIconCropDialog.java:60-69` - *"Eight is generous on purpose"* over
`private static final double MAX_ZOOM = 32.0;`. Raised by `770b9a94`, and the same file already says so
twice (915: *"Raised from 8 at Adam's request"*; 920: *"up to thirty-two times it - so the slider
covers a ratio of sixty-four"*). Anyone "restoring" the documented value cuts magnification fourfold
for the case the dialog exists for, and breaks the slider's log mapping at 938, which assumes a span
of 64.

**DOC-C3.** `StationCaption.java:120-122` - *"The left inset is then measured back from it: full at
rest, less as the caption gets wider, and never past what was bought"*. `place()` (315) is now
`int left = Math.max(0, backShift + (tile - wide) / 2);`. At rest a station shows a dash
(`wide < tile`), so the inset is **more** than `backShift`, not "full". The sentence describes
`max(0, backShift - min(half, backShift))`, which is the defect OB-118 fixed. The correct block is at
282-299 and contradicts this field doc.

**DOC-C4.** `TrainControlUI.java:1866-1873` - *"@return ... null when the operator cancelled - in which
case the index must not be written at all"*, and two lines up *"Nothing is asked when nothing is
absent... This is a dialog nobody should ever see."* The body returns null for three reasons now: the
OB-127 not-local refusal (1888, which also shows a message dialog *before* absences are computed), the
RA-C3 unreadable-index refusal (1912), and cancel (1969). All three current callers `return` on null,
so nothing is broken; a fourth written from this contract reports "cancelled" for a refusal.

**DOC-C5.** `LayoutGrid.java:85` - *"What that leaves uncovered is whether the caller passes the right
two booleans"* - over a three-boolean signature (93-94):
`hidesStationCaptions(boolean inEditor, boolean autonomyMode, boolean pageExcluded)`, body
`return pageExcluded || (inEditor && !autonomyMode);`. There is **no `@param pageExcluded`**, the
argument that dominates the result. Added by `453a3ef4`; the prose paragraph was updated and the tag
list and the count were not.

**DOC-C6.** `LayoutEditor.java:105` - *"Where a group being dragged would land, in a paler shade of the
picking colour."* `COMPONENT_BORDER_LANDING_COLOR` (115) is `Color(0, 90, 220)`, blue; the picking
colour (102) is `Color(210, 0, 0)`. The rest of the same javadoc (107-110) refutes the summary
explicitly: *"A different COLOUR, not a paler shade of the selection... which is exactly how a pale red
landing box looked beside a red selection."* The summary line is the one an IDE tooltip shows.

**DOC-C7.** `docs/plans/autonomy-ui-feature-ledger.md:11` - *"Last updated 2026-08-16, after a
three-way review"* - on a document whose stated purpose (2-3) is to make *"the graph window can go"* a
checkable claim. The window went on 2026-08-21, five days later, with about twenty rows at TODO. At
least one has since shipped: line 74 lists *"Train icon on the diagram | Wanted, comes last | TODO"*,
and the train mark shipped on 2026-08-22 (`test/regression/testTrainMarkIsNotBlank.java`). Its last
line, gap 6, now describes an impossibility: *"May want to open in the old viewer before that window is
deleted."* C because nobody acts on it directly - but it is the only inventory of what the old window
could do that the new one still cannot, so its going stale is a slow loss.

**DOC-C8.** `docs/plans/2026-08-01-diagram-autonomy-plan.md:3-4` - *"Not yet approved for
implementation."* It was implemented; the branch is named after it. Its Phase 2 list (18-20) also names
`GraphLocAssign` for deletion, which `docs/reference/README.md:26-28` correctly says is still live and
*"the only place a locomotive's arrival and departure functions can be set"* - confirmed,
`src/org/traincontrol/gui/GraphLocAssign.java` is present. Neither file in `docs/plans/` carries a
Status line.

**DOC-C9.** `docs/UI-standards.md:20` - *"**GraphEdgeEdit** already does all of this, and is the thing
to copy rather than this table"*. `GraphEdgeEdit` left the build on 2026-08-21 and exists only as
`docs/reference/GraphEdgeEdit.java.txt`. A reader told to copy it will search `src/` and find nothing.
The fallback pointer - *"`RouteEditorFrame` is the hand-written screen built to this"* - is live and
correct, so the fix is a path rather than a rewrite.

**DOC-C10.** `2026-08-19-rendering-cost.md:5` names `test/testRenderingCost.java` *"so this report can
be re-checked rather than believed"* - the one link in that document carrying weight. The class moved
to `test/ui/testRenderingCost.java` on 2026-08-22. Several July documents carry the same class of
broken path; those are history and the archive README covers them. This one is an open document whose
value is the re-check.

**DOC-C11.** `2026-08-19-test-suite-timings.md:3` - *"Sixty classes"*, now 127. The per-class timings
are fine as history; the count reads as current. This is also the clearest instance of
`README.md:70-74`'s third category - a generated report that *"goes stale silently"* - kept in the main
folder rather than folded into a review.

**DOC-C12.** `reviews/README.md:10-12` - *"2026-07-cycle-summary.md indexes the July 2026 cycle... Start
there rather than with any single document."* Read as the general instruction it looks like, in the
third paragraph of the folder README, it sends a new reader to an index of 8 documents out of 70, all
predating the autonomy diagram. The sentence is literally true and its placement is not. See
`DOC-B27`: the missing August index is also where two prefix collisions got in.

**DOC-C13.** `2026-08-20-tests-to-run.md:3` opens *"**Superseded 2026-08-22.**... **Do not add to this
file.**"* It has no Status line, so by `README.md:48` it is `open`, and it sits in the folder
`README.md:29` reserves for *"work that still needs somebody"*. `2026-08-18-manual-test-plan.md` is the
same shape but correctly explains why it stays open (its backlog is unpicked), so only the first is a
finding.

**DOC-C14.** `tests.md:10389` - `### MT-203 - 2026-08-27` sits above `MT-201` (`:10459`), `MT-202`
(`:10502`) and `MT-200` (`:10563`), all dated 2026-08-26. Rule 1 (`manual-tests/README.md:21-23`):
*"A test written today goes at the bottom whatever it is about."* Rule 5 adds *"never reordered"*. Both
tag order and date order are broken across those four. Under the same rules: `MT-138`, `MT-140` and
`MT-141` have no `**From:**` line, against rule 3.

**DOC-C15.** Five places say the issue State uses "three words": `manual-tests/README.md:93` and
`:176`, `issues.md:30` and `:67`, and the code comment at `triage.py:1466` - plus `triage.py:100`,
whose *"so tests.md's three-word rule stays exactly three words"* sits four lines below a
`DISPOSITION_COLORS` dict with **four** keys (`:91-96`); `superseded` was added 2026-08-22.
`issues.md:67` is the worst because it enumerates - *"(`needs test` / `fixed unvalidated` / `fixed
validated`)"* - omitting `superseded`, which `ISSUE_STATE_COLORS` does inherit and does colour. One
stale number, six copies; listed once rather than six times.

**DOC-C16.** Three small untruths in `manual-tests/README.md`, all in the triage.py sections:

- `:200-202` - *"everything from **New issue** becomes a structured `OB-###` item"*. `free_observation`
  allocates by kind (`triage.py:2077`) and the dialog offers both (`:2615-2617`), so a feature request
  filed there becomes `FR-###`. `README.md:113` says this correctly; only line 201 does not.
- `:230-233` - freeform content *"is still returned, under `"freeform"`"*. The CLI emits
  `"freeform_pending"` (`:2846`) and `"has_freeform_pending"` (`:2802`); no key `"freeform"` exists. A
  round written against the documented key silently reads nothing - the one failure the sentence
  promises to prevent.
- `:236-242` - the `verify-ledger` check list is short by three that also count toward `clean` and
  therefore toward exit 3: `malformed_ledger_rows` (`:3034-3039`), `entries_without_kind` (`:3138`),
  `entries_without_a_separator` (`:3139`). Also undocumented: the `tests TAG` positional,
  `tests --full` / `--brief` (`:3191-3199`), `issues --all` (`:3208`).

**DOC-C17.** `AutonomyChecks.java:452-455` lists `@param reducer`, `mayTurn`, `mustTurn`. The signature
at 458 takes a fourth, `Map<TileKey, Set<TilePorts.Side>> barred`, added in `3a52ec14`. Nothing is
misdescribed - the inline comment at 484-488 explains the new argument correctly - the `@param` list is
merely short.

**DOC-C18.** Labels checked against `src/org/traincontrol/resources/messages.properties`:

- `Readme.md:342` - *"the folder you point TrainControl at with \"Choose Local Data Folder\""*. No such
  string; the item is `Open Layout...` (`:1310`). This is in the restore-from-backup instructions,
  which is when a user is anxious and hunting for a named item.
- `Readme.md:430` and `Automation.md:257` - *"Why is it not moving?"*; the button is `Why not Moving?`
  (`:1861`).
- `Automation.md:285` - *"`Save This Diagram as a Picture...`"*; it is `Save Current Track Diagram as a
  Picture...` (`:1883`). The same line adds *"The item below it asks which page"* - there is no such
  item (`TrainControlUI.java:8012-8021` creates one). A third variant, `Save Diagram as a Picture...`
  (`:1822`), exists only as a dialog title, and `assets/automation/README.md` uses that one.
- `AutomationAPI.md:228` - *"Validate Graph"* / *"Initialize New Graph"*; on the surviving fallback
  path they are `Validate Configuration & Open Graph UI` (`:1412`) and `Initialize New Configuration`
  (`:1415`). `AutomationAPI.md:369`'s *"Validate JSON"* button exists under no name;
  `AutomationAPI.md:396`'s *"use your mouse to move points around"* and `:464`/`:477`'s pointers to
  "the graph UI" are `DOC-B8`'s window.
- `Readme.md:246-247` - `Shift+R` and `Shift+C` for paste row/column. Removed, and
  `LayoutEditor.java:5906-5909`, sitting where they were, says *"Both went with the menu items they
  belonged to."* `Readme.md:458` records the menu removal; the shortcut list was not updated.

**DOC-C19.** `Readme.md:462` - *"The Layout menu offers the page you are looking at in one click, or
any other page if you ask."* The second item was deliberately removed;
`TrainControlUI.buildDiagramExportMenu` (`:8012-8021`) creates one and its comment says why: *"the
second earned its place only when the answer was not the page you are looking at... Switch pages and
export again."* (`Readme.md:457`, the other wrong changelog entry, is at `DOC-A3` because of what it
pairs with.)

**DOC-C20.** `Readme.md:156` - *"Configure up to 10 different key mappings for up to 260 locomotives"*.
10 is now `DEFAULT_LOC_MAPPINGS` (`TrainControlUI.java:341`); the ceiling is `MAX_LOC_MAPPINGS = 50`
(`:365`). Understates the product fivefold, which is the harmless direction, but it reads as a limit.

**DOC-C21.** `docs/reference/README.md:12` - *"`GraphViewer` and the three GraphStream jars are deleted
outright."* The class is gone; `resources/gs-algo-2.0.jar`, `gs-core-2.0.jar` and `gs-ui-swing-2.0.jar`
are still on disk. They are off the classpath (`nbproject/project.properties:43-44` lists only flatlaf
and json), so nothing is broken - but this sentence is the reason nobody would go looking.

**DOC-C22.** `Automation.md` references eight images (48, 63, 69, 102, 125, 137, 155, 193);
`assets/automation/` holds only `README.md`. The guide is honest - its table at `:270-283` says the
pictures are still needed - but on GitHub each renders as a broken-image icon with "PLACEHOLDER:" as
the alt text.

**DOC-C23.** The version string is right: `RAW_VERSION = "3.0.0"` (`MarklinControlStation.java:81`)
matches `Readme.md:374`'s `v3.0.0 [Beta]`. But `Readme.md` was last touched at `4ed53461`, 29 commits
ago, and six shipped features have no entry: the startup splash (`gui/StartupSplash.java`), 50
locomotive pages (`TrainControlUI.java:365`), draggable station labels (`LayoutGrid.java:100`, FR-035),
travel restrictions on the ordinary diagram (`messages.properties:1359`, FR-037), plus/minus page
stepping (`LayoutEditor.java:5841-5854`, FR-036), and `LEAST_RECENTLY_VISITED` (`Layout.java:154`).

**Deliberately C, and only the last one is worth writing up.** `README.md:262-264` says the changelog
is for *"only defects a user could actually have hit"*, and `Readme.md` is read by non-technical
users - five of these six are features nobody was waiting on, and an entry each would be noise.
`LEAST_RECENTLY_VISITED` is the exception because `DOC-B19` shows the list that omits it reads as
complete.

**DOC-C24.** Eight smaller drifts, each verified, none worth its own section:

- `AutonomyEditorPanel.java:1243` - *"Four settings that a railway works without"*; the submenu builds
  five since FR-001 added `menuBlockedByPoints` (1285), and the "two loose above / two already here"
  arithmetic in the same paragraph no longer adds up.
- `AutonomyEditorPanel.java:139` - cites `jLabel2 "Toggle Visibility"` in `LayoutEditor`; renamed to
  `toggleVisibility` (declared 6053, styled 5694-5696). The font and colour values quoted are still
  right; the audit trail dead-ends.
- `LocIconCropDialog.java:307` and `:1013` - *"0 (the whole crop window just filled)"*. `MIN_ZOOM = 0.5`
  means zoom 0 is *half* the picture fitting the panel, with white on every side. Corrected in three
  other places in the same file (338, 898, 1052).
- `LocIconCropDialog.java:632-633`, echoed at 450 - `cropWindow()` documented as *"inset by
  WINDOW_MARGIN, and locked to the ratio the locomotive icon is displayed at"*; it now returns
  `largestWindow(frameAspect)` scaled by `frameSize`, so neither clause holds once an edge has been
  pulled.
- `TrainControlUI.java:15206` - *"Those nine now come through here, which also stops them assuming the
  diagram is tab 1."* `jumpToLayoutTab` (18773) still branches on `if (currentIndex != 1)` (18779).
- `TrainControlUI.java:15183` - `showLayoutTab`'s summary *"Brings the track diagram to the front"* is
  now conditional; the body returns silently when the tab is disabled (15206). The inline comment says
  so; the summary does not.
- `LoadingSpinner.java:163` - *"which quietly overrode both callers"*; there are three
  (`LayoutGrid.java:1497`, `BusyDialog.java:48`, `StartupSplash.java:70`, the last added in `eac0e392`).
- `RightClickPageMenu.java:121-123` - *"one that is greyed with an explanation says what to do about
  it."* `canDeleteCurrentPage` (`TrainControlUI:1312`) refuses for two reasons but the tooltip is
  unconditionally `page.ui.tooltipDeletePage` - *"Only an empty page can be deleted. Clear its mappings
  first."* On the `numLocMappings <= MIN_LOC_MAPPINGS` path that advice is wrong.
  `deleteCurrentLocMappingPage` (1401) already splits the two messages, so the right string
  (`page.ui.errorNeedAtLeast`) exists and is simply not reachable from the tooltip.

---

## D - checked, and not defects

| | | |
|---|---|---|
| **DOC-D1** | `reviews/README.md:73-74` - "which is what `testRouteInventory` now does" | Clean |
| **DOC-D2** | `reviews/README.md:238-240` - every generator-driven test in `testLayoutBfs` asserts a floor | Clean |
| **DOC-D3** | `reviews/README.md:176-178` - "It now repeats twenty times" | Clean |
| **DOC-D4** | `GraphReducer.java:512-521` - raised to me as stale; it is correct. **Withdrawn** | Withdrawn |
| **DOC-D5** | `docs/reference/README.md` - the substance is accurate | Clean |
| **DOC-D6** | `CSDetect.java` and the recent `MarklinControlStation.java` comments | Clean |
| **DOC-D7** | `manual-tests/README.md` - eleven claims about `triage.py` that hold | Clean |
| **DOC-D8** | `Automation.md` - clean of the deleted window, and its 17 menu labels all resolve | Clean |
| **DOC-D9** | `AutomationAPI.md` - 25 API items and ~35 JSON keys that are correct | Clean |
| **DOC-D10** | `Readme.md` - the layout-editor shortcuts, the import instructions, the build section | Clean |
| **DOC-D11** | Twenty comment claims across the eleven `gui/` files, verified accurate | Clean |

**DOC-D1.** `reviews/README.md:73-74` - *"leave the harness to write it to a temporary directory - which
is what `testRouteInventory` now does."* True: `test/core/testRouteInventory.java:42`,
`private static final File OUT = new File(System.getProperty("java.io.tmpdir"), "route-inventory");`
Nothing in that class writes under `docs/`.

**DOC-D2.** `reviews/README.md:238-240` - *"Every generator-driven test in `testLayoutBfs` asserts one -
e.g. 'at least 300 pairs actually had a route'."* Both do:
`testRandomGraphsMatchAnIndependentShortestPath` asserts `reachable > 300` (`testLayoutBfs.java:516`);
`testRandomGraphsExclusionTerminatesWithoutRepeating` asserts `exhausted > 50` (`:577`) and
`alternativesFound > 0` (`:581`).

**DOC-D3.** `reviews/README.md:176-178` - *"measured at 247 catches in 500 runs... It now repeats twenty
times."* True: `testLayoutBfs.java:175-177` carries the measurement, `:188` is
`for (int attempt = 0; attempt < 20; attempt++)`, and the same idiom is at four places in
`testLayoutPickPath.java`.

**DOC-D4. Withdrawn.** `GraphReducer.java:512-521` was raised to me as a paragraph describing a refusal
that `ff6368bb` deleted from under it, and therefore as an invitation to reintroduce the OB-120 defect.
I read the block and the code below it: it is correct. The paragraph ends *"So the refusal belongs on
the DESTINATION hop, which is what OB-120 was filed about"*, and the destination test is at `:542-543`,
twenty lines below, with a second comment explaining why it is there rather than at the hop. The block
explains why the refusal is **not** at the line it precedes.

Recorded rather than dropped, per `README.md:124-126`. The lesson is `README.md:135` - verify the layer
you are claiming about - applied to prose: a comment that ends by pointing forward has to be read
against the line it points at, not the line it touches.

**DOC-D5.** `docs/reference/README.md` checked in full. `GraphLocAssign` is live in `src/` as it says;
the four `.txt` files it tables are all present; the claim that they cannot compile because they call
`updatePoint(Point, Graph)`, `highlightLockedEdges` and `addEdge` on a deleted class holds. Only the
jars sentence is wrong (`DOC-C21`).

**DOC-D6.** `CSDetect.java` - the claim with teeth is `WEB_RETRY`'s *"Only reachable hosts ever get
here"* (`:50-51`), and both call paths satisfy it: `detectCentralStation` gates on `isReachable`
(`:79`), and the only other caller sits inside the `else` of `CS2File.ping(initIP)`
(`MarklinControlStation.java:3683`, call at `:3698`). `MarklinControlStation.java` - the FR-041 splash
comments (3739-3746, 3829-3834, 3840-3843) and the `built`/latch comments (3764-3772, 3823-3826,
3873-3876) all match their code.

**DOC-D7.** Eleven claims in `manual-tests/README.md` that hold, each read against the implementation:
the three tabs (`triage.py:1262-1265`); **Request cancel…** filing a new item rather than touching the
target (`:1346-1348`, `:1543-1609`); the launch button using Simulate + Debug (`:1008`,
`args = ["0", "1", "1"]`); the reopened mark, filter, status-line count and `--reopened` flag
(`:229-256`, `:1297`, `:1243`, `:1876-1880`, `:3200`); a promoted item hiding under `open` and
returning under `everything` with a jump button (`:1441-1459`, `:1350-1353`, `:1623-1637`); `triage.py`
never writing a Disposition (its only writes are `:554-581` and `:421-448`); backup and atomic replace
on every write (`:141-167`); all eight documented CLI commands existing with those exact names and
flags; all eight documented `verify-ledger` checks and exit code 3 (`:3042-3142`, `:3165`); the four
dispositions enforced from one source (`VALID_DISPOSITIONS`, `:108`, checked at `:3117`); and separate
`OB`/`FR` counters (`:456`, `:487-506`).

**DOC-D8.** `Automation.md` was searched for instructions into the deleted window and has none - it is
written entirely against the track diagram. Seventeen menu labels it names resolve in
`messages.properties`: `Add a Configuration...` (1590), `Edit Autonomy on Page` (1597), `Advanced
Parameters...` (384), `Autonomy Uses This Link` (1504), `Station Priority ({0})` (389), `Changing
Direction` (1726), `Add a Locomotive to Autonomy...` (1719), `Trains May Arrive...` (1769), `Return
Locomotives Home` (358), `Gracefully Stop Autonomy` (374), `Autonomy Settings...` (1593), `Capture
Locomotive Commands` (1423), `Execute Timetable` (1425), `Unavailable While Occupied` (394-395), `Show
Inactive Labels` (336), `Grey Station Labels` (1358), `Backup TrainControl Data` (1289). *"Outlined in
teal"* (`:205`) matches `TrainControlUI.java:383`; the `Point:StationName` label mechanism (`:149`) is
live at `AutonomySession.java:1011`. Its faults are `DOC-B19`, `DOC-C18` and `DOC-C22` only.

**DOC-D9.** `AutomationAPI.md` - 25 API items verified present, public, with the documented signature
and return type: `new Layout(ViewListener)`, `createPoint`, `createEdge`, `Edge.addConfigCommand`, the
`accessorySetting` and `accessoryDecoderType` enums, ten `Locomotive` methods, `getLocByName`,
`runLocomotive`, `applyDefaultLocCallbacks`, the four `CB_*` constants, `getActiveLocomotives`,
`getReachedMilestones`, `setTimetable`, `executeTimetable`, four `Point` setters, the four return-home
methods, and both example classes in `src/org/traincontrol/examples/`. Every JSON key in the settings
reference at `:420-535` is present in `Layout.fromJSON` (`:6237+`). Two ranges are conservative rather
than wrong: `:196`/`:493` give `speedMultiplier` as 0.1-2.0 where the code accepts `> 0 && <= 2`
(`Point.java:157-169`), and `:196` gives `preArrivalSpeedReduction` as 0.01-1.00 where the code accepts
`> 0 && <= 1` (`Layout.java:1521-1525`).

**DOC-D10.** `Readme.md:242-259` - every layout-editor shortcut other than the two at `DOC-C18` matches
`LayoutEditor.formKeyPressed` (`:5794-5958`), including `Control+I` growing the diagram by a row and a
column. `Readme.md:94-104`, the layout import instructions - all six menu items exist as documented.
`Readme.md:352-358`, Building from Source - `json-20260814.jar`, `flatlaf-3.7.2.jar`,
`jcommander-1.69.jar`, `testng-6.14.3.jar` all match `nbproject/project.properties:34-44`.
`Readme.md:316-334`, the command line and eight locale flags, matches the eight bundles present.

**DOC-D11.** Recorded so they are not re-audited: `MAX_LOC_MAPPINGS = 50` and the deliberate
add/load asymmetry (`TrainControlUI` 1351, 6209, 21469); `updateLayoutLoaded`'s "THE ONLY assignment"
(4034); `showLayoutTab`'s "nine methods" count; `settleAbsentPages`'s "all three page writers";
`LayoutGrid`'s spinner / grace / failsafe block (1434-1543) and `MAX_GLASS_H`; `LoadingSpinner`'s
`FRAME_MS` / `DRAIN_FRAMES` arithmetic; `StationCaption`'s `FONT_SCALE`, `nudge()` and `captionOffset`,
and its `:626` prediction that a fifth longhand copy would appear in `LayoutGrid` - there are now five
(1011, 1054, 1066, 1082, 1092) and the reasoning still holds; `AutonomyEditorPanel`'s `ownerWindow`
"both callers", `promptLocomotives` "only caller", `signalWindowOpen` "only place", and
`WIDTH = 150` vs `SIDEBAR_WIDTH`; `AutonomyMenu`'s guard-shape cross-reference and manage-menu greying
loop. Every named test, message-bundle key and backticked identifier across all eleven `gui/` files
resolves.

---

## What I could not check, and what to do first

**Nothing was run.** Three findings would be sharper with a run, and are stated conservatively without
one:

- `DOC-B1` - I cannot say whether `testJavadocsAreAttached` is currently green or red, and the two
  answers want opposite responses. **This is the first thing to check**, because it is one command and
  it decides whether the guard against `DOC-A3` works at all.
- `DOC-A2` - the comment is false either way; how often `getActiveAccs` actually fails to warn depends
  on a real path's length distribution. Whether there is a *code* finding underneath it is open.
- `DOC-B25` - done by hand over 203 entries and 67 ledger rows. `triage.py verify-ledger` is the tool
  for it and would also cover the three checks at `DOC-C16`. I expect it to agree.

**Not covered.** The 70 documents in `docs/reviews/` were checked against the structural rules in
`README.md` - status lines, prefixes, severity letters - and not for whether each finding's stated
disposition still matches the code. That is a different and much larger pass;
`2026-08-24-reopen-audit.md` is the document that does it, and nothing here contradicts it.

**If only three things get fixed**, they are `DOC-A1` (move the block onto `tailMayStillBeOn` and give
`tailHasProvablyPassed` its own), `DOC-A3` (move the growEdges warning back onto `growEdges`, and
correct `Readme.md:457` so the manual stops describing the thing it forbids), and `DOC-B1` (make the
orphan test track identity, so the next `DOC-A3` fails the build naming the file).
