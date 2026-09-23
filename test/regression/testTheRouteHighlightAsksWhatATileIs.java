package regression;

import java.util.Collections;
import javax.swing.SwingUtilities;
import static org.traincontrol.base.Accessory.accessoryDecoderType.DCC;
import static org.traincontrol.base.Accessory.accessoryDecoderType.MM2;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Highlight on Diagram lights a tile of the kind the address names, and not everything numbered alike
 * (MT-462).
 *
 * Adam, 2026-09-21, having run MT-462: *"(tested with route 7): links, unrelated routes (58), and S88s
 * (10) are highlighted.  Should be switches and signals and the S88 that triggers the route or is
 * involved in conditions only.  Seems the highlighting doesn't care about item type."*
 *
 * **It did not care.**  `TrainControlUI.highlightAddresses` matched on `getRawAddress()` alone, and
 * every kind of tile carries one: a page link's address is the page it points at, a route tile's is the
 * route's id, an s88's is the sensor. So a route commanding accessory 10 lit the s88 numbered 10, a
 * route commanding accessory 58 lit the tile for route 58, and a route whose own id was 7 lit every
 * link aimed at page 7. Three separate kinds of false positive from one missing question.
 *
 * **The scenario has the collision built in**, which is why it is used here: `single-switch` gives
 * WestEnd the s88 address 10 and the turnout the accessory address 1, so "accessory 10" is an address
 * nothing on the page answers to as an accessory and one that an s88 answers to.
 *
 * MUTATION: drop the `answersTo` test from the loop in `highlightAddresses` and the first claim goes
 * red - the s88 lights for an accessory address.
 *
 * @author Adam
 */
public class testTheRouteHighlightAsksWhatATileIs
{
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation model;

    private static TrainControlUI ui;

    /** The page this scenario draws, and the two squares whose addresses collide across kinds. */
    private static final String PAGE = "1 - Junction";

    private static final TileKey WEST_END = new TileKey(PAGE, 1, 3);

    private static final TileKey TURNOUT = new TileKey(PAGE, 5, 3);

    /** WestEnd's s88 address, and the turnout's accessory address, from the scenario's README. */
    private static final int S88_AT_WEST_END = 10;

