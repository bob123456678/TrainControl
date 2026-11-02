package ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JTable;
import javax.swing.JTextField;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.CommandRow;
import org.traincontrol.base.NodeAnd;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.NodeGroup;
import org.traincontrol.base.NodeOr;
import org.traincontrol.base.NodeRouteCommand;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.ThreeWaySwitch;
import org.traincontrol.gui.RouteEditorFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Keys;
import support.RouteListDoors;
import static support.RouteEditing.*;

/**
 * A saved route changed by hand, start to finish, and judged by what is left in the railway's model.  Each claim
 * starts from a yard entry route already in the model (two switches, a signal set green after two seconds, a three-way
 * point set left and a call to the yard's lighting route; "sensor 5 on, and either switch 20 turned or sensor 6 off";
 * firing when a train leaves sensor 7), opens it as the operator does, from the route list's Edit Route, and works it
 * through the editor's own components.
 *
 * The program runs with its window over a copy of the test layout; nothing is saved to its database.
 */
public class testChangingARouteByHand
{
    private static final String PREFIX = "CH ";
    private static final String LIGHTS = PREFIX + "Yard lights";

    private static support.Restart restart;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    private static support.YardRoute yard;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the route editor is a window");

        // Never restarted here: the class only needs the window over a sandbox, which this closes as one.
        restart = support.Restart.open(new java.io.File("test/test_layout"));
        model = restart.getModel();
        ui = (TrainControlUI) model.getGUI();

        yard = support.YardRoute.on(model);
        support.YardRoute.addLights(model, LIGHTS);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (restart == null) return;

