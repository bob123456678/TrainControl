import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** Bodies served instead of the default ones, by path; one under NO_LENGTH is sent with no Content-Length. */
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private static final String NO_LENGTH = "/config/nolength.cs2";

    /** How many bytes of its body each request for cutPath gets, in order, under the whole length; later ones get it all. */
    private volatile String cutPath;
    private volatile int[] cuts = {};
    private final AtomicInteger asked = new AtomicInteger();

    @BeforeMethod
    public void setUp() throws Exception
    {
        dir = Files.createTempDirectory("tc-download").toFile();
        cutOffTheAccessories = false;
        files.clear();
        cutPath = null;
        cuts = new int[0];
        asked.set(0);

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

            byte[] b = files.containsKey(path) ? files.get(path) : body.getBytes(StandardCharsets.UTF_8);
            int send = b.length;

            if (path.equals(cutPath))
            {
                int n = asked.getAndIncrement();

                if (n < cuts.length) send = cuts[n];
            }

            out.write(("HTTP/1.1 200 OK\r\n" + (path.equals(NO_LENGTH) ? "" : "Content-Length: " + b.length + "\r\n")
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(b, 0, send);
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

    /** The whole text CS2File reads from a path on the stand-in station. */
    private String fetch(String path) throws Exception
    {
        StringBuilder text = new StringBuilder();

        try (BufferedReader in = CS2File.fetchURL("http://127.0.0.1:" + server.getLocalPort() + path))
        {
            int c;

            while ((c = in.read()) >= 0) text.append((char) c);
        }

        return text.toString();
    }

    private static final String MAGS = "/config/magnetartikel.cs2";
    private static final String MAGS_BODY = "[magnetartikel]\nversion\n .minor=0\nartikel\n .id=1\n";

    /**
     * A file that ends short of its Content-Length once, then comes whole, is asked for again and read whole (A8, 8d48bf40).
     *
     * 2.8.2 read to the end of whatever arrived, so a dropped connection gave the sync part of the station's list as if it
     * were all of it.
     */
    @Test
    public void testAFileCutOffOnceIsAskedForAgainAndReadWhole() throws Exception
    {
        cutPath = MAGS;
        cuts = new int[]{ 20 };

        assertEquals(fetch(MAGS), MAGS_BODY, "a file that ended short of its length was read as though it were whole");
        assertEquals(asked.get(), 2, "a file that ended short of its length must be asked for once more");
    }

    /** A file that ends short at a different place again fails, never read as a smaller file. */
    @Test
    public void testAFileCutOffEveryTimeFails() throws Exception
    {
        cutPath = MAGS;
        cuts = new int[]{ 30, 20, 10 };

        try
        {
            fetch(MAGS);
            fail("a file that ended short every time was read as though it were whole");
        }
        catch (java.io.IOException expected)
        {
            assertEquals(asked.get(), 2, "a file that ends short must be asked for again before it fails");
        }
    }

    /** Headers with no body, every time, are never an empty file (b0a91977). */
    @Test
    public void testHeadersAloneAreNeverAWholeFile() throws Exception
    {
        cutPath = MAGS;
        cuts = new int[]{ 0, 0, 0 };

        try
        {
            fetch(MAGS);
            fail("headers with no body were read as an empty file");
        }
        catch (java.io.IOException expected)
        {
            // what we want: an empty accessory list would build every tile as MM2
        }
    }

    /** The same short bytes every time are the station's wrong length, read as sent (8d48bf40). */
    @Test
    public void testTheSameShortBytesEveryTimeAreReadAsSent() throws Exception
    {
        cutPath = MAGS;
        cuts = new int[]{ 30, 30, 30 };

        assertEquals(fetch(MAGS), MAGS_BODY.substring(0, 30), "the same bytes every time must be read as sent");
        assertEquals(asked.get(), 3, "a file that ends short is asked for twice more");
    }

    /**
     * A station file that is not UTF-8 is read as ISO-8859-1, so a locomotive named with an umlaut reads right (A8,
     * 8fb579de).  2.8.2 decoded every file as UTF-8, and the name came through with a replacement character.
     */
    @Test
    public void testALatin1LocomotiveNameReadsRight() throws Exception
    {
        files.put("/config/lokomotive.cs2", "[lokomotive]\nlokomotive\n .name=B\u00e4r M\u00fcller\n .adresse=0x14\n"
            .getBytes(StandardCharsets.ISO_8859_1));

        List<Map<String, String>> read = CS2File.parseFile(new BufferedReader(new java.io.StringReader(fetch("/config/lokomotive.cs2"))));

        assertEquals(read.get(0).get("name"), "B\u00e4r M\u00fcller", "a Latin-1 locomotive name was read wrong");
    }

    /** A whole file, in UTF-8 with or without a Content-Length, reads exactly as before, asked for once. */
    @Test
    public void testAWholeFileReadsExactlyAsBefore() throws Exception
    {
        String body = "[lokomotive]\r\nlokomotive\n .name=K\u00f6f \u20ac 3\n .adresse=0x14\n";
        files.put("/config/lokomotive.cs2", body.getBytes(StandardCharsets.UTF_8));
        files.put(NO_LENGTH, body.getBytes(StandardCharsets.UTF_8));
        cutPath = "/config/lokomotive.cs2";

        assertEquals(fetch("/config/lokomotive.cs2"), body, "a whole UTF-8 file did not read exactly as sent");
        assertEquals(asked.get(), 1, "a whole file must be asked for once");
        assertEquals(fetch(NO_LENGTH), body, "a whole file with no length did not read exactly as sent");
    }
}
