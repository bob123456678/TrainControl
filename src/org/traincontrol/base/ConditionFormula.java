package org.traincontrol.base;

import java.util.ArrayList;
import java.util.List;
import org.traincontrol.util.I18n;

/**
 * A boolean formula written over lettered terms - "(A or B) and (C or D)".
 *
 * The second half of a two-step way of building a route condition. The first step is the terms
 * themselves, each a thing that is true or false about the railway: switch 12 is turned, sensor 5 is
 * occupied. The second is how they combine, and that is what this reads.
 *
 * Why it is two steps at all. Conditions used to be one flat list of rows, each carrying the operator
 * that joined it to the next, evaluated left to right - so "a or b and c or d" meant
 * "((a or b) and c) or d", and there was no way to write "(a or b) and (c or d)" at all. Adding
 * brackets to a list of rows means inventing a way to draw nesting in a table, which is a table
 * pretending to be a tree. Separating the two questions - what are the facts, how do they combine -
 * lets each be asked in the form that suits it: a table of terms, and one line of algebra over them.
 *
 * The letters are positional: A is the first term, B the second. They are handles rather than names,
 * so reordering the terms rewrites what a formula means - which is why the editor offers them as
 * things to click rather than asking anybody to remember which is which.
 */
public final class ConditionFormula
{
    private ConditionFormula()
    {
    }

    /**
     * The handle for a term at a given position: A, B, ... Z, then AA, AB.
     *
     * Past twenty-six a condition has stopped being something a person reads, but running out of
     * letters and refusing to name the twenty-seventh would be worse than a two-letter handle nobody
     * will meet.
     *
     * @param index zero-based position in the term list
     * @return its letter
     */
    public static String letterFor(int index)
    {
        if (index < 0) return "";

        StringBuilder out = new StringBuilder();

        int at = index;

        while (at >= 0)
        {
            out.insert(0, (char) ('A' + (at % 26)));

            at = (at / 26) - 1;
        }

        return out.toString();
    }

    /**
     * The formula a flat list of terms already means, for opening an existing route.
     *
     * Every term joined by AND, which is what a condition with no formula of its own has always meant
     * - and what the old row-by-row editor produced when nobody changed an operator.
     *
     * @param terms how many terms there are
     * @return "A and B and C", or empty when there are none
     */
    public static String allOf(int terms)
    {
        StringBuilder out = new StringBuilder();

        for (int at = 0; at < terms; at++)
        {
            if (out.length() > 0) out.append(" and ");

            out.append(letterFor(at));
        }

        return out.toString();
    }

    /**
     * The terms an expression is built from, in the order they first appear in it.
     *
     * The order is what gives each one its letter, so it has to be the order somebody reading the
     * formula left to right would meet them - anything else would hand out handles that look shuffled.
     *
     * A term used twice keeps one entry and one letter, which is right: it is the same fact about the
     * railway, and writing "A and (B or A)" is a perfectly ordinary thing to mean.
     *
     * @param expression a condition, or null
     * @return its terms, in order
     */
    public static List<RouteCommand> termsOf(NodeExpression expression)
    {
        List<RouteCommand> out = new ArrayList<>();

        collect(expression, out);

        return out;
    }

    private static void collect(NodeExpression node, List<RouteCommand> into)
    {
        if (node == null) return;

        if (node instanceof NodeRouteCommand)
        {
            RouteCommand command = ((NodeRouteCommand) node).getRouteCommand();

            for (RouteCommand seen : into)
            {
                if (seen == command || seen.equals(command)) return;
            }

            into.add(command);

            return;
        }

        if (node instanceof NodeAnd)
        {
            collect(((NodeAnd) node).getLeft(), into);
            collect(((NodeAnd) node).getRight(), into);

            return;
        }

        if (node instanceof NodeOr)
        {
            collect(((NodeOr) node).getLeft(), into);
            collect(((NodeOr) node).getRight(), into);

            return;
        }

        if (node instanceof NodeGroup)
        {
            for (NodeExpression inside : ((NodeGroup) node).getExpressions())
            {
                collect(inside, into);
            }
        }
    }

