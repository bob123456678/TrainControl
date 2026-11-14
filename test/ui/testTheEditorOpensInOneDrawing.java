package ui;

import java.awt.Component;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
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
 * Opening an editor builds its diagram once, so the window's first paint is its last: the autonomy editor already in
 * its mode and annotated, and a page grown to the editor's floor drawn by the call that grew it.  On the frozen
 * railway.
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
    @Test(timeOut = 120000)
    public void testTheAutonomyEditorBuildsItsDiagramOnce() throws Exception
    {
        // A page at the floor already, so only the mode's own redraw is counted
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
    private static int marked(LayoutEditor editor) throws Exception
    {
        LayoutGrid grid = (LayoutGrid) Reflect.get(LayoutEditor.class, editor, "grid");

        int marked = 0;

        for (int x = 0; grid.getValueAt(x, 0) != null; x++)
        {
            for (int y = 0; grid.getValueAt(x, y) != null; y++)
            {
                if (Reflect.get(LayoutLabel.class, grid.getValueAt(x, y), "autonomyAnnotation") != null) marked++;
            }
        }

        return marked;
    }
}
