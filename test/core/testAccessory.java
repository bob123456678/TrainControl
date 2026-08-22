package core;

import java.lang.reflect.Field;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.base.RemoteDeviceCollection;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.udp.CS2Message;

/**
 * Use testng 6.14.3
 */
public class testAccessory
{    
    public static MarklinControlStation model;
    
    public testAccessory()
    {
    }

    /**
     * Test accessory class functionality
     */
    @Test
    public void testAccessoryCreation()
    {   
        // Test with high numbers so the model deletes them automatically on restart
        model.newSignal(280, MarklinAccessory.accessoryDecoderType.MM2, true);
        
        MarklinAccessory signal1 = model.getAccessoryByName("Signal 280");
        MarklinAccessory signal2 = model.getAccessoryByAddress(280, MarklinAccessory.accessoryDecoderType.MM2);

        assertEquals(signal1.getName(), "Signal 280");
        assertEquals(signal1, signal2);
        assertEquals(signal1.getType(), MarklinAccessory.accessoryType.SIGNAL);
        assertEquals(signal1.isRed(), true);
        assertEquals(signal1.isGreen(), false);
        assertEquals(signal1.getDecoderType(), MarklinAccessory.accessoryDecoderType.MM2);

        // This should create a new signal in the DB
        model.newSignal(280, MarklinAccessory.accessoryDecoderType.DCC, false);
        signal1 = model.getAccessoryByName("Signal 280 DCC");

        assertTrue(model.getAccessoryByName("Signal 280 DCC") != model.getAccessoryByName("Signal 280"));
        assertEquals(signal1.getName(), "Signal 280 DCC");
        assertEquals(signal1.isGreen(), true);
        assertEquals(signal1.isRed(), false);
        assertEquals(signal1.isSignal(), true);
        assertEquals(signal1.isSwitch(), false);
        assertEquals(signal1.getDecoderType(), MarklinAccessory.accessoryDecoderType.DCC);

        // Test switching
        signal1.setSwitched(true);
        assertEquals(signal1.isSwitched(), true);
        signal1.setSwitched(false);
        assertEquals(signal1.isSwitched(), false);
        
        model.newSwitch(281, MarklinAccessory.accessoryDecoderType.MM2, false);
        MarklinAccessory switch1 = model.getAccessoryByAddress(281, MarklinAccessory.accessoryDecoderType.MM2);
        assertEquals(switch1.getType(), MarklinAccessory.accessoryType.SWITCH);
        assertEquals(switch1.isSwitched(), false);
        assertEquals(switch1.isTurned(), false);
        assertEquals(switch1.isStraight(), true);
        assertEquals(switch1.isSignal(), false);
        assertEquals(switch1.isSwitch(), true);
        
        // This switch should not exist
        MarklinAccessory createdAccessory = model.getAccessoryByName("Switch 3000");
        assertEquals(createdAccessory, null);
        
        // This will trigger the creation of the accessory
        model.getAccessoryState(3000, MarklinAccessory.accessoryDecoderType.DCC);
        createdAccessory = model.getAccessoryByName("Switch 3000 DCC");
        assertNotEquals(createdAccessory, null);
        assertEquals(createdAccessory.isSwitched(), false);
        assertEquals(createdAccessory.getType(), MarklinAccessory.accessoryType.SWITCH);
        
        // Non DCC version should not exist
        assertEquals(model.getAccessoryByName("Switch 3000"), null);
        
        assertFalse(createdAccessory.isValidAddress());
    }
    
    private static final MarklinAccessory.accessoryDecoderType MM2 =
        MarklinAccessory.accessoryDecoderType.MM2;

    /**
     * Empties one logical MM2 address, so a test can start from the state a fresh installation is in.
     * A real database is not clean - the keyboard registers an accessory at every address the operator
     * has ever scrolled past - so there is no reliably unused address to pick.
     *
     * Reaches accDB by reflection because there is no accessory delete on the model.  In-memory only;
     * nothing here calls saveState.
     */
    @SuppressWarnings("unchecked")
    private static RemoteDeviceCollection<MarklinAccessory, Integer> accDb() throws Exception
    {
        Field accDbField = MarklinControlStation.class.getDeclaredField("accDB");
        accDbField.setAccessible(true);

        return (RemoteDeviceCollection<MarklinAccessory, Integer>) accDbField.get(model);
    }

    private static void clearAccessoryAddress(int logicalAddress) throws Exception
    {
        // A Switch and a Signal at one address share a UID, so both names have to go
        accDb().delete("Switch " + logicalAddress);
        accDb().delete("Signal " + logicalAddress);

        assertNull(model.getAccessoryByName("Switch " + logicalAddress));
        assertNull(model.getAccessoryByName("Signal " + logicalAddress));
    }

