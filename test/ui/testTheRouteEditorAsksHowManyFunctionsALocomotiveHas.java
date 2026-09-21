package ui;

import javax.swing.SwingUtilities;

import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.RouteEditorFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The route editor refuses a function number the locomotive has not got (MT-464).
 *
 * Adam, 2026-09-21: *"there is no validation of the function count on mm2 locomotives in the route
 * view, F32 was accepted"*.  An MM2 decoder has five functions - `MarklinLocomotive.MM2_NUM_FN`, which
 * is what `getMaxNumF` hands the constructor and what `getNumF` reports - so F32 on one is a command
 * that saves cleanly and does nothing on the rails.
 *
 * **Why this class exists rather than another claim in `testRouteEditorValidation`.** That class builds
 * its editors as `new RouteEditorFrame(null, null)` - no parent window, so no model - and `problemsWith`
 * returns at `if (parent == null || parent.getModel() == null)` before it reaches any per-row rule.  Its
 * own javadoc says so in as many words: no row-level complaint is reachable from that fixture, not the
 * address, not "no such locomotive", and not this one.  So the function-range rule has never been
 * exercised anywhere.  This one pays for a real window and a real locomotive database to ask it.
 *
 * **Through the CELL, not through a built row**, because the cell is what Adam typed into and because
 * the two halves of a function command live in two columns and one stored value: column 5 rebuilds the
 * setting as `number + ":" + state`, and a row assembled directly would be testing this test's
 * arithmetic instead of the editor's.
 *
 * **What this does NOT claim.** The command table has no live mark: `problemsWith` is consulted by
 * `everythingWrong`, which runs at Save, and nothing shades a command row as it is typed (the red
 * lettering at `WRONG` belongs to the conditions outline).  So a number out of range IS accepted by the
 * cell, and stays on screen looking ordinary until Save.  That is the half of his complaint this cannot
 * fix by checking, and it is filed separately.
 *
 * MUTATION: drop `|| number >= loco.getNumF()` from the FUNCTION rule in `problemsWith` and the first
 * claim goes red; drop the whole rule and the control below still passes, which is what makes the pair
 * worth having.
 *
 * @author Adam
 */
public class testTheRouteEditorAsksHowManyFunctionsALocomotiveHas
{
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation model;

    private static TrainControlUI ui;

    /** An MM2 locomotive, which has five functions and so has no F32. */
    private static final String LOC = "ProbeF32";

