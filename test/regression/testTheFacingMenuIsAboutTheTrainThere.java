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
