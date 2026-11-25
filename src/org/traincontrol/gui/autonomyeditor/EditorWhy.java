package org.traincontrol.gui.autonomyeditor;

import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileAnnotation.ToolMark;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.util.SettledQuestion;
import org.traincontrol.gui.autonomy.DestinationTable;
import org.traincontrol.gui.autonomy.DestinationTable.Status;
import org.traincontrol.util.I18n;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The autonomy editor's answer to Why Not Moving?: for the train on the clicked square, every station it might be
 * sent to, by autonomy or by hand, in a table (FR-221), composed on the WhyRenderer thread and handed back to be painted
 * (FR-136).  Reads the panel's fields through `editor`; the railway's monitor is taken by the Layout calls the answer
 * is made of, off the event thread.
 */
final class EditorWhy extends SettledQuestion
{
    private final AutonomyEditorPanel editor;

    EditorWhy(AutonomyEditorPanel editor)
    {
        this.editor = editor;
    }

    /**
     * Composes the answer on a worker thread rather than painting one, into a value object handed over: `traces` is
     * read by the paint, so writing it from here would draw from the wrong thread.
     *
     * @param layout the railway
     * @param index the square-to-Point translation
     * @param setup the edge names the routes are drawn by, read on the event thread
     * @param tile the square that was clicked
     * @return what to say and what to draw
     */
    AutonomyEditorPanel.WhyAnswer composeWhy(Layout layout,
        org.traincontrol.automationui.StationIndex index, AutonomyEditorPanel.WhySetup setup, TileKey tile)
    {
        // Which train is standing here, asked of the layout rather than the setup, whose opinion of where trains are
        // decides what runs.
        org.traincontrol.base.Locomotive standing = null;

        // Through StationIndex, the one place that knows a square is several Points and which ones.
        for (String pointName : index.pointNamesAt(tile))
        {
            Point p = layout.getPoint(pointName);

            if (p != null && p.getCurrentLocomotive() != null)
            {
                standing = p.getCurrentLocomotive();
                break;
            }
        }

        if (standing == null)
        {
            // Empty, so the previous train's lines and marks go: they answered a question about another square.
            return new AutonomyEditorPanel.WhyAnswer(I18n.t("autosetup.ui.whyNoTrainHere"), false,
                new HashMap<TileKey, List<TileAnnotation.Trace>>(),
                java.util.Collections.<TileKey, ToolMark>emptyMap());
        }

        // The lines this answer draws, installed by the paint.  Empty rather than null from here on: an answer about a
        // train that draws nothing has to take the previous answer's lines off the diagram.
        Map<TileKey, List<TileAnnotation.Trace>> drawn = new LinkedHashMap<>();

        // The train's square and the stations, marked by the answer (FR-159)
        Map<TileKey, ToolMark> marks = new HashMap<>();

        marks.put(tile, ToolMark.PICKED);

        // Both tiers at once (FR-221)
        DestinationTable table = new DestinationTable(layout, standing);

        if (table.cannotStart != null)
        {
            return new AutonomyEditorPanel.WhyAnswer(I18n.f("autosetup.ui.whyCannotStart",
                AutonomyEditorPanel.escape(standing.getName()), AutonomyEditorPanel.escape(table.cannotStart))
                + unsavedWarning(), true, drawn, marks);
        }

        // By page first, the train's own first (MT-429)
        final String currentPage = editor.page != null ? editor.page : tile.getPage();

        // Each square's best answer: by square, not by the name shown, which two unnamed squares on one sensor share
        Map<TileKey, Status> best = new LinkedHashMap<>();

        for (String point : table.points())
        {
            TileKey where = index.squareOf(point);

            String station = where == null ? point : editor.describeTile(where);

            // And its page, where that is not the current page: a name with no page is one somebody has to go and look
            // for, or two squares that read as one.
            String nameOnly = station;

            boolean elsewhere = where != null && where.getPage() != null && !where.getPage().equals(currentPage);

            if (elsewhere) station = I18n.f("autosetup.ui.whyStationOnPage", station, where.getPage());

            if (where != null) settle(best, where, table.statusOf(point));

            table.file(point, station,
                where == null || where.getPage() == null ? "2" : elsewhere ? "1" + where.getPage() : "0", nameOnly);
        }

        // Drawn, so "where can it go" is read off the track: the route of a copy that gave its square's answer, green
        // first, so track a purple route shares with a green one stays the green one's.
        Map<TileKey, List<Edge>> routeTo = new HashMap<>();

        for (String point : table.points())
        {
            TileKey where = index.squareOf(point);
            List<Edge> route = table.routeTo(point);

            if (where != null && route != null && best.get(where) == table.statusOf(point)) routeTo.putIfAbsent(where, route);
        }

        for (Status color : new Status[] {Status.CAN_GO, Status.BY_HAND})
        {
            for (Map.Entry<TileKey, Status> square : best.entrySet())
            {
                if (square.getValue() == color && routeTo.containsKey(square.getKey()))
                {
                    drawRoute(drawn, routeTo.get(square.getKey()), setup.edgesByName, color == Status.BY_HAND);
                }
            }
        }

        for (Map.Entry<TileKey, Status> square : best.entrySet())
        {
            marks.putIfAbsent(square.getKey(), square.getValue() == Status.CAN_GO ? ToolMark.CAN_GO
                : square.getValue() == Status.BY_HAND ? ToolMark.BY_HAND : ToolMark.NOT_NOW);
        }

        int available = 0;
        boolean byHand = false;

        for (DestinationTable.Row row : table.rows())
        {
            if (row.status == Status.CAN_GO) available++;

            byHand |= row.status == Status.BY_HAND;
        }

        // A count only, the table names them (D175)
        String name = AutonomyEditorPanel.escape(standing.getName());
        String going = available == 0 ? I18n.f("autosetup.ui.whyReport", name, I18n.t("autosetup.ui.whyNowhere"), "")
            : I18n.f("autosetup.ui.whyCanGo" + I18n.countForm(available), name, available);

        // "Nowhere to go", with nothing after it, is not an answer: "every station refused this train" cannot be told
        // from "there were no stations to refuse it", which need opposite things done.  So when nothing was considered,
        // say what the setup holds.
        String detail = table.points().isEmpty()
            ? "<br>" + AutonomyEditorPanel.escape(I18n.f("autosetup.ui.whyNothingConsidered",
                countStations(layout), countDestinations(layout),
                AutonomyEditorPanel.escape(describeBlock(layout.getLocomotiveLocation(standing)))))
            : "<br>" + table.html(Integer.MAX_VALUE);

        // The reason behind the reason: "No track route leads there", said once per station, is what a severed railway
        // looks like (a switch or signal drawn without an accessory address cannot be routed over), so with nowhere
        // either tier can send it and blocking findings, say that first.
        if (available == 0 && !byHand)
        {
            int blockingFindings = countBlockingFindings();

            if (blockingFindings > 0)
            {
                detail = "<br><b>" + AutonomyEditorPanel.escape(I18n.f("autosetup.ui.whyBlockedBySetup", blockingFindings))
                    + "</b>" + detail;
            }
        }

        return new AutonomyEditorPanel.WhyAnswer(going + detail + unsavedWarning(), true, drawn, marks);
    }

