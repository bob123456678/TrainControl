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
    public static final int MAX_PATHS = 12;

    /**
     * Kept so the actions built below can still reach them when they fire, which is long after this
     * constructor has returned.  Everything else here is local, because everything else is used while
     * the menu is being assembled.
     */
    private final TrainControlUI ui;

    private final org.traincontrol.automationui.TileGraph.TileKey station;

    private final org.traincontrol.automationui.AutonomySession session;
    
    /**
     * @param station the sensor the caption is about, or null for the diagram-wide menu.  A square
     *        rather than a name: a station is several Points now and none of them is called what the
     *        caption says, so resolving one by its text found nothing and the menu silently lost every
     *        item below the lookup.
     */
    public LayoutRightclickAutonomyMenu(TrainControlUI ui,
        org.traincontrol.automationui.TileGraph.TileKey station)
    {
        this.ui = ui;
        this.station = station;
        this.session = ui.getAutonomySession();

        JMenuItem menuItem;

        // Nothing here while an editor has the diagram.
        //
        // An open editor holds its own unsaved copy of the setup, so anything done from this menu is
        // done against a picture that is about to be overwritten - and overwritten silently: a
        // locomotive placed here vanishes when the editor saves, and one removed comes back.  The
        // Autonomy menu has been shut for exactly this reason since the editor existed.  The diagram's
        // own menu was simply missed, and it is the one people actually reach for.
        //
        // Shown and disabled rather than left out, with the reason on it, so a right-click that does
        // nothing does not read as a broken menu.
        if (ui.isLayoutEditorOpen())
        {
            menuItem = new JMenuItem(I18n.t("autosetup.ui.menuEditorOpen"));
            menuItem.setEnabled(false);
            add(menuItem);

            return;
        }
        
        if (!ui.getModel().hasAutoLayout())
        {
            // Nothing is running because nothing is set up, which is exactly when somebody needs the
            // setup menu most - and the only place they would think to look is the diagram in front
            // of them.
            addSetupMenu();
        }

        if (ui.getModel().hasAutoLayout())
        {     
            if (!ui.getModel().getAutoLayout().isAutoRunning())
            {
                menuItem = new JMenuItem(I18n.t("autolayout.ui.menuStartAutonomy"));
                menuItem.addActionListener(event -> 
                {
                    try
                    {
                        ui.requestStartAutonomy();
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(this, e.getMessage());
                    }
                });

                add(menuItem);

                HomeLocomotiveMenu.addReturnHomeItem(this, ui);

                // The Point standing on that square, preferring one with a train on it
                Point current = ui.getAutonomyPointForTile(station);

                // A destination, or anywhere a train is actually standing.
                //
                // Gated on being a destination alone, a locomotive on a copy that is not one had no
                // menu at all - no remove, no paths, nothing - and arrival restrictions made that
                // reachable: barring a side makes THAT copy a non-destination, and a train can still
                // be placed on it by hand.  It is the same trap the autonomy editor had, where the
                // remove item hung off the designation rather than off the locomotive.
                if (current != null && (current.isDestination() || current.getCurrentLocomotive() != null))
                {
                    // Get the locomotive at this station
                    Locomotive locomotive = current.getCurrentLocomotive();

                    // If we want to view paths, locomotive must not be running
                    if (locomotive != null && !ui.getModel().getAutoLayout().getActiveLocomotives().containsKey(locomotive))
                    {
                        List<List<Edge>> paths = withoutGoingNowhere(ui,
                            ui.getModel().getAutoLayout().getPossiblePaths(locomotive, true));

                        paths.sort((List<Edge> p1, List<Edge> p2) -> Edge.pathToString(p1).compareTo(Edge.pathToString(p2)));

                        if (!paths.isEmpty())
                        {
                            addSeparator();

                            // Show the locomotive name for reference
                            menuItem = new JMenuItem(locomotive.getName());
                            menuItem.setEnabled(false);
                            add(menuItem);
                        }

                        // Counted rather than measured off the menu, which also holds the Start item,
                        // the Return Home item, a separator and the locomotive's name - so the list
                        // was cut about four paths early, and would have moved again the next time
                        // anything was added above it.
                        int shown = 0;

                        for (List<Edge> path : paths)
                        {
                            menuItem = new JMenuItem("-> "
                                + stationName(path.get(path.size() - 1).getEnd()));
                            menuItem.addActionListener(event -> 
                            {
                                try
                                {
                                    // TODO there is commonality with AutoLocomotiveStatus - reuse code
                                    new Thread(() ->
                                    {
                                        if (!ui.getModel().getPowerState())
                                        {
                                            javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, I18n.t("autolayout.ui.powerOnToStart")));
                                        }
                                        else
                                        {
                                            ui.ensureGraphUIVisible();

                                            boolean success = ui.getModel().getAutoLayout().executePath(
                                                path, locomotive, locomotive.getPreferredSpeed(), null
                                            );

                                            if (!success)
                                            {
                                                javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, I18n.t("autolayout.ui.autoFailedCheckLog")));
                                            }
                                        }
                                    }).start();
                                }
                                catch (Exception e)
                                {
                                    JOptionPane.showMessageDialog(this, e.getMessage());
                                }
                            });    

                            add(menuItem);

                            if (++shown >= MAX_PATHS && paths.size() > MAX_PATHS)
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
                                        JOptionPane.showMessageDialog(this, e.getMessage());
                                    }
                                });    

                                add(menuItem);
                                break;
                            }
                        }
                    }

                    addSeparator();

                    // Station name label
                    // The STATION's name, not the copy's.
                    //
                    // A square is several Points and they are named apart - "BottomMainA
                    // (eastbound)" - so that a running log can say which one a train is on.  That is
                    // a name for the model's benefit: a user pointing at a platform did not create
                    // an eastbound one and a westbound one, they created a station, and being shown
                    // the internals of how it is modelled invites them to wonder which is the real
                    // one.
                    menuItem = new JMenuItem(stationName(current));
                    menuItem.setEnabled(false);
                    add(menuItem);

                    // Place a different locomotive at this station, FACING a chosen way.
                    //
                    // A square is several Points - one per side a train can arrive by - and they are
                    // not interchangeable: each copy can only leave the way its own facing allows.  So
                    // "put it here" is not a complete instruction, and this used to answer it with
                    // whichever copy came first, which put the train on a Point whose only moves are
                    // the ones the split exists to forbid.  Autonomy could see the locomotive and
                    // could not route it anywhere.
                    // Putting one down is still only at a destination, as it was: somewhere trains
                    // may not stop is not somewhere to start one from.
                    if (ui.getActiveLoc() != null
                        && current.isDestination()
                        && !ui.isAutonomyBusy()
                        && !ui.getActiveLoc().equals(locomotive))
                    {
                        // Chosen for them, from the ways a train could actually leave.
                        //
                        // Asking was tried and is the wrong question here: on the diagram a user
                        // is pointing at a platform, not at one of the several Points that platform
                        // became, and the sides mean nothing at that moment.  Any legal copy will
                        // do - autonomy drives the train out and learns the real facing from where
                        // it goes - so one is taken at random, and choosing a direction stays in
                        // the autonomy editor, where directions are what the reader is looking at.
                        //
                        // And if NO copy can leave, placing here is placing a train that cannot
                        // move.  Refused with a reason, rather than accepted and reported much
                        // later as a setup that will not run.
                        final java.util.List<String> usable = placeableCopies();

                        menuItem = new JMenuItem(
                            I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName())
                        );

                        menuItem.setEnabled(!usable.isEmpty());

                        if (usable.isEmpty())
                        {
                            menuItem.setToolTipText(I18n.t("layout.ui.hintNoWayOut"));
                        }
                        else
                        {
                            menuItem.addActionListener(event -> placeSomewhereLegal(usable));
                        }

                        add(menuItem);
                    }

                    // Turn the standing train round.
                    //
                    // Placing picks a way out at random, and a locomotive put down by hand is
                    // pointing whichever way the setup last recorded - so there has to be a way to say
                    // "no, it faces the other way" without taking it off and putting it back.
                    //
                    // Only the copies it could actually leave from, for the same reason placing is:
                    // turning a train to face a wall is not an orientation, it is a train that cannot
                    // move.
                    if (current.getCurrentLocomotive() != null && !ui.isAutonomyBusy())
                    {
                        java.util.List<String> ways = placeableCopies();

                        // The train standing here, captured now.  Autonomy is not running - the menu
                        // is gated on that - so nothing but the user can move it between opening the
                        // menu and choosing from it.
                        final String standing = current.getCurrentLocomotive().getName();

                        if (ways.size() > 1)
                        {
                            javax.swing.JMenu facing = new javax.swing.JMenu(
                                I18n.t("layout.ui.menuFacing"));

                            java.util.Map<String, org.traincontrol.automationui.TilePorts.Side> all =
                                session.facingsFor(station);

                            for (String name : ways)
                            {
                                final org.traincontrol.automationui.TilePorts.Side side = all.get(name);

                                if (side == null) continue;

                                final String copy = name;

                                javax.swing.JCheckBoxMenuItem which =
                                    new javax.swing.JCheckBoxMenuItem(
                                        I18n.f("layout.ui.menuPlaceFacing", side.toString()),
                                        side == session.getFacing(station));

                                which.addActionListener(event -> placeFacing(standing, copy, side));

                                facing.add(which);
                            }

                            if (facing.getItemCount() > 0) add(facing);
                        }

                        menuItem = new JMenuItem(
                            I18n.f("layout.ui.menuRemoveLocomotive", current.getCurrentLocomotive().getName())
                        );
                        menuItem.addActionListener(event ->
                        {
                            ui.getModel().getAutoLayout().moveLocomotive(
                                null,
                                current.getName(),
                                false
                            );

                            // The setup as well, or the next build puts the train back: the
                            // configuration still records it standing here, and the running layout is
                            // rebuilt from the configuration.  The facing goes with it - it belonged
                            // to that train, not to the square.
                            if (session != null) session.placeLocomotive(station, null);

                            ui.repaintAutoLocList(false);

                            // The label still says the locomotive's name until something rewrites it
                            ui.updateVisiblePoints();
                        });

                        add(menuItem); 
                    }
                    
                    if (!ui.getModel().getAutoLayout().getLocomotivesToRun().isEmpty() && !ui.isAutonomyBusy())
                    {
                        // Edit locomotive
                        menuItem = new JMenuItem(GraphLocAssign.menuLabelFor(current));
                        menuItem.addActionListener(event -> 
                        {
                            GraphLocAssign edit = new GraphLocAssign(ui, current, false);

                            int dialogResult = JOptionPane.showOptionDialog(
                                ui,
                                edit,
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
                                edit.commitChanges();
                                ui.updateVisiblePoints();
                                ui.repaintAutoLocList(false);
                            }
                        }); 

                        add(menuItem);
                    }

                    // Which locomotive belongs here, as opposed to which one happens to be here now
                    addSeparator();

                    // Asked of the copy that answers for the SQUARE, not of the one the train is on.
                    //
                    // A home belongs to a platform and the build writes it onto every copy of one, so
                    // any copy can be asked - but the copy that speaks for a square is the OCCUPIED
                    // one, and a train can be standing on a copy that is not itself a destination.
                    // Editing the square's type while a train is there produces exactly that, and so
                    // does barring an arrival side.  Asked about that copy the item took itself off
                    // the menu, which is to say it vanished precisely when a train was standing on the
                    // platform - the moment somebody is most likely to be setting its home.
                    Point speaksForTheSquare = current;

                    if (session != null)
                    {
                        for (Point copy : session.getStationIndex()
                            .pointsAt(ui.getModel().getAutoLayout(), station))
                        {
                            if (copy.isDestination())
                            {
                                speaksForTheSquare = copy;
                                break;
                            }
                        }
                    }

                    HomeLocomotiveMenu.addStationItem(this, ui, speaksForTheSquare, ui, null,
                        ui::updateVisiblePoints);
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
                        JOptionPane.showMessageDialog(this, e.getMessage());
                    }
                });    

                add(menuItem);
            }
        }
    }

    /**
     * Puts the active locomotive on one named copy of this square, and remembers which way.
     *
     * Both halves matter.  Placing on the right copy is what lets autonomy move it NOW; recording
     * the facing is what puts it back on the same copy the next time the graph is built, which is
     * every load.  Without the second, the placement is correct until the next reload and arbitrary
     * afterwards.
     *
     * @param pointName the copy to place on
     * @param facing which way it is pointing, or null when the square has only one copy
     */
    /**
     * The whole Autonomy menu, as a submenu, for when nothing is running.
     *
     * Everything in it was already reachable from the menu bar, and the menu bar is not where somebody
     * working on their railway is looking - they are looking at the diagram, and they got to it by
     * right-clicking. This is the same menu rather than a copy of it: one place decides what autonomy
     * offers, so the two cannot drift apart.
     *
     * Its own instance, because a Swing component has one parent and the menu bar already owns the
     * other one - adding that one here would take it out of the menu bar. It costs nothing: the menu
     * builds its items when it is opened rather than when it is made.
     *
     * Under a parent entry so it stays out of the way. Most right-clicks on a diagram are about the
     * train or the square under the pointer; the setup is the occasional errand, and an errand does
     * not need to be in the way of the everyday thing.
     *
     * Only while nothing is running, as Adam asked. Half of what is in there rebuilds the layout, and
     * the items that must not run mid-session already refuse - but offering a page of settings to
     * somebody watching trains move invites a click that will be turned down, and a menu full of
     * things that say no is worse than a menu that waited.
     */
    private void addSetupMenu()
    {
        if (ui.isAutonomyBusy()) return;

        AutonomyMenu setup = new AutonomyMenu(ui);

        if (!setup.isEnabled()) return;

        setup.setText(I18n.t("autosetup.ui.menuAutonomySetup"));

        if (getComponentCount() > 0) addSeparator();

        add(setup);
    }

    /**
     * Drops the paths that end where the train already is.
     *
     * A square is several Points, so "somewhere else" and "a different Point" stopped meaning the
     * same thing: a train at BottomMainB was offered BottomMainB, the copy facing the other way.
     * That is not a destination, it is the platform under its own wheels, and it appeared in the
     * list a user picks from.
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

        java.util.List<String> shut = new java.util.ArrayList<>();

        // copies a train could sit on but never be dispatched from
        java.util.List<String> stranded = new java.util.ArrayList<>();

        for (String name : session.facingsFor(station).keySet())
        {
            Point copy = ui.getModel().getAutoLayout().getPoint(name);

            if (copy == null) continue;

            java.util.List<Edge> away = ui.getModel().getAutoLayout().getNeighbors(copy);

            if (away == null || away.isEmpty()) continue;

            // A copy the train can actually LEAVE, in preference to one it merely sits on.
            //
            // "Has an outgoing edge" was the old test and it is not the same question.  A copy of a
            // split square can have somewhere to go and nowhere to be SENT - everything it reaches is
            // a plain point, a reversing point or parking - and this list is drawn at RANDOM, so a
            // train was put on a dead copy about half the time and then never moved.  That is the
            // "nothing moves" fault: on the sample layout, Tunnel (northbound) offers routes and
            // Tunnel (southbound) offers none, and placement could not tell them apart.
            //
            // Barred copies come second for the reason they always did: barring an arrival side makes
            // that copy a non-destination, and placing a train there earns a warning from parseAuto on
            // every load.
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
                shut.add(name);
            }
        }

        // Nothing open is not the same as nowhere to go.  A square whose copies are all shut to
        // arrivals, or all stranded, is still somewhere a train physically stands, and refusing to
        // place one there would be a different message from the "no way out" one this list produces.
        if (!out.isEmpty()) return out;

        return shut.isEmpty() ? stranded : shut;
    }

    /**
     * Places the active locomotive on one of the copies it could leave from, chosen at random.
     *
     * At random rather than always the first: the first is whichever side the build happened to walk
     * in by, which is stable but arbitrary, and placing several trains along one platform would face
     * them all the same way for no reason a reader could see.
     *
     * @param usable the copies a train can be driven away from
     */
    private void placeSomewhereLegal(java.util.List<String> usable)
    {
        if (usable.isEmpty()) return;

        String name = usable.get(new java.util.Random().nextInt(usable.size()));

        placeFacing(ui.getActiveLoc() == null ? null : ui.getActiveLoc().getName(), name,
            session == null ? null : session.facingsFor(station).get(name));
    }

    /**
     * Puts a named locomotive on a named copy of this square, facing a given way.
     *
     * Takes the locomotive rather than reading the active one, because it has two callers that mean
     * different trains.  Placing means the active locomotive - that is what the menu item says.
     * Turning means the one already standing there, and this used to move the active one instead: with
     * some other locomotive selected on the keyboard, "face east" picked THAT train up and put it down
     * on this platform, in the running layout and in the saved configuration both, while the train the
     * user was pointing at did not move.  With nothing selected it threw instead, and the menu item
     * did nothing at all.
     */
    private void placeFacing(String locName, String pointName,
        org.traincontrol.automationui.TilePorts.Side facing)
    {
        if (locName == null) return;

        ui.getModel().getAutoLayout().moveLocomotive(locName, pointName, false);

        if (session != null)
        {
            // The CONFIGURATION as well as the running layout.  Moving a train in the layout leaves
            // where it was; the configuration was never told, so the locomotive kept its old placement
            // too - and the next build emitted it at two Points, which fromJSON answers by
            // invalidating the whole layout.  Every path was then refused as "configuration is
            // invalid", from a placement made minutes earlier.
            session.placeLocomotive(station, locName);
        }

        if (facing != null && session != null)
        {
            session.setFacing(station, facing);

            try
            {
                session.save();
            }
            catch (java.io.IOException e)
            {
                // The placement stands either way; only the memory of it is at risk
                if (ui.getModel().isDebug()) ui.getModel().log(String.valueOf(e.getMessage()));
            }
        }

        ui.repaintAutoLocList(false);

        // And the station labels, which is where the facing is actually shown.  The arrow beside a
        // locomotive name is drawn from the recorded facing, so changing it without rewriting the
        // labels leaves the diagram asserting the old direction - which is the one place a reader
        // would look to check that the change took.
        ui.updateVisiblePoints();
    }
}
   