    private static final int ADDRESS = 63;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("this asks a real editor window, and a window needs a display");
        }

        // BEFORE init, which opens whatever the layout preference names - Adam's own railway on his
        // machine (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, true, false, true);

        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so the editor would have no model and no rule to ask");

        assertNotNull(model.newMM2Locomotive(LOC, ADDRESS), "the probe locomotive could not be added");

        assertNotNull(model.getLocByName(LOC), "the probe locomotive is not in the database");

        assertTrue(model.getLocByName(LOC).getNumF() < 32,
            "precondition: this locomotive reports " + model.getLocByName(LOC).getNumF()
            + " functions, so F32 is not out of range for it and this test asks nothing");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null)
            {
                try { model.deleteLoc(LOC); } catch (Exception ignored) { }

                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * F32 typed against a five-function locomotive is refused, and F3 is not.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAFunctionPastTheEndIsRefusedAndOneInRangeIsNot() throws Exception
    {
        final RouteEditorFrame[] frame = new RouteEditorFrame[1];

        SwingUtilities.invokeAndWait(() -> frame[0] = new RouteEditorFrame(ui, null, null));

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                frame[0].appendCommand(
                    RouteCommand.RouteCommandFunction(LOC, 1, true).toLine(null).trim());
            });

            assertTrue(frame[0].commandRowCountForTest() > 0,
                "the function command was not added, so there is no row to check");

            // TYPED, into the column the number is edited in.
            SwingUtilities.invokeAndWait(() -> frame[0].setCommandFunctionNumberForTest(0, "32"));

            String outOfRange = I18n.f("route.ui.frameNoSuchFunction", "32 / " + LOC);

            assertTrue(complains(frame[0], outOfRange),
                "F32 against a locomotive with " + model.getLocByName(LOC).getNumF() + " functions is"
                + " not refused, so the route saves and the command does nothing on the rails - Adam's"
                + " \"there is no validation of the function count on mm2 locomotives\".  What the"
                + " editor did say: " + frame[0].problemsForTest());

            // THE CONTROL.  A rule that complains about everything is not a rule, and the address check
            // beside this one was written without one and passed while asking nothing (S14-C1).
            SwingUtilities.invokeAndWait(() -> frame[0].setCommandFunctionNumberForTest(0, "3"));

            assertFalse(complains(frame[0], I18n.f("route.ui.frameNoSuchFunction", "3 / " + LOC)),
                "F3 on a five-function locomotive is refused as well, so this door now blocks a command"
                + " that works.  What the editor said: " + frame[0].problemsForTest());
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        }
    }

    /**
     * The function column offers that locomotive's functions, and keeps a number already in the row.
     *
     * **The other half of what Adam saw.**  The gate above refuses F32 at Save; nothing refused it as he
     * typed it, because the command table has no live mark and column 5 was a plain digits-only cell.
     * The rule this project has paid for repeatedly is that the control which OFFERS a value asks the
     * question the guard asks (OB-057, OB-090), and the OLD route editor did exactly that by building
     * its list from the locomotive - the note at the Save gate says so, in the sentence explaining what
     * was given up when this editor started taking a typed number.
     *
     * **A NUMBER ALREADY IN THE ROW IS OFFERED TOO, out of range or not.**  A combo box handed a value
     * it has not got keeps whatever was selected, so a route already carrying F32 on a five-function
     * locomotive would have that cell rewritten to F0 by a click and a click away - and F0 is the
     * lights.  A cell that changes a command by being looked at is worse than the typo it was guarding.
     *
     * **And where the target is not a locomotive this database has, there is nothing to ask** and a
     * typed number is still taken - the editor must stay usable for a route whose locomotive is not on
     * this railway, which is the whole reason it takes a typed number in the first place.
     *
     * MUTATION: put `column == 5` back on the `digitsOnly()` line and the first claim goes red; drop
     * the "keep what is there" clause from `functionsOffered` and the second does.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheFunctionCellOffersWhatThatLocomotiveHas() throws Exception
    {
        final RouteEditorFrame[] frame = new RouteEditorFrame[1];

        SwingUtilities.invokeAndWait(() -> frame[0] = new RouteEditorFrame(ui, null, null));

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                frame[0].appendCommand(
                    RouteCommand.RouteCommandFunction(LOC, 1, true).toLine(null).trim());
            });

            int has = model.getLocByName(LOC).getNumF();

            java.util.List<String> offered = frame[0].functionChoicesForTest(0);

            assertNotNull(offered,
                "the function cell offers nothing at all for a locomotive this database has, so the"
                + " operator is free to type F32 on a " + has + "-function decoder and hear about it"
                + " minutes later at Save (MT-464)");

            assertEquals(offered.size(), has,
                "the cell offers " + offered + " for a locomotive with " + has + " functions");

            assertTrue(offered.contains("0") && offered.contains(String.valueOf(has - 1)),
                "the numbers offered are not this locomotive's own: " + offered);

            assertFalse(offered.contains(String.valueOf(has)),
                "the cell offers F" + has + " on a locomotive whose functions stop at F" + (has - 1)
                + ": " + offered);

            // A NUMBER ALREADY IN THE ROW, which is how a route saved before this rule opens.
            SwingUtilities.invokeAndWait(() -> frame[0].setCommandFunctionNumberForTest(0, "32"));

            assertTrue(frame[0].functionChoicesForTest(0).contains("32"),
                "a row already holding F32 is offered a list without it, so the combo box keeps its own"
                + " selection and one click rewrites the command to F0 - the lights - without anybody"
                + " choosing anything: " + frame[0].functionChoicesForTest(0));

            // AND A TARGET THIS RAILWAY HAS NOT: nothing to ask, so nothing is refused.
            SwingUtilities.invokeAndWait(() -> frame[0].setCommandTargetForTest(0, "NoSuchLocomotive"));

            assertNull(frame[0].functionChoicesForTest(0),
                "the cell offers a fixed list for a locomotive this database does not have, which"
                + " leaves no way to write a function command for a locomotive that is not on this"
                + " railway - a check that refuses something legal is worse than no check");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        }
    }

    /**
     * Whether the editor's save gate names this problem.
     *
     * @param editor the window
     * @param problem the message it should carry
     * @return whether any line of it contains that message
     */
    private static boolean complains(RouteEditorFrame editor, String problem)
    {
        for (String line : editor.problemsForTest())
        {
            if (line != null && line.contains(problem)) return true;
        }

        return false;
    }
}
