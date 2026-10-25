package org.traincontrol.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import org.traincontrol.base.Route;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;

/**
 * The route list on the Routes tab: running a route (its play button, a click on its row, and every other door, which
 * all come through `executeRoute`), editing and duplicating one, arming or disarming them in bulk, finding one by
 * name, and refreshing the list.
 *
 * The list's cell renderer (`TrainControlUI.CustomTableRenderer`) is a public class of the window and stays there, as
 * do the hover handlers and every field.
 */
final class RouteListPanel
{
    private final TrainControlUI ui;

    RouteListPanel(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * `TrainControlUI.executeRoute`'s body.
     */
    void executeRoute(String route)
    {
        // The same answer at all three doors: the play button grays itself while `routesExecuting` holds the name, and
        // the other two (the row click with its confirmation, and the right-click menu) did not ask, so one gesture was
        // refused at one door and accepted at two (the OB-057/OB-090 shape).  A debounce, not a lock: `execRoute`
        // returns once it has spawned the route thread, so the name is cleared 600 ms later, and `Route.setExecuting()`
        // already stops two threads.  Logged rather than silent, because the operator confirmed a dialog and is owed a
        // reason.
        if (route != null && this.ui.routesExecuting.contains(route))
        {
            this.ui.getModel().log(I18n.f("route.ui.infoAlreadyRunning", route));

            return;
        }

        // Marked before the thread starts, so the spinner is up by the time the pointer leaves the button (FR-043).
        // Every door that runs a route comes through here, including the right-click menu's own Execute (Adam:
        // "executing the route from there should also start the spinner.").
        this.ui.routeStarted(route);

        new Thread(() ->
        {
            runAndTimeTheRoute(route, () ->
            {
                // Asked here rather than inside the model, which has no business showing a dialog, and before the run,
                // so the answer is about the route the operator picked.  Every route the station holds is a
                // MarklinRoute, the only kind that knows the autonomy graph (the model is held as a ViewListener, whose
                // getRoute is typed as the base Route).
                org.traincontrol.base.Route found = this.ui.getModel().getRoute(route);

                org.traincontrol.marklin.MarklinRoute picked =
                    found instanceof org.traincontrol.marklin.MarklinRoute
                    ? (org.traincontrol.marklin.MarklinRoute) found : null;

                // Asked once, and the answer decides how it runs (A3): a conflict checked here and again inside, one
                // that cleared between, comes back as "yes, override" and runs the route with every guard off.  Only
                // the operator's own yes overrides anything.
                if (picked != null)
                {
                    TrainControlUI.RouteConflict answer = this.ui.askAboutRouteConflict(picked, this.ui, null);

                    if (answer == TrainControlUI.RouteConflict.OVERRIDE)
                    {
                        picked.execRouteOverridingConflicts();

                        refreshRouteList();

                        return;
                    }

                    // Said out loud, as the midway door says it (MT-247): the operator confirmed a dialog and is owed a
                    // reason why nothing happened.  The two cancels differ in size: a REFUSED here happens before the
                    // route starts, so not one command goes out, whereas the midway question is answered inside
                    // `MarklinRoute.execRoute`'s command loop, so every earlier command has gone out and stands;
                    // behaviour/routes.md section 7a carries both halves.
                    if (answer == TrainControlUI.RouteConflict.REFUSED)
                    {
                        this.ui.getModel().logf("route.cancelledByOperator", picked.getName());
                    }

                    if (answer == TrainControlUI.RouteConflict.REFUSED)
                    {
                        refreshRouteList();

                        return;
                    }
                }

                // Nothing to confirm: run it the ordinary way, guarded, so a conflict appearing while it
                // runs still stops and asks.
                this.ui.getModel().execRoute(route);
                refreshRouteList();
            });
        }).start();
    }

    /**
     * The three ways out of executeRoute's thread, wrapped so all of them are timed: a try/finally around the body
     * would miss the two early returns (an overridden conflict and a refused one) and measure the happy path only.  It
     * puts the button back on the last line of the finally, through `releaseTheRoutePlayButton`, which holds the gray
     * for a minimum time.
     *
     * @param route the route's name
     * @param work what to run
     */
    private void runAndTimeTheRoute(String route, Runnable work)
    {
        long began = System.currentTimeMillis();

        try
        {
            work.run();
        }
        finally
        {
            // What this measures is the dialog and the spawn, not the route: `work.run()` ends at
            // `model.execRoute(route)`, which starts a thread, so this returns milliseconds later whatever the route is
            // still doing.
            if (this.ui.getModel() != null && this.ui.getModel().isDebug())
            {
                this.ui.getModel().log("Route " + route + " finished executing in "
                    + (System.currentTimeMillis() - began) + "ms");
            }

            this.ui.releaseTheRoutePlayButton(route);
        }
    }

    /**
     * `TrainControlUI.editRoute`'s body.
     */
    void editRoute(String routeName)
    {
        new Thread(()->
        {  
            Route asked = routeName == null ? null : this.ui.getModel().getRoute(routeName);

            // Looked up once: testing one lookup and dereferencing a second let a route deleted between the two throw
            // on a worker.
            if (asked != null)
            {
                {
                    Route currentRoute = asked;
                    
                    // Realized on the EDT: the model lookups and toCSV stay off it, but constructing, packing and
                    // showing a JFrame is not thread safe, and the command-capture path writes this window's document
                    // from the EDT while it is still being built.
                    final String title = I18n.f(
                        "route.ui.dialogEditRouteWithId",
                        routeName,
                        currentRoute.getId()
                    );

                    if (this.ui.refuseWhileEditorOpen()) return;

                    javax.swing.SwingUtilities.invokeLater(() ->
                    {
            // Checked on the EDT, immediately before constructing, so the two cannot be separated: with the check on
            // the worker and the construction posted, two quick clicks both saw a null field and opened two editors,
            // with captured commands going to the one assigned last.
                        if (this.ui.routeEditor != null && this.ui.routeEditor.isVisible())
                        {
                            JOptionPane.showMessageDialog(
                                this.ui,
                                I18n.t("route.ui.errorOnlyOneRouteEditorAllowed")
                            );

                            this.ui.routeEditor.requestFocus();
                            this.ui.routeEditor.toFront();

                            return;
                        }

                        // The route itself, rather than its name to look up again: this window is handed what it is to
                        // show, which makes its read-only behavior testable without a control station on the network.
                        this.ui.routeEditor = new RouteEditorFrame(this.ui, routeName, currentRoute);

                        // Not over a locked route's own title: a route from the Central Station opens with every
                        // control grayed and its title is the only thing that says why.
                        if (!this.ui.routeEditor.isLocked()) this.ui.routeEditor.setTitle(title);
                        this.ui.routeEditor.setVisible(true);
                    });
                }
            }
        }).start();
    }

    /**
     * `TrainControlUI.duplicateRoute`'s body.
     */
    void duplicateRoute(String routeName)
    {
        new Thread(()->
        {  
            if (routeName != null)
            {
                Route currentRoute = this.ui.getModel().getRoute(routeName);

                if (currentRoute != null && currentRoute instanceof Route)
                {
                    String proposedName = currentRoute.getName() + " (" + I18n.t("route.ui.copy") + " %s)";

                    int i = 1;

                    while (this.ui.getModel().getRoute(String.format(proposedName, i)) != null)
                    {
                        i++;
                    }

                    this.ui.getModel().newRoute(String.format(proposedName, i), currentRoute.getRoute(), 
                            currentRoute.getS88(), currentRoute.getTriggerType(), false, currentRoute.getConditions()); 

                    // The list first, and again after the sync: the copy exists the moment newRoute returns, and the
                    // sync is a network round trip that can throw off the event thread (a station not answering ended
                    // this worker before the refresh ran).
                    refreshRouteList();

                    try
                    {
                        // Ensure route changes are synced
                        this.ui.syncWithCS2();
                    }
                    finally
                    {
                        // Whatever the station said, the copy is real and the list has to show it
                        this.ui.repaintLayout();

                        refreshRouteList();
                    }
                }  
            }
        }).start();
    }

    /**
     * `TrainControlUI.BulkEnableOrDisable`'s body.
     */
    void BulkEnableOrDisable(boolean enable)
    {
        // The question is asked on the event thread: a modal JOptionPane raised inside `new Thread(...)` pumps events
        // from a thread Swing does not expect and can come up behind the window or not at all.  The thread below stays
        // for what it was really for, a write per route and one station sync.
        String searchString = JOptionPane.showInputDialog(
            this.ui,
            I18n.f(
                "route.ui.promptEnterSearchStringWithS88State",
                enable ? I18n.t("ui.stateEnabled") : I18n.t("ui.stateDisabled")
            ),
            "*"
        );

        // Cancel returns null, which is not the same as an empty search: `!"".equals(null)` is true, so the loop ran and
        // getName().contains(null) threw on a worker, skipping the sync and refresh that follow it.
        if (searchString == null || "".equals(searchString)) return;

        this.ui.enableOrDisableMatching(searchString, enable);
    }

    /**
     * A click on the route list: the play button runs its route, and a row asks first - or, with the power off, opens the route's
     * editor and asks nothing (FR-140).  `TrainControlUI.RouteListMouseClicked`'s body.
     */
    void RouteListMouseClicked(java.awt.event.MouseEvent evt)
    {

        // Three answers, one for each way of clicking a route (FR-043, MT-217; Adam: "if play button is hit, route
        // executes without confirming.  If route is left-clicked elsewhere, pop up the confirmation first.  If
        // right-clicked, then show the right click menu."): the confirmation was removed when a left click anywhere ran
        // the route, and having to aim, which only the button asks, makes it unnecessary.
        this.ui.clearFoundRoute();

        Route route = this.ui.getRouteAtCursor(evt);

        if (route == null) return;

        if (!javax.swing.SwingUtilities.isLeftMouseButton(evt))
        {
            // The right-click menu is RightClickRouteMenu's own business - it listens for the popup
            // trigger on press and release, which is where a platform decides what a right click is.
            return;
        }

        if (isOverTheRoutePlayButton(evt))
        {
            // Disabled means disabled (MT-217): a grayed button that still fired would start the same route twice.
            // Asked in executeRoute now: this was the only door that asked, and one guard on the funnel is the
            // OB-057/OB-090 shape's answer.
            executeRoute(route.getName());

            return;
        }

        // WITH THE POWER OFF, THE ROW OPENS THE ROUTE'S EDITOR (FR-140): nothing could run, so the click is taken as
        // wanting to see or change the route.  The play button still runs it, as it always does.
        if (this.ui.getModel() != null && !this.ui.getModel().getPowerState())
        {
            editRoute(route.getName());

            return;
        }

        // We need to set this in case there are popup windows
        this.ui.setAlwaysOnTop(true);

        int dialogResult = JOptionPane.showOptionDialog(
            this.ui.routePanel(),
            I18n.f("route.ui.confirmExecuteRouteWithId", route.getName(),
                this.ui.getModel().getRouteId(route.getName())),
            I18n.t("route.ui.dialogExecution"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            TrainControlUI.YES_NO_OPTS,
            TrainControlUI.YES_NO_OPTS[0] // default selection
        );

        if (dialogResult == JOptionPane.YES_OPTION)
        {
            executeRoute(route.getName());
        }

        // Revert preference
        this.ui.windowAlwaysOnTopAsChosen();
    }

    /**
     * Whether a click landed on a cell's play button rather than on the cell.  The button's box is worked out from the
     * cell's own rectangle, as the renderer paints it at the right-hand end, not from anything the renderer remembers:
     * it is one component reused for every cell, so it would answer for whichever cell was painted last.
     *
     * @param evt the click
     * @return true if the pointer is over the button of a cell that has one
     */
    boolean isOverTheRoutePlayButton(java.awt.event.MouseEvent evt)
    {
        int row = this.ui.routeList().rowAtPoint(evt.getPoint());
        int column = this.ui.routeList().columnAtPoint(evt.getPoint());

        if (row < 0 || column < 0) return false;

        // An empty cell has no button: the last row is padded out to three columns with nulls.
        if (this.ui.routeList().getValueAt(row, column) == null) return false;

        java.awt.Rectangle cell = this.ui.routeList().getCellRect(row, column, true);

        java.awt.Rectangle button = TrainControlUI.routePlayButtonBox(cell);

        return button.contains(evt.getPoint());
    }

    /**
     * Asks for a route by name and goes to it (FR-044).  Adam: "'Find Route' popup that asks the user for the route
     * name.  system jumps to the route page and highlights the matched cell (scrolling to it if needed), otherwise
     * shows a notice that it doesn't exist."  Matched case-insensitively, and on a unique CONTAINS when nothing matches
     * exactly; ambiguity is never resolved by guessing (MT-218 asks).
     */
    void findRoute()
    {
        String asked = JOptionPane.showInputDialog(this.ui, I18n.t("route.ui.promptFindRoute"),
            I18n.t("route.ui.menuFindRoute"), JOptionPane.PLAIN_MESSAGE);

        // Cancel returns null, not an empty search, and neither is worth a "no such route" complaint about an
        // unanswered question.
        if (asked == null || asked.trim().isEmpty()) return;

        String wanted = asked.trim();

        List<String> matches = TrainControlUI.routesMatchingIn(this.ui.getModel().getRouteList(), wanted);

        if (matches.isEmpty())
        {
            JOptionPane.showMessageDialog(this.ui, I18n.f("route.ui.errorNoSuchRoute", wanted));

            return;
        }

        String found = matches.get(0);

        // More than one: ask, rather than guess or refuse (MT-218).
        if (matches.size() > 1)
        {
            found = (String) JOptionPane.showInputDialog(this.ui,
                I18n.t("route.ui.promptWhichRoute"), I18n.t("route.ui.menuFindRoute"),
                JOptionPane.PLAIN_MESSAGE, null, matches.toArray(), matches.get(0));

            // Canceled, which is not the same as choosing the first one.
            if (found == null) return;
        }

        this.ui.showRouteInTheList(found);
    }

    /**
     * `TrainControlUI.routeNamedIn`'s body.
     */
    static String routeNamedIn(List<String> names, String wanted)
    {
        List<String> matches = TrainControlUI.routesMatchingIn(names, wanted);

        return matches.size() == 1 ? matches.get(0) : null;
    }

    /**
     * `TrainControlUI.refreshRouteList`'s body.
     */
    void refreshRouteList()
    {
        // singlePass, not invokeLater: joins a pass already running rather than asking for another.
        TrainControlUI.singlePass(() ->
        {
            DefaultTableModel tableModel = new DefaultTableModel()
            {
                @Override
                public boolean isCellEditable(int row, int column)
                {
                    return false;
                }
            };

            tableModel.setColumnCount(TrainControlUI.ROUTE_UI_COLS);

            Object[] items = new Object[TrainControlUI.ROUTE_UI_COLS];

            List<String> names = this.ui.getModel().getRouteList();

            if (TrainControlUI.getPrefs().getBoolean(TrainControlUI.ROUTE_SORT_PREF, false))
            {
                Collections.sort(names);
            }
            
            // Collect the objects to store in the grid - no more strings
            List<Route> routes = names.stream().map(this.ui.getModel()::getRoute).collect(Collectors.toList());

            int i = 0;
            while (!routes.isEmpty())
            {
                items[i++] = routes.get(0);
                routes.remove(0);

                if (i == TrainControlUI.ROUTE_UI_COLS || routes.isEmpty())
                {
                    i = 0;

                    tableModel.addRow(items);

                    items = new Object[TrainControlUI.ROUTE_UI_COLS];
                }
            }

            this.ui.routeList().setModel(tableModel);
            this.ui.routeList().setShowGrid(false);  

            for (int j = 0; j < TrainControlUI.ROUTE_UI_COLS; j++)
            {
                this.ui.routeList().getColumnModel().getColumn(j).setCellRenderer(this.ui.new CustomTableRenderer());
            }

            // Update autonomy route list
            this.ui.updateAutoRouteList();

            // this.RouteList.setToolTipText("Left click route to execute, right click to edit");
        });
    }
}
