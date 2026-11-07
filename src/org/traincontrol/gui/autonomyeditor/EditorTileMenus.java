package org.traincontrol.gui.autonomyeditor;

import java.util.Map;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.util.I18n;

/**
 * The autonomy editor's right-click menus for a square: the tile menu, the text-square menu, and the facing and
 * arrived-from submenus the track diagram's own menu carries too (FR-136).
 *
 * The panel keeps every field and every method something outside these menus calls; this reads and writes them through
 * `editor`.
 */
final class EditorTileMenus
{
    private final AutonomyEditorPanel editor;

    /** A square's facing question: the train it is about, what is offered, what is ticked. */
    static final class FacingFacts
    {
        final String standing;
        final java.util.List<org.traincontrol.automationui.TilePorts.Side> facings;
        final org.traincontrol.automationui.TilePorts.Side recorded;

        FacingFacts(String standing, java.util.List<org.traincontrol.automationui.TilePorts.Side> facings,
            org.traincontrol.automationui.TilePorts.Side recorded)
        {
            this.standing = standing;
            this.facings = facings;
            this.recorded = recorded;
        }

        /**
         * Nothing ticked where no facing is known; a square holding one facing knows it, the only copy being where the
         * train stands.
         */
        boolean isTicked(org.traincontrol.automationui.TilePorts.Side facing)
        {
            return recorded != null ? facing == recorded : facings.size() == 1;
        }
    }

    /** A square's arrived-from question: the sides offered, the side recorded, one recorded the track has not. */
    static final class ArrivedFacts
    {
        final java.util.List<String> sides;
        final String recorded;
        final String recordedButGone;

        ArrivedFacts(java.util.List<String> sides, String recorded, String recordedButGone)
        {
            this.sides = sides;
            this.recorded = recorded;
            this.recordedButGone = recordedButGone;
        }
    }

    /** A square's tail question: the roads offered, and the one recorded. */
    static final class TailFacts
    {
        final java.util.List<TailCrossedPrompt.Choice> choices;
        final TailCrossedPrompt.Choice ticked;

        TailFacts(java.util.List<TailCrossedPrompt.Choice> choices, TailCrossedPrompt.Choice ticked)
        {
            this.choices = choices;
            this.ticked = ticked;
        }
    }

    EditorTileMenus(AutonomyEditorPanel editor)
    {
        this.editor = editor;
    }

