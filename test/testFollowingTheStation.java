import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.marklin.udp.NetworkProxy;
import org.traincontrol.model.View;

/**
 * What the Central Station tells TrainControl, and whether the window follows it.
 *
 * The station here is built with a recording window, which notes the warnings the window would show, so the claims
 * read what the user would see.  Frames are handed to receiveMessage directly, as the CAN listener would.
 */
public class testFollowingTheStation
{
    private static MarklinControlStation model;

    /** The outputs of the power cuts the window was told to warn about, in order. */
    private static final List<Integer> powerCuts = new CopyOnWriteArrayList<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        View view = (View) Proxy.newProxyInstance(View.class.getClassLoader(), new Class<?>[]{ View.class },
            (proxy, method, args) ->
            {
                switch (method.getName())
                {
                    case "showPowerCut": powerCuts.add((Integer) args[0]); return null;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "recording window";
                    case "getAllLocButtonMappings": return new ArrayList<String>();
                    default: return null;
                }
            });

        // No station answers at this address, so the station starts not connected and sends nothing
        model = new MarklinControlStation(new NetworkProxy(InetAddress.getLoopbackAddress()), view, false, false);
        model.setNetworkCommState(false);
    }

    /** A system frame with the given sub command and, for an overload, the output that tripped. */
    private static CS2Message system(int subCommand, int... channel)
    {
        byte[] data = new byte[5 + channel.length];
        data[4] = (byte) subCommand;

        if (channel.length > 0) data[5] = (byte) channel[0];

        return new CS2Message(CS2Message.CMD_SYSTEM, 0x4711, true, data);
    }

    /** Hands a frame to the station and waits until the thread that handles power frames has done with it. */
    private static void receive(CS2Message frame) throws Exception
    {
        model.receiveMessage(frame);

        Field f = MarklinControlStation.class.getDeclaredField("locMessageProcessor");
        f.setAccessible(true);
        ((ExecutorService) f.get(model)).submit(() -> { }).get(5, TimeUnit.SECONDS);
    }

    /**
     * A short circuit at the station (System Overload) shows the power off and warns, once per cut.
     *
     * A CS3 that trips on a short cuts the track power and says only Overload, never Stop.  TrainControl read only Go
     * and Stop, so the power stayed on in the window and autonomy kept sending trains to a dead track (OB-426).
     */
    @Test
    public void testAShortCircuitShowsThePowerOffAndWarnsOncePerCut() throws Exception
    {
        receive(system(CS2Message.CMD_SYSSUB_GO));
        powerCuts.clear();

        receive(system(CS2Message.CMD_SYSSUB_OVERLOAD, 2));

        assertFalse(model.getPowerState(), "after a short circuit the power must show as off");
        assertEquals(powerCuts, Arrays.asList(2), "a short circuit must warn once, naming the output that tripped");

        receive(system(CS2Message.CMD_SYSSUB_OVERLOAD, 2));

        assertEquals(powerCuts, Arrays.asList(2), "the station repeats Overload while the short lasts: one warning per cut");

        receive(system(CS2Message.CMD_SYSSUB_GO));
        assertTrue(model.getPowerState(), "Go turns the power back on");

        receive(system(CS2Message.CMD_SYSSUB_OVERLOAD, 3));

        assertEquals(powerCuts, Arrays.asList(2, 3), "a new cut after Go warns again");
    }

    /**
     * A Stop never warns, and an Overload after it still does: a station that sends Stop before Overload must not
     * lose the warning (755a0e96).
     */
    @Test
    public void testAStopNeverWarnsAndAnOverloadAfterItDoes() throws Exception
    {
        receive(system(CS2Message.CMD_SYSSUB_GO));
        powerCuts.clear();

        receive(system(CS2Message.CMD_SYSSUB_STOP));

        assertFalse(model.getPowerState(), "Stop shows the power off");
        assertTrue(powerCuts.isEmpty(), "a plain Stop is not a short circuit and must not warn");

        receive(system(CS2Message.CMD_SYSSUB_OVERLOAD, 1));

        assertEquals(powerCuts, Arrays.asList(1), "an Overload after a Stop is still a short circuit, and must warn");
    }
}
