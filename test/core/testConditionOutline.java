package core;

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
 * Conditions as an indented list, where the joining words are indented too.
 *
 * The words being indentable is the whole of what makes this work, and it took a wrong turn to find.
 * A first version kept the word beside its condition and decided grouping by "a run of the same word
 * is a group" - which reads left to right and is defensible, and which nobody could see on screen. A
 * word that can be indented says which level it joins at, and then the shape IS the meaning:
 *
 *   Sensor 1
 *   and
 *       Sensor 2
 *       or
 *       Sensor 3
 *
 * is 1 and (2 or 3), because the "or" sits at the level of the two conditions it joins and the "and"
 * sits at the level of sensor 1 and the group.
 *
 * From that follows the one rule: every word at a level must be the same word. "and" and "or" side by
 * side at one level is a sentence with two meanings, and the answer is not to pick one quietly but to
 * say so - which is what problems() reports and the editor draws in red.
 */
public class testConditionOutline
{
    /**
     * An indented word joins the indented things, and the outer word joins what is left.
     */
    @Test
    public void testAnIndentedWordJoinsTheIndentedThings()
    {
        NodeExpression parsed = ConditionOutline.toExpression(outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.AND),
            condition(1, 2),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 3)));

        assertTrue(parsed instanceof NodeAnd,
            "1 and (2 or 3) - the AND is at the outer level, so it is the top of the tree.  An OR "
            + "here would mean the indentation had been ignored");

        assertTrue(((NodeAnd) parsed).getRight() instanceof org.traincontrol.base.NodeGroup,
            "and the indented pair is one thing, not two");
    }

    /**
     * The same conditions with the words at one level mean the other thing.
     */
    @Test
    public void testTheOuterWordIsTheTopOfTheTree()
    {
        NodeExpression parsed = ConditionOutline.toExpression(outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.OR),
            condition(0, 2),
            joining(0, ConditionOutline.Joiner.OR),
            condition(0, 3)));

        assertTrue(parsed instanceof NodeOr, "all three at one level, joined by one word");
    }

    /**
     * Two different words at one level is refused, and the line that disagrees is named.
     *
     * The case the red is for. Left to itself it is a sentence with two meanings, and choosing one
     * quietly is how a route ends up firing at a time nobody asked for.
     */
    @Test
    public void testMixedWordsAtOneLevelAreFlagged()
    {
        List<ConditionOutline.Row> rows = outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.AND),
            condition(0, 2),
            joining(0, ConditionOutline.Joiner.OR),
            condition(0, 3));

        java.util.Set<Integer> flagged = ConditionOutline.problems(rows);

        assertEquals(flagged.size(), 1, "one line disagrees with its level: " + flagged);

        assertTrue(flagged.contains(3),
            "and it is the OR, because the level was already settled as AND by the line above it - "
            + "flagging the first word instead would move the mark as more lines were added");
    }

    /**
     * Indenting the part that was meant to group settles it.
     */
    @Test
    public void testIndentingResolvesTheDisagreement()
    {
        assertTrue(ConditionOutline.problems(outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.AND),
            condition(1, 2),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 3))).isEmpty(),
            "one AND at the outer level and one OR at the inner one - each level agrees with itself, "
            + "which is the whole rule");
    }

    /**
     * A level's words may repeat as often as they like, so long as they agree.
     */
    @Test
    public void testOneWordManyTimesIsFine()
    {
        assertTrue(ConditionOutline.problems(outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.AND),
            condition(0, 2),
            joining(0, ConditionOutline.Joiner.AND),
            condition(0, 3))).isEmpty(), "three conditions, all required");
    }

    /**
     * And the same word at different levels is not a disagreement.
     */
    @Test
    public void testLevelsAreJudgedSeparately()
    {
        assertTrue(ConditionOutline.problems(outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.OR),
            condition(1, 2),
            joining(1, ConditionOutline.Joiner.AND),
            condition(1, 3))).isEmpty(),
            "an OR outside and an AND inside is exactly what indenting is for - judging them together "
            + "would make the feature useless");
    }

    /**
     * Nothing in the list is a route with no conditions.
     */
    @Test
    public void testNothingIsNoCondition()
    {
        assertNull(ConditionOutline.toExpression(new ArrayList<ConditionOutline.Row>()),
            "no lines is a route that fires whenever it is triggered");

        assertNull(ConditionOutline.toExpression(null), "and so is nothing at all");
    }

    /**
     * An existing condition opens as an outline that means the same thing.
     *
     * The half that matters for a railway that already exists: opening a route and saving it
     * unchanged must not move when it fires.
     */
    @Test
    public void testAnExistingConditionSurvivesBeingShownAsAnOutline()
    {
        NodeExpression original = ConditionOutline.toExpression(outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.AND),
            condition(1, 2),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 3)));

        List<ConditionOutline.Row> shown = ConditionOutline.of(original);

        assertTrue(ConditionOutline.problems(shown).isEmpty(),
            "a condition that came from a valid outline must not come back flagged");

        assertEquals(describe(ConditionOutline.toExpression(shown)), describe(original),
            "and it has to mean the same, or opening a route and saving it moves when it fires");
    }

    /**
     * A condition built as a TREE, rather than from an outline, keeps its shape.
     *
     * Everything above starts from an outline, so a nesting the outline could not express could not be
     * reached by these tests at all - and that is exactly what the older editor's Insert AND and
     * Insert OR buttons produced, and what a hand-written condition in a route file can hold.
     *
     * "A and (B or C)" was written out as one flat level reading "A and B or C": two different joining
     * words at the same depth, which this class's own rule refuses.  So the route opened with its
     * condition flagged red and could not be saved until the user restructured something they never
     * wrote - and the reading, which the Test button evaluates, said "(A and B) or C" instead.
     */
    @Test
    public void testATreeThatMixesWordsComesBackNested()
    {
        NodeExpression original = new NodeAnd(sensor(1),
            new NodeOr(sensor(2), sensor(3)));

        List<ConditionOutline.Row> shown = ConditionOutline.of(original);

        assertTrue(ConditionOutline.problems(shown).isEmpty(),
            "the outline disagrees with itself: " + ConditionOutline.problems(shown)
            + " - a route with this condition cannot be saved until it is restructured by hand");

        assertEquals(meaning(ConditionOutline.toExpression(shown)), meaning(original),
            "the condition means something else than it did, which is when it fires on the railway");
    }

    /**
     * And the same with the mixed pair on the LEFT.
     */
    @Test
    public void testATreeWithTheGroupFirstComesBackNested()
    {
        NodeExpression original = new NodeOr(new NodeAnd(sensor(1), sensor(2)), sensor(3));

        List<ConditionOutline.Row> shown = ConditionOutline.of(original);

        assertTrue(ConditionOutline.problems(shown).isEmpty(),
            "the outline disagrees with itself: " + ConditionOutline.problems(shown));

        assertEquals(meaning(ConditionOutline.toExpression(shown)), meaning(original),
            "(A and B) or C came back meaning something else");
    }

    /**
     * A flat chain of ANDs round-trips without growing an indent.
     *
     * An outline that indented itself a little more every time it was opened would walk off the side
     * of the window after a few visits, and nothing else would ever say so.
     */
    @Test
    public void testTheOrdinaryCaseDoesNotDrift()
    {
        NodeExpression original = ConditionOutline.toExpression(outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.AND),
            condition(0, 2),
            joining(0, ConditionOutline.Joiner.AND),
            condition(0, 3)));

        List<ConditionOutline.Row> shown = ConditionOutline.of(original);

        for (ConditionOutline.Row row : shown)
        {
            assertEquals(row.getDepth(), 0, "nothing was grouped, so nothing comes back indented");
        }

        assertEquals(describe(ConditionOutline.toExpression(shown)), describe(original),
            "and it still means the same");
    }

    /**
     * Nesting goes as deep as anybody wants, and comes back out the same.
     *
     * Adam asked whether three levels were possible, having found that the editor seemed to stop at
     * two. The model never had a limit - the interface did, by hiding the indent mark once a line was
     * as deep as the line above allowed, which reads as the feature ending rather than as that line
     * being at its limit.
     *
     * Four levels here rather than three, because a limit at three would pass a test for three.
     */
    @Test
    public void testNestingGoesAsDeepAsItIsAsked()
    {
        List<ConditionOutline.Row> deep = outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.AND),
            condition(1, 2),
            joining(1, ConditionOutline.Joiner.OR),
            condition(2, 3),
            joining(2, ConditionOutline.Joiner.AND),
            condition(3, 4),
            joining(3, ConditionOutline.Joiner.OR),
            condition(3, 5));

        assertTrue(ConditionOutline.problems(deep).isEmpty(),
            "every level agrees with itself, so nothing should be flagged");

        NodeExpression parsed = ConditionOutline.toExpression(deep);

        assertNotNull(parsed, "four levels deep is still a condition");

        List<ConditionOutline.Row> shown = ConditionOutline.of(parsed);

        int deepest = 0;

        for (ConditionOutline.Row row : shown) deepest = Math.max(deepest, row.getDepth());

        assertTrue(deepest >= 3,
            "the nesting has to survive being written back out - a round trip that flattened it would "
            + "quietly rewrite somebody's condition into a different one.  Deepest was " + deepest);

        assertEquals(describe(ConditionOutline.toExpression(shown)), describe(parsed),
            "and mean the same thing after the trip");
    }

    /**
     * Moving a condition past its neighbour leaves the joining word where it was.
     *
     * Adam's: three lines reading "A and B", with B moved up, came back as "A B" - the AND gone and
     * the route now firing on a condition nobody changed.  Moving the LINE put B beside the word
     * instead of past it, and a word with nothing on one side is not a sentence, so the tidy that
     * runs after every move swept the word away.  The arrow said nothing about when the route fires;
     * it changed it anyway, and quietly.
     */
    @Test
    public void testMovingAConditionKeepsTheWordThatJoinsIt()
    {
        List<ConditionOutline.Row> rows = outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.AND),
            condition(0, 2));

        List<ConditionOutline.Row> moved = ConditionOutline.moved(rows, 2, -1);

        assertEquals(moved.size(), 3, "the word is still there: " + describe(
            ConditionOutline.toExpression(moved)));

        assertTrue(moved.get(1).isJoiner(), "and it is still in the middle");

        assertEquals(describe(ConditionOutline.toExpression(moved)), "and(2,1)",
            "the two conditions traded places and nothing else moved");
    }

    /**
     * The nesting is the logic, so moving a condition must not deform it.
     *
     * "1 and (2 or 3)" with 2 moved up is "2 and (1 or 3)" - the same sentence about different
     * sensors.  Lifting the line itself would have pulled a condition out of the group it was
     * nested in, which is a change to the logic rather than to the order.
     */
    @Test
    public void testMovingAConditionDoesNotDeformTheNesting()
    {
        List<ConditionOutline.Row> rows = outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.AND),
            condition(1, 2),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 3));

        List<ConditionOutline.Row> moved = ConditionOutline.moved(rows, 2, -1);

        assertEquals(describe(ConditionOutline.toExpression(moved)), "and(2,(or(1,3)))",
            "the shape is untouched; only which condition sits where in it changed");
    }

    /**
     * A joining word is not moved by hand, and neither is a condition with nowhere to go.
     */
    @Test
    public void testThereIsNothingToMoveAtTheEnds()
    {
        List<ConditionOutline.Row> rows = outline(
            condition(0, 1),
            joining(0, ConditionOutline.Joiner.AND),
            condition(0, 2));

        assertFalse(ConditionOutline.canMove(rows, 0, -1), "nothing above the first condition");
        assertFalse(ConditionOutline.canMove(rows, 2, 1), "nor below the last");
        assertFalse(ConditionOutline.canMove(rows, 1, -1),
            "and a word's place is settled by the conditions around it, not by an arrow");

        assertTrue(ConditionOutline.canMove(rows, 2, -1), "the last condition can come up");
    }

    /**
     * A condition whose FIRST line is indented keeps everything after the group.
     *
     * "(A or B) and C" is an ordinary thing to write in the old editor - it has a button that wraps
     * the selection in brackets - and it comes back out of ConditionOutline.of as an outline whose
     * first row is one level in, because the group is the first thing in the sentence.
     *
     * Reading it started at the depth of the FIRST row and stopped at the first row shallower than
     * that, which is the "and" - so the whole of "and C" was dropped.  Nothing said so: the reading
     * under the table showed the truncated version, no level disagreed with itself so nothing was
     * flagged, and pressing Save wrote the shorter condition back.  Opening such a route and saving
     * it unchanged made it fire on half its conditions.
     */
    @Test
    public void testAConditionThatStartsWithAGroupKeepsTheRest()
    {
        List<ConditionOutline.Row> rows = outline(
            condition(1, 1),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 2),
            joining(0, ConditionOutline.Joiner.AND),
            condition(0, 3));

        assertEquals(describe(ConditionOutline.toExpression(rows)), "and((or(1,2)),3)",
            "everything after the leading group was dropped, which is a route that fires on half "
            + "the conditions somebody wrote");
    }

    /**
     * And it survives the round trip out of an expression and back.
     *
     * The half that matters for a railway that already exists: this is the shape the old editor's
     * "Group highlighted" button produces, and the shape NodeExpression.normalize makes on its own.
     */
    @Test
    public void testALeadingGroupSurvivesBeingShownAsAnOutline()
    {
        NodeExpression original = new NodeAnd(
            new org.traincontrol.base.NodeGroup(java.util.Arrays.<NodeExpression>asList(
                new NodeOr(sensor(1), sensor(2)))),
            sensor(3));

        List<ConditionOutline.Row> shown = ConditionOutline.of(original);

        assertTrue(ConditionOutline.problems(shown).isEmpty(),
            "a condition that came from a real route must not open flagged");

        assertEquals(describe(ConditionOutline.toExpression(shown)), describe(original),
            "opening this route and saving it unchanged rewrote when it fires");
    }

    /**
     * The shape of an expression, for comparing two of them.
     */
    /**
     * describe(), with a bracket round a single thing ignored.
     *
     * Reading an outline back builds a group where the outline indents, so a nested pair comes back
     * as "and(1,(or(2,3)))" where it went in as "and(1,or(2,3))".  That bracket is not a difference in
     * what the condition MEANS - it is a bracket round one expression - and a test about meaning
     * should not fail on it.
     */
    private static String meaning(NodeExpression node)
    {
        return describe(unwrapped(node));
    }

    private static NodeExpression unwrapped(NodeExpression node)
    {
        if (node instanceof org.traincontrol.base.NodeGroup
            && ((org.traincontrol.base.NodeGroup) node).getExpressions().size() == 1)
        {
            return unwrapped(((org.traincontrol.base.NodeGroup) node).getExpressions().get(0));
        }

        if (node instanceof NodeAnd)
        {
            return new NodeAnd(unwrapped(((NodeAnd) node).getLeft()),
                unwrapped(((NodeAnd) node).getRight()));
        }

        if (node instanceof NodeOr)
        {
            return new NodeOr(unwrapped(((NodeOr) node).getLeft()),
                unwrapped(((NodeOr) node).getRight()));
        }

        return node;
    }

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

            for (NodeExpression inside : ((org.traincontrol.base.NodeGroup) node).getExpressions())
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

    private static ConditionOutline.Row condition(int depth, int sensor)
    {
        return ConditionOutline.Row.condition(depth, RouteCommand.RouteCommandFeedback(sensor, true));
    }

    private static ConditionOutline.Row joining(int depth, ConditionOutline.Joiner joiner)
    {
        return ConditionOutline.Row.joining(depth, joiner);
    }

    private static NodeExpression sensor(int address)
    {
        return new org.traincontrol.base.NodeRouteCommand(
            RouteCommand.RouteCommandFeedback(address, true));
    }
}
