package ui;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
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
import support.WireRecorder;
import support.WireRecorder.Sent;

/**
 * The locomotive mapping pages and the doors a locomotive's key opens, used as the operator uses them (FR-136 Phase 6).
 *
 * The pages: the Prev and Next buttons and their keys, and the page menu's Copy, Paste and Reset Mappings with their
 * questions.  The doors: the notes, Find Similar Locomotives, the name and address, Delete from Database, and the
 * function and speed presets, reached by their shortcuts or the key's right-click menu.  Each claim asserts what the
 * pages hold, what the locomotive database now says, or what reached the railway.
 *
 * MUTATION: each claim names the change to the program that turns it red.
 *
 * @author Adam
 */
public class testMappingPagesAndLocomotiveDoors
{
    private static final String ONE = "P6 pages one";
    private static final String TWO = "P6 pages two";
    private static final String THREE = "P6 pages three";
    private static final String PLAIN = "P6 pages plain";
    private static final String DOOMED = "P6 pages doomed";
    private static final String RENAMED = "P6 pages renamed";

    private static final String[] NAMES = {ONE, TWO, THREE, PLAIN, DOOMED, RENAMED, RENAMED + " was"};

    /** No real railway calls itself this, so Find Similar can find only these. */
    private static final String RAILWAY = "P6 Test Railway";

    private static final long WAIT = 5000;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static WireRecorder wire;

    private static MarklinLocomotive one;
    private static MarklinLocomotive two;
    private static MarklinLocomotive three;
    private static MarklinLocomotive plain;

    /** Two pages of our own at the end, so nothing of the operator's is in the way. */
    private static int pageP;
    private static int pageQ;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the pages are on a window");

        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedWithWindowInDebug();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        Components.settle();

        wire = WireRecorder.open();

        for (String name : NAMES)
        {
            if (model.getLocByName(name) != null) model.deleteLoc(name);
        }

        one = model.newMFXLocomotive(ONE, 3201);
        two = model.newMFXLocomotive(TWO, 3202);
        three = model.newMFXLocomotive(THREE, 3203);
        plain = model.newMFXLocomotive(PLAIN, 3204);

        // Two new, empty pages at the end
        for (int add = 0; add < 2; add++)
        {
            Components.onTheEventThread(() ->
            {
                ui.addLocMappingPage();

                return null;
            });
        }

        pageQ = ui.getNumLocMappings();
        pageP = pageQ - 1;

        assertTrue(pageP >= 2, "precondition: there are not three pages");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (wire != null) wire.close();

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

    /**
     * Page P holds one on A and two on S; page Q holds three on A and nothing on S; page P is showing, with A chosen.
     *
     * @throws Exception from the window
     */
    @BeforeMethod
    public void lay() throws Exception
    {
        closeStrayDialogs();

        one.setStructuredNotes(1950, 1970, RAILWAY, "");
        two.setStructuredNotes(1960, 1980, RAILWAY, "");
        three.setStructuredNotes(1990, 2000, RAILWAY, "");
        plain.setStructuredNotes(0, 0, "", "");

        for (MarklinLocomotive loc : new MarklinLocomotive[] {one, two, three, plain})
        {
            loc.setSpeed(0);
            loc.functionsOff();
        }

        turnTo(pageQ);
        setKey(KeyEvent.VK_A, three);
        setKey(KeyEvent.VK_S, null);

        turnTo(pageP);
        setKey(KeyEvent.VK_S, two);
        setKey(KeyEvent.VK_A, one);

        assertTrue(waitFor(() -> ui.getActiveLoc() == one), "precondition: A does not show the first train");
    }

    // Turning the page

