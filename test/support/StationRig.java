package support;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Handler;
import java.util.logging.Logger;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.Util;

/**
 * A model syncing with a stand-in station, set up and taken down in one place: the station answers as the function
 * says, the model starts from an empty data folder and a sandboxed layout folder, and the log is kept.  The station
 * address is the stand-in's until the rig is closed.
 *
 * @author Adam
 */
public final class StationRig implements AutoCloseable
{
    public final StandInStation station;

    public final MarklinControlStation model;

    private final LayoutSandbox sandbox;

    private final String dataDir = System.getProperty(Util.DATA_DIR_PROPERTY);

    private final String addressWas = TestStationAddress.get();

    private final List<String> logged = Collections.synchronizedList(new ArrayList<>());

    private final Handler tap = LogTap.messagesInto(logged);

    /**
     * @param root a folder of the test's own, to hold the data folder
     * @param answer the stand-in's answers, as StandInStation takes them
     */
    public StationRig(File root, BiFunction<String, Integer, byte[]> answer) throws Exception
    {
        this(root, answer, false);
    }

    /** The same, with the application's own window when `window` is true (it needs a display). */
    public StationRig(File root, BiFunction<String, Integer, byte[]> answer, boolean window) throws Exception
    {
        this(root, answer, window, false);
    }

    /** The same; with `atStartUp` true the start-up's own sync goes to the stand-in too, as an application's does. */
    public StationRig(File root, BiFunction<String, Integer, byte[]> answer, boolean window, boolean atStartUp)
        throws Exception
    {
        MarklinControlStation started = null;
        LayoutSandbox opened = null;
        StandInStation standIn = null;

        try
        {
            standIn = new StandInStation(answer);
            System.setProperty(Util.DATA_DIR_PROPERTY, Files.createDirectories(new File(root, "data").toPath()).toString());
            opened = LayoutSandbox.open(new File(root, "none"));
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
            if (atStartUp) TestStationAddress.set(standIn.address());

            started = window ? TestStation.simulatedWithWindow() : TestStation.simulated();
        }
        catch (Exception | Error e)
        {
            this.station = standIn;
            this.model = started;
            this.sandbox = opened;
            close();
            throw e;
        }

        this.station = standIn;
        this.model = started;
        this.sandbox = opened;
        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(this.tap);
        TestStationAddress.set(standIn.address());
    }

    /** Points the layout preference at a folder ("" for the station's own). */
    public static void layoutFolder(String path)
    {
        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, path);
    }

    /**
     * The requests of the station's everyday work, each step with what it gave back and the requests it sent: a sync,
     * a resync, the check for renamed locomotives, the layout download, a sync from an emptied railway, then the sync,
     * the check and the download again with the layout read from a local folder.
     *
     * @param scratch a folder for the downloads
     * @param local a copy of the station's layout files to read the layout from
     */
    public String scenario(File scratch, File local) throws Exception
    {
        StringBuilder out = new StringBuilder();

        out.append(sync("sync")).append(sync("resync")).append(renames("rename proposals"));
        out.append(this.station.recorded("download", () -> download(new File(scratch, "first"))));
        out.append(this.station.recorded("sync from an emptied railway", () ->
        {
            this.model.clearLayouts();
            return this.model.syncWithCS2();
        }));

        layoutFolder(local.getAbsolutePath());
        out.append(sync("sync with a local folder")).append(renames("rename proposals with a local folder"));
        out.append(this.station.recorded("download with a local folder", () -> download(new File(scratch, "second"))));
        return out.toString();
    }

    private String sync(String title) throws Exception
    {
        return this.station.recorded(title, () -> this.model.syncWithCS2());
    }

    private String renames(String title) throws Exception
    {
        return this.station.recorded(title, () -> this.model.getRenameProposals().getProposals().size());
    }

    /** The names of the files a download wrote into the folder's config, sorted. */
    private Object download(File to) throws Exception
    {
        this.model.downloadLayout(to);
        String[] names = new File(to, "config").list();
        java.util.Arrays.sort(names);
        return java.util.Arrays.toString(names);
    }

    /** What the model logged since the rig opened or the last call, less the lines that vary between runs. */
    public List<String> takeLog()
    {
        List<String> out = new ArrayList<>();

        synchronized (this.logged)
        {
            for (String line : this.logged)
            {
                // Timings vary, and the listener's start is logged by its own thread, in no fixed order with the sync
                if (line != null && !line.matches("(?s).*\\d+ ?ms\\b.*") && !line.startsWith("CAN listener running"))
                {
                    out.add(line);
                }
            }

            this.logged.clear();
        }

        return out;
    }

    @Override
    public void close()
    {
        Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(this.tap);
        TestStationAddress.set(this.addressWas);

        // Stopped while the data folder is still this rig's, so whatever the model saves stays there
        StandInStation.tearDown(this.station, this.model, this.sandbox);

        if (this.dataDir == null)
        {
            System.clearProperty(Util.DATA_DIR_PROPERTY);
        }
        else
        {
            System.setProperty(Util.DATA_DIR_PROPERTY, this.dataDir);
        }
    }
}
