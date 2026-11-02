package ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
import javax.swing.JTextField;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.CommandRow;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.RouteEditorFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Restart;
import static support.RouteEditing.*;

/**
 * A route built by hand in the route editor, start to finish, and judged by what Save leaves in the railway's model: a
 * yard entry route made from nothing through the main window's Add Route, a mistake the Save refuses and then lets
 * through once it is put right, the yard route again after the program closes and starts, and a Central Station route
 * that opens to be read and is left as it was.  Every edit goes through the editor's own components: its buttons, its
 * cells' drop-downs and fields, its handles and its captions.
 *
 * The program runs with its window over a copy of the test layout (`support.Restart`), so a restart reads back the
 * database the first program saved.
 */
public class testBuildingARouteByHand
{
    private static final String PREFIX = "BH ";
    private static final String YARD = PREFIX + "Yard entry";
    private static final String LIGHTS = PREFIX + "Yard lights";
    private static final String SIDING = PREFIX + "Siding exit";
    private static final String SHUTTLE = PREFIX + "Station shuttle";

    private static Restart restart;

    private static support.YardRoute yard;

    /** The yard route as Save left it, for the restart to compare. */
    private static String yardAsSaved;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the route editor is a window");

        restart = Restart.open(new java.io.File("test/test_layout"));

        // A run killed before its cleanup leaves its routes in the saved database.
        clearOurRoutes(model());

        yard = support.YardRoute.on(model());
        support.YardRoute.addLights(model(), LIGHTS);

        refresh();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (restart == null) return;

