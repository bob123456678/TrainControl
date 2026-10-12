package ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The covered-track shading on the SCREEN follows the train, and comes off when it leaves (OB-180).
 *
 * Adam: **"when a train is manually moved to a new station in the track diagram viewer using control+X
 * and V, its former shaded icons are not reset."**
 *
 * **This is the first test in this suite that asserts about drawn pixels rather than about the model,
 * and OB-180 is the argument for it.** The covered set was computed correctly the whole time. What was
 * broken is that nothing repainted the tiles whose state had changed: a tile decides its wash when it is
 * DRAWN, and tiles are only drawn when their own accessory, feedback or route changes - none of which a
 * train moving is. Every assertion the suite could make - on `edgesCoveredByStandingTrains`, on
 * `tilesCoveredByStandingTrains`, on the model, on the source - answered identically with the defect
 * present and with it fixed.
 *
 * **The grid is held open across the whole test, and that is the point.** `DiagramExport.render` builds a
 * fresh grid per call, and a fresh label always works its wash out correctly - so a test written on it
 * would have passed with the defect in place. `support.Rendered` keeps one grid and paints it again, so
 * what is compared is what an operator looking at an open window would have seen.
 *
 * **The round trip is Adam's** (2026-09-08): put the train somewhere, take it away again, and the
 * picture must come back to what it was. Three snapshots of one grid:
 *
 * | | |
 * |---|---|
 * | BARE | nothing standing anywhere |
 * | COVERED | the train placed, and a square its tail lies across carries the orange mark (MT-309; it was "is darker than it was" while the mark was a grey wash) |
 * | AFTER | the train moved away, and that square is EXACTLY the bare image again |
 *
 * MUTATION: removing the `repaintTheWashWhereItChanged` call from
 * `TrainControlUI.refreshCoveredTrack` leaves the AFTER image dark - the train has gone and the track
 * behind where it stood is still greyed - which is the defect, seen.
 *
 * @author Adam
 */
public class testTheShadingIsRedrawnWhenATrainMoves
{
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation model;

    private static AutonomySession session;

    private static TrainControlUI ui;

