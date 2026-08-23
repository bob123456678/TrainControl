package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A state Adam has CONFIRMED is right, and everything that should still follow from it.
 *
 * Adam: "Once we are bug free, can you capture confirmed good state along with config files for
 * validation? That is better than guessing."
 *
 * He is right, and the word doing the work is *confirmed*. Every other test in this suite - mine
 * included - asserts a rule somebody believed. Three of the rules I wrote on 2026-08-23 were wrong on
 * the first draft and passed anyway; the mutation checks caught them. A blessed baseline makes no claim
 * about WHY anything is correct. It says: this exact input produced this exact output, and a person who
 * knows the railway looked at it and said yes. Nothing to be mistaken about.
 *
 * It does not replace the rules - a baseline cannot catch a fault the first time it appears, because
 * whatever it captured is by definition what it expects. The two answer different questions: a rule
 * says "this was never allowed", a baseline says "this changed since you approved it".
 *
 * **The layout is copied INTO the baseline.** Not referenced. `cs2_sample_layout` is the railway Adam
 * works on, edited between sessions and rewritten by the application itself, so a baseline pointing at
 * it would go stale the first time he moved a tile - and the failure would look like a regression in
 * TrainControl rather than a change he made on purpose.
 *
 * **To capture, or to bless a deliberate change:**
 *
 * <pre>
 *   java -Dbaseline.capture=true ... regression.testConfirmedGoodState
 * </pre>
 *
 * That writes `test/baseline/` from the current sample layout and passes. Read the git diff before
 * committing it: that diff IS the change being blessed, and it is the only moment anybody looks at it.
 *
 * @author Adam
 */
public class testConfirmedGoodState
{
    /**
     * Where the blessed state lives.
     *
     * Overridable with -Dbaseline.dir so the capture-and-compare cycle can be exercised end to end in
     * a scratch directory. That matters more than it looks: the alternative is handing somebody a
     * button nobody has ever pressed, and the first press would be the one that blesses the real thing.
     */
    private static final File BASELINE =
        new File(System.getProperty("baseline.dir", "test/baseline"));

    private static final File LAYOUT = new File(BASELINE, "layout");
    private static final File CONFIGURATION = new File(BASELINE, "configuration.json");
    private static final File GRAPH = new File(BASELINE, "graph.txt");

    private static MarklinControlStation model;

    private static boolean capturing;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        capturing = Boolean.getBoolean("baseline.capture");

        model = init(null, true, false, false, false);
    }

    /**
     * The graph the blessed configuration reduces to has not changed.
     *
     * Points and edges, sorted and written as text, because a diff of text is something a person can
     * read: "this edge is gone" is a sentence, and a changed byte in a JSON blob is not.
     */
    @Test
    public void testTheDerivedGraphIsWhatWasBlessed() throws Exception
    {
        AutonomySession session = openBaseline();

        if (session.getReducer() == null) throw new SkipException("the baseline reduced to nothing");

        String now = describe(session.getReducer());

        if (capturing)
        {
            write(GRAPH, now);
            return;
        }

        String was = read(GRAPH);

        // Compared through firstDifference rather than by assertEquals on the whole text. Asserting
        // two 4kB strings prints both of them, which is not a report - it is the diff a person came
        // here to avoid reading.
        assertEquals(firstDifference(was, now), "",
            "the railway the blessed setup reduces to has changed - points or edges differ from what "
            + "was confirmed. If that is deliberate, re-capture with -Dbaseline.capture=true and read "
            + "the diff before committing it. " + firstDifference(was, now));
    }

    /**
     * And the configuration it builds is byte for byte what was blessed.
     *
     * This is the file autonomy actually runs from, so a change here is a change to the railway's
     * behaviour whatever the diagram looks like.
     */
    @Test
    public void testTheBuiltConfigurationIsWhatWasBlessed() throws Exception
    {
        AutonomySession session = openBaseline();

        String now = session.buildConfiguration();

        assertNotNull(now, "the baseline setup built nothing");

        if (capturing)
        {
            write(CONFIGURATION, now);
            return;
        }

        String was = read(CONFIGURATION);

        if (now.equals(was)) return;

        // Say WHERE, not just that. A 60kB JSON diff is not a report.
        assertEquals(firstDifference(was, now), "",
            "the configuration built from the blessed setup has changed. " + firstDifference(was, now));
    }

    /**
     * The baseline, opened as a session.
     *
     * Copied from the sample layout on the capture run, and read from `test/baseline/layout` on every
     * other - so nothing Adam does to his own railway afterwards can move this.
     */
    private AutonomySession openBaseline() throws Exception
    {
        File from = capturing ? new File("cs2_sample_layout") : LAYOUT;

        if (!from.isDirectory())
        {
            throw new SkipException(capturing
                ? "no cs2_sample_layout to capture from"
                : "no blessed baseline yet - capture one with -Dbaseline.capture=true once the "
                + "railway is in a state you have confirmed is right");
        }

        if (capturing)
        {
            copy(new File(from, "config"), new File(LAYOUT, "config"));
        }

        String path = "file:///" + LAYOUT.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

        AutonomySession session = new AutonomySession(LAYOUT);

        session.open(pages);

        return session;
    }

    /**
     * The reduced railway as sorted text.
     */
    private String describe(GraphReducer reducer)
    {
        List<String> lines = new ArrayList<>();

        for (java.util.Map.Entry<TileKey, GraphReducer.ReducedPoint> point
            : reducer.getPoints().entrySet())
        {
            lines.add("point " + point.getKey() + " s88=" + point.getValue().getS88()
                + " station=" + point.getValue().isStation() + " name=" + point.getValue().getName());
        }

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            lines.add("edge " + edge.getStart() + " -> " + edge.getEnd()
                + " over " + edge.getPath().size() + " squares");
        }

        Collections.sort(lines);

        StringBuilder out = new StringBuilder();

        for (String line : lines) out.append(line).append("\n");

        return out.toString();
    }

    /**
     * Where two texts first differ, as a sentence rather than as two files.
     */
    private String firstDifference(String was, String now)
    {
        if (was.equals(now)) return "";

        String[] before = was.split("\n");
        String[] after = now.split("\n");

        for (int i = 0; i < Math.min(before.length, after.length); i++)
        {
            if (before[i].equals(after[i])) continue;

            return "First difference at line " + (i + 1) + ".\n  blessed: "
                + trim(before[i]) + "\n  now:     " + trim(after[i]);
        }

        return "The blessed copy has " + before.length + " lines and this one has " + after.length + ".";
    }

    private String trim(String line)
    {
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }

    private String read(File file) throws Exception
    {
        if (!file.isFile())
        {
            throw new SkipException("no blessed " + file.getName() + " yet - capture one with "
                + "-Dbaseline.capture=true once the railway is in a state you have confirmed");
        }

        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private void write(File file, String what) throws Exception
    {
        file.getParentFile().mkdirs();

        Files.write(file.toPath(), what.getBytes(StandardCharsets.UTF_8));

        System.out.println("captured " + file.getPath() + " (" + what.length() + " bytes)");
    }

    private void copy(File from, File to) throws Exception
    {
        if (!from.isDirectory()) return;

        to.mkdirs();

        File[] children = from.listFiles();

        if (children == null) return;

        for (File child : children)
        {
            File target = new File(to, child.getName());

            if (child.isDirectory()) copy(child, target);
            else Files.copy(child.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
