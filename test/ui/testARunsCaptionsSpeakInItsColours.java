package ui;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.DiagramColours;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.AutoLocomotiveStatus;
import org.traincontrol.gui.StationCaption;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A running route's captions speak the route's colours, its destination is a warm yellow, and the Auto tab's station
 * badge is the captions' blue (Adam, 2026-10-09: "Build 2,3,5,6" - the diagram look proposals' second, third and
 * sixth), asked of the real window on his frozen railway with a run staged part way along its route.
 *
 * The colours themselves are claimed beside the pill (`ui.testDiagramLooksRight`); this asks the place that chooses
 * them for a running train - `TrainControlUI.updateStationLabels` - because a rule tested apart from its caller says
 * nothing about whether the caller asks it.
 */
public class testARunsCaptionsSpeakInItsColours
{
    private static void settle(long ms) throws Exception
    {
        for (int i = 0; i < 4; i++) SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(ms);
        for (int i = 0; i < 4; i++) SwingUtilities.invokeAndWait(() -> { });
    }

    private static boolean rgb(Color c, int r, int g, int b)
    {
        return c != null && c.getRed() == r && c.getGreen() == g && c.getBlue() == b;
    }

    /**
     * Staged on the frozen railway: the placed train with the longest offered route, past its first sensor, then held
     * there - simulation switched off once the run is waiting for that sensor, so the next never comes.
     *
     * MUTATION: put the old yellow back on a destination, or the old red on a station not yet reached, or the navy on
     * the Auto tab's badge, and this fails.
     *
     * @throws Exception from the window or the railway
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testACaptionAlongARunSpeaksTheRoutesColours() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("needs a display");

        support.LayoutSandbox sandbox = null;
        MarklinControlStation model = null;
        TrainControlUI ui = null;
        Thread run = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            model = init(null, true, true, false, true);
            model.setNetworkCommState(false);

            ui = (TrainControlUI) model.getGUI();

            assertNotNull(ui, "there is no window");

            settle(300);

            AutonomySession session = ui.getAutonomySession();

            if (session == null) throw new SkipException("no autonomy setup on this railway");

            model.parseAuto(session.buildConfiguration());

            final Layout layout = model.getAutoLayout();

            assertTrue(layout != null && layout.isSimulate(),
                "precondition: the frozen railway's setup is not simulated here, so no run can be staged");

            Locomotive train = null;
            List<Edge> path = null;

            for (Point p : layout.getPoints())
            {
                Locomotive l = p.getCurrentLocomotive();

                if (l == null) continue;

                for (List<Edge> candidate : layout.getPossiblePaths(l, false))
                {
                    if (candidate.size() >= 3 && (path == null || candidate.size() > path.size()))
                    {
                        train = l;
                        path = candidate;
                    }
                }
            }

            assertNotNull(path, "precondition: no train on the frozen railway has a route of three legs");

            final Locomotive mover = train;
            final List<Edge> road = path;

            run = new Thread(() -> layout.executePath(road, mover, 30, null), "captions run");
            run.setDaemon(true);
            run.start();

            Field pendingField = Layout.class.getDeclaredField("locomotivePendingS88");
            pendingField.setAccessible(true);
            Map<?, ?> pending = (Map<?, ?>) pendingField.get(layout);

            String startedAt = layout.getLatestMilestoneS88(mover);

            long until = System.currentTimeMillis() + 30000;

            while ((pending.get(mover) == null || pending.get(mover).equals(startedAt))
                && System.currentTimeMillis() < until) Thread.sleep(5);

            Field simulate = Layout.class.getDeclaredField("simulate");
            simulate.setAccessible(true);
            simulate.setBoolean(layout, false);

            Thread.sleep(3000);

            assertTrue(layout.getActiveLocomotives().containsKey(mover),
                "precondition: the staged run is not under way, so there is no route to caption");

            ui.updateVisiblePoints();

            settle(1200);

            Field stations = TrainControlUI.class.getDeclaredField("layoutStations");
            stations.setAccessible(true);

            final List<StationCaption> pills = new ArrayList<>();

            final TrainControlUI window = ui;

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    for (Set<JLabel> labels : ((Map<Object, Set<JLabel>>) stations.get(window)).values())
                    {
                        for (JLabel label : labels)
                        {
                            if (label instanceof StationCaption && ((StationCaption) label).isPill() && label.isVisible())
                            {
                                pills.add((StationCaption) label);
                            }
                        }
                    }
                }
                catch (IllegalAccessException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertFalse(pills.isEmpty(), "precondition: the window shows no station captions");

            StationCaption destination = null;
            int ahead = 0, red = 0, oldYellow = 0;

            for (StationCaption pill : pills)
            {
                Color fill = pill.getBackground(), text = pill.getForeground();

                if (rgb(fill, 255, 214, 64)) destination = pill;
                if (rgb(fill, 255, 255, 0)) oldYellow++;
                if (text != null && text.getBlue() > text.getRed() + 60 && text.getBlue() > text.getGreen() + 40) ahead++;
                if (text != null && text.getRed() > 140 && text.getGreen() < 100 && text.getBlue() < 100) red++;
            }

            assertEquals(oldYellow, 0, "a destination is still the old pure yellow");

            assertNotNull(destination, mover.getName() + "'s destination is not the warm yellow of the look proposals");

            Color text = destination.getForeground();

            assertTrue((0.299 * text.getRed() + 0.587 * text.getGreen() + 0.114 * text.getBlue()) / 255.0 < 0.3,
                "the destination's text is " + text + ", not dark");

            assertEquals(red, 0, red + " captions along the run are still in the old red");

            assertTrue(ahead > 0, "no caption along " + mover.getName() + "'s run is in the route's blue");

            // THE AUTO TAB'S BADGE AND TEXT, the window's label blue (Adam, 2026-10-09: "On the Autonomy Locomotive
            // Commands tab, match the color of the text and pills to the darker blue used on text labels"): every card
            // whose badge is neither idle grey nor the home teal, and every destination not written in red
            List<AutoLocomotiveStatus> cards = new ArrayList<>();

            collect(ui.getContentPane(), cards);

            assertFalse(cards.isEmpty(), "precondition: the Auto tab has no locomotive cards");

            Field badgeField = AutoLocomotiveStatus.class.getDeclaredField("locStation");
            badgeField.setAccessible(true);

            Field destinationField = AutoLocomotiveStatus.class.getDeclaredField("locDest");
            destinationField.setAccessible(true);

            int blueBadges = 0;

            for (AutoLocomotiveStatus card : cards)
            {
                Color badge = ((JLabel) badgeField.get(card)).getBackground();

                if (badge.equals(Color.LIGHT_GRAY) || badge.equals(TrainControlUI.COLOR_AT_HOME)) continue;

                blueBadges++;

                assertTrue(rgb(badge, 0, 0, 115), "an Auto tab card's station badge is " + badge + ", not the darker blue"
                    + " the window's labels are written in, rgb(0,0,115)");

                Color written = ((JLabel) destinationField.get(card)).getForeground();

                if (written.getRed() > 140) continue;

                assertTrue(rgb(written, 0, 0, 115), "an Auto tab card's text is " + written + ", not the darker blue the"
                    + " window's labels are written in, rgb(0,0,115)");
            }

            assertTrue(blueBadges > 0, "precondition: no Auto tab card has a station badge in blue to look at");
        }
        finally
        {
            if (run != null) run.interrupt();

            if (model != null) model.stop();

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    private static void collect(java.awt.Container in, List<AutoLocomotiveStatus> out)
    {
        for (java.awt.Component c : in.getComponents())
        {
            if (c instanceof AutoLocomotiveStatus) out.add((AutoLocomotiveStatus) c);

            if (c instanceof java.awt.Container) collect((java.awt.Container) c, out);
        }
    }

    /**
     * An empty station's dash is drawn dimmer than a name on the running diagram, as the diagram's own rule for a
     * placeholder says (D1 of the look's design pass; Adam, 2026-10-09: "Do D1 and D2").  `StationCaption.onPill` dims a
     * placeholder, and the editors ask it with the placeholder grey; the running diagram asked it with black, so the dash
     * was as loud as a train's name.
     *
     * MUTATION: colour the dash black again and this fails.
     *
     * @throws Exception from the window or the railway
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testAnEmptyStationsDashIsDimmerThanAName() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("needs a display");

        support.LayoutSandbox sandbox = null;
        MarklinControlStation model = null;
        TrainControlUI ui = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            model = init(null, true, true, false, true);
            model.setNetworkCommState(false);

            ui = (TrainControlUI) model.getGUI();

            assertNotNull(ui, "there is no window");

            settle(300);

            AutonomySession session = ui.getAutonomySession();

            if (session == null) throw new SkipException("no autonomy setup on this railway");

            model.parseAuto(session.buildConfiguration());

            ui.updateVisiblePoints();

            settle(1000);

            Field stations = TrainControlUI.class.getDeclaredField("layoutStations");
            stations.setAccessible(true);

            final List<StationCaption> pills = new ArrayList<>();
            final TrainControlUI window = ui;

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    for (Set<JLabel> labels : ((Map<Object, Set<JLabel>>) stations.get(window)).values())
                    {
                        for (JLabel label : labels)
                        {
                            if (label instanceof StationCaption && ((StationCaption) label).isPill() && label.isVisible())
                            {
                                pills.add((StationCaption) label);
                            }
                        }
                    }
                }
                catch (IllegalAccessException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            double nameContrast = -1, dashContrast = -1;

            for (StationCaption pill : pills)
            {
                Color fill = pill.getBackground(), text = pill.getForeground();

                if (fill == null || text == null) continue;

                double contrast = Math.sqrt(Math.pow(fill.getRed() - text.getRed(), 2)
                    + Math.pow(fill.getGreen() - text.getGreen(), 2) + Math.pow(fill.getBlue() - text.getBlue(), 2));

                if (org.traincontrol.gui.LayoutGrid.LAYOUT_STATION_EMPTY.equals(pill.getText())) dashContrast = contrast;
                else if (pill.getText().matches(".*[A-Za-z0-9].*")) nameContrast = Math.max(nameContrast, contrast);
            }

            assertTrue(dashContrast >= 0 && nameContrast >= 0,
                "precondition: the frozen railway shows no empty station or no named caption at rest");

            assertTrue(dashContrast < nameContrast - 40, "an empty station's dash stands out from its pill by "
                + Math.round(dashContrast) + ", a train's name by " + Math.round(nameContrast) + " - the placeholder is as"
                + " loud as a name");
        }
        finally
        {
            if (model != null) model.stop();

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }
}
