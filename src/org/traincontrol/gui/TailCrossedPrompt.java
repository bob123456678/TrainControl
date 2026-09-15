package org.traincontrol.gui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.swing.JOptionPane;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.util.I18n;

/**
 * The farthest sensor the tail of a hand-placed train has crossed, which says the road its tail lies on.
 *
 * Adam, 2026-09-14: **"if a train is long, why not ask the user to specify the last sensor it crossed from a list
 * of possible sensors?  Then state will always be fully consistent."**  And: **"the prompt should ask the user to
 * pick from a list and select the farthest sensor the tail of the train recently crossed.  Also, ideally in the
 * autonomy editor, it should allow the user to click to select as well."**
 *
 * **What the answer pins down.**  The tail has crossed a sensor once the whole train has passed it, so the whole
 * train lies between the farthest such sensor and the square it stands on - on the one road between them.  That road
 * is what `Point.arrivedAlong` holds for a train autonomy drove, and what the tail walk follows past a junction
 * (MT-335).  A train placed by hand had none, so its tail stopped at the first fork.
 *
 * **Asked only when the answer changes something.**  The side it came in by (`ArrivalSidePrompt`) already picks the
 * first road back; past that the walk follows the only road there is until it reaches a junction.  So the question
 * is put only where the train is long enough for its tail to have crossed sensors on TWO different roads back from
 * one junction - otherwise every answer describes the same track.  A junction the tail reaches but crosses no sensor
 * beyond is not asked about either: the answer would be the junction itself on every road, and could not say which
 * one the tail lies on, so the walk stops there as it always has.
 *
 * **How far back a sensor may be offered is a suggestion, not the blocking rule.**  Each road back is spent against
 * the train's length using the measured lengths of its edges, and a sensor is offered while some of the train is
 * still left beyond it.  An unmeasured edge ends a road, because the walk stops there too (*"if no length specified,
 * just stop there"*).  This does not repeat the walk's allowance for the standing square's own measurement or its
 * place-by-place arithmetic, so at a boundary it can offer one sensor more or fewer than the walk would reach -
 * and what blocks track is still the walk, reading the road the operator chose.
 */
public class TailCrossedPrompt
{
    /** Never instantiated. */
    private TailCrossedPrompt()
    {
    }

    /** The answer that says nobody knows, for `answerForTests`. */
    public static final String NOT_KNOWN = "";

    /** How many sensors back a road is followed at most, so a loop in a hand-written layout cannot run away. */
    private static final int MOST_SENSORS_BACK = 30;

    /** The answer the next question gets without a dialog, for tests; null shows the dialog. */
    private static volatile String answeredByATest;

    /**
     * Makes every question answer this without showing a dialog, until reset with null.  For tests only.
     *
     * @param farthest the farthest sensor's point name, `NOT_KNOWN`, or null to put the dialog back
     */
    public static void answerForTests(String farthest)
    {
        answeredByATest = farthest;
    }

    /**
     * One sensor the tail may have crossed, and the road from it to the square the train stands on.
     */
    public static final class Choice
    {
        private final Point farthest;
        private final List<Edge> road;
        private String label;

        private Choice(Point farthest, List<Edge> road)
        {
            this.farthest = farthest;
            this.road = java.util.Collections.unmodifiableList(new ArrayList<>(road));
        }

        /** @return the sensor */
        public Point getFarthest()
        {
            return farthest;
        }

        /** @return the road, from the sensor to the standing square, in the order a train drives it */
        public List<Edge> getRoad()
        {
            return road;
        }

        /** @return what the operator reads */
        public String getLabel()
        {
            return label;
        }
    }

    /**
     * Every sensor the tail of this train can have crossed, nearest first along each road back.
     *
     * @param layout the running layout
     * @param at the point the train stands on
     * @param arrivedFrom the side it came in by - the first road back - or null
     * @param trainLength its length, or null
     * @param shown what to call a point on screen, or null for its own name
     * @return the choices, never null; empty where nothing can be said
     */
    public static List<Choice> choicesFor(Layout layout, Point at, String arrivedFrom, Integer trainLength,
        Function<String, String> shown)
    {
        List<Choice> choices = new ArrayList<>();

        walk(layout, at, arrivedFrom, trainLength, choices, new int[1]);

        label(choices, shown);

        return choices;
    }

    /**
     * Whether the answer would change which track is blocked, so a placement should ask.
     *
     * The same walk as `choicesFor`, one place: true exactly where it finds a junction the tail has crossed sensors
     * beyond on two different roads.
     *
     * @param layout the running layout
     * @param at the point the train stands on
     * @param arrivedFrom the side it came in by, or null
     * @param trainLength its length, or null
     * @return whether to ask
     */
    public static boolean wouldAsk(Layout layout, Point at, String arrivedFrom, Integer trainLength)
    {
        int[] forks = new int[1];

        walk(layout, at, arrivedFrom, trainLength, new ArrayList<Choice>(), forks);

        return forks[0] > 0;
    }

