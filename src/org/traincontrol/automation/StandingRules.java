package org.traincontrol.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the built railway lets a train do at one square, whoever stands where (FR-111): the answers Station Settings
 * shows.  `Layout.standingRulesAt` fills it in from the copies of the square and the clauses the real doors ask.
 */
public final class StandingRules
{
    /** Yes, for some trains only, or no. */
    public enum Answer
    {
        YES, SOME, NO
    }

    /** What may be asked; each row's cells are per side of the square. */
    public enum Row
    {
        SENT_HERE, SENT_THROUGH, SENT_FROM, TURNED_ROUND
    }

    /** What keeps some trains out where a cell says Some trains (FR-226), each named by the setting it comes from. */
    public enum Limit
    {
        /** Changing direction: only a train that can reverse. */
        REVERSING,
        /** Maximum train length. */
        LENGTH,
        /** Allowed trains: the whitelist or the blacklist. */
        LIST
    }

    /** One answer and the sentence that says why. */
    public static final class Cell
    {
        private final Answer answer;
        private final String reason;
        private final Map<Limit, String> limits;

        Cell(Answer answer, String why)
        {
            this(answer, why, Collections.<Limit, String>emptyMap());
        }

        /** @param reason why, before the limits; null where the limits are all there is to say */
        Cell(Answer answer, String reason, Map<Limit, String> limits)
        {
            this.answer = answer;
            this.reason = reason;
            this.limits = Collections.unmodifiableMap(new LinkedHashMap<>(limits));
        }

        public Answer getAnswer()
        {
            return answer;
        }

        /** The reason, then each limit's sentence. */
        public String getWhy()
        {
            List<String> sentences = new ArrayList<>();

            if (reason != null) sentences.add(reason);

            sentences.addAll(limits.values());

            return String.join("  ", sentences);
        }

        /** Why, without the limits; null where the limits are all there is to say. */
        public String getReason()
        {
            return reason;
        }

        /** Each limit that makes this Some trains, with its sentence, in `Limit`'s order; empty elsewhere. */
        public Map<Limit, String> getLimits()
        {
            return limits;
        }
    }

    /** The side a cell is about where the square has no sides to name. */
    public static final String NO_SIDE = "";

    private final Map<Row, Map<String, Cell>> automatically = new EnumMap<>(Row.class);
    private final Map<Row, Map<String, Cell>> manually = new EnumMap<>(Row.class);

    StandingRules()
    {
        for (Row row : Row.values())
        {
            automatically.put(row, new LinkedHashMap<String, Cell>());
            manually.put(row, new LinkedHashMap<String, Cell>());
        }
    }

    void put(Row row, String side, Cell auto, Cell hand)
    {
        automatically.get(row).put(side, auto);
        manually.get(row).put(side, hand);
    }

    /**
     * @param row what is asked
     * @param byHand whether the trip is sent by hand, rather than chosen by autonomy
     * @return side, as `TilePorts.Side.name()` or `NO_SIDE`, to its answer, in the order the sides were found
     */
    public Map<String, Cell> cells(Row row, boolean byHand)
    {
        return (byHand ? manually : automatically).get(row);
    }

    private final Set<String> waysOut = new TreeSet<>();

    void wayOut(String side)
    {
        waysOut.add(side);
    }

    /** @return the sides a train may come in by and stop, sent or chosen: where Sent Here or Turned Around is not No */
    public Set<String> waysIn()
    {
        Set<String> out = new TreeSet<>();

        for (Row row : new Row[] {Row.SENT_HERE, Row.TURNED_ROUND})
        {
            for (Map<Row, Map<String, Cell>> tier : java.util.Arrays.asList(automatically, manually))
            {
                for (Map.Entry<String, Cell> each : tier.get(row).entrySet())
                {
                    if (each.getValue().getAnswer() != Answer.NO) out.add(each.getKey());
                }
            }
        }

        return out;
    }

    /**
     * @return the sides the built railway's track leads a train out by, from a copy with a way out, which is where
     *     `Row.SENT_FROM` answers yes by hand; `NO_SIDE` where the build did not say where an edge runs
     */
    public Set<String> waysOut()
    {
        return Collections.unmodifiableSet(waysOut);
    }
}
