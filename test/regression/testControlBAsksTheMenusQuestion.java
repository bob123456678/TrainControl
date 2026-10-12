package regression;

import java.util.Arrays;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.AutonomyEditorPanel;

/**
 * Control+B acts exactly where the right-click menu offers a maximum train length, and nowhere else.
 *
 * Adam, 2026-09-12: *"Is control+M taken in the autonomy editor?  If not, map it to the station maximum
 * train length, and add a tooltip."*  It was taken - the main window toggles the menu bar with it, and
 * since the post-processor of 2026-09-11 that reaches this editor too - so he was told what was free and
 * picked **B**, for berth.
 *
 * **The shape of this class is not new and neither is the defect it is here for.**  `MT-313` was Control+S
 * renaming plain track, because the key asked only whether the tile was null while the menu asked three
 * things; `testControlEAsksTheMenusQuestion` is the same class for Control+E, written after that key was
 * found to act on text labels the menu offers nothing on. A key and a menu item that mean the same action
 * have to ask the same question, and the only way to know they do is to put them side by side on every
 * square of a page.
 *
 * So `AutonomyEditorPanel.offersAMaximumTrainLength` is the one predicate: the menu item is added inside
 * the conditions it encodes, and the key calls it. This walks the page and fails when they part company.
 *
 * @author Adam
 */
public class testControlBAsksTheMenusQuestion
{
    /**
     * The two doors agree about every square on the page.
     *
     * The claim that would have caught `MT-313` and Control+E's text-label defect on its own, and the one
     * that catches the NEXT condition somebody adds to `buildTileMenu` and forgets to add to the key.
     *
     * Both answers have to occur on this page or the agreement is between two constants - a station, and
     * plain track, a text label and a sensor that is not a station.
     *
     * MUTATION: gating the menu's item on anything the predicate does not ask - or dropping `isStation`
     * from the predicate - fails this on the sensor that is not a station.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testTheTwoDoorsAgreeAboutEverySquareOnThePage() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-b-page").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithAStationAndAPlainSensor("main")));

            session.initialize("CtrlB");

            TileKey station = new TileKey("main", 1, 1);

            session.setStation(station, true);
            session.rebuild();

            assertTrue(session.getStore().isStation(station),
                "the fixture did not take: " + station + " has to be a station or every square on this"
                + " page answers the same way and the walk below compares two constants");

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            String prefix = menuPrefix();

            java.util.List<String> disagreed = new java.util.ArrayList<>();

            int offered = 0;
            int refused = 0;

            for (int x = 0; x < 12; x++)
            {
                for (int y = 0; y < 4; y++)
                {
                    TileKey tile = new TileKey("main", x, y);

                    javax.swing.JPopupMenu menu = panel.buildTileMenu(tile, null);

                    boolean onTheMenu = menu != null && offersMaximum(menu, prefix);

                    boolean theKeyActs = panel.offersAMaximumTrainLength(tile);

                    if (onTheMenu) offered++;
                    else refused++;

                    if (onTheMenu != theKeyActs)
                    {
                        disagreed.add(tile + ": menu " + (onTheMenu ? "offers" : "does not offer")
                            + " a maximum, the key " + (theKeyActs ? "acts" : "does not act"));
                    }

                    // AND WHERE IT ACTS, it acts on a square that really is a station - the number is
                    // meaningless anywhere else, and a target of null would open a dialog about nothing.
                    if (theKeyActs)
                    {
                        TileKey lands = panel.squareTheMaximumWouldGoOn(tile);

                        assertNotNull(lands, "the key acts on " + tile + " and names no square to write"
                            + " to, so the dialog would open about nothing");

                        assertTrue(session.getStore().isStation(lands),
                            "Control+B over " + tile + " would write a maximum train length to " + lands
                            + ", which is not a station - a maximum decides which trains may STOP"
                            + " somewhere, so on anywhere else it is a number nothing reads");
                    }
                }
            }

            assertEquals(disagreed, new java.util.ArrayList<String>(),
                "the right-click menu and Control+B disagree about " + disagreed.size() + " squares: "
                + disagreed + ". They are meant to be one question - the menu adds its item inside the"
                + " conditions `offersAMaximumTrainLength` encodes - so a disagreement is a second copy"
                + " having grown back, which is how MT-313 and Control+E's text-label defect both began");

            assertTrue(offered > 0,
                "no square on this page offers a maximum at all, so the comparison above is between two"
                + " predicates that both answer false everywhere");

            assertTrue(refused > 0,
                "every square on this page offers a maximum, so the comparison above is between two"
                + " predicates that both answer true everywhere");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /**
     * A sensor that is not a station is refused, which is the condition this key exists inside.
     *
     * Named separately from the walk above because it is the interesting square: plain track and text
     * labels are refused by conditions Control+E already had, and `isStation` is the one this key adds.
     * A predicate that dropped it would still pass every other square on the page.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testASensorThatIsNotAStationIsRefused() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-b-plain").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithAStationAndAPlainSensor("main")));

            session.initialize("CtrlB");

            TileKey station = new TileKey("main", 1, 1);
            TileKey plainSensor = new TileKey("main", 6, 1);

            session.setStation(station, true);
            session.rebuild();

            assertTrue(session.getReducer().getPoints().containsKey(plainSensor),
                "the fixture did not take: " + plainSensor + " has to be a Point, or it is refused for"
                + " being nothing rather than for not being a station");

            assertFalse(session.getStore().isStation(plainSensor),
                "the fixture did not take: " + plainSensor + " must not be a station");

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            assertFalse(panel.offersAMaximumTrainLength(plainSensor),
                "Control+B offers to set a maximum train length on a sensor no train stops at. The"
                + " number decides which trains may STOP somewhere; on a square nothing stops at it is"
                + " a setting the model never reads, and the menu does not offer it there");

            // AND THE CONTROL, on the same page and the same kind of square: the station takes it.
            assertTrue(panel.offersAMaximumTrainLength(station),
                "the station at " + station + " is refused too, so the key refuses everything and the"
                + " claim above is not about stations at all");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /**
     * The tooltip Adam asked for exists, in every language.
     *
     * *"Map it to the station maximum train length, and add a tooltip."*  A tooltip that is on the menu
     * item in English and missing in German is the failure this catches - `core.testMessageBundles`
     * checks that a key present in one bundle is present in all, so what is left is whether the key is
     * used at all.
     */
    @Test
    public void testTheMenuItemCarriesItsTooltip() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-b-tip").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithAStationAndAPlainSensor("main")));

