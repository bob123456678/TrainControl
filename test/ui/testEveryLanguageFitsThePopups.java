package ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RenameProposals;
import org.traincontrol.gui.ManualSync;
import org.traincontrol.gui.SyncDifferencesWindow;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.FrozenEditor;
import support.LanguageFit;
import support.Mouse;
import support.Reflect;
import support.SettingsWindow;
import support.StationDoors;
import support.SyncTable;

/**
 * `testEveryLanguageFits` for the program's popups and right-click menus, on the frozen railway.  Each popup is shown by
 * the program's own code in each language, measured by `support.LanguageFit` while it stands (its label and buttons,
 * and that it fits the screen), then answered; each right-click menu is held to the menu bar's rule.
 */
public class testEveryLanguageFitsThePopups
{
    private static final String PAGE = "1 - Main";

    private static final LanguageFit.Ledger LEDGER = new LanguageFit.Ledger();

    /** The widest line of a popup's text at 100%: `WrappingOptionPaneUI.CAP`, held here so the claim runs on old code. */
    private static final int CAP = 440;

    /** An 80-character Windows path with no spaces, wider than the cap on one line. */
    private static final String PATH = "C:\\Users\\Wilhelmina\\Documents\\TrainControl\\Modellbahn-Wohnzimmer\\MOBA_26-WM.json";

    private static LanguageFit.Railway railway;
    private static File files;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        org.traincontrol.gui.TrainControlUI.installLookAndFeel();
        railway = LanguageFit.Railway.open("live-snapshot");
        files = java.nio.file.Files.createTempDirectory("fit-popups").toFile();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            AskingWindows.closeEveryDialog();

