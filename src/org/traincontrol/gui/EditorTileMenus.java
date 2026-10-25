package org.traincontrol.gui;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.util.I18n;

/**
 * The autonomy editor's right-click menus for a square: the tile menu, the text-square menu, and the facing and
 * arrived-from submenus the track diagram's own menu carries too (FR-136).
 *
 * The panel keeps every method something outside these menus calls, as a one-line call to the method of the same
 * name here, and every field: this reads and writes them through `editor`.  Each dialog is still parented on the
 * editor's window.
 */
final class EditorTileMenus
{
    private final AutonomyEditorPanel editor;

    EditorTileMenus(AutonomyEditorPanel editor)
    {
        this.editor = editor;
    }

    /**
     * The same menu, built and handed back rather than shown, so the main window's own right-click menu can carry it:
     * the track diagram there is the same diagram, and one menu built once cannot drift into offering different things
     * in the two places.
     *
     * @param tile which square
     * @param component what is drawn there, or null to read it off the page
     * @return the menu, or null where this square has nothing to offer
     */
    javax.swing.JPopupMenu buildTileMenu(TileKey tile, LayoutDiagramComponent component)
    {
        if (tile == null || editor.session == null || editor.session.getGraph() == null) return null;

        if (component == null) component = editor.componentAt(tile);

        // Right-clicking abandons whatever gesture was in progress: a half-finished path test left armed would swallow
        // the next ordinary click after the menu closes.
        editor.cancelPendingGesture();

        // A text square carries no track, but a station can be WRITTEN on it, and that must not need another editor.
        LayoutDiagramComponent onPage = editor.componentAt(tile);

        // The page has to be one the session knows, or "no component here" would mean "I could not find the page"
        // and every square on it would offer to become a station label.
        if (editor.pageOf(tile) != null && (onPage == null || onPage.isText()))
        {
            return buildTextMenu(tile, onPage);
        }

        // Nothing on an ignored square is the user's to set, so it says so rather than offering a menu of no-ops.  EXCEPT BULK
        // TOOLS, which are not about this square: on a page excluded from autonomy every square is ignored, and Clear All
        // Home Locomotives would be unreachable though its subject is not that page.
        if (editor.isIgnored(tile))
        {
            editor.say(editor.hint, I18n.t("autosetup.ui.infoTileIgnored"));

            javax.swing.JPopupMenu only = new javax.swing.JPopupMenu();

            // AND TAKING A CAPTION OFF, which the setup check names wherever it sits - on a page left out of
            // autonomy too (OB-341).
            if (!editor.menuOnly && editor.session.getCaptionTarget(tile) != null)
            {
                editor.menuTarget = tile;

                addCaptionItems(only, tile, onPage, "", editor.session.getCaptionTarget(tile), true);
                only.addSeparator();
            }

            only.add(editor.bulkTools());

            return only;
        }

        // Right-clicking anywhere in a run opens the run's own menu: the grayed tiles hand the question to the tile that
        // answers it.  A new local rather than the parameter, which the lambdas below capture and so must stay
        // effectively final.
        final TileKey target = editor.leaderOf(tile);

        // Remembered so every item on this menu can flash what it changed without being told twice.
        editor.menuTarget = target;

        // AND ITS PAIR, when this square is joined to another one on this page (FR-056): Go to the partner is right when the
        // partner is on another page and wrong on this one, where jumping moves the view away from the square asked about.
        editor.highlightPartnerOf(target);

        editor.rememberIfStation(target);

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        boolean isPoint = editor.session.getReducer() != null
            && editor.session.getReducer().getPoints().containsKey(target);

        editor.title(menu, isPoint ? editor.pointTitle(target) : component == null
            ? target.getX() + "," + target.getY() : component.getUserFriendlyTypeName());

        // Built where the station settings are and added beside its other half further down.  Null when the square has
        // only one way in, which is not a question worth asking.
        javax.swing.JMenu arrivals = null;

        // Everything a STATION can be tuned with, under one heading, and added LAST (OB-013).  Null on a square that is
        // not a station: Segment Length stays loose on the menu, so plain track can set a length in one click.
        javax.swing.JMenu advanced = null;

        // Held and added at the foot of the menu - see where it is built
        javax.swing.JMenuItem signalItem = null;

        // And the entry guard's, beside it (FR-096)
        javax.swing.JMenuItem entryItem = null;

        if (isPoint)
        {
            // Locomotives first, because placing one is the commonest reason to open this menu.  PUTTING one down is only at a
            // station (a locomotive can only be SENT to one); TAKING one off is not, because the square under a train can stop
            // being a station while the train stays recorded on it.  So that item follows the LOCOMOTIVE, as the track
            // diagram's own menu does.
            final boolean isStation = editor.session.getStore().isStation(target);
            final String standing = editor.locomotiveAt(target);

            // The locomotive's own settings (Place, Facing, Remove) are left out of the DEEP menu: the track diagram's own
            // menu carries them one level up (order, MT-053: put one here, edit what is here, take it away, then its facing,
            // then its station).  "Add to autonomy" stays here: the diagram's menu places the locomotive selected on the
            // KEYBOARD, while this asks which locomotive.
            if (isStation)
            {
                menu.add(editor.item(I18n.t("autosetup.ui.menuAddToAutonomy"),
                    () -> editor.placeLocomotive(target)));
            }

            if (!editor.menuOnly) editor.addLocomotiveSettings(menu, target);

            if (standing != null && !editor.menuOnly)
            {
                menu.add(editor.item(I18n.f("autosetup.ui.menuRemoveLocomotive", standing),
                    () ->
                    {
                        editor.session.placeLocomotive(target, null);

                        // The setup's new answer for this train is "nowhere", and it is an answer -
                        // without naming it the rebuild puts the train straight back.
                        editor.placementChanged(java.util.Collections.singletonList(standing));
                    }));
            }

            // WHETHER IT RUNS (FR-117): an 'active' checkbox, shown where there is a locomotive at that station.  Read off
            // the setup and written to the train and the setup together.  Not in the deep menu, whose train items are on
            // the track diagram's own menu; and only at rest.
            if (standing != null && !editor.menuOnly && (editor.parentWindow() == null || !editor.parentWindow().aRunHoldsTheSetup()))
            {
                menu.add(editor.toggle(I18n.f("autolayout.ui.menuLocomotiveActive", standing),
                    "autolayout.ui.hintLocomotiveActive", !editor.session.getPausedLocomotives().contains(standing),
                    on -> editor.setActive(standing, on)));
            }

            if (isStation)
            {
                // No "Move a Locomotive to This Station..." (OB-009): the edit dialog below also asks which locomotive, and
                // does more.  The list "Add to Autonomy" offers is the whole roster, so it does what Move did.

                // Which way round the train is standing: asked here because the user is looking at the square, offered only
                // where the answer could be more than one thing and only while a train is on it.  Nobody has to answer: left
                // alone it takes the first facing, and once autonomy runs, where the train ends up says which way it was
                // pointing.  Built by buildFacingMenu so the two surfaces cannot drift (OB-039).
                if (!editor.menuOnly)
                {
                    javax.swing.JMenu facingMenu = buildFacingMenu(target);

                    // ONE MENU FOR BOTH ENDS OF THE TRAIN.  The tail question is inside that menu under its own heading, so
                    // both surfaces get it: the viewer asks TrainControlUI for `buildFacingMenu` and for nothing else.
                    if (facingMenu != null) menu.add(facingMenu);
                }

                // Last of the locomotive group (MT-104).  A home is a fact about a LOCOMOTIVE (which one belongs here), so it
                // goes with the items about the train rather than the ones about the platform.
                if (isStation)
                {
                    String home = editor.homeOf(target);

                    javax.swing.JMenuItem homeItem = editor.item(home == null
                        ? I18n.t("autosetup.ui.menuHomeNone") : I18n.f("autosetup.ui.menuHomeFor", home),
                        () -> editor.promptHome(target));

                    // THE KEY THAT DOES THIS WITHOUT THE MENU (OB-214).  See `SHORTCUT_HOME`.  Not on the track diagram's
                    // menu, which borrows this one: the key works only in the editor.
                    if (!editor.menuOnly) homeItem.setToolTipText(AutonomyEditorPanel.SHORTCUT_HOME);

                    menu.add(homeItem);
                }
            }

            if (standing != null || isStation) menu.addSeparator();

            javax.swing.JMenuItem renameItem =
                editor.item(I18n.t("autosetup.ui.menuRename"), () -> editor.promptName(target));

            // And Control+S, the key that names the square under the pointer (OB-214), in the editor only: on the track
            // diagram it swaps key mappings
            if (!editor.menuOnly) renameItem.setToolTipText(AutonomyEditorPanel.SHORTCUT_NAME);

            menu.add(renameItem);

            menu.addSeparator();

            // What trains may do at this square, as one three-way choice: stop, pass through, or neither.  Mutually
            // exclusive by construction, so there is no combination to get wrong and nothing to gray out.
            final boolean isOpen = !Boolean.FALSE.equals(editor.session.getPointProperty(target, "active"));

            javax.swing.JMenu stationMenu = new javax.swing.JMenu(
                I18n.f("autosetup.ui.menuStationGroup", editor.stationSummary(target, isStation)));

            stationMenu.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.tooltipStationGroup")));

            javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();

            // Headed: four answers under one menu, and the heading says what the four are about
            editor.title(stationMenu, I18n.t("autosetup.ui.menuStationHeading"));

            stationMenu.add(editor.radio(group, I18n.t("autosetup.ui.menuCanStop"),
                "autolayout.ui.tooltip.Station", isOpen && isStation,
                () -> editor.setUsage(target, true, true)));

            stationMenu.add(editor.radio(group, I18n.t("autosetup.ui.menuCanTraverse"),
                "autosetup.ui.hintCanTraverse", isOpen && !isStation,
                () -> editor.setUsage(target, false, true)));

            // Out of service.  What the square IS is left alone, so switching back returns it to what it was.
            stationMenu.add(editor.radio(group, I18n.t("autosetup.ui.menuNeither"),
                "autolayout.ui.tooltip.Active", !isOpen,
                () -> editor.setUsage(target, isStation, false)));

            stationMenu.addSeparator();

            // Autonomy's own choosing, said directly.  Switched off, a route the user picks still reaches this station and
            // Return Home still fills it; only full autonomy leaves it alone.
            javax.swing.JCheckBoxMenuItem auto = editor.toggle(I18n.t("autosetup.ui.menuAutoDestination"),
                "autosetup.ui.hintAutoDestination", editor.session.isAutoDestination(target),
                on -> editor.session.setAutoDestination(target, on));

            // Grayed rather than hidden, so the shape of the choice stays visible.  NOT grayed for an out-of-service square
            // (Adam: *"Can Be Chosen in Full Autonomy - does not apply to returning home, of course.  these should be
            // allowed."*): `active` is whether the railway may use the square, this is whether autonomy chooses it as a
            // destination, and homing never consults it, which is what makes a parking berth work.  Still grayed on a square
            // that is not a station: autonomy cannot choose somewhere trains do not stop.
            auto.setEnabled(isStation);

            stationMenu.add(auto);

            // HOW LONG A TRAIN MAY BE TO STOP HERE (FR-046).  With the usage choices, because it answers the same
            // question: what may stand at this platform.  Added only when the square IS a station.  The label carries the
            // current value, as everything on this menu does.
            if (isStation)
            {
                stationMenu.addSeparator();

                int maxLength = editor.number(target, "maxTrainLength", 0);

                javax.swing.JMenuItem maximum = editor.item(I18n.f("autolayout.ui.menuMaxTrainLength",
                    maxLength == 0 ? I18n.t("autolayout.ui.any") : String.valueOf(maxLength)),
                    () -> editor.promptNumber(target, "maxTrainLength",
                        "autolayout.ui.promptEnterMaxTrainLength", 0));

                // What the number means, and the key that sets it: the units are the operator's own, so "4" says nothing
                // about carriages, centimeters or squares, and a reader cannot discover the shortcut by opening the dialog.
                maximum.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.tooltipMaxTrainLength")
                    + (editor.menuOnly ? "" : "  " + I18n.t("autosetup.ui.tooltipMaxTrainLengthKey"))));

                stationMenu.add(maximum);
            }


