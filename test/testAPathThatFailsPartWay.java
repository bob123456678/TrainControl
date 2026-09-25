import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.model.ViewListener;

/**
 * A train whose path fails part way stops autonomy, and no train is sent again until the autonomy
 * configuration is reloaded.
 *
 * When a dispatch throws after its path is locked - a departure function that fails, or anything else
 * in the middle of executePath - the train is stopped and its path deliberately left locked: the train
 * may be standing on that track, and releasing it would let another train be routed into it.  But every
 * point along that path still records the train, so it stands on several points at once;
 * getLocomotiveLocation answers whichever comes first, and autonomy, Return Home or a train sent by
 * hand could then set off from a point the train is not on.  Autonomy used to carry on dispatching the
 * other trains regardless, and only a reload of the configuration clears those reservations.
 *
 * So the failure now stops autonomy the graceful way - no new path is sent, trains already under way
 * finish theirs - and marks the layout invalid, which every dispatch refuses until the configuration is
 * reloaded (BPV-C13, SG-A3; Adam's ruling of 2026-09-25).
 */
public class testAPathThatFailsPartWay
{
    private static MarklinControlStation model;

    private static final String FAILING = "PF failing";
    private static final String OTHER = "PF other";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Debug mode, because simulation requires it
        model = init(null, true, false, false, true);
        model.stop();

        model.newMM2Locomotive(FAILING, 97);
        model.newMM2Locomotive(OTHER, 98);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        if (model != null)
        {
            model.deleteLoc(FAILING);
            model.deleteLoc(OTHER);
        }
    }

    /**
     * A path that fails part way while autonomy runs: autonomy stops, the layout reads as invalid, the
     * next train is refused with "must be reloaded", and the failed train's track stays held.
     *
     * The failure is the locomotive's route-start callback - where the loader installs the departure
     * functions - throwing once the path is locked, which nothing guards.
     *
     * ON A DEADLINE, as a safety net: were the second train ever actually sent, simulation makes it
     * arrive rather than wait for ever for a sensor.
     */
    @Test(timeOut = 60000)
    public void testAPathThatFailsPartWayStopsAutonomyUntilReloaded() throws Exception
    {
        final List<String> keys = Collections.synchronizedList(new ArrayList<>());

        // The model, with every message key it is asked to log written down on the way through
        ViewListener recording = (ViewListener) Proxy.newProxyInstance(
            ViewListener.class.getClassLoader(), new Class<?>[]{ ViewListener.class }, (proxy, method, args) ->
            {
                if (method.getName().equals("logf")) keys.add((String) args[0]);

                try
                {
                    return method.invoke(model, args);
                }
                catch (InvocationTargetException e)
                {
                    throw e.getCause();
                }
            });

        Layout layout = new Layout(recording);

        String[] names = { "PF A", "PF B", "PF C", "PF D" };

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback sensor = model.newFeedback(8590 + i, null);
            model.setFeedbackState(sensor.getName(), false);

            layout.createPoint(names[i], true, sensor.getName());
        }

        layout.createEdge("PF A", "PF B");
        layout.createEdge("PF C", "PF D");

        MarklinLocomotive failing = model.getLocByName(FAILING);
        MarklinLocomotive other = model.getLocByName(OTHER);

        layout.getPoint("PF A").setLocomotive(failing);
        layout.getPoint("PF C").setLocomotive(other);

        layout.setSimulate(true);

        assertTrue(layout.isSimulate(), "precondition: simulation must be on before anything is asked to move");

        failing.setCallback(Layout.CB_ROUTE_START, l ->
        {
            throw new IllegalStateException("the departure function failed");
        });

        try
        {
            // Autonomy running, with nothing of its own to send
            layout.runLocomotives();

            assertTrue(layout.isAutoRunning(), "precondition: autonomy is running");

            try
            {
                layout.executePath(Collections.singletonList(layout.getEdge("PF A", "PF B")), failing, 30, null);

                fail("precondition: the path was meant to fail part way, and it ran");
            }
            catch (IllegalStateException expected)
            {
                // The failure itself still reaches the caller, as before
            }

            assertEquals(layout.getPoint("PF B").getCurrentLocomotive(), failing,
                "precondition: the failed path has to have been locked, with the train recorded along it");

            assertFalse(layout.isAutoRunning(),
                "a train's path failed part way and autonomy carried on sending the others - the failed "
                + "train is recorded on every point of its path, so a later path can set off from a point "
                + "it is not on");

            assertFalse(layout.isValid(),
                "a train's path failed part way and the layout still reads as valid, so a train can be sent "
                + "again before the configuration is reloaded - and only the reload clears the failed "
                + "train's reservations");

            keys.clear();

            assertFalse(layout.executePath(Collections.singletonList(layout.getEdge("PF C", "PF D")), other, 30,
                null), "another train was sent after a path had failed part way, before the configuration "
                + "was reloaded");

            assertTrue(keys.contains("autolayout.errorConfigurationInvalidMustReload"),
                "the refusal did not say the configuration must be reloaded: " + keys);

            // What the message tells the operator, and why nothing else is sent: the track stays held
            assertEquals(layout.getPoint("PF A").getCurrentLocomotive(), failing,
                "the failed train's track was released - it is kept, because the train may be standing on it");

            assertEquals(layout.getPoint("PF B").getCurrentLocomotive(), failing,
                "the failed train's track was released - it is kept, because the train may be standing on it");
        }
        finally
        {
            layout.stopLocomotives();
        }
    }
}
