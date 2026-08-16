import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessorySetting;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.base.TilePorts;
import org.traincontrol.base.TilePorts.AccessorySlot;
import org.traincontrol.base.TilePorts.Route;
import org.traincontrol.base.TilePorts.Side;

/**
 * The port map: which sides of each tile type connect, in each switch position, at each orientation.
 *
 * This table is the seed for the whole autonomy tile graph, and it was verified by eye against the icon
 * art rather than derived from anything the code can check.  So these tests do not re-derive it - they
 * pin the readings that were confirmed, and they fail loudly if a tile type is ever added without being
 * classified, which is the failure that would otherwise be silent: an unmapped type traces as impassable
 * track and simply removes part of the layout from autonomy.
 *
 * No hardware, no UI, no LocDB - TilePorts is pure data.
 *
 * @author Adam
 */
public class testTilePorts
{
    /**
     * Every componentType must be accounted for.  A new tile type that nobody classifies would return no
     * routes and be treated as impassable, quietly cutting the layout in half at that tile.
     */
    @Test
    public void testEveryComponentTypeIsClassified()
    {
        List<componentType> unclassified = new ArrayList<>();

        for (componentType type : componentType.values())
        {
            boolean classified = TilePorts.getStateCount(type) > 0
                    || TilePorts.isDisqualified(type)
                    || TilePorts.isTerminator(type);

            if (!classified)
            {
                unclassified.add(type);
            }
        }

        assertTrue(unclassified.isEmpty(),
            "Unclassified tile types would trace as impassable track: " + unclassified);
    }

    /**
     * The rotation convention, pinned by two readings that were confirmed against the art.  getImage
     * rotates by (4 - orientation) * 90 clockwise, so orientation 1 applies three quarter turns.
     */
    @Test
    public void testRotationFollowsTheImageConvention()
    {
        // A straight is E-W drawn, and vertical when rotated once
        assertEquals(sidesAt(componentType.STRAIGHT, 0, 0), pairs("EW"));
        assertEquals(sidesAt(componentType.STRAIGHT, 1, 0), pairs("NS"));

        // A curve joins E and S drawn; one rotation takes that to N and E
        assertEquals(sidesAt(componentType.CURVE, 0, 0), pairs("ES"));
        assertEquals(sidesAt(componentType.CURVE, 1, 0), pairs("NE"));
        assertEquals(sidesAt(componentType.CURVE, 2, 0), pairs("NW"));
        assertEquals(sidesAt(componentType.CURVE, 3, 0), pairs("SW"));
    }

    /**
     * A double curve is two independent curves in one tile, not a crossing.
     */
    @Test
    public void testDoubleCurveIsTwoIndependentCurves()
    {
        for (componentType type : new componentType[]{
            componentType.DOUBLE_CURVE, componentType.FEEDBACK_DOUBLE_CURVE})
        {
            Set<String> routes = sidesAt(type, 0, 0);

            assertEquals(routes, pairs("NW", "ES"), type + " at orientation 0");
            assertEquals(routes.size(), 2, type + " should offer exactly two routes");
        }
    }

    /**
     * The orientation domain is the type's rotational symmetry, not always four.  Asking beyond it must
     * fold back rather than invent a rotation the diagram can never hold.
     */
    @Test
    public void testOrientationDomainMatchesRotationalSymmetry()
    {
        assertEquals(TilePorts.numOrientations(componentType.STRAIGHT), 2);
        assertEquals(TilePorts.numOrientations(componentType.FEEDBACK), 2);
        assertEquals(TilePorts.numOrientations(componentType.OVERPASS), 2);
        assertEquals(TilePorts.numOrientations(componentType.SWITCH_CROSSING), 2);
        assertEquals(TilePorts.numOrientations(componentType.CROSSING), 1);
        assertEquals(TilePorts.numOrientations(componentType.TURNTABLE), 1);
        assertEquals(TilePorts.numOrientations(componentType.CURVE), 4);
        assertEquals(TilePorts.numOrientations(componentType.SWITCH_LEFT), 4);

        // A straight has two orientations, so 2 folds back onto 0 rather than producing a third reading
        assertEquals(sidesAt(componentType.STRAIGHT, 2, 0), sidesAt(componentType.STRAIGHT, 0, 0));

        // A crossing has one, so every orientation reads the same
        for (int o = 0; o < 4; o++)
        {
            assertEquals(sidesAt(componentType.CROSSING, o, 0), pairs("NS", "EW"),
                "CROSSING at orientation " + o);
        }
    }

