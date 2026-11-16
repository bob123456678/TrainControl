package regression;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.net.InetSocketAddress;
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
import org.traincontrol.gui.widgets.BusyDialog;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.udp.CSDetect;
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
    private support.NoExit noExit;
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

        noExit = new support.NoExit();
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

    /**
     * Auto-Detect's scan, about ten seconds a network adapter before the window is shown, runs behind the busy sign
     * saying what it does, and the sign is gone before start-up says what it found.  The door is read, not run: a real
     * scan could find the operator's own station.  The sign is run, with a stand-in for the scan that waits for it.
     *
     * MUTATION: in `MarklinControlStation.init`, call `CSDetect.detectCentralStation(...)` directly, and this fails.
     */
    @Test(timeOut = 60000)
    public void testAutoDetectScansBehindTheBusySign() throws Exception
    {
        String init = support.SourceOf.withoutComments(support.SourceOf.body("public static MarklinControlStation init("
            + "String initIP, boolean simulate, boolean showUI, boolean autoPowerOn, boolean debug)"));

        assertTrue(init.replaceAll("\\s+", "").contains("whileTheSignSays(I18n.t(\"ui.detectCentralStationAttempt\"),"
            + "sign->{subnets.addAll(CSDetect.getLocalSubnet());returnCSDetect.detectCentralStation("),
            "Auto-Detect's scan, and the gateway pings that find its subnets, do not run behind the busy sign");

        final java.util.concurrent.CountDownLatch shown = new java.util.concurrent.CountDownLatch(1);
        final List<String> said = Collections.synchronizedList(new ArrayList<>());

        AWTEventListener opened = event ->
        {
            if (event.getID() == java.awt.event.WindowEvent.WINDOW_OPENED
                && event.getSource() instanceof org.traincontrol.gui.widgets.BusyDialog)
            {
                for (javax.swing.JLabel label : support.Components.all((java.awt.Component) event.getSource(),
                    javax.swing.JLabel.class, null)) said.add(label.getText());

                shown.countDown();
            }
        };

        Toolkit.getDefaultToolkit().addAWTEventListener(opened, AWTEvent.WINDOW_EVENT_MASK);

        try
        {
            java.util.function.Function<BusyDialog.Closer, String> scan = sign ->
            {
                try
                {
                    return shown.await(10, java.util.concurrent.TimeUnit.SECONDS) ? "found" : null;
                }
                catch (InterruptedException e)
                {
                    throw new IllegalStateException(e);
                }
            };

            Object found = support.Reflect.method(MarklinControlStation.class, "whileTheSignSays", String.class,
                java.util.function.Function.class).invoke(null, I18n.t("ui.detectCentralStationAttempt"), scan);

            assertEquals(found, "found", "the busy sign was not on screen while the scan ran");

            assertTrue(said.toString().contains(I18n.t("ui.detectCentralStationAttempt")),
                "the busy sign did not say that it is looking for the Central Station: " + said);

            // What start-up says next is queued behind the sign's closing
            SwingUtilities.invokeAndWait(() -> { });

            assertEquals(showingBusySigns(), 0, "the busy sign is still up after the scan");
        }
        finally
        {
            Toolkit.getDefaultToolkit().removeAWTEventListener(opened);
        }
    }

    /**
     * OB-480: as the search moves on to the next subnet, the sign says so.  Two subnets where nobody answers, run
     * through the real Auto-Detect button; the label changes on the event thread, so the window kept answering.
     *
     * MUTATION: in `CSDetect.detectCentralStation`, drop `searching.accept(subnet + "x")` and this fails.
     */
    @Test(timeOut = 60000)
    public void testTheSignNamesEachSubnetAsTheSearchReachesIt() throws Exception
    {
        Search search = searchWithNobodyThere();

        assertTrue(search.signSaid.contains(I18n.f("ui.detectCentralStationOnSubnet", SECOND + "x")),
            "the busy sign never said that the search moved on to the second subnet, so it looked as though nothing "
            + "was happening: " + search.signSaid);
        assertTrue(search.saidOffTheEventThread.isEmpty(), "the sign's words were changed from the search's own "
            + "thread, not the event thread: " + search.saidOffTheEventThread);
    }

    /**
     * OB-480: the start-up sign stays in front of other programs, as there is no window of ours to bring it back.
     *
     * MUTATION: in the `BusyDialog` constructor, drop `if (parent == null) setAlwaysOnTop(true)` and this fails.
     */
    @Test(timeOut = 60000)
    public void testTheSignStaysInFrontWhileTheSearchRuns() throws Exception
    {
        Search search = searchWithNobodyThere();

        assertEquals(search.signOnTop, Collections.singletonList(true),
            "the busy sign was not kept in front, so clicking any other program hid it for the rest of the search");
    }

    /**
     * OB-480: a search that finds nothing says so, in front of other programs, and asks for the address again.
     *
     * MUTATION: in `MarklinControlStation.tellAtStartUp`, drop `box.setAlwaysOnTop(true)` and this fails.
     */
    @Test(timeOut = 60000)
    public void testASearchThatFindsNothingSaysSoInFront() throws Exception
    {
        Search search = searchWithNobodyThere();
        List<Answering.Question> asked = search.asked;

        // The address, the empty search, the address again, and the stop Cancel leads to
        assertEquals(asked.size(), 4, "start-up did not ask what it should have: " + asked.size());
        assertEquals(asked.get(1).message, I18n.t("ui.noCentralStationDetected"), "the empty search was not told");
        assertTrue(asked.get(1).alwaysOnTop, "the end of the search was told behind other programs' windows, where "
            + "nothing of ours could bring it back, so the program seemed to hang");
        assertEquals(asked.get(2).title, I18n.t("ui.ipAddressInputTitle"), "the address was not asked for again");
    }

    /** The two subnets searched: documentation ranges (RFC 5737), where nobody ever answers. */
    private static final String FIRST = "192.0.2.", SECOND = "198.51.100.";

    /** What the user saw of one search. */
    private static final class Search
    {
        final List<String> signSaid = Collections.synchronizedList(new ArrayList<>());
        final List<Boolean> signOnTop = Collections.synchronizedList(new ArrayList<>());
        final List<String> saidOffTheEventThread = Collections.synchronizedList(new ArrayList<>());
        List<Answering.Question> asked;
    }

    /** How long a search over the two empty subnets may take, well inside the claims' timeOut. */
    private static final long SEARCH_WAIT_MS = 30000;

    /**
     * Presses Auto-Detect at start-up over two empty subnets, two hosts each, then Cancel on the address asked next.  The
     * hooks go back only once start-up has ended: a start-up still running without them would scan the real network.
     */
    private static Search searchWithNobodyThere() throws Exception
    {
        final Search search = new Search();
        final Object subnetsBefore = support.Reflect.get(CSDetect.class, null, "subnetsForATest");
        final Object hostsBefore = support.Reflect.get(CSDetect.class, null, "hostsPerSubnet");
        final String noStation = I18n.t("ui.noCentralStationDetected");
        final String noAddress = I18n.t("ui.noIpEnteredShutdown");
        final AtomicInteger prompts = new AtomicInteger();

        // The label is read as the sign opens and on every change after: the second subnet's can come either side
        AWTEventListener opened = event ->
        {
            if (event.getID() != java.awt.event.WindowEvent.WINDOW_OPENED || !(event.getSource() instanceof BusyDialog))
                return;

            search.signOnTop.add(((BusyDialog) event.getSource()).isAlwaysOnTop());

            for (javax.swing.JLabel label : support.Components.all((BusyDialog) event.getSource(),
                javax.swing.JLabel.class, null))
            {
                search.signSaid.add(label.getText());
                label.addPropertyChangeListener("text", change ->
                {
                    search.signSaid.add((String) change.getNewValue());

                    if (!SwingUtilities.isEventDispatchThread()) search.saidOffTheEventThread.add(Thread.currentThread()
                        .getName());
                });
            }
        };

        Thread startUp = new Thread(() ->
        {
            try
            {
                MarklinControlStation.init(null, false, false, false, false);
            }
            catch (Throwable ended)
            {
                // The refused exit, which is how this start-up ends
            }
        }, "start-up");

        Toolkit.getDefaultToolkit().addAWTEventListener(opened, AWTEvent.WINDOW_EVENT_MASK);
        support.Reflect.set(CSDetect.class, null, "subnetsForATest", java.util.Arrays.asList(FIRST, SECOND));
        support.Reflect.set(CSDetect.class, null, "hostsPerSubnet", 2);

        try (Answering answering = Answering.start(message ->
        {
            if (noStation.equals(message) || noAddress.equals(message)) return null;

            return Answering.press(I18n.t(prompts.incrementAndGet() == 1 ? "ui.autoDetect" : "ui.cancel"));
        }))
        {
            startUp.start();
            startUp.join(SEARCH_WAIT_MS);
            search.asked = answering.questions();
        }
        finally
        {
            Toolkit.getDefaultToolkit().removeAWTEventListener(opened);

            startUp.interrupt();
            startUp.join(5000);

            // Left on the empty subnets, deliberately: this class's JVM ends after it
            if (startUp.isAlive()) throw new IllegalStateException("start-up did not end after the search");

            support.Reflect.set(CSDetect.class, null, "subnetsForATest", subnetsBefore);
            support.Reflect.set(CSDetect.class, null, "hostsPerSubnet", hostsBefore);
        }

        return search;
    }

    /** How many busy signs are on screen; asked on the event thread. */
    private static int showingBusySigns() throws Exception
    {
        final int[] count = new int[1];

        SwingUtilities.invokeAndWait(() ->
        {
            for (java.awt.Window w : java.awt.Window.getWindows())
            {
                if (w instanceof org.traincontrol.gui.widgets.BusyDialog && w.isShowing()) count[0]++;
            }
        });

        return count[0];
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
}
