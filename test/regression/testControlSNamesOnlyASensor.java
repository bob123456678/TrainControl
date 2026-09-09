package regression;

import java.io.File;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.file.CS2File;

/**
 * Control+S names a sensor, and does nothing on plain track.
 *
 * Adam, 2026-09-08, MT-313: *"it works on any tile in the autonomy editor, not just sensors. fix that.
 * correctly does not fire outside of the autonomy editor."*
 *
 * **One question with two answers.** The right-click menu offers Rename only inside `if (isPoint)` -
 * a square the reducer knows as a Point, which is to say one with a sensor. Control+S reaches
 * `promptNameFor`, which asked whether the tile was null and nothing else, so the key put a naming
 * dialog on plain track: a name written there is held by nothing and read by nobody.
 *
 * That is `guard-and-affordance-same-question` again, and the repair is the same one: the panel owns
 * the predicate and both doors ask it.
 *
 * Measured against the real sample layout rather than a fixture, because "which squares are Points"
 * is exactly what a hand-built graph cannot tell you - every point in one is a Point by construction.
 *
 * @author Adam
 */
public class testControlSNamesOnlyASensor
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static AutonomySession session;

    private static List<LayoutDiagram> pages;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // A COPY of the real layout, opened before the model (OB-111).
        sandbox = // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and `test/layouts/live-snapshot`).
        //
        // The SHAPE is what this class is about, and the shape is the same in both.  Where his trains
        // are standing, how long they are and which side they came in by are not, and reading those
        // off the live folder is how `testTheLengthGuardsOnTheRealLayout` came to assert that the
        // 2-8-4 stood at BottomMainB - it is at BottomMainA now, and that class was red for a reason
        // that had nothing to do with any guard.  A fixture that moves while nobody is looking makes
        // every class over it say something different every week.
        support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, false);
        model.stop();

        String path = "file:///"
            + sandbox.getFolder().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        pages = support.LayoutSandbox.wired(model, parser);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * A square the reducer knows as a Point can be named.
     *
     * The control on the test below: if `canBeNamed` simply answered false, that one would pass and
     * this would fail, and the key would have stopped working on the squares it is for.
     *
     * @throws Exception on a failure to read the layout
     */
    @Test
    public void testASensorSquareCanBeNamed() throws Exception
    {
        assertNotNull(session.getReducer(), "the sample layout did not reduce, so nothing was tested");

        TileKey sensor = null;

        for (TileKey key : session.getReducer().getPoints().keySet())
        {
            sensor = key;

            break;
        }

        assertNotNull(sensor,
            "the reduced layout has no Points at all, so this test cannot tell a sensor from anything"
            + " else and would pass whatever the rule did");

        assertTrue(session.canBeNamed(sensor),
            "Control+S refuses to name " + sensor + ", which IS a sensor square and is exactly what the"
            + " shortcut is for. The right-click menu offers Rename here");
    }

    /**
     * Plain track cannot be named, because a name written there is held by nothing.
     *
     * MUTATION: this is MT-313. With `canBeNamed` answering `tile != null` - what `promptNameFor`
     * asked before - it fails, naming a square the menu would never have offered.
     *
     * @throws Exception on a failure to read the layout
     */
    @Test
    public void testPlainTrackCannotBeNamed() throws Exception
    {
        TileKey plain = null;

        for (LayoutDiagram page : pages)
        {
            for (LayoutDiagramComponent tile : page.getAll())
            {
                if (tile == null || plain != null) continue;

                TileKey key = new TileKey(page.getName(), tile.getX(), tile.getY());

                // A tile that IS drawn and is NOT a Point: track the reducer has no station for.
                if (!session.getReducer().getPoints().containsKey(key)) plain = key;
            }
        }

        assertNotNull(plain,
            "every drawn tile on this layout is a Point, so there is no plain track to test the rule"
            + " against and this would pass however the rule behaved");

        assertFalse(session.canBeNamed(plain),
            "Control+S offers to name " + plain + ", which is plain track and not a sensor. The"
            + " right-click menu does not offer Rename there, because a name written on track the"
            + " reducer has no Point for is held by nothing and read by nobody (MT-313)");
    }
}
