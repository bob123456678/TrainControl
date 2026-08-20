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

        return read(rows, at, rows.get(0).getDepth());
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
                // A deeper run is one thing at this level, and the whole of it is consumed here
                NodeExpression inside = read(rows, at, row.getDepth());

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

        write(left, depth, into);

        into.add(Row.joining(depth, or ? Joiner.OR : Joiner.AND));

        write(right, depth, into);
    }
}
