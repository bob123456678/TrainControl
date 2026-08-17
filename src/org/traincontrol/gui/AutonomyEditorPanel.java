package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.util.I18n;

/**
 * Setting autonomy up by clicking on the track it runs over.
 *
 * Hand written rather than built in the designer, and mounted into a container the editor already has,
 * so no form file changes and no new window.  It is the first hand-written panel in this project;
 * LayoutGrid is the style it follows.
 *
 * The panel holds the tools and the feedback.  The clicking happens on the diagram itself - a tile is
 * handed here by the editor, and what happens to it depends on which tool is selected.  That is the
 * whole interaction: there is no separate list of things to configure, because the thing being
 * configured is the track that is already on the screen.
 *
 * @author Adam
 */
public class AutonomyEditorPanel extends JPanel
{
    /**
     * What a click on a tile does.
     */
    public static enum Tool
    {
        /**
         * Clicking does nothing; everything is set from the right-click menu.  The ordinary state.
         */
        NONE,

        /**
         * Ask whether a train could get from one sensor to another, and see the route it would take.
         *
         * The only thing here that genuinely needs a mode, because it takes two clicks to say one
         * thing.  Everything else names one tile and belongs on that tile's menu.
         */
        TEST
    }

    private final AutonomySession session;
    private final Runnable onChanged;

    // Which page the editor is showing, so findings can be about the page in front of the user
    private final String page;

    // Called to scroll to and flash a tile, when a finding is clicked
    private java.util.function.Consumer<TileKey> onReveal;

    // Which tile each findings row is about; null for a heading or a finding with no tile
    private final List<TileKey> findingTiles = new java.util.ArrayList<>();

    // What each row is, so it can be coloured the same way the Auto tab colours its own list
    private final List<AutonomyChecks.Severity> findingSeverity = new java.util.ArrayList<>();

    private Tool tool = Tool.NONE;

    /**
     * How wide the panel is allowed to get.  The messages here are sentences rather than labels, and a
     * plain JLabel asks for however wide its text is - which stretched the editor's palette column
     * across the window the first time a long one appeared.
     */
    private static final int WIDTH = 280;

    // The EDITOR window's own conventions, which are not quite the main window's: its headings are
    // Semibold 13 in rgb(0,0,155) (jLabel1 "New Components", jLabel2 "Toggle Visibility") and its
    // buttons are bold 11 (saveButton, cancelButton), a size down from the main window's 12.
    static final java.awt.Font FONT_HEADING =
        new java.awt.Font("Segoe UI Semibold", java.awt.Font.PLAIN, 13);

