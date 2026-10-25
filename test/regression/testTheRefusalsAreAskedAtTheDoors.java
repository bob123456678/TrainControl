package regression;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import support.SourceOf;

/**
 * Every door that changes the railway asks whether it may (`behaviour/editing-while-running.md` section 6a: *"The
 * refusal has to be in the method."*): Execute Timetable and Return Home must not start trains under an open editor,
 * and switching layouts or swapping the layout folder must not replace the diagram during a run.
 *
 * This pins the half that goes wrong: a door that forgets to ask. It does NOT pin the two guards' own bodies, which are
 * a dialog over a predicate: `refuseWhileAutonomyRunning` is `model.isAutonomyRunning()`, asserted behaviorally by
 * `core.testHomeStaging` and `regression.testARouteDoesNotThrowSwitchesUnderATrain`; `refuseWhileEditorOpen` is
 * `isLayoutEditorOpen()`, pinned by shape in `testEditorSurfaceRules`. A behavioral test of the refusals would have to
 * raise the modal dialog each one shows, and a modal dialog inside a battery strands a runner.
 *
 * MUTATION: delete the `refusedToSendATrain(` call from `requestReturnToHome` and the first test fails, naming it.
 *
 * @author Adam
 */
public class testTheRefusalsAreAskedAtTheDoors
{
    /**
     * A page name cannot take another page's file: a page lives in its sanitized name plus `.cs2`, so a name differing
     * from another's only in case (which Windows does not tell apart) or in a character a file name cannot hold is that
     * page's file. Asked of `LayoutPageEdit.pageUsingTheFileOf`, the one question every page door asks.
     *
     * MUTATION: compare the names in their case again, or unsanitized, and this fails.
     */
    @Test
    public void testAPageNameCannotTakeAnotherPagesFile()
    {
        List<String> pages = java.util.Arrays.asList("1 - Main", "A_B", "5 - Test");

        assertEquals(org.traincontrol.automationui.LayoutPageEdit.pageUsingTheFileOf(pages, "1 - main", null), "1 - Main",
            "a page named 1 - main beside 1 - Main is written to 1 - Main's file");

        assertEquals(org.traincontrol.automationui.LayoutPageEdit.pageUsingTheFileOf(pages, "1 - main", "5 - Test"),
            "1 - Main", "5 - Test renamed 1 - main is written to 1 - Main's file");

        assertEquals(org.traincontrol.automationui.LayoutPageEdit.pageUsingTheFileOf(pages, "A/B", null), "A_B",
            "a page named A/B beside A_B is written to A_B's file");

        assertNull(org.traincontrol.automationui.LayoutPageEdit.pageUsingTheFileOf(pages, "1 - MAIN", "1 - Main"),
            "1 - Main renamed in another case was refused, though it keeps its own file");

        assertNull(org.traincontrol.automationui.LayoutPageEdit.pageUsingTheFileOf(pages, "2 - Bottom", null),
            "a name no page's file has was refused");
    }

    /**
     * Every door that names a page asks whose file the name is: Rename, Duplicate and Add Blank Page through one
     * method, Duplicate's own choice of name, and Combine Linked Pages.
     *
     * MUTATION: take the question out of any of the three, and this fails, naming it.
     */
    @Test
    public void testEveryPageDoorAsksWhoseFileTheNameIs() throws Exception
    {
        for (String door : new String[] {"    void duplicateOrRenameCurrentLayout(", "    void combineLinkedPages(",
            "private void duplicateLayoutMenuItemActionPerformed("})
        {
            String body = SourceOf.declaration(door);

            assertTrue(body.contains("LayoutPageEdit.pageUsingTheFileOf("), door + " does not ask whose file the new page's"
                + " name is, so a name differing only in case - or in a character a file name cannot hold - writes over"
                + " another page (BCR-A2)");
        }
    }

