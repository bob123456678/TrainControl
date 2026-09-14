package regression;

import java.lang.reflect.Field;
import java.util.HashMap;
import javax.swing.JToggleButton;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * The function buttons offer what the CONSIST can drive, not what its head can (Adam, MT-359).
 *
 * *"does it propagate to the UI check that determines which function buttons are active?"*  It did not.
 *
 * **The model half has been right since 2026-09-11** - `setF` accepts the whole consist's range, so an
 * MM2 head with an MFX member drives f6 through the member, and `core.testMultiUnitMembership
 * .testAConsistCanDriveEveryFunctionItsMembersHave` covers it.  The window went on lighting buttons from
 * `getNumF()`, the HEAD's own count, so that function worked from a keyboard shortcut, a route or
 * autonomy and was greyed on screen - and the F20-F31 tab was removed outright.  The guard and the
 * affordance asking different questions is `OB-057`/`OB-090` arriving in a third place.
 *
 * **Driven through `repaintLoc`**, which is what the window calls when the active locomotive changes,
 * rather than by reading a predicate: whether a button is enabled is decided there, and every
 * model-level question answers the same before and after the fix.
 *
 * **No icons past the head's range, on Adam's word** - *"It's ok if those function types are default and
 * the buttons show no icons if the head is mm2."*  That is not laziness: `getFunctionType` and
 * `getFunctionIconUrl` read tables sized to the head's own decoder, which is the hazard `getF`'s javadoc
 * warns about.
 *
 * **And no TEXT either** (Adam, OB-213): *"don't print 'F<x>' text labels on the function buttons - keep
 * the label blank as is the default."*  The first cut wrote "F6" into them, reasoning that a button with
 * neither icon nor text says nothing about itself.  It says what every other iconless function button on
 * this window says, which is the point - these are ordinary buttons whose decoder has no picture for
 * them, and a number on some buttons and not others reads as a difference in kind where there is none.
 *
 * MUTATION: put `getNumF()` back in either loop and the first claim fails; light every button regardless
 * and the two-MM2 control fails; write the number back into the label and the blank-label claim fails.
 *
 * @author Adam
 */
