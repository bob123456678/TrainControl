package regression;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuListener;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import static regression.testTheImportDoorReadsAnOldFile.itemCalled;
import static regression.testTheImportDoorReadsAnOldFile.openTheWindow;
import static regression.testTheImportDoorReadsAnOldFile.putTheFolderBack;

/**
 * Autonomy > Manage > Delete asks which configuration, and is offered while none is loaded (OB-319; Adam, 2026-10-04:
 * *"There seems to be a bug with autonomy config management. If there is a setup cant be used, it cant be selected from
 * the list of configs, which prevents subsequent deletion."*).
 *
 * Delete acted on the configuration in use and was greyed until one was loaded; a configuration whose setup cannot be
 * used is refused at its load, so it never became the one in use and could not be deleted.  Here nothing is loaded - as
 * after that refusal - and the configuration deleted is not the one the store names as active.
 *
 * On a copy of the frozen railway, because a delete removes a file.
 *
 * @author Adam
 */
public class testDeleteAsksWhichConfiguration
{
    /** The configuration made to be deleted. */
    private static final String SPARE = "OB-319 spare";

    /**
     * With nothing loaded, Delete is offered; it asks which configuration, offering every one; and it deletes the one
     * chosen and no other (OB-319).
     *
     * MUTATION: grey Delete while nothing is loaded, or delete the configuration in use without asking, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testANotLoadedConfigurationCanBeChosenAndDeleted() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        AtomicBoolean going = new AtomicBoolean(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            // NOTHING LOADED, as after a configuration whose setup would not load
            final boolean[] unloaded = new boolean[1];

            SwingUtilities.invokeAndWait(() -> unloaded[0] = ui[0].unloadAutonomy());

            assertTrue(unloaded[0], "precondition: autonomy could not be unloaded");

            assertNull(ui[0].getActiveDiagramConfiguration(), "precondition: a configuration is still loaded");

            AutonomySession session = ui[0].getAutonomySession();

            assertNotNull(session, "precondition: the window has no autonomy session for the railway");

            String kept = session.getStore().getActiveConfiguration();

            assertNotNull(kept, "precondition: the frozen railway has no configuration");

            session.getStore().createConfiguration(SPARE, kept);
            session.getStore().save();

            // THE MENU OFFERS DELETE
            final JMenu menu = theAutonomySlot(ui[0]);

            SwingUtilities.invokeAndWait(() ->
            {
                for (MenuListener listener : menu.getMenuListeners()) listener.menuSelected(null);
            });

            final JMenuItem delete = itemCalled(menu, I18n.t("autosetup.ui.menuDeleteConfiguration"));

            assertNotNull(delete, "the Autonomy menu has no Delete");

            assertTrue(delete.isEnabled(), "with nothing loaded, Delete is greyed, so a configuration that cannot be"
                + " loaded cannot be deleted (OB-319)");

            // IT ASKS WHICH
            final List<List<Object>> offered = Collections.synchronizedList(new ArrayList<List<Object>>());
            final List<String> asked = Collections.synchronizedList(new ArrayList<String>());

            answering(going, offered, asked);

            SwingUtilities.invokeLater(delete::doClick);

            for (long end = System.currentTimeMillis() + 60000; session.getStore().getConfigurationNames().contains(SPARE)
                && System.currentTimeMillis() < end; ) Thread.sleep(100);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertEquals(offered.size(), 1, "Delete did not ask once which configuration: " + asked);

            assertTrue(offered.get(0).containsAll(Arrays.asList(kept, SPARE)), "Delete's question does not offer both"
                + " configurations: " + offered.get(0));

            assertFalse(session.getStore().getConfigurationNames().contains(SPARE), "the configuration chosen was not"
                + " deleted (OB-319): " + asked);

            assertTrue(session.getStore().getConfigurationNames().contains(kept), "Delete deleted " + kept + ", which was"
                + " not the one chosen");
        }
        finally
        {
            going.set(false);

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Answers every question until `going` is lowered: a choice from a list with the spare configuration, anything else
     * with its first button - and the choices offered and the words of each.
     */
    private static void answering(final AtomicBoolean going, final List<List<Object>> offered, final List<String> asked)
    {
        Thread answering = new Thread(() ->
        {
            java.util.Set<Object> handled = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

            while (going.get())
            {
                try
                {
                    Thread.sleep(150);
                }
                catch (InterruptedException stop)
                {
                    return;
                }

                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (!window.isShowing() || !(window instanceof javax.swing.JDialog)) continue;

                    for (Component c : ((javax.swing.JDialog) window).getContentPane().getComponents())
                    {
                        if (!(c instanceof JOptionPane) || !handled.add(c)) continue;

                        final JOptionPane pane = (JOptionPane) c;

                        asked.add(String.valueOf(pane.getMessage()));

                        if (pane.getWantsInput() && pane.getSelectionValues() != null)
                        {
                            offered.add(new ArrayList<>(Arrays.asList(pane.getSelectionValues())));
                        }

                        SwingUtilities.invokeLater(() ->
                        {
                            if (pane.getWantsInput())
                            {
                                pane.setInputValue(SPARE);
                                pane.setValue(Integer.valueOf(JOptionPane.OK_OPTION));
                            }
                            else
                            {
                                Object[] options = pane.getOptions();

                                pane.setValue(options != null && options.length > 0 ? options[0]
                                    : Integer.valueOf(JOptionPane.OK_OPTION));
                            }
                        });
                    }
                }
            }
        }, "delete door answerer");

        answering.setDaemon(true);
        answering.start();
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
}
