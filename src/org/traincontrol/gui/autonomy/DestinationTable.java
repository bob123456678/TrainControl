package org.traincontrol.gui.autonomy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;

/**
 * Where a train may be sent, for autonomy and by hand in one answer (FR-221): a row per station saying whether it can go
 * there now, by hand only, or not right now, and why.  The locomotive list's why and the editor's Why not Moving? both
 * show it.  Asking takes the railway's monitor, so never on the event thread.
 */
public final class DestinationTable
{
    /** Best first: a station shows the best answer of its copies.  The colors are the editor's marks. */
    public enum Status
    {
        CAN_GO("autolayout.ui.whyCanGoNow", "#22963c"),
        BY_HAND("autolayout.ui.whyByHandOnly", "#be28a0"),
        NOT_NOW("autolayout.ui.whyNotRightNow", "#787878");

        private final String key;
        private final String colour;

        Status(String key, String colour)
        {
            this.key = key;
            this.colour = colour;
        }
    }

    /** What stops any send, or null: then there is nothing to tabulate. */
    public final String cannotStart;

    /** What stops autonomy starting the train, or null: then no station is CAN_GO. */
    public final String autonomyWillNotStart;

    private final Layout.Destinations auto;
    private final Layout.Destinations hand;

    /** Station to its row, filed by `file`. */
    private final Map<String, Row> rows = new LinkedHashMap<>();

    /** The copies Return Home stands this train on: its home (`HomeStaging.atHome`). */
    private final Set<String> home = new HashSet<>();

    public DestinationTable(Layout layout, Locomotive loc)
    {
        // One hold of the railway's monitor, so no dispatch moves a train between the tiers' answers
        synchronized (layout)
        {
            cannotStart = layout.explainCannotStart(loc, true);
            autonomyWillNotStart = cannotStart == null ? layout.explainCannotStart(loc, false) : null;
            auto = layout.explainDestinationsGrouped(loc, false);
            hand = layout.explainDestinationsGrouped(loc, true);

            for (String point : hand.getReasons().keySet())
            {
                if (HomeStaging.atHome(layout.getHomeStation(loc), layout.getPoint(point))) home.add(point);
            }
        }
    }

    /** Every copy reported on, in autonomy's order of preference. */
    public Set<String> points()
    {
        return auto.getReasons().keySet();
    }

    /** One copy's answer: CAN_GO where autonomy may send it now, BY_HAND where only a hand send may, else NOT_NOW. */
    public Status statusOf(String point)
    {
        if (autonomyWillNotStart == null && auto.getReasons().get(point) == null) return Status.CAN_GO;

        return hand.getReasons().get(point) == null ? Status.BY_HAND : Status.NOT_NOW;
    }

    /** The route drawn to a copy: autonomy's where it can go now, the hand send's where only that may. */
    public List<Edge> routeTo(String point)
    {
        Status status = statusOf(point);

        return status == Status.NOT_NOW ? null : (status == Status.CAN_GO ? auto : hand).getRoutes().get(point);
    }

    /**
     * Autonomy's reason where only a hand send may go; else what stops a hand send, and autonomy's reason as well on a
     * line of its own.
     */
    private String reasonOf(String point, Status status)
    {
        String byHand = hand.getReasons().get(point);
        String byAutonomy = auto.getReasons().get(point);

        if (status == Status.CAN_GO) return "";

        if (status == Status.BY_HAND)
        {
            String why = byAutonomy != null ? byAutonomy : I18n.t("autolayout.ui.whyNotStartedByAutonomy");

            // D177, Adam: "you can add a note of "return home only" if needed"
            return home.contains(point) ? why + "\n" + I18n.t("autolayout.ui.whyReturnHomeOnly") : why;
        }

        return byAutonomy == null || byAutonomy.equals(byHand) ? byHand : byHand + "\n" + byAutonomy;
    }

    /**
     * Files a copy under its station, which shows its best copy; among copies nothing reaches now, one autonomy could
     * choose.  Rows sort by answer, stations before berths (FR-080), then `group`, then `name` as read (MT-429).
     */
    public void file(String point, String station, String group, String name)
    {
        Row had = rows.get(station);
        Status status = statusOf(point);
        boolean berth = auto.getBarred().contains(point);

        if (had == null || status.ordinal() < had.status.ordinal()
            || (status == had.status && status == Status.NOT_NOW && had.berth && !berth))
        {
            rows.put(station, new Row(station, status, reasonOf(point, status), berth, group, name));
        }
    }

    /** The stations filed, in the order shown. */
    public List<Row> rows()
    {
        List<Row> out = new ArrayList<>(rows.values());

        out.sort((a, b) ->
        {
            int by = a.status.compareTo(b.status);

            if (by == 0) by = Boolean.compare(a.berth, b.berth);
            if (by == 0) by = a.group.compareTo(b.group);
            if (by == 0) by = Util.compareAsRead(a.name, b.name);

            return by != 0 ? by : a.station.compareTo(b.station);
        });

        return out;
    }

    /** The answer as HTML with no `html` tag round it: autonomy's refusal to start, then at most `most` rows. */
    public String html(int most)
    {
        StringBuilder out = new StringBuilder();

        // In a cell as wide as a wrapped tooltip: the reasons run to three hundred characters
        if (autonomyWillNotStart != null)
        {
            out.append("<table cellspacing=0 cellpadding=1><tr><td width=").append(AutonomyEditorPanel.TOOLTIP_WIDTH)
                .append('>').append(escape(I18n.f("autolayout.ui.whyAutonomyWillNotStart", autonomyWillNotStart)))
                .append("</td></tr></table>");
        }

        out.append("<table cellspacing=0 cellpadding=1><tr>");

        for (String heading : new String[] {"autolayout.ui.whyColumnStation", "autolayout.ui.whyColumnStatus",
            "autolayout.ui.whyColumnReason"})
        {
            out.append("<th align=left").append(GAP).append('>').append(escape(I18n.t(heading))).append("</th>");
        }

        out.append("</tr>");

        List<Row> shown = rows();

        for (int i = 0; i < shown.size(); i++)
        {
            if (i == most)
            {
                out.append("<tr><td colspan=3>...</td></tr>");
                break;
            }

            Row row = shown.get(i);

            out.append("<tr><td valign=top").append(GAP).append('>').append(escape(row.station))
                .append("</td><td valign=top nowrap").append(GAP).append("><font color=").append(row.status.colour).append('>')
                .append(escape(I18n.t(row.status.key))).append("</font></td><td valign=top>").append(escape(row.reason).replace("\n", "<br>"))
                .append("</td></tr>");
        }

        return out.append("</table>").toString();
    }

    /** The room right of a column, so its words do not run into the next one's. */
    private static final String GAP = " style=\"padding-right:12px\"";

    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;");
    }

    /** One station's line. */
    public static final class Row
    {
        public final String station;
        public final Status status;
        public final String reason;

        /** Whether autonomy never chooses the copy it was filed from. */
        private final boolean berth;
        private final String group;
        private final String name;

        private Row(String station, Status status, String reason, boolean berth, String group, String name)
        {
            this.station = station;
            this.status = status;
            this.reason = reason;
            this.berth = berth;
            this.group = group;
            this.name = name;
        }
    }
}
