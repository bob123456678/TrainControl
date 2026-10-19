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
 * The rule grew with Adam's rulings: a tile with track on one side only meets it; only a tile newly placed from the
 * palette is turned (a pasted copy or a moved tile is not); and every track tile is turned - a curve to connect if it
 * can, a switch to have its base on the track - but not a lamp, a label or a route button.
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

    /** Where a tile dropped by landings() came from. */
    private static final int PALETTE = 0;
    private static final int COPY = 1;
    private static final int MOVE = 2;

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
     * A through tile with track on one side only is turned to meet it (Adam, OB-360: *"meet the single neighbor"*); one
     * already meeting it is left as it was; and where one way round meets track at both ends, that wins over one end.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testAThroughTileMeetsASingleNeighbour() throws Exception
    {
        for (componentType type : new componentType[] { componentType.STRAIGHT, componentType.FEEDBACK,
            componentType.SIGNAL, componentType.UNCOUPLER })
        {
            int ways = LayoutDiagramComponent.getNumOrientations(type);
            int[] helds = new int[ways];

            for (int way = 0; way < ways; way++) helds[way] = way;

            int[] east = landings(type, helds, 11, 9);
            int[] north = landings(type, helds, 6, 10);
            int[] both = landings(type, helds, 12, 5);

            for (int way = 0; way < ways; way++)
            {
                assertTrue(runsBetween(type, east[way], Side.E, Side.W), type + " held as " + way + " dropped with"
                    + " track to its east only landed as " + east[way] + ", not meeting it.  Adam, OB-360: \"meet the"
                    + " single neighbor\"");

                assertTrue(runsBetween(type, north[way], Side.N, Side.S), type + " held as " + way + " dropped with"
                    + " track to its north only landed as " + north[way] + ", not meeting it");

                // East, north and south: north-south meets two, east-west one.
                assertTrue(runsBetween(type, both[way], Side.N, Side.S), type + " held as " + way + " dropped with"
                    + " track east, north and south landed as " + both[way] + ": one end met where two could be");

                if (runsBetween(type, way, Side.E, Side.W))
                {
                    assertEquals(east[way], way, type + " already meeting its single neighbour was turned all the same");
                }

                if (runsBetween(type, way, Side.N, Side.S))
                {
                    assertEquals(north[way], way, type + " already meeting its single neighbour was turned all the"
                        + " same");

                    assertEquals(both[way], way, type + " already meeting both ends was turned all the same");
                }
            }
        }
    }

    /**
     * Only a tile newly placed from the palette is turned (Adam, OB-360: *"only turn the first time the user places it,
     * if it's already placed, don't change it."*): a tile that was copied and pasted, or dragged, keeps the way round it
     * had, wherever it lands.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testACopiedOrMovedTileIsNotTurned() throws Exception
    {
        for (componentType type : new componentType[] { componentType.STRAIGHT, componentType.FEEDBACK,
            componentType.SIGNAL, componentType.END })
        {
            int ways = LayoutDiagramComponent.getNumOrientations(type);
            int[] helds = new int[ways];

            for (int way = 0; way < ways; way++) helds[way] = way;

            for (int how : new int[] { COPY, MOVE })
            {
                // Into the east-west gap, and beside the single east-west piece of track.
                int[] gap = landings(type, helds, 2, 2, how);
                int[] beside = landings(type, helds, 8, 5, how);

                for (int way = 0; way < ways; way++)
                {
                    assertEquals(gap[way], way, "a " + (how == COPY ? "copied" : "moved") + " " + type + " lying as "
                        + way + " was turned to " + gap[way] + " on being put down.  Adam, OB-360: \"if it's already"
                        + " placed, don't change it\"");

                    assertEquals(beside[way], way, "a " + (how == COPY ? "copied" : "moved") + " " + type + " lying as "
                        + way + " was turned to " + beside[way] + " on being put down");
                }
            }
        }
    }

    /**
     * The rule is every track tile's, not the straight's (Adam, OB-360: *"we should extend it to basically all tiles, so
     * that a curve connects if it can"*): a curve, plain or with an s88 on it, is turned so that its road joins the
     * track on both of two sides if it can, and so that an end joins the one side if only one has track.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testACurveConnectsIfItCan() throws Exception
    {
        for (componentType type : new componentType[] { componentType.CURVE, componentType.FEEDBACK_CURVE })
        {
            int[] helds = { 0, 1, 2, 3 };
            int[] corner = landings(type, helds, 16, 2);
            int[] north = landings(type, helds, 16, 6);

            for (int way = 0; way < 4; way++)
            {
                assertTrue(runsBetween(type, corner[way], Side.E, Side.S), type + " held as " + way + " dropped with"
                    + " track east and south landed as " + corner[way] + ", not joining both.  Adam, OB-360: \"a curve"
                    + " connects if it can\"");

                assertTrue(endsOn(type, north[way], Side.N), type + " held as " + way + " dropped with track to its"
                    + " north only landed as " + north[way] + ", not meeting it");

                if (runsBetween(type, way, Side.E, Side.S)) assertEquals(corner[way], way, type + " already joining"
                    + " both sides was turned all the same");

                if (endsOn(type, way, Side.N)) assertEquals(north[way], way, type + " already meeting its single"
                    + " neighbour was turned all the same");
            }
        }

        // A double curve is two roads, N-W and E-S as drawn: it is turned so that one of them joins both sides.
        for (componentType type : new componentType[] { componentType.DOUBLE_CURVE,
            componentType.FEEDBACK_DOUBLE_CURVE })
        {
            int[] helds = { 0, 1, 2, 3 };
            int[] corner = landings(type, helds, 16, 2);

            for (int way = 0; way < 4; way++)
            {
                assertTrue(runsBetween(type, corner[way], Side.E, Side.S), type + " held as " + way + " dropped with"
                    + " track east and south landed as " + corner[way] + ", with no road joining both");

                if (runsBetween(type, way, Side.E, Side.S)) assertEquals(corner[way], way, type + " already joining"
                    + " both sides was turned all the same");
            }
        }
    }

    /**
     * A switch is turned so that its base - the end every position of its points shares, where a straight's end would
     * be - is on the track, guessing among the ways that do (Adam, OB-360: *"the base of a switch behaves like a
     * straight track would, so that at least something is aligned (for switches, there's always some guessing
     * involved, which is ok)"*): the nearest turn from the way it was held.  One already based on the track stays as it
     * was.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testASwitchLiesWithItsBaseOnTheTrack() throws Exception
    {
        for (componentType type : new componentType[] { componentType.SWITCH_LEFT, componentType.SWITCH_RIGHT,
            componentType.SWITCH_Y, componentType.SWITCH_THREE, componentType.CUSTOM_PERM_LEFT,
            componentType.CUSTOM_PERM_RIGHT, componentType.CUSTOM_PERM_Y, componentType.CUSTOM_PERM_THREEWAY })
        {
            int[] helds = { 0, 1, 2, 3 };
            int[] west = landings(type, helds, 18, 9);
            int[] north = landings(type, helds, 16, 6);

            for (int way = 0; way < 4; way++)
            {
                assertEquals(TilePorts.deriveToe(type, west[way]), Side.W, type + " held as " + way + " dropped with"
                    + " track to its west only landed as " + west[way] + ", its base not on it.  Adam, OB-360: \"the"
                    + " base of a switch behaves like a straight track would\"");

                assertEquals(TilePorts.deriveToe(type, north[way]), Side.N, type + " held as " + way + " dropped with"
                    + " track to its north only landed as " + north[way] + ", its base not on it");

                if (TilePorts.deriveToe(type, way) == Side.W) assertEquals(west[way], way, type + " already based on"
                    + " the track was turned all the same");

                if (TilePorts.deriveToe(type, way) == Side.N) assertEquals(north[way], way, type + " already based on"
                    + " the track was turned all the same");
            }
        }

        // Between a line north and south, a switch with a straight road runs along it as well as being based on it.
        for (componentType type : new componentType[] { componentType.SWITCH_LEFT, componentType.SWITCH_RIGHT,
            componentType.SWITCH_THREE })
        {
            int[] gap = landings(type, new int[] { 0, 1, 2, 3 }, 5, 2);

            for (int way = 0; way < 4; way++)
            {
                assertTrue(runsBetween(type, gap[way], Side.N, Side.S), type + " held as " + way + " dropped in a"
                    + " north-south gap landed as " + gap[way] + ", its straight road not along the line");
            }
        }
    }

    /**
     * A lamp, a text label and a route button are not track and are never turned, whatever is beside them.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testNonTrackTilesAreNotTurned() throws Exception
    {
        for (componentType type : new componentType[] { componentType.LAMP, componentType.TEXT, componentType.ROUTE })
        {
            int ways = LayoutDiagramComponent.getNumOrientations(type);
            int[] helds = new int[ways];

            for (int way = 0; way < ways; way++) helds[way] = way;

            int[] gap = landings(type, helds, 5, 2);
            int[] beside = landings(type, helds, 11, 9);

            for (int way = 0; way < ways; way++)
            {
                assertEquals(gap[way], way, type + " held as " + way + " was turned in a gap in a line");
                assertEquals(beside[way], way, type + " held as " + way + " was turned beside track");
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
            componentType.UNCOUPLER, componentType.END, componentType.TUNNEL, componentType.LINK, componentType.CURVE,
            componentType.DOUBLE_CURVE, componentType.FEEDBACK_CURVE, componentType.FEEDBACK_DOUBLE_CURVE,
            componentType.SWITCH_LEFT, componentType.SWITCH_RIGHT, componentType.SWITCH_Y, componentType.SWITCH_THREE,
            componentType.SWITCH_CROSSING, componentType.CROSSING, componentType.OVERPASS,
            componentType.CUSTOM_PERM_LEFT, componentType.CUSTOM_PERM_RIGHT, componentType.CUSTOM_PERM_Y,
            componentType.CUSTOM_PERM_THREEWAY })
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

    /** Whether any road of a tile of this type, this way round, ends on this side. */
    private static boolean endsOn(componentType type, int orientation, Side side)
    {
        for (TilePorts.Route road : TilePorts.ports(type, orientation, 0))
        {
            if (road.touches(side)) return true;
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
     * 10,7.  And for a tile that runs through: track east of 11,9 only, north of 6,10 only, and east, north and south of
     * 12,5 (two ends can be met, or one).  And for the rest: track east and south of 16,2 (a corner), north of 16,6 only,
     * and west of 18,9 only.
     */
    private static int[] landings(componentType type, int[] helds, int x, int y) throws Exception
    {
        return landings(type, helds, x, y, PALETTE);
    }

    /**
     * The same, for a tile that came from somewhere else: PALETTE picks it up off the palette, COPY copies one lying on
     * the page at 0,0 and pastes it, MOVE drags that one.
     */
    private static int[] landings(componentType type, int[] helds, int x, int y, int how) throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("OB-360", 20, 12, null, null);

        page.addComponent(componentType.STRAIGHT, 1, 2, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 2, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 1, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 3, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 9, 5, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 8, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 5, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 7, 2, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 12, 9, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 6, 9, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 13, 5, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 12, 4, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 12, 6, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 17, 2, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 16, 3, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 16, 5, NORTH_SOUTH, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 17, 9, EAST_WEST, 0, 0, 0, accessoryDecoderType.MM2, null);

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

                if (how != PALETTE) page.addComponent(type, 0, 0, helds[i], 0, 0, 0, accessoryDecoderType.MM2, null);

                // Every drop redraws the grid into a new one, so the square is asked of the editor's grid as it is now.
                final LayoutLabel target = ((LayoutGrid) field.get(editor[0])).getValueAt(x, y);
                final LayoutLabel source = ((LayoutGrid) field.get(editor[0])).getValueAt(0, 0);

                assertNotNull(target, "precondition: the editor drew no square at " + x + "," + y);

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    if (how == PALETTE) editor[0].initCopy(null, held, false);
                    else editor[0].initCopy(source, null, how == MOVE);

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
