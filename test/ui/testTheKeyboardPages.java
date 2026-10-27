package ui;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Keys;
import support.Mouse;

/**
 * The locomotive keyboard's pages as the operator works them: deleting a page from the page menu, jumping to a
 * locomotive with Quick Find, mapping the unassigned locomotives, and dragging a locomotive from one key to another.
 *
 * Every claim starts from six empty pages with no names.  The page count is a preference, so the class puts Adam's
 * value back (or removes it, when he has none) and never saves the window's state.
 *
 * A real pointer drag hands the drag to the operating system and never gives it back.  So a drag is made the way Swing
 * makes it, against the real handler and each component's own drop target: the pick-up, the pointer resting over a
 * page control, the drop on a key, and the end of a drag dropped nowhere.
 *
 * MUTATION: each claim names the change to the program that turns it red.
 *
 * @author Adam
 */
public class testTheKeyboardPages
{
    private static final String ONE = "KP one";
    private static final String MORE = "KP one more";
    private static final String GHOST = "KP ghost";

    private static final String[] NAMES = {ONE, MORE, GHOST};

    private static final int PAGES = 6;

    private static final long WAIT = 8000;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static Integer pagesPrefWas;

    private static MarklinLocomotive one;
    private static MarklinLocomotive more;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the keyboard is on a window");

        sandbox = support.LayoutSandbox.open();

        int stored = TrainControlUI.getPrefs().getInt(TrainControlUI.LOC_MAPPING_PAGES_PREF, -1);

        pagesPrefWas = stored < 0 ? null : stored;

        model = support.TestStation.simulatedWithWindowInDebug();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        Components.settle();

        for (String name : NAMES)
        {
            if (model.getLocByName(name) != null) model.deleteLoc(name);
        }

        one = model.newMFXLocomotive(ONE, 3411);
        more = model.newMFXLocomotive(MORE, 3412);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (pagesPrefWas == null) TrainControlUI.getPrefs().remove(TrainControlUI.LOC_MAPPING_PAGES_PREF);
            else TrainControlUI.getPrefs().putInt(TrainControlUI.LOC_MAPPING_PAGES_PREF, pagesPrefWas);

