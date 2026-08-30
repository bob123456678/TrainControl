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
 * Reading the code says it should: `pickPath` short-circuits on RANDOM and otherwise ranks every
 * candidate route by `costOf`. But "should" is what this whole exercise exists not to rely on, so this
 * asks each setting what it would choose and prints the answers side by side. If they all name the
 * same route, the setting does nothing on this railway, whatever the code implies.
 *
 * 3.0.0 ONLY, which is why it is not part of ParityDriver: the preference does not exist in 2.8.1, so
 * there is nothing to compare it against and nothing to be a superset of. This answers a different
 * question - not "did we lose routes" but "does this control work".
 *
 * WHAT IT FOUND, FIRST TIME OUT: the setting works, and barely mattered. One train of four chose
 * differently under it, and SHORTEST_LENGTH and LONGEST_LENGTH picked the SAME route as each other -
 * which is what a tie at zero looks like, because only 18 of 132 edges carried a length and lengthOf
 * summed the rest to nothing. Both of those have since been addressed: the rule now travels with the
 * configuration rather than living in a static loaded only by the window, and an unmeasured edge
 * counts as one s88 of track.
 *
 * Still worth knowing when reading the output: RANDOM returns the first route that works without
 * enumerating the alternatives, so it is not one ranking among several - it is the absence of ranking,
 * and it agreeing with a ranked rule means nothing.
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
            // ON THE LAYOUT, because the rule now belongs to the configuration rather than to the
            // running program.  It was static when this probe was written, and the change is the
            // reason the probe stopped compiling against a freshly built jar - which is a better
            // signal than any comment: this file is compiled against both engines, so it notices.
            layout.setPathPreference(preference);

            if (layout.getPathPreference() != preference)
            {
                throw new IllegalStateException("setPathPreference did not take: asked for " + preference
                    + ", the layout reports " + layout.getPathPreference());
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