    /**
     * The hand door (`sendATrainByHand`, the track diagram's and the Auto tab's) sends through `executePathByHand`,
     * which the cap on trains out does not count or refuse.
     *
     * MUTATION: send through plain executePath again, and this fails.
     */
    @Test
    public void testTheHandDoorSendsAsAHandSend() throws Exception
    {
        String body = SourceOf.declaration("void sendATrainByHand(");

        assertTrue(body.contains(".executePathByHand("), "the hand door sends its train as an ordinary dispatch, which the"
            + " cap on trains out refuses and counts during a run (BCR-C1)");

        assertFalse(body.contains(".executePath("), "the hand door still sends a train as an ordinary dispatch somewhere");
    }

    private static final String EDITOR = "refuseWhileEditorOpen()";

    private static final String AUTONOMY = "refuseWhileAutonomyRunning(";

    /** The same for a door that edits the setup or the diagram, which a route running holds as well. */
    private static final String SETUP = "refuseWhileARunHoldsTheSetup(";

    /**
     * The one gate every door that sends a train asks: the editor, the setup and the power, in `whyNoTrainMayBeSent`.
     */
    private static final String GATE = "refusedToSendATrain(";

    /**
     * Every door, the refusal it must ask for, and why. A door is named by the start of its declaration, found wherever
     * the program keeps it. A named list rather than a count: adding a door means adding a line with its reason, the
     * moment to notice that it needs a guard.
     */
    private static final String[][] DOORS =
    {
        // The four doors `38ccbfc8` was filed for.
        {"private void executeTimetableActionPerformed(", GATE,
            "a timetable starts trains, and an editor holds the diagram they would run on"},
        {"    void requestReturnToHome(", GATE,
            "Return Home is a timetable by another name and starts trains the same way"},
        {"private void switchCSLayoutMenuItemActionPerformed(", SETUP + "|" + EDITOR,
            "it replaces the whole diagram - during a run the capture is skipped BECAUSE trains are "
            + "moving, the stop controls go, and runLocomotives keeps driving"},
        {"private void chooseLocalDataFolderMenuItemActionPerformed(", SETUP + "|" + EDITOR,
            "swapping the layout folder replaces the diagram, for the same reason"},
        {"private void openRecentLayout(", SETUP + "|" + EDITOR,
            "a recent layout swaps the layout folder, as Open Layout does (FR-145)"},
        {"private void mountRestoreFromBackup(", SETUP + "|" + EDITOR,
            "Restore from Backup replaces the layout folder and the data, then closes TrainControl (FR-124)"},
        {"    void initializeLocalLayoutMenuItemActionPerformed(", SETUP,
            "Create New Layout switches the layout folder, syncs with the Central Station and resets autonomy (RSA13-B2)"},

        // And the rest, which were longhand at four doors before the two methods collected them.
        {"private void startAutonomyActionPerformed(", GATE,
            "starting autonomy against a diagram somebody is still editing"},
        {"void sendATrainByHand(", GATE,
            "the one door both hand doors send through - the Auto tab's list and the diagram's destinations; the Auto"
            + " tab's own copy never asked about an editor (RLV12-B1)"},
        {"private void addLocomotiveMenuItemActionPerformed(", AUTONOMY,
            "MT-141: no modification to the locomotive database while the layout is running"},
        {"public void promptTrainLength(", AUTONOMY,
            "RSA-A1, MT-141: a train's length is part of the locomotive database, and a run hands the track behind a"
            + " train back by it"},
        {"public void applyTrainLength(Locomotive l, int units)", AUTONOMY,
            "the same, at OK, for a dialog that was open when a run began (RSA-A1)"},
        {"private void syncFullLocStateMenuItemActionPerformed(", AUTONOMY,
            "a full sync rewrites the locomotive database under a running railway"},
        {"private void deleteLayoutMenuItemActionPerformed(", SETUP + "|" + EDITOR,
            "deleting a page the running railway or an open editor is using"},
        {"    void duplicateOrRenameCurrentLayout(", SETUP + "|" + EDITOR,
            "renaming a page moves what the setup and the editor both point at"},
        {"    void combineLinkedPages(", SETUP + "|" + EDITOR,
            "combining rewrites several pages into one"},
        {"private void AddRouteButtonActionPerformed(", EDITOR,
            "a route is part of the diagram the editor holds"},
        {"    void editRoute(String routeName)", EDITOR,
            "the same, from the other door - and this one asks from a worker thread, which is why the "
            + "refusal posts its dialog rather than showing it"},
        {"public void doSync(", AUTONOMY,
            "a Central Station sync replaces the databases underneath a running railway"},
        // Not the excluded-page label (`openAutonomyEditorIfItCan`): opening with an editor already open is not refused
        // anywhere, `openLayoutEditor` brings that window forward as the Edit item does, and asking the refusal first
        // would show "Close the editor first" in exactly the state that item handles.
        {"public void refreshAutonomyPrompt(", EDITOR,
            "the banner is a single button drawn before the state it guards against is entered, so it "
            + "cannot grey itself the way the menu does - its Load button asks (loading saves the setup "
            + "under an open editor); its Fix Setup button opens the editor, which brings an open one "
            + "forward, and asks nothing since GUI-C8"},
    };