    /**
     * Lays the router's route on the diagram, each stretch of track once (OB-394): where another route already runs
     * through a square the same way this one's line adds nothing, and a line that only ends in a square another runs
     * through is already shown arriving.  Nothing where the route crosses an edge the drawing does not know.
     */
    private void drawRoute(
        Map<TileKey, List<TileAnnotation.Trace>> drawn,
        List<Edge> route,
        Map<String, GraphReducer.ReducedEdge> edgesByName,
        boolean purple)
    {
        List<GraphReducer.ReducedEdge> run = new ArrayList<>();

        for (Edge edge : route)
        {
            GraphReducer.ReducedEdge reduced = edgesByName.get(edge.getName());

            if (reduced == null) return;

            run.add(reduced);
        }

        if (run.isEmpty()) return;

        Map<TileKey, List<TileAnnotation.Trace>> one = new LinkedHashMap<>();

        editor.trace(one, run, run.get(0).getStart(), true, purple);

        for (Map.Entry<TileKey, List<TileAnnotation.Trace>> square
            : one.entrySet())
        {
            List<TileAnnotation.Trace> here = drawn.computeIfAbsent(square.getKey(), k -> new ArrayList<>());

            for (TileAnnotation.Trace line : square.getValue()) addLine(here, line);
        }
    }

