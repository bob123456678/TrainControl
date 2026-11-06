package regression;

import java.awt.Container;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final String HEADER11 = "Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,Start Year,End Year,RailwayName,Notes,LastRun\n";

    private static final String IMPORT = I18n.t("loc.ui.importCsvButton");
    private static final String CANCEL = I18n.t("ui.cancel");

    /** What the door showed and did. */
    private static final class Seen
    {
        volatile boolean chose;
        volatile String question;
        volatile List<String> questionDetails = Collections.emptyList();
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
        return press(ui, file, answer, until, null);
    }

    /** The same, running `whenAsked` as the question is on screen, before it is answered. */
    private static Seen press(TrainControlUI ui, File file, String answer, java.util.function.Predicate<Seen> until,
        Runnable whenAsked) throws Exception
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
                seen.questionDetails = shape.details;

                if (whenAsked != null) whenAsked.run();

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

        support.SandboxedWindow sandboxed = new support.SandboxedWindow();

        try
        {
            sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"))).simulated();

            TrainControlUI window = sandboxed.hold(testTheImportDoorReadsAnOldFile.openTheWindow());

            claim.check(window, (MarklinControlStation) window.getModel());
        }
        finally
        {
            sandboxed.close();
        }
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
     * The question counts the new, the existing, those that change address or decoder, and the unreadable; Cancel changes
     * nothing.
     *
     * MUTATION: apply the file before the answer, or count the wrong rows, and this fails.
     */
    @Test
    public void testTheQuestionCountsAndCancelChangesNothing() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final String there = "FR049 door old";

            model.newMM2Locomotive(there, 20);

            File file = write(HEADER + "FR049 door new,,MFX,12345,0,0:00:00,0,0,,\n" + there + ",,DCC,21,0,0:00:00,0,0,,\n"
                + "FR049 door bad,,MFX,xx,0,0:00:00,0,0,,\n");

            try
            {
                Map<String, String> before = everyLocomotive(model);

                Seen seen = press(ui, file, CANCEL, s -> s.question != null);

                assertNotNull(seen.question, "the door did not ask before changing anything");
                assertEquals(words(seen.question), words(I18n.f("loc.ui.confirmImportCsv", 1, 1) + "\n\n"
                    + I18n.f("loc.ui.confirmImportCsvReaddress", 1) + "\n\n" + I18n.f("loc.ui.confirmImportCsvUnreadable", 1)));
                assertEquals(seen.questionDetails, Collections.singletonList(I18n.f("loc.ui.importCsvChangeLine", there, "MM2 20",
                    "DCC 21")), "the question does not list the locomotive that changes address, old and new");
                assertEquals(everyLocomotive(model), before, "Cancel changed the database");
                assertNull(model.getLocByName("FR049 door new"));
                assertTrue(seen.said.isEmpty(), "Cancel went on to say something: " + seen.said);
            }
            finally
            {
                file.delete();
                model.deleteLoc(there);
            }
        });
    }

    /**
     * Import adds the new name with no key, merges the existing name (its address and decoder follow the file, its runtime
     * follows a later last run day), and names the line of the row it could not read.
     *
     * MUTATION: map the new locomotive to a key, leave the address alone, or name the wrong line, and this fails.
     */
    @Test
    public void testImportAddsMergesAndNamesTheSkippedLine() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final String there = "FR049 door old";
            Locomotive old = model.newMM2Locomotive(there, 20);

            old.getHistoricalOperatingTime().put("2024-01-01", 3600000L);

            File file = write(HEADER11 + "FR049 door new,Page 1 Key 1;Page 2 Key 2,DCC,88,30,2:00:00,0,0,,,2024-02-02\n"
                + there + ",,DCC,21,0,3:00:00,0,0,,,2024-06-06\n"
                + "FR049 door bad,,MFX,xx,0,0:00:00,0,0,,,\n");

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty());

                assertEquals(seen.said.size(), 1, "the door said: " + seen.said);

                String said = words(seen.said.get(0));

                assertTrue(said.startsWith(words(I18n.f("loc.ui.infoImportCsvDone", 1, 1))), said);
                assertTrue(said.contains(words(I18n.f("loc.ui.infoImportCsvReaddressed", 1))), said);
                assertEquals(seen.saidShapes.get(0).details, Collections.singletonList(I18n.f("loc.ui.importCsvSkippedLine", 4) + ": "
                    + I18n.t("loc.ui.importCsvReasonUnreadable")), "the skipped row's line and reason are not named");

                Locomotive added = model.getLocByName("FR049 door new");

                assertNotNull(added, "the new name was not added");
                assertEquals(added.getAddress(), 88);
                assertEquals(added.getTotalRuntime(), 7200000L);
                assertTrue(ui.getAllLocButtonMappings(added).isEmpty(), "the new locomotive was put on a key: "
                    + ui.getAllLocButtonMappings(added));

                assertEquals(old.getAddress(), 21, "the file did not move an existing locomotive's address");
                assertEquals(old.getDecoderType(), Locomotive.decoderType.DCC);
                assertEquals(old.getTotalRuntime(), 10800000L, "the existing locomotive did not take the file's larger runtime");
                assertNull(model.getLocByName("FR049 door bad"));

                model.deleteLoc("FR049 door new");
            }
            finally
            {
                file.delete();
                model.deleteLoc(there);
            }
        });
    }

    /**
     * A train that is moving, with the power on, is not re-addressed: the door says so before it asks, and nothing changes.
     *
     * MUTATION: take the moving check out of the door, and this fails.
     */
    @Test
    public void testAMovingTrainIsNotReaddressed() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final String name = "FR049 door rolling";
            Locomotive rolling = model.newMM2Locomotive(name, 30);

            File file = write(HEADER + name + ",,DCC,31,0,0:00:00,0,0,,\n");

            try
            {
                model.go();
                model.waitForPowerState(true);
                rolling.setSpeed(30);
                rolling.waitForSpeedAtOrAbove(1);

                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty());

                assertEquals(seen.said, Collections.singletonList(I18n.f("loc.ui.errorImportCsvMoving", name)));
                assertNull(seen.question, "the door asked about a train it must not re-address");
                assertEquals(rolling.getAddress(), 30);
                assertEquals(rolling.getDecoderType(), Locomotive.decoderType.MM2);
            }
            finally
            {
                rolling.setSpeed(0);
                rolling.waitForSpeedBelow(1);
                model.stop();
                model.waitForPowerState(false);
                file.delete();
                model.deleteLoc(name);
            }
        });
    }

    /**
     * A run that begins while the question is on screen refuses the import when the answer comes: the question is modal,
     * and the gates are asked again after it.
     *
     * MUTATION: take the second gate out of the door, and this fails.
     */
    @Test
    public void testAnAutonomyRunBehindTheQuestionStopsTheImport() throws Exception
    {
        inAWindow((ui, model) ->
        {
            File file = write(HEADER + "FR049 door gate,,MFX,12346,0,0:00:00,0,0,,\n");

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty(), () -> model.getAutoLayout().setStagingInProgress(true));

                assertNotNull(seen.question, "precondition: the door did not ask");
                assertEquals(seen.said, Collections.singletonList(I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning")));
                assertNull(model.getLocByName("FR049 door gate"), "the import went ahead behind a run");
            }
            finally
            {
                model.getAutoLayout().setStagingInProgress(false);
                file.delete();
            }
        });
    }

    /**
     * The locomotive list the operator picks from shows a locomotive the import added, and the message says when a new
     * locomotive came in without running time because the file gave no last run day.
     *
     * MUTATION: leave the list unrefreshed, or leave the no-runtime sentence out, and this fails.
     */
    @Test
    public void testTheListShowsTheNewLocomotiveAndTheMessageSaysItHasNoRuntime() throws Exception
    {
        inAWindow((ui, model) ->
        {
            File file = write(HEADER11 + "FR049 door listed,,MFX,12347,0,3:00:00,0,0,,,\n");

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty());

                assertEquals(seen.said.size(), 1, "the door said: " + seen.said);
                assertTrue(words(seen.said.get(0)).contains(words(I18n.f("loc.ui.infoImportCsvNoRuntime", 1))), seen.said.get(0));

                boolean listed = false;

                for (java.awt.Component c : ui.getLocSelector().getMainLocList().getComponents())
                {
                    listed |= ((org.traincontrol.gui.locomotive.LocomotiveSelectorItem) c).getText().contains("FR049 door listed");
                }

                assertTrue(listed, "the locomotive list does not show the locomotive the import added");

                model.deleteLoc("FR049 door listed");
            }
            finally
            {
                file.delete();
            }
        });
    }

    /**
     * The question lists the locomotives that change address or decoder, ten lines and then "and N more".
     *
     * MUTATION: list them all, or none, and this fails.
     */
    @Test
    public void testTheQuestionListsTenChangesAndThenHowManyMore() throws Exception
    {
        inAWindow((ui, model) ->
        {
            StringBuilder rows = new StringBuilder(HEADER);

            for (int i = 1; i <= 12; i++)
            {
                String name = String.format("FR049 door chg %02d", i);

                model.newMM2Locomotive(name, i);
                rows.append(name).append(",,DCC,").append(1000 + i).append(",0,0:00:00,0,0,,\n");
            }

            File file = write(rows.toString());

            try
            {
                Seen seen = press(ui, file, CANCEL, s -> s.question != null);

                assertEquals(seen.questionDetails.size(), 11, "the list is not ten lines and a count: " + seen.questionDetails);
                assertEquals(seen.questionDetails.get(10), I18n.f("loc.ui.importCsvMoreLine", 2));
                assertEquals(seen.questionDetails.get(0), I18n.f("loc.ui.importCsvChangeLine", "FR049 door chg 01", "MM2 1", "DCC 1001"));
            }
            finally
            {
                file.delete();

                for (int i = 1; i <= 12; i++) model.deleteLoc(String.format("FR049 door chg %02d", i));
            }
        });
    }

    /**
     * An address change that would put a locomotive on the address of a train standing on the layout is left out and
     * counted: the autonomy sweep would take the standing train off the layout.
     *
     * MUTATION: let the file give a locomotive the address of a standing train, and this fails.
     */
    @Test
    public void testAnAddressOfAStandingTrainIsNotTaken() throws Exception
    {
        inAWindow((ui, model) ->
        {
            Locomotive standing = null;

            for (org.traincontrol.automation.Point p : model.getAutoLayout().getPoints())
            {
                Locomotive l = p.getCurrentLocomotive();

                if (standing == null && l != null && l.getDecoderType() != Locomotive.decoderType.MULTI_UNIT && !l.hasLinkedLocomotives())
                {
                    standing = l;
                }
            }

            assertNotNull(standing, "precondition: no train stands on this layout");

            Locomotive other = model.newMM2Locomotive("FR049 door elsewhere", 77);
            File file = write(HEADER + "FR049 door elsewhere,," + standing.getDecoderType().name() + "," + standing.getAddress()
                + ",0,0:00:00,0,0,,\n");

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty());

                assertEquals(other.getAddress(), 77, "a locomotive was given the address of a standing train");
                assertTrue(words(seen.said.get(0)).contains(words(I18n.f("loc.ui.infoImportCsvReaddressRefused", 1))), seen.said.get(0));
                assertNotNull(model.getAutoLayout().getLocomotiveLocation(standing), "the standing train left the layout");
            }
            finally
            {
                file.delete();
                model.deleteLoc("FR049 door elsewhere");
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
