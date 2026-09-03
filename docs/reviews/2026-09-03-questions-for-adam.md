# What is waiting on you, 2026-09-03

Everything that had a defect in it has been fixed and dispositioned. This is the list of things that
are **decisions rather than defects**, plus one machine housekeeping item that has to be you.

Nothing here blocks a release candidate. The first item does block one piece of release evidence.

---

## 1. TrainControl has been running since 02:19 this morning

`java ... TrainControl 0 1 1`, PID 9084, started **2026-09-03 02:19:14** from `build/classes` - so it
was launched out of NetBeans. It is still up.

Three things follow from it, and none of them is damage:

- **`cs2_sample_layout/config/autonomy/setup-before-edit.json` is its unfinished-edit note**, written at
  02:19:22. That file is the snapshot the layout editor takes when it opens and clears when the session
  ends properly - so its presence means that editing session has not ended, which is true, because the
  window is still open. I have **not touched it**; there is a copy at
  `%TEMP%\...\scratchpad\RESCUED-setup-before-edit-0219.json` if you want one somewhere safer.
- **A revert would be a no-op.** I compared it against your live `setup.json` key by key: `stations`,
  `pointNames`, `portals`, `tileLengths`, `tileDirections`, `captions`, `linkNames`, `blockedPoints`,
  `stationSignals`, `barredArrivals`, `disabledLinks`, `excludedPages` - **identical on every one**. If
  you close TrainControl normally the note goes; if the process is killed, the next start will log
  *"The last layout edit did not finish..."* and put back exactly what is already there.
- **It holds the CAN/UDP port**, which is why the 2.8.1 parity comparison cannot run. The test suite
  works around this with `-Dtraincontrol.anyReceivePort=true`; the 2.8.1 jar predates that flag and
  cannot take another port.

**What I need from you: close TrainControl, then run these two.** The harness itself is fixed and
verified (`TSX-C16` - both scripts had been pointing one folder short since the `fb3722f5` move, so
`setup-env.sh` could not build the environment at all). `setup-env.sh` already runs clean against your
current `dist/TrainControl.jar`, and `run.sh` gets as far as recording 2.8.1 before the port stops it.

```bash
sh docs/tools/parity/setup-env.sh && sh docs/tools/parity/run.sh
```

That produces `../traincontrol-parity/out/report.md`, which is the evidence that 3.0.0 offers every
journey 2.8.1 did. The last one is from 2026-08-29 (`SG-B5`).

---

## 2. Should a route fired by a sensor still drive trains over ironwork it did not set? (`RGN-B2`)

Today, an s88-fired route that meets a conflict **sets none of its switches and signals** - deliberately,
as a group, because there is nobody there to ask - and then goes on running its speeds, its functions
and any route it chains to.

I have fixed everything about this that was a defect: the two log messages contradicted each other and
neither was true (both say the same true thing now, in all eight languages), and the changelog said the
route "stops instead", which it does not. All three descriptions agree now.

**What is left is your call, and I deliberately have not made it.** The rule written at
`MarklinRoute.java:585` says a conflicting route is *"REFUSED rather than confirmed, and refused WHOLE
... a route half executed leaves the layout in a state nobody chose"*, and by that definition this is a
half-executed route.

**Why I did not simply make it refuse whole:** refusing whole discards the route's `isStop()` commands
along with everything else, which is throwing away an emergency stop because a turnout was busy. Your own
recorded ruling is that a conflicting route must stay executable *"in case of a transient accessory
failure"*. Both arguments are yours, not mine.

---

## 3. Two bracketed groups at the same indent are refused, for an outline that reads correctly (`IPR-B3`)

`problems()` keys its "this level is settled" answer on depth alone across the whole list, while `read`
consumes each run in its own recursion - so two separate bracketed groups at one indent are flagged red,
`everythingWrong()` adds *"the frame's logic disagrees"*, and `onSave` offers only Fix or Discard. The
outline itself parses correctly.

