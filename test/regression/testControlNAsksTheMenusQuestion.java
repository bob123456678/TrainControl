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

            // AND THE SQUARES THE MENU SENDS TO ITS TEXT MENU (MFV-C3): a label and an empty square, which carry no
            // track - an empty square counts as ignored, so a predicate that asked `isIgnored` before the text branch
            // would refuse the very square a station name is usually put on, and the two squares above would not see it.
            for (TileKey square : new TileKey[] { new TileKey("main", 3, 3), new TileKey("main", 9, 2) })
            {
                javax.swing.JPopupMenu there = panel.buildTileMenu(square, null);

                assertTrue(there != null && offersAName(there),
                    "precondition: the menu offers no station name on the text or empty square " + square + ": "
                    + itemNames(there));

                assertTrue(keyOffersAName(panel, square),
                    "Control+N offers no station name on " + square + ", a text or empty square, where the menu offers "
                    + itemNames(there) + " (MFV-C3)");
            }

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

    /**
     * Every key in the editor puts down a gesture in progress, as the right-click menu does (MFV-C4).
     *
     * The menu abandons an armed tool before it offers anything - opening it is how somebody says "not that, this
     * instead".  Control+N does the same since MFR-B3; Control+S and Control+E did not, so with Test a Path armed one
     * key disarmed it and the next two left it waiting for a click.  Asked on a page left out of autonomy, where no key
     * opens a dialog - each key's own predicate is checked first, so a key that would open one fails a precondition
     * rather than hanging the run.
     *
     * @throws Exception from the session or the reflection
     */
    @Test
    public void testEveryEditorKeyPutsDownAGestureAsTheMenuDoes() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-keys").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithARun("main")));

            final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            final TileKey track = new TileKey("main", 3, 1);

            session.setPageExcluded("main", true);

            assertFalse(asked(panel, "canBeNamed", track), "precondition: Control+S would open a dialog on " + track);
            assertFalse(panel.offersALength(track), "precondition: Control+E would open a dialog on " + track);
            assertFalse(keyOffersAName(panel, track), "precondition: Control+N would open a dialog on " + track);

            String[] keys = { "Control+S", "Control+E", "Control+N" };
            Runnable[] presses = { () -> panel.promptNameFor(track), () -> panel.promptLengthFor(track),
                () -> panel.showStationNameFor(track) };

            // ARMED THROUGH THE PANEL'S OWN FIELD, not its button: on an ignored page the panel's refresh disables Test a
            // Path (`testButton.setEnabled(!ignored)`), so after the first click every further doClick did nothing and
            // the claim failed on its own precondition.  The state a click leaves is what the keys are asked about.
            final java.lang.reflect.Field tool = AutonomyEditorPanel.class.getDeclaredField("tool");

            tool.setAccessible(true);

            @SuppressWarnings({ "unchecked", "rawtypes" })
            final Object testTool = Enum.valueOf((Class) tool.getType(), "TEST");

            for (int i = 0; i < keys.length; i++)
            {
                if (!asked(panel, "anythingIsArmed", null)) tool.set(panel, testTool);

                assertTrue(asked(panel, "anythingIsArmed", null), "precondition: Test a Path did not arm before " + keys[i]);

                presses[i].run();

                assertFalse(asked(panel, "anythingIsArmed", null),
                    keys[i] + " left Test a Path armed, where the right-click menu puts it down before offering anything"
                    + " - one key disarms a gesture and another leaves it waiting for a click (MFV-C4)");
            }
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /** A private boolean question of the panel's, with the square or with nothing. */
    private static boolean asked(AutonomyEditorPanel panel, String name, TileKey tile) throws Exception
    {
        java.lang.reflect.Method method = tile == null
            ? AutonomyEditorPanel.class.getDeclaredMethod(name)
            : AutonomyEditorPanel.class.getDeclaredMethod(name, TileKey.class);

        method.setAccessible(true);

        return (Boolean) (tile == null ? method.invoke(panel) : method.invoke(panel, tile));
    }

    private static Object field(Object on, String name) throws Exception
    {
        java.lang.reflect.Field f = on.getClass().getDeclaredField(name);

        f.setAccessible(true);

        return f.get(on);
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

        // A label, the other square the menu sends to its text menu; (9,2) is left empty.
        page.addComponent(componentType.TEXT, 3, 3, 0, 0, 0, 0, accessoryDecoderType.MM2, "Yard");

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
