package regression;

import java.awt.Image;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.file.CS2File;

/**
 * A station picture whose server takes the connection and never answers ends as a failed read once the timeouts run
 * out, so it holds neither the image loader the locomotive buttons share nor Locomotive Settings' marks (OB-492).
 */
public class testASilentStationPictureGivesUp
{
    /** Past both timeouts, for a loaded machine to schedule the reading thread. */
    private static final long SLACK_MS = 5000;

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // TrainControlUI reads its preferences as the class loads
        sandbox = support.LayoutSandbox.open();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * MUTATION: read the picture in `CS2File.readPicture` with `javax.imageio.ImageIO.read(new URL(url))`; the read
     * waits for as long as the server stays silent.
     */
    @Test
    public void testAPictureFromASilentServerFailsWithinTheTimeouts() throws Exception
    {
        TrainControlUI window = support.Reflect.unconstructed(TrainControlUI.class);

        // Never accepted: the backlog completes the connection, so the request goes out and no answer ever comes
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")))
        {
            String url = "http://127.0.0.1:" + silent.getLocalPort() + "/app/assets/lok/silent.png";
            FutureTask<Image> read = new FutureTask<>(() -> window.getLocImageMaxHeight(url, 100, 100));
            Thread reader = new Thread(read, "silent station picture");

            reader.setDaemon(true);
            reader.start();

            try
            {
                assertNull(read.get(CS2File.CONNECT_TIMEOUT_MS + CS2File.READ_TIMEOUT_MS + SLACK_MS,
                    TimeUnit.MILLISECONDS), "a server that sent nothing gave a picture");
            }
            catch (ExecutionException failed)
            {
                assertTrue(failed.getCause() instanceof IOException,
                    "the silent server's picture failed with " + failed.getCause() + ", not the IOException an"
                    + " unreadable picture gives, so its callers may not show their fallback");
            }
            catch (TimeoutException hung)
            {
                fail("a picture read from a server that never answers was still waiting after the connect and read"
                    + " timeouts had both run out: the image loader and Locomotive Settings' marks wait with it");
            }
        }
    }
}
