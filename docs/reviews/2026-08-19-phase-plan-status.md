# Where 3.0.0 stands against the diagram-autonomy plan

Assessed 2026-08-19 against `docs/plans/2026-08-01-diagram-autonomy-plan.md`, whose own living status
section was last written 2026-08-16. This updates it rather than replacing it.

The plan delivers in two phases: **Phase 1 builds the new and disables the old; Phase 2 deletes the
old.** Phase 2 is explicitly gated on "the ground-truth diff and a period of real use on the author's
layout" — so it is not something this session could or should have finished.

---

## The plan's "still to build" list, item by item

| Item | Status |
| --- | --- |
| **Documentation rewrite** (`Automation.md`, `Readme.md`) | **Done.** `Automation.md` is now a diagram-first user guide with four worked examples, a settings reference and a troubleshooting section; the JSON and API material is preserved in `AutomationAPI.md`. The readme's 3.0.0 changelog is rewritten and the 3.0.0-only bugfixes removed. Eight screenshots remain to be taken — the guide lists exactly what each should show |
| **Editor-side placement/homes UI** | **Done.** The setup editor's right-click carries `Add a Locomotive to Autonomy...`, the home-locomotive chooser, and the station/turning/arrivals/signal settings. This was the "remains wanted" item |
| **Phase 1 finish: sever the remaining old-path entries** | **Not done, and not attempted.** `TrainControlUI` still holds 32 references to `graphViewer`. They are guarded rather than reached on the diagram path, but the plan's intent was to remove the entries, not to guard them |
| **Phase 2 removal** (graph window, GraphStream jars, `GRAPH_*` prefs, JSON-era buttons) | **Not done, correctly.** Gated on real use. The three `gs-*.jar` files are still shipped |
| **Train icon** | **Not done**, deferred by the author, "last" |

---

## What this session added that the plan did not ask for

Worth listing separately, because none of it is plan debt — it came from your instructions and from
what the reviewers found:

- The new route editor (rows rather than typed commands, protocol and delay, capture into commands or
  conditions)
- Multi-select in the diagram editor, and the removal of the paste-row/paste-column and
  shift-the-diagram options it replaces
- "Why is this train not moving" — per-locomotive reasons, on the panel and as an editor tool
- The diagram export
- Path-choice preferences, including least-recently-visited
- The sync moved off the event thread, with a one-at-a-time guard

---

## My assessment of readiness

**Phase 1 is functionally complete for a user**: a layout can be set up, run, explained and edited
entirely on the diagram, and the documentation now describes that path rather than the JSON one.

**Phase 1 is not complete as the plan defines it**, because the old graph window is still reachable
and still referenced from `startAutonomy`-adjacent flows. That is deliberate on the plan's own terms
— "nothing is deleted in Phase 1, so a problem is one toggle away" — but it means the tidy-up the
plan calls "Phase 1 finish" is genuinely outstanding rather than done.

**Phase 2 should not start until you have run the thing.** That is what the plan says and I agree with
it: the removals are irreversible in practice, and the ground-truth diff against your own layout is
the evidence they are waiting on.

### The one thing I would put in front of you

The plan's Phase 1 finish is a tidy-up with no user-visible effect. Everything with a user-visible
effect is done. So the decision in front of you is not "is Phase 1 finished" but **"has this been run
on the real layout long enough to start deleting things"** — and only you can answer that.
