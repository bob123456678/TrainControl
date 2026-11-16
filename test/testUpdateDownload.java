import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.util.Util;

/**
 * Tests that Help > Download Update asks for a file the way GitHub requires.
 *
 * GitHub answers HTTP 406 to a release download whose Accept header does not allow a binary file, and
 * Java's default header does not.  A local server stands in for GitHub: it answers 406 unless the
 * request accepts application/octet-stream, and 200 with the file when it does.  Nothing here ever
 * reaches the internet.
 */
public class testUpdateDownload
{
    private static final String BODY = "pretend this is the new jar";

    private HttpServer server;
    private Path dir;

    @BeforeMethod
    public void setUp() throws Exception
    {
        dir = Files.createTempDirectory("tc-download");

        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/release.jar", exchange ->
        {
            String accept = exchange.getRequestHeaders().getFirst("Accept");
            boolean acceptable = accept != null && accept.contains("application/octet-stream");
            byte[] body = acceptable ? BODY.getBytes(StandardCharsets.UTF_8) : new byte[0];

            exchange.sendResponseHeaders(acceptable ? 200 : 406, acceptable ? body.length : -1);

            if (acceptable)
            {
                exchange.getResponseBody().write(body);
            }

            exchange.close();
        });
        server.start();
    }

    @AfterMethod
    public void tearDown() throws Exception
    {
        server.stop(0);

        Files.walk(dir)
            .sorted(java.util.Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    /**
     * The download must ask for a binary file, or GitHub refuses it with 406.
     */
    @Test
    public void testTheDownloadAsksForABinaryFile() throws IOException
    {
        File target = new File(dir.toFile(), "update.jar");

        Util.downloadFile("http://127.0.0.1:" + server.getAddress().getPort() + "/release.jar", target);

        assertEquals(new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8), BODY,
            "the file the server sent must be what was saved");
    }
}
