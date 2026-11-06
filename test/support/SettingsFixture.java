package support;

import javax.swing.SwingUtilities;
import org.testng.SkipException;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A simulated railway with its window, for the Locomotive Settings claims: the sandbox, the model and the window, and
 * the way they are shut down.  Not a test.
 */
public final class SettingsFixture
{
    public support.LayoutSandbox sandbox;
    public MarklinControlStation model;
    public TrainControlUI ui;

    /** A folder for pictures made by a claim, set by {@link #needPictures}. */
    public java.io.File folder;

    private boolean stationWasUnset;
    private String iconFolderWas;
    private boolean pictures;

    private SettingsFixture()
    {
    }

    /** Opens the sandbox, the model and the window. */
    public static SettingsFixture open() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        SettingsFixture f = new SettingsFixture();

        try
        {
            f.sandbox = support.LayoutSandbox.open();
            f.model = support.TestStation.simulatedWithWindowInDebug();
            f.ui = (TrainControlUI) f.model.getGUI();

            if (f.ui == null) throw new AssertionError("the model has no window");

            Components.settle();
        }
        catch (Exception | Error e)
        {
            f.closeSandbox();

            throw e;
        }

        return f;
    }

    /** Takes these locomotives out of the database, those that are there. */
    public void forget(String... names) throws Exception
    {
        for (String name : names)
        {
            if (model.getLocByName(name) != null) model.deleteLoc(name);
        }
    }

    /**
     * For claims that choose pictures or reset functions: the station address is pointed at nowhere, the chooser's
     * remembered folder is kept to be put back, and a folder for the pictures is made.
     */
    public void needPictures(String prefix) throws Exception
    {
        stationWasUnset = support.TestStationAddress.get() == null;

        if (stationWasUnset) support.TestStationAddress.set(support.TestStationAddress.NOWHERE);

        iconFolderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_ICON_FOLDER, null);

        folder = java.nio.file.Files.createTempDirectory(prefix).toFile();

        pictures = true;
    }

    /** Whatever the last claim left open, a menu or the window, is closed. */
    public void quiet()
    {
        SettingsWindow.closeIfOpen();
        Mouse.closeMenus();
    }

    /** The end of a class: the window is closed, these locomotives are forgotten, and the window and model are shut. */
    public void finish(String... names) throws Exception
    {
        SettingsWindow.closeIfOpen();

        forget(names);

        shutDown();
    }

    /** Closes the window and the model. */
    public void shutDown() throws Exception
    {
        SettingsWindow.closeIfOpen();

        final TrainControlUI closing = ui;

        if (closing != null)
        {
            SwingUtilities.invokeAndWait(() ->
            {
                closing.stopDrivingTrainsFromTheWholeWindow();
                closing.dispose();
            });
        }

        if (model != null) model.stop();
    }

    /** Puts back what {@link #needPictures} changed, and the layout preference. */
    public void closeSandbox()
    {
        if (pictures)
        {
            if (stationWasUnset) support.TestStationAddress.set(null);

            if (iconFolderWas == null) TrainControlUI.getPrefs().remove(TrainControlUI.LAST_USED_ICON_FOLDER);
            else TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_ICON_FOLDER, iconFolderWas);

            java.io.File[] files = folder.listFiles();

            if (files != null) for (java.io.File f : files) f.delete();

            folder.delete();
        }

        if (sandbox != null) sandbox.close();
    }
}
