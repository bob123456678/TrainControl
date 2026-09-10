package regression;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * OB-198: a square the pointer is no longer over is not a square a shortcut may act on.
 *
 * `LayoutEditor.autonomyHover` is set when the pointer enters a label and there was nothing that
 * cleared it - this window has no mouse-exit hook, and stepping to another page replaces the grid
 * without touching the field. Three shortcuts read it: **Control+H** (set home), **Control+S**
 * (rename) and, since FR-066, **Control+E** (set length).
 *
 * A label left behind by a page change is not in the grid it is asked about, so
 * `LayoutGrid.getCoordinates` answers `-1,-1` for it - and the key then acted on `(page, -1, -1)`: a
 * square nobody pointed at, answered with a sentence about a square nobody chose.
 *
 * **Asserted at the one question the three keys now ask.** `hoveredSquare()` is that question, and it
 * FORGETS a stale label rather than merely refusing it, so nothing can read it a second time. Testing
 * the three handlers separately would be testing three copies of a thing there is now one of - and
 * three copies is what the defect was.
 *
 * **The stale label is a real one, built the way the grid builds them and simply not in the grid** -
 * which is exactly the state a page change leaves the field in. The live one is taken off the rendered
 * editor by walking its component tree, because the grid has no accessor and a label the test made
 * itself would not be in it either.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).
 *
 * MUTATION: have `hoveredSquare` return the TileKey without asking the coordinates - which is what the
 * three handlers did - and `testAStaleLabelNamesNoSquare` fails.
 *
 * @author Adam
 */
public class testTheHoveredSquareIsForgotten
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutEditor editor;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the editor and its grid need a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration, so there is no autonomy mode to be in");
        }

        final LayoutDiagram page = model.getLayout(PAGE);

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
     * The editor answers with a square when the pointer really is over one.
     *
     * Asserted first and on its own: every claim below is that the answer is null, and a
     * `hoveredSquare` that answered null for everything would satisfy all of them without refusing
     * anything.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testALiveLabelNamesItsSquare() throws Exception
    {
        final TileKey[] over = new TileKey[1];

        SwingUtilities.invokeAndWait(() -> over[0] = hoverSomethingReal());

        assertNotNull(over[0],
            "no label on this rendered page answers with a square at all, so `hoveredSquare` is"
            + " refusing everything and the claims below say nothing");

        assertEquals(over[0].getPage(), PAGE,
            "the square named is on " + over[0].getPage() + " rather than the page this editor is"
            + " showing");

        assertTrue(over[0].getX() >= 0 && over[0].getY() >= 0,
            "the square named is " + over[0] + ", which is not a square on any grid - that is the"
            + " -1,-1 OB-198 is about, reached from a label that IS in the grid");
    }

    /**
     * A label that is not in this grid names no square - and is forgotten.
     *
     * The defect itself. A page change leaves the field holding a label from the page before, and this
     * is that label: real, built the way the grid builds them, and not in the grid.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAStaleLabelNamesNoSquare() throws Exception
    {
        final TileKey[] after = new TileKey[3];

        SwingUtilities.invokeAndWait(() ->
        {
            // First a square the pointer really is over, so the field is not merely empty.
            after[2] = hoverSomethingReal();

            LayoutLabel stranger = new LayoutLabel(null, editor.getContentPane(), 30, ui, false);

            editor.receiveMoveEvent(move(stranger), stranger);

            after[0] = editor.hoveredSquare();

            // AND ASKED AGAIN, because refusing a stale label once and keeping it is a field the next
            // reader has to remember to distrust - and there have been three readers of this one.
            after[1] = editor.hoveredSquare();
        });

        assertNotNull(after[2],
            "no label on this page answers with a square, so the field was empty before the stale one"
            + " arrived and the refusal below would happen anyway");

        assertNull(after[0],
            "the editor named " + after[0] + " while the pointer is over a label that is not on its"
            + " grid. That is the square OB-198 is about: a page change leaves the field holding a"
            + " label from the page before, `getCoordinates` answers -1,-1 for it, and Control+H,"
            + " Control+S and Control+E all acted on it");

        assertNull(after[1],
            "the editor still remembers the stale label after refusing it once, so the next reader"
            + " gets " + after[1] + " - the field is meant to be forgotten, not merely declined");
    }

    /**
     * And with nothing hovered at all, there is no square.
     *
     * The state the editor opens in, and the one every shortcut has to survive.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testNothingHoveredNamesNoSquare() throws Exception
    {
        final TileKey[] over = new TileKey[1];

        SwingUtilities.invokeAndWait(() ->
        {
            LayoutLabel stranger = new LayoutLabel(null, editor.getContentPane(), 30, ui, false);

            // Clears the field through the stale path, which is the only door a test has to it.
            editor.receiveMoveEvent(move(stranger), stranger);

            editor.hoveredSquare();

            over[0] = editor.hoveredSquare();
        });

        assertNull(over[0], "the editor named " + over[0] + " with nothing hovered");
    }

    /**
     * Hovers the first label on the rendered page that names a square, and returns it.
     *
     * Found on every call rather than cached: a label that is in the grid now is not necessarily in
     * the grid later, because the panel re-renders and a re-rendered grid holds new labels. That is
     * the very state this class is about, so a cached one is a fixture that goes stale for the reason
     * the subject exists.
     *
     * Must be called on the event thread.
     *
     * @return the square the editor now says the pointer is over, or null when no label answers
     */
    private static TileKey hoverSomethingReal()
    {
        for (LayoutLabel label : labelsIn(editor.getContentPane()))
        {
            editor.receiveMoveEvent(move(label), label);

            TileKey over = editor.hoveredSquare();

            if (over != null) return over;
        }

        return null;
    }

    /**
     * A mouse-moved event over a label, which is what the grid's own listener sends.
     *
     * @param over the label
     * @return the event
     */
    private static MouseEvent move(Component over)
    {
        return new MouseEvent(over, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 0, 0, 0,
            false);
    }

    /**
     * Every `LayoutLabel` under a container, depth first.
     *
     * The grid keeps no public accessor for its labels, and a label this class made itself would not be
     * in the grid - which is the very state the stale case is about. So the live one is taken off the
     * rendered window.
     *
     * @param root where to start
     * @return the labels, in the order the window holds them
     */
    private static List<LayoutLabel> labelsIn(Container root)
    {
        List<LayoutLabel> out = new ArrayList<>();

        if (root == null) return out;

        for (Component child : root.getComponents())
        {
            if (child instanceof LayoutLabel) out.add((LayoutLabel) child);

            if (child instanceof Container) out.addAll(labelsIn((Container) child));
        }

        return out;
    }
}
