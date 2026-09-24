package regression;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * Every door that changes the railway asks whether it may.
 *
 * REG9-A1: `refuseWhileEditorOpen` and `refuseWhileAutonomyRunning` had no test of any kind. Both were
 * made to permit everything and fourteen classes stayed green - including `testEditorSurfaceRules`,
 * the 56-test class that exists for this surface and which asserts about `isLayoutEditorOpen()`'s
 * implementation and about nothing that calls it.
 *
 * **What they stand in front of.** `behaviour.md` section 6a: *"Nothing that changes the shape of the
 * graph may run while trains are moving or a plan is being made ... The greying on the menu is not this
 * guard. Menu items are greyed when the popup OPENS and the action fires when it is CLICKED ... The
 * refusal has to be in the method."* The commit that added them (`38ccbfc8`) names what happened
 * without them: Execute Timetable and Return Home started trains under an open editor; switching to the
 * CS layout and swapping the layout folder replace the whole diagram, and during a run the diagram
 * goes, `resetAutonomySession` skips its capture BECAUSE trains are running, the stop controls are
 * removed from the window, and `Layout.runLocomotives` keeps driving.
 *
 * **What this pins, and what it does not.** It pins the half that actually goes wrong: a door that
 * forgets to ask. That is how two of these doors came to have no check at all, and it is what the
 * collecting of the longhand checks into two methods was meant to make hard.
 *
 * It does NOT pin the two guards' own bodies, which are a dialog over a predicate:
 * `refuseWhileAutonomyRunning` is `model.isAutonomyRunning()`, asserted behaviourally by
 * `core.testHomeStaging` (both halves, including staging) and `regression.testARouteDoesNotThrowSwitchesUnderATrain`;
 * `refuseWhileEditorOpen` is `isLayoutEditorOpen()`, which `testEditorSurfaceRules:3246` pins by shape.
 * A behavioural test of the refusals themselves would have to raise the modal dialog each one shows,
 * and a modal dialog inside a battery is how `testTheCeilingIsAskedAboutTheAmount` stranded a runner
 * for ten minutes on 2026-09-09. Said here rather than implied, so nobody reads this class as more
 * than it is.
 *
 * MUTATION: delete the `refuseWhileEditorOpen()` call from `requestReturnToHome` - the door
 * `38ccbfc8` was filed for - and the first test fails, naming it.
 *
 * @author Adam
 */
public class testTheRefusalsAreAskedAtTheDoors
{
    private static final String WINDOW = "src/org/traincontrol/gui/TrainControlUI.java";

    private static final String EDITOR = "refuseWhileEditorOpen()";

    private static final String AUTONOMY = "refuseWhileAutonomyRunning(";

