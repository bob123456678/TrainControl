package core;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import support.HeldState;
import support.StandInStation;
import support.StationRig;

/**
 * A CS3 sync asks the station for each file once: the routes and the locomotives are read from one fetch of the
 * locomotive list, and the positions from the route import's accessory list (FR-207).  A file that is cut off still
 * fails the sync and changes nothing, as a failed fetch is never kept.
 *
 * @author Adam
 */
public class testACS3SyncAsksForEachFileOnce
{
    private static final String INFO_261 = "{\"softwareVersion\":\"2.6.1 (Build 3)\"}";

    private static final String INFO_251 = "{\"softwareVersion\":\"2.5.1 (Build 1)\"}";

    /** Each request of the log that came before, one to a line. */
    private static List<String> repeated(List<String> log)
    {
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();

        for (String request : log) if (!seen.add(request)) out.add(request);

        return out;
    }

    /**
     * A first sync (which reads the diagram from the station) and a resync, for firmware 2.6.1, 2.5.1, and a station
     * whose firmware is found by the probe of the 2.6 locomotive list: no request comes twice.
     *
     * MUTATION: fetch with `fetchURL` in `CS2File.openCS3Locos` rather than `fetchOnce`, and the locomotive list is
     * asked for twice in each sync.
     */
    @Test
    public void testASyncAsksForEachFileOnce() throws Exception
    {
        String[][] stations = { {"260", INFO_261}, {"250", INFO_251}, {"260", null} };

        for (String[] kind : stations)
        {
            try (StationRig rig = new StationRig(Files.createTempDirectory("tc-cs3-once").toFile(),
                StandInStation.cs3(kind[0], kind[1])))
            {
                for (String sync : new String[] {"first sync", "resync"})
                {
                    rig.station.forget();
                    assertTrue(rig.model.syncWithCS2() >= 0, sync + " fails: " + rig.model.getLastSyncError());

                    List<String> log = rig.station.log();

                    assertEquals(repeated(log), new ArrayList<String>(), "the " + sync + " with firmware " + kind[0]
                        + " and info " + kind[1] + " asks for these again: " + log);
                }
            }
        }
    }

    /**
     * A locomotive or accessory list that is cut off every time it is asked for fails the sync with the remedy the user
     * is told, and leaves the routes, locomotives, accessories and pages as they were; the next whole sync takes them.
     *
     * MUTATION: in `CS2File.fetchOnce`, keep a fetch that failed as an empty file, and the sync goes on without the list.
     */
    @Test(dataProvider = "lists")
    public void testACutOffListFailsTheSyncAndChangesNothing(String path, String name) throws Exception
    {
        try (StationRig rig = new StationRig(Files.createTempDirectory("tc-cs3-cut").toFile(),
            StandInStation.cs3("260", INFO_261)))
        {
            MarklinControlStation m = rig.model;

            assertTrue(m.syncWithCS2() >= 0, "the healthy sync fails: " + m.getLastSyncError());
            assertFalse(m.getRouteList().isEmpty() || m.getLocList().isEmpty(), "precondition: the sync took routes"
                + " and locomotives");

            for (String route : new ArrayList<>(m.getRouteList())) m.deleteRoute(route);
            for (String loc : new ArrayList<>(m.getLocList())) m.deleteLoc(loc);

            String before = HeldState.of(m);
            rig.station.cutOff = path;

            assertEquals(m.syncWithCS2(), -1, "a sync whose " + name + " was cut off answered success");
            assertEquals(m.getLastSyncError(), org.traincontrol.util.I18n.f("sync.failedCutOff", name),
                "the sync's reason");
            assertEquals(HeldState.of(m), before, "the sync changed what is held from a cut-off " + name);
            assertTrue(rig.station.requests(path) > 1, "the cut-off list was asked for again, as a cut-off is");

            rig.station.cutOff = null;

            assertTrue(m.syncWithCS2() >= 0, "the next, whole sync fails: " + m.getLastSyncError());
            assertFalse(m.getRouteList().isEmpty() || m.getLocList().isEmpty(), "the next sync takes the routes and"
                + " locomotives");
        }
    }

    @DataProvider
    public static Object[][] lists()
    {
        return new Object[][] { {"/app/api/locos", "locos"}, {"/app/api/mags", "mags"} };
    }
}