    /**
     * The road a hand-placed train's tail lies on, asking where the answer matters.
     *
     * **After the train is down, and named for it.**  The side question is asked BEFORE the move, because its
     * dismissal refuses the placement (Adam, 2026-09-07: "simply don't place the train") and
     * `testEditorSurfaceRules.testADismissedArrivalQuestionPlacesNothing` holds that door to it.  This one is
     * asked after, because its dismissal is Not Known and places the train all the same - and it needs the
     * side the placement just recorded.
     *
     * @param layout the running layout
     * @param at the point the train now stands on
     * @param arrivedFrom the side it came in by, or null
     * @param trainLength its length, or null
     * @param train the train's name, for the question
     * @param parent what to centre the dialog on
     * @param shown what to call a point on screen, or null for its own name
     * @return the road, or null when nothing was asked or the operator did not know
     */
    public static List<Edge> askAfterPlacement(Layout layout, Point at, String arrivedFrom, Integer trainLength,
        String train, Component parent, Function<String, String> shown)
    {
        if (!wouldAsk(layout, at, arrivedFrom, trainLength)) return null;

        List<Choice> choices = choicesFor(layout, at, arrivedFrom, trainLength, shown);

        Choice chosen = ask(parent, train, shownName(shown, at), choices);

        return chosen == null ? null : chosen.getRoad();
    }

    /**
     * Puts the question: a list of the sensors, and Not Known.
     *
     * A dismissed dialog is Not Known - nothing recorded, and the walk keeps the fork rule, which claims least.
     * Unlike the side question it does not refuse the placement: the train is already somewhere a side was given
     * for, and not knowing how far back its tail reaches is an ordinary answer.
     *
     * @param parent what to centre on
     * @param train the train's name
     * @param station where it stands, as the operator reads it
     * @param choices the sensors
     * @return the choice, or null for Not Known
     */
    public static Choice ask(Component parent, String train, String station, List<Choice> choices)
    {
        if (choices == null || choices.isEmpty()) return null;

        if (answeredByATest != null)
        {
            for (Choice choice : choices)
            {
                if (choice.getFarthest().getName().equals(answeredByATest)) return choice;
            }

            return null;
        }

        final Choice[] answer = {null};

        try
        {
            Runnable prompt = () ->
            {
                final javax.swing.JList<String> list = new javax.swing.JList<>(labelsOf(choices));

                list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
                list.setVisibleRowCount(Math.min(10, Math.max(3, choices.size())));

                javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));

                panel.add(new javax.swing.JLabel("<html>" + escape(I18n.f("autolayout.ui.askTailCrossed", train, station))
                    .replace("\n", "<br>") + "</html>"), java.awt.BorderLayout.NORTH);
                panel.add(new javax.swing.JScrollPane(list), java.awt.BorderLayout.CENTER);

                Object[] options = { I18n.t("ui.ok"), I18n.t("autosetup.ui.tailCrossedNotKnown") };

                int chose = JOptionPane.showOptionDialog(parent, panel, I18n.t("autolayout.ui.askArrivalSideTitle"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

                if (chose == 0 && list.getSelectedIndex() >= 0) answer[0] = choices.get(list.getSelectedIndex());
            };

            if (javax.swing.SwingUtilities.isEventDispatchThread()) prompt.run();
            else javax.swing.SwingUtilities.invokeAndWait(prompt);
        }
        catch (Exception cannotAsk)
        {
            // Nothing, and Not Known stands: an unanswered question narrows the walk and blocks no extra track.
        }

        return answer[0];
    }

    /**
     * Which choice a recorded road describes: the longest one it ends with.
     *
     * A driven train's road starts wherever it set off, so it is usually longer than any choice; what matters is the
     * part nearest the train.  An answer given here is exactly a choice's road, and ends with itself.
     *
     * @param choices the choices
     * @param recorded the road the running layout has, or null
     * @return the choice, or null when the road matches none - Not Known
     */
    public static Choice recordedChoice(List<Choice> choices, List<Edge> recorded)
    {
        if (choices == null || recorded == null || recorded.isEmpty()) return null;

        List<String> had = pairsOf(recorded);

        Choice best = null;

        for (Choice choice : choices)
        {
            List<String> mine = pairsOf(choice.getRoad());

            if (mine.isEmpty() || mine.size() > had.size()) continue;

            if (!had.subList(had.size() - mine.size(), had.size()).equals(mine)) continue;

            if (best == null || mine.size() > best.getRoad().size()) best = choice;
        }

        return best;
    }

    /** Each edge as "start>end", with the rail read either way round the same, as the walk reads it. */
    private static List<String> pairsOf(List<Edge> road)
    {
        List<String> pairs = new ArrayList<>();

        for (Edge edge : road)
        {
            if (edge == null || edge.getStart() == null || edge.getEnd() == null) continue;

            String a = edge.getStart().getName();
            String b = edge.getEnd().getName();

            pairs.add(a.compareTo(b) <= 0 ? a + ">" + b : b + ">" + a);
        }

        return pairs;
    }

