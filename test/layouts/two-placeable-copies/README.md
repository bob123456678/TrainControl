# two-placeable-copies

A frozen copy of Adam's railway as it stood on **2026-09-22**, taken for `OB-270`.

**Why a second copy of his railway, when `live-snapshot` already exists.**  `live-snapshot` is months of
edits behind, and the difference is not cosmetic: on it, only the EASTBOUND copy of `BottomMainA` is a
destination, so `StationIndex.speakerAt` lands a paste on that one and the paste happens to agree with
the walk.  On his railway today **both** copies are destinations, `speakerAt` takes the first, and a
train pasted back onto the square stands facing the other way.

Three attempts to reproduce OB-270 came out clean against `live-snapshot` for exactly that reason -
including one claim that passed with the fix and without it, which is a check that cannot fail.  A
fixture that no longer contains the shape being tested is worse than no fixture: it reports clean about
the case it has lost.

**What makes it the fixture for this:** two squares have two PLACEABLE copies facing different ways -
`BottomMainA` (W and E) and `LowerParkingOuter` (S and N).  `live-snapshot` has none.

**What is in it:** the autonomy setup, the legacy `autonomy.json`, the page index and the five pages.
The `.bak` files his folder carries are left out - they are the application's own backups and nothing
reads them.

**Do not point a sandbox at `cs2_sample_layout` instead.**  `LayoutSandbox.open(File)` copies, so
reading his live folder is safe and was done once to establish the difference above - but a claim keyed
to it is a claim whose result changes when he edits his railway, and he edits it while the suite runs.

Read by `ui.testAPastedTrainFacesTheWayTheOperatorChose`.
