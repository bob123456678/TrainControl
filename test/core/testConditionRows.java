package core;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.ConditionRows;
import org.traincontrol.base.NodeAnd;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.NodeGroup;
import org.traincontrol.base.NodeOr;
import org.traincontrol.base.NodeRouteCommand;
import org.traincontrol.base.RouteCommand;

/**
 * The row view of a condition expression, and the promise that it does not change what a route means.
 *
 * This is the model behind the new route editor's condition list.  Everything here is about one
 * question: can a boolean tree be shown as a flat list of rows and put back together unchanged?  For
 * the shapes normalize produces the answer is yes, and for a real bracket the answer must be "no, keep
 * the text editor" rather than a flattened approximation - because a flattened approximation would
 * quietly rewrite somebody's route the first time they pressed Save.
 */
public class testConditionRows
{
    /**
     * A single condition is one row with no joiner.
     */
    @Test
    public void testASingleTermIsOneRow()
    {
        NodeExpression one = new NodeRouteCommand(feedback(21, true));

        List<ConditionRows.Row> rows = ConditionRows.of(one);

        assertNotNull(rows, "a single term must be showable as rows");
        assertEquals(rows.size(), 1);
        assertNull(rows.get(0).getJoiner(), "the last row joins to nothing");
    }

    /**
     * No conditions is no rows, not a failure.
     */
    @Test
    public void testNoConditionsIsAnEmptyList()
    {
        List<ConditionRows.Row> rows = ConditionRows.of(null);

        assertNotNull(rows, "no conditions is a thing rows CAN express - an empty list");
        assertTrue(rows.isEmpty());

        assertNull(ConditionRows.toExpression(rows), "and it goes back to no expression");
    }

    /**
     * A chain of ANDs becomes a chain of rows, and rebuilds identically.
     */
    @Test
    public void testAChainOfAndsRoundTrips()
    {
        NodeExpression original = NodeExpression.normalize(
            new NodeAnd(new NodeRouteCommand(feedback(1, true)),
                new NodeAnd(new NodeRouteCommand(feedback(2, false)),
                    new NodeRouteCommand(feedback(3, true)))));

        List<ConditionRows.Row> rows = ConditionRows.of(original);

        assertNotNull(rows);
        assertEquals(rows.size(), 3);
        assertEquals(rows.get(0).getJoiner(), ConditionRows.Joiner.AND);
        assertEquals(rows.get(1).getJoiner(), ConditionRows.Joiner.AND);
        assertNull(rows.get(2).getJoiner());

        assertEquals(describe(ConditionRows.toExpression(rows)), describe(original),
            "the rebuilt expression is not the one taken apart");
    }

    /**
     * Mixed operators keep their meaning, which is where a careless row list would go wrong.
     *
     * The list is RIGHT-nested: "a AND b OR c" means AND(a, OR(b, c)), and reading it left to right
     * like arithmetic would give OR(AND(a, b), c) - a different railway.  This is the test that says
     * which one the editor means.
     */
    @Test
    public void testMixedOperatorsKeepTheirNesting()
    {
        NodeExpression original = new NodeAnd(new NodeRouteCommand(feedback(1, true)),
            new NodeOr(new NodeRouteCommand(feedback(2, true)),
                new NodeRouteCommand(feedback(3, true))));

        List<ConditionRows.Row> rows = ConditionRows.of(original);

        assertNotNull(rows, "a right-nested mix is exactly what rows are for");
        assertEquals(rows.get(0).getJoiner(), ConditionRows.Joiner.AND);
        assertEquals(rows.get(1).getJoiner(), ConditionRows.Joiner.OR);

        NodeExpression rebuilt = ConditionRows.toExpression(rows);

        assertTrue(rebuilt instanceof NodeAnd,
            "the outermost operator must still be the AND - if this is an OR the rows were read left "
            + "to right, and every mixed condition in every route now means something else");

        assertTrue(((NodeAnd) rebuilt).getRight() instanceof NodeOr,
            "and the OR must still be the inner one");

        assertEquals(describe(rebuilt), describe(original));
    }

    /**
     * A real bracket is refused, rather than flattened into something that nearly means the same.
     *
     * This is the promise that makes the whole thing safe: an expression the rows cannot say is left to
     * the text editor.  Flattening it would change what the route does, silently, on the next Save.
     */
    @Test
    public void testABracketIsRefusedRatherThanFlattened()
    {
        NodeExpression bracketed = new NodeAnd(
            new NodeGroup(Arrays.asList((NodeExpression) new NodeOr(
                new NodeRouteCommand(feedback(1, true)),
                new NodeRouteCommand(feedback(2, true))))),
            new NodeRouteCommand(feedback(3, true)));

        assertNull(ConditionRows.of(bracketed),
            "a bracket says something rows cannot, so the editor must keep the text field for it "
            + "rather than show a flattened version that means something else");
    }

