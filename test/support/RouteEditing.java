package support;

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.NodeAnd;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.NodeGroup;
import org.traincontrol.base.NodeOr;
import org.traincontrol.base.NodeRouteCommand;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.RouteEditorFrame;
import org.traincontrol.util.I18n;

/**
 * The route editor driven as a hand drives it: its tables found by kind, a cell clicked, a handle dragged, a cell edited
 * through its own editor (a drop-down picked, a field typed into and Enter pressed), a row's right-click menu opened and
 * an item chosen, a caption's rule picked, and the buttons under each table pressed.  And a saved route read back as
 * text, so a claim compares it with a route written out by hand.
 *
 * Mouse events are dispatched on the event thread as AWT sends them; a table line is asked of the table (`lineOf`),
 * since the conditions show no line for a joining word.
 */
public final class RouteEditing
{
    /** A command line's columns. */
    public static final int COMMAND_KIND = 2;
    public static final int COMMAND_TARGET = 3;
    public static final int COMMAND_SETTING = 5;
    public static final int COMMAND_DELAY = 7;
    public static final int COMMAND_TRASH = 8;
    public static final int COMMAND_COPY = 9;

    /** A condition line's columns. */
    public static final int CONDITION_KIND = 1;
    public static final int CONDITION_TARGET = 2;
    public static final int CONDITION_SETTING = 3;

    /** Every line's handle, and a column where a press neither edits nor moves on the lines these claims click. */
    public static final int HANDLE = 0;
    public static final int NOTHING_TO_EDIT = 4;

    private RouteEditing()
    {
    }

    /** The route editor's table of one class, by its simple name. */
    public static JTable table(RouteEditorFrame frame, final String kind)
    {
        JTable table = Components.find(frame, JTable.class, t -> kind.equals(t.getClass().getSimpleName()));

        if (table == null) throw new AssertionError("the route editor has no " + kind);

        return table;
    }

    public static JTable commandTable(RouteEditorFrame frame)
    {
        return table(frame, "CommandTable");
    }

    public static JTable conditionTable(RouteEditorFrame frame)
    {
        return table(frame, "ConditionTable");
    }

    /** A private method of a route editor table that takes one int, called as the table calls it. */
    public static Object call(Object table, String name, int argument)
    {
        try
        {
            java.lang.reflect.Method method = table.getClass().getDeclaredMethod(name, int.class);

            method.setAccessible(true);

            return method.invoke(table, argument);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("the condition table has no " + name + "(int)", e);
        }
    }

    /** The table line showing an outline row, asked of the table; -1 for a joining word, which has none (OB-396). */
    public static int screen(final JTable table, final int row)
    {
        return Components.onTheEventThread(() -> (Integer) call(table, "lineOf", row));
    }

    /** The same, as a click of `count` presses: 2 is a double-click. */
    public static void clickCell(final JTable table, final int line, final int column, final int modifiers,
        final int count) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            Rectangle cell = table.getCellRect(line, column, true);

