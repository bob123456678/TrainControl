package core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.marklin.udp.CS2Message;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.model.View;

/**
 * A sensor that changes state says so, however it changed.  A module changes state two ways and only one announced it:
 * `parseMessage` (a sensor arriving over the wire) called `feedbackChanged`; `setState` is every OTHER way (clicking an
 * s88 tile, the simulation's own announce and clear, the restore at start-up) and did not.  The route editor's capture
 * listens for `feedbackChanged`, so a sensor the operator had just clicked was invisible to it, and a route being
 * recorded is about what the railway did, not which code path said so.
 *
 * A recording View is installed over the model's own, because asserting on a log line is a different claim: the
 * announcement used to live inside the `isDebug` block, and a capture that depends on a logging setting is the defect
 * wearing a disguise.
 *
 * MUTATION: take `this.network.feedbackChanged(this.getName(), val)` out of
 * `MarklinFeedback.setState` and the first test fails; move it back inside the `isDebug` block and the
 * second does.
 *
 * @author Adam
 */
public class testAFeedbackChangeIsAnnounced
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    /** What the view was told, in order. */
    private static final List<String> announced = new ArrayList<>();

    /** What the view was given to log, in order. */
    private static final List<String> logged = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox first, before the model: init reads the layout preference.
        sandbox = support.LayoutSandbox.open();

        // NOT in debug, deliberately: the announcement used to sit inside the `isDebug` block, and a
        // test run with debug on could not tell that apart from an announcement that always happens.
        model = support.TestStation.simulated();

        View recorder = (View) Proxy.newProxyInstance(View.class.getClassLoader(),
            new Class<?>[] { View.class },
            (proxy, method, args) ->
            {
                if ("feedbackChanged".equals(method.getName()))
                {
                    announced.add(String.valueOf(args[0]) + "=" + String.valueOf(args[1]));
                }

                if ("log".equals(method.getName())) logged.add(String.valueOf(args[0]));

                // Every other View method answers the way an absent view would.
                Class<?> returns = method.getReturnType();

                if (returns == boolean.class) return false;

                if (returns == int.class) return 0;

                return returns.isPrimitive() ? 0 : null;
            });

        // OVER THE MODEL'S OWN VIEW, which is final and private; reflection keeps this inside the test, where a
        // test-only door would be one the application also has.
        Field view = MarklinControlStation.class.getDeclaredField("view");

        view.setAccessible(true);

        Field modifiers = Field.class.getDeclaredField("modifiers");

        modifiers.setAccessible(true);

        modifiers.setInt(view, view.getModifiers() & ~Modifier.FINAL);

        view.set(model, recorder);

        assertNotNull(view.get(model), "the recording view did not take");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /** Setting a sensor by hand announces it. */
    @Test
    public void testASensorSetByHandIsAnnounced() throws Exception
    {
        final int address = 8891;

        model.newFeedback(address, null);

        announced.clear();

        model.getFeedbackState(String.valueOf(address));

        model.setFeedbackState(String.valueOf(address), true);

        assertTrue(announced.contains(address + "=true"),
            "clicking an s88 tile, the simulation firing a sensor and the restore at start-up all "
            + "reach MarklinFeedback.setState, and none of them told anything watching. The route "
            + "editor's capture listens for exactly this, so a sensor the operator had just clicked "
            + "was invisible to a route being recorded (W21-B1). What was announced: " + announced);
    }

    /** A sensor frame from the station: `was` and `is` the old and new state, equal in an answer to a query. */
    private static void parse(MarklinFeedback feedback, int address, int was, int is)
    {
        feedback.parseMessage(new CS2Message(CS2Message.CMD_ACC_SENSOR, 0x7b34, true,
            new byte[] {0, 2, (byte) (address >> 8), (byte) address, (byte) was, (byte) is, 0, 0}));
    }

    /** Sets a sensor occupied by a live event, which is announced and logged, and clears both records. */
    private static MarklinFeedback occupiedByAnEvent(int address)
    {
        MarklinFeedback feedback = model.newFeedback(address, null);
        announced.clear();
        logged.clear();

        parse(feedback, address, 0, 1);
        assertEquals(announced, Arrays.asList(address + "=true"), "a live event was not announced");
        assertTrue(!logged.isEmpty(), "a live event was not logged");

        // The model logs a line only when it differs from the last: this resets that
        model.log("the answer follows");
        announced.clear();
        logged.clear();
        return feedback;
    }

    /**
     * The answer to a query that confirms the state, as a resync after a lost connection gets for every sensor, is no
     * change: not announced, so the route editor's capture does not take it, and not logged.
     *
     * MUTATION: in `MarklinFeedback.parseMessage`, take out
     * `if (m.getData()[4] == m.getData()[5] && was == (state == 1)) return;`.
     */
    @Test
    public void testAnAnswerThatConfirmsTheStateIsNotAnnounced() throws Exception
    {
        MarklinFeedback feedback = occupiedByAnEvent(8893);

        parse(feedback, 8893, 1, 1);

        assertTrue(announced.isEmpty(), "an answer that changed nothing was announced: " + announced);
        assertTrue(logged.isEmpty(), "an answer that changed nothing was logged: " + logged);
    }

    /**
     * An answer that corrects a wrong state is a real change, and is announced and logged as an event is.
     *
     * MUTATION: in `MarklinFeedback.parseMessage`, take out ` && was == (state == 1)`.
     */
    @Test
    public void testAnAnswerThatCorrectsTheStateIsAnnounced() throws Exception
    {
        MarklinFeedback feedback = occupiedByAnEvent(8894);

        parse(feedback, 8894, 0, 0);

        assertEquals(announced, Arrays.asList("8894=false"), "an answer that cleared the sensor was not announced");
        assertTrue(!logged.isEmpty(), "an answer that cleared the sensor was not logged");
    }

    /**
     * And it announces with debug off, where the announcement used to live: announcing only in debug would make the
     * route capture depend on a logging setting.
     */
    @Test
    public void testItIsAnnouncedWithDebugOff() throws Exception
    {
        final int address = 8892;

        model.newFeedback(address, null);

        assertTrue(!model.isDebug(),
            "this model is in debug mode, so it cannot tell an announcement that always happens from "
            + "one that happens only when logging is on - which is the state W21-B1 was fixed from");

        announced.clear();

        model.setFeedbackState(String.valueOf(address), false);

        assertEquals(announced.size(), 1,
            "a sensor set with debug off was announced " + announced.size() + " times rather than "
            + "once: " + announced);
    }
}
