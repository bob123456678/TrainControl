# Reversing-edges display option review - 2026-07-31

**Prefix for citing this document: `RE`.**

**Version reviewed:** commit `ddfc48e` ("UI"), branch `master`, working tree clean. **Scope:** this
one commit - a display option that hides every graph edge leading into a reversing point, added to
the graph viewer's right-click display submenu, with a stored preference and keys in all eight
bundles. The commit beneath it (`093b5c1`) is the already-validated `HP` fix round, committed; the
coverage boundary is continuous. **Reviewed:** 2026-07-31. **No code was changed as part of this
review, and no tests were run** - the author builds and tests in NetBeans. The one claim that
mattered was checked against the real layout data rather than reasoned about.

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| RE-D1-D7 | No defects found. Clean checks on the display rule's premise (against real data), the unhide path, idiom parity, the consumer census, option interplay, the bundles, and the directed-edge model | D | Verified clean |

No A, B or C findings. Two observations that are decisions rather than defects are recorded at the
end.

---

## What the commit does

`HIDE_REVERSING_EDGES_PREF`, default false, read in exactly two places: a new `JCheckBoxMenuItem` in
`GraphRightClickGeneralMenu`'s display submenu (built only when autonomy is not running, like its
siblings), and one new clause in `TrainControlUI.updateVisiblePoints`, which sets `ui.hide` on any
edge whose *end* point is reversing. Unlike the existing hide-reversing-*stations* option, the
points themselves stay visible - clickable, assignable, readable - only their feeder edges vanish.

---

## D - clean checks

**D1 - the design premise holds on the real data.** The rule hides inbound edges only, and on a
directed graph that is only a decluttering win if reversing entries do not have live opposite edges
- otherwise the sibling arrow between the same two nodes stays drawn and the option would appear to
do half a job. Checked against the author's own `autonomy.json` (84 points, 104 edges, 10 reversing
points) by script: **11 edges lead into reversing points, and 0 of the 11 have a live opposite** -
reversing entries are one-directional in practice, arriving from one side and departing (9 edges)
toward somewhere else. So the hidden edges disappear entirely, no ghost arrows remain, and the code
comment's claim that these feeders are "most of the clutter" around a reversing point is consistent
with what the data shows.

**D2 - the unhide path.** The edge loop's `else` branch removes `ui.hide`, and `intoReversing` is
recomputed from the live preference on every `updateVisiblePoints` pass - so toggling the option
off restores every edge on the next pass, and no stale hide can survive a preference change. The
preference persists across restarts and is read fresh wherever it matters.

**D3 - idiom parity with the siblings.** The new menu item is byte-for-byte the sibling pattern:
same initial-state read, same toggle-by-stored-value listener, same try/catch with the same dialog,
same `!running` gate, added in the natural position between the two options it relates to. The
shared toggle idiom (write the negation of the stored value rather than the checkbox state) is safe
here for the same reasons it is safe in the siblings: the listener is attached after the initial
state is set so construction cannot fire it, the popup is rebuilt per right-click so its state
cannot go stale against another writer, and there is no other writer.

**D4 - consumer census.** Grepped all three hide-preference constants: one menu builder, one
enforcing method, nothing else. There is no second menu surface listing the sibling toggles that
this one should also have been added to - the one-of-several-entrances error had no entrance to
miss.

**D5 - interplay with the existing options.** The new clause is additive: the endpoint-hidden
checks are untouched, so edges hidden because their node is hidden stay hidden regardless of this
preference, and a reversing point hidden by the *stations* option hides its edges by the existing
mechanism whether or not the new one is active. The tooltip's promise that "the points stay
visible" is literally what the code does - nodes are never touched by this clause.

**D6 - the bundles.** Both keys are present in all eight bundles, non-ASCII escaped as the
encoding rule requires, with no placeholders - so the existing bundle-parity and placeholder-format
tests cover them with nothing further needed.

**D7 - the directed-edge model.** `addEdge` creates each Layout edge as its own *directed*
GraphStream edge (the `true` flag), so `e.getEnd().isReversing()` hides exactly the arrows the
option names and nothing adjacent. Outbound edges from reversing points deliberately remain - they
show where a reversing train departs to - which is what the tooltip's wording promises.

---

## Observations - decisions, not defects

**No changelog entry.** The feature is user-facing and has none; every recent user-facing change
gained one. The changelog is the author's editorial call and features are not covered by the
README's changelog rule (which is about defect claims), so this is recorded, not filed.

**No test.** A pure display toggle, in the territory the cycle summary already names as untested
by decision ("the editor flows, verified manually"). The one testable claim in the commit - the
premise in D1 - is pinned here by measurement instead; a unit test could assert the hide attribute
against a small graph, but it would test GraphStream bookkeeping more than this code.
