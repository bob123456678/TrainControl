package regression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * A station with one arrival side barred does not offer that side as somewhere to stop.
 *
 * MT-078, answered 2026-08-22: "Not always honored. In manual operation, I was able to send a train
 * from Tunnel to BottomMainA. BottomMainA had barred arrivals from the west."
 *
 * The rule lives in the BUILD, not in the search. A square that trains can reach from two sides is
 * emitted as two Points - that is how the model records which way a train is facing, since it knows
 * only which Point a locomotive stands on - and barring a side makes THAT copy a non-station. Every
 * destination list, manual and automatic alike, filters on `isDestination()`, so a copy that is not a
 * station cannot be the end of a path.
 *
 * This pins the build half, which is the half that decides. It is worth having either way: if the copy
 * is a destination, every list downstream is right to offer it and the defect is here; if it is not,
 * then a train that arrived at the station arrived at the OTHER copy, which is legal - and the two
 * copies share a base name, so a report naming the station cannot tell them apart.
 *
 * @author Adam
 */
public class testBarredArrivalIsNotADestination
{
    private File layout;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-barred").toFile();
        session = new AutonomySession(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /**
     * Bar one side; that copy stops being a place a train may be sent.
     */
    @Test
    public void testTheBarredCopyIsNotAStation() throws IOException
    {
        TileKey platform = twoEndedStation();

        java.util.List<TilePorts.Side> sides = session.arrivalSides(platform);

        assertEquals(sides.size(), 2,
            "the fixture must be reachable from two sides or this test proves nothing - got " + sides);

        TilePorts.Side barred = sides.get(0);

        session.setBarredArrivals(platform,
            new java.util.LinkedHashSet<>(java.util.Arrays.asList(barred)));

        org.json.JSONObject built =
            new org.json.JSONObject(session.buildConfigurationForInspection());

        int stops = 0, doesNot = 0;

        org.json.JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.getJSONObject(i);

            if (!platform.toString().equals(point.optString("block", null))
                && !String.valueOf(point.optString("name", "")).contains("Bahnsteig")) continue;

            if (point.optBoolean("station", false)) stops++;
            else doesNot++;
        }

        assertTrue(doesNot >= 1,
            "barring one arrival side left every copy of the platform still a station, so the "
            + "restriction changes nothing and a train can still be sent in from the barred side - "
            + "which is what MT-078 reports");

        assertTrue(stops >= 1,
            "barring ONE side left no copy able to stop, so the station has become unreachable "
            + "rather than restricted - the opposite failure, and the worse one");
    }

    /**
     * And un-barring it puts it back, so the setting is a switch rather than a one-way door.
     */
    @Test
    public void testUnbarringRestoresIt() throws IOException
    {
        TileKey platform = twoEndedStation();

        java.util.List<TilePorts.Side> sides = session.arrivalSides(platform);

        TilePorts.Side side = sides.get(0);

        session.setBarredArrivals(platform,
            new java.util.LinkedHashSet<>(java.util.Arrays.asList(side)));

        assertTrue(session.getBarredArrivals(platform).contains(side), "the bar did not take");

        session.setBarredArrivals(platform, new java.util.LinkedHashSet<TilePorts.Side>());

        assertFalse(session.getBarredArrivals(platform).contains(side),
            "un-ticking the side left it barred, so the restriction cannot be undone from the menu "
            + "that set it");
    }

    // ------------------------------------------------------------------------------------------

    /**
     * A platform in the middle of a run, so trains can reach it from either end.
     */
    private TileKey twoEndedStation() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey platform = new TileKey("main", 2, 1);

        session.setPointName(platform, "Bahnsteig");
        session.getStore().setStation(platform, true);