    /**
     * Next shows the next page, whose A drives that page's train; Prev turns back, and A drives the first page's again.
     *
     * MUTATION: `locMappingNumber - 1` in `NextLocMappingActionPerformed`, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testNextAndPrevTurnThePage() throws Exception
    {
        JButton next = (JButton) field("NextLocMapping");
        JButton prev = (JButton) field("PrevLocMapping");

        assertTrue(next.isEnabled(), "precondition: Next is greyed on a page that has one after it");

        Components.onTheEventThread(() ->
        {
            next.doClick();

            return null;
        });

        assertTrue(waitFor(() -> ui.getLocMappingNumber() == pageQ), "Next did not turn to the next page");
        assertEquals(tabs().getSelectedIndex(), pageQ - 1, "Next turned the page and the page tabs did not follow");

        Keys.press(keyboard(), KeyEvent.VK_A);

        assertTrue(waitFor(() -> ui.getActiveLoc() == three), "after Next, A did not drive the next page's train");

        assertFalse(next.isEnabled(), "Next is still lit on the last page");

        Components.onTheEventThread(() ->
        {
            prev.doClick();

            return null;
        });

        assertTrue(waitFor(() -> ui.getLocMappingNumber() == pageP), "Prev did not turn back");

        Keys.press(keyboard(), KeyEvent.VK_A);

        assertTrue(waitFor(() -> ui.getActiveLoc() == one), "after Prev, A did not drive the first page's train again");
    }

    /**
     * Full stop and comma turn a page forward and back; with Alt, they go to the last page and the first.
     *
     * MUTATION: `switchLocMapping(1)` in the Alt+PERIOD arm of `LocControlPanelKeyPressed`, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testThePageKeysTurnThePage() throws Exception
    {
        Keys.press(keyboard(), KeyEvent.VK_PERIOD);

        assertTrue(waitFor(() -> ui.getLocMappingNumber() == pageQ), "full stop did not turn to the next page");

        Keys.press(keyboard(), KeyEvent.VK_COMMA);

        assertTrue(waitFor(() -> ui.getLocMappingNumber() == pageP), "comma did not turn back a page");

        Keys.press(keyboard(), KeyEvent.VK_COMMA, InputEvent.ALT_DOWN_MASK);

        assertTrue(waitFor(() -> ui.getLocMappingNumber() == 1), "Alt+comma did not go to the first page");

        Keys.press(keyboard(), KeyEvent.VK_PERIOD, InputEvent.ALT_DOWN_MASK);

        assertTrue(waitFor(() -> ui.getLocMappingNumber() == ui.getNumLocMappings()),
            "Alt+full stop did not go to the last page");

        Keys.press(keyboard(), KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK);

        assertTrue(waitFor(() -> ui.getLocMappingNumber() == pageP), "Alt+Left did not turn back a page");

        Keys.press(keyboard(), KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK);

        assertTrue(waitFor(() -> ui.getLocMappingNumber() == pageQ), "Alt+Right did not turn to the next page");
    }

    // Copy, paste and reset

    /**
     * Copy Mappings on one page, then Paste Mappings on another, answered Yes: the second page holds what the first does.
     *
     * MUTATION: copy the page onto itself in `pasteCopiedPage` (`locMappingNumber - 1` for `pageToCopy - 1`), and this
     * fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testPasteMappingsAnsweredYesCopiesThePage() throws Exception
    {
        AbstractButton copy = pageMenuItem(I18n.t("page.ui.menuCopyMappings"));

        Components.onTheEventThread(() ->
        {
            copy.doClick();

            return null;
        });

        turnTo(pageQ);

        AbstractButton paste = pageMenuItem(I18n.t("page.ui.menuPasteMappings"));

        List<String> asked = answering(I18n.t("ui.yes"), () -> SwingUtilities.invokeLater(paste::doClick));

        assertEquals(asked.size(), 1, "Paste Mappings asked nothing: " + asked);

        assertTrue(waitFor(() -> onKey(KeyEvent.VK_A) == one && onKey(KeyEvent.VK_S) == two),
            "Paste Mappings, answered Yes, left the page holding " + onKey(KeyEvent.VK_A) + " and " + onKey(KeyEvent.VK_S));

        turnTo(pageP);

        assertSame(onKey(KeyEvent.VK_A), one, "the page copied from lost its A");
    }

    /**
     * Paste Mappings answered No changes nothing.
     *
     * MUTATION: paste whatever the answer in `pasteCopiedPage`, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testPasteMappingsAnsweredNoChangesNothing() throws Exception
    {
        AbstractButton copy = pageMenuItem(I18n.t("page.ui.menuCopyMappings"));

        Components.onTheEventThread(() ->
        {
            copy.doClick();

            return null;
        });

        turnTo(pageQ);

        AbstractButton paste = pageMenuItem(I18n.t("page.ui.menuPasteMappings"));

        List<String> asked = answering(I18n.t("ui.no"), () -> SwingUtilities.invokeLater(paste::doClick));

        assertEquals(asked.size(), 1, "Paste Mappings asked nothing: " + asked);

        Components.settle();

        assertSame(onKey(KeyEvent.VK_A), three, "Paste Mappings, answered No, replaced the page's A");
        assertNull(onKey(KeyEvent.VK_S), "Paste Mappings, answered No, filled the page's S");
    }

    /**
     * Reset Mappings answered Yes empties the page showing and no other; answered No it empties nothing.
     *
     * MUTATION: clear whatever the answer in `clearCurrentPage`, and the No half fails; take `doClearCurrentPage()` out,
     * and the Yes half fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testResetMappingsEmptiesOnlyThisPageAndOnlyOnYes() throws Exception
    {
        AbstractButton reset = pageMenuItem(I18n.t("page.ui.menuResetCurrentMappings"));

        List<String> no = answering(I18n.t("ui.no"), () -> SwingUtilities.invokeLater(reset::doClick));

        assertEquals(no.size(), 1, "Reset Mappings asked nothing: " + no);

        Components.settle();

        assertSame(onKey(KeyEvent.VK_A), one, "Reset Mappings, answered No, emptied the page");

        AbstractButton resetAgain = pageMenuItem(I18n.t("page.ui.menuResetCurrentMappings"));

        List<String> yes = answering(I18n.t("ui.yes"), () -> SwingUtilities.invokeLater(resetAgain::doClick));

        assertEquals(yes.size(), 1, "Reset Mappings asked nothing: " + yes);

        assertTrue(waitFor(() -> onKey(KeyEvent.VK_A) == null && onKey(KeyEvent.VK_S) == null),
            "Reset Mappings, answered Yes, left the page holding something");

        turnTo(pageQ);

        assertSame(onKey(KeyEvent.VK_A), three, "Reset Mappings emptied another page as well");
    }

    // The locomotive's doors

    /**
     * Control+N edits the shown train's notes: the year typed is the year kept.  Cancel keeps the old one.
     *
     * MUTATION: save the end year's text as the start year in `changeLocNotes`, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testControlNEditsTheNotes() throws Exception
    {
        try (Answering answering = Answering.start(asked -> Answering.type("1957")))
        {
            Keys.press(keyboard(), KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK);

            assertTrue(answering.awaitAsked(1, WAIT), "Control+N opened no notes");
            assertTrue(answering.asked().get(0).contains(I18n.t("loc.ui.labelStartYear")),
                "Control+N opened something other than the notes: " + answering.asked());
        }

        assertTrue(waitFor(() -> one.getStructuredNotes().getStartYear() == 1957),
            "the notes kept " + one.getStructuredNotes().getStartYear() + ", not the year typed");
        assertEquals(one.getStructuredNotes().getRailway(), RAILWAY, "saving the notes lost the railway");

        try (Answering answering = Answering.start(asked -> Answering.press(I18n.t("ui.cancel"))))
        {
            Keys.press(keyboard(), KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK);

            assertTrue(answering.awaitAsked(1, WAIT), "Control+N opened no notes the second time");
        }

        Components.settle();

        assertEquals(one.getStructuredNotes().getStartYear(), 1957, "Cancel changed the notes");
    }

    /**
     * Find Similar Locomotives, from the key's right-click menu, finds the trains of the same railway and years, and
     * "Map to Page" puts them, and the train searched from, on the page chosen.
     *
     * MUTATION: drop `matches.add(searchLoc)` in `findSimilarLocs`, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testFindSimilarMapsTheMatchesToAPage() throws Exception
    {
        int expectedPage = ui.getLocMappingNumber() == 1 ? 2 : 1;

        String similar = I18n.t("search.ui.labelSimilarLocomotives");
        String mapTo = I18n.t("page.ui.optionMapToPage");

        List<String> asked;

        try (Answering answering = Answering.start(message ->
            message.contains(similar) ? Answering.press(mapTo) : Answering.pressAt(0)))
        {
            AbstractButton item = rightClickMenuItem(KeyEvent.VK_A, I18n.t("loc.ui.menuFindSimilarLocomotives"));

            SwingUtilities.invokeLater(item::doClick);

            assertTrue(answering.awaitAsked(3, WAIT), "Find Similar did not ask its three questions: " + answering.asked());

            asked = answering.asked();
        }

        assertTrue(asked.get(1).contains(TWO), "the results do not name the train of the same railway and years: "
            + asked.get(1));
        assertFalse(asked.get(1).contains(THREE), "the results name a train of other years: " + asked.get(1));

        assertTrue(waitFor(() -> ui.getLocMappingNumber() == expectedPage), "Map to Page did not show the page chosen");

        assertTrue(waitFor(() -> mapped().equals(new HashSet<>(Arrays.asList(one, two)))),
            "the page chosen holds " + mapped() + ", not the train searched from and its match");
    }

    /**
     * Find Similar on a train with no railway and no years says so, and goes no further.
     *
     * MUTATION: take the insufficient-data return out of `findSimilarLocs`, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testFindSimilarWithNoNotesSaysWhy() throws Exception
    {
        setKey(KeyEvent.VK_D, plain);

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            AbstractButton item = rightClickMenuItem(KeyEvent.VK_D, I18n.t("loc.ui.menuFindSimilarLocomotives"));

            SwingUtilities.invokeLater(item::doClick);

            assertTrue(answering.awaitAsked(1, WAIT), "Find Similar said nothing");

            Thread.sleep(300);

            assertEquals(answering.asked(), Arrays.asList(I18n.t("search.ui.errorNoRailwayOrStartYear")),
                "Find Similar on a train with no notes did not say why it cannot search, or went on to ask more");
        }
    }

    /**
     * Control+R changes the shown train's name and address: the database answers to the new name, the train has the
     * new address, and the key still holds it.
     *
     * MUTATION: drop `this.ui.getModel().renameLoc(...)` from `LocomotiveActions.changeLocAddress`, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testControlRChangesTheNameAndAddress() throws Exception
    {
        MarklinLocomotive renamed = model.newMFXLocomotive(RENAMED + " was", 3205);

        try
        {
            setKey(KeyEvent.VK_F, renamed);

            final int newAddress = 3206;

            Thread answering = answerTheAddressDialog(renamed.getName(), RENAMED, String.valueOf(renamed.getAddress()),
                String.valueOf(newAddress));

            Keys.press(keyboard(), KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK);

            answering.join(WAIT * 2);

            assertFalse(answering.isAlive(), "Control+R opened no name and address dialog");

            assertTrue(waitFor(() -> model.getLocByName(RENAMED) != null), "Control+R, answered with a new name, did not "
                + "rename the train");
            assertNull(model.getLocByName(RENAMED + " was"), "the old name still answers");
            assertEquals(model.getLocByName(RENAMED).getAddress(), newAddress, "the new address was not kept");
            assertSame(onKey(KeyEvent.VK_F), model.getLocByName(RENAMED), "the key lost the renamed train");
        }
        finally
        {
            setKey(KeyEvent.VK_F, null);
        }
    }

    /**
     * Control+Delete asks before it deletes: No keeps the train, Yes takes it out of the database and off its key.
     *
     * MUTATION: delete on No in `deleteLoc` (`NO_OPTION` for `YES_OPTION`), and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testControlDeleteAsksAndYesDeletes() throws Exception
    {
        MarklinLocomotive doomed = model.newMFXLocomotive(DOOMED, 3207);

        setKey(KeyEvent.VK_G, doomed);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.no"))))
        {
            Keys.press(keyboard(), KeyEvent.VK_DELETE, InputEvent.CTRL_DOWN_MASK);

            assertTrue(answering.awaitAsked(1, WAIT), "Control+Delete asked nothing");
            assertEquals(answering.asked().get(0), I18n.f("ui.confirmDeleteFromDatabase", DOOMED),
                "Control+Delete asked something else");
        }

        Components.settle();

        assertNotNull(model.getLocByName(DOOMED), "Control+Delete answered No deleted the train");

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            Keys.press(keyboard(), KeyEvent.VK_DELETE, InputEvent.CTRL_DOWN_MASK);

            assertTrue(answering.awaitAsked(1, WAIT), "Control+Delete asked nothing the second time");
        }

        assertTrue(waitFor(() -> model.getLocByName(DOOMED) == null), "Control+Delete answered Yes left the train in the database");
        assertTrue(waitFor(() -> onKey(KeyEvent.VK_G) == null), "the deleted train is still on its key");
    }

    /**
     * Alt+S saves the functions that are on, Alt+O turns every function off, and Alt+P puts the saved ones back; each
     * reaches the railway.
     *
     * MUTATION: `locFunctionsOff` for `applyPreferredFunctions` in the Alt+P arm, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheFunctionPresetKeys() throws Exception
    {
        Keys.press(keyboard(), KeyEvent.VK_F1);
        Keys.press(keyboard(), KeyEvent.VK_F3);

        assertTrue(waitFor(() -> one.getF(1) && one.getF(3)), "precondition: F1 and F3 did not switch f1 and f3 on");

        Keys.press(keyboard(), KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK);

        // Saved on a thread of its own
        assertTrue(waitFor(() -> saved(one, 1) && saved(one, 3)), "Alt+S did not save f1 and f3");

        int off = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_O, InputEvent.ALT_DOWN_MASK);

        assertTrue(waitFor(() -> !one.getF(1) && !one.getF(3)), "Alt+O left a function on");
        assertTrue(waitFor(() -> lastFunction(off, one, 1) == 0 && lastFunction(off, one, 3) == 0),
            "Alt+O did not tell the railway");

        int back = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK);

        assertTrue(Components.waitFor(() -> one.getF(1) && one.getF(3), WAIT * 4),
            "Alt+P did not put the saved functions back");
        assertTrue(waitFor(() -> lastFunction(back, one, 1) == 1 && lastFunction(back, one, 3) == 1),
            "Alt+P did not tell the railway");
        assertFalse(one.getF(2), "Alt+P switched on a function that was not saved");
    }

    /**
     * Alt+U saves the shown train's speed and Alt+V runs it at that speed again.
     *
     * MUTATION: take `applyPreferredSpeed` out of the Alt+V arm, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheSpeedPresetKeys() throws Exception
    {
        Keys.press(keyboard(), KeyEvent.VK_5);

        assertTrue(waitFor(() -> one.getSpeed() > 0), "precondition: 5 did not start the train");

        final int saved = one.getSpeed();

        Keys.press(keyboard(), KeyEvent.VK_U, InputEvent.ALT_DOWN_MASK);

        assertTrue(waitFor(() -> one.getPreferredSpeed() == saved), "Alt+U did not save the speed");

        Keys.press(keyboard(), KeyEvent.VK_1);

        assertTrue(waitFor(() -> one.getSpeed() == 0), "precondition: 1 did not stop the train");

        int mark = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_V, InputEvent.ALT_DOWN_MASK);

        assertTrue(waitFor(() -> one.getSpeed() == saved), "Alt+V ran the train at " + one.getSpeed() + ", not the "
            + saved + " Alt+U saved");

        assertTrue(waitFor(() -> lastSpeed(mark, one) == saved), "Alt+V did not tell the railway");
    }

    /** Whether a function is on in the train's saved preset. */
    private static boolean saved(Locomotive loc, int fn)
    {
        boolean[] preset = loc.getPreferredFunctions();

        return preset != null && preset.length > fn && preset[fn];
    }

