package regression;

import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.ThreeWaySwitch;

/**
 * The two cases Adam asked to have tested rather than run by hand: MT-003 and MT-004.
 *
 * Both are about a route surviving the trip through the editor - rendered to text on the way in,
 * parsed back on the way out - and both name a specific shape that got lost on that trip before.
 *
 * `testRouteRoundTrip` already covers the trip in general, including three-ways as a pair and the
 * feedback command that used to swallow its neighbour. What it does not cover is the two shapes below,
 * which is why these are here rather than added to it.
 *
 * @author Adam
 */
public class testRouteEditorRoundTripCases
{
    // ---- MT-003 ---------------------------------------------------------------------------------

    /**
     * A condition that BEGINS with a bracket comes back whole.
     *
     * MT-003 names the failure exactly: "a condition beginning with a bracket - `(A or B) and C` - used
     * to come back as `A or B`, silently". Silently is the part that makes it worth a test: the route
     * still loaded, still ran, and did half of what it said - so the only way to notice was to read
     * the reads-as line and know what it should have said.
     *
     * The leading bracket is the whole point. A condition with the bracket in the middle survived,
     * which is why this went unseen.
     */
    @Test
    public void testAConditionStartingWithABracketSurvives() throws Exception
    {
        // Built from objects and rendered, rather than hand-written: the text form is the editor's
        // business, and inventing it in a test would pin my spelling rather than its behaviour.
        NodeExpression bracketed = new org.traincontrol.base.NodeGroup(java.util.Collections.<NodeExpression>singletonList(
            new org.traincontrol.base.NodeOr(feedback(1, true), feedback(2, true))));

        NodeExpression whole = new org.traincontrol.base.NodeAnd(bracketed, feedback(3, false));

        String text = NodeExpression.toTextRepresentation(whole, null);

        assertTrue(text.trim().startsWith("("),
            "this test is about a condition that BEGINS with a bracket, and the rendered form does "
            + "not: " + text);

        NodeExpression parsed = NodeExpression.fromTextRepresentation(text, null);

        assertNotNull(parsed, "the condition did not parse at all: " + text);

        List<RouteCommand> parts = NodeExpression.toList(parsed);

        assertEquals(parts.size(), 3,
            "a bracketed pair AND a third term is three conditions - getting fewer is the MT-003 "
            + "defect, where everything after the closing bracket was dropped. Got: " + parts);

        // And back out again, which is the half that runs when the editor saves
        String again = NodeExpression.toTextRepresentation(parsed, null);

        NodeExpression reparsed = NodeExpression.fromTextRepresentation(again, null);

        assertNotNull(reparsed, "what the editor writes back does not parse: " + again);

        assertEquals(NodeExpression.toList(reparsed).size(), parts.size(),
            "the condition lost a term on the way back out. Wrote: " + again);
    }

    /**
     * And the same condition with the bracket in the MIDDLE, which always worked.
     *
     * Here so that a failure above can be read: if both fail, the parser is broken generally; if only
     * the one above fails, it is the leading bracket specifically, which is what MT-003 is about.
     */
    @Test
    public void testABracketInTheMiddleAlsoSurvives() throws Exception
    {
        NodeExpression bracketed = new org.traincontrol.base.NodeGroup(java.util.Collections.<NodeExpression>singletonList(
            new org.traincontrol.base.NodeOr(feedback(1, true), feedback(2, true))));

        NodeExpression whole = new org.traincontrol.base.NodeAnd(feedback(3, false), bracketed);

        String text = NodeExpression.toTextRepresentation(whole, null);

        NodeExpression parsed = NodeExpression.fromTextRepresentation(text, null);

        assertNotNull(parsed, "the condition did not parse: " + text);

        assertEquals(NodeExpression.toList(parsed).size(), 3, "three conditions: " + text);
    }

    /**
     * One feedback condition, as the editor would build it.
     */
    private NodeExpression feedback(int address, boolean occupied)
    {
        return new org.traincontrol.base.NodeRouteCommand(
            RouteCommand.RouteCommandFeedback(address, occupied));
    }

    // ---- MT-004 ---------------------------------------------------------------------------------

