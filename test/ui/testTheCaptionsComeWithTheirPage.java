package ui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.widgets.StationCaption;
import org.traincontrol.marklin.MarklinControlStation;
import support.Answering;
import support.Components;
import support.Display;
import support.Reflect;

/**
 * The station captions of the main track diagram are written in the event that puts their page up, so its first paint
 * shows them, and a pass that changes nothing asks for no repaint; a caption whose train, locomotive name or station
 * changes still follows it (Adam: "just make sure locomotive renames or remote track diagram changes are properly
 * updated").  On the frozen railway, through the window's own doors.
 */
public class testTheCaptionsComeWithTheirPage
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        Display.needed("the captions are labels in the window");

        // The frozen copy, never the operator's own railway (OB-111).
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedWithWindowInDebug();
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        Components.drain(1);
        Components.drain(1);

        session = ui.getAutonomySession();

        assertNotNull(session, "the snapshot holds no autonomy setup, so no station has a caption");

        model.parseAuto(session.buildConfiguration());

        assertNotNull(model.getAutoLayout(), "the snapshot did not build to a layout");

        // The captions are written only with a configuration active.
        if (ui.getActiveDiagramConfiguration() == null)
        {
            Reflect.set(TrainControlUI.class, ui, "activeDiagramConfiguration",
                session.getStore().getActiveConfiguration());
        }

        rebuildThePage();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (model != null && model.hasAutoLayout()) model.getAutoLayout().stopLocomotives();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A page drawn afresh is put up with its captions already written: they were written one event later, so the first
     * paint of every rebuild showed every pill empty and the second filled them in.  The check is posted the moment
     * the page enters the panel, where Swing's paint of it is posted too.
     *
     * MUTATION: post the captions' writing with invokeLater on the event thread too, and this fails.
     */
    @Test(timeOut = 120000)
    public void testARebuiltPageHasItsCaptionsOnItsFirstPaint() throws Exception
    {
        final JPanel inner = (JPanel) windowField("InnerLayoutPanel");

        final int[] seen = {-1, -1};

        final java.awt.event.ContainerListener watch = new java.awt.event.ContainerAdapter()
        {
            @Override
            public void componentAdded(java.awt.event.ContainerEvent e)
            {
                final Component page = e.getChild();

                SwingUtilities.invokeLater(() ->
                {
                    if (seen[0] >= 0) return;

                    List<StationCaption> pills = pillsUnder(page);

                    if (pills.isEmpty()) return;

                    seen[0] = pills.size();
                    seen[1] = 0;

                    for (StationCaption pill : pills)
                    {
                        if (pill.getText() == null || pill.getText().isEmpty()) seen[1]++;
                    }
                });
            }
        };

        SwingUtilities.invokeAndWait(() -> inner.addContainerListener(watch));

        try
        {
            rebuildThePage();
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> inner.removeContainerListener(watch));
        }

        assertTrue(seen[0] > 0, "precondition: the rebuilt page put up no station caption to look at");

        assertEquals(seen[1], 0, seen[1] + " of the " + seen[0] + " station captions were still empty when the rebuilt"
            + " page was first painted, so every rebuild flashes empty pills before the names arrive");
    }

    /**
     * A caption pass that changes nothing asks for no repaint: every pass set the text, two colors, the fill and a
     * border on every caption, and repainted each, five times a second through a run.
     *
     * MUTATION: put back the unconditional `repaint()` in `updateStationLabels`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testACaptionPassThatChangesNothingAsksForNoRepaint() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.updateVisiblePoints());

        Components.drain(2);

        int named = 0;

        for (StationCaption pill : onTheEventThread(() -> pillsUnder(shownPage())))
        {
            if (pill.getText() != null && !pill.getText().isEmpty()) named++;
        }

        assertTrue(named > 0, "precondition: no station caption on the page shown says anything");

        Counting counter = new Counting();

        RepaintManager was = RepaintManager.currentManager(ui);

        try
        {
            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(counter));

            SwingUtilities.invokeAndWait(() -> ui.updateVisiblePoints());

            Components.drain(2);
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(was));
        }

        assertEquals(counter.captions, 0, "a caption pass with nothing changed asked for " + counter.captions
            + " repaints of the " + named + " captions on the page, each redrawn for saying what it already said");
    }

    /**
     * A train moved from one station to another moves its caption: the station it left goes back to the dash, the one
     * it reached names it, and moving it back puts both back.
     *
     * MUTATION: write a caption's text only where it is empty, and this fails.
     */
    @Test(timeOut = 120000)
    public void testATrainMovedToAnotherStationMovesItsCaption() throws Exception
    {
        Layout railway = model.getAutoLayout();

        TileKey from = null;
        Locomotive train = null;

        for (TileKey square : captionedSquaresShown())
        {
            List<Point> standing = ui.getAutonomyOccupantsForTile(square);

            if (standing.size() == 1)
            {
                from = square;
                train = standing.get(0).getCurrentLocomotive();
                break;
            }
        }

        assertNotNull(train, "precondition: no train stands alone at a captioned station on the page shown");

        final String fromPoint = ui.getAutonomyPointForTile(from).getName();
        final String saidFrom = captionText(from);

        TileKey to = null;
        String saidTo = null;

        for (TileKey square : captionedSquaresShown())
        {
            Point empty = ui.getAutonomyPointForTile(square);

            if (empty == null || !ui.getAutonomyOccupantsForTile(square).isEmpty()) continue;

            saidTo = captionText(square);

            // The railway's own move, as the diagram's drag and the menu's Place make it
            if (railway.moveLocomotive(train.getName(), empty.getName(), false, true))
            {
                to = square;
                break;
            }
        }

        assertNotNull(to, "precondition: " + train.getName() + " could be moved to no empty captioned station");

        try
        {
            SwingUtilities.invokeAndWait(() -> ui.updateVisiblePoints());

            Components.drain(2);

            assertEquals(captionText(from), saidTo, "the station " + train.getName() + " left still does not say it is"
                + " empty");

            assertNotEquals(captionText(to), saidTo, "the station " + train.getName() + " moved to still says it is"
                + " empty");

            assertTrue(captionText(to).contains(train.getName().substring(0, 3)), "the station " + train.getName()
                + " moved to says " + captionText(to));
        }
        finally
        {
            railway.moveLocomotive(train.getName(), fromPoint, false, true);

            SwingUtilities.invokeAndWait(() -> ui.updateVisiblePoints());

            Components.drain(2);
        }

        assertEquals(captionText(from), saidFrom, "moved back, " + train.getName() + "'s station does not name it again");
        assertEquals(captionText(to), saidTo, "moved back, the other station does not go back to its dash");
    }

    /**
     * A locomotive renamed in its settings is renamed on the caption of the station it stands at, and renamed back.
     *
     * MUTATION: write a caption's text only where it is empty, and this fails.
     */
    @Test(timeOut = 120000)
    public void testARenamedLocomotiveIsRenamedOnItsCaption() throws Exception
    {
        TileKey at = null;
        Locomotive train = null;

        for (TileKey square : captionedSquaresShown())
        {
            List<Point> standing = ui.getAutonomyOccupantsForTile(square);

            if (standing.size() == 1)
            {
                at = square;
                train = standing.get(0).getCurrentLocomotive();
                break;
            }
        }

        assertNotNull(train, "precondition: no train stands alone at a captioned station on the page shown");

        final String name = train.getName();
        final String said = captionText(at);
        final String renamed = "Zq" + name.substring(2);

        assertTrue(said.contains(name.substring(0, 3)), "precondition: the caption at " + at + " says " + said
            + ", not " + name);

        try
        {
            rename(train, renamed);

            String now = captionText(at);

            assertTrue(now.contains("Zq"), "the caption at " + at + " still says " + now + " after " + name
                + " was renamed " + renamed);
        }
        finally
        {
            rename(train, name);
        }

        assertEquals(captionText(at), said, "renamed back, the caption at " + at + " does not say what it did");
    }

    /**
     * A station renamed in the setup is renamed on its caption, which keeps the train standing at it, once the railway
     * is rebuilt from the setup with new Points under the new name.
     *
     * MUTATION: drop the station name from `LayoutGrid.captionsAreAsBuilt`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testARenamedStationKeepsItsTrainOnItsCaption() throws Exception
    {
        TileKey at = null;

        for (TileKey square : captionedSquaresShown())
        {
            if (ui.getAutonomyOccupantsForTile(square).size() == 1 && session.getStore().getPointName(square) != null)
            {
                at = square;
                break;
            }
        }

        assertNotNull(at, "precondition: no train stands alone at a named, captioned station on the page shown");

        final String said = captionText(at);
        final String name = session.getStore().getPointName(at);
        final Layout before = model.getAutoLayout();

        try
        {
            session.setPointName(at, name + " D119");

            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            settle();

            assertNotSame(model.getAutoLayout(), before, "precondition: the railway was not rebuilt from the setup");

            assertEquals(captionText(at), said, "the caption at " + at + " no longer shows the train standing there"
                + " after the station was renamed " + name + " D119");

            // And its name, which the caption gives on hover
            final TileKey square = at;
            final Component page = shownPage();

            String tip = onTheEventThread(() ->
            {
                for (JLabel label : ui.getLayoutStations(square))
                {
                    if (SwingUtilities.isDescendingFrom(label, (Container) page)) return label.getToolTipText();
                }

                return null;
            });

            assertTrue(String.valueOf(tip).contains("D119"), "the caption at " + at + " still names the station " + tip
                + " after it was renamed " + name + " D119");
        }
        finally
        {
            session.setPointName(at, name);

            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            settle();
        }
    }

    // ---------------------------------------------------------------- the fixture

    /** Records the repaints asked of station captions, before Swing filters them. */
    private static final class Counting extends RepaintManager
    {
        int captions;

        @Override
        public synchronized void addDirtyRegion(JComponent c, int x, int y, int w, int h)
        {
            if (c instanceof StationCaption) captions++;

            super.addDirtyRegion(c, x, y, w, h);
        }
    }

    /** Renames through the Locomotive Settings window's Save door. */
    private static void rename(Locomotive train, String name) throws Exception
    {
        final Object actions = windowField("locomotiveActions");

        final java.lang.reflect.Method write = Reflect.method(actions.getClass(), "writeNameAndAddress",
            Component.class, Locomotive.class, String.class, String.class, Locomotive.decoderType.class);

        try (Answering answering = Answering.start(message -> null))
        {
            final Object[] done = new Object[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    done[0] = write.invoke(actions, ui, train, name, "", train.getDecoderType());
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertEquals(done[0], Boolean.TRUE, "the rename to " + name + " was refused: " + answering.asked());
        }

        Components.drain(2);
    }

    /** Draws the page shown afresh, as Show Addresses or a sync does, and waits for it. */
    private static void rebuildThePage() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.repaintLayout());

        settle();
    }

    /** Lets the grid renderer's job and the grid it posts finish. */
    private static void settle() throws Exception
    {
        java.util.concurrent.ExecutorService renderer =
            (java.util.concurrent.ExecutorService) windowField("LayoutGridRenderer");

        for (int round = 0; round < 3; round++)
        {
            Components.drain(1);

            renderer.submit(() -> { }).get(30, java.util.concurrent.TimeUnit.SECONDS);

            Components.drain(2);
        }
    }

    private static Component shownPage() throws Exception
    {
        JPanel inner = (JPanel) windowField("InnerLayoutPanel");

        return onTheEventThread(() -> inner.getComponentCount() == 0 ? null : inner.getComponent(0));
    }

    private static List<StationCaption> pillsUnder(Component root)
    {
        List<StationCaption> out = new ArrayList<>();

        if (root instanceof StationCaption && ((StationCaption) root).isPill()) out.add((StationCaption) root);

        if (root instanceof Container)
        {
            for (Component child : ((Container) root).getComponents()) out.addAll(pillsUnder(child));
        }

        return out;
    }

    /** The captioned squares of the page shown, whose captions are on it. */
    private static Set<TileKey> captionedSquaresShown() throws Exception
    {
        final Component page = shownPage();

        return onTheEventThread(() ->
        {
            Set<TileKey> out = new LinkedHashSet<>();

            for (TileKey square : session.getGraph().getTiles().keySet())
            {
                for (JLabel label : ui.getLayoutStations(square))
                {
                    if (label instanceof StationCaption && ((StationCaption) label).isPill() && page instanceof Container
                        && SwingUtilities.isDescendingFrom(label, (Container) page)) out.add(square);
                }
            }

            return out;
        });
    }

    /** What the station's captions on the page shown say, together. */
    private static String captionText(TileKey square) throws Exception
    {
        final Component page = shownPage();

        return onTheEventThread(() ->
        {
            StringBuilder said = new StringBuilder();

            for (JLabel label : ui.getLayoutStations(square))
            {
                if (page instanceof Container && SwingUtilities.isDescendingFrom(label, (Container) page))
                {
                    said.append(label.getText()).append(' ');
                }
            }

            return said.toString();
        });
    }

    private static <T> T onTheEventThread(java.util.concurrent.Callable<T> work) throws Exception
    {
        return Components.onTheEventThread(work);
    }

    private static Object windowField(String name) throws Exception
    {
        return Reflect.get(TrainControlUI.class, ui, name);
    }
}