    /**
     * An existing condition written back out as a formula over its own terms.
     *
     * So that opening a route made before this editor existed, or one written in the older text
     * editor, shows the condition it already has rather than an empty box - and so that saving it
     * unchanged cannot alter when it fires.
     *
     * @param expression the condition, or null
     * @param terms its terms, from termsOf
     * @return the formula, or empty for no condition
     */
    public static String formulaFor(NodeExpression expression, List<RouteCommand> terms)
    {
        StringBuilder out = new StringBuilder();

        write(expression, terms == null ? new ArrayList<RouteCommand>() : terms, out);

        return out.toString().trim();
    }

    private static void write(NodeExpression node, List<RouteCommand> terms, StringBuilder out)
    {
        if (node == null) return;

        if (node instanceof NodeRouteCommand)
        {
            RouteCommand command = ((NodeRouteCommand) node).getRouteCommand();

            for (int at = 0; at < terms.size(); at++)
            {
                if (terms.get(at) == command || terms.get(at).equals(command))
                {
                    out.append(letterFor(at));

                    return;
                }
            }

            // A term that is not in the list cannot be named, and inventing a letter for it would
            // produce a formula that reads and means something else.  "?" reads as broken, which it is.
            out.append("?");

            return;
        }

        if (node instanceof NodeAnd)
        {
            write(((NodeAnd) node).getLeft(), terms, out);
            out.append(" and ");
            write(((NodeAnd) node).getRight(), terms, out);

            return;
        }

        if (node instanceof NodeOr)
        {
            write(((NodeOr) node).getLeft(), terms, out);
            out.append(" or ");
            write(((NodeOr) node).getRight(), terms, out);

            return;
        }

        if (node instanceof NodeGroup)
        {
            out.append("(");

            List<NodeExpression> inside = ((NodeGroup) node).getExpressions();

            for (int at = 0; at < inside.size(); at++)
            {
                if (at > 0) out.append(" and ");

                write(inside.get(at), terms, out);
            }

            out.append(")");
        }
    }

    /**
     * A formula broken into the pieces it is built from: letters, "and", "or", and brackets.
     *
     * So the editor can show it as things rather than as text. A formula nobody may type is a formula
     * made of blocks, and a block has to be something you can point at.
     *
     * @param formula the formula
     * @return its pieces, in order
     */
    public static List<String> tokens(String formula)
    {
        List<String> out = new ArrayList<>();

        if (formula == null) return out;

        int at = 0;

        while (at < formula.length())
        {
            char one = formula.charAt(at);

            if (Character.isWhitespace(one))
            {
                at++;
            }
            else if (one == '(' || one == ')')
            {
                out.add(String.valueOf(one));
                at++;
            }
            else if (Character.isLetter(one))
            {
                int start = at;

                while (at < formula.length() && Character.isLetter(formula.charAt(at))) at++;

                out.add(formula.substring(start, at));
            }
            else
            {
                // Anything else is not part of the language.  Kept as its own piece rather than
                // dropped, so a formula that somehow contains one can still be seen and taken apart.
                out.add(String.valueOf(one));
                at++;
            }
        }

        return out;
    }

    /**
     * The formula with one piece taken out of it, and tidied so what is left still reads.
     *
     * Removing a term leaves the word that joined it to its neighbour with nothing on one side, and a
     * formula that says "A and" is not one somebody meant to write - they meant to remove the term and
     * its joining word together, because that is what removing a requirement is. Removing a bracket
     * takes its partner with it, for the same reason: half a pair is not a thing anybody wants.
     *
     * @param formula the formula
     * @param index which piece, counting from zero
     * @return the formula without it
     */
    public static String without(String formula, int index)
    {
        List<String> pieces = tokens(formula);

        if (index < 0 || index >= pieces.size()) return formula;

        String going = pieces.get(index);

        if ("(".equals(going) || ")".equals(going))
        {
            int partner = partnerOf(pieces, index);

            pieces.remove(index);

            if (partner >= 0) pieces.remove(partner > index ? partner - 1 : partner);
        }
        else if (isJoiner(going))
        {
            pieces.remove(index);
        }
        else
        {
            pieces.remove(index);

            // The word that joined it: the one before by preference, since "A and B" without B is "A"
            // rather than "and A"
            if (index - 1 >= 0 && isJoiner(pieces.get(index - 1))) pieces.remove(index - 1);
            else if (index < pieces.size() && isJoiner(pieces.get(index))) pieces.remove(index);
        }

        return tidy(pieces);
    }

