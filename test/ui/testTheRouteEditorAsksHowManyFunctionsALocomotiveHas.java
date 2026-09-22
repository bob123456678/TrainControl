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
import static org.testng.Assert.assertNotEquals;
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
 * **The command table DOES mark now** (OB-246, Adam 2026-09-22: *"yes"*), and the third claim below is
 * about that.  It did not when this class was written, and this paragraph said so; the mark is the same
 * answer `everythingWrong` gives at Save, said while the choice is still in front of the user.
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

    /**
     * A locomotive whose name holds a COMMA, which is the one name a route cannot store.
     *
     * `RouteCommand.isNameUsable` refuses it because a command line is comma-separated - brackets are
     * allowed (Adam, 2026-09-04: *"bracketed loc names should just be allowed"*), and only the comma
     * is left.  A real database can hold one: nothing on the locomotive doors asks.
     */
    private static final String LOC_COMMA = "Probe, Comma";

    private static final int COMMA_ADDRESS = 64;

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

        assertNotNull(model.newMM2Locomotive(LOC_COMMA, COMMA_ADDRESS),
            "the comma-named probe locomotive could not be added, so the third claim has no"
            + " unstorable name to be about");

        assertFalse(RouteCommand.isNameUsable(LOC_COMMA),
            "precondition: a route can store " + LOC_COMMA + " after all, so there is nothing for the"
            + " mark to say about it");

        assertTrue(RouteCommand.isNameUsable(LOC),
            "precondition: the ordinary probe's own name is unstorable, so the control below would"
            + " be marked too and the claim would not discriminate");

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
                try { model.deleteLoc(LOC_COMMA); } catch (Exception ignored) { }

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
     * A row Save would refuse is marked as it is typed, and a row Save accepts is not.
     *
     * **Adam, 2026-09-22, ruling on OB-246:** *"yes"*.  The target column offers every locomotive in
     * the database, and one of them may hold a COMMA - which `RouteCommand.isNameUsable` refuses,
     * because a command line is comma-separated.  So the dropdown offered it, the operator picked it,
     * and Save refused with *"that name cannot be used in a route"*; the only way out was to rename
     * the locomotive.
     *
     * **Marked rather than hidden.**  Dropping the name from the list would refuse a legal selection
     * with nothing shown, which is the failure mode Adam has ruled against before.  The row is drawn
     * in the refusal ink with the reason on its tooltip: the choice stays, and the editor says what
     * Save will say.  That is the OB-057 / OB-090 rule - the control that offers an action asks the
     * question the guard asks - met by making the offer honest rather than by removing it.
     *
     * **Asserted through the table's own renderer**, because what is claimed is what the user SEES.
     * A claim that asked `problemsWith` would pass with the mark not drawn at all, which is exactly
     * how this editor came to have a Save gate and no marking for a month.
     *
     * **And the mark is the SAME answer as the gate**, which the third assertion pins by name: a mark
     * that said something of its own would be a second rule to keep in step with this one.
     *
     * MUTATION: have `settle` record nothing and the first claim fails, the row drawn in ordinary ink
     * while Save still refuses it.  Mark on the answer alone - drop the `named` clause - and the
     * fourth claim fails: a row nobody has filled in yet is red the moment it is added, and a mark
     * that is always there is a mark nobody reads.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testARowSaveWouldRefuseIsMarkedAsItIsTyped() throws Exception
    {
        final RouteEditorFrame[] frame = new RouteEditorFrame[1];

        SwingUtilities.invokeAndWait(() -> frame[0] = new RouteEditorFrame(ui, null, null));

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                frame[0].appendCommand(
                    RouteCommand.RouteCommandFunction(LOC, 1, true).toLine(null).trim());

                // NOTHING SELECTED.  A selected row is painted in the look-and-feel's selection ink
                // and the renderer leaves the foreground alone there, so a test that rendered the
                // selected row would read the same colour whatever the rule said.
                frame[0].clearCommandSelectionForTest();
            });

            assertTrue(frame[0].commandRowCountForTest() > 0,
                "the function command was not added, so there is no row to mark");

            // THE CONTROL FIRST: a row naming a locomotive a route can store is not marked.
            assertNull(tooltipOf(frame[0], 0),
                "a row Save accepts carries a reason it would be refused: " + tooltipOf(frame[0], 0));

            assertNotEquals(inkOf(frame[0], 0), RouteEditorFrame.refusedInkForTest(),
                "a row Save accepts is already drawn in the refusal ink, so the mark says nothing");

            // AND NOW THE NAME A ROUTE CANNOT STORE, chosen exactly as the dropdown offers it.
            SwingUtilities.invokeAndWait(() ->
            {
                frame[0].setCommandTargetForTest(0, LOC_COMMA);
                frame[0].clearCommandSelectionForTest();
            });

            assertEquals(inkOf(frame[0], 0), RouteEditorFrame.refusedInkForTest(),
                "picking " + LOC_COMMA + " from the dropdown leaves the row drawn in ordinary ink,"
                + " so the operator learns at Save that the only way out is to rename the locomotive"
                + " (OB-246).  What Save would say: " + frame[0].problemsForTest());

            String unusable = I18n.f("route.ui.frameNameNotUsable", LOC_COMMA);

            assertEquals(tooltipOf(frame[0], 0), unusable,
                "the mark gives no reason, or a different one from the gate's - which would be a"
                + " second rule to keep in step with the first.  Tooltip: " + tooltipOf(frame[0], 0));

            assertTrue(complains(frame[0], unusable),
                "the row is marked for something Save does not refuse, so the mark and the gate"
                + " disagree.  What Save said: " + frame[0].problemsForTest());

            // A ROW NOBODY HAS FILLED IN YET IS NOT MARKED.  It is added empty, Save refuses it, and
            // marking on that answer alone painted every new row red before a character was typed.
            SwingUtilities.invokeAndWait(() ->
            {
                frame[0].addCommandRowForTest();
                frame[0].clearCommandSelectionForTest();
            });

            int added = frame[0].commandRowCountForTest() - 1;

            assertNotEquals(inkOf(frame[0], added), RouteEditorFrame.refusedInkForTest(),
                "a command row is red the moment it is added, before anybody has typed into it - a"
                + " mark that is always there is a mark nobody reads");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        }
    }

    /**
     * The ink the table draws a command row's target cell in.
     *
     * @param frame the editor
     * @param row which row
     * @return its foreground
     */
    private static java.awt.Color inkOf(RouteEditorFrame frame, int row) throws Exception
    {
        final java.awt.Color[] ink = new java.awt.Color[1];

        SwingUtilities.invokeAndWait(() -> ink[0] = frame.commandCellForTest(row, 4).getForeground());

        return ink[0];
    }

    /**
     * The reason the table hangs on a command row's target cell, if any.
     *
     * @param frame the editor
     * @param row which row
     * @return its tooltip, or null
     */
    private static String tooltipOf(RouteEditorFrame frame, int row) throws Exception
    {
        final String[] why = new String[1];

        SwingUtilities.invokeAndWait(() ->
        {
            java.awt.Component cell = frame.commandCellForTest(row, 4);

            why[0] = cell instanceof javax.swing.JComponent
                ? ((javax.swing.JComponent) cell).getToolTipText() : null;
        });

        return why[0];
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
