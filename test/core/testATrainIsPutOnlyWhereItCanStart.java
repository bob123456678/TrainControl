package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import support.SourceOf;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train is stood on a copy of its square facing the way the setup records, and among those on one it can be started
 * from.  The copy IS the direction: the runtime never commands an absolute direction, only reverses at a turning
 * square, so a train on a copy facing the wrong way would be dispatched along a route locked one way while its
 * decoder drives the other.  Where no copy trains may arrive at faces the recorded way, the train stands on one that
 * faces it anyway; autonomy will not start it there, and says so.  Among the copies facing the recorded way, one
 * trains may arrive at is chosen first, and the train is never turned around to get it (OB-270).
 *
 * MUTATION: have `placementCopy` or `moveOntoFacingCopy` take the first copy facing that way again, and its claim
 * fails; let either fall back to a copy trains may arrive at before one facing the recorded way, and the facing claims
 * fail; return copy zero with no facing recorded, and the no-facing claim fails.
 *
 * @author Adam
 */
public class testATrainIsPutOnlyWhereItCanStart
{
    private static final String PROBE = "start copy probe";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static List<LayoutDiagram> pages;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();
        model.stop();

        pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        assertNotNull(model.newMM2Locomotive(PROBE, 2396), "could not create this class's train");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (model != null) model.deleteLoc(PROBE);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The diagram's right-click **Place** keeps the train's heading as the other three placement doors do, onto the
     * copy the paste would take, and the same copy every time (OB-296, OB-284).
     *
     * MUTATION: choose among the station copies only, take the first copy facing the way rather than the paste's,
     * ignore the heading at the door, or move with the form of `moveLocomotive` that refuses a barred copy - and this
     * fails.
     */
    @Test
    public void testTheRightClickPlaceKeepsTheTrainsHeading() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        Layout running = build(session);

        String westbound = copyFacing(session, mainA, Side.W);

        assertNotNull(westbound, "precondition: BottomMainA has no copy facing west");
        assertFalse(running.getPoint(westbound).isDestination(), "precondition: " + westbound + " is a station here, so"
            + " nothing below is about a copy trains may not arrive at");

        // What the menu offers Place over - the station copies that reach a station - and the westbound one is not.
        List<String> usable = new ArrayList<>();

        for (String name : session.facingsFor(mainA).keySet())
        {
            Point copy = running.getPoint(name);

            if (copy != null && copy.isDestination() && running.canReachAnyDestination(copy)) usable.add(name);
        }

        assertTrue(!usable.isEmpty() && !usable.contains(westbound), "precondition: the menu's list is " + usable);

        java.lang.reflect.Method rule = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu")
            .getDeclaredMethod("copyToPlaceOn", AutonomySession.class, TileKey.class, Layout.class, Side.class, List.class);

        rule.setAccessible(true);

        Object west = rule.invoke(null, session, mainA, running, Side.W, usable);

        assertEquals(session.facingsFor(mainA).get(west), Side.W, "a train facing west, placed at BottomMainA by the"
            + " right-click Place, was put on " + west + ", facing " + session.facingsFor(mainA).get(west) + " - turned"
            + " round, where the paste keeps it facing west (OB-296, ADU-B1)");

        assertEquals(west, session.copyFacing(mainA, Side.W, running).getName(), "the right-click Place and the paste put a"
            + " train facing west on different copies (ADU-C1)");

        Object east = rule.invoke(null, session, mainA, running, Side.E, usable);

        assertEquals(east, session.copyFacing(mainA, Side.E, running).getName(), "the right-click Place and the paste put a"
            + " train facing east on different copies (ADU-C1)");

        // AND AT EVERY STATION OF HIS RAILWAY, EACH WAY: the paste's copy.  Where a heading has more than one copy
        // - a plain one and its turning twin, or one trains may not arrive at - the first the build made facing that way
        // need not be it; BottomMainA has one copy each way, so it alone cannot tell the two choices apart.
        int discriminating = 0;