    /**
     * Each door still asks.
     *
     * MUTATION: make the window's `initializeLocalLayoutMenuItemActionPerformed` do the work itself instead of handing
     * to `layoutFileActions`, and this fails.
     */
    @Test
    public void testEveryDoorStillAsksBeforeItActs() throws Exception
    {
        List<String> silent = new ArrayList<>();

        for (String[] door : DOORS)
        {
            // A door this list cannot find fails by itself, naming it: renamed, rename it here too; removed, remove its
            // line.
            String body = code(door[0]);

            for (String needed : door[1].split("\\|"))
            {
                if (!body.contains(needed))
                {
                    silent.add(door[0] + " no longer asks " + needed + " - " + door[2]);
                }
            }
        }

        // The window's one-line handler for Create New Layout hands to the door above (FR-136); a handler that did the
        // work itself would act without the refusal while the list read the clean copy.
        String handler = code("private void initializeLocalLayoutMenuItemActionPerformed(");

        if (!handler.contains("layoutFileActions.initializeLocalLayoutMenuItemActionPerformed()"))
        {
            silent.add("the window's Create New Layout handler no longer hands to layoutFileActions, so the refusal "
                + "that door asks is not the one it runs under");
        }

        assertEquals(silent.toString(), "[]",
            "these doors act without asking whether they may. The greying on a menu is not this guard "
            + "- items are greyed when the popup opens and fire when they are clicked, so autonomy "
            + "started from another window in between leaves a live item over a running railway "
            + "(behaviour.md section 6a). " + silent);
    }

    /**
     * The one gate asks what every door that sends a train used to ask for itself: the editor, the setup and the power,
     * and the hand doors in the other two files send through the one door that asks it. The editor is asked as one open
     * OR on its way: Edit Layout posts the editor's build, and a send queued ahead of it would otherwise find no editor
     * and run under it.
     *
     * MUTATION: take any of the three out of `whyNoTrainMayBeSent`, or send from a hand door past the one door, and
     * this fails.
     */
    @Test
    public void testTheOneGateAsksTheEditorTheSetupAndThePower() throws Exception
    {
        String gate = code("static String whyNoTrainMayBeSent(TrainControlUI ui");

        for (String asked : new String[] {"anEditorIsOpenOrOnItsWay()", "whyAutonomyStartIsRefused()", "whyAHandSendIsRefused()",
            "getPowerState()"})
        {
            assertTrue(gate.contains(asked), "the one gate every sending door asks no longer asks " + asked);
        }

        String editor = code("boolean anEditorIsOpenOrOnItsWay(");

        for (String asked : new String[] {"isLayoutEditorOpen()", "this.editorOnItsWay"})
        {
            assertTrue(editor.contains(asked), "the gate's question about the editor no longer asks " + asked);
        }

        // The two hand doors are classes of their own, read whole.
        for (String hand : new String[] {"public final class AutoLocomotiveStatus extends",
            "final class LayoutRightclickAutonomyMenu extends"})
        {
            String source = SourceOf.withoutComments(SourceOf.fileDeclaring(hand));

            assertTrue(source.contains("sendATrainByHand("), hand + " no longer sends through the one door");

            assertFalse(source.contains("executePath("), hand + " dispatches a train itself, past the one door");
        }
    }

