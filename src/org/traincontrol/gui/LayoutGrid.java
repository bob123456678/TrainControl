package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomy.DiagramTileRegistry;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.widgets.AddressLabel;
import org.traincontrol.gui.widgets.AxisRuler;
import org.traincontrol.gui.widgets.LoadingSpinner;
import org.traincontrol.gui.widgets.StationCaption;
import org.traincontrol.util.I18n;

/**
 * This class represents the model for a track diagram
 * @author Adam
 */
public class LayoutGrid
{
    private LayoutLabel[][] grid;
    private final java.util.IdentityHashMap<LayoutLabel, int[]> coordinates = new java.util.IdentityHashMap<>();
    public final int maxWidth;
    public final int maxHeight;
    
    // Should the .text property be rendered in non-empty cells?
    public static final boolean ALLOW_TEXT_ANYWHERE = true;
    
    /** A station with nothing standing on it: an em dash, which says "nothing here" (FR-028). */
    public static final String LAYOUT_STATION_EMPTY = "\u2014";
    /** A train passing through a station it was not sent to: "something is here and not stopping" (FR-028). */
    public static final String LAYOUT_STATION_OCCUPIED = "\u2022\u2022\u2022";
    public static final int LAYOUT_STATION_MAX_LENGTH = 10;
    public static final int LAYOUT_STATION_OPACITY = 210;

    /**
     * The pill under a home locomotive not standing at its home (MT-261): dark, so the name turns white; translucent.
     */
    public static final Color AWAY_FROM_HOME_FILL = new Color(60, 60, 60, LAYOUT_STATION_OPACITY);

    /** Whether the column and row numbers are drawn: exactly when the grid is (OB-179). */
    public static boolean coordinatesVisible()
    {
        return LayoutEditor.showGrid();
    }

    /**
     * Whether station captions are left off this diagram entirely (FR-030): in the track diagram editor, and on a page
     * excluded from autonomy.  `testEditorSurfaceRules` reads the call, to check the caller passes the right THREE
     * booleans.
     *
     * @param pageExcluded whether this page is excluded from the active autonomy configuration; decides the result
     * outright
     */
    public static boolean hidesStationCaptions(boolean inEditor, boolean autonomyMode,
        boolean pageExcluded)
    {
        return pageExcluded || (inEditor && !autonomyMode);
    }

