package ui;

import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;

/**
 * FR-056: right-clicking a tunnel flashes the square it is joined to, limited to a partner on the page being looked at:
 * the third test holds that limit.
 *
 * @author Adam
 */
public class testTheMenuShowsWhereALinkGoes
{
    /**
     * Everything the panel flashed, in order: `onReveal` is the editor's own highlight, so this is what a person would
     * see.
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

    /** Why the claims that build real Swing components skip on a machine with no display. */
    private static final String NEEDS_A_DISPLAY =
        "AutonomyEditorPanel builds real Swing components - this needs a display";

    /** A page with a tunnel at each end of it and ordinary track between them, both on one page. */
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
     * Bulk tools is the last thing on the menu, and nothing dangles after it (MT-257): it is about the whole setup, so
     * it belongs at the bottom behind a divider.  Three claims, since "last" alone is not enough: it is last, what
     * precedes it is a divider, and the menu does not end with one.  `buildTileMenu` rather than a mouse gesture, for
     * the reason `menuOn` gives.
     *
     * MUTATION: put `menu.addSeparator(); menu.add(bulkTools());` back where it was - above the caption
     * items - and the first claim fails, naming what is last instead.
     */
    @Test
    public void testBulkToolsIsTheLastThingOnTheMenu() throws Exception
    {
        onASession("tc-bulk-last", "a popup menu needs a display", session ->
        {
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

            // And the separators are minded, the other half of what was asked.
            assertTrue(menu.getComponent(count - 2) instanceof javax.swing.JSeparator,
                "nothing divides bulk tools from the square's own items, so it reads as one more of "
                + "them: " + shapeOf(menu));

            assertFalse(menu.getComponent(count - 1) instanceof javax.swing.JSeparator,
                "the menu ends with a divider, which is a line under nothing: " + shapeOf(menu));
        }, "main");
    }

    /** What a claim does with the session it is given. */
    private interface OnTheSession
    {
        void run(AutonomySession session) throws Exception;
    }

    /**
     * Skips on a machine with no display; otherwise opens a session in a temporary folder over a page of two tunnels
     * for each name, runs the claim on it, and deletes the folder.
     */
    private static void onASession(String folderPrefix, String skipReason, OnTheSession claim, String... pageNames)
        throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException(skipReason);
        }

        java.io.File layout = java.nio.file.Files.createTempDirectory(folderPrefix).toFile();

        try
        {
            java.util.List<LayoutDiagram> pages = new java.util.ArrayList<>();

            for (String name : pageNames) pages.add(aPageWithTwoTunnels(name));

            AutonomySession session = new AutonomySession(layout);

            session.open(pages);

            claim.run(session);
        }
        finally
        {
            deleteTree(layout);
        }
    }

    /** What a menu component is, for a failure message somebody can act on. */
    private static String describe(java.awt.Component c)
    {
        if (c instanceof javax.swing.JSeparator) return "a divider";

        if (c instanceof javax.swing.JMenu) return "the submenu \"" + ((javax.swing.JMenu) c).getText() + "\"";

        if (c instanceof javax.swing.JMenuItem) return "\"" + ((javax.swing.JMenuItem) c).getText() + "\"";

        return c.getClass().getSimpleName();
    }

    /** The menu's entries in order. */
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

    /** Removes a temporary folder and everything in it. */
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
     * Opens the menu on a square and hands back what that opening flashed.  `buildTileMenu` rather than a mouse
     * gesture, because both right-click surfaces call it.
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
     * The flash itself.
     *
     * MUTATION this catches: remove the `highlightPartnerOf(target)` call from `buildTileMenu`, or
     * narrow its type test to `isLink()` alone - a TUNNEL is not a LINK, and the menu's own link
     * section tests for both, which is where that pair of conditions comes from.
     */
    @Test
    public void testRightClickingATunnelFlashesItsPair() throws Exception
    {
        onASession("tc-link-flash", NEEDS_A_DISPLAY, session ->
        {
            TileKey here = new TileKey("main", 1, 1);
            TileKey there = new TileKey("main", 5, 1);

            session.pairPortals(here, there);

            assertEquals(session.getStore().getPortalPartner(here), there,
                "precondition: the two tunnels were not paired, so there is nothing to flash");

            Flashes flashes = menuOn(session, "main", here);

            assertTrue(flashes.seen.contains(there),
                "right-clicking the tunnel at " + here + " flashed " + flashes.seen + " - not the "
                + "square it is joined to, which is the whole of what was asked for");

            // And the other way round: a pairing is mutual, and a one-way highlight would pass from one end only.
            Flashes back = menuOn(session, "main", there);

            assertTrue(back.seen.contains(here),
                "right-clicking the far end flashed " + back.seen + " rather than " + here
                + " - the highlight only works from one end of the pairing");
        }, "main");
    }

    /** Nothing to flash, and nothing flashed: a tunnel nobody has paired, and ordinary track, must stay dark. */
    @Test
    public void testNothingFlashesWithoutAPairing() throws Exception
    {
        onASession("tc-link-flash-none", NEEDS_A_DISPLAY, session ->
        {
            TileKey unpaired = new TileKey("main", 1, 1);

            assertNull(session.getStore().getPortalPartner(unpaired),
                "precondition: these tunnels were supposed to be left unpaired");

            assertTrue(menuOn(session, "main", unpaired).seen.isEmpty(),
                "an unpaired tunnel flashed something when its menu opened - so the highlight is not "
                + "saying 'this is where it goes', it is just flashing");

            assertTrue(menuOn(session, "main", new TileKey("main", 1, 2)).seen.isEmpty(),
                "a plain piece of straight track flashed something when its menu opened");
        }, "main");
    }

    /**
     * A partner on another page is not flashed, because it cannot be seen: the editor shows one page at a time, so a
     * flash there looks like nothing while telling the code it did something.  Go to the partner is still on the menu.
     *
     * MUTATION this catches: drop the `onThisPage(partner)` test from `highlightPartnerOf`.
     */
    @Test
    public void testAPartnerOnAnotherPageIsLeftToTheJump() throws Exception
    {
        onASession("tc-link-flash-away", NEEDS_A_DISPLAY, session ->
        {
            TileKey here = new TileKey("one", 1, 1);
            TileKey away = new TileKey("two", 1, 1);

            session.pairPortals(here, away);

            assertEquals(session.getStore().getPortalPartner(here), away,
                "precondition: the cross-page pairing was not made");

            assertTrue(menuOn(session, "one", here).seen.isEmpty(),
                "the menu flashed a square on another page, which nobody can see - the highlight has "
                + "to stay on the page being looked at, and the jump is what the other case has");
        }, "one", "two");
    }
}
