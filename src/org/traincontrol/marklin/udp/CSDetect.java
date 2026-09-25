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
     * How many times the web check is tried on a host.
     *
     * The ping was retried and the web request was not, so a single slow reply - while ten threads
     * are pinging the subnet - threw away a station that had just answered its ping ("it works 9 out
     * of 10 times").  Three attempts turn a one-in-ten failure into a one-in-a-thousand one.
     *
     * Only hosts that answered a ping ever get here, so this does not lengthen the scan: the addresses
     * with nothing on them fail at the ping and are never asked twice.
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
    
    /**
     * The one ping every reachability check in this class is made of: a single attempt, waiting
     * NET_TIMEOUT_MS.  A field rather than a call so a test can stand in for a network that drops a
     * reply; nothing in the application sets it.
     */
    private static java.util.function.Predicate<String> ping = CSDetect::pingOnce;

    public static boolean isReachable(String host)
    {
        return ping.test(host);
    }

    private static boolean pingOnce(String host)
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
     * Whether there is a Central Station web server on this host, retried.
     *
     * The retry lives in the method both callers already use - the subnet scan and isCentralStation -
     * so neither has to remember it.
     *
     * @param host the host
     * @param path the path that identifies a Central Station
     * @return whether one answered
     */
    private static boolean checkWebServer(String host, String path)
    {
        for (int tries = 0; tries < WEB_RETRY; tries++)
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
