# A frozen snapshot of the operator's railway

Taken from `cs2_sample_layout/` on **2026-08-29**, at Adam's request (OB-132):

> we should have a test fixture that doesn't change, and is a snapshot of my current layout.

## What this is for

`test_layout/` is the fixture almost every test uses, and it is an older and smaller railway. That is
usually what you want - it is small, it is well understood, and dozens of tests pin counts against it.

What it is not is a railway that RUNS. `testTimetableOnDerivedGraph` starts autonomy on the derived
graph and records where the trains go; on `test_layout` nothing moves inside the run window, so the
test skips, and a skipped test covers nothing. That is the whole of OB-132.

This folder is the other thing: a real railway, with enough live destinations and placed locomotives
that autonomy has somewhere to send them.

## It does not change

**Do not regenerate this from `cs2_sample_layout` because it has moved on.** The point of a fixture is
that a test failing means the CODE changed. Re-snapshotting silently rewrites the thing every
assertion is measured against, and a test that then fails is telling you about the railway rather than
about the software - which is the failure `test_layout` was frozen to avoid in the first place.

If a genuinely new snapshot is wanted, take it as a new folder beside this one and move tests over
deliberately, one at a time, looking at what each one starts saying instead.

## Where it came from, and what is in it

Copied file for file, with the Central Station's own `.bak` files left out - they are the station's
undo history rather than part of the layout, and two copies of a page in one fixture is an invitation
to edit the wrong one.

At the moment it was taken it carried, among other things:

- five pages, of which `1 - Main` and `2 - Bottom` are in the setup and the other three are excluded;
- the one-way run through switch 50 at `1 - Main:17,6` **repaired** - the snapshot is deliberately
  taken after that fix, because before it the two pages were joined by a single one-way edge and no
  train on `2 - Bottom` could reach anything on `1 - Main` at all;
- four locomotives placed, and the `Main` configuration active.

`cs2_sample_layout/` itself is the operator's live railway and is **not** a fixture: it is written by
the application whenever trains move, and `docs/tools/battery.sh` fingerprints it before and after a run
precisely so that a test writing to it is noticed. Nothing may point a test at that folder.
