import java.nio.file.Path;
import java.nio.file.Paths;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test TrainControl save file loading
 */
public class testLoadData
{   
    // Test files stored locally
    private final String data2_5_16 =
        Paths.get(getClass().getResource("LocDB2_5_16.data").toURI()).toString();
    
    private final String data2_4_12 =
            Paths.get(getClass().getResource("LocDB2_4_12.data").toURI()).toString();

    private final String data2_6_5 =
            Paths.get(getClass().getResource("LocDB2_6_5.data").toURI()).toString();

    private final String data2_7_0 =
            Paths.get(getClass().getResource("LocDB2_7_0.data").toURI()).toString();

    private final String data2_3_3 =
            Paths.get(getClass().getResource("LocDB2_3_3.data").toURI()).toString();
    
    public static MarklinControlStation model;
            
    public testLoadData() throws Exception
    {
    }
   
    @Test
    public void testLoad2_4_12() throws Exception
    {   
        assertTrue(!model.restoreState(data2_4_12).isEmpty());
    }
    
    @Test
    public void testLoad2_6_5() throws Exception
    {   
        assertTrue(!model.restoreState(data2_6_5).isEmpty());
    }
    
    @Test
    public void testLoad2_7_0() throws Exception
    {   
        assertTrue(!model.restoreState(data2_7_0).isEmpty());
    }
    
    @Test
    public void testLoad2_5_16() throws Exception
    {   
        assertTrue(!model.restoreState(data2_5_16).isEmpty());
    }
    
    @Test
    public void testLoad2_3_3() throws Exception
    {   
        assertTrue(!model.restoreState(data2_3_3).isEmpty());
    }
        
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true); 
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
