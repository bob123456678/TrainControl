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
 * How many Points each of Adam's squares becomes: the guard the two-copies decision rests on.
 *
 * `docs/reference/two-copies-evaluation.md` (removed; git history, 0a5dab2a) answers his question - *"having two copies
 * seems like unnecessary complexity, I wonder if it can be done more easily by simply following the edges?"* - and its
 * first reason for leaving the copies alone is a measurement of how much complexity the railway carries.  That
 * measurement was published wrong ("every named square builds to exactly one copy") because it was taken on a diagram
 * parsed WITHOUT its accessories: `TileGraph` will not trace through a switch with no `Accessory`, so the reduction
 * falls from 128 edges to 18 and almost every square has one arrival side, hence one copy.  Whether that changes the
 * recommendation is Adam's to decide; this file makes the number true and watched.
 *
 * What it asserts:
 *
 *   - THE RULE.  Every square emits the Points the setup implies: one per arrival side, plus one turning copy per
 *     arrival side where a train may turn round, less the plain copies that would be dead ends, and no plain copy at
 *     all where turning is compulsory; a square with no arrival side is emitted whole.  Checked square by square
 *     against the emitted names.
 *   - THE CENSUS.  The counts, pinned: a tripwire that goes red when the split fires more, and equally when it fires
 *     less.
 *   - THAT THE CENSUS LOOKED AT A RAILWAY.  The edge count is asserted, because 18 edges is indistinguishable from a
 *     real result by reading.
 *
 * Read against a `LayoutSandbox` copy of `cs2_sample_layout` (OB-111). His railway is only ever read.
 *
 * @author Adam
 */
public class testEverySquareBuildsToTheCopiesTheSetupImplies
{
    /**
     * The census, measured on the wired railway of the frozen snapshot.  A change is either an edit Adam made to his
     * own railway (update the numbers and re-read `two-copies-evaluation.md`, whose recommendation is costed against
     * them) or a builder change that has started splitting or merging squares.
     */
    private static final int SQUARES = 58;
    private static final int STATIONS = 33;
    private static final int SPLIT_SQUARES = 25;
    private static final int SPLIT_STATIONS = 9;

    /**
     * The squares that carry the FULL split, two arrival sides and a turn at each; named, because the documentation
     * talks about them by their split copies.
     */
    private static final String[] FOUR_COPY_SQUARES =
    {
        "BottomMainPost", "RampDown"
    };

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;

    private static int edgesAtOpen;

    /** Opens a throwaway copy of the operator's railway, wires it, and reduces it. */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The frozen copy, not the railway he is operating (OB-111): every figure below is a count of what the railway
        // builds to, and it moves as he works on it; `live-snapshot` is the same railway with the clock stopped.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulated();

        String path = "file:///"
            + sandbox.getFolder().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        // WIRED, not merely parsed: `LayoutSandbox.wired` runs the application's `wireComponents` loop, which puts an
        // Accessory behind each switch.  A bare `parseLayout` leaves them null, TileGraph refuses to trace through, the
        // reduction falls from 128 edges to 18 and the census reports one split square.
        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

