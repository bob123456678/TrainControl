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
 * A layout folder that is not perfect, and what TrainControl does about it, from hands-on tests 39, 40 and 38: a page
 * whose name will not go into a filename, a page the index promises and the folder does not hold, and a setup file
 * that cannot be read.
 *
 * The rule behind all three: **what TrainControl cannot understand it must not throw away, and must not stop for.**
 * The failure mode that matters is the quiet one, where nine pages load, the tenth does not, nothing says so, and the
 * next save writes the nine back.
 */
public class testLayoutFolderRobustness
{
    /**
     * A page named with a slash in it comes back after a save: the reader sanitizes the name out of the index, so a
     * writer that does not produces a file it never looks for, and resolveSibling reads the slash as a directory.
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

            // The page it is renaming FROM has to be there: saving under a new name writes the new file and removes the
            // old
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
     * A page the index promises and the folder does not hold does not stop the others: nine pages loading and the tenth
     * named in the log is a layout somebody can work with.
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

            MarklinControlStation model = support.TestStation.simulated();

            model.stop();

            String path = "file:///" + folder.getAbsolutePath().replace('\\', '/') + "/";

            CS2File parser = new CS2File(path, model);
            parser.setLayoutDataLoc(path);

            List<LayoutDiagram> loaded = support.LayoutSandbox.wired(model, parser);

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
     * A setup file that cannot be read leaves the layout alone.  Adam: "make a similar test to ensure autonomy is
     * unloaded (or regenerated, if the app is running) gracefully if its config files are corrupt or manually removed."
     * The store reads before it clears, so a file it cannot parse leaves the previous contents in place and reports the
     * failure, instead of handing the caller a blank setup that the next save would write over the real one.
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
     * A page that will not read does not cost the sensors that only appear on it: syncLayouts deletes every s88 that no
     * LOADED page mentions, and with the per-page guard four pages of five now load and the loop reads that as "the
     * railway".  The existing guard, `!feedbackAddresses.isEmpty()`, covers only TOTAL failure.
     */
    @Test
    public void testAnUnreadablePageDoesNotDeleteTheSensorsOnlyItHad() throws Exception
    {
        File folder = brokenSecondPage();

        try
        {
            MarklinControlStation model =
                support.TestStation.simulated();

            try
            {
                // 5 is on the page that reads; 6 is on the page that does not.
                model.newFeedback(5, null);
                model.newFeedback(6, null);

                syncFrom(model, folder);

                assertTrue(model.isFeedbackSet("5"),
                    "the sensor on the page that DID read was deleted, so this test is not measuring "
                    + "what it thinks it is");

                assertTrue(model.isFeedbackSet("6"),
                    "the sensor whose only appearance was on the page that could not be read was "
                    + "deleted.  Nothing on the layout changed - a file was truncated or half-copied - "
                    + "and the accumulated feedback for a whole page is gone, with the autonomy points "
                    + "watching it (RC-A3)");
            }
            finally
            {
                model.stop();
            }
        }
        finally
        {
            delete(folder);
        }
    }

    /**
     * A folder that reads completely still prunes - the control: without it "do not prune when a page failed" is
     * satisfied by never pruning at all.
     */
    @Test
    public void testAFolderThatReadsCompletelyStillPrunes() throws Exception
    {
        File folder = onePageThatReads();

        try
        {
            MarklinControlStation model =
                support.TestStation.simulated();

            try
            {
                model.newFeedback(5, null);
                model.newFeedback(6, null);

                syncFrom(model, folder);

                assertTrue(model.isFeedbackSet("5"), "the sensor that IS on the page went");

                assertFalse(model.isFeedbackSet("6"),
                    "a sensor on no page at all survived a complete, successful read - so the pruning "
                    + "the loop exists for has stopped happening, which is the wrong way to satisfy "
                    + "RC-A3");
            }
            finally
            {
                model.stop();
            }
        }
        finally
        {
            delete(folder);
        }
    }

    /**
     * A folder where every page fails still throws, so the fallback runs: the revert to the Central Station lives in a
     * catch around syncLayouts, and with the per-page guard an all-bad folder would return an empty list, the catch
     * would never run, and the same nothing would happen at every launch.  It must throw BEFORE clearing, or the
     * fallback is handed a window that has already been emptied.
     */
    @Test
    public void testAFolderWhereEveryPageFailsStillFails() throws Exception
    {
        File folder = everyPageBroken();

        try
        {
            MarklinControlStation model =
                support.TestStation.simulated();

            try
            {
                int before = model.getLayoutList().size();

                boolean threw = false;

                try
                {
                    syncFrom(model, folder);
                }
                catch (Exception expected)
                {
                    threw = true;
                }

                assertTrue(threw,
                    "a layout folder where not one page could be read came back as a success, so the "
                    + "revert to the Central Station never runs: an empty diagram, no message, and the "
                    + "override preference kept, at every launch (RC-A4)");

                assertEquals(model.getLayoutList().size(), before,
                    "the diagram already on screen was cleared before the failure was noticed, so the "
                    + "fallback has nothing to fall back from (RC-A4)");
            }
            finally
            {
                model.stop();
            }
        }
        finally
        {
            delete(folder);
        }
    }

