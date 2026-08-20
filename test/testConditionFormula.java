import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.ConditionFormula;
import org.traincontrol.base.NodeAnd;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.NodeGroup;
import org.traincontrol.base.NodeOr;
import org.traincontrol.base.NodeRouteCommand;
import org.traincontrol.base.RouteCommand;

/**
 * Reading a boolean formula written over lettered terms.
 *
 * The second half of the two-step way of building a condition Adam asked for: the terms are defined
 * as rows, finalised into things with handles, and then combined by writing algebra over those
 * handles. This is the reader for that algebra.
 *
 * The thing it exists to make possible is "(A or B) and (C or D)", which the old flat list of rows
 * could not express at all - each row carried the operator joining it to the next and the whole was
 * evaluated left to right, so the same four terms meant "((A or B) and C) or D" and there was no way
 * to say otherwise.
 *
 * Tested here rather than through the editor because a parser is the one part of an interface that
 * can be examined honestly on its own: every case below is a sentence somebody could type, and the
 * answer is either the tree they meant or a complaint they can act on.
 */
public class testConditionFormula
{
    /**
     * The case that started it.
     */
    @Test
    public void testBracketsGroupWhatIsInsideThem()
    {
        NodeExpression parsed = ConditionFormula.parse("(A or B) and (C or D)", terms(4));

        assertTrue(parsed instanceof NodeAnd,
            "the whole formula is an AND of two bracketed halves - if this is an OR, the brackets "
            + "were ignored and the formula means something else entirely");

        NodeAnd whole = (NodeAnd) parsed;

        assertTrue(whole.getLeft() instanceof NodeGroup, "the left half is a bracketed group");
        assertTrue(whole.getRight() instanceof NodeGroup, "and so is the right");
    }

    /**
     * AND binds tighter than OR, as it does everywhere else that has both.
     *
     * The one rule here somebody might expect to work the other way round, which is why the editor
     * shows what a formula means in words underneath it rather than leaving them to remember.
     */
    @Test
    public void testAndBindsTighterThanOr()
    {
        NodeExpression parsed = ConditionFormula.parse("A or B and C", terms(3));

        assertTrue(parsed instanceof NodeOr,
            "A or B and C is A or (B and C), so the top of the tree is the OR");

        assertTrue(((NodeOr) parsed).getRight() instanceof NodeAnd,
            "and the AND is underneath it, on the right");
    }

    /**
     * A term is the row its letter names, and reordering the rows changes what a formula means.
     *
     * Positional on purpose - the letters are handles rather than names - and worth pinning because
     * it is the one thing about this design that could surprise somebody who moved a row.
     */
    @Test
    public void testALetterNamesThePositionItStandsFor()
    {
        List<RouteCommand> given = terms(3);

        NodeExpression parsed = ConditionFormula.parse("C", given);

        assertTrue(parsed instanceof NodeRouteCommand, "one letter is one term");

        assertSame(((NodeRouteCommand) parsed).getRouteCommand(), given.get(2),
            "C is the third term, by position");
    }

    /**
     * An empty formula is a route with no condition, not an error.
     */
    @Test
    public void testNothingMeansNoCondition()
    {
        assertNull(ConditionFormula.parse("", terms(2)), "an empty formula is no condition at all");
        assertNull(ConditionFormula.parse("   ", terms(2)), "and so is whitespace");
    }

    /**
     * Every way of getting it wrong is refused with something the user can act on.
     *
     * A parser that accepts a broken formula quietly is worse than one that refuses it, because the
     * route then exists, looks right in the list, and never fires - which is the exact failure this
     * whole editor was built to stop happening.
     */
    @Test
    public void testEveryMistakeIsRefusedAndExplained()
    {
        refused("A and", 2, "an operator with nothing after it");
        refused("(A or B", 2, "a bracket that was never closed");
        refused("A B", 2, "two terms with nothing joining them");
        refused("A or Z", 2, "a letter with no row behind it");
        refused("and A", 2, "an operator where a term belongs");
        refused("A or (", 2, "a bracket opened at the very end");
    }

