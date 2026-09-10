package core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer.ReducedPoint;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.file.CS2File;

/**
 * How many Points each of Adam's squares becomes - the guard the two-copies decision rests on.
 *
 * `docs/reference/two-copies-evaluation.md` answers his question - *"having two copies seems like
 * unnecessary complexity, I wonder if it can be done more easily by simply following the edges?"* -
 * and its first reason for leaving the copies alone is a measurement: how much of that complexity the
 * railway actually carries today. That measurement had no guard. It was cited as
 * `testEverySquareOnThisLayoutBuildsToOneCopy` twice in that document, once in `behaviour.md` section
 * 3 and once in `testTheAutoTierScopeMatchesTheRuntime`, and no such class or method existed anywhere
 * under `test/`. This is the guard those four sentences were describing, under the name of the
 * property it can actually assert.
 *
 * **The property they described is false, and this is how it got published.** The evaluation's
 * headline was *"every named square builds to exactly one copy, may-reverse ones included"*. Measured
 * here on the wired railway: **30 of 58 squares build to more than one Point, and 13 of the 33
 * stations do**, four of them to four Points each. The number in the document is what the same census
 * returns on a diagram parsed WITHOUT its accessories - 57 of 58 squares at one copy - because a
 * switch with no `Accessory` behind it is a tile `TileGraph` will not trace through, so the railway
 * falls from 128 reduced edges to 18, almost every square is left with a single arrival side, and a
 * single arrival side is a single copy. `LayoutSandbox.wiredPages` records that recipe defect and the
 * day it was found, which is the day after the evaluation was written.
 *
 * So the evaluation's second and third reasons stand untouched, its first does not, and **whether that
 * changes the recommendation is Adam's to decide** - it is a question about what to do with his
 * railway, not a fault to repair. What this file does is make the number true and make it watched.
 *
 * **What it asserts.**
 *
 *   - THE RULE. Every square emits the Points the setup implies: one per arrival side, plus one
 *     turning copy per arrival side where a train may turn round, less the plain copies that would be
 *     dead ends, and no plain copy at all where turning is compulsory. A square with no arrival side
 *     is emitted whole. That is checked square by square against the emitted names, so a builder
 *     change that silently splits or merges one is caught wherever it happens.
 *   - THE CENSUS. The counts above, pinned. This is the tripwire the evaluation asked for, running in
 *     the direction that turned out to matter: it goes red when the split fires MORE, and equally when
 *     it fires less.
 *   - THAT THE CENSUS LOOKED AT A RAILWAY. The edge count is asserted, because 18 edges is what
 *     produced the published number and it is indistinguishable from a real result by reading.
 *
 * Read against a `LayoutSandbox` copy of `cs2_sample_layout` (OB-111). His railway is only ever read.
 *
 * @author Adam
 */
public class testEverySquareBuildsToTheCopiesTheSetupImplies
{
    /**
     * The census, measured on the wired railway on 2026-09-08.
     *
     * A change to any of these is either an edit Adam made to his own railway - in which case update
     * the number and re-read `two-copies-evaluation.md`, because these are the figures its
     * recommendation is costed against - or a builder change that has started splitting or merging
     * squares, which is the thing this file exists to notice.
     */
    private static final int SQUARES = 58;
    private static final int STATIONS = 33;
    private static final int SPLIT_SQUARES = 30;
    private static final int SPLIT_STATIONS = 13;

    /**
     * The squares that carry the FULL split - two arrival sides and a turn at each.
     *
     * By name rather than by tile, because these are the four the rest of the documentation talks
     * about: `behaviour.md` section 5b's room census names three of them as the berths it newly
     * refuses, and it names them by their split copies - "BottomMainA (eastbound)", "BottomMainC
     * (westbound)" - which is the same fact from the other side.
     */
    private static final String[] FOUR_COPY_SQUARES =
    {
        "BottomMainB", "BottomMainC", "BottomMainPost", "RampDown"
    };

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;

    private static int edgesAtOpen;

