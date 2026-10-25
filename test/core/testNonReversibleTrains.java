package core;

import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.StationIndex;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import support.SourceOf;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train that cannot reverse is only turned round where nobody would call that service, and a platform caption shows
 * one train once.  A square is several Points, one per side a train can arrive by: a rule about one of them is not a
 * rule about the square.
 */
public class testNonReversibleTrains
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = support.TestStation.simulated();

        // A destination Point insists on a feedback that exists.
        model.newFeedback(170, null);
        model.newFeedback(171, null);
        model.newFeedback(172, null);
        model.newFeedback(173, null);
    }

    /** A terminus is offered to a locomotive that cannot reverse, and never chosen for it. */
    @Test
    public void testATerminusIsOfferedByHandAndNotChosenByAutonomy() throws Exception
    {
        Layout layout = twoPointLayout(false, true);

        layout.getPoint("REV_end").setTerminus(true);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(false);

            // Allowed at execution: isPathClear is the tier every door passes through, so refusing there refused the
            // right-click menu too.  Adam: "In manual operation, non reversing trains must be able to back into a
            // terminus if the graph makes that possible.  Otherwise we'd need a third kind of station."  The rule
            // moved to selection: "Filtering at selection, never refusing at execution."
            assertTrue(layout.isPathClear(pathAcross(layout), loc, false),
                "a locomotive that cannot reverse was refused a terminus at EXECUTION, which refuses "
                + "the operator asking for it by hand as well as autonomy");

            loc.setReversible(true);

            assertTrue(layout.isPathClear(pathAcross(layout), loc, false),
                "and one that can reverse must still be allowed there - a rule that refused "
                + "everybody would pass the line above and close the terminus");

            // And the half that keeps the rule: autonomy will not CHOOSE it.  The explainer is the one list of standing
            // reasons a station is never picked, the same one pickPath's filter mirrors.
            loc.setReversible(false);

            // On the graph: with the loco nowhere there are no paths to enumerate and the assertion would pass
            // vacuously.
            assertTrue(layout.moveLocomotive(loc.getName(), "REV_start", false),
                "could not place the locomotive, so the explainer has nothing to explain");

            String why = layout.explainDestinations(loc).get("REV_end");

            assertNotNull(why,
                "the explainer says nothing at all about the terminus, so autonomy has no recorded "
                + "reason for leaving it alone");

            assertTrue(why.toLowerCase().contains("reversible") || why.toLowerCase().contains("terminus"),
                "autonomy's reason for not choosing a terminus is not about reversing - it said: "
                + why);
        }
        finally
        {
            loc.setReversible(was);
            layout.moveLocomotive(null, "REV_start", true);
        }
    }

    /**
     * Autonomy never picks a square a train that cannot reverse would have to turn on.  Adam: **"Never, unless it's
     * part of a 'return home' backing in manoeuvre."**
     *
     * A TERMINUS copy is refused by `pickPath`'s own filter, a REVERSING copy by `isSendableDestination`; nothing asked
     * the door whether the two add up to his sentence.  Return Home plans through `HomeStaging` with `ALWAYS_REVERSE`,
     * which the execution tier permits, so it is not this door (MT-245).  The control is the same train made
     * reversible, with the terminus given a way back out.
     *
     * MUTATIONS: deleting `(!end.isTerminus() || loc.isReversible())` from `pickPath` fails this; deleting
     * `!end.isReversing()` from `isSendableDestination` does not, since the execution tier refuses a reversing point
     * too.
     */
    @Test
    public void testAutonomyNeverPicksATurningCopyForATrainThatCannotReverse() throws Exception
    {
        for (boolean reversing : new boolean[]{ false, true })
        {
            Layout layout = twoPointLayout(reversing, true);

            if (!reversing) layout.getPoint("REV_end").setTerminus(true);

            // A way back out: autonomy sends even a reversible train to a terminus only where a station is reachable
            // from it again.
            if (!reversing) layout.createEdge("REV_end", "REV_start");

            Locomotive loc = model.getLocByName(model.getLocList().get(0));

            boolean was = loc.isReversible();

            String kind = reversing ? "a reversing point" : "a terminus";

            try
            {
                assertTrue(layout.moveLocomotive(loc.getName(), "REV_start", false),
                    "could not place the locomotive, so there is nothing for autonomy to route");

                loc.setReversible(true);

                if (!reversing)
                {
                    assertNotNull(layout.pickPath(loc),
                        "autonomy picks nothing for a train that CAN reverse either, so the claim"
                        + " below is about a layout where nothing is ever chosen");
                }

                loc.setReversible(false);

                assertNull(layout.pickPath(loc),
                    "autonomy chose " + kind + " for a train that cannot reverse. Adam, 2026-09-13:"
                    + " \"Never, unless it's part of a 'return home' backing in manoeuvre\" - and"
                    + " this is full autonomy, not Return Home. A train sent there is a train that"
                    + " cannot leave.");
            }
            finally
            {
                loc.setReversible(was);
                layout.moveLocomotive(null, "REV_start", true);
            }
        }
    }

    // The rule "in full autonomy a train is only ever reversed at a terminus" is about which routes autonomy CHOOSES,
    // so it is tested in testLayoutPickPath.testFullAutonomyDoesNotDriveThroughAReversingPoint.

    /**
    /**
     * One locomotive on several copies of a square is one train in the caption, not several: a locked path reserves
     * every point along it, so the train is on both copies and the caption showed "[BR &lt; |BR &gt;]".
     */
    @Test
    public void testOneTrainIsNotShownTwice() throws Exception
    {
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        // Points built by hand have no layout behind them and so do not sweep, which makes this state constructible;
        // a locked path produces it in life.
        Point east = new Point("PLATFORM (eastbound)", false, null);
        Point west = new Point("PLATFORM (westbound)", false, null);

        east.setLocomotive(loc);
        west.setLocomotive(loc);

        List<Point> both = new LinkedList<>();
        both.add(east);
        both.add(west);

        assertEquals(StationIndex.oneEntryPerLocomotive(both).size(), 1,
            "the same train was listed once per copy of the platform it had reserved, so one "
            + "locomotive appeared on the diagram as two trains facing opposite ways");
    }

    /** Two different trains on one platform are still both shown; a fix that collapsed them would hide a train. */
    @Test
    public void testTwoTrainsAreStillBothShown() throws Exception
    {
        assertTrue(model.getLocList().size() >= 2, "this test needs two locomotives");

        Point east = new Point("PLATFORM (eastbound)", false, null);
        Point west = new Point("PLATFORM (westbound)", false, null);

        east.setLocomotive(model.getLocByName(model.getLocList().get(0)));
        west.setLocomotive(model.getLocByName(model.getLocList().get(1)));

        List<Point> both = new LinkedList<>();
        both.add(east);
        both.add(west);

        assertEquals(StationIndex.oneEntryPerLocomotive(both).size(), 2,
            "two different trains on one platform must both be shown - dropping one puts a train on "
            + "the layout that is not on the diagram");
    }

    /**
     * Nobody is prompted unless a MANUAL send reaches a may-reverse point.  Adam: **"Be sure to add tests that confirm
     * no prompting unless manual is sending to a 'may reverse'", point.  This means no prompting in return home or
     * auto, and prompting in all types of manual."**
     *
     * `ALWAYS_REVERSE` (autonomy's loop, Return Home) is not null, so it once fell through to the manual branch and
     * counted as somebody; it counts as nobody now.  The behavior is asserted here and the wiring by the census below.
     */
    @Test
    public void testOnlyAManualSendIsEverPrompted() throws Exception
    {
        Layout layout = new Layout(model);

        org.traincontrol.marklin.MarklinFeedback sensor = model.newFeedback(232, null);

        model.setFeedbackState(sensor.getName(), false);

        layout.createPoint("ASK_plain", true, sensor.getName());
        layout.createPoint("ASK_turning", true, sensor.getName());

        layout.getPoint("ASK_plain").setBlock("ASK");
        layout.getPoint("ASK_turning").setBlock("ASK");
        layout.getPoint("ASK_turning").setReversing(true);

        Point plain = layout.getPoint("ASK_plain");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        // A policy that records being asked, so "was anybody prompted" is measured.
        final int[] asked = {0};

        Layout.ReversalPolicy counting = (train, where) ->
        {
            asked[0]++;

            return false;
        };

        // AUTONOMY: no policy at all.
        layout.shouldReverseAt(plain, plain, loc, null);

        // RETURN HOME and autonomy's own loop: the four-argument overload's policy.
        layout.shouldReverseAt(plain, plain, loc, Layout.ALWAYS_REVERSE);

        assertEquals(asked[0], 0,
            "precondition: neither of those hands over the counting policy, so this must still be 0");

        assertFalse(layout.shouldReverseAt(plain, plain, loc, Layout.ALWAYS_REVERSE),
            "a Return Home or autonomy run would turn a train at the PLAIN copy of a may-reverse "
            + "square.  ALWAYS_REVERSE is not null, so it fell through to the manual branch and "
            + "answered yes to everything - the promotion of \"may\" to \"must\" the build refuses "
            + "to emit");

        // MANUAL: a policy that is somebody.
        layout.shouldReverseAt(plain, plain, loc, counting);

        assertEquals(asked[0], 1,
            "a manual send to the plain copy of a may-reverse square asked nobody.  The flag lives on "
            + "the turning copy, so asking isReversing() asked nothing on exactly the squares this is "
            + "for");

        // And an ordinary square is not asked about even in manual.
        org.traincontrol.marklin.MarklinFeedback plainSensor = model.newFeedback(233, null);

        model.setFeedbackState(plainSensor.getName(), false);

        layout.createPoint("ASK_ordinary", true, plainSensor.getName());

        layout.shouldReverseAt(layout.getPoint("ASK_ordinary"), plain, loc, counting);

        assertEquals(asked[0], 1,
            "an ordinary square raised the question, so every manual send on the railway would stop "
            + "the train and open a dialog");

        // The door can say a square is asked about, which the runtime cannot: `canReverse` never reaches `parseAuto`,
        // so on a square that cannot be split the operator's marking leaves no trace in the running layout.
        final int[] askedByDoor = {0};

        Layout.ReversalPolicy fromTheSetup = new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point where)
            {
                askedByDoor[0]++;

                return false;
            }

            @Override
            public boolean asksAbout(Point where)
            {
                return true;
            }
        };

        layout.shouldReverseAt(layout.getPoint("ASK_ordinary"), plain, loc, fromTheSetup);

        assertEquals(askedByDoor[0], 1,
            "a door that says this square IS one the operator marked may-reverse was not consulted.  "
            + "The runtime cannot know that - canReverse is never emitted - so a square the build "
            + "could not split leaves no trace, and the question never appears (Adam, twice)");

        // The default answer is still the runtime's own.
        final int[] lambdaAsked = {0};

        layout.shouldReverseAt(layout.getPoint("ASK_ordinary"), plain, loc, (t, w) ->
        {
            lambdaAsked[0]++;

            return false;
        });

        assertEquals(lambdaAsked[0], 0,
            "a policy that says nothing about which squares to ask over is now consulted everywhere, "
            + "so every manual send stops at every point");
    }

    /**
     * Every door into `executePath` either asks a person or has nobody to ask.  Adam: *"prompting in all types of
     * manual."*  A new door written with four arguments inherits `ALWAYS_REVERSE` silently and would never ask, so the
     * census counts: the two hand-driven doors pass a prompt, the two unattended ones take the shorter overload.
     */
    @Test
    public void testEveryManualDoorHandsOverAPrompt() throws Exception
    {
        // One hand door: the diagram's destinations and the Auto tab's list both send through sendATrainByHand.
        for (String hand : new String[] {"final class LayoutRightclickAutonomyMenu extends JPopupMenu",
            "public final class AutoLocomotiveStatus extends javax.swing.JPanel"})
        {
            // The class itself is the claim: found by its declaration, wherever its file is.
            String source = SourceOf.withoutComments(SourceOf.fileDeclaring(hand));

            assertTrue(source.contains("sendATrainByHand("), hand + " no longer sends through the one hand door");

            assertFalse(source.contains("executePath("), hand + " dispatches a train itself, past the one hand door");
        }

        final String[][] doors =
        {
            {"TrainControlUI.sendATrainByHand", "the one hand door both hand doors send through"},
        };

        for (String[] door : doors)
        {
            String source = SourceOf.declaration("void sendATrainByHand(").replaceAll("\\s+", " ");

            // Any of the three shapes counts as handing over a prompt, before the one hand door's dispatch (by either
            // name it has had).
            int dispatch = source.indexOf("executePathByHand(");

            if (dispatch < 0) dispatch = source.indexOf("executePath(");

            assertTrue(dispatch > 0, "precondition: the one hand door no longer dispatches through executePath or"
                + " executePathByHand, so this checks nothing before it");

            source = source.substring(0, dispatch);

            assertTrue(source.contains("ManualReversalPrompt.forJourney(")
                    || source.contains("ManualReversalPrompt.forOperator(")
                    || source.contains("ManualReversalPrompt.ask("),
                door[1] + " (" + door[0] + ") dispatches trains without handing executePath a "
                + "prompt, so a may-reverse point on that route turns the train with nobody asked");
        }

        // And the unattended ones do not: asking a dialog about a turn the planner chose would be asking about a
        // decision the operator already made.  The automation layer is its package, every file of it (FR-136).
        // COMMENTS STRIPPED FIRST.  This read the whole file and failed on a javadoc that names
        // `ManualReversalPrompt` while explaining why the automation layer must not call it - a guard
        // that cannot tell an explanation from an instruction reports on prose rather than on the
        // program, and this is the third one in this repository to do it.
        String code = SourceOf.codeUnder("src/org/traincontrol/automation/");

        assertTrue(code.contains("public boolean executePath(List<Edge> path, Locomotive loc, int speed,"),
            "precondition: the method every door ends in is no longer in the automation package, so this reads the"
            + " wrong folder");

        assertFalse(code.contains("ManualReversalPrompt"),
            "the automation layer now reaches into the window for a dialog, so an unattended run can "
            + "block on one");
    }
    /**
     * Only the destination is asked about, and an intermediate turns as the path requires.
     *
     * Adam, 2026-09-07: **"It is unnecessary to prompt on intermediates.  We care about the reversal
     * if it's the destination, since that dictates where the train can go, and where it is facing."**
     *
     * **This dissolved the problem rather than working around it.**  The problem was a journey routed
     * through a turning copy the operator was asked about and could decline - "keep direction" being
     * the default, the Escape answer and the cannot-ask answer.  The fix before this one refused such
     * journeys before they started, which was correct and was a whole extra mechanism.  Asking only
     * about the end removes the question from every square where the answer was never the operator's
     * to give, and the refusal became dead code the same hour it was written.
     *
     * An intermediate turning copy exists BECAUSE the path chose to turn there.  That is the route's
     * business, and it now behaves exactly as it does for autonomy.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testOnlyTheDestinationIsAskedAboutAndIntermediatesTurnAsRequired() throws Exception
    {
        Layout layout = new Layout(model);

        org.traincontrol.marklin.MarklinFeedback sensor = model.newFeedback(240, null);

        model.setFeedbackState(sensor.getName(), false);

        layout.createPoint("MID_turn", true, sensor.getName());
        layout.createPoint("MID_end", true, sensor.getName());

        layout.getPoint("MID_turn").setReversing(true);

        Point turning = layout.getPoint("MID_turn");
        Point end = layout.getPoint("MID_end");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        // The door's shape after the ruling: the DESTINATION is the only square it asks about, so
        // that is the only square its answer speaks for.
        final Point asked = end;

        Layout.ReversalPolicy destinationOnly = new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                return at == asked;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return at == asked;
            }
        };

        // AN INTERMEDIATE TURNING COPY TURNS, because the route needs it to and nobody was asked.
        assertTrue(layout.shouldReverseAt(turning, end, loc, destinationOnly),
            "an intermediate turning copy did not turn. Its only outgoing edges leave by the side the"
            + " train arrived from, so the train runs on off its reserved path - which is REG7-A1, and"
            + " asking only about the destination is what was supposed to end it");

        // AND THE DESTINATION OBEYS THE ANSWER, which is the half that is the operator's.
        assertTrue(layout.shouldReverseAt(end, end, loc, destinationOnly),
            "the destination did not turn on an answer of yes");

        Layout.ReversalPolicy keptAtTheEnd = new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                return false;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return at == asked;
            }
        };

        assertFalse(layout.shouldReverseAt(end, end, loc, keptAtTheEnd),
            "the destination turned against an answer of no, so the one question the operator is still"
            + " asked does not decide anything");

        // AND THE INTERMEDIATE STILL TURNS under that same answer - the answer is about the end.
        assertTrue(layout.shouldReverseAt(turning, end, loc, keptAtTheEnd),
            "declining the turn at the DESTINATION also stopped an intermediate turning, so the answer"
            + " is being applied to squares nobody was asked about");
    }
    /**
     * A policy that overrides `asksAbout` is what the doors actually hand over - and until now, what
     * nothing had ever run.
     *
     * **This is the gap that let one defect survive a fix, a review and a confirming pass.**  Every
     * behavioral case in this class hands `shouldReverseAt` a `(train, where) -> ...` lambda, and a
     * lambda takes the interface DEFAULT `asksAbout`, which answers `at.isReversing()`.  The doors do
     * not: `ManualReversalPrompt` overrides it, and that override is the input `shouldReverseAt` uses
     * to tell a compulsory turn from a may-reverse one.  So the branch the railway runs had no test at
     * all, and a change to it read as covered by twelve passing ones.
     *
     * The defect it hid: `forJourney` briefly answered `asksAbout` as `turn && asking.asksAbout(at)`,
     * to avoid braking at may-turn squares on a journey nobody was turning.  That let the ANSWER
     * change the QUESTION - "keep direction" made `asksAbout` false everywhere, every may-reverse
     * turning copy then looked compulsory, and the train turned against the operator's explicit no,
     * with `shouldReverse` never consulted.  The clause was added and removed twice in one day,
     * silently both times.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testTheDoorsOwnAsksAboutDecidesWhichTurnsAreCompulsory() throws Exception
    {
        Layout layout = new Layout(model);

        org.traincontrol.marklin.MarklinFeedback sensor = model.newFeedback(231, null);

        model.setFeedbackState(sensor.getName(), false);

        layout.createPoint("ASK_plain", true, sensor.getName());
        layout.createPoint("ASK_turning", true, sensor.getName());

        layout.getPoint("ASK_plain").setBlock("ASK");
        layout.getPoint("ASK_turning").setBlock("ASK");

        layout.getPoint("ASK_turning").setReversing(true);

        Point turning = layout.getPoint("ASK_turning");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        // A door that says "this square is one the operator has a say over" - a MAY-reverse square.
        // Its answer is no, and the answer must stand.
        Layout.ReversalPolicy asksAndSaysNo = new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                return false;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return true;
            }
        };

        assertFalse(layout.shouldReverseAt(turning, turning, loc, asksAndSaysNo),
            "the operator said keep direction at a may-reverse square and the train turned anyway. "
            + "That is CONF-A1 as it actually shipped: asksAbout is how this rule tells a compulsory "
            + "turn from a choice, so a policy that answers false about a may-reverse square makes it "
            + "look compulsory - which is exactly what forJourney did when the answer was no");

        // The same square, the same shape of policy, the opposite answer.
        Layout.ReversalPolicy asksAndSaysYes = new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                return true;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return true;
            }
        };

        assertTrue(layout.shouldReverseAt(turning, turning, loc, asksAndSaysYes),
            "the operator said turn at a may-reverse square and nothing turned");

        // AND A COMPULSORY TURN: the door does not ask about it, so the answer is irrelevant and the
        // turn happens regardless.  Same policy answer as the first case, opposite outcome - which is
        // the whole distinction, and it is carried entirely by asksAbout.
        Layout.ReversalPolicy doesNotAsk = new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                return false;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return false;
            }
        };

        assertTrue(layout.shouldReverseAt(turning, turning, loc, doesNotAsk),
            "a compulsory turn was skipped because a manual policy answered \"keep direction\". A "
            + "turning copy leaves only by the side the train arrived from, so the train is driven "
            + "forward off its reserved path (CONF-A1)");
    }

    /**
     * And the door's answer to `asksAbout` does not depend on what the operator said.
     *
     * The clause that broke this was `turn && asking.asksAbout(at)`, and it was added and removed twice
     * in one day.  `asksAbout` describes the RAILWAY - which squares anybody has a say over - and
     * `shouldReverse` carries the say.  Letting the second leak into the first is what made a "no"
     * read as "this turn is not a choice".
     *
     * Checked as source because `forJourney` puts a modal dialog up before it returns anything, and a
     * test that shows a dialog cannot run here.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheJourneyPolicyAnswersAsksAboutIndependentlyOfTheAnswer() throws Exception
    {
        String journey = SourceOf.declaration(
            "public static org.traincontrol.automation.Layout.ReversalPolicy forJourney(");

        // Comments stripped: the paragraph above this method explains the defect by name, and a guard
        // that reads its own explanation as code reports on prose rather than on the program.
        String code = SourceOf.withoutComments(SourceOf.declarationIn(journey, "public boolean asksAbout(Point at)"));

        // A WORD, not a substring: the first version of this check searched for "turn" and found it
        // inside "return", so it failed on the correct code it was written to protect.
        assertFalse(java.util.regex.Pattern.compile("\\bturn\\b").matcher(code).find(),
            "the journey policy answers asksAbout using the operator's answer. That lets a \"keep "
            + "direction\" make every may-reverse turning copy look compulsory to shouldReverseAt, "
            + "which turns the train against the explicit no (CONF2-B2). The answer belongs in "
            + "shouldReverse; asksAbout is about the railway");
    }
    /**
     * A square trains MAY turn at is asked about on every copy, not only the turning one.  Adam: **"May reverse should
     * always prompt in manual mode."**
     *
     * `reversing` is emitted for a MUST-reverse square and a turning COPY; a may-reverse square carries no flag (the
     * build splits the square instead), so asking `isReversing()` asked nothing on the copy a path happened not to end
     * at.  The question is about the place: the Points sharing its block.  Autonomy is deliberately not widened.
     *
     * MUTATION: ask `isReversing()` instead of `mayReverseAt` and the second assertion fails; widen the autonomy
     * branch too and the third does.
     */
    @Test
    public void testEveryCopyOfAMayReverseSquareIsAskedAbout() throws Exception
    {
        Layout layout = new Layout(model);

        org.traincontrol.marklin.MarklinFeedback sensor = model.newFeedback(230, null);

        model.setFeedbackState(sensor.getName(), false);

        // The two copies a split makes: one plain, one turning, both the same piece of track.
        layout.createPoint("SPLIT_plain", true, sensor.getName());
        layout.createPoint("SPLIT_turning", true, sensor.getName());

        layout.getPoint("SPLIT_plain").setBlock("SPLIT");
        layout.getPoint("SPLIT_turning").setBlock("SPLIT");

        layout.getPoint("SPLIT_turning").setReversing(true);

        Point plain = layout.getPoint("SPLIT_plain");
        Point turning = layout.getPoint("SPLIT_turning");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        // The control: the plain copy carries no flag, which is why the old rule missed it.
        assertFalse(plain.isReversing(),
            "control: the plain copy of a split must NOT be marked reversing, or this test is about "
            + "a must-reverse square and says nothing about may-reverse");

        assertTrue(layout.mayReverseAt(plain),
            "the plain copy of a may-reverse square is not recognised as somewhere trains may turn.  "
            + "The flag lives on the turning copy and the question is about the place, so a manual "
            + "send that ends here asked nobody anything");

        assertTrue(layout.shouldReverseAt(plain, plain, loc, (t, w) -> true),
            "a manual send to the plain copy did not act on an answer of yes");

        assertFalse(layout.shouldReverseAt(plain, plain, loc, (t, w) -> false),
            "a manual send to the plain copy turned the train against an answer of no");

        // AUTONOMY IS NOT WIDENED: no policy means it turns only where the flag says.
        assertFalse(layout.shouldReverseAt(plain, plain, loc, null),
            "autonomy turned a train at the plain copy of a may-reverse square.  That is the "
            + "promotion of \"may\" to \"must\" the build refuses to emit, and it makes a through "
            + "station one no path can be routed through");

        assertTrue(layout.shouldReverseAt(turning, turning, loc, null),
            "autonomy stopped turning at the turning copy, which is where the flag is");

        // AND A SQUARE WITH NO SPLIT AT ALL is still not a question.
        org.traincontrol.marklin.MarklinFeedback lone = model.newFeedback(231, null);

        model.setFeedbackState(lone.getName(), false);

        layout.createPoint("SPLIT_none", true, lone.getName());

        assertFalse(layout.mayReverseAt(layout.getPoint("SPLIT_none")),
            "an ordinary square with one copy was treated as somewhere trains may turn, so every "
            + "manual send on the railway would raise a dialog");
    }
    /**
     * A hand-driven send does not turn a train at a may-reverse point unless somebody says so.  The intent lives in the
     * leg that has not happened yet, so it is asked and `executePath` takes the answer as a policy.  A true terminus is
     * never asked about: *"no unprompted reversals in manual mode unless going to a true terminus (must reverse)."*
     *
     * Run as the rule rather than as a journey, which hangs waiting for an arrival the fixture cannot produce; the
     * call site is pinned by the test below.
     */
    @Test
    public void testAManualSendDoesNotTurnATrainUnasked() throws Exception
    {
        Layout layout = backingInLayout();

        Point ordinary = layout.getPoint("BACK_mid");
        Point terminus = layout.getPoint("BACK_end");
        Point plain = layout.getPoint("BACK_start");

        // The two flags are mutually exclusive (the model refuses a reversing terminus), so the terminus that matters
        // is the journey's DESTINATION.
        assertTrue(ordinary.isReversing() && !ordinary.isTerminus(),
            "precondition: the middle point must be one trains MAY turn at and not a terminus");

        assertTrue(terminus.isTerminus() && !terminus.isReversing(),
            "precondition: the far point must be a terminus, and the model does not let it also be "
            + "a reversing point");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        // THE ANSWER THAT MATTERS: no means no, at a point that is only a may-reverse.
        assertFalse(layout.shouldReverseAt(ordinary, plain, loc, (train, where) -> false),
            "a hand-driven send would turn the train round at a point it MAY reverse at, against an "
            + "answer of no.  The reason for such a turn lives in the leg after this one, so nothing "
            + "in the path can decide it and the operator is asked (Adam, 2026-09-06)");

        // THE CONTROL, without which "it did not turn" is satisfied by a rule that never turns.
        assertTrue(layout.shouldReverseAt(ordinary, plain, loc, (train, where) -> true),
            "yes did not turn the train either, so the rule is refusing rather than asking");

        // A journey to a terminus is not a question, whatever the answer: the turn on the way is how the train gets
        // there (MT-245).
        assertTrue(layout.shouldReverseAt(ordinary, terminus, loc, (train, where) -> false),
            "a train bound for a terminus was left unturned at the reversing point on the way, "
            + "because the policy said no.  That turn is not a choice - it is how a train backs into "
            + "a terminus, and refusing it strands the journey");

        // And a point nobody turns at is never turned at.
        assertFalse(layout.shouldReverseAt(plain, terminus, loc, (t, w) -> true),
            "a point that is not a reversing point was turned at - and on a journey to a terminus, "
            + "which is the branch most likely to say yes to everything");

        // A caller with no opinion behaves as everything did before there was a policy.
        assertTrue(layout.shouldReverseAt(ordinary, plain, loc, null),
            "a null policy stopped meaning \"always\", so every caller that has no opinion has "
            + "quietly changed behaviour");
    }

    /** And `executePathInternal` actually asks it: naming a rule moves the defect to the call site. */
    @Test
    public void testTheRunAsksThatRule() throws Exception
    {
        String flat = SourceOf.declaration("private boolean executePathInternal(").replaceAll("\\s+", " ");

        // THE INTERMEDIATE POINTS.
        assertTrue(flat.contains(
            "shouldReverseAt(current, path.get(path.size() - 1).getEnd(), loc, reversals)"),
            "executePathInternal no longer decides an intermediate reversal through shouldReverseAt, "
            + "so the rule is tested here and something else decides what the railway does");

        // And the arrival, a second site that was asking nobody: the last point is turned by a different statement
        // from the loop's.  Only "the arrival asks one named rule" is pinned (MT-368); what that rule does is
        // `core.testTheArrivalHonoursTheAnswer`.
        assertTrue(flat.contains("if (turnsOnArrival(arrived, loc, reversals))"),
            "the arrival does not consult the policy through turnsOnArrival, so a journey that "
            + "ENDS at a may-reverse point may turn the train without reading the answer "
            + "(DIR-A2, MT-368)");

        // AND THE TRAIN IS STOPPED WHERE IT IS GOING TO BE TURNED.
        //
        // This began as "stopped before anybody is asked", because the dialog was modal, had
        // no time limit, and everything that stopped the train sat inside the branch the answer
        // decides: measured at line speed when the question was put, and still at line speed five
        // seconds later.
        //
        // **That rationale is spent, and the condition changed with it.**  `df584d0b` moved the
        // question to departure, so nobody is asked mid-journey any more and there is no dialog to
        // outrun.  What is left is the turn itself, which still needs the train standing.
        //
        // `mayReverseAt` is the wrong test for that and was reported twice: blocks
        // are per-square, so it is true at the PLAIN copy of a split square as well as the turning
        // one, and autonomy - which reaches this line through ALWAYS_REVERSE - stopped dead and
        // re-accelerated at plain copies it used to pass at line speed.  `current.isReversing()` is
        // the per-copy question, and the operator half is asked of the door beside it.
        //
        // Pinned as source because there is no way to observe a stop that does not happen without
        // driving a train, and a test that drives one through `executePath` hangs this suite.
        // THE STOP IS THE GATE'S OWN ANSWER, so the two cannot disagree.
        //
        // Three versions of this: two expressions meant to agree and which did not; then one shared
        // predicate, which agreed by construction but still had to be kept in step by hand as the gate
        // grew clauses; and now the stop simply asks `shouldReverseAt`.  A train is brought to a stand
        // exactly when it is about to be turned.
        //
        // Only safe because `df584d0b` moved the question to departure - while the policy could put a
        // modal dialog up from inside the run, asking before stopping was the whole problem.
        assertTrue(flat.contains(
            "if (isCurrentLayout() && shouldReverseAt(current, path.get(path.size() - 1).getEnd(), "
            + "loc, reversals)) { loc.setSpeed(0).waitForSpeedBelow(1);"),
            "the stop and the gate are two expressions again, so a train can be turned somewhere it "
            + "was not stopped, or stopped somewhere it will not be turned (CONF-A2, REG6-B4)");

        // AND A COMPULSORY TURN NEVER REACHES A POLICY.
        //
        // Removing the prompt at compulsory turns left the OUTCOME behind: the rule fell through to shouldReverse,
        // which for a manual policy answers "keep direction", so the turn was skipped unconditionally
        // and the hazard went from "if the operator presses the default" to "always".
        //
        // The door is asked because it is the only thing that can tell a compulsory turn from a
        // may-reverse one: `asksAbout` comes from mayTurnTiles(), the reversible squares MINUS the
        // compulsory ones.  That is also why `asksAbout` must not depend on the ANSWER - when it
        // briefly returned `turn && ...`, "keep direction" made every may-reverse turning copy look
        // compulsory and turned the train against an explicit no.
        assertTrue(SourceOf.declaration("public boolean shouldReverseAt(").replaceAll("\\s+", " ").contains(
            "if (current.isReversing() && !reversals.asksAbout(current)) return true;"),
            "a compulsory turn can now reach a reversal policy, which answers \"keep direction\" - so "
            + "the train is driven forward off a turning copy whose only edges leave by the side it "
            + "came in at (CONF-A1)");

        // AND IT LEAVES AT THE SPEED THIS POINT ALLOWS, not the speed the journey asked for.
        //
        // The exit restored the raw `speed`, discarding the multiplier applied for this same square
        // forty lines above - so a train that stopped to turn resumed at full line speed on a stretch
        // its owner had set to run slow, and stayed there until the next point recalculated.  The stop
        // was added for the reversal question; handing back a speed limit was not part of it.
        //
        // Written through `drive`, which leaves a stop ordered since dispatch standing; its wait for the speed gives up
        // on a stop or a retirement.
        assertTrue(flat.contains("if (drive(loc, resume, stopsAtDispatch)) {")
            && flat.contains("loc.waitForSpeedAtOrAbove(resume, () -> this.stopsOrdered.get() != stopsAtDispatch"
            + " || !this.isCurrentLayout());"),
            "the reversal stop gives the point speed multiplier back when it accelerates again "
            + "(REG6-B3)");

        assertTrue(flat.contains("(double) speed * current.getSpeedMultiplier()), 100);"),
            "the resume speed is no longer computed from this point multiplier, so whatever it now "
            + "uses is not the limit the operator set for this square");
    }
    /**
     * ...but it may BACK INTO one, when the way there turns it round.  Adam on MT-245: "trains should be allowed to
     * back into terminuses if they are not reversible (that's why we have the reversing point at feedback 2013)."
     *
     * The rule above is about a train that would have to reverse to LEAVE; one that passes a reversing point on the
     * way arrives already turned and leaves forwards.  The escape is here and not in canRest: whether a train can be
     * turned on the way is a property of the route, and only a route can answer it.
     *
     * MUTATION: dropping the reversesAlongTheWay clause fails this; dropping the whole terminus rule fails the method
     * above, whose fixture has no reversing point.
     */
    @Test
    public void testATrainThatCannotReverseMayBackIntoATerminus() throws Exception
    {
        Layout layout = backingInLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(false);

            assertTrue(layout.getPoint("BACK_mid").isReversing(),
                "the fixture did not take: the middle point must be a reversing point");

            assertTrue(layout.getPoint("BACK_end").isTerminus(),
                "the fixture did not take: the far point must be a terminus");

            assertTrue(layout.isPathClear(pathThrough(layout), loc, false),
                "a train that cannot reverse was refused a terminus it would have BACKED into. The "
                + "reversing point on the way turns it, so it arrives running backwards and leaves "
                + "forwards - which is what the reversing point is for");
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * A train too long for the berth is not backed over the switch.  Adam: "if there is a switch right next to the
     * station and the train is longer than the length of the station track plus switch track, do we have guards
     * against backing over it?"  `validateTrainLength` compares against a typed maximum, not the track that is there.
     *
     * The rule reads recorded lengths only: unmeasured track is unknown, not short, so a layout that records none is
     * unaffected.
     *
     * Two bounds, not one.  Adam: *"four-unit train in a three-unit berth with a two-unit approach: refused both
     * because of the berth (3<4) and the track length that potentially couldn't fit the train while reversing.  if the
     * train isn't reversing, then it should be accepted as long as the berth is long enough."*  Each is checked on its
     * own with the other made long enough, and the refusal's SENTENCE is read, since two bounds that refuse everything
     * would pass a pair of assertFalses.
     *
     * MUTATIONS: dropping the reversal test from `measuredRoomAtTheEndOf` admits the four-unit train at the berth;
     * dropping `comesToRest` from `whyTooLongForThisRoute` admits it at the turn; and making either bound
     * unconditional fails the last assertion, where both are long enough.
     */
    @Test
    public void testATrainTooLongForTheBerthIsNotBackedOverTheSwitch() throws Exception
    {
        Layout layout = backingInLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();
        Integer wasLength = loc.getTrainLength();

        try
        {
            loc.setReversible(false);

            // The berth and its approach, measured; the train longer than both together.
            layout.getEdge("BACK_mid", "BACK_end").setLength(3);
            layout.getEdge("BACK_start", "BACK_mid").setLength(2);

            loc.setTrainLength(10);

            assertFalse(layout.isPathClear(pathThrough(layout), loc, false),
                "a train ten long was sent to reverse into a berth of three with an approach of two, "
                + "so it would stand across the switch behind it and nothing objected");

            // His figures: three-unit berth, two-unit approach, four-unit train.
            loc.setTrainLength(4);

            assertFalse(layout.isPathClear(pathThrough(layout), loc, false),
                "a four-unit train was admitted to a three-unit berth with a two-unit approach. Both "
                + "of Adam's bounds refuse it (2026-09-11): the berth beyond the turn is 3, and the "
                + "track it stands on while it changes direction is 2");

            // WHICH BOUND, ONE AT A TIME.  A long approach, so only the berth can object.
            layout.getEdge("BACK_start", "BACK_mid").setLength(9);

            String atTheBerth = Layout.whyTooLongForThisRoute(pathThrough(layout), loc);

            assertNotNull(atTheBerth, "a four-unit train was admitted to a three-unit berth because "
                + "the nine units of approach behind the reversal were counted towards it. The train "
                + "turns at BACK_mid and comes to rest beyond it, so only the track beyond it is room");

            assertTrue(atTheBerth.contains("BACK_end"),
                "the berth is the short stretch here and the refusal should name it: " + atTheBerth);

            // A long berth and the short approach back, so only the turn can object.
            layout.getEdge("BACK_start", "BACK_mid").setLength(2);
            layout.getEdge("BACK_mid", "BACK_end").setLength(9);

            String atTheTurn = Layout.whyTooLongForThisRoute(pathThrough(layout), loc);

            assertNotNull(atTheTurn, "a four-unit train was sent to change direction on two units of "
                + "track. It stands still at BACK_mid with its body stretched back over the approach, "
                + "so the approach has to hold it - Adam's second reason, 2026-09-11");

            assertTrue(atTheTurn.contains("BACK_mid"),
                "the track the train waits to turn on is the short stretch here, and the refusal "
                + "should name that square rather than the berth: " + atTheTurn);

            // And with both long enough it runs: two bounds that refused everything would pass every assertion above.
            layout.getEdge("BACK_start", "BACK_mid").setLength(4);
            layout.getEdge("BACK_mid", "BACK_end").setLength(4);

            assertTrue(layout.isPathClear(pathThrough(layout), loc, false),
                "a four-unit train was refused a four-unit berth with a four-unit approach, both of "
                + "which are long enough, so one of the bounds is refusing more than it measures");

            // AND THE CONTROL: with nothing measured anywhere the rule cannot know, and says nothing.
            layout.getEdge("BACK_mid", "BACK_end").setLength(0);
            layout.getEdge("BACK_start", "BACK_mid").setLength(0);

            loc.setTrainLength(10);

            assertTrue(layout.isPathClear(pathThrough(layout), loc, false),
                "a layout that records no track lengths was refused a path on the strength of lengths "
                + "it does not have - unmeasured track is unknown, not short");
        }
        finally
        {
            loc.setReversible(was);
            loc.setTrainLength(wasLength);
        }
    }

    /**
     * A reversal on the route splits the run in, and the train has to fit in both halves.  Adam: *"8 unit train on a
     * 9-unit runin would just be refused because 3<8.  if the max train length at the berth was set to 8, we would be
     * OK.  in short, your tests should consider both."*
     *
     * His figure is neither of the two the rule produces: the berth beyond the turn measures 4 and the approach behind
     * it 2 + 3 = 5.  Both refuse an eight-unit train; the refusal reported is the one met first walking forwards, the
     * approach.  The whole-run-in rule is still tested in the last section, where nothing turns the train.
     *
     * MUTATIONS: dropping `comesToRest` from `whyTooLongForThisRoute` fails this, as does applying either bound where
     * nothing turns round.  Dropping the reversal stop from `measuredRoomAtTheEndOf` does NOT, since every refusal here
     * is reached at the turn; `testATrainTooLongForTheBerthIsNotBackedOverTheSwitch` holds that half.
     */
    @Test
    public void testTheRoomIsBoundedAtTheReversalAndAtTheBerth() throws Exception
    {
        Layout layout = longerBackingInLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();
        Integer wasLength = loc.getTrainLength();

        try
        {
            loc.setReversible(false);

            layout.getEdge("LONG_a", "LONG_b").setLength(2);
            layout.getEdge("LONG_b", "LONG_mid").setLength(3);
            layout.getEdge("LONG_mid", "LONG_end").setLength(4);

            loc.setTrainLength(8);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "an eight-unit train was accepted onto a nine-unit run in with a reversing point in "
                + "the middle of it. Adam, 2026-09-11: \"8 unit train on a 9-unit runin would just be "
                + "refused\" - it comes to rest on the 4 beyond the turn, having waited on the 5 "
                + "behind it, and nine units is neither stretch");

            // Which stretch objects first: the approach, because the route reaches it first.
            String why = Layout.whyTooLongForThisRoute(longPath(layout), loc);

            assertTrue(why != null && why.contains("LONG_mid"),
                "the refusal should name the square the train waits to turn on, whose approach is the "
                + "first stretch too short for it: " + why);

            // Both stretches made long enough for the train, and it runs.
            layout.getEdge("LONG_a", "LONG_b").setLength(4);
            layout.getEdge("LONG_b", "LONG_mid").setLength(4);
            layout.getEdge("LONG_mid", "LONG_end").setLength(8);

            assertTrue(layout.isPathClear(longPath(layout), loc, false),
                "an eight-unit train was refused an eight-unit berth with eight units of approach "
                + "behind the turn, so a bound is refusing more than it measures");

            // AND THE BERTH ALONE IS NOT ENOUGH: the same eight-unit berth with a short approach back,
            // so the train would hang off the end of the measured track while it waited to turn.
            layout.getEdge("LONG_a", "LONG_b").setLength(2);
            layout.getEdge("LONG_b", "LONG_mid").setLength(3);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "an eight-unit train was sent to change direction on five units of track because the "
                + "berth beyond the turn is long enough. Both stretches have to hold it (Adam, "
                + "2026-09-11: \"both need to be long enough\")");

            // Where nothing turns the train, the whole run in counts.  Adam: *"if the train isn't reversing, then it
            // should be accepted as long as the berth is long enough."*  The tail lies back over the run in, one
            // continuous stretch, so the count runs back to the last switch or the start of the route.  A reversible
            // locomotive, because the reversing point is what let a non-reversible one into a terminus.
            layout.getPoint("LONG_mid").setReversing(false);

            loc.setReversible(true);

            layout.getEdge("LONG_a", "LONG_b").setLength(2);
            layout.getEdge("LONG_b", "LONG_mid").setLength(3);
            layout.getEdge("LONG_mid", "LONG_end").setLength(4);

            assertTrue(layout.isPathClear(longPath(layout), loc, false),
                "an eight-unit train was refused a nine-unit run in that nothing splits - no switch "
                + "on it and nothing turning the train - so the room is being measured over part of "
                + "the approach rather than all of it");

            // And ten does not fit in nine.
            loc.setTrainLength(10);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "a train of ten was accepted into a run in of nine, so nothing is being measured");

            // A SEGMENT NOBODY HAS MEASURED NO LONGER CANCELS WHAT IS MEASURED (Adam, 2026-09-06).
            //
            // This required the whole path to go unjudged the moment any segment was unmeasured, on
            // the reasoning that "an unknown length is not a zero one".  He overruled it: **"you need
            // to measure total distance between points, not validate that every edge has a length > 0.
            // It is only indeterminate if the entire logical segment has length 0."**
            //
            // What it cost him: a four-unit train admitted to a berth measured at one, because the
            // unmeasured track behind that one unit made the whole run unjudgeable.  The measured
            // evidence was there and was thrown away for the company it kept.
            //
            // So the measured segments still bind.  The four beyond the unmeasured piece remain
            // measured, and ten does not fit in four whatever the unmeasured piece turns out to be.
            layout.getEdge("LONG_b", "LONG_mid").setLength(0);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "an unmeasured segment cancelled a refusal the measured ones had already earned. Ten "
                + "does not fit in the units that ARE measured, and an unknown length behind them "
                + "cannot unprove it (Adam, 2026-09-06)");

            // AND WITH NOTHING MEASURED AT ALL there is no evidence, which is the half of the old
            // rule that survives: refusing on no information would make an unmeasured layout unusable.
            for (org.traincontrol.automation.Edge e : longPath(layout)) e.setLength(0);

            assertTrue(layout.isPathClear(longPath(layout), loc, false),
                "a path where nothing at all is measured was refused. Zero measured track is no "
                + "information rather than no room - Adam: \"generally, allow it\"");
        }
        finally
        {
            loc.setReversible(was);
            loc.setTrainLength(wasLength);
        }
    }

    /**
     * With a switch on the route, the room is only the track after it.  Adam: *"between the switch and the station,
     * the length must be >= length of the train"*, using any direction for simplicity.  So the binding constraint is
     * the LAST switch before the destination: a train longer than what is left beyond it stands on the points while
     * the model records it only at the berth.
     *
     * The same fixture as the test above: nine units of run in, four after the switch.  A train of eight passed the
     * old rule and fails this one.
     *
     * MUTATION: summing the whole path again lets the first assertion pass a train of eight into four units; counting
     * the switch tile itself makes the third accept a train that does not fit.
     */
    @Test
    public void testTheRoomIsMeasuredFromTheLastSwitch() throws Exception
    {
        Layout layout = longerBackingInLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();
        Integer wasLength = loc.getTrainLength();

        try
        {
            loc.setReversible(false);

            layout.getEdge("LONG_a", "LONG_b").setLength(2);
            layout.getEdge("LONG_b", "LONG_mid").setLength(3);
            layout.getEdge("LONG_mid", "LONG_end").setLength(4);

            // The last edge crosses a switch with four units beyond it; the reducer records this from the diagram.
            layout.getEdge("LONG_mid", "LONG_end").setRoomAtTheEnd(4);

            loc.setTrainLength(8);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "a train of eight was let into four units of track beyond the switch because the "
                + "whole nine-unit run in was counted - which is the rule Adam narrowed: \"between "
                + "the switch and the station, the length must be >= length of the train\"");

            loc.setTrainLength(4);

            assertTrue(layout.isPathClear(longPath(layout), loc, false),
                "a train of four was refused four units of room, so the measurement is short of the "
                + "stretch it is meant to be");

            loc.setTrainLength(5);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "a train of five was accepted into four units of room");

            // BOUNDED BUT UNMEASURED IS BOUNDED BY THE SEGMENT IT LIES IN (Adam, 2026-09-06/07).
            //
            // This required the route to be admitted once the stretch beyond the switch went
            // unmeasured, on the reasoning that an unknown length is not a short one.  True in
            // isolation, and it let a four-unit train into a berth he had measured at one - the case
            // he reported twice.
            //
            // The stretch after the switch is PART OF that segment, so it cannot be longer than it.
            // The segment here measures four; a five-unit train does not fit in four, and therefore
            // does not fit in whatever part of the four lies past the switch.  That is a proof, and it
            // only ever refuses more - the bound over-states the real room, so nothing new is let in.
            layout.getEdge("LONG_mid", "LONG_end").setRoomAtTheEnd(-1);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "a five-unit train was admitted because the stretch beyond the switch is unmeasured. "
                + "That stretch lies inside a segment measured at four, and five does not fit in four "
                + "whatever the unmeasured part turns out to be (Adam, 2026-09-07)");

            // AND WITH THE SEGMENT ITSELF UNMEASURED there is nothing to bound it with, so it is not
            // judged - the surviving half of the old rule.
            layout.getEdge("LONG_mid", "LONG_end").setLength(0);

            assertTrue(layout.isPathClear(longPath(layout), loc, false),
                "an unmeasured stretch inside an unmeasured segment was judged anyway. There is no "
                + "evidence at all there, and refusing on none would make every unmeasured layout "
                + "unusable");

            // And an earlier unmeasured edge no longer matters: the guard counts only the edges it uses.
            layout.getEdge("LONG_mid", "LONG_end").setRoomAtTheEnd(4);
            layout.getEdge("LONG_a", "LONG_b").setLength(0);

            loc.setTrainLength(5);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "an unmeasured edge at the far end of the route made the room unknowable, though "
                + "nothing beyond the last switch depends on it");
        }
        finally
        {
            loc.setReversible(was);
            loc.setTrainLength(wasLength);
        }
    }

    /**
     * A train that cannot reverse is not OFFERED an ordinary terminus by hand (OB-205).  Adam: *"75 407 DB can go from
     * Tunnel to BottomMainC manually, even though it is not reversible and this is not a parking berth (excluded from
     * autonomy).  it should not be allowed to be chosen."*
     *
     * This narrows his earlier ruling rather than reversing it: a berth, which autonomy never chooses, is still
     * offered by hand.
     *
     * MUTATION: dropping `isAutoDestination` from the clause fails the third assertion.
     */
    @Test
    public void testANonReversibleTrainIsNotOfferedAnOrdinaryTerminus() throws Exception
    {
        Layout layout = backingInLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        Point terminus = layout.getPoint("BACK_end");

        try
        {
            assertTrue(terminus.isTerminus(), "the fixture did not take: BACK_end must be a terminus");

            assertTrue(terminus.isAutoDestination(),
                "the fixture did not take: BACK_end must be a square autonomy may choose, or the berth"
                + " exemption below is what is being measured rather than the rule");

            loc.setReversible(false);

            assertFalse(layout.isOfferableToOperator(terminus, loc),
                "a train that cannot reverse was offered an ordinary terminus by hand. It arrives and"
                + " cannot leave: the turn is the only way out and it cannot make it (Adam, OB-205)");

            // AND A TRAIN THAT CAN REVERSE IS STILL OFFERED IT.
            loc.setReversible(true);

            assertTrue(layout.isOfferableToOperator(terminus, loc),
                "a train that CAN reverse was refused a terminus, so the rule refuses on the square"
                + " alone and has stopped being about the locomotive");

            // And a berth is still offered by hand.
            loc.setReversible(false);

            terminus.setAutoDestination(false);

            assertTrue(layout.isOfferableToOperator(terminus, loc),
                "a parking berth was refused to a train that cannot reverse. That is the case the rule"
                + " was moved out of `isPathClear` FOR - \"the operator asking for that berth by hand is"
                + " no longer refused\" - and it is still his ruling");
        }
        finally
        {
            terminus.setAutoDestination(true);

            loc.setReversible(was);
        }
    }

    /**
     * The operator is asked about a turn at a MAY-turn destination, and not at a real terminus.  Adam, OB-205:
     * *"is reversed without a direction prompt, even though it is not reversible"*, and *"when I set it as reversible,
     * there is still no 'should it change direction' prompt."*
     *
     * `AutonomyBuilder` emits the turning copy of a may-turn square with `terminus: true`, so `isTerminus()` cannot
     * tell it from a compulsory terminus and both doors read that flag.  The distinction cannot be drawn from the
     * graph, so the DOOR is asked; this drives that directly, the same call twice with only what the policy says it
     * asks about different.
     *
     * MUTATION: taking `!reversals.asksAbout(destination)` back out fails the second half.
     */
    @Test
    public void testTheOperatorIsAskedAboutATurnAtAMayTurnDestination() throws Exception
    {
        Layout layout = backingInLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        Point turn = layout.getPoint("BACK_mid");
        Point end = layout.getPoint("BACK_end");

        assertTrue(turn.isReversing(), "the fixture did not take: BACK_mid must be a reversing point");
        assertTrue(end.isTerminus(), "the fixture did not take: BACK_end must be a terminus");

        // A REAL TERMINUS: the door does not ask about it, and the turn is how the train gets in.
        final boolean[] consulted = {false};

        Layout.ReversalPolicy silent = new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                consulted[0] = true;

                return false;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return false;
            }
        };

        assertTrue(layout.shouldReverseAt(turn, end, loc, silent),
            "the turn on the way into a real terminus became a preference. It is how a train backs in -"
            + " MT-245, and `testATrainThatCannotReversemayBackIntoATerminus` two methods up");

        assertFalse(consulted[0],
            "the policy was asked about a journey into a real terminus, where there is no answer it"
            + " could give that this rule would honour - which is why the prompt stopped being shown");

        // THE SAME CALL, with a door that says this destination is one the operator marked may-turn.
        final boolean[] asked = {false};

        Layout.ReversalPolicy asking = new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                asked[0] = true;

                return false;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return at == end || at == turn;
            }
        };

        assertFalse(layout.shouldReverseAt(turn, end, loc, asking),
            "the train was turned at a destination the operator marked \"may turn round\" without the"
            + " answer being read. The turning copy of a may-turn square is emitted as a terminus, so"
            + " the terminus branch answered for it - and the prompt, reading the same flag, was never"
            + " shown (Adam, OB-205)");

        assertTrue(asked[0],
            "the policy was never consulted about a may-turn destination, so whatever the operator"
            + " answers cannot reach the railway");
    }

    /** Four points, so the run in to the terminus is three segments long. */
    private static Layout longerBackingInLayout() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("LONG_a", true, "170");
        layout.createPoint("LONG_b", true, "171");
        layout.createPoint("LONG_mid", true, "172");
        layout.createPoint("LONG_end", true, "173");

        layout.getPoint("LONG_mid").setReversing(true);
        layout.getPoint("LONG_end").setTerminus(true);

        layout.createEdge("LONG_a", "LONG_b");
        layout.createEdge("LONG_b", "LONG_mid");
        layout.createEdge("LONG_mid", "LONG_end");

        return layout;
    }

    /** The one path such a layout has. */
    private static List<Edge> longPath(Layout layout)
    {
        List<Edge> path = new LinkedList<>();

        path.add(layout.getEdge("LONG_a", "LONG_b"));
        path.add(layout.getEdge("LONG_b", "LONG_mid"));
        path.add(layout.getEdge("LONG_mid", "LONG_end"));

        return path;
    }

    /** Start, a reversing point, and a terminus beyond it. */
    private static Layout backingInLayout() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("BACK_start", true, "170");
        layout.createPoint("BACK_mid", true, "171");
        layout.createPoint("BACK_end", true, "172");

        layout.getPoint("BACK_mid").setReversing(true);
        layout.getPoint("BACK_end").setTerminus(true);

        layout.createEdge("BACK_start", "BACK_mid");
        layout.createEdge("BACK_mid", "BACK_end");

        return layout;
    }

    /** The one path such a layout has, through the reversing point. */
    private static List<Edge> pathThrough(Layout layout)
    {
        List<Edge> path = new LinkedList<>();

        path.add(layout.getEdge("BACK_start", "BACK_mid"));
        path.add(layout.getEdge("BACK_mid", "BACK_end"));

        return path;
    }

    /** Two points and one edge between them, with the far end set up as asked. */
    private static Layout twoPointLayout(boolean reversing, boolean auto) throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("REV_start", true, "170");
        layout.createPoint("REV_end", true, "171");

        Point end = layout.getPoint("REV_end");

        end.setReversing(reversing);
        end.setAutoDestination(auto);

        layout.createEdge("REV_start", "REV_end");

        return layout;
    }

    /** The one path such a layout has. */
    private static List<Edge> pathAcross(Layout layout)
    {
        List<Edge> path = new LinkedList<>();

        path.add(layout.getEdge("REV_start", "REV_end"));

        return path;
    }
}
