package ui;

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
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
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

        assertFalse(window.drawnForTest(StandingRules.Row.SENT_THROUGH, "both").containsValue(StandingRules.Answer.YES),
            "both off still shows a way through");
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

        java.lang.reflect.Field held = AutonomyEditorPanel.class.getDeclaredField("findings");

        held.setAccessible(true);

        @SuppressWarnings("unchecked")
        JList<String> list = (JList<String>) held.get(panel);

        java.lang.reflect.Field tiles = AutonomyEditorPanel.class.getDeclaredField("findingTiles");

        tiles.setAccessible(true);

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
     * Blue is for the section headings alone (Adam: "it looks very blue"); every other label is black Semibold 13
     * (docs/UI-standards.md), and a label in an off section still grays.  `UiStyle.label` itself stays blue, for the windows
     * still waiting on his ruling.
     *
     * MUTATION: build a row's label or a label of the table with `UiStyle.label`, and this fails naming it.
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
            I18n.t("autosetup.ui.stationMatrixHeading"), I18n.t("autosetup.ui.stationPictureHeading"))),
            "what is blue is not the section headings");

        for (String key : new String[] {"autosetup.ui.stationMaxLength", "autosetup.ui.menuAutoDestination",
            "autosetup.ui.stationHome", "autosetup.ui.stationMatrixTrainsCanBe", "autosetup.ui.stationMatrixAutomatically",
            "autosetup.ui.stationMatrixManually", "autosetup.ui.stationRowSentHere", "autosetup.ui.stationRowTurned"})
        {
            javax.swing.JLabel label = labelSaying(window, I18n.t(key));

            assertEquals(label.getForeground(), java.awt.Color.BLACK, key + " is not black");
            assertEquals(label.getFont(), UiStyle.LABEL_FONT, key + " is not Semibold 13");
        }

        assertEquals(UiStyle.label("any").getForeground(), UiStyle.BLUE, "UiStyle.label changed, for every window using it");

        StationDoors.press(window, "station.stops");
        StationDoors.current(window);

        assertFalse(labelSaying(window, I18n.t("autosetup.ui.stationMaxLength")).isEnabled(),
            "a row's label does not gray with its section");
    }

    /**
     * The presets look like buttons, not text: a border, Bold 12, and starting where the row labels start; and the lit one
     * can be told from the others.
     *
     * MUTATION: put `JButton.buttonType` `toolBarButton` back on them, or a gap before the first, and this fails.
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
                assertTrue(button.isBorderPainted() && button.isContentAreaFilled(), preset + " draws no button");
                assertEquals(button.getFont(), new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12), preset + " is not Bold 12");
            }

            javax.swing.JPanel form = StationDoors.part(window, "station.form", javax.swing.JPanel.class);

            java.awt.image.BufferedImage drawn = drawnOn(form);

            int labelRow = rowOf(form, labelSaying(window, I18n.t("autosetup.ui.stationName")));
            int presetRow = rowOf(form, StationDoors.part(window, "station.preset.STATION", javax.swing.JToggleButton.class));

            int labelStarts = firstInk(drawn, labelRow, form.getBackground());
            int presetStarts = firstInk(drawn, presetRow, form.getBackground());

            assertTrue(Math.abs(labelStarts - presetStarts) <= 2, "the presets start at " + presetStarts
                + " and the row labels at " + labelStarts);

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

    private static javax.swing.JLabel labelSaying(java.awt.Component window, String text)
    {
        javax.swing.JLabel found = Components.find(window, javax.swing.JLabel.class, each -> text.equals(each.getText()));

        assertNotNull(found, "the window has no label saying \"" + text + "\"");

        return found;
    }

    /**
     * The window keeps its width while it is open, whatever is edited (Adam: "we don't want it to get wider as station edits
     * change"), in English, German and Polish at 100% and 150%, and nothing it holds is cut off by that: the form and the
     * table still fit, with long text wrapped or shortened.
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
     * The picture wears what the track diagram's own pass puts on this square (with its two arrow options on), through a
     * plain station, a one-way one, a terminus and a closed one, and it follows each change at once.
     *
     * MUTATION: have `fill` skip `picture.show`, and it stands still; have `StationPicture.show` ask `staticAnnotations`
     * for no allowed directions or `staticAnnotationFor` with no arrivals, and it differs from the diagram; and this fails.
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

            org.traincontrol.automationui.TileAnnotation plain = theSameOnBoth(window, onTheDiagram, "a plain station");

            Components.onTheEventThread(() ->
            {
                StationDoors.part(window, "station.depart.0", javax.swing.JComboBox.class).setSelectedIndex(1);

                return null;
            });

            StationDoors.current(window);

            org.traincontrol.automationui.TileAnnotation oneWay = theSameOnBoth(window, onTheDiagram, "a one-way station");

            assertFalse(oneWay.equals(plain), "a one-way station is drawn as a plain one");

            StationDoors.press(window, "station.preset.TERMINUS");
            StationDoors.current(window);

            org.traincontrol.automationui.TileAnnotation terminus = theSameOnBoth(window, onTheDiagram, "a terminus");

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

    /** The diagram's real pass, and what its square then wears against what the picture wears. */
    private static org.traincontrol.automationui.TileAnnotation theSameOnBoth(StationSettingsWindow window,
        org.traincontrol.gui.LayoutLabel onTheDiagram, String what) throws Exception
    {
        java.lang.reflect.Field worn = org.traincontrol.gui.LayoutLabel.class.getDeclaredField("autonomyAnnotation");

        worn.setAccessible(true);

        return Components.onTheEventThread(() ->
        {
            ui.showStaticAutonomyLayer(true);

            try
            {
                Object diagram = worn.get(onTheDiagram);

                assertNotNull(diagram, what + ": the diagram's pass put nothing on the square");

                assertEquals(window.pictureForTest(), diagram, what + ": the picture differs from the diagram");
            }
            catch (IllegalAccessException e)
            {
                throw new AssertionError(e);
            }

            return window.pictureForTest();
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

    /** The middle row, in the form's pixels, of a component somewhere inside it. */
    private static int rowOf(java.awt.Component form, java.awt.Component part)
    {
        return javax.swing.SwingUtilities.convertPoint(part, 0, part.getHeight() / 2, form).y;
    }

    /** The first column, within this row and the five either side, that is not the background. */
    private static int firstInk(java.awt.image.BufferedImage image, int row, java.awt.Color background)
    {
        int first = image.getWidth();

        for (int y = Math.max(0, row - 5); y <= Math.min(image.getHeight() - 1, row + 5); y++)
        {
            for (int x = 0; x < first; x++)
            {
                if ((image.getRGB(x, y) & 0xffffff) != (background.getRGB() & 0xffffff)) first = x;
            }
        }

        return first;
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
                for (String tier : row == StandingRules.Row.SENT_THROUGH ? new String[] {"both"} : new String[] {"auto", "hand"})
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
