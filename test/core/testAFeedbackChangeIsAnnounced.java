package core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.model.View;

/**
 * A sensor that changes state says so, however it changed.
 *
 * `W21-B1`, and `TWV-C5`: that fix shipped with no test at all. A module changes state two ways and
 * only one of them announced it - `parseMessage`, a sensor arriving over the wire, called
 * `feedbackChanged`; `setState` is every OTHER way and did not. Its callers are clicking an s88 tile on
 * the track diagram, the simulation's own announce and clear, and the restore at start-up.
 *
 * **What that cost.** The route editor's capture listens for `feedbackChanged`, so a sensor the
 * operator had just clicked - or one the simulation had just fired - was invisible to it. The capture
 * cannot tell a clicked sensor from a wired one and should not: a route being recorded is about what
 * the railway did, not about which code path said so.
 *
 * **A recording View rather than a real one**, installed over the model's own. The alternative is to
 * assert on a log line, which is a different claim - the announcement used to live inside the
 * `isDebug` block, and a capture that depends on a logging setting is the defect wearing a disguise.
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

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox first, before the model: init reads the layout preference.
        sandbox = support.LayoutSandbox.open();

        // NOT in debug, deliberately: the announcement used to sit inside the `isDebug` block, and a
        // test run with debug on could not tell that apart from an announcement that always happens.
        model = init(null, true, false, false, false);

        View recorder = (View) Proxy.newProxyInstance(View.class.getClassLoader(),
            new Class<?>[] { View.class },
            (proxy, method, args) ->
            {
                if ("feedbackChanged".equals(method.getName()))
                {
                    announced.add(String.valueOf(args[0]) + "=" + String.valueOf(args[1]));
                }

                // Every other View method answers the way an absent view would.
                Class<?> returns = method.getReturnType();

                if (returns == boolean.class) return false;

                if (returns == int.class) return 0;

                return returns.isPrimitive() ? 0 : null;
            });

        // OVER THE MODEL'S OWN VIEW, which is final and private - the field the model forwards to.
        // A test-only door on the model would be a door the application also has; reflection keeps
        // this entirely inside the test.
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

    /**
     * Setting a sensor by hand announces it.
     */
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

    /**
     * And it announces with debug off, which is where the announcement used to live.
     *
     * The line that does this replaced a comment INSIDE the `isDebug` block saying that a model method
     * could be called there in future. Announcing only in debug mode would make the route capture
     * depend on a logging setting - the same defect, reachable by a preference.
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
