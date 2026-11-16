import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.TrainControlUI;
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

    /**
     * The track diagram the station reads instead of its own, through the setting a user picks it with: a switch and a
     * signal at 45, a three-way at 47 and 48 with a signal at 48, and a signal alone at 46.
     */
    private static File layoutFolder;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        layoutFolder = Files.createTempDirectory("tc-following").toFile();
        File pages = new File(layoutFolder, "config/gleisbilder");
        assertTrue(pages.mkdirs(), "the fixture could not make " + pages);

        write(new File(layoutFolder, "config/gleisbild.cs2"), "[gleisbild]", "version", " .major=1",
            "seite", " .id=1", " .name=Main");

        // A page's .artikel is twice the address the tile shows
        write(new File(pages, "Main.cs2"), "[gleisbildseite]", "version", " .major=1",
            "element", " .id=0x10001", " .typ=linksweiche", " .artikel=90",
            "element", " .id=0x10002", " .typ=signal", " .artikel=90",
            "element", " .id=0x10003", " .typ=dreiwegweiche", " .artikel=94",
            "element", " .id=0x10004", " .typ=signal", " .artikel=96",
            "element", " .id=0x10005", " .typ=signal", " .artikel=92");

        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF,
            layoutFolder.getAbsolutePath().replace(File.separatorChar, '/'));

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

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        TrainControlUI.getPrefs().remove(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF);

        for (String name : new String[]{ "config/gleisbilder/Main.cs2", "config/gleisbilder", "config/gleisbild.cs2",
            "config", "" })
        {
            Files.deleteIfExists(new File(layoutFolder, name).toPath());
        }
    }

    private static void write(File file, String... lines) throws Exception
    {
        Files.write(file.toPath(), String.join("\n", lines).concat("\n").getBytes(StandardCharsets.UTF_8));
    }

    /** The tile of the given kind at the given address on the diagram page. */
    private static LayoutDiagramComponent tile(boolean signal, int address)
    {
        assertNotNull(model.getLayout("Main"), "the diagram page did not load");

        for (LayoutDiagramComponent c : model.getLayout("Main").getAll())
        {
            if (c.getAddress() == address && (signal ? c.isSignal() : c.isSwitch())) return c;
        }

        throw new AssertionError("no " + (signal ? "signal" : "switch") + " tile at " + address);
    }

    /** The accessory the station's echoes reach, by logical address. */
    private static Accessory held(int address)
    {
        return model.getAccessoryByAddressIfPresent(address, Accessory.accessoryDecoderType.MM2);
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

    /**
     * A switch and a signal on one address share the one accessory the station's echoes reach, and keep sharing it at
     * the next read of the diagram (OB-464).
     *
     * CAN does not care whether an accessory is a switch or a signal.  Yet the diagram read re-created the accessory
     * for each tile of the other kind, so the tile read first kept an object no echo reaches: a route turned the real
     * signal green while the diagram stayed red.  A three-way's second address shared with a signal is the same case.
     */
    @Test
    public void testASwitchAndASignalOnOneAddressShareOneAccessory() throws Exception
    {
        for (int read = 1; read <= 2; read++)
        {
            if (read == 2) model.refreshLayouts();

            assertSame(tile(false, 45).getAccessory(), held(45),
                "read " + read + ": the switch at 45 holds an accessory the station's echoes never reach");
            assertSame(tile(true, 45).getAccessory(), held(45),
                "read " + read + ": the signal at 45 holds an accessory the station's echoes never reach");

            assertSame(tile(false, 47).getAccessory2(), held(48),
                "read " + read + ": the three-way's second address holds an accessory the station's echoes never reach");
            assertSame(tile(true, 48).getAccessory(), held(48),
                "read " + read + ": the signal at 48 holds an accessory the station's echoes never reach");
        }
    }

    /**
     * At an address with tiles of one kind only, a kind changed on the station is still taken: the database's switch
     * becomes the signal the diagram shows.
     */
    @Test
    public void testAKindChangeAtASingleKindAddressIsStillTaken() throws Exception
    {
        model.newSwitch(46, Accessory.accessoryDecoderType.MM2, false);
        assertTrue(held(46).isSwitch(), "precondition: the database holds a switch at 46");

        model.refreshLayouts();

        assertTrue(held(46).isSignal(), "the diagram's signal at 46 must replace the database's switch");
        assertSame(tile(true, 46).getAccessory(), held(46), "and the tile must hold it");
    }
}