    /**
     * The two locomotive doors that ask a modal question ask again after it, before they write:
     * `refuseWhileARouteDrivesIt` is asked as each door opens, and the edit dialog and delete confirmation then wait
     * long enough for an s88 trigger to start a route that drives the locomotive. Read from the source, as the other
     * doors are: a behavioral test would have to hold a modal dialog open while a route starts.
     *
     * MUTATION: take the second `refuseWhileARouteDrivesIt` out of either door, and this fails naming it.
     */
    @Test
    public void testTheRouteRefusalIsAskedAgainAfterTheDialog() throws Exception
    {
        // The door, what it is for, and what it writes
        String[][] doors =
        {
            {"    void changeLocAddress(Locomotive l, MouseEvent evt)", "renaming or re-addressing a locomotive",
                "this.ui.getModel().changeLocAddress(", "this.ui.getModel().renameLoc("},
            {"    void deleteLoc(String value, MouseEvent evt)", "deleting a locomotive",
                "this.ui.activeLoc = null;", "this.ui.cutLocomotive = null;", "this.ui.getModel().deleteLoc("},
        };

        List<String> wrong = new ArrayList<>();

        for (String[] door : doors)
        {
            String body = code(door[0]);

            int firstWrite = body.length();

            for (int w = 2; w < door.length; w++)
            {
                int at = body.indexOf(door[w]);

                assertTrue(at >= 0, "precondition: " + door[0] + " no longer writes " + door[w] + ", so this would check"
                    + " the wrong place");

                firstWrite = Math.min(firstWrite, at);
            }

            int dialog = body.lastIndexOf("showOptionDialog(", firstWrite);

            assertTrue(dialog >= 0, "precondition: " + door[0] + " no longer asks its question before it writes");

            // The last time it asks before it writes, which has to be after the question
            int asked = body.lastIndexOf("refuseWhileARouteDrivesIt(", firstWrite);

            if (asked < dialog) wrong.add(door[1] + " (" + door[0] + ")");
        }

        assertEquals(wrong.toString(), "[]", "these doors ask whether a running route drives the locomotive only before"
            + " their dialog, and write after it - so a route an s88 trigger starts while the dialog is open is not refused"
            + " (GSR-B6): " + wrong);
    }

    /**
     * A declaration's code, found wherever the program keeps it, with its comments blanked: a comment mentioning a
     * refusal is not a door asking for it.
     */
    private static String code(String signature)
    {
        return SourceOf.withoutComments(SourceOf.declaration(signature));
    }

    /**
     * And no door has quietly appeared that asks nothing. A ratchet that runs LAST on purpose: a stale count that
     * pre-empts a safety check is worse than no ratchet, so the claim above is a separate test method and this one only
     * counts CALL SITES, not doors (several doors ask both refusals).
     */
    @Test
    public void testTheListOfDoorsIsStillComplete() throws Exception
    {
        // Over the whole program's code.
        String source = SourceOf.allCode();

        // One of each occurrence is the declaration of the method itself; `refuseWhileAutonomyRunning` has two more,
        // the window's delegator handing to `RunGates`' declaration.
        int asked = occurrences(source, EDITOR) - 1 + occurrences(source, AUTONOMY) - 3 + occurrences(source, GATE) - 1;

        // Counted inside the declared doors, not from the list's own arithmetic: two errors can cancel in a total (a
        // door that never asked, one that asked twice), while reading the bodies names a door that does not ask and
        // counts a door that asks twice as two.
        int inDeclaredDoors = 0;

        for (String[] door : DOORS)
        {
            String body = code(door[0]);

            inDeclaredDoors += occurrences(body, EDITOR) + occurrences(body, AUTONOMY) + occurrences(body, GATE);
        }

        assertEquals(inDeclaredDoors, asked,
            "there are " + asked + " places in the program that ask one of the three refusals, and "
            + inDeclaredDoors + " of them are inside the doors declared above. More asked than "
            + "declared means a door was added without a line here saying which it is and why it "
            + "needs one - and an undeclared door is one this class stops checking, silently");
    }

