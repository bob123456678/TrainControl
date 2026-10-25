package org.traincontrol.gui;

import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomy.AutonomyReport;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu on the track diagram, to control autonomy
 * @author Adam
 */
final class LayoutRightclickAutonomyMenu extends JPopupMenu
{    
    /**
     * Its tooltips wrapped where a line will not hold them, as it is shown (FR-112) - whatever set them.
     *
     * @param invoker where it is shown
     * @param x where it is shown
     * @param y where it is shown
     */
    @Override
    public void show(java.awt.Component invoker, int x, int y)
    {
        AutonomyEditorPanel.wrapTheTooltips(this);

        super.show(invoker, x, y);
    }

    public static final int MAX_PATHS = 12;

    /**
     * Kept so the actions built below can still reach them when they fire, long after the constructor has returned;
     * everything else is local to the assembly.
     */
    private final TrainControlUI ui;

    private final org.traincontrol.automationui.TileGraph.TileKey station;

    /**
     * The square the pointer was over, station or not.  Kept apart from the station because they answer different
     * questions: a RUNNING layout (where this train may go, which locomotive is here) is about a station, the setup
     * (become a station, which way trains run, how long) is about the square.
     */
    private final org.traincontrol.automationui.TileGraph.TileKey here;

    private final org.traincontrol.automationui.AutonomySession session;
    
