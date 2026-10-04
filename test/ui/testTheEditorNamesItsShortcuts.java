package ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The autonomy editor's keyboard shortcuts are named where their actions are offered.
 *
 * Adam, OB-214, 2026-09-13: *"in the autonomy editor, Set Segment Length needs a tooltip that says
 * 'Control+E'.  change for other missing tooltip hints."*
 *
 * **Which keys were missing**, read off `LayoutEditor`'s key handler against every tooltip: Control+E
 * (Segment Length), Control+S (Rename), Control+H (Home) and Control+G (Track Lengths).  Control+L, D and K
 * were already on their toggles, and Control+B on Max Train Length since 2026-09-12.
 *
 * **Asked of the real menu**, through `buildTileMenu` - the method the right-click calls - on a station of
 * the frozen railway, and of the item that carries each action rather than of the menu as a whole, so a
 * hint put on the wrong item fails.
 *
 * MUTATION: delete `lengthItem.setToolTipText(SHORTCUT_LENGTH)` and the first claim fails.
 *
 * @author Adam
 */
public class testTheEditorNamesItsShortcuts
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutEditor editor;
    private static LayoutDiagram page;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the autonomy editor and its menus need a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration, so there is no editor menu to open");
        }

        page = model.getLayout(PAGE);

        if (page == null) throw new SkipException("no page " + PAGE);

        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
        });

        editor = built[0];
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (editor != null)
            {
                final LayoutEditor closing = editor;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

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
     * Select by Dragging a Box has a key, named on its item in the track diagram editor's right-click menu (FR-107).
     *
     * Adam, 2026-10-03: *"make a hotkey, with a tool tip for select by dragging a box in the track diagram editor"*.  The
     * item's tooltip names the key, and the key does what the item does: one box, then back to normal.
     *
     * MUTATION: take the key out of the editor's key handler, or its name off the item, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testSelectByDraggingABoxHasAKey() throws Exception
    {
        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        try
        {
            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            Thread.sleep(500);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            // A SQUARE WITH TRACK ON IT, to open the menu on
            java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

            gridField.setAccessible(true);

            final org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) gridField.get(track[0]);

            org.traincontrol.gui.LayoutLabel label = null;

            org.traincontrol.base.LayoutDiagramComponent component = null;

            for (int x = page.getMinx(); x <= page.getMaxx() && label == null; x++)
            {
                for (int y = page.getMiny(); y <= page.getMaxy() && label == null; y++)
                {
                    if (page.getComponent(x, y) != null && grid.getValueAt(x, y) != null)
                    {
                        label = grid.getValueAt(x, y);
                        component = page.getComponent(x, y);
                    }
                }
            }

            assertNotNull(label, "precondition: no square with track on " + PAGE);

            final org.traincontrol.gui.LayoutLabel on = label;

            final org.traincontrol.base.LayoutDiagramComponent what = component;

            final java.lang.reflect.Constructor<?> make = Class.forName("org.traincontrol.gui.LayoutEditorRightclickMenu")
                .getConstructor(LayoutEditor.class, TrainControlUI.class, org.traincontrol.gui.LayoutLabel.class,
                    org.traincontrol.base.LayoutDiagramComponent.class);

            make.setAccessible(true);

            final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    menu[0] = (javax.swing.JPopupMenu) make.newInstance(track[0], ui, on, what);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            List<javax.swing.JMenuItem> items = new java.util.ArrayList<>();

            collect(menu[0].getSubElements(), items);

            javax.swing.JMenuItem oneBox = byText(items, I18n.t("layout.ui.menuSelectByDragging"));

            assertNotNull(oneBox, "precondition: the track editor's menu has no Select by Dragging a Box");

            assertTrue(oneBox.getToolTipText() != null
                && oneBox.getToolTipText().contains(LayoutEditor.SHORTCUT_SELECT_BY_DRAGGING),
                "Select by Dragging a Box does not name its key: " + oneBox.getToolTipText());

            // WITH THE POINTER ON A SQUARE, as it is when the key is pressed: hovering gives a square its own pointer
            SwingUtilities.invokeAndWait(() -> track[0].receiveMoveEvent(new java.awt.event.MouseEvent(on,
                java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, 0, false), on));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            // AND THE KEY DOES IT
            final java.lang.reflect.Method pressed = LayoutEditor.class.getDeclaredMethod("formKeyPressed",
                java.awt.event.KeyEvent.class);

            pressed.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    pressed.invoke(track[0], new java.awt.event.KeyEvent(track[0], java.awt.event.KeyEvent.KEY_PRESSED,
                        System.currentTimeMillis(), java.awt.event.InputEvent.CTRL_DOWN_MASK, java.awt.event.KeyEvent.VK_M,
                        'm'));
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            java.lang.reflect.Field once = LayoutEditor.class.getDeclaredField("selectOnce");

            once.setAccessible(true);

            assertTrue(once.getBoolean(track[0]), LayoutEditor.SHORTCUT_SELECT_BY_DRAGGING + " did not start Select by"
                + " Dragging a Box");

            // AND IT SHOWS, as the pointer (MT-661's note; Adam, 2026-10-03: "there is no indicator that we have entered
            // this mode when control M is pressed", and "I'd rather not have text popping up, why not just change the
            // cursor?"): a crosshair over the diagram while picking - the square already under the pointer included
            assertEquals(grid.getContainer().getCursor().getType(), java.awt.Cursor.CROSSHAIR_CURSOR, "the pointer over the"
                + " diagram is not a crosshair while picking");

            assertEquals(on.getCursor().getType(), java.awt.Cursor.CROSSHAIR_CURSOR, "the square under the pointer when the"
                + " key was pressed keeps its own pointer, not the crosshair, until the pointer moves off it");

            // AND GOES WHEN PICKING STOPS
            SwingUtilities.invokeAndWait(() -> track[0].setSelectMode(false));

            assertTrue(grid.getContainer().getCursor().getType() != java.awt.Cursor.CROSSHAIR_CURSOR
                && on.getCursor().getType() != java.awt.Cursor.CROSSHAIR_CURSOR, "the pointer is still a crosshair after"
                + " picking stopped");

            // AND OUTLIVES A REDRAW OF THE DIAGRAM (RSA30-C5): an edit, Undo, the grid's key and a switch of mode each
            // build a new diagram panel, and picking goes on through them - so the new panel shows it too
            SwingUtilities.invokeAndWait(() -> track[0].setSelectMode(true));

            final java.lang.reflect.Method redraw = LayoutEditor.class.getDeclaredMethod("drawGrid");

            redraw.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    redraw.invoke(track[0]);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            final org.traincontrol.gui.LayoutGrid redrawn = (org.traincontrol.gui.LayoutGrid) gridField.get(track[0]);

            assertTrue(redrawn != grid && track[0].isSelectMode(), "precondition: the redraw built no new diagram, or"
                + " picking ended with it");

            assertEquals(redrawn.getContainer().getCursor().getType(), java.awt.Cursor.CROSSHAIR_CURSOR, "after a redraw of"
                + " the diagram the pointer is an arrow while a drag still picks (RSA30-C5)");

            SwingUtilities.invokeAndWait(() -> track[0].setSelectMode(false));

            assertTrue(redrawn.getContainer().getCursor().getType() != java.awt.Cursor.CROSSHAIR_CURSOR, "the pointer is"
                + " still a crosshair after picking stopped, on a redrawn diagram");

            // BUT NOT IN AUTONOMY SETUP, where no drag picks (RSA31-C4): picking carried there shows the arrow, and the
            // crosshair again on the way back, where it is still on
            SwingUtilities.invokeAndWait(() -> track[0].setSelectMode(true));

            SwingUtilities.invokeAndWait(() -> track[0].setAutonomyMode(session));

            for (int i = 0; i < 20; i++) SwingUtilities.invokeAndWait(() -> { });

            Thread.sleep(300);

            for (int i = 0; i < 20; i++) SwingUtilities.invokeAndWait(() -> { });

            final org.traincontrol.gui.LayoutGrid inAutonomy = (org.traincontrol.gui.LayoutGrid) gridField.get(track[0]);

            assertTrue(track[0].isAutonomyMode() && track[0].isSelectMode(), "precondition: not in Autonomy Setup with"
                + " picking carried in");

            assertTrue(inAutonomy.getContainer().getCursor().getType() != java.awt.Cursor.CROSSHAIR_CURSOR, "in Autonomy"
                + " Setup, where no drag picks, the pointer is the crosshair (RSA31-C4)");

            SwingUtilities.invokeAndWait(() -> track[0].setAutonomyMode(null));

            for (int i = 0; i < 20; i++) SwingUtilities.invokeAndWait(() -> { });

            Thread.sleep(300);

            for (int i = 0; i < 20; i++) SwingUtilities.invokeAndWait(() -> { });

            final org.traincontrol.gui.LayoutGrid back = (org.traincontrol.gui.LayoutGrid) gridField.get(track[0]);

            assertTrue(!track[0].isAutonomyMode() && track[0].isSelectMode(), "precondition: not back in the track editor"
                + " with picking still on");

            assertEquals(back.getContainer().getCursor().getType(), java.awt.Cursor.CROSSHAIR_CURSOR, "back in the track"
                + " editor with picking still on, the pointer is not the crosshair");

            SwingUtilities.invokeAndWait(() -> track[0].setSelectMode(false));
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /**
     * Escape that drops a tile picked up to move takes its red outline with it (RSA32-C1).
     *
     * The red outline is the track editor's "this tile is picked up".  Escape let go of the tile but put back only the
     * palette's borders, so the outline - and the pointer's blue one - stayed on the diagram until the pointer entered
     * another square, saying of a tile that it was picked up when it was not.
     *
     * A track tile clicked to pick it up, a hover so the outline is drawn, then Escape through the editor's own keys.
     *
     * MUTATION: let Escape put back only the palette again, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testEscapeTakesThePickedUpOutlineWithIt() throws Exception
    {
        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        try
        {
            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            Thread.sleep(500);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

            gridField.setAccessible(true);

            final org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) gridField.get(track[0]);

            java.lang.reflect.Field what = org.traincontrol.gui.LayoutLabel.class.getDeclaredField("component");

            what.setAccessible(true);

            // A TRACK TILE, and another square to hover over
            org.traincontrol.gui.LayoutLabel tile = null, other = null;

            for (java.awt.Component c : grid.getContainer().getComponents())
            {
                if (!(c instanceof org.traincontrol.gui.LayoutLabel) || ((org.traincontrol.gui.LayoutLabel) c).isSpacer()) continue;

                Object component = what.get(c);

                if (tile == null && component != null && !((org.traincontrol.base.LayoutDiagramComponent) component).isText())
                {
                    tile = (org.traincontrol.gui.LayoutLabel) c;
                }
                else if (tile != null && other == null && component == null)
                {
                    other = (org.traincontrol.gui.LayoutLabel) c;
                }
            }

            assertTrue(tile != null && other != null, "precondition: no track tile and empty square on " + PAGE);

            final org.traincontrol.gui.LayoutLabel picked = tile;

            final org.traincontrol.gui.LayoutLabel hovered = other;

            // PICKED UP, and the outline drawn by the next hover
            SwingUtilities.invokeAndWait(() -> track[0].receiveClickEvent(new java.awt.event.MouseEvent(picked,
                java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
                1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1), picked));

            SwingUtilities.invokeAndWait(() -> track[0].receiveMoveEvent(new java.awt.event.MouseEvent(hovered,
                java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, 0, false), hovered));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(track[0].hasToolFlag() && wearsTheOutline(picked), "precondition: the tile was not picked up, or its"
                + " outline not drawn");

            // ESCAPE, through the editor's own keys
            final java.lang.reflect.Method pressed = LayoutEditor.class.getDeclaredMethod("formKeyPressed",
                java.awt.event.KeyEvent.class);

            pressed.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    pressed.invoke(track[0], new java.awt.event.KeyEvent(track[0], java.awt.event.KeyEvent.KEY_PRESSED,
                        System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ESCAPE, (char) 27));
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(!track[0].hasToolFlag() && track[0].isDisplayable(), "precondition: Escape did not let go of the tile,"
                + " or closed the editor");

            for (java.awt.Component c : grid.getContainer().getComponents())
            {
                assertFalse(c instanceof javax.swing.JLabel && wearsTheOutline((javax.swing.JLabel) c), "after Escape let go"
                    + " of it, a square still wears the picked-up tile's red outline (RSA32-C1)");
            }

            // BUT THE SQUARE UNDER THE POINTER KEEPS ITS BLUE ONE (RSA33-C1): it says which square the pointer is over
            assertTrue(wearsTheHover(track[0], hovered), "after Escape, the square the pointer is still over lost its hover"
                + " outline - it shows nothing until the pointer enters another square (RSA33-C1)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /**
     * A square the pointer is over shows its hover outline while squares are picked (RSA33-C1).
     *
     * Every hover over a selection redrew the picked squares and the grip, and that redraw put every square back first -
     * the one under the pointer included - so while anything was picked no square ever showed the pointer was over it.
     *
     * Three squares picked, then a hover over another.
     *
     * MUTATION: let the selection's redraw leave the hover out again, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTheHoverShowsOverASelection() throws Exception
    {
        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        try
        {
            settleTheEditor();

            java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

            gridField.setAccessible(true);

            final org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) gridField.get(track[0]);

            // THREE SQUARES PICKED, as a box picks them
            java.lang.reflect.Field selectionField = LayoutEditor.class.getDeclaredField("selection");

            selectionField.setAccessible(true);

            final org.traincontrol.base.TileSelection selection =
                (org.traincontrol.base.TileSelection) selectionField.get(track[0]);

            final java.lang.reflect.Method redraw = LayoutEditor.class.getDeclaredMethod("refreshSelectionBorders");

            redraw.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                selection.addRectangle(1, 1, 3, 1);

                try
                {
                    redraw.invoke(track[0]);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            final org.traincontrol.gui.LayoutLabel over = grid.getValueAt(2, 4);

            assertNotNull(over, "precondition: no square at 2,4");

            SwingUtilities.invokeAndWait(() -> track[0].receiveMoveEvent(new java.awt.event.MouseEvent(over,
                java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, 0, false), over));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(wearsTheHover(track[0], over), "with three squares picked, the square the pointer is over shows no"
                + " hover outline (RSA33-C1)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /**
     * Escape pressed while a box is being drawn drops the box too (RSA33-C2).
     *
     * Escape is how a half-made gesture is dropped (behaviour.md 6a, FR-065), and the track editor's list left out the box
     * being drawn: with picking on, the release then picked the box with picking off; with nothing else held, Escape closed
     * the editor with the button still down.
     *
     * A box dragged with the button down - once with Control+M's picking, once with Shift and nothing else held - then
     * Escape, then the release.
     *
     * MUTATION: leave the box out of what Escape drops again, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testEscapeDropsABoxBeingDrawn() throws Exception
    {
        for (final boolean picking : new boolean[] {true, false})
        {
            final LayoutEditor[] track = new LayoutEditor[1];

            SwingUtilities.invokeAndWait(() ->
            {
                track[0] = new LayoutEditor(page, 30, ui, 0);
                track[0].render();
            });

            try
            {
                settleTheEditor();

                java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

                gridField.setAccessible(true);

                final org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) gridField.get(track[0]);

                final org.traincontrol.gui.LayoutLabel from = grid.getValueAt(1, 4);
                final org.traincontrol.gui.LayoutLabel to = grid.getValueAt(5, 4);

                assertTrue(from != null && to != null, "precondition: no squares at 1,4 and 5,4");

                if (picking) SwingUtilities.invokeAndWait(() -> track[0].setSelectMode(true));

                final int held = picking ? java.awt.event.InputEvent.BUTTON1_DOWN_MASK
                    : java.awt.event.InputEvent.BUTTON1_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK;

                // THE BOX, the button still down
                SwingUtilities.invokeAndWait(() ->
                {
                    track[0].beginDrag(new java.awt.event.MouseEvent(from, java.awt.event.MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(), held, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1), from);
                    track[0].receiveMoveEvent(new java.awt.event.MouseEvent(to, java.awt.event.MouseEvent.MOUSE_ENTERED,
                        System.currentTimeMillis(), 0, 1, 1, 0, false), to);
                    track[0].updateDrag(new java.awt.event.MouseEvent(to, java.awt.event.MouseEvent.MOUSE_DRAGGED,
                        System.currentTimeMillis(), held, 1, 1, 0, false), to);
                });

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

                // ESCAPE, through the editor's own keys
                final java.lang.reflect.Method pressed = LayoutEditor.class.getDeclaredMethod("formKeyPressed",
                    java.awt.event.KeyEvent.class);

                pressed.setAccessible(true);

                SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        pressed.invoke(track[0], new java.awt.event.KeyEvent(track[0], java.awt.event.KeyEvent.KEY_PRESSED,
                            System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ESCAPE, (char) 27));
                    }
                    catch (ReflectiveOperationException e)
                    {
                        throw new IllegalStateException(e);
                    }
                });

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

                assertTrue(track[0].isDisplayable(), "Escape pressed while a box was being drawn, with nothing else held ("
                    + (picking ? "picking" : "Shift") + "), closed the editor with the button still down (RSA33-C2)");

                // THE RELEASE
                SwingUtilities.invokeAndWait(() -> track[0].endDrag(new java.awt.event.MouseEvent(to,
                    java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 1, 1, 1, false,
                    java.awt.event.MouseEvent.BUTTON1), to));

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

                java.lang.reflect.Field selectionField = LayoutEditor.class.getDeclaredField("selection");

                selectionField.setAccessible(true);

                assertTrue(((org.traincontrol.base.TileSelection) selectionField.get(track[0])).isEmpty(), "the box Escape"
                    + " should have dropped was picked on the release (" + (picking ? "picking" : "Shift") + ", RSA33-C2)");
            }
            finally
            {
                SwingUtilities.invokeAndWait(() -> track[0].dispose());
            }
        }
    }

    /**
     * The hover outline is not drawn back on a square the pointer has left (RSA34-C1).
     *
     * Round 53 drew it back on the keys' square, which is never forgotten - so once the pointer had left the diagram for
     * its margin, the next redraw of the selection put the blue back on the last square entered.  It goes back now only on
     * the square that wore it.
     *
     * Three squares picked, a hover over another, the margin entered, then Escape.
     *
     * MUTATION: draw the hover back on the keys' square again, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTheHoverIsNotDrawnBackWhereThePointerLeft() throws Exception
    {
        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        try
        {
            settleTheEditor();

            final org.traincontrol.gui.LayoutGrid grid = gridOf(track[0]);

            pick(track[0], 1, 1, 3, 1);

            final org.traincontrol.gui.LayoutLabel over = grid.getValueAt(2, 4);

            assertNotNull(over, "precondition: no square at 2,4");

            SwingUtilities.invokeAndWait(() -> track[0].receiveMoveEvent(new java.awt.event.MouseEvent(over,
                java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, 0, false), over));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(wearsTheHover(track[0], over), "precondition: the hover outline was not drawn");

            // THE MARGIN, as the pointer enters it
            java.lang.reflect.Field marginField = LayoutEditor.class.getDeclaredField("ExtLayoutPanel");

            marginField.setAccessible(true);

            final java.awt.Component margin = (java.awt.Component) marginField.get(track[0]);

            final java.lang.reflect.Method entered = LayoutEditor.class.getDeclaredMethod("ExtLayoutPanelMouseEntered",
                java.awt.event.MouseEvent.class);

            entered.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    entered.invoke(track[0], new java.awt.event.MouseEvent(margin, java.awt.event.MouseEvent.MOUSE_ENTERED,
                        System.currentTimeMillis(), 0, 1, 1, 0, false));
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(wearsTheHover(track[0], over), "precondition: entering the margin did not take the outline off");

            escape(track[0]);

            for (java.awt.Component c : grid.getContainer().getComponents())
            {
                assertFalse(c instanceof javax.swing.JLabel && wearsTheHover(track[0], (javax.swing.JLabel) c), "after the"
                    + " pointer left for the margin, Escape drew the hover outline back on a square it is not over (RSA34-C1)");
            }
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /**
     * Escape between a box's press and its release stops the click that follows too (RSA34-C2).
     *
     * A press that never left its square ends in a click, and Escape dropped the box but not the click: with Shift the
     * square was picked; with Control+M's picking the tile was picked up to move.
     *
     * A press, Escape, the release and the click, on one square - with Shift and nothing else held, then with Control+M.
     *
     * MUTATION: let the click after a dropped box through again, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testEscapeStopsTheClickOfADroppedBox() throws Exception
    {
        for (final boolean picking : new boolean[] {false, true})
        {
            final LayoutEditor[] track = new LayoutEditor[1];

            SwingUtilities.invokeAndWait(() ->
            {
                track[0] = new LayoutEditor(page, 30, ui, 0);
                track[0].render();
            });

            try
            {
                settleTheEditor();

                final org.traincontrol.gui.LayoutLabel square = aTrackSquare(track[0]);

                if (picking) SwingUtilities.invokeAndWait(() -> track[0].setSelectMode(true));

                final int held = picking ? java.awt.event.InputEvent.BUTTON1_DOWN_MASK
                    : java.awt.event.InputEvent.BUTTON1_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK;

                SwingUtilities.invokeAndWait(() -> track[0].beginDrag(new java.awt.event.MouseEvent(square,
                    java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), held, 1, 1, 1, false,
                    java.awt.event.MouseEvent.BUTTON1), square));

                escape(track[0]);

                assertTrue(track[0].isDisplayable(), "precondition: Escape with a box pressed closed the editor");

                // THE RELEASE AND THE CLICK, on the square pressed
                SwingUtilities.invokeAndWait(() ->
                {
                    track[0].endDrag(new java.awt.event.MouseEvent(square, java.awt.event.MouseEvent.MOUSE_RELEASED,
                        System.currentTimeMillis(), held & ~java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false,
                        java.awt.event.MouseEvent.BUTTON1), square);
                    track[0].receiveClickEvent(new java.awt.event.MouseEvent(square, java.awt.event.MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(), held & ~java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false,
                        java.awt.event.MouseEvent.BUTTON1), square);
                });

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

                java.lang.reflect.Field selectionField = LayoutEditor.class.getDeclaredField("selection");

                selectionField.setAccessible(true);

                assertTrue(((org.traincontrol.base.TileSelection) selectionField.get(track[0])).isEmpty()
                    && !track[0].hasToolFlag(), "the click after a box Escape dropped " + (picking ? "picked the tile up"
                    : "picked the square") + " (" + (picking ? "Control+M" : "Shift") + ", RSA34-C2)");
            }
            finally
            {
                SwingUtilities.invokeAndWait(() -> track[0].dispose());
            }
        }
    }

    /**
     * A box being drawn keeps its outline when the pointer enters a square (RSA34-C4).
     *
     * The hover's pass put every square back and redrew the selection only when squares were picked, so the box being
     * drawn - nothing picked yet - lost its outline on each square entered, until the next drag event.
     *
     * A box pressed, a square entered and one drag event, then the posted work run.
     *
     * MUTATION: redraw only for picked squares again, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testABoxKeepsItsOutlineAsThePointerEntersASquare() throws Exception
    {
        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        try
        {
            settleTheEditor();

            final org.traincontrol.gui.LayoutGrid grid = gridOf(track[0]);

            final org.traincontrol.gui.LayoutLabel from = grid.getValueAt(1, 4);
            final org.traincontrol.gui.LayoutLabel to = grid.getValueAt(5, 4);

            assertTrue(from != null && to != null, "precondition: no squares at 1,4 and 5,4");

            SwingUtilities.invokeAndWait(() -> track[0].setSelectMode(true));

            SwingUtilities.invokeAndWait(() ->
            {
                int held = java.awt.event.InputEvent.BUTTON1_DOWN_MASK;

                track[0].beginDrag(new java.awt.event.MouseEvent(from, java.awt.event.MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), held, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1), from);
                track[0].receiveMoveEvent(new java.awt.event.MouseEvent(to, java.awt.event.MouseEvent.MOUSE_ENTERED,
                    System.currentTimeMillis(), 0, 1, 1, 0, false), to);
                track[0].updateDrag(new java.awt.event.MouseEvent(to, java.awt.event.MouseEvent.MOUSE_DRAGGED,
                    System.currentTimeMillis(), held, 1, 1, 0, false), to);
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            java.lang.reflect.Field picked = LayoutEditor.class.getDeclaredField("COMPONENT_BORDER_SELECTED_COLOR");

            picked.setAccessible(true);

            String outlined = "hl:" + ((java.awt.Color) picked.get(null)).getRGB() + ":";

            for (int x = 1; x <= 5; x++)
            {
                assertTrue(keyOf(track[0], grid.getValueAt(x, 4)).startsWith(outlined), "the box being drawn lost its"
                    + " outline at " + x + ",4 when the pointer entered a square (RSA34-C4): " + keyOf(track[0],
                    grid.getValueAt(x, 4)));
            }
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    private static org.traincontrol.gui.LayoutGrid gridOf(LayoutEditor editor) throws ReflectiveOperationException
    {
        java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

        gridField.setAccessible(true);

        return (org.traincontrol.gui.LayoutGrid) gridField.get(editor);
    }

    /** Picks a rectangle of squares, as a box does, and draws them picked. */
    private static void pick(final LayoutEditor editor, final int fromX, final int fromY, final int toX, final int toY)
        throws Exception
    {
        java.lang.reflect.Field selectionField = LayoutEditor.class.getDeclaredField("selection");

        selectionField.setAccessible(true);

        final org.traincontrol.base.TileSelection selection = (org.traincontrol.base.TileSelection) selectionField.get(editor);

        final java.lang.reflect.Method redraw = LayoutEditor.class.getDeclaredMethod("refreshSelectionBorders");

        redraw.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            selection.addRectangle(fromX, fromY, toX, toY);

            try
            {
                redraw.invoke(editor);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });
    }

    /** Escape, through the editor's own keys. */
    private static void escape(final LayoutEditor editor) throws Exception
    {
        final java.lang.reflect.Method pressed = LayoutEditor.class.getDeclaredMethod("formKeyPressed",
            java.awt.event.KeyEvent.class);

        pressed.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                pressed.invoke(editor, new java.awt.event.KeyEvent(editor, java.awt.event.KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ESCAPE, (char) 27));
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });
    }

    /** A square with track on it. */
    private static org.traincontrol.gui.LayoutLabel aTrackSquare(LayoutEditor editor) throws ReflectiveOperationException
    {
        java.lang.reflect.Field what = org.traincontrol.gui.LayoutLabel.class.getDeclaredField("component");

        what.setAccessible(true);

        for (java.awt.Component c : gridOf(editor).getContainer().getComponents())
        {
            if (c instanceof org.traincontrol.gui.LayoutLabel && !((org.traincontrol.gui.LayoutLabel) c).isSpacer())
            {
                Object component = what.get(c);

                if (component != null && !((org.traincontrol.base.LayoutDiagramComponent) component).isText())
                {
                    return (org.traincontrol.gui.LayoutLabel) c;
                }
            }
        }

        throw new IllegalStateException("no track square on " + PAGE);
    }

    /** What the editor's own record says a square wears. */
    private static String keyOf(LayoutEditor editor, javax.swing.JLabel square) throws ReflectiveOperationException
    {
        java.lang.reflect.Field stateField = LayoutEditor.class.getDeclaredField("borderState");

        stateField.setAccessible(true);

        Object key = ((java.util.Map<?, ?>) stateField.get(editor)).get(square);

        return key == null ? "" : key.toString();
    }

    /** Lets an editor just rendered finish its posted build. */
    private static void settleTheEditor() throws Exception
    {
        for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

        Thread.sleep(500);

        for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });
    }

    /** Whether a square wears the hover outline (blue), read from the editor's own record of what each square wears. */
    private static boolean wearsTheHover(LayoutEditor editor, javax.swing.JLabel square) throws ReflectiveOperationException
    {
        java.lang.reflect.Field stateField = LayoutEditor.class.getDeclaredField("borderState");

        stateField.setAccessible(true);

        Object key = ((java.util.Map<?, ?>) stateField.get(editor)).get(square);

        return key != null && key.toString().startsWith("hl:" + java.awt.Color.BLUE.getRGB() + ":");
    }

    /** Whether a square wears the track editor's picked-up outline (`COMPONENT_BORDER_COPIED_COLOR`, red). */
    private static boolean wearsTheOutline(javax.swing.JLabel square)
    {
        javax.swing.border.Border border = square.getBorder();

        return border instanceof javax.swing.border.LineBorder
            && java.awt.Color.RED.equals(((javax.swing.border.LineBorder) border).getLineColor());
    }

    /**
     * A station's right-click menu names Control+E, Control+S and Control+H on the items they do.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheStationMenuNamesItsKeys() throws Exception
    {
        final TileKey station = aStationOnThePage();

        final List<javax.swing.JMenuItem> items = new ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JPopupMenu menu = editor.getAutonomyPanel().buildTileMenu(station,
                page.getComponent(station.getX(), station.getY()));

            collect(menu.getSubElements(), items);
        });

        assertTrue(items.size() > 0, "precondition: the station's menu came back empty");

        javax.swing.JMenuItem length = byText(items, I18n.t("autosetup.ui.menuSetLength"));

        assertNotNull(length, "precondition: " + station + " offers no Segment Length item");

        assertEquals(length.getToolTipText(), AutonomyEditorPanel.SHORTCUT_LENGTH,
            "Segment Length does not say Control+E. Adam, OB-214: \"Set Segment Length needs a tooltip"
            + " that says Control+E\"");

        javax.swing.JMenuItem rename = byText(items, I18n.t("autosetup.ui.menuRename"));

        assertNotNull(rename, "precondition: " + station + " offers no Rename item");

        assertEquals(rename.getToolTipText(), AutonomyEditorPanel.SHORTCUT_NAME,
            "Rename does not say Control+S, the key that names the square under the pointer");

        boolean homeNamed = false;

        String homeNone = I18n.t("autosetup.ui.menuHomeNone");
        String homeFor = I18n.f("autosetup.ui.menuHomeFor", "\u0000").split("\u0000")[0];

        for (javax.swing.JMenuItem item : items)
        {
            String text = item.getText();

            if (text == null || !(text.equals(homeNone) || (!homeFor.isEmpty() && text.startsWith(homeFor))))
            {
                continue;
            }

            homeNamed |= AutonomyEditorPanel.SHORTCUT_HOME.equals(item.getToolTipText());
        }

        assertTrue(homeNamed, "the Home item on a station's menu does not say Control+H");

        // AND CONTROL+N, on the item that shows a station's name on a square (FR-086, Adam on MT-397: "let's add a hotkey
        // for 'show station name here' too").  Its tooltip already says what a caption is, so the key is added to it.
        javax.swing.JMenuItem showHere = null;

        for (javax.swing.JMenuItem item : items)
        {
            String text = item.getText();

            if (text != null && (text.equals(I18n.t("autosetup.ui.menuShowStationHere"))
                || text.equals(I18n.t("autosetup.ui.menuShowStationHereNamed"))
                || text.equals(I18n.t("autosetup.ui.menuStationShowsItself"))))
            {
                showHere = item;
            }
        }

        assertNotNull(showHere, "precondition: " + station + " offers no Show a Station Name Here item");

        assertTrue(showHere.getToolTipText() != null
            && showHere.getToolTipText().contains(AutonomyEditorPanel.SHORTCUT_STATION),
            "Show a Station Name Here does not say Control+N: " + showHere.getToolTipText());
    }

    /**
     * The Track Lengths toggle names Control+G, as the three toggles beside it name theirs.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTheTrackLengthsToggleNamesItsKey() throws Exception
    {
        java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("showLengths");

        field.setAccessible(true);

        final javax.swing.JCheckBox toggle = (javax.swing.JCheckBox) field.get(editor.getAutonomyPanel());

        final String[] tip = new String[1];

        SwingUtilities.invokeAndWait(() -> tip[0] = toggle.getToolTipText());

        assertTrue(tip[0] != null && tip[0].contains(AutonomyEditorPanel.SHORTCUT_LENGTHS),
            "the Track Lengths toggle does not say Control+G: " + tip[0]);
    }

    // ---------------------------------------------------------------------------------------------

    private static TileKey aStationOnThePage()
    {
        for (TileKey tile : session.getStore().getNamedTiles())
        {
            if (PAGE.equals(tile.getPage()) && session.getStore().isStation(tile)
                && page.getComponent(tile.getX(), tile.getY()) != null)
            {
                return tile;
            }
        }

        throw new SkipException("no station on " + PAGE);
    }

    private static void collect(javax.swing.MenuElement[] elements, List<javax.swing.JMenuItem> into)
    {
        for (javax.swing.MenuElement element : elements)
        {
            if (element instanceof javax.swing.JMenuItem) into.add((javax.swing.JMenuItem) element);

            collect(element.getSubElements(), into);
        }
    }

    private static javax.swing.JMenuItem byText(List<javax.swing.JMenuItem> items, String text)
    {
        for (javax.swing.JMenuItem item : items)
        {
            if (text.equals(item.getText())) return item;
        }

        return null;
    }
}
