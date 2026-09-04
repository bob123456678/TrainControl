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

    /**
     * The square the pointer was actually over, station or not.
     *
     * Kept apart from the station above because they answer different questions.  Everything about a
     * RUNNING layout - where this train may go, which locomotive is here - is about a station, and
     * there is nothing to say over plain track.  The setup is about the square: whether it should
     * become a station, which way trains may run through it, how long it is.  Sharing one field meant
     * the setup menu could only be reached from squares that were already set up, which is exactly
     * backwards.
     */
    private final org.traincontrol.automationui.TileGraph.TileKey here;

    private final org.traincontrol.automationui.AutonomySession session;
    
    /**
     * Builds this menu for a square and shows it, if it has anything to say.
     *
     * The one way in (DD-B5). The four surfaces that offer this menu - a tile, a station caption, the
     * main window's panel and the popup window's panel - each used to write out the same three steps:
     * hop to the event thread, build the menu, show it. Three of them also checked whether the menu had
     * come out empty. The fourth, added later, did not, and over a plain piece of track with autonomy
     * running and nothing to offer it opened a one-item-high grey box under the pointer - which, as
     * LayoutLabel's comment puts it, "reads as a fault".
     *
     * A guard that has to be remembered at four call sites is a guard that will be missing at one of
     * them. There is nothing to remember now: the constructor is private, so this is the only way the
     * menu can be shown at all.
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
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            LayoutRightclickAutonomyMenu menu = new LayoutRightclickAutonomyMenu(ui, station, here);

            // An empty menu is not worth showing.
            if (menu.getComponentCount() > 0)
            {
                // AFTER the count, and deliberately.  A heading is not an item, and putting one on
                // first would turn "nothing to offer here" into a grey box with a station name in it -
                // which is the fault the check above exists to prevent, arriving by a new road.
                menu.headline();

                menu.show(at, x, y);
            }
        });
    }

    /**
     * Puts the clicked square's name at the top, greyed.
     *
     * OB-112. Adam, right-clicking a station on the diagram with a setup loaded: "nothing at the top
     * there." The autonomy editor's own menu has opened with a bold, disabled name since it was built,
     * and this menu - the one on the diagram, which is where people actually right-click - never had
     * one. With a setup loaded the whole menu is about a square: this train, these paths, that home.
     * Which square was never said, and the menu covers it while it is open.
     *
     * The name comes from the session rather than from three lines written out again here, so the two
     * menus cannot end up calling one square by different names while both are on screen.
     *
     * Inserted rather than added, because by the time this runs the menu is built - and it runs at all
     * only once showFor knows there is something to head.
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
     * @param station the sensor the caption is about, or null for the diagram-wide menu.  A square
     *        rather than a name: a station is several Points now and none of them is called what the
     *        caption says, so resolving one by its text found nothing and the menu silently lost every
     *        item below the lookup.
     * @param here the square that was clicked, or null where the click was not on one
     */
    private LayoutRightclickAutonomyMenu(TrainControlUI ui,
        org.traincontrol.automationui.TileGraph.TileKey station,
        org.traincontrol.automationui.TileGraph.TileKey here)
    {
        this.ui = ui;
        this.station = station;
        this.here = here;
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
            // UXR-B7: this used to ask isAutoRunning(), which stopLocomotives() clears the instant
            // Graceful Stop is pressed - while the trains it was stopping keep moving until they reach
            // their next station. Every item INSIDE this branch already asks isAutonomyBusy() for its
            // own enabled state (place, remove, edit loc, the setup submenu) - only the choice between
            // this branch and the Graceful Stop item below asked the other question. isAutonomyBusy()
            // is what Layout.isRunning() feeds and is true for the whole coast-down window, so the
            // Stop item now stays offered, and Start does not appear, until the trains actually stop.
            if (!ui.isAutonomyBusy())
            {
                menuItem = new JMenuItem(I18n.t("autolayout.ui.menuStartAutonomy"));

                // Greyed when it could not start (OB-050), and the sentence that used to stand here
                // was wrong (UI review, B1).
                //
                // It said "the Start button has always known; it just was not asked". It had not:
                // canStartAutonomy was the Start button's enabled state, and no writer of that flag
                // consults the checks - the button is deliberately left enabled and explains at press
                // time. So a setup in the "fix it" state offered this item live, and pressing it got
                // a dialog. Two controls on one window, feet apart, disagreeing about one action.
                //
                // canStartAutonomy asks refuseAutonomyStartWhileBroken's own number now, which is the
                // rule this repository has paid for six times in two days: the control that OFFERS an
                // action asks the predicate the guard asks.
                // Asked ONCE each, and this is not tidiness (LD-C6).
                //
                // Both of these reach AutonomySession.check(), which is not cached: it rebuilds the
                // termini and turn-around sets over every point in the graph. Written out four times
                // - canStartAutonomy twice, autonomyErrorCount twice - that is four full walks of the
                // railway on the event thread, every time somebody right-clicks a station, to decide
                // one enabled flag and one tooltip.
                //
                // The predicate is unchanged and still the guard's own, which is what the comment
                // above is about. What changes is how many times it is asked.
                boolean canStart = ui.canStartAutonomy();

                menuItem.setEnabled(canStart);

                if (!canStart)
                {
                    // And say which of the THREE reasons it is (V32-C1).
                    //
                    // The tooltip was hardcoded to the waiting-for-trains message, which is a lie
                    // whenever Start is off for any other reason - including the one immediately
                    // above.  Naming the count fixed two of the three and left the third telling the
                    // same lie: `canStartAutonomy` refuses on `hasErrors()`, which also covers a graph
                    // that will not build at all with nothing having turned that into a finding, and
                    // in that case the count is zero.  So the operator was told to wait for trains
                    // that are not running and never will be.
                    //
                    // The guard has this third arm and got it in the same commit that widened it; the
                    // affordance did not.  `errorCannotBuildDetailOne` is the load door's own wording
                    // for exactly this state.
                    int errors = ui.autonomyErrorCount();

                    menuItem.setToolTipText(AutonomyEditorPanel.wrapped(
                        errors > 0
                            ? I18n.f("autolayout.ui.errorCannotStartWithErrors", errors)
                            : ui.autonomyHasErrors()
                                ? I18n.t("autosetup.ui.errorCannotBuildDetailOne")
                                : I18n.t("autolayout.errorUnableToStartAutonomyWaitForTrains")));
                }

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

                // The Point standing on that square, preferring one with a train on it.
                //
                // From the SQUARE rather than from the station, since MT-069.
                //
                // `station` is autonomyStationAt, which returns null unless the square has been
                // DESIGNATED a station - so on a pass-through Point with a locomotive on it, this was
                // null and the whole block below vanished: no Remove, no paths, no facing. Adam:
                // "Present in the autonomy editor but not in the track diagram." The editor works from
                // the square, which is why it had them.
                //
                // The comment fifteen lines down already describes this trap one level in - "a
                // locomotive on a copy that is not [a destination] had no menu at all ... the same
                // trap the autonomy editor had, where the remove item hung off the designation rather
                // than off the locomotive". That fix was applied to the inner test and not to where
                // the square comes from, so the designation was still deciding, one step earlier.
                Point current = ui.getAutonomyPointForTile(station != null ? station : here);

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
                    //
                    // A PER-LOCOMOTIVE gate, where AutoLocomotiveStatus asks isAutoRunning() about the
                    // whole layout - and the two therefore disagree.  Adam, 2026-08-31: "right-clicking
                    // on the track diagram does not show available options in non-atomic mode", where
                    // the commands panel does.
                    //
                    // CLOSED AS A KNOWN LIMITATION on his ruling (OB-164): "The user can rely on full
                    // autonomy or the panels to send trains more clearly."  Both of those work in
                    // non-atomic mode, so what is lost is one of three ways to the same thing.  Neither
                    // getPossiblePaths nor isPathClear branches on atomicRoutes anywhere, so whatever
                    // this is, it is state a non-atomic run leaves behind rather than the question being
                    // asked - and finding it needs a two-train reproduction nobody has built.
                    //
                    // Left here so the next reader who notices the two surfaces disagree finds the
                    // ruling rather than re-opening it.
                    if (locomotive != null && !ui.getModel().getAutoLayout().getActiveLocomotives().containsKey(locomotive))
                    {
                        List<List<Edge>> paths = withoutGoingNowhere(ui,
                            ui.getModel().getAutoLayout().getPossiblePaths(locomotive, true));

                        paths.sort((List<Edge> p1, List<Edge> p2) -> Edge.pathToString(p1).compareTo(Edge.pathToString(p2)));

                        // EVERYTHING THAT IS POSSIBLE, counted before anything is left out.
                        //
                        // What "more options than are shown" means has to include the ones this menu
                        // decides not to show, not only the ones the cap cuts off - otherwise the
                        // ellipsis is a statement about the cap rather than about the list.
                        final int possible = paths.size();

                        // SWITCHED-OFF SQUARES ARE NOT ON THIS MENU (Adam, 2026-09-01).
                        //
                        // "make the inactive stations disappear from the track diagram menu - they
                        // should only be visible in the autonomy tab."  This menu is the quick way to
                        // send a train somewhere ordinary; a square that has been deliberately taken
                        // out of use is not that, and listing it here puts the least likely
                        // destinations among the most likely ones.
                        //
                        // Filtered rather than greyed: a greyed item still costs a line and still has
                        // to be read past.  They remain reachable - the autonomy tab lists everything,
                        // which is what the ellipsis below is for.
                        List<List<Edge>> shownPaths = new java.util.ArrayList<>();

                        // AND THE ONES AUTONOMY WOULD NEVER CHOOSE GO IN THEIR OWN SUBMENU (FR-058).
                        //
                        // Adam: "show only active stations that can be chosen in full autonomy.  add a
                        // menu called More Destinations and in there, list the points that cannot be
                        // chosen in full autonomy but are still valid.  the current setup lists both in
                        // one flat list, which truncates active stations, which I don't like".
                        //
                        // The cap is the reason this matters rather than tidiness: a parking track that
                        // autonomy will never pick used to cost one of the twelve lines an ordinary
                        // platform wanted, and the ellipsis appeared with real destinations behind it.
                        //
                        // Asked through `isChoosableByAutonomy`, which is the same predicate the
                        // "no available paths" window and the diagram's caption rule ask - its own
                        // javadoc is about not having two answers to this question, so this does not
                        // become a third.
                        List<List<Edge>> otherPaths = new java.util.ArrayList<>();

                        for (List<Edge> path : paths)
                        {
                            Point end = path.get(path.size() - 1).getEnd();

                            // OFF THIS MENU ALTOGETHER: switched off, or excluded for this train.
                            //
                            // The rule is named on the layout rather than written out here, because
                            // this class is package-private and a rule nothing can reach is a rule
                            // nothing can test - which is how the terminus went in and out of it
                            // twice.  Its javadoc carries both rulings.
                            //
                            // These still count towards `possible` below, so the ellipsis offers the
                            // autonomy tab, which lists everything.
                            if (!ui.getModel().getAutoLayout().isOfferableToOperator(end, locomotive))
                            {
                                continue;
                            }

                            // WHICH OF THE TWO LISTS, asked about the square and deliberately not
                            // about this train (Adam, 2026-09-04).
                            //
                            // `isOfferableToOperator` above has already asked everything that is
                            // about the train.  What is left is a property of the square - a
                            // reversing point, a square marked as not an automatic destination - and
                            // the square form is the same predicate the "no available paths" window
                            // and the diagram's captions ask, which is what that javadoc is for.
                            if (ui.getModel().getAutoLayout().isChoosableByAutonomy(end))
                            {
                                shownPaths.add(path);
                            }
                            else
                            {
                                otherPaths.add(path);
                            }
                        }

                        paths = shownPaths;

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
                            add(destinationItem(ui, path, locomotive));

                            // The way through, whenever anything at all has been left out.
                            //
                            // `possible` counts what could be offered before the switched-off squares
                            // were dropped and before the cap, so this fires for either reason and for
                            // both together - which is what Adam asked for: "if there are more
                            // possible options than what is shown ... always show the ...".
                            // WHAT IS ACTUALLY LEFT OUT, which the submenu changed (VD11-B1).
                            //
                            // `possible` is counted before the split, and the non-choosable paths are
                            // no longer omitted - they are in More Destinations, on screen.  Comparing
                            // against `shown` alone therefore made this fire whenever anything at all
                            // was non-choosable, offering to jump the operator to the autonomy tab
                            // while every destination was already in front of him.  On the author's own
                            // configuration, 20 of 71 squares are marked as not automatic destinations
                            // and 5 more can reverse, so it fired on essentially every right-click -
                            // which is the complaint FR-058 was filed to fix, arriving from the other
                            // side.
                            if (++shown >= Math.min(MAX_PATHS, paths.size())
                                && possible > shown + otherPaths.size())
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

                        // MORE DESTINATIONS (FR-058), which is where everything valid but not
                        // automatic lives: a reversing point, and a square marked as not an automatic
                        // destination.  Each is somewhere the operator may legitimately send a train
                        // by hand - "filtering at selection, never refusing at execution" - and
                        // neither is something autonomy would pick on its own.
                        //
                        // NOT a terminus, which an earlier version of this comment promised and which
                        // Adam ruled out on 2026-09-04: a non-reversible train may back into one, so
                        // it belongs in the base list.  And not an excluded square, which is off the
                        // menu entirely rather than demoted.
                        //
                        // Uncapped.  The cap on the top level is about competing with the rest of the
                        // menu for the first screenful, which a submenu does not do - but a submenu
                        // does compete with the height of the screen, and Swing gives an over-tall
                        // JMenu no scroller unless one is installed (VD11-C9).
                        //
                        // Measured rather than argued: on the author's own configuration 20 of 71
                        // squares are marked as not automatic destinations and 5 more can reverse, and
                        // `withoutGoingNowhere` reduces to distinct destinations - so the ceiling here
                        // is about twenty-five items, which fits.  If a railway ever exceeds a screen
                        // this wants a scroller rather than a second ellipsis.
                        if (!otherPaths.isEmpty())
                        {
                            // The heading, when the top-level list did not already add it (VD11-C3).
                            //
                            // Both the separator and the locomotive's name are gated on the TOP-LEVEL
                            // list being non-empty, and this sits outside that gate - so a train with
                            // nothing automatic to offer got a bare "More Destinations" hanging off
                            // the previous item with no separator and no name to say whose it was.
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
                                more.add(destinationItem(ui, path, locomotive));
                            }

                            add(more);
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
                        // A second "Facing" menu used to be built here (MT-086).
                        //
                        // Two facing menus on one right-click, "Facing" and "<name> Is Facing...",
                        // which is one more than the question has answers.  The one further down is
                        // kept: it names the train it is about, it reads as a choice rather than a
                        // list of places, and it is the same menu the setup editor offers, so the two
                        // surfaces cannot drift into disagreeing about the same square.
                        //
                        // What this one did that the other does not is choose which COPY of a split
                        // square the train stands on. That is the same decision arrived at from the
                        // other end - a copy IS a side to arrive from - so setting the facing reaches
                        // it, which is why removing this loses nothing a user could not still say.

                        menuItem = new JMenuItem(
                            I18n.f("layout.ui.menuRemoveLocomotive", current.getCurrentLocomotive().getName())
                        );
                        menuItem.addActionListener(event ->
                        {
                            // PURGE, as the editor's own Remove does (C15).
                            //
                            // The two doors that take a train off a square passed different answers to
                            // the same question. Without the purge the locomotive stays in the list of
                            // trains to run while standing nowhere: it goes on showing in the Autonomy
                            // tab, and runLocomotives logs it as started and spawns a thread that idles
                            // for the rest of the session. Nothing stalls - it has no destination to
                            // yield to anybody - but the railway is keeping a place for a train that is
                            // not on it.
                            ui.getModel().getAutoLayout().moveLocomotive(
                                null,
                                current.getName(),
                                true
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

                    // Which locomotive belongs here used to be offered from this menu AND from inside
                    // Autonomy Setup, so a square asked the same question twice under two labels.  It
                    // belongs with the rest of the setup, and that is the copy that was kept.
                    //
                    // What went with it was the care over WHICH Point to ask: a home belongs to a
                    // platform and the build writes it onto every copy, but the copy that speaks for a
                    // square is the occupied one, and a train can stand on a copy that is not itself a
                    // destination.  The setup menu works from the SQUARE rather than from a Point, so
                    // that whole question stops arising there - which is the better reason for it to
                    // live in one place rather than two.
                }

                // Which way round the train is standing, beside the other things about the train
                // rather than a level down inside the setup submenu
                javax.swing.JMenu facing = ui.buildAutonomyFacingMenu(here);

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
    /**
     * Everything the autonomy editor offers on this square, folded into this menu.
     *
     * The editor's menu rather than the window's Autonomy menu.  The one in the menu bar is about the
     * setup as a whole - configurations, imports, delete everything - and none of it has anything to
     * say about the square being pointed at.  What somebody right-clicking a platform wants is what
     * the editor gives them when they right-click the same platform there: make this a station, face
     * the train this way, bar that arrival, set the length.  Borrowed from the panel that builds it,
     * so the two can never drift.
     *
     * Under a parent entry, because it is a long menu and most right-clicks on a running layout are
     * about sending a train somewhere, not about changing the track it runs on.
     *
     * Nothing while autonomy is running: the setup describes track that trains are on right now, and
     * an editor open over it is already refused for the same reason.
     */
    private void addSetupMenu()
    {
        if (here == null || ui.isAutonomyBusy()) return;

        JPopupMenu built = ui.buildAutonomyTileMenu(here);

        if (built == null || built.getComponentCount() == 0) return;

        javax.swing.JMenu setup = new javax.swing.JMenu(I18n.t("autosetup.ui.menuAutonomySetup"));

        // getComponents hands back a copy, so moving each one out from under the popup as we go does
        // not walk the array we are reading.
        for (java.awt.Component part : built.getComponents())
        {
            setup.add(part);
        }

        // FR-026: the way out to the full editor, at the foot of the settings it is the deep end of.
        //
        // Adam: "to the 'autonomy setup' right click menu on the track viewer, add a shortcut to open
        // the full editor.  deactivate when inappropriate."  Everything above this line is a single
        // square's settings; the editor is where the diagram as a whole is worked on, and the way to
        // it was the Edit button at the other end of the window.
        //
        // "Inappropriate" is not decided here.  It is asked of the window that does the opening, which
        // hands back the reason its own refusal would give - so the item is live exactly when pressing
        // it would work, and says why when it is not.  A menu item that offers an action and a guard
        // that permits it have to ask one question; where they have asked two in this application, the
        // answer has differed.
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
                // DR-B10: the answer is shown rather than dropped.
                AutonomyReport.show(ui, session.save());
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


    /**
     * One "-> somewhere" item, dispatching this locomotive along this path.
     *
     * Lifted out of the loop when `FR-058` split the list in two (`More Destinations`). Two copies of
     * a menu item that starts a train is two places for the power check, the failure dialog and the
     * off-thread dispatch to fall out of step - and the thread is the part that matters: this runs on
     * the event thread, and `executePath` blocks until the train arrives.
     *
     * @param ui the window, for the model and for the dialogs
     * @param path the route to take
     * @param locomotive the train to send
     * @return the item
     */
    private JMenuItem destinationItem(TrainControlUI ui, List<Edge> path, Locomotive locomotive)
    {
        JMenuItem item = new JMenuItem("-> " + stationName(path.get(path.size() - 1).getEnd()));

        item.addActionListener(event ->
        {
            try
            {
                // TODO there is commonality with AutoLocomotiveStatus - reuse code
                new Thread(() ->
                {
                    if (!ui.getModel().getPowerState())
                    {
                        javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            this, I18n.t("autolayout.ui.powerOnToStart")));
                    }
                    else
                    {
                        boolean success = ui.getModel().getAutoLayout().executePath(
                            path, locomotive, locomotive.getPreferredSpeed(), null
                        );

                        if (!success)
                        {
                            javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                                this, I18n.t("autolayout.ui.autoFailedCheckLog")));
                        }
                    }
                }).start();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });

        return item;
    }
}
