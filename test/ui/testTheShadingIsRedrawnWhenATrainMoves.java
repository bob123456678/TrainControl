package ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 * Adam: "when a train is manually moved to a new station in the track diagram viewer using control+X and V, its former
 * shaded icons are not reset."
 *
 * **This asserts about drawn pixels rather than the model.**  The covered set was computed correctly the whole time;
 * nothing repainted the tiles whose state had changed, since a tile decides its wash when it is DRAWN and is only drawn
 * when its own accessory, feedback or route changes.  Every model assertion answered identically with the defect
 * present and fixed.
 *
 * **The grid is held open across the whole test:** `DiagramExport.render` builds a fresh grid per call, and a fresh
 * label always works its wash out correctly.  `support.Rendered` keeps one grid and paints it again, so what is
 * compared is what an operator looking at an open window would have seen.
 *
 * **But painting the whole grid again is not what an open window does** (FR-136, issue 19): a snapshot paints every
 * tile, so the AFTER state came out right with nothing asking for a redraw.  The AFTER state is also taken the way a
 * window does it: the COVERED picture with only the areas production asked to repaint painted again
 * (`support.RepaintRecorder`), and every square the train marked must be back to the BARE picture there.
 *
 * Three snapshots of one grid:
 *
 * | | |
 * |---|---|
 * | BARE | nothing standing anywhere |
 * | COVERED | the train placed, and a square its tail lies across carries the orange mark (MT-309) |
 * | AFTER | the train moved away, and that square is EXACTLY the bare image again |
 *
 * MUTATION: removing the `repaintTheWashWhereItChanged` call from `CoveredTrackView.workOutCoveredTrack` leaves the
 * AFTER picture orange: the train has gone and the track it lay across still carries its mark, which is the defect,
 * seen.
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

        // BEFORE THE MODEL (OB-111): init reads the layout preference too.  THE FROZEN COPY, NOT THE RAILWAY HE IS
        // OPERATING (`test/layouts/live-snapshot`): `cs2_sample_layout`'s placements and lengths move as Adam runs
        // trains on it, and a class that skips everything reads as green.  `Scenario.folderFor` is the only naming of a
        // fixture folder that cannot spell its way out to the operator's own railway.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();

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

    /** The wash goes on where a train stands and comes off where it no longer does. */
    @Test
    public void testTheWashGoesOnAndComesOffTheSameSquare() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Layout built = model.getAutoLayout();

        assertNotNull(built, "the configuration did not build, so there is no railway to stand on");

        // A MEASURED RAILWAY: the wash only appears behind a train whose tail is longer than the track it stands on.
        measureEverything(10);

        model.parseAuto(session.buildConfiguration());

        built = model.getAutoLayout();

        Locomotive train = model.getLocByName(model.getLocList().get(0));

        assertNotNull(train, "no locomotive on this railway");

        Integer wasLength = train.getTrainLength();

        train.setTrainLength(60);

        // THE PAGE THE WASH IS ACTUALLY ON: insisting on page one failed at the test's own precondition.
        TileKey somewhereCovered = placeSoSomethingIsCovered(built, null, train);

        assertNotNull(somewhereCovered,
            "no placement anywhere on this railway left a square covered, so there is no wash to look"
            + " at and this test would pass whatever the drawing did");

        LayoutDiagram page = model.getLayout(somewhereCovered.getPage());

        assertNotNull(page, "the covered square is on a page that is not drawn: " + somewhereCovered);

        support.Rendered view = support.Rendered.open(page, ui);

        support.RepaintRecorder screen = support.RepaintRecorder.install();

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

            // EVERY SQUARE THE TRAIN MARKS ON THIS PAGE, for the screen's half below: the first one found may be the
            // square the train stands on, which the station's own caption and icon redraw for reasons of their own.
            support.CoveredMarks.settle(ui);

            Set<TileKey> markedHere = new LinkedHashSet<>();

            for (TileKey square : session.tilesCoveredByStandingTrains(built))
            {
                if (page.getName().equals(square.getPage())) markedHere.add(square);
            }

            BufferedImage withTrain = view.snapshot();

            // AND FROM HERE THE SCREEN IS REDRAWN ONLY WHERE IT IS ASKED TO BE (issue 19): the picture just taken is what an
            // open window shows, and `screen` writes down every repaint asked for after it.
            screen.clear();

            BufferedImage bareTile = support.Rendered.tile(bare, covered.getX(), covered.getY());
            BufferedImage darkTile = support.Rendered.tile(withTrain, covered.getX(), covered.getY());

            assertNotNull(bareTile, "the covered square " + covered + " is outside the drawn page");

            double bareBrightness = support.Rendered.brightness(bareTile);

            support.Rendered.save(bare, new File(OUT, "bare.png"));
            support.Rendered.save(withTrain, new File(OUT, "covered.png"));

            // ORANGE, NOT DARKER (MT-309): the mark is a line along the road the train is on (Adam: "instead of shading
            // the entire tiles, we need to draw a line (let's say in orange)"), which does not move a mostly white
            // square's mean brightness by a whole point; what it does is put orange on it.
            assertTrue(support.Rendered.hasTheTrainMark(darkTile),
                "the square at " + covered + " is reported as covered and is drawn without the train"
                + " mark on it (mean brightness " + support.Rendered.brightness(darkTile)
                + " against a bare " + bareBrightness + "). The mark is not reaching the screen at"
                + " all, so the assertion below - that it comes off again - could not fail either."
                + " Images in " + OUT);

            // 3. AFTER - the train taken away, and the SAME grid painted again.
            clearEveryTrain(built);

            ui.updateVisiblePoints();

            support.CoveredMarks.settle(ui);

            view.settle();

            // WHAT THE OPERATOR SEES (issue 19): the picture taken with the train there, redrawn only where the window
            // asked, before the full repaint below, which paints every tile and so answers right whatever was asked.
            BufferedImage onScreen = screen.redrawWhereAsked(withTrain, view.host());

            support.Rendered.save(onScreen, new File(OUT, "on-screen.png"));

            for (TileKey square : markedHere)
            {
                BufferedImage bareOne = support.Rendered.tile(bare, square.getX(), square.getY());
                BufferedImage shownOne = support.Rendered.tile(onScreen, square.getX(), square.getY());

                assertTrue(support.Rendered.same(bareOne, shownOne),
                    "the train has gone and the square at " + square + ", which it marked, is still drawn as it was"
                    + " with the train there (" + (support.Rendered.hasTheTrainMark(shownOne) ? "orange" : "not orange")
                    + ") on a window that redraws what it is asked to: nothing asked for that tile to be redrawn"
                    + " (OB-180). Marked squares " + markedHere + "; images in " + OUT);
            }

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
            screen.close();

            train.setTrainLength(wasLength == null ? 0 : wasLength);

            clearEveryTrain(built);
        }
    }

    /**
     * Puts the train down until some square of this page is covered, and says which (null when none): searched rather
     * than named, as which squares carry a tail depends on the lengths and the reduction.
     */
    private TileKey placeSoSomethingIsCovered(Layout built, LayoutDiagram page, Locomotive train)
        throws Exception
    {
        for (Point station : built.getPoints())
        {
            if (!station.isDestination()) continue;

            clearEveryTrain(built);

            built.moveLocomotive(train.getName(), station.getName(), false);

            // AND WHICH SIDE IT CAME IN BY, which `moveLocomotive` does not set and the tail walk cannot do without:
            // `edgesCoveredByStandingTrains` matches the stored arrival side against the geometry on its first hop, so
            // a train merely MOVED onto a square covers nothing.  The operator doors do this too.
            for (String side : org.traincontrol.gui.autonomy.ArrivalSidePrompt.sidesOf(built, station))
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

    /** Takes every train off the railway. */
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

    /** Gives every square the reduction knows a length, so tails have something to be measured against. */
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

        // COLLECTED FIRST, THEN WRITTEN: setTileLength rebuilds the reducer, which would re-derive the graph under the
        // iterator.
        for (TileKey tile : everyTile) session.setTileLength(tile, units);
    }
}
