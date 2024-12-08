package org.traincontrol.marklin.udp;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.traincontrol.marklin.file.CS2File;

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
    
    public static boolean isCentralStation(String host)
    {
        return checkWebServer(host, CS2File.getLayoutMasterURL(""));
    }

    public static String detectCentralStation()
    {
        for (String subnet : getLocalSubnet())
        {
            System.out.println("Detected local subnet " + subnet);

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
                        System.out.println("\n" + host + " is reachable");
                        if (checkWebServer(host, urlPath))
                        {
                            System.out.println("Web server found at: " + host + urlPath);
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
                        System.out.println("Central station detected at: " + result);
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
    
    private static boolean checkWebServer(String host, String path)
    {
        try
        {
            URL url = new URL("http://" + host + path);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
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

        return false;
    }
}
