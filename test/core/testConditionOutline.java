package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.ConditionOutline;
import org.traincontrol.base.NodeAnd;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.NodeOr;
import org.traincontrol.base.NodeGroup;
import org.traincontrol.base.NodeRouteCommand;
import org.traincontrol.base.RouteCommand;

/**
 * Conditions as an indented list, where the joining words are indented too.  A word that can be indented says which
 * level it joins at, so the shape IS the meaning:
 *
 *   Sensor 1
 *   and
 *       Sensor 2
 *       or
 *       Sensor 3
 *
 * is 1 and (2 or 3), because the "or" sits at the level of the two conditions it joins.  From that follows the one
 * rule: every word at a level must be the same word.  "and" and "or" side by side at one level is a sentence with two
 * meanings, so problems() reports it and the editor draws it in red rather than picking one quietly.
 */
public class testConditionOutline
{
    /**
     * A group closes the bracket it opened, even when there is nothing in it (C16).  `toTextRepresentationHelper`
     * wrote the ")" from INSIDE the loop over the children, so an empty group never closed.  It takes hand-written
     * JSON to reach, which is an argument about how it is reached, not whether it is wrong.
     *
     * The one-child and two-child cases are the CONTROL: moving the append out of the loop must not emit a bracket
     * per child.
     */
    @Test
    public void testAnEmptyGroupStillClosesItsBracket()
    {
        String empty = NodeExpression.toTextRepresentation(
            new NodeGroup(new ArrayList<NodeExpression>()), null);

        assertEquals(count(empty, '('), count(empty, ')'),
            "the rendered group has " + count(empty, '(') + " opening brackets and "
            + count(empty, ')') + " closing ones: " + empty + " - a group with no children opens a"
            + " bracket it never closes, and everything rendered after it is read against the wrong"
            + " opener (C16)");

        // THE CONTROL: taking the append out of the loop must not give a bracket per child.
        List<NodeExpression> two = new ArrayList<>();

        two.add(new NodeRouteCommand(RouteCommand.RouteCommandFeedback(1, true)));
        two.add(new NodeRouteCommand(RouteCommand.RouteCommandFeedback(2, false)));

        String filled = NodeExpression.toTextRepresentation(new NodeGroup(two), null);

        assertEquals(count(filled, '('), 1,
            "a group renders one opening bracket, whatever it holds: " + filled);

        assertEquals(count(filled, ')'), 1,
            "a group renders one closing bracket, not one per child: " + filled);
    }

    /** How many times a character appears. */
    private static int count(String text, char c)
    {
        int n = 0;

        for (int i = 0; i < text.length(); i++)
        {
            if (text.charAt(i) == c) n++;
        }

        return n;
    }
    /** An indented word joins the indented things, and the outer word joins what is left. */
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

    /** The same conditions with the words at one level mean the other thing. */
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
     * Two different words at one level is refused, and the line that disagrees is named: choosing one quietly is how a
     * route ends up firing at a time nobody asked for.
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
     * A joining word deeper than a condition beside it is flagged, rather than read as nothing.  The reader takes
     * anything deeper than its level as a group, and a group holding a word and no condition is nothing, so an OR
     * vanished and the level's AND joined all three ("the first or is read as an and here").
     */
    @Test
    public void testAWordDeeperThanAConditionBesideItIsFlagged()
    {
        java.util.Set<Integer> flagged = ConditionOutline.problems(outline(
            condition(0, 2),
            joining(1, ConditionOutline.Joiner.OR),
            condition(0, 3),
            joining(0, ConditionOutline.Joiner.AND),
            condition(1, 1),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 4)));

