package ui;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.DiagramExport;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Saving a track diagram to a picture.
 *
 * The failure this is really about is a BLANK image.  Painting a Swing component that has never been
 * shown paints nothing at all - it has no size, its children have no size, and printAll happily writes
 * a rectangle of white.  It does not throw, no error appears, and the user gets a file that opens
 * perfectly well and contains their layout drawn in white on white.
 *
 * So the assertion that matters is not "a file was produced" but "the file is not all one colour".
 *
 * Needs the real application, because a tile draws itself through it - the image cache and the decode
 * pool both live on TrainControlUI.  Disposed rather than closed at the end: the window-closing
 * handler saves state, which would write the operator's own locomotive database.
 */
public class testDiagramExport
{
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    /**
     * The operator’s railway is not this test’s to open (OB-111).
     *
     * Constructing the window opens whatever the saved layout preference names, which on his machine is
     * his live layout - so this class rewrote his configuration on every battery, identical but for
     * line endings, and left it showing as modified in git status. The sandbox points the preference
     * at a copy of the fixture and puts it back afterwards.
     */
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("drawing tiles needs a display");
        }

        // BEFORE THE MODEL, not just before the window (OB-111, corrected 2026-08-28).
        //
        // MarklinControlStation.init reads the layout preference too - it is what loads the pages -
        // so opening the sandbox after it left the model on the operator's real railway while the
        // window looked at the copy. The comment that used to stand here named only the window.
        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, true);

        model.stop();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        if (ui != null)
        {
            final TrainControlUI toClose = ui;

            SwingUtilities.invokeAndWait(() -> toClose.dispose());
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * A diagram with track on it comes out with something drawn on it.
     */
    @Test
    public void testAnExportedDiagramIsNotBlank() throws Exception
    {
        BufferedImage image = render(40);

        assertNotNull(image, "no picture was produced at all");

        assertTrue(image.getWidth() > 40 && image.getHeight() > 40,
            "the picture is smaller than a single tile, so the grid was never laid out: "
            + image.getWidth() + "x" + image.getHeight());

        assertTrue(colourCount(image) > 1,
            "every pixel of the exported diagram is the same colour.  Painting a component that has "
            + "never been shown paints nothing, and the result is a file that opens fine and holds an "
            + "empty white rectangle - which is the whole reason this test exists");
    }

    /**
     * A bigger tile size gives a bigger picture.
     *
     * The point of the export is a diagram larger than the screen could show, so the size has to
     * actually do something.
     */
    @Test
    public void testTheSizeAskedForIsTheSizeDrawn() throws Exception
    {
        BufferedImage small = render(20);
        BufferedImage large = render(60);

        assertTrue(large.getWidth() > small.getWidth(),
            "asking for three times the tile size gave a picture " + large.getWidth() + " wide "
            + "against " + small.getWidth() + " - so the size asked for is being ignored, and an "
            + "export can never be bigger than the screen already showed");
    }

    /**
     * A size beyond the maximum is brought back to the documented ceiling, not to some other size.
     *
     * TST-C9: comparing render(100000) against a second render(MAX_TILE_SIZE) call proves only that
     * the two inputs are clamped to the SAME size, not that the size is the ceiling the constant
     * names - a clamp mistakenly written as MAX_TILE_SIZE / 4 would move both calls together and this
     * test would still pass. So the expected width is derived independently instead: from a render at
     * a size well under the ceiling, scaled up by the ratio to MAX_TILE_SIZE by hand.
     */
    @Test
    public void testAnAbsurdSizeIsCapped() throws Exception
    {
        // THREE RENDERS, no arithmetic, no tolerance.
        //
        // Comparing an absurd size against MAX alone proves nothing - both go through the clamp, so a
        // clamp written as MAX / 4 moves them together and the test still passes. That is TST-C9.
        //
        // Deriving the expected width instead - render small, scale by the ratio - does not hold
        // either: a diagram is tiles PLUS what the drawing adds that does not scale with them, so
        // multiplying a small render by a large factor multiplies the fixed part too. It came out
        // seven pixels wide of the real answer, and a tolerance loose enough to swallow that is loose
        // enough to hide a genuine drift.
        //
        // What settles it is the SHAPE of the clamp rather than its arithmetic: an absurd size must
        // land on the same width as the ceiling, and the ceiling must be bigger than a quarter of it.
        // A clamp of min(size, MAX / 4) makes all three equal and fails the second comparison, which
        // is the mutation the finding named.
        int quarter = Math.max(1, DiagramExport.MAX_TILE_SIZE / 4);

        int absurd = render(100000).getWidth();
        int ceiling = render(DiagramExport.MAX_TILE_SIZE).getWidth();
        int smaller = render(quarter).getWidth();

        assertEquals(absurd, ceiling,
            "a size beyond the maximum did not come back the same width as the maximum itself, so the "
            + "clamp let it through - an image no program can open, and an OutOfMemoryError on the "
            + "way there");

        assertTrue(ceiling > smaller,
            "rendering at the ceiling and at a quarter of it produced the same width (" + ceiling
            + "), so the clamp is landing on something smaller than MAX_TILE_SIZE - every export is "
            + "quietly coarser than it should be");
    }

    /**
     * Renders OFF the event thread, which is where an export has to run.
     *
     * It waits for tile images to be decoded, and those are applied ON the event thread - so a render
     * that held the event thread would be waiting for work that cannot happen until it lets go.  That
     * is what produced a blank picture, and what this test caught.
     */
    private static BufferedImage render(final int tileSize) throws Exception
    {
        assertFalse(SwingUtilities.isEventDispatchThread(),
            "the test itself must not be on the event thread here");

        return DiagramExport.render(withSomeTrack(), tileSize, ui);
    }

    /**
     * A small diagram with a few tiles on it, laid out the way the parser leaves one.
     */
    private static LayoutDiagram withSomeTrack()
    {
        try
        {
            LayoutDiagram diagram = new LayoutDiagram("export test", 5, 4, null, null);

            for (int x = 0; x < 4; x++)
            {
                diagram.addComponent(new LayoutDiagramComponent(
                    LayoutDiagramComponent.componentType.STRAIGHT, x, 1, 0, 0, 0, 0,
                    Accessory.accessoryDecoderType.MM2), x, 1);
            }

            diagram.checkBounds();

            return diagram;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * How many distinct colours the picture holds, up to the point where the answer stops mattering.
     */
    private static int colourCount(BufferedImage image)
    {
        java.util.Set<Integer> seen = new java.util.HashSet<>();

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                seen.add(image.getRGB(x, y));

                if (seen.size() > 4) return seen.size();
            }
        }

        return seen.size();
    }

    /**
     * Exporting the page on screen produces the same picture as choosing it by name.
     *
     * The active-page item exists so that "a picture of what I am looking at" is one click rather than
     * a question with a visible answer.  What has to hold is that the shortcut and the long way round
     * draw the same thing - a shortcut that quietly exported a different page would be worse than the
     * question it removes.
     *
     * TST-B9: the original body rendered `page` against itself twice - byte-identical arguments, so
     * all it measured was that `DiagramExport.render` is deterministic. The active-page path
     * (`TrainControlUI.java:8044`, `active.addActionListener(event ->
     * exportDiagram(activeLayoutPage()))`) never ran.
     *
     * Rebuilt to go through `activeLayoutPage()` itself - by reflection, since it is private and the
     * rest of `exportDiagram` is a chain of modal dialogs and a file chooser no headless test can drive
     * - after pointing the real selector at a page the way choosing one from the toolbar does.
     *
     * Mutation this must fail: swap the endpoints so the shortcut asks for a DIFFERENT page than the
     * selector shows, or revert `activeLayoutPage()` to always answer the same page regardless of the
     * selector.
     */
    @Test
    public void testTheActivePageDrawsTheSamePictureAsChoosingIt() throws Exception
    {
        java.util.List<String> pages = model.getLayoutList();

        assertTrue(pages.size() >= 2, "the sample layout has fewer than two pages, so choosing the "
            + "wrong one as \"active\" would draw the same picture by accident and this test would "
            + "prove nothing");

        String onScreen = pages.get(pages.size() - 1);
        String somethingElse = pages.get(0);

        assertNotEquals(onScreen, somethingElse,
            "picked the same page twice by accident, so this test would prove nothing");

        java.lang.reflect.Field listField = TrainControlUI.class.getDeclaredField("LayoutList");
        listField.setAccessible(true);

        javax.swing.JComboBox<?> list = (javax.swing.JComboBox<?>) listField.get(ui);

        final Object[] wasSelected = new Object[1];

        SwingUtilities.invokeAndWait(() -> wasSelected[0] = list.getSelectedItem());

        try
        {
            // The real selector, pointed at the page the shortcut is supposed to notice - not the
            // page name handed straight to exportDiagram, which would leave activeLayoutPage()
            // itself unexercised.
            SwingUtilities.invokeAndWait(() -> list.setSelectedItem(onScreen));

            // The exact private call the active-page menu item makes.
            java.lang.reflect.Method activeLayoutPage =
                TrainControlUI.class.getDeclaredMethod("activeLayoutPage");
            activeLayoutPage.setAccessible(true);

            Object reportedActive = activeLayoutPage.invoke(ui);

            assertEquals(reportedActive, onScreen,
                "activeLayoutPage() reported " + reportedActive + " while the selector was showing "
                + onScreen + " - the shortcut would export the wrong page");

            LayoutDiagram chosenByName = model.getLayout(onScreen);
            LayoutDiagram viaShortcut = model.getLayout((String) reportedActive);
            LayoutDiagram different = model.getLayout(somethingElse);

            assertNotNull(chosenByName, "no page found for " + onScreen);
            assertNotNull(different, "no page found for " + somethingElse);

            BufferedImage byName = DiagramExport.render(chosenByName, 60, ui);
            BufferedImage asActive = DiagramExport.render(viaShortcut, 60, ui);
            BufferedImage otherPage = DiagramExport.render(different, 60, ui);

            // CONTROL: the two source pages really do draw differently, or the assertion below would
            // pass no matter which page the shortcut actually picked.
            assertTrue(imagesDiffer(byName, otherPage),
                "the page chosen by name and a different page on the same sample layout drew "
                + "identical pictures, so nothing below can tell a right page from a wrong one");

            assertFalse(imagesDiffer(byName, asActive),
                "the page chosen by name and the picture the active-page shortcut rendered are "
                + "different, so the shortcut is not exporting what is on screen");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> list.setSelectedItem(wasSelected[0]));
        }
    }

    /**
     * Whether two renders differ anywhere on a coarse grid - enough to tell two real pages apart
     * without demanding pixel-perfect determinism from the renderer.
     */
    private static boolean imagesDiffer(BufferedImage a, BufferedImage b)
    {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return true;

        for (int x = 0; x < a.getWidth(); x += 7)
        {
            for (int y = 0; y < a.getHeight(); y += 7)
            {
                if (a.getRGB(x, y) != b.getRGB(x, y)) return true;
            }
        }

        return false;
    }

    /**
     * The diagram carries the column and row numbers when the setting is on (FR-057).
     *
     * Adam, after the first build of this: **"I don't see the axis labels in the editor grid."**
     *
     * `testTheDiagramPrintsItsCoordinates` covers what `AxisRuler` DRAWS, and it drew correctly the
     * whole time - what nothing covered was whether anybody put one on the diagram, which is this
     * project's own "the extracted rule is tested and the call site is not".  Here rather than in that
     * class because building a grid needs the application: a tile draws itself through the window's
     * image cache, and that is what this class already has.
     *
     * Both directions, because a test of the ON case alone passes for a grid that always carries a
     * ruler, and the switch is the whole of what was asked for.
     *
     * MUTATION this catches: removing the `setBorder` from `LayoutGrid`, or reading a preference key
     * nothing writes.
     */
    @Test
    public void testTheGridCarriesTheCoordinateRulerWhenTheSettingIsOn() throws Exception
    {
        final LayoutDiagram page = model.getLayout(model.getLayoutList().get(0));

        assertNotNull(page, "no page to draw");

        boolean was = TrainControlUI.getPrefs().getBoolean(
            TrainControlUI.SHOW_COORDINATES_PREF, false);

        try
        {
            for (final boolean on : new boolean[] { true, false })
            {
                TrainControlUI.getPrefs().putBoolean(TrainControlUI.SHOW_COORDINATES_PREF, on);

                final javax.swing.JPanel panel = new javax.swing.JPanel();
                final org.traincontrol.gui.LayoutGrid[] built =
                    new org.traincontrol.gui.LayoutGrid[1];

                SwingUtilities.invokeAndWait(() ->
                    built[0] = new org.traincontrol.gui.LayoutGrid(page, 30, panel, null, true, ui));

                javax.swing.border.Border border = built[0].getContainer().getBorder();

                if (on)
                {
                    assertTrue(border instanceof org.traincontrol.gui.AxisRuler,
                        "the setting is on and the diagram carries " + border + ", so the numbers are "
                        + "drawn nowhere - which is what Adam reported: \"I don't see the axis labels "
                        + "in the editor grid\"");
                }
                else
                {
                    assertFalse(border instanceof org.traincontrol.gui.AxisRuler,
                        "the setting is off and the diagram still carries a ruler, so the toggle only "
                        + "goes one way");
                }
            }
        }
        finally
        {
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.SHOW_COORDINATES_PREF, was);
        }
    }

    /**
     * Building a grid over a panel retires the grid that was there.
     *
     * DD-B3. Four places in the application build a `LayoutGrid` over an existing panel, and three of
     * them called `discard()` on the outgoing one first. A grid that is not discarded keeps two timers
     * armed, and those timers go on firing into a panel that now belongs to somebody else - the grace
     * timer drops a spinner into the middle of the page the NEW grid has just drawn, and because the
     * panel is a FlowLayout the extra component pushes the tiles along and the last row comes out
     * short.
     *
     * `174178c5` had to add the third call and wrote the finding into its own comment: "both other
     * places that build a grid over an existing panel call this; this one did not." Three out of four
     * is what a rule looks like just before it is missed at the fourth, so the rule moved into the
     * constructor and this checks it is there.
     */
    @Test
    public void testANewGridRetiresTheOneItReplaces() throws Exception
    {
        final LayoutDiagram page = model.getLayout(model.getLayoutList().get(0));

        assertNotNull(page, "no page to draw");

        final javax.swing.JPanel panel = new javax.swing.JPanel();
        final org.traincontrol.gui.LayoutGrid[] built = new org.traincontrol.gui.LayoutGrid[2];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new org.traincontrol.gui.LayoutGrid(page, 30, panel, null, true, ui);
        });

        assertFalse(built[0].isDiscarded(), "a grid retired itself as it was built");

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            panel.removeAll();

            built[1] = new org.traincontrol.gui.LayoutGrid(page, 30, panel, null, true, ui);
        });

        assertTrue(built[0].isDiscarded(),
            "the outgoing grid was left armed when a new one was built over its panel. Its timers fire "
            + "into a panel that is no longer its own, which is a spinner dropped into the middle of "
            + "the page the new grid just drew (DD-B3)");

        assertFalse(built[1].isDiscarded(), "the incoming grid was retired instead of the outgoing one");
    }


    /**
     * A replaced grid is not kept alive by the table that retires it.
     *
     * MT-134, third item: "write a test case for this." The table that lets a new grid retire the old
     * one is a WeakHashMap keyed by the panel - and a WeakHashMap only collects an entry when nothing
     * else reaches the KEY. A LayoutGrid reaches its own panel: it holds `container` and adds it to the
     * parent. So while the value was the grid itself, every entry kept its own key alive and none was
     * ever collected: one page retained per editor, popup or export, for the life of the session.
     *
     * Found in review (NR-3) rather than by anybody noticing, which is how a leak of this size behaves -
     * it costs nothing anyone can see until the day it does.
     *
     * The collection itself is asked for rather than assumed: a bounded loop, because a garbage
     * collector is entitled to take its time and a test that demands the first `gc()` collect is a test
     * that fails on a fast machine for no reason.
     */
    @Test
    public void testAReplacedGridIsNotRetained() throws Exception
    {
        final LayoutDiagram page = model.getLayout(model.getLayoutList().get(0));

        assertNotNull(page, "no page to draw");

        final javax.swing.JPanel panel = new javax.swing.JPanel();
        final org.traincontrol.gui.LayoutGrid[] built = new org.traincontrol.gui.LayoutGrid[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new org.traincontrol.gui.LayoutGrid(page, 30, panel, null, true, ui);
        });

        java.lang.ref.WeakReference<org.traincontrol.gui.LayoutGrid> outgoing =
            new java.lang.ref.WeakReference<>(built[0]);

        built[0] = null;

        // A second grid over the same panel: the first is retired and should now be reachable from
        // nothing at all.
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            panel.removeAll();

            new org.traincontrol.gui.LayoutGrid(page, 30, panel, null, true, ui);
        });

        for (int attempt = 0; attempt < 20 && outgoing.get() != null; attempt++)
        {
            System.gc();

            Thread.sleep(50);
        }

        assertNull(outgoing.get(),
            "a grid that has been replaced is still reachable. The table that retires it is keyed by "
            + "the panel and a grid holds its own panel, so a strong value there keeps its own key "
            + "alive and nothing is ever collected - one page retained per editor, popup or export "
            + "(MT-134, NR-3)");
    }

    /**
     * A dozen editors opened and closed leave nothing behind.
     *
     * FR-012: "open and close the aditor a dozen times on a big layout and watch memory.  nothing to
     * see if this is right."
     *
     * Watching memory is what a person does; it is not what a test should assert, because a heap
     * measurement is noise - a garbage collector is under no obligation to have run, the JIT allocates
     * on its own account, and a threshold loose enough never to fail spuriously is loose enough to miss
     * a page or two of retained diagram. The property underneath is exact and can be asked for
     * directly: after a dozen cycles, ELEVEN of the twelve grids must be reachable from nothing.
     *
     * That is the same question `testAReplacedGridIsNotRetained` above asks once. Asking it twelve
     * times over is not redundant: the leak it found retained one page per cycle, so a single
     * replacement is the case most likely to be fixed by accident, and a dozen is what Adam actually
     * does with the editor.
     *
     * The collection is asked for in a bounded loop rather than demanded on the first `gc()`, for the
     * reason given above: a collector is entitled to take its time.
     */
    @Test
    public void testADozenEditorCyclesRetainNothing() throws Exception
    {
        final LayoutDiagram page = model.getLayout(model.getLayoutList().get(0));

        assertNotNull(page, "no page to draw");

        final javax.swing.JPanel panel = new javax.swing.JPanel();

        List<java.lang.ref.WeakReference<org.traincontrol.gui.LayoutGrid>> built = new ArrayList<>();

        final org.traincontrol.gui.LayoutGrid[] latest = new org.traincontrol.gui.LayoutGrid[1];

        for (int cycle = 0; cycle < 12; cycle++)
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                panel.removeAll();

                latest[0] = new org.traincontrol.gui.LayoutGrid(page, 30, panel, null, true, ui);
            });

            built.add(new java.lang.ref.WeakReference<>(latest[0]));
        }

        // Everything but the one currently on the panel, which is alive on purpose
        java.lang.ref.WeakReference<org.traincontrol.gui.LayoutGrid> current =
            built.get(built.size() - 1);

        latest[0] = null;

        int alive = built.size();

        for (int attempt = 0; attempt < 40 && alive > 1; attempt++)
        {
            System.gc();

            Thread.sleep(50);

            alive = 0;

            for (java.lang.ref.WeakReference<org.traincontrol.gui.LayoutGrid> was : built)
            {
                if (was.get() != null) alive++;
            }
        }

        assertNotNull(current.get(),
            "the grid still on the panel was collected, so this test is measuring the wrong thing");

        assertEquals(alive, 1,
            alive + " of twelve grids are still reachable after being replaced. Each one holds a page "
            + "of tiles, their icons and their listeners, so this is a page of diagram retained per "
            + "editor opened - the shape of leak nobody notices until the day they do (FR-012)");
    }
}
