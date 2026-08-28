package core;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.marklin.udp.CSDetect;

/**
 * MT-060: a Central Station that answers slowly once is still found.
 *
 * Adam, 2026-08-27, on the real railway: "it works 9 out of 10 times.  sometimes I see this without a
 * positive detection: 192.168.50.25 is reachable .......... and then it just goes on.  if I try again,
 * it gets redetected."
 *
 * That message is printed the moment a PING succeeds, so the host was there and answered; everything
 * after it is the web check. The ping was retried `PING_RETRY` times and the web request was not, so
 * one 500ms timeout - while ten threads are pinging the subnet - threw away a station that had just
 * proved it existed, and the scan walked past it.
 *
 * The station in these tests is a real HTTP server on a real socket that behaves the way his did: slow
 * the first time, fine the second. `checkWebServer` is asked directly rather than through the whole
 * subnet scan, because the scan needs a network with a Central Station on it and this needs to run on
 * a laptop.
 *
 * @author Adam
 */
public class testCentralStationDetection
{
    /** The path that says "this is a Central Station", as the scan asks for it. */
    private static final String PATH = "/config/gleisbild.cs2";

    /**
     * One slow answer no longer loses the station.
     *
     * MUTATION: setting WEB_RETRY to 1 - which is the behaviour before this fix - fails the first
     * assertion, and it fails it the same one-in-ten way Adam saw, except deterministically.
     */
    @Test
    public void testAStationThatAnswersSlowlyOnceIsStillFound() throws Exception
    {
        AtomicInteger asked = new AtomicInteger();

        // The first request sleeps past the read timeout; every one after it answers at once.
        HttpServer server = serving(exchange ->
        {
            if (asked.incrementAndGet() == 1)
            {
                try
                {
                    Thread.sleep(CSDetect.WEB_TIMEOUT_MS * 4L);
                }
                catch (InterruptedException stop)
                {
                    Thread.currentThread().interrupt();
                }
            }

            byte[] body = "cs2".getBytes(java.nio.charset.StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        try
        {
            String host = "127.0.0.1:" + server.getAddress().getPort();

            assertTrue(CSDetect.checkWebServer(host, PATH),
                "a Central Station that was slow to answer once was not found, which is the whole of "
                + "MT-060: the ping is retried and the web check was not, so one timeout threw away a "
                + "station that had just answered a ping");

            assertTrue(asked.get() > 1,
                "the station was found on the first ask, so this test never exercised the retry - "
                + "the fake server did not actually time out");
        }
        finally
        {
            server.stop(0);
        }
    }

    /**
     * And one attempt still means one attempt.
     *
     * The guard on the other side: a retry that could not be turned off would make the scan take as
     * many times longer as it retries, on every address that answers a ping and is not a Central
     * Station - a printer, a NAS, anything with a web interface.
     *
     * MUTATION: ignoring the `attempts` argument fails this.
     */
    @Test
    public void testOneAttemptAsksOnce() throws Exception
    {
        AtomicInteger asked = new AtomicInteger();

        // Something that is on the network and is NOT a Central Station.
        HttpServer server = serving(exchange ->
        {
            asked.incrementAndGet();

            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        try
        {
            String host = "127.0.0.1:" + server.getAddress().getPort();

            assertFalse(CSDetect.checkWebServer(host, PATH, 1),
                "something answering 404 was taken for a Central Station");

            assertEquals(asked.get(), 1,
                "asked for one attempt and made " + asked.get() + " - so every device on the subnet "
                + "with a web server on it is asked several times, and the scan takes that much "
                + "longer for a host that was never going to be a Central Station");
        }
        finally
        {
            server.stop(0);
        }
    }

    /**
     * The scan and the explicit address check both go through the retrying version.
     *
     * The retry was put INSIDE the name both callers already use rather than beside it, so that there
     * was no new call site anybody had to remember. This is what says so: two callers, no third method.
     *
     * MUTATION: giving `detectCentralStation` its own single-shot call fails this.
     */
    @Test
    public void testEverythingThatLooksForAStationRetries() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/marklin/udp/CSDetect.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(source.contains("return checkWebServer(host, path, WEB_RETRY);"),
            "the two-argument checkWebServer no longer retries, so both of its callers are back to "
            + "one attempt and MT-060 is back");

        // askWebServer is the single shot, and nothing outside this class may call it.
        assertTrue(source.contains("private static boolean askWebServer("),
            "the one-shot request is no longer private, so a caller can take the un-retried path "
            + "without meaning to");
    }

    /**
     * A local HTTP server on a free port, answering the Central Station path however it is told to.
     */
    private HttpServer serving(com.sun.net.httpserver.HttpHandler handler) throws Exception
    {
        // Port 0: the operating system picks a free one.  A fixed port races the battery, which runs
        // classes one after another but shares a machine with whatever else is on it.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext(PATH, handler);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
        server.start();

        return server;
    }
}
