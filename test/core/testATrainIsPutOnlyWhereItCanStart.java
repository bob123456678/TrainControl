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
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train is stood on a copy of its square facing the way the setup records, and among those on one it can be started
 * from (GUI-B1, TDY2-A1, GUI2-A1, AUT2-A1, GUI2-B1).
 *
 * **The copy IS the direction.**  Nothing else in the running layout says which way a train will move: the runtime never
 * commands an absolute direction, it only reverses at a turning square.  So a train stood on a copy facing the other way
 * from the way it really points is dispatched along a route locked one way while its decoder drives it the other, over
 * track nothing reserved.  GUI-B1's first repair did exactly that where only a copy trains may not arrive at faces the
 * recorded way - the build, the throttle's direction-follow and the Facing menu all stood the train on a copy facing the
 * other way, and the log said the direction had been followed.  Where no copy trains may arrive at faces the recorded
 * way, the train now stands on one that faces it anyway: autonomy will not start it there, and says so - that it faces
 * the way trains may not arrive at that square, and what to do: turn it round, or open that side (GUI3-C1).
 *
 * GUI-B1's own case stands: among the copies facing the recorded way, one trains may arrive at is chosen first.
 *
 * What GUI-B1 was, in the past tense: Adam, 2026-09-23, on OB-270: *"we shouldn't allow an impossible facing to be
 * saved."*  That was built into the paste.  But a facing only a barred copy holds could still reach the setup - through
 * the editor's Place door and the Place Locomotive dialog, or from any setup saved before - and the build, the Facing
 * door and the idle drain after a turn put the train on "the copy that faces X" without asking whether trains may arrive
 * at it, even where one that faces X and may be arrived at existed.  That preference is what still stands; the train is
 * never turned round to get it.
 *
 * **On the frozen railway, as he has it**: BottomMainA with arrivals from the east barred, so its westbound copy is no
 * station; BottomMainPost, which trains may turn at and which has arrivals from the north barred.
 *
 * MUTATION: have `placementCopy` or `moveOntoFacingCopy` take the first copy facing that way again, and its claim fails;
 * let either fall back to a copy trains may arrive at before one facing the recorded way, and the facing claims fail;
 * return copy zero with no facing recorded, and the no-facing claim fails.
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

        model = init(null, true, false, false, true);
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
     * A facing only a copy trains may not arrive at holds still stands the train on a copy facing that way (AUT2-A1).
     *
     * @throws Exception from the build
     */
    /**
     * The diagram's right-click **Place** stands the train on the copy facing the way it already faces, and the same
     * copy every time - no longer one at random (Adam, 2026-09-24, OB-296: *"Yes, keep the train's heading."*).
     *
     * The other three placement doors keep the heading over the copies a train can leave by (OB-270, GUI-B1, OB-284);
     * this one took `usable.get(new Random().nextInt(...))`, so the same train placed the same way faced either way.
     * The rule is the paste's, `AutonomySession.facingAfterAPaste`: the heading where the square can hold it, the one
     * heading a single copy has, and otherwise the first copy the build made - never a draw.
     *
     * MUTATION: draw the copy at random again, or ignore the heading, and this fails.
     *
     * @throws Exception from the reflection or the files
     */
    @Test
    public void testTheRightClickPlaceKeepsTheTrainsHeading() throws Exception
    {
        java.lang.reflect.Method rule = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu")
            .getDeclaredMethod("copyToPlaceOn", List.class, Map.class, Side.class);

        rule.setAccessible(true);

        List<String> usable = java.util.Arrays.asList("Platform (eastbound)", "Platform (westbound)");

        Map<String, Side> facings = new java.util.LinkedHashMap<>();

        facings.put(usable.get(0), Side.E);
        facings.put(usable.get(1), Side.W);

        assertEquals(rule.invoke(null, usable, facings, Side.W), "Platform (westbound)", "a train facing west was put on"
            + " the copy facing east");

        assertEquals(rule.invoke(null, usable, facings, Side.E), "Platform (eastbound)", "a train facing east was put on"
            + " the copy facing west");

        for (int again = 0; again < 8; again++)
        {
            assertEquals(rule.invoke(null, usable, facings, null), usable.get(0), "a train with no heading recorded was"
                + " not put on the first copy the build made - the same placement answered differently");
        }

        assertEquals(rule.invoke(null, java.util.Arrays.asList(usable.get(1)), facings, Side.E), usable.get(1), "the"
            + " one copy a train can leave was refused because the train faced the other way");

        // AND THE DOOR ASKS IT, with the heading read before the move.
        String menu = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java")), java.nio.charset.StandardCharsets.UTF_8);

        int start = menu.indexOf("private void placeSomewhereLegal(");

        assertTrue(start > 0, "precondition: placeSomewhereLegal has moved - if so, move this");

        String body = menu.substring(start, menu.indexOf("\n    }", start));

        assertFalse(body.contains("Random"), "the right-click Place still draws the copy at random (OB-296)");

        assertTrue(body.contains("copyToPlaceOn(") && body.contains("facingOf("), "the right-click Place does not ask"
            + " the heading rule with the train's own heading (OB-296)");
    }

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
            + session.facingsFor(mainA).get(standing.getName()) + " - turned round in the model, so its next route is"
            + " locked one way while its decoder drives it the other (AUT2-A1)");
    }

    /**
     * A train placed with no facing recorded is stood on a copy it can be started from (GUI2-B1).
     *
     * The build returned copy zero when the setup held no facing, and copies are emitted N, E, S, W - so at BottomMainA
     * and BottomMainPost copy zero is the one trains may not arrive at.  A door that places a train and records no facing
     * (several copies it could stand on, and nothing saying which way it points) is ordinary.
     *
     * @throws Exception from the build
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
     * facing its new way (TDY2-A1, GUI2-A1).
     *
     * Adam: *"a locomotive direction command WILL update the direction on the graph if it does not match"*.  The flip is a
     * physical fact - the decoder has already reversed - so the copy has to follow it, whether or not autonomy can start
     * the train from there.
     *
     * @throws Exception from the build
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

    /**
     * And the Facing menu, which is how the operator tells the record which way the train really points (GUI2-A1).
     *
     * @throws Exception from the build
     */
    @Test
    public void testTheFacingMenuMovesTheTrainOntoACopyFacingThatWay() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        final Layout running = build(session);

        session.setRunningLayoutSource(() -> running);

        stoodFacing(session, running, mainA, Side.E);

        session.placeLocomotive(mainA, PROBE);

        session.setFacingAndMove(mainA, Side.W);

        Point standing = standingOn(running);

        assertNotNull(standing, "the Facing menu took the train off the railway");

        assertEquals(session.facingsFor(mainA).get(standing.getName()), Side.W, "told the train at BottomMainA faces"
            + " west, the Facing menu recorded west and left it on " + standing.getName() + ", facing "
            + session.facingsFor(mainA).get(standing.getName()) + " in the model (GUI2-A1)");
    }

    /**
     * The Facing door moves a train onto a copy facing that way, one it can be started from where there is one.
     *
     * @throws Exception from the build
     */
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

    // ---------------------------------------------------------------------------------------------

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
     * The throttle's direction-follow flips the way the train faces ON THE RAILWAY, whatever the setup last said
     * (TDY3-A2, AUT3-A1).
     *
     * `flipFacing` took "the other one" of the SETUP's facing for the square.  The setup names the heading a train set
     * off with until a capture writes the arrival back, so after a run it is absent - and the reversal was dropped - or
     * another train's - and the flip went the wrong way while the log said it was followed.  Either way the train was
     * left on the copy facing the way its decoder no longer drives.
     *
     * MUTATION: read the setup's facing first again and this fails.
     *
     * @throws Exception from the build
     */
    @Test
    public void testAReversalIsFollowedWhateverTheSetupLastSaid() throws Exception
    {
        for (Side lastSaid : new Side[] {null, Side.W})
        {
            AutonomySession session = session();

            TileKey mainA = square(session, "BottomMainA");

            final Layout running = build(session);

            session.setRunningLayoutSource(() -> running);

            stoodFacing(session, running, mainA, Side.E);

            session.placeLocomotive(mainA, PROBE);

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
     * A train the railway has on a copy trains may not arrive at is put back there after a rebuild (TDY3-A1).
     *
     * After a run the setup still names the square a train set off from, and a rebuild - setting a home or a caption from
     * the diagram asks for one - regenerates every placement from it; `putTheTrainsBack` then stands each train back
     * where the railway had it.  It did that through `moveLocomotive`, which refuses a copy that is no station, so a
     * train reversed onto BottomMainA's westbound copy was left where the setup had it - in the model somewhere it is
     * not, and free to be dispatched from there.
     *
     * MUTATION: put the train back through the placement door's refusal again and this fails.
     *
     * @throws Exception from the build
     */
    @Test
    public void testATrainPutBackStandsWhereItStood() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");
        TileKey post = square(session, "BottomMainPost");

        final Layout running = build(session);

        session.setRunningLayoutSource(() -> running);

        stoodFacing(session, running, mainA, Side.E);

        session.placeLocomotive(mainA, PROBE);
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
     * A train put back after a rebuild goes onto a copy it can start from that faces its way, where the square has one
     * (TDY4-B1, AUT4-B1).
     *
     * The put-back may stand a train on a copy of a station trains may not arrive at (TDY3-A1) - for a train that really
     * faces a way only such a copy holds.  At a square trains may turn at, a turning copy facing the same way may be one
     * trains may arrive at: BottomMainPost, with arrivals from the north barred, has its north-arrival copy and the
     * south-arrival turning copy both facing south.  A train the railway had on the barred one - it stood there before the
     * side was barred - was put back onto it, where autonomy never starts it and the sentence tells the operator to turn
     * a train that already faces a way it can start.
     *
     * MUTATION: put the train back on the recorded copy whatever its twin, and this fails.
     *
     * @throws Exception from the build
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
        // and only the put-back can.  With the setup saying BottomMainPost, the build's own choice stood it on the twin
        // whatever the put-back did, and the claim could not fail.
        session.placeLocomotive(square(session, "BottomMainA"), PROBE);

        java.util.Map<String, String[]> where = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(running);

        Layout rebuilt = build(session);

        org.traincontrol.gui.TrainControlUI.putTheTrainsBack(rebuilt, where, null);

        Point after = standingOn(rebuilt);

        assertTrue(after != null && after.getName().equals(twin.getName()), "the train facing south at BottomMainPost was"
            + " put back on " + (after == null ? "nothing" : after.getName()) + " - " + twin.getName() + " faces south"
            + " too and autonomy can start it there (TDY4-B1)");

        // AND THE BARRED ONE IS NOT CALLED BARRED (AUT4-B1): a train facing south can arrive at BottomMainPost, so
        // telling one stood on that copy to turn round would be false.
        assertFalse(rebuilt.isABarredCopyOfAStation(rebuilt.getPoint(barred.getName())), "BottomMainPost's south-facing"
            + " copy trains may not arrive at counts as barred, though its turning twin faces south and is a station -"
            + " a train stood there would be told to turn round (AUT4-B1)");
    }

    /**
     * A home set for a train standing on a copy trains may not arrive at saves no facing no train can come home in
     * (GUI3-C2, AUT3-C2).
     *
     * Adam: *"we shouldn't allow an impossible facing to be saved."*  The facing of the train standing there was saved
     * unfiltered, on the premise that a copy a train stands on is one it may arrive at - which stopped being so when a
     * train facing the barred way was stood on the barred copy.
     *
     * MUTATION: save the railway's facing unfiltered again and this fails.
     *
     * @throws Exception from the build
     */
    @Test
    public void testAHomeIsNotSetFacingAWayNoTrainArrives() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        final Layout running = build(session);

        session.setRunningLayoutSource(() -> running);

        stoodFacing(session, running, mainA, Side.E);

        session.placeLocomotive(mainA, PROBE);
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
     * A train facing the way trains may not arrive at a station is told so, by the square's name, with what to do - and
     * nothing refuses its start by hand (GUI3-C1, AUT3-B1, DCN3-C6).
     *
     * The sentence said "It is standing on BottomMainA (westbound), which is not a station": the copy's name, a station
     * called not one, no remedy - and the editor's Why not Moving? gave it on Manual too, where a route picked by hand
     * may start there.  Return Home said "place it on a station first", and a paste onto the same square turns the
     * train round.
     *
     * MUTATION: name the copy, or refuse the start by hand, and this fails.
     *
     * @throws Exception from the build
     */
    @Test
    public void testATrainFacingABarredWayIsToldWhy() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");
        TileKey post = square(session, "BottomMainPost");

        final Layout running = build(session);

        session.setRunningLayoutSource(() -> running);

        stoodFacing(session, running, mainA, Side.E);

        session.placeLocomotive(mainA, PROBE);
        session.setFacingAndMove(mainA, Side.W);

        org.traincontrol.base.Locomotive train = model.getLocByName(PROBE);

        String expected = org.traincontrol.util.I18n.f("autolayout.why.startFacingBarred", "BottomMainA",
            org.traincontrol.util.I18n.t("autosetup.ui.menuArrivalsGroup"));

        assertEquals(running.explainCannotStart(train), expected, "a train facing the way trains may not arrive at"
            + " BottomMainA is told something else about why autonomy will not start it (GUI3-C1)");

        assertEquals(running.explainCannotStart(train, true), null, "by hand a route may start from where the train"
            + " stands, and Why not Moving? on Manual still said it cannot be sent anywhere (GUI3-C1)");

        // AND THE PAUSE IS AUTONOMY'S ALONE (GUI4-C2): nothing a hand send passes through reads it.
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
     * Among the copies facing the recorded way, the build takes one trains may arrive at (GUI-B1, DCN3-C3).
     *
     * At BottomMainPost, which trains may turn at with arrivals from the north barred, two copies face south: the plain
     * copy arriving from the north - no station - and the turning copy arriving from the south.  A train recorded
     * facing south is stood on the one autonomy can start.
     *
     * MUTATION: let `placementCopy` take the first copy facing that way and this fails.
     *
     * @throws Exception from the build
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

    /** Stands the train on a copy of the square facing this way that it can start from, and says which. */
    /**
     * At a square every train must turn round at, a train facing the barred way is told to drive it off by hand or turn
     * it round - not to open the side (GUI4-C3, Adam 2026-09-24: *"offer the other two remedies only"*).
     *
     * A terminus reached from two sides is an error (MT-361), whose own remedy is to close one of them - so "open that
     * side" there sends the operator from one refusal into a loop.  BottomMainA made a compulsory turn, its east side
     * still barred: the train turned to face east stands on the barred turning copy.
     *
     * MUTATION: offer the three remedies whatever the square, and this fails.
     *
     * @throws Exception from the build
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

            final Layout running = build(session);

            session.setRunningLayoutSource(() -> running);

            stoodFacing(session, running, mainA, Side.W);

            session.placeLocomotive(mainA, PROBE);

            session.flipFacing(PROBE, running);

            Point standing = standingOn(running);

            assertTrue(standing != null && running.isABarredCopyOfAStation(standing), "precondition: turned round at"
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
     * A placement keeps a heading a train can leave by, and turns only one it cannot (OB-284).
     *
     * Adam, 2026-09-24: *"this should check for barred departure directions, not arrival ones.  for barred arrival
     * directions, keep the direction.  for barred departure directions, turn it to a way trains may arrive in"*.  At
     * BottomMainA, arrivals from the east barred, a train facing west stands on a copy trains may not arrive at - and can
     * leave by the west, so a paste, the editor's Place and the Place Locomotive dialog keep it facing west.  Made
     * one-way eastward on its approach, the west copy has no way out: then the placement turns the train east, a way
     * trains may arrive in.
     *
     * MUTATION: keep only headings trains may arrive in, as before, and the first half fails; keep any heading, and the
     * second does.
     *
     * @throws Exception from the build
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
