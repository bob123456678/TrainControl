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
import support.AskingWindows;
import support.Components;
import support.Reflect;

/**
 * The station captions of the main track diagram are written in the event that puts their page up, so its first paint
 * shows them, and a pass that changes nothing asks for no repaint; a caption whose train, locomotive name or station
 * changes still follows it (the owner's rule: renames and remote diagram changes must still update).  And a sensor at
 * rest leaves the strip above the diagram alone.  On the frozen railway, through the
 * window's own doors.
 */
public class testTheCaptionsComeWithTheirPage
{
    private static support.FrozenEditor rig;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The frozen copy in its hidden window, never the operator's own railway (OB-111).
        rig = support.FrozenEditor.open();

        model = rig.model;
        ui = rig.ui;
        session = rig.session;

        rebuildThePage();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (rig != null) rig.close();
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

        final TileKey from = aTrainStandingAlone();
        final Locomotive train = ui.stationCaptions().getAutonomyOccupantsForTile(from).get(0).getCurrentLocomotive();

        final String fromPoint = ui.stationCaptions().getAutonomyPointForTile(from).getName();
        final String saidFrom = captionText(from);

        TileKey to = null;
        String saidTo = null;

        for (TileKey square : captionedSquaresShown())
        {
            Point empty = ui.stationCaptions().getAutonomyPointForTile(square);

            if (empty == null || !ui.stationCaptions().getAutonomyOccupantsForTile(square).isEmpty()) continue;

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

        assertEquals(captionText(from), saidFrom, "moved back, " + train.getName() + "'s station does not name it"
            + " again");
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
        final TileKey at = aTrainStandingAlone();
        final Locomotive train = ui.stationCaptions().getAutonomyOccupantsForTile(at).get(0).getCurrentLocomotive();

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
        final TileKey at = aTrainStandingAlone();

        final String said = captionText(at);
        final String name = session.getStore().getPointName(at);
        final Layout before = model.getAutoLayout();

        try
        {
            session.setPointName(at, name + " Renamed");

            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            settle();

            assertNotSame(model.getAutoLayout(), before, "precondition: the railway was not rebuilt from the setup");

            assertEquals(captionText(at), said, "the caption at " + at + " no longer shows the train standing there"
                + " after the station was renamed " + name + " Renamed");

            // And its name, which the caption gives on hover
            final Component page = shownPage();

            String tip = onTheEventThread(() ->
            {
                for (JLabel label : ui.getLayoutStations(at))
                {
                    if (SwingUtilities.isDescendingFrom(label, (Container) page)) return label.getToolTipText();
                }

                return null;
            });

            assertTrue(String.valueOf(tip).contains("Renamed"), "the caption at " + at + " still names the station "
                + tip + " after it was renamed " + name + " Renamed");
        }
        finally
        {
            session.setPointName(at, name);

            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            settle();
        }
    }

    /**
     * A sensor at rest leaves the autonomy strip above the diagram alone: each one ended in the Auto tab's refresh
     * taking Start's turning mark off when there was none, and the strip laid itself out and repainted for it.
     *
     * MUTATION: take Start's icon off unconditionally again in `turningMark`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testASensorAtRestLeavesTheStripAboveTheDiagramAlone() throws Exception
    {
        final Component page = shownPage();

        final String sensor = onTheEventThread(() ->
        {
            for (org.traincontrol.gui.LayoutLabel tile : Components.all(page, org.traincontrol.gui.LayoutLabel.class,
                each -> each.getComponent() != null && each.getComponent().isFeedback()
                    && each.getComponent().getFeedback() != null))
            {
                return tile.getComponent().getFeedback().getName();
            }

            return null;
        });

        assertNotNull(sensor, "precondition: the page shown has no sensor");

        assertNotNull(Components.onTheEventThread(() ->
            Components.find(ui, org.traincontrol.gui.AutonomyOverlayToggle.class, each -> true)),
            "precondition: the window has no autonomy strip above the diagram");

        Counting counter = new Counting();

        RepaintManager was = RepaintManager.currentManager(ui);

        try
        {
            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(counter));

            for (boolean state : new boolean[] {true, false})
            {
                counter.panels = 0;

                model.setFeedbackState(sensor, state);

                // The Auto tab's refresh has run once its locomotive cards are redrawn
                support.Settle.until(() -> counter.panels > 0, 10000);

                Components.drain(2);

                assertTrue(counter.panels > 0, "precondition: the sensor never reached the Auto tab's refresh");
            }
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(was));
        }

        assertEquals(counter.strip, 0, "the sensor, firing and clearing with nothing running, laid out or repainted the"
            + " autonomy strip above the diagram " + counter.strip + " times");
    }

    // ---------------------------------------------------------------- the fixture

    /** Records the repaints asked of station captions and the strip, and the layouts asked of the strip. */
    private static final class Counting extends RepaintManager
    {
        int captions;
        volatile int strip, panels;

        @Override
        public synchronized void addDirtyRegion(JComponent c, int x, int y, int w, int h)
        {
            if (c instanceof StationCaption) captions++;
            if (c instanceof org.traincontrol.gui.AutonomyOverlayToggle) strip++;

            if (SwingUtilities.getAncestorOfClass(org.traincontrol.gui.autonomy.AutoLocomotiveStatus.class, c) != null)
            {
                panels++;
            }

            super.addDirtyRegion(c, x, y, w, h);
        }

        @Override
        public synchronized void addInvalidComponent(JComponent c)
        {
            if (c instanceof org.traincontrol.gui.AutonomyOverlayToggle) strip++;

            super.addInvalidComponent(c);
        }
    }

    /** A named, captioned station of the page shown with one train standing at it. */
    private static TileKey aTrainStandingAlone() throws Exception
    {
        for (TileKey square : captionedSquaresShown())
        {
            if (ui.stationCaptions().getAutonomyOccupantsForTile(square).size() == 1 && session.getStore().getPointName(square) != null)
            {
                return square;
            }
        }

        throw new AssertionError("precondition: no train stands alone at a named, captioned station on the page shown");
    }

    /** Renames as the Locomotive Settings window's Save does. */
    private static void rename(Locomotive train, String name) throws Exception
    {
        AskingWindows.renameInSettings(ui, train, name);
    }

    /** Draws the page shown afresh, as Show Addresses or a sync does, and waits for it. */
    private static void rebuildThePage() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.repaintLayout());

        settle();
    }

    /** Lets the grid renderer's jobs and the grids they post finish. */
    private static void settle() throws Exception
    {
        support.Settle.theDiagram(ui);
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
                    if (label instanceof StationCaption && ((StationCaption) label).isPill()
                        && page instanceof Container && SwingUtilities.isDescendingFrom(label, (Container) page))
                    {
                        out.add(square);
                    }
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
