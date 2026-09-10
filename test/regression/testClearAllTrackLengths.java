package regression;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * FR-069: Clear All Track Lengths, on the Bulk Tools menu.
 *
 * Adam, 2026-09-10: **"to autonomy bulk tools menu, add 'clear all track lengths' - this should clear
 * the segment lengths across all pages, after the user confirms in a popup."**
 *
 * **Across all pages is the load-bearing half**, and it is what this class measures: lengths are keyed
 * by a square that names its own page, so the fixture measures squares on more than one page and the
 * claim is that none survives. A per-page clear would pass a test that only looked at one.
 *
 * **The dialog itself is not asserted here** - a modal cannot be opened from a test without a robot
 * driving it - so what stands in for it is the pair the menu already keeps: the item's tooltip is the
 * same sentence the confirmation shows, from the same builder, which is the arrangement OB-194 put
 * there precisely so the two cannot drift. If the tooltip says it, the dialog says it.
 *
 * **And the guard is asserted as well as the greying.** The item disables itself when nothing is
 * measured, and `clearAllTileLengths` refuses again when it is pressed - `guard-and-affordance-same-
 * question`, and what OB-057 and OB-090 were both instances of. Only the first is reachable from here;
 * the second is asserted through the session door it calls.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111) - and that railway carries no lengths of
 * its own since 2026-09-10, so every length here is one this class set.
 *
 * MUTATION: make `clearEveryTileLength` clear only the active page's squares and
 * `testItClearsEveryPage` fails; take the count out of the message and
 * `testTheTooltipSaysHowMuchItWillForget` does.
 *
 * @author Adam
 */
public class testClearAllTrackLengths
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutEditor editor;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the autonomy editor and its menus need a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration, so there is no bulk tool to open");
        }

        final LayoutDiagram page = model.getLayout(PAGE);

        if (page == null) throw new SkipException("no page " + PAGE);

        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
        });

        editor = built[0];
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (session != null) session.clearEveryTileLength();

            if (editor != null)
            {
                final LayoutEditor closing = editor;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

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
     * Every measured square on every page is forgotten, and the count is what was there.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testItClearsEveryPage() throws Exception
    {
        List<TileKey> measured = measureAcrossPages();

        assertTrue(pagesOf(measured).size() > 1,
            "every square this test measured is on one page (" + pagesOf(measured) + "), so a clear"
            + " that only emptied the page in front of it would pass. The fixture has to span pages"
            + " for the claim to be about all of them");

        assertEquals(session.tilesWithALength().size(), measured.size(),
            "the session does not report the " + measured.size() + " squares this test measured, so"
            + " the count below would be about some other set");

        int cleared = session.clearEveryTileLength();

        assertEquals(cleared, measured.size(),
            "the clear reported " + cleared + " squares where " + measured.size() + " were measured");

        List<TileKey> left = new ArrayList<>();

        for (TileKey tile : measured)
        {
            if (session.getStore().getTileLength(tile) > 0) left.add(tile);
        }

        assertEquals(left, new ArrayList<TileKey>(),
            "these squares still have a length after Clear All Track Lengths: " + left
            + ". Adam asked for it to clear them \"across all pages\"");

        assertTrue(session.tilesWithALength().isEmpty(),
            "the session still reports measured squares after the clear: "
            + session.tilesWithALength());
    }

    /**
     * With nothing measured, the item is greyed and says why.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testItIsGreyedWhenThereIsNothingToClear() throws Exception
    {
        session.clearEveryTileLength();

        javax.swing.JMenuItem item = theItem();

        assertNotNull(item, "the Bulk Tools menu has no Clear All Track Lengths item on it");

        assertFalse(item.isEnabled(),
            "the item offers to clear lengths on a railway that has none");

        assertEquals(item.getToolTipText() == null ? "" : item.getToolTipText().replaceAll("<[^>]*>", ""),
            I18n.t("autosetup.ui.infoNoTrackLengthsToClear"),
            "the greyed item does not say why it is greyed: " + item.getToolTipText());
    }

    /**
     * And with squares measured, the tooltip says how many it will forget - which is the sentence the
     * dialog will show.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheTooltipSaysHowMuchItWillForget() throws Exception
    {
        List<TileKey> measured = measureAcrossPages();

        javax.swing.JMenuItem item = theItem();

        assertNotNull(item, "the Bulk Tools menu has no Clear All Track Lengths item on it");

        assertTrue(item.isEnabled(),
            "the item is greyed on a railway with " + measured.size() + " measured squares");

        String said = item.getToolTipText() == null ? "" : item.getToolTipText();

        assertTrue(said.contains(String.valueOf(measured.size())),
            "the tooltip does not say how many squares it is about to forget, and the label does."
            + " It says: " + said);

        assertEquals(said.replaceAll("<[^>]*>", ""),
            I18n.f("autolayout.ui.confirmClearAllTrackLengths", measured.size()),
            "the tooltip is not the sentence the confirmation shows. They come from one builder so"
            + " that they cannot drift, which is what OB-194 put there. It says: " + said);

        assertTrue(item.getText().contains(String.valueOf(measured.size())),
            "the menu label does not carry the count: " + item.getText());
    }

    /**
     * Measures three squares, on more than one page where the railway has more than one.
     *
     * @return the squares measured
     */
    private static List<TileKey> measureAcrossPages()
    {
        session.clearEveryTileLength();

        List<TileKey> squares = new ArrayList<>();

        List<String> pages = new ArrayList<>();

        // COLLECTED FIRST, THEN WRITTEN: setTileLength rebuilds the reducer, so walking its own
        // collections while writing to them re-derives the graph under the iterator.
        List<TileKey> candidates = new ArrayList<>(session.getReducer().getPoints().keySet());

        for (TileKey tile : candidates)
        {
            if (pages.contains(tile.getPage()) && pages.size() > 1) continue;

            if (!pages.contains(tile.getPage())) pages.add(tile.getPage());

            squares.add(tile);

            if (squares.size() >= 3 && pages.size() > 1) break;
        }

        for (TileKey tile : squares) session.setTileLength(tile, 4);

        return squares;
    }

    /**
     * The pages a set of squares is spread over.
     *
     * @param squares the squares
     * @return the distinct page names
     */
    private static List<String> pagesOf(List<TileKey> squares)
    {
        List<String> out = new ArrayList<>();

        for (TileKey tile : squares)
        {
            if (!out.contains(tile.getPage())) out.add(tile.getPage());
        }

        return out;
    }

    /**
     * Clear All Track Lengths, off the Bulk Tools menu as the right-click menu builds it.
     *
     * Through `buildBulkMenuForTest`, which calls `bulkTools()` itself: a second construction could
     * agree with itself while the real one is wrong.
     *
     * @return the item, or null when the menu does not carry one
     * @throws Exception from the event thread
     */
    private static javax.swing.JMenuItem theItem() throws Exception
    {
        final javax.swing.JMenuItem[] found = new javax.swing.JMenuItem[1];

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JMenu bulk = editor.getAutonomyPanel().buildBulkMenuForTest();

            for (int i = 0; i < bulk.getItemCount(); i++)
            {
                javax.swing.JMenuItem item = bulk.getItem(i);

                if (item == null || item.getText() == null) continue;

                // Matched on the part of the label that is not the count, so the assertion about the
                // count is not also what finds the item.
                String withoutCount = I18n.f("autolayout.ui.menuClearAllTrackLengths", "")
                    .replace("()", "").trim();

                if (item.getText().startsWith(withoutCount.split("\\(")[0].trim())) found[0] = item;
            }
        });

        return found[0];
    }
}
