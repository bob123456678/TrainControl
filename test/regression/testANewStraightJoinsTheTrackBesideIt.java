package regression;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
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
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.automationui.TilePorts.Side;
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
 * OB-360: the same for every other straight-shaped tile - the s88 feedback tile, the signal and the uncoupler, which
 * run through the square like a straight, and the bumper, the tunnel and the link, which end the track at one side.  The
 * through tiles are turned to lie along the track on both sides; the one-ended tiles are turned to face the track beside
 * them.  Adam: *"when placing straight segments, the layout editor will turn a new straight tile to match the path
 * around it.  however, this doesn't apply to straight s88 tiles.  check that it does, and also ake the same check for
 * tunnels, uncouplers, links, signals, and bumpers."*
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
     * An s88 tile, a signal or an uncoupler dropped into a gap lies along the line, whichever way the palette held it,
     * and one already lying along it keeps its way round (a signal's four ways differ in which end it faces).
     *
     * @throws Exception from the editor
     */
    @Test
    public void testAThroughTileDroppedIntoAGapJoinsTheLine() throws Exception
    {
        for (componentType type : new componentType[] { componentType.FEEDBACK, componentType.SIGNAL,
            componentType.UNCOUPLER })
        {
            int ways = LayoutDiagramComponent.getNumOrientations(type);
            int[] helds = new int[ways];

            for (int way = 0; way < ways; way++) helds[way] = way;

            int[] eastWest = landings(type, helds, 2, 2);
            int[] northSouth = landings(type, helds, 5, 2);

            for (int way = 0; way < ways; way++)
            {
                assertTrue(runsBetween(type, eastWest[way], Side.E, Side.W), type + " held as " + way + " dropped"
                    + " between two east-west straights landed as " + eastWest[way] + ", joining neither.  Adam, OB-360:"
                    + " \"the layout editor will turn a new straight tile to match the path around it\"");

                assertTrue(runsBetween(type, northSouth[way], Side.N, Side.S), type + " held as " + way + " dropped"
                    + " between two north-south straights landed as " + northSouth[way] + ", joining neither");

                if (runsBetween(type, way, Side.E, Side.W))
                {
                    assertEquals(eastWest[way], way, type + " already lying along the line was turned all the same");
                }

                if (runsBetween(type, way, Side.N, Side.S))
                {
                    assertEquals(northSouth[way], way, type + " already lying along the line was turned all the same");
                }
            }
        }
    }

    /**
     * A bumper, a tunnel or a link ends the track at one side: dropped beside a piece of track it is turned so that its
     * end is on that side, from whichever way the palette held it.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testAOneEndedTileFacesTheTrackBesideIt() throws Exception
    {
        for (componentType type : new componentType[] { componentType.END, componentType.TUNNEL, componentType.LINK })
        {
            int[] helds = { 0, 1, 2, 3 };

            // The track is east of (8,5), south of (2,7), north of (5,6) and west of (8,2).
            assertAll(type, landings(type, helds, 8, 5), Side.E, "east");
            assertAll(type, landings(type, helds, 2, 7), Side.S, "south");
            assertAll(type, landings(type, helds, 5, 6), Side.N, "north");
            assertAll(type, landings(type, helds, 8, 2), Side.W, "west");
        }
    }

    /**
     * With nothing to join, every one of them lies the way it was held - the control, so a rule that turns every tile
     * fails.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testATileWithNothingToJoinLiesAsItWasHeld() throws Exception
    {
        for (componentType type : new componentType[] { componentType.FEEDBACK, componentType.SIGNAL,
            componentType.UNCOUPLER, componentType.END, componentType.TUNNEL, componentType.LINK })
        {
            int ways = LayoutDiagramComponent.getNumOrientations(type);
            int[] helds = new int[ways];

            for (int way = 0; way < ways; way++) helds[way] = way;

            int[] landed = landings(type, helds, 10, 7);

            for (int way = 0; way < ways; way++)
            {
                assertEquals(landed[way], way, type + " held as " + way + " dropped where nothing is beside it was"
                    + " turned all the same");
            }
        }
    }

    /** Whether a tile of this type, this way round, has a road between the two sides. */
    private static boolean runsBetween(componentType type, int orientation, Side one, Side other)
    {
        for (TilePorts.Route road : TilePorts.ports(type, orientation, 0))
        {
            if (road.touches(one) && road.touches(other)) return true;
        }

        return false;
    }

    /** Whether a one-ended tile's end is on this side, for every way it was held. */
    private static void assertAll(componentType type, int[] landed, Side side, String where)
    {
        for (int held = 0; held < landed.length; held++)
        {
            boolean faces = false;

            for (TilePorts.Route road : TilePorts.ports(type, landed[held], 0))
            {
                if (road.touches(side)) faces = true;
            }

            assertTrue(faces, type + " held as " + held + " dropped with track to the " + where + " landed as "
                + landed[held] + ", with its end on the wrong side.  Adam, OB-360: \"also ake the same check for"
                + " tunnels, uncouplers, links, signals, and bumpers\"");

            boolean heldFaces = false;

            for (TilePorts.Route road : TilePorts.ports(type, held, 0))
            {
                if (road.touches(side)) heldFaces = true;
            }

            if (heldFaces)
            {
                assertEquals(landed[held], held, type + " already facing the track to the " + where + " was turned all"
                    + " the same");
            }
        }
    }

    /**
     * One page, one editor: drops a tile of the type on the square once for each way round it may be held, and says how
     * each landed.  The page: an east-west line broken at 2,2, a north-south line broken at 5,2, and four lone pieces of
     * track for a one-ended tile to face - east of 8,5, south of 2,7, north of 5,6 and west of 8,2.  Nothing is beside
     * 10,7.
     */
    private static int[] landings(componentType type, int[] helds, int x, int y) throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("OB-360", 12, 10, null, null);

        page.addComponent(componentType.STRAIGHT, 1, 2, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 2, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 1, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 3, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 9, 5, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 8, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 5, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 7, 2, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);

        final LayoutEditor[] editor = new LayoutEditor[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            editor[0] = new LayoutEditor(page, 30, ui, 0);
            editor[0].render();
        });

        final java.util.concurrent.CountDownLatch settled = new java.util.concurrent.CountDownLatch(1);

        ui.whenTilesSettled(() -> settled.countDown());

        settled.await(30, java.util.concurrent.TimeUnit.SECONDS);

        for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

        try
        {
            java.lang.reflect.Field field = LayoutEditor.class.getDeclaredField("grid");

            field.setAccessible(true);

            int[] landed = new int[helds.length];

            for (int i = 0; i < helds.length; i++)
            {
                LayoutDiagram palette = new LayoutDiagram("palette", 2, 2, null, null);

                palette.addComponent(type, 0, 0, helds[i], 0, 0, 0, accessoryDecoderType.MM2, null);

                final LayoutDiagramComponent held = palette.getComponent(0, 0);

                assertNotNull(held, "precondition: the palette tile was not made");

                page.addComponent((LayoutDiagramComponent) null, x, y);

                // Every drop redraws the grid into a new one, so the square is asked of the editor's grid as it is now.
                final LayoutLabel target = ((LayoutGrid) field.get(editor[0])).getValueAt(x, y);

                assertNotNull(target, "precondition: the editor drew no square at " + x + "," + y);

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    editor[0].initCopy(null, held, false);
                    editor[0].executeTool(target);
                });

                LayoutDiagramComponent dropped = page.getComponent(x, y);

                assertNotNull(dropped, "the " + type + " was not placed at " + x + "," + y);

                assertEquals(dropped.getType(), type, "something other than a " + type + " landed");

                landed[i] = dropped.getOrientation();

                for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });
            }

            return landed;
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> editor[0].dispose());
        }
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
