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
 * A Central Station that is not there: a local HTTP server serving the same config files a real one
 * would, so the fetching half of a sync can be tested without hardware.
 *
 * Everything TrainControl knows about a Central Station arrives as an HTTP GET of a `.cs2` file, and
 * until now nothing exercised that path - the parsing tests read the fixtures off disk, which proves
 * the parser and nothing about the fetch. What this covers is the seam between them: that a file
 * served over the wire produces exactly what the same file produces locally, and that a station which
 * is missing, empty, or broken is REPORTED rather than quietly producing an empty database.
 *
 * That last one is the point: "the fetch failed" and "the station has nothing" must never look the
 * same, because the sync acts on what came off the wire.
 *
 * Exactly what it acts on is worth being precise about, because this file used to say something
 * stronger than the code does (TA-B5). `syncWithCS2` does NOT replace the locomotive database
 * wholesale - it matches by UID, renames, re-addresses, and never deletes a locomotive. The database
 * it deletes from is the ROUTES: a fetched route whose commands, s88 or trigger differ from the
 * stored route of that id is deleted and re-added, and a fetched name belonging to another id deletes
 * the route that had it. So the sharpest fault-injection test in this file is the garbled ROUTE file,
 * and the locomotive one beside it is a ratchet against a wipe that does not exist yet.
 *
 * The port is chosen by the operating system, so two of these can run at once and neither needs a
 * privileged port. CS2File takes its address as a string and builds "http://" + it, so a host:port pair
 * goes in unchanged - which is what makes this possible without touching production code.
 */
public class testMockCentralStation
{
    private static HttpServer server;

    /**
     * One model for the whole class.  init binds the Central Station's UDP port, so a second call in
     * the same JVM fails with "address already in use" - which reads as a test failure and is not one.
     */
    private static org.traincontrol.marklin.MarklinControlStation model;

    private static String address;

    /** How many times the mock was asked for anything, so a test can prove a fetch happened. */
    private static final AtomicInteger requests = new AtomicInteger();

    /** Set by a test to make the next fetch fail in a particular way. */
    private static volatile int forcedStatus = 200;

    private static volatile String forcedBody;

    /**
     * A path suffix to serve rubbish for, leaving every other file intact.
     *
     * `forcedBody` garbles the whole station, which is not the case that matters: a sync fetches the
     * layout, the locomotives, the routes and the accessories, and what has to be tested is ONE of
     * them arriving broken while the rest are fine.  That is the shape a real fault takes - a station
     * that has lost or is mid-write on one config file - and it is the shape under which the sync
     * decides whether to delete anything.
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

    @AfterClass
    public static void tearDownClass()
    {
        if (server != null) server.stop(0);
    }

    /**
     * Serves the fixture that matches the path, as a real station would.
     */
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

    /**
     * Which file on disk stands in for a given Central Station path.
     */
    private static File fixtureFor(String path)
    {
        if (path.endsWith("/lokomotive.cs2")) return new File("test/lokomotive.cs2");
        if (path.endsWith("/fahrstrassen.cs2")) return new File("test/fahrstrassen.cs2");
        if (path.endsWith("/magnetartikel.cs2")) return new File("test/magnetartikel.cs2");
        if (path.endsWith("/gleisbild.cs2")) return new File("test_layout/config/gleisbild.cs2");

        return null;
    }

    private static CS2File mockStation()
    {
        return new CS2File(address, null);
    }

    // ---------------------------------------------------------------- parity with a local read

