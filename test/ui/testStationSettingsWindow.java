package ui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.StandingRules;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.AutonomySession.WhichTrains;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.autonomyeditor.StationPreset;
import org.traincontrol.gui.autonomyeditor.StationSettingsWindow;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.locomotive.SegmentedControl;
import org.traincontrol.gui.widgets.PillButton;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Reflect;
import support.StationDoors;

/**
 * The Station Settings window (FR-111, FR-176), pressed as the user presses it on the frozen railway: what it reads, what
 * each control writes, what it grays, and that looking at a square changes nothing.
 *
 * @author Adam
 */
public class testStationSettingsWindow
{
    private static support.FrozenEditor frozen;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static org.json.JSONObject asOpened;

    private static final String PAGE = "1 - Main";

    private LayoutEditor editor;
    private AutonomyEditorPanel panel;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        frozen = support.FrozenEditor.open();
        model = frozen.model;
        ui = frozen.ui;
        session = frozen.session;
        asOpened = frozen.asOpened;
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (frozen != null) frozen.close();
    }

    @BeforeMethod
    public void openTheEditor() throws Exception
    {
        if (frozen == null) throw new SkipException("the class did not set up");

        frozen.putBack();

        editor = frozen.editorOn(PAGE);

        panel = Components.onTheEventThread(() -> editor.getAutonomyPanel());
    }

    @AfterMethod(alwaysRun = true)
    public void closeTheEditor() throws Exception
    {
        support.FrozenEditor.dispose(editor);

        editor = null;

        support.AskingWindows.closeEveryDialog();
    }

    // -------------------------------------------------------------------------------------------- looking changes nothing

    /**
     * An untouched station opened, unfolded and closed changes no setup, dirties nothing, and writes no file.
     *
     * MUTATION: have `StationWindowBase.reload` call the panel's `refresh()`, or have closing commit a field unasked by
     * dropping the "unchanged" check in `commit`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAnUntouchedStationOpenedAndClosedChangesNothing() throws Exception
    {
        TileKey square = aStation();

        assertFalse(panel.isDirty(), "precondition: the editor opened dirty");

        String before = setup();
        Map<String, Long> files = filesWritten();

        StationSettingsWindow window = StationDoors.open(panel, square);

        Components.onTheEventThread(() -> { window.openAdvancedForTest(); return null; });

        StationDoors.current(window);

        StationDoors.close(window);

        assertEquals(setup(), before, "looking at a station changed the setup");

        assertFalse(panel.isDirty(), "looking at a station dirtied the setup");

        assertEquals(filesWritten(), files, "looking at a station wrote a file");
    }

    /**
     * Every kind of square reads the same way: opened and closed, a sensor with no settings stays as it was too.
     *
     * MUTATION: have `fillHome`'s `setSelectedItem` run outside `reading`, so `pressHome` writes, and this fails.
     */
    @Test(timeOut = 180000)
    public void testNoSquareOnThePageChangesByBeingLookedAt() throws Exception
    {
        String before = setup();

        int looked = 0;

        StationSettingsWindow window = null;

        for (TileKey each : session.getReducer().getPoints().keySet())
        {
            if (!PAGE.equals(each.getPage()) || looked >= 25) continue;

            final TileKey square = each;

            window = Components.onTheEventThread(() -> panel.openStationSettings(square));

            looked++;
        }

        assertTrue(looked > 0, "precondition: no sensor squares on " + PAGE);

        StationDoors.current(window);

        StationDoors.close(window);

        assertEquals(setup(), before, "looking at the sensors of a page changed the setup");

        assertFalse(panel.isDirty(), "looking at the sensors of a page dirtied the setup");
    }

    /**
     * On the track diagram the window writes nothing when looked at, and saves a change at once, as there is no Save.
     *
     * MUTATION: drop the `placementChanged` from `act`, and the change is not saved and this fails.
     */
    @Test(timeOut = 120000)
    public void testOnTheDiagramAChangeIsSavedAtOnceAndLookingWritesNothing() throws Exception
    {
        closeTheEditor();

        TileKey square = aStation();

        Map<String, Long> files = filesWritten();

        StationSettingsWindow window = openedFromTheDiagramsMenu(square);

        StationDoors.current(window);

        assertEquals(filesWritten(), files, "looking from the diagram wrote a file");

        boolean was = session.isAutoDestination(square);

        StationDoors.press(window, "station.fullAutonomy");

        StationDoors.current(window);

        AutonomySession reread = new AutonomySession(frozen.sandbox.getFolder());

        reread.open(support.LayoutSandbox.wiredPages(model));

        assertEquals(reread.isAutoDestination(square), !was, "the change from the diagram did not reach the file");

        StationDoors.close(window);

        frozen.putBack();
    }

    // ---------------------------------------------------------------------------------------------------- the two boxes

    /**
     * An old station reads both on; an old closed square both off; a sensor that was no station, Stop off and Running
     * through on.
     *
     * MUTATION: have `StationBoxes.of` ignore `active`, and the closed square reads on and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheBoxesReadWhatAnOldSetupMeant() throws Exception
    {
        TileKey square = aStation();

        StationSettingsWindow window = StationDoors.open(panel, square);

        assertTrue(StationDoors.on(window, "station.stops"), "an old station has Stop off");
        assertTrue(StationDoors.on(window, "station.runsThrough"), "an old station has Running through off");

        Components.onTheEventThread(() -> { session.setPointProperty(square, "active", Boolean.FALSE); return null; });

        Components.onTheEventThread(() -> panel.openStationSettings(square));

        StationDoors.current(window);

        assertFalse(StationDoors.on(window, "station.stops"), "an old closed square has Stop on");
        assertFalse(StationDoors.on(window, "station.runsThrough"), "an old closed square has Running through on");

        Components.onTheEventThread(() -> { session.setPointProperty(square, "active", null); return null; });

        Components.onTheEventThread(() -> { session.setStation(square, false); return null; });

        Components.onTheEventThread(() -> panel.openStationSettings(square));

        StationDoors.current(window);

        assertFalse(StationDoors.on(window, "station.stops"), "a plain sensor has Stop on");
        assertTrue(StationDoors.on(window, "station.runsThrough"), "a plain sensor has Running through off");
    }

    /**
     * The boxes write what the railway's rule reads: both on is a plain station, Running through off stops every train,
     * Stop off is a sensor, and both off takes it out of service.  Every step is back in the box after Save.
     *
     * MUTATION: have `setStopsAndRunsThrough` leave `active` set after turning a square back on, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheBoxesWriteWhatTheRuleReads() throws Exception
    {
        TileKey square = aStation();

        StationSettingsWindow window = StationDoors.open(panel, square);

        StationDoors.press(window, "station.runsThrough");
        StationDoors.current(window);

        assertTrue(session.getStore().isStation(square), "Running through off is no longer a station");
        assertTrue(session.isStopOnly(square), "Running through off lets trains through");

        StationDoors.press(window, "station.stops");
        StationDoors.current(window);

        assertEquals(session.getPointProperty(square, "active"), Boolean.FALSE, "both off is not out of service");

        StationDoors.press(window, "station.stops");
        StationDoors.current(window);

        assertNull(session.getPointProperty(square, "active"), "Stop on leaves the square out of service");
        assertTrue(session.getStore().isStation(square), "Stop on is not a station");
        assertTrue(session.isStopOnly(square), "Stop on alone lets trains through");

        StationDoors.press(window, "station.runsThrough");
        StationDoors.current(window);

        assertFalse(session.isStopOnly(square), "both on still stops every train");

        // Both on again is what an untouched station stores, so an edit undone by hand leaves no mark
        assertTrue(session.snapshotSetup().similar(asOpened), "both on again left a mark in the setup");

        StationDoors.press(window, "station.stops");
        StationDoors.current(window);

        assertFalse(session.getStore().isStation(square), "Stop off is still a station");
        assertFalse(session.isStopOnly(square), "Stop off stops every train");
        assertNull(session.getPointProperty(square, "active"), "Stop off is out of service");
    }

    /**
     * Stop off grays that section, Full Autonomy included, and leaves May Depart and Changing Direction; both off grays
     * those too and says nothing can pass.  A grayed field still shows its value.
     *
     * MUTATION: have `fill` enable `throughSection` on Running Through alone, and this fails with Stop on and Running
     * through off, where they stay live.
     */
    @Test(timeOut = 120000)
    public void testAnOffSectionGraysItsSettings() throws Exception
    {
        TileKey square = aStation();

        Components.onTheEventThread(() -> { session.setPointProperty(square, "maxTrainLength", 30); return null; });

        StationSettingsWindow window = StationDoors.open(panel, square);

        String[] stopParts = {"station.fullAutonomy", "station.maxLength", "station.exitGuardChange",
            "station.entryGuardChange", "station.home"};

        for (String part : stopParts) assertTrue(StationDoors.live(window, part), part + " is gray on a station");

        assertTrue(StationDoors.live(window, "station.turnMay"), "precondition: Changing Direction is gray on a station");

        assertFalse(visible(window, "station.closedLine"), "a station says nothing can pass");

        StationDoors.press(window, "station.runsThrough");
        StationDoors.current(window);

        for (String part : stopParts) assertTrue(StationDoors.live(window, part), part + " grays with Running Through");

        assertTrue(StationDoors.live(window, "station.turnMay"), "Changing Direction grays with Running Through alone");

        StationDoors.press(window, "station.runsThrough");
        StationDoors.press(window, "station.stops");
        StationDoors.current(window);

        for (String part : stopParts) assertFalse(StationDoors.live(window, part), part + " stays live with Stop off");

        assertEquals(StationDoors.text(window, "station.maxLength"), "30", "a grayed field lost its value");

        assertTrue(StationDoors.live(window, "station.turnMay"), "Changing Direction grays with Stop alone");

        StationDoors.press(window, "station.runsThrough");
        StationDoors.current(window);

        assertFalse(StationDoors.live(window, "station.turnMay"), "Changing Direction stays live with both off");

        assertTrue(visible(window, "station.closedLine"), "both off does not say nothing can pass");
    }

    /**
     * A nameless sensor made a station asks its name first; Cancel changes nothing, and the box goes back.
     *
     * MUTATION: have `setUsage` carry on after a Cancel, and this fails.
     */
    @Test(timeOut = 120000)
    public void testANamelessSensorAsksItsNameAndCancelChangesNothing() throws Exception
    {
        TileKey square = aNamelessSensor();

        String before = setup();

        StationSettingsWindow window = StationDoors.open(panel, square);

        assertFalse(StationDoors.on(window, "station.stops"), "precondition: the sensor has Stop on");

        List<String> asked = StationDoors.press(window, "station.stops", message -> Answering.pressAt(1));

        assertEquals(asked.size(), 1, "making it a station did not ask its name: " + asked);

        StationDoors.current(window);

        assertFalse(StationDoors.on(window, "station.stops"), "after Cancel the box stays on");

        assertEquals(setup(), before, "Cancel wrote something");
    }

    // ---------------------------------------------------------------------------------------------------------- fields

    /**
     * Enter applies a field, Escape puts back what it held, and what Enter applied is what Save writes.
     *
     * MUTATION: have the field's Escape action skip `setText(stored)`, or drop the root pane's, and this fails.
     */
    @Test(timeOut = 120000)
    public void testEnterAppliesAFieldAndEscapePutsItBack() throws Exception
    {
        TileKey square = aStation();

        StationSettingsWindow window = StationDoors.open(panel, square);

        StationDoors.typeAndEnter(window, "station.maxLength", "42");

        assertEquals(numberOf(session.getPointProperty(square, "maxTrainLength")), 42.0, "Enter did not apply the length");

        JTextField field = StationDoors.part(window, "station.maxLength", JTextField.class);

        Components.onTheEventThread(() ->
        {
            field.setText("99");

            field.getActionForKeyStroke(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0))
                .actionPerformed(new java.awt.event.ActionEvent(field, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));

            return null;
        });

        assertEquals(StationDoors.text(window, "station.maxLength"), "42", "Escape did not put the field back");

        assertEquals(numberOf(session.getPointProperty(square, "maxTrainLength")), 42.0, "Escape changed the setup");

        // Escape anywhere else closes the window
        Components.onTheEventThread(() ->
        {
            window.getRootPane().getActionForKeyStroke(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0))
                .actionPerformed(new java.awt.event.ActionEvent(window, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));

            return null;
        });

        assertFalse(window.isShowing(), "Escape did not close the window");
    }

    /**
     * A refused value still showing does not follow the window to another square (OB-415): the new square's own value
     * shows, the refusal goes, and closing writes nothing to either square.
     *
     * MUTATION: have `StationSettingsWindow.aimAt` keep a refusal when the square changes, and this fails.
     */
    @Test(timeOut = 120000)
    public void testARefusedValueStaysWithItsSquare() throws Exception
    {
        TileKey first = aStation();
        TileKey second = aSecondStation(first);

        Components.onTheEventThread(() -> { session.setPointProperty(second, "maxTrainLength", 30); return null; });

        Object firstHad = Components.onTheEventThread(() -> session.getPointProperty(first, "maxTrainLength"));

        StationSettingsWindow window = StationDoors.open(panel, first);

        StationDoors.typeAndEnter(window, "station.maxLength", "-5");

        assertTrue(visible(window, "station.maxLengthProblem"), "precondition: a negative length was not refused");

        Components.onTheEventThread(() -> panel.openStationSettings(second));

        StationDoors.current(window);

        assertEquals(StationDoors.text(window, "station.maxLength"), "30", "the other square shows the refused value");

        assertFalse(visible(window, "station.maxLengthProblem"), "the other square shows the first one's refusal");

        StationDoors.close(window);

        assertEquals(numberOf(session.getPointProperty(second, "maxTrainLength")), 30.0, "closing changed the other square");

        assertEquals(Components.onTheEventThread(() -> session.getPointProperty(first, "maxTrainLength")), firstHad,
            "closing changed the square the value was refused for");
    }

    /**
     * Leaving a field applies it, as Close does.  Nothing is applied per keystroke, as each change rebuilds the railway.
     *
     * MUTATION: drop the focus listener in `commitsOnLeaving`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testLeavingAFieldAppliesIt() throws Exception
    {
        TileKey square = aStation();

        StationSettingsWindow window = StationDoors.open(panel, square);

        JTextField field = StationDoors.part(window, "station.maxLength", JTextField.class);

        Object before = session.getPointProperty(square, "maxTrainLength");

        Components.onTheEventThread(() -> { field.setText("37"); return null; });

        assertEquals(session.getPointProperty(square, "maxTrainLength"), before, "typing applied the length before leaving");

        Components.onTheEventThread(() ->
        {
            for (java.awt.event.FocusListener each : field.getFocusListeners())
            {
                each.focusLost(new java.awt.event.FocusEvent(field, java.awt.event.FocusEvent.FOCUS_LOST));
            }

            return null;
        });

        Components.settle();

        assertEquals(numberOf(session.getPointProperty(square, "maxTrainLength")), 37.0, "leaving did not apply the length");

        StationDoors.part(window, "station.maxLength", JTextField.class);

        Components.onTheEventThread(() -> { field.setText("38"); return null; });

        StationDoors.close(window);

        assertEquals(numberOf(session.getPointProperty(square, "maxTrainLength")), 38.0, "Close did not apply the length");
    }

    /**
     * A name another station has is refused beside the field and says it is taken (OB-376), with what was typed still
     * there to fix; fixing it clears the reason.
     *
     * MUTATION: have `commit` put the stored value back after a refusal, or ask `AutonomySession.whyNotAPointName` instead
     * of `whyNotNamed`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testARefusedNameKeepsWhatWasTyped() throws Exception
    {
        TileKey square = aStation();

        String mine = session.getStore().getPointName(square);

        String other = null;

        for (TileKey each : session.getStore().getNamedTiles())
        {
            if (!each.equals(square)) other = session.getStore().getPointName(each);
        }

        assertNotNull(other, "precondition: no other named square");

        StationSettingsWindow window = StationDoors.open(panel, square);

        StationDoors.typeAndEnter(window, "station.name", other);

        assertEquals(session.getStore().getPointName(square), mine, "the taken name was written");

        assertEquals(StationDoors.text(window, "station.name"), other, "the refusal took back what was typed");

        assertTrue(visible(window, "station.nameProblem"), "the refusal gives no reason");

        String reason = Components.onTheEventThread(() ->
            StationDoors.part(window, "station.nameProblem", javax.swing.JLabel.class).getText());

        assertTrue(reason.contains(I18n.f("autosetup.ui.errorPointNameTaken", other.trim())), "the reason does not say the"
            + " name is taken: " + reason);

        StationDoors.typeAndEnter(window, "station.name", "A Name Nobody Has");

        assertEquals(session.getStore().getPointName(square), "A Name Nobody Has", "the new name was not written");

        assertFalse(visible(window, "station.nameProblem"), "the reason stayed after the fix");
    }

    // ------------------------------------------------------------------------------------------------------ Which Trains

    /**
     * Whitelist with nothing on the list opens the list; Cancel leaves All and no list; a pick is the list.
     *
     * MUTATION: have `pressWhich` set the mode with no list asked for, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAListPickedWithNothingOnItOpensTheList() throws Exception
    {
        TileKey square = aStation();

        Components.onTheEventThread(() -> { session.setPointProperty(square, "home", null); return null; });

        String train = model.getLocList().get(0);

        StationSettingsWindow window = StationDoors.open(panel, square);

        Components.onTheEventThread(() -> panel.openStationSettings(square));

        assertEquals(session.getWhichTrains(square), WhichTrains.ALL, "precondition: the square has a list");

        List<String> asked = StationDoors.press(window, "station.whichWhite", message -> Answering.pressAt(1));

        assertEquals(asked.size(), 1, "Whitelist on an empty list did not open the list: " + asked);

        assertEquals(session.getWhichTrains(square), WhichTrains.ALL, "Cancel left a list in force");

        assertTrue(StationDoors.on(window, "station.whichAll"), "after Cancel the radio is not back on All");

        StationDoors.press(window, "station.whichWhite", message -> StationDoors.choosing(train));

        assertEquals(session.getWhichTrains(square), WhichTrains.WHITELIST, "the pick did not put the list in force");

        assertEquals(session.getTrainList(square), Arrays.asList(train), "the pick is not the list");
    }

    /**
     * The two share one list, and All keeps it: Blacklist after All asks nothing and finds the same names.
     *
     * MUTATION: have `setWhichTrains(ALL)` clear the list, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAllKeepsTheListAndTheTwoShareIt() throws Exception
    {
        TileKey square = aStation();

        String train = model.getLocList().get(0);

        Components.onTheEventThread(() ->
        {
            session.setPointProperty(square, "home", null);
            session.setWhichTrains(square, WhichTrains.WHITELIST, Arrays.asList(train));

            return null;
        });

        StationSettingsWindow window = StationDoors.open(panel, square);

        assertTrue(StationDoors.live(window, "station.whichChoose"), "the list cannot be changed under Whitelist");

        StationDoors.press(window, "station.whichAll", message -> { throw new AssertionError("All asked: " + message); });

        assertEquals(session.getWhichTrains(square), WhichTrains.ALL, "All did not take");
        assertEquals(session.getTrainList(square), Arrays.asList(train), "All threw the list away");
        assertFalse(StationDoors.live(window, "station.whichChoose"), "the list can be changed under All");

        StationDoors.press(window, "station.whichBlack", message -> { throw new AssertionError("Blacklist asked: " + message); });

        assertEquals(session.getWhichTrains(square), WhichTrains.BLACKLIST, "Blacklist did not take");
        assertEquals(session.getTrainList(square), Arrays.asList(train), "the blacklist is not the same list");
    }

    /**
     * Turning on a kept list that shuts out the station's home locomotive warns, as choosing the list does (OB-022): No
     * leaves All in force.
     *
     * MUTATION: drop the `homeMayBeBroken` call from `AutonomyEditorPanel.setWhichTrains`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAModeThatShutsOutTheHomeWarns() throws Exception
    {
        TileKey square = aStation();

        String train = model.getLocList().get(0);

        Components.onTheEventThread(() ->
        {
            session.setHome(square, train);
            session.setWhichTrains(square, WhichTrains.ALL, Arrays.asList(train));

            return null;
        });

        StationSettingsWindow window = StationDoors.open(panel, square);

        String warned = I18n.f("autolayout.ui.confirmExcludingHome", train, session.describeTile(square));

        List<String> asked = StationDoors.press(window, "station.whichBlack",
            message -> Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[1])));

        assertEquals(asked, Arrays.asList(warned), "Blacklist over a kept list naming the home did not warn");

        assertEquals(session.getWhichTrains(square), WhichTrains.ALL, "No put the list in force");

        assertTrue(StationDoors.on(window, "station.whichAll"), "after No the radio is not back on All");
    }

    /**
     * What is being typed applies when the window loses the keyboard, so the editor's Save has it; and the editor's Cancel
     * throws it away with the rest: nothing is committed over what Cancel put back.
     *
     * MUTATION: have `StationWindows.closeAll` call `closeWindow`, which commits, and the typed value survives Cancel and this
     * fails.  Drop the window focus listener, and the first half fails.
     */
    @Test(timeOut = 120000)
    public void testWhatIsTypedAppliesOnLeavingTheWindowAndCancelThrowsItAway() throws Exception
    {
        TileKey square = aStation();

        StationSettingsWindow window = StationDoors.open(panel, square);

        JTextField field = StationDoors.part(window, "station.maxLength", JTextField.class);

        Components.onTheEventThread(() ->
        {
            field.setText("61");

            for (java.awt.event.WindowFocusListener each : window.getWindowFocusListeners())
            {
                each.windowLostFocus(new java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_LOST_FOCUS));
            }

            return null;
        });

        assertEquals(numberOf(session.getPointProperty(square, "maxTrainLength")), 61.0, "losing the keyboard did not apply it");

        Components.onTheEventThread(() -> { field.setText("62"); return null; });

        AbstractButton cancel = Components.withText(editor, I18n.t("ui.cancel"));

        assertNotNull(cancel, "precondition: the editor has no Cancel");

        StationDoors.pressAndAnswer(cancel, message -> Answering.pressAt(0));

        editor = null;

        Components.settle();

        assertTrue(session.snapshotSetup().similar(asOpened), "something typed was committed over what Cancel put back");

        assertNull(StationDoors.showing(StationSettingsWindow.class), "the window outlived the editor it hung off");
    }

    /**
     * The window's questions hang off the window that asked (OB-324), not the editor behind it.
     *
     * MUTATION: have `pressedFrom` skip setting `askingWindow`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAQuestionHangsOffTheWindow() throws Exception
    {
        TileKey square = aStation();

        Components.onTheEventThread(() -> { session.setPointProperty(square, "home", null); return null; });

        StationSettingsWindow window = StationDoors.open(panel, square);

        Answering.Question[] question = new Answering.Question[1];

        try (Answering answering = Answering.start(message -> Answering.pressAt(1)))
        {
            Components.press(StationDoors.part(window, "station.whichWhite", AbstractButton.class));

            assertTrue(answering.awaitAsked(1, 15000), "Whitelist asked nothing");

            question[0] = answering.questions().get(0);
        }

        assertSame(question[0].owner, window, "the question hangs off another window");
    }

    // ----------------------------------------------------------------------------------------------------------- presets

    /**
     * Each preset leaves the setup a user setting the four by hand leaves, from a station and from a closed square.
     *
     * MUTATION: have Parking leave Full Autonomy as it was, or have `applyPreset` skip `setUsage`, and this fails.
     */
    @Test(timeOut = 240000)
    public void testAPresetGivesWhatSettingItByHandGives() throws Exception
    {
        TileKey square = aStation();

        for (boolean closed : new boolean[] {false, true})
        {
            for (StationPreset preset : StationPreset.values())
            {
                startFrom(square, closed);

                StationSettingsWindow window = StationDoors.open(panel, square);

                StationDoors.press(window, "station.preset." + preset.name());
                StationDoors.current(window);

                org.json.JSONObject byButton = Components.onTheEventThread(() -> session.snapshotSetup());

                startFrom(square, closed);

                StationDoors.open(panel, square);

                setByHand(window, preset);
                StationDoors.current(window);

                org.json.JSONObject byHand = Components.onTheEventThread(() -> session.snapshotSetup());

                assertTrue(byButton.similar(byHand), preset + (closed ? " from a closed square" : "")
                    + " differs from setting it by hand");

                assertEquals(window.litPresetForTest(), preset, preset + " does not light after it is set by hand");
            }
        }
    }

    /**
     * A preset changes its four settings and nothing else.
     *
     * MUTATION: have `applyPreset` clear the arrival sides, or reset the priority, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAPresetLeavesEveryOtherSettingAlone() throws Exception
    {
        TileKey square = aStation();

        Components.onTheEventThread(() ->
        {
            session.setPointProperty(square, "maxTrainLength", 30);
            session.setPointProperty(square, "priority", 5);
            session.setPointProperty(square, "speedMultiplier", 1.5);

            return null;
        });

        StationSettingsWindow window = StationDoors.open(panel, square);

        Map<String, String> others = otherSettings(square);

        for (StationPreset preset : StationPreset.values())
        {
            StationDoors.press(window, "station.preset." + preset.name());

            assertEquals(otherSettings(square), others, preset + " changed another setting");
        }
    }

    /**
     * The editor's Cancel undoes a preset, as it undoes every other edit.
     *
     * MUTATION: have a preset write around the session so the opening snapshot does not see it, and this fails.
     */
    @Test(timeOut = 120000)
    public void testCancelUndoesAPreset() throws Exception
    {
        TileKey square = aStation();

        StationSettingsWindow window = StationDoors.open(panel, square);

        StationDoors.press(window, "station.preset.TERMINUS");
        StationDoors.current(window);

        assertFalse(session.snapshotSetup().similar(asOpened), "precondition: the preset changed nothing");

        AbstractButton cancel = Components.withText(editor, I18n.t("ui.cancel"));

        assertNotNull(cancel, "precondition: the editor has no Cancel");

        StationDoors.pressAndAnswer(cancel, message -> Answering.pressAt(0));

        editor = null;

        Components.settle();

        assertTrue(session.snapshotSetup().similar(asOpened), "Cancel did not put the setup back");
    }

    /**
     * A button lights whenever the four match its row, however they got there; none lights when nothing matches.
     *
     * MUTATION: have `fill` light the last button pressed rather than asking `StationPreset.matching`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testAButtonLightsHoweverTheSettingsGotThere() throws Exception
    {
        TileKey square = aStation();

        Components.onTheEventThread(() ->
        {
            session.setAutoDestination(square, true);
            session.setStopsAndRunsThrough(square, true, true);

            return null;
        });

        StationSettingsWindow window = StationDoors.open(panel, square);

        // A setup written by an older build, or by any other door
        for (StationPreset preset : StationPreset.values())
        {
            setByHand(window, preset);
            StationDoors.current(window);

            assertEquals(window.litPresetForTest(), preset, "the settings of " + preset + " do not light it");
        }

        // No row has Must with Full Autonomy off
        StationDoors.press(window, "station.preset.PARKING");
        StationDoors.press(window, "station.turnMust");
        StationDoors.current(window);

        assertNull(window.litPresetForTest(), "a button lit though nothing matches");

        StationDoors.press(window, "station.preset.STATION");
        StationDoors.press(window, "station.stops");
        StationDoors.current(window);

        assertNull(window.litPresetForTest(), "a button lit with Stop off");
    }

    // ------------------------------------------------------------------------------------------------------------ matrix

    /**
     * The matrix follows the settings: untick Full Autonomy and automatic runs no longer choose the square.
     *
     * MUTATION: have `changed` skip the reload, and the matrix stands still and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheMatrixFollowsAChange() throws Exception
    {
        TileKey square = aStation();

        Components.onTheEventThread(() ->
        {
            session.setAutoDestination(square, true);

            return null;
        });

        StationSettingsWindow window = StationDoors.open(panel, square);

        Map<String, StandingRules.Answer> before = window.drawnForTest(StandingRules.Row.SENT_HERE, "auto");

        StationDoors.press(window, "station.fullAutonomy");
        StationDoors.current(window);

        Map<String, StandingRules.Answer> after = window.drawnForTest(StandingRules.Row.SENT_HERE, "auto");

        assertFalse(after.equals(before), "the matrix said the same after Full Autonomy changed: " + before);

        StationDoors.press(window, "station.runsThrough");
        StationDoors.press(window, "station.stops");
        StationDoors.current(window);

        // Closed, nothing comes here or through; a train already here may still be sent away by hand
        for (StandingRules.Row row : new StandingRules.Row[] {StandingRules.Row.SENT_HERE, StandingRules.Row.TURNED_ROUND})
        {
            for (String tier : new String[] {"auto", "hand"})
            {
                assertFalse(window.drawnForTest(row, tier).containsValue(StandingRules.Answer.YES), row + " " + tier
                    + " still says yes with both off");
            }
        }

        for (String tier : new String[] {"auto", "hand"})
        {
            assertFalse(window.drawnForTest(StandingRules.Row.SENT_THROUGH, tier).containsValue(StandingRules.Answer.YES),
                "both off still shows a way through");
        }
    }

    /**
     * A terminus on one side only is built as a single copy with no facing, and a train standing on it is still sent away:
     * the way out is real, so Sent From Here says yes.
     *
     * MUTATION: have `LayoutStandingRules.leaving` skip a copy with no facing, and it says the way out is closed and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testATerminusStillSendsItsTrainsAway() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(panel, support.AskingWindows.stationNamed(session, "BottomMainB"));

        StationDoors.press(window, "station.preset.TERMINUS");
        StationDoors.current(window);

        for (String tier : new String[] {"auto", "hand"})
        {
            assertTrue(window.drawnForTest(StandingRules.Row.SENT_FROM, tier).containsValue(StandingRules.Answer.YES),
                "a terminus says no train can be sent from it, " + tier + ": "
                + window.drawnForTest(StandingRules.Row.SENT_FROM, tier));
        }
    }

    /**
     * Must with two sides open stops autonomy, and the matrix says so instead of a table of answers; barring a side
     * brings it back, and the setup was never refused.
     *
     * MUTATION: have `askTheRailway` ignore `whyTheSetupStopsThisSquare`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testMustWithTwoSidesOpenSaysWhy() throws Exception
    {
        TileKey square = aStationWithTwoWaysIn();

        StationSettingsWindow window = StationDoors.open(panel, square);

        StationDoors.press(window, "station.turnMust");
        StationDoors.current(window);

        assertTrue(session.isMustTurnAround(square), "Must was refused");

        assertTrue(visible(window, "station.matrixProblem"), "the matrix does not say why");

        assertTrue(StationDoors.text(window, "station.matrixProblem").contains(
            I18n.t("autosetup.ui.stationMatrixMustTwoSides").substring(0, 10)), "the matrix says something else");

        assertEveryCellSaysNo(window);
    }

    // -------------------------------------------------------------------------------------------------- following squares

    /**
     * Right-clicking another sensor moves the window there, with no second window; a square that is not a sensor leaves it.
     *
     * MUTATION: have `rightClicked` skip the `offersStationSettings` check, and this fails on plain track.
     */
    @Test(timeOut = 120000)
    public void testARightClickSwitchesTheWindow() throws Exception
    {
        TileKey first = aStation();
        TileKey second = aSecondStation(first);

        StationSettingsWindow window = StationDoors.open(panel, first);

        Components.onTheEventThread(() -> { panel.stationWindowsFollow(second); return null; });

        assertEquals(window.squareForTest(), second, "the right-click did not move the window");

        assertSame(Components.onTheEventThread(() -> panel.stationSettingsWindow()), window, "a second window opened");

        Components.onTheEventThread(() -> { panel.stationWindowsFollow(new TileKey(PAGE, 9999, 9999)); return null; });

        assertEquals(window.squareForTest(), second, "a square with no settings moved the window");
    }

    /**
     * The editor outlines the square and its guard signals while the window is open, and not after.
     *
     * MUTATION: have `closed` skip `outline()`, and the mark stays and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorOutlinesTheSquare() throws Exception
    {
        TileKey square = aStation();

        assertFalse(Components.onTheEventThread(() -> panel.isOutlined(square)), "precondition: outlined with no window");

        StationSettingsWindow window = StationDoors.open(panel, square);

        assertTrue(Components.onTheEventThread(() -> panel.isOutlined(square)), "the open window outlines nothing");

        StationDoors.close(window);

        assertFalse(Components.onTheEventThread(() -> panel.isOutlined(square)), "the closed window left its outline");
    }

    /**
     * Double-clicking a finding opens Station Settings on its square, where a single click stays as it was.
     *
     * MUTATION: drop the click count check from the findings' listener, and a single click opens it.  Read the list's
     * selection in `openStationSettingsForTheFinding`, which going to the finding has already cleared, and neither door
     * opens anything, and this fails.
     */
    @Test(timeOut = 120000)
    public void testDoubleClickingAFindingOpensItsSquare() throws Exception
    {
        TileKey square = aStationWithTwoWaysIn();

        Components.onTheEventThread(() ->
        {
            session.setPointProperty(square, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);
            panel.refresh();

            return null;
        });

        Components.settle();

        @SuppressWarnings("unchecked")
        JList<String> list = (JList<String>) Reflect.get(AutonomyEditorPanel.class, panel, "findings");

        java.lang.reflect.Field tiles = Reflect.field(AutonomyEditorPanel.class, "findingTiles");

        int row = Components.onTheEventThread(() -> ((List<?>) tiles.get(panel)).indexOf(square));

        assertTrue(row >= 0, "precondition: no finding about " + square);

        Components.onTheEventThread(() ->
        {
            list.setSelectedIndex(row);

            clicked(list, 1);

            return null;
        });

        Components.settle();

        assertNull(Components.onTheEventThread(() -> panel.stationSettingsWindow()), "a single click opened the window");

        Components.onTheEventThread(() ->
        {
            // The second press selects the row again, before the click
            list.setSelectedIndex(row);

            clicked(list, 2);

            return null;
        });

        StationSettingsWindow window = Components.onTheEventThread(() -> panel.stationSettingsWindow());

        assertNotNull(window, "a double click did not open the window");

        assertEquals(window.squareForTest(), square, "the window opened on another square");

        // Control+O with the pointer off the diagram: the same door, after the list has refreshed
        Components.onTheEventThread(() -> { panel.closeStationWindows(); panel.refresh(); return null; });

        StationSettingsWindow again = Components.onTheEventThread(() -> panel.openStationSettingsForTheFinding());

        assertNotNull(again, "the key opened nothing on the finding chosen");

        assertEquals(again.squareForTest(), square, "the key opened another square's window");
    }

    // -------------------------------------------------------------------------------------------------------------- look

    /**
     * The form reads Changing direction first, over both sections, which are named "Trains can stop here" and "Trains can
     * pass through"; the table says "Turned around here"; the Home locomotive row has its (i), saying what a home is for.
     *
     * MUTATION: put the Changing direction row back under "Trains can pass through", or the old section names or "Turned
     * round here" in the bundle, or drop the home row's mark, and this fails.
     */
    @Test(timeOut = 120000)
    public void testChangingDirectionHeadsBothSectionsAndTheWordsAreNew() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(panel, aStation());

        StationDoors.current(window);

        javax.swing.JCheckBox stops = StationDoors.part(window, "station.stops", javax.swing.JCheckBox.class);
        javax.swing.JCheckBox through = StationDoors.part(window, "station.runsThrough", javax.swing.JCheckBox.class);

        assertEquals(stops.getText(), "Trains can stop here");
        assertEquals(through.getText(), "Trains can pass through");

        javax.swing.JPanel form = StationDoors.part(window, "station.form", javax.swing.JPanel.class);

        int turning = cornerIn(form, labelSaying(window, I18n.t("autosetup.ui.stationTurning"))).y;

        assertTrue(turning < cornerIn(form, stops).y && cornerIn(form, stops).y < cornerIn(form, through).y,
            "Changing direction is not above both sections");

        assertNotNull(labelSaying(window, "Turned around here"), "the table does not say Turned around here");

        assertTrue(Components.onTheEventThread(() -> window.flyoverForTest("home")).contains("Return Home sends it back here"),
            "the Home locomotive row has no (i) saying what a home is for");

        StationDoors.close(window);
    }

    /**
     * May depart sits under Changing direction, above both sections, as it belongs to neither box (Adam, FR-182); it
     * still grays only when both boxes are off.
     *
     * MUTATION: put the May depart row back under "Trains can pass through", and this fails.
     */
    @Test(timeOut = 120000)
    public void testMayDepartHeadsBothSectionsUnderChangingDirection() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(panel, aStation());

        StationDoors.current(window);

        javax.swing.JPanel form = StationDoors.part(window, "station.form", javax.swing.JPanel.class);

        int turning = cornerIn(form, labelSaying(window, I18n.t("autosetup.ui.stationTurning"))).y;
        int depart = cornerIn(form, labelSaying(window, I18n.t("autosetup.ui.stationDepart"))).y;
        int stops = cornerIn(form, StationDoors.part(window, "station.stops", javax.swing.JCheckBox.class)).y;

        assertTrue(turning < depart && depart < stops, "May depart (at " + depart + ") is not between Changing direction (at "
            + turning + ") and Trains can stop here (at " + stops + ")");

        StationDoors.press(window, "station.stops");
        StationDoors.current(window);

        assertTrue(StationDoors.live(window, "station.depart.0"), "May depart grays with Trains can stop here alone off");

        StationDoors.press(window, "station.runsThrough");
        StationDoors.current(window);

        assertFalse(StationDoors.live(window, "station.depart.0"), "May depart is live with both boxes off");

        StationDoors.close(window);
    }

    /**
     * May arrive from is never blank (Adam, FR-182): on a square no track leads a train onto, as some on his "2 - Bottom"
     * page are (a closed one, and pass-through ones fed only by track leading away), it says "(none)", and its (i) says
     * there is no side to choose rather than describing a single side that is not shown.
     *
     * MUTATION: drop the "(none)" label from `fillArrivals`, or the empty case from `StationFlyovers.arrivals`, and this
     * fails.
     */
    @Test(timeOut = 180000)
    public void testMayArriveFromIsNeverBlank() throws Exception
    {
        LayoutEditor bottom = frozen.editorOn("2 - Bottom");

        try
        {
            AutonomyEditorPanel lower = Components.onTheEventThread(() -> bottom.getAutonomyPanel());

            List<TileKey> noWayIn = Components.onTheEventThread(() ->
            {
                List<TileKey> out = new java.util.ArrayList<>();

                for (TileKey each : session.getGraph().getTiles().keySet())
                {
                    if ("2 - Bottom".equals(each.getPage()) && lower.offersStationSettings(each)
                        && session.arrivalSides(each).isEmpty()) out.add(each);
                }

                return out;
            });

            assertFalse(noWayIn.isEmpty(), "precondition: every square on 2 - Bottom has a way in");

            for (TileKey square : noWayIn)
            {
                StationSettingsWindow window = StationDoors.open(lower, square);

                StationDoors.current(window);

                javax.swing.JPanel arrivals = StationDoors.part(window, "station.arrivals", javax.swing.JPanel.class);

                List<String> shown = Components.onTheEventThread(() ->
                {
                    List<String> out = new java.util.ArrayList<>();

                    for (java.awt.Component each : arrivals.getComponents())
                    {
                        if (each instanceof javax.swing.JLabel) out.add(((javax.swing.JLabel) each).getText());
                        if (each instanceof AbstractButton) out.add(((AbstractButton) each).getText());
                    }

                    return out;
                });

                assertEquals(shown, Arrays.asList(I18n.t("autosetup.ui.labelNone")), square + ": May arrive from shows " + shown);

                String flyover = Components.onTheEventThread(() -> window.flyoverForTest("arrivals"));

                assertTrue(flyover.contains(I18n.t("autosetup.ui.flyArriveNoneNow")), square + ": the (i) does not say no track"
                    + " leads in: " + flyover);

                StationDoors.close(window);
            }
        }
        finally
        {
            support.FrozenEditor.dispose(bottom);
        }
    }

    /**
     * The row choosing All, Blacklist or Whitelist under Advanced is labeled "Allowed trains" (Adam, FR-182).
     *
     * MUTATION: put "Which trains" back in the bundle, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheTrainListRowSaysAllowedTrains() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(panel, aStation());

        Components.onTheEventThread(() -> { window.openAdvancedForTest(); return null; });

        StationDoors.current(window);

        assertTrue(Components.onTheEventThread(labelSaying(window, "Allowed trains")::isShowing), "the row is not showing");
    }

    /**
     * The picture's white area keeps 3 px above and below its squares, 5 fewer than it had, for room for the Presets in
     * the right column (Adam, FR-182); it is not squeezed, and every mark and square still lies inside it.
     *
     * MUTATION: put the block's 8 px padding back, and this fails.
     */
    @Test(timeOut = 120000)
    public void testThePictureIsPaddedTightlyAndNothingIsCut() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(panel, aStation());

        StationDoors.current(window);

        JComponent block = StationDoors.part(window, "station.picture.ways", JComponent.class);

        Components.onTheEventThread(() ->
        {
            Map<String, java.awt.Shape> marks = window.pictureMarksForTest();

            int cell = ((java.awt.Rectangle) marks.get("SQUARE")).height;
            int pad = (block.getPreferredSize().height - 3 * cell - 2) / 2;

            assertEquals(pad, 3, "the white area's padding above and below the squares");
            assertTrue(block.getHeight() >= block.getPreferredSize().height, "the picture is squeezed");

            java.awt.Rectangle inside = new java.awt.Rectangle(1, 1, block.getWidth() - 2, block.getHeight() - 2);

            for (Map.Entry<String, java.awt.Shape> each : marks.entrySet())
            {
                assertTrue(inside.contains(each.getValue().getBounds2D()), each.getKey() + " at " + each.getValue().getBounds2D()
                    + " is cut by the picture's edge " + inside);
            }

            return null;
        });
    }

    /**
     * Blue is for the section headings alone (Adam: "it looks very blue"), Presets among them; every other label is black
     * Semibold 13 (docs/UI-standards.md), and a label in an off section still grays.
     *
     * MUTATION: build a row's label with `UiStyle.heading`, or a section heading (Presets) with `UiStyle.label`, and this
     * fails naming it.
     */
    @Test(timeOut = 120000)
    public void testOnlyTheSectionHeadingsAreBlue() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(panel, aStation());

        Components.onTheEventThread(() -> { window.openAdvancedForTest(); return null; });

        StationDoors.current(window);

        java.util.Set<String> blue = new java.util.TreeSet<>();

        for (javax.swing.JLabel each : Components.all(window, javax.swing.JLabel.class, null))
        {
            if (UiStyle.BLUE.equals(each.getForeground())) blue.add(each.getText());
        }

        for (AbstractButton each : Components.all(window, AbstractButton.class, null))
        {
            if (UiStyle.BLUE.equals(each.getForeground())) blue.add(each.getText());
        }

        assertEquals(blue, new java.util.TreeSet<>(Arrays.asList(I18n.t("autosetup.ui.stationSectionStop"),
            I18n.t("autosetup.ui.stationSectionThrough"), I18n.t("autosetup.ui.stationSectionAdvanced"),
            I18n.t("autosetup.ui.stationMatrixHeading"), I18n.t("autosetup.ui.stationPictureHeading"),
            I18n.t("autosetup.ui.stationPresets"))),
            "what is blue is not the section headings");

        for (String key : new String[] {"autosetup.ui.stationMaxLength", "autosetup.ui.menuAutoDestination",
            "autosetup.ui.stationHome", "autosetup.ui.stationMatrixTrainsCanBe", "autosetup.ui.stationMatrixAutomatically",
            "autosetup.ui.stationMatrixManually", "autosetup.ui.stationRowSentHere", "autosetup.ui.stationRowTurned"})
        {
            javax.swing.JLabel label = labelSaying(window, I18n.t(key));

            assertEquals(label.getForeground(), java.awt.Color.BLACK, key + " is not black");
            assertEquals(label.getFont(), UiStyle.LABEL_FONT, key + " is not Semibold 13");
        }

        assertEquals(UiStyle.heading("any").getForeground(), UiStyle.BLUE, "UiStyle.heading changed, for every window using it");
        assertEquals(UiStyle.label("any").getForeground(), java.awt.Color.BLACK, "UiStyle.label changed, for every window using it");

        StationDoors.press(window, "station.stops");
        StationDoors.current(window);

        assertFalse(labelSaying(window, I18n.t("autosetup.ui.stationMaxLength")).isEnabled(),
            "a row's label does not gray with its section");
    }

    /**
     * The presets look like pills, not text: drawn as pills, Bold 12, at the top of the right column under a "Presets"
     * section heading drawn as "What's allowed here" is, which they start under, above that heading; and the lit one can be
     * told from the others.  The heading and the right column are Adam's (FR-182), for room beside the picture.
     *
     * MUTATION: put `JButton.buttonType` `toolBarButton` back on them, make them plain `JToggleButton`s, or a gap before
     * the first, or put them back in the form beside a black label, and this fails.
     */
    @Test(timeOut = 120000)
    public void testThePresetsLookLikeButtons() throws Exception
    {
        TileKey square = aStation();

        StationSettingsWindow window = StationDoors.open(panel, square);

        Components.settle();

        Components.onTheEventThread(() ->
        {
            for (StationPreset preset : StationPreset.values())
            {
                javax.swing.JToggleButton button = StationDoors.part(window, "station.preset." + preset.name(),
                    javax.swing.JToggleButton.class);

                assertNull(button.getClientProperty("JButton.buttonType"), preset + " is a flat toolbar button");
                assertTrue(button instanceof org.traincontrol.gui.widgets.PillButton, preset + " is not a pill");
                assertEquals(button.getFont(), new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12), preset + " is not Bold 12");
            }

            java.awt.Container content = window.getContentPane();

            javax.swing.JPanel form = StationDoors.part(window, "station.form", javax.swing.JPanel.class);

            javax.swing.JToggleButton first = StationDoors.part(window, "station.preset.STATION", javax.swing.JToggleButton.class);
            javax.swing.JLabel presets = labelSaying(window, I18n.t("autosetup.ui.stationPresets"));
            javax.swing.JLabel allowed = labelSaying(window, I18n.t("autosetup.ui.stationMatrixHeading"));

            assertEquals(presets.getForeground(), allowed.getForeground(), "the Presets heading is not drawn as What's allowed here is");
            assertEquals(presets.getFont(), allowed.getFont(), "the Presets heading is not in What's allowed here's font");

            assertFalse(javax.swing.SwingUtilities.isDescendingFrom(first, form), "the presets are still in the form");
            assertTrue(cornerIn(content, presets).x >= cornerIn(content, form).x + form.getWidth(),
                "the Presets heading is not in the right column");

            assertEquals(cornerIn(content, first).x, cornerIn(content, presets).x, "the presets do not start under their heading");
            assertTrue(cornerIn(content, presets).y + presets.getHeight() <= cornerIn(content, first).y,
                "the presets are not under their heading");
            assertTrue(cornerIn(content, first).y + first.getHeight() < cornerIn(content, allowed).y,
                "the presets are not above What's allowed here");

            javax.swing.JToggleButton last = StationDoors.part(window, "station.preset.REVERSING_POINT",
                javax.swing.JToggleButton.class);

            assertTrue(javax.swing.SwingUtilities.convertRectangle(last.getParent(), last.getBounds(), content).getMaxX()
                <= content.getWidth(), "the last button runs off the window");

            return null;
        });

        javax.swing.JToggleButton station = StationDoors.part(window, "station.preset.STATION", javax.swing.JToggleButton.class);

        Components.onTheEventThread(() ->
        {
            boolean was = station.isSelected();

            station.setSelected(false);

            java.awt.image.BufferedImage off = drawnOn(station);

            station.setSelected(true);

            java.awt.image.BufferedImage on = drawnOn(station);

            station.setSelected(was);

            assertTrue(difference(off, on, 0) > 0, "the lit preset cannot be told from the others");

            return null;
        });
    }

    /**
     * Advanced opened differs from Advanced closed by its arrow alone, with no band behind it, hovered or pressed.
     *
     * MUTATION: make it a selected toggle, or leave its content area filled so hover draws one, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAnOpenAdvancedHeadingDiffersOnlyInItsArrow() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(panel, aStation());

        AbstractButton heading = StationDoors.part(window, "station.advanced", AbstractButton.class);

        Components.onTheEventThread(() ->
        {
            int arrow = heading.getIcon().getIconWidth() + heading.getIconTextGap();

            java.awt.image.BufferedImage closed = drawnOn(heading);

            window.openAdvancedForTest();

            window.validate();

            java.awt.image.BufferedImage open = drawnOn(heading);

            assertEquals(difference(closed, open, arrow + heading.getInsets().left), 0, "beyond its arrow, the open heading"
                + " is drawn differently from the closed one");

            heading.getModel().setRollover(true);

            java.awt.image.BufferedImage hovered = drawnOn(heading);

            heading.getModel().setRollover(false);
            heading.getModel().setArmed(true);
            heading.getModel().setPressed(true);

            java.awt.image.BufferedImage pressed = drawnOn(heading);

            heading.getModel().setPressed(false);
            heading.getModel().setArmed(false);

            assertEquals(difference(open, hovered, 0), 0, "hovering draws a band behind the heading");
            assertEquals(difference(open, pressed, 0), 0, "pressing draws a band behind the heading");

            return null;
        });
    }

    /** Where a part's top left corner is, in the form's pixels. */
    private static java.awt.Point cornerIn(java.awt.Component form, java.awt.Component part)
    {
        return Components.onTheEventThread(() -> javax.swing.SwingUtilities.convertPoint(part, 0, 0, form));
    }

    private static javax.swing.JLabel labelSaying(java.awt.Component window, String text)
    {
        javax.swing.JLabel found = Components.find(window, javax.swing.JLabel.class, each -> text.equals(each.getText()));

        assertNotNull(found, "the window has no label saying \"" + text + "\"");

        return found;
    }

    /**
     * The window keeps its width while open, whatever is edited (Adam: "we don't want it to get wider as station edits
     * change"), in English, German and Polish at 100% and 150%, with nothing cut off: the form and the table still fit.
     *
     * MUTATION: drop `lockTheWidth`'s columns, or have `pack` take the width it asks for, and this fails on the edit that
     * moves it.
     */
    @Test(timeOut = 420000)
    public void testTheWidthNeverChanges() throws Exception
    {
        try
        {
            for (java.util.Locale locale : new java.util.Locale[] {java.util.Locale.ENGLISH, java.util.Locale.GERMAN,
                new java.util.Locale("pl")})
            {
                for (float zoom : new float[] {1f, 1.5f})
                {
                    Components.onTheEventThread(() ->
                    {
                        I18n.setLocale(locale);

                        com.formdev.flatlaf.util.UIScale.setZoomFactor(zoom);

                        panel.closeStationWindows();

                        return null;
                    });

                    frozen.putBack();

                    everyEditKeepsTheWidth(locale + " at " + zoom);
                }
            }
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                I18n.setLocale(java.util.Locale.ENGLISH);

                com.formdev.flatlaf.util.UIScale.setZoomFactor(1f);

                return null;
            });
        }
    }

    private void everyEditKeepsTheWidth(String where) throws Exception
    {
        TileKey square = aStation();

        StationSettingsWindow window = StationDoors.open(panel, square);

        StationDoors.current(window);

        int width = Components.onTheEventThread(window::getWidth);

        String[] pressed = {"station.advanced", "station.preset.TERMINUS", "station.preset.PARKING", "station.runsThrough",
            "station.stops", "station.stops", "station.runsThrough", "station.preset.REVERSING_POINT",
            "station.preset.STATION"};

        for (String each : pressed)
        {
            StationDoors.press(window, each);
            StationDoors.current(window);

            assertTheWidthHolds(window, width, where + ", " + each);
        }

        // A refusal under a field, in both fields that can refuse
        StationDoors.typeAndEnter(window, "station.maxLength", "not a number at all, and long enough to run on and on");
        assertTheWidthHolds(window, width, where + ", a refused length");

        StationDoors.typeAndEnter(window, "station.maxLength", "4");

        String other = Components.onTheEventThread(() ->
        {
            for (TileKey each : session.getStore().getNamedTiles())
            {
                if (!each.equals(square)) return session.getStore().getPointName(each);
            }

            return null;
        });

        StationDoors.typeAndEnter(window, "station.name", other);
        assertTheWidthHolds(window, width, where + ", a refused name");

        // A long name, which the title and the field show
        StationDoors.typeAndEnter(window, "station.name", "A station with a very long name indeed which goes on and on and on");
        StationDoors.current(window);
        assertTheWidthHolds(window, width, where + ", a long name");

        // Long lists in every value that shows one, and the sentence under the table
        Components.onTheEventThread(() ->
        {
            List<TileKey> everySignal = new java.util.ArrayList<>();
            List<TileKey> everyPoint = new java.util.ArrayList<>(session.getStore().getNamedTiles());

            everyPoint.remove(square);

            for (Map.Entry<TileKey, org.traincontrol.base.LayoutDiagramComponent> each : session.getGraph().getTiles().entrySet())
            {
                if (each.getValue() != null && each.getValue().isSignal()) everySignal.add(each.getKey());
            }

            session.setProtectingSignals(square, everySignal);
            session.setEntrySignals(square, everySignal);
            session.getStore().setBlockingPoints(square, everyPoint);
            session.setWhichTrains(square, AutonomySession.WhichTrains.BLACKLIST, Arrays.asList("A train", "Another train"));

            return null;
        });

        StationDoors.press(window, "station.fullAutonomy");
        StationDoors.current(window);
        assertTheWidthHolds(window, width, where + ", long lists");

        // Folded again, and on to another square, which may have other sides
        StationDoors.press(window, "station.advanced");
        StationDoors.current(window);
        assertTheWidthHolds(window, width, where + ", Advanced folded");

        StationDoors.open(panel, aSecondStation(square));
        StationDoors.current(window);
        assertTheWidthHolds(window, width, where + ", another square");

        Components.onTheEventThread(() -> { panel.closeStationWindows(); return null; });
    }

    private static void assertTheWidthHolds(StationSettingsWindow window, int width, String after) throws Exception
    {
        Components.settle();

        Components.onTheEventThread(() ->
        {
            assertEquals(window.getWidth(), width, "the window changed width after: " + after);

            window.validate();

            for (String each : new String[] {"station.form", "station.matrix"})
            {
                javax.swing.JPanel part = StationDoors.part(window, each, javax.swing.JPanel.class);

                assertTrue(part.getWidth() >= part.getPreferredSize().width, each + " is " + part.getWidth() + " wide and"
                    + " wants " + part.getPreferredSize().width + ", so it is cut off, after: " + after);
            }

            return null;
        });
    }

    /**
     * The picture's station wears the badge the track diagram's own pass puts on this square (with its two arrow options
     * on), through a plain station, a one-way one, a terminus and a closed one, and its marks follow each change at once.
     *
     * MUTATION: have `fill` skip `picture.show`, and it stands still; have `StationPicture.show` wear no badge, and it
     * differs from the diagram; and this fails.
     */
    @Test(timeOut = 180000)
    public void testThePictureWearsWhatTheDiagramDoes() throws Exception
    {
        TileKey square = aStation();

        java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

        String restricted = prefs.get(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, null);
        String allowed = prefs.get(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS, null);

        try
        {
            prefs.putBoolean(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);
            prefs.putBoolean(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS, true);

            org.traincontrol.gui.LayoutLabel onTheDiagram = Components.onTheEventThread(() ->
            {
                org.traincontrol.gui.LayoutLabel label = new org.traincontrol.gui.LayoutLabel(
                    session.getGraph().getTiles().get(square), new javax.swing.JPanel(), 30, ui, false);

                label.setSquare(square);

                ui.getDiagramTileRegistry().register(square, label);

                return label;
            });

            StationSettingsWindow window = StationDoors.open(panel, square);

            StationDoors.current(window);

            String plain = theSameOnBoth(window, onTheDiagram, "a plain station");

            Components.onTheEventThread(() ->
            {
                StationDoors.part(window, "station.depart.0", javax.swing.JComboBox.class).setSelectedIndex(1);

                return null;
            });

            StationDoors.current(window);

            String oneWay = theSameOnBoth(window, onTheDiagram, "a one-way station");

            assertFalse(oneWay.equals(plain), "a one-way station is drawn as a plain one");

            StationDoors.press(window, "station.preset.TERMINUS");
            StationDoors.current(window);

            String terminus = theSameOnBoth(window, onTheDiagram, "a terminus");

            assertFalse(terminus.equals(oneWay), "a terminus is drawn as a station");

            StationDoors.press(window, "station.runsThrough");
            StationDoors.press(window, "station.stops");
            StationDoors.current(window);

            assertFalse(theSameOnBoth(window, onTheDiagram, "a closed station").equals(terminus), "a closed station is drawn as an"
                + " open one");
        }
        finally
        {
            if (restricted == null) prefs.remove(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS);
            else prefs.put(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, restricted);

            if (allowed == null) prefs.remove(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS);
            else prefs.put(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS, allowed);

            Components.onTheEventThread(() -> { ui.getDiagramTileRegistry().reset(); return null; });
        }
    }

    /**
     * TopMainR1 stands on track that only runs northward: with Both Ways chosen for its own square, the picture's station
     * has a way in from the south and a way out to the north, drawn green only at the north.
     *
     * MUTATION: have `StationPicture` draw a way out open at every side trains may come in by, as Both Ways says, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testAOneWayStationShowsOnlyTheWayTrainsGo() throws Exception
    {
        TileKey square = Components.onTheEventThread(() -> support.AskingWindows.stationNamed(session, "TopMainR1"));

        StationSettingsWindow window = StationDoors.open(panel, square);

        javax.swing.JComboBox<?> depart = StationDoors.part(window, "station.depart.0", javax.swing.JComboBox.class);

        Components.onTheEventThread(() ->
        {
            depart.setSelectedItem(I18n.t("autosetup.ui.menuRouteBoth"));

            return null;
        });

        StationDoors.current(window);

        assertEquals(Components.onTheEventThread(depart::getSelectedItem), I18n.t("autosetup.ui.menuRouteBoth"),
            "precondition: Both Ways is chosen for the station");

        assertWays(window, set("S"), set("N"), set("N"));

        StationDoors.close(window);
    }

    /**
     * TopR1ParkLong's track runs on to its east side and stops there: the picture draws a way in and out at the west, and
     * no mark at the east.
     *
     * MUTATION: have `LayoutStandingRules` take a way out as the side opposite the copy's way in, straight on, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testADeadEndSideShowsNoWay() throws Exception
    {
        TileKey square = Components.onTheEventThread(() -> support.AskingWindows.stationNamed(session, "TopR1ParkLong"));

        assertTrue(Components.onTheEventThread(() ->
        {
            for (org.traincontrol.automationui.TilePorts.Route route : session.getRoutes(square).values())
            {
                if (route.getA() == org.traincontrol.automationui.TilePorts.Side.E
                    || route.getB() == org.traincontrol.automationui.TilePorts.Side.E) return true;
            }

            return false;
        }), "precondition: the station's track reaches its east side");

        StationSettingsWindow window = StationDoors.open(panel, square);

        assertWays(window, set("W"), set("W"), set("W"));

        for (int mark : new int[] {CLOSED_RED, ARRIVAL_YELLOW})
        {
            assertFalse(sidesDrawnIn(window, mark).contains("E"), "a mark at the dead end, in " + Integer.toHexString(mark));
        }

        assertFalse(Components.onTheEventThread(window::pictureMarksForTest).containsKey("OUT.E"), "a way out at the dead end");
        assertFalse(Components.onTheEventThread(window::pictureMarksForTest).containsKey("IN.E"), "a way in at the dead end");

        StationDoors.close(window);
    }

    /**
     * Every station on the frozen railway, on every page: the ways in drawn open are the sides where Sent Here or Turned
     * Round of `Layout.standingRulesAt` is not No, and the ways out the sides where Sent From is.
     *
     * MUTATION: have `LayoutStandingRules` read a way out from `copy.getCopyFacing()`, which a one-Point square does not
     * record, and this fails.
     */
    @Test(timeOut = 600000)
    public void testTheWaysAgreeWithWhatIsAllowed() throws Exception
    {
        assertNotNull(model.getAutoLayoutIfLoaded(), "precondition: the frozen setup is built");

        List<String> differ = new java.util.ArrayList<>();

        int asked = 0;

        for (String page : Components.onTheEventThread(() -> new java.util.TreeSet<>(model.getLayoutList())))
        {
            List<TileKey> stations = stationsOn(page);

            if (stations.isEmpty()) continue;

            LayoutEditor on = PAGE.equals(page) ? editor : frozen.editorOn(page);

            try
            {
                AutonomyEditorPanel onPanel = Components.onTheEventThread(() -> on.getAutonomyPanel());

                for (TileKey square : stations)
                {
                    List<String> copies = Components.onTheEventThread(() ->
                        new java.util.ArrayList<>(session.getStationIndex().pointNamesAt(square)));

                    StationSettingsWindow window = StationDoors.open(onPanel, square);

                    // The railway the window asked
                    StandingRules rules = model.getAutoLayoutIfLoaded().standingRulesAt(copies);

                    assertNotNull(rules, square + ": precondition: its copies are on the railway");

                    Set<String> in = new java.util.TreeSet<>();

                    for (StandingRules.Row row : new StandingRules.Row[] {StandingRules.Row.SENT_HERE,
                        StandingRules.Row.TURNED_ROUND})
                    {
                        for (boolean byHand : new boolean[] {false, true})
                        {
                            for (Map.Entry<String, StandingRules.Cell> each : rules.cells(row, byHand).entrySet())
                            {
                                if (each.getValue().getAnswer() != StandingRules.Answer.NO) in.add(each.getKey());
                            }
                        }
                    }

                    Set<String> leaving = new java.util.TreeSet<>();

                    for (boolean byHand : new boolean[] {false, true})
                    {
                        for (Map.Entry<String, StandingRules.Cell> each
                            : rules.cells(StandingRules.Row.SENT_FROM, byHand).entrySet())
                        {
                            if (each.getValue().getAnswer() != StandingRules.Answer.NO) leaving.add(each.getKey());
                        }
                    }

                    Set<String> drawnIn = Components.onTheEventThread(() -> window.pictureWaysForTest(false));
                    Set<String> drawnOut = Components.onTheEventThread(() -> window.pictureWaysForTest(true));

                    boolean oneCopy = copies.size() == 1;

                    if (!drawnIn.equals(in)) differ.add(square + ": in " + drawnIn + ", allowed " + in);

                    if (oneCopy ? drawnOut.isEmpty() != leaving.isEmpty() : !drawnOut.equals(leaving))
                    {
                        differ.add(square + ": out " + drawnOut + ", sent from " + leaving);
                    }

                    asked++;

                    StationDoors.close(window);
                }
            }
            finally
            {
                if (on != editor) support.FrozenEditor.dispose(on);
            }
        }

        assertTrue(asked >= 30, "precondition: only " + asked + " stations were asked about");

        assertTrue(differ.isEmpty(), "the picture's ways differ from what is allowed: " + differ);
    }

    /**
     * Running through is the form's box and the table's row, and has no mark: BottomMainA, which trains run through both
     * ways, wears no tested-path line, and none of that line's yellow or orange is drawn.
     *
     * MUTATION: have `StationPicture.show` give the station a trace for each side trains may run through from, as the
     * mockup did, and this fails.
     */
    @Test(timeOut = 120000)
    public void testNoRunThroughMarkIsDrawn() throws Exception
    {
        TileKey square = Components.onTheEventThread(() -> support.AskingWindows.stationNamed(session, "BottomMainA"));

        StationSettingsWindow window = StationDoors.open(panel, square);

        assertTrue(Components.onTheEventThread(() -> window.drawnForTest(StandingRules.Row.SENT_THROUGH, "hand"))
            .containsValue(StandingRules.Answer.YES), "precondition: trains run through it");

        assertTrue(Components.onTheEventThread(() -> window.pictureForTest().getTraces().isEmpty()),
            "the station wears a run-through line");

        javax.swing.JComponent block = StationDoors.part(window, "station.picture.ways", javax.swing.JComponent.class);

        int line = Components.onTheEventThread(() -> pixels(drawnOn(block), new java.awt.Rectangle(block.getSize()),
            rgb -> rgb == TRACE_YELLOW || rgb == TRACE_ORANGE));

        assertEquals(line, 0, "pixels of a run-through line are drawn");

        StationDoors.close(window);
    }

    /**
     * Only arrows carry color: the square east of BottomMainA is a signal whose art has a red lamp, and the picture draws
     * that square with no red in it.
     *
     * MUTATION: have `StationPicture` paint the next squares without `inGray`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testANeighborsSignalIsDrawnWithoutColor() throws Exception
    {
        TileKey square = Components.onTheEventThread(() -> support.AskingWindows.stationNamed(session, "BottomMainA"));

        assertTrue(Components.onTheEventThread(() -> session.getGraph().getTiles()
            .get(square.beside(org.traincontrol.automationui.TilePorts.Side.E)).isSignal()),
            "precondition: the square east of the station is a signal");

        StationSettingsWindow window = StationDoors.open(panel, square);

        javax.swing.JComponent block = StationDoors.part(window, "station.picture.ways", javax.swing.JComponent.class);

        int[] red = Components.onTheEventThread(() ->
        {
            java.awt.Rectangle east = (java.awt.Rectangle) window.pictureMarksForTest().get("SQUARE.E");

            assertNotNull(east, "the picture has no square east of the station");

            java.awt.Component signal = null;

            // The art lives in the first child, out of the pointer's way
            for (java.awt.Component each : ((java.awt.Container) block.getComponent(0)).getComponents())
            {
                if (each.getBounds().equals(east)) signal = each;
            }

            assertNotNull(signal, "no square of the picture is the signal");

            return new int[] {pixels(drawnOn((javax.swing.JComponent) signal), new java.awt.Rectangle(signal.getSize()), RED),
                pixels(drawnOn(block), east, RED)};
        });

        assertTrue(red[0] > 0, "precondition: the signal's own art has no red");

        assertEquals(red[1], 0, "red pixels on the signal's square in the picture");

        StationDoors.close(window);
    }

    /**
     * A click on a mark changes what the form's control for it changes, and the table follows, at BottomMainA: the barred
     * way in from the east opens as May Arrive From East does; the way out east closes as Toward W under May Depart does;
     * the arrow on the square west of it closes that way as that square's right-click Trains May Depart does.
     *
     * MUTATION: have `wayOut` toggle the arm opposite the one clicked, and this fails.
     */
    @Test(timeOut = 180000)
    public void testEachClickChangesWhatItsControlDoes() throws Exception
    {
        final org.traincontrol.automationui.TilePorts.Side east = org.traincontrol.automationui.TilePorts.Side.E;
        final org.traincontrol.automationui.TilePorts.Side westSide = org.traincontrol.automationui.TilePorts.Side.W;

        TileKey square = Components.onTheEventThread(() -> support.AskingWindows.stationNamed(session, "BottomMainA"));
        TileKey west = square.beside(westSide);

        StationSettingsWindow window = StationDoors.open(panel, square);

        assertFalse(StationDoors.on(window, "station.arrive.E"), "precondition: arrivals from the east are closed");

        // The way in from the east
        clickOn(window, "IN.E");
        StationDoors.current(window);

        assertTrue(StationDoors.on(window, "station.arrive.E"), "the click did not tick May Arrive From East");
        assertFalse(Components.onTheEventThread(() -> session.getBarredArrivals(square).contains(east)),
            "the setup still bars arrivals from the east");
        assertEquals(Components.onTheEventThread(() -> window.drawnForTest(StandingRules.Row.SENT_HERE, "hand")).get("E"),
            StandingRules.Answer.YES, "the table did not follow the way in");
        assertTrue(Components.onTheEventThread(() -> window.pictureWaysForTest(false)).contains("E"),
            "the picture did not follow the way in");

        // The way out to the east
        clickOn(window, "OUT.E");
        StationDoors.current(window);

        javax.swing.JComboBox<?> depart = StationDoors.part(window, "station.depart.0", javax.swing.JComboBox.class);

        assertEquals(Components.onTheEventThread(depart::getSelectedItem), I18n.f("autosetup.ui.menuRouteToward", "W"),
            "the click did not choose Toward W under May Depart");
        assertEquals(Components.onTheEventThread(() -> window.drawnForTest(StandingRules.Row.SENT_FROM, "hand")).get("E"),
            StandingRules.Answer.NO, "the table did not follow the way out");
        assertFalse(Components.onTheEventThread(() -> window.pictureWaysForTest(true)).contains("E"),
            "the picture did not follow the way out");

        // The square to the west, its arrow at its own west side
        clickOn(window, "NEXT.W.W");
        StationDoors.current(window);

        assertEquals(Components.onTheEventThread(() ->
        {
            Map.Entry<org.traincontrol.automationui.TileGraph.RouteId, org.traincontrol.automationui.TilePorts.Route> only =
                session.getRoutes(west).entrySet().iterator().next();

            org.traincontrol.automationui.TilePorts.Route road = only.getValue();

            org.traincontrol.automationui.TileGraph.Direction now = org.traincontrol.automationui.TileGraph.inForce(
                session.directionAsDrawn(west, only.getKey()), road);

            return now == (road.getA() == east ? org.traincontrol.automationui.TileGraph.Direction.TOWARD_A
                : org.traincontrol.automationui.TileGraph.Direction.TOWARD_B);
        }), Boolean.TRUE, "the square west of the station does not now run eastward only");
        assertFalse(Components.onTheEventThread(() -> window.pictureWaysForTest(true)).contains("W"),
            "the picture did not follow the next square");

        StationDoors.close(window);
    }

    /**
     * On every station of the page each side's way out sits on that side's axis with its tip at the edge, and its way in
     * beside it, pointing in, off the axis and the station's art (Adam: "the yellow/grey egress arrows should be offset
     * from the station"); both inside the station's square, apart, and clear of every next square.  A curve keeps its ways
     * in each plainly nearer its own side (Adam: "I7 put the arrows elsewhere just for the popup").
     *
     * MUTATION: draw a straight side's way in on the axis again (`apartChevron` without `ALONGSIDE`), or a quarter square
     * along the edge, where the diagram puts its arrival chevrons, and this fails.
     */
    @Test(timeOut = 300000)
    public void testTheMarksOfASideLineUp() throws Exception
    {
        List<TileKey> stations = stationsOn(PAGE);

        List<String> wrong = new java.util.ArrayList<>();

        int sides = 0;

        for (TileKey square : stations)
        {
            StationSettingsWindow window = StationDoors.open(panel, square);

            Map<String, java.awt.Shape> marks = Components.onTheEventThread(window::pictureMarksForTest);

            java.awt.Rectangle at = (java.awt.Rectangle) marks.get("SQUARE");

            java.awt.Rectangle inside = new java.awt.Rectangle(at);

            inside.grow(1, 1);

            boolean bent = Components.onTheEventThread(() -> isCurve(square));

            for (String side : new String[] {"N", "E", "S", "W"})
            {
                java.awt.Shape out = marks.get("OUT." + side);

                if (out == null) continue;

                sides++;

                java.awt.geom.Line2D axis = (java.awt.geom.Line2D) marks.get("AXIS." + side);

                for (String kind : new String[] {"OUT.", "IN."})
                {
                    java.awt.Shape mark = marks.get(kind + side);

                    if (mark == null) continue;

                    double[] middle = middleOf(mark);

                    double off = axis.ptLineDist(middle[0], middle[1]);

                    // "I7 put the arrows elsewhere just for the popup": a curve's two ways in leave the axis, kept by their own edge
                    if (kind.equals("IN.") && bent)
                    {
                        double[] own = edgeMiddle(at, side);
                        double ownGap = Math.hypot(middle[0] - own[0], middle[1] - own[1]);

                        for (String other : new String[] {"N", "E", "S", "W"})
                        {
                            double[] there = edgeMiddle(at, other);

                            if (!other.equals(side) && Math.hypot(middle[0] - there[0], middle[1] - there[1]) <= ownGap)
                            {
                                wrong.add(square + " IN." + side + " is not plainly near its own side");
                            }
                        }
                    }
                    else if (kind.equals("IN."))
                    {
                        if (off < 12.0) wrong.add(square + " IN." + side + " sits " + off + " from the axis, on the station");
                    }
                    else if (off > 1.0) wrong.add(square + " " + kind + side + " is " + off + " off its axis");

                    if (!inside.contains(mark.getBounds2D())) wrong.add(square + " " + kind + side + " leaves the station's square");

                    for (Map.Entry<String, java.awt.Shape> next : marks.entrySet())
                    {
                        if (next.getKey().startsWith("SQUARE.") && next.getValue().intersects(mark.getBounds2D()))
                        {
                            wrong.add(square + " " + kind + side + " is over " + next.getKey());
                        }
                    }
                }

                java.awt.Shape in = marks.get("IN." + side);

                if (in != null)
                {
                    java.awt.geom.Area both = new java.awt.geom.Area(out);

                    both.intersect(new java.awt.geom.Area(in));

                    if (!both.isEmpty()) wrong.add(square + " " + side + ": its way in overlaps its way out");
                }
            }

            StationDoors.close(window);
        }

        assertTrue(sides >= 20, "precondition: only " + sides + " sides were looked at");

        assertTrue(wrong.isEmpty(), "marks off their axis or overlapping: " + wrong);
    }

    /** Whether the station is a curve. */
    private static boolean isCurve(TileKey square)
    {
        for (org.traincontrol.automationui.TilePorts.Route road : session.getRoutes(square).values())
        {
            if (road.getA().opposite() != road.getB() && road.getA() != road.getB()) return true;
        }

        return false;
    }

    private static double[] edgeMiddle(java.awt.Rectangle at, String side)
    {
        switch (side)
        {
            case "N": return new double[] {at.getCenterX(), at.y};
            case "S": return new double[] {at.getCenterX(), at.y + at.height};
            case "E": return new double[] {at.x + at.width, at.getCenterY()};
            default: return new double[] {at.x, at.getCenterY()};
        }
    }

    /**
     * Every mark of every station on every page is pressed by a click at its middle, though on a curve a way in's chevron
     * lies inside the box round the way out beside it; and a real click on TopMainR2Inter's way in from the east leaves
     * May Depart alone.  Nothing is let off, the two ways in of a curve included.
     *
     * MUTATION: have `StationPicture.Mark.hit` test the shape's bounds grown by 3 pixels, taking the first mark that
     * holds the point, or put `apartChevron`'s chevron back on the chord, and this fails.
     */
    @Test(timeOut = 300000)
    public void testEveryMarkIsPressedByAClickAtItsMiddle() throws Exception
    {
        StationSettingsWindow first = StationDoors.open(panel,
            Components.onTheEventThread(() -> support.AskingWindows.stationNamed(session, "TopMainR2Inter")));

        javax.swing.JComboBox<?> depart = StationDoors.part(first, "station.depart.0", javax.swing.JComboBox.class);

        Object before = Components.onTheEventThread(depart::getSelectedItem);

        clickOn(first, "IN.E");
        StationDoors.current(first);

        assertEquals(Components.onTheEventThread(depart::getSelectedItem), before,
            "a click on TopMainR2Inter's way in from the east changed May Depart");

        StationDoors.close(first);

        List<String> wrong = new java.util.ArrayList<>();
        int[] marks = {0};

        eachStation(square -> true, (square, window) ->
            marks[0] += Components.onTheEventThread(() -> pressedWrong(window, wrong)));

        assertTrue(marks[0] > 100, "precondition: only " + marks[0] + " marks were clicked");

        assertTrue(wrong.isEmpty(), "a click at a mark's middle presses another: " + wrong);
    }

    /**
     * A real click, routed through the window as AWT routes one, presses every mark of the curved stations and of a
     * straight one (or is refused with the reason its tooltip gives), and hovering shows the mark's own tooltip: the station art under the marks never takes the pointer.
     *
     * MUTATION: have `StationPicture.label` add each art label to `block` instead of the hidden `art` panel, and this
     * fails (the click lands on the LayoutLabel).
     */
    @Test(timeOut = 600000)
    public void testARoutedClickPressesEveryMarkAndHoverShowsItsTip() throws Exception
    {
        List<String> wrong = new java.util.ArrayList<>();
        int[] done = {0, 0, 0};

        eachStation(square -> isCurved(square) || "BottomMainA".equals(
            Components.onTheEventThread(() -> session.getStore().getPointName(square))), (square, window) ->
        {
            String name = String.valueOf(Components.onTheEventThread(() -> session.getStore().getPointName(square)));

            List<String> keys = new java.util.ArrayList<>(Components.onTheEventThread(() -> window.pictureMarksForTest().keySet()));

            keys.removeIf(key -> key.startsWith("SQUARE") || key.startsWith("AXIS"));

            if (isCurved(square)) done[0]++;

            for (String key : keys)
            {
                Set<String> before = Components.onTheEventThread(window::pictureStateForTest);
                String setupBefore = setup();

                String[] hover = new String[3];

                routedClick(window, key, hover);
                StationDoors.current(window);

                if (!"block".equals(hover[0])) wrong.add(name + " " + key + ": the pointer is over " + hover[0]);
                else if (hover[2] == null || !hover[2].equals(hover[1])) wrong.add(name + " " + key + ": the tooltip is " + hover[1]);

                if (!before.equals(Components.onTheEventThread(window::pictureStateForTest)) || !setupBefore.equals(setup())) done[2]++;
                else if (!refusal(hover[2], key))
                {
                    wrong.add(name + " " + key + ": the click changed nothing, the tip says " + hover[2]);
                }

                done[1]++;
            }
        });

        assertTrue(done[0] >= 3, "precondition: only " + done[0] + " curved stations were found");
        assertTrue(done[1] > 20, "precondition: only " + done[1] + " marks were pressed");
        assertTrue(done[2] > 10, "precondition: only " + done[2] + " presses changed anything");

        assertTrue(wrong.isEmpty(), "a routed click or hover missed its mark: " + wrong);
    }

    /** The pixel counts of the bright green blobs of a picture: the arrows' fill, which their dark outline sets apart. */
    private static List<Integer> greenAreas(BufferedImage img)
    {
        List<Integer> out = new java.util.ArrayList<>();
        boolean[][] seen = new boolean[img.getWidth()][img.getHeight()];

        for (int x = 0; x < img.getWidth(); x++)
        {
            for (int y = 0; y < img.getHeight(); y++)
            {
                if (seen[x][y] || !isArrowGreen(img.getRGB(x, y))) continue;

                int n = 0;
                java.util.ArrayDeque<int[]> todo = new java.util.ArrayDeque<>();

                todo.add(new int[] {x, y});
                seen[x][y] = true;

                while (!todo.isEmpty())
                {
                    int[] at = todo.poll();

                    n++;

                    for (int side = 0; side < 4; side++)
                    {
                        int nx = at[0] + (side < 2 ? 2 * side - 1 : 0);
                        int ny = at[1] + (side < 2 ? 0 : 2 * side - 5);

                        if (nx >= 0 && ny >= 0 && nx < img.getWidth() && ny < img.getHeight() && !seen[nx][ny]
                            && isArrowGreen(img.getRGB(nx, ny)))
                        {
                            seen[nx][ny] = true;
                            todo.add(new int[] {nx, ny});
                        }
                    }
                }

                if (n >= 30) out.add(n);
            }
        }

        return out;
    }

    private static boolean isArrowGreen(int rgb)
    {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;

        return g > 150 && r < 130 && b < 150 && g - r > 60;
    }

    /** The longest distance between two corners of an arrow's outline: its size, however it points. */
    private static double arrowSpan(java.awt.Shape arrow)
    {
        List<double[]> corners = new java.util.ArrayList<>();
        double[] at = new double[6];

        for (java.awt.geom.PathIterator it = arrow.getPathIterator(null); !it.isDone(); it.next())
        {
            if (it.currentSegment(at) != java.awt.geom.PathIterator.SEG_CLOSE) corners.add(new double[] {at[0], at[1]});
        }

        double most = 0;

        for (double[] a : corners) for (double[] b : corners) most = Math.max(most, Math.hypot(a[0] - b[0], a[1] - b[1]));

        return most;
    }

    /**
     * On a straight station every arrow, a way out's and the next squares', is the size the track diagram draws a next
     * square's arrows at, as shaped and as painted.  On a curved one the ways out are the smaller 48 px arrows and the
     * station's icon is whole: its blue circle is at least 20 pixels across and 250 pixels of it show.
     *
     * MUTATION: in `StationPicture.stationMarks`, make a straight side's span 70, or in `StationPicture.show` pass
     * `0.6f` for a curved station's icon, or in `StationPicture.stationMarks` take the curved span as `SQUARE`, and
     * this fails.
     */
    @Test(timeOut = 600000)
    public void testAStraightStationsArrowsAreOneSizeAndACurvedStationKeepsItsIcon() throws Exception
    {
        int[] done = {0, 0};
        int[] compared = {0};
        List<String> wrong = new java.util.ArrayList<>();

        eachStation(square -> isCurved(square) ? done[0] < 3 : done[1] < 3, (square, window) ->
        {
            boolean curved = isCurved(square);

            if (curved) done[0]++;
            else done[1]++;

            JComponent block = StationDoors.part(window, "station.picture.ways", JComponent.class);
            Map<String, java.awt.Shape> marks = Components.onTheEventThread(window::pictureMarksForTest);
            Set<String> state = Components.onTheEventThread(window::pictureStateForTest);
            int cell = ((java.awt.Rectangle) marks.get("SQUARE")).width;

            for (Map.Entry<String, java.awt.Shape> each : marks.entrySet())
            {
                if (!each.getKey().startsWith("OUT.")) continue;

                boolean open = state.stream().anyMatch(k -> k.startsWith(each.getKey() + " ") && k.endsWith(":true"));
                double expected = arrowSpan(org.traincontrol.automationui.TileAnnotation.arrowShape(new int[] {0, 0},
                    new double[] {1, 0}, open, curved ? 48 : cell));

                // A slanted arrow's whole-pixel corners differ from a level one's by a pixel or so
                if (Math.abs(arrowSpan(each.getValue()) - expected) > 1.5)
                {
                    wrong.add(square + " " + each.getKey() + " is " + arrowSpan(each.getValue()) + " long, not " + expected);
                }
            }

            BufferedImage img = new BufferedImage(block.getWidth(), block.getHeight(), BufferedImage.TYPE_INT_RGB);

            Components.onTheEventThread(() ->
            {
                java.awt.Graphics2D g = img.createGraphics();

                g.setColor(Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                block.paint(g);
                g.dispose();

                return null;
            });

            // As painted, on a straight station every green arrow, the station's and the next squares' alike, has the
            // same area
            List<Integer> areas = greenAreas(img);

            if (!curved && areas.size() >= 2) compared[0]++;

            if (!curved && areas.size() >= 2 && java.util.Collections.max(areas) > 1.3 * java.util.Collections.min(areas))
            {
                wrong.add(square + ": green arrows of " + areas + " pixels");
            }

            if (!curved) return;

            java.awt.Rectangle at = (java.awt.Rectangle) marks.get("SQUARE");
            int left = Integer.MAX_VALUE;
            int right = -1;
            int blue = 0;

            for (int y = at.y; y < at.y + at.height; y++)
            {
                for (int x = at.x; x < at.x + at.width; x++)
                {
                    int rgb = img.getRGB(x, y);

                    if ((rgb & 0xff) > 120 && ((rgb >> 16) & 0xff) < 60 && ((rgb >> 8) & 0xff) < 60)
                    {
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                        blue++;
                    }
                }
            }

            if (right < 0) wrong.add(square + ": no blue circle painted");
            else if (right - left + 1 < 20 || blue < 250)
            {
                wrong.add(square + ": the circle is " + (right - left + 1) + " px across, " + blue + " px showing");
            }
        });

        assertTrue(done[0] >= 3 && done[1] >= 2, "precondition: " + done[0] + " curved and " + done[1] + " straight stations");
        assertTrue(compared[0] >= 2, "precondition: only " + compared[0] + " straight pictures with two green arrows");
        assertTrue(wrong.isEmpty(), "arrows or icon off: " + wrong);
    }

    /** Whether a mark's tooltip gives the reason its click can change nothing. */
    private static boolean refusal(String tip, String key)
    {
        String side = key.substring(key.length() - 1);

        for (String reason : new String[] {I18n.t("autosetup.ui.stationPicOutBeyond"), I18n.t("autosetup.ui.stationPicInKept"),
            I18n.t("autosetup.ui.stationPicCannot"), I18n.f("autosetup.ui.hintOneWayIn", I18n.t("autosetup.ui.side" + side))})
        {
            if (tip != null && tip.contains(reason)) return true;
        }

        return false;
    }

    /** What a claim does with one station's open window. */
    private interface Visit
    {
        void at(TileKey square, StationSettingsWindow window) throws Exception;
    }

    /** Opens Station Settings on each station of the railway that `which` takes, hands it to `visit`, and closes it. */
    private void eachStation(java.util.function.Predicate<TileKey> which, Visit visit) throws Exception
    {
        Set<String> pages = Components.onTheEventThread(() ->
        {
            Set<String> out = new java.util.TreeSet<>();

            for (TileKey each : session.getReducer().getPoints().keySet()) out.add(each.getPage());

            return out;
        });

        for (String page : pages)
        {
            LayoutEditor on = PAGE.equals(page) ? editor : frozen.editorOn(page);

            AutonomyEditorPanel onPanel = Components.onTheEventThread(() -> on.getAutonomyPanel());

            try
            {
                for (TileKey square : stationsOn(page))
                {
                    if (!which.test(square)) continue;

                    StationSettingsWindow window = StationDoors.open(onPanel, square);

                    visit.at(square, window);

                    StationDoors.close(window);
                }
            }
            finally
            {
                if (on != editor) support.FrozenEditor.dispose(on);
            }
        }
    }

    /** Each of the window's marks asked what a click at its middle presses; one line in `wrong` per other answer. */
    private static int pressedWrong(StationSettingsWindow window, List<String> wrong)
    {
        int asked = 0;

        Map<String, java.awt.Shape> marks = new LinkedHashMap<>(window.pictureMarksForTest());

        marks.keySet().removeIf(key -> key.startsWith("SQUARE") || key.startsWith("AXIS"));

        List<String> keys = new java.util.ArrayList<>(marks.keySet());

        for (int i = 0; i < keys.size(); i++)
        {
            for (int j = i + 1; j < keys.size(); j++)
            {
                java.awt.geom.Area both = new java.awt.geom.Area(marks.get(keys.get(i)));

                both.intersect(new java.awt.geom.Area(marks.get(keys.get(j))));

                if (!both.isEmpty()) wrong.add(window.squareForTest() + " " + keys.get(i) + " overlaps " + keys.get(j));
            }
        }

        for (Map.Entry<String, java.awt.Shape> each : marks.entrySet())
        {
            double[] middle = middleOf(each.getValue());

            String pressed = window.pictureMarkAtForTest(new java.awt.Point((int) Math.round(middle[0]),
                (int) Math.round(middle[1])));

            if (!each.getKey().equals(pressed)) wrong.add(window.squareForTest() + " " + each.getKey() + " presses " + pressed);

            asked++;
        }

        return asked;
    }

    /** The diagram's colors, as 0xRRGGBB: a way out, a shut one, a way in, and a tested path's two lines. */
    private static final int ONE_WAY_GREEN = 0x46cd5a;
    private static final int CLOSED_RED = 0xc80000;
    private static final int ARRIVAL_YELLOW = 0xffcd00;
    private static final int TRACE_YELLOW = 0xffd600;
    private static final int TRACE_ORANGE = 0xff9628;

    /** What the picture's station shows: its open ways in and out, and the sides it is drawn green at. */
    private static void assertWays(StationSettingsWindow window, Set<String> in, Set<String> out, Set<String> green)
        throws Exception
    {
        assertEquals(Components.onTheEventThread(() -> window.pictureWaysForTest(false)), in, "the ways in");
        assertEquals(Components.onTheEventThread(() -> window.pictureWaysForTest(true)), out, "the ways out");

        assertEquals(sidesDrawnIn(window, ONE_WAY_GREEN), green, "the sides of the station a green arrow is drawn at");
    }

    /** The sides of the picture's station square that pixels of exactly this color lie nearest to. */
    private static Set<String> sidesDrawnIn(StationSettingsWindow window, int color) throws Exception
    {
        javax.swing.JComponent block = StationDoors.part(window, "station.picture.ways", javax.swing.JComponent.class);

        return Components.onTheEventThread(() ->
        {
            java.awt.image.BufferedImage image = drawnOn(block);

            java.awt.Rectangle at = (java.awt.Rectangle) window.pictureMarksForTest().get("SQUARE");

            Set<String> out = new java.util.TreeSet<>();

            for (int y = at.y; y < at.y + at.height; y++)
            {
                for (int x = at.x; x < at.x + at.width; x++)
                {
                    if ((image.getRGB(x, y) & 0xffffff) != color) continue;

                    int[] to = {y - at.y, at.x + at.width - 1 - x, at.y + at.height - 1 - y, x - at.x};

                    int nearest = 0;

                    for (int i = 1; i < 4; i++) if (to[i] < to[nearest]) nearest = i;

                    out.add(new String[] {"N", "E", "S", "W"}[nearest]);
                }
            }

            return out;
        });
    }

    /** A strongly red pixel, as a signal's lamp is and no arrow of a next square is. */
    private static final java.util.function.IntPredicate RED = rgb ->
        (rgb >> 16 & 0xff) > 150 && (rgb >> 16 & 0xff) - (rgb >> 8 & 0xff) > 70 && (rgb >> 16 & 0xff) - (rgb & 0xff) > 70;

    /** How many pixels of an image inside this rectangle are of this kind, read as 0xRRGGBB. */
    private static int pixels(java.awt.image.BufferedImage image, java.awt.Rectangle within,
        java.util.function.IntPredicate kind)
    {
        int found = 0;

        for (int y = within.y; y < within.y + within.height; y++)
        {
            for (int x = within.x; x < within.x + within.width; x++)
            {
                if (kind.test(image.getRGB(x, y) & 0xffffff)) found++;
            }
        }

        return found;
    }

    /** The stations of one page of the frozen railway. */
    private static List<TileKey> stationsOn(String page)
    {
        return Components.onTheEventThread(() ->
        {
            List<TileKey> out = new java.util.ArrayList<>();

            for (TileKey each : session.getReducer().getPoints().keySet())
            {
                if (page.equals(each.getPage()) && session.getStore().isStation(each)) out.add(each);
            }

            return out;
        });
    }

    /** The middle of a mark's corners, which lies on a symmetric mark's axis. */
    private static double[] middleOf(java.awt.Shape shape)
    {
        double x = 0;
        double y = 0;
        int n = 0;

        double[] at = new double[6];

        for (java.awt.geom.PathIterator each = shape.getPathIterator(null); !each.isDone(); each.next())
        {
            int kind = each.currentSegment(at);

            // A curved outline (a next square's round mark) has no corners to average: its middle is its box's
            if (kind == java.awt.geom.PathIterator.SEG_CUBICTO || kind == java.awt.geom.PathIterator.SEG_QUADTO)
            {
                return new double[] {shape.getBounds2D().getCenterX(), shape.getBounds2D().getCenterY()};
            }

            if (kind == java.awt.geom.PathIterator.SEG_MOVETO || kind == java.awt.geom.PathIterator.SEG_LINETO)
            {
                x += at[0];
                y += at[1];
                n++;
            }
        }

        return n == 0 ? new double[] {shape.getBounds2D().getCenterX(), shape.getBounds2D().getCenterY()}
            : new double[] {x / n, y / n};
    }

    /**
     * A left click on the middle of one of the picture's marks, routed through the window as AWT routes a real click, so
     * it goes to the deepest component under the pointer.
     */
    private static void clickOn(StationSettingsWindow window, String mark) throws Exception
    {
        routedClick(window, mark, null);
    }

    /**
     * Presses a mark through the window.  When `hover` is given it gets the pointer's tooltip over the mark and the
     * component under it first.
     */
    private static void routedClick(StationSettingsWindow window, String mark, String[] hover) throws Exception
    {
        javax.swing.JComponent block = StationDoors.part(window, "station.picture.ways", javax.swing.JComponent.class);

        Components.settle();

        Components.onTheEventThread(() ->
        {
            java.awt.Shape shape = window.pictureMarksForTest().get(mark);

            assertNotNull(shape, "the picture has no " + mark);

            double[] middle = middleOf(shape);

            java.awt.Point at = javax.swing.SwingUtilities.convertPoint(block, (int) Math.round(middle[0]),
                (int) Math.round(middle[1]), window);

            if (hover != null)
            {
                java.awt.Component under = javax.swing.SwingUtilities.getDeepestComponentAt(window, at.x, at.y);

                hover[0] = String.valueOf(under == block ? "block" : under == null ? null : under.getClass().getSimpleName());

                java.awt.Point inBlock = javax.swing.SwingUtilities.convertPoint(window, at, under);

                hover[1] = under instanceof javax.swing.JComponent ? ((javax.swing.JComponent) under).getToolTipText(
                    new java.awt.event.MouseEvent(under, java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0, inBlock.x, inBlock.y, 0,
                        false)) : null;

                hover[2] = block.getToolTipText(new java.awt.event.MouseEvent(block, java.awt.event.MouseEvent.MOUSE_MOVED, 0,
                    0, (int) Math.round(middle[0]), (int) Math.round(middle[1]), 0, false));
            }

            window.dispatchEvent(new java.awt.event.MouseEvent(window, java.awt.event.MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, at.x, at.y, 0, false, java.awt.event.MouseEvent.NOBUTTON));

            for (int id : new int[] {java.awt.event.MouseEvent.MOUSE_PRESSED, java.awt.event.MouseEvent.MOUSE_RELEASED,
                java.awt.event.MouseEvent.MOUSE_CLICKED})
            {
                window.dispatchEvent(new java.awt.event.MouseEvent(window, id, System.currentTimeMillis(),
                    id == java.awt.event.MouseEvent.MOUSE_PRESSED ? java.awt.event.InputEvent.BUTTON1_DOWN_MASK : 0, at.x, at.y, 1,
                    false, java.awt.event.MouseEvent.BUTTON1));
            }

            return null;
        });
    }

    /** Whether any road of the station is a curve: its two arms are not opposite sides. */
    private static boolean isCurved(TileKey square)
    {
        return Components.onTheEventThread(() ->
        {
            for (org.traincontrol.automationui.TilePorts.Route road : session.getRoutes(square).values())
            {
                if (road.getA() != road.getB() && road.getA().opposite() != road.getB()) return true;
            }

            return false;
        });
    }

    private static Set<String> set(String... sides)
    {
        return new java.util.TreeSet<>(Arrays.asList(sides));
    }

    /** The diagram's real pass, its square's badge against the picture's, and what the picture then shows. */
    private static String theSameOnBoth(StationSettingsWindow window,
        org.traincontrol.gui.LayoutLabel onTheDiagram, String what) throws Exception
    {
        java.lang.reflect.Field worn = Reflect.field(org.traincontrol.gui.LayoutLabel.class, "autonomyAnnotation");

        return Components.onTheEventThread(() ->
        {
            ui.showStaticAutonomyLayer(true);

            try
            {
                Object diagram = worn.get(onTheDiagram);

                assertNotNull(diagram, what + ": the diagram's pass put nothing on the square");

                assertEquals(window.pictureForTest().getBadge(), ((org.traincontrol.automationui.TileAnnotation) diagram).getBadge(),
                    what + ": the picture's badge differs from the diagram's");
            }
            catch (IllegalAccessException e)
            {
                throw new AssertionError(e);
            }

            return window.pictureForTest().getBadge() + " out " + window.pictureWaysForTest(true) + " in "
                + window.pictureWaysForTest(false) + " " + window.pictureMarksForTest().keySet();
        });
    }

    /** A component drawn on its parent's background. */
    private static java.awt.image.BufferedImage drawnOn(javax.swing.JComponent part)
    {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(Math.max(1, part.getWidth()),
            Math.max(1, part.getHeight()), java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = out.createGraphics();

        g.setColor(part.getParent().getBackground());
        g.fillRect(0, 0, out.getWidth(), out.getHeight());

        part.paint(g);

        g.dispose();

        return out;
    }

    /** How many pixels differ, from this column on, over the width both share. */
    private static int difference(java.awt.image.BufferedImage one, java.awt.image.BufferedImage other, int fromColumn)
    {
        int differ = 0;

        for (int y = 0; y < Math.min(one.getHeight(), other.getHeight()); y++)
        {
            for (int x = fromColumn; x < Math.min(one.getWidth(), other.getWidth()); x++)
            {
                if (one.getRGB(x, y) != other.getRGB(x, y)) differ++;
            }
        }

        return differ;
    }

    // ----------------------------------------------------------------------------------------------------------- wording

    /**
     * Every language's refusal names Running Through in the window's own words, so the user can find the box.
     *
     * MUTATION: put another word for it in a bundle's refusal, and this fails.
     */
    @Test
    public void testTheRefusalNamesTheWindowsBox() throws Exception
    {
        for (String lang : new String[] {"", "_da", "_de", "_es", "_fr", "_it", "_nl", "_pl"})
        {
            java.util.Properties bundle = new java.util.Properties();

            try (java.io.InputStream in = new java.io.FileInputStream("src/org/traincontrol/resources/messages" + lang + ".properties"))
            {
                bundle.load(in);
            }

            String label = bundle.getProperty("autosetup.ui.stationSectionThrough");

            assertNotNull(label, "no label in messages" + lang);

            assertTrue(bundle.getProperty("autolayout.errorThroughStopOnlyStation").contains(label),
                "messages" + lang + ": the refusal does not say \"" + label + "\": "
                + bundle.getProperty("autolayout.errorThroughStopOnlyStation"));
        }
    }

    // ------------------------------------------------------------------------------------------------------------ doors

    private static void clicked(JList<String> list, int times)
    {
        java.awt.event.MouseEvent click = new java.awt.event.MouseEvent(list, java.awt.event.MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(), 0, 5, 5, times, false, java.awt.event.MouseEvent.BUTTON1);

        for (java.awt.event.MouseListener each : list.getMouseListeners()) each.mouseClicked(click);
    }

    private static void assertEveryCellSaysNo(StationSettingsWindow window)
    {
        Components.onTheEventThread(() ->
        {
            for (StandingRules.Row row : StandingRules.Row.values())
            {
                for (String tier : new String[] {"auto", "hand"})
                {
                    for (StandingRules.Answer answer : window.drawnForTest(row, tier).values())
                    {
                        assertEquals(answer, StandingRules.Answer.NO, row + " " + tier + " still shows an answer");
                    }
                }
            }

            return null;
        });
    }

    private static String setup()
    {
        return Components.onTheEventThread(() -> session.snapshotSetup().toString());
    }

    /** Every file in the sandbox and when it last changed. */
    private static Map<String, Long> filesWritten() throws Exception
    {
        Components.settle();

        Map<String, Long> out = new LinkedHashMap<>();

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(frozen.sandbox.getFolder().toPath()))
        {
            walk.filter(Files::isRegularFile).sorted().forEach(p -> out.put(p.toString(), p.toFile().lastModified()));
        }

        return out;
    }

    private static boolean visible(java.awt.Component window, String name)
    {
        return Components.onTheEventThread(() -> Components.named(window, name, JComponent.class).isVisible());
    }

    private static double numberOf(Object stored)
    {
        return Double.parseDouble(String.valueOf(stored));
    }

    /**
     * The lit pill is filled with the theme's accent and the others are not.
     *
     * MUTATION: make `PillButton.fillColor` ignore `isSelected()`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheLitPillIsFilledWithTheBlue() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(panel, aStation());

        StationDoors.press(window, "station.preset.TERMINUS");
        StationDoors.current(window);

        Color blue = Components.onTheEventThread(SegmentedControl::accent);

        for (StationPreset preset : StationPreset.values())
        {
            PillButton pill = StationDoors.part(window, "station.preset." + preset.name(), PillButton.class);
            Color inside = Components.onTheEventThread(() -> middleLeft(drawn(pill), pill));

            if (preset == StationPreset.TERMINUS) assertEquals(inside, blue, "the lit pill is not filled with the blue");
            else assertNotEquals(inside, blue, preset + " is filled with the blue though it is not lit");
        }
    }

    /**
     * The ends are round: each corner shows the background, the middle of the left end does not.
     *
     * MUTATION: make `PillButton.paintComponent` fill a plain rectangle, and this fails.
     */
    @Test(timeOut = 120000)
    public void testThePillsHaveRoundEnds() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(panel, aStation());

        StationDoors.press(window, "station.preset.PARKING");
        StationDoors.current(window);

        for (StationPreset preset : StationPreset.values())
        {
            PillButton pill = StationDoors.part(window, "station.preset." + preset.name(), PillButton.class);
            BufferedImage shot = Components.onTheEventThread(() -> drawn(pill));
            int w = shot.getWidth() - 1;
            int h = shot.getHeight() - 1;

            for (int[] corner : new int[][] {{0, 0}, {w, 0}, {0, h}, {w, h}})
            {
                assertEquals(shot.getRGB(corner[0], corner[1]) & 0xffffff, BACKGROUND.getRGB() & 0xffffff,
                    preset + " has a square corner");
            }

            assertNotEquals(shot.getRGB(1, h / 2) & 0xffffff, BACKGROUND.getRGB() & 0xffffff,
                preset + " has no body at the middle of its end");
        }
    }

    /**
     * Pressing a pill still sets its preset and lights it, a read-only pill stays legible and a lit one still reads as
     * lit, and an enabled pill shows the hand cursor.
     *
     * MUTATION: have `pressPreset` apply nothing, `PillButton.fillColor` use a gray fill when not enabled, or drop
     * `setCursor` from the constructor, and this fails.
     */
    @Test(timeOut = 120000)
    public void testThePillsStillPressAndReadWhenReadOnly() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(panel, aStation());

        for (StationPreset preset : StationPreset.values())
        {
            StationDoors.press(window, "station.preset." + preset.name());
            StationDoors.current(window);

            assertEquals(window.litPresetForTest(), preset, preset + " did not light when pressed");
        }

        PillButton lit = StationDoors.part(window, "station.preset.REVERSING_POINT", PillButton.class);
        PillButton unlit = StationDoors.part(window, "station.preset.STATION", PillButton.class);

        assertEquals(toolkitCursor(lit).getType(), java.awt.Cursor.HAND_CURSOR, "no hand cursor on an enabled pill");

        // A pill nobody has enabled or disabled: the constructor alone sets the hand
        assertEquals(toolkitCursor(Components.onTheEventThread(() -> new PillButton("fresh"))).getType(),
            java.awt.Cursor.HAND_CURSOR, "a new pill has no hand cursor");

        Components.onTheEventThread(() -> { lit.setEnabled(false); unlit.setEnabled(false); return null; });

        Color blue = Components.onTheEventThread(SegmentedControl::accent);

        assertEquals(Components.onTheEventThread(() -> middleLeft(drawn(lit), lit)), blue, "a read-only lit pill no longer reads as lit");

        for (PillButton pill : new PillButton[] {lit, unlit})
        {
            BufferedImage shot = Components.onTheEventThread(() -> drawn(pill));
            Color fill = new Color(shot.getRGB(6, shot.getHeight() / 2));

            assertTrue(strongestContrast(shot, fill) > 0.45, "the text of a read-only " + pill.getName() + " is hard to read");
        }
    }

    /** The cursor the toolkit shows: the component's own field, not what an override of getCursor answers. */
    private static java.awt.Cursor toolkitCursor(java.awt.Component c) throws Exception
    {
        return (java.awt.Cursor) Reflect.get(java.awt.Component.class, c, "cursor");
    }

    private static final Color BACKGROUND = new Color(250, 0, 250);

    private static BufferedImage drawn(JComponent part)
    {
        return drawn(part, BACKGROUND);
    }

    /** The component painted over a flat background, off screen. */
    private static BufferedImage drawn(JComponent part, Color background)
    {
        BufferedImage out = new BufferedImage(Math.max(1, part.getWidth()), Math.max(1, part.getHeight()),
            BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();

        g.setColor(background);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        part.paint(g);
        g.dispose();

        return out;
    }

    private static Color middleLeft(BufferedImage shot, JComponent part)
    {
        return new Color(shot.getRGB(6, shot.getHeight() / 2) & 0xffffff);
    }

    /** The largest luminance gap, 0 to 1, between the fill and any pixel inside the pill's middle band. */
    private static double strongestContrast(BufferedImage shot, Color fill)
    {
        double best = 0;

        for (int y = 3; y < shot.getHeight() - 3; y++)
        {
            for (int x = shot.getWidth() / 4; x < 3 * shot.getWidth() / 4; x++)
            {
                best = Math.max(best, Math.abs(luma(new Color(shot.getRGB(x, y))) - luma(fill)));
            }
        }

        return best;
    }

    private static double luma(Color c)
    {
        return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255;
    }

    private static TileKey aStation()
    {
        return Components.onTheEventThread(() ->
        {
            for (TileKey each : session.getReducer().getPoints().keySet())
            {
                if (PAGE.equals(each.getPage()) && session.getStore().isStation(each) && !session.isTurnAround(each)
                    && session.getStore().getPointName(each) != null) return each;
            }

            throw new AssertionError("precondition: no plain station on " + PAGE);
        });
    }

    private static TileKey aSecondStation(TileKey notThis)
    {
        return Components.onTheEventThread(() ->
        {
            for (TileKey each : session.getReducer().getPoints().keySet())
            {
                if (PAGE.equals(each.getPage()) && session.getStore().isStation(each) && !each.equals(notThis)) return each;
            }

            throw new AssertionError("precondition: only one station on " + PAGE);
        });
    }

    private static TileKey aStationWithTwoWaysIn()
    {
        return Components.onTheEventThread(() ->
        {
            for (TileKey each : session.getReducer().getPoints().keySet())
            {
                if (PAGE.equals(each.getPage()) && session.getStore().isStation(each) && session.arrivalSides(each).size() > 1
                    && !session.isMustTurnAround(each))
                {
                    // Every side open, whatever the frozen setup bars
                    session.setBarredArrivals(each, java.util.Collections.emptySet());

                    return each;
                }
            }

            throw new AssertionError("precondition: no station with two ways in on " + PAGE);
        });
    }

    private static TileKey aNamelessSensor()
    {
        return Components.onTheEventThread(() ->
        {
            for (TileKey each : session.getReducer().getPoints().keySet())
            {
                String name = session.getStore().getPointName(each);

                if (PAGE.equals(each.getPage()) && !session.getStore().isStation(each) && (name == null || name.isEmpty())) return each;
            }

            throw new AssertionError("precondition: no nameless sensor on " + PAGE);
        });
    }

    /** The window through the Station Settings... item of the menu the diagram builds. */
    private static StationSettingsWindow openedFromTheDiagramsMenu(TileKey square) throws Exception
    {
        Components.onTheEventThread(() ->
        {
            Components.withText(ui.buildAutonomyTileMenu(square), I18n.t("autosetup.ui.menuStationSettings")).doClick();

            return null;
        });

        StationSettingsWindow opened = StationDoors.showing(StationSettingsWindow.class);

        assertNotNull(opened, "Station Settings did not open from the diagram");

        StationDoors.current(opened);

        return opened;
    }

    /** The setup as it opened, with this square closed if asked. */
    private void startFrom(TileKey square, boolean closed) throws Exception
    {
        Components.onTheEventThread(() ->
        {
            session.restoreSetup(asOpened);

            if (closed) session.setPointProperty(square, "active", Boolean.FALSE);

            return null;
        });

        Components.settle();
    }

    /** Sets the four settings of a preset with the window's own boxes and radios. */
    private static void setByHand(StationSettingsWindow window, StationPreset preset) throws Exception
    {
        if (!StationDoors.on(window, "station.stops")) StationDoors.press(window, "station.stops");

        if (!StationDoors.on(window, "station.runsThrough")) StationDoors.press(window, "station.runsThrough");

        boolean wantsAuto = preset == StationPreset.STATION || preset == StationPreset.TERMINUS;

        if (StationDoors.on(window, "station.fullAutonomy") != wantsAuto) StationDoors.press(window, "station.fullAutonomy");

        String turn = preset == StationPreset.TERMINUS ? "station.turnMust"
            : preset == StationPreset.REVERSING_POINT ? "station.turnMay" : "station.turnNever";

        if (!StationDoors.on(window, turn)) StationDoors.press(window, turn);
    }

    private static Map<String, String> otherSettings(TileKey square)
    {
        return Components.onTheEventThread(() ->
        {
            Map<String, String> out = new LinkedHashMap<>();

            for (String key : new String[] {"maxTrainLength", "priority", "speedMultiplier", "home", "excludedLocs",
                "allowedLocs", "blockedBy"})
            {
                out.put(key, String.valueOf(session.getPointProperty(square, key)));
            }

            out.put("name", session.getStore().getPointName(square));
            out.put("barred", String.valueOf(session.getBarredArrivals(square)));
            out.put("exit", String.valueOf(session.getProtectingSignals(square)));
            out.put("entry", String.valueOf(session.getEntrySignals(square)));

            return out;
        });
    }
}
