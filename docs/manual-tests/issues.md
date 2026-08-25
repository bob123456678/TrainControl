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


### FR-013 - 2026-08-24 - The store should hold objects, not strings

**Kind:** feature request
**Raised from:** Adam, after the page-renumber round
**Filed:** 2026-08-24

Adam: "Ideally: string keys only matter at import/export. Internally, we should always use objects. We
can continue sorting by page name string and using the IDs as the unique identifiers."

`AutonomyCompanionStore` keys its eleven collections by `"pageName:x,y"` STRINGS, and `tileDirections`
additionally carries a `#dx,dy` route suffix that is parsed by hand at every site that touches it.
`TileKey` already exists as the object those strings stand for.

**Why this is worth doing, from this week's evidence.** Every defect in the page-renumber round was a
string key meaning something other than what the reader assumed:

- ids and names are both `String`, so a key built from an id and a key built from a name are the same
  type and compile interchangeably - which is how `fromStored` came to resolve one through the wrong
  map, and how a whole setup was reattached to the wrong pages with nothing looking wrong.
- the `#` suffix has been got wrong twice: once as a dead `tileDirections.remove(key)` that could never
  match a suffixed key (DD-A1), and once in `forgetSquares`, which had to grow a loop of its own to
  handle it.
- `isOnPage`, `rekeyOne`, `parseTileKey` and `pageOf` are all string surgery that a typed key would not
  need.

**What it is not.** Not a change to the FILE format: strings stay on disk, and the id/name translation
stays exactly where it is, in `toStored`/`fromStored` at the boundary. Not a change to the UI's sort
order either - pages still sort by name.

**It must also dissolve OB-067, which is now this item's problem.** `toStored` and `pageOf` rest on an
invariant stated in the code - "Ids are numeric and names are not, so the two never collide" - which
nothing enforces. `validateLayoutName` allows digits, so a page called "2" is legal, and a page whose
NAME equals another page's ID misroutes both translations.

Adam, asked whether to forbid such names: **"A page should be allowed to be named 2 - let FR-013
dissolve it."** So the name stays legal and the pun goes, which means this work is not finished until a
page id and a page name can no longer be mistaken for one another by any code path. A `TileKey` holding
a typed page reference does that by construction; a `TileKey` holding a `String` page merely moves the
problem, so that is the line between doing this and appearing to.

The on-disk repair path added for OB-062 is the most exposed and is the one to check first: every key
there sits in memory in id form, so `toStored` would rewrite `"2:x,y"` through the page *named* "2".
Nothing has hit it yet - it is a trap laid for later, and this is the work that removes it.

**Shape of the work.** `Map<String, X>` becomes `Map<TileKey, X>` across the store; `tileDirections`
becomes a compound key of square plus route rather than a string with a suffix; the boundary methods
gain the conversion that is today spread through the class. Mechanical but wide, and it touches the one
class that holds the data Adam has already lost once - so it wants its own commit with the battery
green either side, and the existing round-trip tests are what make it safe to attempt.

**Claude, 2026-08-24.** Where this stands: the correctness bar this item set itself is met; the
refactor it describes is not started.

**Done** - "the work is not finished until a page id and a page name can no longer be mistaken for one
another by any code path". They cannot be, and [MT-161](tests.md#mt-161) holds it. Entries belonging to
a page the index cannot resolve no longer enter memory at all: they are held as the JSON they arrived
as and written back verbatim, so every key in the live collections is a page NAME of a page that is
loaded. That is the invariant this class has always assumed and never had, and it dissolves OB-067,
which is now closed. It also closes a second hole nobody had noticed - one save while a page's file was
missing overwrote that page's whole setup.

**Not done** - the conversion itself. `Map<String, X>` is still `Map<String, X>`, `tileDirections`
still carries its `#dx,dy` suffix as string surgery, and `isOnPage`, `rekeyOne` and `parseTileKey` are
still parsing. Adam's "internally, we should always use objects" is unaddressed.