    /**
     * Re-creating an accessory as the other type must not leave the old name behind.
     *
     * A switch and a signal at one address share a database id, so re-registering under the other
     * name used to leave the first one in the name map forever - listed by getItemNames, and still
     * resolving, to the new device.  This happens for real when a track diagram tile is changed from
     * a switch to a signal and syncLayouts re-creates the accessory.
     */
    @Test
    public void testChangingAccessoryTypeDoesNotStrandTheOldName() throws Exception
    {
        clearAccessoryAddress(285);

        model.newSwitch(285, MM2, false);
        assertTrue(accDb().getItemNames().contains("Switch 285"));

        // The layout now says this address is a signal
        MarklinAccessory signal = model.newSignal(285, MM2, false);

        assertEquals(signal.getType(), MarklinAccessory.accessoryType.SIGNAL);
        assertTrue(accDb().getItemNames().contains("Signal 285"));

        assertFalse(accDb().getItemNames().contains("Switch 285"),
            "the superseded name must not linger in the database");

        // Existing references to the old name still work, via the accessory type fallback
        assertEquals(model.getAccessoryByName("Switch 285"), signal);
    }

    /**
     * A signal and a switch at the same address are the same physical decoder, and the database is
     * keyed by address and protocol - so either name must find the one entry.  Autonomy edges and
     * routes refer to accessories by name, and previously a command naming the other type simply did
     * not resolve, leaving that accessory silently uncommanded.
     */
    @Test
    public void testSignalAndSwitchNamesResolveToTheSameDecoder()
    {
        MarklinAccessory signal = model.newSignal(281, MM2, false);

        assertEquals(model.getAccessoryByName("Signal 281"), signal);
        assertEquals(model.getAccessoryByName("Switch 281"), signal,
            "the switch name at the same address must resolve to the same decoder");
    }

    /**
     * Only the type prefix is interchangeable.  The address and any protocol suffix must still match
     * exactly, and a name that matches nothing under either type resolves to nothing.
     */
    @Test
    public void testOnlyTheAccessoryTypePrefixIsInterchangeable()
    {
        MarklinAccessory dcc = model.newSwitch(284, MarklinAccessory.accessoryDecoderType.DCC, false);

        // The protocol suffix is part of the identity - MM2 284 and DCC 284 are different decoders
        assertEquals(model.getAccessoryByName("Signal 284 DCC"), dcc);
        assertNotEquals(model.getAccessoryByName("Signal 284"), dcc,
            "the MM2 name must not resolve to the DCC accessory at the same address");

        assertNull(model.getAccessoryByName("Switch 99999"));
        assertNull(model.getAccessoryByName("Not an accessory"));
    }

    /**
     * An edge configuration command naming a signal at an address that already holds a switch must be
     * accepted - they are the same decoder.  It used to be rejected as an address conflict, which left
     * the autonomy edge referring to a name that never resolved.
     */
    @Test
    public void testSignalCommandResolvesToAnExistingSwitch() throws Exception
    {
        model.newSwitch(282, MM2, false);
        assertNotNull(model.getAccessoryByName("Switch 282"));

        Edge.validateConfigCommand("Signal 282", "red", model);

        assertEquals(model.getAccessoryByName("Signal 282"), model.getAccessoryByName("Switch 282"),
            "the command should resolve to the decoder already at that address");
    }

    /**
     * And at a genuinely unused address the accessory is created, as the requested type, with no
     * spurious extra accessory left behind at a neighbouring address.
     */
    @Test
    public void testSignalIsCreatedAtAnUnusedAddress() throws Exception
    {
        clearAccessoryAddress(283);
        clearAccessoryAddress(282);

        Edge.validateConfigCommand("Signal 283", "red", model);

        MarklinAccessory created = model.getAccessoryByName("Signal 283");

        assertNotNull(created, "the signal should have been created");
        assertEquals(created.getType(), MarklinAccessory.accessoryType.SIGNAL);

        assertNull(model.getAccessoryByName("Signal 282"),
            "creating an accessory must not register one at the address below it");
    }

