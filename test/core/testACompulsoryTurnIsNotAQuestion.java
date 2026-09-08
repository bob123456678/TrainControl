package core;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.ManualReversalPrompt;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A square the railway turns every train at is never put to the operator (REG6-A1).
 *
 * **This one could have derailed a train, so it is worth being exact about.**  The prompt's
 * `asksAbout` used to begin `if (at.isReversing()) return true;`.  For a MUST-reverse square,
 * `AutonomyBuilder` emits only turning copies and flags every one of them `reversing` - so that clause
 * could not tell a square Adam marked "trains may turn here" from one the railway turns every train
 * at.  The very next clause excludes `mandatoryTurnTiles()` with some care; the first one put them
 * straight back.
 *
 * What made it dangerous rather than untidy is the default.  A turning copy's only outgoing edges
 * leave by the side the train arrived from.  "Keep direction" is the default button, the Escape
 * answer, and the answer used when the dialog cannot be shown at all - so the likeliest reply to a
 * question that should never have been asked sends a train forward onto track its path does not hold.
 *
 * The repair is that the SETUP is the only source. That is also the right answer for an unrelated
 * reason: `canReverse` is never emitted to `parseAuto`, so nothing in the running layout can answer
 * "may trains turn here", and anything there that appears to is answering a different question.
 *
 * MUTATION, confirmed: put `if (at.isReversing()) return true;` back at the top of `asksAbout` and
 * `testAMustTurnSquareIsNotAskedAbout` goes red.
 */