**What it would cost, measured rather than guessed.** Changing the ten declarations to `TileKey` and
`Set<TileKey>` produces **126 compile errors, every one of them inside
`AutonomyCompanionStore.java`** - the public API is already TileKey-shaped, so no caller anywhere in
the application breaks. That is a much smaller blast radius than this ticket assumed when it was
written, and it makes the work a contained, compile-error-driven session rather than a wide one.

Two things to carry into it:

- The configurations are **not** part of it. They key by page NAME on disk and are never translated,
  so they have no id/name question at all - the opposite keying from setup.json, and worth knowing
  before touching either.
- `tileDirections` wants a compound key of square plus route rather than a string with a suffix. That
  is the half that is design rather than mechanics.

Left open deliberately, and not started rather than half done: this is the class holding the data Adam
has already lost twice, and a conversion abandoned in the middle is worse than one not begun.

**Claude, 2026-08-24.** Stage one landed - `0c79bbe7`, with [MT-171](tests.md#mt-171) to check on
the railway.

Nine of the ten square-keyed collections hold `TileKey`: point names, tile lengths, barred arrivals,
portals, captions, station signals, blocked points, link names, the station set and the disabled-portal
set. `isOnPage` is one field comparison and `rekeyOne` one construction - the string surgery is gone
rather than moved - and the read boundary translates as it reads, so the two-list agreement that had to
hold by eye, and the state where collections held stored keys in memory-key fields, are both
unrepresentable.

**What remains, and it is smaller than this ticket first described.**

1. **`tileDirections`.** Still `Map<String, String>`, because its key is a square PLUS a route suffix
   and wants a compound key. It keeps four named `*Suffixed` helper variants - `onPageSuffixed`,
   `putBackSuffixed`, `rekeySuffixed`, `dropMissingSuffixed`, alongside the existing
   `moveSuffixedKeys` - which exist to be deleted with it. Erasure forbids overloading the typed
   versions, which is why they are named rather than overloaded.
2. **Nothing else.** `configurations` are JSON keyed by the printed form and always were;
   `excludedPages` is a set of page names, not squares; `getPointNames()` deliberately still returns
   printed keys, because it is the nearest thing to an export and its callers key their own maps that
   way.

**The lesson from stage one, for whoever does stage two.** It compiled clean and failed nine tests, and
every failure was the same defect: `Map.get`, `Map.containsKey`, `Map.remove`, `Set.contains`,
`Set.remove` and `String.equals` all take `Object`, so a printed key handed to a typed collection
compiles and silently answers false. Five live no-ops, all found by tests rather than by the compiler.

That is the honest limit of what this conversion buys. It removes the PARSING - the split-on-a-colon
that OB-071 lived inside - and it does not remove the `Object`-typed lookup. Stage two should start by
grepping every `.get(`, `.contains(`, `.containsKey(`, `.remove(` and `.equals(` against the collection
it is called on, and checking the argument's static type by hand.


### FR-018 - 2026-08-24 - a page whose file returns should get its old id back

**Kind:** feature  
**Found while fixing OB-067, and it needs your decision rather than a fix chosen for you.**

`writeLayoutIndex` retires the id of any page that is not in the list it is given. That is deliberate
and it is what stops a later page inheriting a deleted page's settings - the MT-135 loss, in the form
it took on 23 August.

The case it does not distinguish is a page that was never deleted. `CS2File` skips a page whose file
will not parse or is not there, and says so; on this railway, which lives in OneDrive, an unhydrated
placeholder or a file held open by the sync client is enough. If the index is written while a page is
in that state, the page is treated exactly as though it had been deleted: its id is retired, and when
the file comes back it is a NEW page with a new number.

Its settings are safe - they stay in `setup.json` under the old id, held and written back verbatim
(OB-067) - but they no longer attach to anything, because the page they belong to now answers to a
different number. Nothing is lost and nothing is found.

**Why this is a decision and not an obvious fix.** The two cases look identical from the index's point
of view: "this page is not in the list I was given". Telling them apart means asking a question the
index does not currently ask - whether the page's FILE is present - and the answer changes what safety
means:

- Retiring too eagerly costs a returning page its settings, which is this item.
- Retiring too reluctantly means a genuinely deleted page's id stays reserved, and a new page with the
  same name would collect the old one's settings. That is the loss the retirement was added to prevent,
  and it is the worse of the two.

So the shape of a fix is probably "retire the id only when the page file is really gone, and keep it
reserved while the file merely failed to load" - which is the same distinction `pagesNotLoaded` already
makes for the setup, and which the index does not make at all.

**How likely is it in practice?** It needs a save of the layout index during the window when a page is
unreadable. That is not rare here: the index is rewritten on page add, rename, delete and combine, and
OneDrive placeholders are a normal state on this machine.

**What would you like?** Options, roughly in order of how much they change:

1. Leave it. The settings survive; a page that comes back is set up again by hand.
2. Warn: if the index is about to be written while a page the setup knows about is not loaded, say so
   and let you cancel. Cheap, and it puts the decision where the information is.
3. Reserve rather than retire, for pages that failed to load as opposed to pages that were deleted.
   Correct, and the most work - it means the index has to distinguish the two, which today it cannot.

Option 2 on its own would have prevented every instance of this that could have happened so far, and
does not require the distinction. My recommendation is 2 now and 3 only if you meet it for real.

**Adam, 2026-08-24.** "if we are talking about orphaned data, why not warn the user and then prune?
maybe I am misunderstanding the distinction"

**Claude, 2026-08-24.** He was not misunderstanding it - he dissolved it. My three options all
assumed the application had to guess which case it was in, and ranked them by how well each guessed.
It does not have to guess. It is the only party that cannot tell the two apart; the person who just
renamed a page knows perfectly well whether they deleted it.

**The agreed design.** When the index is about to be written and a page the setup knows about is not
loaded, stop and say so by name, then offer the two answers as what they actually mean:

- *Keep it* - the page is coming back (OneDrive has not hydrated it, the file is held open). The id
  stays reserved, nothing is retired, and the settings reattach when the file returns.
- *It is gone* - the page really was deleted. The id is retired as it is today, AND the orphaned
  settings under it are pruned rather than held forever, which is the half option 3 never addressed.

Cancel stays available and leaves the index unwritten.

That is option 2 and option 3 at once, at a fraction of option 3's cost, because the distinction the
index cannot make is supplied by the one participant who can. It also fixes something none of my
three options touched: today a genuinely deleted page's settings are held verbatim under a retired id
for ever, growing setup.json with data that can never attach to anything again.

**Not built yet.** The design is settled; no code has been written. It needs the warning point in
`writeLayoutIndex`'s callers rather than in `writeLayoutIndex` itself, which must stay callable from
tests and from the startup path without a dialog - see IAR-A1, which already gave it a `renamedFromTo`
parameter for the same reason.



### OB-084 - 2026-08-24 - testRenderingCost is a coin toss, and the battery number depends on it

**Kind:** bug  
**Not from this round.** Found while confirming a battery, and **re-diagnosed on 2026-08-24 after the
first explanation turned out to be wrong** - the correction is below and is the more useful half.

**What is true, measured.** `ui.testRenderingCost.testLabelsBuiltPerCell` asserts at most two labels
per cell, which on the page it measures is 672. Run five times against a single unchanged sample
layout, the same page each time:

| run | cells | labels built | verdict |
|---|---|---|---|
| 1 | 336 | 720 | fails |
| 2 | 336 | 621 | passes |
| 3 | 336 | 685 | fails |
| 4 | 336 | 720 | fails |
| 5 | 336 | 597 | passes |

Same fixture, same page, same build. **The test is non-deterministic** - roughly a coin toss - and
`docs/reviews/README.md` has the paragraph for exactly this: "A regression test that only sometimes
catches the regression is worse than none, because it reads as protection."

**What I said first, and why it was wrong.** The original entry said the number depended on the sample
setup's `version` field: 720 for a version 1 file, 621 for the version 2 the application rewrites it
as, so the test failed from a clean checkout and passed inside the battery because earlier classes had
migrated the fixture. That was two measurements, one of each, and it fit. Run 3 above is a version 2
file producing 685, and run 4 a version 2 file producing 720. The fixture contents are identical either
way - the two files differ in the version number and two empty collections, nothing else.

So the version had nothing to do with it, and the "clean checkout versus battery" story was a
coincidence of two samples. I wrote a crisp causal claim from a sample of two, which is the mistake
this session has spent most of its review effort on.

**The likely mechanism, offered as a lead rather than a finding.** The test resets
`LayoutLabel.COUNT_CONSTRUCTED`, builds the grid inside `invokeAndWait`, and reads the counter as soon
as that returns. Both `LayoutGrid` and `LayoutLabel` post work with `invokeLater` - and `LayoutLabel`
also starts a worker thread - so anything the grid schedules rather than does is counted or not
depending on where the event queue has got to. That would produce exactly this: a stable floor with a
variable amount of extra work landing before the read. Not confirmed; whoever picks this up should
confirm it before acting on it.

**What this means for the battery numbers in this session's commits.** Several say "101 classes green"
and several say "100 classes green, the one failure being OB-084". Both are honest reports of what ran,
and the difference between them is this coin toss rather than anything about the code. No battery
result in this session should be read as evidence about `testRenderingCost` either way.

**What to do, in order.**

1. Make the measurement deterministic - drain the event queue before reading the counter, or count on
   the thread that builds. Until then the assertion means nothing.
2. Then, and only then, look at whether the number is too high. The bound was written at 1.6 labels
   per cell and the floor here is around 600 for 336 cells, which is 1.8 - so there may be a real
   regression underneath, and it is invisible while the measurement is this noisy.
3. The label duplication itself belongs to [OB-053](#ob-053), which Adam has asked to be left alone.

**Claude, 2026-08-24.** Six more runs, and they change the picture: it is not behaving like a coin
toss today, it is failing every time.

Three runs against the current build gave 708, 720, 720 labels for 336 cells, against a bound of 672.
Three runs against the build from before the OB-090 change - compiled from `c4a2b7bf` into a separate
directory, so the two differ only in the code - gave 720, 720, 720.

Two things follow. The first is the reason I measured: nothing in this round caused it, and the
identical figure on both sides settles that rather than leaving it to judgement. The second is more
useful. The earlier sample (720, 621, 685, 720, 597) straddled the bound; this one does not go near
it. So the variance is real but the FLOOR may have risen, which is exactly the case step 2 above says
is invisible while the measurement is noisy - and it is now visible enough to be worth someone
looking. 720 for 336 cells is 2.14 labels per cell, and the comment in the test says more than two per
cell means the grid is being built twice over.

That is a statement about what the number is doing, not a diagnosis. The measurement still has to be
made deterministic first, per step 1, or the next person will be reading tea leaves as I have been.


### OB-085 - 2026-08-24 - the staging scan could prove a blockedBy cycle impossible, and does not

**Kind:** bug  
**Filed rather than fixed, deliberately.** Raised as FSR-C3 by the second review of the fix round.

The impossibility scan in `HomeStaging.plan()` proves that no arrangement can park a locomotive at its
home. Its FR-001 test was removed on 2026-08-24 after two attempts at it were both wrong, and the
comment left in its place said there is "no state-independent statement to make about an FR-001
blocker".

That is too strong, and the reviewer produced the counterexample. Two homes, each held back by a square
the other's occupant must end up on, is impossible from the structure alone - whoever is standing where
at the start, the last move of any arrangement puts a train on a square that closes the other station.
No occupancy needs to be read to know it.

What it costs today is not wrongness but time: the search exhausts its budget and answers
NO_PLAN_FOUND - "no arrangement found, it may still be possible" - which claims less than the truth but
claims nothing false. On the small probe it took 9ms. On a layout the size of Adam's it would spend the
whole budget to say "maybe" about something a pairwise scan settles.

**Why it is not being fixed now.** The last two things put into that scan were both wrong, both looked
obviously right, and both shipped with a test that could not tell the difference. The scan is the one
place in this class that makes a claim to the operator rather than a suggestion - IMPOSSIBLE names
locomotives as blocked and skips the search - so the bar for adding to it is higher than for anything
else here, and a third attempt made in the same session as the first two is not the way to clear it.

**What a fix would look like.** The pairwise goal scan directly below it already does this shape of
reasoning for conflicting homes - two homes on one detection section - and says why: "it spends the
whole budget to say maybe about something provable in a pairwise scan". The cycle case belongs beside
it, as a second pairwise test, and wants a test whose fixture is a genuine two-home cycle and a
mutation showing that the scan rather than the search is what answers.

**Claude, 2026-08-24.** Still open, not started, and not touched tonight.

Recording it so this entry is not silent: what would close it is a test whose fixture is a genuine
two-home cycle, plus a mutation showing that the pairwise SCAN rather than the search is what answers
it. That is the same shape as the test beside it, and until it exists this entry is analysis rather
than a change.


### OB-086 - 2026-08-24 - the duplication review's remainder: six places one rule is written twice

**Kind:** bug  
**From [2026-08-24-duplication-robustness.md](../reviews/2026-08-24-duplication-robustness.md), prefix
DR.** That pass found one A and ten B findings. Five were fixed the same day - the held-field lists
(DR-A1), the FR-001 reason that could never reach the window (DR-B3), the two silent name-resolution
doors (DR-B9), the two live collections read off-thread (DR-B7), and the swallowed index read that
renumbered every page (DR-B4 part 2). This is the rest, kept together because they are one subject.

Every one of them is the shape Adam named when he asked for the pass: **one decision written down in
more than one place, which then drifts.** None has bitten yet. Page management and autonomy linkage had
not bitten either, until they did.

| | What is written twice | What it costs when they drift |
|---|---|---|
| **DR-B1** | The staging audit's list of "correct divergences" between planner and runtime is missing FR-001, which OB-073 added to both sides on different terms | The debug audit accuses the planner of a fault on every layout that uses FR-001 - noise in the one tool that exists to find real divergence |
| **DR-B2** | FR-001 itself exists in three inequivalent forms: runtime (block-aware, fenced behind autonomy running), planner (sensor-sibling-aware, unfenced), test oracle (point-level, no departing-train exemption) | The planner and the railway disagree about which stations are available. `AutonomyBuilder`'s own comment names the layout where the block-and-sensor coincidence ends |
| **DR-B4 (1) (3)** | Two parsers of `gleisbild.cs2` - `readLayoutIndexIds` and `CS2File` - agreeing by a sentence; and nothing anywhere notices two pages claiming one id | The setup keyed by ids the index does not believe. This is the misattachment class, with no rename in sight to blame |
| **DR-B5** | `pageIsHere` restates `pageOf` branch for branch, kept in step by a comment saying it must be | FR-018, already filed, changes one of them |
| **DR-B6** | A third inline arrival-sides loop (`facingChoices`) bypassing the `arrivalSides` door; a new `onwardFrom`/`facingOf` pair kept in step by a sentence | The DD-A7 family, which this repository has already paid for twice |
| **DR-B8** | The settings matrix never calls `setPageIds`, so every save/load cell runs name-keyed | The whole id-translation layer is outside the guard that exists to cover it - the "written raw" defect class the store has had once already |
| **DR-B10** | The "an absent page must not be judged" rule is enforced by four different mechanisms, one pruner runs outside the guard, and the reconciliation report is discarded at five of six save doors | A page's settings pruned while its file is missing, silently, which is the loss OB-067 was about arriving by a different door |

Plus eight C findings in the same document, which are the same subject at lower stakes.

**Claude, 2026-08-24.** Two more, from the reopen audit:

- **RA-C2.** A blocked-points entry that is carried through because the picker no longer offers it
  renders as a blank check box when its square has lost its name. The picker also has no automated
  guard at all now - the source rule that pinned its filters was deleted with the filters. The
  check-side warning this item already recommends is the same fix.
- **RA-C3.** For an index that is genuinely locked, the delete path still throws AFTER the page file
  is gone and the setup has forgotten it, leaving an index entry for a page that no longer exists.
  SV-B1 fixed the common case - a file this build cannot decode - and this residual survives.

**Why this is one item rather than seven.** They share a remedy shape - name the rule, give it one
home, and let the ratchet count the sites - and doing them separately means seven passes over the same
four files. DD-A1 is the cautionary example: the eleventh collection took five commits to finish
wiring because each site was found on its own.

**Where to start, if this is picked up.** DR-B8 first: it is test-only, it pairs with the DR-A1 work
already done, and until the matrix runs with page ids set, every other fix in this list is being
checked by a guard that cannot see the layer they are about.

**What must NOT be consolidated,** recorded so it is not rediscovered: `reconcile` and `applyTo` stay
hand-written (DD-D9), and the transient occupancy term stays out of any shared "sendable destination"
predicate - `explainDestinations` separates standing bars from transient ones deliberately, and that
separation is what makes the FR-017 window's two groups mean anything.

### OB-089 - 2026-08-24 - the test suite audit's remainder: seven guards that assert less than they read

**Kind:** bug  
**From [2026-08-24-test-suite-audit.md](../reviews/2026-08-24-test-suite-audit.md), prefix TA.** Adam
asked for a pass over the tests looking for "false assumptions, missing ground truth, or incomplete
coverage". It found one A and ten B, backed by twenty mutation experiments - eight of which
demonstrated a false pass by making the change the test should have caught and watching it stay green.

Four were fixed the same day: TA-A1 (the encoding test never asserted the accented page's id, so a
lenient decode that renumbered every page passed 11 of 11), TA-B1 (a class commented out of
`build.xml` satisfied the guard against classes leaving the battery), TA-B4 (a wiring check that read
tokens inside private helpers, so deleting the only caller left it green - DD-A6's exact shape), and
the CRLF class, which the audit confirmed is now fully closed.

This is the rest. They share one shape, and it is the shape worth naming: **a test that reads
something adjacent to the thing it claims to guard.** A token instead of a call, non-emptiness instead
of contents, a fixture built by the code under test instead of an independent statement of what is
right.

| | What is asserted | What is not |
|---|---|---|
| **TA-B2** | `deletePage` names each collection it forgets | That it forgets them. The gathering neutered but still naming the collection passes 85 tests across all four guard classes - the settings matrix has no deletePage column |
| **TA-B3** | `testLoadData`'s seven old-build fixtures load non-empty | That they load *correctly*. A restore keeping one component of each passes 7 of 7 |
| **TA-B5** | The Central Station sync-safety block, in four sentences | A connect-timeout assertion that cannot fail (the "unreachable station" is a closed local port, which refuses instantly), a cumulative static counter used as proof of a fetch, a vacuous `before`, and a false premise about sync wiping locomotives - while route deletion, which the sync does perform, has no garbled-fetch test |
| **TA-B6** | CS3 `isNotFoundError` | Its JSON branch, which is the one real pre-2.6.0 firmware exercises. Replaced with `return false`, everything passes: the test server only ever sends real 404s |
| **TA-B7** | That UDP messages are constructed | That any BYTE goes out correctly. Nothing anywhere asserts an outgoing datagram; the short-datagram guard and reader-restart-after-reopen are untested, and a reopen to an ephemeral port would satisfy both existing assertions with reception dead |
| **TA-B8** | `testFacingFollowsTheTrack`'s oracle | Anything independent - it checks membership in the same `getRoutes` map the facings are built from, so MT-125 is satisfied by construction. Plus a silent return on a null reducer |
| **TA-B9** | That `DiagramMonitor` can be constructed | That it ever publishes. Both publisher tests use a null layout, so `compute()` - milestones, runs, lock wash - is unreachable, and a monitor that says nothing for ever passes |
| **TA-B10** | That the route editor's frame is locked | The `isCellEditable` gate, which is the surface its own javadoc names - the test cannot see table cell editors. The focusability loop is vacuous if the list is empty |

Plus fifteen C findings in the same document.

**Where to start.** TA-B9 and TA-B7 are the two with no coverage at all rather than weak coverage, and
they are the two closest to the railway: a monitor that stops publishing and a datagram that goes out
malformed are both invisible to every test that exists. TA-B2 is the cheapest - one more column in the
settings matrix, which is a file that already has the shape.

**What the audit also established, and is worth keeping.** Eight guards were mutated and fired
correctly, so they are known-good rather than assumed-good. One candidate finding was disproved before
it was filed - `reconcile`'s blocked-points keep-side IS guarded. And one premise in the brief I gave
the reviewer was wrong: I said no test drives a real event thread, and `testBusyDialogInteraction`
pumps a real modal EDT. The genuine EDT hole is narrower than I claimed - blocking dialogs on the
export path, and four test classes that call `setViewListener` off the event thread themselves.

**Claude, 2026-08-24.** Still open, not started, and not touched tonight.

Two of tonight\u2019s findings belong to its family and were fixed on their own tickets rather than
here: the guard whose assertion its own comment satisfied (see the MT-153 work) and the surface rule
that remembered which bundle keys carried a placeholder instead of reading them. Both are exactly the
"guards that assert less than they read" this entry is about, so the remainder is narrower than it was
this morning - but the seven it lists have not been worked through.

### OB-098 - 2026-08-24 - switch to cs layout is possible even when not connected to a cs

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:44  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

switch to cs layout is possible even when not connected to a cs.  also, backup up files from cs should not be offered in this case.

### OB-099 - 2026-08-24 - cs3 files still not backed up

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:46  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

these are examples of files that should be downloaded when connected to a CS3 only (not CS2) (IP depends on what's being used) http://192.168.50.25/app/api/locos http://192.168.50.25/app/api/automatics http://192.168.50.25/app/api/mags

### OB-100 - 2026-08-24 - cs download layout menu item toggle

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:48  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

download central station layout files should also be greyed out when not connected to a cs

### OB-101 - 2026-08-24 - capture locomotive commands toggle

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:50  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

capture locomotive commands button can still be toggled while trains are returning home

### OB-102 - 2026-08-24 - directions shown in timetable stations

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:51  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

timetable stations show (northbound) and (southbound) etc.

### OB-103 - 2026-08-24 - failing to read layout from CS shows no error in UI

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:51  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

if we get 2026-08-24 23:51:15.936 Model error: no layout loaded., there is no UI error notice shown.

### OB-104 - 2026-08-24 - autonomy tab not greyed out when using CS layout

**Kind:** bug  
**Raised from:** MT-177 (Four menu and window details from the testing round)  
**Filed:** 2026-08-24 23:53  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

I was able to start autonomy, even though no trains on graph and even though layout was being read from CS.  a defunct train started running, and switches were set.

### FR-022 - 2026-08-24 - crop function

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 23:57  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

add a crop / pan function to local locomotive icons at the time of image selection.

### OB-105 - 2026-08-25 - no traincontrol window icon in IP prompt

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-25 00:11  
**Build:** commit 8db330da, build\classes, compiled 24 Aug 23:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

no traincontrol window icon in IP prompt

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
