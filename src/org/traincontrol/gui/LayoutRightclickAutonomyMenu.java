package org.traincontrol.gui;

import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomy.AutonomyReport;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu on the track diagram, to control autonomy
 * @author Adam
 */
final class LayoutRightclickAutonomyMenu extends JPopupMenu
{    
    /** Its tooltips wrapped where a line will not hold them, as it is shown (FR-112) - whatever set them. */
    @Override
    public void show(java.awt.Component invoker, int x, int y)
    {
        AutonomyEditorPanel.wrapTheTooltips(this);

        ui.itemsPressFromWindow(this, javax.swing.SwingUtilities.getWindowAncestor(invoker));

        super.show(invoker, x, y);
    }

    public static final int MAX_PATHS = 12;

    /** Kept so the actions built below can reach them when they fire, after the constructor has returned. */
    private final TrainControlUI ui;

    private final org.traincontrol.automationui.TileGraph.TileKey station;

    /**
     * The square the pointer was over, station or not: a RUNNING layout's answers (where this train may go) are about a
     * station, the setup's (become a station, which way trains run, how long) about the square.
     */
    private final org.traincontrol.automationui.TileGraph.TileKey here;

    private final org.traincontrol.automationui.AutonomySession session;
    
    /**
     * Builds this menu for a square and shows it, if it has anything to say.  The one way in (the constructor is
     * private), so the show-only-if-non-empty check cannot be missing at one call site: an empty menu is a gray box.
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
        // A square's menu only with a setup loaded (OB-307); the diagram's own menu (no square) still opens, offering
        // the setup.
        if ((station != null || here != null) && (ui == null || !ui.isAutonomyLoaded())) return;

        if (ui != null) ui.stationWindowsFollow(here != null ? here : station);

        // The railway is asked first, off the event thread (OB-192): `getPossiblePaths` is `synchronized` on the
        // `Layout`, and a dispatch holds that monitor across a `CONFIGURE_SLEEP` per accessory.  The square is resolved
        // here, on the event thread, as `getAutonomyPointForTile` goes through the LAZY `getAutonomySession()`; only
        // the Point, a value, crosses to the worker.
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

                    // Still on screen: `JPopupMenu.show` throws on a component taken off screen.
                    if (at.isShowing()) menu.show(at, x, y);
                }
            });
        }, "LayoutRightclickAutonomyMenu options");

        gathering.setDaemon(true);

        gathering.start();
    }

    /** What the paths section needs from the railway, gathered off the event thread: answers, not questions. */
    static final class PathOptions
    {
        /** The train these paths belong to, so the menu can tell the answer is about the train it is now drawing. */
        final Locomotive locomotive;

        /** Offerable, and a square autonomy would choose: the top-level list. */
        final List<List<Edge>> shown;

        /** Offerable, but never chosen by autonomy: More Destinations. */
        final List<List<Edge>> other;

        // No count of everything the search returned (OB-205): comparing against it made the "..." appear whenever
        // anything was filtered.  See `theListWasCutShort`.

        /**
         * The railway these paths were found on, which the destination sends on: a replaced railway refuses at its
         * fence.
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
     * Whether the menu offers the "..." at the foot of the list: only when there were more destinations than fitted
     * (FR-058), counting only those this menu could have shown (OB-205).
     */
    static boolean theListWasCutShort(int offered, int shown, int inMoreDestinations)
    {
        return offered > shown + inMoreDestinations;
    }

    /** Whether a setup edit from this menu is refused because trains are running, having said so. */
    private boolean refusedWhileRunning()
    {
        return ui.refuseWhileARunHoldsTheSetup(ui);
    }

