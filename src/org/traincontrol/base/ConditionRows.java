package org.traincontrol.base;

import java.util.ArrayList;
import java.util.List;

/**
 * A condition expression as a list of rows, which is what an editor can show and a person can edit.
 *
 * The model keeps conditions as a TREE - NodeAnd and NodeOr with two children each, NodeGroup for a
 * bracket, NodeRouteCommand at the leaves. That is the right shape to evaluate and the wrong shape to
 * put in front of somebody: nothing about "AND(a, OR(b, c))" says what a railway does.
 *
 * The shape an editor wants is the one every filter dialog uses - a row per term, each row carrying
 * the operator that joins it to what comes BELOW it, so the last row carries nothing:
 *
 *     AND   s88 21 is on
 *     OR    BR 628 is at Tunnel
 *           Signal 4 is green
 *
 * That reads as AND(a, OR(b, c)), not OR(AND(a, b), c): each row joins to everything below it, rather
 * than left to right like arithmetic. Rebuilding from rows nests the same way round, so a tree taken
 * apart into rows and put back together is the tree it started as.
 *
 * Trees arrive nested BOTH ways. The text parser builds right-nested, from its operator stack; but
 * NodeExpression.fromList builds LEFT-nested, and that is the path the Central Station importer takes,
 * so an imported route with three or more conditions is AND(AND(a, b), c). Refusing those meant the
 * editor was unavailable for the most ordinary condition there is. Since AND and OR are associative, a
 * chain of one operator means the same thing whichever way it leans, and {@link #of} accepts either.
 *
 * A chain of MIXED operators leaning left is a different matter: OR(AND(a, b), c) is a bracket, it is
 * not equal to AND(a, OR(b, c)), and no row list says it. Those still answer null, as does a NodeGroup
 * holding a real bracket - the caller keeps the text editor for that expression rather than showing
 * something that is nearly right. Silently flattening would change what a route does the next time
 * anybody pressed Save.
 */
public final class ConditionRows
{
    /**
     * How a row joins to the rest of the expression.
     */
    public enum Joiner
    {
        AND,
        OR
    }

    /**
     * One term, and the operator joining it to what follows.
     */
    public static final class Row
    {
        private final Joiner joiner;
        private final RouteCommand command;

        public Row(Joiner joiner, RouteCommand command)
        {
            this.joiner = joiner;
            this.command = command;
        }

        /**
         * @return how this row joins to the rows BELOW it, or null on the last row, which joins to
         *         nothing
         */
        public Joiner getJoiner()
        {
            return joiner;
        }

        public RouteCommand getCommand()
        {
            return command;
        }

        @Override
        public String toString()
        {
            return (joiner == null ? "" : joiner + " ") + command;
        }
    }

    private ConditionRows()
    {
    }

    /**
     * The rows behind an expression, or null when it says something rows cannot.
     *
     * @param expression the condition tree, or null for no conditions
     * @return the rows, an empty list for no conditions, or null when the expression must keep the
     *         text editor
     */
    public static List<Row> of(NodeExpression expression)
    {
        List<Row> rows = new ArrayList<>();

        if (expression == null) return rows;

        return flatten(expression, rows) ? rows : null;
    }

    /**
     * Walks a tree into rows, whichever way it leans, answering false when it says something rows do
     * not.
     *
     * The right-hand side may hold anything a row list can express, because that is where the nesting
     * a row list means lives. The LEFT-hand side may only be a chain of the same operator: a chain
     * leaning the other way is the same expression, since AND and OR are associative, but a mixed one
     * is a bracket and gets refused.
     */
    private static boolean flatten(NodeExpression at, List<Row> rows)
    {
        if (at instanceof NodeRouteCommand)
        {
            // The last term joins to nothing after it
            rows.add(new Row(null, ((NodeRouteCommand) at).getRouteCommand()));

            return true;
        }

        NodeExpression left;
        NodeExpression right;
        Joiner joiner;

        if (at instanceof NodeAnd)
        {
            left = ((NodeAnd) at).getLeft();
            right = ((NodeAnd) at).getRight();
            joiner = Joiner.AND;
        }
        else if (at instanceof NodeOr)
        {
            left = ((NodeOr) at).getLeft();
            right = ((NodeOr) at).getRight();
            joiner = Joiner.OR;
        }
        else
        {
            // A NodeGroup, or anything else: a bracket says something a flat list does not
            return false;
        }

        int from = rows.size();

        if (!flatten(left, rows)) return false;

        // Everything the left produced has to be joined by THIS operator, or the shape is a bracket
        for (int i = from; i < rows.size() - 1; i++)
        {
            if (rows.get(i).getJoiner() != joiner) return false;
        }

        // Its last term is what joins to the right-hand side
        int last = rows.size() - 1;

        rows.set(last, new Row(joiner, rows.get(last).getCommand()));

        return flatten(right, rows);
    }

    /**
     * The expression a list of rows means.
     *
     * Right-nested, matching what normalize produces, so that a tree taken apart by {@link #of} and put
     * back together here is the tree it started as.
     *
     * @param rows the rows, in the order they are shown
     * @return the expression, or null when there are no rows
     */
    public static NodeExpression toExpression(List<Row> rows)
    {
        if (rows == null || rows.isEmpty()) return null;

        // Built from the back, because the list is right-nested: the last row is the innermost term and
        // every row above wraps what is below it.
        NodeExpression built = new NodeRouteCommand(rows.get(rows.size() - 1).getCommand());

        for (int i = rows.size() - 2; i >= 0; i--)
        {
            Row row = rows.get(i);

            NodeExpression term = new NodeRouteCommand(row.getCommand());

            // A missing joiner on a row that is not the last one means AND, which is what a list of
            // conditions means when nobody has said otherwise
            built = row.getJoiner() == Joiner.OR
                ? new NodeOr(term, built)
                : new NodeAnd(term, built);
        }

        return built;
    }
}
