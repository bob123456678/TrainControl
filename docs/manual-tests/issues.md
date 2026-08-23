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

### FR-001 - 2026-08-22 - excluding points

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-22 19:27  
**Build:** commit fc672631, build\classes, compiled 22 Aug 19:20 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

similar to excluding locomotives, we should be able to exclude the autonomous selection of a station when another (specified) point is occupied.  This is similar to how explicit lock edges worked.

### OB-022 - 2026-08-22 - DD-A6: three safety rules are in code nothing calls

**Kind:** bug
**Raised from:** the duplication and design review, at Adam's request
**Filed:** 2026-08-22

**From [DD-A6](../reviews/2026-08-22-duplication-and-design.md).** Filed by Claude at Adam's request
after reviewing the duplication report; ranked first of four.

`HomeLocomotiveMenu` had five callers and has one. The graph window was deleted (`d8db4879`) and took
four of them with it, leaving the RULES in the abandoned copy and the USE in the surviving 24-line one.
Verified by grep 2026-08-22: `addStationItem`, `addClearAllItem`, `editHomeLocomotive`,
`confirmExclusion`, `HomeStaging.canBeHome` and `HomeStaging.homeBrokenByExcluding` have **zero**
production callers between them.

Three rules are therefore unreachable:

- `canBeHome` - without it, a home that cannot be reached makes every future Return Home report
  IMPOSSIBLE, and the advice that dialog gives is to check the track, which is the wrong remedy.
- `homeBrokenByExcluding` - excluding a locomotive from the station that is its home now silently
  leaves a station and a locomotive disagreeing about each other.
- The "keep an assignment naming a locomotive not on the graph" rule - a station whose home names a
  since-removed locomotive opens showing "None", and pressing OK clears it.

**And the tests still pass, because they call the dead code directly** - so the suite reports the
guards as working. That is the part that makes this worth doing before the tidier-looking items.

Two ways to finish it, and the report is right that doing neither is the only option that is definitely
wrong: re-wire the live paths through the abandoned ones (~80 lines, three rules come back, their tests
start pinning something a user can reach), or delete the ~200 unreachable lines and their tests and
record that three rules were consciously dropped.

### OB-023 - 2026-08-22 - DD-B3 and DD-B5: two guards that reach three of four sites

**Kind:** bug
**Raised from:** the duplication and design review, at Adam's request
**Filed:** 2026-08-22

**From [DD-B3 and DD-B5](../reviews/2026-08-22-duplication-and-design.md).** Ranked second.

Two of a kind, worth one pass:

- **DD-B3** - four places construct a `LayoutGrid`; three of them call `discard()` on the outgoing one
  first. A grid that is not discarded leaves two timers armed that still hold the panel, which drops a
  spinner into the middle of the new grid.
- **DD-B5** - the right-click entry point is written four times and the empty-menu guard is on three,
  so one surface can show an empty popup.

~55 lines between them, and the point is to make both impossible to forget rather than to fix the
fourth site. Worth doing in the editor area specifically because that is where this month's defects
have been.

### OB-024 - 2026-08-22 - DD-C9 and DD-C10: two ten-minute cleanups

**Kind:** bug
**Raised from:** the duplication and design review, at Adam's request
**Filed:** 2026-08-22

**From [DD-C9 and DD-C10](../reviews/2026-08-22-duplication-and-design.md).** Ranked third, and
only because they are nearly free.

- **DD-C9** - `TileGraph` has `sideTowards` and `sideToward`: two methods one letter apart answering
  one question two ways. Confirmed still both present, nine call sites between them.
- **DD-C10** - the port table exists three times, one of them a Python script whose javadoc asks you to
  hand-edit it in step with the Java.

Neither has produced a defect. They are here because a name one letter from another name is a defect
waiting for a tired reader, and a table maintained by hand in two languages is one that will disagree.

### OB-025 - 2026-08-22 - DD-A1: the store says the same thing eleven times, fourteen times over

