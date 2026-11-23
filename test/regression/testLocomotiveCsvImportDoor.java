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
import support.Reflect;

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

    /** The import question while it shows, for a claim's `whenAsked` to read and press. */
    private static volatile JOptionPane asking;

    /** What the door showed and did. */
    private static final class Seen
    {
        volatile boolean chose;
        volatile String question;
        /** The question was answered, after `whenAsked` ran. */
        volatile boolean answered;
        volatile List<String> questionDetails = Collections.emptyList();
        /** Every dialog's title, in the order they showed. */
        final List<String> titles = Collections.synchronizedList(new ArrayList<>());
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
        return press(ui, "importLocsFromCSVMenuItem", file, answer, until, whenAsked);
    }

    /** The same, for the Locomotives menu item held in the field `doorField`. */
    private static Seen press(TrainControlUI ui, String doorField, File file, String answer,
        java.util.function.Predicate<Seen> until, Runnable whenAsked) throws Exception
    {
        final Seen seen = new Seen();
        final boolean[] going = {true};
        final java.util.Set<Object> handled = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        // Stopped as it wakes, so a watcher left from an earlier press never answers the next press's question
        Thread answering = new Thread(() -> support.DialogWatch.untilStillGoing(() -> going[0], 150, dialog ->
        {
            if (handled.add(dialog)) seen.titles.add(dialog.getTitle());

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
                asking = pane;

                if (whenAsked != null) whenAsked.run();

                seen.answered = true;

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
            final JMenuItem door = (JMenuItem) Reflect.get(TrainControlUI.class, ui, doorField);

            assertNotNull(door, "the Locomotives menu has no " + doorField + " item");

            SwingUtilities.invokeLater(door::doClick);

            support.Components.waitFor(() -> until.test(seen), 60000);

            // WHATEVER ELSE IT POSTS
            support.Components.settle();
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
                    + I18n.f("loc.ui.importCsvReasonCells", I18n.f("loc.ui.importCsvBadCell", "Address", "xx"))),
                    "the skipped row's line and reason are not named");

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
     * A row with an unusable address, speed or year is skipped whole, and its line names each wrong cell by the heading the
     * file has and the value in it.
     *
     * MUTATION: name no cell, name only the first wrong one, or give the heading in lower case, and this fails.
     */
    @Test
    public void testASkippedRowNamesTheCellsThatMadeItUnreadable() throws Exception
    {
        List<String> lines = detailsOfImporting("FR049 door year,,MFX,50,0,0:00:00,c. 1950,0,,,\n"
            + "FR049 door two,,MFX,51,150,0:00:00,0,19x0,,,\n" + "FR049 door fine,,MFX,52,0,0:00:00,0,0,,,\n"
            + "FR049 door kept,,DCC,21,101,0:00:00,0,0,,,\n");

        assertEquals(lines, java.util.Arrays.asList(
            I18n.f("loc.ui.importCsvSkippedLine", 2) + ": " + I18n.f("loc.ui.importCsvReasonCells",
                I18n.f("loc.ui.importCsvBadCell", "Start Year", "c. 1950")),
            I18n.f("loc.ui.importCsvSkippedLine", 3) + ": " + I18n.f("loc.ui.importCsvReasonCells",
                I18n.f("loc.ui.importCsvBadCell", "PreferredSpeed", "150") + ", "
                + I18n.f("loc.ui.importCsvBadCell", "End Year", "19x0")),
            I18n.f("loc.ui.importCsvSkippedLine", 5) + ": " + I18n.f("loc.ui.importCsvReasonCells",
                I18n.f("loc.ui.importCsvBadCell", "PreferredSpeed", "101"))), "the skipped rows do not name their cells");
    }

    /**
     * A file whose only row cannot be used says there is nothing to import and how many rows could not be read, with the
     * skipped line and its wrong cell listed under it (MT-991 step 4).
     *
     * MUTATION: leave the list out of the nothing-to-import message, leave the cell out of it, or count 0 skipped rows,
     * and this fails.
     */
    @Test
    public void testNothingToImportNamesTheSkippedCell() throws Exception
    {
        support.PopupShape said = shapeOfImporting("FR049 door only,,MFX,50,0,0:00:00,c. 1950,0,,,\n");

        assertEquals(words(said.sentence), words(I18n.f("loc.ui.infoImportCsvNothing", 1)), "the nothing-to-import"
            + " message does not say so, or miscounts the skipped row");
        assertEquals(said.details, Collections.singletonList(
            I18n.f("loc.ui.importCsvSkippedLine", 2) + ": " + I18n.f("loc.ui.importCsvReasonCells",
                I18n.f("loc.ui.importCsvBadCell", "Start Year", "c. 1950"))), "the nothing-to-import message does not name the cell");
    }

    /**
     * MT-991 steps 1 to 3 as written: the file Export to CSV saves, its first row's Start Year made "c. 1950", imported
     * again.  The summary counts one skipped row and names line 2 with its cell, and every locomotive is as it was.
     *
     * MUTATION: in `LocomotiveCsvDoor.tell`, leave out the `infoImportCsvSkipped` sentence, and this fails.
     */
    @Test
    public void testAnExportWithOneBadYearImportsTheRestAndNamesItsLine() throws Exception
    {
        inAWindow((ui, model) ->
        {
            File file = new File(System.getProperty("java.io.tmpdir"), "tc-export-" + System.nanoTime() + ".csv");

            try
            {
                Map<String, String> before = everyLocomotive(model);

                List<String> lines = new ArrayList<>(Files.readAllLines(exportThroughTheMenu(ui, file).toPath(),
                    StandardCharsets.UTF_8));

                assertTrue(lines.size() >= 3 && !lines.get(1).contains("\""), "precondition: the export has no plain"
                    + " first row and a second to merge: " + lines.size() + " lines");

                int year = java.util.Arrays.asList(lines.get(0).split(",")).indexOf("Start Year");
                String[] first = lines.get(1).split(",", -1);

                first[year] = "c. 1950";
                lines.set(1, String.join(",", first));
                Files.write(file.toPath(), lines, StandardCharsets.UTF_8);

                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty());

                assertEquals(seen.said.size(), 1, "the door said: " + seen.said);
                assertTrue(words(seen.said.get(0)).contains(words(I18n.f("loc.ui.infoImportCsvSkipped", 1))),
                    "the summary does not say one row was skipped: " + seen.said.get(0));
                assertTrue(seen.saidShapes.get(0).details.contains(I18n.f("loc.ui.importCsvSkippedLine", 2) + ": "
                    + I18n.f("loc.ui.importCsvReasonCells", I18n.f("loc.ui.importCsvBadCell", "Start Year", "c. 1950"))),
                    "the summary does not name line 2 and its Start Year: " + seen.saidShapes.get(0).details);
                assertEquals(everyLocomotive(model), before, "importing the export changed a locomotive, "
                    + first[0] + "'s skipped row included");
            }
            finally
            {
                file.delete();
            }
        });
    }

    /**
     * Presses Locomotives > Export to CSV, saves into `file` in its chooser, closes the panel showing the export, and
     * waits for the file to be written.
     */
    private static File exportThroughTheMenu(TrainControlUI ui, File file) throws Exception
    {
        Seen seen = press(ui, "exportLocsToCSVMenuItem", file, null, s -> file.length() > 0, null);

        assertTrue(file.length() > 0, "Export to CSV wrote nothing to " + file + "; the answerer saw " + seen.said);

        return file;
    }

    /** The list under the message the door says after importing a file of these rows; the locomotives made are deleted. */
    private static List<String> detailsOfImporting(String rows) throws Exception
    {
        return shapeOfImporting(rows).details;
    }

    /** The message the door says after importing a file of these rows; the locomotives made are deleted. */
    private static support.PopupShape shapeOfImporting(String rows) throws Exception
    {
        List<support.PopupShape> shape = new ArrayList<>();

        inAWindow((ui, model) ->
        {
            File file = write(HEADER11 + rows);

            model.newMFXLocomotive("FR049 door kept", 20);

            try
            {
                shape.add(press(ui, file, IMPORT, s -> !s.said.isEmpty()).saidShapes.get(0));

                for (String name : new String[] {"FR049 door year", "FR049 door two", "FR049 door only"})
                {
                    assertNull(model.getLocByName(name), "a skipped row made " + name);
                }

                assertEquals(model.getLocByName("FR049 door kept").getAddress(), 20, "a skipped row changed an existing locomotive");
            }
            finally
            {
                file.delete();
                for (String name : new String[] {"FR049 door year", "FR049 door two", "FR049 door fine", "FR049 door only", "FR049 door kept"})
                {
                    model.deleteLoc(name);
                }
            }
        });

        return shape.get(0);
    }

    /**
     * A train that is moving, with the power on, is not re-addressed: the door says so before it asks, and nothing changes.
     *
     * MUTATION: take the moving check out of the door, and this fails.
     */
    @Test
    public void testAMovingTrainIsNotReaddressed() throws Exception
    {
        refusedForATrainWithASpeed(false);
    }

    /**
     * MT-894 step 6: the same train, its speed still showing after the power is switched off, is still refused by name.
     *
     * MUTATION: in `LocomotiveSettings.hasSpeed(ViewListener, Locomotive)`, add `&& model.getPowerState()`, and this fails.
     */
    @Test
    public void testATrainWithASpeedIsRefusedWithThePowerOff() throws Exception
    {
        refusedForATrainWithASpeed(true);
    }

    /** Sets a train going, with the power then switched off or not, and imports a file that re-addresses it. */
    private static void refusedForATrainWithASpeed(boolean powerOff) throws Exception
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

                // The simulated station does not echo a stop, so the power is set where the model keeps it
                if (powerOff) Reflect.field(MarklinControlStation.class, "powerState").setBoolean(model, false);

                assertTrue(!powerOff || rolling.getSpeed() > 0, "precondition: the train's speed did not stay showing");

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

    /** Puts a locomotive on the first free point of the layout; the point, to be emptied afterwards. */
    private static org.traincontrol.automation.Point stand(MarklinControlStation model, Locomotive l)
    {
        for (org.traincontrol.automation.Point point : model.getAutoLayout().getPoints())
        {
            if (point.getCurrentLocomotive() == null)
            {
                point.setLocomotive(l);

                return point;
            }
        }

        throw new AssertionError("precondition: no free point on this layout to stand a train on");
    }

    /**
     * The question lists every locomotive that changes address or decoder, in order, in the scrolling area.
     *
     * MUTATION: list only the first ten, and this fails.
     */
    @Test
    public void testTheQuestionListsEveryChangeInOrder() throws Exception
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

                assertEquals(seen.questionDetails.size(), 12, "the list is not every change: " + seen.questionDetails);
                assertEquals(seen.questionDetails.get(0), I18n.f("loc.ui.importCsvChangeLine", "FR049 door chg 01", "MM2 1", "DCC 1001"));
                assertEquals(seen.questionDetails.get(11), I18n.f("loc.ui.importCsvChangeLine", "FR049 door chg 12", "MM2 12", "DCC 1012"));
            }
            finally
            {
                file.delete();

                for (int i = 1; i <= 12; i++) model.deleteLoc(String.format("FR049 door chg %02d", i));
            }
        });
    }

    /**
     * Stands a head on the layout (with a member linked to it, or not), and imports a row that moves another locomotive onto
     * the address of the head (or of its member): the locomotive keeps its address, is named in the question and in the
     * message, and the standing train stays on the layout.
     */
    private static void keptOntoAStandingTrain(boolean ontoTheMember) throws Exception
    {
        inAWindow((ui, model) ->
        {
            org.traincontrol.marklin.MarklinLocomotive head = model.newMM2Locomotive("FR049 door head", 72);
            Locomotive member = model.newMM2Locomotive("FR049 door member", 73);
            Locomotive other = model.newMM2Locomotive("FR049 door other", 74);

            if (ontoTheMember)
            {
                java.util.Map<String, Double> link = new HashMap<>();

                link.put(member.getName(), 1.0);
                head.setLinkedLocomotives(link);
            }

            org.traincontrol.automation.Point point = stand(model, head);
            File file = write(HEADER + "FR049 door other,,MM2," + (ontoTheMember ? 73 : 72) + ",0,0:00:00,0,0,,\n");

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty());

                String line = I18n.f("loc.ui.importCsvKeptLine", "FR049 door other", I18n.t("loc.ui.importCsvKeptStanding"));

                assertTrue(seen.questionDetails.contains(line), "the question does not name the locomotive that keeps its address: "
                    + seen.questionDetails);
                assertTrue(seen.saidShapes.get(0).details.contains(line), "the message does not name it: " + seen.saidShapes.get(0).details);
                assertEquals(other.getAddress(), 74, "a locomotive was given the address of a standing train, or of its member");
                assertNotNull(model.getAutoLayout().getLocomotiveLocation(head), "the standing train left the layout");
            }
            finally
            {
                point.setLocomotive(null);
                file.delete();

                for (String name : new String[] {"head", "member", "other"}) model.deleteLoc("FR049 door " + name);
            }
        });
    }

    /**
     * An address change that would put a locomotive on the address of a train standing on the layout is kept out and named.
     *
     * MUTATION: let the file give a locomotive the address of a standing train, and this fails.
     */
    @Test
    public void testAStandingTrainsAddressIsKeptByName() throws Exception
    {
        keptOntoAStandingTrain(false);
    }

    /**
     * The same for the address of a member of a standing consist: the sweep would take the head off the layout.
     *
     * MUTATION: look only at the standing train itself and not its members, and this fails.
     */
    @Test
    public void testAMemberOfAStandingConsistsAddressIsKeptByName() throws Exception
    {
        keptOntoAStandingTrain(true);
    }

    /**
     * A clash that an earlier row clears is cleared in the plan too: a train with a speed that the file moves onto the
     * address a standing train leaves is in the plan, and the moving check names it before anything is asked.
     *
     * MUTATION: judge every change against the addresses as they are now, and this fails.
     */
    @Test
    public void testAClashFreedByAnEarlierRowStillMeetsTheMovingCheck() throws Exception
    {
        inAWindow((ui, model) ->
        {
            Locomotive standing = model.newMM2Locomotive("FR049 door leaves", 70);
            Locomotive rolling = model.newMM2Locomotive("FR049 door arrives", 71);
            org.traincontrol.automation.Point point = stand(model, standing);
            File file = write(HEADER + "FR049 door leaves,,MM2,75,0,0:00:00,0,0,,\nFR049 door arrives,,MM2,70,0,0:00:00,0,0,,\n");

            try
            {
                model.go();
                model.waitForPowerState(true);
                rolling.setSpeed(40);
                rolling.waitForSpeedAtOrAbove(1);

                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty());

                assertEquals(seen.said, Collections.singletonList(I18n.f("loc.ui.errorImportCsvMoving", "FR049 door arrives")));
                assertNull(seen.question, "the door asked, and would have re-addressed a moving train");
                assertEquals(rolling.getAddress(), 71);
                assertEquals(standing.getAddress(), 70);
            }
            finally
            {
                rolling.setSpeed(0);
                rolling.waitForSpeedBelow(1);
                model.stop();
                model.waitForPowerState(false);
                point.setLocomotive(null);
                file.delete();
                model.deleteLoc("FR049 door leaves");
                model.deleteLoc("FR049 door arrives");
            }
        });
    }

    /**
     * A train that gets a speed while the question is open is not re-addressed when it is answered Yes: the door asks
     * the moving check again after the question and refuses.
     *
     * MUTATION: leave the moving check out of the gates asked after the question, and this fails.
     */
    @Test
    public void testATrainThatStartsMovingBehindTheQuestionIsNotReaddressed() throws Exception
    {
        inAWindow((ui, model) ->
        {
            Locomotive behind = model.newMM2Locomotive("FR049 door behind", 30);
            File file = write(HEADER + "FR049 door behind,,DCC,31,0,0:00:00,0,0,,\n");

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty(), () ->
                {
                    behind.setSpeed(40);
                    behind.waitForSpeedAtOrAbove(1);
                });

                assertNotNull(seen.question, "precondition: the door did not ask");
                assertEquals(seen.said, Collections.singletonList(I18n.f("loc.ui.errorImportCsvMoving", "FR049 door behind")));
                assertEquals(behind.getAddress(), 30, "a train that began to move behind the question was re-addressed");
            }
            finally
            {
                behind.setSpeed(0);
                behind.waitForSpeedBelow(1);
                file.delete();
                model.deleteLoc("FR049 door behind");
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

                support.Components.waitFor(() -> model.getRoute(route).isExecuting(), 5000);

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

    /** A file in the temporary folder that does not exist yet, for Export to CSV to write. */
    private static File unwritten(String name)
    {
        return new File(System.getProperty("java.io.tmpdir"), "tc-" + name + "-" + System.nanoTime() + ".csv");
    }

    /** An address no locomotive holds, counting down from `from`. */
    private static int freeAddress(MarklinControlStation model, int from)
    {
        java.util.Set<Integer> held = new java.util.HashSet<>();

        for (Locomotive l : model.getLocomotives()) held.add(l.getAddress());

        int address = from;

        while (held.contains(address)) address--;

        return address;
    }

    /**
     * Comma-separated text as Excel on a European Windows saves it: semicolons, a field holding one in quotes, Windows
     * line ends, and the Windows code page.
     */
    private static byte[] asExcelSavesIt(String text)
    {
        StringBuilder out = new StringBuilder();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);

            if (quoted && c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') field.append(text.charAt(++i));
            else if (c == '"') quoted = !quoted;
            else if (quoted || (c != ',' && c != '\n' && c != '\r')) field.append(c);
            else if (c != '\r')
            {
                String cell = field.toString();

                out.append(cell.matches("(?s).*[;\"\n].*") ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell);
                out.append(c == ',' ? ";" : "\r\n");
                field.setLength(0);
            }
        }

        return out.append(field).toString().getBytes(java.nio.charset.Charset.forName("windows-1252"));
    }

    /**
     * MT-894 steps 1 and 2: the file Export to CSV saves ends its heading with LastRun; imported again as it is, the
     * question merges every locomotive and changes no address or decoder, the import adds nothing and changes nothing,
     * and the export is then what that file holds.
     *
     * MUTATION: in `MarklinLocomotiveCsv.wouldReaddress`, answer that every row changes address, and this fails.
     */
    @Test
    public void testAnExportImportsBackAsItWas() throws Exception
    {
        inAWindow((ui, model) ->
        {
            File first = unwritten("export");

            try
            {
                Map<String, String> before = everyLocomotive(model);
                int all = model.getLocomotives().size();
                byte[] exported = Files.readAllBytes(exportThroughTheMenu(ui, first).toPath());
                String heading = new String(exported, StandardCharsets.UTF_8).replace("\uFEFF", "").split("\r?\n", 2)[0];

                assertTrue(heading.endsWith(",LastRun"), "the export's last column is not LastRun: " + heading);

                Seen seen = press(ui, first, IMPORT, s -> !s.said.isEmpty());

                assertEquals(words(seen.question), words(I18n.f("loc.ui.confirmImportCsv", 0, all)), "the question about"
                    + " a file just exported does not say every locomotive is merged and nothing else");
                assertTrue(seen.questionDetails.isEmpty(), "the question lists changes in a file just exported: "
                    + seen.questionDetails);
                assertEquals(seen.said.size(), 1, "the door said: " + seen.said);
                assertEquals(words(seen.said.get(0)), words(I18n.f("loc.ui.infoImportCsvDone", 0, all)));
                assertEquals(everyLocomotive(model), before, "importing the file just exported changed a locomotive");
                assertEquals(model.exportLocsToCSV(), new String(exported, StandardCharsets.UTF_8).replace("\uFEFF", ""),
                    "the export differs after its own file was imported");
            }
            finally
            {
                first.delete();
            }
        });
    }

    /**
     * MT-894 step 3: the export saved again as Excel on a European Windows saves it, with one locomotive's address
     * changed and a row added for a new one whose name has an umlaut.  The question lists the change by name, old to
     * new, and counts one new; after Import the address has changed, the new locomotive is there with its umlaut, and
     * nothing else came in.
     *
     * MUTATION: in `MarklinLocomotiveCsv.separator`, always answer a comma, and this fails.
     */
    @Test
    public void testAnExportSavedByExcelBringsItsChangeAndItsNewName() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final String moved = "FR049 door excel";
            final String added = "FR049 door J\u00fcrgen";
            final int from = freeAddress(model, 14000);

            Locomotive excel = model.newMFXLocomotive(moved, from);

            final int to = freeAddress(model, from - 1);
            File file = unwritten("excel");

            try
            {
                String text = new String(Files.readAllBytes(exportThroughTheMenu(ui, file).toPath()),
                    StandardCharsets.UTF_8).replace("\uFEFF", "");
                String row = "\n" + moved + ",,MFX," + from + ",";

                assertTrue(text.contains(row), "precondition: the export has no plain row for " + moved);

                text = text.replace(row, "\n" + moved + ",,MFX," + to + ",") + (text.endsWith("\n") ? "" : "\n") + added
                    + ",,MFX," + freeAddress(model, 12000) + ",0,0:00:00,0,0,,,\n";

                assertTrue(java.nio.charset.Charset.forName("windows-1252").newEncoder().canEncode(text),
                    "precondition: the export holds a name the Windows code page cannot save");

                Files.write(file.toPath(), asExcelSavesIt(text));

                int before = model.getLocomotives().size();
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty());

                assertTrue(words(seen.question).startsWith(words(I18n.f("loc.ui.confirmImportCsv", 1, before))),
                    "the question does not count one new locomotive: " + seen.question);
                assertTrue(seen.questionDetails.contains(I18n.f("loc.ui.importCsvChangeLine", moved, "MFX " + from,
                    "MFX " + to)), "the question does not list the address change by name, old to new: "
                    + seen.questionDetails);
                assertEquals(excel.getAddress(), to, "the address change in Excel's file was not made");
                assertNotNull(model.getLocByName(added), "the new locomotive did not come in with its umlaut");
                assertEquals(model.getLocomotives().size(), before + 1, "more than the one new locomotive came in");
            }
            finally
            {
                file.delete();
                model.deleteLoc(moved);
                model.deleteLoc(added);
            }
        });
    }

    /**
     * Imports, `times` times through the door and answered Import, a file of one row for a locomotive made at MM2 20 with
     * an hour already run on an earlier day; after each, `check` is given the locomotive and the one message said.
     */
    private static void importOneRow(String row, int times, java.util.function.BiConsumer<Locomotive, String> check)
        throws Exception
    {
        inAWindow((ui, model) ->
        {
            Locomotive l = model.newMM2Locomotive("FR049 door row", 20);
            File file = write(HEADER11 + "FR049 door row" + row + "\n");

            l.getHistoricalOperatingTime().put("2024-01-01", 3600000L);

            try
            {
                for (int time = 0; time < times; time++)
                {
                    List<String> said = press(ui, file, IMPORT, s -> !s.said.isEmpty()).said;

                    assertEquals(said.size(), 1, "import " + (time + 1) + " said: " + said);
                    check.accept(l, words(said.get(0)));
                }
            }
            finally
            {
                file.delete();
                model.deleteLoc(l.getName());
            }
        });
    }

    /**
     * MT-894 step 5: a later LastRun with a larger runtime raises the runtime by the difference, filed on that day, where
     * the statistics read it; the same file imported again adds nothing.
     *
     * MUTATION: in `MarklinLocomotiveCsv.merge`, file the raise on today instead of the file's last run day, and this
     * fails.
     */
    @Test
    public void testALaterLastRunRaisesTheRuntimeOnItsDayOnce() throws Exception
    {
        importOneRow(",,MM2,20,0,3:00:00,0,0,,,2024-06-06", 2, (runner, said) ->
        {
            assertEquals(runner.getTotalRuntime(), 10800000L, "the file's larger runtime is not the total, once");
            assertEquals(runner.getRuntimeOnDay("2024-06-06"), 7200000L, "the difference is not on the file's last run day");
            assertEquals(runner.getRuntimeOnDay("2024-01-01"), 3600000L, "the import changed another day");
        });
    }

    /**
     * MT-894 step 8: a row whose runtime is 12:99:00 still brings its other changes, and the message says one runtime
     * could not be read; the runtime stays as it was.
     *
     * MUTATION: in `MarklinLocomotiveCsv.runtimeMs`, stop checking that the minutes are under 60, and this fails.
     */
    @Test
    public void testABadRuntimeIsSaidAndTheRestOfTheRowApplies() throws Exception
    {
        importOneRow(",,DCC,21,0,12:99:00,0,0,,,2024-06-06", 1, (timed, said) ->
        {
            assertTrue(said.contains(words(I18n.f("loc.ui.infoImportCsvBadRuntime", 1))), "the message does not say the"
                + " runtime could not be read: " + said);
            assertEquals(timed.getAddress(), 21, "the row's address was not taken because of its runtime");
            assertEquals(timed.getDecoderType(), Locomotive.decoderType.DCC);
            assertEquals(timed.getTotalRuntime(), 3600000L, "a runtime of 12:99:00 was filed");
        });
    }

    /** Runs `look` while the question shows; a failure is kept in `failed`, so the question is still answered. */
    private static Runnable safely(List<String> failed, Runnable look)
    {
        return () ->
        {
            try
            {
                look.run();
            }
            catch (Throwable t)
            {
                failed.add(String.valueOf(t));
            }
        };
    }

    /** The selected radios of the question's table, by name ("row:ANSWER"), top to bottom. */
    private static List<String> picked()
    {
        List<String> out = new ArrayList<>();

        for (javax.swing.JRadioButton b : support.Components.all(asking, javax.swing.JRadioButton.class,
            javax.swing.JRadioButton::isSelected)) out.add(b.getName());

        return out;
    }

    /** Clicks the question's control named `name`, or saying `text` when name is null, and waits for what it does. */
    private static void click(String name, String text)
    {
        javax.swing.AbstractButton b = name != null ? support.Components.named(asking, name,
            javax.swing.AbstractButton.class) : support.Components.withText(asking, text);

        support.Components.onTheEventThread(() ->
        {
            b.doClick();
            return null;
        });
    }

    /** A file row for a new mfx locomotive. */
    private static String mfx(String name, int address)
    {
        return name + ",,MFX," + address + ",0,0:00:00,0,0,,\n";
    }

    /**
     * A new name at a decoder address a locomotive here holds is a possible duplicate (FR-249): the question shows it in
     * a table, one row starting on Keep Both, and counts only the other new locomotive with the new ones.  A file with no
     * possible duplicate has no table, and Cancel imports nothing.
     *
     * MUTATION: make `MarklinLocomotiveCsv.duplicates` find none, and this fails.
     */
    @Test
    public void testAPossibleDuplicateIsAskedInATableAndCancelImportsNothing() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final String here = "FR249 door here", twin = "FR249 door twin", alone = "FR249 door alone";
            final int held = freeAddress(model, 13000);

            model.newMFXLocomotive(here, held);

            final int free = freeAddress(model, held - 1);
            File file = write(HEADER + mfx(twin, held) + mfx(alone, free));
            File plain = write(HEADER + mfx(alone, free));
            List<String> rows = new ArrayList<>(), none = new ArrayList<>(), failed = new ArrayList<>();

            try
            {
                Map<String, String> before = everyLocomotive(model);
                Seen seen = press(ui, file, CANCEL, s -> s.answered, safely(failed, () -> rows.addAll(picked())));

                assertEquals(failed, Collections.emptyList());
                assertEquals(rows, Collections.singletonList(twin + ":BOTH"),
                    "the table is not one row for the possible duplicate, on Keep Both");
                assertTrue(words(seen.question).startsWith(words(I18n.f("loc.ui.confirmImportCsv", 1, 0))),
                    "the question counts the possible duplicate with the new ones: " + seen.question);
                assertTrue(seen.question.contains(I18n.f("loc.ui.importCsvDuplicates", 1)),
                    "the table has no heading: " + seen.question);
                assertEquals(everyLocomotive(model), before, "Cancel imported something");
                assertTrue(seen.said.isEmpty(), "Cancel went on to say something: " + seen.said);

                press(ui, plain, CANCEL, s -> s.answered, safely(failed, () -> none.addAll(picked())));

                assertEquals(failed, Collections.emptyList());
                assertTrue(none.isEmpty(), "a file with no possible duplicate showed a table: " + none);
            }
            finally
            {
                file.delete();
                plain.delete();
                model.deleteLoc(here);
            }
        });
    }

    /**
     * Each answer in the duplicates' table is carried out (FR-249): Keep Both adds the file's locomotive beside the one
     * here, Keep Old leaves it out, and Keep New renames a train standing on the layout to the file's name, the same
     * locomotive, so it stays where it stands and keeps its timetable entry.  The message counts them, and the import
     * runs no Sync.
     *
     * MUTATION: in `MarklinLocomotiveCsv.apply`, add a Keep New row as Keep Both, and this fails.
     */
    @Test
    public void testEachAnswerInTheDuplicatesTableIsCarriedOut() throws Exception
    {
        inAWindow((ui, model) ->
        {
            org.traincontrol.automation.Layout layout = model.getAutoLayout();
            org.traincontrol.automation.Point standing = null;

            // A train standing alone at its decoder address, so its Keep New is offered
            for (org.traincontrol.automation.Point p : layout.getPoints())
            {
                Locomotive l = p.getCurrentLocomotive();
                long sharing = l == null ? 0 : model.getLocomotives().stream().filter(o -> o.getAddress()
                    == l.getAddress() && o.getDecoderType() == l.getDecoderType()).count();

                if (standing == null && sharing == 1 && l.getDecoderType() != Locomotive.decoderType.MULTI_UNIT) standing = p;
            }

            assertNotNull(standing, "precondition: no train stands alone at its address in the snapshot");

            final org.traincontrol.automation.Point stands = standing;
            final Locomotive train = stands.getCurrentLocomotive();
            final String was = train.getName(), both = "FR249 door both", left = "FR249 door left", taken = "FR249 door took";
            final String bothHere = "FR249 door both here", leftHere = "FR249 door left here";
            final int a = freeAddress(model, 13000);

            model.newMFXLocomotive(bothHere, a);

            final int b = freeAddress(model, a - 1);

            model.newMFXLocomotive(leftHere, b);

            org.traincontrol.automation.TimetablePath entry = new org.traincontrol.automation.TimetablePath(train,
                Collections.singletonList(layout.getEdges().iterator().next()), 0);

            layout.getTimetable().add(entry);

            File file = write(HEADER + mfx(both, a) + mfx(left, b) + taken + ",," + train.getDecoderType() + ","
                + train.getAddress() + ",0,0:00:00,0,0,,\n");
            List<String> failed = new ArrayList<>();

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty(), safely(failed, () ->
                {
                    click(left + ":OLD", null);
                    click(taken + ":NEW", null);
                }));

                assertEquals(failed, Collections.emptyList());
                assertNotNull(model.getLocByName(both), "Keep Both did not add the file's locomotive");
                assertNotNull(model.getLocByName(bothHere), "Keep Both lost the locomotive here");
                assertNull(model.getLocByName(left), "Keep Old added the file's locomotive");
                assertNotNull(model.getLocByName(leftHere), "Keep Old lost the locomotive here");
                assertTrue(model.getLocByName(taken) == train, "Keep New did not rename the locomotive here");
                assertNull(model.getLocByName(was), "Keep New left the old name behind");
                assertTrue(stands.getCurrentLocomotive() == train, "the renamed train no longer stands where it stood");
                assertTrue(layout.getTimetable().contains(entry) && entry.getLoc() == train,
                    "the renamed train lost its timetable entry");
                assertTrue(seen.said.get(0).contains(I18n.f("loc.ui.infoImportCsvRenamed", 1))
                    && seen.said.get(0).contains(I18n.f("loc.ui.infoImportCsvKeptOld", 1)),
                    "the message does not count the answers: " + seen.said);
                assertFalse(seen.titles.contains(I18n.t("sync.ui.title")), "the import ran a Sync: " + seen.titles);
            }
            finally
            {
                layout.getTimetable().remove(entry);

                if (model.getLocByName(taken) == train) model.renameLoc(taken, was);

                file.delete();

                for (String name : new String[] {both, left, bothHere, leftHere}) model.deleteLoc(name);
            }
        });
    }

    /**
     * Keep New is grayed, with its reason on hover beside "(can't apply)", where several locomotives here share the
     * address, where the file also lists the locomotive here, and where it is running; a second row for one locomotive
     * here is grayed once the first takes it (FR-249).  The Keep New heading skips every grayed row; Keep Old's sets
     * every row.
     *
     * MUTATION: in `MarklinLocomotiveCsv.duplicates`, leave out the RUNNING reason, and this fails.
     */
    @Test
    public void testKeepNewIsGrayedWithItsReasonAndSetAllSkipsIt() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final String s1 = "FR249 door s1", s2 = "FR249 door s2", listed = "FR249 door listed", rolling = "FR249 door rolling";
            final String held = "FR249 door held", sNew = "FR249 door s new", lNew = "FR249 door l new";
            final String rNew = "FR249 door r new", t1 = "FR249 door t1", t2 = "FR249 door t2";
            final int s = freeAddress(model, 13000);

            model.newMFXLocomotive(s1, s);
            model.newMFXLocomotive(s2, s);

            final int l = freeAddress(model, s - 1);

            model.newMFXLocomotive(listed, l);

            final int r = freeAddress(model, l - 1);
            Locomotive moving = model.newMFXLocomotive(rolling, r);
            final int t = freeAddress(model, r - 1);

            model.newMFXLocomotive(held, t);

            File file = write(HEADER + mfx(sNew, s) + mfx(lNew, l) + mfx(listed, l) + mfx(rNew, r) + mfx(t1, t)
                + mfx(t2, t));
            List<String> failed = new ArrayList<>(), reasons = new ArrayList<>(), onNew = new ArrayList<>();
            List<String> onOld = new ArrayList<>(), tookTip = new ArrayList<>();

            try
            {
                model.go();
                model.waitForPowerState(true);
                moving.setSpeed(30);
                moving.waitForSpeedAtOrAbove(1);

                Map<String, String> before = everyLocomotive(model);

                press(ui, file, CANCEL, seen -> seen.answered, safely(failed, () ->
                {
                    String cant = I18n.t("sync.ui.cantApply");

                    for (javax.swing.JLabel mark : support.Components.all(asking, javax.swing.JLabel.class,
                        x -> cant.equals(x.getText()) && x.isVisible())) reasons.add(mark.getToolTipText());

                    for (String row : new String[] {sNew, lNew, rNew})
                    {
                        javax.swing.AbstractButton keepNew = support.Components.named(asking, row + ":NEW",
                            javax.swing.AbstractButton.class);

                        if (keepNew.isEnabled()) failed.add(row + "'s Keep New is not grayed");

                        reasons.add(keepNew.getToolTipText());
                    }

                    click(null, I18n.t("loc.ui.importCsvKeepNew"));
                    onNew.addAll(picked());

                    javax.swing.JLabel took = support.Components.named(asking, t2 + ":taken", javax.swing.JLabel.class);

                    tookTip.add(took.isVisible() + " " + took.getToolTipText());
                    click(null, I18n.t("loc.ui.importCsvKeepOld"));
                    onOld.addAll(picked());
                }));

                String several = I18n.f("loc.ui.importCsvWhySeveral", "MFX " + s);
                String lists = I18n.f("loc.ui.importCsvWhyListed", listed);
                String runs = I18n.f("loc.ui.importCsvWhyRunning", rolling);

                assertEquals(failed, Collections.emptyList());
                assertEquals(reasons, java.util.Arrays.asList(several, lists, runs, several, lists, runs),
                    "each grayed Keep New does not say why, beside (can't apply) and on hover");
                assertEquals(onNew, java.util.Arrays.asList(sNew + ":BOTH", lNew + ":BOTH", rNew + ":BOTH", t1 + ":NEW",
                    t2 + ":BOTH"), "the Keep New heading did not skip the grayed rows and the one taken");
                assertEquals(tookTip, Collections.singletonList("true " + I18n.f("loc.ui.importCsvWhyTaken", t1, held)),
                    "the row whose locomotive another row took does not say so");
                assertEquals(onOld, java.util.Arrays.asList(sNew + ":OLD", lNew + ":OLD", rNew + ":OLD", t1 + ":OLD",
                    t2 + ":OLD"), "the Keep Old heading did not set every row");
                assertEquals(everyLocomotive(model), before, "Cancel imported something");
            }
            finally
            {
                moving.setSpeed(0);
                moving.waitForSpeedBelow(1);
                model.stop();
                model.waitForPowerState(false);
                file.delete();

                for (String name : new String[] {s1, s2, listed, rolling, held}) model.deleteLoc(name);
            }
        });
    }
}
