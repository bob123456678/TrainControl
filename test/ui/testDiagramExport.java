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
import org.traincontrol.gui.autonomy.DiagramExport;
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
     * The operator's railway is not this test's to open (OB-111): constructing the window opens whatever the saved
     * layout preference names, so the sandbox points it at a copy of the fixture and puts it back afterwards.
     */
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("drawing tiles needs a display");
        }

        // BEFORE THE MODEL, not just before the window (OB-111): MarklinControlStation.init reads the layout preference
        // too, so opening the sandbox after it left the model on the operator's real railway.
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedInDebug();

        model.stop();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
    }

    @AfterClass(alwaysRun = true)
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
     * Comparing render(100000) against a second render(MAX_TILE_SIZE) call proves only that both are clamped to the
     * SAME size; a clamp mistakenly written as MAX_TILE_SIZE / 4 would move both calls together.
     */
    @Test
    public void testAnAbsurdSizeIsCapped() throws Exception
    {
        // THREE RENDERS, no arithmetic, no tolerance: scaling a small render fails because a diagram is tiles PLUS
        // drawing that does not scale with them.  What settles it is the SHAPE of the clamp: an absurd size must land
        // on the same width as the ceiling, and the ceiling must be bigger than a quarter of it (min(size, MAX / 4)
        // fails that).
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
     * How many distinct colors the picture holds, up to the point where the answer stops mattering.
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
     * Reaching the active-page path means going through `activeLayoutPage()` itself, by reflection since it is private
     * and the rest of `exportDiagram` is a chain of modal dialogs and a file chooser, after pointing the real selector
     * at a page the way choosing one from the toolbar does.
     *
     * Mutation this must fail: swap the endpoints so the shortcut asks for a DIFFERENT page than the selector shows,
     * or revert `activeLayoutPage()` to always answer the same page regardless of the selector.
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
            // The real selector, pointed at the page the shortcut is supposed to notice, so activeLayoutPage() itself
            // is exercised.
            SwingUtilities.invokeAndWait(() -> list.setSelectedItem(onScreen));

            // The exact private call the active-page menu item makes, on the window's Layouts menu (LayoutMenus).
            Object menus = layoutMenus();

            java.lang.reflect.Method activeLayoutPage = menus.getClass().getDeclaredMethod("activeLayoutPage");
            activeLayoutPage.setAccessible(true);

            Object reportedActive = activeLayoutPage.invoke(menus);

            assertEquals(reportedActive, onScreen,
                "activeLayoutPage() reported " + reportedActive + " while the selector was showing "
                + onScreen + " - the shortcut would export the wrong page");

            // ONE OBJECT, COMPARED WITH ITSELF FROM HERE ON: a determinism check on `render`, since a non-deterministic
            // rendering would make every picture comparison in this class meaningless.
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
     * The preference key the column and row numbers once had (OB-179).  A literal, because nothing in the application
     * names it any more; a stored `false` under it still sits in operators' preferences and must not hide the numbers,
     * so the test writes it.
     */
    private static final String RETIRED_COORDINATES_PREF = "ShowCoordinates";

    /**
     * The column and row numbers are part of the grid: on with it, off without (OB-179), in BOTH WINDOWS and both
     * directions of the switch (a test of the ON case alone passes for a grid that always carries a ruler).
     * `testTheDiagramPrintsItsCoordinates` covers what `AxisRuler` DRAWS; this covers whether anybody put one on the
     * diagram.
     *
     * THE RETIRED KEY IS SET TO FALSE THROUGHOUT.
     *
     * MUTATION this catches: removing the `setBorder` from `LayoutGrid`, restoring the retired
     * preference as a second term of `coordinatesVisible`, dropping the `LayoutEditor.showGrid()` term
     * so the numbers stop following the grid, or dropping the `master instanceof LayoutEditor` term -
     * which is the one that put a ruler on the running diagram.
     */
    @Test
    public void testTheCoordinateRulerIsPartOfTheGrid() throws Exception
    {
        final LayoutDiagram page = model.getLayout(model.getLayoutList().get(0));

        assertNotNull(page, "no page to draw");

        // WHETHER IT WAS STORED, not what the accessor answers: writing the accessor's default back materializes a
        // preference on a machine that never set one, which a later change of default could not reach.  Both keys,
        // because this test writes both: the grid's, which decides, and the retired one, which must not.
        boolean gridStored = TrainControlUI.getPrefs().get(TrainControlUI.EDITOR_GRID_PREF, null) != null;

        boolean gridWas = TrainControlUI.getPrefs().getBoolean(
            TrainControlUI.EDITOR_GRID_PREF, false);

        boolean retiredStored = TrainControlUI.getPrefs().get(RETIRED_COORDINATES_PREF, null) != null;

        boolean retiredWas = TrainControlUI.getPrefs().getBoolean(RETIRED_COORDINATES_PREF, false);

        final org.traincontrol.gui.layouteditor.LayoutEditor[] editor = new org.traincontrol.gui.layouteditor.LayoutEditor[1];

        try
        {
            SwingUtilities.invokeAndWait(() ->
                editor[0] = new org.traincontrol.gui.layouteditor.LayoutEditor(page, 30, ui, 0));

            TrainControlUI.getPrefs().putBoolean(RETIRED_COORDINATES_PREF, false);

            for (final boolean on : new boolean[] { true, false })
            {
                TrainControlUI.getPrefs().putBoolean(TrainControlUI.EDITOR_GRID_PREF, on);

                // THE EDITOR, which is one of the two windows that should carry it.
                final javax.swing.JPanel forEditor = new javax.swing.JPanel();
                final org.traincontrol.gui.LayoutGrid[] inEditor =
                    new org.traincontrol.gui.LayoutGrid[1];

                SwingUtilities.invokeAndWait(() -> inEditor[0] =
                    new org.traincontrol.gui.LayoutGrid(page, 30, forEditor, editor[0], true, ui));

                javax.swing.border.Border border = inEditor[0].getContainer().getBorder();

                if (on)
                {
                    assertTrue(border instanceof org.traincontrol.gui.widgets.AxisRuler,
                        "the grid is on and the editor's diagram carries " + border + ", so the "
                        + "numbers are drawn nowhere.  Adam: \"coordinates should always be on with "
                        + "the grid\" - and the only other setting in play here is the retired "
                        + "\"" + RETIRED_COORDINATES_PREF + "\", stored false, which nothing should "
                        + "still be reading");
                }
                else
                {
                    assertFalse(border instanceof org.traincontrol.gui.widgets.AxisRuler,
                        "the grid is off and the editor's diagram still carries a ruler, so the "
                        + "numbers outlived the thing they number");
                }

                // AND THE VIEWER, which should carry it in neither state.
                final javax.swing.JPanel forViewer = new javax.swing.JPanel();
                final org.traincontrol.gui.LayoutGrid[] inViewer =
                    new org.traincontrol.gui.LayoutGrid[1];

                SwingUtilities.invokeAndWait(() -> inViewer[0] =
                    new org.traincontrol.gui.LayoutGrid(page, 30, forViewer, null, true, ui));

                assertFalse(inViewer[0].getContainer().getBorder()
                        instanceof org.traincontrol.gui.widgets.AxisRuler,
                    "the running diagram carries the coordinate ruler with the grid " + on + " - "
                    + "Adam: \"they are visible in the track diagram viewer when they shouldn't be\"");
            }

            // AND ON A MACHINE THAT HAS NEVER SET EITHER (Adam: "make coordinates on be default"): both keys come out
            // and the defaults are asked, since the loop above proves only that the numbers follow a STORED grid
            // setting.
            TrainControlUI.getPrefs().remove(RETIRED_COORDINATES_PREF);
            TrainControlUI.getPrefs().remove(TrainControlUI.EDITOR_GRID_PREF);

            final javax.swing.JPanel fresh = new javax.swing.JPanel();
            final org.traincontrol.gui.LayoutGrid[] outOfTheBox = new org.traincontrol.gui.LayoutGrid[1];

            SwingUtilities.invokeAndWait(() -> outOfTheBox[0] =
                new org.traincontrol.gui.LayoutGrid(page, 30, fresh, editor[0], true, ui));

            assertTrue(outOfTheBox[0].getContainer().getBorder()
                    instanceof org.traincontrol.gui.widgets.AxisRuler,
                "with nothing stored for either setting the editor's diagram carries no ruler, so the "
                + "numbers are off out of the box - and the grid, whose default is on, is what they "
                + "are supposed to follow");
        }
        finally
        {
            if (gridStored) TrainControlUI.getPrefs().putBoolean(TrainControlUI.EDITOR_GRID_PREF, gridWas);
            else TrainControlUI.getPrefs().remove(TrainControlUI.EDITOR_GRID_PREF);

            if (retiredStored) TrainControlUI.getPrefs().putBoolean(RETIRED_COORDINATES_PREF, retiredWas);
            else TrainControlUI.getPrefs().remove(RETIRED_COORDINATES_PREF);

            if (editor[0] != null)
            {
                final org.traincontrol.gui.layouteditor.LayoutEditor open = editor[0];

                SwingUtilities.invokeAndWait(() -> open.dispose());
            }
        }
    }

    /**
     * Building a grid over a panel retires the grid that was there.  Four places in the application build a
     * `LayoutGrid` over an existing panel, and three called `discard()` on the outgoing one first.  A grid that is not
     * discarded keeps two timers armed, firing into a panel that now belongs to somebody else: the grace timer drops
     * a spinner into the middle of the page the NEW grid has just drawn, and the extra component pushes the tiles
     * along (FlowLayout), so the last row comes out short.  Three out of four is what a rule looks like just before it
     * is missed at the fourth, so the rule moved into the constructor and this checks it is there.
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
     * A replaced grid is not kept alive by the table that retires it (MT-134): a WeakHashMap keyed by the panel
     * collects an entry only when nothing else reaches the KEY, and a LayoutGrid reaches its own panel, so while the
     * value was the grid itself every entry kept its own key alive.
     *
     * The collection is asked for in a bounded loop, since a garbage collector may take its time and a test that
     * demands the first `gc()` collect fails on a fast machine for no reason.
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
     * A dozen editors opened and closed leave nothing behind (FR-012).  A heap measurement is noise, so the exact
     * property is asked for directly: after a dozen cycles, ELEVEN of the twelve grids must be reachable from nothing.
     * Asking `testAReplacedGridIsNotRetained`'s question twelve times is not redundant, since the leak retained one
     * page per cycle and a single replacement is the case most likely to be fixed by accident.  The collection is asked
     * for in a bounded loop, as there.
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

    /**
     * The Layout menu's picture export asks for Small, Medium or Large, in that order, with no pixel counts (OB-374),
     * opened through the menu item's own action and read off the screen as the user sees it.
     *
     * MUTATION: build the dialog's choices from the two sizes the export used to offer (with their pixel counts), or
     * leave Medium out of `layoutSizes`.
     */
    @Test
    public void testTheScreenshotMenuOffersSmallMediumAndLarge() throws Exception
    {
        java.lang.reflect.Field itemField = TrainControlUI.class.getDeclaredField("exportDiagramItem");
        itemField.setAccessible(true);

        final javax.swing.JMenuItem item = (javax.swing.JMenuItem) itemField.get(ui);

        assertNotNull(item, "the Layout menu has no picture export item");

        final List<String> offered = new java.util.concurrent.CopyOnWriteArrayList<>();

        String question = org.traincontrol.util.I18n.t("layout.ui.promptDiagramSizeChoice");

        try (support.Answering answering = support.Answering.start(message ->
            {
                if (!question.equals(message)) return null;

                for (java.awt.Window w : java.awt.Window.getWindows())
                {
                    if (!(w instanceof javax.swing.JDialog) || !w.isShowing()) continue;

                    for (java.awt.Component c : ((javax.swing.JDialog) w).getContentPane().getComponents())
                    {
                        if (!(c instanceof javax.swing.JOptionPane)) continue;

                        Object[] values = ((javax.swing.JOptionPane) c).getSelectionValues();

                        if (values != null) for (Object value : values) offered.add(String.valueOf(value));
                    }
                }

                return offered.isEmpty() ? null : support.Answering.type(offered.get(0));
            }))
        {
            SwingUtilities.invokeLater(() -> item.getActionListeners()[0].actionPerformed(
                new java.awt.event.ActionEvent(item, java.awt.event.ActionEvent.ACTION_PERFORMED, "")));

            assertTrue(answering.awaitAsked(1, 15000), "choosing the export asked nothing: " + answering.asked());
            assertTrue(answering.asked().get(0).equals(question), "the export's first question was "
                + answering.asked().get(0) + ", not the size");

            // The save dialog that follows is canceled by the answerer; wait for it to go.
            assertTrue(support.Settle.until(() ->
            {
                for (java.awt.Window w : java.awt.Window.getWindows())
                {
                    if (w instanceof javax.swing.JDialog && w.isShowing()) return false;
                }

                return true;
            }, 15000), "a dialog is still open after the size was answered");
        }

        assertEquals(offered, java.util.Arrays.asList(org.traincontrol.util.I18n.t("layout.ui.small"),
            org.traincontrol.util.I18n.t("layout.ui.medium"), org.traincontrol.util.I18n.t("layout.ui.large")),
            "the export should offer the pop-out window's Small, Medium and Large, in that order");

        for (String size : offered)
        {
            assertFalse(size.matches(".*\\d.*"), "\"" + size + "\" spells out a pixel count; users know what they use");
        }
    }

    /**
     * Medium draws at the pop-out window's Medium tile size, between Small's and Large's, and the picture follows.
     *
     * MUTATION: answer the pop-out's Large size for Medium in `diagramTileSizeFor`.
     */
    @Test
    public void testMediumExportsBetweenSmallAndLarge() throws Exception
    {
        java.lang.reflect.Method sizeFor;

        Object menus = layoutMenus();

        try
        {
            sizeFor = menus.getClass().getDeclaredMethod("diagramTileSizeFor", Object.class);
        }
        catch (NoSuchMethodException none)
        {
            fail("the export has no way to turn a picked size into a tile size");

            return;
        }

        sizeFor.setAccessible(true);

        int[] tile = new int[3];
        int[] wide = new int[3];
        String[] keys = {"layout.ui.small", "layout.ui.medium", "layout.ui.large"};

        for (int i = 0; i < 3; i++)
        {
            tile[i] = (Integer) sizeFor.invoke(menus, org.traincontrol.util.I18n.t(keys[i]));
            wide[i] = render(tile[i]).getWidth();
        }

        assertEquals(tile[1], TrainControlUI.MEDIUM_TILE_SIZE, "Medium is not the pop-out window's Medium size");
        assertTrue(tile[0] < tile[1] && tile[1] < tile[2], "Medium is not between Small and Large: " + java.util.Arrays.toString(tile));
        assertTrue(wide[0] < wide[1] && wide[1] < wide[2],
            "the pictures do not grow with the size: " + java.util.Arrays.toString(wide));
    }

    /** The window's Layouts menu, which holds the export's private steps. */
    private static Object layoutMenus() throws Exception
    {
        java.lang.reflect.Field menus = TrainControlUI.class.getDeclaredField("layoutMenus");

        menus.setAccessible(true);

        return menus.get(ui);
    }
}
