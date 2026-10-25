package core;

import support.TestStationAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.marklin.file.CS2File;

/**
 * A Central Station that is not there: a local HTTP server serving the same config files a real one would, so the
 * fetching half of a sync can be tested without hardware.
 *
 * Everything TrainControl knows about a Central Station arrives as an HTTP GET of a `.cs2` file.  This covers the seam
 * between that and the parsers: a file served over the wire produces exactly what the same file produces locally, and a
 * station that is missing, empty or broken is REPORTED rather than quietly producing an empty database ("the fetch
 * failed" and "the station has nothing" must never look the same, because the sync acts on what came off the wire).
 *
 * `syncWithCS2` never deletes a locomotive: it matches by UID, renames and re-addresses.  It deletes ROUTES: a fetched
 * route whose commands, s88 or trigger differ from the stored route of that id is deleted and re-added, and a fetched
 * name belonging to another id deletes the route that had it.  So the sharpest fault-injection test is the garbled
 * ROUTE file, and the locomotive one beside it is a ratchet against a wipe that does not exist yet.
 *
 * The port is chosen by the operating system, so two of these can run at once; CS2File takes its address as a string,
 * so a host:port pair goes in unchanged without touching production code.
 */
public class testMockCentralStation
{
    private static HttpServer server;

    /** One model for the whole class: init binds the Central Station's UDP port, so a second call in the JVM fails. */
    private static org.traincontrol.marklin.MarklinControlStation model;

    private static String address;

    /** How many times the mock was asked for anything, so a test can prove a fetch happened. */
    private static final AtomicInteger requests = new AtomicInteger();

    /** Set by a test to make the next fetch fail in a particular way. */
    private static volatile int forcedStatus = 200;

    private static volatile String forcedBody;

    /**
     * A path suffix to serve rubbish for, leaving every other file intact: a real fault is ONE config file arriving
     * broken while the rest are fine, and that is the shape under which the sync decides whether to delete anything.
     */
    private static volatile String garbledPath;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/", exchange -> serve(exchange));
        server.setExecutor(null);
        server.start();

        address = "127.0.0.1:" + server.getAddress().getPort();