    /**
     * And the complaint is a sentence rather than a stack trace.
     */
    @Test
    public void testTheComplaintIsReadable()
    {
        String problem = ConditionFormula.problemWith("A or Z", 2);

        assertNotNull(problem, "this formula names a term that does not exist");

        assertTrue(problem.contains("Z"),
            "the complaint has to say WHICH letter is wrong, or it is a puzzle rather than a "
            + "message: " + problem);

        assertNull(ConditionFormula.problemWith("A or B", 2),
            "and a formula that reads must not be complained about");
    }

    /**
     * Handles run past Z rather than running out.
     *
     * Past twenty-six terms a condition has stopped being something a person reads, but refusing to
     * name the twenty-seventh would be worse than a two-letter handle nobody will meet.
     */
    @Test
    public void testTheLettersDoNotRunOut()
    {
        assertEquals(ConditionFormula.letterFor(0), "A");
        assertEquals(ConditionFormula.letterFor(25), "Z");
        assertEquals(ConditionFormula.letterFor(26), "AA");

        assertNull(ConditionFormula.problemWith(ConditionFormula.letterFor(26), 27),
            "the twenty-seventh term must be usable by the handle it was given");
    }

    /**
     * An existing route with no formula of its own reads as all of its terms together.
     *
     * What a condition has always meant when nobody changed an operator, so opening an old route and
     * saving it unchanged cannot alter when it fires.
     */
    @Test
    public void testAnExistingConditionReadsAsEverythingTogether()
    {
        assertEquals(ConditionFormula.allOf(3), "A and B and C");
        assertEquals(ConditionFormula.allOf(1), "A");
        assertEquals(ConditionFormula.allOf(0), "");

        assertNull(ConditionFormula.problemWith(ConditionFormula.allOf(3), 3),
            "and what it produces has to be something it can read back");
    }

    /**
     * A condition written as a formula comes back as the same formula.
     *
     * The half that makes this usable on a railway that already exists. Routes were built before this
     * editor and in the older text one, and opening those has to show the condition they have rather
     * than an empty box - saving unchanged must not alter when a route fires.
     */
    @Test
    public void testAFormulaSurvivesBeingReadAndWrittenBack()
    {
        List<RouteCommand> distinct = distinctTerms(4);

        String original = "(A or B) and (C or D)";

        NodeExpression parsed = ConditionFormula.parse(original, distinct);

        List<RouteCommand> recovered = ConditionFormula.termsOf(parsed);

        assertEquals(recovered.size(), 4, "all four terms have to come back, in order");

        assertEquals(ConditionFormula.formulaFor(parsed, recovered), original,
            "the formula written back must be the one that went in - a route opened and saved "
            + "unchanged cannot start firing at different times");
    }

    /**
     * A term used twice keeps one letter.
     *
     * "A and (B or A)" is an ordinary thing to mean - the same fact about the railway, used in two
     * places - and giving the second appearance its own letter would say there were three facts.
     */
    @Test
    public void testATermUsedTwiceIsStillOneTerm()
    {
        List<RouteCommand> distinct = distinctTerms(2);

        NodeExpression parsed = ConditionFormula.parse("A and (B or A)", distinct);

        assertEquals(ConditionFormula.termsOf(parsed).size(), 2,
            "two facts are used, one of them twice");
    }

    private static void refused(String formula, int terms, String why)
    {
        try
        {
            ConditionFormula.parse(formula, terms(terms));

            fail("\"" + formula + "\" is " + why + " and must be refused, not guessed at");
        }
        catch (IllegalArgumentException expected)
        {
            assertNotNull(expected.getMessage(),
                "a refusal with no message is a refusal nobody can act on");
        }
    }

    /**
     * Stand-in terms. What they command does not matter here - only how many there are and which
     * position each occupies.
     */
    /**
     * Terms that are distinguishable from each other, for the tests that care which is which.
     */
    private static List<RouteCommand> distinctTerms(int many)
    {
        List<RouteCommand> out = new ArrayList<>();

        for (int at = 0; at < many; at++)
        {
            out.add(RouteCommand.RouteCommandFeedback(at + 1, true));
        }

        return out;
    }

    private static List<RouteCommand> terms(int many)
    {
        List<RouteCommand> out = new ArrayList<>();

        for (int at = 0; at < many; at++) out.add(RouteCommand.RouteCommandStop());

        return out;
    }
}
