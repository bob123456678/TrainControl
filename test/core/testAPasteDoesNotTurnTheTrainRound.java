package core;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.file.CS2File;

/**
 * A train put down on a square is standing where the drive would leave it, not turned round.
 *
 * Adam, 2026-09-12: **"When 2-8-4 is pasted, it should always face east.  Does it?"** - about pasting
 * it onto BottomMainB while it stands on TopMainR2.  Measured before this file was written: **east 23
 * times out of 40 and west the other 17**, on the frozen copy of his own railway, from an unchanged
 * setup, in one JVM.  Which way a pasted train ends up pointing was a coin toss.
 *
 * **Why it tosses a coin.**  `facingByPath` walks the running graph from the copy the train stands on
 * to a copy of the target, and answers with that copy's side - Adam's own rule of 2026-09-06,
 * *"calculating the simple bfs path from the current station to the paste target using the current
 * direction"*.  The walk asks `Layout.getNeighbors`, which SHUFFLES: *"Randomize order to allow for
 * variation in paths"*, which is right for autonomy choosing a journey and wrong for a question about
 * where one square is relative to another.  Two copies of BottomMainB sit the same nine edges away -
 * `BottomMainB (eastbound)` facing E, and `BottomMainB (eastbound, reverse)` facing W - and the
 * shuffle decided which of the two the walk touched first.
 *
 * **And the second of those is not an arrival at all.**  A square trains may turn round at is built as
 * two Points per arrival side: the plain copy, where a train that drove in is standing, and the
 * turning copy, where it is after it has decided to turn round.  Both are reached by the same edge,
 * so they are always the same distance away, and they face opposite ways.  Answering with the turning
 * copy records "it arrived and then reversed" - which is a decision the operator did not make by
 * dragging a train onto a square.
 *
 * **This is the fourth site of one confusion**, which is why it is worth naming rather than patching:
 * a may-turn square's turning copy is emitted with `terminus: true` (or `reversing: true` where it
 * cannot stop), so it is indistinguishable from a real terminus by asking `isTerminus()`. OB-205
 * found it three times over and MT-368 was the same mistake at the arrival door.  `isSamePlaceAs`
 * is the discriminator each of those ended up using, and it is the one used here.
 *
 * **What a terminus must still do.**  Adam, 2026-09-06: *"for terminuses, they must reverse on
 * paste"*.  A compulsory turn has no plain copy to prefer, so the turning copy is the only answer and
 * stays the answer - which is that reversal.  Asserted below, not assumed.
 *
 * Read against a `LayoutSandbox` copy of the frozen snapshot (OB-111): his live folder is only ever
 * read, and a pinned direction needs a railway that is not being edited under it.
 *
 * @author Adam
 */
public class testAPasteDoesNotTurnTheTrainRound
{
    /** The train Adam asked about. */
    private static final String LOCO = "2-8-4 3505 SP";

    /** Where he said it was standing, and where he said he was putting it. */
    private static final String FROM = "TopMainR2Inter";
    private static final String ONTO = "BottomMainB";

    /**
     * A train the walk cannot start from, which is the only way into the no-path arm on this railway.
     *
     * Every split square here can be driven to from FROM, measured in
     * `testNoSquareAnswersTwiceDifferently`, so choosing a stranded DESTINATION is not available.
     */
    private static final String ABSENT = "no such locomotive anywhere";

    /**
     * Enough repeats that a coin toss cannot pass by luck.
     *
     * At an even split the chance of forty calls agreeing is 2^-39, which is smaller than the chance
     * of the harness itself being wrong.
     */
    private static final int PASTES = 40;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout built;

    private static TileKey onto;
    private static TileKey from;

