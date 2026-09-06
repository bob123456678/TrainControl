package core;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The Auto half of the Path Type radio means what autonomy means.
 *
 * Adam, 2026-09-05: **"Radio button Auto or Manual dictates what is in scope for the routing check."**
 *
 * The radio's Auto setting reports a station as somewhere autonomy will never choose, and it decides
 * that from `AutonomySession.stationsAutonomyWillNotChoose()` - a rule computed from the DIAGRAM.  The
 * railway decides it somewhere else entirely: `Layout` refuses a destination that is reversing or is
 * not an auto destination before it is a candidate at all.  Two rules, two files, one question.
 *
 * `guard-and-affordance-same-question` is the whole reason this exists.  Where this application has
 * asked one question in two places, the two answers have differed - and a check that reports what
 * autonomy will do is worth nothing if it disagrees with what autonomy does.  Nothing covered this set
 * before the radio started reading it.
 *
 * **This does not assert the rule is right.**  It asserts the two statements of it agree, which is the
 * failure that would make the new control lie.  If Adam changes what autonomy may choose, both sides
 * move together or this goes red - which is the point.
 */
public class testTheAutoTierScopeMatchesTheRuntime
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;

    @BeforeClass
    public static void setUp() throws Exception
    {
        // OB-111: the sandbox is opened BEFORE the model is built.
        sandbox = support.LayoutSandbox.open(new File("cs2_sample_layout"));

        model = init(null, true, false, false, false);

        String path = "file:///"
            + sandbox.getFolder().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

        model.parseAuto(session.buildConfiguration());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDown() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * Every square the diagram excludes, the running layout excludes too - and the other way about.
     */
    @Test
    public void testTheDiagramAndTheRailwayAgreeAboutWhereAutonomyWillGo() throws Exception
    {
        Layout built = model.getAutoLayout();

        assertTrue(built != null, "the sample configuration did not build, so nothing was compared");

        Set<TileKey> excludedByTheDiagram = session.stationsAutonomyWillNotChoose();

        List<String> disagreements = new LinkedList<>();

        int stations = 0;

        // PER SQUARE, NOT PER POINT (CONF-B6).
        //
        // The first version of this compared each Point against the diagram's answer about its
        // SQUARE, and those are different questions once a square is split.  A may-reverse station
        // has a turning copy the railway refuses and a plain copy it accepts; the diagram has one
        // answer for the square, and the honest one is "can autonomy choose ANY copy of this".
        //
        // It passed anyway, because no square on this layout is split - which is exactly the kind of
        // agreement that means nothing, and `testEverySquareOnThisLayoutBuildsToOneCopy` is the
        // record of why.
        java.util.Map<TileKey, Boolean> railwayWillChoose = new java.util.LinkedHashMap<>();

        for (Point point : built.getPoints())
        {
            if (!point.isDestination()) continue;

            TileKey square = session.getStationIndex().squareOf(point.getName());

            if (square == null) continue;

            // The railway's own rule, quoted rather than paraphrased: `Layout:3576` refuses a
            // destination that is reversing or is not an auto destination.
            boolean thisCopy = !point.isReversing() && point.isAutoDestination();

            railwayWillChoose.put(square,
                Boolean.TRUE.equals(railwayWillChoose.get(square)) || thisCopy);
        }

        stations = railwayWillChoose.size();

        for (java.util.Map.Entry<TileKey, Boolean> square : railwayWillChoose.entrySet())
        {
            boolean railwayRefuses = !square.getValue();

            boolean diagramRefuses = excludedByTheDiagram.contains(square.getKey());

            if (railwayRefuses != diagramRefuses)
            {
                disagreements.add(square.getKey() + ": the railway "
                    + (railwayRefuses ? "will not" : "will") + " choose it, the diagram says it "
                    + (diagramRefuses ? "will not" : "will"));
            }
        }

        assertTrue(stations >= 2,
            "no station was compared, so this asserted nothing about anything (" + stations + ")");

        assertTrue(disagreements.isEmpty(),
            "the Path Type check would report the opposite of what autonomy does: " + disagreements);
    }

    /**
     * And the set is neither everything nor nothing, or the agreement above is trivially true.
     *
     * `assert-the-variable-not-the-control`: two rules that both answer "no" to every station agree
     * perfectly and tell nobody anything.  This is what makes the comparison above a real one.
     */
    @Test
    public void testTheSetActuallyDividesTheStations() throws Exception
    {
        Set<TileKey> excluded = session.stationsAutonomyWillNotChoose();

        int stations = 0;

        for (Point point : model.getAutoLayout().getPoints())
        {
            if (point.isDestination() && session.getStationIndex().squareOf(point.getName()) != null)
            {
                stations++;
            }
        }

        assertFalse(excluded.isEmpty(),
            "no station is excluded from autonomy on the sample layout, so the Auto setting of the"
            + " Path Type radio can never say anything different from Manual and the comparison"
            + " above passes on a rule that never fires");

        assertTrue(excluded.size() < stations,
            "every station is excluded from autonomy (" + excluded.size() + " of " + stations
            + "), which would mean autonomy has nowhere to go at all - and would also make the"
            + " agreement checked above true for a reason that has nothing to do with the rule");
    }
}