    @Test
    public void testRouteCommand() throws Exception
    {   
        model.newSwitch(285, MarklinAccessory.accessoryDecoderType.MM2, false);
        MarklinAccessory createdAccessory = model.getAccessoryByAddress(285, MarklinAccessory.accessoryDecoderType.MM2);
        
        // Test copying an accessory setting
        RouteCommand rc = RouteCommand.fromLine(createdAccessory.toAccessorySettingString(), false);
        assertTrue(rc.isAccessory());
        assertFalse(rc.isAutonomyLightsOn());
        assertFalse(rc.isFunction());
        assertFalse(rc.isFunctionsOff());
        assertFalse(rc.isStop());
        assertFalse(rc.isLightsOn());
        assertFalse(rc.isLocomotiveSpeed());
        assertEquals(rc.getAddress(), 285);
        assertFalse(rc.getSetting());
        
        // Test hypothetical setting
        RouteCommand rc2 = RouteCommand.fromLine(createdAccessory.toAccessorySettingString(true, MarklinAccessory.accessoryDecoderType.MM2.toString()), false);
        assertTrue(rc2.getSetting());
        
        // Test import and export
        String line = rc.toLine(createdAccessory);
        RouteCommand rc3 = RouteCommand.fromLine(line, false);
        assertEquals(rc, rc3);
        
        String line2 = rc2.toLine(createdAccessory);
        RouteCommand rc4 = RouteCommand.fromLine(line2, false);
        assertEquals(rc2, rc4);
    }
    
    @Test
    public void testSwitchingViaModel() throws Exception
    {  
        MarklinAccessory switch400 = model.getAccessoryByAddress(400, MarklinAccessory.accessoryDecoderType.DCC);
        
        model.setAccessoryState(400, MarklinAccessory.accessoryDecoderType.DCC, true);
        assertTrue(switch400.isSwitched());
        assertTrue(model.getAccessoryState(400, MarklinAccessory.accessoryDecoderType.DCC));

        model.setAccessoryState(400, MarklinAccessory.accessoryDecoderType.DCC, false);
        assertFalse(switch400.isSwitched());
        assertFalse(model.getAccessoryState(400, MarklinAccessory.accessoryDecoderType.DCC));
        
        MarklinAccessory switch1 = model.getAccessoryByAddress(1, MarklinAccessory.accessoryDecoderType.MM2);
        model.setAccessoryState(1, MarklinAccessory.accessoryDecoderType.MM2, true);
        assertTrue(switch1.isSwitched());
        
        switch1 = model.getAccessoryByAddress(1, MarklinAccessory.accessoryDecoderType.MM2);
        assertTrue(switch1.isSwitched());
        switch1 = model.getAccessoryByAddress(1, MarklinAccessory.accessoryDecoderType.MM2);
        System.out.println(switch1);

        // TODO - by name must differentiate between MM and DCC
        model.newSwitch(1, MarklinAccessory.accessoryDecoderType.MM2, true);

        switch1 = model.getAccessoryByName(switch1.isSwitch() ? "Switch 1" : "Signal 1");
        assertTrue(switch1.isSwitched());
        assertTrue(model.getAccessoryState(1, MarklinAccessory.accessoryDecoderType.MM2));
    }
    
    @Test
    public void testAddressValidation() throws Exception
    {  
        MarklinAccessory switchNeg = model.getAccessoryByAddress(-1, MarklinAccessory.accessoryDecoderType.MM2);
        MarklinAccessory switch0 = model.getAccessoryByAddress(0, MarklinAccessory.accessoryDecoderType.MM2);
        
        // Model will reject switch creation for invalid addresses
        assertNull(switchNeg);
        assertNull(switch0);
        
        // nothing should happen for the same reason as above
        model.setAccessoryState(-1, MarklinAccessory.accessoryDecoderType.MM2, true);
        model.setAccessoryState(0, MarklinAccessory.accessoryDecoderType.MM2, true);
        
        // This will be allowed
        switch0 = model.newSwitch(0, MarklinAccessory.accessoryDecoderType.MM2, true);
        switchNeg = model.newSignal(-1, MarklinAccessory.accessoryDecoderType.MM2, true);
        
        assertNotNull(switchNeg);
        assertNotNull(switch0);

        MarklinAccessory switch1 = model.getAccessoryByAddress(1, MarklinAccessory.accessoryDecoderType.MM2);
        MarklinAccessory switch255 = model.getAccessoryByAddress(255, MarklinAccessory.accessoryDecoderType.MM2);
        MarklinAccessory switch256 = model.getAccessoryByAddress(256, MarklinAccessory.accessoryDecoderType.MM2);
        MarklinAccessory switch257 = model.getAccessoryByAddress(257, MarklinAccessory.accessoryDecoderType.DCC);
        MarklinAccessory switch319 = model.getAccessoryByAddress(319, MarklinAccessory.accessoryDecoderType.DCC);
        MarklinAccessory switch320 = model.getAccessoryByAddress(320, MarklinAccessory.accessoryDecoderType.DCC); 
        MarklinAccessory switch321 = model.getAccessoryByAddress(321, MarklinAccessory.accessoryDecoderType.DCC);

        MarklinAccessory switch2047 = model.getAccessoryByAddress(2047, MarklinAccessory.accessoryDecoderType.DCC);
        MarklinAccessory switch2048 = model.getAccessoryByAddress(2048, MarklinAccessory.accessoryDecoderType.DCC);
        MarklinAccessory switch2049 = model.getAccessoryByAddress(2049, MarklinAccessory.accessoryDecoderType.DCC);

        assertFalse(switchNeg.isValidAddress());
        assertFalse(switch0.isValidAddress());
        assertTrue(switch1.isValidAddress());
        assertTrue(switch255.isValidAddress());
        assertTrue(switch256.isValidAddress());
        assertTrue(switch257.isValidAddress());
        assertTrue(MarklinAccessory.isValidDCCAddress(switch256.getAddress()));
        assertTrue(MarklinAccessory.isValidMM2Address(switch256.getAddress()));
        assertTrue(MarklinAccessory.isValidMM2Address(switch319.getAddress()));
        assertTrue(MarklinAccessory.isValidMM2Address(switch320.getAddress()));
        assertFalse(MarklinAccessory.isValidMM2Address(switch321.getAddress()));
        assertTrue(switch2047.isValidAddress());
        assertTrue(switch2048.isValidAddress());
        assertFalse(switch2049.isValidAddress());
        assertFalse(MarklinAccessory.isValidDCCAddress(switch2049.getAddress()));
        assertTrue(MarklinAccessory.isValidDCCAddress(switch2048.getAddress()));
    }
    
