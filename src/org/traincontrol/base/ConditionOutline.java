package org.traincontrol.base;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A route's conditions as an indented list, and the expression that list means.
 *
 * The conditions are a tree - this and that, or the other - and every way of showing a tree as a flat
 * list has to put the shape somewhere. Writing it beside the list as algebra put it in letters that
 * stood for rows, which is a thing to remember rather than a thing to see. An outline puts it in the
 * list itself.
 *
 * A line is either a condition or the word joining what is on either side of it, and BOTH can be
 * indented. That second part is what makes the whole thing work, and it took a wrong turn to find:
 *
 * <pre>
 *     Sensor 1 occupied
 * and
 *         Sensor 2 occupied
 *     or
 *         Sensor 3 occupied
 * </pre>
 *
 * The indented "or" joins the two indented conditions; the "and" at the outer level joins sensor 1 to
 * that whole group. So it reads "1 and (2 or 3)" - and the reader can see which word joins what by
 * where it sits, without a precedence rule or a bracket anywhere.
 *
 * From that follows the one rule this has: EVERY WORD AT A LEVEL MUST BE THE SAME WORD. "and" and
 * "or" side by side at one level is a sentence with two meanings, and the answer is not to pick one
 * quietly - it is to say so, and let the reader indent the part they meant to group. That is what
 * problems() reports and what the editor draws in red.
 */
public final class ConditionOutline
{
    /**
     * How two things are joined.
     */
    public enum Joiner
    {
        AND,
        OR
    }

    /**
     * One line: either a condition, or the word joining what is either side of it.
     */
    public static final class Row
    {
        private final int depth;
        private final Joiner joiner;
        private final RouteCommand command;

        private Row(int depth, Joiner joiner, RouteCommand command)
        {
            this.depth = Math.max(0, depth);
            this.joiner = joiner;
            this.command = command;
        }

        /**
         * @param depth how far indented, from zero
         * @param command the condition
         * @return a condition line
         */
        public static Row condition(int depth, RouteCommand command)
        {
            return new Row(depth, null, command);
        }

        /**
         * @param depth how far indented, from zero
         * @param joiner the word
         * @return a joining line
         */
        public static Row joining(int depth, Joiner joiner)
        {
            return new Row(depth, joiner == null ? Joiner.AND : joiner, null);
        }

        public boolean isJoiner()
        {
            return command == null;
        }

        public int getDepth()
        {
            return depth;
        }

        public Joiner getJoiner()
        {
            return joiner;
        }

        public RouteCommand getCommand()
        {
            return command;
        }

        /**
         * @param to the new depth
         * @return the same line, indented differently
         */
        public Row atDepth(int to)
        {
            return new Row(to, joiner, command);
        }

        /**
         * @param how the new word
         * @return the same joining line, with a different word
         */
        public Row joinedBy(Joiner how)
        {
            return isJoiner() ? joining(depth, how) : this;
        }

        /**
         * @param what the new condition
         * @return the same line, about a different condition
         */
        public Row about(RouteCommand what)
        {
            return isJoiner() ? this : condition(depth, what);
        }
    }

    private ConditionOutline()
    {
    }

    /**
     * The lines whose word disagrees with the rest of its level.
     *
     * A level with "and" and "or" on it means two different things depending on which is read first,
     * and the editor shows those lines in red rather than choosing. Indenting one of the conditions
     * settles it, which is the whole reason lines can be indented.
     *
     * The FIRST word at a level is taken as the one meant, so the ones flagged are the ones that
     * differ from it. That is a guess about which is the mistake, but it is the useful way round: it
     * marks one line rather than all of them, and it is stable as more are added.
     *
     * @param rows the outline
     * @return the indices of the lines to flag, empty when the outline reads
     */
    public static Set<Integer> problems(List<Row> rows)
    {
        Set<Integer> out = new LinkedHashSet<>();

        if (rows == null) return out;

        // The word first seen at each depth
        java.util.Map<Integer, Joiner> settled = new java.util.LinkedHashMap<>();

        for (int at = 0; at < rows.size(); at++)
        {
            Row row = rows.get(at);

            if (!row.isJoiner()) continue;

            Joiner already = settled.get(row.getDepth());

            if (already == null) settled.put(row.getDepth(), row.getJoiner());
            else if (already != row.getJoiner()) out.add(at);
        }

        return out;
    }

