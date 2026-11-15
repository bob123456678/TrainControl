package core;

import java.io.File;
import java.nio.file.Files;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
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
 * MUTATION: read a function's `isMoment` as false (CS2File.parseLocomotivesCS3), and the function triggers in the dump
 * differ.
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
            support.HeldState.dump(m, out);
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
}