            menu.add(stationMenu);

            // Switching direction, on any square, and not entangled with anything above: a berth is usually both left
            // alone by autonomy AND reversing in.  Three answers, because "may" and "must" are different railways.  May
            // leaves the plain copies, so a train can pass straight through and the path finder chooses; must leaves only
            // the turning ones, so every arrival turns.  On a dead end they are the same thing.
            javax.swing.JMenu turning = new javax.swing.JMenu(
                I18n.t("autosetup.ui.menuTurningGroup"));

            javax.swing.ButtonGroup turns = new javax.swing.ButtonGroup();

            boolean must = editor.session.isMustTurnAround(target);
            boolean may = editor.session.isTurnAround(target) && !must;

            editor.title(turning, I18n.t("autosetup.ui.menuTurningHeading"));

            turning.add(editor.radio(turns, I18n.t("autosetup.ui.menuTurnNever"),
                "autosetup.ui.hintTurnNever", !may && !must,
                () -> editor.setTurning(target, false, false)));

            turning.add(editor.radio(turns, I18n.t("autosetup.ui.menuTurnMay"),
                "autosetup.ui.hintCanReverse", may,
                () -> editor.setTurning(target, true, false)));

            turning.add(editor.radio(turns, I18n.t("autosetup.ui.menuTurnMust"),
                "autosetup.ui.hintTurnMust", must,
                () -> editor.setTurning(target, true, true)));