            int x = cell.x + cell.width / 2;
            int y = cell.y + cell.height / 2;
            long now = System.currentTimeMillis();

            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_PRESSED, now,
                modifiers | InputEvent.BUTTON1_DOWN_MASK, x, y, count, false,
                MouseEvent.BUTTON1));
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_RELEASED, now + 1,
                modifiers, x, y, count, false, MouseEvent.BUTTON1));
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_CLICKED, now + 1,
                modifiers, x, y, count, false, MouseEvent.BUTTON1));
        });
    }

    /** Presses an outline row's handle, drags to a point of the table and lets go there, as the mouse sends it. */
    public static void drag(final JTable table, int row, final Point to) throws Exception
    {
        final int line = screen(table, row);

        SwingUtilities.invokeAndWait(() ->
        {
            Rectangle handle = table.getCellRect(line, HANDLE, true);
            int x = handle.x + handle.width / 2;
            int y = handle.y + 12;
            long now = System.currentTimeMillis();

            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_PRESSED, now,
                InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_DRAGGED, now + 1,
                InputEvent.BUTTON1_DOWN_MASK, x, y + 8, 0, false, MouseEvent.NOBUTTON));
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_DRAGGED, now + 2,
                InputEvent.BUTTON1_DOWN_MASK, to.x, to.y, 0, false, MouseEvent.NOBUTTON));
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_RELEASED, now + 3,
                0, to.x, to.y, 1, false, MouseEvent.BUTTON1));
        });

        Components.settle();
    }

    /** A right-click in one cell of the line showing an outline row. */
    public static JPopupMenu rightClickCell(final JTable table, int row, int column)
    {
        final int line = screen(table, row);
        Rectangle cell = Components.onTheEventThread(() -> table.getCellRect(line, column, true));

        return Mouse.rightClickAt(table, new Point(cell.x + cell.width / 2,
            cell.y + cell.height / 2), 5000);
    }

    /** A right-click below the last line, where the table has no line. */
    public static JPopupMenu rightClickBelow(final JTable table)
    {
        int height = Components.onTheEventThread(() -> table.getHeight());

        return Mouse.rightClickAt(table, new Point(40, height + 6), 5000);
    }

    /** A right-click on an outline row's line where no press edits anything, and the item chosen; it must be enabled. */
    public static void rightClickAndChoose(JTable table, int line, String key)
    {
        JPopupMenu menu = rightClickCell(table, line, NOTHING_TO_EDIT);

        if (menu == null) throw new AssertionError("a right-click on line " + line + " opened no menu");

        JMenuItem chosen = item(menu, key);

        if (!chosen.isEnabled()) throw new AssertionError(key + " is grayed on line " + line + ": " + offered(menu));

        Mouse.choose(chosen);
    }

    /** The menu's items as their message keys, each marked + when enabled and - when not; a divider as |. */
    public static String offered(final JPopupMenu menu)
    {
        String[] keys = {"route.ui.menuInsertConditionAbove", "route.ui.menuInsertConditionBelow",
            "route.ui.menuAddCondition", "route.ui.menuInsertCommandAbove", "route.ui.menuInsertCommandBelow",
            "route.ui.menuAddCommand", "route.ui.menuMoveUp", "route.ui.menuMoveDown", "route.ui.menuDuplicate",
            "route.ui.menuGroupSelected", "route.ui.menuUngroup", "route.ui.menuSplitHere", "ui.delete"};

        StringBuilder out = new StringBuilder();

        for (Component part : menu.getComponents())
        {
            if (part instanceof JPopupMenu.Separator) out.append(" |");

            if (!(part instanceof JMenuItem)) continue;

            JMenuItem item = (JMenuItem) part;
            String named = item.getText();

            for (String key : keys)
            {
                if (I18n.t(key).equals(item.getText())) named = key;
            }

            out.append(out.length() == 0 ? "" : " ").append(item.isEnabled() ? "+" : "-").append(named);
        }

        return out.toString();
    }

    public static JMenuItem item(JPopupMenu menu, String key)
    {
        for (Component part : menu.getComponents())
        {
            if (part instanceof JMenuItem && I18n.t(key).equals(((JMenuItem) part).getText()))
            {
                return (JMenuItem) part;
            }
        }

        throw new AssertionError("the menu has no " + key + ": " + offered(menu));
    }

    /** The table's caption lines, top to bottom. */
    public static List<Integer> captionLines(final JTable table)
    {
        return Components.onTheEventThread(() ->
        {
            List<Integer> out = new ArrayList<>();

            for (int line = 0; line < table.getRowCount(); line++)
            {
                if ((Boolean) call(table, "isCaptionLine", line)) out.add(line);
            }

            return out;
        });
    }

    public static String captionText(final JTable table, int caption)
    {
        final int line = captionLines(table).get(caption);

        return Components.onTheEventThread(() ->
        {
            java.lang.reflect.Method label = table.getClass().getDeclaredMethod("captionFor", int.class);

            label.setAccessible(true);

            return ((JLabel) label.invoke(table, line)).getText();
        });
    }

    /** Clicks the caption on a caption line and chooses one of its rules from the menu that opens. */
    public static void chooseRule(final JTable table, int caption, String key) throws Exception
    {
        final int line = captionLines(table).get(caption);
        final Rectangle pill = Components.onTheEventThread(() ->
        {
            java.lang.reflect.Method bounds = table.getClass().getDeclaredMethod("captionBounds", int.class);

            bounds.setAccessible(true);

            return (Rectangle) bounds.invoke(table, line);
        });

        SwingUtilities.invokeAndWait(() ->
        {
            int x = pill.x + pill.width / 2;
            int y = pill.y + pill.height / 2;
            long now = System.currentTimeMillis();

            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_PRESSED, now,
                InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_RELEASED, now + 1,
                0, x, y, 1, false, MouseEvent.BUTTON1));
        });

        Components.waitFor(() -> Components.popupShowing() != null, 5000);

        JPopupMenu menu = Components.popupShowing();

        if (menu == null) throw new AssertionError("a click on caption " + caption + " opened no menu");

        Mouse.choose(item(menu, key));
    }

    /** The section holding a table: its ancestor that sits beside the other table's section. */
    public static Container section(JTable table, JTable other)
    {
        Container out = table.getParent();

        while (out.getParent() != null && !out.getParent().isAncestorOf(other)) out = out.getParent();

        return out;
    }

    /** The commands' section of the window: their frame, the row under it, and Capture. */
    public static Container commandSection(final RouteEditorFrame frame)
    {
        return Components.onTheEventThread(() -> section(commandTable(frame), conditionTable(frame)));
    }

    /** The button saying this outside the commands' section: the conditions' Undo, not the commands'. */
    public static AbstractButton notOnTheCommands(final RouteEditorFrame frame, String key)
    {
        final Container commands = commandSection(frame);

        return Components.find(frame, AbstractButton.class, b -> I18n.t(key).equals(b.getText())
            && !commands.isAncestorOf(b));
    }

    /** The button in the commands' section saying this. */
    public static AbstractButton commandButton(final RouteEditorFrame frame, String key)
    {
        AbstractButton out = Components.withText(commandSection(frame), I18n.t(key));

        if (out == null) throw new AssertionError("the commands' section has no " + key);

        return out;
    }

    /** Presses a button of the route editor outside the commands' section by its text, and lets its work finish. */
    public static void press(final RouteEditorFrame frame, String key)
    {
        pressed(notOnTheCommands(frame, key), key + " is grayed", "the route editor has no button " + key);
    }

    public static void pressOnTheCommands(final RouteEditorFrame frame, String key)
    {
        pressed(commandButton(frame, key), key + " is grayed on the commands", "the commands' section has no " + key);
    }

    private static void pressed(AbstractButton button, String grayed, String missing)
    {
        if (button == null) throw new AssertionError(missing);

        if (!Components.onTheEventThread(button::isEnabled)) throw new AssertionError(grayed);

        Components.press(button);
        Components.settle();
    }

    // What these claims add: a cell edited through its own editor, and a line found by what it shows.

    /** The first line whose two columns show these values, or -1; a value is compared as the table returns it. */
    public static int lineShowing(final JTable table, final int column, final String value, final int otherColumn,
        final String other)
    {
        return Components.onTheEventThread(() ->
        {
            for (int line = 0; line < table.getRowCount(); line++)
            {
                if (value.equals(String.valueOf(table.getValueAt(line, column)))
                    && (other == null || other.equals(String.valueOf(table.getValueAt(line, otherColumn))))) return line;
            }

            return -1;
        });
    }

    /** Selects a line alone by a click on its handle, which selects and moves nothing. */
    public static void selectLine(JTable table, int line) throws Exception
    {
        clickCell(table, line, HANDLE, 0, 1);
    }

    /** Adds a line to the selection with a Shift-click on its handle. */
    public static void extendTo(JTable table, int line) throws Exception
    {
        clickCell(table, line, HANDLE, InputEvent.SHIFT_DOWN_MASK, 1);
    }

    /**
     * Opens a cell's drop-down and picks the item that is this value (the stored word or name the list holds), as a
     * click on it does; the pick commits the cell.
     */
    public static void pick(final JTable table, final int line, final int column, final Object value)
    {
        Components.onTheEventThread(() ->
        {
            JComboBox<?> box = editorOf(table, line, column, JComboBox.class);
            boolean offered = false;

            for (int at = 0; at < box.getItemCount(); at++) offered |= value.equals(box.getItemAt(at));

            if (!offered) throw new AssertionError("the drop-down on line " + line + " column " + column
                + " does not offer " + value);

            box.setSelectedItem(value);

            if (table.isEditing()) table.getCellEditor().stopCellEditing();

            return null;
        });

        Components.settle();
    }

    /** Opens a cell's text field as a double-click does, puts this text in it, and presses Enter there. */
    public static void type(final JTable table, final int line, final int column, final String text)
    {
        Keys.press(typeWithoutEnter(table, line, column, text), KeyEvent.VK_ENTER);

        if (Components.onTheEventThread(table::isEditing)) throw new AssertionError("Enter left line " + line
            + " column " + column + " being edited");
    }

    /** Opens a cell's text field as a double-click does and puts this text in it, leaving the edit open. */
    public static JTextComponent typeWithoutEnter(final JTable table, final int line, final int column,
        final String text)
    {
        return Components.onTheEventThread(() ->
        {
            JTextComponent editor = editorOf(table, line, column, JTextComponent.class);

            editor.setText(text);

            return editor;
        });
    }

    /** Starts editing a cell as a click on it does, on the event thread, and gives back its editor. */
    private static <T> T editorOf(JTable table, int line, int column, Class<T> type)
    {
        if (!table.editCellAt(line, column)) throw new AssertionError("line " + line + " column " + column
            + " cannot be edited");

        Component editor = table.getEditorComponent();

        if (!type.isInstance(editor)) throw new AssertionError("line " + line + " column " + column + " is edited by "
            + editor + ", not a " + type.getSimpleName());

        return type.cast(editor);
    }

    /** The component a label of the window's top line names, the first after it that is one of these. */
    public static <T extends Component> T beside(RouteEditorFrame frame, String labelKey, final Class<T> type)
    {
        final JLabel label = Components.find(frame, JLabel.class, l -> I18n.t(labelKey).equals(l.getText()));

        if (label == null) throw new AssertionError("the route editor has no label " + labelKey);

        return Components.onTheEventThread(() ->
        {
            Component[] row = label.getParent().getComponents();
            boolean after = false;

            for (Component part : row)
            {
                if (after && type.isInstance(part)) return type.cast(part);

                after |= part == label;
            }

            throw new AssertionError("nothing follows " + labelKey);
        });
    }

    /** The condition line of this kind at this address. */
    public static int conditionLine(JTable conditions, org.traincontrol.base.CommandRow.Kind kind, int address)
    {
        int line = lineShowing(conditions, CONDITION_TARGET, String.valueOf(address), CONDITION_KIND,
            org.traincontrol.base.CommandRow.labelFor(kind));

        if (line < 0) throw new AssertionError("no condition " + kind + " at " + address);

        return line;
    }

    /**
     * Presses Save, answering any question with its first button, and waits for the editor to close; gives back the
     * questions asked, "[]" when none.
     */
    public static String save(final RouteEditorFrame frame)
    {
        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.press(Components.withText(frame, I18n.t("route.ui.frameSave")));

            if (!Components.waitFor(() -> !frame.isDisplayable(), 10000))
            {
                throw new AssertionError("Save did not close the editor: " + answering.asked());
            }

            Components.settle();

            return answering.asked().toString();
        }
    }

    /** Puts text into a field as typing does. */
    public static void typeInto(final JTextComponent field, final String text)
    {
        Components.onTheEventThread(() ->
        {
            field.setText(text);

            return null;
        });
    }

    /** The route editor showing now, if one is. */
    public static RouteEditorFrame editorShowing()
    {
        return Components.onTheEventThread(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof RouteEditorFrame && window.isShowing()) return (RouteEditorFrame) window;
            }

            return null;
        });
    }

    /** Disposes every route editor, as a claim's cleanup. */
    public static void closeEditors()
    {
        Mouse.closeMenus();

        Components.onTheEventThread(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof RouteEditorFrame) window.dispose();
            }

            return null;
        });
    }

    /** An address no signal of the layout answers at, from a preferred one up, so a command there is a switch. */
    public static int switchAt(org.traincontrol.marklin.MarklinControlStation model, int preferred)
    {
        for (int address = preferred; ; address += 10)
        {
            boolean signal = false;

            for (Accessory.accessoryDecoderType type : Accessory.accessoryDecoderType.values())
            {
                Accessory at = model.getAccessoryByAddressIfPresent(address, type);

                signal |= at != null && at.isSignal();
            }

            if (!signal) return address;
        }
    }

    /** The lowest address at which the layout has a signal on the default protocol, or 0. */
    public static int aSignal(org.traincontrol.marklin.MarklinControlStation model)
    {
        for (int address = 1; address <= 320; address++)
        {
            Accessory at = model.getAccessoryByAddressIfPresent(address, Accessory.DEFAULT_IMPLICIT_PROTOCOL);

            if (at != null && at.isSignal()) return address;
        }

        return 0;
    }

    // A route read back as text: what a claim compares with a route written out by hand.

    /** A route's commands, each as its stored line: "12,1 | 13,0 | 31,0,300 | Route Yard lights". */
    public static String commandsOf(Route route)
    {
        StringBuilder out = new StringBuilder();

        for (RouteCommand command : route.getRoute())
        {
            out.append(out.length() == 0 ? "" : " | ").append(command.toLine(null).trim());
        }

        return out.toString();
    }

    /**
     * A condition expression with its groups as brackets: "Feedback 5,1 & (20,1 | Feedback 6,0)".  A run of one word
     * reads flat however the tree nests it; an "and" directly inside an "or" (or the other way) without a group around
     * it is shown in braces, since it would read the same as a bracket and mean something else.
     */
    public static String conditionsOf(NodeExpression node)
    {
        if (node == null) return "";

        if (node instanceof NodeRouteCommand) return ((NodeRouteCommand) node).getRouteCommand().toLine(null).trim();

        if (node instanceof NodeGroup)
        {
            StringBuilder out = new StringBuilder();

            for (NodeExpression inside : ((NodeGroup) node).getExpressions())
            {
                out.append(out.length() == 0 ? "" : " ").append(conditionsOf(inside));
            }

            return "(" + out + ")";
        }

        boolean and = node instanceof NodeAnd;
        NodeExpression left = and ? ((NodeAnd) node).getLeft() : ((NodeOr) node).getLeft();
        NodeExpression right = and ? ((NodeAnd) node).getRight() : ((NodeOr) node).getRight();

        return joined(left, and) + (and ? " & " : " | ") + joined(right, and);
    }

    private static String joined(NodeExpression side, boolean and)
    {
        boolean other = and ? side instanceof NodeOr : side instanceof NodeAnd;

        return other ? "{" + conditionsOf(side) + "}" : conditionsOf(side);
    }

    /** Everything a route says about itself, for a claim that it is unchanged. */
    public static String everythingOf(Route route)
    {
        if (route == null) return "(no route)";

        return route.getName() + " #" + route.getId() + " s88 " + route.getS88() + " " + route.getTriggerType()
            + (route.isEnabled() ? " automatic" : " manual") + (route.isLocked() ? " locked" : "") + " / "
            + commandsOf(route) + " / " + conditionsOf(route.getConditions());
    }
}
