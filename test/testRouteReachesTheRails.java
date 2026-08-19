import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.CommandRow;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A route built the way the new editor builds one, executed, and checked where it lands: the rails.
 *
 * Everything else about the new route editor is tested one layer up - a command survives being shown
 * as columns, a row rebuilds the command it came from. This asks the question those cannot: when the
 * route actually runs, does the right accessory move?
 *
 * That question could not be asked before Adam pointed out `DEBUG_SIMULATE_PACKETS`. With the network
 * off, debug on and that flag set, every outgoing CAN message is echoed back as though a Central
 * Station had answered - so an accessory command reaches the model's own view of the layout and can be
 * read back. It is a loopback, not a Central Station: it proves the command was formed and dispatched
 * correctly, not that real hardware would obey it.
 *
 * The two bugs this exists for both lived in `CommandRow`, and both were invisible until Save:
 * a DCC accessory silently became MM2, and every per-command delay was dropped. MM2 and DCC are
 * separate address spaces, so the first is not "the switch fails to move" - it is a DIFFERENT switch
 * moving. This is the test that would have caught it at the level a user would meet it.
 */
public class testRouteReachesTheRails
{
    private static MarklinControlStation model;
    private static boolean wasSimulating;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        wasSimulating = MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        // Echo outgoing CAN messages back as received ones.  Needs debug mode, and needs the network
        // OFF - the loopback lives in the branch exec() takes when it cannot transmit.
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        model = init(null, true, false, false, true);

