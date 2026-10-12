package regression;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A straight dropped between two pieces of track is turned to join them (OB-292).
 *
 * Adam, 2026-09-24: *"when new straight tracks are placed in the autonomy editor and they would connect two other
 * tracks, they are automatically oriented to connect rather than not."*  A straight came off the palette the way the
 * palette held it, so dropping one into a gap in a north-south line left it lying east-west, joining neither side,
 * until it was turned by hand.
 *
 * Asked of the editor itself - the palette pick-up and the drop - because the rule is only worth anything where the tile
 * lands.  On a page of its own, in memory: nothing is saved.
 *
 * MUTATION: place the straight the way the palette holds it again, and this fails; turn it whatever is beside it, and
 * the control fails.
 *
 * @author Adam
 */
public class testANewStraightJoinsTheTrackBesideIt
{
    /** STRAIGHT's two ways round, as the diagrams in the tests draw them: 0 runs east-west, 1 north-south. */
    private static final int EAST_WEST = 0;
    private static final int NORTH_SOUTH = 1;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("placing a tile needs the editor, which needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * Into a gap in an east-west line it lies east-west, and into a gap in a north-south line north-south, whichever
     * way the palette held it.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testAStraightDroppedIntoAGapJoinsTheLine() throws Exception
    {
        assertEquals(placeBetween(NORTH_SOUTH, 2, 2), EAST_WEST, "a straight dropped between two east-west straights"
            + " lies north-south, joining neither.  Adam, OB-292: \"when new straight tracks ... would connect two other"
            + " tracks, they are automatically oriented to connect\"");

        assertEquals(placeBetween(EAST_WEST, 5, 2), NORTH_SOUTH, "a straight dropped between two north-south straights"
            + " lies east-west, joining neither");
    }

    /**
     * With nothing to join, it lies the way it was held - the control, so a rule that turns every straight fails.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testAStraightWithNothingToJoinLiesAsItWasHeld() throws Exception
    {
        assertEquals(placeBetween(NORTH_SOUTH, 2, 5), NORTH_SOUTH, "a straight dropped where nothing is beside it was"
            + " turned all the same");

        assertEquals(placeBetween(EAST_WEST, 2, 5), EAST_WEST, "a straight dropped where nothing is beside it was"
            + " turned all the same");
    }

    /**
     * A page with an east-west line broken at 2,2 and a north-south line broken at 5,2; picks a straight up off the
     * palette the given way round, drops it on the square, and says which way round it landed.
     */
    private static int placeBetween(int heldAs, int x, int y) throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("OB-292", 8, 7, null, null);

        page.addComponent(componentType.STRAIGHT, 1, 2, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 2, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 1, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 3, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);

        // THE PALETTE'S TILE, the way round it is held: minted on a scratch page, as the palette's own are, and off it.
        LayoutDiagram palette = new LayoutDiagram("palette", 2, 2, null, null);

        palette.addComponent(componentType.STRAIGHT, 0, 0, heldAs, 0, 0, 0, accessoryDecoderType.MM2, null);

        final LayoutDiagramComponent held = palette.getComponent(0, 0);

        assertNotNull(held, "precondition: the palette tile was not made");

        final LayoutEditor[] editor = new LayoutEditor[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            editor[0] = new LayoutEditor(page, 30, ui, 0);
            editor[0].render();
        });

        // RENDER DRAWS THE GRID ON A LATER TURN of the event queue, as the other editor tests wait for.
        final java.util.concurrent.CountDownLatch settled = new java.util.concurrent.CountDownLatch(1);

        ui.whenTilesSettled(() -> settled.countDown());

        settled.await(30, java.util.concurrent.TimeUnit.SECONDS);

        for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

        try
        {
            java.lang.reflect.Field field = LayoutEditor.class.getDeclaredField("grid");

            field.setAccessible(true);

            final LayoutGrid grid = (LayoutGrid) field.get(editor[0]);
            final LayoutLabel target = grid.getValueAt(x, y);

            assertNotNull(target, "precondition: the editor drew no square at " + x + "," + y);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                editor[0].initCopy(null, held, false);
                editor[0].executeTool(target);
            });

            LayoutDiagramComponent landed = page.getComponent(x, y);

            assertNotNull(landed, "the straight was not placed at " + x + "," + y);

            assertEquals(landed.getType(), componentType.STRAIGHT, "something other than a straight landed");

            return landed.getOrientation();
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> editor[0].dispose());
        }
    }
}