    /**
     * An EMPTY folder is not a failed one, and must not revert anything: the throw is gated on pages having FAILED, not
     * on the list coming back empty, because syncLayoutsFromConfiguredSource answers a throw by clearing the user's
     * layout-path preference and reverting to the Central Station.
     */
    @Test
    public void testAnEmptyFolderIsNotAFailedOne() throws Exception
    {
        File folder = Files.createTempDirectory("tc-emptyfolder").toFile();

        try
        {
            File config = new File(folder, "config");
            File pages = new File(config, "gleisbilder");

            assertTrue(pages.mkdirs(), "could not build the fixture");

            // An index that promises nothing at all, built without an escape sequence so no backslash has to survive
            // the script that writes this file.
            String nl = new String(new char[] { 10 });

            write(new File(config, "gleisbild.cs2"),
                "[gleisbild]" + nl + "version" + nl + " .major=1" + nl + "groesse" + nl + " .wert=0" + nl);

            MarklinControlStation model =
                support.TestStation.simulated();

            try
            {
                syncFrom(model, folder);
            }
            catch (Exception thrown)
            {
                fail("an empty layout folder was treated as a failed one, so the user\u2019s layout path "
                    + "preference is cleared and TrainControl reverts to the Central Station over a "
                    + "folder that is merely empty (RC-A4): " + thrown);
            }
            finally
            {
                model.stop();
            }
        }
        finally
        {
            delete(folder);
        }
    }

    /** Two pages, the second one truncated mid-element so that reading it throws. */
    private static File brokenSecondPage() throws Exception
    {
        File folder = Files.createTempDirectory("tc-partial").toFile();
        File pages = new File(new File(folder, "config"), "gleisbilder");

        assertTrue(pages.mkdirs(), "could not build the fixture");

        write(new File(new File(folder, "config"), "gleisbild.cs2"),
            "[gleisbild]\nversion\n .major=1\ngroesse\n .wert=0\nseite\n .name=Reads\nseite\n .name=Broken\n");

        write(new File(pages, "Reads.cs2"), page(5));

        // An element whose id is not a number at all, which throws where the page is parsed rather
        // than being skipped as an unknown component.
        write(new File(pages, "Broken.cs2"),
            "[gleisbildseite]\nversion\n .major=1\nelement\n .id=not a number\n .typ=s88kontakt\n .artikel=6\n");

        return folder;
    }

    /** One page, which reads, mentioning sensor 5 and nothing else. */
    private static File onePageThatReads() throws Exception
    {
        File folder = Files.createTempDirectory("tc-complete").toFile();
        File pages = new File(new File(folder, "config"), "gleisbilder");

        assertTrue(pages.mkdirs(), "could not build the fixture");

        write(new File(new File(folder, "config"), "gleisbild.cs2"),
            "[gleisbild]\nversion\n .major=1\ngroesse\n .wert=0\nseite\n .name=Reads\n");

        write(new File(pages, "Reads.cs2"), page(5));

        return folder;
    }

    /** One page, and it does not read. */
    private static File everyPageBroken() throws Exception
    {
        File folder = Files.createTempDirectory("tc-allbad").toFile();
        File pages = new File(new File(folder, "config"), "gleisbilder");

        assertTrue(pages.mkdirs(), "could not build the fixture");

        write(new File(new File(folder, "config"), "gleisbild.cs2"),
            "[gleisbild]\nversion\n .major=1\ngroesse\n .wert=0\nseite\n .name=Broken\n");

        write(new File(pages, "Broken.cs2"),
            "[gleisbildseite]\nversion\n .major=1\nelement\n .id=not a number\n .typ=s88kontakt\n .artikel=6\n");

        return folder;
    }

    /** A page holding one feedback tile at the given address. */
    private static String page(int address)
    {
        return "[gleisbildseite]\nversion\n .major=1\nelement\n .id=0x101\n .typ=s88kontakt\n .artikel="
            + address + "\n";
    }

    /**
     * Points the model's parser at a folder and runs the real sync, reflectively: both the parser field and syncLayouts
     * are private, and a stand-in would test the stand-in.
     */
    private static void syncFrom(MarklinControlStation model, File folder) throws Exception
    {
        String path = "file:///" + folder.getAbsolutePath().replace('\\', '/') + "/";

        java.lang.reflect.Field parserField =
            MarklinControlStation.class.getDeclaredField("fileParser");
        parserField.setAccessible(true);

        CS2File parser = (CS2File) parserField.get(model);
        parser.setLayoutDataLoc(path);

        java.lang.reflect.Method sync =
            MarklinControlStation.class.getDeclaredMethod("syncLayouts");
        sync.setAccessible(true);

        try
        {
            sync.invoke(model);
        }
        catch (java.lang.reflect.InvocationTargetException wrapped)
        {
            if (wrapped.getCause() instanceof Exception) throw (Exception) wrapped.getCause();

            throw wrapped;
        }
    }

    /**
     * And a setup folder that has been deleted by hand is simply a layout with no setup; the other half of Adam's
     * request.
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
