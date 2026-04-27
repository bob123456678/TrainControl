import java.net.URISyntaxException;
import java.util.List;
import org.traincontrol.marklin.file.CS2File;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;

/**
 * Tests CS3 parsing using a simulated web server
 */
public class testParseWebServer
{   
    private String cs3_loks;
    private String cs3_loks_v260;

    public List<MarklinLocomotive> loksCS3;
    public List<MarklinLocomotive> loksCS2;
    public List<MarklinLocomotive> loksCS2_fromCS3;
    public List<MarklinLocomotive> loksCS3_v260;
    
    private String cs3_mags;
    private String cs3_automatics;
    private String cs3_automatics_v260;

    //public MarklinControlStation model;
    public CS2File parser;
    
    /**
     * Utility function to get a locomotive from the list
     * @param lst
     * @param name
     * @return 
     */
    public static MarklinLocomotive getLocByName(List<MarklinLocomotive> lst, String name)
    {
        if (lst != null)
        {
            for (MarklinLocomotive l : lst)
            {
                try
                {
                    if (l.getName().equals(name)) return l;
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
                
        return null;
    }
            
    @BeforeClass
    public void init() throws URISyntaxException
    {
        this.cs3_loks_v260 = getClass().getResource("CS3_loks_v260.json").toURI().toString();
        this.cs3_loks = getClass().getResource("CS3_loks.json").toURI().toString();
               
        this.cs3_mags = getClass().getResource("CS3_mags.json").toURI().toString();
        this.cs3_automatics = getClass().getResource("CS3_automatics.json").toURI().toString();
        this.cs3_automatics_v260 = getClass().getResource("CS3_automatics_v260.json").toURI().toString();     
    }
    
    
    /**
     * Tests the parser end-to-end by simulating a web server
     * @throws Exception 
     */
    @Test
    public void testParseCS3LoksServer() throws Exception
    {
        CS3TestServer server = new CS3TestServer(cs3_loks, cs3_loks_v260, cs3_mags, cs3_automatics, cs3_automatics_v260);

        // Simulate firmware version
        server.startServer(260);   // or 250
        
        parser = new CS2File("localhost:8080", null);
        
        List<MarklinLocomotive> db = parser.parseLocomotivesCS3();
        List<MarklinRoute> routes260 = parser.parseRoutesCS3();
        
        assertEquals(getLocByName(db, "ICE 3 406").getAddress(), 1);
        assertEquals(getLocByName(db, "ICE 3 406").getDecoderType(), MarklinLocomotive.decoderType.MM2);
        assertEquals(db.size(), 154);

        assertEquals(getLocByName(db, "ES44 x2").getDecoderType(), MarklinLocomotive.decoderType.MULTI_UNIT);
        assertEquals(getLocByName(db, "MF+ER").getDecoderType(), MarklinLocomotive.decoderType.MULTI_UNIT);
        assertEquals(getLocByName(db, "SBB420  Red/Cargo").getDecoderType(), MarklinLocomotive.decoderType.MULTI_UNIT);
        assertEquals(getLocByName(db, "Test TC").getDecoderType(), MarklinLocomotive.decoderType.MULTI_UNIT);
        
        assertEquals(true, parser.isCS3Version260OrAbove());
        
        server.stopServer();
        
        // Test older versions
        server.startServer(250);
        assertEquals(false, parser.isCS3Version260OrAbove());
        
        db = parser.parseLocomotivesCS3();
        List<MarklinRoute> routes250 = parser.parseRoutesCS3();

        assertEquals(getLocByName(db, "ICE 3 406").getAddress(), 1);
        assertEquals(getLocByName(db, "ICE 3 406").getDecoderType(), MarklinLocomotive.decoderType.MM2);
        assertEquals(db.size(), 136);

        assertEquals(getLocByName(db, "ES44 x2").getDecoderType(), MarklinLocomotive.decoderType.MULTI_UNIT);
        assertEquals(getLocByName(db, "MF+ER").getDecoderType(), MarklinLocomotive.decoderType.MULTI_UNIT);
        assertEquals(getLocByName(db, "SBB420  Red/Cargo").getDecoderType(), MarklinLocomotive.decoderType.MULTI_UNIT);
        assertEquals(getLocByName(db, "Test TC").getDecoderType(), MarklinLocomotive.decoderType.MULTI_UNIT);
        
        assertEquals(routes250.size(), routes260.size());
        assertEquals(!routes250.isEmpty(), true);

        server.stopServer();
    }
      
    
    @BeforeClass
    public static void setUpClass() throws Exception
    {

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
