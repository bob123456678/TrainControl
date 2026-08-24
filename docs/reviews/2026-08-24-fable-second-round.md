# Second-round review: the repair of the repair

**Status:** open

**Prefix:** `FSR` — cite findings from here as `FSR-C1`, `FSR-D4`, and so on.

**What was reviewed, and when.** Commit `1373bea5` ("The validation pass, acted on: three of my seven
fixes did not hold") on `autonomy-diagram-r0`, read on 2026-08-24 against the tree at that commit,
which is HEAD. The brief: a second attempt made under the pressure of "that was wrong" is exactly
where a third mistake hides, so every replacement was attacked rather than confirmed.

**Method.** Read-only on src, test and docs; nothing in the tree was edited. Four things were run,
all from the scratchpad:

- `core.testHomeStaging` singly — 60 tests, 60 green;
- `regression.testEditorSurfaceRules` singly — 12 tests, 12 green;
- the same `testHomeStaging` against a **mutated copy** of `HomeStaging` compiled outside the
  repository and shadowed on the classpath — the OB-073 regression reproduced by replacing the
  state-aware `canRest(loc, to, state)` at line 694 with the stateless form — to find out which
  assert actually catches it (FSR-C1);
- a scratch probe (`ProbeCycle`, never added to the tree) that builds a ring whose two homes watch
  each other through `blockedBy`, to test the commit's claim that no state-independent statement
  exists about an FR-001 blocker (FSR-C3);
- and `py -3 docs/manual-tests/triage.py verify-ledger` — clean, exit 0, 38 rows against 38 open
  entries.

The commit's own battery claim (99 classes green) was not re-run and is not asserted here.

---

## The verdict on the three repairs, in one table

The repairs themselves hold. Every attack on the new *behaviour* came back clean and is itemised in
D. What did not survive contact is the **record**: four claims written down this round — in the
commit message, the test's javadoc, MT-157's comment, and the review document — are false, and one
documented rule was broken while correcting another. Those are the C findings.

| Repair | Verdict | Where the attacks landed |
|---|---|---|
| `MarklinControlStation` — null latch, `finally` as sole release, `exit(1)` | **sound** | FSR-D1, FSR-D2; one unswept sibling in FSR-C6 |
| `HomeStaging` — occupancy test out of the scan entirely | **sound, and weaker than it could be** | FSR-D3; the overstated claim in FSR-C3 |
| `AutonomyEditorPanel` — filters reverted, carry-through added | **sound** | FSR-D4; the irremovability cost in FSR-C5 |
| `testHomeStaging` — the inverted OB-073 test | **guards, but not the way it says** | FSR-C1, FSR-D5 |
| `testEditorSurfaceRules` — \r stripped, one test deleted | **sound** | FSR-D6; a copied comment in FSR-C7 |
| `tests.md` — MT-157 corrected, MT-164 superseded | **MT-164 by the book, MT-157 not** | FSR-C4, FSR-D7 |

---

## A — high. Wrong behaviour on the layout, or data silently lost.

None found. The specific doors checked are in D: the latch ordering (D1), the exit condition (D2),
the scan removal (D3), and the carry-through's ability to duplicate, null, self-select or destroy a
stored restriction (D4).

---

## B — medium. Incorrect results, or crashes in specific configurations.

None found.

---

## C — low. Cosmetic, dead code, or narrow edge cases.

| | Finding | Disposition |
|---|---|---|
| **FSR-C1** | The inverted test guards through its move-count bound, not the replay its comments credit | **fixed** |
| **FSR-C2** | "Every test runs with showUI=false" is false — two battery classes run the full start-up branch | **fixed** |
| **FSR-C3** | "No state-independent statement about an FR-001 blocker" is overstated — a blockedBy cycle between homes is one | **fixed** |
| **FSR-C4** | MT-157's instruction was rewritten in place, which the manual-tests README forbids | **fixed** |
| **FSR-C5** | A carried-through blockedPoints entry is invisible in the picker and can never be removed through it | **fixed** |
| **FSR-C6** | `System.exit(0)` two lines below the comment that calls it a habit | **fixed** |
| **FSR-C7** | The carriage-return comment describes rules its site does not have | **fixed** |

### FSR-C1 — the replay cannot see FR-001, so the test's documented failure mode is wrong

**Where.** `test/core/testHomeStaging.java` —
`testAHomeHeldBackByAnOccupiedPointStillGetsAnExecutablePlan`, its javadoc ("When the fix regresses,
`applyPlan`'s first assert fails on the move that walks into the held-back station"), the inline
comment ("The half OB-073 was about: every move finds its destination free when it runs"), and the
same claim in MT-157's new comment and in the round review's disposition ("it replays the plan move
by move ... that is the property OB-073 was ever about").

**What is wrong.** `applyPlan` asserts two things per move: the destination's
`getCurrentLocomotive()` is null, and `Layout.moveLocomotive` returns true. Neither can observe
FR-001. `moveLocomotive` (Layout.java line 4753) is hand placement: it never reads `getBlockedBy`,
and it does not even refuse an occupied destination — it displaces, deliberately ("it is a person
telling the model where a train actually is"). So a plan that sends A straight into the held-back
station replays clean: HS B is empty (the blocker stands on HS D, not HS B), the placement is
accepted, and everyone ends "home".

**Measured.** The OB-073 regression was compiled into a shadow copy of `HomeStaging` — the
state-aware `canRest(loc, to, state)` at line 694 replaced with the stateless form — and the whole
class run against it. 60 run, 1 failure, and the failure is the **move-count assert**, not the
replay:

```
a one-move plan cannot be right: something has to leave the watched square before A arrives,
so the answer is at least two moves.  Got: [HS alpha -> HS B] expected [true] but found [false]
```

So the guard guards — deterministically, on this fixture, because the broken planner's greedy pass
sends A home in one move and `>= 2` catches it. But the mechanism every comment credits is inert for
exactly this regression, and the property the replay does assert ("destination free when the move
runs") is the occupancy half, not the FR-001 half OB-073 was about. A future regression that
produced a multi-move FR-001-violating plan would replay clean and pass; the fixture makes that
contrived today, which is why this is C and not B.

**Why it is recorded at this length.** This commit's own MT-157 comment closes with "A test that
pins the wrong property will confirm whatever you mutate" — about the previous author's mutation
note. The new test's comments then mis-attribute which assert does the work, in the same file, in
the same entry. The fix is one honest sentence: the replay checks occupancy and outcome, and the
move-count bound is what catches a planner that stops reading `getBlockedBy`. Adding an FR-001
check to `applyPlan` (read `getBlockedBy` of each move's destination against the model before
placing) would make the comments true instead.

### FSR-C2 — two battery classes run with showUI=true

**Where.** The commit message ("No test in the battery can see it, because every test runs with
showUI=false") and the round review's FBR-A1 section and closing paragraph, which use the same
sentence to establish that the start-up path is untestable and needs a hands-on start.

**What is wrong.** `test/core/testAutonomyPathValidation.java` line 45 and
`test/core/testLayoutTiles.java` line 62 both call `init(null, true, true, false, true)` — the
third parameter is `showUI`. The first says so in its own comment ("showUI = true so the failure
popup renders"). Both are battery classes, and both traverse the whole branch this commit repaired:
the latch, the `built` check, and — were `built` ever read false — `System.exit(1)` killing the test
JVM mid-battery.

**Why it matters.** The claim was load-bearing twice over. It justified shipping FBR-A1's fix with
no automated guard; in fact the battery does exercise the ordering, and a regression that
reintroduced the race would surface there as a flaky mid-run JVM death (with the old `exit(0)`, a
silent one — which is worth knowing next time a one-JVM battery run dies without a word). And it was
written as the closing lesson of a section about not trusting descriptions of code over the code.
Grepping `init(` across `test/` is one command; it finds the two `true`s immediately.

Verified: every other `init(` call in `test/` passes `false` for showUI; the `ui.*` classes
construct `TrainControlUI` directly and call `setViewListener` themselves, which does not reach the
repaired branch.

### FSR-C3 — a blockedBy cycle between homes is a state-independent proof, so the scan is weaker than it could be

**Where.** The commit message and the comment at `HomeStaging.java` lines 344–345: "There is no
state-independent statement to make about an FR-001 blocker, so there is nothing here to keep."

**What is wrong.** There is one such statement. Take two homed locomotives whose homes watch each
other: home(A) `blockedBy` contains home(B), and home(B) `blockedBy` contains home(A). Whatever the
starting occupancy, the plan must end with both on their homes, so some final arrival happens while
the other cycle member is already home — and the state-aware `canRest` refuses exactly that arrival,
in every ordering, because the occupant of the watched square is not the arriving locomotive.
Stepping off and coming back does not help; the re-arrival is refused the same way. The impossibility
is a property of `homes` plus `getBlockedBy` — structure the scan already reads — and of no
occupancy at all. The same holds for any longer cycle, and for cycles closed through sensor
siblings.

**Measured.** A scratch probe built the four-station ring with A homed to HS B, B homed to HS D, and
the two homes watching each other. `plan()` answered:

```
outcome=NO_PLAN_FOUND  moves=[]  blocked=[]  tookMs=9
```

Nine milliseconds here because the ring's state space exhausts; on a large layout the same
arrangement burns the full 15-second budget to say "it may still be possible" about something
provable in a pairwise scan — the exact cost the conflicting-homes scan five lines down exists to
avoid, for a structurally identical mistake (two homes that cannot both be satisfied).

A second, narrower shape is provable from immovability rather than structure: `firstClearRoute`
refuses an inactive origin, so a locomotive standing on an **inactive** watched square can never
leave it — and if that locomotive is already home there (or a free agent), the scan says nothing,
because line 316 skips the already-home and the homeless before any check runs. That needs a point
deactivated after a home landed on it, which is why it is a footnote and not a finding of its own.

**Why it is C and not a defect.** NO_PLAN_FOUND claims less than the truth rather than more, the
answer is never wrong, and the removal this commit made was correct — the occupancy tests it deleted
proved things that were false. This records that the flat "nothing to keep" is not quite true, so
that a future reader who meets the cycle case does not conclude the scan's doctrine forbids proving
it. If it is ever worth the code: the cycle test belongs beside the `sharesSection` pairwise scan,
which is the same kind of goal-conflict proof.

### FSR-C4 — MT-157's instruction was rewritten, and the README says instructions are never rewritten

**Where.** `docs/manual-tests/tests.md`, MT-157, "What to do" — the paragraph "Press Return Home. It
must refuse up front, not start moving trains and then give up" was replaced with three new
paragraphs, in this commit. `docs/manual-tests/README.md` rule 5: "Entries are never deleted, never
reordered, and their instructions are never rewritten. Two things may change on an existing entry:
its **Disposition** line, and its **Comments** section ... If a test turns out to be wrong or
obsolete, write that in its Comments and leave the entry where it is. If it needs to be done
differently, write a new entry and reference the old tag."

**What is wrong.** The correction itself is right — the old instruction asked Adam to confirm the
behaviour FBR-B2 proved wrong — and the Comments addition under MT-157 is exactly what the rule
asks for. But the instruction was then also rewritten in place, which is the one thing rule 5
forbids, and the entry's own title still reads "Return Home refuses a plan it cannot carry out",
so the entry now contradicts itself: a title asking for a refusal over an instruction demanding a
plan. The rule's shape for this case was available and was even used in the same commit: MT-164 was
superseded by the book (disposition changed, ledger row removed, kept in file, successor named by
tag). MT-157 wanted the same treatment — superseded, with a new entry carrying the corrected
instruction — or the correction confined to Comments.

**Why it matters.** Rule 5 exists so that a recorded result keeps meaning what it meant. MT-157 is
`fixed unvalidated`; when Adam runs it, nothing will say the instruction he is following is not the
one the disposition was written against. Recoverable from git, invisible from the file.

### FSR-C5 — a carried-through entry can never be removed through the picker

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java` `promptBlockingPoints` — the
carry-through loop, against the filters above it.

**What is wrong.** The carry-through is correct and FSR-D4 records the attacks it survived. Its cost
is not written down: an entry the picker filters out — a square captioning this station, or one
whose name was later deleted (`getNamedTiles` no longer offers it) — is preserved on every OK,
shown nowhere, and offered no check box, so the only dialog that edits `blockedPoints` can never
remove it. Unchecking everything and pressing OK re-writes the invisible entry. The restriction
keeps firing at runtime with nothing on screen to say it exists; the escape hatches are indirect
(re-name the square, or re-point the caption, so the picker offers it again).

**Why it is C.** The alternative the commit reverted was worse — silent destruction — and the
population is entries made before OB-083 or squares un-named since. But the comment sells
"keep what was not asked about" without the corollary "and what was not asked about cannot be
declined", and the `check()` warning for a `blockedPoints` entry that no longer resolves — proposed
in FBR-C4's original write-up and declined twice now — is still the only shape that makes these
entries visible again. Recording the cost so the third decline, if it comes, is made knowingly.

### FSR-C6 — the exit the comment calls a habit is still there

**Where.** `src/org/traincontrol/marklin/MarklinControlStation.java` line 3807, against the new
comment at 3783: "every other exit on this path reports success out of habit rather than intent."

**What is wrong.** The comment is right and names its own sibling: twenty lines below, the catch
around the `display()` `invokeLater` still ends in `System.exit(0)` on a fatal failure to post. The
path is close to unreachable — it needs `invokeLater` itself to throw — which is why this is at the
bottom of C rather than higher, but the round's own SOP line applies: when you fix a call site, grep
for its twins before closing the finding. This one was found by reading the comment that describes
it.

### FSR-C7 — the copied comment describes the other site

**Where.** `test/regression/testEditorSurfaceRules.java` lines 158 and 705 — the identical comment
"two of the rules below bound their windows on a newline followed by the closing brace" above both
`\r`-stripping reads.

**What is wrong.** At the second site one rule bounds a window that way (`"\n    }\n"`, line 738) —
one, not two, now that the picker test is deleted. At the first site none does:
`testTheFacingSubmenuIsBuiltOnce` only counts occurrences, and the strip there is prophylactic. The
comment was written once for the pre-deletion second site and pasted at both. The strip itself is
right at both sites; only the sentence is wrong.

---

## D — not defects. Attacks that found nothing, and checks that came back clean.

| | What was checked |
|---|---|
| **FSR-D1** | The latch ordering is sound now, and nothing else needed the latch |
| **FSR-D2** | `!built` means what the exit assumes, and the remaining no-countdown cases predate the fix |
| **FSR-D3** | The scan removal is clean, and the stateless `canRest` is genuinely state-independent |
| **FSR-D4** | The carry-through cannot duplicate, null, self-select, or destroy |
| **FSR-D5** | `testHomeStaging`: 60 tests, no duplicate names, green, and the new test's preconditions |
| **FSR-D6** | `testEditorSurfaceRules`: well-formed after the deletion, nothing else depends on line endings |
| **FSR-D7** | MT-164's supersession follows the README, and the ledger is clean |
| **FSR-D8** | The null-name path behind carried entries is unchanged, and still untraced |

### FSR-D1 — the ordering, attacked and sound

`built.set(true)` now precedes the only `countDown` (the lambda's `finally`), so the countDown/await
edge covers the write; the race FBR-A1 measured cannot recur. `setViewListener` was read end to end
for latch dependence: its only use is the null-guarded release at `TrainControlUI.java` line 5338,
the method's last statement, so passing null is a no-op exactly as intended. Every other caller was
enumerated: seven test-file calls, each passing a private `new CountDownLatch(1)` nobody awaits —
none is affected by the production call now passing null. If `invokeLater` itself threw
synchronously, the exception propagates out of `init` before the `await` — a crash, not a hang.

### FSR-D2 — the exit condition

`built` is false after `await()` returns only when the lambda ran and `setViewListener` threw —
the `finally` is the sole release, so `await` returning proves the lambda finished. In that case
there is no usable window and `exit(1)` is right. The two remaining ways to never return are
unchanged by this commit and were not introduced by it: the posted task never running (a dead event
thread at start-up), and `await()` having no timeout — the second is the first wearing the latch's
clothes, and both need the toolkit to fail before the first window exists. An interrupt propagates
out of `init` (`throws InterruptedException`) rather than mis-reading `built`.

### FSR-D3 — the removal, and the stateless rule

`couldEverRest` and `heldByAnImmovable` are gone without residue: zero references in src, test and
docs/manual-tests; `pointsBySensor` and `locationOf` keep their other users; no orphaned javadoc.
The scan's surviving `canRest(l, home)` reads `isDestination`, `isActive`, `getExcludedLocs`,
`validateTrainLength`, `isTerminus` and `loc.isReversible()` — properties of the station and the
locomotive, no occupancy anywhere, so the scan's claim ("impossible by construction") is now true of
every test it makes. The state-aware `canRest` inside `firstClearRoute` (line 694) stays and is
asked of the evolving state (`apply` mutates per move), which is what makes the returned plans
executable. `testABlockerWithAHomeOfItsOwnIsNotAProofOfImpossibility` still holds with the scan
gone — its `assertNotEquals(IMPOSSIBLE)` pins the scan staying out.

### FSR-D4 — the carry-through's edges

Every sharp edge the brief named was walked:

- **Duplicates.** The loop adds only entries `choices` lacks; the box loop adds only from `choices`;
  `getNamedTiles` is a map key-set, so `choices` has no duplicates. And `setBlockingPoints`
  (store line 241) de-duplicates on write anyway.
- **Null.** `getBlockingPoints` skips unparseable keys, so `already` carries no null; a null would
  be dropped on write regardless.
- **The station itself.** `choices` excludes it, so it could only arrive via `already` from a
  hand-edited file — and `setBlockingPoints` refuses `blocker.equals(station)` on write, so it
  cannot survive an OK.
- **`List.contains` on TileKey.** `TileGraph.TileKey` has proper value `equals`/`hashCode`
  (page, x, y), and both lists carry the same in-memory page form — both are parsed from the same
  store's normalised keys.
- **The early return.** `choices.isEmpty()` and Cancel both return before any write, so stored
  entries survive both paths untouched.
- **Destruction.** The one write, `setBlockingPoints(station, chosen)`, now receives the union of
  the carried entries and the boxes, so OK can no longer delete what it did not show — which was
  FBR-A2's whole point. The order of the stored list changes (carried entries move to the front);
  nothing reads the order for meaning.

One pre-existing loss-shape was re-checked and is not the carry-through's: an entry whose watched
square sits on an absent page is held at the store level (OB-067), so `already` never contains it
and an OK still overwrites the held copy at save — exactly as FBR-D4 recorded before this round, no
worse and not new.

### FSR-D5 — the test file, and the new test's teeth

Exactly 60 `@Test` methods, 60 distinct names (checked mechanically — the splice-error class of
defect is absent), the file ends where it should, and the class runs 60/60 green singly. The new
test asserts its preconditions: `getBlockedBy().size() == 1` before anything else, and both
placements' return values. The fixture was walked through `setHomeLocomotive` and `claimHome` to
confirm what FBR-B2 said: assigning HS B to LOC_A strips LOC_B's positional claim, and standing
LOC_B on HS D re-homes it there, so the plan needs B off, A in, B back — which is why `>= 2` is a
sound lower bound (two would leave B misplaced; the real answer is three). `assertEquals(outcome,
READY)` is not too strong: the fixture has an executable plan by construction, the state space is
four stations and two locomotives, and no legitimate budget or limit change can make NO_PLAN_FOUND
the right answer for it — a planner that cannot solve this fixture is broken, which is what the
assert is for. The mutation run in FSR-C1 is also this section's proof that the test can fail, and
fails on the canonical regression.

### FSR-D6 — the source-rule file after the deletion

12 `@Test` methods, well-formed, 12/12 green singly — the class FBR-C8 found red at HEAD is green
in the tree this commit left. Both `readAllBytes` reads of `AutonomyEditorPanel` strip `\r`; the
other reads in the file are `readAllLines` (line-ending-blind by construction) or feed
`contains(...)` checks that no `\r` can break; the one backward character window (400 chars before
`signalWindowOpen = false`) tolerates CRLF inflation with room to spare and is green under this
CRLF tree. The deleted test left nothing dangling: no reference to
`testTheBlockedPointsPickerOffersOnlySquaresThatResolve` anywhere in src or test.

### FSR-D7 — MT-164, and the ledger

The supersession follows the README: disposition line changed, row removed from the outstanding
table, entry kept in the file, and the comment names what took it over (MT-157) and why the
distinction it asked for does not exist. `verify-ledger` is clean — 38 rows, 38 open entries, no
duplicates, exit 0. The contrast with MT-157's handling is FSR-C4.

### FSR-D8 — the untraced path is no worse

FBR-C7 left one thing untraced: a `blockedPoints` square with no reducer point flows
`nodeName(null, copies.get(0))` into the builder's `watching` array, and what `parseAuto` does with
that was not followed to the bottom. The carry-through keeps such entries alive (a filtered caption
square rides through every OK), so the question was re-checked for reachability: it is exactly as
reachable as before the round — the picker offered those squares outright in the OB-083 state this
commit reverted to — so nothing here made it worse, and it remains untraced here too, for the same
reason: it predates the round and this review's scope is the commit.

---

## What was not covered

The commit's changes to `docs/reviews/2026-08-24-fable-round-review.md` (the validation pass and its
dispositions) were read as record, not re-reviewed line by line. The battery claim was not re-run;
the two classes named in FSR-C2 were not run singly. `parseAuto`'s handling of a null watching name
(FSR-D8) remains untraced, deliberately. Nothing was exercised through the user interface; the
start-up path's fix is confirmed by ordering argument and by the two showUI tests' history, not by a
hands-on start.

---

## Dispositions

**Claude, 2026-08-24.** All seven acted on; none declined. Nothing here was a defect in behaviour - the
repairs held - and six of the seven were things I had written down that were not true. That is the part
worth reading.

| | What was done |
|---|---|
| **FSR-C1** | Fixed by making the claim true rather than by rewording it. `applyPlan` now asserts the FR-001 condition itself - nothing may occupy a square holding the destination back - because `moveLocomotive` places a locomotive rather than refusing and never reads `getBlockedBy`. The move-count assertion was ALSO moved to after the replay: with it first, the OB-073 mutation failed on "a one-move plan cannot be right", which is true of that plan and says nothing about what is wrong with it. Mutation-checked again afterwards, and the failure is now `move "HS alpha -> HS B" sends a locomotive into a station that is held back while HS D is occupied`. |
| **FSR-C2** | Corrected where it was written, in the previous document's disposition. Five test classes run with `showUI=true`, not none: two pass it outright and three reach it through the no-argument `init()`, whose overload passes `true`. I had grepped the two `init(...)` shapes and not read the overload. |
| **FSR-C3** | Claim corrected in the code, and the case filed as [OB-085](../manual-tests/issues.md) rather than implemented. A `blockedBy` cycle between two homes IS provable from structure, so "no state-independent statement can be made" was too strong. Not added now: the last two things put into that scan were both wrong, both looked obviously right, and a third attempt in the same session as the first two is not how that bar gets cleared. |
| **FSR-C4** | Fixed properly. MT-157's instructions are back exactly as written, the entry is superseded, and [MT-165](../manual-tests/tests.md#mt-165) carries the correct instructions. Rewriting instructions in place is forbidden precisely so that a recorded result still means something, and this entry's title had come to contradict its own body. |
| **FSR-C5** | Fixed, and it replaced FBR-A2's repair rather than adding to it. Every stored entry is now OFFERED by the picker, ticked, whatever the filters say about what may be newly chosen - so nothing is hidden, which means nothing can be silently kept or silently deleted. Carrying entries invisibly past the dialog fixed the deletion and made the restriction permanent instead. |
| **FSR-C6** | Fixed. The sibling `System.exit(0)` two branches below now exits 1 as well. |
| **FSR-C7** | Fixed. Each of the two comments describes its own site; the second says it is consistency rather than a fix, and that it used to claim otherwise. |

### The habit behind six of these

FSR-C1, C2, C3, C6 and C7 are all the same thing: a sentence written from what the code looked like it
did, rather than from what it does. C1 credited a helper with an assertion it could not make; C2 named
a set of tests without reading the overload that puts three more in it; C3 stated an impossibility that
is not one; C7 described rules that were not at that site; C6 wrote about exit codes being habit and
then left the habit two lines below.

None of them changed behaviour, and all of them would have misled the next reader - which is what the
comment was for. The previous document's own disposition already names this as the round's recurring
mistake. It recurred inside the fix for it.
