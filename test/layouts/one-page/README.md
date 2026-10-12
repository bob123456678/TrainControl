# one-page

**The sample layout cut down to its first page**, `1 - Main`, with nothing set up.  The other four pages,
their entries in `gleisbild.cs2` and the page-link arrows that pointed at them are removed.  The same files
ship with MT-380 on the dev branch.

## Invariants — a test may rely on these

- **One page**, `1 - Main`, the only one `gleisbild.cs2` lists.
- **No autonomy setup**: there is no `config/autonomy` folder, so the Autonomy menu offers Import.

## Not invariants — set these in code

- **The setup.** `testASetupMovesToAOnePageLayout` exports one from the frozen copy of the sample layout and
  imports it here, in a sandbox copy.
- **Lengths, trains, priorities.** Nothing here sets them.

## Used by

- `regression.testASetupMovesToAOnePageLayout`