    private static boolean isJoiner(String piece)
    {
        return "and".equalsIgnoreCase(piece) || "or".equalsIgnoreCase(piece);
    }

    /**
     * The bracket matching the one at an index, or -1 when it has none.
     */
    private static int partnerOf(List<String> pieces, int index)
    {
        boolean forward = "(".equals(pieces.get(index));

        int depth = 0;

        for (int at = index; at >= 0 && at < pieces.size(); at += forward ? 1 : -1)
        {
            String one = pieces.get(at);

            if ("(".equals(one)) depth += forward ? 1 : -1;
            else if (")".equals(one)) depth += forward ? -1 : 1;

            if (depth == 0) return at;
        }

        return -1;
    }

    /**
     * Puts the pieces back together, dropping anything left dangling.
     *
     * A joining word at either end, a doubled one, and a bracket pair with nothing in it are all
     * things a removal can leave behind, and none of them is something a person would write.
     */
    private static String tidy(List<String> pieces)
    {
        boolean changed = true;

        while (changed)
        {
            changed = false;

            for (int at = 0; at < pieces.size(); at++)
            {
                boolean atStart = at == 0 || "(".equals(pieces.get(at - 1));
                boolean atEnd = at == pieces.size() - 1 || ")".equals(pieces.get(at + 1));

                if (isJoiner(pieces.get(at)) && (atStart || atEnd))
                {
                    pieces.remove(at);
                    changed = true;
                    break;
                }

                if ("(".equals(pieces.get(at)) && at + 1 < pieces.size()
                    && ")".equals(pieces.get(at + 1)))
                {
                    pieces.remove(at + 1);
                    pieces.remove(at);
                    changed = true;
                    break;
                }
            }
        }

        StringBuilder out = new StringBuilder();

        for (int at = 0; at < pieces.size(); at++)
        {
            String one = pieces.get(at);

            boolean space = out.length() > 0 && !")".equals(one)
                && !"(".equals(out.charAt(out.length() - 1) + "");

            if (space) out.append(' ');

            out.append(one);
        }

        return out.toString().trim();
    }

    /**
     * Reads a formula and builds the expression it describes.
     *
     * @param formula what the user typed
     * @param terms the terms, in the order their letters follow
     * @return the expression, or null when the formula is empty - a route with no condition
     * @throws IllegalArgumentException with a sentence saying what is wrong and where, for showing
     */
    public static NodeExpression parse(String formula, List<RouteCommand> terms)
    {
        if (formula == null || formula.trim().isEmpty()) return null;

        Parser parser = new Parser(formula, terms == null ? new ArrayList<RouteCommand>() : terms);

        NodeExpression out = parser.expression();

        parser.expectEnd();

        return out;
    }

    /**
     * Whether a formula reads, without building anything.
     *
     * @param formula what the user typed
     * @param terms how many terms exist
     * @return null when it is fine, or the complaint
     */
    public static String problemWith(String formula, int terms)
    {
        List<RouteCommand> stand = new ArrayList<>();

        for (int at = 0; at < terms; at++) stand.add(RouteCommand.RouteCommandStop());

        try
        {
            parse(formula, stand);

            return null;
        }
        catch (IllegalArgumentException e)
        {
            return e.getMessage();
        }
    }

