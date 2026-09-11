package ui;

import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.AutonomyEditorPanel;

/**
 * FR-056: right-clicking a tunnel flashes the square it is joined to.
 *
 * Adam, 2026-09-02: **"when in the autonomy editor and you right click a tunnel tile, highlight its
 * pair."**
 *
 * The menu already offered to GO to the partner, and that item is the right answer for the case links
 * were written for - the two ends on different pages, where the only way to see the other one is to
 * be taken there. It is the wrong answer for the case Adam is looking at: his own tunnel pair is four
 * squares apart on `1 - Main`, and jumping to the partner moves the view away from the square he
 * right-clicked in order to ask about it.
 *
 * So the flash is deliberately limited to a partner on the page being looked at, and that limit is
 * what the third test below is for. A highlight on a page nobody is looking at is not a highlight,
 * and a build that fired one would look identical to a build that fired nothing.
 *
 * @author Adam
 */
public class testTheMenuShowsWhereALinkGoes
{
    /**
     * Everything the panel flashed, in order.
     *
     * `onReveal` is the editor's own highlight - `LayoutEditor.reveal` scrolls the square into view
     * and gives it the yellow wash - so recording what the panel asks it to flash is recording
     * exactly what a person would see.
     */
    private static final class Flashes implements java.util.function.Consumer<TileKey>
    {
        final java.util.List<TileKey> seen = new java.util.ArrayList<>();

        @Override
        public void accept(TileKey tile)
        {
            seen.add(tile);
        }
    }

    /**
     * A page with a tunnel at each end of it and ordinary track between them.
     *
     * Both on ONE page, which is Adam's case and the one the jump handles badly.
     */
    private static LayoutDiagram aPageWithTwoTunnels(String name) throws java.io.IOException
    {
        LayoutDiagram page = new LayoutDiagram(name, 12, 4, null, null);

        // A tunnel at orientation 0 opens to the south, so the track it meets runs vertically.
        page.addComponent(componentType.TUNNEL, 1, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 1, 2, 1, 0, 0, 0, accessoryDecoderType.MM2, null);

        page.addComponent(componentType.TUNNEL, 5, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 2, 1, 0, 0, 0, accessoryDecoderType.MM2, null);

        page.setPageId(name);

        return page;
    }

