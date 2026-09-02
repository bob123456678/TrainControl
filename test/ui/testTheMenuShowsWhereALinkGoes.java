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
