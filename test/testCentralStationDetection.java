import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.marklin.udp.CSDetect;

/**
 * A Central Station that answers slowly once is still recognised.
 *
 * Auto-detect pings every address on the subnet from ten threads at once, and a host that answers the
 * ping is then asked for the Central Station's web page.  The ping was retried; the web request was
 * not - one 500 ms attempt, and any exception meant "not a Central Station".  So one slow reply threw
 * away a station that had just proved it was there, and the scan walked past it ("it works 9 out of
 * 10 times").  The same check runs when connecting to a typed address, where it could give a false
 * "not a Central Station" warning.
 *
 * The station here is a real HTTP server on a local port that behaves the way the real one did: slow
 * the first time, fine the second.  It is asked through isCentralStation, the check both callers use.
 *
 * Ported from the 3.0 branch (42d66b31).
 */
public class testCentralStationDetection
{
    /** The page that says "this is a Central Station", as the check asks for it. */
    private static final String PATH = CS2File.getLayoutMasterURL("");

    /**
     * One slow answer no longer loses the station.
     */
    @Test
    public void testAStationThatAnswersSlowlyOnceIsStillFound() throws Exception
    {
        AtomicInteger asked = new AtomicInteger();

        // The first request sleeps well past the read timeout; every one after it answers at once
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

            try
            {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            finally
            {
                exchange.close();
            }
        });

        try
        {
            String host = "127.0.0.1:" + server.getAddress().getPort();

            assertTrue(CSDetect.isCentralStation(host),
                "a Central Station that was slow to answer once was not recognised: the ping is "
                + "retried and the web check was not, so one timeout threw away a station that had "
                + "just answered a ping");

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
     * And something that is not a Central Station is still turned down, after a bounded number of
     * attempts - a printer or a NAS on the same network must not be taken for one, nor asked for ever.
     */
    @Test
    public void testSomethingElseIsStillNotACentralStation() throws Exception
    {
        AtomicInteger asked = new AtomicInteger();

        HttpServer server = serving(exchange ->
        {
            asked.incrementAndGet();

            try
            {
                exchange.sendResponseHeaders(404, -1);
            }
            finally
            {
                exchange.close();
            }
        });

        try
        {
            String host = "127.0.0.1:" + server.getAddress().getPort();

            assertFalse(CSDetect.isCentralStation(host),
                "something answering 404 was taken for a Central Station");

            assertTrue(asked.get() >= 1 && asked.get() <= 3,
                "asked " + asked.get() + " times - the check must ask, and must give up");
        }
        finally
        {
            server.stop(0);
        }
    }

    /**
     * A local HTTP server on a free port, answering the Central Station path however it is told to.
     */
    private static HttpServer serving(com.sun.net.httpserver.HttpHandler handler) throws Exception
    {
        // Port 0: the operating system picks a free one
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext(PATH, handler);
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();

        return server;
    }
}