    /**
     * Every door that puts a train down uses the railway's answer (`behaviour/facing.md` section 4: *"nothing about a
     * placement is recorded until the railway has accepted it"*). `Layout.moveLocomotive` returns false rather than
     * throwing when it refuses, and a door that discarded that answer wrote the placement into the setup AND SAVED it,
     * so the next build emitted the train on a square it was never put on.
     *
     * A named list, and every door on it guards: `GraphLocAssign.commitChanges` was once allowed to discard the answer
     * because `commitAndRecord` records no placement for a refused move, but that kept the setup honest and not the
     * locomotive: the dialog's length, speed and functions were written to a train the railway had refused to place.
     *
     * MUTATION: drop the `if (!` from any of the three doors, and this names it.
     */
    @Test
    public void testEveryPlacementDoorUsesTheRailwaysAnswer() throws Exception
    {
        // The doors, for the report: the diagram menu's Place and Remove items, the diagram's paste door (a refusal
        // that also cleared the clipboard left a train cut with Control+X on no square and on no clipboard), and the
        // assign dialog. Asked of the whole program, so a door that moves to another file is still counted: five
        // answers are tested today.
        List<String> wrong = new ArrayList<>();

        String source = SourceOf.allCode();

        int calls = occurrences(source, "moveLocomotive(");

        int guarded = countGuarded(source);

        if (calls == 0)
        {
            wrong.add("no door places a train at all any more, so this rule has stopped being checked rather than"
                + " being kept");
        }

        if (guarded < 5)
        {
            wrong.add("only " + guarded + " calls of moveLocomotive use its answer, where the diagram menu's two, the assign"
                + " dialog's one and the window's paste and put-back made five");
        }

        // And each of the five still does, door by door (FR-136): a total holds when one door drops its guard and a
        // guarded call is added elsewhere in the same change.
        final String[][] doors =
        {
            {"private void placeFacing(String locName, String pointName,", "the diagram menu's Place"},
            {"private void removeLocomotiveHere(Point current)", "the diagram menu's Remove"},
            {"public boolean commitChanges()", "the assign dialog"},
            {"private boolean locomotiveGestureOnDiagram(int keyCode, boolean controlPressed)", "the window's paste"},
            {"    static java.util.Set<String> putTheTrainsBack(org.traincontrol.automation.Layout built,"
                + "\n        java.util.Map<String, String[]> standing, java.util.function.Consumer<String> log,"
                + "\n        java.util.Set<String> placementsJustEdited)", "the rebuild's put-back"},
        };

        for (String[] door : doors)
        {
            if (countGuarded(code(door[0])) < 1)
            {
                wrong.add(door[1] + " (" + door[0] + ") no longer uses moveLocomotive's answer");
            }
        }

        assertEquals(wrong.toString(), "[]",
            "these doors write a placement the railway may have refused. moveLocomotive returns false "
            + "and logs rather than throwing, so a discarded answer is a placement saved for a move "
            + "that never happened - and the setup is the half that survives a restart "
            + "(behaviour.md section 4, W21-B3). " + wrong);
    }