    static final java.awt.Font FONT_BUTTON = new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11);

    static final java.awt.Font FONT_CONTROL = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14);

    static final java.awt.Color HEADING_COLOUR = new java.awt.Color(0, 0, 155);

    /**
     * The findings list and the sentences beside it, one size down: they are dense reading rather than
     * labels, and at 14 a real layout's list does not fit the column.
     */
    static final java.awt.Font FONT_HINT = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12);

    private static <T extends javax.swing.JComponent> T control(T component)
    {
        component.setFont(FONT_CONTROL);
        return component;
    }

    private static <T extends javax.swing.AbstractButton> T button(T component)
    {
        component.setFont(FONT_BUTTON);
        component.setFocusable(false);
        return component;
    }

    private final JLabel banner = new JLabel();
    private final JLabel hint = new JLabel();
    private final DefaultListModel<String> findingsModel = new DefaultListModel<>();
    private final JList<String> findings = new JList<>(findingsModel);

    private final JCheckBox showDirections = new JCheckBox(I18n.t("autosetup.ui.btnShowDirections"), true);
    private final JCheckBox showAllDirections =
        new JCheckBox(I18n.t("autosetup.ui.btnShowAllDirections"), false);
    private final JCheckBox showLengths = new JCheckBox(I18n.t("autosetup.ui.btnShowLengths"), false);

    // Portal pairing takes two clicks, and the first is remembered here
    private TileKey pendingPortal;

    // Bulk selection, so a one-way run is set in one gesture rather than forty
    private final Set<TileKey> selection = new LinkedHashSet<>();

    // The path test also takes two clicks; the first end and the last found route live here
    /**
     * Run after this window has written to a track diagram page, to rebuild the grid it is drawn on.
     */
    private Runnable onDiagramChanged;

    private TileKey testFrom;

    /**
     * Held so that a gesture can be called off from somewhere other than the button itself - a tool
     * left pressed while nothing is waiting for a click is a control lying about what it is doing.
     */
    private JToggleButton testButton;
    private final Set<TileKey> testPath = new LinkedHashSet<>();

    // A one-way run started from the right-click menu, waiting for its far end
    private TileKey oneWayFrom;

    // Where the locomotive roster comes from.  Supplied rather than read here, because the session is
    // headless and knows nothing about the control station.
    private java.util.function.Supplier<List<String>> locomotiveNames;

    // One arrow per run of track between sensors.  Recomputed on refresh rather than per tile, because
    // it is derived from the whole reduction and the editor asks about every square in turn.
    private Map<TileKey, org.traincontrol.automationui.TileAnnotation.Mark> flowMarks =
        new java.util.LinkedHashMap<>();

    // Which tile speaks for each run of plain track.  A run has one direction, so only its first tile
    // is set; the rest follow and are drawn greyed so nobody tries to set them separately.
    private Map<TileKey, TileKey> runLeaders = new java.util.LinkedHashMap<>();

    /**
     * @param session the setup being edited
     * @param page the diagram page this editor window is showing
     * @param onChanged run after every edit, so the diagram can redraw itself
     */
    public AutonomyEditorPanel(AutonomySession session, String page, Runnable onChanged)
    {
        this.session = session;
        this.page = page;
        this.onChanged = onChanged;

        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        add(buildTools(), BorderLayout.NORTH);
        add(buildFindings(), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);

        // Pinned, so a long sentence in the hint or a finding cannot widen the column it lives in.
        setPreferredSize(new Dimension(WIDTH, 640));
        setMinimumSize(new Dimension(WIDTH, 240));
        setMaximumSize(new Dimension(WIDTH, Integer.MAX_VALUE));

        // Nothing in this column is worked by keyboard, and a control that takes focus swallows the
        // key presses the window around it uses.  button() already does this one control at a time;
        // the sweep covers the lists and anything added later that forgets.
        AutonomyViewerPanel.unfocusable(this);

        refresh();
    }

    /**
     * Wraps a sentence to the panel's width instead of demanding a column as wide as the sentence.
     */
    private void say(JLabel label, String text)
    {
        // Straight to the banner across the top of the window, which has the width a sentence needs.
        // The label stays as the fallback for a panel mounted without one, and is wrapped so that it
        // cannot drag its column out of shape the way it used to.
        if (messageBanner != null && label == hint)
        {
            messageBanner.show(text);
            return;
        }

        label.setText("<html><body style='width:" + (WIDTH - 30) + "px'>"
            + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
    }

    /**
     * Says something that has to stay until the user acts on it - a prompt for a second click, which a
     * message that faded after six seconds would leave them stranded halfway through.
     */
    private void waitFor(String text)
    {
        if (messageBanner != null) messageBanner.showUntilChanged(text);
        else say(hint, text);
    }

    /**
     * @param banner the strip across the top of the editor, where messages go
     */
    public void setBanner(AutonomyBanner messageBanner)
    {
        this.messageBanner = messageBanner;
    }

    // The strip across the top of the window.  Named apart from the status banner in the tools column,
    // which is a different thing: that one shows what the setup IS, this one what just happened.
    private AutonomyBanner messageBanner;

    /**
     * The same, for a message that carries its own mark-up.  Only for text this class builds itself -
     * never for anything a user typed, which is what say() escapes.
     */
    private void sayRich(JLabel label, String html)
    {
        if (messageBanner != null && label == hint)
        {
            messageBanner.show("<html>" + html + "</html>");
            return;
        }

        label.setText("<html><body style='width:" + (WIDTH - 30) + "px'>" + html + "</body></html>");
    }

    /**
     * The tools column.
     *
     * A vertical BoxLayout rather than a GridLayout, because GridLayout gives EVERY row the height of
     * the tallest one - so the moment the hint wrapped to three lines, every checkbox and button grew
     * to three lines too and the column came apart.  Here each row takes the height it needs.
     */
    private JPanel buildTools()
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        // No heading of its own: the editor already prints one above this column, and setAutonomyMode
        // retitles it, so a second said the same thing twice.
        //
        // One tool.  Connections, points, links and lengths were all buttons that only changed what a
        // click MEANT, and every one of them acted on a single named tile - which is what a right-click
        // menu is for.  They are all on the tile's own menu now, where the thing being configured is
        // the thing under the pointer.
        testButton = toolButton(Tool.TEST, I18n.t("autosetup.ui.toolTest"));
        panel.add(row(testButton));

        // the toggles change what is drawn, not what is decided, so all they do is redraw
        showDirections.addActionListener(e -> refresh());
        showAllDirections.addActionListener(e -> refresh());
        showLengths.addActionListener(e -> refresh());

        panel.add(row(control(showDirections)));
        panel.add(row(control(showAllDirections)));

        hint.setFont(FONT_HINT);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(hint);

        banner.setOpaque(true);
        banner.setFont(FONT_HEADING);
        banner.setAlignmentX(LEFT_ALIGNMENT);
        banner.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        panel.add(banner);

        return panel;
    }

    /**
     * One control on its own line, flush left and no taller than it needs to be.
     */
    private JPanel row(java.awt.Component component)
    {
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 1));
        holder.setOpaque(false);
        holder.setAlignmentX(LEFT_ALIGNMENT);
        holder.setMaximumSize(new Dimension(WIDTH, 30));
        holder.add(component);

        return holder;
    }

    /**
     * Drops any multi-click gesture that is part way through, and un-presses the tool that started it.
     *
     * Silent: it runs on the way into the right-click menu, where a message about what was abandoned
     * would be replaced by the menu's own work a moment later anyway.
     */
    private void cancelPendingGesture()
    {
        boolean pending = tool != Tool.NONE || testFrom != null || oneWayFrom != null
            || pendingPortal != null;

        if (!pending) return;

        tool = Tool.NONE;
        testFrom = null;
        oneWayFrom = null;
        pendingPortal = null;
        testPath.clear();

        if (testButton != null) testButton.setSelected(false);

        say(hint, I18n.t("autosetup.ui.hintClickToCycle"));

        refresh();
    }

    /**
     * The one tool.  Deliberately NOT in a ButtonGroup: with a single toggle, a group would make it
     * impossible to switch back off again.
     */
    private JToggleButton toolButton(final Tool which, String text)
    {
        final JToggleButton button = new JToggleButton(text, false);

        button(button);

        button.addActionListener(e ->
        {
            tool = button.isSelected() ? which : Tool.NONE;
            pendingPortal = null;
            selection.clear();
            testFrom = null;
            testPath.clear();

            // A one-way run waiting for its far end survived this, so the next click anywhere was
            // swallowed by a gesture the user had already moved on from.
            oneWayFrom = null;

            say(hint, tool == Tool.TEST
                ? I18n.t("autosetup.ui.promptTestStart") : I18n.t("autosetup.ui.hintClickToCycle"));

            refresh();
        });

        return button;
    }

    private JScrollPane buildFindings()
    {
        findings.setVisibleRowCount(8);
        findings.setFont(FONT_HINT);

        // Same colours as the Auto tab's list: red for what must be fixed, amber for what is worth
        // checking, grey for the headings.  The two views describe the same setup.
        findings.setCellRenderer(new javax.swing.DefaultListCellRenderer()
        {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                AutonomyChecks.Severity severity =
                    index < findingSeverity.size() ? findingSeverity.get(index) : null;

                boolean heading = index < findingTiles.size()
                    && findingTiles.get(index) == null && severity == null;

                setFont(heading ? AutonomyViewerPanel.FONT_BOLD : FONT_HINT);

                if (!isSelected)
                {
                    setForeground(heading ? AutonomyViewerPanel.SUBHEADING_COLOUR
                        : severity == AutonomyChecks.Severity.ERROR
                            ? AutonomyViewerPanel.ERROR_COLOUR
                        : severity == AutonomyChecks.Severity.WARNING
                            ? AutonomyViewerPanel.WARNING_COLOUR
                        : java.awt.Color.DARK_GRAY);
                }

                return this;
            }
        });

        // Clicking a finding goes to the tile it is about.  Reading "no train can leave Platform 3" is
        // only half an answer; the other half is which square that is, on a page of two hundred.
        findings.addListSelectionListener(e ->
        {
            if (e.getValueIsAdjusting()) return;

            int row = findings.getSelectedIndex();

            if (row >= 0 && row < findingTiles.size() && findingTiles.get(row) != null)
            {
                if (onReveal != null) onReveal.accept(findingTiles.get(row));
            }
        });

        JScrollPane scroll = new JScrollPane(findings);
        scroll.setBorder(BorderFactory.createTitledBorder(I18n.t("autosetup.ui.colWarnings")));
        scroll.setPreferredSize(new Dimension(WIDTH - 20, 160));

        return scroll;
    }

    private JPanel buildActions()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        JButton check = new JButton(I18n.t("autosetup.ui.btnCheckConfiguration"));
        check.addActionListener(e -> recheck());
        panel.add(button(check));

        JButton nameAll = new JButton(I18n.t("autosetup.ui.btnNameEverything"));
        nameAll.addActionListener(e -> nameEverything());
        panel.add(button(nameAll));

        JButton save = new JButton(I18n.t("autosetup.ui.btnApply"));
        save.addActionListener(e -> save());
        panel.add(button(save));

        return panel;
    }

    /**
     * Re-runs the checks and says so, so that pressing it twice does not look like a button that does
     * nothing the second time.
     */
    private void recheck()
    {
        refresh();

        say(hint, findingsModel.isEmpty()
            ? I18n.t("autosetup.ui.labelCheckedClean")
            : I18n.f("autosetup.ui.labelCheckedNow", findingsModel.size()));
    }

    // --- what a click does ------------------------------------------------------------------------

    /**
     * A tile on the diagram was clicked while autonomy mode is on.
     *
     * @param tile which square
     * @param component what is drawn there
     * @param addToSelection whether the click was a shift-click, which adds to a bulk selection instead
     *        of acting immediately
     */
    /**
     * Everything that can be set on a tile, on one menu.
     *
     * Built the way LayoutEditorRightclickMenu builds its own - a bold disabled title, plain items in
     * the look-and-feel's own font, separators between groups - so that right-clicking in autonomy mode
     * looks like right-clicking anywhere else in this window.
     *
     * The point settings are items rather than a dialog, and in the order the graph window's point menu
     * used them, reusing its wording key for key.  A dialog had to lay out nine unrelated controls and
     * did it badly; a menu of checkboxes says the same things in the place the user already looks.
     *
     * @param tile which square
     * @param component what is drawn there
     * @param invoker the component to show the menu over
     * @param x where on it
     * @param y
     */
    public void tileRightClicked(TileKey tile, LayoutDiagramComponent component,
        java.awt.Component invoker, int x, int y)
    {
        if (tile == null || session.getGraph() == null) return;

        // Right-clicking abandons whatever gesture was in progress.  Opening a menu is how somebody
        // says "not that, this instead", and a half-finished path test that stayed armed underneath it
        // would swallow the next ordinary click on the far side of the menu closing.
        cancelPendingGesture();

        // A text square carries no track, so everything below would be a no-op on it - but it is the
        // one thing on the diagram a station can be WRITTEN on, and being sent to a different editor to
        // do that is the round trip this surface exists to remove.
        LayoutDiagramComponent onPage = componentAt(tile);

        // The page has to be one the session knows, or "no component here" would mean "I could not
        // find the page" and every square on it would offer to become a station label.
        if (pageOf(tile) != null && (onPage == null || onPage.isText()))
        {
            showTextMenu(tile, onPage, invoker, x, y);
            return;
        }

        // Nothing on an ignored square is the user's to set, so it says so rather than offering a menu
        // whose every item would be a no-op.
        if (isIgnored(tile))
        {
            say(hint, I18n.t("autosetup.ui.infoTileIgnored"));
            return;
        }

        // Right-clicking anywhere in a run opens the run's own menu, so the greyed tiles are not dead
        // - they simply hand the question to the tile that answers it.  A new local rather than
        // reassigning the parameter, which the lambdas below capture and so must stay effectively final.
        final TileKey target = leaderOf(tile);

        // Remembered so every item on this menu can flash what it changed without being told twice.
        menuTarget = target;

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        boolean isPoint = session.getReducer() != null
            && session.getReducer().getPoints().containsKey(target);

        title(menu, isPoint ? pointTitle(target) : component == null
            ? target.getX() + "," + target.getY() : component.getUserFriendlyTypeName());

        if (isPoint)
        {
            // Locomotives first, because placing one is the commonest reason to open this menu once a
            // layout is set up - the designations below it are settled early and rarely touched again.
            //
            // Only at a station: a locomotive can only be SENT to a station, so standing one anywhere
            // else records a position autonomy could never route away from.
            if (session.getStore().isStation(target))
            {
                String standing = locomotiveAt(target);

                if (standing != null)
                {
                    menu.add(item(I18n.f("autosetup.ui.menuRemoveLocomotive", standing),
                        () -> session.placeLocomotive(target, null)));
                }

                menu.add(item(I18n.t("autosetup.ui.menuAddToAutonomy"),
                    () -> placeLocomotive(target, true)));

                menu.add(item(I18n.t("autosetup.ui.menuAddToStation"),
                    () -> placeLocomotive(target, false)));

                menu.addSeparator();
            }

            menu.add(item(I18n.t("autosetup.ui.menuRename"), () -> promptName(target)));

            menu.addSeparator();

            // Three switches, and only three: is this a station, may trains turn round here, and - if
            // it is a station - is it a berth trains are only ever sent to on purpose.  Terminus and
            // reversing are gone from the UI entirely; they are what these get COMPILED to, and having
            // both vocabularies on one menu let a square be told two contradictory things.
            final boolean isStation = session.getStore().isStation(target);
            final boolean isParking = session.isParking(target);

            javax.swing.JMenu stationMenu = new javax.swing.JMenu(
                I18n.f("autosetup.ui.menuStationGroup", stationSummary(target, isStation)));

            stationMenu.add(toggle(I18n.t("autosetup.ui.menuStation"),
                "autolayout.ui.tooltip.Station", isStation, on -> setStation(target, on)));

            stationMenu.addSeparator();

            // Parking compiles to a reversing station: autonomy never chooses one and cannot route a
            // train through it, but a route picked by hand still reaches it, and so does Return Home.
            javax.swing.JCheckBoxMenuItem parking = toggle(I18n.t("autosetup.ui.menuParking"),
                "autosetup.ui.hintParking", isParking,
                on -> session.setPointFlag(target, AutonomyBuilder.PARKING, on));

            // Greyed rather than hidden, so the shape of the choice stays visible: somebody looking for
            // it finds it, sees it is unavailable, and can tell why from the item above.
            parking.setEnabled(isStation);

            stationMenu.add(parking);

            menu.add(stationMenu);

            // Turning round, on any square.  Disabled while parking is on because a berth turns its
            // trains round by definition - and because asking for both would emit a terminus AND a
            // reversing flag on one Point, which the model refuses in either order, failing the whole
            // configuration rather than this one square.
            javax.swing.JCheckBoxMenuItem turnAround = toggle(
                I18n.t("autosetup.ui.menuCanReverse"),
                isParking ? "autosetup.ui.hintParkingTurnsRound" : "autosetup.ui.hintCanReverse",
                isParking || session.isTurnAround(target),
                on -> session.setPointFlag(target, AutonomyBuilder.CAN_REVERSE, on));

            turnAround.setEnabled(!isParking);

            menu.add(turnAround);

            // Active is its own switch on every point, as the graph menu had it.  It is the only way to
            // say "not this session" without also changing what a train does on arrival - which is what
            // parking would do instead, and they are not the same statement.
            menu.add(toggle(I18n.t("autolayout.ui.checkboxActive"),
                "autolayout.ui.tooltip.Active",
                !Boolean.FALSE.equals(session.getPointProperty(target, "active")),
                on -> session.setPointProperty(target, "active", on ? null : Boolean.FALSE)));

            menu.addSeparator();

            // Every label carries its current value, as the graph window's did - a menu that says
            // "Speed multiplier" and nothing else makes the user open it to find out what it is.
            menu.add(item(I18n.f("autolayout.ui.menuSpeedMultiplier", percent(target)),
                () -> promptPercent(target)));

            javax.swing.JMenu advanced = new javax.swing.JMenu(
                I18n.t("autolayout.ui.menuEditAdvancedParameters"));

            int length = number(target, "maxTrainLength", 0);

            advanced.add(item(I18n.f("autolayout.ui.menuMaxTrainLength",
                length == 0 ? I18n.t("autolayout.ui.any") : String.valueOf(length)),
                () -> promptNumber(target, "maxTrainLength",
                    "autolayout.ui.promptEnterMaxTrainLength", 0)));

            int priority = number(target, "priority", 0);

            advanced.add(item(I18n.f("autolayout.ui.menuStationPriority",
                priority == 0 ? I18n.t("autolayout.ui.default") : String.valueOf(priority)),
                () -> promptNumber(target, "priority",
                    "autolayout.ui.promptEnterStationPriority", 0)));

            menu.add(advanced);

            menu.add(item(I18n.f("autolayout.ui.menuExcludedLocomotives",
                strings(target, "excludedLocs").size()),
                () -> promptLocomotives(target, "excludedLocs", allLocomotives())));

            String home = homeOf(target);

            menu.add(item(home == null ? I18n.t("autosetup.ui.menuHomeNone")
                                       : I18n.f("autosetup.ui.menuHomeFor", home),
                () -> promptHome(target)));

            menu.addSeparator();
        }

        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(target);

        if (!routes.isEmpty())
        {
            boolean many = routes.size() > 1;

            for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry : routes.entrySet())
            {
                org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

                // A switch's branches each get their own submenu; a plain target puts its four answers
                // straight on the menu rather than burying them one level down.
                if (many)
                {
                    // no font set: submenus inherit the look and feel's, as the editor's own do
                    javax.swing.JMenu branch = new javax.swing.JMenu(
                        I18n.f("autosetup.ui.menuBranch",
                            String.valueOf(route.getA()), String.valueOf(route.getB())));

                    for (javax.swing.JMenuItem option : directionItems(target, entry.getKey(), route))
                    {
                        branch.add(option);
                    }

                    menu.add(branch);
                }
                else
                {
                    for (javax.swing.JMenuItem option : directionItems(target, entry.getKey(), route))
                    {
                        menu.add(option);
                    }
                }
            }

            if (many)
            {
                javax.swing.JMenu all = new javax.swing.JMenu(
                    I18n.t("autosetup.ui.menuAllBranches"));

                all.add(item(I18n.t("autosetup.ui.menuRouteBoth"),
                    () -> setAllBranches(target, Direction.BOTH)));
                all.add(item(I18n.t("autosetup.ui.menuRouteNone"),
                    () -> setAllBranches(target, Direction.NONE)));

                menu.add(all);
            }

            menu.addSeparator();
        }

        menu.add(item(I18n.t("autosetup.ui.menuOneWayRun"), () ->
        {
            oneWayFrom = target;
            waitFor(I18n.t("autosetup.ui.promptOneWayTo"));
        }));

        menu.add(item(I18n.t("autosetup.ui.menuSetLength"), () -> applyLength(target)));

        if (component != null && (component.isLink()
            || component.getType() == LayoutDiagramComponent.componentType.TUNNEL))
        {
            menu.addSeparator();

            menu.add(item(I18n.t("autosetup.ui.menuSetName"), () -> promptLinkName(target)));
            menu.add(item(I18n.t("autosetup.ui.menuPairLink"), () -> pairFromList(target)));

            if (session.getStore().getPortalPartner(target) != null)
            {
                menu.add(item(I18n.t("autosetup.ui.menuUnpairLink"),
                    () -> session.unpairPortal(target)));
            }
        }

        // A station name can go on any square whose track runs straight through, not only on a text
        // square: a straight, a sensor, a signal, an uncoupler.  The label is drawn beside the tile
        // wherever it sits, so there is no reason to make the user find a text square first - and on a
        // platform the sensible place for the name is the platform road itself.
        //
        // The CLICKED square, not the run leader the rest of this menu acts on: a name belongs where
        // it was put, and moving it to the head of the run would drop it somewhere else entirely.
        if (isStraightThrough(tile))
        {
            menu.addSeparator();

            final LayoutDiagramComponent here = componentAt(tile);

            menu.add(item(I18n.t("autosetup.ui.menuShowStationHere"),
                () -> promptStationLabel(tile, here)));

            if (here != null && here.getLabel() != null
                    && here.getLabel().startsWith(AutonomySession.STATION_LABEL_PREFIX))
            {
                menu.add(item(I18n.t("autosetup.ui.menuClearStationHere"),
                    () -> applyStationLabel(tile, null)));
            }
        }

        menu.show(invoker, x, y);
    }

    /**
     * Whether the track on this square runs straight through it.
     *
     * One route, joining two OPPOSITE sides.  That is exactly the set the author named - straights,
     * straight sensors, signals, uncouplers - without listing types that would have to be kept in step
     * with the port map every time one was added.  Curves, switches, crossings and dead ends all fail
     * it, and none of them has room beside the track for a name anyway.
     */
    private boolean isStraightThrough(TileKey tile)
    {
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(tile);

        if (routes.size() != 1) return false;

        org.traincontrol.automationui.TilePorts.Route route = routes.values().iterator().next();

        if (route.getA() == null || route.getB() == null || route.getA() == route.getB()) return false;

        // N,E,S,W in order, so opposite sides are two apart either way round
        return Math.abs(route.getA().ordinal() - route.getB().ordinal()) == 2;
    }

    /**
     * Flashes the square the open menu belongs to, so a menu edit is as visible as a click.
     */
    private void flashMenuTarget()
    {
        if (menuTarget != null && onReveal != null) onReveal.accept(menuTarget);
    }

    // Which square the open right-click menu is acting on
    private TileKey menuTarget;

    /**
     * What the menu calls this point: its name where it has one, and what it is where it does not.
     */
    private String pointTitle(TileKey tile)
    {
        String name = session.getStore().getPointName(tile);

        return name == null || name.trim().isEmpty() ? describeTile(tile) : name.trim();
    }

    /**
     * A bold, disabled heading at the top of a menu - the editor's own idiom.
     */
    private void title(javax.swing.JPopupMenu menu, String text)
    {
        javax.swing.JMenuItem titleItem = new javax.swing.JMenuItem(text);
        titleItem.setEnabled(false);
        titleItem.setFont(titleItem.getFont().deriveFont(java.awt.Font.BOLD));

        menu.add(titleItem);
        menu.addSeparator();
    }

    /**
     * A menu item that runs something and then redraws.  No font is set: the editor's own menus use the
     * look and feel's, and one that did not would be the only odd menu in the window.
     */
    /**
     * A menu item that runs something, redraws, and flashes the square it changed.
     *
     * The flash matters more here than on a click: the menu covers the tile while it is open, and on a
     * run the square that changes is the head of the run rather than the one right-clicked - so
     * without it the one square that moved was the one the user could not see.
     */
    private javax.swing.JMenuItem item(String text, final Runnable action)
    {
        javax.swing.JMenuItem menuItem = new javax.swing.JMenuItem(text);

        menuItem.addActionListener(e ->
        {
            try
            {
                action.run();
            }
            catch (RuntimeException ex)
            {
                JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()));
            }

            refresh();

            flashMenuTarget();
        });

        return menuItem;
    }

    private javax.swing.JCheckBoxMenuItem toggle(String text, boolean on,
        final java.util.function.Consumer<Boolean> action)
    {
        return toggle(text, null, on, action);
    }

    /**
     * @param tooltipKey the graph window's own tooltip for this setting, so the explanation travels
     *        with the option rather than being lost when the dialog that carried it went
     */
    private javax.swing.JCheckBoxMenuItem toggle(String text, String tooltipKey, boolean on,
        final java.util.function.Consumer<Boolean> action)
    {
        final javax.swing.JCheckBoxMenuItem menuItem = new javax.swing.JCheckBoxMenuItem(text, on);

        if (tooltipKey != null) menuItem.setToolTipText(I18n.t(tooltipKey));

        menuItem.addActionListener(e ->
        {
            action.accept(menuItem.isSelected());
            refresh();

            flashMenuTarget();
        });

        return menuItem;
    }

    /**
     * The menu for a text square: which station, if any, it is showing.
     */
    private void showTextMenu(final TileKey tile, final LayoutDiagramComponent component,
        java.awt.Component invoker, int x, int y)
    {
        menuTarget = tile;

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        String label = component == null || component.getLabel() == null ? "" : component.getLabel();
        boolean carries = label.startsWith(AutonomySession.STATION_LABEL_PREFIX);

        title(menu, carries ? label.substring(AutonomySession.STATION_LABEL_PREFIX.length())
            : label.trim().isEmpty() ? I18n.t("autosetup.ui.titleEmptyText") : label);

        // Offered on a blank square too, not only on one that already carries text: writing a station
        // name on a blank square is how a diagram with no text squares at all gets its first one.
        menu.add(item(I18n.t("autosetup.ui.menuShowStationHere"), () -> promptStationLabel(tile, component)));

        if (carries)
        {
            menu.add(item(I18n.t("autosetup.ui.menuClearStationHere"), () -> applyStationLabel(tile, null)));
        }

        menu.show(invoker, x, y);
    }

    /**
     * Asks which station this square should show.
     */
    private void promptStationLabel(TileKey tile, LayoutDiagramComponent component)
    {
        java.util.List<String> names = new java.util.ArrayList<>();

        if (session.getReducer() != null)
        {
            for (org.traincontrol.automationui.GraphReducer.ReducedPoint point
                : session.getReducer().getPoints().values())
            {
                if (point.isStation() && point.getName() != null) names.add(point.getName());
            }
        }

        java.util.Collections.sort(names);

        if (names.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.errorNoStationsToLabel"));
            return;
        }

        javax.swing.JComboBox<String> choice =
            new javax.swing.JComboBox<>(names.toArray(new String[0]));

        String label = component == null || component.getLabel() == null ? "" : component.getLabel();

        if (label.startsWith(AutonomySession.STATION_LABEL_PREFIX))
        {
            choice.setSelectedItem(label.substring(AutonomySession.STATION_LABEL_PREFIX.length()));
        }

        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 6));
        panel.add(new JLabel(I18n.t("autosetup.ui.promptStationLabel")), java.awt.BorderLayout.NORTH);
        panel.add(choice, java.awt.BorderLayout.CENTER);

        // Naming what is about to be lost.  A text square can be carrying an ordinary caption - a yard
        // name, a note - and replacing it silently with a station label destroys something the user
        // wrote, on a diagram this editor otherwise never touches.
        if (!label.trim().isEmpty() && !label.startsWith(AutonomySession.STATION_LABEL_PREFIX))
        {
            panel.add(new JLabel(I18n.f("autosetup.ui.warnReplacingText", label)),
                java.awt.BorderLayout.SOUTH);
        }

        if (JOptionPane.showConfirmDialog(this, panel, I18n.t("autosetup.ui.titleStationLabel"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        applyStationLabel(tile, (String) choice.getSelectedItem());
    }

    /**
     * Writes the label and says so.
     *
     * The page goes to disk here and now.  Save in this window means the autonomy setup, so a diagram
     * change carried until then would be written by a button that says it is doing something else -
     * and abandoned by a Cancel that the user reasonably thought applied only to autonomy.
     */
    private void applyStationLabel(TileKey tile, String name)
    {
        try
        {
            session.setStationLabel(tile, name);

            // The editor's own grid has to be REBUILT, not repainted: the caption is part of the tile
            // art, and the annotation refresh that follows every other edit does not touch it.  The
            // main window needs no telling - closing this editor runs an uncached repaint, and it is
            // the grid build that registers a station label.
            if (onDiagramChanged != null) onDiagramChanged.run();

            say(hint, name == null ? I18n.t("autosetup.ui.clearedStationLabel")
                : I18n.f("autosetup.ui.setStationLabel", name));

            refresh();

            flashMenuTarget();
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(this, I18n.f("error.generic", String.valueOf(e.getMessage())));
        }
    }

    /**
     * What the Station heading says about a sensor without being opened.
     */
    private String stationSummary(TileKey tile, boolean isStation)
    {
        if (!isStation) return I18n.t("autosetup.ui.stationNo");

        if (session.isParking(tile)) return I18n.t("autosetup.ui.stationParking");

        return I18n.t(session.isTurnAround(tile)
            ? "autosetup.ui.stationTerminus" : "autosetup.ui.stationYes");
    }

    private void setStation(TileKey tile, boolean on)
    {
        session.setStation(tile, on);

        // A sensor demoted back to a plain point keeps no designation nobody can see any more.
        //
        // Active is NOT cleared with it.  It applies to any point, station or not - the graph menu
        // offered it on all of them - so clearing it here would silently re-enable a point somebody had
        // switched off, on a gesture that says nothing about that.
        if (!on)
        {
            session.setPointProperty(tile, "terminus", null);
        }
    }

    private void setAllBranches(TileKey tile, Direction direction)
    {
        session.setDirection(new LinkedHashSet<>(java.util.Arrays.asList(tile)), direction);
    }

    /**
     * Asks for a number, storing nothing when it is left at the value that means "no setting".
     */
    private void promptNumber(TileKey tile, String key, String promptKey, int unset)
    {
        Object current = session.getPointProperty(tile, key);

        // I18n.f, not t: two of these prompts name the point they are about, and I18n.t does no
        // substitution at all - so the user was asked to "Enter the priority for {0}".
        String entered = JOptionPane.showInputDialog(this,
            I18n.f(promptKey, describeTile(tile)),
            current instanceof Number ? String.valueOf(current) : String.valueOf(unset));

        if (entered == null) return;

        try
        {
            int value = Integer.parseInt(entered.trim());

            session.setPointProperty(tile, key, value == unset ? null : value);
        }
        catch (NumberFormatException e)
        {
            JOptionPane.showMessageDialog(this, I18n.t("autosetup.ui.errorNegativeLength"));
        }
    }


    private Set<String> strings(TileKey tile, String key)
    {
        Set<String> out = new LinkedHashSet<>();

        Object value = session.getPointProperty(tile, key);

        if (value instanceof org.json.JSONArray)
        {
            for (Object o : (org.json.JSONArray) value) out.add(String.valueOf(o));
        }
        else if (value != null)
        {
            out.add(String.valueOf(value));
        }

        return out;
    }
    /**
     * A stored number for a point, or the value that means "not set".
     */
    private int number(TileKey tile, String key, int fallback)
    {
        Object value = session.getPointProperty(tile, key);

        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    /**
     * The speed multiplier as a PERCENTAGE, which is what the user works in.
     *
     * The model stores a factor and refuses anything outside (0, 2] - so writing the percentage
     * straight through made any real value, 80 or 120, invalidate the whole configuration on load.
     */
    private int percent(TileKey tile)
    {
        Object stored = session.getPointProperty(tile, "speedMultiplier");

        return stored instanceof Number
            ? (int) Math.round(((Number) stored).doubleValue() * 100) : 100;
    }

    private void promptPercent(TileKey tile)
    {
        String entered = JOptionPane.showInputDialog(this,
            I18n.f("autolayout.ui.promptEnterSpeedMultiplier", describeTile(tile)),
            String.valueOf(percent(tile)));

        if (entered == null) return;

        try
        {
            int value = Integer.parseInt(entered.trim());

            if (value <= 0 || value > 200)
            {
                JOptionPane.showMessageDialog(this,
                    I18n.t("autolayout.ui.errorInvalidSpeedMultiplier"));
                return;
            }

            // 100% is the default and is stored as nothing, so a file records decisions only
            session.setPointProperty(tile, "speedMultiplier",
                value == 100 ? null : value / 100.0);
        }
        catch (NumberFormatException e)
        {
            JOptionPane.showMessageDialog(this, I18n.t("autolayout.ui.errorInvalidSpeedMultiplier"));
        }
    }

    /**
     * Which locomotive calls this point home, or null.
     *
     * ONE locomotive: Point.homeLoc is a single String, and the model allows a locomotive only one
     * home.  A multi-select wrote a JSON array here, which parseAuto read with optString and turned
     * into the literal text ["BR 111"] - matching no locomotive, and then captured back in that form
     * so the damage outlived the code that caused it.
     */
    private String homeOf(TileKey tile)
    {
        Object stored = session.getPointProperty(tile, "home");

        return stored instanceof String && !((String) stored).trim().isEmpty()
            ? (String) stored : null;
    }

    private void promptHome(TileKey tile)
    {
        List<String> names = new java.util.ArrayList<>();
        names.add(I18n.t("autosetup.ui.labelNone"));
        names.addAll(placedLocomotives());

        if (names.size() == 1)
        {
            JOptionPane.showMessageDialog(this, I18n.t("error.noLocs"));
            return;
        }

        String current = homeOf(tile);

        Object chosen = JOptionPane.showInputDialog(this,
            I18n.t("autosetup.ui.promptHomeFor"), I18n.t("autosetup.ui.menuHomeNone"),
            JOptionPane.PLAIN_MESSAGE, null, names.toArray(),
            current == null ? names.get(0) : current);

        if (chosen == null) return;

        session.setPointProperty(tile, "home",
            names.get(0).equals(chosen) ? null : String.valueOf(chosen));
    }

    /**
     * Every locomotive the control station knows about.
     */    /**
     * Every locomotive the control station knows about.
     */
    private List<String> allLocomotives()
    {
        return locomotiveNames == null
            ? java.util.Collections.<String>emptyList() : locomotiveNames.get();
    }

    /**
     * Only the locomotives placed somewhere on this layout.
     *
     * A home is where a particular engine belongs, so offering the whole roster invites choosing one
     * that autonomy has never heard of - the setting would be stored and quietly do nothing.
     */
    private List<String> placedLocomotives()
    {
        List<String> out = new java.util.ArrayList<>();

        if (session.getReducer() == null) return out;

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            String at = locomotiveAt(tile);

            if (at != null && !out.contains(at)) out.add(at);
        }

        return out;
    }

    /**
     * Which locomotive is standing on a point, or null.
     */
    private String locomotiveAt(TileKey tile)
    {
        Object placed = session.getPointProperty(tile, "loc");

        if (!(placed instanceof org.json.JSONObject)) return null;

        org.json.JSONObject loc = (org.json.JSONObject) placed;

        return loc.has("name") ? loc.getString("name") : null;
    }

    /**
     * Puts a locomotive on a point.
     *
     * One at a time, and only where it is not already: a locomotive standing in two places at once is
     * a state the running layout cannot represent, so it is removed from wherever it was first.
     */
    private void placeLocomotive(TileKey tile, boolean fromRoster)
    {
        // Two different questions, which the old menu also kept apart.  Bringing a locomotive INTO
        // autonomy offers everything the control station knows and it has never run.  Moving one
        // offers only the locomotives autonomy already runs, so a roster of forty does not have to be
        // read through to find the four that matter.
        List<String> names = new java.util.ArrayList<>();

        if (fromRoster)
        {
            for (String name : allLocomotives())
            {
                if (!placedLocomotives().contains(name)) names.add(name);
            }
        }
        else
        {
            names.addAll(placedLocomotives());
            names.remove(locomotiveAt(tile));
        }

        if (names.isEmpty())
        {
            JOptionPane.showMessageDialog(this, I18n.t(fromRoster
                ? "autosetup.ui.infoAllLocomotivesInAutonomy" : "error.noLocs"));
            return;
        }

        Object chosen = JOptionPane.showInputDialog(this,
            I18n.t(fromRoster ? "autosetup.ui.promptAddToAutonomy"
                              : "autosetup.ui.promptAddToStation"),
            I18n.t(fromRoster ? "autosetup.ui.menuAddToAutonomy"
                              : "autosetup.ui.menuAddToStation"),
            JOptionPane.PLAIN_MESSAGE, null, names.toArray(), names.get(0));

        if (chosen == null) return;

        String name = String.valueOf(chosen);

        // lift it off wherever it was standing before
        for (TileKey other : session.getReducer().getPoints().keySet())
        {
            if (name.equals(locomotiveAt(other))) session.placeLocomotive(other, null);
        }

        session.placeLocomotive(tile, name);
    }

    /**
     * Asks which locomotives, as a multi-select list rather than typed names - a name that does not
     * match the roster exactly would silently do nothing.
     *
     * @param tile
     * @param key the point property to store the choice under
     * @param names the locomotives worth offering, which is not always the whole roster
     */
    private void promptLocomotives(TileKey tile, String key, List<String> names)
    {
        if (names.isEmpty())
        {
            JOptionPane.showMessageDialog(this, I18n.t("error.noLocs"));
            return;
        }

        javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();

        for (String name : names) model.addElement(name);

        javax.swing.JList<String> list = new javax.swing.JList<>(model);

        // Tall enough to choose from without scrolling on an ordinary roster; the old dialog showed
        // four rows and made picking three engines out of twenty a scrolling exercise.
        list.setVisibleRowCount(Math.max(8, Math.min(18, names.size())));
        control(list);

        Set<String> chosen = strings(tile, key);
        List<Integer> indexes = new java.util.ArrayList<>();

        for (int i = 0; i < names.size(); i++)
        {
            if (chosen.contains(names.get(i))) indexes.add(i);
        }

        int[] selection = new int[indexes.size()];

        for (int i = 0; i < indexes.size(); i++) selection[i] = indexes.get(i);

        list.setSelectedIndices(selection);

        if (JOptionPane.showConfirmDialog(this, new JScrollPane(list), I18n.t(key.equals("home")
                ? "autosetup.ui.labelHomeFor" : "autosetup.ui.labelExcludedLocs"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        List<String> picked = list.getSelectedValuesList();

        session.setPointProperty(tile, key,
            picked.isEmpty() ? null : new org.json.JSONArray(picked));
    }

    private void promptName(TileKey tile)
    {
        String current = session.getStore().getPointName(tile);

        String name = JOptionPane.showInputDialog(this,
            I18n.t("autosetup.ui.promptPointName"), current == null ? "" : current);

        if (name == null) return;

        if (name.contains("\""))
        {
            JOptionPane.showMessageDialog(this, I18n.t("autosetup.ui.warnQuotesStrippedFromName"));
            name = name.replace("\"", "");
        }

        session.setPointName(tile, name.trim());
    }

    private void promptLinkName(TileKey tile)
    {
        String current = session.getStore().getLinkName(tile);

        String name = JOptionPane.showInputDialog(this,
            I18n.t("autosetup.ui.promptLinkName"), current == null ? "" : current);

        if (name != null) session.setLinkName(tile, name);
    }

    /**
     * Pairs a link by choosing its partner from a list of every link on the layout.
     *
     * A list rather than a second click, because a link almost always leads to ANOTHER PAGE - and the
     * other page is not on screen, so there is nothing there to click. Two-click pairing only ever
     * worked for the rare same-page case.
     */
    private void pairFromList(TileKey tile)
    {
        List<TileKey> candidates = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();

        for (Map.Entry<TileKey, LayoutDiagramComponent> entry
            : session.getGraph().getTiles().entrySet())
        {
            LayoutDiagramComponent component = entry.getValue();

            if (component == null || entry.getKey().equals(tile)) continue;

            if (!component.isLink()
                && component.getType() != LayoutDiagramComponent.componentType.TUNNEL) continue;

            String named = session.getStore().getLinkName(entry.getKey());

            candidates.add(entry.getKey());
            labels.add(named == null || named.trim().isEmpty()
                ? I18n.f("autosetup.ui.labelUnnamedLink", entry.getKey().toString())
                : named + "  -  " + entry.getKey().toString());
        }

        if (candidates.isEmpty())
        {
            JOptionPane.showMessageDialog(this, I18n.t("autosetup.ui.errorNoOtherLinks"));
            return;
        }

        Object chosen = JOptionPane.showInputDialog(this,
            I18n.t("autosetup.ui.promptPickPartner"), I18n.t("autosetup.ui.toolPortals"),
            JOptionPane.PLAIN_MESSAGE, null, labels.toArray(), labels.get(0));

        if (chosen == null) return;

        session.pairPortals(tile, candidates.get(labels.indexOf(String.valueOf(chosen))));
    }

    private void applyLength(TileKey tile)
    {
        Set<TileKey> targets = selection.isEmpty()
            ? new LinkedHashSet<>(java.util.Arrays.asList(tile)) : new LinkedHashSet<>(selection);

        // read from a tile that is actually going to change: with a selection the clicked tile is not
        // necessarily among them, and prefilling from it would show a number the dialog will not touch
        TileKey sample = targets.iterator().next();

        String entered = JOptionPane.showInputDialog(this,
            I18n.t("autosetup.ui.promptTileLength"),
            String.valueOf(session.getStore().getTileLength(sample)));

        if (entered == null) return;

        int length;

        try
        {
            length = Integer.parseInt(entered.trim());
        }
        catch (NumberFormatException e)
        {
            JOptionPane.showMessageDialog(this, I18n.t("autosetup.ui.errorNegativeLength"));
            return;
        }

        if (length < 0)
        {
            JOptionPane.showMessageDialog(this, I18n.t("autosetup.ui.errorNegativeLength"));
            return;
        }

        for (TileKey target : targets)
        {
            session.setTileLength(target, length);
        }

        selection.clear();
    }

    /**
     * A tile on the diagram was clicked while autonomy mode is on.
     *
     * @param tile which square
     * @param component what is drawn there
     * @param addToSelection whether the click was a shift-click, which adds to a bulk selection instead
     *        of acting immediately
     */
    public void tileClicked(TileKey tile, LayoutDiagramComponent component, boolean addToSelection)
    {
        if (tile == null || session.getGraph() == null) return;

        // A one-way run was started from a right-click menu and is waiting for its far end.
        if (oneWayFrom != null)
        {
            TileKey from = oneWayFrom;
            oneWayFrom = null;

            int changed = session.setOneWayRun(from, tile);

            say(hint, changed < 0 ? I18n.t("autosetup.ui.oneWayNoPath")
                : I18n.f("autosetup.ui.oneWayDone", changed));

            refresh();
            return;
        }

        // Autonomy takes no notice of this square, so a click here changes nothing.  Route buttons are
        // the case that matters: their connections are INFERRED from the track around them, so letting
        // them be set by hand would offer a decision the next rebuild would silently discard.
        if (isIgnored(tile))
        {
            say(hint, I18n.t("autosetup.ui.infoTileIgnored"));
            return;
        }

        if (addToSelection)
        {
            if (!selection.remove(tile)) selection.add(tile);

            refresh();
            return;
        }

        try
        {
            switch (tool)
            {
                case TEST: applyTest(tile, component); break;
                default: cycle(tile); break;
            }
        }
        catch (RuntimeException e)
        {
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
        }

        refresh();
    }

    /**
     * Left-click: change which way trains may run through this piece of track.
     *
     * The obvious gesture, and the one the tools used to provide - click the track, watch it change.
     * It cycles both ways -> one way -> the other way -> closed, and says in words what it just became,
     * so nobody has to infer the order from the arrows.
     *
     * A tile with more than one branch is not cycled: a click cannot say WHICH branch is meant, and
     * changing all of them at once is how a switch ends up set in a way nobody chose.  Those say so and
     * point at the menu, where the branches are listed separately.
     *
     * Whatever is clicked, the change lands on the run - clicking any tile of a straight run sets the
     * whole run, which is what makes one click enough.
     */
    private void cycle(TileKey tile)
    {
        TileKey target = leaderOf(tile);

        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(target);

        if (routes.isEmpty()) return;

        if (routes.size() > 1)
        {
            cycleBranching(target, routes);
            return;
        }

        Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> only =
            routes.entrySet().iterator().next();

        org.traincontrol.automationui.TilePorts.Route route = only.getValue();

        Direction next = after(session.getGraph().getDirection(target, only.getKey()));

        int changed = session.setRunDirection(target, only.getKey(), next);

        // The tile that changed can be some way from the one clicked, at the head of a long run, so it
        // is flashed as well as named - a message about a square nobody can find is half an answer.
        if (!target.equals(tile) && onReveal != null) onReveal.accept(target);

        // Say what actually happened.  This used to announce a direction unconditionally, so a run that
        // could not be set - a dead end, in the days when that was possible - reported success.
        say(hint, changed == 0 ? I18n.t("autosetup.ui.oneWayNoPath")
            : I18n.f("autosetup.ui.cycledTo", describeTile(target), describe(next, route)));
    }

    /**
     * Left-click on a switch, a crossing or a double curve: the next combination of open and shut ARMS.
     *
     * Not the next of four whole-tile states, which is what this did before and what kept being wrong.
     * The states a junction has are not four; on a crossing they are sixteen, and the useful ones -
     * north-south open while east-west is shut - are exactly the ones a whole-tile cycle cannot reach.
     * Neither can they be enumerated as Direction constants: TOWARD_A names a route's own first side,
     * and nothing makes those agree between branches, so "TOWARD_A everywhere" on a switch with its toe
     * at S means toward N and toward S at once.
     *
     * So the state cycled is what the drawing already shows: one bit per ARM of the tile, saying
     * whether a train may leave through it.  Every combination is reachable, and the click is a plain
     * binary counter over them, which makes the order predictable without anybody having to learn it.
     *
     * The bits translate back to routes exactly.  A route (A,B) reads its two arms: both open is both
     * ways, one open is one way toward it, neither is closed.  An arm shared by two branches - a
     * switch's toe - is one bit, so the two branches cannot end up disagreeing about it, which is the
     * state that used to draw a green arrow on top of a red one.
     */
    private void cycleBranching(TileKey target,
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes)
    {
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides = armsOf(routes);

        if (sides.isEmpty()) return;

        int next = (armMask(target, routes, sides) + 1) % (1 << sides.size());

        Map<RouteId, Direction> wanted = new java.util.LinkedHashMap<>();

        for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
            : routes.entrySet())
        {
            org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

            boolean openA = (next & (1 << sides.indexOf(route.getA()))) != 0;
            boolean openB = (next & (1 << sides.indexOf(route.getB()))) != 0;

            wanted.put(entry.getKey(), openA && openB ? Direction.BOTH
                : openA ? Direction.TOWARD_A
                : openB ? Direction.TOWARD_B : Direction.NONE);
        }

        // One re-derivation for the tile, not one per branch
        session.setDirections(target, wanted);

        say(hint, I18n.f("autosetup.ui.cycledSwitch", describeTile(target), armState(next, sides)));

        refresh();
    }

    /**
     * The arms of a tile - every side any of its routes touches - in a fixed order so the counter
     * always steps the same way.
     */
    private static java.util.List<org.traincontrol.automationui.TilePorts.Side> armsOf(
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes)
    {
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides = new java.util.ArrayList<>();

        for (org.traincontrol.automationui.TilePorts.Side side
            : org.traincontrol.automationui.TilePorts.Side.values())
        {
            for (org.traincontrol.automationui.TilePorts.Route route : routes.values())
            {
                if ((route.getA() == side || route.getB() == side) && !sides.contains(side))
                {
                    sides.add(side);
                }
            }
        }

        return sides;
    }

    /**
     * Which arms are currently open, as a bitmask over armsOf().
     *
     * Read the way the arrows are drawn - an arm is open if ANY branch through it lets a train out -
     * so the number the counter advances from is the state the user can see.
     */
    private int armMask(TileKey target,
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes,
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides)
    {
        int mask = 0;

        for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
            : routes.entrySet())
        {
            org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

            Direction direction = session.getGraph().getDirection(target, entry.getKey());

            if (direction == Direction.BOTH || direction == Direction.TOWARD_A)
            {
                mask |= 1 << sides.indexOf(route.getA());
            }

            if (direction == Direction.BOTH || direction == Direction.TOWARD_B)
            {
                mask |= 1 << sides.indexOf(route.getB());
            }
        }

        return mask;
    }

    /**
     * The open arms in words - "N, W" - or the whole-tile answer when they are all open or all shut.
     */
    private String armState(int mask, java.util.List<org.traincontrol.automationui.TilePorts.Side> sides)
    {
        if (mask == 0) return I18n.t("autosetup.ui.dirNone");

        if (mask == (1 << sides.size()) - 1) return I18n.t("autosetup.ui.dirBoth");

        java.util.List<String> open = new java.util.ArrayList<>();

        for (int i = 0; i < sides.size(); i++)
        {
            if ((mask & (1 << i)) != 0) open.add(String.valueOf(sides.get(i)));
        }

        return I18n.f("autosetup.ui.dirOpenArms", String.join(", ", open));
    }

    /**
     * The next state in the cycle: both ways -> one way -> the other way -> closed -> both ways.
     */
    private static Direction after(Direction current)
    {
        switch (current)
        {
            case BOTH: return Direction.TOWARD_A;
            case TOWARD_A: return Direction.TOWARD_B;
            case TOWARD_B: return Direction.NONE;
            default: return Direction.BOTH;
        }
    }

    /**
     * What a direction means, in words, naming the side rather than an A or a B nobody can see.
     */
    private String describe(Direction direction, org.traincontrol.automationui.TilePorts.Route route)
    {
        switch (direction)
        {
            case TOWARD_A: return I18n.f("autosetup.ui.dirToward", String.valueOf(route.getA()));
            case TOWARD_B: return I18n.f("autosetup.ui.dirToward", String.valueOf(route.getB()));
            case NONE: return I18n.t("autosetup.ui.dirNone");
            default: return I18n.t("autosetup.ui.dirBoth");
        }
    }

    /**
     * Asks whether a train could get from one sensor to another, and shows the route.
     *
     * Two clicks, like portal pairing.  The answer comes from the same reduction everything else uses,
     * so what this says is what a running train would find - not a second opinion.
     */
    private void applyTest(TileKey tile, LayoutDiagramComponent component)
    {
        if (component == null || !component.isFeedback())
        {
            say(hint, I18n.t("autosetup.ui.labelPointNotStation"));
            return;
        }

        if (testFrom == null)
        {
            testFrom = tile;
            testPath.clear();
            waitFor(I18n.t("autosetup.ui.promptTestDestination"));
            return;
        }

        if (testFrom.equals(tile))
        {
            say(hint, I18n.t("autosetup.ui.testSameTile"));
            return;
        }

        // Both ways, always.  Asking the user to nominate a direction only makes them run the test
        // twice to learn the thing they actually wanted to know - a one-way run looks identical to a
        // broken one until you have tried it from the other end.
        java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> there =
            session.getReducer() == null ? null : session.getReducer().findPath(testFrom, tile);

        java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> back =
            session.getReducer() == null ? null : session.getReducer().findPath(tile, testFrom);

        testPath.clear();

        // The outline shows whichever direction works, so there is something on the track to look at
        // even when only one way is possible.
        outline(there != null ? there : back, testFrom, tile);

        sayRich(hint, I18n.f("autosetup.ui.testBothWays",
            escape(describeTile(testFrom)), escape(describeTile(tile)), leg(there), leg(back)));

        testFrom = null;
    }

    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;");
    }

    private String leg(java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> run)
    {
        return run == null ? I18n.t("autosetup.ui.testLegBlocked")
            : I18n.f("autosetup.ui.testLegReachable", run.size());
    }

    private void outline(java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> run,
        TileKey from, TileKey to)
    {
        if (run == null) return;

        testPath.add(from);
        testPath.add(to);

        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge : run)
        {
            for (org.traincontrol.automationui.GraphReducer.TileStep step : edge.getPath())
            {
                testPath.add(step.getTile());
            }
        }
    }

    /**
     * What a square is, for a message that would otherwise be a coordinate.
     */
    private String describeTile(TileKey tile)
    {
        String named = session.getStore().getPointName(tile);

        if (named != null && !named.trim().isEmpty()) return named.trim();

        org.traincontrol.base.LayoutDiagramComponent component =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        return component != null && component.isFeedback()
            ? "s88 " + component.getRawAddress() : tile.getX() + "," + tile.getY();
    }

    // --- state ------------------------------------------------------------------------------------

    public Tool getTool()
    {
        return tool;
    }

    public boolean isShowingDirections()
    {
        return showDirections.isSelected();
    }

    public boolean isShowingLengths()
    {
        return showLengths.isSelected();
    }

    public Set<TileKey> getSelection()
    {
        return java.util.Collections.unmodifiableSet(selection);
    }

    /**
     * Whether there are edits the user has not saved - what decides whether closing has to ask.
     * @return
     */
    public boolean isDirty()
    {
        return session.isDirty();
    }

    /**
     * What the editor should draw over one square: its routes and their directions, its length, and
     * whether it is part of the bulk selection.
     *
     * Computed per tile on request rather than published as a map, because the editor already walks its
     * own grid to redraw and knows exactly which page is showing - this panel does not.
     *
     * @param tile
     * @return the annotation, possibly blank, never null
     */
    public org.traincontrol.automationui.TileAnnotation annotationFor(TileKey tile)
    {
        java.util.List<org.traincontrol.automationui.TileAnnotation.Mark> marks = new java.util.ArrayList<>();

        boolean ignored = isIgnored(tile);

        // A tile that merely follows its run draws no arrows of its own, so the run reads as one
        // decision made at one end rather than eleven waiting to be made.  It is NOT shaded: grey on
        // this diagram means autonomy cannot use a square, and a follower is perfectly usable.
        boolean follower = isFollower(tile);

        if (showDirections.isSelected() && session.getGraph() != null && !ignored && !follower)
        {
            Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(tile);

            // more than one route means a switch, a crossing or a double curve - somewhere a train has
            // a choice, and somewhere the user needs to see every option rather than only the closed
            boolean branching = routes.size() > 1;

            for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
                : routes.entrySet())
            {
                org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

                Direction direction = session.getGraph().getDirection(tile, entry.getKey());

                // A route the hardware restricts is one-way whatever the user chose: the graph leaves the
                // authored direction BOTH there (see defaultDirection), but a train still cannot pass
                // against the blades, and the drawing has to say what a train can actually do.
                if (route.getDirectedToward() != null && direction != Direction.NONE)
                {
                    direction = route.getDirectedToward() == route.getA()
                        ? Direction.TOWARD_A : Direction.TOWARD_B;
                }

                // Only what RESTRICTS a train is drawn on plain track.  Both-ways is the overwhelming
                // majority of a layout and is also the default, so drawing it put an arrow on every
                // square and left the real decisions with nothing to stand out against.
                //
                // A switch is the exception, and always shows every branch: a switch is WHERE the
                // decisions are, and a branch drawn only when restricted leaves the user unable to see
                // that the other branches exist, let alone which of them they have already dealt with.
                if (direction == Direction.BOTH && !branching && !showAllDirections.isSelected())
                {
                    continue;
                }

                marks.add(new org.traincontrol.automationui.TileAnnotation.Mark(
                    route.getA(), route.getB(), direction));
            }

            // ...but a bare layout cannot answer "does this sensor reach that one, and which way", so
            // each run of track between two sensors carries one arrow in the middle of it.
            if (marks.isEmpty() && flowMarks.containsKey(tile))
            {
                marks.add(flowMarks.get(tile));
            }
        }

        // 0 means "does not count" and is the default everywhere, so drawing it would number every tile
        int length = -1;

        if (showLengths.isSelected())
        {
            int stored = session.getStore().getTileLength(tile);

            if (stored > 0) length = stored;
        }

        // the found route borrows the selection outline: the two are never on screen together, because
        // switching tools clears both
        boolean outlined = selection.contains(tile)
            || testPath.contains(tile) || tile.equals(testFrom);

        return new org.traincontrol.automationui.TileAnnotation(marks, length, outlined,
            badgeFor(tile), isDimmed(tile), isCurved(tile), isPairedPortal(tile));
    }

    /**
     * Whether this square is a link that has been paired with another.
     *
     * Unpaired links are left alone: a two-way door drawn on one that leads nowhere would promise a
     * route that does not exist, and the missing pairing already has a finding of its own.
     */
    private boolean isPairedPortal(TileKey tile)
    {
        LayoutDiagramComponent component =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        return component != null
            && org.traincontrol.automationui.TilePorts.hasPortal(component.getType())
            && session.getStore().getPortalPartner(tile) != null;
    }

    /**
     * Whether this square's track is drawn as a chord cutting a corner.
     *
     * Curves and double curves only, by TYPE rather than by geometry: a switch's diagonal arm is a
     * chord too, and the author asked for the tilt on curves alone.
     */
    private boolean isCurved(TileKey tile)
    {
        LayoutDiagramComponent component =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        if (component == null) return false;

        LayoutDiagramComponent.componentType type = component.getType();

        return type == LayoutDiagramComponent.componentType.CURVE
            || type == LayoutDiagramComponent.componentType.FEEDBACK_CURVE
            || type == LayoutDiagramComponent.componentType.DOUBLE_CURVE
            || type == LayoutDiagramComponent.componentType.FEEDBACK_DOUBLE_CURVE;
    }

    /**
     * Whether a tile should be pushed back rather than drawn at full strength.
     *
     * Signals only.  They sit on almost every run, their art is the heaviest on the diagram, and
     * autonomy commands them green as a matter of course - so at full strength they read as the most
     * important thing on a page where they are usually the least interesting.  Still configurable:
     * a signal can be restricted like any other tile, and a restriction on one still draws.
     */
    /**
     * What this sensor has been designated as, or null when it is not a station.
     */
    private org.traincontrol.automationui.TileAnnotation.Badge badgeFor(TileKey tile)
    {
        // Every sensor that made it into the graph gets a badge, not only the stations.  A plain point
        // is a thing the user has decided NOT to make a station, and it should look like a decision
        // rather than like an ordinary tile nobody has reached yet.
        if (session.getReducer() == null
            || !session.getReducer().getPoints().containsKey(tile)) return null;

        String name = session.getStore().getPointName(tile);

        org.traincontrol.automationui.TilePorts.Route route = firstRoute(tile);

        boolean station = session.getStore().isStation(tile);

        // Read off the three switches, not off the flags they compile to: those are derived at build
        // time now and are never authored, so reading them would leave every badge blank.  A station
        // that turns trains round wears the terminus badge and anything else that does wears the
        // reversing one, which is what each COMPILES to and what the graph window drew.
        boolean turns = session.isTurnAround(tile);

        return new org.traincontrol.automationui.TileAnnotation.Badge(
            station,
            station && turns,
            !station && turns,
            Boolean.FALSE.equals(session.getPointProperty(tile, "active"))
                || session.isParking(tile),
            name != null && !name.trim().isEmpty(),
            route == null ? null : route.getA(),
            route == null ? null : route.getB());
    }

    /**
     * The tile's first route, which is where its badge is drawn.
     */
    private org.traincontrol.automationui.TilePorts.Route firstRoute(TileKey tile)
    {
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(tile);

        return routes.isEmpty() ? null : routes.values().iterator().next();
    }

    /**
     * Whether this tile takes its direction from another one, rather than carrying its own.
     */
    private boolean isFollower(TileKey tile)
    {
        TileKey leader = runLeaders.get(tile);

        return leader != null && !leader.equals(tile);
    }

    /**
     * The tile that actually gets set when the user acts on this one.
     */
    private TileKey leaderOf(TileKey tile)
    {
        TileKey leader = runLeaders.get(tile);

        return leader == null ? tile : leader;
    }

    /**
     * Whether autonomy takes no notice of this square, so nothing here is the user's to decide.
     *
     * Three ways that happens, and the drawing does not distinguish them because the answer to all
     * three is the same: leave it alone.
     *   - the page is excluded
     *   - the tile type cannot be routed over (turntables, scissors)
     *   - the tile carries whatever line it sits on rather than a line of its own (route buttons),
     *     which is INFERRED from its neighbours and so is not something to click
     */
    private boolean isIgnored(TileKey tile)
    {
        if (session.getStore().getExcludedPages().contains(tile.getPage())) return true;

        org.traincontrol.base.LayoutDiagramComponent component =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        // A blank square is not track, so there is nothing to set on it.  Treating it as configurable
        // opened a menu offering a one-way run and a length on empty space.
        if (component == null) return true;

        // A lamp carries no track at all - it is decoration on the diagram - so it is greyed with the
        // route buttons and turntables rather than left looking like something to configure.
        if (component.getType() == LayoutDiagramComponent.componentType.LAMP) return true;

        return org.traincontrol.automationui.TilePorts.isDisqualified(component.getType())
            || org.traincontrol.automationui.TilePorts.isTransparent(component.getType());
    }

    /**
     * Whether the square is drawn shaded.
     *
     * Narrower than isIgnored, and deliberately so: shading is a message about a DRAWING - "autonomy
     * cannot use this piece of track" - and an empty square is not a piece of track.  Shading them
     * turned the gaps between lines into a field of grey boxes that read as broken rather than blank.
     * They are still not configurable; they just have nothing to say.
     */
    private boolean isDimmed(TileKey tile)
    {
        return componentAt(tile) != null && isIgnored(tile);
    }

    /**
     * The diagram square at a key, read from the PAGE rather than the graph.
     *
     * The graph omits excluded pages entirely, so asking it whether a square on such a page is blank
     * always answers yes - and everything on an excluded page would then stop being shaded, losing the
     * one thing the shading is there to say.
     */
    private org.traincontrol.base.LayoutDiagramComponent componentAt(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagram page = pageOf(tile);

        return page == null ? null : page.getComponent(tile.getX(), tile.getY());
    }

    /**
     * The page a key names, or null if the session has never heard of it.
     */
    private org.traincontrol.base.LayoutDiagram pageOf(TileKey tile)
    {
        for (org.traincontrol.base.LayoutDiagram diagram : session.getPages())
        {
            if (diagram.getName().equals(tile.getPage())) return diagram;
        }

        return null;
    }

    /**
     * Re-reads the setup and shows what it says.
     *
     * Runs after every edit, which is affordable because the derivation is cheap and the alternative -
     * a panel that agrees with itself while the graph has moved on - is the failure this whole design
     * exists to avoid.
     */
    public final void refresh()
    {
        flowMarks = session.flowMarks();
        runLeaders = session.runLeaders();

        findingsModel.clear();
        findingTiles.clear();
        findingSeverity.clear();

        // Split into what must be fixed and what is only worth checking, and headed the same way as
        // the Auto tab's list.  The two views answer the same question about the same setup, so a
        // reader who has learned to read one should not have to learn the other.
        //
        // Only this page, though: an editor window shows one page, and a finding about another cannot
        // be acted on here.  The whole-layout view is the one in the Auto tab.
        List<Object[]> errorRows = new java.util.ArrayList<>();
        List<Object[]> warningRows = new java.util.ArrayList<>();

        for (org.traincontrol.automationui.TileGraph.Problem problem : session.getGraph() == null
            ? java.util.Collections.<org.traincontrol.automationui.TileGraph.Problem>emptyList()
            : session.getGraph().getProblems())
        {
            if (!onThisPage(problem.getTile())) continue;

            (problem.isBlocking() ? errorRows : warningRows).add(new Object[]
            {
                problem.getTile(),
                describe(problem.getMessageKey(), problem.getTile() == null
                    ? "" : describeTile(problem.getTile()))
            });
        }

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (!onThisPage(finding.getTile())) continue;

            String subject = finding.getTile() == null
                ? finding.getSubject() : describeTile(finding.getTile());

            (finding.getSeverity() == AutonomyChecks.Severity.ERROR ? errorRows : warningRows)
                .add(new Object[] {finding.getTile(), describe(finding.getMessageKey(), subject)});
        }

        int errors = errorRows.size();

        section(I18n.f("autosetup.ui.headingErrors", errorRows.size()), errorRows,
            AutonomyChecks.Severity.ERROR);
        section(I18n.f("autosetup.ui.headingWarningsShort", warningRows.size()), warningRows,
            AutonomyChecks.Severity.WARNING);

        // Unnamed points are the one thing the checks cannot see: a generated name is a valid name, it
        // is just useless in a timetable.  Counted here so the panel can offer to fix them all at once.
        int unnamed = unnamedPoints().size();

        if (errors > 0)
        {
            banner.setText(I18n.f("autosetup.ui.labelBlockingCount", errors));
            banner.setBackground(new java.awt.Color(255, 210, 210));
        }
        else if (unnamed > 0)
        {
            banner.setText(I18n.f("autosetup.ui.labelUnnamedCount", unnamed));
            banner.setBackground(new java.awt.Color(255, 240, 200));
        }
        else
        {
            banner.setText(I18n.f("autosetup.ui.labelGraphSize",
                session.getReducer().getPoints().size(),
                session.getReducer().getEdges().size()));
            banner.setBackground(new java.awt.Color(214, 245, 214));
        }

        if (!selection.isEmpty())
        {
            say(hint, I18n.f("autosetup.ui.labelTilesSelected", selection.size()));
        }

        if (onChanged != null) onChanged.run();
    }

    /**
     * Adds one severity section to the list, headed and indented as the Auto tab's is.
     */
    private void section(String heading, List<Object[]> rows, AutonomyChecks.Severity severity)
    {
        if (rows.isEmpty()) return;

        findingsModel.addElement(heading);
        findingTiles.add(null);
        findingSeverity.add(null);

        for (Object[] row : rows)
        {
            findingsModel.addElement("   " + row[1]);
            findingTiles.add((TileKey) row[0]);
            findingSeverity.add(severity);
        }
    }

    private boolean onThisPage(TileKey tile)
    {
        return tile == null || page == null || page.equals(tile.getPage());
    }

    /**
     * The sensors on this page that nobody has named, in reading order.
     */
    private List<TileKey> unnamedPoints()
    {
        List<TileKey> out = new java.util.ArrayList<>();

        if (session.getReducer() == null) return out;

        for (org.traincontrol.automationui.GraphReducer.ReducedPoint point
            : session.getReducer().getPoints().values())
        {
            if (!onThisPage(point.getTile())) continue;

            String named = session.getStore().getPointName(point.getTile());

            if (named == null || named.trim().isEmpty()) out.add(point.getTile());
        }

        java.util.Collections.sort(out, new java.util.Comparator<TileKey>()
        {
            @Override
            public int compare(TileKey a, TileKey b)
            {
                return a.getY() != b.getY() ? a.getY() - b.getY() : a.getX() - b.getX();
            }
        });

        return out;
    }

    /**
     * Walks the unnamed sensors on this page, one prompt each, highlighting the one being named.
     *
     * The alternative is hunting for them: a generated name is a valid name, so nothing refuses to work
     * and nothing points at them - they simply turn up as coordinates in a timetable weeks later.
     */
    private void nameEverything()
    {
        List<TileKey> unnamed = unnamedPoints();

        if (unnamed.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoEverythingNamed"));
            return;
        }

        for (int i = 0; i < unnamed.size(); i++)
        {
            TileKey tile = unnamed.get(i);

            // shown before asking, so the question is about a square the user can see
            if (onReveal != null) onReveal.accept(tile);

            String name = JOptionPane.showInputDialog(this,
                I18n.f("autosetup.ui.promptNameEverything", i + 1, unnamed.size()), "");

            // cancel stops the walk rather than skipping one, because a walk of forty needs a way out
            if (name == null) break;

            if (!name.trim().isEmpty()) session.setPointName(tile, name.trim());
        }

        refresh();
    }

    /**
     * @param onReveal called with a tile that should be scrolled to and flashed
     */
    /**
     * @param onDiagramChanged run after a track diagram page is written, so the grid can be rebuilt
     */
    public void setOnDiagramChanged(Runnable onDiagramChanged)
    {
        this.onDiagramChanged = onDiagramChanged;
    }

    public void setOnReveal(java.util.function.Consumer<TileKey> onReveal)
    {
        this.onReveal = onReveal;
    }

    /**
     * @return the track-lengths toggle, so the window can keep it exclusive with its own Addresses box
     */
    public JCheckBox getShowLengths()
    {
        return control(showLengths);
    }

    /**
     * @param locomotiveNames supplies the roster, for exclusion and home lists
     */
    public void setLocomotiveNames(java.util.function.Supplier<List<String>> locomotiveNames)
    {
        this.locomotiveNames = locomotiveNames;
    }

    private String describe(String key, String subject)
    {
        try
        {
            return I18n.f(key, subject);
        }
        catch (RuntimeException e)
        {
            // a key that has not reached the bundles yet should not blank the whole list
            return key + " " + subject;
        }
    }

    public void save()
    {
        try
        {
            AutonomyCompanionStore.Reconciliation report = session.save();

            if (!report.isClean())
            {
                StringBuilder text = new StringBuilder();

                for (Map.Entry<String, List<String>> entry
                    : report.getNamesStillReferenced().entrySet())
                {
                    text.append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
                }

                for (String forgotten : report.getForgottenNames())
                {
                    text.append(forgotten).append("\n");
                }

                if (text.length() > 0)
                {
                    JOptionPane.showMessageDialog(this, text.toString());
                }
            }

            refresh();
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
        }
    }
}
