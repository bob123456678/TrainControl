# Adam's review of 2026-08-22, and what was done about it

Fifteen items, from his annotations on `2026-08-22-f2-review.md`. All fixed except one, which is a
question of cost rather than of correctness and is set out at the bottom. Two of his items were
questions rather than bugs; both are answered here, and one of them turned out to be the most useful
thing in the round.

Prefix: **AR** (Adam's round).

| # | What | Disposition |
|---|------|-------------|
| AR-1 | "Place Locomotive" and "Edit Locomotive" open a popup saying **null** | Fixed - the panel could not find the main window |
| AR-2 | Place Locomotive is redundant in the autonomy editor | Removed - the item now appears only to EDIT a train that is there |
| AR-3 | "Combine Linked Pages" appears in the menu dozens of times | Fixed - it was added again on every mount |
| AR-4 | Its tooltip is too wide | Fixed - wrapped |
| AR-5 | Split "Modify Current Layout" into Manage Pages, and add "Edit Layout Page..." listing the pages | Done |
| AR-6 | Long page names must not widen the sidebar buttons | Fixed - one width, cut short, whole name in a tooltip |
| AR-7 | Sidebar buttons bold black, headings blue semibold, per the style guide | Done |
| AR-8 | Too many pages must scroll | Done - beyond eight |
| AR-9 | Uneven gaps between the visibility checkboxes, and under the blue headings | Fixed - one constant, used by both |
| AR-10 | Guard the third form-layout cast | Done - all three now guarded |
| AR-11 | "Why is it not moving" is not expandable past a point | Fixed - taller, and the scrollbar shows itself |
| AR-12 | TopMainR1Inter says the train has nowhere to go, which seems wrong | **It is right, and the reason is worth reading** - see below |
| AR-13 | A marker showing a train is standing there in autonomy view | Done - a white six-armed star in the middle of the square |
| AR-14 | Populate the `[---]` labels with the train's name | Done, in the running diagram's black-on-white style |
| AR-15 | Unchecking "show autonomy" does not hide the station labels | Fixed |
| AR-16 | Can switching avoid closing and reopening the window? | Not done - see the last section |

## AR-12: the train really has nowhere to go, and here is why

This is the one worth your time.

I reproduced it on the sample layout - both your working copy and the pristine committed one, which
behave identically, so it is not something your editing session did. What comes back is 28 stations, each
saying "No track route leads there", which reads as the report being broken. It is not:

```
points=59  stations=29  sendable=22
reduced edges=16   built graph edges=7
blocking problems=79
  ERROR autosetup.ui.errorTileHasNoAddress at 1 - Main:0,1
  ERROR autosetup.ui.errorTileHasNoAddress at 1 - Main:1,1
  ...77 more
```

**Seven edges for twenty-nine stations.** The railway is not connected, so every destination is honestly
unreachable. The cause is the seventy-nine blocking findings: a switch or a signal drawn with no
accessory address cannot be routed over - trusting it to already be lying the right way is the danger
that refusal exists to prevent - so every run through one is cut, and a layout with a few dozen of them
is a set of perfectly good stations with nothing joining them.

The sample folder ships no `magnetartikel.cs2`, so those addresses come from whatever Central Station is
connected. With none, the whole diagram is unaddressed.

**What was wrong was the answer, not the analysis.** Saying "no track route leads there" twenty-eight
times describes the symptom and blames the track. The report now leads with the cause when there is one:

> **This setup has 79 blocking finding(s), so most of the railway cannot be routed over at all - a
> switch or signal with no address cuts every run through it. Fix those first: the answers below are
> about the fragments that are left.**

And when nothing was considered at all - a different failure that produced the same bare "nowhere to go"
- it now says how many stations exist, how many can be sent to, and which block the train is on, since
stations sharing the train's block are never candidates.

**To test it properly** you need a layout whose switches and signals have addresses: your real one, or
the sample with a Central Station connected. On the sample as it stands, "nowhere to go" is the correct
answer and the new line explains it.

## AR-1: why the popups said "null"

`AutonomyEditorPanel.parentWindow()` found the main window by walking up the window ancestry. Inside the
layout editor that walk cannot arrive: the editor is a `JFrame`, a `JFrame` has no owner, and the chain
ends there. So it returned null, `GraphLocAssign` dereferenced it on its first line, and the
`NullPointerException` was caught by `item()`'s handler - which shows a dialog containing the exception's
message. A `NullPointerException` has no message. The dialog said "null".

Both halves are fixed: the panel is now TOLD which window is the main one, and the handler no longer
shows a bare message - it falls back to the exception's type and writes the stack to the log, so the next
one of these says something.

## AR-10: what to test

The three casts reach for the form's own `GroupLayout` to swap one control for another. Nothing should
look different; the guard only matters if the form is ever rebuilt with a different layout manager. To
exercise all three:

1. Open the **autonomy editor**. The Addresses checkbox should have become a small column - Text Labels,
   Addresses, Show track lengths, then a blue "Directions" heading and its dropdown.
2. The banner should be across the top and the findings list across the bottom.
3. Switch to the **track editor**. The Addresses checkbox must be back where the form had it, alone.

## AR-16, and the wiring gap: what is actually left

**Switching without closing the window.** The mode half is nearly free: `setAutonomyMode(session)` already
has a `session == null` branch that takes the panel down and puts the form back, so switching Track and
Autonomy in place is close to wired. It has never run, though - closing has always been how autonomy mode
ends - so it wants a careful pass rather than a quick flip.

The page half is the expensive one. `layout` is a final field the whole class is built around: the grid,
the title, the remembered window bounds, the page the autonomy panel edits, the annotations and the
exclusion checks all follow from it. Switching in place means making it swappable and then auditing
everything that caches anything keyed by page - which is precisely the class of bug that produced
LT-A8, LT-A9 and FR-A1. Closing and reopening gets that right by construction.

Suggested: do the mode half in place, leave the page half closing and reopening until something else
makes it worth the audit.

**The `executeTool` wiring gap** (FR-A1's home) is still uncovered, and this round added a second reason
to care: paste, fill and palette placement now call `forgetTiles`, and nothing tests that they do. What
to test by hand is in `2026-08-20-tests-to-run.md` as 44 and 45, plus the new 53-58 below.
