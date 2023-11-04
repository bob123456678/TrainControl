/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/EmptyTestNGTest.java to edit this template
 */

import base.Locomotive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import marklin.MarklinControlStation;
import static marklin.MarklinControlStation.init;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author adamo
 */
public class testLocDB {
    
    public static MarklinControlStation model;
    
    public testLocDB()
    {
    }

    /**
     * Adding and deleting a locomotive from the database
     */
    @Test
    public void testAddAndDeleteLoc()
    {   
        int numLocs = model.getLocList().size();
        String locName = "New locomotive test";
        int address = 10;
        
        model.newMFXLocomotive(locName, address);

        assert model.getLocByName(locName) != null;   
        assert model.getLocList().size() == numLocs + 1;
        assert model.getLocByName(locName).getAddress() == address;
        assert model.getLocByName(locName).getName().equals(locName);
        assert model.getLocList().contains(locName);
        
        model.deleteLoc(locName);
     
        assert model.getLocByName(locName) == null;
        assert model.getLocList().size() == numLocs;
        assert !model.getLocList().contains(locName);
    }
    
    /**
     * Adding and deleting a locomotive from the database
     */
    @Test
    public void testRenameLoc()
    {   
        String locName = "New locomotive test 2";
        String locName2 = "New locomotive test 2 copy";
        
        model.newMFXLocomotive(locName, 20);
        model.deleteLoc(locName2);

        model.renameLoc(locName, locName2);
                
        assert model.getLocByName(locName) == null;
        assert model.getLocByName(locName2) != null;
        assert locName2.equals(model.getLocByName(locName2).getName());
    }
    
    /**
     * Test the lists of locomotives
     */
    @Test
    public void testLocList()
    {   
        String locName = "New locomotive test 2";
        
        model.newMFXLocomotive(locName, 20);
        
        List<String> locNames = model.getLocList();
        
        List<String> locNames2 = new ArrayList<>();
        
        for (Locomotive l : model.getLocomotives())
        {
            locNames2.add(l.getName());
        }
               
        assertEquals(locNames.containsAll(locNames2), true);
        assertEquals(locNames2.containsAll(locNames), true);
        assertEquals(locNames2.size(), locNames.size());
        
        model.deleteLoc(locName);
        
        assertEquals(model.getLocByName(locName), null);
    }
    
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        testLocDB.model = init(null, true, false, false, false); 
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
