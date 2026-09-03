# Regression review: what a 2.7.4c user loses on the way to 3.0.0

**Status:** open

**Prefix for citing these findings elsewhere:** `RGN`

**Reviewed:** `e4c94ac9`, tagged `v3_0_0_rc4`, on branch `autonomy-diagram-r0`, on 2026-08-31.
The baseline throughout is the release tag **`v2_7_4c`** - 948 commits and 125 changed source files
back. Nothing was compiled and nothing was run; every finding below was reached by reading both
revisions of the same code path and, where the claim depends on real data, by reading
`cs2_sample_layout/` (read only, never written).

**The question this pass asked** is not whether the new code is good. It is: what could a 2.7.4c user
do that a 3.0.0 user cannot, or that now does something different without being told? So the
severities below are about the *delta*, not about the new subsystem in isolation. Two of the three
A/B findings are things that only bite somebody arriving from 2.7.4c, and are therefore invisible to
anybody testing 3.0.0 on its own terms - which is most of the testing this cycle has had.

**Already known, deliberately not re-reported.** `RC-B3`, `RC-B4` and `RC-B5` were read first. So was
the RC round's list of *"regressions against 2.7.4c that are decisions, not defects"* - duplicate
station names, the deleted `GraphEdgeEdit` Test button, `Readme.md:441-442` still promising the old
text editor, `LayoutEditor.editTextWithDropdown` having no callers, the route editor forgetting its
size, commands only appendable at the end, station maximum train length, undrawn excluded
locomotives, Ctrl+E / Ctrl+U, no bulk "clear locomotives" - and the RC round's carried-forward worry
that `RouteEditorFrame`'s new save-time refusals could lock somebody out of an existing route.
`RGN-C3` is one specific, reachable instance of that last worry and is written up because the RC note
did not name it; the rest are not repeated here. The two removals Adam agreed to - "paste entire
row"/"paste entire column" and the double-curve warning - are not findings.

**Method, so the gaps are visible.** Four sweeps: (1) the preference defaults at both revisions,
diffed mechanically; (2) every method declaration in every modified source file, diffed for
disappearances; (3) every message-bundle key present at `v2_7_4c` and absent at HEAD, as a proxy for
UI that no longer exists; (4) feature-by-feature reading of the paths a user actually walks - load
autonomy, fire a route, back up, save a diagram page, restore the window layout. Sweeps 2 and 3 found
almost nothing (see `RGN-D7`); everything below came from sweeps 1 and 4.

| | |
|---|---|
| **A1** | open - legacy `autonomy.json` import silently drops every run-wide setting, the whole timetable, and all 90 authored edge lengths |
| **A2** | open - the Auto tab is disabled for every user whose autonomy comes from `autonomy.json`, so nothing on it can be reached after upgrading |
| **B1** | open - `Point:` station captions are stripped out of the user's own `.cs2` page files, one way, with no changelog line |
| **B2** | open - an s88-fired route with a conflict does not "stop": it drops its accessories and runs everything else |
| **B3** | open - the v2.7.4 changelog section was rewritten so that two 3.0.0 changes read as things the user already has |
| **C1** | open - auto-save on exit is forced on and its checkbox hidden, so `autonomy.json` is rewritten for somebody who turned that off |
| **C2** | open - CS2 route delays are parsed differently: `sekunde=2.3` was 2000 ms and is now 2300 ms |
| **C3** | open - a locomotive whose name contains a bracket can no longer be used in any route command, and neither door that creates a locomotive knows the rule |
| **C4** | open - `UIState.data` written by 3.0.0 with a non-default page count loses its page names when read back by 2.7.4c |
| **D1-D8** | checks that came back clean |

---

## A - high

### A1 - importing a 2.7.4c `autonomy.json` silently drops every run-wide setting, the timetable, and every edge length

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading, and measured against Adam's own file |

`AutonomySession.importLegacy` reads exactly one key out of the file it is given:

    src/org/traincontrol/automationui/AutonomySession.java:530
        org.json.JSONArray points = legacy.optJSONArray("points");

Everything else in the file is never looked at. Per point it honours `name`, `s88`, `loc`, `home`,
`terminus`, `reversing`, `station`, `maxTrainLength` and the four `CARRIED_SETTINGS` at
`AutonomySession.java:493` (`priority`, `speedMultiplier`, `excludedLocs`, `active`). There is no read
of the top-level object and no read of `edges`.

`Layout.toJSON` at `v2_7_4c` wrote twelve run-wide settings, a timetable, and a `length` on every
edge, and `parseAuto` read all of them back. Measured against
`cs2_sample_layout/config/autonomy_legacy/autonomy.json` - Adam's own graph, 62 points, 90 edges:

