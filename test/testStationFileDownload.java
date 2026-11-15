import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.marklin.file.CS2File;

/**
 * Downloading the layout files from a Central Station is all or nothing.
 *
 * A stand-in station on the loopback address serves the page index and, for one file, a body that is cut
 * off part way, as a dropped connection does.  The files already in the target folder
 * must survive that, instead of being replaced by a truncated copy that the next start reads as the
 * whole diagram.  Never talks to a real station.
 */
public class testStationFileDownload
{
    private static final String BEFORE = "the previous accessory file, which must survive\n";

    private ServerSocket server;
    private File dir;
    private volatile boolean cutOffTheAccessories;

    @BeforeMethod
    public void setUp() throws Exception
    {
        dir = Files.createTempDirectory("tc-download").toFile();
        cutOffTheAccessories = false;

        server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());

        Thread t = new Thread(() ->
        {
            try
            {
                while (true)
                {
                    serve(server.accept());
                }
            }
            catch (Exception e)
            {
                // The socket was closed: the test is over
            }
        });

        t.setDaemon(true);
        t.start();
    }

    /**
     * Answers one request.  The accessory file can be cut off: sent as chunks, then the connection is
     * dropped before the last chunk, which is how a client sees a transfer that stops part way.
     */
    private void serve(Socket s) throws Exception
    {
        try (Socket socket = s)
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String path = in.readLine().split(" ")[1];
            String line;

            while ((line = in.readLine()) != null && !line.isEmpty())
            {
                // Skip the request headers
            }

            OutputStream out = socket.getOutputStream();

            if (path.equals("/config/magnetartikel.cs2") && cutOffTheAccessories)
            {
                String part = "[magnetartikel]\nartikel\n .id=1\n";
                out.write(("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
                    + Integer.toHexString(part.length()) + "\r\n" + part + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                return;
            }

            String body = "[magnetartikel]\nversion\n .minor=0\nartikel\n .id=1\n";

            if (path.equals("/config/gleisbild.cs2")) body = "[gleisbild]\nversion\n .minor=0\nseite\n .name=Main\n";
            if (path.equals("/config/gleisbilder/Main.cs2")) body = "[gleisbildseite]\nversion\n .minor=0\nartikel\n .id=1\n";

            byte[] b = body.getBytes(StandardCharsets.UTF_8);

            out.write(("HTTP/1.1 200 OK\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
            out.write(b);
            out.flush();
        }
    }

    @AfterMethod
    public void tearDown() throws Exception
    {
        server.close();

        Files.walk(dir.toPath())
            .sorted(java.util.Comparator.reverseOrder())
            .map(java.nio.file.Path::toFile)
            .forEach(File::delete);
    }

    private CS2File station()
    {
        String where = "127.0.0.1:" + server.getLocalPort();
        CS2File f = new CS2File(where, null);
        f.setLayoutDataLoc("http://" + where);
        return f;
    }

    private File magnetFile()
    {
        return new File(new File(dir, "config"), "magnetartikel.cs2");
    }

    @Test
    public void testACompleteDownloadIsWritten() throws Exception
    {
        station().downloadCS2Layout(dir);

        assertTrue(new String(Files.readAllBytes(magnetFile().toPath()), StandardCharsets.UTF_8)
            .contains("version"), "the accessory file should have been written whole");
        assertTrue(new File(dir, "config/gleisbilder/Main.cs2").exists(), "the page should have been written");
    }

    @Test
    public void testATransferThatStopsPartWayLeavesThePreviousFile() throws Exception
    {
        new File(dir, "config").mkdirs();
        Files.write(magnetFile().toPath(), BEFORE.getBytes(StandardCharsets.UTF_8));

        cutOffTheAccessories = true;

        try
        {
            station().downloadCS2Layout(dir);
            fail("a transfer that stops part way must be reported as failed");
        }
        catch (Exception expected)
        {
            // Expected
        }

        assertEquals(new String(Files.readAllBytes(magnetFile().toPath()), StandardCharsets.UTF_8), BEFORE,
            "the previous accessory file was replaced by the part that arrived");
    }
}
