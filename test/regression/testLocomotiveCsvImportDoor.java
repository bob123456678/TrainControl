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
        /** The titles of the dialogs that stood lower than an always-on-top main window, so under it. */
        final List<String> under = Collections.synchronizedList(new ArrayList<>());
    }

    /** The shared shape of a claim: a window, a file, an answer, and the door pressed. */
    private interface Claim
    {
        void check(TrainControlUI ui, MarklinControlStation model) throws Exception;
    }

    /**
     * The duplicates popup keeps its heading close: with nothing to say above the table it starts the popup, and with a
     * sentence it sits no more than the house gap (6 px) under it; the table sits no more than 6 px under the heading
     * (MT-1097).
     *
     * MUTATION: in `LocomotiveCsvDoor`, drop `bare`, or put the 12 px strut back, or in `DetailsArea.message` give the
     * BorderLayout its 8 px gap with no list, and this fails.
     */
    @Test
    public void testTheDuplicatesHeadingSitsCloseAboveTheTable() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final int held = freeAddress(model, 13000), free = freeAddress(model, held - 1);

            model.newMFXLocomotive("gap here", held);
            File bare = write(HEADER + mfx("gap twin", held));
            File said = write(HEADER + mfx("gap twin", held) + mfx("gap alone", free));

            try
            {
                int[] none = gapsAround(ui, bare), some = gapsAround(ui, said);

                assertEquals(none[0], 0, "a popup with nothing above the table starts " + none[0] + " px above its heading");
                assertTrue(some[0] >= 0 && some[0] <= 6, "the heading sits " + some[0] + " px under the sentence");
                assertTrue(none[1] >= 0 && none[1] <= 6 && some[1] >= 0 && some[1] <= 6,
                    "the table sits " + none[1] + " and " + some[1] + " px under the heading");
            }
            finally
            {
                bare.delete();
                said.delete();
                model.deleteLoc("gap here");
            }
        });
    }

    /** The gaps in the duplicates popup for `file`: above its heading (from the sentence, or the popup's top), and below. */
    private static int[] gapsAround(TrainControlUI ui, File file) throws Exception
    {
        final int[] gaps = new int[2];

        press(ui, file, CANCEL, s -> s.answered, () -> support.Components.onTheEventThread(() ->
        {
            List<javax.swing.JTextArea> sentence = support.Components.all(asking, javax.swing.JTextArea.class, a -> true);
            javax.swing.JLabel heading = support.Components.all(asking, javax.swing.JLabel.class,
                l -> l.getText() != null && l.getText().startsWith("Locomotives that may already")).get(0);
            org.traincontrol.gui.widgets.ChoiceTable table = support.Components.all(asking,
                org.traincontrol.gui.widgets.ChoiceTable.class, t -> true).get(0);
            java.awt.Rectangle head = SwingUtilities.convertRectangle(heading.getParent().getParent(),
                heading.getParent().getBounds(), asking);
            java.awt.Rectangle grid = SwingUtilities.convertRectangle(table.getParent(), table.getBounds(), asking);

            gaps[0] = heading.getParent().getY();
            if (!sentence.isEmpty())
            {
                java.awt.Rectangle text = SwingUtilities.convertRectangle(sentence.get(0).getParent(),
                    sentence.get(0).getBounds(), asking);
                gaps[0] = head.y - (text.y + text.height);
            }
            gaps[1] = grid.y - (head.y + head.height);

            return null;
        }));

        return gaps;
    }

    /**
     * The duplicates popup is no wider than the main window, and the table scrolls sideways only past it (MT-1097).
     *
     * MUTATION: in `LocomotiveCsvDoor`, leave out the `limitWidth` call, and the popup is wider than a 700 px window;
     * reserve 140 instead of 72 scaled + 18, and a window just wide enough for the popup still scrolls the table.
     */
    @Test
    public void testTheDuplicatesPopupIsNoWiderThanTheMainWindow() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final int held = freeAddress(model, 13000);
            model.newMFXLocomotive("wide here", held);
            File file = write(HEADER + mfx("wide twin", held));

            try
            {
                int natural = popupWidth(ui, file, java.awt.Toolkit.getDefaultToolkit().getScreenSize().width);
                int exact = popupWidth(ui, file, natural), narrower = popupWidth(ui, file, natural - 1);

                assertTrue(natural > 300, "precondition: the popup is " + natural + " px wide");
                assertEquals(exact, natural, "a window as wide as the popup: it is " + exact + " px, not " + natural);
                assertTrue(narrower <= natural - 1, "one pixel narrower the popup is " + narrower + " px in a "
                    + (natural - 1) + " px window");
                assertTrue(narrower < natural, "the table did not narrow in the " + (natural - 1) + " px window");
                assertTrue(popupWidth(ui, file, 700) <= 700, "the popup is wider than a 700 px window");
            }
            finally
            {
                file.delete();
                model.deleteLoc("wide here");
            }
        });
    }

    /** The duplicates popup's width with the main window `width` px wide (the window as it was is restored). */
    private static int popupWidth(TrainControlUI ui, File file, int width) throws Exception
    {
        final int[] shown = new int[2];
        final java.awt.Dimension was = support.Components.onTheEventThread(() -> ui.getSize());
        final java.awt.Dimension least = support.Components.onTheEventThread(() -> ui.getMinimumSize());

        try
        {
            support.Components.onTheEventThread(() ->
            {
                ui.setMinimumSize(new java.awt.Dimension(300, 300));
                ui.setSize(width, was.height);
                ui.setVisible(true);
                return null;
            });
            press(ui, file, CANCEL, s -> s.answered, () -> support.Components.onTheEventThread(() ->
            {
                shown[0] = SwingUtilities.getWindowAncestor(asking).getWidth();
                shown[1] = ui.getWidth();

                return null;
            }));
        }
        finally
        {
            support.Components.onTheEventThread(() ->
            {
                ui.setMinimumSize(least);
                ui.setVisible(false);
                ui.setSize(was);
                return null;
            });
        }

        assertEquals(shown[1], width, "precondition: the main window did not take the width");
        return shown[0];
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
            if (handled.add(dialog))
            {
                seen.titles.add(dialog.getTitle());

                if (ui.isAlwaysOnTop() && !dialog.isAlwaysOnTop()) seen.under.add(dialog.getTitle());
            }

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
                assertEquals(words(seen.question), words(counts(1, 1) + "\n\n"
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
     * MUTATION: in `LocomotiveCsvImport.tell`, leave out the `infoImportCsvSkipped` sentence, and this fails.
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
     * With Always on Top on, Export to CSV's chooser and panel stand as high as the main window, never hidden under
     * it while they hold every window (OB-522): the chooser opens before the panel is in a window.  A closed window
     * stops being their owner, or the next would reopen it.
     *
     * MUTATION: drop `JOptionPane.setRootFrame(this)` from `TrainControlUI`'s constructor and the chooser hangs off
     * Swing's hidden shared frame, under the window; drop the line in `TrainControlUI.dispose` and the last fails.
     */
    @Test
    public void testTheExportStandsAboveAnAlwaysOnTopWindow() throws Exception
    {
        final java.awt.Frame[] window = {null};

        inAWindow((ui, model) ->
        {
            window[0] = ui;

            final JMenuItem onTop = (JMenuItem) Reflect.get(TrainControlUI.class, ui, "windowAlwaysOnTopMenuItem");
            final boolean was = onTop.isSelected();
            File file = new File(System.getProperty("java.io.tmpdir"), "tc-export-" + System.nanoTime() + ".csv");

            try
            {
                if (!was) SwingUtilities.invokeAndWait(onTop::doClick);

                assertTrue(ui.isAlwaysOnTop(), "precondition: Always on Top did not put the window on top");

                Seen seen = press(ui, "exportLocsToCSVMenuItem", file, null, s -> file.length() > 0, null);

                assertTrue(seen.chose && file.length() > 0, "the export's chooser never saved; dialogs: " + seen.titles);
                assertEquals(seen.under, Collections.emptyList(), "these stood under the always-on-top window,"
                    + " holding it unclickable: " + seen.under + " of " + seen.titles);
            }
            finally
            {
                if (onTop.isSelected() != was) SwingUtilities.invokeAndWait(onTop::doClick);

                file.delete();
            }
        });

        assertTrue(JOptionPane.getRootFrame() != window[0], "the closed window still owns every windowless dialog");
    }

    /**
     * Canceling the Save window an export opens at once ends the export: no panel and no message follow (OB-525), for
     * Export to CSV and Export Routes alike.
     *
     * MUTATION: show the panel whatever `JsonExportPanel.isCancelled()` says and the export window opens after Cancel.
     */
    @Test
    public void testCancelingTheExportsSaveWindowEndsTheExport() throws Exception
    {
        inAWindow((ui, model) ->
        {
            for (String door : new String[] {"exportLocsToCSVMenuItem", "exportRoutesMenuItem"})
            {
                Seen seen = press(ui, door, null, null, s -> s.chose, null);

                assertTrue(seen.chose, door + ": the export's chooser never showed");
                assertEquals(seen.titles.size(), 1, door + ": a window opened after Cancel: " + seen.titles);
                assertEquals(seen.said, Collections.emptyList(), door + ": a message followed Cancel");
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
     * MUTATION: in `LocomotiveEditRule.hasSpeed(ViewListener, Locomotive)`, add `&& model.getPowerState()`, and this fails.
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

                assertEquals(words(seen.question), words(counts(0, all)), "the question about"
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

                assertTrue(words(seen.question).startsWith(words(counts(1, before))),
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

    /** What the question says of the new and the merged counts: a count of 0 is left out, and 1 reads in the singular. */
    private static String counts(int added, int merged)
    {
        return (added == 0 ? "" : I18n.f(added == 1 ? "loc.ui.confirmImportCsvOne" : "loc.ui.confirmImportCsv", added))
            + (added == 0 || merged == 0 ? "" : " ") + (merged == 0 ? "" : I18n.f(merged == 1
            ? "loc.ui.confirmImportCsvMergeOne" : "loc.ui.confirmImportCsvMerge", merged));
    }

    /** A file row for a new mfx locomotive. */
    private static String mfx(String name, int address)
    {
        return name + ",,MFX," + address + ",0,0:00:00,0,0,,\n";
    }

    /**
     * A new name at a decoder address a locomotive here holds is a possible duplicate (FR-249): the question shows it in
     * a table, one row starting on Merge, and counts only the other new locomotive with the new ones.  A file with no
     * possible duplicate has no table, and Cancel imports nothing.
     *
     * MUTATION: make `MarklinLocomotiveCsv.duplicates` find none, or leave out `setAll(Choice.MERGE)` in the
     * `LocomotiveCsvImport.Duplicates` constructor, and this fails.
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
                assertEquals(rows, Collections.singletonList(twin + ":MERGE"),
                    "the table is not one row for the possible duplicate, on Merge");
                assertTrue(words(seen.question).startsWith(words(counts(1, 0))),
                    "the question counts the possible duplicate with the new ones: " + seen.question);
                assertTrue(seen.question.contains(I18n.f("loc.ui.importCsvDuplicates", 1)),
                    "the table has no heading: " + seen.question);
                assertTrue(seen.question.contains("already be in the database"),
                    "the heading does not say what the rows are: " + seen.question);
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
     * here, Ignore leaves it out, and Merge renames a train standing on the layout to the file's name, the same
     * locomotive, so it stays where it stands and keeps its timetable entry.  The message counts them, and the import
     * runs no Sync, but the rename marks the menu as the station may now differ.
     *
     * MUTATION: in `MarklinLocomotiveCsv.apply`, add a Merge row as Keep Both or an Ignore row as Keep Both, or in
     * `ManualSync.imported`, mark only re-addressed locomotives, and this fails.
     */
    @Test
    public void testEachAnswerInTheDuplicatesTableIsCarriedOut() throws Exception
    {
        inAWindow((ui, model) ->
        {
            org.traincontrol.automation.Layout layout = model.getAutoLayout();
            org.traincontrol.automation.Point standing = null;

            // A train standing alone at its decoder address, so its Merge is offered
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
            final int unsettled = model.getUnsettledCount();

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty(), safely(failed, () ->
                {
                    click(both + ":BOTH", null);
                    click(left + ":IGNORE", null);
                    click(taken + ":MERGE", null);
                }));

                assertEquals(failed, Collections.emptyList());
                assertNotNull(model.getLocByName(both), "Keep Both did not add the file's locomotive");
                assertNotNull(model.getLocByName(bothHere), "Keep Both lost the locomotive here");
                assertNull(model.getLocByName(left), "Ignore added the file's locomotive");
                assertNotNull(model.getLocByName(leftHere), "Ignore lost the locomotive here");
                assertTrue(model.getLocByName(taken) == train, "Merge did not rename the locomotive here");
                assertNull(model.getLocByName(was), "Merge left the old name behind");
                assertTrue(stands.getCurrentLocomotive() == train, "the renamed train no longer stands where it stood");
                assertTrue(layout.getTimetable().contains(entry) && entry.getLoc() == train,
                    "the renamed train lost its timetable entry");
                assertTrue(seen.said.get(0).contains(I18n.t("loc.ui.infoImportCsvRenamedOne"))
                    && seen.said.get(0).contains(I18n.t("loc.ui.infoImportCsvIgnoredOne")),
                    "the message does not count the answers: " + seen.said);
                assertFalse(seen.titles.contains(I18n.t("sync.ui.title")), "the import ran a Sync: " + seen.titles);
                assertEquals(model.getUnsettledCount(), -1, "the rename did not mark the menu");
            }
            finally
            {
                model.setUnsettledCount(unsettled);
                layout.getTimetable().remove(entry);

                if (model.getLocByName(taken) == train) model.renameLoc(taken, was);

                file.delete();

                for (String name : new String[] {both, left, bothHere, leftHere}) model.deleteLoc(name);
            }
        });
    }

    /**
     * Replace renames the locomotive here to the file's name and takes the file's details, its functions as a new
     * locomotive has them, keeping its running time (FR-249).  The finishing message counts it apart from a Merge, and
     * its rename marks the menu, as the station may now differ.
     *
     * MUTATION: in `MarklinLocomotiveCsv.apply`, leave out `clear` for a Replace row, or leave Replace's rename out of
     * `result.renamed`; in `LocomotiveCsvImport.tell`, leave out the Replace line, and this fails.
     */
    @Test
    public void testReplaceTakesTheFilesDetailsIsCountedAndMarksTheMenu() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final String was = "FR249 door replaced here", name = "FR249 door replaced";
            final int address = freeAddress(model, 13000);
            Locomotive here = model.newMFXLocomotive(was, address);

            here.setFunctionType(2, 9, 0);
            here.setPreferredSpeed(40);
            here.setStructuredNotes(1900, 0, "DR", "kept here");
            here.raiseTotalRuntimeTo(3600000L, "2026-01-02");

            File file = write(HEADER + name + ",,MFX," + address + ",25,0:00:00,1960,0,DB,\n");
            List<String> failed = new ArrayList<>();
            final int unsettled = model.getUnsettledCount();

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty(), safely(failed, () -> click(name + ":REPLACE",
                    null)));

                assertEquals(failed, Collections.emptyList());
                assertTrue(model.getLocByName(name) == here && model.getLocByName(was) == null,
                    "Replace did not rename the locomotive here");
                assertEquals(here.getFunctionTypes(), new int[here.getNumF()], "Replace kept the functions");
                assertEquals(here.getPreferredSpeed() + " " + here.getStructuredNotes().getStartYear() + " "
                    + here.getStructuredNotes().getRailway() + " [" + here.getStructuredNotes().getNotes() + "]",
                    "25 1960 DB []", "Replace did not take the file's details");
                assertEquals(here.getTotalRuntime(), 3600000L, "Replace lost the running time");
                assertTrue(seen.said.get(0).contains(I18n.t("loc.ui.infoImportCsvReplacedOne"))
                    && !seen.said.get(0).contains(I18n.t("loc.ui.infoImportCsvRenamedOne"))
                    && seen.said.get(0).contains(I18n.f("loc.ui.infoImportCsvDone", 0, 0)),
                    "the message does not count the Replace apart from a Merge: " + seen.said);
                assertEquals(model.getUnsettledCount(), -1, "the Replace's rename did not mark the menu");
            }
            finally
            {
                model.setUnsettledCount(unsettled);
                file.delete();
                model.deleteLoc(name);
                model.deleteLoc(was);
            }
        });
    }

    /**
     * Merge and Replace are grayed, with the reason on hover beside "(can't apply)", where several locomotives here share
     * the address, where the file also lists the locomotive here, and where it is running or a route drives it; a second
     * row for one locomotive here is grayed once the first takes it (FR-249).  A row whose Merge is grayed starts on
     * Keep Both.  The Replace heading skips every grayed row; Ignore's sets every row.
     *
     * MUTATION: in `MarklinLocomotiveCsv.duplicates`, leave out the RUNNING reason; in `LocomotiveCsvImport.Duplicates`,
     * give Replace no reason, or let `setAll` pick a grayed choice, and this fails.
     */
    @Test
    public void testMergeAndReplaceAreGrayedWithTheirReasonAndFallBackToKeepBoth() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final String s1 = "FR249 door s1", s2 = "FR249 door s2", listed = "FR249 door listed", rolling = "FR249 door rolling";
            final String held = "FR249 door held", sNew = "FR249 door s new", lNew = "FR249 door l new";
            final String rNew = "FR249 door r new", t1 = "FR249 door t1", t2 = "FR249 door t2";
            final String driven = "FR249 door driven", dNew = "FR249 door d new", route = "FR249 door route";
            final int s = freeAddress(model, 13000);

            model.newMFXLocomotive(s1, s);
            model.newMFXLocomotive(s2, s);

            final int l = freeAddress(model, s - 1);

            model.newMFXLocomotive(listed, l);

            final int r = freeAddress(model, l - 1);
            Locomotive moving = model.newMFXLocomotive(rolling, r);
            final int t = freeAddress(model, r - 1);

            model.newMFXLocomotive(held, t);

            final int d = freeAddress(model, t - 1);
            RouteCommand fires = RouteCommand.RouteCommandFunction(driven, 1, true);

            model.newMFXLocomotive(driven, d);
            fires.setDelay(20000);
            model.newRoute(route, new ArrayList<>(Collections.singletonList(fires)), 0,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

            File file = write(HEADER + mfx(sNew, s) + mfx(lNew, l) + mfx(listed, l) + mfx(rNew, r) + mfx(dNew, d)
                + mfx(t1, t) + mfx(t2, t));
            List<String> failed = new ArrayList<>(), reasons = new ArrayList<>(), onReplace = new ArrayList<>();
            List<String> onIgnore = new ArrayList<>(), tookTip = new ArrayList<>(), start = new ArrayList<>();

            try
            {
                model.go();
                model.waitForPowerState(true);
                moving.setSpeed(30);
                moving.waitForSpeedAtOrAbove(1);
                new Thread(() -> model.execRoute(route)).start();
                support.Components.waitFor(() -> model.getRoute(route).isExecuting(), 5000);

                Map<String, String> before = everyLocomotive(model);

                press(ui, file, CANCEL, seen -> seen.answered, safely(failed, () ->
                {
                    String cant = I18n.t("sync.ui.cantApply");

                    start.addAll(picked());

                    for (javax.swing.JLabel mark : support.Components.all(asking, javax.swing.JLabel.class,
                        x -> cant.equals(x.getText()) && x.isVisible() && x.getName() == null)) reasons.add(mark.getToolTipText());

                    for (String row : new String[] {sNew, lNew, rNew, dNew, t2})
                    {
                        for (String answer : new String[] {":MERGE", ":REPLACE"})
                        {
                            javax.swing.AbstractButton b = support.Components.named(asking, row + answer,
                                javax.swing.AbstractButton.class);

                            if (b == null || b.isEnabled()) failed.add(row + answer + " is not grayed");
                            else if (row != t2) reasons.add(b.getToolTipText());
                        }
                    }

                    javax.swing.JLabel took = support.Components.named(asking, t2 + ":taken", javax.swing.JLabel.class);

                    tookTip.add(took.getText().equals(cant) + " " + took.getToolTipText());
                    click(null, I18n.t("loc.ui.importCsvReplace"));
                    onReplace.addAll(picked());
                    click(null, I18n.t("sync.ui.ignore"));
                    onIgnore.addAll(picked());
                }));

                String several = I18n.f("loc.ui.importCsvWhySeveral", "MFX " + s);
                String lists = I18n.f("loc.ui.importCsvWhyListed", listed);
                String runs = I18n.f("loc.ui.importCsvWhyRunning", rolling);
                String drives = I18n.f("loc.ui.importCsvWhyRunning", driven);

                assertEquals(failed, Collections.emptyList());
                assertEquals(start, java.util.Arrays.asList(sNew + ":BOTH", lNew + ":BOTH", rNew + ":BOTH", dNew + ":BOTH",
                    t1 + ":MERGE", t2 + ":BOTH"), "a row whose Merge is grayed or taken does not start on Keep Both");
                assertEquals(reasons, java.util.Arrays.asList(several, lists, runs, drives, several, several, lists, lists,
                    runs, runs, drives, drives), "each grayed Merge and Replace does not say why, beside (can't apply) and "
                    + "on hover");
                assertEquals(onReplace, java.util.Arrays.asList(sNew + ":BOTH", lNew + ":BOTH", rNew + ":BOTH",
                    dNew + ":BOTH", t1 + ":REPLACE", t2 + ":BOTH"),
                    "the Replace heading did not skip the grayed rows and the one taken");
                assertEquals(tookTip, Collections.singletonList("true " + I18n.f("loc.ui.importCsvWhyTaken", t1, held)),
                    "the row whose locomotive another row took does not say so");
                assertEquals(onIgnore, java.util.Arrays.asList(sNew + ":IGNORE", lNew + ":IGNORE", rNew + ":IGNORE",
                    dNew + ":IGNORE", t1 + ":IGNORE", t2 + ":IGNORE"), "the Ignore heading did not set every row");
                assertEquals(everyLocomotive(model), before, "Cancel imported something");
            }
            finally
            {
                moving.setSpeed(0);
                moving.waitForSpeedBelow(1);
                model.stop();
                model.waitForPowerState(false);
                file.delete();
                model.deleteRoute(route);

                for (String name : new String[] {s1, s2, listed, rolling, held, driven}) model.deleteLoc(name);
            }
        });
    }

    /**
     * "(can't apply)" beside a row's file name, where another row takes its locomotive, leaves every Merge and Replace
     * in view: no scroll bar shows, as the dialog was sized with the mark (FR-249).
     *
     * MUTATION: in `LocomotiveCsvImport.Duplicates.addRow`, leave out the mark's reserved width, and this fails.
     */
    @Test
    public void testATakenMarkLeavesEveryKeepNewInView() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final String here = "FR249 door holder", first = "FR249 door first", second = "FR249 door second";
            final int address = freeAddress(model, 13000);

            model.newMFXLocomotive(here, address);

            File file = write(HEADER + mfx(first, address) + mfx(second, address));
            List<String> failed = new ArrayList<>(), out = new ArrayList<>();

            try
            {
                press(ui, file, CANCEL, s -> s.answered, safely(failed, () ->
                {
                    click(first + ":REPLACE", null);
                    support.Components.settle();
                    out.addAll(support.Components.onTheEventThread(() ->
                    {
                        List<String> bad = new ArrayList<>();
                        org.traincontrol.gui.widgets.ChoiceTable table = support.Components.inside(asking,
                            org.traincontrol.gui.widgets.ChoiceTable.class);

                        if (table.getHorizontalScrollBar().isVisible()) bad.add("a scroll bar shows");

                        for (javax.swing.AbstractButton b : support.Components.all(asking, javax.swing.AbstractButton.class,
                            x -> x.getName() != null && x.getName().matches(".*:(MERGE|REPLACE)")))
                        {
                            if (!table.getViewport().getViewRect().contains(javax.swing.SwingUtilities.convertRectangle(
                                b.getParent(), b.getBounds(), table.getViewport().getView()))) bad.add(b.getName());
                        }

                        return bad;
                    }));
                }));

                assertEquals(failed, Collections.emptyList());
                assertEquals(out, Collections.emptyList(), "the taken mark pushed a Merge or Replace out of view");
            }
            finally
            {
                file.delete();
                model.deleteLoc(here);
            }
        });
    }

    /**
     * An answer that can no longer be carried out is said in the finishing message: a Merge with nothing left to rename
     * is added beside it, and an Ignore on a row no longer a duplicate is added, each named with why (FR-249).
     *
     * MUTATION: in `LocomotiveCsvImport.tell`, leave out the two lines, and this fails.
     */
    @Test
    public void testAnAnswerThatCannotBeCarriedOutIsSaidAtTheEnd() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final String holdsNew = "FR249 door holds new", holdsOld = "FR249 door holds old";
            final String keepNew = "FR249 door new one", keepOld = "FR249 door old one";
            final int a = freeAddress(model, 13000);

            model.newMFXLocomotive(holdsNew, a);

            final int b = freeAddress(model, a - 1);

            model.newMFXLocomotive(holdsOld, b);

            File file = write(HEADER + mfx(keepNew, a) + mfx(keepOld, b));
            List<String> failed = new ArrayList<>();

            try
            {
                Seen seen = press(ui, file, IMPORT, s -> !s.said.isEmpty(), safely(failed, () ->
                {
                    click(keepNew + ":MERGE", null);
                    click(keepOld + ":IGNORE", null);

                    // Behind the question, both rows stop being possible duplicates
                    model.deleteLoc(holdsNew);
                    model.deleteLoc(holdsOld);
                }));

                assertEquals(failed, Collections.emptyList());
                assertNotNull(model.getLocByName(keepNew), "the Merge row was not added");
                assertNotNull(model.getLocByName(keepOld), "the Ignore row was not added");
                assertTrue(seen.said.get(0).contains(I18n.f("loc.ui.infoImportCsvNotRenamed", keepNew + " ("
                    + I18n.t("loc.ui.importCsvNotGone") + ")")), "the message does not say the rename was not made: "
                    + seen.said);
                assertTrue(seen.said.get(0).contains(I18n.f("loc.ui.infoImportCsvNotLeftOut", keepOld)),
                    "the message does not say the row was not left out: " + seen.said);
            }
            finally
            {
                file.delete();

                for (String name : new String[] {holdsNew, holdsOld, keepNew, keepOld}) model.deleteLoc(name);
            }
        });
    }
}