    private static final int ACCESSORY_OF_THE_TURNOUT = 1;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the highlight is a drawing, and drawing needs a display");
        }

        // BEFORE init, which opens whatever the layout preference names - Adam's own railway on his
        // machine (OB-111).  The sandbox redirects it to a copy of the scenario.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        // showUI, because the registry this asks about is filled by the grid the window mounts.
        model = init(null, true, true, false, true);

        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so nothing here can reach the drawing path");

        settle();
        settle();

        assertFalse(ui.getDiagramTileRegistry().labelsFor(WEST_END).isEmpty(),
            "precondition: no label is registered for WestEnd, so nothing below could light anyway");

        assertFalse(ui.getDiagramTileRegistry().labelsFor(TURNOUT).isEmpty(),
            "precondition: no label is registered for the turnout");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * An accessory address that only a sensor carries lights nothing.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testASensorIsNotLitByAnAccessoryAddress() throws Exception
    {
        int lit = light(S88_AT_WEST_END, TrainControlUI.AddressedAs.ACCESSORY);

        assertEquals(lit, 0,
            "an accessory address lit " + lit + " tile(s) on a page whose only accessory is numbered "
            + ACCESSORY_OF_THE_TURNOUT + ".  What answered is the s88 numbered " + S88_AT_WEST_END
            + ", because the match was on the number alone - which is what Adam saw on MT-462: a route"
            + " commanding accessory 10 lighting the sensor 10, and a route numbered 7 lighting every"
            + " link aimed at page 7");

        assertFalse(flashing(WEST_END),
            "the sensor at WestEnd is flashing for an accessory address it does not answer to");
    }

    /**
     * The same address, asked of the sensors, lights the sensor.
     *
     * The control for the claim above: without it, a filter that refuses everything would pass.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheSameAddressAsASensorDoesLightIt() throws Exception
    {
        int lit = light(S88_AT_WEST_END, TrainControlUI.AddressedAs.FEEDBACK);

        assertTrue(lit > 0,
            "the sensor numbered " + S88_AT_WEST_END + " does not light when the address is asked of"
            + " the sensors, so the kind test refuses everything rather than telling the kinds apart");

        assertTrue(flashing(WEST_END), "the sensor at WestEnd did not flash");
    }

    /**
     * And an accessory address the turnout does answer to lights the turnout.
     *
     * The second control, on the other side: the first two claims together would also pass if
     * ACCESSORY lit nothing at all.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAnAccessoryAddressLightsTheAccessory() throws Exception
    {
        int lit = light(ACCESSORY_OF_THE_TURNOUT, TrainControlUI.AddressedAs.ACCESSORY);

        assertTrue(lit > 0,
            "the turnout numbered " + ACCESSORY_OF_THE_TURNOUT + " does not light for its own"
            + " accessory address, so Highlight on Diagram now lights nothing at all");

        assertTrue(flashing(TURNOUT), "the turnout did not flash");
    }

    /**
     * A three-way answers to its second decoder too, which is how a route sets its other diverging road (GUI-C5).
     *
     * `layout.switchThreeWayAddr` prints both addresses; only the first is the tile's logical address, so a route
     * commanding only the second lit nothing and said there was nothing to show.
     *
     * MUTATION: drop the three-way clause from `answersToAccessoryAddress` and this fails.
     *
     * @throws Exception building the tiles
     */
    @Test
    public void testAThreeWayAnswersToItsSecondDecoder() throws Exception
    {
        org.traincontrol.base.LayoutDiagramComponent threeWay = accessoryTile(
            org.traincontrol.base.LayoutDiagramComponent.componentType.SWITCH_THREE, 10, null);

        assertTrue(threeWay.answersToAccessoryAddress(11, MM2), "a three-way at 10 does not answer to 11, its"
            + " second decoder - a route commanding only that one lights nothing (GUI-C5)");

        assertTrue(threeWay.answersToAccessoryAddress(10, MM2), "control: a three-way at 10 does not answer to 10");

        assertFalse(threeWay.answersToAccessoryAddress(12, MM2), "a three-way at 10 answers to 12, which is"
            + " neither of its decoders");

        org.traincontrol.base.LayoutDiagramComponent plain = accessoryTile(
            org.traincontrol.base.LayoutDiagramComponent.componentType.SWITCH_LEFT, 10, null);

        assertFalse(plain.answersToAccessoryAddress(11, MM2), "an ordinary turnout at 10 answers to 11 - only a"
            + " three-way is two decoders");
    }

    /**
     * An address is one per protocol: an MM2 turnout 5 and a DCC turnout 5 are two decoders (GUI-C5).
     *
     * MUTATION: stop comparing the protocol in `answersToAccessoryAddress` and this fails.
     *
     * @throws Exception building the tiles
     */
    @Test
    public void testAnAddressIsOnePerProtocol() throws Exception
    {
        org.traincontrol.base.LayoutDiagramComponent dcc = accessoryTile(
            org.traincontrol.base.LayoutDiagramComponent.componentType.SWITCH_LEFT, 5, DCC);

        assertFalse(dcc.answersToAccessoryAddress(5, MM2), "a DCC turnout 5 answers to a command for MM2 5,"
            + " which is a different decoder - a route commanding one lit both (GUI-C5)");

        assertTrue(dcc.answersToAccessoryAddress(5, DCC), "control: a DCC turnout 5 does not answer to DCC 5");

        // A tile with no protocol is the implicit one, which is what the parser and a route command both read a
        // missing protocol as.
        org.traincontrol.base.LayoutDiagramComponent unstated = accessoryTile(
            org.traincontrol.base.LayoutDiagramComponent.componentType.SWITCH_LEFT, 5, null);

        assertTrue(unstated.answersToAccessoryAddress(5, MM2), "a turnout with no protocol does not answer to"
            + " MM2, which is what a missing protocol means everywhere else");

        assertFalse(unstated.answersToAccessoryAddress(5, DCC), "a turnout with no protocol answers to DCC");
    }

    /**
     * And the window asks the decoder: the scenario's MM2 turnout is not lit for a DCC command (GUI-C5).
     *
     * The claims above are about the tile; this is the door Highlight on Diagram uses.
     *
     * MUTATION: have `highlightAccessories` pass the addresses on without their protocols and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheHighlightAsksTheDecoder() throws Exception
    {
        endEveryFlash();

        final int[] lit = new int[2];

        SwingUtilities.invokeAndWait(() -> lit[0] = ui.highlightAccessories(
            Collections.singletonMap(ACCESSORY_OF_THE_TURNOUT, Collections.singleton(DCC)),
            org.traincontrol.util.ImageUtil.HIGHLIGHT, 4000));

        settle();

        assertEquals(lit[0], 0, "a command to DCC " + ACCESSORY_OF_THE_TURNOUT + " lit the scenario's MM2"
            + " turnout numbered alike, which is a different decoder (GUI-C5)");

        endEveryFlash();

        SwingUtilities.invokeAndWait(() -> lit[1] = ui.highlightAccessories(
            Collections.singletonMap(ACCESSORY_OF_THE_TURNOUT, Collections.singleton(MM2)),
            org.traincontrol.util.ImageUtil.HIGHLIGHT, 4000));

        settle();

        assertTrue(lit[1] > 0, "control: a command to MM2 " + ACCESSORY_OF_THE_TURNOUT + " does not light the"
            + " scenario's turnout, so the door refuses everything");
    }

    private static org.traincontrol.base.LayoutDiagramComponent accessoryTile(
        org.traincontrol.base.LayoutDiagramComponent.componentType type, int address,
        org.traincontrol.base.Accessory.accessoryDecoderType protocol) throws Exception
    {
        org.traincontrol.base.LayoutDiagramComponent tile =
            new org.traincontrol.base.LayoutDiagramComponent(type, 1, 1, 0, 0, 0, 0, protocol);

        tile.setLogicalAddress(address, protocol == null ? MM2 : protocol, false);

        return tile;
    }

    /**
     * Lights one address, of one kind, and lets the event thread finish.
     *
     * @param address the address
     * @param kind what it names
     * @return how many labels were lit
     * @throws Exception from the event thread
     */
    private static int light(final int address, final TrainControlUI.AddressedAs kind) throws Exception
    {
        endEveryFlash();

        final int[] lit = new int[1];

        SwingUtilities.invokeAndWait(() -> lit[0] = ui.highlightAddresses(
            Collections.singleton(address), kind, org.traincontrol.util.ImageUtil.HIGHLIGHT, 4000));

        settle();

        return lit[0];
    }

    /**
     * Whether any label on a square is holding a flash.
     *
     * @param square the square
     * @return whether one is
     */
    private static boolean flashing(TileKey square)
    {
        for (LayoutLabel label : ui.getDiagramTileRegistry().labelsFor(square))
        {
            if (label.isFlashOutstanding()) return true;
        }

        return false;
    }

    /**
     * Ends every flash the last claim started, so one cannot decide the next.
     *
     * @throws Exception from the event thread
     */
    private static void endEveryFlash() throws Exception
    {
        for (TileKey square : new TileKey[] {WEST_END, TURNOUT})
        {
            for (LayoutLabel label : ui.getDiagramTileRegistry().labelsFor(square))
            {
                final LayoutLabel ending = label;

                SwingUtilities.invokeAndWait(() -> ending.endFlash());
            }
        }

        settle();
    }

    /**
     * Lets the event thread finish what a gesture posted.
     *
     * @throws Exception from the event thread
     */
    private static void settle() throws Exception
    {
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> { });
    }
}
