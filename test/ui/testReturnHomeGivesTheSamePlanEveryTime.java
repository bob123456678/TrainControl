package ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.AbstractButton;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Reflect;
import support.Restart;

/**
 * MT-911 (OB-413) in the window, on his frozen railway with its four trains and their homes as saved: the same
 * arrangement, put back by hand, gives the same Return Home plan at every press, after a restart and under load, read
 * from the log as he reads it, and the trains all come home.  The planner's own claims are
 * `core.testReturnHomeIsTheSameEveryTime`.
 *
 * No claim pins a route: each compares the plans of one arrangement, so s88 directions changed under it (OB-472) change
 * the plans but not the claim.
 *
 * @author Adam
 */
public class testReturnHomeGivesTheSamePlanEveryTime
{
    /** Four trains, each away from its home; every move goes straight home. */
    private static final String[][] FIRST = {{"2-8-4 3505 SP", "TopMainR1"}, {"75 407 DB", "TopR1ParkLong"},
        {"EN57-203", "TunnelCenterPark"}, {"EN57-947", "TunnelRightPark"}};

    /** Four trains elsewhere; EN57-947's home is behind another, so it needs two moves. */
    private static final String[][] SECOND = {{"2-8-4 3505 SP", "TopR1ParkLong"}, {"75 407 DB", "TunnelCenterPark"},
        {"EN57-203", "TunnelRightPark"}, {"EN57-947", "BottomMainC"}};

    private static final Pattern PLAN = lineOf("autolayout.infoReturnToHomePlan");
    private static final Pattern MOVE = lineOf("autolayout.infoReturnToHomeMove");

    private static Restart restart;
    /** The first arrangement's plan, as the first claim read it, for the restart to meet. */
    private static List<String> firstPlan;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        support.Display.needed("Return Home is pressed in the window");

