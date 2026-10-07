package regression;

import java.lang.reflect.Field;
import java.util.List;
import javax.swing.SwingUtilities;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
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
        // THROUGH THE ONE DOOR THE FLAG IS SET BY, so the track diagram's notice is cleared with it
        java.lang.reflect.Method newer = TrainControlUI.class.getDeclaredMethod("setupNewerThanTheRailway", boolean.class);

        newer.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                newer.invoke(ui, false);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });
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

    /**
     * A setup edit whose load is declined - a link unpaired, a problem that stops the build - puts nothing back and
     * records nothing over the railway it did not replace (RSA18-A1): put back in the edited setup's names, every train
     * whose copy the edit renamed was taken off that railway and recorded where nothing then kept it, and once the link was
     * paired again the train was on neither and the square it stands on read free.
     *
     * MUTATION: put the trains back whatever the load did, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testALoadDeclinedForAProblemPutsNothingBack() throws Exception
    {
        final org.traincontrol.automationui.TileGraph.TileKey link = new org.traincontrol.automationui.TileGraph.TileKey("1 - Main", 12, 1);

        final org.traincontrol.automationui.TileGraph.TileKey partner = session.getStore().getPortalPartner(link);

        assertNotNull(partner, "precondition: the link at 1 - Main:12,1 is not paired on the snapshot");

        final org.traincontrol.automation.Layout railway = model.getAutoLayout();

        final String copy = "BottomMainPost (northbound, reverse)";

        assertNotNull(railway.getPoint(copy), "precondition: the snapshot's railway has no " + copy);

        final String train = model.getLocList().get(0);

        final boolean[] stood = new boolean[1];

        SwingUtilities.invokeAndWait(() -> stood[0] = railway.moveLocomotive(train, copy, false, true));

        assertTrue(stood[0], "precondition: " + train + " was not stood on " + copy);

        try
        {
            // UNPAIR THIS LINK, and the rebuild the diagram's menu makes: a link left unpaired stops the build
            SwingUtilities.invokeAndWait(() ->
            {
                session.unpairPortal(link);

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();

            assertSame(model.getAutoLayout(), railway, "precondition: the load was not declined, so this is not the case");

            boolean onIt = false;

            for (org.traincontrol.automation.Point point : railway.getPoints())
            {
                if (point.getCurrentLocomotive() != null && train.equals(point.getCurrentLocomotive().getName())) onIt = true;
            }

            assertTrue(onIt, train + " was taken off the railway a declined load left as it was (RSA18-A1)");

            // AND THE OTHER DOOR THAT PUTS THE TRAINS BACK AFTER A LOAD (`carryTheTrainsAcross`), its load declined too
            final java.lang.reflect.Method carry = TrainControlUI.class.getDeclaredMethod("carryTheTrainsAcross",
                Runnable.class);

            carry.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    carry.invoke(ui, (Runnable) () -> { });
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });

            settle();

            boolean stillOnIt = false;

            for (org.traincontrol.automation.Point point : railway.getPoints())
            {
                if (point.getCurrentLocomotive() != null && train.equals(point.getCurrentLocomotive().getName()))
                {
                    stillOnIt = true;
                }
            }

            assertTrue(stillOnIt, train + " was taken off the railway by carryTheTrainsAcross, whose load left it as it"
                + " was (RSA18-A1)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                session.pairPortals(link, partner);

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();
        }
    }

    /**
     * A train the rebuild records where it stands - its turning taken away, no copy facing its way - is drawn there
     * (RSA18-C1): the diagram learned which trains stand on no Point inside the load, before the record, and drew it
     * nowhere.
     *
     * MUTATION: leave the diagram untold after the record, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testATrainRecordedByTheRebuildIsDrawnWhereItStands() throws Exception
    {
        final org.traincontrol.automationui.TileGraph.TileKey square = new org.traincontrol.automationui.TileGraph.TileKey("1 - Main", 20, 13);

        final org.traincontrol.automation.Layout railway = model.getAutoLayout();

        final String turned = "BottomMainB (eastbound, reverse)";

        assertNotNull(railway.getPoint(turned), "precondition: the snapshot's railway has no " + turned);

        final Object mayTurn = session.getPointProperty(square, org.traincontrol.automationui.AutonomyBuilder.CAN_REVERSE);
        final Object mustTurn = session.getPointProperty(square, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE);

        final String train = model.getLocList().get(0);

        final org.traincontrol.gui.DiagramMonitorDriver driver = ui.getDiagramMonitorDriver();

        final boolean[] stood = new boolean[1];

        SwingUtilities.invokeAndWait(() ->
        {
            driver.bind(session);
            driver.setEnabled(true);
            driver.start();

            stood[0] = railway.moveLocomotive(train, turned, false, true);
        });

        assertTrue(stood[0], "precondition: " + train + " was not stood on " + turned);

        try
        {
            // TRAINS NEVER CHANGE DIRECTION HERE, and the rebuild the diagram's menu makes
            SwingUtilities.invokeAndWait(() ->
            {
                session.setPointProperty(square, org.traincontrol.automationui.AutonomyBuilder.CAN_REVERSE, null);
                session.setPointProperty(square, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, null);

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();

            assertEquals(session.getLocomotiveNameAt(square), train, "precondition: " + train + " is not recorded on"
                + " BottomMainB, where it stands: " + session.trainsOnNoPoint());

            org.traincontrol.automationui.TileOverlay drawn = null;

            for (int tries = 0; tries < 50; tries++)
            {
                SwingUtilities.invokeAndWait(() -> { });

                drawn = ui.getDiagramTileRegistry().overlayAt(square);

                if (drawn != null && drawn.isParked()) break;

                Thread.sleep(100);
            }

            assertTrue(drawn != null && drawn.isParked(), train + ", recorded by the rebuild on BottomMainB where no copy"
                + " faces its way, is drawn nowhere (RSA18-C1): " + drawn);
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                session.setPointProperty(square, org.traincontrol.automationui.AutonomyBuilder.CAN_REVERSE, mayTurn);
                session.setPointProperty(square, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, mustTurn);

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();
        }
    }

    /**
     * An edit made while the setup cannot be built - a link unpaired, its load declined - survives the next fold of the
     * railway built before it (RSA19-B1): the railway was not replaced and nothing marked the setup newer, so opening or
     * closing the editor, Export, New Configuration and the exit wrote that railway's settings back over the edit.
     *
     * MUTATION: leave the setup unmarked when the load is declined, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnEditWhileTheLoadIsDeclinedSurvivesTheFold() throws Exception
    {
        final org.traincontrol.automationui.TileGraph.TileKey link = new org.traincontrol.automationui.TileGraph.TileKey("1 - Main", 12, 1);
        final org.traincontrol.automationui.TileGraph.TileKey station = new org.traincontrol.automationui.TileGraph.TileKey("1 - Main", 22, 6);

        final org.traincontrol.automationui.TileGraph.TileKey partner = session.getStore().getPortalPartner(link);

        assertNotNull(partner, "precondition: the link at 1 - Main:12,1 is not paired on the snapshot");

        final org.traincontrol.automation.Layout railway = model.getAutoLayout();

        final Object lengthWas = session.getPointProperty(station, "maxTrainLength");

        final java.lang.reflect.Method capture = TrainControlUI.class.getDeclaredMethod("captureRunningLayout");

        capture.setAccessible(true);

        final String tableWas = session.getGlobal("timetable");

        final int railwayEntriesWas = railway.getTimetable().size();

        try
        {
            // UNPAIR THIS LINK, and the rebuild the diagram's menu makes: declined, a link left unpaired
            SwingUtilities.invokeAndWait(() ->
            {
                session.unpairPortal(link);

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();

            assertSame(model.getAutoLayout(), railway, "precondition: the load was not declined, so this is not the case");

            // THE RUN'S RESULT, on the railway alone: a train moved, and a setting the Auto tab writes to the railway
            final String train = model.getLocList().get(0);

            String to = null;

            for (org.traincontrol.automation.Point point : railway.getPoints())
            {
                if (to == null && point.isDestination() && point.getCurrentLocomotive() == null && point.getSquare() != null
                    && !"1 - Main:22,6".equals(point.getSquare()))
                {
                    to = point.getName();
                }
            }

            assertNotNull(to, "precondition: no free station on the railway");

            final String toName = to;
            final String toSquare = railway.getPoint(to).getSquare();
            final int delayWas = railway.getMaxDelay();
            final boolean[] moved = new boolean[1];

            SwingUtilities.invokeAndWait(() ->
            {
                moved[0] = railway.moveLocomotive(train, toName, false, true);

                try
                {
                    railway.setMaxDelay(delayWas + 3);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertTrue(moved[0], "precondition: " + train + " was not moved to " + toName);

            // AND AN ENTRY THE RUN CAPTURED, on the railway alone (RSA21-C3)
            final int entries = captureAnEntry(railway);

            // AN EDIT WHILE IT WAITS, and the fold every door makes (the editor's opening and closing, Export, the exit)
            final Object[] set = new Object[1];

            SwingUtilities.invokeAndWait(() ->
            {
                session.setPointProperty(station, "maxTrainLength", 9);

                set[0] = session.getPointProperty(station, "maxTrainLength");

                try
                {
                    capture.invoke(ui);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });

            settle();

            assertEquals(String.valueOf(set[0]), "9", "precondition: the edit was not made");

            assertEquals(String.valueOf(session.getPointProperty(station, "maxTrainLength")), "9", "the fold of the"
                + " railway built before the edit took the edit away (RSA19-B1)");

            // AND KEPT WHAT THE RAILWAY OWNS (RSA20-A1): where the run left the train, and the settings it holds
            assertEquals(session.getLocomotiveNameAt(org.traincontrol.automationui.AutonomyCompanionStore.parseTileKey(
                toSquare)), train, "the fold while the setup is newer wrote nothing of where the run left " + train
                + " (RSA20-A1)");

            assertEquals(session.getGlobal("maxDelay"), String.valueOf(delayWas + 3), "the fold while the setup is newer"
                + " wrote nothing of the railway's settings (RSA20-A1)");

            assertEquals(entriesInTheSetup(), entries, "the fold while the setup is newer wrote nothing of the timetable"
                + " the run captured (RSA20-A1, RSA21-C3)");

            // AND THE EXIT'S OWN FOLD, the setup still newer: another setting moved on the railway
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    railway.setMaxDelay(delayWas + 4);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }

                ui.saveState(false, true);
            });

            settle();

            assertEquals(session.getGlobal("maxDelay"), String.valueOf(delayWas + 4), "the exit's fold while the setup is"
                + " newer wrote nothing of the railway's settings (RSA20-A1)");

            assertEquals(String.valueOf(session.getPointProperty(station, "maxTrainLength")), "9", "the exit's fold took"
                + " the edit away (RSA19-B1)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                while (railway.getTimetable().size() > railwayEntriesWas)
                {
                    railway.getTimetable().remove(railway.getTimetable().size() - 1);
                }

                putTheTimetableBack(tableWas);

                session.pairPortals(link, partner);

                session.setPointProperty(station, "maxTrainLength", lengthWas);

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();
        }
    }

    /**
     * A rebuild from the setup - the one every gesture on the track diagram's menu makes - keeps the railway's settings
     * and timetable (RSA20-B1): the Auto tab and a run write them to the railway, and the rebuild from the setup threw
     * away whatever had not been folded.
     *
     * MUTATION: rebuild without folding the railway's settings first, or fold them without the timetable, and this
     * fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testARebuildKeepsTheRailwaysSettings() throws Exception
    {
        final org.traincontrol.automation.Layout railway = model.getAutoLayout();

        final int delayWas = railway.getMaxDelay();

        final String tableWas = session.getGlobal("timetable");

        // AN ENTRY A RUN CAPTURED, on the railway alone (RSA21-C3)
        final int entries = captureAnEntry(railway);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                // AS THE AUTO TAB DOES: on the railway
                railway.setMaxDelay(delayWas + 2);
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }

            ui.rebuildRunningLayoutFromSetup(false, null);
        });

        settle();

        try
        {
            assertTrue(model.getAutoLayout() != railway, "precondition: the railway was not rebuilt");

            assertEquals(model.getAutoLayout().getMaxDelay(), delayWas + 2, "the rebuild from the setup threw away the"
                + " maximum delay set on the railway (RSA20-B1)");

            assertEquals(model.getAutoLayout().getTimetable().size(), entries, "the rebuild from the setup threw away the"
                + " timetable entry the run captured (RSA20-B1, RSA21-C3)");

            assertEquals(entriesInTheSetup(), entries, "the rebuild did not fold the timetable entry the run captured into"
                + " the setup (RSA20-B1, RSA21-C3)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    model.getAutoLayout().setMaxDelay(delayWas);

                    model.getAutoLayout().getTimetable().clear();

                    putTheTimetableBack(tableWas);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();
        }
    }

    /**
     * A setting the railway turned off is off after a rebuild from the setup (RSA21-C1): Simulate and the sequential
     * flag are written only while on, so the compare with the railway as it was built found no key, and the setup kept
     * the setting on.
     *
     * MUTATION: fold only the keys the railway writes now, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testARebuildKeepsASettingTheRailwayTurnedOff() throws Exception
    {
        final String active = session.getStore().getActiveConfiguration();

        final String simulateWas = session.getGlobal("simulate");

        try
        {
            // BUILT WITH IT ON
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    session.setGlobal("simulate", true);
                }
                catch (java.io.IOException e)
                {
                    throw new IllegalStateException(e);
                }

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();

            final org.traincontrol.automation.Layout railway = model.getAutoLayout();

            assertTrue(railway.isSimulate(), "precondition: the railway was not built simulating");

            // TURNED OFF ON THE RAILWAY, as the Auto tab's checkbox does, and a gesture on the diagram's menu rebuilds
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    railway.setSimulate(false);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();

            assertTrue(model.getAutoLayout() != railway, "precondition: the railway was not rebuilt");

            assertFalse(model.getAutoLayout().isSimulate(), "Simulate, turned off on the railway, is back on after a"
                + " rebuild from the setup (RSA21-C1)");

            assertEquals(session.getGlobal("simulate"), null, "the setup still has Simulate on after a rebuild that"
                + " folded the railway's settings (RSA21-C1)");
        }
        finally
        {
            putTheGlobalBack(active, "simulate", simulateWas);
        }
    }

    /**
     * Atomic routes the load turned on itself - a train with no length - are not folded into the setup as though the
     * operator had chosen them (RSA21-C1): the next gesture on the diagram's menu wrote them, and the operator's "off" no
     * longer came back the day the lengths were given (MT-470).
     *
     * MUTATION: leave the railway's settings as built without the value the load forced, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheLoadsOwnAtomicRoutesAreNotFolded() throws Exception
    {
        final String active = session.getStore().getActiveConfiguration();

        final String atomicWas = session.getGlobal("atomicRoutes");

        // A TRAIN WITH NO LENGTH, which is what makes the load turn them on
        String standing = null;

        for (org.traincontrol.automation.Point point : model.getAutoLayout().getPoints())
        {
            if (standing == null && point.getCurrentLocomotive() != null) standing = point.getCurrentLocomotive().getName();
        }

        assertNotNull(standing, "precondition: no train stands on the railway");

        final org.traincontrol.base.Locomotive shortened = model.getLocByName(standing);

        final Integer lengthWas = shortened.getTrainLength();

        try
        {
            shortened.setTrainLength(0);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    session.setGlobal("atomicRoutes", false);
                }
                catch (java.io.IOException e)
                {
                    throw new IllegalStateException(e);
                }

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();

            final org.traincontrol.automation.Layout railway = model.getAutoLayout();

            assertTrue(railway.isAtomicRoutes(), "precondition: the load did not turn atomic routes on for "
                + shortened.getName() + ", which has no length");

            // A GESTURE ON THE DIAGRAM'S MENU, nobody touching atomic routes
            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(false, null));

            settle();

            assertTrue(model.getAutoLayout() != railway, "precondition: the railway was not rebuilt");

            assertEquals(session.getGlobal("atomicRoutes"), "false", "the rebuild folded the atomic routes the load turned"
                + " on itself into the setup, as though the operator had chosen them (RSA21-C1, MT-470)");
        }
        finally
        {
            shortened.setTrainLength(lengthWas);

            putTheGlobalBack(active, "atomicRoutes", atomicWas);
        }
    }

    /**
     * A timetable entry on the railway alone, as a run's capture adds one (RSA21-C3).
     *
     * @param railway the railway
     * @return how many entries it then has
     * @throws Exception from the event thread
     */
    private static int captureAnEntry(final org.traincontrol.automation.Layout railway) throws Exception
    {
        org.traincontrol.base.Locomotive loc = null;

        java.util.List<org.traincontrol.automation.Edge> path = null;

        for (org.traincontrol.automation.Point point : railway.getPoints())
        {
            if (path != null || point.getCurrentLocomotive() == null) continue;

            java.util.List<java.util.List<org.traincontrol.automation.Edge>> paths =
                railway.getPossiblePaths(point.getCurrentLocomotive(), false);

            if (paths != null && !paths.isEmpty())
            {
                loc = point.getCurrentLocomotive();
                path = paths.get(0);
            }
        }

        assertNotNull(path, "precondition: no train on the railway has a path to capture");

        final org.traincontrol.automation.TimetablePath entry = new org.traincontrol.automation.TimetablePath(loc, path, 0L);

        SwingUtilities.invokeAndWait(() -> railway.getTimetable().add(entry));

        return railway.getTimetable().size();
    }

    /** @return how many timetable entries the setup holds */
    private static int entriesInTheSetup()
    {
        String stored = session.getGlobal("timetable");

        return stored == null ? 0 : new org.json.JSONArray(stored).length();
    }

    /**
     * Puts the setup's timetable back as it was.
     *
     * @param was the timetable as it was stored, or null for none
     */
    private static void putTheTimetableBack(String was)
    {
        org.json.JSONObject globals = session.getStore().getConfiguration(session.getStore().getActiveConfiguration())
            .optJSONObject("globals");

        if (globals == null) return;

        if (was == null) globals.remove("timetable");
        else globals.put("timetable", new org.json.JSONArray(was));

        try
        {
            session.saveWithoutReconciling();
        }
        catch (java.io.IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Puts one of the setup's settings back as it was, and the railway with it.
     *
     * @param active the configuration
     * @param key the setting
     * @param was its value as stored, or null for none
     * @throws Exception from the window
     */
    private static void putTheGlobalBack(String active, String key, String was) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            org.json.JSONObject globals = session.getStore().getConfiguration(active).optJSONObject("globals");

            if (globals != null)
            {
                if (was == null) globals.remove(key);
                else if ("true".equals(was) || "false".equals(was)) globals.put(key, Boolean.valueOf(was));
                else globals.put(key, was);
            }

            try
            {
                session.saveWithoutReconciling();
            }
            catch (java.io.IOException e)
            {
                throw new IllegalStateException(e);
            }

            ui.rebuildRunningLayoutFromSetup(false, null);
        });

        settle();
    }

    /**
     * The track diagram says when the setup has changes the railway has not taken (Adam, 2026-10-02: "Add the setup
     * notice, but make it brief"): a link unpaired leaves the setup unable to build, the last railway that built stays
     * loaded, and only the log said so.  The line goes again once a load takes the change.
     *
     * MUTATION: set the flag without telling the strip, or leave the strip's line hidden, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheDiagramSaysWhenTheSetupIsNotApplied() throws Exception
    {
        final org.traincontrol.automationui.TileGraph.TileKey link =
            new org.traincontrol.automationui.TileGraph.TileKey("1 - Main", 12, 1);

        final org.traincontrol.automationui.TileGraph.TileKey partner = session.getStore().getPortalPartner(link);

        assertNotNull(partner, "precondition: the link at 1 - Main:12,1 is not paired on the snapshot");

        Field field = TrainControlUI.class.getDeclaredField("autonomyOverlayToggle");

        field.setAccessible(true);

        final org.traincontrol.gui.AutonomyOverlayToggle strip = (org.traincontrol.gui.AutonomyOverlayToggle) field.get(ui);

        assertNotNull(strip, "precondition: the window has no strip above the track diagram");

        forget();

        assertFalse(strip.isSetupWaitingShown(), "precondition: the strip says the setup is not applied before anything"
            + " was changed");

        try
        {
            // UNPAIR THIS LINK, and the rebuild the diagram's menu makes: declined, the last railway kept
            SwingUtilities.invokeAndWait(() ->
            {
                session.unpairPortal(link);

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();

            assertTrue(declined(), "precondition: the rebuild was not declined");

            assertTrue(strip.isSetupWaitingShown(), "the track diagram does not say the setup has changes the railway has"
                + " not taken, while the last railway that built stays loaded");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                session.pairPortals(link, partner);

                ui.rebuildRunningLayoutFromSetup(false, null);
            });

            settle();
        }

        assertFalse(strip.isSetupWaitingShown(), "the track diagram still says the setup is not applied after a load took"
            + " the change");
    }

    /**
     * The timetable's three doors ask whether anything runs again after their confirmation, which can stay open as long as
     * the operator likes (OB-260, RSA48-C1, RSA48-C2): a run begun behind the question and then a Yes changes nothing, and
     * the refusal is said.
     *
     * Delete Entry asked again from round 80; Clear Timetable and Restart Timetable asked only before, and Clear then
     * emptied the list a run's capture appends to.  Busy is the staging flag, as this class makes it everywhere: the race's
     * outcome without the race.
     *
     * MUTATION: take the second question out of any of the three doors, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheTimetableDoorsAskAgainAfterTheirQuestion() throws Exception
    {
        final org.traincontrol.automation.Layout railway = ui.getModel().getAutoLayout();

        // ITS OWN THREE ENTRIES, a one-edge path each: what the claims before this one leave of the snapshot's timetable
        // is theirs to decide
        final List<org.traincontrol.automation.TimetablePath> was = new java.util.ArrayList<>(railway.getTimetable());

        assertFalse(railway.getEdges().isEmpty(), "precondition: the railway has no edges to make a timetable from");

        final org.traincontrol.automation.Edge edge = railway.getEdges().iterator().next();

        final org.traincontrol.base.Locomotive train = model.getLocByName(model.getLocList().get(0));

        final List<org.traincontrol.automation.TimetablePath> before = new java.util.ArrayList<>();

        for (int i = 0; i < 3; i++)
        {
            before.add(new org.traincontrol.automation.TimetablePath(train, java.util.Arrays.asList(edge), 0));
        }

        railway.setTimetable(before);

        final String refusal = org.traincontrol.util.I18n.t("autolayout.ui.errorWaitForActiveLocomotivesToStop");

        try
        {
            // RESTART: every entry run, so the reset has something to do and asks
            for (org.traincontrol.automation.TimetablePath entry : before) entry.setExecutionTime(1000);

            assertEquals(yesOnceARunBegins(() -> ui.restartTimetable()), refusal, "Restart Timetable did not refuse a"
                + " Yes given after a run began behind its question (RSA48-C1)");

            for (org.traincontrol.automation.TimetablePath entry : before)
            {
                assertEquals(entry.getExecutionTime(), 1000L, "Restart Timetable set back an entry under a run begun"
                    + " behind its question (RSA48-C1)");
            }

            // CLEAR
            assertEquals(yesOnceARunBegins(() -> ui.clearTimetable()), refusal, "Clear Timetable did not refuse a Yes"
                + " given after a run began behind its question (RSA48-C1)");

            assertTheSame(railway.getTimetable(), before, "Clear Timetable emptied the timetable under a run begun behind"
                + " its question (RSA48-C1)");

            // DELETE ENTRY, at the first row of the Auto tab's timetable
            final javax.swing.JTable table = timetableTable();

            long until = System.currentTimeMillis() + 10000;

            while (System.currentTimeMillis() < until && rowsOf(table) == 0)
            {
                Thread.sleep(100);

                SwingUtilities.invokeAndWait(() -> invokeRepaintTimetable());
            }

            assertTrue(rowsOf(table) > 0, "precondition: the timetable's table shows no rows");

            final java.awt.event.MouseEvent onTheFirstRow = new java.awt.event.MouseEvent(table,
                java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 2, table.getRowHeight() / 2, 1,
                false);

            assertEquals(yesOnceARunBegins(() -> ui.deleteTimetableEntry(onTheFirstRow)), refusal, "Delete Entry did not"
                + " refuse a Yes given after a run began behind its question (OB-260, RSA48-C2)");

            // ITS REMOVE RUNS ON A WORKER: given the time to, had it been sent
            Thread.sleep(1000);

            assertTheSame(railway.getTimetable(), before, "Delete Entry removed an entry under a run begun behind its"
                + " question (OB-260, RSA48-C2)");
        }
        finally
        {
            busy(false);

            railway.setTimetable(was);
        }
    }

    /**
     * Opens a door on the event thread, answers its first question Yes once the railway is made busy behind it, and
     * reads and closes the message that follows.
     *
     * @param door the door
     * @return the message after the Yes, or null when none came
     * @throws Exception from the wait
     */
    private static String yesOnceARunBegins(Runnable door) throws Exception
    {
        SwingUtilities.invokeLater(door);

        final boolean[] answered = new boolean[1];

        long until = System.currentTimeMillis() + 10000;

        while (!answered[0] && System.currentTimeMillis() < until)
        {
            Thread.sleep(100);

            SwingUtilities.invokeAndWait(() ->
            {
                javax.swing.JOptionPane pane = aPaneShowing(true);

                if (pane == null) return;

                try
                {
                    busy(true);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }

                pane.setValue(pane.getOptions()[0]);

                SwingUtilities.getWindowAncestor(pane).dispose();

                answered[0] = true;
            });
        }

        assertTrue(answered[0], "precondition: the door asked nothing");

        final String[] said = new String[1];

        until = System.currentTimeMillis() + 5000;

        while (said[0] == null && System.currentTimeMillis() < until)
        {
            Thread.sleep(100);

            SwingUtilities.invokeAndWait(() ->
            {
                javax.swing.JOptionPane pane = aPaneShowing(false);

                if (pane == null) return;

                said[0] = String.valueOf(pane.getMessage());

                pane.setValue(javax.swing.JOptionPane.OK_OPTION);

                SwingUtilities.getWindowAncestor(pane).dispose();
            });
        }

        busy(false);

        return said[0];
    }

    /**
     * The option pane in a showing dialog: a question (one with options) or a message (none).  The caller answers it and
     * then closes its window, in that order, so the door reads the answer.
     *
     * @param question whether a question is wanted
     * @return the pane, or null when no such dialog is showing
     */
    private static javax.swing.JOptionPane aPaneShowing(boolean question)
    {
        for (java.awt.Window window : java.awt.Window.getWindows())
        {
            if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

            javax.swing.JOptionPane pane = paneIn((java.awt.Container) window);

            if (pane == null || (pane.getOptions() != null) != question) continue;

            return pane;
        }

        return null;
    }

    /**
     * The option pane inside a container, at any depth.
     *
     * @param container where to look
     * @return the pane, or null
     */
    private static javax.swing.JOptionPane paneIn(java.awt.Container container)
    {
        for (java.awt.Component c : container.getComponents())
        {
            if (c instanceof javax.swing.JOptionPane) return (javax.swing.JOptionPane) c;

            if (c instanceof java.awt.Container)
            {
                javax.swing.JOptionPane inner = paneIn((java.awt.Container) c);

                if (inner != null) return inner;
            }
        }

        return null;
    }

    /**
     * That the timetable holds exactly these entries, the same objects in the same order.
     *
     * @param now the timetable
     * @param was the entries it held
     * @param message what it means if not
     */
    private static void assertTheSame(List<?> now, List<?> was, String message)
    {
        assertEquals(now.size(), was.size(), message);

        for (int i = 0; i < was.size(); i++) assertSame(now.get(i), was.get(i), message);
    }

    /**
     * The Auto tab's timetable table.
     *
     * @return the table
     * @throws Exception from the reflection
     */
    private static javax.swing.JTable timetableTable() throws Exception
    {
        Field table = TrainControlUI.class.getDeclaredField("timetable");

        table.setAccessible(true);

        return (javax.swing.JTable) table.get(ui);
    }

    /**
     * How many rows a table shows, read on the event thread.
     *
     * @param table the table
     * @return its rows
     * @throws Exception from the event thread
     */
    private static int rowsOf(javax.swing.JTable table) throws Exception
    {
        final int[] rows = new int[1];

        SwingUtilities.invokeAndWait(() -> rows[0] = table.getRowCount());

        return rows[0];
    }

    /**
     * Asks the window to fill its timetable's table, as a path's start and end do.
     */
    private static void invokeRepaintTimetable()
    {
        try
        {
            java.lang.reflect.Method repaint = TrainControlUI.class.getDeclaredMethod("repaintTimetable");

            repaint.setAccessible(true);

            repaint.invoke(ui);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