    /**
     * A locomotive database fetched over the wire is the one the file describes.
     *
     * Parity rather than a fixed expectation, because a fixed one would only say that the fixture has
     * not changed. What matters is that fetching adds nothing and loses nothing.
     *
     * Both sides share the parser, so what this can catch is a transport-level loss - a truncated
     * body, a mangled encoding, a dropped block - and not a parser that is wrong in the same way
     * twice.  It used to compare sorted NAMES alone, which left the addresses and decoder types
     * outside the comparison entirely (TA-B5): a fetch that read every name correctly and every
     * address as zero passed under a docstring saying the two agreed.  The address is the field that
     * decides which engine a command reaches, so it is in the comparison now.
     */
    @Test
    public void testLocomotivesFetchedOverHttpMatchTheFile() throws Exception
    {
        int before = requests.get();

        List<MarklinLocomotive> overHttp = mockStation().parseLocomotives();

        assertTrue(requests.get() > before, "nothing was actually fetched");

        // UTF-8 explicitly, because that is what fetchURL uses.  Reading the fixture with the platform
        // default instead made this test disagree with itself over an umlaut - the HTTP side had
        // "Reihe 2048 OeBB" right and the local side did not, which says nothing about the code and
        // everything about how the comparison was set up.
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

    /**
     * And the accessories.
     */
    @Test
    public void testAccessoriesFetchedOverHttpParse() throws Exception
    {
        List<MarklinAccessory> accessories =
            mockStation().parseMags(CS2File.parseFile(CS2File.fetchURL(mockStation().getMagURL(false))));

        assertFalse(accessories.isEmpty(), "the fixture should contain accessories");
    }

    // ---------------------------------------------------------------- and when the station is not well

    /**
     * A station that answers 404 fails loudly rather than producing an empty database.
     *
     * This is the one that matters. A sync replaces the locomotive database wholesale, so a fetch that
     * failed and a station with no locomotives must never look the same - the first would wipe every
     * locomotive the user has.
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

    /**
     * A station that answers with rubbish does not produce a half-built database.
     */
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
     * Nothing is listening on the port: the connection is refused, and that is reported.
     *
     * This test used to carry a `took < 30000` assertion said to be about the connect timeout, and it
     * could not fail (TA-B5): a closed port on the loopback interface answers RST in microseconds, so
     * the timeout is never reached and deleting `setConnectTimeout` changed nothing here.  The timing
     * claim has moved to the test below, which uses an address that really does go quiet.  What is
     * left is the property this fixture can actually demonstrate, and it is worth having on its own:
     * a refused connection is an error, never an empty database.
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
     * A station that has been switched off is given up on rather than waited for.
     *
     * This is the freeze the connect timeout exists to prevent, and TA-B5 found that nothing tested
     * it: a station whose power has been pulled does not refuse the connection, it stops answering
     * altogether, and the SYNs go into a hole.  The operating system then retries for its own idea of
     * long enough - measured on this machine at 21 seconds - with the interface waiting on it.
     *
     * 192.0.2.1 is TEST-NET-1 (RFC 5737): reserved for documentation, routed nowhere, and so black
     * holes rather than refusing.  A machine whose routing table rejects it outright would fail fast
     * for the wrong reason and make this vacuous, so that case SKIPS rather than passing - a skip is
     * visible in the run and a silent pass is not.
     *
     * Mutation this must fail: delete `connection.setConnectTimeout(CONNECT_TIMEOUT_MS)` from
     * `CS2File.fetchURL`.
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

            // Four times the configured timeout, so a fetch that legitimately makes more than one
            // attempt still fits, and the operating system's own 21-second retry does not
            assertTrue(took < CS2File.CONNECT_TIMEOUT_MS * 4,
                "a station that never answers took " + took + "ms to give up, where the configured "
                + "connect timeout is " + CS2File.CONNECT_TIMEOUT_MS + "ms.  Without that timeout the "
                + "wait is the operating system's, and the whole interface is held for the length of "
                + "it every time a switched-off station is synced");
        }
    }

    // ---------------------------------------------------------------- the whole sync, end to end

    /**
     * A full sync against the mock station, reconciliation and all.
     *
     * syncWithCS2 is two hundred lines that decide what happens to every locomotive the user owns -
     * matched by UID, renamed, re-addressed, function types compared - and until the TEST_CS2_ADDRESS
     * seam existed none of it could be run without a Central Station on the network.  What this asserts
     * is modest on purpose: that a sync against a station which IS there succeeds and brings its
     * locomotives in.  The value is that the path now executes at all, so the reconciliation can be
     * given sharper tests later, and the fetch/apply split has something to prove itself against.
     *
     * Nothing is saved.  The databases are mutated in memory, this class runs in its own JVM, and
     * saveState is a UI action - so the operator's LocDB.data on disk is not touched.
     */
    @Test
    public void testAFullSyncAgainstTheMockStationSucceeds() throws Exception
    {
        String was = TestStationAddress.get();

        // `requests` is class-static and never reset, and three earlier tests have already fetched
        // through it.  Asserting `requests.get() > 0` after the sync therefore said nothing about the
        // sync at all (TA-B5) - it was true before the sync ran.  The delta is the fact
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
     * A route file that arrives as rubbish leaves the routes alone.
     *
     * The risk this class was written for, aimed at the database the sync really does delete from.
     * Its own javadoc used to say a failed sync "would wipe every locomotive the user has", and that
     * is not what the code does - `syncWithCS2` matches, renames and re-addresses locomotives and
     * never deletes one.  It DOES delete routes: a fetched route whose commands, s88 or trigger differ
     * from the stored one of that id is deleted and re-added, and a fetched name that belongs to a
     * different id deletes the route that had it.  Both decisions are made from what came off the
     * wire, so a fetch that came back as nonsense is a fetch that could decide anything.
     *
     * Only the route file is garbled here.  A station with a broken `fahrstrassen.cs2` is a real
     * fault; a station where every file is broken at once is not, and garbling everything would not
     * reach the route-deletion code at all.
     *
     * Mutation this must fail: make the route half of `syncWithCS2` act on an empty fetch as though
     * the station had said the routes were gone - `for (MarklinRoute r : this.getRoutes()) if
     * (!named(parsedRoutes, r.getName())) this.deleteRoute(r.getName());` after the parse.
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
     * A sync against a station that is not there leaves the locomotive database alone.
     *
     * The premise here used to be stronger than the code: "this is the case that would cost a user
     * every locomotive they own".  It would not, as things stand - `syncWithCS2` matches locomotives
     * by UID, renames and re-addresses them, and has no path that deletes one, so there is no wipe for
     * a failed fetch to trigger (TA-B5).  What this test is really worth is as a ratchet: the day
     * something in the sync learns to remove a locomotive that the station no longer lists, a failed
     * fetch will look exactly like a station that lists nothing, and this fails.
     *
     * The database the sync DOES delete from is the routes, and that is tested above.
     */
    @Test
    public void testASyncAgainstNothingDoesNotEmptyTheDatabase() throws Exception
    {
        int before = model.getLocList().size();

        // Assert the variable, not the control.  On an empty database "the count did not change" is
        // 0 == 0, which is true however badly the sync behaves
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
     * Each locomotive as name, address and decoder type - the three fields a command needs to reach
     * the right engine, rather than the name alone.
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
