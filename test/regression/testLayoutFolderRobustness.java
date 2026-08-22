package regression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;

/**
 * A layout folder that is not perfect, and what TrainControl does about it.
 *
 * Written at Adam's request, from hands-on tests 39, 40 and 38 - the three that are about a folder in a
 * state nobody intended: a page whose name will not go into a filename, a page the index promises and
 * the folder does not hold, and a setup file that cannot be read.
 *
 * The rule behind all three is the same one: **what TrainControl cannot understand it must not throw
 * away, and must not stop for.**  A layout is somebody's railway, drawn over years, and the failure mode
 * that matters is not a crash - it is the quiet one, where nine pages load, the tenth does not, nothing
 * says so, and the next save writes the nine back.
 */
public class testLayoutFolderRobustness
{
    /**
     * A page named with a slash in it comes back after a save.
     *
     * "Up/Down" is an ordinary name for a page on a two-level railway and an impossible one for a file.
     * The reader finds a page by taking the name out of the index and sanitising it, so a writer that
     * does not sanitise produces a file the reader will never look for - and resolveSibling reads the
     * slash as a directory, so the page went into a folder of its own and vanished from the layout.
     */
    @Test
    public void testAPageNamedWithASlashSurvivesASave() throws Exception
    {
        File folder = Files.createTempDirectory("tc-slash").toFile();

        try
        {
            File pages = new File(folder, "config/gleisbilder");

            assertTrue(pages.mkdirs(), "could not build the fixture");

            // The page knows where it lives by its URL, which is what getFilePath resolves
            File placeholder = new File(pages, "placeholder.cs2");

            // The page it is renaming FROM has to be there: saving a page under a new name writes the
            // new file and removes the old one
            write(placeholder, "[gleisbildseite]\nversion\n .major=1\n");

            LayoutDiagram page = new LayoutDiagram("Up/Down", 8, 6,
                placeholder.toURI().toURL().toString(), null);

            page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);

            page.setPageId("1");
            page.checkBounds();

            page.saveChanges("Up/Down", false);

            File written = new File(pages, "Up_Down.cs2");

            assertTrue(written.exists(),
                "a page whose name holds a slash was not written where the reader will look for it - "
                + "the folder holds " + Arrays.toString(pages.list()));

            assertFalse(new File(pages, "Up").isDirectory(),
                "the slash was taken as a directory, so the page is in a folder of its own");
        }
        finally
        {
            delete(folder);
        }
    }

    /**
     * A page the index promises and the folder does not hold does not stop the others.
     *
     * The index and the pages are separate files, and they get out of step: a page deleted by hand, a
     * folder half-copied, a sync that dropped one.  Nine pages loading and the tenth being named in the
     * log is a layout somebody can work with; an exception on the way in is not.
     */
    @Test
    public void testAMissingPageDoesNotStopTheOthers() throws Exception
    {
        File folder = Files.createTempDirectory("tc-missing").toFile();

        try
        {
            File config = new File(folder, "config");
            File pages = new File(config, "gleisbilder");

            assertTrue(pages.mkdirs(), "could not build the fixture");

            // Two pages promised, one present
            write(new File(config, "gleisbild.cs2"),
                "[gleisbild]\nversion\n .major=1\ngroesse\n .wert=0\nseite\n .name=Here\nseite\n .name=Gone\n");

            write(new File(pages, "Here.cs2"),
                "[gleisbildseite]\nversion\n .major=1\nelement\n .id=0x101\n .typ=gerade\n .artikel=0\n");

            MarklinControlStation model = MarklinControlStation.init(null, true, false, false, false);

            model.stop();

            String path = "file:///" + folder.getAbsolutePath().replace('\\', '/') + "/";

            CS2File parser = new CS2File(path, model);
            parser.setLayoutDataLoc(path);

            List<LayoutDiagram> loaded = parser.parseLayout(new LinkedList<MarklinAccessory>());

            assertNotNull(loaded, "a missing page took the whole layout down");

            boolean here = false;

            for (LayoutDiagram page : loaded)
            {
                if ("Here".equals(page.getName())) here = true;
            }

            assertTrue(here, "the page that IS there did not load, because another one was not: "
                + loaded.size() + " page(s) came back");
        }
        finally
        {
            delete(folder);
        }
    }

    /**
     * A setup file that cannot be read leaves the layout alone.
     *
     * Adam: "make a similar test to ensure autonomy is unloaded (or regenerated, if the app is running)
     * gracefully if its config files are corrupt or manually removed."
     *
     * The store reads before it clears, so a file it cannot parse leaves the previous contents in place
     * and reports the failure - rather than emptying itself and handing the caller a live, blank setup
     * that the next save would write over the top of the real one.
     */
    @Test
    public void testACorruptSetupFileIsRefusedRatherThanEmptied() throws Exception
    {
        File folder = Files.createTempDirectory("tc-corrupt").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(page("main")));

            session.getStore().setStation(new TileKey("main", 1, 1), true);
            session.getStore().setPointName(new TileKey("main", 1, 1), "Platform 3");

            session.getStore().save();

            File setup = new File(new File(folder, "config/autonomy"), "setup.json");

            assertTrue(setup.exists(), "the fixture did not write a setup file");

            write(setup, "{ this is not json at all");

            AutonomySession broken = new AutonomySession(folder);

            boolean refused = false;

            try
            {
                broken.getStore().load();
            }
            catch (IOException expected)
            {
                refused = true;
            }

            assertTrue(refused,
                "an unreadable setup file was accepted, which means whatever it held is now an empty "
                + "store that the next save will write over the real file");
        }
        finally
        {
            delete(folder);
        }
    }

    /**
     * And a setup folder that has been deleted by hand is simply a layout with no setup.
     *
     * The other half of Adam's request.  Nothing to recover and nothing to complain about: a folder with
     * no autonomy in it is the state every layout starts in.
     */
    @Test
    public void testARemovedSetupIsJustALayoutWithoutOne() throws Exception
    {
        File folder = Files.createTempDirectory("tc-removed").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(page("main")));

            session.getStore().setStation(new TileKey("main", 1, 1), true);
            session.getStore().save();

            delete(new File(folder, "config/autonomy"));

            AutonomySession after = new AutonomySession(folder);

            after.getStore().load();

            assertFalse(after.getStore().isStation(new TileKey("main", 1, 1)),
                "the setup came back from a folder that is not there");

            assertFalse(after.getStore().exists(), "the store thinks a deleted setup still exists");

            // And it can be set up again from scratch without complaint
            after.open(Arrays.asList(page("main")));

            after.getStore().setStation(new TileKey("main", 1, 1), true);

            after.getStore().save();

            assertTrue(after.getStore().exists(), "a fresh setup could not be written after a deletion");
        }
        finally
        {
            delete(folder);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static LayoutDiagram page(String name) throws IOException
    {
        LayoutDiagram page = new LayoutDiagram(name, 8, 6, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        page.checkBounds();

        return page;
    }

    private static void write(File file, String text) throws IOException
    {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static void delete(File file)
    {
        File[] kids = file.listFiles();

        if (kids != null) for (File kid : kids) delete(kid);

        file.delete();
    }
}
