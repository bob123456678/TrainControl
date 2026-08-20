package org.traincontrol.base;

import java.util.ArrayList;
import java.util.List;

/**
 * A route's conditions as an indented list, and the expression that list means.
 *
 * The conditions are a tree - this and that, or the other - and every attempt to show a tree as a
 * flat list of rows has to put the shape somewhere. Writing it beside the list as algebra was one
 * answer, and the trouble with it was that the algebra referred to the rows by letters: a handle you
 * have to hold in your head while looking away from the thing it names.
 *
 * An outline puts the shape in the list itself. Indentation is nesting, which everybody already reads
 * that way from every list application there has ever been, and each row is its own condition rather
 * than a letter standing for one.
 *
 * Two rules, and between them they say everything:
 *
 * <ul>
 * <li>Each row carries the word joining it to the row before it at its own level.
 * <li>A run of rows joined by the same word is a group. A change of word starts a new group, and that
 *     word is what joins the two groups together.
 * </ul>
 *
 * So "A, or B, and C, or D" is (A or B) and (C or D) - which is what those words mean read left to
 * right, and what "A and B or C" already means in every language that has both. Indenting a row nests
 * it and everything indented with it beneath what came before.
 */
public final class ConditionOutline
{
    /**
     * How a row attaches to the one before it.
     */
    public enum Joiner
    {
        AND,
        OR
    }

    /**
     * One line of the outline: a condition, how deep it sits, and what joins it to its neighbour.
     */
    public static final class Row
    {
        private final int depth;
        private final Joiner joiner;
        private final RouteCommand command;

        /**
         * @param depth how far indented, from zero
         * @param joiner what joins it to the row before at its level, ignored on the first
         * @param command the condition itself
         */
        public Row(int depth, Joiner joiner, RouteCommand command)
        {
            this.depth = Math.max(0, depth);
            this.joiner = joiner == null ? Joiner.AND : joiner;
            this.command = command;
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
         * The same row at a different depth, for indenting and outdenting.
         *
         * @param to the new depth
         * @return a new row
         */
        public Row atDepth(int to)
        {
            return new Row(to, joiner, command);
        }

        /**
         * The same row joined a different way.
         *
         * @param how the new joiner
         * @return a new row
         */
        public Row joinedBy(Joiner how)
        {
            return new Row(depth, how, command);
        }
    }

    private ConditionOutline()
    {
    }

    /**
     * The expression an outline means.
     *
     * @param rows the outline, in order
     * @return the expression, or null when there are no rows - a route with no conditions
     */
    public static NodeExpression toExpression(List<Row> rows)
    {
        if (rows == null || rows.isEmpty()) return null;

        int[] at = new int[]{0};

        return read(rows, at, rows.get(0).getDepth());
    }

    /**
     * Reads every row at or below a depth, stopping when the outline comes back out.
     */
    private static NodeExpression read(List<Row> rows, int[] at, int depth)
    {
        List<NodeExpression> nodes = new ArrayList<>();
        List<Joiner> joiners = new ArrayList<>();

        while (at[0] < rows.size() && rows.get(at[0]).getDepth() >= depth)
        {
            Row row = rows.get(at[0]);

            if (row.getDepth() == depth)
            {
                nodes.add(new NodeRouteCommand(row.getCommand()));
                joiners.add(row.getJoiner());

                at[0]++;
            }
            else
            {
                // A deeper run: the whole of it is one thing, joined to what came before by the word
                // on its first row.  That is what indenting a row means - it and its indented
                // neighbours become a unit.
                joiners.add(row.getJoiner());

                NodeExpression inside = read(rows, at, row.getDepth());

                nodes.add(group(inside));
            }
        }

        return combine(nodes, joiners);
    }

    /**
     * Puts a run together: groups of one word, joined to each other by the words between them.
     */
    private static NodeExpression combine(List<NodeExpression> nodes, List<Joiner> joiners)
    {
        if (nodes.isEmpty()) return null;

        // Each run is a list of nodes sharing one word.  The word that STARTS a run is the one that
        // joins it to the run before, and takes no part in the run's own joining - which is what
        // makes "A or B and C or D" two groups rather than an argument about precedence.
        List<List<NodeExpression>> runs = new ArrayList<>();
        List<Joiner> within = new ArrayList<>();
        List<Joiner> between = new ArrayList<>();

        List<NodeExpression> run = new ArrayList<>();
        run.add(nodes.get(0));

        Joiner runJoiner = null;

        for (int at = 1; at < nodes.size(); at++)
        {
            Joiner joiner = joiners.get(at);

            if (runJoiner == null || joiner == runJoiner)
            {
                runJoiner = joiner;
                run.add(nodes.get(at));
            }
            else
            {
                runs.add(run);
                within.add(runJoiner);
                between.add(joiner);

                run = new ArrayList<>();
                run.add(nodes.get(at));

                runJoiner = null;
            }
        }

        runs.add(run);
        within.add(runJoiner == null ? Joiner.AND : runJoiner);

        // Each run folded with its own word, and bracketed when there is more than one run - the
        // brackets are what the reader would have had to write, and they survive being saved
        List<NodeExpression> folded = new ArrayList<>();

        for (int at = 0; at < runs.size(); at++)
        {
            NodeExpression one = fold(runs.get(at), within.get(at));

            folded.add(runs.size() > 1 && runs.get(at).size() > 1 ? group(one) : one);
        }

        NodeExpression out = folded.get(0);

        for (int at = 1; at < folded.size(); at++)
        {
            out = between.get(at - 1) == Joiner.OR
                ? new NodeOr(out, folded.get(at)) : new NodeAnd(out, folded.get(at));
        }

        return out;
    }

    private static NodeExpression fold(List<NodeExpression> run, Joiner joiner)
    {
        NodeExpression out = run.get(0);

        for (int at = 1; at < run.size(); at++)
        {
            out = joiner == Joiner.OR ? new NodeOr(out, run.get(at)) : new NodeAnd(out, run.get(at));
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
     * @return the rows
     */
    public static List<Row> of(NodeExpression expression)
    {
        List<Row> out = new ArrayList<>();

        write(expression, 0, Joiner.AND, out);

        return out;
    }

    /**
     * Walks the tree, flattening runs of one word and indenting anything bracketed.
     *
     * @param node where in the tree
     * @param depth how far in
     * @param joining what joins this node to what came before
     * @param into the rows so far
     */
    private static void write(NodeExpression node, int depth, Joiner joining, List<Row> into)
    {
        if (node == null) return;

        if (node instanceof NodeRouteCommand)
        {
            into.add(new Row(depth, joining, ((NodeRouteCommand) node).getRouteCommand()));

            return;
        }

        if (node instanceof NodeGroup)
        {
            List<NodeExpression> inside = ((NodeGroup) node).getExpressions();

            // A bracket around a single term is a bracket around nothing, and indenting it would
            // suggest a nesting the reader did not write
            if (inside.size() == 1 && inside.get(0) instanceof NodeRouteCommand)
            {
                write(inside.get(0), depth, joining, into);

                return;
            }

            for (int at = 0; at < inside.size(); at++)
            {
                write(inside.get(at), depth + 1, at == 0 ? joining : Joiner.AND, into);
            }

            return;
        }

        boolean or = node instanceof NodeOr;

        NodeExpression left = or ? ((NodeOr) node).getLeft() : ((NodeAnd) node).getLeft();
        NodeExpression right = or ? ((NodeOr) node).getRight() : ((NodeAnd) node).getRight();

        write(left, depth, joining, into);
        write(right, depth, or ? Joiner.OR : Joiner.AND, into);
    }
}
