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
        panel.add(row(toolButton(Tool.TEST, I18n.t("autosetup.ui.toolTest"))));

        // the toggles change what is drawn, not what is decided, so all they do is redraw
        showDirections.addActionListener(e -> refresh());
        showAllDirections.addActionListener(e -> refresh());
        showLengths.addActionListener(e -> refresh());

        panel.add(row(control(showDirections)));
        panel.add(row(control(showAllDirections)));
        panel.add(row(control(showLengths)));

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

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        boolean isPoint = session.getReducer() != null
            && session.getReducer().getPoints().containsKey(target);

        title(menu, isPoint ? pointTitle(target) : component == null
            ? target.getX() + "," + target.getY() : component.getUserFriendlyTypeName());

        if (isPoint)
        {
            menu.add(item(I18n.t("autolayout.ui.menuRenamePoint"), () -> promptName(target)));

            menu.addSeparator();

            // The four designations, in the graph window's own order and words.
            menu.add(toggle(I18n.t("autolayout.ui.markAsStation"),
                "autolayout.ui.tooltip.Station",
                session.getStore().isStation(target), on -> setStation(target, on)));

            menu.add(toggle(I18n.t("autolayout.ui.checkboxMarkTerminusStation"),
                "autolayout.ui.tooltip.TerminusStation", flag(target, "terminus"),
                on -> session.setPointProperty(target, "terminus", on ? Boolean.TRUE : null)));

            menu.add(toggle(I18n.t("autolayout.ui.checkboxMarkReversingPoint"),
                "autolayout.ui.tooltip.ReversingPoint", flag(target, "reversing"),
                on -> session.setPointProperty(target, "reversing", on ? Boolean.TRUE : null)));

            // "Active" in the model is what the user calls parking: autonomy will not choose it and
            // will not start a train standing there, while a route picked by hand still may.
            menu.add(toggle(I18n.t("autolayout.ui.checkboxActive"),
                "autosetup.ui.hintParking",
                !Boolean.FALSE.equals(session.getPointProperty(target, "active")),
                on -> session.setPointProperty(target, "active", on ? null : Boolean.FALSE)));

            menu.addSeparator();

            menu.add(item(I18n.t("autolayout.ui.menuSpeedMultiplier"),
                () -> promptNumber(target, "speedMultiplier",
                    "autolayout.ui.promptEnterSpeedMultiplier", 100)));

            menu.add(item(I18n.t("autolayout.ui.menuEditAdvancedParameters"),
                () -> promptNumber(target, "maxTrainLength",
                    "autolayout.ui.promptEnterMaxTrainLength", 0)));

            menu.add(item(I18n.t("autosetup.ui.labelPriority"),
                () -> promptNumber(target, "priority",
                    "autolayout.ui.promptEnterStationPriority", 0)));

            javax.swing.JMenuItem excluded = item(I18n.t("autolayout.ui.menuExcludedLocomotives"),
                () -> promptLocomotives(target, "excludedLocs"));
            excluded.setToolTipText(I18n.t("autolayout.ui.tooltip.ExcludedLocomotives"));
            menu.add(excluded);

            menu.add(item(I18n.t("autosetup.ui.labelHomeFor"),
                () -> promptLocomotives(target, "home")));

            menu.addSeparator();
        }

        Map<RouteId, org.traincontrol.base.TilePorts.Route> routes = session.getRoutes(target);

        if (!routes.isEmpty())
        {
            boolean many = routes.size() > 1;

            for (Map.Entry<RouteId, org.traincontrol.base.TilePorts.Route> entry : routes.entrySet())
            {
                org.traincontrol.base.TilePorts.Route route = entry.getValue();

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
            say(hint, I18n.t("autosetup.ui.promptOneWayTo"));
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

        menu.show(invoker, x, y);
    }

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
        });

        return menuItem;
    }

    private void setStation(TileKey tile, boolean on)
    {
        session.setStation(tile, on);

        // A sensor demoted back to a plain point keeps no designation nobody can see any more.
        if (!on)
        {
            session.setPointProperty(tile, "terminus", null);
            session.setPointProperty(tile, "active", null);
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

        String entered = JOptionPane.showInputDialog(this, I18n.t(promptKey),
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

    /**
     * Asks which locomotives, as a multi-select list rather than typed names - a name that does not
     * match the roster exactly would silently do nothing.
     */
    private void promptLocomotives(TileKey tile, String key)
    {
        List<String> names = locomotiveNames == null
            ? java.util.Collections.<String>emptyList() : locomotiveNames.get();

        if (names.isEmpty())
        {
            JOptionPane.showMessageDialog(this, I18n.t("error.noLocs"));
            return;
        }

        javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();

        for (String name : names) model.addElement(name);

        javax.swing.JList<String> list = new javax.swing.JList<>(model);
        list.setVisibleRowCount(Math.min(10, names.size()));
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
            // Set on the run, not the tile: a run of plain track has one direction, and setting it a
            // tile at a time is both busywork and a way to end up with a run that contradicts itself.
            session.setRunDirection(tile, routeId, direction);
            refresh();
        });

        return item;
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

    private boolean flag(TileKey tile, String key)
    {
        return Boolean.TRUE.equals(session.getPointProperty(tile, key));
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
     * Asks what to call a sensor.  Quotes are stripped, because Point strips them itself and a name
     * carrying one would silently change under the user.
     */
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

        Map<RouteId, org.traincontrol.base.TilePorts.Route> routes = session.getRoutes(target);

        if (routes.isEmpty()) return;

        if (routes.size() > 1)
        {
            say(hint, I18n.t("autosetup.ui.infoPickABranch"));
            return;
        }

        Map.Entry<RouteId, org.traincontrol.base.TilePorts.Route> only =
            routes.entrySet().iterator().next();

        org.traincontrol.base.TilePorts.Route route = only.getValue();

        Direction next;

        switch (session.getGraph().getDirection(target, only.getKey()))
        {
            case BOTH: next = Direction.TOWARD_A; break;
            case TOWARD_A: next = Direction.TOWARD_B; break;
            case TOWARD_B: next = Direction.NONE; break;
            default: next = Direction.BOTH; break;
        }

        session.setRunDirection(target, only.getKey(), next);

        say(hint, I18n.f("autosetup.ui.cycledTo", describeTile(target), describe(next, route)));
    }

    /**
     * What a direction means, in words, naming the side rather than an A or a B nobody can see.
     */
    private String describe(Direction direction, org.traincontrol.base.TilePorts.Route route)
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

        // A tile that merely follows its run draws nothing of its own and is washed out, so the run
        // reads as one decision made at one end rather than eleven waiting to be made.
        boolean follower = isFollower(tile);

        if (showDirections.isSelected() && session.getGraph() != null && !ignored && !follower)
        {
            Map<RouteId, org.traincontrol.base.TilePorts.Route> routes = session.getRoutes(tile);

            // more than one route means a switch, a crossing or a double curve - somewhere a train has
            // a choice, and somewhere the user needs to see every option rather than only the closed
            boolean branching = routes.size() > 1;

            for (Map.Entry<RouteId, org.traincontrol.base.TilePorts.Route> entry
                : routes.entrySet())
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

        return new org.traincontrol.base.TileAnnotation(marks, length, outlined,
            badgeFor(tile), ignored, isMuted(tile) || follower);
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
    private org.traincontrol.base.TileAnnotation.Badge badgeFor(TileKey tile)
    {
        // Every sensor that made it into the graph gets a badge, not only the stations.  A plain point
        // is a thing the user has decided NOT to make a station, and it should look like a decision
        // rather than like an ordinary tile nobody has reached yet.
        if (session.getReducer() == null
            || !session.getReducer().getPoints().containsKey(tile)) return null;

        String name = session.getStore().getPointName(tile);

        org.traincontrol.base.TilePorts.Route route = firstRoute(tile);

        return new org.traincontrol.base.TileAnnotation.Badge(
            session.getStore().isStation(tile),
            flag(tile, "terminus"),
            flag(tile, "reversing"),
            Boolean.FALSE.equals(session.getPointProperty(tile, "active")),
            name != null && !name.trim().isEmpty(),
            route == null ? null : route.getA(),
            route == null ? null : route.getB());
    }

    /**
     * The tile's first route, which is where its badge is drawn.
     */
    private org.traincontrol.base.TilePorts.Route firstRoute(TileKey tile)
    {
        Map<RouteId, org.traincontrol.base.TilePorts.Route> routes = session.getRoutes(tile);

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
        runLeaders = session.runLeaders();

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
