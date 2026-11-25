package support;

import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.AbstractButton;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * The Routes tab's route list as the operator works it: a route's cell found, clicked or right-clicked, its menu's
 * Edit Route opening the route editor, and Duplicate Route's worker waited out.  Clicks are posted, so a question one
 * asks cannot hold the test's thread; answer it with {@link Answering}.
 */
public final class RouteListDoors
{
    private RouteListDoors()
    {
    }

    /** The route list's table: the one holding routes. */
    public static JTable table(TrainControlUI ui)
    {
        JTable table = Components.find(ui, JTable.class, t ->
        {
            for (int row = 0; row < t.getRowCount(); row++)
            {
                for (int column = 0; column < t.getColumnCount(); column++)
                {
                    if (t.getValueAt(row, column) instanceof org.traincontrol.base.Route) return true;
                }
            }

            return false;
        });

        if (table == null) throw new AssertionError("the window has no table of routes");

        return table;
    }

    /** A route's cell in the list, or null. */
    public static Rectangle cellOf(final TrainControlUI ui, final String route)
    {
        return Components.onTheEventThread(() ->
        {
            JTable table = table(ui);

            for (int row = 0; row < table.getRowCount(); row++)
            {
                for (int column = 0; column < table.getColumnCount(); column++)
                {
                    Object value = table.getValueAt(row, column);

                    if (value instanceof org.traincontrol.base.Route
                        && route.equals(((org.traincontrol.base.Route) value).getName()))
                    {
                        return table.getCellRect(row, column, true);
                    }
                }
            }

            return null;
        });
    }

    /**
     * Brings the route list forward and sends a mouse event to a route's cell, posted so a question the click asks
     * cannot hold this thread.
     */
    public static void click(TrainControlUI ui, String route, boolean onThePlayButton, final int kind, boolean popup)
    {
        final JTable table = table(ui);

        Components.select(table);

        Components.settle();

        Rectangle cell = cellOf(ui, route);

        if (cell == null) throw new AssertionError("precondition: the route " + route + " is not in the route list");

        final java.awt.Point at = onThePlayButton
            ? new java.awt.Point((int) TrainControlUI.routePlayButtonBox(cell).getCenterX(), (int) cell.getCenterY())
            : new java.awt.Point(cell.x + 6, (int) cell.getCenterY());

        final int button = popup ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1;
        final int mask = popup ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
        final int modifiers = kind == MouseEvent.MOUSE_CLICKED ? 0 : mask;

        SwingUtilities.invokeLater(() -> table.dispatchEvent(new MouseEvent(table, kind,
            System.currentTimeMillis(), modifiers, at.x, at.y, 1, popup, button)));
    }

    /**
     * Right-clicks a route's cell as Windows does, the popup trigger on the release and then a click, posted to the
     * window so the menu's own listener on the list is part of what is tested, and gives back the route's menu.
     */
    public static JPopupMenu rightClick(TrainControlUI ui, String route)
    {
        // A menu left open would take the press, and be found below as if this click had opened it
        Mouse.closeMenus();

        Components.select(table(ui));

        Components.settle();

        rightButton(ui, route, MouseEvent.MOUSE_PRESSED, false);
        rightButton(ui, route, MouseEvent.MOUSE_RELEASED, true);
        rightButton(ui, route, MouseEvent.MOUSE_CLICKED, false);

        Components.settle();

        if (!Components.waitFor(() -> Components.popupShowing() != null, 5000))
        {
            throw new AssertionError("a right-click on the route opened no menu");
        }

        final JPopupMenu menu = Components.popupShowing();

        // The route's own menu, or a message saying which one was found instead
        String found = Components.onTheEventThread(() ->
        {
            StringBuilder out = new StringBuilder(menu.getClass().getName()).append(':');

            for (java.awt.Component item : menu.getComponents())
            {
                out.append(' ').append(item instanceof javax.swing.JMenuItem ? ((javax.swing.JMenuItem) item).getText()
                    : "|");
            }

            return out.toString();
        });

        if (!found.contains(I18n.t("route.ui.menuExecute"))
            || !found.contains(" " + route + " (" + I18n.t("route.ui.id") + ": "))
        {
            throw new AssertionError("a right-click on " + route + " opened a menu that is not the route's: " + found);
        }

        return menu;
    }

