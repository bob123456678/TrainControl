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
}
