package ui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.autonomyeditor.StationSettingsWindow;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;
import support.Components;
import support.StationDoors;

/**
 * The owner's notes from testing the autonomy editor (MT-889, MT-890, MT-920, OB-436, OB-437, OB-438, MT-948, OB-441), each
 * pressed on the frozen railway as a user presses it.
 */
public class testTheAutonomyEditorsTriageNotes
{
    private static support.FrozenEditor frozen;
    private static AutonomySession session;

    private static final String PAGE = "1 - Main";

    private LayoutEditor editor;
    private AutonomyEditorPanel panel;

    @BeforeMethod
    public void openTheEditor() throws Exception
    {
        if (frozen == null)
        {
            frozen = support.FrozenEditor.open();
            session = frozen.session;
        }

        frozen.putBack();

        editor = frozen.editorOn(PAGE);
        panel = Components.onTheEventThread(() -> editor.getAutonomyPanel());

        Components.onTheEventThread(() -> { editor.setBounds(60, 40, 1500, 900); return null; });
    }

    @AfterMethod(alwaysRun = true)
    public void closeTheEditor() throws Exception
    {
        support.AskingWindows.closeEveryDialog();

        support.FrozenEditor.dispose(editor);

        editor = null;
    }

    @AfterClass(alwaysRun = true)
    public static void closeTheRailway() throws Exception
    {
        if (frozen != null) frozen.close();
    }

    // ------------------------------------------------------------------------------------------------ MT-889

    /**
     * The filter's clear X sits inside the text field, shows only while there is text, and the row has air above it.
     *
     * MUTATION: add the clear button to the row beside the field instead of giving it to the field as its trailing
     * component, drop its `setVisible`, or take the top inset off the row's border, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheFilterCrossIsInsideTheBoxAndThereIsAGapAbove() throws Exception
    {
        JTextField field = Components.inside(panel.getFindingsBox(), JTextField.class);
        JButton cross = Components.inside(field, JButton.class);

        assertNotNull(cross, "the clear X is not inside the filter field");
        assertFalse(Components.onTheEventThread(cross::isVisible), "the X shows with nothing to clear");

        Components.onTheEventThread(() -> { field.setText("a"); return null; });

        assertTrue(Components.onTheEventThread(cross::isVisible), "the X is not shown beside typed text");

        layOut();

        int gap = Components.onTheEventThread(() -> SwingUtilities.convertPoint(field, 0, 0, panel.getFindingsBox()).y);

        assertTrue(gap >= LayoutEditor.HEADING_GAP, "the filter row has " + gap + " px above it");

        int right = Components.onTheEventThread(() -> SwingUtilities.convertPoint(cross, cross.getWidth(), 0, field).x);

        assertTrue(right <= field.getWidth(), "the X ends past the field's right end");

        Components.onTheEventThread(() -> { cross.doClick(); return null; });

        assertEquals(field.getText(), "", "the X did not clear the box");
    }

    // ------------------------------------------------------------------------------------------------ MT-890

    /**
     * A one-page list is eight rows tall by default, and a blue Station Tools heading with a heading's air above and
     * below sits between Path Type's radios and Bulk Edit....
     *
     * MUTATION: set `LayoutEditor.SIDEBAR_ROWS_AT_LEAST` back to 6, drop the heading, or put it under the button, and
     * this fails.
     */
    @Test(timeOut = 120000)
    public void testThePageListIsEightRowsAndStationToolsHeadsBulkEdit() throws Exception
    {
        assertEquals(LayoutEditor.SIDEBAR_ROWS_AT_LEAST, 8, "the page list's default rows");

        layOut();

        JLabel heading = Components.find(panel, JLabel.class, l -> I18n.t("autosetup.ui.headingStationTools").equals(l.getText()));
        AbstractButton bulk = StationDoors.part(panel, "autonomy.pickStations", AbstractButton.class);

        assertNotNull(heading, "no Station Tools heading");
        assertEquals(I18n.t("autosetup.ui.headingStationTools"), "Station Tools");
        assertEquals(heading.getForeground(), UiStyle.BLUE, "Station Tools is not a blue heading");
        assertEquals(heading.getFont(), UiStyle.LABEL_FONT);

        java.awt.Insets air = heading.getInsets();

        assertEquals(air.top, LayoutEditor.HEADING_GAP + 6, "the air above the heading");
        assertEquals(air.bottom, LayoutEditor.HEADING_GAP, "the air below the heading");

        int headingY = Components.onTheEventThread(() -> SwingUtilities.convertPoint(heading, 0, 0, panel).y);
        int bulkY = Components.onTheEventThread(() -> SwingUtilities.convertPoint(bulk, 0, 0, panel).y);
        int manualBottom = Components.onTheEventThread(() ->
        {
            Component manual = Components.withText(panel, I18n.t("autosetup.ui.pathTypeManual"));

            return manual == null ? 0 : SwingUtilities.convertPoint(manual, 0, manual.getHeight(), panel).y;
        });

        assertTrue(headingY < bulkY, "Station Tools is not above Bulk Edit...");
        assertTrue(headingY + heading.getHeight() <= bulkY, "Station Tools overlaps Bulk Edit...");
        assertTrue(manualBottom <= headingY, "Station Tools is not under Path Type's radios");
    }

