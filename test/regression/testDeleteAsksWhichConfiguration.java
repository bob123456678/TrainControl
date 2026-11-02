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

/**
 * Autonomy > Manage > Delete asks which configuration, and is offered while none is loaded (OB-319).  Here nothing is
 * loaded - as after the refusal of a configuration whose setup cannot be used - and the configuration deleted is not
 * the one the store names as active.
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
     * MUTATION: gray Delete while nothing is loaded, or delete the configuration in use without asking, and this fails.
     */
    @Test
    public void testANotLoadedConfigurationCanBeChosenAndDeleted() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        final TrainControlUI[] ui = new TrainControlUI[1];

        final support.SandboxedWindow sandboxed = new support.SandboxedWindow();

        AtomicBoolean going = new AtomicBoolean(true);

        try
        {
            sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot")));

            ui[0] = sandboxed.hold(openTheWindow());

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

            sandboxed.close();
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

            support.DialogWatch.until(going::get, 150, dialog ->
            {
                for (Component c : dialog.getContentPane().getComponents())
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
            });
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
