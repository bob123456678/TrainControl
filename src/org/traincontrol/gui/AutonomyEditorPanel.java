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
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import org.traincontrol.base.AutonomyChecks;
import org.traincontrol.base.AutonomyCompanionStore;
import org.traincontrol.base.AutonomySession;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.TileGraph.Direction;
import org.traincontrol.base.TileGraph.RouteId;
import org.traincontrol.base.TileGraph.TileKey;
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
         * Cycle which way trains may move through the tile.
         */
        CONNECTIONS,

        /**
         * Name a sensor, and say whether it is a station.
         */
        POINTS,

        /**
         * Name a link and pair it with the one it leads to.
         */
        PORTALS,

        /**
         * Say how long a piece of track counts as.
         */
        LENGTHS,

        /**
         * Ask whether a train could get from one sensor to another, and see the route it would take.
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

    private Tool tool = Tool.CONNECTIONS;

    /**
     * How wide the panel is allowed to get.  The messages here are sentences rather than labels, and a
     * plain JLabel asks for however wide its text is - which stretched the editor's palette column
     * across the window the first time a long one appeared.
     */
    private static final int WIDTH = 280;

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
    private TileKey testFrom;
    private final Set<TileKey> testPath = new LinkedHashSet<>();

    // A one-way run started from the right-click menu, waiting for its far end
    private TileKey oneWayFrom;

    // Where the locomotive roster comes from.  Supplied rather than read here, because the session is
    // headless and knows nothing about the control station.
    private java.util.function.Supplier<List<String>> locomotiveNames;

    // One arrow per run of track between sensors.  Recomputed on refresh rather than per tile, because
    // it is derived from the whole reduction and the editor asks about every square in turn.
    private Map<TileKey, org.traincontrol.base.TileAnnotation.Mark> flowMarks =
        new java.util.LinkedHashMap<>();

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

        refresh();
    }

    /**
     * Wraps a sentence to the panel's width instead of demanding a column as wide as the sentence.
     */
    private void say(JLabel label, String text)
    {
        label.setText("<html><body style='width:" + (WIDTH - 30) + "px'>"
            + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
    }

    /**
     * The same, for a message that carries its own mark-up.  Only for text this class builds itself -
     * never for anything a user typed, which is what say() escapes.
     */
    private void sayRich(JLabel label, String html)
    {
        label.setText("<html><body style='width:" + (WIDTH - 30) + "px'>" + html + "</body></html>");
    }

    private JPanel buildTools()
    {
        JPanel panel = new JPanel(new GridLayout(0, 1, 2, 2));

        panel.add(heading(I18n.t("autosetup.ui.title")));

        ButtonGroup group = new ButtonGroup();

        panel.add(toolButton(Tool.CONNECTIONS, I18n.t("autosetup.ui.toolConnections"), group, true));
        panel.add(toolButton(Tool.POINTS, I18n.t("autosetup.ui.toolPoints"), group, false));
        panel.add(toolButton(Tool.PORTALS, I18n.t("autosetup.ui.toolPortals"), group, false));
        panel.add(toolButton(Tool.LENGTHS, I18n.t("autosetup.ui.toolLengths"), group, false));
        panel.add(toolButton(Tool.TEST, I18n.t("autosetup.ui.toolTest"), group, false));

        // the toggles change what is drawn, not what is decided, so all they do is redraw
        showDirections.addActionListener(e -> refresh());
        showAllDirections.addActionListener(e -> refresh());
        showLengths.addActionListener(e -> refresh());

        panel.add(AutonomyViewerPanel.styled(showDirections, false));
        panel.add(AutonomyViewerPanel.styled(showAllDirections, false));
        panel.add(AutonomyViewerPanel.styled(showLengths, false));

        hint.setFont(AutonomyViewerPanel.FONT_LIST);
        panel.add(hint);

        banner.setOpaque(true);
        banner.setFont(AutonomyViewerPanel.FONT_BOLD);
        banner.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        panel.add(banner);

        return panel;
    }

    private JToggleButton toolButton(final Tool which, String text, ButtonGroup group, boolean selected)
    {
        JToggleButton button = new JToggleButton(text, selected);

        AutonomyViewerPanel.styled(button, true);
        button.setFocusable(false);
        button.addActionListener(e ->
        {
            tool = which;
            pendingPortal = null;
            selection.clear();
            testFrom = null;
            testPath.clear();

            if (which == Tool.TEST) say(hint, I18n.t("autosetup.ui.promptTestStart"));

            refresh();
        });

        group.add(button);

        return button;
    }

    private JScrollPane buildFindings()
    {
        findings.setVisibleRowCount(8);
        findings.setFont(AutonomyViewerPanel.FONT_LIST);

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
        panel.add(AutonomyViewerPanel.styled(check, false));

        JButton nameAll = new JButton(I18n.t("autosetup.ui.btnNameEverything"));
        nameAll.addActionListener(e -> nameEverything());
        panel.add(AutonomyViewerPanel.styled(nameAll, false));

        JButton save = new JButton(I18n.t("autosetup.ui.btnApply"));
        save.addActionListener(e -> save());
        panel.add(AutonomyViewerPanel.styled(save, true));

        return panel;
    }

    private JLabel heading(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(AutonomyViewerPanel.FONT);
        label.setForeground(AutonomyViewerPanel.HEADING_COLOUR);
        return label;
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
     * A tile was right-clicked: everything that can be set on it, named, on one menu.
     *
     * The reason this exists alongside the tools: cycling a tile is quick once you know the order, and
     * opaque before then - a switch has a route per branch, so a click can change three things at once
     * and the user is left inferring the rule from the arrows. The menu says what the options ARE.
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

        // Nothing on an ignored square is the user's to set, so it says so rather than offering a menu
        // whose every item would be a no-op.
        if (isIgnored(tile))
        {
            say(hint, I18n.t("autosetup.ui.infoTileIgnored"));
            return;
        }

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        if (component != null && component.isFeedback())
        {
            javax.swing.JMenuItem properties = new javax.swing.JMenuItem(
                I18n.t("autosetup.ui.menuPointProperties"));
            properties.addActionListener(e -> { pointProperties(tile); refresh(); });
            menu.add(AutonomyViewerPanel.styled(properties, true));
            menu.addSeparator();
        }

        // The gesture that per-tile cycling could not express: say where trains may run BETWEEN two
        // places, and let the tiles in between work out what that means for each of them.
        javax.swing.JMenuItem oneWay = new javax.swing.JMenuItem(
            I18n.t("autosetup.ui.menuOneWayRun"));
        oneWay.addActionListener(e ->
        {
            oneWayFrom = tile;
            say(hint, I18n.t("autosetup.ui.promptOneWayTo"));
        });
        menu.add(AutonomyViewerPanel.styled(oneWay, false));
        menu.addSeparator();

        Map<RouteId, org.traincontrol.base.TilePorts.Route> routes = session.getRoutes(tile);

        if (!routes.isEmpty())
        {
            menu.add(AutonomyViewerPanel.styled(
                new JLabel("  " + I18n.t("autosetup.ui.menuRouteHeading")), true));

            // One submenu per branch, so a switch's branches are visibly separate things rather than
            // one gesture that changes all of them without saying so.
            boolean many = routes.size() > 1;

            for (Map.Entry<RouteId, org.traincontrol.base.TilePorts.Route> entry : routes.entrySet())
            {
                org.traincontrol.base.TilePorts.Route route = entry.getValue();

                javax.swing.JMenuItem holder = many
                    ? new javax.swing.JMenu(I18n.f("autosetup.ui.menuBranch",
                        String.valueOf(route.getA()), String.valueOf(route.getB())))
                    : null;

                for (javax.swing.JMenuItem item : directionItems(tile, entry.getKey(), route))
                {
                    if (holder == null) menu.add(item); else ((javax.swing.JMenu) holder).add(item);
                }

                if (holder != null) menu.add(AutonomyViewerPanel.styled(holder, false));
            }

            if (many)
            {
                javax.swing.JMenu all = new javax.swing.JMenu(
                    I18n.t("autosetup.ui.menuAllBranches"));

                for (final Direction direction : new Direction[] {Direction.BOTH, Direction.NONE})
                {
                    javax.swing.JMenuItem item = new javax.swing.JMenuItem(
                        direction == Direction.BOTH
                            ? I18n.t("autosetup.ui.menuRouteBoth")
                            : I18n.t("autosetup.ui.menuRouteNone"));

                    final TileKey target = tile;
                    item.addActionListener(e ->
                    {
                        session.setDirection(new LinkedHashSet<>(
                            java.util.Arrays.asList(target)), direction);
                        refresh();
                    });

                    all.add(AutonomyViewerPanel.styled(item, false));
                }

                menu.add(AutonomyViewerPanel.styled(all, false));
            }

            menu.addSeparator();
        }

        if (component != null && component.isFeedback())
        {
            javax.swing.JMenuItem name = new javax.swing.JMenuItem(
                I18n.t("autosetup.ui.menuSetName"));
            name.addActionListener(e -> { promptName(tile); refresh(); });
            menu.add(AutonomyViewerPanel.styled(name, false));

            javax.swing.JCheckBoxMenuItem station = new javax.swing.JCheckBoxMenuItem(
                I18n.t("autosetup.ui.menuToggleStation"), session.getStore().isStation(tile));
            station.addActionListener(e ->
            {
                session.setStation(tile, station.isSelected());
                refresh();
            });
            menu.add(AutonomyViewerPanel.styled(station, false));
        }

        if (component != null && (component.isLink()
            || component.getType() == LayoutDiagramComponent.componentType.TUNNEL))
        {
            javax.swing.JMenuItem name = new javax.swing.JMenuItem(
                I18n.t("autosetup.ui.menuSetName"));
            name.addActionListener(e -> { promptLinkName(tile); refresh(); });
            menu.add(AutonomyViewerPanel.styled(name, false));

            javax.swing.JMenuItem pair = new javax.swing.JMenuItem(
                I18n.t("autosetup.ui.menuPairLink"));
            pair.addActionListener(e -> { pairFromList(tile); refresh(); });
            menu.add(AutonomyViewerPanel.styled(pair, false));

            if (session.getStore().getPortalPartner(tile) != null)
            {
                javax.swing.JMenuItem unpair = new javax.swing.JMenuItem(
                    I18n.t("autosetup.ui.menuUnpairLink"));
                unpair.addActionListener(e -> { session.unpairPortal(tile); refresh(); });
                menu.add(AutonomyViewerPanel.styled(unpair, false));
            }
        }

        javax.swing.JMenuItem length = new javax.swing.JMenuItem(
            I18n.t("autosetup.ui.menuSetLength"));
        length.addActionListener(e -> { applyLength(tile); refresh(); });
        menu.add(AutonomyViewerPanel.styled(length, false));

        menu.show(invoker, x, y);
    }

    /**
     * The three answers for one route, with the two one-way options named by where they lead rather
     * than by an A and a B nobody can see.
     */
    private List<javax.swing.JMenuItem> directionItems(final TileKey tile, final RouteId routeId,
        org.traincontrol.base.TilePorts.Route route)
    {
        List<javax.swing.JMenuItem> items = new java.util.ArrayList<>();

        Direction current = session.getGraph().getDirection(tile, routeId);

        items.add(directionItem(tile, routeId, Direction.BOTH,
            I18n.t("autosetup.ui.menuRouteBoth"), current));
        items.add(directionItem(tile, routeId, Direction.TOWARD_A,
            I18n.f("autosetup.ui.menuRouteToward", String.valueOf(route.getA())), current));
        items.add(directionItem(tile, routeId, Direction.TOWARD_B,
            I18n.f("autosetup.ui.menuRouteToward", String.valueOf(route.getB())), current));
        items.add(directionItem(tile, routeId, Direction.NONE,
            I18n.t("autosetup.ui.menuRouteNone"), current));

        return items;
    }

    private javax.swing.JMenuItem directionItem(final TileKey tile, final RouteId routeId,
        final Direction direction, String text, Direction current)
    {
        javax.swing.JRadioButtonMenuItem item =
            new javax.swing.JRadioButtonMenuItem(text, direction == current);

        item.addActionListener(e ->
        {
            session.setDirection(tile, routeId, direction);
            refresh();
        });

        return AutonomyViewerPanel.styled(item, false);
    }

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
                case CONNECTIONS: applyConnection(tile); break;
                case POINTS: applyPoint(tile, component); break;
                case PORTALS: applyPortal(tile, component); break;
                case LENGTHS: applyLength(tile); break;
                case TEST: applyTest(tile, component); break;
                default: break;
            }
        }
        catch (RuntimeException e)
        {
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
        }

        refresh();
    }

    /**
     * Cycles a tile, or the whole selection if one has been built up.
     *
     * A switch has a route per branch, and clicking cycles them together: the panel lists them
     * separately for the cases where they need to differ, but the common case is a whole switch opened
     * or closed at once.
     */
    private void applyConnection(TileKey tile)
    {
        if (!selection.isEmpty())
        {
            Direction next = nextForSelection();

            session.setDirection(new LinkedHashSet<>(selection), next);
            selection.clear();

            return;
        }

        Map<RouteId, org.traincontrol.base.TilePorts.Route> routes = session.getRoutes(tile);

        if (routes.isEmpty()) return;

        for (RouteId routeId : routes.keySet())
        {
            session.cycleDirection(tile, routeId);
        }
    }

    /**
     * What a bulk apply should set.
     *
     * Whatever the first selected tile would cycle to, so a bulk apply behaves like clicking one tile -
     * the user is not asked to learn a second way of choosing a direction.
     */
    private Direction nextForSelection()
    {
        TileKey first = selection.iterator().next();

        Map<RouteId, org.traincontrol.base.TilePorts.Route> routes = session.getRoutes(first);

        if (routes.isEmpty()) return Direction.BOTH;

        RouteId routeId = routes.keySet().iterator().next();

        switch (session.getGraph().getDirection(first, routeId))
        {
            case BOTH: return Direction.TOWARD_A;
            case TOWARD_A: return Direction.TOWARD_B;
            case TOWARD_B: return Direction.NONE;
            default: return Direction.BOTH;
        }
    }

    /**
     * Names a sensor and says whether it is a station.
     *
     * Only a sensor can be either, which is the model's own rule rather than one invented here: a Point
     * refuses to be a destination without one.
     */
    private void applyPoint(TileKey tile, LayoutDiagramComponent component)
    {
        if (component == null || !component.isFeedback())
        {
            say(hint, I18n.t("autosetup.ui.labelPointNotStation"));
            return;
        }

        promptName(tile);

        int station = JOptionPane.showConfirmDialog(this,
            I18n.t("autosetup.ui.menuDesignateStation"), I18n.t("autosetup.ui.title"),
            JOptionPane.YES_NO_OPTION);

        session.setStation(tile, station == JOptionPane.YES_OPTION);
    }

    /**
     * Everything a Point can be, on one dialog.
     *
     * These are the distinctions autonomy actually runs on - whether a train may be SENT here, whether
     * one arriving must leave the way it came, whether it turns round here - and none of them had any
     * way to be set at all. They are per configuration, because they are what one configuration varies
     * against another over the same track.
     *
     * "Reversing station" is not a fourth thing: it is a station that is also a reversing point, which
     * is why they are checkboxes rather than a list to choose one from.
     */
    private void pointProperties(TileKey tile)
    {
        if (session.getReducer() == null || !session.getReducer().getPoints().containsKey(tile))
        {
            JOptionPane.showMessageDialog(this, I18n.t("autosetup.ui.errorNotAPoint"));
            return;
        }

        JPanel panel = new JPanel(new GridLayout(0, 1, 2, 2));

        JCheckBox station = check(panel, "autosetup.ui.labelIsStation",
            session.getStore().isStation(tile));
        JCheckBox terminus = check(panel, "autosetup.ui.labelIsTerminus", flag(tile, "terminus"));
        JCheckBox reversing = check(panel, "autosetup.ui.labelIsReversing", flag(tile, "reversing"));

        // active defaults to true when nothing has been stored, matching parseAuto
        Object storedActive = session.getPointProperty(tile, "active");
        JCheckBox active = check(panel, "autosetup.ui.labelIsActive",
            storedActive == null || Boolean.TRUE.equals(storedActive));

        javax.swing.JTextField maxLength = field(panel, "autosetup.ui.labelMaxTrainLength",
            number(tile, "maxTrainLength", 0));
        javax.swing.JTextField multiplier = field(panel, "autosetup.ui.labelSpeedMultiplier",
            number(tile, "speedMultiplier", 100));
        javax.swing.JTextField priority = field(panel, "autosetup.ui.labelPriority",
            number(tile, "priority", 0));

        // Locomotive exclusions and homes, which the graph window used to own and which had no home at
        // all after it went.  A multi-select list rather than free text: the names have to match the
        // roster exactly or the exclusion silently does nothing.
        javax.swing.JList<String> excluded = locomotiveList(panel, "autosetup.ui.labelExcludedLocs",
            strings(tile, "excludedLocs"));

        javax.swing.JList<String> home = locomotiveList(panel, "autosetup.ui.labelHomeFor",
            strings(tile, "home"));

        String name = session.getStore().getPointName(tile);

        if (JOptionPane.showConfirmDialog(this, new JScrollPane(panel),
            I18n.f("autosetup.ui.titlePointProperties",
                name == null || name.trim().isEmpty() ? tile.toString() : name),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        session.setStation(tile, station.isSelected());

        // Stored only when set, so a configuration file records decisions rather than every default.
        session.setPointProperty(tile, "terminus", terminus.isSelected() ? Boolean.TRUE : null);
        session.setPointProperty(tile, "reversing", reversing.isSelected() ? Boolean.TRUE : null);
        session.setPointProperty(tile, "active", active.isSelected() ? null : Boolean.FALSE);

        session.setPointProperty(tile, "maxTrainLength", parse(maxLength, 0));
        session.setPointProperty(tile, "speedMultiplier", parse(multiplier, 100));
        session.setPointProperty(tile, "priority", parse(priority, 0));

        session.setPointProperty(tile, "excludedLocs", selected(excluded));
        session.setPointProperty(tile, "home", selected(home));
    }

    private JCheckBox check(JPanel panel, String key, boolean value)
    {
        JCheckBox box = new JCheckBox(I18n.t(key), value);
        panel.add(AutonomyViewerPanel.styled(box, false));
        return box;
    }

    private javax.swing.JTextField field(JPanel panel, String key, int value)
    {
        panel.add(AutonomyViewerPanel.styled(new JLabel(I18n.t(key)), false));

        javax.swing.JTextField text = new javax.swing.JTextField(String.valueOf(value));
        panel.add(AutonomyViewerPanel.styled(text, false));

        return text;
    }

    private javax.swing.JList<String> locomotiveList(JPanel panel, String key, Set<String> chosen)
    {
        panel.add(AutonomyViewerPanel.styled(new JLabel(I18n.t(key)), false));

        javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();

        List<String> names = locomotiveNames == null
            ? java.util.Collections.<String>emptyList() : locomotiveNames.get();

        for (String name : names) model.addElement(name);

        javax.swing.JList<String> list = new javax.swing.JList<>(model);
        list.setVisibleRowCount(4);
        AutonomyViewerPanel.styled(list, false);

        List<Integer> indexes = new java.util.ArrayList<>();

        for (int i = 0; i < names.size(); i++)
        {
            if (chosen.contains(names.get(i))) indexes.add(i);
        }

        int[] selection = new int[indexes.size()];

        for (int i = 0; i < indexes.size(); i++) selection[i] = indexes.get(i);

        list.setSelectedIndices(selection);

        panel.add(new JScrollPane(list));

        return list;
    }

    private boolean flag(TileKey tile, String key)
    {
        return Boolean.TRUE.equals(session.getPointProperty(tile, key));
    }

    private int number(TileKey tile, String key, int fallback)
    {
        Object value = session.getPointProperty(tile, key);

        return value instanceof Number ? ((Number) value).intValue() : fallback;
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
     * @return the number, or null when it is the default and so not worth storing
     */
    private Object parse(javax.swing.JTextField field, int ignoreWhen)
    {
        try
        {
            int value = Integer.parseInt(field.getText().trim());

            return value == ignoreWhen ? null : value;
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private Object selected(javax.swing.JList<String> list)
    {
        List<String> chosen = list.getSelectedValuesList();

        return chosen.isEmpty() ? null : new org.json.JSONArray(chosen);
    }

    private void promptName(TileKey tile)
    {
        String current = session.getStore().getPointName(tile);

        String name = JOptionPane.showInputDialog(this,
            I18n.t("autosetup.ui.promptPointName"), current == null ? "" : current);

        if (name == null) return;

        if (name.contains("\""))
        {
            // Point strips quotes from names, so a name containing one would change under the user
            JOptionPane.showMessageDialog(this, I18n.t("autosetup.ui.warnQuotesStrippedFromName"));
            name = name.replace("\"", "");
        }

        session.setPointName(tile, name);
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

    /**
     * The portal tool: name the link, then pick its partner from the list.
     */
    private void applyPortal(TileKey tile, LayoutDiagramComponent component)
    {
        if (component == null || !(component.isLink() || component.getType()
            == LayoutDiagramComponent.componentType.TUNNEL))
        {
            say(hint, I18n.t("autosetup.ui.infoUnnamedLinkNotConnected"));
            return;
        }

        promptLinkName(tile);
        pairFromList(tile);
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
            say(hint, I18n.t("autosetup.ui.promptTestDestination"));
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
        java.util.List<org.traincontrol.base.GraphReducer.ReducedEdge> there =
            session.getReducer() == null ? null : session.getReducer().findPath(testFrom, tile);

        java.util.List<org.traincontrol.base.GraphReducer.ReducedEdge> back =
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

    private String leg(java.util.List<org.traincontrol.base.GraphReducer.ReducedEdge> run)
    {
        return run == null ? I18n.t("autosetup.ui.testLegBlocked")
            : I18n.f("autosetup.ui.testLegReachable", run.size());
    }

    private void outline(java.util.List<org.traincontrol.base.GraphReducer.ReducedEdge> run,
        TileKey from, TileKey to)
    {
        if (run == null) return;

        testPath.add(from);
        testPath.add(to);

        for (org.traincontrol.base.GraphReducer.ReducedEdge edge : run)
        {
            for (org.traincontrol.base.GraphReducer.TileStep step : edge.getPath())
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
    public org.traincontrol.base.TileAnnotation annotationFor(TileKey tile)
    {
        java.util.List<org.traincontrol.base.TileAnnotation.Mark> marks = new java.util.ArrayList<>();

        boolean ignored = isIgnored(tile);

        if (showDirections.isSelected() && session.getGraph() != null && !ignored)
        {
            for (Map.Entry<RouteId, org.traincontrol.base.TilePorts.Route> entry
                : session.getRoutes(tile).entrySet())
            {
                org.traincontrol.base.TilePorts.Route route = entry.getValue();

                Direction direction = session.getGraph().getDirection(tile, entry.getKey());

                // A route the hardware restricts is one-way whatever the user chose: the graph leaves the
                // authored direction BOTH there (see defaultDirection), but a train still cannot pass
                // against the blades, and the drawing has to say what a train can actually do.
                if (route.getDirectedToward() != null && direction != Direction.NONE)
                {
                    direction = route.getDirectedToward() == route.getA()
                        ? Direction.TOWARD_A : Direction.TOWARD_B;
                }

                // Only what RESTRICTS a train is drawn.  Track that runs both ways is the overwhelming
                // majority of a layout and is also the default, so drawing it put an arrow on every
                // square and left the handful of real decisions with nothing to stand out against.
                // Bidirectional track is now simply unmarked - the same information, read by absence.
                if (direction == Direction.BOTH && !showAllDirections.isSelected()) continue;

                marks.add(new org.traincontrol.base.TileAnnotation.Mark(
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

        boolean station = session.getStore().isStation(tile);

        String name = session.getStore().getPointName(tile);

        return new org.traincontrol.base.TileAnnotation(marks, length, outlined, station,
            name != null && !name.trim().isEmpty(), ignored, isMuted(tile));
    }

    /**
     * Whether a tile should be pushed back rather than drawn at full strength.
     *
     * Signals only.  They sit on almost every run, their art is the heaviest on the diagram, and
     * autonomy commands them green as a matter of course - so at full strength they read as the most
     * important thing on a page where they are usually the least interesting.  Still configurable:
     * a signal can be restricted like any other tile, and a restriction on one still draws.
     */
    private boolean isMuted(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagramComponent component =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        return component != null && component.isSignal();
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

        if (component == null) return false;

        return org.traincontrol.base.TilePorts.isDisqualified(component.getType())
            || org.traincontrol.base.TilePorts.isTransparent(component.getType());
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

        findingsModel.clear();
        findingTiles.clear();

        int errors = 0;

        // Only this page.  An editor window shows one page, and a finding about a different one cannot
        // be acted on here - it would just be a line the reader has to learn to skip.  The whole-layout
        // view lives in the Auto tab, grouped by page.
        for (org.traincontrol.base.TileGraph.Problem problem : session.getGraph() == null
            ? java.util.Collections.<org.traincontrol.base.TileGraph.Problem>emptyList()
            : session.getGraph().getProblems())
        {
            if (!onThisPage(problem.getTile())) continue;

            if (problem.isBlocking()) errors++;

            findingsModel.addElement(describe(problem.getMessageKey(),
                problem.getTile() == null ? "" : problem.getTile().toString()));
            findingTiles.add(problem.getTile());
        }

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (!onThisPage(finding.getTile())) continue;

            if (finding.getSeverity() == AutonomyChecks.Severity.ERROR) errors++;

            findingsModel.addElement(describe(finding.getMessageKey(), finding.getSubject()));
            findingTiles.add(finding.getTile());
        }

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

        for (org.traincontrol.base.GraphReducer.ReducedPoint point
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
    public void setOnReveal(java.util.function.Consumer<TileKey> onReveal)
    {
        this.onReveal = onReveal;
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
