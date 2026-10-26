package regression;

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
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * OB-194: Bulk Tools says what it is about to clear, and what Cancel will do about it.
 *
 * Since OB-223 Cancel in the autonomy editor restores the setup as it opened, so the locomotives a clear took off come
 * back (`regression.testCancelUndoesAutonomyEdits.testCancelPutsBackTheLocomotivesABulkClearTook` runs it): the warning
 * says Cancel puts them back and Save keeps the change.
 *
 * Asserted by asking the editor, not by reading its source: the panel is stood up over the frozen railway
 * (never `cs2_sample_layout`, OB-111) with trains standing on it, and the warning is the string the confirmation will
 * show and the tooltip shows in short - one builder choosing both, so the two cannot say different things.  Two claims
 * are not about English: the warning must name every locomotive it is about to lift (a count cannot be checked), and it
 * must name Cancel, asked as `I18n.t("ui.cancel")` like the editor's own button, so it holds in all eight languages.
 * On the track diagram's own menu, where there is no Cancel, it must not name one.
 *
 * MUTATION: take `{1}` out of the message and the naming claim fails; take the Cancel sentence out and
 * the second does; point the tooltip back at `I18n.t` and the third does.
 *
 * @author Adam
 */
public class testTheBulkClearSaysWhatCancelDoes
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutEditor editor;

    private static final String PAGE = "1 - Main";

    /** The warning the editor produced, read once on the event thread */
    private static String warning;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the autonomy editor and its menus need a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new AssertionError("no autonomy configuration, so there is no bulk tool to open");
        }

        final LayoutDiagram page = model.getLayout(PAGE);

        if (page == null) throw new AssertionError("no page " + PAGE);

        editor = buildTheEditor(page);

        final String[] said = new String[1];

        SwingUtilities.invokeAndWait(
            () -> said[0] = editor.getAutonomyPanel().clearLocomotivesWarning());

        warning = said[0];
    }

    /** The autonomy editor on a page, built as the main window builds it. */
    private static LayoutEditor buildTheEditor(final LayoutDiagram page) throws Exception
    {
        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
        });

        return built[0];
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
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

    /** The warning names every locomotive the clear is about to lift: a count is a number nobody can check. */
    @Test
    public void testTheWarningNamesTheLocomotivesItWillClear()
    {
        assertNotNull(warning, "the editor produced no warning at all");

        java.util.List<String> standing = new java.util.ArrayList<>();

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            String name = session.getLocomotiveNameAt(tile);

            if (name != null && !standing.contains(name)) standing.add(name);
        }

        assertFalse(standing.isEmpty(),
            "no locomotive is standing anywhere on the frozen railway, so the bulk clear has nothing"
            + " to warn about and this claim would pass on an empty list");

        java.util.List<String> unnamed = new java.util.ArrayList<>();

        for (String name : standing)
        {
            if (!warning.contains(name)) unnamed.add(name);
        }

        assertTrue(unnamed.isEmpty(),
            "the warning does not name " + unnamed + ", which the clear is about to take off the"
            + " setup. It says: " + warning);

        assertTrue(warning.contains(String.valueOf(session.tilesWithALocomotive().size())),
            "the warning does not say how many squares it is about to empty, and the menu item it"
            + " belongs to does. It says: " + warning);
    }

    /**
     * And it names Cancel, which since OB-223 is the button that does undo it, asked by the key the editor's Cancel
     * carries.
     */
    @Test
    public void testTheWarningNamesCancel()
    {
        assertNotNull(warning, "the editor produced no warning at all");

        assertTrue(warning.contains(I18n.t("ui.cancel")),
            "the warning does not mention " + I18n.t("ui.cancel") + " - the editor's own button - so"
            + " nothing tells the operator that Cancel puts the locomotives back and Save keeps them off"
            + " (OB-194, WK7-C2). It says: " + warning);
    }

    /**
     * The tooltip on the menu item says the same thing in short - the count and what Cancel does - through the builder
     * that picks the confirmation's sentence (FR-112).  Asked of the built menu, so a tooltip set from a different
     * string would be caught.
     */
    @Test
    public void testTheMenuItemCarriesTheSameWarning() throws Exception
    {
        final java.util.List<String> tips = new java.util.ArrayList<>();

        SwingUtilities.invokeAndWait(() -> collect(
            editor.getAutonomyPanel().buildBulkMenuForTest(), tips));

        assertFalse(tips.isEmpty(),
            "the Bulk Tools menu has no items with tooltips at all, so there is nothing to compare -"
            + " either the menu has changed shape or it was not built");

        boolean carried = false;

        for (String tip : tips)
        {
            if (tip != null && tip.contains(I18n.t("ui.cancel"))
                && tip.contains(String.valueOf(session.tilesWithALocomotive().size())))
            {
                carried = true;
            }
        }

        assertTrue(carried,
            "no item on the Bulk Tools menu says what Cancel does about the clear, so the warning"
            + " arrives only after the item has been clicked. Tooltips: " + tips);
    }

    /**
     * The two length clears carry their sentence on the menu too, and it is the editor's one.  The track-length and
     * maximum pairs were built by copying the `page == null` choice into four places, so a swapped pair of keys would
     * show a clear promising a Cancel on the surface that has none.  Asked of the built menu, on the editor surface,
     * where Cancel is real.
     */
    @Test
    public void testTheLengthClearsCarryTheEditorsSentence() throws Exception
    {
        // All three clears have something to clear: a clear with nothing to do carries its "nothing to clear" notice
        // instead of the warning, and counting those would count the wrong sentence.
        TileKey measured = null;
        TileKey limited = null;

        for (TileKey square : session.getGraph().getTiles().keySet())
        {
            if (measured == null) { session.setTileLength(square, 5); measured = square; }
            else if (limited == null && session.assignMaxTrainLength(square, 40)) limited = square;
        }

        session.rebuild();

        assertFalse(session.tilesWithALength().isEmpty(),
            "precondition: no square took a length, so the track-length clear has nothing to warn about");

        assertFalse(session.tilesWithAMaxTrainLength().isEmpty(),
            "precondition: no station took a maximum, so that clear has nothing to warn about");

        // The set the menu item reads: the clear is gated on `placementsAutonomyWillWrite`, not on
        // `tilesWithALocomotive`, which walks every page.
        assertFalse(session.placementsAutonomyWillWrite().isEmpty(),
            "precondition: autonomy would write no placements, so that clear has nothing to warn about");

        final java.util.List<String> tips = new java.util.ArrayList<>();

        SwingUtilities.invokeAndWait(() -> collect(
            editor.getAutonomyPanel().buildBulkMenuForTest(), tips));

        assertFalse(tips.isEmpty(), "the Bulk Tools menu has no items with tooltips at all");

        int namingCancel = 0;

        for (String tip : tips)
        {
            if (tip != null && tip.contains(I18n.t("ui.cancel"))) namingCancel++;
        }

        // Three clears, three sentences that name Cancel (locomotives, track lengths, station maxima): a pair of keys
        // swapped in any of the four places that choose between the two sentences takes one away.
        assertTrue(namingCancel >= 3,
            "only " + namingCancel + " of this editor's bulk clears say what Cancel does, and there are"
            + " three - the locomotives, the track lengths and the station maxima.  One of them is showing"
            + " the track diagram's sentence, which promises the clear is already saved. Tooltips: " + tips);
    }

    /**
     * The tooltips count as the dialogs do: one measured square or one station is "1 square", not "1 squares", and two
     * take the plural, in every language (`I18n.countForm`).  Asked of the built menu, on the editor surface.
     *
     * MUTATION: build the track-length and station-maximum tooltips with `I18n.f(onThisSurface(key), count)`, as the
     * locomotive tooltip is, and the "1 squares" check fails.
     */
    @Test
    public void testTheTooltipsCountOneSquareAsOneSquare() throws Exception
    {
        session.clearEveryTileLength();
        session.clearEveryMaxTrainLength();

        java.util.List<TileKey> squares = new java.util.ArrayList<>(session.getGraph().getTiles().keySet());

        session.setTileLength(squares.get(0), 5);

        TileKey station = null;

        for (TileKey square : squares)
        {
            if (station == null && session.assignMaxTrainLength(square, 40)) station = square;
        }

        assertNotNull(station, "precondition: no station took a maximum, so there is no station tooltip to read");

        session.rebuild();

        assertEquals(session.tilesWithALength().size(), 1, "precondition: not exactly one measured square");
        assertEquals(session.tilesWithAMaxTrainLength().size(), 1, "precondition: not exactly one limited station");

        String oneLength = tipOf("autolayout.ui.menuClearAllTrackLengths", 1);
        String oneStation = tipOf("autolayout.ui.menuClearAllMaxTrainLengths", 1);

        assertFalse(oneLength.contains("1 squares"), "one measured square is called '1 squares': " + oneLength);
        assertFalse(oneStation.contains("1 stations"), "one limited station is called '1 stations': " + oneStation);

        assertTrue(oneLength.contains(I18n.f("autosetup.ui.tipClearAllTrackLengthsOne", 1)),
            "the track-length tooltip for one square is not the singular sentence: " + oneLength);

        assertTrue(oneStation.contains(I18n.f("autosetup.ui.tipClearAllMaxTrainLengthsOne", 1)),
            "the station-maximum tooltip for one station is not the singular sentence: " + oneStation);

        session.setTileLength(squares.get(1), 5);
        session.rebuild();

        String twoLengths = tipOf("autolayout.ui.menuClearAllTrackLengths", 2);

        assertTrue(twoLengths.contains(I18n.f("autosetup.ui.tipClearAllTrackLengths" + I18n.countForm(2), 2)),
            "the track-length tooltip for two squares is not the sentence for two: " + twoLengths);

        session.clearEveryTileLength();
        session.clearEveryMaxTrainLength();
    }

    /** The tooltip of the Bulk Tools item whose label is `key` counting `count`, as the built menu carries it. */
    private static String tipOf(String key, int count) throws Exception
    {
        final String label = I18n.f(key, count);
        final String[] tip = new String[1];

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JMenu bulk = editor.getAutonomyPanel().buildBulkMenuForTest();

            for (int at = 0; at < bulk.getItemCount(); at++)
            {
                javax.swing.JMenuItem item = bulk.getItem(at);

                if (item != null && label.equals(item.getText())) tip[0] = item.getToolTipText();
            }
        });

        assertNotNull(tip[0], "no Bulk Tools item reads '" + label + "' with a tooltip");

        return tip[0];
    }

    /**
     * The track diagram's own right-click menu does not promise a Cancel it does not have: the same Bulk Tools item is
     * there, but the clear is saved the moment it is made and the menu is not offered while an editor is open
     * (`TrainControlUI.buildAutonomyTileMenu`), so no editor's Cancel can reach it.
     */
    @Test
    public void testTheDiagramsOwnMenuDoesNotPromiseACancel() throws Exception
    {
        TileKey square = null;
        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(tile.getPage()) && session.getLocomotiveNameAt(tile) != null) square = tile;
        }
        assertNotNull(square, "precondition: no locomotive stands on " + PAGE + " to right-click");
        final TileKey at = square;
        final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];
        final String[] said = new String[1];
        final java.lang.reflect.Field panel = TrainControlUI.class.getDeclaredField("autonomyTileMenus");
        panel.setAccessible(true);
        // With no editor open, as a person meets the diagram's menu; built again straight after for the later claims.
        final LayoutEditor closing = editor;
        SwingUtilities.invokeAndWait(() -> closing.dispose());
        SwingUtilities.invokeAndWait(() ->
        {
            menu[0] = ui.buildAutonomyTileMenu(at);
            try
            {
                Object built = panel.get(ui);
                if (built != null) said[0] = ((org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel) built).clearLocomotivesWarning();
            }
            catch (IllegalAccessException failed)
            {
                throw new RuntimeException(failed);
            }
        });
        editor = buildTheEditor(model.getLayout(PAGE));
        assertNotNull(menu[0], "precondition: the track diagram offered no menu on " + at);
        assertNotNull(said[0], "precondition: the diagram's menu panel was not built");
        assertFalse(said[0].contains(I18n.t("ui.cancel")),
            "the track diagram's Clear All Locomotives warns that " + I18n.t("ui.cancel") + " puts them back,"
            + " and that door has no Cancel - the clear is saved as it is made. It says: " + said[0]);
        final java.util.List<String> tips = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() ->
        {
            for (java.awt.Component child : menu[0].getComponents())
            {
                if (child instanceof javax.swing.JMenu) collect((javax.swing.JMenu) child, tips);
            }
        });
        String count = String.valueOf(session.tilesWithALocomotive().size());
        boolean found = false;
        for (String tip : tips)
        {
            if (tip == null || !tip.contains(count)) continue;
            found = true;
            assertFalse(tip.contains(I18n.t("ui.cancel")),
                "a tooltip on the track diagram's menu promises " + I18n.t("ui.cancel") + ": " + tip);
        }
        assertTrue(found, "precondition: no tooltip on the track diagram's menu names the " + count
            + " squares the clear would empty, so the menu has changed shape. Tooltips: " + tips);
    }

    /** Every tooltip on a menu and its submenus. */
    private static void collect(javax.swing.JMenu menu, java.util.List<String> into)
    {
        if (menu == null) return;

        for (int at = 0; at < menu.getItemCount(); at++)
        {
            javax.swing.JMenuItem item = menu.getItem(at);

            if (item == null) continue;

            if (item.getToolTipText() != null) into.add(item.getToolTipText());

            if (item instanceof javax.swing.JMenu) collect((javax.swing.JMenu) item, into);
        }
    }
}
