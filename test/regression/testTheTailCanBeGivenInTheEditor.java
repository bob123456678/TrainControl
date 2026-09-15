package regression;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The farthest sensor a standing train's tail has crossed can be given in the autonomy editor - from the right-click
 * menu, or by clicking the sensor on the diagram.
 *
 * Adam, 2026-09-14: *"the prompt should ask the user to pick from a list and select the farthest sensor the tail of
 * the train recently crossed.  Also, ideally in the autonomy editor, it should allow the user to click to select as
 * well."*
 *
 * **The fixture.**  The frozen `live-snapshot` with every square measured at one unit, so every road back is measured
 * and a long train's tail reaches past junctions, and one of its standing trains made long enough that the question
 * is put.  Each claim puts the setup back as it found it (OB-111: never `cs2_sample_layout`).
 *
 * **Asked through the real doors.**  The menu is the one `AutonomyEditorPanel.buildFacingMenu` serves both surfaces,
 * its items are clicked, and the diagram click is `tileClicked` - what the grid calls.
 *
 * @author Adam
 */
public class testTheTailCanBeGivenInTheEditor
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;

    /** Long enough that on a railway measured at one unit a square the tail reaches past has two roads back. */
    private static final int LONG = 12;

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
     * The menu lists every sensor the tail can have crossed, Not Known, and a way to pick on the diagram.
     */
    @Test
    public void testTheMenuListsTheSensorsTheTailCanHaveCrossed() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            List<String> texts = texts(f.menu());

            int heading = texts.indexOf(I18n.t("autosetup.ui.headingTailCrossed"));

            assertTrue(heading >= 0, "the menu of a train whose tail can have crossed sensors on two roads back does not"
                + " ask how far back it reaches. Items: " + texts);

            for (TailCrossedPrompt.Choice choice : f.choices())
            {
                assertTrue(texts.subList(heading, texts.size()).contains(choice.getLabel()),
                    "the menu does not offer " + choice.getLabel() + ", a sensor the tail can have crossed. Items: " + texts);
            }

            assertTrue(texts.subList(heading, texts.size()).contains(I18n.t("autosetup.ui.tailCrossedNotKnown")),
                "the menu does not offer Not Known. Items: " + texts);

            assertTrue(texts.subList(heading, texts.size()).contains(I18n.t("autosetup.ui.menuPickTailCrossed")),
                "the editor's menu does not offer picking the sensor on the diagram. Items: " + texts);
        }
        finally
        {
            f.close();
        }
    }

    /**
     * Choosing a sensor from the menu records the road from it - in the setup, and on the running railway after the
     * rebuild the choice asks for.
     */
    @Test
    public void testChoosingASensorFromTheMenuRecordsItsRoad() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            TailCrossedPrompt.Choice farthest = f.choices().get(f.choices().size() - 1);

            JMenuItem item = find(f.menu(), farthest.getLabel());

            assertNotNull(item, "precondition: no menu item for " + farthest.getLabel());

            SwingUtilities.invokeAndWait(item::doClick);

            settle();

            f.assertRecorded(farthest, "chosen from the menu");
        }
        finally
        {
            f.close();
        }
    }

    /**
     * Picking on the diagram: the sensors are outlined, and a click on one records its road.
     */
    @Test
    public void testClickingTheSensorOnTheDiagramRecordsItsRoad() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            f.arm();

            TailCrossedPrompt.Choice target = null;
            TileKey square = null;

            for (TailCrossedPrompt.Choice choice : f.choices())
            {
                TileKey at = session.getStationIndex().squareOf(choice.getFarthest());

                if (at == null || !f.panel.tailPickSquares().contains(at)) continue;

                int onThatSquare = 0;

                for (TailCrossedPrompt.Choice other : f.choices())
                {
                    if (at.equals(session.getStationIndex().squareOf(other.getFarthest()))) onThatSquare++;
                }

                if (onThatSquare == 1)
                {
                    target = choice;
                    square = at;
                }
            }

            assertNotNull(square, "precondition: no outlined sensor is reached by exactly one road, so a click would ask"
                + " again. Outlined: " + f.panel.tailPickSquares());

            final TileKey clicked = square;

            SwingUtilities.invokeAndWait(() -> f.panel.tileClicked(clicked, null, false));

            settle();

            assertNull(f.panel.tailPickFor(), "the click on an outlined sensor did not end the pick");

            f.assertRecorded(target, "clicked on the diagram");
        }
        finally
        {
            f.close();
        }
    }

    /**
     * A click anywhere else says so and stays armed; Escape lets go.
     */
    @Test
    public void testAClickElsewhereKeepsThePickArmed() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            f.arm();

            String before = session.getArrivedAlong(f.tile);

            assertFalse(f.panel.tailPickSquares().contains(f.tile), "precondition: the train's own square is outlined");

            SwingUtilities.invokeAndWait(() -> f.panel.tileClicked(f.tile, null, false));

            settle();

            assertEquals(f.panel.tailPickFor(), f.tile,
                "a click on a square the tail cannot have crossed dropped the pick, so a near miss costs the whole menu");

            assertEquals(session.getArrivedAlong(f.tile), before, "a click on a square the tail cannot have crossed recorded a road");

            final java.lang.reflect.Method cancel = AutonomyEditorPanel.class.getDeclaredMethod("cancelPendingGesture");

            cancel.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    cancel.invoke(f.panel);
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
            });

            assertNull(f.panel.tailPickFor(), "Escape did not let go of the tail pick");

            assertTrue(f.panel.tailPickSquares().isEmpty(), "Escape left sensors outlined");
        }
        finally
        {
            f.close();
        }
    }

    // ---------------------------------------------------------------- the fixture

    /** One claim's railway: the setup as found, measured, a long train found, an editor open on its page. */
    private static final class Fixture
    {
        private final org.json.JSONObject asFound;
        private final LayoutEditor editor;
        private final AutonomyEditorPanel panel;
        private final TileKey tile;
        private final Locomotive train;
        private final Integer lengthWas;

        private Fixture(org.json.JSONObject asFound, LayoutEditor editor, TileKey tile, Locomotive train, Integer lengthWas)
        {
            this.asFound = asFound;
            this.editor = editor;
            this.panel = editor.getAutonomyPanel();
            this.tile = tile;
            this.train = train;
            this.lengthWas = lengthWas;
        }

        static Fixture open() throws Exception
        {
            final org.json.JSONObject asFound = session.snapshotSetup();

            for (TileKey square : new ArrayList<>(session.getGraph().getTiles().keySet()))
            {
                session.setTileLength(square, 1);
            }

            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            settle();

            Layout running = model.getAutoLayout();

            TileKey found = null;
            Locomotive train = null;
            Integer lengthWas = null;

            for (Point point : running.getPoints())
            {
                Locomotive loc = point.getCurrentLocomotive();

                if (loc == null || point.getArrivedFrom() == null) continue;

                TileKey at = session.getStationIndex().squareOf(point);

                if (at == null || model.getLayout(at.getPage()) == null) continue;

                if (!TailCrossedPrompt.wouldAsk(running, point, point.getArrivedFrom(), LONG)) continue;

                found = at;
                train = loc;
                lengthWas = loc.getTrainLength();

                break;
            }

            if (found == null)
            {
                session.restoreSetup(asFound);

                throw new SkipException("no standing train on the snapshot, measured at one unit, has a tail of " + LONG
                    + " units that can have crossed sensors on two roads back");
            }

            train.setTrainLength(LONG);

            final TileKey at = found;
            final LayoutDiagram page = model.getLayout(at.getPage());
            final LayoutEditor[] built = new LayoutEditor[1];

            SwingUtilities.invokeAndWait(() ->
            {
                built[0] = new LayoutEditor(page, 30, ui, 0);
                built[0].render();
                built[0].setAutonomyMode(session);
            });

            settle();

            return new Fixture(asFound, built[0], at, train, lengthWas);
        }

        /** The running Point the train stands on now - rebuilds replace it. */
        Point standing()
        {
            for (Point point : model.getAutoLayout().getPoints())
            {
                if (point.getCurrentLocomotive() == train) return point;
            }

            return null;
        }

        List<TailCrossedPrompt.Choice> choices()
        {
            Point point = standing();

            return TailCrossedPrompt.choicesFor(model.getAutoLayout(), point, point.getArrivedFrom(), LONG,
                session::baseNameOf);
        }

        javax.swing.JMenu menu() throws Exception
        {
            final javax.swing.JMenu[] menu = new javax.swing.JMenu[1];

            SwingUtilities.invokeAndWait(() -> menu[0] = panel.buildFacingMenu(tile));

            assertNotNull(menu[0], "precondition: no facing menu for the train at " + tile);

            return menu[0];
        }

        void arm() throws Exception
        {
            JMenuItem pick = find(menu(), I18n.t("autosetup.ui.menuPickTailCrossed"));

            assertNotNull(pick, "precondition: the editor's menu does not offer picking the sensor on the diagram");

            SwingUtilities.invokeAndWait(pick::doClick);

            settle();

            assertEquals(panel.tailPickFor(), tile, "picking on the diagram did not wait for a click about " + tile);

            assertFalse(panel.tailPickSquares().isEmpty(), "picking on the diagram outlined no sensor");
        }

        void assertRecorded(TailCrossedPrompt.Choice chosen, String how) throws Exception
        {
            assertEquals(session.getArrivedAlong(tile), Layout.namesOfRoad(chosen.getRoad()),
                chosen.getLabel() + " was " + how + ", and the setup does not hold the road from it");

            Point point = standing();

            assertNotNull(point, "the train is not standing anywhere after the rebuild");

            TailCrossedPrompt.Choice onTheRailway = TailCrossedPrompt.recordedChoice(choices(), point.getArrivedAlong());

            assertNotNull(onTheRailway, chosen.getLabel() + " was " + how + ", and after the rebuild the train on the"
                + " running railway has no road matching any choice: " + Layout.namesOfRoad(point.getArrivedAlong()));

            assertEquals(onTheRailway.getFarthest().getName(), chosen.getFarthest().getName(),
                chosen.getLabel() + " was " + how + ", and the running railway follows a different road");
        }

        void close() throws Exception
        {
            try
            {
                SwingUtilities.invokeAndWait(() -> editor.dispose());

                settle();
            }
            finally
            {
                train.setTrainLength(lengthWas);

                session.restoreSetup(asFound);
            }
        }
    }

    private static List<String> texts(javax.swing.JMenu menu)
    {
        List<String> out = new ArrayList<>();

        for (int i = 0; i < menu.getItemCount(); i++)
        {
            JMenuItem item = menu.getItem(i);

            if (item != null && item.getText() != null) out.add(item.getText());
        }

        return out;
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
