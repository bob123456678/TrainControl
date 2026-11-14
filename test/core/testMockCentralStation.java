package core;

import support.Reflect;
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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.marklin.file.CS2File;

/**
 * A Central Station that is not there: a local HTTP server serving the same config files a real one would, so the
 * fetching half of a sync can be tested without hardware.
 *
 * Covers the seam between the HTTP GET of a `.cs2` file and the parsers: a file served over the wire produces what the
 * same file produces locally, and a missing, empty or broken station is REPORTED, never a quietly empty database.
 *
 * `syncWithCS2` never deletes a locomotive but it deletes ROUTES, so the sharpest fault injection is the garbled ROUTE
 * file; the locomotive one is a ratchet against a wipe that does not exist yet.  The port is chosen by the operating
 * system, so two of these can run at once.
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

    /** A path suffix to serve rubbish for, leaving every other file intact: a real fault is ONE config file broken. */
    private static volatile String garbledPath;

    /** A path suffix whose next fetch arrives broken: cut short with its length promised, or in the wrong character set. */
    private static volatile String damagedPath;

    private static volatile boolean cutShort;

    /** With cutShort: the body is sent whole but promises more, the same every time, as a station's wrong length. */
    private static volatile boolean wrongLength;

    /** With cutShort: the headers promise the body and none of it is sent, the same every time. */
    private static volatile boolean headersOnly;

    /** When each broken fetch of the damaged path was served, in nanoseconds. */
    private static final List<Long> damagedFetchTimes = java.util.Collections.synchronizedList(new LinkedList<>());

    /** How many more fetches of the damaged path arrive broken; the rest arrive whole. */
    private static final AtomicInteger damagedFetchesLeft = new AtomicInteger(Integer.MAX_VALUE);

    /** Each cut-off stops at a different length, as a dropped connection does. */
    private static final AtomicInteger cutsServed = new AtomicInteger();

    /** Serves accessory address 1 as a DCC article, so a tile built without the list (as MM2) shows. */
    private static volatile boolean dccAtOne;

    private static final String ADDRESS_ONE = " .id=1\n .name=1\n .typ=linksweiche\n .stellung=1\n .schaltzeit=200\n"
        + " .decoder=mehr_alt\n .dectyp=mm2";

    /** How many layout pages the mock was asked for. */
    private static final AtomicInteger pageRequests = new AtomicInteger();

    /** Syncs read the diagrams from a copy of the fixture, as from a user's own folder, unless a test points them here. */
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

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

        if (sandbox != null) sandbox.close();
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
        else if (exchange.getRequestURI().getPath().endsWith("/geraet.vrs"))
        {
            body = "[geraet]\ngeraet\n .produkt=Central Station 2\n".getBytes("UTF-8");
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

        if (dccAtOne && exchange.getRequestURI().getPath().endsWith("/magnetartikel.cs2"))
        {
            String text = new String(body, "UTF-8").replace("\r", "");

            if (!text.contains(ADDRESS_ONE)) throw new IOException("the fixture's record for address 1 has changed");

            body = text.replace(ADDRESS_ONE, ADDRESS_ONE.replace("dectyp=mm2", "dectyp=dcc")).getBytes("UTF-8");
        }

        String damaged = damagedPath;

        if (damaged != null && exchange.getRequestURI().getPath().endsWith(damaged)
            && damagedFetchesLeft.getAndDecrement() > 0)
        {
            damagedFetchTimes.add(System.nanoTime());

            if (cutShort)
            {
                exchange.sendResponseHeaders(200, wrongLength ? body.length + 7 : body.length);

                // Not closed: the exception out of this handler makes the server drop the connection with the rest owed.
                OutputStream out = exchange.getResponseBody();

                out.write(body, 0, headersOnly ? 0 : wrongLength ? body.length
                    : body.length / (2 + cutsServed.getAndIncrement() % 4));
                out.flush();

                throw new IOException("the mock station drops the connection half way");
            }

            body = new String(body, "UTF-8").getBytes("ISO-8859-1");
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

        int pages = path.indexOf("/gleisbilder/");

        if (pages >= 0)
        {
            pageRequests.incrementAndGet();

            return new File("test/test_layout/config/gleisbilder", path.substring(pages + "/gleisbilder/".length()));
        }

        return null;
    }

    private static CS2File mockStation()
    {
        return new CS2File(address, null);
    }

    // ---------------------------------------------------------------- parity with a local read

    /**
     * A locomotive database fetched over the wire is the one the file describes: parity rather than a fixed
     * expectation.  Both sides share the parser, so a transport that loses or reorders a record shows; a body cut
     * short or in the wrong encoding is the damaged-file claims below.  Name, address and decoder type are compared,
     * since the address decides which engine a command reaches.
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
     * A switched-off station is given up on, not waited for (the OS would retry SYNs for 21 seconds).  192.0.2.1
     * (RFC 5737) black-holes; a machine whose routing table rejects it fails fast for the wrong reason, so that SKIPS.
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
     * the graph, as the Locomotive Settings window's Save does (OB-303).  Served BR 85 006 Gl. is at address 5; the
     * database has it at 70 in a consist on one station, and another train at 5 stands on the other.
     *
     * MUTATION: sweep nothing after the sync's locomotive pass, and this fails.
     */
    @Test
    public void testASyncThatGivesAMemberAStandingTrainsAddressTakesThatTrainOff() throws Exception
    {
        String was = TestStationAddress.get();

        final String served = SERVED;

        final Runnable putBack = theServedLocomotiveAt70();

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

            putBack.run();
        }
    }

    /**
     * A sync does not re-address a locomotive that has a speed, as Locomotive Settings will not: the train would run on
     * at the old address while Stop here commands the new one.  Once it stands, the next sync takes the address.
     * Served BR 85 006 Gl. is at address 5; the database has it at 70.
     *
     * MUTATION: drop the `LocomotiveSettings.hasSpeed` clause from `adoptCentralStationAddress`'s deferral.
     */
    @Test
    public void testASyncDoesNotReAddressALocomotiveWithASpeed() throws Exception
    {
        String was = TestStationAddress.get();

        final String served = SERVED;

        final Runnable putBack = theServedLocomotiveAt70();

        try
        {
            TestStationAddress.set(address);

            model.getLocByName(served).setSpeed(40);

            assertTrue(model.syncWithCS2() >= 0, "precondition: the sync against the mock station failed");

            assertEquals(model.getLocByName(served).getAddress(), 70, "a sync moved a locomotive with a speed to the"
                + " address the station serves: the train runs on at the old address, and Stop commands the new one");

            model.getLocByName(served).setSpeed(0);

            assertTrue(model.syncWithCS2() >= 0, "precondition: the second sync against the mock station failed");

            assertEquals(model.getLocByName(served).getAddress(), 5, "the sync after the train stopped did not take the"
                + " address the station serves");
        }
        finally
        {
            TestStationAddress.set(was);

            try { model.getLocByName(served).setSpeed(0); } catch (Exception ignored) { }

            putBack.run();
        }
    }

    /** A locomotive `test/lokomotive.cs2` serves at address 5. */
    private static final String SERVED = "BR 85 006 Gl.";

    /** Puts {@link #SERVED} at address 70, made if missing, and gives back what puts it as it was. */
    private static Runnable theServedLocomotiveAt70() throws Exception
    {
        MarklinLocomotive loc = model.getLocByName(SERVED);

        final boolean made = loc == null;
        final int addressWas = made ? -1 : loc.getAddress();
        final MarklinLocomotive.decoderType typeWas = made ? null : loc.getDecoderType();

        if (made) model.newMM2Locomotive(SERVED, 70);
        else model.changeLocAddress(SERVED, 70, MarklinLocomotive.decoderType.MM2);

        return () ->
        {
            try
            {
                if (made) model.deleteLoc(SERVED);
                else model.changeLocAddress(SERVED, addressWas, typeWas);
            }
            catch (Exception ignored) { }
        };
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
     * Stands the Central Station's OBB 1043 on one point and the member its database copy lacks on another (OB-303).
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
     * redraws the railway (OB-303): the window's own doors refresh the layout after the same sweep.
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
     * not left standing until the next load (OB-303).  Here the run is the railway's own flag, set by reflection.
     *
     * MUTATION: forget what a sync held back while autonomy ran, and this fails.
     */
    @Test
    public void testAMemberAddedDuringARunIsTakenOffByTheNextSync() throws Exception
    {
        String was = TestStationAddress.get();

        List<String> made = new LinkedList<>();

        java.lang.reflect.Field running = Reflect.field(org.traincontrol.automation.Layout.class, "running");

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
     * A full sync against the mock station: the locomotives it lists, taken out of the database first, are all back
     * afterwards, as the file describes them.  Nothing is saved: the databases are mutated in memory.
     *
     * MUTATION: make `syncWithCS2` skip `newLocomotive(...)` for a locomotive the database lacks, and this fails.
     */
    @Test
    public void testAFullSyncAgainstTheMockStationSucceeds() throws Exception
    {
        String was = TestStationAddress.get();

        List<String> served = describe(mockStation().parseLocomotives());

        int taken = takeTheServedLocomotivesOut();

        assertTrue(taken > 0, "precondition: the database holds none of the station's locomotives to take out");

        List<String> before = heldAmongTheServed();

        assertNotEquals(before, served, "precondition: the database starts out as the station's list");

        int requestsBefore = requests.get();

        TestStationAddress.set(address);

        try
        {
            int result = model.syncWithCS2();

            assertTrue(result >= 0,
                "a sync against a station that is answering should not report failure, got " + result);

            assertTrue(requests.get() > requestsBefore, "the sync never asked the station for anything");

            assertEquals(heldAmongTheServed(), served, "after the sync the database does not hold the station's"
                + " locomotives as its file describes them, though " + taken + " were missing before it");
        }
        finally
        {
            TestStationAddress.set(was);
        }
    }

    /**
     * A sync with no layout folder of ours fetches the diagram pages over HTTP, every page the station's index names.
     *
     * MUTATION: make `CS2File.getLayoutURL` answer a path the mock does not serve, and this fails.
     */
    @Test
    public void testASyncFetchesTheLayoutPagesOverHttp() throws Exception
    {
        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        String folder = prefs.get(org.traincontrol.gui.TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        String was = TestStationAddress.get();

        List<String> named = new LinkedList<>();

        for (java.util.Map<String, String> block : CS2File.parseFile(new java.io.BufferedReader(
            new java.io.InputStreamReader(new java.io.FileInputStream("test/test_layout/config/gleisbild.cs2"),
                java.nio.charset.StandardCharsets.UTF_8))))
        {
            if ("seite".equals(block.get("_type"))) named.add(block.get("name"));
        }

        assertFalse(named.isEmpty(), "precondition: the fixture's index names no pages");

        try
        {
            prefs.remove(org.traincontrol.gui.TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF);

            model.clearLayouts();

            TestStationAddress.set(address);

            int before = pageRequests.get();

            assertTrue(model.syncWithCS2() >= 0, "precondition: the sync against the mock station failed");

            assertEquals(pageRequests.get() - before, named.size(),
                "the sync did not ask the station for each page its index names");

            List<String> held = model.getLayoutList();

            assertEquals(new java.util.TreeSet<>(held), new java.util.TreeSet<>(named),
                "the pages fetched over HTTP are not the pages the station's index names");
        }
        finally
        {
            TestStationAddress.set(was);

            prefs.put(org.traincontrol.gui.TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, folder);

            model.refreshLayouts();
        }
    }

    /**
     * A locomotive file that stops half way is not taken for the whole of it: the database is as it was and the sync
     * says it failed.
     *
     * MUTATION: take the byte count out of `CS2File.fetchURL`, and the cut file is read as a smaller one: the sync
     * succeeds on it and this fails.
     */
    @Test
    public void testATruncatedLocomotiveFileIsNotTakenForTheWholeOfIt() throws Exception
    {
        assertADamagedLocomotiveFileChangesNothing(true);
    }

    /**
     * Every file the sync reads comes through `CS2File.fetchURL`, so one that ends short of its Content-Length is an
     * error for each: locomotives, routes, accessories, the page index and a page.  The CS3 JSON readers use the same
     * method.
     *
     * MUTATION: take the byte count out of `fetchURL`, and this fails for every path.
     */
    @Test(dataProvider = "files")
    public void testAnyFileThatEndsShortIsAnErrorNotASmallerFile(String suffix, String url) throws Exception
    {
        damagedPath = suffix;

        cutShort = true;

        try
        {
            try (java.io.BufferedReader in = CS2File.fetchURL("http://" + address + url))
            {
                String line;

                while ((line = in.readLine()) != null) { }
            }

            fail(url + " arrived cut off half way and was read to its end as though it were whole");
        }
        catch (java.io.IOException expected)
        {
            // what we want: the caller cannot mistake this for a smaller file
        }
        finally
        {
            damagedPath = null;
        }
    }

    @DataProvider
    public static Object[][] files()
    {
        return new Object[][]
        {
            {"/lokomotive.cs2", "/config/lokomotive.cs2"}, {"/fahrstrassen.cs2", "/config/fahrstrassen.cs2"},
            {"/magnetartikel.cs2", "/config/magnetartikel.cs2"}, {"/gleisbild.cs2", "/config/gleisbild.cs2"},
            {"Main.cs2", "/config/gleisbilder/1%20-%20Main.cs2"}
        };
    }

    /**
     * A station that promises more bytes than it sends, the same short body every time, is a wrong length rather than a
     * cut-off: the sync reads what it sent, and every locomotive comes in as the file has it.
     *
     * MUTATION: drop the retry in `CS2File.fetchURL` (throw at the first short body), and the sync fails.
     */
    @Test
    public void testAStationThatAlwaysSendsTheSameShortBodyStillSyncs() throws Exception
    {
        String was = TestStationAddress.get();

        List<String> served = describe(mockStation().parseLocomotives());

        assertTrue(takeTheServedLocomotivesOut() > 0, "precondition: the database holds none of the station's locomotives");

        damagedPath = "/lokomotive.cs2";
        cutShort = true;
        wrongLength = true;

        TestStationAddress.set(address);

        try
        {
            assertTrue(model.syncWithCS2() >= 0, "a station whose file is always the same bytes short of its length could"
                + " not be synced with: " + model.getLastSyncError());

            assertEquals(heldAmongTheServed(), served, "the locomotives are not the file's");
        }
        finally
        {
            damagedPath = null;
            wrongLength = false;

            TestStationAddress.set(was);
        }
    }

    /**
     * The same short bytes read as sent are said in the application's log, in the user's language, not only to the
     * developer's logger.
     *
     * MUTATION: write the line in `CS2File.fetchURL` to `java.util.logging` for `CS2File` again, and the log lacks it.
     */
    @Test
    public void testAShortBodyReadAsSentIsInTheLog() throws Exception
    {
        List<String> logged = java.util.Collections.synchronizedList(new LinkedList<>());
        java.util.logging.Handler tap = support.LogTap.messagesInto(logged);
        java.util.logging.Logger station = java.util.logging.Logger.getLogger(
            org.traincontrol.marklin.MarklinControlStation.class.getName());

        station.addHandler(tap);

        // The model drops a line equal to the one before it, which another claim's fetch of this file may have written
        model.log("next claim " + System.nanoTime());

        String url = "http://" + address + "/config/lokomotive.cs2";
        int size = Files.readAllBytes(new File("test/lokomotive.cs2").toPath()).length;

        damagedPath = "/lokomotive.cs2";
        cutShort = true;
        wrongLength = true;

        try
        {
            CS2File.fetchURL(url).close();

            assertTrue(logged.contains(org.traincontrol.util.I18n.f("network.stationFileReadAsSent", url,
                String.valueOf(size), String.valueOf(size + 7))), "the log does not say the file was read as sent: "
                + logged);
        }
        finally
        {
            station.removeHandler(tap);
            damagedPath = null;
            wrongLength = false;
        }
    }

    /**
     * A body that ends short is asked for again after a pause, not at once: three fetches milliseconds apart would all
     * meet a station that is briefly unable to send the file.
     *
     * MUTATION: drop the pause in `CS2File.fetchURL`, and the fetches come together.
     */
    @Test
    public void testAShortBodyIsAskedForAgainAfterAPause() throws Exception
    {
        damagedPath = "/lokomotive.cs2";
        cutShort = true;
        wrongLength = true;
        damagedFetchTimes.clear();

        try
        {
            CS2File.fetchURL("http://" + address + "/config/lokomotive.cs2").close();

            List<Long> at = new java.util.ArrayList<>(damagedFetchTimes);

            assertEquals(at.size(), 3, "the short body is fetched three times in all");

            for (int i = 1; i < at.size(); i++)
            {
                assertTrue(at.get(i) - at.get(i - 1) >= 200_000_000L, "fetch " + (i + 1) + " came "
                    + (at.get(i) - at.get(i - 1)) / 1_000_000 + " ms after the one before");
            }
        }
        finally
        {
            damagedPath = null;
            wrongLength = false;
        }
    }

    /**
     * Headers with no body, every time, are never the station's whole file, though they are the same each time: the
     * accessory list fails the sync, rather than being read as empty and every tile at a DCC article's address built
     * as MM2.  The diagram is read from the station.
     *
     * MUTATION: take the empty-body failure out of `CS2File.fetchURL`, and the sync succeeds with the tile MM2.
     */
    @Test
    public void testHeadersAndNoBodyEveryTimeFailTheSync() throws Exception
    {
        assertAnAccessoryListThatFailsToArriveBuildsNothing(true);
    }

    /**
     * A file cut off once and whole when asked again is read whole.
     *
     * MUTATION: drop the retry in `CS2File.fetchURL`, and the fetch fails.
     */
    @Test
    public void testAFileCutOffOnceIsReadWholeOnTheRetry() throws Exception
    {
        damagedPath = "/lokomotive.cs2";
        cutShort = true;
        damagedFetchesLeft.set(1);

        try
        {
            assertEquals(describe(mockStation().parseLocomotives()), describe(mockStation().parseLocomotives(CS2File.parseFile(
                new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream("test/lokomotive.cs2"),
                    java.nio.charset.StandardCharsets.UTF_8))))), "a file whole on the second fetch is read as the file");
        }
        finally
        {
            damagedPath = null;
            damagedFetchesLeft.set(Integer.MAX_VALUE);
        }
    }

    /**
     * An accessory list that fails to arrive fails the sync before the diagram is built: no tile at a DCC article's
     * address is made MM2, and the next sync builds the diagram right.  The diagram is read from the station.
     *
     * MUTATION: catch every exception around `getMagList` in `MarklinControlStation.syncLayouts`, as an empty list, and
     * the tiles are built as MM2 and stay so.
     */
    @Test
    public void testAnAccessoryListThatFailsToArriveFailsTheSyncAndBuildsNothingFromIt() throws Exception
    {
        assertAnAccessoryListThatFailsToArriveBuildsNothing(false);
    }

    /**
     * A first sync from the station with its accessory list cut off, or headers alone every time: it fails and builds no
     * tile at DCC address 1 as MM2, and the next sync builds it right.
     */
    private static void assertAnAccessoryListThatFailsToArriveBuildsNothing(boolean asHeadersOnly) throws Exception
    {
        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();
        String folder = prefs.get(org.traincontrol.gui.TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
        String was = TestStationAddress.get();

        try
        {
            prefs.remove(org.traincontrol.gui.TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF);
            TestStationAddress.set(address);
            dccAtOne = true;

            model.clearLayouts();
            assertTrue(model.syncWithCS2() >= 0, "precondition: the healthy sync failed: " + model.getLastSyncError());

            String control = protocolsAtAddressOne();
            assertTrue(!control.isEmpty() && !control.contains("MM2"), "precondition: address 1 reads as DCC: " + control);

            // As at a first start: the station's diagram is read only into an empty layout database
            model.clearLayouts();
            damagedPath = "/magnetartikel.cs2";
            cutShort = true;
            headersOnly = asHeadersOnly;

            assertEquals(model.syncWithCS2(), -1, "a sync whose accessory list did not arrive answered success");
            assertNotNull(model.getLastSyncError(), "and names no reason");
            assertFalse(protocolsAtAddressOne().contains("MM2"), "the tile at DCC address 1 was built as MM2 from a list"
                + " that never arrived: " + protocolsAtAddressOne());

            damagedPath = null;

            assertTrue(model.syncWithCS2() >= 0, "the next, healthy sync failed: " + model.getLastSyncError());
            assertEquals(protocolsAtAddressOne(), control, "after the next sync, the tile at DCC address 1 is");
        }
        finally
        {
            damagedPath = null;
            headersOnly = false;
            dccAtOne = false;
            TestStationAddress.set(was);
            prefs.put(org.traincontrol.gui.TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, folder);
            model.refreshLayouts();
        }
    }

    /** The protocols of the accessory tiles at address 1 on every page, in page order. */
    private static String protocolsAtAddressOne()
    {
        StringBuilder found = new StringBuilder();

        for (String name : model.getLayoutList())
        {
            for (org.traincontrol.base.LayoutDiagramComponent c : model.getLayout(name).getAll())
            {
                if (c.isAccessory() && c.getAddress() == 1) found.append(c.getProtocol()).append(' ');
            }
        }

        return found.toString().trim();
    }

    /**
     * A station whose device file fails to arrive fails the sync before the routes and locomotives are read, rather than
     * being taken for a CS2: the database is as it was.
     *
     * MUTATION: drop the throw of `typeUnknown` in `MarklinControlStation.syncWithCS2`, and the sync succeeds.
     */
    @Test
    public void testAStationThatDoesNotSayWhatItIsFailsTheSync() throws Exception
    {
        String was = TestStationAddress.get();
        List<String> described = everythingHeld();

        damagedPath = "/geraet.vrs";
        cutShort = true;

        TestStationAddress.set(address);

        try
        {
            assertEquals(model.syncWithCS2(), -1, "a sync whose device file did not arrive answered success");
            assertNotNull(model.getLastSyncError(), "and names no reason");
            assertEquals(everythingHeld(), described, "the sync changed the locomotive database");
        }
        finally
        {
            damagedPath = null;

            TestStationAddress.set(was);
        }
    }

    /**
     * `ping` answers once the headers of /can/ arrive and does not wait for its body, which a station may never end.
     *
     * MUTATION: have `CS2File.ping` read the body to its end (through `fetchURL`), and it answers false after the read
     * timeout.
     */
    @Test(timeOut = 60000)
    public void testPingDoesNotWaitForTheBody() throws Exception
    {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        HttpServer endless = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        endless.createContext("/", exchange ->
        {
            exchange.sendResponseHeaders(200, 0);

            OutputStream out = exchange.getResponseBody();
            out.write("still going".getBytes("UTF-8"));
            out.flush();

            try
            {
                release.await();
            }
            catch (InterruptedException stop)
            {
                Thread.currentThread().interrupt();
            }

            exchange.close();
        });
        endless.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        endless.start();

        try
        {
            long started = System.nanoTime();
            boolean reached = CS2File.ping("127.0.0.1:" + endless.getAddress().getPort());
            long tookMs = (System.nanoTime() - started) / 1000000;

            assertTrue(reached, "a station whose /can/ body does not end was not reached");
            assertTrue(tookMs < CS2File.READ_TIMEOUT_MS / 2, "ping waited " + tookMs + " ms, for the body");
        }
        finally
        {
            release.countDown();
            endless.stop(0);
        }
    }

    /**
     * A station file in UTF-8 or in ISO-8859-1 syncs with every name exactly right, umlauts included.
     *
     * MUTATION: decode every fetched file as UTF-8 only, in `CS2File.fetchURL`, and the Latin-1 case fails.
     */
    @Test(dataProvider = "encodings")
    public void testAStationFileInEitherEncodingSyncsWithTheNamesRight(boolean latin1) throws Exception
    {
        String was = TestStationAddress.get();

        List<String> served = describe(mockStation().parseLocomotives());

        assertTrue(takeTheServedLocomotivesOut() > 0, "precondition: the database holds none of the station's locomotives");

        damagedPath = latin1 ? "/lokomotive.cs2" : null;

        cutShort = false;

        TestStationAddress.set(address);

        try
        {
            assertTrue(model.syncWithCS2() >= 0, "the sync of a " + (latin1 ? "Latin-1" : "UTF-8") + " file failed");

            assertEquals(heldAmongTheServed(), served, "a " + (latin1 ? "Latin-1" : "UTF-8") + " file synced with"
                + " names or addresses that are not the file's");
        }
        finally
        {
            damagedPath = null;

            TestStationAddress.set(was);
        }
    }

    @DataProvider
    public static Object[][] encodings()
    {
        return new Object[][] {{false}, {true}};
    }

    /** A locomotive file that stops half way is not taken for the whole of it: the database is as it was. */
    private void assertADamagedLocomotiveFileChangesNothing(boolean truncated) throws Exception
    {
        String was = TestStationAddress.get();

        int taken = takeTheServedLocomotivesOut();

        assertTrue(taken > 0, "precondition: the database holds none of the station's locomotives to take out");

        List<String> described = everythingHeld();

        damagedPath = "/lokomotive.cs2";

        cutShort = truncated;

        TestStationAddress.set(address);

        try
        {
            int result = model.syncWithCS2();

            assertEquals(result, -1, "the sync did not report failing on a locomotive file that arrived cut off half way");

            assertNotNull(model.getLastSyncError(), "the sync names no error for the damaged file");

            assertEquals(everythingHeld(), described, "the sync changed the locomotive database from a damaged file");
        }
        finally
        {
            damagedPath = null;

            assertTrue(model.syncWithCS2() >= 0, "the sync that puts the locomotives back failed");

            TestStationAddress.set(was);
        }
    }

    private static List<String> everythingHeld()
    {
        List<MarklinLocomotive> held = new LinkedList<>();

        for (String name : model.getLocList()) held.add(model.getLocByName(name));

        return describe(held);
    }

    /** Takes out of the database each locomotive the station's file lists; how many were there. */
    private static int takeTheServedLocomotivesOut() throws Exception
    {
        int taken = 0;

        for (MarklinLocomotive l : mockStation().parseLocomotives())
        {
            if (model.getLocByName(l.getName()) != null && model.deleteLoc(l.getName())) taken++;
        }

        return taken;
    }

    /** The database's own entry for each locomotive the station's file lists, as name, address and decoder type. */
    private static List<String> heldAmongTheServed() throws Exception
    {
        List<MarklinLocomotive> held = new LinkedList<>();

        for (MarklinLocomotive l : mockStation().parseLocomotives())
        {
            MarklinLocomotive mine = model.getLocByName(l.getName());

            if (mine != null) held.add(mine);
        }

        return describe(held);
    }

    /**
     * A route file that arrives as rubbish leaves the routes alone: the database the sync really does delete from.
     * Only the route file is garbled; every file broken at once would not reach the route-deletion code.
     *
     * MUTATION: make the route half of `syncWithCS2` delete every route absent from an empty parse.
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
     * path that deletes a locomotive yet.
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

    /** Each locomotive as name, address and decoder type: what a command needs to reach the right engine. */
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
