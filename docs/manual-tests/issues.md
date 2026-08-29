# Issues

Bugs and feature requests, in one inbox. Adam writes here - by hand, or through
[triage.py](triage.py)'s **New issue** button. Claude reads here, turns each item into a finding in
`docs/reviews/` (for a bug, under that round's prefix) or works it directly (for a feature request),
opens an `MT-###` entry in [tests.md](tests.md) to cover a bug fix (a feature request only if the work
turns out to need one), and clears the item out of the Inbox.

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
its receipt row gets a **State** in three of the four words `tests.md`'s disposition uses (not
**superseded**, which has no meaning for a request nobody has coded yet), plus one of its own,
**declined**, for something cancelled - see below - set by Claude and only by Claude. It
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

### OB-130 - 2026-08-29 - Ruling needed: may importing a legacy graph shut pages you already decided about

**Kind:** bug  
**Raised from:** the triage API  
**Filed:** 2026-08-29  

excludeRepeatedSensorPages has TWO call sites and they carry contradictory intentions, both deliberate. The comment on testRunningAgainOverASettledSetupChangesNothing says the method is safe only where there are no operator decisions to overrule - "the first configuration on a layout is created, which is the one moment there are no decisions to overrule" - and that a second call site is the bug. The second site, AutonomyViewerPanel.importLegacyGraph, carries a justification written later: a setup made before this existed, or one whose pages have been redrawn since, has never had it applied, and importing is exactly when that matters. The question is whether importing may shut pages you had already chosen to keep. A test currently ratchets the count at 2 so a third site cannot appear unnoticed; settling this means changing that number and recording which way it went. Found by the test-suite review (TST-B15).

### OB-132 - 2026-08-29 - testTimetableOnDerivedGraph now skips, so it covers nothing

**Kind:** bug  
**Raised from:** the triage API  
**Filed:** 2026-08-29  

This class ran clean through several batteries and began skipping after the review round, with "nothing moved in 12s, so there is no timetable to replay". The cause is a correct fix: TST-B11 found it was reading model.getLayoutList(), i.e. whatever layout the machine happened to have - your real railway - and pointed it at test_layout instead, correcting the configuration name from "Autonomy 1" (which matched nothing) to "Main". Against the fixture, two stations are found and five of seven locomotives are placed, and then nothing runs in the window. So it is now honest and vacuous, which by this repo own rule is the same as having no test. Unknown whether the cause is the run window, the Main configuration paths, or the placement.

## What has been picked up

Newest first. This is a receipt for something promoted into `tests.md` - **Became** names its
`MT-###` tag, and its state lives there from then on. Something tracked directly instead - most
feature requests, going forward - has no `MT-###` tag to point at; **State** is its disposition,
in three of the four words `tests.md` uses (`needs test` / `fixed unvalidated` / `fixed
validated` - not `superseded`, which has no meaning for something nobody has coded yet), plus
**declined** for something cancelled, set by Claude and only by Claude, the same rule as
everywhere else it appears. Exactly one of
State or Became is filled in for any row - a feature request either gets its own tag, or it does
not, never both.