            if (files != null)
            {
                for (File f : files.listFiles()) f.delete();

                files.delete();
            }
        }
        finally
        {
            if (railway != null) railway.close(LEDGER, "popups");
        }
    }

    /**
     * The rule reaches into a popup: a label fixed too narrow inside a question is found, one that fits is not.
     *
     * MUTATION: leave out the `lookAt` call in `Answering`'s watcher, and the first assertion fails.
     */
    @Test(timeOut = 60000)
    public void testThePopupRuleCanActuallyFail() throws Exception
    {
        assertFalse(questionHolding("Eine Konfigurationseinstellungsmoeglichkeit die viel zu lang ist").isEmpty(),
            "a 64-character label fixed at 80 pixels inside a question was called a fit, so the popups below are not "
            + "measured at all");

        assertTrue(questionHolding("OK").isEmpty(), "\"OK\" in 80 pixels was called clipped, so the rule refuses text "
            + "that fits");
    }

    /**
     * A long message wraps at the cap, a long path included: several sentences and an 80-character path with no spaces
     * lay out no wider than the cap plus the pane's insets, keeping the message's own line break, while short text stays
     * one line and HTML is shown as given (FR-254).
     *
     * MUTATION: have `WrappingOptionPaneUI.addMessageComponents` pass the text on unwrapped, and the first assertion fails.
     */
    @Test(timeOut = 60000)
    public void testALongMessageWrapsAtTheCap()
    {
        String text = "Backup saved to the folder you chose, but some of the files could not be copied. Check that "
            + "nothing else has them open, and try again. The file that failed was " + PATH + "\nNothing else was changed.";
        String html = "<html>" + text.replace("\n", " ") + "</html>";

        Components.onTheEventThread(() ->
        {
            javax.swing.JOptionPane pane = new javax.swing.JOptionPane(text, javax.swing.JOptionPane.PLAIN_MESSAGE);
            java.awt.Insets insets = pane.getInsets();
            int cap = com.formdev.flatlaf.util.UIScale.scale(CAP);

            assertEquals(PATH.length(), 80, "precondition: the path is not 80 characters");
            assertTrue(pane.getFontMetrics(javax.swing.UIManager.getFont("Label.font")).stringWidth(PATH) > cap,
                "precondition: the path alone fits within the cap, so it proves nothing about breaking one");

            assertTrue(pane.getPreferredSize().width <= cap + insets.left + insets.right, "the popup is "
                + pane.getPreferredSize().width + " px wide, over the " + cap + " px cap and its insets: " + linesOf(pane));
            assertEquals(linesOf(pane).get(linesOf(pane).size() - 1), "Nothing else was changed.",
                "the message's own line break was not kept: " + linesOf(pane));
            assertEquals(String.join("", linesOf(pane)).replace(" ", ""), text.replace("\n", "").replace(" ", ""),
                "the wrapped lines do not hold the whole message: " + linesOf(pane));
            assertTrue(linesOf(pane).stream().anyMatch(l -> l.endsWith("\\")), "the long path was not broken after a "
                + "separator: " + linesOf(pane));
            java.awt.FontMetrics fm = pane.getFontMetrics(pane.getFont());
            assertEquals(org.traincontrol.gui.widgets.WrappingOptionPaneUI.wrap("😀😀", fm, 1),
                "😀\n😀", "an emoji was split, or a width under one character failed");
            assertFalse(java.util.Arrays.asList(org.traincontrol.gui.widgets.WrappingOptionPaneUI.wrap("  abcdefghij", fm,
                fm.stringWidth("abc")).split("\n", -1)).contains(""), "an indent made an empty line");

            assertEquals(linesOf(new javax.swing.JOptionPane("Saved.")), Collections.singletonList("Saved."),
                "a short message was not left on one line");
            assertEquals(linesOf(new javax.swing.JOptionPane(html)), Collections.singletonList(html),
                "an HTML message was not shown as given");

            return null;
        });
    }

    /**
     * The rule reaches a table's column headings, which no component of their own draws: a long heading over a narrow
     * column is found, a short one is not.
     *
     * MUTATION: leave `tables` out of `LanguageFit.measure`, and the first assertion fails.
     */
    @Test(timeOut = 60000)
    public void testTheTableRuleCanActuallyFail() throws Exception
    {
        assertFalse(tableHeaded("Eine Konfigurationseinstellungsmoeglichkeit").isEmpty(),
            "a long heading over a 100-pixel column was called a fit, so no table heading below is measured");

        assertTrue(tableHeaded("A").isEmpty(), "a one-letter heading was called clipped");
    }

    /**
     * The popups 3.1.0 added or changed, and those the menus open, fit in every language: the failed sync with Retry
     * and Close, About, the update question, the backup question, the restore refusal, the CSV nothing-to-import
     * message, the setup a rename could not update, the editor-open refusals, Clear All's question and the Cancel
     * question that names the trains going back.
     * No line of their text, a failed sync's exception text and path included, is wider than the cap (FR-254).
     *
     * MUTATION: put a label of fixed preferred width 20 pixels into `AboutPanel`'s form, and this fails in every language.
     */
    @Test(timeOut = 300000)
    public void testThePopupsFitInEveryLanguage() throws Exception
    {
        File csv = new File(files, "nothing.csv");
        File notAnArchive = new File(files, "backup.zip");

        java.nio.file.Files.write(csv.toPath(), ("Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,"
            + "Start Year,End Year,RailwayName,Notes,LastRun\n").getBytes("UTF-8"));
        java.nio.file.Files.write(notAnArchive.toPath(), "not a zip".getBytes("UTF-8"));

        java.lang.reflect.Method syncRetry = Reflect.method(TrainControlUI.class, "offerSyncRetry", java.awt.Component.class,
            String.class, Runnable.class);
        java.lang.reflect.Method backupAsks = Reflect.method(org.traincontrol.gui.widgets.BackupAction.class,
            "askOnEventThread", String.class);
        java.lang.reflect.Method csvDoor = Reflect.method(Class.forName("org.traincontrol.gui.LocomotiveCsvImport"), "open",
            TrainControlUI.class, java.awt.Component.class);
        java.lang.reflect.Method linkRefused = Reflect.method(org.traincontrol.gui.RunGates.class,
            "refuseALinkOrSyncWhileEditorOpen", TrainControlUI.class, java.awt.Component.class);
        java.lang.reflect.Method clearAll = Reflect.method(org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.class,
            "clearAllPlacements");
        java.lang.reflect.Method cancel = Reflect.method(LayoutEditor.class, "confirmExit");
        java.lang.reflect.Method unrepaired = Reflect.method(org.traincontrol.gui.AutonomyLifecycle.class, "leftUnrepaired",
            String.class, String.class, List.class);
        java.lang.reflect.Method sayAfterRenames = Reflect.method(org.traincontrol.gui.AutonomyLifecycle.class,
            "sayAfterRenames", java.awt.Component.class, String.class, List.class, String.class);
        List<String> unnamed = new ArrayList<>();

        LanguageFit.assertNothingClipped("popups", LanguageFit.everyLanguage(language ->
        {
            List<String> found = new ArrayList<>();

            found.addAll(popup("popup-sync-failed", language, m -> Answering.pressAt(1), null,
                later(() -> syncRetry.invoke(railway.ui, railway.ui, null, (Runnable) () -> { })), null));
            found.addAll(popup("popup-sync-failed-exception", language, m -> Answering.pressAt(1), null,
                later(() -> syncRetry.invoke(railway.ui, railway.ui, "java.io.FileNotFoundException: " + PATH
                    + " (The process cannot access the file because it is being used by another process)",
                    (Runnable) () -> { })), null));
            found.addAll(popup("popup-about", language, m -> null, null, () ->
            {
                Components.press((javax.swing.AbstractButton) Reflect.get(TrainControlUI.class, railway.ui, "aboutMenuItem"));

                return null;
            }, null));
            found.addAll(popup("popup-update", language, m -> Answering.pressAt(0), null,
                later(() -> org.traincontrol.gui.widgets.UpdateFlow.askAboutTheUpdate(railway.ui, "9.9.9", "3.1.0", true)), null));
            found.addAll(popup("popup-backup", language, m -> Answering.pressAt(1), null,
                later(() -> backupAsks.invoke(new org.traincontrol.gui.widgets.BackupAction(railway.ui),
                    I18n.t("ui.askBackupIncludeCentralStationLayout"))), null));
            found.addAll(popup("popup-restore-refused", language, m -> null, notAnArchive,
                later(() -> { new org.traincontrol.gui.widgets.RestoreAction(railway.ui).restoreFromBackup(); return null; }), null));
            found.addAll(popup("popup-csv-nothing", language, m -> null, csv, later(() -> csvDoor.invoke(null, railway.ui, railway.ui)),
                null));
            found.addAll(popup("popup-rename-setup-not-updated", language, m -> Answering.pressAt(1), null, later(() ->
            {
                unrepaired.invoke(railway.ui.autonomyLifecycle(), "BR 103 137-6", "BR 103 137-6 Rheingold",
                    java.util.Arrays.asList("Main", "Weekend timetable"));
                return sayAfterRenames.invoke(railway.ui.autonomyLifecycle(), railway.ui, null, Collections.emptyList(),
                    I18n.t("sync.ui.title"));
            }), null));

            FrozenEditor.putBack(railway.ui, railway.session, railway.asOpened);

            LayoutEditor editor = FrozenEditor.editorOn(railway.model, railway.ui, railway.session, PAGE);

            try
            {
                Object panel = Components.onTheEventThread(editor::getAutonomyPanel);
                List<String> asked = new ArrayList<>();

                found.addAll(popup("popup-editor-open", language, m -> null, null,
                    later(() -> railway.ui.refuseWhileEditorOpen(railway.ui)), null));
                found.addAll(popup("popup-no-link-while-editing", language, m -> null, null,
                    later(() -> linkRefused.invoke(null, railway.ui, railway.ui)), null));
                found.addAll(popup("popup-clear-all", language, m -> Answering.press(AskingWindows.YES), null,
                    later(() -> clearAll.invoke(panel)), null));

                Components.settle();

                found.addAll(popup("popup-discard", language, m -> Answering.press(AskingWindows.YES), null,
                    later(() -> cancel.invoke(editor)), asked));

                assertTrue(Components.waitFor(() -> !editor.isDisplayable(), 20000), "Cancel did not close the editor");

                String plain = I18n.t("autosetup.ui.confirmExitWithoutSaving");

                if (asked.isEmpty() || !asked.get(0).startsWith(plain) || asked.get(0).length() <= plain.length())
                {
                    unnamed.add(language + ": " + asked);
                }
            }
            finally
            {
                FrozenEditor.dispose(editor);
                FrozenEditor.putBack(railway.ui, railway.session, railway.asOpened);
            }

            return found;
        }));

        assertTrue(unnamed.isEmpty(), "the Cancel question named no train, so the question that names them was not "
            + "measured: " + unnamed);
    }

    /**
     * The manual Sync's table and its longer finishing message fit in every language: the linked headings, a by-hand
     * row, a grayed rename, an address that collides, and the counts the message adds (FR-249).
     *
     * MUTATION: in `ChoiceTable.link`, fix a heading's preferred size at 20 by 20 pixels, and this fails in every language.
     */
    @Test(timeOut = 300000)
    public void testTheSyncTableAndItsMessageFitInEveryLanguage() throws Exception
    {
        MarklinLocomotive asked = new MarklinLocomotive(railway.model, 23, Locomotive.decoderType.MM2, "BR 218");
        MarklinLocomotive holder = new MarklinLocomotive(railway.model, 24, Locomotive.decoderType.MM2, "BR 218 007");
        MarklinLocomotive other = new MarklinLocomotive(railway.model, 25, Locomotive.decoderType.MM2, "BR 103");
        MarklinLocomotive mover = new MarklinLocomotive(railway.model, 26, Locomotive.decoderType.MM2, "BR 218 old");
        Map<Integer, List<MarklinLocomotive>> held = new HashMap<>();
        for (MarklinLocomotive l : new MarklinLocomotive[] {asked, holder, other, mover})
            held.put(l.getIntUID(), Collections.singletonList(l));

        LanguageFit.assertNothingClipped("sync table", LanguageFit.everyLanguage(language ->
        {
            List<String> found = Collections.synchronizedList(new ArrayList<>());
            // By hand; a rename grayed; an address held here; a rename onto a held name, Apply picked, saying what it deletes
            List<RenameProposals.Row> rows = java.util.Arrays.asList(new RenameProposals.Row(asked, holder.getName(),
                25, null, null, holder, null, asked.getIntUID(), other.getIntUID()), new RenameProposals.Row(other,
                holder.getName(), null, I18n.f("sync.ui.whyListedTwice", other.getName(), holder.getName()), null,
                holder, null, other.getIntUID(), other.getIntUID()), new RenameProposals.Row(holder, null, 23, null,
                null, null, null, holder.getIntUID(), asked.getIntUID()), new RenameProposals.Row(mover, holder.getName(),
                null, null, null, holder, null, mover.getIntUID(), mover.getIntUID()));
            RenameProposals proposals = new RenameProposals(new ArrayList<>(), 0, rows, held, false);

            try (SyncTable table = SyncTable.canceling(t ->
            {
                int[] seen = {0};
                t.in(mover.getName(), "RENAME", javax.swing.AbstractButton.class).doClick();
                for (String each : LanguageFit.measurePopup(LEDGER, "popup-sync-table", language, t.dialog, seen))
                    found.add("popup-sync-table: " + each);
            }))
            {
                SwingUtilities.invokeLater(() -> SyncDifferencesWindow.ask(railway.ui, proposals, null));
                assertTrue(Components.waitFor(() -> !table.shown().isEmpty(), 15000), "the Sync table showed nothing in "
                    + language);
                Components.settle();
            }
            found.addAll(popup("popup-sync-done", language, m -> null, null, later(() ->
            {
                JOptionPane.showMessageDialog(railway.ui, ManualSync.syncSummary(12, 2, true, 3, 2, 4, 1,
                    5));
                return null;
            }), null));
            return found;
        }));
    }

    /**
     * The CSV import's possible-duplicates table fits in every language: its headings, a row's gray "(can't apply)" and the
     * one a Merge or Replace puts on the row that wanted the same locomotive (FR-249).
     *
     * MUTATION: in `ChoiceTable.link`, fix a heading's preferred size at 20 by 20 pixels, and this fails in every language.
     */
    @Test(timeOut = 300000)
    public void testTheCsvDuplicatesTableFitsInEveryLanguage() throws Exception
    {
        Locomotive held = railway.model.getLocomotives().stream().filter(l -> !l.hasLinkedLocomotives()
            && l.getDecoderType() != Locomotive.decoderType.MULTI_UNIT).findFirst().get();
        String row = ",,{0},{1},0,0:00:00,0,0,,\n";
        File csv = new File(files, "duplicates.csv");

        java.nio.file.Files.write(csv.toPath(), ("Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,"
            + "Start Year,End Year,RailwayName,Notes,LastRun\n" + "Fit first" + row + "Fit second" + row).replace("{0}",
            held.getDecoderType().name()).replace("{1}", String.valueOf(held.getAddress())).getBytes("UTF-8"));

        java.lang.reflect.Method csvDoor = Reflect.method(Class.forName("org.traincontrol.gui.LocomotiveCsvImport"), "open",
            TrainControlUI.class, java.awt.Component.class);

        LanguageFit.assertNothingClipped("csv duplicates", LanguageFit.everyLanguage(language -> popup("popup-csv-duplicates",
            language, m -> Answering.pressAt(1), csv, later(() -> csvDoor.invoke(null, railway.ui, railway.ui)), null,
            dialog -> Components.all(dialog, javax.swing.AbstractButton.class, b -> "Fit first:REPLACE".equals(b.getName())
            && b.isEnabled()).forEach(javax.swing.AbstractButton::doClick))));
    }

    /**
     * The right-click menus 3.1.0 added or changed fit the menu bar's rule in every language: the diagram's menu over a
     * train and over an empty station, and the locomotive key's menu.
     *
     * MUTATION: give an item of `DiagramAutonomyContextMenu` a preferred width of 2000 pixels, and this fails.
     */
    @Test(timeOut = 240000)
    public void testTheRightClickMenusFitInEveryLanguage() throws Exception
    {
        FrozenEditor.putBack(railway.ui, railway.session, railway.asOpened);

        TileKey train = squareWhere(true);
        TileKey empty = squareWhere(false);
        org.traincontrol.base.Locomotive loc = railway.model.getLocByName(railway.session.trainsWhereTheyStand().get(train));
        javax.swing.JButton key = SettingsWindow.showOnKeyA(railway.ui, loc);
        int ceiling = Components.onTheEventThread(() -> LanguageFit.menuCeiling(railway.ui));

        LanguageFit.assertNothingClipped("right-click-menus", LanguageFit.everyLanguage(language ->
        {
            List<String> found = new ArrayList<>();

            for (TileKey square : new TileKey[] {train, empty})
            {
                JPopupMenu menu = StationDoors.diagramMenu(railway.ui, square);

                assertFalse(Components.onTheEventThread(() -> LanguageFit.popupMenu(menu, "of the diagram at " + square,
                    ceiling, found)).isEmpty(), "the diagram's menu at " + square + " says nothing");
            }

            JPopupMenu keyMenu = SettingsWindow.rightClickKey(key);

            try
            {
                assertNotNull(keyMenu, "the key's right-click menu did not open");
                assertFalse(Components.onTheEventThread(() -> LanguageFit.popupMenu(keyMenu, "of the locomotive key",
                    ceiling, found)).isEmpty(), "the key's menu says nothing");
            }
            finally
            {
                Mouse.closeMenus();
            }

            return found;
        }));
    }

    /**
     * Shows the popup `door` makes, measures it as it stands and answers it by `rule` (picking `file` in a file chooser,
     * when given); returns what does not fit, and adds what it said to `asked` when given.
     */
    private static List<String> popup(String window, String language, Function<String, Answering.Answer> rule, File file,
        Callable<?> door, List<String> asked) throws Exception
    {
        return popup(window, language, rule, file, door, asked, d -> { });
    }

    /** The same, running `first` on the event thread as the popup appears, before it is measured. */
    private static List<String> popup(String window, String language, Function<String, Answering.Answer> rule, File file,
        Callable<?> door, List<String> asked, java.util.function.Consumer<javax.swing.JDialog> first) throws Exception
    {
        List<String> found = Collections.synchronizedList(new ArrayList<>());
        int[] count = {0};

        try (Answering answering = Answering.start(rule).choosing(file).looking(dialog ->
        {
            String id = window + (++count[0] > 1 ? "-" + count[0] : "");
            int[] seen = {0};

            first.accept(dialog);

            for (String each : LanguageFit.measurePopup(LEDGER, id, language, dialog, seen)) found.add(id + ": " + each);

            if (seen[0] == 0) found.add(id + ": nothing with text was measured, so the dialog was not laid out");

            int cap = com.formdev.flatlaf.util.UIScale.scale(CAP);

            for (javax.swing.JLabel line : Components.all(dialog, javax.swing.JLabel.class,
                l -> "OptionPane.label".equals(l.getName()) && l.getPreferredSize().width > cap))
            {
                found.add(id + ": \"" + line.getText() + "\" is " + line.getPreferredSize().width + " px, over the "
                    + cap + " px cap");
            }
        }))
        {
            door.call();

            assertTrue(answering.awaitAsked(1, 15000), window + " showed nothing in " + language);

            Components.settle();

            if (asked != null) asked.addAll(answering.asked());
        }

        assertEquals(count[0] > 0, true, window + " was asked but never measured");

        return found;
    }

    /** The lines a popup's text is laid out on, one label each. */
    private static List<String> linesOf(javax.swing.JOptionPane pane)
    {
        List<String> lines = new ArrayList<>();

        for (javax.swing.JLabel line : Components.all(pane, javax.swing.JLabel.class,
            l -> "OptionPane.label".equals(l.getName()))) lines.add(line.getText());

        return lines;
    }

    /** A door that does this on the event thread later, as a click does, so its modal dialog does not hold the caller. */
    private static Callable<Object> later(Callable<?> work)
    {
        return () ->
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    work.call();
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });

            return null;
        };
    }

    /** What the rule finds in a question holding one label fixed at 80 pixels wide. */
    private static List<String> questionHolding(String text) throws Exception
    {
        javax.swing.JLabel label = new javax.swing.JLabel(text);

        label.setPreferredSize(new java.awt.Dimension(80, 20));

        List<String> found = Collections.synchronizedList(new ArrayList<>());

        try (Answering answering = Answering.start(m -> null).looking(dialog ->
            found.addAll(LanguageFit.measurePopup(null, "control", "en", dialog, new int[1]))))
        {
            SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(null, label));

            assertTrue(answering.awaitAsked(1, 15000), "the question was not shown");

            Components.settle();
        }

        return found;
    }

    /** What the rule finds in a window holding a one-column table 100 pixels wide under this heading. */
    private static List<String> tableHeaded(String heading)
    {
        return Components.onTheEventThread(() ->
        {
            javax.swing.JFrame frame = new javax.swing.JFrame();

            try
            {
                javax.swing.JTable table = new javax.swing.JTable(new Object[1][1], new Object[] {heading});

                table.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
                table.getColumnModel().getColumn(0).setPreferredWidth(100);
                table.getColumnModel().getColumn(0).setWidth(100);

                frame.getContentPane().add(new javax.swing.JScrollPane(table));
                frame.pack();
                frame.setSize(300, 200);
                frame.validate();

                return LanguageFit.measure(null, "control-table", "en", frame, new int[1]);
            }
            finally
            {
                frame.dispose();
            }
        });
    }

    /** A station on the page with the setup's own train on it, or an empty one. */
    private static TileKey squareWhere(boolean withATrain)
    {
        for (TileKey each : railway.session.getReducer().getPoints().keySet())
        {
            if (!PAGE.equals(each.getPage()) || !railway.session.getStore().isStation(each)) continue;

            String standing = railway.session.getLocomotiveNameAt(each);

            if (withATrain ? standing != null && standing.equals(railway.session.trainsWhereTheyStand().get(each))
                : standing == null) return each;
        }

        throw new AssertionError("precondition: no station " + (withATrain ? "with" : "without") + " a train on " + PAGE);
    }
}
