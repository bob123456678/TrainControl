package regression;

import javax.swing.SwingUtilities;
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
 * OB-194: Bulk Tools says what it is about to clear, and what Cancel will do about it.
 *
 * **Renamed on 2026-09-14 from `testTheBulkClearWarnsThatCancelWillNotUndoIt` (WK7-C2).**  Since OB-223 Cancel in
 * the autonomy editor restores the setup as it opened, and the locomotives a clear took off come back -
 * `regression.testCancelUndoesAutonomyEdits.testCancelPutsBackTheLocomotivesABulkClearTook` runs it - so the
 * warning says Cancel puts them back and Save keeps the change.  What follows is the class as it was written,
 * when Cancel could not.
 *
 * Adam, on MT-311 (2026-09-08): **"It works, but bug: clearning locomotives in the autonomy editor
 * cannot be undone by a cancel.  Make this clear in the popup."**  And his ruling of 2026-09-09:
 * **"Warning if using the bulk tool."**  So the answer is the warning, not a real undo - the smaller
 * of the two, and the one he asked for.
 *
 * **What changed underneath, and why the entry that passed said the opposite.**  MT-311's own
 * expectation reads *"neither writes to disk - Cancel puts everything back"*, which was true when it
 * was written.  OB-183 changed where a placement comes from - Adam, 2026-09-08: *"Where a train IS is
 * a fact, and where the file thinks it is is a record"* - so placements are carried across a rebuild
 * rather than regenerated from the setup, and Cancel then restored the FILE while the railway was what the
 * placements were read from.  Two halves of one sentence stopped agreeing, and nothing on screen said
 * so.
 *
 * **Asserted by asking the editor, not by reading its source.**  The panel is stood up over the frozen
 * railway with trains standing on it, and the warning is the string the confirmation will show and the
 * tooltip already shows - one builder, so the two cannot say different things.
 *
 * **Two claims that are not about English.**  The warning must name every locomotive it is about to
 * lift - a count is not something anybody can check - and it must name Cancel, which since OB-223 is the
 * button that undoes it, asked as `I18n.t("ui.cancel")`, the same key the editor's own Cancel button
 * carries.  Both hold in whichever of the eight languages the run happens to be in.  And on the track
 * diagram's own menu, where there is no Cancel, it must not name one (WKV-B1).
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).
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

        final String[] said = new String[1];

        SwingUtilities.invokeAndWait(
            () -> said[0] = editor.getAutonomyPanel().clearLocomotivesWarning());

        warning = said[0];
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

    /**
     * The warning names every locomotive the clear is about to lift.
     *
     * A bulk gesture is one answer about all of them, and a count is a number nobody can check against
     * the railway in front of them.  The same argument `placementChanged` is given its list for.
     */
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
     * And it names Cancel, which since OB-223 is the button that DOES undo it (WK7-C2).
     *
     * Adam's sentence exactly - *"cannot be undone by a cancel.  Make this clear in the popup"* - and
     * asked in a way that is true in all eight languages: `ui.cancel` is the key the editor's own
     * Cancel button carries, so whatever that button says, this warning says it too.
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
     * The tooltip on the menu item says the same thing, from the same builder.
     *
     * A warning that appears only after the item has been clicked is a warning half the time.  Asked
     * of the built menu rather than of the source, so a tooltip set from a different string would be
     * caught rather than described.
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
     * The two LENGTH clears carry their sentence on the menu too, and it is the editor's one (OP2-C2).
     *
     * **Nothing ran either sentence.**  AUS-C2 built the track-length and maximum pairs by copying the
     * `page == null` choice into four places, and the commit said
     * `testCancelUndoesAutonomyEdits.testCancelPutsTheLengthsBack` ran it - which it does not: that test
     * calls `session.clearEveryTileLength()` and never goes near a sentence.  So a swapped pair of keys in
     * one of the four would have shown a clear promising a Cancel on the surface that has none, and nothing
     * would have said so.
     *
     * Asked of the built menu, like the locomotive claim above it, and of the same editor surface - where
     * Cancel is real, so the sentence naming it is the true one here.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheLengthClearsCarryTheEditorsSentence() throws Exception
    {
        // ALL THREE CLEARS HAVE SOMETHING TO CLEAR.  A clear with nothing to do carries its "nothing to
        // clear" notice instead of the warning, and a test that counted those would be counting the wrong
        // sentence - which is what the first draft of this did.
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

        // THE SET THE MENU ITEM READS (FXV-C7).  `tilesWithALocomotive` walks every page; the clear is
        // gated on `placementsAutonomyWillWrite`, and the comment beside the item says the two disagree -
        // so asserting the wrong one could have left the item disabled with its notice showing while this
        // test believed it was reading a warning.
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

        // THREE CLEARS, THREE SENTENCES THAT NAME CANCEL.  The locomotives, the track lengths and the
        // station maxima: on this surface all three are undone by Cancel, so all three say so.  A pair of
        // keys swapped in any of the four places that choose between the two sentences takes one of these
        // away, which is the mistake the copied ternary made possible and nothing was watching for.
        assertTrue(namingCancel >= 3,
            "only " + namingCancel + " of this editor's bulk clears say what Cancel does, and there are"
            + " three - the locomotives, the track lengths and the station maxima.  One of them is showing"
            + " the track diagram's sentence, which promises the clear is already saved. Tooltips: " + tips);
    }

    /**
     * The track diagram's own right-click menu does not promise a Cancel it does not have (WKV-B1).
     *
     * The same Bulk Tools item is on the diagram's tile menu - this panel's menus served with no page - and
     * there the clear is saved the moment it is made: the diagram's panel saves the setup on every change, and
     * its menu is not offered at all while an editor is open (`TrainControlUI.buildAutonomyTileMenu`), so no
     * editor's Cancel can reach it.  A warning saying Cancel puts the locomotives back is false on that door.
     * Asked of the real diagram menu, through `buildAutonomyTileMenu`.
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
        SwingUtilities.invokeAndWait(() ->
        {
            menu[0] = ui.buildAutonomyTileMenu(at);
            try
            {
                Object built = panel.get(ui);
                if (built != null) said[0] = ((org.traincontrol.gui.AutonomyEditorPanel) built).clearLocomotivesWarning();
            }
            catch (IllegalAccessException failed)
            {
                throw new RuntimeException(failed);
            }
        });
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

    /**
     * Every tooltip on a menu and its submenus.
     *
     * @param menu the menu
     * @param into where to put them
     */
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
