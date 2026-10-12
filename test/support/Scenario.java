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
 * A named railway of a known SHAPE, opened in one call.
 *
 * Adam, 2026-09-09: **"many versions of a track diagram/layout that we can use in our tests.  Then the
 * ground truth doesn't move underneath us, and things like lengths and train positions can be
 * manipulated at will... a folder in the test folder to house many different scenarios, each linked to
 * a test case"**, and **"hand-authored for topology, lengths in code."**
 *
 * **The gap this closes is named in `test/README.md` (MON-C17 to C21).**  Every hand-built fixture in
 * this suite was a straight chain of two or three ordinary points - no switch, no curve, no split
 * square - so a whole class of defect could not be expressed by any fixture, and the tests that DID
 * have realistic geometry got it by opening the operator's live railway, whose ground truth moves as he
 * operates it.  Three tests broke on 2026-09-09 for exactly that reason.  A scenario is the third
 * option: real CS2 files, checked in, hand-authored for one shape, and frozen.
 *
 * **What is an invariant and what is not.**  The TOPOLOGY, the station names and the s88 addresses are
 * authored in the files and a test may rely on them - each scenario's `README.md` states which.
 * Lengths, train placements and locomotive properties are deliberately NOT in the files: they are the
 * half Adam wants to vary freely, so a test sets them itself (`session.setTileLength`,
 * `loc.setTrainLength`, `point.setLocomotive`) and no two tests can disagree about them by inheriting
 * one author's choice.
 *
 * **Built ON `LayoutSandbox`, not beside it.**  Every safety rule that class carries - the copy, the
 * preference put back afterwards, `setUnattended` - is its own and is reached through it rather than
 * repeated here.  The one rule added is that a scenario can only ever name a folder under
 * `test/layouts`: `open` refuses a path separator, so no caller can spell its way out to
 * `cs2_sample_layout`.
 *
 * Usage is three lines, in `@BeforeClass` and `@AfterClass`:
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
    /**
     * Where the scenarios live, relative to the project root - the working directory every test runs
     * from.
     */
    public static final String ROOT = "test/layouts";

    /**
     * What a scenario may be called: the shape it is, in lower case, hyphenated.
     *
     * Deliberately narrow.  This is the whole of the protection against a caller reaching out of the
     * library - `..`, a drive letter and both separators are all outside it - and a narrow rule that
     * refuses a legal name is a rename, while a wide one that admits an illegal path is the operator's
     * railway being opened by a test.
     */
    private static final String NAME_RULE = "[a-z0-9]+(-[a-z0-9]+)*";

    private final String name;

    private final LayoutSandbox sandbox;

    private final MarklinControlStation model;

    private final AutonomySession session;

    private final List<LayoutDiagram> pages;

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
     * Copies the named scenario to a sandbox, builds a model and a session over it, and hands back
     * everything a test needs.
     *
     * The ORDER is the reason this exists as one call.  `LayoutSandbox` must be opened BEFORE the model
     * (OB-111: `init` reads the layout preference, which on Adam's machine names his real railway), the
     * pages must be the MODEL's own rather than a second parse (or every switch comes back with a null
     * accessory and the railway reduces to a handful of edges), and the session has to be pointed at
     * the sandbox COPY rather than the checked-in folder.  Three rules, each of which has been got
     * wrong in this suite, and none of which a caller has to remember again.
     *
     * @param name the scenario folder's name - the SHAPE, e.g. "single-switch"
     * @return the opened scenario, to be closed when the class is done with it
     * @throws IllegalArgumentException if the name is not a scenario in `test/layouts`
     * @throws Exception if the folder cannot be copied or the model cannot be built
     */
    public static Scenario open(String name) throws Exception
    {
        File folder = folderFor(name);

        // INSIDE THE TRY, and closed again if anything below throws (TSX-B8).
        //
        // A test class opens its sandbox in `@BeforeClass` and is safe without this, because
        // `@AfterClass(alwaysRun = true)` runs even when the set-up threw. THIS method is not a setup
        // method: it is three more things that can throw - `init` failing to bind its port, an
        // unreadable fixture folder, a configuration the session refuses - and a caller that never
        // receives a Scenario has nothing to call `close()` on. What that leaves behind is the
        // machine-global layout preference pointing at a folder under %TEMP%, which is the railway
        // TrainControl opens the next time Adam starts it.
        LayoutSandbox sandbox = null;

        try
        {
            // BEFORE the model, always (OB-111).
            sandbox = LayoutSandbox.open(folder);

            MarklinControlStation model = MarklinControlStation.init(null, true, false, false, false);

            List<LayoutDiagram> pages = LayoutSandbox.wiredPages(model);

            AutonomySession session = new AutonomySession(sandbox.getFolder());

            session.open(pages);

            return new Scenario(name, sandbox, model, session, pages);
        }
        catch (Exception | Error failed)
        {
            // The preference goes back before the failure is reported, so a scenario that could not be
            // opened costs the test and nothing else.
            if (sandbox != null) sandbox.close();

            throw failed;
        }
    }

    /**
     * The checked-in folder a scenario name refers to, with the name checked.
     *
     * Separate from `open` so the guards can ask the same question without standing a railway up.
     *
     * @param name the scenario name
     * @return the folder, which is guaranteed to exist and to be under ROOT
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

    /**
     * Every scenario in the library, by name.
     *
     * @return the folder names under ROOT, sorted; empty when the library is missing
     */
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
     * Builds the running graph from the session as it stands, which is what the application does after
     * every edit.
     *
     * Call it again after changing anything - a length, a station flag - because the configuration is
     * generated from the session and the `Layout` is a snapshot of one generation of it.
     *
     * @return the built layout
     * @throws Exception if the configuration will not parse
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

    /**
     * A square on the scenario's FIRST page, which is the only page the hand-authored scenarios have.
     *
     * @param x the column
     * @param y the row, growing downwards
     * @return the key
     */
    public TileKey tile(int x, int y)
    {
        if (pages.isEmpty())
        {
            throw new IllegalStateException("the \"" + name + "\" scenario has no pages at all");
        }

        return tile(pages.get(0).getName(), x, y);
    }

    /**
     * A square on a named page, for the scenarios that have more than one.
     *
     * The page NAME rather than its id, because that is what a `TileKey` holds once the store has
     * loaded - the id form only exists on disk. Getting this the wrong way round produces a key that
     * matches nothing and a test that asserts about null.
     *
     * @param page the page name
     * @param x the column
     * @param y the row
     * @return the key
     */
    public TileKey tile(String page, int x, int y)
    {
        return new TileKey(page, x, y);
    }

    /**
     * @return the scenario's name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the throwaway COPY the session and the model are pointed at, never the checked-in folder
     */
    public File getFolder()
    {
        return sandbox.getFolder();
    }

    /**
     * @return the control station, in simulation, with no window
     */
    public MarklinControlStation getModel()
    {
        return model;
    }

    /**
     * @return the autonomy session over the copy
     */
    public AutonomySession getSession()
    {
        return session;
    }

    /**
     * @return the model's own wired pages, in its order
     */
    public List<LayoutDiagram> getPages()
    {
        return pages;
    }

    /**
     * Puts the layout preference back, exactly as `LayoutSandbox` does.
     *
     * A test that does not call this has changed which layout the application opens the next time the
     * operator starts it, which is worse than the churn the sandbox exists to remove.
     */
    public void close()
    {
        if (sandbox != null) sandbox.close();
    }
}