| in the file | after import | why |
|---|---|---|
| `minDelay` 3 | 1 | `AutonomyBuilder.java:40-46`, the `Globals` constructor |
| `maxDelay` 13 | 5 | same |
| `maxLocInactiveSeconds` 120 | 0 | `Layout.java:606` field default; `parseAuto` only reads the key `if (o.has(...))` |
| `activateRoutes` true | false | `Layout.java:648` field default, same reason |
| `timetable` - 36 legs | gone | never read |
| `length` on 90 of 90 edges | derived from tile lengths, 0 where none is authored | never read |
| `defaultLocSpeed` 35, `preArrivalSpeedReduction` 0.5, `atomicRoutes` true, `turnOnFunctionsOnDeparture` true, `turnOffFunctionsOnArrival` false, `maxLatency` 0, `maxActiveTrains` 0 | unchanged | the builder default happens to equal the file's value |

So on this file four settings change, a 36-leg timetable disappears, and thirty measured edge lengths
become zero - which is exactly the data that stops an over-long train being routed onto a short
section.

**Nothing says so.** The summary dialog is
`autosetup.ui.infoLegacyImported` - *"Named {0} squares, placed {1} locomotives, marked {2} as turning
trains round and carried {3} other settings..."* - and `{3}` is `result.settings`, which counts only
the four per-point keys above. The one sentence in the dialog that talks about settings reports a
number that has nothing to do with the settings that were lost.

**The mechanism to carry them already exists and is used on the other path.**
`AutonomySession.captureFromLayout` at `:2578-2586` does precisely this:

    for (String key : root.keySet())
    {
        if (!"points".equals(key) && !"edges".equals(key)) globals.put(key, root.get(key));
    }
    configuration.put("globals", globals);

and `AutonomyCompanionStore` already stores and prunes `globals.timetable` (`:1444-1469`).
`importLegacy` simply does not do it.

**Severity.** A rather than B because it is silent loss of authored data on the one path every
existing user has to walk, and because the timetable and the edge lengths cannot be reconstructed
from the diagram - they are the part of a 2.7.4c setup that took the longest to build.

**How to confirm.** Open a session over a copy of `cs2_sample_layout` (a copy - not the folder
itself), call `importLegacy(new JSONObject(<autonomy_legacy/autonomy.json>), null)`, then print
`store.getConfiguration(store.getActiveConfiguration()).optJSONObject("globals")`. Expect null or
empty; compare against `new JSONObject(file).keySet()` minus `points` and `edges`, which is twelve
keys plus `timetable`. For the lengths, build with `session.builder(session.globals()).build()` and
print the emitted `length` for the edge pair matching legacy edge
`{"start":"TopMainR2Inter","end":"TopMainR2Pre"}` against that edge's `length` in the file.

### A2 - the Auto tab is disabled for everybody whose autonomy comes from `autonomy.json`

**FIXED 2026-09-03, and it reproduces - Adam's own doubt was right to be a doubt, and wrong.**

He asked for a test: *"Could not run this.  make a test case for this.  in my testing, it loaded OK."*
`testTheAutoTabIsReachableWithALegacyAutonomyJson` is that test, and building the state he described -
a LOCAL layout with an `autonomy.json` and **no diagram configuration at all** - greys the Auto tab.
The reason his own run looked fine is that the fixture, and his railway, both carry a diagram
configuration: asking for a session makes one active, and `loaded` is then true.

`loaded` was `session == null || activeDiagramConfiguration != null`.  Both halves are right and neither
covers an upgrading user: the session exists because the layout is local, and no configuration is
active because there are none to activate.

The third arm is the one that says what the question is about - a setup with NO configurations is the
JSON path, and it is loaded by definition, which is what `valid` above already establishes.  The state
this gate exists to catch, a valid graph left over from a blank default or another layout, has
configurations to choose from and is unaffected.

MT-244 can be run against this; the automated half is done.

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading; the visible symptom needs execution |

`refreshAutonomyTabState` decides whether the "Auto" tab is usable:

    src/org/traincontrol/gui/TrainControlUI.java:3658
        boolean loaded = getAutonomySession() == null || this.activeDiagramConfiguration != null;
    src/org/traincontrol/gui/TrainControlUI.java:3677
        setAutoTabEnabled(valid && loaded && isLocalLayout());

and `setAutoTabEnabled` greys `KeyboardTab` index 2 and steps off it if it is showing. At `v2_7_4c`,
`KeyboardTab.setEnabledAt` is only ever called for index 1; the Auto tab was never disabled at all.

`activeDiagramConfiguration` is set in exactly one place - the diagram-configuration load path
(`TrainControlUI.java:3535`) - and its own accessor says so: *"or null when it came from the legacy
JSON path"* (`:3552-3553`). `getAutonomySession()` returns non-null for **any** local layout folder:
`AutonomyCompanionStore.isUsable()` is `layoutFolder != null && layoutFolder.isDirectory()`
(`AutonomyCompanionStore.java:727`), with no requirement that a setup exist.