    /**
     * Lets a station caption be dragged to another square in the autonomy editor (FR-035); release runs `moveCaption`.
     */
    private static void dragCaption(final StationCaption caption, final javax.swing.JLabel handle,
        final LayoutLabel square, final org.traincontrol.automationui.TileGraph.TileKey on,
        final LayoutEditor editor, final Container container)
    {
        handle.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));

        final java.awt.Point[] began = new java.awt.Point[1];
        final boolean[] dragging = new boolean[1];

        // A press on a train drawn over the caption drags the train, not the caption (FR-160).
        final boolean[] train = new boolean[1];

        java.awt.event.MouseAdapter gesture = new java.awt.event.MouseAdapter()
        {
            // Forward the hover to the method the tiles call: the caption covers its square, so entering it would end
            // the outline.
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (handle != square) editor.receiveMoveEvent(e, square);
            }

            @Override
            public void mouseMoved(MouseEvent e)
            {
                if (handle != square) editor.receiveMoveEvent(e, square);
            }

            // A right-click belongs to the square (the caption would swallow the autonomy menu); a left click on a
            // train drawn over the name goes to a tool that takes it for its station too (OB-354).
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (handle != square && (javax.swing.SwingUtilities.isRightMouseButton(e)
                    || editor.trainClicked(e) != null))
                {
                    editor.receiveClickEvent(
                        javax.swing.SwingUtilities.convertMouseEvent(handle, e, square), square);
                }
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                // Left button only: a right-press asks about the square (menu from `mouseClicked`).
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e))
                {
                // Clear both: clearing only `began` would leave `dragging` true and freeze the ghost.
                    began[0] = null;
                    dragging[0] = false;

                    editor.hideCaptionGhost();
                    editor.clearCaptionDropTarget();

                    return;
                }

                train[0] = editor.pressTrain(e);

                if (train[0])
                {
                    began[0] = null;
                    dragging[0] = false;

                    return;
                }

                began[0] = e.getPoint();
                dragging[0] = false;
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                // The square's own listener already carries a train it was pressed on; the caption over it hands on.
                if (train[0])
                {
                    if (handle != square) editor.dragTrain(e);

                    return;
                }

                if (began[0] == null) return;

                if (!dragging[0] && began[0].distance(e.getPoint()) < DRAG_SLOP) return;

                if (!dragging[0])
                {
                    dragging[0] = true;

                    pickUp(caption, handle, began[0], editor);
                }

                editor.moveCaptionGhost(javax.swing.SwingUtilities.convertPoint(
                    handle, e.getPoint(), editor.getLayeredPane()));

                LayoutLabel over = tileUnder(container, handle, e);

                // The mark asks the drop's own question, so green is exactly what will be accepted (OB-057, OB-090).
                editor.showCaptionDropTarget(over, over != null && editor.getAutonomyPanel() != null
                    && editor.getAutonomyPanel().canDropCaption(on, tileOf(editor, over, on),
                        over.getComponent()));
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (train[0])
                {
                    train[0] = false;

                    if (handle != square && javax.swing.SwingUtilities.isLeftMouseButton(e)) editor.releaseTrain(e);

                    return;
                }

                // The left button finishes a drag; any other cancels it.
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e))
                {
                    began[0] = null;
                    dragging[0] = false;

                    editor.hideCaptionGhost();
                    editor.clearCaptionDropTarget();

                    return;
                }

                boolean moved = dragging[0];

                began[0] = null;
                dragging[0] = false;

                editor.hideCaptionGhost();
                editor.clearCaptionDropTarget();

                if (!moved || editor.getAutonomyPanel() == null) return;

                LayoutLabel over = tileUnder(container, handle, e);

                editor.getAutonomyPanel().moveCaption(on, tileOf(editor, over, on),
                    over == null ? null : over.getComponent());
            }
        };

        handle.addMouseListener(gesture);
        handle.addMouseMotionListener(gesture);
    }

    /** Photographs the caption cropped to its pill and hands it to the window to carry, held where grabbed (FR-035). */
    private static void pickUp(StationCaption caption, javax.swing.JLabel handle,
        java.awt.Point at, LayoutEditor editor)
    {
        java.awt.Rectangle pill = caption.drawnBounds();

        if (pill == null || pill.width <= 0 || pill.height <= 0) return;

        java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(
            pill.width, pill.height, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, 0.8f));

            g.translate(-pill.x, -pill.y);

            caption.paint(g);
        }
        finally
        {
            g.dispose();
        }

        java.awt.Point grab = handle == caption
            ? new java.awt.Point(at.x - pill.x, at.y - pill.y)
            : new java.awt.Point(pill.width / 2, pill.height / 2);

        grab.x = Math.max(0, Math.min(pill.width, grab.x));
        grab.y = Math.max(0, Math.min(pill.height, grab.y));

        editor.showCaptionGhost(shot, grab.x, grab.y);
    }

    /** A tile turned back into a square on the page, or null when it has no coordinates. */
    private static org.traincontrol.automationui.TileGraph.TileKey tileOf(LayoutEditor editor,
        LayoutLabel label, org.traincontrol.automationui.TileGraph.TileKey samePage)
    {
        if (label == null) return null;

        int x = editor.getGridX(label);
        int y = editor.getGridY(label);

        if (x < 0 || y < 0) return null;

        return new org.traincontrol.automationui.TileGraph.TileKey(samePage.getPage(), x, y);
    }

    /**
     * The tile under the pointer, asked of the tiles: captions are in front of the diagram, so Swing would name another
     * station's.
     */
    private static LayoutLabel tileUnder(Container container, java.awt.Component from, MouseEvent e)
    {
        java.awt.Point at = javax.swing.SwingUtilities.convertPoint(from, e.getPoint(), container);

        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof LayoutLabel && child.getBounds().contains(at))
            {
                // Not the spacers: a drop on one shows green yet is stored against a square the drawing loop skips.
                if (((LayoutLabel) child).isSpacer()) continue;

                return (LayoutLabel) child;
            }
        }

        return null;
    }

    /** The square under a mouse event, or null off the diagram, for a train dragged over the diagram (FR-160). */
    public static LayoutLabel squareUnder(Container container, java.awt.Component from, MouseEvent e)
    {
        return tileUnder(container, from, e);
    }

    /** How far the mouse moves before a press on a caption counts as a drag rather than a click. */
    public static final int DRAG_SLOP = 4;

    /**
     * Whether the track on this square runs up and down (FR-028), asked of the geometry as the autonomy graph asks it,
     * so a caption and the path through it cannot disagree.  A switch in its straight position answers TRUE.
     */
    public static boolean runsNorthSouth(LayoutDiagramComponent c)
    {
        if (c == null) return false;

        boolean acrossTheSquare = false;
        boolean upTheSquare = false;

        try
        {
            // A sensor's state is not a shape (OB-263): it found no ports and read a vertical station as east to west.
            for (org.traincontrol.automationui.TilePorts.Route route
                : org.traincontrol.automationui.TilePorts.ports(
                    c.getType(), c.getOrientation(), c.isFeedback() ? 0 : c.getState()))
            {
                if (route.touches(org.traincontrol.automationui.TilePorts.Side.E)
                    || route.touches(org.traincontrol.automationui.TilePorts.Side.W))
                {
                    acrossTheSquare = true;
                }

                if (route.touches(org.traincontrol.automationui.TilePorts.Side.N)
                    || route.touches(org.traincontrol.automationui.TilePorts.Side.S))
                {
                    upTheSquare = true;
                }
            }
        }
        catch (RuntimeException e)
        {
            return false;
        }

        return upTheSquare && !acrossTheSquare;
    }

    /**
     * How a station caption is spelled when a train stands on it: the name cut to LAYOUT_STATION_MAX_LENGTH, then the
     * facing arrow (FR-028).
     */
    public static String stationCaption(String name, String arrow)
    {
        if (name == null) return LAYOUT_STATION_EMPTY;

        String cut = name.substring(0, Math.min(name.length(), LAYOUT_STATION_MAX_LENGTH)).trim();

        return StationCaption.withArrow(cut, arrow);
    }

    // Component that holds the layout
    private JPanel container;

    /** The spinner this grid put into the panel, held so `discard()` can take it out again. */
    private LoadingSpinner mountedSpinner;

    /**
     * Whether this grid's container ever reached the panel; a throw before the swap leaves the old one looking live
     * (OB-128).
     */
    private boolean swapped = false;

    private JPanel spinnerPanel;

    /**
     * Whether another grid has replaced this one on the same panel; a replaced grid's timers and callbacks do nothing.
     */
    private volatile boolean discarded = false;

    /** The panel this grid was built into, emptied by `discard()` when the build never reached the swap. */
    private JPanel owner;

    private TrainControlUI window;

    /** The captions this grid was built with (MT-670): each square to its station or null, each station to its name. */
    private final java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
        org.traincontrol.automationui.TileGraph.TileKey> captionsAsBuilt = new java.util.HashMap<>();

    private final java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, String> captionNamesAsBuilt =
        new java.util.HashMap<>();

    /**
     * Each tile this grid registered with the window's registry (MT-670); the grid is kept only while all are
     * registered.
     */
    private final java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, LayoutLabel> tilesAsRegistered =
        new java.util.HashMap<>();

    /** What the grid was built of, for the same question: the page, whether in an editor, and the window it is in. */
    private String builtPage;
    private boolean builtInEditor;
    private Container builtMaster;

    /** The caption labels THIS grid registered, so discarding it hands back exactly those. */
    private final java.util.List<javax.swing.JLabel> registeredCaptions = new java.util.ArrayList<>();

    /** Where a container remembers the caption labels its grid registered, so it cannot outlive what it is about. */
    public static final String CAPTIONS_REGISTERED = "TrainControl.captionsRegistered";

    /** Makes a diagram's window scroll half a square at a time on both axes (MT-726): wheel and scroll bar arrows. */
    public static void scrollHalfASquareAtATime(javax.swing.JScrollPane pane, int size)
    {
        int step = Math.max(1, size / 2);

        pane.getVerticalScrollBar().setUnitIncrement(step);
        pane.getHorizontalScrollBar().setUnitIncrement(step);
    }

    /**
     * Where a container remembers the grid that built it, for the window to ask whether it is still the setup's
     * (MT-670).
     */
    private static final String GRID = "TrainControl.layoutGrid";

    /** The grid that built a container, or null. */
    public static LayoutGrid of(java.awt.Component container)
    {
        Object grid = container instanceof JPanel ? ((JPanel) container).getClientProperty(GRID) : null;

        return grid instanceof LayoutGrid ? (LayoutGrid) grid : null;
    }

    /**
     * The grid currently drawn into each panel, so building a new one retires the old.  Weak keys and values: a grid
     * reaches its panel.
     */
    private static final java.util.Map<JPanel, java.lang.ref.WeakReference<LayoutGrid>> LIVE =
        java.util.Collections.synchronizedMap(
            new java.util.WeakHashMap<JPanel, java.lang.ref.WeakReference<LayoutGrid>>());

    /**
     * Retires whatever grid is registered against this panel (a throwing constructor has already registered).
     * Idempotent.
     */
    public static void retire(JPanel host)
    {
        if (host == null) return;

        java.lang.ref.WeakReference<LayoutGrid> was = LIVE.get(host);

        LayoutGrid standing = was == null ? null : was.get();

        if (standing != null && !standing.isDiscarded()) standing.discard();
    }

    /** Whether this grid has been retired and should not touch its panel again. */
    public boolean isDiscarded()
    {
        return discarded;
    }

    /** Whether this grid took its panel over from one already drawn on it. */
    private boolean replacing = false;

    /** The tile size this grid was built at: a rebuild at the same size is not an arrival (MT-273). */
    private final int tileSize;

    private boolean sameSizeRebuild = false;

    private javax.swing.Timer failsafe;

    private javax.swing.Timer grace;

    /**
     * Stops this grid from touching its panel again; called on the outgoing grid before a new one is built.
     * Idempotent.
     */
    public void discard()
    {
        discarded = true;

        // Hand back this grid's caption labels, but only if the window says it is finished with: a cached container
        // registers nothing.
        if (window != null) window.forgetLayoutStations(registeredCaptions, container);

        if (container == null) return;

        container.setVisible(true);

        if (failsafe != null) failsafe.stop();
        if (grace != null) grace.stop();

        if (mountedSpinner != null && spinnerPanel != null)
        {
            spinnerPanel.remove(mountedSpinner);

            mountedSpinner = null;
        }

        // And a panel left holding a dead container from a build that threw before the swap.
        if (!swapped && owner != null)
        {
            owner.removeAll();
            owner.revalidate();
            owner.repaint();
        }
    }
    
    private boolean cacheable = false;
    
    /**
     * The panel a diagram is built into, which paints the trains last (OB-159).  Swing has no layers, and the caption
     * and the locomotive under it are on different components.  Public and static so a test can build one from plain
     * components.
     */
    public static JPanel newDiagramContainer()
    {
        return new JPanel()
        {
            @Override
            protected void paintChildren(java.awt.Graphics g)
            {
                super.paintChildren(g);

                // A station on a curve spills onto the squares beside it, drawn under the captions, trains and
                // addresses.
                java.util.Map<LayoutLabel, java.awt.Rectangle> spilt = new java.util.LinkedHashMap<>();

                for (java.awt.Component child : getComponents())
                {
                    if (child instanceof LayoutLabel && ((LayoutLabel) child).paintStationSpill(g))
                    {
                        spilt.put((LayoutLabel) child, ((LayoutLabel) child).spillReach());
                    }
                }

                // The arrows of the squares a spill reached go back over it.
                if (!spilt.isEmpty())
                {
                    // Only where the spill was drawn: arrows over the whole square would come out in front of a
                    // caption.
                    java.awt.geom.Area spilled = new java.awt.geom.Area();

                    for (LayoutLabel label : spilt.keySet())
                    {
                        java.awt.Shape shape = label.spillShapeOnTheDiagram();

                        if (shape != null) spilled.add(new java.awt.geom.Area(shape));
                    }

                    java.awt.Graphics2D under = (java.awt.Graphics2D) g.create();

                    try
                    {
                        under.clip(spilled);

                        for (java.awt.Component child : getComponents())
                        {
                            if (child instanceof LayoutLabel) ((LayoutLabel) child).paintArrowsOverSpill(under, spilt);
                        }

                        java.awt.Rectangle reach = spilled.getBounds();

                        for (java.awt.Component child : getComponents())
                        {
                            if (child instanceof StationCaption && child.isVisible() && child.getBounds().intersects(reach))
                            {
                                java.awt.Graphics over = under.create(child.getX(), child.getY(), child.getWidth(),
                                    child.getHeight());

                                try
                                {
                                    child.paint(over);
                                }
                                finally
                                {
                                    over.dispose();
                                }
                            }
                        }
                    }
                    finally
                    {
                        under.dispose();
                    }
                }

                for (java.awt.Component child : getComponents())
                {
                    if (child instanceof LayoutLabel) ((LayoutLabel) child).paintTrainOverCaptions(g);
                }

                // The addresses over the trains (FR-116), only those in what is being repainted.
                java.awt.Rectangle clip = g.getClipBounds();

                for (java.awt.Component child : getComponents())
                {
                    if (child instanceof AddressLabel && child.isVisible()
                        && (clip == null || clip.intersects(child.getBounds())))
                    {
                        java.awt.Graphics over = g.create(child.getX(), child.getY(), child.getWidth(), child.getHeight());

                        try
                        {
                            child.paint(over);
                        }
                        finally
                        {
                            over.dispose();
                        }
                    }
                }

            }

            /**
             * These children DO overlap, and Swing has to be told (OB-172): a caption reaching into the gutter would
             * paint over the numbers.
             */
            @Override
            public boolean isOptimizedDrawingEnabled()
            {
                return false;
            }

            /**
             * The axis numbers, drawn last in `paint` (OB-172): a border paints before the children, which a caption in
             * the gutter would cover.
             */
            @Override
            public void paint(java.awt.Graphics g)
            {
                super.paint(g);

                if (getBorder() instanceof AxisRuler) getBorder().paintBorder(this, g, 0, 0, getWidth(), getHeight());
            }
        };
    }

    /**
     * This class draws the train layout and ensures that proper event references are set in the model
     * @param layout reference to the layout from the model
     * @param size size of each tile, in pixels
     * @param parent panel to contain the layout
     * @param master container with the panel
     * @param popup is this layout being rendered in a separate window?
     */
    public LayoutGrid(LayoutDiagram layout, int size, JPanel parent, Container master, boolean popup, TrainControlUI ui)
    {
        // This grid inside an editor, not "an editor is open somewhere": `layout.getEdit()` is shared by both editors.
        final boolean inEditor = layout.getEdit() && master instanceof LayoutEditor;

        this.builtPage = layout.getName();
        this.builtInEditor = inEditor;
        this.builtMaster = master;

        // Before anything else touches the panel: a replaced grid's armed timers would fire into it.
        this.owner = parent;
        this.window = ui;
        this.tileSize = size;

        if (parent != null)
        {
            java.lang.ref.WeakReference<LayoutGrid> was =
                LIVE.put(parent, new java.lang.ref.WeakReference<>(this));

            LayoutGrid outgoing = was == null ? null : was.get();

            replacing = outgoing != null && outgoing != this;

            sameSizeRebuild = replacing && outgoing.tileSize == size;

            if (replacing) outgoing.discard();
        }

        // No station captions at all in the TRACK diagram editor (FR-030; see hidesStationCaptions).
        final boolean hidesCaptions = hidesStationCaptions(inEditor,
            master instanceof LayoutEditor && ((LayoutEditor) master).isAutonomyMode(),
            ui != null && ui.isPageExcludedFromAutonomy(layout.getName()));

        boolean autonomyEditor = master instanceof LayoutEditor
            && ((LayoutEditor) master).isAutonomyMode();

        // Calculate boundaries
        int offsetX = layout.getMinx();
        int offsetY = layout.getMiny();

        int width = layout.getMaxx() - layout.getMinx() + 1;
        int height = layout.getMaxy() - layout.getMiny() + 1;

        // Increment width to fix GBC ui issue
        width = width + 1;
        height = height + 1;

        // Create layout
        
        // Not emptied yet: the old diagram stays up until the swap below.
        
        // We need a non scaling panel for small layouts
        // if (width * size < parent.getWidth() || height * size < parent.getHeight() || popup)
        // {
            container = newDiagramContainer();
            container.setBackground(Color.white);

            container.putClientProperty(CAPTIONS_REGISTERED, registeredCaptions);

            container.putClientProperty(GRID, this);
            
            // Things mess up without this
            // "Being edited" is true in both windows at once, so this asks whether THIS grid is in an editor (MT-106).
            if (LayoutDiagram.IGNORE_PADDING || inEditor)
            {
                parent.setLayout(new FlowLayout());
            }
            else
            {
                // If we want to left-align smaller layouts
                parent.setLayout(new FlowLayout(FlowLayout.LEFT));
            }
            
            // We can only cache these panels
            cacheable = true;
        // }
        // else
        // {
        //     container = parent;
        // }

        // The column and row numbers, in the margin the border reserves (FR-057), numbered as the DIAGRAM is
        // (`offsetX`, `offsetY`) but told the real counts (`width` and `height` include the spacer).  Editors only,
        // with the grid on.
        if (master instanceof LayoutEditor && coordinatesVisible())
        {
            // Measured, not multiplied: the gray grid's borders add a pixel or two per cell; asked at paint time.
            final LayoutGrid drawing = this;

            // More than one row to measure from (OB-172): a column whose top square is absent would lose its number.
            java.util.List<java.util.function.IntFunction<java.awt.Rectangle>> columnSources =
                new java.util.ArrayList<>();

            java.util.List<java.util.function.IntFunction<java.awt.Rectangle>> rowSources =
                new java.util.ArrayList<>();

            for (int probe = 0; probe < Math.min(6, Math.max(width, height)); probe++)
            {
                final int which = probe;

                columnSources.add(column ->
                {
                    LayoutLabel cell = drawing.getValueAt(column, which);

                    return cell == null ? null : cell.getBounds();
                });

                rowSources.add(row ->
                {
                    LayoutLabel cell = drawing.getValueAt(which, row);

                    return cell == null ? null : cell.getBounds();
                });
            }

            container.setBorder(AxisRuler.overRows(offsetX, offsetY, width - 1, height - 1,
                columnSources, rowSources));
        }
        else
        {
            container.setBorder(null);
        }

        // Generate grid
        container.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints(); 

        // And the room the ruler asked for, added to the panel (FR-057); the editor sizes its own panel from these.
        java.awt.Insets gutter = container.getBorder() == null
            ? new java.awt.Insets(0, 0, 0, 0) : container.getBorder().getBorderInsets(container);

        // `width` and `height` include the spacer column and row that stop a long label widening its neighbors
        // (`LayoutLabel.markSpacer`).
        int fullWidth = width * size + gutter.left + gutter.right;
        int fullHeight = height * size + gutter.top + gutter.bottom;

        container.setSize(fullWidth, fullHeight);
        container.setMaximumSize(new Dimension(fullWidth, fullHeight));

        maxWidth = fullWidth;
        maxHeight = fullHeight;
               
        grid = new LayoutLabel[width][height];

        // Asked once a build and handed to each square: the editor's grid setting is an uncached registry read.
        final Boolean gridLines = inEditor ? LayoutEditor.showGrid() : null;
                       
        for(int y = 0; y < height; y++)
        {
            for(int x = 0; x < width; x++)
            {
                // GBC fix - we create a dummy column at the end with nothing in it to ensure long labels don't misalign things
                if (x == (width - 1) || y == (height - 1))
                {
                    grid[x][y] = new LayoutLabel(null, master, size, ui, false);
                    coordinates.put(grid[x][y], new int[]{x, y});

                    // Not a square, so not part of the gray grid (OB-055).
                    grid[x][y].markSpacer();
                    gbc.gridwidth = 0;
                    gbc.gridheight = 0;
                    gbc.gridx = x;
                    gbc.gridy = y;
                    container.add(grid[x][y], gbc);  
                    continue;
                }
                // End GBC fix
                
                LayoutDiagramComponent c = layout.getComponent(x + offsetX, y  + offsetY);

                // The square this cell is, and the station whose caption belongs on it (a blank square can carry one).
                final org.traincontrol.automationui.TileGraph.TileKey square =
                    new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), x + offsetX, y + offsetY);

                final org.traincontrol.automationui.TileGraph.TileKey captioned =
                    hidesCaptions ? null : (ui == null ? null : ui.autonomyCaptionAt(square));

                captionsAsBuilt.put(square, captioned);

                // The edit value ensures that the icon is disabled in edit mode, and it disables clickability/events
                grid[x][y] = new LayoutLabel(c, master, size, ui, inEditor, gridLines);
                coordinates.put(grid[x][y], new int[]{x, y});

                // The autonomy editor's trains follow its own switch (FR-119), and no other diagram's do.
                if (autonomyEditor && ((LayoutEditor) master).getAutonomyPanel() != null)
                {
                    grid[x][y].setTrainsShown(((LayoutEditor) master).getAutonomyPanel()::isShowingTrains);
                }

                // The square it is drawing, so it can ask whether a train is lying across it.  The
                // key is already built a few lines above for the caption lookup.
                grid[x][y].setSquare(square);
                gbc.anchor = GridBagConstraints.BASELINE_LEADING;

                boolean drawsText = captioned != null
                    || (c != null && (ALLOW_TEXT_ANYWHERE && c.hasLabel()
                        || !ALLOW_TEXT_ANYWHERE && c.isText()));

                if (drawsText)
                {
                    // Text labels can overflow.  This ensures that they don't widen other cells.
                    gbc.gridwidth = 0;
                }
                else
                {
                    gbc.gridwidth = 1;
                }
                
                gbc.gridx = x;
                gbc.gridy = y;
                gbc.gridheight = 1; // Height will always be 1, except if rendering text
                
                // grid[x][y].setBorder(new LineBorder(Color.BLUE, 1)); // for debugging only
                
                container.add(grid[x][y], gbc);  
                
                // Render text separately
                if (drawsText)
                {
                    StationCaption text = new StationCaption();

                    // Set by the autonomy editor's placed-train branch; read after the generic styling, which would
                    // take the translucency back.
                    boolean standingTrain = false;

                    // Whether this caption names a home locomotive that is somewhere else (MT-261): it picks the pill's
                    // fill.
                    boolean awayFromHome = false;

                    Color labelColour = Color.BLACK;
                    
                    final String own = c == null || c.getLabel() == null ? "" : c.getLabel();

                    final String captionName = captioned == null
                        ? null : ui.autonomyStationNameAt(captioned);

                    if (captioned != null) captionNamesAsBuilt.put(captioned, captionName);

                    // Whether this label is drawn at all (OB-272): in the autonomy editor a caption follows the caption
                    // dropdown and the user's own writing the text switch (FR-133); in the track editor one switch
                    // decides both.
                    final AutonomyEditorPanel captionPanel = autonomyEditor && master instanceof LayoutEditor
                        ? ((LayoutEditor) master).getAutonomyPanel() : null;

                    final boolean drawn = captioned != null && autonomyEditor && captionPanel != null
                        ? captionPanel.isDrawingCaptions() : !inEditor || !layout.getEditHideText();

                    // Autonomy station caption, live.  The user's OWN writing stays.
                    if (captioned != null && !inEditor)
                    {
                        // Blank until autonomy says otherwise: the station's name would pass for a live caption.
                        text.setText("");

                        text.putClientProperty(TrainControlUI.LAYOUT_STATION_BUILD, registeredCaptions);

                        // And of this page, so another page's build does not let it go (OB-341).
                        text.putClientProperty(TrainControlUI.LAYOUT_STATION_PAGE, layout.getName());

                        // This callback will populate the label
                        ui.addLayoutStation(captioned, text, parent);

                        registeredCaptions.add(text);
                        text.setToolTipText(captionName);

                        final org.traincontrol.automationui.TileGraph.TileKey station = captioned;

                        text.addMouseListener(new MouseAdapter()
                        {
                            // The name's own listener gets mouseExited as the pointer moves onto it, so this reports
                            // the hover for the STATION.
                            @Override
                            public void mouseEntered(MouseEvent e)
                            {
                                ui.setHoveredDiagramTile(station.getPage(), station.getX(), station.getY());
                            }

                            @Override
                            public void mouseExited(MouseEvent e)
                            {
                                ui.setHoveredDiagramTile(null, -1, -1);
                            }

                            @Override
                            public void mouseClicked(MouseEvent e)
                            {
                                // A double-click is two clicks, each activating (OB-138); no branch here opens the
                                // setup.

                                if (e.getButton() == MouseEvent.BUTTON3)
                                {
                                    LayoutRightclickAutonomyMenu.showFor(ui, station, station,
                                        e.getComponent(), e.getX(), e.getY());
                                }
                                // Left-clicking a station will activate its locomotive
                                else
                                {
                                    // By square: a square where trains may turn is several Points, none called what the
                                    // diagram says.
                                    org.traincontrol.automation.Point standing =
                                        ui.getAutonomyPointForTile(station);

                                    if (standing != null)
                                    {
                                        Locomotive atStation = standing.getCurrentLocomotive();
                                        Locomotive active = ui.getActiveLoc();

                                        if (atStation != null && !atStation.equals(active))
                                        {
                                            ui.jumpToLocomotive(atStation.getName());
                                        }
                                    }
                                }
                            }
                        });
                    }
                    // Regular labels
                    else if (drawn)
                    {
                        if (captioned != null && autonomyEditor)
                        {
                            // The station by default, the parked train on request (FR-030): this window is about
                            // arrangement.
                            boolean naming = !(master instanceof LayoutEditor)
                                || ((LayoutEditor) master).getAutonomyPanel() == null
                                || !((LayoutEditor) master).getAutonomyPanel().isShowingParkedTrains();

                            // Where the train lives, if asked (MT-261); asked first, as a caption has room for one
                            // name.
                            boolean homes = master instanceof LayoutEditor
                                && ((LayoutEditor) master).getAutonomyPanel() != null
                                && ((LayoutEditor) master).getAutonomyPanel().isShowingHomeLocomotives();

                            String home = homes ? ui.autonomyHomeAt(captioned) : null;

                            if (home != null)
                            {
                                text.setText(stationCaption(home, ""));

                                // Away is the dark fill, and `StationCaption.onPill` picks the readable foreground for
                                // it.
                                String standing = ui.autonomyLocomotiveAt(captioned);

                                boolean athome = home.equals(standing);

                                standingTrain = true;

                                labelColour = athome ? Color.BLACK : Color.WHITE;

                                awayFromHome = !athome;
                            }
                            else if (homes)
                            {
                                text.setText("");

                                text.setOpaque(false);

                                standingTrain = false;
                            }
                            else if (naming)
                            {
                                // The station's own name, grayed like the placeholder.
                                text.setText(captionName == null ? LAYOUT_STATION_EMPTY : captionName);

                                labelColour = captionName == null
                                    ? new Color(150, 150, 150) : Color.BLACK;

                                standingTrain = captionName != null;
                            }
                            else
                            // What the setup puts on this square, which is what the editor is about (there may be no
                            // run).
                            {
                            String placed = ui.autonomyCaptionTextAt(captioned);

                            if (placed != null)
                            {
                                text.setText(placed);

                                // The running diagram's own style for a named train; applied after the generic WHITE
                                // background.
                                standingTrain = true;

                                labelColour = Color.BLACK;
                            }
                            else
                            {
                                // No locomotive, no label (OB-312).
                                text.setText("");

                                text.setOpaque(false);

                                standingTrain = false;
                            }
                            }
                        }
                        else if (captioned != null)
                        {
                            // A caption the diagram cannot act on (excluded page, track editor): drawn so the square is
                            // visibly spoken for.
                            text.setText(captionName == null ? LAYOUT_STATION_EMPTY : captionName);

                            if (inEditor) labelColour = new Color(150, 150, 150);
                        }
                        else
                        {
                            // Everything else: what the user wrote, as written (OB-344).
                            text.setText(own);

                            // Writing of their own, grayed while autonomy is edited: not what the editor is for.
                            if (autonomyEditor) labelColour = new Color(150, 150, 150);
                        }
                    }


                    text.setForeground(labelColour);
                    text.setBackground(Color.WHITE);
                    // StationCaption.LABEL_FONT: one typeface for every word on a diagram (OB-116).
                    text.setFont(new Font(StationCaption.LABEL_FONT, Font.PLAIN, size / 2));

                    // A caption is a pill; writing of the user's own is not (FR-028).
                    if (captioned != null)
                    {
                        text.setPill(true);
                        text.setBackground(StationCaption.restingFill());
                        // onPill, not readableOn: it dims a neutral gray toward the pill, so a placeholder stays dimmer
                        // than a name.
                        text.setForeground(
                            StationCaption.onPill(StationCaption.restingFill(), labelColour));

                        text.setFont(text.getFont().deriveFont(
                            text.getFont().getSize2D() * StationCaption.FONT_SCALE));
                    }

                    // A halo round every label that is not a pill, so it shows against black tracks.
                    text.setHalo(!text.isPill());

                    // The one label drawn ON something: translucent so the tile art shows through.
                    if (standingTrain)
                    {
                        // A pill paints its own fill and must stay transparent.
                        text.setOpaque(!text.isPill());

                        // A home that is empty gets the dark pill (MT-261); `onPill` picks the readable foreground.
                        Color fill = text.isPill()
                            ? (awayFromHome ? AWAY_FROM_HOME_FILL : StationCaption.restingFill())
                            : new Color(255, 255, 255, LAYOUT_STATION_OPACITY);

                        text.setBackground(fill);

                        text.setForeground(
                            StationCaption.onPill(text.getBackground(), labelColour));
                    }
                    
                    // Where a pill lands, for EVERY caption: a caption on a blank square needs the placement too
                    // (OB-115).
                    if (text.isPill())
                    {
                        // Adam: "land them so that they align just below straight tracks if the track goes east to
                        // west, or centered over the track if north to south."  A border, not gbc.anchor: this cell is
                        // gridheight = 0, so SOUTH would mean the bottom of the PAGE; NORTHWEST so it does not vote on
                        // the baseline (OB-115).
                        boolean onEnd = runsNorthSouth(c);

                        text.setRotated(onEnd);

                        int offset = StationCaption.captionOffset(size, text.lineHeight());

                        // A column of room to the left to center the caption (insets cannot be negative); not at the
                        // left edge.
                        int backShift = onEnd || x == 0 ? 0 : size;

                        // And a row above for a rotated one; only one of the two is bought, or the cell would move
                        // diagonally.
                        int upShift = onEnd && y > 0 ? size : 0;

                        gbc.gridx = x - (backShift > 0 ? 1 : 0);
                        gbc.gridy = y - (upShift > 0 ? 1 : 0);

                        text.setTileGeometry(size, backShift, upShift, offset);

                        gbc.anchor = GridBagConstraints.NORTHWEST;
                    }

                    // Shift on-tile labels down
                    // Current limitation if we wanted to use borders: if you have a text element and an on-tile label in the same row,
                    // they both get shifted down by the same amount.  Therefore, do this multiline hack.
                    if (c != null && !c.isText() && drawn)
                    {
                        //text.setBorder(new EmptyBorder(16 * (size / 30), 0, 0, 0)); //top, left, bottom, right
                        gbc.gridheight = 0;

                        // A caption with a train on it is left plain: its text is set later (updateStationLabels).
                        if (!text.isPill() && !standingTrain)
                        {
                            text.setText("<html><br>" + text.getText().replaceAll(" ", "&nbsp;") + "</html>");
                        }

                        // Show the correct cursor
                        if (c.isClickable() && !inEditor) text.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }
                    else
                    {
                        //text.setBorder(new EmptyBorder(5 * (size / 30), 0, 0, 0)); //top, left, bottom, right
                        // 11 * (size / 30) at left to center
                        gbc.gridheight = 0;    
                    }
                    
                    // Draggable, in the autonomy editor, on a square carrying a caption (FR-035) whose labels are drawn
                    // (OB-139).
                    if (autonomyEditor && captioned != null && master instanceof LayoutEditor
                        && drawn)
                    {
                        org.traincontrol.automationui.TileGraph.TileKey here =
                            new org.traincontrol.automationui.TileGraph.TileKey(
                                layout.getName(), x, y);

                        // The station this label NAMES, usually not the square it is on.
                        text.setToolTipText(captionName);

                        dragCaption(text, text, grid[x][y], here, (LayoutEditor) master, container);
                        dragCaption(text, grid[x][y], grid[x][y], here, (LayoutEditor) master,
                            container);
                    }

                    container.add(text, gbc);
                    container.setComponentZOrder(text, 0);

                    // And back to this square's own column and row: centring moves gbc, which the address label reuses.
                    gbc.gridx = x;
                    gbc.gridy = y;
                }
                
                // Show address labels
                if (c != null &&
                        layout.getShowAddress() &&
                        !c.isText() && c.isClickable())
                {
                    AddressLabel text = new AddressLabel();
                    
                    // Cascade click event
                    final JLabel outer = grid[x][y];
                    
                    if (!inEditor)
                    {
                        text.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }
                   
                    text.addMouseListener(new MouseAdapter()
                    {
                        @Override
                        public void mouseClicked(MouseEvent e)
                        {
                            // Manually trigger the mouseClicked event of grid[x][y]
                            for (MouseListener listener : outer.getMouseListeners())
                            {
                                listener.mouseClicked(e);
                            }
                        }
                        
                        @Override
                        public void mouseEntered(MouseEvent e)
                        {
                            // Manually trigger the mouseClicked event of grid[x][y]
                            for (MouseListener listener : outer.getMouseListeners())
                            {
                                listener.mouseEntered(e);
                            }
                        }

                        // And its exit: the label covers the top left of its square, so the track editor's outline
                        // would stay.
                        @Override
                        public void mouseExited(MouseEvent e)
                        {
                            for (MouseListener listener : outer.getMouseListeners())
                            {
                                listener.mouseExited(e);
                            }
                        }
                    });

                    text.setForeground(Color.RED);
                    text.setFont(new Font("Segoe UI", Font.PLAIN, size / 3)); 
                    
                    // To avoid a bug where feedback doesn't yet exist, turn off tooltips in the editor
                    if (!inEditor)
                    {
                        text.setToolTipText(c.toSimpleString());
                    }
                    
                    //text.setBorder(new EmptyBorder(16 * (size / 30), 0, 0, 0)); //top, left, bottom, right
                    gbc.gridheight = 0;
                    gbc.anchor = GridBagConstraints.NORTHWEST;
                    
                    // For uncouplers, show the precise address
                    String redOrGreen = "";
                    
                    if (c.isUncoupler())
                    {
                        if (c.isLogicalGreen())
                        {
                            redOrGreen = "g";
                        }
                        else
                        {
                            redOrGreen = "r";
                        }
                    }
                    
                    // Add the protocol
                    String protocol = "";

                    if (c.getProtocol() != null && c.getProtocol() != Accessory.accessoryDecoderType.MM2)
                    {
                        protocol = c.getProtocol().toString().toLowerCase();
                    }
                    
                    text.setLines(c.isLink() && c.getLinkPageName() != null ? c.getLinkPageName()
                        : c.getLogicalAddress() + redOrGreen, protocol);

                    // To the row's end, as text labels are: an arrow's page name is wider than a square and would
                    // widen its column, leaving a blank gap in the diagram.
                    gbc.gridwidth = GridBagConstraints.REMAINDER;

                    container.add(text, gbc);
                    container.setComponentZOrder(text, 0);
                }
                                                                              
                // Which square this label is, told to the label for every label: its menu and the shortcuts ask it.
                if (ui != null) grid[x][y].setAutonomySquare(layout.getName(), x + offsetX, y + offsetY);

                // Register the tile with autonomy, if it is watching.
                if (c != null && ui != null)
                {

                    if (ui.getDiagramTileRegistry() != null)
                    {
                        org.traincontrol.automationui.TileGraph.TileKey tile =
                            new org.traincontrol.automationui.TileGraph.TileKey(layout.getName(), c.getX(), c.getY());

                        ui.getDiagramTileRegistry().register(tile, grid[x][y]);

                        tilesAsRegistered.put(tile, grid[x][y]);
                    }
                }

                // Set references for each tile accessory
                if (c != null)
                {
                    // If popup is true, LayoutLabel.isParentVisible will be used to clean up stale label references
                    if ((c.isSwitch() || c.isSignal()) && c.getAccessory() != null)
                    {
                        c.getAccessory().addTile(grid[x][y]);
                    }
                    
                    if (c.isFeedback() && c.getFeedback() != null)
                    {
                        c.getFeedback().addTile(grid[x][y]);
                    }
                    
                    if (c.isThreeWay() && c.getAccessory2() != null)
                    {
                        c.getAccessory2().addTile(grid[x][y]); 
                    }          
                    
                    if (c.isRoute() && c.getRoute() != null)
                    {
                        c.getRoute().addTile(grid[x][y]);
                    }
                }
            }
        }     
        
        if (!container.equals(parent))
        {
            // One pass, so the panel is never empty.
            parent.removeAll();

            parent.add(container);

            swapped = true;

            showWhenTilesAreReady(parent, ui);
        } 
    }

    /** Holds the diagram back until its track is decoded, showing a spinner meanwhile; only when there is a wait. */
    private void showWhenTilesAreReady(JPanel parent, TrainControlUI ui)
    {
        if (ui == null) return;

        // Nothing to wait for (OB-109): hiding a track editor rebuild would blink.  Asked HERE, before the labels are
        // built.
        if (ui.tilesAreSettled()) return;

        // A diagram already on screen is not taken away (OB-109) unless the tile size changed; a rebuild at the same
        // size is left alone entirely (MT-273).
        if (sameSizeRebuild) return;

        if (!replacing) container.setVisible(false);

        final LoadingSpinner spinner = new LoadingSpinner();

        // Sized to the space the diagram will take (OB-129), so nothing jumps on the swap.
        spinner.setPreferredSize(new Dimension(maxWidth, maxHeight));

        final boolean[] revealed = {false};

        final Runnable reveal = () ->
        {
            if (revealed[0] || discarded) return;

            revealed[0] = true;

            parent.remove(spinner);

            mountedSpinner = null;

            container.setVisible(true);

            parent.revalidate();
            parent.repaint();
        };

        ui.whenTilesSettled(reveal);

        // Revealed anyway after a few seconds: a signal that never arrives must not hide the diagram for good.
        failsafe = new javax.swing.Timer(8000, e -> reveal.run());

        failsafe.setRepeats(false);
        failsafe.start();

        grace = new javax.swing.Timer(120, e ->
        {
            if (revealed[0] || discarded) return;

            // Idempotent on a grid hidden up front; matters for a replacement.
            container.setVisible(false);

            parent.add(spinner);

            mountedSpinner = spinner;
            spinnerPanel = parent;

            parent.revalidate();
            parent.repaint();
        });

        grace.setRepeats(false);
        grace.start();
    } 
    
    /**
     * Whether the setup still gives every square the caption it was built with (MT-670); false for a grid with no
     * window to ask.
     */
    public boolean captionsAreAsBuilt()
    {
        if (window == null || builtPage == null || captionsAsBuilt.isEmpty()) return false;

        boolean hides = hidesStationCaptions(builtInEditor,
            builtMaster instanceof LayoutEditor && ((LayoutEditor) builtMaster).isAutonomyMode(),
            window.isPageExcludedFromAutonomy(builtPage));

        for (java.util.Map.Entry<org.traincontrol.automationui.TileGraph.TileKey,
            org.traincontrol.automationui.TileGraph.TileKey> cell : captionsAsBuilt.entrySet())
        {
            org.traincontrol.automationui.TileGraph.TileKey now = hides ? null : window.autonomyCaptionAt(cell.getKey());

            if (!java.util.Objects.equals(now, cell.getValue())) return false;

            if (now != null && !java.util.Objects.equals(window.autonomyStationNameAt(now), captionNamesAsBuilt.get(now)))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Whether every tile this grid registered is registered still (MT-670); false where one was let go or there is no
     * registry.
     */
    public boolean tilesAreStillRegistered()
    {
        DiagramTileRegistry registry = window == null ? null : window.getDiagramTileRegistry();

        if (registry == null) return false;

        for (java.util.Map.Entry<org.traincontrol.automationui.TileGraph.TileKey, LayoutLabel> tile
            : tilesAsRegistered.entrySet())
        {
            java.util.Set<LayoutLabel> here = registry.labelsFor(tile.getKey());

            if (here == null || !here.contains(tile.getValue())) return false;
        }

        return true;
    }

    /** Whether this grid draws that page at that size. */
    public boolean draws(String page, int size)
    {
        return page != null && page.equals(builtPage) && size == tileSize;
    }

    /**
     * Return the container that was generated
     */
    public JPanel getContainer()
    {
        return container;
    }
    
    public boolean isCacheable()
    {
        return cacheable;
    }
    
    /**
     * Gets the coordinates of the specified layout label
     */
    public int[] getCoordinates(LayoutLabel target)
    {
        int[] at = coordinates.get(target);

        return at == null ? new int[]{-1, -1} : at.clone();
    }
    
    public LayoutLabel getValueAt(int x, int y)
    {
        if (x < 0 || x >= grid.length || y < 0 || y >= grid[x].length)
        {
            return null;
        }
        
        return grid[x][y];
    }
    
    /**
    * Retrieves a specific column from the grid.
    * @param colIndex The index of the column to retrieve.
    * @return A List of LayoutLabel objects representing the row.
    * @throws IndexOutOfBoundsException if the colIndex is invalid.
    */
    public List<LayoutLabel> getColumn(int colIndex)
    {
       if (colIndex < 0 || colIndex >= grid.length)
       {
            throw new IndexOutOfBoundsException(
                I18n.f("error.columnIndexOutOfBounds", colIndex)
            );
       }
       
       return Arrays.asList(grid[colIndex]);
    }

   /**
    * Retrieves a specific column from the grid.
    * @param rowIndex The index of the column to retrieve.
    * @return A List of LayoutLabel objects representing the column.
    * @throws IndexOutOfBoundsException if the columnIndex is invalid.
    */
    public List<LayoutLabel> getRow(int rowIndex)
    {
        if (grid.length == 0 || rowIndex < 0 || rowIndex >= grid[0].length)
        {
            throw new IndexOutOfBoundsException(
                I18n.f("error.rowIndexOutOfBounds", rowIndex)
            );
        }

        List<LayoutLabel> column = new ArrayList<>();
        for (LayoutLabel[] grid1 : grid)
        {
            column.add(grid1[rowIndex]);
        }

        return column;
    }
}
