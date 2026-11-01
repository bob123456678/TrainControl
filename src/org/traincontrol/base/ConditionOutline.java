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
 * with two meanings; the class says so (`problems()`, drawn red by the editor) and the reader settles it by choosing
 * the group's rule.
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
     * choosing the group's rule settles it.
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
     * its level's word: choosing the group's rule, the remedy for a disagreement, does not help it.
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
     * straightened.  So within a level the two conditions trade places and every word and level stays where it was:
     * "A and (B or C)" with C moved up is "A and (C or B)".
     *
     * A condition whose neighbor at its own level is a whole group moves past the group instead (FR-144): "(A or B or
     * C) and D" with D moved up is "D and (A or B or C)", every line keeping its level.
     *
     * A condition at the edge of its group, where the word beside it is shallower, crosses into the neighboring group
     * at its own level (OB-379): "(A and B) or (C and D)" with C moved up is "(A and B and C) or (D)".  With no group
     * at its level across the edge it does not move: a drag is how a condition leaves a group.
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

    /** Whether a move that way changes anything. */
    public static boolean canMove(List<Row> rows, int line, int by)
    {
        return plan(rows, line, by) != null;
    }

    /**
     * Moves several conditions one place together (MT-868): each by the rule of `moved`, the one leading the way first,
     * so a run keeps its order.  Where the leading one has nowhere to go, none moves.  A selection holding a whole
     * nested group moves by pieces instead, as a drag does: each condition or whole group one item along its own group,
     * so no group merges into its neighbor.
     *
     * @param chosen line indices of the conditions; words among them are ignored
     * @param by -1 for up, 1 for down
     * @return a new list, or null where nothing moves
     */
    public static List<Row> movedTogether(List<Row> rows, java.util.Collection<Integer> chosen, int by)
    {
        if (rows == null || chosen == null || by == 0) return null;

        Group top = groupsOf(rows);
        List<Object> blocks = new ArrayList<>();

        if (top != null) blocks(top, new java.util.HashSet<>(chosen), blocks);

        for (Object block : blocks)
        {
            if (block instanceof Group) return movedByPieces(top, blocks, by);
        }

        List<RouteCommand> moving = new ArrayList<>();

        for (int at : new java.util.TreeSet<>(chosen))
        {
            if (at >= 0 && at < rows.size() && !rows.get(at).isJoiner()) moving.add(rows.get(at).getCommand());
        }

        if (by > 0) java.util.Collections.reverse(moving);

        List<Row> out = rows;

        for (RouteCommand command : moving)
        {
            Move move = plan(out, lineHolding(out, command), by);

            if (move == null && out == rows) return null;

            if (move != null) out = move.rows;
        }

        return out == rows ? null : out;
    }

    /** Each piece one item along its own group, the leading one first; null where the leading one is at the end. */
    private static List<Row> movedByPieces(Group top, List<Object> blocks, int by)
    {
        List<Object> order = new ArrayList<>(blocks);

        if (by > 0) java.util.Collections.reverse(order);

        boolean moved = false;

        for (Object block : order)
        {
            Group from = block instanceof Group ? ((Group) block).parent : holderOf(top, block);
            List<Object> items = itemsOf(from);
            int next = items.indexOf(block) + (by < 0 ? -1 : 1);

            if (next < 0 || next >= items.size() || order.contains(items.get(next)))
            {
                if (!moved) return null;

                continue;
            }

            java.util.Collections.swap(from.elements, from.elements.indexOf(block), from.elements.indexOf(items.get(next)));
            moved = true;
        }

        return lines(top);
    }

    /**
     * The outline less these conditions: each, or each whole group of them, goes with one word of its own group, and a
     * group left empty goes too, so no other group's word changes.
     *
     * @param conditions line indices of the conditions; words among them are ignored
     * @return a new list
     */
    public static List<Row> removed(List<Row> rows, Set<Integer> conditions)
    {
        Group top = groupsOf(rows);

        if (top == null || conditions == null || conditions.isEmpty())
        {
            return rows == null ? new ArrayList<Row>() : new ArrayList<>(rows);
        }

        List<Object> pieces = new ArrayList<>();

        blocks(top, conditions, pieces);

        for (Object piece : pieces)
        {
            // A condition standing before a group whose first line is a word, as a stored route can have it (FR-147):
            // that word is the condition's own, and the word before it joins what stays.
            Group from = piece instanceof Group ? null : holderOf(top, piece);
            int at = from == null ? -1 : from.elements.indexOf(piece);
            Object next = at >= 0 && at + 1 < from.elements.size() ? from.elements.get(at + 1) : null;

            if (next instanceof Group && !((Group) next).elements.isEmpty() && isWord(((Group) next).elements.get(0)))
            {
                ((Group) next).elements.remove(0);
                from.elements.remove(at);
            }
            else
            {
                cutOut(top, piece);
            }
        }

        // A word with nothing before or after it joins nothing, as an outline that was already flagged can leave one.
        List<Row> out = lines(top);

        while (!out.isEmpty() && out.get(0).isJoiner()) out.remove(0);

        while (!out.isEmpty() && out.get(out.size() - 1).isJoiner()) out.remove(out.size() - 1);

        return out;
    }

    /**
     * Takes a piece out with one word beside it, the one before first, and a group left with no condition after it.  A
     * group whose first line is a word goes with that word alone: it joined the group to what stands before it.
     */
    private static void cutOut(Group top, Object piece)
    {
        boolean carriesItsWord = piece instanceof Group && !((Group) piece).elements.isEmpty()
            && isWord(((Group) piece).elements.get(0));

        Group from = pullOut(top, piece, !carriesItsWord);

        if (from != null && from.parent != null && !from.holdsACondition()) cutOut(top, from);
    }

    /** The line holding this very condition, by identity: two lines can ask the same thing; -1 where none does. */
    private static int lineHolding(List<Row> rows, RouteCommand command)
    {
        for (int at = 0; at < rows.size(); at++)
        {
            if (rows.get(at).getCommand() == command) return at;
        }

        return -1;
    }

    /** Where the condition a move takes ends up, so the editor can keep it selected. */
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
     * A condition at its group's edge joins the group past the shallower word beside it, at that group's near end,
     * leaving with its own level's word; a condition alone at its level leaves with the deeper of the two words.
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
     * The outline with a new condition above or below a line, at that line's level and joined by its level's word
     * (FR-147).  On a word line it goes at the word's own level, joined by that word (FR-150), above and below alike.
     *
     * @param by -1 for above, 1 for below
     * @return a new list, or a copy unchanged where the line is not in the outline
     */
    public static List<Row> inserted(List<Row> rows, int line, int by, RouteCommand command)
    {
        List<Row> out = rows == null ? new ArrayList<Row>() : new ArrayList<>(rows);

        if (line < 0 || line >= out.size() || by == 0) return out;

        int depth = out.get(line).getDepth();

        if (out.get(line).isJoiner())
        {
            out.add(line + 1, Row.joining(depth, out.get(line).getJoiner()));
            out.add(line + 1, Row.condition(depth, command));

            return out;
        }
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

    /**
     * The first word at a line's own level in its group, which `whatIsWrong` takes as the one meant; AND where the
     * level has none yet.
     */
    public static Joiner wordOfTheLevel(List<Row> rows, int line)
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
     * The lines of `after` an edit changed from `before` (FR-153): those outside the longest run the two share, a word
     * counting for more than a condition so that two traded conditions show rather than a word between them.  With
     * nothing new, as after a delete, the first line that differs, or the last where the end went.
     *
     * @return line indices into `after`; empty where the two are the same
     */
    public static Set<Integer> changed(List<Row> before, List<Row> after)
    {
        Set<Integer> out = new LinkedHashSet<>();

        if (after == null || after.isEmpty()) return out;

        List<Row> was = before == null ? new ArrayList<Row>() : before;
        int n = was.size();
        int m = after.size();

        // best[i][j]: the most that the lines from i of `was` and from j of `after` can share
        int[][] best = new int[n + 1][m + 1];

        for (int i = n - 1; i >= 0; i--)
        {
            for (int j = m - 1; j >= 0; j--)
            {
                best[i][j] = Math.max(best[i + 1][j], best[i][j + 1]);

                if (same(was.get(i), after.get(j)))
                {
                    best[i][j] = Math.max(best[i][j], best[i + 1][j + 1] + worth(after.get(j)));
                }
            }
        }

        int i = 0;

        for (int j = 0; j < m;)
        {
            if (i < n && same(was.get(i), after.get(j)) && best[i][j] == best[i + 1][j + 1] + worth(after.get(j)))
            {
                i++;
                j++;
            }
            else if (i < n && best[i][j] == best[i + 1][j]) i++;
            else out.add(j++);
        }

        if (!out.isEmpty() || n == m) return out;

        int at = 0;

        while (at < m && at < n && same(was.get(at), after.get(at))) at++;

        out.add(Math.min(at, m - 1));

        return out;
    }

    /** The same line: the same word, or the very same condition, at the same depth. */
    private static boolean same(Row a, Row b)
    {
        if (a.getDepth() != b.getDepth() || a.isJoiner() != b.isJoiner()) return false;

        return a.isJoiner() ? a.getJoiner() == b.getJoiner() : a.getCommand() == b.getCommand();
    }

    private static int worth(Row row)
    {
        return row.isJoiner() ? 3 : 2;
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

    /**
     * One group of an outline, as `read` finds it: the lines at one depth in one run, with every deeper run inside it
     * a nested group one level down.  The editor draws each as an outline with its word on it (OB-396).
     */
    public static final class Group
    {
        private final int depth;
        private final Group parent;

        /** Its own lines (a `Line`) and its nested groups, in order. */
        private final List<Object> elements = new ArrayList<>();

        private int first;
        private int last;

        private Group(int depth, Group parent)
        {
            this.depth = depth;
            this.parent = parent;
        }

        public int getDepth()
        {
            return depth;
        }

        /** @return the group this one is nested in, or null for the outermost */
        public Group getParent()
        {
            return parent;
        }

        /** @return the first line of the outline it spans */
        public int getFirst()
        {
            return first;
        }

        /** @return the last line of the outline it spans */
        public int getLast()
        {
            return last;
        }

        /**
         * What the group joins, in order: each condition as its line number (an Integer), each nested group that holds a
         * condition as the Group.
         */
        public List<Object> getItems()
        {
            List<Object> out = new ArrayList<>();

            for (Object element : elements)
            {
                if (element instanceof Group)
                {
                    if (((Group) element).holdsACondition()) out.add(element);
                }
                else if (!((Line) element).row.isJoiner())
                {
                    out.add(((Line) element).at);
                }
            }

            return out;
        }

        /** @return the first word at its own level, which `whatIsWrong` takes as the one meant; null for none */
        public Joiner getWord()
        {
            for (Object element : elements)
            {
                if (element instanceof Line && ((Line) element).row.isJoiner()) return ((Line) element).row.getJoiner();
            }

            return null;
        }

        /** @return whether a condition stands anywhere inside it */
        public boolean holdsACondition()
        {
            for (Object element : elements)
            {
                if (element instanceof Group ? ((Group) element).holdsACondition() : !((Line) element).row.isJoiner())
                {
                    return true;
                }
            }

            return false;
        }

        /** @return the condition lines anywhere inside it, in order */
        public List<Integer> getConditions()
        {
            List<Integer> out = new ArrayList<>();

            for (Object element : elements)
            {
                if (element instanceof Group) out.addAll(((Group) element).getConditions());
                else if (!((Line) element).row.isJoiner()) out.add(((Line) element).at);
            }

            return out;
        }

        /** @return whether every line it spans is one of these */
        private boolean within(Set<Integer> lines)
        {
            List<Integer> mine = getConditions();

            return !mine.isEmpty() && lines.containsAll(mine);
        }
    }

    /** A line of the outline inside a Group, with where it stood; -1 for one an edit made. */
    private static final class Line
    {
        final Row row;
        final int at;

        Line(Row row, int at)
        {
            this.row = row;
            this.at = at;
        }
    }

    /**
     * The outline as groups, read as `toExpression` reads it, so the groups drawn are the groups meant.
     *
     * @return the outermost group, or null when there are no lines
     */
    public static Group groupsOf(List<Row> rows)
    {
        if (rows == null || rows.isEmpty()) return null;

        int outermost = rows.get(0).getDepth();

        for (Row row : rows) outermost = Math.min(outermost, row.getDepth());

        return parse(rows, new int[]{0}, outermost, null);
    }

    private static Group parse(List<Row> rows, int[] at, int depth, Group parent)
    {
        Group group = new Group(depth, parent);

        group.first = at[0];

        while (at[0] < rows.size() && rows.get(at[0]).getDepth() >= depth)
        {
            if (rows.get(at[0]).getDepth() == depth)
            {
                group.elements.add(new Line(rows.get(at[0]), at[0]));
                at[0]++;
            }
            else
            {
                group.elements.add(parse(rows, at, depth + 1, group));
            }
        }

        group.last = at[0] - 1;

        return group;
    }

    /** The lines a tree of groups stands for: each nested group one level below the one holding it. */
    private static List<Row> lines(Group top)
    {
        List<Row> out = new ArrayList<>();

        lines(top, top.depth, out);

        return out;
    }

    private static void lines(Group group, int depth, List<Row> into)
    {
        for (Object element : group.elements)
        {
            if (element instanceof Group)
            {
                lines((Group) element, depth + 1, into);
            }
            else
            {
                Row row = ((Line) element).row;

                into.add(row.getDepth() == depth ? row : row.atDepth(depth));
            }
        }
    }

    /**
     * The innermost group holding every one of these lines.
     *
     * @param top the outline's groups, from `groupsOf`
     * @return that group, or null when there is none
     */
    public static Group holding(Group top, Set<Integer> lines)
    {
        if (top == null || lines == null || lines.isEmpty()) return null;

        int lowest = java.util.Collections.min(lines);
        int highest = java.util.Collections.max(lines);

        if (lowest < top.first || highest > top.last) return null;

        Group at = top;

        for (boolean deeper = true; deeper;)
        {
            deeper = false;

            for (Object element : at.elements)
            {
                if (element instanceof Group && ((Group) element).first <= lowest && ((Group) element).last >= highest)
                {
                    at = (Group) element;
                    deeper = true;
                    break;
                }
            }
        }

        return at;
    }

    /** The same group in a freshly read copy of the outline, found by its depth and first line. */
    private static Group find(Group top, Group like)
    {
        if (top == null || like == null) return null;

        if (top.depth == like.depth && top.first == like.first && top.last == like.last) return top;

        for (Object element : top.elements)
        {
            Group found = element instanceof Group ? find((Group) element, like) : null;

            if (found != null) return found;
        }

        return null;
    }

    /** The other word: a group nested to say something its parent does not. */
    public static Joiner opposite(Joiner word)
    {
        return word == Joiner.OR ? Joiner.AND : Joiner.OR;
    }

    /** A group's items joined by one word, each pair once: what a group says once its word is chosen. */
    private static List<Object> joined(List<Object> items, Joiner word)
    {
        List<Object> out = new ArrayList<>();

        for (Object item : items)
        {
            if (!out.isEmpty()) out.add(new Line(Row.joining(0, word), -1));

            out.add(item);
        }

        return out;
    }

    /** A group's own items as its elements hold them: conditions as their lines, nested groups that hold one. */
    private static List<Object> itemsOf(Group group)
    {
        List<Object> out = new ArrayList<>();

        for (Object element : group.elements)
        {
            if (element instanceof Group ? ((Group) element).holdsACondition() : !((Line) element).row.isJoiner())
            {
                out.add(element);
            }
        }

        return out;
    }

    /**
     * The outline with every word of one group the given word (OB-396), each pair of its items joined once: a word
     * nested where it joins nothing goes too, so choosing a group's word settles whatever `whatIsWrong` said of it.
     *
     * @param group a group of `rows`, from `groupsOf`
     * @return a new list, or a copy unchanged where the group is not in the outline
     */
    public static List<Row> reworded(List<Row> rows, Group group, Joiner word)
    {
        Group top = groupsOf(rows);
        Group at = find(top, group);

        if (at == null) return rows == null ? new ArrayList<Row>() : new ArrayList<>(rows);

        List<Object> items = itemsOf(at);

        at.elements.clear();
        at.elements.addAll(joined(items, word));

        return lines(top);
    }

    /**
     * What Group Selected would wrap: the run of one group's items these conditions are, where it is two items or more
     * and not the whole group.  A nested group counts as an item when all of it is chosen.
     *
     * @return {the group, its first item's element, its last item's element}, or null where the lines are no such run
     */
    private static Object[] run(Group top, Set<Integer> chosen)
    {
        Group group = holding(top, chosen);

        if (group == null) return null;

        List<Object> items = itemsOf(group);
        int from = -1;
        int to = -1;
        int covered = 0;

        for (int item = 0; item < items.size(); item++)
        {
            Object element = items.get(item);
            boolean all = element instanceof Group ? ((Group) element).within(chosen) : chosen.contains(((Line) element).at);
            boolean some = element instanceof Group && !java.util.Collections.disjoint(((Group) element).getConditions(),
                chosen);

            if (!all && some) return null;

            if (!all) continue;

            if (from >= 0 && to != item - 1) return null;

            if (from < 0) from = item;

            to = item;
            covered += element instanceof Group ? ((Group) element).getConditions().size() : 1;
        }

        if (from < 0 || to - from < 1 || to - from + 1 == items.size() || covered != chosen.size()) return null;

        return new Object[]{group, items.get(from), items.get(to)};
    }

    /** Whether Group Selected applies to these condition lines (OB-396); `grouped` says what it does. */
    public static boolean canGroup(List<Row> rows, Set<Integer> chosen)
    {
        return run(groupsOf(rows), chosen) != null;
    }

    /**
     * The outline with these conditions, a run of two or more items of one group and not all of it, wrapped in a new
     * group one level in, joined by the word that already joined them (OB-396): grouping never changes what they mean,
     * and the new group's rule is then chosen on its caption.
     *
     * @param chosen condition lines
     * @return a new list, or a copy unchanged where `canGroup` says no
     */
    public static List<Row> grouped(List<Row> rows, Set<Integer> chosen)
    {
        Group top = groupsOf(rows);
        Object[] run = run(top, chosen);

        if (run == null) return rows == null ? new ArrayList<Row>() : new ArrayList<>(rows);

        Group group = (Group) run[0];
        int from = group.elements.indexOf(run[1]);
        int to = group.elements.indexOf(run[2]);

        Group wrapped = new Group(group.depth + 1, group);
        List<Object> inside = new ArrayList<>(group.elements.subList(from, to + 1));

        // The first word inside the run: in "1 or 2 or 3 and 4", grouping 1 to 3 keeps "or".
        Joiner word = null;

        for (Object element : inside) if (word == null && isWord(element)) word = ((Line) element).row.getJoiner();

        inside.removeIf(ConditionOutline::isWord);

        group.elements.subList(from, to + 1).clear();
        group.elements.add(from, wrapped);

        wrapped.elements.addAll(joined(inside, word == null ? Joiner.AND : word));

        return lines(top);
    }

    /**
     * Where Split Here would cut (OB-396): one condition, not the first item of the group whose own item it is.
     *
     * @return {that group, the condition's element}, or null where it does not apply
     */
    private static Object[] cut(Group top, Set<Integer> chosen)
    {
        if (top == null || chosen == null || chosen.size() != 1) return null;

        int line = chosen.iterator().next();
        Group group = holderOfCondition(top, line);

        if (group == null) return null;

        List<Object> items = itemsOf(group);

        for (int item = 1; item < items.size(); item++)
        {
            if (items.get(item) instanceof Line && ((Line) items.get(item)).at == line)
            {
                return new Object[]{group, items.get(item)};
            }
        }

        return null;
    }

    /** Whether Split Here applies to these condition lines (OB-396); `split` says what it does. */
    public static boolean canSplit(List<Row> rows, Set<Integer> chosen)
    {
        return cut(groupsOf(rows), chosen) != null;
    }

    /** The group whose own elements hold the condition on this line, or null. */
    private static Group holderOfCondition(Group group, int line)
    {
        for (Object element : group.elements)
        {
            if (element instanceof Group)
            {
                Group found = holderOfCondition((Group) element, line);

                if (found != null) return found;
            }
            else if (((Line) element).at == line && !((Line) element).row.isJoiner())
            {
                return group;
            }
        }

        return null;
    }

    /**
     * The outline with a group split just above one of its conditions (OB-396): the items before it are one part, it
     * and the items after it the other, a part of two or more items a group with the group's word, a part of one item
     * that item, and the two parts joined by the other word.  "A and B and C" split at C is "(A and B) or C".  A nested
     * group whose parent already joins by the other word gives the parent its two parts; otherwise the group stays, its
     * word the other one, so the meaning is exactly the two parts joined.
     *
     * @param chosen one condition line, not the first item of its group
     * @return a new list, or a copy unchanged where `canSplit` says no
     */
    public static List<Row> split(List<Row> rows, Set<Integer> chosen)
    {
        Group top = groupsOf(rows);
        Object[] cut = cut(top, chosen);

        if (cut != null) return splitAt(top, (Group) cut[0], cut[1]);

        return new ArrayList<>(rows == null ? new ArrayList<Row>() : rows);
    }

    /** `split` on the groups of an outline, at one of its group's items; the lines it leaves. */
    private static List<Row> splitAt(Group top, Group group, Object item)
    {
        List<Object> items = itemsOf(group);
        int at = items.indexOf(item);
        Joiner word = group.getWord() == null ? Joiner.AND : group.getWord();
        Joiner other = opposite(word);

        List<Object> parts = new ArrayList<>();

        parts.add(part(group, items.subList(0, at), word));
        parts.add(part(group, items.subList(at, items.size()), word));

        Group parent = group.parent;

        if (parent != null && (parent.getWord() == null || parent.getWord() == other))
        {
            int place = parent.elements.indexOf(group);

            parent.elements.remove(place);
            parent.elements.addAll(place, joined(parts, other));
        }
        else
        {
            group.elements.clear();
            group.elements.addAll(joined(parts, other));
        }

        return lines(top);
    }

    /** One part of a split: one item as it is, or several in a group of their own joined by `word`. */
    private static Object part(Group holder, List<Object> items, Joiner word)
    {
        if (items.size() == 1) return items.get(0);

        Group out = new Group(holder.depth + 1, holder);

        out.elements.addAll(joined(new ArrayList<>(items), word));

        return out;
    }

    /**
     * The nested groups Ungroup lifts for a selection (MT-868): the group directly holding each selected condition,
     * leaving out the top level and any group inside another of them, so a group picked by its caption is lifted whole
     * and its own nested groups stay groups.
     *
     * @param chosen line indices of the selected conditions; words among them are ignored
     * @return the groups, in order; empty where there is nothing to lift
     */
    public static List<Group> ungroupables(List<Row> rows, java.util.Collection<Integer> chosen)
    {
        List<Group> out = new ArrayList<>();

        if (rows == null || chosen == null) return out;

        Group top = groupsOf(rows);
        List<Group> touched = new ArrayList<>();

        for (int at : new java.util.TreeSet<>(chosen))
        {
            if (at < 0 || at >= rows.size() || rows.get(at).isJoiner()) continue;

            Group group = holding(top, java.util.Collections.singleton(at));

            if (group != null && group.parent != null && !touched.contains(group)) touched.add(group);
        }

        for (Group group : touched)
        {
            boolean inner = false;

            for (Group up = group.parent; up != null; up = up.parent) inner |= touched.contains(up);

            if (!inner) out.add(group);
        }

        return out;
    }

    /**
     * The outline with the innermost nested group holding these lines dissolved into its parent, its items joined by
     * the parent's word (OB-396).
     *
     * @param word the parent's word, which a parent of one item has nowhere to say
     * @return a new list, or a copy unchanged where these lines are not inside a nested group
     */
    public static List<Row> ungrouped(List<Row> rows, Set<Integer> chosen, Joiner word)
    {
        Group top = groupsOf(rows);
        Group group = holding(top, chosen);

        if (group == null || group.parent == null) return rows == null ? new ArrayList<Row>() : new ArrayList<>(rows);

        List<Object> into = group.parent.elements;
        int at = into.indexOf(group);

        into.remove(at);
        into.addAll(at, joined(itemsOf(group), word));

        return lines(top);
    }

    /**
     * The outline with a condition added at the end of a group, joined by the group's word.
     *
     * @param group a group of `rows`, or null for the outermost
     * @param word the group's word, which a group of one item has nowhere to say
     * @return a new list
     */
    public static List<Row> appended(List<Row> rows, Group group, Joiner word, RouteCommand command)
    {
        Group top = groupsOf(rows);

        if (top == null)
        {
            List<Row> out = new ArrayList<>();

            out.add(Row.condition(0, command));

            return out;
        }

        Group at = group == null ? top : find(top, group);

        if (at == null) at = top;

        if (!itemsOf(at).isEmpty()) at.elements.add(new Line(Row.joining(0, word), -1));

        at.elements.add(new Line(Row.condition(0, command), -1));

        return lines(top);
    }

    /**
     * The outline with these conditions moved into a group, before its item `at` (OB-396), each joined by that group's
     * word.  A nested group all of whose conditions move goes as one; a group they leave empty goes with its word.
     *
     * @param moving condition lines
     * @param group where they go, a group of `rows`
     * @param at which of its items they go before; its item count for the end
     * @param word that group's word
     * @return a new list, or null where the group is among what moves
     */
    public static List<Row> dropped(List<Row> rows, Set<Integer> moving, Group group, int at, Joiner word)
    {
        Group top = groupsOf(rows);
        Group into = find(top, group);

        if (into == null || moving == null || moving.isEmpty()) return null;

        List<Object> blocks = new ArrayList<>();

        blocks(top, moving, blocks);

        for (Group inside = into; inside != null; inside = inside.parent)
        {
            if (blocks.contains(inside)) return null;
        }

        // Where they land, as the item they go before: the positions move as the blocks come out.
        List<Object> items = itemsOf(into);
        Object before = null;

        for (int item = Math.max(0, at); item < items.size() && before == null; item++)
        {
            if (!blocks.contains(items.get(item))) before = items.get(item);
        }

        for (Object block : blocks) takeOut(top, block, into);

        int place = before == null ? into.elements.size() : into.elements.indexOf(before);
        List<Object> landing = joined(blocks, word);

        if (before != null) landing.add(new Line(Row.joining(0, word), -1));
        else if (!itemsOf(into).isEmpty()) landing.add(0, new Line(Row.joining(0, word), -1));

        into.elements.addAll(place, landing);

        return lines(top);
    }

    /**
     * The outline with copies of these conditions directly after the last of them, as one run in their order (FR-162).
     * The pieces are those `movedTogether` moves: a nested group all of whose conditions are chosen is copied whole,
     * its rule and words with it.  The copies join the group holding the last piece, by its word, as a drop does.
     *
     * @param chosen line indices of the conditions; words among them are ignored
     * @param word the word of the group `copiedInto` names
     * @param copy makes each condition's copy, in order
     * @return a new list, or null where no condition is chosen
     */
    public static List<Row> duplicated(List<Row> rows, Set<Integer> chosen, Joiner word,
        java.util.function.UnaryOperator<RouteCommand> copy)
    {
        Group top = groupsOf(rows);
        List<Object> pieces = top == null || chosen == null ? new ArrayList<Object>() : piecesOf(top, chosen);

        if (pieces.isEmpty()) return null;

        Object last = pieces.get(pieces.size() - 1);
        Group into = holderOfPiece(top, last);
        List<Object> copies = new ArrayList<>();

        for (Object piece : pieces) copies.add(copyOf(piece, into, copy));

        List<Object> landing = joined(copies, word);

        landing.add(0, new Line(Row.joining(0, word), -1));
        into.elements.addAll(into.elements.indexOf(last) + 1, landing);

        return lines(top);
    }

    /**
     * The group `duplicated` puts the copies in: the one holding the last chosen piece.
     *
     * @param top the outline's groups, from `groupsOf`
     * @return that group, or null where no condition is chosen
     */
    public static Group copiedInto(Group top, Set<Integer> chosen)
    {
        List<Object> pieces = top == null || chosen == null ? new ArrayList<Object>() : piecesOf(top, chosen);

        return pieces.isEmpty() ? null : holderOfPiece(top, pieces.get(pieces.size() - 1));
    }

    private static List<Object> piecesOf(Group top, Set<Integer> chosen)
    {
        List<Object> out = new ArrayList<>();

        blocks(top, chosen, out);

        return out;
    }

    private static Group holderOfPiece(Group top, Object piece)
    {
        return piece instanceof Group ? ((Group) piece).parent : holderOf(top, piece);
    }

    /** A piece made anew: a condition by `copy`, a group with its words and a copy of each piece inside. */
    private static Object copyOf(Object piece, Group parent, java.util.function.UnaryOperator<RouteCommand> copy)
    {
        if (piece instanceof Line)
        {
            Row row = ((Line) piece).row;

            return new Line(row.isJoiner() ? row : row.about(copy.apply(row.getCommand())), -1);
        }

        Group was = (Group) piece;
        Group out = new Group(was.depth, parent);

        for (Object element : was.elements) out.elements.add(copyOf(element, out, copy));

        // A word leading the group joined it to what stood before; the copy is joined by the word put before it.
        while (!out.elements.isEmpty() && isWord(out.elements.get(0))) out.elements.remove(0);

        return out;
    }

    /** The largest pieces that move whole: a condition, or a nested group every condition of which moves. */
    private static void blocks(Group group, Set<Integer> moving, List<Object> into)
    {
        for (Object element : group.elements)
        {
            if (element instanceof Group)
            {
                if (((Group) element).within(moving)) into.add(element);
                else blocks((Group) element, moving, into);
            }
            else if (!((Line) element).row.isJoiner() && moving.contains(((Line) element).at))
            {
                into.add(element);
            }
        }
    }

    /** Takes a piece out of its group with one word beside it; a group left with nothing goes too, but never `keep`. */
    private static void takeOut(Group top, Object piece, Group keep)
    {
        Group from = pullOut(top, piece, true);

        if (from != null && from.parent != null && !holdsOrIs(from, keep)) takeOut(top, from, keep);
    }

    /**
     * Takes a piece out of the group holding it and, when `withAWord`, one word beside it, the one before first.
     * Returns that group, or null when no group holds the piece.
     */
    private static Group pullOut(Group top, Object piece, boolean withAWord)
    {
        Group from = piece instanceof Group ? ((Group) piece).parent : holderOf(top, piece);

        if (from == null) return null;

        List<Object> elements = from.elements;
        int at = elements.indexOf(piece);

        if (at < 0) return null;

        elements.remove(at);

        if (withAWord && at - 1 >= 0 && isWord(elements.get(at - 1))) elements.remove(at - 1);
        else if (withAWord && at < elements.size() && isWord(elements.get(at))) elements.remove(at);

        return from;
    }

    private static boolean isWord(Object element)
    {
        return element instanceof Line && ((Line) element).row.isJoiner();
    }

    /** Whether a group still holds a condition, or is or holds the group something is about to land in. */
    private static boolean holdsOrIs(Group group, Group keep)
    {
        if (group == keep || group.holdsACondition()) return true;

        for (Object element : group.elements)
        {
            if (element instanceof Group && holdsOrIs((Group) element, keep)) return true;
        }

        return false;
    }

    /** The group whose own elements hold this line. */
    private static Group holderOf(Group group, Object line)
    {
        if (group.elements.contains(line)) return group;

        for (Object element : group.elements)
        {
            Group found = element instanceof Group ? holderOf((Group) element, line) : null;

            if (found != null) return found;
        }

        return null;
    }
}
