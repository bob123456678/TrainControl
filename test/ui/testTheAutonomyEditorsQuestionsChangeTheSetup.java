package ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;

/**
 * The autonomy editor's questions, answered as the user answers them, and what each answer leaves in the setup once the
 * editor's Save has written it (FR-136 Phase 6).
 *
 * The Bulk Tools that ask before they act - Home All Trains Where They Stand, Clear All Home Locomotives, Place All Trains
 * at Their Homes - answered Yes and No; and a square's own questions: its speed multiplier, the locomotives kept out of
 * it, its home locomotive, and taking its train off.  Each claim opens the editor afresh on the frozen railway, picks the
 * item from the menu the editor builds, answers the real dialog, presses Save, and reads the setup back from disk.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).  Between claims the setup is put back as it opened.
 *
 * @author Adam
 */
public class testTheAutonomyEditorsQuestionsChangeTheSetup
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;

    /** The setup as the class found it, put back before every claim. */
    private static org.json.JSONObject asOpened;

    private static final String PAGE = "1 - Main";

    private LayoutEditor editor;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the autonomy editor needs a display");

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration, so there is no editor to open");
        }

        asOpened = Components.onTheEventThread(() -> session.snapshotSetup());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
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

    /** The setup put back as it opened, the railway built from it, and the editor opened on the main page. */
    @BeforeMethod
    public void openTheEditor() throws Exception
    {
        if (asOpened == null) throw new SkipException("the class did not set up");

        Components.onTheEventThread(() ->
        {
            session.restoreSetup(asOpened);

            // every train named, so the railway stands each where the setup has it rather than where the last claim left it
            Set<String> trains = new LinkedHashSet<>(session.placementsAutonomyWillWrite().values());

            trains.addAll(session.homesToPlace().values());

            ui.rebuildRunningLayoutFromSetup(false, trains);

            return null;
        });

        Components.settle();

        final LayoutDiagram page = model.getLayout(PAGE);

        assertNotNull(page, "precondition: no page " + PAGE);

        editor = Components.onTheEventThread(() ->
        {
            LayoutEditor built = new LayoutEditor(page, 30, ui, 0);

            built.render();
            built.setAutonomyMode(session);

            return built;
        });

        Components.settle();
    }

    @AfterMethod(alwaysRun = true)
    public void closeTheEditor() throws Exception
    {
        org.traincontrol.gui.FacingPrompt.answerForTests(null);

        if (editor != null)
        {
            final LayoutEditor closing = editor;

            SwingUtilities.invokeAndWait(() -> closing.dispose());

            editor = null;
        }
    }

    // ---------------------------------------------------------------- Bulk Tools

    /**
     * Home All Trains Where They Stand, answered Yes: every standing train's square is its home, saved.  The question says
     * how many.
     *
     * MUTATION: have the Yes skip `session.homeEveryPlacedTrain()`, and this fails.
     */
    @Test
    public void testHomeAllTrainsWhereTheyStandYesHomesEachOnItsSquare() throws Exception
    {
        Map<TileKey, String> standing = session.trainsWhereTheyStand();

        assertFalse(standing.isEmpty(), "precondition: no train stands on the frozen railway");

        List<String> asked = pressAndAnswer(bulkItem(I18n.f("autolayout.ui.menuHomeEveryTrainWhereItStands",
            standing.size())), yesTo(I18n.f("autolayout.ui.confirmHomeEveryTrainWhereItStands", standing.size())));

        assertEquals(asked.get(0), I18n.f("autolayout.ui.confirmHomeEveryTrainWhereItStands", standing.size()),
            "the question is not the one about homing the standing trains");

        AutonomySession saved = saveAndReadBack();

        Map<TileKey, String> wrong = new LinkedHashMap<>();

        for (Map.Entry<TileKey, String> e : standing.entrySet())
        {
            if (!e.getValue().equals(saved.getPointProperty(e.getKey(), "home")))
            {
                wrong.put(e.getKey(), String.valueOf(saved.getPointProperty(e.getKey(), "home")));
            }
        }

        assertTrue(wrong.isEmpty(), "after Yes and Save, these squares are not the home of the train on them: " + wrong);
    }

    /**
     * Answered No, it changes no home.
     *
     * MUTATION: let No fall through to the homing, and this fails.
     */
    @Test
    public void testHomeAllTrainsWhereTheyStandNoChangesNoHome() throws Exception
    {
        Map<TileKey, String> homes = homesIn(session);

        Map<TileKey, String> standing = session.trainsWhereTheyStand();

        assertFalse(standingAsHomes(standing).equals(homes), "precondition: every train already stands at its"
            + " home, so a No and a Yes would leave the same homes");

        pressAndAnswer(bulkItem(I18n.f("autolayout.ui.menuHomeEveryTrainWhereItStands", standing.size())), no());

        assertEquals(homesIn(saveAndReadBack()), homes, "after No and Save, the homes changed");
    }

    /**
     * Clear All Home Locomotives, answered Yes: no square has a home, saved; the trains stay where they stand.
     *
     * MUTATION: have the Yes skip `session.clearEveryHome()`, and this fails.
     */
    @Test
    public void testClearAllHomeLocomotivesYesClearsEveryHome() throws Exception
    {
        int homed = session.tilesWithAHome().size();

        assertTrue(homed > 0, "precondition: no square of the frozen railway has a home");

        Map<TileKey, String> placed = session.placementsAutonomyWillWrite();

        List<String> asked = pressAndAnswer(bulkItem(I18n.f("autolayout.ui.menuClearAllHomeLocomotives", homed)),
            yesTo(I18n.t("autolayout.ui.confirmClearAllHomeLocomotives")));

        assertEquals(asked.get(0), I18n.t("autolayout.ui.confirmClearAllHomeLocomotives"), "the question is not the one"
            + " about clearing the homes");

        AutonomySession saved = saveAndReadBack();

        assertEquals(saved.tilesWithAHome(), new ArrayList<TileKey>(), "after Yes and Save, squares still have homes");

        assertEquals(saved.placementsAutonomyWillWrite(), placed, "clearing the homes moved the trains");
    }

    /**
     * Answered No, every home stays.
     *
     * MUTATION: let No fall through to the clear, and this fails.
     */
    @Test
    public void testClearAllHomeLocomotivesNoKeepsEveryHome() throws Exception
    {
        Map<TileKey, String> homes = homesIn(session);

        assertFalse(homes.isEmpty(), "precondition: no square of the frozen railway has a home");

        pressAndAnswer(bulkItem(I18n.f("autolayout.ui.menuClearAllHomeLocomotives", session.tilesWithAHome().size())),
            no());

        assertEquals(homesIn(saveAndReadBack()), homes, "after No and Save, the homes changed");
    }

    /**
     * Place All Trains at Their Homes, answered Yes: each train with a home stands on it, and a train with none is taken
     * off, saved.  The question gives both counts.
     *
     * MUTATION: have the Yes skip `session.placeEveryTrainAtHome(...)`, and this fails.
     */
    @Test
    public void testPlaceAllTrainsAtTheirHomesYesStandsEachOnItsHome() throws Exception
    {
        Map<TileKey, String> homes = session.homesToPlace();

        assertTrue(awayFromHome(homes, session) > 0, "precondition: every train already stands at its home");

        String question = I18n.f("autolayout.ui.confirmPlaceEveryTrainAtHome", homes.size(), homeless(homes));

        List<String> asked = pressAndAnswer(bulkItem(I18n.f("autolayout.ui.menuPlaceEveryTrainAtHome", homes.size())),
            yesTo(question));

        assertEquals(asked.get(0), question, "the question does not give the homes and the trains without one");

        AutonomySession saved = saveAndReadBack();

        assertEquals(awayFromHome(homes, saved), 0, "after Yes and Save, a train with a home is not on it: "
            + saved.placementsAutonomyWillWrite());

        Set<String> placedNow = new LinkedHashSet<>(saved.placementsAutonomyWillWrite().values());

        placedNow.removeAll(homes.values());

        assertTrue(placedNow.isEmpty(), "after Yes and Save, trains with no home still stand: " + placedNow);
    }

    /**
     * Answered No, no train moves.
     *
     * MUTATION: let No fall through to the placing, and this fails.
     */
    @Test
    public void testPlaceAllTrainsAtTheirHomesNoMovesNoTrain() throws Exception
    {
        Map<TileKey, String> homes = session.homesToPlace();

        assertTrue(awayFromHome(homes, session) > 0, "precondition: every train already stands at its home");

        Map<TileKey, String> placed = session.placementsAutonomyWillWrite();

        pressAndAnswer(bulkItem(I18n.f("autolayout.ui.menuPlaceEveryTrainAtHome", homes.size())), no());

        assertEquals(saveAndReadBack().placementsAutonomyWillWrite(), placed, "after No and Save, the trains moved");
    }

    // ---------------------------------------------------------------- a square's own questions

    /**
     * Speed Multiplier answered 150: the square's multiplier is 1.5, saved.
     *
     * MUTATION: store the percentage as `value / 10.0`, and this fails.
     */
    @Test
    public void testSpeedMultiplierTakesAPercentage() throws Exception
    {
        TileKey square = aStationWithATrain();

        assertNull(session.getPointProperty(square, "speedMultiplier"), "precondition: the square has a multiplier");

        List<String> asked = pressAndAnswer(speedItem(square), message -> Answering.type("150"));

        assertEquals(asked.size(), 1, "the speed multiplier asked more than its question: " + asked);

        assertEquals(numberOf(saveAndReadBack().getPointProperty(square, "speedMultiplier")), 1.5,
            "after 150% and Save, the square's multiplier is not 1.5");
    }

    /**
     * Answered 250, it is refused with the sentence giving the range, and nothing is written.
     *
     * MUTATION: let the range run to 300, and this fails.
     */
    @Test
    public void testSpeedMultiplierRefusesAboveTwoHundred() throws Exception
    {
        assertRefusedPercent("250");
    }

    /**
     * Answered with a word, it is refused with the same sentence, and nothing is written.
     *
     * MUTATION: leave the refusal out of the catch, and this fails.
     */
    @Test
    public void testSpeedMultiplierRefusesAWord() throws Exception
    {
        assertRefusedPercent("fast");
    }

    private void assertRefusedPercent(final String typed) throws Exception
    {
        TileKey square = aStationWithATrain();

        assertNull(session.getPointProperty(square, "speedMultiplier"), "precondition: the square has a multiplier");

        List<String> asked = pressAndAnswer(speedItem(square),
            message -> message.equals(I18n.t("autolayout.ui.errorInvalidSpeedMultiplier")) ? null : Answering.type(typed));

        assertEquals(asked.size(), 2, typed + " was not refused with a second message: " + asked);

        assertEquals(asked.get(1), I18n.t("autolayout.ui.errorInvalidSpeedMultiplier"), typed + " was refused with"
            + " another sentence");

        assertNull(saveAndReadBack().getPointProperty(square, "speedMultiplier"), typed + " was written as the square's"
            + " multiplier");
    }

    /**
     * Excluded Locomotives with one chosen and OK: the square keeps that train out, saved.
     *
     * MUTATION: store the list as empty, and this fails.
     */
    @Test
    public void testExcludedLocomotivesKeepsTheChosenOneOut() throws Exception
    {
        TileKey square = aStationWithATrain();

        String home = (String) session.getPointProperty(square, "home");

        String chosen = null;

        for (String name : model.getLocList())
        {
            if (chosen == null && !name.equals(home)) chosen = name;
        }

        assertNotNull(chosen, "precondition: no locomotive to keep out");

        List<String> asked = pressAndAnswer(excludedItem(square), choosing(chosen));

        assertEquals(asked.size(), 1, "keeping " + chosen + " out asked more than its question: " + asked);

        assertEquals(String.valueOf(saveAndReadBack().getPointProperty(square, "excludedLocs")),
            new org.json.JSONArray().put(chosen).toString(), "after choosing " + chosen + " and Save, the square does not"
            + " keep it out");
    }

    /**
     * Keeping out the square's own home train asks first, naming it; No writes nothing.
     *
     * MUTATION: let the question's No carry on to the write, and this fails.
     */
    @Test
    public void testExcludingTheHomeTrainAsksAndNoWritesNothing() throws Exception
    {
        TileKey square = null;

        for (Map.Entry<TileKey, String> e : session.homesToPlace().entrySet())
        {
            if (square == null && PAGE.equals(e.getKey().getPage())
                && session.getPointProperty(e.getKey(), "excludedLocs") == null) square = e.getKey();
        }

        assertNotNull(square, "precondition: no square on " + PAGE + " has a home");

        final String home = (String) session.getPointProperty(square, "home");

        final boolean[] listed = {false};

        List<String> asked = pressAndAnswer(excludedItem(square), message ->
        {
            if (listed[0]) return noAnswer();

            listed[0] = true;

            return choosing(home).apply(message);
        });

        assertEquals(asked.size(), 2, "keeping out the home train " + home + " did not ask about it: " + asked);

        assertTrue(asked.get(1).contains(home), "the question does not name the home train " + home + ": " + asked.get(1));

        assertNull(saveAndReadBack().getPointProperty(square, "excludedLocs"), "after No and Save, the square keeps a"
            + " train out");
    }

    /**
     * Set Home Locomotive with a train homed elsewhere asks before it moves the home; Yes moves it, saved: home here, and
     * not there.
     *
     * MUTATION: skip the question, and this fails.
     */
    @Test
    public void testAHomeElsewhereIsMovedOnlyOnYes() throws Exception
    {
        Object[] picked = aSquareAndATrainHomedElsewhere();

        TileKey square = (TileKey) picked[0];
        String train = (String) picked[1];
        TileKey elsewhere = (TileKey) picked[2];

        String question = I18n.f("autolayout.ui.confirmHomeIsAlreadySet", train, "").trim();

        List<String> asked = pressAndAnswer(homeItem(square), message ->
            message.startsWith(I18n.t("autosetup.ui.promptHomeFor")) ? Answering.type(train)
            : message.startsWith(question.substring(0, Math.min(question.length(), 12))) ? yes() : Answering.pressAt(0));

        assertTrue(asked.size() >= 2 && asked.get(1).contains(train), "giving " + train + " a second home did not ask"
            + " first: " + asked);

        AutonomySession saved = saveAndReadBack();

        assertEquals(saved.getPointProperty(square, "home"), train, "after Yes and Save, " + train + " is not at home here");

        assertNull(saved.getPointProperty(elsewhere, "home"), "after Yes and Save, " + train + " is still at home"
            + " where it was");
    }

    /**
     * Answered No, both homes stay as they were.
     *
     * MUTATION: let No carry on to `setHome`, and this fails.
     */
    @Test
    public void testAHomeElsewhereStaysOnNo() throws Exception
    {
        Object[] picked = aSquareAndATrainHomedElsewhere();

        TileKey square = (TileKey) picked[0];
        String train = (String) picked[1];
        TileKey elsewhere = (TileKey) picked[2];

        Object homeHere = session.getPointProperty(square, "home");

        List<String> asked = pressAndAnswer(homeItem(square), message ->
            message.startsWith(I18n.t("autosetup.ui.promptHomeFor")) ? Answering.type(train) : noAnswer());

        assertTrue(asked.size() >= 2, "giving " + train + " a second home did not ask first: " + asked);

        AutonomySession saved = saveAndReadBack();

        assertEquals(saved.getPointProperty(square, "home"), homeHere, "after No and Save, the square's home changed");

        assertEquals(saved.getPointProperty(elsewhere, "home"), train, "after No and Save, " + train + " lost its home");
    }

    /**
     * Remove {0} from This Square takes the train off the square, saved, and leaves every other train where it was.
     *
     * MUTATION: have the item skip `session.placeLocomotive(target, null)`, and this fails.
     */
    @Test
    public void testRemoveTakesTheTrainOffTheSquare() throws Exception
    {
        TileKey square = aStationWithATrain();

        String train = session.getLocomotiveNameAt(square);

        Map<TileKey, String> others = session.placementsAutonomyWillWrite();

        others.remove(square);

        pressAndAnswer(tileItem(square, I18n.f("autosetup.ui.menuRemoveLocomotive", train)), message -> null);

        AutonomySession saved = saveAndReadBack();

        assertNull(saved.getLocomotiveNameAt(square), "after Remove and Save, " + train + " still stands on the square");

        assertEquals(saved.placementsAutonomyWillWrite(), others, "Remove moved other trains");
    }

    // ---------------------------------------------------------------- the station's protecting signals

    /**
     * Exit Guard Signal..., then By Address with a signal's address, then Done: the window closes and the station is
     * protected by that signal, saved.
     *
     * MUTATION: have By Address skip pairing what it found, or Done leave the window open, and this fails.
     */
    @Test
    public void testASignalPairedByAddressIsKeptOnDone() throws Exception
    {
        TileKey station = aStationWithNoSignal();
        TileKey signal = aSignalOnThePage();

        int address = session.getGraph().getTiles().get(signal).getAddress();

        List<String> asked = pairBy(station, String.valueOf(address), 1, 1);

        assertEquals(asked, java.util.Collections.singletonList(I18n.t("autosetup.ui.promptSignalAddress")),
            "pairing by address asked something else, or more");

        assertEquals(saveAndReadBack().getProtectingSignals(station), java.util.Collections.singletonList(signal),
            "after By Address " + address + ", Done and Save, the station is not protected by that signal");
    }

    /**
     * An address that is not a number is refused with the sentence naming it, and nothing is paired.
     *
     * MUTATION: drop the sentence for a piece that is not a number, and this fails.
     */
    @Test
    public void testAnAddressThatIsNotANumberIsRefused() throws Exception
    {
        TileKey station = aStationWithNoSignal();

        List<String> asked = pairBy(station, "north", 0, 2);

        assertEquals(asked.size(), 2, "the word was not refused with a message: " + asked);

        assertEquals(asked.get(1), I18n.f("autosetup.ui.errorSignalAddressNotANumber", "north"), "the word was refused"
            + " with another sentence");

        assertEquals(saveAndReadBack().getProtectingSignals(station), new ArrayList<TileKey>(), "a signal was paired"
            + " from a word");
    }

    /**
     * Opens the station's Exit Guard Signal window from its menu, presses By Address and types this, then presses Done
     * once every question has been answered and the window lists as many signals as expected (or three seconds later).  The window is a dialog of
     * the editor's own, so it is driven here; its address question and its refusals are plain ones, which `Answering`
     * takes.
     *
     * @param questions how many questions the address brings: its own, and a refusal if there is one
     */
    private List<String> pairBy(TileKey station, final String typed, final int expected, final int questions)
        throws Exception
    {
        final String title = I18n.t("autosetup.ui.menuPairSignal");

        final AbstractButton item = tileItem(station, title);

        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);
        final java.util.concurrent.atomic.AtomicBoolean byAddress = new java.util.concurrent.atomic.AtomicBoolean();
        final long[] answeredAt = {0};
        final CountDownLatch done = new CountDownLatch(1);

        try (final Answering answering = Answering.start(
            message -> message.equals(I18n.t("autosetup.ui.promptSignalAddress")) ? Answering.type(typed) : null))
        {
            Thread driving = new Thread(() ->
            {
                while (going.get())
                {
                    Components.onTheEventThread(() ->
                    {
                        javax.swing.JDialog window = null;
                        boolean questionUp = false;

                        for (java.awt.Window w : java.awt.Window.getWindows())
                        {
                            if (!(w instanceof javax.swing.JDialog) || !w.isShowing()) continue;

                            if (Components.find(w, JOptionPane.class, null) != null) questionUp = true;
                            else if (title.equals(((javax.swing.JDialog) w).getTitle())) window = (javax.swing.JDialog) w;
                        }

                        if (window == null || questionUp) return null;

                        if (byAddress.compareAndSet(false, true))
                        {
                            Components.press(Components.withText(window, I18n.t("autosetup.ui.optionSignalAddress")));
                        }
                        else if (answering.asked().size() >= questions)
                        {
                            JList<?> list = Components.find(window, JList.class, null);

                            // the empty list says so in a row of its own
                            int listed = list == null ? -1 : list.getModel().getSize() == 1
                                && I18n.t("autosetup.ui.signalListEmpty").equals(list.getModel().getElementAt(0))
                                ? 0 : list.getModel().getSize();

                            if (answeredAt[0] == 0) answeredAt[0] = System.currentTimeMillis();

                            // DONE ONCE THE LIST SHOWS THE ANSWER, or after three seconds if it never does: then the claim
                            // reads what was kept, rather than this waiting for a list that is not coming
                            if (listed == expected || System.currentTimeMillis() - answeredAt[0] > 3000)
                            {
                                Components.press(Components.withText(window, I18n.t("autosetup.ui.optionSignalsDone")));
                            }
                        }

                        return null;
                    });

                    try
                    {
                        Thread.sleep(100);
                    }
                    catch (InterruptedException e)
                    {
                        return;
                    }
                }
            }, "pairing");

            driving.setDaemon(true);
            driving.start();

            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    item.doClick();
                }
                finally
                {
                    done.countDown();
                }
            });

            try
            {
                assertTrue(done.await(30, TimeUnit.SECONDS), "the Exit Guard Signal window did not close on Done; it"
                    + " asked: " + answering.asked());
            }
            finally
            {
                going.set(false);

                driving.join(2000);
            }

            Components.settle();

            return answering.asked();
        }
    }

    /** A station on the main page that no signal protects yet. */
    private static TileKey aStationWithNoSignal()
    {
        for (TileKey tile : session.placementsAutonomyWillWrite().keySet())
        {
            if (PAGE.equals(tile.getPage()) && session.getProtectingSignals(tile).isEmpty()
                && session.getEntrySignals(tile).isEmpty()) return tile;
        }

        throw new AssertionError("precondition: every station with a train on " + PAGE + " has a signal");
    }

    /** A signal on the main page with an accessory and an address no other signal has, which pairing by address finds. */
    private static TileKey aSignalOnThePage()
    {
        Map<Integer, List<TileKey>> byAddress = new LinkedHashMap<>();

        for (Map.Entry<TileKey, org.traincontrol.base.LayoutDiagramComponent> e : session.getGraph().getTiles().entrySet())
        {
            if (e.getValue() != null
                && e.getValue().getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.SIGNAL
                && e.getValue().getAccessory() != null)
            {
                byAddress.computeIfAbsent(e.getValue().getAddress(), a -> new ArrayList<>()).add(e.getKey());
            }
        }

        // ITS ADDRESS ITS OWN, so the address names this signal and no other
        for (List<TileKey> carrying : byAddress.values())
        {
            if (carrying.size() == 1 && PAGE.equals(carrying.get(0).getPage())) return carrying.get(0);
        }

        throw new AssertionError("precondition: no signal on " + PAGE);
    }

    // ---------------------------------------------------------------- doors

    /** A square on the main page with a train standing on it, the first in the setup's order. */
    private static TileKey aStationWithATrain()
    {
        for (TileKey tile : session.placementsAutonomyWillWrite().keySet())
        {
            if (PAGE.equals(tile.getPage())) return tile;
        }

        throw new AssertionError("precondition: no train stands on " + PAGE);
    }

    /**
     * A station on the main page and a standing train homed on another square, whose name no other standing train's
     * contains (the picker's filter is typed into).
     */
    private static Object[] aSquareAndATrainHomedElsewhere()
    {
        Set<String> standing = new LinkedHashSet<>(session.placementsAutonomyWillWrite().values());

        for (Map.Entry<TileKey, String> home : session.homesToPlace().entrySet())
        {
            String train = home.getValue();

            if (!standing.contains(train)) continue;

            boolean unique = true;

            for (String other : standing) unique &= other.equals(train) || !other.contains(train);

            if (!unique) continue;

            for (TileKey square : session.placementsAutonomyWillWrite().keySet())
            {
                if (PAGE.equals(square.getPage()) && !square.equals(home.getKey())
                    && session.getStore().isStation(square))
                {
                    return new Object[] {square, train, home.getKey()};
                }
            }
        }

        throw new AssertionError("precondition: no standing train is homed away from a station on " + PAGE);
    }

    private AutonomyEditorPanel panel()
    {
        return editor.getAutonomyPanel();
    }

    /** An item of Bulk Tools as the editor builds it. */
    private AbstractButton bulkItem(String text)
    {
        javax.swing.JMenu bulk = Components.onTheEventThread(() -> panel().buildBulkMenuForTest());

        AbstractButton item = Components.withText(bulk, text);

        assertNotNull(item, "precondition: Bulk Tools has no \"" + text + "\"");

        assertTrue(item.isEnabled(), "precondition: \"" + text + "\" is greyed: " + item.getToolTipText());

        return item;
    }

    /** An item of a square's right-click menu as the editor builds it. */
    private AbstractButton tileItem(final TileKey square, String text)
    {
        javax.swing.JPopupMenu menu = Components.onTheEventThread(() -> panel().buildTileMenu(square, null));

        assertNotNull(menu, "precondition: " + square + " has no menu");

        AbstractButton item = Components.withText(menu, text);

        assertNotNull(item, "precondition: the menu of " + square + " has no \"" + text + "\"");

        return item;
    }

    private AbstractButton speedItem(TileKey square)
    {
        return tileItem(square, I18n.f("autolayout.ui.menuSpeedMultiplier", 100));
    }

    private AbstractButton excludedItem(TileKey square)
    {
        Object listed = session.getPointProperty(square, "excludedLocs");

        int count = listed instanceof org.json.JSONArray ? ((org.json.JSONArray) listed).length() : 0;

        return tileItem(square, I18n.f("autolayout.ui.menuExcludedLocomotives", count));
    }

    private AbstractButton homeItem(TileKey square)
    {
        Object home = session.getPointProperty(square, "home");

        return tileItem(square, home == null ? I18n.t("autosetup.ui.menuHomeNone")
            : I18n.f("autosetup.ui.menuHomeFor", home));
    }

    /**
     * Presses an item as a click does and answers each question it asks by the rule, returning once the click's own work
     * is done, and what was asked.
     */
    private static List<String> pressAndAnswer(final AbstractButton item, Function<String, Answering.Answer> rule)
        throws Exception
    {
        final CountDownLatch done = new CountDownLatch(1);

        try (Answering answering = Answering.start(rule))
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    item.doClick();
                }
                finally
                {
                    done.countDown();
                }
            });

            assertTrue(done.await(30, TimeUnit.SECONDS), "\"" + item.getText() + "\" did not finish; it asked: "
                + answering.asked());

            Components.settle();

            return answering.asked();
        }
    }

    /** The editor's Save, pressed; then the setup read back from the files it wrote. */
    private AutonomySession saveAndReadBack() throws Exception
    {
        AbstractButton save = Components.withText(editor, I18n.t("layout.ui.saveChanges"));

        assertNotNull(save, "precondition: the editor has no Save button");

        pressAndAnswer(save, message -> null);

        AutonomySession reread = new AutonomySession(sandbox.getFolder());

        reread.open(support.LayoutSandbox.wiredPages(model));

        return reread;
    }

    private static Function<String, Answering.Answer> yesTo(final String question)
    {
        return message -> message.equals(question) ? yes() : null;
    }

    private static Answering.Answer yes()
    {
        return Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[0]));
    }

    private static Function<String, Answering.Answer> no()
    {
        return message -> noAnswer();
    }

    private static Answering.Answer noAnswer()
    {
        return Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[1]));
    }

    /**
     * Picks this locomotive in the list of the question on screen, then presses OK: the list is part of the question's
     * own panel, which `Answering` does not type into.
     */
    private static Function<String, Answering.Answer> choosing(final String name)
    {
        return message ->
        {
            Components.onTheEventThread(() ->
            {
                for (java.awt.Window w : java.awt.Window.getWindows())
                {
                    if (!w.isShowing()) continue;

                    JOptionPane pane = Components.find(w, JOptionPane.class, null);

                    if (pane == null) continue;

                    @SuppressWarnings("unchecked")
                    JList<String> list = Components.find(pane, JList.class, null);

                    if (list != null) list.setSelectedValue(name, true);
                }

                return null;
            });

            return Answering.pressAt(0);
        };
    }

    /** Every home in a setup, square to train. */
    private static Map<TileKey, String> homesIn(AutonomySession setup)
    {
        Map<TileKey, String> out = new LinkedHashMap<>();

        for (TileKey tile : setup.tilesWithAHome()) out.put(tile, String.valueOf(setup.getPointProperty(tile, "home")));

        return out;
    }

    /** The homes the standing trains would have if each were homed where it stands, merged over the homes now. */
    private static Map<TileKey, String> standingAsHomes(Map<TileKey, String> standing)
    {
        Map<TileKey, String> out = homesIn(session);

        out.values().removeAll(standing.values());

        out.putAll(standing);

        return out;
    }

    /** How many homed trains do not stand on their home in this setup. */
    private static int awayFromHome(Map<TileKey, String> homes, AutonomySession setup)
    {
        int away = 0;

        for (Map.Entry<TileKey, String> e : homes.entrySet())
        {
            if (!e.getValue().equals(setup.getLocomotiveNameAt(e.getKey()))) away++;
        }

        return away;
    }

    /** The standing trains with no home, which Place All takes off. */
    private static int homeless(Map<TileKey, String> homes)
    {
        int count = 0;

        for (String standing : new LinkedHashSet<>(session.trainsWhereTheyStand().values()))
        {
            if (standing != null && !homes.containsValue(standing)) count++;
        }

        return count;
    }

    private static double numberOf(Object stored)
    {
        assertTrue(stored instanceof Number, "the stored value is not a number: " + stored);

        return ((Number) stored).doubleValue();
    }
}
