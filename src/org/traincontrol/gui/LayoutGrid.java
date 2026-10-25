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
    public final int maxWidth;
    public final int maxHeight;
    
    // Should the .text property be rendered in non-empty cells?
    public static final boolean ALLOW_TEXT_ANYWHERE = true;
    
    /** A station with nothing standing on it: an em dash, which says "nothing here" (FR-028). */
    public static final String LAYOUT_STATION_EMPTY = "\u2014";
    /**
     * A train passing through a station it was not sent to: three dots, "something is here and not stopping" (FR-028).
     */
    public static final String LAYOUT_STATION_OCCUPIED = "\u2022\u2022\u2022";
    public static final int LAYOUT_STATION_MAX_LENGTH = 10;
    public static final int LAYOUT_STATION_OPACITY = 210;

    /**
     * The pill under a home locomotive that is not standing at its home (MT-261).  A dark fill, because the name turns
     * white by the color behind it (`StationCaption.onPill`); translucent so the tile art shows through.
     */
    public static final Color AWAY_FROM_HOME_FILL = new Color(60, 60, 60, LAYOUT_STATION_OPACITY);

    /**
     * Whether the column and row numbers are drawn: exactly when the grid is (OB-179).  Adam: "coordinates should
     * always be on with the grid, off without, and the menu option gone."  Holds no preference of its own; the retired
     * key is described at `TrainControlUI` and its stored value is deliberately not read.
     *
     * @return true when the numbers should be drawn
     */
    public static boolean coordinatesVisible()
    {
        return LayoutEditor.showGrid();
    }

    /**
     * Whether station captions are left off this diagram entirely (FR-030): in the track diagram editor, and on a page
     * excluded from autonomy, whose caption would name nothing and is never in the registry both visibility switches
     * walk.  A method so its truth table can be asked without a window; `testEditorSurfaceRules` reads the call, to
     * check the caller passes the right THREE booleans.
     *
     * @param inEditor whether this grid is inside an editor at all
     * @param autonomyMode whether that editor is the autonomy one
     * @param pageExcluded whether this page is excluded from the active autonomy configuration; decides the result
     * outright
     * @return true when no caption should be drawn
     */
    public static boolean hidesStationCaptions(boolean inEditor, boolean autonomyMode,
        boolean pageExcluded)
    {
        return pageExcluded || (inEditor && !autonomyMode);
    }

    /**
     * Lets a station caption be dragged to another square in the autonomy editor (FR-035).  Installed on the pill and
     * on the square beneath it; release runs `moveCaption`, as the right-click menu does, so the two agree about where
     * a name may go.  A drag is told from a click by distance.
     */
    private static void dragCaption(final StationCaption caption, final javax.swing.JLabel handle,
        final LayoutLabel square, final org.traincontrol.automationui.TileGraph.TileKey on,
        final LayoutEditor editor, final Container container)
    {
        // At rest, not only while dragging: the pointer says a thing can be picked up before the drag starts.
        handle.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));

        final java.awt.Point[] began = new java.awt.Point[1];
        final boolean[] dragging = new boolean[1];

        java.awt.event.MouseAdapter gesture = new java.awt.event.MouseAdapter()
        {
            // Forward the hover to the method the tiles call: the caption is stacked over its square, so moving onto
            // the name sends the square a mouseExited and the outline would go out.  Only from the caption.
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

            // A right-click belongs to the square: the caption covers its middle and would swallow the autonomy menu.
            // Hand it to `receiveClickEvent` with the event converted to the square's coordinates.  A left click on a
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
                // Left button only: a right-press asks about the square (menu from `mouseClicked`, which a drag
                // suppresses).
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e))
                {
                // Clear both, not just the start: clearing only `began` leaves `dragging` true, freezing the ghost
                // while the right release still commits the move.  Pressing the other button mid-drag cancels.
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
                if (began[0] == null) return;

                // Below this distance a press is a click, which both listeners already answer.
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

                // Put down first: a release arrives whether or not a drag started, and the floating label would stay.
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

    /**
     * Photographs the caption cropped to its pill (its cell runs to the bottom of the diagram) and hands it to the
     * window to carry, held where it was grabbed, or by the middle when the drag began on the square (FR-035).
     */
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
            // Slightly see-through, so the squares stay readable while the label is carried over them.
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

        // Held inside the picture, or the label would swing away from the pointer.
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
     * The tile under the pointer, asked of the tiles rather than of Swing: captions are z-ordered in front of the
     * diagram, so `getDeepestComponentAt` would answer with another station's name.  Null when the pointer is off the
     * diagram.
     */
    private static LayoutLabel tileUnder(Container container, java.awt.Component from, MouseEvent e)
    {
        java.awt.Point at = javax.swing.SwingUtilities.convertPoint(from, e.getPoint(), container);

        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof LayoutLabel && child.getBounds().contains(at))
            {
                // Not the spacers (the dummy row and column): a drop on one shows green yet is stored against a square
                // the drawing loop skips, so the label would vanish.
                if (((LayoutLabel) child).isSpacer()) continue;

                return (LayoutLabel) child;
            }
        }

        return null;
    }

    /** How far the mouse moves before a press on a caption counts as a drag rather than a click. */
    private static final int DRAG_SLOP = 4;

    /**
     * Whether the track on this square runs up and down rather than across (FR-028), asked of the geometry as the
     * autonomy graph asks it, so a caption and the path through it cannot disagree.  False for a crossing, a curve, or
     * no geometry.  A switch in its straight position answers TRUE: `mayCarryACaption` refuses switches, but two
     * identical switches saved in different states would otherwise be captioned differently.  Public so it can be
     * checked without a grid.
     *
     * @param c what is drawn on the square
     * @return true when the rails run north to south and nothing else
     */
    public static boolean runsNorthSouth(LayoutDiagramComponent c)
    {
        if (c == null) return false;

        boolean acrossTheSquare = false;
        boolean upTheSquare = false;

        try
        {
            // A sensor's state is not a shape (OB-263): a Central Station saves whether a train stood on it, which
            // found no ports and read a vertical station as running east to west.
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
            // A tile type the port table does not describe: leave the caption where it was, but still draw it.
            return false;
        }

        return upTheSquare && !acrossTheSquare;
    }

    /**
     * How a station caption is spelled when a train is standing on it: the name cut to LAYOUT_STATION_MAX_LENGTH, then
     * the facing arrow, no brackets (FR-028).  Shared by the running diagram and the autonomy editor so they spell it
     * alike.
     *
     * @param name the locomotive
     * @param arrow the facing arrow, already carrying its own leading space, or "" for none
     * @return the caption
     */
    public static String stationCaption(String name, String arrow)
    {
        if (name == null) return LAYOUT_STATION_EMPTY;

        String cut = name.substring(0, Math.min(name.length(), LAYOUT_STATION_MAX_LENGTH)).trim();

        // Through StationCaption, because the side the arrow goes on depends on which way it points.
        return StationCaption.withArrow(cut, arrow);
    }

    // Component that holds the layout
    private JPanel container;

    /** The spinner this grid put into the panel, held so `discard()` can take it out again. */
    private LoadingSpinner mountedSpinner;

    /**
     * Whether this grid's container ever reached the panel.  The panel is emptied and the new container added at the
     * end of the build, so a throw before then leaves the old container looking live (OB-128); `discard()` empties it
     * while false.
     */
    private boolean swapped = false;

    private JPanel spinnerPanel;

    /**
     * Whether another grid has replaced this one on the same panel.  Timers and a callback holding the panel can still
     * be due after the rebuild's `parent.removeAll()` and would act on the new grid, so a replaced grid is told and
     * they do nothing.
     */
    private volatile boolean discarded = false;

    /** The panel this grid was built into, emptied by `discard()` when the build never reached the swap. */
    private JPanel owner;

    private TrainControlUI window;

    /**
     * The captions this grid was built with: each square to the station whose caption it carries or null, and each
     * station to the name it gave (MT-670).  A setup change asks them whether rebuilding would draw anything different.
     */
    private final java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
        org.traincontrol.automationui.TileGraph.TileKey> captionsAsBuilt = new java.util.HashMap<>();

    private final java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, String> captionNamesAsBuilt =
        new java.util.HashMap<>();

    /**
     * Each tile this grid registered with the window's registry, by its square (MT-670).  The registry lets go of a tile
     * whose panel is hidden as it annotates, and only building the grid again registered it anew; a grid kept with a
     * tile let go would go on showing that square as it was, so a grid is kept only while all of them are registered.
     */
    private final java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, LayoutLabel> tilesAsRegistered =
        new java.util.HashMap<>();

    /** What the grid was built of, for the same question: the page, whether in an editor, and the window it is in. */
    private String builtPage;
    private boolean builtInEditor;
    private Container builtMaster;

    /**
     * The caption labels THIS grid registered, so discarding it hands back exactly those: one panel serves every page,
     * so forgetting by panel would forget every page's captions.
     */
    private final java.util.List<javax.swing.JLabel> registeredCaptions = new java.util.ArrayList<>();

    /**
     * Where a container remembers the caption labels its grid registered.  Hung on the container, not kept in a map, so
     * it cannot outlive what it is about; the page cache holds containers, and the window needs the labels from one.
     */
    public static final String CAPTIONS_REGISTERED = "TrainControl.captionsRegistered";

    /**
     * Makes a diagram's window scroll half a square at a time on both axes, at the size it is drawn (MT-726): each step
     * of the mouse wheel and each press of a scroll bar's arrow.  Adam: "Make it half a square at a time".  The
     * diagram's panel is not Scrollable, so without this its bars step one pixel at a time.
     *
     * @param pane the scroll pane the diagram is in
     * @param size the square's edge in pixels
     */
    public static void scrollHalfASquareAtATime(javax.swing.JScrollPane pane, int size)
    {
        int step = Math.max(1, size / 2);

        pane.getVerticalScrollBar().setUnitIncrement(step);
        pane.getHorizontalScrollBar().setUnitIncrement(step);
    }

    /**
     * Where a container remembers the grid that built it, for the window to ask whether the grid on screen is still the
     * setup's (MT-670).  Hung on the container for the reason `CAPTIONS_REGISTERED` is.
     */
    private static final String GRID = "TrainControl.layoutGrid";

    /**
     * The grid that built a container.
     *
     * @param container a container on screen, or null
     * @return its grid, or null where it is no grid's
     */
    public static LayoutGrid of(java.awt.Component container)
    {
        Object grid = container instanceof JPanel ? ((JPanel) container).getClientProperty(GRID) : null;

        return grid instanceof LayoutGrid ? (LayoutGrid) grid : null;
    }

    /**
     * The grid currently drawn into each panel, so that building a new one retires the old one: four places build a
     * grid over an existing panel, and the rule lives here rather than at each call site.  Weak keys and weak values: a
     * LayoutGrid reaches its own panel, so a strong value would keep its key alive and no entry would be collected.
     */
    private static final java.util.Map<JPanel, java.lang.ref.WeakReference<LayoutGrid>> LIVE =
        java.util.Collections.synchronizedMap(
            new java.util.WeakHashMap<JPanel, java.lang.ref.WeakReference<LayoutGrid>>());

    /**
     * Retires whatever grid is registered against this panel, whether or not the caller got a reference to it.  A
     * constructor that throws part-way has already registered (`LIVE.put` comes first), and the diagram export's
     * bracket discards `grid[0]`, which is only assigned if the constructor returned.  Idempotent; safe on a panel with
     * no grid.
     *
     * @param host the panel a grid was built into
     */
    public static void retire(JPanel host)
    {
        if (host == null) return;

        java.lang.ref.WeakReference<LayoutGrid> was = LIVE.get(host);

        LayoutGrid standing = was == null ? null : was.get();

        if (standing != null && !standing.isDiscarded()) standing.discard();
    }

    /**
     * @return whether this grid has been retired and should not touch its panel again
     */
    public boolean isDiscarded()
    {
        return discarded;
    }

    /**
     * Whether this grid took its panel over from one that was already drawn on it, read from LIVE in the constructor.
     */
    private boolean replacing = false;

    /**
     * The tile size this grid was built at, to tell whether the grid it replaced had the same one (MT-273).  A rebuild
     * at the same size is not an arrival: every tile is already cached.  A different size re-keys every image, so the
     * page really does come in square by square, which is what the hold-back and the spinner are for.
     */
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

        // Hand back the caption labels this grid registered: `addLayoutStation` prunes lazily, so a cleared caption or
        // a renamed page's square would leave its label on the retired container, keeping the previous grid reachable.
        // Only if the window says this grid is finished with (it owns the page cache): a cached container comes back
        // without registering anything, so labels dropped here would leave its captions blank.
        if (window != null) window.forgetLayoutStations(registeredCaptions, container);

        // Null only if the constructor did not reach the panel; guarded so a failure stays itself instead of an NPE
        // here.
        if (container == null) return;

        // Shown whatever state the reveal reached: a grid discarded mid-decode would stay hidden, and the page cache
        // would then show an empty diagram.
        container.setVisible(true);

        if (failsafe != null) failsafe.stop();
        if (grace != null) grace.stop();

        // And the spinner this grid mounted, if the reveal never ran to take it out; stopping the timers does not.
        if (mountedSpinner != null && spinnerPanel != null)
        {
            spinnerPanel.remove(mountedSpinner);

            mountedSpinner = null;
        }

        // And a panel left holding something this grid never replaced: a build that threw before the swap leaves the
        // previous grid's dead container.  Here rather than in a catch, because every caller builds over an existing
        // panel through this on the way to the next attempt and only one of them catches.
        if (!swapped && owner != null)
        {
            owner.removeAll();
            owner.revalidate();
            owner.repaint();
        }
    }
    
    private boolean cacheable = false;
    
    /**
     * The panel a diagram is built into, which paints the trains last (OB-159).  Adam: "it is a z order issue.  The
     * stations paint over the locomotives."  Swing has no layers, and the caption and the locomotive under it are on
     * different components: captions in front paint over the locomotive, the tile in front paints out the name
     * (OB-117).  So the children paint in their order, then every tile is asked for its train.  Public and static so a
     * test can build one from plain components.
     *
     * @return a panel that draws tiles, captions, curve spills (with their arrows and captions over them), trains,
     * addresses
     */
    public static JPanel newDiagramContainer()
    {
        return new JPanel()
        {
            @Override
            protected void paintChildren(java.awt.Graphics g)
            {
                super.paintChildren(g);

                // A station on a curve spills onto the squares beside it, drawn once every square is, since a square
                // painted after would cover it.  Under the captions, trains and addresses; the captions are drawn again
                // over it below.
                java.util.Map<LayoutLabel, java.awt.Rectangle> spilt = new java.util.LinkedHashMap<>();

                for (java.awt.Component child : getComponents())
                {
                    if (child instanceof LayoutLabel && ((LayoutLabel) child).paintStationSpill(g))
                    {
                        spilt.put((LayoutLabel) child, ((LayoutLabel) child).spillReach());
                    }
                }

                // The arrows of the squares a spill reached go back over it: they sit mid-edge, where a curve's station
                // reaches.
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
                            if (child instanceof LayoutLabel)
                            {
                                ((LayoutLabel) child).paintArrowsOverSpill(under, spilt);
                            }
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
                    if (child instanceof LayoutLabel)
                    {
                        ((LayoutLabel) child).paintTrainOverCaptions(g);
                    }
                }

                // The addresses over the trains (FR-116): an address covers only its letters, so drawn last it hides
                // nothing of the icon but the number.  Only those in what is being repainted: a hover repaints three
                // squares by three.
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
             * These children DO overlap, and Swing has to be told (OB-172): `isOptimizedDrawingEnabled` defaults to
             * true, so a dirty child is repainted without the parent, and a caption reaching into the gutter paints
             * over the numbers.  A `StationCaption` is 38 by up to 55 pixels against a 30-pixel cell.
             */
            @Override
            public boolean isOptimizedDrawingEnabled()
            {
                return false;
            }

            /**
             * The axis numbers, drawn last of all (OB-172).  In `paint` rather than `paintChildren`, which gives each
             * child its own scratch graphics.  A `Border` is painted before the children, so a caption reaching into
             * the gutter covers the numbers; painted again rather than moved out of the border, which is what reserves
             * the gutter.
             */
            @Override
            public void paint(java.awt.Graphics g)
            {
                super.paint(g);

                if (getBorder() instanceof AxisRuler)
                {
                    getBorder().paintBorder(this, g, 0, 0, getWidth(), getHeight());
                }
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
     * @param ui
     */
    public LayoutGrid(LayoutDiagram layout, int size, JPanel parent, Container master, boolean popup, TrainControlUI ui)
    {
        // This grid inside an editor, not "an editor is open somewhere": `layout.getEdit()` is the flag both editors
        // share for mutual exclusion, so it would make every grid, the viewer's included, draw the editor's way and
        // attach listeners that cast their parent to LayoutEditor.
        final boolean inEditor = layout.getEdit() && master instanceof LayoutEditor;

        this.builtPage = layout.getName();
        this.builtInEditor = inEditor;
        this.builtMaster = master;

        // Before anything else touches the panel: a replaced grid with timers still armed fires into a panel no longer
        // its own.
        this.owner = parent;
        this.window = ui;
        this.tileSize = size;

        if (parent != null)
        {
            java.lang.ref.WeakReference<LayoutGrid> was =
                LIVE.put(parent, new java.lang.ref.WeakReference<>(this));

            LayoutGrid outgoing = was == null ? null : was.get();

            replacing = outgoing != null && outgoing != this;

            // Asked before the discard, which finishes with the grid being asked about.
            sameSizeRebuild = replacing && outgoing.tileSize == size;

            if (replacing) outgoing.discard();
        }

        // No station captions at all in the TRACK diagram editor (FR-030; see hidesStationCaptions).  Ask the window
        // which editor this is: `layout.getEdit()` is true in both.  Decided before `captioned` is read, not at each of
        // the four places that draw one.
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
        // JPanel container; // no longer needed - included as class field for caching
        
        // Not emptied yet: that would leave the panel blank for the whole build; the old diagram stays up until the
        // swap below.
        
        // We need a non scaling panel for small layouts
        // if (width * size < parent.getWidth() || height * size < parent.getHeight() || popup)
        // {
            container = newDiagramContainer();
            container.setBackground(Color.white);

            // The live list, so whatever this grid registers later is reachable from the container
            container.putClientProperty(CAPTIONS_REGISTERED, registeredCaptions);

            // And the grid itself, for the window's question whether it is still the setup's (MT-670)
            container.putClientProperty(GRID, this);
            
            // Things mess up without this
            //
            // "Being edited" is true in both windows at once (the main window shares the LayoutDiagram with the
            // editor), so this asks whether THIS grid is in an editor (MT-106): with the shared flag, any repaint of
            // the viewer while an editor was open re-laid the viewer the editor's way.  The viewer's tiles stay
            // clickable because the clickability line below passes `inEditor`.
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

        // The column and row numbers, in the margin the border reserves (FR-057).  A border rather than two more rows
        // of cells (see AxisRuler), so the grid inside is exactly what it was.
        //
        // The numbers are the DIAGRAM's, not cell indices (`offsetX`/`offsetY`), so the printed number is the one an
        // issue would quote; `width` and `height` include the spacer row and column, so the ruler is told the real
        // counts.
        //
        // Editors only, not the viewer where trains are watched.  `master instanceof LayoutEditor` rather than
        // `inEditor`, the flag meaning "the diagram is editable" that merely happens to agree (`LayoutEditor.render()`
        // sets it in autonomy mode too).  And only with the grid on, through `coordinatesVisible` so the rule is read
        // in one place.
        if (master instanceof LayoutEditor && coordinatesVisible())
        {
            // Measured, not multiplied: `column * size` is right only while every cell is exactly a tile wide, and the
            // gray grid's line borders add a pixel or two per cell, so the error accumulates.  These lambdas are asked
            // at paint time, when a square's bounds are real; `grid` is filled in below for the same reason.
            final LayoutGrid drawing = this;

            // More than one row to measure from (OB-172): a column whose top square is absent or not yet given bounds
            // would lose its number.  All offered rows are the same column, so any gives the same x.
            java.util.List<java.util.function.IntFunction<java.awt.Rectangle>> columnSources =
                new java.util.ArrayList<>();

            java.util.List<java.util.function.IntFunction<java.awt.Rectangle>> rowSources =
                new java.util.ArrayList<>();

            // Six probes, compared by size rather than taken in order: buys immunity to a run of spacers at the edge.
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

        // And the room the ruler asked for, added to the panel rather than taken out of the diagram (FR-057): a border
        // paints inside the component's bounds, so sizing the panel at `width * size` would carve the gutter out of the
        // tiles.  `maxWidth` and `maxHeight` are what the EDITOR sizes its own panel from, so they carry the gutter
        // too.
        java.awt.Insets gutter = container.getBorder() == null
            ? new java.awt.Insets(0, 0, 0, 0) : container.getBorder().getBorderInsets(container);

        // `width` and `height` already include the spacer, the dummy column and row that stop an overflowing text label
        // widening its neighbors; it has no number but is a real row and column, see `LayoutLabel.markSpacer`.
        int fullWidth = width * size + gutter.left + gutter.right;
        int fullHeight = height * size + gutter.top + gutter.bottom;

        container.setSize(fullWidth, fullHeight);
        container.setMaximumSize(new Dimension(fullWidth, fullHeight));

        maxWidth = fullWidth;
        maxHeight = fullHeight;
               
        grid = new LayoutLabel[width][height];

        // Asked once a build and handed to each square: the editor's grid setting is a registry read that nothing
        // caches.
        final Boolean gridLines = inEditor ? LayoutEditor.showGrid() : null;
                       
        for(int y = 0; y < height; y++)
        {
            for(int x = 0; x < width; x++)
            {
                // GBC fix - we create a dummy column at the end with nothing in it to ensure long labels don't misalign things
                if (x == (width - 1) || y == (height - 1))
                {
                    grid[x][y] = new LayoutLabel(null, master, size, ui, false);

                    // Not a square, so not part of the gray grid (OB-055): these hold nothing, and a border would show
                    // them.
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

                // The square this cell is, and the station whose caption belongs on it.  A caption is part of the
                // autonomy setup, so it can sit on a square with no component, which is why text is drawn for a caption
                // as well.
                final org.traincontrol.automationui.TileGraph.TileKey square =
                    new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), x + offsetX, y + offsetY);

                final org.traincontrol.automationui.TileGraph.TileKey captioned =
                    hidesCaptions ? null : (ui == null ? null : ui.autonomyCaptionAt(square));

                captionsAsBuilt.put(square, captioned);

                // The edit value ensures that the icon is disabled in edit mode, and it disables clickability/events
                grid[x][y] = new LayoutLabel(c, master, size, ui, inEditor, gridLines);

                // THE AUTONOMY EDITOR'S TRAINS FOLLOW ITS OWN SWITCH (FR-119), and no other diagram's do
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
                    // A StationCaption, a JLabel that can draw itself as a pill (FR-028).  Every text label is built
                    // here and only captions become pills, which is why this is a subclass and not a separate
                    // component.
                    StationCaption text = new StationCaption();

                    // Set by the autonomy editor's placed-train branch below; read after the generic label styling,
                    // which would take the translucency back.
                    boolean standingTrain = false;

                    // Whether this caption names a home locomotive that is somewhere else (MT-261): it picks the pill's
                    // fill.
                    boolean awayFromHome = false;

                    // Black unless something below has a reason to say otherwise
                    Color labelColour = Color.BLACK;
                    
                    // What the user wrote on this square, which is a different thing from the caption
                    final String own = c == null || c.getLabel() == null ? "" : c.getLabel();

                    // The station name, for a caption to show when no train is standing on it
                    final String captionName = captioned == null
                        ? null : ui.autonomyStationNameAt(captioned);

                    if (captioned != null) captionNamesAsBuilt.put(captioned, captionName);

                    // Whether this label is drawn at all (OB-272).  In the autonomy editor a caption follows the
                    // caption dropdown and the user's own writing the text switch (FR-133); elsewhere one switch
                    // decides both.
                    final AutonomyEditorPanel captionPanel = autonomyEditor && master instanceof LayoutEditor
                        ? ((LayoutEditor) master).getAutonomyPanel() : null;

                    final boolean drawn = captioned != null && autonomyEditor && captionPanel != null
                        ? captionPanel.isDrawingCaptions() : !layout.getEditHideText();

                    // Autonomy station caption, live.  An excluded page is already gone by here: `hidesStationCaptions`
                    // decides once, earlier, and nulls `captioned`, so asking again would be dead code.  The user's OWN
                    // writing stays.
                    if (captioned != null && !inEditor)
                    {
                        // Blank until autonomy says otherwise: with nothing loaded there is no answer to give, and the
                        // station's name would pass for a live caption.
                        text.setText("");

                        // Of this build, so registering its other caption of the same station spares it
                        text.putClientProperty(TrainControlUI.LAYOUT_STATION_BUILD, registeredCaptions);

                        // And of this page, so another page's build does not let it go (OB-341)
                        text.putClientProperty(TrainControlUI.LAYOUT_STATION_PAGE, layout.getName());

                        // This callback will populate the label
                        ui.addLayoutStation(captioned, text, parent);

                        registeredCaptions.add(text);
                        text.setToolTipText(captionName);

                        final org.traincontrol.automationui.TileGraph.TileKey station = captioned;

                        text.addMouseListener(new MouseAdapter()
                        {
                            // The name is a component of its own, over the square, whose listener gets mouseExited the
                            // moment the pointer moves onto the name; so this reports the hover itself, making
                            // Control+V work over the name.  It reports the STATION, not the square the text sits on: a
                            // caption may be drawn beside it.
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
                                // A double-click is two clicks, and each activates (OB-138).  Adam: "double clicking
                                // station label in track viewer should activate that locomotive (as if it was selected
                                // on the key mappings) if it is mapped, not open the editor."  So no branch here opens
                                // the autonomy setup.

                                if (e.getButton() == MouseEvent.BUTTON3)
                                {
                                    LayoutRightclickAutonomyMenu.showFor(ui, station, station,
                                        e.getComponent(), e.getX(), e.getY());
                                }
                                // Left-clicking a station will activate its locomotive
                                else
                                {
                                    // By square: a square where trains may turn round is several Points, none called
                                    // what the diagram says.
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
                            // The station by default, the parked train on request (FR-030): this window is about how
                            // things are arranged, not what happens to be standing where right now.
                            boolean naming = !(master instanceof LayoutEditor)
                                || ((LayoutEditor) master).getAutonomyPanel() == null
                                || !((LayoutEditor) master).getAutonomyPanel().isShowingParkedTrains();

                            // Where the train lives, if that is what has been asked for (MT-261).  Asked first, as the
                            // more specific question: a caption has room for one name.  A square with no home shows
                            // nothing (below).
                            boolean homes = master instanceof LayoutEditor
                                && ((LayoutEditor) master).getAutonomyPanel() != null
                                && ((LayoutEditor) master).getAutonomyPanel().isShowingHomeLocomotives();

                            String home = homes ? ui.autonomyHomeAt(captioned) : null;

                            if (home != null)
                            {
                                text.setText(stationCaption(home, ""));

                                // Away is the dark one, which is how the name comes out white: `StationCaption.onPill`
                                // picks the foreground that reads on the fill it is given.  Home and standing here gets
                                // the resting fill.
                                String standing = ui.autonomyLocomotiveAt(captioned);

                                boolean athome = home.equals(standing);

                                standingTrain = true;

                                labelColour = athome ? Color.BLACK : Color.WHITE;

                                awayFromHome = !athome;
                            }
                            else if (homes)
                            {
                                // No home, no label (Adam: "if there are no home locomotives, just hide the label at
                                // that station, rather than still showing the station name"): nothing, not the "no
                                // name" placeholder.
                                text.setText("");

                                text.setOpaque(false);

                                standingTrain = false;
                            }
                            else if (naming)
                            {
                                // The station's own name, grayed like the placeholder: a label on the diagram, not set
                                // here.
                                text.setText(captionName == null ? LAYOUT_STATION_EMPTY : captionName);

                                labelColour = captionName == null
                                    ? new Color(150, 150, 150) : Color.BLACK;

                                standingTrain = captionName != null;
                            }
                            else
                            // What the setup puts on this square, which is what the editor is about: there may be no
                            // run, so a platform with a train assigned must still show it.
                            {
                            String placed = ui.autonomyCaptionTextAt(captioned);

                            if (placed != null)
                            {
                                text.setText(placed);

                                // The running diagram's own style for a named train: black on translucent white.
                                // Applied after the generic setBackground(WHITE) below, or it would be overwritten and,
                                // this branch also turning opacity on, paint a solid white rectangle.
                                standingTrain = true;

                                labelColour = Color.BLACK;
                            }
                            else
                            {
                                // No locomotive, no label (OB-312), as Home Locs draws nothing where there is no home.
                                text.setText("");

                                text.setOpaque(false);

                                standingTrain = false;
                            }
                            }
                        }
                        else if (captioned != null)
                        {
                            // A caption the diagram cannot act on (an excluded page, or the track diagram editor):
                            // drawn so the square is visibly spoken for; captions are edited where autonomy is.
                            text.setText(captionName == null ? LAYOUT_STATION_EMPTY : captionName);

                            if (inEditor) labelColour = new Color(150, 150, 150);
                        }
                        else
                        {
                            // Everything else: what the user wrote, including an old "Point:" label on a Central
                            // Station layout, drawn as written (OB-344).
                            text.setText(own);

                            // Writing of their own, grayed while autonomy is being edited: it says what part of the
                            // railway this is, but is not what the editor is for.
                            if (autonomyEditor) labelColour = new Color(150, 150, 150);
                        }
                    }


                    text.setForeground(labelColour);
                    text.setBackground(Color.WHITE);
                    // StationCaption.LABEL_FONT, not "Segoe UI": one typeface for every word on a diagram, with the
                    // four matched arrows (OB-116).
                    text.setFont(new Font(StationCaption.LABEL_FONT, Font.PLAIN, size / 2));

                    // A caption is a pill; writing of the user's own is not (FR-028).  `captioned` is the whole test: a
                    // yard name somebody typed stays text.
                    if (captioned != null)
                    {
                        text.setPill(true);
                        text.setBackground(StationCaption.restingFill());
                        // onPill and not readableOn: the branches above choose a gray for a placeholder and a black for
                        // a name on purpose.  onPill dims a neutral gray towards the pill, so a placeholder stays
                        // dimmer than a name (`testAPlaceholderStaysDimmerThanAName`).
                        text.setForeground(
                            StationCaption.onPill(StationCaption.restingFill(), labelColour));

                        // A tenth smaller than the diagram's other text: the pill is taller than what it holds.
                        text.setFont(text.getFont().deriveFont(
                            text.getFont().getSize2D() * StationCaption.FONT_SCALE));
                    }

                    // A halo round every label that is not a pill, the user's own writing and an old file's station
                    // label, so it shows against black tracks.
                    text.setHalo(!text.isPill());

                    // The one label drawn ON something rather than beside it: translucent so the tile art shows
                    // through.  After the WHITE above, which would overwrite it.
                    if (standingTrain)
                    {
                        // A pill paints its own fill and must stay transparent, or Swing draws the rectangle it
                        // replaces.
                        text.setOpaque(!text.isPill());

                        // A home that is empty gets the dark pill (MT-261): white ink on the pale resting pill would be
                        // invisible, so the fill changes and `onPill` below picks the readable foreground for it.
                        Color fill = text.isPill()
                            ? (awayFromHome ? AWAY_FROM_HOME_FILL : StationCaption.restingFill())
                            : new Color(255, 255, 255, LAYOUT_STATION_OPACITY);

                        text.setBackground(fill);

                        text.setForeground(
                            StationCaption.onPill(text.getBackground(), labelColour));
                    }
                    
                    // Where a pill lands, and out of the row's baseline, for EVERY caption.  Not conditional on `c !=
                    // null`: a caption on a blank square needs the placement too (OB-115), and the caption placer picks
                    // blank squares.
                    if (text.isPill())
                    {
                        // Adam: "land them so that they align just below straight tracks if the track goes east to
                        // west, or centered over the track if north to south."  A blank square has no rails, so it
                        // takes the east-west answer.
                        //
                        // A border and not gbc.anchor: this cell is gridheight = 0 (REMAINDER), so anchoring SOUTH
                        // inside it means the bottom of the PAGE.  NORTHWEST because a caption must not vote on the
                        // row's baseline (BASELINE_LEADING aligns every component anchored that way, OB-115).
                        //
                        // Rotated for track that runs up the square; the orientation is asked once, here, and the
                        // caption told.
                        boolean onEnd = runsNorthSouth(c);

                        text.setRotated(onEnd);

                        int offset = StationCaption.captionOffset(size, text.lineHeight());

                        // A column of room to the left, so the caption can be centered on its square: insets cannot be
                        // negative, so the cell moves back a column and the label pays the difference back as a left
                        // inset.  Not at the left edge, where there is no column to move into.
                        int backShift = onEnd || x == 0 ? 0 : size;

                        // And a row of room above for a rotated one, the same trick turned a quarter.  Only one of the
                        // two is bought: both would move the cell diagonally away from the square it names.
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

                        // A caption with a train on it is left as plain text: on the running diagram it is registered
                        // empty and its text set later by updateStationLabels, so it never reaches the wrap below; the
                        // editor sets the text now, and the wrap's leading <br> would make this opaque label paint a
                        // block a tile tall.
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
                    
                    // Draggable, in the autonomy editor, and only a square that carries a caption (FR-035): station
                    // labels only.  And only if the labels are actually drawn (OB-139): `captioned` exists whether or
                    // not the text layer shows, and a move cursor on an invisible label offers a drag of nothing.  The
                    // grid is rebuilt when Text Labels is toggled, so ticking it back restores the drag.
                    if (autonomyEditor && captioned != null && master instanceof LayoutEditor
                        && drawn)
                    {
                        org.traincontrol.automationui.TileGraph.TileKey here =
                            new org.traincontrol.automationui.TileGraph.TileKey(
                                layout.getName(), x, y);

                        // The station this label NAMES, usually not the square it is on: a caption goes on blank space
                        // beside its platform, so asking the square would answer "none".
                        text.setToolTipText(captionName);

                        // Both the label and the square under it (Adam: "have it fire on the label or the tile, so the
                        // mouse icon is more clearly shown"): a pill is a small target.
                        dragCaption(text, text, grid[x][y], here, (LayoutEditor) master, container);
                        dragCaption(text, grid[x][y], grid[x][y], here, (LayoutEditor) master,
                            container);
                    }

                    container.add(text, gbc);
                    container.setComponentZOrder(text, 0);

                    // And back to this square's own column and row: centring moves gbc.gridx a column left and the
                    // rotation moves gridy up, and gbc is then reused by the address label below, which pays nothing
                    // back.
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

                        // And its exit: the label covers the top left of its square, and a pointer that left from here
                        // told no square, so the track editor's outline stayed and the main window's keys still aimed
                        // at it.
                        @Override
                        public void mouseExited(MouseEvent e)
                        {
                            for (MouseListener listener : outer.getMouseListeners())
                            {
                                listener.mouseExited(e);
                            }
                        }
                    });

                    // Red letters on a white halo, not in a box; see AddressLabel.
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
                    
                    // The address, and the protocol on a second line where it is not MM2
                    text.setLines(c.getLogicalAddress() + redOrGreen, protocol);
                    
                    container.add(text, gbc);
                    container.setComponentZOrder(text, 0);
                }
                                                                              
                // Which square this label is, told to the label itself, for every label including blank ones: its
                // right-click menu asks it rather than the main window (which names the wrong page in a popup), and the
                // keyboard shortcuts need it for a blank label, which is where a station's name is usually drawn.
                if (ui != null)
                {
                    grid[x][y].setAutonomySquare(layout.getName(), x + offsetX, y + offsetY);
                }

                // Register the tile with autonomy, if it is watching.  Here because only this place knows both the page
                // and the square.
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
            // One pass: the old diagram comes off and the new one goes on with no layout between, so the panel is never
            // empty.
            parent.removeAll();

            parent.add(container);

            swapped = true;

            showWhenTilesAreReady(parent, ui);
        } 
    }

    /**
     * Holds the diagram back until its track has been decoded, showing a spinner meanwhile, so the labels do not seem
     * to float on nothing.  Only when there is a wait: the spinner is mounted only if the tiles are still not ready
     * later.
     */
    private void showWhenTilesAreReady(JPanel parent, TrainControlUI ui)
    {
        if (ui == null) return;

        // Nothing to wait for, so nothing to hide (OB-109): every placement in the track diagram editor rebuilds the
        // grid, and hiding it until the next EDT pass would blink.  Asked HERE and not earlier: LayoutLabel counts a
        // decode before submitting it, so a grid asking after building its labels cannot see zero while its own work is
        // on the way.
        if (ui.tilesAreSettled()) return;

        // And a diagram already on screen is not taken away to be rebuilt (OB-109): the hold-back is for a page
        // arriving.  A replacement at a different tile size re-keys every image and does arrive square by square, so it
        // waits with the spinner; if the rebuild is quick the reveal gets there first.
        //
        // A rebuild at the same size is left alone entirely (MT-273): `replacing` stops the hiding here, but the grace
        // timer below would still fire and take the page away for a decode the just-placed tile keeps outstanding.
        if (sameSizeRebuild) return;

        if (!replacing) container.setVisible(false);

        final LoadingSpinner spinner = new LoadingSpinner();

        // Sized to the space the diagram will take, so nothing jumps on the swap and the glass lands in the middle
        // (OB-129): the parent is a FlowLayout, which aligns to the top.  The glass's drawn size does not follow it
        // (MAX_GLASS_H).
        spinner.setPreferredSize(new Dimension(maxWidth, maxHeight));

        final boolean[] revealed = {false};

        final Runnable reveal = () ->
        {
            // Nothing from a grid that has been replaced.  See discard().
            if (revealed[0] || discarded) return;

            revealed[0] = true;

            parent.remove(spinner);

            mountedSpinner = null;

            container.setVisible(true);

            parent.revalidate();
            parent.repaint();
        };

        ui.whenTilesSettled(reveal);

        // And revealed anyway after a few seconds, whatever the count says: a signal that never arrives would hide the
        // diagram for the rest of the session, a far worse fault than the flicker the hold-back removes.
        failsafe = new javax.swing.Timer(8000, e -> reveal.run());

        failsafe.setRepeats(false);
        failsafe.start();

        // A short grace period before the spinner is shown at all - see above
        grace = new javax.swing.Timer(120, e ->
        {
            if (revealed[0] || discarded) return;

            // Idempotent on a grid hidden up front; matters for a replacement, left showing and now slow enough for a
            // spinner.
            container.setVisible(false);

            parent.add(spinner);

            // Remembered so discard() can take it out - see the field.
            mountedSpinner = spinner;
            spinnerPanel = parent;

            parent.revalidate();
            parent.repaint();
        });

        grace.setRepeats(false);
        grace.start();
    } 
    
    /**
     * Whether the setup still gives every square this grid drew the caption it was built with, and every caption its
     * name, so building the grid again would draw the same one (MT-670).
     *
     * @return true where nothing about the captions has changed; false for a grid built with no window to ask
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
     * Whether every tile this grid registered is registered still, so what the window tells the registry reaches every
     * square of it (MT-670).
     *
     * @return false where one has been let go, or there is no registry to ask
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

    /**
     * @param page a page's name
     * @param size a tile size
     * @return whether this grid draws that page at that size
     */
    public boolean draws(String page, int size)
    {
        return page != null && page.equals(builtPage) && size == tileSize;
    }

    /**
     * Return the container that was generated
     * @return 
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
     * @param target
     * @return 
     */
    public int[] getCoordinates(LayoutLabel target)
    {
        for (int x = 0; x < grid.length; x++)
        {
            for (int y = 0; y < grid[x].length; y++)
            {
                if (grid[x][y] == target)
                {
                    return new int[]{x, y};
                }
            }
        }

        return new int[]{-1, -1};
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
