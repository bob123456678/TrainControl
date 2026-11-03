package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.file.CS2File;

/**
 * A CS3 locomotive's picture is asked for as the station's own web app asks for it: the file name without a trailing
 * ".png", and a folder other than the two standard ones passed as `?path=` (the rule in the CS3 2.6.1 web app,
 * `Ti`/`dC`, with `Vx = ["/home/cs3/cs3data/lokicons", "/usr/local/cs3/lokicons"]`).  Pictures in the standard folders,
 * which are all of Adam's 160, are asked for exactly as before.
 *
 * MUTATION: build the URL from the last path segment alone again (getImageURLCS3 of what follows the last "/"), and
 * the ".png" and USB cases fail.
 *
 * @author Adam
 */
public class testACS3IconFollowsTheWebAppsRule
{
    private static final String IP = "192.0.2.1";

    private static String urlFor(String icon) throws Exception
    {
        JSONArray all = new JSONArray(new String(Files.readAllBytes(new File("test/CS3_loks_v260.json").toPath()),
            StandardCharsets.UTF_8));

        JSONObject one = null;

        for (int i = 0; i < all.length() && one == null; i++)
        {
            if (!all.getJSONObject(i).has("traktion"))
            {
                one = all.getJSONObject(i);
            }
        }

        one.put("icon", icon);

        List<MarklinLocomotive> parsed = new CS2File(IP, null).parseLocomotivesCS3(new JSONArray().put(one));

        assertEquals(parsed.size(), 1);
        return parsed.get(0).getImageURL();
    }

    @Test
    public void testTheStandardFoldersAreAskedForAsBefore() throws Exception
    {
        assertEquals(urlFor("/usr/local/cs3/lokicons/DB BR 118 028-0"),
            "http://" + IP + "/app/assets/lok/DB%20BR%20118%20028-0.png");
        assertEquals(urlFor("/home/cs3/cs3data/lokicons/en57_1000_(1)"),
            "http://" + IP + "/app/assets/lok/en57_1000_%281%29.png");
        assertEquals(urlFor("leere Lok"), "http://" + IP + "/app/assets/lok/leere%20Lok.png", "a bare name");
    }

    @Test
    public void testANameEndingInPngIsNotDoubled() throws Exception
    {
        assertEquals(urlFor("/usr/local/cs3/lokicons/My Loco.png"), "http://" + IP + "/app/assets/lok/My%20Loco.png");
    }

    @Test
    public void testAnotherFolderIsPassedAsPath() throws Exception
    {
        assertEquals(urlFor("/media/usb0/my icons/BR 50.png"),
            "http://" + IP + "/app/assets/lok/BR%2050.png?path=/media/usb0/my%20icons");
    }

    /** Every locomotive in the 2.6 fixture, all in the standard folders, keeps the URL the old rule gave it. */
    @Test
    public void testEveryFixtureLocomotiveKeepsItsURL() throws Exception
    {
        JSONArray all = new JSONArray(new String(Files.readAllBytes(new File("test/CS3_loks_v260.json").toPath()),
            StandardCharsets.UTF_8));
        CS2File parser = new CS2File(IP, null);

        for (MarklinLocomotive l : parser.parseLocomotivesCS3(all))
        {
            for (int i = 0; i < all.length(); i++)
            {
                if (all.getJSONObject(i).getString("name").equals(l.getName()) && all.getJSONObject(i).has("icon"))
                {
                    String[] pieces = all.getJSONObject(i).getString("icon").split("/");

                    assertEquals(l.getImageURL(), parser.getImageURLCS3(pieces[pieces.length - 1]), l.getName());
                }
            }
        }
    }
}
