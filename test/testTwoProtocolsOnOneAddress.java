import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.udp.CANMessage;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.marklin.udp.NetworkProxy;
import org.traincontrol.model.View;
import org.traincontrol.util.I18n;

/**
 * On a CS3 an MM2 and a DCC article can share an address, and its pages name only the address.  Each tile there takes
 * the protocol of the article the station's own track plan (`/app/api/gbs`) puts there; a tile the plan cannot tell
 * keeps the first article's protocol, with one warning per address and page while the parser lives (OB-402).  The read
 * does not depend on the station's type, which a failed detection leaves unknown (SVL2-B1).  A download writes each
 * square's protocol (OB-465), and a save writes every accessory's, MM2 included (OB-402).
 *
 * The fixture `test/cs3_v261` is the owner's CS3 plus (2.6.1): W 14 (DCC) and W 15 (MM2) are both address 25 on the
 * grid page Main Layout, at (4,7) and (5,9); W 11 (DCC, a double slip at (16,6) and (17,6)) and W 1R (MM2, on the free
 * page Startpackung at (10,4)) are both address 1.  A stand-in station on the loopback address serves it and counts the
 * requests.  Never talks to a real station.
 */
public class testTwoProtocolsOnOneAddress
{
    private static final String FIXTURE = "test/cs3_v261";

    private static HttpServer web;
    private static String address;
    private static File scratch;

    /** What the stand-in serves at a path in place of the fixture's file; a null body is a 404. */
    private static final Map<String, byte[]> served = java.util.Collections.synchronizedMap(new HashMap<>());

    /** Requests by path. */
    private static final Map<String, Integer> asked = new ConcurrentHashMap<>();

    /** A path the stand-in drops the connection on, with no answer; null for none. */
    private static volatile String dropped;

    // What the parser printed: with no control station it logs to the console
    private static final ByteArrayOutputStream console = new ByteArrayOutputStream();
    private static PrintStream consoleWas;

    // The parser of the last read, for a second read
    private static CS2File parser;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        I18n.setLocale(Locale.ENGLISH);
        scratch = Files.createTempDirectory("tc-two-protocols").toFile();