    /**
     * A full 13-byte accessory echo, as it arrives off the wire.
     *
     * The short accessoryEcho below is enough to drive parseMessage directly, but receiveMessage only
     * routes a message whose RESPONSE bit is set - the low bit of the second byte - and that bit cannot
     * be set through the outgoing constructor. So this one is assembled by hand.
     */
    private static CS2Message rawAccessoryEcho(int uid, int setting)
    {
        byte[] raw = new byte[CS2Message.MESSAGE_LENGTH];

        // Command in the top seven bits of byte 1, response flag in its low bit
        raw[1] = (byte) ((CS2Message.CMD_ACC_SWITCH << 1) | 1);
        raw[2] = 0x47;
        raw[3] = 0x11;
        raw[4] = 6;

        raw[5] = (byte) (uid >> 24);
        raw[6] = (byte) (uid >> 16);
        raw[7] = (byte) (uid >> 8);
        raw[8] = (byte) uid;
        raw[9] = (byte) setting;
        raw[10] = 1;

        return new CS2Message(raw);
    }

    /**
     * receiveMessage hands accessory work to an executor, so state changes arrive asynchronously.
     */
    private static boolean waitUntil(java.util.function.BooleanSupplier condition) throws Exception
    {
        for (int i = 0; i < 100; i++)
        {
            if (condition.getAsBoolean())
            {
                return true;
            }

            Thread.sleep(50);
        }

        return false;
    }

    /**
     * An accessory echo for an accessory that has been removed from the database must be ignored.
     *
     * receiveMessage used to test `hasId` and then call `getById` three separate times; an accessory
     * deleted in between - `restoreState` drops any with an invalid address, while the CAN listener is
     * already running - made the third lookup return null and threw inside the executor.
     *
     * **What this test can and cannot show.** The executor captures that exception in its `Future`,
     * which nobody reads, so the failure is invisible from outside and the pre-fix code would NOT have
     * failed the middle assertion here. What it does pin is the surrounding behaviour: the echo reaches
     * a live accessory, an echo for a deleted one does not throw *synchronously* on the caller's thread,
     * and the pipeline keeps working afterwards. The synchronous part matters - if this handling is ever
     * made synchronous, that exception lands on the CAN reader thread, which is exactly how A8 killed
     * the listener.
     */
    @Test(timeOut = 30000)
    public void testEchoForADeletedAccessoryIsIgnored() throws Exception
    {
        clearAccessoryAddress(291);

        MarklinAccessory acc = model.newSwitch(291, MarklinAccessory.accessoryDecoderType.MM2, false);
        int uid = acc.getUID();

        assertFalse(acc.isSwitched(), "created straight");

        // The echo really does reach a live accessory through receiveMessage.  Setting 0 means turned.
        // Each message below uses a different setting from the one before it, because receiveMessage
        // discards a packet identical to the previous one.
        model.receiveMessage(rawAccessoryEcho(uid, 0));

        assertTrue(waitUntil(() -> acc.isSwitched()), "the echo must reach the accessory");

        // Now remove it and echo again.  This is the state the guard exists for.
        accDb().delete(acc.getName());

        assertNull(model.getAccessoryByName(acc.getName()), "precondition: the accessory is gone");

        model.receiveMessage(rawAccessoryEcho(uid, 1));

        // And the pipeline is still alive afterwards
        MarklinAccessory replacement = model.newSwitch(291, MarklinAccessory.accessoryDecoderType.MM2, false);

        model.receiveMessage(rawAccessoryEcho(replacement.getUID(), 0));

        assertTrue(waitUntil(() -> replacement.isSwitched()),
            "a later echo must still be delivered after one arrived for a deleted accessory");
    }

