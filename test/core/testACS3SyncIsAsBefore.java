package core;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.RemoteDeviceCollection;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import support.StandInStation;

/**
 * What a sync with a CS3 sends and makes, for firmware 2.5 and 2.6.1: a first sync from the station, a resync, the check
 * for renamed locomotives and the layout download, then a sync with a local layout folder and the same two again.  Each
 * step lists its requests in the order they came, with their query strings; the end lists every accessory and its
 * position, every page with its ID and tiles, the routes, the locomotives and the log.  The golden is
 * `test/baseline/cs3_sync_golden.txt`, written by this class on dev at e9bbd789; later changes to it are listed in
 * `test/baseline/cs3_sync_golden_changes.tsv`, line by line (support.GoldenChanges).
 *
 * The stand-in reports its firmware at /app/api/info (FR-170).  The 2.6 one serves the 2.6.1 capture in `test/cs3_v261` (accessories, their positions, track plans, layout files)
 * and the 2.6 locomotive and route lists; the 2.5 one serves the older lists and accessories.
 *
 * MUTATION: have the route import skip its first route (drop the first element of parsedRoutes in syncWithCS2) and the
 * routes differ.
 *
 * @author Adam
 */
public class testACS3SyncIsAsBefore
{
    private static final String GOLDEN = "test/baseline/cs3_sync_golden.txt";

    private static final String CHANGES = "test/baseline/cs3_sync_golden_changes.tsv";

    @Test
    public void testASyncWithACS3IsAsBefore() throws Exception
    {
        File root = Files.createTempDirectory("tc-cs3-sync").toFile();
        String actual = run(root, "260") + run(root, "250");
        support.GoldenCheck.check(actual.replace(root.getAbsolutePath(), "ROOT")
            .replace(root.getAbsolutePath().replace('\\', '/'), "ROOT"), GOLDEN, CHANGES, "cs3_sync_actual.txt");
    }

    private static String run(File root, String kind) throws Exception
    {
        File dir = new File(root, kind);
        File local = new File(dir, "local");
        StringBuilder out = new StringBuilder("######## firmware " + kind + "\n");

        try (support.StationRig rig = new support.StationRig(dir, StandInStation.cs3(kind, "260".equals(kind) ? "{\"softwareVersion\":\"2.6.1 (Build 3)\"}"
            : "{\"softwareVersion\":\"2.5.1 (Build 1)\"}")))
        {
            copy(new File("test/cs3_v261/config"), new File(local, "config"));
            out.append(rig.scenario(dir, local));
            MarklinControlStation m = rig.model;
            StandInStation s = rig.station;
            dump(m, out);
            out.append("-- log\n");

            for (String line : rig.takeLog())
            {
                out.append("  ").append(line).append("\n");
            }

            return out.toString().replace(s.address(), "STATION");
        }
    }

    private static void copy(File from, File to) throws Exception
    {
        if (from.isDirectory())
        {
            for (File f : from.listFiles())
            {
                copy(f, new File(to, f.getName()));
            }
        }
        else
        {
            to.getParentFile().mkdirs();
            Files.copy(from.toPath(), to.toPath());
        }
    }

    /** Everything the sync made, in a fixed order, one fact to a line. */
    @SuppressWarnings("unchecked")
    private static void dump(MarklinControlStation model, StringBuilder out) throws Exception
    {
        java.lang.reflect.Field f = MarklinControlStation.class.getDeclaredField("accDB");
        f.setAccessible(true);
        RemoteDeviceCollection<MarklinAccessory, Integer> accDB =
            (RemoteDeviceCollection<MarklinAccessory, Integer>) f.get(model);

        section(out, "accessories", accDB.getItems().stream().map(a -> "acc uid=" + Integer.toHexString(a.getUID())
            + " name=" + a.getName() + " " + a.getType() + " " + a.getDecoderType() + " switched=" + a.isSwitched()));
        out.append("-- pages ").append(model.getLayoutList()).append("\n");

        for (String name : model.getLayoutList())
        {
            LayoutDiagram d = model.getLayout(name);
            out.append("page ").append(name).append(" id=").append(d.getPageId()).append(" unreadable=")
                .append(d.isUnreadable()).append("\n");

            for (LayoutDiagramComponent c : d.getAll())
            {
                MarklinAccessory held = (MarklinAccessory) c.getAccessory();
                out.append("  (" + c.getX() + "," + c.getY() + ") " + c.getType() + " addr=" + c.getAddress() + " prot="
                    + c.getProtocol() + (held == null ? "" : " acc=" + held.getName() + "/" + held.isSwitched()) + "\n");
            }
        }

        section(out, "routes", model.getRoutes().stream().map(r -> "route " + r.toString().replace("\n", " | ") + " | "
            + r.getRoute()));
        section(out, "locomotives", model.getLocomotives().stream().map(l -> "loc " + l.getName() + " addr="
            + l.getAddress() + " " + l.getDecoderType() + " uid=" + ((MarklinLocomotive) l).getUID() + " image="
            + ((MarklinLocomotive) l).getImageURL() + " f=" + ((MarklinLocomotive) l).getNumF()));
    }

    /** A heading with its count, then the lines in sorted order. */
    private static void section(StringBuilder out, String heading, java.util.stream.Stream<String> lines)
    {
        List<String> sorted = lines.sorted().collect(java.util.stream.Collectors.toList());
        out.append("-- ").append(heading).append(" ").append(sorted.size()).append("\n");
        sorted.forEach(line -> out.append("  ").append(line).append("\n"));
    }
}