Compose the three clauses:

- **Local layout folder + `autonomy.json`.** `session != null`, `activeDiagramConfiguration == null`
  -> `loaded` false -> tab disabled.
- **Central Station layout + `autonomy.json`.** `isLocalLayout()` false -> tab disabled. (This half
  is OB-104 and is deliberate; it is still a 2.7.4c capability that is gone, and it has no changelog
  line.)
- The only combination that leaves the tab open without a diagram configuration is
  `LAYOUT_OVERRIDE_PATH_PREF` naming a folder that is not a directory - a stale path, i.e. a broken
  installation.

So there is no configuration in which the legacy JSON path can enable the Auto tab. The comment
directly above line 3677 asserts the opposite - *"The JSON window is unaffected on a LOCAL layout,
which is where anybody using it is"* - and it is the local case that the first clause catches.

**What is on that tab and has no other home:** the locomotive run list and its per-locomotive speeds
(`locCommandTab`), the timetable (`timetablePanel`), and the autonomy settings panel
(`autoSettingsPanel`) - minimum and maximum delay, default speed, pre-arrival reduction, atomic
routes, maximum active trains, the routing rule. Start, Graceful Stop and Return Home do survive on
the diagram's right-click menu (`LayoutRightclickAutonomyMenu.java:159-240`, which asks only
`hasAutoLayout()`), so this is not a total lock-out - but a 2.7.4c user who upgrades can start their
railway and cannot change a single autonomy setting or run a timetable.

It compounds with the surrounding UI rather than being softened by it. `mountAutonomyControls`
(`:3344`, the `session != null` branch) removes the JSON tab and hides `validateButton`, `loadJSONButton`, `exportJSON`,
`loadDefaultBlankGraph` and `jsonDocumentationButton` whenever `session != null`, so on a local layout
there is no JSON window to fall back to either. And `refreshAutonomyPrompt` (`:5942`) only shows
the diagram banner when `!session.getStore().getConfigurationNames().isEmpty()`, which is false for a
fresh upgrade - so nothing offers the import that would fix it, and nothing explains the greyed tab.

**Severity.** A. It is wrong behaviour on the layout for every legacy user, it is silent, and the
first thing it hides is the settings panel - so `RGN-A1`'s changed pacing cannot even be corrected by
hand.

**How to confirm.** Point `LAYOUT_OVERRIDE_PATH_PREF` at a *copy* of `cs2_sample_layout` that has no
`config/autonomy/setup.json`, put `autonomy_legacy/autonomy.json` where the application auto-loads it,
start with Load Autonomy ticked, and print `KeyboardTab.isEnabledAt(2)` together with
`getActiveDiagramConfiguration()` and `getAutonomySession() != null`. Expect `false`, `null`, `true`.
The cheap unit-level version: assert that `valid && loaded && isLocalLayout()` cannot be true when
`getAutonomySession() != null` and `activeDiagramConfiguration == null`.

---

## B - medium

### B1 - `Point:` captions are deleted out of the user's own `.cs2` files, and it has already happened

| | |
|---|---|
| **Disposition** | fixed - it is announced now, in the changelog and in the log |
| **Confidence** | confirmed by reading, and confirmed against the shipped layout |

`AutonomySession.open()` calls `migrateStationLabels()` unconditionally (`:139`). That method
(`:1631`) finds every diagram component whose label starts with `Point:`, records it as a caption in
the companion store, and then erases it from the page:

    src/org/traincontrol/automationui/AutonomySession.java:1714
        component.setLabel("");

followed by `saveChanges` on each page it touched. At `v2_7_4c` that label *was* the station name -
`LayoutGrid.LAYOUT_STATION_PREFIX = "Point:"` - and the `.cs2` file was where it was read from.

This is not hypothetical. In `cs2_sample_layout/config/gleisbilder/`:

| file | `Point:` labels |
|---|---|
| `1 - Main.cs2.bak` | 13 |
| `2 - Bottom.cs2.bak` | 2 |
| `1 - Main.cs2` | 0 |
| `2 - Bottom.cs2` | 0 |

The migration has already run on the real layout and the `.bak` beside each page is the only remaining
copy of those fifteen names. Anybody who upgrades, imports, and then wants to go back to 2.7.4c finds
their station captions gone from the track diagram, with no writer of `Point:` labels anywhere at HEAD
to put them back.

It is guarded in one useful way: the label is only erased when `tileNamed(...)` resolves it, which
needs a setup that already knows a station of that name. A bare upgrade with no import is therefore
safe; it is the import in `RGN-A1` that arms this.

