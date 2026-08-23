package regression;

import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A platform with two protecting signals throws BOTH of them.
 *
 * MT-023, Adam: "Does not work - only first is set to red. Selection process is ok."
 *
 * A station may be reachable from each end, so it may be protected by a signal on each approach. They
 * are commanded together and show the same aspect - they say the same thing about the same platform -
 * and a platform guarded at one end and open at the other is worse than one guarded at neither,
 * because it looks protected.
 *
 * **Why this test is at the layout rather than in the editor.** Reading the chain settled where it is
 * NOT: the store keeps a list, `protectingSignalNames` maps every one of them, the builder writes one
 * as a bare string and several as an array, `fromJSON` reads both shapes back (testAutoLayout covers
 * that), and the aspect is memoised per ACCESSORY rather than per Point - with a comment recording that
 * keying it per Point was itself a bug, because one copy of a square wrote a memo while standing empty
 * and the signal stayed green with a train at the platform.
 *
 * Every link handles several. What no test covered was the end of the chain actually being reached for
 * more than one - which is exactly what "only first is set to red" describes.
 *
 * `refreshAllProtectingSignals` is public and asks each signal directly, without the "only while
 * running" guard that protects the per-occupancy path, so this needs no trains and no hardware.
 *
 * @author Adam
 */
public class testBothProtectingSignalsAreThrown
{
    private static MarklinControlStation model;

    /** The test range testAccessory established; 280-285 are its own, so these are the next free two */
    private static final int NEAR = 286;
    private static final int FAR = 287;

    private static MarklinAccessory near;
    private static MarklinAccessory far;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);

        near = model.getAccessoryByAddress(NEAR, Accessory.accessoryDecoderType.MM2);
        far = model.getAccessoryByAddress(FAR, Accessory.accessoryDecoderType.MM2);

        assertNotNull(near, "could not get an accessory at " + NEAR);
        assertNotNull(far, "could not get an accessory at " + FAR);
    }

    @AfterClass
    public static void tearDownClass()
    {
        // Left as found.  These are rows in the operator's own accessory database, and a test that
        // leaves two signals thrown has changed what his railway believes about itself.
        if (near != null) near.setState(Accessory.accessorySetting.GREEN);
        if (far != null) far.setState(Accessory.accessorySetting.GREEN);

        if (model != null) model.stop();
    }

    /**
     * Occupied: both red. Empty again: both green.
     */
    @Test
    public void testAPlatformGuardedAtBothEndsThrowsBothSignals() throws Exception
    {
        Layout layout = Layout.fromJSON(twoSignalLayout(), model);

        assertNotNull(layout, "the layout did not parse");
        assertTrue(layout.isValid(), "the layout is invalid: " + Layout.getLastError());

        assertEquals(layout.getPoint("PLATFORM").getProtectingSignals(),
            Arrays.asList(near.getName(), far.getName()),
            "the platform did not come back protected by both signals, so anything below this would "
            + "be testing the wrong thing");

        near.setState(Accessory.accessorySetting.GREEN);
        far.setState(Accessory.accessorySetting.GREEN);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertNotNull(loc, "no locomotive to stand on the platform");

        layout.getPoint("PLATFORM").setLocomotive(loc);

        layout.refreshAllProtectingSignals();

        assertTrue(near.isSwitched(),
            "the first signal protecting an occupied platform was not thrown");

        assertTrue(far.isSwitched(),
            "only the FIRST signal was thrown. The platform is guarded at one end and open at the "
            + "other, which is worse than being guarded at neither - it looks protected (MT-023)");

        // And back, because a rule that only closes is half a rule
        layout.getPoint("PLATFORM").setLocomotive(null);

        layout.refreshAllProtectingSignals();

        assertFalse(near.isSwitched(), "the first signal stayed red over an empty platform");

        assertFalse(far.isSwitched(),
            "the second signal stayed red over an empty platform - which holds every train that "
            + "approaches from that end, for good");
    }

    /**
     * Deleting a locomotive takes it off the railway, not just out of the lists.
     *
     * UR-3, from the uninformed review. `locDeleted` sweeps six things - the run list, the active
     * locomotives, the milestones, each Point's exclusions, each Point's home, and the home claims -
     * and does not clear the locomotive STANDING on a Point.
     *
     * Two consequences, both bad. The square stays occupied by a train that no longer exists, so
     * nothing can ever be routed through it again; and `Point.toJSON` writes the name back out, so the
     * next load reports a locomotive that is not in the database and **invalidates the whole
     * configuration** - which answers null for every point in it, so the railway simply stops working.
     *
     * The same shape as the exclusions and the home two lines above it, both of which had to be added
     * later for exactly this reason. Their own comments say so.
     */
    @Test
    public void testDeletingALocomotiveTakesItOffThePointItStandsOn() throws Exception
    {
        Layout layout = Layout.fromJSON(twoSignalLayout(), model);

        assertTrue(layout.isValid(), "the layout is invalid: " + Layout.getLastError());

        Locomotive standing = model.getLocByName(model.getLocList().get(0));

        assertNotNull(standing, "no locomotive to stand on the platform");

        layout.getPoint("PLATFORM").setLocomotive(standing);

        assertEquals(layout.getPoint("PLATFORM").getCurrentLocomotive(), standing,
            "the locomotive was not placed, so nothing below tests anything");

        layout.locDeleted(standing);

        assertNull(layout.getPoint("PLATFORM").getCurrentLocomotive(),
            "a deleted locomotive is still standing on the platform. The square is occupied by a train "
            + "that does not exist, so nothing can be routed through it again (UR-3)");

        assertFalse(layout.getPoint("PLATFORM").toJSON().toString().contains(standing.getName()),
            "the deleted locomotive's name is still written into the configuration. On the next load "
            + "that is a locomotive not in the database, which invalidates the WHOLE configuration - "
            + "every point in it then answers null and the railway stops working");
    }

    /**
     * A layout with one platform guarded at each end.
     *
     * The s88 numbers and the run-wide delays are here because `fromJSON` invalidates the WHOLE layout
     * over a missing one - and an invalidated layout answers null for every point in it, which reads
     * exactly like the signals having been dropped.
     */
    private String twoSignalLayout()
    {
        return "{"
            + "\"points\": ["
            + "  {\"name\": \"PLATFORM\", \"station\": true, \"s88\": 106,"
            + "   \"protectingSignal\": [\"" + near.getName() + "\", \"" + far.getName() + "\"]},"
            + "  {\"name\": \"APPROACH\", \"station\": true, \"s88\": 107}"
            + "],"
            + "\"edges\": [{\"start\": \"APPROACH\", \"end\": \"PLATFORM\", \"length\": 1}],"
            + "\"minDelay\": 1, \"maxDelay\": 2, \"defaultLocSpeed\": 35}";
    }
}
