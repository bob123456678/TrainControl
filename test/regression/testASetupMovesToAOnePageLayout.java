package regression;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuListener;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;

/**
 * His setup, exported from the frozen copy of his railway, imported onto the one-page layout MT-380 ships - through the
 * Autonomy menu and the autonomy editor, as MT-512, MT-513 and MT-514 do it in one sitting (Adam, 2026-09-25: automated
 * tests supersede the MTs they answer).
 *
 * The one-page layout is copied into a sandbox first.  The steps point TrainControl at the folder in the repository
 * itself, and the import then saves a setup into it - after which the shipped layout is no longer one with nothing set
 * up, and `testTheSuppliedManualTestFilesAreWhatTheySay` says so.
 *
 * @author Adam
 */
public class testASetupMovesToAOnePageLayout
{
    /** The layout MT-380's files ship, with one page, 1 - Main. */
    private static final File ONE_PAGE = new File("docs/manual-tests/files/MT-380-one-page-layout/layout");

    /**
     * MT-512: on the one-page layout with nothing set up, the Autonomy menu offers Import... live.  MT-513: importing his
     * export gives ONE warning, naming the four pages this layout does not have.  MT-514: after it, opening the autonomy
     * editor, closing it and opening it again says nothing about the setup being left alone.
     *
     * MUTATION: grey Import on a layout with nothing set up, warn once per page, or let the save after the import be
     * declined, and this fails.
     *
     * @throws Exception from the windows or the files
     */
    @Test
    public void testHisSetupGoesOntoTheOnePageLayout() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        assertTrue(new File(ONE_PAGE, "config/gleisbild.cs2").isFile(), "precondition: MT-380's one-page layout is not in"
            + " docs/manual-tests/files");

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        File exported = File.createTempFile("tc-his-setup", ".json");

        List<String> hisPages = new ArrayList<>();

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        Answerer answerer = null;

