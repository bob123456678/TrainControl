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
 * Adam, 2026-09-07, ruling on C26 - which had reported the drag writing the locomotive's name to the
 * system clipboard as an unwanted side effect: **"c26 is deliberate, that is the dragging functionality
 * you implemented on the keyboard. there should be tests to ensure no regression there."**
 *
 * There were none. `setCopyTarget`, `doPaste`, `getSwapTarget` and `clearCopyTarget` between them carry
 * copy, cut, move and swap across fifty pages, and not one line of it was covered - which is how a
 * reviewer came to read a deliberate feature as a defect in the first place. A behaviour nothing
 * asserts is a behaviour the next reader has to guess at.
 *
 * The four gestures are asserted through the public surface, on a real window. What they do to the
 * MAPPING is the thing that matters: the paint follows it, and a wrong mapping is a locomotive that
 * answers to somebody else's key.
 *
 * **The page is the hard part.** A copy remembers which page it started on, and a paste made after the
 * user has flipped pages has to clear or swap the SOURCE on its own page rather than on whichever one
 * happens to be showing. Every one of these tests that involves the source crossing a page boundary is
 * there because that is the assumption a refactor would quietly drop.
 *
 * The clipboard half of the gesture is pinned in `testEditorSurfaceRules` rather than here: asserting
 * it for real would mean overwriting whatever the person running the battery had copied, and a test
 * that costs its runner their clipboard is not worth what it proves.
 *
 * @author Adam
 */
public class testTheKeyboardDrag
{
    private static MarklinControlStation model;