    /**
     * Every placement door asks which headings a train can LEAVE by, not which it may arrive in (OB-284). The rule is
     * claimed in `core.testATrainIsPutOnlyWhereItCanStart`; this is its four call sites.
     *
     * MUTATION: have any door filter by `placeableFacingsFor` again, and this fails.
     */
    @Test
    public void testThePlacementDoorsKeepAHeadingATrainCanLeaveBy() throws Exception
    {
        // Asked of the whole program, so a door that moves is still counted: `placeableFacingsFor` is named by its
        // declaration and the one use inside the session, and by no door; `departableFacingsFor` by its declaration and
        // the six places the four doors ask it.
        assertEquals(SourceOf.occurrences("placeableFacingsFor("), 2, "a door chooses a placed train's heading among"
            + " the ways trains may arrive in, which turns round a train that can leave the way it faces (OB-284)");
        assertTrue(SourceOf.occurrences("departableFacingsFor(") >= 7, "a door no longer asks which headings a train can"
            + " leave by (OB-284)");

        // And each place still asks it (FR-136): a total of seven holds when one door stops asking and another call is
        // added elsewhere, so the places are named with the calls each makes.
        final Object[][] places =
        {
            {"    void placeLocomotive(TileKey tile)", 1},
            {"public static void commitAndRecord(GraphLocAssign edit)", 2},
            {"private LayoutRightclickAutonomyMenu(TrainControlUI ui,", 1},
            {"static String copyToPlaceOn(", 1},
            {"private java.util.Map<String, org.traincontrol.automationui.TilePorts.Side> placeableFacings(", 1},
        };

        for (Object[] place : places)
        {
            int asks = occurrences(code((String) place[0]), "departableFacingsFor(");

            assertTrue(asks >= (Integer) place[1], place[0] + " asks which headings a train can leave by " + asks
                + " times, not " + place[1] + " (OB-284)");
        }
    }

    /**
     * Only the rebuild's put-back and the three doors that keep a heading a train can leave by stand a train on a copy
     * of a station trains may not arrive at (OB-284, OB-296). `moveLocomotive`'s four-argument form accepts such a copy
     * when its last argument is true, and the guard above counts calls without reading their arguments, so a door
     * passing `true` would still read as refusing. Any other door passing `true` is a new decision.
     *
     * MUTATION: pass true from any door but these four - the editor's Place, say - and this fails.
     */
    @Test
    public void testOnlyThePutBackAcceptsABarredCopy() throws Exception
    {
        List<String> found = new ArrayList<>();

        // Every file of the program, comments blanked.
        {
            String source = SourceOf.allCode();

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

                // The five-argument form too: its fourth argument is the barred copy's, and a guard that counted four
                // arguments alone read every call of it as refusing.
                if (commas != 3 && commas != 4) continue;

                String[] args = source.substring(at + "moveLocomotive(".length(), end).split(",");

                String barred = args.length > 3 ? args[3].trim() : "";

                // The declaration's own parameter, and every call that passes false, refuse.
                if (barred.startsWith("boolean ") || "false".equals(barred)) continue;

                found.add(source.substring(at, end + 1));
            }
        }

        // The calls, whichever file holds them: the model's own, the assign dialog, the diagram menu's Place, the
        // rebuild's put-back and the paste.
        java.util.Collections.sort(found);

