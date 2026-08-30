import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Does the routing-logic preference actually change which route is chosen?
 *
 * Adam: "The pathing is a preference only in 3.0.0, in the autonomy menu.  Not sure how it's built, not
 * sure if it does anything."
 *
 * Reading the code says it should: the menu writes the choice to a preference AND pushes it into
 * Layout's static `pathPreference`; `pickPath` short-circuits on RANDOM and otherwise ranks every
 * candidate route by `costOf`. But "should" is what this whole exercise exists not to rely on, so this
 * asks each setting what it would choose and prints the answers side by side. If they are all the same
 * route, the setting does nothing on this railway, whatever the code implies.
 *
 * 3.0.0 ONLY, which is why it is not part of ParityDriver: the preference does not exist in 2.8.1, so
 * there is nothing to compare it against and nothing to be a superset of. This answers a different
 * question - not "did we lose routes" but "does this control work".
 *
 * TWO WAYS IT CAN SILENTLY NOT APPLY, both worth knowing:
 *
 *   - `pathPreference` is STATIC on Layout and defaults to RANDOM, and the only thing that loads the
 *     saved value into it is the menu builder in the window. Anything that runs autonomy without
 *     building that menu - a script, an example, this probe unless it sets the value itself - runs on
 *     RANDOM no matter what is saved.
 *   - RANDOM returns the first route that works without enumerating alternatives, so it is not one
 *     ranking among several; it is the absence of ranking.
 *
 * Usage: PathPreferenceProbe &lt;layoutFolder&gt; &lt;autonomyJson&gt; &lt;outFile&gt;
 */
public class PathPreferenceProbe
{
    private static final String[] PLACES =
        {"BottomMainA", "BottomMainB", "BottomMainC", "BottomInner"};

    private static final int[] ADDRESSES = {901, 902, 903, 904};

    private static final String PREFIX = "PARITY-";

    public static void main(String[] args) throws Exception
    {
        if (args.length < 3)
        {
            System.err.println("Usage: PathPreferenceProbe <layoutFolder> <autonomyJson> <outFile>");

            System.exit(2);
        }

        File layoutFolder = new File(args[0]).getAbsoluteFile();
        File autonomyJson = new File(args[1]).getAbsoluteFile();
        File out = new File(args[2]).getAbsoluteFile();

        Preferences prefs = Preferences.userNodeForPackage(TrainControlUI.class);

        prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, layoutFolder.getAbsolutePath());

        MarklinControlStation control = MarklinControlStation.init(null, true, false, false, true);

        Layout layout = Layout.fromJSON(
            new String(Files.readAllBytes(autonomyJson.toPath()), Charset.forName("UTF-8")), control);

        if (layout == null) throw new IllegalStateException("the engine refused " + autonomyJson);

        List<String> lines = new ArrayList<>();

        List<String> starts = new ArrayList<>();

        for (int i = 0; i < PLACES.length; i++)
        {
            control.deleteLoc(PREFIX + ADDRESSES[i]);
            control.newDCCLocomotive(PREFIX + ADDRESSES[i], ADDRESSES[i]);

            starts.add(firstPlatform(layout, PLACES[i]));
        }

        for (Layout.PathPreference preference : Layout.PathPreference.values())
        {
            Layout.setPathPreference(preference);

            // Proof that the value took, since it is a static somebody else may also be setting.
            if (Layout.getPathPreference() != preference)
            {
                throw new IllegalStateException("setPathPreference did not take: asked for " + preference
                    + ", Layout reports " + Layout.getPathPreference());
            }

            // Placed fresh for every preference, so each one is choosing from the same board rather
            // than from wherever the previous one left the trains.
            for (Point point : layout.getPoints())
            {
                if (point.getCurrentLocomotive() != null) point.setLocomotive(null);
            }

            for (int i = 0; i < PLACES.length; i++)
            {
                layout.moveLocomotive(PREFIX + ADDRESSES[i], starts.get(i), true);
            }

            for (int i = 0; i < PLACES.length; i++)
            {
                Locomotive loc = control.getLocByName(PREFIX + ADDRESSES[i]);

                List<Edge> chosen = layout.pickPath(loc);

                lines.add(preference.name() + "\t" + PREFIX + ADDRESSES[i] + "\t"
                    + (chosen == null || chosen.isEmpty() ? "(none)" : describe(chosen)));
            }
        }

        PrintWriter writer = new PrintWriter(out, "UTF-8");

        try
        {
            for (String line : lines) writer.println(line);
        }
        finally
        {
            writer.close();
        }

        System.out.println("wrote " + lines.size() + " choices to " + out);

        System.exit(0);
    }

    private static String firstPlatform(Layout layout, String place)
    {
        for (Point point : layout.getPoints())
        {
            String name = point.getName();

            if ((name.equals(place) || name.startsWith(place + " (")) && point.isDestination())
            {
                return name;
            }
        }

        throw new IllegalStateException("no platform facing found for " + place);
    }

    private static String describe(List<Edge> path)
    {
        return path.get(0).getStart().getName() + " -> "
            + path.get(path.size() - 1).getEnd().getName() + "  (" + path.size() + " edges)";
    }
}
