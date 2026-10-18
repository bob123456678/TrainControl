# RSA74 - Validation of TrainControl 3.0.2, round 4, over RSA73's fixes (`037364f7..2ce0052e` on `dev`): the last-moment question refuses a route at all four run doors and nowhere inside a run, every refusal gives back what it took, and the window left cannot hold a turn in practice; the hand send's comments did not list the new step (C1) - CONVERGED

**Status:** closed

**Prefix:** RSA74

**Reviewed:** branch `dev` at `2ce0052e` (*RSA73-C2: the refusal after Yes names the route and says to wait*): `git diff 037364f7 2ce0052e`.  Read-only; no build or test (a full battery held the lock).  2026-10-18.

**Counts:** 0 A, 0 B, 1 C, D below.

**This round has CONVERGED: no A and no B.**

---

### RSA74-C1 - The hand send's javadoc and its two callers' comments listed its steps without the new last-moment question, and one comment line ran to 201 characters

| | |
|---|---|
| **Disposition** | Fixed - the javadoc and both callers' comments name the step, and the line is wrapped.  The method's name (`refusedForAnEditorOnItsWay`) is kept: a claim looks it up by name, and its javadoc says it also asks a refresh and a route. |
| **Grade** | C - comments. |
| **Names** | RSA73-C1 |
| **Where** | `TrainControlUI.sendATrainByHand` (`:7558-7561`), `followTheTurn` (`:14993`); `AutoLocomotiveStatus.java:1186-1189`; `LayoutRightclickAutonomyMenu.java:1494-1498`. |
| **Needs execution** | read. |

---

### RSA74-D1 - Four callers, all operator doors; no run under way reaches the check; every refusal gives back its controls; the hand send's check after every question; the window left; the guide's paragraph

| | |
|---|---|
| **Disposition** | Checked - clean (but C1). |
| **Grade** | D |
| **Where** | Start's worker (`:30806`), Execute Timetable (`:30594`), Return Home after planning (`:27008`), the hand send (`:7588`); Start's `finally`, Execute Timetable's re-enable, Return Home's `finally`, the hand send before `handSendsUnderWay`; Start's gap is microseconds between `invokeAndWait` and the posted Graceful Stop; `Automation.md:644`. |
| **Needs execution** | read. |
