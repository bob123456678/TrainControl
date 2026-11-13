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
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import support.Trees;
import static support.Diagrams.aPageWithARunAndALabel;

/**
 * Control+E has to act on the square the right-click menu would act on, and only where it offers to (FR-066): the
 * guard and the affordance ask one question.  Read-only: it builds its own diagram in a temporary folder.
 *
 * @author Adam
 */
public class testControlEAsksTheMenusQuestion
{
    /** The key and the menu write the length to the same square. */
    @Test
    public void testTheKeyMeasuresTheSquareTheMenuWouldMeasure() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-e-run").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            // The run is what makes the label's page one the session knows: the text branch is fenced on
            // `pageOf(tile) != null`.
            session.open(Arrays.asList(aPageWithARunAndALabel("main")));

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
            Trees.delete(folder);
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

            session.open(Arrays.asList(aPageWithARunAndALabel("main")));

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
            Trees.delete(folder);
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

            session.open(Arrays.asList(aPageWithARunAndALabel("main")));

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
            Trees.delete(folder);
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
}
