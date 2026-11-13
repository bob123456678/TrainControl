package regression;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
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
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.Components;
import support.Display;
import support.Reflect;
import support.Teardown;

/**
 * The farthest sensor a standing train's tail has crossed can be given in the autonomy editor, from the right-click
 * menu or by clicking the sensor on the diagram.  Each claim puts the setup back as it found it (OB-111).  Asked
 * through the real doors: the items `AutonomyEditorPanel.addFacingItems` adds on both surfaces, and the diagram click
 * is `tileClicked`.
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
        Display.needed("the editor and its menus need a display");

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));
        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new AssertionError("no autonomy configuration on the snapshot");
        }

        if (ui.getActiveDiagramConfiguration() == null)
        {
            final String active = session.getStore().getActiveConfiguration();

            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

            Components.drain(8);
        }

        assertNotNull(ui.getActiveDiagramConfiguration(), "precondition: no configuration is loaded");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        Teardown.close(sandbox, model, ui);
    }

    /** The menu lists every sensor the tail can have crossed, Not Known, and a way to pick on the diagram. */
    @Test
    public void testTheMenuListsTheSensorsTheTailCanHaveCrossed() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            // menu() fails where the train's menu does not ask how far back its tail reaches
            List<String> texts = texts(f.menu());

            for (TailCrossedPrompt.Choice choice : f.choices())
            {
                assertTrue(texts.contains(choice.getLabel()),
                    "the menu does not offer " + choice.getLabel() + ", a sensor the tail can have crossed. Items: " + texts);
            }

            assertTrue(texts.contains(I18n.t("autosetup.ui.tailCrossedNotKnown")),
                "the menu does not offer Not Known. Items: " + texts);

            assertTrue(texts.contains(I18n.t("autosetup.ui.menuPickTailCrossed")),
                "the editor's menu does not offer picking the sensor on the diagram. Items: " + texts);
        }
        finally
        {
            f.close();
        }
    }

    /**
     * How far back the tail reaches is a submenu on the station's own menu, in the editor and on the track diagram, only
     * where there is a choice: the same train made too short to reach past the junction is not asked (FR-181).
     *
     * MUTATION: let `tailFacts` answer where `TailCrossedPrompt.wouldAsk` says no, and this fails.
     */
    @Test
    public void testTheTailIsAskedOnlyWhereThereIsAChoice() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            final String title = I18n.f("autosetup.ui.menuTailCrossedGroup", f.train.getName());

            assertTrue(offers(editorMenu(f), title), "the editor's menu at " + f.tile + " does not ask how far back the"
                + " tail of " + f.train.getName() + " reaches: no " + title);

            f.train.setTrainLength(1);

            Point point = f.standing();

            assertFalse(TailCrossedPrompt.wouldAsk(model.getAutoLayout(), point, point.getArrivedFrom(), 1),
                "precondition: a train of one unit at " + f.tile + " still has two roads back");

            assertFalse(offers(editorMenu(f), title), "the editor's menu asks how far back the tail of a train reaches"
                + " where there is only one answer");

            f.closeTheEditorForTheDiagram();

            assertFalse(offers(support.StationDoors.diagramMenu(ui, f.tile), title), "the track diagram's menu asks how"
                + " far back the tail of a train reaches where there is only one answer");

            f.train.setTrainLength(LONG);

            assertTrue(offers(support.StationDoors.diagramMenu(ui, f.tile), title), "the track diagram's menu at "
                + f.tile + " does not ask how far back the tail reaches: no " + title);
        }
        finally
        {
            f.close();
        }
    }

    /**
     * The train window's tail row, where the tail has a choice, ends in a Pick button in the application's button font,
     * Segoe UI Bold 12, as the Locomotive Settings button above it.
     *
     * MUTATION: give `train.tailPick` a Plain 13 font again in `TrainTab.addTailChoices`, and this fails.
     */
    @Test
    public void testTheTrainWindowsPickButtonWearsTheButtonFont() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            org.traincontrol.gui.autonomyeditor.StationSettingsWindow window = support.StationDoors.openTrain(f.panel, f.tile);

            javax.swing.AbstractButton pick = support.StationDoors.part(window, "train.tailPick",
                javax.swing.AbstractButton.class);

            assertEquals(pick.getFont(), org.traincontrol.gui.widgets.UiStyle.BUTTON_FONT, "Pick is not Bold 12");

            support.StationDoors.close(window);
        }
        finally
        {
            f.close();
        }
    }

    /**
     * On Station Settings' Train tab each answer of Tail lies stands on a line of its own, one under the other (Adam: "put
     * each radio button for 'tail lies' on its own line, so that wide station names don't further widen the window").
     *
     * MUTATION: lay `train.tail` out as a row again (`FlowLayout`) in `TrainTab`, and this fails.
     */
    @Test
    public void testEachTailAnswerStandsOnItsOwnLine() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            java.awt.Window window = support.StationDoors.openTrain(f.panel, f.tile);

            List<java.awt.Point> at = Components.onTheEventThread(() ->
            {
                List<java.awt.Point> out = new ArrayList<>();

                for (javax.swing.JRadioButton each : Components.all(Components.named(window, "train.tail",
                    java.awt.Container.class), javax.swing.JRadioButton.class, null)) out.add(each.getLocation());

                return out;
            });

            assertTrue(at.size() >= 3, "precondition: Tail lies has not two sensors and Not known: " + at);

            for (int i = 1; i < at.size(); i++)
            {
                assertEquals(at.get(i).x, at.get(0).x, "the answers of Tail lies do not start at one left edge: " + at);
                assertTrue(at.get(i).y > at.get(i - 1).y, "an answer of Tail lies is not on a line below the last: " + at);
            }

            support.StationDoors.close(window);
        }
        finally
        {
            f.close();
        }
    }

    /**
     * A station named at great length, among Tail lies' answers, leaves Station Settings as wide as it opened before
     * (FR-195): the Train tab takes the room the window has and asks for none.
     *
     * MUTATION: have `TrainTab` extend `JPanel`, which asks for its width, rather than `RoomyPanel`, and this fails.
     */
    @Test
    public void testALongStationNameDoesNotWidenTheWindow() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            org.traincontrol.gui.autonomyeditor.StationSettingsWindow window = support.StationDoors.openTrain(f.panel, f.tile);

            int before = Components.onTheEventThread(window::getWidth);

            support.StationDoors.close(window);

            TileKey named = session.getStationIndex().squareOf(f.choices().get(0).getFarthest());

            assertNotNull(named, "precondition: the first answer of Tail lies has no square to name");

            // Longer than the window is wide, so a tab that asked for its width would have to widen it
            String longName = "Upper Ballymacarrett Junction Carriage Sidings and Goods Yard (Northbound Loop), beside"
                + " the Lower Ballymacarrett Junction Carriage Sidings and Goods Yard (Southbound Loop)";

            window = support.StationDoors.open(f.panel, named);

            support.StationDoors.typeAndEnter(window, "station.name", longName);

            support.StationDoors.close(window);

            window = support.StationDoors.openTrain(f.panel, f.tile);

            final java.awt.Window shown = window;

            javax.swing.JRadioButton carrying = Components.onTheEventThread(() -> Components.find(Components.named(shown,
                "train.tail", java.awt.Container.class), javax.swing.JRadioButton.class, r -> r.getText().contains(longName)));

            assertNotNull(carrying, "precondition: no answer of Tail lies carries the long name");

            assertTrue(Components.onTheEventThread(() -> carrying.getPreferredSize().width) > before,
                "precondition: the long name is not wider than the window");

            assertEquals((int) Components.onTheEventThread(window::getWidth), before, "a long station name widened the window");

            support.StationDoors.close(window);
        }
        finally
        {
            f.close();
        }
    }

    /** Choosing a sensor from the menu records the road from it, in the setup and on the running railway. */
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

            Components.drain(8);

            f.assertRecorded(farthest, "chosen from the menu");
        }
        finally
        {
            f.close();
        }
    }

    /** Picking on the diagram: the sensors are outlined, and a click on one records its road. */
    @Test
    public void testClickingTheSensorOnTheDiagramRecordsItsRoad() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            f.arm();

            TailCrossedPrompt.Choice target = f.clickOnTheOnlyRoad();

            assertNull(f.panel.tailPickFor(), "the click on an outlined sensor did not end the pick");

            f.assertRecorded(target, "clicked on the diagram");
        }
        finally
        {
            f.close();
        }
    }

    /** A click anywhere else says so and stays armed; Escape lets go. */
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

            Components.drain(8);

            assertEquals(f.panel.tailPickFor(), f.tile,
                "a click on a square the tail cannot have crossed dropped the pick, so a near miss costs the whole menu");

            assertEquals(session.getArrivedAlong(f.tile), before, "a click on a square the tail cannot have crossed recorded a road");

            // ESCAPE ITSELF: `putToolsDown` is what the key runs; the right-click path is `cancelPendingGesture`.
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
     * Closing Station Settings ends the tail pick waiting about its square, by Done Editing or the close box, however
     * the pick began (its Train tab's Pick on the Diagram, or the square's menu): nothing waits for a click and no
     * sensor stays outlined (MT-959).
     *
     * MUTATION: drop the `endTailPick` call in `StationWindows.closed`, and this fails.
     */
    @Test
    public void testClosingStationSettingsEndsTheTailPick() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            for (String route : new String[] {"tab, Done Editing", "tab, close box", "menu, Done Editing"})
            {
                final org.traincontrol.gui.autonomyeditor.StationSettingsWindow window =
                    support.StationDoors.openTrain(f.panel, f.tile);

                if (route.startsWith("menu")) f.arm();
                else pressPick(f, window);

                List<TileKey> outlined = new ArrayList<>(f.panel.tailPickSquares());

                if (route.endsWith("close box"))
                {
                    SwingUtilities.invokeAndWait(() -> window.dispatchEvent(
                        new java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING)));
                }
                else support.StationDoors.close(window);

                Components.drain(8);

                assertFalse(Components.onTheEventThread(window::isShowing), route + ": precondition: the window stayed open");
                assertNull(f.panel.tailPickFor(), route + ": the pick still waits for a click after the window closed");
                assertEquals(stillOutlined(f, outlined), new ArrayList<TileKey>(), route + ": sensors stay outlined");
            }
        }
        finally
        {
            f.close();
        }
    }

    /**
     * Pick on the Diagram stays pressed in while the pick waits; pressing it again ends the pick and takes the outlines
     * down, and the window stays open (MT-959).
     *
     * MUTATION: have the button's press arm the pick again without asking `endTailPick` first, and this fails.
     */
    @Test
    public void testPickOnTheDiagramPressedAgainEndsThePick() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            org.traincontrol.gui.autonomyeditor.StationSettingsWindow window = support.StationDoors.openTrain(f.panel, f.tile);

            pressPick(f, window);

            assertTrue(support.StationDoors.on(window, "train.tailPick"), "Pick on the Diagram is not pressed in while it waits");

            List<TileKey> outlined = new ArrayList<>(f.panel.tailPickSquares());

            pressPick(f, window);

            assertNull(f.panel.tailPickFor(), "pressing Pick on the Diagram again did not end the pick");
            assertEquals(stillOutlined(f, outlined), new ArrayList<TileKey>(), "pressing it again left sensors outlined");
            assertFalse(support.StationDoors.on(window, "train.tailPick"), "the button stayed pressed in after the pick ended");
            assertTrue(Components.onTheEventThread(window::isShowing), "pressing it again closed the window");

            support.StationDoors.close(window);
        }
        finally
        {
            f.close();
        }
    }

    /**
     * Escape ends the tail pick: in Station Settings, which stays open until a second Escape, and in the editor
     * (`putToolsDown`, what its Escape runs), after which the window's button is up again (MT-959).
     *
     * MUTATION: bind Escape in `StationWindowBase` to `closeWindow` alone again, and this fails.
     */
    @Test
    public void testEscapeEndsThePickInEitherWindow() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            org.traincontrol.gui.autonomyeditor.StationSettingsWindow window = support.StationDoors.openTrain(f.panel, f.tile);

            pressPick(f, window);

            List<TileKey> outlined = new ArrayList<>(f.panel.tailPickSquares());

            support.Keys.press(window.getRootPane(), java.awt.event.KeyEvent.VK_ESCAPE);
            Components.drain(8);

            assertNull(f.panel.tailPickFor(), "Escape in Station Settings did not end the pick");
            assertEquals(stillOutlined(f, outlined), new ArrayList<TileKey>(), "Escape there left sensors outlined");
            assertTrue(Components.onTheEventThread(window::isShowing), "Escape closed Station Settings with a pick to end");

            pressPick(f, window);

            SwingUtilities.invokeAndWait(() -> f.panel.putToolsDown());
            Components.drain(8);

            assertNull(f.panel.tailPickFor(), "Escape in the editor did not end the pick");
            assertFalse(support.StationDoors.on(window, "train.tailPick"), "the button stayed pressed in after Escape in the editor");

            support.Keys.press(window.getRootPane(), java.awt.event.KeyEvent.VK_ESCAPE);
            Components.drain(8);

            assertFalse(Components.onTheEventThread(window::isShowing), "with no pick waiting, Escape did not close the window");
        }
        finally
        {
            f.close();
        }
    }

    /**
     * A road picked on the diagram from the Train tab stays when Station Settings closes afterward: ending the pick puts
     * nothing back (MT-959).
     *
     * MUTATION: have `StationWindows.closed` record Not Known for its square (`recordTailRoad(square, null)`), and this
     * fails.
     */
    @Test
    public void testClosingKeepsTheRoadAlreadyPicked() throws Exception
    {
        Fixture f = Fixture.open();

        try
        {
            org.traincontrol.gui.autonomyeditor.StationSettingsWindow window = support.StationDoors.openTrain(f.panel, f.tile);

            pressPick(f, window);

            TailCrossedPrompt.Choice target = f.clickOnTheOnlyRoad();

            support.StationDoors.close(window);
            Components.drain(8);

            f.assertRecorded(target, "clicked on the diagram before Station Settings closed");
        }
        finally
        {
            f.close();
        }
    }

    /** Presses the Train tab's Pick on the Diagram, as a click does, and waits for the window to read it back. */
    private static void pressPick(Fixture f, java.awt.Window window) throws Exception
    {
        support.StationDoors.press(window, "train.tailPick");
        Components.drain(8);

        if (f.panel.tailPickFor() != null) assertFalse(f.panel.tailPickSquares().isEmpty(), "the pick outlined no sensor");
    }

    /** Which of these squares the editor still draws outlined. */
    private static List<TileKey> stillOutlined(Fixture f, List<TileKey> squares)
    {
        return Components.onTheEventThread(() ->
        {
            List<TileKey> out = new ArrayList<>();

            for (TileKey each : squares) if (f.panel.isOutlined(each)) out.add(each);

            return out;
        });
    }

    /**
     * Pasting a long train asks for the farthest sensor its tail crossed, and keeps the answer (the paste door).
     *
     * The real Control+V gesture, the questions answered through their test doors.  The answer is the farthest sensor
     * offered, so the road recorded is one no other answer would give.
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

            f.closeTheEditorForTheDiagram();

            // OFF THE RAILWAY AND ON THE CLIPBOARD, which is what Control+X leaves behind.
            SwingUtilities.invokeAndWait(() -> model.getAutoLayout().moveLocomotive(null, before.getName(), true));

            Components.drain(8);

            org.traincontrol.gui.autonomy.ArrivalSidePrompt.answerForTests(side);
            org.traincontrol.gui.autonomy.FacingPrompt.answerForTests(heading);
            TailCrossedPrompt.answerForTests(farthest.getFarthest().getName());

            final java.lang.reflect.Field cut = Reflect.field(TrainControlUI.class, "cutLocomotive");

            final java.lang.reflect.Method gesture =
                Reflect.method(TrainControlUI.class, "locomotiveGestureOnDiagram", int.class, boolean.class);

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

            Components.drain(8);

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
            org.traincontrol.gui.autonomy.ArrivalSidePrompt.answerForTests(null);
            org.traincontrol.gui.autonomy.FacingPrompt.answerForTests(null);
            f.close();
        }
    }

    /**
     * A paste that puts no question keeps the road the train has: the doors wrote "no road" whenever nothing was
     * chosen, so pasting a train back where it stands, or OK in its locomotive dialog, erased the road it drove in on.
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

            f.pasteInPlace(TailCrossedPrompt.DISMISSED);

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

    /** Closing the question without an answer keeps the road the train has. */
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
     * A paste right after a run keeps the road the run left, though the setup has not caught up: a run writes its
     * arrival on the running railway only, and the doors decided keep-or-replace by reading the SETUP.
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
     * The arrival-side menu is not offered for a square no train is standing on: the item appears when the SETUP says a
     * locomotive is there, but it writes to a Point chosen by `pointOnTheLayout`, which falls back to ANY copy, so the
     * side went onto an empty Point nothing reads or saves.  Matches the method's javadoc: "a tail belongs to a train,
     * and a square with none has nothing to say".
     */
    @Test
    public void testTheArrivalSideIsNotOfferedForAnEmptySquare() throws Exception
    {
        // ANY STANDING TRAIN WILL DO: this claim is about a square with nobody on it, and riding the junction fixture
        // would skip (which reads as green) on a snapshot without one.
        Fixture f = Fixture.openOnAnyStandingTrain();

        try
        {
            final javax.swing.JMenu[] offered = new javax.swing.JMenu[1];

            SwingUtilities.invokeAndWait(() -> offered[0] = f.panel.buildArrivedFromMenu(f.tile));

            assertNotNull(offered[0],
                "precondition: the arrival-side menu is not offered even with a train standing there,"
                + " so this test cannot tell the two cases apart");

            // THE RUNNING RAILWAY LETS GO OF THE TRAIN and the setup keeps it: between assigning a locomotive and
            // placing one.
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

        /**
         * The same fixture for a claim that needs only a square with a train standing on it; `open()` also needs a
         * junction.
         */
        static Fixture openOnAnyStandingTrain() throws Exception
        {
            return open(false);
        }

        static Fixture open() throws Exception
        {
            return open(true);
        }

        static Fixture open(boolean needsAJunction) throws Exception
        {
            final org.json.JSONObject asFound = session.snapshotSetup();

            for (TileKey square : new ArrayList<>(session.getGraph().getTiles().keySet()))
            {
                session.setTileLength(square, 1);
            }

            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            Components.drain(8);

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

                if (needsAJunction
                    && !TailCrossedPrompt.wouldAsk(running, point, point.getArrivedFrom(), LONG)) continue;

                found = at;
                train = loc;
                lengthWas = loc.getTrainLength();

                break;
            }

            if (found == null)
            {
                session.restoreSetup(asFound);

                throw new AssertionError(needsAJunction
                    ? "no standing train on the snapshot, measured at one unit, has a tail of " + LONG
                        + " units that can have crossed a sensor past a junction"
                    : "no train is standing anywhere on the snapshot at all");
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

            Components.drain(8);

            return new Fixture(asFound, built[0], at, train, lengthWas);
        }

        /** Takes the train off and pastes it back on its square through Control+V, the tail question answered. */
        void paste(String answer) throws Exception
        {
            closeTheEditorForTheDiagram();

            final Point before = standing();

            assertNotNull(before, "precondition: the train is not standing anywhere");

            final String side = before.getArrivedFrom();
            final org.traincontrol.automationui.TilePorts.Side heading =
                session.facingOf(train.getName(), model.getAutoLayout());

            SwingUtilities.invokeAndWait(() -> model.getAutoLayout().moveLocomotive(null, before.getName(), true));

            Components.drain(8);

            org.traincontrol.gui.autonomy.ArrivalSidePrompt.answerForTests(side);
            org.traincontrol.gui.autonomy.FacingPrompt.answerForTests(heading);
            TailCrossedPrompt.answerForTests(answer);

            try
            {
                final java.lang.reflect.Field cut = Reflect.field(TrainControlUI.class, "cutLocomotive");

                final java.lang.reflect.Method gesture =
                    Reflect.method(TrainControlUI.class, "locomotiveGestureOnDiagram", int.class, boolean.class);

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

                Components.drain(8);

                assertEquals(handled[0], Boolean.TRUE, "the diagram's paste door did not take Control+V: " + handled[0]);
                assertNotNull(standing(), "precondition: the paste put the train nowhere");
            }
            finally
            {
                TailCrossedPrompt.answerForTests(null);
                org.traincontrol.gui.autonomy.ArrivalSidePrompt.answerForTests(null);
                org.traincontrol.gui.autonomy.FacingPrompt.answerForTests(null);
            }
        }

        /** Pastes the train back onto the square it stands on, the tail question answered. */
        void pasteInPlace(String answer) throws Exception
        {
            closeTheEditorForTheDiagram();

            final Point before = standing();

            assertNotNull(before, "precondition: the train is not standing anywhere");

            final String side = before.getArrivedFrom();
            final org.traincontrol.automationui.TilePorts.Side heading =
                session.facingOf(train.getName(), model.getAutoLayout());

            org.traincontrol.gui.autonomy.ArrivalSidePrompt.answerForTests(side);
            org.traincontrol.gui.autonomy.FacingPrompt.answerForTests(heading);
            TailCrossedPrompt.answerForTests(answer);

            try
            {
                final java.lang.reflect.Field cut = Reflect.field(TrainControlUI.class, "cutLocomotive");

                final java.lang.reflect.Method gesture =
                    Reflect.method(TrainControlUI.class, "locomotiveGestureOnDiagram", int.class, boolean.class);

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

                Components.drain(8);

                assertEquals(handled[0], Boolean.TRUE, "the diagram's paste door did not take Control+V: " + handled[0]);
                assertNotNull(standing(), "precondition: the paste put the train nowhere");
            }
            finally
            {
                TailCrossedPrompt.answerForTests(null);
                org.traincontrol.gui.autonomy.ArrivalSidePrompt.answerForTests(null);
                org.traincontrol.gui.autonomy.FacingPrompt.answerForTests(null);
            }
        }

        /** Records the farthest sensor's road by pasting the train back in place and answering with it. */
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

        /** The "...'s Tail Reaches Back To..." submenu of the train's items (FR-181); fails where there is none. */
        javax.swing.JMenu menu() throws Exception
        {
            final javax.swing.JPopupMenu items = new javax.swing.JPopupMenu();

            SwingUtilities.invokeAndWait(() -> panel.addFacingItems(items, tile));

            final String title = I18n.f("autosetup.ui.menuTailCrossedGroup", train.getName());

            javax.swing.JMenu tail = support.Components.find(items, javax.swing.JMenu.class, m -> title.equals(m.getText()));

            assertNotNull(tail, "the menu of a train whose tail can have crossed a sensor past a junction does not ask how"
                + " far back it reaches: no " + title + " at " + tile);

            return tail;
        }

        void arm() throws Exception
        {
            JMenuItem pick = find(menu(), I18n.t("autosetup.ui.menuPickTailCrossed"));

            assertNotNull(pick, "precondition: the editor's menu does not offer picking the sensor on the diagram");

            SwingUtilities.invokeAndWait(pick::doClick);

            Components.drain(8);

            assertEquals(panel.tailPickFor(), tile, "picking on the diagram did not wait for a click about " + tile);

            assertFalse(panel.tailPickSquares().isEmpty(), "picking on the diagram outlined no sensor");
        }

        /** With a pick armed, clicks the outlined sensor reached by one road alone, so no list asks; its choice. */
        TailCrossedPrompt.Choice clickOnTheOnlyRoad() throws Exception
        {
            TailCrossedPrompt.Choice target = null;
            TileKey square = null;

            for (TailCrossedPrompt.Choice choice : choices())
            {
                TileKey at = session.getStationIndex().squareOf(choice.getFarthest());

                if (at == null || !panel.tailPickSquares().contains(at)) continue;

                int onThatSquare = 0;

                for (TailCrossedPrompt.Choice other : choices())
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
                + " again. Outlined: " + panel.tailPickSquares());

            final TileKey clicked = square;

            SwingUtilities.invokeAndWait(() -> panel.tileClicked(clicked, null, false));

            Components.drain(8);

            return target;
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

        /** Closes the editor before the diagram's paste door, which refuses while an editor is open. */
        void closeTheEditorForTheDiagram() throws Exception
        {
            SwingUtilities.invokeAndWait(() -> editor.dispose());

            Components.drain(8);
        }

        void close() throws Exception
        {
            try
            {
                SwingUtilities.invokeAndWait(() -> editor.dispose());

                Components.drain(8);
            }
            finally
            {
                train.setTrainLength(lengthWas);

                session.restoreSetup(asFound);
            }
        }
    }

    private static javax.swing.JPopupMenu editorMenu(Fixture f)
    {
        return support.Components.onTheEventThread(() -> f.panel.buildTileMenu(f.tile, null));
    }

    /** Whether the menu itself, not a submenu of it, has an item saying this. */
    private static boolean offers(javax.swing.JPopupMenu menu, String text)
    {
        for (java.awt.Component part : menu.getComponents())
        {
            if (part instanceof JMenuItem && text.equals(((JMenuItem) part).getText())) return true;
        }

        return false;
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
}
