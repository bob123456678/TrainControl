package org.traincontrol.gui.autonomyeditor;

import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.TileAnnotation.ToolMark;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.SettledQuestion;
import org.traincontrol.util.I18n;

/**
 * The autonomy editor's answer to Why Not Moving?: for the train on the clicked square, every station it might be
 * sent to and why each was refused (FR-080), composed on the WhyRenderer thread and handed back to be painted
 * (FR-136).
 *
 * The panel keeps `composeWhy` as a one-line call to the method of the same name here, and every field: this reads
 * them through `editor`.  Its settle and await take the panel's `whySettled`; the railway's monitor is taken by the
 * Layout calls the answer is made of, off the event thread.
 */
final class EditorWhy extends SettledQuestion
{
    private final AutonomyEditorPanel editor;

    EditorWhy(AutonomyEditorPanel editor)
    {
        this.editor = editor;
    }

    /**
     * Composes the answer on a worker thread rather than painting one: the stations autonomy could choose, then the
     * ones it never will, each alphabetical, with the page named for a station that is not on the train's (FR-080).
     * Built into a value object and handed over: `traces` is read by the paint, so writing it from here would draw from
     * the wrong thread.
     *
     * @param layout the railway
     * @param index the square-to-Point translation
     * @param setup the edge names the routes are drawn by, read on the event thread
     * @param tile the square that was clicked
     * @param byHand true when Path Type is Manual: the reasons a hand-driven send meets (MT-434)
     * @return what to say and what to draw
     */
    AutonomyEditorPanel.WhyAnswer composeWhy(org.traincontrol.automation.Layout layout,
        org.traincontrol.automationui.StationIndex index, AutonomyEditorPanel.WhySetup setup, TileKey tile, boolean byHand)
    {
        // Which train is standing here, asked of the layout rather than the setup, whose opinion of where trains are
        // decides what runs.
        org.traincontrol.base.Locomotive standing = null;

        // Through StationIndex, the one place that knows a square is several Points and which ones.
        for (String pointName : index.pointNamesAt(tile))
        {
            org.traincontrol.automation.Point p = layout.getPoint(pointName);

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
                new java.util.HashMap<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>>(),
                java.util.Collections.<TileKey, ToolMark>emptyMap());
        }

        // The lines this answer draws, installed by the paint.  Empty rather than null from here on: an answer about a
        // train that draws nothing has to take the previous answer's lines off the diagram.
        java.util.Map<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>>
            drawn = new java.util.LinkedHashMap<>();

        // The train's square and the stations, marked by the answer (FR-159)
        java.util.Map<TileKey, ToolMark> marks = new java.util.HashMap<>();

        marks.put(tile, ToolMark.PICKED);

        // For the tier asked: by hand, only what stops a route of any kind.
        String cannotStart = layout.explainCannotStart(standing, byHand);

        if (cannotStart != null)
        {
            return new AutonomyEditorPanel.WhyAnswer(I18n.f("autosetup.ui.whyCannotStart",
                AutonomyEditorPanel.escape(standing.getName()), AutonomyEditorPanel.escape(cannotStart)) + unsavedWarning(), true, drawn,
                marks);
        }

        // The reasons and which autonomy would never choose, taken under one lock so a station cannot be printed under
        // the wrong heading.
        org.traincontrol.automation.Layout.Destinations grouped = layout.explainDestinationsGrouped(standing, byHand);

        java.util.Map<String, String> reasons = grouped.getReasons();

        // Where the train could be sent by hand right now: a barred station's own reason is its bar, which says nothing
        // of whether a route would take it there (an intermediate terminus, a length that does not fit).
        org.traincontrol.automation.Layout.Destinations byHandToo = byHand ? grouped
            : layout.explainDestinationsGrouped(standing, true);

        java.util.Map<String, String> sendable = byHandToo.getReasons();

        // Stations first, then berths, each alphabetical (FR-080: *"show all stations first, then show berths
        // (non-autonomy stations), both in alphabetical order"*), grouped as `AutoLocomotiveStatus.whyNotReport` groups
        // its window (a station with any copy autonomy could choose is filed with the choosable ones).  And by page
        // first (MT-429): the page this editor shows, or where it has none the page the train stands on, then the
        // others in page-name order.
        final String currentPage = editor.page != null ? editor.page : tile.getPage();

        final java.util.Map<String, String[]> sortKey = new java.util.HashMap<>();

