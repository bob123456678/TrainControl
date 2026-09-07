# The four package sweeps: what is still live

`C1-C29` from [`2026-08-17-whole-project-review.md`](../reviews/2026-08-17-whole-project-review.md),
the least-known part of the backlog. They were filed as four bundle rows — `automation/`, `marklin/`,
`base/`, `gui/` — and never triaged, so "four open items" was really **28**.

**Adjudication method, and its limit.** I spot-checked six against the code as it stands today. Six
of twenty-eight is a sample, not a sweep, and it found both kinds — still-live and stale — which is
the finding: **these cannot be closed or kept as a block.** The rest need the same treatment
one at a time.

## Checked, and still live

| | What | Why it matters |
|---|---|---|
| **C6** | `Point.java:150` — `this.uniqueId = ++id;` on a static field, unsynchronised | Two Points built on different threads can take the same id. Ids are what the setup keys settings to; this is the same class of fault as the page renumber that cost a layout restore. **Cheap to fix** (`AtomicInteger`), and I would do this one without asking. |
| **C22** | `TrainControlUI.java:17616` — `doClearCurrentPage` nulls `activeLoc` unconditionally | Clearing a page kills keyboard control of a locomotive that may be on another page. Small, user-visible. |
| **C14** | Six `Thread.currentThread().interrupt()` sites in `Locomotive` | The reported shape was re-assert-and-loop, which spins. Needs reading rather than counting — the count alone does not prove the spin. |

## Checked, and appears stale

| | What | Status |
|---|---|---|
| **C20** | `LocomotiveStats`' filter building a regex from user text, so typing `(` throws | No `Pattern` or `matches` in that file today. The mechanism seems gone. Worth one confirming read before closing. |
| **C29** | `RouteEditor`'s locomotive combo NPE | No matching `getSelectedItem()` call found. Likely restructured out. |
| **C21** | `deleteLoc` clearing mappings only on the current page | The method now touches one mapping site; the ghost-button shape may have been fixed by the mapping rework. Needs a read. |

## Not yet checked — 22 remaining

`C2`–`C5`, `C7`, `C9`–`C13`, `C15`–`C19`, `C23`–`C28`. Titles suggest a mix: speed truncation on
receive, a DCC UID fallback, CSV export NPEs, a headless IP prompt, off-EDT menu enabling, clipboard
side effects on drag, stale comments, an always-true `instanceof`.

## What I would ask you to rule on

1. **C6 — may I just fix it?** An unsynchronised id allocator behind everything the setup keys to.
   One line, no behaviour change anyone would notice, and the failure mode is the expensive one.
2. **Is the rest worth a pass at all?** They are all graded C, they are five weeks old, and a third
   of my sample had already gone away on its own. The honest options are: triage all 28 properly
   (half a day, and it will close more than it opens), fix the two or three that are cheap and
   user-visible and close the rest as stale, or leave the bundle alone and revisit if something bites.
3. **C7 specifically** — "received speed is integer-divided by 10, so a fine-step speed set by
   another controller is lost". If that is real it affects what the app *believes* about a train,
   which is the kind of thing the length and blocking rules now depend on. I would check that one
   regardless of what you decide about the others.

## On the structural items

`DD-C1` (decompose `TrainControlUI`) and `DD-A1` (the store's eleven collections declared in six
places) are **not started**, deliberately. Both are multi-session refactors of code every feature
touches, and starting one at the end of a long session — after a day in which two of my own "fixes"
were worse than the defect and one duplicated a rule that already existed — is how a working railway
gets broken for a week. They are real, they are not urgent, and they want a session of their own with
nothing else in it.

*Written 2026-09-07.*
