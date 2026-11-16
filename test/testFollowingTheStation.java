import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
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

    private static StationAt network;

    /**
     * The station's address, which a claim can point at a stand-in web server on this machine; until then the
     * loopback address, where no station answers.
     */
    private static class StationAt extends NetworkProxy
    {
        volatile String ip;

        StationAt() throws IOException
        {
            super(InetAddress.getLoopbackAddress());
        }

        @Override
        public String getIP()
        {
            return this.ip != null ? this.ip : super.getIP();
        }
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // It writes the real preference node, which only docs/tools/one.sh replaces with a node of its own
        if (System.getProperty(org.traincontrol.util.Util.PREFERENCES_PROPERTY, "").trim().isEmpty())
            throw new org.testng.SkipException("needs docs/tools/one.sh");

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
        network = new StationAt();
        model = new MarklinControlStation(network, view, false, false);
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
     * A sync does not re-address a locomotive that has a speed; the next sync after it stops does (XFF1-C7).
     *
     * If a locomotive's address was changed at the Central Station while it was driven here, a sync moved it to the
     * new address, so the train ran on at the old one while Stop here commanded the new one.  The station is a
     * stand-in web server on this machine serving the locomotive list; no frame is sent.
     */
    @Test
    public void testASyncDoesNotReAddressALocomotiveThatIsMoving() throws Exception
    {
        Map<String, String> files = new HashMap<>();
        files.put("/config/fahrstrassen.cs2", "[fahrstrassen]\nversion\n .minor=4\n");
        files.put("/config/magnetartikel.cs2", "[magnetartikel]\nversion\n .minor=1\n");
        files.put("/config/lokomotive.cs2", "[lokomotive]\nversion\n .minor=3\nlokomotive\n .name=Follow moving\n"
            + " .uid=0x14\n .adresse=0x14\n .typ=mm2_dil8\n");

        HttpServer server = serve(files, null);

        try
        {
            network.ip = "127.0.0.1:" + server.getAddress().getPort();

            MarklinLocomotive loc = model.newMM2Locomotive("Follow moving", 10);
            loc.setSpeed(40);

            assertTrue(model.syncWithCS2() >= 0, "the sync against the stand-in station failed; see the log");
            assertEquals(model.getLocByName("Follow moving").getAddress(), 10,
                "a sync moved a locomotive at speed to the station's new address: Stop would now command another decoder");

            loc.setSpeed(0);

            assertTrue(model.syncWithCS2() >= 0, "the second sync against the stand-in station failed; see the log");
            assertEquals(model.getLocByName("Follow moving").getAddress(), 20,
                "once the locomotive has stopped, the next sync takes the station's address");
        }
        finally
        {
            network.ip = null;
            server.stop(0);
        }
    }

    /**
     * A stand-in station on this machine serving the given files.  A request for cutPath gets the file's whole length in
     * its headers but less of its body each time, as a connection that keeps dropping does.
     */
    private static HttpServer serve(Map<String, String> files, String cutPath) throws IOException
    {
        AtomicInteger cuts = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange ->
        {
            String path = exchange.getRequestURI().getPath();
            String body = files.get(path);

            if (body == null)
            {
                exchange.sendResponseHeaders(404, -1);
            }
            else
            {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);

                OutputStream out = exchange.getResponseBody();
                out.write(bytes, 0, path.equals(cutPath) ? bytes.length / 2 - 5 * cuts.getAndIncrement() : bytes.length);
                out.flush();
            }

            exchange.close();
        });
        server.start();

        return server;
    }

    /**
     * An accessory file that ends short every time fails the sync before the diagram is read, so no tile is built from
     * part of the list (A8, 8d48bf40).
     *
     * 2.8.2 read the part that arrived as the whole list, so a DCC accessory past the cut was built as MM2 and the
     * diagram's tile commanded a decoder that is not there.  A length check alone would have made it worse: the failed
     * list read as none, and every tile built as MM2.
     */
    @Test
    public void testAnAccessoryFileThatEndsShortFailsTheSyncAndChangesNothing() throws Exception
    {
        Map<String, String> files = new HashMap<>();
        files.put("/config/fahrstrassen.cs2", "[fahrstrassen]\nversion\n .minor=4\n");
        files.put("/config/lokomotive.cs2", "[lokomotive]\nversion\n .minor=3\n");
        files.put("/config/gleisbild.cs2", "[gleisbild]\nversion\n .major=1\nseite\n .id=1\n .name=Station\n");
        files.put("/config/gleisbilder/Station.cs2", "[gleisbildseite]\nversion\n .major=1\n"
            + "element\n .id=0x10001\n .typ=linksweiche\n .artikel=120\n");

        StringBuilder mags = new StringBuilder("[magnetartikel]\nversion\n .minor=1\n");

        for (int id = 1; id <= 6; id++)
        {
            mags.append("artikel\n .id=").append(id).append("\n .name=").append(id).append("\n .typ=linksweiche\n .dectyp=mm2\n");
        }

        files.put("/config/magnetartikel.cs2", mags.append("artikel\n .id=60\n .name=60\n .typ=linksweiche\n .dectyp=dcc\n").toString());

        String override = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
        TrainControlUI.getPrefs().remove(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF);
        HttpServer whole = serve(files, null);

        try
        {
            network.ip = "127.0.0.1:" + whole.getAddress().getPort();
            model.clearLayouts();

            assertTrue(model.syncWithCS2() >= 0, "the sync of the whole files failed; see the log");
            Accessory dcc = model.getAccessoryByAddressIfPresent(60, Accessory.accessoryDecoderType.DCC);
            assertNotNull(dcc, "precondition: the whole accessory file makes the tile at 60 DCC");
            whole.stop(0);

            HttpServer cut = serve(files, "/config/magnetartikel.cs2");
            network.ip = "127.0.0.1:" + cut.getAddress().getPort();
            model.clearLayouts();

            try
            {
                assertEquals(model.syncWithCS2(), -1, "a sync whose accessory file ended short every time did not fail");
            }
            finally
            {
                cut.stop(0);
            }

            assertNull(model.getAccessoryByAddressIfPresent(60, Accessory.accessoryDecoderType.MM2),
                "the DCC tile at 60 was built as MM2 from part of the accessory file");
            assertSame(model.getAccessoryByAddressIfPresent(60, Accessory.accessoryDecoderType.DCC), dcc,
                "the DCC accessory at 60 changed");
            assertNull(model.getLayout("Station"), "the diagram was read from part of the accessory file");
        }
        finally
        {
            whole.stop(0);
            network.ip = null;
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, override);
            model.refreshLayouts();
        }
    }

    /** A locomotive file that ends short every time fails the sync, and no locomotive is taken from the part (A8). */
    @Test
    public void testALocomotiveFileThatEndsShortFailsTheSyncAndChangesNothing() throws Exception
    {
        Map<String, String> files = new HashMap<>();
        files.put("/config/fahrstrassen.cs2", "[fahrstrassen]\nversion\n .minor=4\n");
        files.put("/config/magnetartikel.cs2", "[magnetartikel]\nversion\n .minor=1\n");
        files.put("/config/lokomotive.cs2", "[lokomotive]\nversion\n .minor=3\nlokomotive\n .name=Cut A\n"
            + " .uid=0x15\n .adresse=0x15\n .typ=mm2_dil8\nlokomotive\n .name=Cut B with a long name to cut\n"
            + " .uid=0x16\n .adresse=0x16\n .typ=mm2_dil8\n .symbol=1\n .icon=a long icon name for the cut\n");

        HttpServer server = serve(files, "/config/lokomotive.cs2");

        try
        {
            network.ip = "127.0.0.1:" + server.getAddress().getPort();

            assertEquals(model.syncWithCS2(), -1, "a sync whose locomotive file ended short every time did not fail");
            assertNull(model.getLocByName("Cut A"), "a locomotive was taken from part of the locomotive file");
        }
        finally
        {
            network.ip = null;
            server.stop(0);
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
