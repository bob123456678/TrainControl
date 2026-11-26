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
import org.traincontrol.gui.autonomy.AutonomyLocomotiveStatus;
import org.traincontrol.gui.widgets.StationCaption;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.Reflect;

/**
 * A running route's captions speak the route's colors, its destination is a warm yellow, and the Auto tab's station
 * badge is the captions' blue (Adam: "Build 2,3,5,6", the diagram look proposals), asked of the real window on his
 * frozen railway with a run staged part way along its route.  The colors themselves are claimed beside the pill
 * (`ui.testDiagramLooksRight`); this asks `StationCaptionText.updateStationLabels`, since a rule tested apart from its
 * caller says nothing about the caller.
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

    /** Whether a color is this one, whatever its transparency. */
    private static boolean sameInk(Color c, Color wanted)
    {
        return rgb(c, wanted.getRed(), wanted.getGreen(), wanted.getBlue());
    }

    /**
     * The darker blue the window's own labels are written in (`HEADING_COLOUR`, which the Auto tab's `TAB_BLUE` is),
     * asked rather than restated.
     */
    private static final Color LABEL_BLUE =
        support.ProgramConstants.colour(org.traincontrol.gui.AutonomyConfigurationsPanel.class, "HEADING_COLOUR");

    /** The train the run is staged with, from TopMainR2Inter to TopMainR1. */
    private static final String MOVER = "2-8-4 3505 SP";

    /**
     * Staged on the frozen railway: 2-8-4 3505 SP from TopMainR2Inter to TopMainR1, with 75 407 DB standing at
     * BottomMainA past its first sensor, then held there: simulation is switched off once the run is waiting for that
     * sensor.
     *
     * MUTATION: put the old yellow back on a destination, or the old red on a station not yet reached, or the navy on
     * the Auto tab's badge, and this fails.
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
        final Map<Locomotive, Integer> lengthsWere = new java.util.HashMap<>();

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            sandbox.simulated();

            // ITS TRAINS, NAMED (FR-136): the run, and one train standing by.
            sandbox.withNoTrains()
                .standing(new org.traincontrol.automationui.TileGraph.TileKey("1 - Main", 0, 11), MOVER, "E", "N")
                .standing(new org.traincontrol.automationui.TileGraph.TileKey("1 - Main", 20, 12), "75 407 DB", "E", "W");

            model = support.TestStation.simulatedWithWindowInDebug();
            model.setNetworkCommState(false);

            // ITS TRAINS' LENGTHS, CLEARED: his 3505 SP of four units cannot stop at TopMainR1 (room for three), and a
            // claim about colors keeps the length rule out.
            for (String name : new String[] {MOVER, "75 407 DB"})
            {
                Locomotive borrowed = model.getLocByName(name);

                assertNotNull(borrowed, "precondition: the database has no " + name);

                lengthsWere.put(borrowed, borrowed.getTrainLength());

                borrowed.setTrainLength(0);
            }

            ui = (TrainControlUI) model.getGUI();

            assertNotNull(ui, "there is no window");

            settle(300);

            AutonomySession session = ui.getAutonomySession();

            if (session == null) throw new AssertionError("no autonomy setup on this railway");

            model.parseAuto(session.buildConfiguration());

            final Layout layout = model.getAutoLayout();

            assertTrue(layout != null && layout.isSimulate(),
                "precondition: the frozen railway's setup is not simulated here, so no run can be staged");

            Locomotive train = null;
            List<Edge> path = null;

            for (Point p : layout.getPoints())
            {
                Locomotive l = p.getCurrentLocomotive();

                if (l == null || !MOVER.equals(l.getName())) continue;

                for (List<Edge> candidate : layout.getPossiblePaths(l, false))
                {
                    if (!"TopMainR1".equals(candidate.get(candidate.size() - 1).getEnd().getName())) continue;

                    if (candidate.size() >= 3 && (path == null || candidate.size() > path.size()))
                    {
                        train = l;
                        path = candidate;
                    }
                }
            }

            assertNotNull(path, "precondition: " + MOVER + " has no route of three legs from TopMainR2Inter to TopMainR1");

            final Locomotive mover = train;
            final List<Edge> road = path;

            run = new Thread(() -> layout.executePath(road, mover, 30, null), "captions run");
            run.setDaemon(true);
            run.start();

            Map<?, ?> pending = (Map<?, ?>) Reflect.get(Layout.class, layout, "locomotivePendingS88");

            String startedAt = layout.getLatestMilestoneS88(mover);

            long until = System.currentTimeMillis() + 30000;

            while ((pending.get(mover) == null || pending.get(mover).equals(startedAt))
                && System.currentTimeMillis() < until) Thread.sleep(5);

            Field simulate = Reflect.field(Layout.class, "simulate");
            simulate.setBoolean(layout, false);

            Thread.sleep(3000);

            assertTrue(layout.getActiveLocomotives().containsKey(mover),
                "precondition: the staged run is not under way, so there is no route to caption");

            ui.updateVisiblePoints();

            settle(1200);

            Field stations = Reflect.field(TrainControlUI.class, "layoutStations");

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
            int ahead = 0, red = 0, oldYellow = 0, warmYellow = 0;

            for (StationCaption pill : pills)
            {
                Color fill = pill.getBackground(), text = pill.getForeground();

                // THE TRAIN'S LINE AS IT SHOWS ON WHITE, SOLID (Adam: "Make the yellow labels (trains on their way
                // somewhere) have the same orange background color as occupied train tiles, just without the fading",
                // then "Go with as the line actually looks on white")
                if (sameInk(fill, DiagramColours.DESTINATION) && fill.getAlpha() == 255) destination = pill;
                if (rgb(fill, 255, 214, 64)) warmYellow++;
                if (rgb(fill, 255, 255, 0)) oldYellow++;
                if (text != null && text.getBlue() > text.getRed() + 60 && text.getBlue() > text.getGreen() + 40) ahead++;
                if (text != null && text.getRed() > 140 && text.getGreen() < 100 && text.getBlue() < 100) red++;
            }

            assertEquals(oldYellow, 0, "a destination is still the old pure yellow");

            assertEquals(warmYellow, 0, "a destination is still the warm yellow, where Adam asked for the orange of the track a"
                + " train is on");

            assertNotNull(destination, mover.getName() + "'s destination is not the train's line as it shows on white,"
                + " " + DiagramColours.DESTINATION + ", solid");

            Color text = destination.getForeground();

            assertTrue((0.299 * text.getRed() + 0.587 * text.getGreen() + 0.114 * text.getBlue()) / 255.0 < 0.3,
                "the destination's text is " + text + ", not dark");

            assertEquals(red, 0, red + " captions along the run are still in the old red");

            assertTrue(ahead > 0, "no caption along " + mover.getName() + "'s run is in the route's blue");

            // THE AUTO TAB'S BADGE AND TEXT, the window's label blue (Adam: "match the color of the text and pills to
            // the darker blue used on text labels"): every card whose badge is neither idle gray nor home teal, and
            // every destination not in red
            List<AutonomyLocomotiveStatus> cards = new ArrayList<>();

            collect(ui.getContentPane(), cards);

            assertFalse(cards.isEmpty(), "precondition: the Auto tab has no locomotive cards");

            Field badgeField = Reflect.field(AutonomyLocomotiveStatus.class, "locStation");

            Field destinationField = Reflect.field(AutonomyLocomotiveStatus.class, "locDest");

            int blueBadges = 0;

            for (AutonomyLocomotiveStatus card : cards)
            {
                Color badge = ((JLabel) badgeField.get(card)).getBackground();

                if (badge.equals(Color.LIGHT_GRAY) || badge.equals(TrainControlUI.COLOR_AT_HOME)) continue;

                blueBadges++;

                assertTrue(sameInk(badge, LABEL_BLUE), "an Auto tab card's station badge is " + badge + ", not the darker"
                    + " blue the window's labels are written in, " + LABEL_BLUE);

                Color written = ((JLabel) destinationField.get(card)).getForeground();

                if (written.getRed() > 140) continue;

                assertTrue(sameInk(written, LABEL_BLUE), "an Auto tab card's text is " + written + ", not the darker blue"
                    + " the window's labels are written in, " + LABEL_BLUE);
            }

            assertTrue(blueBadges > 0, "precondition: no Auto tab card has a station badge in blue to look at");
        }
        finally
        {
            if (run != null) run.interrupt();

            for (Map.Entry<Locomotive, Integer> was : lengthsWere.entrySet()) was.getKey().setTrainLength(was.getValue());

            if (model != null) model.stop();

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    private static void collect(java.awt.Container in, List<AutonomyLocomotiveStatus> out)
    {
        for (java.awt.Component c : in.getComponents())
        {
            if (c instanceof AutonomyLocomotiveStatus) out.add((AutonomyLocomotiveStatus) c);

            if (c instanceof java.awt.Container) collect((java.awt.Container) c, out);
        }
    }

    /**
     * An empty station's dash is drawn dimmer than a name on the running diagram (D1 of the look's design pass; Adam:
     * "Do D1 and D2"): `StationCaption.onPill` dims a placeholder, and the running diagram asked it with black.
     *
     * MUTATION: color the dash black again and this fails.
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

            sandbox.simulated();

            model = support.TestStation.simulatedWithWindowInDebug();
            model.setNetworkCommState(false);

            ui = (TrainControlUI) model.getGUI();

            assertNotNull(ui, "there is no window");

            settle(300);

            AutonomySession session = ui.getAutonomySession();

            if (session == null) throw new AssertionError("no autonomy setup on this railway");

            model.parseAuto(session.buildConfiguration());

            ui.updateVisiblePoints();

            settle(1000);

            Field stations = Reflect.field(TrainControlUI.class, "layoutStations");

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

    /** The square the main window believes the pointer is over, or null. */
    private static String hovered(TrainControlUI ui) throws Exception
    {
        Object tile = Reflect.get(org.traincontrol.gui.DiagramTrainKeys.class, ui.diagramLocGestures(),
            "hoveredDiagramTile");

        return tile == null ? null : tile.toString();
    }

    /**
     * The pointer on a live station name reports that station's square, leaving it reports none, a right-click opens
     * the autonomy menu, and a left-click on a train's name makes that train the selected one.  A plain track square
     * reports and clears its own square the same way.
     *
     * MUTATION: in `LayoutGrid` (the live caption's `MouseAdapter`), empty `mouseEntered`, `mouseExited` or the BUTTON3
     * branch of `mouseClicked`, or the `jumpToLocomotive` call, and this fails; in `LayoutLabel`, empty the hover
     * listener's `mouseExited`.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testThePointerOnACaptionAndASquareIsHeard() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("needs a display");

        support.LayoutSandbox sandbox = null;
        MarklinControlStation model = null;
        TrainControlUI ui = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            sandbox.simulated();

            // One train standing, named, so the station it stands on shows a name (FR-136)
            sandbox.withNoTrains()
                .standing(new org.traincontrol.automationui.TileGraph.TileKey("1 - Main", 0, 11), MOVER, "E", "N");

            model = support.TestStation.simulatedWithWindowInDebug();
            model.setNetworkCommState(false);

            ui = (TrainControlUI) model.getGUI();

            assertNotNull(ui, "there is no window");

            settle(300);

            AutonomySession session = ui.getAutonomySession();

            if (session == null) throw new AssertionError("no autonomy setup on this railway");

            model.parseAuto(session.buildConfiguration());

            ui.updateVisiblePoints();

            settle(1000);

            final TrainControlUI window = ui;
            final MarklinControlStation station = model;
            final Field stations = Reflect.field(TrainControlUI.class, "layoutStations");
            final Object[] named = new Object[2];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    for (Map.Entry<Object, Set<JLabel>> e : ((Map<Object, Set<JLabel>>) stations.get(window)).entrySet())
                    {
                        for (JLabel label : e.getValue())
                        {
                            if (named[0] == null && label instanceof StationCaption && label.isShowing()
                                && e.getKey().toString().equals(new org.traincontrol.automationui.TileGraph.TileKey(
                                    "1 - Main", 0, 11).toString()))
                            {
                                named[0] = label;
                                named[1] = e.getKey();
                            }
                        }
                    }
                }
                catch (IllegalAccessException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertNotNull(named[0], "precondition: no train's name is shown on a station of the frozen railway");

            final JLabel caption = (JLabel) named[0];

            support.Mouse.enter(caption);

            settle(200);

            assertEquals(hovered(ui), named[1].toString(), "the pointer on a station's name did not report its square");

            support.Mouse.exit(caption);

            settle(200);

            assertNull(hovered(ui), "the pointer leaving a station's name still reports its square");

            try
            {
                assertNotNull(support.Mouse.rightClick(caption, 15000), "a right-click on a station's name opened no menu");
            }
            finally
            {
                support.Mouse.closeMenus();
            }

            final Locomotive train = model.getLocByName(MOVER);

            assertNotNull(train, "precondition: the database has no " + MOVER);

            // The train on one button and another train on the next, which is the one on show: a name is found by
            // the buttons it is mapped to.
            final String other = "75 407 DB";
            final java.awt.Component[] first = new java.awt.Component[1];

            support.Components.onTheEventThread(() ->
            {
                window.mapLocToCurrentButton(MOVER);

                try
                {
                    for (Object b : ((Map<?, ?>) Reflect.get(TrainControlUI.class, window, "labelMapping")).keySet())
                    {
                        if (!train.equals(window.currentLocMapping().get(b)) && first[0] == null) first[0] = (java.awt.Component) b;
                    }

                    Reflect.set(TrainControlUI.class, window, "currentButton", first[0]);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }

                window.mapLocToCurrentButton(other);

                return null;
            });

            assertTrue(support.Components.waitFor(() -> window.getActiveLoc() != null
                && other.equals(window.getActiveLoc().getName()), 10000), "precondition: the other train is not on show");

            support.Mouse.click(caption);

            assertTrue(support.Components.waitFor(() -> train.equals(window.getActiveLoc()), 10000),
                "a click on a train's name on the diagram did not select that train");

            final org.traincontrol.gui.LayoutLabel square = support.Components.find(ui.getContentPane(),
                org.traincontrol.gui.LayoutLabel.class, l -> l.isShowing() && l.getWidth() > 0);

            assertNotNull(square, "precondition: no square is drawn on the diagram");

            support.Mouse.enter(square);

            settle(200);

            assertNotNull(hovered(ui), "the pointer on a square did not report it");

            support.Mouse.exit(square);

            settle(200);

            assertNull(hovered(ui), "the pointer leaving a square still reports it");
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
