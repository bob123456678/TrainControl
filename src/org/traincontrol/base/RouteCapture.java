package org.traincontrol.base;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a captured route looks like once the same accessory has been thrown twice.
 *
 * Capture is the route editor's trick of writing the route as the user throws the switches and signals.  Somebody who
 * changes their mind throws a turnout back and has both throws in the route; this keeps the LAST value for each thing
 * and drops the rest.  It lives here rather than in a window so tests and the edge editor need not reach into a JFrame
 * for a static.
 */
public final class RouteCapture
{
    private RouteCapture()
    {
    }

    /**
     * The part of a line that says WHAT it sets, as an exact prefix of the line so the rebuild can put it back together
     * as key + "," + value.  An accessory line is "name,setting", so its first field identifies it.  A locomotive line
     * is "prefix,name,value": keying on the first field alone would give every locomotive the key "locspeed" and drop
     * all but the last.  A function line identifies a function too, as a route may set several on one locomotive.
     * Deliberately not "everything but the last field": every locomotive line can carry an optional trailing delay.
     */
    private static String dedupKeyOf(String line)
    {
        int fields = identifyingFieldsOf(line);

        int cut = -1;

        for (int i = 0; i < fields; i++)
        {
            cut = line.indexOf(',', cut + 1);

            if (cut < 0) return line;
        }

        return line.substring(0, cut);
    }

    public static String filterConfigCommands(String text)
    {
        String[] lines = text.split("\n");
        Map<String, String> map = new LinkedHashMap<>();

        for (String line : lines)
        {
            String key = dedupKeyOf(line);

            String value = line.length() > key.length() ? line.substring(key.length() + 1) : "";

            // Rewriting an accessory moves it to the end, so the surviving lines are in the order they were last
            // written; keeping the latest value at the earliest position inverted captured three-way pairs (reaching
            // "right" clicks through "left"), collapsing them to turn-before-straight, the one order a three-way must
            // never be given.
            map.remove(key);
            map.put(key, value);
        }

        StringBuilder filteredCommands = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet())
        {
            if ("".equals(entry.getValue()))
            {
                filteredCommands.append(entry.getKey()).append("\n");
            }
            else
            {
                filteredCommands.append(entry.getKey()).append(",").append(entry.getValue()).append("\n");
            }
        }

        return filteredCommands.toString().trim();
    }

    /** How many leading comma-separated fields identify what the given line sets. */
    private static int identifyingFieldsOf(String line)
    {
        int comma = line.indexOf(',');

        if (comma < 0) return 1;

        String prefix = line.substring(0, comma).trim().toLowerCase();

        if (RouteCommand.LOC_FUNC_PREFIX.equals(prefix)) return 3;

        if (RouteCommand.LOC_SPEED_PREFIX.equals(prefix)
            || RouteCommand.LOC_DIRECTION_PREFIX.equals(prefix)
            || RouteCommand.LOC_AUTO_PREFIX.equals(prefix)) return 2;

        return 1;
    }
}