It was filed as **wanting a ruling more than a patch**, and no ruling is recorded anywhere. The question
is whether two groups at one indent are a legal thing to write - if they are, `problems()` needs to ask
per run; if they are not, the editor should stop you writing one rather than refusing the save
afterwards.

Related and now fixed, so this is the only one left in that file: a bracket in a **non-leading** position
used to come back with the wrong operator - `3 or ((1 or 2) and 4)` re-read as `3 or 1 or 2 or 4`, with
the AND silently becoming an OR and Save writing it back (`IPR-B2`).

---

## 4. A mid-run failure strands a train on locked track (`RC` carried #3)

The `RuntimeException` catch in `Layout.java:5022-5031` removes the locomotive from `activeLocomotives`,
`locomotiveMilestones` and `clearedEdges`, then from `takingPath` - and those maps are exactly what
`getActiveAccs` reads to know which accessories a route must not throw. The path is left locked on
purpose (`:5019-5021`), and there is no "abandoned but still locked" set anywhere.

So after a mid-run failure the track stays locked and the protection that goes with it does not. This is
the one item on this list I would call A-ish rather than a preference, and it is here because **the fix
is a design decision**: either a new set that `getActiveAccs` also reads, or the abandoned locomotive
keeps its entries and something else has to know it is not running. I did not want to pick one inside a
release.

---

## 5. Two residuals in your own `setup.json`, from imports that have since been fixed

Both are data rather than code, so they are yours to keep or clear.

- **Six fabricated `tileLengths`** (`IPR-A1`): `5:20,13`, `5:0,11`, `5:20,14`, `5:1,10`, `5:14,3`,
  `5:5,4`. The import that wrote them is fixed; the numbers it wrote are still there.
- **34 captions for 33 stations, with `5:6,4` named twice** (`IPR-C1`), measured on the frozen copy at
  `test/operator_layout`. The fourth door past "one station, one caption" is the label migration.

---

## 6. `v2.8.0` and `v2.8.1` have changelog sections and dates but no git tags

`v2_7_3`, `v2_7_4`, `v2_7_4b` and `v2_7_4c` are tagged; `v2.8.0 [8/2/2026]` and `v2.8.1 [8/17/2026]` are
not. This is a question rather than a complaint, and it changes one thing I did today.

`RGN-B3` was that two changes sat under `v2.7.4` and had never shipped in it. I moved them to **v2.8.0**,
because that is where the commits landed: `v2_7_4c` was tagged 2026-07-25 at 01:27, Path Integrity
Validation went in at 15:33 the same day, and the next release heading after it is 2.8.0. **If 2.8.0 and
2.8.1 never actually went out**, then everything from 2026-07-25 onwards is 3.0.0 content and three
headings want merging into one - which is your call about what you released, not something the
repository can answer.

---

## 7. The four questions already asked, unchanged

These are in `docs/reviews/2026-09-03-c-sweep-report.md` under "What needs Adam" and are repeated here
only so this is one list:

1. Should the editor's "reaches nothing" warning match the runtime's rule? (`V36-C4`)
2. What shape should "Test Connection" come back in? (`RG3-C4`, MT-257 item 5) - your question back.
3. Should a route-command deletion say what it removed? (`FV2-C9`, `R28-A1`)
4. `DY3-C8` - already answered on MT-260; listed as a pointer only.

And three smaller ones carried from the release review's own list:

- Two stations may be given the same name silently; `uniqueNames()` disambiguates to `X (2)` without
  warning, and the javadoc claiming a user is told at authoring time is **orphaned** (`RC`).
- Should `BALANCED_PRIORITY` consider de-prioritised stations at all? The code implements `RC-B2`'s
  conservative answer.
- Whether any locomotive of yours has a bracket in its name, which cannot be used in a route command
  (`RGN-C3`). That needs your database, not the code.