A one-way upgrade is not automatically a defect, and the code argues its case at length. But it edits
files the user owns, the changelog says nothing about it, and there is no "and your diagram files will
be rewritten" anywhere the user will see. That is at minimum a changelog line and arguably a prompt.

**How to confirm.** Already confirmed by the fixture above. To watch it happen: copy a layout folder,
put a `Point:X` label on a tile, create a setup with a station named `X`, open a session over it, and
diff the page file before and after `open()`.

**Disposition: fixed as the finding framed it - "at minimum a changelog line" - and with the log line
as well. What it does is unchanged.**

Re-reading it before touching it, the migration is careful in every way that matters: it only takes a
label naming a station the setup already knows, it leaves an unrecognised one exactly where it is, it
rewrites only pages it actually changed, and `LayoutDiagram.saveChanges:470` keeps a `.cs2.bak` the
**first** time a page is rewritten - deliberately the first, so the copy is the state before this build
touched anything. So the fifteen names in the fixture are not lost; they are in the `.bak` the finding
itself listed.

What was actually wrong is narrower and is what the finding's last paragraph says: **it edits files the
user owns and says nothing.** Failures were reported (`TrainControlUI.java:2650`) and successes were
not - which is the wrong way round, because a failure means it runs again next time and a success is
the one-way half.

- `AutonomySession` now records `migratedPages` and `migratedCaptions`, cleared at each `open()` so
  they describe that open and not the history.
- `TrainControlUI` logs them beside the existing failure report: which pages were rewritten, how many
  names were taken, and that a `.cs2.bak` is sitting beside each one. The log rather than a dialog -
  it happens once, on the first start after an upgrade, and it is a record of something already done
  rather than a question.
- `Readme.md` gains a bullet under 3.0.0 saying the same thing in the user's words, including that a
  name matching no station is left alone.

`testTheSessionSaysWhichPagesTheMigrationRewrote` covers both counters and, in its second half, that a
**second** open reports nothing - a cumulative counter would put the notice in front of a user whose
files nothing had touched, at every start-up for ever. MUTATION: dropping `migratedPages.add` fails it
(109 tests, 1 failure; restored, 109 green).

**Not made into a prompt.** The finding says "arguably a prompt", and the argument against is that a
prompt implies a choice: there is nothing to decide - the labels have to move for the new setup to own
them, and refusing would leave the diagram showing every station name twice. A modal at first start,
about work already done, is a dialog whose only button is OK.

### B2 - an s88-fired route with a conflict does not stop; it drops its accessories and runs the rest

| | |
|---|---|
| **Disposition** | closed - descriptions fixed; behaviour ruled unchanged by Adam, 2026-09-03 |
| **Confidence** | confirmed by reading |

The changelog says: *"a route fired by a sensor stops instead, because there is nobody there to ask."*
The code says:

    src/org/traincontrol/marklin/MarklinRoute.java:625
        boolean skipAccessories = auto && conflict != null;

and then the command loop runs to the end. `skipAccessories` is consulted only inside
`if (rc.isAccessory())`. Every other branch - `isStop()`, locomotive speed, locomotive direction,
locomotive function, and a chained `Route` command - executes exactly as before. At `v2_7_4c` there
was no conflict concept at all and every command was sent.

So an s88-fired route with a conflict, during autonomy, will still stop a locomotive, still change its
speed and functions, and still fire whatever route it chains to - having thrown none of its ironwork.
The comment forty lines above line 625 states the rule this is meant to implement: *"REFUSED rather
than confirmed, and refused WHOLE ... a route half executed leaves the layout in a state nobody
chose."* Refusing only the accessories is a half-executed route by that definition.

Two things make this worth an entry rather than a shrug. First, the changelog word is "stops", and a
user reading it will assume nothing happened. Second, `MarklinRoute.java:844` now passes `auto` down
to a chained route (`r.execRoute(auto, recursionLimit - 1, false)`; `v2_7_4c` passed `false`), so the
chained route inherits the same accessory-dropping - it is not a fresh, askable decision.

I have not decided which behaviour is right; the safety argument for dropping the accessories is
sound. What is wrong is that three descriptions of it - the changelog, the rule at `:585`, and the
code - disagree.

**How to confirm.** Start autonomy, lock a path over turnout N, then let an s88-triggered route that
both sets turnout N and issues a `locspeed` fire. Print the accessory's state and the locomotive's
speed before and after, and grep the log for `route.refusedAccessoryOnActivePath`. Expect the
accessory unchanged, the speed changed, and one log line.

**Disposition: the disagreement is closed. The behaviour is not changed, and should not be by me.**

The finding's own sentence is the one to answer: *"I have not decided which behaviour is right; the
safety argument for dropping the accessories is sound. What is wrong is that three descriptions of it -
the changelog, the rule at `:585`, and the code - disagree."* All three now say the same thing.

