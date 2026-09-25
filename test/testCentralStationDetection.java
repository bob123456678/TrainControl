import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import static org.testng.Assert.*;
import org.testng.SkipException;
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
     * One lost reply from a network's gateway does not make auto-detect impossible.
     *
     * Before it scans, auto-detect pings the gateway of each local network - its .1 address - to
     * decide which networks to scan.  That ping was the one in this class that was not retried, and
     * it gates the whole scan: one dropped reply left no network to scan, and the operator was told
     * auto-detect is not possible at all (BPV-C9).
     *
     * The network here is a stand-in that drops the first reply from every address and answers after
     * that; the local networks are this machine's own.  Nothing is sent on the network.
     *
     * Ported from the 3.0 branch (2b07376f, W21-C2).
     */
    @Test
    public void testOneLostReplyFromTheGatewayStillFindsTheNetwork() throws Exception
    {
        Field ping = CSDetect.class.getDeclaredField("ping");
        ping.setAccessible(true);

        Object real = ping.get(null);

        try
        {
            // The control: a network that always answers.  A machine with no local network has no
            // gateway to ask, and the assertion below would prove nothing.
            ping.set(null, (Predicate<String>) host -> true);

            if (!CSDetect.hasLocalSubnets())
            {
                throw new SkipException("Not run here: this machine has no local network with a "
                    + "broadcast address, so there is no gateway to ping");
            }

            // Every address drops its first reply, then answers
            Map<String, AtomicInteger> asked = new ConcurrentHashMap<>();

            ping.set(null, (Predicate<String>) host ->
                asked.computeIfAbsent(host, h -> new AtomicInteger()).incrementAndGet() > 1);

            assertTrue(CSDetect.hasLocalSubnets(),
                "one lost reply to the gateway ping left no local network to scan, so auto-detect "
                + "would say it is not possible - that ping was the only one in the class not retried");
        }
        finally
        {
            ping.set(null, real);
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