    /**
     * The same menu, built and handed back rather than shown, so the main window's own right-click menu carries the
     * same one and the two places cannot drift.
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

        if (isPoint)
        {
            final boolean isStation = editor.session.getStore().isStation(target);
            final String standing = editor.locomotiveAt(target);

            // Every setting of the square is in its window (FR-111), which a key opens too.
            javax.swing.JMenuItem settings = settingsItem(target);

            if (!editor.menuOnly) settings.setToolTipText(AutonomyEditorPanel.SHORTCUT_SETTINGS);

            menu.add(settings);

            menu.addSeparator();

            // PUTTING a train down is only at a station; TAKING one off is not, as a square can stop being one under it.
            if (!editor.menuOnly)
            {
                javax.swing.JMenuItem train = trainItem(target);

                if (train != null) menu.add(train);

                if (standing != null)
                {
                    menu.add(editor.item(I18n.f("autosetup.ui.menuRemoveLocomotive", standing),
                        () -> editor.removeLocomotive(target)));
                }

                // Whether it runs (FR-117): read off the train and written to it and the setup together; only at rest.
                if (standing != null && (editor.parentWindow() == null || !editor.parentWindow().aRunHoldsTheSetup()))
                {
                    menu.add(editor.toggle(I18n.f("autolayout.ui.menuLocomotiveActive", standing),
                        "autolayout.ui.hintLocomotiveActive",
                        !editor.isPaused(standing, editor.session.getPausedLocomotives()),
                        on -> editor.setActive(standing, on)));
                }

                // Which way round the train is standing, and where its tail lies (OB-039).
                if (isStation)
                {
                    javax.swing.JMenu facingMenu = buildFacingMenu(target);

                    if (facingMenu != null) menu.add(facingMenu);
                }
            }

            menu.addSeparator();
        }

        // Everything about where trains may run, under one heading: each item is a different sentence about the same
        // subject.
        javax.swing.JMenu connections = new javax.swing.JMenu(I18n.t("autosetup.ui.menuConnections"));

        // Headed, because there are four kinds of thing in here once a square has several arms
        editor.title(connections, I18n.t("autosetup.ui.menuDepartHeading"));

        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = editor.session.getRoutes(target);

        // A link has no direction of its own, by decision: the track either side governs which way trains may run.  Its
        // one route is a stub (the same side twice), so offering the four answers would be a setting that silently does
        // nothing.  Pinned by testALinkIsNotOfferedADirection (see TileGraph.PORTAL_ROUTE); one-way cross-page running
        // needs the jump itself to carry a direction (backlog).
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

        // Only when there is something under the heading: everything here sits inside canCarryDirection(target), false for
        // a portal, so a link or tunnel would get a grayed heading alone (OB-032).  A sensor's is in its window.
        if (!isPoint && editor.hasItemsBesidesTitle(connections)) menu.add(connections);

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


        // A station name can go on almost any square, not only a text square: the label is drawn beside the tile.
        // Allowed on curves and dead ends (OB-042, OB-044); refused only on clickable elements (switches, signals),
        // where a caption would put text over a control.  The CLICKED square, not the run leader: a name belongs where
        // it was put.  Editor only: they write text onto the DIAGRAM, and the deep menu is reached by right-clicking
        // the diagram itself.
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

    /** Station Settings..., for a square's menu on either surface. */
    javax.swing.JMenuItem settingsItem(final TileKey target)
    {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(I18n.t("autosetup.ui.menuStationSettings"));

        item.addActionListener(e -> editor.openStationSettings(target));

        return item;
    }

