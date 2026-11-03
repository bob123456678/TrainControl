package core;

import java.awt.Point;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.traincontrol.marklin.file.CS3TrackPlan;

/**
 * The CS3's track plans (`/app/api/gbs`) read on their own, with no station and no CS2File: which articles each page
 * holds, the square each is on, and where the grid sits on the page.  A plan that will not read, in whole or in part,
 * answers "not here" and never throws.
 *
 * The fixture is Adam's CS3 plus (2.6.1), `test/cs3_v261/app/api/gbs.json`: W 14 and W 15 (both address 25) on the
 * grid page Main Layout at squares (0,7) and (1,9), which are (4,7) and (5,9) on the page; W 11 on Main Layout and
 * W 1R on the free page Startpackung (both address 1).
 *
 * MUTATION: read the articles with getString and getDouble (CS3TrackPlan.Page), and the bad articles throw.
 *
 * @author Adam
 */
public class testACS3TrackPlanTellsTwinsApartOrSaysItCannot
{
    // Main Layout's articles of one protocol voting, each from its tile four squares right of its plan square
    private static final Map<String, List<Point>> VOTES = new HashMap<>();

    static
    {
        VOTES.put("W 2", Arrays.asList(new Point(8, 4)));
        VOTES.put("W 3", Arrays.asList(new Point(8, 1)));
        VOTES.put("W 5", Arrays.asList(new Point(9, 1)));
    }

    private static JSONObject fixture() throws Exception
    {
        return new JSONObject(new String(Files.readAllBytes(new File("test/cs3_v261/app/api/gbs.json").toPath()),
            StandardCharsets.UTF_8));
    }

    private static JSONObject page(JSONObject gbs, String name)
    {
        for (int i = 0; i < gbs.getJSONArray("plans").length(); i++)
        {
            if (name.equals(gbs.getJSONArray("plans").getJSONObject(i).getString("name")))
            {
                return gbs.getJSONArray("plans").getJSONObject(i);
            }
        }

        throw new AssertionError("no page " + name);
    }

    private static JSONObject article(JSONObject gbs, String page, String internname)
    {
        JSONArray all = page(gbs, page).getJSONArray("articles");

        for (int i = 0; i < all.length(); i++)
        {
            if (internname.equals(all.getJSONObject(i).getString("internname")))
            {
                return all.getJSONObject(i);
            }
        }

        throw new AssertionError("no article " + internname);
    }

    /**
     * W 14 and W 15 share address 25 on Main Layout; the grid sits four squares right, so the tile at (4,7) is W 14's
     * and the one at (5,9) is W 15's.
     */
    @Test
    public void testW14AndW15AreToldApartBySquare() throws Exception
    {
        CS3TrackPlan.Page main = new CS3TrackPlan(fixture()).page("Main Layout");

        assertEquals(main.gridOffset(VOTES), new Point(4, 0));
        assertTrue(main.holds("W 14") && main.holds("W 15"));
        assertTrue(main.isOn("W 14", new Point(0, 7)));
        assertFalse(main.isOn("W 15", new Point(0, 7)));
        assertTrue(main.isOn("W 15", new Point(1, 9)));
        assertFalse(main.isOn("W 14", new Point(1, 9)));
    }

    /**
     * W 11 and W 1R share address 1 on different pages, so the page alone tells them; Startpackung is a free page,
     * whose squares give no offset however its articles vote.
     */
    @Test
    public void testW11AndW1RAreToldApartByPage() throws Exception
    {
        CS3TrackPlan plan = new CS3TrackPlan(fixture());

        assertTrue(plan.page("Main Layout").holds("W 11"));
        assertFalse(plan.page("Main Layout").holds("W 1R"));
        assertTrue(plan.page("Startpackung").holds("W 1R"));
        assertFalse(plan.page("Startpackung").holds("W 11"));

        Map<String, List<Point>> votes = new HashMap<>();
        votes.put("W 2L", Arrays.asList(new Point(6, 2)));
        votes.put("W 3R", Arrays.asList(new Point(12, 2)));
        assertNull(plan.page("Startpackung").gridOffset(votes));
    }

    /**
     * A rename on the station is in the internname, "#" and two hex digits standing for a byte of its UTF-8; the
     * article is found by its name as decoded.
     */
    @Test
    public void testARenamedArticleIsFoundByItsDecodedName() throws Exception
    {
        JSONObject gbs = fixture();
        article(gbs, "Main Layout", "W#2015").put("internname", "Weiche#20#C3#9C#2015");
        CS3TrackPlan.Page main = new CS3TrackPlan(gbs).page("Main Layout");

        assertTrue(main.isOn("Weiche Ü 15", new Point(1, 9)));
        assertFalse(main.holds("W 15"));
        assertFalse(main.holds("Weiche#20#C3#9C#2015"));
    }

    /**
     * The grid's place is the offset at least two votes agree on with no other tying it; one vote, or a tie, is no
     * answer.
     */
    @Test
    public void testAnOffsetNeedsTwoVotesAndNoTie() throws Exception
    {
        CS3TrackPlan.Page main = new CS3TrackPlan(fixture()).page("Main Layout");

        Map<String, List<Point>> one = new HashMap<>();
        one.put("W 2", Arrays.asList(new Point(8, 4)));
        assertNull(main.gridOffset(one), "one vote");

        Map<String, List<Point>> tie = new HashMap<>(VOTES);
        tie.put("W 6", Arrays.asList(new Point(13, 1)));
        tie.put("W 8", Arrays.asList(new Point(13, 3)));
        tie.put("W 9", Arrays.asList(new Point(9, 7)));
        assertNull(main.gridOffset(tie), "three votes for (4,0), three for (2,0)");

        assertNull(main.gridOffset(new HashMap<>()), "no votes");
    }

