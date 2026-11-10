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
     * The warning triangle shows left of Power OFF, with a tooltip saying why, while the connection is being replaced,
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
     * The triangle beside Power OFF is drawn true at 1x (Adam: "make sure the exclamation point is aligned"): the mark is
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
        JLabel shown = WindowField.of(ui, "stationNotAnswering");

        notAnswering(true);

        try
        {
            Components.onTheEventThread(() ->
            {
                java.awt.Container row = stop.getParent().getParent();

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
                java.awt.Rectangle text = new java.awt.Rectangle(), none = new java.awt.Rectangle();
                java.awt.Insets in = stop.getInsets();
                java.awt.Rectangle view = new java.awt.Rectangle(in.left, in.top, stop.getWidth() - in.left - in.right,
                    stop.getHeight() - in.top - in.bottom);

                SwingUtilities.layoutCompoundLabel(stop, stop.getFontMetrics(stop.getFont()), stop.getText(), null,
                    stop.getVerticalAlignment(), stop.getHorizontalAlignment(), stop.getVerticalTextPosition(),
                    stop.getHorizontalTextPosition(), view, none, text, stop.getIconTextGap());

                text = SwingUtilities.convertRectangle(stop, text, row);

                int back = drawn.getRGB(icon.x, icon.y);

                // The triangle: rows and columns where the icon's box differs from the button behind it by half or more
                int[] box = inkBox(drawn, icon, rgb -> distance(rgb, back) > 128);

                // The mark: near-black inside the triangle
                int[] mark = inkBox(drawn, icon, rgb -> ((rgb >> 16) & 255) < 100 && ((rgb >> 8) & 255) < 100);

                // The text's ink, caps top to baseline
                int[] words = inkBox(drawn, text, rgb -> distance(rgb, back) > 128);

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

                double triangle = (box[1] + box[3]) / 2.0;
                double line = (words[1] + words[3]) / 2.0;

                assertEquals(triangle, line, 1.0, "the triangle (rows " + box[1] + ".." + box[3] + ") is not centered on"
                    + " the text line (rows " + words[1] + ".." + words[3] + ")");

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

    /** {left, right} of a part, in the coordinates of the row Power OFF stands in. */
    private static int[] across(java.awt.Component part, java.awt.Component row)
    {
        return Components.onTheEventThread(() ->
        {
            java.awt.Rectangle at = SwingUtilities.convertRectangle(part.getParent(), part.getBounds(), row);

            return new int[] {at.x, at.x + at.width};
        });
    }

    /**
     * The triangle stands left of Power OFF, not in it (Adam: "put it to the left of the button, while ensuring the button
     * rescales dynamically in width when the triangle is/isn't there"): shown, it takes its room from the button, so the
     * two end where the button alone did and Power ON does not move; gone, the button has its whole width back.  Power
     * OFF's text still fits the narrower button in every language.
     *
     * MUTATION: have `warnOnStop` set the triangle as Power OFF's icon again, or leave the shared panel's width to the
     * triangle plus the button's own, and this fails.
     */
    @Test
    public void testTheTriangleTakesItsRoomFromTheButton() throws Exception
    {
        JButton stop = WindowField.of(ui, "PowerOff");
        JButton on = WindowField.of(ui, "OnButton");
        java.awt.Container row = Components.onTheEventThread(on::getParent);

        notAnswering(false);

        int[] alone = across(stop, row);
        int[] onAt = across(on, row);

        try
        {
            notAnswering(true);

            assertNull(Components.onTheEventThread(stop::getIcon), "the triangle is still inside Power OFF");

            JLabel triangle = WindowField.of(ui, "stationNotAnswering");

            assertTrue(Components.onTheEventThread(triangle::isVisible), "no triangle while the station is not answering");

            int[] mark = across(triangle, row);
            int[] narrower = across(stop, row);

            assertEquals(mark[0], alone[0], "the triangle does not start where Power OFF did");
            assertTrue(mark[1] <= narrower[0], "the triangle is not left of Power OFF");
            assertEquals(narrower[1], alone[1], "Power OFF's right edge moved");
            assertEquals(across(on, row)[0], onAt[0], "Power ON moved");

            for (String language : new String[] {"en", "da", "de", "es", "fr", "it", "nl", "pl"})
            {
                String text = java.util.ResourceBundle.getBundle("org.traincontrol.resources.messages",
                    new java.util.Locale(language)).getString("ui.main.powerOFF");

                int needs = Components.onTheEventThread(() -> stop.getFontMetrics(stop.getFont()).stringWidth(text)
                    + stop.getInsets().left + stop.getInsets().right);

                assertTrue(needs <= narrower[1] - narrower[0], language + ": \"" + text + "\" needs " + needs + " and the"
                    + " narrower Power OFF is " + (narrower[1] - narrower[0]));
            }
        }
        finally
        {
            notAnswering(false);
        }

        assertEquals(java.util.Arrays.toString(across(stop, row)), java.util.Arrays.toString(alone),
            "Power OFF did not get its whole width back");
        assertEquals(across(on, row)[0], onAt[0], "Power ON moved once the triangle went");
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