        web = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        web.createContext("/", exchange ->
        {
            String path = exchange.getRequestURI().getPath();
            asked.merge(path, 1, Integer::sum);

            if (path.equals(dropped))
            {
                exchange.close();
                throw new IllegalStateException("the stand-in drops the connection");
            }

            File file = new File(FIXTURE + (path.startsWith("/app/api/") ? path + ".json" : path));
            byte[] body = served.containsKey(path) ? served.get(path) : file.isFile() ? Files.readAllBytes(file.toPath())
                : null;

            if (body == null)
            {
                exchange.sendResponseHeaders(404, -1);
            }
            else
            {
                exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);

                try (OutputStream out = exchange.getResponseBody())
                {
                    out.write(body);
                }
            }

            exchange.close();
        });
        web.start();
        address = "127.0.0.1:" + web.getAddress().getPort();

        consoleWas = System.out;
        System.setOut(new PrintStream(new OutputStream()
        {
            @Override
            public void write(int b)
            {
                console.write(b);
                consoleWas.write(b);
            }
        }, true));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        System.setOut(consoleWas);
        web.stop(0);

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(scratch.toPath()))
        {
            walk.sorted(java.util.Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(File::delete);
        }
    }

    @BeforeMethod
    public void reset()
    {
        served.clear();
        asked.clear();
        dropped = null;
        console.reset();
    }

    private static int requests(String path)
    {
        return asked.getOrDefault(path, 0);
    }

    /** The station's pages, read as syncLayouts reads them: its own accessory file, then its pages. */
    private static List<LayoutDiagram> read() throws Exception
    {
        parser = new CS2File(address, null);
        return parser.parseLayout(parser.getMagList(true));
    }

    /** A folder's pages, read as a local copy is read. */
    private static List<LayoutDiagram> read(File folder) throws Exception
    {
        parser = new CS2File(address, null);
        parser.setLayoutDataLoc("file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/");
        return parser.parseLayout(parser.getMagList(true));
    }

    private static LayoutDiagram page(List<LayoutDiagram> pages, String name)
    {
        for (LayoutDiagram d : pages)
        {
            if (d.getName().equals(name)) return d;
        }

        throw new AssertionError("no page " + name);
    }

    private static accessoryDecoderType protocol(List<LayoutDiagram> pages, String name, int x, int y)
    {
        return page(pages, name).getComponent(x, y).getProtocol();
    }

    private static List<String> warnings()
    {
        List<String> out = new ArrayList<>();

        for (String line : new String(console.toByteArray(), StandardCharsets.UTF_8).split("\r?\n"))
        {
            if (line.contains("share address")) out.add(line);
        }

        return out;
    }

    /** Reads the station and checks W 14's and W 15's squares at address 25 and how many warnings came. */
    private static List<LayoutDiagram> readAndCheck(accessoryDecoderType at47, accessoryDecoderType at59, int warned)
        throws Exception
    {
        List<LayoutDiagram> pages = read();

        assertEquals(protocol(pages, "Main Layout", 4, 7), at47, "the tile at (4,7), W 14's square");
        assertEquals(protocol(pages, "Main Layout", 5, 9), at59, "the tile at (5,9), W 15's square");
        assertEquals(warnings().size(), warned, "warnings: " + warnings());
        return pages;
    }

    private static byte[] fixture(String path) throws IOException
    {
        return Files.readAllBytes(new File(FIXTURE + (path.startsWith("/app/api/") ? path + ".json" : path)).toPath());
    }

    /** Serves the fixture's gbs.json changed by `change`. */
    private static void gbs(UnaryOperator<JSONObject> change) throws IOException
    {
        JSONObject doc = new JSONObject(new String(fixture("/app/api/gbs"), StandardCharsets.UTF_8));
        served.put("/app/api/gbs", change.apply(doc).toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject plan(JSONObject doc, String name)
    {
        JSONArray plans = doc.getJSONArray("plans");

        for (int i = 0; i < plans.length(); i++)
        {
            if (name.equals(plans.getJSONObject(i).getString("name"))) return plans.getJSONObject(i);
        }

        throw new AssertionError("no plan " + name);
    }

    /** Serves gbs.json with the page's articles passed through `keep` (false drops one) or renamed by `renames`. */
    private static void articles(String page, java.util.function.Predicate<String> keep, Map<String, String> renames)
        throws IOException
    {
        gbs(doc ->
        {
            JSONArray all = plan(doc, page).getJSONArray("articles");

            for (int i = all.length() - 1; i >= 0; i--)
            {
                String name = all.getJSONObject(i).getString("internname");

                if (!keep.test(name)) all.remove(i);
                else if (renames.containsKey(name)) all.getJSONObject(i).put("internname", renames.get(name));
            }

            return doc;
        });
    }

    /** Serves gbs.json with an article added to a page's plan. */
    private static void addArticle(String page, String internname) throws IOException
    {
        gbs(doc ->
        {
            plan(doc, page).getJSONArray("articles").put(new JSONObject().put("internname", internname).put("x", 100)
                .put("y", 100));
            return doc;
        });
    }

    /** The fixture's magnetartikel.cs2 with `from` replaced by `to`. */
    private static void mags(String from, String to) throws IOException
    {
        String path = "/config/magnetartikel.cs2";
        String text = new String(served.containsKey(path) ? served.get(path) : fixture(path), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
        assertTrue(text.contains(from), "the fixture has " + from);
        served.put(path, text.replace(from, to).getBytes(StandardCharsets.UTF_8));
    }

    /** W 14 and W 15, one grid page, address 25: told apart by their squares, with one fetch of the plans. */
    @Test
    public void testAGridPagePairIsToldApartBySquare() throws Exception
    {
        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.MM2, 0);

        assertEquals(requests("/app/api/gbs"), 1, "one fetch of the plans for the whole parse: " + asked);
    }

    /** W 11 and W 1R, address 1 on two pages: told apart by which page's plan holds each. */
    @Test
    public void testAPairOnTwoPagesIsToldApartByPage() throws Exception
    {
        List<LayoutDiagram> pages = read();

        assertEquals(protocol(pages, "Startpackung", 10, 4), accessoryDecoderType.MM2, "W 1R on Startpackung");
        assertEquals(protocol(pages, "Main Layout", 16, 6), accessoryDecoderType.DCC, "W 11, the slip's first square");
        assertEquals(protocol(pages, "Main Layout", 17, 6), accessoryDecoderType.DCC, "W 11, its second square");
    }

    /** Both address-1 articles on the free plan page: no grid to tell them, so the first's protocol and a warning. */
    @Test
    public void testAPairOnAPlanPageKeepsTheFirstAndWarns() throws Exception
    {
        addArticle("Startpackung", "W#2011");

        List<LayoutDiagram> pages = readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.MM2, 1);

        assertEquals(protocol(pages, "Startpackung", 10, 4), accessoryDecoderType.DCC, "the first article's, as before");
        assertTrue(warnings().get(0).contains("W 11 DCC, W 1R MM2") && warnings().get(0).contains("Startpackung")
            && warnings().get(0).contains("10,4"), "it names both articles, the page and the square: " + warnings());
    }

    /**
     * Plans that will not read (not found, empty, not JSON, no plans): no exception, the first article's protocol, and
     * a warning per address with two protocols and page: 25 and 1 on Main Layout, 1 on Startpackung.
     */
    @Test
    public void testPlansThatWillNotReadKeepTheFirstAndWarn() throws Exception
    {
        for (byte[] body : new byte[][] {null, new byte[0], "not json".getBytes(StandardCharsets.UTF_8),
            "{\"plans\":[]}".getBytes(StandardCharsets.UTF_8)})
        {
            reset();
            served.put("/app/api/gbs", body);

            List<LayoutDiagram> pages = readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.DCC, 3);

            assertEquals(protocol(pages, "Startpackung", 10, 4), accessoryDecoderType.DCC, "as before");
            assertEquals(requests("/app/api/gbs"), 1, "asked once");
        }
    }

    /** The page's plan missing: the page cannot be told, the other page still is. */
    @Test
    public void testAPageMissingFromThePlansKeepsTheFirstAndWarns() throws Exception
    {
        gbs(doc ->
        {
            plan(doc, "Main Layout").put("name", "Renamed");
            return doc;
        });

        // 25 and 1 on Main Layout
        List<LayoutDiagram> pages = readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.DCC, 2);

        assertEquals(protocol(pages, "Startpackung", 10, 4), accessoryDecoderType.MM2, "the other page is still told");
    }

    /** No article of a single-protocol address on the page: its grid cannot be placed, so its pair is not told. */
    @Test
    public void testAGridThatCannotBePlacedKeepsTheFirstAndWarns() throws Exception
    {
        articles("Main Layout", name -> name.equals("W#2014") || name.equals("W#2015") || name.equals("W#2011"),
            new HashMap<>());

        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.DCC, 1);
        assertTrue(warnings().get(0).contains("4,7 5,9"), "one warning names both squares: " + warnings());
    }

    /** The plan's word stands: with W 15 not on Main Layout's plan, both tiles there are W 14's. */
    @Test
    public void testAnArticleMissingFromThePlanIsNotOnThePage() throws Exception
    {
        articles("Main Layout", name -> !name.equals("W#2015"), new HashMap<>());

        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.DCC, 0);
        assertEquals(requests("/app/api/gbs"), 1, "the plan was asked: " + asked);
    }

    /** A third article at 25, on another page: Main Layout's pair is told as before. */
    @Test
    public void testAThirdArticleOnAnotherPage() throws Exception
    {
        mags("artikel\n .name=W 15\n", "artikel\n .name=W 16\n .id=25\n .typ=linksweiche\n .dectyp=mm2\nartikel\n .name=W 15\n");
        addArticle("Startpackung", "W#2016");

        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.MM2, 0);
    }

    /** A rename on the station renames the article in both files; the match is by name, so it still holds. */
    @Test
    public void testARenamedArticleIsStillFound() throws Exception
    {
        mags(".name=W 15\n", ".name=Weiche \u00dc 15\n");

        Map<String, String> renames = new HashMap<>();
        renames.put("W#2015", "Weiche#20#C3#9C#2015");
        articles("Main Layout", name -> true, renames);

        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.MM2, 0);
    }

    /**
     * Plans that fail to arrive (a dropped connection, not a 404) fail the read, so the sync fails before it clears the
     * diagram rather than building the tiles with the first article's protocol.
     *
     * MUTATION: drop the `trackPlanFailure` throw at the end of `CS2File.parseLayout`, and the read returns pages.
     */
    @Test
    public void testPlansThatFailToArriveFailTheRead() throws Exception
    {
        dropped = "/app/api/gbs";

        try
        {
            List<LayoutDiagram> pages = read();

            fail("plans that never arrived gave " + pages.size() + " pages built without them");
        }
        catch (IOException expected)
        {
            // what we want: the caller fails before it clears anything
        }
    }

    /** No address with two protocols: the plans are never asked for, and nothing is warned. */
    @Test
    public void testNoAddressWithTwoProtocolsNoFetch() throws Exception
    {
        mags(".name=W 15\n .id=25\n .typ=linksweiche\n .stellung=1\n .schaltzeit=200\n .dectyp=mm2",
            ".name=W 15\n .id=25\n .typ=linksweiche\n .stellung=1\n .schaltzeit=200\n .dectyp=dcc");
        mags(".name=W 1R\n .id=1\n .typ=rechtsweiche\n .stellung=0\n .schaltzeit=200\n .dectyp=mm2",
            ".name=W 1R\n .id=1\n .typ=rechtsweiche\n .stellung=0\n .schaltzeit=200\n .dectyp=dcc");

        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.DCC, 0);

        assertEquals(requests("/app/api/gbs"), 0, "all requests: " + asked);
    }

    /**
     * Articles that share a name vote together for where the grid sits: with W 2 the only voter on Main Layout's plan,
     * one W 2 in the accessory list is one vote, too few to place the grid; listed twice, it is two, and the pair at 25
     * is told.
     */
    @Test
    public void testArticlesSharingANameVoteTogether() throws Exception
    {
        java.util.function.Predicate<String> onlyW2Votes = name -> name.equals("W#2014") || name.equals("W#2015")
            || name.equals("W#2011") || name.equals("W#202");

        articles("Main Layout", onlyW2Votes, new HashMap<>());
        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.DCC, 1);

        reset();
        articles("Main Layout", onlyW2Votes, new HashMap<>());
        mags("artikel\n .name=W 2\n", "artikel\n .name=W 2\n .id=9\n .typ=rechtsweiche\n .dectyp=mm2\nartikel\n .name=W 2\n");
        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.MM2, 0);
    }

    /** Each parse reads the plans afresh: a station whose plan changed between two syncs is read as it is now. */
    @Test
    public void testTheNextParseReadsThePlansAgain() throws Exception
    {
        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.MM2, 0);

        articles("Main Layout", name -> !name.equals("W#2015"), new HashMap<>());
        List<LayoutDiagram> pages = parser.parseLayout(parser.getMagList(true));

        assertEquals(requests("/app/api/gbs"), 2, "one fetch per parse: " + asked);
        assertEquals(protocol(pages, "Main Layout", 5, 9), accessoryDecoderType.DCC, "W 15 is gone from the plan");
    }

    /**
     * A slip whose first square is told and whose second is not: the warning names the protocol the untold square
     * keeps.  With W 11 moved off (16,6), W 1R's (MM2) square, the second, (17,6), keeps the first article's DCC.
     */
    @Test
    public void testTheWarningNamesTheUntoldSquaresProtocol() throws Exception
    {
        gbs(doc ->
        {
            JSONArray all = plan(doc, "Main Layout").getJSONArray("articles");
            all.put(new JSONObject().put("internname", "W#201R").put("x", 875).put("y", 455));

            for (int i = 0; i < all.length(); i++)
            {
                if ("W#2011".equals(all.getJSONObject(i).getString("internname")))
                {
                    all.getJSONObject(i).put("x", 35).put("y", 35);
                }
            }

            return doc;
        });

        List<LayoutDiagram> pages = readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.MM2, 1);

        assertEquals(protocol(pages, "Main Layout", 16, 6), accessoryDecoderType.MM2, "W 1R's square");
        assertEquals(protocol(pages, "Main Layout", 17, 6), accessoryDecoderType.DCC, "the untold square");
        assertTrue(warnings().get(0).contains("at 17,6 on page Main Layout") && warnings().get(0).contains("DCC is used"),
            "it names the untold square and the protocol it keeps: " + warnings());
    }

    /** Serves gbs.json with one field of a page's article set, a null value as JSON null. */
    private static void articleField(String page, String internname, String key, Object value) throws IOException
    {
        gbs(doc ->
        {
            JSONArray all = plan(doc, page).getJSONArray("articles");

            for (int i = 0; i < all.length(); i++)
            {
                if (internname.equals(all.getJSONObject(i).getString("internname")))
                {
                    all.getJSONObject(i).put(key, value == null ? JSONObject.NULL : value);
                    return doc;
                }
            }

            throw new AssertionError("no article " + internname);
        });
    }

    /**
     * An article whose position or name will not read costs no page.  W 15's square unread leaves (5,9) untold, with
     * a warning; W 15 named by a number is not on the plan, so both tiles are W 14's; a bad article not at 25 changes
     * nothing.
     */
    @Test
    public void testABadArticleCostsNoPage() throws Exception
    {
        Object[][] cases = {
            {"W#2015", "x", "abc", accessoryDecoderType.DCC, 1}, {"W#2015", "x", null, accessoryDecoderType.DCC, 1},
            {"W#2015", "internname", 7, accessoryDecoderType.DCC, 0}, {"W#206", "x", "abc", accessoryDecoderType.MM2, 0},
            {"_HA#2014", "y", null, accessoryDecoderType.MM2, 0}, {"_HA#2014", "internname", 7, accessoryDecoderType.MM2, 0}
        };

        for (Object[] c : cases)
        {
            reset();
            articleField("Main Layout", (String) c[0], (String) c[1], c[2]);

            readAndCheck(accessoryDecoderType.DCC, (accessoryDecoderType) c[3], (Integer) c[4]);
        }
    }

    /** A refresh, as after each edit, reads the pages again with the same parser and warns no more. */
    @Test
    public void testARefreshWarnsNoMore() throws Exception
    {
        served.put("/app/api/gbs", null);
        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.DCC, 3);

        parser.parseLayout(parser.getMagList(true));

        assertEquals(warnings().size(), 3, "warnings: " + warnings());
    }

    /**
     * A local copy: its squares may have been edited, so the plans are not asked for, and the warning says so rather
     * than that the plan did not say.
     */
    @Test
    public void testALocalCopyNoFetch() throws Exception
    {
        List<LayoutDiagram> pages = read(new File(FIXTURE));

        assertEquals(requests("/app/api/gbs"), 0, "all requests: " + asked);
        assertEquals(protocol(pages, "Main Layout", 5, 9), accessoryDecoderType.DCC);
        assertEquals(warnings().size(), 3, "each address with two protocols is named once per page: " + warnings());
        assertTrue(warnings().stream().allMatch(w -> w.contains("not checked against the Central Station")),
            "the local copy's wording: " + warnings());
    }

    /** The station's type, as a stand-in window and network give it to a start-up sync. */
    private static class StationAt extends NetworkProxy
    {
        StationAt() throws IOException
        {
            super(InetAddress.getLoopbackAddress());
        }

        @Override
        public String getIP()
        {
            return address;
        }

        @Override
        public boolean sendMessage(CANMessage m)
        {
            return true;
        }
    }

    /**
     * A start-up whose device file fails to arrive leaves the station's type unknown, so the station is taken for a
     * CS2, and still builds W 15's tile as MM2: the diagram does not depend on the type (SVL2-B1).  2.8.2's own read
     * built it as DCC, and nothing read it again, a later Sync keeping the diagram it has.
     */
    @Test(timeOut = 120000)
    public void testAStartUpWhoseDeviceFileFailsStillTellsThePairApart() throws Exception
    {
        served.put("/config/fahrstrassen.cs2", "[fahrstrassen]\nversion\n .minor=4\n".getBytes(StandardCharsets.UTF_8));
        served.put("/config/lokomotive.cs2", "[lokomotive]\nversion\n .minor=3\n".getBytes(StandardCharsets.UTF_8));
        dropped = "/config/geraet.vrs";

        View view = (View) Proxy.newProxyInstance(View.class.getClassLoader(), new Class<?>[]{ View.class },
            (proxy, method, args) ->
            {
                switch (method.getName())
                {
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "recording window";
                    case "getAllLocButtonMappings": return new ArrayList<String>();
                    default: return null;
                }
            });

        StationAt network = new StationAt();

        try
        {
            MarklinControlStation model = new MarklinControlStation(network, view, false, false);

            assertTrue(requests("/config/geraet.vrs") > 0, "precondition: the device file was asked for: " + asked);
            assertFalse(model.isCS3(), "precondition: the type stayed unknown, so the station was taken for a CS2");
            assertTrue(model.getNetworkCommState(), "precondition: the start-up sync failed");
            assertEquals(model.getLayout("Main Layout").getComponent(5, 9).getProtocol(), accessoryDecoderType.MM2,
                "W 15's tile after a start-up whose device file did not arrive");
        }
        finally
        {
            Field socket = NetworkProxy.class.getDeclaredField("socket");
            socket.setAccessible(true);
            ((DatagramSocket) socket.get(network)).close();
        }
    }

    // ---- Download Central Station Layout Files (OB-465)

    /** Every addressed switch, signal and uncoupler square of the pages, by page and square. */
    private static Map<String, accessoryDecoderType> protocols(List<LayoutDiagram> pages)
    {
        Map<String, accessoryDecoderType> out = new TreeMap<>();

        for (LayoutDiagram d : pages)
        {
            for (LayoutDiagramComponent c : d.getAll())
            {
                if (c.isAccessory() && c.getAddress() > 0) out.put(d.getName() + " " + c.getX() + "," + c.getY(), c.getProtocol());
            }
        }

        return out;
    }

    private static File download(String name) throws Exception
    {
        File copy = new File(scratch, name);
        CS2File station = new CS2File(address, null);
        station.downloadCS2Layout(copy);
        return copy;
    }

    /**
     * Read from the station, downloaded, and opened as a local layout: every square has the protocol it had on the
     * station, and each page differs from what the station served only in its `.prot` lines.
     *
     * MUTATION: drop the `withSavedProtocols` call from `CS2File.downloadCS2Layout`, and W 15 and W 1R open as DCC.
     */
    @Test
    public void testEverySquareOpensWithTheProtocolItHadOnTheStation() throws Exception
    {
        Map<String, accessoryDecoderType> station = protocols(read());
        assertEquals(station.get("Main Layout 5,9"), accessoryDecoderType.MM2, "precondition: W 15 read from the station");
        assertEquals(station.get("Startpackung 10,4"), accessoryDecoderType.MM2, "precondition: W 1R read from the station");

        File copy = download("downloaded");

        assertEquals(protocols(read(copy)), station, "every addressed square, downloaded and opened, against the station's");

        for (String name : new String[] {"Main Layout", "Startpackung", "Main", "Something"})
        {
            String path = "/config/gleisbilder/" + name + ".cs2";
            String written = new String(Files.readAllBytes(new File(copy, path).toPath()), StandardCharsets.UTF_8);

            assertEquals(written.replace("\r\n", "\n").replaceAll("(?m)^ \\.prot=.*\n", "").trim(),
                new String(fixture(path), StandardCharsets.UTF_8).replace("\r\n", "\n").replaceAll("(?m)^ \\.prot=.*\n", "").trim(),
                name + ": the copy differs from what the station served other than in .prot");
        }
    }

    /**
     * A station changed after it was read: W 14 and W 15 exchanged in the CS3's plan.  The copy downloaded then has every
     * square as the station has it today.
     */
    @Test
    public void testACopyDownloadedAfterTheStationChangedMatchesTheStationToday() throws Exception
    {
        read();

        String w14 = "\"internname\":\"W#2014\",\"x\":35,\"y\":525";
        String w15 = "\"internname\":\"W#2015\",\"x\":105,\"y\":665";
        String plan = new String(fixture("/app/api/gbs"), StandardCharsets.UTF_8);
        assertTrue(plan.contains(w14) && plan.contains(w15), "precondition: the fixture's plan moved W 14 or W 15");
        served.put("/app/api/gbs", plan.replace(w14, "W14HERE").replace(w15, w14.replace("2014", "2015"))
            .replace("W14HERE", w15.replace("2015", "2014")).getBytes(StandardCharsets.UTF_8));

        File copy = download("downloaded-after-a-change");
        Map<String, accessoryDecoderType> today = protocols(read());

        assertEquals(today.get("Main Layout 4,7"), accessoryDecoderType.MM2, "precondition: W 15 is now at (4,7)");
        assertEquals(today.get("Main Layout 5,9"), accessoryDecoderType.DCC, "precondition: W 14 is now at (5,9)");
        assertEquals(protocols(read(copy)), today, "a copy downloaded after the station changed, against the station today");
    }

    /**
     * A download whose CS3 plan fails to arrive writes nothing into the folder, not pages whose two-protocol squares
     * carry the accessory list's guess.
     */
    @Test
    public void testADownloadWhosePlanFailsWritesNothing() throws Exception
    {
        File copy = new File(scratch, "downloaded-without-a-plan");
        File index = new File(copy, "config/gleisbild.cs2");
        assertTrue(index.getParentFile().mkdirs(), "could not build the fixture");
        byte[] before = "the layout there before".getBytes(StandardCharsets.UTF_8);
        Files.write(index.toPath(), before);

        dropped = "/app/api/gbs";

        try
        {
            new CS2File(address, null).downloadCS2Layout(copy);

            fail("a download whose plan did not arrive was reported as done");
        }
        catch (IOException expected)
        {
            // what we want
        }

        List<String> files = new ArrayList<>();

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(copy.toPath()))
        {
            walk.filter(Files::isRegularFile).forEach(f -> files.add(copy.toPath().relativize(f).toString()));
        }

        assertEquals(files, java.util.Collections.singletonList(new File("config/gleisbild.cs2").getPath()),
            "a download whose plan did not arrive wrote into the folder");
        assertEquals(Files.readAllBytes(index.toPath()), before, "a download whose plan did not arrive replaced the index");
    }

    // ---- Saving (OB-402)

    /** A fresh copy of the fixture to save into. */
    private static File sandbox(String name) throws Exception
    {
        File copy = new File(scratch, name);
        java.nio.file.Path from = new File(FIXTURE).toPath();

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(from))
        {
            for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) walk::iterator)
            {
                java.nio.file.Path to = copy.toPath().resolve(from.relativize(p).toString());

                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to);
            }
        }

        return copy;
    }

    /**
     * W 15 at Main Layout (5,9) and W 1R at Startpackung (10,4), set to MM2 and saved in a local copy, read back as MM2;
     * the DCC articles at their addresses and an ordinary MM2 turnout are unchanged.
     *
     * MUTATION: write `.prot` only for a protocol other than MM2 (LayoutDiagramComponent.savedProtocol), and every MM2
     * tile at an address with two protocols reads back as DCC.
     */
    @Test
    public void testTheMM2TilesReadBackAsMM2() throws Exception
    {
        File folder = sandbox("mm2-tiles");
        List<LayoutDiagram> pages = read(folder);

        assertEquals(protocol(pages, "Main Layout", 5, 9), accessoryDecoderType.DCC,
            "the fixture's premise: W 15's tile reads as the DCC article's protocol");

        page(pages, "Main Layout").getComponent(5, 9).setProtocol(accessoryDecoderType.MM2);
        page(pages, "Main Layout").saveChanges(null, false);
        page(pages, "Startpackung").getComponent(10, 4).setProtocol(accessoryDecoderType.MM2);
        page(pages, "Startpackung").saveChanges(null, false);

        List<LayoutDiagram> again = read(folder);

        assertEquals(protocol(again, "Main Layout", 5, 9), accessoryDecoderType.MM2, "W 15");
        assertEquals(protocol(again, "Startpackung", 10, 4), accessoryDecoderType.MM2, "W 1R");
        assertEquals(protocol(again, "Main Layout", 4, 7), accessoryDecoderType.DCC, "W 14");
        assertEquals(protocol(again, "Main Layout", 16, 6), accessoryDecoderType.DCC, "W 11");
        assertEquals(protocol(again, "Startpackung", 9, 8), accessoryDecoderType.MM2, "W 2");
    }

    /** A tile drawn new at 25, and W 2's tile re-addressed to 1, both MM2 as the editor sets them, read back as MM2. */
    @Test
    public void testATileDrawnOrReaddressedInTheEditorReadsBackAsMM2() throws Exception
    {
        File folder = sandbox("drawn-tiles");
        LayoutDiagram main = page(read(folder), "Main Layout");
        int x = 0;

        while (main.getComponent(x, 0) != null) x++;

        // As the editor's palette makes a tile (protocol MM2), then the address popup sets 25, MM2
        main.addComponent(LayoutDiagramComponent.componentType.SWITCH_RIGHT, x, 0, 0, 0, 1, 2, accessoryDecoderType.MM2,
            null);
        main.getComponent(x, 0).setLogicalAddress(25, accessoryDecoderType.MM2, false);
        main.saveChanges(null, false);

        LayoutDiagram start = page(read(folder), "Startpackung");
        start.getComponent(9, 8).setLogicalAddress(1, accessoryDecoderType.MM2, false);
        start.saveChanges(null, false);

        List<LayoutDiagram> again = read(folder);

        assertEquals(protocol(again, "Main Layout", x, 0), accessoryDecoderType.MM2, "the new tile at 25");
        assertEquals(protocol(again, "Startpackung", 9, 8), accessoryDecoderType.MM2, "W 2's tile, now at 1");
    }

    /**
     * The second square of the W 11 slip (Main Layout (17,6), address 1), set to MM2 and saved, then read, copied as
     * the editor copies a tile for an undo state, a move or a paste, and saved again, keeps MM2.
     */
    @Test
    public void testAFixedTileSurvivesALaterSaveAndACopy() throws Exception
    {
        File folder = sandbox("fixed-tile");
        LayoutDiagram main = page(read(folder), "Main Layout");
        main.getComponent(17, 6).setProtocol(accessoryDecoderType.MM2);
        main.saveChanges(null, false);

        LayoutDiagram later = page(read(folder), "Main Layout");
        later.addComponent(new LayoutDiagramComponent(later.getComponent(17, 6)), 17, 6);
        later.saveChanges(null, false);

        assertEquals(protocol(read(folder), "Main Layout", 17, 6), accessoryDecoderType.MM2,
            "(17,6) after a later save of a copy of it");
    }

    /**
     * On a CS2's pages (`test/layout`) and the CS3 copy's: every addressed switch, signal and uncoupler tile writes
     * `.prot`, MM2 included; every other tile writes `.prot` only for a protocol other than MM2, so track writes none;
     * and the saved pages read back with every tile's protocol as before.
     *
     * MUTATION: write `.prot=MM2` on every tile (drop the accessory test from `savedProtocol`), and a track tile
     * writes it.
     */
    @Test
    public void testASaveWritesEveryAccessorysProtocol() throws Exception
    {
        File cs2 = new File(scratch, "cs2-layout");
        java.nio.file.Path from = new File("test/layout").toPath();

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(from))
        {
            for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) walk::iterator)
            {
                java.nio.file.Path to = cs2.toPath().resolve(from.relativize(p).toString());

                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to);
            }
        }

        File cs3 = sandbox("cs3-layout");
        Map<String, accessoryDecoderType> before = new HashMap<>();
        int mm2Accessories = 0;

        for (File folder : new File[] {cs2, cs3})
        {
            for (LayoutDiagram d : read(folder))
            {
                for (LayoutDiagramComponent c : d.getAll())
                {
                    boolean accessory = c.getAddress() > 0 && c.isAccessory();
                    String where = folder.getName() + " " + d.getName() + " " + c.getX() + "," + c.getY() + " " + c.getType();

                    assertEquals(c.exportToCS2TextFormat().contains(" .prot=" + c.getProtocol() + "\n"),
                        accessory || c.getProtocol() != accessoryDecoderType.MM2, where + ": " + c.exportToCS2TextFormat());
                    mm2Accessories += accessory && c.getProtocol() == accessoryDecoderType.MM2 ? 1 : 0;
                    before.put(where, c.getProtocol());
                }

                d.saveChanges(null, false);
            }
        }

        assertTrue(mm2Accessories > 0, "the fixtures have MM2 accessory tiles");

        for (File folder : new File[] {cs2, cs3})
        {
            for (LayoutDiagram d : read(folder))
            {
                for (LayoutDiagramComponent c : d.getAll())
                {
                    String where = folder.getName() + " " + d.getName() + " " + c.getX() + "," + c.getY() + " " + c.getType();
                    assertEquals(c.getProtocol(), before.get(where), where + ": read back as before the save");
                }
            }
        }
    }
}
