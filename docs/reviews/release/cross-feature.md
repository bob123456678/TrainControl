# Cross-feature review of TrainControl {RELEASE}: follow the data and the windows, not the files

Area reviews check the code in their scope. None follows one piece of data, or one window, across features, and that
is where the owner's hand-found bugs came from (a layout opened and looked at). Two reviewers get this same brief,
one Opus and one Fable, independently. Work alone; your reports will be compared. Prefix `{PREFIX}`.

## Rules

- **Repository:** C:/Users/adamo/OneDrive/Documents/NetBeansProjects/TrainControl, a Java 8 Swing app that drives a Marklin model railway through a Central Station 2/3 and runs trains automatically.
- **Review** dev at `{DEV}` against the last release, tag `{LAST_TAG}`. Read with `git show {DEV}:<path>`, `git grep <pattern> {DEV}`, `git diff {LAST_TAG} {DEV}`, `git log`, `git blame`. Builders change the main checkout, so don't rely on its working files.
- **Read first:** `CLAUDE.md`; `docs/reviews/README.md` (grades, "Before calling something a finding"); `docs/reference/behaviour.md` (owner rulings; a ruled behavior is not a defect); the orchestrator's `builder-rules.md`.
- **Known items are not new:** `py -3 docs/manual-tests/triagedb.py issues`, `... findings <ref>`, and the earlier reports the orchestrator names.
- **Calibration:** when the orchestrator says a calibration run is on, don't read tracker items (OB, FR, MT) filed or commented on after `{FREEZE}`, or any fix brief. Say in the report which titles you saw by accident.
- **Helpers:** Sonnet subagents may read for you. Re-read every site you report from them, and say so.

## The method

**Pick one thing and follow it everywhere it goes.** At each hop ask whether it is carried, translated, lost, duplicated or left stale. Write the chain as the code has it (file:line at each hop), then the hops where something drops or goes stale. Paths 1 to 3 are where the finds were, so spend most of your time there.

1. **One accessory square on a CS3 page, through its whole life:** the station's files (`magnetartikel.cs2`, the page file, the CS3's own plan in `/app/api/gbs`); the parse; the diagram in the main window and the same page in a pop-out; a route's command and the station's echo; Save; Download Central Station Layout Files; switching to the downloaded copy and reopening it after a restart; Sync; switching back to the station. At each hop: does the square keep its address, **protocol**, state and orientation, and do all windows showing it agree? The concrete case is the CS3 snapshot in `test/layouts/cs3-main-layout/` (its README.md first): two protocols at one address on one page, and a pair across two pages.
2. **Every window that shows a layout** (main, pop-out page windows, editors, the Autonomy menu) **through every event that changes the layout under it:** switching layouts each way (station, folder, new layout, download, Recent Layouts, a failed open); Sync; a page added, removed or renamed; a restart that restores windows. Does each window close, rebuild, or keep showing something no longer in use, with live controls?
3. **Start-up, from the window opening to a usable diagram,** on a CS2, a CS3, a local folder and with no station, and with the diagram tab or a pop-out restored. What does the user see at each moment? Is there a time the window looks empty or broken with no sign of work? Is there a wait whose length depends on the station?
4. **A sensor's state:** frames, the sensor download after connect, reconnect and Sync, the diagram, S88-armed routes and autonomy. Which events fire routes, which don't, and do all windows agree? Include sensors that no page draws.
5. **A locomotive:** name, address, decoder and functions across the station sync, Locomotive Settings, CSV export and import, multi-unit links, the autonomy setup's references, and a rename or delete.
6. **The autonomy setup:** across the editor's Save and Cancel, a layout switch, Download, a restart and a configuration switch.

**Named checks.** Each was once missed by every area review. Do them whatever the path says.
- **Download keeps the protocol:** download from a stand-in CS3, read the copy back as a folder, compare every square, and check what the copy's two-protocol squares drive after reopening and after one Save.
- **Windows follow a layout switch:** after each switch door, do pop-out windows and the Autonomy menu still show the old layout, or say something false (for example "needs a layout on this computer" after a download)?
- **No blank diagram at start-up:** timeline the window, the notice, the pictures wait, the grid and the spinner. Say what the user sees at each second.
- **A failed open leaves things as they were:** layout in use, page list, diagram, sensors.

**Running probes was productive.** Best results so far: a headless probe that reads the snapshot from a stand-in CS3 (`support.StandInStation` and the CS3 stand-ins; find them with `py -3 docs/tools/claims.py StandIn`), downloads it, reopens the copy and diffs every square; and one window run that samples the main window every 30 ms through start-up, opens two pop-outs, runs a door's own sequence, then switches layouts. Probe rules:
- build in your own detached worktree (`git worktree add --detach <path> {DEV}` from the main checkout, then env.sh); headless preferred;
- at most one window class per run, and window runs only in a slot the orchestrator grants; each run under about 4 minutes;
- never connect to a Central Station; never read or write `cs2_sample_layout/`, LocDB.data, UIState.data or preferences;
- copy the snapshot to a sandbox before any probe reads it.

## Grades and report

- **A:** wrong behavior on the layout, or data silently lost. **B:** incorrect results or crashes in specific configurations, or a window showing the user something false. **C:** cosmetic or narrow. **D:** checked and clean.
- For anything a user would hit by opening, switching or downloading a layout, give the steps a user would take.
- Report to the file the orchestrator names: a title; `**Status:** open`; `**Prefix:** {PREFIX}`; `**Reviewed:** dev {DEV}, cross-feature, <date>`; a method paragraph saying which paths you followed and how far (read or run); each finding as `### {PREFIX}-A1 - <title>` then `| **Disposition** | open |`, with path and hop, file:line, user steps, consequence, the commit that brought it (or "before {LAST_TAG}"), and a probe result or a verification request. Record clean paths as D findings (headings, not bullets).
- End with the report line: counts by grade; each A and B in one line with the user steps in brief; what you didn't reach; `Converged: yes` or `Converged: no`. A path left unfinished means `no`.
