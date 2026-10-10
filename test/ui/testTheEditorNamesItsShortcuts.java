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
     * Nothing in the autonomy editor's window takes the keyboard from it (OB-200; Adam, 2026-09-12: *"when items in the
     * list of issues are selected, hotkeys on the track diagram stop working, and there is no way to regain focus.  just
     * send the commands through with the list of issues panel selected."*).
     *
     * The shortcuts are the window's own key handler, and its controls are kept from taking the keyboard so that the
     * window keeps it (OB-019); the findings list and the directions box were not, so one click on either and every key
     * went to it.  Asked of every control a click would give the keyboard to, in the whole window, so one added later is
     * asked too.
     *
     * MUTATION: let the findings list or the directions box take the keyboard again, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testNothingInTheAutonomyEditorTakesTheKeyboard() throws Exception
    {
        final java.util.List<String> taking = new java.util.ArrayList<>();
        final java.util.List<java.awt.Component> asked = new java.util.ArrayList<>();
        final java.awt.Component[] list = new java.awt.Component[1];

        SwingUtilities.invokeAndWait(() ->
        {
            controlsTakingTheKeyboard(editor, taking, asked);

            list[0] = editor.getAutonomyPanel().getFindingsPanel().getViewport().getView();
        });

        assertTrue(asked.contains(list[0]), "precondition: the findings list is not among the controls asked, so this asks"
            + " nothing about the control Adam clicked");

        assertTrue(taking.isEmpty(), "a control of the autonomy editor takes the keyboard when it is clicked, and the"
            + " editor's shortcuts stop until something gives it back (OB-200): " + taking);
    }

    /**
     * Every visible control in a container that a click would give the keyboard to, by its class and its words; and every
     * visible control a click could, into `asked`.  Visible, not showing, so the answer does not depend on the window
     * being on a screen.
     */
    private static void controlsTakingTheKeyboard(java.awt.Container in, java.util.List<String> out,
        java.util.List<java.awt.Component> asked)
    {
        for (java.awt.Component c : in.getComponents())
        {
            if (!c.isVisible()) continue;

            boolean takesAClick = c instanceof javax.swing.JList || c instanceof javax.swing.JComboBox
                || c instanceof javax.swing.AbstractButton || c instanceof javax.swing.JSpinner
                || c instanceof javax.swing.JTable || c instanceof javax.swing.JTree || c instanceof javax.swing.JSlider
                || c instanceof javax.swing.text.JTextComponent;

            if (takesAClick) asked.add(c);

            if (takesAClick && c.isFocusable())
            {
                out.add(c.getClass().getSimpleName() + (c instanceof javax.swing.AbstractButton
                    ? " '" + ((javax.swing.AbstractButton) c).getText() + "'" : ""));
            }

            if (c instanceof java.awt.Container) controlsTakingTheKeyboard((java.awt.Container) c, out, asked);
        }
    }

    /**
     * A page switch in the autonomy editor never shows the track editor's title (MT-669; Adam, 2026-10-04: *"when
     * switching pages, the autonomy editor window title briefly shows the layout editor (not autonomy editor) title for a
     * split second, before being updated."*).  The switch set the track editor's title and left the autonomy editor's to
     * a posted step; it is now the arriving mode's from the start.
     *
     * MUTATION: set the track editor's title on the way into autonomy mode again, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAPageSwitchShowsOnlyTheAutonomyEditorsTitle() throws Exception
    {
        String to = null;

        for (String name : model.getLayoutList())
        {
            if (!PAGE.equals(name) && session.getGraph() != null && session.getGraph().getPages().contains(name)) to = name;
        }

        if (to == null) throw new SkipException("the frozen railway has no second page in autonomy");

        final String toPage = to;

        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
            track[0].setAutonomyMode(session);
        });

        final List<String> titles = java.util.Collections.synchronizedList(new ArrayList<String>());

        try
        {
            settleTheEditor();

            assertEquals(track[0].getTitle(), I18n.f("autosetup.ui.windowTitle", PAGE), "precondition: the editor is not"
                + " the autonomy editor on " + PAGE);

            track[0].addPropertyChangeListener("title", e -> titles.add(String.valueOf(e.getNewValue())));

            final java.lang.reflect.Method arrive = LayoutEditor.class.getDeclaredMethod("arriveAt", String.class,
                boolean.class);

            arrive.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    arrive.invoke(track[0], toPage, true);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            settleTheEditor();

            assertEquals(track[0].getTitle(), I18n.f("autosetup.ui.windowTitle", toPage), "precondition: the switch did"
                + " not arrive in the autonomy editor on " + toPage);

            assertFalse(titles.contains(I18n.f("app.ui.windowLayoutEditorTitle", toPage)), "switching the autonomy"
                + " editor's page showed the track editor's title on the way (MT-669): " + titles);
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
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

                // THE POINTER OVER THE SQUARE, and then the press - as a real pointer presses it
                SwingUtilities.invokeAndWait(() ->
                {
                    track[0].receiveMoveEvent(new java.awt.event.MouseEvent(square, java.awt.event.MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(), 0, 1, 1, 0, false), square);
                    track[0].beginDrag(new java.awt.event.MouseEvent(square, java.awt.event.MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(), held, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1), square);
                });

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

    /**
     * A page switch forgets the square under the pointer (Adam, 2026-10-04: *"on page switch - yes, clear the active
     * square"*).
     *
     * Delete and the other keys that act on the square under the pointer asked the old page's coordinates on the new page
     * until the pointer moved: the track editor's `lastHoveredX/Y`, and the autonomy editor's `autonomyHover`, whose
     * square is read against the page now showing.
     *
     * A square hovered, the autonomy editor's square set as its hover sets it, then a switch to another page.
     *
     * MUTATION: let a switch keep either square, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testAPageSwitchForgetsTheSquareUnderThePointer() throws Exception
    {
        String other = null;

        for (String name : model.getLayoutList()) if (other == null && !PAGE.equals(name)) other = name;

        if (other == null) throw new SkipException("the snapshot has one page only");

        final String to = other;

        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        try
        {
            settleTheEditor();

            final org.traincontrol.gui.LayoutLabel over = gridOf(track[0]).getValueAt(2, 4);

            assertNotNull(over, "precondition: no square at 2,4");

            final java.lang.reflect.Field autonomyHover = LayoutEditor.class.getDeclaredField("autonomyHover");

            autonomyHover.setAccessible(true);

            final java.lang.reflect.Field hoveredX = LayoutEditor.class.getDeclaredField("lastHoveredX");

            hoveredX.setAccessible(true);

            final java.lang.reflect.Method arrive = LayoutEditor.class.getDeclaredMethod("arriveAt", String.class,
                boolean.class);

            arrive.setAccessible(true);

            // THE HOVER, THE SWITCH AND THE READING IN ONE EVENT: this class shows the editor, and a real pointer resting
            // over it moves the keys' square between events - over the palette, to -1 - which once passed this claim with
            // the fix taken out
            final Object[] read = new Object[4];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    track[0].receiveMoveEvent(new java.awt.event.MouseEvent(over, java.awt.event.MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(), 0, 1, 1, 0, false), over);

                    // THE AUTONOMY EDITOR'S SQUARE, as its own hover sets it
                    autonomyHover.set(track[0], over);

                    read[0] = hoveredX.getInt(track[0]);

                    arrive.invoke(track[0], to, false);

                    read[1] = hoveredX.getInt(track[0]);
                    read[2] = autonomyHover.get(track[0]);
                    read[3] = track[0].hoveredSquare();
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            settleTheEditor();

            assertEquals(read[0], 2, "precondition: the hover did not set the keys' square");

            assertTrue(track[0].isDisplayable(), "precondition: the switch to " + to + " gave the window up");

            assertEquals(read[1], -1, "after a switch to " + to + ", the keys still act on the square hovered on " + PAGE
                + " (Adam, 2026-10-04)");

            assertTrue(read[2] == null && read[3] == null, "after a switch to " + to + ", the autonomy editor's keys still"
                + " act on the square hovered on " + PAGE + " (Adam, 2026-10-04)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /**
     * Two requests to redraw the diagram, made before the first is drawn, draw it once (speed, 2026-10-04).
     *
     * A request made while a redraw was pending queued another one after it, though the pending one had not yet read
     * anything and so would draw the latest state anyway.  Opening autonomy mode made exactly this pair - the remembered
     * caption mode's redraw, then the one meant to follow it - and drew the grid three times instead of twice.
     *
     * MUTATION: queue a second redraw for a request made before the first has started, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTwoRedrawRequestsBeforeTheFirstIsDrawnDrawOnce() throws Exception
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

            final java.lang.reflect.Method redraw = LayoutEditor.class.getDeclaredMethod("refreshGrid");

            redraw.setAccessible(true);

            int before = org.traincontrol.gui.LayoutLabel.COUNT_CONSTRUCTED.get();

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    redraw.invoke(track[0]);
                    redraw.invoke(track[0]);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            settleTheEditor();

            int built = org.traincontrol.gui.LayoutLabel.COUNT_CONSTRUCTED.get() - before;

            // THE SQUARES A DRAW MAKES - the panel holds captions and overlays besides, which a draw does not count
            int perDraw = 0;

            for (java.awt.Component c : gridOf(track[0]).getContainer().getComponents())
            {
                if (c instanceof org.traincontrol.gui.LayoutLabel) perDraw++;
            }

            assertTrue(perDraw > 0 && built > 0, "precondition: nothing was drawn");

            assertEquals(built, perDraw, "two requests made before the first redraw had started drew the diagram "
                + ((double) built / perDraw) + " times (" + built + " squares at " + perDraw + " a draw)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /**
     * A click that changes a switch's directions refreshes the autonomy editor once (speed, 2026-10-04).
     *
     * The switch's own step refreshed - a whole setup check, the list, the strip - and the click refreshed again straight
     * after it, as it does for every click.
     *
     * A panel with a counting listener, and one click on a square with more than one route.
     *
     * MUTATION: let the switch's step refresh as well again, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAClickOnASwitchRefreshesOnce() throws Exception
    {
        TileKey branching = null;

        for (TileKey tile : session.getGraph().getTiles().keySet())
        {
            if (branching == null && PAGE.equals(tile.getPage()) && session.getRoutes(tile).size() > 1) branching = tile;
        }

        if (branching == null) throw new SkipException("no square with more than one route on " + PAGE);

        final TileKey square = branching;

        final org.traincontrol.base.LayoutDiagramComponent what = session.getGraph().getTiles().get(square);

        final org.json.JSONObject asFound = session.snapshotSetup();

        final int[] refreshed = {0};

        final AutonomyEditorPanel[] panel = new AutonomyEditorPanel[1];

        try
        {
            SwingUtilities.invokeAndWait(() -> panel[0] = new AutonomyEditorPanel(session, PAGE, () -> refreshed[0]++));

            refreshed[0] = 0;

            SwingUtilities.invokeAndWait(() -> panel[0].tileClicked(square, what, false));

            assertEquals(refreshed[0], 1, "one click on " + square + ", a switch, refreshed the autonomy editor "
                + refreshed[0] + " times - each a whole setup check");
        }
        finally
        {
            session.restoreSetup(asFound);
        }
    }

    /**
     * The square under the pointer keeps its hover outline through Escape though it wore another outline, and a square the
     * pointer left for the palette does not get it back (RSA35-C1).
     *
     * Round 54 drew the blue back on the square that last WORE it - and a square under the pointer can be wearing another
     * outline (a tile picked up, picked, the grip), so it got none; while the square the pointer left for the palette,
     * which nothing takes the blue off, got it back.  It is drawn now on the square the pointer is over, while it is over
     * the grid.
     *
     * A click picks up the tile under the pointer, then Escape; and two squares picked, a hover, the palette, then Escape.
     *
     * MUTATION: draw the blue back from the record again, or wherever the pointer was last, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTheHoverFollowsThePointerThroughEscape() throws Exception
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

            final String blue = "hl:" + java.awt.Color.BLUE.getRGB() + ":";

            // A TILE PICKED UP UNDER THE POINTER, then Escape
            final org.traincontrol.gui.LayoutLabel tile = aTrackSquare(track[0]);

            SwingUtilities.invokeAndWait(() -> track[0].receiveMoveEvent(new java.awt.event.MouseEvent(tile,
                java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, 0, false), tile));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            SwingUtilities.invokeAndWait(() -> track[0].receiveClickEvent(new java.awt.event.MouseEvent(tile,
                java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false,
                java.awt.event.MouseEvent.BUTTON1), tile));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(track[0].hasToolFlag(), "precondition: the click did not pick the tile up");

            escape(track[0]);

            assertTrue(keyOf(track[0], tile).startsWith(blue), "after Escape dropped the tile picked up under the pointer,"
                + " that square shows no hover outline (RSA35-C1): " + keyOf(track[0], tile));

            // TWO SQUARES PICKED, A HOVER, THE POINTER ONTO THE PALETTE, then Escape
            pick(track[0], 1, 1, 2, 1);

            final org.traincontrol.gui.LayoutLabel over = gridOf(track[0]).getValueAt(6, 5);

            assertNotNull(over, "precondition: no square at 6,5");

            SwingUtilities.invokeAndWait(() -> track[0].receiveMoveEvent(new java.awt.event.MouseEvent(over,
                java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, 0, false), over));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            final org.traincontrol.gui.LayoutLabel piece = aPalettePiece(track[0]);

            SwingUtilities.invokeAndWait(() -> track[0].receiveMoveEvent(new java.awt.event.MouseEvent(piece,
                java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, 0, false), piece));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            escape(track[0]);

            for (java.awt.Component c : gridOf(track[0]).getContainer().getComponents())
            {
                assertFalse(c instanceof javax.swing.JLabel && keyOf(track[0], (javax.swing.JLabel) c).startsWith(blue),
                    "after the pointer left for the palette, Escape drew the hover outline back on a square (RSA35-C1)");
            }
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /**
     * A box Escape dropped after its drag left the square it started on leaves that square's next click alone
     * (RSA35-C2).
     *
     * A press whose drag leaves its square ends in no click, so the click round 54 set aside to ignore waited for whatever
     * click came to that square next - a middle click that turns the tile, a right-click's menu.  It is forgotten at a
     * release off the square, and only a left click is ever ignored.
     *
     * Control+M, a box from a track square dragged two squares on, Escape, the release, then a middle click on the first
     * square: the tile turns.
     *
     * MUTATION: keep the click to ignore past a release off its square again, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testADroppedBoxsDragLeavesTheNextClickAlone() throws Exception
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

            // A TRACK SQUARE with a square two to its right
            org.traincontrol.gui.LayoutLabel start = null, end = null;

            final int[] at = new int[2];

            for (int y = 1; y < 20 && start == null; y++)
            {
                for (int x = 1; x < 20 && start == null; x++)
                {
                    org.traincontrol.gui.LayoutLabel here = grid.getValueAt(x, y), there = grid.getValueAt(x + 2, y);

                    if (here != null && there != null && !here.isSpacer() && !there.isSpacer() && here.getComponent() != null
                        && !here.getComponent().isText())
                    {
                        start = here;
                        end = there;
                        at[0] = x;
                        at[1] = y;
                    }
                }
            }

            assertNotNull(start, "precondition: no track square with a square two to its right on " + PAGE);

            final org.traincontrol.gui.LayoutLabel from = start, to = end;

            final int turnedWas = from.getComponent().getOrientation();

            SwingUtilities.invokeAndWait(() -> track[0].setSelectMode(true));

            SwingUtilities.invokeAndWait(() ->
            {
                int held = java.awt.event.InputEvent.BUTTON1_DOWN_MASK;

                track[0].receiveMoveEvent(new java.awt.event.MouseEvent(from, java.awt.event.MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(), 0, 1, 1, 0, false), from);
                track[0].beginDrag(new java.awt.event.MouseEvent(from, java.awt.event.MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), held, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1), from);
                track[0].receiveMoveEvent(new java.awt.event.MouseEvent(to, java.awt.event.MouseEvent.MOUSE_ENTERED,
                    System.currentTimeMillis(), 0, 1, 1, 0, false), to);
                track[0].updateDrag(new java.awt.event.MouseEvent(to, java.awt.event.MouseEvent.MOUSE_DRAGGED,
                    System.currentTimeMillis(), held, 1, 1, 0, false), to);
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            escape(track[0]);

            // THE RELEASE, delivered - as Swing delivers it - to the square pressed, with the pointer on the other
            SwingUtilities.invokeAndWait(() -> track[0].endDrag(new java.awt.event.MouseEvent(from,
                java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 1, 1, 1, false,
                java.awt.event.MouseEvent.BUTTON1), from));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            // A MIDDLE CLICK on the square the box started on
            SwingUtilities.invokeAndWait(() -> track[0].receiveClickEvent(new java.awt.event.MouseEvent(from,
                java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false,
                java.awt.event.MouseEvent.BUTTON2), from));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            org.traincontrol.base.LayoutDiagramComponent now = gridOf(track[0]).getValueAt(at[0], at[1]).getComponent();

            boolean turned = now != null && now.getOrientation() != turnedWas;

            // PUT BACK
            for (int i = 0; i < 8 && now != null && now.getOrientation() != turnedWas; i++)
            {
                final org.traincontrol.gui.LayoutLabel again = gridOf(track[0]).getValueAt(at[0], at[1]);

                SwingUtilities.invokeAndWait(() -> track[0].receiveClickEvent(new java.awt.event.MouseEvent(again,
                    java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false,
                    java.awt.event.MouseEvent.BUTTON2), again));

                for (int j = 0; j < 10; j++) SwingUtilities.invokeAndWait(() -> { });

                now = again.getComponent();
            }

            assertTrue(turned, "a middle click on the square a dropped box's drag had left was swallowed - the tile did not"
                + " turn (RSA35-C2)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /** A track square with a square two to its right, as {square, the one two along}, its column and row in at. */
    private static org.traincontrol.gui.LayoutLabel[] aTrackSquareWithRoom(LayoutEditor editor, int[] at)
        throws ReflectiveOperationException
    {
        final org.traincontrol.gui.LayoutGrid grid = gridOf(editor);

        for (int y = 1; y < 20; y++)
        {
            for (int x = 1; x < 20; x++)
            {
                org.traincontrol.gui.LayoutLabel here = grid.getValueAt(x, y), there = grid.getValueAt(x + 2, y);

                if (here != null && there != null && !here.isSpacer() && !there.isSpacer() && here.getComponent() != null
                    && !here.getComponent().isText())
                {
                    at[0] = x;
                    at[1] = y;

                    return new org.traincontrol.gui.LayoutLabel[] {here, there};
                }
            }
        }

        return null;
    }

    /** Turns the tile at a square back to an orientation with middle clicks, each on the square as it is drawn now. */
    private static void turnBack(LayoutEditor editor, int[] at, int orientation) throws Exception
    {
        for (int i = 0; i < 8; i++)
        {
            final org.traincontrol.gui.LayoutLabel again = gridOf(editor).getValueAt(at[0], at[1]);

            if (again.getComponent() == null || again.getComponent().getOrientation() == orientation) return;

            SwingUtilities.invokeAndWait(() -> editor.receiveClickEvent(new java.awt.event.MouseEvent(again,
                java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false,
                java.awt.event.MouseEvent.BUTTON2), again));

            for (int j = 0; j < 10; j++) SwingUtilities.invokeAndWait(() -> { });
        }
    }

    /**
     * Each tile of the editor's page tied to something, by square and kind, to what it is tied to.
     *
     * @param editor the editor
     * @return the ties, compared by identity
     * @throws ReflectiveOperationException reading the editor's page
     */
    private static java.util.Map<String, Object> tiesOf(LayoutEditor editor) throws ReflectiveOperationException
    {
        java.lang.reflect.Field field = LayoutEditor.class.getDeclaredField("layout");

        field.setAccessible(true);

        java.util.Map<String, Object> out = new java.util.TreeMap<>();

        for (org.traincontrol.base.LayoutDiagramComponent c : ((LayoutDiagram) field.get(editor)).getAll())
        {
            String at = c.getX() + "," + c.getY();

            if (c.getAccessory() != null) out.put(at + " accessory", c.getAccessory());
            if (c.getAccessory2() != null) out.put(at + " second accessory", c.getAccessory2());
            if (c.getFeedback() != null) out.put(at + " sensor", c.getFeedback());
            if (c.getRoute() != null) out.put(at + " route", c.getRoute());
        }

        return out;
    }

    /** How many ties of one kind. */
    private static long tiesOfKind(java.util.Map<String, Object> ties, String kind)
    {
        return ties.keySet().stream().filter(k -> k.endsWith(" " + kind)).count();
    }

    /**
     * An undo in the track diagram editor puts the page's tiles back still tied to their switches, signals, sensors and
     * route buttons (found by RSA36, outside its rounds).
     *
     * Every undo state is a copy of each tile, and the copy kept the address but not what the address was tied to when
     * the page was read: after one Control+Z every switch and signal of the page had no accessory until the page was read
     * again - a switch clicked on the main window's diagram did nothing, and the setup counted every one as unaddressed.
     *
     * A tile turned, then undone: every tie the page had before, it has after, to the same accessory, sensor or route.
     *
     * MUTATION: let the copy leave out the accessory, or the sensor, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testAnUndoKeepsTheTilesTiedToTheirAccessories() throws Exception
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

            final java.util.Map<String, Object> before = tiesOf(track[0]);

            assertTrue(tiesOfKind(before, "accessory") > 0 && tiesOfKind(before, "sensor") > 0, "precondition: "
                + PAGE + " has no switch or signal tied to an accessory, or no sensor tied to one: " + before.keySet());

            final int[] at = new int[2];

            final org.traincontrol.gui.LayoutLabel[] squares = aTrackSquareWithRoom(track[0], at);

            assertNotNull(squares, "precondition: no track square on " + PAGE);

            // A TILE TURNED, AND THE TURN UNDONE
            SwingUtilities.invokeAndWait(() ->
            {
                track[0].rotate(squares[0]);
                track[0].undo();
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            java.util.Map<String, Object> after = tiesOf(track[0]);

            java.util.Set<String> lost = new java.util.TreeSet<>(before.keySet());

            lost.removeAll(after.keySet());

            assertTrue(lost.isEmpty(), "after an undo " + lost.size() + " of " + before.size() + " ties on " + PAGE
                + " were lost - the first: " + (lost.isEmpty() ? "" : lost.iterator().next()));

            assertEquals(after, before, "after an undo a tile on " + PAGE + " is tied to something else");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /**
     * Control+K keeps the picked squares, the selection's grip and the pointer's blue outline (RSA44-C2).
     *
     * The grid's own redraw - Control+K and the Grid box, through `setShowGrid` - took every outline off until the pointer
     * entered another square, the sibling of the undo's redraw RSA43-C4 fixed.  Turned off and on again, so the setting
     * ends as it began.
     *
     * MUTATION: end `setShowGrid` without drawing the outlines back, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testControlKKeepsTheGripAndThePointersOutline() throws Exception
    {
        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        final boolean gridWas = LayoutEditor.showGrid();

        try
        {
            settleTheEditor();

            final int[] at = new int[2];

            assertNotNull(aTrackSquareWithRoom(track[0], at), "precondition: no track square on " + PAGE);

            java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

            gridField.setAccessible(true);

            final int x = at[0];
            final int y = at[1];

            // TWO SQUARES PICKED, AND THE POINTER OVER A THIRD
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) gridField.get(track[0]);

                    for (int dx = 0; dx < 2; dx++)
                    {
                        org.traincontrol.gui.LayoutLabel square = grid.getValueAt(x + dx, y);

                        track[0].receiveClickEvent(new java.awt.event.MouseEvent(square,
                            java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                            java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1,
                            false, java.awt.event.MouseEvent.BUTTON1), square);
                    }

                    org.traincontrol.gui.LayoutLabel over = grid.getValueAt(x, y + 1);

                    track[0].receiveMoveEvent(new java.awt.event.MouseEvent(over, java.awt.event.MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(), 0, 1, 1, 0, false), over);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(gripIsDrawn(gridField, track[0]) && wearsTheHover(track[0], squareAt(gridField, track[0], x, y + 1)),
                "precondition: before Control+K the selection's grip or the pointer's outline is not drawn");

            // CONTROL+K, twice - off and back - with the pointer where it was
            for (int turn = 0; turn < 2; turn++)
            {
                SwingUtilities.invokeAndWait(() -> track[0].setShowGrid(!LayoutEditor.showGrid()));

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

                assertTrue(gripIsDrawn(gridField, track[0]), "after Control+K the picked squares lost the grip that moves"
                    + " them, though it still moves them (RSA44-C2)");

                assertTrue(wearsTheHover(track[0], squareAt(gridField, track[0], x, y + 1)), "after Control+K the square the"
                    + " pointer is still over lost its blue outline (RSA44-C2)");
            }
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                if (LayoutEditor.showGrid() != gridWas) track[0].setShowGrid(gridWas);

                track[0].dispose();
            });
        }
    }

    /**
     * The pointer gone off the squares takes its blue outline with it, wherever it went, and a move onto the next square
     * does not; and the margin keeps the selection's grip (Adam, 2026-10-06: *"Fix them"*, on RSA45's notes).
     *
     * A square heard nothing when the pointer left it, so only the margin took the blue off - and a pointer can go
     * straight from a square onto the tools column, the Grid box beside the squares, crossing no margin.  The blue stayed
     * on the last square, and the Grid box, which draws the outlines back since round 76, drew it there again.  And the
     * margin took the grip off with the blue, until the pointer was on a square again.
     *
     * MUTATION: leave the blue where the pointer went off the squares, take it off on a move to the next square, or have
     * the margin take the grip off, and this fails.
     *
     * @throws Exception from the window or reflection
     */
    @Test
    public void testThePointerOffTheSquaresTakesItsOutlineWithIt() throws Exception
    {
        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        final boolean gridWas = LayoutEditor.showGrid();

        try
        {
            settleTheEditor();

            final int[] at = new int[2];

            assertNotNull(aTrackSquareWithRoom(track[0], at), "precondition: no track square on " + PAGE);

            final java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

            gridField.setAccessible(true);

            final int x = at[0];
            final int y = at[1];

            // TWO SQUARES PICKED, AND THE POINTER OVER A THIRD
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) gridField.get(track[0]);

                    for (int dx = 0; dx < 2; dx++)
                    {
                        org.traincontrol.gui.LayoutLabel square = grid.getValueAt(x + dx, y);

                        track[0].receiveClickEvent(new java.awt.event.MouseEvent(square,
                            java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                            java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1,
                            false, java.awt.event.MouseEvent.BUTTON1), square);
                    }

                    org.traincontrol.gui.LayoutLabel over = grid.getValueAt(x, y + 1);

                    track[0].receiveMoveEvent(new java.awt.event.MouseEvent(over, java.awt.event.MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(), 0, 1, 1, 0, false), over);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            final javax.swing.JLabel over = squareAt(gridField, track[0], x, y + 1);
            final javax.swing.JLabel next = squareAt(gridField, track[0], x + 1, y + 1);

            assertNotNull(next, "precondition: no square beside the one the pointer is over");

            assertTrue(gripIsDrawn(gridField, track[0]) && wearsTheHover(track[0], over), "precondition: the selection's"
                + " grip or the pointer's outline is not drawn");

            // ONTO THE NEXT SQUARE: leaving one square for another takes nothing off - the next square's own hover moves it
            leaveFor(over, next.getX() - over.getX() + next.getWidth() / 2, next.getY() - over.getY() + next.getHeight() / 2);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(wearsTheHover(track[0], over), "a move onto the next square took the pointer's outline off before that"
                + " square's own hover drew it");

            // STRAIGHT OFF THE SQUARES - onto the tools column and the Grid box beside them - crossing no margin
            java.awt.Point outside = SwingUtilities.convertPoint(over.getParent(), new java.awt.Point(-20, -20), over);

            leaveFor(over, outside.x, outside.y);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(wearsTheHover(track[0], over), "the pointer went off the squares, crossing no margin, and its outline"
                + " stayed on the last square it was over");

            assertTrue(gripIsDrawn(gridField, track[0]), "the pointer going off the squares took the selection's grip off");

            // THE GRID BOX, twice, the pointer on it: the outlines drawn back, and not the pointer's - asked of the squares
            // the redraw built, which are new ones
            for (int turn = 0; turn < 2; turn++)
            {
                SwingUtilities.invokeAndWait(() -> track[0].setShowGrid(!LayoutEditor.showGrid()));

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

                for (java.awt.Component c : squareAt(gridField, track[0], x, y + 1).getParent().getComponents())
                {
                    assertFalse(c instanceof javax.swing.JLabel && wearsTheHover(track[0], (javax.swing.JLabel) c), "the"
                        + " Grid box drew the pointer's outline back on a square the pointer had left for it (RSA45, noted)");
                }

                assertTrue(gripIsDrawn(gridField, track[0]), "after the Grid box the picked squares lost their grip"
                    + " (RSA44-C2)");
            }

            // THE MARGIN, from a square: the pointer's outline off, and the grip kept
            final javax.swing.JLabel again = squareAt(gridField, track[0], x, y + 1);

            final org.traincontrol.gui.LayoutLabel square = (org.traincontrol.gui.LayoutLabel) again;

            SwingUtilities.invokeAndWait(() -> track[0].receiveMoveEvent(new java.awt.event.MouseEvent(square,
                java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, 0, false), square));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(wearsTheHover(track[0], again), "precondition: the pointer's outline was not drawn again");

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

            assertFalse(wearsTheHover(track[0], again), "entering the margin did not take the pointer's outline off"
                + " (RSA34-C1)");

            assertTrue(gripIsDrawn(gridField, track[0]), "entering the margin took the selection's grip off, though it"
                + " still moves the picked squares (RSA45, noted)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                if (LayoutEditor.showGrid() != gridWas) track[0].setShowGrid(gridWas);

                track[0].dispose();
            });
        }
    }

    /**
     * The pointer gone off the squares from what covers one - an accessory's address label, or a menu that closed over the
     * tools column - takes its blue outline with it too; so does a square entered and left in one go, and a square left for
     * the grid's padding (RSA46-C1, -C2; Adam, 2026-10-06: *"Do fix C1 and C2"*).
     *
     * An address label passed a square its entry but not its exit; a menu takes the square's exit while the pointer is
     * still over the square, and nothing asked again when it closed.  The Grid box then drew the blue back on a square
     * the pointer had left.  The pass that takes the outline off is posted, behind the one the last square's hover
     * posted; and the padding beside the last column is no square.
     *
     * MUTATION: let an address label keep the square's exit to itself, let the window not hear the pointer arrive off the
     * squares, take the outline off at once rather than after the hover's pass, or count the padding as a square - and this
     * fails.
     *
     * @throws Exception from the window or reflection
     */
    @Test
    public void testThePointerOffTheSquaresFromWhatCoversThemTakesItsOutline() throws Exception
    {
        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        final boolean gridWas = LayoutEditor.showGrid();
        final boolean[] addressesOn = new boolean[1];

        try
        {
            settleTheEditor();

            final java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

            gridField.setAccessible(true);

            // ADDRESSES SHOWN, as the box shows them - turned on only when they are off: the run's preferences start as the
            // operator's, and his Show Addresses may already have them on, which a toggle would turn off
            final java.lang.reflect.Field drawn = LayoutEditor.class.getDeclaredField("layout");

            drawn.setAccessible(true);

            final LayoutDiagram shown = (LayoutDiagram) drawn.get(track[0]);

            SwingUtilities.invokeAndWait(() ->
            {
                if (!shown.getShowAddress())
                {
                    track[0].toggleAddresses();

                    addressesOn[0] = true;
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            final javax.swing.JPanel squares = gridOf(track[0]).getContainer();

            // AN ADDRESS LABEL, and the square it lies on
            javax.swing.JLabel address = null;
            org.traincontrol.gui.LayoutLabel under = null;

            for (java.awt.Component c : squares.getComponents())
            {
                // the address label itself (it was a plain label holding HTML until round 102 gave it a halo)
                if (c instanceof org.traincontrol.gui.AddressLabel)
                {
                    for (java.awt.Component s : squares.getComponents())
                    {
                        if (s instanceof org.traincontrol.gui.LayoutLabel && !((org.traincontrol.gui.LayoutLabel) s).isSpacer()
                            && s.getBounds().contains(c.getX() + 1, c.getY() + 1))
                        {
                            address = (javax.swing.JLabel) c;
                            under = (org.traincontrol.gui.LayoutLabel) s;
                        }
                    }

                    if (address != null) break;
                }
            }

            assertNotNull(address, "precondition: no accessory's address label on " + PAGE);

            final javax.swing.JLabel label = address;
            final org.traincontrol.gui.LayoutLabel square = under;

            // (a) OVER THE SQUARE, ONTO ITS ADDRESS, AND FROM THERE STRAIGHT OFF THE SQUARES
            hoverOver(track[0], square);

            assertTrue(wearsTheHover(track[0], square), "precondition: the pointer's outline was not drawn on the square");

            leaveFor(square, label.getX() - square.getX() + 2, label.getY() - square.getY() + 2);

            SwingUtilities.invokeAndWait(() -> label.dispatchEvent(new java.awt.event.MouseEvent(label,
                java.awt.event.MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 2, 2, 0, false)));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(wearsTheHover(track[0], square), "precondition: moving onto the square's own address label took its"
                + " outline off");

            java.awt.Point off = SwingUtilities.convertPoint(squares, new java.awt.Point(-20, -20), label);

            leaveFor(label, off.x, off.y);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(anyWearsTheHover(track[0]), "the pointer went off the squares from an accessory's address label, and"
                + " its outline stayed on the square (RSA46-C1)");

            theGridBoxTwice(track[0], "after the pointer left from an address label (RSA46-C1)");

            // (b) A MENU OVER A SQUARE, then the pointer out from under it over the tools column
            final org.traincontrol.gui.LayoutLabel menued = squareNextTo(gridOf(track[0]).getContainer(), square);

            hoverOver(track[0], menued);

            assertTrue(wearsTheHover(track[0], menued), "precondition: the pointer's outline was not drawn on the square");

            // The menu opening over the square takes its exit where the pointer still is
            leaveFor(menued, menued.getWidth() / 2, menued.getHeight() / 2);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(wearsTheHover(track[0], menued), "a menu opened over a square took the outline off the square it is"
                + " about");

            java.lang.reflect.Field box = LayoutEditor.class.getDeclaredField("showGridCheckbox");

            box.setAccessible(true);

            final java.awt.Component tools = (java.awt.Component) box.get(track[0]);

            SwingUtilities.invokeAndWait(() -> tools.dispatchEvent(new java.awt.event.MouseEvent(tools,
                java.awt.event.MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 2, 2, 0, false)));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(anyWearsTheHover(track[0]), "the pointer came out from under a menu onto the tools column, and the"
                + " outline stayed on the square the menu was over (RSA46-C1)");

            theGridBoxTwice(track[0], "after the pointer came out from under a menu (RSA46-C1)");

            // ONTO A SQUARE AND STRAIGHT OFF THE SQUARES, IN ONE GO, nothing picked: the pass that takes the outline off
            // lands after the one the hover queued (RSA46-C2)
            final org.traincontrol.gui.LayoutLabel quick = squareNextTo(gridOf(track[0]).getContainer(), menued);

            SwingUtilities.invokeAndWait(() ->
            {
                track[0].receiveMoveEvent(new java.awt.event.MouseEvent(quick, java.awt.event.MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(), 0, 1, 1, 0, false), quick);

                java.awt.Point gone = SwingUtilities.convertPoint(quick.getParent(), new java.awt.Point(-20, -20), quick);

                quick.dispatchEvent(new java.awt.event.MouseEvent(quick, java.awt.event.MouseEvent.MOUSE_EXITED,
                    System.currentTimeMillis(), 0, gone.x, gone.y, 0, false));
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(anyWearsTheHover(track[0]), "a square entered and then left for off the squares in one go kept its"
                + " outline: the pass that takes it off ran before the hover's (RSA46-C2)");

            // ONTO THE GRID'S PADDING, beside the last column: no square (RSA46-C2)
            javax.swing.JPanel now = gridOf(track[0]).getContainer();

            org.traincontrol.gui.LayoutLabel spacer = null, beside = null;

            for (java.awt.Component c : now.getComponents())
            {
                if (!(c instanceof org.traincontrol.gui.LayoutLabel) || !((org.traincontrol.gui.LayoutLabel) c).isSpacer()) continue;

                for (java.awt.Component s : now.getComponents())
                {
                    if (s instanceof org.traincontrol.gui.LayoutLabel && !((org.traincontrol.gui.LayoutLabel) s).isSpacer()
                        && s.getY() == c.getY() && s.getX() + s.getWidth() == c.getX())
                    {
                        spacer = (org.traincontrol.gui.LayoutLabel) c;
                        beside = (org.traincontrol.gui.LayoutLabel) s;
                    }
                }

                if (spacer != null) break;
            }

            assertNotNull(spacer, "precondition: no square beside the grid's padding on " + PAGE);

            final org.traincontrol.gui.LayoutLabel last = beside;

            hoverOver(track[0], last);

            assertTrue(wearsTheHover(track[0], last), "precondition: the pointer's outline was not drawn on the last column");

            leaveFor(last, spacer.getX() - last.getX() + spacer.getWidth() / 2, spacer.getY() - last.getY()
                + spacer.getHeight() / 2);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(anyWearsTheHover(track[0]), "the pointer went from the last column onto the grid's padding, and the"
                + " outline stayed: the padding was taken for a square (RSA46-C2)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                if (addressesOn[0]) track[0].toggleAddresses();

                if (LayoutEditor.showGrid() != gridWas) track[0].setShowGrid(gridWas);

                track[0].dispose();
            });
        }
    }

    /**
     * The pointer is found off the squares however it gets there, and only from this window: out from under the
     * right-click menu once it closes, back into the window over a part nothing listens on, or past the edge of the view
     * from a square cut by it - and not by an arrival in a dialog over the square.  The watch is on the toolkit while the
     * window is, and gone with it (RSA47-C1 to -C3).
     *
     * A menu takes the square's exit where the pointer still is, and while it was open the watch left the outline alone;
     * nothing asked again when it closed.  The window's own entry, which AWT hands on only where nothing listens, was
     * asked whose window it was in, and a window is in none.  And a square half scrolled out of sight counted as a square
     * all the way across.
     *
     * MUTATION: ask nothing when the menu closes, leave the window's own entry out, count the hidden half of a square,
     * act on an arrival in another window, or leave the watch on the toolkit after dispose - and this fails.
     *
     * @throws Exception from the windows or reflection
     */
    @Test
    public void testThePointerIsFoundOffTheSquaresWhereverItGoes() throws Exception
    {
        int watchesBefore = pointerWatches();

        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        javax.swing.JDialog over = null;

        try
        {
            settleTheEditor();

            assertEquals(pointerWatches(), watchesBefore + 1, "the editor window put no watch on the toolkit, or more than"
                + " one");

            final int[] at = new int[2];

            assertNotNull(aTrackSquareWithRoom(track[0], at), "precondition: no track square on " + PAGE);

            final java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

            gridField.setAccessible(true);

            final org.traincontrol.gui.LayoutLabel p = (org.traincontrol.gui.LayoutLabel) squareAt(gridField, track[0], at[0],
                at[1]);

            // (C1) A MENU OVER THE SQUARE, closed with the pointer off the squares
            hoverOver(track[0], p);

            assertTrue(wearsTheHover(track[0], p), "precondition: the pointer's outline was not drawn on the square");

            leaveFor(p, p.getWidth() / 2, p.getHeight() / 2);

            java.lang.reflect.Method made = LayoutEditor.class.getDeclaredMethod("menuFor",
                org.traincontrol.gui.LayoutLabel.class, org.traincontrol.base.LayoutDiagramComponent.class);

            made.setAccessible(true);

            final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    menu[0] = (javax.swing.JPopupMenu) made.invoke(track[0], p, page.getComponent(at[0], at[1]));
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(wearsTheHover(track[0], p), "precondition: a menu over the square took its outline off");

            // WHERE THE POINTER IS, said through the editor's own reader rather than read off the screen (RSA48-C3): the
            // real pointer is wherever it was left, and this window is off the screen
            final java.lang.reflect.Field pointerOver = LayoutEditor.class.getDeclaredField("pointerOver");

            pointerOver.setAccessible(true);

            final Object theScreens = pointerOver.get(track[0]);

            final javax.swing.JPanel panelOfSquares = gridOf(track[0]).getContainer();

            final java.awt.Point overP = SwingUtilities.convertPoint(p, p.getWidth() / 2, p.getHeight() / 2,
                panelOfSquares);

            // (RSA48-C3) CLOSED WITH THE POINTER OVER THE SQUARE: the outline stays
            pointerOver.set(track[0], (java.util.function.Function<javax.swing.JPanel, java.awt.Point>) on -> overP);

            closeTheMenu(menu[0]);

            assertTrue(wearsTheHover(track[0], p), "the right-click menu closed with the pointer over the square it was"
                + " opened on, and the outline was taken off (RSA48-C3)");

            // (C1) AND OFF THE SQUARES: it goes
            pointerOver.set(track[0], (java.util.function.Function<javax.swing.JPanel, java.awt.Point>) on -> null);

            closeTheMenu(menu[0]);

            pointerOver.set(track[0], theScreens);

            assertFalse(anyWearsTheHover(track[0]), "the right-click menu closed with the pointer off the squares, and the"
                + " outline stayed on the square it was over (RSA47-C1)");

            // (C1) BACK INTO THE WINDOW WHERE NOTHING LISTENS: the window's own entry, as AWT hands it to the watch
            final org.traincontrol.gui.LayoutLabel q = (org.traincontrol.gui.LayoutLabel) squareAt(gridField, track[0], at[0],
                at[1]);

            hoverOver(track[0], q);

            assertTrue(wearsTheHover(track[0], q), "precondition: the pointer's outline was not drawn again");

            leaveFor(q, q.getWidth() / 2, q.getHeight() / 2);

            final java.awt.event.AWTEventListener watch = theWatchOf(track[0]);

            assertNotNull(watch, "precondition: no watch of this window's on the toolkit");

            SwingUtilities.invokeAndWait(() -> watch.eventDispatched(new java.awt.event.MouseEvent(track[0],
                java.awt.event.MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 1, 1, 0, false)));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(anyWearsTheHover(track[0]), "the pointer came back into the window over a part nothing listens on,"
                + " and the outline stayed (RSA47-C1)");

            // (C3) AN ARRIVAL IN ANOTHER WINDOW - a dialog over the square - leaves the outline where it is
            final org.traincontrol.gui.LayoutLabel r = (org.traincontrol.gui.LayoutLabel) squareAt(gridField, track[0], at[0],
                at[1]);

            hoverOver(track[0], r);

            final javax.swing.JButton inTheDialog = new javax.swing.JButton("OK");

            final javax.swing.JDialog[] dialog = new javax.swing.JDialog[1];

            SwingUtilities.invokeAndWait(() ->
            {
                dialog[0] = new javax.swing.JDialog(track[0]);
                dialog[0].getContentPane().add(inTheDialog);
                dialog[0].pack();
            });

            over = dialog[0];

            SwingUtilities.invokeAndWait(() -> inTheDialog.dispatchEvent(new java.awt.event.MouseEvent(inTheDialog,
                java.awt.event.MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 2, 2, 0, false)));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(wearsTheHover(track[0], r), "an arrival in a dialog over the square took its outline off, as though"
                + " the pointer had gone somewhere else in the editor (RSA47-C3)");

            // (C2) PAST THE EDGE OF THE VIEW, from a square half scrolled out of sight - the window narrowed first, so the
            // view scrolls and keeps where it is scrolled to: a view that fits is put back at 0 by the next layout
            SwingUtilities.invokeAndWait(() ->
            {
                track[0].setSize(560, 560);
                track[0].validate();
            });

            settleTheEditor();

            javax.swing.JPanel squares = gridOf(track[0]).getContainer();

            final javax.swing.JViewport viewport = (javax.swing.JViewport) SwingUtilities.getAncestorOfClass(
                javax.swing.JViewport.class, squares);

            assertNotNull(viewport, "precondition: the squares are not in a scrolled view");

            final int room = squares.getWidth() - viewport.getExtentSize().width;

            assertTrue(room > 0, "precondition: the view does not scroll in this window - " + viewport.getExtentSize()
                + " of " + squares.getSize());

            org.traincontrol.gui.LayoutLabel cut = null;

            for (java.awt.Component c : squares.getComponents())
            {
                if (c instanceof org.traincontrol.gui.LayoutLabel && !((org.traincontrol.gui.LayoutLabel) c).isSpacer()
                    && c.getX() > c.getWidth() && c.getY() > 0 && c.getWidth() > 4 && c.getX() + c.getWidth() / 2 <= room
                    && (cut == null || c.getX() < cut.getX()))
                {
                    cut = (org.traincontrol.gui.LayoutLabel) c;
                }
            }

            assertNotNull(cut, "precondition: no square away from the left edge to scroll half out of sight");

            final org.traincontrol.gui.LayoutLabel half = cut;

            hoverOver(track[0], half);

            SwingUtilities.invokeAndWait(() -> viewport.setViewPosition(new java.awt.Point(half.getX() + half.getWidth() / 2,
                0)));

            assertFalse(squares.getVisibleRect().contains(half.getX() + half.getWidth() / 4, half.getY() + 1), "precondition:"
                + " the left half of the square is still in view");

            assertTrue(wearsTheHover(track[0], half), "precondition: the pointer's outline is not on the half-hidden square");

            leaveFor(half, half.getWidth() / 4, half.getHeight() / 2);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(anyWearsTheHover(track[0]), "the pointer left a square cut by the edge of the view, past that edge,"
                + " and the outline stayed: the hidden half counted as a square (RSA47-C2)");

            SwingUtilities.invokeAndWait(() -> viewport.setViewPosition(new java.awt.Point(0, 0)));
        }
        finally
        {
            final javax.swing.JDialog closing = over;

            SwingUtilities.invokeAndWait(() ->
            {
                if (closing != null) closing.dispose();

                track[0].dispose();
            });
        }

        // (C3) THE WATCH GONE WITH THE WINDOW
        assertEquals(pointerWatches(), watchesBefore, "the editor's watch stayed on the toolkit after the window was"
            + " disposed, holding nothing and called on every mouse event for the rest of the session (RSA47-C3)");
    }

    /** The toolkit's listeners that are editor windows' pointer watches. */
    private static int pointerWatches()
    {
        int n = 0;

        for (java.awt.event.AWTEventListener l : java.awt.Toolkit.getDefaultToolkit().getAWTEventListeners())
        {
            java.awt.event.AWTEventListener inner = l instanceof java.awt.event.AWTEventListenerProxy
                ? ((java.awt.event.AWTEventListenerProxy) l).getListener() : l;

            if (inner.getClass().getName().endsWith("$PointerWatch")) n++;
        }

        return n;
    }

    /** This editor window's pointer watch, as the toolkit holds it. */
    private static java.awt.event.AWTEventListener theWatchOf(LayoutEditor editor) throws Exception
    {
        java.lang.reflect.Field field = LayoutEditor.class.getDeclaredField("pointerWatch");

        field.setAccessible(true);

        return (java.awt.event.AWTEventListener) field.get(editor);
    }

    /** The pointer's hover over a square, as its own listener gives it, and the event thread after it. */
    private static void hoverOver(final LayoutEditor editor, final org.traincontrol.gui.LayoutLabel square) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> editor.receiveMoveEvent(new java.awt.event.MouseEvent(square,
            java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, 0, false), square));

        for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });
    }

    /** Whether any square of the editor's grid as it stands wears the pointer's outline. */
    private static boolean anyWearsTheHover(LayoutEditor editor) throws Exception
    {
        for (java.awt.Component c : gridOf(editor).getContainer().getComponents())
        {
            if (c instanceof javax.swing.JLabel && wearsTheHover(editor, (javax.swing.JLabel) c)) return true;
        }

        return false;
    }

    /** The Grid box twice, the pointer on it: no square may wear the pointer's outline after either. */
    private static void theGridBoxTwice(final LayoutEditor editor, String when) throws Exception
    {
        for (int turn = 0; turn < 2; turn++)
        {
            SwingUtilities.invokeAndWait(() -> editor.setShowGrid(!LayoutEditor.showGrid()));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(anyWearsTheHover(editor), "the Grid box drew the pointer's outline back on a square " + when);
        }
    }

    /** A square of the grid that is not this one, and not the padding: the nearest to its right or below it. */
    private static org.traincontrol.gui.LayoutLabel squareNextTo(javax.swing.JPanel squares,
        org.traincontrol.gui.LayoutLabel not)
    {
        org.traincontrol.gui.LayoutLabel best = null;

        for (java.awt.Component c : squares.getComponents())
        {
            if (!(c instanceof org.traincontrol.gui.LayoutLabel) || ((org.traincontrol.gui.LayoutLabel) c).isSpacer()) continue;

            if (c.getBounds().equals(not.getBounds()) || c.getWidth() == 0) continue;

            if (best == null || c.getLocation().distance(not.getLocation()) < best.getLocation().distance(not.getLocation()))
            {
                best = (org.traincontrol.gui.LayoutLabel) c;
            }
        }

        return best;
    }

    /** The pointer leaving a square for a point in the square's own coordinates, as AWT tells the square. */
    private static void leaveFor(final java.awt.Component square, final int x, final int y) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> square.dispatchEvent(new java.awt.event.MouseEvent(square,
            java.awt.event.MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, x, y, 0, false)));
    }

    /**
     * The click's tooltip line is for the visit: a page switch keeps it gone, so do the editor's own reopenings, and the
     * next opening through the main window says it again (RSA44-C1, -C4).
     *
     * Adam, on MT-697: *"Stop showing it after the user has clicked any square"*, and then *"have that tooltip tracking
     * state reset after the editor is closed"*.  A finding or a notice on another page, and a link's other end, change page
     * by closing the window and opening another - the user closed nothing, so the click goes with them; and the opening
     * after the user closes the editor is on the same main window and setup, where a later change would be likeliest to
     * keep the click.
     *
     * The class's own editor is put away for this, so the main window opens its own, and built again after.
     *
     * Each of the three doors is gone through twice: from a window a square was clicked in, where the line stays gone, and
     * from one nothing was clicked in, where it is still said (RSA45-C2).
     *
     * MUTATION: let a page switch, or any of the editor's three own reopenings, forget the click or hand on one nobody
     * made; or keep it in the main window past the editor's closing - and this fails.
     *
     * @throws Exception from the windows or reflection
     */
    @Test(timeOut = 300000)
    public void testTheClickLineIsForTheVisit() throws Exception
    {
        // THE CLASS'S EDITOR PUT AWAY - by dispose, not its X: the other claims leave it holding edits, which its X would ask
        // about on the screen
        final LayoutEditor first = editor;

        SwingUtilities.invokeAndWait(() -> first.dispose());

        // THE MAIN WINDOW AS AFTER EVERY EDITOR HAS CLOSED: other claims of this class build editors of their own and put
        // them away with a bare dispose, which does not give Edit back - and a door asked while Edit is grey answers with a
        // dialog that waits on the screen
        SwingUtilities.invokeAndWait(() -> ui.setEditLayoutEnabled(true));

        // Every window this claim opens, put away in the finally
        List<LayoutEditor> opened = new ArrayList<>();

        LayoutEditor a = null;

        try
        {
            String says = I18n.t("autosetup.ui.tooltipClickToChangeDirections");

            // OPENED AS THE MAIN WINDOW OPENS IT
            a = openedBy(() -> ui.openAutonomyEditor(new TileKey(PAGE, 1, 1)), null);

            opened.add(a);

            int[] track = new int[2];

            assertNotNull(aTrackSquareWithRoom(a, track), "precondition: no track square on " + PAGE);

            assertTrue(says(a, track, says), "precondition: an editor just opened does not say what a click does");

            // ANY SQUARE CLICKED - a blank one
            int[] blank = blankSquare(a);

            final LayoutEditor clicking = a;
            final org.traincontrol.gui.LayoutLabel square = gridOf(a).getValueAt(blank[0], blank[1]);

            SwingUtilities.invokeAndWait(() -> clicking.receiveClickEvent(new java.awt.event.MouseEvent(square,
                java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
                1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1), square));

            settleTheEditor();

            assertFalse(says(a, track, says), "after a square was clicked the track still says what a click does");

            // A PAGE SWITCH IN PLACE keeps it gone - with nothing unsaved, so it asks nothing
            assertFalse(unsaved(a), "precondition: the editor has unsaved work, so leaving the page would ask on the screen");

            java.lang.reflect.Method leave = LayoutEditor.class.getDeclaredMethod("leaveFor", String.class, boolean.class);

            leave.setAccessible(true);

            final LayoutEditor switching = a;

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    leave.invoke(switching, OTHER_PAGE, true);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            settleTheEditor();

            assertTrue(a.isDisplayable(), "precondition: the page switch closed the window, so it was not a switch in place");

            int[] there = new int[2];

            assertNotNull(aTrackSquareWithRoom(a, there), "precondition: no track square on " + OTHER_PAGE);

            assertFalse(says(a, there, says), "after a page switch in place the track says what a click does again, though"
                + " the visit is the same (MT-699 step 3)");

            // THE EDITOR'S OWN REOPENINGS - a finding on another page, a guard notice on another page, a link's other end:
            // each closes the window and has another opened, and the user closed nothing - the same visit (RSA44-C1), by
            // every door (RSA45-C2)
            String[][] clicked = {{"finding", PAGE}, {"notice", OTHER_PAGE}, {"link", PAGE}};

            LayoutEditor from = a;

            for (String[] door : clicked)
            {
                LayoutEditor to = throughDoor(from, door[0], new TileKey(door[1], 1, 1));

                opened.add(to);

                int[] again = new int[2];

                assertNotNull(aTrackSquareWithRoom(to, again), "precondition: no track square on " + door[1]);

                assertFalse(says(to, again, says), "the window the " + door[0] + "'s door opened says what a click does"
                    + " again, though the user closed nothing (RSA44-C1, RSA45-C2)");

                from = to;
            }

            // CLOSED BY THE USER - the X - and opened again through the same main window: said again (RSA44-C4)
            closeAsTheUserDoes(from);

            assertFalse(from.isDisplayable(), "precondition: the X did not close the editor");

            LayoutEditor c = openedBy(() -> ui.openAutonomyEditor(new TileKey(PAGE, 1, 1)), from);

            opened.add(c);

            int[] fresh = new int[2];

            assertNotNull(aTrackSquareWithRoom(c, fresh), "precondition: no track square on " + PAGE);

            assertTrue(says(c, fresh, says), "an editor opened after the user closed the one a square was clicked in does"
                + " not say what a click does - Adam: \"have that tooltip tracking state reset after the editor is closed\"");

            // AND NO DOOR HANDS ON A CLICK NOBODY MADE (RSA45-C2): from a window nothing was clicked in, each door's window
            // still says what a click does
            String[][] unclicked = {{"finding", OTHER_PAGE}, {"notice", PAGE}, {"link", OTHER_PAGE}};

            from = c;

            for (String[] door : unclicked)
            {
                LayoutEditor to = throughDoor(from, door[0], new TileKey(door[1], 1, 1));

                opened.add(to);

                int[] still = new int[2];

                assertNotNull(aTrackSquareWithRoom(to, still), "precondition: no track square on " + door[1]);

                assertTrue(says(to, still, says), "the window the " + door[0] + "'s door opened from one nothing was"
                    + " clicked in does not say what a click does - a click handed on that nobody made (RSA45-C2)");

                from = to;
            }
        }
        finally
        {
            for (LayoutEditor open : opened)
            {
                if (open != null)
                {
                    final LayoutEditor closing = open;

                    SwingUtilities.invokeAndWait(() -> { if (closing.isDisplayable()) closing.dispose(); });
                }
            }

            settleTheEditor();

            // Edit as every editor closed leaves it, whatever stopped this claim part way
            SwingUtilities.invokeAndWait(() -> ui.setEditLayoutEnabled(true));

            // THE CLASS'S EDITOR BACK, as setUpClass built it
            final LayoutEditor[] built = new LayoutEditor[1];

            SwingUtilities.invokeAndWait(() ->
            {
                built[0] = new LayoutEditor(page, 30, ui, 0);
                built[0].render();
                built[0].setAutonomyMode(session);
            });

            editor = built[0];

            settleTheEditor();
        }
    }

    /**
     * While an editor window is open the main window is not kept on top of it, even when the editor switches page in
     * place, and closing the editor puts the operator's setting back (Adam, 2026-10-06: "when opening the autonomy
     * editor, temporarily turn off 'always on top' in the main window, then restore it ... seems to have regressed").
     *
     * Opening did take it off.  But a page or mode switch inside the editor runs the refresh a closing editor runs, and
     * that refresh put the setting back with the editor still open - the main window came up over it.
     *
     * MUTATION: let the refresh apply the setting whatever is open, or not apply it when the editor goes, and this fails.
     *
     * @throws Exception from the windows or reflection
     */
    @Test(timeOut = 300000)
    public void testTheMainWindowStaysBelowAnOpenEditor() throws Exception
    {
        final String key = TrainControlUI.ONTOP_SETTING_PREF;
        final String was = TrainControlUI.getPrefs().get(key, null);
        final boolean onTopWas = ui.isAlwaysOnTop();

        final LayoutEditor first = editor;

        SwingUtilities.invokeAndWait(() -> first.dispose());
        SwingUtilities.invokeAndWait(() -> ui.setEditLayoutEnabled(true));

        LayoutEditor a = null;

        try
        {
            // THE OPERATOR WANTS IT ON TOP
            TrainControlUI.getPrefs().putBoolean(key, true);

            SwingUtilities.invokeAndWait(() -> ui.setAlwaysOnTop(true));

            a = openedBy(() -> ui.openAutonomyEditor(new TileKey(PAGE, 1, 1)), null);

            assertFalse(ui.isAlwaysOnTop(), "precondition: opening the autonomy editor left the main window on top of it");

            // A PAGE SWITCH IN PLACE, nothing unsaved
            assertFalse(unsaved(a), "precondition: the editor has unsaved work, so leaving the page would ask on the screen");

            java.lang.reflect.Method leave = LayoutEditor.class.getDeclaredMethod("leaveFor", String.class, boolean.class);

            leave.setAccessible(true);

            final LayoutEditor switching = a;

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    leave.invoke(switching, OTHER_PAGE, true);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            settleTheEditor();

            assertTrue(a.isDisplayable(), "precondition: the page switch closed the window, so it was not a switch in place");

            assertFalse(ui.isAlwaysOnTop(), "after a page switch inside the editor the main window is kept on top of it"
                + " again - the editor's own refresh put the setting back with the editor still open");

            // CLOSED BY THE USER: the setting back
            closeAsTheUserDoes(a);

            settleTheEditor();

            assertTrue(ui.isAlwaysOnTop(), "after the editor closed, the main window is not put back on top as the"
                + " operator's setting asks");
        }
        finally
        {
            if (a != null)
            {
                final LayoutEditor closing = a;

                SwingUtilities.invokeAndWait(() -> { if (closing.isDisplayable()) closing.dispose(); });
            }

            settleTheEditor();

            if (was == null) TrainControlUI.getPrefs().remove(key);
            else TrainControlUI.getPrefs().put(key, was);

            SwingUtilities.invokeAndWait(() -> ui.setAlwaysOnTop(onTopWas));

            SwingUtilities.invokeAndWait(() -> ui.setEditLayoutEnabled(true));

            final LayoutEditor[] built = new LayoutEditor[1];

            SwingUtilities.invokeAndWait(() ->
            {
                built[0] = new LayoutEditor(page, 30, ui, 0);
                built[0].render();
                built[0].setAutonomyMode(session);
            });

            editor = built[0];

            settleTheEditor();
        }
    }

    /** The other page of the frozen railway, for a switch in place. */
    private static final String OTHER_PAGE = "2 - Bottom";

    /**
     * The editor one of the editor's own reopenings opens: a finding on another page, a guard notice on another page, or
     * a link's other end - each closes the window it is in and has the main window open another.
     *
     * @param from the window the door is in, with nothing unsaved
     * @param door "finding", "notice" or "link"
     * @param tile the square it goes to
     * @return the editor opened
     */
    private static LayoutEditor throughDoor(LayoutEditor from, String door, TileKey tile) throws Exception
    {
        // NOTHING UNSAVED, so the link's door asks nothing on the screen
        assertFalse(unsaved(from), "precondition: the editor has unsaved work, so the " + door + "'s door would ask on the"
            + " screen");

        Runnable opening;

        if ("finding".equals(door))
        {
            java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("onJumpToPage");

            field.setAccessible(true);

            @SuppressWarnings("unchecked")
            final java.util.function.Consumer<TileKey> toPage =
                (java.util.function.Consumer<TileKey>) field.get(from.getAutonomyPanel());

            opening = () -> toPage.accept(tile);
        }
        else if ("notice".equals(door))
        {
            java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("onJumpToNotice");

            field.setAccessible(true);

            @SuppressWarnings("unchecked")
            final java.util.function.BiConsumer<TileKey, List<TileKey>> toNotice =
                (java.util.function.BiConsumer<TileKey, List<TileKey>>) field.get(from.getAutonomyPanel());

            opening = () -> toNotice.accept(tile, new ArrayList<TileKey>());
        }
        else
        {
            assertEquals(door, "link", "precondition: no such door");

            opening = () -> from.jumpToSquare(tile);
        }

        LayoutEditor opened = openedBy(opening, from);

        assertFalse(from.isDisplayable(), "precondition: the " + door + "'s door did not close the window it left");

        return opened;
    }

    /**
     * The autonomy editor the main window shows after an opening - waited for until it is a new window, built and in
     * autonomy mode.
     *
     * @param opening what opens it, run on the event thread
     * @param not the editor it must not be, or null
     * @return the editor
     */
    private static LayoutEditor openedBy(Runnable opening, LayoutEditor not) throws Exception
    {
        // NOT WITH EDIT GREY: the main window answers that with a dialog that waits on the screen
        java.lang.reflect.Field button = TrainControlUI.class.getDeclaredField("editLayoutButton");

        button.setAccessible(true);

        final boolean[] live = new boolean[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                live[0] = ((javax.swing.JButton) button.get(ui)).isEnabled();
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        if (not == null || !not.isDisplayable())
        {
            assertTrue(live[0], "precondition: the main window's Edit is grey, so an editor would be refused with a dialog");
        }

        SwingUtilities.invokeAndWait(opening);

        java.lang.reflect.Field shown = TrainControlUI.class.getDeclaredField("openEditor");

        shown.setAccessible(true);

        long until = System.currentTimeMillis() + 20000;

        while (System.currentTimeMillis() < until)
        {
            settleTheEditor();

            Object open = shown.get(ui);

            if (open instanceof LayoutEditor && open != not && ((LayoutEditor) open).isDisplayable()
                && ((LayoutEditor) open).isAutonomyMode() && gridOf((LayoutEditor) open) != null)
            {
                return (LayoutEditor) open;
            }
        }

        Object open = shown.get(ui);

        java.lang.reflect.Field onItsWay = TrainControlUI.class.getDeclaredField("editorOnItsWay");

        onItsWay.setAccessible(true);

        StringBuilder windows = new StringBuilder();

        for (java.awt.Window w : java.awt.Window.getWindows())
        {
            if (w.isDisplayable()) windows.append(w.getClass().getSimpleName()).append(w.isShowing() ? "(showing) " : " ");
        }

        throw new AssertionError("precondition: no autonomy editor was opened - the window's editor " + open
            + (open instanceof LayoutEditor ? " displayable " + ((LayoutEditor) open).isDisplayable() + " autonomy "
                + ((LayoutEditor) open).isAutonomyMode() : "")
            + "; on its way " + onItsWay.get(ui) + "; windows " + windows + "; the log: " + logTop());
    }

    /** Whether an editor holds autonomy work its Save has not written. */
    private static boolean unsaved(LayoutEditor on) throws Exception
    {
        java.lang.reflect.Method asked = LayoutEditor.class.getDeclaredMethod("hasUnsavedAutonomyWork");

        asked.setAccessible(true);

        final boolean[] out = new boolean[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                out[0] = (Boolean) asked.invoke(on);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        return out[0];
    }

    /**
     * Closes an autonomy editor as its X does, after making sure it has nothing unsaved - so the close asks nothing on the
     * screen, and the main window is told, as a bare dispose does not tell it.
     */
    private static void closeAsTheUserDoes(LayoutEditor on) throws Exception
    {
        if (on == null || !on.isDisplayable()) return;

        assertFalse(unsaved(on), "precondition: the editor has unsaved work, so its X would ask on the screen");

        SwingUtilities.invokeAndWait(() -> on.dispatchEvent(
            new java.awt.event.WindowEvent(on, java.awt.event.WindowEvent.WINDOW_CLOSING)));

        settleTheEditor();
    }

    /** The newest lines of the main window's log, for a failure to say what went wrong. */
    private static String logTop() throws Exception
    {
        java.lang.reflect.Field area = TrainControlUI.class.getDeclaredField("debugArea");

        area.setAccessible(true);

        String text = ((javax.swing.JTextArea) area.get(ui)).getText();

        return text.substring(0, Math.min(1500, text.length()));
    }

    /** Whether the track square at these coordinates says what a click does, the pointer moved over it. */
    private static boolean says(LayoutEditor on, int[] at, String line) throws Exception
    {
        final String[] tip = new String[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                org.traincontrol.gui.LayoutLabel square = gridOf(on).getValueAt(at[0], at[1]);

                on.receiveMoveEvent(new java.awt.event.MouseEvent(square, java.awt.event.MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(), 0, 1, 1, 0, false), square);

                tip[0] = square.getToolTipText();
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        return String.valueOf(tip[0]).contains(line);
    }

    /** A blank square of an editor's grid. */
    private static int[] blankSquare(LayoutEditor on) throws ReflectiveOperationException
    {
        org.traincontrol.gui.LayoutGrid grid = gridOf(on);

        for (int y = 0; y < 20; y++)
        {
            for (int x = 0; x < 20; x++)
            {
                org.traincontrol.gui.LayoutLabel here = grid.getValueAt(x, y);

                if (here != null && !here.isSpacer() && here.getComponent() == null) return new int[] {x, y};
            }
        }

        throw new AssertionError("precondition: no blank square");
    }

    /**
     * An undo's redraw keeps the selection's grip and the pointer's blue outline (RSA43-C4).
     *
     * The redraw a Control+Z asks for ended with a pass that took every outline down and put back only the picked squares'
     * yellow: the grip that moves the group and the square under the pointer went grey until the pointer next moved,
     * though both still acted - after five rounds (52 to 56) of making exactly these outlines right through every gesture,
     * and Control+Z is pressed with the mouse still.
     *
     * A tile turned so there is something to undo, two squares picked, the pointer over a third, Control+Z: the grip and
     * the blue are still drawn on the squares the grid now holds.
     *
     * MUTATION: end the redraw with the border pass alone again, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testAnUndoKeepsTheGripAndThePointersOutline() throws Exception
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

            final int[] at = new int[2];

            final org.traincontrol.gui.LayoutLabel[] squares = aTrackSquareWithRoom(track[0], at);

            assertNotNull(squares, "precondition: no track square on " + PAGE);

            java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

            gridField.setAccessible(true);

            // SOMETHING TO UNDO
            SwingUtilities.invokeAndWait(() -> track[0].rotate(squares[0]));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            // TWO SQUARES PICKED, AND THE POINTER OVER A THIRD - on the grid as it now stands
            final int x = at[0];
            final int y = at[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) gridField.get(track[0]);

                    for (int dx = 0; dx < 2; dx++)
                    {
                        org.traincontrol.gui.LayoutLabel square = grid.getValueAt(x + dx, y);

                        track[0].receiveClickEvent(new java.awt.event.MouseEvent(square,
                            java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                            java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1,
                            false, java.awt.event.MouseEvent.BUTTON1), square);
                    }

                    org.traincontrol.gui.LayoutLabel over = grid.getValueAt(x, y + 1);

                    track[0].receiveMoveEvent(new java.awt.event.MouseEvent(over, java.awt.event.MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(), 0, 1, 1, 0, false), over);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(gripIsDrawn(gridField, track[0]) && wearsTheHover(track[0], squareAt(gridField, track[0], x, y + 1)),
                "precondition: before the undo the selection's grip or the pointer's outline is not drawn");

            // CONTROL+Z, with the pointer where it was
            SwingUtilities.invokeAndWait(() -> track[0].undo());

            for (int i = 0; i < 20; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(gripIsDrawn(gridField, track[0]), "after an undo the picked squares lost the grip that moves them,"
                + " though it still moves them (RSA43-C4)");

            assertTrue(wearsTheHover(track[0], squareAt(gridField, track[0], x, y + 1)), "after an undo the square the pointer"
                + " is still over lost its blue outline (RSA43-C4)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /** The label the editor's grid holds now at a square. */
    private static javax.swing.JLabel squareAt(java.lang.reflect.Field gridField, LayoutEditor editor, int x, int y)
        throws Exception
    {
        final javax.swing.JLabel[] out = new javax.swing.JLabel[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                out[0] = ((org.traincontrol.gui.LayoutGrid) gridField.get(editor)).getValueAt(x, y);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        return out[0];
    }

    /** Whether any square of the editor's grid wears the selection's grip. */
    private static boolean gripIsDrawn(java.lang.reflect.Field gridField, LayoutEditor editor) throws Exception
    {
        final boolean[] out = new boolean[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                for (java.awt.Component c : ((org.traincontrol.gui.LayoutGrid) gridField.get(editor)).getContainer().getComponents())
                {
                    if (c instanceof javax.swing.JLabel && ((javax.swing.JLabel) c).getBorder() != null
                        && ((javax.swing.JLabel) c).getBorder().getClass().getName().endsWith("SelectionGrip")) out[0] = true;
                }
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        return out[0];
    }

    /**
     * A box Escape dropped and released on its own square after the pointer moved with the button down leaves that square's
     * next click alone (RSA36-C1).
     *
     * AWT makes no click of a press and release with any movement between - out and back, or a pixel within the square -
     * so the click set aside to ignore waited for that square's next click, of any button: a right-click's menu, a middle
     * click's turn, a Shift click's pick.
     *
     * Control+M, a press on a track square, the pointer moved within it - and, the second time, out to a square two along
     * and back - Escape, the release there, then a left click: the tile is picked up.  A LEFT click, because only the
     * very next left click is ever set aside: a kept record would swallow it, as it swallowed a Shift click's pick.  Each
     * in one event, so a real pointer resting over the shown editor cannot move the hover between the steps.
     *
     * MUTATION: keep the click to ignore past a release after the pointer moved, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testAPressThatMovedLeavesTheNextClickAlone() throws Exception
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

            final int[] at = new int[2];

            final org.traincontrol.gui.LayoutLabel[] squares = aTrackSquareWithRoom(track[0], at);

            assertNotNull(squares, "precondition: no track square with a square two to its right on " + PAGE);

            // ESCAPE'S OWN ACTION: the key handler posts it, and this is all one event
            final java.lang.reflect.Method key = LayoutEditor.class.getDeclaredMethod("escapePressed");

            key.setAccessible(true);

            final java.lang.reflect.Field ignoring = LayoutEditor.class.getDeclaredField("clickToIgnore");

            ignoring.setAccessible(true);

            for (final boolean outAndBack : new boolean[] {false, true})
            {
                final org.traincontrol.gui.LayoutLabel from = gridOf(track[0]).getValueAt(at[0], at[1]);

                final org.traincontrol.gui.LayoutLabel to = gridOf(track[0]).getValueAt(at[0] + 2, at[1]);

                final Object[] escaped = new Object[2];

                SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        int held = java.awt.event.InputEvent.BUTTON1_DOWN_MASK;

                        track[0].setSelectMode(true);
                        track[0].receiveMoveEvent(new java.awt.event.MouseEvent(from, java.awt.event.MouseEvent.MOUSE_MOVED,
                            System.currentTimeMillis(), 0, 1, 1, 0, false), from);
                        track[0].beginDrag(new java.awt.event.MouseEvent(from, java.awt.event.MouseEvent.MOUSE_PRESSED,
                            System.currentTimeMillis(), held, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1), from);

                        if (outAndBack)
                        {
                            track[0].receiveMoveEvent(new java.awt.event.MouseEvent(to,
                                java.awt.event.MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 1, 1, 0, false), to);
                            track[0].updateDrag(new java.awt.event.MouseEvent(from,
                                java.awt.event.MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), held, 65, 1, 0, false),
                                from);
                            track[0].receiveMoveEvent(new java.awt.event.MouseEvent(from,
                                java.awt.event.MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 1, 1, 0, false),
                                from);
                        }

                        // THE POINTER MOVED: a drag event within the square
                        track[0].updateDrag(new java.awt.event.MouseEvent(from, java.awt.event.MouseEvent.MOUSE_DRAGGED,
                            System.currentTimeMillis(), held, 2, 1, 0, false), from);

                        key.invoke(track[0]);

                        escaped[0] = ignoring.get(track[0]);

                        // THE RELEASE, on the square pressed - AWT makes no click of it
                        track[0].endDrag(new java.awt.event.MouseEvent(from, java.awt.event.MouseEvent.MOUSE_RELEASED,
                            System.currentTimeMillis(), 0, 2, 1, 1, false, java.awt.event.MouseEvent.BUTTON1), from);

                        // A LEFT CLICK there, which picks the tile up
                        track[0].receiveClickEvent(new java.awt.event.MouseEvent(from,
                            java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 2, 1, 1, false,
                            java.awt.event.MouseEvent.BUTTON1), from);

                        escaped[1] = track[0].hasToolFlag();
                    }
                    catch (ReflectiveOperationException e)
                    {
                        throw new IllegalStateException(e);
                    }
                });

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

                // THE TILE LET GO OF
                if (Boolean.TRUE.equals(escaped[1])) escape(track[0]);

                assertTrue(escaped[0] == from, "precondition: Escape did not set the box's click aside" + (outAndBack
                    ? " after the drag out and back" : ""));

                assertEquals(escaped[1], Boolean.TRUE, "a left click on the square of a dropped box, released there after the"
                    + " pointer moved" + (outAndBack ? " out and back" : " within it") + ", was swallowed - the tile was not"
                    + " picked up (RSA36-C1)");
            }
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /**
     * Only the release's own left click is ignored: any other click that comes first acts, and takes the record with it
     * (RSA36-C1).
     *
     * A press and release on one square with the pointer held still make a left click, and that is the click set aside.
     * A click that is not it - another button - is not that click, so it acts, and the record goes: the left click after
     * it acts too.
     *
     * Control+M, a press on a track square held still, Escape, the release there, then a middle click and a left click on
     * that square in the same event: the tile turns, and is picked up.
     *
     * MUTATION: ignore a click of any button again, or keep the record past a click that was not the one set aside, and
     * this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testOnlyTheReleasesOwnClickIsIgnored() throws Exception
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

            final int[] at = new int[2];

            final org.traincontrol.gui.LayoutLabel[] squares = aTrackSquareWithRoom(track[0], at);

            assertNotNull(squares, "precondition: no track square with a square two to its right on " + PAGE);

            final org.traincontrol.gui.LayoutLabel from = squares[0];

            final int turnedWas = from.getComponent().getOrientation();

            // ESCAPE'S OWN ACTION: the key handler posts it, and this is all one event
            final java.lang.reflect.Method key = LayoutEditor.class.getDeclaredMethod("escapePressed");

            key.setAccessible(true);

            final java.lang.reflect.Field ignoring = LayoutEditor.class.getDeclaredField("clickToIgnore");

            ignoring.setAccessible(true);

            final Object[] read = new Object[3];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    int held = java.awt.event.InputEvent.BUTTON1_DOWN_MASK;

                    track[0].setSelectMode(true);
                    track[0].receiveMoveEvent(new java.awt.event.MouseEvent(from, java.awt.event.MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(), 0, 1, 1, 0, false), from);
                    track[0].beginDrag(new java.awt.event.MouseEvent(from, java.awt.event.MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(), held, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1), from);

                    key.invoke(track[0]);

                    // THE RELEASE, on the square pressed, the pointer held still: its click is the one set aside
                    track[0].endDrag(new java.awt.event.MouseEvent(from, java.awt.event.MouseEvent.MOUSE_RELEASED,
                        System.currentTimeMillis(), 0, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1), from);

                    read[0] = ignoring.get(track[0]);

                    // A MIDDLE CLICK first - not that click
                    track[0].receiveClickEvent(new java.awt.event.MouseEvent(from,
                        java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false,
                        java.awt.event.MouseEvent.BUTTON2), from);

                    // AND A LEFT CLICK, which picks the tile up
                    track[0].receiveClickEvent(new java.awt.event.MouseEvent(from,
                        java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false,
                        java.awt.event.MouseEvent.BUTTON1), from);

                    read[2] = track[0].hasToolFlag();
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            // THE TILE LET GO OF, then read, and turned back
            if (Boolean.TRUE.equals(read[2])) escape(track[0]);

            org.traincontrol.base.LayoutDiagramComponent now = gridOf(track[0]).getValueAt(at[0], at[1]).getComponent();

            boolean turned = now != null && now.getOrientation() != turnedWas;

            turnBack(track[0], at, turnedWas);

            assertTrue(read[0] == from, "precondition: a release held still did not keep the box's click aside");

            assertTrue(turned, "a middle click after a dropped box's release was"
                + " swallowed - only the release's own left click is set aside (RSA36-C1)");

            assertEquals(read[2], Boolean.TRUE, "the left click after a middle click on a dropped box's square was swallowed"
                + " - the record outlived the click that was not it (RSA36-C1)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /**
     * A palette piece held across a page switch leaves the palette's red outline behind with it (RSA35-C3).
     *
     * The switch drops the tool, and the palette went on showing the piece armed - the editor's sign for what the next
     * click does - so the next click on track picked that track up instead of placing anything.
     *
     * MUTATION: let a switch drop the tool without the palette's outline again, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testAPageSwitchTakesTheArmedPiecesOutline() throws Exception
    {
        String other = null;

        for (String name : model.getLayoutList()) if (other == null && !PAGE.equals(name)) other = name;

        if (other == null) throw new SkipException("the snapshot has one page only");

        final String to = other;

        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        try
        {
            settleTheEditor();

            final String red = "hl:" + java.awt.Color.RED.getRGB() + ":";

            final org.traincontrol.gui.LayoutLabel piece = aPalettePiece(track[0]);

            SwingUtilities.invokeAndWait(() -> track[0].receiveClickEvent(new java.awt.event.MouseEvent(piece,
                java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false,
                java.awt.event.MouseEvent.BUTTON1), piece));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(track[0].hasToolFlag() && keyOf(track[0], piece).startsWith(red), "precondition: the palette piece"
                + " was not picked up and outlined: " + keyOf(track[0], piece));

            final java.lang.reflect.Method arrive = LayoutEditor.class.getDeclaredMethod("arriveAt", String.class,
                boolean.class);

            arrive.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    arrive.invoke(track[0], to, false);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            settleTheEditor();

            assertFalse(track[0].hasToolFlag(), "precondition: the switch did not drop the tool");

            java.lang.reflect.Field paletteField = LayoutEditor.class.getDeclaredField("newComponents");

            paletteField.setAccessible(true);

            for (java.awt.Component c : ((java.awt.Container) paletteField.get(track[0])).getComponents())
            {
                assertFalse(c instanceof javax.swing.JLabel && keyOf(track[0], (javax.swing.JLabel) c).startsWith(red),
                    "after a switch dropped the tool, the palette still shows a piece armed (RSA35-C3)");
            }
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /** The first piece of the editor's palette. */
    private static org.traincontrol.gui.LayoutLabel aPalettePiece(LayoutEditor editor) throws ReflectiveOperationException
    {
        java.lang.reflect.Field paletteField = LayoutEditor.class.getDeclaredField("newComponents");

        paletteField.setAccessible(true);

        for (java.awt.Component c : ((java.awt.Container) paletteField.get(editor)).getComponents())
        {
            if (c instanceof org.traincontrol.gui.LayoutLabel) return (org.traincontrol.gui.LayoutLabel) c;
        }

        throw new IllegalStateException("the palette has no piece");
    }

    /** Lets an editor just rendered finish its posted build. */
    private static void settleTheEditor() throws Exception
    {
        for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

        // OUT FROM UNDER THE REAL POINTER (round 80).  The editor windows these claims open are shown on the screen, and a
        // pointer resting where one appears tells the window the pointer has arrived over it - over a part nothing listens
        // on, that is the pointer off the squares (RSA47-C1), and it took a simulated hover's outline off part way through
        // a claim.  Off the screen, no real pointer reaches them.
        SwingUtilities.invokeAndWait(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof LayoutEditor && window.isShowing() && window.getX() > -5000) window.setLocation(-8000, -8000);
            }
        });

        Thread.sleep(500);

        for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });
    }

    /** Closes a right-click menu as Swing tells the editor it has closed, and lets what that posts run. */
    private static void closeTheMenu(final javax.swing.JPopupMenu menu) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            for (javax.swing.event.PopupMenuListener heard : menu.getPopupMenuListeners())
            {
                if (heard.getClass().getName().startsWith(LayoutEditor.class.getName()))
                {
                    heard.popupMenuWillBecomeInvisible(new javax.swing.event.PopupMenuEvent(menu));
                }
            }
        });

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

    /**
     * The exit brings a minimised track editor back before it asks about its unsaved work (the popups sweep, on Adam's
     * MT-703 note): the question is owned by the editor, and a minimised window hides what it owns - so closing
     * TrainControl with the editor in the taskbar asked Save, Discard or Cancel where nobody could see it, and the exit
     * waited on it.  The editor has a turned square unsaved, so the exit does ask, and the editor is read while the
     * question shows (RSA53-C2: an editor with nothing unsaved asked nothing, and the claim saw only that the exit
     * restored it).
     *
     * MUTATION: ask without bringing the editor back, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheExitBringsAMinimisedEditorBackBeforeItAsks() throws Exception
    {
        final LayoutEditor[] track = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            track[0] = new LayoutEditor(page, 30, ui, 0);
            track[0].render();
        });

        final Thread[] answering = new Thread[1];

        final java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();

        try
        {
            settleTheEditor();

            // SOMETHING UNSAVED, so the exit asks
            final org.traincontrol.gui.LayoutLabel[] squares = aTrackSquareWithRoom(track[0], new int[2]);

            assertNotNull(squares, "precondition: the page has no track square to turn");

            SwingUtilities.invokeAndWait(() -> track[0].rotate(squares[0]));

            final boolean[] canUndo = new boolean[1];

            SwingUtilities.invokeAndWait(() -> canUndo[0] = track[0].canUndo());

            assertTrue(canUndo[0], "precondition: turning a square left the editor nothing unsaved to ask about");

            SwingUtilities.invokeAndWait(() -> track[0].setExtendedState(java.awt.Frame.ICONIFIED));

            long until = System.currentTimeMillis() + 5000;

            while ((track[0].getExtendedState() & java.awt.Frame.ICONIFIED) == 0 && System.currentTimeMillis() < until)
            {
                Thread.sleep(50);
            }

            assertTrue((track[0].getExtendedState() & java.awt.Frame.ICONIFIED) != 0, "precondition: the editor would"
                + " not minimise");

            // THE QUESTION, answered Discard - the editor read while it shows; and closed unanswered if it never shows
            // as a button can be pressed on, so a question nobody can see fails this rather than hanging it
            final String discard = I18n.t("layout.ui.switchDiscard");

            final java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();

            final java.util.concurrent.atomic.AtomicReference<java.awt.Window> owner =
                new java.util.concurrent.atomic.AtomicReference<>();

            answering[0] = new Thread(() ->
            {
                long giveUp = System.currentTimeMillis() + 10000;

                while (!stop.get())
                {
                    for (java.awt.Window w : java.awt.Window.getWindows())
                    {
                        // ANY OWNER: a question owned elsewhere is answered too, and its owner read, rather than left
                        // to hang the exit
                        if (!(w instanceof javax.swing.JDialog) || !w.isVisible()) continue;

                        if (System.currentTimeMillis() > giveUp)
                        {
                            SwingUtilities.invokeLater(w::dispose);

                            continue;
                        }

                        javax.swing.AbstractButton button = buttonIn(w, discard);

                        if (button == null) continue;

                        if (seen.compareAndSet(null, (track[0].getExtendedState() & java.awt.Frame.ICONIFIED) != 0
                            ? "minimised" : "shown"))
                        {
                            owner.set(w.getOwner());
                        }

                        SwingUtilities.invokeLater(button::doClick);
                    }

                    try
                    {
                        Thread.sleep(50);
                    }
                    catch (InterruptedException e)
                    {
                        return;
                    }
                }
            }, "the exit's question");

            answering[0].setDaemon(true);
            answering[0].start();

            final boolean[] may = new boolean[1];

            SwingUtilities.invokeAndWait(() -> may[0] = track[0].maySettleBeforeExit());

            assertNotNull(seen.get(), "precondition: the exit did not ask about the turned square");

            assertTrue(may[0], "precondition: Discard did not let the exit go on");

            assertTrue(owner.get() == track[0], "the exit's question about the editor's unsaved work is owned by "
                + owner.get() + ", not by the editor");

            assertEquals(seen.get(), "shown", "the exit asked about the editor's unsaved work while the editor was still"
                + " minimised - where the question it owns cannot be seen");
        }
        finally
        {
            stop.set(true);

            if (answering[0] != null) answering[0].join(2000);

            SwingUtilities.invokeAndWait(() -> track[0].dispose());
        }
    }

    /** The button of that text in a window, or null. */
    private static javax.swing.AbstractButton buttonIn(java.awt.Container in, String text)
    {
        for (java.awt.Component child : in.getComponents())
        {
            if (child instanceof javax.swing.AbstractButton && text.equals(((javax.swing.AbstractButton) child).getText()))
            {
                return (javax.swing.AbstractButton) child;
            }

            if (child instanceof java.awt.Container)
            {
                javax.swing.AbstractButton found = buttonIn((java.awt.Container) child, text);

                if (found != null) return found;
            }
        }

        return null;
    }
}
