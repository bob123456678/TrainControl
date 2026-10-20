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
        // The same answer at all three doors.  Not protection against two threads throwing one route's
        // accessories at once: `Route.setExecuting()` is a synchronized re-entrancy guard on the model's own funnel, so a second attempt has
        // always returned immediately.  What it IS: the play button greys itself while `routesExecuting` holds the name, and the other two
        // doors (the row click with its confirmation, and the right-click menu) did not ask, so the same gesture was refused at one door and
        // accepted at two (the OB-057/OB-090 shape).  The window is short: `execRoute` returns as soon as it has spawned the route thread,
        // so the name is cleared 600 ms later whatever the route is still doing - a debounce, not a lock.  Logged rather than silent,
        // because unlike the greyed button these two doors say nothing on their own, and the operator confirmed a dialog and is owed a reason.
        if (route != null && this.ui.routesExecuting.contains(route))
        {
            this.ui.getModel().log(I18n.f("route.ui.infoAlreadyRunning", route));

            return;
        }

        // Marked before the thread starts, so the spinner is up by the time the pointer leaves the button (FR-043).  Every door that runs a
        // route comes through here, including the right-click menu's own Execute (Adam: "executing the route from there should also start
        // the spinner.").
        this.ui.routeStarted(route);

        new Thread(() ->
        {
            runAndTimeTheRoute(route, () ->
            {
                // Asked here rather than inside the model, which has no business showing a dialog - and
                // asked BEFORE the run, so the answer is about the route the operator picked.
                // The model is held as a ViewListener, whose getRoute is typed as the base Route; only a
                // MarklinRoute knows about the autonomy graph, and every route the station holds is one.
                org.traincontrol.base.Route found = this.ui.getModel().getRoute(route);

                org.traincontrol.marklin.MarklinRoute picked =
                    found instanceof org.traincontrol.marklin.MarklinRoute
                    ? (org.traincontrol.marklin.MarklinRoute) found : null;

                // Asked once, and the answer decides HOW it runs (A3): the conflict must not be checked here and again inside, since one that cleared
                // between the two comes back as "yes, override" and runs the route with every guard off.  Three answers, and only the operator's own
                // yes overrides anything.
                if (picked != null)
                {
                    TrainControlUI.RouteConflict answer = this.ui.askAboutRouteConflict(picked, this.ui, null);

                    if (answer == TrainControlUI.RouteConflict.OVERRIDE)
                    {
                        picked.execRouteOverridingConflicts();

                        refreshRouteList();

                        return;
                    }

                    // Said out loud, as the midway door says it: MT-247 made the two doors agree that a cancel ends the route, but this one
                    // returned in silence while the midway one logs two lines; the operator confirmed a dialog and is owed a reason why nothing
                    // happened.  The two cancels are not the same size: a REFUSED here happens BEFORE the route starts, so not one command goes out,
                    // whereas the midway question is answered from inside `MarklinRoute.execRoute`'s command loop, so every earlier command has gone
                    // out and stands (a stop obeyed, an accessory thrown); `execRoute` says so where it happens, and behaviour.md section 7a carries
                    // both halves.
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
     * The three ways out of executeRoute's thread, wrapped so all of them are timed.  Left as a wrapper rather than a try/finally
     * around the body because the body returns early twice (an overridden conflict and a refused one), and a measurement that misses
     * those two measures the happy path only.  It puts the button back on the last line of the finally, through
     * `releaseTheRoutePlayButton`, which holds the grey for a minimum time first.
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
            // What this measures is the dialog and the spawn, not the route: `work.run()` ends at `model.execRoute(route)`, which
            // starts a thread, so this returns milliseconds later whatever the route is still doing.  It answers "did the press get through and
            // how long did the asking take", not "how long did the route take".
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

            // LOOKED UP ONCE.  This tested one lookup and then dereferenced a second, so a
            // route deleted between the two threw on a worker with nothing to catch it.
            if (asked != null)
            {
                {
                    Route currentRoute = asked;
                    
                    // Realised on the EDT.  Everything above - the model lookups, toCSV - belongs
                    // off it and stays off it, but constructing, packing and showing a JFrame is not
                    // thread safe, and the command-capture path then writes this window's document
                    // from the EDT while it is still being built on another thread.
                    final String title = I18n.f(
                        "route.ui.dialogEditRouteWithId",
                        routeName,
                        currentRoute.getId()
                    );

                    if (this.ui.refuseWhileEditorOpen()) return;

                    javax.swing.SwingUtilities.invokeLater(() ->
                    {
            // Checked on the EDT, immediately before constructing, so the two cannot be separated: with the check on the worker and the
            // construction posted, two quick clicks both saw a null field, both posted, and two editors opened, with captured commands going to
            // the one assigned last while the user typed in the other.  The model reads above stay off the EDT; only the decision moves.
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

                        // The route itself, rather than its name to look up again: this window is
                        // handed what it is to show, which is what makes its read-only behaviour
                        // testable without a control station on the network.
                        this.ui.routeEditor = new RouteEditorFrame(this.ui, routeName, currentRoute);

                        // Not over a locked route's own title.  A route from the Central Station
                        // opens with every control greyed and its title is the only thing that says
                        // why; overwriting it with the ordinary edit-route title left a window full
                        // of dead controls and no explanation.
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

                    // The list first, and again after the sync: the copy exists the moment newRoute returns, so that is when the user should see it, and
                    // the sync is a network round trip that, off the event thread, is a plain call that can throw (a station not answering ended this
                    // worker on a stack trace nobody sees and the refresh never ran).
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
        // The question is asked on the event thread: a modal JOptionPane raised inside `new Thread(...)` (both callers had started a
        // thread of their own) pumps events from a thread Swing does not expect to, and can come up behind the window or not at all.  An
        // action handler already runs on the event thread.  The thread below stays for what it was really for - a write per route and one
        // station sync - and the two calls after it marshal themselves.
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

        // Three answers, one for each way of clicking a route (FR-043, revised at MT-217; Adam: "if play button is hit, route executes
        // without confirming.  If route is left-clicked elsewhere, pop up the confirmation first.  If right-clicked, then show the right
        // click menu."): the confirmation was removed when a left click ANYWHERE ran the route and the dialog was the only thing between a
        // stray click and a train moving; having to AIM, which only the button asks, makes it unnecessary.  The highlight has answered its
        // question by the time somebody clicks in this table.
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
            // Disabled means disabled (MT-217): the button greys while its route runs, and a greyed button that still fires is a lie, and would
            // start the same route twice.  Asked in executeRoute now, not here: this was the only door that asked, and the two that do
            // not (the row click with its confirmation, and the right-click menu) reach the same method; one guard on the funnel is the
            // OB-057/OB-090 shape, where two copies, one per door, is how the other two came to be missing.
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
     * Whether a click landed on a cell's play button rather than on the cell.
     *
     * The button is painted by the renderer at the right-hand end of the cell, so its box is worked out
     * the same way here - from the cell's own rectangle - rather than from anything the renderer
     * remembers. A renderer is one component reused for every cell; asking it where it last drew would
     * be asking about whichever cell was painted most recently.
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
     * Asks for a route by name and goes to it (FR-044).
     *
     * Adam: "'Find Route' popup that asks the user for the route name.  system jumps to the route page
     * and highlights the matched cell (scrolling to it if needed), otherwise shows a notice that it
     * doesn't exist."
     *
     * Matched case-insensitively, and on a unique CONTAINS when nothing matches exactly - somebody who
     * types "yard" and has one route with that in its name meant that route, and refusing them on
     * exactness would be pedantry. Ambiguity is not resolved by guessing: two matches is treated as no
     * match, because landing on the wrong one silently is worse than being told to be specific.
     */
    void findRoute()
    {
        String asked = JOptionPane.showInputDialog(this.ui, I18n.t("route.ui.promptFindRoute"),
            I18n.t("route.ui.menuFindRoute"), JOptionPane.PLAIN_MESSAGE);

        // Cancel returns null, which is not the same as an empty search - and neither is worth a
        // "no such route" complaint about a question nobody answered.
        if (asked == null || asked.trim().isEmpty()) return;

        String wanted = asked.trim();

        List<String> matches = TrainControlUI.routesMatchingIn(this.ui.getModel().getRouteList(), wanted);

        if (matches.isEmpty())
        {
            JOptionPane.showMessageDialog(this.ui, I18n.f("route.ui.errorNoSuchRoute", wanted));

            return;
        }

        String found = matches.get(0);

        // MORE THAN ONE: ASK, rather than guess or refuse (MT-218).
        //
        // The key this asks with was already in all eight bundles and used by nothing - written for
        // this and never wired up.
        if (matches.size() > 1)
        {
            found = (String) JOptionPane.showInputDialog(this.ui,
                I18n.t("route.ui.promptWhichRoute"), I18n.t("route.ui.menuFindRoute"),
                JOptionPane.PLAIN_MESSAGE, null, matches.toArray(), matches.get(0));

            // Cancelled, which is not the same as choosing the first one.
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
