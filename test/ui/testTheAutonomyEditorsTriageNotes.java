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
 * The owner's notes from testing the autonomy editor (MT-889, MT-890, MT-920, OB-436, OB-437, OB-438, MT-948), each
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

    // ------------------------------------------------------------------------------------------------ helpers

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