    /**
     * Opens a throwaway copy of the operator's railway, wires it, and reduces it.
     *
     * @throws Exception when the folder cannot be read at all, which is a broken harness rather than a
     *         failing guard and is better raised than reported as skipped tests
     */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and the review's B8).
        //
        // Every figure below is a count of what Adam's railway builds to, and it moves as he works on
        // it - so this pinned four numbers against a folder with uncommitted edits in it, and a clean
        // checkout measured something else.  `live-snapshot` is the same railway with the clock
        // stopped, which is what a pin needs.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, false);

        String path = "file:///"
            + sandbox.getFolder().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        // WIRED, not merely parsed, and this class is the reason that distinction is worth stating
        // rather than assuming.  `LayoutSandbox.wired` runs the application own `wireComponents`
        // loop after the parse, which is what puts an Accessory behind each switch; a bare
        // `parseLayout` leaves them null, TileGraph refuses to trace through a switch it cannot
        // command, and the reduction falls from 128 edges to 18.  Almost every square is then left
        // with a single arrival side, a single arrival side is a single copy, and the census below
        // reports ONE split square out of fifty-eight - which is the number that was published as a
        // property of this railway.
        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

        edgesAtOpen = session.getReducer().getEdges().size();
    }

    /**
     * Puts the layout preference back.
     */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * Every square emits the Points its own settings imply.
     *
     * The rule, stated from the three things the setup knows about a square - which sides trains
     * arrive by, whether they may turn round there, whether they must - and checked against what the
     * builder actually emitted:
     *
     *   - no arrival side at all, and the square is emitted whole: one Point, no facing to record;
     *   - turning not allowed: exactly one Point per arrival side, and none of them a turning copy;
     *   - turning compulsory: exactly one Point per arrival side, and EVERY one of them a turning copy
     *     - the plain copy is not emitted, which is the whole difference between may and must;
     *   - turning optional: a turning copy per arrival side, plus the plain copies that have somewhere
     *     to go other than back the way they came.
     *
     * Plain and turning copies are told apart by their emitted names, which carry ", reverse" - so
     * this reads the configuration rather than re-deriving the split and agreeing with itself.
     *
     * MUTATION, run: stopping `AutonomyBuilder.nodesFor` emitting the turning copy fails this with
     * twenty squares named, seventeen of them reported as emitted as NO Point at all - a compulsory
     * turn has no plain copy to fall back on, so it leaves the railway entirely.
     *
     * TWO MUTATIONS THAT DO NOT FAIL IT, and they are worth writing down because they look like they
     * should. Dropping `onwards ||` from the plain-copy guard, and dropping `!must` from it, both
     * change nothing here: every compulsory turn on this railway is a dead end, so `onwards` is
     * already false at each of them and either guard alone is enough. The clause that is actually
     * load-bearing on this layout is the turning copy itself.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheCopiesAreWhatTheSquaresSettingsImply() throws Exception
    {
        AutonomyBuilder builder = session.builder(null);

        Map<TileKey, List<String>> emitted = namesPerSquare(builder);

        Set<TileKey> mayTurn = new LinkedHashSet<>(session.reversibleTiles());
        Set<TileKey> must = new LinkedHashSet<>(session.mandatoryTurnTiles());

        List<String> wrong = new ArrayList<>();

        // OVER THE SQUARES, NOT OVER WHAT WAS EMITTED.  Walking the emitted names cannot see a square
        // that came back as NOTHING - which is a real outcome of this code, not a hypothetical: remove
        // the turning copy from `nodesFor` and a compulsory-turn square emits neither copy and simply
        // disappears from the railway, silently, while every square still present looks correct.
        for (TileKey square : session.getReducer().getPoints().keySet())
        {
            List<String> names = emitted.containsKey(square)
                ? emitted.get(square) : Collections.<String>emptyList();

            if (names.isEmpty())
            {
                wrong.add(square + " is emitted as NO Point at all, so that square has left the"
                    + " railway: nothing can be sent there, nothing can be routed through it, and the"
                    + " configuration does not mention it");

                continue;
            }

            List<TilePorts.Side> sides = builder.arrivalSidesOf(square);

            boolean canTurn = mayTurn.contains(square) || must.contains(square);
            boolean compulsory = must.contains(square);

            int turning = 0;

            for (String name : names)
            {
                if (name.contains(", reverse)")) turning++;
            }

            int plain = names.size() - turning;

            String what = square + " " + names + " sides=" + sides
                + (compulsory ? " must-turn" : canTurn ? " may-turn" : "");

            if (sides.isEmpty())
            {
                if (names.size() != 1) wrong.add(what + ": no arrival side, so it should be ONE Point");

                // A square emitted as a single Point carries the bare name, so there is nothing to
                // classify: the ", reverse" suffix is only added where a square split.
                continue;
            }

            if (!canTurn && names.size() != sides.size())
            {
                wrong.add(what + ": trains may not turn here, so it should be one Point per arrival"
                    + " side (" + sides.size() + ")");
            }

            if (compulsory && names.size() != sides.size())
            {
                wrong.add(what + ": turning is compulsory here, so it should be one TURNING Point per"
                    + " arrival side (" + sides.size() + ") and no plain copy at all");
            }

            if (canTurn && !compulsory
                && (names.size() < sides.size() || names.size() > 2 * sides.size()))
            {
                wrong.add(what + ": trains may turn here, so it should be between " + sides.size()
                    + " and " + (2 * sides.size()) + " Points - a turning copy per arrival side, plus"
                    + " the plain copies that are not dead ends");
            }

            // AND THE MIX, which is where a merge or a split actually shows up.  Only asked of a square
            // that split, because a square emitted as one Point is named without the suffix whether it
            // turns or not.
            if (names.size() > 1)
            {
                int expectedTurning = canTurn ? sides.size() : 0;

                if (turning != expectedTurning)
                {
                    wrong.add(what + ": " + turning + " of its Points are turning copies, and the"
                        + " setting implies " + expectedTurning + " - one per arrival side where a"
                        + " train may turn round, and none anywhere else");
                }

                if (plain > sides.size() || (compulsory && plain != 0))
                {
                    wrong.add(what + ": " + plain + " plain copies for " + sides.size()
                        + " arrival sides.  There is at most one per side, and none at all where"
                        + " turning is compulsory");
                }

                if (!canTurn && plain != sides.size())
                {
                    wrong.add(what + ": " + plain + " plain copies for " + sides.size()
                        + " arrival sides on a square nothing turns at, where every side gets one");
                }
            }
        }

        assertTrue(wrong.isEmpty(),
            wrong.size() + " squares are not emitted as the Points their settings imply: " + wrong
            + ".  A square that gained a Point has been SPLIT and every caller now has to say which"
            + " copy it means; a square that lost one has been MERGED, and a merged square has lost"
            + " the facing - which is the only thing the split is for (behaviour.md section 8)");
    }

    /**
     * The census, and it is the tripwire `two-copies-evaluation.md` asked for.
     *
     * That document's recommendation - don't follow the edges instead, not now, not ruled out - lists
     * "it buys nothing on your railway today" first, and this is the number that claim is made of.
     * The number is not what the document says. Thirty of the fifty-eight squares split, thirteen of
     * the thirty-three stations do, and four stations are emitted as four Points each. **Read the
     * evaluation again before trusting its first reason**; the other two - that this is a change to
     * the thing every feature stands on, and that the facing property does not go away - are
     * independent of it and still hold.
     *
     * Red is not automatically a fault. A square Adam adds, marks may-reverse, or joins to a second
     * approach moves these numbers legitimately: update them here and re-read that document, which is
     * exactly what it asks for. What must not happen is the numbers moving without anybody noticing.
     *
     * MUTATION, run, and it is the one that shows why this is here as well as the rule above:
     * `AutonomySession.reversibleTiles` returning nothing takes the turning copy off every may-turn
     * square. The RULE TEST STAYS GREEN - a square with no turn is correctly emitted as one Point per
     * arrival side, which is exactly what it then sees - while this reports 29 split squares against
     * 30 and an empty four-copy set. A change in how much splitting the railway carries is not a
     * broken rule; it is a different railway, and only a census can see it.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheCopyCensusIsWhatItWasMeasuredAt() throws Exception
    {
        AutonomyBuilder builder = session.builder(null);

        Map<TileKey, List<String>> emitted = namesPerSquare(builder);

        int stations = 0;
        int split = 0;
        int splitStations = 0;

        Set<String> fourCopies = new TreeSet<>();

        for (Map.Entry<TileKey, ReducedPoint> entry : session.getReducer().getPoints().entrySet())
        {
            ReducedPoint point = entry.getValue();

            List<String> names = emitted.get(entry.getKey());

            int copies = names == null ? 0 : names.size();

            if (point.isStation()) stations++;
            if (copies > 1) split++;
            if (copies > 1 && point.isStation()) splitStations++;
            if (copies >= 4) fourCopies.add(point.getName());
        }

        assertEquals(session.getReducer().getPoints().size(), SQUARES,
            "this railway no longer has " + SQUARES + " squares on it, so every count below has moved"
            + " for a reason that is nothing to do with the split.  Re-measure them together");

        assertEquals(stations, STATIONS, "the number of stations has changed");

        assertEquals(split, SPLIT_SQUARES,
            split + " of " + SQUARES + " squares now build to more than one Point, and " + SPLIT_SQUARES
            + " did when this was measured.  docs/reference/two-copies-evaluation.md is costed against"
            + " that number - its first reason for keeping the two copies is \"it buys nothing on your"
            + " railway today\" - so re-read it before updating this line.  If the count went UP the"
            + " complexity is being paid on the railway rather than in the source, which is the day"
            + " that document names for re-costing the change; if it went DOWN, squares have been"
            + " merged and their facing has gone with them");

        assertEquals(splitStations, SPLIT_STATIONS,
            splitStations + " of the " + stations + " stations build to more than one Point, against "
            + SPLIT_STATIONS + " when this was measured.  A station is where the copies cost the most:"
            + " every door that offers a destination, every home, and every length rule has to pick"
            + " one");

        assertEquals(fourCopies, new TreeSet<>(java.util.Arrays.asList(FOUR_COPY_SQUARES)),
            "the squares emitted as four Points are now " + fourCopies + " rather than "
            + java.util.Arrays.asList(FOUR_COPY_SQUARES) + ".  These are the fully split ones - two"
            + " arrival sides and a turn at each - and they are the squares behaviour.md section 5b's"
            + " room census talks about by their copy names");
    }

    /**
     * And the census looked at a wired railway rather than at rubble.
     *
     * This is the control that the published number did not have. A diagram parsed without its
     * accessories reduces to 18 edges instead of 128; almost every square is then left with one
     * arrival side, so the same census returns 1 split square out of 58 - a clean, plausible,
     * completely wrong answer that reads exactly like a real one. It is how
     * *"on this railway the split does not fire"* came to be written down.
     *
     * So the edge count is asserted before the counts above mean anything.
     *
     * MUTATION, run: replacing `LayoutSandbox.wired(model, parser)` in the setup with a bare
     * `parser.parseLayout(new LinkedList&lt;&gt;())` - the recipe every sandbox test used until
     * 2026-09-08 - reduces this railway to 18 edges and fails this, and the census with it, at 1 split
     * square of 58.
     */
    @Test
    public void testTheCensusLookedAtAWiredRailway()
    {
        assertTrue(edgesAtOpen > 50,
            "only " + edgesAtOpen + " edges were derived from this diagram.  A switch with no accessory"
            + " behind it is a tile TileGraph refuses to trace through, so the railway comes apart,"
            + " nearly every square is left with a single arrival side, and a single arrival side is a"
            + " single copy.  The census above is then a census of rubble - and that is not a"
            + " hypothetical: it is where two-copies-evaluation.md's headline came from");
    }

    /**
     * Every Point the builder would emit, grouped by the square it belongs to.
     *
     * Read from `tilesByName`, which is the map the emitted configuration is keyed by - so this counts
     * what the railway is actually built as rather than re-deriving the split rule and agreeing with
     * itself.
     *
     * @param builder the builder to ask
     * @return square to the names it is emitted under, in the builder's own order
     */
    private static Map<TileKey, List<String>> namesPerSquare(AutonomyBuilder builder)
    {
        Map<TileKey, List<String>> out = new LinkedHashMap<>();

        for (Map.Entry<String, TileKey> entry : builder.tilesByName().entrySet())
        {
            List<String> names = out.get(entry.getValue());

            if (names == null)
            {
                names = new ArrayList<>();
                out.put(entry.getValue(), names);
            }

            names.add(entry.getKey());
        }

        for (List<String> names : out.values()) Collections.sort(names);

        return out;
    }

}