        restart = Restart.open(support.Scenario.folderFor("live-snapshot"));
    }

    /** Every run has come to rest by then: each press waits for it. */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (restart != null) restart.close();
    }

    /** The program running now. */
    private static MarklinControlStation model()
    {
        return restart.getModel();
    }

    /** A log line's pattern, with every placeholder of its message free. */
    private static Pattern lineOf(String key)
    {
        StringBuilder re = new StringBuilder();

        for (String part : I18n.t(key).split("\\{\\d\\}", -1)) re.append(re.length() == 0 ? "" : "(.*)").append(Pattern.quote(part));

        return Pattern.compile(re.toString());
    }

    /** The window's log, oldest line first, each without its stamp. */
    private static List<String> log()
    {
        String text;

        try
        {
            text = Components.onTheEventThread(() -> (String) Reflect.method(TrainControlUI.class, "logText")
                .invoke(model().getGUI()));
        }
        catch (Exception unread)
        {
            throw new IllegalStateException(unread);
        }

        List<String> lines = new ArrayList<>();

        for (String line : text.split("\n")) lines.add(0, line.replaceFirst("^\\d\\d:\\d\\d:\\d\\d ", ""));

        return lines;
    }

    /** The newest plan in the log: its line and the `planned:` line of each move, in the order logged. */
    private static List<String> newestPlan(List<String> log)
    {
        List<String> plan = new ArrayList<>();

        for (int at = log.size() - 1; at >= 0 && plan.isEmpty(); at--)
        {
            if (!PLAN.matcher(log.get(at)).matches()) continue;

            for (int line = at; line == at || line < log.size() && MOVE.matcher(log.get(line)).matches(); line++)
            {
                plan.add(log.get(line));
            }
        }

        return plan;
    }

    private static int plansIn(List<String> log)
    {
        int plans = 0;

        for (String line : log) if (PLAN.matcher(line).matches()) plans++;

        return plans;
    }

    /** Puts each train by hand on its square of the arrangement, and the railway at the simulator's full pace. */
    private static Layout arrange(String[][] arrangement) throws Exception
    {
        Layout railway = model().getAutoLayout();

        railway.setSimulate(true);
        railway.setMinDelay(0);
        railway.setMaxDelay(0);

        for (String[] train : arrangement)
        {
            assertTrue(railway.moveLocomotive(train[0], train[1], false), "precondition: " + train[0] + " could not be"
                + " put on " + train[1]);
        }

        assertNotEquals(railway.triageReturnToHome(), HomeStaging.Outcome.ALREADY_HOME, "precondition: the trains are"
            + " already home");

        return railway;
    }

    /**
     * Presses Return Home, with every processor kept busy while the plan is worked out if `loaded`, and waits for the
     * trains to come home and the run to rest; gives back the plan the log shows.
     */
    private static List<String> pressReturnHome(String[][] arrangement, boolean loaded) throws Exception
    {
        final Layout railway = arrange(arrangement);

        if (!model().getPowerState()) model().go();

        assertTrue(model().waitForPowerState(true, 10000), "precondition: the power is not on");

        final AbstractButton returnHome = (AbstractButton) Reflect.get(TrainControlUI.class, model().getGUI(),
            "returnHomeButton");

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(returnHome::isEnabled), 60000), "precondition:"
            + " Return Home is not offered: " + Components.onTheEventThread(returnHome::getToolTipText));

        final int before = plansIn(log());
        final boolean[] busy = {loaded};

        for (int cpu = 0; loaded && cpu < Runtime.getRuntime().availableProcessors(); cpu++)
        {
            Thread spin = new Thread(() -> { while (busy[0]) Math.sqrt(Math.random()); }, "MT-911 load");

            spin.setDaemon(true);
            spin.start();
        }

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.press(returnHome);

            assertTrue(Components.waitFor(() -> plansIn(log()) > before, 60000), "Return Home logged no plan; the"
                + " window said " + answering.asked());

            busy[0] = false;

            assertTrue(Components.waitFor(() -> railway.getActiveLocomotives().isEmpty()
                && railway.triageReturnToHome() == HomeStaging.Outcome.ALREADY_HOME, support.Settle.PATIENCE_MS),
                "the trains did not all come home, facing as homed; the window said " + answering.asked());
        }
        finally
        {
            busy[0] = false;
        }

        assertTrue(support.Settle.gracefully(railway, support.Settle.PATIENCE_MS), "the run home did not come to rest");

        List<String> log = log();
        List<String> plan = newestPlan(log);

        assertTrue(plan.size() > 2 && plan.get(0).contains("READY"), "precondition: no plan of two moves or more: " + plan);
        assertEquals(plan.size() - 1, Integer.parseInt(PLAN.matcher(plan.get(0)).replaceFirst("$2")), "the log does"
            + " not have one planned: line for each move: " + plan);
        assertFalse(log.contains(I18n.t("autolayout.infoReturnToHomeSearchRanOut")), "the log says the search reached"
            + " its limit");

        return plan;
    }

    /**
     * MT-911 steps 1 to 3, 5 and 6: one arrangement pressed three times, put back by hand each time and the third time
     * with every processor busy, gives one plan, the same moves in the same order, and the trains all come home.
     *
     * MUTATION: in `HomeStaging.greedy`, try the trains in a shuffled order, and this fails.
     */
    @Test(timeOut = 900000)
    public void testOneArrangementGivesOnePlan() throws Exception
    {
        List<String> plan = pressReturnHome(FIRST, false);

        assertEquals(pressReturnHome(FIRST, false), plan, "the second press gave another plan");
        assertEquals(pressReturnHome(FIRST, true), plan, "the third press, with the machine busy, gave another plan");

        firstPlan = plan;
    }

    /**
     * MT-911 step 4: after a restart the first arrangement gives the plan it gave before, and a second arrangement
     * gives its own plan every time.
     *
     * MUTATION: in `HomeStaging.greedy`, try the trains in the order of their identity hash codes, which a restart
     * changes, and this fails.
     */
    @Test(timeOut = 900000, dependsOnMethods = "testOneArrangementGivesOnePlan")
    public void testARestartAndASecondArrangementKeepTheirPlans() throws Exception
    {
        final MarklinControlStation model = restart.again();

        assertTrue(Components.waitFor(() -> model.hasAutoLayout(), 30000), "precondition: the setup did not load after"
            + " the restart");

        assertEquals(pressReturnHome(FIRST, false), firstPlan, "after a restart the first arrangement gave another plan");

        List<String> second = pressReturnHome(SECOND, false);

        assertNotEquals(second, firstPlan, "precondition: the second arrangement gave the first one's plan");
        assertEquals(pressReturnHome(SECOND, false), second, "the second arrangement gave another plan when pressed again");
    }
}