    /**
     * Bulk tools is the last thing on the menu, and nothing dangles after it (Adam, 2026-09-11).
     *
     * *"Can we make 'bulk tools' be the last item in the right-click menu in the autonomy editor?  mind
     * the separators."*
     *
     * Everything else on this menu is about the SQUARE that was clicked; bulk tools is about the whole
     * setup, which is why it is a submenu at all (MT-257) and why the bottom, behind a divider, is where
     * it belongs. In the middle it read as being about the square under the pointer.
     *
     * **Three claims, because "last" alone is not enough.** An item that is last on a menu whose last
     * entry is a separator looks right to an index check and wrong to a person; and a divider directly
     * above it is what makes it read as a group rather than as one more square action. So: it is last,
     * what precedes it is a divider, and the menu does not end with one.
     *
     * `buildTileMenu` rather than a mouse gesture, for the reason `menuOn` gives above it: that method
     * is what BOTH right-click surfaces call.
     *
     * MUTATION: put `menu.addSeparator(); menu.add(bulkTools());` back where it was - above the caption
     * items - and the first claim fails, naming what is last instead.
     */
    @Test
    public void testBulkToolsIsTheLastThingOnTheMenu() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("a popup menu needs a display");
        }

        java.io.File layout = java.nio.file.Files.createTempDirectory("tc-bulk-last").toFile();

        try
        {
            LayoutDiagram page = aPageWithTwoTunnels("main");

            AutonomySession session = new AutonomySession(layout);

            session.open(Arrays.asList(page));

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            javax.swing.JPopupMenu menu = panel.buildTileMenu(new TileKey("main", 1, 2), null);

            assertNotNull(menu, "no menu opened at all, so this test would pass whatever the order was");

            int count = menu.getComponentCount();

            assertTrue(count >= 3, "the menu has only " + count + " entries, which is too few for this "
                + "to be measuring an order");

            java.awt.Component last = menu.getComponent(count - 1);

            assertTrue(last instanceof javax.swing.JMenu,
                "the last thing on the menu is " + describe(last) + ", not the bulk tools submenu.  "
                + "Everything else here is about the square that was clicked; this one is about the "
                + "whole setup, and in the middle it reads as being about the square (Adam, "
                + "2026-09-11).  The menu holds: " + shapeOf(menu));

            assertEquals(((javax.swing.JMenu) last).getText(),
                org.traincontrol.util.I18n.t("autosetup.ui.menuBulkTools"),
                "the last entry is a submenu, but not that one: " + shapeOf(menu));

            // AND THE SEPARATORS ARE MINDED, which is the other half of what was asked.
            assertTrue(menu.getComponent(count - 2) instanceof javax.swing.JSeparator,
                "nothing divides bulk tools from the square's own items, so it reads as one more of "
                + "them: " + shapeOf(menu));

            assertFalse(menu.getComponent(count - 1) instanceof javax.swing.JSeparator,
                "the menu ends with a divider, which is a line under nothing: " + shapeOf(menu));
        }
        finally
        {
            deleteTree(layout);
        }
    }

    /**
     * What a menu component is, for a failure message somebody can act on.
     */
    private static String describe(java.awt.Component c)
    {
        if (c instanceof javax.swing.JSeparator) return "a divider";

        if (c instanceof javax.swing.JMenu) return "the submenu \"" + ((javax.swing.JMenu) c).getText() + "\"";

        if (c instanceof javax.swing.JMenuItem) return "\"" + ((javax.swing.JMenuItem) c).getText() + "\"";

        return c.getClass().getSimpleName();
    }

    /**
     * The menu's entries in order.
     */
    private static String shapeOf(javax.swing.JPopupMenu menu)
    {
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < menu.getComponentCount(); i++)
        {
            if (out.length() > 0) out.append(" | ");

            out.append(describe(menu.getComponent(i)));
        }

        return out.toString();
    }

    /**
     * Removes a temporary folder and everything in it.
     */
    private static void deleteTree(java.io.File where)
    {
        java.io.File[] children = where.listFiles();

        if (children != null)
        {
            for (java.io.File child : children) deleteTree(child);
        }

        where.delete();
    }

    /**
     * Opens the menu on a square and hands back what that opening flashed.
     *
     * `buildTileMenu` rather than a mouse gesture, because it is the method BOTH right-click surfaces
     * call - the editor's own diagram and the main window's - so a test that drove one of them would
     * be silent about the other.
     */
    private static Flashes menuOn(AutonomySession session, String page, TileKey square)
        throws Exception
    {
        AutonomyEditorPanel panel = new AutonomyEditorPanel(session, page, () -> { });

        Flashes flashes = new Flashes();

        panel.setOnReveal(flashes);

        assertNotNull(panel.buildTileMenu(square, null),
            "no menu opened on " + square + " at all, so this test would pass whatever the "
            + "highlight did");

        return flashes;
    }

    /**
     * The thing Adam asked for.
     *
     * MUTATION this catches: remove the `highlightPartnerOf(target)` call from `buildTileMenu`, or
     * narrow its type test to `isLink()` alone - a TUNNEL is not a LINK, and the menu's own link
     * section tests for both, which is where that pair of conditions comes from.
     */
    @Test
    public void testRightClickingATunnelFlashesItsPair() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("AutonomyEditorPanel builds real Swing components - this needs "
                + "a display");
        }

        java.io.File layout = java.nio.file.Files.createTempDirectory("tc-link-flash").toFile();

        try
        {
            LayoutDiagram page = aPageWithTwoTunnels("main");

            AutonomySession session = new AutonomySession(layout);

            session.open(Arrays.asList(page));

            TileKey here = new TileKey("main", 1, 1);
            TileKey there = new TileKey("main", 5, 1);

            session.pairPortals(here, there);

            assertEquals(session.getStore().getPortalPartner(here), there,
                "precondition: the two tunnels were not paired, so there is nothing to flash");

            Flashes flashes = menuOn(session, "main", here);

            assertTrue(flashes.seen.contains(there),
                "right-clicking the tunnel at " + here + " flashed " + flashes.seen + " - not the "
                + "square it is joined to, which is the whole of what was asked for");

            // And the other way round, because a pairing is mutual and a one-way highlight would be
            // half a feature that reads as a working one from whichever end was tried first.
            Flashes back = menuOn(session, "main", there);

            assertTrue(back.seen.contains(here),
                "right-clicking the far end flashed " + back.seen + " rather than " + here
                + " - the highlight only works from one end of the pairing");
        }
        finally
        {
            deleteRecursively(layout);
        }
    }

    /**
     * Nothing to flash, and nothing flashed.
     *
     * Two squares that must stay dark: a tunnel nobody has paired, and a piece of ordinary track.
     * Without these the test above passes for a panel that flashes something on every right-click,
     * which would be a highlight that says nothing at all.
     */
    @Test
    public void testNothingFlashesWithoutAPairing() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("AutonomyEditorPanel builds real Swing components - this needs "
                + "a display");
        }

        java.io.File layout = java.nio.file.Files.createTempDirectory("tc-link-flash-none").toFile();

        try
        {
            LayoutDiagram page = aPageWithTwoTunnels("main");

            AutonomySession session = new AutonomySession(layout);

            session.open(Arrays.asList(page));

            TileKey unpaired = new TileKey("main", 1, 1);

            assertNull(session.getStore().getPortalPartner(unpaired),
                "precondition: these tunnels were supposed to be left unpaired");

            assertTrue(menuOn(session, "main", unpaired).seen.isEmpty(),
                "an unpaired tunnel flashed something when its menu opened - so the highlight is not "
                + "saying 'this is where it goes', it is just flashing");

            assertTrue(menuOn(session, "main", new TileKey("main", 1, 2)).seen.isEmpty(),
                "a plain piece of straight track flashed something when its menu opened");
        }
        finally
        {
            deleteRecursively(layout);
        }
    }

    /**
     * A partner on another page is NOT flashed, because it cannot be seen.
     *
     * This is the limit that makes the feature honest rather than the one that makes it small. The
     * editor shows one page at a time - the field is final and the grid, the annotations and the page
     * exclusion all follow from it - so flashing a square on a page nobody is looking at produces
     * exactly the same screen as flashing nothing, while telling the code it did something. **Go to
     * the partner** is still on the menu, and that is the answer for this case; it was written for it.
     *
     * MUTATION this catches: drop the `onThisPage(partner)` test from `highlightPartnerOf`.
     */
    @Test
    public void testAPartnerOnAnotherPageIsLeftToTheJump() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("AutonomyEditorPanel builds real Swing components - this needs "
                + "a display");
        }

        java.io.File layout = java.nio.file.Files.createTempDirectory("tc-link-flash-away").toFile();

        try
        {
            LayoutDiagram one = aPageWithTwoTunnels("one");
            LayoutDiagram two = aPageWithTwoTunnels("two");

            AutonomySession session = new AutonomySession(layout);

            session.open(Arrays.asList(one, two));

            TileKey here = new TileKey("one", 1, 1);
            TileKey away = new TileKey("two", 1, 1);

            session.pairPortals(here, away);

            assertEquals(session.getStore().getPortalPartner(here), away,
                "precondition: the cross-page pairing was not made");

            assertTrue(menuOn(session, "one", here).seen.isEmpty(),
                "the menu flashed a square on another page, which nobody can see - the highlight has "
                + "to stay on the page being looked at, and the jump is what the other case has");
        }
        finally
        {
            deleteRecursively(layout);
        }
    }

    /**
     * A temporary directory and everything under it.
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
