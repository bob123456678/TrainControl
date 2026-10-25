package regression;

import java.util.Arrays;
import java.util.Map;
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
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;

/**
 * Control+E has to act on the square the right-click menu would act on, and only where it offers to (FR-066).
 *
 * `AutonomyEditorPanel.promptLengthFor` asks the menu's own question, but `isIgnored` is not all of it.
 * `buildTileMenu` also sends a text square to `buildTextMenu` (no length item at all) and acts on `leaderOf(tile)`
 * rather than on `tile`; the key did neither.  On a text label the key opened the dialog where the menu offers nothing;
 * on plain track it wrote the hovered tile's length, not the run leader's, so using both on one run counted it twice.
 * Both tests hold the shape the javadoc names: the guard and the affordance asking one question.
 *
 * Read-only: it builds its own diagram in a temporary folder and never touches a real railway.
 *
 * @author Adam
 */
public class testControlEAsksTheMenusQuestion
{
    /**
     * A page with a text label on it, and a run of plain track beside it.  The track is what makes the label's page one
     * the session knows: the text branch is fenced on `pageOf(tile) != null`.
     */
    private static LayoutDiagram aPageWithALabelAndARun(String name) throws java.io.IOException
    {
        LayoutDiagram page = new LayoutDiagram(name, 12, 4, null, null);

        // A sensor at each end, so what lies between them is one run of plain track.
        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);

        for (int x = 2; x <= 5; x++)
        {
            page.addComponent(componentType.STRAIGHT, x, 1, 0, 0, 0, 0, accessoryDecoderType.MM2,
                null);
        }

        page.addComponent(componentType.FEEDBACK, 6, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        // And a label, the one thing on a diagram that carries no track and is still the user's to click.
        page.addComponent(componentType.TEXT, 3, 3, 0, 0, 0, 0, accessoryDecoderType.MM2, "Yard");

        page.setPageId(name);

        return page;
    }

    /** The key opens the length dialog on a text label, where the menu offers no such item. */
    @Test
    public void testTheKeyDoesNotActOnASquareTheMenuOffersNothingOn() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-e-text").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithALabelAndARun("main")));

            TileKey label = new TileKey("main", 3, 3);

            assertNotNull(session.getGraph().getTiles().get(label),
                "the graph does not hold the label at all, so `isIgnored` would answer true for the"
                + " blank-square reason and this test would prove nothing about text");

            assertTrue(session.getGraph().getTiles().get(label).isText(),
                "the square at " + label + " is not a text label, so the fixture is not the one this"
                + " claim is about");

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            // What the menu offers on that square.
            javax.swing.JPopupMenu menu = panel.buildTileMenu(label, null);

            assertNotNull(menu, "no menu opened on the label at all, so there is nothing to compare"
                + " the key against");

            assertFalse(itemNames(menu).isEmpty(),
                "the menu on the label is empty, so it offers nothing about ANY square and the"
                + " comparison below is not about the length item");

            assertFalse(itemNames(menu).contains(
                org.traincontrol.util.I18n.t("autosetup.ui.menuSetLength")),
                "the menu on a text label now offers Set Length - " + itemNames(menu) + " - so the"
                + " premise of this test has moved and the key is no longer the wider door");

            // And what the key asks.  `promptLengthFor` opens a modal dialog, so what a test can put beside the menu is
            // the public predicate it acts on.
            assertFalse(panel.offersALength(label),
                "Control+E acts on the text label at " + label + " and opens the length dialog on a"
                + " square whose right-click menu offers " + itemNames(menu) + " and no length at"
                + " all. `buildTileMenu` asks three questions before it reaches Set Length, and the"
                + " TEXT branch is one of them. This is MT-313's shape, which `promptLengthFor`'s"
                + " own javadoc names as the thing it avoids");

            assertEquals(panel.squareTheLengthWouldGoOn(label), null,
                "the key names " + panel.squareTheLengthWouldGoOn(label) + " as the square it would"
                + " measure, on a label the menu offers no length for");

            // And the control: the predicate has not simply started answering false everywhere.
            TileKey track = new TileKey("main", 4, 1);

            assertTrue(panel.offersALength(track),
                "the key offers no length on the plain track at " + track + " either, so"
                + " `offersALength` answers false for everything and the claim above says nothing"
                + " about text labels");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /** The key and the menu write the length to the same square. */
    @Test
    public void testTheKeyMeasuresTheSquareTheMenuWouldMeasure() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-e-run").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithALabelAndARun("main")));

            Map<TileKey, TileKey> leaders = session.runLeaders();

            // A tile of a run that is not the tile that speaks for it; without one the two doors cannot disagree.
            TileKey follower = null;

            for (Map.Entry<TileKey, TileKey> each : leaders.entrySet())
            {
                if (!each.getKey().equals(each.getValue()))
                {
                    follower = each.getKey();

                    break;
                }
            }