        java.util.Comparator<String> byPageThenName = (a, b) ->
        {
            String[] x = sortKey.get(a);
            String[] y = sortKey.get(b);

            int byPage = x[0].compareTo(y[0]);

            if (byPage != 0) return byPage;

            int byName = x[1].compareTo(y[1]);

            return byName != 0 ? byName : a.compareTo(b);
        };

        java.util.Set<String> available = new java.util.TreeSet<>(byPageThenName);
        java.util.Map<String, String> choosable = new java.util.TreeMap<>(byPageThenName);
        java.util.Map<String, String> neverChosen = new java.util.TreeMap<>(byPageThenName);

        // The route each colored square may be drawn by, per color: the router's own, to the first of its copies
        java.util.Map<ToolMark, java.util.Map<TileKey, java.util.List<org.traincontrol.automation.Edge>>> routeTo =
            new java.util.EnumMap<>(ToolMark.class);

        java.util.Set<TileKey> outOfReach = new java.util.HashSet<>();

        // Collapsed to stations, as the locomotive panel's tooltip does: the reasons are keyed by the running graph's
        // Points and a square is several, so a derived-graph station would appear three times under names the user
        // never chose.
        for (java.util.Map.Entry<String, String> entry : reasons.entrySet())
        {
            TileKey where = index.squareOf(entry.getKey());

            String station = where == null ? entry.getKey() : editor.describeTile(where);

            // And its page, where that is not the current page (FR-080: *"If a point is on another page, show it"*;
            // MT-429): a name with no page is one somebody has to go and look for, or two squares that read as one.
            String nameOnly = station;

            boolean elsewhere = where != null && where.getPage() != null && !where.getPage().equals(currentPage);

            if (elsewhere) station = I18n.f("autosetup.ui.whyStationOnPage", station, where.getPage());

            // By square, not by the name shown, which two unnamed squares on one sensor share.  Purple only where it
            // could be sent by hand; one it cannot be is gray like any other (OB-394).
            if (where != null)
            {
                ToolMark mark = entry.getValue() == null
                    ? ToolMark.CAN_GO
                    : grouped.getBarred().contains(entry.getKey()) && sendable.containsKey(entry.getKey())
                        && sendable.get(entry.getKey()) == null
                        ? ToolMark.NEVER
                        : ToolMark.NOT_NOW;

                markCopy(marks, outOfReach, where, mark, grouped.getBarred().contains(entry.getKey()));

                // Green by autonomy's route, purple by the one a hand send would take
                java.util.List<org.traincontrol.automation.Edge> route = mark == ToolMark.CAN_GO
                    ? grouped.getRoutes().get(entry.getKey())
                    : mark == ToolMark.NEVER ? byHandToo.getRoutes().get(entry.getKey()) : null;

                if (route != null)
                {
                    routeTo.computeIfAbsent(mark, m -> new java.util.HashMap<>()).putIfAbsent(where, route);
                }
            }

            sortKey.put(station, new String[] {
                where == null || where.getPage() == null ? "2" : elsewhere ? "1" + where.getPage() : "0", nameOnly });

            if (entry.getValue() == null)
            {
                available.add(station);
            }
            else
            {
                // One line per station.  The first reason is the one that would have stopped it; the
                // others are the same square's other arrival sides saying the same thing.
                grouped.fileUnderStation(entry.getKey(), station, choosable, neverChosen);
            }
        }

        finishMarks(marks, outOfReach);

        // Drawn, so "where can it go" is read off the track rather than out of a list: green first, so track a purple
        // route shares with a green one stays the green one's.
        for (ToolMark color : new ToolMark[] {ToolMark.CAN_GO, ToolMark.NEVER})
        {
            for (java.util.Map.Entry<TileKey, ToolMark> square : marks.entrySet())
            {
                java.util.Map<TileKey, java.util.List<org.traincontrol.automation.Edge>> byColor = routeTo.get(color);

                if (square.getValue() == color && byColor != null && byColor.get(square.getKey()) != null)
                {
                    drawRoute(drawn, byColor.get(square.getKey()), setup.edgesByName, square.getKey(),
                        color == ToolMark.NEVER);
                }
            }
        }

        // "Nowhere to go", with nothing after it, is not an answer: "every station refused this train" cannot be told
        // from "there were no stations to refuse it", which need opposite things done.  So when nothing was considered,
        // say what the setup holds.
        String going = available.isEmpty()
            ? I18n.t("autosetup.ui.whyNowhere")
            : I18n.f("autosetup.ui.whyCanGo", available.size(), AutonomyEditorPanel.escape(join(new java.util.ArrayList<>(available))));

        StringBuilder blocked = new StringBuilder();

