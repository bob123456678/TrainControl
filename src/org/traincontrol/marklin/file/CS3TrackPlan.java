package org.traincontrol.marklin.file;

import java.awt.Point;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The CS3's own track plans (`/app/api/gbs`): each page's articles and the squares they are on.  Read leniently: an
 * article, square or page that will not read counts as not there, and no gbs content makes it throw (OB-402).
 *
 * @author Adam
 */
public final class CS3TrackPlan
{
    private final Map<String, Page> pages = new HashMap<>();

    /**
     * @param gbs the station's answer, or null for a read that failed
     */
    public CS3TrackPlan(JSONObject gbs)
    {
        JSONArray plans = gbs == null ? null : gbs.optJSONArray("plans");

        for (int i = 0; plans != null && i < plans.length(); i++)
        {
            JSONObject plan = plans.optJSONObject(i);

            if (plan != null)
            {
                this.pages.putIfAbsent(plan.optString("name"), new Page(plan));
            }
        }
    }

    /**
     * The plan of the named page, or null if the station has none.
     */
    public Page page(String name)
    {
        return this.pages.get(name);
    }

    /**
     * A CS3 internname as the article's name: "#" and two hex digits stand for a byte of its UTF-8 ("W#2014" = "W 14").
     */
    private static String decodeInternname(String internname)
    {
        try
        {
            return URLDecoder.decode(internname.replace("%", "%25").replace("+", "%2B").replace('#', '%'), "UTF-8");
        }
        catch (Exception e)
        {
            return internname;
        }
    }

    /**
     * One page's plan: which articles it holds, and the square of each on its grid.
     */
    public static final class Page
    {
        private final boolean grid;

        // By article name; null for an article whose position will not read
        private final Map<String, Point> squares = new HashMap<>();

        private Page(JSONObject plan)
        {
            this.grid = "stellpult".equals(plan.optString("type"));
            JSONArray articles = plan.optJSONArray("articles");

            for (int i = 0; articles != null && i < articles.length(); i++)
            {
                JSONObject a = articles.optJSONObject(i);
                String name = a == null ? null : a.optString("internname", null);
                double x = a == null ? Double.NaN : a.optDouble("x");
                double y = a == null ? Double.NaN : a.optDouble("y");

                if (name != null)
                {
                    this.squares.put(decodeInternname(name), Double.isNaN(x) || Double.isNaN(y) ? null
                        : new Point((int) Math.round((x - 35) / 70), (int) Math.round((y - 35) / 70)));
                }
            }
        }

        /** Whether the page is a grid ("stellpult"), on which an article's square can be told. */
        public boolean isGrid()
        {
            return this.grid;
        }

        /** Whether the page holds the named article, wherever it is. */
        public boolean holds(String article)
        {
            return this.squares.containsKey(article);
        }

        /** Whether the named article is on this square of the plan's grid; false if its square will not read. */
        public boolean isOn(String article, Point square)
        {
            return square.equals(this.squares.get(article));
        }

        /**
         * Where the plan's grid sits on the page's squares, voted by known articles: each article votes once for each
         * page square given for it.  The offset most votes agree on, if at least two do and no other offset ties it;
         * null otherwise, and on a free ("plan") page.
         *
         * @param squaresByArticle for an article of an address with one protocol, the squares of its tiles on the page
         */
        public Point gridOffset(Map<String, List<Point>> squaresByArticle)
        {
            if (!this.grid)
            {
                return null;
            }

            Map<Point, Integer> votes = new HashMap<>();

            squaresByArticle.forEach((article, tiles) ->
            {
                Point square = this.squares.get(article);

                for (int i = 0; square != null && i < tiles.size(); i++)
                {
                    votes.merge(new Point(tiles.get(i).x - square.x, tiles.get(i).y - square.y), 1, Integer::sum);
                }
            });

            List<Map.Entry<Point, Integer>> ranked = new ArrayList<>(votes.entrySet());
            ranked.sort((a, b) -> b.getValue() - a.getValue());

            if (ranked.isEmpty() || ranked.get(0).getValue() < 2
                || (ranked.size() > 1 && ranked.get(1).getValue().equals(ranked.get(0).getValue())))
            {
                return null;
            }

            return ranked.get(0).getKey();
        }
    }
}
