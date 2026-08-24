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
| 2026-08-24 | OB-067 | bug | A page named "2" breaks the id/name translation | - | FR-013 |
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