    /**
     * Asks the railway everything the paths section needs, on the caller's thread.  MUST NOT BE CALLED ON THE EVENT
     * THREAD (`showFor` arranges that): every call in here is `synchronized` on the `Layout`.  Answers null wherever
     * there is nothing to list.
     */
    private static PathOptions gatherPathOptions(TrainControlUI ui, Point current)
    {
        // The constructor's first gates, so a gather never takes the railway's monitor for
        // a menu that will not draw it.
        if (ui == null || current == null || ui.isLayoutEditorOpen()) return null;

        // ASKED ONCE, NOT BUILT: this runs on a worker, and Unload can clear the model between asking whether
        // there is a railway and asking for it
        final org.traincontrol.automation.Layout running = ui.getModel() == null ? null
            : ui.getModel().getAutoLayoutIfLoaded();

        if (running == null) return null;

        Locomotive locomotive = current.getCurrentLocomotive();

        // Per train, as its Auto tab card: another train sent by hand does not hide this one's (FR-187)
        if (locomotive == null || !RunGates.mayBeSentByHandNow(ui, running, locomotive)) return null;

        List<List<Edge>> paths = withoutGoingNowhere(ui, running.getPossiblePaths(locomotive, true));

        // By the name shown, as read (OB-450: ParkingTrack4 before ParkingTrack10), the rule Why not Moving? sorts by
        final org.traincontrol.automationui.AutonomySession setup = ui.getAutonomySession();

        paths.sort(java.util.Comparator.comparing((List<Edge> path) -> stationName(setup, path.get(path.size() - 1).getEnd()),
            org.traincontrol.util.Util::compareAsRead).thenComparing(Edge::pathToString));

        // Switched-off squares are only in the autonomy tab; filtered rather than grayed.
        List<List<Edge>> shownPaths = new java.util.ArrayList<>();

        // Squares autonomy would never choose go in their own submenu, More Destinations (FR-058), asked through
        // `isChoosableByAutonomy`.
        List<List<Edge>> otherPaths = new java.util.ArrayList<>();

        for (List<Edge> path : paths)
        {
            Point end = path.get(path.size() - 1).getEnd();

            // Off this menu altogether (switched off, or excluded for this train), not counted towards the ellipsis.
            if (!running.isOfferableToOperator(end, locomotive)) continue;

            // Asked of the square, not of this train (which `isOfferableToOperator` has covered).
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
     * Puts the clicked square's name on top of every square's menu, grayed and bold like the editor's (OB-112), with
     * Station Settings... right under it where offered (MT-957); plain track is headed with its kind (OB-451).  Inserted
     * rather than added, because the menu is already built when this runs.
     */
    private void headline()
    {
        org.traincontrol.automationui.TileGraph.TileKey subject = station != null ? station : here;

        if (subject == null || session == null) return;

        JMenuItem heading = new JMenuItem(headingText != null ? headingText : ui.squareHeading(subject));

        heading.setEnabled(false);
        heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD));

        boolean settingsFirst = getComponent(0) instanceof JMenuItem
            && I18n.t("autosetup.ui.menuStationSettings").equals(((JMenuItem) getComponent(0)).getText());

        insert(heading, 0);

        if (!settingsFirst) insert(new JPopupMenu.Separator(), 1);
    }

    /** The station's name, where the menu is about a station rather than the square. */
    private String headingText;

