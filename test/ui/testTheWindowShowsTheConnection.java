package ui;

import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.udp.NetworkProxy;
import support.Components;
import support.Display;
import support.Reflect;
import support.TcpStation;
import support.WindowField;

/**
 * The main window shows the state of the connection to the Central Station: "(UDP)" after the latency once a CS3's TCP
 * connection has been given up for UDP, and the warning triangle in the lower right while the station is not answering, or
 * red throughout a simulation.
 */
public class testTheWindowShowsTheConnection
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static NetworkProxy proxy;

    /** The triangle as the window was built, in a simulation: its icon drawn, its tooltip, and whether it shows. */
    private static java.awt.image.BufferedImage simulatedIcon;
    private static String simulatedTip;
    private static boolean simulatedShown;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        Display.needed("the latency and Power OFF are on the main window");

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));
        model = support.TestStation.simulatedWithWindowInDebug();
        ui = (TrainControlUI) model.getGUI();
        proxy = (NetworkProxy) Reflect.get(MarklinControlStation.class, model, "NetworkInterface");

        JLabel triangle = WindowField.of(ui, "stationNotAnswering");

        Components.onTheEventThread(() ->
        {
            simulatedIcon = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = simulatedIcon.createGraphics();

            triangle.getIcon().paintIcon(triangle, g, 0, 0);
            g.dispose();

            simulatedTip = triangle.getToolTipText();
            simulatedShown = triangle.isVisible();
            return null;
        });

        // The other claims are about a real station, which is not a simulation: the window asks the model which it is
        Reflect.set(MarklinControlStation.class, model, "simulation", false);
        answered();
    }

    /**
     * In a simulation there is no Central Station to answer: the triangle shows from the start, red,
     * and its tooltip says TrainControl is not connected (FR-228).
     *
     * MUTATION: drop `on |= simulated` from `TrainControlUI.warnOnStop`, or have `RowIcons.warning` fill amber whatever
     * `red` asks, and this fails.
     */
    @Test
    public void testASimulationShowsTheRedTriangleFromTheStart() throws Exception
    {
        assertTrue(simulatedShown, "no triangle in a simulation");
        assertEquals(simulatedTip, org.traincontrol.util.I18n.t("ui.main.tooltip.simulation"),
            "the triangle does not say TrainControl is simulating");

        int red = 0, amber = 0, light = 0;

        for (int argb : simulatedIcon.getRGB(0, 0, 16, 16, null, 0, 16))
        {
            if ((argb >>> 24) < 255) continue;

            if (amber(argb)) amber++;
            if (((argb >> 16) & 255) > 180 && ((argb >> 8) & 255) < 80 && (argb & 255) < 80) red++;
            if (((argb >> 16) & 255) > 220 && ((argb >> 8) & 255) > 220 && (argb & 255) > 220) light++;
        }

        assertTrue(red > 40 && amber == 0, "the triangle is not red: " + red + " red and " + amber + " amber pixels");
        assertTrue(light >= 4, "the triangle's mark does not read light on the red: " + light + " light pixels");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /** A ping answered in 12 ms, as the window shows it once the event thread has run. */
    private static void answered() throws Exception
    {
        ui.updateLatency(12);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static String latency() throws Exception
    {
        JLabel label = WindowField.of(ui, "latencyLabel");

        answered();

        return Components.onTheEventThread(label::getText);
    }

    /**
     * The latency names the protocol in use: TCP while a CS3 is on TCP, UDP once that connection is given up, and UDP
     * for a CS2, which uses nothing else; none in a simulation with no station.
     *
     * MUTATION: name UDP in `TrainControlUI.updateLatency` whatever the model's transport is, and this fails.
     */
    @Test(timeOut = 60000)
    public void testTheLatencyNamesTheProtocolInUse() throws Exception
    {
        assertFalse(latency().endsWith("P)"), "a simulation with no station named a protocol: " + latency());

        TcpStation station = new TcpStation();

        try
        {
            model.setNetworkCommState(true);
            assertTrue(latency().endsWith("(UDP)"), "a CS2, which uses UDP, was not said to: " + latency());

            proxy.connectOverTcp(station.port());
            assertTrue(latency().endsWith("(TCP)"), "a CS3 on TCP was not said to be: " + latency());

            station.close();
            assertTrue(Components.waitFor(() -> tcpGivenUp(), 30000), "precondition: the closed connection was kept");
            assertTrue(latency().endsWith("(UDP)"), "a CS3 whose TCP connection was given up was not said to be on UDP: "
                + latency());
        }
        finally
        {
            station.close();
            model.setNetworkCommState(false);
            Reflect.set(NetworkProxy.class, proxy, "tcpPort", 0);
        }
    }

    /** Whether the proxy has no TCP connection and is replacing none. */
    private static boolean tcpGivenUp()
    {
        try
        {
            return Reflect.get(NetworkProxy.class, proxy, "tcp") == null && !proxy.isReplacing();
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The warning triangle shows, with a tooltip saying why, while the connection is being replaced,
     * while a frame has gone unanswered over a second, and while the pings go unanswered; it is gone again once the
     * station answers, and the button has its text and size back.
     *
     * MUTATION: never show the triangle in `TrainControlUI.warnOnStop`, and this fails.
     */
    @Test
    public void testStopWearsTheTriangleWhileTheStationIsNotAnswering() throws Exception
    {
        JButton stop = WindowField.of(ui, "PowerOff");
        JLabel triangle = WindowField.of(ui, "stationNotAnswering");
        String text = Components.onTheEventThread(stop::getText);
        Dimension size = Components.onTheEventThread(stop::getSize);

        answered();
        assertFalse(triangle.isVisible(), "precondition: the triangle shows while the station answers");

        Reflect.set(NetworkProxy.class, proxy, "replacing", true);

        try
        {
            answered();
            assertTrue(triangle.isVisible(), "no triangle while the connection is replaced");
            assertEquals(triangle.getToolTipText(), org.traincontrol.util.I18n.t("ui.main.tooltip.stationNotAnswering"),
                "the triangle does not say why");
        }
        finally
        {
            Reflect.set(NetworkProxy.class, proxy, "replacing", false);
        }

        answered();
        assertFalse(triangle.isVisible(), "the triangle stayed once the connection was back and answering");

        // A frame written 2 s ago on a live connection, nothing heard since
        long now = System.nanoTime();
        Reflect.set(NetworkProxy.class, proxy, "tcp", new java.net.Socket());
        Reflect.set(NetworkProxy.class, proxy, "lastHeardAt", now - 3000000000L);
        Reflect.set(NetworkProxy.class, proxy, "firstUnansweredAt", now - 2000000000L);

        try
        {
            answered();
            assertTrue(triangle.isVisible(), "no triangle while a frame has gone unanswered over a second");
        }
        finally
        {
            Reflect.set(NetworkProxy.class, proxy, "tcp", null);
            Reflect.set(NetworkProxy.class, proxy, "firstUnansweredAt", 0L);
        }

        answered();
        assertFalse(triangle.isVisible(), "the triangle stayed once the frame was answered");

        // The pings unanswered for 10 s, as the 5 s watch finds them
        Reflect.set(MarklinControlStation.class, model, "pingOutstandingSince", System.nanoTime() - 10000000000L);

        try
        {
            Reflect.method(TrainControlUI.class, "watchTheConnection").invoke(ui);
            SwingUtilities.invokeAndWait(() -> { });
            assertTrue(triangle.isVisible(), "no triangle while the pings go unanswered");
        }
        finally
        {
            Reflect.set(MarklinControlStation.class, model, "pingOutstandingSince", 0L);
        }

        answered();
        assertFalse(triangle.isVisible(), "the triangle stayed once the pings were answered");
        assertEquals(stop.getToolTipText(), "Escape", "Power OFF's tooltip changed");
        assertEquals(Components.onTheEventThread(stop::getText), text, "Power OFF's text changed");
        Components.onTheEventThread(() -> { ui.validate(); return null; });
        assertEquals(Components.onTheEventThread(stop::getSize), size, "Power OFF did not get its size back");
    }

    /**
     * The triangle is drawn true at 1x: the mark is mirror-even about the triangle's middle, and a row of amber parts
     * its dot from the base.
     *
     * MUTATION: take the `VALUE_STROKE_PURE` hint out of `RowIcons.warning`, or put the dot back on the base, and this
     * fails.
     */
    @Test
    public void testTheTrianglesMarkIsCenteredAndSitsOnTheTextLine() throws Exception
    {
        JButton stop = WindowField.of(ui, "PowerOff");
        JLabel shown = WindowField.of(ui, "stationNotAnswering");

        notAnswering(true);

        try
        {
            Components.onTheEventThread(() ->
            {
                java.awt.Container row = ui.getContentPane();

                java.awt.image.BufferedImage drawn = new java.awt.image.BufferedImage(row.getWidth(), row.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = drawn.createGraphics();

                row.paint(g);
                g.dispose();

                // Where the label puts its icon, as its painter does
                java.awt.Rectangle icon = new java.awt.Rectangle(), unused = new java.awt.Rectangle();

                SwingUtilities.layoutCompoundLabel(shown, shown.getFontMetrics(shown.getFont()), null, shown.getIcon(),
                    shown.getVerticalAlignment(), shown.getHorizontalAlignment(), shown.getVerticalTextPosition(),
                    shown.getHorizontalTextPosition(), new java.awt.Rectangle(shown.getSize()), icon, unused, 0);

                icon = SwingUtilities.convertRectangle(shown, icon, row);

                int back = drawn.getRGB(icon.x, icon.y);

                // The mark: near-black inside the triangle
                int[] mark = inkBox(drawn, icon, rgb -> ((rgb >> 16) & 255) < 100 && ((rgb >> 8) & 255) < 100);

                // The icon's middle: its outline's antialiasing need not fall evenly on the two sides at 1x
                double middle = icon.x + (icon.width - 1) / 2.0;

                assertEquals((mark[0] + mark[2]) / 2.0, middle, 0.01, "the mark is not on the triangle's middle: mark"
                    + " columns " + mark[0] + ".." + mark[2] + ", icon " + icon.x + ".." + (icon.x + icon.width - 1));

                for (int y = icon.y; y < icon.y + icon.height; y++)
                {
                    for (int x = icon.x; x < icon.x + icon.width; x++)
                    {
                        int mirror = (int) Math.round(2 * middle - x);

                        assertEquals(dark(drawn.getRGB(x, y)), dark(drawn.getRGB(mirror, y)), "the mark is not mirror-even:"
                            + " row " + (y - icon.y) + ", columns " + (x - icon.x) + " and " + (mirror - icon.x));
                    }
                }

                // Below the dot, before the base's edge, a row of the triangle's amber
                int column = (int) Math.floor(middle);

                assertTrue(amber(drawn.getRGB(column, mark[3] + 1)), "the dot sits on the base, with no amber between");

                return null;
            });
        }
        finally
        {
            notAnswering(false);
        }
    }

    /** The triangle shown or taken down, as a connection being replaced does, and the row laid out. */
    private static void notAnswering(boolean not) throws Exception
    {
        Reflect.set(NetworkProxy.class, proxy, "replacing", not);

        answered();

        Components.onTheEventThread(() -> { ui.validate(); return null; });
    }

    /** The window's locomotive panel, which holds Power OFF and the function tabs. */
    private static java.awt.Container panel()
    {
        return WindowField.of(ui, "LocFunctionsPanel");
    }

    /** Where every part of the panel but the triangle is, as one text, in the panel's coordinates. */
    private static String others(boolean withTabs)
    {
        return Components.onTheEventThread(() ->
        {
            java.awt.Container panel = panel();
            StringBuilder out = new StringBuilder("panel " + panel.getSize());

            for (String name : new String[] {"PowerOff", "OnButton", "CurrentKeyLabel", "ActiveLocLabel", "Backward",
                "Forward", "SpeedSlider", "locIcon", "FunctionTabs"})
            {
                java.awt.Component c = WindowField.of(ui, name);

                if (name.equals("FunctionTabs") && !withTabs) continue;

                out.append("\n  ").append(name).append(' ').append(SwingUtilities.convertRectangle(c.getParent(),
                    c.getBounds(), panel));
            }

            return out.toString();
        });
    }

    /** Where Power OFF, Power ON and the two labels above the locomotive's rows are, in the panel's coordinates. */
    private static String top()
    {
        StringBuilder out = new StringBuilder();

        for (String name : new String[] {"PowerOff", "OnButton", "CurrentKeyLabel", "ActiveLocLabel"})
        {
            java.awt.Component c = WindowField.of(ui, name);

            out.append(name).append(' ').append(SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), panel()))
                .append(';');
        }

        return out.toString();
    }

    /** Makes `loc` (null for none) the active locomotive and lets the window draw it, then lays the window out. */
    private static void show(org.traincontrol.base.Locomotive loc) throws Exception
    {
        Reflect.set(TrainControlUI.class, ui, "activeLoc", loc);
        SwingUtilities.invokeAndWait(() -> ui.repaintLoc(true, null));

        @SuppressWarnings("unchecked")
        java.util.List<java.util.concurrent.Future<?>> futures = (java.util.List<java.util.concurrent.Future<?>>)
            Reflect.get(TrainControlUI.class, ui, "locFutures");
        assertTrue(Components.waitFor(() ->
        {
            synchronized (ui)
            {
                for (java.util.concurrent.Future<?> f : new java.util.ArrayList<>(futures)) if (!f.isDone()) return false;
            }

            return true;
        }, 10000), "the window's render did not finish");

        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        Components.onTheEventThread(() -> { ui.validate(); return null; });
    }

    /**
     * The triangle stands in the lower right of the locomotive panel, in the empty end of the function tabs' strip
     * (Adam on MT-1073: "can it actually go in the lower-right of the app, where there is negative space from the
     * function tabbed pane?"): right of the last tab and inside the panel, centered on the strip within a pixel, shown
     * in a simulation with no locomotive too, where it stands exactly as with one. Nothing else moves with the triangle,
     * and with no locomotive Power OFF, Power ON and the two labels above the rows stand as with one. Versus dev only
     * the picture's empty label moves then (it holds no icon or text and draws nothing), since the rows hidden with
     * no locomotive keep their room.
     *
     * MUTATION: put the triangle in a panel with Power OFF again (the old `installNotAnsweringMark`), leave it out
     * of the holder `installNotAnsweringMark` puts around the function tabs, or drop its `setHonorsVisibility` calls, and
     * this fails.
     */
    @Test
    public void testTheTriangleStandsInTheLowerRightPastTheFunctionTabs() throws Exception
    {
        JLabel triangle = WindowField.of(ui, "stationNotAnswering");
        javax.swing.JTabbedPane tabs = WindowField.of(ui, "FunctionTabs");
        org.traincontrol.marklin.MarklinLocomotive one = model.newMM2Locomotive("TriangleOne", 4001);
        org.traincontrol.marklin.MarklinLocomotive two = model.newMFXLocomotive("TriangleTwo", 4002);

        java.awt.Rectangle[] first = new java.awt.Rectangle[1];
        String[] firstTop = new String[1];

        try
        {
            for (org.traincontrol.base.Locomotive loc : new org.traincontrol.base.Locomotive[] {one, null, two})
            {
                show(loc);

                String what = loc == null ? "no locomotive" : loc.getName();
                boolean tabsShown = Components.onTheEventThread(tabs::isVisible);

                assertEquals(tabsShown, loc != null, "precondition: tabs shown with " + what);

                // In a simulation the triangle shows with no help from the connection
                Reflect.set(MarklinControlStation.class, model, "simulation", true);
                notAnswering(false);
                assertTrue(Components.onTheEventThread(triangle::isVisible), what + ": no triangle in a simulation");

                String without = others(tabsShown);

                // And a station that is not answering shows it, too: nothing moves
                Reflect.set(MarklinControlStation.class, model, "simulation", false);

                try
                {
                    notAnswering(false);
                    assertFalse(Components.onTheEventThread(triangle::isVisible), what + ": triangle while answering");
                    assertEquals(others(tabsShown), without, what + ": a part moved when the triangle went");
                    notAnswering(true);
                    assertTrue(Components.onTheEventThread(triangle::isVisible), what + ": no triangle, not answering");
                    assertEquals(others(tabsShown), without, what + ": a part moved when the triangle came");
                }
                finally
                {
                    Reflect.set(MarklinControlStation.class, model, "simulation", false);
                    notAnswering(false);
                }

                Components.onTheEventThread(() ->
                {
                    java.awt.Container panel = panel();
                    java.awt.Rectangle at = SwingUtilities.convertRectangle(triangle.getParent(), triangle.getBounds(), panel);
                    java.awt.Rectangle strip = SwingUtilities.convertRectangle(tabs, tabs.getBoundsAt(tabs.getTabCount() - 1),
                        panel);

                    assertTrue(loc == null || tabs.getTabCount() == (loc == two ? 2 : 1), what + ": tabs: " + tabs.getTabCount());
                    assertTrue(at.width > 0 && at.height > 0, what + ": the triangle has no size");
                    assertTrue(new java.awt.Rectangle(panel.getSize()).contains(at), what + ": the triangle is outside the panel");
                    assertTrue(at.x >= strip.x + strip.width, what + ": the triangle is not right of the last tab: " + at
                        + " tab " + strip);
                    assertTrue(at.x > panel.getWidth() / 2, what + ": the triangle is not on the right: " + at);
                    if (first[0] == null) first[0] = at;

                    JLabel icon = WindowField.of(ui, "locIcon");

                    // The picture's label is empty with no locomotive: it draws nothing, and its tools need a locomotive
                    assertTrue(loc != null || (icon.getIcon() == null && icon.getText().isEmpty() && !icon.isOpaque()),
                        what + ": the picture's label draws something");
                    assertEquals(top(), loc == null ? firstTop[0] : (firstTop[0] = top()), what + ": a part above moved");
                    assertEquals(at, first[0], what + ": the triangle is not where it is with a locomotive");
                    assertEquals(at.y + at.height / 2.0, strip.y + strip.height / 2.0, 1.0, what + ": not centered on the strip");
                    return null;
                });
            }
        }
        finally
        {
            show(null);
            model.deleteLoc("TriangleOne");
            model.deleteLoc("TriangleTwo");
        }
    }

    /** {left, top, right, bottom} of the pixels in `in` that pass; fails where none do. */
    private static int[] inkBox(java.awt.image.BufferedImage drawn, java.awt.Rectangle in,
        java.util.function.IntPredicate ink)
    {
        int[] out = {Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1};

        for (int y = in.y; y < in.y + in.height; y++)
        {
            for (int x = in.x; x < in.x + in.width; x++)
            {
                if (!ink.test(drawn.getRGB(x, y))) continue;

                out[0] = Math.min(out[0], x);
                out[1] = Math.min(out[1], y);
                out[2] = Math.max(out[2], x);
                out[3] = Math.max(out[3], y);
            }
        }

        assertTrue(out[2] >= 0, "nothing drawn in " + in);

        return out;
    }

    private static int distance(int one, int other)
    {
        int out = 0;

        for (int shift = 0; shift <= 16; shift += 8) out = Math.max(out, Math.abs(((one >> shift) & 255) - ((other >> shift) & 255)));

        return out;
    }

    private static boolean dark(int rgb)
    {
        return ((rgb >> 16) & 255) < 100 && ((rgb >> 8) & 255) < 100;
    }

    private static boolean amber(int rgb)
    {
        return ((rgb >> 16) & 255) > 220 && ((rgb >> 8) & 255) > 150 && (rgb & 255) < 120;
    }
}