        assertEquals(found.toString(), "[moveLocomotive(getLoc(), p.getName(), false, true), "
            + "moveLocomotive(locName, pointName, false, true), "
            + "moveLocomotive(locomotive, targetPoint, purge, evenOntoABarredCopy, false), "
            + "moveLocomotive(placing.getName(), point.getName(), false, true), "
            + "moveLocomotive(was.getKey(), back.getName(), false, true, true)]",
            "a door other than the rebuild's put-back may stand a train on a copy of a station trains may not arrive at"
            + " (REG4-C3): " + found);
    }

    /**
     * How many `moveLocomotive` calls in this source have their answer tested; counted rather than pattern-matched on
     * the whole call, which spans lines in two of the doors.
     */
    private static int countGuarded(String source)
    {
        int found = 0;

        for (int at = source.indexOf("moveLocomotive("); at >= 0;
             at = source.indexOf("moveLocomotive(", at + 1))
        {
            // Back to the start of the statement: the last brace or semicolon.
            int from = Math.max(Math.max(source.lastIndexOf(';', at), source.lastIndexOf('{', at)),
                source.lastIndexOf('}', at));

            String statement = source.substring(from + 1, at);

            if (statement.contains("if (!") || statement.contains("if(!")) found++;
        }

        return found;
    }

    private static int occurrences(String source, String needle)
    {
        int count = 0;

        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + 1)) count++;

        return count;
    }

    /**
     * The diagram's destination sends on the railway its paths were found on, as the Auto tab's does, so a railway
     * replaced between the gather and the click refuses the send at its own fence instead of the new railway locking
     * the old one's track.
     *
     * MUTATION: dispatch the destination on the railway current at the click, and this fails.
     */
    @Test
    public void testTheDiagramsDestinationSendsOnTheRailwayItsPathsCameFrom() throws Exception
    {
        String item = code("private JMenuItem destinationItem(");

        assertTrue(item.contains("sendATrainByHand("), "the diagram's destination no longer sends through the one door");

        assertFalse(item.contains("getAutoLayout"), "the diagram's destination asks for the railway again at the click,"
            + " not the one its paths were found on (RSA-C6)");

        boolean carried = false;

        for (java.lang.reflect.Field field : Class.forName(
            "org.traincontrol.gui.LayoutRightclickAutonomyMenu$PathOptions").getDeclaredFields())
        {
            if (field.getType() == org.traincontrol.automation.Layout.class) carried = true;
        }

        assertTrue(carried, "the gathered paths do not carry the railway they were found on (RSA-C6)");
    }

    /**
     * The doors that choose a journey carry the stops counted where it was chosen: Return Home runs its plan with the
     * count read at the press and refreshes the Auto tab as it starts, a hand send carries the count read at the click,
     * and autonomy reads it before it chooses.
     *
     * MUTATION: let any of the three read the count later, or Return Home leave the Auto tab as it was, and this fails.
     */
    @Test
    public void testTheDoorsCarryTheStopsCountedWhereTheyChose() throws Exception
    {
        String home = code("    void requestReturnToHome(");

        assertTrue(home.contains(".executeTimetable(stopsAtPress)"), "Return Home runs its plan with the stops counted"
            + " when it starts running, not when it was pressed - a Yes while it plans does not end it (RSA2-C1)");

        assertTrue(home.indexOf("stopsOrdered()") >= 0 && home.indexOf("stopsOrdered()") < home.indexOf("new Thread("),
            "Return Home does not count the stops at the press, before its worker (RSA2-C1)");

        assertTrue(home.indexOf("repaintAutoLocList(true)") >= 0
            && home.indexOf("repaintAutoLocList(true)") < home.indexOf("loadReturnToHomeTimetable()"),
            "Return Home does not refresh the Auto tab before it plans, so its paths stay offered (RSA2-C3)");

        String hand = code("void sendATrainByHand(");

        assertTrue(hand.contains("stopsOrdered()") && hand.contains("answered, stopsAtClick)"), "a hand send does not"
            + " carry the stops counted at the click (RSA2-C2)");

        String loop = code("public void runLocomotive(");

        assertTrue(loop.indexOf("stopsOrdered.get()") >= 0 && loop.indexOf("stopsOrdered.get()") < loop.indexOf("pickPath(")
            && loop.contains("ALWAYS_REVERSE, stopsAtChoice)"), "autonomy does not count the stops before it chooses, and"
            + " carry that count to its journey (RSA2-C2)");
    }

    /**
     * Only the rebuild's put-back stands a train on a copy that is no station at all: the five-argument
     * `moveLocomotive`'s last argument is the railway saying where a train already is, and from any other door it would
     * put a train where autonomy cannot start it.
     *
     * MUTATION: pass true from any other door, and this fails.
     */
    @Test
    public void testOnlyThePutBackStandsATrainOnANonStation() throws Exception
    {
        List<String> found = new ArrayList<>();

        // Every file of the program, comments blanked.
        {
            String source = SourceOf.allCode();

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

                if (commas != 4) continue;

                String last = source.substring(lastComma + 1, end).trim();

                if (last.startsWith("boolean ") || "false".equals(last)) continue;

                found.add(source.substring(at, end + 1));
            }
        }

        assertEquals(found.toString(), "[moveLocomotive(was.getKey(), back.getName(), false, true, true)]",
            "a door other than the rebuild's put-back may stand a train on a copy that is no station (RSA17-A1): " + found);
    }
}
