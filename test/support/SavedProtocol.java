package support;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagramComponent;

/**
 * The one difference from the goldens a saved page may show, as every accessory tile writes its protocol: an added
 * ` .prot=MM2` in the block of an addressed switch, signal or uncoupler tile that read as MM2.  The goldens were written
 * on dev, so a dump has those lines taken out before it is compared.  A tile that must now write the line and does not
 * gets a marker instead, and a line anywhere else is left in, so either differs from the golden.
 *
 * @author Adam
 */
public final class SavedProtocol
{
    private static final Pattern PARSE_TILE = Pattern.compile("^  \\((\\d+),(\\d+)\\) (\\w+) o=\\d+ addr=(-?\\d+) raw=-?\\d+ prot=(\\w+) .*");

    private static final Pattern SYNC_TILE = Pattern.compile("^  \\((\\d+),(\\d+)\\) (\\w+) addr=(-?\\d+) prot=(\\w+).*");

    private static final String MISSING = " (.prot=MM2 missing)";

    private SavedProtocol()
    {
    }

    /** Whether a tile of this type, address and protocol writes ` .prot=MM2`. */
    private static boolean writesMM2(String type, String address, String protocol) throws Exception
    {
        LayoutDiagramComponent c = new LayoutDiagramComponent(LayoutDiagramComponent.componentType.valueOf(type), 0, 0, 0,
            0, 0, 0, accessoryDecoderType.MM2);

        return Integer.parseInt(address) > 0 && "MM2".equals(protocol) && (c.isSwitch() || c.isSignal() || c.isUncoupler());
    }

    /** testCS2InputsParseAsBefore's dump: each tile's line is followed by its save block on one line. */
    public static String inParseDump(String dump) throws Exception
    {
        String[] lines = dump.split("\n", -1);

        for (int i = 0; i + 1 < lines.length; i++)
        {
            Matcher m = PARSE_TILE.matcher(lines[i]);

            if (m.matches() && writesMM2(m.group(3), m.group(4), m.group(5)))
            {
                lines[i + 1] = lines[i + 1].contains(" |  .prot=MM2") ? lines[i + 1].replaceFirst(" \\|  \\.prot=MM2", "")
                    : lines[i + 1] + MISSING;
            }
        }

        return String.join("\n", lines);
    }

    /**
     * testACS2SyncIsAsBefore's dump: a page's tile lines come before its save, in which each element names its square in
     * its `.id` (0xPPYYXX).
     */
    public static String inSyncDump(String dump) throws Exception
    {
        StringBuilder out = new StringBuilder();
        Map<String, Boolean> writes = new HashMap<>();
        String square = null;
        boolean seen = false;

        for (String line : dump.split("\n", -1))
        {
            Matcher m = SYNC_TILE.matcher(line);

            if (line.startsWith("page "))
            {
                writes.clear();
            }
            else if (m.matches())
            {
                writes.put(m.group(1) + "," + m.group(2), writesMM2(m.group(3), m.group(4), m.group(5)));
            }

            if (square != null && (line.equals("    element") || !line.startsWith("     ")))
            {
                if (Boolean.TRUE.equals(writes.get(square)) && !seen)
                {
                    out.append("    ").append(square).append(MISSING).append("\n");
                }

                square = null;
            }

            if (line.equals("    element"))
            {
                seen = false;
            }
            else if (line.startsWith("     .id=0x"))
            {
                int id = Integer.parseInt(line.substring(11), 16);
                square = (id & 0xff) + "," + ((id >> 8) & 0xff);
            }
            else if (line.equals("     .prot=MM2") && square != null && Boolean.TRUE.equals(writes.get(square)))
            {
                seen = true;
                continue;
            }

            out.append(line).append("\n");
        }

        return out.substring(0, out.length() - 1);
    }
}
