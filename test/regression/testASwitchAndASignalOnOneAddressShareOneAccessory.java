package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Tiles of both kinds on one decoder address, a switch and a signal, all hold the database's accessory and follow its
 * echo, on a CS2 and on a CS3: CAN does not care which kind the accessory is (OB-405).
 *
 * The fixture `test/cs3_v261` is a CS3 plus (2.6.1) export: W 13 (double slip, Main Layout (13,8) and (14,8)) and D 2
 * (signal, (15,8)) are both DCC 2048; a second signal at 2048 is added on Startpackung (15,0), another page.  The
 * database starts with "Signal 2048 DCC" and keeps it, so no tile holds an object the station's echoes never reach.
 * The station type is set by reflection, as a sync's detection sets it.
 *
 * MUTATION: drop `!this.mixedAddresses.contains(targetAddress)` from `wireComponents`' re-create condition, and the
 * slip tiles hold an accessory that is not the database's, and the echo does not move them.
 *
 * @author Adam
 */
public class testASwitchAndASignalOnOneAddressShareOneAccessory
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE init, which syncs whatever layout the preference names.
        sandbox = support.LayoutSandbox.open(new File("test/cs3_v261"));
        Files.write(new File(sandbox.getFolder(), "config/gleisbilder/Startpackung.cs2").toPath(),
            ("element\n .id=0x1000f\n .typ=signal\n .artikel=4096\n"
                + "element\n .id=0x1000e\n .typ=dreiwegweiche\n .artikel=400\n"
                + "element\n .id=0x1000d\n .typ=signal\n .artikel=402\n").getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.APPEND);
        model = support.TestStation.simulated();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        support.StandInStation.tearDown(null, model, sandbox);
    }

    private static LayoutDiagramComponent tile(String page, int x, int y)
    {
        LayoutDiagram d = model.getLayout(page);
        assertNotNull(d, "the fixture's " + page + " page was synced");
        return d.getComponent(x, y);
    }

    private static LayoutDiagramComponent[] atDCC2048()
    {
        return new LayoutDiagramComponent[] {tile("Main Layout", 13, 8), tile("Main Layout", 14, 8),
            tile("Main Layout", 15, 8), tile("Startpackung", 15, 0)};
    }

    /**
     * Syncs the pages again, as the given station, from a database that holds a signal at DCC 2048 and at
     * MM2 201 and the three-way's switch at MM2 200, and gives the accessory at DCC 2048.
     */
    private static MarklinAccessory sync(boolean cs3) throws Exception
    {
        support.StandInStation.setCS3(model, cs3);
        model.newSignal(2048, accessoryDecoderType.DCC, false);
        model.newSwitch(200, accessoryDecoderType.MM2, false);
        model.newSignal(201, accessoryDecoderType.MM2, false);

        for (int pass = 0; pass < 2; pass++)
        {
            model.refreshLayouts();
        }

        return model.getAccessoryByAddressIfPresent(2048, accessoryDecoderType.DCC);
    }

    /**
     * On a CS2 and on a CS3, after two syncs, every tile at DCC 2048 on both pages holds the database's accessory,
     * which is still the signal "Signal 2048 DCC", and the station's echo moves them all.
     */
    @Test
    public void testEveryTileHoldsTheDatabasesAccessoryAndFollowsItsEcho() throws Exception
    {
        for (boolean cs3 : new boolean[] {false, true})
        {
            MarklinAccessory db = sync(cs3);
            String station = cs3 ? "CS3: " : "CS2: ";

            assertEquals(db.getName(), "Signal 2048 DCC", station + "the database's accessory is not renamed");
            assertTrue(db.isSignal(), station + "nor changed in kind");

            for (LayoutDiagramComponent c : atDCC2048())
            {
                assertSame(c.getAccessory(), db, station + c.getX() + "," + c.getY() + " holds "
                    + c.getAccessory().getName());
            }

            for (boolean turned : new boolean[] {!db.isSwitched(), db.isSwitched()})
            {
                model.receiveMessage(support.Packets.accessoryEcho(db, turned));

                assertTrue(support.Settle.until(() -> tile("Main Layout", 13, 8).getAccessory().isSwitched() == turned,
                    10000), station + "the slip follows the echo to " + (turned ? "turned" : "straight"));

                for (LayoutDiagramComponent c : atDCC2048())
                {
                    assertEquals(c.getAccessory().isSwitched(), turned, station + c.getX() + "," + c.getY());
                }
            }
        }
    }

    /**
     * A three-way's second address with a signal tile on it (MM2 200 and 201 on Startpackung (14,0), the signal at
     * (13,0)), on a CS2 and on a CS3: the three-way's second accessory and the signal tile hold the database's object at
     * 201, which stays the signal it was, after two syncs.
     *
     * MUTATION: drop `!this.mixedAddresses.contains(targetAddress + 1)` from `wireComponents`, and the three-way
     * re-creates 201 as a switch.
     */
    @Test
    public void testAThreeWaysSecondAddressIsSharedToo() throws Exception
    {
        for (boolean cs3 : new boolean[] {false, true})
        {
            sync(cs3);

            MarklinAccessory db = model.getAccessoryByAddressIfPresent(201, accessoryDecoderType.MM2);
            String station = cs3 ? "CS3: " : "CS2: ";

            assertTrue(db.isSignal(), station + "201 is still the signal, " + db.getName());
            assertSame(tile("Startpackung", 14, 0).getAccessory2(), db, station + "the three-way's second accessory");
            assertSame(tile("Startpackung", 13, 0).getAccessory(), db, station + "the signal tile");
        }
    }

    /**
     * At an address with tiles of one kind the accessory follows the tile's kind, on a CS2 and a CS3:
     * S 1 (MM2 4), a signal tile, held in the database as a switch, is re-created as a signal, as when the station
     * changes an article's kind.
     *
     * MUTATION: count every address as mixed (`this.mixedAddresses = kinds.keySet()` in `syncLayouts`), and S 1 stays a
     * switch.
     */
    @Test
    public void testAKindChangedOnTheStationIsStillTaken() throws Exception
    {
        for (boolean cs3 : new boolean[] {false, true})
        {
            support.StandInStation.setCS3(model, cs3);
            model.newSwitch(4, accessoryDecoderType.MM2, false);
            model.refreshLayouts();

            MarklinAccessory db = model.getAccessoryByAddressIfPresent(4, accessoryDecoderType.MM2);

            assertTrue(db.isSignal(), (cs3 ? "CS3" : "CS2") + ": S 1 re-created as a signal, " + db.getName());
            assertSame(tile("Startpackung", 12, 7).getAccessory(), db, "the signal tile holds it");
        }
    }
}