        model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);
        model.stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (server != null) server.stop(0);
    }

    /** Serves the fixture that matches the path, as a real station would. */
    private static void serve(HttpExchange exchange) throws IOException
    {
        requests.incrementAndGet();

        if (forcedStatus != 200)
        {
            exchange.sendResponseHeaders(forcedStatus, -1);
            exchange.close();
            return;
        }

        byte[] body;

        String garbled = garbledPath;

        if (garbled != null && exchange.getRequestURI().getPath().endsWith(garbled))
        {
            body = "this file is not what it says it is\nnor is this line\n".getBytes("UTF-8");

            exchange.sendResponseHeaders(200, body.length);

            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }

            return;
        }

        if (forcedBody != null)
        {
            body = forcedBody.getBytes("UTF-8");
        }
        else
        {
            File file = fixtureFor(exchange.getRequestURI().getPath());

            if (file == null || !file.isFile())
            {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            body = Files.readAllBytes(file.toPath());
        }

        exchange.sendResponseHeaders(200, body.length);

        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(body);
        }
    }

    /** Which file on disk stands in for a given Central Station path. */
    private static File fixtureFor(String path)
    {
        if (path.endsWith("/lokomotive.cs2")) return new File("test/lokomotive.cs2");
        if (path.endsWith("/fahrstrassen.cs2")) return new File("test/fahrstrassen.cs2");
        if (path.endsWith("/magnetartikel.cs2")) return new File("test/magnetartikel.cs2");
        if (path.endsWith("/gleisbild.cs2")) return new File("test/test_layout/config/gleisbild.cs2");

        return null;
    }

    private static CS2File mockStation()
    {
        return new CS2File(address, null);
    }

    // ---------------------------------------------------------------- parity with a local read

    /**
     * A locomotive database fetched over the wire is the one the file describes: parity rather than a fixed
     * expectation, so fetching adds nothing and loses nothing.  Both sides share the parser, so this catches
     * transport-level loss (a truncated body, a mangled encoding, a dropped block).  Name, address and decoder type are
     * compared, since the address decides which engine a command reaches.
     */
    @Test
    public void testLocomotivesFetchedOverHttpMatchTheFile() throws Exception
    {
        int before = requests.get();

        List<MarklinLocomotive> overHttp = mockStation().parseLocomotives();

        assertTrue(requests.get() > before, "nothing was actually fetched");

        // UTF-8 explicitly, because that is what fetchURL uses; the platform default disagreed over an umlaut.
        List<MarklinLocomotive> fromDisk = mockStation().parseLocomotives(
            CS2File.parseFile(new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream("test/lokomotive.cs2"), java.nio.charset.StandardCharsets.UTF_8))));

        assertEquals(describe(overHttp), describe(fromDisk),
            "the same file served over HTTP produced different locomotives - name, address or "
            + "decoder type");

        assertFalse(overHttp.isEmpty(), "the fixture should contain locomotives, or this proves nothing");

        // And the addresses are not all the same value, or comparing them would prove nothing either
        java.util.Set<Integer> addresses = new java.util.LinkedHashSet<>();

        for (MarklinLocomotive l : overHttp) addresses.add(l.getAddress());

        assertTrue(addresses.size() > 1,
            "every fetched locomotive came back on the same address (" + addresses + "), so the "
            + "comparison above would agree with a fetch that read none of them");
    }

    /**
     * And the routes - against what the file actually holds.
     *
     * This used to assert only that the list was non-empty and free of duplicates, under a name that
     * said it matched the file.  A fetch that dropped or mangled half the routes passed.  It now reads
     * the same fixture from disk and compares the names, which is what the locomotive test beside it
     * does and what the name promised all along.
     */
    @Test
    public void testRoutesFetchedOverHttpMatchTheFile() throws Exception
    {
        List<MarklinRoute> overHttp = mockStation().parseRoutes();

        assertFalse(overHttp.isEmpty(), "the fixture should contain routes, or this proves nothing");

        List<String> got = new LinkedList<>();

        for (MarklinRoute r : overHttp) got.add(r.getName());

        assertEquals(got.size(), new java.util.LinkedHashSet<>(got).size(),
            "a fetched route list should not contain duplicates");

        // The same fixture read from disk, UTF-8 explicitly, the way the locomotive test above does
        List<MarklinRoute> fromDisk = mockStation().parseRoutes(
            CS2File.parseFile(new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream("test/fahrstrassen.cs2"),
                java.nio.charset.StandardCharsets.UTF_8))), mockStation().getMagList(false));

        List<String> expected = new LinkedList<>();

        for (MarklinRoute r : fromDisk) expected.add(r.getName());

        assertEquals(got, expected,
            "the routes fetched over HTTP are not the routes the file holds - a fetch that drops or "
            + "renames routes is a Central Station sync that quietly changes what the railway does");
    }

    /** And the accessories. */
    @Test
    public void testAccessoriesFetchedOverHttpParse() throws Exception
    {
        List<MarklinAccessory> accessories =
            mockStation().parseMags(CS2File.parseFile(CS2File.fetchURL(mockStation().getMagURL(false))));

        assertFalse(accessories.isEmpty(), "the fixture should contain accessories");
    }

    // ---------------------------------------------------------------- and when the station is not well

    /**
     * A station that answers 404 fails loudly rather than producing an empty database: a failed fetch and a station
     * with no locomotives must never look the same.
     */
    @Test
    public void testAMissingFileThrowsRatherThanReturningNothing() throws Exception
    {
        forcedStatus = 404;

        try
        {
            List<MarklinLocomotive> locomotives = mockStation().parseLocomotives();

            fail("a 404 produced a list of " + locomotives.size() + " instead of an error, so a sync "
                + "against a station that has lost its config file would empty the database");
        }
        catch (Exception expected)
        {
            // what we want: the caller cannot mistake this for an answer
        }
        finally
        {
            forcedStatus = 200;
        }
    }

    /** A station that answers with rubbish does not produce a half-built database. */
    @Test
    public void testAMalformedFileDoesNotProduceHalfADatabase() throws Exception
    {
        forcedBody = "this is not a cs2 file at all\nnor is this\n";

        try
        {
            List<MarklinLocomotive> locomotives = mockStation().parseLocomotives();

            assertTrue(locomotives.isEmpty(),
                "rubbish parsed into " + locomotives.size() + " locomotives, which would be merged "
                + "into the user's database as though the station had said so");
        }
        catch (Exception acceptable)
        {
            // Throwing is also a correct answer - what must not happen is a plausible-looking result
        }
        finally
        {
            forcedBody = null;
        }
    }

    /**
     * Nothing is listening on the port: the connection is refused, and that is reported, never an empty database.  A
     * closed loopback port answers RST at once, so the connect timeout is tested below with an address that goes quiet.
     */
    @Test
    public void testARefusedConnectionIsReportedRatherThanAnswered() throws Exception
    {
        // A port nothing is listening on.  Chosen by opening and closing a socket, so it is free and
        // this test does not guess.
        int dead;

        try (java.net.ServerSocket socket = new java.net.ServerSocket(0))
        {
            dead = socket.getLocalPort();
        }

        CS2File nothingThere = new CS2File("127.0.0.1:" + dead, null);

        try
        {
            List<MarklinLocomotive> locomotives = nothingThere.parseLocomotives();

            fail("a station that is not there answered with " + locomotives.size() + " locomotives.  "
                + "A sync would then replace the user's database with that");
        }
        catch (Exception expected)
        {
            // what we want: the caller cannot mistake this for an answer
        }
    }

    /**
     * A station that has been switched off is given up on rather than waited for: its SYNs go into a hole, and the
     * operating system would retry for its own idea of long enough (21 seconds measured) with the interface waiting.
     *
     * 192.0.2.1 is TEST-NET-1 (RFC 5737), routed nowhere, so it black-holes rather than refusing.  A machine whose
     * routing table rejects it outright would fail fast for the wrong reason, so that case SKIPS rather than passing.
     *
     * MUTATION: delete `connection.setConnectTimeout(CONNECT_TIMEOUT_MS)` from `CS2File.fetchURL`.
     */
    @Test
    public void testAStationThatNeverAnswersIsGivenUpOn() throws Exception
    {
        CS2File blackHole = new CS2File("192.0.2.1", null);

        long started = System.currentTimeMillis();

        try
        {
            blackHole.parseLocomotives();

            fail("an address that routes nowhere answered with a database");
        }
        catch (Exception expected)
        {
            long took = System.currentTimeMillis() - started;

            if (took < CS2File.CONNECT_TIMEOUT_MS)
            {
                throw new org.testng.SkipException("this machine rejected 192.0.2.1 in " + took
                    + "ms rather than letting the connection go quiet, so the connect timeout cannot "
                    + "be observed here.  The refused-connection test beside this one still holds");
            }

            // Four times the configured timeout, so a fetch with more than one attempt still fits and the operating
            // system's own 21-second retry does not
            assertTrue(took < CS2File.CONNECT_TIMEOUT_MS * 4,
                "a station that never answers took " + took + "ms to give up, where the configured "
                + "connect timeout is " + CS2File.CONNECT_TIMEOUT_MS + "ms.  Without that timeout the "
                + "wait is the operating system's, and the whole interface is held for the length of "
                + "it every time a switched-off station is synced");
        }
    }

    // ---------------------------------------------------------------- the whole sync, end to end

    /**
     * A sync that gives a member of a standing multi-unit the address of another standing train takes that train off
     * the graph, as the window's Change Name or Address does: the sync is the only door a Central Station multi-unit's
     * members come in by, and it swept nothing, so autonomy would run the second as a train of its own.  Here the
     * served file's BR 85 006 Gl. is at address 5, the database has it at 70 in a consist standing on one station, and
     * another train at 5 stands on the other.
     *
     * MUTATION: sweep nothing after the sync's locomotive pass, and this fails.
     */
    @Test
    public void testASyncThatGivesAMemberAStandingTrainsAddressTakesThatTrainOff() throws Exception
    {
        String was = TestStationAddress.get();

        final String served = "BR 85 006 Gl.";

        MarklinLocomotive member = model.getLocByName(served);

        final boolean made = member == null;
        final int addressWas = made ? -1 : member.getAddress();
        final MarklinLocomotive.decoderType typeWas = made ? null : member.getDecoderType();

        if (made) member = model.newMM2Locomotive(served, 70);
        else model.changeLocAddress(served, 70, MarklinLocomotive.decoderType.MM2);

        member = model.getLocByName(served);

        MarklinLocomotive head = model.newMM2Locomotive("SYNC head", 71);
        MarklinLocomotive other = model.newMM2Locomotive("SYNC other", 5);

        try
        {
            java.util.Map<String, Double> consist = new java.util.HashMap<>();

            consist.put(served, 1.0);

            assertEquals(head.setLinkedLocomotives(consist), 1, "precondition: the member could not be linked to the head");

            org.traincontrol.automation.Layout layout = model.getAutoLayout();

            org.traincontrol.marklin.MarklinFeedback first = model.newFeedback(8401, null);
            org.traincontrol.marklin.MarklinFeedback second = model.newFeedback(8402, null);

            model.setFeedbackState(first.getName(), false);
            model.setFeedbackState(second.getName(), false);

            layout.createPoint("SYNC A", true, first.getName());
            layout.createPoint("SYNC B", true, second.getName());
            layout.createEdge("SYNC A", "SYNC B");

            layout.getPoint("SYNC A").setLocomotive(head);
            layout.getPoint("SYNC B").setLocomotive(other);

            TestStationAddress.set(address);

            assertTrue(model.syncWithCS2() >= 0, "precondition: the sync against the mock station failed");

            assertEquals(model.getLocByName(served).getAddress(), 5, "precondition: the sync did not give " + served
                + " the address the station serves");

            assertNotNull(layout.getLocomotiveLocation(head), "the sync took the multi-unit off its own station");

            assertNull(layout.getLocomotiveLocation(other), "the sync gave a member of the multi-unit standing on SYNC A"
                + " the address of the train standing on SYNC B, and both still stand - autonomy would run that train as"
                + " its own while every command to the multi-unit moves it (RLA2-B2)");
        }
        finally
        {
            TestStationAddress.set(was);

            model.clearAutoLayout();

            try { model.deleteLoc("SYNC other"); } catch (Exception ignored) { }
            try { model.deleteLoc("SYNC head"); } catch (Exception ignored) { }

            if (made)
            {
                try { model.deleteLoc(served); } catch (Exception ignored) { }
            }
            else
            {
                try { model.changeLocAddress(served, addressWas, typeWas); } catch (Exception ignored) { }
            }
        }
    }

    /** The Central Station multi-unit `test/lokomotive.cs2` serves, with two members (OB-303). */
    private static final String SERVED_MULTI_UNIT = "OBB 1043";

    /** A served multi-unit standing on the graph, and a member of it its database copy does not have (OB-303). */
    private static final class StandingMultiUnit
    {
        org.traincontrol.automation.Layout layout;
        MarklinLocomotive multiUnit;
        MarklinLocomotive member;
    }

    /**
     * Stands the Central Station's OBB 1043 on one point and the member its database copy does not have on another
     * (OB-303).  A first sync brings OBB 1043 in with the two members `test/lokomotive.cs2` gives it, each made a
     * locomotive where this database has none; then the database's copy is cut back to the first member.
     *
     * @param made filled with the names of the locomotives this makes, for the caller to delete
     * @return the multi-unit, the member, and the railway they stand on
     */
    private static StandingMultiUnit aMultiUnitAndAMemberStanding(List<String> made) throws Exception
    {
        TestStationAddress.set(address);

        MarklinLocomotive served = null;

        for (MarklinLocomotive each : mockStation().parseLocomotives())
        {
            if (SERVED_MULTI_UNIT.equals(each.getName())) served = each;
        }

        assertNotNull(served, "precondition: the mock station no longer serves " + SERVED_MULTI_UNIT);

        assertTrue(model.syncWithCS2() >= 0, "precondition: the sync that brings " + SERVED_MULTI_UNIT + " in failed");

        StandingMultiUnit out = new StandingMultiUnit();

        out.multiUnit = model.getLocByName(SERVED_MULTI_UNIT);

        assertTrue(out.multiUnit != null && out.multiUnit.getDecoderType() == MarklinLocomotive.decoderType.MULTI_UNIT
            && java.util.Objects.equals(out.multiUnit.getUID(), served.getUID()), "precondition: this database does not"
            + " hold " + SERVED_MULTI_UNIT + " as the multi-unit the station serves, so the sync does not set its"
            + " members: " + out.multiUnit);

        java.util.Map<String, Double> members = out.multiUnit.getModelMultiUnitLocomotiveNames();

        assertTrue(members != null && members.size() == 2, "precondition: the served " + SERVED_MULTI_UNIT
            + " does not have two members: " + members);

        List<String> names = new java.util.ArrayList<>(new java.util.TreeSet<>(members.keySet()));

        int free = 72;

        for (String name : names)
        {
            if (model.getLocByName(name) == null)
            {
                assertNotNull(model.newMM2Locomotive(name, free++), "precondition: could not make the member " + name);

                made.add(name);
            }
        }

        // THE DATABASE'S COPY GIVEN THE FIRST MEMBER ONLY
        java.util.Map<String, Double> first = new java.util.HashMap<>();

        first.put(names.get(0), 1.0);

        out.multiUnit.setModelMultiUnitLocomotives(first);

        out.member = model.getLocByName(names.get(1));

        out.layout = model.getAutoLayout();

        org.traincontrol.marklin.MarklinFeedback one = model.newFeedback(8403, null);
        org.traincontrol.marklin.MarklinFeedback two = model.newFeedback(8404, null);

        model.setFeedbackState(one.getName(), false);
        model.setFeedbackState(two.getName(), false);

        out.layout.createPoint("OB303 A", true, one.getName());
        out.layout.createPoint("OB303 B", true, two.getName());
        out.layout.createEdge("OB303 A", "OB303 B");

        out.layout.getPoint("OB303 A").setLocomotive(out.multiUnit);
        out.layout.getPoint("OB303 B").setLocomotive(out.member);

        assertTrue(out.multiUnit.isSimultaneousMultiUnitCompatible(out.member)
            && out.member.isSimultaneousMultiUnitCompatible(out.multiUnit), "precondition: with one member in its"
            + " copy, " + SERVED_MULTI_UNIT + " already conflicts with " + out.member.getName() + ", so a sweep would"
            + " take it off whatever the sync changed");

        return out;
    }

    /**
     * A sync that gives a standing Central Station multi-unit a member standing on the graph takes that member off, and
     * redraws the railway (OB-303): the window's own doors call the layout's refresh after the same sweep, and the
     * autonomy panels went on drawing the member standing.
     *
     * MUTATION: sweep nothing after the sync's member pass, or redraw nothing after it, and this fails.
     */
    @Test
    public void testASyncThatAddsAStandingMemberTakesItOffAndRedraws() throws Exception
    {
        String was = TestStationAddress.get();

        List<String> made = new LinkedList<>();

        try
        {
            StandingMultiUnit standing = aMultiUnitAndAMemberStanding(made);

            final AtomicInteger redrawn = new AtomicInteger();

            standing.layout.setCallback("OB-303 probe", (edges, loc, flag) ->
            {
                redrawn.incrementAndGet();

                return null;
            });

            assertTrue(model.syncWithCS2() >= 0, "precondition: the sync against the mock station failed");

            assertNotNull(standing.layout.getLocomotiveLocation(standing.multiUnit), "the sync took the multi-unit off"
                + " its own station");

            assertNull(standing.layout.getLocomotiveLocation(standing.member), "the sync gave the standing "
                + SERVED_MULTI_UNIT + " the member standing beside it, and both still stand - autonomy would run that"
                + " member as a train of its own while every command to the multi-unit moves it (RLA2-B2, OB-303)");

            assertTrue(redrawn.get() > 0, "the sync took " + standing.member.getName() + " off the graph and redrew"
                + " nothing, so the autonomy panels go on drawing it standing (OB-303)");
        }
        finally
        {
            TestStationAddress.set(was);

            model.clearAutoLayout();

            for (String name : made)
            {
                try { model.deleteLoc(name); } catch (Exception ignored) { }
            }
        }
    }

    /**
     * A member a Central Station multi-unit is given while autonomy runs is taken off by the first sync after the run,
     * not left standing until the next load (OB-303).  The sweep is not made while autonomy runs, so the next sync saw
     * no change; here the run is the railway's own flag, set by reflection.
     *
     * MUTATION: forget what a sync held back while autonomy ran, and this fails.
     */
    @Test
    public void testAMemberAddedDuringARunIsTakenOffByTheNextSync() throws Exception
    {
        String was = TestStationAddress.get();

        List<String> made = new LinkedList<>();

        java.lang.reflect.Field running = org.traincontrol.automation.Layout.class.getDeclaredField("running");

        running.setAccessible(true);

        StandingMultiUnit standing = null;

        try
        {
            standing = aMultiUnitAndAMemberStanding(made);

            running.set(standing.layout, true);

            assertTrue(model.isAutonomyRunning(), "precondition: autonomy does not read as running");

            assertTrue(model.syncWithCS2() >= 0, "precondition: the sync while autonomy ran failed");

            assertEquals(standing.multiUnit.getModelMultiUnitLocomotiveNames().size(), 2, "precondition: the sync"
                + " while autonomy ran did not take the new member in");

            assertNotNull(standing.layout.getLocomotiveLocation(standing.member), "a sync while autonomy runs took a"
                + " train off the graph, which the window's own doors do not do mid-run (RLA2-B2)");

            running.set(standing.layout, false);

            assertFalse(model.isAutonomyRunning(), "precondition: autonomy still reads as running");

            assertTrue(model.syncWithCS2() >= 0, "precondition: the sync after the run failed");

            assertNull(standing.layout.getLocomotiveLocation(standing.member), "the member " + SERVED_MULTI_UNIT
                + " was given while autonomy ran still stands after the first sync with the run stopped - nothing"
                + " remembered that its sweep was held back, so it stands until the next load or Place (OB-303)");
        }
        finally
        {
            if (standing != null) running.set(standing.layout, false);

            TestStationAddress.set(was);

            model.clearAutoLayout();

            for (String name : made)
            {
                try { model.deleteLoc(name); } catch (Exception ignored) { }
            }
        }
    }

    /**
     * A full sync against the mock station, reconciliation and all.  Modest on purpose: a sync against a station that
     * IS there succeeds and brings its locomotives in; the value is that the path executes at all.  Nothing is saved:
     * the databases are mutated in memory, so the operator's LocDB.data on disk is not touched.
     */
    @Test
    public void testAFullSyncAgainstTheMockStationSucceeds() throws Exception
    {
        String was = TestStationAddress.get();

        // `requests` is class-static and never reset, so a bare `> 0` after the sync was true before it ran; the delta
        // is the fact
        int before = requests.get();

        TestStationAddress.set(address);

        try
        {
            int result = model.syncWithCS2();

            assertTrue(result >= 0,
                "a sync against a station that is answering should not report failure, got " + result);

            assertFalse(model.getLocList().isEmpty(),
                "the sync brought in no locomotives at all, though the station served a full file");

            assertTrue(requests.get() > before,
                "the sync never asked the station for anything - it went " + before + " requests in "
                + "and came out at " + requests.get() + ", so whatever it did it did from memory");
        }
        finally
        {
            TestStationAddress.set(was);
        }
    }

    /**
     * A route file that arrives as rubbish leaves the routes alone: the database the sync really does delete from.  A
     * fetched route that differs from the stored one of that id is deleted and re-added, and a fetched name that
     * belongs to a different id deletes the route that had it, so a fetch that came back as nonsense could decide
     * anything.
     *
     * Only the route file is garbled: a station where every file is broken at once is not a real fault and would not
     * reach the route-deletion code.
     *
     * MUTATION: make the route half of `syncWithCS2` act on an empty fetch as though the station had said the routes
     * were gone - `for (MarklinRoute r : this.getRoutes()) if (!named(parsedRoutes, r.getName()))
     * this.deleteRoute(r.getName());` after the parse.
     */
    @Test
    public void testAGarbledRouteFileDoesNotDeleteTheRoutes() throws Exception
    {
        String was = TestStationAddress.get();

        List<String> mine = java.util.Arrays.asList(
            "TA-B5 route one", "TA-B5 route two", "TA-B5 route three");

        for (String name : mine)
        {
            List<org.traincontrol.base.RouteCommand> commands = new LinkedList<>();

            commands.add(org.traincontrol.base.RouteCommand.RouteCommandAccessory(70,
                org.traincontrol.base.Accessory.accessoryDecoderType.MM2, true));

            assertTrue(model.newRoute(name, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
                false, null), "the fixture could not add the route \"" + name + "\"");
        }

        try
        {
            int before = model.getRouteList().size();

            // Assert the variable, not the control: with no routes in the database, "they are all
            // still there" afterwards is true of nothing
            assertTrue(before >= mine.size(),
                "the database holds " + before + " routes, so there is nothing here that a deletion "
                + "could take away");

            garbledPath = "/fahrstrassen.cs2";

            TestStationAddress.set(address);

            model.syncWithCS2();

            for (String name : mine)
            {
                assertNotNull(model.getRoute(name),
                    "the route \"" + name + "\" was deleted by a sync whose route file came back as "
                    + "rubbish.  A route is what the railway DOES - the operator finds out when it "
                    + "fires and no point moves");
            }

            assertTrue(model.getRouteList().size() >= before,
                "a sync whose route file came back as rubbish deleted routes: " + before
                + " before, " + model.getRouteList().size() + " after");
        }
        finally
        {
            garbledPath = null;

            TestStationAddress.set(was);

            for (String name : mine)
            {
                if (model.getRoute(name) != null) model.deleteRoute(name);
            }
        }
    }

    /**
     * A sync against a station that is not there leaves the locomotive database alone.  A ratchet: `syncWithCS2` has no
     * path that deletes a locomotive, but the day it learns to remove one the station no longer lists, a failed fetch
     * will look like a station that lists nothing, and this fails.  The routes are tested above.
     */
    @Test
    public void testASyncAgainstNothingDoesNotEmptyTheDatabase() throws Exception
    {
        int before = model.getLocList().size();

        // Assert the variable, not the control: on an empty database "the count did not change" is 0 == 0
        assertTrue(before > 0,
            "the locomotive database is empty before the sync, so nothing below could detect a wipe");

        int dead;

        try (java.net.ServerSocket socket = new java.net.ServerSocket(0))
        {
            dead = socket.getLocalPort();
        }

        String was = TestStationAddress.get();

        TestStationAddress.set("127.0.0.1:" + dead);

        try
        {
            model.syncWithCS2();

            assertEquals(model.getLocList().size(), before,
                "a sync against a station that is not there changed the locomotive database - it had "
                + before + " and now has " + model.getLocList().size());
        }
        finally
        {
            TestStationAddress.set(was);
        }
    }

    /**
     * Each locomotive as name, address and decoder type: the three fields a command needs to reach the right engine.
     */
    private static List<String> describe(List<MarklinLocomotive> locomotives)
    {
        List<String> out = new LinkedList<>();

        for (MarklinLocomotive l : locomotives)
        {
            out.add(l.getName() + " | " + l.getAddress() + " | " + l.getDecoderType());
        }

        java.util.Collections.sort(out);

        return out;
    }
}
