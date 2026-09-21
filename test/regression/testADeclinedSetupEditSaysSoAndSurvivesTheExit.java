package regression;

import java.lang.reflect.Field;
import java.util.List;
import javax.swing.SwingUtilities;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * A setup edit that could not be applied says so, and is still there after the exit (MT-267, MT-326).
 *
 * **Two manual tests, one mechanism, and neither could be run by hand.**  VD10-C2 coalesced the setup
 * rebuild into one per gesture, which posts it an event LATER than the write that asked for it.  A run
 * starting inside that window finds the gate shut and the rebuild is refused - correctly, because
 * rebuilding underneath a moving railway is what `prepareAutonomyReload` exists to refuse.
 *
 * MT-267 is that the refusal says so: the edit looked as though it had been made and had not taken
 * effect, with no message anywhere.  Its own step 2 admits the problem - *"this is deliberately hard to
 * hit and you may not manage it"* - which is a test asking to be automated.
 *
 * MT-326 is the second half of the same promise.  The message says the edit *"will be picked up the next
 * time the setup is loaded"*, and that was not true on its own: the save on the way out folds the RUNNING
 * layout back over the configuration and removes what the layout does not carry, and the layout was built
 * before the edit.  Exiting deleted the edit, silently, on the exact path the message called safe
 * (ACC-B3).  Adam, on MT-326: *"I don't understand this test - autonomy shouldn't be editable/startable
 * while running."*  He is right, and the entry was badly written: this is a race, not a mode.
 *
 * **The race is not raced here.**  `isAutonomyBusy()` answers true for a staging flow as well as a
 * running layout, so the state the race produces is reachable without one - which is the whole reason
 * this can be a test at all.  What is asserted is the consequence, not the timing.
 *
 * @author Adam
 */
public class testADeclinedSetupEditSaysSoAndSurvivesTheExit
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static TrainControlUI ui;

    private static AutonomySession session;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the viewer panel this asks about needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));
        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration on the snapshot");
        }

        if (ui.getActiveDiagramConfiguration() == null)
        {
            final String active = session.getStore().getActiveConfiguration();

            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

            settle();
        }

        assertNotNull(ui.getActiveDiagramConfiguration(), "precondition: no configuration is loaded");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A rebuild refused because the railway is busy logs the sentence, and records that it was refused.
     *
     * MT-267's step 3, which its own step 2 says he may never reach by hand.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testTheRefusedRebuildSaysSo() throws Exception
    {
        List<String> heard = listen();

        busy(true);

        try
        {
            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true, null));

            settle();

            assertTrue(said(heard, "autosetup.log.setupEditNotApplied"),
                "the rebuild was refused because the railway is busy and said nothing, so the edit looks"
                + " as though it was made and did not take effect.  Heard: " + heard);

            assertTrue(declined(),
                "the refusal was not recorded, so the save on the way out will fold the running layout"
                + " back over the configuration and delete the edit it could not apply (MT-326)");
        }
        finally
        {
            busy(false);
            forget();
            quiet();
        }
    }

    /**
     * The courtesy door - a close that carries no edit - records nothing.
     *
     * OPV-C5's correction: this was recorded for ANY decline, and `autonomyEditorClosed()` reaches the
     * same rebuild on every close.  A session in which nothing was edited would lose its exit capture
     * and be told an edit could not be applied.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testACloseThatCarriesNoEditRecordsNothing() throws Exception
    {
        List<String> heard = listen();

        busy(true);

        try
        {
            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(false, null));

            settle();

            assertFalse(said(heard, "autosetup.log.setupEditNotApplied"),
                "a door carrying no edit was told an edit could not be applied.  Heard: " + heard);

            assertFalse(declined(),
                "a close that edited nothing cost the session its exit capture, which is the whole of"
                + " what OPV-C5 corrected");
        }
        finally
        {
            busy(false);
            forget();
            quiet();
        }
    }

    /**
     * With nothing running, the same call neither complains nor records.
     *
     * The control: without it the two claims above would pass on a rebuild that recorded a decline
     * whatever the railway was doing.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testAnOrdinaryRebuildRecordsNothing() throws Exception
    {
        List<String> heard = listen();

        try
        {
            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(true, null));

            settle();

            assertFalse(said(heard, "autosetup.log.setupEditNotApplied"),
                "an ordinary rebuild, with nothing running, reported that the edit could not be applied."
                + "  Heard: " + heard);

            assertFalse(declined(), "and it recorded a decline that never happened");
        }
        finally
        {
            forget();
            quiet();
        }
    }

    /**
     * Makes `isAutonomyBusy()` answer as asked, without a running railway.
     *
     * `stagingFlowActive` is the other thing that predicate reads, and it is a plain flag - so the state
     * the race produces is reachable without racing anything.
     *
     * @param running what it should answer
     * @throws Exception from the reflection
     */
    private static void busy(boolean running) throws Exception
    {
        Field flag = TrainControlUI.class.getDeclaredField("stagingFlowActive");

        flag.setAccessible(true);
        flag.setBoolean(ui, running);
    }

    /**
     * Whether a decline has been recorded against the exit save.
     *
     * @return the flag
     * @throws Exception from the reflection
     */
    private static boolean declined() throws Exception
    {
        Field flag = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        flag.setAccessible(true);

        return flag.getBoolean(ui);
    }

    /**
     * Clears that record, so one claim cannot decide the next.
     *
     * @throws Exception from the reflection
     */
    private static void forget() throws Exception
    {
        Field flag = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        flag.setAccessible(true);
        flag.setBoolean(ui, false);
    }

    /**
     * Everything the model logs from now on.
     *
     * @return the lines, as they arrive
     */
    private static List<String> listen()
    {
        final List<String> heard = new java.util.concurrent.CopyOnWriteArrayList<>();

        java.util.logging.Handler ear = new java.util.logging.Handler()
        {
            @Override
            public void publish(java.util.logging.LogRecord record)
            {
                heard.add(record.getMessage());
            }

            @Override
            public void flush()
            {
            }

            @Override
            public void close()
            {
            }
        };

        ears.add(ear);

        java.util.logging.Logger.getLogger(MarklinControlStation.class.getName()).addHandler(ear);

        return heard;
    }

    /**
     * The handlers this class has added, so they come off again.
     */
    private static final List<java.util.logging.Handler> ears =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Takes every handler off the model's logger.
     */
    private static void quiet()
    {
        java.util.logging.Logger log =
            java.util.logging.Logger.getLogger(MarklinControlStation.class.getName());

        for (java.util.logging.Handler ear : ears) log.removeHandler(ear);

        ears.clear();
    }

    /**
     * Whether the lines heard include the sentence a message key produces.
     *
     * Matched on the key's own text rather than the key, because `logf` formats before it logs.
     *
     * @param heard the lines
     * @param key the message key
     * @return whether one of them is that sentence
     */
    private static boolean said(List<String> heard, String key)
    {
        String sentence = org.traincontrol.util.I18n.t(key);

        // The first clause of it, because the sentence carries no arguments here but may gain some.
        String enough = sentence.length() > 40 ? sentence.substring(0, 40) : sentence;

        for (String line : heard)
        {
            if (line != null && line.contains(enough)) return true;
        }

        return false;
    }

    /**
     * Lets the event thread finish what this gesture posted.
     *
     * @throws Exception from the event thread
     */
    private static void settle() throws Exception
    {
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> { });
    }
}
