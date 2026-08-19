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
 * The shape an editor wants is the one every filter dialog uses - a row per term, each row after the
 * first carrying the operator that joins it to what came before:
 *
 *     s88 21 is on
 *     AND   BR 628 is at Tunnel
 *     OR    Signal 4 is green
 *
 * The two are the same thing as long as the tree is RIGHT-nested, which is exactly what
 * NodeExpression.normalize produces: the row list above is AND(a, OR(b, c)), not OR(AND(a, b), c).
 * Reading it as "each row joins to everything below it" is therefore correct, and reading it
 * left-to-right like arithmetic is not. Rebuilding from rows nests the same way round, so a
 * tree that came from rows and a tree that went into them are the same tree.
 *
 * Not every tree is a row list. A NodeGroup holding a real bracket, or a hand-written JSON whose
 * shape normalize did not produce, says something a flat list cannot - so {@link #of} answers null and
 * the caller keeps the text editor for that expression rather than showing something that is nearly
 * right. Silently flattening would change what a route does the next time anybody pressed Save.
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

        NodeExpression at = expression;

        while (true)
        {
            if (at instanceof NodeRouteCommand)
            {
                // The last term joins to nothing after it
                rows.add(new Row(null, ((NodeRouteCommand) at).getRouteCommand()));

                return rows;
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
                return null;
            }

            // The left of a normalized node is a single term.  Anything else - a nested operator, a
            // group - is a bracket, and brackets are what rows cannot express.
            if (!(left instanceof NodeRouteCommand)) return null;

            rows.add(new Row(joiner, ((NodeRouteCommand) left).getRouteCommand()));

            at = right;
        }
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