| Filed | Ref | Kind | What | State | Became |
|---|---|---|---|---|---|
| 2026-08-29 | OB-139 | bug | move pointer in autonomy editor. | - | [MT-210](tests.md#mt-210) |
| 2026-08-29 | OB-138 | bug | doube clicking station label in track viewer | - | [MT-209](tests.md#mt-209) |
| 2026-08-29 | OB-137 | bug | route table freeze on import | - | [MT-208](tests.md#mt-208) |
| 2026-08-29 | OB-136 | bug | simulate: true has gone from the live autonomy configuration | - | [MT-207](tests.md#mt-207) |
| 2026-08-29 | OB-134 | bug | Six destructive confirmations still pre-select Yes | fixed unvalidated | - |
| 2026-08-29 | OB-133 | bug | The unreadable-import test has never exercised the rollback it is named for | fixed validated | - |
| 2026-08-29 | OB-135 | bug | The wait mark still does not animate on the track diagram | - | [MT-206](tests.md#mt-206) |
| 2026-08-29 | OB-131 | bug | Start Autonomy never comes back if a train never berths | - | [MT-205](tests.md#mt-205) |
| 2026-08-29 | FR-042 | feature request | Spot-check the newly translated autonomy strings | - | [MT-204](tests.md#mt-204) |
| 2026-08-28 | FR-041 | feature request | A splash while the station is reached, before there is a window to put one in | fixed unvalidated | - |
| 2026-08-28 | OB-129 | bug | The wait mark counted timer ticks on an event thread that coalesces them, and was capped at 400 in a top-aligned parent | fixed unvalidated | - |
| 2026-08-28 | FR-040 | feature request | The Layout menu names its data source instead of offering to tell you | fixed unvalidated | - |
| 2026-08-28 | OB-128 | bug | A greyed tab is not a closed door: nine methods opened it by index, and the panel kept the railway that had been unloaded | fixed validated | - |
| 2026-08-28 | OB-127 | bug | An empty layout path is the working directory, and its index named five pages that were never deleted | fixed validated | - |
| 2026-08-28 | OB-126 | bug | The grey Edit Layout button named one of its three reasons whatever was true | fixed unvalidated | - |
| 2026-08-28 | OB-125 | bug | The crop editor reopens where the crop was taken, not on the default view | fixed unvalidated | - |
| 2026-08-28 | FR-038 | feature request | Mis-filed: the crop editor quirk is a bug, re-filed as OB-125 | cancelled | - |
| 2026-08-28 | FR-039 | feature request | The request to cancel FR-038, which is done - nothing of its own to work | cancelled | - |
| 2026-08-27 | FR-036 | feature request | Plus and minus walk through the pages, through the switch that already existed | fixed unvalidated | - |
| 2026-08-27 | FR-037 | feature request | Travel restrictions can be drawn on the ordinary track diagram, on by default | fixed unvalidated | - |
| 2026-08-27 | OB-122 | bug | Not a defect: the warning was right, and the track diagram was the thing at fault | fixed validated | - |
| 2026-08-27 | OB-123 | bug | A reversing point is judged by where a train could go, not by what arrives | fixed unvalidated | - |
| 2026-08-27 | OB-124 | bug | Four windows had no application icon, and the rule was written seven times | fixed unvalidated | - |
| 2026-08-27 | FR-033 | feature request | Fifty locomotive mapping pages, refused in the method and greyed in the menu | fixed unvalidated | - |
| 2026-08-27 | OB-121 | bug | The + row in the conditions list was handed the previous cell’s grey by a recycled renderer | fixed unvalidated | - |
| 2026-08-27 | FR-034 | feature request | The label chooser opens on the nearest station, and the last-clicked one is spent after one use | fixed unvalidated | - |
| 2026-08-27 | FR-035 | feature request | Station labels can be dragged in the autonomy editor, by the label or by its square | fixed unvalidated | - |
| 2026-08-27 | OB-120 | bug | Test a path drew routes into stations that refuse arrivals from that side | fixed unvalidated | - |
| 2026-08-27 | OB-119 | bug | Escape did not put the autonomy editor's tools down | fixed unvalidated | - |
| 2026-08-27 | FR-032 | feature request | Crop or pan an icon again without reselecting the source | - | [MT-203](tests.md#mt-203) |
| 2026-08-27 | OB-118 | bug | An empty station's caption sat at the left of its square rather than over the track | fixed unvalidated | - |
| 2026-08-27 | OB-117 | bug | The running-train tile painted out the station caption underneath it | fixed unvalidated | - |
| 2026-08-27 | OB-116 | bug | The left and right facing arrows were half the height of the up and down ones | fixed unvalidated | - |
| 2026-08-26 | LR-6 | bug | A comment describing behaviour that changed under it, and the dead half of the condition beside it | fixed validated | - |
| 2026-08-26 | LR-5 | bug | The first-dispatch signal sweep asked the counter again instead of remembering it had been first | fixed unvalidated | - |
| 2026-08-26 | LR-4 | bug | Two trains on one platform still drew brackets inside the pill | - | [MT-197](tests.md#mt-197) |
| 2026-08-26 | LR-3 | bug | A placeholder station and a named one came out the same colour on the pill, under a comment saying they did not | - | [MT-197](tests.md#mt-197) |
| 2026-08-26 | LR-2 | bug | The route conflict question named the wrong reason, and the refusal was logged before it was one | - | [MT-202](tests.md#mt-202) |
| 2026-08-26 | LR-1 | bug | Closing TrainControl with the track editor open and answering Discard half-discarded the edit | - | [MT-201](tests.md#mt-201) |
| 2026-08-26 | OB-114 | bug | The capture test waits for the railway to move rather than for a fixed number of seconds | fixed unvalidated | - |
| 2026-08-26 | OB-110 | bug | Already fixed when it was filed - the build predates it; re-test under MT-179 | - | [MT-179](tests.md#mt-179) |
| 2026-08-26 | OB-053 | bug | Not a defect as filed: one label per cell, built once. The REBUILDING underneath it is real and is a separate, smaller question | fixed validated | - |
| 2026-08-26 | FR-031 | feature request | Station labels can be light grey, and it is remembered | - | [MT-200](tests.md#mt-200) |
| 2026-08-26 | FR-030 | feature request | No captions in the track editor; station names in the autonomy one, with a switch | - | [MT-199](tests.md#mt-199) |
| 2026-08-26 | FR-029 | feature request | Seven flat sidebar icons in the theme blue | - | [MT-198](tests.md#mt-198) |
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
