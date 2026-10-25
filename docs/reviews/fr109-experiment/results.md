# FR-109 experiment: builder results

| builder | branch / commit | tokens | tool calls | duration | red | green | files (+/-) |
|---|---|---|---|---|---|---|---|
| post-haiku | exp-post-haiku 2aaa5df7 | 265,130 | 72 | 1,348 s | 5/5 fail | 5/0 + 14 classes green | 7 files, +788 -116 |
| pre-haiku | exp-pre-haiku cbb25668 (committed by me) | ~360k (stopped) | ~150 | ~7,500 s (stopped) | unknown | DID NOT FINISH: stopped by Adam's call at ~360k tokens; its test JVM had hung 30 min once on an unanswered dialog | 5 files, +1048 -106 |
| pre-sonnet | exp-pre-sonnet c5a0b615 | 299,723 | 98 | 6,586 s | 6/6 fail | 6/0 + 13 classes green (1 red fixed: menu took focus; 4 rerun) | 9 files, +1195 -111 |
| post-sonnet | exp-post-sonnet b950dcb0 | 194,440 | 56 | 1,481 s | 5/5 fail (6th test added after red) | 6/0 + 17 classes green | 5 files, +751 -9 |

## post-haiku notes (from its report)
- Reused: RunGates, existing keys (no bundle changes), LayoutFileActions rename via new refusePageName extraction; split TrainControlUI delete into askToDeleteLayoutPage + deleteLayoutPage.
- Touched an existing pin (testTheRefusalsAreAskedAtTheDoors) to follow the new door.
- New tests cover track mode only; autonomy-mode reopen untested.
- Red not inspected per failure ("should be the menu lookup").
- Made promptUserForLayout package-private.

## post-sonnet notes (from its report)
- Menu bar "Manage Pages"; closes the editor, runs the main window's own handlers, reopens on the selected page in the same mode.
- New pageActionSettled() callback hooked into every exit of the three handlers and LayoutFileActions (GEN-FIRST/LAST handler bodies, outside GEN regions).
- Red failures read per test ("the layout editor has no ...").
- 6th test (setup-editor rename) never shown red.
- Window flashes shut/open on cancel or refusal; run refusal not tested through the editor.

## pre-sonnet notes (from its report)
- Base b6f3dfcb (3.0.2), no CLAUDE.md. Editor items call the main window's handlers with the editor as source; new editorBehind(evt) skips only the editor-open refusal; LayoutEditor.stepOutFor/arriveOn.
- Delete tail moved out to deleteTheConfirmedPage. Window census bumped; layout READMEs' Used by updated.
- Tests cover duplicate/case-only refusal, declined delete, only-page refusal, route-running refusal (all three items).
- Hit testTheEditorNamesItsShortcuts red (menu took focus), fixed; did not rerun the other classes after.
- Unsaved-work prompt reuses a key whose wording is slightly off. Autonomy-mode path untested.
- Duration inflated by slot waits (~30 min) and the pre-tree runner holding every slot.
