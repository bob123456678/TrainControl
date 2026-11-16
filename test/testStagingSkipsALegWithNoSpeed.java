import java.util.List;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.base.Accessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.udp.CS2Message;

/**
 * A locomotive with no speed loses its own Return Home leg, not everybody else's.
 *
 * runLocomotives already skips a locomotive whose preferred speed is outside 1 to 100, with a line in
 * the log, and starts every other one.  The timetable's dispatch loop never learned that: executePath
 * refuses such a locomotive and returns false, which the retry loop read as busy track.  It waited,
 * asked again, and after three attempts declared the entry stuck, stopped every train and abandoned
 * the run - so one train placed on the graph without its speed ever being set ended a whole Return
 * Home.
 *
 * Its own class because it needs debug mode, which setSimulate requires and testHomeStaging does not
 * init with.
 *
 * Ported from the 3.0 branch (fd31d2b2, SG-A5).
 */
public class testStagingSkipsALegWithNoSpeed
{
    private static MarklinControlStation model;

    private static final String LOC_STUCK = "SG stuck";
    private static final String LOC_MOVING = "SG moving";

    private static final int STUCK_ADDRESS = 76;
    private static final int MOVING_ADDRESS = 77;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Debug mode last, because simulation requires it
        model = init(null, true, false, false, true);
        model.stop();

