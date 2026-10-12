package core;

import java.util.HashMap;
import java.util.Map;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class testTwoCentralStationMultiUnitsShareAMember
{
    private static MarklinControlStation model;

    /**
     * OPENED BEFORE THE MODEL, because `init` loads whatever layout the machine has - which on
     * Adam's is his real railway (OB-111).
     *
     * This class is about locomotives and needs no layout at all, which is exactly the case that
     * gets forgotten: nothing here reads a page, so nothing here fails when the page is his.
     * `testSwitchingToACentralStationLayout` counts the classes that skip this and would not let
     * a fifty-sixth one through.
     */
    private static support.LayoutSandbox sandbox;

    private static final String[] TEST_LOCS = { "X8 member", "X8 mu one", "X8 mu two", "X8 linked" };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        model = init(null, true, false, false, false);
        model.stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            for (String name : TEST_LOCS)
            {
                if (model != null) model.deleteLoc(name);
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    private static MarklinLocomotive loco(String name, int address) throws Exception
    {
        MarklinLocomotive l = model.getLocByName(name);

        if (l == null) l = model.newMM2Locomotive(name, address);

        return l;
    }

    /** A Central Station multi-unit holding the named members. */
    private static MarklinLocomotive centralStationMultiUnit(String name, int muAddress,
        String... members) throws Exception
    {
        MarklinLocomotive l = loco(name, 1);

        model.changeLocAddress(name, muAddress, Locomotive.decoderType.MULTI_UNIT);

        l = model.getLocByName(name);

        Map<String, Double> names = new HashMap<>();

        for (String m : members) names.put(m, 1.0);

        l.setModelMultiUnitLocomotives(names);

        return l;
    }

    @Test
    public void twoCentralStationMultiUnitsSharingAMemberAreSeenAsCompatible() throws Exception
    {
        MarklinLocomotive member = loco("X8 member", 60);

        MarklinLocomotive muOne = centralStationMultiUnit("X8 mu one", 4001, "X8 member");
        MarklinLocomotive muTwo = centralStationMultiUnit("X8 mu two", 4002, "X8 member");

        assertEquals(muOne.getDecoderType(), Locomotive.decoderType.MULTI_UNIT,
            "precondition: mu one is a Central Station multi-unit");
        assertEquals(muTwo.getDecoderType(), Locomotive.decoderType.MULTI_UNIT,
            "precondition: mu two is a Central Station multi-unit");

        assertTrue(muOne.getModelMultiUnitLocomotives().contains(member),
            "precondition: mu one holds the member");
        assertTrue(muTwo.getModelMultiUnitLocomotives().contains(member),
            "precondition: mu two holds the member");

        // Control: a predicate that refused everything would satisfy the two claims below.
        assertFalse(muOne.isSimultaneousMultiUnitCompatible(member),
            "control: a multi-unit and its own member are never simultaneously compatible");

        assertFalse(muOne.isSimultaneousMultiUnitCompatible(muTwo),
            "two multi-units that drive the same locomotive must not be run as two trains");
        assertFalse(muTwo.isSimultaneousMultiUnitCompatible(muOne),
            "and the same asked the other way round");
    }

    /**
     * The mixed case, which the caller's both-directions call already covers - here as the control
     * that says the gap above is specific to a MULTI_UNIT on the LEFT of the comparison.
     */
    @Test
    public void aLinkedHeadAndAMultiUnitSharingAMemberAreCaught() throws Exception
    {
        MarklinLocomotive member = loco("X8 member", 60);
        MarklinLocomotive linked = loco("X8 linked", 61);

        Map<String, Double> links = new HashMap<>();
        links.put(member.getName(), 1.0);
        linked.preSetLinkedLocomotives(links);
        linked.setLinkedLocomotives();

        assertTrue(linked.getLinkedLocomotives().containsKey(member),
            "precondition: the link was made");

        MarklinLocomotive mu = centralStationMultiUnit("X8 mu one", 4001, "X8 member");

        assertFalse(linked.isSimultaneousMultiUnitCompatible(mu),
            "asked with the linked head on the left, the shared member is found");

        assertFalse(mu.isSimultaneousMultiUnitCompatible(linked),
            "and asked with the multi-unit on the left it must be found too");
    }
}