    /**
     * `station` is the sensor the caption is about, or null for the diagram-wide menu: a square rather than a name,
     * since a station is several Points and none is called what the caption says.  `here` is the square that was
     * clicked, or null where the click was not on one.  `options` is what `showFor` gathered off the event thread, or
     * null where there is nothing to list.  NOTHING IN THIS CONSTRUCTOR MAY ASK THE RAILWAY ITSELF (see `showFor`).
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
        // from this menu would be overwritten.  Says so, and offers the way there.
        if (ui.isLayoutEditorOpen())
        {
            menuItem = new JMenuItem(I18n.t("autosetup.ui.menuEditorIsOpen"));
            menuItem.setEnabled(false);
            add(menuItem);

            add(AutonomyMenu.goToOpenEditorItem(ui));

            return;
        }

        final boolean ready = ui.getModel().hasAutoLayout();

        // Start versus Graceful Stop asks isAutonomyBusy(), as every item in this branch does: isAutoRunning() clears the
        // instant Graceful Stop is pressed while trains coast, so Stop stays offered and Start does not appear.
        final boolean busy = ready && ui.isAutonomyBusy();

        final boolean held = ui.aRunHoldsTheSetup();

        // Found from the SQUARE, not the station, which is null on a pass-through Point (MT-069).
        final Point current = ready ? ui.getAutonomyPointForTile(station != null ? station : here) : null;

        // A destination, or anywhere a train stands: a copy barred as a non-destination can hold one placed by hand.
        final boolean stationHere = current != null && (current.isDestination() || current.getCurrentLocomotive() != null);

        final Locomotive locomotive = stationHere ? current.getCurrentLocomotive() : null;

        // The STATION's name, not the copy's (FR-182).
        if (stationHere) headingText = stationName(current);

        // The clicked square's items first, autonomy as a whole last; headline() puts the name on top.
        JMenuItem settings = ui.stationSettingsItem(here);

        if (settings != null) add(settings);

        addSeparator();

        // Is Active tops the train's own items (FR-182)
        if (locomotive != null) addActive(locomotive);

        if (here != null && !held) ui.addAutonomyFacingItems(this, here);

        addSeparator();

        if (stationHere) addDestinations(locomotive, options);

        addSeparator();

        addHandDoors(current, locomotive, stationHere, busy, held);

        addSeparator();

        addSetupMenu();

        addSeparator();

        if (ready && !busy)
        {
            menuItem = new JMenuItem(I18n.t("autolayout.ui.menuStartAutonomy"));

            // Grayed when it could not start (OB-050): canStartAutonomy asks the predicate the guard asks.  Asked once:
            // AutonomySession.check() is not cached and walks every point on the event thread.
            boolean canStart = ui.canStartAutonomy();

            // Both sentences from one reading: Start's tooltip and the Return Home item's.
            String[] why = canStart ? null : ui.whyStartAndAHandSendAreRefused();

            menuItem.setEnabled(canStart);

            // Which of the four reasons it is, through the one rule (MT-263).
            if (!canStart) menuItem.setToolTipText(AutonomyEditorPanel.wrapped(why[0]));

            menuItem.addActionListener(event ->
            {
                try
                {
                    ui.requestStartAutonomy();
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(ui.dialogParent(), e.getMessage());
                }
            });

            add(menuItem);

            // The hand doors' sentence, from the same reading
            String broken = why == null ? null : why[1];

            HomeLocomotiveMenu.addReturnHomeItem(this, ui, broken);
        }
        else if (ready)
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
                    JOptionPane.showMessageDialog(ui.dialogParent(), e.getMessage());
                }
            });

            add(menuItem);
        }

        tidy(this);
    }

    /** No divider first, last or twice in a row: each group above adds its own, empty or not. */
    private static void tidy(JPopupMenu menu)
    {
        for (int at = menu.getComponentCount() - 1; at >= 0; at--)
        {
            boolean divider = menu.getComponent(at) instanceof javax.swing.JSeparator;

            if (divider && (at == 0 || at == menu.getComponentCount() - 1
                || menu.getComponent(at - 1) instanceof javax.swing.JSeparator)) menu.remove(at);
        }
    }

    /**
     * Change Train at... first, Place <active> Here, Remove, Place Another Locomotive Here... last, then Edit
     * <name>..., never grouped with the autonomy items (FR-182).
     */
    private void addHandDoors(final Point current, Locomotive locomotive, boolean stationHere, boolean busy, boolean held)
    {
        // Station Settings' Train tab; while autonomy runs, only over a train (FR-111).
        JMenuItem trainWindow = here == null || locomotive == null && (stationHere ? busy : held) ? null
            : ui.trainWindowItem(here);

        boolean placesOne = trainWindow != null && I18n.t("autosetup.ui.menuPlaceHere").equals(trainWindow.getText());

        if (trainWindow != null && !placesOne) add(trainWindow);

        // Place a different locomotive here, FACING a chosen way: a square is several Points, one per arrival side, so
        // the copy must be picked.  Offered only on a destination (OB-284), the active one directly (FR-048).
        boolean placesTheActive = stationHere && ui.getActiveLoc() != null && current.isDestination() && !held
            && !ui.getActiveLoc().equals(locomotive);

        if (placesTheActive)
        {
            // The copy facing the way the train already faces if it has a way out, else the first offered; where none
            // can leave, offered all the same and `placeSomewhereLegal` records no facing.
            final java.util.List<String> offered = new java.util.ArrayList<>(placeableCopies());

            // Every copy the train could leave by, including one `placeableCopies` never offers.
            if (session != null)
            {
                for (String copy : session.departableFacingsFor(station, ui.getModel().getAutoLayout()).keySet())
                {
                    if (!offered.contains(copy)) offered.add(copy);
                }
            }

            JMenuItem place = new JMenuItem(I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName()));

            place.addActionListener(event -> placeSomewhereLegal(offered));

            add(place);
        }