    /**
     * Builds this menu for a square and shows it, if it has anything to say.
     *
     * The one way in: the constructor is private, so the show-only-if-non-empty check cannot be missing at one call
     * site.  An empty menu would open a one-item-high gray box under the pointer, which as LayoutLabel's comment puts
     * it "reads as a fault".
     *
     * @param ui the application
     * @param station the sensor the caption is about, or null for the diagram-wide menu
     * @param here the square that was clicked, or null where the click was not on one
     * @param at the component the click landed on
     * @param x where in it
     * @param y where in it
     */
    static void showFor(TrainControlUI ui, org.traincontrol.automationui.TileGraph.TileKey station, org.traincontrol.automationui.TileGraph.TileKey here,
        final java.awt.Component at, final int x, final int y)
    {
        // A square's menu only with a setup loaded (OB-307): over a square it would offer Start and a train's routes on
        // a railway nobody loaded.  The diagram's own menu (no square) still opens, offering the setup, which is how
        // one gets loaded from the diagram.
        if ((station != null || here != null) && (ui == null || !ui.isAutonomyLoaded())) return;

        // The railway is asked first, and not on the event thread (OB-192): the paths section needs `getPossiblePaths`
        // (`synchronized` on the `Layout`, a search of the whole graph), and a dispatch holds that monitor across a
        // `CONFIGURE_SLEEP` per accessory, so a right-click on an idle train during somebody else's departure would
        // freeze the window for the length of the switch-throwing.
        //
        // Same shape as `CoveredTrackRenderer` and `ReturnHomeTriageRenderer`: a short-lived thread per right-click,
        // the answers come back, the menu is built from them.  The menu appears a moment late when the railway is busy,
        // which beats a frozen window.
        //
        // The square is resolved here on the event thread: `getAutonomyPointForTile` goes through the LAZY
        // `getAutonomySession()`, which parses every page, can write to disk and can raise a dialog.  Only the Point, a
        // value, crosses to the worker.
        final Point standing = ui != null && ui.getModel() != null && ui.getModel().hasAutoLayout()
            ? ui.getAutonomyPointForTile(station != null ? station : here) : null;

        Thread gathering = new Thread(() ->
        {
            final PathOptions options = gatherPathOptions(ui, standing);

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                LayoutRightclickAutonomyMenu menu = new LayoutRightclickAutonomyMenu(ui, station, here, options);

                // An empty menu is not worth showing.
                if (menu.getComponentCount() > 0)
                {
                    // After the count, deliberately: a heading is not an item, and putting one on first would turn
                    // "nothing to offer here" into a gray box with a station name in it.
                    menu.headline();

                    // Still on screen: `JPopupMenu.show` throws on a component taken off screen, and a page change or
                    // grid rebuild in the gap between click and menu is ordinary.
                    if (at.isShowing()) menu.show(at, x, y);
                }
            });
        }, "LayoutRightclickAutonomyMenu options");

        gathering.setDaemon(true);

        gathering.start();
    }

    /**
     * What the paths section needs from the railway, gathered off the event thread.  Every field is an answer, not a
     * question: the menu asks the `Layout` nothing (see `showFor`).
     */
    static final class PathOptions
    {
        /**
         * The train these paths belong to.
         *
         * Kept so the menu can tell the answer is about the train it is now drawing: the gather and the build are on
         * different threads, and a train moved or removed in between makes this the wrong list, not a stale one.
         */
        final Locomotive locomotive;

        /** Offerable, and a square autonomy would choose: the top-level list. */
        final List<List<Edge>> shown;

        /** Offerable, but never chosen by autonomy: More Destinations. */
        final List<List<Edge>> other;

        // There is deliberately no count of everything the search returned (OB-205): comparing against it made the
        // "..." appear whenever anything was filtered.  See `theListWasCutShort`.

        /**
         * The railway these paths were found on, which the destination sends on: a railway replaced between the gather
         * and the click refuses at its own fence rather than the new one locking the old one's track.
         */
        final org.traincontrol.automation.Layout railway;

        private PathOptions(org.traincontrol.automation.Layout railway, Locomotive locomotive, List<List<Edge>> shown,
            List<List<Edge>> other)
        {
            this.railway = railway;
            this.locomotive = locomotive;
            this.shown = shown;
            this.other = other;
        }
    }

    /**
     * Whether the menu offers the way out to the autonomy tab - the "..." at the foot of the list.  It counts only
     * destinations this menu could have shown, not the squares it deliberately hides (switched off, excluding this
     * train, ordinary termini of a train that cannot reverse; OB-205), which the autonomy tab lists.  So the ellipsis
     * means one thing: **there were more destinations than fitted** (FR-058).
     *
     * @param offered how many ordinary destinations this menu has to show, after its own filtering
     * @param shown how many of them are on screen
     * @param inMoreDestinations how many are in the submenu
     * @return whether to add the item
     */
    static boolean theListWasCutShort(int offered, int shown, int inMoreDestinations)
    {
        return offered > shown + inMoreDestinations;
    }

    /**
     * Whether a setup edit from this menu is refused because trains are running, having said so (no setup edit is
     * possible during a run) - as the setup menu's own items refuse.
     *
     * @return true where the edit is refused
     */
    private boolean refusedWhileRunning()
    {
        return ui.refuseWhileARunHoldsTheSetup(ui);
    }

    /**
     * Asks the railway everything the paths section needs, on the caller's thread.  MUST NOT BE CALLED ON THE EVENT
     * THREAD (`showFor` arranges that, `testNothingOnTheEventThreadTakesTheRailwaysMonitor` checks): every call in here
     * is `synchronized` on the `Layout`.
     *
     * Answers null wherever there is nothing to list: no setup, an open editor, a busy railway, no train on the square,
     * or a train already running.
     *
     * @param ui the application
     * @param current the Point standing on the clicked square, resolved by `showFor` on the event
     *        thread, or null where there is none
     * @return the answers, or null when this square has no paths to offer
     */
    private static PathOptions gatherPathOptions(TrainControlUI ui, Point current)
    {
        // The same gates the constructor applies, in the same order, so a gather never takes the railway's monitor for
        // a menu that will not draw it.  The constructor's copy decides: a run can start between here and the build.
        if (ui == null || current == null || ui.isLayoutEditorOpen()) return null;

        // ASKED ONCE, NOT BUILT: this runs on a worker, and Unload can clear the model between asking whether
        // there is a railway and asking for it
        final org.traincontrol.automation.Layout running = ui.getModel() == null ? null
            : ui.getModel().getAutoLayoutIfLoaded();

        if (running == null) return null;

        if (ui.isAutonomyBusy()) return null;

        if (!current.isDestination() && current.getCurrentLocomotive() == null) return null;

        Locomotive locomotive = current.getCurrentLocomotive();

        if (locomotive == null) return null;

        // If we want to view paths, locomotive must not be running.  See the constructor for OB-164,
        // which is why this is a per-locomotive gate rather than isAutoRunning().
        if (running.getActiveLocomotives().containsKey(locomotive)) return null;

        List<List<Edge>> paths = withoutGoingNowhere(ui, running.getPossiblePaths(locomotive, true));

        paths.sort((List<Edge> p1, List<Edge> p2) -> Edge.pathToString(p1).compareTo(Edge.pathToString(p2)));


        // Switched-off squares are only in the autonomy tab: this menu is the quick way to send a train somewhere
        // ordinary.  Filtered rather than grayed, which would still cost a line to read past.
        List<List<Edge>> shownPaths = new java.util.ArrayList<>();

        // Squares autonomy would never choose go in their own submenu, More Destinations (FR-058), so a parking track
        // cannot cost one of the twelve lines an ordinary platform wanted.  Asked through `isChoosableByAutonomy`, the
        // predicate the "no available paths" window and the diagram's caption rule ask.
        List<List<Edge>> otherPaths = new java.util.ArrayList<>();

        for (List<Edge> path : paths)
        {
            Point end = path.get(path.size() - 1).getEnd();

            // Off this menu altogether (switched off, or excluded for this train) and not counted towards the ellipsis:
            // the autonomy tab lists them.  The rule is named on the layout because this class is package-private and a
            // rule nothing can reach cannot be tested.
            if (!running.isOfferableToOperator(end, locomotive))
            {
                continue;
            }

            // Asked of the square and not of this train, which `isOfferableToOperator` has covered: what is left (a
            // reversing point, a square marked as not an automatic destination) is the same predicate the "no available
            // paths" window and the diagram's captions ask.
            if (running.isChoosableByAutonomy(end))
            {
                shownPaths.add(path);
            }
            else
            {
                otherPaths.add(path);
            }
        }

        return new PathOptions(running, locomotive, shownPaths, otherPaths);
    }

    /**
     * Puts the clicked square's name at the top, grayed and bold like the autonomy editor's own menu (OB-112): the menu
     * covers the square while it is open.  The name comes from the session so the two menus cannot call one square by
     * different names.  Inserted rather than added, because the menu is already built when this runs, once showFor
     * knows there is something to head.
     */
    private void headline()
    {
        org.traincontrol.automationui.TileGraph.TileKey subject = station != null ? station : here;

        if (subject == null || session == null) return;

        JMenuItem heading = new JMenuItem(session.describeTile(subject));

        heading.setEnabled(false);
        heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD));

        insert(heading, 0);
        insert(new JPopupMenu.Separator(), 1);
    }

    /**
     * @param station the sensor the caption is about, or null for the diagram-wide menu.  A square rather than a name:
     * a station is several Points and none is called what the caption says.
     * @param here the square that was clicked, or null where the click was not on one
     * @param options what the railway answered about this square's paths, gathered off the event thread by
     *        `showFor`, or null where there is nothing to list.  NOTHING IN THIS CONSTRUCTOR MAY ASK THE RAILWAY
     *        ITSELF (see `showFor`).
     */
    private LayoutRightclickAutonomyMenu(TrainControlUI ui,
        org.traincontrol.automationui.TileGraph.TileKey station,
        org.traincontrol.automationui.TileGraph.TileKey here,
        PathOptions options)
    {
        this.ui = ui;
        this.station = station;
        this.here = here;
        this.session = ui.getAutonomySession();

        JMenuItem menuItem;

        // Nothing here while an editor has the diagram: it holds its own unsaved copy of the setup, so anything done
        // from this menu would be overwritten (a placed locomotive vanishes on save).  The Autonomy menu is shut for
        // the same reason.  Shown and disabled with the reason on it, so a right-click that does nothing does not read
        // as a broken menu.
        if (ui.isLayoutEditorOpen())
        {
            menuItem = new JMenuItem(I18n.t("autosetup.ui.menuEditorOpen"));
            menuItem.setEnabled(false);
            add(menuItem);

            return;
        }
        
        if (!ui.getModel().hasAutoLayout())
        {
            // Nothing is running because nothing is set up, which is when somebody needs the setup menu most, and
            // the diagram in front of them is where they would look.
            addSetupMenu();
        }

        if (ui.getModel().hasAutoLayout())
        {
            // Start versus Graceful Stop asks isAutonomyBusy(), as every item in this branch does: isAutoRunning()
            // clears the instant Graceful Stop is pressed while trains coast to their stations, so Stop stays offered,
            // and Start does not appear, until they stop.
            if (!ui.isAutonomyBusy())
            {
                menuItem = new JMenuItem(I18n.t("autolayout.ui.menuStartAutonomy"));

                // Grayed when it could not start (OB-050): canStartAutonomy asks the predicate the guard asks.  (The
                // Start button itself stays enabled and explains at press time.)  Asked as few times as the answers
                // allow: AutonomySession.check() is not cached and rebuilds the termini and turn-around sets over every
                // point, so asking four times would be four full walks of the railway on the event thread, on every
                // right-click of a station.
                boolean canStart = ui.canStartAutonomy();

                // Both sentences from one reading: Start's tooltip and the Return Home item's, where Start is not
                // offered.  Asked apart over a broken setup they walked it four more times.  Where Start is offered the
                // setup has no errors and neither is asked.
                String[] why = canStart ? null : ui.whyStartAndAHandSendAreRefused();

                menuItem.setEnabled(canStart);

                if (!canStart)
                {
                    // And say which of the four reasons it is (the fourth, a layout on the Central Station): the
                    // tooltip must not be the waiting-for-trains message whenever Start is off for another reason, such
                    // as `hasErrors()` on a graph that will not build at all, where that message's count is zero
                    // (`errorCannotBuildDetailOne` is the load door's wording).  Through the one rule (MT-263): the
                    // wording asks how many blocking problems there were, so three do not read as one.
                    menuItem.setToolTipText(AutonomyEditorPanel.wrapped(why[0]));
                }

                menuItem.addActionListener(event -> 
                {
                    try
                    {
                        ui.requestStartAutonomy();
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(ui, e.getMessage());
                    }
                });

                add(menuItem);

                // THE HAND DOORS' SENTENCE, from the same reading.
                String broken = why == null ? null : why[1];

                HomeLocomotiveMenu.addReturnHomeItem(this, ui, broken);

                // The Point standing on that square, preferring one with a train on it.  From the SQUARE rather than
                // the station (MT-069): `station` is null unless the square was designated a station, so on a
                // pass-through Point with a locomotive the whole block below would vanish.
                Point current = ui.getAutonomyPointForTile(station != null ? station : here);

                // A destination, or anywhere a train is actually standing: gated on destination alone, a locomotive on
                // a copy that is not one (barring a side makes that copy a non-destination, yet a train can still be
                // placed on it by hand) would have no menu.
                if (current != null && (current.isDestination() || current.getCurrentLocomotive() != null))
                {
                    // Get the locomotive at this station
                    Locomotive locomotive = current.getCurrentLocomotive();

                    // If we want to view paths, locomotive must not be running
                    //
                    // A per-locomotive gate, where AutoLocomotiveStatus asks isAutoRunning() about the whole layout, so
                    // the two disagree.  Known limitation, closed on the owner's ruling (OB-164): "The user can rely on
                    // full autonomy or the panels to send trains more clearly."  Left here so the next reader who sees
                    // the two surfaces disagree finds the ruling.
                    //
                    // The answer is the one gathered for this train, never a fresh question: equality says it is about
                    // the locomotive standing here now rather than when the right-click landed.
                    if (locomotive != null && options != null && locomotive.equals(options.locomotive))
                    {
                        List<List<Edge>> paths = options.shown;


                        List<List<Edge>> otherPaths = options.other;

                        if (!paths.isEmpty())
                        {
                            addSeparator();

                            // Show the locomotive name for reference
                            menuItem = new JMenuItem(locomotive.getName());
                            menuItem.setEnabled(false);
                            add(menuItem);
                        }

                        // Counted rather than measured off the menu, which also holds the Start item, the Return Home
                        // item, a separator and the locomotive's name.
                        int shown = 0;

                        for (List<Edge> path : paths)
                        {
                            add(destinationItem(ui, options.railway, path, locomotive));

                            // The way through, whenever there were more destinations than fitted: the
                            // non-choosable paths are on screen in More Destinations, so comparing against `shown`
                            // alone would fire on essentially every right-click (FR-058).  See `theListWasCutShort`.
                            if (++shown >= Math.min(MAX_PATHS, paths.size())
                                && theListWasCutShort(paths.size() + otherPaths.size(), shown,
                                    otherPaths.size()))
                            {
                                menuItem = new JMenuItem("...");
                                menuItem.addActionListener(event -> 
                                {
                                    try
                                    {
                                        ui.jumpToAutonomyLocTab();
                                    }
                                    catch (Exception e)
                                    {
                                        JOptionPane.showMessageDialog(ui, e.getMessage());
                                    }
                                });    

                                add(menuItem);
                                break;
                            }
                        }

                        // More Destinations (FR-058) holds everything valid but not automatic: a reversing point, and a
                        // square marked as not an automatic destination, each somewhere the operator may legitimately
                        // send a train by hand ("filtering at selection, never refusing at execution").  A terminus is
                        // not here (a non-reversible train may back into one, so it is in the base list); nor is an
                        // excluded square, which is off the menu entirely.
                        //
                        // Uncapped: the top-level cap is about competing for the first screenful, which a submenu does
                        // not do, but Swing gives an over-tall JMenu no scroller.  On the author's configuration that
                        // is about twenty-five items, which fits; a railway exceeding a screen would want a scroller
                        // rather than a second ellipsis.
                        if (!otherPaths.isEmpty())
                        {
                            // The heading, when the top-level list did not already add it: the separator and name are
                            // gated on that list being non-empty, so a train with nothing automatic to offer would
                            // otherwise get a bare "More Destinations".
                            if (paths.isEmpty())
                            {
                                addSeparator();

                                javax.swing.JMenuItem whose =
                                    new javax.swing.JMenuItem(locomotive.getName());

                                whose.setEnabled(false);

                                add(whose);
                            }

                            javax.swing.JMenu more =
                                new javax.swing.JMenu(I18n.t("autolayout.ui.menuMoreDestinations"));

                            for (List<Edge> path : otherPaths)
                            {
                                more.add(destinationItem(ui, options.railway, path, locomotive));
                            }

                            add(more);

                        }

                        // There is no second way out.  The "..." means there were more ordinary destinations than
                        // fitted, and a train with none has nowhere to go, which the menu says by having nothing under
                        // its name ("No available paths" and the editor's *Why not Moving?* give the reasons).  So the
                        // item is decided in ONE place, the loop above through `theListWasCutShort`, and
                        // `regression.testTheDestinationDoorsAgree` asserts there is only one.
                    }

                    addSeparator();

                    // Station name label
                    // The STATION's name, not the copy's: a square is several Points named apart ("BottomMainA
                    // (eastbound)") for the running log, but a user pointing at a platform created a station, not an
                    // eastbound one and a westbound one.
                    menuItem = new JMenuItem(stationName(current));
                    menuItem.setEnabled(false);
                    add(menuItem);

                    // Place a different locomotive at this station, FACING a chosen way.  A square is several Points,
                    // one per side a train can arrive by, and each copy can only leave the way its own facing allows,
                    // so "put it here" must pick the copy: a careless one is a Point whose only moves are the ones the
                    // split exists to forbid.  Offered only on a square that is a destination.  Which copy the train
                    // stands on is its heading's (below), including a copy trains may not arrive at (OB-284), where Why
                    // not Moving? says so.
                    if (ui.getActiveLoc() != null
                        && current.isDestination()
                        && !ui.aRunHoldsTheSetup()
                        && !ui.getActiveLoc().equals(locomotive))
                    {
                        // Chosen for them, from the ways a train could actually leave: a user pointing at a platform is
                        // not pointing at one of its several Points.  The copy is the one facing the way the train
                        // already faces, wherever that copy has a way out (OB-284), otherwise the first this menu
                        // offers, never a draw (OB-296: "Yes, keep the train's heading.").  Turning it is the facing
                        // item below.
                        //
                        // Where no copy can leave, offered all the same, as the editor's Place is (OB-350):
                        // `placeSomewhereLegal` stands the train where the build would and records no facing.  The
                        // setup check still warns about the square.
                        final java.util.List<String> usable = placeableCopies();

                        // Offered on the action's own question: every copy the train could leave by, which is what its
                        // heading is kept over, including one trains may not arrive at (`placeableCopies` never offers
                        // one).  Its order still decides the copy where no heading holds.
                        final java.util.Map<String, org.traincontrol.automationui.TilePorts.Side> departable =
                            session == null || !ui.getModel().hasAutoLayout()
                                ? new java.util.LinkedHashMap<String, org.traincontrol.automationui.TilePorts.Side>()
                                : session.departableFacingsFor(station, ui.getModel().getAutoLayout());

                        final java.util.List<String> offered = new java.util.ArrayList<>(usable);

                        for (String copy : departable.keySet())
                        {
                            if (!offered.contains(copy)) offered.add(copy);
                        }

                        menuItem = new JMenuItem(
                            I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName())
                        );

                        menuItem.addActionListener(event -> placeSomewhereLegal(offered));

                        add(menuItem);
                    }

                    // Turn the standing train round.  Placing keeps the heading the train had, which may not be the one
                    // it has now, so there has to be a way to say "it faces the other way" without taking it off and
                    // putting it back.  Only the copies it could leave from, as for placing.  Every setup item here is
                    // left out while a route runs (`aRunHoldsTheSetup`).
                    if (current.getCurrentLocomotive() != null && !ui.aRunHoldsTheSetup())
                    {
                        // There is one "Facing" menu, the one further down (MT-086): it names the train it is about and
                        // is the same menu the setup editor offers, so the two surfaces cannot disagree.  Choosing
                        // which COPY of a split square the train stands on is the same decision as the facing.

                        menuItem = new JMenuItem(
                            I18n.f("layout.ui.menuRemoveLocomotive", current.getCurrentLocomotive().getName())
                        );
                        // A method rather than a lambda, so this constructor asks the railway nothing: `moveLocomotive`
                        // is `synchronized` on the `Layout`, and the guard would otherwise have to allow the whole
                        // constructor.
                        menuItem.addActionListener(event -> removeLocomotiveHere(current));

                        add(menuItem); 
                    }
                    
                    if (!ui.getModel().getAutoLayout().getLocomotivesToRun().isEmpty() && !ui.aRunHoldsTheSetup())
                    {
                        // Edit locomotive
                        menuItem = new JMenuItem(GraphLocAssign.menuLabelFor(current));
                        menuItem.addActionListener(event ->
                        {
                            // Not while trains run: offered only at rest, and a click after a run began would write the
                            // placement, facing and tail into the setup and save it.  Asked at the click and again at
                            // OK.
                            if (refusedWhileRunning()) return;

                            // The setup goes in with it: the dialog offers the arrival side and works this square's
                            // sides out from the build.
                            GraphLocAssign edit = new GraphLocAssign(ui, current,
                                ui.getAutonomySession(),
                                ui.getModel() == null ? null : ui.getModel().getAutoLayout());

                            int dialogResult = JOptionPane.showOptionDialog(
                                ui,
                                // The form with the arrival side under it, wrapped in one place for both doors
                                // (`asDialogContent`).
                                edit.asDialogContent(),
                                I18n.f("autolayout.ui.dialogEditOrAssignLocomotive",
                                    stationName(current)),
                                JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                TrainControlUI.OK_CANCEL_OPTS,
                                TrainControlUI.OK_CANCEL_OPTS[0]
                            );

                            if (dialogResult == JOptionPane.OK_OPTION)
                            {
                                if (refusedWhileRunning()) return;

                                // And into the setup: an assignment made from the diagram must survive the next
                                // configuration load.  Through the shared door, so the two cannot drift apart.
                                GraphLocAssign.commitAndRecord(edit);

                                ui.updateVisiblePoints();
                                ui.repaintAutoLocList(false);
                            }
                        }); 

                        add(menuItem);
                    }

                    // Whether it runs (FR-117): an "active" checkbox for a locomotive at that station.  Only at rest;
                    // while trains run the Auto tab's pause button says the same.  Read off the train, as autonomy and
                    // Return Home do.
                    if (locomotive != null && !ui.aRunHoldsTheSetup())
                    {
                        final Locomotive standing = locomotive;

                        final javax.swing.JCheckBoxMenuItem active = new javax.swing.JCheckBoxMenuItem(
                            I18n.f("autolayout.ui.menuLocomotiveActive", standing.getName()),
                            !standing.isAutonomyPaused());

                        active.setToolTipText(I18n.t("autolayout.ui.hintLocomotiveActive"));

                        active.addActionListener(event -> setActive(standing, active.isSelected()));

                        add(active);
                    }

                    // Which locomotive belongs here is offered from the setup menu below, which works from the SQUARE
                    // rather than a Point, so which Point to ask does not arise.
                }

                // Which way round the train is standing, beside the other things about the train
                // rather than a level down inside the setup submenu
                javax.swing.JMenu facing = ui.aRunHoldsTheSetup() ? null : ui.buildAutonomyFacingMenu(here);

                if (facing != null)
                {
                    add(facing);
                }

                addSetupMenu();
            }
            else
            {
                menuItem = new JMenuItem(I18n.t("autolayout.ui.menuStopAutonomyGracefully"));
                menuItem.addActionListener(event -> 
                {
                    try
                    {
                        ui.requestStopAutonomy();
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(ui, e.getMessage());
                    }
                });    

                add(menuItem);
            }
        }
    }

    /**
     * Puts the active locomotive on one named copy of this square, and remembers which way: placing on the right copy
     * lets autonomy move it now, recording the facing puts it back on the same copy at every load.
     *
     * @param pointName the copy to place on
     * @param facing which way it is pointing, or null when the square has only one copy
     */
    /**
     * The whole Autonomy menu, as a submenu, for when nothing is running.  The same menu rather than a copy, so one
     * place decides what autonomy offers.  Its own instance, because a Swing component has one parent and the menu bar
     * owns the other; it costs nothing since the menu builds its items when opened.
     *
     * Under a parent entry so it stays out of the way of the everyday right-click.  Only while nothing is running: half
     * of what is in there rebuilds the layout, and a menu full of things that say no is worse than one that waited.
     */
    /**
     * Everything the autonomy editor offers on this square, folded into this menu.  The editor's menu rather than the
     * window's Autonomy menu, which is about the setup as a whole; what somebody right-clicking a platform wants is
     * what the editor gives them there.  Borrowed from the panel that builds it, so the two never drift.
     *
     * Under a parent entry, because it is a long menu and most right-clicks on a running layout are about sending a
     * train.  Nothing while autonomy is running: the setup describes track trains are on right now.
     */
    private void addSetupMenu()
    {
        if (here == null || ui.aRunHoldsTheSetup()) return;

        JPopupMenu built = ui.buildAutonomyTileMenu(here);

        if (built == null || built.getComponentCount() == 0) return;

        javax.swing.JMenu setup = new javax.swing.JMenu(I18n.t("autosetup.ui.menuAutonomySetup"));

        // getComponents hands back a copy, so moving each one out from under the popup as we go does
        // not walk the array we are reading.
        for (java.awt.Component part : built.getComponents())
        {
            setup.add(part);
        }

        // FR-026: the way out to the full editor, at the foot of the settings it is the deep end of.  Whether it is
        // appropriate is asked of the window that opens it, which hands back the reason its own refusal would give, so
        // the item is live exactly when pressing it would work and says why when it is not.
        String refusal = ui.whyAutonomyEditorCannotOpen();

        JMenuItem openEditor = new JMenuItem(I18n.t("autosetup.ui.menuOpenFullEditor"));

        if (refusal == null)
        {
            // On the square that was clicked, so the editor comes up on the right page with that tile
            // found and flashing - the same landing a finding gets.
            openEditor.addActionListener(event -> ui.openAutonomyEditor(here));
        }
        else
        {
            openEditor.setEnabled(false);
            openEditor.setToolTipText(I18n.t(refusal));
        }

        setup.addSeparator();
        setup.add(openEditor);

        if (getComponentCount() > 0) addSeparator();

        add(setup);
    }

    /**
     * Drops the paths that end where the train already is.  A square is several Points, so "a different Point" is not
     * "somewhere else": a train at BottomMainB must not be offered BottomMainB, the copy facing the other way.
     *
     * Filtered here rather than in the layout, which cannot tell: its only candidate key is the sensor, which a station
     * and its approach guard share while being two places.  Only the setup knows that two Points built from one square
     * are one place.
     *
     * @param ui the window, which owns the setup
     * @param paths what the layout offered
     * @return the ones that actually go somewhere
     */
    private static List<List<Edge>> withoutGoingNowhere(TrainControlUI ui, List<List<Edge>> paths)
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        return session == null ? paths : session.getStationIndex().distinctDestinations(paths);
    }

    /**
     * What to call a Point when a person is reading it.  The built graph names the copies of a square apart
     * ("BottomMainA (eastbound)") for the running log, but a user pointing at a platform created a station, not an
     * eastbound one and a westbound one.  Falls back to the Point's own name when the setup cannot be asked.
     *
     * @param point a Point of the running graph
     * @return the station name a reader would recognize
     */
    private String stationName(Point point)
    {
        return session == null ? (point == null ? "" : point.getName())
            : session.getStationIndex().describe(point);
    }

    /**
     * The copies of this square a train could actually be driven away from.  A copy with no outgoing edge is one the
     * split made arrivable and not leavable; placing there is placing a train that cannot move, which autonomy reports
     * much later as a configuration that will not run.
     *
     * @return the point names, empty when nothing here can move
     */
    private java.util.List<String> placeableCopies()
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        if (session == null || !ui.getModel().hasAutoLayout()) return out;

        // `shut` (the copies barred to arrivals) is counted and not offered.  Kept as a separate bucket rather
        // than folded into "not usable", because the reasoning at the foot of this method needs the distinction.
        java.util.List<String> shut = new java.util.ArrayList<>();

        // copies a train could sit on but never be dispatched from
        java.util.List<String> stranded = new java.util.ArrayList<>();

        for (String name : session.facingsFor(station).keySet())
        {
            Point copy = ui.getModel().getAutoLayout().getPoint(name);

            if (copy == null) continue;

            java.util.List<Edge> away = ui.getModel().getAutoLayout().getNeighbors(copy);

            if (away == null || away.isEmpty()) continue;

            // A copy the train can actually LEAVE, in preference to one it merely sits on: a copy of a split square can
            // have somewhere to go and nowhere to be SENT (everything it reaches is a plain point, a reversing point or
            // parking), and a train put there never moves.  Barred copies are not on this list (barring an arrival side
            // makes that copy a non-destination); the right-click Place still stands a train on one where its heading
            // is that way (OB-284).
            if (!ui.getModel().getAutoLayout().canReachAnyDestination(copy))
            {
                stranded.add(name);
            }
            else if (copy.isDestination())
            {
                out.add(name);
            }
            else
            {
                shut.add(name);  // counted, not offered - see the note at the top and the return below
            }
        }

        // Nothing open is not nowhere to go: a square whose copies are all stranded is still somewhere a train
        // physically stands, and refusing to place one there would give a different message from the "no way out" one.
        if (!out.isEmpty()) return out;

        // Not a barred copy, first: `shut` is the copies whose `isDestination()` is explicitly false.  The right-click
        // Place accepts them and offers the item over every copy a train could leave by (`departableFacingsFor`); this
        // list is only the order a copy is taken in where the heading holds none.  Stranded copies stay, barred or not:
        // standing a train where autonomy cannot send it is something an operator may want.
        return stranded;
    }

    /**
     * Places the active locomotive on the copy it could leave from that faces the way it already faces (OB-296).
     *
     * The heading is read before the move, from the copy the train stands on (`facingOf`), by the paste's rule (OB-270,
     * OB-284), so the same train placed the same way always faces the same way.  Where no copy can leave, the copy the
     * build stands an unfaced train on, with no facing recorded (OB-350).
     *
     * @param usable the copies a train can be driven away from, empty where there are none
     */
    private void placeSomewhereLegal(java.util.List<String> usable)
    {
        // ASKED AGAIN AT THE CLICK, as Edit Locomotive is: a route may have begun since the menu opened
        if (refusedWhileRunning()) return;

        String loc = ui.getActiveLoc() == null ? null : ui.getActiveLoc().getName();

        org.traincontrol.automation.Layout running = org.traincontrol.gui.TailCrossedPrompt.runningNow(ui.getModel());

        // ITS HEADING, read before the move, from the copy it stands on.
        org.traincontrol.automationui.TilePorts.Side keep = session == null ? null : session.facingOf(loc, running);

        String name = usable.isEmpty() ? null : copyToPlaceOn(session, station, running, keep, usable);

        // NO WAY OUT (OB-350): where the build stands a train nothing says the facing of
        if (usable.isEmpty() && session != null)
        {
            org.traincontrol.automation.Point unfaced = session.copyWithNoFacing(station, running);

            name = unfaced == null ? null : unfaced.getName();
        }

        if (name == null) return;

        placeFacing(loc, name, session == null || usable.isEmpty() ? null : session.facingsFor(station).get(name));
    }

    /**
     * The copy a train is put on by the right-click Place, as the paste chooses it (OB-296): the heading over every
     * copy the train could leave by - one trains may not arrive at included (OB-284: *"for barred arrival directions,
     * keep the direction"*) - by `AutonomySession.facingAfterAPaste`; then the copy facing that way by `copyFacing`,
     * the plain one before its turning twin.  Where no heading holds, the first copy the menu offers.
     *
     * The menu's own list is not enough: the copy facing the heading at a square with a barred side is no station, so
     * it is never on that list.
     *
     * @param session the setup, or null
     * @param station the square
     * @param running the railway running, or null
     * @param keep the heading the train has before the move, or null when none is known
     * @param usable the copies the menu offers Place over, in the build's order
     * @return the copy to put it on, or null when there is none
     */
    static String copyToPlaceOn(org.traincontrol.automationui.AutonomySession session,
        org.traincontrol.automationui.TileGraph.TileKey station, org.traincontrol.automation.Layout running,
        org.traincontrol.automationui.TilePorts.Side keep, java.util.List<String> usable)
    {
        if (usable == null || usable.isEmpty()) return null;

        if (session == null || running == null || station == null) return usable.get(0);

        org.traincontrol.automationui.TilePorts.Side intended = org.traincontrol.automationui.AutonomySession
            .facingAfterAPaste(session.departableFacingsFor(station, running), keep, null);

        org.traincontrol.automation.Point copy = intended == null ? null : session.copyFacing(station, intended, running);

        return copy != null ? copy.getName() : usable.get(0);
    }

    /**
     * Whether the operator marked this square as one trains may turn round at.
     *
     * Asked of the SETUP, because `canReverse` never reaches the running layout: the builder expresses it by splitting
     * the square.  The same question and answer as `TrainControlUI.mayTurnHere` (paste door) and `GraphLocAssign`
     * (dialog).
     *
     * @return true when trains may turn there
     */
    private boolean mayTurnHere()
    {
        return session != null && station != null && session.mayTurnTiles().contains(station);
    }

    /**
     * Puts a named locomotive on a named copy of this square, facing a given way.
     *
     * Takes the locomotive rather than reading the active one, because placing means the active locomotive but turning
     * means the one already standing there: reading the active one for a turn would pick THAT train up and put it on
     * this platform, in the running layout and the saved configuration both.
     *
     * One caller today; the parameter stays because the reason above is about what the method is handed, and the
     * arrival-side branch still asks whether this is a placement or a turn.
     */
    private void placeFacing(String locName, String pointName,
        org.traincontrol.automationui.TilePorts.Side facing)
    {
        if (locName == null) return;

        final org.traincontrol.automation.Layout running = ui.getModel() == null
            ? null : ui.getModel().getAutoLayout();

        final org.traincontrol.automation.Point landing =
            running == null ? null : running.getPoint(pointName);

        // A placement or a turn?  Only placing is an arrival: a turn does not change which way the train CAME in, and
        // `Point.setLocomotive` leaves `arrivedFrom` alone when the occupant does not change, so this asks about
        // placements only.
        final boolean arriving = landing != null && (landing.getCurrentLocomotive() == null
            || !locName.equals(landing.getCurrentLocomotive().getName()));

        String tail = null;

        if (arriving)
        {
            // And where its tail is: the same placement by drag asks and blocks the track behind the train, so this
            // door must too (`behaviour.md` section 4).  Through the same door the paste uses, with the same rule:
            // forced at a terminus, taken from behind the heading at an ordinary station, asked on a square trains may
            // turn at.  This door has no dialog of its own (`GraphLocAssign` has a combo), so it is the prompt, as for
            // a paste.
            //
            // Before the move, so a dismissal can leave the railway as it was.  The unbarred sides here and in the
            // `wouldAsk` below must be the same list (OB-204).
            tail = org.traincontrol.gui.autonomy.ArrivalSidePrompt.forPlacement(running, landing,
                facing == null ? null : facing.name(), mayTurnHere(), ui,
                session == null ? null : session.unbarredArrivalSides(station));

            // A dismissal, and not the other two nulls: `forPlacement` also answers null when the square offers nothing
            // to choose between, and refusing there would be a silent, permanent refusal.
            if (tail == null && org.traincontrol.gui.autonomy.ArrivalSidePrompt.wouldAsk(running, landing,
                mayTurnHere(), session == null ? null : session.unbarredArrivalSides(station)))
            {
                return;
            }
        }

        // The railway's answer decides whether any of this happens: `moveLocomotive` returns false and logs rather than
        // throwing (autonomy running, unknown locomotive or point, target not a destination), and the placement and
        // facing are written into the setup and saved only for a move the railway accepted, because the setup is the
        // half that survives a restart.  A copy `moveLocomotive` declines is closed at this door, which moves with the
        // form that accepts a copy trains may not arrive at, as the paste does.
        //
        // The remaining case is `behaviour.md` section 6a: a menu is grayed when the popup opens and the action fires
        // at the click, so autonomy started from another window in between leaves a live item over a running railway.
        // The refusal is in the method and this caller honors it; its log line is the user-visible half.
        // WHAT THE TRAIN HAD ON THE RAILWAY, before this door moves it.
        org.traincontrol.automation.Point wasOn = null;

        if (running != null)
        {
            for (org.traincontrol.automation.Point each : running.getPoints())
            {
                if (each.getCurrentLocomotive() != null && locName.equals(each.getCurrentLocomotive().getName())) wasOn = each;
            }
        }

        final java.util.List<org.traincontrol.automation.Edge> roadBefore = wasOn == null ? null : wasOn.getArrivedAlong();
        final String sideBefore = wasOn == null ? null : wasOn.getArrivedFrom();
        final org.traincontrol.automationui.TileGraph.TileKey squareBefore = wasOn == null || session == null
            || session.getStationIndex() == null ? null : session.getStationIndex().squareOf(wasOn);

        // EVEN ONTO A COPY TRAINS MAY NOT ARRIVE AT, as the paste and the dialog move (OB-284): the
        // heading chose it, and the railway's three-argument form refuses it, which answered the click with a log line.
        if (!ui.getModel().getAutoLayout().moveLocomotive(locName, pointName, false, true)) return;

        // Whether the setup this door writes to is still the window's once its tail question is answered.
        boolean setupStands = true;

        // What the save before the tail question reconciled, shown with this door's own save (OB-305).
        final org.traincontrol.automationui.AutonomyCompanionStore.Reconciliation[] savedEarly = {null};

        if (session != null)
        {
            // The configuration as well as the running layout: without this the locomotive keeps its old placement
            // there too, and the next build would emit it at two Points, which fromJSON answers by invalidating the
            // whole layout.
            session.placeLocomotive(station, locName);

            // The facing, before the tail question, as the locomotive dialog writes it.  None where no copy can say, as
            // the editor's Place records none (OB-350).
            session.setFacing(station, facing);

            // AND THE TAIL, after `placeLocomotive`, because a change of occupant clears it.  Only for an arrival.
            if (arriving)
            {
                setupStands = org.traincontrol.gui.TailCrossedPrompt.recordTheTail(session, station, running, landing,
                    tail, locName, roadBefore, station != null && station.equals(squareBefore), sideBefore, ui,
                    saved -> savedEarly[0] = saved);
            }
        }

        // WHAT THE SAVE BEFORE THE TAIL QUESTION RECONCILED (OB-305), shown here rather than in front of the
        // question, and once: the save below then finds nothing more, and a refusal over missing pages is said once for
        // each set of them (MT-380).
        AutonomyReport.show(ui, savedEarly[0]);

        // Not a setup the window has let go in the wait, which the reset that replaced it already wrote.  With no facing
        // too: the placement above is written either way (OB-373).
        if (session != null && setupStands)
        {
            try
            {
                // The reconciliation the save returns is shown, not dropped: it reports what the save had to let go (a
                // setting on a page no longer there, a pairing whose partner has gone), and silence would surprise the
                // operator later.
                AutonomyReport.show(ui, session.save());
            }
            catch (java.io.IOException e)
            {
                // The placement stands either way; only the memory of it is at risk, so it is logged, as the other doors do
                ui.getModel().log(e);
            }
        }

        ui.repaintAutoLocList(false);

        // And the station labels, where the facing is shown: the arrow beside a locomotive name is drawn from the
        // recorded facing.
        ui.updateVisiblePoints();
    }


    /**
     * One "-> somewhere" item, dispatching this locomotive along this path.
     *
     * Shared by the top-level list and `More Destinations` (FR-058), so the power check, the failure dialog and the
     * off-thread dispatch cannot fall out of step; this runs on the event thread and `executePath` blocks until the
     * train arrives.
     *
     * @param ui the window, for the model and for the dialogs
     * @param railway the railway the path was found on, which it is sent on
     * @param path the route to take
     * @param locomotive the train to send
     * @return the item
     */
    private JMenuItem destinationItem(TrainControlUI ui, org.traincontrol.automation.Layout railway, List<Edge> path,
        Locomotive locomotive)
    {
        JMenuItem item = new JMenuItem("-> " + stationName(path.get(path.size() - 1).getEnd()));

        // Through the one door both hand doors send through: whether a train may be sent at all (editor, setup, power,
        // this path's standing rules, reversal, the last-moment question, the atomic-routes gate) and the dispatch.
        // Every message there belongs to the main window, which this menu has left by the time an item's action runs.
        item.addActionListener(event -> ui.sendATrainByHand(railway, path, locomotive));

        return item;
    }

    /**
     * The Is Active tick (FR-117), asked again at the click as Edit Locomotive is: a run may have started since the menu
     * opened.
     *
     * @param train the train standing here
     * @param active whether autonomy may run it
     */
    private void setActive(Locomotive train, boolean active)
    {
        if (refusedWhileRunning()) return;

        ui.setLocomotiveActive(session, train, active);
    }

    /**
     * Takes the standing train off this square, and off the setup with it.
     *
     * On the event thread, deliberately: `moveLocomotive` is `synchronized` on the `Layout`, so this can queue behind
     * its holder, but it is a click, offered only when `isAutonomyBusy()` is false, and it changes the railway rather
     * than asking it a question; a worker would let the repaint below run before the change.
     *
     * @param current the Point the train is standing on
     */
    private void removeLocomotiveHere(Point current)
    {
        // ASKED AGAIN AT THE CLICK, as Edit Locomotive is: a route may have begun since the menu opened
        if (refusedWhileRunning()) return;

        // Purge, as the editor's own Remove does: both doors that take a train off a square must pass the same answer,
        // or the locomotive stays in the list of trains to run while standing nowhere.  The railway's answer, then the
        // save: a refusal must not be discarded, or the setup would say the square is empty while the railway still has
        // the train, and the next save from any other door commits that.
        if (!ui.getModel().getAutoLayout().moveLocomotive(null, current.getName(), true)) return;

        // The setup as well, or the next build puts the train back from the configuration.  The facing goes with it: it
        // belonged to that train, not to the square.
        if (session != null)
        {
            session.placeLocomotive(station, null);

            // And written: the two repaints below do not reach the autonomy panel's refresh callback, so without this
            // save a removed train is back at the next launch.  `GraphLocAssign` states the rule: every door that
            // writes the setup saves it.
            try
            {
                AutonomyReport.show(ui, session.save());
            }
            catch (java.io.IOException e)
            {
                // The removal stands either way; only the memory of it is at risk, which is the same
                // answer placeFacing gives beside this.
                ui.getModel().log(e);
            }
        }

        ui.repaintAutoLocList(false);

        // The label still says the locomotive's name until something rewrites it
        ui.updateVisiblePoints();
    }
}