    /** A data row: what is wrong, and the change that makes it so. */
    private static Object[] row(String what, Consumer<JSONObject> spoil)
    {
        return new Object[] {what, spoil};
    }

    /** Main Layout's plan with W 15's article spoiled. */
    private static CS3TrackPlan.Page withW15(Consumer<JSONObject> spoil) throws Exception
    {
        JSONObject gbs = fixture();
        spoil.accept(article(gbs, "Main Layout", "W#2015"));
        return new CS3TrackPlan(gbs).page("Main Layout");
    }

    @DataProvider
    public Object[][] badSquares()
    {
        return new Object[][] {
            row("no x", a -> a.remove("x")),
            row("no y", a -> a.remove("y")),
            row("x a word", a -> a.put("x", "abc")),
            row("y a list", a -> a.put("y", new JSONArray().put(665))),
            row("x null", a -> a.put("x", JSONObject.NULL)),
        };
    }

    /**
     * W 15 with a position that will not read is held by its page but on no square, so its tile cannot be told; W 14
     * beside it is still on its own.
     */
    @Test(dataProvider = "badSquares")
    public void testAnUnreadableSquareIsOnNoSquare(String what, Consumer<JSONObject> spoil) throws Exception
    {
        CS3TrackPlan.Page main = withW15(spoil);

        assertTrue(main.holds("W 15"), what);

        for (Point p : Arrays.asList(new Point(1, 9), new Point(0, 9), new Point(1, 0), new Point(0, 0)))
        {
            assertFalse(main.isOn("W 15", p), what + " at " + p);
        }

        assertTrue(main.isOn("W 14", new Point(0, 7)), what);
        assertEquals(main.gridOffset(VOTES), new Point(4, 0), what);
    }

    @DataProvider
    public Object[][] badNames()
    {
        return new Object[][] {
            row("a number", a -> a.put("internname", 7)),
            row("missing", a -> a.remove("internname")),
            row("null", a -> a.put("internname", JSONObject.NULL)),
        };
    }

    /**
     * W 15 with an internname that is not its name is not on the page at all.
     */
    @Test(dataProvider = "badNames")
    public void testAnArticleNotNamedIsNotHeld(String what, Consumer<JSONObject> spoil) throws Exception
    {
        CS3TrackPlan.Page main = withW15(spoil);

        assertFalse(main.holds("W 15"), what);
        assertFalse(main.isOn("W 15", new Point(1, 9)), what);
        assertTrue(main.isOn("W 14", new Point(0, 7)), what);
    }

    @DataProvider
    public Object[][] badPlans()
    {
        return new Object[][] {
            row("the page missing", g -> page(g, "Main Layout").put("name", "Renamed")),
            row("the page unnamed", g -> page(g, "Main Layout").remove("name")),
            row("no plans", g -> g.remove("plans")),
            row("plans a word", g -> g.put("plans", "abc")),
            row("plans not objects", g -> g.put("plans", new JSONArray().put(1).put("x"))),
            row("empty", g -> new java.util.ArrayList<>(g.keySet()).forEach(g::remove)),
        };
    }

    /**
     * With Main Layout's plan gone or the plans unreadable, the station has no plan of that page.
     */
    @Test(dataProvider = "badPlans")
    public void testAMissingPageHasNoPlan(String what, Consumer<JSONObject> spoil) throws Exception
    {
        JSONObject gbs = fixture();
        spoil.accept(gbs);

        assertNull(new CS3TrackPlan(gbs).page("Main Layout"), what);
    }

    /**
     * Of two pages with one name, the first is the plan.
     */
    @Test
    public void testTheFirstPageOfANameCounts() throws Exception
    {
        JSONObject gbs = fixture();
        gbs.getJSONArray("plans").put(new JSONObject().put("name", "Main Layout").put("type", "stellpult")
            .put("articles", new JSONArray()));

        assertTrue(new CS3TrackPlan(gbs).page("Main Layout").holds("W 14"));
    }

    /**
     * A page whose articles will not read holds none and gives no offset; Main Layout's other articles still read
     * when one of them is not an object.
     */
    @Test
    public void testAPageWithUnreadableArticlesHoldsNone() throws Exception
    {
        JSONObject gbs = fixture();
        page(gbs, "Main Layout").put("articles", "abc");
        CS3TrackPlan.Page main = new CS3TrackPlan(gbs).page("Main Layout");

        assertFalse(main.holds("W 14"));
        assertNull(main.gridOffset(VOTES));

        gbs = fixture();
        page(gbs, "Main Layout").getJSONArray("articles").put(0, 7).put(JSONObject.NULL);
        main = new CS3TrackPlan(gbs).page("Main Layout");

        assertTrue(main.isOn("W 15", new Point(1, 9)));
        assertFalse(main.holds("W 2"), "the article replaced by 7");
    }

    /**
     * A read that failed, a reply that was not JSON among them, comes as no document: there is no plan of any page.
     */
    @Test
    public void testAFailedReadHasNoPlans()
    {
        assertNull(new CS3TrackPlan(null).page("Main Layout"));
        assertNull(new CS3TrackPlan(new JSONObject()).page("Main Layout"));
    }
}
