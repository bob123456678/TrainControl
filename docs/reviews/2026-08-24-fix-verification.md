# Verification of the 24 August fixes

**Status:** open

**Prefix:** `FV` — cite findings from here as `FV-A1`, `FV-B2`, and so on.

**What was reviewed, and when.** Commit `ac61dcec` and the working tree as it stood on 2026-08-24,
against the seven findings raised by the preceding pass. The reviewer was asked to judge the code, not
the commit message, and was told explicitly that the message is a claim under test. Read-only: nothing
was built or run.

**Method.** Each of the seven claims was traced to the code that implements it, and each was probed for
the failure it was supposed to prevent. Where a fix was confirmed, the confirmation is recorded under D,
because "this was checked and is right" is worth as much to a later reader as a defect.

---

## A — high. Wrong behaviour on the layout, or data silently lost.

| | Finding | Disposition |
|---|---|---|
| **FV-A1** | Renaming a page prunes the page it just renamed | **Fixed** in `38ccbfc8` |
| **FV-A2** | Switching layout source replaces the diagram under moving trains | **Fixed** in `38ccbfc8` |

### FV-A1 — the rename path reconciles against stale page names

`AutonomySession.save()` prunes the setup against the pages the session holds, and at the moment the
rename calls it those pages still carry the **old** name: `LayoutDiagram.saveChanges` writes a new file
but never renames the object, and `refreshLayouts` does not run until `layoutEditingComplete`, later.

So the store had just been rekeyed to the new name and `reconcile` was handed a set of squares under the
old one. Every name, station, length, direction, protecting signal, caption and disabled link on the
renamed page read as track that had been deleted, and was dropped and written. That is the MT-135 loss
by a second route — and the `Reconciliation` report that would have said so is discarded at this call
site.

The delete path in the same commit used `saveWithoutReconciling` for exactly this reason. The rename
block was rewritten in that commit and kept the reconciling call.

**Disposition: fixed.** `saveWithoutReconciling` here too. Covered by
`testAutonomyDiagramStore.testARenamedPageSurvivesASaveAndLoad`, written in the shape Adam asked for —
mutate, check, save, load, check again, with the untouched page asserted as hard as the renamed one.

### FV-A2 — the doors that replace the whole diagram had no guard

`switchCSLayoutMenuItemActionPerformed` and `chooseLocalDataFolderMenuItemActionPerformed` replace every
page at once and had neither a running check nor an editor check, while the four page-level doors had
just been given one. During a run the diagram is swapped under moving trains, `resetAutonomySession`
skips its state capture *because* trains are running — losing every placement since the last save — and
the Start and graceful-stop controls are removed from the window while `Layout.runLocomotives` carries
on driving.

**Disposition: fixed.** Both refuse while autonomy is running and while an editor is open.

---

## B — medium. Incorrect results, or crashes in specific configurations.

| | Finding | Disposition |
|---|---|---|
| **FV-B1** | Combining linked pages fabricates an autonomy setup | **Fixed** in `38ccbfc8` |
| **FV-B2** | Route-database doors are unguarded while autonomy runs | **Open — needs Adam's ruling** |

### FV-B1 — the third door of the fabrication family

`combineLinkedPages` still called the lazy `getAutonomySession()` — which opens every page, runs the
caption migration and can raise a dialog — and then `saveQuietly()` unconditionally. On a layout where
autonomy had never been touched, combining two pages created `config/autonomy/setup.json` out of a
drawing gesture. The rename and delete doors were fixed for this in the commit under review; this one
was missed.

**Disposition: fixed.** Uses the already-built session and writes only to a setup that exists. Nothing
is lost by declining: `excludeRepeatedSensorPages` shuts a combined page automatically the first time a
setup is really created.

### FV-B2 — routes

`doSync` was guarded on the reasoning that it "deletes and re-adds routes". Direct route mutations while
autonomy runs remain unguarded: `deleteRoute`, the route-id change, route duplication,
`BulkEnableOrDisable`, `enableOrDisableRoute`, `importRoutes`, and the route editor's save (which itself
calls `syncWithCS2`).

**Disposition: open, and deliberately not decided here.** MT-141's wording names the locomotive
database, the track diagram, the autonomy config and the placements — not routes. Whether a route
change during a run is a modification of "a running layout" is Adam's call, not the reviewer's. Filed
so it is not lost.

---

## C — low. Cosmetic, dead code, or narrow edge cases.

| | Finding | Disposition |
|---|---|---|
| **FV-C1** | The refusal dialog talks about locomotives when refusing a page delete | **Open** |
| **FV-C2** | Two doors ask for a page name before refusing | **Open** |
| **FV-C3** | The on-disk page statics had no test | **Fixed** |

### FV-C1

`refuseWhileAutonomyRunning` shows `autolayout.ui.errorCannotEditLocomotivesWhileRunning` at all six
doors, including the page delete and the database sync. It was the right message at the four doors it
was lifted from and is wrong at the two new ones.

### FV-C2

`renameLayoutMenuItemActionPerformed` and `addBlankPageMenuItemActionPerformed` prompt for the page name
*before* the guard inside `duplicateOrRenameCurrentLayout` runs, so while trains are running the user
types a name and is then refused. The guard is in the right place for correctness and the wrong place
for courtesy.

### FV-C3

`renamePageOnDisk` and `deletePageOnDisk` had no coverage, and the second read in `repairOnDisk` — which
exists so page rules have names to work on — was removable with the whole suite still green.

**Disposition: fixed**, and the fix taught something worth recording. A test of the *rename* still does
not notice, and cannot: the shared half is keyed by page id, so a rename needs nothing from that read —
the id does not move and the name follows the index — and configuration points are keyed by name and are
rewritten either way. It is the **delete** that needs it, because `deletePage` gathers by asking
`isOnPage` of every key, and against id-form keys that is false for all of them.
`testADeletedPageIsForgottenThroughTheOnDiskDoor` pins it, mutation-checked.

---

## D — not defects.

| | Finding | Disposition |
|---|---|---|
| **FV-D1** | Seven claimed fixes, all verified as holding | Closed |

### FV-D1 — what was checked and found right

Recorded because a verification pass that reports only its exceptions tells a later reader nothing about
how much of the work was sound.

- **Fabrication on page rename/delete** — both paths use the built session field, gate on `exists()`,
  and otherwise repair the file. Traced downstream too: `layoutEditingComplete` →
  `resetAutonomySession` only writes when a configuration is active, and the lazy getter's
  `AutonomySession.open` and `migrateStationLabels` create nothing on a virgin layout.
- **`repairOnDisk`'s double load** — self-consistent by construction, because the map is built from the
  file's own `pages` record rather than the live index. The "old file with no `pages` record" worry is
  unfounded: id-form keys are only ever written together with that record.
- **The four new lockdown doors** — all four guards precede any mutation, and
  `refuseWhileAutonomyRunning(null)` is safe.
- **`deletePage`'s value-only squares** — the four collections added are the complete square-valued set,
  and every gathered value is filtered by `isOnPage`, so nothing on another page is over-forgotten.
- **The rewritten id tests** — assert properties rather than cases, and the reuse fixture genuinely
  reuses the retired id; the anti-vacuity assertion fails loudly if it ever stops doing so.
- **The dead Ctrl+V guard** — the surviving check uses the same predicate before the square is resolved,
  and the removed message key has no remaining reference anywhere.
- **The javadoc reattachment** — attached to the right method, and no new orphan introduced.
