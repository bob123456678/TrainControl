package org.traincontrol.automation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.traincontrol.automation.StandingRules.Answer;
import org.traincontrol.automation.StandingRules.Cell;
import org.traincontrol.automation.StandingRules.Row;
import org.traincontrol.util.I18n;

/**
 * The body of `Layout.standingRulesAt` (FR-111): which copies of a square the build emitted, read with the clauses the
 * real doors ask (`isSendableDestination`, `isChoosableByAutonomy`, `strandsATrainThatCannotReverse`, the start rules),
 * so it holds no rule of its own.  Occupancy, lengths and lists are left out.  Takes no lock: the caller holds the
 * railway's monitor.
 */
final class LayoutStandingRules
{
    private final Layout owner;
    private final LayoutExplanations explanations;

    LayoutStandingRules(Layout owner, LayoutExplanations explanations)
    {
        this.owner = owner;
        this.explanations = explanations;
    }

    StandingRules at(List<Point> copies)
    {
        StandingRules rules = new StandingRules();

        Set<String> arrivals = new LinkedHashSet<>();

        for (Point copy : copies)
        {
            for (Edge in : incoming(copy))
            {
                arrivals.add(in.getEntrySide() == null ? StandingRules.NO_SIDE : in.getEntrySide());
            }
        }

        // Nothing leads in: the three rows about arriving have no side, and each says so
        if (arrivals.isEmpty())
        {
            Cell none = cell(Answer.NO, "autolayout.rules.noWayIn");

            rules.put(Row.SENT_HERE, StandingRules.NO_SIDE, none, none);
            rules.put(Row.SENT_THROUGH, StandingRules.NO_SIDE, none, none);
            rules.put(Row.TURNED_ROUND, StandingRules.NO_SIDE, none, none);
        }

        for (String side : arrivals)
        {
            List<Point> landing = arrivingBy(copies, side);

            stopping(rules, copies, landing, side);
            passing(rules, landing, side);
            turning(rules, landing, side);
        }

        Set<String> exits = new LinkedHashSet<>();

        for (Point copy : copies)
        {
            exits.add(copy.getCopyFacing() == null ? StandingRules.NO_SIDE : copy.getCopyFacing());
        }

        // Track on a side no copy faces is a way out that was closed
        exits.addAll(arrivals);

        if (exits.size() > 1) exits.remove(StandingRules.NO_SIDE);

        for (String side : exits) leaving(rules, copies, side);

        for (Point copy : copies)
        {
            for (Edge out : owner.getNeighbors(copy)) rules.wayOut(sideLeftBy(copy, out));
        }

        return rules;
    }

    /**
     * The side of its square a train leaves by along this edge, which the model does not keep (a one-Point square has
     * no facing): toward the first place the build says the edge runs over.  `NO_SIDE` where it does not say.
     */
    private static String sideLeftBy(Point copy, Edge out)
    {
        String from = copy.getSquare();
        String to = out.getPlaceIds().isEmpty() ? null : Layout.tileOf(out.getPlaceIds().get(0));

        int colon = from == null || to == null ? -1 : from.lastIndexOf(':');

        if (colon < 0 || to.lastIndexOf(':') != colon || !to.startsWith(from.substring(0, colon + 1)))
        {
            return StandingRules.NO_SIDE;
        }

        try
        {
            String[] a = from.substring(colon + 1).split(",");
            String[] b = to.substring(colon + 1).split(",");

            int dx = Integer.parseInt(b[0].trim()) - Integer.parseInt(a[0].trim());
            int dy = Integer.parseInt(b[1].trim()) - Integer.parseInt(a[1].trim());

            if (Math.abs(dx) + Math.abs(dy) != 1) return StandingRules.NO_SIDE;

            // y grows southward on the diagram
            return dx > 0 ? "E" : dx < 0 ? "W" : dy > 0 ? "S" : "N";
        }
        catch (NumberFormatException | ArrayIndexOutOfBoundsException e)
        {
            return StandingRules.NO_SIDE;
        }
    }