        for (TileKey station : session.getStore().getNamedTiles())
        {
            if (!session.getStore().isStation(station) || session.facingsFor(station).size() < 2) continue;

            List<String> offered = new ArrayList<>();

            for (String name : session.facingsFor(station).keySet())
            {
                Point copy = running.getPoint(name);

                if (copy != null && copy.isDestination() && running.canReachAnyDestination(copy)) offered.add(name);
            }

            if (offered.isEmpty()) continue;

            for (Side side : Side.values())
            {
                Point paste = session.copyFacing(station, side, running);

                if (paste == null) continue;

                if (!paste.getName().equals(copyFacing(session, station, side))) discriminating++;

                assertEquals(rule.invoke(null, session, station, running, side, offered), paste.getName(), "the"
                    + " right-click Place put a train facing " + side + " at " + session.getStore().getPointName(station)
                    + " on another copy than the paste does (ADU-C1)");
            }
        }

        assertTrue(discriminating > 0, "precondition: at every station of his railway the first copy facing each way is"
            + " the paste's copy too, so nothing here tells the two choices apart");

        for (int again = 0; again < 8; again++)
        {
            assertEquals(rule.invoke(null, session, mainA, running, null, usable), usable.get(0), "a train with no"
                + " heading recorded was not put on the first copy the menu offers - the same placement answered"
                + " differently");
        }

        // THE DOOR: the heading read before the move and handed to the rule, and the move onto a barred copy allowed.
        String body = SourceOf.declaration("private void placeSomewhereLegal(");

        assertFalse(body.contains("Random"), "the right-click Place still draws the copy at random (OB-296)");

        // AND THE ITEM IS OFFERED ON THE ACTION'S OWN QUESTION: every copy the train could leave by - and
        // where none can, offered still and placed where the build would, as the editor's Place is (OB-350).
        assertFalse(SourceOf.allCode().contains("menuItem.setEnabled(!departable.isEmpty());"), "the right-click Place is"
            + " greyed where no copy can leave, while the editor's Place places there (OB-350)");

        assertTrue(body.contains("copyWithNoFacing(station, running)"), "the right-click Place does nothing where no copy"
            + " can leave: the action is handed an empty list (ADU2-C2, OB-350)");

        assertTrue(SourceOf.allCode().contains("placeSomewhereLegal(offered)"), "the right-click Place is offered over"
            + " copies it is then not handed: with every copy it could leave by barred, the action gets an empty list and does nothing"
            + " (ADU2-C2)");

        int read = body.indexOf("keep = ");
        int asked = body.indexOf("copyToPlaceOn(session, station, running, keep, usable)");
        int moved = body.indexOf("placeFacing(");

        assertTrue(read >= 0 && body.indexOf("facingOf(", read) > read && asked > read && moved > asked, "the right-click"
            + " Place does not read the train's heading, hand it to the rule, and only then move it (ADD-C5)");

