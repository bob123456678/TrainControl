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
     * A bracket anywhere but at the start survives being shown as an outline (IPR-B2).
     *
     * `3 or ((1 or 2) and 4)`. Every level of it is written at the right depth - 0 for the OR, 1 for
     * the AND, 2 for the bracketed OR inside it - but the depth-1 rows come out **after** the depth-2
     * ones, because the AND's left child is the bracket:
     *
     *     cond(0,3) join(0,OR) cond(2,1) join(2,OR) cond(2,2) join(1,AND) cond(1,4)
     *
     * so the reader met a jump from 0 to 2 with no depth-1 row before it to anchor on. It recursed at
     * the row's own depth, consumed `1 or 2` as a sibling of `3`, and then read the depth-1 remainder
     * as a second sibling - a level of one item, whose AND had nowhere to go. **The AND became an
     * OR**, silently: no level disagreed with itself, so nothing was flagged, the reading under the
     * table showed the wrong sentence, the Test button evaluated it, and Save wrote it back. The route
     * then fired on a condition nobody wrote.
     *
     * **The population is routes written in 2.x's text editor with a bracket in a non-leading
     * position**, opened in this editor. The new editor cannot build the shape, `fromTextRepresentation`
     * has no caller left in `src/`, and Adam's own `routes.json` has no `NodeGroup` at all - so this is
     * "could happen" rather than "does happen", which is why the finding graded it B.
     *
     * MUTATION: reading a deeper run at `row.getDepth()` instead of at `depth + 1` fails this. The two
     * are the same number wherever the run begins exactly one level deeper, which is why this needed a
     * shape whose depths step by two.
     *
     * **That shape does not need a bracket** (`VD9-C2`). `Or(And(Or(1,2), 4), 3)` has no `NodeGroup`
     * anywhere and comes back as `or(or(or(1,2),4),3)` under the old reader - the same lost AND. The
     * family is a cross-operator child that is itself the left child of a cross-operator parent: two
     * alternations in a row down the left spine, each bumped by `writeChild`, both written before the
     * outer joiner. One alternation is safe.
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
     * The same defect with no bracket anywhere in the tree (`IPR-B2`, widened by `VD9-C2`).
     *
     * `(1 or 2) and 4, or 3` - as a tree, `Or(And(Or(1,2), 4), 3)`. No `NodeGroup`, and it fails the
     * same way: `or(or(or(1,2),4),3)` under the old reader, with the AND gone.
     *
     * **This is the case that says what the family really is.** The first version of this fix was
     * written up as being about *a bracket in a non-leading position*, and a bracket is only one way
     * to arrive: the step is two whenever a cross-operator child is itself the left child of a
     * cross-operator parent, because `writeChild` bumps each of them and the left spine is written
     * before the outer joiner. One alternation is safe - `Or(And(a,b), c)` steps 1,1,1,0,0 and always
     * read correctly. Two in a row is not.
     *
     * It also matters for who is at risk. Screened on the author's own `routes.json` for **this**
     * shape rather than for `NodeGroup`, all 39 conditions come back clean - but the two earlier
     * screens asked the wrong question and happened to reach the same answer.
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

    /**
     * Every condition in the operator's own routes survives the outline, parsed as 3.0.0 parses it.
     *
     * **Adam's question, 2026-09-03:** *"are you comparing if the old routes from my 2.8.1 files match
     * those parsed in 3.0.0?"* The screens behind `IPR-B2` were not. They walked the structure of
     * `routes.json`, and that structure is **not** what the editor is handed:
     * `NodeExpression.fromJSON` runs `normalize`, which inserts a `NodeGroup` around any cross-operator
     * left child. A file with no brackets in it can therefore produce a bracketed tree, and a screen of
     * the file is answering about a tree that no longer exists.
     *
     * So this parses instead of reading. Every condition in the frozen copy of his layout goes through
     * `fromJSON` - the same call `MarklinRoute.fromJSON` makes - and then through `of()` and
     * `toExpression()`, and the meanings are compared. That is the whole of what the editor does to a
     * stored route between opening it and saving it.
     *
     * **The frozen copy, not the live folder.** `test/operator_layout` is a snapshot of his railway
     * taken for exactly this kind of question; `cs2_sample_layout` is the live one and moves under a
     * test's feet. If his routes ever grow the shape, this fails on the snapshot the day it is
     * refreshed, which is soon enough.
     *
     * MUTATION: reading a deeper run at `row.getDepth()` leaves this green today - his routes have no
     * two-alternation condition - which is the honest result and is why the two constructed cases
     * above exist. This test is the reachability half, not the mechanism half.
     */
    @Test
    public void testEveryConditionInTheOperatorsRoutesSurvivesTheOutline() throws Exception
    {
        java.io.File file = new java.io.File("test/operator_layout/config/gleisbilder/routes.json");

        if (!file.isFile())
        {
            throw new org.testng.SkipException("no frozen copy of the operator's routes at " + file);
        }

        String text = new String(java.nio.file.Files.readAllBytes(file.toPath()),
            java.nio.charset.StandardCharsets.UTF_8);

        // The file is an object with a "routes" array in it, which is the shape MarklinRoute writes.
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
}