        model.newMM2Locomotive(LOC_STUCK, STUCK_ADDRESS);
        model.newMM2Locomotive(LOC_MOVING, MOVING_ADDRESS);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        if (model != null)
        {
            if (model.hasAutoLayout()) model.getAutoLayout().stopLocomotives();

            model.deleteLoc(LOC_STUCK);
            model.deleteLoc(LOC_MOVING);
        }
    }

    /**
     * A train placed by hand with no speed is given the graph's default, as loading the graph gives it (3.0's
     * MT-233).  It kept none until the next reload, so Start and Return Home skipped a train the operator had just
     * put down.
     *
     * MUTATION: take the default out of moveLocomotive, and this fails.
     */
    @Test(timeOut = 60000)
    public void testATrainPlacedByHandWithNoSpeedIsGivenTheDefault() throws Exception
    {
        Layout layout = load(ring(LOC_STUCK, null, LOC_MOVING));

        assertTrue(layout.getDefaultLocSpeed() >= 1, "precondition: the graph has no usable default speed");

        loc(LOC_STUCK).setPreferredSpeed(0);

        assertTrue(layout.moveLocomotive(LOC_STUCK, "SG B", false), "precondition: the train was not placed");

        assertEquals(loc(LOC_STUCK).getPreferredSpeed(), layout.getDefaultLocSpeed(), "a train placed by hand with "
            + "no speed kept none - loading the graph gives it the default, placing it did not, so Start and Return "
            + "Home skipped a train the operator had just put down");
    }

    /**
     * The other train still goes home even though one train has no speed - kept as the guard for a train whose speed is
     * outside 1 to 100 when it is sent, now that a placement gives one with none the default.
     *
     * ON A DEADLINE: this drives the real dispatch loop, and a regression here is a hang rather than a
     * failure.
     */
    @Test(timeOut = 180000)
    public void testAnEntryWithNoSpeedLosesItsOwnLegAndNotTheRun() throws Exception
    {
        Layout layout = load(ring(LOC_STUCK, null, LOC_MOVING));

        try
        {
            layout.setSimulate(true);
        }
        catch (Exception e)
        {
            throw new SkipException("simulation could not be enabled: " + e.getMessage());
        }

        assertTrue(layout.isSimulate(), "simulation must be on before anything is asked to move");

        // Each train is one station away from the home it is given
        layout.setHomeLocomotive("SG B", LOC_STUCK);
        layout.setHomeLocomotive("SG D", LOC_MOVING);

        loc(LOC_MOVING).setPreferredSpeed(35);
        loc(LOC_STUCK).setPreferredSpeed(0);

        assertTrue(loc(LOC_STUCK).getPreferredSpeed() < 1,
            "precondition: one locomotive has no usable speed, as one placed without the speed "
            + "dialog has");

        HomeStaging.Plan plan = layout.loadReturnToHomeTimetable();

        assertTrue(plan.isPossible(), "precondition: Return Home must have a plan: " + plan);

        assertTrue(layout.isTimetableSequential(),
            "precondition: a Return Home run goes one train at a time");

        List<TimetablePath> legs = layout.getTimetable();

        assertEquals(legs.size(), 2, "precondition: one leg for each train: " + legs);

        final boolean[] ranToTheEnd = new boolean[1];
        final Throwable[] thrown = new Throwable[1];

        Thread run = new Thread(() ->
        {
            try
            {
                ranToTheEnd[0] = layout.executeTimetable();
            }
            catch (Throwable bad)
            {
                thrown[0] = bad;
            }
        });

        run.start();
        run.join(120000);

        assertFalse(run.isAlive(),
            "the Return Home run did not finish in two minutes - the dispatch loop is stuck");

        if (thrown[0] != null) throw new RuntimeException(thrown[0]);

        assertEquals(layout.getLocomotiveLocation(loc(LOC_MOVING)).getName(), "SG D",
            "the train with a speed never got home: one locomotive without a speed ended the whole "
            + "Return Home run");

        assertEquals(layout.getLocomotiveLocation(loc(LOC_STUCK)).getName(), "SG A",
            "the locomotive with no speed moved, which it must not - a skip is not a dispatch");

        assertTrue(ranToTheEnd[0],
            "the run reported itself abandoned - every train stopped and the operator told the "
            + "path stayed blocked - because one locomotive had no speed set");
    }

    /**
     * And when the leg with no speed comes FIRST, the leg after it still runs.
     *
     * A Return Home run starts each leg only once the leg before it has started - its execution time
     * is set.  A skipped leg never starts, so the skip stamps it; without the stamp the next leg waits
     * for ever for a train that will never set off, and the run hangs with Start greyed until a
     * restart.  The test above plans the leg with no speed LAST, where nothing waits on it, so it
     * cannot see a missing stamp (BPV-C2).  Here the train with no speed stands where its leg is
     * planned first.
     *
     * ON A DEADLINE, as above.
     */
    @Test(timeOut = 90000)
    public void testAnEntryWithNoSpeedPlannedFirstDoesNotHoldUpTheNext() throws Exception
    {
        Layout layout = load(ring(LOC_MOVING, null, LOC_STUCK));

        try
        {
            layout.setSimulate(true);
        }
        catch (Exception e)
        {
            throw new SkipException("simulation could not be enabled: " + e.getMessage());
        }

        assertTrue(layout.isSimulate(), "simulation must be on before anything is asked to move");

        // Each train is one station away from the home it is given
        layout.setHomeLocomotive("SG B", LOC_MOVING);
        layout.setHomeLocomotive("SG D", LOC_STUCK);

        loc(LOC_MOVING).setPreferredSpeed(35);
        loc(LOC_STUCK).setPreferredSpeed(0);

        HomeStaging.Plan plan = layout.loadReturnToHomeTimetable();

        assertTrue(plan.isPossible(), "precondition: Return Home must have a plan: " + plan.getOutcome());

        List<TimetablePath> legs = layout.getTimetable();

        assertEquals(legs.size(), 2, "precondition: one leg for each train: " + legs);

        assertEquals(legs.get(0).getLoc(), loc(LOC_STUCK),
            "precondition: the leg with no speed has to be planned first, which is the case where the "
            + "next leg waits on its stamp - the plan's order changed, and this no longer tests it: "
            + legs);

        final boolean[] ranToTheEnd = new boolean[1];
        final Throwable[] thrown = new Throwable[1];

        Thread run = new Thread(() ->
        {
            try
            {
                ranToTheEnd[0] = layout.executeTimetable();
            }
            catch (Throwable bad)
            {
                thrown[0] = bad;
            }
        });

        run.start();
        run.join(30000);

        assertFalse(run.isAlive(),
            "the Return Home run did not finish in thirty seconds - the leg after the one with no "
            + "speed is waiting for that leg to start, which it never will");

        if (thrown[0] != null) throw new RuntimeException(thrown[0]);

        assertEquals(layout.getLocomotiveLocation(loc(LOC_MOVING)).getName(), "SG B",
            "the train with a speed never got home: the leg with no speed, planned first, held up the "
            + "one after it");

        assertEquals(layout.getLocomotiveLocation(loc(LOC_STUCK)).getName(), "SG C",
            "the locomotive with no speed moved, which it must not - a skip is not a dispatch");

        assertTrue(ranToTheEnd[0],
            "the run reported itself abandoned because one locomotive had no speed set");
    }

    /**
     * Return Home waits while the track power is off, and goes on once it is back.  Each attempt was refused with
     * the power off, so after three attempts two seconds apart the run said the leg was stuck, stopped every train
     * and gave up, with the train still away from home.
     *
     * ON A DEADLINE, as above.
     */
    @Test(timeOut = 90000)
    public void testReturnHomeWaitsForThePower() throws Exception
    {
        returnHomeThroughACut(false);
    }

    /**
     * The same when the power goes off while the leg's route is being set, so that attempt is refused: it is not
     * counted towards giving up, and the next waits for the power.
     *
     * ON A DEADLINE, as above.
     */
    @Test(timeOut = 90000)
    public void testReturnHomeWaitsForThePowerCutWhileItsRouteIsSet() throws Exception
    {
        returnHomeThroughACut(true);
    }

    /**
     * Sends one train home, one station away, through 9 s of power cut: from the start, or once the leg's route,
     * five switches, is being set.
     */
    private void returnHomeThroughACut(boolean whileTheRouteIsSet) throws Exception
    {
        Layout layout = load(ring(LOC_MOVING, null, null));

        if (whileTheRouteIsSet)
        {
            for (int i = 0; i < 5; i++)
            {
                layout.getEdge("SG A", "SG B").addConfigCommand(model.newSwitch(90 + i,
                    Accessory.accessoryDecoderType.MM2, false).getName(), Accessory.accessorySetting.TURN);
            }
        }

        try
        {
            layout.setSimulate(true);
        }
        catch (Exception e)
        {
            throw new SkipException("simulation could not be enabled: " + e.getMessage());
        }

        layout.setHomeLocomotive("SG B", LOC_MOVING);
        loc(LOC_MOVING).setPreferredSpeed(35);

        HomeStaging.Plan plan = layout.loadReturnToHomeTimetable();

        assertTrue(plan.isPossible(), "precondition: Return Home must have a plan: " + plan.getOutcome());
        assertEquals(layout.getTimetable().size(), 1, "precondition: one leg: " + layout.getTimetable());

        final boolean[] ranToTheEnd = new boolean[1];
        final Throwable[] thrown = new Throwable[1];

        Thread run = new Thread(() ->
        {
            try
            {
                ranToTheEnd[0] = layout.executeTimetable();
            }
            catch (Throwable bad)
            {
                thrown[0] = bad;
            }
        });

        try
        {
            if (whileTheRouteIsSet)
            {
                run.start();

                // Setting the route takes about 0.9 s
                Thread.sleep(300);
                power(false);
            }
            else
            {
                power(false);
                run.start();
            }

            // Longer than three refused attempts two seconds apart, each setting the route first
            Thread.sleep(9000);

            assertTrue(run.isAlive(), "the Return Home run ended while the power was off: it gave up on the leg "
                + "instead of waiting for the power");

            assertEquals(layout.getLocomotiveLocation(loc(LOC_MOVING)).getName(), "SG A",
                "the train moved while the power was off");
        }
        finally
        {
            power(true);
        }

        run.join(60000);

        assertFalse(run.isAlive(), "the Return Home run did not finish within a minute of the power coming back");

        if (thrown[0] != null) throw new RuntimeException(thrown[0]);

        assertTrue(ranToTheEnd[0], "the run reported itself abandoned, the leg stuck and every train stopped, "
            + "because the power was off for a while");

        assertEquals(layout.getLocomotiveLocation(loc(LOC_MOVING)).getName(), "SG B",
            "the train never got home after the power came back");
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    /**
     * The track power going off or on, as the Central Station reports it; the simulation sends no echo of its own.
     */
    private static void power(boolean on) throws InterruptedException
    {
        model.receiveMessage(new CS2Message(CS2Message.CMD_SYSTEM,
            new byte[] { 0, 0, 0, 0, (byte) (on ? CS2Message.CMD_SYSSUB_GO : CS2Message.CMD_SYSSUB_STOP) }));

        long deadline = System.currentTimeMillis() + 2000;

        while (model.getPowerState() != on && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(10);
        }

        assertEquals(model.getPowerState(), on, "precondition: the power did not go " + (on ? "on" : "off"));
    }

    /**
     * Four stations in a ring, every edge in both directions, so the graph never refuses anything.
     */
    private static String ring(String locAtA, String locAtB, String locAtC)
    {
        return ("{'points': ["
            + station("SG A", 0, locAtA) + ","
            + station("SG B", 1, locAtB) + ","
            + station("SG C", 2, locAtC) + ","
            + station("SG D", 3, null)
            + "],'edges': ["
            + edge("SG A", "SG B") + "," + edge("SG B", "SG A") + ","
            + edge("SG B", "SG C") + "," + edge("SG C", "SG B") + ","
            + edge("SG C", "SG D") + "," + edge("SG D", "SG C") + ","
            + edge("SG D", "SG A") + "," + edge("SG A", "SG D")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 35}").replace('\'', '"');
    }

    private static String station(String name, int s88Offset, String loc)
    {
        return "{'name': '" + name + "', 'station': true, 's88': " + (8990 + s88Offset)
            + (loc == null ? "" : ", 'loc': {'name': '" + loc + "'}") + "}";
    }

    private static String edge(String from, String to)
    {
        return "{'start': '" + from + "', 'end': '" + to + "'}";
    }

    private static Layout load(String config)
    {
        model.parseAuto(config);

        Layout layout = model.getAutoLayout();

        assertTrue(layout.isValid(),
            "precondition: the test graph must parse - " + Layout.getLastError());

        return layout;
    }

    private static MarklinLocomotive loc(String name)
    {
        return model.getLocByName(name);
    }
}
