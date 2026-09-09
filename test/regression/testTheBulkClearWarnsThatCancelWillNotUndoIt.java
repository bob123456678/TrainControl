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
 * OB-194: Bulk Tools says what it is about to clear, and that Cancel will not bring it back.
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
 * rather than regenerated from the setup, and Cancel restores the FILE while the railway is what the
 * placements are read from.  Two halves of one sentence stopped agreeing, and nothing on screen said
 * so.
 *
 * **Asserted by asking the editor, not by reading its source.**  The panel is stood up over the frozen
 * railway with trains standing on it, and the warning is the string the confirmation will show and the
 * tooltip already shows - one builder, so the two cannot say different things.
 *
 * **Two claims that are not about English.**  The warning must name every locomotive it is about to
 * lift - a count is not something anybody can check - and it must name the button that will NOT undo
 * it, asked as `I18n.t("ui.cancel")`, which is the same key the editor's own Cancel button carries.
 * Both hold in whichever of the eight languages the run happens to be in.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).
 *
 * MUTATION: take `{1}` out of the message and the naming claim fails; take the Cancel sentence out and
 * the second does; point the tooltip back at `I18n.t` and the third does.
 *
 * @author Adam
 */
public class testTheBulkClearWarnsThatCancelWillNotUndoIt
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
     * And it names the button that will NOT undo it.
     *
     * Adam's sentence exactly - *"cannot be undone by a cancel.  Make this clear in the popup"* - and
     * asked in a way that is true in all eight languages: `ui.cancel` is the key the editor's own
     * Cancel button carries, so whatever that button says, this warning says it too.
     */
    @Test
    public void testTheWarningNamesTheCancelThatWillNotUndoIt()
    {
        assertNotNull(warning, "the editor produced no warning at all");

        assertTrue(warning.contains(I18n.t("ui.cancel")),
            "the warning does not mention " + I18n.t("ui.cancel") + " - the editor's own button - so"
            + " nothing tells the operator that closing without saving will not put the locomotives"
            + " back, which is the whole of OB-194. It says: " + warning);
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
            "no item on the Bulk Tools menu warns that Cancel will not undo the clear, so the warning"
            + " arrives only after the item has been clicked. Tooltips: " + tips);
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
