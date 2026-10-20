package ui;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import org.traincontrol.util.SelfUpdate;
import org.traincontrol.util.Util;
import support.Answering;
import support.Components;

/**
 * Help > Download Update asks what to do with the new version, fetches it, and says where it put it (FR-125).
 *
 * Adam, 2026-10-12: *"when user clicks on the new version, they see a download, download & update, or cancel button"*,
 * and *"If no jar in current folder, it shouldn't be offered as a menu option."*  A test runs from a folder of classes,
 * so the question offers Download and Cancel.
 *
 * **Nothing is fetched from the internet.**  The window's update check is turned off before the window is built, and
 * the new version is set where that check would set it, pointing at a server on this computer.  The download goes
 * where the program puts it with no jar to replace, the working directory, under a version no release will have,
 * and is deleted after each claim.  The folder the program then shows is refused by the claim's own security manager,
 * so no file manager opens on the desktop; the folder it asked for is kept.
 *
 * Download & Update cannot be reached here: it needs TrainControl running from a jar it can replace.  Its refusals
 * ("not a whole TrainControl jar", "the backup did not finish") need a test hook to reach and are left to Adam.
 *
 * MUTATIONS, each seen red: the download written somewhere other than the target the notice names (the download
 * claim); the error notice dropped from the download's catch (the failure claim); Cancel taken as Download (the
 * cancel claim).
 */
public class testDownloadUpdateFetchesTheNewVersion
{
    /** A version no release will ever have, so the download's file name is ours alone. */
    private static final String VERSION = "99.0.1";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    private static HttpServer server;
    private static final AtomicInteger requests = new AtomicInteger();
    private static volatile int status = 200;

    /** What the server sends: not a jar, which Download alone does not ask. */
    private static final byte[] SERVED = "P6 update payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private static boolean checkedWas;
    private static String latestWas;
    private static String urlWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the main window needs a display");

