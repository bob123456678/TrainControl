# Issues

Bugs and feature requests, in one inbox. Adam writes here - by hand, or through
[triage.py](triage.py)'s **New issue** button. Claude reads here, turns each item into a finding in
`docs/reviews/` (for a bug, under that round's prefix) or works it directly (for a feature request),
opens an `MT-###` entry in [tests.md](tests.md) to cover it, and clears the item out of the Inbox.

This replaces the separate `bug-reports.md` and `feature-requests.md` files from 2026-08-22 - the two
inboxes worked identically and existed only because bugs and features felt like different things when
this was hand-maintained. Now that [triage.py](triage.py) parses the file, one Kind field does the same
job as two files, and a bug filed while looking for a feature (or the reverse) has one place to land
regardless.

**How to use it, by hand.** Put anything under **Inbox** below - a sentence, a paragraph, a sketch.
There is no format to follow. Say whether it is a bug or a feature request if you can; if you can't
tell yet, say so and Claude will sort it out.

**How to use it, through triage.py.** The **New issue** button opens a small form: pick bug or feature
request, a one-line summary, and detail. It writes a structured entry here - `triage.py issues` can
then list every open one without anyone re-reading the file by hand.

**A bug is `OB-###`; a feature request is `FR-###`.** Separate counters - a glance at the ref says
which lifecycle an item is on. `OB` kept counting from before the split existed; nothing already
filed was renumbered.

**What happens next:** at the start of the next round, Claude picks up everything in the Inbox and
gives it a receipt row here. A **bug** also gets an `MT-###` tag in `tests.md`, disposition **needs
test** - a fix needs a repeatable hands-on check that the regression stays fixed, so that tag is
handed out immediately, not earned. A **feature request** is tracked directly instead, by default:
its receipt row gets a **State** in the same three words `tests.md`'s disposition uses (plus a
fourth, **declined**, for something cancelled - see below), set by Claude and only by Claude. It
only gets promoted to an `MT-###` tag if the eventual work turns out to need a genuine hands-on
test the way a bug fix does.

**Filing is not asking for it to be worked.** Filing puts it on the list; asking gets it built. That
split is deliberate - it lets you write something down the moment you think of it without deciding
then and there whether it is worth doing now. The exception is your own judgement: say a bug is urgent
in its own text and it is treated that way.

**Cancelling something already filed works the same way filing does: you request it, Claude acts on
it.** The **Request cancel…** button in triage.py's Feature requests/Bugs tabs - on a pending item or
an already-picked-up one - opens a small prompt for an optional reason and files a new structured
item naming what it is cancelling. Nothing changes immediately: at the start of the next round,
Claude reads that request, sets the target's State to **declined** (or, for a bug already promoted,
records the decision in its `MT-###` entry instead, since a promoted bug's own Comments are where its
outcome lives), and closes both items out. Only Claude sets State, the same rule as everywhere else -
this is how you ask, not how you decide.

**Before adding to the Inbox below - by hand, through the app, or as an automated round reading this
file to decide what to do - check whether it is already there.** `py -3 docs\manual-tests\triage.py
issues` lists every pending item in one command. OB-001 and OB-002 in the receipt table below are
what skipping this looks like: the same observation, filed twice, two minutes apart, because nothing
checked whether the first filing had already happened before the second one ran. A round that reads
this tracker to decide what needs doing has to read the Inbox and the ledger BEFORE writing to either,
not just before reporting back - "I looked, so I know what to build" is not the same claim as "I
looked, so I know this is not already here."

---

## Inbox

### OB-108 - 2026-08-25 - the layout editor writes the setup per gesture and the diagram only on Save

**Kind:** bug  
**Raised from:** the three-day review pass of 2026-08-25 (`AU-B8`)  
**Filed:** 2026-08-25 by Claude

**Filed rather than fixed, and the reason is that every fix for it is a judgement call about what to
give up.** It is low probability and the highest kind of cost, which is the combination that deserves
your decision rather than mine at the end of a long round.

**The mechanism.** `LayoutEditor.rememberAutonomy` calls `saveQuietly()` after every drag, move and
bulk edit - by design, because the autonomy session is rebuilt from disk after each one. The diagram
itself is edited in memory only: `LayoutDiagram.addComponent` writes nothing, and discard works by
re-reading the page files, so a diagram edit reaches disk at editor Save and not before.

Cancel is handled correctly - `undoAutonomyEdits` restores the setup and saves it.

The window is an ABNORMAL exit while an editor is open with unsaved diagram edits: the process killed,
the power lost, the machine sleeping badly. Disk then holds a setup keyed to the squares as MOVED,
and page files with the track where it was. On restart every page loads fine, so `pagesSafeToJudge` is
true and the OB-068 hold does not apply - and the first reconciling save prunes the moved stations'
names, lengths, facings and restrictions as settings for track that does not exist.

Silent, and it is the loss the whole reconcile-guard mechanism exists to prevent, arriving through a
door none of its four enforcement sites can see.

**Why it is not obviously fixable.** The three shapes I can see all cost something:

1. **Write the edited page's file beside the setup, per gesture.** Keeps the two halves together at
   every moment. Costs: the editor stops being cancellable in the sense it is now - discard works by
   re-reading the files, and the files would already have changed. That is a real feature being
   traded away.
2. **Journal the setup edits to a sidecar until Save.** Keeps cancel exactly as it is and closes the
   window completely. Costs: a new file, a new format, and a recovery path that has to be right - and
   an incomplete recovery path is a way to lose the same data by a new route.
