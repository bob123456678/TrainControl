import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.ConditionOutline;
import org.traincontrol.base.NodeAnd;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.NodeOr;
import org.traincontrol.base.RouteCommand;

/**
 * Conditions as an indented list, and what that list means.
 *
 * This replaces writing the logic as algebra beside the table. The algebra referred to the rows by
 * letters, and a letter is a handle somebody has to hold in their head while looking away from the
 * thing it names - which is where every confusion with that design came from. An outline puts the
 * shape in the list itself: indentation is nesting, and each row is its own condition.
 *
 * Two rules do all the work, and these tests are mostly about the second one:
 *
 *   - each row carries the word joining it to the row before it at its level
 *   - a run of rows joined by the same word is a group, and a change of word starts a new one
 *
 * So "A or B and C or D" is (A or B) and (C or D). That is what those words mean read left to right,
 * and it is the same thing "A and B or C" already means in every language that has both - which is
 * the point: there is no precedence rule here for anybody to learn, because there is no expression to
 * parse. The shape is on screen.
 */
public class testConditionOutline
{
    /**
     * The case the whole design exists for.
     */
    @Test
    public void testAChangeOfWordStartsANewGroup()
    {
        NodeExpression parsed = ConditionOutline.toExpression(outline(
            row(0, null, 1),
            row(0, ConditionOutline.Joiner.OR, 2),
            row(0, ConditionOutline.Joiner.AND, 3),
            row(0, ConditionOutline.Joiner.OR, 4)));

        assertTrue(parsed instanceof NodeAnd,
            "(1 or 2) and (3 or 4) - the AND is what joins the two groups, so it is the top of the "
            + "tree.  An OR here would mean the words had been read as one long chain");
    }

    /**
     * One word throughout is one group, however many rows.
     */
    @Test
    public void testOneWordThroughoutIsOneGroup()
    {
        NodeExpression parsed = ConditionOutline.toExpression(outline(
            row(0, null, 1),
            row(0, ConditionOutline.Joiner.AND, 2),
            row(0, ConditionOutline.Joiner.AND, 3)));

        assertTrue(parsed instanceof NodeAnd, "three conditions, all required");

        assertTrue(ConditionOutline.toExpression(outline(
            row(0, null, 1),
            row(0, ConditionOutline.Joiner.OR, 2))) instanceof NodeOr,
            "and two conditions, either of which will do");
    }

    /**
     * Indenting nests a row and the ones indented with it.
     */
    @Test
    public void testIndentingNests()
    {
        NodeExpression parsed = ConditionOutline.toExpression(outline(
            row(0, null, 1),
            row(1, ConditionOutline.Joiner.AND, 2),
            row(1, ConditionOutline.Joiner.OR, 3)));

        assertTrue(parsed instanceof NodeAnd,
            "1 and (2 or 3) - the indented pair is one thing, joined to the first row by the word on "
            + "the first indented row");

        NodeAnd whole = (NodeAnd) parsed;

        assertNotNull(whole.getRight(), "the indented pair is the right-hand side");
    }

    /**
     * A route with no conditions has no expression, which is not the same as an empty one.
     */
    @Test
    public void testNothingIsNoCondition()
    {
        assertNull(ConditionOutline.toExpression(new ArrayList<ConditionOutline.Row>()),
            "no rows is a route that fires whenever it is triggered");

        assertNull(ConditionOutline.toExpression(null), "and so is nothing at all");
    }

    /**
     * A single condition is itself, with no wrapping.
     */
    @Test
    public void testOneRowIsOneCondition()
    {
        NodeExpression parsed = ConditionOutline.toExpression(outline(row(0, null, 1)));

        assertNotNull(parsed, "one row is one condition");

        assertFalse(parsed instanceof NodeAnd, "and nothing is joined to it");
        assertFalse(parsed instanceof NodeOr, "nor that");
    }

    /**
     * An existing condition opens as an outline, and that outline means the same thing.
     *
     * The half that matters for a railway that already exists. Routes were written before this and in
     * the older text editor, and opening one has to show the condition it has - saving it unchanged
     * must not alter when it fires.
     */
    @Test
    public void testAnExistingConditionSurvivesBeingShownAsAnOutline()
    {
        NodeExpression original = ConditionOutline.toExpression(outline(
            row(0, null, 1),
            row(0, ConditionOutline.Joiner.OR, 2),
            row(0, ConditionOutline.Joiner.AND, 3),
            row(0, ConditionOutline.Joiner.OR, 4)));

        List<ConditionOutline.Row> shown = ConditionOutline.of(original);

        assertEquals(shown.size(), 4, "four conditions go in and four come out");

        NodeExpression again = ConditionOutline.toExpression(shown);

        assertEquals(describe(again), describe(original),
            "the outline has to mean what the condition meant, or opening a route and saving it "
            + "unchanged moves when it fires");
    }

    /**
     * And a flat chain of ANDs round-trips too, which is what most conditions are.
     */
    @Test
    public void testTheOrdinaryCaseRoundTrips()
    {
        NodeExpression original = ConditionOutline.toExpression(outline(
            row(0, null, 1),
            row(0, ConditionOutline.Joiner.AND, 2),
            row(0, ConditionOutline.Joiner.AND, 3)));

        List<ConditionOutline.Row> shown = ConditionOutline.of(original);

        assertEquals(shown.size(), 3, "three in, three out");

        for (ConditionOutline.Row row : shown)
        {
            assertEquals(row.getDepth(), 0,
                "nothing was bracketed, so nothing should come back indented - an outline that "
                + "grows an indent every time it is opened would walk off the side of the window");
        }

        assertEquals(describe(ConditionOutline.toExpression(shown)), describe(original),
            "and it still means the same");
    }

    /**
     * The shape of an expression, for comparing two of them.
     */
    private static String describe(NodeExpression node)
    {
        if (node == null) return "-";

        if (node instanceof NodeAnd)
        {
            return "and(" + describe(((NodeAnd) node).getLeft()) + ","
                + describe(((NodeAnd) node).getRight()) + ")";
        }

        if (node instanceof NodeOr)
        {
            return "or(" + describe(((NodeOr) node).getLeft()) + ","
                + describe(((NodeOr) node).getRight()) + ")";
        }

        if (node instanceof org.traincontrol.base.NodeGroup)
        {
            StringBuilder out = new StringBuilder("(");

            for (NodeExpression inside
                : ((org.traincontrol.base.NodeGroup) node).getExpressions())
            {
                out.append(describe(inside));
            }

            return out.append(")").toString();
        }

        return String.valueOf(
            ((org.traincontrol.base.NodeRouteCommand) node).getRouteCommand().getAddress());
    }

    private static List<ConditionOutline.Row> outline(ConditionOutline.Row... rows)
    {
        List<ConditionOutline.Row> out = new ArrayList<>();

        for (ConditionOutline.Row row : rows) out.add(row);

        return out;
    }

    /**
     * One row: a sensor condition, so the rows can be told apart by address.
     */
    private static ConditionOutline.Row row(int depth, ConditionOutline.Joiner joiner, int sensor)
    {
        return new ConditionOutline.Row(depth, joiner, RouteCommand.RouteCommandFeedback(sensor, true));
    }
}
