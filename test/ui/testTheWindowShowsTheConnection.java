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
 * connection has been given up for UDP, and the warning triangle on Power OFF while the station is not answering.
 */
public class testTheWindowShowsTheConnection
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static NetworkProxy proxy;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        Display.needed("the latency and Power OFF are on the main window");

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));
        model = support.TestStation.simulatedWithWindowInDebug();
        ui = (TrainControlUI) model.getGUI();
        proxy = (NetworkProxy) Reflect.get(MarklinControlStation.class, model, "NetworkInterface");
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
     * The latency says "(UDP)" once a CS3 that would not take TCP is reached over UDP instead, and not for a station
     * never asked for TCP, as a CS2 is.
     *
     * MUTATION: answer false from `NetworkProxy.isOnUdpInsteadOfTcp`, and this fails.
     */
    @Test
    public void testTheLatencySaysUdpAfterACS3FellBackToIt() throws Exception
    {
        assertFalse(latency().contains("(UDP)"), "a station never asked for TCP, as a CS2, was said to be on UDP"
            + " instead: " + latency());

        try
        {
            proxy.connectOverTcp(TcpStation.closedPort());

            assertTrue(latency().contains("(UDP)"), "a CS3 that would not take TCP, reached over UDP, was not said to be"
                + " on UDP: " + latency());
        }
        finally
        {
            Reflect.set(NetworkProxy.class, proxy, "tcpPort", 0);
        }
    }

    /**
     * Power OFF wears the warning triangle, with a tooltip saying why, while the connection is being replaced, while a
     * frame has gone unanswered over a second, and while the pings go unanswered; it is gone again once the station
     * answers, and the button keeps its text and size throughout.
     *
     * MUTATION: never set the icon in `TrainControlUI.warnOnStop`, and this fails.
     */
    @Test
    public void testStopWearsTheTriangleWhileTheStationIsNotAnswering() throws Exception
    {
        JButton stop = WindowField.of(ui, "PowerOff");
        String text = Components.onTheEventThread(stop::getText);
        Dimension size = Components.onTheEventThread(stop::getSize);

        answered();
        assertNull(stop.getIcon(), "precondition: Power OFF wears a triangle while the station answers");

        Reflect.set(NetworkProxy.class, proxy, "replacing", true);

        try
        {
            answered();
            assertNotNull(stop.getIcon(), "no triangle while the connection is replaced");
            assertNotEquals(stop.getToolTipText(), "Escape", "the triangle does not say why");
        }
        finally
        {
            Reflect.set(NetworkProxy.class, proxy, "replacing", false);
        }

        answered();
        assertNull(stop.getIcon(), "the triangle stayed once the connection was back and answering");

        // A frame written 2 s ago on a live connection, nothing heard since
        long now = System.nanoTime();
        Reflect.set(NetworkProxy.class, proxy, "tcp", new java.net.Socket());
        Reflect.set(NetworkProxy.class, proxy, "lastHeardAt", now - 3000000000L);
        Reflect.set(NetworkProxy.class, proxy, "firstUnansweredAt", now - 2000000000L);

        try
        {
            answered();
            assertNotNull(stop.getIcon(), "no triangle while a frame has gone unanswered over a second");
        }
        finally
        {
            Reflect.set(NetworkProxy.class, proxy, "tcp", null);
            Reflect.set(NetworkProxy.class, proxy, "firstUnansweredAt", 0L);
        }

        answered();
        assertNull(stop.getIcon(), "the triangle stayed once the frame was answered");

        // The pings unanswered for 10 s, as the 5 s watch finds them
        Reflect.set(MarklinControlStation.class, model, "pingOutstandingSince", System.nanoTime() - 10000000000L);

        try
        {
            Reflect.method(TrainControlUI.class, "watchTheConnection").invoke(ui);
            SwingUtilities.invokeAndWait(() -> { });
            assertNotNull(stop.getIcon(), "no triangle while the pings go unanswered");
        }
        finally
        {
            Reflect.set(MarklinControlStation.class, model, "pingOutstandingSince", 0L);
        }

        answered();
        assertNull(stop.getIcon(), "the triangle stayed once the pings were answered");
        assertEquals(stop.getToolTipText(), "Escape", "the tooltip was not given back");
        assertEquals(Components.onTheEventThread(stop::getText), text, "Power OFF's text changed");
        assertEquals(Components.onTheEventThread(stop::getSize), size, "Power OFF changed size");
    }

    /**
     * The triangle on Power OFF is drawn true at 1x (Adam: "make sure the exclamation point is aligned"): the mark is
     * mirror-even about the triangle's middle, a row of amber parts its dot from the base, and the triangle stands
     * centered on the button's text line.
     *
     * MUTATION: take the `VALUE_STROKE_PURE` hint out of `RowIcons.warning`, or put the dot back on the base, and this
     * fails.
     */
    @Test
    public void testTheTrianglesMarkIsCenteredAndSitsOnTheTextLine() throws Exception
    {
        JButton stop = WindowField.of(ui, "PowerOff");

        javax.swing.Icon was = Components.onTheEventThread(stop::getIcon);

        try
        {
            Components.onTheEventThread(() ->
            {
                stop.setIcon(org.traincontrol.gui.widgets.RowIcons.warning(16));

                java.awt.image.BufferedImage drawn = new java.awt.image.BufferedImage(stop.getWidth(), stop.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = drawn.createGraphics();

                stop.paint(g);
                g.dispose();

                java.awt.Rectangle icon = new java.awt.Rectangle(), text = new java.awt.Rectangle();
                java.awt.Insets in = stop.getInsets();
                java.awt.Rectangle view = new java.awt.Rectangle(in.left, in.top, stop.getWidth() - in.left - in.right,
                    stop.getHeight() - in.top - in.bottom);

                SwingUtilities.layoutCompoundLabel(stop, stop.getFontMetrics(stop.getFont()), stop.getText(), stop.getIcon(),
                    stop.getVerticalAlignment(), stop.getHorizontalAlignment(), stop.getVerticalTextPosition(),
                    stop.getHorizontalTextPosition(), view, icon, text, stop.getIconTextGap());

                int back = drawn.getRGB(icon.x, icon.y);

                // The triangle: rows and columns where the icon's box differs from the button behind it by half or more
                int[] box = inkBox(drawn, icon, rgb -> distance(rgb, back) > 128);

                // The mark: near-black inside the triangle
                int[] mark = inkBox(drawn, icon, rgb -> ((rgb >> 16) & 255) < 100 && ((rgb >> 8) & 255) < 100);

                // The text's ink, caps top to baseline
                int[] words = inkBox(drawn, text, rgb -> distance(rgb, back) > 128);

                double middle = (box[0] + box[2]) / 2.0;

                assertEquals((mark[0] + mark[2]) / 2.0, middle, 0.01, "the mark is not on the triangle's middle: mark"
                    + " columns " + mark[0] + ".." + mark[2] + ", triangle " + box[0] + ".." + box[2]);

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

                double triangle = (box[1] + box[3]) / 2.0;
                double line = (words[1] + words[3]) / 2.0;

                assertEquals(triangle, line, 1.0, "the triangle (rows " + box[1] + ".." + box[3] + ") is not centered on"
                    + " the text line (rows " + words[1] + ".." + words[3] + ")");

                return null;
            });
        }
        finally
        {
            Components.onTheEventThread(() -> { stop.setIcon(was); return null; });
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