    /** A train coming in by this side and ending its trip here. */
    private void stopping(StandingRules rules, List<Point> copies, List<Point> landing, String side)
    {
        if (isClosed(copies))
        {
            Cell closed = closed();

            rules.put(Row.SENT_HERE, side, closed, closed);

            return;
        }

        List<Point> stops = new ArrayList<>();

        for (Point copy : landing)
        {
            if (copy.isDestination()) stops.add(copy);
        }

        if (stops.isEmpty())
        {
            boolean aStation = false;

            for (Point copy : copies) aStation |= copy.isDestination() || owner.isABarredCopyOfAStation(copy);

            Cell no = cell(Answer.NO, aStation ? "autolayout.rules.arrivalsShut" : "autolayout.rules.notAStation");

            rules.put(Row.SENT_HERE, side, no, no);

            return;
        }

        rules.put(Row.SENT_HERE, side, stoppingChosen(stops), stoppingSent(stops));
    }

    private Cell stoppingChosen(List<Point> stops)
    {
        Cell best = null;

        for (Point stop : stops)
        {
            Cell here;

            if (!owner.isSendableDestination(stop))
            {
                // Autonomy's own reason, except a square it leaves alone, which is this window's tick
                here = stop.isAutoDestination() ? new Cell(Answer.NO, owner.whyBarredFromAutonomy(stop))
                    : cell(Answer.NO, "autolayout.rules.notChosen");
            }
            else if (!stop.isTerminus())
            {
                here = cell(Answer.YES, hasAWayOut(stop) ? "autolayout.rules.mayEndHere" : "autolayout.rules.mayEndHereStuck");
            }
            else if (hasAWayOut(stop))
            {
                here = cell(Answer.SOME, "autolayout.rules.reversibleThatCanLeave");
            }
            else
            {
                here = cell(Answer.NO, "autolayout.rules.couldNotLeave");
            }

            best = better(best, here);
        }

        return best;
    }

    private Cell stoppingSent(List<Point> stops)
    {
        Cell best = null;

        for (Point stop : stops)
        {
            Cell here = explanations.strandsATrainThatCannotReverse(stop)
                ? cell(Answer.SOME, "autolayout.rules.reversibleOffered")
                : cell(Answer.YES, stop.isAutoDestination() ? "autolayout.rules.mayBeSentHere" : "autolayout.rules.mayBeSentHereMore");

            best = better(best, here);
        }

        return best;
    }

    /** A train coming in by this side and carrying on out of another, which automatic and manual trips ask alike. */
    private void passing(StandingRules rules, List<Point> landing, String side)
    {
        Cell cell = null;

        for (Point copy : landing)
        {
            Cell here;

            if (!copy.isActive())
            {
                here = closed();
            }
            else if (copy.isTerminus() || copy.isReversing())
            {
                here = cell(Answer.NO, "autolayout.rules.onlyTurnsHere");
            }
            else if (!hasAWayOut(copy))
            {
                here = cell(Answer.NO, "autolayout.rules.wayOnShut");
            }
            else if (copy.isStopOnly())
            {
                here = cell(Answer.NO, "autolayout.rules.mustStop");
            }
            else
            {
                here = cell(Answer.YES, copy.isDestination() || !owner.isABarredCopyOfAStation(copy)
                    ? "autolayout.rules.runsThrough" : "autolayout.rules.runsThroughNoStop");
            }

            cell = better(cell, here);
        }

        if (cell == null) cell = cell(Answer.NO, "autolayout.rules.wayOnShut");

        rules.put(Row.SENT_THROUGH, side, cell, cell);
    }

