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
    
    /**
     * A station with nothing standing on it: an em dash, which says "nothing here" (FR-028).  The pill is the
     * caption's shape, so the text only has to say what the platform holds.
     */
    public static final String LAYOUT_STATION_EMPTY = "\u2014";
    /**
     * A train passing through a station it was not sent to: three dots say "something is here, and it is not
     * stopping" without looking like a placeholder somebody forgot to fill in (FR-028).
     */
    public static final String LAYOUT_STATION_OCCUPIED = "\u2022\u2022\u2022";
    public static final int LAYOUT_STATION_MAX_LENGTH = 10;
    public static final int LAYOUT_STATION_OPACITY = 210;

    /**
     * The pill under a home locomotive that is not standing at its home (MT-261 ruling 2).
     *
     * Adam: "labels show the home locomotive in white to indicate it's not there."  The name turns white by the
     * colour BEHIND it (`StationCaption.onPill` picks a foreground that reads on whatever fill it is handed), so
     * this is a dark fill rather than a white foreground.  Translucent like its sibling, so the tile art shows
     * through.
     */
    public static final Color AWAY_FROM_HOME_FILL = new Color(60, 60, 60, LAYOUT_STATION_OPACITY);

    /**
     * Whether the column and row numbers are drawn at all: exactly when the grid is (OB-179).
     *
     * Adam: **"coordinates should always be on with the grid, off without, and the menu option gone."**  A ruler
     * round a diagram with no grid numbers nothing, and a grid whose squares cannot be named is half a grid.
     *
     * It is one setting, not two combined: this holds no preference of its own and delegates to the grid, so the
     * ruler's call site reads as the rule.  The retired preference key is described at `TrainControlUI`; its stored
     * value is deliberately left on the operator's machine and not read.
     *
     * @return true when the numbers should be drawn
     */
    public static boolean coordinatesVisible()
    {
        return LayoutEditor.showGrid();
    }

    /**
     * Whether station captions are left off this diagram entirely (FR-030).
     *
     * Adam: "in the track diagram editor, hide autonomy labels completely."  That editor is about where the rails
     * are, and a caption there is an autonomy object drawn over the square being moved, which cannot be edited from
     * that window.  The RUNNING diagram keeps them, and so does the autonomy editor, where they are set.
     *
     * A page left out of autonomy draws none either (B6, Adam: "hide captions"): the graph is built without that
     * page, so a caption there would name nothing, and neither visibility switch could reach it because both walk
     * the registry of captions, which an excluded page's caption is never in.
     *
     * A method rather than a line of `&&`, so the rule can be asked its truth table without building a window.
     * `testEditorSurfaceRules` reads the call, to check the caller passes the right THREE booleans.
     *
     * @param inEditor whether this grid is inside an editor at all
     * @param autonomyMode whether that editor is the autonomy one
     * @param pageExcluded whether this page is excluded from the active autonomy configuration - the argument that
     *                     decides the result outright
     * @return true when no caption should be drawn
     */
    public static boolean hidesStationCaptions(boolean inEditor, boolean autonomyMode,
        boolean pageExcluded)
    {
        return pageExcluded || (inEditor && !autonomyMode);
    }

    /**
     * Lets one station caption be dragged to another square (FR-035).
     *
     * Adam: "in the autonomy editor ONLY, make it possible to move around station labels (only) by clicking and
     * dragging them", and then: "have it fire on the label or the tile, so the mouse icon is more clearly shown.
     * Snapshot the label so users can see it is being moved."
     *
     * Installed TWICE per caption, on the pill and on the square beneath it, so the thing being moved and the thing
     * the events arrive on are separate parameters.  What happens on release is `moveCaption`, the caption command
     * the right-click menu already runs, so a dragged label cannot fall out of step with the menu about where a
     * name may go.  A drag is told from a CLICK by distance, because a caption answers a double-click and a square
     * a plain one.
     *
     * @param caption the label being moved, and the thing photographed
     * @param handle what the pointer takes hold of - the label itself, or the square under it
     * @param square the square under the caption, which is what the hover outline is drawn on
     * @param on the square the caption sits on
     * @param editor the window, for the drop mark, the floating copy, and the coordinates
     * @param container the grid, whose tiles are searched for the one under the pointer
     */
    private static void dragCaption(final StationCaption caption, final javax.swing.JLabel handle,
        final LayoutLabel square, final org.traincontrol.automationui.TileGraph.TileKey on,
        final LayoutEditor editor, final Container container)
    {
        // At REST, not only while dragging.  The pointer is how the diagram says a thing can be picked
        // up, and saying it only once the drag has started is saying it too late.
        handle.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));

        final java.awt.Point[] began = new java.awt.Point[1];
        final boolean[] dragging = new boolean[1];

        java.awt.event.MouseAdapter gesture = new java.awt.event.MouseAdapter()
        {
            // The blue outline follows the pointer onto the NAME (Adam: "make sure hovering the label triggers the
            // cell hover effect").  A caption is stacked on top of its square, so moving onto the name sends the
            // square a mouseExited and the outline goes out.  It is forwarded to the method the tiles call, so
            // there is one appearance to keep right.  Only from the caption: on the square it already happens.
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

            // A RIGHT-CLICK belongs to the square, not to the name lying on top of it.  The caption covers the
            // middle of its square and takes every click there, so the autonomy menu would not open where people
            // aim.  It is handed to `receiveClickEvent`, the method the tile's own listener calls, so the menu is
            // the same menu.  The event is CONVERTED first: the menu opens at the coordinates it is given, and the
            // caption's are not the square's.
            //
            // AND A LEFT CLICK ON A TRAIN drawn over the name, to a tool that takes it for its station (OB-354):
            // the editor finds the train's square.
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
                // THE LEFT BUTTON ONLY.  A right-press asks about the square: the menu opens from `mouseClicked`,
                // which a drag would suppress, and the label must not be picked up and dropped wherever the cursor
                // wanders.  `mouseClicked` makes the same `isRightMouseButton` distinction.
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e))
                {
                    // BOTH cleared, not just the start point.  Clearing only `began` would leave `dragging` true,
                    // so a right-press part way through a left-drag would freeze the ghost and the drop mark
                    // (`mouseDragged` returns on a null start) while the right RELEASE still committed the move.
                    // Pressing the other button mid-drag is how a person cancels.
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

                // Far enough to mean it.  Below this a press is a click, and both of these answer
                // clicks already.
                if (!dragging[0] && began[0].distance(e.getPoint()) < DRAG_SLOP) return;

                if (!dragging[0])
                {
                    dragging[0] = true;

                    pickUp(caption, handle, began[0], editor);
                }

                editor.moveCaptionGhost(javax.swing.SwingUtilities.convertPoint(
                    handle, e.getPoint(), editor.getLayeredPane()));

                LayoutLabel over = tileUnder(container, handle, e);

                // The mark asks the DROP's own question, so what is shown green is exactly what will
                // be accepted.  A highlight with a rule of its own is the fault OB-057 and OB-090 were
                // both filed for: an affordance that offers what the action then refuses.
                editor.showCaptionDropTarget(over, over != null && editor.getAutonomyPanel() != null
                    && editor.getAutonomyPanel().canDropCaption(on, tileOf(editor, over, on),
                        over.getComponent()));
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                // The LEFT button finishes a drag; any other one cancels it.  Without this test a release of some
                // other button would read `dragging` and commit the drop.
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

                // Put down FIRST, and unconditionally.  A release arrives whether or not a drag ever
                // started, and a floating label left on the drag layer would sit over the diagram with
                // nothing moving it.
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
     * Photographs the caption and hands it to the window to carry (FR-035).
     *
     * Cropped to the PILL. A caption's cell runs to the bottom of the diagram, so a picture of the
     * whole component would be mostly empty and the part you can see would hang a long way from the
     * pointer.
     *
     * Taken hold of where it was grabbed when the pointer is on the label itself, and by the middle
     * when the drag started on the square - where there is no corresponding spot.
     *
     * @param caption the label being moved
     * @param handle what the pointer took hold of
     * @param at where on the handle it was taken hold of
     * @param editor the window that carries the floating copy
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
            // Slightly see-through, so the diagram underneath stays readable while something is being
            // carried over it - the point of the drag is to choose a square, and the label must not
            // hide the squares.
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

        // Held INSIDE the picture whichever way it started: a grab point outside it would swing the
        // label away from the pointer instead of hanging it off the spot it was taken by.
        grab.x = Math.max(0, Math.min(pill.width, grab.x));
        grab.y = Math.max(0, Math.min(pill.height, grab.y));

        editor.showCaptionGhost(shot, grab.x, grab.y);
    }

    /**
     * A tile turned back into a square on the page, or null when it has no coordinates.
     *
     * @param editor the window, which knows where its labels sit
     * @param label the tile
     * @param samePage any square on the page being looked at, for its name
     * @return the square, or null
     */
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
     * The TILE under the pointer, asked of the tiles rather than of Swing.
     *
     * `getDeepestComponentAt` answers with whatever is topmost, and captions are deliberately
     * z-ordered in front of the diagram - so a drag passing over another station's name would be told
     * the pointer was over that name rather than over the square beneath it. The tiles are the only
     * components that cover the page without overlapping each other, which makes them the right thing
     * to ask.
     *
     * @param container the grid
     * @param from the component the event arrived on
     * @param e the event
     * @return the tile under the pointer, or null when the pointer is off the diagram
     */
    private static LayoutLabel tileUnder(Container container, java.awt.Component from, MouseEvent e)
    {
        java.awt.Point at = javax.swing.SwingUtilities.convertPoint(from, e.getPoint(), container);

        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof LayoutLabel && child.getBounds().contains(at))
            {
                // NOT the spacers, which are not squares.  The grid ends in a dummy row and column that exist to
                // stop long labels misaligning it, and they are LayoutLabels with real bounds.  A label dropped on
                // one would be accepted (the drop mark shows GREEN, the spacer carrying no component) and stored
                // against a square the drawing loop skips, so it would vanish.  The strip along the bottom runs
                // under the last row of track, exactly where a station on that row would be dropped.
                if (((LayoutLabel) child).isSpacer()) continue;

                return (LayoutLabel) child;
            }
        }

        return null;
    }

    /** How far the mouse moves before a press on a caption counts as a drag rather than a click. */
    private static final int DRAG_SLOP = 4;

    /**
     * Whether the track on this square runs up and down rather than across (FR-028).
     *
     * Asked of the geometry rather than of the tile's name or its rotation number: the same question the autonomy
     * graph asks when it works out which ways a train may leave a square, so a caption and the path drawn through it
     * cannot disagree about which way the rails go.
     *
     * False for a crossing or a curve, and false when the geometry cannot be had at all: those squares have no
     * single direction for a caption to be placed against, and the answer for them is where captions have always
     * been.
     *
     * NOT false for a switch: in its straight position it offers one route north to south, so it answers TRUE.
     * Little reaches that today because `mayCarryACaption` refuses captions on switches and signals, but two
     * identical switches saved in different states would be captioned differently.
     *
     * Public so that its answer for a straight rail, and for the same rail turned a quarter, can be checked without
     * building a grid, as `stationCaption` below can be: where a caption goes is a question about geometry.
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
            // A SENSOR'S STATE IS NOT A SHAPE (OB-263): it has one state in the port table, and the state a Central Station
            // saves for it is whether a train stood on it - 1 found no ports, and a vertical station saved occupied read
            // as running east to west, its caption flat
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
            // A tile type the port table does not describe.  Not knowing which way the rails run is a
            // fine reason to leave the caption where it was, and no reason at all to stop drawing it.
            return false;
        }

        return upTheSquare && !acrossTheSquare;
    }

    /**
     * How a station caption is spelled when a train is standing on it: a name cut to LAYOUT_STATION_MAX_LENGTH, then
     * the facing arrow.
     *
     * No brackets (FR-028): the pill is the caption's shape and the width belongs to it.  Defined here so the running
     * diagram and the autonomy editor, which draw the same placements, spell it alike; a bare untruncated name would
     * be as wide as the name and cover the tiles either side.
     *
     * @param name the locomotive
     * @param arrow the facing arrow, already carrying its own leading space, or "" for none
     * @return the caption
     */
    public static String stationCaption(String name, String arrow)
    {
        if (name == null) return LAYOUT_STATION_EMPTY;

        String cut = name.substring(0, Math.min(name.length(), LAYOUT_STATION_MAX_LENGTH)).trim();

        // Joined through StationCaption rather than with `+`, because which SIDE the arrow goes on depends on which
        // way it points (Adam), and that rule has to be the same here and in the crowded caption.
        return StationCaption.withArrow(cut, arrow);
    }

    // Component that holds the layout
    private JPanel container;

    /**
     * The spinner this grid put into the panel, and the panel it went into.
     *
     * Held so that `discard()` can take it out again.  The spinner is added by the grace timer and removed by the
     * reveal, and discarding cancels the reveal, so a grid discarded between those two moments would leave its
     * spinner mounted in a panel it no longer owns.  The panel is emptied only at the end of the build, so an
     * outgoing spinner would sit beside the outgoing diagram for the whole rebuild (`parent` is a FlowLayout, which
     * pushes the tiles along).
     */
    private LoadingSpinner mountedSpinner;

    /**
     * Whether this grid's container ever reached the panel.
     *
     * The panel is emptied and the new container added at the very end of the build, so anything that throws before
     * then leaves the panel holding the OLD container: visible, its timers stopped by `discard()`, its captions
     * handed back, and looking live when it is not (the fault OB-128 was filed about).  `discard()` empties the
     * panel when this is still false.  No throw is known on this path; "nothing known" is the same reason the
     * reveal has a failsafe.
     */
    private boolean swapped = false;

    private JPanel spinnerPanel;

    /**
     * Whether this grid has been replaced by another on the same panel.
     *
     * A grid hides itself until its tiles have finished decoding and shows a spinner meanwhile, so two timers and a
     * callback holding the PANEL can still be due after the grid that armed them has gone.  Rebuilding the diagram
     * calls parent.removeAll(), so they would act on somebody else's grid: the grace timer would add a spinner into
     * the middle of it, and because the panel is a FlowLayout the tiles are pushed along and the last row comes out
     * short.  So a replaced grid is told, and its three stragglers do nothing.
     */
    private volatile boolean discarded = false;

    /**
     * The panel this grid was built into, which `discard()` empties when the build never reached the swap.
     *
     * It is not how `discard()` finds the labels to hand back.  That is `registeredCaptions`, because one panel
     * serves every page, so forgetting by panel would forget every page's captions.
     */
    private JPanel owner;

    private TrainControlUI window;

    /**
     * The captions this grid was built with: each square it drew, to the station whose caption it carries or null, and
     * each of those stations to the name it gave (MT-670).  They are the setup's answers a grid cannot be told again once
     * it is built - a caption is a label of its own, in a cell laid out wider - so a setup change asks them to know
     * whether building the grid again would draw anything different.
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
     * The caption labels THIS grid registered, so discarding it can hand back exactly those.
     *
     * Not everything registered against the panel: the main window has ONE panel for every page, so forgetting by
     * panel would forget the captions of every page, and a page served from the grid cache registers nothing on the
     * way back, so its captions would be blank for good.
     */
    private final java.util.List<javax.swing.JLabel> registeredCaptions = new java.util.ArrayList<>();

    /**
     * Where a container remembers the caption labels the grid inside it registered.
     *
     * The page cache holds CONTAINERS, and when it is emptied those grids are finished with for good -
     * but a container is all the window has at that moment, so it has to be able to get from one to the
     * labels.  Hung on the container rather than kept in a map here, so it cannot outlive what it is
     * about.
     */
    public static final String CAPTIONS_REGISTERED = "TrainControl.captionsRegistered";

    /**
     * Makes a diagram's window scroll half a square at a time on both axes, at the size it is drawn (MT-726): each
     * step of the mouse wheel and each press of a scroll bar's arrow moves the diagram half a square.  Adam: "make
     * scrolling in the track diagram viewer faster on both axes", and "Make it half a square at a time".  The panel
     * a diagram sits in is not Scrollable, so without this its scroll bars step one pixel at a time.
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
     * Where a container remembers the grid that built it - for the window to ask whether the grid on screen is still the
     * setup's (MT-670).  Hung on the container for the reason `CAPTIONS_REGISTERED` is: a map of containers to grids kept
     * every grid the window ever built, as each grid holds its container.
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
     * The grid currently drawn into each panel, so that building a new one can retire the old one.
     *
     * Four places build a grid over an existing panel, and a rule remembered at three of four call sites is one that
     * is missed at the fourth (a spinner dropped into the middle of the page the new grid had just drawn).  So the
     * rule lives here: building a grid IS retiring the one it replaces.
     *
     * Weak keys, so a panel that has gone away takes its entry with it.  Weak values too: a WeakHashMap collects an
     * entry only when nothing else reaches the key, and a LayoutGrid reaches its own panel, so a plain grid as the
     * value would keep its key alive and no entry would ever be collected.
     */
    private static final java.util.Map<JPanel, java.lang.ref.WeakReference<LayoutGrid>> LIVE =
        java.util.Collections.synchronizedMap(
            new java.util.WeakHashMap<JPanel, java.lang.ref.WeakReference<LayoutGrid>>());

    /**
     * Retires whatever grid is registered against this panel, whether or not the caller ever got a reference
     * to it.
     * **A constructor that throws part-way has already registered.**  `LIVE.put` is the first thing the
     * constructor does, so a build that fails later leaves a grid holding captions with no reference anywhere for a
     * `finally` to discard.  The diagram export's bracket discards `grid[0]`, which is only assigned if the
     * constructor returned.
     *
     * Idempotent, and safe on a panel that never had a grid.
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
     * Whether this grid took its panel over from one that was already drawn on it.
     *
     * Read from the LIVE registry in the constructor, where the outgoing grid is discarded - so it
     * costs nothing and cannot disagree with what actually happened.
     */
    private boolean replacing = false;

    /**
     * The tile size this grid was built at, and whether the grid it replaced was built at the same
     * one (MT-273).
     *
     * A REBUILD AT THE SAME SIZE IS NOT AN ARRIVAL.  Every tile it draws is already in the cache at
     * that key, so the page it puts up is complete the moment it is mounted - and the one or two
     * squares that are not (a tile type newly placed) arrive within a frame or two.
     *
     * A rebuild at a DIFFERENT size is an arrival wearing a rebuild's clothes: changing the size
     * re-keys every image, so nothing is cached and the page really does come in square by square.
     * That is the case the hold-back and the spinner were written for, and it keeps them.
     */
    private final int tileSize;

    private boolean sameSizeRebuild = false;

    private javax.swing.Timer failsafe;

    private javax.swing.Timer grace;

    /**
     * Stops this grid from touching its panel again.
     *
     * Called on the OUTGOING grid before a new one is built over the same panel.  Idempotent, and
     * safe on a grid that never had a spinner.
     */
    public void discard()
    {
        discarded = true;

        // The caption labels this grid registered, handed back.  addLayoutStation prunes LAZILY (a stale label goes
        // only when a successor for the same square with the same owner is registered), so a CLEARED caption, or a
        // square on a page renamed or deleted, would leave its label as a child of the retired container for good,
        // keeping the whole previous grid reachable.  Here, because this is the one moment something knows a grid
        // is finished with; it runs before the replacement registers anything, so it cannot take the new grid's
        // labels with it.
        //
        // Only if this grid is really finished with, which the WINDOW decides because it owns the page cache: a
        // grid whose container is cached is coming back, and the cache re-attaches the container without
        // registering anything, so labels dropped here would leave that page's captions blank.
        if (window != null) window.forgetLayoutStations(registeredCaptions, container);

        // Null only for a grid whose constructor did not reach the panel - it registers itself against
        // that panel before it builds, so a failure part way through leaves one here to be discarded by
        // the next grid over the same panel.  Nothing on that path throws today; guarded so that a
        // failure stays the failure it was rather than becoming an NPE in the recovery.
        if (container == null) return;

        // Shown, whatever state the reveal had reached.  A grid is HIDDEN while its tiles decode and shown again by
        // the reveal, which discarding cancels, so a grid discarded mid-decode would stay hidden, and coming back
        // to it from the page cache would show an empty diagram.
        container.setVisible(true);

        if (failsafe != null) failsafe.stop();
        if (grace != null) grace.stop();

        // AND THE SPINNER THIS GRID MOUNTED, if the reveal never ran to take it out.  Stopping the timers
        // stops a spinner being added, not one already there.
        if (mountedSpinner != null && spinnerPanel != null)
        {
            spinnerPanel.remove(mountedSpinner);

            mountedSpinner = null;
        }

        // AND A PANEL LEFT HOLDING SOMETHING THIS GRID NEVER REPLACED.  When a build threw before the
        // swap, the panel still holds the container of the grid BEFORE this one, its timers stopped and its captions
        // gone; emptying it makes the failure look like one.  At discard rather than in a catch, because three
        // callers build a grid over an existing panel and only one of them catches, and this is where all three go
        // on the way to the next attempt.
        if (!swapped && owner != null)
        {
            owner.removeAll();
            owner.revalidate();
            owner.repaint();
        }
    }
    
    private boolean cacheable = false;
    
    /**
     * The panel a diagram is built into, which paints the trains last (OB-159).
     *
     * Adam: "it is a z order issue.  The stations paint over the locomotives."
     *
     * Swing paints children in one order with no notion of layers, and the two things that want the front, the
     * station caption and the locomotive standing under it, are on different components.  Captions in front paint
     * over the locomotive (OB-159); the tile in front paints out the NAME, because a tile is opaque (OB-117).  So
     * the trains are not a component's z-order at all: the children paint in their order, then every tile is asked
     * for its train, which lands over both.
     *
     * Public and static so a test can build one out of plain components and look at what comes out, with no
     * railway.
     *
     * @return a panel that draws its tiles, then its captions, then what its curves' stations spill onto the squares
     *     beside them with those squares' arrows and the captions back over it, then its trains, then its addresses
     */
    public static JPanel newDiagramContainer()
    {
        return new JPanel()
        {
            @Override
            protected void paintChildren(java.awt.Graphics g)
            {
                super.paintChildren(g);

                // A STATION ON A CURVE SPILLS ONTO THE SQUARES BESIDE IT (Adam: "can we instead make the stations
                // spill over onto adjacent tiles?  This would look much better than trying to reduce the size."),
                // drawn here once every square is drawn: its own square cuts it off at its edges, and a square
                // painted after it would paint its white over what reached that far.  Under the captions, the
                // trains and the addresses, as the icon on its own square is; the captions are drawn again over it
                // below.
                java.util.Map<LayoutLabel, java.awt.Rectangle> spilt = new java.util.LinkedHashMap<>();

                for (java.awt.Component child : getComponents())
                {
                    if (child instanceof LayoutLabel && ((LayoutLabel) child).paintStationSpill(g))
                    {
                        spilt.put((LayoutLabel) child, ((LayoutLabel) child).spillReach());
                    }
                }

                // AND THE ARROWS OF THE SQUARES A SPILL REACHED, BACK OVER IT (Adam: "make sure that the optional
                // ingress/egress arrows remain visible, especially on curves").  A square's arrows sit at the middle
                // of its edges, exactly where a curve's station reaches its neighbours.
                if (!spilt.isEmpty())
                {
                    // ONLY WHERE THE SPILL WAS DRAWN, and the captions over that again: everywhere else
                    // the arrows and captions are as the squares drew them, a caption in front of the tiles (OB-117),
                    // and arrows drawn over the whole square would come out in front of a caption covering it.
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

                // AND THE ADDRESSES OVER THE TRAINS (FR-116; Adam: "Red address labels look good, but make sure they
                // are rendered on top of the autonomy locomotive icons").  An address is what the operator turned
                // Show Addresses on to read, and it covers only its letters and their halo, so drawn last it hides
                // nothing of the icon but the number.
                // ONLY THOSE IN WHAT IS BEING REPAINTED: a hover repaints three squares by three, and
                // painting every address on the page each time would be clipped to nothing.  The pass above already
                // skips what the clip misses.
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
             * These children DO overlap, and Swing has to be told (OB-172).
             *
             * A full repaint goes through this container's `paint`, which redraws the ruler last and is correct.  An
             * ordinary repaint does not: `isOptimizedDrawingEnabled` defaults to TRUE, which promises Swing that no
             * two children overlap, so it repaints a dirty child DIRECTLY, without the parent, and a caption that
             * reaches into the gutter paints over the numbers with nothing to put them back.
             *
             * The promise is false here: a `StationCaption` is 38 pixels tall and up to 55 wide against a 30-pixel
             * cell.  Returning false makes every repaint go through the parent, which the numbers need, at some cost
             * in repaint area (`testRenderingCost` measures it).
             */
            @Override
            public boolean isOptimizedDrawingEnabled()
            {
                return false;
            }

            /**
             * The axis numbers, drawn last of all (OB-172).
             *
             * In `paint` rather than at the end of `paintChildren`: Swing gives each child its own scratch graphics
             * inside `paintChildren`, and `paint` is the place that is unambiguously after everything.
             *
             * **Why the numbers vanish without it.**  A `Border` is painted by `JComponent.paint` BEFORE the
             * children, so every child reaching into the eighteen pixels the ruler reserves is drawn over the number
             * underneath.  A `StationCaption` is 38 pixels tall and up to 55 wide against a 30-pixel cell, so one of
             * them takes out the numbers for two rows, including rows carrying no caption of their own.  The numbers
             * are not skipped by `paintBorder` (every cell measures exactly 30x30 against a 14-pixel font); they are
             * covered.
             *
             * Painted a second time rather than moved out of the border: the border is what RESERVES the gutter, and
             * a ruler that stopped being one would take the room away with it.  `super.paint` runs the whole
             * sequence, and then this puts the ruler back over the top.
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
        // Is THIS grid inside an editor, not "is an editor open somewhere".  layout.getEdit() alone is the second
        // question: it is the flag the two editors share for their mutual exclusion, so while either was open every
        // grid on screen would answer yes, the viewer included, and would draw the editor's grey grid, grey its
        // captions, drop its hand cursors and tooltips, and attach mouse listeners that cast their parent to
        // LayoutEditor, which the viewer is not.
        final boolean inEditor = layout.getEdit() && master instanceof LayoutEditor;

        this.builtPage = layout.getName();
        this.builtInEditor = inEditor;
        this.builtMaster = master;

        // Before anything else touches the panel: whatever was drawn here is being replaced, and a
        // replaced grid with timers still armed fires into a panel that is no longer its own.
        this.owner = parent;
        this.window = ui;
        this.tileSize = size;

        if (parent != null)
        {
            java.lang.ref.WeakReference<LayoutGrid> was =
                LIVE.put(parent, new java.lang.ref.WeakReference<>(this));

            LayoutGrid outgoing = was == null ? null : was.get();

            replacing = outgoing != null && outgoing != this;

            // Asked BEFORE the discard, because it is a question about the grid being replaced and
            // the discard is what finishes with it.
            sameSizeRebuild = replacing && outgoing.tileSize == size;

            if (replacing) outgoing.discard();
        }

        // No station captions at all in the TRACK diagram editor (FR-030; see hidesStationCaptions).  Which editor
        // this is must be asked of the window: layout.getEdit() is true in BOTH, because the autonomy editor
        // borrows the diagram editor's edit flag for its mutual exclusion, and the track diagram editor needs the
        // raw "Point:" text it can edit.
        //
        // Decided before `captioned` is read rather than at each of the four places that draw one, so the rule is
        // not enforced at the point of use, where it could be forgotten.
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
        
        // NOT EMPTIED YET: see the swap below (Adam: "less flickering").  Emptying the panel here would leave it
        // blank for the whole build (every tile, image decode and caption), and any paint landing in that window,
        // as decoding pumps the event queue, would draw a blank page.  The old diagram stays up until the new one
        // is ready.
        
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
            // "Being edited" is a fact about the DIAGRAM and is true in both windows at once (the main window
            // shares the LayoutDiagram with the editor), so it is the wrong question for anything about how THIS
            // grid is arranged.  The flag is shared at all only for the editors' mutual exclusion.  So this
            // asks whether THIS grid is in an editor (MT-106): with the shared flag, any repaint of the viewer while
            // an editor was open re-laid the viewer the editor's way.  The viewer's tiles stay clickable while an
            // editor is open because the clickability line below passes `inEditor`.
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

        // THE COLUMN AND ROW NUMBERS, in the margin the border reserves (FR-057).
        //
        // Adam: "coordinates are referenced in issues but not visible to the user.  add a grid around the diagram."
        // Drawn as a border rather than as two more rows of cells (see AxisRuler), so the grid inside is exactly
        // what it was and every caller that addresses it by coordinate is unaffected.
        //
        // The numbers are the DIAGRAM's, not the cell indices: `offsetX`/`offsetY` are where its left-most and
        // top-most track actually are, which makes the printed number the one an issue would quote.  `width` and
        // `height` already include the spacer row and column, which hold nothing, so the ruler is told the real
        // counts.  Asked on every build, which makes the toggle a redraw rather than a special case.
        //
        // IN THE EDITORS, NOT IN THE VIEWER: Adam asked for them in both the autonomy editor and the track diagram
        // editor, and the viewer is where trains are watched, so a ruler there is scenery over the railway.
        // `master instanceof LayoutEditor` rather than `inEditor`, because the window test answers "is this an
        // editor at all" instead of borrowing a flag that means "the diagram is editable" and happens to agree.
        // The flag is set in autonomy mode too: `LayoutEditor.render()` calls `layout.setEdit()` unconditionally,
        // after `setAutonomyMode` has run.
        //
        // AND ONLY WITH THE GRID ON, which is what `coordinatesVisible` answers; asked through it so there is one
        // place to read the rule and one place to change it.
        if (master instanceof LayoutEditor && coordinatesVisible())
        {
            // MEASURED, NOT MULTIPLIED.  Positions worked out as `column * size` are right only while every cell is
            // exactly a tile wide, and with the grey grid on each square wears a line border that reserves room, so
            // the real pitch is a pixel or two more.  The error is cumulative: the first few numbers sit over their
            // squares and the twentieth is a whole square out (Adam: "the axis numbers drift and are out of
            // alignment").  These two lambdas are asked at PAINT time, when the layout has run and a square's
            // bounds are what they really are; `grid` is filled in below this line, which is fine for the same
            // reason.
            final LayoutGrid drawing = this;

            // MORE THAN ONE ROW TO MEASURE FROM (OB-172): a column whose top square is absent, or has not been
            // given its bounds yet (an editor swap can leave that for a moment), would lose its number entirely.
            // The first few rows and columns are offered; they are all the same column, so any gives the same x.
            java.util.List<java.util.function.IntFunction<java.awt.Rectangle>> columnSources =
                new java.util.ArrayList<>();

            java.util.List<java.util.function.IntFunction<java.awt.Rectangle>> rowSources =
                new java.util.ArrayList<>();

            // Six probes: the candidates are compared by size rather than taken in order, so a wider sweep costs a
            // few lambda calls at paint time and buys immunity to a run of spacers at the edge of the diagram.
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

        // AND THE ROOM THE RULER ASKED FOR, added to the panel rather than taken out of the diagram (FR-057).
        //
        // A border paints inside the component's own bounds, so the eighteen pixels it reserves come out of
        // whatever the component was given.  Sizing the panel at exactly `width * size` by `height * size` would
        // carve the gutter out of the diagram, pushing the tiles right and down inside a panel that had not grown
        // and sending the numbers under the edge of the scroll pane.  `maxWidth` and `maxHeight` are what the
        // EDITOR sizes its own panel from, so they carry the gutter too.
        java.awt.Insets gutter = container.getBorder() == null
            ? new java.awt.Insets(0, 0, 0, 0) : container.getBorder().getBorderInsets(container);

        // THE SPACER IS PAID FOR, and `width`/`height` already include it.  The ruler numbers every real square;
        // what has no number is the dummy column and row GridBagLayout is given so that an overflowing text label
        // cannot widen its neighbours, and those are not squares.  Sizing the container without them is wrong; see
        // `LayoutLabel.markSpacer`.
        int fullWidth = width * size + gutter.left + gutter.right;
        int fullHeight = height * size + gutter.top + gutter.bottom;

        container.setSize(fullWidth, fullHeight);
        container.setMaximumSize(new Dimension(fullWidth, fullHeight));

        maxWidth = fullWidth;
        maxHeight = fullHeight;
               
        grid = new LayoutLabel[width][height];

        // ASKED ONCE A BUILD, AND HANDED TO EACH SQUARE: the editor's grid setting is a preference, a Windows
        // registry read of 22 microseconds that nothing caches, so asking it per square would be one read per square
        // on every redraw of the editor's diagram.
        final Boolean gridLines = inEditor ? LayoutEditor.showGrid() : null;
                       
        for(int y = 0; y < height; y++)
        {
            for(int x = 0; x < width; x++)
            {
                // GBC fix - we create a dummy column at the end with nothing in it to ensure long labels don't misalign things
                if (x == (width - 1) || y == (height - 1))
                {
                    grid[x][y] = new LayoutLabel(null, master, size, ui, false);

                    // Not a square, so not part of the grey grid (OB-055).  These hold nothing, so GridBagLayout
                    // gives them whatever is left rather than a square's worth, and the border would make them
                    // visible: an extra row at the bottom and an extra half column on the right.
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
                // autonomy setup, pointing at the sensor's square, so it is asked for rather than found by reading a
                // prefix off a label.  It can therefore sit on a square carrying no component at all, which is why
                // the text below is drawn for a caption as well as for a label.
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
                    // A StationCaption, which is a JLabel that can draw itself as a pill (FR-028).
                    //
                    // Every text label on the diagram is built here - captions, station names, and the
                    // user's own writing - and only the first of those becomes a pill. The rest are
                    // JLabels behaving exactly as they did, which is why this is a subclass and not a
                    // separate component: one place builds them, one place decides which is which.
                    StationCaption text = new StationCaption();

                    // Set by the autonomy editor's placed-train branch below, and read after the
                    // generic label styling, which would otherwise take the translucency back.
                    boolean standingTrain = false;

                    // Whether this caption names a home locomotive that is somewhere else (MT-261
                    // ruling 2).  It picks the pill's fill, which is what makes the name read white.
                    boolean awayFromHome = false;

                    // Black unless something below has a reason to say otherwise
                    Color labelColour = Color.BLACK;
                    
                    // What the user wrote on this square, which is a different thing from the caption
                    final String own = c == null || c.getLabel() == null ? "" : c.getLabel();

                    // The station name, for a caption to show when no train is standing on it
                    final String captionName = captioned == null
                        ? null : ui.autonomyStationNameAt(captioned);

                    if (captioned != null) captionNamesAsBuilt.put(captioned, captionName);

                    // WHETHER THIS LABEL IS DRAWN AT ALL (OB-272).  In the autonomy editor a CAPTION follows the
                    // caption dropdown and the user's own writing follows the text switch, which only the Text
                    // Labels box turns on (FR-133; Adam: "make text labels be a dedicated setting, and hide the text
                    // labels unless it is selected.").  Everywhere else one switch decides both.
                    final AutonomyEditorPanel captionPanel = autonomyEditor && master instanceof LayoutEditor
                        ? ((LayoutEditor) master).getAutonomyPanel() : null;

                    final boolean drawn = captioned != null && autonomyEditor && captionPanel != null
                        ? captionPanel.isDrawingCaptions() : !layout.getEditHideText();

                    // Autonomy station caption, live.  An excluded page is already gone by here: `hidesStationCaptions`
                    // takes the decision once, earlier, and nulls `captioned` for it, so asking again would be dead
                    // code, and the dead half of a condition is where a rule quietly stops being enforced when the
                    // live half changes.  Nothing autonomy knows is drawn on an excluded page (B6; Adam: "hide
                    // captions").  Writing of the user's OWN is untouched: it was never autonomy's to hide.
                    if (captioned != null && !inEditor)
                    {
                        // Blank until autonomy says otherwise.  A caption belongs to the running setup: it shows what
                        // is standing at that station, and with nothing loaded there is no answer to give.  Seeding
                        // it with the station's name would cover an unloaded diagram in names that look like live
                        // captions and are not.
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
                            // The name is a component of its own, stacked on top of the square, and the keyboard's
                            // idea of what is hovered comes from the square's listener, which gets mouseEXITED the
                            // moment the pointer moves onto the name.  So this reports the hover itself, which makes
                            // Control+V work over the name as it does over the platform.  It reports the STATION
                            // rather than the square the text sits on: a caption may be drawn on blank space beside
                            // its platform, and pointing at a station's name means that station either way.
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
                                // A DOUBLE-CLICK IS TWO CLICKS, and each activates (OB-138).
                                //
                                // Adam: "double clicking station label in track viewer should activate that
                                // locomotive (as if it was selected on the key mappings) if it is mapped, not open
                                // the editor."  So no branch here opens the autonomy setup; the ordinary left-click
                                // path below does what he describes (jumpToLocomotive switches to the locomotive's
                                // mapping page and selects its button, and does nothing when it has no mapping).
                                // The setup is not stranded: the right-click menu on this same square still opens
                                // the full editor, and explains itself when it has to refuse.

                                if (e.getButton() == MouseEvent.BUTTON3)
                                {
                                    LayoutRightclickAutonomyMenu.showFor(ui, station, station,
                                        e.getComponent(), e.getX(), e.getY());
                                }
                                // Left-clicking a station will activate its locomotive
                                else
                                {
                                    // By SQUARE: a square where trains may turn round is several Points, none of
                                    // them called what the diagram says, so a lookup by the text of the caption
                                    // would find nothing.
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
                            // The STATION by default, the parked train on request (FR-030).
                            //
                            // Adam: "in the autonomy editor, have them show the station name by
                            // default ... rather than the parked train."  This is the window where a
                            // railway is named, and a caption saying which locomotive happens to be
                            // standing somewhere answers a question about right now in a window about
                            // how things are arranged. The running diagram is where the trains are.
                            boolean naming = !(master instanceof LayoutEditor)
                                || ((LayoutEditor) master).getAutonomyPanel() == null
                                || !((LayoutEditor) master).getAutonomyPanel().isShowingParkedTrains();

                            // WHERE THE TRAIN LIVES, if that is what has been asked for (MT-261 ruling 2).
                            //
                            // Adam: "labels show the home locomotive in white to indicate it's not there (and
                            // black/no change if it's there)."
                            //
                            // Asked FIRST, because it is the more specific question: a caption has room for one
                            // name, and somebody who has turned this on is looking at homes rather than at station
                            // names or at what happens to be parked.  A square with no home shows nothing (below).
                            boolean homes = master instanceof LayoutEditor
                                && ((LayoutEditor) master).getAutonomyPanel() != null
                                && ((LayoutEditor) master).getAutonomyPanel().isShowingHomeLocomotives();

                            String home = homes ? ui.autonomyHomeAt(captioned) : null;

                            if (home != null)
                            {
                                text.setText(stationCaption(home, ""));

                                // AWAY IS THE DARK ONE, which is how the name comes out white.
                                //
                                // `StationCaption.onPill` picks a foreground that reads on whatever fill it is
                                // given, so the colour is chosen by naming the FILL rather than by hard-coding a
                                // white that would vanish on the resting pill.  Home and standing here gets the
                                // ordinary resting fill and its ordinary near-black text.
                                String standing = ui.autonomyLocomotiveAt(captioned);

                                boolean athome = home.equals(standing);

                                standingTrain = true;

                                labelColour = athome ? Color.BLACK : Color.WHITE;

                                awayFromHome = !athome;
                            }
                            else if (homes)
                            {
                                // NO HOME, NO LABEL (Adam: "if there are no home locomotives, just hide the label at
                                // that station, rather than still showing the station name").
                                //
                                // Home Locs is one of several options that exclude each other, and a mode that
                                // quietly shows another mode's content is indistinguishable from a control that does
                                // not work.  Nothing at all rather than the empty placeholder, which means "a
                                // station with no name": these squares have names, they have no HOME.
                                text.setText("");

                                text.setOpaque(false);

                                standingTrain = false;
                            }
                            else if (naming)
                            {
                                // The station's own name, which is what this window is for.  Greyed like the
                                // placeholder: it is a label on the diagram rather than something set here.
                                text.setText(captionName == null ? LAYOUT_STATION_EMPTY : captionName);

                                labelColour = captionName == null
                                    ? new Color(150, 150, 150) : Color.BLACK;

                                standingTrain = captionName != null;
                            }
                            else
                            // What the SETUP puts on this square, which is the question the editor is about.  The
                            // running diagram shows what is on the rails; here there may be no run at all, so a
                            // platform with a train assigned to it still has to show it.
                            {
                            String placed = ui.autonomyCaptionTextAt(captioned);

                            if (placed != null)
                            {
                                text.setText(placed);

                                // The running diagram's own style for a named train: black on translucent white, so
                                // it reads over whatever tile art is underneath.
                                //
                                // Applied AFTER the generic setBackground(WHITE) below rather than here: set here it
                                // would be overwritten, and because this is the one branch that also turns opacity
                                // ON, the label would paint a solid white rectangle, which a translucent style
                                // exists to avoid.
                                standingTrain = true;

                                labelColour = Color.BLACK;
                            }
                            else
                            {
                                // NO LOCOMOTIVE, NO LABEL (OB-312; Adam: "When in text labels-parked locomotive,
                                // dont show labels without a locomotive"), as Home Locs draws nothing where there is
                                // no home.  Station Names is the option that shows where the captions land.
                                text.setText("");

                                text.setOpaque(false);

                                standingTrain = false;
                            }
                            }
                        }
                        else if (captioned != null)
                        {
                            // A caption the diagram cannot act on: an excluded page, or the TRACK
                            // DIAGRAM editor, where it is drawn so the square is visibly spoken for
                            // but is not that editor to change - captions are edited where autonomy
                            // is edited.
                            text.setText(captionName == null ? LAYOUT_STATION_EMPTY : captionName);

                            if (inEditor) labelColour = new Color(150, 150, 150);
                        }
                        else
                        {
                            // Everything else: what the user wrote there.  This includes an old autonomy "Point:"
                            // label on a layout served by the Central Station, which is drawn as written (OB-344;
                            // Adam: "stop stripping that prefix.").
                            text.setText(own);

                            // Writing of their own, greyed while autonomy is being edited: it is still
                            // worth seeing - it says what part of the railway this is - and it is not
                            // what the editor is for.
                            if (autonomyEditor) labelColour = new Color(150, 150, 150);
                        }
                    }


                    text.setForeground(labelColour);
                    text.setBackground(Color.WHITE);
                    // StationCaption.LABEL_FONT, not "Segoe UI" - it is the same typeface for every
                    // word on a diagram and additionally has the four matched arrows (OB-116).
                    text.setFont(new Font(StationCaption.LABEL_FONT, Font.PLAIN, size / 2));

                    // A caption is a pill; writing of the user's own is not (FR-028).
                    //
                    // `captioned` is the whole test: it is the square autonomy has been told to draw a
                    // station on. A yard name somebody typed is text on a diagram and stays text.
                    if (captioned != null)
                    {
                        text.setPill(true);
                        text.setBackground(StationCaption.restingFill());
                        // onPill and not readableOn: the branches above choose a grey for a placeholder and a black
                        // for a name, deliberately, and readableOn would throw both away because its condition is a
                        // superset of all of them.  onPill dims a neutral grey towards the pill, so a placeholder
                        // stays dimmer than a name (`testAPlaceholderStaysDimmerThanAName`).
                        text.setForeground(
                            StationCaption.onPill(StationCaption.restingFill(), labelColour));

                        // A tenth smaller than the diagram's other text: the pill is taller than what
                        // it holds, and the rows of a diagram are one tile apart.
                        text.setFont(text.getFont().deriveFont(
                            text.getFont().getSize2D() * StationCaption.FONT_SCALE));
                    }

                    // AND A HALO ROUND EVERY LABEL THAT IS NOT A PILL (Adam: "Give regular text labels on the track
                    // diagram (.text) the same halo as address labels (possibly a smaller outline) so that labels are
                    // visible against black backgrounds like tracks."): the user's own writing, and an old file's
                    // station label.  A pill has its own fill.
                    text.setHalo(!text.isPill());

                    // The one label drawn ON something rather than beside it.  Opaque, so the name
                    // reads over the tile art; translucent, so the tile art still shows through.  It
                    // has to come after the WHITE above, which is the whole reason it is down here.
                    if (standingTrain)
                    {
                        // A pill paints its own fill and must stay transparent, or Swing draws the
                        // rectangle underneath it that the pill exists to replace.
                        text.setOpaque(!text.isPill());

                        // A HOME THAT IS EMPTY GETS THE DARK PILL (MT-261 ruling 2).
                        //
                        // Adam asked for the name "in white to indicate it's not there".  White ink on the resting
                        // pill, which is pale, would be invisible, so the fill is what changes and `onPill` below
                        // picks the readable foreground for it.
                        Color fill = text.isPill()
                            ? (awayFromHome ? AWAY_FROM_HOME_FILL : StationCaption.restingFill())
                            : new Color(255, 255, 255, LAYOUT_STATION_OPACITY);

                        text.setBackground(fill);

                        text.setForeground(
                            StationCaption.onPill(text.getBackground(), labelColour));
                    }
                    
                    // Where a pill lands, and out of the row's baseline, for EVERY caption.  This must not depend on
                    // `c != null`: a caption drawn on a BLANK square needs the placement too (OB-115), and blank
                    // squares are not a curiosity: the caption placer calls one "the most readable place of all" and
                    // picks it second.
                    if (text.isPill())
                    {
                        // Adam: "land them so that they align just below straight tracks if the track goes east to
                        // west, or centered over the track if north to south."  An east-west rail runs across the
                        // middle of the square, so a caption on the middle would sit on it; a north-south rail runs
                        // UP it, and a caption beneath would be beside the track rather than on it.  A blank square
                        // has no rails to be placed against, so it takes the east-west answer.
                        //
                        // A border and not gbc.anchor: this cell is declared gridheight = 0 (REMAINDER), so it runs
                        // from this row to the bottom of the diagram, and anchoring SOUTH inside it means the bottom
                        // of the PAGE.
                        //
                        // NORTHWEST because a caption has no business voting on the row's baseline.
                        // BASELINE_LEADING lines up every component anchored that way, so a caption whose height
                        // changed would move the row's baseline and take its neighbours with it (OB-115).
                        //
                        // ROTATED for track that runs up the square, which also decides which way the caption is
                        // centred and which way it is offset, so the orientation is asked once, here, and the
                        // caption is told.
                        boolean onEnd = runsNorthSouth(c);

                        text.setRotated(onEnd);

                        int offset = StationCaption.captionOffset(size, text.lineHeight());

                        // A column of room to the left, so the caption can be CENTRED on its square
                        // rather than starting at it. Centring means beginning left of the square, and
                        // no border can say that - insets cannot be negative - so the cell is moved
                        // back a column and the label pays the difference back as a left inset.
                        //
                        // Not at the left edge, where there is no column to move into: a caption there
                        // starts at its own square exactly as it did before.
                        int backShift = onEnd || x == 0 ? 0 : size;

                        // And a row of room ABOVE for a rotated one, which is the same trick turned a
                        // quarter: it is centred along a rail that runs up the square, so centring
                        // means starting above that square, and insets cannot say that either.
                        //
                        // Only one of the two is ever bought. A flat caption needs no vertical room
                        // and a rotated one needs no horizontal room, and buying both would move the
                        // cell diagonally away from the square it names.
                        int upShift = onEnd && y > 0 ? size : 0;

                        gbc.gridx = x - (backShift > 0 ? 1 : 0);
                        gbc.gridy = y - (upShift > 0 ? 1 : 0);

                        text.setTileGeometry(size, backShift, upShift, offset);

                        gbc.anchor = GridBagConstraints.NORTHWEST;
                    }

                    // Shift on-tile labels down
                    // Current limitation if we wanted to use borders: if you have a text element and an on-tile label in the same row
                    // , they both get shifted down by the same amount.  Therefore, do this multiline hack.
                    if (c != null && !c.isText() && drawn)
                    {
                        //text.setBorder(new EmptyBorder(16 * (size / 30), 0, 0, 0)); //top, left, bottom, right
                        gbc.gridheight = 0;

                        // A caption with a train on it is left as PLAIN text: the editor's label is the same label
                        // with the same styling as the running diagram's, and only the string differed.  On the
                        // running diagram the caption is registered EMPTY here and its text set afterwards by
                        // updateStationLabels, so it never reaches the wrap below.  The editor has no run to wait
                        // for and sets the text now, so it would pick up the wrap: a leading <br> makes the label two
                        // lines tall and the &nbsp; stops it wrapping, so an OPAQUE label (this is the only one)
                        // would paint its background as a block a tile tall and three wide over whatever was beside
                        // it.
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
                    
                    // DRAGGABLE, in the autonomy editor, and only a square that carries a caption (FR-035).  Not
                    // tiles and not the user's own writing: Adam asked for station labels only.
                    // AND THE LABELS ARE ACTUALLY DRAWN (OB-139).  `captioned` is the caption OBJECT, which exists
                    // whether or not the text layer is showing; with Text Labels unticked the pills disappear, and a
                    // move cursor on an invisible label would offer a drag of something the user cannot see.  Not
                    // installed at all rather than installed without its cursor: the cursor IS the diagram saying
                    // the thing can be picked up, and the two must not disagree.  The grid is rebuilt when the box
                    // is toggled, so ticking it back restores the drag.
                    if (autonomyEditor && captioned != null && master instanceof LayoutEditor
                        && drawn)
                    {
                        org.traincontrol.automationui.TileGraph.TileKey here =
                            new org.traincontrol.automationui.TileGraph.TileKey(
                                layout.getName(), x, y);

                        // The station this label NAMES, which is usually not the square it is on.  A caption goes on
                        // blank space beside its platform, because a platform road rarely has room for the text, so
                        // asking the square underneath what station it is would answer "none" for precisely the
                        // labels worth asking about.  What the caption was built from is the answer.
                        text.setToolTipText(captionName);

                        // BOTH the label and the square under it (Adam: "have it fire on the label or the tile, so
                        // the mouse icon is more clearly shown").  A pill is a small target, and a move cursor that
                        // only appears over those few pixels gives the diagram almost no way of saying a label can
                        // be picked up.
                        dragCaption(text, text, grid[x][y], here, (LayoutEditor) master, container);
                        dragCaption(text, grid[x][y], grid[x][y], here, (LayoutEditor) master,
                            container);
                    }

                    container.add(text, gbc);
                    container.setComponentZOrder(text, 0);

                    // And back to this square's own column.  Centring a caption moves gbc.gridx a column LEFT so the
                    // pill can start before its square, and gbc is then reused by the address label below, which
                    // pays nothing back: on every captioned, clickable square the sensor's number would be drawn a
                    // whole tile away from the sensor, over its neighbour's.  gridy is paid back for the same reason
                    // (the rotation moves it up): a shift taken out for the caption must not be left in the
                    // constraints for whoever comes next.
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

                        // AND ITS EXIT: the label covers the top left of its square, and a pointer that left
                        // the squares from here told no square - the track editor's outline stayed on it, and the main
                        // window's keys still aimed at it
                        @Override
                        public void mouseExited(MouseEvent e)
                        {
                            for (MouseListener listener : outer.getMouseListeners())
                            {
                                listener.mouseExited(e);
                            }
                        }
                    });

                    // RED LETTERS ON A WHITE HALO, NOT IN A BOX (Adam: "Can we prettify address labels to have a white
                    // halo outline around the red text rather than a rectangle around them?"); see AddressLabel
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
                                                                              
                // Which SQUARE this label is, told to the label itself: every label, including the ones with nothing
                // drawn on them.  Its right-click menu asks it rather than the main window (which names the wrong
                // page in every popup window), and the keyboard shortcuts ask which square the pointer is over, which
                // a blank label could not answer.  A station's name is usually drawn on blank space beside the
                // platform, so those blanks are exactly the squares somebody aims at.
                if (ui != null)
                {
                    grid[x][y].setAutonomySquare(layout.getName(), x + offsetX, y + offsetY);
                }

                // Register the tile with autonomy, if it is watching.  Here because this is the only place that
                // knows both the page and the square: a LayoutLabel is told neither, and LayoutGrid keeps no
                // reference to the LayoutDiagram after the constructor returns.
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
            // ONE PASS: the old diagram comes off and the new one goes on with no layout between
            // them, so the panel is never painted empty.
            parent.removeAll();

            parent.add(container);

            swapped = true;

            showWhenTilesAreReady(parent, ui);
        } 
    }

    /**
     * Holds the diagram back until its track has been decoded, showing a spinner in the meantime.
     *
     * Otherwise a diagram arrives in two stages: text labels immediately, because text needs no image, then the
     * track a second or so later as the decodes come back off the pool, and the labels appear to float on nothing
     * meanwhile.
     *
     * Only when there is actually a wait.  The second time a page is opened every tile is a cache hit,
     * whenTilesSettled runs on the next EDT pass, and swapping a spinner in and straight out again would be a
     * flicker where there had been none, so the spinner is only mounted if the tiles are still not ready a moment
     * later.
     *
     * @param parent the panel the grid was just added to
     * @param ui the window whose tile loader is doing the decoding
     */
    private void showWhenTilesAreReady(JPanel parent, TrainControlUI ui)
    {
        if (ui == null) return;

        // Nothing to wait for, so nothing to hide (OB-109).  Every placement in the track diagram editor rebuilds the
        // whole grid, and on every build after the first each tile is a cache hit, so hiding the diagram and letting
        // whenTilesSettled give it back on the next EDT pass would blink whenever a paint landed between the two.
        //
        // It has to be asked HERE and not earlier: LayoutLabel counts a decode before submitting it, deliberately, so
        // a grid asking after building its labels cannot see zero while work it caused is still on its way to the
        // pool.  A cold build is untouched: the count is not zero, so the diagram is held back and the spinner armed.
        // (Making the HIDE wait alongside the spinner would cost 120ms of labels floating on nothing, the fault the
        // hold-back exists to remove.)
        if (ui.tilesAreSettled()) return;

        // And a diagram already on screen is not taken away to be rebuilt (OB-109).  The hold-back is for a page
        // ARRIVING, and hiding one that is already drawn is the opposite of what it is for: the editor rebuilds the
        // whole grid after every placement, so the first tile of a type nobody has drawn at this size is one decode,
        // and one decode would take the entire page off the screen.
        //
        // Not "never hide" on a replacement either: changing the tile SIZE re-keys every image, and a page arriving
        // square by square is the thing the spinner exists for.  So a replacement waits with the spinner instead.
        // If the rebuild is quick, which every placement is, the reveal gets there first and nothing is ever hidden;
        // if it is slow, the diagram goes behind the spinner 120ms in, as a cold page does.
        //
        // A REBUILD AT THE SAME SIZE IS LEFT ALONE ENTIRELY (MT-273).  `replacing` stops the page being hidden HERE,
        // but the grace timer below would still fire: a rebuild with one decode outstanding would stay up for 120ms
        // and then be taken away and put behind a spinner anyway.  The editor rebuilds after every placement, and the
        // tile just placed is exactly the decode that keeps the count above zero.
        if (sameSizeRebuild) return;

        if (!replacing) container.setVisible(false);

        final LoadingSpinner spinner = new LoadingSpinner();

        // Sized to the space the diagram will take, so nothing jumps when the two are swapped, and so the glass
        // lands in the MIDDLE of it (OB-129).  The parent is laid out with FlowLayout, which aligns to the top, so a
        // smaller capped box over a larger diagram would sit at the top of the page rather than over the middle of
        // it.  The glass is centred within this component already; its drawn size does not follow the component (see
        // MAX_GLASS_H).
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

        // And revealed anyway after a few seconds, whatever the count says.
        //
        // Hiding the diagram until a signal arrives means a signal that never arrives hides the diagram
        // for the rest of the session, and a blank window is a far worse fault than the flicker this
        // exists to remove.  Nothing known can swallow the count - the decrement is in a finally - but
        // "nothing known" is not a good enough reason to make the diagram depend on it.
        failsafe = new javax.swing.Timer(8000, e -> reveal.run());

        failsafe.setRepeats(false);
        failsafe.start();

        // A short grace period before the spinner is shown at all - see above
        grace = new javax.swing.Timer(120, e ->
        {
            if (revealed[0] || discarded) return;

            // Idempotent on a grid that was hidden up front; the one that matters is a replacement,
            // which was left showing and has now been slow enough to be worth a spinner.
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
     * Whether the setup still gives every square this grid drew the caption it was built with, and every caption its name,
     * so building the grid again would draw the same one (MT-670).  Asked as the grid asked: the same rule for hiding
     * captions, the same two questions of the window.
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
