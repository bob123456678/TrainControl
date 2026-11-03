package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RemoteDeviceCollection;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.Util;
import support.StandInStation;

/**
 * A whole sync with a CS2 does exactly what it did before the CS3 probe's changes: the requests, every accessory and
 * whether the database and the tiles hold the same object, every tile's wiring, every page as a save writes it, the
 * locomotives, the routes and the log.
 *
 * A stand-in CS2 serves railways built here from `test/layout`: as it is (synced twice); with a switch and a signal on
 * one address, on one page and on two (twice); its local copy; and with one accessory id given twice, MM2 then DCC, as
 * only a CS3 writes it (twice); and with the signal at 97 turned into a turnout, as a station changes an article's kind
 * (once).  The model starts from an empty data folder, so nothing of the run's database enters
 * it.  The golden, `test/baseline/cs2_sync_golden.txt`, was written by this class run on the code before those changes
 * (dev at 0d2d9a24): a mismatch writes what this run saw to `build/cs2_sync_actual.txt`, which is how it was made.
 *
 * One change is on purpose and listed line by line in `test/baseline/cs2_sync_golden_changes.tsv`: at an address with
 * a switch tile and a signal tile (54 and 68 in the railway with both), every tile holds the database's accessory, which
 * is no longer re-created for each tile of the other kind.  So those tiles are live, 68 keeps the switch the first
 * railway made rather than becoming a signal, and the "Adding" lines of the re-creations are gone.
 *
 * Since every accessory tile writes its protocol, a save may also add ` .prot=MM2` to an addressed switch, signal or
 * uncoupler that read as MM2, and only there (support.SavedProtocol).
 *
 * MUTATION: find the addresses with two protocols whatever the station (drop `control.isCS3()` from
 * CS2File.parseLayout), and railway D's page writes `.prot=MM2`.  Or count every address as mixed
 * (`this.mixedAddresses = kinds.keySet()`), and 97 is not re-created as a switch.
 *
 * @author Adam
 */
public class testACS2SyncIsAsBefore
{
    private static final String GOLDEN = "test/baseline/cs2_sync_golden.txt";

    // The lines the branch changes on purpose, each named with the golden's line; see withTheChanges
    private static final String CHANGES = "test/baseline/cs2_sync_golden_changes.tsv";

    private static final byte[] CS2_DEVICE = "[geraet]\ngeraet\n .produkt=Central Station 2\n"
        .getBytes(StandardCharsets.UTF_8);

    // The railway the stand-in serves now
    private static volatile File served;