    /** A train coming in by this side and going back out of it. */
    private void turning(StandingRules rules, List<Point> landing, String side)
    {
        Point terminus = null;
        Point reversing = null;
        boolean plain = false;

        for (Point copy : landing)
        {
            if (copy.isTerminus()) terminus = copy;
            else if (copy.isReversing()) reversing = copy;
            else plain |= hasAWayOut(copy);
        }

        Cell auto;
        Cell hand;

        Point turn = terminus != null ? terminus : reversing;

        if (turn == null)
        {
            auto = cell(Answer.NO, "autolayout.rules.neverTurns");
            hand = auto;
        }
        else if (!turn.isActive())
        {
            auto = closed();
            hand = auto;
        }
        else if (!hasAWayOut(turn))
        {
            auto = cell(Answer.NO, "autolayout.rules.wayBackShut");
            hand = auto;
        }
        else if (terminus == null)
        {
            // A way of turning partway through a trip: refused to autonomy by `reversesAlongTheWay`, and a stop-only
            // station's arrivals-shut side would have to be run through to use it
            auto = cell(Answer.NO, turn.isStopOnly() ? "autolayout.rules.mustStop" : "autolayout.rules.noTurnPartway");
            hand = turn.isStopOnly() ? auto : cell(Answer.YES, "autolayout.rules.mayTurnOnTheWay");
        }
        else
        {
            auto = owner.isSendableDestination(terminus) ? cell(Answer.SOME, "autolayout.rules.reversibleThatCanLeave")
                : cell(Answer.NO, "autolayout.rules.notChosen");

            hand = plain ? cell(Answer.SOME, "autolayout.rules.askedWhenSent")
                : explanations.strandsATrainThatCannotReverse(terminus) ? cell(Answer.SOME, "autolayout.rules.reversibleOffered")
                : cell(Answer.YES, "autolayout.rules.mayBeSentAndTurns");
        }

        rules.put(Row.TURNED_ROUND, side, auto, hand);
    }

    /** A train standing here and starting a trip out of this side. */
    private void leaving(StandingRules rules, List<Point> copies, String side)
    {
        Cell auto = null;
        Cell hand = null;

        for (Point copy : copies)
        {
            // A square's only copy has no facing of its own: it leaves by whatever way it has
            if (copy.getCopyFacing() != null && !copy.getCopyFacing().equals(side)) continue;

            if (!hasAWayOut(copy)) continue;

            String refused = explanations.whyNoTrainIsStartedFrom(copy);

            auto = better(auto, refused == null ? cell(Answer.YES, "autolayout.rules.mayStart")
                : !copy.isActive() ? closed() : new Cell(Answer.NO, refused));

            hand = better(hand, cell(Answer.YES, copy.isActive() ? "autolayout.rules.maySendOut"
                : "autolayout.rules.maySendOutClosed"));
        }

        if (auto == null)
        {
            auto = cell(Answer.NO, "autolayout.rules.wayOutShut");
            hand = auto;
        }

        rules.put(Row.SENT_FROM, side, auto, hand);
    }

    private boolean isClosed(List<Point> copies)
    {
        for (Point copy : copies)
        {
            if (copy.isActive()) return false;
        }

        return true;
    }

    private boolean hasAWayOut(Point copy)
    {
        return !owner.getNeighbors(copy).isEmpty();
    }

    private List<Edge> incoming(Point copy)
    {
        List<Edge> out = new ArrayList<>();

        for (Edge edge : owner.getEdges())
        {
            if (edge.getEnd() == copy) out.add(edge);
        }

        return out;
    }

    private List<Point> arrivingBy(List<Point> copies, String side)
    {
        List<Point> out = new ArrayList<>();

        for (Point copy : copies)
        {
            for (Edge in : incoming(copy))
            {
                String entry = in.getEntrySide() == null ? StandingRules.NO_SIDE : in.getEntrySide();

                if (entry.equals(side) && !out.contains(copy)) out.add(copy);
            }
        }

        return out;
    }

    private static Cell better(Cell one, Cell other)
    {
        return one == null || other.getAnswer().ordinal() < one.getAnswer().ordinal() ? other : one;
    }

    private static Cell closed()
    {
        return cell(Answer.NO, "autolayout.rules.closed");
    }

    private static Cell cell(Answer answer, String key)
    {
        return new Cell(answer, I18n.t(key));
    }
}
