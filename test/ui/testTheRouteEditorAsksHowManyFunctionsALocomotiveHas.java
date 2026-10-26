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
 * Adam: "there is no validation of the function count on mm2 locomotives in the route view, F32 was accepted".  An MM2
 * decoder has five functions (`MarklinLocomotive.MM2_NUM_FN`, what `getNumF` reports), so F32 saves cleanly and does
 * nothing on the rails.
 *
 * It is not another claim in `testRouteEditorValidation`: that class builds `new RouteEditorFrame(null, null)`, and
 * `problemsWith` returns at `if (parent == null || parent.getModel() == null)` before any per-row rule, so the
 * function-range rule was never exercised.  This one pays for a real window and a real locomotive database.
 *
 * Through the CELL, not a built row: column 5 rebuilds the setting as `number + ":" + state`, and a row assembled
 * directly would test this test's arithmetic instead of the editor's.
 *
 * The command table also marks the row (OB-246, Adam: "yes"); the mark is the same answer `everythingWrong` gives at
 * Save, said while the choice is still in front of the user, and the third claim below is about it.
 *
 * MUTATION: drop `|| number >= loco.getNumF()` from the FUNCTION rule in `problemsWith` and the first claim goes red;
 * drop the whole rule and the control below still passes, which is what makes the pair worth having.
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
     * A locomotive whose name holds a COMMA, the one name a route cannot store: `RouteCommand.isNameUsable` refuses it
     * because a command line is comma-separated (brackets are allowed).  A real database can hold one.
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

        model = support.TestStation.simulatedWithWindowInDebug();

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
            // beside this one was written without one and passed while asking nothing.
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
     * A row Save would refuse is marked as it is typed, and a row Save accepts is not (OB-246).
     *
     * The target column offers every locomotive in the database, one of which may hold a COMMA that
     * `RouteCommand.isNameUsable` refuses, so the operator picked it and Save refused; the only way out was renaming
     * the locomotive.  Marked rather than hidden, since dropping a name would refuse a legal selection with nothing
     * shown: the row is drawn in the refusal ink with the reason on its tooltip (the OB-057 / OB-090 rule: the control
     * that offers an action asks the question the guard asks).
     *
     * Asserted through the table's own renderer, because what is claimed is what the user SEES; a claim on
     * `problemsWith` would pass with the mark not drawn.  The third assertion pins the mark as the SAME answer as the
     * gate by name.
     *
     * MUTATION: have `settle` record nothing and the first claim fails, the row drawn in ordinary ink
     * while Save still refuses it.  Mark on the answer alone - drop the `named` clause - and the
     * fourth claim fails: a row nobody has filled in yet is red the moment it is added, and a mark
     * that is always there is a mark nobody reads.  Stop `unshaded` clearing the tooltip and the + row
     * claim fails: that row wears the refusal of the row drawn before it.
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

                // NOTHING SELECTED: a selected row is painted in the look-and-feel's selection ink, which hides the
                // rule.
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

            // AND THE + ROW UNDER IT SAYS NOTHING: this renderer is one recycled component, so it wore the refusal of
            // the row drawn before it.
            final int plus = frame[0].commandRowCountForTest();
            final String[] onPlus = new String[1];

            SwingUtilities.invokeAndWait(() ->
            {
                frame[0].commandCellForTest(0, 4);

                java.awt.Component cell = frame[0].commandCellForTest(plus, 4);

                onPlus[0] = cell instanceof javax.swing.JComponent
                    ? ((javax.swing.JComponent) cell).getToolTipText() : null;
            });

            assertNull(onPlus[0], "the + row, which adds a command, carries the refusal of the row drawn"
                + " before it: " + onPlus[0] + " (GUI-C2)");

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
     */
    private static java.awt.Color inkOf(RouteEditorFrame frame, int row) throws Exception
    {
        final java.awt.Color[] ink = new java.awt.Color[1];

        SwingUtilities.invokeAndWait(() -> ink[0] = frame.commandCellForTest(row, 4).getForeground());

        return ink[0];
    }

    /**
     * The reason the table hangs on a command row's target cell, if any.
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
     * The control which OFFERS a value asks the question the guard asks (OB-057, OB-090): the old route editor built
     * its list from the locomotive, and the typed-number editor gave that up, with no live mark and a digits-only cell.
     *
     * A NUMBER ALREADY IN THE ROW IS OFFERED TOO, out of range or not: a combo box handed a value it has not got keeps
     * whatever was selected, so a route carrying F32 on a five-function locomotive would be rewritten to F0 (the
     * lights) by a click and a click away.
     *
     * Where the target is not a locomotive this database has, there is nothing to ask and a typed number is still
     * taken, so the editor stays usable for a route whose locomotive is not on this railway.
     *
     * MUTATION: put `column == 5` back on the `digitsOnly()` line and the first claim goes red; drop
     * the "keep what is there" clause from `functionsOffered` and the second does.
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
     * A name the database no longer has stays in the cell that holds it (Adam: "Fix b5, 253").  A combo box handed a
     * value it has not got keeps its own selection, so one click into a route naming a deleted locomotive and one away
     * rewrote the command to the first locomotive in the list, which satisfied the Save gate.  The function column
     * already kept a number in its cell (MT-464, above).
     *
     * MUTATION: offer only the database's names again, and this fails.
     */
    @Test
    public void testANameTheDatabaseNoLongerHasStaysInItsCell() throws Exception
    {
        final String gone = "GSR-B5 deleted locomotive";
        final String goneRoute = "GSR-B5 deleted route";

        assertNull(model.getLocByName(gone), "precondition: the database has a locomotive named " + gone);
        assertNull(model.getRoute(goneRoute), "precondition: the database has a route named " + goneRoute);

        final RouteEditorFrame[] frame = new RouteEditorFrame[1];

        SwingUtilities.invokeAndWait(() -> frame[0] = new RouteEditorFrame(ui, null, null));

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                frame[0].appendCommand(RouteCommand.RouteCommandFunction(gone, 1, true).toLine(null).trim());
                frame[0].appendCommand(RouteCommand.RouteCommandRoute(goneRoute).toLine(null).trim());
                frame[0].setConditionRowsForTest(java.util.Arrays.asList(org.traincontrol.base.ConditionOutline.Row
                    .condition(1, RouteCommand.RouteCommandAutoLocomotive(gone, 8851))));
            });

            assertTrue(frame[0].targetChoicesForTest(0).contains(gone), "a command naming a locomotive the database no"
                + " longer has is offered a list without it, so one click into the cell and one away rewrites it to"
                + " another locomotive: " + frame[0].targetChoicesForTest(0));

            assertTrue(frame[0].targetChoicesForTest(1).contains(goneRoute), "a command naming a route the database no"
                + " longer has is offered a list without it: " + frame[0].targetChoicesForTest(1));

            assertTrue(frame[0].conditionTargetChoicesForTest(0).contains(gone), "a condition naming a locomotive the"
                + " database no longer has is offered a list without it: " + frame[0].conditionTargetChoicesForTest(0));
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        }
    }

    /**
     * A condition that a locomotive is at a sensor names a sensor: saved with the sensor 0, the route could
     * never fire, and nothing said so - a sensor condition is refused it (`frameNotASensor`), this one was not.
     *
     * MUTATION: drop the sensor check and this fails.
     */
    @Test
    public void testAnAutoLocomotiveConditionNeedsASensor() throws Exception
    {
        final RouteEditorFrame[] frame = new RouteEditorFrame[1];

        final java.util.List<String> good = new java.util.ArrayList<>(), bad = new java.util.ArrayList<>();

        SwingUtilities.invokeAndWait(() -> frame[0] = new RouteEditorFrame(ui, null, null));

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                frame[0].setConditionRowsForTest(java.util.Arrays.asList(org.traincontrol.base.ConditionOutline.Row
                    .condition(1, RouteCommand.RouteCommandAutoLocomotive(LOC, 8851))));

                good.addAll(frame[0].problemsForTest());

                frame[0].setConditionRowsForTest(java.util.Arrays.asList(org.traincontrol.base.ConditionOutline.Row
                    .condition(1, RouteCommand.RouteCommandAutoLocomotive(LOC, 0))));

                bad.addAll(frame[0].problemsForTest());
            });

            assertFalse(good.stream().anyMatch(p -> p.contains("8851")), "control: a condition on sensor 8851 is"
                + " refused: " + good);

            assertTrue(bad.size() > good.size() && bad.stream().anyMatch(p -> p.contains("0")), "a condition that "
                + LOC + " is at sensor 0 saves, though no sensor is 0 and the route can never fire (GSR-C1): " + bad);
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        }
    }

    /**
     * A route is not offered as a route it can call: Save refuses a route that calls itself
     * (`frameRouteCallsItself`), and the ROUTE column's dropdown offered it, so the editor offered a choice it would then
     * refuse.
     *
     * MUTATION: offer the database's whole list again and this fails.
     */
    @Test
    public void testARouteIsNotOfferedToItself() throws Exception
    {
        final String name = "GSR-C3 route";

        java.util.List<RouteCommand> commands = new java.util.ArrayList<>();

        commands.add(RouteCommand.RouteCommandRoute("GSR-C3 elsewhere"));

        assertTrue(model.newRoute(name, commands, 0, org.traincontrol.marklin.MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
            false, null), "precondition: the route could not be made");

        final RouteEditorFrame[] frame = new RouteEditorFrame[1];

        try
        {
            SwingUtilities.invokeAndWait(() -> frame[0] = new RouteEditorFrame(ui, name));

            java.util.List<String> offered = frame[0].targetChoicesForTest(0);

            assertTrue(model.getRouteList().contains(name), "precondition: the database has no route " + name);

            assertFalse(offered.contains(name), "editing " + name + ", its ROUTE column offers " + name + " itself,"
                + " which Save then refuses as a route calling itself (GSR-C3): " + offered);
        }
        finally
        {
            if (frame[0] != null) SwingUtilities.invokeAndWait(() -> frame[0].dispose());

            try { model.deleteRoute(name); } catch (Exception ignored) { }
        }
    }

    /**
     * A name with a space at either end stays in its cell as it stands.  The cells once offered the name
     * trimmed, and a combo box cannot select a value it has not got, so one click into a command naming " X " and one away
     * still wrote the first locomotive in the list.  Only a routes file edited by hand carries such a name.
     *
     * MUTATION: offer the cell's name trimmed again, and this fails.
     */
    @Test
    public void testAPaddedNameStaysInItsCellAsItStands() throws Exception
    {
        final String padded = " RSA56-C3 padded ";

        java.util.List<RouteCommand> commands = new java.util.ArrayList<>();
        commands.add(RouteCommand.RouteCommandFunction(padded, 1, true));

        final org.traincontrol.marklin.MarklinRoute route = new org.traincontrol.marklin.MarklinRoute(model,
            "RSA56-C3 route", 9865, commands, 0, org.traincontrol.marklin.MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
            false, null);

        final RouteEditorFrame[] frame = new RouteEditorFrame[1];

        SwingUtilities.invokeAndWait(() -> frame[0] = new RouteEditorFrame(ui, "RSA56-C3 route", route));

        try
        {
            assertEquals(frame[0].commandRowForTest(0).getTarget(), padded, "precondition: the editor did not keep the"
                + " name as the route holds it");

            assertTrue(frame[0].targetChoicesForTest(0).contains(padded), "a command naming \"" + padded + "\" is"
                + " offered a list without that name as it stands, so one click into the cell and one away writes another"
                + " locomotive: " + frame[0].targetChoicesForTest(0));
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        }
    }

    /**
     * Whether the editor's save gate names this problem.
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