- **The two log messages**, which were the worst of it, were fixed on 2026-09-03 (`8a8ce798`): one said
  *"nothing further in the route was switched either"*, which reads as the route having stopped, and
  the other said *"the rest of the route ran"*, which hides the ironwork being skipped. An operator
  reading either could not tell what his railway had just done. Both say the true thing now, in all
  eight bundles.
- **The changelog** was the half left, and it was the half a user actually reads. It said *"a route
  fired by a sensor stops instead"*; it now says it *"sets none of its switches and signals instead...
  the rest of it, such as speeds and functions, still runs."*
- **The code's own comment** at `MarklinRoute.java:618-626` already describes the s88 door exactly -
  every accessory dropped as a group, nobody there to ask, and why per-command refusal would be worse
  there. It is the rule at `:585` that generalised, and the comment beneath it is the specific case.

**Why the behaviour is left alone.** Two reasons, and neither is caution for its own sake. Adam's
recorded ruling is that a conflicting route must stay executable *"in case of a transient accessory
failure"*; and the alternative - refusing the route whole - discards the `isStop()` commands with
everything else, which is to say it throws away an emergency stop because a turnout was busy. That is
a worse failure than the one being fixed, and it is the shape of change that has to be his.

**So the question goes in the report**, phrased as the code phrases it: should a route fired by a
sensor, which cannot set its ironwork, still drive trains over track it did not switch?

**Answered, 2026-09-03: leave it as it is.** The route goes on running its speeds, functions and any
route it chains to, having set none of its ironwork. His earlier ruling - that a conflicting route
stays executable *"in case of a transient accessory failure"* - governs, and refusing whole would
discard the route's emergency stop with everything else.

So the finding closes on the three descriptions agreeing, which they now do. **CLOSED.**

### B3 - the v2.7.4 changelog section was rewritten, so two 3.0.0 changes read as things the user already has

| | |
|---|---|
| **Disposition** | fixed - moved to v2.8.0, not v3.0.0; see below |
| **Confidence** | confirmed by reading |

`git show v2_7_4c:Readme.md` line 331:

    * v2.7.4 [7/24/2026]
        - Backups are now saved to the tc_backup folder in the current directory
        - Bug fixes
            - Minor UI performance improvements
            - Fixed rare race conditions in autonomy code

`Readme.md:537` at HEAD:

    * v2.7.4 [7/25/2026]
        - Autonomy
            - Added Path Integrity Validation features. If a switch/signal configuration cannot be
              confirmed by the Central Station, the locomotive will not run. ...
        - UI
            - Backups are now saved to the tc_backup folder in the current directory
        - Bug fixes
            - Fixed bug where only one locomotive could be triggered from the track diagram in
              semi-autonomous mode
            ...

The date changed and two bullets were added retroactively. `git grep PATH_INTEGRITY_VALIDATION
v2_7_4c -- src/` returns nothing: the feature is not in the tag. `git log -S "Added Path Integrity
Validation features" -- Readme.md` gives one commit, `34f75670 "Path validation refined - testing"`,
which is on this development line.

The behaviour that is actually new is substantial and is on by default:

    src/org/traincontrol/automation/Layout.java:54   public static int PATH_VALIDATION_MS = 1000;
    src/org/traincontrol/automation/Layout.java:60   public static boolean PATH_INTEGRITY_VALIDATION = true;
    src/org/traincontrol/gui/TrainControlUI.java:883 prefs.getBoolean(ENHANCED_PATH_VALIDATION, true)

Before a locomotive departs, `validatePathActuation` (`Layout.java:2859`) waits up to
`PATH_VALIDATION_MS * (accessories + 1)` milliseconds for the Central Station to echo every accessory
on the path, and if any does not confirm, the locomotive does not depart and its locks are released
(`handleMisconfiguredPath`). A 2.7.4c user gets that on first run of 3.0.0 without a word about it
under v3.0.0, because the only bullet describing it is filed under the release they are coming from.

The version numbering the task flagged is real and this is the one place it does harm: the changelog
also presents `v2.8.0 [8/2/2026]` and `v2.8.1 [8/17/2026]` as shipped releases and neither exists as a
tag, so everything under those two headings is also new to a 2.7.4c user. That part is harmless -
the bullets are all there and a reader gets the truth by reading further up. Editing the v2.7.4
section is not harmless, because it is the one section a 2.7.4c user will *skip*.

**How to confirm.** The three git commands above; no execution needed.

**Disposition: fixed, and both bullets belong to v2.8.0 rather than to 3.0.0.**

