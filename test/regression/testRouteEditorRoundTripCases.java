package regression;

import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.ThreeWaySwitch;

/**
 * The two cases tested rather than run by hand, MT-003 and MT-004: a route surviving the trip through the editor
 * (rendered to text on the way in, parsed back on the way out) in two shapes that got lost on that trip before.
 * `testRouteRoundTrip` covers the trip in general; these two shapes are not in it.
 *
 * @author Adam
 */
public class testRouteEditorRoundTripCases
{
    // MT-003

    /**
     * A condition that BEGINS with a bracket comes back whole (MT-003): `(A or B) and C` used to come back as `A or B`,
     * silently, so the route still loaded and ran and did half of what it said. A bracket in the middle survived, which
     * is why this went unseen.
     */
    @Test
    public void testAConditionStartingWithABracketSurvives() throws Exception
    {
        // Built from objects and rendered, not hand-written: the text form is the editor's business, and inventing it
        // here would pin a spelling rather than its behavior.
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

        // And back out again, the half that runs when the editor saves
        String again = NodeExpression.toTextRepresentation(parsed, null);

        NodeExpression reparsed = NodeExpression.fromTextRepresentation(again, null);

        assertNotNull(reparsed, "what the editor writes back does not parse: " + again);

        assertEquals(NodeExpression.toList(reparsed).size(), parts.size(),
            "the condition lost a term on the way back out. Wrote: " + again);
    }

    /**
     * And the same condition with the bracket in the MIDDLE, which always worked: here so that a failure above can be
     * read (both fail, the parser is broken generally; only the one above fails, it is the leading bracket).
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

    /** One feedback condition, as the editor would build it. */
    private NodeExpression feedback(int address, boolean occupied)
    {
        return new org.traincontrol.base.NodeRouteCommand(
            RouteCommand.RouteCommandFeedback(address, occupied));
    }

    // MT-004

    /**
     * All three positions of a three-way produce a pair, and the three pairs differ (MT-004): a three-way is two
     * accessories the editor draws as one row, so each position is a PAIR of commands, and two identical pairs would
     * send the point to the wrong road.
     *
     * What this cannot test: Adam's "enable the echo packets option ... and then see if the accessory status is
     * correctly set, and the icon matches" is a round trip through a Central Station, and there is none on this
     * machine. So this pins that the three positions are three distinct instructions, each an ordered pair; whether the
     * station echoes them back stays a hands-on check, and MT-004 stays open for it.
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
     * And each position's pair really does round-trip as a route, in order. The order is the instruction: the release
     * has to precede the throw, or the point is asked to be in two states at once. The text is parsed back through
     * `RouteCommand.fromLine` and the order read off the PARSED commands, which is what a route executes, not off the
     * list they were rendered from (that comparison was order-invariant by construction and could not fail).
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

            // And the first command out of the route is a RELEASE, asked of the parsed commands. Only the first:
            // STRAIGHT releases BOTH motors (expand's own rule is `position != Position.STRAIGHT` for the second
            // command), so requiring a throw there asks for a road that does not exist; the rule is that nothing is
            // thrown before the other has been released.
            assertFalse(back.get(0).getSetting(),
                position + ": the first command out of the route is a THROW. Both motors of a "
                + "three-way are then driven over at once, which routes nowhere and which some "
                + "mechanisms bind in (TD-6)");

            assertEquals(back.get(1).getSetting(), position != ThreeWaySwitch.Position.STRAIGHT,
                position + ": the second command sets the wrong way. Every position but STRAIGHT is "
                + "chosen by throwing one motor; STRAIGHT is the one where both are released");

            // The release waits before the throw follows; without the delay the two commands go out together.
            assertTrue(back.get(0).getDelay() > 0,
                position + ": the release carries no delay, so the throw follows it immediately");
        }
    }
}
