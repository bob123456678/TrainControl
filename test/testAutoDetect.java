import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import marklin.MarklinControlStation;
import marklin.MarklinLocomotive;
import marklin.udp.CSDetect;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 */
public class testAutoDetect
{    
    public static MarklinControlStation model;
    public static MarklinLocomotive l;
    
    // Expected IP of the central station
    public static String targetIP = "192.168.50.25";
    
    // How many times to repeat each test
    public static int numAttempts = 1;
    
    // Configuration for the detection class
    public static int TEST_NET_TIMEOUT_MS = 200;
    public static int TEST_CONCURRENCY = 10;
    public static int TEST_WEB_TIMEOUT_MS = 500;
    public static int TEST_PING_RETRY = 2;
    
    public testAutoDetect()
    {
        
    }
    
    /**
     * Test locomotive address validation
     */
    @Test
    public void testAutoDetect()
    {  
        int success = 0;
        List<Long> times = new ArrayList<>();
        
        for (int i = 0; i < numAttempts; i++)
        {
            long start = System.currentTimeMillis();
            if (targetIP.equals(CSDetect.detectCentralStation())) success++;
            times.add(System.currentTimeMillis() - start);
        }
        
        System.out.println("Average time to auto detect: " + times.stream().mapToLong(Long::longValue).average().orElse(0) + " ms");
        
        assertEquals(success, numAttempts);
    }
    
    /**
     * Test ICMP based ping
     */
    @Test
    public void testPing()
    {  
        int success = 0;
        
        for (int i = 0; i < numAttempts; i++)
        {
            if (CSDetect.isReachable(targetIP, CSDetect.PING_RETRY)) success++;
        }
        
        assertEquals(success, numAttempts);
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Once tests reliably pass, input these values into the class
        // Modify NET_TIMEOUT_MS
        Field netTimeoutField = CSDetect.class.getDeclaredField("NET_TIMEOUT_MS");
        netTimeoutField.setAccessible(true);
        Field netTimeoutModifiersField = Field.class.getDeclaredField("modifiers");
        netTimeoutModifiersField.setAccessible(true);
        netTimeoutModifiersField.setInt(netTimeoutField, netTimeoutField.getModifiers() & ~Modifier.FINAL);
        netTimeoutField.set(null, TEST_NET_TIMEOUT_MS);

        // Modify THREAD_POOL_SIZE
        Field threadPoolSizeField = CSDetect.class.getDeclaredField("THREAD_POOL_SIZE");
        threadPoolSizeField.setAccessible(true);
        Field threadPoolSizeModifiersField = Field.class.getDeclaredField("modifiers");
        threadPoolSizeModifiersField.setAccessible(true);
        threadPoolSizeModifiersField.setInt(threadPoolSizeField, threadPoolSizeField.getModifiers() & ~Modifier.FINAL);
        threadPoolSizeField.set(null, TEST_CONCURRENCY);

        // Modify WEB_TIMEOUT_MS
        Field webTimeoutField = CSDetect.class.getDeclaredField("WEB_TIMEOUT_MS");
        webTimeoutField.setAccessible(true);
        Field webTimeoutModifiersField = Field.class.getDeclaredField("modifiers");
        webTimeoutModifiersField.setAccessible(true);
        webTimeoutModifiersField.setInt(webTimeoutField, webTimeoutField.getModifiers() & ~Modifier.FINAL);
        webTimeoutField.set(null, TEST_WEB_TIMEOUT_MS);
        
        Field pingRetryField = CSDetect.class.getDeclaredField("PING_RETRY");
        pingRetryField.setAccessible(true);
        Field pingRetryMofidiersField = Field.class.getDeclaredField("modifiers");
        pingRetryMofidiersField.setAccessible(true);
        pingRetryMofidiersField.setInt(pingRetryField, pingRetryField.getModifiers() & ~Modifier.FINAL);
        pingRetryField.set(null, TEST_PING_RETRY);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception
    {
    }

    @AfterMethod
    public void tearDownMethod() throws Exception
    {
    }
}