The finding is right that they did not ship in v2.7.4, and the tag history says which release they
did ship in. `v2_7_4c` - the last tag on that line - is `1ef11b62`, **2026-07-25 01:27**.
`PATH_INTEGRITY_VALIDATION` first appears in `20fa6d05` "Path validation initial", **2026-07-25
15:33**, and `git merge-base --is-ancestor 20fa6d05 v2_7_4c` says no. The semi-autonomous trigger fix
is `b51ff24a`, the same day. Neither is in `v2_7_4`, `v2_7_4b` or `v2_7_4c` (`RAW_VERSION` is
`"2.7.4"` in all three and the constant is in none of them). The next release heading after those
commits is `v2.8.0 [8/2/2026]`.

So they are 2.8.0 changes, and 2.8.0 already carries the *refinement*: "Path integrity validation now
waits for the Central Station to confirm every switch and signal on the path" sits under its Autonomy
Bug Fixes, which only makes sense with the introduction above it. Both bullets moved there - the
feature into `- Autonomy`, the fix into `- Autonomy Bug Fixes` - and the v2.7.4 section is now
character-for-character what `v2_7_4c` shipped, minus the heading restructure Adam did later.

**This still fixes the harm the finding names.** What made it worse than the untagged-2.8.x point was
that v2.7.4 is the one section a 2.7.4c user skips; 2.8.0 is one they read. Filing it under 3.0.0
instead would also have been read - but it would have been wrong, and a user on 2.8.1 would be told a
feature they have had since August is new.

**Answered, 2026-09-03: both shipped; they are simply untagged.** So the two bullets moved into
`v2.8.0` are where they belong, and the three headings stay as three. Nothing further to do here.

Not covered by a test. A rule that says "no bullet under a released version may describe code absent
from that tag" is writable, but two of the three headings involved have no tag to check against, so it
would assert over the part that was never in doubt.

---

## C - low

### C1 - auto-save on exit is forced on, and its checkbox hidden, so `autonomy.json` is rewritten for somebody who turned it off

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

    src/org/traincontrol/gui/TrainControlUI.java:870-871
        this.autosave.setSelected(true);
        this.autosave.setVisible(false);

against `prefs.getBoolean(AUTOSAVE_SETTING_PREF, true)` at `v2_7_4c`. The comment says the stored
preference is *"deliberately ignored rather than read"*, on the reasoning that turning it off only ever
meant losing work.

That reasoning covers what the flag saves; it does not cover what the flag *prevents*. On the legacy
path the same flag gates rewriting `autonomy.json` from the in-memory graph
(`TrainControlUI.java:2232`, `activeDiagramConfiguration == null && this.autosave.isSelected()`), and
`:2265` then writes the file. A user who unticked the box specifically to keep a hand-authored,
hand-ordered `autonomy.json` from being machine-rewritten now has it rewritten on every exit, with the
control removed. `AUTOSAVE_SETTING_PREF` and `autosaveActionPerformed` are both still present
(`:229`, `:20736`), so the preference exists and is simply unreachable.

Narrow - it needs the legacy path, and 2.7.4c overwrote the file too whenever the box was ticked, which
it was by default. Worth a C and a changelog line.

**How to confirm.** Set `AutoSave` false in the preference store, run with a legacy `autonomy.json`,
exit, and compare the file's bytes before and after.

### C2 - CS2 route delays now mean something different

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

    src/org/traincontrol/marklin/file/CS2File.java:827   (HEAD)
        delay = Float.valueOf(Float.parseFloat(kv[1].trim()) * 1000).intValue();
    v2_7_4c:src/org/traincontrol/marklin/file/CS2File.java:737
        delay = Float.valueOf(kv[1]).intValue() * 1000;

Truncate-then-scale became scale-then-truncate. The same `fahrstrassen.cs2` now imports with different
pauses: `sekunde=2.3` was 2000 ms and is 2300 ms, `sekunde=3.2` was 3000 ms and is 3200 ms, and
anything below one second was previously 0 - which the `delay > 0` guard then skipped entirely - and
is now honoured. `Oles kreds/config/fahrstrassen.cs2` and `test/fahrstrassen.cs2` both carry `2.3` and
`3.2`, so this is a real change to real routes.

It is a fix, and the right one. It has no changelog line, and dropping sub-second pauses is a defect a
user could have hit, which is the README's own bar for an entry.

**How to confirm.** `new CS2File(...).parseRoutes()` over `Oles kreds/config/fahrstrassen.cs2` at both
revisions; print `rc.getDelay()` for every command.

### C3 - a locomotive whose name contains a bracket can no longer be used in any route command

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading; whether any real locomotive is affected needs Adam's own database |

`RouteEditorFrame.java:2227` refuses to save a route any of whose commands names a locomotive failing
`RouteCommand.isNameUsable` (`RouteCommand.java:596`), which rejects `,`, `(` and `)`.