    // ------------------------------------------------------------------------------------------------ OB-438

    /**
     * The column is headed Debugging Tools in every language, Path Type is a black item label, and the radios share
     * the left edge of the label and buttons around them.
     *
     * MUTATION: put `autosetup.ui.titleCap` back to Autonomy Tools, make Path Type `UiStyle.heading`, or take the zero
     * margin and border off the radios, and this fails.
     */
    @Test(timeOut = 120000)
    public void testDebuggingToolsAndPathTypeAreBlackAndAligned() throws Exception
    {
        assertEquals(I18n.t("autosetup.ui.titleCap"), "Debugging Tools");

        for (String language : new String[] {"da", "de", "es", "fr", "it", "nl", "pl"})
        {
            String title = java.util.ResourceBundle.getBundle("org.traincontrol.resources.messages",
                new java.util.Locale(language)).getString("autosetup.ui.titleCap");

            assertFalse(title.toLowerCase().contains("autonom"), language + " still calls the column Autonomy Tools: " + title);
        }

        layOut();

        JLabel pathType = Components.find(panel, JLabel.class, l -> I18n.t("autosetup.ui.labelPathType").equals(l.getText()));

        assertNotNull(pathType, "no Path Type label");
        assertEquals(pathType.getForeground(), java.awt.Color.BLACK, "Path Type is not black");
        assertEquals(pathType.getFont(), UiStyle.LABEL_FONT, "Path Type is not bold-weight like the item labels");

        int edge = left(pathType);

        assertEquals(left(radio("autosetup.ui.pathTypeAuto")), edge, "Auto is not flush with Path Type");
        assertEquals(left(radio("autosetup.ui.pathTypeManual")), edge, "Manual is not flush with Path Type");
        assertEquals(left(StationDoors.part(panel, "autonomy.pickStations", Component.class)), edge,
            "Bulk Edit... is not flush with the radios");

        Component test = Components.withText(panel, I18n.t("autosetup.ui.toolTest"));
        Component why = Components.withText(panel, I18n.t("autosetup.ui.toolWhy"));

        int radiosGap = top(radio("autosetup.ui.pathTypeManual"))
            - (top(radio("autosetup.ui.pathTypeAuto")) + Components.onTheEventThread(() -> radio("autosetup.ui.pathTypeAuto").getHeight()));
        int buttonsGap = top(why) - (top(test) + Components.onTheEventThread(test::getHeight));

        assertEquals(radiosGap, buttonsGap, "the radios are spaced differently from the buttons above them");
    }

    // ------------------------------------------------------------------------------------------------ MT-920

    /**
     * With Bulk Edit... down, every station that can be picked wears a visible gray outline (darker than the grid's
     * light gray) and a pick keeps its teal band; with it up, none does.
     *
     * MUTATION: color PICKABLE `Color.LIGHT_GRAY` again, or answer null from `toolMark` for the stations not picked, and
     * this fails.
     */
    @Test(timeOut = 120000)
    public void testBulkEditOutlinesEveryPickableStationInGray() throws Exception
    {
        List<TileKey> stations = stations();

        StationDoors.press(panel, "autonomy.pickStations");

        TileKey picked = stations.get(0);

        Components.onTheEventThread(() ->
        {
            panel.tileClicked(picked, session.getGraph().getTiles().get(picked), false);

            return null;
        });

        Components.settle();

        for (TileKey each : stations)
        {
            org.traincontrol.automationui.TileAnnotation.ToolMark mark =
                Components.onTheEventThread(() -> panel.annotationFor(each).getToolMark());

            assertEquals(mark, each.equals(picked) ? org.traincontrol.automationui.TileAnnotation.ToolMark.TOGETHER
                : org.traincontrol.automationui.TileAnnotation.ToolMark.PICKABLE, each + " while Bulk Edit... is down");
        }

        java.awt.image.BufferedImage tile = new java.awt.image.BufferedImage(60, 60, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = tile.createGraphics();

        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 60, 60);

        new org.traincontrol.automationui.TileAnnotation(java.util.Collections.<org.traincontrol.automationui.TileAnnotation.Mark>emptyList(),
            -1, false).markedByATool(org.traincontrol.automationui.TileAnnotation.ToolMark.PICKABLE).paint(g, 60, 60);
        g.dispose();

        int edge = tile.getRGB(1, 30) & 0xFFFFFF;

        assertTrue(edge != 0xFFFFFF && edge != (java.awt.Color.LIGHT_GRAY.getRGB() & 0xFFFFFF),
            "the outline is white or the grid's light gray: #" + Integer.toHexString(edge));
        assertEquals(edge, 0x808080, "the outline is not gray");

        StationDoors.press(panel, "autonomy.pickStations");

        for (TileKey each : stations)
        {
            assertNull(Components.onTheEventThread(() -> panel.annotationFor(each).getToolMark()), each + " outlined after the tool went down");
        }
    }

    // ------------------------------------------------------------------------------------------------ OB-437