    private static final String[] TEST_LOCS = { "KD one", "KD two", "KD three" };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        for (String name : TEST_LOCS)
        {
            if (model != null) model.deleteLoc(name);
        }
    }

    /**
     * A copy leaves the source where it is; a cut takes it off at once.
     *
     * The difference is the whole of the distinction between the two gestures, and it is made in an
     * unusual way: `setCopyTarget` performs the cut by nulling `copyTarget` and pasting that null over
     * the source button, then putting the real one back. So the same statement that arms the drag also
     * empties the key, and anything that reorders those three lines breaks cut without touching copy -
     * which a test of copy alone would report as clean.
     *
     * MUTATION: dropping the `if (cut)` block leaves the source populated and fails the second half.
     *
     * @throws Exception on a failure to build the window
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
     * A paste puts the locomotive down and disarms the drag.
     *
     * `doPaste` ends in `clearCopyTarget`, which is what stops one pick-up becoming many drops. Without
     * it the next click anywhere on the keyboard would paste again, and the person would have no way to
     * tell an armed drag from a finished one - `hasCopyTarget` is what the interface asks before it
     * offers Paste at all.
     *
     * MUTATION: removing the clearCopyTarget call fails the last two assertions.
     *
     * @throws Exception on a failure to build the window
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
     * A move empties the key it came from, on THAT key's page.
     *
     * The page is the point. `copyTargetPage` is recorded when the drag starts and the clear is applied
     * to `locMapping.get(copyTargetPage - 1)`, not to whatever page is showing when the paste happens -
     * and flipping pages mid-drag is the ordinary way to move a locomotive from page one to page three.
     *
     * Read the wrong way round, a move across pages empties a key on the destination page: a locomotive
     * the person never touched disappears, and the one they were moving is left behind on its old key.
     * Both halves are asserted for that reason.
     *
     * MUTATION: using the current page instead of copyTargetPage leaves the source populated.
     *
     * @throws Exception on a failure to build the window
     */
    @Test
    public void testAMoveAcrossPagesEmptiesTheKeyItCameFrom() throws Exception
    {
        TrainControlUI ui = build();

        List<JButton> keys = keysOf(ui);
        Locomotive one = loco("KD one");
        Locomotive two = loco("KD two");

        put(ui, 1, keys.get(0), one);

        // Something on the DESTINATION page's copy of the same key, so a clear applied to the wrong
        // page has a victim rather than writing null over null and looking correct.
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
     * A swap sends whatever was under the drop back to where the drag started.
     *
     * The exchange, and the one gesture with two writes to get right. `getSwapTarget` is what the
     * interface shows the person before they commit, so it has to name the same locomotive the paste
     * will actually move - an affordance describing a different outcome from the action behind it is
     * this project's most repeated defect.
     *
     * Across pages again, for the reason given above.
     *
     * MUTATION: swapping into the current page rather than copyTargetPage fails the third assertion.
     *
     * @throws Exception on a failure to build the window
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
     * A drag armed on an empty key is not a drag.
     *
     * `setCopyTarget` reads the mapping and stores whatever it finds, including null, and the clipboard
     * write below it is guarded on the result being non-null. `hasCopyTarget` must agree: an armed drag
     * carrying nothing would let a paste write null over a populated key, which is a delete performed by
     * a gesture that has no business deleting anything.
     *
     * @throws Exception on a failure to build the window
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
     * A window with a model behind it.
     *
     * The model is set by reflection rather than through setViewListener, which starts the threads a
     * connected window runs; nothing here needs them and a test that starts them has to stop them.
     *
     * @return the window
     * @throws Exception on a failure to build
     */
    private TrainControlUI build() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the keyboard is on a window");
        }

        // BEFORE the window: its constructor reads the machine-global layout preference and would
        // otherwise open Adam's real railway (OB-111).
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

        return built[0];
    }

    /**
     * Three of the keyboard’s locomotive keys, in a stable order.
     *
     * Taken from `buttonMapping`, which is the window’s real keyboard - key code to on-screen button,
     * built in the constructor.  `locMapping` would be the obvious place to look and is the wrong one:
     * it is empty until a saved state is loaded, because it holds only the keys that have a locomotive.
     * Reading it gave three tests that skipped straight past their preconditions.
     *
     * Sorted by key code so the same three come back in the same order every run - a HashMap’s
     * iteration order is not a promise, and a test whose source and destination swap between runs fails
     * intermittently for a reason nobody will find.
     *
     * @param ui the window
     * @return at least three keys
     * @throws Exception on a reflection failure
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
     * Puts a locomotive on a key on a given page, without going through the gesture under test.
     *
     * @param ui the window
     * @param page one-based, as the interface numbers them
     * @param key the button
     * @param loc the locomotive, or null to empty it
     * @throws Exception on a reflection failure
     */
    @SuppressWarnings("unchecked")
    private void put(TrainControlUI ui, int page, JButton key, Locomotive loc) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField("locMapping");

        f.setAccessible(true);

        ((List<HashMap<JButton, Locomotive>>) f.get(ui)).get(page - 1).put(key, loc);
    }

    /**
     * What is on a key on a given page.
     *
     * Asked of the page directly rather than through getButtonLocomotive, which only ever answers about
     * whichever page is showing - and which page a write landed on is the question these tests exist to
     * ask.
     *
     * @param ui the window
     * @param page one-based
     * @param key the button
     * @return the locomotive, or null
     * @throws Exception on a reflection failure
     */
    @SuppressWarnings("unchecked")
    private Locomotive at(TrainControlUI ui, int page, JButton key) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField("locMapping");

        f.setAccessible(true);

        return ((List<HashMap<JButton, Locomotive>>) f.get(ui)).get(page - 1).get(key);
    }

    /**
     * Moves the window to a page.
     *
     * By the field rather than switchLocMapping, which repaints tabs and selects them; the page NUMBER
     * is all these tests need and driving the tab strip would make them about the tab strip.
     *
     * @param ui the window
     * @param page one-based
     * @throws Exception on a reflection failure
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

    /**
     * A locomotive to drag, made once and reused.
     *
     * @param name one of TEST_LOCS
     * @return the locomotive
     */
    private MarklinLocomotive loco(String name)
    {
        MarklinLocomotive existing = model.getLocByName(name);

        if (existing != null) return existing;

        // MM2, and an address well clear of anything on a track diagram.  The real locomotive
        // database is open here, so the address matters: one already in use would hand back somebody
        // else’s locomotive and these tests would be dragging Adam’s trains around.
        int address = 240 + java.util.Arrays.asList(TEST_LOCS).indexOf(name);

        return model.newMM2Locomotive(name, address);
    }
}
