package support;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A named railway of a known SHAPE, opened in one call (Adam: "a folder in the test folder to house many different
 * scenarios, each linked to a test case", "hand-authored for topology, lengths in code").  A scenario is real CS2
 * files, checked in and frozen, so a test gets realistic geometry without the operator's live railway.
 *
 * The TOPOLOGY, station names and s88 addresses are authored in the files (each scenario's `README.md` states which a
 * test may rely on).  Lengths, train placements and locomotive properties are deliberately NOT: a test sets them
 * itself (`session.setTileLength`, `loc.setTrainLength`, `point.setLocomotive`).
 *
 * Built ON `LayoutSandbox`, so its safety rules are reached rather than repeated; the one rule added: a scenario can
 * only name a folder under `test/layouts`.  Usage, in `@BeforeClass` and `@AfterClass`:
 *
 *     scenario = support.Scenario.open("single-switch");
 *     Layout built = scenario.build();
 *     ...
 *     scenario.close();
 *
 * @author Adam
 */
public final class Scenario
{
    /** Where the scenarios live, relative to the project root. */
    public static final String ROOT = "test/layouts";

    /**
     * What a scenario may be called: the shape it is, in lower case, hyphenated.  Deliberately narrow, because this is
     * the whole of the protection against a caller reaching out of the library (`..`, a drive letter, a separator).
     */
    private static final String NAME_RULE = "[a-z0-9]+(-[a-z0-9]+)*";

    private final String name;

    private final LayoutSandbox sandbox;

    private final MarklinControlStation model;

    private final AutonomySession session;

    private final List<LayoutDiagram> pages;

    private final List<String> trainsMade = new ArrayList<>();

    private Scenario(String name, LayoutSandbox sandbox, MarklinControlStation model,
        AutonomySession session, List<LayoutDiagram> pages)
    {
        this.name = name;
        this.sandbox = sandbox;
        this.model = model;
        this.session = session;
        this.pages = pages;
    }

    /**
     * Copies the named scenario to a sandbox, builds a model and a session over it, and hands back everything a test
     * needs.  The ORDER is the reason this is one call: `LayoutSandbox` must be opened BEFORE the model (OB-111), the
     * pages must be the MODEL's own rather than a second parse (or every switch comes back with a null accessory), and
     * the session must be pointed at the sandbox COPY.
     *
     * @throws IllegalArgumentException if the name is not a scenario in `test/layouts`
     */
    public static Scenario open(String name) throws Exception
    {
        return open(name, folderFor(name), false);
    }

    /** The same in debug, which `Layout.setSimulate` requires, for a test that runs simulated trains. */
    public static Scenario openSimulated(String name) throws Exception
    {
        return open(name, folderFor(name), true);
    }

    /** The same over any layout folder, read from a copy: a fixture outside the library, for a comparison between builds. */
    public static Scenario openSimulated(File folder) throws Exception
    {
        return open(folder.getName(), folder, true);
    }

    private static Scenario open(String name, File folder, boolean debug) throws Exception
    {
        // INSIDE THE TRY, and closed again if anything below throws: this is not a setup method, so a caller that never
        // receives a Scenario has nothing to call `close()` on, and the machine-global layout preference would be left
        // pointing at a folder under %TEMP%.
        LayoutSandbox sandbox = null;

        try
        {
            // BEFORE the model, always (OB-111).
            sandbox = LayoutSandbox.open(folder);

            MarklinControlStation model = debug ? TestStation.simulatedInDebug()
                : MarklinControlStation.init(null, true, false, false, false);

            List<LayoutDiagram> pages = LayoutSandbox.wiredPages(model);

            AutonomySession session = new AutonomySession(sandbox.getFolder());

            session.open(pages);

            return new Scenario(name, sandbox, model, session, pages);
        }
        catch (Exception | Error failed)
        {
            // The preference goes back before the failure is reported.
            if (sandbox != null) sandbox.close();

            throw failed;
        }
    }

    /**
     * The checked-in folder a scenario name refers to, with the name checked; separate from `open` so the guards can
     * ask without standing a railway up.
     */
    public static File folderFor(String name)
    {
        if (name == null || !name.matches(NAME_RULE))
        {
            throw new IllegalArgumentException("\"" + name + "\" is not a scenario name. A scenario is"
                + " a folder under " + ROOT + " named for the SHAPE it is, in lower case with hyphens"
                + " - and only a bare name, so that nothing can spell its way out to the operator's"
                + " own railway.");
        }

        File folder = new File(ROOT, name);

        if (!folder.isDirectory())
        {
            throw new IllegalArgumentException("there is no scenario called \"" + name + "\" - "
                + folder.getAbsolutePath() + " is not a folder. The scenarios are: " + names());
        }

        return folder;
    }

    /** Every scenario in the library, by name, sorted; empty when the library is missing. */
    public static List<String> names()
    {
        List<String> out = new ArrayList<>();

        File[] folders = new File(ROOT).listFiles();

        if (folders == null) return out;

        for (File folder : folders)
        {
            if (folder.isDirectory()) out.add(folder.getName());
        }

        Collections.sort(out);

        return out;
    }

    /**
     * Builds the running graph from the session as it stands, which is what the application does after every edit.
     * Call it again after changing anything, because the `Layout` is a snapshot of one generation of the configuration.
     */
    public Layout build() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Layout built = model.getAutoLayout();

        if (built == null)
        {
            throw new IllegalStateException("the \"" + name + "\" scenario did not build to a layout at"
                + " all, so nothing below it is being tested");
        }

        return built;
    }

    /** `build`, then simulated and unpaced, so a trip runs to its end at once; for a scenario opened simulated. */
    public Layout buildSimulated() throws Exception
    {
        Layout built = build();

        built.setSimulate(true);
        built.setMinDelay(0);
        built.setMaxDelay(0);

        return built;
    }

    /** A square on the scenario's FIRST page, which is the only page the hand-authored scenarios have. */
    public TileKey tile(int x, int y)
    {
        if (pages.isEmpty())
        {
            throw new IllegalStateException("the \"" + name + "\" scenario has no pages at all");
        }

        return tile(pages.get(0).getName(), x, y);
    }

    /** A square on a named page: the page NAME, because that is what a `TileKey` holds once the store has loaded. */
    public TileKey tile(String page, int x, int y)
    {
        return new TileKey(page, x, y);
    }

    /** The scenario's name. */
    public String getName()
    {
        return name;
    }

    /** The throwaway COPY the session and the model are pointed at, never the checked-in folder. */
    public File getFolder()
    {
        return sandbox.getFolder();
    }

    /**
     * A locomotive of the test's own, reversible and with a speed, deleted again when the scenario closes.
     *
     * @param name its name
     * @param address its address, high enough not to meet the database's
     * @return the locomotive
     */
    public org.traincontrol.base.Locomotive newTrain(String name, int address)
    {
        org.traincontrol.base.Locomotive train = model.newMM2Locomotive(name, address);

        train.setReversible(true);
        train.setPreferredSpeed(50);

        trainsMade.add(name);

        return train;
    }

    /** Where the railway would send this train if asked now, by name, sorted: what the right-click menu offers. */
    public static List<String> endsOffered(Layout railway, org.traincontrol.base.Locomotive train)
    {
        List<String> ends = new ArrayList<>();

        for (List<org.traincontrol.automation.Edge> path : railway.getPossiblePaths(train, true))
        {
            ends.add(path.get(path.size() - 1).getEnd().getName());
        }

        Collections.sort(ends);

        return ends;
    }

    /** The control station, in simulation, with no window. */
    public MarklinControlStation getModel()
    {
        return model;
    }

    /** The autonomy session over the copy. */
    public AutonomySession getSession()
    {
        return session;
    }

    /** The model's own wired pages, in its order. */
    public List<LayoutDiagram> getPages()
    {
        return pages;
    }

    /**
     * Puts the layout preference back, exactly as `LayoutSandbox` does; a test that does not call this changes which
     * layout the application opens next.
     */
    public void close()
    {
        for (String train : trainsMade) model.deleteLoc(train);

        if (sandbox != null) sandbox.close();
    }
}
