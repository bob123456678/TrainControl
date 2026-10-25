package ui;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import org.traincontrol.util.SelfUpdate;
import org.traincontrol.util.Util;
import support.Answering;
import support.Components;

/**
 * Help > Download Update > Download & Update, from a jar it can replace: each way it stops short of the exit says why
 * and leaves the jar as it was (FR-125).
 *
 * Adam, 2026-10-12: *"after downloading the new jar, it should take a backup, then start the update script and close
 * TrainControl."*  A test runs from a folder of classes, where Download & Update is not offered, so each claim points
 * `SelfUpdate.runningJarForTests` at a throwaway jar in a folder of its own (Adam, 2026-10-19: "OK on download update
 * test hooks") and clicks the menu item, answering the real dialogs through `support.Answering`.
 *
 * **Nothing is fetched from the internet and nothing real is replaced.**  The window's update check is off before the
 * window is built; the new version is served by a server on this computer, under a version no release will have; the
 * download goes beside the throwaway jar; the backup goes into the run's own data folder and is deleted after each
 * claim.  A security manager refuses, and records, any process started and any exit while a claim runs, so a defect
 * that let the update reach the exit is a red here, not an updater on the desktop.
 *
 * The census in `regression.testSwitchingToACentralStationLayout` does not count this class: it takes the window
 * `init` builds, as `testDownloadUpdateFetchesTheNewVersion` does.
 *
 * MUTATIONS, each seen red: `downloadTheUpdate` not asking `whyThisUpdateMustWait` (the refused-while-running claim);
 * `closeNowForTheUpdate` not asking it again (the run-started-meanwhile claim); `installTheDownload` not stopping on
 * `whyNotAWholeJar` (the not-a-jar claim); `installTheDownload` taking an incomplete backup as complete (the backup
 * claim); `downloadTheUpdate` going on when `updateHandOver.begin()` refuses (the already-under-way claim).
 */
public class testDownloadAndUpdateFromAJar
{
    /** A version no release will ever have, so the download's file name is ours alone. */
    private static final String VERSION = "99.0.2";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AbstractButton item;

    private static HttpServer server;
    private static final AtomicInteger requests = new AtomicInteger();

    /** What the server sends. */
    private static volatile byte[] served;

    /** Held, the server keeps the download waiting until it is counted down. */
    private static volatile CountDownLatch gate;

    /** A whole jar, as a release is: a manifest naming its main class. */
    private static final byte[] WHOLE = jar("NewVersion");

    private static boolean checkedWas;
    private static String latestWas;
    private static String urlWas;
    private static long sizeWas;
    private static String digestWas;

    /** The claim's own folder, with the throwaway jar the program pretends to run from. */
    private File folder;
    private File running;
    private byte[] runningBytes;

    private Set<String> archivesBefore;
    private NoUpdaterNoExit guard;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the main window needs a display");

        // The update's backup goes into the data folder; only the runners give a test run one of its own
        if (System.getProperty(Util.DATA_DIR_PROPERTY, "").trim().isEmpty())
        {
            throw new SkipException("Download & Update backs up into the data folder, and this run has none of its own"
                + " (one.sh and battery.sh give it one)");
        }