    /**
     * Throwing a switch REPLACES its routes rather than adding to them.  This is the reading that removed
     * the whole common-leg/branch/toe apparatus from the port map, so it is worth pinning per type.
     */
    @Test
    public void testSwitchStatesReplaceRatherThanAdd()
    {
        // Straight through when unswitched, diverging left when thrown - N is unreachable once thrown
        assertEquals(sidesAt(componentType.SWITCH_LEFT, 0, 0), pairs("NS"));
        assertEquals(sidesAt(componentType.SWITCH_LEFT, 0, 1), pairs("SW"));

        assertEquals(sidesAt(componentType.SWITCH_RIGHT, 0, 0), pairs("NS"));
        assertEquals(sidesAt(componentType.SWITCH_RIGHT, 0, 1), pairs("SE"));

        // A Y turnout has no straight route in either position
        assertEquals(sidesAt(componentType.SWITCH_Y, 0, 0), pairs("SW"));
        assertEquals(sidesAt(componentType.SWITCH_Y, 0, 1), pairs("SE"));
        assertFalse(sidesAt(componentType.SWITCH_Y, 0, 0).contains("NS"));
        assertFalse(sidesAt(componentType.SWITCH_Y, 0, 1).contains("NS"));

        // Three positions from one toe
        assertEquals(sidesAt(componentType.SWITCH_THREE, 0, 0), pairs("NS"));
        assertEquals(sidesAt(componentType.SWITCH_THREE, 0, 1), pairs("SW"));
        assertEquals(sidesAt(componentType.SWITCH_THREE, 0, 2), pairs("SE"));

        // A double slip swaps its two through routes for its two diagonals
        assertEquals(sidesAt(componentType.SWITCH_CROSSING, 0, 0), pairs("NS", "EW"));
        assertEquals(sidesAt(componentType.SWITCH_CROSSING, 0, 1), pairs("NW", "SE"));
    }

    /**
     * Every turnout route touches S at orientation 0 - S is the toe.  Nothing in the code depends on the
     * word "toe", but the direction default (base to forks) and the defective-switch restriction are both
     * expressed in terms of it, so if this reading were wrong both would be backwards.
     */
    @Test
    public void testEveryTurnoutRouteTouchesTheToe()
    {
        componentType[] turnouts = {
            componentType.SWITCH_LEFT, componentType.SWITCH_RIGHT, componentType.SWITCH_Y,
            componentType.SWITCH_THREE, componentType.CUSTOM_PERM_LEFT, componentType.CUSTOM_PERM_RIGHT,
            componentType.CUSTOM_PERM_Y, componentType.CUSTOM_PERM_THREEWAY
        };

        for (componentType type : turnouts)
        {
            for (int state = 0; state < TilePorts.getStateCount(type); state++)
            {
                for (Route r : TilePorts.ports(type, 0, state))
                {
                    assertTrue(r.touches(Side.S),
                        type + " state " + state + " route " + r + " does not touch the toe");
                }
            }
        }
    }

    /**
     * A defective switch has no address and cannot be thrown, so a facing move cannot choose a branch.
     * Trailing moves merge safely whatever the blades are doing.  Note this is the exact opposite of a
     * working switch's default, and both are correct - do not harmonize them.
     */
    @Test
    public void testDefectiveSwitchesAreTrailingOnly()
    {
        componentType[] defective = {
            componentType.CUSTOM_PERM_LEFT, componentType.CUSTOM_PERM_RIGHT,
            componentType.CUSTOM_PERM_Y, componentType.CUSTOM_PERM_THREEWAY
        };

        for (componentType type : defective)
        {
            List<Route> routes = TilePorts.ports(type, 0, 0);

            assertFalse(routes.isEmpty(), type + " should still offer routes");

            for (Route r : routes)
            {
                Side branch = r.other(Side.S);

                assertTrue(r.isTraversableFrom(branch),
                    type + ": trailing move " + branch + "->S must be allowed");
                assertFalse(r.isTraversableFrom(Side.S),
                    type + ": facing move S->" + branch + " must be refused");
            }

            // and no accessory is ever commanded, since there is no address to command
            assertTrue(TilePorts.commands(type, 0).isEmpty(), type + " must command nothing");
        }
    }

    /**
     * The restriction has to survive rotation - a defective switch drawn sideways is still trailing only,
     * toward wherever its toe has rotated to.
     */
    @Test
    public void testTrailingOnlyRestrictionRotates()
    {
        for (int o = 0; o < 4; o++)
        {
            Side toe = Side.S.rotateClockwise(4 - o);

            for (Route r : TilePorts.ports(componentType.CUSTOM_PERM_LEFT, o, 0))
            {
                assertEquals(r.getDirectedToward(), toe,
                    "orientation " + o + ": route " + r + " should be directed toward " + toe);
                assertTrue(r.isTraversableFrom(r.other(toe)));
                assertFalse(r.isTraversableFrom(toe));
            }
        }
    }

