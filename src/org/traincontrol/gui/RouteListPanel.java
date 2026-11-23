package org.traincontrol.gui;

import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Route;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;

/**
 * The route list on the Routes tab: running a route (its play button, a click on its row, and every other door, which
 * all come through `executeRoute`), asking about its conflict, holding its button gray while it runs, editing and
 * duplicating one, arming or disarming them in bulk, finding one by name and showing it, and refreshing the list.
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
        // The same answer at all three doors (the OB-057/OB-090 shape).  A debounce, not a lock: `execRoute` returns
        // once it has spawned the route thread, so the name is cleared 600 ms later.  Logged rather than silent: the
        // operator confirmed a dialog and is owed a reason.
        if (route != null && this.ui.routesExecuting.contains(route))
        {
            this.ui.getModel().log(I18n.f("route.ui.infoAlreadyRunning", route));

            return;
        }

        // Marked before the thread starts, so the spinner is up by the time the pointer leaves the button (FR-043).
        // Every door that runs a route comes through here, including the right-click menu's own Execute (Adam:
        // "executing the route from there should also start the spinner.").
        routeStarted(route);

        new Thread(() ->
        {
            runAndTimeTheRoute(route, () ->
            {
                // Asked here rather than inside the model, which has no business showing a dialog, and before the run,
                // so the answer is about the route the operator picked.  Every route the station holds is a
                // MarklinRoute, the only kind that knows the autonomy graph (the model is held as a ViewListener typed
                // to the base Route).
                Route found = this.ui.getModel().getRoute(route);

                MarklinRoute picked = found instanceof MarklinRoute ? (MarklinRoute) found : null;

                // Asked once, and the answer decides how it runs (A3): a conflict checked here and again inside, one
                // that cleared between, comes back as "yes, override" and runs the route with every guard off.  Only
                // the operator's own yes overrides anything.
                if (picked != null)
                {
                    TrainControlUI.RouteConflict answer = askAboutRouteConflict(picked, this.ui, null);

                    if (answer == TrainControlUI.RouteConflict.OVERRIDE)
                    {
                        picked.execRouteOverridingConflicts();

                        refreshRouteList();

                        return;
                    }

                    // Said out loud, as the midway door says it (MT-247): the operator confirmed a dialog and is owed a
                    // reason.  A REFUSED here happens before the route starts, so not one command goes out, whereas the
                    // midway question is answered inside `MarklinRoute.execRoute`'s command loop, so every earlier
                    // command stands (behaviour/routes.md section 7a carries both halves).
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

            releaseTheRoutePlayButton(route);
        }
    }

    /** `known` is the accessory and reason key when the caller already has them; null to ask the route itself. */
    TrainControlUI.RouteConflict askAboutRouteConflict(MarklinRoute route, java.awt.Component over, String[] known)
    {
        if (route == null) return TrainControlUI.RouteConflict.REFUSED;

        String[] why = known != null ? known : route.conflictingAccessoryAndReason();
        String conflict = why == null ? null : why[0];

        // No conflict any more is its own answer (autonomy releases accessories from its own threads, so it can clear
        // after the caller's check): false would silently do nothing, true would run the route with every conflict
        // check off.
        if (conflict == null) return TrainControlUI.RouteConflict.NOTHING_TO_CONFIRM;

        Object[] options = { I18n.t("ui.ok"), I18n.t("ui.cancel") };
        final int[] choice = {JOptionPane.CLOSED_OPTION};

        // The reason the route gave: `heldReason` tells a turnout on a locked path from a signal protecting a platform
        // with a train PARKED at it, and the dialog must not ask about a moving train in both cases.
        String reason = why == null ? null : why[1];

        // A platform only RESERVED is asked about as that: a locked path holds its destination for a train still on its
        // way.
        String question = "route.refusedSignalProtectingOccupiedPlatform".equals(reason)
            ? "layout.ui.confirmRouteProtectingSignal"
            : "route.refusedSignalProtectingReservedPlatform".equals(reason)
                ? "layout.ui.confirmRouteProtectingReservedSignal" : "layout.ui.confirmRouteActiveRoute";

        Runnable ask = () -> choice[0] = JOptionPane.showOptionDialog(
            over == null || !over.isShowing() ? this.ui : over,
            I18n.f(question, route.getName(), conflict),
            I18n.t("layout.ui.dialogPleaseConfirm"),
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);

        try
        {
            if (SwingUtilities.isEventDispatchThread()) ask.run();
            else SwingUtilities.invokeAndWait(ask);
        }
        catch (InterruptedException | java.lang.reflect.InvocationTargetException couldNotAsk)
        {
            this.ui.getModel().log(couldNotAsk);

            return TrainControlUI.RouteConflict.REFUSED;
        }

        // Only an explicit OK; Escape and the close box mean no.
        return choice[0] == 0 ? TrainControlUI.RouteConflict.OVERRIDE : TrainControlUI.RouteConflict.REFUSED;
    }

    /** Marks a route as running (`route` is its name) and starts the animation. */
    void routeStarted(String route)
    {
        if (route == null) return;

        this.ui.routesExecuting.add(route);
        this.ui.startedRunningAt.put(route, System.currentTimeMillis());

        // Said in debug: "nothing happens visually" cannot be told from "too quick to see" without a measurement.
        if (this.ui.getModel() != null && this.ui.getModel().isDebug())
        {
            this.ui.getModel().log("Route " + route + " marked as running");
        }

        // A rebuild rather than a repaint (MT-217): refreshRouteList replaces the table's model, so every cell redraws.
        SwingUtilities.invokeLater(() -> refreshRouteList());
    }

    /** The button comes back once the gray has been on screen long enough, not when the route ends. */
    void releaseTheRoutePlayButton(String route)
    {
        if (route == null) return;

        // The later of the route's end and the gray having been on screen long enough.  A floor rather than a fixed
        // hold, which would be wrong if execution ever became synchronous; today `execRoute` returns at once, so the
        // floor is nearly the whole wait.
        Long since = this.ui.startedRunningAt.get(route);
        long showFor = since == null ? 0
            : TrainControlUI.ROUTE_MINIMUM_VISIBLE_MS - (System.currentTimeMillis() - since);

        if (showFor > 0)
        {
            SwingUtilities.invokeLater(() ->
            {
                javax.swing.Timer rest = new javax.swing.Timer((int) showFor,
                    e -> clearRunningRoute(route));

                rest.setRepeats(false);
                rest.start();
            });

            return;
        }

        clearRunningRoute(route);
    }

    private void clearRunningRoute(String route)
    {
        this.ui.routesExecuting.remove(route);

        Long began = this.ui.startedRunningAt.remove(route);

        if (this.ui.getModel() != null && this.ui.getModel().isDebug())
        {
            this.ui.getModel().log("Route " + route + " no longer running"
                + (began == null ? "" : " after " + (System.currentTimeMillis() - began) + "ms"));
        }

        SwingUtilities.invokeLater(() -> refreshRouteList());
    }

    /** `TrainControlUI.resetRouteSpinners`'s body. */
    void resetRouteSpinners()
    {
        this.ui.routesExecuting.clear();
        this.ui.startedRunningAt.clear();
    }

    /**
     * `TrainControlUI.editRoute`'s body.
     */
    void editRoute(String routeName)
    {
        // Another window's door hears the refusal over it, on the event thread, ahead of the worker's
        final java.awt.Window askedFrom = this.ui.dialogParent();

        if (askedFrom != this.ui && this.ui.refuseWhileEditorOpen(askedFrom)) return;

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

                    SwingUtilities.invokeLater(() ->
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

                    while (this.ui.getModel().getRoute(String.format(proposedName, i)) != null) i++;

                    final LayoutDiagram before = this.ui.shownDiagram();

                    this.ui.getModel().newRoute(String.format(proposedName, i), currentRoute.getRoute(), 
                            currentRoute.getS88(), currentRoute.getTriggerType(), false, currentRoute.getConditions()); 

                    // The list first, and again after the sync: the copy exists the moment newRoute returns, and the
                    // sync is a network round trip that can throw off the event thread (a station not answering ended
                    // this worker before the refresh ran).
                    refreshRouteList();

                    try
                    {
                        // Ensure route changes are synced
                        this.ui.syncAfterAnEdit();
                    }
                    finally
                    {
                        // Whatever the station said, the copy is real and the list has to show it
                        this.ui.redrawRouteSquaresOrRebuild(before);

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

        enableOrDisableMatching(searchString, enable);
    }

    /** Sets automatic execution for each route whose name contains `search` ("*" for all), past the prompt (MT-467). */
    void enableOrDisableMatching(String search, boolean enable)
    {
        new Thread(()->
        {
            // Whether the station has heard of any of them (OB-155): one sync per batch, none when every route was
            // allocated here.
            boolean anyOnTheStation = false;
            LayoutDiagram before = this.ui.shownDiagram();

            for (String routeName : this.ui.getModel().getRouteList())
            {
                Route r = this.ui.getModel().getRoute(routeName);

                // The list is a copy but the lookup is not, and a sync's re-read of a station route briefly removes an
                // id: a name from a list may not be a route.
                if (r == null) continue;

                if (r.hasS88() || r.isEnabled())
                {
                    if (r.getName().contains(search) || "*".equals(search))
                    {
                         // Read before the write, which replaces the route object; the lock, not the id, says whether
                         // the station carried it.
                         if (r.isLocked()) anyOnTheStation = true;

                         // The same write enableOrDisableRoute uses, so the two cannot drift; the guards around it stay
                         // separate because this loop syncs once per batch, not once per route.
                         writeRouteEnabledState(r, enable);
                    }
                }
            }

            if (anyOnTheStation) this.ui.syncAfterAnEdit();

            this.ui.redrawRouteSquaresOrRebuild(before);
            refreshRouteList();
        }).start();
    }

    /** The one route-enabled write both callers make, so it cannot drift; guards and syncs stay with the callers. */
    void writeRouteEnabledState(Route r, boolean enable)
    {
        this.ui.getModel().editRoute(r.getName(), r.getName(), r.getRoute(), r.getS88(), r.getTriggerType(), enable,
                r.getConditions());
    }

    /** `TrainControlUI.deleteRoute`'s body. */
    void deleteRoute(String routeName)
    {
        Route route = this.ui.getModel().getRoute(routeName);
        
        if (route != null)
        {            
            // Counted before the delete, as afterwards nothing is left to count: deleting a route removes the other
            // routes' commands that run it, and the question says how many routes call it.
            int calledBy = this.ui.getModel().routesCalling(route.getName()).size();

            int dialogResult = JOptionPane.showOptionDialog(
                this.ui.routePanel(),
                calledBy > 0
                    ? I18n.f("route.ui.confirmDeleteRouteWithCallers", route.getName(), calledBy)
                    : I18n.f("route.ui.confirmDeleteRoute", route.getName()),
                I18n.t("route.ui.dialogDeleteRoute"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                // Default to No: Enter should not delete a route by accident.
                TrainControlUI.YES_NO_OPTS[1]
            );
            
            if (dialogResult == JOptionPane.YES_OPTION)
            {
                // Read before the delete, the only moment the lock exists (OB-155).  The lock, not the id, says whether
                // the station carried the route; see enableOrDisableRoute.
                boolean wasLocal = !route.isLocked();

                LayoutDiagram before = this.ui.shownDiagram();

                // Not the model's `deleteRoute`, which `editRoute` uses to delete and re-add and which keeps callers:
                // this takes the route out of every route that runs it.
                this.ui.getModel().deleteRouteAndItsCalls(route.getName());
                refreshRouteList();

                // Only when the station could have known the route: one with no lock never came from the station, so a
                // sync (the whole database, behind a modal spinner) can bring back nothing about it.
                if (!wasLocal) this.ui.syncAfterAnEdit();

                // Again after the sync, as `duplicateRoute` does: the sync re-adds routes, so the table would point at
                // replaced instances.
                if (!wasLocal) refreshRouteList();

                this.ui.redrawRouteSquaresOrRebuild(before);
                this.ui.repaintLoc();
            }
        }
    }

    /** Prompts for a new route ID; the model refuses one already taken, and a non-numeric answer is said as such. */
    void changeRouteId(String routeName)
    {        
        try
        {
            Route currentRoute = this.ui.getModel().getRoute(routeName);
            
            if (currentRoute != null)
            {
                String input = JOptionPane.showInputDialog(
                    this.ui,
                    I18n.t("route.ui.promptEnterNewRouteId"),
                    currentRoute.getId()
                );

                if (input != null)
                {
                    int newId = Math.abs(Integer.parseInt(input));

                    if (newId != currentRoute.getId())
                    {
                        LayoutDiagram before = this.ui.shownDiagram();

                        if (!this.ui.getModel().changeRouteId(routeName, newId))
                        {
                            JOptionPane.showMessageDialog(
                                this.ui,
                                I18n.t("route.ui.errorDuplicateRouteId")
                            );
                        }

                        // Both ends, because either could be the station's (OB-155): the old route's lock says whether
                        // it carried the route, the new id's range whether it could have issued the id.
                        if (!org.traincontrol.marklin.MarklinControlStation.isLocalRouteId(newId)
                            || currentRoute.isLocked())
                        {
                            this.ui.syncAfterAnEdit();
                        }

                        this.ui.redrawRouteSquaresOrRebuild(before);
                        refreshRouteList();
                    }
                }
            }
        }
        catch (HeadlessException | NumberFormatException e)
        {
            JOptionPane.showMessageDialog(
                this.ui,
                I18n.t("route.ui.errorIdMustBePositiveInteger")
            );
        }
    }

    /** `TrainControlUI.getRouteAtCursor`'s body. */
    Route getRouteAtCursor(MouseEvent evt)
    {
        try
        {
            javax.swing.JTable list = this.ui.routeList();

            return (Route) list.getValueAt(list.rowAtPoint(evt.getPoint()), list.columnAtPoint(evt.getPoint()));
        }
        catch (RuntimeException e)
        {
            return null;
        }  
    }

    /** `TrainControlUI.enableOrDisableRoute`'s body. */
    void enableOrDisableRoute(String routeName, boolean enable)
    {
        new Thread(() ->
        {
            Route r = this.ui.getModel().getRoute(routeName);

            // A name from a list is not a route: `editRoute` and a sync's re-read of a station route both delete and
            // re-add, so a route can be absent between choosing the name and this lookup, and on a worker with no
            // handler that fails silently.
            if (r == null) return;

            // Only enabling needs an s88, which is what lets a route FIRE itself; a route enabled with no s88 (Bulk's
            // filter allows it) must still be disableable from its own right-click item.
            if (!enable || r.hasS88())
            {
                // Sync only when the station carried the route (OB-155): the write below is a delete-and-re-add in the
                // LOCAL database and tells the station nothing, so for any other route the round trip (the whole
                // database, behind a modal spinner) brings back nothing.  The route's LOCK says it (only the sync's
                // import sets it), the id does not: most of Adam's own routes predate `isLocalRouteId`'s cutover.  Read
                // before the write, which replaces the route object.
                final boolean stationCarriedIt = r.isLocked();
                final LayoutDiagram before = this.ui.shownDiagram();

                writeRouteEnabledState(r, enable);

                if (stationCarriedIt) this.ui.syncAfterAnEdit();

                this.ui.redrawRouteSquaresOrRebuild(before);

                refreshRouteList();
            }
            else
            {
                javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    this.ui,
                    I18n.f("route.ui.errorS88RequiredForAutoFire", r.getName())
                ));
            }
        }).start();
    }

    /**
     * A click on the route list: the play button runs its route, and a row asks first - or, with the power off, the
     * whole cell opens the route's editor and asks nothing (FR-140, FR-164).  `TrainControlUI.RouteListMouseClicked`'s
     * body.
     */
    void RouteListMouseClicked(MouseEvent evt)
    {
        // Three answers, one for each way of clicking a route (FR-043, MT-217; Adam: "if play button is hit, route
        // executes without confirming.  If route is left-clicked elsewhere, pop up the confirmation first.  If
        // right-clicked, then show the right click menu."): the confirmation was removed when a left click anywhere ran
        // the route, and having to aim, which only the button asks, makes it unnecessary.
        clearFoundRoute();

        Route route = this.ui.getRouteAtCursor(evt);

        if (route == null) return;

        if (!SwingUtilities.isLeftMouseButton(evt))
        {
            // The right-click menu is RightClickRouteMenu's own business - it listens for the popup
            // trigger on press and release, which is where a platform decides what a right click is.
            return;
        }

        // WITH THE POWER OFF, THE CELL OPENS THE ROUTE'S EDITOR (FR-140): nothing could run, so the click is taken as
        // wanting to see or change the route.  The button, drawn as a wrench then, does the same (FR-164); the
        // right-click menu's Execute Route still runs it.
        if (routeListEditsRoutes())
        {
            editRoute(route.getName());

            return;
        }

        if (isOverTheRoutePlayButton(evt))
        {
            // Disabled means disabled (MT-217): a grayed button that still fired would start the same route twice.
            // The guard sits in executeRoute, the one funnel every door goes through (OB-090).
            executeRoute(route.getName());

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

        if (dialogResult == JOptionPane.YES_OPTION) executeRoute(route.getName());

        // Revert preference
        this.ui.windowAlwaysOnTopAsChosen();
    }

    /** Whether the route list opens a route's editor rather than running it: the power is off (FR-140, FR-164). */
    boolean routeListEditsRoutes()
    {
        return this.ui.getModel() != null && !this.ui.getModel().getPowerState();
    }

    /**
     * Whether a click landed on a cell's play button rather than on the cell.  The button's box is worked out from the
     * cell's own rectangle, as the renderer paints it at the right-hand end, not from anything the renderer remembers:
     * it is one component reused for every cell, so it would answer for whichever cell was painted last.
     */
    boolean isOverTheRoutePlayButton(MouseEvent evt)
    {
        int row = this.ui.routeList().rowAtPoint(evt.getPoint());
        int column = this.ui.routeList().columnAtPoint(evt.getPoint());

        if (row < 0 || column < 0) return false;

        // An empty cell has no button: the last row is padded out to three columns with nulls.
        if (this.ui.routeList().getValueAt(row, column) == null) return false;

        Rectangle cell = this.ui.routeList().getCellRect(row, column, true);

        Rectangle button = TrainControlUI.routePlayButtonBox(cell);

        return button.contains(evt.getPoint());
    }

    /**
     * Asks for a route by name and goes to it (FR-044): matched case-insensitively, and on a unique CONTAINS when
     * nothing matches exactly; ambiguity is never resolved by guessing (MT-218 asks).
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

        showRouteInTheList(found);
    }

    /** Brings the route list up, scrolls to a route (`name`, as the model spells it) and highlights it. */
    void showRouteInTheList(String name)
    {
        this.ui.foundRoute = name;

        // Found by identity, not by index: the designer's ordering is not a contract.
        int routesAt = this.ui.keyboardTab().indexOfComponent(this.ui.routePanel());

        if (routesAt >= 0) this.ui.keyboardTab().setSelectedIndex(routesAt);

        javax.swing.JTable table = this.ui.routeList();

        for (int row = 0; row < table.getRowCount(); row++)
        {
            for (int column = 0; column < table.getColumnCount(); column++)
            {
                Object at = table.getValueAt(row, column);

                if (!(at instanceof Route) || !name.equals(((Route) at).getName())) continue;

                table.scrollRectToVisible(table.getCellRect(row, column, true));
                table.repaint();

                return;
            }
        }

        // In the model but not in the table (mid-rebuild, or filtered out): the highlight is set, so the next repaint
        // shows it.
        table.repaint();
    }

    /** Forgets the found route, so the highlight does not outlive the question it answered. */
    void clearFoundRoute()
    {
        if (this.ui.foundRoute == null) return;

        this.ui.foundRoute = null;
        this.ui.routeList().repaint();
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
