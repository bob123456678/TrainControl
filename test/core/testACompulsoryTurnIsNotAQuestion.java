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
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and `test/layouts/live-snapshot`).
        //
        // The SHAPE is what this class is about, and the shape is the same in both.  Where his trains
        // are standing, how long they are and which side they came in by are not, and reading those
        // off the live folder is how `testTheLengthGuardsOnTheRealLayout` came to assert that the
        // 2-8-4 stood at BottomMainB - it is at BottomMainA now, and that class was red for a reason
        // that had nothing to do with any guard.  A fixture that moves while nobody is looking makes
        // every class over it say something different every week.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

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
        // FIFTY UNITS, ON EVERY TRAIN THE SNAPSHOT HAS STANDING, kept for ever until now.
        //
        // `testCoveredTrackResolvesToSquaresTheDiagramDraws` measures every placed locomotive at 50 so
        // that each is lying across whatever is behind it. `init()` opens Adam's own locomotive
        // database rather than an empty one, so those lengths were writes to his railway - silent
        // ones, and the next thing to read them is the anti-collision rule.
        giveTheLengthsBack();

        if (sandbox != null) sandbox.close();
    }
    /**
     * The lengths this class has changed on Adam's own locomotives, and what they were.
     *
     * `MarklinControlStation.init` opens his real locomotive database rather than an empty one, and
     * the layout sandbox does not freeze it - it copies the layout folder and nothing else. So a
     * length set on a borrowed train is a change to his railway that outlives the run.
     *
     * A map rather than a field per site, because what gets borrowed here is "whatever is standing",
     * and how many that is depends on the snapshot.
     */
    private static final java.util.Map<org.traincontrol.base.Locomotive, Integer> LENGTHS_WE_CHANGED =
        new java.util.LinkedHashMap<>();

    /**
     * Remembers a borrowed train's length before this class writes over it.
     *
     * FIRST VALUE WINS: a train measured twice in one run must go back to what it was before the
     * FIRST change, not to what the previous claim left on it.
     *
     * @param loc the borrowed train
     */
    private static void borrowTheLengthOf(org.traincontrol.base.Locomotive loc)
    {
        if (loc == null || LENGTHS_WE_CHANGED.containsKey(loc)) return;

        LENGTHS_WE_CHANGED.put(loc, loc.getTrainLength());
    }

    /**
     * Puts every borrowed length back.
     *
     * Each on its own, so one failure does not keep the others borrowed. A length left behind is
     * silent: nothing on screen says a train is measured at fifty, and the next thing to read it is
     * the anti-collision rule.
     */
    private static void giveTheLengthsBack()
    {
        for (java.util.Map.Entry<org.traincontrol.base.Locomotive, Integer> was
             : LENGTHS_WE_CHANGED.entrySet())
        {
            try
            {
                was.getKey().setTrainLength(was.getValue() == null ? 0 : was.getValue());
            }
            catch (Exception cannotPutItBack)
            {
            }
        }

        LENGTHS_WE_CHANGED.clear();
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

            borrowTheLengthOf(point.getCurrentLocomotive());

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

    // ------------------------------------------------------------------------------------------------ RSA50, built lines

    /**
     * A line of three stations - Alpha, Beta, Gamma - set up as an operator would and built into the running layout by the
     * setup's own builder (RSA50-A1: a claim whose railway is assembled by hand can give a Point a block the build never
     * emits, and pass on a shape no user has).  Alpha is a compulsory turn; Gamma is one too, or a may-turn square at the
     * end of the line; Beta may be a may-turn square.
     *
     * @param folder where the setup is kept
     * @param tag what every name starts with
     * @param address the first of the three sensors
     * @param gammaMay whether the end of the line is marked "may" rather than "must"
     * @param betaMay whether the middle station is marked "may"
     * @return the setup
     * @throws Exception from the setup
     */
    private static AutonomySession aLine(File folder, String tag, int address, boolean gammaMay, boolean betaMay)
        throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 7, 3, null, null);

        org.traincontrol.base.Accessory.accessoryDecoderType mm2 = org.traincontrol.base.Accessory.accessoryDecoderType.MM2;

        for (int i = 0; i < 3; i++) model.newFeedback(address + i, null);

        page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK, 1, 1, 0, 0, address, address,
            mm2, null);
        page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, mm2, null);
        page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK, 3, 1, 0, 0, address + 1,
            address + 1, mm2, null);
        page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, mm2, null);
        page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK, 5, 1, 0, 0, address + 2,
            address + 2, mm2, null);
        page.setPageId("1");

        AutonomySession line = new AutonomySession(folder);

        line.open(java.util.Arrays.asList(page));
        line.initialize(tag);

        TileKey alpha = new TileKey("main", 1, 1);
        TileKey beta = new TileKey("main", 3, 1);
        TileKey gamma = new TileKey("main", 5, 1);

        for (TileKey t : new TileKey[] {alpha, beta, gamma}) line.setStation(t, true);

        line.setPointName(alpha, tag + "Alpha");
        line.setPointName(beta, tag + "Beta");
        line.setPointName(gamma, tag + "Gamma");

        line.setPointProperty(alpha, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);

        if (gammaMay)
        {
            line.setPointFlag(gamma, org.traincontrol.automationui.AutonomyBuilder.CAN_REVERSE, true);
            line.setPointProperty(gamma, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, null);
        }
        else
        {
            line.setPointProperty(gamma, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);
        }

        if (betaMay)
        {
            line.setPointFlag(beta, org.traincontrol.automationui.AutonomyBuilder.CAN_REVERSE, true);
            line.setPointProperty(beta, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, null);
        }

        line.rebuild();

        model.parseAuto(line.buildConfiguration());

        assertNotNull(model.getAutoLayout(), "precondition: the line did not build");

        return line;
    }

    /** The edge into this copy from a copy whose name starts so, or null. */
    private static org.traincontrol.automation.Edge into(Layout rail, Point end, String from)
    {
        for (org.traincontrol.automation.Edge edge : rail.getEdges())
        {
            if (edge.getEnd() == end && edge.getStart().getName().startsWith(from)) return edge;
        }

        return null;
    }

    /** The copy whose name starts so, of the kind asked for (turning or not), that an edge from a copy of that name reaches. */
    private static org.traincontrol.automation.Edge intoA(Layout rail, String to, boolean turning, String from)
    {
        for (org.traincontrol.automation.Edge edge : rail.getEdges())
        {
            Point end = edge.getEnd();

            if (!end.getName().startsWith(to) || (end.isTerminus() || end.isReversing()) != turning) continue;

            if (edge.getStart().getName().startsWith(from)) return edge;
        }

        return null;
    }

    /** Where a train stands on the running layout: the Point that holds it. */
    private static Point standing(Layout rail, org.traincontrol.base.Locomotive x)
    {
        for (Point p : rail.getPoints())
        {
            if (p.getCurrentLocomotive() == x) return p;
        }

        return null;
    }

    /** Waits for something to come true, or gives up. */
    private static boolean waitFor(java.util.function.BooleanSupplier what, long ms) throws InterruptedException
    {
        long until = System.currentTimeMillis() + ms;

        while (System.currentTimeMillis() < until)
        {
            if (what.getAsBoolean()) return true;

            Thread.sleep(20);
        }

        return what.getAsBoolean();
    }

    /** Sends a train by hand through the hand door's own entry to the railway, and plays its arrival sensor. */
    private static void sendByHand(Layout rail, org.traincontrol.base.Locomotive x, List<org.traincontrol.automation.Edge> path,
        Layout.ReversalPolicy answer) throws Exception
    {
        Point end = path.get(path.size() - 1).getEnd();

        Thread journey = new Thread(() -> rail.executePathByHand(path, x, 30, answer, -1), "built-line-claim");

        journey.setDaemon(true);
        journey.start();

        assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
            "precondition: the hand send did not start: " + Layout.getLastError());

        model.setFeedbackState(end.getS88(), true);

        journey.join(20000);

        model.setFeedbackState(end.getS88(), false);

        assertFalse(journey.isAlive(), "precondition: the hand send did not end");
    }

    /** The operator's answer at the destination, as the door hands it over once asked. */
    private static Layout.ReversalPolicy answered(final boolean turn, final Point end, final Layout.ReversalPolicy asking)
    {
        return new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(org.traincontrol.base.Locomotive train, Point at)
            {
                return turn && at == end;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return at == end && asking.asksAbout(at);
            }
        };
    }

    /** What the window does with the railway's record of a turn once the railway is idle (`writeTheTurns`). */
    private static void theIdleDrain(Layout rail, AutonomySession line)
    {
        java.util.Map<String, String> turned = rail.takeReversalsOnArrival();

        try
        {
            for (java.util.Iterator<java.util.Map.Entry<String, String>> pending = turned.entrySet().iterator();
                pending.hasNext();)
            {
                java.util.Map.Entry<String, String> t = pending.next();

                if (line.faceTheWayItCameIn(t.getKey(), rail.getPoint(t.getValue()), rail) != null) pending.remove();
            }
        }
        finally
        {
            rail.restoreReversalsOnArrival(turned);
        }
    }

    /** Runs a claim on a built line in a folder of its own, putting the class's railway and the train back after. */
    private static void onALine(String tag, org.traincontrol.base.Locomotive x, ThrowingClaim claim) throws Exception
    {
        File folder = java.nio.file.Files.createTempDirectory("tc-built-line-" + tag).toFile();

        final int speedWas = x.getPreferredSpeed();

        // A TRAIN THAT CAN REVERSE, whatever the locomotive database says of this one: one that cannot is not turned at a
        // square it can drive through, and the claims below are about trains that are
        final boolean reversibleWas = x.isReversible();

        borrowTheLengthOf(x);

        try
        {
            x.setSpeed(0);
            x.setTrainLength(1);
            x.setPreferredSpeed(30);
            x.setReversible(true);

            claim.run(folder);
        }
        finally
        {
            Layout rail = model.getAutoLayout();

            if (rail != null) rail.stopLocomotives();

            x.setSpeed(0);
            x.setPreferredSpeed(speedWas);
            x.setReversible(reversibleWas);

            model.parseAuto(session.buildConfiguration());

            deleteAll(folder);
        }
    }

    /** A claim run on a built line. */
    private interface ThrowingClaim
    {
        void run(File folder) throws Exception;
    }

    private static void deleteAll(File f)
    {
        File[] inside = f.listFiles();

        if (inside != null) for (File c : inside) deleteAll(c);

        f.delete();
    }

    /**
     * The end of a line marked "Trains May Change Direction Here", as the build makes it - one copy, no block - turns a
     * train sent there by hand, and the operator is not asked (RSA50-A1).  Round 82 left the copy with no block out, as
     * a Point of a hand-written railway, and the build gives a single copy no block: so the operator was asked, "keep"
     * was honoured, and the train stood facing the end of the line with its next journey routed back.
     *
     * MUTATION: leave a copy with no block out of `Layout.noCopyFacesOnFrom` again, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheEndOfALineMarkedMayTurnsATrainNobodyIsAskedAbout() throws Exception
    {
        final org.traincontrol.base.Locomotive x = model.getLocByName(model.getLocList().get(0));

        onALine("EOLM", x, folder ->
        {
            AutonomySession line = aLine(folder, "EOLM", 1931, true, false);

            Layout rail = model.getAutoLayout();

            Point gamma = rail.getPoint("EOLMGamma");

            assertNotNull(gamma, "precondition: the end of the line built to no Point of its own name");

            assertTrue(gamma.isTerminus() && gamma.getBlock() == null, "precondition: the build no longer makes the end of"
                + " a line marked may one turning copy with no block - the shape RSA50-A1 is about");

            Layout.ReversalPolicy asking = ManualReversalPrompt.forOperator(line, null);

            assertTrue(asking.asksAbout(gamma), "precondition: the setup no longer counts the end of the line as a square"
                + " the operator has a say over");

            List<org.traincontrol.automation.Edge> path = java.util.Arrays.asList(into(rail, gamma, "EOLMBeta"));

            assertNotNull(path.get(0), "precondition: no edge from Beta to the end of the line");

            assertTrue(ManualReversalPrompt.destinationAskedAbout(asking, rail, path) == null, "the operator is asked"
                + " whether to keep the direction at the end of a line, where keeping it leaves the train facing the end"
                + " and its next journey driving it there (RSA50-A1)");

            path.get(0).getStart().setLocomotive(x);

            boolean forward = x.goingForward();

            sendByHand(rail, x, path, answered(false, gamma, asking));

            assertTrue(x.goingForward() != forward, "a train sent by hand to the end of a line marked may, kept, was not"
                + " turned: it stands facing the end of the line while its next journey is routed back (RSA50-A1)");
        });
    }

    /**
     * A timetable entry recorded with "keep" plays back turned where the railway turns every train (RSA50-A2): the end of
     * a line marked "may" - recorded before round 83 - or a station marked "must" after the recording.  The compulsory
     * turn is asked before the recorded answer.
     *
     * MUTATION: answer the recorded "keep" before the compulsory turn, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAKeepRecordedWhereTheRailwayTurnsEveryTrainPlaysBackTurned() throws Exception
    {
        final org.traincontrol.base.Locomotive x = model.getLocByName(model.getLocList().get(0));

        final long stuckWas = Layout.TIMETABLE_STUCK_MS;

        onALine("EOLT", x, folder ->
        {
            aLine(folder, "EOLT", 1934, true, false);

            Layout rail = model.getAutoLayout();

            Point gamma = rail.getPoint("EOLTGamma");

            List<org.traincontrol.automation.Edge> path = java.util.Arrays.asList(into(rail, gamma, "EOLTBeta"));

            org.traincontrol.automation.TimetablePath entry = new org.traincontrol.automation.TimetablePath(x, path, 0);

            entry.setTurnAtTheEnd(false);

            rail.setTimetable(java.util.Arrays.asList(entry));

            path.get(0).getStart().setLocomotive(x);

            boolean forward = x.goingForward();

            Layout.TIMETABLE_STUCK_MS = 8000;

            try
            {
                Thread runner = new Thread(rail::executeTimetable, "recorded-keep-playback");

                runner.setDaemon(true);
                runner.start();

                assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
                    "precondition: the entry did not start: " + Layout.getLastError());

                model.setFeedbackState(gamma.getS88(), true);

                assertTrue(waitFor(() -> !rail.getActiveLocomotives().containsKey(x), 15000), "precondition: the entry"
                    + " did not end");

                model.setFeedbackState(gamma.getS88(), false);

                assertTrue(x.goingForward() != forward, "a timetable entry recorded with keep was played back keeping the"
                    + " train facing the end of a line - a turn every train must make (RSA50-A2)");
            }
            finally
            {
                Layout.TIMETABLE_STUCK_MS = stuckWas;
            }
        });
    }

    /**
     * A train turned by hand at a may-turn station's plain copy is stood at once on the copy that faces the way it came
     * in - the copy the window's idle drain would stand it on, which then changes nothing (RSA50-A3).  The drain ran only
     * once the railway was idle: while another train was out, this one stood on the copy for its old heading, and its next
     * hand send set the route ahead while its decoder drove it back.
     *
     * MUTATION: leave a turned train for the idle drain again, or stand it on another copy than the drain's, and this
     * fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATrainTurnedAtAPlainCopyIsStoodFacingItsWayAtOnce() throws Exception
    {
        final org.traincontrol.base.Locomotive x = model.getLocByName(model.getLocList().get(0));

        onALine("TPC", x, folder ->
        {
            AutonomySession line = aLine(folder, "TPC", 1937, false, true);

            Layout rail = model.getAutoLayout();

            org.traincontrol.automation.Edge e = intoA(rail, "TPCBeta", false, "TPCAlpha");

            assertNotNull(e, "precondition: no edge from Alpha onto a plain copy of Beta");

            Point plain = e.getEnd();

            assertTrue(plain.getCopyFacing() != null, "precondition: Beta's copies do not record which way each faces");

            Layout.ReversalPolicy asking = ManualReversalPrompt.forOperator(line, null);

            e.getStart().setLocomotive(x);

            final List<org.traincontrol.automation.Edge> path = java.util.Arrays.asList(e);

            Thread journey = new Thread(() -> rail.executePathByHand(path, x, 30, answered(true, plain, asking), -1),
                "turned-at-a-plain-copy");

            journey.setDaemon(true);
            journey.start();

            assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
                "precondition: the hand send did not start: " + Layout.getLastError());

            // ON ITS WAY, the copy it will stand on - which the caption's arrow and the icon read - faces the way it will
            // face once turned
            Point will = rail.copyItWillStandOn(x);

            assertTrue(will != null && e.getEntrySide().equals(will.getCopyFacing()), "on its way to be turned at a plain"
                + " copy, the train is said to be going to stand on " + will + ", facing its old heading - the caption's"
                + " arrow and the icon show the way it will not face (RSA50-A3)");

            model.setFeedbackState(plain.getS88(), true);

            journey.join(20000);

            model.setFeedbackState(plain.getS88(), false);

            assertFalse(journey.isAlive(), "precondition: the hand send did not end");

            Point on = standing(rail, x);

            assertNotNull(on, "precondition: the train stands nowhere after its arrival");

            assertTrue(e.getEntrySide().equals(on.getCopyFacing()), "a train turned at a plain copy still stands on a copy"
                + " facing its old heading (" + on.getName() + ", facing " + on.getCopyFacing() + ") until the railway is"
                + " idle - its next hand send is routed ahead while its decoder drives it back (RSA50-A3)");

            theIdleDrain(rail, line);

            assertTrue(standing(rail, x) == on, "the window's idle drain moved the turned train again, onto "
                + standing(rail, x) + ": the arrival did not stand it where the drain would (RSA50-A3, RSA50-B1)");

            // AND THE DRAIN STILL WRITES THE TURN into the setup, from the record the arrival pointed at the copy
            assertTrue(e.getEntrySide().equals(String.valueOf(line.getFacing(new TileKey("main", 3, 1)))), "the window's"
                + " idle drain wrote no turn into the setup: its record still names the copy the train has left");
        });
    }

    /**
     * A timetable recorded by hand where the operator turned a train at a may-turn station plays back (RSA50-B1): the
     * window's idle drain stood the turned train on another copy between the recorded sends, and the next entry was
     * recorded from it; playback is never idle, so the train was still on the copy it turned on and the next entry could
     * not start.  It is stood there at the arrival now, in the recording and in the playback alike.
     *
     * MUTATION: leave a turned train for the idle drain again, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATimetableRecordedWithATurnPlaysBack() throws Exception
    {
        final org.traincontrol.base.Locomotive x = model.getLocByName(model.getLocList().get(0));

        final long stuckWas = Layout.TIMETABLE_STUCK_MS;

        onALine("RWT", x, folder ->
        {
            AutonomySession line = aLine(folder, "RWT", 1940, false, true);

            Layout rail = model.getAutoLayout();

            org.traincontrol.automation.Edge first = intoA(rail, "RWTBeta", true, "RWTAlpha");

            assertNotNull(first, "precondition: no edge from Alpha onto the turning copy of Beta");

            Layout.ReversalPolicy asking = ManualReversalPrompt.forOperator(line, null);

            Point start = first.getStart();

            start.setLocomotive(x);

            boolean forward = x.goingForward();

            // RECORDED: turned at Beta, the window's drain between the sends as at any idle moment, and back to Alpha
            rail.setTimetableCapture(true);

            sendByHand(rail, x, java.util.Arrays.asList(first), answered(true, first.getEnd(), asking));

            theIdleDrain(rail, line);

            Point turnedOn = standing(rail, x);

            org.traincontrol.automation.Edge second = null;

            for (org.traincontrol.automation.Edge edge : rail.getEdges())
            {
                if (edge.getStart() == turnedOn && edge.getEnd().getName().startsWith("RWTAlpha")) second = edge;
            }

            assertNotNull(second, "precondition: the turned train, on " + turnedOn + ", has no way back to Alpha");

            sendByHand(rail, x, java.util.Arrays.asList(second), ManualReversalPrompt.KEEP_DIRECTION);

            rail.setTimetableCapture(false);

            assertTrue(rail.getTimetable().size() == 2, "precondition: the two sends were not recorded: "
                + rail.getTimetable());

            // PLAYED BACK from where the recording began, facing as it did
            x.setSpeed(0);

            start.setLocomotive(x);

            if (x.goingForward() != forward) x.switchDirection();

            for (org.traincontrol.automation.TimetablePath entry : rail.getTimetable())
            {
                entry.setSecondsToNext(0);
                entry.setExecutionTime(0);
            }

            final Point alpha = second.getEnd();
            final Point beta = first.getEnd();

            Layout.TIMETABLE_STUCK_MS = 8000;

            try
            {
                Thread runner = new Thread(rail::executeTimetable, "recorded-turn-playback");

                runner.setDaemon(true);
                runner.start();

                assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
                    "precondition: the first entry did not start: " + Layout.getLastError());

                model.setFeedbackState(beta.getS88(), true);

                assertTrue(waitFor(() -> !rail.getActiveLocomotives().containsKey(x), 15000), "precondition: the first"
                    + " entry did not end");

                model.setFeedbackState(beta.getS88(), false);

                assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
                    "the second entry, recorded from the copy the turned train was stood on, never started - playback is"
                    + " never idle, and the train was still on the copy it turned on (RSA50-B1): "
                    + Layout.getLastError());

                model.setFeedbackState(alpha.getS88(), true);

                assertTrue(waitFor(() -> !rail.getActiveLocomotives().containsKey(x), 15000), "the second entry did not"
                    + " arrive");

                model.setFeedbackState(alpha.getS88(), false);
            }
            finally
            {
                Layout.TIMETABLE_STUCK_MS = stuckWas;
            }
        });
    }

    /**
     * A train a plan turns - autonomy, Return Home, staging, a timetable recorded from autonomy - stays on the turning
     * copy it arrived on, where the plan's next leg starts (RSA51-B1).  Round 83 stood every turned train at once on the
     * copy the window's idle drain would choose, a plain copy of the other approach where there is one; Return Home's
     * planner models the train on the move's end and plans its next move from there (AMH-D4), so that move was refused
     * at its start check three times and the run stopped half-staged.  The re-stand at the arrival is for the turns an
     * operator answered, whose next journey nobody has planned.
     *
     * MUTATION: stand a train a plan turned on the drain's copy again, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATrainAPlanTurnsStaysWhereItsNextLegStarts() throws Exception
    {
        final org.traincontrol.base.Locomotive x = model.getLocByName(model.getLocList().get(0));

        onALine("PTS", x, folder ->
        {
            aLine(folder, "PTS", 1943, false, true);

            Layout rail = model.getAutoLayout();

            org.traincontrol.automation.Edge in = intoA(rail, "PTSBeta", true, "PTSAlpha");

            assertNotNull(in, "precondition: no edge from Alpha onto the turning copy of Beta");

            final Point turning = in.getEnd();

            in.getStart().setLocomotive(x);

            // THE PLAN'S LEG, with the policy every plan hands in
            sendByHand(rail, x, java.util.Arrays.asList(in), Layout.ALWAYS_REVERSE);

            assertTrue(standing(rail, x) == turning, "a train a plan turned at a may-turn station's turning copy was moved"
                + " onto " + standing(rail, x) + " - the plan's next leg starts from the turning copy, and is refused"
                + " (RSA51-B1)");

            // AND THAT NEXT LEG STARTS: from the turning copy, back the way it came
            org.traincontrol.automation.Edge back = null;

            for (org.traincontrol.automation.Edge edge : rail.getEdges())
            {
                if (edge.getStart() == turning && edge.getEnd().getName().startsWith("PTSAlpha")) back = edge;
            }

            assertNotNull(back, "precondition: the turning copy has no way back to Alpha");

            sendByHand(rail, x, java.util.Arrays.asList(back), Layout.ALWAYS_REVERSE);

            assertTrue(back.getEnd().getCurrentLocomotive() == x || standing(rail, x).isSamePlaceAs(back.getEnd()),
                "the plan's next leg from the turning copy did not take the train back to Alpha");
        });
    }
}
