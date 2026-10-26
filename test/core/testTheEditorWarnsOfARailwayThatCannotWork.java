package core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import support.SourceOf;

/**
 * The editor's findings: a sensor address shared, an arrival that is trapped, a station nothing reaches, a
 * terminus with two ways in.
 *
 * The session and layout folder every claim starts from are built by `AutonomySessionFixture`.
 *
 * @author Adam
 */
public class testTheEditorWarnsOfARailwayThatCannotWork extends AutonomySessionFixture
{

    /**
     * A sensor address on two squares of one page is said once, naming both squares, as a WARNING (FR-139).
     *
     * MUTATION: leave the check out of the setup check, or leave one of the two squares out of its places, and this
     * fails.
     */
    @Test
    public void testASensorAddressOnTwoSquaresIsSaidOnce() throws Exception
    {
        session.open(Arrays.asList(runOfTrack(), twoSensorPage("second", "2", 40)));
        session.initialize("FR139 shared");

        org.traincontrol.automationui.AutonomyChecks.Finding shared = sharedAddressFinding();

        assertNotNull(shared, "address 40 is on two squares of one page, and the setup check does not say so");

        assertEquals(shared.getSeverity(), org.traincontrol.automationui.AutonomyChecks.Severity.WARNING,
            "a shared sensor address is not a WARNING: " + shared);

        assertEquals(new LinkedHashSet<>(shared.getRelated()),
            new LinkedHashSet<>(Arrays.asList(new TileKey("second", 1, 1), new TileKey("second", 3, 1))),
            "the places are not the two squares that carry address 40: " + shared.getRelated());
    }

    /**
     * A sensor address two pages share is OB-150's ERROR alone - the later page repeats the earlier one's sensors - and
     * the shared-address warning does not say it again (FR-139; Adam: "suppress the warning where OB-150 applies").
     *
     * MUTATION: count the squares of a page OB-150 reports, and this fails.
     */
    @Test
    public void testASharedAddressThatOB150ReportsIsNotSaidAgain() throws Exception
    {
        session.open(Arrays.asList(runOfTrack(), sensorPage("second", "2", 11)));
        session.initialize("FR139 OB150");

        boolean repeated = false;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            repeated |= org.traincontrol.automationui.AutonomyChecks.DUPLICATE_SENSOR_PAGE.equals(finding.getMessageKey());
        }

        assertTrue(repeated, "precondition: address 11 on a square of each of two pages is not OB-150's ERROR");