    private static int lastSpeed(int mark, MarklinLocomotive loc)
    {
        List<Sent> sent = wire.speedCommandsSince(mark, loc.getIntUID());

        return sent.isEmpty() ? -1 : sent.get(sent.size() - 1).speed();
    }

    private static int lastFunction(int mark, MarklinLocomotive loc, int fn)
    {
        int last = -1;

        for (Sent s : wire.functionCommandsSince(mark, loc.getIntUID()))
        {
            if (s.fifth() == fn) last = s.sixth();
        }

        return last;
    }

    /** The trains on the page showing. */
    private static Set<Locomotive> mapped()
    {
        return Components.onTheEventThread(() ->
        {
            Set<Locomotive> on = new HashSet<>();

            for (JButton key : letters().values())
            {
                Locomotive loc = ui.getButtonLocomotive(key);

                if (loc != null) on.add(loc);
            }

            return on;
        });
    }

    /** The train on a letter of the page showing. */
    private static Locomotive onKey(int key)
    {
        return Components.onTheEventThread(() -> ui.getButtonLocomotive(letters().get(key)));
    }

    /** Turns to a page by its tab, as clicking the tab does. */
    private static void turnTo(int page) throws Exception
    {
        Components.onTheEventThread(() ->
        {
            tabs().setSelectedIndex(page - 1);

            return null;
        });

        assertTrue(waitFor(() -> ui.getLocMappingNumber() == page), "precondition: page " + page + " did not show");

        Components.settle();
    }

