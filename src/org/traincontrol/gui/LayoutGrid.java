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
    
    // Prefix that denotes a station label
    // Used to show autonomy locations on the layout
    //
    // Taken from the setup layer rather than declared here, so the side that decides whether a station
    // HAS a label and the side that draws it cannot drift apart.  Every existing reference to this name
    // still works; only where the string is defined has moved.
    public static final String LAYOUT_STATION_PREFIX =
        org.traincontrol.automationui.AutonomySession.STATION_LABEL_PREFIX;
    /**
     * A station with nothing standing on it.
     *
     * An em dash rather than "[---]" (FR-028).  The brackets were the caption's shape - "a caption is
     * about the same width whatever is in it" - and the pill is its shape now, so what is left inside
     * is only what the caption has to SAY, which for an empty platform is "nothing here".
     */
    public static final String LAYOUT_STATION_EMPTY = "\u2014";
    /**
     * A train passing through a station it was not sent to.
     *
     * Three dots rather than "[xxx]" (FR-028): the brackets were the caption’s shape and the
     * pill is its shape now, and three dots say "something is here, and it is not stopping"
     * without looking like a placeholder somebody forgot to fill in.
     */
    public static final String LAYOUT_STATION_OCCUPIED = "\u2022\u2022\u2022";
    public static final int LAYOUT_STATION_MAX_LENGTH = 10;
    public static final int LAYOUT_STATION_OPACITY = 210;

    /**
     * The pill under a home locomotive that is not standing at its home (MT-261 ruling 2).
     *
     * Adam: "labels show the home locomotive in white to indicate it's not there."  The colour that
     * makes a name white is the one BEHIND it - `StationCaption.onPill` chooses a foreground that
     * reads on whatever fill it is handed - so this is a dark fill rather than a white foreground,
     * which on the pale resting pill would have been an invisible caption.
     *
     * Translucent to the same degree as its sibling, so the tile art still shows through it and an
     * away-from-home platform does not become a solid block on the diagram.
     */
    public static final Color AWAY_FROM_HOME_FILL = new Color(60, 60, 60, LAYOUT_STATION_OPACITY);

    /**
     * Whether a diagram prints its coordinates when nobody has said either way (FR-057).
     *
     * ON.  Adam asked for "a grid around the diagram" because "coordinates are referenced in issues
     * but not visible to the user", and an option that has to be found before it does anything is not
     * an answer to that.  Control+K turns it off, in either editor, and the choice is remembered.
     */
    public static final boolean SHOW_COORDINATES_DEFAULT = true;

    /**
     * Whether station captions are left off this diagram entirely (FR-030).
     *
     * Adam: "in the track diagram editor, hide autonomy labels completely."  That editor is about
     * where the rails are. A caption there is an autonomy object drawn over the thing being moved: it
     * cannot be edited from that window, it covers the square underneath, and every one of them is in
     * the way of the one job that window has.
     *
     * The RUNNING diagram keeps them - that is where they say something - and so does the autonomy
     * editor, which is where they are set.
     *
     * **And a page left out of autonomy draws none either** (B6, Adam: "hide captions"). There is no
     * Point behind a caption on an excluded page - the graph is built without that page entirely - so
     * it was a station name that named nothing, wired to nothing, and neither of the two visibility
     * switches could reach it: both work by walking the registry of captions, and an excluded page's
     * caption is never registered in it. So the one case where autonomy will most certainly never use
     * a square was the one case whose label could not be turned off. That is FR-023's original
     * complaint, surviving inside its own fix.
     *
     * A method rather than a line of `&&`, so the rule can be asked its truth table without building a
     * window. What that leaves uncovered is whether the caller passes the right THREE booleans (DOC-C5:
     * this used to say "two", from before {@code pageExcluded} was added), which is the usual price of
     * pulling a rule out of its call site - `testEditorSurfaceRules` reads the call for exactly that
     * reason.
     *
     * @param inEditor whether this grid is inside an editor at all
     * @param autonomyMode whether that editor is the autonomy one
     * @param pageExcluded whether this page is excluded from the active autonomy configuration - the
     *                     argument that decides the result outright, added by {@code 453a3ef4}
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
     * Adam: "in the autonomy editor ONLY, make it possible to move around station labels (only) by
     * clicking and dragging them", and then: "have it fire on the label or the tile, so the mouse icon
     * is more clearly shown.  Snapshot the label so users can see it is being moved."
     *
     * Installed TWICE per caption - once on the pill, once on the square beneath it - which is why
     * the thing being moved and the thing the events arrive on are separate parameters. Both hands
     * move the same label.
     *
     * What happens on release is `moveCaption`, which is the caption command the right-click menu
     * already runs. So a dragged label stores nothing new and cannot fall out of step with the menu
     * about where a name may go.
     *
     * A drag is told from a CLICK by distance. A caption answers a double-click and a square answers a
     * plain one, and turning every press into a drag would take both away; a few pixels of movement is
     * the difference between pointing at something and moving it.
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
            // The blue outline follows the pointer onto the NAME (Adam, 2026-08-27: "make sure
            // hovering the label triggers the cell hover effect").
            //
            // A caption is stacked on top of its square, so moving onto the name sends the square a
            // mouseExited and the outline goes out - on the bigger and more obvious of the two things
            // to point at. Forwarded to the method the tiles call rather than drawing a second
            // outline here, so there is one appearance to keep right instead of two.
            //
            // Only from the caption. On the square this is already happening, and doing it again
            // would draw the same outline twice for one movement.
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

            // A RIGHT-CLICK belongs to the square, not to the name lying on top of it.
            //
            // The caption covers the middle of its square and takes every click there, so the autonomy
            // menu - which is how everything about a square is set - simply did not open on the part
            // of the diagram people aim at. Handed to `receiveClickEvent`, which is the method the
            // tile's own listener calls, so the menu is the same menu rather than a second one built
            // to match.
            //
            // The event is CONVERTED first: the menu opens at the coordinates it is given, and the
            // caption's coordinates are not the square's - the popup would appear offset by however
            // far the label sits from the corner of its tile.
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (handle != square && javax.swing.SwingUtilities.isRightMouseButton(e))
                {
                    editor.receiveClickEvent(
                        javax.swing.SwingUtilities.convertMouseEvent(handle, e, square), square);
                }
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                // THE LEFT BUTTON ONLY (reviewer, 2026-08-28).
                //
                // Every button started a drag. Right-press a captioned square meaning to open the
                // properties menu, let the pointer wander four pixels before letting go, and the label
                // was picked up and dropped wherever the cursor happened to be - while `mouseClicked`
                // never fired, so the menu did not open either. The gesture that was meant to ASK
                // about a square silently rearranged it.
                //
                // The distinction was already made on the click path a few lines above, which tests
                // `isRightMouseButton`. It was missed here. LayoutEditor carries the comment for the
                // identical fault one level down: "without this branch a right-click ran the tool as
                // well, which looked like a menu that failed to appear".
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e))
                {
                    // BOTH cleared, not just the start point.
                    //
                    // Clearing only `began` left `dragging` true, so a right-press part way through a
                    // left-drag froze the ghost and the drop mark - `mouseDragged` returns on a null
                    // start - while the right RELEASE still committed the move, at wherever the pointer
                    // had got to. Pressing the other button mid-drag is what a person does to cancel.
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
                // The LEFT button finishes a drag; any other one cancels it.
                //
                // This had no button test at all: the guard went on `mousePressed` only, so a release
                // of some other button still read `dragging` and committed the drop. Found by a
                // reviewer, and neither of the tests written with that guard could see it - both
                // asserted the presence of a token in `mousePressed` and neither mentioned this method.
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
                // NOT the spacers, which are not squares (reviewer, 2026-08-27).
                //
                // The grid ends in a dummy row and a dummy column that exist to stop long labels
                // misaligning it, and they are LayoutLabels with real bounds like any other. A label
                // dropped on one was accepted - the drop mark even showed GREEN, because the spacer
                // carries no component and a square with nothing on it may hold a caption - and the
                // caption was then stored against a square the drawing loop skips before it ever looks
                // for one. It vanished, and the setup carried an entry nothing could show.
                //
                // The strip along the bottom runs the full width of the diagram, directly under the
                // last row of track, which is exactly where somebody would drop a label for a station
                // on that row.
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
     * Asked of the geometry rather than of the tile's name or its rotation number: the same question
     * the autonomy graph asks when it works out which ways a train may leave a square, so a caption
     * and the path drawn through it cannot disagree about which way the rails go.
     *
     * False for a crossing or a curve, and false when the geometry cannot be had at all: those squares
     * have no single direction for a caption to be placed against, and the answer for them is where
     * captions have always been.
     *
     * NOT false for a switch, which this sentence used to claim (reviewer, 2026-08-28). A switch in its
     * straight position offers one route north to south, so it answers TRUE. Little reaches that today
     * because `mayCarryACaption` refuses captions on switches and signals - but two identical switches
     * saved in different states would be captioned differently, and the sentence was what a reader
     * would have trusted.
     *
     * Public so the one thing worth checking about it can be checked - which way it answers for a
     * straight rail and for the same rail turned a quarter - without building a grid and reading
     * constraints off it. The same reason `stationCaption` above is public: where a caption goes is a
     * question about geometry, and geometry can be asked without a window.
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
            for (org.traincontrol.automationui.TilePorts.Route route
                : org.traincontrol.automationui.TilePorts.ports(
                    c.getType(), c.getOrientation(), c.getState()))
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
     * How a station caption is spelled when a train is standing on it.
     *
     * A name cut to LAYOUT_STATION_MAX_LENGTH, then the facing arrow.
     *
     * The brackets that used to be here were the caption's SHAPE, and the reason given for them was
     * that "a caption is about the same width whatever is in it", so that an empty platform and an
     * occupied one did not change the tile underneath. FR-028 gave the caption a shape of its own -
     * a pill - and the width now belongs to that, so the brackets were two characters of a name that
     * had already been cut to fit.
     *
     * Written down here because it was written down in the running diagram only, and the autonomy
     * editor - added later, drawing the same placements - spelled it as a bare untruncated name. That
     * label was as wide as the name, so it covered the tiles either side of it, and did not look like
     * the diagram it was meant to match.
     *
     * @param name the locomotive
     * @param arrow the facing arrow, already carrying its own leading space, or "" for none
     * @return the caption
     */
    public static String stationCaption(String name, String arrow)
    {
        if (name == null) return LAYOUT_STATION_EMPTY;

        String cut = name.substring(0, Math.min(name.length(), LAYOUT_STATION_MAX_LENGTH)).trim();

        // No brackets since FR-028: the pill draws the shape they were standing in for.
        //
        // Joined through StationCaption rather than with `+`, because which SIDE the arrow goes on
        // depends on which way it points (Adam, 2026-08-27) and that rule has to be the same here and
        // in the crowded caption.
        return StationCaption.withArrow(cut, arrow);
    }
    public static final int LAYOUT_ADDRESS_OPACITY = 200;

    // Component that holds the layout
    private JPanel container;

    /**
     * Whether this grid has been replaced by another on the same panel.
     *
     * A grid hides itself until its tiles have finished decoding, and shows a spinner in the meantime
     * - which means two timers and a callback holding the PANEL, still due to fire after the grid
     * that armed them has gone.  Rebuilding the diagram calls parent.removeAll(), so those three then
     * act on somebody else's grid: the grace timer adds a spinner into the middle of it, and because
     * the panel is a FlowLayout an extra component there pushes the tiles along and the last row comes
     * out short.  A half-drawn row appearing after a resize is exactly that.
     *
     * So a replaced grid is told, and its three stragglers do nothing.
     */
    private volatile boolean discarded = false;

    /**
     * The panel this grid was built into.
     *
     * (DOC-B16: assigned once, in the constructor, and never read anywhere in this file.) This used to
     * be how discard() found the labels to hand back - "the labels this grid owns" answered by asking
     * which grid was built over this panel - which is the approach `registeredCaptions`'s own javadoc
     * says was tried and was wrong: one panel serves every page, so forgetting by panel forgets every
     * page's captions, not just the retired grid's. discard() now uses `registeredCaptions` instead.
     * This field is a leftover of the design that lost; nothing should be reintroduced that reads it
     * as if it still decided anything.
     */
    private JPanel owner;

    private TrainControlUI window;

    /**
     * The caption labels THIS grid registered, so discarding it can hand back exactly those.
     *
     * Not "everything registered against the panel", which was the first attempt and was wrong in a way
     * the repaint code warns about three lines from where it is called: the main window has ONE panel
     * for every page, so forgetting by panel forgot the captions of every page there is - and a page
     * served from the grid cache registers nothing on the way back, so its captions were blank for
     * good.  That is the defect "dropping labels wholesale on a repaint" describes, reached through a
     * different door.
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
     * The grid currently drawn into each panel, so that building a new one can retire the old one.
     *
     * DD-B3: four places build a grid over an existing panel and three of them remembered to discard
     * the outgoing one first. The fourth was found by `174178c5`, whose comment is the finding - "both
     * other places that build a grid over an existing panel call this; this one did not" - and the
     * symptom was a spinner dropped into the middle of the page the NEW grid had just drawn.
     *
     * Being remembered at three of four call sites is what a rule looks like just before it is missed
     * at the fourth. So the rule lives here instead, where building a grid IS retiring the one it
     * replaces and there is nothing left to remember.
     *
     * Weak keys: a panel that has gone away takes its entry with it, and nothing here keeps a window
     * alive that the application has finished with.
     */
    /**
     * Weakly on BOTH sides.
     *
     * A WeakHashMap only collects an entry when nothing else reaches the key, and a LayoutGrid reaches
     * its own panel - it holds `container` and adds it to the parent. So a plain grid as the value kept
     * its own key alive and no entry was ever collected: one page retained per editor, popup or export.
     * Found in review, before it grew into anything.
     */
    private static final java.util.Map<JPanel, java.lang.ref.WeakReference<LayoutGrid>> LIVE =
        java.util.Collections.synchronizedMap(
            new java.util.WeakHashMap<JPanel, java.lang.ref.WeakReference<LayoutGrid>>());

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

        // The caption labels this grid registered, handed back.
        //
        // addLayoutStation prunes LAZILY - a stale label goes only when a successor for the same square
        // with the same owner is registered - so a caption that is CLEARED left its label in the map for
        // good: nothing is registered for that square again, so no successor ever arrives.  The label is
        // still a child of the retired container, which keeps the whole previous grid reachable, every
        // tile and listener on that page with it.  The same held for every square on a page that was
        // renamed or deleted, since the key carries the page name.
        //
        // Here because this is the one moment something knows a grid is finished with.  It runs before
        // the replacement registers anything - LIVE.put and this call are the first thing the new
        // constructor does - so it cannot take the new grid's labels with it.
        // Its own labels, and only if this grid is really finished with - which the WINDOW decides,
        // because it owns the page cache and this does not.  A grid whose container is cached is coming
        // back: the cache re-attaches the container without building anything or registering anything,
        // so labels dropped here would be gone for the session and that page's captions blank.
        if (window != null) window.forgetLayoutStations(registeredCaptions, container);

        // Null only for a grid whose constructor did not reach the panel - it registers itself against
        // that panel before it builds, so a failure part way through leaves one here to be discarded by
        // the next grid over the same panel.  Nothing on that path throws today; guarded so that a
        // failure stays the failure it was rather than becoming an NPE in the recovery.
        if (container == null) return;

        // Shown, whatever state the reveal had reached.  A grid is HIDDEN while its tiles decode and
        // shown again by the reveal - which discarding cancels - so a grid discarded while it was
        // still decoding stayed hidden for good.  It may already be in the page cache, and coming
        // back to that page then showed an empty diagram until something rebuilt the cache.
        container.setVisible(true);

        if (failsafe != null) failsafe.stop();
        if (grace != null) grace.stop();
    }
    
    private boolean cacheable = false;
    
    /**
     * The panel a diagram is built into, which paints the trains last (OB-159).
     *
     * Adam: "it is a z order issue.  The stations paint over the locomotives."
     *
     * Swing paints children in one order and has no notion of layers, and the two things that want to
     * be in front - the station caption and the locomotive standing under it - are on different
     * components.  Whichever is given the front, one of Adam's two reports comes back: captions in
     * front and the locomotive is painted over (OB-159); the tile in front and the NAME is painted
     * out, because a tile is opaque (OB-117).
     *
     * So the trains are not a component's z-order at all.  The children paint in the order they
     * always did, and then every tile is asked for its train, which lands over both.
     *
     * Public and static so a test can build one out of plain components and look at what comes out -
     * which is the whole of what this has to get right and needs no railway to establish.
     *
     * @return a panel that draws its tiles, then its captions, then its trains
     */
    public static JPanel newDiagramContainer()
    {
        return new JPanel()
        {
            @Override
            protected void paintChildren(java.awt.Graphics g)
            {
                super.paintChildren(g);

                for (java.awt.Component child : getComponents())
                {
                    if (child instanceof LayoutLabel)
                    {
                        ((LayoutLabel) child).paintTrainOverCaptions(g);
                    }
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
        // Is THIS grid inside an editor - not "is an editor open somewhere".
        //
        // layout.getEdit() alone is the second question. It is the flag the two editors share for their
        // mutual exclusion, so while either was open every grid on screen answered yes, the viewer
        // included: it drew the editor's grey grid around its squares (Adam: "the BUG where the VIEWER
        // gets a grid is still there"), greyed its captions, dropped its hand cursors and its tooltips,
        // and attached mouse listeners that cast their parent to LayoutEditor - which the viewer is not.
        //
        // One line above already asked it this way, as a conjunction, and was right. Everything else in
        // this constructor asked the short version and was wrong in the viewer.
        final boolean inEditor = layout.getEdit() && master instanceof LayoutEditor;

        // Before anything else touches the panel: whatever was drawn here is being replaced, and a
        // replaced grid with timers still armed fires into a panel that is no longer its own.
        this.owner = parent;
        this.window = ui;

        if (parent != null)
        {
            java.lang.ref.WeakReference<LayoutGrid> was =
                LIVE.put(parent, new java.lang.ref.WeakReference<>(this));

            LayoutGrid outgoing = was == null ? null : was.get();

            replacing = outgoing != null && outgoing != this;

            if (replacing) outgoing.discard();
        }

        // Which editor this is.  layout.getEdit() is true in BOTH - the autonomy editor borrows the
        // diagram editor's edit flag for its mutual exclusion - so keying label rendering on it changed
        // the track diagram editor as well, where the raw "Point:" text is exactly what the user needs
        // to see and edit.
        // No station captions at all in the TRACK diagram editor (FR-030).
        //
        // Adam: "in the track diagram editor, hide autonomy labels completely."  That editor is about
        // where the rails are, and a caption there is an autonomy object drawn over the thing being
        // moved - it cannot be edited from that window, it covers the square underneath, and every
        // one of them is in the way of the one job that window has.
        //
        // Decided before `captioned` is read rather than at each of the four places that draw one:
        // a rule enforced at the point of use is a rule with four chances to be forgotten, and this
        // file has form.
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
        
        parent.removeAll();
        
        // We need a non scaling panel for small layouts
        // if (width * size < parent.getWidth() || height * size < parent.getHeight() || popup)
        // {
            container = newDiagramContainer();
            container.setBackground(Color.white);

            // The live list, so whatever this grid registers later is reachable from the container
            container.putClientProperty(CAPTIONS_REGISTERED, registeredCaptions);
            
            // Things mess up without this
            //
            // "Being edited" is a fact about the DIAGRAM and is therefore true in both windows at once
            // - the main window shares the LayoutDiagram with the editor - and it is the wrong question
            // for anything about THIS grid, which is what LAYOUT is: how this particular grid is
            // arranged.
            //
            // This used to add "that is right for clickability below, where the viewer's tiles must
            // stop routing clicks while an editor owns the page". It is not right any more and was not
            // meant to be: the line it points at passes `inEditor`, so the viewer's tiles stay
            // clickable while an editor is open, which is the whole point of asking the longer
            // question. Corrected rather than deleted because this is the only written record of why
            // the flag is shared at all (NR-7).
            //
            // MT-106: the two were the same flag, so any repaint of the viewer while an editor was
            // open re-laid the viewer the editor's way. It went unnoticed while the viewer was only
            // ever rebuilt after the editor closed; the editor now stays open across a page or mode
            // switch, and the teardown that runs on each one rebuilds it.
            //
            // So this asks whether THIS grid is in an editor, which is the question it was always
            // trying to ask.
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
        // Adam: "coordinates are referenced in issues but not visible to the user.  add a grid around
        // the diagram."  Drawn as a border rather than as two more rows of cells - see AxisRuler for
        // why - so the grid inside is exactly what it was and every caller that addresses it by
        // coordinate is unaffected.
        //
        // The numbers are the DIAGRAM's, not the cell indices: `offsetX`/`offsetY` are where its
        // left-most and top-most track actually are, which is what makes the printed number the one an
        // issue would quote.  `width` and `height` have already been incremented for the spacer row and
        // column, which hold nothing, so the ruler is told the real counts.
        //
        // Read from the preference on every build, which is what makes the toggle a redraw rather than
        // a special case.
        if (TrainControlUI.getPrefs().getBoolean(TrainControlUI.SHOW_COORDINATES_PREF,
            SHOW_COORDINATES_DEFAULT))
        {
            container.setBorder(new AxisRuler(size, offsetX, offsetY, width - 1, height - 1));
        }
        else
        {
            container.setBorder(null);
        }

        // Generate grid
        container.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints(); 

        // AND THE ROOM THE RULER ASKED FOR, added to the panel rather than taken out of the diagram
        // (FR-057, Adam: "I don't see the axis labels in the editor grid").
        //
        // A border paints inside the component's own bounds, so the eighteen pixels it reserves come
        // out of whatever the component was given.  These three numbers were all `width * size` by
        // `height * size` - the grid exactly - so the gutter was carved out of the diagram: the tiles
        // were pushed right and down inside a panel that had not grown, and the numbers went under the
        // edge of the scroll pane.  The border was there the whole time and could not be seen, which
        // is a worse failure than not drawing it at all.
        //
        // `maxWidth` and `maxHeight` are what the EDITOR sizes its own panel from, so they have to
        // carry the gutter too - otherwise the panel is the old size and the container overflows it.
        java.awt.Insets gutter = container.getBorder() == null
            ? new java.awt.Insets(0, 0, 0, 0) : container.getBorder().getBorderInsets(container);

        int fullWidth = width * size + gutter.left + gutter.right;
        int fullHeight = height * size + gutter.top + gutter.bottom;

        container.setSize(fullWidth, fullHeight);
        container.setMaximumSize(new Dimension(fullWidth, fullHeight));

        maxWidth = fullWidth;
        maxHeight = fullHeight;
               
        grid = new LayoutLabel[width][height];
                       
        for(int y = 0; y < height; y++)
        {
            for(int x = 0; x < width; x++)
            {
                // GBC fix - we create a dummy column at the end with nothing in it to ensure long labels don't misalign things
                if (x == (width - 1) || y == (height - 1))
                {
                    grid[x][y] = new LayoutLabel(null, master, size, ui, false);

                    // Not a square, so not part of the grey grid (OB-055).
                    //
                    // Adam: "when grid is turned on, there is a grid on an extra row at the bottom, and
                    // an extra half column on the right.  the half column starts halfway down and has
                    // the same number of cells, but each is only at half height."  That is this row and
                    // this column: they hold nothing, so GridBagLayout gives them whatever is left
                    // rather than a square's worth, and the border made them visible.
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

                // The square this cell is, and the station whose caption belongs on it.
                //
                // A caption is no longer text drawn into the diagram - it is part of the autonomy setup,
                // pointing at the sensor's square - so it is asked for rather than found by reading a
                // prefix off a label.  It can therefore sit on a square carrying no component at all,
                // which is why the text below is drawn for a caption as well as for a label.
                final org.traincontrol.automationui.TileGraph.TileKey square =
                    new org.traincontrol.automationui.TileGraph.TileKey(
                        layout.getName(), x + offsetX, y + offsetY);

                final org.traincontrol.automationui.TileGraph.TileKey captioned =
                    hidesCaptions ? null : (ui == null ? null : ui.autonomyCaptionAt(square));

                // The edit value ensures that the icon is disabled in edit mode, and it disables clickability/events
                grid[x][y] = new LayoutLabel(c, master, size, ui, inEditor);
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

                    // Autonomy station caption, live.
                    //
                    // An excluded page is already gone by here, and the clause that used to say so has
                    // been removed with this comment (review, 2026-08-26).  `hidesStationCaptions`
                    // takes the decision once, three hundred lines up, and nulls `captioned` for an
                    // excluded page - so asking again was dead, and the dead half of a condition is
                    // where a rule quietly stops being enforced when the live half changes.
                    //
                    // This also used to promise "the name is still drawn, below, as ordinary text",
                    // which stopped being true when B6 routed exclusion through that one decision:
                    // nothing autonomy knows is drawn on an excluded page now, which is what Adam asked
                    // for ("hide captions", 2026-08-25).  Writing of the user's OWN is untouched - it
                    // was never autonomy's to hide.
                    if (captioned != null && !inEditor)
                    {
                        // Blank until autonomy says otherwise.
                        //
                        // A caption belongs to the running setup: it shows what is standing at that
                        // station, and with nothing loaded there is no answer to give.  Seeding it
                        // with the station's name instead was tried and was worse - an unloaded
                        // diagram covered in names that look like live captions and are not, which is
                        // the placeholder state this comment exists to stop somebody restoring.
                        text.setText("");

                        // This callback will populate the label
                        ui.addLayoutStation(captioned, text, parent);

                        registeredCaptions.add(text);
                        text.setToolTipText(captionName);

                        final org.traincontrol.automationui.TileGraph.TileKey station = captioned;

                        text.addMouseListener(new MouseAdapter()
                        {
                            // The name is a component of its own, stacked on top of the square, and the
                            // keyboard's idea of what is hovered comes from the square's listener - which
                            // gets mouseEXITED the moment the pointer moves onto the name.  So Control+V
                            // over the platform worked and Control+V over the name did nothing, when the
                            // name is the larger and more obvious of the two things to aim at.
                            //
                            // Reports the STATION rather than whichever square the text happens to sit on:
                            // a caption may be drawn on blank space beside its platform, and pointing at a
                            // station's name means that station either way.
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
                                // Adam: "double clicking station label in track viewer should activate
                                // that locomotive (as if it was selected on the key mappings) if it is
                                // mapped, not open the editor."
                                //
                                // This used to open the autonomy setup at the station, deliberately.
                                // He has ruled the other way, so the branch is gone rather than given
                                // another condition - what is left is the ordinary left-click path
                                // below, which is exactly the behaviour he describes: jumpToLocomotive
                                // switches to the locomotive\u2019s mapping page and selects its button, and
                                // does nothing when it has no mapping.
                                //
                                // The setup is not stranded. The right-click menu on this same square
                                // still opens the full editor, and it is the door that explains itself
                                // when it has to refuse - which is why the removed branch had to carry
                                // a copy of that reasoning, and why the copy goes with it.

                                if (e.getButton() == MouseEvent.BUTTON3)
                                {
                                    LayoutRightclickAutonomyMenu.showFor(ui, station, station,
                                        e.getComponent(), e.getX(), e.getY());
                                }
                                // Left-clicking a station will activate its locomotive
                                else
                                {
                                    // By SQUARE.  A square where trains may turn round is several
                                    // Points, none of them called what the diagram says, and a lookup
                                    // by the text of the caption simply returned null.
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
                    else if (!layout.getEditHideText())
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

                            // WHERE THE TRAIN LIVES, if that is what has been asked for (MT-261
                            // ruling 2, R28-C3).
                            //
                            // Adam: "labels show the home locomotive in white to indicate it's not
                            // there (and black/no change if it's there)."  Nothing in 3.0.0 drew the
                            // home assignment at all, so the only way to see where a locomotive
                            // belonged was to open one square's menu at a time.
                            //
                            // Asked FIRST, because it is the more specific question: a caption has
                            // room for one name, and somebody who has turned this on is looking at
                            // homes rather than at station names or at what happens to be parked.
                            // Squares with no home fall through to whichever of the other two is on.
                            boolean homes = master instanceof LayoutEditor
                                && ((LayoutEditor) master).getAutonomyPanel() != null
                                && ((LayoutEditor) master).getAutonomyPanel().isShowingHomeLocomotives();

                            String home = homes ? ui.autonomyHomeAt(captioned) : null;

                            if (home != null)
                            {
                                text.setText(stationCaption(home, ""));

                                // AWAY IS THE DARK ONE, which is how the name comes out white.
                                //
                                // `StationCaption.onPill` picks a foreground that reads on whatever
                                // fill it is given, so the colour is chosen by naming the FILL rather
                                // than by hard-coding a white that would vanish on the resting pill.
                                // Home and standing here gets the ordinary resting fill and its
                                // ordinary near-black text - "no change if it's there".
                                String standing = ui.autonomyLocomotiveAt(captioned);

                                boolean athome = home.equals(standing);

                                standingTrain = true;

                                labelColour = athome ? Color.BLACK : Color.WHITE;

                                awayFromHome = !athome;
                            }
                            else if (naming)
                            {
                                // The station's own name, which is what the track editor used to show
                                // and what this window is for. Greyed like the placeholder was: it is
                                // a label on the diagram rather than something set here.
                                text.setText(captionName == null ? LAYOUT_STATION_EMPTY : captionName);

                                labelColour = captionName == null
                                    ? new Color(150, 150, 150) : Color.BLACK;

                                standingTrain = captionName != null;
                            }
                            else
                            // What the SETUP puts on this square, which is the question the editor is
                            // about.  The running diagram shows what is on the rails; here there may be
                            // no run at all, and a platform with a train assigned to it was drawing the
                            // empty placeholder - so the one view where placements are made was the one
                            // view that did not show them.
                            {
                            String placed = ui.autonomyCaptionTextAt(captioned);

                            if (placed != null)
                            {
                                text.setText(placed);

                                // The running diagram's own style for a named train: black on
                                // translucent white, so it reads over whatever tile art is underneath.
                                //
                                // Applied AFTER the generic setBackground(WHITE) below rather than
                                // here.  Set here it was overwritten by it - and because this is the
                                // one branch that also turns opacity ON, the result was the only
                                // label on the diagram painting a solid white rectangle, which is the
                                // exact thing a translucent style exists to avoid.
                                standingTrain = true;

                                labelColour = Color.BLACK;
                            }
                            else
                            {
                                // The placeholder the running diagram shows when nothing is standing
                                // there - so the square looks like what it will look like.
                                text.setText(LAYOUT_STATION_EMPTY);

                                // Greyed HERE and nowhere else.  This placeholder says only "a caption
                                // lands on this square", and in the editor it sits on top of the arrows
                                // that say which way trains may arrive - which are the thing somebody
                                // has opened the editor to look at.  Kept rather than hidden: where the
                                // captions are is worth seeing while arranging them.
                                //
                                // A NAMED train is not that: it is the answer, not a placeholder, and
                                // greying it would hide the thing the user just set.
                                labelColour = new Color(150, 150, 150);
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
                        else if (own != null && own.startsWith(LAYOUT_STATION_PREFIX))
                        {
                            // A station label on a diagram autonomy cannot act on.
                            //
                            // These come from the old autonomy, which wrote "Point:Bahnhof" straight
                            // onto the diagram, and they are still there on a layout served by the
                            // Central Station - where there is no local folder, so no setup, so nothing
                            // ever turns them into captions.  Drawn raw they showed the user an
                            // internal marker they never typed and cannot remove from that machine.
                            //
                            // The name without its marker is what they meant by it, and is what the
                            // caption would have said had autonomy been able to read it.
                            text.setText(own.substring(LAYOUT_STATION_PREFIX.length()));

                            if (autonomyEditor) labelColour = new Color(150, 150, 150);
                        }
                        else
                        {
                            // Everything else: what the user wrote there.
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
                        // onPill and not readableOn: the branches above choose a grey for a
                        // placeholder and a black for a name, deliberately, and readableOn threw both
                        // away because its condition is a superset of all of them.
                        //
                        // This said "the distinction survives now" for two days before it was true.
                        // onPill kept RED and let grey fall through to the same readableOn, so a
                        // placeholder and a name came out the identical colour - found by a review that
                        // did not believe the comment and ran it.  onPill dims a neutral grey towards
                        // the pill now, and `testAPlaceholderStaysDimmerThanAName` is what keeps this
                        // sentence honest.
                        text.setForeground(
                            StationCaption.onPill(StationCaption.restingFill(), labelColour));

                        // A tenth smaller than the diagram's other text: the pill is taller than what
                        // it holds, and the rows of a diagram are one tile apart.
                        text.setFont(text.getFont().deriveFont(
                            text.getFont().getSize2D() * StationCaption.FONT_SCALE));
                    }

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
                        // Adam asked for the name "in white to indicate it's not there".  White ink on
                        // the resting pill - which is pale - would be invisible, so the fill is what
                        // changes and `onPill` below picks the readable foreground for it.  The result
                        // is the white he asked for, on a caption that can still be read.
                        Color fill = text.isPill()
                            ? (awayFromHome ? AWAY_FROM_HOME_FILL : StationCaption.restingFill())
                            : new Color(255, 255, 255, LAYOUT_STATION_OPACITY);

                        text.setBackground(fill);

                        text.setForeground(
                            StationCaption.onPill(text.getBackground(), labelColour));
                    }
                    
                    // Where a pill lands, and out of the row's baseline - for EVERY caption.
                    //
                    // This lived inside the branch below, which asks `c != null`: a caption drawn on a
                    // BLANK square therefore kept the baseline anchor and got no placement at all,
                    // which is both the wrong position and the live half of OB-115. Blank squares are
                    // not a curiosity - the caption placer calls one "the most readable place of all"
                    // and picks it second.
                    if (text.isPill())
                    {
                        // Adam: "land them so that they align just below straight tracks if the track
                        // goes east to west, or centered over the track if north to south." An
                        // east-west rail runs across the middle of the square, so a caption on the
                        // middle would sit on it; a north-south rail runs UP it, and a caption beneath
                        // would be beside the track rather than on it. A blank square has no rails to
                        // be placed against, so it takes the east-west answer.
                        //
                        // A border and not gbc.anchor: this cell is declared gridheight = 0 -
                        // REMAINDER - so it runs from this row to the bottom of the diagram, and
                        // anchoring SOUTH inside it means the bottom of the PAGE.
                        //
                        // NORTHWEST because a caption has no business voting on the row's baseline.
                        // BASELINE_LEADING lines up every component anchored that way, so a caption
                        // whose height changed moved the row's baseline and took its neighbours with
                        // it - three pixels, on labels nobody had touched (OB-115).
                        // ROTATED for track that runs up the square (Adam, 2026-08-27), which is
                        // also what decides which way the caption is centred and which way it is
                        // offset - so the orientation is asked once, here, and the caption is told.
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
                    if (c != null && !c.isText() && !layout.getEditHideText())
                    {
                        //text.setBorder(new EmptyBorder(16 * (size / 30), 0, 0, 0)); //top, left, bottom, right
                        gbc.gridheight = 0;

                        // A caption with a train on it is left as PLAIN text, which is the one thing
                        // that made the editor's version of this label look different from the running
                        // diagram's.  It is the same label, with the same styling; what differed was
                        // the string.
                        //
                        // On the running diagram the caption is registered EMPTY here and its text set
                        // afterwards by updateStationLabels, so it never reaches the wrap below.  The
                        // editor has no run to wait for, so it sets the text now - and picked up the
                        // wrap on the way past.  A leading <br> makes the label two lines tall and the
                        // &nbsp; stops it wrapping, so an OPAQUE label - which this is the only one of
                        // - painted its background as a block a tile tall and three wide, over
                        // whatever was beside it.
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
                    
                    // DRAGGABLE, in the autonomy editor, and only a square that carries a caption
                    // (FR-035).  Not tiles and not the user's own writing: Adam asked for station
                    // labels only.
                    // AND THE LABELS ARE ACTUALLY DRAWN (OB-139).
                    //
                    // `captioned` is the caption OBJECT, which exists whether or not the text layer is
                    // showing - so with Text Labels unticked the pills disappear and every square that
                    // had one went on offering a move cursor for something invisible. A drag begun
                    // there would move a label the user cannot see.
                    //
                    // Not installed at all rather than installed without its cursor: the cursor IS the
                    // diagram saying the thing can be picked up, and the two must not disagree. The
                    // grid is rebuilt when the box is toggled, so ticking it back restores the drag.
                    if (autonomyEditor && captioned != null && master instanceof LayoutEditor
                        && !layout.getEditHideText())
                    {
                        org.traincontrol.automationui.TileGraph.TileKey here =
                            new org.traincontrol.automationui.TileGraph.TileKey(
                                layout.getName(), x, y);

                        // The station this label NAMES, which is usually not the square it is on.
                        //
                        // A caption goes on blank space beside its platform, because a platform road
                        // rarely has room for the text - so asking the square underneath what station
                        // it is answers "none" for precisely the labels worth asking about. What the
                        // caption was built from is the answer.
                        text.setToolTipText(captionName);

                        // BOTH the label and the square under it (Adam, 2026-08-27: "have it fire on
                        // the label or the tile, so the mouse icon is more clearly shown").  A pill is
                        // a small target, and a move cursor that only appears over those few pixels
                        // gives the diagram almost no way of saying a label can be picked up.
                        dragCaption(text, text, grid[x][y], here, (LayoutEditor) master, container);
                        dragCaption(text, grid[x][y], grid[x][y], here, (LayoutEditor) master,
                            container);
                    }

                    container.add(text, gbc);
                    container.setComponentZOrder(text, 0);

                    // And back to this square's own column.
                    //
                    // Centring a caption moves gbc.gridx a column LEFT so the pill can start before
                    // its square. gbc is then reused by the address label below, which pays nothing
                    // back - so on every captioned, clickable square the sensor's number was drawn a
                    // whole tile away from the sensor, over its neighbour's. Fifteen of them on one
                    // page of the operator's layout.
                    //
                    // The bounds harness that checked FR-028 could not see this: it built its grids
                    // with the address labels switched OFF, so it measured a diagram this cannot
                    // happen on and reported that nothing had moved. A harness only answers about the
                    // diagram you point it at.
                    //
                    // gridy is paid back for exactly the same reason, and was added with the rotation
                    // that first moved it (2026-08-27). The bug above is what this line is: a shift
                    // taken out for the caption and left in the constraints for whoever came next.
                    gbc.gridx = x;
                    gbc.gridy = y;
                }
                
                // Show address labels
                if (c != null &&
                        layout.getShowAddress() &&
                        !c.isText() && c.isClickable())
                {
                    JLabel text = new JLabel();
                    
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
                    });

                    text.setForeground(Color.RED);
                    text.setOpaque(true);
                    text.setBackground(new Color(255, 255, 255, LayoutGrid.LAYOUT_ADDRESS_OPACITY)); // yellow
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
                        protocol = "<br>" + c.getProtocol().toString().toLowerCase() + "";
                    }
                    
                    text.setText("<html>" + c.getLogicalAddress() + redOrGreen + protocol + "</html>");      
                    
                    container.add(text, gbc);
                    container.setComponentZOrder(text, 0);
                }
                                                                              
                // Register the tile with autonomy, if it is watching.
                //
                // Here because this is the only place that knows both the page and the square: a
                // LayoutLabel is told neither, and LayoutGrid keeps no reference to the LayoutDiagram
                // after the constructor returns.
                // Which SQUARE this label is, told to the label itself - every label, including the
                // ones with nothing drawn on them.
                //
                // Its right-click menu used to ask the main window which page was showing, which is the
                // wrong page in every popup window; and the keyboard shortcuts ask which square the
                // pointer is over, which a blank label could not answer at all.  A station's name is
                // usually drawn on blank space beside the platform, so those blanks are exactly the
                // squares somebody aims at.
                if (ui != null)
                {
                    grid[x][y].setAutonomySquare(layout.getName(), x + offsetX, y + offsetY);
                }

                if (c != null && ui != null)
                {

                    if (ui.getDiagramTileRegistry() != null)
                    {
                        ui.getDiagramTileRegistry().register(
                            new org.traincontrol.automationui.TileGraph.TileKey(layout.getName(), c.getX(), c.getY()),
                            grid[x][y]);
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
            parent.add(container);

            showWhenTilesAreReady(parent, ui);
        } 
    }

    /**
     * Holds the diagram back until its track has been decoded, showing a spinner in the meantime.
     *
     * A diagram used to arrive in two stages: text labels immediately, because text needs no image,
     * then the track a second or so later as the decodes came back off the pool.  The staging is an
     * accident of how the work is split, not something the user asked to watch, and while it lasts the
     * labels appear to float on nothing.
     *
     * Only when there is actually a wait.  The second time a page is opened every tile is a cache hit,
     * whenTilesSettled runs on the next EDT pass, and swapping a spinner in and straight out again
     * would be a flicker where there had been none - so the spinner is only mounted if the tiles are
     * still not ready a moment later.
     *
     * @param parent the panel the grid was just added to
     * @param ui the window whose tile loader is doing the decoding
     */
    private void showWhenTilesAreReady(JPanel parent, TrainControlUI ui)
    {
        if (ui == null) return;

        // Nothing to wait for, so nothing to hide (OB-109).
        //
        // Adam: "when placing new tiles in the track diagram editor, the diagram sometimes flickers."
        // Every placement rebuilds the whole grid, and on the second and every later build each tile
        // is a cache hit - so this hid the diagram and whenTilesSettled gave it back on the next EDT
        // pass. A paint landing between those two is the blink, and whether one does depends on where
        // the event thread happens to be, which is the "sometimes".
        //
        // It has to be asked HERE and not earlier: LayoutLabel counts a decode before submitting it,
        // deliberately, so that a grid asking this question after building its labels cannot see zero
        // while work it caused is still on its way to the pool. Every label above is built by now.
        //
        // A cold build is untouched - the count is not zero, and the diagram is held back and the
        // spinner armed exactly as before. Making the HIDE wait alongside the spinner instead would
        // have cost that: 120ms of labels floating on nothing, which is the fault the hold-back was
        // written to remove.
        if (ui.tilesAreSettled()) return;

        // And a diagram already on screen is not taken away to be rebuilt (OB-109).
        //
        // The hold-back was written for a page ARRIVING - "a diagram used to arrive in two stages" -
        // and hiding one that is already drawn is the opposite of what it is for. The editor rebuilds
        // the whole grid after every placement, so the first tile of a type nobody has drawn at this
        // size is one decode, and one decode was taking the entire page off the screen.
        //
        // Not "never hide" on a replacement either: changing the tile SIZE re-keys every image, and a
        // page arriving square by square is the thing the spinner exists for. So a replacement waits
        // with the spinner instead. If the rebuild is quick - which every placement is - the reveal
        // gets there first and nothing is ever hidden; if it is slow, the diagram goes behind the
        // spinner 120ms in, exactly as a cold page does.
        if (!replacing) container.setVisible(false);

        final LoadingSpinner spinner = new LoadingSpinner();

        // Sized to the space the diagram will take, so nothing jumps when the two are swapped - and
        // so the glass lands in the MIDDLE of it (OB-129).
        //
        // The 400 cap that used to be here defeated both. The parent is laid out with FlowLayout,
        // which aligns to the top, so a 400-square box over a larger diagram sat at the top of the
        // page rather than over the middle of it. The glass is centred within this component already;
        // what was wrong is how much of the page the component covered. Its drawn size no longer
        // follows the component - see MAX_GLASS_H.
        spinner.setPreferredSize(new Dimension(maxWidth, maxHeight));

        final boolean[] revealed = {false};

        final Runnable reveal = () ->
        {
            // Nothing from a grid that has been replaced.  See discard().
            if (revealed[0] || discarded) return;

            revealed[0] = true;

            parent.remove(spinner);

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

            parent.revalidate();
            parent.repaint();
        });

        grace.setRepeats(false);
        grace.start();
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