At `v2_7_4c` the only check was in the old editor and it was **comma only, and only for conditions**:
`RouteEditor.java:1560`, `if (((String) locNameList.getSelectedItem()).contains(","))`. A route
command such as `locspeed,SBB 460 (2),40` saved and ran perfectly well - the line format splits on
commas, and it is the *condition* parser that brackets break.

So a 2.7.4c route naming a bracketed locomotive can no longer be saved at all, even to change the
route's name - and the RC round's carried-forward worry about `RouteEditorFrame`'s new refusals said
the only reachable case was a deleted chained route. This is a second one.

The reason it stays a C rather than climbing is that I could not show such a locomotive exists: no
name in the sample fixtures contains a bracket. But the rule is enforced at only two of four doors.
`isNameUsable` is asked by the route editor (`:2227`) and by the rename dialogs
(`TrainControlUI.java:16486`, `:22172`), and by nothing in `src/org/traincontrol/marklin/` or in
`AddLocomotive` - so a bracketed name can still arrive from a Central Station sync or be typed into
Add Locomotive, and then the route editor refuses every route that mentions it. The comment on
`isNameUsable` says *"three separate doors have to agree about it"*; there are four.

**How to confirm.** Add a locomotive named `Test (2)` through Add Locomotive - if that is accepted,
the gap is real - then put a speed command for it in a route and press Save.

### C4 - a `UIState.data` written by 3.0.0 with a non-default page count loses its page names under 2.7.4c

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

`v2_7_4c` restores the trailing page-names map only when `saveStates.size() > NUM_LOC_MAPPINGS`, with
`NUM_LOC_MAPPINGS` fixed at 10 (`v2_7_4c:TrainControlUI.java:1645`, `:259`). HEAD's page count is a
preference between 2 and 50 (`TrainControlUI.java:340`, `:349`, `:364`) and the file still has exactly
one trailing entry, so a 3.0.0 user who deletes a page writes nine pages plus names - ten entries -
and 2.7.4c evaluates `10 > 10` as false, restores no page names, no active page and no active button,
and serialises the empty map back over them on exit.

Downgrade-only, and clean in both directions at the default ten pages, which is the only count a
2.7.4c installation can have. HEAD reading a 2.7.4c file is fine: `!saveStates.isEmpty()`
(`TrainControlUI.java:6572`) replaced the count comparison precisely because it was fragile.

**How to confirm.** Save state at HEAD with `numLocMappings = 9`, then run `restoreState()` under
2.7.4c and print `pageNames.size()`. Expect 0.

---

## D - not defects

### D1 - the same-address accessory conflict error, removed on purpose

`acc.commandConflictSameAddressMustRename` is one of 21 bundle keys present at `v2_7_4c` and absent at
HEAD, and unlike the other twenty it is not graph UI. It was thrown by
`Edge.validateConfigCommand` when a command named a signal at an address already holding a switch. The
replacement comment at `Edge.java:145-149` is right: `getAccessoryByName` already falls back to the
address and protocol encoded in the name, so the two resolve to each other - they are one decoder and
the type only selects how it is drawn. Not a regression.

### D2 - the fifty-page cap does not truncate an existing installation

The changelog claims *"an installation that already holds more than fifty still loads all of them"*.
It holds. `TrainControlUI.java:611` reads the count with `Math.max(MIN_LOC_MAPPINGS, ...)` and no upper
clamp, the growth loop at `:6515` is explicitly marked *"NOT SUBJECT TO MAX_LOC_MAPPINGS"*, and the
only place `MAX_LOC_MAPPINGS` is enforced is `addLocMappingPage` (`:1352`).

### D3 - the locomotive database round-trips both ways

`MarklinSimpleComponent.java` is byte-identical between the two revisions - same `serialVersionUID`,
same fields - and `git diff v2_7_4c..HEAD` on it is empty. `CustomObjectInputStream`
(`MarklinControlStation.java:1591`) is unchanged, including its 2.3.2 and 2.7.0 class-rename mappings.
`RouteCommand`'s UID and the four `Node*` classes' fields are unchanged. None of the deleted classes
(`GraphViewer`, `GraphEdgeEdit`, `GraphLocExclude`, `RouteEditor`) was `Serializable`, so nothing in
either file can reference them.

### D4 - `routes.json` import and export are unchanged

`exportRoutes`, `parseRoutesFromJson`, `importRoutes`, `RouteCommand.toJSON` and `fromJSON` are
untouched by the diff. A routes file written by 2.7.4c reads at HEAD and back.

### D5 - `.cs2` page files: HEAD is strictly less lossy, apart from `RGN-B1`