        sandbox = support.LayoutSandbox.open();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/", exchange ->
        {
            requests.incrementAndGet();

            if (status != 200)
            {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();

                return;
            }

            exchange.sendResponseHeaders(200, SERVED.length);
            exchange.getResponseBody().write(SERVED);
            exchange.close();
        });

        server.start();

        // NO CHECK AGAINST THE INTERNET: off before the window is built, in this run's preferences, and put back
        checkedWas = TrainControlUI.getPrefs().getBoolean(TrainControlUI.CHECK_FOR_UPDATES, true);

        TrainControlUI.getPrefs().putBoolean(TrainControlUI.CHECK_FOR_UPDATES, false);

        latestWas = TrainControlUI.LATEST_VERSION;
        urlWas = TrainControlUI.LATEST_DOWNLOAD_URL;

        model = support.TestStationAddress.withNoStation(() -> MarklinControlStation.init(null, true, true, false, false));

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        Components.settle();

        // What the update check sets when a newer release is found
        TrainControlUI.LATEST_VERSION = VERSION;
        TrainControlUI.LATEST_DOWNLOAD_URL = "http://127.0.0.1:" + server.getAddress().getPort() + "/TrainControl.jar";
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            TrainControlUI.LATEST_VERSION = latestWas;
            TrainControlUI.LATEST_DOWNLOAD_URL = urlWas;

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

    @AfterMethod(alwaysRun = true)
    public void tidy() throws Exception
    {
        status = 200;

        // Not a file of anybody's: the name carries a version no release has
        for (File left : new File[] {target(), new File(target().getAbsolutePath() + Util.PARTIAL_DOWNLOAD_SUFFIX)})
        {
            Files.deleteIfExists(left.toPath());
        }
    }

    /**
     * Download fetches the new version to the place the notice names, and asks for that folder to be shown.  The menu
     * item is back to itself afterwards.
     *
     * @throws Exception from the window or the server
     */
    @Test
    public void testDownloadPutsTheNewVersionWhereTheNoticeSays() throws Exception
    {
        assertFalse(target().exists(), "precondition: " + target() + " is there before the download");

        final AbstractButton item = item();

        final String download = I18n.t("ui.update.download");

        List<String> said;
        List<String> shown;

        NoFileManager guard = new NoFileManager();

        System.setSecurityManager(guard);

        try (Answering answering = Answering.start(message -> message.startsWith(firstLineOfTheQuestion())
            ? Answering.press(download) : Answering.press(I18n.t("ui.ok"))))
        {
            SwingUtilities.invokeLater(item::doClick);

            assertTrue(answering.awaitAsked(2, 30000), "the download never said it had finished: " + answering.asked());

            said = answering.asked();

            assertTrue(Components.waitFor(() -> !guard.asked.isEmpty(), 10000), "the download did not show its folder");

            shown = new ArrayList<>(guard.asked);
        }
        finally
        {
            System.setSecurityManager(null);
        }

        assertEquals(said.get(0), I18n.f("ui.update.available", VERSION, MarklinControlStation.RAW_VERSION),
            "the question does not name the new version and this one");

        assertEquals(said.get(1), I18n.f("ui.infoDownloadedUpdateReplaceExistingJar", target().getAbsolutePath()),
            "the notice does not say where the download is");

        assertTrue(target().isFile(), "nothing is where the notice says the download is: " + target());

        assertTrue(Arrays.equals(Files.readAllBytes(target().toPath()), SERVED), "the file is not what was served");

        assertEquals(shown, Collections.singletonList(target().getAbsoluteFile().getParent()), "the download did not show"
            + " the folder it is in");

        assertTrue(Components.waitFor(() -> item.isEnabled() && I18n.t("ui.main.downloadUpdate").equals(item.getText()),
            10000), "Download Update is not itself again after the download: \"" + item.getText() + "\"");
    }

    /**
     * A download the server refuses says so, and leaves no file behind, whole or partial.
     *
     * @throws Exception from the window or the server
     */
    @Test
    public void testAFailedDownloadSaysSoAndLeavesNothing() throws Exception
    {
        status = 404;

        final AbstractButton item = item();

        final String download = I18n.t("ui.update.download");

        List<String> said;

        try (Answering answering = Answering.start(message -> message.startsWith(firstLineOfTheQuestion())
            ? Answering.press(download) : Answering.press(I18n.t("ui.ok"))))
        {
            SwingUtilities.invokeLater(item::doClick);

            assertTrue(answering.awaitAsked(2, 30000), "the failed download said nothing: " + answering.asked());

            said = answering.asked();
        }

        assertEquals(said.get(1), I18n.t("ui.errorDownloadingUpdateFile"), "a refused download does not say it failed");

        assertTrue(requests.get() > 0, "precondition: the server was never asked");

        assertFalse(target().exists(), "a refused download left a file where the new version goes");

        assertFalse(new File(target().getAbsolutePath() + Util.PARTIAL_DOWNLOAD_SUFFIX).exists(), "a refused download"
            + " left a partial file");

        assertTrue(Components.waitFor(() -> item.isEnabled() && I18n.t("ui.main.downloadUpdate").equals(item.getText()),
            10000), "Download Update is not itself again after a failed download: \"" + item.getText() + "\"");
    }

    /**
     * Without a jar to replace, the question offers no Download & Update; Cancel fetches nothing.
     *
     * @throws Exception from the window
     */
    @Test
    public void testCancelFetchesNothingAndThereIsNoUpdateWithoutAJar() throws Exception
    {
        int before = requests.get();

        final AbstractButton item = item();

        for (String pressed : new String[] {I18n.t("ui.update.downloadAndUpdate"), I18n.t("ui.cancel")})
        {
            List<String> said;

            try (Answering answering = Answering.start(message -> Answering.press(pressed)))
            {
                SwingUtilities.invokeLater(item::doClick);

                assertTrue(answering.awaitAsked(1, 10000), "Download Update asked nothing");

                Components.settle();

                said = answering.asked();
            }

            if (pressed.equals(I18n.t("ui.cancel")))
            {
                assertEquals(said.size(), 1, "Cancel was followed by another question: " + said);
            }
            else
            {
                assertTrue(said.size() == 2 && said.get(1).startsWith("UNANSWERED: "), "with no jar to replace, the"
                    + " question offers Download & Update (Adam: \"If no jar in current folder, it shouldn't be offered\"): "
                    + said);
            }
        }

        Components.settle();

        assertEquals(requests.get(), before, "the server was asked for the new version without Download being pressed");

        assertFalse(target().exists(), "a cancelled question downloaded the new version");
    }

    /** The question's first sentence, which the answer is chosen by. */
    private static String firstLineOfTheQuestion()
    {
        return I18n.f("ui.update.available", VERSION, MarklinControlStation.RAW_VERSION);
    }

    /** Where the program puts the new version with no jar to replace. */
    private static File target()
    {
        return SelfUpdate.downloadTarget(null, VERSION).getAbsoluteFile();
    }

    private static AbstractButton item()
    {
        AbstractButton item = Components.withText(ui, I18n.t("ui.main.downloadUpdate"));

        assertNotNull(item, "the window has no Download Update");

        assertTrue(item.isEnabled(), "precondition: Download Update is greyed");

        return item;
    }

    /**
     * Refuses the file manager the program opens, and keeps the folder it asked for: `Desktop.open` asks whether the
     * folder exists before it opens it, and that question is refused.  Everything else is allowed.
     */
    private static final class NoFileManager extends SecurityManager
    {
        final List<String> asked = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void checkPermission(Permission permission)
        {
            if (!(permission instanceof java.io.FilePermission) || !permission.getActions().contains("read")) return;

            for (StackTraceElement frame : Thread.currentThread().getStackTrace())
            {
                if ("java.awt.Desktop".equals(frame.getClassName()))
                {
                    asked.add(permission.getName());

                    throw new SecurityException("no file manager is opened in a test");
                }
            }
        }

        @Override
        public void checkPermission(Permission permission, Object context)
        {
            checkPermission(permission);
        }
    }
}