    /**
     * Every door, the refusal it must ask for, and why that door needs it.
     *
     * A named list rather than a count, following this suite's convention: a count says something is
     * wrong and a list says what. Adding a door means adding a line here with its reason, which is the
     * moment to notice that a door needs a guard at all.
     */
    private static final String[][] DOORS =
    {
        // THE FOUR 38ccbfc8 WAS FILED FOR.
        {"executeTimetableActionPerformed", EDITOR,
            "a timetable starts trains, and an editor holds the diagram they would run on"},
        {"requestReturnToHome", EDITOR,
            "Return Home is a timetable by another name and starts trains the same way"},
        {"switchCSLayoutMenuItemActionPerformed", AUTONOMY + "|" + EDITOR,
            "it replaces the whole diagram - during a run the capture is skipped BECAUSE trains are "
            + "moving, the stop controls go, and runLocomotives keeps driving"},
        {"chooseLocalDataFolderMenuItemActionPerformed", AUTONOMY + "|" + EDITOR,
            "swapping the layout folder replaces the diagram, for the same reason"},

        // AND THE REST, which were longhand at four doors before the two methods collected them.
        {"startAutonomyActionPerformed", EDITOR,
            "starting autonomy against a diagram somebody is still editing"},
        {"addLocomotiveMenuItemActionPerformed", AUTONOMY,
            "MT-141: no modification to the locomotive database while the layout is running"},
        {"syncFullLocStateMenuItemActionPerformed", AUTONOMY,
            "a full sync rewrites the locomotive database under a running railway"},
        {"deleteLayoutMenuItemActionPerformed", AUTONOMY + "|" + EDITOR,
            "deleting a page the running railway or an open editor is using"},
        {"duplicateOrRenameCurrentLayout", AUTONOMY + "|" + EDITOR,
            "renaming a page moves what the setup and the editor both point at"},
        {"combineLinkedPages", AUTONOMY + "|" + EDITOR,
            "combining rewrites several pages into one"},
        {"AddRouteButtonActionPerformed", EDITOR,
            "a route is part of the diagram the editor holds"},
        {"editRoute", EDITOR,
            "the same, from the other door - and this one asks from a worker thread, which is why the "
            + "refusal posts its dialog rather than showing it"},
        {"doSync", AUTONOMY,
            "a Central Station sync replaces the databases underneath a running railway"},
        // NOT the excluded-page label (`openAutonomyEditorIfItCan`), since GUI-C8: it opens the editor, and opening
        // with an editor already open is not refused anywhere - `openLayoutEditor` brings that window forward, which
        // is what the Edit item it stands for does (`whyAutonomyEditorCannotOpen` returns null for an open editor).
        // Asking the refusal first showed "Close the editor first" in exactly the state the Edit item handles.
        {"refreshAutonomyPrompt", EDITOR,
            "the banner is a single button drawn before the state it guards against is entered, so it "
            + "cannot grey itself the way the menu does - its Load button asks (loading saves the setup "
            + "under an open editor); its Fix Setup button opens the editor, which brings an open one "
            + "forward, and asks nothing since GUI-C8"},
    };

    /**
     * Each door still asks.
     */
    @Test
    public void testEveryDoorStillAsksBeforeItActs() throws Exception
    {
        String source = withoutComments(read(WINDOW));

        List<String> silent = new ArrayList<>();

        for (String[] door : DOORS)
        {
            String body = bodyOf(source, door[0]);

            assertFalse(body.isEmpty(),
                "cannot find " + door[0] + " in " + WINDOW + " - if it was renamed, rename it here "
                + "too; if it was removed, remove its line. A door this list cannot find is a door "
                + "this test stops checking, silently");

            for (String needed : door[1].split("\\|"))
            {
                if (!body.contains(needed))
                {
                    silent.add(door[0] + " no longer asks " + needed + " - " + door[2]);
                }
            }
        }

        assertEquals(silent.toString(), "[]",
            "these doors act without asking whether they may. The greying on a menu is not this guard "
            + "- items are greyed when the popup opens and fire when they are clicked, so autonomy "
            + "started from another window in between leaves a live item over a running railway "
            + "(behaviour.md section 6a). " + silent);
    }

    /**
     * And no door has quietly appeared that asks nothing.
     *
     * **A ratchet, and it runs LAST on purpose** (REG9-B1): a staleness detector that pre-empts a
     * safety check is worse than no ratchet, and this file's sibling
     * `testSwitchingToACentralStationLayout` spent a day proving it - a stale count there kept the
     * assertion protecting Adam's railway from executing at all. So the claim above is a separate test
     * method, and this one only counts.
     *
     * The count is of CALL SITES, not doors: several doors ask both refusals.
     */
    @Test
    public void testTheListOfDoorsIsStillComplete() throws Exception
    {
        String source = withoutComments(read(WINDOW));

        // One of each occurrence is the declaration of the method itself.
        int asked = occurrences(source, EDITOR) - 1 + occurrences(source, AUTONOMY) - 1;

        // COUNTED INSIDE THE DECLARED DOORS, not from the list's own arithmetic.
        //
        // The first version added up the list instead, and two errors then cancelled: a door was
        // declared that never asked at all - `putThePendingTurnsBack`, where the name appears in a
        // COMMENT explaining why another door's guard makes something safe - while
        // `refreshAutonomyPrompt` asked twice (once since GUI-C8) and was counted once. The totals matched and the check
        // passed. Reading the bodies cannot cancel that way: a door that does not ask contributes
        // nothing and the test above names it, and a door that asks twice contributes two.
        int inDeclaredDoors = 0;

        for (String[] door : DOORS)
        {
            String body = bodyOf(source, door[0]);

            inDeclaredDoors += occurrences(body, EDITOR) + occurrences(body, AUTONOMY);
        }

        assertEquals(inDeclaredDoors, asked,
            "there are " + asked + " places in " + WINDOW + " that ask one of the two refusals, and "
            + inDeclaredDoors + " of them are inside the doors declared above. More asked than "
            + "declared means a door was added without a line here saying which it is and why it "
            + "needs one - and an undeclared door is one this class stops checking, silently");
    }