    /**
     * One route's line added to a square's, each stretch once: a stub it runs through gives way to it where both are
     * one color; one of the other color keeps its half, and this line adds only the other half (OB-394).
     */
    static void addLine(List<TileAnnotation.Trace> here,
        TileAnnotation.Trace line)
    {
        if (alreadyDrawn(here, line)) return;

        TileAnnotation.Trace stub = null;

        for (TileAnnotation.Trace old : here)
        {
            if ((old.getFrom() == null || old.getTo() == null) && touches(line, old)) stub = old;
        }

        if (stub == null || line.getFrom() == null || line.getTo() == null)
        {
            here.add(line);
        }
        else if (stub.isManualOnly() == line.isManualOnly())
        {
            here.remove(stub);
            here.add(line);
        }
        else
        {
            org.traincontrol.automationui.TilePorts.Side taken = stub.getFrom() != null ? stub.getFrom() : stub.getTo();

            here.add(new TileAnnotation.Trace(null,
                line.getFrom() == taken ? line.getTo() : line.getFrom(), true, line.isManualOnly()));
        }
    }

    /** Whether a square's lines already show this one: the same two sides either way round, or a stub of one. */
    private static boolean alreadyDrawn(List<TileAnnotation.Trace> here,
        TileAnnotation.Trace line)
    {
        for (TileAnnotation.Trace old : here)
        {
            boolean same = (old.getFrom() == line.getFrom() && old.getTo() == line.getTo())
                || (old.getFrom() == line.getTo() && old.getTo() == line.getFrom());

            boolean stubOfIt = (line.getFrom() == null || line.getTo() == null) && touches(old, line);

            if (same || stubOfIt) return true;
        }

        return false;
    }

    /** Whether two lines in one square share a side. */
    private static boolean touches(TileAnnotation.Trace a,
        TileAnnotation.Trace b)
    {
        return (a.getFrom() != null && (a.getFrom() == b.getFrom() || a.getFrom() == b.getTo()))
            || (a.getTo() != null && (a.getTo() == b.getFrom() || a.getTo() == b.getTo()));
    }

    /** Keeps the best answer of a square's copies, can go over by hand over not now, as the table files a station. */
    static void settle(Map<TileKey, Status> best, TileKey square, Status status)
    {
        Status had = best.get(square);

        if (had == null || status.compareTo(had) < 0) best.put(square, status);
    }

    /**
     * How many findings would stop this setup being built at all: the list the findings box shows, counted, since
     * blocking findings are the cause of a train that cannot go anywhere.
     */
    private int countBlockingFindings()
    {
        int count = 0;

        for (AutonomyChecks.Finding finding : editor.session.check())
        {
            if (finding.getSeverity() == AutonomyChecks.Severity.ERROR) count++;
        }

        return count;
    }

    /**
     * How many stations the running setup holds at all, counted from the running layout rather than the store: the
     * store can hold a station the last build refused, and the question is why the train in front of the user is not
     * moving now.
     */
    private static int countStations(Layout layout)
    {
        int count = 0;

        for (Point point : layout.getPoints())
        {
            if (point.isDestination()) count++;
        }

        return count;
    }

    /**
     * And how many of those a train may actually be SENT to - a station can be a place trains stop
     * without being a place autonomy chooses.
     */
    private static int countDestinations(Layout layout)
    {
        int count = 0;

        for (Point point : layout.getPoints())
        {
            // The rule, asked rather than spelled out again.
            if (layout.isSendableDestination(point)) count++;
        }

        return count;
    }

    /**
     * What the train is standing on, for a report that has nothing else to say.  The block matters only here: every
     * station sharing a block with the train is skipped before it is given a reason, so with one block holding all the
     * platforms the considered list comes back empty and nothing says why.
     */
    private static String describeBlock(Point standing)
    {
        if (standing == null) return "?";

        return standing.getBlock() == null ? standing.getName() : String.valueOf(standing.getBlock());
    }

    /**
     * A line warning that this answer is about the SAVED setup, when there are edits that are not in it; only then,
     * since a caveat printed every time is a caveat nobody reads.
     *
     * @return the warning, or an empty string when the setup is saved
     */
    private String unsavedWarning()
    {
        return editor.session != null && editor.session.isDirty()
            ? "<br><br>" + AutonomyEditorPanel.escape(I18n.t("autosetup.ui.whyUnsaved")) : "";
    }

    @Override
    protected Object settledLock()
    {
        return editor.whySettled;
    }

    /** Counted, not coalesced: every ask started is its own question until its worker gives it up. */
    @Override
    protected boolean outstanding()
    {
        return editor.whyWorking.get() > 0;
    }

    @Override
    protected void giveUp()
    {
        editor.whyWorking.decrementAndGet();
    }
}