        // Left out while a route runs.  Turning it is Change Direction (MT-086).
        if (stationHere && current.getCurrentLocomotive() != null && !held)
        {
            JMenuItem remove = new JMenuItem(I18n.f("layout.ui.menuRemoveLocomotive", current.getCurrentLocomotive().getName()));

            // A method, so this constructor asks the railway nothing (`moveLocomotive` takes the `Layout` monitor).
            remove.addActionListener(event -> removeLocomotiveHere(current));

            add(remove);
        }

        if (placesOne)
        {
            // Beside Place <active> Here, it says it is for any other.
            if (placesTheActive) trainWindow.setText(I18n.t("autosetup.ui.menuPlaceAnotherHere"));

            add(trainWindow);
        }

        if (locomotive != null) add(editLocomotiveItem(locomotive));
    }

    /**
     * The standing train's destinations under "Send <name> To:" (MT-922), from the answer gathered for it (equality says it is about the
     * train standing here now); true when it added any.
     */
    private boolean addDestinations(Locomotive locomotive, PathOptions options)
    {
        if (locomotive == null || options == null || !locomotive.equals(options.locomotive)
            || options.shown.isEmpty() && options.other.isEmpty()) return false;

        JMenuItem whose = new JMenuItem(I18n.f("autolayout.ui.menuSendTo", locomotive.getName()));
        whose.setEnabled(false);
        add(whose);

        // Counted rather than measured off the menu, which also holds other items.
        int shown = 0;

        for (List<Edge> path : options.shown)
        {
            add(destinationItem(ui, options.railway, path, locomotive));

            // The way through whenever there were more destinations than fitted (FR-058), decided here alone
            if (++shown >= Math.min(MAX_PATHS, options.shown.size())
                && theListWasCutShort(options.shown.size() + options.other.size(), shown, options.other.size()))
            {
                JMenuItem rest = new JMenuItem("...");
                rest.addActionListener(event -> ui.jumpToAutonomyLocTab());
                add(rest);
                break;
            }
        }

        // More Destinations (FR-058): everything valid but not automatic, such as a reversing point or a square marked
        // as no automatic destination.  Uncapped: Swing gives an over-tall JMenu no scroller.
        if (!options.other.isEmpty())
        {
            javax.swing.JMenu more = new javax.swing.JMenu(I18n.t("autolayout.ui.menuMoreDestinations"));

            for (List<Edge> path : options.other) more.add(destinationItem(ui, options.railway, path, locomotive));

            add(more);
        }

        return true;
    }

    /** The Is Active tick (FR-117), as the train's Auto tab card has it. */
    private void addActive(final Locomotive train)
    {
        final javax.swing.JCheckBoxMenuItem active = new javax.swing.JCheckBoxMenuItem(
            I18n.f("autolayout.ui.menuLocomotiveActive", train.getName()), !train.isAutonomyPaused());

        active.setToolTipText(I18n.t("autolayout.ui.hintLocomotiveActive"));

        active.addActionListener(event -> setActive(train, active.isSelected()));

        add(active);
    }

    /**
     * The standing train's own settings (FR-048), on every menu over a train (Adam: "there should always be an edit
     * loc"); during a run the window opens locked.
     */
    private JMenuItem editLocomotiveItem(final Locomotive standing)
    {
        JMenuItem edit = new JMenuItem(I18n.f("autolayout.ui.menuEditLocomotive", standing.getName()));

        edit.addActionListener(event -> ui.openLocomotiveSettings(standing, LocomotiveSettingsFrame.Tab.AUTONOMY, null));

        return edit;
    }