        sandbox = support.LayoutSandbox.open();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/", exchange ->
        {
            requests.incrementAndGet();

            final CountDownLatch held = gate;

            try
            {
                if (held != null) held.await(60, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            final byte[] body = served;

            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // A second download, were one started, is answered and counted rather than queued behind a held first
        server.setExecutor(Executors.newCachedThreadPool(r ->
        {
            Thread t = new Thread(r, "fake release server");

            t.setDaemon(true);

            return t;
        }));

        server.start();

        // NO CHECK AGAINST THE INTERNET: off before the window is built, in this run's preferences, and put back
        checkedWas = TrainControlUI.getPrefs().getBoolean(TrainControlUI.CHECK_FOR_UPDATES, true);

        TrainControlUI.getPrefs().putBoolean(TrainControlUI.CHECK_FOR_UPDATES, false);

        latestWas = TrainControlUI.LATEST_VERSION;
        urlWas = TrainControlUI.LATEST_DOWNLOAD_URL;
        sizeWas = TrainControlUI.LATEST_DOWNLOAD_SIZE;
        digestWas = TrainControlUI.LATEST_DOWNLOAD_DIGEST;

        model = support.TestStation.simulatedWithWindow();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        Components.settle();

        assertNotNull(model.getAutoLayout(), "precondition: the sandboxed layout has no autonomy layout to run");

        item = Components.withText(ui, I18n.t("ui.main.downloadUpdate"));

        assertNotNull(item, "the window has no Download Update");

        // What the update check sets when a newer release is found; a release that gives no size or checksum, so the
        // jar check alone decides whether a download is whole
        TrainControlUI.LATEST_VERSION = VERSION;
        TrainControlUI.LATEST_DOWNLOAD_URL = "http://127.0.0.1:" + server.getAddress().getPort() + "/TrainControl.jar";
        TrainControlUI.LATEST_DOWNLOAD_SIZE = -1;
        TrainControlUI.LATEST_DOWNLOAD_DIGEST = null;
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            TrainControlUI.LATEST_VERSION = latestWas;
            TrainControlUI.LATEST_DOWNLOAD_URL = urlWas;
            TrainControlUI.LATEST_DOWNLOAD_SIZE = sizeWas;
            TrainControlUI.LATEST_DOWNLOAD_DIGEST = digestWas;

            TrainControlUI.getPrefs().putBoolean(TrainControlUI.CHECK_FOR_UPDATES, checkedWas);

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(closing::dispose);
            }

            if (model != null) model.stop();

            if (server != null) server.stop(0);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    @BeforeMethod
    public void setUp() throws Exception
    {
        served = WHOLE;
        gate = null;

        folder = Files.createTempDirectory("tc-update-from-a-jar").toFile();
        running = new File(folder, "TrainControl.jar");
        runningBytes = jar("OldVersion");

        Files.write(running.toPath(), runningBytes);

        archivesBefore = archives();

        assertTrue(item.isEnabled() && I18n.t("ui.main.downloadUpdate").equals(item.getText()), "precondition: Download"
            + " Update is not itself: \"" + item.getText() + "\"");

        SelfUpdate.runningJarForTests(running);

        guard = new NoUpdaterNoExit();

        System.setSecurityManager(guard);
    }

    @AfterMethod(alwaysRun = true)
    public void tidy() throws Exception
    {
        try
        {
            final CountDownLatch held = gate;

            gate = null;

            if (held != null) held.countDown();

            if (model == null || item == null) return;

            model.getAutoLayout().setStagingInProgress(false);

            // Whatever the claim left under way finishes before the next starts
            Components.waitFor(() -> item.isEnabled() && I18n.t("ui.main.downloadUpdate").equals(item.getText()), 30000);

            Components.settle();
        }
        finally
        {
            System.setSecurityManager(null);

            SelfUpdate.runningJarForTests(null);

            for (String made : archives())
            {
                if (archivesBefore != null && !archivesBefore.contains(made))
                {
                    Files.deleteIfExists(new File(backupFolder(), made).toPath());
                }
            }

            if (folder != null)
            {
                for (File left : folder.listFiles()) Files.deleteIfExists(left.toPath());

                Files.deleteIfExists(folder.toPath());
            }
        }
    }

    /**
     * Refused before anything is fetched while a run is going: the question offers Download & Update (there is a jar
     * to replace), and pressing it says autonomy, a timetable, Return Home or a route must stop first.  The server is
     * never asked and the jar's folder is as it was.
     *
     * @throws Exception from the window
     */
    @Test
    public void testItIsRefusedBeforeTheDownloadWhileARunIsGoing() throws Exception
    {
        // A Return Home run, as the window marks it on the railway
        model.getAutoLayout().setStagingInProgress(true);

        final int before = requests.get();

        final List<String> said = clickAndAnswer(2);

        assertEquals(said.get(0), question() + "\n\n" + I18n.t("ui.update.explainInstall"), "the question does"
            + " not name the new version and this one, or does not explain Download & Update: " + said);

        assertEquals(said.get(1), I18n.t("ui.update.refusedWhileRunning"), "Download & Update was not refused while a"
            + " run is going: " + said);

        assertEquals(said.size(), 2, "more was said than the refusal: " + said);

        assertEquals(requests.get(), before, "the new version was downloaded for an update that was refused");

        assertEquals(namesIn(folder), Collections.singleton(running.getName()), "a refused update left files beside the"
            + " jar");

        assertTheJarAndTheProgramAreAsTheyWere();
    }

    /**
     * A run started while the download was under way stops the update at its exit (asked again there, since the
     * download and the backup take time): the refusal is said, the new version stays beside the jar unused, and the
     * update is over - a second Download & Update is refused for the run, not as one still under way.
     *
     * @throws Exception from the window
     */
    @Test
    public void testARunStartedDuringTheDownloadStopsItAtTheExit() throws Exception
    {
        final int before = requests.get();

        gate = new CountDownLatch(1);

        List<String> said;

        try (Answering answering = Answering.start(rule()))
        {
            SwingUtilities.invokeLater(item::doClick);

            assertTrue(Components.waitFor(() -> requests.get() > before, 30000), "the download never started: "
                + answering.asked());

            model.getAutoLayout().setStagingInProgress(true);

            gate.countDown();

            assertTrue(answering.awaitAsked(2, 90000), "the update said nothing after the download: "
                + answering.asked());

            Components.settle();

            said = answering.asked();
        }

        assertEquals(said.get(1), I18n.t("ui.update.refusedWhileRunning"), "the update did not stop at its exit for"
            + " a run started during its download: " + said);

        assertEquals(said.size(), 2, "more was said than the refusal: " + said);

        assertEquals(requests.get(), before + 1, "the new version was not downloaded exactly once");

        assertEquals(namesIn(folder), new HashSet<>(Arrays.asList(running.getName(), download().getName())),
            "the jar's folder is not the jar and the download beside it");

        assertTrue(Arrays.equals(Files.readAllBytes(download().toPath()), WHOLE), "the download is not what was served");

        assertTheJarAndTheProgramAreAsTheyWere();

        // The update is over: asked again, it is the run that refuses, not one update already under way
        final List<String> again = clickAndAnswer(2);

        assertEquals(again.get(1), I18n.t("ui.update.refusedWhileRunning"), "the stopped update is still under way: "
            + again);

        assertEquals(requests.get(), before + 1, "the second, refused, update downloaded");
    }

    /**
     * A download that is not a whole TrainControl jar - here a page of text where the jar should be - is said to be
     * so, and nothing is backed up, started or replaced.
     *
     * @throws Exception from the window
     */
    @Test
    public void testADownloadThatIsNotAJarIsRefused() throws Exception
    {
        served = "<html>Not Found</html>".getBytes(StandardCharsets.UTF_8);

        final List<String> said = clickAndAnswer(2);

        assertEquals(said.get(1), I18n.f("ui.update.errorNotWhole", I18n.t("ui.update.notWholeNotAJar")), "a download"
            + " that is not a jar was not refused as one: " + said);

        assertEquals(said.size(), 2, "more was said than the refusal: " + said);

        assertEquals(archives(), archivesBefore, "a backup was taken for a download that was refused");

        assertTheJarAndTheProgramAreAsTheyWere();
    }

    /**
     * A backup that does not finish stops the update (Adam: the update goes no further without it), naming what could
     * not be saved - here a file in the layout's folder that something else holds locked - and nothing is started or
     * replaced.
     *
     * @throws Exception from the window or the files
     */
    @Test
    public void testAnUnfinishedBackupStopsIt() throws Exception
    {
        final File held = new File(new File(sandbox.getFolder(), "config"), "held-by-the-update-test.txt");

        Files.write(held.toPath(), "locked while the backup reads it".getBytes(StandardCharsets.UTF_8));

        List<String> said;

        try (RandomAccessFile file = new RandomAccessFile(held, "rw"); FileChannel channel = file.getChannel();
            FileLock lock = channel.lock())
        {
            said = clickAndAnswer(2);
        }
        finally
        {
            Files.deleteIfExists(held.toPath());
        }

        final String[] around = I18n.f("ui.update.errorBackup", "\u0000").split("\u0000");

        assertTrue(said.get(1).startsWith(around[0]) && said.get(1).endsWith(around[1])
            && said.get(1).contains(held.getName()), "an unfinished backup did not stop the update, naming the file it"
            + " could not save: " + said);

        assertEquals(said.size(), 2, "more was said than the refusal: " + said);

        assertTheJarAndTheProgramAreAsTheyWere();
    }

    /**
     * A second Download & Update while the first is still under way - downloaded and backed up, its exit waiting for
     * a dialog the user had open - is refused with "An update is already under way."  Only one download was made and
     * no updater started; the first then stops at its exit for the run started meanwhile, as above.
     *
     * While the first downloads and backs up the menu item is grayed, so the user cannot ask twice then; the exit's wait
     * for an open dialog is when the item is back and the update still under way.
     *
     * @throws Exception from the window
     */
    @Test
    public void testASecondUpdateWhileOneIsUnderWayIsRefused() throws Exception
    {
        final int before = requests.get();

        gate = new CountDownLatch(1);

        final JDialog open = new JDialog(ui, "the user's own dialog", true);

        List<String> said;

        try (Answering answering = Answering.start(rule()))
        {
            SwingUtilities.invokeLater(item::doClick);

            assertTrue(Components.waitFor(() -> requests.get() > before, 30000), "the download never started: "
                + answering.asked());

            assertFalse(Components.onTheEventThread(item::isEnabled), "precondition: Download Update can be clicked"
                + " while the download runs");

            // A dialog of the user's, open when the backup ends, so the exit waits for it
            SwingUtilities.invokeLater(() ->
            {
                open.setSize(200, 100);
                open.setVisible(true);
            });

            assertTrue(Components.waitFor(open::isShowing, 10000), "precondition: the user's dialog did not open");

            gate.countDown();

            // Downloaded and backed up: the item is back, and the update waits at its exit
            assertTrue(Components.waitFor(() -> item.isEnabled()
                && I18n.t("ui.main.downloadUpdate").equals(item.getText()), 90000), "the first update did not reach"
                + " its exit: " + answering.asked());

            Components.settle();

            assertEquals(answering.asked().size(), 1, "precondition: the first update said something before its exit: "
                + answering.asked());

            // A run started meanwhile, so the first update stops at its exit rather than closing the program
            model.getAutoLayout().setStagingInProgress(true);

            // The user closes the dialog and asks again
            SwingUtilities.invokeLater(() ->
            {
                open.dispose();

                item.doClick();
            });

            assertTrue(answering.awaitAsked(4, 30000), "the second update and the first's exit did not both answer: "
                + answering.asked());

            Components.settle();

            said = answering.asked();
        }
        finally
        {
            SwingUtilities.invokeLater(open::dispose);
        }

        assertTrue(said.get(1).startsWith(question()), "the second request did not ask the question: " + said);

        assertEquals(said.get(2), I18n.t("ui.update.alreadyUnderWay"), "a second Download & Update while one was under"
            + " way was not refused as one: " + said);

        assertEquals(said.get(3), I18n.t("ui.update.refusedWhileRunning"), "the first update did not stop at its exit"
            + " for the run: " + said);

        assertEquals(said.size(), 4, "more was said: " + said);

        assertEquals(requests.get(), before + 1, "two updates downloaded the new version");

        assertTheJarAndTheProgramAreAsTheyWere();
    }

    /** No updater started, no exit asked for, the jar itself unchanged and no copy of it beside it. */
    private void assertTheJarAndTheProgramAreAsTheyWere() throws Exception
    {
        assertEquals(guard.refused(), Collections.emptyList(), "the update went past where it should have stopped");

        assertTrue(Arrays.equals(Files.readAllBytes(running.toPath()), runningBytes), "the running jar was changed");

        for (String name : namesIn(folder))
        {
            assertFalse(name.startsWith(SelfUpdate.UPDATER_COPY_PREFIX), "an updater's copy was made: " + name);
        }

        assertTrue(Components.waitFor(() -> item.isEnabled() && I18n.t("ui.main.downloadUpdate").equals(item.getText()),
            10000), "Download Update is not itself again: \"" + item.getText() + "\"");
    }

    /** Clicks Download Update, presses Download & Update and OK on what follows, until this many were asked. */
    private static List<String> clickAndAnswer(int count)
    {
        try (Answering answering = Answering.start(rule()))
        {
            SwingUtilities.invokeLater(item::doClick);

            assertTrue(answering.awaitAsked(count, 90000), "Download & Update said too little: " + answering.asked());

            Components.settle();

            return answering.asked();
        }
    }

    /** Download & Update for the question, OK for everything after it. */
    private static Function<String, Answering.Answer> rule()
    {
        return message -> message.startsWith(question())
            ? Answering.press(I18n.t("ui.update.downloadAndUpdate")) : Answering.press(I18n.t("ui.ok"));
    }

    /** The question's first sentence. */
    private static String question()
    {
        return I18n.f("ui.update.available", VERSION, MarklinControlStation.RAW_VERSION);
    }

    /** Where the new version goes, beside the throwaway jar. */
    private File download()
    {
        return SelfUpdate.downloadTarget(running, VERSION);
    }

    private static Set<String> namesIn(File dir)
    {
        return new HashSet<>(Arrays.asList(dir.list()));
    }

    /** The run's own backup folder. */
    private static File backupFolder()
    {
        return new File(Util.getBackupPath("x")).getAbsoluteFile().getParentFile();
    }

    private static Set<String> archives()
    {
        String[] names = backupFolder().list();

        return names == null ? new TreeSet<>() : new TreeSet<>(Arrays.asList(names));
    }

    /** A small runnable-looking jar: a manifest naming a main class, and one entry. */
    private static byte[] jar(String main)
    {
        try
        {
            Manifest manifest = new Manifest();

            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, main);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try (JarOutputStream out = new JarOutputStream(bytes, manifest))
            {
                out.putNextEntry(new JarEntry(main + ".txt"));
                out.write(main.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }

            return bytes.toByteArray();
        }
        catch (java.io.IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Refuses, and records, any process started and any exit while a claim runs: the updater is started from the
     * exit, and the exit ends the JVM.  Everything else is allowed.
     */
    private static final class NoUpdaterNoExit extends SecurityManager
    {
        private final List<String> refused = Collections.synchronizedList(new ArrayList<>());

        List<String> refused()
        {
            synchronized (refused)
            {
                return new ArrayList<>(refused);
            }
        }

        @Override
        public void checkExec(String cmd)
        {
            refused.add("started " + cmd);

            throw new SecurityException("no process is started in a test: " + cmd);
        }

        @Override
        public void checkExit(int status)
        {
            refused.add("exit " + status);

            throw new SecurityException("no exit in a test");
        }

        @Override
        public void checkPermission(Permission permission)
        {
        }

        @Override
        public void checkPermission(Permission permission, Object context)
        {
        }
    }
}
