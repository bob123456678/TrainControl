package ui;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import support.Components;
import support.FrozenEditor;
import support.Reflect;

/**
 * Opening an editor, or switching its page, builds the diagram once, so the window's first paint is its last: the
 * autonomy editor already in its mode and annotated, and a page grown to the editor's floor drawn by the call that grew
 * it.  On the frozen railway.
 */
public class testTheEditorOpensInOneDrawing
{
    private static FrozenEditor rig;

    @BeforeClass
    public static void open() throws Exception
    {
        rig = FrozenEditor.open();
    }

    @AfterClass(alwaysRun = true)
    public static void close() throws Exception
    {
        if (rig != null) rig.close();
    }

    /**
     * The autonomy editor's diagram is built once on opening, with the setup's marks on it: it was built a second time
     * one event after the window showed, a second full paint of the diagram.
     *
     * MUTATION: post `setAutonomyMode`'s redraw whether or not a grid was drawn before the mode, and this fails.
     */
    @Test(timeOut = 120000, priority = 1)
    public void testTheAutonomyEditorBuildsItsDiagramOnce() throws Exception
    {
        // A page at the floor already, so only the mode's own redraw is counted; after the claim below, which grows one
        String page = aPage(false);

        List<Component> built = new ArrayList<>();

        LayoutEditor editor = opened(page, true, built);

        try
        {
            assertEquals(built.size(), 1, "opening the autonomy editor on " + page + " built its diagram "
                + built.size() + " times, so the window paints it twice: once, then again a moment later");

            int marked = Components.onTheEventThread(() -> marked(editor));

            assertTrue(marked > 0, "the one diagram the autonomy editor built carries none of the setup's marks");
        }
        finally
        {
            FrozenEditor.dispose(editor);
        }
    }

    /**
     * A page smaller than the editor's floor is grown and drawn by the same call: growing asked for a redraw of its
     * own, a second build of the diagram straight after the first.
     *
     * MUTATION: drop the pause around `render()`'s `drawGrid`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAPageGrownToTheFloorIsBuiltOnce() throws Exception
    {
        String page = aPage(true);

        List<Component> built = new ArrayList<>();

        LayoutEditor editor = opened(page, false, built);

        try
        {
            LayoutDiagram diagram = rig.model.getLayout(page);

            assertTrue(diagram.getSx() >= LayoutEditor.DEFAULT_NEW_SIZE_COLS
                && diagram.getSy() >= LayoutEditor.DEFAULT_NEW_SIZE_ROWS,
                "precondition: opening did not grow " + page + " to the editor's floor");

            assertEquals(built.size(), 1, "opening the editor on " + page + ", which it grows to its floor, built"
                + " the diagram " + built.size() + " times");
        }
        finally
        {
            FrozenEditor.dispose(editor);
        }
    }

    /**
     * A page switch in the autonomy editor builds the arriving page once, in the mode: it was built before the mode was
     * entered, painted bare, then built and painted again with the setup's marks a moment later.
     *
     * MUTATION: move `arriveAt`'s `setAutonomyMode(session)` back after `drawGridOnce()`, and this fails.
     */
    @Test(timeOut = 120000, priority = 2)
    public void testAPageSwitchInTheAutonomyEditorBuildsOnce() throws Exception
    {
        Switch seen = switched(true);

        assertEquals(seen.built.size(), 1, "switching the autonomy editor to " + seen.to + " built its diagram "
            + seen.built.size() + " times, so the window paints it twice: bare, then again with the setup's marks");

        assertEquals(seen.painted.size(), 1, "switching the autonomy editor to " + seen.to + " painted "
            + seen.painted.size() + " different diagrams, the first of them thrown away a moment later");

        assertTrue(seen.firstPaintMarked > 0, "the first paint after switching the autonomy editor to " + seen.to
            + " showed the diagram without the setup's marks");
    }

    /**
     * A page switch in the track editor builds and paints the arriving page once, as before.
     *
     * MUTATION: call `refreshGrid()` after `arriveAt`'s `drawGridOnce()`, and this fails.
     */
    @Test(timeOut = 120000, priority = 2)
    public void testAPageSwitchInTheTrackEditorBuildsOnce() throws Exception
    {
        Switch seen = switched(false);

        assertEquals(seen.built.size(), 1, "switching the track editor to " + seen.to + " built its diagram "
            + seen.built.size() + " times");

        assertEquals(seen.painted.size(), 1, "switching the track editor to " + seen.to + " painted "
            + seen.painted.size() + " different diagrams");
    }

    /** What one page switch built and painted. */
    private static final class Switch
    {
        String to;
        final List<Component> built = new ArrayList<>();

        /** Each diagram a paint of the window drew, by identity, in order. */
        final List<Component> painted = new ArrayList<>();

        int firstPaintMarked = -1;
    }

    /**
     * Opens the editor on one page at the floor and switches it to another through the page list, as a click does;
     * counts the diagrams the switch mounted and the ones the window's paints drew.
     */
    private static Switch switched(boolean autonomy) throws Exception
    {
        List<String> pages = pagesAtTheFloor();

        if (pages.size() < 2) throw new org.testng.SkipException("the frozen railway has no two pages at the floor");

        Switch seen = new Switch();

        seen.to = pages.get(1);

        LayoutEditor editor = opened(pages.get(0), autonomy, seen.built);

        RepaintManager was = RepaintManager.currentManager((JComponent) null);

        try
        {
            seen.built.clear();

            SwingUtilities.invokeAndWait(() ->
            {
                RepaintManager.setCurrentManager(new PaintWatch(editor, seen));

                JList<?> list = (JList<?>) get(editor, "pageList");

                int row = -1;

                for (int i = 0; i < list.getModel().getSize(); i++)
                {
                    if (seen.to.equals(list.getModel().getElementAt(i))) row = i;
                }

                Rectangle cell = list.getCellBounds(row, row);

                list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                    cell.x + 5, cell.y + 5, 1, false, MouseEvent.BUTTON1));
            });