    /**
     * Builds the CAN accessory echo the Central Station sends back for an accessory command.
     * Setting 0 means turned, 1 means straight (see MarklinAccessory.parseMessage).
     */
    private static CS2Message accessoryEcho(int uid, int setting)
    {
        return new CS2Message(CS2Message.CMD_ACC_SWITCH, new byte[]
        {
            (byte) (uid >> 24), (byte) (uid >> 16), (byte) (uid >> 8), (byte) uid,
            (byte) setting, 1
        });
    }

    /**
     * An accessory is not confirmed at any position until the Central Station has echoed it.
     *
     * isConfirmedAt used to compare against stateAtLastActuation alone, which is seeded from the
     * ASSUMED startup state - so it returned true for any accessory whose assumption already matched
     * the command, and the first autonomy path to set a switch to the position it was believed to be
     * in passed validation without the Central Station having acknowledged anything at all.
     *
     * The second part matters just as much: an echo that does NOT move the accessory still confirms
     * it.  Only state-changing echoes used to count, so an accessory commanded to the position it was
     * already in would never have become confirmed under the stricter rule.
     */
    @Test
    public void testAccessoryIsNotConfirmedUntilTheCentralStationEchoes() throws Exception
    {
        clearAccessoryAddress(290);

        MarklinAccessory acc = model.newSwitch(290, MarklinAccessory.accessoryDecoderType.MM2, false);

        assertFalse(acc.isSwitched(), "created straight");

        // The assumed state agrees with "straight", and that alone used to count as confirmation
        assertFalse(acc.isConfirmedAt(false), "nothing has been echoed yet, so nothing is confirmed");
        assertFalse(acc.isConfirmedAt(true));

        // An echo of the position it was already believed to be in confirms it without moving it
        acc.parseMessage(accessoryEcho(acc.getUID(), 1));

        assertTrue(acc.isConfirmedAt(false), "the Central Station has now acknowledged this accessory");
        assertFalse(acc.isConfirmedAt(true));

        // An echo that does move it advances the confirmed position
        acc.parseMessage(accessoryEcho(acc.getUID(), 0));

        assertTrue(acc.isConfirmedAt(true));
        assertFalse(acc.isConfirmedAt(false));
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        testAccessory.model = init(null, true, false, false, false);
        model.stop();
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

    /**
     * Which settings throw the accessory, and setState agreeing with the answer.
     *
     * Two callers depend on this mapping: setState acts on it, and the autonomy path configuration
     * orders an edge's commands by it, so that a three-way turnout has its diverging drive commanded
     * only after the other has been released - never both at once, which routes nowhere.
     *
     * Pinned because inverting it is invisible: every accessory would still be commanded, just in the
     * wrong order, and only a three-way would show it - on the layout rather than in a test.
     */
    @Test
    public void testWhichSettingsThrowAnAccessory() throws Exception
    {
        assertTrue(Accessory.isThrow(Accessory.accessorySetting.TURN), "TURN throws");
        assertTrue(Accessory.isThrow(Accessory.accessorySetting.RED), "RED throws");

        assertFalse(Accessory.isThrow(Accessory.accessorySetting.STRAIGHT), "STRAIGHT releases");
        assertFalse(Accessory.isThrow(Accessory.accessorySetting.GREEN), "GREEN releases");

        // And the other caller has to agree with it, or ordering by one while acting on the other would
        // put the commands in exactly the wrong sequence.  Address picked clear of the ones the rest of
        // this class uses, since nothing here tears accessories down.
        MarklinAccessory acc = model.newSwitch(295, MarklinAccessory.accessoryDecoderType.MM2, false);

        for (Accessory.accessorySetting setting : Accessory.accessorySetting.values())
        {
            assertTrue(acc.setState(setting), setting + " must be a valid setting");

            assertEquals(acc.isSwitched(), Accessory.isThrow(setting),
                "setState(" + setting + ") must leave the accessory in the state isThrow reports");
        }
    }
}