        edgesAtOpen = session.getReducer().getEdges().size();
    }

    /** Puts the layout preference back. */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * Every square emits the Points its own settings imply, stated from what the setup knows (which sides trains arrive
     * by, whether they may turn round there, whether they must) and checked against what the builder emitted:
     *
     *   - no arrival side at all: emitted whole, one Point, no facing to record;
     *   - turning not allowed: one Point per arrival side, none a turning copy;
     *   - turning compulsory: one Point per arrival side, EVERY one a turning copy (the plain copy is not emitted);
     *   - turning optional: a turning copy per arrival side, plus the plain copies that have somewhere to go other than
     *     back the way they came.
     *
     * Plain and turning copies are told apart by their emitted names, which carry ", reverse", so this reads the
     * configuration rather than re-deriving the split.
     *
     * MUTATIONS: stopping `AutonomyBuilder.nodesFor` emitting the turning copy fails this, with the squares named,
     * those that lost their turning copy emitted as NO Point at all.  Dropping `onwards ||` from the plain-copy guard,
     * or `!must` from it, does NOT: every compulsory turn on this railway is a dead end, so either guard alone is
     * enough.
     */
    @Test
    public void testTheCopiesAreWhatTheSquaresSettingsImply() throws Exception
    {
        AutonomyBuilder builder = session.builder(null);

        Map<TileKey, List<String>> emitted = namesPerSquare(builder);

        Set<TileKey> mayTurn = new LinkedHashSet<>(session.reversibleTiles());
        Set<TileKey> must = new LinkedHashSet<>(session.mandatoryTurnTiles());

        List<String> wrong = new ArrayList<>();

        // Over the squares, not over what was emitted: walking the emitted names cannot see a square that came back as
        // NOTHING, which removing the turning copy from `nodesFor` produces for a compulsory turn.
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

            // And the mix, which is where a merge or a split shows up; only asked of a square that split, since one
            // Point is named without the suffix either way.
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
     * The census, the tripwire `two-copies-evaluation.md` asked for: the number its first reason ("it buys nothing on
     * your railway today") is made of.  Red is not automatically a fault: a square Adam adds, marks may-reverse or
     * joins to a second approach moves these numbers legitimately; update them here and re-read that document.  What
     * must not happen is the numbers moving without anybody noticing.
     *
     * MUTATION: `AutonomySession.reversibleTiles` returning nothing takes the turning copy off every may-turn square.
     * The RULE TEST STAYS GREEN (a square with no turn is correctly emitted as one Point per arrival side) while this
     * reports fewer split squares and an empty four-copy set: a different railway, which only a census can see.
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

        // WHAT A REFRESH OF THE SNAPSHOT CHANGES (FR-136): every figure, so taking one is a change of numbers.
        String refresh = "  If live-snapshot was refreshed on purpose, re-read the evaluation (git show"
            + " 0a5dab2a:docs/reference/two-copies-evaluation.md) and set in this class SQUARES = "
            + session.getReducer().getPoints().size() + ", STATIONS = " + stations + ", SPLIT_SQUARES = " + split
            + ", SPLIT_STATIONS = " + splitStations + " and FOUR_COPY_SQUARES = " + fourCopies;

        assertEquals(session.getReducer().getPoints().size(), SQUARES,
            "this railway no longer has " + SQUARES + " squares on it, so every count below has moved"
            + " for a reason that is nothing to do with the split.  Re-measure them together." + refresh);

        assertEquals(stations, STATIONS, "the number of stations has changed." + refresh);

        assertEquals(split, SPLIT_SQUARES,
            split + " of " + SQUARES + " squares now build to more than one Point, and " + SPLIT_SQUARES
            + " did when this was measured.  docs/reference/two-copies-evaluation.md (git show"
            + " 0a5dab2a:docs/reference/two-copies-evaluation.md) is costed against"
            + " that number - its first reason for keeping the two copies is \"it buys nothing on your"
            + " railway today\" - so re-read it before updating this line.  If the count went UP the"
            + " complexity is being paid on the railway rather than in the source, which is the day"
            + " that document names for re-costing the change; if it went DOWN, squares have been"
            + " merged and their facing has gone with them." + refresh);

        assertEquals(splitStations, SPLIT_STATIONS,
            splitStations + " of the " + stations + " stations build to more than one Point, against "
            + SPLIT_STATIONS + " when this was measured.  A station is where the copies cost the most:"
            + " every door that offers a destination, every home, and every length rule has to pick"
            + " one." + refresh);

        assertEquals(fourCopies, new TreeSet<>(java.util.Arrays.asList(FOUR_COPY_SQUARES)),
            "the squares emitted as four Points are now " + fourCopies + " rather than "
            + java.util.Arrays.asList(FOUR_COPY_SQUARES) + ".  These are the fully split ones - two"
            + " arrival sides and a turn at each - and they are the squares behaviour.md section 5b's"
            + " room census talks about by their copy names." + refresh);
    }

    /**
     * And the census looked at a wired railway rather than at rubble: a diagram parsed without its accessories reduces
     * to 18 edges instead of 128, and the same census then returns 1 split square out of 58, a clean, plausible, wrong
     * answer.  The edge count is asserted before the counts above mean anything.
     *
     * MUTATION: replacing `LayoutSandbox.wired(model, parser)` in the setup with a bare
     * `parser.parseLayout(new LinkedList&lt;&gt;())` reduces this railway to 18 edges and fails this, and the census
     * with it.
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
     * Every Point the builder would emit, grouped by the square it belongs to, read from `tilesByName` so this counts
     * what the railway is built as.
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
