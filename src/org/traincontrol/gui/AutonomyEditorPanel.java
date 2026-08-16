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

    private Tool tool = Tool.CONNECTIONS;

    private final JLabel banner = new JLabel();
    private final JLabel hint = new JLabel();
    private final DefaultListModel<String> findingsModel = new DefaultListModel<>();
    private final JList<String> findings = new JList<>(findingsModel);

    private final JCheckBox showDirections = new JCheckBox(I18n.t("autosetup.ui.btnShowDirections"), true);
    private final JCheckBox showLengths = new JCheckBox(I18n.t("autosetup.ui.btnShowLengths"), false);

    // Portal pairing takes two clicks, and the first is remembered here
    private TileKey pendingPortal;

    // Bulk selection, so a one-way run is set in one gesture rather than forty
    private final Set<TileKey> selection = new LinkedHashSet<>();

    // The path test also takes two clicks; the first end and the last found route live here
    private TileKey testFrom;
    private final Set<TileKey> testPath = new LinkedHashSet<>();

    /**
     * @param session the setup being edited
     * @param onChanged run after every edit, so the diagram can redraw itself
     */
    public AutonomyEditorPanel(AutonomySession session, Runnable onChanged)
    {
        this.session = session;
        this.onChanged = onChanged;

        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        add(buildTools(), BorderLayout.NORTH);
        add(buildFindings(), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);

        refresh();
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
        showLengths.addActionListener(e -> refresh());

        panel.add(showDirections);
        panel.add(showLengths);

        hint.setFont(hint.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
        panel.add(hint);

        banner.setOpaque(true);
        banner.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        panel.add(banner);

        return panel;
    }

    private JToggleButton toolButton(final Tool which, String text, ButtonGroup group, boolean selected)
    {
        JToggleButton button = new JToggleButton(text, selected);

        button.setFocusable(false);
        button.addActionListener(e ->
        {
            tool = which;
            pendingPortal = null;
            selection.clear();
            testFrom = null;
            testPath.clear();

            if (which == Tool.TEST) hint.setText(I18n.t("autosetup.ui.promptTestStart"));

            refresh();
        });

        group.add(button);

        return button;
    }

    private JScrollPane buildFindings()
    {
        findings.setVisibleRowCount(8);

        JScrollPane scroll = new JScrollPane(findings);
        scroll.setBorder(BorderFactory.createTitledBorder(I18n.t("autosetup.ui.colWarnings")));
        scroll.setPreferredSize(new Dimension(260, 160));

        return scroll;
    }

    private JPanel buildActions()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        JButton check = new JButton(I18n.t("autosetup.ui.btnCheckConfiguration"));
        check.addActionListener(e -> refresh());
        panel.add(check);

        JButton save = new JButton(I18n.t("autosetup.ui.btnApply"));
        save.addActionListener(e -> save());
        panel.add(save);

        return panel;
    }

    private JLabel heading(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        return label;
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
    public void tileClicked(TileKey tile, LayoutDiagramComponent component, boolean addToSelection)
    {
        if (tile == null || session.getGraph() == null) return;

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
            hint.setText(I18n.t("autosetup.ui.labelPointNotStation"));
            return;
        }

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

        int station = JOptionPane.showConfirmDialog(this,
            I18n.t("autosetup.ui.menuDesignateStation"), I18n.t("autosetup.ui.title"),
            JOptionPane.YES_NO_OPTION);

        session.setStation(tile, station == JOptionPane.YES_OPTION);
    }

    /**
     * Names a link, then pairs it with the next one clicked.
     *
     * Two clicks rather than a dialog listing candidates, because the second click can be on another
     * page - and the CS2 file records only which page a link points at, never which tile, which is why
     * this has to be authored at all.
     */
    private void applyPortal(TileKey tile, LayoutDiagramComponent component)
    {
        if (component == null || !(component.isLink() || component.getType()
            == LayoutDiagramComponent.componentType.TUNNEL))
        {
            hint.setText(I18n.t("autosetup.ui.infoUnnamedLinkNotConnected"));
            return;
        }

        if (pendingPortal == null)
        {
            String current = session.getStore().getLinkName(tile);

            String name = JOptionPane.showInputDialog(this,
                I18n.t("autosetup.ui.promptLinkName"), current == null ? "" : current);

            if (name != null) session.setLinkName(tile, name);

            pendingPortal = tile;
            hint.setText(I18n.t("autosetup.ui.tooltipGestures"));

            return;
        }

        if (pendingPortal.equals(tile))
        {
            // clicking the same one again releases it rather than pairing it with itself
            session.unpairPortal(tile);
            pendingPortal = null;
            return;
        }

        session.pairPortals(pendingPortal, tile);
        pendingPortal = null;
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
            hint.setText(I18n.t("autosetup.ui.labelPointNotStation"));
            return;
        }

        if (testFrom == null)
        {
            testFrom = tile;
            testPath.clear();
            hint.setText(I18n.t("autosetup.ui.promptTestDestination"));
            return;
        }

        java.util.List<org.traincontrol.base.GraphReducer.ReducedEdge> run =
            session.getReducer() == null ? null : session.getReducer().findPath(testFrom, tile);

        testPath.clear();

        if (run == null)
        {
            hint.setText(I18n.t("autosetup.ui.testUnreachable"));
        }
        else
        {
            // outline every tile the run covers, ends included, so the answer is on the track itself
            testPath.add(testFrom);
            testPath.add(tile);

            for (org.traincontrol.base.GraphReducer.ReducedEdge edge : run)
            {
                for (org.traincontrol.base.GraphReducer.TileStep step : edge.getPath())
                {
                    testPath.add(step.getTile());
                }
            }

            hint.setText(I18n.f("autosetup.ui.testReachable", run.size()));
        }

        testFrom = null;
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

        if (showDirections.isSelected() && session.getGraph() != null)
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

                marks.add(new org.traincontrol.base.TileAnnotation.Mark(
                    route.getA(), route.getB(), direction));
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

        return new org.traincontrol.base.TileAnnotation(marks, length, outlined);
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
        findingsModel.clear();

        List<AutonomyChecks.Finding> found = session.check();

        int errors = 0;

        for (AutonomyChecks.Finding finding : found)
        {
            if (finding.getSeverity() == AutonomyChecks.Severity.ERROR) errors++;

            findingsModel.addElement(describe(finding));
        }

        if (errors > 0)
        {
            banner.setText(errors + " " + I18n.t("autosetup.ui.colWarnings"));
            banner.setBackground(new java.awt.Color(255, 210, 210));
        }
        else
        {
            banner.setText(session.getReducer().getPoints().size() + " / "
                + session.getReducer().getEdges().size());
            banner.setBackground(new java.awt.Color(214, 245, 214));
        }

        if (!selection.isEmpty())
        {
            hint.setText(I18n.f("autosetup.ui.labelTilesSelected", selection.size()));
        }

        if (onChanged != null) onChanged.run();
    }

    private String describe(AutonomyChecks.Finding finding)
    {
        String message;

        try
        {
            message = I18n.f(finding.getMessageKey(), finding.getSubject());
        }
        catch (RuntimeException e)
        {
            // a key that has not reached the bundles yet should not blank the whole list
            message = finding.getMessageKey() + " " + finding.getSubject();
        }

        return message;
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