        try
        {
            closeEditors();

            // Saved without them, so the next run's database starts clean.
            clearOurRoutes(model());

            model().saveState(false);
        }
        finally
        {
            restart.close();
        }
    }

    /**
     * Scenario 1: a yard entry route made from nothing.  Add Route on the Routes tab opens a new route's editor; the
     * route is named, given two switches, a signal set to green with a delay, a three-way point set left and a call to
     * the yard's lighting route, each chosen in its cells; its conditions are built as "sensor 5 on, and either switch
     * 20 turned or sensor 6 off" by adding three, grouping the last two and choosing At least one on the new group's
     * caption; it is set to fire automatically when a train leaves sensor 7, and saved.  The model then holds exactly
     * that route, under a local id, one route more than before, and the route list shows it.
     *
     * MUTATION: in `ConditionOutline.toExpression`, join every level by AND, and the saved conditions read "Feedback
     * 5,1 & (20,1 & Feedback 6,0)".
     */
    @Test
    public void testAYardEntryRouteMadeFromNothingIsSavedAsBuilt() throws Exception
    {
        final int routesBefore = model().getRouteList().size();

        final RouteEditorFrame frame = addRoute(YARD);

        try
        {
            JTable commands = commandTable(frame);

            for (int add = 0; add < 5; add++) pressOnTheCommands(frame, "route.ui.menuAddCommand");

            type(commands, 0, COMMAND_TARGET, String.valueOf(yard.switch1));
            pick(commands, 0, COMMAND_SETTING, "turn");

            type(commands, 1, COMMAND_TARGET, String.valueOf(yard.switch2));

            // The address names a signal on the layout, so the row becomes one and offers green and red.
            type(commands, 2, COMMAND_TARGET, String.valueOf(yard.signal));
            assertEquals(Components.onTheEventThread(() -> commands.getValueAt(2, COMMAND_KIND)),
                CommandRow.labelFor(CommandRow.Kind.SIGNAL), "a signal's address did not make its row a signal");
            pick(commands, 2, COMMAND_SETTING, "green");
            type(commands, 2, COMMAND_DELAY, "2000");

            pick(commands, 3, COMMAND_KIND, CommandRow.labelFor(CommandRow.Kind.THREE_WAY));
            type(commands, 3, COMMAND_TARGET, String.valueOf(yard.threeWay));
            pick(commands, 3, COMMAND_SETTING, "left");

            pick(commands, 4, COMMAND_KIND, CommandRow.labelFor(CommandRow.Kind.ROUTE));
            pick(commands, 4, COMMAND_TARGET, LIGHTS);

            JTable conditions = conditionTable(frame);

            press(frame, "route.ui.menuAddCondition");
            type(conditions, conditionLine(conditions, CommandRow.Kind.FEEDBACK, 1), CONDITION_TARGET, "5");

            press(frame, "route.ui.menuAddCondition");
            int second = conditionLine(conditions, CommandRow.Kind.FEEDBACK, 1);
            pick(conditions, second, CONDITION_KIND, CommandRow.labelFor(CommandRow.Kind.ACCESSORY));
            type(conditions, second, CONDITION_TARGET, String.valueOf(yard.conditionSwitch));
            pick(conditions, second, CONDITION_SETTING, "turn");

            press(frame, "route.ui.menuAddCondition");
            int third = conditionLine(conditions, CommandRow.Kind.FEEDBACK, 1);
            type(conditions, third, CONDITION_TARGET, "6");
            pick(conditions, third, CONDITION_SETTING, "off");

            selectLine(conditions, lineShowing(conditions, CONDITION_TARGET, String.valueOf(yard.conditionSwitch),
                CONDITION_KIND, CommandRow.labelFor(CommandRow.Kind.ACCESSORY)));
            extendTo(conditions, conditionLine(conditions, CommandRow.Kind.FEEDBACK, 6));
            press(frame, "route.ui.menuGroupSelected");
            chooseRule(conditions, 1, "route.ui.conditionCaptionAny");

            Components.press(Components.withText(frame, I18n.t("route.ui.frameEnabled")));
            Components.settle();
            typeInto(beside(frame, "route.ui.frameS88", JTextField.class), "7");
            Components.select(beside(frame, "route.ui.frameTrigger", javax.swing.JComboBox.class),
                I18n.t("route.ui.triggerLeaves"));

            assertEquals(save(frame), "[]", "Save asked a question about a route it should take");
        }
        finally
        {
            closeEditors();
        }

        Route saved = model().getRoute(YARD);

        assertNotNull(saved, "Save made no route called " + YARD + ": " + model().getRouteList());
        assertEquals(commandsOf(saved), yard.switch1 + ",1 | " + yard.switch2 + ",0 | " + yard.signal + ",0,2000 | "
            + (yard.threeWay + 1) + ",0,300 | " + yard.threeWay + ",1 | Route " + LIGHTS, "the commands Save wrote: two"
            + " switches, the signal green after 2 s, the three-way left as its two motors, then the call");
        assertEquals(conditionsOf(saved.getConditions()), "Feedback 5,1 & (" + yard.conditionSwitch
            + ",1 | Feedback 6,0)", "the conditions Save wrote");
        assertEquals(saved.getS88() + " " + saved.getTriggerType() + " " + saved.isEnabled() + " " + saved.isLocked(),
            "7 OCCUPIED_THEN_CLEAR true false", "fires itself when a train leaves sensor 7, and is the application's");
        assertTrue(MarklinControlStation.isLocalRouteId(saved.getId()), "a route made here took a station's id: "
            + saved.getId());
        assertEquals(model().getRouteList().size(), routesBefore + 1, "Save made more or fewer routes than the one");
        assertTrue(Components.waitFor(() -> support.RouteListDoors.cellOf(ui(), YARD) != null, 10000),
            "the route list does not show the route Save made");

        yardAsSaved = everythingOf(saved);
    }

    /**
     * Scenario 7: the yard route survives a restart.  The program is closed as its close box closes it, which saves the
     * database, and started again; the route it reads back is the same in every part, its id and its conditions'
     * grouping included.  So is the route the export writes and the import parses.
     *
     * MUTATION: in `MarklinSimpleComponent(MarklinRoute r)`, drop `this.conditions = r.getConditions();`, and the route
     * comes back from the database with no conditions.
     */
    @Test(dependsOnMethods = "testAYardEntryRouteMadeFromNothingIsSavedAsBuilt")
    public void testTheYardRouteIsTheSameAfterARestart() throws Exception
    {
        assertNotNull(yardAsSaved, "precondition: the yard route was not saved");

        restart.again();

        assertEquals(everythingOf(model().getRoute(YARD)), yardAsSaved, "the yard route read back after a restart");

        // An import arms only the routes the file says were armed, so a parsed route is manual until then.
        final String json = model().exportRoutes();
        Route exported = null;

        for (Route parsed : model().parseRoutesFromJson(json))
        {
            if (YARD.equals(parsed.getName())) exported = parsed;
        }

        assertNotNull(exported, "the export holds no " + YARD);
        assertEquals(everythingOf(exported), yardAsSaved.replace(" automatic /", " manual /"), "the yard route written"
            + " out by the export and parsed back");
        assertTrue(model().routesSavedArmed(json).contains(YARD), "the export does not say the yard route was armed");

        assertTrue(Components.waitFor(() -> support.RouteListDoors.cellOf(ui(), YARD) != null, 10000),
            "the route list after the restart does not show the yard route");
    }

    /**
     * Scenario 5: a mistake caught, then fixed.  A siding route ticked to fire automatically, with its sensor left at 0
     * and its first switch given no address, is refused by Save, naming both; Go Back and Fix leaves the editor open and
     * the model without it.  With the address typed, Save still refuses, naming only the sensor; with the sensor typed
     * too, Save takes the route, and the model holds it as corrected.
     *
     * MUTATION: in `RouteEditorFrame.everythingWrong`, drop the `frameAutomaticNeedsSensor` refusal, and the first
     * refusal names only the row.
     */
    @Test
    public void testAMistakeStopsTheSaveUntilItIsPutRight() throws Exception
    {
        final int exitSwitch = switchAt(model(), 40);
        final int sidingSwitch = switchAt(model(), exitSwitch + 1);

        final String unaddressed = I18n.f("route.ui.frameRowNumberIsWrong", "1", I18n.f("route.errorNotA", "",
            I18n.t("route.wordAddress")));
        final String noSensor = I18n.t("route.ui.frameAutomaticNeedsSensor");

        final RouteEditorFrame frame = addRoute(SIDING);

        try
        {
            Components.press(Components.withText(frame, I18n.t("route.ui.frameEnabled")));
            Components.settle();

            JTable commands = commandTable(frame);

            pressOnTheCommands(frame, "route.ui.menuAddCommand");
            pressOnTheCommands(frame, "route.ui.menuAddCommand");
            type(commands, 1, COMMAND_TARGET, String.valueOf(sidingSwitch));
            pick(commands, 1, COMMAND_SETTING, "turn");

            String first = refusal(frame);

            assertTrue(first.contains(unaddressed) && first.contains(noSensor), "Save's refusal should name the switch"
                + " with no address and the missing sensor: " + first);
            assertTrue(frame.isShowing(), "Go Back and Fix closed the editor");
            assertNull(model().getRoute(SIDING), "a refused Save made the route anyway");

            type(commands, 0, COMMAND_TARGET, String.valueOf(exitSwitch));

            String second = refusal(frame);

            assertTrue(second.contains(noSensor) && !second.contains(unaddressed), "with the address typed, Save should"
                + " refuse for the sensor alone: " + second);

            typeInto(beside(frame, "route.ui.frameS88", JTextField.class), "8");

            assertEquals(save(frame), "[]", "Save asked a question about the corrected route");
        }
        finally
        {
            closeEditors();
        }

        Route saved = model().getRoute(SIDING);

        assertNotNull(saved, "the corrected route was not saved");
        assertEquals(commandsOf(saved) + " / " + saved.getS88() + " " + saved.isEnabled(), exitSwitch + ",0 | "
            + sidingSwitch + ",1 / 8 true", "the corrected route as Save wrote it");
    }

    /**
     * Scenario 8: a Central Station route, opened from the route list by View Route Details, can be looked at and not
     * changed.  Typing into a cell, Alt+Down, the Delete key and the trash on the commands, Add Command, and the Delete
     * key and a right-click on the conditions are each tried; Save is grayed, Cancel closes without asking, and the
     * model's route is as it was.
     *
     * MUTATION: in `RouteEditorFrame.becomeReadOnly`, drop `if (saveButton != null) saveButton.setEnabled(false);`,
     * and Save is offered on the station's route.
     */
    @Test
    public void testACentralStationRouteCanBeReadButNotChanged() throws Exception
    {
        List<RouteCommand> list = new ArrayList<>();

        list.add(RouteCommand.RouteCommandAccessory(switchAt(model(), 50), Accessory.accessoryDecoderType.MM2, true));
        list.add(RouteCommand.RouteCommandAccessory(switchAt(model(), 51), Accessory.accessoryDecoderType.MM2, false));

        int id = 1;

        while (model().getRoute(id) != null) id++;

        assertTrue(model().newRoute(SHUTTLE, id, list, 9, Route.s88Triggers.CLEAR_THEN_OCCUPIED, false,
            org.traincontrol.base.NodeExpression.fromList(java.util.Collections.singletonList(
                RouteCommand.RouteCommandFeedback(9, false)))), "precondition: the station's route was not made");

        model().getRoute(SHUTTLE).setLocked(true);
        refresh();

        final String before = everythingOf(model().getRoute(SHUTTLE));

        final RouteEditorFrame frame = (RouteEditorFrame) support.RouteListDoors.open(ui(), SHUTTLE,
            "route.ui.menuViewRouteDetails");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            JTable commands = commandTable(frame);
            JTable conditions = conditionTable(frame);

            assertFalse(Components.onTheEventThread(() -> commands.editCellAt(0, COMMAND_TARGET)),
                "a cell of the station's route opened for typing");

            selectLine(commands, 0);
            support.Keys.chord(commands, java.awt.event.KeyEvent.VK_DOWN, java.awt.event.InputEvent.ALT_DOWN_MASK);
            support.Keys.press(commands, java.awt.event.KeyEvent.VK_DELETE);
            clickCell(commands, 1, COMMAND_TRASH, 0, 1);

            javax.swing.AbstractButton add = commandButton(frame, "route.ui.menuAddCommand");

            if (Components.onTheEventThread(add::isEnabled)) Components.press(add);

            selectLine(conditions, conditionLine(conditions, CommandRow.Kind.FEEDBACK, 9));
            support.Keys.press(conditions, java.awt.event.KeyEvent.VK_DELETE);

            assertNull(support.Mouse.rightClickAt(conditions, new java.awt.Point(60, 12), 500), "a right-click on the"
                + " station's conditions offered a menu");

            javax.swing.AbstractButton save = Components.withText(frame, I18n.t("route.ui.frameSave"));

            assertFalse(Components.onTheEventThread(save::isEnabled), "Save is offered on a route the station owns");

            Components.press(Components.withText(frame, I18n.t("route.ui.frameCancel")));

            assertTrue(Components.waitFor(() -> !frame.isDisplayable(), 10000), "Cancel left the station's route open");
            assertEquals(answering.asked().toString(), "[]", "Cancel on a route nobody could change asked a question");
        }
        finally
        {
            closeEditors();
        }

        assertEquals(everythingOf(model().getRoute(SHUTTLE)), before, "looking at the station's route changed it");
    }

    /** Add Route on the Routes tab, and the new route's name typed over the one it offers; its editor. */
    private static RouteEditorFrame addRoute(String name)
    {
        javax.swing.AbstractButton add = Components.withText(ui(), I18n.t("ui.main.addRoute"));

        Components.select(add);
        Components.press(add);

        assertTrue(Components.waitFor(() -> editorShowing() != null, 10000), "Add Route opened no route editor");

        RouteEditorFrame frame = editorShowing();

        typeInto(beside(frame, "route.ui.frameName", JTextField.class), name);

        return frame;
    }

    /** Presses Save expecting its refusal, goes back to fix it, and gives back what the refusal said. */
    private static String refusal(RouteEditorFrame frame) throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("route.ui.frameGoAndFixIt"))))
        {
            Components.press(Components.withText(frame, I18n.t("route.ui.frameSave")));

            assertTrue(answering.awaitAsked(1, 10000), "Save took a route it should refuse");

            Components.settle();

            assertTrue(answering.asked().get(0).startsWith(I18n.t("route.ui.frameCannotSaveYet")), "Save asked something"
                + " other than what stops it: " + answering.asked());

            return answering.asked().get(0);
        }
    }

    private static MarklinControlStation model()
    {
        return restart.getModel();
    }

    private static TrainControlUI ui()
    {
        return (TrainControlUI) model().getGUI();
    }

    private static void refresh()
    {
        Components.onTheEventThread(() ->
        {
            ui().refreshRouteList();

            return null;
        });

        Components.settle();
    }

    private static void clearOurRoutes(MarklinControlStation on)
    {
        for (String name : new ArrayList<>(on.getRouteList()))
        {
            if (name.startsWith(PREFIX)) on.deleteRoute(name);
        }
    }
}