    /**
     * Autonomy Setup >, at the foot of every square's menu: what the editor's menu offers on this square, borrowed
     * from the panel that builds it so the two never drift, then Bulk Tools and the full editor.  Not while a run holds
     * the setup.
     */
    private void addSetupMenu()
    {
        if (here == null || ui.aRunHoldsTheSetup()) return;

        JPopupMenu built = ui.buildAutonomyTileMenu(here);

        if (built == null || built.getComponentCount() == 0) return;

        javax.swing.JMenu setup = new javax.swing.JMenu(I18n.t("autosetup.ui.menuAutonomySetup"));

        // getComponents hands back a copy, so moving each out from under the popup does not walk the array being read.
        for (java.awt.Component part : built.getComponents())
        {
            // The editor's heading and Station Settings are on this menu already (OB-451)
            if (setup.getMenuComponentCount() == 0 && (part instanceof javax.swing.JSeparator || !part.isEnabled())
                || part instanceof JMenuItem
                && I18n.t("autosetup.ui.menuStationSettings").equals(((JMenuItem) part).getText())) continue;

            setup.add(part);
        }

        setup.addSeparator();
        setup.add(openFullEditorItem());

        tidy(setup.getPopupMenu());

        add(setup);
    }

    /**
     * FR-026: the way out to the full editor, live exactly when pressing it would work and giving the window's own
     * refusal reason when it is not.
     */
    private JMenuItem openFullEditorItem()
    {
        String refusal = ui.whyAutonomyEditorCannotOpen();

        JMenuItem openEditor = new JMenuItem(I18n.t("autosetup.ui.menuOpenFullEditor"));

        if (refusal == null)
        {
            // On the square that was clicked, so the editor comes up on the right page with that tile found and
            // flashing.
            openEditor.addActionListener(event -> ui.openAutonomyEditor(here));
        }
        else
        {
            openEditor.setEnabled(false);
            openEditor.setToolTipText(I18n.t(refusal));
        }

        return openEditor;
    }

