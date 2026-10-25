package ui;

import java.awt.Frame;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.LayoutEditorAddressPopup;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Mouse;

/**
 * The track diagram editor, driven as the user drives it: the Edit button, the pointer over a square, the keys, the
 * right-click menu, clicks and drags, and the answers given to its dialogs (FR-136 Phase 6).
 *
 * Each claim reads its outcome from one of three places: the page file written by Save and read back by a fresh
 * parser, the editor's undo stack as its own menu shows it, or the page the window holds after the editor closes.
 *
 * ON THE SINGLE-SWITCH RAILWAY, in a sandbox.  The page is one row of track with a turnout at 5,3 (see the scenario's
 * README).  Every claim reads the square it is about from the file before it acts, and the claims that save each work
 * on their own square, so they do not depend on the order they run in.
 */
public class testTheTrackEditorByHand
{
    private static final String PAGE = "1 - Junction";

    /** The turnout, accessory 1 */
    private static final int SWITCH_X = 5, SWITCH_Y = 3;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static boolean lastEditorWasAutonomy;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the editor is a window, and its squares take the mouse");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        // The Edit button opens whichever editor was used last; these claims are about the track editor.  Set, and put
        // back, in this run's own preference node.
        lastEditorWasAutonomy = TrainControlUI.getPrefs().getBoolean(TrainControlUI.LAST_EDITOR_AUTONOMY_PREF, false);