    /** Puts a train on a letter of the page showing, or takes it off with null, as the selector does. */
    private static void setKey(int key, Locomotive loc) throws Exception
    {
        JButton button = letters().get(key);

        Keys.press(keyboard(), key);

        assertTrue(waitFor(() -> field0("currentButton") == button), "precondition: the letter did not choose its key");

        Components.onTheEventThread(() ->
        {
            Locomotive there = ui.getButtonLocomotive(button);

            // Mapping the train already there takes it off
            if (loc == null && there != null) ui.mapLocToCurrentButton(there.getName());

            if (loc != null && there != loc) ui.mapLocToCurrentButton(loc.getName());

            return null;
        });

        assertTrue(waitFor(() -> ui.getButtonLocomotive(button) == loc), "precondition: the key does not hold " + loc);
    }

    /**
     * Opens the page menu with a right-click on the page label and finds an item in it.
     *
     * @return the item
     */
    private static AbstractButton pageMenuItem(String item) throws Exception
    {
        // The page label, or the tab of the page showing where the preference shows page tabs instead: both carry the page
        // menu.  On the tab, not beside it: a right-click on another tab turns to that page first
        Component label = (Component) field("LocMappingNumberLabel");

        Components.select(label);

        JPopupMenu menu;

        if (Components.onTheEventThread(label::isShowing))
        {
            menu = rightClick(label, null);
        }
        else
        {
            JTabbedPane tabs = tabs();

            java.awt.Rectangle tab = Components.onTheEventThread(() -> tabs.getBoundsAt(tabs.getSelectedIndex()));

            menu = rightClick(tabs, new java.awt.Point((int) tab.getCenterX(), (int) tab.getCenterY()));
        }

        AbstractButton chosen = Components.withText(menu, item);

        assertNotNull(chosen, "the page menu has no \"" + item + "\"");

        return chosen;
    }

