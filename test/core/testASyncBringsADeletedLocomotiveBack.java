package core;

import java.nio.file.Files;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import support.StandInStation;
import support.StationRig;

/**
 * A locomotive deleted from the database comes back with the next sync of a station that still lists it, which is what
 * the delete question tells the user.
 */
public class testASyncBringsADeletedLocomotiveBack
{
    /**
     * Syncs a stand-in station, deletes one of the locomotives it listed, and syncs again.
     *
     * MUTATION: make the sync skip a locomotive the station lists that the database lacks, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheNextSyncAddsItBack() throws Exception
    {
        try (StationRig rig = new StationRig(Files.createTempDirectory("tc-delete-sync").toFile(),
            StandInStation.cs3("260", null)))
        {
            assertTrue(rig.model.syncWithCS2() >= 0, "the first sync: " + rig.model.getLastSyncError());

            Locomotive one = rig.model.getLocomotives().iterator().next();
            String name = one.getName();

            assertTrue(rig.model.deleteLoc(name));
            assertFalse(rig.model.getLocomotives().stream().anyMatch(l -> l.getName().equals(name)), "not deleted");

            assertTrue(rig.model.syncWithCS2() >= 0, "the second sync: " + rig.model.getLastSyncError());
            assertNotNull(rig.model.getLocByName(name), "the sync did not bring " + name + " back");
        }
    }
}
