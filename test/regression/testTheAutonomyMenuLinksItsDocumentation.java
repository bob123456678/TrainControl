package regression;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuListener;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.Display;
import support.Reflect;
import support.Teardown;

/**
 * The Autonomy menu has no guide item: Help has it (OB-468), and an open editor grays everything else on the menu.
 *
 * MUTATION: put the Autonomy Guide item back in `AutonomyMenu.rebuild`, and the first claim fails; leave an item live
 * under an open editor, and the second does.
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
        Display.needed("the menu is part of the window, and the window needs a display");

        // BEFORE init, which opens whatever the layout preference names (OB-111).
        support.FrozenEditor frozen = support.FrozenEditor.window("single-switch");

        sandbox = frozen.sandbox;
        model = frozen.model;
        ui = frozen.ui;

        for (int pass = 0; pass < 8; pass++) SwingUtilities.invokeAndWait(() -> { });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        Teardown.close(sandbox, model, ui);
    }

    /** Opened, the Autonomy menu offers items but not the guide, which Help holds (OB-468). */
    @Test
    public void testTheAutonomyMenuHasNoGuideItem() throws Exception
    {
        List<String> items = texts(opened());

        assertFalse(items.isEmpty(), "precondition: the Autonomy menu is empty");

        assertFalse(items.contains(I18n.t("ui.menu.autonomyGuide")), "the Autonomy menu offers the guide, which is in"
            + " Help only.  Items: " + items);
    }

    /** With an editor open, nothing on the menu is live: each item saves the setup or rebuilds the window (MT-111). */
    @Test
    public void testEveryItemIsGrayedWhileAnEditorIsOpen() throws Exception
    {
        Field open = Reflect.field(TrainControlUI.class, "openEditor");

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

            List<String> live = new ArrayList<>();

            for (int i = 0; i < menu.getItemCount(); i++)
            {
                JMenuItem item = menu.getItem(i);

                // Go to the Opened Editor is the one thing that can be done
                if (item != null && item.isEnabled() && !I18n.t("autosetup.ui.menuGoToOpenEditor").equals(item.getText()))
                {
                    live.add(item.getText());
                }
            }

            assertTrue(live.isEmpty(), "an open editor leaves these Autonomy menu items live: " + live);
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

    /** The Autonomy menu, built as opening it builds it: from its own listener, when it is selected. */
    private static JMenu opened() throws Exception
    {
        final JMenu menu = (JMenu) Reflect.get(TrainControlUI.class, ui, "autonomyMenu");

        assertNotNull(menu, "precondition: the window has no Autonomy menu on a layout stored on this computer");

        SwingUtilities.invokeAndWait(() ->
        {
            for (MenuListener listener : menu.getMenuListeners()) listener.menuSelected(null);
        });

        return menu;
    }

    /** The text of every item on the menu. */
    private static List<String> texts(JMenu menu)
    {
        List<String> items = new ArrayList<>();

        for (int i = 0; i < menu.getItemCount(); i++)
        {
            if (menu.getItem(i) != null) items.add(menu.getItem(i).getText());
        }

        return items;
    }
}