        model.setNetworkCommState(false);
    }

    @AfterClass
    public static void tearDownClass()
    {
        // Restored, because it is process-global: leaving it set would make every later class in the
        // same JVM think its commands were being answered.
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = wasSimulating;
    }

    /**
     * A DCC accessory commanded through a route moves the DCC one, not the MM2 one.
     *
     * The bug this pins, stated as a user would meet it: a route containing "Switch 3 DCC, turn",
     * opened in the new editor and saved unchanged, threw MM2 switch 3 instead - a different piece of
     * railway, or a phantom one if no such accessory exists.
     */
    @Test
    public void testADccRouteCommandMovesTheDccAccessory() throws Exception
    {
        // Both protocols at the same numeric address, which is the case that tells them apart
        model.newSwitch(240, Accessory.accessoryDecoderType.DCC, false);
        model.newSwitch(240, Accessory.accessoryDecoderType.MM2, false);

        assertFalse(model.getAccessoryState(240, Accessory.accessoryDecoderType.DCC),
            "the DCC switch should start straight");
        assertFalse(model.getAccessoryState(240, Accessory.accessoryDecoderType.MM2),
            "and so should the MM2 one");

        // Built as the editor builds it: a row, carrying its protocol, turned back into a command
        CommandRow row = new CommandRow(CommandRow.Kind.ACCESSORY, "240", "turn",
            Accessory.accessoryDecoderType.DCC, 0);

        runAsRoute("DCC reaches the rails", row.toCommand());

        assertTrue(model.getAccessoryState(240, Accessory.accessoryDecoderType.DCC),
            "the DCC switch at 240 was not thrown, so the route did not reach the accessory it names");

        assertFalse(model.getAccessoryState(240, Accessory.accessoryDecoderType.MM2),
            "the MM2 switch at 240 moved instead.  MM2 and DCC are separate address spaces - this is "
            + "not a switch failing to throw, it is the WRONG switch throwing, on a layout where both "
            + "exist");
    }

    /**
     * And an MM2 command still moves the MM2 one, so the test above is not passing by accident.
     */
    @Test
    public void testAnMm2RouteCommandMovesTheMm2Accessory() throws Exception
    {
        model.newSwitch(241, Accessory.accessoryDecoderType.DCC, false);
        model.newSwitch(241, Accessory.accessoryDecoderType.MM2, false);

        CommandRow row = new CommandRow(CommandRow.Kind.ACCESSORY, "241", "turn",
            Accessory.accessoryDecoderType.MM2, 0);

        runAsRoute("MM2 reaches the rails", row.toCommand());

        assertTrue(model.getAccessoryState(241, Accessory.accessoryDecoderType.MM2),
            "the MM2 switch at 241 was not thrown");

        assertFalse(model.getAccessoryState(241, Accessory.accessoryDecoderType.DCC),
            "the DCC switch at 241 moved instead");
    }

    /**
     * Every command in a route arrives, in order, when they are a mixture of protocols.
     *
     * The realistic shape: a layout with both kinds of decoder, and a route that sets several of each.
     * A protocol carried per command has to stay attached to ITS command - carrying one protocol for
     * the whole route would pass both tests above and fail here.
     */
    @Test
    public void testAMixedProtocolRouteArrivesWhole() throws Exception
    {
        for (int address = 242; address <= 245; address++)
        {
            model.newSwitch(address, Accessory.accessoryDecoderType.DCC, false);
            model.newSwitch(address, Accessory.accessoryDecoderType.MM2, false);
        }

        List<RouteCommand> commands = new LinkedList<>();

        commands.add(new CommandRow(CommandRow.Kind.ACCESSORY, "242", "turn",
            Accessory.accessoryDecoderType.DCC, 0).toCommand());
        commands.add(new CommandRow(CommandRow.Kind.ACCESSORY, "243", "turn",
            Accessory.accessoryDecoderType.MM2, 0).toCommand());
        commands.add(new CommandRow(CommandRow.Kind.ACCESSORY, "244", "turn",
            Accessory.accessoryDecoderType.DCC, 0).toCommand());
        commands.add(new CommandRow(CommandRow.Kind.ACCESSORY, "245", "turn",
            Accessory.accessoryDecoderType.MM2, 0).toCommand());

        runAsRoute("mixed protocols", commands.toArray(new RouteCommand[0]));

        assertTrue(model.getAccessoryState(242, Accessory.accessoryDecoderType.DCC), "242 DCC");
        assertTrue(model.getAccessoryState(243, Accessory.accessoryDecoderType.MM2), "243 MM2");
        assertTrue(model.getAccessoryState(244, Accessory.accessoryDecoderType.DCC), "244 DCC");
        assertTrue(model.getAccessoryState(245, Accessory.accessoryDecoderType.MM2), "245 MM2");

        assertFalse(model.getAccessoryState(242, Accessory.accessoryDecoderType.MM2),
            "242's MM2 twin moved, so the protocol did not stay attached to its own command");
        assertFalse(model.getAccessoryState(243, Accessory.accessoryDecoderType.DCC),
            "243's DCC twin moved");
    }

    /**
     * A per-command delay is honoured rather than dropped.
     *
     * Timed rather than inspected, because "the field survived" is what the round-trip tests already
     * cover.  What a user cares about is that the route takes longer to run - which is the whole point
     * of a delay: a slow point motor settling before the next command draws current.
     *
     * Generous bounds.  This is asserting that the delays happen at all, not that they are precise:
     * three commands at 400ms cannot finish in under a second, and a machine under load must not make
     * the test fail.
     */
    @Test
    public void testDelaysAreActuallyWaited() throws Exception
    {
        for (int address = 246; address <= 248; address++)
        {
            model.newSwitch(address, Accessory.accessoryDecoderType.MM2, false);
        }

        List<RouteCommand> commands = new LinkedList<>();

        for (int address = 246; address <= 248; address++)
        {
            commands.add(new CommandRow(CommandRow.Kind.ACCESSORY, String.valueOf(address), "turn",
                Accessory.accessoryDecoderType.MM2, 400).toCommand());
        }

        long start = System.currentTimeMillis();

        runAsRoute("delays are waited", commands.toArray(new RouteCommand[0]));

        long took = System.currentTimeMillis() - start;

        assertTrue(took >= 1000,
            "three commands with 400ms delays ran in " + took + "ms.  The delays were dropped, which "
            + "is a route that still lists and still runs and now fires everything at once - the "
            + "timing a layout depends on to keep a slow point motor from being overtaken");

        for (int address = 246; address <= 248; address++)
        {
            assertTrue(model.getAccessoryState(address, Accessory.accessoryDecoderType.MM2),
                address + " did not throw, so the delay stopped the command rather than spacing it");
        }
    }

    /**
     * Builds a route from commands, runs it, and waits for it to finish.
     *
     * execRoute runs on its own thread so the interface can update, so this polls rather than assuming
     * the route is done when the call returns.
     */
    private static void runAsRoute(String name, RouteCommand... commands) throws Exception
    {
        List<RouteCommand> list = new LinkedList<>();

        for (RouteCommand rc : commands) list.add(rc);

        if (model.getRouteList().contains(name)) model.deleteRoute(name);

        model.newRoute(name, list, 0, org.traincontrol.marklin.MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
            false, null);

        model.getRoute(name).execRoute(false);

        // Wait for it to START before waiting for it to finish.
        //
        // execRoute spawns a thread and sets the flag inside it, so polling isExecuting() straight
        // away sees false, and a wait that treats that as "already done" deletes the route out from
        // under the thread that is about to run it.  That is what the first version of this did, and
        // every assertion failed with the accessory untouched - which looks exactly like the product
        // bug it is testing for.
        long startBy = System.currentTimeMillis() + 5000;

        while (!model.getRoute(name).isExecuting() && System.currentTimeMillis() < startBy)
        {
            Thread.sleep(10);
        }

        // Bounded: a route that never finishes must fail the assertions rather than hang the suite
        long deadline = System.currentTimeMillis() + 30000;

        while (model.getRoute(name).isExecuting() && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20);
        }

        model.deleteRoute(name);
    }
}