    /**
     * Every door that puts a train down uses the railway's answer.
     *
     * `behaviour.md` section 4 states this as a rule - *"nothing about a placement is recorded until
     * the railway has accepted it"* - and the coverage pass of 2026-09-11 found nothing holding it:
     * `W21-B3` is cited by no test in the suite.
     *
     * **What it was.** `Layout.moveLocomotive` refuses in four cases - autonomy is running, the
     * locomotive is unknown, the point is unknown, the target is not a destination - and returns false
     * rather than throwing. The diagram's menu discarded that answer, so the placement and the facing
     * were written into the setup AND SAVED for a move the railway had just declined. The setup is the
     * half that survives a restart, so the next build emitted the train on a square it was never put
     * on.
     *
     * **A named list, and one of them is allowed to discard it** - which is the point of writing them
     * down rather than counting. `GraphLocAssign.commitChanges` does discard the answer, and what
     * makes that safe is the line after it: `commitAndRecord` asks the POINT what is standing there
     * rather than assuming the move took, so a refused placement records the train that is really
     * there - or nothing - and never the one the dialog hoped for.
     *
     * MUTATION: drop the `if (!` from either of the two doors that guard, and this names it.
     */
    @Test
    public void testEveryPlacementDoorUsesTheRailwaysAnswer() throws Exception
    {
        String[][] doors =
        {
            {"src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java", "guards",
                "the diagram's Place and Remove items - W21-B3 and TWV-B5 are these two"},
            {"src/org/traincontrol/gui/TrainControlUI.java", "guards",
                "the diagram's paste door - TWV-B4, which discarded the refusal AND cleared the "
                + "clipboard, so a train cut with Control+X was on no square and on no clipboard"},
            {"src/org/traincontrol/gui/GraphLocAssign.java", "discards",
                "the assign dialog: commitChanges moves and does not look, and commitAndRecord "
                + "afterwards asks the POINT what is standing there rather than assuming"},
        };

        List<String> wrong = new ArrayList<>();

        for (String[] door : doors)
        {
            String source = withoutComments(read(door[0]));

            int calls = occurrences(source, "moveLocomotive(");

            int guarded = occurrences(source, "if (!") > 0
                ? countGuarded(source) : 0;

            if ("guards".equals(door[1]) && guarded == 0)
            {
                wrong.add(door[0] + " no longer uses moveLocomotive's answer anywhere - " + door[2]);
            }

            if (calls == 0)
            {
                wrong.add(door[0] + " no longer places a train at all, so this rule has stopped being "
                    + "checked there rather than being kept - " + door[2]);
            }
        }

        assertEquals(wrong.toString(), "[]",
            "these doors write a placement the railway may have refused. moveLocomotive returns false "
            + "and logs rather than throwing, so a discarded answer is a placement saved for a move "
            + "that never happened - and the setup is the half that survives a restart "
            + "(behaviour.md section 4, W21-B3). " + wrong);
    }

