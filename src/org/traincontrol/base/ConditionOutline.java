package org.traincontrol.base;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A route's conditions as an indented list, and the expression that list means.
 *
 * The conditions are a tree, and an outline puts its shape in the list itself rather than in algebra beside it.  A line
 * is either a condition or the word joining what is on either side of it, and BOTH can be indented:
 *
 * <pre>
 *     Sensor 1 occupied
 * and
 *         Sensor 2 occupied
 *     or
 *         Sensor 3 occupied
 * </pre>
 *
 * The indented "or" joins the two indented conditions; the "and" at the outer level joins sensor 1 to that whole group:
 * "1 and (2 or 3)", read from where each word sits, without a precedence rule or a bracket.
 *
 * The one rule: EVERY WORD AT A LEVEL MUST BE THE SAME WORD.  "and" and "or" side by side at one level is a sentence
 * with two meanings; the class says so (`problems()`, drawn red by the editor) and lets the reader indent the part they
 * meant to group.
 */
public final class ConditionOutline
{
    /** How two things are joined. */
    public enum Joiner
    {
        AND,
        OR
    }

    /** Why a line is flagged.  The two are put right differently, so the editor says which. */
    public enum Problem
    {
        /** A word that differs from the word already joining its level: the sentence has two meanings. */
        DISAGREES,

        /** A word deeper than a condition beside it: it joins nothing at its own depth. */
        JOINS_NOTHING
    }

    /** One line: either a condition, or the word joining what is either side of it. */
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

        /** A condition line. */
        public static Row condition(int depth, RouteCommand command)
        {
            return new Row(depth, null, command);
        }

        /** A joining line. */
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

        /** The same line, indented differently. */
        public Row atDepth(int to)
        {
            return new Row(to, joiner, command);
        }

        /** The same joining line, with a different word. */
        public Row joinedBy(Joiner how)
        {
            return isJoiner() ? joining(depth, how) : this;
        }