    /** Train at Bramley..., or Place a Locomotive Here... on an empty station; null on an empty square of neither kind. */
    javax.swing.JMenuItem trainItem(final TileKey target)
    {
        final String standing = trainStanding(target);

        if (standing == null && !editor.session.getStore().isStation(target)) return null;

        javax.swing.JMenuItem item = new javax.swing.JMenuItem(standing == null ? I18n.t("autosetup.ui.menuPlaceHere")
            : I18n.f("autosetup.ui.menuTrainAt", editor.describeTile(target)));

        item.addActionListener(e -> editor.openTrainWindow(target));

        return item;
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
     * "Show Station Here..." and its clear, added to whichever menu is being built (shared so the two menus cannot
     * drift).
     *
     * @param label whatever text the square already carries
     * @param captioned the station this square is already showing, or null
     * @param stopOnly only the item that stops showing it, for a square that may not be given a caption but has one
     * (OB-341)
     */
    private void addCaptionItems(javax.swing.JPopupMenu menu, final TileKey tile,
        final LayoutDiagramComponent component, String label, TileKey captioned, boolean stopOnly)
    {
        boolean mine = label.trim().isEmpty() || captioned != null;

        // Both items NAME the station when there is one (FR-014): a caption draws the station's OCCUPANT, and an empty station
        // draws as ---, so a square captioning a station with no train says nothing about which station it
        // is.  Here because both menus build their caption items through this method.
        String showing = captioned == null ? null : editor.describeTile(captioned);

        // The name is NOT repeated here (MT-162): the item below already reads "Stop Showing <name>".  A station square
        // is offered no choice (`promptStationLabel` captions it with itself); the item is grayed only when it would
        // change nothing, because the square already shows itself.
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
     * A bold, disabled line naming what the items under it are about, as the right-click menu heads itself: an untitled
     * set of options leaves the reader to infer the subject.
     */
    private static javax.swing.JMenuItem heading(String text)
    {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(text);

        item.setEnabled(false);
        item.setFont(item.getFont().deriveFont(java.awt.Font.BOLD));

        return item;
    }

    /** What the arrived-from question offers here, or null when no train stands here on the running railway. */
    ArrivedFacts arrivedFacts(final TileKey target)
    {
        if (target == null || editor.locomotiveAt(target) == null) return null;

        org.traincontrol.automation.Layout running =
            editor.runningLayout == null ? null : editor.runningLayout.get();

        if (running == null) return null;

        org.traincontrol.automation.Point point = editor.pointOnTheLayout(running, target);

        if (point == null) return null;

        // A train has to be standing there on the RUNNING railway: the offer above asks the SETUP, and
        // `pointOnTheLayout` falls back to any copy, so a square the setup assigns a train while the running layout has
        // not placed one would write a side onto an unoccupied Point, which `Point.toJSON` declines to save.  While the
        // running layout is held the item is absent rather than grayed; otherwise `setupChanged`'s rebuild catches the
        // placement up.
        if (point.getCurrentLocomotive() == null) return null;

        // THE BUILD'S SIDES, which the model reads as well (OB-182): the entry side travels in the configuration, and
        // `Layout.entrySideOf` is what both the arrival write and the tail walk consult, so offering it here stores a
        // value they can match.
        java.util.List<String> sides = new java.util.ArrayList<>(
            // UNBARRED (OB-204): this records where a train CAME FROM, which a side nothing may arrive by is not.
            org.traincontrol.gui.autonomy.ArrivalSidePrompt.sidesOf(editor.session.unbarredArrivalSides(target)));

        final String recorded = editor.session.getArrivedFrom(target);

        // A recorded side the track does not have is shown, not offered: the radios are ticked by
        // `side.equals(recorded)`, so one not in the list would tick NOTHING and read as "no tail recorded" for a
        // square that has one.  Unlike the facing menu (OB-177) it is not added to the list, as such a side can never
        // match the geometry: it appears once, ticked, DISABLED.  "Not offered here" rather than "not on the track": a
        // split square has several copies' sides.
        final String recordedButGone = recorded != null && !sides.contains(recorded) ? recorded : null;

        if (sides.isEmpty() && recordedButGone == null) return null;

        return new ArrivedFacts(sides, recorded, recordedButGone);
    }

    /**
     * Where the tail of the train standing here lies, the "arrived from" side; the diagram's menu carries this one too
     * (OB-039).
     * @return the menu, or null when no train is standing here
     */
    javax.swing.JMenu buildArrivedFromMenu(final TileKey target)
    {
        ArrivedFacts facts = arrivedFacts(target);

        if (facts == null) return null;

        final java.util.List<String> sides = facts.sides;
        final String recorded = facts.recorded;
        final String recordedButGone = facts.recordedButGone;

        javax.swing.JMenu menu = new javax.swing.JMenu(I18n.t("autosetup.ui.menuArrivedFrom"));

        menu.setToolTipText(AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.hintArrivedFrom")));

        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();

        for (final String side : sides)
        {
            menu.add(editor.radio(group, org.traincontrol.gui.autonomy.ArrivalSidePrompt.labelFor(side),
                "autosetup.ui.hintArrivedFrom",
                side.equals(recorded),
                () -> setArrivedFrom(target, side)));
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

    /** Records the side the train standing here came in by. */
    void setArrivedFrom(TileKey target, String side)
    {
        final String recorded = editor.session.getArrivedFrom(target);

        editor.session.setArrivedFrom(target, side);

        // A road back describes one side: a changed side forgets it, here and on the railway below.
        if (!side.equals(recorded)) editor.session.setArrivedAlong(target, null);

        // SAVED AND SHOWN, like every other door that writes a point property.
        editor.setupChanged();

        // The running layout too: what blocks track is the Point, so a diagram changed over an old railway value is a defect.
        org.traincontrol.automation.Layout now = editor.runningLayout == null ? null : editor.runningLayout.get();

        if (now != null)
        {
            org.traincontrol.automation.Point on = editor.pointOnTheLayout(now, target);

            if (on != null) on.setArrivedFrom(side);

            if (on != null && !side.equals(recorded)) on.setArrivedAlong(null);
        }
    }

    /**
     * The train the railway has standing here, and only then the setup's: after a run the setup names the pre-run occupant
     * (behaviour/editing-while-running.md 6a), so a click would turn neither.  `setFacingAndMove` turns the one named.
     */
    String trainStanding(final TileKey target)
    {
        if (target == null) return null;

        org.traincontrol.automation.Layout showing =
            editor.runningLayout == null ? null : editor.runningLayout.get();

        String onTheRailway = editor.session == null ? null : editor.session.trainOnTheRailway(target, showing);

        return onTheRailway != null ? onTheRailway : editor.locomotiveAt(target);
    }

    /** The one door of a facing chosen by hand, from the menu or the train window. */
    void chooseFacing(final TileKey target, final org.traincontrol.automationui.TilePorts.Side facing)
    {
        editor.session.setFacingAndMove(target, facing);

        editor.setupChanged();
    }

    /** What the facing question offers here, or null unless a locomotive is standing there. */
    FacingFacts facingFacts(final TileKey target)
    {
        final String standing = trainStanding(target);

        if (standing == null) return null;

        org.traincontrol.automation.Layout showing =
            editor.runningLayout == null ? null : editor.runningLayout.get();

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

        // And whatever is actually recorded, even when this square cannot hold it (OB-177): the radios are ticked by
        // `facing == recorded`, so one not among the offered would tick NOTHING, reading as "no facing" for a train
        // that has one (`facingsThatCannotBeHeld` reports it).  It gets an entry rather than a silent correction: which
        // is wrong, the record or the track, is not this menu's to decide.  Not where no track leads out (OB-348).
        if (recorded != null && !facings.isEmpty() && !facings.contains(recorded)) facings.add(recorded);

        return new FacingFacts(standing, facings, recorded);
    }

    /**
     * The "{loc} Is Facing..." submenu, or null unless a locomotive stands here; the diagram's menu carries it too.  Grayed
     * with its reason as tooltip where no track leads out (OB-347).
     * @return the submenu, or null
     */
    javax.swing.JMenu buildFacingMenu(final TileKey target)
    {
        final FacingFacts facts = facingFacts(target);

        if (facts == null) return null;

        final String standing = facts.standing;

        final java.util.List<org.traincontrol.automationui.TilePorts.Side> facings = facts.facings;

        // Shown even with one answer, so the menu explains itself instead of vanishing.  A facing has to be one the
        // BUILD can hold (`placementCopy`, else `startableCopy` quietly turns the train round).  Grayed, not hidden,
        // where no track leads out (OB-347).  ONE SUBMENU (OB-348): built once, here, so the track diagram and the
        // editor cannot offer two that drift apart.
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
                facts.isTicked(facing),
                // The redraw is in radio() itself.  The railway too, not only the setup: `setFacingAndMove` stands the train
                // on the copy that says the new direction, so the caption and the train cannot disagree until the next
                // build.
                () -> chooseFacing(target, facing)));
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

    /** The tail question here, or null where it does not matter (`TailCrossedPrompt.wouldAsk`). */
    TailFacts tailFacts(final TileKey target)
    {
        org.traincontrol.automation.Layout running = editor.runningLayout == null ? null : editor.runningLayout.get();

        if (running == null) return null;

        org.traincontrol.automation.Point point = editor.pointOnTheLayout(running, target);

        if (point == null || point.getCurrentLocomotive() == null) return null;

        String side = point.getArrivedFrom() != null ? point.getArrivedFrom() : editor.session.getArrivedFrom(target);

        Integer length = point.getCurrentLocomotive().getTrainLength();

        if (!TailCrossedPrompt.wouldAsk(running, point, side, length)) return null;

        java.util.List<TailCrossedPrompt.Choice> choices =
            TailCrossedPrompt.choicesFor(running, point, side, length, editor.session::baseNameOf);

        return new TailFacts(choices, TailCrossedPrompt.recordedChoice(choices, point.getArrivedAlong()));
    }

    /**
     * The "farthest sensor the tail crossed" choices, only where `TailCrossedPrompt.wouldAsk` finds two roads back (MT-477).
     */
    private void appendTailCrossed(javax.swing.JMenu menu, final TileKey target)
    {
        TailFacts facts = tailFacts(target);

        if (facts == null) return;

        java.util.List<TailCrossedPrompt.Choice> choices = facts.choices;

        TailCrossedPrompt.Choice ticked = facts.ticked;

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