    /**
     * Does something that asks one question, answers it with a button, and lets the answer's work finish.
     *
     * @return what was asked
     */
    private static List<String> answering(String button, Runnable ask)
    {
        try (Answering answering = Answering.start(message -> Answering.press(button)))
        {
            ask.run();

            assertTrue(answering.awaitAsked(1, WAIT), "nothing was asked");

            Components.settle();

            return answering.asked();
        }
    }

    /** Opens a letter key's right-click menu and finds an item in it, in a submenu or not. */
    private static AbstractButton rightClickMenuItem(int key, String item) throws Exception
    {
        JPopupMenu menu = rightClick(letters().get(key), null);

        AbstractButton found = Components.withText(menu, item);

        assertNotNull(found, "the key's menu has no \"" + item + "\"");

        return found;
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

    /**
     * Answers the name and address dialog: the field showing the old name gets the new one, the field showing the old
     * address the new one, and OK is pressed.
     */
    private static Thread answerTheAddressDialog(String oldName, String newName, String oldAddress, String newAddress)
    {
        Thread t = new Thread(() ->
        {
            long until = System.currentTimeMillis() + WAIT * 2;

            while (System.currentTimeMillis() < until)
            {
                boolean done = Components.onTheEventThread(() ->
                {
                    for (Window w : Window.getWindows())
                    {
                        if (!(w instanceof JDialog) || !w.isShowing()) continue;

                        JOptionPane pane = Components.find(w, JOptionPane.class, null);

                        if (pane == null) continue;

                        JTextField name = Components.find(pane, JTextField.class, f -> oldName.equals(f.getText()));
                        JTextField address = Components.find(pane, JTextField.class, f -> oldAddress.equals(f.getText()));

                        if (name == null || address == null) continue;

                        name.setText(newName);
                        address.setText(newAddress);

                        JButton ok = Components.find(pane, JButton.class, b -> I18n.t("ui.ok").equals(b.getText()));

                        SwingUtilities.invokeLater(ok::doClick);

                        return true;
                    }

                    return false;
                });

                if (done) return;

                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException e)
                {
                    return;
                }
            }
        }, "answering the address dialog");

        t.setDaemon(true);
        t.start();

        return t;
    }