        try
        {
            closeEditors();

            for (String name : new ArrayList<>(model.getRouteList()))
            {
                if (name.startsWith(PREFIX)) model.deleteRoute(name);
            }
        }
        finally
        {
            restart.close();
        }
    }

    /**
     * Scenario 2: the yard route reopened and changed.  The call to the lighting route is dragged by its handle to the
     * top, the signal moved up by Alt+Up, the second switch given another address and the first a straight setting;
     * the first switch is duplicated from its menu and the copy given its own address and turned; the call is deleted
     * by the Delete key and the three-way by its trash.  The conditions' group is lifted by Ungroup and the outline
     * split at sensor 6.  Saved, the model holds exactly the changed route, under the same name and id, and no other
     * route.
     *
     * MUTATION: in `CommandTable.duplicateRows`, insert the copies at `chosen.first()` rather than `chosen.last() + 1`,
     * and the copy, changed where Duplicate selected it, saves above its original.
     */
    @Test
    public void testAChangedRouteIsSavedUnderItsOwnId() throws Exception
    {
        final String name = aYardRoute("Yard entry");
        final int id = model.getRouteId(name);
        final int routes = model.getRouteList().size();
        final int switch3 = switchAt(model, 14);
        final int switch4 = switchAt(model, 16);

        RouteEditorFrame frame = edit(name);

        try
        {
            JTable commands = commandTable(frame);

            java.awt.Rectangle top = Components.onTheEventThread(() -> commands.getCellRect(0, 1, true));

            drag(commands, 4, new java.awt.Point(60, top.y + 3));
            selectLine(commands, 3);
            Keys.chord(commands, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.ALT_DOWN_MASK);

            assertEquals(shownTargets(commands), " " + LIGHTS + " " + yard.switch1 + " " + yard.signal + " "
                + yard.switch2 + " " + yard.threeWay, "precondition: the call dragged to the top and the signal moved up"
                + " past the second switch");

            type(commands, 3, COMMAND_TARGET, String.valueOf(switch3));
            pick(commands, 1, COMMAND_SETTING, "straight");

            // The copy is the row Duplicate selects, which is the one a user goes on to change.
            rightClickAndChoose(commands, 1, "route.ui.menuDuplicate");
            final int copy = Components.onTheEventThread(commands::getSelectedRow);
            type(commands, copy, COMMAND_TARGET, String.valueOf(switch4));
            pick(commands, copy, COMMAND_SETTING, "turn");

            selectLine(commands, 0);
            Keys.press(commands, java.awt.event.KeyEvent.VK_DELETE);
            clickCell(commands, 4, COMMAND_TRASH, 0, 1);

            JTable conditions = conditionTable(frame);

            selectLine(conditions, conditionLine(conditions, CommandRow.Kind.ACCESSORY, yard.conditionSwitch));
            press(frame, "route.ui.menuUngroup");
            selectLine(conditions, conditionLine(conditions, CommandRow.Kind.FEEDBACK, 6));
            press(frame, "route.ui.menuSplitHere");

            assertEquals(save(frame), "[]", "Save asked a question about an ordinary edit");
        }
        finally
        {
            closeEditors();
        }

        Route saved = model.getRoute(name);

        assertNotNull(saved, "the route is gone after Save: " + model.getRouteList());
        assertEquals(saved.getId(), id, "Save gave the changed route another id");
        assertEquals(commandsOf(saved), yard.switch1 + ",0 | " + switch4 + ",1 | " + yard.signal + ",0,2000 | "
            + switch3 + ",0", "the commands Save wrote: the first switch straight, its copy turned, the signal, the"
            + " readdressed switch");
        assertEquals(conditionsOf(saved.getConditions()), "(Feedback 5,1 & " + yard.conditionSwitch
            + ",1) | Feedback 6,0", "the conditions Save wrote, ungrouped and split at sensor 6");
        assertEquals(saved.getS88() + " " + saved.getTriggerType() + " " + saved.isEnabled(),
            "7 OCCUPIED_THEN_CLEAR true", "what the edit did not touch");
        assertEquals(model.getRouteList().size(), routes, "Save of an existing route added or lost a route");
    }

    /**
     * Scenario 3: what Undo takes back is not saved.  Three edits to the commands (an address typed, Alt+Down, the
     * trash on the signal) and three to the conditions (a sensor's address typed, All chosen on the group's caption,
     * Add Condition); then Ctrl+Z twice on the commands, Ctrl+Z once on the conditions, and Ctrl+Y once on the
     * commands.  Saved, the route holds the address and the move but not the deletion, and the two condition edits but
     * not the new condition: each table's keys reach its own edits only.
     *
     * MUTATION: in `CommandTable.noteEdit`, drop `history.remember(shown)`, and Ctrl+Z on the commands takes nothing
     * back, so the signal is saved deleted.
     */
    @Test
    public void testWhatIsUndoneIsNotSaved() throws Exception
    {
        final String name = aYardRoute("Undo yard");
        final int switch5 = switchAt(model, 18);

        RouteEditorFrame frame = edit(name);

        try
        {
            JTable commands = commandTable(frame);
            JTable conditions = conditionTable(frame);

            type(commands, 0, COMMAND_TARGET, String.valueOf(switch5));
            selectLine(commands, 0);
            Keys.chord(commands, java.awt.event.KeyEvent.VK_DOWN, java.awt.event.InputEvent.ALT_DOWN_MASK);
            clickCell(commands, 2, COMMAND_TRASH, 0, 1);

            type(conditions, conditionLine(conditions, CommandRow.Kind.FEEDBACK, 5), CONDITION_TARGET, "8");
            chooseRule(conditions, 1, "route.ui.conditionCaptionAll");
            press(frame, "route.ui.menuAddCondition");

            final String all = "<" + I18n.t("route.ui.conditionCaptionAll") + ">";
            final String edited = " " + all + " 8 " + all + " " + yard.conditionSwitch + " 6";
            final String restored = " " + switch5 + " " + yard.switch2 + " " + yard.signal + " " + yard.threeWay + " "
                + LIGHTS;

            Keys.chord(commands, java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK);
            Keys.chord(commands, java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK);

            assertEquals(shownTargets(commands) + " /" + shownConditions(conditions), restored + " /" + edited + " 1",
                "two Ctrl+Z on the commands: their move and deletion taken back, every condition edit still showing (the"
                + " new one on sensor 1)");

            Keys.chord(conditions, java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK);

            assertEquals(shownTargets(commands) + " /" + shownConditions(conditions), restored + " /" + edited,
                "Ctrl+Z on the conditions: the added condition taken back, the commands as they were");

            Keys.chord(commands, java.awt.event.KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_DOWN_MASK);

            assertEquals(save(frame), "[]", "Save asked a question about an ordinary edit");
        }
        finally
        {
            closeEditors();
        }

        Route saved = model.getRoute(name);

        assertEquals(commandsOf(saved), yard.switch2 + ",0 | " + switch5 + ",1 | " + yard.signal + ",0,2000 | "
            + (yard.threeWay + 1) + ",0,300 | " + yard.threeWay + ",1 | Route " + LIGHTS, "the commands Save wrote: the"
            + " new address moved down one, and the signal's deletion taken back");
        assertEquals(conditionsOf(saved.getConditions()), "Feedback 8,1 & (" + yard.conditionSwitch
            + ",1 & Feedback 6,0)", "the conditions Save wrote: sensor 8, the group's rule All, and the added condition"
            + " taken back");
    }

    /**
     * Scenario 4: Cancel keeps the route.  The yard route is renamed, made manual, readdressed, reordered, cut and added
     * to, and its conditions ungrouped, added to and given another rule; Cancel asks whether to throw the changes away,
     * and Yes closes the editor.  The model's route is as it was, no route has the new name, and Edit Route opens it
     * again showing the original: its name, its sensor, Automatically Fire ticked, its commands' targets and its
     * conditions.  Cancel there closes without asking.
     *
     * MUTATION: in `RouteEditorFrame.build`, give Cancel `this::onSave` rather than `this::closeIfThrowingNothingAway`,
     * and Cancel saves the edits.
     */
    @Test
    public void testCancelKeepsTheRouteAsItWasSaved() throws Exception
    {
        final String name = aYardRoute("Cancel yard");
        final String before = everythingOf(model.getRoute(name));

        RouteEditorFrame frame = edit(name);

        try
        {
            JTable commands = commandTable(frame);
            JTable conditions = conditionTable(frame);

            typeInto(beside(frame, "route.ui.frameName", JTextField.class), name + " renamed");
            Components.press(Components.withText(frame, I18n.t("route.ui.frameEnabled")));
            Components.settle();

            type(commands, 1, COMMAND_TARGET, String.valueOf(switchAt(model, 90)));
            drag(commands, 0, new java.awt.Point(60, Components.onTheEventThread(() ->
                commands.getCellRect(4, 1, true).y + commands.getRowHeight() - 2)));
            selectLine(commands, 2);
            Keys.press(commands, java.awt.event.KeyEvent.VK_DELETE);
            pressOnTheCommands(frame, "route.ui.menuAddCommand");

            selectLine(conditions, conditionLine(conditions, CommandRow.Kind.FEEDBACK, 6));
            press(frame, "route.ui.menuUngroup");
            press(frame, "route.ui.menuAddCondition");
            chooseRule(conditions, 0, "route.ui.conditionCaptionAny");

            try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
            {
                Components.press(Components.withText(frame, I18n.t("route.ui.frameCancel")));

                assertTrue(answering.awaitAsked(1, 10000), "Cancel over a heavily edited route asked nothing");
                assertEquals(answering.asked().get(0), I18n.t("route.ui.confirmDiscardChanges"), "Cancel's question");
                assertTrue(Components.waitFor(() -> !frame.isDisplayable(), 10000), "Yes did not close the editor");
            }
        }
        finally
        {
            closeEditors();
        }

        assertEquals(everythingOf(model.getRoute(name)), before, "Cancel changed the saved route");
        assertNull(model.getRoute(name + " renamed"), "Cancel saved the route under its new name");

        RouteEditorFrame again = edit(name);

        try
        {
            JTable conditions = conditionTable(again);
            String shown = Components.onTheEventThread(() ->
                beside(again, "route.ui.frameName", JTextField.class).getText() + " s88 "
                + beside(again, "route.ui.frameS88", JTextField.class).getText() + " "
                + Components.withText(again, I18n.t("route.ui.frameEnabled")).isSelected() + " /"
                + shownTargets(commandTable(again)) + " /" + shownConditions(conditions));

            assertEquals(shown, name + " s88 7 true / " + yard.switch1 + " " + yard.switch2 + " " + yard.signal + " "
                + yard.threeWay + " " + LIGHTS + " / <" + I18n.t("route.ui.conditionCaptionAll") + "> 5 <"
                + I18n.t("route.ui.conditionCaptionAny") + "> " + yard.conditionSwitch + " 6",
                "the route opened again after Cancel");

            try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.no"))))
            {
                Components.press(Components.withText(again, I18n.t("route.ui.frameCancel")));

                assertTrue(Components.waitFor(() -> !again.isDisplayable(), 10000), "Cancel left the unchanged route"
                    + " open: " + answering.asked());
                assertEquals(answering.asked().toString(), "[]", "Cancel on an unchanged route asked a question");
            }
        }
        finally
        {
            closeEditors();
        }
    }

    /**
     * Scenario 6: a route duplicated from the route list, and the copy changed.  The copy is the original's commands and
     * conditions, manual; it is renamed, made to fire on sensor 11, its first switch given another address and set
     * straight, its call deleted by the trash and its group's rule made All, and saved.  The copy holds exactly that,
     * under an id of its own, and the original is as it was.
     *
     * MUTATION: in `RouteListPanel.duplicateRoute`, pass `currentRoute.isEnabled()` to `newRoute` rather than `false`,
     * and the copy fires on the original's sensor before it is touched.
     */
    @Test
    public void testADuplicatedRouteIsChangedWithoutTouchingTheOriginal() throws Exception
    {
        final String name = aYardRoute("Junction");
        final String copy = name + " (" + I18n.t("route.ui.copy") + " 1)";
        final String west = name + " west";
        final String original = everythingOf(model.getRoute(name));
        final int switch6 = switchAt(model, 70);

        javax.swing.JPopupMenu menu = RouteListDoors.rightClick(ui, name);

        Components.press(Components.withText(menu, I18n.t("route.ui.menuDuplicateRoute")));

        assertTrue(Components.waitFor(() -> model.getRoute(copy) != null, 30000), "Duplicate Route made no " + copy);
        assertTrue(Components.waitFor(() -> !RouteListDoors.duplicating(), 60000), "Duplicate Route's worker was still"
            + " going a minute on");
        assertTrue(Components.waitFor(() -> RouteListDoors.cellOf(ui, copy) != null, 10000), "the route list does not"
            + " show the copy");

        Route made = model.getRoute(copy);

        assertEquals(commandsOf(made) + " / " + conditionsOf(made.getConditions()) + " / " + made.getS88() + " "
            + made.isEnabled(), commandsOf(model.getRoute(name)) + " / " + conditionsOf(model.getRoute(name)
            .getConditions()) + " / 7 false", "the copy before it is touched: the original's commands and conditions,"
            + " manual");

        RouteEditorFrame frame = edit(copy);

        try
        {
            JTable commands = commandTable(frame);

            typeInto(beside(frame, "route.ui.frameName", JTextField.class), west);
            Components.press(Components.withText(frame, I18n.t("route.ui.frameEnabled")));
            Components.settle();
            typeInto(beside(frame, "route.ui.frameS88", JTextField.class), "11");

            type(commands, 0, COMMAND_TARGET, String.valueOf(switch6));
            pick(commands, 0, COMMAND_SETTING, "straight");
            clickCell(commands, 4, COMMAND_TRASH, 0, 1);
            chooseRule(conditionTable(frame), 1, "route.ui.conditionCaptionAll");

            assertEquals(save(frame), "[]", "Save asked a question about the copy");
        }
        finally
        {
            closeEditors();
        }

        Route saved = model.getRoute(west);

        assertNotNull(saved, "the renamed copy is not in the model: " + model.getRouteList());
        assertNull(model.getRoute(copy), "the copy is still there under its first name");
        assertNotEquals(saved.getId(), model.getRouteId(name), "the copy has the original's id");
        assertEquals(commandsOf(saved) + " / " + conditionsOf(saved.getConditions()) + " / " + saved.getS88() + " "
            + saved.isEnabled(), switch6 + ",0 | " + yard.switch2 + ",0 | " + yard.signal + ",0,2000 | "
            + (yard.threeWay + 1) + ",0,300 | " + yard.threeWay + ",1 / Feedback 5,1 & (" + yard.conditionSwitch
            + ",1 & Feedback 6,0) / 11 true",
            "the copy as Save wrote it");
        assertEquals(everythingOf(model.getRoute(name)), original, "changing the copy changed the original");
    }

    /**
     * The yard entry route, stored in the model under the prefix and shown in the route list; its name.  Its signal is
     * green after two seconds, its three-way set left, and it fires when a train leaves sensor 7 with "sensor 5 on, and
     * either switch 20 turned or sensor 6 off".
     */
    private static String aYardRoute(String called)
    {
        final String name = PREFIX + called;

        if (model.getRoute(name) != null) model.deleteRoute(name);

        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(yard.switch1, Accessory.accessoryDecoderType.MM2, true));
        commands.add(RouteCommand.RouteCommandAccessory(yard.switch2, Accessory.accessoryDecoderType.MM2, false));

        RouteCommand green = RouteCommand.RouteCommandAccessory(yard.signal, Accessory.accessoryDecoderType.MM2, false);

        green.setDelay(2000);
        commands.add(green);
        commands.addAll(ThreeWaySwitch.expand(yard.threeWay, Accessory.accessoryDecoderType.MM2,
            ThreeWaySwitch.Position.LEFT, ThreeWaySwitch.SETTLE));
        commands.add(RouteCommand.RouteCommandRoute(LIGHTS));

        NodeExpression conditions = new NodeAnd(new NodeRouteCommand(RouteCommand.RouteCommandFeedback(5, true)),
            new NodeGroup(Arrays.asList((NodeExpression) new NodeOr(new NodeRouteCommand(
            RouteCommand.RouteCommandAccessory(yard.conditionSwitch, Accessory.accessoryDecoderType.MM2, true)),
            new NodeRouteCommand(RouteCommand.RouteCommandFeedback(6, false))))));

        assertTrue(model.newRoute(name, commands, 7, Route.s88Triggers.OCCUPIED_THEN_CLEAR, true, conditions),
            "precondition: the route " + name + " was not made");

        assertEquals(conditionsOf(model.getRoute(name).getConditions()), "Feedback 5,1 & (" + yard.conditionSwitch
            + ",1 | Feedback 6,0)", "precondition: the model did not store the conditions as written");

        Components.onTheEventThread(() ->
        {
            ui.refreshRouteList();

            return null;
        });

        Components.settle();

        return name;
    }

    /** Opens a route's editor from the route list's Edit Route. */
    private static RouteEditorFrame edit(String name)
    {
        RouteEditorFrame frame = (RouteEditorFrame) RouteListDoors.edit(ui, name);

        Components.settle();

        return frame;
    }

    /** The commands' targets as the table shows them: " 12 13 16 30 CH Yard lights". */
    private static String shownTargets(JTable commands)
    {
        return Components.onTheEventThread(() ->
        {
            StringBuilder out = new StringBuilder();

            for (int line = 0; line < commands.getRowCount(); line++)
            {
                out.append(" ").append(commands.getValueAt(line, COMMAND_TARGET));
            }

            return out.toString();
        });
    }

    /** The conditions as the table shows them, top to bottom, each caption as its rule: " <All> 5 <Any> 20 6". */
    private static String shownConditions(JTable conditions)
    {
        List<Integer> captions = captionLines(conditions);
        StringBuilder out = new StringBuilder();

        for (int line = 0; line < Components.onTheEventThread(conditions::getRowCount); line++)
        {
            final int at = line;

            out.append(" ").append(captions.contains(line) ? "<" + captionText(conditions, captions.indexOf(line)) + ">"
                : Components.onTheEventThread(() -> conditions.getValueAt(at, CONDITION_TARGET)));
        }

        return out.toString();
    }
}