        return platform;
    }

    private LayoutDiagram runOfTrack() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 10, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 2, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        // A signal off to one side, to pair with the platform.  Wired as parsing a real layout does:
        // without an accessory it has no address to command and the pairing is dropped on the way to
        // the built configuration.
        page.addComponent(componentType.SIGNAL, 1, 2, 0, 0, 23, 0, accessoryDecoderType.MM2, null);

        page.getComponent(1, 2).setAccessory(new org.traincontrol.marklin.MarklinAccessory(
            null, 23, org.traincontrol.base.Accessory.accessoryType.SIGNAL,
            accessoryDecoderType.MM2, "Signal 23", false, 0));

        page.setPageId("1");

        return page;
    }

    /**
     * A barred copy still carries the protecting signal, because it is still the same platform.
     *
     * UR-6, from the uninformed review. The builder emits the pairing under
     * `protecting != null && !protecting.isEmpty() && stops`, two lines below a comment that says the
     * opposite: "The signals thrown to red while this platform is claimed. On every copy, because the
     * copies are one platform."
     *
     * `stops` is false exactly when this copy's arrival side is barred, so the copy trains may not be
     * SENT to is also the copy that does not hold its signal red. That matters because of what a bar
     * means - Adam, on MT-078: autonomy will not route into a barred side, and a person may. So a train
     * can be standing on that copy, and when it is, nothing protects the platform: `refreshOneSignal`
     * decides by asking every Point whose protecting signals contain the accessory, and this one is not
     * among them. The signal shows green over an occupied platform.
     *
     * Two copies of one square are two Points to the model and one piece of track on the railway. A
     * train standing on either is a train standing at the platform.
     *
     * Nothing refuses a protecting signal on a non-station: `setProtectingSignals` stores what it is
     * given and parseAuto does not check it against `station`. That was worth confirming before
     * removing the condition, because the `stops` variable exists for a case where the model DOES
     * refuse - a terminus that is not a destination - and answers a refusal by invalidating the whole
     * layout.
     */
    @Test
    public void testABarredCopyStillHoldsItsProtectingSignal() throws IOException
    {
        TileKey platform = guardedTwoEndedStation();

        java.util.List<TilePorts.Side> sides = session.arrivalSides(platform);

        assertEquals(sides.size(), 2, "the fixture must be reachable from two sides - got " + sides);

        session.setBarredArrivals(platform,
            new java.util.LinkedHashSet<>(java.util.Arrays.asList(sides.get(0))));

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfigurationForInspection());

        org.json.JSONArray points = built.getJSONArray("points");

        int copies = 0, guarded = 0;

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.getJSONObject(i);

            if (!platform.toString().equals(point.optString("block", null))) continue;

            copies++;

            if (point.has("protectingSignal")) guarded++;
        }

        assertEquals(copies, 2,
            "the platform did not come out as two copies, so there is no barred copy to test");

        assertEquals(guarded, copies,
            "the barred copy was emitted without its protecting signal. A train can still be standing "
            + "there - a bar stops autonomy routing in, not a person driving in - and that copy is not "
            + "among the Points the signal asks about, so the platform shows GREEN while it is "
            + "occupied (UR-6)");
    }

    /**
     * The same run of track, with a signal beside it paired to the platform.
     */
    private TileKey guardedTwoEndedStation() throws IOException
    {
        TileKey platform = twoEndedStation();

        TileKey signal = new TileKey("main", 1, 2);

        session.setProtectingSignals(platform, Arrays.asList(signal));

        assertFalse(session.protectingSignalNames().getOrDefault(platform,
            java.util.Collections.<String>emptyList()).isEmpty(),
            "the signal did not reach an accessory name, so nothing would be emitted whatever the "
            + "builder decided");

        return platform;
    }

    /**
     * One locomotive, one home - enforced where the home is SET, not only where it is loaded.
     *
     * TD-8, from the three-day history review. The running layout has enforced this since July:
     * `setHomeLocomotive` clears the same locomotive's home from every other Point as it assigns one.
     * The setup-side editor, which arrived in August, ends at `setPointProperty(tile, "home", picked)`
     * and nothing sweeps - so two squares could be given the same home from the menu, silently.
     *
     * What happens then is decided on the next load, in `rebuildHomeStations`: the second assignment it
     * meets is dropped with a log line, and which one loses depends on `points.values()` iteration
     * order. One of the two homes the operator set goes away, and the only notice is in the log.
     *
     * That method's comment said "only a hand-edited file reaches here". It was true when it was
     * written, on 2026-07-28; the home editor arrived on 2026-08-16, and a menu has reached it ever
     * since. The comment is corrected too, because a reader trusting it will not look for this.
     */
    @Test
    public void testALocomotiveHasOneHomeAcrossTheWholeSetup() throws Exception
    {
        session.open(java.util.Arrays.asList(threeSensors()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey first = new TileKey("main", 1, 1);
        TileKey second = new TileKey("main", 2, 1);

        session.setHome(first, "BR 218");

        assertEquals(session.homeElsewhere(second, "BR 218"), first,
            "the existing home was not found, so the warning at the menu would never fire");

        session.setHome(second, "BR 218");

        assertNull(session.getPointProperty(first, "home"),
            "the locomotive is now the home of TWO squares. On the next load one of them is dropped "
            + "by iteration order, with a log line as the only notice - so an assignment the operator "
            + "made disappears and nothing says which (TD-8)");

        assertEquals(session.getPointProperty(second, "home"), "BR 218",
            "the home did not arrive at the square it was moved to");

        assertNull(session.homeElsewhere(second, "BR 218"),
            "the square that now holds the home should not report itself as somewhere else");
    }

    /**
     * And setting a home somewhere else's locomotive does not disturb it.
     */
    @Test
    public void testMovingOneHomeLeavesAnotherLocomotivesAlone() throws Exception
    {
        session.open(java.util.Arrays.asList(threeSensors()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey first = new TileKey("main", 1, 1);
        TileKey second = new TileKey("main", 2, 1);
        TileKey third = new TileKey("main", 3, 1);

        session.setHome(first, "BR 218");
        session.setHome(second, "V 200");
        session.setHome(third, "BR 218");

        assertEquals(session.getPointProperty(second, "home"), "V 200",
            "moving one locomotive's home took another locomotive's with it");

        assertEquals(session.getPointProperty(third, "home"), "BR 218");

        assertNull(session.getPointProperty(first, "home"));
    }

    private LayoutDiagram threeSensors() throws java.io.IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 10, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 2, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /**
     * Barring every way in is INFORMATION, because a person can still drive a train there.
     *
     * MT-078. Adam's finding was that a manual dispatch reached a station whose arrivals were barred
     * from that side, and his ruling settles what the rule is: barred arrivals are advisory. Autonomy
     * will not route into a barred side; a person looking at the railway may. "We should let the user
     * know a train can't come in in any way (warning). If manual only, it's info."
     *
     * So the two severities belong to two different conditions, and both already existed - one of them
     * with the wrong one. A station with every side barred is reachable BY HAND, so blocking the whole
     * setup from starting over it was wrong: that is information. The case where nothing can arrive by
     * any means is a square no track reaches, which is POINT_ISOLATED, and that is a warning already.
     *
     * The contrived layout is built and the setting changed programmatically, which is what the entry
     * asked for.
     */
    @Test
    public void testAStationWithEveryArrivalBarredIsOnlyInformation() throws IOException
    {
        TileKey platform = twoEndedStation();

        java.util.List<TilePorts.Side> sides = session.arrivalSides(platform);

        assertEquals(sides.size(), 2, "the fixture must be reachable from two sides - got " + sides);

        session.setBarredArrivals(platform, new java.util.LinkedHashSet<>(sides));

        assertTrue(session.shutStations().containsKey(platform),
            "barring every side did not shut the station, so nothing below tests anything");

        AutonomyChecks.Finding shut = null;

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (AutonomyChecks.NO_ARRIVALS_LEFT.equals(finding.getMessageKey())) shut = finding;
        }

        assertNotNull(shut, "no finding was raised for a station nothing can be routed to");

        assertEquals(shut.getSeverity(), AutonomyChecks.Severity.INFO,
            "a station with every arrival barred is reported as " + shut.getSeverity() + ". It is "
            + "still reachable by hand - a bar stops autonomy routing in, not a person driving in - so "
            + "this must not block the setup from starting (MT-078)");
    }

    /**
     * Autonomy can be set up FROM AN IMPORT, with no setup here to begin with.
     *
     * FR-007: "it should be possible to initially load autonomy from an import, not just forcing the
     * creation of a new one."
     *
     * The import machinery was already there and already handled a store that had never been written
     * to - what was missing was the way in. The Autonomy menu offered "add a configuration" and nothing
     * else while `names.isEmpty()`, with Import sitting in the branch that only runs once a
     * configuration exists. So the first thing the menu asked of somebody who already HAD a setup -
     * from another machine, from somebody running the same layout, or from their own backup - was to
     * build a new one from scratch and find the import afterwards.
     *
     * This is the model half: a session on a folder that has never held a setup takes a bundle and
     * comes out with a working configuration. The menu half is one item, in AutonomyMenu.
     */
    @Test
    public void testASetupCanBeCreatedByImportingOne() throws Exception
    {
        // Somebody else's layout, exported
        java.io.File theirs = java.nio.file.Files.createTempDirectory("tc-fr007-from").toFile();

        AutonomySession exporter = new AutonomySession(theirs);

        exporter.open(java.util.Arrays.asList(threeSensors()));

        exporter.getStore().createConfiguration("Theirs", null);
        exporter.getStore().setActiveConfiguration("Theirs");
        exporter.setStation(new TileKey("main", 2, 1), true);
        exporter.setPointName(new TileKey("main", 2, 1), "Hauptbahnhof");

        org.json.JSONObject bundle = exporter.getStore().exportBundle("Theirs");

        assertNotNull(bundle, "nothing was exported, so nothing below tests anything");

        // A layout that has never had autonomy set up on it
        java.io.File mine = java.nio.file.Files.createTempDirectory("tc-fr007-to").toFile();

        AutonomySession fresh = new AutonomySession(mine);

        fresh.open(java.util.Arrays.asList(threeSensors()));

        assertFalse(fresh.exists(), "the fixture is not a fresh layout, so this proves nothing");

        assertTrue(fresh.getStore().getConfigurationNames().isEmpty(),
            "the fresh session already has a configuration");

        fresh.getStore().importBundle("Imported", bundle);
        fresh.save();

        assertEquals(fresh.getStore().getConfigurationNames(),
            java.util.Arrays.asList("Imported"),
            "importing into a layout with no setup did not produce one, so the only way in is still "
            + "to create a configuration first (FR-007)");

        assertEquals(fresh.getStore().getActiveConfiguration(), "Imported",
            "the imported configuration is not the active one, so nothing would run");

        assertTrue(fresh.getStore().isStation(new TileKey("main", 2, 1)),
            "the track decisions did not come across, so the configuration refers to points this "
            + "layout has never heard of");

        assertEquals(fresh.getStore().getPointName(new TileKey("main", 2, 1)), "Hauptbahnhof",
            "the station's name did not come across");

        // And it is on disk, so the next start finds it
        AutonomySession reopened = new AutonomySession(mine);

        reopened.open(java.util.Arrays.asList(threeSensors()));

        assertTrue(reopened.exists(), "the imported setup was not written, so it is gone on restart");

        assertEquals(reopened.getStore().getConfigurationNames(),
            java.util.Arrays.asList("Imported"));

        delete(theirs);
        delete(mine);
    }

    private void delete(File f)
    {
        if (f.isDirectory())
        {
            File[] kids = f.listFiles();

            if (kids != null) for (File kid : kids) delete(kid);
        }

        f.delete();
    }
}
