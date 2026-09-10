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
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train that cannot reverse is only turned round where nobody would call that service.
 *
 * Both of these come from Adam watching his own layout run. He saw two non-reversible trains sent to
 * an ordinary platform and turned round there, through a reversing point - and he saw the platform's
 * caption showing one train twice, facing both ways at once.
 *
 * The two are not the same fault, but they share a cause worth stating: a square is several Points,
 * one per side a train can arrive by, and a rule written about one of them is not a rule about the
 * square. The reversal rule had been written about the terminus flag and not about the other way a
 * layout says "turn round here"; the caption had been written about Points and not about trains.
 */
public class testNonReversibleTrains
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);

        // The sensors these points stand on.  A destination Point insists on a feedback that exists,
        // which is the model refusing to describe a platform with no way of knowing a train is there.
        model.newFeedback(170, null);
        model.newFeedback(171, null);
        model.newFeedback(172, null);
        model.newFeedback(173, null);
    }

  /**
     * A terminus is offered to a locomotive that cannot reverse, and never chosen for it.
     *
     * The long-standing rule, kept here because the round that changed everything around it is
     * exactly when a rule like this gets lost. A terminus is a place a train can only leave by
     * reversing, so sending one there that cannot is sending it somewhere it cannot leave.
     */
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

            // ALLOWED AT EXECUTION, as of Adam's ruling of 2026-09-01.
            //
            // "In manual operation, non reversing trains must be able to back into a terminus if the
            // graph makes that possible.  Otherwise we'd need a third kind of station."
            //
            // isPathClear is the tier EVERY door passes through, so refusing here refused the
            // right-click menu too. Measured on his own layout: 2-8-4 3505 SP is non-reversible,
            // stands at TopMainR2, and there is a five-edge route to TopMainR0Park - the graph makes
            // it possible and this said no.
            //
            // The rule has not gone; it has moved to where the reversing-station rule already lived,
            // on the doctrine written beside it: "Filtering at selection, never refusing at
            // execution." The half of this test that matters is now the one below.
            assertTrue(layout.isPathClear(pathAcross(layout), loc, false),
                "a locomotive that cannot reverse was refused a terminus at EXECUTION, which refuses "
                + "the operator asking for it by hand as well as autonomy");

            loc.setReversible(true);

            assertTrue(layout.isPathClear(pathAcross(layout), loc, false),
                "and one that can reverse must still be allowed there - a rule that refused "
                + "everybody would pass the line above and close the terminus");

            // AND THE HALF THAT KEEPS THE RULE: autonomy will not CHOOSE it.
            //
            // Asked through the explainer, which is the one list of standing reasons a station is
            // never picked - the same list pickPath's filter mirrors, and the one the "no available
            // paths" window prints.
            loc.setReversible(false);

            // ON THE GRAPH, because the explainer answers about a locomotive that is somewhere: with
            // nothing placed there are no paths to enumerate and it reports on nothing at all, which
            // would make the assertion below pass for the wrong reason.
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

    // Where the reversing-point rule actually lives, so nobody looks for it here.
    //
    // "In full autonomy a train is only ever reversed at a terminus" is a rule about which routes
    // autonomy CHOOSES, not about which routes are legal - a hand-driven move and the staging planner
    // may both use a headshunt. It is therefore tested in
    // test/core/testLayoutPickPath.java's testFullAutonomyDoesNotDriveThroughAReversingPoint, beside
    // the other choosing rules, and a first attempt to put it here took the manual route and the
    // staging run out with it.
    //
    // This used to be a @Test method whose entire body was assertNotNull(testLayoutPickPath.class,
    // ...) - a compile-time-guaranteed non-null that asserted nothing and only occupied a slot in the
    // test count. A comment says the same thing without doing that.

    /**
     * One locomotive on several copies of a square is one train in the caption, not several.
     *
     * Locking a path RESERVES every point along it for that train, deliberately without taking it off
     * anywhere else - that is how a junction is held against a second train. Where a path runs through
     * two copies of one square, the train really is on both, and the caption showed it twice with a
     * different arrow each time: "[BR &lt; |BR &gt;]", one train apparently facing both ways.
     */
    @Test
    public void testOneTrainIsNotShownTwice() throws Exception
    {
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        // Two copies of one platform, both holding the same train.  Points built by hand have no
        // layout behind them and so do not sweep, which is what makes this state constructible here -
        // and it is the state a locked path produces in life.
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

    /**
     * Two different trains on one platform are still both shown.
     *
     * This is a real thing on a real layout - two trains sent to one platform from opposite ends, each
     * arriving on the copy facing its own way - and the caption exists to say so. A fix that collapsed
     * them would hide a train.
     */
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
     * Nobody is prompted unless a MANUAL send reaches a may-reverse point (Adam, 2026-09-06).
     *
     * **"Be sure to add tests that confirm no prompting unless manual is sending to a 'may reverse'",
     * point.  This means no prompting in return home or auto, and prompting in all types of manual."**
     *
     * **That request found a defect.** `ALWAYS_REVERSE` is what the four-argument overload hands to
     * autonomy's own loop and to the timetable, which is what Return Home runs. It is not null, so it
     * fell through to the manual branch, answered yes to everything, and would have turned staged
     * trains at every plain copy they passed - the "may" promoted to "must" that `AutonomyBuilder`
     * refuses to emit. It counts as nobody now.
     *
     * The behaviour is asserted here and the WIRING by the census below, because a rule that behaves
     * correctly and a door that hands it the wrong policy are two different defects.
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

        // A policy that RECORDS being asked, so "was anybody prompted" is measured rather than
        // inferred from the answer.
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

        // AND THE DOOR CAN SAY THAT A SQUARE IS ASKED ABOUT, which is the half the runtime cannot
        // answer at all.
        //
        // `canReverse` never reaches `parseAuto` - `AutonomyBuilder` skips it with "it is the
        // instruction to split, not something parseAuto knows" - so on a square that cannot be split,
        // the operator's marking leaves NO trace here.  Adam met that twice: he marked a square
        // may-reverse, sent a train to it, and nothing asked, because both earlier fixes looked for a
        // flag in the running layout.
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

        // And the default answer is still the runtime's own, so a policy that does not care behaves
        // exactly as it did.
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
     * Every door into `executePath` either asks a person or is one that has nobody to ask.
     *
     * Adam: *"prompting in all types of manual."*  The rule above is right and says nothing about
     * which doors hand it a person - and a new door written with four arguments inherits
     * `ALWAYS_REVERSE` silently, which is how it would come to be the one that never asks.
     *
     * Counted rather than located: the two hand-driven doors pass a prompt, and the two unattended
     * ones take the shorter overload.
     */
    @Test
    public void testEveryManualDoorHandsOverAPrompt() throws Exception
    {
        final String[][] doors =
        {
            {"src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java", "the track diagram"},
            {"src/org/traincontrol/gui/AutoLocomotiveStatus.java", "the Locomotive commands tab"},
        };

        for (String[] door : doors)
        {
            java.io.File file = new java.io.File(door[0]);

            assertTrue(file.exists(), "precondition: " + door[0] + " has to be readable, or this test "
                + "reports every door as silent and means nothing");

            String source = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", " ");

            // Either door may hand over the shared policy or call the prompt itself; what the census
            // refuses is a door that hands over neither.
            // Any of the three shapes counts as handing over a prompt.  `forJourney` is the one both
            // doors use since Adam asked for the question to be put BEFORE dispatch rather than from
            // inside the run: "make it be on departure itself, that way there is no dispatch prior to
            // user input."
            assertTrue(source.contains("ManualReversalPrompt.forJourney(")
                    || source.contains("ManualReversalPrompt.forOperator(")
                    || source.contains("ManualReversalPrompt.ask("),
                door[1] + " (" + door[0] + ") dispatches trains without handing executePath a "
                + "prompt, so a may-reverse point on that route turns the train with nobody asked");
        }

        // AND THE UNATTENDED ONES DO NOT, which is the other half: the timetable is what Return Home
        // runs, and asking a dialog about a turn the planner chose would be asking about a decision
        // the operator already made.
        String layout = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/automation/Layout.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        // COMMENTS STRIPPED FIRST.  This read the whole file and failed on a javadoc that names
        // `ManualReversalPrompt` while explaining why the automation layer must not call it - a guard
        // that cannot tell an explanation from an instruction reports on prose rather than on the
        // program, and this is the third one in this repository to do it.
        String code = layout.replaceAll("(?s)/[*].*?[*]/", " ").replaceAll("//[^\\r\\n]*", " ");

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
     * **This dissolved `REG7-A1` rather than working around it.**  That finding was a journey routed
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
     * nothing had ever run (CONF2-B2).
     *
     * **This is the gap that let one defect survive a fix, a review and a confirming pass.**  Every
     * behavioural case in this class hands `shouldReverseAt` a `(train, where) -> ...` lambda, and a
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
        String prompt = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/ManualReversalPrompt.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int journey = prompt.indexOf("forJourney(");

        assertTrue(journey > 0, "forJourney has been renamed and this check now guards nothing");

        int asks = prompt.indexOf("public boolean asksAbout(Point at)", journey);

        assertTrue(asks > 0, "the journey policy no longer answers asksAbout");

        StringBuilder code = new StringBuilder();

        // Comments stripped: the paragraph above this method explains the defect by name, and a guard
        // that reads its own explanation as code reports on prose rather than on the program.
        for (String line : prompt.substring(asks,
            prompt.indexOf("            }", asks)).split("\\r?\\n"))
        {
            String trimmed = line.trim();

            if (!trimmed.startsWith("//")) code.append(trimmed).append(" ");
        }

        // A WORD, not a substring: the first version of this check searched for "turn" and found it
        // inside "return", so it failed on the correct code it was written to protect.
        assertFalse(code.toString().matches(".*\\bturn\\b.*"),
            "the journey policy answers asksAbout using the operator's answer. That lets a \"keep "
            + "direction\" make every may-reverse turning copy look compulsory to shouldReverseAt, "
            + "which turns the train against the explicit no (CONF2-B2). The answer belongs in "
            + "shouldReverse; asksAbout is about the railway");
    }
    /**
     * A square trains MAY turn at is asked about on every copy, not only the turning one
     * (Adam, 2026-09-06).
     *
     * **"May reverse should always prompt in manual mode."**
     *
     * `reversing` is emitted for a MUST-reverse square and for a turning COPY. A may-reverse square
     * carries no flag at all - the build expresses it by SPLITTING the square, and `AutonomyBuilder`
     * explains why it must not do otherwise: putting the flag on a may-turn square "silently promoted
     * the user's choice to the other one, and made a through station one no path could be routed
     * through".
     *
     * So asking `isReversing()` asked nothing on exactly the squares Adam was testing: whether the
     * question appeared depended on which copy a path happened to end at, which is not something
     * anybody can see from the menu. The question is about the PLACE, and the copies of a place are
     * the Points sharing its block.
     *
     * **Autonomy is deliberately not widened.** It turns where the flag says and nowhere else - the
     * promotion of "may" to "must" is the thing the build refuses to emit.
     *
     * MUTATION: ask `isReversing()` instead of `mayReverseAt` and the second assertion fails; widen
     * the autonomy branch too and the third does.
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

        // THE CONTROL: the plain copy carries no flag, which is the whole reason the old rule missed
        // it.  If this ever becomes true the fixture has stopped modelling a split.
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
     * A hand-driven send does not turn a train at a may-reverse point unless somebody says so
     * (Adam, 2026-09-06).
     *
     * **The intent lives in the leg that has not happened yet.** A reversing point turns whatever
     * passes it, and the reason may be "back into that berth next time" or may be nothing at all.
     * Adam: *"in manual mode, the system has no way of knowing that the intention is to reverse into a
     * berth on the next turn.  So we can't possibly both allow that and disallow it based on train."*
     * So it is asked, and `executePath` takes the answer as a policy.
     *
     * **A true terminus is never asked about** - the other half of the ruling: *"no unprompted
     * reversals in manual mode unless going to a true terminus (must reverse)."* There the train has
     * run out of track and the turn is not a choice.
     *
     * **Run as the rule rather than as a journey.** The first version of this drove a train along a
     * path; it hung waiting for an arrival that the fixture cannot produce, which is a test that has
     * to be killed rather than one that fails. The rule is what encodes the ruling, so the rule is
     * what is run - and the call site is pinned by the test below, because naming a rule moves the
     * defect to the call.
     */
    @Test
    public void testAManualSendDoesNotTurnATrainUnasked() throws Exception
    {
        Layout layout = backingInLayout();

        Point ordinary = layout.getPoint("BACK_mid");
        Point terminus = layout.getPoint("BACK_end");
        Point plain = layout.getPoint("BACK_start");

        // THE TWO FLAGS ARE MUTUALLY EXCLUSIVE, which is what the first version of this test got
        // wrong: it set the terminus reversing and the model refused - "Terminus stations cannot be
        // set as reversing".  So a rule asking whether THIS point is both could never fire, and the
        // terminus that matters is the journey's DESTINATION.
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

        // A JOURNEY TO A TERMINUS IS NOT A QUESTION, whatever the answer: the turn on the way is how
        // the train gets there at all, which is Adam's MT-245 ruling.
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

    /**
     * And `executePathInternal` actually asks it (Adam, 2026-09-06).
     *
     * The test above pins the rule. Naming a rule moves the defect to the call site, which is this
     * repository's recurring shape - so the call is checked too.
     */
    @Test
    public void testTheRunAsksThatRule() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/automation/Layout.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        String flat = source.replaceAll("\\s+", " ");

        // THE INTERMEDIATE POINTS.
        assertTrue(flat.contains(
            "shouldReverseAt(current, path.get(path.size() - 1).getEnd(), loc, reversals)"),
            "executePathInternal no longer decides an intermediate reversal through shouldReverseAt, "
            + "so the rule is tested here and something else decides what the railway does");

        // AND THE ARRIVAL, which is a SECOND site and was asking nobody (DIR-A2).
        //
        // The policy is consulted from inside `if (i != path.size() - 1)`; the last point is turned
        // forty lines below the loop by a different statement. So a hand-driven send whose
        // DESTINATION is a may-reverse point turned the train without a word - the literal case the
        // feature was built for. A call-site check that knew about one site reported clean about that.
        assertTrue(flat.contains(
            "if (arrived.isTerminus() || shouldReverseAt(arrived, arrived, loc, reversals))"),
            "the arrival does not consult the policy, so a journey that ENDS at a may-reverse point "
            + "turns the train without asking (DIR-A2)");

        // AND THE TRAIN IS STOPPED WHERE IT IS GOING TO BE TURNED.
        //
        // This began as DIR-A1 - "stopped before anybody is asked" - because the dialog was modal, had
        // no time limit, and everything that stopped the train sat inside the branch the answer
        // decides: measured at line speed when the question was put, and still at line speed five
        // seconds later.
        //
        // **That rationale is spent, and the condition changed with it.**  `df584d0b` moved the
        // question to departure, so nobody is asked mid-journey any more and there is no dialog to
        // outrun.  What is left is the turn itself, which still needs the train standing.
        //
        // `mayReverseAt` is the wrong test for that and was reported twice (SPEC-A2, REG6-B3): blocks
        // are per-square, so it is true at the PLAIN copy of a split square as well as the turning
        // one, and autonomy - which reaches this line through ALWAYS_REVERSE - stopped dead and
        // re-accelerated at plain copies it used to pass at line speed.  `current.isReversing()` is
        // the per-copy question, and the operator half is asked of the door beside it.
        //
        // Pinned as source because there is no way to observe a stop that does not happen without
        // driving a train, and a test that drives one through `executePath` hangs this suite.
        // THE STOP IS THE GATE'S OWN ANSWER, so the two cannot disagree (CONF-A2).
        //
        // Three versions of this: two expressions meant to agree and which did not; then one shared
        // predicate, which agreed by construction but still had to be kept in step by hand as the gate
        // grew clauses; and now the stop simply asks `shouldReverseAt`.  A train is brought to a stand
        // exactly when it is about to be turned.
        //
        // Only safe because `df584d0b` moved the question to departure - while the policy could put a
        // modal dialog up from inside the run, asking before stopping was the whole of DIR-A1.
        assertTrue(flat.contains(
            "if (isCurrentLayout() && shouldReverseAt(current, path.get(path.size() - 1).getEnd(), "
            + "loc, reversals)) { loc.setSpeed(0).waitForSpeedBelow(1);"),
            "the stop and the gate are two expressions again, so a train can be turned somewhere it "
            + "was not stopped, or stopped somewhere it will not be turned (CONF-A2, REG6-B4)");

        // AND A COMPULSORY TURN NEVER REACHES A POLICY (CONF-A1).
        //
        // Removing REG6-A1's prompt left the OUTCOME behind: the rule fell through to shouldReverse,
        // which for a manual policy answers "keep direction", so the turn was skipped unconditionally
        // and the hazard went from "if the operator presses the default" to "always".
        //
        // The door is asked because it is the only thing that can tell a compulsory turn from a
        // may-reverse one: `asksAbout` comes from mayTurnTiles(), the reversible squares MINUS the
        // compulsory ones.  That is also why `asksAbout` must not depend on the ANSWER - when it
        // briefly returned `turn && ...`, "keep direction" made every may-reverse turning copy look
        // compulsory and turned the train against an explicit no.
        assertTrue(flat.contains(
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
        assertTrue(flat.contains("loc.setSpeed(resume).waitForSpeedAtOrAbove(resume);"),
            "the reversal stop gives the point speed multiplier back when it accelerates again "
            + "(REG6-B3)");

        assertTrue(flat.contains("(double) speed * current.getSpeedMultiplier()), 100);"),
            "the resume speed is no longer computed from this point multiplier, so whatever it now "
            + "uses is not the limit the operator set for this square");
    }
    /**
     * ...but it may BACK INTO one, when the way there turns it round (Adam, 2026-08-31).
     *
     * His words, on MT-245: "trains should be allowed to back into terminuses if they are not
     * reversible (that's why we have the reversing point at feedback 2013)."
     *
     * The rule above is about a train that would have to reverse to LEAVE. A train that passes a
     * reversing point on the way arrives at the terminus already turned - it backs in - and leaves
     * forwards, so it never runs backwards out of anywhere and the objection does not apply.
     *
     * Measured on his own layout before this was changed: TunnelLeftPark is a terminus, and EN57-203
     * and EN57-947 are both non-reversible, so this one clause was refusing both the manual send and
     * the home. `isAutoDestination` is asked only by pickPath, never by getPossiblePaths or
     * isPathClear, so a non-automatic station was always manually selectable - this was the only thing
     * standing in the way of either.
     *
     * The escape is deliberately here and not in canRest: whether a train can be TURNED on the way is
     * a property of the route, and only a route can answer it.
     *
     * MUTATION: dropping the reversesAlongTheWay clause fails this; dropping the whole terminus rule
     * fails the method above, whose fixture has no reversing point.
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
     * A train too long for the berth is not backed over the switch (Adam, 2026-09-01).
     *
     * "if there is a switch right next to the station and the train is longer than the length of the
     * station track plus switch track, do we have guards against backing over it?"  There were none.
     * `validateTrainLength` is the only length rule in the model and it compares the train against a
     * MAXIMUM SOMEBODY TYPED on the station, not against the track that is actually there - and it is
     * skipped entirely when that maximum is zero, which is most squares on a real layout.
     *
     * So a train reversing into a berth shorter than itself stood across the switch behind it, and
     * nothing objected. This refuses the path instead.
     *
     * **Only where the answer is knowable.** The rule reads the lengths that have been recorded, and
     * unmeasured track is not a short berth - it is an unknown one. A layout that records no lengths
     * at all is unaffected; a layout that records some gets a notice in the editor asking for the ones
     * that matter, which is the other half of what he asked for.
     *
     * MUTATION: dropping the reversal test refuses nothing.
     *
     * The second mutation this used to name - "comparing against the whole path rather than the track
     * at the reversal" - was two things wrong (TS3-C3).  This fixture cannot tell them apart: its path
     * is two edges of 2 and 3 against a train of 10, so 5 and 3 both refuse.  And since Adam's ruling
     * of 2026-09-01 the whole run in IS the rule - `measuredRoomAtTheEndOf` sums every segment - so
     * it described the shipped code rather than a mutation.  What covers that distinction properly is
     * `testTheRoomIsEverySegmentLeadingUpToTheReversal`, two methods down, on a three-segment fixture.
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

            // And the same railway with a train that fits.
            loc.setTrainLength(4);

            assertTrue(layout.isPathClear(pathThrough(layout), loc, false),
                "a train that fits in the berth and its approach was refused, so the rule refuses "
                + "more than it was asked to");

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
     * With no switch on the route, the room is the WHOLE run in (Adam, 2026-09-01).
     *
     * "Do you sum the track segments leading up to it?  if they are long enough, then we are good.  if
     * segments < train length, then we can't reverse over the switch."
     *
     * **NARROWED on 2026-09-02, and this test is the half that survived.**  He ruled that the
     * measurement is "between the switch and the station", so the whole-route sum now applies only
     * where the route crosses no switch at all - which is this fixture, whose edges are hand-built and
     * carry no switch.  `testTheRoomIsMeasuredFromTheLastSwitch` is the other half.
     *
     * The first version of the guard added the last two edges, reading "the station track plus switch
     * track" as a count of segments rather than as an example of them - which is stricter than his
     * rule everywhere the run in is longer than that, and refuses trains that fit.
     *
     * Three segments is the shortest fixture that can tell the two apart: 2 + 3 + 4 is nine, the last
     * two are seven, and a train of eight fits under his rule and not under the first one.
     *
     * MUTATION: summing only the last two edges fails the first assertion.
     */
    @Test
    public void testTheRoomIsEverySegmentLeadingUpToTheReversal() throws Exception
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

            assertTrue(layout.isPathClear(longPath(layout), loc, false),
                "a train of eight was refused a run in of nine, so the room is being measured over "
                + "part of the approach rather than all of it");

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
            // So the measured segments still bind.  Nine units remain measured here and ten does not
            // fit in nine, whatever the unmeasured piece turns out to be.
            layout.getEdge("LONG_b", "LONG_mid").setLength(0);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "an unmeasured segment cancelled a refusal the measured ones had already earned. Ten "
                + "does not fit in the nine units that ARE measured, and an unknown length behind them "
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
     * With a switch on the route, the room is only the track after it (Adam, 2026-09-02).
     *
     * He was asked whether a train longer than berth-plus-switch may still come to rest across the
     * switch behind its berth when the run in as a whole is long enough, and answered: *"it depends on
     * the direction.  if the train crosses the fork through the base, then the track after the switch
     * has to be long enough to accommodate it.  in other words, between the switch and the station,
     * the length must be >= length of the train."*  Asked which crossings that covers, since on a
     * simple turnout every route touches the toe: *"for the switches, for simplicity, let's use any
     * direction, that way we are guaranteed to be safe."*
     *
     * So the binding constraint is the LAST switch before the destination, whichever way the route
     * crosses it.  A train longer than what is left beyond it stands on the points, blocking every
     * route through them, while the model records it only at the berth.
     *
     * **The same fixture as the test above, and that is the point.**  Nine units of run in, four of
     * them after the switch.  A train of eight passed the old rule and fails this one; nothing about
     * the layout changed except that the last edge now says where its switch is.
     *
     * MUTATION this catches: summing the whole path again - the first assertion passes a train of
     * eight into four units of room.  Also: counting the switch tile itself, which would make the
     * third assertion accept a train that does not fit.
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

            // The last edge crosses a switch, and four of its units lie beyond it.  This is what the
            // reducer records from the diagram; here it is set by hand, because this test is about
            // what the guard does with it.
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

            // AND AN EARLIER UNMEASURED EDGE NO LONGER MATTERS, which is a widening rather than a
            // narrowing: the guard counts only the edges it actually uses.
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
     * Four points, so the run in to the terminus is three segments long.
     */
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

    /**
     * The one path such a layout has.
     */
    private static List<Edge> longPath(Layout layout)
    {
        List<Edge> path = new LinkedList<>();

        path.add(layout.getEdge("LONG_a", "LONG_b"));
        path.add(layout.getEdge("LONG_b", "LONG_mid"));
        path.add(layout.getEdge("LONG_mid", "LONG_end"));

        return path;
    }

    /**
     * Start, a reversing point, and a terminus beyond it.
     */
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

    /**
     * The one path such a layout has, through the reversing point.
     */
    private static List<Edge> pathThrough(Layout layout)
    {
        List<Edge> path = new LinkedList<>();

        path.add(layout.getEdge("BACK_start", "BACK_mid"));
        path.add(layout.getEdge("BACK_mid", "BACK_end"));

        return path;
    }

    /**
     * Two points and one edge between them, with the far end set up as asked.
     *
     * @param reversing whether arriving at the far end turns the train round
     * @param auto whether autonomy may choose the far end as a destination
     */
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

    /**
     * The one path such a layout has.
     */
    private static List<Edge> pathAcross(Layout layout)
    {
        List<Edge> path = new LinkedList<>();

        path.add(layout.getEdge("REV_start", "REV_end"));

        return path;
    }
}