    private static final File OUT =
        new File(System.getProperty("java.io.tmpdir"), "traincontrol-shading");

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("looking at what is drawn needs a display");
        }

        // BEFORE THE MODEL (OB-111): init reads the layout preference too, so opening the sandbox
        // afterwards would leave the model on Adam's real railway.
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and `test/layouts/live-snapshot`).
        //
        // This opened `cs2_sample_layout`, whose placements and lengths move as Adam runs trains on it
        // - so on 2026-09-09 this class reported "no standing train covers any track here" against a
        // working tree where every train had been driven away, and a class that skips everything reads
        // as a green one.  `live-snapshot` is the same railway with the clock stopped at `e6f4649c`,
        // which is the state these assertions were written against.
        //
        // `Scenario.folderFor` rather than a path: it is the only naming of a fixture folder that
        // cannot spell its way out to the operator's own railway, and `LayoutSandbox` still copies
        // what it names before anything reads it.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        javax.swing.SwingUtilities.invokeAndWait(new Runnable()
        {
            @Override
            public void run()
            {
                ui = new TrainControlUI();
            }
        });

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        OUT.mkdirs();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (ui != null)
        {
            javax.swing.SwingUtilities.invokeAndWait(new Runnable()
            {
                @Override
                public void run()
                {
                    ui.dispose();
                }
            });
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * The wash goes on where a train stands and comes off where it no longer does.
     *
     * @throws Exception on a failure to build or to paint
     */
    @Test
    public void testTheWashGoesOnAndComesOffTheSameSquare() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Layout built = model.getAutoLayout();

        assertNotNull(built, "the configuration did not build, so there is no railway to stand on");

        // A MEASURED RAILWAY.  The wash only appears behind a train whose tail is longer than the
        // track it is standing on, and that needs lengths - which most squares do not carry.
        measureEverything(10);

        model.parseAuto(session.buildConfiguration());

        built = model.getAutoLayout();

        Locomotive train = model.getLocByName(model.getLocList().get(0));

        assertNotNull(train, "no locomotive on this railway");

        Integer wasLength = train.getTrainLength();

        train.setTrainLength(60);

        // THE PAGE THE WASH IS ACTUALLY ON.  Insisting on page one made this fail at its own
        // precondition - "no placement on this page left any square covered" - which is the right
        // failure for a test that would otherwise pass vacuously, and the wrong page to be looking at.
        TileKey somewhereCovered = placeSoSomethingIsCovered(built, null, train);

        assertNotNull(somewhereCovered,
            "no placement anywhere on this railway left a square covered, so there is no wash to look"
            + " at and this test would pass whatever the drawing did");

        LayoutDiagram page = model.getLayout(somewhereCovered.getPage());

        assertNotNull(page, "the covered square is on a page that is not drawn: " + somewhereCovered);

        support.Rendered view = support.Rendered.open(page, ui);

        try
        {
            // 1. BARE - nothing standing anywhere.
            clearEveryTrain(built);

            ui.updateVisiblePoints();

            BufferedImage bare = view.snapshot();

            // 2. COVERED - the train placed somewhere that actually covers track on THIS page.
            TileKey covered = placeSoSomethingIsCovered(built, page, train);

            assertNotNull(covered,
                "the search found a covered square on " + page.getName() + " a moment ago and cannot"
                + " now, so this is not stable enough to assert on");

            BufferedImage withTrain = view.snapshot();

            BufferedImage bareTile = support.Rendered.tile(bare, covered.getX(), covered.getY());
            BufferedImage darkTile = support.Rendered.tile(withTrain, covered.getX(), covered.getY());

            assertNotNull(bareTile, "the covered square " + covered + " is outside the drawn page");

            double bareBrightness = support.Rendered.brightness(bareTile);

            support.Rendered.save(bare, new File(OUT, "bare.png"));
            support.Rendered.save(withTrain, new File(OUT, "covered.png"));

            // ORANGE, NOT DARKER (MT-309).
            //
            // This asked whether the square had got darker, which was the right question while the
            // mark was a grey wash over the whole tile.  Adam has replaced that with a line along the
            // road the train is on - "instead of shading the entire tiles, we need to draw a line
            // (let's say in orange)" - and a line over a few pixels of a mostly white square does not
            // move its mean brightness by a whole point.  What it does is put orange on it.
            assertTrue(support.Rendered.hasTheTrainMark(darkTile),
                "the square at " + covered + " is reported as covered and is drawn without the train"
                + " mark on it (mean brightness " + support.Rendered.brightness(darkTile)
                + " against a bare " + bareBrightness + "). The mark is not reaching the screen at"
                + " all, so the assertion below - that it comes off again - could not fail either."
                + " Images in " + OUT);

            // 3. AFTER - the train taken away, and the SAME grid painted again.
            clearEveryTrain(built);

            ui.updateVisiblePoints();

            BufferedImage after = view.snapshot();

            BufferedImage afterTile = support.Rendered.tile(after, covered.getX(), covered.getY());

            support.Rendered.save(after, new File(OUT, "after.png"));

            assertTrue(support.Rendered.same(bareTile, afterTile),
                "the train has gone and the square at " + covered + " is still not drawn the way it was"
                + " before it arrived (brightness " + support.Rendered.brightness(afterTile)
                + " against " + bareBrightness + "). Updating the covered set is not showing it: a tile"
                + " decides its wash when it is drawn, and nothing about a train moving redraws one"
                + " (OB-180). Images in " + OUT);
        }
        finally
        {
            train.setTrainLength(wasLength == null ? 0 : wasLength);

            clearEveryTrain(built);
        }
    }

    /**
     * Puts the train down until some square of this page is covered, and says which.
     *
     * Searched rather than named: which squares carry a tail depends on the lengths, the shape of the
     * run-in and where the reduction put its Points, and a hard-coded square is a guess that goes stale
     * the first time any of those changes.
     *
     * @param built the railway
     * @param page the page being drawn
     * @param train the locomotive
     * @return the covered square, or null when no placement covered anything on this page
     * @throws Exception on a failure to refresh
     */
    private TileKey placeSoSomethingIsCovered(Layout built, LayoutDiagram page, Locomotive train)
        throws Exception
    {
        for (Point station : built.getPoints())
        {
            if (!station.isDestination()) continue;

            clearEveryTrain(built);

            built.moveLocomotive(train.getName(), station.getName(), false);

            // AND WHICH SIDE IT CAME IN BY, which `moveLocomotive` does not set and the tail walk
            // cannot do without.
            //
            // `edgesCoveredByStandingTrains` matches the stored arrival side against the geometry on
            // its first hop and stops when nothing matches - so a train simply MOVED onto a square
            // covers nothing at all, and an earlier version of this test searched the whole railway
            // and correctly reported that there was no wash anywhere to look at.
            //
            // The operator doors do this too: a hand placement asks or assumes the side, and this is
            // the same value by the same rule.
            for (String side : org.traincontrol.gui.ArrivalSidePrompt.sidesOf(built, station))
            {
                station.setArrivedFrom(side);

                ui.updateVisiblePoints();

                for (TileKey square : session.tilesCoveredByStandingTrains(built))
                {
                    if (page == null || page.getName().equals(square.getPage())) return square;
                }
            }

            ui.updateVisiblePoints();

            for (TileKey square : session.tilesCoveredByStandingTrains(built))
            {
                // A null page means "anywhere" - the first pass does not know which page to look at.
                if (page == null || page.getName().equals(square.getPage())) return square;
            }
        }

        return null;
    }

    /**
     * Takes every train off the railway.
     *
     * @param built the railway
     */
    private void clearEveryTrain(Layout built)
    {
        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() != null)
            {
                try
                {
                    built.moveLocomotive(null, point.getName(), true);
                }
                catch (Exception cannotClear)
                {
                    // Another point will still be tried; an empty railway is the goal, not this one.
                }
            }
        }
    }

    /**
     * Gives every square the reduction knows a length, so tails have something to be measured against.
     *
     * @param units the length
     */
    private void measureEverything(int units)
    {
        java.util.Set<TileKey> everyTile = new java.util.LinkedHashSet<>();

        everyTile.addAll(session.getReducer().getPoints().keySet());

        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge
            : session.getReducer().getEdges())
        {
            for (org.traincontrol.automationui.GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) everyTile.add(step.getTile());
            }
        }

        // COLLECTED FIRST, THEN WRITTEN.  setTileLength rebuilds the reducer, so walking its own
        // collections while writing to it re-derives the graph under the iterator.
        for (TileKey tile : everyTile) session.setTileLength(tile, units);
    }
}
