package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.marklin.file.CS2File;

/**
 * Every CS2-format input the repository holds parses exactly as it did before the CS3 probe's changes: every page
 * (each tile's type, square, rotation, addresses, protocol, state, label and the block a save would write), every
 * accessory, route and locomotive.  The golden, `test/baseline/cs2_parse_golden.txt`, was written by `main` below run
 * on the code before those changes (dev at 0d2d9a24).  The CS3 plus fixture is in the list too, read as a CS2 would
 * read it: without a station, so the CS3-only paths stay shut.
 *
 * Since every accessory tile writes its protocol, a save may add ` .prot=MM2` to an addressed switch, signal or
 * uncoupler that read as MM2, and only there (support.SavedProtocol).
 *
 * MUTATION: write `.prot=MM2` on every tile (drop the accessory test from LayoutDiagramComponent's `else if`), and the
 * golden differs at the first track tile; or on none (drop the `else if`), and at the first MM2 switch.
 *
 * @author Adam
 */
public class testCS2InputsParseAsBefore
{
    private static final String GOLDEN = "test/baseline/cs2_parse_golden.txt";

    private static final String[] LAYOUTS =
    {
        "test/layout", "test/layout_subpage", "test/test_layout", "test/test_layout_snapshot", "test/operator_layout",
        "test/baseline/layout", "test/layouts/curve-into-platform", "test/layouts/live-snapshot", "test/layouts/one-page",
        "test/layouts/single-switch", "test/layouts/unfolded-route-tiles", "test/cs3_v261",
        "docs/manual-tests/files/MT-244-layout-without-autonomy-setup/layout",
        "docs/manual-tests/files/MT-380-one-page-layout/layout"
    };

    private static final String[] MAGS =
    {
        "test/magnetartikel.cs2", "test/layout/config/magnetartikel.cs2", "test/cs3_v261/config/magnetartikel.cs2"
    };

    private static final String[] LOCOMOTIVES = {"test/lokomotive.cs2", "test/lokomotive_cs3.cs2"};

    private static String url(String path)
    {
        return new File(path).toURI().toString();
    }

    /** The whole dump, one line per fact. */
    public static String dump() throws Exception
    {
        List<String> out = new ArrayList<>();
        CS2File parser = new CS2File("0.0.0.0", null);

        for (String folder : LAYOUTS)
        {
            out.add("== layout " + folder);

            try
            {
                parser.setLayoutDataLoc(url(folder));
                List<MarklinAccessory> mags = new File(folder, "config/magnetartikel.cs2").isFile()
                    ? parser.getMagList(true) : new ArrayList<MarklinAccessory>();

                for (LayoutDiagram d : parser.parseLayout(mags))
                {
                    out.add("page " + d.getName() + " id=" + d.getPageId() + " unreadable=" + d.isUnreadable());

                    for (LayoutDiagramComponent c : d.getAll())
                    {
                        out.add("  (" + c.getX() + "," + c.getY() + ") " + c.getType() + " o=" + c.getOrientation()
                            + " addr=" + c.getAddress() + " raw=" + c.getRawAddress() + " prot=" + c.getProtocol()
                            + " state=" + c.getState() + " label=" + c.getLabel());
                        out.add("    " + c.exportToCS2TextFormat().trim().replace("\r", "").replace("\n", " | "));
                    }
                }
            }
            catch (Exception e)
            {
                out.add("EXCEPTION " + e);
            }
        }

        for (String file : MAGS)
        {
            out.add("== accessories " + file);

            try
            {
                for (MarklinAccessory a : parser.parseMags(CS2File.parseFile(CS2File.fetchURL(url(file)))))
                {
                    out.add("  " + a.getName() + " addr=" + a.getAddress() + " uid=" + a.getUID() + " " + a.getType()
                        + " " + a.getDecoderType() + " switched=" + a.isSwitched());
                }
            }
            catch (Exception e)
            {
                out.add("EXCEPTION " + e);
            }
        }

        out.add("== routes test/fahrstrassen.cs2");

        try
        {
            // Without a station to log to, a route lacking its id, item or name is left out here rather than logged
            List<java.util.Map<String, String>> routes = CS2File.parseFile(CS2File.fetchURL(url("test/fahrstrassen.cs2")));
            routes.removeIf(m -> "fahrstrasse".equals(m.get("_type"))
                && (m.get("id") == null || m.get("item") == null || m.get("name") == null));

            for (MarklinRoute r : parser.parseRoutes(routes,
                parser.parseMags(CS2File.parseFile(CS2File.fetchURL(url("test/magnetartikel.cs2"))))))
            {
                out.add("  " + r.toString() + " s88=" + r.getS88() + " | " + r.getRoute());
            }
        }
        catch (Exception e)
        {
            out.add("EXCEPTION " + e);
        }

        for (String file : LOCOMOTIVES)
        {
            out.add("== locomotives " + file);

            try
            {
                for (MarklinLocomotive l : parser.parseLocomotives(CS2File.parseFile(CS2File.fetchURL(url(file)))))
                {
                    out.add("  " + l.toString().replace("\n", " | ") + " | functions=" + l.getNumF()
                        + " | image=" + l.getImageURL());
                }
            }
            catch (Exception e)
            {
                out.add("EXCEPTION " + e);
            }
        }

        return String.join("\n", out) + "\n";
    }

    @Test
    public void testEveryCS2InputParsesAsBefore() throws Exception
    {
        String[] want = new String(Files.readAllBytes(new File(GOLDEN).toPath()), StandardCharsets.UTF_8)
            .replace("\r", "").split("\n");
        String[] got = support.SavedProtocol.inParseDump(dump()).split("\n");

        for (int i = 0; i < Math.min(want.length, got.length); i++)
        {
            assertEquals(got[i], want[i], "line " + (i + 1) + " of " + GOLDEN);
        }

        assertEquals(got.length, want.length, "lines in " + GOLDEN);
        assertTrue(want.length > 1000, "the golden covers the inputs: " + want.length + " lines");
    }

    /** Writes the golden: run on the code before the change. */
    public static void main(String[] args) throws Exception
    {
        Files.write(new File(args.length > 0 ? args[0] : GOLDEN).toPath(), dump().getBytes(StandardCharsets.UTF_8));
    }
}
