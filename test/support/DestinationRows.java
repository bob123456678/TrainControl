package support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.traincontrol.util.I18n;

/**
 * The rows of the destinations table a why answer shows (FR-221), read out of its HTML: station, answer and reason as
 * `DestinationTable.html` writes them, mark-up escaped as written.
 */
public final class DestinationRows
{
    private static final Pattern ROW = Pattern.compile("<tr><td[^>]*>(.*?)</td><td[^>]*><font color=[^>]*>(.*?)</font>"
        + "</td><td[^>]*>(.*?)</td></tr>");

    private DestinationRows()
    {
    }

    /** Each row as {station, answer, reason}, in the order shown. */
    public static List<String[]> of(String html)
    {
        List<String[]> out = new ArrayList<>();

        Matcher row = ROW.matcher(html == null ? "" : html);

        while (row.find()) out.add(new String[] {row.group(1), row.group(2), row.group(3)});

        return out;
    }

    /** The stations whose answer is this one (`autolayout.ui.whyCanGoNow`, `whyByHandOnly` or `whyNotRightNow`). */
    public static List<String> answering(String html, String key)
    {
        List<String> out = new ArrayList<>();

        for (String[] row : of(html))
        {
            if (row[1].equals(I18n.t(key))) out.add(row[0]);
        }

        return out;
    }
}