`getComponentType` is character-identical at both revisions. HEAD additionally preserves unmodelled
element keys, unmodelled elements, and the non-element blocks that 2.7.4c replaced with a hardcoded
`version/.major=1`, and it writes back the file's own `.typ` word instead of collapsing fifteen signal
variants to `signal`. Everything it emits parses under 2.7.4c's `parseFile`. The page-content upgrade
is not one-way. (Page *ids* are a separate matter: HEAD preserves them and 2.7.4c renumbers by list
position, so letting 2.7.4c rewrite `gleisbild.cs2` reattaches an autonomy setup to the wrong pages -
which is what MT-135 already documents, so it is not a new finding.)

### D6 - route sort order, ids, enable and disable

`refreshRouteList`, `getRouteList`, `ROUTE_STARTING_ID` and the id allocation in `newRoute` are
character-for-character unchanged. The only new gate, `isLocalRouteId`, decides whether to skip a CS2
sync after saving and never changes an id. Enable/disable is strictly more permissive at HEAD: a route
enabled with no sensor can now be turned off, where 2.7.4c showed the menu item and errored on the
click.

### D7 - the two mechanical sweeps came back almost empty

Every method declaration in every modified file, diffed: the only disappearances outside the deleted
graph UI were `Layout.getPossibleEdges`, `TrainControlUI.showTab`/`addEdge`/`updatePoint`/
`updateEdgeLength`/`highlightLockedEdges`/`ensureGraphUIVisible`/`renderAutoLayoutGraph`,
`ImageUtil.rotateImage`/`textToImage` - all graph-window machinery - and
`RightClickFunctionMenu.mousePressed`/`mouseReleased`, which `UXR-C21` removed as never-registered dead
listeners. The function button's speed presets (Alt-V, Alt-U) survive at
`RightClickFunctionMenu.java:107-117`. Every `KeyStroke.getKeyStroke` accelerator in `TrainControlUI` is
identical at both revisions, and the only components missing from `TrainControlUI.form` are `jMenu1`
and `reopenGraphButton`.

### D8 - preference defaults, diffed mechanically

Of the twenty-odd `prefs.get*` calls, only one existing default flipped: `AUTO_LOAD_AUTONOMY` from
`false` to `true` (`TrainControlUI.java:882`), which the changelog states and the comment at `:875`
argues for. `ONTOP_SETTING_DEFAULT` is still true. `HIDE_INACTIVE_PREF`, `HIDE_REVERSING_PREF` and
`SHOW_STATION_LENGTH` are gone with the graph window. The new defaults are
`SHOW_INACTIVE_LABELS_DEFAULT` true, `DIAGRAM_RESTRICTION_ARROWS` true, `STATION_LABELS_GREY` false,
`CROP_LOC_ICON_PREF` false, `LAST_EDITOR_AUTONOMY_PREF` false - all of them drawing or convenience, all
in the changelog. `ENHANCED_PATH_VALIDATION` true is the one that changes what the railway does, and it
is `RGN-B3`.

---

## What this pass did not look at

- **Autonomy runtime parity.** Whether a train picks the same route and holds the same locks as it
  did at 2.7.4c is the largest regression surface here and I did not touch it. `tools/parity/` exists
  for exactly that and its README still records an open loss; the RC round left the same gap for the
  same reason (it needs a jar and a running layout). Everything I say about autonomy above is about
  *reaching* it, not about what it does once running.
- **`RGN-A1` and `RGN-A2` interact and I only traced them separately.** Importing the legacy file
  fixes `A2` (it creates a configuration, so `activeDiagramConfiguration` becomes non-null) at the
  price of `A1`. Whether the import leaves a *working* setup on Adam's real graph - 62 points against
  the diagram's sensors, with the ambiguous-s88 refusal in the middle of it - I did not check, and it
  is the single most valuable thing somebody with a machine could do next.
- **The Central Station sync, the backup archive, and the diagram editor's multi-select** were read
  only far enough to answer format questions. The layout editor had its own round (`LE`) and the
  backup path had `FR-015`/`FR-019`/`FR-020`/`FR-021`; I did not re-derive either. One thing I noticed
  and did not chase: `backupDataMenuItemActionPerformed` now calls `saveState(false, false)` and
  `model.saveState(false)`, which write the **live** files, where 2.7.4c's backup wrote only into the
  backup folder. The comment explains why and the `uiStateLoadFailed` guard covers the worst case, so
  I left it - but "Backup" committing current state to disk is a change of meaning.
- **Locomotive import, icon cropping, the splash screen, translations** - not looked at at all.
- **Nothing was executed**, so every "confirmed by reading" above is a claim about what the code says,
  not about what the application does. `A2` in particular is the composition of three predicates in
  two methods; I am confident about each of them and I have not seen the tab greyed out.
- **The bundle-key sweep is a weak proxy for lost UI.** It finds a removed feature only if that
  feature had a string of its own that nothing else reuses. A menu item that was deleted while its
  label stayed in use elsewhere is invisible to it, and I have no second method that would catch one.
