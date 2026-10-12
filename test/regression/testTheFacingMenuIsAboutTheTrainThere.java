package regression;

import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * After a run, the Facing menu is about the train the railway has on the square, and turns that one (TDY4-C5).
 *
 * After a run the setup still names each square's pre-run occupant (behaviour.md 6a), and nothing captures before the
 * diagram's right-click.  The menu took its train from the setup: with P placed on a square in the setup and T standing
 * there on the railway, it was titled "P is facing", ticked T's facing, and a click wrote P's record and moved P - found
 * on no copy of the square, so nothing moved.  T was not turned and the operator's correction was dropped without a
 * word.  With nothing placed there in the setup there was no menu at all.  TDY3-A2's shape one door along: `flipFacing`
 * was keyed on the railway's train by `4be3798a`, and this menu was not.
 *
 * Asked through the real door, as `testTheTailCanBeGivenInTheEditor` asks it: `AutonomyEditorPanel.buildFacingMenu`,
 * which both surfaces serve, and its item clicked.
 *
 * MUTATION: take the menu's train from the setup again, and the title claim fails; move the setup's train on a click,
 * and the turn claim fails.
 *
 * @author Adam
 */
public class testTheFacingMenuIsAboutTheTrainThere
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the editor and its menus need a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));
        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration on the snapshot");
        }

        if (ui.getActiveDiagramConfiguration() == null)
        {
            final String active = session.getStore().getActiveConfiguration();

            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

            settle();
        }

        assertNotNull(ui.getActiveDiagramConfiguration(), "precondition: no configuration is loaded");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The setup has P on a square where two facings can be chosen; the railway has T there instead.  The menu names T,
     * and choosing the other facing stands T on the copy facing that way, leaving P's record alone.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAfterARunTheMenuTurnsTheTrainTheRailwayHasThere() throws Exception
    {
        final org.json.JSONObject asFound = session.snapshotSetup();
        final LayoutEditor[] editor = new LayoutEditor[1];

        try
        {
            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            settle();

            Layout running = model.getAutoLayout();

            // A SQUARE THE SETUP HAS A TRAIN ON, where the railway has it too and two facings can be chosen.
            TileKey square = null;
            Point copy = null;
            String inTheSetup = null;

            for (Point point : running.getPoints())
            {
                Locomotive loc = point.getCurrentLocomotive();

                if (loc == null) continue;

                TileKey at = session.getStationIndex().squareOf(point);

                if (at == null || model.getLayout(at.getPage()) == null) continue;

                if (!loc.getName().equals(session.getLocomotiveNameAt(at))) continue;

                if (session.facingChoices(at).size() < 2) continue;

                square = at;
                copy = point;
                inTheSetup = loc.getName();

                break;
            }

            if (square == null) throw new SkipException("no placed train on the snapshot stands where two facings can be"
                + " chosen");

            // AND A TRAIN ON NO SQUARE, of the railway or the setup.
            String setupText = session.snapshotSetup().toString();
            String onTheRailway = null;

            for (String name : model.getLocList())
            {
                if (running.getLocomotiveLocation(model.getLocByName(name)) != null) continue;

                if (setupText.contains(org.json.JSONObject.quote(name))) continue;

                onTheRailway = name;

                break;
            }

            if (onTheRailway == null) throw new SkipException("every locomotive is placed somewhere");

            // AFTER A RUN: the railway has T where the setup still has P.
            Side recordedWas = session.getFacing(square);

            running.moveLocomotive(null, copy.getName(), false);

            assertTrue(running.moveLocomotive(onTheRailway, copy.getName(), false), "precondition: could not stand "
                + onTheRailway + " on " + copy.getName());

            Side facingWas = session.facingOnTheRailway(square, running);

            Side turnTo = null;

            for (Side side : session.facingChoices(square))
            {
                if (side != facingWas) turnTo = side;
            }

            assertNotNull(turnTo, "precondition: no other facing to choose at " + square);

            final LayoutDiagram page = model.getLayout(square.getPage());

            SwingUtilities.invokeAndWait(() ->
            {
                editor[0] = new LayoutEditor(page, 30, ui, 0);
                editor[0].render();
                editor[0].setAutonomyMode(session);
            });

            settle();

            final AutonomyEditorPanel panel = editor[0].getAutonomyPanel();
            final TileKey target = square;
            final javax.swing.JMenu[] menu = new javax.swing.JMenu[1];

            SwingUtilities.invokeAndWait(() -> menu[0] = panel.buildFacingMenu(target));

            assertNotNull(menu[0], "no Facing menu for " + onTheRailway + ", standing at " + square + " on the railway");

            assertEquals(menu[0].getText(), I18n.f("autosetup.ui.menuFacingGroup", onTheRailway), "the Facing menu at "
                + square + " names the setup's train, " + inTheSetup + ", while the railway has " + onTheRailway
                + " there (TDY4-C5)");

            JMenuItem choice = find(menu[0], I18n.t("autosetup.ui.facing" + turnTo.name()));

            assertNotNull(choice, "precondition: the menu does not offer " + turnTo);

            SwingUtilities.invokeAndWait(choice::doClick);

            settle();

            Layout now = model.getAutoLayout();

            Point where = now.getLocomotiveLocation(model.getLocByName(onTheRailway));

            assertNotNull(where, onTheRailway + " is standing nowhere after the menu turned it");

            assertEquals(session.getStationIndex().facingsAt(square).get(where.getName()), turnTo, "choosing " + turnTo
                + " in the Facing menu did not turn " + onTheRailway + ", the train the railway has at " + square
                + " - it stands on " + where.getName() + " (TDY4-C5)");

            assertEquals(session.getFacing(square), recordedWas, "turning " + onTheRailway + " rewrote the facing the"
                + " setup records for " + inTheSetup + ", a train the railway does not have there");
        }
        finally
        {
            if (editor[0] != null)
            {
                final LayoutEditor closing = editor[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            session.restoreSetup(asFound);

            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            settle();
        }
    }

    /**
     * On its way to a square it may turn at, without turning, a train's label already faces the way it will stand (OB-314).
     *
     * Adam, 2026-10-03: *"when sending et22-245 from topmainr2inter to bottommainb, the label at bottommainb shows arrival
     * arrive facting west, not east, as if it would be reversed. But on arrival, it gets fixed."*  The path to a square
     * trains may turn at routinely ends on its TURNING copy, which faces the other way, and the label reads the facing of
     * the copy the train holds - so until it arrived, and was stood on the plain copy, the label showed it reversed.
     *
     * A train sent onto a turning copy with KEEP DIRECTION answered, the sensors played by hand so nothing arrives before
     * the label is read.
     *
     * MUTATION: let the reading take the copy the path ends on again, and this fails.
     *
     * @throws Exception from the window or the run
     */
    @Test
    public void testOnItsWayTheLabelFacesTheWayItWillStand() throws Exception
    {
        final org.traincontrol.automation.Layout running = model.getAutoLayout();

        assertNotNull(running, "precondition: no running railway");

        // A MAY-TURN SQUARE: a turning copy and a plain copy, reached by one approach, facing different ways
        org.traincontrol.automation.Point turning = null, plain = null, approach = null;

        TileKey square = null;

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (turning != null) break;

            java.util.Map<String, Side> facings = session.getStationIndex().facingsAt(tile);

            for (String t : session.getStationIndex().pointNamesAt(tile))
            {
                org.traincontrol.automation.Point tp = running.getPoint(t);

                if (tp == null || !(tp.isTerminus() || tp.isReversing()) || tp.getCurrentLocomotive() != null) continue;

                for (String p : session.getStationIndex().pointNamesAt(tile))
                {
                    org.traincontrol.automation.Point pp = running.getPoint(p);

                    if (pp == null || pp == tp || pp.isTerminus() || pp.isReversing() || pp.getCurrentLocomotive() != null)
                    {
                        continue;
                    }

                    if (facings.get(t) == null || facings.get(p) == null || facings.get(t) == facings.get(p)) continue;

                    for (org.traincontrol.automation.Point a : running.getPoints())
                    {
                        // A station, so a train can be stood there
                        if (a.getCurrentLocomotive() != null || a.isSamePlaceAs(tp) || !a.isDestination()) continue;

                        if (running.getEdge(a.getName(), t) == null || running.getEdge(a.getName(), p) == null) continue;

                        turning = tp;
                        plain = pp;
                        approach = a;
                        square = tile;
                        break;
                    }

                    if (turning != null) break;
                }

                if (turning != null) break;
            }
        }

        if (turning == null) throw new SkipException("the snapshot has no free may-turn square with an approach to both copies");

        // A TRAIN STANDING NOWHERE
        String train = null;

        for (String name : model.getLocList())
        {
            if (running.getLocomotiveLocation(model.getLocByName(name)) == null) train = name;
        }

        assertNotNull(train, "precondition: every locomotive is placed somewhere");

        final org.traincontrol.base.Locomotive loc = model.getLocByName(train);

        // SIMULATED, so the route's switches confirm without a Central Station; the label is read the moment the train
        // sets off, long before a simulated arrival
        final boolean simulating = running.isSimulate();

        running.setSimulate(true);

        model.setFeedbackState(approach.getS88(), true);
        model.setFeedbackState(turning.getS88(), false);
        model.setFeedbackState(plain.getS88(), false);

        assertTrue(running.moveLocomotive(train, approach.getName(), false), "precondition: could not stand " + train
            + " on " + approach.getName());

        final java.util.List<org.traincontrol.automation.Edge> path = new java.util.ArrayList<>();

        path.add(running.getEdge(approach.getName(), turning.getName()));

        final org.traincontrol.automation.Point askedAbout = turning;

        final org.traincontrol.automation.Point approach_ = approach;

        org.traincontrol.automation.Layout.ReversalPolicy keepDirection = new org.traincontrol.automation.Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(org.traincontrol.base.Locomotive t, org.traincontrol.automation.Point at)
            {
                return false;
            }

            @Override
            public boolean asksAbout(org.traincontrol.automation.Point at)
            {
                return at == askedAbout;
            }
        };

        // WHAT THE RAILWAY SAYS, for a dispatch that does not go
        final java.util.List<String> said = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());

        java.util.logging.Handler listening = new java.util.logging.Handler()
        {
            @Override
            public void publish(java.util.logging.LogRecord record)
            {
                said.add(record.getMessage());
            }

            @Override
            public void flush()
            {
            }

            @Override
            public void close()
            {
            }
        };

        java.util.logging.Logger.getLogger("").addHandler(listening);

        final Boolean[] went = new Boolean[1];

        Thread run = new Thread(() -> went[0] = running.executePath(path, loc, 20, null, keepDirection));

        run.setDaemon(true);

        Side shown = null;

        try
        {
            run.start();

            long until = System.currentTimeMillis() + 15000;

            while (!(running.isRunning() && loc.getSpeed() > 0) && run.isAlive() && System.currentTimeMillis() < until)
            {
                Thread.sleep(5);
            }

            assertTrue(loc.getSpeed() > 0, "precondition: the train never set off - the dispatch returned " + went[0]
                + "; the railway said " + said + "; valid " + running.isValid() + ", current "
                + running.isCurrentLayout() + ", clear " + running.isPathClear(path, loc) + ", at the start "
                + (approach_ == null ? "?" : String.valueOf(loc.equals(approach_.getCurrentLocomotive()))));

            assertTrue(running.getActiveLocomotives().containsKey(loc), "precondition: the train arrived before the label"
                + " could be read");

            // ON ITS WAY: what the label reads
            shown = session.facingOnTheRailway(square, running);
        }
        finally
        {
            model.setFeedbackState(turning.getS88(), true);
            model.setFeedbackState(approach.getS88(), false);

            long until = System.currentTimeMillis() + 30000;

            while (run.isAlive() && System.currentTimeMillis() < until) Thread.sleep(100);

            running.moveLocomotive(null, turning.getName(), false);
            running.moveLocomotive(null, plain.getName(), false);
            running.moveLocomotive(null, approach.getName(), false);

            model.setFeedbackState(turning.getS88(), false);

            running.setSimulate(simulating);

            java.util.logging.Logger.getLogger("").removeHandler(listening);
        }

        java.util.Map<String, Side> facings = session.getStationIndex().facingsAt(square);

        assertEquals(shown, facings.get(plain.getName()), "on its way to " + square + " with KEEP DIRECTION, the label read "
            + shown + " - the turning copy's facing - where the train will stand facing " + facings.get(plain.getName())
            + " (OB-314)");
    }

    /**
     * While its route is set, before it sets off, a train's label already faces the way it will stand (RSA30-C3).
     *
     * OB-314's answer was kept beside the journey, which is written only at the hand-over, after the route's switches are
     * set - and the claim reserves the turning copy long before.  So for the length of the route setting the label read
     * the turning copy and showed the train the wrong way round, then turned as it set off: on the real railway, the
     * Central Station's confirmations as well.  The answer is kept with the claim now, and read through it.
     *
     * The dispatch held at its hand-over, its claim taken and its route set, by holding the journeys' map's monitor; the
     * label read on another thread, with a time limit, so a reading that waits for the hand-over fails rather than hangs.
     *
     * MUTATION: keep the answer only from the hand-over again, and this fails.
     *
     * @throws Exception from the window or the run
     */
    @Test
    public void testWhileItsRouteIsSetTheLabelFacesTheWayItWillStand() throws Exception
    {
        final org.traincontrol.automation.Layout running = model.getAutoLayout();

        assertNotNull(running, "precondition: no running railway");

        // A MAY-TURN SQUARE: a turning copy and a plain copy, reached by one approach, facing different ways
        org.traincontrol.automation.Point turning = null, plain = null, approach = null;

        TileKey square = null;

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (turning != null) break;

            java.util.Map<String, Side> facings = session.getStationIndex().facingsAt(tile);

            for (String t : session.getStationIndex().pointNamesAt(tile))
            {
                org.traincontrol.automation.Point tp = running.getPoint(t);

                if (tp == null || !(tp.isTerminus() || tp.isReversing()) || tp.getCurrentLocomotive() != null) continue;

                for (String p : session.getStationIndex().pointNamesAt(tile))
                {
                    org.traincontrol.automation.Point pp = running.getPoint(p);

                    if (pp == null || pp == tp || pp.isTerminus() || pp.isReversing() || pp.getCurrentLocomotive() != null)
                    {
                        continue;
                    }

                    if (facings.get(t) == null || facings.get(p) == null || facings.get(t) == facings.get(p)) continue;

                    for (org.traincontrol.automation.Point a : running.getPoints())
                    {
                        if (a.getCurrentLocomotive() != null || a.isSamePlaceAs(tp) || !a.isDestination()) continue;

                        if (running.getEdge(a.getName(), t) == null || running.getEdge(a.getName(), p) == null) continue;

                        turning = tp;
                        plain = pp;
                        approach = a;
                        square = tile;
                        break;
                    }

                    if (turning != null) break;
                }

                if (turning != null) break;
            }
        }

        if (turning == null) throw new SkipException("the snapshot has no free may-turn square with an approach to both copies");

        String train = null;

        for (String name : model.getLocList())
        {
            if (running.getLocomotiveLocation(model.getLocByName(name)) == null) train = name;
        }

        assertNotNull(train, "precondition: every locomotive is placed somewhere");

        final org.traincontrol.base.Locomotive loc = model.getLocByName(train);

        final boolean simulating = running.isSimulate();

        running.setSimulate(true);

        model.setFeedbackState(approach.getS88(), true);
        model.setFeedbackState(turning.getS88(), false);
        model.setFeedbackState(plain.getS88(), false);

        assertTrue(running.moveLocomotive(train, approach.getName(), false), "precondition: could not stand " + train
            + " on " + approach.getName());

        final java.util.List<org.traincontrol.automation.Edge> path = new java.util.ArrayList<>();

        path.add(running.getEdge(approach.getName(), turning.getName()));

        final org.traincontrol.automation.Point askedAbout = turning;

        org.traincontrol.automation.Layout.ReversalPolicy keepDirection = new org.traincontrol.automation.Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(org.traincontrol.base.Locomotive t, org.traincontrol.automation.Point at)
            {
                return false;
            }

            @Override
            public boolean asksAbout(org.traincontrol.automation.Point at)
            {
                return at == askedAbout;
            }
        };

        java.lang.reflect.Field journeysField = org.traincontrol.automation.Layout.class.getDeclaredField("activeLocomotives");

        journeysField.setAccessible(true);

        final Object journeys = journeysField.get(running);

        java.lang.reflect.Field takingField = org.traincontrol.automation.Layout.class.getDeclaredField("takingPath");

        takingField.setAccessible(true);

        final java.util.Map<?, ?> taking = (java.util.Map<?, ?>) takingField.get(running);

        final TileKey where = square;

        final Side[] shown = new Side[1];

        Thread run = null;

        try
        {
            synchronized (journeys)
            {
                run = new Thread(() -> running.executePath(path, loc, 20, null, keepDirection));

                run.setDaemon(true);

                run.start();

                long until = System.currentTimeMillis() + 15000;

                while (run.isAlive() && System.currentTimeMillis() < until && !atTheHandOver(run, taking, loc))
                {
                    Thread.sleep(2);
                }

                assertTrue(atTheHandOver(run, taking, loc), "precondition: the dispatch never reached its hand-over with its"
                    + " claim taken - alive " + run.isAlive() + ", state " + run.getState() + ", claim "
                    + taking.containsKey(loc));

                Thread reading = new Thread(() -> shown[0] = session.facingOnTheRailway(where, running));

                reading.setDaemon(true);

                reading.start();

                reading.join(5000);

                assertFalse(reading.isAlive(), "reading the label waited for the hand-over");
            }
        }
        finally
        {
            model.setFeedbackState(turning.getS88(), true);
            model.setFeedbackState(approach.getS88(), false);

            long until = System.currentTimeMillis() + 30000;

            while (run != null && run.isAlive() && System.currentTimeMillis() < until) Thread.sleep(100);

            running.moveLocomotive(null, turning.getName(), false);
            running.moveLocomotive(null, plain.getName(), false);
            running.moveLocomotive(null, approach.getName(), false);

            model.setFeedbackState(turning.getS88(), false);

            running.setSimulate(simulating);
        }

        java.util.Map<String, Side> facings = session.getStationIndex().facingsAt(square);

        assertEquals(shown[0], facings.get(plain.getName()), "while its route to " + square + " was set, with KEEP DIRECTION,"
            + " the label read " + shown[0] + " - the turning copy's facing - where the train will stand facing "
            + facings.get(plain.getName()) + " (RSA30-C3)");
    }

    /** A dispatch waiting at its hand-over: its claim taken, and blocked entering the hand-over's monitor. */
    private static boolean atTheHandOver(Thread run, java.util.Map<?, ?> taking, Object loc)
    {
        if (run.getState() != Thread.State.BLOCKED || !taking.containsKey(loc)) return false;

        StackTraceElement[] stack = run.getStackTrace();

        return stack.length > 0 && "executePathInternal".equals(stack[0].getMethodName());
    }

    private static JMenuItem find(javax.swing.JMenu menu, String text)
    {
        for (int i = 0; i < menu.getItemCount(); i++)
        {
            JMenuItem item = menu.getItem(i);

            if (item != null && text.equals(item.getText())) return item;
        }

        return null;
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 8; pass++)
        {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
