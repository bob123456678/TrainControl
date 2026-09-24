package regression;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuListener;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The Autonomy menu has a Documentation item, opening the guide the old tab's link opened (Adam, 2026-09-24).
 *
 * *"Add a 'documentation' link to the autonomy JMenu that points to the same URL as the current jlabel."*  The link sat
 * on the Load Autonomy Configuration tab, which goes (OB-254); the guide it opened, `TrainControlUI.README_URL`, is
 * about the autonomy the menu drives.
 *
 * MUTATION: leave the item out, or point it elsewhere, and the first claim fails; let the editor guard grey it with
 * the rest, and the second does.
 *
 * @author Adam
 */
public class testTheAutonomyMenuLinksItsDocumentation
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the menu is part of the window, and the window needs a display");
        }

        // BEFORE init, which opens whatever the layout preference names (OB-111).
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        for (int pass = 0; pass < 8; pass++) SwingUtilities.invokeAndWait(() -> { });
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
     * Opened, the Autonomy menu offers Documentation, and it can be chosen.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTheAutonomyMenuOffersDocumentation() throws Exception
    {
        List<String> items = new ArrayList<>();

        JMenuItem documentation = documentation(opened(), items);

        assertNotNull(documentation, "the Autonomy menu has no Documentation item (Adam, 2026-09-24).  Items: " + items);

        assertTrue(documentation.isEnabled(), "the Autonomy menu's Documentation item is greyed");

        // THE SAME PLACE THE OLD TAB'S LINK OPENED - asked of the source, since pressing it opens a browser.
        String code = new String(Files.readAllBytes(Paths.get("src/org/traincontrol/gui/AutonomyMenu.java")),
            StandardCharsets.UTF_8);

        assertTrue(code.contains("Util.openUrl(TrainControlUI.README_URL)"), "the Documentation item does not open"
            + " TrainControlUI.README_URL, the guide the old tab's link opened");

        // ON A THREAD OF ITS OWN (Adam, 2026-09-24, on MT-546: "can it get its own thread?").  Opening a browser is a
        // call into Windows, and the event thread has nothing to wait for it about.
        assertTrue(code.contains("new Thread(() -> Util.openUrl(TrainControlUI.README_URL)"), "the Documentation item"
            + " opens the guide on the event thread - Adam, 2026-09-24: \"can it get its own thread?\"");
    }

    /**
     * With an editor open, everything else on the menu is greyed and Documentation is not.
     *
     * The rest is greyed because each of those items saves the setup or rebuilds the window under an editor that has
     * not saved (MT-111).  Reading the guide does neither.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testDocumentationStaysUsableWhileAnEditorIsOpen() throws Exception
    {
        Field open = TrainControlUI.class.getDeclaredField("openEditor");

        open.setAccessible(true);

        final Object was = open.get(ui);
        final LayoutEditor[] built = new LayoutEditor[1];

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                built[0] = new LayoutEditor(model.getLayout(model.getLayoutList().get(0)), 30, ui, 0);

                built[0].render();
            });

            open.set(ui, built[0]);

            assertTrue(ui.isLayoutEditorOpen(), "precondition: the window does not see the editor as open");

            JMenu menu = opened();

            List<String> items = new ArrayList<>();
            List<String> greyed = new ArrayList<>();

            JMenuItem documentation = documentation(menu, items);

            for (int i = 0; i < menu.getItemCount(); i++)
            {
                JMenuItem item = menu.getItem(i);

                if (item != null && !item.isEnabled()) greyed.add(item.getText());
            }

            assertFalse(greyed.isEmpty(), "precondition: nothing on the menu was greyed, so the open editor's guard"
                + " did not run.  Items: " + items);

            assertNotNull(documentation, "with an editor open the Autonomy menu has no Documentation item.  Items: "
                + items);

            assertTrue(documentation.isEnabled(), "an open editor greys the Autonomy menu's Documentation item with the"
                + " rest - but reading the guide saves nothing and rebuilds nothing.  Greyed: " + greyed);
        }
        finally
        {
            open.set(ui, was);

            if (built[0] != null)
            {
                final LayoutEditor closing = built[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            // Built again without the editor, so the next claim finds the menu as a window with none open has it.
            opened();
        }
    }

    /**
     * The Autonomy menu, built as opening it builds it: from its own listener, when it is selected.
     */
    private static JMenu opened() throws Exception
    {
        Field field = TrainControlUI.class.getDeclaredField("autonomyMenu");

        field.setAccessible(true);

        final JMenu menu = (JMenu) field.get(ui);

        assertNotNull(menu, "precondition: the window has no Autonomy menu on a layout stored on this computer");

        SwingUtilities.invokeAndWait(() ->
        {
            for (MenuListener listener : menu.getMenuListeners()) listener.menuSelected(null);
        });

        return menu;
    }

    /**
     * The menu's Documentation item, or null; every item's text is added to `items` for the failure messages.
     */
    private static JMenuItem documentation(JMenu menu, List<String> items)
    {
        JMenuItem found = null;

        for (int i = 0; i < menu.getItemCount(); i++)
        {
            JMenuItem item = menu.getItem(i);

            if (item == null) continue;

            items.add(item.getText());

            if (I18n.t("ui.main.documentation").equals(item.getText())) found = item;
        }

        return found;
    }
}
