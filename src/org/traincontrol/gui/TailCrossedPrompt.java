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
 * is put only where a junction behind the train has two roads back and its tail has crossed a sensor on at least one
 * of them (TLR-B1) - that sensor and Not Known then describe different track; elsewhere every answer describes the
 * same track.  A junction the tail reaches but crosses no sensor beyond is not asked about: the answer would be the
 * junction itself on every road, and could not say which one the tail lies on, so the walk stops there as it always
 * has.  Two copies of one square are one road (TLR-B2).
 *
 * **How far back a sensor may be offered is a suggestion, not the blocking rule.**  Each road back is spent against
 * the train's length using the measured lengths of its edges, and a sensor is offered once the train reaches it,
 * with some of the train left beyond it or none (OB-226).  An unmeasured edge ends a road, because the walk stops there too (*"if no length specified,
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

    /** The question closed without an answer, for `answerForTests` (TLV-A1). */
    public static final String DISMISSED = "(dismissed)";

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
     * The same walk as `choicesFor`, one place: true exactly where it finds a junction with two roads back and a sensor
     * the tail has crossed on at least one of them.
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
     * @return what the question came to - see `Answer` (TLW-C2)
     */
    public static Answer askAfterPlacement(Layout layout, Point at, String arrivedFrom, Integer trainLength,
        String train, Component parent, Function<String, String> shown)
    {
        return askAfterPlacement(layout, at, arrivedFrom, trainLength, train, parent, shown, null);
    }

    /**
     * The same, with the list starting on the road the train already has (TLW-C4), so OK does not throw it away.
     *
     * @param recorded the road the train had before the door ran, or null
     * @return what the question came to
     */
    public static Answer askAfterPlacement(Layout layout, Point at, String arrivedFrom, Integer trainLength,
        String train, Component parent, Function<String, String> shown, List<Edge> recorded)
    {
        if (!wouldAsk(layout, at, arrivedFrom, trainLength)) return Answer.NOT_ASKED;

        List<Choice> choices = choicesFor(layout, at, arrivedFrom, trainLength, shown);

        Reply reply = reply(parent, train, shownName(shown, at), choices, preselectedIndex(choices, recorded));

        return reply.answered ? new Answer(true, reply.choice == null ? null : reply.choice.getRoad()) : Answer.NOT_ASKED;
    }

    /**
     * Puts the question: a list of the sensors, and Not Known.
     *
     * A closed dialog is no answer and a choice of nothing (TLW-C2).  Unlike the side question it does not refuse the
     * placement: the train is already somewhere a side was given for.
     *
     * @param parent what to centre on
     * @param train the train's name
     * @param station where it stands, as the operator reads it
     * @param choices the sensors
     * @return the choice, or null for Not Known
     */
    public static Choice ask(Component parent, String train, String station, List<Choice> choices)
    {
        return reply(parent, train, station, choices, -1).choice;
    }

    /**
     * Where the list should start: the choice the recorded road describes, or none (TLW-C4).
     *
     * @param choices the choices
     * @param recorded the road the train has, or null
     * @return its index, or -1
     */
    public static int preselectedIndex(List<Choice> choices, List<Edge> recorded)
    {
        Choice ticked = recordedChoice(choices, recorded);

        if (ticked != null) return choices.indexOf(ticked);

        // THE SENSOR NEAREST THE BACK, WHEN THERE IS ONE (FR-088).  Adam, MT-435, 2026-09-15: *"The closest sensor to the
        // back should be the default selection in the length window, so the user can just click OK if appropriate."*
        // Asked about several: nearest the back if there is one, nothing chosen otherwise.  Nearest the back is a sensor
        // no other offered sensor lies beyond on the same road.
        int nearestTheBack = -1;

        for (int i = 0; i < choices.size(); i++)
        {
            List<String> mine = pairsOf(choices.get(i).getRoad());
            boolean somethingBeyond = false;

            for (Choice other : choices)
            {
                List<String> theirs = pairsOf(other.getRoad());

                if (theirs.size() > mine.size() && theirs.subList(theirs.size() - mine.size(), theirs.size()).equals(mine))
                {
                    somethingBeyond = true;

                    break;
                }
            }

            if (somethingBeyond) continue;

            if (nearestTheBack >= 0) return -1;

            nearestTheBack = i;
        }

        return nearestTheBack;
    }

    /**
     * What a placement's question came to (TLV-A1).
     *
     * Two things a bare road could not say apart: "Not known" is an answer and forgets a road; no question - or one
     * closed without an answer - says nothing, and must leave whatever road the train has.
     */
    public static final class Answer
    {
        /** Nothing was asked, or nothing was answered. */
        public static final Answer NOT_ASKED = new Answer(false, null);

        private final boolean answered;
        private final List<Edge> road;

        private Answer(boolean answered, List<Edge> road)
        {
            this.answered = answered;
            this.road = road;
        }

        /** @return whether the operator answered - a sensor, or Not Known */
        public boolean wasAnswered()
        {
            return answered;
        }

        /** @return the road answered, or null for Not Known or no answer */
        public List<Edge> getRoad()
        {
            return road;
        }

        /**
         * The road a door should record for the train, in both stores (TLV-A1, TLW-A1).
         *
         * The answer, where there was one.  Otherwise the road the train had ON THE RAILWAY before the door moved it -
         * kept where it is still on the same square with the same side, and dropped where it is not, because a road
         * describes one arrival.  Decided from the railway, not the setup: a run writes its arrival on the running
         * layout alone, and the setup hears of it only at the next capture, so reading the setup erased the road a
         * train had just driven in on.  Both stores are then written, so they agree - including when the door moved the
         * train onto another copy of its square, which clears the road there.
         *
         * @param roadBefore the road the train had on the running layout before the door ran, or null
         * @param sameSquare whether it is on the same square as before
         * @param sideBefore the side it had on the running layout before, or null
         * @param sideNow the side the door just wrote
         * @return the road to record, or null for none
         */
        public List<Edge> roadToRecord(List<Edge> roadBefore, boolean sameSquare, String sideBefore, String sideNow)
        {
            if (answered) return road;

            boolean sameSide = sideBefore == null ? sideNow == null : sideBefore.equalsIgnoreCase(sideNow);

            return sameSquare && sameSide ? roadBefore : null;
        }
    }

    /** A choice, and whether it was answered at all. */
    private static final class Reply
    {
        private final boolean answered;
        private final Choice choice;

        private Reply(boolean answered, Choice choice)
        {
            this.answered = answered;
            this.choice = choice;
        }
    }

    /**
     * The dialog, telling Not Known (an answer) from a closed dialog (none).
     */
    private static Reply reply(Component parent, String train, String station, List<Choice> choices, final int preselect)
    {
        if (choices == null || choices.isEmpty()) return new Reply(false, null);

        if (answeredByATest != null)
        {
            if (NOT_KNOWN.equals(answeredByATest)) return new Reply(true, null);

            if (DISMISSED.equals(answeredByATest)) return new Reply(false, null);

            for (Choice choice : choices)
            {
                if (choice.getFarthest().getName().equals(answeredByATest)) return new Reply(true, choice);
            }

            // SAID, not taken for a dismissal (TLW-C3): a test answering with a sensor that is not offered is a test
            // asking about a different railway from the one it built.
            throw new IllegalStateException("a test answered " + answeredByATest + ", which is not offered: "
                + java.util.Arrays.asList(labelsOf(choices)));
        }

        final Choice[] answer = {null};
        final boolean[] answered = {false};

        try
        {
            Runnable prompt = () ->
            {
                final javax.swing.JList<String> list = new javax.swing.JList<>(labelsOf(choices));

                list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

                if (preselect >= 0 && preselect < choices.size()) list.setSelectedIndex(preselect);
                list.setVisibleRowCount(Math.min(10, Math.max(3, choices.size())));

                javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));

                panel.add(new javax.swing.JLabel("<html>" + escape(I18n.f("autolayout.ui.askTailCrossed", train, station))
                    .replace("\n", "<br>") + "</html>"), java.awt.BorderLayout.NORTH);
                panel.add(new javax.swing.JScrollPane(list), java.awt.BorderLayout.CENTER);

                Object[] options = { I18n.t("ui.ok"), I18n.t("autosetup.ui.tailCrossedNotKnown") };

                int chose = JOptionPane.showOptionDialog(parent, panel, I18n.t("autolayout.ui.askArrivalSideTitle"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

                // OK with a sensor chosen is that sensor; OK with none, or Not Known, is Not Known.  Closed is no answer.
                if (chose == 0 || chose == 1) answered[0] = true;

                if (chose == 0 && list.getSelectedIndex() >= 0) answer[0] = choices.get(list.getSelectedIndex());
            };

            if (javax.swing.SwingUtilities.isEventDispatchThread()) prompt.run();
            else javax.swing.SwingUtilities.invokeAndWait(prompt);
        }
        catch (Exception cannotAsk)
        {
            // Nothing, and no answer stands: the road the train has, if any, is left as it was.
        }

        return new Reply(answered[0], answer[0]);
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

    /**
     * Each edge as its two places, the rail read either way round the same, as the walk reads it - by place, so a road a
     * train drove in one lane still matches the choices worked out from the other lane's copy it was turned onto (TLR-A1).
     */
    private static List<String> pairsOf(List<Edge> road)
    {
        List<String> pairs = new ArrayList<>();

        for (Edge edge : road)
        {
            if (edge == null || edge.getStart() == null || edge.getEnd() == null) continue;

            String a = placeOf(edge.getStart());
            String b = placeOf(edge.getEnd());

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
     * has crossed and counting the junctions with two roads back where it crossed a sensor on at least one.
     */
    private static void walk(Layout layout, Point at, String arrivedFrom, Integer trainLength, List<Choice> into,
        int[] forks)
    {
        if (layout == null || at == null || arrivedFrom == null || trainLength == null || trainLength <= 0) return;

        // THE FIRST ROAD BACK IS THE SIDE IT CAME IN BY - the tail walk's own first hop, and `entrySideOf` is the one
        // definition of a side both read.
        //
        // ONLY RAILS THAT ARRIVE HERE (OB-227).  Adam, MT-435, 2026-09-15: *"the blocked orange path crosses switch 99 and
        // switch 100 instead of going to bottomsecondary, which would require the train to reverse in.  that isn't a
        // realistic path."*  The road a tail lies on is the one the train DROVE in on, so every rail of it runs towards
        // the train; a rail laid the other way is one it could only have reached by turning round.
        Map<String, Edge> firstHops = new LinkedHashMap<>();

        for (Edge candidate : layout.getIncomingEdges(at))
        {
            if (candidate.getEnd() != at || candidate.getStart() == null) continue;

            String cameInBy = layout.entrySideOf(candidate, at);

            if (cameInBy == null || !arrivedFrom.equalsIgnoreCase(cameInBy)) continue;

            // ONE ROAD PER PIECE OF METAL (OB-276), and the plain lane rather than a turning copy where two copies
            // leave by the same rail, so the road recorded does not depend on the order the rails come back in.
            String key = roadKeyOf(candidate);

            Edge had = firstHops.get(key);

            if (had == null || (isATurningCopy(had.getStart()) && !isATurningCopy(candidate.getStart())))
            {
                firstHops.put(key, candidate);
            }
        }

        Set<String> walked = new LinkedHashSet<>();

        walked.add(placeOf(at));

        List<Edge> road = new ArrayList<>();

        int crossedHere = 0;

        for (Edge hop : firstHops.values())
        {
            if (back(layout, hop, hop.getStart(), at, trainLength, road, walked, into, forks, 1)) crossedHere++;
        }

        // A JUNCTION IS A QUESTION when it has two roads back and the tail crossed a sensor on at least one of them
        // (TLR-B1): that sensor and Not Known then describe different track.  Crossed on none, every answer is the
        // junction itself and the walk stops there anyway.
        if (firstHops.size() > 1 && crossedHere > 0) forks[0]++;
    }

    /**
     * One road back: over `hop` to `behind`, and on from there.
     *
     * @return whether the tail crossed `behind`
     */
    private static boolean back(Layout layout, Edge hop, Point behind, Point ahead, int left, List<Edge> road,
        Set<String> walked, List<Choice> into, int[] forks, int depth)
    {
        if (behind == null || walked.contains(placeOf(behind)) || depth > MOST_SENSORS_BACK) return false;

        // An unmeasured stretch ends the road: nothing can be said about how much train is left after it.
        if (hop.getLength() <= 0) return false;

        int beyond = left - hop.getLength();

        // CROSSED WHEN THE TRAIN REACHES IT (OB-226).  Adam, MT-435, 2026-09-15: *"When 75 407 DB is set to length 3, only
        // BottomMainAPre is offered (2 away from the station), but Tunnel should also be offered since it is 3 away."*
        // A train exactly as long as the track to a sensor has its tail at that sensor, and it is offered.
        if (beyond < 0) return false;

        // The rail as the train drove it: it arrives at the square ahead (OB-227).
        road.add(0, hop);
        walked.add(placeOf(behind));

        into.add(new Choice(behind, road));

        int crossedBeyond = 0;

        Set<String> seen = new LinkedHashSet<>();

        for (Edge candidate : layout.getIncomingEdges(behind))
        {
            // ONLY RAILS THAT ARRIVE HERE, as at the first hop (OB-227).
            if (candidate.getEnd() != behind) continue;

            Point further = candidate.getStart();

            // BY PLACE (TLR-B2): a square a train may turn at is a lane copy and a turning copy, the same metal under
            // two names, and counted by name it was a second road back.
            if (further == null || walked.contains(placeOf(further)) || !seen.add(roadKeyOf(candidate))) continue;

            if (back(layout, candidate, further, behind, beyond, road, walked, into, forks, depth + 1)) crossedBeyond++;
        }

        if (seen.size() > 1 && crossedBeyond > 0) forks[0]++;

        walked.remove(placeOf(behind));
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

            // The next sensor towards the train says which road it is, usually (TLR-C3).
            choice.label = count.get(name) > 1 && choice.getRoad().size() > 1
                ? I18n.f("autolayout.ui.tailCrossedVia", name, viaNames(choice, shown, 1)) : name;
        }

        // AND WHERE THAT IS NOT ENOUGH, every sensor on the way (TLV-B3): two roads that part and meet again before the
        // next sensor read the same by it.
        Map<String, Integer> again = new LinkedHashMap<>();

        for (Choice choice : choices) again.put(choice.label, again.getOrDefault(choice.label, 0) + 1);

        for (Choice choice : choices)
        {
            if (again.get(choice.label) > 1 && choice.getRoad().size() > 1)
            {
                choice.label = I18n.f("autolayout.ui.tailCrossedVia", shownName(shown, choice.getFarthest()),
                    viaNames(choice, shown, Integer.MAX_VALUE));
            }
        }
    }

    /**
     * The sensors between a choice and the train, nearest the choice first, as the operator reads them.
     *
     * @param choice the choice
     * @param shown what to call a point on screen
     * @param most how many to name
     * @return the names, joined
     */
    private static String viaNames(Choice choice, Function<String, String> shown, int most)
    {
        List<String> names = new ArrayList<>();

        Point previous = choice.getFarthest();

        for (int i = 0; i + 1 < choice.getRoad().size() && names.size() < most; i++)
        {
            Edge rail = choice.getRoad().get(i);

            // The end of this rail that is not where the walk along the road just was - rails may be laid either way.
            Point next = rail.getStart() != null && rail.getStart().isSamePlaceAs(previous) ? rail.getEnd() : rail.getStart();

            if (next == null) break;

            names.add(shownName(shown, next));

            previous = next;
        }

        return String.join(", ", names);
    }

    /**
     * One square, whatever it is called: the block a split square's copies share, or the Point's own name.
     *
     * @param point a Point
     * @return the key
     */
    private static String placeOf(Point point)
    {
        return point.getBlock() != null ? "block " + point.getBlock() : "point " + point.getName();
    }

    /**
     * One road back, by the METAL it runs over: the square it comes from and the places the rail crosses (OB-276).
     *
     * Adam, 2026-09-23: *"when pasting 75 407 DB on bottomsecondary, the tail question lists rampdown twice in the
     * list."*  Measured on his railway: the two were `RampDown (northbound, reverse) -> BottomSecondary` and
     * `RampDown (southbound) -> BottomSecondary`, over exactly the same places, 21,7 to 13,11.  The turning copy of the
     * northbound lane is a train that came in from the south and turned, so it leaves south - by the same rail as the
     * southbound lane.  Keyed by LANE, as `roadKeyOf(Point)` keys, they were two roads: the list offered one answer
     * twice, and the junction count behind `wouldAsk` counted a junction that is not there, so the question was put
     * where both answers describe the same track.  21 such pairs on his railway, at five squares, every one at RampDown
     * or BottomMainPost.
     *
     * A balloon's two ends are still two roads: they reach the square by different rails, so their places differ.  A
     * rail with no places - a hand-written configuration, or one from before 3.0.0 - falls back to the lane.
     *
     * @param rail a rail arriving towards the train
     * @return the key
     */
    static String roadKeyOf(Edge rail)
    {
        if (rail == null) return "";

        List<String> places = rail.getPlaceIds();

        if (places == null || places.isEmpty() || rail.getStart() == null) return roadKeyOf(rail.getStart());

        return placeOf(rail.getStart()) + " over " + places;
    }

    /**
     * @param point a Point
     * @return whether it is the turning copy of a lane - "X (eastbound, reverse)" beside "X (eastbound)"
     */
    private static boolean isATurningCopy(Point point)
    {
        return point != null && !laneOf(point.getName()).equals(point.getName());
    }

    /**
     * @param point a Point
     * @return whether it is a turning copy, for a test that has to say which of two copies was kept
     */
    public static boolean isATurningCopyForTests(Point point)
    {
        return isATurningCopy(point);
    }

    /**
     * One road's end: the square AND the lane (TLW-B1).
     *
     * A square's turning copy runs on the lane of the copy it turns (the builder names it "X (eastbound, reverse)"
     * beside "X (eastbound)"), so the two are one road.  Its copies for the two arrival sides are the square's two
     * ends - a balloon reaches one directly and the other round its loop - and those are two roads.  Read from the
     * name because the name is where the builder writes the lane; a Point with no block is its own road.
     *
     * @param point a Point
     * @return the key
     */
    static String roadKeyOf(Point point)
    {
        return point.getBlock() == null ? "point " + point.getName()
            : "block " + point.getBlock() + " lane " + laneOf(point.getName());
    }

    /**
     * The name with its turning mark taken off: "X (eastbound, reverse)" -> "X (eastbound)", "X (reverse)" -> "X".
     *
     * @param name a Point name
     * @return the lane's name
     */
    static String laneOf(String name)
    {
        return name == null ? "" : name.replace(", reverse)", ")").replace(" (reverse)", "");
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
