package org.traincontrol.gui;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.DiagramColours;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.widgets.StationCaption;

/**
 * What the track diagram's station captions say: the station, train and home a square shows, asked of the setup, and
 * the words and colors a running Point writes onto its labels.  The labels themselves stay registered on the window.
 */
final class StationCaptions
{
    private final TrainControlUI ui;

    StationCaptions(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * The station whose caption is drawn on a square, asked of the setup rather than read off the label; null when
     * none.
     */
    TileGraph.TileKey autonomyCaptionAt(
        TileGraph.TileKey where)
    {
        AutonomySession session = this.ui.getAutonomySession();

        if (session == null) return null;

        TileGraph.TileKey caption = session.getCaptionTarget(where);

        // No label on a square that is both empty and shut; the setup check leaves it alone too (OB-341).

        // One name per station per page, the offset one winning (MT-337): the store can hold more than the editor
        // allows, and a caption on another page is fine.  A station captioned on its own square and beside it on the
        // same page is drawn once, the beside one; two offset captions are both drawn and the setup check reports them
        // as an error.  The rule is the session's (`isCaptionDrawn`).
        if (caption != null && !session.isCaptionDrawn(where)) return null;

        return caption;
    }

    /** The locomotive the SETUP puts on a square, not the one standing there; null when the setup puts none there. */
    String autonomyLocomotiveAt(TileGraph.TileKey station)
    {
        AutonomySession session = this.ui.getAutonomySession();

        // The session knows the shape of a placement, which is a JSONObject, not a plain string.
        return session == null ? null : session.getLocomotiveNameAt(station);
    }

    /** Which locomotive LIVES at a square, not which is standing on it (MT-261); null with no home or no setup. */
    String autonomyHomeAt(TileGraph.TileKey station)
    {
        AutonomySession session = this.ui.getAutonomySession();

        if (session == null || station == null) return null;

        Object stored = session.getPointProperty(station, "home");

        return stored instanceof String && !((String) stored).trim().isEmpty()
            ? (String) stored : null;
    }

    /**
     * The caption for a square the setup puts a locomotive on, bracketed and cut to length.  The facing comes from the
     * square, as in the editor there may be no run.  Null when the setup puts none there.
     */
    String autonomyCaptionTextAt(TileGraph.TileKey station)
    {
        String name = autonomyLocomotiveAt(station);

        return name == null ? null : LayoutGrid.stationCaption(name, facingArrow(station));
    }

    /**
     * The station's own name for a caption when no train is standing there; null when the square is not a Point of this
     * setup.
     */
    String autonomyStationNameAt(TileGraph.TileKey station)
    {
        AutonomySession session = this.ui.getAutonomySession();

        return session == null ? null : session.pointNameForTile(station);
    }

    /**
     * The running Point on a square, preferring one with a train on it (a square is several Points, only one holds the
     * locomotive).  By square, not caption text, which fails for a renamed or split station.  Null if nothing is there.
     */
    Point getAutonomyPointForTile(
        TileGraph.TileKey station)
    {
        // Asked once, not built: asking whether there is a railway and then for it builds an empty one where Unload
        // clears it between.
        Layout shown = this.ui.getModel().getAutoLayoutIfLoaded();

        if (shown == null || station == null) return null;

        AutonomySession session = this.ui.getAutonomySession();

        return session == null ? null : session.getStationIndex().speakerAt(shown, station);
    }

    /**
     * Every locomotive on a square, in copy order (two trains can share a platform from opposite ends); empty when
     * none.
     */
    List<Point> getAutonomyOccupantsForTile(
        TileGraph.TileKey station)
    {
        Layout shown = this.ui.getModel().getAutoLayoutIfLoaded();

        if (shown == null || station == null)
        {
            return Collections.<Point>emptyList();
        }

        AutonomySession session = this.ui.getAutonomySession();

        return session == null ? Collections.<Point>emptyList() : session.getStationIndex().occupantsAt(shown, station);
    }

    /**
     * Which way the train standing on a square is pointing, as one arrow ("" when nobody has said).  Read from the
     * setup: asking the running graph which copy a Point is would build the naming once per Point per feedback event.
     */
    private String facingArrow(TileGraph.TileKey square)
    {
        AutonomySession session = this.ui.getAutonomySession();

        // Only a facing the track runs (OB-348); the icon cannot be drawn along any other
        return session == null || square == null ? "" : arrow(session.recordedFacingAlongTheTrack(square));
    }

    /** A space and the side's arrow, or "" for none. */
    private static String arrow(TilePorts.Side facing)
    {
        if (facing == null) return "";

        switch (facing)
        {
            case N: return " " + StationCaption.ARROW_N;
            case S: return " " + StationCaption.ARROW_S;
            case E: return " " + StationCaption.ARROW_E;
            case W: return " " + StationCaption.ARROW_W;
            default: return "";
        }
    }

    /** A square with several trains: each name cut so the pair fits where one does; the facing is per TRAIN. */
    private String crowdedLabel(List<Point> crowd,
        TileGraph.TileKey square)
    {
        // No brackets, as the single-train caption has none (FR-028).
        StringBuilder text = new StringBuilder();

        // Room left for the names after the arrows and bars, budgeted from the whole length.
        int furniture = 3 * crowd.size();

        int room = Math.max(3,
            (LayoutGrid.LAYOUT_STATION_MAX_LENGTH - furniture) / Math.max(1, crowd.size()));

        for (Point at : crowd)
        {
            Locomotive standing = at.getCurrentLocomotive();

            if (standing == null) continue;

            if (text.length() > 0) text.append("|");

            String name = standing.getName();

            text.append(StationCaption.withArrow(
                name.substring(0, Math.min(name.length(), room)).trim(),
                facingArrowOf(at, square)));
        }

        return text.toString();
    }

    /** The arrow for one train, or "": the copy it stands on IS its direction; falls back to the square's facing. */
    private String facingArrowOf(Point at,
        TileGraph.TileKey square)
    {
        AutonomySession session = this.ui.getAutonomySession();

        if (session == null || at == null) return facingArrow(square);

        // Through the session, so this and the facing menu agree (OB-181); asked, not built (Unload left it empty).
        TilePorts.Side facing = session.facingOnTheRailway(square,
            this.ui.getModel() == null ? null : this.ui.getModel().getAutoLayoutIfLoaded());

        // A crowded square speaks for the copy handed in, not for the first occupied one.
        if (facing == null) facing = session.getStationIndex().facingsAt(square).get(at.getName());

        // Else its only way out, as the icon has it (OB-510)
        if (facing == null) facing = session.getStationIndex().onlyWayOutAt(square);
        return facing == null ? facingArrow(square) : arrow(facing);
    }

    /** Writes one Point's autonomy state onto the diagram's station labels; gated on a setup running, not a window. */
    void updateStationLabels(Point point)
    {
        // A caption points at the Point's square; the Point's own name misses once a station is several Points.
        AutonomySession labelSession = this.ui.getAutonomySession();

        final TileGraph.TileKey named = labelSession == null ? null : labelSession.tileForPointName(point.getName());

        // Falls back to the square the railway built, where the setup no longer knows the name.
        final TileGraph.TileKey square = named != null || labelSession == null ? named
            : AutonomyCompanionStore.parseTileKey(point.getSquare());

        // Whichever copy holds a train speaks for the square; an empty sibling wrote "[---]" over an occupied platform.
        final Point speaking = this.getAutonomyPointForTile(square);

        final Point p = speaking == null ? point : speaking;

        final List<Point> crowd = this.getAutonomyOccupantsForTile(square);

        final Layout shown = this.ui.getModel().getAutoLayoutIfLoaded();

        if (shown != null && !this.ui.getLayoutStations(square).isEmpty() && this.ui.activeDiagramConfiguration != null)
        {
            Point destination = shown.getDestination(p.getCurrentLocomotive());
            Point start = shown.getStart(p.getCurrentLocomotive());
            Locomotive current = p.getCurrentLocomotive();
            List<Point> milestones = shown.getReachedMilestones(current);
            
            // At once on the event thread, so a rebuilt page's first paint already has its captions.
            final Runnable apply = () ->
            {
                // This will exclude locked points
                for (JLabel j : this.ui.getLayoutStations(square))
                {
                    // A pill paints its own fill and stays transparent; any other caption fills its own background.
                    boolean pill = j instanceof StationCaption && ((StationCaption) j).isPill();

                    String text;
                    Color fg, bg;

                    // Two trains on one platform: no one route coloring fits both, so name both and say no more.
                    if (crowd.size() > 1)
                    {
                        text = crowdedLabel(crowd, square);
                        fg = Color.BLACK;
                        bg = StationCaption.restingFill();
                    }
                    else if (current == null)
                    {
                        // A placeholder gray, which `StationCaption.onPill` dims below a name.
                        text = LayoutGrid.LAYOUT_STATION_EMPTY;
                        fg = new Color(150, 150, 150);
                        bg = StationCaption.restingFill();
                    }
                    else if (milestones == null)
                    {
                        // The arrow comes from the COPY the train stands on, not the square's stored facing.
                        text = LayoutGrid.stationCaption(current.getName(), facingArrowOf(p, square));
                        fg = Color.BLACK;
                        bg = StationCaption.restingFill();
                    }
                    else if (p.equals(destination))
                    {
                        text = LayoutGrid.stationCaption(current.getName(), facingArrowOf(p, square));
                        fg = Color.BLACK;
                        bg = StationCaption.DESTINATION_FILL;
                    }
                    else if (p.equals(start))
                    {
                        text = LayoutGrid.stationCaption(current.getName(), facingArrowOf(p, square));
                        fg = new Color(255, 255, 255);
                        bg = new Color(160, 160, 160, LayoutGrid.LAYOUT_STATION_OPACITY);
                    }
                    else
                    {
                        // No locomotive name at intermediate stations.  One arrow (OB-158): the route's COPY IS the
                        // direction of travel; the bullets are the fallback where no facing is recorded.
                        String through = facingArrowOf(p, square).trim();

                        text = through.isEmpty() ? LayoutGrid.LAYOUT_STATION_OCCUPIED : through;

                        // Route colors: ahead's blue until the train arrives, then driven gray.
                        fg = milestones.contains(p) ? DiagramColours.PATH_DRIVEN : DiagramColours.PATH_AHEAD;
                        bg = StationCaption.restingFill();
                    }

                    // On a pill the text color must follow the fill: black on navy is invisible (FR-028).
                    if (pill) fg = StationCaption.onPill(bg, fg);

                    // Only what differs: each set repaints this caption, and a run asks five times a second.
                    if (j.isOpaque() == pill)
                    {
                        j.setOpaque(!pill);
                        j.repaint();
                    }

                    if (!text.equals(j.getText())) j.setText(text);
                    if (!fg.equals(j.getForeground())) j.setForeground(fg);
                    if (!bg.equals(j.getBackground())) j.setBackground(bg);
                }
            };

            if (SwingUtilities.isEventDispatchThread()) apply.run();
            else SwingUtilities.invokeLater(apply);
        }
    }
}
