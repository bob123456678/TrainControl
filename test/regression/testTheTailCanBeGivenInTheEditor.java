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

    /** Long enough that on a railway measured at one unit the tail crosses a sensor past a junction. */
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

            assertTrue(heading >= 0, "the menu of a train whose tail can have crossed a sensor past a junction does not"
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

            // ESCAPE ITSELF (TLR-C2): `putToolsDown` is what the key runs; the right-click path is `cancelPendingGesture`.
            SwingUtilities.invokeAndWait(() -> f.panel.putToolsDown());

            assertNull(f.panel.tailPickFor(), "Escape did not let go of the tail pick");

            assertTrue(f.panel.tailPickSquares().isEmpty(), "Escape left sensors outlined");
        }
        finally
        {
            f.close();
        }
    }

    /**
     * Pasting a long train asks for the farthest sensor its tail crossed, and keeps the answer (the paste door).
     *
     * The real Control+V gesture over the square (`TrainControlUI.locomotiveGestureOnDiagram`), with the questions
     * answered through their test doors.  The answer is the farthest sensor offered, so the road recorded is one no
     * other answer - and no answer at all - would give.
     */
    @Test
    public void testPastingALongTrainAsksAndKeepsTheAnswer() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            Point before = f.standing();

            assertNotNull(before, "precondition: the train is not standing anywhere");

            String side = before.getArrivedFrom();

            // THE HEADING IT HAD, so a may-reverse square's facing question is answered rather than shown: a dialog
            // nobody can see would hold the run until it timed out.
            final org.traincontrol.automationui.TilePorts.Side heading =
                session.facingOf(f.train.getName(), model.getAutoLayout());

            List<TailCrossedPrompt.Choice> offered = f.choices();

            TailCrossedPrompt.Choice farthest = offered.get(offered.size() - 1);

            // OFF THE RAILWAY AND ON THE CLIPBOARD, which is what Control+X leaves behind.
            SwingUtilities.invokeAndWait(() -> model.getAutoLayout().moveLocomotive(null, before.getName(), true));

            settle();

            org.traincontrol.gui.ArrivalSidePrompt.answerForTests(side);
            org.traincontrol.gui.FacingPrompt.answerForTests(heading);
            TailCrossedPrompt.answerForTests(farthest.getFarthest().getName());

            final java.lang.reflect.Field cut = TrainControlUI.class.getDeclaredField("cutLocomotive");
            cut.setAccessible(true);

            final java.lang.reflect.Method gesture = TrainControlUI.class.getDeclaredMethod(
                "locomotiveGestureOnDiagram", int.class, boolean.class);
            gesture.setAccessible(true);

            final Object[] handled = new Object[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    cut.set(ui, f.train);
                    ui.setHoveredDiagramTile(f.tile.getPage(), f.tile.getX(), f.tile.getY());
                    handled[0] = gesture.invoke(ui, java.awt.event.KeyEvent.VK_V, true);
                }
                catch (Exception refused)
                {
                    handled[0] = refused;
                }
            });

            settle();

            assertEquals(handled[0], Boolean.TRUE, "the diagram's paste door did not take Control+V: " + handled[0]);

            Point after = f.standing();

            assertNotNull(after, "precondition: the paste put the train nowhere");

            assertEquals(after.getArrivedFrom(), side, "precondition: the paste recorded a different side, so the"
                + " sensors offered are not the ones this claim answered from");

            assertEquals(session.getArrivedAlong(f.tile), Layout.namesOfRoad(farthest.getRoad()),
                "a " + LONG + "-unit train pasted where its tail can have crossed a sensor past a junction was not asked"
                + " for the farthest one, or the answer " + farthest.getLabel() + " was not kept in the setup");

            TailCrossedPrompt.Choice onTheRailway = TailCrossedPrompt.recordedChoice(f.choices(), after.getArrivedAlong());

            assertNotNull(onTheRailway, "the answer reached the setup and not the train on the running railway");

            assertEquals(onTheRailway.getFarthest().getName(), farthest.getFarthest().getName(),
                "the train on the running railway follows a different road from the one answered");
        }
        finally
        {
            TailCrossedPrompt.answerForTests(null);
            org.traincontrol.gui.ArrivalSidePrompt.answerForTests(null);
            org.traincontrol.gui.FacingPrompt.answerForTests(null);
            f.close();
        }
    }

    /**
     * Answering Not Known forgets a road the train held before (TLR-C5, TLV-C2).
     *
     * Pasted back onto the square it stands on, the train is not a change of occupant, so nothing else clears the road.
     * The question is put and answered Not Known, and the road goes from both stores.
     */
    @Test
    public void testNotKnownOnAPasteForgetsAnOldRoad() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            TailCrossedPrompt.Choice farthest = f.recordARoadByPasting();

            f.pasteInPlace(TailCrossedPrompt.NOT_KNOWN);

            assertNull(session.getArrivedAlong(f.tile), "Not Known was answered and the setup still holds the road "
                + farthest.getLabel() + " - the next rebuild follows it");

            assertNull(f.standing().getArrivedAlong(), "Not Known was answered and the train on the running railway still"
                + " follows the road " + farthest.getLabel());
        }
        finally
        {
            f.close();
        }
    }

    /**
     * A paste that puts no question keeps the road the train has (TLV-A1).
     *
     * The doors wrote "no road" whenever nothing was chosen, and nothing is chosen where nothing is asked - so pasting a
     * train back where it stands, or pressing OK in its locomotive dialog, erased the road autonomy drove it in on, and
     * the tracks behind it opened again.
     */
    @Test
    public void testAPasteThatAsksNothingKeepsTheRoad() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            TailCrossedPrompt.Choice farthest = f.recordARoadByPasting();

            f.train.setTrainLength(1);

            assertFalse(TailCrossedPrompt.wouldAsk(model.getAutoLayout(), f.standing(), f.standing().getArrivedFrom(), 1),
                "precondition: a one-unit train is still asked");

            f.pasteInPlace(TailCrossedPrompt.NOT_KNOWN);

            assertEquals(session.getArrivedAlong(f.tile), Layout.namesOfRoad(farthest.getRoad()),
                "the train was pasted back where it stands, nothing was asked, and the setup lost its road");

            assertEquals(Layout.namesOfRoad(f.standing().getArrivedAlong()), Layout.namesOfRoad(farthest.getRoad()),
                "the train was pasted back where it stands, nothing was asked, and the train on the running railway lost"
                + " its road or was given another (TLW-C3)");
        }
        finally
        {
            f.close();
        }
    }

    /**
     * Closing the question without an answer keeps the road the train has (TLV-A1).
     */
    @Test
    public void testDismissingTheQuestionKeepsTheRoad() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            TailCrossedPrompt.Choice farthest = f.recordARoadByPasting();

            assertTrue(TailCrossedPrompt.wouldAsk(model.getAutoLayout(), f.standing(), f.standing().getArrivedFrom(), LONG),
                "precondition: the question is not put, so this claim would be about a paste that asks nothing (TLW-C3)");

            f.pasteInPlace(TailCrossedPrompt.DISMISSED);

            assertEquals(session.getArrivedAlong(f.tile), Layout.namesOfRoad(farthest.getRoad()),
                "the question was closed without an answer, and the setup lost the road the train had");

            assertEquals(Layout.namesOfRoad(f.standing().getArrivedAlong()), Layout.namesOfRoad(farthest.getRoad()),
                "the question was closed without an answer, and the train on the running railway lost its road");
        }
        finally
        {
            f.close();
        }
    }

    /**
     * A paste right after a run keeps the road the run left, though the setup has not caught up (TLW-A1).
     *
     * A run writes its arrival on the running railway only; the setup hears of it at the next capture.  The doors
     * decided keep-or-replace by reading the SETUP, which here says nothing about the side or the road - so a paste
     * of the train back where it stands, with nothing asked, wrote "no road" over the one it drove in on.
     */
    @Test
    public void testAPasteAfterARunKeepsTheRoadTheRunLeft() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            TailCrossedPrompt.Choice farthest = f.choices().get(f.choices().size() - 1);

            // WHAT A RUN LEAVES: the side and road on the running Point, and a setup that has not been told.
            Point standing = f.standing();

            standing.setArrivedAlong(farthest.getRoad());

            session.setArrivedAlong(f.tile, null);
            session.setArrivedFrom(f.tile, null);

            f.train.setTrainLength(1);

            assertFalse(TailCrossedPrompt.wouldAsk(model.getAutoLayout(), standing, standing.getArrivedFrom(), 1),
                "precondition: a one-unit train is still asked");

            f.pasteInPlace(TailCrossedPrompt.DISMISSED);

            assertEquals(Layout.namesOfRoad(f.standing().getArrivedAlong()), Layout.namesOfRoad(farthest.getRoad()),
                "the train was pasted back where it stands after a run, nothing was asked, and the running railway lost the"
                + " road the run left - the door read the setup, which had not been told of the run");

            assertEquals(session.getArrivedAlong(f.tile), Layout.namesOfRoad(farthest.getRoad()),
                "the train was pasted back where it stands after a run, nothing was asked, and the setup was not brought"
                + " into line with the road the run left");
        }
        finally
        {
            f.close();
        }
    }

    /**
     * The arrival-side menu is not offered for a square no train is standing on (OP3-C7).
     *
     * **The offer and the write disagreed about which store they were reading.**  The item appears when
     * the SETUP says a locomotive is at the square (`session.getLocomotiveNameAt`), and it writes to a
     * Point on the running layout chosen by `pointOnTheLayout` - which prefers an occupied copy but
     * falls back to ANY copy.  So with a train assigned in the setup and not placed on the running
     * railway, the side went onto an empty Point: nothing reads it there, and since AUR-C1 nothing
     * saves it either, so the two stores quietly stopped agreeing.
     *
     * The rule now matches the method's own javadoc - *"a tail belongs to a train, and a square with
     * none has nothing to say"*.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheArrivalSideIsNotOfferedForAnEmptySquare() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            final javax.swing.JMenu[] offered = new javax.swing.JMenu[1];

            SwingUtilities.invokeAndWait(() -> offered[0] = f.panel.buildArrivedFromMenu(f.tile));

            assertNotNull(offered[0],
                "precondition: the arrival-side menu is not offered even with a train standing there,"
                + " so this test cannot tell the two cases apart");

            // THE RUNNING RAILWAY LETS GO OF THE TRAIN, and the setup keeps it - which is the state an
            // operator is in between assigning a locomotive and placing one.
            Layout running = model.getAutoLayout();

            for (String name : session.getStationIndex().pointNamesAt(f.tile))
            {
                Point copy = running.getPoint(name);

                if (copy != null) copy.setLocomotive(null);
            }

            assertNotNull(session.getLocomotiveNameAt(f.tile),
                "precondition: the SETUP stopped naming a locomotive here too, so the two stores agree"
                + " and the case this is about no longer exists");

            SwingUtilities.invokeAndWait(() -> offered[0] = f.panel.buildArrivedFromMenu(f.tile));

            assertNull(offered[0],
                "the arrival-side menu was offered for a square with no train standing on it.  What it"
                + " writes goes onto an unoccupied Point, where nothing reads it and nothing saves it"
                + " (OP3-C7)");
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
        final Locomotive train;
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
                    + " units that can have crossed a sensor past a junction");
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

        /**
         * Takes the train off and pastes it back on its square through Control+V, the tail question answered.
         *
         * @param answer the farthest sensor's point name, or `TailCrossedPrompt.NOT_KNOWN`
         */
        void paste(String answer) throws Exception
        {
            final Point before = standing();

            assertNotNull(before, "precondition: the train is not standing anywhere");

            final String side = before.getArrivedFrom();
            final org.traincontrol.automationui.TilePorts.Side heading =
                session.facingOf(train.getName(), model.getAutoLayout());

            SwingUtilities.invokeAndWait(() -> model.getAutoLayout().moveLocomotive(null, before.getName(), true));

            settle();

            org.traincontrol.gui.ArrivalSidePrompt.answerForTests(side);
            org.traincontrol.gui.FacingPrompt.answerForTests(heading);
            TailCrossedPrompt.answerForTests(answer);

            try
            {
                final java.lang.reflect.Field cut = TrainControlUI.class.getDeclaredField("cutLocomotive");
                cut.setAccessible(true);

                final java.lang.reflect.Method gesture = TrainControlUI.class.getDeclaredMethod(
                    "locomotiveGestureOnDiagram", int.class, boolean.class);
                gesture.setAccessible(true);

                final Object[] handled = new Object[1];

                SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        cut.set(ui, train);
                        ui.setHoveredDiagramTile(tile.getPage(), tile.getX(), tile.getY());
                        handled[0] = gesture.invoke(ui, java.awt.event.KeyEvent.VK_V, true);
                    }
                    catch (Exception refused)
                    {
                        handled[0] = refused;
                    }
                });

                settle();

                assertEquals(handled[0], Boolean.TRUE, "the diagram's paste door did not take Control+V: " + handled[0]);
                assertNotNull(standing(), "precondition: the paste put the train nowhere");
            }
            finally
            {
                TailCrossedPrompt.answerForTests(null);
                org.traincontrol.gui.ArrivalSidePrompt.answerForTests(null);
                org.traincontrol.gui.FacingPrompt.answerForTests(null);
            }
        }

        /**
         * Pastes the train back onto the square it stands on - no change of occupant - the tail question answered.
         *
         * @param answer the farthest sensor's point name, `TailCrossedPrompt.NOT_KNOWN` or `TailCrossedPrompt.DISMISSED`
         */
        void pasteInPlace(String answer) throws Exception
        {
            final Point before = standing();

            assertNotNull(before, "precondition: the train is not standing anywhere");

            final String side = before.getArrivedFrom();
            final org.traincontrol.automationui.TilePorts.Side heading =
                session.facingOf(train.getName(), model.getAutoLayout());

            org.traincontrol.gui.ArrivalSidePrompt.answerForTests(side);
            org.traincontrol.gui.FacingPrompt.answerForTests(heading);
            TailCrossedPrompt.answerForTests(answer);

            try
            {
                final java.lang.reflect.Field cut = TrainControlUI.class.getDeclaredField("cutLocomotive");
                cut.setAccessible(true);

                final java.lang.reflect.Method gesture = TrainControlUI.class.getDeclaredMethod(
                    "locomotiveGestureOnDiagram", int.class, boolean.class);
                gesture.setAccessible(true);

                final Object[] handled = new Object[1];

                SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        cut.set(ui, train);
                        ui.setHoveredDiagramTile(tile.getPage(), tile.getX(), tile.getY());
                        handled[0] = gesture.invoke(ui, java.awt.event.KeyEvent.VK_V, true);
                    }
                    catch (Exception refused)
                    {
                        handled[0] = refused;
                    }
                });

                settle();

                assertEquals(handled[0], Boolean.TRUE, "the diagram's paste door did not take Control+V: " + handled[0]);
                assertNotNull(standing(), "precondition: the paste put the train nowhere");
            }
            finally
            {
                TailCrossedPrompt.answerForTests(null);
                org.traincontrol.gui.ArrivalSidePrompt.answerForTests(null);
                org.traincontrol.gui.FacingPrompt.answerForTests(null);
            }
        }

        /**
         * Records the farthest sensor's road by pasting the train back in place and answering with it.
         *
         * @return the choice recorded
         */
        TailCrossedPrompt.Choice recordARoadByPasting() throws Exception
        {
            TailCrossedPrompt.Choice farthest = choices().get(choices().size() - 1);

            pasteInPlace(farthest.getFarthest().getName());

            assertEquals(session.getArrivedAlong(tile), Layout.namesOfRoad(farthest.getRoad()),
                "precondition: pasting the train back in place and answering " + farthest.getLabel() + " did not record its road");

            return farthest;
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
