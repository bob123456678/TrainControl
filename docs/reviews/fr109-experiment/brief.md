You are building a feature in TrainControl, a Java 8 Swing application that drives a model railway (Märklin Central Station). Work ONLY in your worktree: `{WT}` (your own branch; already set up and clean). Test scratch folder: `{SCR}` (it holds cp.txt, the classpath).

**The feature (FR-109, the owner's words: "add basic layout administration, like renaming and deleting and adding pages, to the layout editor").**  Pages are managed today from the main window's Layouts menu (Add Blank Page, Rename Current Page, Delete Current Page). Bring those three into the layout editor window itself (the track diagram editor, class `LayoutEditor` in `src/org/traincontrol/gui/`):
- Three items in the layout editor (a menu or buttons, your choice), acting on the page being edited.
- They must behave exactly as the Layouts menu items of the same names do: the same name checks, the same confirmations, the same refusals while autonomy or a route is running. Call the code those items already use; do not write a second copy of their rules.
- After Add or Rename, the editor shows that page; after Delete, it shows another page, or closes if none is left.
- New UI is hand-written Swing. Never edit the NetBeans `//GEN-BEGIN`..`//GEN-END` regions or the generated variables block. Message text goes in the bundles `src/org/traincontrol/resources/messages*.properties` (8 languages, ASCII only, other characters as `\uXXXX`); reuse existing keys where they fit.

**Tests.** At least one test per action that drives it the way a user would (open the layout editor, use the new item) and checks what the user sees and what is saved, shown failing before your change and passing after. Look at existing tests of the layout editor and of the Layouts menu page actions for fixtures (`test/`, packages core, regression, ui). A new test class must be added to `build.xml`. Run tests with `TC_SCRATCH={SCR} bash docs/tools/one.sh pkg.ClassName [more classes]` (or `label:pkg.Class.method1,pkg.Class.method2` for single methods), from your worktree. The runner waits for a free test slot by itself if the machine is busy; run it as one background command and wait for it to finish. Run the classes your change can affect.

**Rules.**
- Never touch `LocDB.data`, `UIState.data` or the user's preferences outside the test sandboxes, nor anything under `cs2_sample_layout/`. Never connect to a Central Station.
- Do not modify `docs/tools/`. Stage files by name (never `git add -A`), never `git stash`, never push. Commit your work on your branch when done.
- If the worktree has a `CLAUDE.md`, follow it.
- You are on your own: decide reasonable details yourself rather than asking.

**Report** (short): what you changed (files), the tests you added, the red and green "Total tests run" lines, the classes you ran and their Total lines, and anything you were unsure about.
