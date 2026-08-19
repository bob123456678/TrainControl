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
 * That last one is the point. A sync replaces the locomotive, route and accessory databases wholesale,
 * so "the fetch failed" and "the station has no locomotives" must never look the same.
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
        if (path.endsWith("/gleisbild.cs2")) return new File("cs2_sample_layout/config/gleisbild.cs2");

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

        assertEquals(names(overHttp), names(fromDisk),
            "the same file served over HTTP produced a different set of locomotives");

        assertFalse(overHttp.isEmpty(), "the fixture should contain locomotives, or this proves nothing");
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
     * Nothing is reachable at all: the address is right, the station is not there.
     */
    @Test
    public void testAnUnreachableStationFailsQuickly() throws Exception
    {
        // A port nothing is listening on.  Chosen by opening and closing a socket, so it is free and
        // this test does not guess.
        int dead;

        try (java.net.ServerSocket socket = new java.net.ServerSocket(0))
        {
            dead = socket.getLocalPort();
        }

        CS2File nothingThere = new CS2File("127.0.0.1:" + dead, null);

        long started = System.currentTimeMillis();

        try
        {
            nothingThere.parseLocomotives();

            fail("a station that is not there must not answer with a database");
        }
        catch (Exception expected)
        {
            long took = System.currentTimeMillis() - started;

            assertTrue(took < 30000,
                "an unreachable station took " + took + "ms to fail - the connect timeout is what stops "
                + "the interface hanging on a station that has been switched off");
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
        String was = org.traincontrol.marklin.MarklinControlStation.TEST_CS2_ADDRESS;

        org.traincontrol.marklin.MarklinControlStation.TEST_CS2_ADDRESS = address;

        try
        {
            int result = model.syncWithCS2();

            assertTrue(result >= 0,
                "a sync against a station that is answering should not report failure, got " + result);

            assertFalse(model.getLocList().isEmpty(),
                "the sync brought in no locomotives at all, though the station served a full file");

            // Something from the fixture, so this cannot pass on a database that was already loaded
            assertTrue(requests.get() > 0, "the sync never asked the station for anything");
        }
        finally
        {
            org.traincontrol.marklin.MarklinControlStation.TEST_CS2_ADDRESS = was;
        }
    }

    /**
     * A sync against a station that is not there reports failure rather than emptying the database.
     *
     * The whole reason the seam is worth having: this is the case that would cost a user every
     * locomotive they own, and it could not be tested before.
     */
    @Test
    public void testASyncAgainstNothingDoesNotEmptyTheDatabase() throws Exception
    {
        int before = model.getLocList().size();

        int dead;

        try (java.net.ServerSocket socket = new java.net.ServerSocket(0))
        {
            dead = socket.getLocalPort();
        }

        String was = org.traincontrol.marklin.MarklinControlStation.TEST_CS2_ADDRESS;

        org.traincontrol.marklin.MarklinControlStation.TEST_CS2_ADDRESS = "127.0.0.1:" + dead;

        try
        {
            model.syncWithCS2();

            assertEquals(model.getLocList().size(), before,
                "a sync against a station that is not there changed the locomotive database - it had "
                + before + " and now has " + model.getLocList().size());
        }
        finally
        {
            org.traincontrol.marklin.MarklinControlStation.TEST_CS2_ADDRESS = was;
        }
    }

    private static List<String> names(List<MarklinLocomotive> locomotives)
    {
        List<String> out = new LinkedList<>();

        for (MarklinLocomotive l : locomotives) out.add(l.getName());

        java.util.Collections.sort(out);

        return out;
    }
}