            if (model != null)
            {
                for (String name : NAMES)
                {
                    if (model.getLocByName(name) != null) model.deleteLoc(name);
                }
            }

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() ->
                {
                    closing.stopDrivingTrainsFromTheWholeWindow();
                    closing.dispose();
                });
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** Six pages, none named, nothing on any key, page 1 showing with its Q key chosen. */
    @BeforeMethod
    public void lay() throws Exception
    {
        closeStrayDialogs();

        Components.onTheEventThread(() ->
        {
            for (Map<JButton, Locomotive> page : pages()) page.clear();

            ((Map<?, ?>) field("pageNames")).clear();

            return null;
        });

        pagesTo(PAGES);

        Components.onTheEventThread(() ->
        {
            for (Map<JButton, Locomotive> page : pages()) page.clear();

            ((Map<?, ?>) field("pageNames")).clear();

            // A window just opened has searched for nothing yet
            setField("lastSearch", "");
            ((Set<?>) field("lastResults")).clear();

            call("retitlePages");

            return null;
        });

        turnTo(1);

        Keys.press(keyboard(), KeyEvent.VK_Q);

        assertTrue(Components.waitFor(() -> field0("currentButton") == key(KeyEvent.VK_Q), WAIT),
            "precondition: Q did not choose its key");
        assertEquals(ui.getNumLocMappings(), PAGES, "precondition: there are not six pages");
    }

    // Deleting a page

    /**
     * Delete Page, from the page menu, takes away an empty page: the pages after it move up with their trains and
     * their names, the page number that was deleted now shows what followed it, and the new count is what is stored.
     *
     * MUTATION: loop `at < going` instead of `at < this.numLocMappings` where `deleteCurrentLocMappingPage` moves
     * the names up, and the names stay on the wrong pages.
     */
    @Test
    public void testDeletingAnEmptyPageMovesTheLaterOnesUp() throws Exception
    {
        nameThePage(3, "Shed");
        nameThePage(4, "Yard");
        nameThePage(6, "Depot");

        put(4, KeyEvent.VK_A, one);
        put(6, KeyEvent.VK_S, more);

        turnTo(3);

        deleteThroughTheMenu(3);

        assertEquals(ui.getNumLocMappings(), PAGES - 1, "the page count did not fall by one");
        assertEquals((int) Components.onTheEventThread(tabs()::getTabCount), PAGES - 1, "the page tabs did not fall by one");
        assertEquals(ui.getLocMappingNumber(), 3, "the page showing is not the one that took the deleted page's place");

        assertTrue(titleOfPage(3).contains("Yard"), "page 3 is not the old page 4, Yard; it reads " + titleOfPage(3));
        assertTrue(titleOfPage(5).contains("Depot"), "page 5 is not the old page 6, Depot; it reads " + titleOfPage(5));

        for (int page = 1; page <= PAGES - 1; page++)
        {
            assertFalse(titleOfPage(page).contains("Shed"), "the deleted page's name is still on page " + page);
        }

        assertEquals(titleOfPage(1), I18n.f("page.ui.pageNo", 1), "an unnamed page is not worded as Page 1");
        assertEquals(titleOfPage(4), I18n.f("page.ui.pageNo", 4), "page 4, now unnamed, is not worded as Page 4");

        assertSame(onKey(3, KeyEvent.VK_A), one, "the train that was on page 4 did not move up to page 3");
        assertSame(onKey(5, KeyEvent.VK_S), more, "the train that was on page 6 did not move up to page 5");

        assertEquals(TrainControlUI.getPrefs().getInt(TrainControlUI.LOC_MAPPING_PAGES_PREF, -1), PAGES - 1,
            "the new page count is not what the next start would open with");
    }

    /**
     * Deleting the last page leaves the others as they were, shows the page that is last now, and Next is grayed on it.
     *
     * MUTATION: `this.locMapping.remove(0)` in place of `remove(going - 1)` in `deleteCurrentLocMappingPage`, and the
     * trains move up a page.
     */
    @Test
    public void testDeletingTheLastPageLeavesTheOthersAlone() throws Exception
    {
        put(1, KeyEvent.VK_E, more);
        put(5, KeyEvent.VK_W, one);

        turnTo(PAGES);

        deleteThroughTheMenu(PAGES);

        assertEquals(ui.getNumLocMappings(), PAGES - 1, "the page count did not fall by one");
        assertTrue(Components.waitFor(() -> ui.getLocMappingNumber() == PAGES - 1, WAIT),
            "the window is not on the page that is last now");
        assertEquals((int) Components.onTheEventThread(tabs()::getSelectedIndex), PAGES - 2, "the tab shown is not the last");
        assertFalse(Components.onTheEventThread(((JButton) field("NextLocMapping"))::isEnabled),
            "Next is still lit on the last page");

        assertSame(onKey(1, KeyEvent.VK_E), more, "the train on page 1 is not where it was");
        assertSame(onKey(5, KeyEvent.VK_W), one, "the train on page 5 is not where it was");
    }

    /**
     * A page with a locomotive on it cannot be deleted: its menu item is grayed, and the method itself says why and
     * changes nothing, the locomotive still on its key.
     *
     * MUTATION: delete `if (on != null) return false;` in `canDeleteCurrentPage`, and the item is lit and the page goes.
     */
    @Test
    public void testAPageWithATrainCannotBeDeleted() throws Exception
    {
        put(2, KeyEvent.VK_D, one);

        turnTo(2);

        AbstractButton item = pageMenuItem(I18n.f("page.ui.menuDeletePage", 2));

        assertFalse(Components.onTheEventThread(item::isEnabled), "Delete Page is lit over a page with a train on it");

        Mouse.closeMenus();

        String said = askedByDeleting();

        assertEquals(said, I18n.t("page.ui.errorPageNotEmpty"), "deleting a page with a train did not say why not");
        assertEquals(ui.getNumLocMappings(), PAGES, "the page with a train was deleted");
        assertSame(onKey(2, KeyEvent.VK_D), one, "the train left its key");
    }

    /**
     * There are always at least two pages: at two, an empty page's Delete item is grayed, and the method says so and
     * changes nothing.
     *
     * MUTATION: delete the `numLocMappings <= MIN_LOC_MAPPINGS` test from `canDeleteCurrentPage`, and the last page
     * goes.
     */
    @Test
    public void testTwoPagesStay() throws Exception
    {
        pagesTo(2);

        turnTo(2);

        AbstractButton item = pageMenuItem(I18n.f("page.ui.menuDeletePage", 2));

        assertFalse(Components.onTheEventThread(item::isEnabled), "Delete Page is lit with only two pages");

        Mouse.closeMenus();

        assertEquals(askedByDeleting(), I18n.f("page.ui.errorNeedAtLeast", 2), "the refusal does not say why");
        assertEquals(ui.getNumLocMappings(), 2, "a page was deleted below two");
    }

    // Quick Find

    /**
     * Quick Find, from the Locomotive menu, takes the window to the page and key where the locomotive is mapped, and
     * makes it the one driven; the name is read in any case.
     *
     * MUTATION: `if (false)` for the `locMappingNumber != e.getPage()` test in `jumpToLocomotive`, and the window stays
     * on its page.
     */
    @Test
    public void testQuickFindGoesToTheKeyThePageAndTheLocomotive() throws Exception
    {
        put(4, KeyEvent.VK_E, one);

        JMenuItem find = (JMenuItem) Components.withText(menuBar(), I18n.t("ui.main.toolbar.quickFindLoc"));

        assertNotNull(find, "the Locomotive menu has no Quick Find");

        try (Answering answering = Answering.start(message -> Answering.type("KP ONE")))
        {
            Mouse.choose(find);

            assertTrue(answering.awaitAsked(1, WAIT), "Quick Find asked nothing");
            assertEquals(answering.asked().get(0), I18n.t("loc.ui.promptEnterLocomotiveNameToJumpTo"),
                "Quick Find does not ask for a name");
        }

        assertTrue(Components.waitFor(() -> ui.getLocMappingNumber() == 4, WAIT), "Quick Find did not turn to page 4");
        assertTrue(Components.waitFor(() -> field0("currentButton") == key(KeyEvent.VK_E), WAIT),
            "Quick Find did not choose the key");
        assertTrue(Components.waitFor(() -> ui.getActiveLoc() == one, WAIT), "Quick Find did not make it the one driven");
        assertEquals((int) Components.onTheEventThread(tabs()::getSelectedIndex), 3, "the page tab is not page 4");
    }

    /**
     * Asking again with nothing typed repeats the search and goes to the next key holding the locomotive, in page
     * order, round to the first again; the key it is on is never the answer unless it is the only one.
     *
     * MUTATION: delete the second `this.lastResults.clear();` in `jumpToLocomotive`, and the search stops after one
     * round.
     */
    @Test
    public void testAskingAgainVisitsEachKeyInTurn() throws Exception
    {
        put(1, KeyEvent.VK_A, one);
        put(2, KeyEvent.VK_B, one);
        put(5, KeyEvent.VK_C, one);

        turnTo(1);
        choose(KeyEvent.VK_A);

        search("kp one");

        assertVisited(2, KeyEvent.VK_B);

        search("");

        assertVisited(5, KeyEvent.VK_C);

        search("");

        assertVisited(1, KeyEvent.VK_A);

        search("");

        assertVisited(2, KeyEvent.VK_B);
    }

    /**
     * With the locomotive on exactly two keys and the first search made from one of them, it goes to the other.
     *
     * MUTATION: in `jumpToLocomotive`, skip the current key only while `> 1` keys are left unseen, and the search stays put.
     */
    @Test
    public void testTwoKeysTheFirstSearchGoesToTheOther() throws Exception
    {
        put(1, KeyEvent.VK_A, one);
        put(2, KeyEvent.VK_B, one);

        turnTo(1);
        choose(KeyEvent.VK_A);

        search("kp one");

        assertVisited(2, KeyEvent.VK_B);

        search("");

        assertVisited(1, KeyEvent.VK_A);
    }

    /**
     * A remap between two searches does not cut the round short: a key that no longer holds the locomotive is forgotten,
     * so every key that does is visited before the round starts again (OB-386).
     *
     * MUTATION: delete the `this.lastResults.retainAll(results);` in `jumpToLocomotive`, and page 4 is never visited.
     */
    @Test
    public void testARemapBetweenSearchesSkipsNoKey() throws Exception
    {
        put(1, KeyEvent.VK_A, one);
        put(2, KeyEvent.VK_B, one);

        turnTo(5);
        choose(KeyEvent.VK_Q);

        search("kp one");

        assertVisited(1, KeyEvent.VK_A);

        // Off page 1, and on pages 3 and 4
        put(1, KeyEvent.VK_A, null);
        put(3, KeyEvent.VK_C, one);
        put(4, KeyEvent.VK_D, one);

        search("");

        assertVisited(2, KeyEvent.VK_B);

        search("");

        assertVisited(3, KeyEvent.VK_C);

        search("");

        assertVisited(4, KeyEvent.VK_D);

        search("");

        assertVisited(2, KeyEvent.VK_B);
    }

    /**
     * A name that is exactly a locomotive's is answered before one that merely contains it, whatever the page order.
     *
     * MUTATION: compare with `equals("")` in the first loop of `getAllLocButtonMappingsMap`, and the earlier page wins.
     */
    @Test
    public void testAnExactNameBeatsALongerOne() throws Exception
    {
        put(1, KeyEvent.VK_A, more);
        put(3, KeyEvent.VK_B, one);

        turnTo(5);
        choose(KeyEvent.VK_Q);

        search("kp one");

        assertVisited(3, KeyEvent.VK_B);

        search("kp one");

        assertVisited(1, KeyEvent.VK_A);
    }

    /**
     * A name nobody holds goes nowhere.
     *
     * MUTATION: `contains("")` in place of `contains(s.trim().toLowerCase())` in `getAllLocButtonMappingsMap`, and
     * the search finds every key.
     */
    @Test
    public void testAMissingNameGoesNowhere() throws Exception
    {
        put(3, KeyEvent.VK_B, one);

        turnTo(5);
        choose(KeyEvent.VK_W);

        search("no such locomotive");

        assertNothingMoved(5, KeyEvent.VK_W);
    }

    /**
     * Canceling Quick Find goes nowhere and forgets nothing: asking again with nothing typed still repeats the last
     * name.
     *
     * MUTATION: `if (true)` in place of `if (input != null)` in `LocomotiveActions.quickLocSearch`, and the repeat
     * after the cancel finds no last name.
     */
    @Test
    public void testCancelingQuickFindChangesNothing() throws Exception
    {
        put(1, KeyEvent.VK_A, one);
        put(2, KeyEvent.VK_B, one);

        turnTo(3);
        choose(KeyEvent.VK_Q);

        search("kp one");

        assertVisited(1, KeyEvent.VK_A);

        try (Answering answering = Answering.start(message -> Answering.pressAt(1)))
        {
            Keys.press(keyboard(), KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);

            assertTrue(answering.awaitAsked(1, WAIT), "Control+F asked nothing");
        }

        Components.settle();

        assertNothingMoved(1, KeyEvent.VK_A);

        search("");

        assertVisited(2, KeyEvent.VK_B);
    }

    // Map Unassigned Locomotives

    /**
     * Map Unassigned Locomotives fills the keys that are free, in the order they lie on the keyboard, with the
     * locomotives mapped nowhere, in alphabetical order; a key already holding one is left alone and a locomotive
     * already on a page is not put on a second key.  Choosing it again changes nothing.
     *
     * MUTATION: delete `&& this.getAllLocButtonMappings(l).isEmpty()` in `mapUnassignedLocomotives`, and the
     * locomotive already mapped is mapped again.
     */
    @Test
    public void testMapUnassignedFillsTheFreeKeysInOrder() throws Exception
    {
        List<String> names = model.getLocList();

        assertTrue(names.size() > 30, "precondition: the database has too few locomotives to fill a page");

        Locomotive elsewhere = model.getLocByName(names.get(0));

        put(4, KeyEvent.VK_B, elsewhere);
        put(2, KeyEvent.VK_T, one);

        turnTo(2);

        chooseMenu(I18n.t("page.ui.menuMapUnassignedLocomotives"));

        List<String> unmapped = new ArrayList<>(names);

        unmapped.remove(names.get(0));
        unmapped.remove(ONE);

        List<JButton> free = keysInKeyboardOrder();

        free.remove(key(KeyEvent.VK_T));

        assertTrue(Components.waitFor(() -> ui.getButtonLocomotive(free.get(free.size() - 1)) != null, WAIT),
            "the last free key was never filled");

        assertSame(ui.getButtonLocomotive(key(KeyEvent.VK_T)), one, "the key that held a locomotive was filled over");

        List<String> got = new ArrayList<>();
        List<String> want = new ArrayList<>();

        for (int at = 0; at < free.size(); at++)
        {
            Locomotive on = ui.getButtonLocomotive(free.get(at));

            got.add(on == null ? null : on.getName());
            want.add(unmapped.get(at));
        }

        assertEquals(got, want, "the free keys, along the keyboard, do not hold the unmapped locomotives A to Z");

        chooseMenu(I18n.t("page.ui.menuMapUnassignedLocomotives"));

        Components.settle();

        List<String> again = new ArrayList<>();

        for (JButton free1 : free)
        {
            Locomotive on = ui.getButtonLocomotive(free1);

            again.add(on == null ? null : on.getName());
        }

        assertEquals(again, got, "choosing Map Unassigned a second time changed the page");
    }

    /**
     * A key holding a locomotive that has since been deleted counts as free.
     *
     * MUTATION: delete `|| this.model.getLocByName(this.currentLocMapping().get(key).getName()) == null` in
     * `mapUnassignedLocomotives`, and the key keeps the deleted locomotive.
     */
    @Test
    public void testADeletedLocomotiveLeavesItsKeyFree() throws Exception
    {
        MarklinLocomotive ghost = model.newMFXLocomotive(GHOST, 3413);

        put(2, KeyEvent.VK_Q, ghost);

        model.deleteLoc(GHOST);

        turnTo(2);

        Locomotive stale = ui.getButtonLocomotive(key(KeyEvent.VK_Q));

        assertTrue(stale != null && model.getLocByName(GHOST) == null,
            "precondition: the key did not keep the deleted locomotive");

        chooseMenu(I18n.t("page.ui.menuMapUnassignedLocomotives"));

        String first = model.getLocList().get(0);

        assertTrue(Components.waitFor(() ->
        {
            Locomotive on = ui.getButtonLocomotive(key(KeyEvent.VK_Q));

            return on != null && first.equals(on.getName());
        }, WAIT), "the key with the deleted locomotive was not filled with the first unmapped one");
    }

    // Dragging

    /**
     * A drag picks the locomotive up from its key, rests over Next until the page turns, and lands on a key of the
     * next page: the key it came from is empty, the key it fell on holds the locomotive, and nothing is left picked up.
     *
     * MUTATION: delete `ui.doPaste((JButton) support.getComponent(), false, false);` in `importData`, and the drop is
     * accepted and the key stays empty.
     */
    @Test
    public void testADraggedLocomotiveLandsOnTheKeyOfAnotherPage() throws Exception
    {
        put(1, KeyEvent.VK_A, one);

        turnTo(1);

        Transferable held = pickUp(key(KeyEvent.VK_A));

        assertNull(ui.getButtonLocomotive(key(KeyEvent.VK_A)), "the key still holds the locomotive being dragged");

        rest(field("NextLocMapping"), held, 2);

        Drop drop = dropOn(key(KeyEvent.VK_S), held);

        assertTrue(drop.accepted && drop.completed, "the drop on the next page's S was not taken");
        assertSame(ui.getButtonLocomotive(key(KeyEvent.VK_S)), one, "the locomotive did not land on S");
        assertFalse(ui.hasCopyTarget(), "the locomotive is still held after it landed");

        endDrag(key(KeyEvent.VK_A), held, DnDConstants.ACTION_MOVE);

        assertSame(ui.getButtonLocomotive(key(KeyEvent.VK_S)), one, "ending the drag moved the locomotive");
        assertNull(onKey(1, KeyEvent.VK_A), "the locomotive is back on the key it came from");
    }

    /**
     * Resting over Next turns to the next page and over Prev to the one before.
     *
     * MUTATION: `+ 1 - offset` for `+ 1 + offset` in `enablePageSwitching`, and the controls turn the wrong way.
     */
    @Test
    public void testRestingOverNextAndPrevTurnsThePage() throws Exception
    {
        put(3, KeyEvent.VK_A, one);

        turnTo(3);

        Transferable held = pickUp(key(KeyEvent.VK_A));

        rest(field("NextLocMapping"), held, 4);
        rest(field("PrevLocMapping"), held, 3);
        rest(field("PrevLocMapping"), held, 2);

        endDrag(key(KeyEvent.VK_A), held, DnDConstants.ACTION_NONE);
    }

    /**
     * A drag that leaves the control before the page turns does not turn it.
     *
     * MUTATION: empty `PageSwitcher.cancel` (its `timer.stop();` and `pending = 0;`), and the page turns after the pointer has
     * gone.
     */
    @Test
    public void testLeavingTheControlBeforeThePageTurnsLeavesThePage() throws Exception
    {
        put(3, KeyEvent.VK_A, one);

        turnTo(3);

        Transferable held = pickUp(key(KeyEvent.VK_A));

        JComponent next = (JComponent) field("NextLocMapping");

        Peer peer = new Peer(held);

        DropTarget target = next.getDropTarget();

        Components.onTheEventThread(() ->
        {
            target.dragOver(new DropTargetDragEvent(contextOf(target, peer), new Point(2, 2),
                DnDConstants.ACTION_MOVE, DnDConstants.ACTION_MOVE));

            target.dragExit(new java.awt.dnd.DropTargetEvent(contextOf(target, peer)));

            return null;
        });

        Thread.sleep(1500);

        Components.settle();

        assertEquals(ui.getLocMappingNumber(), 3, "the page turned after the pointer had left the control");

        endDrag(key(KeyEvent.VK_A), held, DnDConstants.ACTION_NONE);
    }

    /**
     * A page control is no destination: over it the drag is refused, and a drop on it is refused with the locomotive
     * still held.
     *
     * MUTATION: `e.acceptDrop(DnDConstants.ACTION_MOVE)` for `e.rejectDrop()` in `PageSwitcher.drop`, and this fails.
     */
    @Test
    public void testAPageControlIsNotADestination() throws Exception
    {
        put(3, KeyEvent.VK_A, one);

        turnTo(3);

        Transferable held = pickUp(key(KeyEvent.VK_A));

        JComponent next = (JComponent) field("NextLocMapping");

        Drop drop = dropOn(next, held);

        assertTrue(drop.rejected && !drop.accepted, "a drop on Next was not refused");
        assertTrue(ui.hasCopyTarget(), "a drop on Next used up the locomotive");

        endDrag(key(KeyEvent.VK_A), held, DnDConstants.ACTION_NONE);
    }

    /**
     * A drag dropped nowhere puts the locomotive back on the key it came from, even after the page was turned in the
     * meantime, and the window returns to that page.
     *
     * MUTATION: delete `tabs.setSelectedIndex(origin.page - 1);` in `exportDone`, and the locomotive is put on the page
     * the drag ended on.
     */
    @Test
    public void testACanceledDragPutsTheLocomotiveBack() throws Exception
    {
        put(1, KeyEvent.VK_A, one);

        turnTo(1);

        Transferable held = pickUp(key(KeyEvent.VK_A));

        assertNull(ui.getButtonLocomotive(key(KeyEvent.VK_A)), "precondition: the key still holds what is picked up");

        rest(field("NextLocMapping"), held, 2);
        rest(field("NextLocMapping"), held, 3);

        endDrag(key(KeyEvent.VK_A), held, DnDConstants.ACTION_NONE);

        assertEquals(ui.getLocMappingNumber(), 1, "the window did not go back to the page the drag began on");
        assertSame(onKey(1, KeyEvent.VK_A), one, "the locomotive is not back on its key");
        assertNull(onKey(3, KeyEvent.VK_A), "the locomotive was put on the page the drag ended on");
        assertFalse(ui.hasCopyTarget(), "the locomotive is still held");
    }

    /**
     * Dropping on the key it was picked up from is refused; the same letter on another page is not.
     *
     * MUTATION: delete `&& origin.page == ui.getLocMappingNumber()` in `canImport`, and the letter on another page is
     * refused.
     */
    @Test
    public void testTheKeyItCameFromIsNotADestinationButItsLetterElsewhereIs() throws Exception
    {
        put(1, KeyEvent.VK_A, one);

        turnTo(1);

        Transferable held = pickUp(key(KeyEvent.VK_A));

        Drop onItself = dropOn(key(KeyEvent.VK_A), held);

        assertTrue(onItself.rejected && !onItself.accepted, "the key it came from took the drop");
        assertTrue(ui.hasCopyTarget(), "a refused drop used up the locomotive");

        rest(field("NextLocMapping"), held, 2);

        Drop elsewhere = dropOn(key(KeyEvent.VK_A), held);

        assertTrue(elsewhere.accepted, "the same letter on another page was refused");
        assertSame(onKey(2, KeyEvent.VK_A), one, "the locomotive did not land on A of page 2");
        assertNull(onKey(1, KeyEvent.VK_A), "A of page 1 still holds the locomotive");
    }

    /**
     * A wiggle of a few pixels is a click, not a drag; the right button never drags; and an empty key has nothing to
     * drag.  None of them picks anything up.
     *
     * MUTATION: `DRAG_THRESHOLD = 0` in `LocButtonTransferHandler`, and the wiggle picks the locomotive up.
     */
    @Test
    public void testOnlyARealDragFromAFullKeyPicksAnythingUp() throws Exception
    {
        put(1, KeyEvent.VK_A, one);

        turnTo(1);

        JButton full = key(KeyEvent.VK_A);
        JButton empty = key(KeyEvent.VK_S);

        gesture(full, MouseEvent.BUTTON1, 3);

        assertFalse(ui.hasCopyTarget(), "a three pixel wiggle picked the locomotive up");
        assertSame(ui.getButtonLocomotive(full), one, "a wiggle took the locomotive off its key");

        gesture(full, MouseEvent.BUTTON3, 40);

        assertFalse(ui.hasCopyTarget(), "a drag with the right button picked the locomotive up");
        assertSame(ui.getButtonLocomotive(full), one, "a right drag took the locomotive off its key");

        gesture(empty, MouseEvent.BUTTON1, 40);

        assertFalse(ui.hasCopyTarget(), "dragging an empty key picked something up");
    }

    // Pages and keys

    @SuppressWarnings("unchecked")
    private static List<Map<JButton, Locomotive>> pages()
    {
        return (List<Map<JButton, Locomotive>>) field0("locMapping");
    }

    /** Page `page`'s key, as the user sees it: what the window would show on that page. */
    private static Locomotive onKey(int page, int key)
    {
        return Components.onTheEventThread(() -> pages().get(page - 1).get(key(key)));
    }

    /** Sets what a key of a page holds, as the selector does: by the page's own mapping. */
    private static void put(int page, int key, Locomotive loco)
    {
        Components.onTheEventThread(() ->
        {
            pages().get(page - 1).put(key(key), loco);

            return null;
        });
    }

    private static JButton key(int code)
    {
        @SuppressWarnings("unchecked")
        Map<Integer, JButton> letters = (Map<Integer, JButton>) field0("buttonMapping");

        return letters.get(code);
    }

    /** The keys as they lie on the keyboard: row by row, left to right. */
    private static List<JButton> keysInKeyboardOrder()
    {
        return Components.onTheEventThread(() ->
        {
            List<JButton> keys = new ArrayList<>(((Map<JButton, ?>) field0("labelMapping")).keySet());

            Component top = SwingUtilities.getRoot(keys.get(0));

            Comparator<JButton> byRow = Comparator.comparingInt(b -> SwingUtilities.convertPoint(b, 0, 0, top).y);

            Collections.sort(keys, byRow.thenComparingInt(b -> SwingUtilities.convertPoint(b, 0, 0, top).x));

            return keys;
        });
    }

    private static void pagesTo(int count) throws Exception
    {
        while (ui.getNumLocMappings() > count)
        {
            turnTo(ui.getNumLocMappings());

            Components.onTheEventThread(() ->
            {
                ui.deleteCurrentLocMappingPage();

                return null;
            });
        }

        while (ui.getNumLocMappings() < count)
        {
            Components.onTheEventThread(() ->
            {
                ui.addLocMappingPage();

                return null;
            });
        }

        Components.settle();
    }

    private static void turnTo(int page) throws Exception
    {
        Components.onTheEventThread(() ->
        {
            tabs().setSelectedIndex(page - 1);

            return null;
        });

        assertTrue(Components.waitFor(() -> ui.getLocMappingNumber() == page, WAIT), "page " + page + " did not show");

        Components.settle();
    }

    private static void choose(int code)
    {
        Keys.press(keyboardOrNull(), code);

        assertTrue(Components.waitFor(() -> field0("currentButton") == key(code), WAIT), "the key was not chosen");
    }

    private static void nameThePage(int page, String name)
    {
        Components.onTheEventThread(() ->
        {
            @SuppressWarnings("unchecked")
            Map<Integer, String> names = (Map<Integer, String>) field0("pageNames");

            names.put(page, name);

            call("retitlePages");

            return null;
        });
    }

    /** What the tab of a page says. */
    private static String titleOfPage(int page)
    {
        return Components.onTheEventThread(() -> tabs().getTitleAt(page - 1));
    }

    private static void assertVisited(int page, int code)
    {
        assertTrue(Components.waitFor(() -> ui.getLocMappingNumber() == page && field0("currentButton") == key(code), WAIT),
            "the search did not end on " + (char) code + " of page " + page + ", but on "
            + ((JButton) field0("currentButton")).getText() + " of page " + ui.getLocMappingNumber());
    }

    private static void assertNothingMoved(int page, int code)
    {
        Components.settle();

        assertEquals(ui.getLocMappingNumber(), page, "the page changed");
        assertSame(field0("currentButton"), key(code), "the chosen key changed");
    }

    /** Asks Quick Find with Control+F and types the name into its question. */
    private static void search(String name) throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.type(name)))
        {
            Keys.press(keyboard(), KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);

            assertTrue(answering.awaitAsked(1, WAIT), "Control+F asked nothing");
        }

        Components.settle();
    }

    // The page menu

    /**
     * The page menu as a right-click opens it, on the page label or, where tabs are shown instead, the page's tab.  A
     * menu closes when another program's window takes the focus, so a right-click that opened none is made again.
     */
    private static JPopupMenu pageMenu() throws Exception
    {
        Component label = (Component) field("LocMappingNumberLabel");

        JTabbedPane tabs = tabs();

        for (int attempt = 0; attempt < 3; attempt++)
        {
            Components.select(label);

            JPopupMenu menu;

            if (Components.onTheEventThread(label::isShowing))
            {
                menu = Mouse.rightClick(label, 3000);
            }
            else
            {
                Rectangle tab = Components.onTheEventThread(() -> tabs.getBoundsAt(tabs.getSelectedIndex()));

                menu = Mouse.rightClickAt(tabs, new Point(tab.x + tab.width / 2, tab.y + tab.height / 2), 3000);
            }

            if (menu != null) return menu;

            Mouse.closeMenus();
        }

        return null;
    }

    private static AbstractButton pageMenuItem(String text) throws Exception
    {
        JPopupMenu menu = pageMenu();

        assertNotNull(menu, "the right-click opened no page menu");

        AbstractButton item = Components.withText(menu, text);

        assertNotNull(item, "the page menu has no \"" + text + "\"");

        return item;
    }

    private static void chooseMenu(String text) throws Exception
    {
        Mouse.choose((JMenuItem) pageMenuItem(text));
    }

    private static void deleteThroughTheMenu(int page) throws Exception
    {
        AbstractButton item = pageMenuItem(I18n.f("page.ui.menuDeletePage", page));

        assertTrue(Components.onTheEventThread(item::isEnabled), "Delete Page " + page + " is grayed over an empty page");

        Mouse.choose((JMenuItem) item);

        Components.settle();
    }

    /** Asks the window to delete the page showing, answers its refusal, and gives what it said. */
    private static String askedByDeleting() throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.ok"))))
        {
            Thread asking = new Thread(() -> ui.deleteCurrentLocMappingPage());

            asking.setDaemon(true);
            asking.start();

            assertTrue(answering.awaitAsked(1, WAIT), "the window said nothing about the page");

            asking.join(WAIT);

            return answering.asked().get(0);
        }
    }

    // Drag and drop, as Swing does it

    /** What a drop target was told. */
    private static final class Drop
    {
        boolean accepted;
        boolean rejected;
        boolean completed;
    }

    /** The drag-and-drop system's side of a drag in this JVM: the transferable, and a record of the answers. */
    private static final class Peer implements InvocationHandler
    {
        final Transferable held;
        final Drop drop = new Drop();

        DropTarget target;

        Peer(Transferable held)
        {
            this.held = held;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
        {
            switch (method.getName())
            {
                case "getTransferable":
                    return held;
                case "getTransferDataFlavors":
                    return held.getTransferDataFlavors();
                case "isTransferableJVMLocal":
                    return true;
                case "getDropTarget":
                    return target;
                case "acceptDrop":
                    drop.accepted = true;
                    return null;
                case "rejectDrop":
                    drop.rejected = true;
                    return null;
                case "dropComplete":
                    drop.completed = (Boolean) args[0];
                    return null;
                case "getTargetActions":
                    return DnDConstants.ACTION_MOVE;
                default:
                    return null;
            }
        }
    }

    private static DropTargetContext contextOf(DropTarget target, Peer peer)
    {
        try
        {
            DropTargetContext context = target.getDropTargetContext();

            peer.target = target;

            Class<?> contextPeer = Class.forName("java.awt.dnd.peer.DropTargetContextPeer");

            Method notify = DropTargetContext.class.getDeclaredMethod("addNotify", contextPeer);

            notify.setAccessible(true);
            notify.invoke(context, Proxy.newProxyInstance(contextPeer.getClassLoader(), new Class<?>[] {contextPeer},
                peer));

            return context;
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("the drag-and-drop context cannot be given a stand-in for the system: " + e, e);
        }
    }

    /** What Swing does when a drag begins on a key: the handler makes what is carried, and the key is cut. */
    private static Transferable pickUp(JButton from) throws Exception
    {
        return Components.onTheEventThread(() ->
        {
            javax.swing.TransferHandler handler = from.getTransferHandler();

            Method make = javax.swing.TransferHandler.class.getDeclaredMethod("createTransferable", JComponent.class);

            make.setAccessible(true);

            return (Transferable) make.invoke(handler, from);
        });
    }

    /** What Swing does when a drag ends: the handler is told what became of it. */
    private static void endDrag(JButton from, Transferable held, int action) throws Exception
    {
        Components.onTheEventThread(() ->
        {
            Method done = javax.swing.TransferHandler.class.getDeclaredMethod("exportDone", JComponent.class,
                Transferable.class, int.class);

            done.setAccessible(true);
            done.invoke(from.getTransferHandler(), from, held, action);

            return null;
        });

        Components.settle();
    }

    /** The pointer lets go over a component: its drop target is given the drop. */
    private static Drop dropOn(JComponent where, Transferable held) throws Exception
    {
        Peer peer = new Peer(held);

        DropTarget target = Components.onTheEventThread(where::getDropTarget);

        assertNotNull(target, "the component takes no drops");

        Components.onTheEventThread(() ->
        {
            DropTargetContext context = contextOf(target, peer);

            target.dragEnter(new DropTargetDragEvent(context, new Point(3, 3), DnDConstants.ACTION_MOVE,
                DnDConstants.ACTION_MOVE));
            target.drop(new DropTargetDropEvent(context, new Point(3, 3), DnDConstants.ACTION_MOVE,
                DnDConstants.ACTION_MOVE));

            return null;
        });

        Components.settle();

        return peer.drop;
    }

    /** The pointer rests over a page control until the window is on this page. */
    private static void rest(Object control, Transferable held, int page) throws Exception
    {
        JComponent over = (JComponent) control;

        Peer peer = new Peer(held);

        DropTarget target = Components.onTheEventThread(over::getDropTarget);

        Components.onTheEventThread(() ->
        {
            target.dragOver(new DropTargetDragEvent(contextOf(target, peer), new Point(2, 2), DnDConstants.ACTION_MOVE,
                DnDConstants.ACTION_MOVE));

            return null;
        });

        assertTrue(Components.waitFor(() -> ui.getLocMappingNumber() == page, 4000),
            "resting over the control did not turn to page " + page + "; the window is on " + ui.getLocMappingNumber());

        Components.onTheEventThread(() ->
        {
            target.dragExit(new java.awt.dnd.DropTargetEvent(contextOf(target, peer)));

            return null;
        });
    }

    /** Press, move this far with this button, and let go, on a component, as the key's own listeners see them. */
    private static void gesture(JButton on, int button, int distance)
    {
        int down = button == MouseEvent.BUTTON1 ? InputEvent.BUTTON1_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK;

        Components.onTheEventThread(() ->
        {
            long now = System.currentTimeMillis();

            on.dispatchEvent(new MouseEvent(on, MouseEvent.MOUSE_PRESSED, now, down, 4, 4, 1, false, button));
            on.dispatchEvent(new MouseEvent(on, MouseEvent.MOUSE_DRAGGED, now + 5, down, 4 + distance, 4, 0, false,
                MouseEvent.NOBUTTON));
            on.dispatchEvent(new MouseEvent(on, MouseEvent.MOUSE_RELEASED, now + 10, 0, 4 + distance, 4, 1, false, button));

            return null;
        });

        Components.settle();
    }

    // The window

    private static Component keyboardOrNull()
    {
        return (Component) field0("KeyboardTab");
    }

    private static Component keyboard() throws Exception
    {
        return (Component) field("KeyboardTab");
    }

    private static javax.swing.JMenuBar menuBar()
    {
        return Components.onTheEventThread(ui::getJMenuBar);
    }

    private static JTabbedPane tabs()
    {
        return (JTabbedPane) field0("locKeyTabs");
    }

    private static void call(String method)
    {
        try
        {
            Method m = TrainControlUI.class.getDeclaredMethod(method);

            m.setAccessible(true);
            m.invoke(ui);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("the window has no " + method + ": " + e, e);
        }
    }

    private static Object field(String name) throws Exception
    {
        Field f = TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(ui);
    }

    private static void setField(String name, Object value) throws Exception
    {
        Field f = TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);
        f.set(ui, value);
    }

    private static Object field0(String name)
    {
        try
        {
            return field(name);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static void closeStrayDialogs()
    {
        Components.onTheEventThread(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof JDialog && w.isShowing()) w.dispose();
            }

            javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();

            return null;
        });
    }
}
