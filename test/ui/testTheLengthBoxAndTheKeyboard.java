package ui;

import javax.swing.JSpinner;
import javax.swing.JTextField;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.autonomyeditor.StationSettingsWindow;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;
import support.Components;
import support.StationDoors;

/**
 * Station Settings' length box and the keyboard (FR-234): its focus ring, a field still being typed in, and Control+Enter
 * (FR-200).  They ask which component has the keyboard, which belongs to the whole desktop, so the class runs in the
 * serial lane.
 *
 * @author Adam
 */
public class testTheLengthBoxAndTheKeyboard
{
    private static support.FrozenEditor frozen;
    private static LayoutEditor editor;
    private static StationSettingsWindow window;
    private static TileKey station;

    /** One window on a plain named station, for every claim: only the last, which closes it, writes the draft. */
    @BeforeClass
    public static void openTheWindow() throws Exception
    {
        frozen = support.FrozenEditor.open();
        editor = frozen.editorOn("1 - Main");

        AutonomySession session = frozen.session;
        station = Components.onTheEventThread(() -> session.getReducer().getPoints().keySet().stream()
            .filter(each -> "1 - Main".equals(each.getPage()) && session.getStore().isStation(each)
                && !session.isTurnAround(each) && session.getStore().getPointName(each) != null)
            .findFirst().orElseThrow(() -> new AssertionError("precondition: no plain named station")));

        window = StationDoors.open(Components.onTheEventThread(editor::getAutonomyPanel), station);
    }

    @AfterClass(alwaysRun = true)
    public static void closeTheWindow() throws Exception
    {
        try
        {
            if (window != null) StationDoors.close(window);

            support.FrozenEditor.dispose(editor);
        }
        finally
        {
            if (frozen != null) frozen.close();
        }
    }

    /**
     * The length box wears FlatLaf's focus ring while its field has the keyboard, and not while Name has it: FlatLaf
     * rings a spinner only for its own kind of editor.
     *
     * MUTATION: drop the "JComponent.focusOwner" client property in `StationSettingsWindow.lengthArrows`, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testTheLengthBoxRingsWhileItHasTheKeyboard() throws Exception
    {
        JSpinner box = StationDoors.part(window, "station.maxLengthArrows", JSpinner.class);

        giveTheKeyboard("station.maxLength");

        assertTrue(Components.onTheEventThread(() -> com.formdev.flatlaf.ui.FlatSpinnerUI.isPermanentFocusOwner(box)),
            "the length box wears no focus ring while its field has the keyboard");

        giveTheKeyboard("station.name");

        assertFalse(Components.onTheEventThread(() -> com.formdev.flatlaf.ui.FlatSpinnerUI.isPermanentFocusOwner(box)),
            "the length box wears the focus ring while Name has the keyboard");
    }

    /**
     * A press away from a field still being typed in applies it but leaves its text as typed, a trailing space too, so
     * the typing goes on where it was; Enter then shows it as stored.
     *
     * MUTATION: have `StationSettingsWindow.commit` put the stored text back whatever holds the keyboard, and the press
     * away trims the name being typed.
     */
    @Test(timeOut = 120000)
    public void testAPressAwayLeavesANameBeingTyped() throws Exception
    {
        JTextField name = giveTheKeyboard("station.name");
        String stored = Components.onTheEventThread(name::getText);

        Components.onTheEventThread(() -> { name.setText(stored + " "); return null; });

        support.Mouse.press(Components.find(window.getContentPane(), javax.swing.JLabel.class,
            l -> I18n.t("autosetup.ui.stationMatrixHeading").equals(l.getText())), java.awt.event.MouseEvent.BUTTON1);
        StationDoors.current(window);

        assertTrue(Components.onTheEventThread(name::isFocusOwner), "precondition: the press took the keyboard from Name");
        assertEquals(Components.onTheEventThread(name::getText), stored + " ", "a press away trimmed the name being typed");

        Components.onTheEventThread(() -> { name.postActionEvent(); return null; });
        StationDoors.current(window);

        assertEquals(Components.onTheEventThread(name::getText), stored, "Enter did not show the name as stored");
    }

    /**
     * Control+Enter in the length box is Done Editing (FR-200): the length typed, with no Enter of its own, is applied
     * and the window closes.
     *
     * MUTATION: drop the Control+Enter binding from `StationWindowBase`, and nothing is applied.
     */
    @Test(timeOut = 120000, priority = 1)
    public void testControlEnterIsDoneEditing() throws Exception
    {
        JTextField length = giveTheKeyboard("station.maxLength");

        Components.onTheEventThread(() -> { length.setText("43"); return null; });

        support.Keys.chord(length, java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK);

        assertEquals(Double.parseDouble(String.valueOf(frozen.session.getPointProperty(station, "maxTrainLength"))), 43.0,
            "Control+Enter did not apply the length typed");

        assertFalse(Components.onTheEventThread(window::isShowing), "Control+Enter left the window open");

        window = null;
    }

    /** Hands a named field the keyboard, the window brought to the front for it; the field. */
    private static JTextField giveTheKeyboard(String fieldName)
    {
        JTextField field = StationDoors.part(window, fieldName, JTextField.class);

        Components.onTheEventThread(() -> { window.toFront(); field.requestFocus(); return null; });

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(field::isFocusOwner), 10000),
            "precondition: " + fieldName + " never took the keyboard");

        Components.settle();

        return field;
    }
}
