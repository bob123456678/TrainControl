package core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.marklin.udp.NetworkProxy;
import org.traincontrol.util.I18n;

/**
 * Tests for NetworkProxy: the CAN listener's fault handling, and the address it reports.
 *
 * The listener used to wrap its entire while(true) loop in a single try, so the first IOException from
 * socket.receive() ended the reader thread for the rest of the session.  TrainControl kept running and
 * kept transmitting, but never received another CAN message - no feedback, no accessory echoes, no
 * power state changes, and path integrity validation then failed every path.  The only visible sign was
 * a single log line.
 *
 * The distinction that matters is transient versus terminal: a receive error has to be survived, while
 * a closed socket must still end the thread.  Both are asserted below, in that order, followed by the
 * reopen in sendMessage which used to sit after the send and so could never run, and the address
 * getIP reports.
 *
 * Deliberately not covered: the reader's exit path must not close a socket that sendMessage has since
 * reopened.  That is a race between the loop test and the thread exiting, so a test for it would pass
 * on broken code most of the time - it is verified by inspection instead.  Its user-visible
 * consequence, transmission recovering, is covered by testSendReopensAClosedSocket.
 *
 * RUN THIS CLASS ON ITS OWN.  It deliberately faults and closes the model's UDP socket, which ends CAN
 * reception for the whole JVM; any test class sharing the JVM afterwards would see a dead network.
 *
 * The socket is reached by reflection because NetworkProxy exposes no way to fault it - which is also
 * why this failure mode was never exercised.
 */
public class testNetworkProxy
{
    private static MarklinControlStation model;
    private static NetworkProxy proxy;

    private static final int ONE_MB_IN_KB = 1024;

    /**
     * What this machine grants a socket that asks for 1 MB, in KB: all of it on Windows, while Linux caps it (rmem_max)
     * without an error.  Asked of a fresh socket that nothing is receiving on, so it answers at once.
     */
    private static int grantableKb() throws java.net.SocketException
    {
        try (java.net.DatagramSocket probe = new java.net.DatagramSocket())
        {
            probe.setReceiveBufferSize(ONE_MB_IN_KB * 1024);

            return Math.min(ONE_MB_IN_KB, probe.getReceiveBufferSize() / 1024);
        }
    }

    /** What the model logged from before it started, for the listener's start lines. */
    private static final List<String> LOGGED = Collections.synchronizedList(new ArrayList<>());

    // Shared across the ordered tests below: installed by the first, closed by the second
    private static FaultyDatagramSocket faulty;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Kept for the class: the JVM is this class's alone
        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(support.LogTap.messagesInto(LOGGED));

        model = init(null, true, false, false, true);

        Field proxyField = MarklinControlStation.class.getDeclaredField("NetworkInterface");
        proxyField.setAccessible(true);
        proxy = (NetworkProxy) proxyField.get(model);

