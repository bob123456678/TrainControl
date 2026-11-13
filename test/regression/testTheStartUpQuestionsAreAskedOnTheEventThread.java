package regression;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.net.InetSocketAddress;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Display;

/**
 * OB-445: start-up's questions to the user, the Central Station's address and the "no answer" message, are asked on
 * the event thread, which Swing needs.  Each claim runs `init` on a plain thread, as `main` does, with no station: the
 * questions are answered as the user would, and the exit that ends the start-up is refused.
 *
 * @author Adam
 */
public class testTheStartUpQuestionsAreAskedOnTheEventThread
{
    /** A closed port, so the address is refused at once and no station is ever reached. */
    private static final String NOBODY = "127.0.0.1:1";

    private final Preferences prefs = TrainControlUI.getPrefs();
    private String storedAddress;
    private NoExit noExit;
    private Watcher watcher;
    private support.LayoutSandbox sandbox;

    @BeforeMethod
    public void setUp() throws Exception
    {
        Display.needed("start-up's questions are dialogs");

        // Start-up is refused before any layout is read, but if it ever got that far it must not reach the operator's
        sandbox = support.LayoutSandbox.open();

        storedAddress = prefs.get(TrainControlUI.IP_PREF, null);
        prefs.remove(TrainControlUI.IP_PREF);

        noExit = new NoExit();
        System.setSecurityManager(noExit);

        watcher = new Watcher();
        Toolkit.getDefaultToolkit().addAWTEventListener(watcher, AWTEvent.HIERARCHY_EVENT_MASK);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown()
    {
        if (watcher != null) Toolkit.getDefaultToolkit().removeAWTEventListener(watcher);
        if (noExit != null) System.setSecurityManager(null);

        if (storedAddress == null) prefs.remove(TrainControlUI.IP_PREF);
        else prefs.put(TrainControlUI.IP_PREF, storedAddress);

        if (sandbox != null) sandbox.close();
    }

    /**
     * With no address stored: the address question, a wrong address typed into it, the "no response" box, the question
     * again, Cancel, and the "no address" box before the exit.  Every one is built and shown on the event thread, and
     * the typed address reaches start-up (the box names it).
     *
     * MUTATION: in `MarklinControlStation.init`, call `askForTheAddress(typedLastTime)` directly instead of through
     * `onTheEventThread` and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheAddressQuestionAndItsAnswersAreShownOnTheEventThread() throws Exception
    {
        final String noResponse = I18n.f("ui.noResponseFromIp", NOBODY);
        final String noAddress = I18n.t("ui.noIpEnteredShutdown");
        final AtomicInteger prompts = new AtomicInteger();

        try (Answering answering = Answering.start(message ->
        {
            if (noResponse.equals(message) || noAddress.equals(message)) return null;

            return prompts.incrementAndGet() == 1 ? Answering.type(NOBODY) : Answering.press(I18n.t("ui.cancel"));
        }))
        {
            Throwable ended = startUp();

            List<Answering.Question> asked = answering.questions();

            assertEquals(asked.size(), 4, "start-up did not ask what it should have: " + answering.asked());
            assertEquals(asked.get(0).title, I18n.t("ui.ipAddressInputTitle"), "the address was not asked for first");
            assertEquals(asked.get(1).message, noResponse, "the typed address did not reach start-up's connection");
            assertEquals(asked.get(2).title, I18n.t("ui.ipAddressInputTitle"), "the address was not asked for again");
            assertEquals(asked.get(3).message, noAddress, "Cancel did not say that start-up stops");

            assertEquals(noExit.exits(), Collections.singletonList(1), "start-up did not end with exit 1: " + ended);
        }

        assertTrue(watcher.seen().isEmpty(), "a start-up question was built or shown off the event thread (OB-445): "
            + watcher.seen());
    }

    /**
     * An address that answers but is no Central Station: the warning is built and shown on the event thread.  The
     * stand-in answers only the "are you there" page, so the connection after the warning fails on the address.
     *
     * MUTATION: make `MarklinControlStation.tellAtStartUp` call `JOptionPane.showMessageDialog` on the calling thread
     * and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheNotACentralStationWarningIsShownOnTheEventThread() throws Exception
    {
        com.sun.net.httpserver.HttpServer standIn =
            com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        standIn.createContext("/", exchange ->
        {
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().startsWith("/can/") ? 200 : 404, -1);
            exchange.close();
        });

        standIn.start();

        final String address = "127.0.0.1:" + standIn.getAddress().getPort();
        final String notAStation = I18n.f("ui.deviceNotCentralStationOrUnreachable", address);

        prefs.put(TrainControlUI.IP_PREF, address);

        try (Answering answering = Answering.start(message -> null))
        {
            Throwable ended = startUp();

            assertEquals(answering.asked(), Collections.singletonList(notAStation),
                "start-up did not warn once that the address is no Central Station; it ended with " + ended);
        }
        finally
        {
            standIn.stop(0);
        }

        assertTrue(watcher.seen().isEmpty(), "the warning was built or shown off the event thread (OB-445): "
            + watcher.seen());
    }

    /**
     * Start-up installs the look and feel on the event thread.  Metal is put back first, as a cold start finds it, so
     * start-up has one to install.
     *
     * MUTATION: in `MarklinControlStation.init`, call `TrainControlUI.installLookAndFeel()` directly instead of through
     * `onTheEventThread` and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheLookAndFeelIsInstalledOnTheEventThread() throws Exception
    {
        final List<String> installedOn = Collections.synchronizedList(new ArrayList<>());

        java.beans.PropertyChangeListener installed = change ->
        {
            if (!"lookAndFeel".equals(change.getPropertyName())) return;

            installedOn.add(SwingUtilities.isEventDispatchThread() ? "the event thread" : Thread.currentThread().getName());
        };

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel());
            }
            catch (javax.swing.UnsupportedLookAndFeelException never)
            {
                throw new IllegalStateException(never);
            }
        });

        UIManager.addPropertyChangeListener(installed);

        try (Answering answering = Answering.start(message -> I18n.t("ui.noIpEnteredShutdown").equals(message)
            ? null : Answering.press(I18n.t("ui.cancel"))))
        {
            startUp();
        }
        finally
        {
            UIManager.removePropertyChangeListener(installed);
        }

        assertTrue(!installedOn.isEmpty(), "start-up did not install the look and feel");
        assertEquals(new java.util.HashSet<>(installedOn), Collections.singleton("the event thread"),
            "start-up installed the look and feel off the event thread (OB-445)");
    }

    /** Runs `init` as `main` does, on a thread of its own, and returns how it ended. */
    private static Throwable startUp() throws InterruptedException
    {
        final Throwable[] ended = new Throwable[1];

        Thread startUp = new Thread(() ->
        {
            try
            {
                MarklinControlStation.init(null, false, false, false, false);

                ended[0] = new AssertionError("start-up went on without a Central Station");
            }
            catch (Throwable t)
            {
                ended[0] = t;
            }
        }, "start-up");

        startUp.start();
        startUp.join(100000);

        return ended[0];
    }

    /**
     * Notes a question built or shown off the event thread: a component joining a `JOptionPane`, the pane joining its
     * dialog, and the dialog appearing each tell the thread that does it, which is the one Swing is used from.
     */
    private static final class Watcher implements AWTEventListener
    {
        private final List<String> seen = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void eventDispatched(AWTEvent event)
        {
            if (SwingUtilities.isEventDispatchThread() || !(event instanceof HierarchyEvent)) return;

            java.awt.Component c = ((HierarchyEvent) event).getComponent();

            Object pane = c instanceof JOptionPane ? c : SwingUtilities.getAncestorOfClass(JOptionPane.class, c);

            if (pane == null || seen.size() >= 5) return;

            Object said = ((JOptionPane) pane).getMessage();

            seen.add(c.getClass().getSimpleName() + " of \"" + (said instanceof Object[] ? ((Object[]) said)[0] : said)
                + "\" on " + Thread.currentThread().getName());
        }

        List<String> seen()
        {
            synchronized (seen)
            {
                return new ArrayList<>(seen);
            }
        }
    }

    /** Allows everything but an exit, whose status it keeps ("exitVM.1" is exit 1). */
    private static final class NoExit extends SecurityManager
    {
        private final List<String> refused = new java.util.concurrent.CopyOnWriteArrayList<>();

        List<Integer> exits()
        {
            List<Integer> statuses = new ArrayList<>();

            for (String name : refused) statuses.add(Integer.valueOf(name.substring("exitVM.".length())));

            return statuses;
        }

        @Override
        public void checkPermission(Permission asked)
        {
            if (!asked.getName().startsWith("exitVM.")) return;

            refused.add(asked.getName());

            throw new SecurityException("start-up may not exit in a test");
        }

        @Override
        public void checkPermission(Permission asked, Object context)
        {
            checkPermission(asked);
        }
    }
}