            menu.add(turning);

            // Where trains may pull IN from, on the menu itself beside what the square IS.  Only where there is more than one way in:
            // barring the only arrival side would leave a station no train could be sent to, which is a pass-through, the choice above.
            final java.util.List<org.traincontrol.automationui.TilePorts.Side> ways =
                editor.session.arrivalSides(target);

            // Shown GRAYED where there is only one way in, rather than left out (MT-079): an absent menu and a menu with
            // nothing to offer look identical from the outside, and a grayed one with a tooltip answers without being
            // asked.
            if (isStation && ways.size() == 1)
            {
                javax.swing.JMenu only = new javax.swing.JMenu(
                    I18n.t("autosetup.ui.menuArrivalsGroup"));

                only.setEnabled(false);

                only.setToolTipText(AutonomyEditorPanel.wrapped(I18n.f("autosetup.ui.hintOneWayIn",
                    I18n.t("autosetup.ui.side" + ways.get(0).name()))));

                arrivals = only;
            }
            else if (isStation && ways.size() > 1)
            {
                arrivals = new javax.swing.JMenu(
                    I18n.t("autosetup.ui.menuArrivalsGroup"));

                arrivals.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.hintArrivals")));

                editor.title(arrivals, I18n.t("autosetup.ui.menuArrivalsHeading"));

                final java.util.Set<org.traincontrol.automationui.TilePorts.Side> barred =
                    editor.session.getBarredArrivals(target);

                for (final org.traincontrol.automationui.TilePorts.Side side : ways)
                {
                    javax.swing.JCheckBoxMenuItem allow = editor.toggle(
                        I18n.f("autosetup.ui.menuArrivalFrom",
                            I18n.t("autosetup.ui.side" + side.name())),
                        "autosetup.ui.hintArrivals", !barred.contains(side),
                        on -> editor.setArrivalAllowed(target, side, on));

                    // The LAST way in cannot be shut here either: unticking them one at a time is the same as barring a
                    // single-sided station.
                    allow.setEnabled(barred.contains(side)
                        || editor.session.arrivalsCanBeClosed(target, java.util.Collections.singleton(side)));

                    arrivals.add(allow);
                }

                // Held rather than added here.  It is one half of a pair (which ends trains may come IN by, which ends they
                // may go OUT by) and the two are added together, below.
            }

            // The signal thrown to red while this platform is claimed, paired by hand rather than inferred: the nearest signal
            // on the approach is not always the one that protects a platform, and a wrong guess throws a real signal.  Last
            // of the station settings, under the arrivals; labeled with the signal's ADDRESS and outlined on the diagram
            // while the item is acted on; left out of the deep menu, where pairing means clicking a diagram it is drawn over.
            if (isStation && !editor.menuOnly)
            {
                java.util.List<TileKey> paired = editor.session.getProtectingSignals(target);

                // Held, not added (MT-104): it goes at the foot of the menu, under the arrive/depart pair and beside
                // Advanced Parameters, the last of the platform's own settings.
                signalItem = editor.item(
                    paired.isEmpty()
                        ? I18n.t("autosetup.ui.menuPairSignal")
                        : I18n.f(paired.size() == 1
                            ? "autosetup.ui.menuPairedSignal" : "autosetup.ui.menuPairedSignals",
                            editor.signalAddresses(paired)),
                    () -> editor.pairProtectingSignal(target));

                // WHAT THE GUARD DOES (OB-293), said as the runtime does it, `Layout.refreshProtectingSignal`.
                signalItem.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.tooltipExitGuard")));

                // THE ENTRY GUARD, the same dialog on the other list (FR-096), and labeled the same way.
                java.util.List<TileKey> guarding = editor.session.getEntrySignals(target);

                entryItem = editor.item(
                    guarding.isEmpty()
                        ? I18n.t("autosetup.ui.menuPairEntrySignal")
                        : I18n.f(guarding.size() == 1
                            ? "autosetup.ui.menuPairedEntrySignal" : "autosetup.ui.menuPairedEntrySignals",
                            editor.signalAddresses(guarding)),
                    () -> editor.pairGuardSignals(AutonomyEditorPanel.Guard.ENTRY, target));

                // And this one's, as `Layout.throwEntryGuard` does it (OB-293).
                entryItem.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.tooltipEntryGuard")));
            }


            // Everything a station can be TUNED with, under one heading: the settings a railway works without (station
            // priority, speed multiplier, which locomotives are kept out or let in, and blockedByPoints, FR-001).  Every
            // label carries its current value, as the graph window's did.
            advanced = new javax.swing.JMenu(
                I18n.t("autolayout.ui.menuEditAdvancedParameters"));

            // Maximum Train Length is in the Station submenu (FR-046), not here: it is a fact about a platform and means nothing on a
            // square that is not one, while Advanced is shown for squares that are not stations.
            int priority = editor.number(target, "priority", 0);