    /**
     * Station Settings is the first item under the station's name, whether or not a train stands there; the train's
     * items come after it behind a divider.
     *
     * MUTATION: put the Settings item back after the Active and facing items in `EditorTileMenus.buildTileMenu`, and
     * this fails on the station with a train.
     */
    @Test(timeOut = 120000)
    public void testStationSettingsIsRightUnderTheNameWithOrWithoutATrain() throws Exception
    {
        List<TileKey> stations = stations();
        TileKey empty = stations.get(stations.size() - 1);
        TileKey occupied = stations.get(0);

        String train = support.StandingTrain.standingNowhere(frozen.model, session);

        support.StandingTrain.standOn(frozen.model, session, train, occupied);

        for (TileKey at : new TileKey[] {empty, occupied})
        {
            javax.swing.JPopupMenu menu = Components.onTheEventThread(() ->
                panel.buildTileMenu(at, session.getGraph().getTiles().get(at)));

            List<Component> parts = new ArrayList<>(java.util.Arrays.asList(menu.getComponents()));

            assertTrue(parts.get(0) instanceof AbstractButton || parts.get(0) instanceof JLabel
                || parts.get(0) instanceof javax.swing.JMenuItem, "the menu starts with something unexpected");

            assertTrue(parts.get(1) instanceof javax.swing.JSeparator, "the name is not followed by its divider");
            assertEquals(((javax.swing.JMenuItem) parts.get(2)).getText(), I18n.t("autosetup.ui.menuStationSettings"),
                "Station Settings is not the first item under the name at " + at + " (train " + (at == occupied) + ")");
            assertTrue(parts.get(3) instanceof javax.swing.JSeparator, "Settings is not followed by a divider");
        }
    }

    // ------------------------------------------------------------------------------------------------ OB-436

    /**
     * The guard rows are labeled Exit guard signal and Entry guard signal; a press anywhere in the window that takes no
     * typing applies the Maximum train length and the "Only trains up to" line follows at once; a blank field with
     * several stations picked writes nothing.
     *
     * MUTATION: put the labels back to Exit guard and Entry guard, drop `StationSettingsWindow.pressAway`, or have a
     * blank several-stations field write 0, and this fails.
     */
    @Test(timeOut = 180000)
    public void testGuardLabelsAndClickingAwayAppliesTheLength() throws Exception
    {
        TileKey square = stations().get(0);

        StationSettingsWindow window = StationDoors.open(panel, square);

        assertEquals(I18n.t("autosetup.ui.stationExitGuard"), "Exit guard signal");
        assertEquals(I18n.t("autosetup.ui.stationEntryGuard"), "Entry guard signal");
        assertNotNull(Components.find(window.getContentPane(), JLabel.class, l -> l.getText() != null && "Exit guard signal".equals(UiStyle.withoutColon(l.getText()))),
            "the window has no Exit guard signal label");
        assertNotNull(Components.find(window.getContentPane(), JLabel.class, l -> l.getText() != null && "Entry guard signal".equals(UiStyle.withoutColon(l.getText()))),
            "the window has no Entry guard signal label");

        JTextField field = StationDoors.part(window, "station.maxLength", JTextField.class);
        JLabel line = StationDoors.part(window, "station.matrixAlso", JLabel.class);

        Components.onTheEventThread(() -> { field.setText("37"); return null; });

        assertFalse(Components.onTheEventThread(line::getText).contains("37"), "typing alone updated the line");

        JLabel anywhere = Components.find(window.getContentPane(), JLabel.class,
            l -> I18n.t("autosetup.ui.stationMatrixHeading").equals(l.getText()));

        support.Mouse.press(anywhere, java.awt.event.MouseEvent.BUTTON1);

        StationDoors.current(window);

        assertEquals(((Number) session.getPointProperty(square, "maxTrainLength")).intValue(), 37, "the click away did not apply the length");
        assertTrue(Components.onTheEventThread(line::getText).contains("37"),
            "the Only trains up to line did not follow: " + Components.onTheEventThread(line::getText));

        StationDoors.close(window);

        // Several stations differing on the length show a blank field; a press away writes nothing to either
        List<TileKey> two = stations().subList(0, 2);

        Components.onTheEventThread(() ->
        {
            session.setPointProperty(two.get(0), "maxTrainLength", 11);
            session.setPointProperty(two.get(1), "maxTrainLength", 12);

            return null;
        });

        StationSettingsWindow several = bulkEdit(two);
        JTextField blank = StationDoors.part(several, "station.maxLength", JTextField.class);

        assertEquals(Components.onTheEventThread(blank::getText), "", "precondition: the differing length is not blank");

        support.Mouse.press(StationDoors.part(several, "station.several", JLabel.class), java.awt.event.MouseEvent.BUTTON1);

        StationDoors.current(several);

        assertEquals(String.valueOf(session.getPointProperty(two.get(0), "maxTrainLength")), "11", "a blank field wrote the first");
        assertEquals(String.valueOf(session.getPointProperty(two.get(1), "maxTrainLength")), "12", "a blank field wrote the second");
    }

    /**
     * A refused Maximum train length keeps its text, and a press away from it still does what it presses, the first and
     * every later one: the refusal rebuilds nothing under the press.
     *
     * MUTATION: have `StationSettingsWindow.commit` call `actOnEach` with `putBack` true, and this fails.
     */
    @Test(timeOut = 180000)
    public void testARefusedLengthLosesNoPressAwayFromIt() throws Exception
    {
        ArrivalBox pick = new ArrivalBox(false);
        final TileKey at = pick.square;
        final String side = pick.side;
        StationSettingsWindow window = pick.window;
        JTextField field = StationDoors.part(window, "station.maxLength", JTextField.class);

        Components.onTheEventThread(() -> { field.setText("abc"); return null; });

        for (int press = 1; press <= 2; press++)
        {
            boolean barred = barred(at, side);

            pressAndRelease(window, "station.arrive." + side);

            StationDoors.current(window);

            assertEquals(barred(at, side), !barred, "press " + press + " on the arrive-from box was lost");
        }

        assertEquals(Components.onTheEventThread(field::getText), "abc", "the refused length lost its text");
    }