    /**
     * The expression an outline means.
     *
     * Where a level disagrees with itself, the first word at that level is used - the editor refuses
     * to save such an outline, so this only has to be defined rather than right.
     *
     * @param rows the outline, in order
     * @return the expression, or null when there is nothing in it
     */
    public static NodeExpression toExpression(List<Row> rows)
    {
        if (rows == null || rows.isEmpty()) return null;

        int[] at = new int[]{0};

        // The OUTERMOST level in the outline, not the depth of the first line.
        //
        // A condition that begins with a bracketed group - "(A or B) and C", which the old editor has
        // a button for and which NodeExpression.normalize produces on its own - opens as an outline
        // whose first row is one level in, because the group is the first thing in the sentence.
        // Starting from the first row's depth then made read() stop at the first line shallower than
        // it, which is the "and": everything from there on was dropped.
        //
        // Silently, which is what made it dangerous.  The reading under the table showed the
        // truncated version, no level disagreed with itself so nothing was flagged red, and Save
        // wrote the shorter condition back - so opening such a route and saving it unchanged left it
        // firing on half the conditions somebody had written.
        int outermost = rows.get(0).getDepth();

        for (Row row : rows) outermost = Math.min(outermost, row.getDepth());

        return read(rows, at, outermost);
    }

    /**
     * Reads every line at or below a depth, stopping when the outline comes back out.
     */
    private static NodeExpression read(List<Row> rows, int[] at, int depth)
    {
        List<NodeExpression> items = new ArrayList<>();
        List<Joiner> words = new ArrayList<>();

        while (at[0] < rows.size() && rows.get(at[0]).getDepth() >= depth)
        {
            Row row = rows.get(at[0]);

            if (row.getDepth() == depth)
            {
                if (row.isJoiner())
                {
                    words.add(row.getJoiner());
                }
                else
                {
                    items.add(new NodeRouteCommand(row.getCommand()));
                }

                at[0]++;
            }
            else
            {
                // A deeper run is one thing at this level, and the whole of it is consumed here.
                //
                // AT depth + 1, NOT AT THE ROW’S OWN DEPTH (IPR-B2).
                //
                // The two are the same number wherever this branch meets a row exactly one level
                // deeper, which is every outline in which no level’s own rows come out after a
                // deeper run.  They differ when they do -
                // "3 or ((1 or 2) and 4)" writes the OR at 0, the AND at 1 and the bracket at 2, and
                // because the bracket is the AND’s LEFT child the reader meets 0 then 2, with no
                // depth-1 row yet to anchor on.
                //
                // Reading that run at 2 made it a sibling of the 3, and the depth-1 remainder a second
                // sibling - a level holding one item, whose AND had nowhere to go and was dropped.  The
                // AND became an OR and nothing said so: no level disagreed with itself, so no row was
                // flagged, and Save wrote the new meaning back.  Reading it at depth + 1 puts the run
                // where the writer put it, as the first item of the next level down, and the AND that
                // follows joins it.
                //
                // THE FAMILY IS WIDER THAN A BRACKET, and the first account of this said otherwise
                // (VD9-C2).  The step is two whenever a cross-operator child is itself the left child
                // of a cross-operator parent: writeChild bumps each of them and the left spine is
                // written first, so both bumps land before the outer joiner does.  A NodeGroup is one
                // way to arrive there and not the only one - `Or(And(Or(1,2), 4), 3)` has no group in
                // it at all and came back as `or(or(or(1,2),4),3)`, the AND gone, under the old
                // reader.  One alternation is safe; two in a row is not.
                NodeExpression inside = read(rows, at, depth + 1);

                if (inside != null) items.add(group(inside));
            }
        }

        if (items.isEmpty()) return null;

        // One word for the level, since a level that disagrees with itself is refused before this
        Joiner word = words.isEmpty() ? Joiner.AND : words.get(0);

        NodeExpression out = items.get(0);

        for (int item = 1; item < items.size(); item++)
        {
            out = word == Joiner.OR
                ? new NodeOr(out, items.get(item)) : new NodeAnd(out, items.get(item));
        }

        return out;
    }

    private static NodeExpression group(NodeExpression inside)
    {
        List<NodeExpression> one = new ArrayList<>();

        one.add(inside);

        return new NodeGroup(one);
    }

    /**
     * An existing condition as an outline, so a route made before this opens as one.
     *
     * @param expression the condition, or null
     * @return the lines
     */
    public static List<Row> of(NodeExpression expression)
    {
        List<Row> out = new ArrayList<>();

        write(expression, 0, out);

        return out;
    }