        // On Manual, the stations it cannot be sent to: autonomy's "could choose" is not the question asked (MT-434).
        whyGroup(blocked, I18n.f(byHand ? "autolayout.ui.whyHeaderByHand" : "autolayout.ui.whyHeaderCandidates",
            choosable.size()), choosable);
        whyGroup(blocked, I18n.f("autolayout.ui.whyHeaderBarred", neverChosen.size()), neverChosen);

        String detail = blocked.toString();

        if (available.isEmpty() && reasons.isEmpty())
        {
            detail = "<br>" + AutonomyEditorPanel.escape(I18n.f("autosetup.ui.whyNothingConsidered",
                countStations(layout), countDestinations(layout),
                AutonomyEditorPanel.escape(describeBlock(layout.getLocomotiveLocation(standing)))));
        }

        // The reason behind the reason: "No track route leads there", said once per station, is what a severed railway
        // looks like (a switch or signal drawn without an accessory address cannot be routed over), so with nothing
        // available and blocking findings, say that first.
        if (available.isEmpty())
        {
            int blockingFindings = countBlockingFindings();

            if (blockingFindings > 0)
            {
                detail = "<br><b>" + AutonomyEditorPanel.escape(I18n.f("autosetup.ui.whyBlockedBySetup", blockingFindings))
                    + "</b>" + detail;
            }
        }