    /**
     * All three positions of a three-way produce a pair, and the three pairs differ.
     *
     * Adam asked for "all 3 possible directions". A three-way is two accessories that the editor draws
     * as one row, so each position is a PAIR of commands - and the three pairs have to be genuinely
     * different, or two of the positions are the same instruction under different names and the point
     * would go to the wrong road.
     *
     * **What this cannot test, and it is the half Adam described.** He asked to "enable the echo
     * packets option in the main class, and then see if the accessory status is correctly set, and the
     * icon matches". That is a round trip through a Central Station: commands out, echo back, model
     * updated, icon redrawn. There is no station on this machine, and the one test that talks to real
     * hardware - testAutoDetect - is excluded from the battery for exactly that reason.
     *
     * So this pins what can be pinned without hardware: that the three positions are three distinct
     * instructions, and that each is expressed as an ordered pair. Whether the station echoes them back
     * correctly stays a hands-on check, and MT-004 stays open for it.
     */
    @Test
    public void testTheThreePositionsAreThreeDifferentInstructions()
    {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();

        for (ThreeWaySwitch.Position position : ThreeWaySwitch.Position.values())
        {
            List<RouteCommand> pair = ThreeWaySwitch.expand(5,
                Accessory.accessoryDecoderType.MM2, position, ThreeWaySwitch.SETTLE);

            assertEquals(pair.size(), 2,
                position + " is not a pair of commands - a three-way is two accessories, and a "
                + "position that expands to anything else cannot be sent");

            seen.add(pair.get(0).toLine(null).trim() + " | " + pair.get(1).toLine(null).trim());
        }

        assertEquals(seen.size(), ThreeWaySwitch.Position.values().length,
            "two positions of the three-way expand to the same commands, so one of them sends a "
            + "train to the wrong road. Got: " + seen);
    }

    /**
     * And each position's pair really does round-trip as a route, in order.
     *
     * The order is the instruction: the release has to precede the throw, or the point is asked to be
     * in two states at once and settles wherever the hardware happens to finish. That is real ironwork,
     * two motors on consecutive addresses.
     *
     * **This test used to be unable to fail** (TD-6). It built the route text from the same list, in
     * the same index order, that it read `first` and `second` from - so if `expand` returned its pair
     * the wrong way round, all three swapped together and the comparison came out the same. It was
     * order-invariant by construction, and could not fail for the reason its own message gave, while
     * its javadoc promised a round trip nothing performed: the text was never parsed back.
     *
     * It parses back now, through `RouteCommand.fromLine`, which is the direction nothing else here
     * covered. The order is then read off the PARSED commands, which is what a route actually executes,
     * rather than off the list they were rendered from.
     */
    @Test
    public void testEachPositionKeepsItsOrderThroughTheRouteText() throws Exception
    {
        for (ThreeWaySwitch.Position position : ThreeWaySwitch.Position.values())
        {
            List<RouteCommand> pair = ThreeWaySwitch.expand(5,
                Accessory.accessoryDecoderType.MM2, position, ThreeWaySwitch.SETTLE);

            String asARoute = pair.get(0).toLine(null) + pair.get(1).toLine(null);

            assertTrue(asARoute.trim().contains("\n"),
                position + ": the pair ran together onto one line, which is the shape that made a "
                + "feedback command swallow its neighbour - see testRouteRoundTrip");

            List<RouteCommand> back = new java.util.ArrayList<>();

            for (String line : asARoute.split("\n"))
            {
                if (line.trim().isEmpty()) continue;

                back.add(RouteCommand.fromLine(line.trim(), false));
            }

            assertEquals(back.size(), 2,
                position + ": the route text did not read back as two commands, so what the route "
                + "would execute is not the pair that was rendered");

            assertEquals(back.get(0).toLine(null).trim(), pair.get(0).toLine(null).trim(),
                position + ": the FIRST command to come back out of the route text is not the one "
                + "that went in first. The release has to precede the throw, or the point is asked to "
                + "be in two states at once and settles wherever the hardware finishes (TD-6)");

            assertEquals(back.get(1).toLine(null).trim(), pair.get(1).toLine(null).trim(),
                position + ": the SECOND command came back changed");

            // And the first command out of the route is a RELEASE, asked of the parsed commands rather
            // than of the list they were written from - which is the half the old assertion left out.
            //
            // Only the first. STRAIGHT releases BOTH motors, since it is the position neither of them
            // chooses - expand's own rule is `position != Position.STRAIGHT` for the second command -
            // so requiring a throw there asks for a road that does not exist. The rule being guarded is
            // "nothing is thrown before the other has been released", and that is a statement about
            // the first.
            assertFalse(back.get(0).getSetting(),
                position + ": the first command out of the route is a THROW. Both motors of a "
                + "three-way are then driven over at once, which routes nowhere and which some "
                + "mechanisms bind in (TD-6)");

            assertEquals(back.get(1).getSetting(), position != ThreeWaySwitch.Position.STRAIGHT,
                position + ": the second command sets the wrong way. Every position but STRAIGHT is "
                + "chosen by throwing one motor; STRAIGHT is the one where both are released");

            // The release waits before the throw follows.  Without the delay the two commands go out
            // together and the point is asked to be in two states at once.
            assertTrue(back.get(0).getDelay() > 0,
                position + ": the release carries no delay, so the throw follows it immediately");
        }
    }
}