    private static Component keyboard() throws Exception
    {
        return (Component) field("KeyboardTab");
    }

    private static JTabbedPane tabs() throws Exception
    {
        return (JTabbedPane) field("locKeyTabs");
    }

    private static Object field(String name) throws Exception
    {
        Field f = TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(ui);
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

    @SuppressWarnings("unchecked")
    private static Map<Integer, JButton> letters()
    {
        return (Map<Integer, JButton>) field0("buttonMapping");
    }

    private static boolean waitFor(BooleanSupplier condition)
    {
        return Components.waitFor(condition, WAIT);
    }

    // A stand-in for the Mouse helper another worker owns

    /**
     * A right-click as Windows delivers it: the press, then the release that is the pop-up trigger.
     *
     * @param at where on the component, or null for its middle
     *
     * @return the menu it opened
     */
    private static JPopupMenu rightClick(final Component on, final java.awt.Point at)
    {
        Components.select(on);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(on::isShowing), WAIT),
            "precondition: the component to right-click is not on screen");

        Components.onTheEventThread(() ->
        {
            long now = System.currentTimeMillis();

            int x = at != null ? at.x : Math.max(1, on.getWidth() / 2);
            int y = at != null ? at.y : Math.max(1, on.getHeight() / 2);

            on.dispatchEvent(new MouseEvent(on, MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON3_DOWN_MASK, x, y, 1,
                false, MouseEvent.BUTTON3));
            on.dispatchEvent(new MouseEvent(on, MouseEvent.MOUSE_RELEASED, now + 20, 0, x, y, 1, true,
                MouseEvent.BUTTON3));

            return null;
        });

        Components.settle();

        JPopupMenu menu = Components.popupShowing();

        assertNotNull(menu, "the right-click opened no menu");

        return menu;
    }
}
