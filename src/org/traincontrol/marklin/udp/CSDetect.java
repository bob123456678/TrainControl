package org.traincontrol.marklin.udp;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.util.I18n;

/**
 * This class attempts to automatically detect a central station on the network
 * @author Adam
 */
public class CSDetect
{
    // How long we wait for web requests to complete
    public static final int WEB_TIMEOUT_MS = 500;
    
    // How long we wait for network pings to complete
    public static final int NET_TIMEOUT_MS = 200;
    
    // Concurrent requests to send
    public static final int THREAD_POOL_SIZE = 10;
    
    // Sometimes pings fail.  How many times do we retry?
    public static final int PING_RETRY = 2;

    /**
     * How many times the web check is tried on a host that has already answered a ping (MT-060).
     *
     * Adam, 2026-08-27: "it works 9 out of 10 times.  sometimes I see this without a positive
     * detection: 192.168.50.25 is reachable .......... and then it just goes on.  if I try again, it
     * gets redetected."
     *
     * The ping was retried and the web request was not, so a single timeout threw away a station that
     * had just answered. Three attempts turn a one-in-ten failure into a one-in-a-thousand one.
     *
     * Only reachable hosts ever get here, so this does not lengthen the scan: the two hundred and
     * fifty addresses with nothing on them fail at the ping and are never asked twice.
     */
    public static final int WEB_RETRY = 3;
    
    public static boolean isCentralStation(String host)
    {
        return checkWebServer(host, CS2File.getLayoutMasterURL(""));
    }

    public static String detectCentralStation()
    {
        for (String subnet : getLocalSubnet())
        {
            System.out.println(I18n.f("network.detectedLocalSubnet", subnet));

            String urlPath = CS2File.getLayoutMasterURL("");
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 1; i < 255; i++)
            {
                final String host = subnet + i;
                
                Future<String> future = executor.submit(() ->
                {
                    System.out.print(".");
                    
                    //System.out.println("Testing " + host);
                    if (isReachable(host, PING_RETRY))
                    {
                        System.out.println("\n" + I18n.f("network.hostReachable", host));
                        if (checkWebServer(host, urlPath))
                        {
                            System.out.println(I18n.f("network.webServerFoundAt", host, urlPath));
                            executor.shutdownNow();
                            return host;
                        }
                    }
                    
                    return null;
                });
                
                futures.add(future);
            }

            executor.shutdown();

            // Loop through the results
            for (Future<String> future : futures)
            {
                try
                {
                    String result = future.get();
                    if (result != null)
                    {
                        System.out.println(I18n.f("network.centralStationDetectedAt", result));
                        return result;
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }
    
    public static boolean hasLocalSubnets()
    {
        return !getLocalSubnet().isEmpty();
    }

    private static List<String> getLocalSubnet()
    {
        List<String> out = new ArrayList<>();
        
        try
        {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements())
            {
                NetworkInterface networkInterface = interfaces.nextElement();

                if (networkInterface.isLoopback() || !networkInterface.isUp())
                {
                    continue;
                }

                List<InterfaceAddress> addresses = networkInterface.getInterfaceAddresses();
                for (InterfaceAddress address : addresses)
                {
                    InetAddress inetAddress = address.getAddress();
                    InetAddress broadcast = address.getBroadcast();

                    if (inetAddress.isSiteLocalAddress() && broadcast != null)
                    {
                        // Check for default gateway by sending a ping
                        if (isReachable(inetAddress.getHostAddress().substring(0, inetAddress.getHostAddress().lastIndexOf('.')) + ".1"))
                        {
                            String ip = inetAddress.getHostAddress();
                            out.add(ip.substring(0, ip.lastIndexOf('.') + 1));
                        }
                    }
                }
            }
        }
        catch (SocketException e)
        {
            e.printStackTrace();
        }
        
        return out;
    }
    
    public static boolean isReachable(String host)
    {
        try
        {
            InetAddress inet = InetAddress.getByName(host);
            return inet.isReachable(NET_TIMEOUT_MS);
        }
        catch (Exception e)
        {            
            return false;
        }
    }

    public static boolean isReachable(String host, int attempts)
    {
        for (int i = 0; i < attempts; i++)
        {
            if (isReachable(host))
            {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Whether there is a Central Station web server on this host, retried (MT-060).
     *
     * The retry lives in the name every caller already uses, rather than in a new one beside it. A
     * second method would have to be remembered at each call site, and a rule that has to be
     * remembered is a rule that gets left out - which is how the last several defects here arrived.
     *
     * @param host the host, which may carry a port for a test
     * @param path the path that identifies a Central Station
     * @return whether one answered
     */
    public static boolean checkWebServer(String host, String path)
    {
        return checkWebServer(host, path, WEB_RETRY);
    }

    /**
     * The same, with the number of attempts named - the way in for a test (MT-060).
     *
     * A fake server that times out once and then answers is the whole behaviour under test, and it
     * cannot be built against a real Central Station. `host` may therefore carry a port, which costs
     * nothing: the scan passes a bare address and the URL reads the same either way.
     *
     * @param host the host, which may carry a port
     * @param path the path that identifies a Central Station
     * @param attempts how many times to ask before giving up
     * @return whether one answered
     */
    public static boolean checkWebServer(String host, String path, int attempts)
    {
        for (int tries = 0; tries < Math.max(1, attempts); tries++)
        {
            if (askWebServer(host, path)) return true;
        }

        return false;
    }

    private static boolean askWebServer(String host, String path)
    {
        HttpURLConnection connection = null;

        try
        {
            URL url = new URL("http://" + host + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(WEB_TIMEOUT_MS);
            connection.setReadTimeout(WEB_TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            //System.out.println(url);
            //System.out.println(responseCode);
            if (responseCode == 200)
            {
                /*BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null)
                {
                    content.append(inputLine);
                }
                in.close();
                System.out.println("Content: " + content.toString());*/
                return true;
            }
        }
        catch (Exception e)
        {
            // Ignore unreachable hosts
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }

        return false;
    }
    
    public static boolean isVNCAvailable(String host)
    {
        return isPortOpen(host, 5900, WEB_TIMEOUT_MS);
    }

    private static boolean isPortOpen(String host, int port, int timeout)
    {
        try (Socket socket = new Socket())
        {
            SocketAddress socketAddress = new InetSocketAddress(host, port);
            socket.connect(socketAddress, timeout);
            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }
}