        assertTrue(SourceOf.declaration("private void placeFacing(")
            .contains("moveLocomotive(locName, pointName, false, true)"),
            "the right-click Place moves with the form of moveLocomotive that refuses a copy trains may not arrive at"
            + " (ADU-B1, ADU-C2)");
    }

    /** A facing only a copy trains may not arrive at holds still stands the train on a copy facing that way. */
    @Test
    public void testTheBuildKeepsTheFacingTheSetupRecords() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        Layout before = build(session);

        String westbound = copyFacing(session, mainA, Side.W);

        assertNotNull(westbound, "precondition: BottomMainA has no copy facing west");
        assertFalse(before.getPoint(westbound).isDestination(), "precondition: " + westbound + " is a station here, so"
            + " the facing west is a possible one and nothing below is about a barred copy");

        // THE FACING ONLY THAT COPY HOLDS, as a door or an older setup leaves it.
        session.placeLocomotive(mainA, PROBE);
        session.setFacing(mainA, Side.W);

        Point standing = standingOn(build(session));

        assertNotNull(standing, "the build put the train nowhere");

        assertEquals(session.facingsFor(mainA).get(standing.getName()), Side.W, "the setup records the train at"
            + " BottomMainA facing west, and the build stood it on " + standing.getName() + ", which faces "
            + session.facingsFor(mainA).get(standing.getName()) + " - turned around in the model, so its next route is"
            + " locked one way while its decoder drives it the other (AUT2-A1)");
    }

    /**
     * A train placed with no facing recorded is stood on a copy it can be started from: copies are emitted N, E, S, W,
     * so at BottomMainA and BottomMainPost copy zero is the one trains may not arrive at.
     */
    @Test
    public void testATrainWithNoFacingIsPutWhereItCanStart() throws Exception
    {
        for (String name : new String[] {"BottomMainA", "BottomMainPost"})
        {
            AutonomySession session = session();

            TileKey square = square(session, name);

            session.placeLocomotive(square, PROBE);
            session.setFacing(square, null);

            Point standing = standingOn(build(session));

            assertNotNull(standing, "the build put the train at " + name + " nowhere");

            assertTrue(standing.isDestination(), "placed at " + name + " with no facing recorded, the train was stood"
                + " on " + standing.getName() + " - no station, so autonomy will not start it from there, and nothing"
                + " said which way it points to make that the truth (GUI2-B1)");
        }
    }

    /**
     * A train reversed on the throttle at a square whose other facing only a barred copy holds is moved onto a copy
     * facing its new way: the flip is a physical fact (the decoder has already reversed), so the copy follows it
     * whether or not autonomy can start the train from there.
     */
    @Test
    public void testAReversalOnTheThrottleIsFollowed() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        final Layout running = build(session);

        session.setRunningLayoutSource(() -> running);

        Point eastbound = stoodFacing(session, running, mainA, Side.E);

        session.placeLocomotive(mainA, PROBE);
        session.setFacing(mainA, Side.E);

        assertEquals(session.flipFacing(PROBE, running), mainA, "precondition: the direction-follow did not act on the"
            + " train at BottomMainA");

        assertEquals(session.getFacing(mainA), Side.W, "precondition: the flip did not record the train facing west");

        Point standing = standingOn(running);

        assertNotNull(standing, "the flip took the train off the railway");

        assertEquals(session.facingsFor(mainA).get(standing.getName()), Side.W, "reversed on the throttle at"
            + " BottomMainA, the train was recorded facing west and left on " + standing.getName() + " - facing "
            + session.facingsFor(mainA).get(standing.getName()) + " in the model, so its next route is locked that way"
            + " while its decoder drives it west (TDY2-A1)");

        assertTrue(eastbound != standing, "precondition: the train did not move at all");
    }

    /** And the Facing menu, which is how the operator tells the record which way the train really points. */
    @Test
    public void testTheFacingMenuMovesTheTrainOntoACopyFacingThatWay() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        final Layout running = theProbeStoodFacing(session, mainA, Side.E);

        session.setFacingAndMove(mainA, Side.W);

        Point standing = standingOn(running);

        assertNotNull(standing, "the Facing menu took the train off the railway");

        assertEquals(session.facingsFor(mainA).get(standing.getName()), Side.W, "told the train at BottomMainA faces"
            + " west, the Facing menu recorded west and left it on " + standing.getName() + ", facing "
            + session.facingsFor(mainA).get(standing.getName()) + " in the model (GUI2-A1)");
    }

    /** The Facing door moves a train onto a copy facing that way, one it can be started from where there is one. */
    @Test
    public void testTheFacingDoorNeverMovesATrainOntoACopyItCannotStartFrom() throws Exception
    {
        AutonomySession session = session();

        TileKey post = square(session, "BottomMainPost");

        final Layout running = build(session);

        session.setRunningLayoutSource(() -> running);

        // THE PREMISE: the first copy facing south is one no train may arrive at.
        String firstFacingSouth = copyFacing(session, post, Side.S);

        assertNotNull(firstFacingSouth, "precondition: BottomMainPost has no copy facing south");
        assertFalse(running.getPoint(firstFacingSouth).isDestination(), "precondition: " + firstFacingSouth + " is a"
            + " station, so the first copy facing south is a good one and this cannot fail");

        // A TRAIN THAT CAME IN FROM THE SOUTH AND TURNED - standing on the turning copy, facing south.
        Point turned = running.getPoint("BottomMainPost (northbound, reverse)");

        assertTrue(turned != null && turned.isDestination(), "precondition: no turning copy of BottomMainPost a train may"
            + " stand on");

        assertTrue(running.moveLocomotive(PROBE, turned.getName(), false), "could not stand the train on " + turned.getName());

        session.placeLocomotive(post, PROBE);

        session.setFacingAndMove(post, Side.S);

        Point standing = standingOn(running);

        assertNotNull(standing, "the facing door took the train off the railway");

        assertTrue(standing.isDestination(), "told the train at BottomMainPost faces south, the facing door moved it onto "
            + standing.getName() + " - no station, so autonomy will not start it from there");
    }

    private static AutonomySession session() throws Exception
    {
        AutonomySession session = new AutonomySession(sandbox.getFolder());

        session.open(pages);

        return session;
    }

    private static Layout build(AutonomySession session) throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Layout built = model.getAutoLayout();

        assertNotNull(built, "the setup did not build: " + Layout.getLastError());

        return built;
    }

    private static TileKey square(AutonomySession session, String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        throw new AssertionError("his railway has no " + name);
    }

    /** The first copy of the square, in the build's order, facing this way. */
    private static String copyFacing(AutonomySession session, TileKey square, Side facing)
    {
        for (Map.Entry<String, Side> copy : session.facingsFor(square).entrySet())
        {
            if (copy.getValue() == facing) return copy.getKey();
        }

        return null;
    }

    /**
     * The throttle's direction-follow flips the way the train faces ON THE RAILWAY, whatever the setup last said:
     * the setup names the heading a train set off with until a capture writes the arrival back, so after a run it is
     * absent or another train's.
     *
     * MUTATION: read the setup's facing first again and this fails.
     */
    @Test
    public void testAReversalIsFollowedWhateverTheSetupLastSaid() throws Exception
    {
        for (Side lastSaid : new Side[] {null, Side.W})
        {
            AutonomySession session = session();

            TileKey mainA = square(session, "BottomMainA");

            final Layout running = theProbeStoodFacing(session, mainA, Side.E);

            // WHAT THE SETUP HAS, which is not the railway's answer: nothing, or another train's facing.
            session.setFacing(mainA, lastSaid);

            session.flipFacing(PROBE, running);

            Point standing = standingOn(running);

            assertNotNull(standing, "the flip took the train off the railway");

            assertEquals(session.facingsFor(mainA).get(standing.getName()), Side.W, "the train stood facing east at"
                + " BottomMainA and was reversed on the throttle, with the setup saying " + lastSaid + " - and it was"
                + " left on " + standing.getName() + ", so its next route is locked the way its decoder no longer"
                + " drives (TDY3-A2)");
        }
    }

    /**
     * A train the railway has on a copy trains may not arrive at is put back there after a rebuild: the setup still
     * names the square the train set off from, and `putTheTrainsBack` stands each train where the railway had it,
     * through a move that must not refuse a copy that is no station.
     *
     * MUTATION: put the train back through the placement door's refusal again and this fails.
     */
    @Test
    public void testATrainPutBackStandsWhereItStood() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");
        TileKey post = square(session, "BottomMainPost");

        final Layout running = theProbeStoodFacing(session, mainA, Side.E);
        session.setFacingAndMove(mainA, Side.W);

        Point standing = standingOn(running);

        assertTrue(standing != null && !standing.isDestination(), "precondition: the train is not on the copy of"
            + " BottomMainA trains may not arrive at");

        // WHAT A RUN LEAVES: the setup names another square.
        session.placeLocomotive(post, PROBE);

        java.util.Map<String, String[]> where = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(running);

        Layout rebuilt = build(session);

        org.traincontrol.gui.TrainControlUI.putTheTrainsBack(rebuilt, where, null);

        Point after = standingOn(rebuilt);

        assertTrue(after != null && after.getName().equals(standing.getName()), "the railway had the train on "
            + standing.getName() + " and the rebuild's put-back left it on " + (after == null ? "nothing" : after.getName())
            + " - where the setup last had it, not where it is (TDY3-A1)");
    }

    /**
     * A train put back after a rebuild goes onto a copy it can start from that faces its way, where the square has
     * one: BottomMainPost, with arrivals from the north barred, has its north-arrival copy and the south-arrival
     * turning copy both facing south, and a train the railway had on the barred one must go back onto the other, or
     * autonomy never starts it.
     *
     * MUTATION: put the train back on the recorded copy whatever its twin, and this fails.
     */
    @Test
    public void testAPutBackTakesAStartableCopyFacingItsWay() throws Exception
    {
        AutonomySession session = session();

        TileKey post = square(session, "BottomMainPost");

        final Layout running = build(session);

        Point barred = null;
        Point twin = null;

        for (Map.Entry<String, Side> copy : session.facingsFor(post).entrySet())
        {
            Point point = running.getPoint(copy.getKey());

            if (point == null || copy.getValue() != Side.S) continue;

            if (point.isDestination()) twin = point;
            else if (barred == null) barred = point;
        }

        assertTrue(barred != null && twin != null, "precondition: BottomMainPost has no barred copy and startable copy"
            + " both facing south");

        // STANDING ON THE BARRED ONE, as a train there before the side was barred does.
        for (Point point : running.getPoints())
        {
            if (point.getCurrentLocomotive() != null && PROBE.equals(point.getCurrentLocomotive().getName()))
            {
                point.setLocomotive(null);
            }
        }

        barred.setLocomotive(model.getLocByName(PROBE));

        // WHAT A RUN LEAVES: the setup names another square, so the build does not stand the train at BottomMainPost
        // and only the put-back can; the setup saying BottomMainPost would let the build choose the twin, and the claim
        // not fail.
        session.placeLocomotive(square(session, "BottomMainA"), PROBE);

        java.util.Map<String, String[]> where = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(running);

        Layout rebuilt = build(session);

        org.traincontrol.gui.TrainControlUI.putTheTrainsBack(rebuilt, where, null);

        Point after = standingOn(rebuilt);

        assertTrue(after != null && after.getName().equals(twin.getName()), "the train facing south at BottomMainPost was"
            + " put back on " + (after == null ? "nothing" : after.getName()) + " - " + twin.getName() + " faces south"
            + " too and autonomy can start it there (TDY4-B1)");

        // AND THE BARRED ONE IS NOT CALLED BARRED: a train facing south can arrive at BottomMainPost, so
        // telling one stood on that copy to turn around would be false.
        assertFalse(rebuilt.isABarredCopyOfAStation(rebuilt.getPoint(barred.getName())), "BottomMainPost's south-facing"
            + " copy trains may not arrive at counts as barred, though its turning twin faces south and is a station -"
            + " a train stood there would be told to turn around (AUT4-B1)");
    }

    /**
     * A home set for a train standing on a copy trains may not arrive at saves no facing no train can come home in: the
     * facing of the train standing there must be filtered (a train facing the barred way stands on the barred copy).
     *
     * MUTATION: save the railway's facing unfiltered again and this fails.
     */
    @Test
    public void testAHomeIsNotSetFacingAWayNoTrainArrives() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        final Layout running = theProbeStoodFacing(session, mainA, Side.E);
        session.setFacingAndMove(mainA, Side.W);

        try
        {
            session.setHome(mainA, PROBE, null);

            Object saved = session.getPointProperty(mainA, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING);

            assertFalse("W".equals(saved), "a home at BottomMainA was saved facing west, a way no train may arrive"
                + " there - a facing Return Home can never bring a train back in (GUI3-C2)");
        }
        finally
        {
            session.setHome(mainA, null);
        }
    }

    /**
     * A train facing the way trains may not arrive at a station is told so, by the square's name, with what to do, and
     * nothing refuses its start by hand; the editor's Why not Moving? does not say it on Manual.
     *
     * MUTATION: name the copy, or refuse the start by hand, and this fails.
     */
    @Test
    public void testATrainFacingABarredWayIsToldWhy() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");
        TileKey post = square(session, "BottomMainPost");

        final Layout running = theProbeStoodFacing(session, mainA, Side.E);
        session.setFacingAndMove(mainA, Side.W);

        org.traincontrol.base.Locomotive train = model.getLocByName(PROBE);

        String expected = org.traincontrol.util.I18n.f("autolayout.why.startFacingBarred", "BottomMainA",
            org.traincontrol.util.I18n.t("autosetup.ui.menuArrivalsGroup"));

        assertEquals(running.explainCannotStart(train), expected, "a train facing the way trains may not arrive at"
            + " BottomMainA is told something else about why autonomy will not start it (GUI3-C1)");

        assertEquals(running.explainCannotStart(train, true), null, "by hand a route may start from where the train"
            + " stands, and Why not Moving? on Manual still said it cannot be sent anywhere (GUI3-C1)");

        // AND THE PAUSE IS AUTONOMY'S ALONE: nothing a hand send passes through reads it.
        train.setAutonomyPaused(true);

        try
        {
            assertEquals(running.explainCannotStart(train, true), null, "a paused train is told on Manual that it cannot"
                + " be sent anywhere, and the right-click menu sends it (GUI4-C2)");
        }
        finally
        {
            train.setAutonomyPaused(false);
        }

        // AND RETURN HOME'S SENTENCE, for a train whose home is elsewhere.
        String home = null;

        for (Map.Entry<String, Side> copy : session.facingsFor(post).entrySet())
        {
            Point point = running.getPoint(copy.getKey());

            if (point != null && point.isDestination()) home = point.getName();
        }

        assertNotNull(home, "precondition: BottomMainPost has no copy a train may be homed on");

        running.setHomeLocomotive(home, PROBE);

        try
        {
            java.util.List<String> why = org.traincontrol.automation.HomeStaging.snapshot(running).plan().getReasons()
                .get(train);

            String expectedHome = org.traincontrol.util.I18n.f("autolayout.whyHomeStartFacingBarred", "BottomMainA",
                org.traincontrol.util.I18n.t("autosetup.ui.menuArrivalsGroup"));

            assertTrue(why != null && why.contains(expectedHome), "Return Home did not say the train faces the way trains"
                + " may not arrive at BottomMainA, and what to do; it said " + why + " (AUT3-B1)");
        }
        finally
        {
            running.clearHomeLocomotives();
        }
    }

    /**
     * Among the copies facing the recorded way, the build takes one trains may arrive at: at BottomMainPost, with
     * arrivals from the north barred, two copies face south (plain, arriving from the north, no station; turning,
     * arriving from the south), and a train recorded facing south is stood on the one autonomy can start.
     *
     * MUTATION: let `placementCopy` take the first copy facing that way and this fails.
     */
    @Test
    public void testTheBuildPrefersACopyItCanStartFrom() throws Exception
    {
        AutonomySession session = session();

        TileKey post = square(session, "BottomMainPost");

        session.placeLocomotive(post, PROBE);
        session.setFacing(post, Side.S);

        Point standing = standingOn(build(session));

        assertNotNull(standing, "the build put the train nowhere");

        assertEquals(session.facingsFor(post).get(standing.getName()), Side.S, "precondition: the build turned the train");

        assertTrue(standing.isDestination(), "recorded facing south at BottomMainPost, the train was stood on "
            + standing.getName() + " - no station - where a copy facing south that trains may arrive at exists");
    }

    /**
     * At a square every train must turn around at, a train facing the barred way is told to drive it off by hand or turn
     * it round - not to open the side, which a terminus reached from two sides (MT-361) would answer with another
     * refusal.
     *
     * MUTATION: offer the three remedies whatever the square, and this fails.
     */
    @Test
    public void testAtACompulsoryTurnOpeningTheSideIsNotOffered() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        session.setPointProperty(mainA, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);

        try
        {
            assertFalse(session.terminiWithTwoWaysIn().containsKey(mainA), "precondition: BottomMainA made a compulsory"
                + " turn is already the terminus error, so its east side is not barred");

            final Layout running = theProbeStoodFacing(session, mainA, Side.W);

            session.flipFacing(PROBE, running);

            Point standing = standingOn(running);

            assertTrue(standing != null && running.isABarredCopyOfAStation(standing), "precondition: turned around at"
                + " BottomMainA, the train does not stand on the copy trains may not arrive at: " + standing);

            String said = running.explainCannotStart(model.getLocByName(PROBE));

            assertEquals(said, org.traincontrol.util.I18n.f("autolayout.why.startFacingBarredMustTurn", "BottomMainA"),
                "at a compulsory turn the train facing the barred way is offered opening that side, which raises the"
                + " terminus error (GUI4-C3)");
        }
        finally
        {
            session.setPointProperty(mainA, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, null);
        }
    }

    /**
     * A placement keeps a heading a train can leave by, and turns only one it cannot (OB-284): barred departure
     * directions are checked, not arrival ones.
     *
     * MUTATION: keep only headings trains may arrive in, and the first half fails; keep any heading, and the second
     * does.
     */
    @Test
    public void testAPlacementKeepsAHeadingItCanLeaveBy() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        Layout running = build(session);

        String westbound = copyFacing(session, mainA, Side.W);

        assertNotNull(westbound, "precondition: BottomMainA has no copy facing west");
        assertFalse(running.getPoint(westbound).isDestination(), "precondition: trains may arrive at BottomMainA"
            + " facing west, so nothing here is about a barred arrival");
        assertFalse(running.getNeighbors(running.getPoint(westbound)).isEmpty(), "precondition: a train facing west"
            + " at BottomMainA has no way out west");

        assertEquals(AutonomySession.facingAfterAPaste(session.departableFacingsFor(mainA, running), Side.W, null),
            Side.W, "a train facing west at BottomMainA, where trains may not arrive facing west and can leave by the"
            + " west, is turned east by a placement (OB-284: \"for barred arrival directions, keep the direction\")");

        // AND WITH NO WAY OUT WEST, turned to a way trains may arrive in.
        assertTrue(session.setOneWayRun(square(session, "BottomMainAPre"), mainA) > 0, "precondition: no run from"
            + " BottomMainAPre to BottomMainA to make one-way");

        running = build(session);

        String stillWestbound = copyFacing(session, mainA, Side.W);

        assertTrue(stillWestbound == null || running.getNeighbors(running.getPoint(stillWestbound)).isEmpty(),
            "precondition: made one-way toward BottomMainA, a train facing west there still has a way out");

        Side placed = AutonomySession.facingAfterAPaste(session.departableFacingsFor(mainA, running), Side.W, null);

        assertTrue(placed != Side.W, "a train facing west at BottomMainA, where no train can leave by the west, is"
            + " left facing west by a placement (OB-284: \"for barred departure directions, turn it\")");
        assertTrue(placed == null || session.homeFacingsFor(mainA).contains(placed), "turned, it faces " + placed
            + ", which is not a way trains may arrive in (OB-284)");
    }

    /** The railway built, serving the session, with the probe train stood facing that way on the square and placed there. */
    private static Layout theProbeStoodFacing(AutonomySession session, TileKey square, Side facing) throws Exception
    {
        final Layout running = build(session);

        session.setRunningLayoutSource(() -> running);

        stoodFacing(session, running, square, facing);

        session.placeLocomotive(square, PROBE);

        return running;
    }

    private static Point stoodFacing(AutonomySession session, Layout running, TileKey square, Side facing)
    {
        for (Map.Entry<String, Side> copy : session.facingsFor(square).entrySet())
        {
            Point point = running.getPoint(copy.getKey());

            if (copy.getValue() != facing || point == null || !point.isDestination()) continue;

            assertTrue(running.moveLocomotive(PROBE, point.getName(), false), "could not stand the train on "
                + point.getName());

            return point;
        }

        throw new AssertionError("precondition: no copy of the square faces " + facing + " and is a station");
    }

    private static Point standingOn(Layout layout)
    {
        for (Point p : layout.getPoints())
        {
            if (p.getCurrentLocomotive() != null && PROBE.equals(p.getCurrentLocomotive().getName())) return p;
        }

        return null;
    }
}
