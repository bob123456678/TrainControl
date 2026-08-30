import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Writes the 3.0.0 autonomy configuration out as the same JSON the old engine reads.
 *
 * This is the only step in the parity harness that 2.8.1 could not run, and it exists so that the step
 * AFTER it - {@link ParityDriver} - does not have to know which engine produced its input. Both sides
 * then get asked the same questions through the same API, and a difference in the answers is a
 * difference in the graph rather than in how it was interrogated.
 *
 * That the new setup can be emitted in the old format at all is the thing that makes this comparison
 * honest. The two engines are not being run side by side and eyeballed; they are being handed to one
 * reader that treats them identically.
 *
 * Usage: BuildDiagramSetup &lt;layoutFolder&gt; &lt;outJson&gt;
 */
public class BuildDiagramSetup
{
    public static void main(String[] args) throws Exception
    {
        if (args.length < 2)
        {
            System.err.println("Usage: BuildDiagramSetup <layoutFolder> <outJson>");

            System.exit(2);
        }

        File layoutFolder = new File(args[0]).getAbsoluteFile();
        File out = new File(args[1]).getAbsoluteFile();

        if (!layoutFolder.isDirectory()) throw new IllegalStateException("no layout at " + layoutFolder);

        Preferences prefs = Preferences.userNodeForPackage(TrainControlUI.class);

        prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, layoutFolder.getAbsolutePath());

        // autoPowerOn is FALSE, and that is not a preference.
        //
        // With it true this blocks forever at "Restoring state...": powering on asks a Central Station
        // that is not there, and there is no window to put the question to. The suite's own fixtures
        // call init(null, true, false, false, ...) for the same reason.
        MarklinControlStation control = MarklinControlStation.init(null, true, false, false, true);

        // PAGE ORDER MATTERS AND getLayoutList SORTS.
        //
        // A link records its destination as an index over every page, so the order these are handed to
        // the session in is not cosmetic - reorder them and every cross-page arrow points somewhere
        // else. getLayoutList returns them sorted by name, which for this layout is the same as file
        // order because the pages are numbered, and that coincidence is checked rather than relied on.
        List<LayoutDiagram> pages = new ArrayList<>();

        String previous = null;

        for (String name : control.getLayoutList())
        {
            if (previous != null && previous.compareTo(name) > 0)
            {
                throw new IllegalStateException("page names are not in ascending order, so sorted "
                    + "order is not file order and link destinations would be wrong: "
                    + control.getLayoutList());
            }

            previous = name;

            pages.add(control.getLayout(name));
        }

        if (pages.isEmpty()) throw new IllegalStateException("no pages were parsed from " + layoutFolder);

        AutonomySession session = new AutonomySession(layoutFolder);

        session.open(pages);

        String json = session.buildConfiguration();

        if (json == null || json.trim().isEmpty())
        {
            throw new IllegalStateException("the session produced no configuration - the diagram was "
                + "probably refused; run the checker to see why");
        }

        PrintWriter writer = new PrintWriter(out, "UTF-8");

        try
        {
            writer.print(json);
        }
        finally
        {
            writer.close();
        }

        System.out.println("wrote " + json.length() + " chars of configuration to " + out
            + " from " + pages.size() + " pages");

        System.exit(0);
    }
}