public class testTheFunctionButtonsFollowTheConsist
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the function buttons are on a window");
        }

        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, false);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            for (String name : new String[] {"MU head UI", "MU member UI", "MU plain UI", "MU plain UI2"})
            {
                if (model != null && model.getLocByName(name) != null) model.deleteLoc(name);
            }

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * An MM2 head with an MFX member lights the buttons its member can drive.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAMixedConsistLightsTheWiderRange() throws Exception
    {
        MarklinLocomotive head = model.newMM2Locomotive("MU head UI", 91);
        MarklinLocomotive member = model.newMFXLocomotive("MU member UI", 92);

        assertTrue(head.getNumF() < 7,
            "precondition: the MM2 head already has f6 of its own, so this proves nothing");

        assertTrue(member.getNumF() > 6,
            "precondition: the MFX member has no more functions than the head");

        link(head, member);

        assertTrue(head.drivableFunctionCount() > 6,
            "precondition: the model does not think this consist can drive f6, so the window has"
            + " nothing to follow - the half tested by core.testMultiUnitMembership has regressed");

        show(head);

        assertTrue(button(6).isEnabled(),
            "f6 is greyed for a consist that can drive it. Adam, MT-359: \"does it propagate to the UI"
            + " check that determines which function buttons are active?\" - setF accepts it, a"
            + " keyboard shortcut works, and the button says no");

        assertTrue(button(6).isVisible(), "f6 is not even on screen for a consist that can drive it");
    }

    /**
     * And a consist of two MM2s still stops where MM2 stops.
     *
     * Without this the claim above is satisfied by lighting every button for every locomotive, which
     * is the direction this change would most easily go wrong in.
     *
     * @throws Exception from the window
     */
    @Test(dependsOnMethods = "testAMixedConsistLightsTheWiderRange")
    public void testAnAllMM2ConsistKeepsTheMM2Range() throws Exception
    {
        MarklinLocomotive head = model.newMM2Locomotive("MU plain UI", 93);
        MarklinLocomotive member = model.newMM2Locomotive("MU plain UI2", 94);

        link(head, member);

        show(head);

        assertFalse(button(6).isEnabled(),
            "f6 is offered for a consist of two MM2 locomotives, neither of which has it. The rule is"
            + " the highest of the MEMBERS, not the highest there is." + whatTheConsistIs(head));
    }

    /**
     * What the window was actually looking at, appended to a failure.
     *
     * This claim failed once inside a full battery on 2026-09-13 and passes on its own, and the
     * message named only the conclusion - which leaves guessing between three unrelated causes: the
     * head not being the MM2 it was asked for, the member resolving to something else by NAME
     * (consists are held by name and looked up in the model's database, so a collision resolves to a
     * different locomotive), or the window drawing a different locomotive than the one this set.
     *
     * A flaky guard reads as protection while providing none, and one that cannot say why costs an
     * investigation every time it fires. This is the cheapest thing that tells the three apart.
     *
     * @param head the consist's head, as this test built it
     * @return a description, ready to append
     */
    /**
     * The last locomotive asked for is the one the window draws, however quickly the two requests came
     * (TDR-B5, FTN-B1).
     *
     * The guard this pins against returned without doing anything while an earlier render was unfinished, so a
     * request made in that window was dropped and the window went on showing the first locomotive's function
     * buttons.  The repair keeps such a request and runs it when the painting finishes.
     *
     * **Only that re-run can make this pass.**  The renderer is held BEFORE it posts a render asked for a
     * locomotive that is not the one shown - so when that painting runs it leaves the panel alone - and the
     * mixed consist is asked for while the render is certainly in flight.  Nothing but the deferred re-run in
     * `renderFinished` then draws the mixed consist.  The first version of this held the renderer after posting,
     * by which time the painting could already have cleared the flag, and the deferral went untested (FTN-B1).
     *
     * MUTATION, run: delete the `renderActiveLoc(force, locs)` at the end of `renderFinished` and this goes red.
     *
     * @throws Exception from the window
     */
    @Test(dependsOnMethods = {"testAMixedConsistLightsTheWiderRange", "testAnAllMM2ConsistKeepsTheMM2Range"})
    public void testTheLastLocomotiveAskedForIsTheOneDrawn() throws Exception
    {
        MarklinLocomotive mixed = model.getLocByName("MU head UI");
        MarklinLocomotive plain = model.getLocByName("MU plain UI");
        MarklinLocomotive notShown = model.getLocByName("MU member UI");

        assertNotNull(mixed, "precondition: the mixed consist is gone");
        assertNotNull(plain, "precondition: the all-MM2 consist is gone");
        assertNotNull(notShown, "precondition: the mixed consist's member is gone");

        show(plain);

        assertFalse(button(6).isEnabled(), "precondition: f6 is lit for the all-MM2 consist even when shown alone");

        Field active = TrainControlUI.class.getDeclaredField("activeLoc");

        active.setAccessible(true);

        final java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        TrainControlUI.beforeARenderIsPosted = () ->
        {
            held.countDown();

            try
            {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        };

        try
        {
            // A render about a locomotive that is not shown: when it paints, it leaves the panel as it is.
            javax.swing.SwingUtilities.invokeAndWait(() ->
                ui.repaintLoc(false, java.util.Collections.<Locomotive>singletonList(notShown)));

            assertTrue(held.await(10, java.util.concurrent.TimeUnit.SECONDS),
                "precondition: the renderer never reached the hook, so no render is in flight");

            // ... and, while that render is certainly in flight, the mixed consist.
            active.set(ui, mixed);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.repaintLoc(true, null));
        }
        finally
        {
            TrainControlUI.beforeARenderIsPosted = null;

            release.countDown();
        }

        waitForTheRender();
        waitForTheRender();

        assertTrue(button(6).isEnabled(),
            "the mixed consist was asked for while a render was in flight, and the window still shows the"
            + " all-MM2 consist's buttons - f6 greyed for a consist that can drive it. The request was not kept"
            + " and run when that render finished (TDR-B5, FTN-B1)");
    }

    /**
     * A locomotive's direction echo does not build the autonomy session on the thread it arrives on (FTN-B2).
     *
     * `followDirectionChanges` runs inside the window's monitor, from `repaintLoc`, on whatever thread the
     * Central Station's message came in on - and it asked `getAutonomySession()`, the lazy builder that parses
     * every page and can rewrite files.  The rule written for exactly this, at `repaintTimetable`, is to read
     * the field.  Since TDR-B5 the event thread takes that monitor at the end of every locomotive render, so a
     * session built there held the whole window up for the length of a parse.
     *
     * @throws Exception from the window or reflection
     */
    @Test
    public void testADirectionEchoDoesNotBuildTheSession() throws Exception
    {
        MarklinLocomotive anyone = model.newMM2Locomotive("MU echo UI", 95);

        try
        {
            Field session = TrainControlUI.class.getDeclaredField("autonomySession");

            session.setAccessible(true);

            // The railway graph exists, so the guard gets as far as asking about the session.
            model.getAutoLayout();

            if (!model.hasAutoLayout()) throw new org.testng.SkipException("no autonomy layout to follow directions on");

            // What the getter WOULD build here, asked on purpose, so the claim below is not about a fixture that
            // could never build one.
            java.lang.reflect.Method getter = TrainControlUI.class.getDeclaredMethod("getAutonomySession");

            getter.setAccessible(true);

            final Object[] built = new Object[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    built[0] = getter.invoke(ui);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });

            if (built[0] == null) throw new org.testng.SkipException("this sandbox builds no autonomy session at all");

            // As `initializeTrackDiagram` leaves it: the session reset, the railway graph still there.
            session.set(ui, null);

            // On an ordinary thread, the way a locomotive message arrives.
            Thread message = new Thread(() -> ui.repaintLoc(false,
                java.util.Collections.<Locomotive>singletonList(anyone)), "a locomotive message");

            message.start();
            message.join(30000);

            assertFalse(message.isAlive(), "precondition: the repaint never returned");

            assertNull(session.get(ui),
                "a direction echo arriving on a message thread built the autonomy session - every page parsed,"
                + " inside the window's monitor - where the rule is to read the field and leave the building to"
                + " the event thread (FTN-B2, SV-B2)");
        }
        finally
        {
            waitForTheRender();

            model.deleteLoc("MU echo UI");
        }
    }

    private static String whatTheConsistIs(MarklinLocomotive head) throws Exception
    {
        StringBuilder said = new StringBuilder();

        said.append("\n  Head ").append(head.getName())
            .append(": decoder ").append(head.getDecoderType())
            .append(", address ").append(head.getAddress())
            .append(", getNumF ").append(head.getNumF())
            .append(", drivableFunctionCount ").append(head.drivableFunctionCount());

        for (Locomotive member : head.getLinkedLocomotives().keySet())
        {
            said.append("\n  Member ").append(member == null ? "NULL" : member.getName())
                .append(": getNumF ").append(member == null ? "-" : member.getNumF());

            if (member instanceof MarklinLocomotive)
            {
                said.append(", decoder ").append(((MarklinLocomotive) member).getDecoderType())
                    .append(", address ").append(((MarklinLocomotive) member).getAddress());
            }
        }

        Field active = TrainControlUI.class.getDeclaredField("activeLoc");

        active.setAccessible(true);

        Object showing = active.get(ui);

        said.append("\n  The window is drawing: ")
            .append(showing == null ? "nothing" : ((Locomotive) showing).getName())
            .append(showing == head ? " (which is this head)" : " (which is NOT this head)");

        return said.toString();
    }

    /**
     * A button the consist can drive past its head's own range carries no text (Adam, OB-213).
     *
     * *"For the buttons on multi-unit Mm2 locomotives paired with mfx/dcc ones, don't print 'F<x>'
     * text labels on the function buttons - keep the label blank as is the default."*
     *
     * Asserted on the same consist the first claim lights, because the two are one gesture: the button
     * is offered AND it looks like every other iconless one. A claim about the enablement alone passed
     * happily while the label said "F6".
     *
     * @throws Exception from the window
     */
    @Test(dependsOnMethods = "testAMixedConsistLightsTheWiderRange")
    public void testTheWidenedButtonsCarryNoText() throws Exception
    {
        MarklinLocomotive head = model.getLocByName("MU head UI");

        assertNotNull(head, "the mixed consist is gone, so there is no widened button to look at");

        show(head);

        assertTrue(button(6).isEnabled(),
            "precondition: f6 is not offered for this consist, so there is no widened button here and"
            + " the claim below would pass on a button nobody widened");

        String label = button(6).getText();

        assertTrue(label == null || label.isEmpty(),
            "f6 is offered through an MFX member and its button is labelled \"" + label + "\". Adam,"
            + " OB-213: \"don't print 'F<x>' text labels on the function buttons - keep the label blank"
            + " as is the default.\" A number on some buttons and not others reads as a difference in"
            + " kind, and there is none - these are ordinary buttons whose decoder has no icon.");

        assertNull(button(6).getIcon(),
            "f6's button carries an icon past the head's own range, which reads a function-type table"
            + " sized to the head's decoder - the hazard getF's javadoc warns about");
    }

    /** The same two calls `core.testMultiUnitMembership` uses to build a consist. */
    private static void link(MarklinLocomotive head, MarklinLocomotive member)
    {
        java.util.Map<String, Double> list = new HashMap<>();

        list.put(member.getName(), 1.0);

        head.preSetLinkedLocomotives(list);
        head.setLinkedLocomotives();
    }

    /** Makes this locomotive the active one and lets the window draw its panel. */
    private static void show(Locomotive loc) throws Exception
    {
        Field active = TrainControlUI.class.getDeclaredField("activeLoc");

        active.setAccessible(true);

        // WAIT FOR THE WINDOW'S OWN RENDER, BEFORE AND AFTER (2026-09-14).
        //
        // `repaintLoc` returns without doing anything while an earlier render is still running, and the
        // render itself goes to a worker and back through `invokeLater`.  So a `show` straight after
        // another `show` - which is what `testTheWidenedButtonsCarryNoText` does after the all-MM2 claim
        // - could have its repaint dropped, and the buttons read were the previous consist's: f6 greyed
        // for a consist that can drive it.  One pump of the event thread was never a wait for either.
        waitForTheRender();

        active.set(ui, loc);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui.repaintLoc(true, null));

        waitForTheRender();
    }

    /** Until every render the window has queued is finished, and the event thread has run what they posted. */
    @SuppressWarnings("unchecked")
    private static boolean waitForTheRender() throws Exception
    {
        Field futures = TrainControlUI.class.getDeclaredField("locFutures");

        futures.setAccessible(true);

        long deadline = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < deadline)
        {
            boolean done = true;

            synchronized (ui)
            {
                for (java.util.concurrent.Future<?> f
                    : new java.util.ArrayList<>((java.util.List<java.util.concurrent.Future<?>>) futures.get(ui)))
                {
                    done &= f.isDone();
                }
            }

            if (done) return pumped();

            Thread.sleep(20);
        }

        // SAID HERE, not as a greyed f6 three lines later that would blame the rule for a stuck render.
        throw new AssertionError("the window's render did not finish within ten seconds, so the buttons"
            + " below would be read from whatever it last drew");
    }

    private static boolean pumped() throws Exception
    {

        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        javax.swing.SwingUtilities.invokeAndWait(() -> { });

        return true;
    }

    /** The button for a function number, out of the window's own map. */
    @SuppressWarnings("unchecked")
    private static JToggleButton button(int fNumber) throws Exception
    {
        Field mapping = TrainControlUI.class.getDeclaredField("rFunctionMapping");

        mapping.setAccessible(true);

        HashMap<Integer, JToggleButton> buttons =
            (HashMap<Integer, JToggleButton>) mapping.get(ui);

        JToggleButton button = buttons.get(fNumber);

        if (button == null) throw new SkipException("this window has no button for f" + fNumber);

        return button;
    }
}