            session.initialize("CtrlB");

            TileKey station = new TileKey("main", 1, 1);

            session.setStation(station, true);
            session.rebuild();

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            javax.swing.JMenuItem maximum =
                findItem(panel.buildTileMenu(station, null), menuPrefix());

            assertNotNull(maximum, "the station's menu carries no Maximum Train Length item at all, so"
                + " there is nothing for a tooltip to be on");

            assertNotNull(maximum.getToolTipText(),
                "the Maximum Train Length item has no tooltip. The label carries the number and the"
                + " units are the operator's own, so \"4\" alone says nothing about what it counts -"
                + " and nothing on screen says the shortcut exists (Adam, 2026-09-12)");

            assertTrue(maximum.getToolTipText().contains("B"),
                "the tooltip does not mention the shortcut: " + maximum.getToolTipText());
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /**
     * The menu item's label up to its number, so a square with any maximum on it can be recognised.
     *
     * Taken from the bundle rather than written here, so a reworded item does not quietly stop being
     * found - which would make the walk above compare "false" against "false" on every square.
     *
     * @return the text before the value
     */
    private static String menuPrefix()
    {
        String pattern = org.traincontrol.util.I18n.t("autolayout.ui.menuMaxTrainLength");

        int at = pattern.indexOf("{0}");

        return at < 0 ? pattern : pattern.substring(0, at);
    }

    /**
     * Whether this menu, or any submenu of it, offers the maximum.
     *
     * The item lives inside the station submenu, so a flat read of the popup's own components finds
     * nothing and would report every square as refusing.
     *
     * @param menu the popup
     * @param prefix what the item's label starts with
     * @return whether it is there
     */
    private static boolean offersMaximum(javax.swing.JPopupMenu menu, String prefix)
    {
        return findItem(menu, prefix) != null;
    }

    private static javax.swing.JMenuItem findItem(javax.swing.JPopupMenu menu, String prefix)
    {
        if (menu == null) return null;

        for (java.awt.Component each : menu.getComponents())
        {
            javax.swing.JMenuItem found = findItem(each, prefix);

            if (found != null) return found;
        }

        return null;
    }

    private static javax.swing.JMenuItem findItem(java.awt.Component component, String prefix)
    {
        if (component instanceof javax.swing.JMenu)
        {
            javax.swing.JMenu menu = (javax.swing.JMenu) component;

            for (java.awt.Component child : menu.getMenuComponents())
            {
                javax.swing.JMenuItem found = findItem(child, prefix);

                if (found != null) return found;
            }

            return null;
        }

        if (component instanceof javax.swing.JMenuItem)
        {
            javax.swing.JMenuItem item = (javax.swing.JMenuItem) component;

            if (item.getText() != null && item.getText().startsWith(prefix)) return item;
        }

        return null;
    }

    /**
     * A page with two sensors and a run of plain track between them, plus a text label.
     *
     * One sensor becomes a station and the other does not, which is the pair the interesting claim needs:
     * both are Points, and only one of them may hold a maximum.
     *
     * @param name the page
     * @return the diagram
     */
    private static LayoutDiagram aPageWithAStationAndAPlainSensor(String name) throws java.io.IOException
    {
        LayoutDiagram page = new LayoutDiagram(name, 12, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);

        for (int x = 2; x <= 5; x++)
        {
            page.addComponent(componentType.STRAIGHT, x, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        }

        page.addComponent(componentType.FEEDBACK, 6, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.addComponent(componentType.TEXT, 3, 3, 0, 0, 0, 0, accessoryDecoderType.MM2, "Yard");

        page.setPageId(name);

        return page;
    }

    /**
     * `AutonomyEditorPanel` builds real Swing components, so this needs a display.
     */
    private static void needsADisplay()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("AutonomyEditorPanel builds real Swing components - this needs a"
                + " display");
        }
    }

    /**
     * A temporary directory and everything under it.
     *
     * @param file what to remove
     */
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
