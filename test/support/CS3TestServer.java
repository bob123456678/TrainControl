package support;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URI;

public class CS3TestServer
{
    private HttpServer server;
    /**
     * Zero until the first start, then whatever the machine gave us (TSX-C17).
     *
     * This was 8080 - a constant, with no setter, no argument and no property, handed straight to the
     * socket - which is the single most contended port on a developer machine. The suite has already
     * paid for a fixed port twice on the UDP side and written the argument down both times, in
     * `battery.sh`: a bind failure comes out of `@BeforeClass` as "Total tests run: 16, Failures: 0,
     * Skips: 16", which reads as a class nobody wrote tests for.
     *
     * **Kept after the first bind, because a restart has to land on the same number.**
     * `testParseWebServer` stops the server and starts it again to serve a different firmware version,
     * with a parser built once against the address - so an ephemeral port per start would break it.
     * Binding zero once and reusing the answer gives both: no collision, and a stable address.
     */
    private int port = 0;

    private final byte[] loks250Json;
    private final byte[] loks260Json;
    private final byte[] magsJson;
    private final byte[] automatics250Json;
    private final byte[] automatics260Json;

    public CS3TestServer(String loks250Path,
                         String loks260Path,
                         String magsPath,
                         String automatics250Path,
                         String automatics260Path) throws Exception
    {
        this.loks250Json = Files.readAllBytes(toPath(loks250Path));
        this.loks260Json = Files.readAllBytes(toPath(loks260Path));
        this.magsJson = Files.readAllBytes(toPath(magsPath));
        this.automatics250Json = Files.readAllBytes(toPath(automatics250Path));
        this.automatics260Json = Files.readAllBytes(toPath(automatics260Path));
    }

    private java.nio.file.Path toPath(String input) throws Exception
    {
        if (input.startsWith("file:"))
        {
            return Paths.get(new URI(input));
        }
        return Paths.get(input);
    }

    public void startServer(int version) throws Exception
    {
        boolean is260 = version >= 260;

        server = HttpServer.create(new InetSocketAddress(port), 0);

        // The real number, so getPort() can answer and a restart can ask for it again.
        port = server.getAddress().getPort();

        //
        // /app/api/loks
        //
        server.createContext("/app/api/loks", exchange ->
        {
            if (is260)
            {
                sendNotFound(exchange);
            }
            else
            {
                sendJson(exchange, loks250Json);
            }
        });

        //
        // /app/api/locos
        //
        server.createContext("/app/api/locos", exchange ->
        {
            if (is260)
            {
                sendJson(exchange, loks260Json);
            }
            else
            {
                sendNotFound(exchange);
            }
        });

        //
        // /app/api/mags  (always available)
        //
        server.createContext("/app/api/mags", exchange ->
        {
            sendJson(exchange, magsJson);
        });

        //
        // /app/api/automatics
        //
        server.createContext("/app/api/automatics", exchange ->
        {
            if (is260)
            {
                sendJson(exchange, automatics260Json);
            }
            else
            {
                sendJson(exchange, automatics250Json);
            }
        });

        server.start();
        System.out.println("CS3 test server running on port " + port + " (version=" + version + ")");
    }

    public void stopServer()
    {
        if (server != null)
        {
            server.stop(0);
            System.out.println("CS3 test server stopped");
        }
    }

    private void sendJson(HttpExchange exchange, byte[] data) throws IOException
    {
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(data);
        }
    }

    private void sendNotFound(HttpExchange exchange) throws IOException
    {
        byte[] data = "{\"error\":\"Not Found\"}".getBytes();
        exchange.sendResponseHeaders(404, data.length);
        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(data);
        }
    }

    /**
     * The port this is listening on, which is only meaningful after {@link #startServer(int)}.
     *
     * It used to read a constant back, which made it an accessor that implied the number was
     * answerable when it was not (TSX-C17).
     *
     * @return the port, or 0 before the first start
     */
    public int getPort()
    {
        return port;
    }
}
