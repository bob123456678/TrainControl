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
 * MUTATION: leave the item out, or point it elsewhere, and this fails.
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
        Field field = TrainControlUI.class.getDeclaredField("autonomyMenu");

        field.setAccessible(true);

        final JMenu menu = (JMenu) field.get(ui);

        assertNotNull(menu, "precondition: the window has no Autonomy menu on a layout stored on this computer");

        // As opening it does: it is built when it is selected.
        SwingUtilities.invokeAndWait(() ->
        {
            for (MenuListener listener : menu.getMenuListeners()) listener.menuSelected(null);
        });

        List<String> items = new ArrayList<>();
        JMenuItem documentation = null;

        for (int i = 0; i < menu.getItemCount(); i++)
        {
            JMenuItem item = menu.getItem(i);

            if (item == null) continue;

            items.add(item.getText());

            if (I18n.t("ui.main.documentation").equals(item.getText())) documentation = item;
        }

        assertNotNull(documentation, "the Autonomy menu has no Documentation item (Adam, 2026-09-24).  Items: " + items);

        assertTrue(documentation.isEnabled(), "the Autonomy menu's Documentation item is greyed");

        // THE SAME PLACE THE OLD TAB'S LINK OPENED - asked of the source, since pressing it opens a browser.
        String code = new String(Files.readAllBytes(Paths.get("src/org/traincontrol/gui/AutonomyMenu.java")),
            StandardCharsets.UTF_8);

        assertTrue(code.contains("Util.openUrl(TrainControlUI.README_URL)"), "the Documentation item does not open"
            + " TrainControlUI.README_URL, the guide the old tab's link opened");
    }
}
