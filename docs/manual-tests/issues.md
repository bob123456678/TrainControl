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

**What is already done, 2026-08-23** - see [MT-130](tests.md#mt-130):

- The two live defects DD-A1 found are fixed. `reconcile` was keeping a deleted square's link name and
  switched-off flag, so the next link drawn there inherited both; and `forgetSquares` carried a line
  that could never match.
- `testStoreCollectionsAreHandledEverywhere` fails the build when a kept collection is missing from any
  of the twelve bookkeeping sites. Every omission DD-A1 lists would have been caught by it.

**What remains is the registry itself** - the ~830 lines, each collection knowing how to do its own
bookkeeping. The guard makes the omissions loud; it does not make them impossible, and it does not make
the file shorter. Left open at Adam's request rather than closed on the strength of a test.

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

### OB-067 - 2026-08-24 - a page named "2" breaks the id/name translation

**Kind:** bug
**Raised from:** review of the last day of commits, at Adam's request
**Filed:** 2026-08-24
**Decided:** 2026-08-24 - Adam: "A page should be allowed to be named 2 - let FR-013 dissolve it."

`toStored` and `pageOf` rest on an invariant stated in the code - "ids are numeric and names are not,
so the two never collide" - and nothing enforces it. `validateLayoutName` allows digits, so a page
called "2" is legal, and a page whose NAME equals another page's ID misroutes both translations. The
on-disk repair path is the most exposed: every key there is in id form, so `toStored` would rewrite
`"2:x,y"` through the page *named* "2".

**Not to be fixed on its own.** The name stays legal and the pun goes when FR-013 replaces these string
keys with objects. That is written into FR-013 as a requirement rather than left as an aspiration: the
work is not finished until an id and a name can no longer be mistaken for one another by any code path.

Left in the Inbox deliberately. It is a real, open bug with no hands-on test to hand out - there is
nothing for a person to check until FR-013 lands - and this file has no way to say "picked up, waiting
on another item": a bug carries a State of `-` and an `MT-###` link, so a receipt row for this one
showed a bare dash and read as though somebody had forgotten to fill it in. Filed and undone is the
truth, so filed and undone is how it is recorded.

The same family as OB-071, which FR-013 also dissolves.

### FR-014 - 2026-08-24 - show station name here

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-24 01:52  
**Build:** commit 62af99e6, build\classes, compiled 24 Aug 01:48 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

the show station name here right click menu option in the autonomy editor should clearly indicate the current station being shown, in cases where the user just sees [---] on the diagram.

### FR-017 - 2026-08-24 - the "no available paths" reasons, as a list you can read

**Kind:** feature request
**Raised from:** Adam, testing MT-144
**Filed:** 2026-08-24

Adam: "let's also make it clickable and show the notes in a popup with a scrollable text area with the
whole list of stations. Order them by ones that can be chosen autonomously and ones that cannot, with
the autonomous ones first."

Today the information mark beside **No available paths** is hover-only: the reasons are computed when
the pointer stops on the label and shown as a tooltip. A tooltip is the wrong container for this - it
cannot be scrolled, it goes away while you read it, and the list can be long on a real railway.

Wanted: click the mark, get a window with the whole list in a scrollable text area, ordered with the
stations autonomy could choose first and the ones it cannot after them.

One thing to be careful of, from the code that computes this: `explainDestinations` walks every
candidate route to every station and takes the Layout monitor to do it. The comment beside it records
what happened when that ran on the event thread - "that is the freeze this file's own comments say must
never happen, reintroduced by the feature meant to explain it". So the popup has to compute off the
event thread and fill itself in when the answer arrives.