    /**
     * The outline with one condition moved past the condition above or below it.
     *
     * Not by moving the LINE.  The joining words are lines of their own, so lifting a condition one
     * line puts it next to the word above rather than past it - and a word with a word on one side
     * and nothing on the other is not a sentence, so straightening the outline afterwards throws it
     * away.  "A and B" with B moved up came back as "A B": one fewer condition joined, and a change
     * to when the route fires made by a button that says nothing about firing.
     *
     * So the two conditions trade places and every word, and every level, stays exactly where it
     * was.  The shape IS the logic here - which condition is nested in which group, joined by which
     * word - and the arrows reorder the conditions within it rather than rebuilding it.  "A and (B
     * or C)" with B moved up is "B and (A or C)": the same sentence about different sensors, which
     * is what somebody pressing the arrow beside B means.
     *
     * @param rows the outline
     * @param line the row that was pressed
     * @param by -1 for up, 1 for down
     * @return a new list in the new order, or a copy unchanged where there is nowhere to go
     */
    public static List<Row> moved(List<Row> rows, int line, int by)
    {
        List<Row> out = new ArrayList<>();

        if (rows != null) out.addAll(rows);

        int to = destination(out, line, by);

        if (to < 0) return out;

        RouteCommand moving = out.get(line).getCommand();

        out.set(line, out.get(line).about(out.get(to).getCommand()));
        out.set(to, out.get(to).about(moving));

        return out;
    }

    /**
     * Whether there is another condition that way for this one to trade places with.
     *
     * Asked by the editor to decide whether to draw the arrow at all: an arrow that does nothing is
     * worse than no arrow, because pressing it reads as the feature being broken.
     *
     * @param rows the outline
     * @param line the row
     * @param by -1 to look up, 1 to look down
     * @return true when moved() would do something
     */
    public static boolean canMove(List<Row> rows, int line, int by)
    {
        return destination(rows, line, by) >= 0;
    }

    /**
     * Which row a move would land on, or -1 for none.
     *
     * @return the index of the nearest condition that way, skipping the words in between
     */
    private static int destination(List<Row> rows, int line, int by)
    {
        if (rows == null || line < 0 || line >= rows.size() || by == 0) return -1;

        // A word's place is settled by the conditions around it, so it is not moved by hand.
        if (rows.get(line).isJoiner()) return -1;

        for (int at = line + by; at >= 0 && at < rows.size(); at += by)
        {
            if (!rows.get(at).isJoiner()) return at;
        }

        return -1;
    }

    /**
     * Walks the tree, flattening runs of one word and indenting anything bracketed.
     */
    private static void write(NodeExpression node, int depth, List<Row> into)
    {
        if (node == null) return;

        if (node instanceof NodeRouteCommand)
        {
            into.add(Row.condition(depth, ((NodeRouteCommand) node).getRouteCommand()));

            return;
        }

        if (node instanceof NodeGroup)
        {
            List<NodeExpression> inside = ((NodeGroup) node).getExpressions();

            // A bracket round a single condition is a bracket round nothing, and indenting it would
            // suggest a nesting the reader did not write
            if (inside.size() == 1 && inside.get(0) instanceof NodeRouteCommand)
            {
                write(inside.get(0), depth, into);

                return;
            }

            for (int at = 0; at < inside.size(); at++)
            {
                if (at > 0) into.add(Row.joining(depth + 1, Joiner.AND));

                write(inside.get(at), depth + 1, into);
            }

            return;
        }

        boolean or = node instanceof NodeOr;

        NodeExpression left = or ? ((NodeOr) node).getLeft() : ((NodeAnd) node).getLeft();
        NodeExpression right = or ? ((NodeOr) node).getRight() : ((NodeAnd) node).getRight();

        writeChild(left, depth, or, into);

        into.add(Row.joining(depth, or ? Joiner.OR : Joiner.AND));

        writeChild(right, depth, or, into);
    }

    /**
     * A child that joins its own pair with the OTHER word goes a level deeper.
     *
     * The whole grammar of this outline is that a level is one word: "and" beside "or" at the same
     * depth means two different things and is shown in red.  Writing every child at the parent's depth
     * therefore flattened "A and (B or C)" into one level reading "A and B or C" - which is not what
     * the route says, is refused by this class's own rule the moment anything is saved, and parses
     * back as "(A and B) or C" if it is not.  The Test button evaluated that wrong expression.
     *
     * A child joined by the SAME word stays flat, which is what makes "A and B and C" one list rather
     * than a staircase.
     */
    private static void writeChild(NodeExpression child, int depth, boolean parentIsOr,
        List<Row> into)
    {
        boolean childIsOr = child instanceof NodeOr;
        boolean childIsAnd = child instanceof NodeAnd;

        write(child, (childIsOr || childIsAnd) && childIsOr != parentIsOr ? depth + 1 : depth, into);
    }
}
