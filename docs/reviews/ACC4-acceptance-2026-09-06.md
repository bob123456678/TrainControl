# ACC4: acceptance review of the last seven days, 2026-09-06

**Status:** open

**Prefix for citing these findings elsewhere:** `ACC4` (confirmed unused - `grep -rl "ACC4-" docs/`
returned nothing before this file).

**Reviewed:** branch `autonomy-diagram-r0`, commits `72234e18..ec4d5ec7` (2026-08-30 through
2026-09-06, 221 commits), working tree read at `ec4d5ec7` on 2026-09-06. Two commits (`ca0265f4`,
`ec4d5ec7`) landed *while this review was running*; they were read and are in scope, but they are
minutes old and nothing here should be read as field validation of them.

**Method.** Analysis only, per the review's ground rules: no tests were run, no builds were made,
`cs2_sample_layout/` was read and never written. Every claim below says what it rests on. The message
bundles were checked by a script that parses all eight files byte-by-byte (key parity, non-ASCII
bytes, MessageFormat apostrophe hazards, and a cross-check of every `I18n.t`/`I18n.f` call site in
`src/` against the bundle). Everything else is code and history reading.

**Grade: B.** The week's work is close to acceptable, and the review machinery that ran alongside it
(seven reviewer prefixes, four validation rounds, and this morning's DIR pass) caught most of what it
produced. What keeps this off an A is one defect in today's newest feature - the manual-reversal
prompt - that does exactly the thing its own comment promises it cannot do, in the gesture an
operator will reach for first. Fix ACC4-1 and re-letter this a conditional A.

---

## Findings, most severe first

### ACC4-1 - Dismissing the "Keep direction?" dialog reverses the train

| | |
|---|---|
| **Severity** | A |
| **Disposition** | Open |
| **Confidence** | High - code read against documented `JOptionPane` semantics, and against four sibling dialogs in the same week's work that all handle the case this one misses. Not executed. |

`src/org/traincontrol/gui/ManualReversalPrompt.java:242`:

```java
answer[0] = chose != 0;
```

`JOptionPane.showOptionDialog` returns the index of the chosen button - `0` for Yes, `1` for No -
**and `CLOSED_OPTION`, which is `-1`, when the operator presses Escape or clicks the title-bar X.**
`-1 != 0` is true, so a dismissed dialog answers "reverse the train".

**What the operator experiences.** They right-click a train on the diagram (or use the Locomotive
commands tab) and send it along a path that passes a may-reverse square. Before dispatch, a dialog
asks "*EN57 has reached BottomMainB, where trains may turn round. Keep its current direction?*" They
press Escape - the universal "go away, do the default" gesture - or close the box. The train departs
and is **turned round at that square, and at every other asking square on the journey**, because
`forJourney` carries one answer to all of them. They expected nothing to change: the dialog's own
comment eleven lines up says *"dismissing this dialog with the keyboard is safe"*, and the class
javadoc says *"Turning a train because a dialog failed is the worse of the two mistakes."* Both
sentences describe the code as it stood before `48eca362`, not after.

**How it happened.** The previous wording ("change direction?") had `answer[0] = chose == 0` with
No as the default - under which `CLOSED_OPTION` fell on the safe side by accident. `48eca362`
(2026-09-06 08:08, after this morning's DIR review closed) inverted the question to "keep
direction?" and inverted the mapping, which moved `-1` from the safe side to the reversing side.
Both hand-driven doors share this method (`AutoLocomotiveStatus.java:1043`,
`LayoutRightclickAutonomyMenu.java:1051`), so both are affected.

**The codebase already knows this trap.** `LayoutLabel.java:378` documents `CLOSED_OPTION`
explicitly; `AutonomyEditorPanel.java:3553` guards `if (chose != 0 && chose != 1) return;`; the new
power dialog in `TrainControlUI.java` (MT-261) says *"Anything that is not one of the two yeses is a
no - Escape and the close box included, which is the correction the tile's own dialogs needed."*
This is the one dialog of the week's five that missed the sweep. The fix is one token:
`chose == 1`.

### ACC4-2 - The departure-time question says the train "has reached" a square it has not left for

| | |
|---|---|
| **Severity** | B |
| **Disposition** | Open |
| **Confidence** | High - the message history and the commit that moved the ask are both in the tree. |

`src/org/traincontrol/resources/messages.properties:306` (and the same key in all seven sibling
bundles):

```
autolayout.ui.confirmManualReversal={0} has reached {1}, where trains may turn round.\n\nKeep its current direction?
```

The text was written at `8fca0215`, when the question was asked **mid-run, on arrival at the
square**. `df584d0b` (Adam: *"make it be on departure itself"*) moved the ask to before dispatch -
and did not touch the message. So the operator clicks "send to X", the train has not moved a
millimetre, and a dialog states as fact that it *has reached* a station partway down the line.

**Why this is B rather than C.** The sentence is the entire context for a decision that turns a
train round. An operator running several trains, reading "*BR 218 has reached BottomMainB*", is
being told something false about where their rolling stock is at the moment they are asked to rule
on it - and the natural misreadings ("a different journey finished", "the app has lost track of the
train") are worse than the wording error itself. All eight languages carry the arrival phrasing.

Two javadoc sentences in the same class went stale the same way and should go with the fix:
`ManualReversalPrompt.java:205` (*"@param where the point it has reached"*) and the `ask()` threading
note at `ManualReversalPrompt.java:187-190` (*"Both callers dispatch on a worker"* - since
`df584d0b`, both callers ask on the EDT before the worker starts; the `invokeAndWait` branch is now
the fallback rather than the rule).

### ACC4-3 - `reversalPolicy()` is dead, and its javadoc describes parameters it does not have

| | |
|---|---|
| **Severity** | C |
| **Disposition** | Open |
| **Confidence** | High - `grep -rn "reversalPolicy" src/` returns exactly the declaration. |

`src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:1015`. `df584d0b` replaced this
method's only call with `ManualReversalPrompt.forJourney` and left the private method behind. Its
javadoc carries `@param train` and `@param where` for a method that takes no arguments, and repeats
the stale "the point it has reached" phrasing. It also carries the DIR-C8 window-parenting fix,
which is now duplicated knowledge: the live copy of that reasoning is at the `forJourney` call site.
Delete the method.

### ACC4-4 - The changelog still advertises the splash screen OB-170 removed

| | |
|---|---|
| **Severity** | C |
| **Disposition** | Open |
| **Confidence** | High. |

`Readme.md:410`: *"A splash screen appears while TrainControl is reaching the Central Station,
before there is a window to show progress in."* OB-170 (closed 2026-09-03, by Adam's own
three-way experiment) established that showing a second window is precisely what broke the start-up
keyboard, and `StartupSplash` is now a panel worn by the main window - its javadoc says *"if a
second start-up window ever comes back, so does OB-170."* The operator still gets a spinner and a
connecting message, so the visible behaviour survives; the sentence describing it as a splash
screen, before there is a window, is now the opposite of the design. This is the same
changelog-drift class as RGN-B2/RGN-B3, which were both fixed this week; this line survived the
sweep because it sits under Interface rather than under the sections those findings audited.

### ACC4-5 - `IS_PRE_RELEASE` is `true`, and 3.0.0 must not ship that way

| | |
|---|---|
| **Severity** | C |
| **Disposition** | Open |
| **Confidence** | High - and this one is *deliberate*, not a leftover. |

`src/org/traincontrol/marklin/MarklinControlStation.java:98`. Adam asked for this flag on
2026-09-05 and its javadoc says, in bold, *"Set to false when 3.0.0 ships. Nothing derives it."*
This finding exists so the release checklist has a row that cannot be missed: a 3.0.0 built from
today's tree stamps a build ID into the log and the main window title of every user's copy. It is
the answer to this review's "echo packets"-style question - the one constant that was switched on
during development on purpose and must be switched off on purpose.

---

## The acceptance questions, answered

**1. Anything that would visibly misbehave for an operator?** Yes - ACC4-1, and it is the week's
headline feature misfiring on the commonest dismissal gesture. Scenario: operator sends a train
through a may-reverse square, Escapes the new dialog expecting the promised safe default, and the
train reverses at that square (and every later asking square on the path). Everything else that
would qualify was caught by the week's own review rounds before it reached this pass; I verified
the highest-risk closures (the reversal stop-before-ask DIR-A1, the destination ask DIR-A2, the
emergency-stop carve-out DIR-A3, the inactive-square unification of `36734d33`, the OB-170 window
arrangement) by reading the current tree rather than the review's claims, and the code matches the
dispositions.

**2. Anything half-finished?** No dialog without a way out, and no dead-end menu item found. The
"Path Type" radio (`ec4d5ec7`, an hour old) is wired end-to-end: it draws the same trace either way
and adds the autonomy-tier note on Auto, which matches its tooltip. Two things in its orbit are
*deliberately unfinished* and correctly flagged rather than guessed at: its tooltip contradicts
Adam's stated belief that Return Home follows manual rules (the commit measured the opposite -
Return Home runs under the autonomy tier - and asks him to confirm), and `7d4661d0`'s paste-facing
fix is the fourth attempt at a defect Adam has reported three times, whose own test admits it cannot
exercise the multi-copy case on this layout. Both need his eyes, neither is a code defect I can
demonstrate. The dead `reversalPolicy()` (ACC4-3) is leftover, not half-finished.

**3. Message bundles?** Clean, and verified by script rather than by eye: 1,902 keys, identical
sets across all eight languages; zero non-ASCII bytes in any file; zero undoubled apostrophes in any
message fetched through `I18n.f` (MessageFormat), zero doubled apostrophes in messages fetched only
through `I18n.t`, zero `{0}` placeholders fetched through `t()`. The dynamic key families
(`route.kind.*`, `autosetup.ui.facing*`, `autosetup.ui.side*`, `autolayout.ui.pathPreference*`) were
cross-checked against their enums - every constant has its key. The five `pathType`/`tooltipPathType`
keys added an hour ago made it into all eight bundles. The one bundle defect found this week is
ACC4-2, which is a truth problem, not a format problem.

**4. Swing correctness?** No violations found in the week's additions. Both reversal doors ask on
the EDT before starting their worker; every failure dialog raised from a worker goes through
`invokeLater`; the direction-echo refresh that ran on the Central Station's message thread was
caught and marshalled by DIR-B4 within the day; `singlePass` and the OB-170 start-up sequence
(`invokeAndWait` for `showConnecting`) are correct and unusually well argued in their comments.
`ask()`'s dual-path (`isEventDispatchThread` check) means the mid-run fallback stays safe if
anything ever calls it off-EDT again.

**5. Leftovers?** `DEBUG_SIMULATE_PACKETS` is `false`; `DEBUG_LOG_NETWORK` is `true` but is
compile-time gated behind the runtime `debug` flag (both conditions required at every use site) and
predates this week by months - not a leftover. No `TODO`/`FIXME` was *added* this week (the one in
the diff at `LayoutRightclickAutonomyMenu.java:1055` is old code that moved). No commented-out code
was added. The `reopenGraphButton` removal is complete - no orphaned handler, key, or `.form`
reference. The deliberate switch that must be flipped before shipping is ACC4-5.

---

## Watch items (not findings - fresh code awaiting Adam's hands)

These are today's last four commits, written against defects Adam reported from the field this
morning. All four are internally coherent on reading; none has been run on the railway since:

- `7d4661d0` - paste now records the facing. Fourth attempt; the multi-copy control is untestable
  on this layout by the commit's own measurement (`testEverySquareOnThisLayoutBuildsToOneCopy`).
- `ca0265f4` - a mid-run reversal is followed at run end. The fix is an ordering
  (guards before the recording, `putIfAbsent` baseline) verified by mutation at the unit level; the
  end-to-end path needs a live Central Station feeding echoes.
- `ec4d5ec7` - Path Type radio, plus the flagged Return Home tier question awaiting his ruling.
- The reversal prompt flow as a whole (`48eca362..df584d0b`) - and ACC4-1 should be fixed before he
  tests it, or the Escape he will inevitably press mid-test will turn a train.
