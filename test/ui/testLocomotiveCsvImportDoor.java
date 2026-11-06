package ui;

import java.awt.Container;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;

/**
 * Locomotives > Import from CSV (FR-049), driven as the operator drives it: the menu item pressed, the file chosen in the
 * chooser, the one question answered, and the message read.  The reading and merging are `core.testLocomotiveCsvImport`.
 */
public class testLocomotiveCsvImportDoor
{
    private static final String HEADER = "Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,Start Year,End Year,RailwayName,Notes\n";

    private static final String IMPORT = I18n.t("loc.ui.importCsvButton");
    private static final String CANCEL = I18n.t("ui.cancel");

    /** What the door showed and did. */
    private static final class Seen
    {
        volatile boolean chose;
        volatile String question;
        final List<String> said = Collections.synchronizedList(new ArrayList<>());
        final List<support.PopupShape> saidShapes = Collections.synchronizedList(new ArrayList<>());
    }

    /** The shared shape of a claim: a window, a file, an answer, and the door pressed. */
    private interface Claim
    {
        void check(TrainControlUI ui, MarklinControlStation model) throws Exception;
    }

    /**
     * Presses the item with the file chosen (null cancels the chooser) and the question answered with `answer`, and
     * waits for `until`.
     */
    private static Seen press(TrainControlUI ui, File file, String answer, java.util.function.Predicate<Seen> until)
        throws Exception
    {
        final Seen seen = new Seen();
        final boolean[] going = {true};
        final java.util.Set<Object> handled = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        Thread answering = new Thread(() -> support.DialogWatch.until(() -> going[0], 150, dialog ->
        {
            JFileChooser chooser = support.Components.inside(dialog.getContentPane(), JFileChooser.class);

            if (chooser != null)
            {
                if (!handled.add(chooser)) return;

                seen.chose = true;

                SwingUtilities.invokeLater(() ->
                {
                    if (file == null)
                    {
                        chooser.cancelSelection();
                    }
                    else
                    {
                        chooser.setSelectedFile(file);
                        chooser.approveSelection();
                    }
                });

                return;
            }

            JOptionPane pane = support.Components.inside(dialog.getContentPane(), JOptionPane.class);

            if (pane == null || !handled.add(pane)) return;

            support.PopupShape shape = support.PopupShape.of(pane);

            if (I18n.t("loc.ui.importCsvTitle").equals(dialog.getTitle()))
            {
                seen.question = shape.sentence;

                SwingUtilities.invokeLater(() ->
                {
                    for (Object option : pane.getOptions())
                    {
                        if (String.valueOf(option).equals(answer)) pane.setValue(option);
                    }
                });

                return;
            }

            seen.saidShapes.add(shape);
            seen.said.add(shape.sentence);

            SwingUtilities.invokeLater(() -> pane.setValue(Integer.valueOf(JOptionPane.OK_OPTION)));
        }), "locomotive import answerer");

        answering.setDaemon(true);
        answering.start();

        try
        {
            java.lang.reflect.Field item = TrainControlUI.class.getDeclaredField("importLocsFromCSVMenuItem");

            item.setAccessible(true);

            final JMenuItem door = (JMenuItem) item.get(ui);

            assertNotNull(door, "the Locomotives menu has no Import from CSV item");

            SwingUtilities.invokeLater(door::doClick);

            for (long end = System.currentTimeMillis() + 60000; !until.test(seen) && System.currentTimeMillis() < end; )
            {
                Thread.sleep(100);
            }

            // WHATEVER ELSE IT POSTS
            Thread.sleep(500);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });
        }
        finally
        {
            going[0] = false;
        }

        return seen;
    }

    private static void inAWindow(Claim claim) throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        final TrainControlUI[] ui = new TrainControlUI[1];
        final support.SandboxedWindow sandboxed = new support.SandboxedWindow();
        try
        {
            sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"))).simulated();

            ui[0] = sandboxed.hold(openTheWindow());

            claim.check(ui[0], (MarklinControlStation) ui[0].getModel());
        }
        finally
        {
            sandboxed.close();
        }
    }

    private static TrainControlUI openTheWindow() throws Exception
    {
        MarklinControlStation model = support.TestStation.simulatedInDebug();

        final TrainControlUI[] made = new TrainControlUI[1];

        SwingUtilities.invokeAndWait(() -> made[0] = new TrainControlUI());

        made[0].setViewListener(model, new CountDownLatch(1));

        final CountDownLatch settled = new CountDownLatch(1);

        made[0].whenTilesSettled(() -> settled.countDown());

        settled.await(30, TimeUnit.SECONDS);

        for (int turn = 0; turn < 4; turn++) SwingUtilities.invokeAndWait(() -> { });

        return made[0];
    }

    private static File write(String text) throws Exception
    {
        File file = File.createTempFile("tc-locs", ".csv");

        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));

        return file;
    }

    private static Map<String, String> everyLocomotive(MarklinControlStation model)
    {
        Map<String, String> all = new HashMap<>();

        for (Locomotive l : model.getLocomotives())
        {
            all.put(l.getName(), l.getDecoderType() + "|" + l.getAddress() + "|" + l.getPreferredSpeed() + "|"
                + l.getTotalRuntime() + "|" + l.getNotes());
        }

        return all;
    }

    private static String words(String text)
    {
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * The menu item opens the file chooser; a chooser canceled changes nothing and asks nothing.
     *
     * MUTATION: leave the item out of the menu, or read the database before the chooser is answered, and this fails.
     */
    @Test
    public void testTheMenuItemOpensTheChooserAndCancelingChangesNothing() throws Exception
    {
        inAWindow((ui, model) ->
        {
            Map<String, String> before = everyLocomotive(model);

            Seen seen = press(ui, null, IMPORT, s -> s.chose);

            assertTrue(seen.chose, "Locomotives > Import from CSV did not open the file chooser");
            assertNull(seen.question, "a canceled chooser went on to ask a question");
            assertTrue(seen.said.isEmpty(), "a canceled chooser said something: " + seen.said);
            assertEquals(everyLocomotive(model), before);
        });
    }

    /**
     * The question counts the new, the existing and the unreadable; Cancel changes nothing.
     *
     * MUTATION: apply the file before the answer, or count the wrong rows, and this fails.
     */
    @Test
    public void testTheQuestionCountsAndCancelChangesNothing() throws Exception
    {
        inAWindow((ui, model) ->
        {
            String there = model.getLocList().get(0);
            File file = write(HEADER + "FR049 door new,,MFX,88,0,0:00:00,0,0,,\n" + there + ",,MFX,88,0,0:00:00,0,0,,\n"
                + "FR049 door bad,,MFX,xx,0,0:00:00,0,0,,\n");

            try
            {
                Map<String, String> before = everyLocomotive(model);

                Seen seen = press(ui, file, CANCEL, s -> s.question != null);

                assertNotNull(seen.question, "the door did not ask before changing anything");
                assertEquals(words(seen.question), words(I18n.f("loc.ui.confirmImportCsv", 1, 1) + "\n\n"
                    + I18n.f("loc.ui.confirmImportCsvUnreadable", 1)));
                assertEquals(everyLocomotive(model), before, "Cancel changed the database");
                assertNull(model.getLocByName("FR049 door new"));
                assertTrue(seen.said.isEmpty(), "Cancel went on to say something: " + seen.said);
            }
            finally
            {
                file.delete();
            }
        });
    }

    /**
     * Import adds the new name with no key, merges the existing name without moving its address, and names the line of
     * the row it could not read.
     *
     * MUTATION: map the new locomotive to a key, let the file move the address, or name the wrong line, and this fails.
     */
    @Test
    public void testImportAddsMergesAndNamesTheSkippedLine() throws Exception
    {
        inAWindow((ui, model) ->
        {
            Locomotive there = model.getLocByName(model.getLocList().get(0));
            int address = there.getAddress();
            long runtime = there.getTotalRuntime();
            long fileRuntime = runtime + 7200000L;
            File file = write(HEADER + "FR049 door new,Page 1 Key 1;Page 2 Key 2,DCC,88,30,2:00:00,0,0,,\n"
                + org.traincontrol.util.Util.escapeCsv(there.getName()) + ",,MM2," + (address + 1) + ",0,"
                + org.traincontrol.util.Conversion.convertSecondsToHMmSs(fileRuntime) + ",0,0,,\n"
                + "FR049 door bad,,MFX,xx,0,0:00:00,0,0,,\n");

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty());

                assertEquals(seen.said.size(), 1, "the door said: " + seen.said);

                String said = words(seen.said.get(0));

                assertTrue(said.startsWith(words(I18n.f("loc.ui.infoImportCsvDone", 1, 1))), said);
                assertEquals(seen.saidShapes.get(0).details, Collections.singletonList(I18n.f("loc.ui.importCsvSkippedLine", 4)),
                    "the skipped row's line is not named");

                Locomotive added = model.getLocByName("FR049 door new");

                assertNotNull(added, "the new name was not added");
                assertEquals(added.getAddress(), 88);
                assertEquals(added.getTotalRuntime(), 7200000L);
                assertTrue(ui.getAllLocButtonMappings(added).isEmpty(), "the new locomotive was put on a key: "
                    + ui.getAllLocButtonMappings(added));

                assertEquals(there.getAddress(), address, "the file moved an existing locomotive's address");
                assertEquals(there.getTotalRuntime(), fileRuntime / 1000 * 1000, "the existing locomotive did not take the file's larger runtime");
                assertNull(model.getLocByName("FR049 door bad"));

                model.deleteLoc("FR049 door new");
            }
            finally
            {
                file.delete();
            }
        });
    }

    /**
     * Refused while autonomy runs, before the chooser opens; nothing changes.
     *
     * MUTATION: take the refusal out of the door, and this fails.
     */
    @Test
    public void testRefusedWhileAutonomyRuns() throws Exception
    {
        inAWindow((ui, model) ->
        {
            Map<String, String> before = everyLocomotive(model);

            model.getAutoLayout().setStagingInProgress(true);

            try
            {
                assertTrue(model.isAutonomyRunning(), "precondition: no run is under way");

                Seen seen = press(ui, null, IMPORT, s -> !s.said.isEmpty());

                assertEquals(seen.said, Collections.singletonList(I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning")));
                assertFalse(seen.chose, "the chooser opened while autonomy ran");
                assertEquals(everyLocomotive(model), before);
            }
            finally
            {
                model.getAutoLayout().setStagingInProgress(false);
            }
        });
    }

    /**
     * A locomotive a running route drives is not merged into: the door refuses before it asks, naming the route.
     *
     * MUTATION: take the route check out of the door, and this fails.
     */
    @Test
    public void testRefusedWhileARouteDrivesAMergedLocomotive() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final String loc = "FR049 driven";
            final String route = "FR049 route";

            model.newMM2Locomotive(loc, 63);

            List<RouteCommand> commands = new ArrayList<>();
            RouteCommand fires = RouteCommand.RouteCommandFunction(loc, 1, true);

            fires.setDelay(20000);
            commands.add(fires);

            model.newRoute(route, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

            File file = write(HEADER + loc + ",,MM2,63,50,9:00:00,0,0,,Merged\n");

            try
            {
                new Thread(() -> model.execRoute(route)).start();

                for (long end = System.currentTimeMillis() + 5000; !model.getRoute(route).isExecuting()
                    && System.currentTimeMillis() < end; )
                {
                    Thread.sleep(20);
                }

                assertNotNull(model.runningRouteDriving(loc), "precondition: the route is not driving the locomotive");

                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty());

                assertEquals(seen.said, Collections.singletonList(I18n.f("loc.ui.errorLocomotiveDrivenByARunningRoute", loc, route)));
                assertNull(seen.question, "the door asked about a locomotive a running route drives");
                assertEquals(model.getLocByName(loc).getPreferredSpeed(), 0, "the file merged into a driven locomotive");
                assertEquals(model.getLocByName(loc).getTotalRuntime(), 0L);
            }
            finally
            {
                file.delete();
                model.deleteRoute(route);
                model.deleteLoc(loc);
            }
        });
    }

    /**
     * A file that is not a locomotive export is said so and changes nothing.
     *
     * MUTATION: read any text as an export, and this fails.
     */
    @Test
    public void testAFileThatIsNotAnExportIsSaidSo() throws Exception
    {
        inAWindow((ui, model) ->
        {
            Map<String, String> before = everyLocomotive(model);
            File file = write("{\"routes\": []}");

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty());

                assertEquals(seen.said, Collections.singletonList(I18n.t("loc.ui.errorImportCsvNotAnExport")));
                assertNull(seen.question);
                assertEquals(everyLocomotive(model), before);
            }
            finally
            {
                file.delete();
            }
        });
    }
}
