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
import support.SourceOf;

/**
 * A train put down on a square is standing where the drive would leave it, not turned round.
 *
 * Adam: "When 2-8-4 is pasted, it should always face east.  Does it?"  Which way a pasted train pointed was a coin
 * toss on the frozen copy of his railway, because `facingByPath` walks `Layout.getNeighbors`, which SHUFFLES (right
 * for autonomy choosing a journey, wrong for where one square is relative to another), and two copies of BottomMainB
 * sit the same nine edges away: `BottomMainB (eastbound)` facing E and `BottomMainB (eastbound, reverse)` facing W.
 *
 * The second is not an arrival at all.  A may-turn square is built as a plain copy (where a train that drove in
 * stands) and a turning copy (after deciding to turn round), reached by the same edge and facing opposite ways.
 * Answering with the turning copy records a decision the operator did not make by dragging a train onto a square.  A
 * turning copy is emitted with `terminus: true` (or `reversing: true`), so `isTerminus()` cannot tell it from a real
 * terminus (OB-205, MT-368); `isSamePlaceAs` is the discriminator used here.
 *
 * A terminus must still reverse on paste: "for terminuses, they must reverse on paste".  A compulsory turn has no
 * plain copy, so the turning copy stays the answer.  Asserted below, not assumed.
 *
 * Read against a `LayoutSandbox` copy of the frozen snapshot (OB-111): a pinned direction needs a railway that is not
 * being edited under it.
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
     * A train the walk cannot start from, the only way into the no-path arm (every split square can be driven to from
     * FROM, measured in `testNoSquareAnswersTwiceDifferently`).
     */
    private static final String ABSENT = "no such locomotive anywhere";

    /**
     * Enough repeats that a coin toss cannot pass by luck: forty agreeing calls at an even split is 2^-39.
     */
    private static final int PASTES = 40;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout built;

    private static TileKey onto;
    private static TileKey from;

    /**
     * Opens the frozen railway and stands the train where Adam described it.  The snapshot has this locomotive already
     * ON BottomMainB, the square being pasted onto, so the fixture moves it.
     *
     * @throws Exception when the railway cannot be read, a broken harness rather than a failing guard
     */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulated();

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

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * The trap this file is about is present on this railway: otherwise the claims below would pass on a square with
     * nothing to get wrong.  The distances come from a breadth-first walk done here, so a builder change that stops
     * emitting the turning copy, or moves it, fails this rather than silently retiring the other claims.
     */
    @Test
    public void testTheSquareCanGetThisWrong()
    {
        // WHAT THIS FIXTURE CANNOT TELL YOU: the rule is "among the copies at the NEAREST distance, prefer one that is
        // not a turning copy", but `AutonomyBuilder` emits a square's plain copy before the turning copy of the same
        // arrival side, so the tie-break never decides anything HERE.  It keeps the answer right if that order changes;
        // this line makes a change in emission order show up instead of silently losing coverage.
        java.util.List<String> order = new java.util.ArrayList<>(session.facingsFor(onto).keySet());

        // Per ARRIVAL SIDE, the pair the tie-break chooses between: the plain and turning copies of one side sit the
        // same distance away.  The twin is found by name, `base + " (" + heading + (reverse ? ", reverse)" : ")")`.
        // Measured order: [westbound, westbound-reverse, eastbound, eastbound-reverse], so a turning copy DOES precede
        // a plain one across the square, never its own twin.
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
     * Pasting the same train onto the same square twice gives the same railway twice.  Adam: "in all your simulations,
     * state should never drift."  Nothing moves between the calls, so any difference is drift with nothing to blame.
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
     * The direction it gives is the one the drive leaves it in, not the reverse of it.
     *
     * East is the side of `BottomMainB (eastbound)`, where a train that drove down the line stands; west is that of
     * `BottomMainB (eastbound, reverse)`, the same arrival after turning round.  Asked of the square rather than
     * written as E, so it stays right if he re-lays that end of the railway.
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
     * A terminus still turns a train round on paste: "for terminuses, they must reverse on paste".  Preferring the
     * plain copy must not reach for one that is not there, since a compulsory turn is emitted as turning copies ONLY.
     * Over every named square, because "the plain copy exists" is not visible at any single station.
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

            // Reachable or not, the answer is one of that square's own sides; what it must never be is nothing.
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
     * No square anywhere on the railway answers twice differently, by either route to an answer.
     *
     * The claim above covers the square Adam reported, which the train can be DRIVEN to.  The no-path arm is reached by
     * a train that is not standing on the railway at all (`walkTo` has nowhere to start), as in
     * `testAPastedTrainKeepsItsDirection`, a real case: a locomotive brought into autonomy for the first time.  Every
     * square here with more than one copy can be driven to from TopMainR2Inter, measured.  Both arms are asserted to
     * have run.
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
     * The old walk only tested the copies it ARRIVED at, never the one it set off from, so a train asked about its own
     * square was walked all the way round and answered with the other copy.  `sameSquare` exists because the menus
     * offer a train the square it is on; turning it round for accepting that offer would be the worst version of the
     * defect.
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
     * The no-path claims above use a train that is not on the railway at all, so `facingOf` answers null and the code
     * falls through to the first departable copy; the line implementing Adam's ruling, "Option 1 is fine as long as
     * the direction isnt flipped", was never executed by a test.  This uses a placed train on a square the target
     * cannot be reached from, with a heading BottomMainB can hold: without the rule the answer is whichever departable
     * copy is first in build order.
     */
    @Test
    public void testAStrandedTrainKeepsTheHeadingItHas()
    {
        Map<String, Integer> reach = distances();

        String stranded = null;

        Side heading = null;

        TileKey target = null;

        // OVER TARGETS AS WELL AS TRAINS: every placed train can be driven to BottomMainB, so the arm needs any square
        // a placed train cannot reach whose facings include its heading.
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

        // WHEN THIS RAILWAY STRANDS A PLACED TRAIN, that is the case, and it is asked as it always was.
        if (stranded != null)
        {
            assertEquals(session.facingByPath(built, stranded, target), heading,
                "put down on " + session.getStore().getPointName(target) + " with no way to drive there, "
                + stranded + " should keep the heading it already has (" + heading + "), which that"
                + " square can hold. Adam, 2026-09-12: \"Option 1 is fine as long as the direction isnt"
                + " flipped (which it was before).\" Without that rule the answer is the first departable"
                + " copy in build order, which is a flip whenever build order disagrees with the train.");
        }
        else
        {
            // MEASURED: it does not, today.  Every placed train can be driven to every split square that can hold
            // the heading it has, so the arm is not reached by choosing one.
            assertEquals(reach.size(), 85,
                "no placed train is stranded from a square that can hold its heading, which is what"
                + " makes the keep-the-heading arm unreachable by choosing one - but the reachable-Point count"
                + " has moved, so the railway has changed and this measurement is stale. Re-measure:"
                + " if some train IS stranded now, this claim starts checking the rule and the note"
                + " above should go.");
        }

        // AND THE CASE BUILT FOR IT, so the rule is asked whether or not the railway has one (FR-136): a train placed
        // in the setup after `built` was made has a heading and no walk.  The departable copies are in build order; the
        // first is what the arm answers without the rule, so the heading kept must not be the first.
        List<Side> departable = new java.util.ArrayList<>();

        for (Map.Entry<String, Side> copy : session.facingsFor(onto).entrySet())
        {
            Point point = built.getPoint(copy.getKey());

            if (point == null) continue;

            List<Edge> out = built.getNeighbors(point);

            if (out != null && !out.isEmpty()) departable.add(copy.getValue());
        }

        Side kept = null;

        for (Side side : departable)
        {
            if (side != departable.get(0))
            {
                kept = side;

                break;
            }
        }

        assertNotNull(kept,
            ONTO + " has no two copies a train could depart from that face different ways (" + departable
            + "), so keeping a heading cannot be told from taking the first copy and the claim cannot fail");

        Set<String> standing = new LinkedHashSet<>();

        // EVERY POINT, not the locomotives set to run: a train standing on a Point outside the diagram's squares is
        // on the railway too, and is walked from.
        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() != null) standing.add(point.getCurrentLocomotive().getName());
        }

        String absent = null;

        for (String name : model.getLocList())
        {
            if (!standing.contains(name))
            {
                absent = name;

                break;
            }
        }

        assertNotNull(absent, "every locomotive in the database stands on the built railway, so there is"
            + " no train the walk cannot start from");

        TileKey placedAt = null;

        for (TileKey square : session.getStore().getNamedTiles())
        {
            if (square.equals(onto) || session.getPointProperty(square, "loc") != null) continue;

            if (session.facingsFor(square).containsValue(kept))
            {
                placedAt = square;

                break;
            }
        }

        assertNotNull(placedAt, "no empty square can hold a train facing " + kept
            + ", so the setup cannot be given the heading to keep");

        session.placeLocomotive(placedAt, absent);
        session.setFacing(placedAt, kept);

        try
        {
            assertEquals(session.facingOf(absent, built), kept,
                "control: the setup does not say " + absent + " is facing " + kept + ", so there is no"
                + " heading to keep");

            org.testng.Assert.assertNull(built.getLocomotiveLocation(model.getLocByName(absent)),
                "control: " + absent + " stands on the built railway, so it is walked from");

            assertEquals(session.facingByPath(built, absent, onto), kept,
                "put down on " + ONTO + " with no way to drive there, " + absent + " should keep the heading it"
                + " already has (" + kept + "), which that square can hold. Adam, 2026-09-12: \"Option 1 is fine as"
                + " long as the direction isnt flipped (which it was before).\" Without that rule the answer is the"
                + " first departable copy in build order, which is a flip whenever build order disagrees with the"
                + " train.");
        }
        finally
        {
            session.placeLocomotive(placedAt, null);
        }
    }

    /**
     * A may-reverse square asks which way the train should face; an ordinary one does not.
     *
     * Adam: "If pasting at a 'may reverse' square, ask what direction the train should face."  The walk cannot answer
     * there, since turning round is what the square is FOR and both headings are reachable.  Claimed here is the
     * DECISION, not the dialog (driving `JOptionPane` from a test hung this suite once).  `facingsFor` answers per
     * COPY, so a square with three copies facing east hands east back three times; the offering must collapse them.
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
            if (org.traincontrol.gui.autonomy.FacingPrompt.wouldAsk(session.facingsFor(square).values()))
            {
                asked++;
            }
        }

        // WHAT IT DOES WITH REPEATS, the input it actually gets: `choicesFor` walks the four compass points and takes
        // each at most once, so asserting over the railway's own squares would assert a property of the loop.  This
        // gives it something to collapse.
        assertEquals(org.traincontrol.gui.autonomy.FacingPrompt.choicesFor(
            java.util.Arrays.asList(Side.E, Side.E, Side.N, Side.E)),
            java.util.Arrays.asList(Side.N, Side.E),
            "three copies facing east and one facing north should offer two headings, north first -"
            + " compass order, so the buttons do not move between squares - and the offering instead"
            + " came back as " + org.traincontrol.gui.autonomy.FacingPrompt.choicesFor(
                java.util.Arrays.asList(Side.E, Side.E, Side.N, Side.E)));

        assertFalse(org.traincontrol.gui.autonomy.FacingPrompt.wouldAsk(
            java.util.Arrays.asList(Side.E, Side.E, Side.E)),
            "three copies all facing east put a question to the operator, whose three buttons are the"
            + " same answer. Collapsing them is what leaves one choice, and one choice is not a"
            + " question");

        assertTrue(asked > 0,
            "no may-reverse square on this railway offers more than one heading, so Adam's question -"
            + " \"if pasting at a 'may reverse' square, ask what direction the train should face\" -"
            + " is never put and this claim cannot fail");

        // AND NOT ANYWHERE ELSE.  A square with one heading has nothing to ask, whatever it is.
        assertFalse(org.traincontrol.gui.autonomy.FacingPrompt.wouldAsk(
            java.util.Arrays.asList(Side.E)),
            "a square that can hold exactly one heading still puts a question to the operator, whose"
            + " only answer is the one the paste would have used anyway");

        assertFalse(org.traincontrol.gui.autonomy.FacingPrompt.wouldAsk(java.util.Collections.emptyList()),
            "a square that can hold no heading at all still puts a question, and the dialog has no"
            + " buttons on it");

        // THE PASTE ASKS IT, and asks it before anything moves.
        String flat = SourceOf.declaration("    boolean locomotiveGestureOnDiagram(").replaceAll("\\s+", " ");

        assertTrue(flat.contains("if (mayTurnHere(aimed) && ui.getAutonomySession() != null)"),
            "the paste no longer asks the facing question at may-reverse squares (Adam, 2026-09-13)");

        assertTrue(flat.contains("if (ui.facingChosenAtTheLanding == null) return true;"),
            "a dismissed facing question no longer leaves the railway untouched. Adam's rule for the"
            + " question one step above it: \"simply don't place the train, leave it on the clipboard"
            + " as if no paste had been done\"");

        // READ BEFORE THE TAIL QUESTION WAITS, and written from there.
        String placement = SourceOf.declaration("private void rememberPlacement(").replaceAll("\\s+", " ");

        assertTrue(placement.contains("final org.traincontrol.automationui.TilePorts.Side facingChosen ="
            + " ui.facingChosenAtTheLanding;") && placement.contains("session.setFacing(tile, facingChosen != null ? facingChosen"),
            "the operator's answer is no longer what gets written, so the question is asked and then"
            + " overruled by the walk it exists to replace");
    }

    /**
     * The facing question's buttons say where the train points, not where it came from (OB-215).  The prompt borrowed
     * the arrival-side labels, which answer the opposite question about the other end of the train.  Asked of every
     * heading against the arrival label for the same compass point, in the running language.
     */
    @Test
    public void testTheFacingButtonsNameAHeadingNotAnArrival()
    {
        for (Side heading : Side.values())
        {
            String facing = org.traincontrol.gui.autonomy.FacingPrompt.labelFor(heading);

            String arrival = org.traincontrol.gui.autonomy.ArrivalSidePrompt.labelFor(heading.name());

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
     * The branch sits in `locomotiveGestureOnDiagram`, a private key handler needing a shown window, a hovered tile and
     * a loaded clipboard, whose paste can raise a modal facing prompt (the shape of test that hung this suite).  So
     * this pins two things and claims nothing else:
     *   - the QUESTION the branch asks of the landing: `facingsFor` says which headings a square can hold, and a
     *     remembered heading it cannot hold must not be forced on it;
     *   - the SHAPE of the branch: all three conditions are still in it, notably "and only while the train is actually
     *     off the railway", which a later edit would drop.
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

        // WHAT THE LANDING HAS RAIL FOR, read off the tile itself rather than off the answer being checked
        // (FR-136): the sides its routes touch.  A train can only point down a side the square has rail on.
        org.traincontrol.base.LayoutDiagramComponent tile = session.getGraph().getTiles().get(onto);

        assertNotNull(tile, ONTO + " is not a square of the diagram, so there is no rail to ask");

        Set<org.traincontrol.automationui.TilePorts.Side> railOn = new LinkedHashSet<>();

        for (int state = 0; state < org.traincontrol.automationui.TilePorts.getStateCount(tile.getType()); state++)
        {
            for (org.traincontrol.automationui.TilePorts.Route road
                : org.traincontrol.automationui.TilePorts.ports(tile.getType(), tile.getOrientation(), state))
            {
                railOn.add(road.getA());
                railOn.add(road.getB());
            }
        }

        assertFalse(railOn.isEmpty(), ONTO + " has no rail on any side, so every heading would be one it cannot hold");

        int cannotHold = 0;

        for (org.traincontrol.automationui.TilePorts.Side side
            : org.traincontrol.automationui.TilePorts.Side.values())
        {
            if (railOn.contains(side)) continue;

            cannotHold++;

            assertFalse(canHold.contains(side),
                "facingsFor says " + onto + " can hold " + side + ", a side it has no rail on, which is"
                + " the question the clipboard branch asks before forcing a remembered heading on a"
                + " landing");
        }

        assertTrue(cannotHold > 0,
            ONTO + " has rail on all four sides, so every heading is one it can hold and the question has no"
            + " answer that could be wrong");

        // THE SHAPE, in the file.
        String flat = SourceOf.declaration("    boolean locomotiveGestureOnDiagram(").replaceAll("\\s+", " ");

        assertTrue(flat.contains(
            "boolean stillLifted = ui.getModel() == null || ui.getModel().getAutoLayout() == null"
            + " || ui.getModel().getAutoLayout().getLocomotiveLocation(placing) == null;"),
            "the paste no longer works out whether the cut train is still off the railway. Without"
            + " that test the remembered heading is preferred for as long as the clipboard is loaded,"
            + " including after the train has been put back by another door - and the remembered"
            + " heading is then the stale reading this branch exists to avoid, pointing the other way"
            + " (SVV-C5)");

        assertTrue(flat.contains("if (ui.cutFacing != null && placing == ui.cutLocomotive"
            + " && stillLifted"),
            "the clipboard branch no longer requires all three of a remembered heading, the train"
            + " being the cut one, and that train being off the railway (MT-368, SVV-C5)");

        assertTrue(flat.contains("ui.getAutonomySession().facingsFor(aimed).containsValue(ui.cutFacing)"),
            "the clipboard heading is no longer checked against what the landing can hold, so a paste"
            + " can leave a train facing a way its new square has no rail for (MT-377: \"as long as"
            + " the direction isnt flipped\")");
    }

    /**
     * Whether this train could drive to a named copy, by its own walk rather than the one under test.
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
     * How far every Point is from where the train stands, in edges: its own breadth-first walk, since a claim that asks
     * the code its own question agrees with whatever the code does.
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
