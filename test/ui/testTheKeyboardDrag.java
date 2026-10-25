package ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.JButton;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.gui.TrainControlUI;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Dragging a locomotive from one keyboard key to another.
 *
 * Adam, on C26 (the drag writing the locomotive's name to the system clipboard): "c26 is deliberate, that is the
 * dragging functionality you implemented on the keyboard. there should be tests to ensure no regression there."
 *
 * `setCopyTarget`, `doPaste`, `getSwapTarget` and `clearCopyTarget` carry copy, cut, move and swap across fifty pages.
 * The four gestures are asserted through the public surface, on a real window; what they do to the MAPPING is what
 * matters, since a wrong mapping is a locomotive that answers to somebody else's key.
 *
 * **The page is the hard part.** A copy remembers which page it started on, and a paste made after the user has flipped
 * pages has to clear or swap the SOURCE on its own page rather than on whichever one is showing.
 *
 * **These tests DO write to the system clipboard**, because the real gesture puts the locomotive name on it (C26,
 * deliberate).  `build` saves whatever text was on the clipboard and restores it afterwards; a non-text clipboard
 * cannot be restored.  No assertion here reads the clipboard: the write itself is pinned in `testEditorSurfaceRules`.
 *
 * @author Adam
 */
public class testTheKeyboardDrag
{
    private static MarklinControlStation model;

    private static final String[] TEST_LOCS = { "KD one", "KD two", "KD three" };

    /** The copy this whole class runs against - see setUpClass. */
    private static support.LayoutSandbox classSandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // A THROWAWAY COPY OF THE LAYOUT, opened before the model is built (OB-111): `init` reads the layout preference
        // and loads whatever it names, and it runs here, so the class would read Adam's real railway before any test
        // could redirect it.
        classSandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();
        model.stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (classSandbox != null) classSandbox.close();
        for (String name : TEST_LOCS)
        {
            if (model != null) model.deleteLoc(name);
        }
    }

    /**
     * A copy leaves the source where it is; a cut takes it off at once.  `setCopyTarget` performs the cut by nulling
     * `copyTarget` and pasting that null over the source button, then putting the real one back, so anything that
     * reorders those three lines breaks cut without touching copy.
     *
     * MUTATION: dropping the `if (cut)` block leaves the source populated and fails the second half.
     */
    @Test
    public void testACopyLeavesTheSourceAndACutTakesItOff() throws Exception
    {
        TrainControlUI ui = build();

        List<JButton> keys = keysOf(ui);
        Locomotive one = loco("KD one");

        put(ui, 1, keys.get(0), one);

        assertSame(ui.getButtonLocomotive(keys.get(0)), one, "precondition: the key holds it");

        ui.setCopyTarget(keys.get(0), false);

        assertSame(ui.getCopyTarget(), one, "a copy did not pick the locomotive up at all");

        assertSame(ui.getButtonLocomotive(keys.get(0)), one,
            "a COPY emptied the key it copied from. Copy and cut differ in exactly this, and nothing"
            + " else distinguishes them");

        ui.clearCopyTarget();

        ui.setCopyTarget(keys.get(0), true);

        assertSame(ui.getCopyTarget(), one, "a cut did not pick the locomotive up");

        assertNull(ui.getButtonLocomotive(keys.get(0)),
            "a CUT left the locomotive on its key, so the drag now shows it in two places and the"
            + " second one is a lie until the paste lands - or forever, if it never does");
    }

    /**
     * A paste puts the locomotive down and disarms the drag.  `doPaste` ends in `clearCopyTarget`, which stops one
     * pick-up becoming many drops; `hasCopyTarget` is what the interface asks before it offers Paste at all.
     *
     * MUTATION: removing the clearCopyTarget call fails the last two assertions.
     */
    @Test
    public void testAPastePutsItDownAndDisarmsTheDrag() throws Exception
    {
        TrainControlUI ui = build();

        List<JButton> keys = keysOf(ui);
        Locomotive one = loco("KD one");

        put(ui, 1, keys.get(0), one);

        ui.setCopyTarget(keys.get(0), false);

        assertTrue(ui.hasCopyTarget(), "precondition: the drag is armed");

        ui.doPaste(keys.get(1), false, false);

        assertSame(ui.getButtonLocomotive(keys.get(1)), one,
            "the paste did not land - the target key is not holding the locomotive that was dragged"
            + " onto it");

        assertFalse(ui.hasCopyTarget(),
            "the drag is still armed after it landed, so the next click pastes a second copy and"
            + " nothing on screen distinguishes a live drag from a finished one");

        assertNull(ui.getCopyTarget(),
            "hasCopyTarget says the drag is over and getCopyTarget still hands the locomotive out."
            + " Two answers to one question is how the interface and the action come to disagree");
    }

    /**
     * A move empties the key it came from, on THAT key's page: `copyTargetPage` is recorded when the drag starts and
     * the clear goes to `locMapping.get(copyTargetPage - 1)`, not to the page showing at paste time.  Read the wrong
     * way round, a move across pages empties a key on the destination page, so both halves are asserted.
     *
     * MUTATION: using the current page instead of copyTargetPage leaves the source populated.
     */
    @Test
    public void testAMoveAcrossPagesEmptiesTheKeyItCameFrom() throws Exception
    {
        TrainControlUI ui = build();

        List<JButton> keys = keysOf(ui);
        Locomotive one = loco("KD one");
        Locomotive two = loco("KD two");

        put(ui, 1, keys.get(0), one);

        // Something on the DESTINATION page's copy of the same key, so a clear on the wrong page has a victim.
        put(ui, 2, keys.get(0), two);

        ui.setCopyTarget(keys.get(0), false);

        page(ui, 2);

        ui.doPaste(keys.get(1), false, true);

        assertSame(at(ui, 2, keys.get(1)), one,
            "the move did not land on the page it was dropped on");

        assertNull(at(ui, 1, keys.get(0)),
            "the source key on page one still holds the locomotive after a move, so it is now on two"
            + " keys and a move has become a copy");

        assertSame(at(ui, 2, keys.get(0)), two,
            "the move cleared the same key on the WRONG page - a locomotive nobody touched has"
            + " vanished from page two while the one being moved stayed put on page one");
    }

    /**
     * A swap sends whatever was under the drop back to where the drag started, across pages again.  `getSwapTarget` is
     * what the interface shows before the person commits, so it has to name the locomotive the paste will actually
     * move.
     *
     * MUTATION: swapping into the current page rather than copyTargetPage fails the third assertion.
     */
    @Test
    public void testASwapAcrossPagesExchangesTheTwoKeys() throws Exception
    {
        TrainControlUI ui = build();

        List<JButton> keys = keysOf(ui);
        Locomotive one = loco("KD one");
        Locomotive three = loco("KD three");

        put(ui, 1, keys.get(0), one);
        put(ui, 2, keys.get(1), three);

        ui.setCopyTarget(keys.get(0), false);

        page(ui, 2);

        assertSame(ui.getSwapTarget(), one,
            "getSwapTarget names the wrong locomotive. It is what the menu shows before the swap is"
            + " committed, so it has to be the one the swap will actually move");

        ui.doPaste(keys.get(1), true, false);

        assertSame(at(ui, 2, keys.get(1)), one,
            "the dragged locomotive did not land on the key it was dropped on");

        assertSame(at(ui, 1, keys.get(0)), three,
            "the key the drag started from did not receive what was under the drop, so the swap only"
            + " went one way and one of the two locomotives is now unmapped");
    }

    /**
     * A drag armed on an empty key is not a drag: `hasCopyTarget` must agree with the guarded clipboard write, or a
     * paste would write null over a populated key, a delete by a gesture that has no business deleting.
     */
    @Test
    public void testDraggingAnEmptyKeyArmsNothing() throws Exception
    {
        TrainControlUI ui = build();

        List<JButton> keys = keysOf(ui);

        put(ui, 1, keys.get(0), null);

        assertFalse(ui.buttonHasLocomotive(keys.get(0)), "precondition: the key is empty");

        ui.setCopyTarget(keys.get(0), false);

        assertFalse(ui.hasCopyTarget(),
            "an empty key armed a drag. Pasting it writes null over whatever it lands on, which"
            + " deletes a mapping through a gesture that only ever moves them");
    }

    // ---------------------------------------------------------------- the fixture

    /**
     * A window with a model behind it.  The model is set by reflection rather than through setViewListener, which
     * starts the threads a connected window runs; nothing here needs them.
     */
    private TrainControlUI build() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the keyboard is on a window");
        }

        // BEFORE the window: its constructor reads the machine-global layout preference and would open Adam's real
        // railway (OB-111).
        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] built = new TrainControlUI[1];

        try
        {
            sandbox = support.LayoutSandbox.open();

            javax.swing.SwingUtilities.invokeAndWait(() -> built[0] = new TrainControlUI());
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }

        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField("model");

        f.setAccessible(true);
        f.set(built[0], model);

        // WHATEVER WAS ON THE CLIPBOARD, so the battery can be run without losing it; put back by
        // `giveTheClipboardBack`.
        priorClipboard = readClipboard();

        return built[0];
    }

    /** Whatever text was on the system clipboard before a test ran, or null. */
    private String priorClipboard;

    /** The clipboard as text, or null when it holds something else or cannot be read. */
    private String readClipboard()
    {
        try
        {
            java.awt.datatransfer.Transferable held = java.awt.Toolkit.getDefaultToolkit()
                .getSystemClipboard().getContents(null);

            if (held == null
                || !held.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor))
            {
                return null;
            }

            return (String) held.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
        }
        catch (Exception cannotRead)
        {
            // A locked or non-text clipboard.  Nothing to give back, and nothing worth failing over.
            return null;
        }
    }

    /** Puts back whatever text the clipboard held before the test. */
    @org.testng.annotations.AfterMethod(alwaysRun = true)
    public void giveTheClipboardBack()
    {
        if (priorClipboard == null) return;

        try
        {
            java.awt.datatransfer.StringSelection back =
                new java.awt.datatransfer.StringSelection(priorClipboard);

            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(back, back);
        }
        catch (Exception cannotRestore)
        {
            // The same lock the code under test now survives.  Reported by not being restored.
        }
    }

    /**
     * Three of the keyboard's locomotive keys, in a stable order.  Taken from `buttonMapping`, the window's real
     * keyboard built in the constructor: `locMapping` is empty until a saved state is loaded, and reading it gave three
     * tests that skipped past their preconditions.  Sorted by key code, since a HashMap's iteration order would make
     * source and destination swap between runs and fail intermittently.
     */
    @SuppressWarnings("unchecked")
    private List<JButton> keysOf(TrainControlUI ui) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField("buttonMapping");

        f.setAccessible(true);

        HashMap<Integer, JButton> board = (HashMap<Integer, JButton>) f.get(ui);

        List<Integer> codes = new ArrayList<>(board.keySet());

        java.util.Collections.sort(codes);

        List<JButton> keys = new ArrayList<>();

        for (Integer code : codes)
        {
            if (board.get(code) != null) keys.add(board.get(code));
        }

        assertTrue(keys.size() >= 3,
            "the window has " + keys.size() + " locomotive keys, and these tests need three distinct"
            + " ones. Without them a source and a destination could be the same key and every"
            + " assertion below would be about one square");

        return keys;
    }

    /**
     * Puts a locomotive on a key on a given page (one-based), or empties it with null, without going through the
     * gesture under test.
     */
    @SuppressWarnings("unchecked")
    private void put(TrainControlUI ui, int page, JButton key, Locomotive loc) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField("locMapping");

        f.setAccessible(true);

        ((List<HashMap<JButton, Locomotive>>) f.get(ui)).get(page - 1).put(key, loc);
    }

    /**
     * What is on a key on a given page (one-based), asked of the page directly: getButtonLocomotive only answers about
     * the page showing.
     */
    @SuppressWarnings("unchecked")
    private Locomotive at(TrainControlUI ui, int page, JButton key) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField("locMapping");

        f.setAccessible(true);

        return ((List<HashMap<JButton, Locomotive>>) f.get(ui)).get(page - 1).get(key);
    }

    /**
     * Moves the window to a page by the field rather than switchLocMapping, which would make these tests about the tab
     * strip.
     */
    private void page(TrainControlUI ui, int page) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField("locMappingNumber");

        f.setAccessible(true);
        f.setInt(ui, page);

        java.lang.reflect.Field count = TrainControlUI.class.getDeclaredField("numLocMappings");

        count.setAccessible(true);

        if (count.getInt(ui) < page) count.setInt(ui, page);
    }

    /** A locomotive to drag, made once and reused. */
    private MarklinLocomotive loco(String name)
    {
        MarklinLocomotive existing = model.getLocByName(name);

        if (existing != null) return existing;

        // MM2, and an address well clear of anything on a track diagram: the real database is open here, and an address
        // in use would hand back somebody else's locomotive.
        int address = 240 + java.util.Arrays.asList(TEST_LOCS).indexOf(name);

        return model.newMM2Locomotive(name, address);
    }
}