        try
        {
            // STEP 1: HIS EXPORT, as Autonomy > Export writes it - the configuration in use, bundled, as indented JSON.
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            MarklinControlStation his = MarklinControlStation.init(null, true, false, false, false);

            try
            {
                AutonomySession session = new AutonomySession(sandbox.getFolder());

                session.open(support.LayoutSandbox.wiredPages(his));

                for (org.traincontrol.base.LayoutDiagram page : session.getPages()) hisPages.add(page.getName());

                org.json.JSONObject bundle = session.getStore().exportBundle(session.getStore().getActiveConfiguration());

                assertNotNull(bundle, "precondition: the frozen railway's configuration in use could not be exported");

                Files.write(exported.toPath(), bundle.toString(4).getBytes(StandardCharsets.UTF_8));
            }
            finally
            {
                his.stop();
            }

            sandbox.close();

            sandbox = null;

            assertTrue(hisPages.contains("1 - Main") && hisPages.size() == 5, "precondition: the frozen railway does not"
                + " have the five pages the steps assume: " + hisPages);

            // AND THE ONE-PAGE LAYOUT AS THE LOCAL LAYOUT - a copy of it.
            sandbox = support.LayoutSandbox.open(ONE_PAGE);

            ui[0] = openTheWindow();

            answerer = new Answerer(exported);

            Thread answering = new Thread(answerer, "one-page import answerer");

            answering.setDaemon(true);
            answering.start();

            // MT-512: THE AUTONOMY MENU, opened.
            final JMenu menu = theAutonomySlot(ui[0]);

            SwingUtilities.invokeAndWait(() ->
            {
                for (MenuListener listener : menu.getMenuListeners()) listener.menuSelected(null);
            });

            JMenuItem importItem = null;
            List<String> items = new ArrayList<>();

            for (int i = 0; i < menu.getItemCount(); i++)
            {
                JMenuItem item = menu.getItem(i);

                if (item == null) continue;

                items.add(item.getText());

                if (I18n.t("autosetup.ui.btnImportConfiguration").equals(item.getText())) importItem = item;
            }

            assertNotNull(importItem, "on a layout with nothing set up, the Autonomy menu has no Import... (MT-512): "
                + items);

            assertTrue(importItem.isEnabled(), "on a layout with nothing set up, the Autonomy menu's Import... is greyed"
                + " (MT-512)");

            // MT-513: IMPORT..., his file.
            final JMenuItem importing = importItem;

            SwingUtilities.invokeLater(importing::doClick);

            String imported = I18n.t("autosetup.ui.infoImported");

            imported = imported.substring(0, imported.indexOf('{'));

            for (long end = System.currentTimeMillis() + 180000; !answerer.saidStarting(imported)
                && System.currentTimeMillis() < end; ) Thread.sleep(100);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(answerer.chose && answerer.named, "precondition: the import did not show the file chooser and ask"
                + " for a name");

            assertTrue(answerer.saidStarting(imported), "the import never said it had imported: " + answerer.seen);

            List<String> warnings = answerer.titled(I18n.t("autosetup.ui.titleImportPagesNotHere"));

            assertEquals(warnings.size(), 1, "importing his five-page setup onto the one-page layout did not warn exactly"
                + " once (MT-513): " + answerer.seen);

            List<String> notHere = new ArrayList<>(hisPages);

            notHere.remove("1 - Main");

            for (String page : notHere)
            {
                assertTrue(warnings.get(0).contains(page), "the warning does not name " + page + ", a page this layout"
                    + " does not have (MT-513): " + warnings.get(0));
            }

            assertFalse(warnings.get(0).contains("1 - Main"), "the warning names 1 - Main, the page this layout has"
                + " (MT-513): " + warnings.get(0));

            String leftAlone = I18n.t("autosetup.ui.titleSetupNotTidied");

            assertTrue(answerer.titled(leftAlone).isEmpty(), "the import said the setup was left alone: " + answerer.seen);

            // MT-514: THE AUTONOMY EDITOR OPENED, CLOSED AND OPENED AGAIN - with what has been said forgotten first, so a
            // warning already given once in this run cannot be what stays quiet.
            forgetWhatWasSaid();

            for (int opening = 1; opening <= 2; opening++)
            {
                openAndCloseTheEditor(ui[0], "1 - Main");

                assertTrue(answerer.titled(leftAlone).isEmpty(), "opening the autonomy editor after the import (time "
                    + opening + ") said the setup was left alone (MT-514): " + answerer.seen);
            }
        }
        finally
        {
            if (answerer != null) answerer.running = false;

            if (folderWas == null) TrainControlUI.getPrefs().remove(TrainControlUI.LAST_USED_FOLDER);
            else TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();

            if (!exported.delete()) exported.deleteOnExit();
        }
    }

    // ---------------------------------------------------------------- the doors

    /** Autonomy > Edit Autonomy > the page, then the editor's own close box. */
    private static void openAndCloseTheEditor(TrainControlUI ui, String page) throws Exception
    {
        SwingUtilities.invokeLater(() -> ui.openAutonomyEditorOnPage(page));

        Window editor = null;

        for (long end = System.currentTimeMillis() + 60000; editor == null && System.currentTimeMillis() < end; )
        {
            Thread.sleep(100);

            for (Window window : Window.getWindows())
            {
                if (window.isShowing() && "LayoutEditor".equals(window.getClass().getSimpleName())) editor = window;
            }
        }

        assertNotNull(editor, "the autonomy editor did not open on " + page);

        for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

        Thread.sleep(500);

        final Window closing = editor;

        SwingUtilities.invokeAndWait(() -> closing.dispatchEvent(
            new java.awt.event.WindowEvent(closing, java.awt.event.WindowEvent.WINDOW_CLOSING)));

        for (long end = System.currentTimeMillis() + 30000; closing.isShowing() && System.currentTimeMillis() < end; )
        {
            Thread.sleep(100);
        }

        assertFalse(closing.isShowing(), "the autonomy editor did not close");

        for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });
    }

    /** The "left alone" message is said once per set of pages per run; forgotten, so the next opening can say it. */
    @SuppressWarnings("unchecked")
    private static void forgetWhatWasSaid() throws Exception
    {
        java.lang.reflect.Field said = Class.forName("org.traincontrol.gui.AutonomyReport").getDeclaredField("ALREADY_SAID");

        said.setAccessible(true);

        ((Set<String>) said.get(null)).clear();
    }

    private static TrainControlUI openTheWindow() throws Exception
    {
        MarklinControlStation model = MarklinControlStation.init(null, true, false, false, true);

        final TrainControlUI[] made = new TrainControlUI[1];

        SwingUtilities.invokeAndWait(() -> made[0] = new TrainControlUI());

        made[0].setViewListener(model, new CountDownLatch(1));

        final CountDownLatch settled = new CountDownLatch(1);

        made[0].whenTilesSettled(() -> settled.countDown());

        settled.await(30, TimeUnit.SECONDS);

        for (int turn = 0; turn < 4; turn++) SwingUtilities.invokeAndWait(() -> { });

        return made[0];
    }

    /** Whatever stands in the menu bar's Autonomy slot. */
    private static JMenu theAutonomySlot(TrainControlUI ui) throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("mainMenuBar");

        field.setAccessible(true);

        JMenuBar bar = (JMenuBar) field.get(ui);

        String heading = I18n.t("autosetup.ui.menuAutonomy");

        for (int i = 0; i < bar.getMenuCount(); i++)
        {
            JMenu menu = bar.getMenu(i);

            if (menu != null && heading.equals(menu.getText())) return menu;
        }

        throw new AssertionError("precondition: the menu bar has no Autonomy menu at all");
    }

    /** Answers every dialog as it appears - the file, the name as suggested, the first button - and remembers them. */
    private static final class Answerer implements Runnable
    {
        private final File file;
        private final List<String[]> seen = Collections.synchronizedList(new ArrayList<>());
        private final Set<Object> handled = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private volatile boolean running = true;
        private volatile boolean chose = false;
        private volatile boolean named = false;

        Answerer(File file)
        {
            this.file = file;
        }

        @Override
        public void run()
        {
            while (running)
            {
                try
                {
                    Thread.sleep(150);
                }
                catch (InterruptedException stop)
                {
                    return;
                }

                for (Window window : Window.getWindows())
                {
                    if (!window.isShowing() || !(window instanceof JDialog)) continue;

                    final JDialog dialog = (JDialog) window;

                    final JFileChooser chooser = find(dialog.getContentPane(), JFileChooser.class);

                    if (chooser != null)
                    {
                        if (!handled.add(chooser)) continue;

                        chose = true;

                        SwingUtilities.invokeLater(() ->
                        {
                            chooser.setSelectedFile(file);
                            chooser.approveSelection();
                        });

                        continue;
                    }

                    final JOptionPane pane = find(dialog.getContentPane(), JOptionPane.class);

                    if (pane == null || !handled.add(pane)) continue;

                    String text = String.valueOf(pane.getMessage());

                    seen.add(new String[] {String.valueOf(dialog.getTitle()), text});

                    if (I18n.t("autosetup.ui.promptImportName").equals(text))
                    {
                        named = true;

                        // THE NAME AS SUGGESTED, pressing OK.
                        SwingUtilities.invokeLater(() ->
                        {
                            pane.setInputValue(pane.getInitialSelectionValue());
                            pane.setValue(Integer.valueOf(JOptionPane.OK_OPTION));
                        });

                        continue;
                    }

                    final Object[] options = pane.getOptions();

                    SwingUtilities.invokeLater(() -> pane.setValue(options != null && options.length > 0 ? options[0]
                        : Integer.valueOf(JOptionPane.OK_OPTION)));
                }
            }
        }

        List<String> titled(String title)
        {
            List<String> out = new ArrayList<>();

            synchronized (seen)
            {
                for (String[] one : seen) if (title.equals(one[0])) out.add(one[1]);
            }

            return out;
        }

        boolean saidStarting(String start)
        {
            synchronized (seen)
            {
                for (String[] one : seen) if (one[1].startsWith(start)) return true;
            }

            return false;
        }
    }

    private static <T> T find(Container container, Class<T> type)
    {
        for (Component child : container.getComponents())
        {
            if (type.isInstance(child)) return type.cast(child);

            if (child instanceof Container)
            {
                T found = find((Container) child, type);

                if (found != null) return found;
            }
        }

        return null;
    }
}
