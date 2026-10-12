package org.traincontrol.base;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a captured route looks like once the same accessory has been thrown twice.
 *
 * Capture is the route editor's most useful trick: the user ticks a box, throws the switches and
 * signals in the order they want them, and watches the route write itself instead of looking up
 * addresses.  Somebody doing that changes their mind - a turnout goes the wrong way, is thrown back,
 * and both throws are now in the route.  This keeps the LAST value for each thing and drops the rest.
 *
 * Lifted out of the old text route editor when that was deleted.  It was written there, it belongs
 * to routes rather than to a window, and two test classes and the edge editor were all reaching into
 * a JFrame for a static.  The new editor did not have it at all, which is why capture there recorded
 * every throw.
 */
public final class RouteCapture
{
    private RouteCapture()
    {
    }

    /**
     * The part of a line that says WHAT it sets, as opposed to what it sets it to.
     *
     * Always an exact prefix of the line, so that the rebuild below can put the line back together as
     * key + "," + value.
     *
     * An accessory line is "name,setting", so its first field identifies it.  A locomotive line is
     * "prefix,name,value" - and keying on the first field alone made every locomotive in the route
     * share the key "locspeed", so filtering kept the last one and silently dropped the rest from the
     * middle of the text area.  A function line identifies a function as well: a route may set several
     * on the same locomotive and each is its own setting.
     *
     * Deliberately not "everything but the last field": every locomotive line can carry an optional
     * trailing delay, so the last field is not reliably the value.
     *
     * @param line
     * @return
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

            // Rewriting an accessory moves it to the end, so the lines that survive are in the
            // order they were last written.  Keeping the latest value at the earliest position
            // answered the same question two different ways, and it inverted captured three-way
            // pairs: reaching "right" means clicking through "left", and the four lines that
            // captured collapsed to turn-before-straight - the one order a three-way must never
            // be given.
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

    /**
     * How many leading comma-separated fields identify what the given line sets.
     * @param line
     * @return
     */
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