        assertNotNull(proxy, "the model must have a network proxy");
    }

    /**
     * A socket whose receive() fails a fixed number of times and then parks, the way a healthy receive
     * would.  Unbound - receive is overridden, so the underlying socket is never actually read.  The
     * park is interruptible by close() so that the terminal case can be tested afterwards.
     */
    private static class FaultyDatagramSocket extends DatagramSocket
    {
        private final AtomicInteger receiveCalls = new AtomicInteger(0);
        private final CountDownLatch reachedNormalReceive = new CountDownLatch(1);
        private final int failuresToInject;

        FaultyDatagramSocket(int failuresToInject) throws SocketException
        {
            super((SocketAddress) null);
            this.failuresToInject = failuresToInject;
        }

        @Override
        public void receive(DatagramPacket p) throws IOException
        {
            if (this.receiveCalls.incrementAndGet() <= this.failuresToInject)
            {
                throw new PortUnreachableException("injected transient fault");
            }

            this.reachedNormalReceive.countDown();

            while (!isClosed())
            {
                try
                {
                    Thread.sleep(20);
                }
                catch (InterruptedException e)
                {
                    throw new InterruptedIOException();
                }
            }

            throw new SocketException("socket closed");
        }
    }

    /**
     * Counts live threads sitting in NetworkProxy's reader loop.
     */
    private static int countReaderThreads()
    {
        int count = 0;

        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet())
        {
            for (StackTraceElement frame : entry.getValue())
            {
                if (frame.getClassName().startsWith("org.traincontrol.marklin.udp.NetworkProxy$ReadMessages"))
                {
                    count++;
                    break;
                }
            }
        }

        return count;
    }

    private static DatagramSocket socketOf(NetworkProxy p) throws Exception
    {
        Field socketField = NetworkProxy.class.getDeclaredField("socket");
        socketField.setAccessible(true);
        return (DatagramSocket) socketField.get(p);
    }

    private static void setSocket(NetworkProxy p, DatagramSocket s) throws Exception
    {
        Field socketField = NetworkProxy.class.getDeclaredField("socket");
        socketField.setAccessible(true);
        socketField.set(p, s);
    }

    /**
     * getIP must return a usable literal address.  It used to strip the slash out of
     * InetAddress.toString(), which is "hostname/literal" - correct for an address entered as a dotted
     * quad, where there is no hostname, but for a hostname it glued the two halves together into
     * something like "localhost127.0.0.1".  Every HTTP fetch then failed while the UDP socket, which
     * uses the InetAddress directly, carried on working.
     */
    @Test(priority = 0)
    public void testGetIpReturnsALiteralAddress() throws Exception
    {
        String ip = proxy.getIP();

        assertFalse(ip.contains("/"), "getIP must not contain a slash: " + ip);

        assertEquals(ip, java.net.InetAddress.getByName(null).getHostAddress(),
            "getIP should be the literal address, with no hostname glued on");
    }

    /**
     * The receive socket holds at least 1 MB, so frames arriving while the listener is held up wait there rather than
     * being dropped by the operating system, sensor changes included (OB-421).  The default is far smaller.
     *
     * MUTATION: in `NetworkProxy.openReceiveSocket`, take out `opened.setReceiveBufferSize(RECEIVE_BUFFER_BYTES)`.
     */
    @Test(priority = 0)
    public void testTheReceiveSocketHasRoomForAStall() throws Exception
    {
        assertTrue(support.Components.waitFor(() -> grantedKb(LOGGED) >= 0, 5000),
            "the listener's start line does not give the receive buffer it was granted: " + LOGGED);

        assertTrue(grantedKb(LOGGED) >= grantableKb(), "the receive socket holds " + grantedKb(LOGGED)
            + " KB, so a held-up listener loses the station's frames");
    }

    /**
     * The receive buffer the last listener start line gives, in KB, or -1 if there is none.  Read from the log rather
     * than the socket: `getReceiveBufferSize` waits for the listener's `receive` to return, which needs a frame.
     */
    private static int grantedKb(List<String> logged)
    {
        String[] around = I18n.f("network.canListenerRunning", "\u0001").split("\u0001");

        int kb = -1;

        synchronized (logged)
        {
            for (String line : logged)
            {
                if (line.startsWith(around[0]) && line.endsWith(around[1]))
                {
                    String digits = line.substring(around[0].length(), line.length() - around[1].length())
                        .replaceAll("[^0-9]", "");

                    if (!digits.isEmpty()) kb = Integer.parseInt(digits);
                }
            }
        }

        return kb;
    }

    /**
     * Recoverable receive errors must not end the listener.  This is the finding: a single one used to.
     */
    @Test(priority = 1)
    public void testTransientReceiveErrorDoesNotStopTheCanListener() throws Exception
    {
        long deadline = System.currentTimeMillis() + 5000;

        while (countReaderThreads() == 0 && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(50);
        }

        assertEquals(countReaderThreads(), 1,
            "precondition: exactly one CAN listener thread should be running");

        faulty = new FaultyDatagramSocket(3);

        DatagramSocket original = socketOf(proxy);

        // Install the faulty socket first, then close the real one to break the reader out of its
        // current receive().  The reader re-reads the field after the failure, so by then it is looking
        // at the faulty socket - which is open, and therefore a recoverable fault rather than a close.
        setSocket(proxy, faulty);
        original.close();

        assertTrue(faulty.reachedNormalReceive.await(15, TimeUnit.SECONDS),
            "the reader must survive the injected failures and go back to receiving");

        assertEquals(countReaderThreads(), 1,
            "A8: the CAN listener must still be running after a recoverable receive error");

        assertTrue(faulty.receiveCalls.get() > 3,
            "the reader should have retried past every injected failure");
    }

    /**
     * A closed socket is still terminal - the listener is meant to stop, not spin.
     */
    @Test(priority = 2, dependsOnMethods = "testTransientReceiveErrorDoesNotStopTheCanListener")
    public void testClosingTheSocketStopsTheListener() throws Exception
    {
        faulty.close();

        long deadline = System.currentTimeMillis() + 5000;

        while (countReaderThreads() > 0 && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(50);
        }

        assertEquals(countReaderThreads(), 0,
            "a closed socket must end the listener rather than spinning on it");
    }

    /**
     * sendMessage used to test for a closed socket only AFTER calling send(), which throws on a closed
     * socket - so the reopen was unreachable and transmission stayed broken for the rest of the
     * session.  The check now runs first.
     */
    @Test(priority = 3, dependsOnMethods = "testClosingTheSocketStopsTheListener")
    public void testSendReopensAClosedSocket() throws Exception
    {
        assertTrue(socketOf(proxy).isClosed(), "precondition: the socket is closed");

        LOGGED.clear();

        boolean sent = proxy.sendMessage(new CS2Message(CS2Message.CAN_CMD_PING, new byte[0]));

        assertTrue(sent, "A8: sending should reopen the socket and succeed");

        assertFalse(socketOf(proxy).isClosed(), "A8: the socket should have been reopened");

        // The same MUTATION as testTheReceiveSocketHasRoomForAStall: the reopen goes through the same door
        final int grantable = grantableKb();

        assertTrue(support.Components.waitFor(() -> grantedKb(LOGGED) >= grantable, 5000), "the reopened receive"
            + " socket's listener gives no receive buffer of 1 MB or more: " + LOGGED);

        // AND SOMETHING IS LISTENING ON IT AGAIN.  Asserting only that transmission came back reached the state the
        // class exists to prevent, "able to transmit but deaf", and measured it as a pass: no listener is started, so
        // no feedback, accessory echo or power change is ever seen again.  Given time to start, as the priority-2 test
        // waits.
        long deadline = System.currentTimeMillis() + 5000;

        while (countReaderThreads() == 0 && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(50);
        }

        assertEquals(countReaderThreads(), 1,
            "transmission came back and reception did not: the socket is open again and nothing is "
            + "listening on it, which is the deaf-but-transmitting state this class was hardened "
            + "against (W21-B2)");
    }
}