    /** One event of a right-click on a route's cell, through the window and the system queue, as the OS sends it. */
    private static void rightButton(TrainControlUI ui, String route, final int kind, final boolean popup)
    {
        final JTable table = table(ui);

        Rectangle cell = cellOf(ui, route);

        if (cell == null) throw new AssertionError("precondition: the route " + route + " is not in the route list");

        final java.awt.Point at = new java.awt.Point(cell.x + 6, (int) cell.getCenterY());

        final int modifiers = kind == MouseEvent.MOUSE_PRESSED ? InputEvent.BUTTON3_DOWN_MASK : 0;

        Components.onTheEventThread(() ->
        {
            // In sight first, as a route the user clicks is: the window hands the click to what is under it
            table.scrollRectToVisible(new Rectangle(at.x, at.y, 1, 1));

            Window window = SwingUtilities.getWindowAncestor(table);
            java.awt.Point there = SwingUtilities.convertPoint(table, at, window);

            java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new MouseEvent(window, kind,
                System.currentTimeMillis(), modifiers, there.x, there.y, 1, popup, MouseEvent.BUTTON3));

            return null;
        });
    }

    /**
     * Opens the route's editor from its right-click menu, by the item saying `itemKey` (Edit Route, or View Route
     * Details on a station's route), and gives back the window that opened.
     */
    public static Window open(TrainControlUI ui, String route, String itemKey)
    {
        final AbstractButton edit = Components.withText(rightClick(ui, route), I18n.t(itemKey));

        if (edit == null) throw new AssertionError("the route's right-click menu has no " + itemKey);

        final int before = editorsShowing().size();

        SwingUtilities.invokeLater(edit::doClick);

        if (!Components.waitFor(() -> editorsShowing().size() > before, 10000))
        {
            throw new AssertionError(itemKey + " opened no route editor on " + route);
        }

        Mouse.closeMenus();

        return editorsShowing().get(editorsShowing().size() - 1);
    }

    /** Opens the route's editor with Edit Route, and gives back the window titled as Edit Route titles it. */
    public static Window edit(TrainControlUI ui, String route)
    {
        final String title = I18n.f("route.ui.dialogEditRouteWithId", route, ui.getModel().getRouteId(route));

        Window opened = open(ui, route, "route.ui.menuEditRoute");

        if (!Components.waitFor(() -> editorsTitled(title) == 1, 10000))
        {
            throw new AssertionError("Edit Route opened no editor titled \"" + title + "\"");
        }

        if (!opened.isShowing() || !title.equals(((java.awt.Frame) opened).getTitle()))
        {
            throw new AssertionError("the editor titled \"" + title + "\" is gone");
        }

        return opened;
    }

    /** How many route editors are showing under this title. */
    public static int editorsTitled(String title)
    {
        int found = 0;

        for (Window window : Window.getWindows())
        {
            if ("RouteEditorWindow".equals(window.getClass().getSimpleName()) && window.isShowing()
                && title.equals(((java.awt.Frame) window).getTitle())) found++;
        }

        return found;
    }

    /** The route editors showing, oldest first. */
    public static java.util.List<Window> editorsShowing()
    {
        java.util.List<Window> out = new java.util.ArrayList<>();

        for (Window window : Window.getWindows())
        {
            if ("RouteEditorWindow".equals(window.getClass().getSimpleName()) && window.isShowing()) out.add(window);
        }

        return out;
    }

    /** What the route's right-click menu offers: Enable or Disable Auto Execution, as it reads, or null for neither. */
    public static String autoExecutionOffered(TrainControlUI ui, String route)
    {
        JPopupMenu menu = rightClick(ui, route);

        try
        {
            for (String key : new String[] {"route.ui.menuEnableAutoExecution", "route.ui.menuDisableAutoExecution"})
            {
                if (Components.withText(menu, I18n.t(key)) != null) return I18n.t(key);
            }

            return null;
        }
        finally
        {
            Mouse.closeMenus();
        }
    }

    /** Chooses Enable or Disable Auto Execution from the route's right-click menu, and waits for the route to follow. */
    public static void switchAutoExecution(TrainControlUI ui, String route, boolean on)
    {
        String item = I18n.t(on ? "route.ui.menuEnableAutoExecution" : "route.ui.menuDisableAutoExecution");
        AbstractButton found = Components.withText(rightClick(ui, route), item);

        if (found == null) throw new AssertionError("the right-click menu of " + route + " has no " + item);

        Mouse.choose((javax.swing.JMenuItem) found);

        if (!Components.waitFor(() -> armed(ui, route) == on, 10000))
        {
            throw new AssertionError(item + " did not reach " + route);
        }
    }

    private static boolean armed(TrainControlUI ui, String route)
    {
        org.traincontrol.base.Route r = ui.getModel().getRoute(route);

        return r != null && r.isEnabled();
    }

    /**
     * MT-1001's set-up and step 1, through the doors: two routes on sensors made, the Autonomy Settings tab's Toggle
     * specified routes ticked and only the first picked in its list, the box then left as `toggled` says, and by hand
     * the first disabled and the second enabled from their menus.  Closing deletes both and puts the routes' and the
     * list's settings back.
     */
    public static final class HandSwitched implements AutoCloseable
    {
        /** The route the list holds, and the one it leaves out. */
        private final String listed, unlisted;

        private final TrainControlUI ui;
        private final java.util.Map<String, Boolean> armedWere = new java.util.HashMap<>();
        private final boolean toggleWas;
        private final java.util.List<Integer> listWas;

        /**
         * Switches MT-1001's routes by hand, presses a run's `door` through `run`, and checks what R1's and R2's menus
         * offer once it has started: as the list holds them with the box `toggled`, as switched by hand without.
         */
        public static void runAndRead(TrainControlUI ui, boolean toggled, String door,
            java.util.concurrent.Callable<?> run) throws Exception
        {
            try (HandSwitched routes = new HandSwitched(ui, "MT-1001", toggled))
            {
                run.call();

                String enable = I18n.t("route.ui.menuEnableAutoExecution");
                String disable = I18n.t("route.ui.menuDisableAutoExecution");
                java.util.List<String> read = routes.offered();
                java.util.List<String> held = toggled ? java.util.Arrays.asList(disable, enable)
                    : java.util.Arrays.asList(enable, disable);

                if (!read.equals(held))
                {
                    throw new AssertionError("after " + door + (toggled ? ", R1 (listed) and R2 (not) do not read as the"
                        + " list holds them: " : " with Toggle specified routes unticked, R1 and R2 do not read as"
                        + " switched by hand: ") + read);
                }
            }
        }

        private HandSwitched(TrainControlUI ui, String prefix, boolean toggled) throws Exception
        {
            this.ui = ui;
            this.listed = prefix + " R1";
            this.unlisted = prefix + " R2";

            org.traincontrol.model.ViewListener model = ui.getModel();

            this.toggleWas = model.getAutoLayout().isActivateRoutes();
            this.listWas = new java.util.ArrayList<>(model.getAutoLayout().getActivateRouteIDs());

            for (String name : model.getRouteList()) this.armedWere.put(name, model.getRoute(name).isEnabled());

            int sensor = 4795;

            for (String name : new String[] {this.listed, this.unlisted})
            {
                if (!model.newRoute(name, new java.util.ArrayList<>(), sensor++,
                    org.traincontrol.base.Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null))
                {
                    throw new AssertionError("precondition: the route " + name + " was not made");
                }
            }

            Components.onTheEventThread(() ->
            {
                ui.routeListPanel().refreshRouteList();

                return null;
            });

            Components.settle();

            // A failure here still leaves no route behind, as `close` would not run
            try
            {
                listOnly(this.listed, toggled);

                switchAutoExecution(ui, this.listed, false);
                switchAutoExecution(ui, this.unlisted, true);
            }
            catch (Exception | Error e)
            {
                close();

                throw e;
            }
        }

        /** Ticks the box, clicks the route's row in the list, and leaves the box as `toggled` says. */
        private void listOnly(String route, boolean toggled) throws Exception
        {
            final javax.swing.JCheckBox box = (javax.swing.JCheckBox) Reflect.get(TrainControlUI.class, this.ui,
                "toggleSpecifiedRoutes");
            final javax.swing.JList<?> list = (javax.swing.JList<?>) Reflect.get(TrainControlUI.class, this.ui,
                "autoRouteList");
            final int id = this.ui.getModel().getRouteId(route);

            Components.select(box);

            tick(box, true);

            final Rectangle row = Components.onTheEventThread(() ->
            {
                for (int at = 0; at < list.getModel().getSize(); at++)
                {
                    Object r = list.getModel().getElementAt(at);

                    if (r instanceof org.traincontrol.base.Route && ((org.traincontrol.base.Route) r).getName().equals(route))
                    {
                        list.ensureIndexIsVisible(at);

                        return list.getCellBounds(at, at);
                    }
                }

                return null;
            });

            if (row == null) throw new AssertionError("precondition: the Autonomy Settings list has no " + route);

            Mouse.clickAt(list, new java.awt.Point((int) row.getCenterX(), (int) row.getCenterY()));

            if (!Components.waitFor(() -> java.util.Collections.singletonList(id).equals(
                this.ui.getModel().getAutoLayout().getActivateRouteIDs()), 10000))
            {
                throw new AssertionError("a click on " + route + " in the Autonomy Settings list did not list it alone: "
                    + this.ui.getModel().getAutoLayout().getActivateRouteIDs());
            }

            tick(box, toggled);
        }

        private void tick(javax.swing.JCheckBox box, boolean on)
        {
            if (this.ui.getModel().getAutoLayout().isActivateRoutes() == on) return;

            Mouse.click(box);

            if (!Components.waitFor(() -> this.ui.getModel().getAutoLayout().isActivateRoutes() == on, 10000))
            {
                throw new AssertionError("a click on Toggle specified routes did not " + (on ? "tick" : "untick") + " it");
            }
        }

        /** What the listed and the unlisted route's menus offer, in that order. */
        private java.util.List<String> offered()
        {
            return java.util.Arrays.asList(autoExecutionOffered(this.ui, this.listed),
                autoExecutionOffered(this.ui, this.unlisted));
        }

        @Override
        public void close()
        {
            org.traincontrol.model.ViewListener model = this.ui.getModel();

            model.deleteRoute(this.listed);
            model.deleteRoute(this.unlisted);

            model.getAutoLayout().setActivateRouteIDs(this.listWas);
            model.getAutoLayout().setActivateRoutes(this.toggleWas);

            for (java.util.Map.Entry<String, Boolean> was : this.armedWere.entrySet())
            {
                org.traincontrol.marklin.MarklinRoute r = (org.traincontrol.marklin.MarklinRoute) model.getRoute(was.getKey());

                if (r == null || r.isEnabled() == was.getValue()) continue;

                if (was.getValue()) r.enable(); else r.disable();
            }

            Components.onTheEventThread(() ->
            {
                this.ui.routeListPanel().refreshRouteList();

                return null;
            });
        }
    }

    /** Whether Duplicate Route's worker, which syncs with the station after the copy is made, is still going. */
    public static boolean duplicating()
    {
        for (StackTraceElement[] frames : Thread.getAllStackTraces().values())
        {
            for (StackTraceElement frame : frames)
            {
                if (frame.getMethodName().startsWith("lambda$duplicateRoute$")) return true;
            }
        }

        return false;
    }
}