        assertTrue(flagged.contains(1),
            "the OR one level deeper than both conditions it sits between was not flagged - so it is read as"
            + " nothing, the AND joins all three, and the route fires on a different condition from the one"
            + " on screen (Adam, 2026-09-14: \"the first or is read as an and here\"). Flagged: " + flagged);
    }

    /**
     * Each flagged line says which of the two things is wrong with it, because they are put right differently (Adam:
     * "a warning triangle icon with a tooltip explaining what's wrong").
     */
    @Test
    public void testEachFlagSaysWhy()
    {
        java.util.List<ConditionOutline.Row> rows = outline(
            condition(0, 2),
            joining(0, ConditionOutline.Joiner.OR),
            condition(0, 3),
            joining(0, ConditionOutline.Joiner.AND),
            condition(0, 4),
            joining(1, ConditionOutline.Joiner.OR),
            condition(0, 5));

        java.util.Map<Integer, ConditionOutline.Problem> why = ConditionOutline.whatIsWrong(rows);

        assertEquals(why.get(3), ConditionOutline.Problem.DISAGREES,
            "the AND after '2 or 3' is not reported as differing from its level's word: " + why);

        assertEquals(why.get(5), ConditionOutline.Problem.JOINS_NOTHING,
            "the OR indented past both its conditions is not reported as joining nothing: " + why);

        assertEquals(why.keySet(), ConditionOutline.problems(rows),
            "the reasons and the red lines disagree about which lines are wrong");
    }

    /** The control: a word SHALLOWER than both its neighbors is how two groups are joined, and is not flagged. */
    @Test
    public void testAWordJoiningTwoGroupsIsNotFlagged()
    {
        assertTrue(ConditionOutline.problems(outline(
            condition(1, 2),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 3),
            joining(0, ConditionOutline.Joiner.AND),
            condition(1, 1),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 4))).isEmpty(),
            "(2 or 3) and (1 or 4) was flagged - the AND sits shallower than both its neighbours, which is exactly"
            + " how an outline joins two groups");
    }

    /** Indenting the part that was meant to group settles it. */
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

    /** A level's words may repeat as often as they like, so long as they agree. */
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

    /** And the same word at different levels is not a disagreement. */
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

    /** Nothing in the list is a route with no conditions. */
    @Test
    public void testNothingIsNoCondition()
    {
        assertNull(ConditionOutline.toExpression(new ArrayList<ConditionOutline.Row>()),
            "no lines is a route that fires whenever it is triggered");

        assertNull(ConditionOutline.toExpression(null), "and so is nothing at all");
    }

    /**
     * An existing condition opens as an outline that means the same thing: opening and saving a route must not move
     * when it fires.
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
     * A condition built as a TREE, rather than from an outline, keeps its shape.  The older editor's Insert AND and
     * Insert OR buttons produce nestings an outline could not express: "A and (B or C)" was written as one flat level
     * reading "A and B or C", so the route opened flagged red and its reading said "(A and B) or C".
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

    /** And the same with the mixed pair on the LEFT. */
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

    /** A flat chain of ANDs round-trips without growing an indent. */
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
     * Nesting goes as deep as anybody wants, and comes back out the same.  The model never had a limit; the interface
     * hid the indent mark once a line was as deep as the line above allowed.  Four levels, because a limit at three
     * would pass a test for three.
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
     * Moving a condition past its neighbor leaves the joining word where it was: moving the LINE put B beside the word
     * instead of past it, and the tidy after every move swept the word away ("A and B" with B up came back "A B").
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
     * The nesting is the logic, so moving a condition must not deform it: "1 and (2 or 3)" with 2 moved up is
     * "2 and (1 or 3)", not a condition pulled out of its group.
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
     * FR-144: a condition whose neighbor at its own level is a group moves past the whole group.  "(1 or 2 or 3)
     * and 4" with 4 moved up is "4 and (1 or 2 or 3)", every line at its level, and moving it down undoes that.
     *
     * MUTATION: drop the group branch of `moved`, and 4 trades places with 3 inside the group.
     */
    @Test
    public void testAConditionMovesPastAWholeGroup()
    {
        List<ConditionOutline.Row> rows = outline(
            condition(1, 1),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 2),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 3),
            joining(0, ConditionOutline.Joiner.AND),
            condition(0, 4));

        List<ConditionOutline.Row> expected = outline(
            condition(0, 4),
            joining(0, ConditionOutline.Joiner.AND),
            condition(1, 1),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 2),
            joining(1, ConditionOutline.Joiner.OR),
            condition(1, 3));

        assertEquals(ConditionOutline.landing(rows, 6, -1), 0, "4 lands on the first line");

        List<ConditionOutline.Row> moved = ConditionOutline.moved(rows, 6, -1);

        assertEquals(describe(ConditionOutline.toExpression(moved)),
            describe(ConditionOutline.toExpression(expected)), "4 is now in front of the whole group");

        for (int line = 0; line < expected.size(); line++)
        {
            assertEquals(moved.get(line).getDepth(), expected.get(line).getDepth(), "the level of line " + line);
        }

        assertTrue(ConditionOutline.problems(moved).isEmpty(), "nothing is flagged after the move");

        assertEquals(ConditionOutline.landing(moved, 0, 1), 6, "and moving it down lands on the last line");

        assertEquals(describe(ConditionOutline.toExpression(ConditionOutline.moved(moved, 0, 1))),
            describe(ConditionOutline.toExpression(rows)), "moving it back down undoes the move");
    }

    /** A joining word is not moved by hand, and neither is a condition with nowhere to go. */
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
     * A condition whose FIRST line is indented keeps everything after the group.  "(A or B) and C" (the old editor's
     * bracket button) arrives as an outline whose first row is one level in; reading stopped at the first row shallower
     * than that, so "and C" was dropped and saving wrote the shorter condition back.
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
     * And it survives the round trip out of an expression and back (the old editor's "Group highlighted", and
     * NodeExpression.normalize).
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
     * A bracket anywhere but at the start survives being shown as an outline.  For `3 or ((1 or 2) and 4)` the depth-1
     * rows come out AFTER the depth-2 ones, so the reader met a jump from 0 to 2 with no depth-1 row to anchor on,
     * consumed `1 or 2` as a sibling of `3`, and the AND became an OR silently:
     *
     *     cond(0,3) join(0,OR) cond(2,1) join(2,OR) cond(2,2) join(1,AND) cond(1,4)
     *
     * The shape needs no bracket: `Or(And(Or(1,2), 4), 3)` has no `NodeGroup` and read as `or(or(or(1,2),4),3)`.  The
     * family is a cross-operator child that is itself the left child of a cross-operator parent; one alternation is
     * safe.  Routes written in 2.x's text editor can hold it; the new editor cannot build it.
     *
     * MUTATION: reading a deeper run at `row.getDepth()` instead of at `depth + 1` fails this.  The two are the same
     * number wherever the run begins exactly one level deeper, which is why this needed depths that step by two.
     */
    @Test
    public void testABracketAfterTheStartSurvivesBeingShownAsAnOutline()
    {
        NodeExpression original = new NodeOr(sensor(3),
            new NodeAnd(
                new org.traincontrol.base.NodeGroup(java.util.Arrays.<NodeExpression>asList(
                    new NodeOr(sensor(1), sensor(2)))),
                sensor(4)));

        List<ConditionOutline.Row> shown = ConditionOutline.of(original);

        assertTrue(ConditionOutline.problems(shown).isEmpty(),
            "a condition that came from a real route must not open flagged: "
            + ConditionOutline.problems(shown));

        assertEquals(meaning(ConditionOutline.toExpression(shown)), meaning(original),
            "the condition means something different after being shown as an outline and read back "
            + "- and the difference is an AND that became an OR, so the route fires on any of four "
            + "sensors instead of on 3, or on 4 together with one of 1 and 2 (IPR-B2)");
    }

    /**
     * The same defect with no bracket anywhere in the tree: `(1 or 2) and 4, or 3` as `Or(And(Or(1,2), 4), 3)` has no
     * `NodeGroup`, and fails the same way with the AND gone.  The step is two whenever a cross-operator child is itself
     * the left child of a cross-operator parent, because `writeChild` bumps each of them; `Or(And(a,b), c)` is safe.
     *
     * MUTATION: reading a deeper run at `row.getDepth()` fails this exactly as it fails the case above.
     */
    @Test
    public void testTwoOperatorChangesInARowSurviveWithNoBracketAtAll()
    {
        NodeExpression original = new NodeOr(
            new NodeAnd(new NodeOr(sensor(1), sensor(2)), sensor(4)),
            sensor(3));

        List<ConditionOutline.Row> shown = ConditionOutline.of(original);

        assertTrue(ConditionOutline.problems(shown).isEmpty(),
            "a condition that came from a real route must not open flagged: "
            + ConditionOutline.problems(shown));

        assertEquals(meaning(ConditionOutline.toExpression(shown)), meaning(original),
            "two operator changes in a row down the left spine lost the inner one, and there is no "
            + "bracket in this tree - so the population at risk is not 'routes with a NodeGroup', "
            + "which is what the finding and its first disposition both said (VD9-C2)");
    }

    /**
     * describe(), with a bracket round a single thing ignored: reading an outline back builds a group where it indents,
     * which is not a difference in meaning.
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

    /**
     * Every condition in the operator's own routes survives the outline, parsed as 3.0.0 parses it.  A screen of the
     * `routes.json` structure answers about a tree that no longer exists, because `NodeExpression.fromJSON` runs
     * `normalize`, which wraps a `NodeGroup` around any cross-operator left child.  So this parses every condition in
     * the frozen copy of his layout through `fromJSON` (the call `MarklinRoute.fromJSON` makes), then `of()` and
     * `toExpression()`, and compares meanings.  `test/operator_layout` is a snapshot; `cs2_sample_layout` is live and
     * moves under a test's feet.
     *
     * MUTATION: reading a deeper run at `row.getDepth()` leaves this green today, because his routes have no
     * two-alternation condition.  This is the reachability half, not the mechanism half.
     */
    @Test
    public void testEveryConditionInTheOperatorsRoutesSurvivesTheOutline() throws Exception
    {
        java.io.File file = new java.io.File("test/operator_layout/config/gleisbilder/routes.json");

        if (!file.isFile())
        {
            throw new AssertionError("no frozen copy of the operator's routes at " + file);
        }

        String text = new String(java.nio.file.Files.readAllBytes(file.toPath()),
            java.nio.charset.StandardCharsets.UTF_8);

        // The file is an object with a "routes" array, the shape MarklinRoute writes.
        org.json.JSONArray routes = new org.json.JSONObject(text).getJSONArray("routes");

        int withConditions = 0;

        java.util.List<String> changed = new java.util.ArrayList<>();

        for (int i = 0; i < routes.length(); i++)
        {
            org.json.JSONObject route = routes.getJSONObject(i);

            if (!route.has("conditions")) continue;

            withConditions++;

            // THE SAME CALL MarklinRoute.fromJSON MAKES, which is where normalize runs.
            NodeExpression parsed = NodeExpression.fromJSON(route.getJSONObject("conditions"));

            List<ConditionOutline.Row> shown = ConditionOutline.of(parsed);

            NodeExpression back = ConditionOutline.toExpression(shown);

            if (!meaning(parsed).equals(meaning(back)))
            {
                changed.add(route.optString("name", "route " + i)
                    + ": " + meaning(parsed) + "  ->  " + meaning(back));
            }
        }

        assertTrue(withConditions >= 20,
            "only " + withConditions + " conditions were found in the operator's routes, which is "
            + "fewer than his railway is known to carry - so this read the wrong file or the format "
            + "has moved, and a green result here would mean nothing");

        assertEquals(changed, new java.util.ArrayList<String>(),
            changed.size() + " of the operator's own route conditions mean something different after "
            + "being opened in the route editor and saved.  Opening a route and pressing Save is "
            + "enough to do this; nothing is flagged and the reading under the table shows the new "
            + "sentence (IPR-B2)");
    }
    /**
     * Two bracketed groups at one indent are not a disagreement: `A and (B or C) and (D and E)` is buildable with
     * ordinary gestures and `toExpression` reads it correctly, but `settled` was keyed on DEPTH ALONE across the whole
     * list, so the editor showed a line in red and refused the save.  `read` consumes each indented run in its own
     * call, so the check was stricter than the code it protects.  A word may still not disagree with another word in
     * the SAME run.
     *
     * MUTATION: going back to a single map keyed on depth flags row 7 and fails the first assertion.
     */
    @Test
    public void testTwoGroupsAtOneIndentAreNotADisagreement()
    {
        java.util.List<ConditionOutline.Row> rows = new java.util.ArrayList<>();

        rows.add(condition(0, 1));
        rows.add(joining(0, ConditionOutline.Joiner.AND));
        rows.add(condition(1, 2));
        rows.add(joining(1, ConditionOutline.Joiner.OR));
        rows.add(condition(1, 2));
        rows.add(joining(0, ConditionOutline.Joiner.AND));
        rows.add(condition(1, 2));
        rows.add(joining(1, ConditionOutline.Joiner.AND));
        rows.add(condition(1, 2));

        assertEquals(ConditionOutline.problems(rows), java.util.Collections.emptySet(),
            "`A and (B or C) and (D and E)` was flagged as a level disagreeing with itself.  The two "
            + "runs at depth 1 are separated by a joiner at depth 0, so they are different groups - "
            + "which is exactly how `read` consumes them, and the editor refused to save an outline "
            + "it reads perfectly (IPR-B3)");

        // AND THE RULE IT STILL ENFORCES, which is what stops this being a license to disagree.
        java.util.List<ConditionOutline.Row> mixed = new java.util.ArrayList<>();

        mixed.add(condition(0, 1));
        mixed.add(joining(0, ConditionOutline.Joiner.AND));
        mixed.add(condition(0, 1));
        mixed.add(joining(0, ConditionOutline.Joiner.OR));
        mixed.add(condition(0, 1));

        assertEquals(ConditionOutline.problems(mixed), new java.util.LinkedHashSet<>(
            java.util.Arrays.asList(3)),
            "two words side by side at one level still have to be flagged - that is a sentence with "
            + "two meanings, and it is the whole reason this check exists");
    }
}
