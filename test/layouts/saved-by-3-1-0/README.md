# saved-by-3-1-0

**`single-switch`, as 3.1.0 saves it with each new setting of FR-111**, for opening in an older build.  The same topology,
page, names and addresses as `single-switch`, whose README states them.

- `Approach` (`3,3`) is a stop-only station: `"runThrough": false`.
- `MainPlatform` (`7,3`) has Which Trains on All with a list remembered: `"rememberedLocs": ["Saved list train"]`.

`core.testATrainMustStopAndCannotPassThrough` compares this file with what this build writes for those choices, so it
cannot go stale.  `docs/tools/old-setups.sh --opens-in dev-3.0.9` runs `core.testASetupFromANewerBuildOpens` in the older
build over these files, then reads back what that build saved.

## Not invariants

- **Lengths, train placements and locomotive properties.**  A test sets them itself; the locomotive named on the
  remembered list is created by the test.

## Used by

- `core.testASetupFromANewerBuildOpens`
- `core.testATrainMustStopAndCannotPassThrough`
- `ui.testPageLinksGoByPageId`