    /**
     * Straight and thrown always differentiate, so every address a tile has is specified in every state.
     * Leaving one unmentioned would let a previously thrown branch stay selected.
     */
    @Test
    public void testEveryStateCommandsEveryAddressItHas()
    {
        // Two-position switches command their one address both ways
        assertEquals(TilePorts.commands(componentType.SWITCH_LEFT, 0).get(AccessorySlot.PRIMARY),
            accessorySetting.STRAIGHT);
        assertEquals(TilePorts.commands(componentType.SWITCH_LEFT, 1).get(AccessorySlot.PRIMARY),
            accessorySetting.TURN);

        // A three-way commands BOTH addresses in all three positions
        for (int state = 0; state < 3; state++)
        {
            assertEquals(TilePorts.commands(componentType.SWITCH_THREE, state).size(), 2,
                "three-way state " + state + " must specify both addresses");
        }

        assertEquals(TilePorts.commands(componentType.SWITCH_THREE, 0).get(AccessorySlot.PRIMARY),
            accessorySetting.STRAIGHT);
        assertEquals(TilePorts.commands(componentType.SWITCH_THREE, 0).get(AccessorySlot.SECONDARY),
            accessorySetting.STRAIGHT);
        assertEquals(TilePorts.commands(componentType.SWITCH_THREE, 1).get(AccessorySlot.PRIMARY),
            accessorySetting.TURN);
        assertEquals(TilePorts.commands(componentType.SWITCH_THREE, 2).get(AccessorySlot.SECONDARY),
            accessorySetting.TURN);

        // A double slip is the only tile that must be commanded in BOTH states rather than only when thrown
        assertFalse(TilePorts.commands(componentType.SWITCH_CROSSING, 0).isEmpty());
        assertFalse(TilePorts.commands(componentType.SWITCH_CROSSING, 1).isEmpty());
    }

    /**
     * Crossings and overpasses look identical here on purpose: same two routes, never joining.  What
     * separates them is not the port map but lock derivation, where an overpass is the sole tile whose
     * two routes do not conflict.
     */
    @Test
    public void testCrossingAndOverpassOfferTwoDisjointRoutes()
    {
        for (componentType type : new componentType[]{componentType.CROSSING, componentType.OVERPASS})
        {
            List<Route> routes = TilePorts.ports(type, 0, 0);

            assertEquals(routes.size(), 2, type + " should offer two routes");

            Set<Side> seen = new HashSet<>();

            for (Route r : routes)
            {
                assertTrue(seen.add(r.getA()), type + ": side " + r.getA() + " appears in two routes");
                assertTrue(seen.add(r.getB()), type + ": side " + r.getB() + " appears in two routes");
            }

            assertEquals(seen.size(), 4, type + " should use all four sides exactly once");
        }
    }

    /**
     * Portal tiles connect on one visible side; the continuation is authored, never inferred.  An
     * unnamed or unpaired portal simply does not connect, which is the safe default when autonomy is
     * added to a diagram someone drew years ago.
     */
    @Test
    public void testPortalTilesHaveOneVisibleSide()
    {
        assertTrue(TilePorts.hasPortal(componentType.TUNNEL));
        assertTrue(TilePorts.hasPortal(componentType.LINK));
        assertFalse(TilePorts.hasPortal(componentType.STRAIGHT));

        // A tunnel's visible side is S as drawn; a link attaches opposite its arrow head, so W
        assertEquals(singleStub(componentType.TUNNEL, 0), Side.S);
        assertEquals(singleStub(componentType.LINK, 0), Side.W);

        // and they rotate
        assertEquals(singleStub(componentType.TUNNEL, 1), Side.E);
        assertEquals(singleStub(componentType.LINK, 1), Side.N);

        // An END is a stub too, but with no continuation at all
        assertEquals(singleStub(componentType.END, 0), Side.N);
        assertFalse(TilePorts.hasPortal(componentType.END));
    }

