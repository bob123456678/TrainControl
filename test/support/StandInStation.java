package support;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import static org.testng.Assert.assertTrue;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A loopback HTTP server standing in for a Central Station's web server, counting the requests by path.  Each request
 * is answered with what the given function returns for its path and its count so far (1 for the first): null is a 404
 * with the CS3's own "Not Found" body, an empty array an empty 200.
 *
 * @author Adam
 */
public final class StandInStation implements AutoCloseable
{
    /** The device file of a CS3 plus, as `isCS3` reads it. */
    public static final byte[] CS3_DEVICE = "[geraet]\ngeraet\n .produkt=Central Station 3 plus\n"
        .getBytes(StandardCharsets.UTF_8);

    private final HttpServer server;

    private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();

    public StandInStation(BiFunction<String, Integer, byte[]> answer) throws IOException
    {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        this.server.createContext("/", exchange ->
        {
            String path = exchange.getRequestURI().getPath();
            byte[] body = answer.apply(path, requests.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet());
            int status = body == null ? 404 : 200;

            if (body == null)
            {
                body = "{\"error\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8);
            }

            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);

            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }
        });

        this.server.start();
    }

    /** host:port, as a station address. */
    public String address()
    {
        return "127.0.0.1:" + this.server.getAddress().getPort();
    }

    /** How many requests a path has had since the last forget. */
    public int requests(String path)
    {
        AtomicInteger n = this.requests.get(path);
        return n == null ? 0 : n.get();
    }

    /** Every path asked for, with its count, in path order. */
    public String asked()
    {
        return new java.util.TreeMap<>(this.requests).toString();
    }

    public void forget()
    {
        this.requests.clear();
    }

    /** Runs a sync of the model with this station, putting the station address back after. */
    public void sync(MarklinControlStation model) throws Exception
    {
        String was = TestStationAddress.get();

        try
        {
            TestStationAddress.set(address());

            assertTrue(model.syncWithCS2() >= 0, "the sync with the stand-in succeeds: " + model.getLastSyncError());
        }
        finally
        {
            TestStationAddress.set(was);
        }
    }

    /** Closes the station, stops the model and closes the sandbox, the sandbox even if the others throw. */
    public static void tearDown(StandInStation station, MarklinControlStation model, LayoutSandbox sandbox)
    {
        try
        {
            if (station != null)
            {
                station.close();
            }

            if (model != null)
            {
                model.stop();
            }
        }
        finally
        {
            if (sandbox != null)
            {
                sandbox.close();
            }
        }
    }

    @Override
    public void close()
    {
        this.server.stop(0);
    }

    /** A file's bytes, or null when there is no such file. */
    public static byte[] file(String path)
    {
        try
        {
            return new File(path).isFile() ? Files.readAllBytes(new File(path).toPath()) : null;
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** Whether the model takes its station for a CS3, set as a sync's detection sets it. */
    public static void setCS3(MarklinControlStation model, boolean cs3) throws ReflectiveOperationException
    {
        java.lang.reflect.Field f = MarklinControlStation.class.getDeclaredField("isCS3");
        f.setAccessible(true);
        f.set(model, cs3);
    }
}