            assertNotNull(follower, "no tile on this page belongs to a run it does not lead, so the"
                + " two doors cannot disagree here and this claim is about a different fixture."
                + "  runLeaders says " + leaders);

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            // `buildTileMenu` binds its Set Length item to `applyLength(leaderOf(tile))`, and `runLeaders` is the same
            // map read from the session, so the key's target has to match it.
            assertEquals(panel.squareTheLengthWouldGoOn(follower), leaders.get(follower),
                "right-clicking " + follower + " sets the length of " + leaders.get(follower)
                + " - the menu binds its item to `applyLength(leaderOf(tile))` - and Control+E over"
                + " the same square names " + panel.squareTheLengthWouldGoOn(follower) + "."
                + "  Lengths are summed per tile along an edge, so using both doors on one run counts"
                + " the run twice, and the dialog opens showing 0 on a run that is measured."
                + "  The key has to take the leader, exactly as the menu does");

            // And the control: the leader is not the clicked square, so the assertion above is a comparison.
            assertFalse(follower.equals(leaders.get(follower)),
                "the fixture's follower at " + follower + " leads its own run, so the two doors"
                + " cannot disagree here and the claim above would pass unchanged");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /**
     * Over every square of the page: the menu offers a length exactly where the key acts.  Walking the whole page is
     * the only form in which "they ask one question" is checkable, and it catches the next condition somebody adds to
     * `buildTileMenu` and forgets in the key.
     *
     * MUTATION: gating the menu's Set Length item on anything `offersALength` does not ask - a review
     * used `isPoint` - fails this on every plain-track square, and left both claims above green.
     */
    @Test
    public void testTheTwoDoorsAgreeAboutEverySquareOnThePage() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-e-page").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithALabelAndARun("main")));

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            String setLength = org.traincontrol.util.I18n.t("autosetup.ui.menuSetLength");

            java.util.List<String> disagreed = new java.util.ArrayList<>();

            int offered = 0;
            int refused = 0;

            for (int x = 0; x < 12; x++)
            {
                for (int y = 0; y < 4; y++)
                {
                    TileKey tile = new TileKey("main", x, y);

                    javax.swing.JPopupMenu menu = panel.buildTileMenu(tile, null);

                    boolean onTheMenu = menu != null && itemNames(menu).contains(setLength);

                    boolean theKeyActs = panel.offersALength(tile);

                    if (onTheMenu) offered++;
                    else refused++;

                    if (onTheMenu != theKeyActs)
                    {
                        disagreed.add(tile + ": menu " + (onTheMenu ? "offers" : "does not offer")
                            + " a length, the key " + (theKeyActs ? "acts" : "does not act"));
                    }
                }
            }

            assertEquals(disagreed, new java.util.ArrayList<String>(),
                "the right-click menu and Control+E disagree about " + disagreed.size() + " squares: "
                + disagreed + ". They are meant to be one question - `buildTileMenu` adds its item"
                + " inside `if (offersALength(tile))` - so a disagreement is a second copy having"
                + " grown back");

            // And both answers happen on this page, or the agreement above is between two constants.
            assertTrue(offered > 0,
                "no square on this page offers a length at all, so the comparison above is between two"
                + " predicates that both answer false everywhere");

            assertTrue(refused > 0,
                "every square on this page offers a length, so the comparison above is between two"
                + " predicates that both answer true everywhere - and the label at 3,3 should not");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /**
     * And the predicate's content, named off the fixture rather than read off itself: the whole-page comparison cannot
     * redden on a wrong predicate, since the menu adds its item inside `if (offersALength(tile))`.  A text label
     * carries no track to measure, and a square on an excluded page is one autonomy takes no notice of; both are named
     * here, so the claim survives the predicate being rewritten.
     */
    @Test
    public void testTheKeyRefusesTheTwoSquaresTheMenuOffersNoLengthFor() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-e-content").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithALabelAndARun("main")));

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            TileKey label = new TileKey("main", 3, 3);
            TileKey track = new TileKey("main", 4, 1);

            assertTrue(panel.offersALength(track),
                "the plain track at " + track + " offers no length, so the two refusals below could"
                + " be a predicate that answers false for everything");

            assertFalse(panel.offersALength(label),
                "Control+E acts on the text label at " + label + ", whose right-click menu is about"
                + " what is WRITTEN on the square and carries no length item at all");

            // And a square on a page autonomy is told to ignore.
            session.setPageExcluded("main", true);

            try
            {
                assertFalse(panel.offersALength(track),
                    "Control+E acts on " + track + " while its whole page is excluded from autonomy."
                    + " Nothing on such a square is the user's to set, which is why the right-click"
                    + " menu offers only Bulk Tools there");
            }
            finally
            {
                session.setPageExcluded("main", false);
            }

            assertTrue(panel.offersALength(track),
                "the track at " + track + " still offers no length once its page is back in service,"
                + " so the refusal above was not the exclusion");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /** The labels of every item on a menu, submenus included. */
    private static java.util.List<String> itemNames(javax.swing.JPopupMenu menu)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        for (java.awt.Component each : menu.getComponents())
        {
            if (each instanceof javax.swing.JMenuItem)
            {
                out.add(((javax.swing.JMenuItem) each).getText());
            }
        }

        return out;
    }

    /** `AutonomyEditorPanel` builds real Swing components, so this needs a display. */
    private static void needsADisplay()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("AutonomyEditorPanel builds real Swing components - this needs a"
                + " display");
        }
    }

    /** A temporary directory and everything under it. */
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
