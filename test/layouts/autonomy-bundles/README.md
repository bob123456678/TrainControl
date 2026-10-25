# autonomy-bundles

**Three autonomy setups Adam exported from his railway**, as the Autonomy menu's export writes them (a bundle:
`shared`, `configuration` and `version` 1).  Not a railway: there are no pages here.  A test imports a bundle into
an empty store over the pages of another fixture and builds what it says.

| File | What it is |
|---|---|
| `Autonomy 1b.json` | The setup that got stuck, which `core.testRouteInventory` reports as "2-stuck-1b" |
| `Autonomy 1d.json` | A later one, with more barred arrivals and turnout directions |
| `Autonomy 1e.json` | The latest of the three |

## Where it came from

Copied byte for byte on 2026-10-19 from `tc_backup/` in Adam's checkout, which git ignores, so before this only his
machine could run the claims that read them.  Adam, 2026-10-19: *"create made-up stand-ins shaped like them.  You can
put in the autonomy files, but not my real locdb/uistate."*  So these are his autonomy files, and nothing else of his:
no `LocDB.data` and no `UIState.data`.  A test that needs locomotives uses the test database (`test/LocDB3_0_0.data`,
which the runners copy into the run's own data folder).

## Invariants - a test may rely on these

- **The three files and their bytes**, as exported.  Each names the five pages of his railway (`1 - Main` to
  `5 - Test`), leaves out `3 - Top Parking`, `4 - Combined` and `5 - Test`, names 44 squares and 29 stations, and
  holds one configuration, `Autonomy 1`, of 58 points.
- **What they build to** over `test/test_layout`'s pages, imported into an empty store: a valid layout, each.

## Not invariants - set these in code

- **The pages.** The bundles carry none; the test chooses which railway they are imported over.
- **Lengths, trains and locomotives.** The six lengths and the placements in the files are what Adam had then; a test
  that needs others sets them itself, and the locomotives a placement names need not be in the test database.

## Used by

- `core.testRouteInventory`