**Kind:** bug
**Raised from:** the duplication and design review, at Adam's request
**Filed:** 2026-08-22

**From [DD-A1](../reviews/2026-08-22-duplication-and-design.md).** Ranked last of the four
deliberately - biggest win, biggest blast radius.

`AutonomyCompanionStore` holds eleven collections and repeats the same per-collection shape fourteen
times. The report traces the four commits it took to finish adding the eleventh, which is the cost
stated as a fact rather than a worry.

**Its precondition is now met.** The report says to do this only after DD-A2 - the matrix test that
guards it was one of thirty-five classes `ant test` never ran - and DD-A2 was closed in `ae94421a`.
`ant test` now runs 75 classes including `testAutonomyStoreSettingsMatrix`.

**Its own commit, nothing else in it,** and read DD-D9 first: `reconcile` and `applyTo` must stay
hand-written even if this lands.

### OB-026 - 2026-08-22 - route tracing

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-22 21:14  
**Build:** commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

When arriving at a curved station (specifically s88 1015 from the north), the red trace draws a straight line on the tile, rather than following the shape of the station.  Running through curves looks OK.  Verify that this bug doesn't also exist elsewhere.

### OB-027 - 2026-08-22 - Autonomy tools buttons

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-22 21:33  
**Build:** commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

"Why is it not moving" -> Why not Moving?
Name everything -> Name Everything...
Leave this page out -> Exclude Page

### OB-028 - 2026-08-22 - grid in autonomy editor

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-22 21:35  
**Build:** commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

in the autonomy editor, the gray grid is not needed.  show the track diagram as it appears in the viewer, without the tile borders.  make sure the borders return in the editor.

### FR-006 - 2026-08-22 - editor grids

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-22 21:36  
**Build:** commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

in the editor, make the gray grid an option you can toggle in the visibile elements.  on by default, but persisted if turned off.  make sure hovering (blue/red outlines) doesn't increase tile widths when it is off.

### OB-029 - 2026-08-22 - autonomy error bar

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-22 21:39  
**Build:** commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

with autonomy completely unloaded, I am still shown x/y errors on this page at the top of the track diagram after closing the diagram  editor.

### FR-007 - 2026-08-22 - loading autonomy

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-22 21:41  
**Build:** commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

it should be possible to initially load autonomy from an import, not just forcing the creation of a new one.

### OB-030 - 2026-08-22 - tooltip lengths in the Autonomy top Jmenu

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-22 21:43  
**Build:** commit fc672631, build\classes, compiled 22 Aug 20:32 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

many tooltips are very wide.  split them.

### OB-031 - 2026-08-22 - link pairing

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-22 22:12  
**Build:** commit fc672631, build\classes, compiled 22 Aug 22:10 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when an active link from one page pairs with a link on another, both must be marked as active. right now, the target does't have to be

---

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
| 2026-08-22 | - | feature request | Highlight on Diagram button in the route editor, and rename Test to Test Condition | - | [MT-064](tests.md#mt-064) |

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

*Ref is `-` for MT-064: it was filed before the `OB-###` numbering existed, directly as a sentence in
what was then `feature-requests.md`. Everything filed from here on gets a real `OB-###`.*

*`OB-001`/`OB-002`, `OB-004`, `OB-006` and `OB-007` are now `FR-002`, `FR-003`, `FR-004` and
`FR-005` - renamed 2026-08-22 once bugs and feature requests got separate counters, since all four
are feature requests that predate the split. The table above and the `MT-###` entries they link to
use the new refs; older prose in this file and in commit messages still names them by the OB number
they were filed under, and this mapping is how to trace one to the other.*

---

## Where the older backlog is

`docs/reviews/2026-08-18-manual-test-plan.md` has a "Feature backlog (Adam, 18 August)" section -
things written down so they would not be lost, none of them scheduled. It has not been picked up into
this mechanism, deliberately: filing something here is a decision, and those were explicitly not
decisions. Anything from it you want on the ledger, paste into the Inbox above and it will be.