        return new AutonomyEditorPanel.WhyAnswer(I18n.f("autosetup.ui.whyReport", AutonomyEditorPanel.escape(standing.getName()),
            going, colours(marks.values()) + detail + unsavedWarning()), true, drawn, marks);
    }

    /**
     * Lays the router's route on the diagram, each stretch of track once (OB-394): where another route already runs
     * through a square the same way this one's line adds nothing, and a line that only ends in a square another runs
     * through is already shown arriving.  Nothing where the route crosses an edge the drawing does not know.
     */
    private void drawRoute(
        java.util.Map<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>> drawn,
        java.util.List<org.traincontrol.automation.Edge> route,
        java.util.Map<String, org.traincontrol.automationui.GraphReducer.ReducedEdge> edgesByName, TileKey to,
        boolean purple)
    {
        java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> run = new java.util.ArrayList<>();

        for (org.traincontrol.automation.Edge edge : route)
        {
            org.traincontrol.automationui.GraphReducer.ReducedEdge reduced = edgesByName.get(edge.getName());

            if (reduced == null) return;

            run.add(reduced);
        }

        if (run.isEmpty()) return;

        java.util.Map<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>> one =
            new java.util.LinkedHashMap<>();

        editor.trace(one, run, run.get(0).getStart(), true, to, purple);

        for (java.util.Map.Entry<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>> square
            : one.entrySet())
        {
            java.util.List<org.traincontrol.automationui.TileAnnotation.Trace> here =
                drawn.computeIfAbsent(square.getKey(), k -> new java.util.ArrayList<>());

            for (org.traincontrol.automationui.TileAnnotation.Trace line : square.getValue()) addLine(here, line);
        }
    }

    /**
     * One route's line added to a square's, each stretch once: a stub it runs through gives way to it where both are
     * one color; one of the other color keeps its half, and this line adds only the other half (OB-394).
     */
    static void addLine(java.util.List<org.traincontrol.automationui.TileAnnotation.Trace> here,
        org.traincontrol.automationui.TileAnnotation.Trace line)
    {
        if (alreadyDrawn(here, line)) return;

        org.traincontrol.automationui.TileAnnotation.Trace stub = null;

        for (org.traincontrol.automationui.TileAnnotation.Trace old : here)
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

            here.add(new org.traincontrol.automationui.TileAnnotation.Trace(null,
                line.getFrom() == taken ? line.getTo() : line.getFrom(), true, line.isManualOnly()));
        }
    }

    /** Whether a square's lines already show this one: the same two sides either way round, or a stub of one. */
    private static boolean alreadyDrawn(java.util.List<org.traincontrol.automationui.TileAnnotation.Trace> here,
        org.traincontrol.automationui.TileAnnotation.Trace line)
    {
        for (org.traincontrol.automationui.TileAnnotation.Trace old : here)
        {
            boolean same = (old.getFrom() == line.getFrom() && old.getTo() == line.getTo())
                || (old.getFrom() == line.getTo() && old.getTo() == line.getFrom());

            boolean stubOfIt = (line.getFrom() == null || line.getTo() == null) && touches(old, line);

            if (same || stubOfIt) return true;
        }

        return false;
    }

    /** Whether two lines in one square share a side. */
    private static boolean touches(org.traincontrol.automationui.TileAnnotation.Trace a,
        org.traincontrol.automationui.TileAnnotation.Trace b)
    {
        return (a.getFrom() != null && (a.getFrom() == b.getFrom() || a.getFrom() == b.getTo()))
            || (a.getTo() != null && (a.getTo() == b.getFrom() || a.getTo() == b.getTo()));
    }

    /**
     * Marks a square with the best of its copies' answers, as the answer files a station: can go, else not now, else
     * never, in `ToolMark`'s order, where the train's own square's PICKED comes first and so keeps its outline.
     */
    private static void settle(java.util.Map<TileKey, ToolMark> marks, TileKey square, ToolMark mark)
    {
        ToolMark had = marks.get(square);

        if (had == null || mark.ordinal() < had.ordinal()) marks.put(square, mark);
    }

    /**
     * One copy's mark on its square, green over gray over purple; a copy autonomy never chooses that a hand send cannot
     * reach either is held back, so it does not gray a square another copy can be sent to (OB-394).
     */
    static void markCopy(java.util.Map<TileKey, ToolMark> marks, java.util.Set<TileKey> outOfReach, TileKey square,
        ToolMark mark, boolean barred)
    {
        if (mark == ToolMark.NOT_NOW && barred) outOfReach.add(square);
        else settle(marks, square, mark);
    }

    /** The held-back squares no copy said more of, gray. */
    static void finishMarks(java.util.Map<TileKey, ToolMark> marks, java.util.Set<TileKey> outOfReach)
    {
        for (TileKey square : outOfReach) marks.putIfAbsent(square, ToolMark.NOT_NOW);
    }

    /** What the stations' colors mean, naming only the colors drawn, on the line under where it can go. */
    private static String colours(java.util.Collection<ToolMark> drawn)
    {
        java.util.List<String> said = new java.util.ArrayList<>();

        if (drawn.contains(ToolMark.CAN_GO)) said.add(I18n.t("autosetup.ui.whyColourGreen"));
        if (drawn.contains(ToolMark.NOT_NOW)) said.add(I18n.t("autosetup.ui.whyColourGray"));
        if (drawn.contains(ToolMark.NEVER)) said.add(I18n.t("autosetup.ui.whyColourPurple"));

        return said.isEmpty() ? "" : "<br>" + AutonomyEditorPanel.escape(String.join("  ", said));
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
    private static int countStations(org.traincontrol.automation.Layout layout)
    {
        int count = 0;

        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.isDestination()) count++;
        }

        return count;
    }

    /**
     * And how many of those a train may actually be SENT to - a station can be a place trains stop
     * without being a place autonomy chooses.
     */
    private static int countDestinations(org.traincontrol.automation.Layout layout)
    {
        int count = 0;

        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            // The rule, asked rather than spelled out again: a third copy without `!isReversing()` overcounted every
            // reversing station.
            if (layout.isSendableDestination(point)) count++;
        }

        return count;
    }

    /**
     * One headed group of "station: reason" lines for the why answer, left out entirely when it is empty (an empty
     * heading reads as a group that failed to load).  The headings are the locomotive panel window's own (FR-080).
     *
     * @param into the answer being built
     * @param heading the group's heading, already translated
     * @param lines station to reason, in the order to show them
     */
    private static void whyGroup(StringBuilder into, String heading, java.util.Map<String, String> lines)
    {
        if (lines.isEmpty()) return;

        into.append("<br><b>").append(AutonomyEditorPanel.escape(heading)).append("</b>");

        for (java.util.Map.Entry<String, String> line : lines.entrySet())
        {
            into.append("<br>").append(AutonomyEditorPanel.escape(line.getKey())).append(": ").append(AutonomyEditorPanel.escape(line.getValue()));
        }
    }

    /**
     * What the train is standing on, for a report that has nothing else to say.  The block matters only here: every
     * station sharing a block with the train is skipped before it is given a reason, so with one block holding all the
     * platforms the considered list comes back empty and nothing says why.
     */
    private static String describeBlock(org.traincontrol.automation.Point standing)
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

    /**
     * Station names, comma separated, capped so one line stays one line.
     */
    private static String join(java.util.List<String> names)
    {
        StringBuilder out = new StringBuilder();

        int shown = 0;

        for (String name : names)
        {
            if (shown == 4)
            {
                out.append(", ...");
                break;
            }

            if (shown > 0) out.append(", ");

            out.append(name);
            shown++;
        }

        return out.toString();
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