    /**
     * A recursive descent over the grammar
     * <pre>
     *   expression := conjunction (OR conjunction)*
     *   conjunction := factor (AND factor)*
     *   factor := LETTER | "(" expression ")"
     * </pre>
     *
     * AND binds tighter than OR, as it does in every language that has both, so "A or B and C" is
     * "A or (B and C)". That is the one thing here somebody might expect to work the other way, which
     * is exactly why the editor shows what a formula means in words underneath it.
     */
    private static final class Parser
    {
        private final String text;
        private final List<RouteCommand> terms;
        private int at;

        Parser(String text, List<RouteCommand> terms)
        {
            this.text = text;
            this.terms = terms;
        }

        NodeExpression expression()
        {
            NodeExpression left = conjunction();

            while (word("or") || symbol("||"))
            {
                left = new NodeOr(left, conjunction());
            }

            return left;
        }

        private NodeExpression conjunction()
        {
            NodeExpression left = factor();

            while (word("and") || symbol("&&"))
            {
                left = new NodeAnd(left, factor());
            }

            return left;
        }

        private NodeExpression factor()
        {
            skipSpace();

            if (at >= text.length())
            {
                throw new IllegalArgumentException(I18n.t("route.formula.errorEndsEarly"));
            }

            if (text.charAt(at) == '(')
            {
                at++;

                NodeExpression inside = expression();

                skipSpace();

                if (at >= text.length() || text.charAt(at) != ')')
                {
                    throw new IllegalArgumentException(I18n.t("route.formula.errorUnclosed"));
                }

                at++;

                // A NodeGroup rather than the bare expression, so that saving and reopening gives back
                // the brackets the user wrote.  They are not redundant to a reader even where they are
                // redundant to the algebra.
                List<NodeExpression> grouped = new ArrayList<>();
                grouped.add(inside);

                return new NodeGroup(grouped);
            }

            int start = at;

            while (at < text.length() && Character.isLetter(text.charAt(at))) at++;

            String letter = text.substring(start, at).toUpperCase();

            if (letter.isEmpty())
            {
                throw new IllegalArgumentException(
                    I18n.f("route.formula.errorUnexpected", String.valueOf(text.charAt(at))));
            }

            // "and" and "or" where a term belongs is a formula missing something, and saying so beats
            // reporting the letter A as an unknown term
            if ("AND".equals(letter) || "OR".equals(letter))
            {
                throw new IllegalArgumentException(
                    I18n.f("route.formula.errorOperatorWithoutTerm", letter.toLowerCase()));
            }

            int index = indexOf(letter);

            if (index < 0 || index >= terms.size())
            {
                throw new IllegalArgumentException(I18n.f("route.formula.errorNoSuchTerm", letter));
            }

            return new NodeRouteCommand(terms.get(index));
        }

        /**
         * The position a handle names, or -1 when it is not a handle at all.
         */
        private static int indexOf(String letter)
        {
            int out = 0;

            for (int c = 0; c < letter.length(); c++)
            {
                char one = letter.charAt(c);

                if (one < 'A' || one > 'Z') return -1;

                out = out * 26 + (one - 'A' + 1);
            }

            return out - 1;
        }

        /**
         * Takes a word if it is next, whole - so a term called ANDover is not read as "and" plus
         * "over".
         */
        private boolean word(String which)
        {
            skipSpace();

            int end = at + which.length();

            if (end > text.length()) return false;

            if (!text.substring(at, end).equalsIgnoreCase(which)) return false;

            if (end < text.length() && Character.isLetter(text.charAt(end))) return false;

            at = end;

            return true;
        }

        private boolean symbol(String which)
        {
            skipSpace();

            if (!text.startsWith(which, at)) return false;

            at += which.length();

            return true;
        }

        private void skipSpace()
        {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
        }

        void expectEnd()
        {
            skipSpace();

            if (at < text.length())
            {
                throw new IllegalArgumentException(
                    I18n.f("route.formula.errorTrailing", text.substring(at).trim()));
            }
        }
    }
}