        /** The same line, about a different condition. */
        public Row about(RouteCommand what)
        {
            return isJoiner() ? this : condition(depth, what);
        }
    }

    private ConditionOutline()
    {
    }

    /**
     * The lines whose word disagrees with the rest of its level.  The editor shows them in red rather than choosing;
     * indenting one of the conditions settles it.
     *
     * The FIRST word at a level is taken as the one meant.  A word deeper than a condition beside it is flagged too: it
     * joins nothing at its own depth.  That is a guess about which is the mistake, but it marks one line rather than
     * all of them and is stable as more are added.
     *
     * @return the indices of the lines to flag, empty when the outline reads
     */
    public static Set<Integer> problems(List<Row> rows)
    {
        return new LinkedHashSet<>(whatIsWrong(rows).keySet());
    }

    /**
     * The lines `problems` flags, each with the reason (Adam: "a warning triangle icon with a tooltip explaining what's
     * wrong").  A word deeper than a condition beside it is reported as joining nothing even where it also differs from
     * its level's word: indenting a condition, the remedy for a disagreement, does not help it.
     *
     * @return line index to reason, in the order the lines are found; empty when the outline reads
     */
    public static java.util.Map<Integer, Problem> whatIsWrong(List<Row> rows)
    {
        java.util.Map<Integer, Problem> out = new java.util.LinkedHashMap<>();

        if (rows == null) return out;

        // The word first seen in the RUN currently open at each depth, not at each depth across the whole list: `read`
        // consumes each indented run in its own recursive call, so two runs at one depth ("A and (B or C) and (D and
        // E)") are unambiguous to it, and a stricter check would refuse a save the reader parses perfectly.  What still
        // holds is the class's rule: two different words SIDE BY SIDE at one level.  A joiner at a shallower depth
        // closes every run deeper than it.
        java.util.Map<Integer, Joiner> settled = new java.util.LinkedHashMap<>();

        for (int at = 0; at < rows.size(); at++)
        {
            Row row = rows.get(at);

            if (!row.isJoiner()) continue;

            // Everything deeper than this word belonged to a group this word has just ended.
            for (java.util.Iterator<Integer> deeper = settled.keySet().iterator(); deeper.hasNext();)
            {
                if (deeper.next() > row.getDepth()) deeper.remove();
            }

            Joiner already = settled.get(row.getDepth());

            if (already == null) settled.put(row.getDepth(), row.getJoiner());
            else if (already != row.getJoiner()) out.put(at, Problem.DISAGREES);
        }

        // A WORD DEEPER THAN A CONDITION BESIDE IT JOINS NOTHING (Adam: "the first or is read as an and here").  To
        // `read` it is a group holding a word and no condition, so the word vanished and the level's other word joined
        // everything, with no two words side by side to flag.  Shallower than both neighbors is fine: that is how two
        // groups are joined.
        for (int at = 1; at + 1 < rows.size(); at++)
        {
            Row row = rows.get(at);

            if (!row.isJoiner()) continue;

            if (row.getDepth() > rows.get(at - 1).getDepth() || row.getDepth() > rows.get(at + 1).getDepth())
            {
                out.put(at, Problem.JOINS_NOTHING);
            }
        }

        return out;
    }

    /**
     * The expression an outline means.  Where a level disagrees with itself the first word at that level is used: the
     * editor refuses to save such an outline, so this only has to be defined rather than right.
     *
     * @return the expression, or null when there is nothing in it
     */
    public static NodeExpression toExpression(List<Row> rows)
    {
        if (rows == null || rows.isEmpty()) return null;

        int[] at = new int[]{0};

        // The OUTERMOST level in the outline, not the depth of the first line: a condition beginning with a bracketed
        // group, "(A or B) and C", opens with its first row one level in, and starting from that depth made read() stop
        // at the "and" and silently drop everything after it, with nothing flagged red.
        int outermost = rows.get(0).getDepth();

        for (Row row : rows) outermost = Math.min(outermost, row.getDepth());

        return read(rows, at, outermost);
    }

    /** Reads every line at or below a depth, stopping when the outline comes back out. */
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
                // AT depth + 1, NOT AT THE ROW'S OWN DEPTH.  The two differ when the step is two: "3 or ((1 or 2) and
                // 4)" writes the OR at 0, the AND at 1 and the bracket at 2, and because the bracket is the AND's LEFT
                // child the reader meets 0 then 2 with no depth-1 row yet to anchor on.  Reading that run at 2 made it
                // a sibling of the 3 and dropped the AND, silently turning it into an OR.  At depth + 1 the run is the
                // first item of the next level down, and the AND that follows joins it.
                //
                // The step is two whenever a cross-operator child is itself the left child of a cross-operator parent
                // (`writeChild` bumps each of them and the left spine is written first), which a NodeGroup is one way
                // to reach: `Or(And(Or(1,2), 4), 3)` has no group and also needs it.  One alternation is safe; two in a
                // row is not.
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

    /** An existing condition as an outline, so a route made before this opens as one. */
    public static List<Row> of(NodeExpression expression)
    {
        List<Row> out = new ArrayList<>();

        write(expression, 0, out);

        return out;
    }

    /**
     * The outline with one condition moved one place up or down.
     *
     * Not by moving the LINE: the joining words are lines of their own, so lifting a condition one line puts it next to
     * the word above rather than past it, and a word with nothing on one side is thrown away when the outline is
     * straightened ("A and B" with B moved up came back as "A B", a change to when the route fires).  So within a level
     * the two conditions trade places and every word and level stays where it was: "A and (B or C)" with C moved up is
     * "A and (C or B)".
     *
     * A condition whose neighbor at its own level is a whole group moves past the group instead (FR-144): "(A or B or
     * C) and D" with D moved up is "D and (A or B or C)", every line keeping its level.
     *
     * A condition at the edge of its group, where the word beside it is shallower, crosses into the neighboring group
     * at its own level (OB-379): "(A and B) or (C and D)" with C moved up is "(A and B and C) or (D)", joined by that
     * group's word, and the group it left loses it and one word.  With no group at its level across the edge it does
     * not move: outdenting is how a condition leaves a group.
     *
     * @param by -1 for up, 1 for down
     * @return a new list in the new order, or a copy unchanged where there is nowhere to go
     */
    public static List<Row> moved(List<Row> rows, int line, int by)
    {
        Move move = plan(rows, line, by);

        if (move != null) return move.rows;

        return rows == null ? new ArrayList<Row>() : new ArrayList<>(rows);
    }

    /**
     * Whether a move that way changes anything.  The editor draws the arrow only then: an arrow that does nothing reads
     * as the feature being broken.
     */
    public static boolean canMove(List<Row> rows, int line, int by)
    {
        return plan(rows, line, by) != null;
    }

    /**
     * Where the condition a move takes ends up, so the editor can keep it selected: its line after moved(), or the line
     * itself.
     */
    public static int landing(List<Row> rows, int line, int by)
    {
        Move move = plan(rows, line, by);

        return move == null ? line : move.landing;
    }

    /** A move worked out once, so `moved`, `canMove` and `landing` cannot disagree. */
    private static final class Move
    {
        final List<Row> rows;
        final int landing;

        Move(List<Row> rows, int landing)
        {
            this.rows = rows;
            this.landing = landing;
        }
    }

    /** The outline after moving one condition, and its new line; null where it has nowhere to go. */
    private static Move plan(List<Row> rows, int line, int by)
    {
        if (rows == null || line < 0 || line >= rows.size() || by == 0 || rows.get(line).isJoiner()) return null;

        by = by < 0 ? -1 : 1;

        int edge = line + by;

        if (edge >= 0 && edge < rows.size() && rows.get(edge).isJoiner()
            && rows.get(edge).getDepth() < rows.get(line).getDepth())
        {
            return intoTheNextGroup(rows, line, by);
        }

        List<Row> out = new ArrayList<>(rows);

        int[] group = groupBeside(out, line, by);

        if (group != null)
        {
            List<Row> before = new ArrayList<>(out.subList(0, Math.min(line, group[0])));
            List<Row> moving = new ArrayList<>(out.subList(group[0], group[1] + 1));
            Row word = out.get(line + by);
            Row condition = out.get(line);
            List<Row> after = new ArrayList<>(out.subList(Math.max(line, group[1]) + 1, out.size()));

            out.clear();
            out.addAll(before);

            if (by < 0)
            {
                out.add(condition);
                out.add(word);
                out.addAll(moving);
            }
            else
            {
                out.addAll(moving);
                out.add(word);
                out.add(condition);
            }

            out.addAll(after);

            return new Move(out, by < 0 ? group[0] : group[1]);
        }

        int to = destination(out, line, by);

        if (to < 0) return null;

        RouteCommand moving = out.get(line).getCommand();

        out.set(line, out.get(line).about(out.get(to).getCommand()));
        out.set(to, out.get(to).about(moving));

        return new Move(out, to);
    }

    /**
     * A condition at its group's edge joins the group past the shallower word beside it, at that group's near end.  The
     * word it leaves with is its own level's; a condition alone at its level leaves with the deeper of the two words
     * round it, as that is the one left joining nothing.
     */
    private static Move intoTheNextGroup(List<Row> rows, int line, int by)
    {
        int depth = rows.get(line).getDepth();
        int edge = line + by;
        int near = edge + by;

        if (near < 0 || near >= rows.size() || rows.get(near).getDepth() < depth) return null;

        int far = near;

        while (far + by >= 0 && far + by < rows.size() && rows.get(far + by).getDepth() >= depth) far += by;

        int first = Math.min(near, far);
        int last = Math.max(near, far);

        Joiner word = null;
        boolean atThisLevel = false;

        for (int at = first; at <= last; at++)
        {
            if (rows.get(at).getDepth() != depth) continue;

            atThisLevel = true;

            if (rows.get(at).isJoiner() && word == null) word = rows.get(at).getJoiner();
        }

        if (!atThisLevel) return null;

        int behind = line - by;
        boolean anything = behind >= 0 && behind < rows.size();

        if (anything && !rows.get(behind).isJoiner()) return null;

        Row own = anything && rows.get(behind).getDepth() == depth ? rows.get(behind) : null;

        // A lone condition across the edge has no word of its own yet: it takes the one this condition brings.
        if (word == null)
        {
            if (first != last || rows.get(first).isJoiner() || own == null) return null;

            word = own.getJoiner();
        }

        int drop = !anything ? edge
            : own != null || rows.get(behind).getDepth() >= rows.get(edge).getDepth() ? behind : edge;

        List<Row> out = new ArrayList<>(rows);
        Row moving = rows.get(line);
        int at = by < 0 ? edge : edge + 1;

        for (int gone : new int[]{Math.max(line, drop), Math.min(line, drop)})
        {
            out.remove(gone);

            if (gone < at) at--;
        }

        if (by < 0)
        {
            out.add(at, Row.joining(depth, word));
            out.add(at + 1, moving);

            return new Move(out, at + 1);
        }

        out.add(at, moving);
        out.add(at + 1, Row.joining(depth, word));

        return new Move(out, at);
    }

    /**
     * The outline with a new condition above or below a line, at that line's level and joined by its level's word (AND
     * where the level has none yet), as the editor's Insert Condition makes one (FR-147).
     *
     * @param by -1 for above, 1 for below
     * @return a new list, or a copy unchanged where the line is not a condition
     */
    public static List<Row> inserted(List<Row> rows, int line, int by, RouteCommand command)
    {
        List<Row> out = rows == null ? new ArrayList<Row>() : new ArrayList<>(rows);

        if (line < 0 || line >= out.size() || out.get(line).isJoiner() || by == 0) return out;

        int depth = out.get(line).getDepth();
        Row word = Row.joining(depth, wordOfTheLevel(out, line));
        Row fresh = Row.condition(depth, command);

        if (by < 0)
        {
            out.add(line, word);
            out.add(line, fresh);
        }
        else
        {
            out.add(line + 1, fresh);
            out.add(line + 1, word);
        }

        return out;
    }

    /** The first word at a line's own level in the group it is in, which `whatIsWrong` takes as the one meant. */
    private static Joiner wordOfTheLevel(List<Row> rows, int line)
    {
        int depth = rows.get(line).getDepth();
        int start = line;

        while (start > 0 && rows.get(start - 1).getDepth() >= depth) start--;

        for (int at = start; at < rows.size() && rows.get(at).getDepth() >= depth; at++)
        {
            if (rows.get(at).isJoiner() && rows.get(at).getDepth() == depth) return rows.get(at).getJoiner();
        }

        return Joiner.AND;
    }

    /**
     * The group a condition would move past: the word beside it is at its own level, and the term past that word is
     * nested deeper.  Answers {first, last} of the group's lines, or null where the neighbor is a single condition or
     * nothing.
     */
    private static int[] groupBeside(List<Row> rows, int line, int by)
    {
        if (rows == null || line < 0 || line >= rows.size() || by == 0 || rows.get(line).isJoiner()) return null;

        int depth = rows.get(line).getDepth();
        int word = line + by;
        int start = word + by;

        if (start < 0 || start >= rows.size() || !rows.get(word).isJoiner() || rows.get(word).getDepth() != depth)
        {
            return null;
        }

        if (rows.get(start).getDepth() <= depth) return null;

        int end = start;

        while (end + by >= 0 && end + by < rows.size() && rows.get(end + by).getDepth() > depth) end += by;

        return new int[]{Math.min(start, end), Math.max(start, end)};
    }

    /**
     * Which row a move would land on, or -1 for none: the nearest condition that way, skipping the words in between.
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

    /** Walks the tree, flattening runs of one word and indenting anything bracketed. */
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

            // A bracket round a single condition is a bracket round nothing; indenting it would suggest a nesting not
            // written
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
     * A child that joins its own pair with the OTHER word goes a level deeper.  A level is one word, so writing every
     * child at the parent's depth flattened "A and (B or C)" into one level reading "A and B or C", which is refused by
     * this class's own rule on save and parses back as "(A and B) or C".  A child joined by the SAME word stays flat,
     * so "A and B and C" is one list rather than a staircase.
     */
    private static void writeChild(NodeExpression child, int depth, boolean parentIsOr,
        List<Row> into)
    {
        boolean childIsOr = child instanceof NodeOr;
        boolean childIsAnd = child instanceof NodeAnd;

        write(child, (childIsOr || childIsAnd) && childIsOr != parentIsOr ? depth + 1 : depth, into);
    }
}