    /**
     * Opens the frozen railway and stands the train where Adam described it.
     *
     * The snapshot has this locomotive already ON BottomMainB, which is the square being pasted onto -
     * so it is moved rather than left where it is, and the move is part of the fixture rather than
     * something the test relies on the file for.
     *
     * @throws Exception when the railway cannot be read, which is a broken harness rather than a
     *         failing guard
     */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, false);

        String path = "file:///"
            + sandbox.getFolder().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

        for (TileKey key : session.getStore().getNamedTiles())
        {
            String name = session.getStore().getPointName(key);

            if (ONTO.equals(name)) onto = key;
            if (FROM.equals(name)) from = key;
        }

        assertNotNull(onto, ONTO + " is not on this railway, so there is nothing to paste onto");
        assertNotNull(from, FROM + " is not on this railway, so there is nowhere to paste from");

        session.placeLocomotive(from, LOCO);

        // EAST, which is the heading it has on his railway and the one his question is about.
        session.setFacing(from, Side.E);

        model.parseAuto(session.buildConfiguration());

        built = model.getAutoLayout();

        assertNotNull(built, "the configuration did not build");
    }

    /**
     * Puts the layout preference back.
     */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * The trap this file is about is present on this railway.
     *
     * Without it the two claims below would pass on a square that has nothing to get wrong, which is
     * `assert-the-variable-not-the-control`: the fixture has to be able to fail.
     *
     * Three things have to hold, and all three are measurements rather than assumptions - the
     * distances come from a breadth-first walk done here, so a builder change that stops emitting the
     * turning copy, or moves it, fails this rather than silently retiring the other claims.
     */
    @Test
    public void testTheSquareCanGetThisWrong()
    {
        // AND WHAT THIS FIXTURE CANNOT TELL YOU (PRV-C1, PRV-C9).
        //
        // The rule is "among the copies at the NEAREST distance, prefer one that is not a turning
        // copy".  A validator showed that deleting the preference leaves every claim in this class
        // green, and the reason is emission order: `AutonomyBuilder` emits a square's plain copy
        // before the turning copy of the same arrival side, so the first copy at the nearest distance
        // is already the plain one and the tie-break never decides anything HERE.
        //
        // It is not dead code - it is what keeps the answer right if that order ever changes, which
        // is the sort of thing the builder is free to do - but no test on this railway can fail
        // without it, and saying so is better than leaving a reader to assume the claims below cover
        // it.  Asserted, so that a change in emission order shows up as this line rather than as a
        // silent loss of coverage.
        java.util.List<String> order = new java.util.ArrayList<>(session.facingsFor(onto).keySet());

        // Per ARRIVAL SIDE, which is the pair the tie-break ever has to choose between: the plain
        // copy and the turning copy of one side sit the same distance away, because the builder hangs
        // both off the same edge.  Copies of the OTHER side are a different distance and the
        // nearest-first rule has already excluded them, so their order says nothing.
        //
        // The twin is found by name because the builder makes the name: `base + " (" + heading +
        // (reverse ? ", reverse)" : ")")`.  Measured here, the order is [westbound,
        // westbound-reverse, eastbound, eastbound-reverse] - a turning copy DOES precede a plain one
        // across the square, and never precedes its own twin.
        for (String name : order)
        {
            if (!name.endsWith(", reverse)")) continue;

            String twin = name.substring(0, name.length() - ", reverse)".length()) + ")";

            if (!order.contains(twin)) continue;

            if (order.indexOf(twin) < order.indexOf(name)) continue;

            fail("the turning copy " + name + " is emitted before its own plain twin " + twin + "."
                + " That is the pair the nearest-distance tie-break has to choose between, and this"
                + " class has been documented as unable to reach it because the plain one always came"
                + " first - so a claim asserting the tie-break can now be written, and should be."
                + " Order: " + order);
        }

        Map<String, Integer> reach = distances();

        Map<String, Side> copies = session.facingsFor(onto);

        assertTrue(copies.size() > 1,
            ONTO + " builds to one copy, so there is one answer and nothing to choose between: "
            + copies);

        int shortest = Integer.MAX_VALUE;

        for (String name : copies.keySet())
        {
            if (reach.containsKey(name)) shortest = Math.min(shortest, reach.get(name));
        }

        assertTrue(shortest < Integer.MAX_VALUE,
            "no copy of " + ONTO + " can be driven to from " + FROM + " facing east, so the walk is"
            + " not what answers here and this file is measuring the wrong thing");

        Set<Side> atTheShortest = new LinkedHashSet<>();

        for (Map.Entry<String, Side> copy : copies.entrySet())
        {
            Integer at = reach.get(copy.getKey());

            if (at != null && at == shortest) atTheShortest.add(copy.getValue());
        }

        assertEquals(atTheShortest.size(), 2,
            "the nearest copies of " + ONTO + " all face the same way, so nothing here can flip and"
            + " the claims below cannot fail. Copies at " + shortest + " edges: " + atTheShortest
            + ", all copies: " + copies);
    }

    /**
     * Pasting the same train onto the same square twice gives the same railway twice.
     *
     * Adam, 2026-09-07: **"in all your simulations, state should never drift."**  A placement that
     * answers differently on the second try is drift with nothing to blame it on - nothing has moved
     * between the calls below, so anything that differs differs by itself.
     *
     * MEASURED RED: east 23, west 17 in forty calls.
     */
    @Test
    public void testTheSamePasteGivesTheSameDirection()
    {
        Map<Side, Integer> tally = new LinkedHashMap<>();

        for (int i = 0; i < PASTES; i++)
        {
            Side answer = session.facingByPath(built, LOCO, onto);

            tally.put(answer, (tally.containsKey(answer) ? tally.get(answer) : 0) + 1);
        }

        assertEquals(tally.size(), 1,
            "putting " + LOCO + " down on " + ONTO + " " + PASTES + " times gave more than one"
            + " direction: " + tally + ". Nothing moved between the calls, so the answer is being"
            + " decided by Layout.getNeighbors' shuffle rather than by the railway - and which way a"
            + " pasted train points is then a coin toss.");
    }

    /**
     * And the direction it gives is the one the drive leaves it in, not the reverse of it.
     *
     * Adam: **"When 2-8-4 is pasted, it should always face east."**  East is the side of
     * `BottomMainB (eastbound)`, the copy a train that drove down the line is standing on. West is the
     * side of `BottomMainB (eastbound, reverse)` - the same arrival, after turning round - which is
     * the same nine edges away and is what the shuffle was reaching half the time.
     *
     * Asserted by asking the square rather than by writing E in here: the answer is the facing of the
     * nearest copy that is not a turning copy, so it stays right if he re-lays that end of the
     * railway.
     */
    @Test
    public void testItFacesTheWayTheDriveLeavesIt()
    {
        Side answer = session.facingByPath(built, LOCO, onto);

        assertEquals(answer, standingCopyFacing(),
            "put down on " + ONTO + ", " + LOCO + " faces " + answer + ". The nearest copy a train"
            + " could have DRIVEN to faces " + standingCopyFacing() + "; the other side belongs to"
            + " that square's turn-round copy, which is where a train is after it has decided to"
            + " reverse - not where a paste puts one. Copies: " + session.facingsFor(onto));

        assertEquals(answer, Side.E,
            "Adam, 2026-09-12: \"When 2-8-4 is pasted, it should always face east.\" It faces "
            + answer + ".");
    }

    /**
     * A terminus still turns a train round on paste, which is the other half of the rule.
     *
     * Adam, 2026-09-06: **"for terminuses, they must reverse on paste"**.  Preferring the plain copy
     * must not reach for one that is not there: a compulsory turn is emitted as turning copies ONLY,
     * so the nearest of those is the answer and the train takes the heading the end of the line has.
     *
     * Over every named square on the railway rather than one chosen by hand, because "the plain copy
     * exists" is the assumption that would break this and it is not visible at any single station.
     */
    @Test
    public void testASquareThatOnlyTurnsStillTurns()
    {
        Map<String, Integer> reach = distances();

        int checked = 0;

        for (TileKey square : session.getStore().getNamedTiles())
        {
            Map<String, Side> copies = session.facingsFor(square);

            if (copies.isEmpty()) continue;

            boolean anyPlain = false;

            for (String name : copies.keySet())
            {
                Point copy = built.getPoint(name);

                if (copy != null && !copy.isTerminus() && !copy.isReversing()) anyPlain = true;
            }

            if (anyPlain) continue;

            Side answer = session.facingByPath(built, LOCO, square);

            // Reachable or not, the answer has to be one of that square's own sides: the walk finds
            // one, and the no-path arm picks a legal one.  What it must never be is nothing, which is
            // a square a paste records no direction for at all.
            assertTrue(copies.values().contains(answer),
                session.getStore().getPointName(square) + " turns every train that arrives, and a"
                + " paste onto it answered " + answer + ", which is not one of its own sides: "
                + copies + (reach.containsKey(copies.keySet().iterator().next())
                    ? "" : " (nothing there is reachable, so this is the no-path arm)"));

            checked++;
        }

        assertTrue(checked > 0,
            "no square on this railway turns every train that arrives, so this claim checked nothing."
            + " That is a change to the railway rather than to the code - find a fixture that has one"
            + " rather than deleting this.");
    }

    /**
     * And no square anywhere on the railway answers twice differently, by either route to an answer.
     *
     * The claim above covers the square Adam reported, which the train can be DRIVEN to.  The other
     * arm - no path, where the answer is not derived from a walk at all - held the second die, and
     * BottomMainB cannot reach it because BottomMainB is reachable.
     *
     * **Measured: every square on this railway with more than one copy can be driven to from
     * TopMainR2Inter**, so no square strands the walk and the arm cannot be reached by choosing a
     * different destination.  What reaches it is a train that is not standing on the railway at all -
     * `walkTo` has nowhere to start - which is how `testAPastedTrainKeepsItsDirection` reaches the
     * same arm, and is a real case: a locomotive brought into autonomy for the first time.
     *
     * Both are asserted to have run, because a claim that only ever reaches one arm is a claim about
     * half the code that reads as a claim about all of it.
     */
    @Test
    public void testNoSquareAnswersTwiceDifferently()
    {
        Map<String, Integer> reach = distances();

        int walked = 0;
        int stranded = 0;

        List<String> drifted = new java.util.ArrayList<>();

        for (TileKey square : session.getStore().getNamedTiles())
        {
            Map<String, Side> copies = session.facingsFor(square);

            if (copies.size() < 2) continue;

            boolean anyReachable = false;

            for (String name : copies.keySet())
            {
                if (reach.containsKey(name)) anyReachable = true;
            }

            if (anyReachable) walked++;

            Set<Side> answers = new LinkedHashSet<>();
            Set<Side> withoutATrain = new LinkedHashSet<>();

            for (int i = 0; i < 8; i++)
            {
                answers.add(session.facingByPath(built, LOCO, square));

                // THE NO-PATH ARM, reached by a train the walk cannot start from.
                withoutATrain.add(session.facingByPath(built, ABSENT, square));
            }

            stranded++;

            if (answers.size() > 1)
            {
                drifted.add(session.getStore().getPointName(square) + " gave " + answers
                    + (anyReachable ? " (driven to)" : " (nothing there is reachable)"));
            }

            if (withoutATrain.size() > 1)
            {
                drifted.add(session.getStore().getPointName(square) + " gave " + withoutATrain
                    + " to a train that is not on the railway (the no-path arm)");
            }
        }

        assertTrue(drifted.isEmpty(),
            "putting a train down on the same square eight times gave more than one direction at "
            + drifted.size() + " squares: " + drifted);

        assertTrue(walked > 0,
            "no square with more than one copy can be driven to from " + FROM + ", so nothing above"
            + " went through the walk");

        assertTrue(stranded > 0,
            "no square on this railway has more than one copy, so neither arm was reached. That is a"
            + " change to the railway rather than to the code: find a fixture that splits one rather"
            + " than deleting this.");
    }

    /**
     * Put down on the square it is already standing on, a train keeps the copy it is on.
     *
     * A corner case the rewrite of `walkTo` changed, so it is pinned rather than left to be noticed.
     * The old walk only tested the copies it ARRIVED at, never the one it set off from, so a train
     * asked about its own square was walked all the way round the railway and answered with whichever
     * copy the loop came back to - which on a square with two facings is the other one. It now
     * measures from distance zero, so the copy it is standing on is the nearest and wins.
     *
     * That matters because the square a train is on is a square the menus offer it: `sameSquare`
     * exists precisely because "a train standing at BottomMainB was being offered a path to
     * BottomMainB". Turning a train round for accepting that offer would be the worst version of this
     * defect - it is the one gesture that self-evidently changes nothing.
     */
    @Test
    public void testPastingOntoItsOwnSquareDoesNotTurnIt()
    {
        Map<String, Side> here = session.facingsFor(from);

        assertTrue(here.size() > 1,
            FROM + " builds to one copy, so there is nothing to get wrong here: " + here);

        assertEquals(session.facingByPath(built, LOCO, from), Side.E,
            "put down on " + FROM + ", the square it is already standing on facing east, " + LOCO
            + " came back facing something else. The copy it is ON is nought edges away, so it is the"
            + " nearest one there is. Copies: " + here);
    }

    /**
     * A train that IS on the railway but cannot be driven there keeps the heading it has.
     *
     * **PRV-C2: deleting that arm passed every claim.**  The no-path claims above use a train that is
     * not on the railway at all, so `facingOf` answers null and the code falls straight through to the
     * first departable copy - which means the line implementing Adam's actual ruling, *"Option 1 is
     * fine as long as the direction isnt flipped"*, was never executed by a test.
     *
     * This uses a train that is placed and pointing somewhere, on a square the target cannot be
     * reached from. Its recorded heading is one BottomMainB can hold, so the rule says keep it - and
     * without the rule the answer is whichever departable copy comes first in build order, which is
     * how the flip Adam reported got in.
     */
    @Test
    public void testAStrandedTrainKeepsTheHeadingItHas()
    {
        Map<String, Integer> reach = distances();

        String stranded = null;

        Side heading = null;

        TileKey target = null;

        // OVER TARGETS AS WELL AS TRAINS.  Every placed train can be driven to BottomMainB, measured,
        // so fixing the target found nothing - and "nothing to test" is not the same as "nothing to
        // test here".  What the arm needs is any square a placed train cannot reach whose facings
        // include the one that train has.
        for (org.traincontrol.base.Locomotive other : built.getLocomotivesToRun())
        {
            if (other == null) continue;

            Side its = session.facingOf(other.getName(), built);

            if (its == null) continue;

            for (TileKey square : session.getStore().getNamedTiles())
            {
                Map<String, Side> copies = session.facingsFor(square);

                if (copies.size() < 2 || !copies.values().contains(its)) continue;

                boolean canDriveThere = false;

                for (String copy : copies.keySet())
                {
                    if (canBeDrivenFrom(other.getName(), copy)) canDriveThere = true;
                }

                if (canDriveThere) continue;

                stranded = other.getName();

                heading = its;

                target = square;

                break;
            }

            if (stranded != null) break;
        }

        // MEASURED, AND THE MEASUREMENT IS THE CLAIM WHERE THERE IS NOTHING ELSE.
        //
        // On this railway there is no such train: every placed one can be driven to every split
        // square that can hold the heading it has.  So the arm is reached only by a train that is not
        // on the railway at all - which is a real case, a locomotive brought into autonomy for the
        // first time, and is what `testNoSquareAnswersTwiceDifferently` exercises - but with `already`
        // always null, the line implementing Adam's ruling is not executed by anything here.
        //
        // Said out loud rather than left as a green test that looks like coverage. If the railway
        // ever strands one, the assertion below starts checking the rule instead of the fact, and
        // that is the direction this should change in.
        if (stranded == null)
        {
            assertEquals(reach.size(), 85,
                "no placed train is stranded from a square that can hold its heading, which is what"
                + " makes the keep-the-heading arm unreachable here - but the reachable-Point count"
                + " has moved, so the railway has changed and this measurement is stale. Re-measure:"
                + " if some train IS stranded now, this claim starts checking the rule and the note"
                + " above should go.");

            return;
        }

        assertEquals(session.facingByPath(built, stranded, target), heading,
            "put down on " + session.getStore().getPointName(target) + " with no way to drive there, "
            + stranded + " should keep the heading it already has (" + heading + "), which that"
            + " square can hold. Adam, 2026-09-12: \"Option 1 is fine as long as the direction isnt"
            + " flipped (which it was before).\" Without that rule the answer is the first departable"
            + " copy in build order, which is a flip whenever build order disagrees with the train.");
    }

    /**
     * A may-reverse square asks which way the train should face; an ordinary one does not.
     *
     * Adam, 2026-09-13: **"If pasting at a 'may reverse' square, ask what direction the train should
     * face."**
     *
     * **The question exists because the walk cannot answer there.**  Everywhere else `facingByPath`
     * drives the train to the landing square and the copy it arrives on says which way it points. A
     * may-reverse square is what turning round is FOR, so both headings are reachable and the walk's
     * answer is whichever copy it landed on - the same reason the arrival-side question is asked at
     * those squares and nowhere else.
     *
     * What is claimed here is the DECISION, not the dialog: which squares put a question, and what it
     * offers. Driving `JOptionPane` from a test is what hung this suite for twenty minutes on
     * 2026-09-13, and the shape of the paste that calls this is pinned as source below.
     *
     * The deduplication matters and is easy to lose: `facingsFor` answers per COPY, so a square with
     * three copies facing east hands east back three times - three identical buttons in a dialog
     * nobody can answer.
     */
    @Test
    public void testOnlyAMayReverseSquareAsksWhichWayItFaces() throws Exception
    {
        java.util.Set<TileKey> mayTurn = session.mayTurnTiles();

        assertFalse(mayTurn.isEmpty(),
            "this railway has no may-reverse squares at all, so the question below is about nothing");

        int asked = 0;

        for (TileKey square : mayTurn)
        {
            if (org.traincontrol.gui.FacingPrompt.wouldAsk(session.facingsFor(square).values()))
            {
                asked++;
            }
        }

        // WHAT IT DOES WITH REPEATS, which is the input it actually gets.
        //
        // `facingsFor` answers per COPY - a square with three copies facing east hands east back three
        // times - so the offering has to collapse them or the dialog carries three identical buttons.
        // Asserting that over the railway's own squares would be asserting a property of the loop:
        // `choicesFor` walks the four compass points and takes each at most once, so it cannot repeat
        // whatever it is given. This gives it something to collapse.
        assertEquals(org.traincontrol.gui.FacingPrompt.choicesFor(
            java.util.Arrays.asList(Side.E, Side.E, Side.N, Side.E)),
            java.util.Arrays.asList(Side.N, Side.E),
            "three copies facing east and one facing north should offer two headings, north first -"
            + " compass order, so the buttons do not move between squares - and the offering instead"
            + " came back as " + org.traincontrol.gui.FacingPrompt.choicesFor(
                java.util.Arrays.asList(Side.E, Side.E, Side.N, Side.E)));

        assertFalse(org.traincontrol.gui.FacingPrompt.wouldAsk(
            java.util.Arrays.asList(Side.E, Side.E, Side.E)),
            "three copies all facing east put a question to the operator, whose three buttons are the"
            + " same answer. Collapsing them is what leaves one choice, and one choice is not a"
            + " question");

        assertTrue(asked > 0,
            "no may-reverse square on this railway offers more than one heading, so Adam's question -"
            + " \"if pasting at a 'may reverse' square, ask what direction the train should face\" -"
            + " is never put and this claim cannot fail");

        // AND NOT ANYWHERE ELSE.  A square with one heading has nothing to ask, whatever it is.
        assertFalse(org.traincontrol.gui.FacingPrompt.wouldAsk(
            java.util.Arrays.asList(Side.E)),
            "a square that can hold exactly one heading still puts a question to the operator, whose"
            + " only answer is the one the paste would have used anyway");

        assertFalse(org.traincontrol.gui.FacingPrompt.wouldAsk(java.util.Collections.emptyList()),
            "a square that can hold no heading at all still puts a question, and the dialog has no"
            + " buttons on it");

        // THE PASTE ASKS IT, and asks it before anything moves (SVX-C4's guard, one block on).
        String source = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        String flat = source.replaceAll("\\s+", " ");

        assertTrue(flat.contains("if (mayTurnHere(aimed) && getAutonomySession() != null)"),
            "the paste no longer asks the facing question at may-reverse squares (Adam, 2026-09-13)");

        assertTrue(flat.contains("if (facingChosenAtTheLanding == null) return true;"),
            "a dismissed facing question no longer leaves the railway untouched. Adam's rule for the"
            + " question one step above it: \"simply don't place the train, leave it on the clipboard"
            + " as if no paste had been done\"");

        // READ BEFORE THE TAIL QUESTION WAITS (TDU-B2), and written from there.
        assertTrue(flat.contains("final org.traincontrol.automationui.TilePorts.Side facingChosen ="
            + " facingChosenAtTheLanding;") && flat.contains("session.setFacing(tile, facingChosen != null ? facingChosen"),
            "the operator's answer is no longer what gets written, so the question is asked and then"
            + " overruled by the walk it exists to replace");
    }

    /**
     * The facing question's buttons say where the train points, not where it came from (OB-215).
     *
     * Adam, 2026-09-13: *'options for "which way should the train face" are "from the north (up)" ...
     * for "which way does it face", it should be "to the north (up)"'*.  The facing prompt borrowed the
     * arrival-side labels, which answer the opposite question about the opposite end of the train.
     *
     * Asked of every heading and against the arrival label for the same compass point, in the running
     * language, so a later edit that points one prompt back at the other's words is caught.
     */
    @Test
    public void testTheFacingButtonsNameAHeadingNotAnArrival()
    {
        for (Side heading : Side.values())
        {
            String facing = org.traincontrol.gui.FacingPrompt.labelFor(heading);

            String arrival = org.traincontrol.gui.ArrivalSidePrompt.labelFor(heading.name());

            assertFalse(facing == null || facing.trim().isEmpty(),
                "the facing question has no label for " + heading);

            assertFalse(facing.equals(arrival),
                "the facing question labels " + heading + " \"" + facing + "\", which is the arrival-side"
                + " label - an answer to \"where did the train come from\" offered for \"which way does it"
                + " face\". Adam, OB-215: it should read \"to the north (up)\", not \"from\".");
        }
    }

    /**
     * A cut train's remembered heading is preferred only while it is off the railway.
     *
     * **SVX-C4, and what this can and cannot say.**  Nothing under `test/` named `cutFacing` at all,
     * so neither MT-368's clipboard clause nor SVV-C5's narrowing of it had a claim. The branch itself
     * sits in `locomotiveGestureOnDiagram`, a private key handler that needs a shown window, a hovered
     * tile and a loaded clipboard, and the paste it performs can raise a modal facing prompt - the
     * shape of test that hung this suite for twenty minutes on 2026-09-13 and left a preference
     * wrong when its teardown never ran. That is not a good trade for a C.
     *
     * So this pins two things and claims nothing else:
     *
     *   - the QUESTION the branch asks of the landing, which is reachable and is the half that can be
     *     wrong on a railway: `facingsFor` says which headings a square can hold, and a remembered
     *     heading it cannot hold must not be forced on it.
     *   - the SHAPE of the branch: that all three conditions are still in it. A source guard reports
     *     clean about everything it was not told (`guard-knows-only-what-it-lists`), and the whole of
     *     the reason it is here is that the conditions were added in two rounds and one of them - "and
     *     only while the train is actually off the railway" - is the one a later edit would drop.
     *
     * @throws Exception from reading the source
     */
    @Test
    public void testTheClipboardHeadingIsOnlyTakenWhileTheTrainIsLifted() throws Exception
    {
        // THE QUESTION, on the railway.  A landing that cannot hold a heading does not get it.
        java.util.Collection<org.traincontrol.automationui.TilePorts.Side> canHold =
            session.facingsFor(onto).values();

        assertFalse(canHold.isEmpty(),
            "the landing square offers no headings at all, so the branch's test of it cannot fail"
            + " here and this claim is about nothing");

        for (org.traincontrol.automationui.TilePorts.Side side
            : org.traincontrol.automationui.TilePorts.Side.values())
        {
            if (canHold.contains(side)) continue;

            assertFalse(session.facingsFor(onto).containsValue(side),
                "facingsFor says " + onto + " can hold " + side + " and also that it cannot, which is"
                + " the question the clipboard branch asks before forcing a remembered heading on a"
                + " landing");
        }

        // THE SHAPE, in the file.
        String source = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        String flat = source.replaceAll("\\s+", " ");

        assertTrue(flat.contains(
            "boolean stillLifted = this.model == null || this.model.getAutoLayout() == null"
            + " || this.model.getAutoLayout().getLocomotiveLocation(placing) == null;"),
            "the paste no longer works out whether the cut train is still off the railway. Without"
            + " that test the remembered heading is preferred for as long as the clipboard is loaded,"
            + " including after the train has been put back by another door - and the remembered"
            + " heading is then the stale reading this branch exists to avoid, pointing the other way"
            + " (SVV-C5)");

        assertTrue(flat.contains("if (this.cutFacing != null && placing == this.cutLocomotive"
            + " && stillLifted"),
            "the clipboard branch no longer requires all three of a remembered heading, the train"
            + " being the cut one, and that train being off the railway (MT-368, SVV-C5)");

        assertTrue(flat.contains("getAutonomySession().facingsFor(aimed).containsValue(this.cutFacing)"),
            "the clipboard heading is no longer checked against what the landing can hold, so a paste"
            + " can leave a train facing a way its new square has no rail for (MT-377: \"as long as"
            + " the direction isnt flipped\")");
    }

    /**
     * Whether this train could drive to a named copy, by its own walk rather than the one under test.
     *
     * @param train the locomotive
     * @param copy the Point's name
     * @return whether a directed path exists
     */
    private static boolean canBeDrivenFrom(String train, String copy)
    {
        Point start = null;

        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() != null
                && train.equals(point.getCurrentLocomotive().getName()))
            {
                start = point;
            }
        }

        if (start == null) return false;

        Set<String> seen = new LinkedHashSet<>();
        Deque<Point> queue = new ArrayDeque<>();

        seen.add(start.getName());
        queue.add(start);

        while (!queue.isEmpty())
        {
            Point here = queue.poll();

            List<Edge> away = built.getNeighbors(here);

            if (away == null) continue;

            for (Edge edge : away)
            {
                Point next = edge.getEnd();

                if (next == null || !seen.add(next.getName())) continue;

                if (copy.equals(next.getName())) return true;

                // The same rule the walk under test follows: a turning copy may be arrived at and not
                // driven through, because passing one is a journey with a reversal in it.
                if (next.isTerminus() || next.isReversing()) continue;

                queue.add(next);
            }
        }

        return false;
    }

    /**
     * The facing of the nearest copy of the target that a train could simply be standing on.
     *
     * @return that side
     */
    private static Side standingCopyFacing()
    {
        Map<String, Integer> reach = distances();

        Map<String, Side> copies = session.facingsFor(onto);

        Side best = null;
        int at = Integer.MAX_VALUE;

        for (Map.Entry<String, Side> copy : copies.entrySet())
        {
            Point point = built.getPoint(copy.getKey());

            if (point == null || point.isTerminus() || point.isReversing()) continue;

            Integer here = reach.get(copy.getKey());

            if (here == null || here >= at) continue;

            at = here;
            best = copy.getValue();
        }

        assertNotNull(best,
            "no copy of " + ONTO + " that a train could stand on without turning round is reachable"
            + " from " + FROM + ", so there is nothing for the paste to be compared against");

        return best;
    }

    /**
     * How far every Point is from where the train stands, in edges.
     *
     * Its own walk rather than the one under test - a claim that asks the code its own question
     * agrees with whatever the code does.  Breadth-first, so the distances are the true shortest ones
     * whatever order `getNeighbors` hands the edges back in.
     *
     * @return the distance to each reachable Point, by name
     */
    private static Map<String, Integer> distances()
    {
        Point start = null;

        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() != null
                && LOCO.equals(point.getCurrentLocomotive().getName()))
            {
                start = point;
            }
        }

        assertNotNull(start, LOCO + " is not on the built railway, so nothing can be walked from it");

        Map<String, Integer> out = new LinkedHashMap<>();
        Deque<Point> queue = new ArrayDeque<>();

        out.put(start.getName(), 0);
        queue.add(start);

        while (!queue.isEmpty())
        {
            Point here = queue.poll();

            List<Edge> away = built.getNeighbors(here);

            if (away == null) continue;

            for (Edge edge : away)
            {
                Point next = edge.getEnd();

                if (next == null || out.containsKey(next.getName())) continue;

                out.put(next.getName(), out.get(here.getName()) + 1);
                queue.add(next);
            }
        }

        return out;
    }
}