    /**
     * Scissors are a drawing convention - two tiles depicting one double slip - so a diagram carrying one
     * is refused rather than having the tile ignored.  Ignoring it would leave a hole that traces quietly
     * route around, which is worse than saying no.
     */
    @Test
    public void testScissorsAreDisqualifiedAndTurntablesTerminate()
    {
        assertTrue(TilePorts.isDisqualified(componentType.CUSTOM_SCISSORS));
        assertTrue(TilePorts.isDisqualified(componentType.CUSTOM_PERM_SCISSORS));
        assertFalse(TilePorts.isRoutable(componentType.CUSTOM_SCISSORS));

        // A turntable is legitimate track that simply is not routable, so it stops autonomy rather than
        // refusing the whole diagram
        assertTrue(TilePorts.isTerminator(componentType.TURNTABLE));
        assertFalse(TilePorts.isDisqualified(componentType.TURNTABLE));
        assertFalse(TilePorts.isRoutable(componentType.TURNTABLE));
    }

    /**
     * Decoration carries no track.  A lamp sitting in the middle of a run must break it, not bridge it.
     */
    @Test
    public void testDecorationCarriesNoTrack()
    {
        for (componentType type : new componentType[]{
            componentType.LAMP, componentType.ROUTE, componentType.TEXT})
        {
            assertTrue(TilePorts.ports(type, 0, 0).isEmpty(), type + " must carry no routes");
            assertFalse(TilePorts.isRoutable(type), type + " must not be routable");
        }
    }

    /**
     * Signals and uncouplers carry an address but do not branch: topologically they are plain straights.
     */
    @Test
    public void testSignalAndUncouplerAreStraights()
    {
        assertEquals(sidesAt(componentType.SIGNAL, 0, 0), pairs("EW"));
        assertEquals(sidesAt(componentType.UNCOUPLER, 0, 0), pairs("EW"));

        // one position each - they never change which way track runs
        assertEquals(TilePorts.getStateCount(componentType.SIGNAL), 1);
        assertEquals(TilePorts.getStateCount(componentType.UNCOUPLER), 1);
    }

    /**
     * A train cannot pass a red signal, so crossing a signal tile commands it green - the same shape as a
     * switch command, which is what lets the reducer gather both without a special case.  Setting other
     * signals red for safety stays with conditional routes.
     *
     * An uncoupler is not a condition of passing: firing it is something the user asks for.
     */
    @Test
    public void testCrossingASignalCommandsItGreen()
    {
        assertEquals(TilePorts.commands(componentType.SIGNAL, 0).get(AccessorySlot.PRIMARY),
            accessorySetting.GREEN);
        assertEquals(TilePorts.commands(componentType.SIGNAL, 0).size(), 1,
            "a signal has one address");

        assertTrue(TilePorts.commands(componentType.UNCOUPLER, 0).isEmpty(),
            "passing over an uncoupler must not fire it");
    }

    /**
     * Feedback tiles are plain track that also carries an s88.  Every one becomes a Point, so their
     * topology has to match their non-feedback equivalents exactly.
     */
    @Test
    public void testFeedbackTilesMatchTheirPlainEquivalents()
    {
        assertEquals(sidesAt(componentType.FEEDBACK, 0, 0), sidesAt(componentType.STRAIGHT, 0, 0));
        assertEquals(sidesAt(componentType.FEEDBACK_CURVE, 0, 0), sidesAt(componentType.CURVE, 0, 0));
        assertEquals(sidesAt(componentType.FEEDBACK_DOUBLE_CURVE, 0, 0),
            sidesAt(componentType.DOUBLE_CURVE, 0, 0));
    }

    // --- helpers ----------------------------------------------------------------------------------

    /**
     * The routes of a tile as a set of side-pair strings in NESW order, e.g. {"NS", "EW"}.
     */
    private Set<String> sidesAt(componentType type, int orientation, int state)
    {
        Set<String> out = new HashSet<>();

        for (Route r : TilePorts.ports(type, orientation, state))
        {
            out.add(canonical(r.getA(), r.getB()));
        }

        return out;
    }

    private Set<String> pairs(String... names)
    {
        Set<String> out = new HashSet<>();

        for (String n : names)
        {
            out.add(canonical(Side.valueOf(n.substring(0, 1)), Side.valueOf(n.substring(1, 2))));
        }

        return out;
    }

    /**
     * Side pairs are unordered, so name them in enum order to compare them.
     */
    private String canonical(Side a, Side b)
    {
        return a.ordinal() <= b.ordinal() ? a.toString() + b.toString() : b.toString() + a.toString();
    }

    /**
     * The single open side of a stub tile.
     */
    private Side singleStub(componentType type, int orientation)
    {
        List<Route> routes = TilePorts.ports(type, orientation, 0);

        assertEquals(routes.size(), 1, type + " should have exactly one route");
        assertEquals(routes.get(0).getA(), routes.get(0).getB(), type + " should be a stub");

        return routes.get(0).getA();
    }
}
