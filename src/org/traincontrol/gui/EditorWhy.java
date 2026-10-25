package org.traincontrol.gui;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.TileGraph.TileKey;
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
     *
     * The report and the lines are built into a value object and handed over instead of being written into the banner
     * and into `traces` from here: `traces` is read by the paint, so a worker writing into it would be drawing on the
     * diagram from the wrong thread.
     *
     * @param layout the railway
     * @param index the square-to-Point translation
     * @param setup what the setup says about turning, arrivals and closed squares, read on the event thread
     * @param tile the square that was clicked
     * @param byHand true when Path Type is Manual: the reasons a hand-driven send meets (MT-434)
     * @return what to say and what to draw
     */
    AutonomyEditorPanel.WhyAnswer composeWhy(org.traincontrol.automation.Layout layout,
        org.traincontrol.automationui.StationIndex index, AutonomyEditorPanel.WhySetup setup, TileKey tile, boolean byHand)
    {
        // Which train is standing here, asked of the LAYOUT rather than of the setup, because the layout's opinion of
        // where trains are decides what runs.
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
            // NOTHING DRAWN AND NOTHING CLEARED, which is what a null trace map means: the three answers that are not
            // about a train leave whatever was on the diagram alone.
            return new AutonomyEditorPanel.WhyAnswer(I18n.t("autosetup.ui.whyNoTrainHere"), false, null);
        }

        // The lines this answer draws, built here and installed by the paint.  Empty rather than null from here on: an
        // answer about a train that draws nothing has to TAKE the previous answer's lines off the diagram.
        java.util.Map<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>>
            drawn = new java.util.LinkedHashMap<>();

        // FOR THE TIER ASKED: by hand, only what stops a route of any kind.
        String cannotStart = layout.explainCannotStart(standing, byHand);

        if (cannotStart != null)
        {
            return new AutonomyEditorPanel.WhyAnswer(I18n.f("autosetup.ui.whyCannotStart",
                AutonomyEditorPanel.escape(standing.getName()), AutonomyEditorPanel.escape(cannotStart)) + unsavedWarning(), true, drawn);
        }

        // THE REASONS AND WHICH OF THEM AUTONOMY WOULD NEVER CHOOSE, taken under one lock so a station cannot be
        // printed under the wrong heading.
        org.traincontrol.automation.Layout.Destinations grouped = layout.explainDestinationsGrouped(standing, byHand);

        java.util.Map<String, String> reasons = grouped.getReasons();

        // STATIONS FIRST, THEN BERTHS, EACH ALPHABETICAL (FR-080: *"show all stations first, then show berths (non-autonomy
        // stations), both in alphabetical order"*), grouped as `AutoLocomotiveStatus.whyNotReport` groups its window (a station
        // with any copy autonomy could choose is filed with the choosable ones).  AND BY PAGE FIRST (MT-429): the page this editor
        // shows, or where the panel has no page the page the train stands on, then the others in page-name order.  Each shown
        // name's key is recorded before it is filed.
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

        // FROM THE EVENT THREAD'S COPIES, and never from the session: this runs on WhyRenderer.
        java.util.Set<TileKey> mustTurn = setup.mustTurn;
        java.util.Set<TileKey> mayTurn = setup.mayTurn;

        // The red arrows, so the routes drawn here are routes a train would actually be offered (OB-120): drawing a run
        // into a station that refuses arrivals from that side answers wrongly, in the direction that wastes the most
        // time.
        java.util.Map<TileKey, java.util.Set<org.traincontrol.automationui.TilePorts.Side>> barred = setup.barred;

        // Collapsed to STATIONS, the way the locomotive panel's tooltip does: the reasons come back keyed by the running
        // graph's Points, and a square is several of those, so a derived-graph station would appear three times over under
        // generated names the user never chose.
        for (java.util.Map.Entry<String, String> entry : reasons.entrySet())
        {
            TileKey where = index.squareOf(entry.getKey());

            String station = where == null ? entry.getKey() : editor.describeTile(where);

            // AND ITS PAGE, where that is not the current page (FR-080: *"If a point is on another page, show it"*;
            // MT-429): a name with no page is a name somebody has to go and look for, or two different squares that read
            // as one.
            String nameOnly = station;

            boolean elsewhere = where != null && where.getPage() != null && !where.getPage().equals(currentPage);

            if (elsewhere) station = I18n.f("autosetup.ui.whyStationOnPage", station, where.getPage());

            sortKey.put(station, new String[] {
                where == null || where.getPage() == null ? "2" : elsewhere ? "1" + where.getPage() : "0", nameOnly });

            if (entry.getValue() == null)
            {
                if (!available.add(station)) continue;

                // Drawn, so "where can it go" is read off the track rather than out of a list
                if (where != null && setup.reducer != null)
                {
                    // AND THE CLOSED SQUARES, so this tool and the findings panel walk one railway.
                    editor.trace(drawn, setup.reducer.findPath(tile, where, mayTurn, mustTurn, barred,
                        setup.shut), tile, true, where);
                }
            }
            else
            {
                // One line per station.  The first reason is the one that would have stopped it; the
                // others are the same square's other arrival sides saying the same thing.
                grouped.fileUnderStation(entry.getKey(), station, choosable, neverChosen);
            }
        }

        // "Nowhere to go", with nothing after it, is not an answer: "every station refused this train" cannot be told from "there
        // were no stations to refuse it", which need opposite things done.  So when nothing was considered, say what the setup
        // holds.
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

        // The reason behind the reason: "No track route leads there", said once per station, is what a SEVERED railway looks like
        // (a switch or signal drawn without an accessory address cannot be routed over), the answer to the wrong question.  So
        // when nothing is available and the setup has blocking findings, say that first.
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
            going, detail + unsavedWarning()), true, drawn);
    }

    /**
     * How many findings would stop this setup being built at all.
     *
     * The same list the findings box shows, counted rather than read: a report that says a train cannot
     * go anywhere is describing a symptom, and when there are blocking findings they are the cause.
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
     * How many stations the running setup holds at all.
     *
     * Counted from the RUNNING layout rather than from the store, because that is what the answer is
     * about: the store can hold a station the last build refused, and the question being asked is why
     * the train in front of the user is not moving now.
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
            // THE RULE, ASKED, rather than spelled out again: a third copy of it, without `!isReversing()`,
            // overcounted every reversing station, and the number feeds "why is nothing considered".
            if (layout.isSendableDestination(point)) count++;
        }

        return count;
    }

    /**
     * One headed group of "station: reason" lines for the why answer, left out entirely when it is empty.
     *
     * The headings are the locomotive panel's window's own, for FR-080: the same two groups under the
     * same two names.  An empty heading reads as a group that failed to load, so there is none.
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
     * What the train is standing on, for a report that has nothing else to say.
     *
     * The block matters here and nowhere else in this report: every station sharing a block with the
     * train is skipped before it can be given a reason, so on a layout where one block holds all the
     * platforms the considered list comes back empty and nothing says why.
     */
    private static String describeBlock(org.traincontrol.automation.Point standing)
    {
        if (standing == null) return "?";

        return standing.getBlock() == null ? standing.getName() : String.valueOf(standing.getBlock());
    }

    /**
     * A line warning that this answer is about the SAVED setup, when there are edits that are not in it.
     *
     * Only when there are: a caveat printed every time is a caveat nobody reads.
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
    Object settledLock()
    {
        return editor.whySettled;
    }

    /** Counted, not coalesced: every ask started is its own question until its worker gives it up. */
    @Override
    boolean outstanding()
    {
        return editor.whyWorking.get() > 0;
    }

    @Override
    void giveUp()
    {
        editor.whyWorking.decrementAndGet();
    }
}
