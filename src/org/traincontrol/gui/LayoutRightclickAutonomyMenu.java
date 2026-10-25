package org.traincontrol.gui;

import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
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
     * Kept so the actions built below can still reach them when they fire, which is long after this
     * constructor has returned.  Everything else here is local, because everything else is used while
     * the menu is being assembled.
     */
    private final TrainControlUI ui;

    private final org.traincontrol.automationui.TileGraph.TileKey station;

    /**
     * The square the pointer was actually over, station or not.
     *
     * Kept apart from the station above because they answer different questions.  Everything about a
     * RUNNING layout (where this train may go, which locomotive is here) is about a station, and there
     * is nothing to say over plain track.  The setup is about the square: whether it should become a
     * station, which way trains may run through it, how long it is.
     */
    private final org.traincontrol.automationui.TileGraph.TileKey here;

    private final org.traincontrol.automationui.AutonomySession session;
    
    /**
     * Builds this menu for a square and shows it, if it has anything to say.
     *
     * The one way in.  The surfaces that offer this menu (a tile, a station caption, the main window's
     * panel and the popup window's panel) all hop to the event thread, build the menu and show it only if it came
     * out non-empty: over plain track with autonomy running and nothing to offer, an empty menu would open a
     * one-item-high grey box under the pointer, which as LayoutLabel's comment puts it "reads as a fault".  The
     * constructor is private, so this is the only way the menu can be shown and the check cannot be missing at one
     * call site.
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
        // A square's menu only with a setup loaded (OB-307): over a square it would offer Start and a train's routes
        // on a railway nobody had loaded.  The diagram's own menu (no square) still opens: with nothing loaded it
        // offers the setup, which is how a setup gets loaded from the diagram.
        if ((station != null || here != null) && (ui == null || !ui.isAutonomyLoaded())) return;

        // The railway is asked first, and not on the event thread.
        //
        // The paths section needs `getPossiblePaths` (`synchronized` on the `Layout`, and a search of the whole
        // graph), then `isOfferableToOperator` and `isChoosableByAutonomy` once per candidate.  A dispatch holds that
        // monitor across a `CONFIGURE_SLEEP` per accessory of a path, so on the event thread a right-click on an idle
        // train during somebody else's departure would freeze the whole window for the length of the switch-throwing
        // (OB-192).
        //
        // Same shape as `CoveredTrackRenderer` and `ReturnHomeTriageRenderer`: the questions go to a worker, the
        // answers come back, and the menu is built from them.  A thread rather than a pool, because this is one
        // short-lived answer per right-click and there is nothing to coalesce.  The menu appears a moment later than
        // the click when the railway is busy: a popup that arrives late is a popup, and a frozen window is a fault.
        //
        // The square is resolved here, on the event thread, and not on the worker.  `getAutonomyPointForTile` goes
        // through `getAutonomySession()`, the LAZY builder that parses every page, can write to disk and can raise a
        // dialog; none of that belongs on a raw thread.  The constructor builds the session on this thread
        // anyway.  What crosses to the worker is the Point, which is a value.
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
                    // AFTER the count, and deliberately.  A heading is not an item, and putting one on
                    // first would turn "nothing to offer here" into a grey box with a station name in it -
                    // which is the fault the check above exists to prevent, arriving by a new road.
                    menu.headline();

                    // STILL ON SCREEN.  There is now a gap between the click and the menu, and
                    // `JPopupMenu.show` asks its invoker where it is on screen - which throws on a
                    // component that has been taken off it.  A page change or a grid rebuild in that
                    // gap is ordinary, so this is a real case rather than a defensive line.
                    if (at.isShowing()) menu.show(at, x, y);
                }
            });
        }, "LayoutRightclickAutonomyMenu options");

        gathering.setDaemon(true);

        gathering.start();
    }

    /**
     * What the paths section of this menu needs from the railway, gathered off the event thread.
     *
     * Every field here is an answer, not a question: the menu builds itself from these and asks the
     * `Layout` nothing.  That is the whole point - see `showFor` for why.
     */
    static final class PathOptions
    {
        /**
         * The train these paths belong to.
         *
         * Kept so the menu can tell that the answer is about the train it is now drawing.  The gather
         * and the build are on different threads with a gap between them, and a train can be moved or
         * removed in that gap; an answer about a different locomotive is not a stale list, it is the
         * wrong list.
         */
        final Locomotive locomotive;

        /** Offerable, and a square autonomy would choose: the top-level list. */
        final List<List<Edge>> shown;

        /** Offerable, but never chosen by autonomy: More Destinations. */
        final List<List<Edge>> other;

        // There is deliberately no count of everything the search returned before this menu dropped the squares it
        // never shows (OB-205): comparing against it made the "..." appear whenever anything at all had been
        // filtered, which is nearly every right-click for a train that cannot reverse.  See `theListWasCutShort`.

        /**
         * The railway these paths were found on, which the destination sends on: a railway replaced between
         * the gather and the click then refuses at its own fence, rather than the new one locking the old one's
         * track.  The Auto tab sends on the railway its list was found on too.
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
     * Whether the menu offers the way out to the autonomy tab - the "..." at the foot of the list.
     *
     * It counts only the destinations this menu could have shown, not the squares it deliberately hides: a
     * switched-off square, one that excludes this train, and, after OB-205, every ordinary terminus whenever the
     * train cannot reverse.  Those are squares the operator went out of their way to switch off, and the autonomy
     * tab lists them.  So the ellipsis means one thing: **there were more destinations than fitted** (FR-058).
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
     * Asks the railway everything the paths section needs, on the caller's thread.
     *
     * MUST NOT BE CALLED ON THE EVENT THREAD, which is what `showFor` exists to arrange and what
     * `testNothingOnTheEventThreadTakesTheRailwaysMonitor` checks: every call in here is
     * `synchronized` on the `Layout`.
     *
     * Answers null wherever there is nothing to list - no setup, an open editor, a busy railway, no
     * train on the square, or a train that is already running.  The menu then simply has no paths
     * section, which is what it had in those cases before.
     *
     * @param ui the application
     * @param current the Point standing on the clicked square, resolved by `showFor` on the event
     *        thread, or null where there is none
     * @return the answers, or null when this square has no paths to offer
     */
    private static PathOptions gatherPathOptions(TrainControlUI ui, Point current)
    {
        // The same gates the constructor applies, in the same order, because a gather that ran where the menu will
        // not draw would take the railway's monitor for an answer nobody reads.  They are asked twice, and the
        // constructor's copy is the one that decides: the operator can start a run between here and the build, and a
        // list gathered a moment before that must not appear on a menu whose other items already know.
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


        // Switched-off squares are not on this menu, only in the autonomy tab.  This menu is the quick way to send a
        // train somewhere ordinary, and a square deliberately taken out of use is not that.  Filtered rather than
        // greyed: a greyed item still costs a line and still has to be read past.  The autonomy tab lists
        // everything, which is what the ellipsis is for.
        List<List<Edge>> shownPaths = new java.util.ArrayList<>();

        // The squares autonomy would never choose go in their own submenu, More Destinations (FR-058): the menu shows
        // only active stations that can be chosen in full autonomy.  The cap is the reason this matters: a parking
        // track that autonomy will never pick would cost one of the twelve lines an ordinary platform wanted.
        // Asked through `isChoosableByAutonomy`, the same predicate the "no available paths" window and the diagram's
        // caption rule ask, so this does not become a third answer to that question.
        List<List<Edge>> otherPaths = new java.util.ArrayList<>();

        for (List<Edge> path : paths)
        {
            Point end = path.get(path.size() - 1).getEnd();

            // Off this menu altogether: switched off, or excluded for this train.  The rule is named on the layout
            // rather than written out here, because this class is package-private and a rule nothing can reach is a
            // rule nothing can test.  Its javadoc carries both rulings.  They are not counted towards the ellipsis
            // either: the autonomy tab lists them.
            if (!running.isOfferableToOperator(end, locomotive))
            {
                continue;
            }

            // Which of the two lists is asked about the square and deliberately not about this train:
            // `isOfferableToOperator` above has already asked everything that is about the train.  What is left is a
            // property of the square (a reversing point, a square marked as not an automatic destination), and the
            // square form is the same predicate the "no available paths" window and the diagram's captions ask.
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
     * Puts the clicked square's name at the top, greyed.
     *
     * OB-112.  With a setup loaded the whole menu is about a square (this train, these paths, that home), and the
     * menu covers the square while it is open, so the name goes at the top, greyed and bold like the autonomy
     * editor's own menu.
     *
     * The name comes from the session rather than from lines written out again here, so the two menus cannot call
     * one square by different names while both are on screen.
     *
     * Inserted rather than added, because by the time this runs the menu is built, and it runs at all only once
     * showFor knows there is something to head.
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
     * @param station the sensor the caption is about, or null for the diagram-wide menu.  A square rather than a
     *        name: a station is several Points and none of them is called what the caption says, so resolving one
     *        by its text finds nothing.
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

        // Nothing here while an editor has the diagram.  An open editor holds its own unsaved copy of the setup, so
        // anything done from this menu would be done against a picture that is about to be overwritten silently: a
        // locomotive placed here vanishes when the editor saves, and one removed comes back.  The Autonomy menu is
        // shut for the same reason.  Shown and disabled with the reason on it, so a right-click that does nothing
        // does not read as a broken menu.
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
            // The choice between Start and Graceful Stop asks isAutonomyBusy(), as every item inside this
            // branch does for its own enabled state.  isAutoRunning() is cleared the instant Graceful Stop is
            // pressed, while the trains keep moving until they reach their next station; isAutonomyBusy() (what
            // Layout.isRunning() feeds) is true for the whole coast-down, so the Stop item stays offered, and Start
            // does not appear, until the trains actually stop.
            if (!ui.isAutonomyBusy())
            {
                menuItem = new JMenuItem(I18n.t("autolayout.ui.menuStartAutonomy"));

                // Greyed when it could not start (OB-050).  canStartAutonomy asks whyAutonomyStartIsRefused's own
                // number, so the control that OFFERS an action asks the predicate the guard asks.  (The Start button
                // itself is left enabled and explains at press time, so its enabled state is not that predicate.)
                // Asked as few times as the answers allow, and this is not tidiness: once where Start
                // is offered, and where it is not, once more for both sentences below.
                //
                // Both reach AutonomySession.check(), which is not cached: it rebuilds the termini and turn-around
                // sets over every point in the graph, so asking four times would be four full walks of the railway
                // on the event thread, on every right-click of a station, to decide one enabled flag and one tooltip.
                boolean canStart = ui.canStartAutonomy();

                // BOTH SENTENCES FROM ONE READING: Start's tooltip and the Return Home item's, where
                // Start is not offered.  Asked apart over a broken setup - which is when the operator
                // right-clicks to find out what is wrong - they walked the setup four more times.  Where Start
                // is offered the setup has no errors, and neither is asked.
                String[] why = canStart ? null : ui.whyStartAndAHandSendAreRefused();

                menuItem.setEnabled(canStart);

                if (!canStart)
                {
                    // And say which of the FOUR reasons it is (the fourth, a layout on the Central Station).
                    // The tooltip must not be the waiting-for-trains message whenever Start is off
                    // for another reason: `canStartAutonomy` also refuses on `hasErrors()`, which covers a graph that
                    // will not build at all with no finding turned up, where the count that message names is zero.
                    // `errorCannotBuildDetailOne` is the load door's own wording for exactly this state.
                    // THROUGH THE ONE RULE (MT-263): the wording asks how many blocking problems there were, so
                    // three of them do not read as one.
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
                // from the station (MT-069): `station` is autonomyStationAt, which returns null unless the square has
                // been DESIGNATED a station, so on a pass-through Point with a locomotive on it the whole block below
                // would vanish (no Remove, no paths, no facing).  The editor works from the square, and so does this.
                Point current = ui.getAutonomyPointForTile(station != null ? station : here);

                // A destination, or anywhere a train is actually standing.  Gated on being a destination alone, a
                // locomotive on a copy that is not one would have no menu at all (no remove, no paths): arrival
                // restrictions make that reachable, as barring a side makes THAT copy a non-destination while a train
                // can still be placed on it by hand.  The remove item hangs off the locomotive, not the designation.
                if (current != null && (current.isDestination() || current.getCurrentLocomotive() != null))
                {
                    // Get the locomotive at this station
                    Locomotive locomotive = current.getCurrentLocomotive();

                    // If we want to view paths, locomotive must not be running
                    //
                    // A PER-LOCOMOTIVE gate, where AutoLocomotiveStatus asks isAutoRunning() about the whole layout,
                    // so the two disagree.
                    //
                    // KNOWN LIMITATION, closed on the owner's ruling (OB-164): "The user can rely on full autonomy or
                    // the panels to send trains more clearly."  Both work in non-atomic mode, so what is lost is one
                    // of three ways to the same thing.  Neither getPossiblePaths nor isPathClear branches on
                    // atomicRoutes, so the cause is state a non-atomic run leaves behind.  Left here so the next
                    // reader who notices the two surfaces disagree finds the ruling rather than re-opening it.
                    //
                    // And the answer is the one gathered for this train, never a fresh question: `gatherPathOptions`
                    // applied the running test off the event thread, and equality here says the answer is about the
                    // locomotive standing here now rather than the one standing here when the right-click landed.
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
                        // square marked as not an automatic destination.  Each is somewhere the operator may
                        // legitimately send a train by hand ("filtering at selection, never refusing at execution"), and
                        // neither is something autonomy would pick on its own.  A terminus is not here, because a
                        // non-reversible train may back into one and it belongs in the base list; nor is an excluded
                        // square, which is off the menu entirely rather than demoted.
                        //
                        // Uncapped.  The cap on the top level is about competing with the rest of the menu for
                        // the first screenful, which a submenu does not do; a submenu competes with the height of the
                        // screen, and Swing gives an over-tall JMenu no scroller unless one is installed.  On the
                        // author's own configuration 20 of 71 squares are marked as not automatic destinations and 5 more
                        // can reverse, and `withoutGoingNowhere` reduces to distinct destinations, so the ceiling is
                        // about twenty-five items, which fits.  A railway that exceeded a screen would want a scroller
                        // rather than a second ellipsis.
                        if (!otherPaths.isEmpty())
                        {
                            // The heading, when the top-level list did not already add it: both the separator
                            // and the locomotive's name are gated on the TOP-LEVEL list being non-empty, and this sits
                            // outside that gate, so a train with nothing automatic to offer would otherwise get a bare
                            // "More Destinations" with no separator and no name to say whose it was.
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

                        // There is no second way out.  The "..." means "there were more ordinary destinations than
                        // fitted" (the owner's rule), and a train with none is not
                        // that: it is a train with nowhere to go, which the menu says by having nothing under the
                        // locomotive's name.  Nothing is lost that is not said better elsewhere: "No available paths" in
                        // the locomotive list names every station and the reason each was refused, and the setup
                        // editor's *Why not Moving?* answers the same question on the diagram.  So the item is decided in
                        // exactly ONE place, the loop above through `theListWasCutShort`, and
                        // `regression.testTheDestinationDoorsAgree` asserts there is only one.
                    }

                    addSeparator();

                    // Station name label
                    // The STATION's name, not the copy's.  A square is several Points named apart ("BottomMainA
                    // (eastbound)") so that a running log can say which one a train is on, but that is a name for the
                    // model's benefit: a user pointing at a platform created a station, not an eastbound one and a
                    // westbound one.
                    menuItem = new JMenuItem(stationName(current));
                    menuItem.setEnabled(false);
                    add(menuItem);

                    // Place a different locomotive at this station, FACING a chosen way.
                    //
                    // A square is several Points, one per side a train can arrive by, and they are not interchangeable:
                    // each copy can only leave the way its own facing allows, so "put it here" must pick the copy.  A
                    // copy chosen carelessly is a Point whose only moves are the ones the split exists to forbid, and
                    // autonomy could see the locomotive and not route it anywhere.
                    // Offered only on a square that is a destination.  Which copy of it the train stands on is its
                    // heading's (below), including a copy trains may not arrive at (OB-284), where Why not Moving? says so.
                    if (ui.getActiveLoc() != null
                        && current.isDestination()
                        && !ui.aRunHoldsTheSetup()
                        && !ui.getActiveLoc().equals(locomotive))
                    {
                        // Chosen for them, from the ways a train could actually leave.  Asking is the wrong question
                        // here: on the diagram a user is pointing at a platform, not at one of the several Points it
                        // became.  The copy is the one facing the way the train already faces, wherever a copy facing
                        // that way has a way out (OB-284), and otherwise the first this menu offers, never a draw
                        // (OB-296: "Yes, keep the train's heading.").  Turning it is the facing item below.
                        //
                        // And where NO copy can leave, offered all the same, as the editor's Place is (OB-350):
                        // `placeSomewhereLegal` stands the train where the build would and records no facing.
                        // The setup check still warns about the square.
                        final java.util.List<String> usable = placeableCopies();

                        // OFFERED ON THE ACTION'S OWN QUESTION: every copy the train could leave by, which is
                        // what its heading is kept over, including one trains may not arrive at (`placeableCopies` never
                        // offers one of those).  Its order still decides the copy where no heading holds, and any other
                        // copy a train could leave by comes after it.
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

                    // Turn the standing train round.
                    //
                    // Placing keeps the heading the train had, which may not be the one it has on the railway now, and a
                    // locomotive put down by hand points whichever way the setup last recorded, so there has to be a way
                    // to say "no, it faces the other way" without taking it off and putting it back.  Only the copies it
                    // could actually leave from, as for placing: a train turned to face a wall cannot move.
                    // Every setup item here is left out while a route runs as well (`aRunHoldsTheSetup`).
                    if (current.getCurrentLocomotive() != null && !ui.aRunHoldsTheSetup())
                    {
                        // There is one "Facing" menu, the one further down (MT-086): it names the train it is about, reads
                        // as a choice rather than a list of places, and is the same menu the setup editor offers, so the
                        // two surfaces cannot disagree about the same square.  Choosing which COPY of a split square the
                        // train stands on is the same decision as the facing, since a copy IS a side to arrive from.

                        menuItem = new JMenuItem(
                            I18n.f("layout.ui.menuRemoveLocomotive", current.getCurrentLocomotive().getName())
                        );
                        // A METHOD RATHER THAN A LAMBDA HERE, so that this constructor asks the railway nothing at all:
                        // `moveLocomotive` is `synchronized` on the `Layout`, and the guard would otherwise have
                        // to allow the constructor as a whole, which would also allow the next path search somebody adds.
                        menuItem.addActionListener(event -> removeLocomotiveHere(current));

                        add(menuItem); 
                    }
                    
                    if (!ui.getModel().getAutoLayout().getLocomotivesToRun().isEmpty() && !ui.aRunHoldsTheSetup())
                    {
                        // Edit locomotive
                        menuItem = new JMenuItem(GraphLocAssign.menuLabelFor(current));
                        menuItem.addActionListener(event ->
                        {
                            // NOT WHILE TRAINS RUN.  Offered only at rest, and a click after a run began would
                            // write the placement, the facing and the tail into the setup and save it.  Asked at the
                            // click and again at OK.
                            if (refusedWhileRunning()) return;

                            // THE SETUP GOES IN WITH IT: the dialog offers the arrival
                            // side now, and works this square's sides out from the build to do it.
                            GraphLocAssign edit = new GraphLocAssign(ui, current,
                                ui.getAutonomySession(),
                                ui.getModel() == null ? null : ui.getModel().getAutoLayout());

                            int dialogResult = JOptionPane.showOptionDialog(
                                ui,
                                // The form WITH the arrival side under it, wrapped in one place for
                                // both doors - see `asDialogContent`.
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

                                // AND INTO THE SETUP: an assignment made from the diagram must survive the next
                                // load of a configuration.  Through the shared door rather than a copy of the lines inside
                                // Autonomy Setup, so the two cannot drift apart.
                                GraphLocAssign.commitAndRecord(edit);

                                ui.updateVisiblePoints();
                                ui.repaintAutoLocList(false);
                            }
                        }); 

                        add(menuItem);
                    }

                    // WHETHER IT RUNS (FR-117): an "active" checkbox, shown if there is a locomotive at that station.  Only
                    // at rest; while trains run the Auto tab's pause button says the same.  Read off the train, which is
                    // what autonomy and Return Home read.
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

                    // Which locomotive belongs here is offered from the setup menu below, not from this one.  That menu
                    // works from the SQUARE rather than from a Point, so the question of WHICH Point to ask (the occupied
                    // copy speaks for a square, and a train can stand on a copy that is not itself a destination) does not
                    // arise.
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
     * Puts the active locomotive on one named copy of this square, and remembers which way.
     *
     * Both halves matter.  Placing on the right copy is what lets autonomy move it NOW; recording the facing is what puts
     * it back on the same copy the next time the graph is built, which is every load.
     *
     * @param pointName the copy to place on
     * @param facing which way it is pointing, or null when the square has only one copy
     */
    /**
     * The whole Autonomy menu, as a submenu, for when nothing is running.
     *
     * Everything in it was already reachable from the menu bar, but somebody working on their railway is looking at the
     * diagram.  This is the same menu rather than a copy, so one place decides what autonomy offers.
     *
     * Its own instance, because a Swing component has one parent and the menu bar already owns the other one.  It costs
     * nothing: the menu builds its items when it is opened rather than when it is made.
     *
     * Under a parent entry so it stays out of the way of the everyday right-click on the train or the square.  Only while
     * nothing is running: half of what is in there rebuilds the layout, and a menu full of things that say no is worse
     * than a menu that waited.
     */
    /**
     * Everything the autonomy editor offers on this square, folded into this menu.
     *
     * The editor's menu rather than the window's Autonomy menu.  The one in the menu bar is about the setup as a whole
     * (configurations, imports, delete everything); what somebody right-clicking a platform wants is what the editor
     * gives them on the same platform there: make this a station, face the train this way, bar that arrival, set the
     * length.  Borrowed from the panel that builds it, so the two can never drift.
     *
     * Under a parent entry, because it is a long menu and most right-clicks on a running layout are about sending a
     * train somewhere.  Nothing while autonomy is running: the setup describes track that trains are on right now, and
     * an editor open over it is already refused for the same reason.
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

        // FR-026: the way out to the full editor, at the foot of the settings it is the deep end of.  Everything above is
        // a single square's settings; the editor is where the diagram as a whole is worked on.  "Inappropriate" is not
        // decided here: it is asked of the window that does the opening, which hands back the reason its own refusal
        // would give, so the item is live exactly when pressing it would work and says why when it is not.  A menu item
        // that offers an action and a guard that permits it have to ask one question.
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
     * Drops the paths that end where the train already is.
     *
     * A square is several Points, so "somewhere else" and "a different Point" are not the same thing: a train at
     * BottomMainB must not be offered BottomMainB, the copy facing the other way, which is the platform under its own
     * wheels.
     *
     * Filtered here rather than in the layout, which cannot tell: its only candidate key is the
     * sensor, and a station and its approach guard share one while being genuinely two places.  What
     * makes two Points one place is the square they were built from, and only the setup knows that.
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
     * What to call a Point when a person is reading it.
     *
     * The built graph names the copies of a square apart - "BottomMainA (eastbound)" - so a running
     * log can say which one a train is on.  That is a name for the model.  A user pointing at a
     * platform did not create an eastbound platform and a westbound one; they created a station, and
     * showing them the internals invites the question of which is the real one.
     *
     * Falls back to the Point's own name when the setup cannot be asked, which is better than
     * showing nothing.
     *
     * @param point a Point of the running graph
     * @return the station name a reader would recognise
     */
    private String stationName(Point point)
    {
        return session == null ? (point == null ? "" : point.getName())
            : session.getStationIndex().describe(point);
    }

    /**
     * The copies of this square a train could actually be driven away from.
     *
     * A copy with no outgoing edge is one the split made arrivable and not leavable.  Placing there
     * is placing a train that cannot move, which autonomy reports much later as a configuration
     * that will not run.
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

            // A copy the train can actually LEAVE, in preference to one it merely sits on.  "Has an outgoing edge" is
            // not the same question: a copy of a split square can have somewhere to go and nowhere to be SENT
            // (everything it reaches is a plain point, a reversing point or parking), and a train put on such a copy
            // never moves.  On the sample layout, Tunnel (northbound) offers routes and Tunnel (southbound) offers none.
            //
            // Barred copies are not on this list: barring an arrival side makes that copy a non-destination.  The
            // right-click Place still stands a train on one where its heading is that way (OB-284); this list
            // is the order a copy is taken in where no heading holds, and a barred copy comes after it.
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

        // Nothing open is not the same as nowhere to go.  A square whose copies are all stranded is
        // still somewhere a train physically stands, and refusing to place one there would be a
        // different message from the "no way out" one this list produces.
        if (!out.isEmpty()) return out;

        // NOT A BARRED COPY, FIRST.  `shut` is the copies whose `isDestination()` is explicitly false.
        // The right-click Place accepts them (the form of `moveLocomotive` that does) and offers the item over
        // every copy a train could leave by (`departableFacingsFor`); this list is only the order a copy is taken in
        // where the train's heading holds none.  Stranded copies stay, barred or not: standing a train somewhere
        // autonomy cannot send it anywhere is a thing an operator may legitimately want to do.
        return stranded;
    }

    /**
     * Places the active locomotive on the copy it could leave from that faces the way it already faces (OB-296).
     *
     * The heading is read before the move, from the copy the train stands on (`facingOf`), and the rule is the
     * paste's (OB-270, OB-284), so the same train placed the same way always faces the same way.
     *
     * Where no copy can leave, the copy the build stands an unfaced train on, with no facing recorded - the editor's Place
     * (OB-350).
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
     * The copy a train is put on by the right-click Place, as the paste chooses it (OB-296): the heading
     * over every copy the train could leave by - one trains may not arrive at included (OB-284: *"for barred arrival
     * directions, keep the direction"*) - by `AutonomySession.facingAfterAPaste`; then the copy facing that way by
     * `copyFacing`, the plain one before its turning twin.  Where no heading holds, the first copy the menu offers - the
     * same answer every time.
     *
     * The menu's own list is not enough: the copy facing the heading at a square with a barred side is no station, so it
     * is never on that list.
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
     * Asked of the SETUP, because `canReverse` never reaches the running layout - the builder
     * expresses it by splitting the square and says so.  The same question `TrainControlUI.mayTurnHere`
     * asks for the paste door and `GraphLocAssign` for the dialog, and the same answer.
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
     * Takes the locomotive rather than reading the active one, because the two things this can mean are different
     * trains.  Placing means the active locomotive, as the menu item says; turning means the one already standing
     * there.  Reading the active one for a turn would pick THAT train up and put it down on this platform when some
     * other locomotive is selected on the keyboard, in the running layout and the saved configuration both.
     *
     * One caller today, not two: the turning door was retired.  The parameter stays because the reason above
     * is about what the method is handed, and the arrival-side branch below still asks whether this is a placement or
     * a turn.
     */
    private void placeFacing(String locName, String pointName,
        org.traincontrol.automationui.TilePorts.Side facing)
    {
        if (locName == null) return;

        final org.traincontrol.automation.Layout running = ui.getModel() == null
            ? null : ui.getModel().getAutoLayout();

        final org.traincontrol.automation.Point landing =
            running == null ? null : running.getPoint(pointName);

        // A PLACEMENT, OR A TURN?  The two callers mean different things and only one of them is an
        // arrival: placing puts the active locomotive down somewhere it was not, while "face east"
        // turns the train already standing here.  A turn does not change which way the train CAME in,
        // and `Point.setLocomotive` leaves `arrivedFrom` alone when the occupant does not change - so
        // this asks about placements only, and a turn goes through untouched.
        final boolean arriving = landing != null && (landing.getCurrentLocomotive() == null
            || !locName.equals(landing.getCurrentLocomotive().getName()));

        String tail = null;

        if (arriving)
        {
            // AND WHERE ITS TAIL IS: the same placement by drag asks the question and blocks the track behind
            // the train, so this door must too (`behaviour.md` section 4: the missing arrival side is set from the data,
            // by the user, or randomly).
            //
            // Through the same door the paste uses, with the same rule behind it: forced at a terminus, taken from behind
            // the heading at an ordinary station, asked on a square trains may turn at.  This door has no dialog of its
            // own to offer it in (`GraphLocAssign` does, with a combo), so here it is the prompt, as it is for a paste.
            //
            // BEFORE THE MOVE, so that a dismissal can leave the railway as it was; asked afterwards there would be
            // nothing left to decline.
            // THE UNBARRED SIDES, here and in the `wouldAsk` below it (OB-204): the two have to be handed the same list
            // or one asks a question the other does not expect an answer to.
            tail = org.traincontrol.gui.ArrivalSidePrompt.forPlacement(running, landing,
                facing == null ? null : facing.name(), mayTurnHere(), ui,
                session == null ? null : session.unbarredArrivalSides(station));

            // A DISMISSAL, and not the other two nulls: `forPlacement` also answers null when
            // the square offers nothing to choose between, and refusing to place there would be a
            // silent, permanent refusal with no dialog to explain it.
            if (tail == null && org.traincontrol.gui.ArrivalSidePrompt.wouldAsk(running, landing,
                mayTurnHere(), session == null ? null : session.unbarredArrivalSides(station)))
            {
                return;
            }
        }

        // THE RAILWAY'S ANSWER DECIDES WHETHER ANY OF THIS HAPPENS.
        //
        // `moveLocomotive` returns false and logs rather than throwing, in four cases: autonomy is running, the
        // locomotive is unknown, the point is unknown, and the target is not a destination.  The placement and the
        // facing are written into the setup AND SAVED only for a move the railway accepted, because the setup is the
        // half that survives a restart.  A copy `moveLocomotive` declines is closed at this door: it moves with the form
        // that accepts a copy trains may not arrive at, as the paste does.
        //
        // The remaining case is the one `behaviour.md` section 6a states as a rule: a menu is greyed when the popup
        // OPENS and the action fires when it is clicked, so autonomy started from another window in between leaves a
        // live item over a running railway.  The refusal is in the method, and this caller honours it.
        //
        // The log line `moveLocomotive` already writes is the user-visible half, so nothing is added here.
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
            // The CONFIGURATION as well as the running layout.  Moving a train in the layout leaves where it was; without
            // this the locomotive keeps its old placement in the configuration too, and the next build would emit it at
            // two Points, which fromJSON answers by invalidating the whole layout.
            session.placeLocomotive(station, locName);

            // THE FACING, BEFORE THE TAIL QUESTION, as the locomotive dialog writes it: it was chosen before,
            // and written after it was dropped with a late answer - where only the road can have gone stale.  None where
            // no copy can say, as the editor's Place records none (OB-350).
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
                // The reconciliation the save returns is SHOWN, not dropped: a save reconciles the setup against
                // the diagram and reports what it had to let go (a setting on a page that is no longer there, a pairing
                // whose partner has gone), and dropped silently those changes would surprise the operator later.
                AutonomyReport.show(ui, session.save());
            }
            catch (java.io.IOException e)
            {
                // The placement stands either way; only the memory of it is at risk, so it is logged, as the other doors do
                ui.getModel().log(e);
            }
        }

        ui.repaintAutoLocList(false);

        // And the station labels, which is where the facing is actually shown: the arrow beside a locomotive name is
        // drawn from the recorded facing, so changing it without rewriting the labels leaves the diagram asserting the
        // old direction.
        ui.updateVisiblePoints();
    }


    /**
     * One "-> somewhere" item, dispatching this locomotive along this path.
     *
     * Shared by the top-level list and `More Destinations` (FR-058).  Two copies of a menu item that starts a train
     * would be two places for the power check, the failure dialog and the off-thread dispatch to fall out of step, and
     * the thread is the part that matters: this runs on the event thread, and `executePath` blocks until the train
     * arrives.
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

        // THROUGH THE ONE DOOR both hand doors send through: whether a train may be sent at all (the editor;
        // the setup; the power; this path's standing rules; the reversal question; the last-moment question again; the
        // atomic-routes gate) and the dispatch.  Every message there belongs to the main window, not to this menu, which
        // has left its window by the time an item's action runs.
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
     * ON THE EVENT THREAD, deliberately and knowingly.  `moveLocomotive` is `synchronized` on
     * the `Layout`, so this can queue behind a holder of that monitor - but it is a menu item the
     * operator has just clicked, it is offered only when `isAutonomyBusy()` is false, and what it does
     * is change the railway rather than ask it a question.  Bouncing a change of state off to a worker
     * would mean the repaint below runs before the change it is repainting.
     *
     * @param current the Point the train is standing on
     */
    private void removeLocomotiveHere(Point current)
    {
        // ASKED AGAIN AT THE CLICK, as Edit Locomotive is: a route may have begun since the menu opened
        if (refusedWhileRunning()) return;

        // PURGE, as the editor's own Remove does (C15): both doors that take a train off a square must pass the same
        // answer.  Without the purge the locomotive stays in the list of trains to run while standing nowhere, goes on
        // showing in the Autonomy tab, and runLocomotives logs it as started and spawns a thread that idles for the rest
        // of the session; the railway would be keeping a place for a train that is not on it.
        // THE RAILWAY'S ANSWER, AND THEN THE SAVE: a refusal must not be discarded, or the setup would
        // say the square is empty while the railway still has the train on it, and the next save from any other door
        // commits that.
        if (!ui.getModel().getAutoLayout().moveLocomotive(null, current.getName(), true)) return;

        // The setup as well, or the next build puts the train back: the configuration still records it
        // standing here, and the running layout is rebuilt from the configuration.  The facing goes
        // with it - it belonged to that train, not to the square.
        if (session != null)
        {
            session.placeLocomotive(station, null);

            // AND WRITTEN: the two repaints below do not reach the autonomy panel's refresh callback, so without this save
            // a train removed here is back on the square at the next launch.  `GraphLocAssign` states the rule: every door
            // that writes the setup saves it (the paste door, the facing menu, and placeFacing beside this one).
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