        TrainControlUI.getPrefs().putBoolean(TrainControlUI.LAST_EDITOR_AUTONOMY_PREF, false);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.LAST_EDITOR_AUTONOMY_PREF, lastEditorWasAutonomy);

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

    /**
     * An editor a failed claim left open is closed without saving, so the next claim opens its own.
     */
    @AfterMethod(alwaysRun = true)
    public void closeWhatIsLeft() throws Exception
    {
        Mouse.closeMenus();

        Components.onTheEventThread(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof JDialog && window.isShowing()) window.dispose();
            }

            return null;
        });

        final LayoutEditor left = editorOpen();

        if (left != null)
        {
            try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
            {
                Components.press(Components.withText(left, I18n.t("ui.cancel")));

                Components.waitFor(() -> !left.isDisplayable(), 10000);
            }
        }

        Components.waitFor(this::editButtonIsBack, 30000);
    }

    // ---------------------------------------------------------------- the keys

    /**
     * Control+R over a square turns it a quarter, and Save writes the new heading.
     *
     * MUTATION: drop Control+R's `rotate` call and the square is not turned.
     */
    @Test
    public void testControlRTurnsTheSquareUnderThePointer() throws Exception
    {
        int was = saved().getComponent(SWITCH_X, SWITCH_Y).getOrientation();
        int turns = saved().getComponent(SWITCH_X, SWITCH_Y).getNumOrientations();

        LayoutEditor editor = open();

        Mouse.enter(square(editor, SWITCH_X, SWITCH_Y));

        key(editor, KeyEvent.VK_R, true);

        assertEquals(editor.getMarklinLayout().getComponent(SWITCH_X, SWITCH_Y).getOrientation(), (was + 1) % turns,
            "Control+R did not turn the turnout under the pointer");

        save(editor);

        assertEquals(saved().getComponent(SWITCH_X, SWITCH_Y).getOrientation(), (was + 1) % turns,
            "the turn was not in the page file after Save");
    }

    /**
     * Control+Z takes the last edit back and Control+Y does it again, and the editor's own menu shows the stack: Undo
     * on after an edit, Redo on after an undo.  Discarded, the file is as it was.
     *
     * MUTATION: make Control+Z call `redo`, or Control+Y call `undo`, and the heading is wrong after the key; leave the
     * menu's Undo always on and the untouched editor offers it.
     */
    @Test
    public void testControlZTakesItBackAndControlYDoesItAgain() throws Exception
    {
        int was = saved().getComponent(SWITCH_X, SWITCH_Y).getOrientation();

        LayoutEditor editor = open();

        assertFalse(enabled(editor, SWITCH_X, SWITCH_Y, I18n.t("ui.undo")),
            "Undo was offered by an editor nobody had touched");

        Mouse.enter(square(editor, SWITCH_X, SWITCH_Y));

        key(editor, KeyEvent.VK_R, true);

        int turned = editor.getMarklinLayout().getComponent(SWITCH_X, SWITCH_Y).getOrientation();

        assertNotEquals(turned, was, "Control+R did nothing, so the undo below has nothing to undo");

        assertTrue(enabled(editor, SWITCH_X, SWITCH_Y, I18n.t("ui.undo")), "after an edit the menu offers no Undo");
        assertFalse(enabled(editor, SWITCH_X, SWITCH_Y, I18n.t("ui.redo")), "Redo was offered with nothing undone");

        key(editor, KeyEvent.VK_Z, true);

        assertEquals(editor.getMarklinLayout().getComponent(SWITCH_X, SWITCH_Y).getOrientation(), was,
            "Control+Z did not put the heading back");

        assertFalse(enabled(editor, SWITCH_X, SWITCH_Y, I18n.t("ui.undo")), "the one edit was undone, yet Undo is on");
        assertTrue(enabled(editor, SWITCH_X, SWITCH_Y, I18n.t("ui.redo")), "after an undo the menu offers no Redo");

        key(editor, KeyEvent.VK_Y, true);

        assertEquals(editor.getMarklinLayout().getComponent(SWITCH_X, SWITCH_Y).getOrientation(), turned,
            "Control+Y did not do the turn again");

        discard(editor);

        assertEquals(saved().getComponent(SWITCH_X, SWITCH_Y).getOrientation(), was,
            "a discarded edit reached the page file");

        assertEquals(model.getLayout(PAGE).getComponent(SWITCH_X, SWITCH_Y).getOrientation(), was,
            "after a discard the window still holds the turned square");
    }

    /**
     * Delete takes the square under the pointer, Control+Z brings it back, and a second Delete saved leaves it gone from
     * the file.
     *
     * MUTATION: drop the Delete key's `delete` of the hovered square and the square stays.
     */
    @Test
    public void testDeleteTakesTheSquareUnderThePointer() throws Exception
    {
        final int x = 2, y = 3;

        LayoutDiagramComponent.componentType was = saved().getComponent(x, y).getType();

        LayoutEditor editor = open();

        Mouse.enter(square(editor, x, y));

        key(editor, KeyEvent.VK_DELETE, false);

        assertNull(editor.getMarklinLayout().getComponent(x, y), "Delete left the square under the pointer");

        key(editor, KeyEvent.VK_Z, true);

        assertNotNull(editor.getMarklinLayout().getComponent(x, y), "Control+Z did not bring the square back");
        assertEquals(editor.getMarklinLayout().getComponent(x, y).getType(), was, "it came back as something else");

        Mouse.enter(square(editor, x, y));

        key(editor, KeyEvent.VK_DELETE, false);

        save(editor);

        assertNull(saved().getComponent(x, y), "the deleted square is still in the page file");
    }

    /**
     * With squares picked by shift-clicking them, Delete takes those and leaves the one under the pointer.
     *
     * MUTATION: make the Delete key skip its selection branch, and the hovered square goes while the picked stay.
     */
    @Test
    public void testDeleteTakesThePickedSquaresNotTheOneUnderThePointer() throws Exception
    {
        LayoutEditor editor = open();

        // Two squares of the branch, picked
        Mouse.click(square(editor, 5, 2), MouseEvent.BUTTON1, 1, InputEvent.SHIFT_DOWN_MASK);
        Mouse.click(square(editor, 7, 2), MouseEvent.BUTTON1, 1, InputEvent.SHIFT_DOWN_MASK);

        assertEquals(editor.getSelection().size(), 2, "shift-clicking two squares did not pick them");

        // And the pointer over a third
        Mouse.enter(square(editor, 0, 3));

        key(editor, KeyEvent.VK_DELETE, false);

        assertNull(editor.getMarklinLayout().getComponent(5, 2), "a picked square survived Delete");
        assertNull(editor.getMarklinLayout().getComponent(7, 2), "a picked square survived Delete");
        assertNotNull(editor.getMarklinLayout().getComponent(0, 3),
            "Delete took the square under the pointer although squares were picked");

        assertTrue(editor.getSelection().isEmpty(), "the picked squares are still picked after they were deleted");

        discard(editor);

        assertNotNull(saved().getComponent(5, 2), "a discarded delete reached the page file");
    }

    /**
     * Control+T asks for the square's text, and the text typed is written to the file.
     *
     * MUTATION: drop `lc.setLabel(newText)` and nothing is written.
     */
    @Test
    public void testControlTWritesTheTextTyped() throws Exception
    {
        final int x = 4, y = 3;

        LayoutEditor editor = open();

        Mouse.enter(square(editor, x, y));

        try (Answering answering = Answering.start(message -> Answering.type("Up Line")))
        {
            key(editor, KeyEvent.VK_T, true);

            assertTrue(answering.awaitAsked(1, 10000), "Control+T asked nothing");

            assertEquals(answering.asked().get(0), I18n.t("layout.ui.promptEnterTileLabel"));
        }

        Components.settle();

        assertEquals(editor.getMarklinLayout().getComponent(x, y).getLabel(), "Up Line");

        assertTrue(editor.canUndo(), "the text was not an undoable edit");

        save(editor);

        assertEquals(saved().getComponent(x, y).getLabel(), "Up Line", "the text did not reach the page file");
    }

    /**
     * Cancelling the text question changes nothing, and leaves nothing to undo.
     *
     * MUTATION: take the undo snapshot whatever the answer, and a cancelled question leaves an undo step behind.
     */
    @Test
    public void testControlTCancelledChangesNothing() throws Exception
    {
        final int x = 6, y = 3;

        String was = saved().getComponent(x, y).getLabel();

        LayoutEditor editor = open();

        Mouse.enter(square(editor, x, y));

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.cancel"))))
        {
            key(editor, KeyEvent.VK_T, true);

            assertTrue(answering.awaitAsked(1, 10000), "Control+T asked nothing");
        }

        Components.settle();

        assertEquals(editor.getMarklinLayout().getComponent(x, y).getLabel(), was, "a cancelled question wrote text");

        assertFalse(editor.canUndo(), "a cancelled question left an undo step");
    }

    /**
     * Control+A asks for the turnout's address in the address popup, and the address typed is written to the file.
     *
     * MUTATION: drop the `setLogicalAddress` call on OK and the address stays.
     */
    @Test
    public void testControlAGivesTheTurnoutTheAddressTyped() throws Exception
    {
        LayoutEditor editor = open();

        Mouse.enter(square(editor, SWITCH_X, SWITCH_Y));

        try (Answering answering = Answering.start(message -> Answering.type("37")))
        {
            key(editor, KeyEvent.VK_A, true);

            assertTrue(answering.awaitAsked(1, 10000), "Control+A asked nothing");

            assertTrue(answering.asked().get(0).contains(I18n.t("layout.ui.helpAccessoryAddressRange")),
                "the question was not the accessory address popup: " + answering.asked());
        }

        Components.settle();

        assertEquals(editor.getMarklinLayout().getComponent(SWITCH_X, SWITCH_Y).getLogicalAddress(), 37);

        save(editor);

        assertEquals(saved().getComponent(SWITCH_X, SWITCH_Y).getLogicalAddress(), 37,
            "the address did not reach the page file");
    }

    /**
     * The address field keeps only digits: a letter typed among them is taken out as the key is let go.
     *
     * MUTATION: drop the popup field's key-released filter and the letter stays, so OK fails to read a number.
     */
    @Test
    public void testTheAddressFieldTakesOnlyDigits() throws Exception
    {
        LayoutEditor editor = open();

        Mouse.enter(square(editor, SWITCH_X, SWITCH_Y));

        key(editor, KeyEvent.VK_A, true);

        assertTrue(Components.waitFor(() -> addressField() != null, 10000), "Control+A opened no address popup");

        final JTextField field = addressField();

        String left = Components.onTheEventThread(() ->
        {
            field.setText("4x2");

            field.dispatchEvent(new KeyEvent(field, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,
                KeyEvent.VK_2, '2'));

            return field.getText();
        });

        assertEquals(left, "42", "the letter typed into the address was kept");

        Components.press(Components.find(SwingUtilities.getWindowAncestor(field), AbstractButton.class,
            b -> I18n.t("ui.ok").equals(b.getText())));

        Components.waitFor(() -> addressField() == null, 10000);

        Components.settle();

        assertEquals(editor.getMarklinLayout().getComponent(SWITCH_X, SWITCH_Y).getLogicalAddress(), 42);
    }

    /**
     * Cancelling the address popup changes nothing and leaves nothing to undo.
     *
     * MUTATION: treat every answer but the close box as OK (`result != CLOSED_OPTION`), and Cancel leaves an undo step.
     */
    @Test
    public void testControlACancelledChangesNothing() throws Exception
    {
        int was = saved().getComponent(SWITCH_X, SWITCH_Y).getLogicalAddress();

        LayoutEditor editor = open();

        Mouse.enter(square(editor, SWITCH_X, SWITCH_Y));

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.cancel"))))
        {
            key(editor, KeyEvent.VK_A, true);

            assertTrue(answering.awaitAsked(1, 10000), "Control+A asked nothing");
        }

        Components.settle();

        assertEquals(editor.getMarklinLayout().getComponent(SWITCH_X, SWITCH_Y).getLogicalAddress(), was);

        assertFalse(editor.canUndo(), "a cancelled address left an undo step");
    }

    /**
     * Control+L hides the text labels and shows them again, and the Text Labels box says which.
     *
     * MUTATION: drop the Control+L branch and neither the box nor the page changes.
     */
    @Test
    public void testControlLHidesAndShowsTheText() throws Exception
    {
        LayoutEditor editor = open();

        final JCheckBox box = (JCheckBox) Components.withText(editor, I18n.t("layout.ui.textLabels"));

        assertNotNull(box, "there is no Text Labels box");

        // Read from the page, not from the box: the box can disagree with the page when the editor opens (see
        // testTheTextLabelsBoxAgreesWithThePageAfterTheAutonomyEditor)
        boolean hidden = editor.getMarklinLayout().getEditHideText();

        key(editor, KeyEvent.VK_L, true);

        assertEquals(editor.getMarklinLayout().getEditHideText(), !hidden, "Control+L did not change what is drawn");
        assertEquals(box.isSelected(), hidden, "the Text Labels box does not say what Control+L did");

        key(editor, KeyEvent.VK_L, true);

        assertEquals(editor.getMarklinLayout().getEditHideText(), hidden, "a second Control+L did not put it back");
        assertEquals(box.isSelected(), !hidden, "the Text Labels box does not say what the second Control+L did");
    }

    /**
     * The Text Labels box does what Control+L does.
     *
     * MUTATION: make the box's handler do nothing and the page's text setting stays.
     */
    @Test
    public void testTheTextLabelsBoxHidesTheText() throws Exception
    {
        LayoutEditor editor = open();

        final AbstractButton box = Components.withText(editor, I18n.t("layout.ui.textLabels"));

        boolean hidden = editor.getMarklinLayout().getEditHideText();

        Components.press(box);
        Components.settle();

        assertEquals(editor.getMarklinLayout().getEditHideText(), !hidden, "the Text Labels box changed nothing");
        assertEquals(box.isSelected(), hidden);

        Components.press(box);
        Components.settle();

        assertEquals(editor.getMarklinLayout().getEditHideText(), hidden);
    }

    // ---------------------------------------------------------------- the right-click menu

    /**
     * Rotate on the square's right-click menu turns it, and Save writes it.
     *
     * MUTATION: drop the item's `rotate` call, or the square's click listener, and nothing turns.
     */
    @Test
    public void testRightClickRotateTurnsTheSquare() throws Exception
    {
        final int x = 8, y = 3;

        int was = saved().getComponent(x, y).getOrientation();
        int turns = saved().getComponent(x, y).getNumOrientations();

        LayoutEditor editor = open();

        choose(editor, x, y, I18n.t("ui.rotate"));

        save(editor);

        assertEquals(saved().getComponent(x, y).getOrientation(), (was + 1) % turns,
            "Rotate on the menu did not turn the buffer stop in the file");
    }

    /**
     * Undo and Redo on the menu walk the undo stack: Undo puts the turn back, Redo does it again.
     *
     * MUTATION: swap the two items' actions and the heading is wrong after each.
     */
    @Test
    public void testRightClickUndoAndRedoWalkTheStack() throws Exception
    {
        final int x = 7, y = 3;

        int was = model.getLayout(PAGE).getComponent(x, y).getOrientation();

        LayoutEditor editor = open();

        choose(editor, x, y, I18n.t("ui.rotate"));

        int turned = editor.getMarklinLayout().getComponent(x, y).getOrientation();

        assertNotEquals(turned, was, "Rotate did nothing, so there is nothing to undo");

        choose(editor, x, y, I18n.t("ui.undo"));

        assertEquals(editor.getMarklinLayout().getComponent(x, y).getOrientation(), was, "Undo did not take it back");

        choose(editor, x, y, I18n.t("ui.redo"));

        assertEquals(editor.getMarklinLayout().getComponent(x, y).getOrientation(), turned, "Redo did not do it again");

        discard(editor);
    }

    /**
     * Delete on the menu takes the square, and Save writes it gone.
     *
     * MUTATION: drop the item's `delete` call and the square stays.
     */
    @Test
    public void testRightClickDeleteTakesTheSquare() throws Exception
    {
        final int x = 6, y = 2;

        assertNotNull(saved().getComponent(x, y), "the sensor to delete is not there");

        LayoutEditor editor = open();

        choose(editor, x, y, I18n.t("ui.delete"));

        save(editor);

        assertNull(saved().getComponent(x, y), "Delete on the menu left the square in the file");
    }

    /**
     * Edit Text Label on the menu asks for the text and writes it.
     *
     * MUTATION: drop the item's `editText` call and nothing is asked; drop `lc.setLabel(newText)` and nothing is written.
     */
    @Test
    public void testRightClickEditTextLabelWritesTheText() throws Exception
    {
        final int x = 1, y = 3;

        LayoutEditor editor = open();

        try (Answering answering = Answering.start(message -> Answering.type("West End")))
        {
            choose(editor, x, y, I18n.t("layout.ui.editTextLabel"));

            assertTrue(answering.awaitAsked(1, 10000), "Edit Text Label asked nothing");
        }

        Components.settle();

        save(editor);

        assertEquals(saved().getComponent(x, y).getLabel(), "West End");
    }

    /**
     * The menu names the square's address, and its item asks for a new one and writes it.
     *
     * MUTATION: drop the item's `editAddress` call and nothing is asked; drop the `setLogicalAddress` call on OK and the old
     * address stays in the file.
     */
    @Test
    public void testRightClickEditAddressNamesItAndChangesIt() throws Exception
    {
        LayoutEditor editor = open();

        LayoutDiagramComponent turnout = editor.getMarklinLayout().getComponent(SWITCH_X, SWITCH_Y);

        String item = I18n.f("layout.ui.menuEditAddressLabel", I18n.t("layout.ui.address"),
            turnout.getLogicalAddress(), turnout.getProtocol() == null ? ""
                : org.traincontrol.base.Accessory.getProtocolStringForName(turnout.getProtocol().toString()));

        try (Answering answering = Answering.start(message -> Answering.type("51")))
        {
            choose(editor, SWITCH_X, SWITCH_Y, item);

            assertTrue(answering.awaitAsked(1, 10000), "the address item asked nothing");
        }

        Components.settle();

        save(editor);

        assertEquals(saved().getComponent(SWITCH_X, SWITCH_Y).getLogicalAddress(), 51);
    }

    /**
     * Select Everything on This Page picks every square, Delete on the selection menu empties the page, and Undo puts
     * every square back.
     *
     * MUTATION: make Select Everything pick one row, or make the selection's Delete do nothing, and squares survive.
     */
    @Test
    public void testSelectEverythingThenDeleteThenUndo() throws Exception
    {
        LayoutEditor editor = open();

        LayoutDiagram page = editor.getMarklinLayout();

        int squares = page.getAll().size();

        assertTrue(squares > 0, "the page is empty, so this tests nothing");

        chooseIn(editor, SWITCH_X, SWITCH_Y, selectionMenu(editor), I18n.t("layout.ui.menuSelectAll"));

        assertEquals(editor.getSelection().size(), page.getSx() * page.getSy(),
            "Select Everything did not pick every square of the page");

        chooseIn(editor, SWITCH_X, SWITCH_Y, selectionMenu(editor), I18n.t("ui.delete"));

        assertEquals(page.getAll().size(), 0, "Delete on the selection left squares on the page");

        choose(editor, SWITCH_X, SWITCH_Y, I18n.t("ui.undo"));

        assertEquals(page.getAll().size(), squares, "Undo did not bring every square back");

        discard(editor);
    }

    /**
     * Rotate on the selection menu turns every picked square that can turn.
     *
     * MUTATION: make the selection's Rotate turn only the first square and the others keep their heading.
     */
    @Test
    public void testSelectEverythingThenRotateTurnsEverySquare() throws Exception
    {
        LayoutEditor editor = open();

        LayoutDiagram page = editor.getMarklinLayout();

        java.util.Map<String, Integer> was = headings(page);

        chooseIn(editor, SWITCH_X, SWITCH_Y, selectionMenu(editor), I18n.t("layout.ui.menuSelectAll"));
        chooseIn(editor, SWITCH_X, SWITCH_Y, selectionMenu(editor), I18n.t("ui.rotate"));

        int turned = 0;

        for (LayoutDiagramComponent c : page.getAll())
        {
            if (c.getNumOrientations() < 2) continue;

            int before = was.get(c.getX() + "," + c.getY());

            assertEquals(c.getOrientation(), (before + 1) % c.getNumOrientations(),
                "the square at " + c.getX() + "," + c.getY() + " was not turned");

            turned++;
        }

        assertTrue(turned >= 3, "fewer than three squares could turn, so this tests little: " + turned);

        discard(editor);
    }

    /**
     * Fill with the Chosen Tile puts the palette piece the user picked on every picked square, and Save writes them.
     *
     * MUTATION: make Fill stop after the first square and the row is not filled.
     */
    @Test
    public void testFillPutsThePickedPieceOnEveryPickedSquare() throws Exception
    {
        final int row = 12;

        LayoutEditor editor = open();

        LayoutLabel straight = Components.find(editor, LayoutLabel.class, label -> editor.getGridX(label) == -1
            && label.getComponent() != null
            && label.getComponent().getType() == LayoutDiagramComponent.componentType.STRAIGHT);

        assertNotNull(straight, "the palette has no straight track");

        Mouse.click(straight);

        assertTrue(editor.hasToolFlag(), "clicking the palette's straight did not pick it up");

        chooseIn(editor, 3, row, selectionMenu(editor), I18n.t("layout.ui.menuSelectRow"));
        chooseIn(editor, 3, row, selectionMenu(editor), I18n.t("layout.ui.menuFillSelection"));

        int width = editor.getMarklinLayout().getSx();

        save(editor);

        LayoutDiagram file = saved();

        for (int x = 0; x < width; x++)
        {
            assertNotNull(file.getComponent(x, row), "Fill left " + x + "," + row + " empty");
            assertEquals(file.getComponent(x, row).getType(), LayoutDiagramComponent.componentType.STRAIGHT);
        }
    }

    /**
     * Clear Diagram asks first: No keeps every square, Yes empties the page.
     *
     * MUTATION: clear before asking, or on any answer, and No empties the page.
     */
    @Test
    public void testClearDiagramAsksFirst() throws Exception
    {
        LayoutEditor editor = open();

        LayoutDiagram page = editor.getMarklinLayout();

        int squares = page.getAll().size();

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.no"))))
        {
            chooseIn(editor, SWITCH_X, SWITCH_Y, I18n.t("layout.ui.menuDiagram"), I18n.t("layout.ui.clearDiagram"));

            assertTrue(answering.awaitAsked(1, 10000), "Clear Diagram asked nothing");

            assertEquals(answering.asked().get(0), I18n.t("layout.ui.confirmDeleteTrackDiagram"));
        }

        Components.settle();

        assertEquals(page.getAll().size(), squares, "No cleared the diagram");

        assertFalse(editor.canUndo(), "No left an undo step");

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            chooseIn(editor, SWITCH_X, SWITCH_Y, I18n.t("layout.ui.menuDiagram"), I18n.t("layout.ui.clearDiagram"));

            assertTrue(answering.awaitAsked(1, 10000), "Clear Diagram asked nothing");
        }

        Components.settle();

        assertEquals(page.getAll().size(), 0, "Yes left squares on the page");

        discard(editor);

        assertEquals(saved().getAll().size(), squares, "a discarded Clear reached the page file");
    }

    // ---------------------------------------------------------------- the mouse

    /**
     * A click picks a square up, and a click on an empty square puts it down there: the square moves, and Save writes
     * it.
     *
     * MUTATION: make a plain click on a placed square copy rather than cut it, and the old square keeps its piece.
     */
    @Test
    public void testAClickPicksUpAndAClickPutsDown() throws Exception
    {
        final int fromX = 7, fromY = 2, toX = 7, toY = 8;

        LayoutDiagramComponent.componentType was = saved().getComponent(fromX, fromY).getType();

        LayoutEditor editor = open();

        assertNull(editor.getMarklinLayout().getComponent(toX, toY), "the square to put it on is not empty");

        Mouse.click(square(editor, fromX, fromY));

        assertTrue(editor.hasToolFlag(), "the click picked nothing up");

        Mouse.click(square(editor, toX, toY));

        assertNull(editor.getMarklinLayout().getComponent(fromX, fromY), "the square was copied, not moved");

        save(editor);

        assertNull(saved().getComponent(fromX, fromY), "the old square is still in the file");
        assertNotNull(saved().getComponent(toX, toY), "the new square is not in the file");
        assertEquals(saved().getComponent(toX, toY).getType(), was, "something else arrived");
    }

    /**
     * A drag carries a square from where the button went down to where it came up, and Undo takes it back.
     *
     * MUTATION: drop the release's `executeTool(target)`, or the square's press listener, and nothing lands.
     */
    @Test
    public void testADragMovesTheSquareAndUndoTakesItBack() throws Exception
    {
        final int fromX = 0, fromY = 3, toX = 0, toY = 9;

        LayoutEditor editor = open();

        LayoutDiagram page = editor.getMarklinLayout();

        LayoutDiagramComponent.componentType was = page.getComponent(fromX, fromY).getType();

        Mouse.drag(square(editor, fromX, fromY), square(editor, toX, toY));

        assertNull(page.getComponent(fromX, fromY), "the dragged square is still where it was");
        assertNotNull(page.getComponent(toX, toY), "nothing landed where the drag ended");
        assertEquals(page.getComponent(toX, toY).getType(), was);

        key(editor, KeyEvent.VK_Z, true);

        assertNotNull(page.getComponent(fromX, fromY), "Undo did not take the drag back");
        assertNull(page.getComponent(toX, toY), "Undo left a copy where the drag ended");

        discard(editor);
    }

    /**
     * Dragging a picked square carries the whole selection by the same step, and Undo puts it back.
     *
     * MUTATION: make the release lose the drag's vertical step (`moveSelection(dx, 0)`), or the square's press listener do
     * nothing, and the row stays.
     */
    @Test
    public void testDraggingTheSelectionMovesItAll() throws Exception
    {
        final int row = 2, to = 10, grab = 5;

        LayoutEditor editor = open();

        LayoutDiagram page = editor.getMarklinLayout();

        java.util.Map<Integer, LayoutDiagramComponent.componentType> was = row(page, row);

        assertTrue(was.size() >= 2, "the row holds fewer than two squares, so this tests little");
        assertTrue(row(page, to).isEmpty(), "the row to move to is not empty");

        chooseIn(editor, grab, row, selectionMenu(editor), I18n.t("layout.ui.menuSelectRow"));

        Mouse.drag(square(editor, grab, row), square(editor, grab, to));

        assertEquals(row(page, to), was, "the selection did not arrive whole");
        assertTrue(row(page, row).isEmpty(), "the selection's old row still holds squares");

        key(editor, KeyEvent.VK_Z, true);

        assertEquals(row(page, row), was, "Undo did not put the selection back");
        assertTrue(row(page, to).isEmpty(), "Undo left squares where the selection went");

        discard(editor);
    }

    /**
     * A selection dragged so that part of it would leave the diagram is refused, with the sentence saying so, and
     * nothing moves.
     *
     * MUTATION: drop the `fitsAfterMove` check and squares are lost off the edge.
     */
    @Test
    public void testASelectionDraggedOffTheDiagramIsRefused() throws Exception
    {
        final int row = 3, grab = 5;

        LayoutEditor editor = open();

        LayoutDiagram page = editor.getMarklinLayout();

        java.util.Map<Integer, LayoutDiagramComponent.componentType> was = row(page, row);

        chooseIn(editor, grab, row, selectionMenu(editor), I18n.t("layout.ui.menuSelectRow"));

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            // A whole row moved one square right: its last square would leave
            Mouse.drag(square(editor, grab, row), square(editor, grab + 1, row));

            assertTrue(answering.awaitAsked(1, 10000), "nothing was said about the refused move");

            assertEquals(answering.asked().get(0), I18n.t("layout.ui.errorSelectionWouldLeaveTheDiagram"));
        }

        Components.settle();

        assertEquals(row(page, row), was, "the refused move changed the row");

        assertFalse(editor.canUndo(), "the refused move left an undo step");
    }

    /**
     * A double click on the turnout asks for its address, as Control+A does.
     *
     * MUTATION: drop the double click's `editAddress` and nothing is asked.
     */
    @Test
    public void testADoubleClickOnTheTurnoutAsksItsAddress() throws Exception
    {
        LayoutEditor editor = open();

        try (Answering answering = Answering.start(message -> Answering.type("63")))
        {
            Mouse.click(square(editor, SWITCH_X, SWITCH_Y), MouseEvent.BUTTON1, 2, 0);

            assertTrue(answering.awaitAsked(1, 10000), "a double click asked nothing");
        }

        Components.settle();

        assertEquals(editor.getMarklinLayout().getComponent(SWITCH_X, SWITCH_Y).getLogicalAddress(), 63,
            "the address given was not taken");

        discard(editor);
    }

    /**
     * A middle click turns the square.
     *
     * MUTATION: drop the middle button's branch and the square keeps its heading.
     */
    @Test
    public void testAMiddleClickTurnsTheSquare() throws Exception
    {
        final int x = 6, y = 3;

        LayoutEditor editor = open();

        LayoutDiagramComponent square = editor.getMarklinLayout().getComponent(x, y);

        int was = square.getOrientation();

        Mouse.click(square(editor, x, y), MouseEvent.BUTTON2, 1, 0);

        assertEquals(editor.getMarklinLayout().getComponent(x, y).getOrientation(),
            (was + 1) % square.getNumOrientations(), "a middle click did not turn the track");

        discard(editor);
    }

    /**
     * The address popup on a sensor asks for its s88 contact, and the number typed is written to the file.
     *
     * MUTATION: drop the popup's feedback branch and its help text is not the s88 one.
     */
    @Test
    public void testTheAddressPopupGivesASensorItsContact() throws Exception
    {
        final int x = 3, y = 3;

        LayoutEditor editor = open();

        assertTrue(editor.getMarklinLayout().getComponent(x, y).isFeedback(), "the square is not a sensor");

        Mouse.enter(square(editor, x, y));

        try (Answering answering = Answering.start(message -> Answering.type("23")))
        {
            key(editor, KeyEvent.VK_A, true);

            assertTrue(answering.awaitAsked(1, 10000), "Control+A asked nothing");

            assertTrue(answering.asked().get(0).contains(I18n.t("layout.ui.helpFeedbackS88")),
                "the popup did not ask for an s88 contact: " + answering.asked());
        }

        Components.settle();

        save(editor);

        assertEquals(saved().getComponent(x, y).getLogicalAddress(), 23, "the contact did not reach the page file");
    }

    /**
     * The popup's DCC choice makes the turnout a DCC accessory, and the window reads it back so after Save.
     *
     * MUTATION: make `getProtocol` answer MM2 whichever is chosen, and the turnout stays MM2.
     */
    @Test
    public void testTheAddressPopupMakesTheTurnoutDcc() throws Exception
    {
        LayoutEditor editor = open();

        assertNotEquals(editor.getMarklinLayout().getComponent(SWITCH_X, SWITCH_Y).getProtocol(),
            org.traincontrol.base.Accessory.accessoryDecoderType.DCC, "the turnout is DCC already");

        Mouse.enter(square(editor, SWITCH_X, SWITCH_Y));

        key(editor, KeyEvent.VK_A, true);

        assertTrue(Components.waitFor(() -> addressField() != null, 10000), "Control+A opened no address popup");

        final java.awt.Window popup = SwingUtilities.getWindowAncestor(addressField());

        AbstractButton dcc = Components.withText(popup, "DCC");

        assertNotNull(dcc, "the popup offers no DCC");

        Components.press(dcc);
        Components.settle();

        Components.press(Components.find(popup, AbstractButton.class, b -> I18n.t("ui.ok").equals(b.getText())));

        assertTrue(Components.waitFor(() -> addressField() == null, 10000), "OK did not close the popup");

        Components.settle();

        save(editor);

        assertEquals(model.getLayout(PAGE).getComponent(SWITCH_X, SWITCH_Y).getProtocol(),
            org.traincontrol.base.Accessory.accessoryDecoderType.DCC, "the turnout read back is not DCC");
    }

    /**
     * The + button grows the page by a row and a column, and - shrinks it back.
     *
     * MUTATION: make + add rows only, and the page is no wider.
     */
    @Test
    public void testPlusGrowsThePageAndMinusShrinksIt() throws Exception
    {
        LayoutEditor editor = open();

        LayoutDiagram page = editor.getMarklinLayout();

        int sx = page.getSx(), sy = page.getSy();

        Components.press(Components.withText(editor, "+"));
        Components.settle();

        assertEquals(page.getSx(), sx + 1, "+ did not add a column");
        assertEquals(page.getSy(), sy + 1, "+ did not add a row");

        Components.press(Components.withText(editor, "-"));
        Components.settle();

        assertEquals(page.getSx(), sx, "- did not take the column away");
        assertEquals(page.getSy(), sy, "- did not take the row away");

        discard(editor);
    }

    /**
     * A piece placed in the last column stops - from shrinking the page, and says why.
     *
     * MUTATION: drop the `edgesAreEmpty` check and the piece is cut off with the column.
     */
    @Test
    public void testMinusRefusesToCutOffTrack() throws Exception
    {
        LayoutEditor editor = open();

        LayoutDiagram page = editor.getMarklinLayout();

        // An empty edge first, so the page could shrink but for the piece placed below
        Components.press(Components.withText(editor, "+"));
        Components.settle();

        int sx = page.getSx(), sy = page.getSy();

        LayoutLabel straight = Components.find(editor, LayoutLabel.class, label -> editor.getGridX(label) == -1
            && label.getComponent() != null
            && label.getComponent().getType() == LayoutDiagramComponent.componentType.STRAIGHT);

        Mouse.click(straight);
        Mouse.click(square(editor, sx - 1, 0));

        assertNotNull(page.getComponent(sx - 1, 0), "the piece was not placed in the last column");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.press(Components.withText(editor, "-"));

            assertTrue(answering.awaitAsked(1, 10000), "- said nothing");

            assertEquals(answering.asked().get(0), I18n.t("layout.ui.errorEdgesNotEmpty"));
        }

        Components.settle();

        assertEquals(page.getSx(), sx, "the page shrank over the piece");
        assertNotNull(page.getComponent(sx - 1, 0), "the piece was lost");

        discard(editor);
    }

    /**
     * The Addresses box writes every accessory's address on the diagram, and takes them off again.
     *
     * MUTATION: make the box's handler do nothing, and the page's address setting stays.
     */
    @Test
    public void testTheAddressesBoxShowsTheAddresses() throws Exception
    {
        LayoutEditor editor = open();

        final AbstractButton box = Components.withText(editor, I18n.t("layout.ui.addresses"));

        assertNotNull(box, "there is no Addresses box");

        boolean shown = editor.getMarklinLayout().getShowAddress();

        Components.press(box);
        Components.settle();

        assertEquals(editor.getMarklinLayout().getShowAddress(), !shown, "the Addresses box changed nothing");

        Components.press(box);
        Components.settle();

        assertEquals(editor.getMarklinLayout().getShowAddress(), shown, "a second press did not put it back");
    }

    /**
     * In the autonomy editor, Control+G shows the track lengths and hides them again.
     *
     * MUTATION: drop Control+G's `toggleTrackLengths` call and the box does not change.
     */
    @Test
    public void testControlGShowsTheLengthsInTheAutonomyEditor() throws Exception
    {
        TrainControlUI.getPrefs().putBoolean(TrainControlUI.LAST_EDITOR_AUTONOMY_PREF, true);

        try
        {
            assertTrue(Components.waitFor(() -> editButton().isEnabled(), 30000), "the Edit button never came back");

            Components.press(editButton());

            assertTrue(Components.waitFor(() -> editorOpen() != null && editorOpen().isAutonomyMode()
                && editorOpen().getAutonomyPanel() != null, 30000), "the Edit button opened no autonomy editor");

            Components.settle();

            LayoutEditor editor = editorOpen();

            JCheckBox lengths = editor.getAutonomyPanel().getShowLengths();

            boolean shown = lengths.isSelected();

            key(editor, KeyEvent.VK_G, true);

            assertEquals(lengths.isSelected(), !shown, "Control+G did not change Track Lengths");

            key(editor, KeyEvent.VK_G, true);

            assertEquals(lengths.isSelected(), shown, "a second Control+G did not put it back");
        }
        finally
        {
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.LAST_EDITOR_AUTONOMY_PREF, false);
        }
    }

    /**
     * The track editor's Text Labels box says whether the page's text is shown, after the autonomy editor has hidden it.
     *
     * The autonomy editor's unticked Text Labels box hides the page's text (FR-133) and closing it leaves it hidden, so
     * the next track editor must open with its box unticked too (OB-364).
     *
     * MUTATION: drop the `showTextCheckbox.setSelected(!l.getEditHideText())` line in the LayoutEditor constructor and
     * the box opens ticked over hidden text.
     */
    @Test
    public void testTheTextLabelsBoxAgreesWithThePageAfterTheAutonomyEditor() throws Exception
    {
        TrainControlUI.getPrefs().putBoolean(TrainControlUI.LAST_EDITOR_AUTONOMY_PREF, true);

        try
        {
            assertTrue(Components.waitFor(() -> editButton().isEnabled(), 30000), "the Edit button never came back");

            Components.press(editButton());

            assertTrue(Components.waitFor(() -> editorOpen() != null && editorOpen().isAutonomyMode()
                && editorOpen().getAutonomyPanel() != null, 30000), "the Edit button opened no autonomy editor");

            Components.settle();

            final LayoutEditor autonomy = editorOpen();

            AbstractButton text = Components.find(autonomy, JCheckBox.class,
                b -> I18n.t("layout.ui.textLabels").equals(b.getText()) && b.isShowing());

            assertNotNull(text, "the autonomy editor has no Text Labels box");

            // Unticked, as it opens by default
            if (text.isSelected())
            {
                Components.press(text);
                Components.settle();
            }

            assertTrue(autonomy.getMarklinLayout().getEditHideText(), "the unticked box did not hide the text");

            Components.press(Components.withText(autonomy, I18n.t("ui.cancel")));

            assertTrue(Components.waitFor(() -> !autonomy.isDisplayable(), 10000), "the autonomy editor did not close");
        }
        finally
        {
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.LAST_EDITOR_AUTONOMY_PREF, false);
        }

        LayoutEditor editor = open();

        JCheckBox box = (JCheckBox) Components.withText(editor, I18n.t("layout.ui.textLabels"));

        assertEquals(box.isSelected(), !editor.getMarklinLayout().getEditHideText(),
            "the Text Labels box is ticked while the page's text is hidden");
    }

    // ---------------------------------------------------------------- the editor

    private static LayoutDiagram saved() throws Exception
    {
        String url = "file:///" + sandbox.getFolder().getAbsolutePath().replace('\\', '/') + "/";

        CS2File parser = new CS2File(url, null);

        parser.setLayoutDataLoc(url);

        for (LayoutDiagram page : support.LayoutSandbox.wired(model, parser))
        {
            if (PAGE.equals(page.getName())) return page;
        }

        fail("the page file " + PAGE + " could not be read back");

        return null;
    }

    /** Opens the track editor with the main window's Edit button, and waits for its squares. */
    private static LayoutEditor open() throws Exception
    {
        assertTrue(Components.waitFor(() -> editButton().isEnabled(), 30000), "the Edit button never came back");

        Components.press(editButton());

        assertTrue(Components.waitFor(() -> editorOpen() != null
            && square(editorOpen(), SWITCH_X, SWITCH_Y) != null, 30000), "the Edit button opened no editor");

        Components.settle();

        LayoutEditor editor = editorOpen();

        assertFalse(editor.isAutonomyMode(), "the Edit button opened the autonomy editor, not the track editor");

        return editor;
    }

    private static AbstractButton editButton()
    {
        return Components.withText(ui, I18n.t("ui.main.editLayout"));
    }

    private boolean editButtonIsBack()
    {
        return editorOpen() == null && editButton().isEnabled();
    }

    private static LayoutEditor editorOpen()
    {
        return Components.onTheEventThread(() ->
        {
            for (Frame frame : Frame.getFrames())
            {
                if (frame instanceof LayoutEditor && frame.isDisplayable()) return (LayoutEditor) frame;
            }

            return null;
        });
    }

    /** The diagram's square at this column and row, as drawn now. */
    private static LayoutLabel square(final LayoutEditor editor, final int x, final int y)
    {
        return Components.find(editor, LayoutLabel.class,
            label -> editor.getGridX(label) == x && editor.getGridY(label) == y);
    }

    /** Save, and the main window's reload of the page after it. */
    private static void save(final LayoutEditor editor) throws Exception
    {
        Components.press(Components.withText(editor, I18n.t("layout.ui.saveChanges")));

        assertTrue(Components.waitFor(() -> !editor.isDisplayable(), 10000), "Save did not close the editor");

        assertTrue(Components.waitFor(() -> editButton().isEnabled(), 30000), "the window did not reload the page");
    }

    /** Cancel, answering Yes to throwing the changes away. */
    private static void discard(final LayoutEditor editor) throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            Components.press(Components.withText(editor, I18n.t("ui.cancel")));

            assertTrue(Components.waitFor(() -> !editor.isDisplayable(), 10000), "Cancel did not close the editor");
        }

        assertTrue(Components.waitFor(() -> editButton().isEnabled(), 30000), "the window did not reload the page");
    }

    /**
     * A key pressed in the editor.  Dispatched at the frame, which is this window's focus owner by construction (see
     * `testEscapeClosesTheEditor`).  A private stand-in for the shared keys helper.
     */
    private static void key(final LayoutEditor editor, final int code, final boolean control)
    {
        SwingUtilities.invokeLater(() -> editor.dispatchEvent(new KeyEvent(editor, KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(), control ? InputEvent.CTRL_DOWN_MASK : 0, code, KeyEvent.CHAR_UNDEFINED)));

        Components.settle();
    }

    /** Right-clicks the square and chooses the item that says this, at the menu's top level or one submenu down. */
    private static void choose(LayoutEditor editor, int x, int y, final String text)
    {
        JPopupMenu menu = Mouse.rightClick(square(editor, x, y), 10000);

        assertNotNull(menu, "a right-click on " + x + "," + y + " opened no menu");

        JMenuItem item = Components.find(menu, JMenuItem.class,
            i -> text.equals(i.getText()) && !(i instanceof JMenu) && i.getParent() == menu);

        assertNotNull(item, "the menu at " + x + "," + y + " has no item \"" + text + "\"");
        assertTrue(item.isEnabled(), "\"" + text + "\" is greyed out");

        Mouse.choose(item);
    }

    /** Whether the square's right-click menu has this item switched on; the menu is closed again. */
    private static boolean enabled(LayoutEditor editor, int x, int y, final String text)
    {
        JPopupMenu menu = Mouse.rightClick(square(editor, x, y), 10000);

        assertNotNull(menu, "a right-click on " + x + "," + y + " opened no menu");

        JMenuItem item = Components.find(menu, JMenuItem.class,
            i -> text.equals(i.getText()) && i.getParent() == menu);

        assertNotNull(item, "the menu has no item \"" + text + "\"");

        boolean on = item.isEnabled();

        Mouse.closeMenus();

        return on;
    }

    /** The selection submenu's title as it reads now, with the count of picked squares. */
    private static String selectionMenu(LayoutEditor editor)
    {
        return I18n.f("layout.ui.menuSelection", editor.getSelection().size());
    }

    /** Right-clicks the square and chooses an item of one of its submenus. */
    private static void chooseIn(LayoutEditor editor, int x, int y, final String submenu, final String text)
    {
        JPopupMenu menu = Mouse.rightClick(square(editor, x, y), 10000);

        assertNotNull(menu, "a right-click on " + x + "," + y + " opened no menu");

        JMenu sub = Components.find(menu, JMenu.class, m -> submenu.equals(m.getText()));

        assertNotNull(sub, "the menu has no submenu \"" + submenu + "\"");

        JMenuItem item = Components.find(sub, JMenuItem.class, i -> i != sub && text.equals(i.getText()));

        assertNotNull(item, "the submenu \"" + submenu + "\" has no item \"" + text + "\"");
        assertTrue(item.isEnabled(), "\"" + text + "\" is greyed out");

        Mouse.choose(item);
    }

    /** Every square's heading, by "x,y". */
    private static java.util.Map<String, Integer> headings(LayoutDiagram page)
    {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();

        for (LayoutDiagramComponent c : page.getAll()) out.put(c.getX() + "," + c.getY(), c.getOrientation());

        return out;
    }

    /** What stands on each square of a row, by column. */
    private static java.util.Map<Integer, LayoutDiagramComponent.componentType> row(LayoutDiagram page, int y)
    {
        java.util.Map<Integer, LayoutDiagramComponent.componentType> out = new java.util.TreeMap<>();

        for (int x = 0; x < page.getSx(); x++)
        {
            if (page.getComponent(x, y) != null) out.put(x, page.getComponent(x, y).getType());
        }

        return out;
    }

    private static JTextField addressField()
    {
        return Components.onTheEventThread(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof JDialog) || !window.isShowing()) continue;

                LayoutEditorAddressPopup popup = Components.find(window, LayoutEditorAddressPopup.class, null);

                if (popup != null) return Components.find(popup, JTextField.class, JTextField::isVisible);
            }

            return null;
        });
    }
}