    /**
     * A length that is taken applies on the press away from it, and the press still does what it presses: an
     * arrive-from box toggles, and a May depart list opens without an error.  The window updates its boxes and lists in
     * place, so none is rebuilt under the pointer.
     *
     * MUTATION: have `StationSettingsWindow.fillArrivals` rebuild its boxes on every reload, and the box does not toggle;
     * have `fillDepart` rebuild its lists on every reload, and the list does not open.
     */
    @Test(timeOut = 180000)
    public void testATakenLengthLosesNoPressAwayFromIt() throws Exception
    {
        ArrivalBox pick = new ArrivalBox(true);
        final TileKey at = pick.square;
        final String side = pick.side;
        final StationSettingsWindow open = pick.window, window = open;
        JTextField field = StationDoors.part(window, "station.maxLength", JTextField.class);

        boolean barred = barred(at, side);

        Components.onTheEventThread(() -> { field.setText("300"); return null; });

        pressAndRelease(window, "station.arrive." + side);

        StationDoors.current(window);

        assertEquals(((Number) session.getPointProperty(at, "maxTrainLength")).intValue(), 300, "the press away did not apply the length");
        assertEquals(barred(at, side), !barred, "the press on the arrive-from box was lost");

        // A press on May depart opens its list
        Components.onTheEventThread(() -> { field.setText("301"); return null; });

        java.util.List<Throwable> thrown = new java.util.concurrent.CopyOnWriteArrayList<>();
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> thrown.add(e));

        try
        {
            support.Mouse.press(StationDoors.part(window, "station.depart.0", javax.swing.JComboBox.class), java.awt.event.MouseEvent.BUTTON1);
            Components.settle();

            assertTrue(thrown.isEmpty(), "the press on May depart threw " + thrown);
            assertTrue(Components.onTheEventThread(() -> StationDoors.part(open, "station.depart.0", javax.swing.JComboBox.class).isPopupVisible()),
                "the press on May depart did not open its list");
        }
        finally
        {
            Thread.setDefaultUncaughtExceptionHandler(before);
            support.Mouse.release(StationDoors.part(window, "station.depart.0", javax.swing.JComboBox.class), java.awt.event.MouseEvent.BUTTON1);
            Components.onTheEventThread(() -> { StationDoors.part(open, "station.depart.0", javax.swing.JComboBox.class).hidePopup(); return null; });
        }

