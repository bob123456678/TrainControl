package core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessorySetting;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.automationui.TilePorts.AccessorySlot;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;

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
public class testAutonomyDiagramPorts
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
     * The rotation direction, pinned against the art rather than against reasoning about it.
     *
     * getImage rotates by (4 - orientation) * 90 degrees, and Java2D's positive angle is clockwise on
     * screen because y points down - so orientation 1 is three quarter turns clockwise, which looks like
     * one turn anticlockwise.  Getting that backwards would still produce a self-consistent port map, and
     * every edge in the layout would be wrong.
     *
     * These values were measured by applying the same transform to the icons and reading which sides the
     * track touches (docs/plans/portmap-verification.py has the extraction).
     */
    @Test
    public void testRotationDirectionMatchesTheRenderedArt()
    {
        // curve: ES -> NE -> NW -> SW
        assertEquals(sidesAt(componentType.CURVE, 1, 0), pairs("NE"));
        assertEquals(sidesAt(componentType.CURVE, 2, 0), pairs("NW"));
        assertEquals(sidesAt(componentType.CURVE, 3, 0), pairs("SW"));

        // end stub: N -> W -> S -> E
        assertEquals(singleStub(componentType.END, 0), Side.N);
        assertEquals(singleStub(componentType.END, 1), Side.W);
        assertEquals(singleStub(componentType.END, 2), Side.S);
        assertEquals(singleStub(componentType.END, 3), Side.E);

        // tunnel stub: S -> E -> N -> W
        assertEquals(singleStub(componentType.TUNNEL, 1), Side.E);
        assertEquals(singleStub(componentType.TUNNEL, 2), Side.N);
        assertEquals(singleStub(componentType.TUNNEL, 3), Side.W);

        // switch_left occupies NSW drawn, and ESW / NES / NEW as it turns
        assertEquals(occupiedSides(componentType.SWITCH_LEFT, 0), "NSW");
        assertEquals(occupiedSides(componentType.SWITCH_LEFT, 1), "ESW");
        assertEquals(occupiedSides(componentType.SWITCH_LEFT, 2), "NES");
        assertEquals(occupiedSides(componentType.SWITCH_LEFT, 3), "NEW");
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

        // and they rotate - orientation 1 is three quarter turns clockwise, i.e. one counter-clockwise,
        // so S goes to E and W goes to S
        assertEquals(singleStub(componentType.TUNNEL, 1), Side.E);
        assertEquals(singleStub(componentType.LINK, 1), Side.S);

        // a second rotation moves each on by one more counter-clockwise step
        assertEquals(singleStub(componentType.TUNNEL, 2), Side.N);
        assertEquals(singleStub(componentType.LINK, 2), Side.E);

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
        for (componentType type : new componentType[]{componentType.LAMP, componentType.TEXT})
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
     * A route button sits ON the line rather than beside it, and the track runs beneath it.
     *
     * Its own art carries no track - it is a pair of arrows - so this cannot be read off the icon.  It
     * comes from how layouts use them: the sample layout threads 43 through its running lines and its
     * hand-built graph connects straight across every one.  Treating them as decoration severed every
     * run that contained one.
     *
     * Orientation says nothing here - the same drehung appears in horizontal and vertical runs - so it
     * conducts both ways like a crossing, continuing whatever line it sits in without joining two lines
     * that merely meet at it.
     */
    @Test
    public void testARouteButtonDeclaresItselfTransparentRatherThanClaimingTrack()
    {
        assertTrue(TilePorts.isTransparent(componentType.ROUTE),
            "a route button carries no track of its own");

        // it claims no sides here, because which sides it conducts is not a property of the tile: the
        // same button appears in horizontal and vertical runs at the same orientation, and beside the
        // rails carrying nothing at all.  TileGraph settles it from the neighbours.
        assertTrue(TilePorts.ports(componentType.ROUTE, 0, 0).isEmpty(),
            "a transparent tile must not claim sides the art does not have");

        // but it is still routable - trains do pass over it, unlike a lamp
        assertTrue(TilePorts.isRoutable(componentType.ROUTE));
        assertFalse(TilePorts.isRoutable(componentType.LAMP));

        // and it commands nothing: a button on the line is not a condition of passing it
        assertTrue(TilePorts.commands(componentType.ROUTE, 0).isEmpty());
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
     * Every side the tile touches across all of its states, in NESW order - the reading the icon
     * extraction produces, so it can be compared against measured art.
     */
    private String occupiedSides(componentType type, int orientation)
    {
        Set<Side> seen = new HashSet<>();

        for (int state = 0; state < TilePorts.getStateCount(type); state++)
        {
            for (Route r : TilePorts.ports(type, orientation, state))
            {
                seen.add(r.getA());
                seen.add(r.getB());
            }
        }

        StringBuilder out = new StringBuilder();

        for (Side s : new Side[]{Side.N, Side.E, Side.S, Side.W})
        {
            if (seen.contains(s)) out.append(s.toString());
        }

        return out.toString();
    }

    /**
     * The single open side of a stub tile.
     */

    /**
     * The whole port table, every tile type at once.
     *
     * This is `docs/plans/portmap-verification.py` brought into the suite (DD-C10). That script held
     * the only complete statement of the port map anywhere - all twenty-eight tile types, their routes
     * and their branches - and drew a picture of it for a person to check against the artwork. The
     * trouble was how it stayed true: `TilePorts`'s javadoc instructed the reader to keep the Python
     * copy in step BY HAND. A verification that has to be hand-synchronised with the thing it verifies
     * is not a verification, it is a second opinion with the same author - and that copy had already
     * gone stale, still marking the LINK side "UNCONFIRMED" long after `d4d5b7ba` confirmed it.
     *
     * So the table is here, in the language it describes, where it is executed rather than remembered.
     *
     * **The union across every state, not state 0.** A three-way has two addresses and therefore more
     * states than a simple switch, and numbering them is `TilePorts`'s business; what a reader wants
     * to know is which pieces of track this tile joins in any position at all. The per-state and
     * per-orientation behaviour is pinned by the tests above, which is the right division: those say
     * how the tile TURNS, this says what it IS.
     *
     * A stub - a tile with one open side, like an end, a tunnel mouth or a link - is a route whose two
     * sides are the same, so it appears here doubled: "NN".
     */
    @Test
    public void testTheWholePortTableIsWhatTheMapSaysItIs()
    {
        Map<componentType, String[]> table = new LinkedHashMap<>();

        table.put(componentType.STRAIGHT,              new String[] {"EW"});
        table.put(componentType.CURVE,                 new String[] {"ES"});
        table.put(componentType.DOUBLE_CURVE,          new String[] {"NW", "ES"});
        table.put(componentType.FEEDBACK,              new String[] {"EW"});
        table.put(componentType.FEEDBACK_CURVE,        new String[] {"ES"});
        table.put(componentType.FEEDBACK_DOUBLE_CURVE, new String[] {"NW", "ES"});
        table.put(componentType.SIGNAL,                new String[] {"EW"});
        table.put(componentType.UNCOUPLER,             new String[] {"EW"});

        // Stubs: one open side, so the route's two ends are the same side
        table.put(componentType.END,                   new String[] {"NN"});
        table.put(componentType.TUNNEL,                new String[] {"SS"});
        table.put(componentType.LINK,                  new String[] {"WW"});

        table.put(componentType.CROSSING,              new String[] {"NS", "EW"});
        table.put(componentType.OVERPASS,              new String[] {"NS", "EW"});

        // Switches: the straight road and whatever the blades offer
        table.put(componentType.SWITCH_LEFT,           new String[] {"NS", "SW"});
        table.put(componentType.SWITCH_RIGHT,          new String[] {"NS", "ES"});
        table.put(componentType.SWITCH_Y,              new String[] {"SW", "ES"});
        table.put(componentType.SWITCH_THREE,          new String[] {"NS", "SW", "ES"});
        table.put(componentType.SWITCH_CROSSING,       new String[] {"NS", "EW", "NW", "ES"});

        // Permanent ways: every road open at once, no address to throw
        table.put(componentType.CUSTOM_PERM_LEFT,      new String[] {"NS", "SW"});
        table.put(componentType.CUSTOM_PERM_RIGHT,     new String[] {"NS", "ES"});
        table.put(componentType.CUSTOM_PERM_Y,         new String[] {"SW", "ES"});
        table.put(componentType.CUSTOM_PERM_THREEWAY,  new String[] {"NS", "SW", "ES"});

        // Carries no trains: decorative, disqualified, or transparent to its neighbours
        table.put(componentType.CUSTOM_SCISSORS,       new String[] {});
        table.put(componentType.CUSTOM_PERM_SCISSORS,  new String[] {});
        table.put(componentType.TURNTABLE,             new String[] {});
        table.put(componentType.LAMP,                  new String[] {});
        table.put(componentType.ROUTE,                 new String[] {});
        table.put(componentType.TEXT,                  new String[] {});

        assertEquals(table.size(), componentType.values().length,
            "the table above names " + table.size() + " tile types and the enum has "
            + componentType.values().length + ". A type added without a line here is a type whose port "
            + "map nothing states - which is how the Python copy this replaces went stale");

        for (Map.Entry<componentType, String[]> row : table.entrySet())
        {
            Set<String> found = new HashSet<>();

            for (int state = 0; state < Math.max(1, TilePorts.getStateCount(row.getKey())); state++)
            {
                for (Route r : TilePorts.ports(row.getKey(), 0, state))
                {
                    found.add(canonical(r.getA(), r.getB()));
                }
            }

            assertEquals(found, pairs(row.getValue()), row.getKey()
                + " joins different pieces of track than the port map says it does");
        }
    }
    private Side singleStub(componentType type, int orientation)
    {
        List<Route> routes = TilePorts.ports(type, orientation, 0);

        assertEquals(routes.size(), 1, type + " should have exactly one route");
        assertEquals(routes.get(0).getA(), routes.get(0).getB(), type + " should be a stub");

        return routes.get(0).getA();
    }
}