3. **Decline to reconcile when the setup is newer than the newest page file.** Cheap, no format
   change, no feature lost. Costs: it is a heuristic, and I do not know its false-positive rate. A
   normal save writes both, and which lands last depends on the order of two calls I have not traced.
   A guard that fires on ordinary use is one you would learn to ignore.

My inclination is 3 if it turns out to be false-positive free, and 2 if it is not - but that is worth
ten minutes of measurement rather than a guess, and it is the kind of change that wants somebody
watching it.

**How likely, honestly.** It needs the process to die while an editor is open with unsaved diagram
edits. You do close editors. But this railway lives under OneDrive on a machine that sleeps, and the
window is every second an editor is open.

**Adam, 2026-08-25: "ob-108 - revert to pre save state."** Shape 2, and the reasoning behind picking it
turns out to be simpler than the three-way list above made it look. Shape 3 is a heuristic about file
times, and a heuristic that fails safe as a refusal fails UNSAFE as a proof - it would be deciding not
to reconcile on evidence that does not actually mean what it is being read as. Shape 1 trades away
Cancel, which is a working feature.

**Done, in `7a56d029`.** The editor already snapshots the setup in memory when it opens and puts it
back on Cancel; the same snapshot now also goes to disk, as `config/autonomy/setup-before-edit.json`,
and is put back at startup if it is still there - which can only mean the last session did not finish.
A snapshot that lives in memory is lost by exactly the event it exists to survive.