    /**
     * Only the rebuild's put-back stands a train on a copy of a station trains may not arrive at (REG4-C3, TDY3-A1).
     *
     * `moveLocomotive`'s four-argument form accepts such a copy when its last argument is true, and the guard above
     * counts calls without reading their arguments - a door passing `true` would still read as refusing.  A placement
     * is somebody choosing where a train goes, and none may choose a copy autonomy will not start; the put-back is the
     * railway saying where a train already is.
     *
     * MUTATION: pass true from the paste door and this fails.
     *
     * @throws Exception reading the sources
     */
    @Test
    public void testOnlyThePutBackAcceptsABarredCopy() throws Exception
    {
        List<String> found = new ArrayList<>();

        List<java.nio.file.Path> sources = new ArrayList<>();

        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(java.nio.file.Paths.get("src")))
        {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
        }

        for (java.nio.file.Path path : sources)
        {
            String source = withoutComments(read(path.toString().replace('\\', '/')));

            for (int at = source.indexOf("moveLocomotive("); at >= 0; at = source.indexOf("moveLocomotive(", at + 1))
            {
                int depth = 0;
                int end = at + "moveLocomotive(".length() - 1;
                int commas = 0;
                int lastComma = -1;

                for (; end < source.length(); end++)
                {
                    char c = source.charAt(end);

                    if (c == '(') depth++;
                    else if (c == ')' && --depth == 0) break;
                    else if (c == ',' && depth == 1)
                    {
                        commas++;
                        lastComma = end;
                    }
                }

                if (commas != 3) continue;

                String last = source.substring(lastComma + 1, end).trim();

                // The declaration's own parameter, and every call that passes false, refuse.
                if (last.startsWith("boolean ") || "false".equals(last)) continue;

                found.add(path.getFileName() + ": " + source.substring(at, end + 1));
            }
        }

        assertEquals(found.toString(), "[TrainControlUI.java: moveLocomotive(was.getKey(), back.getName(), false, true)]",
            "a door other than the rebuild's put-back may stand a train on a copy of a station trains may not arrive at"
            + " (REG4-C3): " + found);
    }

    /**
     * How many `moveLocomotive` calls in this source have their answer tested.
     *
     * Counted rather than pattern-matched on the whole call, because the call spans lines in two of
     * the three doors and a regex over it would be a second spelling of the shape it is checking.
     */
    private static int countGuarded(String source)
    {
        int found = 0;

        for (int at = source.indexOf("moveLocomotive("); at >= 0;
             at = source.indexOf("moveLocomotive(", at + 1))
        {
            // Back to the start of the statement, which is as far as the last brace or semicolon.
            int from = Math.max(Math.max(source.lastIndexOf(';', at), source.lastIndexOf('{', at)),
                source.lastIndexOf('}', at));

            String statement = source.substring(from + 1, at);

            if (statement.contains("if (!") || statement.contains("if(!")) found++;
        }

        return found;
    }

    /**
     * A method's body, by name, brace-matched from its declaration.
     */
    private static String bodyOf(String source, String method) throws Exception
    {
        java.util.regex.Matcher at = java.util.regex.Pattern.compile(
            "\\b" + java.util.regex.Pattern.quote(method) + "\\s*\\([^;{]*\\)[^;{]*\\{").matcher(source);

        while (at.find())
        {
            int open = source.indexOf('{', at.start());

            int depth = 0;

            for (int i = open; i < source.length(); i++)
            {
                if (source.charAt(i) == '{') depth++;

                if (source.charAt(i) == '}')
                {
                    depth--;

                    if (depth == 0) return source.substring(open, i + 1);
                }
            }
        }

        return "";
    }

    private static int occurrences(String source, String needle)
    {
        int count = 0;

        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + 1)) count++;

        return count;
    }

    private static String read(String path) throws Exception
    {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    /**
     * Comments out, because every one of these calls is discussed in a comment beside another one, and
     * a comment mentioning a refusal is not a door asking for it.
     */
    private static String withoutComments(String source)
    {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\n]*", "");
    }
}
