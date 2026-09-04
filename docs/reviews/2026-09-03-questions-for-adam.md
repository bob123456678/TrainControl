# What is waiting on you, 2026-09-03 (evening)

Everything that had a defect in it has been fixed and dispositioned. This is what is left that is
**yours rather than mine**.

Four items that were on the morning version of this list are now closed, and are recorded at the end
so the list does not lose its own history.

---

## 1. Run MT-265 and the homing half of MT-246

Both are hands-on and neither blocks anything.

**[MT-265](../manual-tests/tests.md#mt-265)** — four gestures behind today's fixes. Each was proved by
a test written first and watched to fail, but every one of those tests works a level below the mouse,
so what is left is that the gesture reaches the code the test covers.

**[MT-246](../manual-tests/tests.md#mt-246)**, the homing half only — the signals half you already
passed. The defect you found is fixed: set a home from the track diagram, check "Return Locomotives
Home" lights up, close the application, reopen, check the home is still there.

---

## 2. Should the semi-autonomous destination list say why a station is absent?

This came out of your MT-246 triage: *"EN57-203 can't go to bottomleftpark semi-autonomously (no option
shown)."*

**The code is right.** Measured on the frozen copy of your layout, built into a real graph:
`TunnelLeftPark` is a station, active, a **terminus**, with **autoDestination false**; `EN57-203` is
**not reversible**. A non-reversible locomotive can only reach a terminus by a route that turns it
round, which is step 7 of MT-246 itself — *"reported as impossible for that locomotive, rather than
offered and then failing on the first move"*. And separately you have marked that square as not an
automatic destination.

**What is wrong is the silence.** An absent entry explains nothing, and you read it as a failure, which
is the correct reading of no feedback at all. The "Why not Moving?" tool answers exactly this question
for a train that will not move; the destination list has no equivalent.

So: should an ineligible station appear greyed with a reason, rather than not appear? That is a design
choice about a list you use constantly, and it is yours.

---

## 3. Two residuals in your own `setup.json`

Data rather than code, so yours to keep or clear.

- **Six fabricated `tileLengths`** (`IPR-A1`): `5:20,13`, `5:0,11`, `5:20,14`, `5:1,10`, `5:14,3`,
  `5:5,4`. The import that wrote them is fixed; the numbers are still there.
- **34 captions for 33 stations, with `5:6,4` named twice** (`IPR-C1`), measured on the frozen copy.
  The fourth door past "one station, one caption" was the label migration.

---

## 4. The four questions already asked, unchanged

In `2026-09-03-c-sweep-report.md` under "What needs Adam", repeated here only so this is one list:

1. Should the editor's "reaches nothing" warning match the runtime's rule? (`V36-C4`)
2. What shape should "Test Connection" come back in? (`RG3-C4`, MT-257 item 5) — your question back.
3. Should a route-command deletion say what it removed? (`FV2-C9`, `R28-A1`)
4. `DY3-C8` — already answered on MT-260; a pointer only.

And three smaller ones from the release review:

- Two stations may be given the same name silently; `uniqueNames()` disambiguates to `X (2)` without
  warning, and the javadoc claiming a user is told at authoring time is **orphaned** (`RC`).
- Should `BALANCED_PRIORITY` consider de-prioritised stations at all? The code implements `RC-B2`'s
  conservative answer.
- Whether any locomotive of yours has a bracket in its name, which cannot be used in a route command
  (`RGN-C3`). That needs your database.

---

## 5. Standing decision: sixteen dead methods, deferred past 3.0.0

`REL-C15` found eighteen methods in the autonomy, diagram and route UI with **no caller anywhere in**
**`src/` or `test/`** - found by counting every `name(` occurrence and every `::name` reference across
the whole tree, then confirming each by hand.

**Two were removed**, because they are traps rather than clutter:

- `TrainControlUI.greyOutAutonomy` - a public method whose javadoc says *"disables the start autonomy
  button"* and whose body **executes a graceful stop of the running railway**. Its 2.8.1 caller was
  the graph window's close handler, and there is no graph window any more. A name that says one thing
  and a body that stops your trains is the worst kind of thing to leave lying about for somebody to
  wire up.
- `AutonomySession.restoreCaptionsOnPage` - whose javadoc said it was what the track diagram editor's
  undo uses. It is not, and has not been since that mechanism was replaced by `snapshotPage` /
  `restorePage`.

**The other sixteen were left, deliberately.** They are accessors and wrappers -
`getTool`, `isShowingLengths`, `withNewFirst`, `routeNamed`, `getNumLocMappings`, `isSelectMode`,
`getAutonomyOverlay`, `getMarks`, `getTraces`, `isSaying`, `isSignalAt`, `isRotated`, `showTextMenu`,
`addBoxHighlighted`, `receiveKeyEvent`, `editTextWithDropdown` - and deleting them buys tidiness and
nothing else.

**Why I stopped there, in one sentence:** a sixteen-method deletion inside a release candidate is a
diff nobody can review by eye, for no behavioural gain, and the whole point of the finding was that
unreviewable dead code is where traps hide.

**The negative result is the one that mattered and it stands**: the scan was run to close a stated
blind spot - *"the sweep cannot see a key referenced only from a method with no callers"* - and **no**
**capability was found whose only door is one of these**. Two looked like it and are not:
`showTextMenu`'s menu is reached from `buildTileMenu`, and `addBoxHighlighted`'s callers went with a
feature already cleared as a deliberate removal.

**What I would suggest:** delete the sixteen in one commit early in 3.0.1, when a large mechanical
diff costs nothing. Three carry their own comments explaining they are unreachable, so the reading is
already done. If you would rather keep any of them as API for something you have in mind, say which
and I will annotate them instead so the next sweep stops re-finding them.

---

# Closed since this morning

- **The parity comparison ran.** You closed TrainControl, the port came free, and 2.8.1 against 3.0.0
  reproduces 2026-08-29 exactly: no destination lost, no concurrency pair lost, the same four
  variant-identity route differences. Report at `2026-09-03-parity-report.md`. **And your follow-up
  question is now part of the harness**: every route condition is parsed under *both* jars and compared
  by truth table — all 39 come out identical.
- **`RGN-B2`** — you ruled the s88 behaviour stays as it is. Closed on the three descriptions agreeing.
- **`RGN-B3`** — you confirmed v2.8.0 and v2.8.1 shipped untagged, so the two bullets are where they
  belong.
- **`RC` carried #3** — you ruled "force a graceful stop, alert the user, then unlock." Implemented;
  the stop and the alert already existed, the release is new.