    /**
     * The road as the setup writes it, for a door that records the answer.
     *
     * @param road the road, or null
     * @return its [start, end] name pairs as a JSON array string, or null
     */
    public static String namesOf(List<Edge> road)
    {
        return Layout.namesOfRoad(road);
    }

    // ---------------------------------------------------------------- the walk

    /**
     * Follows every road back from the standing square, spending the train's length, collecting the sensors its tail
     * has crossed and counting the junctions where it crossed sensors on more than one road.
     */
    private static void walk(Layout layout, Point at, String arrivedFrom, Integer trainLength, List<Choice> into,
        int[] forks)
    {
        if (layout == null || at == null || arrivedFrom == null || trainLength == null || trainLength <= 0) return;

        // THE FIRST ROAD BACK IS THE SIDE IT CAME IN BY - the tail walk's own first hop, and `entrySideOf` is the one
        // definition of a side both read.  The copy of the rail that ARRIVES here is preferred, as the walk does.
        Map<String, Edge> firstHops = new LinkedHashMap<>();

        for (Edge candidate : layout.getNeighborsAndIncoming(at))
        {
            Point other = candidate.getStart() == at ? candidate.getEnd() : candidate.getStart();

            if (other == null) continue;

            String cameInBy = layout.entrySideOf(candidate, at);

            if (cameInBy == null || !arrivedFrom.equalsIgnoreCase(cameInBy)) continue;

            Edge known = firstHops.get(other.getName());

            if (known == null || (known.getEnd() != at && candidate.getEnd() == at))
            {
                firstHops.put(other.getName(), candidate);
            }
        }

        Set<String> walked = new LinkedHashSet<>();

        walked.add(at.getName());

        List<Edge> road = new ArrayList<>();

        int crossedHere = 0;

        for (Edge hop : firstHops.values())
        {
            Point behind = hop.getStart() == at ? hop.getEnd() : hop.getStart();

            if (back(layout, hop, behind, at, trainLength, road, walked, into, forks, 1)) crossedHere++;
        }

        if (crossedHere > 1) forks[0]++;
    }

    /**
     * One road back: over `hop` to `behind`, and on from there.
     *
     * @return whether the tail crossed `behind`
     */
    private static boolean back(Layout layout, Edge hop, Point behind, Point ahead, int left, List<Edge> road,
        Set<String> walked, List<Choice> into, int[] forks, int depth)
    {
        if (behind == null || walked.contains(behind.getName()) || depth > MOST_SENSORS_BACK) return false;

        // An unmeasured stretch ends the road: nothing can be said about how much train is left after it.
        if (hop.getLength() <= 0) return false;

        int beyond = left - hop.getLength();

        // Crossed only if some of the train is still left beyond the sensor.
        if (beyond <= 0) return false;

        // THE RAIL AS A TRAIN DRIVES IT: from the sensor behind towards the square ahead, where that direction exists.
        Edge driven = layout.getEdge(behind.getName(), ahead.getName());

        road.add(0, driven != null ? driven : hop);
        walked.add(behind.getName());

        into.add(new Choice(behind, road));

        int crossedBeyond = 0;

        Set<String> seen = new LinkedHashSet<>();

        for (Edge candidate : layout.getNeighborsAndIncoming(behind))
        {
            Point further = candidate.getStart() == behind ? candidate.getEnd() : candidate.getStart();

            if (further == null || walked.contains(further.getName()) || !seen.add(further.getName())) continue;

            if (back(layout, candidate, further, behind, beyond, road, walked, into, forks, depth + 1)) crossedBeyond++;
        }

        if (crossedBeyond > 1) forks[0]++;

        walked.remove(behind.getName());
        road.remove(0);

        return true;
    }

    // ---------------------------------------------------------------- the words

    /**
     * Names every choice, telling apart two that would read the same.
     */
    private static void label(List<Choice> choices, Function<String, String> shown)
    {
        Map<String, Integer> count = new LinkedHashMap<>();

        for (Choice choice : choices)
        {
            String name = shownName(shown, choice.getFarthest());

            count.put(name, count.getOrDefault(name, 0) + 1);
        }

        for (Choice choice : choices)
        {
            String name = shownName(shown, choice.getFarthest());

            if (count.get(name) > 1 && choice.getRoad().size() > 1)
            {
                // The next sensor towards the train says which road it is.
                Edge next = choice.getRoad().get(1);
                Point via = next.getStart() == null ? next.getEnd() : next.getStart();

                choice.label = I18n.f("autolayout.ui.tailCrossedVia", name, shownName(shown, via));
            }
            else
            {
                choice.label = name;
            }
        }
    }

    private static String shownName(Function<String, String> shown, Point point)
    {
        if (point == null) return "";

        String name = shown == null ? null : shown.apply(point.getName());

        return name == null || name.trim().isEmpty() ? point.getName() : name;
    }

    private static String[] labelsOf(List<Choice> choices)
    {
        String[] labels = new String[choices.size()];

        for (int i = 0; i < choices.size(); i++) labels[i] = choices.get(i).getLabel();

        return labels;
    }

    private static String escape(String text)
    {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