        assertNull(sharedAddressFinding(), "a share OB-150 already reports is said a second time");
    }

    /**
     * A page autonomy does not use is not one of the places a sensor address is shared between (FR-139).
     *
     * The control is the same two squares with both pages in, so the absence below is about the exclusion and not about
     * a check that has stopped finding anything.
     *
     * MUTATION: read the excluded pages out of the sensor check, or leave the excluded page in its places, and this
     * fails.
     */
    @Test
    public void testAnExcludedPageDoesNotShareItsSensorAddress() throws Exception
    {
        session.open(Arrays.asList(runOfTrack(), twoSensorPage("second", "2", 40)));
        session.initialize("FR139 excluded");

        assertNotNull(sharedAddressFinding(), "precondition: the page's two squares are not reported before it is left out");

        session.setPageExcluded("second", true);

        assertNull(sharedAddressFinding(), "a sensor on a page left out of autonomy is still said to share its address");
    }

    /**
     * A sensor with no address is not a shared address, however many squares have none (FR-139).
     *
     * An address of zero or less is no address: the reduction makes no point of one and the duplicate-sensor check
     * passes over one.
     *
     * MUTATION: count a sensor whose address is 0 or -1 as an address, and this fails.
     */
    @Test
    public void testASensorWithNoAddressIsNotShared() throws Exception
    {
        LayoutDiagram unaddressed = new LayoutDiagram("second", 6, 4, null, null);

        unaddressed.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        unaddressed.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, -1, -1, accessoryDecoderType.MM2, null);
        unaddressed.setPageId("2");

        session.open(Arrays.asList(runOfTrack(), unaddressed));
        session.initialize("FR139 none");

        assertNull(sharedAddressFinding(), "two sensors with no address are said to share one");
    }

    /**
     * A square that builds into two copies, one per way a train comes in, is one place and is not said to share its
     * address with itself (FR-139).
     *
     * The precondition is the copies: the middle square builds into more than one Point, or the claim says nothing.
     *
     * MUTATION: count each copy of a square as a place, and this fails.
     */
    @Test
    public void testASquareThatBuildsIntoCopiesIsNotSharedWithItself() throws Exception
    {
        threeStationsInARow("FR139 copies");

        TileKey middle = new TileKey("main", 3, 1);

        int copies = 0;

        org.json.JSONArray points = new org.json.JSONObject(session.buildConfiguration()).getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            if (middle.toString().equals(points.getJSONObject(i).optString("square"))) copies++;
        }

        assertTrue(copies > 1, "precondition: the middle square builds into " + copies + " Point, so the claim below is"
            + " about nothing");

        assertNull(sharedAddressFinding(), "a square that builds into " + copies + " copies is said to share its address"
            + " with itself");
    }

    /** The setup check's shared-sensor-address finding, or null (FR-139). */
    private org.traincontrol.automationui.AutonomyChecks.Finding sharedAddressFinding()
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.SENSOR_ADDRESS_SHARED.equals(finding.getMessageKey()))
            {
                return finding;
            }
        }

        return null;
    }

    /** A page of its own with two sensors, at (1,1) and (3,1), both carrying `address`, and a straight between (FR-139). */
    private LayoutDiagram twoSensorPage(String name, String id, int address) throws IOException
    {
        LayoutDiagram page = sensorPage(name, id, address);

        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, address, address, accessoryDecoderType.MM2, null);

        return page;
    }

    /** A page of its own with one sensor at (1,1) carrying `address`, and a straight beside it (FR-139). */
    private LayoutDiagram sensorPage(String name, String id, int address) throws IOException
    {
        LayoutDiagram page = new LayoutDiagram(name, 6, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, address, address, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.setPageId(id);

        return page;
    }

    /**
     * check reports it as an error (Adam: "Generally I prefer warnings or errors over outright refusals, since it makes
     * editing easier for the user.  Make refusals that make sense be errors").
     *
     * MUTATION: report it as a warning, and this fails.
     */
    @Test
    public void testADirectionUnderAPlacedTrainIsSetAndReportedAsAnError() throws Exception
    {
        threeStationsInARow("R34 facing");

        TileKey beta = new TileKey("main", 3, 1);

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(beta, "Beta");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        session.placeLocomotive(beta, "R34 facing train");
        session.setFacing(beta, org.traincontrol.automationui.TilePorts.Side.W);
        session.rebuild();

        // ONE WAY EAST EITHER SIDE OF IT: no copy of Beta faces west
        session.setDirection(new LinkedHashSet<>(Arrays.asList(new TileKey("main", 2, 1), new TileKey("main", 4, 1))),
            Direction.TOWARD_A);

        assertEquals(session.getStore().getTileDirection(new TileKey("main", 2, 1), new RouteId(0, 0)), Direction.TOWARD_A,
            "a direction under a placed train was not set (Adam, 2026-10-01: warnings or errors over refusals)");

        assertTrue(findingAt(org.traincontrol.automationui.AutonomyChecks.FACING_IMPOSSIBLE,
            org.traincontrol.automationui.AutonomyChecks.Severity.ERROR, beta), "a placed train left facing a way its"
            + " station no longer has is not reported as an error: " + session.check());

        assertEquals(session.getLocomotiveNameAt(beta), "R34 facing train", "the train left its square in the setup");
    }

    /**
     * A placed train facing track that runs only towards it is a warning - set, not refused (Adam: "smaller issues be
     * warnings") - and the track closed instead is not, nothing arriving by it.
     *
     * MUTATION: leave the warning out, and this fails.
     */
    @Test
    public void testATrainFacingTrackRunningTowardsItIsAWarning() throws Exception
    {
        threeStationsInARow("R34 towards");

        TileKey beta = new TileKey("main", 3, 1);
        TileKey west = new TileKey("main", 2, 1);

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(beta, "Beta");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        session.placeLocomotive(beta, "R34 towards train");
        session.setFacing(beta, org.traincontrol.automationui.TilePorts.Side.W);
        session.rebuild();

        session.setRunDirection(west, new RouteId(0, 0), Direction.TOWARD_A);

        assertTrue(findingAt(org.traincontrol.automationui.AutonomyChecks.TRAIN_FACES_TRACK_TOWARDS_IT,
            org.traincontrol.automationui.AutonomyChecks.Severity.WARNING, beta), "a placed train facing track that runs"
            + " only towards it is not a warning: " + session.check());

        // CLOSED INSTEAD: nothing arrives by that side
        session.setRunDirection(west, new RouteId(0, 0), Direction.NONE);

        assertFalse(findingAt(org.traincontrol.automationui.AutonomyChecks.TRAIN_FACES_TRACK_TOWARDS_IT,
            org.traincontrol.automationui.AutonomyChecks.Severity.WARNING, beta), "track closed ahead of a train was"
            + " reported as running towards it: " + session.check());
    }

    /**
     * A trapped arrival is named by the side it comes in by, and the advice offers to close arrivals from that side, as
     * well as opening the way ahead or letting trains change direction there (OB-201; Adam: *"This error message should
     * say either close arrivals from <direction>, open the way ahead, or let trains change direction."*).
     *
     * MUTATION: name the point instead of the side, or every way in rather than the trapped one, and this fails.
     */
    @Test
    public void testATrappedArrivalNamesTheSideItComesFrom() throws Exception
    {
        threeStationsInARow("OB-201");

        // TRAINS TURN ROUND AT BOTH ENDS, so a train reaches 3,1 from either side (OB-361)
        turnTrainsRoundAtBothEnds();

        TileKey beta = new TileKey("main", 3, 1);

        // EAST OF BETA, ONE WAY TOWARDS IT: a train arriving from the west cannot go on, one from the east can
        session.setDirection(new TileKey("main", 4, 1), new RouteId(0, 0), Direction.TOWARD_B);
        session.rebuild();

        org.traincontrol.automationui.AutonomyChecks.Finding trapped = null;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.ARRIVAL_TRAPPED.equals(finding.getMessageKey())
                && beta.equals(finding.getTile()))
            {
                trapped = finding;
            }
        }

        assertNotNull(trapped, "precondition: a train arriving at 3,1 from the west is not reported trapped: "
            + session.check());

        String west = org.traincontrol.util.I18n.t("autosetup.ui.sideW");

        assertEquals(trapped.getSubject(), west, "the trapped arrival is not named by the side it comes in by (OB-201)");

        String said = org.traincontrol.util.I18n.f(trapped.getMessageKey(), "3,1", trapped.getSubject());

        assertTrue(said.split(java.util.regex.Pattern.quote(west), -1).length - 1 >= 2, "the advice does not offer to"
            + " close arrivals from the side the train comes in by (OB-201): " + said);
    }

    /**
     * Closing arrivals from the side a trapped arrival comes in by clears the warning.  It is the first of the three
     * ways out the warning offers (OB-201), and the check never asked which arrivals were closed: the operator who took
     * that advice got the same warning back, still telling them to close arrivals from that side.
     *
     * MUTATION: count a closed side's copy as trapped again, and this fails.
     */
    @Test
    public void testClosingTheTrappedSideClearsTheWarning() throws Exception
    {
        threeStationsInARow("RSA37-B1");

        turnTrainsRoundAtBothEnds();

        TileKey beta = new TileKey("main", 3, 1);

        session.setDirection(new TileKey("main", 4, 1), new RouteId(0, 0), Direction.TOWARD_B);
        session.rebuild();

        org.traincontrol.automationui.AutonomyChecks.Finding before = trappedAt(beta);

        assertNotNull(before, "precondition: a train arriving at 3,1 from the west is not reported trapped: "
            + session.check());

        assertEquals(before.getMessageKey(), org.traincontrol.automationui.AutonomyChecks.ARRIVAL_TRAPPED, "precondition:"
            + " the warning does not offer to close arrivals from the west, where the arrivals menu can");

        // THE ADVICE TAKEN, as the arrivals menu takes it
        session.setBarredArrivals(beta, new LinkedHashSet<>(Arrays.asList(Side.W)));
        session.rebuild();

        assertNull(trappedAt(beta), "closing arrivals from the side the warning names left the warning in place, still"
            + " telling the operator to close them (RSA37-B1): " + session.check());
    }

    /**
     * The trapped-arrival warning offers to close arrivals only where the arrivals menu can do it: not at a station
     * trapped from every way in, whose last open side the menu will not shut.  There it offers the other two ways out,
     * and names the side once.  A plain point is not told at all: no train is sent there (OB-361).
     *
     * MUTATION: offer closing arrivals everywhere, or nowhere, or tell a plain point, and this fails.
     */
    @Test
    public void testClosingArrivalsIsOfferedOnlyWhereTheMenuCanDoIt() throws Exception
    {
        threeStationsInARow("RSA37-B1 offered");

        turnTrainsRoundAtBothEnds();

        TileKey beta = new TileKey("main", 3, 1);

        TileKey west = new TileKey("main", 2, 1);

        TileKey east = new TileKey("main", 4, 1);

        // ONE WAY TOWARDS BETA FROM BOTH SIDES: trapped from the east and the west, and closing both would leave no way in
        session.setDirection(west, new RouteId(0, 0), Direction.TOWARD_A);
        session.setDirection(east, new RouteId(0, 0), Direction.TOWARD_B);
        session.rebuild();

        org.traincontrol.automationui.AutonomyChecks.Finding both = trappedAt(beta);

        assertNotNull(both, "precondition: 3,1, one way towards it from both sides, is not reported trapped: "
            + session.check());

        assertTrue(both.getSubject().contains(org.traincontrol.util.I18n.t("autosetup.ui.sideE"))
            && both.getSubject().contains(org.traincontrol.util.I18n.t("autosetup.ui.sideW")), "precondition: 3,1 is not"
            + " trapped from both its sides: " + both.getSubject());

        assertEquals(both.getMessageKey(), org.traincontrol.automationui.AutonomyChecks.ARRIVAL_TRAPPED_NO_CLOSING, "a"
            + " station trapped from every way in is told to close arrivals from all of them, which the arrivals menu"
            + " refuses (RSA37-B1)");

        // ONE SIDE CLOSED: the other still trapped, and now the last way in
        session.setBarredArrivals(beta, new LinkedHashSet<>(Arrays.asList(Side.W)));
        session.rebuild();

        org.traincontrol.automationui.AutonomyChecks.Finding last = trappedAt(beta);

        assertNotNull(last, "precondition: closing the west left nothing trapped from the east: " + session.check());

        assertEquals(last.getSubject(), org.traincontrol.util.I18n.t("autosetup.ui.sideE"), "the side closed is still"
            + " named as trapped");

        assertEquals(last.getMessageKey(), org.traincontrol.automationui.AutonomyChecks.ARRIVAL_TRAPPED_NO_CLOSING, "a"
            + " station is told to close arrivals from its last open way in, which the arrivals menu refuses (RSA37-B1)");

        // AND THE WORDS: the side named once, as where the train comes from, and no more
        String side = org.traincontrol.util.I18n.t("autosetup.ui.sideE");

        String said = org.traincontrol.util.I18n.f(last.getMessageKey(), "3,1", last.getSubject());

        assertEquals(said.split(java.util.regex.Pattern.quote(side), -1).length - 1, 1, "the advice without closing"
            + " arrivals names the side other than as where the train comes from: " + said);

        // A PLAIN POINT, trapped from the west, where trains from 1,1 arrive: nothing is sent there, so nothing is said
        session.setBarredArrivals(beta, new LinkedHashSet<Side>());
        session.setDirection(west, new RouteId(0, 0), Direction.BOTH);
        session.setStation(beta, false);
        session.rebuild();

        assertTrue(hasACopyWithNoWayOut(beta), "precondition: the plain point 3,1 has no copy a train cannot leave");

        assertNull(trappedAt(beta), "a plain point is said to trap a train, and no train is sent there (OB-361): "
            + session.check());
    }

    /**
     * An arrival no train can make is not said to be trapped (OB-361; Adam: *"it is impossible for trains to arrive
     * from the west anyway, so both these conditions should disqualify the notice."*).  3,1 is a station with track
     * east of it running only towards it, so a train arriving from the west could not go on; then trains stop turning
     * round at 1,1, the end of the line west of it, and no train arrives at 3,1 from the west at all.
     *
     * MUTATION: say it for a copy no train reaches, and this fails.
     */
    @Test
    public void testAnArrivalNoTrainMakesIsNotTrapped() throws Exception
    {
        threeStationsInARow("OB-361 unreached");

        turnTrainsRoundAtBothEnds();

        TileKey beta = new TileKey("main", 3, 1);

        session.setDirection(new TileKey("main", 4, 1), new RouteId(0, 0), Direction.TOWARD_B);
        session.rebuild();

        assertNotNull(trappedAt(beta), "precondition: a train arriving at 3,1 from the west is not reported trapped: "
            + session.check());

        // AND NOW NO TRAIN ARRIVES FROM THE WEST: trains reaching 1,1, the end of the line, no longer turn round there
        session.setPointProperty(new TileKey("main", 1, 1), "mustReverse", null);
        session.rebuild();

        assertTrue(hasACopyWithNoWayOut(beta), "precondition: 3,1 no longer has a copy a train cannot leave, so nothing"
            + " below is about one no train reaches");

        assertNull(trappedAt(beta), "an arrival at 3,1 from the west, which no train can make, is said to be trapped"
            + " (OB-361): " + session.check());
    }

    /** Whether the built railway has a copy of this square that no edge leaves. */
    private boolean hasACopyWithNoWayOut(TileKey square) throws Exception
    {
        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        Set<String> leaving = new LinkedHashSet<>();

        org.json.JSONArray edges = built.getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++) leaving.add(edges.getJSONObject(i).getString("start"));

        org.json.JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.getJSONObject(i);

            if (square.toString().equals(point.optString("square")) && !leaving.contains(point.getString("name"))) return true;
        }

        return false;
    }

    /** The setup check's trapped-arrival finding at this square, whichever advice it gives, or null. */
    private org.traincontrol.automationui.AutonomyChecks.Finding trappedAt(TileKey at)
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.isArrivalTrapped(finding.getMessageKey())
                && at.equals(finding.getTile()))
            {
                return finding;
            }
        }

        return null;
    }

    /**
     * A station at the end of a line that trains may not turn at is an error - a train sent there could never leave
     * could never leave - and marking that trains must change direction there clears it.
     *
     * MUTATION: leave the error out, and this fails.
     */
    @Test
    public void testAStationAtTheEndOfALineIsAnError() throws Exception
    {
        threeStationsInARow("R34 end");

        TileKey alpha = new TileKey("main", 1, 1);

        session.setPointName(alpha, "Alpha");
        session.setPointName(new TileKey("main", 3, 1), "Beta");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");
        session.rebuild();

        assertTrue(findingAt(org.traincontrol.automationui.AutonomyChecks.STATION_AT_THE_END_OF_A_LINE,
            org.traincontrol.automationui.AutonomyChecks.Severity.ERROR, alpha), "a station at the end of a line, trains"
            + " not turning there, is not an error: " + session.check());

        assertFalse(findingAt(org.traincontrol.automationui.AutonomyChecks.STATION_AT_THE_END_OF_A_LINE,
            org.traincontrol.automationui.AutonomyChecks.Severity.ERROR, new TileKey("main", 3, 1)), "a through station"
            + " was reported at the end of a line");

        // ONE FINDING AND ONE PIECE OF ADVICE: not the error and the warning that trains cannot turn there
        assertNull(trappedAt(alpha), "a station at the end of a line is reported twice, an error and a warning, with"
            + " advice that disagrees: " + session.check());

        // NOR THE WARNING THAT IT REACHES NOTHING, which the error already says (MUTATION: drop the two keys from the
        // end-of-line removal in `AutonomySession.checkAfresh`)
        assertEquals(findingsAbout(org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING, alpha)
            + findingsAbout(org.traincontrol.automationui.AutonomyChecks.TERMINUS_STRANDED, alpha), 0,
            "a station at the end of a line is also said to reach nothing, which its error already says: " + session.check());

        // TRAINS MUST CHANGE DIRECTION THERE
        session.setPointProperty(alpha, "mustReverse", Boolean.TRUE);
        session.rebuild();

        assertFalse(findingAt(org.traincontrol.automationui.AutonomyChecks.STATION_AT_THE_END_OF_A_LINE,
            org.traincontrol.automationui.AutonomyChecks.Severity.ERROR, alpha), "a station at the end of a line trains"
            + " must turn at is still an error: " + session.check());
    }

    /** Trains must change direction at both ends of `threeStationsInARow`, so each sets off back along the line. */
    private void turnTrainsRoundAtBothEnds()
    {
        session.setPointProperty(new TileKey("main", 1, 1), "mustReverse", Boolean.TRUE);
        session.setPointProperty(new TileKey("main", 5, 1), "mustReverse", Boolean.TRUE);
        session.rebuild();
    }

    /** Whether the setup check has a finding of this kind and severity at this square. */
    private boolean findingAt(String messageKey, org.traincontrol.automationui.AutonomyChecks.Severity severity, TileKey at)
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (messageKey.equals(finding.getMessageKey()) && finding.getSeverity() == severity
                && at.equals(finding.getTile()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Every clashing sensor is reported, and a page that clashes still poisons nothing (MT-223).
     *
     * Adam: "all conflicting sensors should be listed."  A page that repeats anything contributes nothing to what
     * counts as seen, or a third page sharing one of its other sensors is blamed against the page that should itself
     * have been switched off.  The fixture does both: "repeat" clashes on TWO sensors and must produce two findings,
     * and "third" shares a sensor only "repeat" holds and must produce none.
     */
    @Test
    public void testEveryClashingSensorIsReportedAndOnlyTheRepeatingPageIs() throws Exception
    {
        LayoutDiagram first = new LayoutDiagram("main", 8, 4, null, null);

        first.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        first.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        first.setPageId("1");
        first.checkBounds();

        // a zoomed copy: it repeats BOTH of main's sensors, and carries one of its own
        LayoutDiagram repeat = new LayoutDiagram("repeat", 8, 4, null, null);

        repeat.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        repeat.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        repeat.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        repeat.setPageId("2");
        repeat.checkBounds();

        // and a page sharing only the sensor that "repeat" alone holds
        LayoutDiagram third = new LayoutDiagram("third", 8, 4, null, null);

        third.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        third.setPageId("3");
        third.checkBounds();

        session.open(Arrays.asList(first, repeat, third));
        session.initialize("Default");

        java.util.List<String> clashes = new java.util.ArrayList<>();

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.DUPLICATE_SENSOR_PAGE.equals(
                finding.getMessageKey()))
            {
                clashes.add(finding.getSubject() + " @ " + finding.getTile());
            }
        }

        assertEquals(clashes.size(), 2,
            "both of the repeated sensors have to be reported - a page that duplicates forty of them "
            + "is forty things to fix, and reporting one leaves the other thirty-nine invisible "
            + "(MT-223).  Found: " + clashes);

        for (String clash : clashes)
        {
            assertTrue(clash.contains("repeat"),
                "the finding must name and point at the REPEATING page, not the one that had the "
                + "sensor first: " + clash);
        }

        // and the page that only shares what "repeat" alone brought is innocent
        for (String clash : clashes)
        {
            assertFalse(clash.contains("third"),
                "a page sharing a sensor that only the REPEATING page holds was reported, so that "
                + "page is still feeding what it holds into the seen set (LE2-B8): " + clash);
        }
    }

    /**
     * The editor warns about an arrival a train could never leave, and one nothing could reach.
     *
     * Every other check asks about the SQUARE, but a square is emitted as one Point per side a train can arrive by, and
     * the copies do not share their edges: a square can be reachable while one of its arrivals is a trap.  The fixture
     * is a square with two arrivals, healthy from one side and stuck from the other (barring the far section leaves the
     * middle station's eastward arrival with nothing to go on to).
     *
     * Adam: "we need a warning for instances like the previous version of this."
     *
     * Which of TOWARD_A and TOWARD_B points which way is the tile's business, so both are applied and exactly one of
     * them must trap the arrival.  The last assertion asks `check()` about the current state, so the trapping direction
     * is put back before it.  The no-way-in half cannot be provoked by a diagram (a copy is emitted per side a train
     * can arrive by, so barring the approach leaves no copy at all): it is injected into the built graph below.
     *
     * MUTATION: asking `reducer.getPoints()` - the squares - rather than the built graph finds neither, because the
     * square is on a run that is perfectly connected.
     */
    @Test
    public void testTheEditorWarnsAboutACopyWithNoWayOutOrIn() throws Exception
    {
        LayoutDiagram page = deadEndRun();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Copies", null);
        session.getStore().setActiveConfiguration("Copies");

        TileKey near = new TileKey("main", 1, 1);
        TileKey middle = new TileKey("main", 4, 1);
        TileKey stub = new TileKey("main", 7, 1);

        session.setStation(near, true);
        session.setPointName(near, "NearEnd");

        session.setStation(middle, true);
        session.setPointName(middle, "Middle");

        session.setStation(stub, true);
        session.setPointName(stub, "FarEnd");

        // Both ends may turn: an unmarked dead end traps its arrivals, and the fixture would start with two.
        session.setPointFlag(near, AutonomyBuilder.CAN_REVERSE, true);
        session.setPointFlag(stub, AutonomyBuilder.CAN_REVERSE, true);

        session.rebuild();

        assertTrue(session.destinationCopiesWithNoWayOut().isEmpty(),
            "an ordinary two-way run reports a station a train cannot leave: "
            + session.destinationCopiesWithNoWayOut());

        assertTrue(session.destinationCopiesWithNoWayIn().isEmpty(),
            "an ordinary two-way run reports a station nothing can reach: "
            + session.destinationCopiesWithNoWayIn());

        // THE NO-WAY-IN HALF, BY INJECTION (FR-136).  No diagram provokes it, so the built graph the check reads is
        // handed one with every way into a copy of the middle station cut: the copy has to be named, and only that one.
        org.json.JSONObject injected = new org.json.JSONObject(session.buildConfigurationForInspection());

        String cutCopy = null;

        for (Object o : injected.getJSONArray("points"))
        {
            org.json.JSONObject point = (org.json.JSONObject) o;

            if (point.optBoolean("station", false) && point.getString("name").startsWith("Middle"))
            {
                cutCopy = point.getString("name");

                break;
            }
        }

        assertNotNull(cutCopy, "the middle station built to no copy, so there is none to cut the ways into");

        java.lang.reflect.Method noWayIn = AutonomySession.class.getDeclaredMethod("destinationCopiesWithNoWayIn",
            org.json.JSONObject.class, java.util.Map.class);

        noWayIn.setAccessible(true);

        assertTrue(((java.util.Map<?, ?>) noWayIn.invoke(session, injected, null)).isEmpty(),
            "control: the intact graph reports a copy nothing can reach");

        org.json.JSONArray kept = new org.json.JSONArray();

        int cut = 0;

        for (Object o : injected.getJSONArray("edges"))
        {
            org.json.JSONObject edge = (org.json.JSONObject) o;

            if (cutCopy.equals(edge.getString("end")))
            {
                cut++;

                continue;
            }

            kept.put(edge);
        }

        assertTrue(cut > 0, "control: nothing led into " + cutCopy + ", so there was no way in to cut");

        injected.put("edges", kept);

        java.util.Map<?, ?> unreachable = (java.util.Map<?, ?>) noWayIn.invoke(session, injected, null);

        assertEquals(unreachable.get(middle), cutCopy,
            "a copy of a healthy station with every way into it cut was not reported: " + unreachable);

        assertEquals(unreachable.size(), 1, "the copy nothing can reach was reported with others: " + unreachable);

        // ONE WAY ALONG THE FAR SECTION ONLY, so the middle station keeps its healthy arrival.
        Set<TileKey> far = new LinkedHashSet<>();

        for (int x = 5; x <= 7; x++)
        {
            far.add(new TileKey("main", x, 1));
        }

        session.setDirection(far, Direction.TOWARD_A);
        session.rebuild();

        String outA = String.valueOf(session.destinationCopiesWithNoWayOut());
        String inA = String.valueOf(session.destinationCopiesWithNoWayIn());

        session.setDirection(far, Direction.TOWARD_B);
        session.rebuild();

        String outB = String.valueOf(session.destinationCopiesWithNoWayOut());
        String inB = String.valueOf(session.destinationCopiesWithNoWayIn());

        assertTrue(outA.contains("Middle") || outB.contains("Middle"),
            "barring the far section one way leaves the middle station an arrival with no track out "
            + "of it, and nothing said so.  A train sent there can never leave, and autonomy may send "
            + "one.  Out: " + outA + " then " + outB);

        // AND THE FAR END IS NOT REPORTED: its only arrival is stuck, so it is a stuck SQUARE, which
        // checkStationUnreachable and checkStationReachesNothing already warn about.
        assertFalse(inA.contains("FarEnd") || inB.contains("FarEnd"),
            "a square whose every arrival is stuck was reported per-copy as well, which is the same "
            + "complaint the square-level checks already make.  In: " + inA + " then " + inB);

        assertTrue(outA.contains("Middle") != outB.contains("Middle"),
            "the same arrival was reported as trapped whichever way the far section was barred, so "
            + "the check is not reading the direction at all.  Out: " + outA + " then " + outB);

        // THE TRAPPING DIRECTION, PUT BACK FIRST: `subjectsOf` asks `check()` about the CURRENT state, which is
        // whatever the last `setDirection` set, and which of the two traps the arrival is the tile's business.
        session.setDirection(far, outA.contains("Middle") ? Direction.TOWARD_A : Direction.TOWARD_B);
        session.rebuild();

        assertFalse(subjectsOf(org.traincontrol.automationui.AutonomyChecks.COPY_NO_WAY_OUT).isEmpty(),
            "the session knows about the trapped arrival but the editor never reports it");
    }

    /**
     * And the one that has a way out, and still gets the train nowhere.
     *
     * Adam: "yes, extend the check to catch that shape too."  The square is healthy and one of its arrivals is not: a
     * run with a station at each end and a plain sensor beyond, where the sensor is a dead end nothing may turn at.
     * Arriving at the middle station from the west you can only carry on into that dead end; from the east you reach a
     * real station.  Where every arrival is stuck the square-level check has it, so the control is the same railway
     * with the dead end marked "trains may turn here", and then nothing is reported.
     *
     * MUTATION: counting any Point the train can get to, rather than only a STATION, makes this pass with the railway
     * unchanged.
     *
     * The walk also refuses to count a station on the SAME square (a trapped arrival's turning twin); that needs a
     * loop, which this straight run lacks, so that rule is defensive rather than demonstrated.
     */
    @Test
    public void testTheEditorWarnsAboutACopyThatReachesNoOtherStation() throws Exception
    {
        LayoutDiagram page = deadEndRun();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("Reaches", null);
        session.getStore().setActiveConfiguration("Reaches");

        TileKey near = new TileKey("main", 1, 1);
        TileKey middle = new TileKey("main", 4, 1);
        TileKey stub = new TileKey("main", 7, 1);

        session.setStation(near, true);
        session.setPointName(near, "NearEnd");

        session.setStation(middle, true);
        session.setPointName(middle, "Middle");

        // THE STUB IS DELIBERATELY NOT A STATION: the train gets there and still has nowhere it may stop, which is the
        // control for reporting on STATIONS rather than Points.
        session.setPointFlag(near, AutonomyBuilder.CAN_REVERSE, true);
        session.setPointFlag(stub, AutonomyBuilder.CAN_REVERSE, true);

        session.rebuild();

        assertTrue(session.destinationCopiesReachingNoStation().isEmpty(),
            "with the stub marked as somewhere trains may turn, every arrival gets somewhere - and "
            + "this reported one that does not: " + session.destinationCopiesReachingNoStation());

        // NOW TAKE THE TURN AWAY, which is the whole change.
        session.setPointFlag(stub, AutonomyBuilder.CAN_REVERSE, false);

        session.rebuild();

        java.util.Map<TileKey, String> stranded = session.destinationCopiesReachingNoStation();

        assertFalse(stranded.isEmpty(),
            "a station whose eastward arrival can only carry on into a dead end nothing may turn at "
            + "reaches no other station, and nothing said so.  This is the shape Adam asked for: a "
            + "way in, a way out, and nowhere to go.  Copies: " + stranded
            + "  no way out: " + session.destinationCopiesWithNoWayOut());

        assertTrue(String.valueOf(stranded).contains("Middle"),
            "the station that reaches nothing is Middle, and this named something else: " + stranded);

        // AND NOT THE STUB, which is not a station and is nobody's destination.
        assertFalse(String.valueOf(stranded).contains("7,1"),
            "plain track was reported as a station that reaches nothing: " + stranded);

        // AND THE SQUARE-LEVEL CHECK IS SILENT, which is the reason this one exists.  If it fired
        // here, this check would be a second way of saying something the editor already says.
        assertFalse(subjectsOf(
            org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING).contains("Middle"),
            "the square-level check already reports Middle, so a copy-level check adds nothing here "
            + "and the fixture is not the shape it was meant to be");

        assertFalse(subjectsOf(
            org.traincontrol.automationui.AutonomyChecks.COPY_REACHES_NOTHING).isEmpty(),
            "the session knows the arrival gets nowhere but the editor never reports it");
    }

    /**
     * A square trains turn round at, from which no station can be reached, is reported.
     *
     * Adam, closing OB-113: "We need to add a warning if a reversing point leads to nothing else."  A healthy
     * switchback never appears here, since the whole railway is reachable from it; what is left is a reversing square
     * in a pocket of track with no station, which `TERMINUS_STRANDED` and `STATION_REACHES_NOTHING` (stations only) do
     * not watch.  The second half gives the pocket a station, which stops the notice, so the check is not one that
     * lists every reversing point.
     *
     * MUTATION: dropping the `!reachesAStation` condition fails the second half; removing the call to
     * `checkReversingGoesSomewhere` fails the first.
     */
    @Test
    public void testAReversingPointThatLeadsNowhereIsReported() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        // A second run of track, not joined to the first.  The station is over on the original one.
        page.addComponent(componentType.FEEDBACK, 1, 3, 0, 0, 7, 13, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 3, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 3, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 3, 0, 0, 8, 14, accessoryDecoderType.MM2, null);

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey farEnd = new TileKey("main", 1, 3);
        TileKey reversing = new TileKey("main", 4, 3);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setPointName(farEnd, "Siding End");
        session.setPointName(reversing, "Turnback");

        // Reversing is a property of the ACTIVE configuration, and setPointProperty returns quietly
        // when there is none - so without these two lines the flag below does nothing at all.
        session.getStore().createConfiguration("Reversing", null);
        session.getStore().setActiveConfiguration("Reversing");

        session.setPointFlag(reversing, AutonomyBuilder.CAN_REVERSE, true);

        session.rebuild();

        // Precondition: a fixture that never set the flag looks like a check that did not fire.
        assertTrue(session.mayTurnTiles().contains(reversing)
            || session.mandatoryTurnTiles().contains(reversing),
            "precondition: the square is not actually one where trains change direction");

        if (!hasFinding(org.traincontrol.automationui.AutonomyChecks.REVERSING_LEADS_NOWHERE))
        {
            // Said with the whole list, because a check that did not fire and a check that fired about
            // some other square look identical from a boolean.
            StringBuilder saw = new StringBuilder();

            for (org.traincontrol.automationui.AutonomyChecks.Finding f : session.check())
            {
                saw.append("\n  ").append(f.getMessageKey()).append(" ").append(f.getSubject());
            }

            fail("a square trains turn round at, with no station reachable from it, was not reported. "
                + "Turning round somewhere a train can never get to a station from is the shape of "
                + "OB-113 - a route that does not exist and nothing on the diagram saying why. "
                + "Findings were:" + saw);
        }

        // The control: give that pocket of track a station, and the notice has to go.  This is the half
        // that separates the check from one that simply lists every reversing point on the railway.
        session.getStore().setStation(farEnd, true);

        session.rebuild();

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.REVERSING_LEADS_NOWHERE),
            "the notice stayed after a station appeared within reach of the reversing square, so it is "
            + "reporting reversing points rather than reversing points that lead nowhere - which is how "
            + "a findings list stops being read");
    }

    /**
     * A pocket whose only station is one autonomy may never choose still leads nowhere.
     *
     * Adam: "Make it a notice."  The runtime's `Layout.isSendableDestination` requires `!isReversing() &&
     * isAutoDestination()` before autonomy picks a destination, so a station that is only a parking berth read as
     * healthy in the editor and was useless in the run.  Only the two square-level clauses are checked: the other two
     * take a locomotive, and this notice is drawn on a square with no train in the question.  The fixture is the one
     * above with one flag moved.
     *
     * MUTATION: counting a station regardless of `isAutoDestination` fails the second half; counting nothing at all
     * fails the first.
     */
    @Test
    public void testAPocketWhoseOnlyStationIsAParkingBerthStillLeadsNowhere() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        page.addComponent(componentType.FEEDBACK, 1, 3, 0, 0, 7, 13, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 3, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 3, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 3, 0, 0, 8, 14, accessoryDecoderType.MM2, null);

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 1, 1);
        TileKey farEnd = new TileKey("main", 1, 3);
        TileKey reversing = new TileKey("main", 4, 3);

        session.getStore().setStation(station, true);
        session.setPointName(station, "Bahnhof");
        session.setPointName(farEnd, "Siding End");
        session.setPointName(reversing, "Turnback");

        session.getStore().createConfiguration("Reversing", null);
        session.getStore().setActiveConfiguration("Reversing");

        session.setPointFlag(reversing, AutonomyBuilder.CAN_REVERSE, true);

        // The pocket gets its station, which is what silences the notice above.
        session.getStore().setStation(farEnd, true);

        session.rebuild();

        assertTrue(session.mayTurnTiles().contains(reversing)
            || session.mandatoryTurnTiles().contains(reversing),
            "precondition: the square is not actually one where trains change direction");

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.REVERSING_LEADS_NOWHERE),
            "precondition: an ordinary station in the pocket must silence the notice, or the second "
            + "half below proves nothing");

        // AND NOW IT IS A BERTH AUTONOMY MAY NEVER CHOOSE.
        session.setAutoDestination(farEnd, false);

        session.rebuild();

        assertFalse(session.isAutoDestination(farEnd),
            "the fixture did not take: the station is still one autonomy may choose");

        assertTrue(hasFinding(org.traincontrol.automationui.AutonomyChecks.REVERSING_LEADS_NOWHERE),
            "the only station reachable from that reversing point is one autonomy will never choose, "
            + "so a train sent there turns round and is still nowhere - which is exactly what this "
            + "notice is for, and the editor counted it as somewhere to go (V36-C4)");

        // AND THE SEVERITY IS THE ONE HE ASKED FOR.
        for (org.traincontrol.automationui.AutonomyChecks.Finding f : session.check())
        {
            if (!org.traincontrol.automationui.AutonomyChecks.REVERSING_LEADS_NOWHERE
                .equals(f.getMessageKey())) continue;

            assertEquals(f.getSeverity(),
                org.traincontrol.automationui.AutonomyChecks.Severity.NOTICE,
                "Adam asked for a notice, and tightening the rule is only safe because it is one");
        }
    }
    /**
     * What a square is called, in the three cases there are.
     *
     * The rule lives in the session so the diagram's right-click menu (OB-112) and the editor ask the same one: what
     * somebody named it, then the sensor address printed beside it, then where it is.  Each case is its own assertion.
     *
     * MUTATION: dropping the feedback branch fails the second assertion; returning the coordinates always fails all
     * three.
     */
    @Test
    public void testASquareIsNamedByWhatIsKnownAboutIt() throws Exception
    {
        LayoutDiagram page = pageOnDisk();

        session.open(Arrays.asList(page));

        TileKey named = new TileKey("main", 1, 1);
        TileKey sensor = new TileKey("main", 4, 1);
        TileKey plain = new TileKey("main", 2, 1);

        session.setPointName(named, "  Bahnhof  ");

        assertEquals(session.describeTile(named), "Bahnhof",
            "a square somebody named is called that - trimmed, because the name is going into a menu "
            + "heading and the padding a text field leaves behind is not part of it");

        String sensorName = session.describeTile(sensor);

        assertTrue(sensorName.startsWith("s88 "),
            "an unnamed sensor square is known by the address printed on the diagram next to it, "
            + "which is the only thing about it the user can already see. Got: " + sensorName);

        assertEquals(session.describeTile(plain), "2,1",
            "a square with neither a name nor a sensor has only where it is");
    }

    /**
     * "May change direction here" is judged by where a train can GO, not by what arrives (OB-123).
     *
     * Adam: "this is wrong: the train can continue or reverse.  It should test for two outgoing paths, not two
     * incoming." A square a train can only enter from one side but leave by either offers exactly the choice the
     * setting describes; that shape needs a one-way run to build.  Both halves are in one test because `hasFinding`
     * asks by message key and not by square: the far end, also set reversing, is a genuine dead end and must still be
     * reported, as the control.
     *
     * MUTATION: putting `arrivalSides(tile).size() < 2` back reports the middle square and fails.
     */
    @Test
    public void testMayTurnIsJudgedByWhereATrainCanGo() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("OB123", null);
        session.getStore().setActiveConfiguration("OB123");

        TileKey middle = new TileKey("main", 3, 1);
        TileKey east = new TileKey("main", 5, 1);
        TileKey west = new TileKey("main", 1, 1);

        session.setPointName(middle, "LowerBack");
        session.setPointName(west, "Stub");

        session.setPointFlag(middle, AutonomyBuilder.CAN_REVERSE, true);
        session.setPointFlag(west, AutonomyBuilder.CAN_REVERSE, true);

        session.rebuild();

        // Precondition: with track running both ways the middle is not the case under test at all.
        assertEquals(session.arrivalSides(middle).size(), 2,
            "precondition: the middle square does not have two arrivals to begin with, so making it "
            + "one-way below proves nothing");

        // Trains may run only eastward over the second half.
        assertTrue(session.setOneWayRun(middle, east) > 0,
            "precondition: the run east could not be made one-way, so the shape this test is about "
            + "was never built");

        session.rebuild();

        assertEquals(session.arrivalSides(middle).size(), 1,
            "precondition: the middle square still has two arrival sides after the run was made "
            + "one-way, so this is not the one-in-two-out case Adam described");

        java.util.List<String> reported = subjectsOf(
            org.traincontrol.automationui.AutonomyChecks.MAY_TURN_ON_DEAD_END);

        // THE CASE. One way in, and still somewhere to carry on to.
        assertFalse(reported.contains("LowerBack"),
            "the square a train can enter from one side and leave by either was reported as a place "
            + "where \"every train turns round anyway\" - it can carry on east, which is the whole "
            + "of OB-123. Reported: " + reported);

        // THE CONTROL, which is what stops this passing by the check having been switched off.
        assertTrue(reported.contains("Stub"),
            "the end of the line - one way in, and the only way out is back the way you came - is no "
            + "longer reported, so the check has stopped saying anything rather than saying the right "
            + "thing. Reported: " + reported);
    }

    /**
     * Two ways in and one way out is still a real choice, for the train that came the other way.
     *
     * A square with two arrivals and ONE departure forces a turn on the train that arrived by the departure side and
     * offers a genuine choice to the other, so a departure count would call the setting pointless.
     *
     * MUTATION: `return departures.size() < 2` in place of the per-arrival answer fails this.
     */
    @Test
    public void testTwoWaysInAndOneOutIsStillAChoice() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("OB123b", null);
        session.getStore().setActiveConfiguration("OB123b");

        TileKey middle = new TileKey("main", 3, 1);
        TileKey west = new TileKey("main", 1, 1);

        session.setPointName(middle, "LowerBack");

        session.setPointFlag(middle, AutonomyBuilder.CAN_REVERSE, true);

        // Trains may run only EASTWARD over the western half, so the middle square can be arrived at
        // from the west but not departed to it.
        assertTrue(session.setOneWayRun(west, middle) > 0,
            "precondition: the western run could not be made one-way");

        session.rebuild();

        assertEquals(session.arrivalSides(middle).size(), 2,
            "precondition: the middle square no longer has two arrivals, so this is not the "
            + "two-in-one-out case this test is about");

        java.util.List<String> reported = subjectsOf(
            org.traincontrol.automationui.AutonomyChecks.MAY_TURN_ON_DEAD_END);

        assertFalse(reported.contains("LowerBack"),
            "a square with two ways in and one way out was called a place where every train turns "
            + "round anyway. The western half runs one way EASTWARD, so the train that arrived from "
            + "the west can carry on east; only the one that came from the east is forced to turn - "
            + "it cannot go back west. Reported: " + reported);
    }

    /**
     * The findings walk the railway a TRAIN can use, red arrows included (OB-120).
     *
     * Bars only ever REMOVE runs, so findings blind to them see more railway than a train can use.  Both halves are
     * asserted together: without the bar the station is fine, with it the station is stranded.
     *
     * MUTATION: let `AutonomyBuilder.arrivalAllowed` ignore the bars, and this fails: the findings follow the build.
     */
    @Test
    public void testTheFindingsObeyTheRedArrows() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("OB120", null);
        session.getStore().setActiveConfiguration("OB120");

        TileKey west = new TileKey("main", 1, 1);
        TileKey east = new TileKey("main", 5, 1);

        session.setStation(west, true);
        session.setPointName(west, "WestEnd");

        session.setStation(east, true);
        session.setPointName(east, "EastEnd");

        // Trains turn round at the west end, or a train standing there faces the buffers and is sent nowhere
        session.setPointFlag(west, AutonomyBuilder.CAN_REVERSE, true);

        session.rebuild();

        // A railway where the two stations can reach each other is the control: without it, a finding
        // that fires for some unrelated reason would look like the bar working.
        assertFalse(subjectsOf(org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING)
            .contains("WestEnd"),
            "precondition: the west end already reaches nothing before anything was barred, so this "
            + "fixture cannot show what barring does");

        // Now shut the only way into the east end.
        java.util.List<org.traincontrol.automationui.TilePorts.Side> ways =
            session.arrivalSides(east);

        assertFalse(ways.isEmpty(), "precondition: the east end has no arrival sides to bar");

        session.setBarredArrivals(east, new java.util.LinkedHashSet<>(ways));

        session.rebuild();

        java.util.List<String> stranded =
            subjectsOf(org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING);

        java.util.List<String> terminus =
            subjectsOf(org.traincontrol.automationui.AutonomyChecks.TERMINUS_STRANDED);

        assertTrue(stranded.contains("WestEnd") || terminus.contains("WestEnd"),
            "the west end can now reach no station a train may actually enter - every way into the "
            + "east end is barred - and nothing said so. The findings are walking runs the railway "
            + "refuses, which is the half of OB-120 that never reached its call sites. Reported: "
            + stranded + " / " + terminus);
    }

    /**
     * A compulsory turn that reaches nothing is named as a terminus.
     *
     * `AutonomyChecks.isTerminus` must not rely on the authored "terminus" key (setting a square's flags clears it) or
     * on "no reduced edge leaves the tile", which is false for every compulsory turn, whose turning copy departs.
     */
    @Test
    public void testAStrandedCompulsoryTurnIsNamedAsATerminus() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        session.getStore().createConfiguration("AMG", null);
        session.getStore().setActiveConfiguration("AMG");

        TileKey west = new TileKey("main", 1, 1);
        TileKey east = new TileKey("main", 5, 1);

        session.setStation(west, true);
        session.setPointName(west, "WestEnd");

        session.setStation(east, true);
        session.setPointName(east, "EastEnd");

        session.setPointFlag(west, AutonomyBuilder.MUST_REVERSE, true);

        session.setBarredArrivals(east, new java.util.LinkedHashSet<>(session.arrivalSides(east)));

        session.rebuild();

        assertTrue(session.mandatoryTurnTiles().contains(west), "precondition: the west end is not a compulsory turn");

        java.util.List<String> stranded =
            subjectsOf(org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING);

        java.util.List<String> terminus =
            subjectsOf(org.traincontrol.automationui.AutonomyChecks.TERMINUS_STRANDED);

        assertTrue(stranded.contains("WestEnd") || terminus.contains("WestEnd"),
            "precondition: the west end reaches nothing and neither finding names it: " + stranded + " / " + terminus);

        assertTrue(terminus.contains("WestEnd"),
            "the west end is a compulsory turn that reaches no station, and the check named it as a station that reaches"
            + " nothing rather than as a terminus a train would be stranded at (AMG-C1)");
    }

    /**
     * The findings ask the build where trains can get to, and work none of it out themselves.
     *
     * A walk over the reduction's edges has to re-apply the four authored sets by hand, and each re-application is a
     * second answer that can drift from the railway's (OB-120 gave a bar to one such walk and not its sibling).
     *
     * MUTATION: call `reducer.findPath` (or any reducer walk) from `AutonomyChecks`, and this fails.
     */
    @Test
    public void testTheFindingsAskTheBuildWhereTrainsCanGo() throws Exception
    {
        String checks = SourceOf.fileDeclaring("public class AutonomyChecks");

        assertFalse(checks.contains("findPath(") || checks.contains("reachableTiles("),
            "the findings walk the reduction again, re-applying the turn sets, the red arrows and the closures by hand,"
            + " instead of reading the reach the build gives them (BuiltReach)");

        assertTrue(checks.contains("checkReversingGoesSomewhere(reducer, mayTurn, mustTurn, reach,")
            && checks.contains("checkStations(reducer, termini, mustTurn, closed, reach)"),
            "the two reachability checks are no longer both given the build's reach");
    }

    /**
     * A station whose one copy cannot leave by a side the track goes reaches nothing, as the railway has it.
     *
     * Middle is reached from the east only, since the run west of it is one way, westward.  A train there faces west,
     * and west is a dead end; no copy of Middle faces east, so the track east of it, which runs both ways, is no way
     * out.  A walk starting facing every way would find EastEnd; the railway is right.
     *
     * MUTATION: start the square's walk from every way out of the square rather than from its copies, and this fails.
     */
    @Test
    public void testAStationWhoseOnlyCopyCannotLeaveThatWayReachesNothing() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));
        session.initialize("OneCopy");

        TileKey west = new TileKey("main", 1, 1);
        TileKey middle = new TileKey("main", 3, 1);
        TileKey east = new TileKey("main", 5, 1);

        session.setStation(middle, true);
        session.setPointName(middle, "Middle");
        session.setStation(east, true);
        session.setPointName(east, "EastEnd");
        session.setPointFlag(east, AutonomyBuilder.CAN_REVERSE, true);
        session.rebuild();

        assertFalse(strandedNames().contains("Middle"),
            "precondition: with track both ways Middle already reaches no station: " + strandedNames());

        assertTrue(session.setOneWayRun(middle, west) > 0, "precondition: the run west of Middle could not be made one-way");
        session.rebuild();

        assertEquals(session.arrivalSides(middle), Arrays.asList(Side.E),
            "precondition: Middle is not reached from the east only, so it is not the one-copy shape");

        assertTrue(strandedNames().contains("Middle"),
            "Middle's one copy faces west, into a dead end, and no train there can face east: the railway routes it"
                + " nowhere, and the editor did not say so: " + strandedNames());
    }

    /**
     * A copy trains may not stop at does not hide a station copy that reaches nothing.
     *
     * Middle refuses arrivals from the east.  The copy a train sent there stands on came in from the west and faces
     * east, into a dead end that is no station.  The barred copy faces west, to WestEnd: a square-level answer that
     * counted it would call Middle fine, and the copy-level finding asks only among the station's own copies.
     *
     * MUTATION: let `BuiltReach.bySquare` start from every copy of a station rather than from its station copies, and
     * this fails.
     */
    @Test
    public void testABarredCopyDoesNotHideAStrandedStation() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));
        session.initialize("Barred");

        TileKey west = new TileKey("main", 1, 1);
        TileKey middle = new TileKey("main", 3, 1);

        session.setStation(west, true);
        session.setPointName(west, "WestEnd");
        session.setPointFlag(west, AutonomyBuilder.CAN_REVERSE, true);
        session.setStation(middle, true);
        session.setPointName(middle, "Middle");
        session.rebuild();

        assertFalse(strandedNames().contains("Middle"),
            "precondition: unbarred, Middle already reaches no station: " + strandedNames());

        session.setBarredArrivals(middle, new LinkedHashSet<>(Arrays.asList(Side.E)));
        session.rebuild();

        assertEquals(session.getBarredArrivals(middle), new LinkedHashSet<>(Arrays.asList(Side.E)),
            "precondition: Middle does not refuse arrivals from the east");

        assertTrue(strandedNames().contains("Middle"),
            "a train sent to Middle arrives from the west and can only carry on into a dead end, and the editor said"
                + " nothing, because the copy trains may not stop at reaches WestEnd: " + strandedNames());
    }

    /** The stations said to reach nothing, either way of saying it. */
    private List<String> strandedNames()
    {
        List<String> out = new ArrayList<>(subjectsOf(org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING));

        out.addAll(subjectsOf(org.traincontrol.automationui.AutonomyChecks.TERMINUS_STRANDED));

        return out;
    }

    /**
     * A station set to No - Nothing Can Pass is said to be, once - not reported as unreachable (FR-101).
     *
     * Adam: *"these show 'no train can reach &lt;point&gt; from any other station....check the direction'.  Update the
     * error message to say that is marked for nothing to be able to pass, user to validate if intentional."*
     *
     * MUTATION: let the reachability sentences report a closed station again, or leave out the new one, and this fails.
     */
    @Test
    public void testAStationNothingCanPassIsSaidToBeClosed() throws Exception
    {
        session.open(Arrays.asList(platformWithTwoApproaches()));
        session.initialize("Closed");

        TileKey platform = new TileKey("main", 5, 1);
        TileKey other = new TileKey("main", 1, 1);

        session.setStation(platform, true);
        session.setStation(other, true);
        session.setStation(new TileKey("main", 7, 1), true);
        session.rebuild();

        String unreachable = org.traincontrol.automationui.AutonomyChecks.STATION_UNREACHABLE;
        String closed = "autosetup.ui.checkStationClosed";

        assertEquals(findingsAbout(unreachable, platform), 0, "precondition: in service, the platform is already"
            + " reported as one no train can reach");

        session.setPointProperty(platform, "active", Boolean.FALSE);
        session.rebuild();

        assertEquals(findingsAbout(closed, platform), 1, "a station set to No - Nothing Can Pass is not said to be -"
            + " Adam, FR-101: \"say that is marked for nothing to be able to pass, user to validate if intentional\"");

        assertEquals(findingsAbout(unreachable, platform)
            + findingsAbout(org.traincontrol.automationui.AutonomyChecks.STATION_REACHES_NOTHING, platform)
            + findingsAbout(org.traincontrol.automationui.AutonomyChecks.TERMINUS_STRANDED, platform), 0,
            "a station set to No - Nothing Can Pass is still reported as one no train can reach or leave, with advice"
            + " about the directions");
    }

    /**
     * A station every train must turn round at may have only one way in (MT-361).
     *
     * Adam, on MT-361: *"terminuses (stations that must reverse) are currently allowed to have ingress from two sides.
     * Make this be an autonomy ERROR that the user has to fix."*
     *
     * A square marked "must turn round" is emitted as one Point per arrival side, each leaving the way it came, so with
     * two ways in it becomes two termini and the track out of the other side is never used.
     *
     * An ERROR is asserted rather than assumed: `errorCount() > 0` refuses to start autonomy at all, and a warning
     * would let the configuration run.  The three remedies the message names all clear it: bar one way in, change the
     * square to "may turn round", or stop calling it a station.  The last matters: a must-turn square that is not a
     * station is a plain reversing point, and two ways into one is an ordinary mid-layout turn-round (the `BACK_mid` of
     * `core.testNonReversibleTrains`), which a rule that reported it would get wrong.
     *
     * MUTATIONS: dropping the `isStation` test reports the reversing point; dropping the barred-arrival test leaves the
     * first remedy unable to clear it; using `isTurnAround` instead of `mandatoryTurnTiles` reports the may-turn
     * square.
     */
    @Test
    public void testATerminusWithTwoWaysInIsAnError() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));
        session.initialize("Termini");

        TileKey middle = new TileKey("main", 3, 1);

        session.setStation(middle, true);
        session.setPointProperty(middle, "mustReverse", Boolean.TRUE);
        session.rebuild();

        assertTrue(session.isMustTurnAround(middle),
            "the fixture did not take: " + middle + " has to be a square every train must turn at");

        assertTrue(session.getStore().isStation(middle),
            "the fixture did not take: " + middle + " has to be a station");

        assertEquals(session.terminiWithTwoWaysIn().get(middle), Integer.valueOf(2),
            "the middle sensor of this page has track on both sides, so two sides reach it - and the"
            + " rule counted " + session.terminiWithTwoWaysIn().get(middle) + ".  Without two this test"
            + " is about a fixture that cannot show the fault: " + session.terminiWithTwoWaysIn());

        assertTrue(hasFinding(org.traincontrol.automationui.AutonomyChecks.TERMINUS_WITH_TWO_WAYS_IN),
            "a station where every train must turn round has track reaching it from both sides and"
            + " nothing objected. It is two termini, each sending its trains back the way they came,"
            + " and the track out of the far side is never used (Adam, MT-361)");

        // AN ERROR, NOT A WARNING: only an error refuses to start autonomy.
        assertEquals(severityOf(org.traincontrol.automationui.AutonomyChecks.TERMINUS_WITH_TWO_WAYS_IN),
            org.traincontrol.automationui.AutonomyChecks.Severity.ERROR,
            "the finding is not an ERROR, so autonomy still starts on a railway carrying it - which is"
            + " exactly what Adam asked to stop: \"make this be an autonomy ERROR that the user has to"
            + " fix\"");

        assertTrue(session.errorCount() > 0,
            "the finding is an error and `errorCount()` does not count it, so every door that asks that"
            + " question - the Start button, the refusal - would still offer to run this setup");

        // REMEDY ONE: bar one way in with the arrows.
        session.setBarredArrivals(middle,
            java.util.Collections.singleton(org.traincontrol.automationui.TilePorts.Side.W));
        session.rebuild();

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.TERMINUS_WITH_TWO_WAYS_IN),
            "one way in is barred, so this square has one way in - and the error the message tells the"
            + " operator to fix that way is still there, which would leave them nothing to do");

        // REMEDY TWO: let trains run through it instead.
        session.setBarredArrivals(middle, java.util.Collections.<org.traincontrol.automationui.TilePorts.Side>emptySet());
        session.setPointProperty(middle, "mustReverse", Boolean.FALSE);
        session.setPointProperty(middle, "canReverse", Boolean.TRUE);
        session.rebuild();

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.TERMINUS_WITH_TWO_WAYS_IN),
            "a station where trains MAY turn round is reported as a terminus with two ways in. That is"
            + " the ordinary station a train can either run through or reverse in, which is what \"may\""
            + " is for - and the rule has taken the operator's choice away");

        // REMEDY THREE, and the square this rule must leave alone: a must-turn square that is not a
        // station is a reversing point, and two ways into one of those is a mid-layout turn-round.
        session.setPointProperty(middle, "canReverse", Boolean.FALSE);
        session.setPointProperty(middle, "mustReverse", Boolean.TRUE);
        session.setStation(middle, false);
        session.rebuild();

        assertFalse(session.getStore().isStation(middle),
            "the fixture did not take: the square has to stop being a station for this claim");

        assertFalse(hasFinding(org.traincontrol.automationui.AutonomyChecks.TERMINUS_WITH_TWO_WAYS_IN),
            "a square that turns every train round and is NOT a station is reported as a terminus. It is"
            + " emitted as a plain reversing point, and a reversing point with track on both sides is an"
            + " ordinary mid-layout turn-round - `core.testNonReversibleTrains` is built on one");
    }

    /** The severity of the first finding carrying this key, or null. */
    private org.traincontrol.automationui.AutonomyChecks.Severity severityOf(String messageKey)
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getMessageKey().equals(messageKey)) return finding.getSeverity();
        }

        return null;
    }

    /**
     * A station shut from every direction is reported - as INFORMATION.
     *
     * The editor will not let anybody tick the last way in, so this is for the ways round it: a diagram edited after
     * the fact, or a file written by hand.  The consequence is quiet (the station stops being somewhere autonomy sends
     * a train), so it is said out loud.
     *
     * Adam (MT-078): "We should let the user know a train can't come in in any way (warning). If manual only, it's
     * info." A bar is advisory: autonomy will not route into a barred side but a person driving by hand may, so an
     * ERROR would block the whole setup over a station the operator can still use.  The case that IS a warning, nothing
     * can arrive by any means, is a square no track reaches: POINT_ISOLATED.
     */
    @Test
    public void testAStationWithEveryWayInBarredIsReported() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));

        TileKey station = new TileKey("main", 3, 1);

        session.setStation(station, true);
        session.setPointName(station, "Bahnhof");

        session.setBarredArrivals(station,
            new java.util.LinkedHashSet<>(session.arrivalSides(station)));

        assertTrue(session.shutStations().containsKey(station),
            "a station no train can reach is not being noticed");

        boolean reported = false;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (org.traincontrol.automationui.AutonomyChecks.NO_ARRIVALS_LEFT
                .equals(finding.getMessageKey()))
            {
                reported = true;

                assertEquals(finding.getSeverity(),
                    org.traincontrol.automationui.AutonomyChecks.Severity.INFO,
                    "a station with every way in barred is still reachable by hand, so reporting it "
                    + "as an ERROR stops the whole setup running over something the operator can "
                    + "still use (MT-078)");
            }
        }

        assertTrue(reported,
            "nothing told the user autonomy will no longer send a train to their station");
    }
    /**
     * And the CHECKS are actually given it, which is a separate question.
     *
     * Asked of the findings themselves, since a test of the rule alone could not fail: reverting the call site left it
     * green.  Adam: *"show how inactive points would affect the routes"* - close the middle of a run and the station
     * beyond it becomes unreachable.  (A barred side is passed THROUGH and merely not stopped at, OB-120.)
     *
     * MUTATION: hand `barredArrivals()` to `AutonomyChecks.run` again and the second assertion fails.
     */
    @Test
    public void testClosingASquareCutsOffWhatIsBeyondIt() throws Exception
    {
        session.open(Arrays.asList(deadEndRun()));

        session.getStore().createConfiguration("CutOff", null);
        session.getStore().setActiveConfiguration("CutOff");

        final TileKey near = new TileKey("main", 1, 1);
        final TileKey middle = new TileKey("main", 4, 1);
        final TileKey far = new TileKey("main", 7, 1);

        session.setStation(near, true);
        session.setStation(far, true);

        // Trains turn round at the near end, or a train standing there faces the buffers and is sent nowhere
        session.setPointFlag(near, AutonomyBuilder.CAN_REVERSE, true);

        session.rebuild();

        // THE CONTROL: with everything in service the far station is reachable, so the assertion
        // below cannot be satisfied by a railway that was broken to begin with.
        assertFalse(unreachable(session.check()).contains(far),
            "control: the far station is already unreachable with every square in service, so this "
            + "fixture cannot show what closing one does.  Findings: " + session.check());

        session.setPointProperty(middle, "active", Boolean.FALSE);

        session.rebuild();

        assertTrue(unreachable(session.check()).contains(far),
            "the middle of the run was switched out of service and the station beyond it is still "
            + "reported as reachable.  The runtime blocks every path through a closed square - "
            + "manual routes included - and the cross on the diagram says so, but the checks walked "
            + "straight through it (V31-C3).  Findings: " + session.check());

        // AND THE CLOSED SQUARE IS NOT A DESTINATION EITHER.  Adam: *"in manual mode, inactive endpoints and
        // intermediates should be refused as well.  Just not inactive start points.  Inactive really means nothing can
        // pass."*  The START is exempt by construction (the walk seeds `from` before the loop), so it is not tested
        // here.
        session.setStation(middle, true);

        session.rebuild();

        // SAID IN ITS OWN WORDS (FR-101): the closed station is named as closed rather than as unreachable, which it
        // also is.
        assertEquals(findingsAbout(org.traincontrol.automationui.AutonomyChecks.STATION_CLOSED, middle), 1,
            "a station switched out of service is still offered as somewhere to send a train.  "
            + "Inactive means nothing can pass - the runtime refuses it as a destination at every "
            + "door now, so offering it is a destination the railway will refuse.  Findings: "
            + session.check());
    }

    /** The tiles carrying an unreachable-station finding in these findings. */
    private static java.util.Set<TileKey> unreachable(
        java.util.List<org.traincontrol.automationui.AutonomyChecks.Finding> findings)
    {
        java.util.Set<TileKey> out = new java.util.LinkedHashSet<>();

        for (org.traincontrol.automationui.AutonomyChecks.Finding f : findings)
        {
            if (org.traincontrol.automationui.AutonomyChecks.STATION_UNREACHABLE.equals(f.getMessageKey()))
            {
                out.add(f.getTile());
            }
        }

        return out;
    }
}