            assertTrue(Components.waitFor(() -> Components.onTheEventThread(() ->
                seen.to.equals(((LayoutDiagram) get(editor, "layout")).getName())
                    && !(Boolean) get(editor, "changingPage")), 30000),
                "precondition: the editor did not arrive on " + seen.to);

            // Long enough for every posted redraw, and the paints after it, to have run
            Components.settle();
            Components.drain(8);
            Components.settle();

            assertEquals((boolean) Components.onTheEventThread(() -> editor.isAutonomyMode()), autonomy,
                "precondition: the switch changed the editor's mode");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(was));

            FrozenEditor.dispose(editor);
        }

        return seen;
    }

    /** Records, after each paint that reached the editor's diagram, which diagram it drew and how many marks it bore. */
    private static final class PaintWatch extends RepaintManager
    {
        private final LayoutEditor editor;
        private final Switch seen;
        private boolean diagramDirty;

        PaintWatch(LayoutEditor editor, Switch seen)
        {
            this.editor = editor;
            this.seen = seen;
        }

        @Override
        public void addDirtyRegion(java.awt.Window window, int x, int y, int w, int h)
        {
            if (window == editor) diagramDirty = true;

            super.addDirtyRegion(window, x, y, w, h);
        }

        @Override
        public void addDirtyRegion(JComponent c, int x, int y, int w, int h)
        {
            JComponent diagram = (JComponent) get(editor, "ExtLayoutPanel");

            if (w > 0 && h > 0 && SwingUtilities.getWindowAncestor(c) == editor && diagram.isShowing() && c.isShowing()
                && SwingUtilities.convertRectangle(c, new Rectangle(x, y, w, h), editor.getRootPane())
                    .intersects(SwingUtilities.convertRectangle(diagram.getParent(), diagram.getBounds(),
                        editor.getRootPane())))
            {
                diagramDirty = true;
            }

            super.addDirtyRegion(c, x, y, w, h);
        }

        @Override
        public void paintDirtyRegions()
        {
            boolean reached = diagramDirty;

            diagramDirty = false;

            super.paintDirtyRegions();

            LayoutGrid grid = (LayoutGrid) get(editor, "grid");

            if (!reached || grid == null) return;

            if (!seen.painted.contains(grid.getContainer())) seen.painted.add(grid.getContainer());

            if (seen.firstPaintMarked < 0) seen.firstPaintMarked = marked(editor);
        }
    }

    /** The pages of the frozen railway at the editor's floor, which opening does not grow. */
    private static List<String> pagesAtTheFloor()
    {
        List<String> out = new ArrayList<>();

        for (String name : rig.model.getLayoutList())
        {
            LayoutDiagram diagram = rig.model.getLayout(name);

            if (diagram.getSx() >= LayoutEditor.DEFAULT_NEW_SIZE_COLS
                && diagram.getSy() >= LayoutEditor.DEFAULT_NEW_SIZE_ROWS) out.add(name);
        }

        return out;
    }

    /** An editor field, from the event thread. */
    private static Object get(LayoutEditor editor, String field)
    {
        try
        {
            return Reflect.get(LayoutEditor.class, editor, field);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** A page of the frozen railway below the editor's floor, or at it; skipped where there is none. */
    private static String aPage(boolean belowTheFloor)
    {
        for (String name : rig.model.getLayoutList())
        {
            LayoutDiagram diagram = rig.model.getLayout(name);

            boolean below = diagram.getSx() < LayoutEditor.DEFAULT_NEW_SIZE_COLS
                || diagram.getSy() < LayoutEditor.DEFAULT_NEW_SIZE_ROWS;

            if (below == belowTheFloor) return name;
        }

        throw new org.testng.SkipException("the frozen railway has no page " + (belowTheFloor ? "below" : "at")
            + " the editor's floor");
    }

    /** The editor opened as the main window opens it, each diagram mounted in it collected. */
    private static LayoutEditor opened(String page, boolean autonomy, List<Component> built) throws Exception
    {
        LayoutDiagram diagram = rig.model.getLayout(page);

        LayoutEditor editor = Components.onTheEventThread(() ->
        {
            LayoutEditor window = new LayoutEditor(diagram, 30, rig.ui, 0);

            JPanel holder = (JPanel) Reflect.get(LayoutEditor.class, window, "ExtLayoutPanel");

            holder.addContainerListener(new ContainerAdapter()
            {
                @Override
                public void componentAdded(ContainerEvent e)
                {
                    if (!(e.getChild() instanceof org.traincontrol.gui.widgets.LoadingSpinner)) built.add(e.getChild());
                }
            });

            window.render();

            if (autonomy) window.setAutonomyMode(rig.session);

            return window;
        });

        // Long enough for every posted redraw to have run
        Components.settle();
        Components.drain(8);
        Components.settle();

        return editor;
    }

    /** How many squares of the editor's diagram carry an autonomy mark. */
    private static int marked(LayoutEditor editor)
    {
        LayoutGrid grid = (LayoutGrid) get(editor, "grid");

        int marked = 0;

        for (int x = 0; grid.getValueAt(x, 0) != null; x++)
        {
            for (int y = 0; grid.getValueAt(x, y) != null; y++)
            {
                if (annotation(grid.getValueAt(x, y)) != null) marked++;
            }
        }

        return marked;
    }

    private static Object annotation(LayoutLabel label)
    {
        try
        {
            return Reflect.get(LayoutLabel.class, label, "autonomyAnnotation");
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }
}
