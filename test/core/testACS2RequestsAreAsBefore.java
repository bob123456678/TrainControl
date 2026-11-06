package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import support.StandInStation;

/**
 * Every request TrainControl sends a CS2, in the order it sends them with their query strings: a sync from the station,
 * a resync, a sync again from an emptied railway, a sync with a local layout folder, the check for renamed locomotives
 * and the layout download, the last two with and without a local folder set.  The golden,
 * `test/baseline/cs2_requests_golden.txt`, was written by this class run on dev at e9bbd789 and never changes: it is the
 * proof that no CS2 request moves when the CS3 code does.
 *
 * MUTATION: have getRenameProposals ask the CS3 locomotive endpoint for a CS2 (call parseLocomotivesCS3 whatever
 * `isCS3` says), and the rename lines differ.
 *
 * @author Adam
 */
public class testACS2RequestsAreAsBefore
{
    private static final String GOLDEN = "test/baseline/cs2_requests_golden.txt";

    private static final byte[] CS2_DEVICE = "[geraet]\ngeraet\n .produkt=Central Station 2\n"
        .getBytes(StandardCharsets.UTF_8);

    @Test
    public void testTheRequestsOfACS2AreAsBefore() throws Exception
    {
        Path root = Files.createTempDirectory("tc-cs2-requests");
        String actual;
        File served = railway(new File(root.toFile(), "station"));

        // The local folder: the same railway, so the sync reads its layout from there
        File local = railway(new File(root.toFile(), "local"));

        try (support.StationRig rig = new support.StationRig(root.toFile(), (path, n) ->
            path.equals("/config/geraet.vrs") ? CS2_DEVICE : StandInStation.file(served.getPath() + path)))
        {
            actual = rig.scenario(root.toFile(), local).replace(rig.station.address(), "STATION");
        }

        support.GoldenCheck.check(actual, GOLDEN, "test/baseline/cs2_requests_golden_changes.tsv", "cs2_requests_actual.txt");
    }

    /** test/layout with the CS2 locomotive and route files, as a station's folder. */
    private static File railway(File to) throws Exception
    {
        new File(to, "config/gleisbilder").mkdirs();

        for (String file : new String[] {"gleisbild.cs2", "magnetartikel.cs2", "gleisbilder/test.cs2"})
        {
            Files.copy(new File("test/layout/config/" + file).toPath(), new File(to, "config/" + file).toPath());
        }

        Files.copy(new File("test/lokomotive.cs2").toPath(), new File(to, "config/lokomotive.cs2").toPath());
        Files.copy(new File("test/fahrstrassen.cs2").toPath(), new File(to, "config/fahrstrassen.cs2").toPath());
        return to;
    }
}