public class testACompulsoryTurnIsNotAQuestion
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

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDown() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * Marked must-reverse, and the operator is not consulted about it.
     */
    @Test
    public void testAMustTurnSquareIsNotAskedAbout() throws Exception
    {
        TileKey square = anOrdinaryStation();

        // "Trains must turn here" - the instruction the builder DOES emit, as `reversing`.
        session.setPointProperty(square, "mustReverse", true);

        model.parseAuto(session.buildConfiguration());

        Layout built = model.getAutoLayout();

        assertNotNull(built, "the configuration did not build, so nothing was asked of anything");

        assertTrue(session.mandatoryTurnTiles().contains(square),
            "the square did not become a compulsory turn, so this test is not about one and proves"
            + " nothing about REG6-A1");

        Point copy = copyOf(built, square);

        assertNotNull(copy, "the must-turn square built to no Point at all");

        // MEASURED, NOT ASSUMED: on this layout the copy does NOT carry the flag.
        //
        // Every named square here builds to one copy and the builder cannot split any of them, so a
        // compulsory turn produces no turning copy and `isReversing()` stays false. That means the
        // exact shape REG6-A1 needs - a `reversing` copy that is a MUST-turn - cannot be constructed
        // on this railway, and the assertion below, while true, is not exercising the clause that was
        // removed.  Said out loud rather than left as a green tick: `test-green-is-not-no-failures`.
        //
        // So the clause itself is checked as source, immediately after.  Between them the rule is
        // covered both ways this layout allows.
        boolean thisLayoutCanBuildTheShape = copy.isReversing();

        assertFalse(ManualReversalPrompt.forOperator(session, null).asksAbout(copy),
            "REG6-A1: the operator is asked whether to turn a train at a square the railway turns"
            + " EVERY train at. A turning copy leaves only by the side the train came in at, so"
            + " 'keep direction' - the default button, the Escape answer and the cannot-ask answer -"
            + " drives the train forward off its reserved path.");

        // AND THE CLAUSE THAT CAUSED IT IS GONE, which is the half this layout cannot show by running.
        //
        // `guard-knows-only-what-it-lists` cuts the other way here and is the reason this is worth
        // writing: the behavioural assertion above knows only about squares whose copies carry no
        // flag, and would report clean about every railway where they do - which is every railway the
        // defect could actually hurt.
        String prompt = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/ManualReversalPrompt.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        // The one inside forOperator, which is the policy both manual doors hand over. Found by the
        // method it sits in rather than by position, so reordering the file does not silently move
        // this check onto a different asksAbout.
        int door = prompt.indexOf("public static org.traincontrol.automation.Layout.ReversalPolicy"
            + " forOperator(");

        assertTrue(door > 0, "forOperator has been renamed and this check now guards nothing");

        int asks = prompt.indexOf("public boolean asksAbout(Point at)", door);

        assertTrue(asks > 0, "forOperator no longer answers asksAbout at all");

        int endOfMethod = prompt.indexOf("            }", asks);

        // COMMENTS STRIPPED FIRST. The first version of this check read the prose as though it were
        // code and failed on the paragraph explaining the very defect it guards - a guard that cannot
        // tell an explanation from an instruction is not reporting on the program at all.
        StringBuilder code = new StringBuilder();

        for (String line : prompt.substring(asks, endOfMethod).split("\\r?\\n"))
        {
            String trimmed = line.trim();

            if (!trimmed.startsWith("//")) code.append(trimmed).append(" ");
        }

        assertFalse(code.toString().contains("isReversing()"),
            "REG6-A1 is back: asksAbout consults isReversing(), which is true for every copy of a"
            + " COMPULSORY turn as well as for a may-reverse one. The setup is the only thing that"
            + " can tell them apart, because canReverse is never emitted to parseAuto at all."
            + (thisLayoutCanBuildTheShape ? "" : " (This layout cannot build a reversing copy, so"
            + " the assertion above could not catch it.)"));

        session.setPointProperty(square, "mustReverse", null);
    }

    /**
     * And the control: a square Adam DID mark may-reverse is still asked about.
     *
     * Without this, deleting `asksAbout` entirely passes the test above.
     */
    @Test
    public void testAMayReverseSquareIsStillAskedAbout() throws Exception
    {
        TileKey square = anOrdinaryStation();

        session.setPointProperty(square, "canReverse", true);

        model.parseAuto(session.buildConfiguration());

        assertTrue(session.mayTurnTiles().contains(square),
            "the square did not become a may-turn one, so the control cannot run");

        Point copy = copyOf(model.getAutoLayout(), square);

        assertNotNull(copy, "the may-reverse square built to no Point");

        assertTrue(ManualReversalPrompt.forOperator(session, null).asksAbout(copy),
            "a square marked may-reverse is no longer put to the operator, which is Adam's ruling"
            + " undone: \"may reverse should always prompt in manual mode\"");

        session.setPointProperty(square, "canReverse", null);
    }

    /**
     * The covered track reaches the diagram as squares, so it can be greyed out.
     *
     * Adam: **"locked tiles in this way should be greyed out until the train blocking it moves."**
     *
     * The rule itself lives in `Layout` and is tested there over random railways; this checks the
     * JOIN - that the runtime edges it returns actually resolve to squares the diagram draws.  A
     * mapping that silently resolves nothing returns an empty set, which greys nothing, and looks
     * exactly like a railway with no trains protruding.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testCoveredTrackResolvesToSquaresTheDiagramDraws() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        org.traincontrol.automation.Layout built = model.getAutoLayout();

        // Long enough that every train on the layout is lying across whatever is behind it.
        int trains = 0;

        for (org.traincontrol.automation.Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() == null) continue;

            point.getCurrentLocomotive().setTrainLength(50);

            trains++;
        }

        assertTrue(trains > 0,
            "no train is standing anywhere on the sample layout, so nothing can be covered and this"
            + " asserts nothing");

        java.util.Map<org.traincontrol.automation.Edge, org.traincontrol.base.Locomotive> edges =
            built.edgesCoveredByStandingTrains();

        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> squares =
            session.tilesCoveredByStandingTrains(built);

        // MEASURED, AND THE MEASUREMENT IS THE FINDING.
        //
        // With four fifty-unit trains standing on Adam's railway, NOTHING is covered.  The walk stops
        // at the first segment without a positive length, and almost nothing on this layout carries
        // one - so the protrusion rule is correct and inert here until more tile lengths are recorded.
        //
        // Said out loud rather than asserted away: a reader meeting this test needs to know that a
        // green tick here does not mean track is being blocked on his railway today.  The day lengths
        // are recorded, the branch below starts running and the join is checked for real.
        if (edges.isEmpty())
        {
            assertTrue(squares.isEmpty(),
                "no edge is covered, yet squares are being greyed out - the mapping is inventing"
                + " blocked track from nothing");

            return;
        }

        assertFalse(squares.isEmpty(),
            "the covered edges (" + edges.size() + " of them) resolve to no diagram squares at all, so"
            + " nothing would be greyed out however much track is blocked. The join from runtime Edge"
            + " to TileKey is not finding the reduced edges");
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * A named station that is not already a terminus or a turn, so the marks below mean something.
     */
    private TileKey anOrdinaryStation() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        for (Point point : model.getAutoLayout().getPoints())
        {
            if (!point.isDestination() || point.isTerminus() || point.isReversing()) continue;

            TileKey square = session.getStationIndex().squareOf(point.getName());

            if (square != null
                && !session.mandatoryTurnTiles().contains(square)
                && !session.mayTurnTiles().contains(square))
            {
                return square;
            }
        }

        throw new AssertionError("no ordinary station on the sample layout to mark, so neither this"
            + " test nor its control can run");
    }

    private Point copyOf(Layout built, TileKey square)
    {
        for (Point point : built.getPoints())
        {
            if (square.equals(session.getStationIndex().squareOf(point.getName()))) return point;
        }

        return null;
    }
}