    /**
     * Drops the paths that end where the train already is.  A square is several Points, so a train at BottomMainB must
     * not be offered BottomMainB, the copy facing the other way.  Filtered here rather than in the layout, which cannot
     * tell: only the setup knows that two Points built from one square are one place.
     */
    private static List<List<Edge>> withoutGoingNowhere(TrainControlUI ui, List<List<Edge>> paths)
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        return session == null ? paths : session.getStationIndex().distinctDestinations(paths);
    }

    /**
     * What to call a Point when a person is reading it: the station name, not the built graph's per-copy name.  Falls
     * back to the Point's own name when the setup cannot be asked.
     */
    private String stationName(Point point)
    {
        return stationName(session, point);
    }

    private static String stationName(org.traincontrol.automationui.AutonomySession session, Point point)
    {
        return session == null ? (point == null ? "" : point.getName())
            : session.getStationIndex().describe(point);
    }

    /**
     * The copies of this square a train could actually be driven away from: a copy with no outgoing edge is one the
     * split made arrivable and not leavable, and a train placed there cannot move.
     */
    private java.util.List<String> placeableCopies()
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        if (session == null || !ui.getModel().hasAutoLayout()) return out;

        // `shut` (the copies barred to arrivals) is counted and not offered, kept as its own bucket for the reasoning
        // at the foot of this method.
        java.util.List<String> shut = new java.util.ArrayList<>();

        // copies a train could sit on but never be dispatched from
        java.util.List<String> stranded = new java.util.ArrayList<>();

        for (String name : session.facingsFor(station).keySet())
        {
            Point copy = ui.getModel().getAutoLayout().getPoint(name);

            if (copy == null) continue;

            java.util.List<Edge> away = ui.getModel().getAutoLayout().getNeighbors(copy);

            if (away == null || away.isEmpty()) continue;

            // A copy the train can actually LEAVE, in preference to one it merely sits on: a copy can have somewhere to
            // go and nowhere to be SENT (everything it reaches is a plain point, a reversing point or parking).  Barred
            // copies are not on this list; the right-click Place still stands a train on one where its heading is that
            // way (OB-284).
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

        // Nothing open is not nowhere to go: a square whose copies are all stranded is still somewhere a train stands.
        if (!out.isEmpty()) return out;

        // Not a barred copy, first: `shut` is the copies whose `isDestination()` is explicitly false.  The right-click
        // Place offers every copy a train could leave by (`departableFacingsFor`); this list is only the order a copy
        // is taken in where the heading holds none.  Stranded copies stay, barred or not.
        return stranded;
    }

    /**
     * Places the active locomotive on the copy it could leave from that faces the way it already faces (OB-296); where
     * no copy can leave, on the copy the build stands an unfaced train on, with no facing recorded (OB-350).
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
     * The copy a right-click Place puts the train on, as the paste chooses it (OB-296), not by the menu's own list: the
     * copy facing the heading at a square with a barred side is no station.
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
     * Whether the operator marked this square as one trains may turn around at.  Asked of the SETUP, because
     * `canReverse` never reaches the running layout (the builder splits the square); the same answer as
     * `TrainControlUI.mayTurnHere`.
     */
    private boolean mayTurnHere()
    {
        return session != null && station != null && session.mayTurnTiles().contains(station);
    }

    /**
     * Puts a named locomotive on a named copy of this square, facing a given way.  Takes the locomotive rather than
     * reading the active one: placing means the active locomotive but turning means the one already standing there, and
     * reading the active one for a turn would pick THAT train up.
     */
    private void placeFacing(String locName, String pointName,
        org.traincontrol.automationui.TilePorts.Side facing)
    {
        if (locName == null) return;

        final org.traincontrol.automation.Layout running = ui.getModel() == null
            ? null : ui.getModel().getAutoLayout();

        final org.traincontrol.automation.Point landing =
            running == null ? null : running.getPoint(pointName);

        // A placement or a turn?  Only placing is an arrival: a turn does not change which way the train CAME in.
        final boolean arriving = landing != null && (landing.getCurrentLocomotive() == null
            || !locName.equals(landing.getCurrentLocomotive().getName()));

        String tail = null;

        if (arriving)
        {
            // And where its tail is: the same placement by drag asks and blocks the track behind the train, so this
            // door must too (`behaviour/facing.md` section 4), through the paste's door and rule.  Before the move, so
            // a dismissal can leave the railway as it was.  The unbarred sides here and in the `wouldAsk` below must be
            // the same list (OB-204).
            tail = org.traincontrol.gui.autonomy.ArrivalSidePrompt.forPlacement(running, landing,
                facing == null ? null : facing.name(), mayTurnHere(), ui.dialogParent(),
                session == null ? null : session.unbarredArrivalSides(station));

            // A dismissal, and not the other two nulls: `forPlacement` also answers null when the square offers nothing
            // to choose between.
            if (tail == null && org.traincontrol.gui.autonomy.ArrivalSidePrompt.wouldAsk(running, landing,
                mayTurnHere(), session == null ? null : session.unbarredArrivalSides(station)))
            {
                return;
            }
        }

        // The railway's answer decides whether any of this happens: `moveLocomotive` returns false and logs rather than
        // throwing, and the setup (the half that survives a restart) is written only for a move the railway accepted.
        // A menu is grayed when it opens, so autonomy started elsewhere since leaves a live item over a running railway
        // (`behaviour/editing-while-running.md` section 6a): the refusal is in the method.
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

        // EVEN ONTO A COPY TRAINS MAY NOT ARRIVE AT, as the paste and the dialog move (OB-284): the railway's
        // three-argument form refuses it.
        if (!ui.getModel().getAutoLayout().moveLocomotive(locName, pointName, false, true)) return;

        // Whether the setup this door writes to is still the window's once its tail question is answered.
        boolean setupStands = true;

        // What the save before the tail question reconciled, shown with this door's own save (OB-305).
        final org.traincontrol.automationui.AutonomyCompanionStore.Reconciliation[] savedEarly = {null};

        if (session != null)
        {
            // The configuration as well as the running layout, or the next build would emit the locomotive at two
            // Points and fromJSON would invalidate the layout.
            session.placeLocomotive(station, locName);

            // The facing, before the tail question, as the locomotive dialog writes it; none where no copy can say
            // (OB-350).
            session.setFacing(station, facing);

            // AND THE TAIL, after `placeLocomotive`, because a change of occupant clears it.  Only for an arrival.
            if (arriving)
            {
                setupStands = org.traincontrol.gui.TailCrossedPrompt.recordTheTail(session, station, running, landing,
                    tail, locName, roadBefore, station != null && station.equals(squareBefore), sideBefore, ui,
                    ui, saved -> savedEarly[0] = saved);
            }
        }

        // WHAT THE SAVE BEFORE THE TAIL QUESTION RECONCILED (OB-305), shown once here: the save below then finds
        // nothing more (MT-380).
        AutonomyReport.show(ui.dialogParent(), savedEarly[0]);

        // Not a setup the window has let go in the wait; the placement above is written either way (OB-373).
        if (session != null && setupStands)
        {
            try
            {
                // The reconciliation the save returns is shown, not dropped: it reports what the save had to let go.
                AutonomyReport.show(ui.dialogParent(), session.save());
            }
            catch (java.io.IOException e)
            {
                // The placement stands either way; only the memory of it is at risk, so it is logged, as the other doors do
                ui.getModel().log(e);
            }
        }

        ui.repaintAutoLocList(false);

        // And the station labels: the arrow beside a locomotive name is drawn from the recorded facing.
        ui.updateVisiblePoints();
    }


    /**
     * One "-> somewhere" item, dispatching this locomotive along this path; shared by the top-level list and `More
     * Destinations` (FR-058), so the checks and the dispatch cannot fall out of step.
     */
    private JMenuItem destinationItem(TrainControlUI ui, org.traincontrol.automation.Layout railway, List<Edge> path,
        Locomotive locomotive)
    {
        JMenuItem item = new JMenuItem("-> " + stationName(path.get(path.size() - 1).getEnd()));

        // Through the one door both hand doors send through (whether a train may be sent at all, and the dispatch); its
        // messages belong to the main window.
        item.addActionListener(event -> ui.sendATrainByHand(railway, path, locomotive));

        return item;
    }

    /**
     * The Is Active tick (FR-117), decided at the click: while a run holds the setup it pauses the train alone, as the
     * card's button does (FR-187); at rest the setup records it.
     */
    private void setActive(Locomotive train, boolean active)
    {
        if (ui.aRunHoldsTheSetup()) ui.pauseTrainWhileRunning(train, !active);
        else ui.setLocomotiveActive(session, train, active);
    }

    /**
     * Takes the standing train off this square, and off the setup with it.  On the event thread, deliberately: it is a
     * click, offered only when `isAutonomyBusy()` is false, and a worker would let the repaint below run before the
     * change.
     */
    private void removeLocomotiveHere(Point current)
    {
        // ASKED AGAIN AT THE CLICK, as Edit Locomotive is: a route may have begun since the menu opened
        if (refusedWhileRunning()) return;

        // Purge, as the editor's own Remove does, or the locomotive stays in the list of trains to run while standing
        // nowhere.  The railway's answer, then the save: a refusal must not be discarded, or the setup would say the
        // square is empty while the railway still has the train.
        if (!ui.getModel().getAutoLayout().moveLocomotive(null, current.getName(), true)) return;

        // The setup as well, or the next build puts the train back from the configuration.  The facing goes with it: it
        // belonged to that train, not to the square.
        if (session != null)
        {
            session.placeLocomotive(station, null);

            // And written: the two repaints below do not reach the autonomy panel's refresh callback, so without this
            // save a removed train is back at the next launch.
            try
            {
                AutonomyReport.show(ui.dialogParent(), session.save());
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
