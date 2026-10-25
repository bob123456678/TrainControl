# unfolded-route-tiles

**Adam's railway as it stood before the route-tile fold first ran on it**: the five pages, `gleisbild.cs2`,
`setup.json` and `configuration-Main.json` of `live-snapshot` as frozen from commit `e36df979`, copied byte for
byte.  `routes.json` and `autonomy_legacy` are left out; nothing that opens this folder reads them.

It exists because the fold rewrites the file it reads: once a build with the fold has opened his railway, his
route tiles hold no length, so a refreshed `live-snapshot` can no longer show the fold happening.  This copy
is never refreshed.

## Invariants — a test may rely on these

- **Five route tiles holding a length of 1 each**: `2 - Bottom` 19,3 and 4,11, `1 - Main` 6,7, 16,12 and 15,13.
  No other route tile has a length.
- **Everything else is `live-snapshot` at `e36df979`**: the pages, the stations, the lengths.

## Not invariants — set these in code

- **Trains, homes, the timetable, simulation.**  They came along with `configuration-Main.json` and no test
  here reads them.

## Used by

- `core.testMassAssignLengths`
