package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.logging.Handler;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import support.StandInStation;

/**
 * On a CS3, a tile at an address with an MM2 and a DCC article takes the protocol of the article the station's own
 * track plan (`/app/api/gbs`) puts there; a tile the plan cannot tell keeps the first article's protocol, with a
 * warning naming the articles, the page and the squares, once per page while the parser lives (one sync).  A plan that
 * will not read, in whole or in part, costs no page (OB-402).
 *
 * The fixture `test/cs3_v261` is Adam's CS3 plus (2.6.1): W 14 (DCC) and W 15 (MM2) are both address 25 on the grid
 * page Main Layout, at (4,7) and (5,9); W 11 (DCC, a double slip) and W 1R (MM2, on the free page Startpackung) are
 * both address 1.  A stand-in station serves it and counts the requests.
 *
 * MUTATION: make `resolveTwoProtocolTiles` return at once, and W 15 and W 1R read as DCC.
 *
 * @author Adam
 */
public class testACS3TileTakesTheProtocolItsPlanGivesIt
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static StandInStation station;
    private static final List<String> logged = new ArrayList<>();
    private static final Handler tap = support.LogTap.messagesInto(logged);

    // The parser of the last read, for its page counts and a second read
    private static CS2File parser;

    /** What the stand-in serves at a path in place of the fixture's file; a null body is a 404. */
    private static final Map<String, byte[]> served = new HashMap<>();

    /** A path the stand-in drops the connection on, with no answer; null for none. */
    private static volatile String dropped;

    /** A whole CS3 for a full sync, which drops the connection on its device file this many more times. */
    private static StandInStation wholeStation;
    private static final java.util.concurrent.atomic.AtomicInteger deviceDrops =
        new java.util.concurrent.atomic.AtomicInteger();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();
        model = support.TestStation.simulated();
        station = new StandInStation((path, n) ->
        {
            if (path.equals(dropped)) throw new IllegalStateException("the stand-in drops the connection");

            return served.containsKey(path) ? served.get(path) : fixture(path);
        });

        java.util.function.BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("260",
            "{\"softwareVersion\":\"2.6.1 (Build 3)\"}");

        wholeStation = new StandInStation((path, n) ->
        {
            if (path.equals("/config/geraet.vrs") && deviceDrops.getAndUpdate(k -> Math.max(0, k - 1)) > 0)
            {
                throw new IllegalStateException("the stand-in drops the connection");
            }

            return cs3.apply(path, n);
        });

        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(tap);

        if (wholeStation != null) wholeStation.close();

        StandInStation.tearDown(station, model, sandbox);
    }

    @BeforeMethod
    public void reset()
    {
        served.clear();
        station.forget();

        // The model drops a message equal to the one before it, so a warning repeated from the last claim would be lost
        model.log("next claim " + System.nanoTime());
        logged.clear();
    }

    @AfterMethod(alwaysRun = true)
    public void backToCS2() throws Exception
    {
        StandInStation.setCS3(model, false);
    }

    private static byte[] fixture(String path)
    {
        return StandInStation.file("test/cs3_v261" + (path.startsWith("/app/api/") ? path + ".json" : path));
    }

    /** The station's pages, read as syncLayouts reads them: its own accessory file, then its pages. */
    private static List<LayoutDiagram> read(boolean cs3) throws Exception
    {
        StandInStation.setCS3(model, cs3);
        parser = new CS2File(station.address(), model);
        return parser.parseLayout(parser.getMagList(true));
    }

    private static accessoryDecoderType protocol(List<LayoutDiagram> pages, String page, int x, int y)
    {
        for (LayoutDiagram d : pages)
        {
            if (d.getName().equals(page))
            {
                return d.getComponent(x, y).getProtocol();
            }
        }

        fail("no page " + page);
        return null;
    }

    private static List<String> warnings()
    {
        List<String> out = new ArrayList<>();

        for (String line : logged)
        {
            if (line.contains("share address"))
            {
                out.add(line);
            }
        }

        return out;
    }

    /** Reads the station on a CS3 and checks W 14's and W 15's squares at address 25 and how many warnings came. */
    private static List<LayoutDiagram> readAndCheck(accessoryDecoderType at47, accessoryDecoderType at59, int warned)
        throws Exception
    {
        List<LayoutDiagram> pages = read(true);

        assertEquals(protocol(pages, "Main Layout", 4, 7), at47, "the tile at (4,7), W 14's square");
        assertEquals(protocol(pages, "Main Layout", 5, 9), at59, "the tile at (5,9), W 15's square");
        assertEquals(warnings().size(), warned, "warnings: " + warnings());
        assertEquals(parser.getPagesThatCouldNotBeRead(), 0, "every page read");
        return pages;
    }

    /** Serves the fixture's gbs.json changed by `change`. */
    private static void gbs(UnaryOperator<JSONObject> change)
    {
        JSONObject doc = new JSONObject(new String(fixture("/app/api/gbs"), StandardCharsets.UTF_8));
        served.put("/app/api/gbs", change.apply(doc).toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject plan(JSONObject doc, String name)
    {
        JSONArray plans = doc.getJSONArray("plans");

        for (int i = 0; i < plans.length(); i++)
        {
            if (name.equals(plans.getJSONObject(i).getString("name")))
            {
                return plans.getJSONObject(i);
            }
        }

        throw new AssertionError("no plan " + name);
    }

    /** Serves gbs.json with the page's articles passed through `keep` (false drops one) or renamed by `renames`. */
    private static void articles(String page, java.util.function.Predicate<String> keep, Map<String, String> renames)
    {
        gbs(doc ->
        {
            JSONArray all = plan(doc, page).getJSONArray("articles");

            for (int i = all.length() - 1; i >= 0; i--)
            {
                String name = all.getJSONObject(i).getString("internname");

                if (!keep.test(name))
                {
                    all.remove(i);
                }
                else if (renames.containsKey(name))
                {
                    all.getJSONObject(i).put("internname", renames.get(name));
                }
            }

            return doc;
        });
    }

    /** Serves gbs.json with an article added to a page's plan. */
    private static void addArticle(String page, String internname)
    {
        gbs(doc ->
        {
            plan(doc, page).getJSONArray("articles").put(new JSONObject().put("internname", internname).put("x", 100)
                .put("y", 100));
            return doc;
        });
    }

    /** The fixture's magnetartikel.cs2 with `from` replaced by `to`. */
    private static void mags(String from, String to)
    {
        String text = new String(served.containsKey("/config/magnetartikel.cs2") ? served.get("/config/magnetartikel.cs2")
            : fixture("/config/magnetartikel.cs2"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue(text.contains(from), "the fixture has " + from);
        served.put("/config/magnetartikel.cs2", text.replace(from, to).getBytes(StandardCharsets.UTF_8));
    }

    /** W 14 and W 15, one grid page, address 25: told apart by their squares, with one fetch of the plans. */
    @Test
    public void testAGridPagePairIsToldApartBySquare() throws Exception
    {
        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.MM2, 0);

        assertEquals(station.requests("/app/api/gbs"), 1, "one fetch of the plans for the whole parse: " + station.asked());
    }

    /** W 11 and W 1R, address 1 on two pages: told apart by which page's plan holds each. */
    @Test
    public void testAPairOnTwoPagesIsToldApartByPage() throws Exception
    {
        List<LayoutDiagram> pages = read(true);

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
     * A station whose plans will not read: no exception, the first article's protocol, a warning per address with two
     * protocols and page: 25 and 1 on Main Layout, 1 on Startpackung.
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
            assertEquals(station.requests("/app/api/gbs"), 1, "asked once");
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
            new HashMap<String, String>());

        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.DCC, 1);
        assertTrue(warnings().get(0).contains("4,7 5,9"), "one warning names both squares: " + warnings());
    }

    /** The plan's word stands: with W 15 not on Main Layout's plan, both tiles there are W 14's. */
    @Test
    public void testAnArticleMissingFromThePlanIsNotOnThePage() throws Exception
    {
        articles("Main Layout", name -> !name.equals("W#2015"), new HashMap<String, String>());

        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.DCC, 0);
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
        mags(".name=W 15\n", ".name=Weiche Ü 15\n");

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
            List<LayoutDiagram> pages = read(true);

            fail("plans that never arrived gave " + pages.size() + " pages built without them");
        }
        catch (java.io.IOException expected)
        {
            // what we want: the caller fails before it clears anything
        }
        finally
        {
            dropped = null;
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

        assertEquals(station.requests("/app/api/gbs"), 0, "all requests: " + station.asked());
    }

    /**
     * Articles that share a name vote together for where the grid sits: with W 2 the only voter on Main Layout's plan,
     * one W 2 in the accessory list is one vote, too few to place the grid; listed twice, it is two, and the pair at 25
     * is told.
     *
     * MUTATION: keep only the last article of a name in the vote (CS2File.plannedArticles), and the pair is
     * not told.
     */
    @Test
    public void testArticlesSharingANameVoteTogether() throws Exception
    {
        java.util.function.Predicate<String> onlyW2Votes = name -> name.equals("W#2014") || name.equals("W#2015")
            || name.equals("W#2011") || name.equals("W#202");

        articles("Main Layout", onlyW2Votes, new HashMap<String, String>());
        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.DCC, 1);

        reset();
        articles("Main Layout", onlyW2Votes, new HashMap<String, String>());
        mags("artikel\n .name=W 2\n",
            "artikel\n .name=W 2\n .id=9\n .typ=rechtsweiche\n .dectyp=mm2\nartikel\n .name=W 2\n");
        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.MM2, 0);
    }

    /**
     * Each parse reads the plans afresh: a station whose plan changed between two syncs is read as it is now.
     *
     * MUTATION: keep the plans from one parse to the next (drop `this.trackPlan = null` from CS2File.parseLayout), and
     * the second parse neither asks nor sees W 15 gone.
     */
    @Test
    public void testTheNextParseReadsThePlansAgain() throws Exception
    {
        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.MM2, 0);

        articles("Main Layout", name -> !name.equals("W#2015"), new HashMap<String, String>());
        List<LayoutDiagram> pages = parser.parseLayout(parser.getMagList(true));

        assertEquals(station.requests("/app/api/gbs"), 2, "one fetch per parse: " + station.asked());
        assertEquals(protocol(pages, "Main Layout", 5, 9), accessoryDecoderType.DCC, "W 15 is gone from the plan");
    }

    /**
     * A slip whose first square is told and whose second is not: the warning names the protocol the untold square
     * keeps.  With W 11 moved off (16,6), W 1R's (MM2) square, the second, (17,6), keeps the first article's DCC.
     *
     * MUTATION: take the warning's protocol from the address's first tile again (CS2File.resolveTwoProtocolTiles), and
     * it says MM2.
     */
    @Test
    public void testTheWarningNamesTheUntoldSquaresProtocol() throws Exception
    {
        gbs(doc ->
        {
            plan(doc, "Main Layout").getJSONArray("articles").put(new JSONObject().put("internname", "W#201R")
                .put("x", 875).put("y", 455));
            JSONArray all = plan(doc, "Main Layout").getJSONArray("articles");

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

    /**
     * Not a CS3, though its accessory file repeats an address with two protocols: as before, the plans are never asked
     * for, nothing is warned, and every tile has the protocol it had.
     *
     * MUTATION: find the addresses with two protocols whatever the station (drop `control.isCS3()` from
     * CS2File.parseLayout), and the plans are asked for and the tiles warned about.
     */
    @Test
    public void testNotACS3NoFetch() throws Exception
    {
        List<LayoutDiagram> pages = read(false);

        assertEquals(station.requests("/app/api/gbs"), 0, "all requests: " + station.asked());
        assertEquals(warnings().size(), 0, "warnings: " + warnings());
        assertEquals(protocol(pages, "Main Layout", 5, 9), accessoryDecoderType.DCC);
        assertEquals(protocol(pages, "Startpackung", 10, 4), accessoryDecoderType.DCC);
    }

    /** Serves gbs.json with one field of a page's article set, a null value as JSON null. */
    private static void articleField(String page, String internname, String key, Object value)
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
     *
     * MUTATION: read the articles with getString and getDouble (CS3TrackPlan.Page), and Main Layout is lost.
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

    /**
     * A refresh, as after each edit, reads the pages again with the same parser and warns no more.
     *
     * MUTATION: drop the `twoProtocolsWarned` check (CS2File.resolveTwoProtocolTiles), and the refresh warns three
     * times more.
     */
    @Test
    public void testARefreshWarnsNoMore() throws Exception
    {
        served.put("/app/api/gbs", null);
        readAndCheck(accessoryDecoderType.DCC, accessoryDecoderType.DCC, 3);

        parser.parseLayout(parser.getMagList(true));

        assertEquals(warnings().size(), 3, "warnings: " + warnings());
    }

    /**
     * A local copy, even on a CS3: its squares may have been edited, so the plans are not asked for, and the warning
     * says so rather than that the plan did not say.
     *
     * MUTATION: treat every folder as the station's (`fromStation = true` in CS2File.resolveTwoProtocolTiles), and the
     * plans are asked for and the warning says the plan did not say.
     */
    @Test
    public void testALocalCopyNoFetch() throws Exception
    {
        StandInStation.setCS3(model, true);
        parser = new CS2File(station.address(), model);
        parser.setLayoutDataLoc("file:///" + new File("test/cs3_v261").getAbsolutePath() + "/");

        List<LayoutDiagram> pages = parser.parseLayout(parser.getMagList(true));

        assertEquals(station.requests("/app/api/gbs"), 0, "all requests: " + station.asked());
        assertEquals(protocol(pages, "Main Layout", 5, 9), accessoryDecoderType.DCC);
        assertEquals(warnings().size(), 3, "each address with two protocols is named once per page: " + warnings());
        assertTrue(warnings().stream().allMatch(w -> w.contains("not checked against the Central Station")),
            "the local copy's wording: " + warnings());
    }

    /**
     * A full sync with the whole CS3 from the layout folder `folder` ("" for the station's diagram), as a fresh start
     * makes it: no diagram, type unknown, the device file dropped twice (the JDK repeats a dropped request once).
     */
    private static int firstSyncWithTheDeviceFileDropped(String folder) throws Exception
    {
        org.traincontrol.gui.TrainControlUI.getPrefs().put(org.traincontrol.gui.TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF,
            folder);
        model.clearLayouts();
        StandInStation.setCS3(model, false);
        deviceDrops.set(2);

        return syncWithTheWholeStation();
    }

    private static int syncWithTheWholeStation()
    {
        String was = support.TestStationAddress.get();

        try
        {
            support.TestStationAddress.set(wholeStation.address());

            return model.syncWithCS2();
        }
        finally
        {
            support.TestStationAddress.set(was);
        }
    }

    /** W 15's square on Main Layout as the model holds it: MM2 by the station's plan. */
    private static String w15()
    {
        LayoutDiagram page = model.getLayout("Main Layout");

        return page == null ? "no page" : String.valueOf(page.getComponent(5, 9).getProtocol());
    }

    /** Runs `claim` with the layout folder preference put back after, and the diagram read again from it. */
    private static void withTheFolderPutBack(java.util.concurrent.Callable<Void> claim) throws Exception
    {
        String folder = org.traincontrol.gui.TrainControlUI.getPrefs().get(
            org.traincontrol.gui.TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        try
        {
            claim.call();
        }
        finally
        {
            deviceDrops.set(0);
            org.traincontrol.gui.TrainControlUI.getPrefs().put(
                org.traincontrol.gui.TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, folder);
            model.refreshLayouts();
        }
    }

    /**
     * A device file that fails to arrive fails the sync before the station's diagram is read, as the type decides W
     * 15's protocol: nothing is built, and the retry reads the diagram as a CS3's (W 15 is MM2), not a CS2's.
     *
     * MUTATION: drop the `stationTypeUnknown` throw before `syncLayouts` in the no-folder branch of
     * `MarklinControlStation.syncLayoutsFromConfiguredSource`, and the failed sync builds the pages, as a CS2's.
     */
    @Test
    public void testAStationsDiagramWaitsForTheStationsType() throws Exception
    {
        assertTheStationsDiagramWaitsForTheType("");
    }

    /** A first sync from `folder` with the device file dropped builds no page, and the retry builds W 15 as MM2. */
    private static void assertTheStationsDiagramWaitsForTheType(String folder) throws Exception
    {
        withTheFolderPutBack(() ->
        {
            assertEquals(firstSyncWithTheDeviceFileDropped(folder), -1, "a sync whose device file did not arrive succeeded");
            assertEquals(model.getLayoutList(), new ArrayList<>(),
                "pages were built before the station's type was known");

            assertTrue(syncWithTheWholeStation() >= 0, "the retry failed: " + model.getLastSyncError());
            assertEquals(w15(), "MM2", "after the failed sync and the retry, W 15's tile is commanded as");
            return null;
        });
    }

    /**
     * The same through a layout folder that will not read, which falls back to the station's diagram: the fallback waits
     * for the type too, and the folder is kept for the retry, which falls back with the type known.
     *
     * MUTATION: drop the `stationTypeUnknown` throw in the fallback of
     * `MarklinControlStation.syncLayoutsFromConfiguredSource`, and the failed sync builds the pages, as a CS2's.
     */
    @Test
    public void testAFolderThatWillNotReadWaitsForTheTypeToo() throws Exception
    {
        assertTheStationsDiagramWaitsForTheType(new File(sandbox.getFolder(), "no such folder").getAbsolutePath());
    }

    /**
     * A layout folder is read whether or not the type is known, as a simulation's start needs its diagram: the sync
     * still fails, with the folder's pages loaded, and the next one succeeds.
     *
     * MUTATION: throw `stationTypeUnknown` before the folder is read in
     * `MarklinControlStation.syncLayoutsFromConfiguredSource`, and no page is loaded.
     */
    @Test
    public void testALayoutFolderIsReadThoughTheTypeIsUnknown() throws Exception
    {
        withTheFolderPutBack(() ->
        {
            assertEquals(firstSyncWithTheDeviceFileDropped(sandbox.getFolder().getAbsolutePath()), -1,
                "a sync whose device file did not arrive succeeded");
            assertFalse(model.getLayoutList().isEmpty(), "the layout folder's pages were not loaded");

            assertTrue(syncWithTheWholeStation() >= 0, "the next sync failed: " + model.getLastSyncError());
            return null;
        });
    }

    /**
     * "Switch to the Central Station's layout" clears the diagram and syncs; the device file failing that once, after a
     * sync learned the type, still builds the pages (as a CS3's: W 15 is MM2), though the sync fails.
     *
     * MUTATION: withhold the diagram whenever detection failed, whatever was learned before (drop `typeLearned` from
     * `MarklinControlStation.requireTheTypeKnown`), and no page is built.
     */
    @Test
    public void testTheDiagramIsKeptWhenTheDeviceFileFailsAfterTheTypeWasLearned() throws Exception
    {
        withTheFolderPutBack(() ->
        {
            org.traincontrol.gui.TrainControlUI.getPrefs().put(org.traincontrol.gui.TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
            model.clearLayouts();
            StandInStation.setCS3(model, false);
            assertTrue(syncWithTheWholeStation() >= 0, "precondition: " + model.getLastSyncError());

            model.clearLayouts();
            deviceDrops.set(2);

            assertEquals(syncWithTheWholeStation(), -1, "a sync whose device file did not arrive succeeded");
            assertEquals(w15(), "MM2", "the type was known (CS3), so the diagram is built with it");
            return null;
        });
    }
}