    /**
     * A missing joiner means AND, which is what a list of conditions means when nobody has said.
     */
    @Test
    public void testARowWithNoJoinerMeansAnd()
    {
        List<ConditionRows.Row> rows = new LinkedList<>();

        rows.add(new ConditionRows.Row(null, feedback(1, true)));
        rows.add(new ConditionRows.Row(null, feedback(2, true)));

        NodeExpression built = ConditionRows.toExpression(rows);

        assertTrue(built instanceof NodeAnd, "two conditions with nothing said between them are ANDed");
    }

    private static RouteCommand feedback(int address, boolean state)
    {
        return RouteCommand.RouteCommandFeedback(address, state);
    }

    /**
     * A structural rendering, so two trees can be compared without relying on equals.
     */
    private static String describe(NodeExpression node)
    {
        if (node == null) return "-";

        if (node instanceof NodeRouteCommand)
        {
            return String.valueOf(((NodeRouteCommand) node).getRouteCommand());
        }

        if (node instanceof NodeAnd)
        {
            return "AND(" + describe(((NodeAnd) node).getLeft()) + ", "
                + describe(((NodeAnd) node).getRight()) + ")";
        }

        if (node instanceof NodeOr)
        {
            return "OR(" + describe(((NodeOr) node).getLeft()) + ", "
                + describe(((NodeOr) node).getRight()) + ")";
        }

        StringBuilder out = new StringBuilder("GROUP(");

        for (NodeExpression child : ((NodeGroup) node).getExpressions())
        {
            out.append(describe(child)).append(' ');
        }

        return out.append(')').toString();
    }

    /**
     * A condition chain that leans the other way is still a row list.
     *
     * The importer and the text parser disagree about nesting: NodeExpression.fromList builds
     * LEFT-nested, and that is what MarklinRoute.addConditionS88 uses, so every route imported from the
     * Central Station with three or more conditions arrives as AND(AND(a, b), c).  Refusing those made
     * the condition editor unavailable for the most ordinary condition a route has.  AND is
     * associative, so the two lean the same railway.
     */
    @Test
    public void testALeftNestedChainIsAccepted()
    {
        NodeExpression left = new NodeAnd(
            new NodeAnd(new NodeRouteCommand(RouteCommand.RouteCommandFeedback(21, true)),
                        new NodeRouteCommand(RouteCommand.RouteCommandFeedback(22, true))),
            new NodeRouteCommand(RouteCommand.RouteCommandFeedback(23, true)));

        List<ConditionRows.Row> rows = ConditionRows.of(left);

        assertNotNull(rows,
            "a left-nested AND chain was refused, which is every Central Station route with three "
            + "conditions in it");

        assertEquals(rows.size(), 3);

        assertEquals(rows.get(0).getJoiner(), ConditionRows.Joiner.AND);
        assertEquals(rows.get(1).getJoiner(), ConditionRows.Joiner.AND);
        assertNull(rows.get(2).getJoiner(), "the last row joins to nothing after it");

        // And the rebuild says the same thing, leaning the way rows lean
        NodeExpression rebuilt = ConditionRows.toExpression(rows);

        NodeExpression sameThingLeaningRight = new NodeAnd(
            new NodeRouteCommand(RouteCommand.RouteCommandFeedback(21, true)),
            new NodeAnd(new NodeRouteCommand(RouteCommand.RouteCommandFeedback(22, true)),
                        new NodeRouteCommand(RouteCommand.RouteCommandFeedback(23, true))));

        assertEquals(describe(rebuilt), describe(sameThingLeaningRight),
            "the rebuild has to mean what the original meant - AND is associative, so it may lean the "
            + "other way, but it may not regroup");
    }

    /**
     * A left-leaning chain of MIXED operators is still refused.
     *
     * The line the fix above must not cross.  OR(AND(a, b), c) is a bracket: it is not AND(a, OR(b,
     * c)), and no list of rows says it.  Flattening it would change what the route does.
     */
    @Test
    public void testALeftNestedMixedChainIsStillRefused()
    {
        NodeExpression mixed = new NodeOr(
            new NodeAnd(new NodeRouteCommand(RouteCommand.RouteCommandFeedback(21, true)),
                        new NodeRouteCommand(RouteCommand.RouteCommandFeedback(22, true))),
            new NodeRouteCommand(RouteCommand.RouteCommandFeedback(23, true)));

        assertNull(ConditionRows.of(mixed),
            "OR(AND(a, b), c) was flattened into rows, which read as AND(a, OR(b, c)) - a different "
            + "condition, and the route would change the next time anybody pressed Save");
    }
}