        assertEquals(((Number) session.getPointProperty(at, "maxTrainLength")).intValue(), 301, "the press on May depart did not apply the length");
    }

    /** Station Settings open on the first station with an arrival side to shut, and a live May depart list if asked. */
    private final class ArrivalBox
    {
        TileKey square;
        StationSettingsWindow window;
        String side;

        ArrivalBox(boolean depart)
        {
            for (TileKey each : stations())
            {
                window = StationDoors.open(panel, each);

                javax.swing.JCheckBox box = Components.find(window, javax.swing.JCheckBox.class,
                    b -> b.getName() != null && b.getName().startsWith("station.arrive.") && b.isEnabled());

                if (box == null || depart && !StationDoors.live(window, "station.depart.0")) continue;

                square = each;
                side = box.getName().substring("station.arrive.".length());

                return;
            }

            throw new AssertionError("precondition: no station has an arrival side to shut"
                + (depart ? " and a May depart list" : ""));
        }
    }

    /** Whether the station bars arrivals from the side. */
    private boolean barred(TileKey at, String side)
    {
        return Components.onTheEventThread(() -> session.getBarredArrivals(at).contains(org.traincontrol.automationui.TilePorts.Side.valueOf(side)));
    }

    /**
     * A press on a named control, then its release on the control of that name at the pointer by then: AWT forgets a
     * press target removed from the window, so a control rebuilt under the press gets a release it never saw pressed.
     */
    private static void pressAndRelease(Component window, String name)
    {
        support.Mouse.press(StationDoors.part(window, name, Component.class), java.awt.event.MouseEvent.BUTTON1);
        Components.settle();
        support.Mouse.release(StationDoors.part(window, name, Component.class), java.awt.event.MouseEvent.BUTTON1);
    }

    /**
     * Bulk Edit shows the Settings tab, whichever tab one station's window was left on (FR-195).
     *
     * MUTATION: drop the `showTab(false)` in `AutonomyEditorPanel.pickStation`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testBulkEditShowsTheSettingsTab() throws Exception
    {
        TileKey station = stations().get(0);

        StationDoors.openTrain(panel, station);

        assertTrue(StationDoors.showsTheTrainTab(StationDoors.showing(StationSettingsWindow.class)), "precondition: the Train tab is not shown");

        StationSettingsWindow window = bulkEdit(java.util.Collections.singletonList(station));

        assertFalse(StationDoors.showsTheTrainTab(window), "Bulk Edit left the Train tab showing");
    }

    /** Arms Bulk Edit and clicks each square; the window that opens. */
    private StationSettingsWindow bulkEdit(List<TileKey> squares) throws Exception
    {
        StationDoors.press(panel, "autonomy.pickStations");

        for (TileKey each : squares)
        {
            Components.onTheEventThread(() -> { panel.tileClicked(each, session.getGraph().getTiles().get(each), false); return null; });
            Components.settle();
        }

        StationSettingsWindow window = StationDoors.showing(StationSettingsWindow.class);

        assertNotNull(window, "Bulk Edit opened no window");

        StationDoors.current(window);

        return window;
    }

    // ------------------------------------------------------------------------------------------------ MT-948

    /**
     * A guard pick waiting for a click on the diagram ends when Station Settings closes, by any route: a click on a
     * signal afterward neither pairs it nor brings the guard window back.
     *
     * MUTATION: drop the `endGuardPick` call in `StationWindows.closed`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testClosingStationSettingsEndsAGuardPickWaitingForAClick() throws Exception
    {
        for (String route : new String[] {"close box", "Escape", "Close button", "editor closes"})
        {
            TileKey station = stations().get(0);
            StationSettingsWindow window = StationDoors.open(panel, station);

            AbstractButton pencil = StationDoors.part(window, "station.exitGuardChange", AbstractButton.class);
            JDialog guard = openTheGuardWindow(pencil);

            Components.onTheEventThread(() ->
            {
                AbstractButton byClick = Components.withText(guard, I18n.t("autosetup.ui.optionClickSignal"));

                byClick.doClick();

                return null;
            });

            Components.settle();

            assertTrue(Components.onTheEventThread(panel::isFocusedOnSignals), route + ": precondition: no click is awaited");

            int before = session.getProtectingSignals(station).size();

            closeBy(route, window);

            Components.settle();

            assertFalse(Components.onTheEventThread(panel::isFocusedOnSignals), route + ": the pick is still awaiting its click");

            if (route.equals("editor closes"))
            {
                editor = frozen.editorOn(PAGE);
                panel = Components.onTheEventThread(() -> editor.getAutonomyPanel());
            }

            TileKey signal = aSignal();

            SwingUtilities.invokeLater(() -> panel.tileClicked(signal, session.getGraph().getTiles().get(signal), false));

            Components.settle();
            Thread.sleep(300);

            assertNull(StationDoors.guardWindow(), route + ": the guard window came back after Station Settings closed");
            assertEquals(session.getProtectingSignals(station).size(), before, route + ": the click paired a signal");

            support.AskingWindows.closeEveryDialog();
        }
    }

    /**
     * The guard window itself closes with Station Settings, which it hangs off.
     *
     * MUTATION: have `AutonomyEditorPanel.owner` answer the editor's window instead of the asking window, and this
     * fails; it also passes on the code before MT-948, which only left the pick waiting.
     */
    @Test(timeOut = 180000)
    public void testClosingStationSettingsClosesTheOpenGuardWindow() throws Exception
    {
        TileKey station = stations().get(0);
        StationSettingsWindow window = StationDoors.open(panel, station);

        JDialog guard = openTheGuardWindow(StationDoors.part(window, "station.entryGuardChange", AbstractButton.class));

        assertNotNull(guard);

        closeBy("close box", window);

        assertTrue(Components.waitFor(() -> StationDoors.guardWindow() == null, 10000), "the guard window stayed open");
    }

    /**
     * Station Settings first opens off the diagram (MT-948): right of the editor where the screen has room, else inside
     * the editor's right edge from its right-hand column's top; on the screen; and it stays where the user put it when
     * another station is opened.
     *
     * MUTATION: put `AutonomyEditorPanel.besideOwner` back in `StationWindowBase.aim`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testStationSettingsOpensBesideTheDiagram() throws Exception
    {
        java.awt.Rectangle screen = Components.onTheEventThread(() -> AutonomyEditorPanel.screenOf(editor));

        StationSettingsWindow window = StationDoors.open(panel, stations().get(0));
        java.awt.Dimension size = Components.onTheEventThread(window::getSize);

        StationDoors.close(window);

        // Room on the right
        java.awt.Rectangle roomy = new java.awt.Rectangle(screen.x, screen.y, screen.width - size.width - 40, 700);

        java.awt.Rectangle at = boundsOfAFreshWindow(roomy);

        assertEquals(at.x, roomy.x + roomy.width, "Station Settings is not right of the editor: " + at + ", editor " + roomy);
        assertEquals(at.y, roomy.y, "Station Settings is not level with the editor's top: " + at);

        // No room: over the right-hand column, inside the editor's right edge
        java.awt.Rectangle full = new java.awt.Rectangle(screen.x, screen.y, screen.width, screen.height);

        at = boundsOfAFreshWindow(full);

        int column = Components.onTheEventThread(() -> SwingUtilities.convertPoint(panel, 0, 0, editor).y) + full.y;

        assertEquals(at.x + at.width, full.x + full.width, "Station Settings does not end at the editor's right edge: " + at);
        assertEquals(at.y, Math.min(column, screen.y + screen.height - at.height), "Station Settings is not at the column's top: " + at);
        assertTrue(screen.contains(at), "Station Settings is off the screen: " + at + " on " + screen);

        // Moved by the user, it stays there for the next station
        StationSettingsWindow open = StationDoors.showing(StationSettingsWindow.class);

        Components.onTheEventThread(() -> { open.setLocation(screen.x + 30, screen.y + 30); return null; });

        StationDoors.open(panel, stations().get(1));

        assertEquals(Components.onTheEventThread(open::getLocation), new java.awt.Point(screen.x + 30, screen.y + 30),
            "Station Settings moved when another station was opened in it");
    }

    /**
     * The guard window opens under Station Settings, touching its bottom edge and left-aligned, covering none of it;
     * where the screen has no room below, beside it at its top (MT-948).
     *
     * MUTATION: have `EditorSignals.place` always use `besideOwner`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheGuardWindowOpensUnderStationSettings() throws Exception
    {
        java.awt.Rectangle screen = Components.onTheEventThread(() -> AutonomyEditorPanel.screenOf(editor));
        StationSettingsWindow window = StationDoors.open(panel, stations().get(0));

        Components.onTheEventThread(() -> { window.setLocation(screen.x + 40, screen.y + 20); return null; });

        java.awt.Rectangle settings = Components.onTheEventThread(window::getBounds);
        JDialog guard = openTheGuardWindow(StationDoors.part(window, "station.exitGuardChange", AbstractButton.class));
        java.awt.Rectangle under = Components.onTheEventThread(guard::getBounds);

        assertEquals(under.getLocation(), new java.awt.Point(settings.x, settings.y + settings.height),
            "the guard window is not under Station Settings: " + under + " under " + settings);

        Components.onTheEventThread(() -> { guard.dispose(); return null; });
        assertTrue(Components.waitFor(() -> StationDoors.guardWindow() == null, 10000), "precondition: the guard window stayed");

        // Near the screen's bottom: beside it
        Components.onTheEventThread(() -> { window.setLocation(screen.x + 40, screen.y + screen.height - settings.height - 10); return null; });

        java.awt.Rectangle low = Components.onTheEventThread(window::getBounds);
        JDialog again = openTheGuardWindow(StationDoors.part(window, "station.exitGuardChange", AbstractButton.class));
        java.awt.Rectangle beside = Components.onTheEventThread(again::getBounds);

        assertFalse(beside.intersects(low), "the guard window covers Station Settings: " + beside + " over " + low);
        assertEquals(beside.y, low.y, "the guard window is not beside Station Settings at its top: " + beside);
    }

    /**
     * The guard window is no wider than its buttons, at most `GUARD_WIDTH` and its border, its sentence wrapping; each
     * row has a red X at its right end, Remove as its tooltip, which removes that signal; Remove Selected is gone
     * (MT-948).
     *
     * MUTATION: drop the `remove.accept` in `EditorSignals.RemovableList`'s click, and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheGuardWindowIsNarrowAndEachRowHasARemovingX() throws Exception
    {
        TileKey station = stations().get(0);
        List<TileKey> signals = signals(2);

        Components.onTheEventThread(() -> { session.setProtectingSignals(station, signals); return null; });

        StationSettingsWindow window = StationDoors.open(panel, station);
        JDialog guard = openTheGuardWindow(StationDoors.part(window, "station.exitGuardChange", AbstractButton.class));

        int width = Components.onTheEventThread(guard::getWidth);

        assertTrue(width <= 480, "the guard window is " + width + " pixels wide");
        assertNull(Components.withText(guard, "Remove Selected"), "Remove Selected is still there");

        javax.swing.JList<?> list = Components.find(guard, javax.swing.JList.class, null);
        java.awt.Point cross = Components.onTheEventThread(() ->
        {
            java.awt.Rectangle row = list.getCellBounds(0, 0);

            return new java.awt.Point(row.x + row.width - 10, row.y + row.height / 2);
        });

        assertEquals(Components.onTheEventThread(() -> list.getToolTipText(mouse(list, java.awt.event.MouseEvent.MOUSE_MOVED, cross))),
            I18n.t("autosetup.ui.optionRemoveSignal"), "the X's tooltip");
        assertNull(Components.onTheEventThread(() -> list.getToolTipText(mouse(list, java.awt.event.MouseEvent.MOUSE_MOVED,
            new java.awt.Point(8, cross.y)))), "the row's text has the X's tooltip");

        for (int kind : new int[] {java.awt.event.MouseEvent.MOUSE_PRESSED, java.awt.event.MouseEvent.MOUSE_RELEASED,
            java.awt.event.MouseEvent.MOUSE_CLICKED})
        {
            Components.onTheEventThread(() -> { list.dispatchEvent(mouse(list, kind, cross)); return null; });
        }

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> session.getProtectingSignals(station))
            .equals(signals.subList(1, 2)), 10000), "the X did not remove its row's signal: "
            + Components.onTheEventThread(() -> session.getProtectingSignals(station)));
        assertEquals((int) Components.onTheEventThread(() -> list.getModel().getSize()), 1, "the list did not follow");
    }

    /**
     * A double click on the first row's X removes that signal alone, not the next row's that comes up under the second
     * click; a right click on an X removes nothing; Delete and Backspace remove the row chosen.
     *
     * MUTATION: have `RemovableList` take any button and any click count on an X, and the right click takes one; drop
     * its Delete binding, and the keys take one too few.
     */
    @Test(timeOut = 180000)
    public void testTheXTakesOneSignalPerClickAndTheKeysTakeTheChosenOne() throws Exception
    {
        TileKey station = stations().get(0);
        List<TileKey> signals = signals(4);

        assertEquals(signals.size(), 4, "precondition: the page has four signals");

        Components.onTheEventThread(() -> { session.setProtectingSignals(station, signals); return null; });

        StationSettingsWindow window = StationDoors.open(panel, station);
        JDialog guard = openTheGuardWindow(StationDoors.part(window, "station.exitGuardChange", AbstractButton.class));
        javax.swing.JList<?> list = Components.find(guard, javax.swing.JList.class, null);
        java.awt.Point cross = Components.onTheEventThread(() ->
        {
            java.awt.Rectangle row = list.getCellBounds(0, 0);

            return new java.awt.Point(row.x + row.width - 10, row.y + row.height / 2);
        });

        // A right click
        clickAt(list, cross, java.awt.event.MouseEvent.BUTTON3, 1);

        assertEquals(Components.onTheEventThread(() -> session.getProtectingSignals(station)), signals, "a right click removed a signal");

        // A double click
        clickAt(list, cross, java.awt.event.MouseEvent.BUTTON1, 1);
        clickAt(list, cross, java.awt.event.MouseEvent.BUTTON1, 2);

        assertEquals(Components.onTheEventThread(() -> session.getProtectingSignals(station)), signals.subList(1, 4),
            "a double click on the first X did not remove that signal alone");

        // The keys, on the row chosen
        int[] keys = {java.awt.event.KeyEvent.VK_DELETE, java.awt.event.KeyEvent.VK_BACK_SPACE};

        for (int k = 0; k < keys.length; k++)
        {
            final int chosen = k;

            Components.onTheEventThread(() -> { list.setSelectedIndex(1 - chosen); return null; });

            support.Keys.press(list, keys[k]);
        }

        // Delete on the second row (the third signal), then Backspace on the first (the second)
        assertEquals(Components.onTheEventThread(() -> session.getProtectingSignals(station)), signals.subList(3, 4),
            "Delete and Backspace did not each remove the row chosen");
    }

    /** Press, release and click with a button, as the n-th click of a run, dispatched where AWT would send them. */
    private static void clickAt(Component on, java.awt.Point at, int button, int count)
    {
        int down = button == java.awt.event.MouseEvent.BUTTON3 ? java.awt.event.InputEvent.BUTTON3_DOWN_MASK : java.awt.event.InputEvent.BUTTON1_DOWN_MASK;

        for (int kind : new int[] {java.awt.event.MouseEvent.MOUSE_PRESSED, java.awt.event.MouseEvent.MOUSE_RELEASED,
            java.awt.event.MouseEvent.MOUSE_CLICKED})
        {
            Components.onTheEventThread(() ->
            {
                on.dispatchEvent(new java.awt.event.MouseEvent(on, kind, System.currentTimeMillis(),
                    kind == java.awt.event.MouseEvent.MOUSE_PRESSED ? down : 0, at.x, at.y, count, button == java.awt.event.MouseEvent.BUTTON3
                    && kind == java.awt.event.MouseEvent.MOUSE_PRESSED, button));

                return null;
            });
        }
    }

    // ------------------------------------------------------------------------------------------------ OB-441

    /**
     * Where a setting leaves a train at the station unable to leave, the setup check's own sentence stands in red under
     * the picture, and goes when the setting is put right: TopR1ParkLong, at the end of its line, made a plain station
     * and then a terminus again.
     *
     * MUTATION: pass null for `stuck` to `StationMatrixPanel.show` in `StationSettingsWindow.askTheRailway`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testAStationTrainsCannotLeaveSaysSoUnderThePicture() throws Exception
    {
        TileKey square = named("TopR1ParkLong");
        StationSettingsWindow window = StationDoors.open(panel, square);
        JLabel problem = StationDoors.part(window, "station.matrixProblem", JLabel.class);

        StationDoors.press(window, "station.preset.STATION");
        StationDoors.current(window);

        String said = I18n.f("autosetup.ui.checkStationAtTheEndOfALine", Components.onTheEventThread(() -> session.describeTile(square)))
            .replaceAll("\\s+", " ");

        assertTrue(Components.onTheEventThread(problem::isVisible), "no warning under the picture for a station trains cannot leave");
        assertTrue(StationDoors.said(window, "station.matrixProblem").contains(said), "the warning is not the setup check's: "
            + StationDoors.said(window, "station.matrixProblem"));

        StationDoors.press(window, "station.preset.TERMINUS");
        StationDoors.current(window);

        assertFalse(Components.onTheEventThread(problem::isVisible), "the warning stayed once trains could leave");
    }

    /**
     * The picture draws a way in only where the editor's arrivals view does: on every arrival side of a station, and
     * none on a square that is no station.
     *
     * MUTATION: put back `session.arrivalSides(showing)` as the test in `StationPicture.stationMarks`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testThePictureDrawsTheEditorsWaysIn() throws Exception
    {
        TileKey plain = Components.onTheEventThread(() ->
        {
            for (TileKey k : session.getReducer().getPoints().keySet())
            {
                if (PAGE.equals(k.getPage()) && !session.getStore().isStation(k) && !session.arrivalSides(k).isEmpty()) return k;
            }

            return null;
        });

        assertNotNull(plain, "precondition: no plain sensor with ways in on " + PAGE);

        for (TileKey square : new TileKey[] {plain, named("TopR1ParkLong")})
        {
            StationSettingsWindow window = StationDoors.open(panel, square);

            java.util.Set<String> drawn = new java.util.TreeSet<>();

            for (String mark : Components.onTheEventThread(window::pictureStateForTest))
            {
                if (mark.startsWith("IN.")) drawn.add(mark.substring(3, 4));
            }

            java.util.Set<String> editors = new java.util.TreeSet<>();

            for (org.traincontrol.automationui.TileAnnotation.Arrival arrival : Components.onTheEventThread(() -> session.arrivalMarks(square, true)))
            {
                editors.add(arrival.getSide().name());
            }

            assertEquals(drawn, editors, "the picture's ways in at " + square + " are not the editor's");
        }
    }

    private static TileKey named(String name)
    {
        return Components.onTheEventThread(() ->
        {
            for (TileKey k : session.getReducer().getPoints().keySet())
            {
                if (name.equals(session.getStore().getPointName(k))) return k;
            }

            throw new AssertionError("precondition: no " + name);
        });
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static java.awt.event.MouseEvent mouse(Component on, int kind, java.awt.Point at)
    {
        return new java.awt.event.MouseEvent(on, kind, System.currentTimeMillis(),
            kind == java.awt.event.MouseEvent.MOUSE_PRESSED ? java.awt.event.InputEvent.BUTTON1_DOWN_MASK : 0,
            at.x, at.y, 1, false, kind == java.awt.event.MouseEvent.MOUSE_MOVED ? 0 : java.awt.event.MouseEvent.BUTTON1);
    }

    /** Station Settings' bounds as it first opens over an editor with these bounds. */
    private java.awt.Rectangle boundsOfAFreshWindow(java.awt.Rectangle bounds) throws Exception
    {
        support.AskingWindows.closeEveryDialog();

        Components.onTheEventThread(() -> { editor.setBounds(bounds); editor.validate(); return null; });

        StationSettingsWindow window = StationDoors.open(panel, stations().get(0));

        return Components.onTheEventThread(window::getBounds);
    }

    private static List<TileKey> signals(int count)
    {
        return Components.onTheEventThread(() ->
        {
            List<TileKey> out = new ArrayList<>();

            for (java.util.Map.Entry<TileKey, org.traincontrol.base.LayoutDiagramComponent> each : session.getGraph().getTiles().entrySet())
            {
                if (out.size() < count && each.getValue().getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.SIGNAL
                    && each.getValue().getAccessory() != null && PAGE.equals(each.getKey().getPage())) out.add(each.getKey());
            }

            return out;
        });
    }

    private void closeBy(String route, StationSettingsWindow window) throws Exception
    {
        switch (route)
        {
            case "close box":
                Components.onTheEventThread(() ->
                {
                    window.dispatchEvent(new java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING));

                    return null;
                });
                break;
            case "Escape":
                support.Keys.press(window.getRootPane(), java.awt.event.KeyEvent.VK_ESCAPE);
                break;
            case "Close button":
                StationDoors.close(window);
                break;
            default:
                support.FrozenEditor.dispose(editor);
        }
    }

    private JDialog openTheGuardWindow(AbstractButton pencil) throws Exception
    {
        Components.press(pencil);

        assertTrue(Components.waitFor(() -> StationDoors.guardWindow() != null, 10000), "the guard window did not open");

        return StationDoors.guardWindow();
    }

    private static TileKey aSignal()
    {
        return Components.onTheEventThread(() ->
        {
            for (java.util.Map.Entry<TileKey, org.traincontrol.base.LayoutDiagramComponent> each : session.getGraph().getTiles().entrySet())
            {
                if (each.getValue().isSignal() && PAGE.equals(each.getKey().getPage())) return each.getKey();
            }

            return null;
        });
    }

    private static List<TileKey> stations()
    {
        return Components.onTheEventThread(() ->
        {
            List<TileKey> out = new ArrayList<>();

            for (TileKey k : session.getReducer().getPoints().keySet())
            {
                if (session.getStore().isStation(k) && PAGE.equals(k.getPage())) out.add(k);
            }

            return out;
        });
    }

    private void layOut()
    {
        Components.onTheEventThread(() ->
        {
            editor.getContentPane().setSize(1500, 870);
            layoutAll(editor.getContentPane());

            return null;
        });
    }

    private Component radio(String key)
    {
        return Components.withText(panel, I18n.t(key));
    }

    private static int left(Component c)
    {
        return Components.onTheEventThread(() -> SwingUtilities.convertPoint(c, 0, 0, SwingUtilities.getRootPane(c)).x);
    }

    private static int top(Component c)
    {
        return Components.onTheEventThread(() -> SwingUtilities.convertPoint(c, 0, 0, SwingUtilities.getRootPane(c)).y);
    }

    private static void layoutAll(Component c)
    {
        if (c instanceof Container)
        {
            c.doLayout();

            for (Component k : ((Container) c).getComponents()) layoutAll(k);
        }
    }
}