Both endings clear it, Save and Cancel, wired separately because the editor has them separately. That
half is the one that decides whether the mechanism is safe rather than merely useful: a note left
behind by a clean Save would throw away the edit you just made, which is worse than the bug. It is a
test rather than a comment - see [MT-191](tests.md#mt-191).

**State:** fixed unvalidated.

### OB-053 - 2026-08-23 - the diagram builds two labels per cell

**Kind:** bug  
**Raised from:** testRenderingCost.testLabelsBuiltPerCell, failing  
**Filed:** 2026-08-23 by Claude at Adam's request

Building one page of 336 cells constructs **720 LayoutLabels** - 2.14 per cell. The test's own note
says it was 1.6 per cell when it was written, and it fails above 2.0 because "more than two per cell
means something has started building the grid twice over".

Adam: "i could see text labels adding overhead, but never 2x."

**What is already ruled out.** Deterministic - 720 on three separate runs, not a timing artefact. Not
`LayoutGrid`: swapping in the copy from `87ada706`, the last commit whose full battery was green, gives
the same 720. Not the damaged autonomy data: restoring the complete setup gives the same 720. An
attempt to bisect to `87ada706` wholesale failed because the old `src` and `test` will not compile
against the current build inputs, so the question of whether this is new is still open.

**Where to look.** 336 x 2 = 672, and 720 - 672 = 48, so the shape is two per cell plus about fifty -
which would fit an icon label and a text label per cell, plus a caption or address label on some. That
is a guess from arithmetic; the way to settle it is to count what is actually constructed rather than
reason about it. `LayoutLabel.COUNT_CONSTRUCTED` and `COUNT_APPLIED` already exist for exactly this,
and the picture harness can render the page so the extra labels can be SEEN.

**Why it matters beyond the number.** MT-014 carries Adam's note that the editor feels slow, and a grid
built twice is the shape that would explain it.

**Claude, 2026-08-24.** Still open, and deliberately untouched tonight.

Adam asked for this one to be left alone, and nothing in this round went near the grid. It is worth
recording that it is probably the thing underneath [OB-084](#ob-084): a grid built twice is exactly
what would make `testRenderingCost` report two labels per cell, and tonight it reported 708 to 720
against a bound of 672 in six runs and then passed in the battery. Fixing this would likely settle
that measurement rather than the other way round.



**Claude, 2026-08-25.** A measurement, not a fix - this is still yours to look at with me.

Fixing OB-084 settled what the number actually is, and it is not what either entry said. Counted
inside the event-thread call that builds the grid, between the reset and the constructor returning,
it is **exactly one label per cell** - 384 for 384, six identical runs, no variance at all.

Every figure in this entry’s history - 1.6 per cell, 2.14, the 720 and the 621 - was measuring
construction PLUS however much deferred rebuilding had landed before somebody read the counter. That
is a different quantity and it has no fixed value: wait longer and it grows. Waiting for quiet gives
between 2.0 and 3.0 per cell depending on the machine.

So the question this entry asks is better put as: **why does the diagram rebuild every label about
twice more after it has been built?** Nothing is being built twice over at construction. Something is
rebuilding afterwards, and that is both a different mechanism and a more tractable one.

Your "i could see text labels adding overhead, but never 2x" still stands, and is still unanswered -
but it is about the rebuilding rather than the building.

**Adam, 2026-08-25: "ob-053 - so not a bug?"**

Half of it is not. The thing this was FILED as - "something has started building the grid twice over" -
is measurably not happening. Construction is exactly one label per cell, 384 for 384, six runs with no
variance. Every figure that made it look like two was counting construction plus however much deferred
rebuilding had landed before somebody read the counter, which is a quantity with no fixed value: wait
longer and it grows.

What is left is real but is a different question, and a smaller one: **the diagram rebuilds every label
about twice more after it has been built.** That is wasted work on a page you have said feels slow, and
it is the thing worth chasing - but it is a rebuild loop, not a double build, and it is not the
correctness problem the title claims.

Renaming it would be tidier than leaving it. Left as it is for now because you asked for this one to be
looked at together, and the history above is the evidence for why the title is wrong.

**State:** not a defect as filed; the rebuilding underneath it is still open.

### OB-025 - 2026-08-22 - DD-A1: the store says the same thing eleven times, fourteen times over

**Kind:** bug  
**Raised from:** the duplication and design review, at Adam's request  
**Filed:** 2026-08-22  
**Reopened:** 2026-08-23, at Adam's request - "leave OB-25 open"

**From [DD-A1](../reviews/2026-08-22-duplication-and-design.md).** Ranked last of the four
deliberately - biggest win, biggest blast radius.

`AutonomyCompanionStore` holds eleven collections and repeats the same per-collection shape fourteen
times. The report traces the four commits it took to finish adding the eleventh, which is the cost
stated as a fact rather than a worry.

**Its precondition is now met.** The report says to do this only after DD-A2 - the matrix test that
guards it was one of thirty-five classes `ant test` never ran - and DD-A2 was closed in `ae94421a`.

**Its own commit, nothing else in it,** and read DD-D9 first: `reconcile` and `applyTo` must stay
hand-written even if this lands.

**Claude, 2026-08-23.** What is already done - see [MT-130](tests.md#mt-130):

- The two live defects DD-A1 found are fixed. `reconcile` was keeping a deleted square's link name and
  switched-off flag, so the next link drawn there inherited both; and `forgetSquares` carried a line
  that could never match.
- `testStoreCollectionsAreHandledEverywhere` fails the build when a kept collection is missing from any
  of the twelve bookkeeping sites. Every omission DD-A1 lists would have been caught by it.

**What remains is the registry itself** - the ~830 lines, each collection knowing how to do its own
bookkeeping. The guard makes the omissions loud; it does not make them impossible, and it does not make
the file shorter. Left open at Adam's request rather than closed on the strength of a test.

**Claude, 2026-08-24.** Still open, still at Adam\u2019s request, and my recommendation is to hold it a
little longer.

The registry this describes only pays for itself if all eleven collections have the same shape, and
one of them does not: `tileDirections` still carries the `#dx,dy` route suffix and four `*Suffixed`
helpers that exist to be deleted by FR-013 stage two. Building the registry today means special-casing
that collection on the day it is written, which is the opposite of the point.

Nothing tonight changed the balance here. The guard test still makes an omission loud rather than
impossible, which is what it was always doing.



**Claude, 2026-08-25.** The thing I asked to hold this for is done, so the objection is gone.

My note above said the registry only pays for itself if all eleven collections have the same shape,
and that one of them did not: `tileDirections` still carried a `#dx,dy` route suffix and four
`*Suffixed` helpers. FR-013 stage two landed in `b71b6a26`. There is no string-keyed collection left,
the suffixed helpers are deleted, and every collection now goes through the same generic bookkeeping.

So the eleven really are one shape, and a registry written today would not have to special-case
anything on the day it is written.

Not started, and deliberately not started late in an autonomous round: this is the class holding the
data you have already lost twice, the ticket says "its own commit, nothing else in it", and it wants a
green battery either side with somebody watching. It is the obvious first thing for the next session.

**Adam, 2026-08-25: "fix it." Done, in `1d287202`, alone in its commit.**

Each collection is now one entry in `kept()`, and the ten mechanical sites walk it. Seven kinds cover
the twelve: a square to a string, to a length, to another square, to a list of squares; a square and a
route across it; a set of squares; a set of pages. What is special about a collection lives with that
collection - captions sparing a label its own tile lands on, lists being copied into a snapshot
because `forgetSquares` writes through them, `excludedPages` being keyed by page so the square-level
sites do nothing to it.

`reconcile` and `applyTo` are still written out by hand, per DD-D9's condition. Both ask a question the
registry cannot express, and an entry answering "not applicable" for them would be pretending they are
uniform.

Two smaller duplications went with it. `translatePortals` was `translateTileMap(portals)` letter for
letter. And the file's known-field list is now derived from the registry rather than repeating it -
which matters in a direction that is easy to get backwards: a collection MISSING from that list is
read into its own collection *and* kept as an unknown field, so the next save writes both and the
stale copy wins. That is what reverted every caption edit once.

`testStoreCollectionsAreHandledEverywhere` stays and guards what is left: that every kept collection is
IN the registry - the one thing the compiler cannot check - that every site still walks it, and that
the two hand-written sites still name everything.

**Both mutations were run.** A collection dropped from the registry fails the guard and nine of the
twelve settings-matrix tests. A site re-inlined with identical behaviour fails the guard and nothing
else - which is the case the textual guard exists for and the behavioural one is blind to.

**State:** fixed unvalidated.

### OB-110 - 2026-08-25 - switch to central station layout

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-25 18:16  
**Build:** commit 11b9ded6, build\classes, compiled 25 Aug 18:03 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

switch to central station layout is still not greyed out in debug mode/when not connected to central station

**Claude, 2026-08-25. Already fixed - this needs re-testing, not work.**

This is [MT-179](tests.md#mt-179) again, and it was filed against a build that predates the fix: the
header above records commit `11b9ded6`, compiled 18:03, and the fix landed later that day. The tree now
has `switchCSLayoutMenuItem.setEnabled(connected)`, where `connected` is `isCentralStationConnected()` -
which asks BOTH halves, because the first attempt asked `getNetworkCommState()` alone and that reports
whether the last sync succeeded. A sync reads through `CS2File`, which reads a local layout folder
perfectly happily, so a simulated session called itself connected. Your exact wording is quoted in the
comment at that line.

Please confirm on the current build; if it is greyed, this closes as a duplicate of MT-179.

### OB-114 - 2026-08-26 - testTimetableCaptureThroughARealRun is load-sensitive

**Kind:** bug  
**Raised from:** the triage API  
**Filed:** 2026-08-26  

testARealRunCapturesNothingWithCaptureOff drives a real autonomy run and asserts a locomotive moved before checking that nothing was captured. On a loaded machine nothing moves inside its budget and it fails on its own precondition - 'no locomotive moved, so nothing was declined and nothing is proved' - which is the honest failure of a test that could not reach the state it tests, but it fails a whole battery to say so.

Seen 2026-08-26 during a full battery run while the application was open and driving trains; the class passes on its own, twice, in 22s. The precondition is right to be there. What is wrong is that a timing budget decides whether a battery is green, so the same suite says different things depending on what else the machine is doing.

Worth either waiting on the movement rather than on a clock, or skipping with a stated reason when the run does not start, which is what a test that cannot reach its subject should do.

### FR-029 - 2026-08-26 - sidebar icon modernization

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-26 02:06  
**Build:** commit 309b984f, build\classes, compiled 26 Aug 02:02 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

the sidebar icons (locomotive, track, autonomy, signal, route, stats, log), while nice, date the application.  use modernized, simple icons with a plain blue color matching the flatlaf theme.

### FR-030 - 2026-08-26 - autonomy label content in editors.

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-26 02:10  
**Build:** commit 309b984f, build\classes, compiled 26 Aug 02:02 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

in the track diagram editor, hide autonomy labels completely. in the autonomy editor, have them show the station name by default (as currently in the track diagram editor) rather than the parked train.  in the autonomy editor, have an option to switch between showing station name and parked train in the labels.

### FR-031 - 2026-08-26 - autonomy station label color

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-26 02:12  
**Build:** commit 309b984f, build\classes, compiled 26 Aug 02:02 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

add a jmenu (preferences) setting for the station labels to be blue (default) or light gray (non default).  persist as with other settings.

## What has been picked up

Newest first. This is a receipt for something promoted into `tests.md` - **Became** names its
`MT-###` tag, and its state lives there from then on. Something tracked directly instead - most
feature requests, going forward - has no `MT-###` tag to point at; **State** is its disposition,
in the same three words `tests.md` uses (`needs test` / `fixed unvalidated` / `fixed validated`),
set by Claude and only by Claude, the same rule as everywhere else it appears. Exactly one of
State or Became is filled in for any row - a feature request either gets its own tag, or it does
not, never both.

| Filed | Ref | Kind | What | State | Became |
|---|---|---|---|---|---|
| 2026-08-26 | OB-115 | bug | Captions stopped voting on the row baseline, and stopped dragging their neighbours with them | - | [MT-197](tests.md#mt-197) |
| 2026-08-26 | FR-028 | feature request | Station captions are blue ovals with arrow icons, placed against the track | - | [MT-197](tests.md#mt-197) |
| 2026-08-26 | FR-027 | feature request | A locomotive on the square a train is running on, from a file you can replace | - | [MT-196](tests.md#mt-196) |
| 2026-08-26 | FR-025 | feature request | What holds a station back can be picked by clicking it | - | [MT-195](tests.md#mt-195) |
| 2026-08-26 | OB-111 | bug | The class that was rewriting your railway on every battery, found and given a sandbox | fixed unvalidated | - |
| 2026-08-26 | OB-109 | bug | The diagram is not taken off the screen to be rebuilt | - | [MT-194](tests.md#mt-194) |
| 2026-08-26 | OB-113 | bug | A reversing point that reaches no station is reported - your own case was a missing one, and nothing said so | - | [MT-193](tests.md#mt-193) |
| 2026-08-26 | FR-026 | feature request | The full editor is one item away on the diagram’s own menu | - | [MT-192](tests.md#mt-192) |
| 2026-08-26 | OB-112 | bug | The diagram’s autonomy menu says which square it is about | - | [MT-192](tests.md#mt-192) |
| 2026-08-25 | FB-A1..C2 | bug | [Independent pass over the last day](../reviews/2026-08-25-independent-fable.md) - two more A, one of them a defect in an LD fix | fixed unvalidated | - |
| 2026-08-25 | LD-A1..C5 | bug | [The last day, reviewed](../reviews/2026-08-25-last-day.md) - six A and seven B, seven of them from that same day; C6-C9 left open | fixed unvalidated | - |
| 2026-08-25 | OB-108 | bug | A layout edit that never finished is put back to how it was | - | [MT-191](tests.md#mt-191) |
| 2026-08-25 | OB-025 | bug | The store keeps a registry of what it keeps | fixed unvalidated | - |
| 2026-08-25 | OB-107 | bug | The signal window opened over the diagram it describes | - | [MT-182](tests.md#mt-182) |
| 2026-08-25 | OB-085 | bug | Two homes holding each other back are now proved impossible | - | [MT-187](tests.md#mt-187) |
| 2026-08-25 | OB-086 | bug | The duplication review's remainder - six places one rule was written twice | - | [MT-187](tests.md#mt-187) |
| 2026-08-25 | OB-089 | bug | The test suite audit’s remainder - seven guards that asserted less than they read | fixed unvalidated | - |
| 2026-08-25 | OB-084 | bug | testRenderingCost was a coin toss | fixed unvalidated | - |
| 2026-08-25 | FR-024 | feature request | The wait mark is a grey hourglass | - | [MT-183](tests.md#mt-183) |
| 2026-08-25 | FR-023 | feature request | Show Inactive Labels | - | [MT-181](tests.md#mt-181) |
| 2026-08-25 | FR-022 | feature request | Crop and pan a local locomotive icon | - | [MT-184](tests.md#mt-184) |
| 2026-08-25 | FR-018 | feature request | A page whose file returns keeps its id, and a deleted one is pruned | - | [MT-185](tests.md#mt-185) |
| 2026-08-25 | FR-013 | feature request | The store holds objects, not strings | - | [MT-186](tests.md#mt-186) |
| 2026-08-25 | OB-105 | bug | No application icon on the IP prompt | - | [MT-180](tests.md#mt-180) |
| 2026-08-24 | OB-103 | bug | A layout that would not read showed no notice | - | [MT-180](tests.md#mt-180) |
| 2026-08-24 | OB-102 | bug | Timetable stations carried their direction suffix | - | [MT-180](tests.md#mt-180) |
| 2026-08-24 | OB-099 | bug | CS3 locomotive database was not downloaded | - | [MT-170](tests.md#mt-170) |
| 2026-08-25 | OB-106 | bug | Legacy import made no configuration to load into | - | [MT-178](tests.md#mt-178) |
| 2026-08-24 | OB-104 | bug | Autonomy could start over a Central Station layout | - | [MT-179](tests.md#mt-179) |
| 2026-08-24 | OB-101 | bug | Capture toggle live while trains returned home | - | [MT-179](tests.md#mt-179) |
| 2026-08-24 | OB-100 | bug | Download CS layout offered with no station connected | - | [MT-179](tests.md#mt-179) |
| 2026-08-24 | OB-098 | bug | Switch to CS layout offered with no station connected | - | [MT-179](tests.md#mt-179) |
| 2026-08-24 | OB-097 | bug | A finished route still read as active on the locomotive panel | - | [MT-175](tests.md#mt-175) |
| 2026-08-24 | OB-096 | bug | The no-available-paths window: white text area, standard font size | - | [MT-177](tests.md#mt-177) |
| 2026-08-24 | OB-095 | bug | Show autonomy controls checkbox visible with nothing loaded | - | [MT-177](tests.md#mt-177) |
| 2026-08-24 | OB-094 | bug | Switch to Central Station Layout stayed selectable on a station layout | - | [MT-177](tests.md#mt-177) |
| 2026-08-24 | OB-093 | bug | Autonomy checkbox visible beside a greyed tab; the notice now offers a download | - | [MT-177](tests.md#mt-177) |
| 2026-08-24 | OB-092 | bug | Renaming a page to "5" excluded the page whose id is 5 and emptied it | - | [MT-161](tests.md#mt-161) |
| 2026-08-24 | OB-090 | bug | Autonomy error count, and Fix it offered instead of Start | - | [MT-173](tests.md#mt-173) |
| 2026-08-23 | FR-001 | feature request | Station unavailable while another point is in use, as lock edges | fixed unvalidated | - |
| 2026-08-22 | FR-006 | feature request | Editor grid is a toggle, and hovering no longer resizes a tile | fixed unvalidated | - |
| 2026-08-22 | FR-007 | feature request | Autonomy can be set up by importing, from the menu with nothing set up | fixed unvalidated | - |
| 2026-08-23 | FR-010 | feature request | Home locomotive picker filters, and offers the one being driven | fixed unvalidated | - |
| 2026-08-23 | FR-011 | feature request | Add to autonomy uses the same filtering picker | fixed unvalidated | - |
| 2026-08-23 | FR-012 | feature request | A dozen editor cycles must retain nothing | fixed validated | - |
| 2026-08-22 | FR-008 | feature request | Route editor: Highlight on Diagram dropped, Test renamed Test Condition | fixed validated | - |
| 2026-08-23 | OB-054 | bug | Page link menu: a repeated heading and an empty section | - | [MT-143](tests.md#mt-143) |
| 2026-08-23 | OB-055 | bug | The grid was drawn on the editor's own spacer row and column | - | [MT-143](tests.md#mt-143) |
| 2026-08-23 | OB-056 | bug | The grid toggle did nothing in the autonomy editor | - | [MT-143](tests.md#mt-143) |
| 2026-08-23 | OB-057 | bug | Autonomy could be started with errors outstanding, or an editor open | - | [MT-143](tests.md#mt-143) |
| 2026-08-24 | OB-059 | bug | Deleting a page told the autonomy setup nothing at all | - | [MT-142](tests.md#mt-142) |
| 2026-08-24 | OB-060 | bug | Page ids were list positions, so any rename or delete renumbered the others | - | [MT-142](tests.md#mt-142) |
| 2026-08-24 | OB-061 | bug | A source guard promised more coverage than it checked | - | [MT-142](tests.md#mt-142) |
| 2026-08-23 | OB-058 | bug | The Edit button brings an already-open editor forward | - | [MT-144](tests.md#mt-144) |
| 2026-08-24 | OB-063 | bug | The info mark had no glyph, so the font drew a box | - | [MT-144](tests.md#mt-144) |
| 2026-08-24 | OB-062 | bug | A locomotive rename did not reach a setup nothing had open | - | [MT-145](tests.md#mt-145) |
| 2026-08-24 | OB-064 | bug | Renaming or deleting a page invented an autonomy setup | - | [MT-142](tests.md#mt-142) |
| 2026-08-24 | OB-065 | bug | Page delete, rename, combine and the database sync ran during autonomy | - | [MT-141](tests.md#mt-141) |
| 2026-08-24 | OB-066 | bug | deletePage left cross-page pointers to the deleted page | - | [MT-142](tests.md#mt-142) |
| 2026-08-24 | OB-068 | bug | A page that fails to load had its whole setup pruned | - | [MT-148](tests.md#mt-148) |
| 2026-08-24 | OB-069 | bug | The timetable was an unrepaired holder of locomotive names | - | [MT-149](tests.md#mt-149) |
| 2026-08-24 | OB-070 | bug | Closing the app never asked the editor about unsaved work | - | [MT-155](tests.md#mt-155) |
| 2026-08-24 | OB-071 | bug | A page name containing a colon lost its setup to another page | - | [MT-150](tests.md#mt-150) |
| 2026-08-24 | OB-072 | bug | A failed timetable leg reported the run as completed | - | [MT-156](tests.md#mt-156) |
| 2026-08-24 | OB-073 | bug | The return-home planner could not see the FR-001 restriction | - | [MT-157](tests.md#mt-157) |
| 2026-08-24 | OB-074 | bug | A Central Station rename bypassed the unusable-name guard | - | [MT-153](tests.md#mt-153) |
| 2026-08-24 | OB-075 | bug | Legacy import wrote homes without the one-home sweep | - | [MT-151](tests.md#mt-151) |
| 2026-08-24 | OB-076 | bug | The editor's Cancel reverted edits made from the main window | - | [MT-154](tests.md#mt-154) |
| 2026-08-24 | OB-077 | bug | Start-up could hang for ever if the window failed to build | - | [MT-160](tests.md#mt-160) |
| 2026-08-24 | OB-078 | bug | A modal refusal dialog was raised from worker threads | - | [MT-160](tests.md#mt-160) |
| 2026-08-24 | OB-079 | bug | The event thread could block on the Layout monitor | - | [MT-160](tests.md#mt-160) |
| 2026-08-24 | OB-080 | bug | Comments that contradicted the code, and the two defects behind them | - | [MT-152](tests.md#mt-152) |
| 2026-08-24 | OB-081 | bug | A locomotive rename did not reach the diagram labels | - | [MT-153](tests.md#mt-153) |
| 2026-08-24 | OB-082 | bug | The autonomy editor title used a dash rather than a colon | - | [MT-158](tests.md#mt-158) |
| 2026-08-24 | OB-083 | bug | Cosmetics of the unavailable-while-occupied window | - | [MT-158](tests.md#mt-158) |
| 2026-08-24 | OB-067 | bug | A page named after another page's id collected its settings | - | [MT-161](tests.md#mt-161) |
| 2026-08-24 | FR-015 | feature request | Backup writes one archive holding all the state | fixed unvalidated | [MT-159](tests.md#mt-159) |
| 2026-08-24 | FR-014 | feature request | The caption menu items name the station | fixed unvalidated | [MT-162](tests.md#mt-162) |
| 2026-08-24 | FR-017 | feature request | The no-available-paths reasons, as a window | fixed unvalidated | [MT-163](tests.md#mt-163) |
| 2026-08-24 | FR-019 | feature request | The backup dialog offers to show the file | fixed unvalidated | [MT-166](tests.md#mt-166) |
| 2026-08-24 | FR-020 | feature request | Backing up a layout that lives on the Central Station | fixed unvalidated | [MT-170](tests.md#mt-170) |
| 2026-08-24 | FR-021 | feature request | The route file is downloaded, so it reaches the backup | fixed unvalidated | [MT-172](tests.md#mt-172) |
| 2026-08-24 | OB-091 | bug | The autonomy editor reserves the same room for the grid | - | [MT-172](tests.md#mt-172) |
| 2026-08-24 | OB-087 | bug | A deadlock reported on an old build; a real one found and reverted | - | [MT-167](tests.md#mt-167) |
| 2026-08-24 | OB-088 | bug | Capture stopped whenever the setup was rebuilt | - | [MT-168](tests.md#mt-168) |
| 2026-08-23 | OB-045 | bug | Autonomy Setup greyed while trains run | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-046 | bug | Go to the other end asks save/discard/cancel | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-047 | bug | Neither editor opens while trains run | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-048 | bug | Segment lengths capped at three digits | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-049 | bug | Renaming a page keeps its autonomy setup | - | [MT-135](tests.md#mt-135) |
| 2026-08-23 | OB-050 | bug | Start Autonomy greyed when it cannot start | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-051 | bug | Import and export moved where they can be found | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-052 | bug | The tidy-up report says what it is | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-042 | bug | Station labels on curves | - | [MT-132](tests.md#mt-132) |
| 2026-08-23 | OB-044 | bug | Station labels on bumpers | - | [MT-132](tests.md#mt-132) |
| 2026-08-23 | OB-043 | bug | Segment length entry | - | [MT-133](tests.md#mt-133) |
| 2026-08-23 | OB-041 | bug | Switching a paired link off switches its partner off | - | [MT-131](tests.md#mt-131) |
| 2026-08-23 | OB-023 | bug | The right-click menu and grid teardown, unified | - | [MT-128](tests.md#mt-128) |
| 2026-08-23 | OB-024 | bug | Port map and side-lookup cleanups | - | [MT-129](tests.md#mt-129) |
| 2026-08-23 | OB-039 | bug | Changing a locomotive's orientation updates its label | - | [MT-125](tests.md#mt-125) |
| 2026-08-23 | OB-040 | bug | Picking a guarding signal de-clutters the diagram | - | [MT-126](tests.md#mt-126) |
| 2026-08-23 | OB-028 | bug | The autonomy editor draws the railway, not a grid over it | - | [MT-127](tests.md#mt-127) |
| 2026-08-22 | OB-026 | bug | The trace stub at the end of a run cuts across a curved tile | - | [MT-119](tests.md#mt-119) |
| 2026-08-22 | OB-038 | bug | Export/import restoring a placement - already covered by a test | - | [MT-118](tests.md#mt-118) |
| 2026-08-22 | OB-037 | bug | The train star was drawn too small for its own outline | - | [MT-124](tests.md#mt-124) |
| 2026-08-22 | OB-036 | bug | Findings read "(Page 2)" rather than "On 2 -" | - | [MT-123](tests.md#mt-123) |
| 2026-08-22 | OB-035 | bug | Placing from the viewer did not update the caption | - | [MT-122](tests.md#mt-122) |
| 2026-08-22 | OB-031 | bug | Pairing a link now switches both ends on | - | [MT-121](tests.md#mt-121) |
| 2026-08-22 | OB-030 | bug | The Autonomy menu's tooltips wrap | - | [MT-120](tests.md#mt-120) |
| 2026-08-22 | OB-034 | bug | Renaming a station blanked its label until renamed back | - | [MT-116](tests.md#mt-116) |
| 2026-08-22 | OB-033 | bug | The Layouts menu declines while an editor is open, and both lead back | - | [MT-115](tests.md#mt-115) |
| 2026-08-22 | OB-029 | bug | Findings shown for a configuration nobody had loaded | - | [MT-114](tests.md#mt-114) |
| 2026-08-22 | OB-032 | bug | An empty "Trains May Depart" heading is hidden | - | [MT-113](tests.md#mt-113) |
| 2026-08-22 | OB-027 | bug | Three tool labels renamed | - | [MT-113](tests.md#mt-113) |
| 2026-08-22 | OB-022 | bug | DD-A6: three safety rules in code nothing called | - | [MT-112](tests.md#mt-112) |
| 2026-08-22 | OB-021 | bug | Layouts menu: Edit Layout Page under Manage Pages, and a doubled divider | - | [MT-111](tests.md#mt-111) |
| 2026-08-22 | OB-020 | bug | The autonomy tools column is narrower, and three labels changed | - | [MT-110](tests.md#mt-110) |
| 2026-08-22 | OB-019 | bug | Track lengths: a hotkey, the focus theft, and the font size | - | [MT-109](tests.md#mt-109) |
| 2026-08-22 | OB-018 | bug | Route editor: Save to the bottom right corner, Cancel beside it | - | [MT-108](tests.md#mt-108) |
| 2026-08-22 | OB-017 | bug | The track palette was empty after autonomy mode, under the wrong heading | - | [MT-107](tests.md#mt-107) |
| 2026-08-22 | OB-016 | bug | The track diagram viewer was drawn in edit mode while the editor was open | - | [MT-106](tests.md#mt-106) |
| 2026-08-22 | OB-015 | bug | The mode buttons are text-sized, not bold | - | [MT-105](tests.md#mt-105) |
| 2026-08-22 | OB-014 | bug | The page list is text-sized | - | [MT-105](tests.md#mt-105) |
| 2026-08-22 | OB-013 | bug | The tile menu reordered: five moves, and Length becomes Segment Length inside Advanced Parameters | - | [MT-104](tests.md#mt-104) |
| 2026-08-22 | OB-012 | bug | Starting autonomy from the track diagram menu jumped to the autonomy tab | - | [MT-103](tests.md#mt-103) |
| 2026-08-22 | OB-011 | bug | "Route Choice" reads "Choose Routing Logic..." | - | [MT-102](tests.md#mt-102) |
| 2026-08-22 | OB-010 | bug | "Show track lengths" reads "Track Lengths" | - | [MT-102](tests.md#mt-102) |
| 2026-08-22 | OB-009 | bug | Placing a locomotive did not update the labels; Move retired in favour of the edit dialog | - | [MT-101](tests.md#mt-101) |
| 2026-08-22 | OB-008 | bug | A direction edit with the arrows hidden happened invisibly | - | [MT-100](tests.md#mt-100) |
| 2026-08-22 | FR-005 | feature request | A white * on the station icon where a train is set up to be standing | - | [MT-099](tests.md#mt-099) |
| 2026-08-22 | FR-004 | feature request | Move "make a one way run from here" off the right-click menu and onto a button that asks for both points and a direction | - | [MT-098](tests.md#mt-098) |
| 2026-08-22 | OB-005 | bug | Switching between the autonomy view and the track diagram editor flashes - the window closes and reopens | - | [MT-095](tests.md#mt-095) |
| 2026-08-22 | FR-003 | feature request | Editor sidebar: buttons become a clickable list, and the layout/autonomy pair becomes a radio switch | - | [MT-097](tests.md#mt-097) |
| 2026-08-22 | OB-003 | bug | Editor window size varies by page and is often too small - default to the diagram's own size, capped at the screen | - | [MT-096](tests.md#mt-096) |
| 2026-08-22 | FR-002 | feature request | Appearance of stations and incoming arrows - circles, squares and diamonds are not semantic, and the arrows are messy | needs test | - |
| 2026-08-22 | FR-009 | feature request | Highlight on Diagram button in the route editor, and rename Test to Test Condition | - | [MT-064](tests.md#mt-064) |

**OB-008 to OB-012 are fixed, 2026-08-22.** Two of them share `MT-102`, because they are the same
test: read two labels and check they say the right thing. Splitting that into two entries would mean
two trips to the same screen.

**One part of `OB-009` is answered rather than fixed, and it is called out in `MT-101`.** "Adding a
locomotive to the graph doesn't correctly place it at the station where it belongs" - I could not find
a placement going to the wrong square, and the likeliest explanation is the missing refresh that was
the third part of the same report: a placement that does not appear looks exactly like a placement that
went somewhere else. `MT-101` asks Adam to re-check that specific half now the labels update, and says
what to tell me if it still happens.

**All five are fixed, 2026-08-22.** The three feature requests earned an `MT-###` after all, for the
same reason the two bugs did: each changed something only a person at the railway can confirm.

`FR-005` was the interesting one - what it asked for was already built and had been for a while. The
star is drawn by `paintTrainMark`; `paint()` just never got that far, because `isBlank()` did not count
a train as content and a square with only a train on it was therefore "nothing to draw". It appeared on
stations, which carry a badge and so were never blank, and was missing on exactly the squares the
request was about.

**`OB-003` and `OB-005` are fixed, 2026-08-22, on Adam's "fix all the bugs".** Both earned an
`MT-###` after all, which is the rule working rather than an exception to it: each one changed
behaviour that only a person at the railway can confirm, and `OB-005` in particular introduced three
new ways to lose work that no automated test can see the whole of. The three feature requests filed
alongside them - `FR-003`, `FR-004`, `FR-005` - are untouched and still have no tag.

**On the five filed 2026-08-22, and the Kind field.** All five arrived as `bug`; two of them are, and
three are feature requests. `FR-003`, `FR-004` and `FR-005` do not describe anything behaving wrongly -
they ask for a control to be built differently, moved, or added. `OB-003` and `OB-005` are behaviour:
a window that comes up the wrong size, and a switch that visibly closes and reopens.

Recorded by substance rather than by the dropdown, because the two routes differ - a bug earns an
`MT-###` regression check once it is fixed, a feature request does not by default. Nothing is lost
either way: the Kind as filed is above, and if I have called one wrong, say so and it moves.

**None of the five has an `MT-###`, deliberately.** That is the `MT-094` lesson applied - a tag is
earned when the work turns out to need a repeatable hands-on check, not handed out at pick-up. `OB-003`
and `OB-005` are the two most likely to earn one when they are built.

**`FR-005` may already be half-built.** `AutonomyEditorPanel` already calls `annotation.withTrain()`
for any square the setup puts a locomotive on, so the editor is already drawing *a* mark there. Worth
looking at what it currently draws before adding a second one - the request may be to change that mark
to a white `*` rather than to add one. Flagged rather than assumed.

**`OB-005` is the cost of a decision, not an accident.** The F2 sidebar was specified as "switching
tabs or mode is the same as the old exit and reopen: prompt for save/discard, then regenerate", and
the flash is that regeneration being visible. Removing it means keeping the window and swapping its
contents, which is a different design from the one that was asked for - worth saying before it is
built, not after.

**`FR-002` is retired-and-relit, not new.** It was promoted to `MT-094` on 2026-08-22, which turned
out to be the wrong call - a feature request that had not even been designed yet, sitting in the
Tests ledger looking like a hands-on regression test. `MT-094` stays in `tests.md`, superseded
rather than deleted (its tag is already cited by a commit), and this row is the live one now:
tracked here directly, with its own State, never promoted again unless the eventual work turns out
to need a genuine repeatable hands-on test the way a bug fix does.

*OB-001 and OB-002 are the same request submitted twice, two minutes apart, against commits 3a2106ab
and cd27e285. Recorded as one entry - a duplicate is a duplicate, and two ledger rows for one decision
is exactly the noise the ledger exists to avoid. If the second was meant to say something the first did
not, put the difference back in the Inbox and it gets its own entry.*

*`MT-064` is `FR-009` now - it was filed before any numbering existed at all, directly as a sentence
in what was then `feature-requests.md`, and its ref sat as `-` until 2026-08-22, when it got the
same real number everything else here has.*

*`OB-001`/`OB-002`, `OB-004`, `OB-006` and `OB-007` are now `FR-002`, `FR-003`, `FR-004` and
`FR-005` - renamed 2026-08-22 once bugs and feature requests got separate counters, since all four
are feature requests that predate the split. The table above and the `MT-###` entries they link to
use the new refs; older prose in this file and in commit messages still names them by the OB number
they were filed under, and this mapping is how to trace one to the other.*

## Where the older backlog is

`docs/reviews/2026-08-18-manual-test-plan.md` has a "Feature backlog (Adam, 18 August)" section -
things written down so they would not be lost, none of them scheduled. It has not been picked up into
this mechanism, deliberately: filing something here is a decision, and those were explicitly not
decisions. Anything from it you want on the ledger, paste into the Inbox above and it will be.