            advanced.add(editor.item(I18n.f("autolayout.ui.menuStationPriority",
                priority == 0 ? I18n.t("autolayout.ui.default") : String.valueOf(priority)),
                () -> editor.promptNumber(target, "priority",
                    "autolayout.ui.promptEnterStationPriority", 0)));

            advanced.add(editor.item(I18n.f("autolayout.ui.menuSpeedMultiplier", editor.percent(target)),
                () -> editor.promptPercent(target)));

            // named for its mode (FR-121): the locomotives kept out, or the only ones let in
            advanced.add(editor.item(I18n.f(editor.allowsOnlyListed(target) ? "autolayout.ui.menuAllowedLocomotivesOnly"
                : "autolayout.ui.menuExcludedLocomotives", editor.strings(target, "excludedLocs").size()),
                () -> editor.promptLocomotives(target, "excludedLocs", editor.allLocomotives())));

            // FR-001: like excluded locomotives, about PLACES rather than trains, and a restriction on what autonomy may
            // choose.
            advanced.add(editor.item(I18n.f("autolayout.ui.menuBlockedByPoints",
                editor.session.getStore().getBlockingPoints(target).size()),
                () -> editor.promptBlockingPoints(target)));

            menu.addSeparator();
        }

        // Everything about where trains may run, under one heading: each item is a different sentence about the same
        // subject.
        javax.swing.JMenu connections = new javax.swing.JMenu(I18n.t("autosetup.ui.menuConnections"));

        // Headed, because there are four kinds of thing in here once a square has several arms
        editor.title(connections, I18n.t("autosetup.ui.menuDepartHeading"));

        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = editor.session.getRoutes(target);

        // A link has no direction of its own, by decision: the track either side of it governs which way trains may run.  Its one
        // route is a stub (the same side twice), so "toward A" and "toward B" name the same place and offering the four answers
        // would be a setting that silently does nothing.  Pinned by testALinkIsNotOfferedADirection (see TileGraph.PORTAL_ROUTE);
        // one-way cross-page running needs the jump itself to carry a direction (backlog).
        if (editor.session.canCarryDirection(target))
        {
            boolean many = routes.size() > 1;

            // Every arm, tickable, all visible together.  A click steps through four common answers and cannot reach the
            // rest; these are the arrows themselves, one box each, so any combination is one look and one tick - and on a
            // switch they are the whole of its settings (OB-351).
            if (many)
            {
                final java.util.List<org.traincontrol.automationui.TilePorts.Side> arms =
                    AutonomyEditorPanel.armsOf(routes);

                int mask = editor.armMask(target, routes, arms);

                editor.title(connections, I18n.t("autosetup.ui.menuArmsHeading"));

                for (int i = 0; i < arms.size(); i++)
                {
                    final org.traincontrol.automationui.TilePorts.Side arm = arms.get(i);

                    connections.add(editor.toggle(I18n.f("autosetup.ui.menuArm", String.valueOf(arm)),
                        "autosetup.ui.hintArms", (mask & (1 << i)) != 0,
                        on -> editor.setArm(target, arm, on)));
                }

                connections.addSeparator();
            }

            // NO ROAD OF ITS OWN WHERE ROADS SHARE AN ARM (OB-351; Adam: *"into spur should simply require S to be green, and out of
            // spur should require W to be green.  Align it so that the arrows tell the full story."*): on a switch a road's own
            // submenu could shut its spur's way out at the toe while the main line's arrow stayed green, a closure the diagram does
            // not show.  A crossing's or double curve's roads end at arms of their own, so each road's submenu stays.
            boolean byArmsOnly = many && editor.session.getGraph().sharesAnArm(target);

            for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry : routes.entrySet())
            {
                org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

                if (byArmsOnly) break;

                // A switch's branches each get their own submenu; a plain target puts its four answers straight on the menu.
                if (many)
                {
                    // no font set: submenus inherit the look and feel's, as the editor's own do
                    javax.swing.JMenu branch = new javax.swing.JMenu(
                        I18n.f("autosetup.ui.menuBranch",
                            String.valueOf(route.getA()), String.valueOf(route.getB())));

                    for (javax.swing.JMenuItem option : editor.directionItems(target, entry.getKey(), route))
                    {
                        branch.add(option);
                    }

                    connections.add(branch);
                }
                else
                {
                    for (javax.swing.JMenuItem option : editor.directionItems(target, entry.getKey(), route))
                    {
                        connections.add(option);
                    }
                }
            }

            if (many)
            {
                javax.swing.JMenu all = new javax.swing.JMenu(
                    I18n.t("autosetup.ui.menuAllBranches"));

                all.add(editor.item(I18n.t("autosetup.ui.menuRouteBoth"),
                    () -> editor.setAllBranches(target, Direction.BOTH)));
                all.add(editor.item(I18n.t("autosetup.ui.menuRouteNone"),
                    () -> editor.setAllBranches(target, Direction.NONE)));

                connections.add(all);
            }

        }

        // No "Make a One-Way Run from Here" item (OB-006): everything else on this menu names ONE square and acts on it,
        // while a one-way run waits for a second click, which is a mode, and a mode advertised from a menu that closes the
        // moment you pick it shows the user nothing.  It is a button beside Test and Why.

        // The two halves of the same question, side by side: which ends trains may arrive by, and which they may leave
        // by.
        if (arrivals != null) menu.add(arrivals);

        // Only when there is something under the heading.  Everything that fills this submenu sits inside
        // canCarryDirection(target), false for any portal, so right-clicking a link or a tunnel would open a grayed
        // heading and a separator (the state hasItemsBesidesTitle detects, OB-032).
        if (editor.hasItemsBesidesTitle(connections)) menu.add(connections);

        // Then a divider, the signal, and the drawer of numbers (MT-104).  Everything above is a decision about this
        // square; what is left is the platform's protection and the settings that change how well those decisions work.
        if (signalItem != null || advanced != null) menu.addSeparator();

        if (signalItem != null) menu.add(signalItem);

        if (entryItem != null) menu.add(entryItem);

        if (advanced != null) menu.add(advanced);

        // A link's own settings, on the menu ITSELF rather than inside the departures submenu.  A link is a jump to
        // another page, not track with directions, and the three things anybody does to one (use it or not, pair it,
        // unpair it) are the whole reason to right-click it.
        if (component != null && (component.isLink()
            || component.getType() == LayoutDiagramComponent.componentType.TUNNEL))
        {
            menu.addSeparator();

            // No heading of its own (OB-054): the menu is already titled with what the square IS, "Page Link".  Autonomy can be told
            // to leave a link alone entirely: a diagram can carry one that belongs to the drawing rather than the railway, and
            // refusing to build until it is paired would insist on something the user decided against.
            menu.add(editor.toggle(I18n.t("autosetup.ui.menuUseLink"),
                "autosetup.ui.hintUseLink",
                !editor.session.getStore().isPortalDisabled(target),
                on ->
                {
                    editor.session.setPortalDisabled(target, !on);

                    // A shut link is a missing edge in the running graph, so the railway has to be told.  This
                    // writer is not inside a door, which is why it pushes no undo point.
                    editor.setupChanged();
                }));

            menu.add(editor.item(I18n.t("autosetup.ui.menuPairLink"), () -> editor.pairFromList(target)));

            final TileKey partner = editor.session.getStore().getPortalPartner(target);

            if (partner != null)
            {
                // The other end: a pairing is the one thing on this menu ABOUT somewhere else, usually on another page,
                // which the drawing cannot show.
                menu.add(editor.item(I18n.f("autosetup.ui.menuGoToLinkPartner", editor.linkLabel(partner)),
                    () -> editor.goToLink(partner)));

                // The railway has to be told, because this item REMOVES an edge: `session.unpairPortal` rebuilds only the
                // editor's own graph, so on the track-diagram door, which saves at once, the unpair would reach disk while
                // autonomy went on routing through it.
                menu.add(editor.item(I18n.t("autosetup.ui.menuUnpairLink"),
                    () ->
                    {
                        editor.session.unpairPortal(target);

                        editor.setupChanged();
                    }));
            }

            // And a rule under the group: above it is about the link as a link, below it about the SQUARE, the same items
            // any other square gets.
            menu.addSeparator();
        }

        // Naming a link, on the menu itself rather than inside Connections: a link is FOUND by its name (an unnamed one shows in
        // the pairing list as a coordinate pair nobody recognizes), so it is the first thing done to a new link; what it is
        // CALLED is not a connection.
        if (component != null && (component.isLink()
            || component.getType() == LayoutDiagramComponent.componentType.TUNNEL))
        {
            menu.add(editor.item(I18n.t("autosetup.ui.menuSetName"), () -> editor.promptLinkName(target)));
        }

        // On the menu itself rather than inside Advanced Parameters (OB-013): Advanced belongs to a STATION and a length to
        // any square.  Offered where `offersALength` says and bound to the square it names (FR-066): Control+E and this
        // item ask the same question, so there is one.
        if (editor.offersALength(tile))
        {
            javax.swing.JMenuItem lengthItem = editor.item(I18n.t("autosetup.ui.menuSetLength"),
                () -> editor.applyLength(editor.squareTheLengthWouldGoOn(tile)));

            // OB-214: the tooltip names Control+E, where Control+E does it
            if (!editor.menuOnly) lengthItem.setToolTipText(AutonomyEditorPanel.SHORTCUT_LENGTH);

            menu.add(lengthItem);
        }


        // A station name can go on almost any square, not only on a text square: the label is drawn beside the tile wherever
        // it sits.  Allowed on curves and dead ends (OB-042, OB-044); refused only on clickable elements (switches and
        // signals), because a caption there puts text over a control.  The CLICKED square, not the run leader: a name
        // belongs where it was put.  Editor only: they write text onto the DIAGRAM, and the deep menu is reached by
        // right-clicking the diagram itself.
        final LayoutDiagramComponent here = editor.componentAt(tile);

        if (editor.mayCarryACaption(here) && !editor.menuOnly)
        {
            menu.addSeparator();

            String text = here == null || here.getLabel() == null ? "" : here.getLabel();

            addCaptionItems(menu, tile, here, text, editor.session.getCaptionTarget(tile), false);
        }
        else if (!editor.menuOnly && editor.session.getCaptionTarget(tile) != null)
        {
            // A caption sits on this square all the same (a switch or signal built where one was already shown, or a
            // setup edited by hand) and the setup check names it (OB-341), so it has to be possible to take it off here.
            menu.addSeparator();

            addCaptionItems(menu, tile, here, "", editor.session.getCaptionTarget(tile), true);
        }

        // Bulk tools last (MT-257): they are about the WHOLE SETUP rather than this square, so they sit at the bottom
        // behind a divider.  The separator is added unconditionally and `tidy` decides whether it survives, so this cannot
        // leave a stray line above the submenu.
        menu.addSeparator();

        menu.add(editor.bulkTools());

        AutonomyEditorPanel.tidy(menu);

        return menu;
    }

    /**
     * The text-square menu, built and handed back.  See buildTileMenu.
     */
    private javax.swing.JPopupMenu buildTextMenu(final TileKey tile,
        final LayoutDiagramComponent component)
    {
        editor.menuTarget = tile;

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        String label = component == null || component.getLabel() == null ? "" : component.getLabel();

        // What the square is showing: a station's caption, the user's own text, or nothing.  The caption is an autonomy
        // object drawn here, not text on the diagram, so it is asked for rather than read off the label.
        TileKey captioned = editor.session.getCaptionTarget(tile);

        editor.title(menu, captioned != null ? editor.describeTile(captioned)
            : label.trim().isEmpty() ? I18n.t("autosetup.ui.titleEmptyText") : label);

        // Offered on a blank square too: writing a station name on a blank square is how a diagram with no text squares gets its
        // first one.  And offered over somebody's own text, asking instead of refusing, naming the text it would replace: a refusal
        // left a state with no way out; a question is a way out, a disabled item is not.
        addCaptionItems(menu, tile, component, label, captioned, false);

        // And bulk tools, as every square of track ends with (MT-474): they are about the whole setup rather than the
        // square under the pointer, so they go at the bottom behind a divider, as `buildTileMenu` places them (MT-257).
        menu.addSeparator();

        menu.add(editor.bulkTools());

        AutonomyEditorPanel.tidy(menu);

        return menu;
    }

    /**
     * "Show Station Here..." and its clear, added to whichever menu is being built.
     *
     * Shared so the two menus cannot drift into offering different things.
     *
     * @param menu the menu being built
     * @param tile the square
     * @param component what is drawn on it, or null for a blank square
     * @param label whatever text the square already carries
     * @param captioned the station this square is already showing, or null
     * @param stopOnly only the item that stops showing it, for a square that may not be given a caption but has one
     *        (OB-341)
     */
    private void addCaptionItems(javax.swing.JPopupMenu menu, final TileKey tile,
        final LayoutDiagramComponent component, String label, TileKey captioned, boolean stopOnly)
    {
        boolean mine = label.trim().isEmpty() || captioned != null;

        // Both items NAME the station when there is one (FR-014): a caption draws the station's OCCUPANT, and an empty station
        // draws as GraphLocAssign.NONE_LABEL, so a square captioning a station with no train says nothing about which station it
        // is.  Here because both menus build their caption items through this method.
        String showing = captioned == null ? null : editor.describeTile(captioned);

        // The name is NOT repeated here (MT-162): the item below already reads "Stop Showing <name>".  The two keys still differ
        // ("a Station Name" when the square shows none, "a Different Station" when it already shows one).
        // A station square has three states, and `promptStationLabel` captions it with itself whatever it is showing, so
        // none is ever offered a choice; what differs is whether choosing it would CHANGE anything:
        //   already showing itself    nothing to do.  Grayed, and the label says why.
        //   showing another station   resets it to its own.  Live - a drag can produce this.
        //   showing nothing           captions it with itself.  Live: graying it would make Stop Showing one-way.
        // Neither live case reads "a DIFFERENT station", because a station square cannot show one.  The grayed item is
        // disabled rather than removed, and its tooltip carries the reason.
        boolean showsItself = editor.session.getStore().isStation(tile) && tile.equals(captioned);

        boolean aStationSquare = editor.session.getStore().isStation(tile);

        // Not on a square that may not carry one: it can only be taken off there (OB-341)
        if (!stopOnly)
        {
            javax.swing.JMenuItem name = editor.item(
                showsItself ? I18n.t("autosetup.ui.menuStationShowsItself")
                    : showing == null || aStationSquare ? I18n.t("autosetup.ui.menuShowStationHere")
                    : I18n.t("autosetup.ui.menuShowStationHereNamed"),
                () -> editor.promptStationLabel(tile, component));

            // Grayed, with the reason, on a square the running diagram does not draw
            boolean inTheDrawnArea = editor.session.isInTheDrawnArea(tile);

            name.setEnabled(!showsItself && inTheDrawnArea);

            // WHAT A CAPTION IS, on the item that makes one: nothing else on this menu says that a caption is a live
            // readout of another square rather than a name plate.
            name.setToolTipText(!inTheDrawnArea && !showsItself ? AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.captionOutsideTheDrawnArea"))
                : AutonomyEditorPanel.wrapped(
                (showsItself ? I18n.t("autosetup.ui.tooltipStationShowsItself")
                    : !mine ? I18n.t("autosetup.ui.tooltipTextInTheWay")
                    : I18n.t("autosetup.ui.tooltipShowStationHere"))
                // AND ITS KEY (FR-086), added to the sentence rather than replacing it: this tooltip is the only place that says what a
                // caption is.  IN THE EDITOR ONLY: the main window builds this menu with `setMenuOnly`, where Control+N is the
                // notes key.
                + (editor.menuOnly ? "" : "  (" + AutonomyEditorPanel.SHORTCUT_STATION + ")")));

            menu.add(name);
        }

        if (captioned != null)
        {
            // Naming what is about to be cleared matters here: this item is destructive, and "Clear This Square" on a
            // square reading [---] asks the user to confirm removing something they cannot identify.
            menu.add(editor.item(I18n.f("autosetup.ui.menuClearStationHereNamed", showing),
                () -> editor.applyCaption(tile, null)));
        }
    }

    /**
     * A bold, disabled line naming what the items under it are about: the same device the right-click menu heads itself
     * with, because a set of options with no title is a set the reader has to infer the subject of.
     *
     * @param text what the set below is about
     * @return the heading item
     */
    private static javax.swing.JMenuItem heading(String text)
    {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(text);

        item.setEnabled(false);
        item.setFont(item.getFont().deriveFont(java.awt.Font.BOLD));

        return item;
    }

    /**
     * Where the tail of the train standing here lies - the "arrived from" side.
     *
     * Exposed in the right-click menu's advanced settings, in both the autonomy editor and the track diagram viewer.
     * Built here rather than twice: `TrainControlUI.buildAutonomyTileMenu` serves this panel's menu to the track
     * diagram, so one implementation reaches both surfaces (two copies is how `OB-039` survived being fixed).
     *
     * The sides offered are the ones the railway HAS: `ArrivalSidePrompt.sidesOf` is the same list the placement prompt
     * uses, so the menu and the prompt cannot disagree about which answers exist.
     *
     * Null when no train is standing here: a tail belongs to a train.
     *
     * @param target the square
     * @return the menu, or null when there is nothing to offer
     */
    javax.swing.JMenu buildArrivedFromMenu(final TileKey target)
    {
        if (target == null || editor.locomotiveAt(target) == null) return null;

        org.traincontrol.automation.Layout running =
            editor.runningLayout == null ? null : editor.runningLayout.get();

        if (running == null) return null;

        org.traincontrol.automation.Point point = editor.pointOnTheLayout(running, target);

        if (point == null) return null;

        // A train has to be standing there on the RUNNING railway: the offer above asks the SETUP, and `pointOnTheLayout`
        // falls back to any copy, so a square the setup assigns a train while the running layout has not placed one would
        // write a side onto an unoccupied Point, which `Point.toJSON` declines to save.  While the running layout is held
        // (during a run, before the first rebuild after a configuration is loaded) the item is absent rather than grayed;
        // otherwise `setupChanged`'s rebuild catches the placement up within the gesture.
        if (point.getCurrentLocomotive() == null) return null;

        // THE BUILD'S SIDES, which the model reads as well (OB-182): the entry side travels in the configuration, and
        // `Layout.entrySideOf` is what both the arrival write and the tail walk consult, so offering it here stores a
        // value they can match.
        java.util.List<String> sides = new java.util.ArrayList<>(
            // UNBARRED (OB-204): this menu records where a train CAME FROM, and a side nothing may
            // arrive by is not one of those.  The "Trains May Arrive" menu above still offers every
            // side, because its whole job is to open and close them.
            org.traincontrol.gui.autonomy.ArrivalSidePrompt.sidesOf(editor.session.unbarredArrivalSides(target)));

        final String recorded = editor.session.getArrivedFrom(target);

        // A recorded side the track does not have is shown, not offered: the radios are ticked by `side.equals(recorded)`,
        // so a recorded side not in the list would tick NOTHING and read as "no tail recorded" for a square that has one,
        // a value still steering the tail walk.  Unlike the facing menu (OB-177) it is not added to the list, since an
        // arrival side the track does not have can never match the geometry: it appears once, ticked, DISABLED.  "Not
        // offered here" rather than "not on the track", because the list is the sides of the COPY this train stands on and
        // a split square has several.
        final String recordedButGone =
            recorded != null && !sides.contains(recorded) ? recorded : null;

        if (sides.isEmpty() && recordedButGone == null) return null;

        javax.swing.JMenu menu = new javax.swing.JMenu(I18n.t("autosetup.ui.menuArrivedFrom"));

        menu.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.hintArrivedFrom")));

        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();

        for (final String side : sides)
        {
            menu.add(editor.radio(group, org.traincontrol.gui.autonomy.ArrivalSidePrompt.labelFor(side),
                "autosetup.ui.hintArrivedFrom",
                side.equals(recorded),
                () ->
                {
                    editor.session.setArrivedFrom(target, side);

                    // A road back describes one side: the tail cannot lie along a road that leaves by another, so a
                    // changed side forgets it, here and on the railway below.
                    if (!side.equals(recorded)) editor.session.setArrivedAlong(target, null);

                    // SAVED AND SHOWN, like every other door that writes a point property.
                    editor.setupChanged();

                    // THE RUNNING LAYOUT TOO, not only the setup: what blocks track is the Point, and a setting that
                    // changes the diagram while the railway uses the old value is a defect.
                    org.traincontrol.automation.Layout now =
                        editor.runningLayout == null ? null : editor.runningLayout.get();

                    if (now != null)
                    {
                        org.traincontrol.automation.Point on = editor.pointOnTheLayout(now, target);

                        if (on != null) on.setArrivedFrom(side);

                        if (on != null && !side.equals(recorded)) on.setArrivedAlong(null);
                    }
                }));
        }

        if (recordedButGone != null)
        {
            javax.swing.JRadioButtonMenuItem gone = new javax.swing.JRadioButtonMenuItem(
                I18n.f("autosetup.ui.arrivedFromNotOnTrack",
                    org.traincontrol.gui.autonomy.ArrivalSidePrompt.labelFor(recordedButGone)), true);

            // Not in the group, and not enabled: it is a statement about the square, not a choice.
            gone.setEnabled(false);

            gone.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.hintArrivedFromNotOnTrack")));

            menu.add(gone);
        }

        // NO WAY TO CLEAR THE SIDE, by Adam's ruling: **"clearing should not be possible, only setting."**  Every side the
        // geometry offers is above, so correcting a wrong answer means picking the right one, and clearing only switched the tail
        // blocking off.  The automatic clear stays in Point.setLocomotive: a new occupant did not arrive the way the old one did.
        return menu;
    }

    /**
     * The "{loc} Is Facing..." submenu for a square, or null when there is nothing to ask.
     *
     * Handed out on its own so that the track diagram's own right-click menu can carry it, beside the other things it
     * offers about the train standing there (Place, Facing, Remove and the locomotive's settings); the deep menu does
     * not offer it.
     *
     * Null unless a locomotive is standing there.  Where the square has no facing to choose - no track leads out of
     * it - the submenu is there and grayed, with the reason in its tooltip (OB-347).
     *
     * @param target the square
     * @return the submenu, or null
     */
    javax.swing.JMenu buildFacingMenu(final TileKey target)
    {
        if (target == null) return null;

        org.traincontrol.automation.Layout showing =
            editor.runningLayout == null ? null : editor.runningLayout.get();

        // THE TRAIN THE RAILWAY HAS HERE, and only then the setup's.  After a run the setup still names each
        // square's pre-run occupant (behaviour.md 6a) and nothing captures before this menu, so it would name the setup's
        // train and tick the railway's, and a click would turn neither.  `setFacingAndMove` turns the one named here.
        String onTheRailway = editor.session == null ? null : editor.session.trainOnTheRailway(target, showing);

        final String standing = onTheRailway != null ? onTheRailway : editor.locomotiveAt(target);

        if (standing == null) return null;

        final java.util.List<org.traincontrol.automationui.TilePorts.Side> facings =
            new java.util.ArrayList<>(editor.session.facingChoices(target));

        // WHAT THE RAILWAY SAYS, and only then what the file says (OB-181): the station label reads which COPY of the square the
        // train stands on, which on a split square IS its direction, while the stored facing is written only when somebody places
        // a train by hand.  The file is the fallback: an empty square has no train whose direction this could be.
        org.traincontrol.automationui.TilePorts.Side facingOnTheRailway =
            editor.session.facingOnTheRailway(target, showing);

        // NOT ONE THE TRACK DOES NOT RUN (OB-348), which the caption does not show and the icon cannot be drawn along
        final org.traincontrol.automationui.TilePorts.Side recorded =
            facingOnTheRailway != null ? facingOnTheRailway : editor.session.recordedFacingAlongTheTrack(target);

        // AND WHATEVER IS ACTUALLY RECORDED, even when this square cannot hold it (OB-177): the radios are ticked by
        // `facing == recorded`, so a recorded facing not among the offered ones would tick NOTHING, reading as "no facing" for a
        // train that has one (`facingsThatCannotBeHeld` reports it).  It gets an entry rather than being silently corrected,
        // because which is wrong, the record or the track under it, is not this menu's to decide.  NOT WHERE NO TRACK LEADS OUT
        // (OB-348): there the menu is grayed (OB-347) whatever is recorded.
        if (recorded != null && !facings.isEmpty() && !facings.contains(recorded)) facings.add(recorded);

        // Shown even with one answer, so the menu explains itself instead of vanishing ("no menu" reads as a fault).  A
        // facing has to be one the BUILD can hold (`placementCopy` matches it against the copies the split made, and
        // anything else falls back to `startableCopy` and quietly turns the train round), so a second facing is never
        // offered where the railway holds one; the one facing is shown, ticked, with a line saying why there is nothing
        // to choose.
        //
        // Grayed, not hidden, where no track leads out (OB-347; Adam: *"rather than hiding it, grey it out."*).  ONE
        // SUBMENU (OB-348): built once, here, so the track diagram and the editor cannot offer two that drift apart.
        javax.swing.JMenu facingMenu = new javax.swing.JMenu(
            I18n.f("autosetup.ui.menuFacingGroup", standing));

        if (facings.isEmpty())
        {
            facingMenu.setEnabled(false);
            facingMenu.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.hintFacingNoWayOut")));

            return facingMenu;
        }

        facingMenu.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.hintFacing")));

        // A HEADING, because this menu answers two questions: two sets of compass points with nothing between them would read as
        // eight ways of saying one thing, and they are opposite ends of the train.
        facingMenu.add(heading(I18n.t("autosetup.ui.headingFacing")));

        javax.swing.ButtonGroup facingGroup = new javax.swing.ButtonGroup();

        for (final org.traincontrol.automationui.TilePorts.Side facing : facings)
        {
            facingMenu.add(editor.radio(facingGroup,
                I18n.t("autosetup.ui.facing" + facing.name()), "autosetup.ui.hintFacing",
                // NOTHING TICKED WHERE NOTHING IS KNOWN: with no facing recorded and none on the railway,
                // the build stands the train on a copy it can start from, which need not be the first facing.  Where
                // the square holds one facing, that one IS known - the only copy is where the train stands.
                recorded != null ? facing == recorded : facings.size() == 1,
                // The redraw is in radio() itself.  The railway too, not only the setup: `setFacingAndMove` stands the train
                // on the copy that says the new direction, so the caption and the train cannot disagree until the next
                // build.
                () -> editor.session.setFacingAndMove(target, facing)));
        }

        if (facings.size() == 1)
        {
            javax.swing.JMenuItem only = new javax.swing.JMenuItem(
                I18n.t("autosetup.ui.facingOnlyOne"));

            only.setEnabled(false);
            only.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.hintFacingOnlyOne")));

            facingMenu.add(only);
        }

        // AND WHERE ITS TAIL IS, in the same menu: the two questions are opposite ends of one train, and the viewer only ever asks
        // for this menu.  Under its own heading, after a separator, so the two sets of compass points are not one list.
        javax.swing.JMenu tail = buildArrivedFromMenu(target);

        if (tail != null && tail.getMenuComponentCount() > 0)
        {
            facingMenu.addSeparator();
            facingMenu.add(heading(I18n.t("autosetup.ui.headingArrivedFrom")));

            // The ITEMS, not the submenu: a submenu inside this one would be the third click for a question already
            // two levels down.
            while (tail.getMenuComponentCount() > 0)
            {
                java.awt.Component item = tail.getMenuComponent(0);

                tail.remove(0);
                facingMenu.add(item);
            }
        }

        // And how far back its tail reaches: the same list as the placement question, in the same menu as the side it
        // follows from, on both surfaces - and in the editor a way to click the sensor instead.
        appendTailCrossed(facingMenu, target);

        return facingMenu;
    }

    /**
     * The "farthest sensor the tail crossed" choices for the train standing here, where the answer matters.
     *
     * Nothing is added unless `TailCrossedPrompt.wouldAsk` says a junction behind the train has two roads back that put
     * the tail on different track - it has crossed a sensor on one of them, or passed the switch where they part
     * (MT-477): elsewhere every answer describes the same track.  The ticked entry is the recorded road -
     * the running layout's, since that is what blocks track - matched as the longest choice it ends with, because a
     * driven train's road starts wherever it set off.
     *
     * @param menu the facing menu
     * @param target the square
     */
    private void appendTailCrossed(javax.swing.JMenu menu, final TileKey target)
    {
        org.traincontrol.automation.Layout running = editor.runningLayout == null ? null : editor.runningLayout.get();

        if (running == null) return;

        org.traincontrol.automation.Point point = editor.pointOnTheLayout(running, target);

        if (point == null || point.getCurrentLocomotive() == null) return;

        String side = point.getArrivedFrom() != null ? point.getArrivedFrom() : editor.session.getArrivedFrom(target);

        Integer length = point.getCurrentLocomotive().getTrainLength();

        if (!TailCrossedPrompt.wouldAsk(running, point, side, length)) return;

        java.util.List<TailCrossedPrompt.Choice> choices =
            TailCrossedPrompt.choicesFor(running, point, side, length, editor.session::baseNameOf);

        TailCrossedPrompt.Choice ticked = TailCrossedPrompt.recordedChoice(choices, point.getArrivedAlong());

        menu.addSeparator();
        menu.add(heading(I18n.t("autosetup.ui.headingTailCrossed")));

        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();

        for (final TailCrossedPrompt.Choice choice : choices)
        {
            menu.add(editor.radio(group, choice.getLabel(), "autosetup.ui.hintTailCrossed", choice == ticked,
                () -> editor.recordTailRoad(target, choice.getRoad())));
        }

        menu.add(editor.radio(group, I18n.t("autosetup.ui.tailCrossedNotKnown"), "autosetup.ui.hintTailCrossed",
            ticked == null, () -> editor.recordTailRoad(target, null)));

        // CLICKING IS THE EDITOR'S: on the track diagram's menu this panel has no grid to be clicked on.
        if (!editor.menuOnly && editor.page != null)
        {
            menu.add(editor.item(I18n.t("autosetup.ui.menuPickTailCrossed"), () -> editor.armTailPick(target)));
        }
    }
}