    @Test
    public void testASyncWithACS2IsAsBefore() throws Exception
    {
        String actual = support.SavedProtocol.inSyncDump(String.join("\n", syncAll()) + "\n");
        String golden = withTheChanges(!new File(GOLDEN).isFile() ? ""
            : new String(Files.readAllBytes(new File(GOLDEN).toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n"));

        if (!actual.equals(golden))
        {
            File out = new File("build/cs2_sync_actual.txt");
            out.getParentFile().mkdirs();
            Files.write(out.toPath(), actual.getBytes(StandardCharsets.UTF_8));

            String[] a = actual.split("\n", -1);
            String[] g = golden.split("\n", -1);
            int i = 0;

            while (i < Math.min(a.length, g.length) && a[i].equals(g[i]))
            {
                i++;
            }

            fail("differs from " + GOLDEN + " with " + CHANGES + " at line " + (i + 1) + " (written to " + out
                + "):\n  golden: " + (i < g.length ? g[i] : "(end)") + "\n  now:    " + (i < a.length ? a[i] : "(end)"));
        }
    }

    /**
     * The golden with the changes the branch makes on purpose: each line of CHANGES is a golden line number, the line
     * as dev wrote it and the line now ("(gone)" when there is none), tab-separated.  A changed golden fails here.
     */
    private static String withTheChanges(String golden) throws Exception
    {
        if (!new File(CHANGES).isFile())
        {
            return golden;
        }

        List<String> lines = new ArrayList<>(java.util.Arrays.asList(golden.split("\n", -1)));

        for (String change : Files.readAllLines(new File(CHANGES).toPath(), StandardCharsets.UTF_8))
        {
            String[] f = change.split("\t");
            assertEquals(f.length, 3, CHANGES + " row is not line, was and now: " + change);

            int at = Integer.parseInt(f[0]) - 1;
            assertEquals(lines.get(at), f[1], CHANGES + " names golden line " + f[0] + " as it was");
            lines.set(at, f[2].equals("(gone)") ? null : f[2]);
        }

        lines.removeIf(java.util.Objects::isNull);
        return String.join("\n", lines);
    }

    /** Every run's dump, with the stand-in's address, the folders and timings taken out. */
    private static List<String> syncAll() throws Exception
    {
        Path root = Files.createTempDirectory("tc-cs2-sync");
        File a = railway(root, "A");
        File c = railway(root, "C");
        File d = railway(root, "D");
        append(new File(c, "config/gleisbilder/test.cs2"), "element\n .id=0x10a0a\n .typ=signal\n .artikel=108\n");
        append(new File(c, "config/gleisbild.cs2"), "seite\n .id=2\n .name=two\n");
        append(new File(c, "config/gleisbilder/two.cs2"), "[gleisbildseite]\nversion\n .major=1\npage=2\nelement\n"
            + " .id=0x20101\n .typ=signal\n .artikel=136\nelement\n .id=0x20102\n .typ=linksweiche\n .artikel=108\n");
        append(new File(d, "config/magnetartikel.cs2"), "artikel\n .id=54\n .name=TWIN DCC\n .typ=rechtsweiche\n"
            + " .dectyp=dcc\n");
        File local = railway(root, "local");

        // The station turned the signal at 97 into a turnout: an address of one kind, re-created with its new kind
        File e = railway(root, "E");
        File page = new File(e, "config/gleisbilder/test.cs2");
        String signal97 = " .id=0x10306\n .typ=signal\n";
        assertTrue(read(page).contains(signal97), "test/layout has the signal at 97");
        Files.write(page.toPath(), read(page).replace(signal97, " .id=0x10306\n .typ=linksweiche\n")
            .getBytes(StandardCharsets.UTF_8));

        List<String> out = new ArrayList<>();
        // Written by whichever thread logs, so read under its lock
        List<String> logged = java.util.Collections.synchronizedList(new ArrayList<>());
        Handler tap = support.LogTap.messagesInto(logged);
        String dataDir = System.getProperty(Util.DATA_DIR_PROPERTY);
        support.LayoutSandbox sandbox = null;
        StandInStation station = null;
        MarklinControlStation model = null;

        try
        {
            sandbox = support.LayoutSandbox.open(new File(root.toFile(), "none"));
            station = new StandInStation((path, n) -> path.equals("/config/geraet.vrs") ? CS2_DEVICE
                : StandInStation.file(served.getPath() + path));
            System.setProperty(Util.DATA_DIR_PROPERTY, Files.createDirectory(root.resolve("data")).toString());
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
            model = support.TestStation.simulated();
            Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);

            for (File railway : new File[] {a, a, c, c, local, d, d, e})
            {
                served = railway == local ? a : railway;
                station.forget();
                logged.clear();
                TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF,
                    railway == local ? local.getAbsolutePath() : "");
                model.clearLayouts();

                String was = support.TestStationAddress.get();
                int result;

                try
                {
                    support.TestStationAddress.set(station.address());
                    result = model.syncWithCS2();
                }
                finally
                {
                    support.TestStationAddress.set(was);
                }

                out.add("######## " + railway.getName() + " result=" + result + " cs3=" + model.isCS3() + " error="
                    + model.getLastSyncError());
                out.add("requests " + station.asked());
                dump(model, out);
                out.add("-- log");

                List<String> lines;

                synchronized (logged)
                {
                    lines = new ArrayList<>(logged);
                }

                for (String line : lines)
                {
                    // The listener's start is logged by its own thread, in no fixed order with the sync's lines
                    if (line != null && !line.matches("(?s).*\\d+ ?ms\\b.*") && !line.equals("CAN listener running."))
                    {
                        out.add("  " + line);
                    }
                }
            }
        }
        finally
        {
            Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(tap);

            // Stopped while the data folder is still this class's, so whatever it saves stays there
            StandInStation.tearDown(station, model, sandbox);

            if (dataDir == null)
            {
                System.clearProperty(Util.DATA_DIR_PROPERTY);
            }
            else
            {
                System.setProperty(Util.DATA_DIR_PROPERTY, dataDir);
            }
        }

        List<String> clean = new ArrayList<>();

        for (String line : out)
        {
            clean.add(line.replace(station.address(), "STATION").replace(root.toFile().getAbsolutePath(), "ROOT")
                .replace(root.toFile().getAbsolutePath().replace('\\', '/'), "ROOT"));
        }

        return clean;
    }

    /** test/layout with the CS2 locomotive and route files, as a station's folder. */
    private static File railway(Path root, String name) throws Exception
    {
        File to = new File(root.toFile(), name);
        File config = new File(to, "config");
        new File(config, "gleisbilder").mkdirs();

        for (String file : new String[] {"gleisbild.cs2", "magnetartikel.cs2", "gleisbilder/test.cs2"})
        {
            append(new File(config, file), read(new File("test/layout/config/" + file)));
        }

        append(new File(config, "lokomotive.cs2"), read(new File("test/lokomotive.cs2")));
        append(new File(config, "fahrstrassen.cs2"), read(new File("test/fahrstrassen.cs2")));
        return to;
    }

    private static String read(File f) throws Exception
    {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r", "");
    }

    /** Adds lines to a file, on a line of their own. */
    private static void append(File f, String text) throws Exception
    {
        String was = f.isFile() ? read(f) : "";
        String joined = was.isEmpty() || was.endsWith("\n") ? was + text : was + "\n" + text;
        Files.write(f.toPath(), joined.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static void dump(MarklinControlStation model, List<String> out) throws Exception
    {
        java.lang.reflect.Field f = MarklinControlStation.class.getDeclaredField("accDB");
        f.setAccessible(true);
        RemoteDeviceCollection<MarklinAccessory, Integer> accDB =
            (RemoteDeviceCollection<MarklinAccessory, Integer>) f.get(model);

        List<String> accs = new ArrayList<>();

        for (MarklinAccessory a : accDB.getItems())
        {
            accs.add("  acc uid=" + Integer.toHexString(a.getUID()) + " name=" + a.getName() + " " + a.getType() + " "
                + a.getDecoderType() + " switched=" + a.isSwitched() + " n=" + a.getNumActuations() + " byId="
                + (accDB.getById(a.getUID()) == a) + " byName=" + (accDB.getByName(a.getName()) == a));
        }

        Collections.sort(accs);
        out.add("-- accessories " + accs.size());
        out.addAll(accs);
        out.add("-- pages " + model.getLayoutList());

        for (String name : model.getLayoutList())
        {
            LayoutDiagram d = model.getLayout(name);
            out.add("page " + name + " id=" + d.getPageId() + " unreadable=" + d.isUnreadable());

            for (LayoutDiagramComponent c : d.getAll())
            {
                out.add("  (" + c.getX() + "," + c.getY() + ") " + c.getType() + " addr=" + c.getAddress() + " prot="
                    + c.getProtocol() + wiring(accDB, (MarklinAccessory) c.getAccessory(), " acc=")
                    + wiring(accDB, (MarklinAccessory) c.getAccessory2(), " acc2="));
            }

            out.add("  saved as:");

            for (String line : d.exportToCS2TextFormat().replace("\r", "").split("\n"))
            {
                out.add("    " + line);
            }
        }

        List<String> locs = new ArrayList<>();

        for (Locomotive l : model.getLocomotives())
        {
            MarklinLocomotive m = (MarklinLocomotive) l;
            locs.add("  loc " + m.getName() + " addr=" + m.getAddress() + " " + m.getDecoderType() + " uid=" + m.getUID()
                + " image=" + m.getImageURL() + " f=" + m.getNumF());
        }

        Collections.sort(locs);
        out.add("-- locomotives " + locs.size());
        out.addAll(locs);

        List<String> routes = new ArrayList<>();

        for (MarklinRoute r : model.getRoutes())
        {
            routes.add("  route " + r.toString().replace("\n", " | ") + " | " + r.getRoute());
        }

        Collections.sort(routes);
        out.add("-- routes " + routes.size());
        out.addAll(routes);
    }

    /** The accessory a tile holds, and whether it is the database's own object. */
    private static String wiring(RemoteDeviceCollection<MarklinAccessory, Integer> accDB, MarklinAccessory a, String key)
    {
        return a == null ? "" : key + a.getName() + "/" + a.getType() + "/" + Integer.toHexString(a.getUID()) + " live="
            + (accDB.getById(a.getUID()) == a);
    }
}
