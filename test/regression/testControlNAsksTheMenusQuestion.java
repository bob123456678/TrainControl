package regression;

import java.util.Arrays;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.util.I18n;

/**
 * Control+N offers a station name exactly where the right-click menu does (MFR-B3).
 *
 * FR-086 (Adam, on MT-397, 2026-09-15): *"let's add a hotkey for 'show station name here' too"*.  The key's javadoc
 * says it asks what the menu asks; it did not ask `isIgnored`, so on an ignored square - a page left out of autonomy,
 * say - it opened the station chooser and wrote a caption where the menu offers nothing but the bulk tools.  The shape
 * `testControlEAsksTheMenusQuestion` already holds for Control+E: the guard and the affordance asking one question,
 * put side by side through a public predicate because the key itself opens a modal dialog.
 *
 * Read-only: its own diagram in a temporary folder.
 *
 * @author Adam
 */
public class testControlNAsksTheMenusQuestion
{
    /**
     * On plain track the menu offers a name and so does the key; on the same track on an ignored page, neither does.
     *
     * @throws Exception from the session or the reflection
     */
    @Test
    public void testTheKeyOffersNoNameWhereTheMenuOffersNone() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-n").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithARun("main")));

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            TileKey track = new TileKey("main", 3, 1);

            // THE CONTROL: a square the menu offers a name on, and the key agrees.
            javax.swing.JPopupMenu menu = panel.buildTileMenu(track, null);

            assertNotNull(menu, "precondition: no menu on the plain track at " + track);

            assertTrue(offersAName(menu), "precondition: the menu on plain track offers no station name - "
                + itemNames(menu) + " - so there is nothing for the key to agree with");

            assertTrue(keyOffersAName(panel, track),
                "the key offers no station name on plain track where the menu does: " + itemNames(menu));

            // AN IGNORED SQUARE: the same track, its page left out of autonomy.
            session.setPageExcluded("main", true);

            javax.swing.JPopupMenu ignored = panel.buildTileMenu(track, null);

            assertFalse(ignored != null && offersAName(ignored),
                "precondition: the menu still offers a station name on an ignored square: " + itemNames(ignored));

            assertFalse(keyOffersAName(panel, track),
                "Control+N offers the station chooser on " + track + ", a square on a page left out of autonomy, where"
                + " the right-click menu offers " + (ignored == null ? "nothing" : itemNames(ignored).toString())
                + " (MFR-B3)");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /** What the key acts on - public, so the two doors can be put side by side. */
    private static boolean keyOffersAName(AutonomyEditorPanel panel, TileKey tile) throws Exception
    {
        java.lang.reflect.Method asked;

        try
        {
            asked = AutonomyEditorPanel.class.getMethod("offersAStationName", TileKey.class);
        }
        catch (NoSuchMethodException none)
        {
            fail("AutonomyEditorPanel has no offersAStationName: Control+N asks its own question, which is not the"
                + " menu's - it does not ask isIgnored (MFR-B3)");

            return false;
        }

        return (Boolean) asked.invoke(panel, tile);
    }

    private static boolean offersAName(javax.swing.JPopupMenu menu)
    {
        java.util.List<String> names = itemNames(menu);

        return names.contains(I18n.t("autosetup.ui.menuShowStationHere"))
            || names.contains(I18n.t("autosetup.ui.menuShowStationHereNamed"));
    }

    private static LayoutDiagram aPageWithARun(String name) throws java.io.IOException
    {
        LayoutDiagram page = new LayoutDiagram(name, 12, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);

        for (int x = 2; x <= 5; x++)
        {
            page.addComponent(componentType.STRAIGHT, x, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        }

        page.addComponent(componentType.FEEDBACK, 6, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId(name);

        return page;
    }

    private static java.util.List<String> itemNames(javax.swing.JPopupMenu menu)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        if (menu == null) return out;

        for (java.awt.Component each : menu.getComponents())
        {
            if (each instanceof javax.swing.JMenuItem) out.add(((javax.swing.JMenuItem) each).getText());
        }

        return out;
    }

    private static void needsADisplay()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("AutonomyEditorPanel builds real Swing components - this needs a display");
        }
    }

    private static void deleteRecursively(java.io.File file)
    {
        java.io.File[] children = file.listFiles();

        if (children != null)
        {
            for (java.io.File child : children) deleteRecursively(child);
        }

        file.delete();
    }
}
