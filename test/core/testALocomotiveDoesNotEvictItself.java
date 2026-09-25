package core;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * Renaming a locomotive does not take it off the railway.
 *
 * MT-149, filed critical. Adam: "place loc on station.  Rename it.  It is now gone from the station,
 * and in status ??? on the layout.  Rename it back, and it doesn't come back.  It should survive the
 * rename everywhere."
 *
 * **`sanitizeMultiUnits` evicts the locomotive it is asked about.** It walks every Point and clears any
 * that holds a locomotive not `isSimultaneousMultiUnitCompatible` with the one passed in - and that
 * method ends `return !this.hasEquivalentAddress(l)`, so a locomotive compared with ITSELF has an
 * equivalent address and is declared incompatible. Asked about a locomotive that is standing
 * somewhere, the sweep therefore takes it off the square it is standing on.
 *
 * Both rename doors call it straight after `renameLoc`, which is why a rename loses the placement -
 * and why renaming back does not bring it back: nothing restores a placement, so the second rename
 * finds nothing to evict and the train is simply gone. The "?????" on the panel is the same fact seen
 * from the other end: `getLocomotiveLocation` now returns null.
 *
 * **It bites on rename and not on placement**, which is why it has been there unnoticed:
 * `moveLocomotive` calls the same sweep BEFORE putting the locomotive down, so there is nothing of its
 * own to evict.
 *
 * A locomotive cannot conflict with itself, so the sweep skips it.
 *
 * MUTATION this catches: dropping the self test evicts the train and both assertions fail.
 *
 * @author Adam
 */
public class testALocomotiveDoesNotEvictItself
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static final String NAME = "SM sweeper";
    private static final String RENAMED = "SM sweeper renamed";

    private static final int ADDRESS = 73;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Before the model: init reads the layout preference and would otherwise open Adam's own
        // railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);
        model.stop();

        model.newMM2Locomotive(NAME, ADDRESS);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (model != null)
        {
            try { model.deleteLoc(RENAMED); } catch (Exception ignored) { }
            try { model.deleteLoc(NAME); } catch (Exception ignored) { }
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * The sweep leaves the locomotive it was asked about where it is.
     */
    @Test
    public void testTheSweepDoesNotTakeTheTrainItIsAskedAbout() throws Exception
    {
        Layout layout = layoutWithOnePlacedTrain();

        MarklinLocomotive loc = model.getLocByName(NAME);

        assertNotNull(layout.getLocomotiveLocation(loc),
            "precondition: the locomotive has to be on the graph for the sweep to take it off");

        layout.sanitizeMultiUnits(loc);

        assertNotNull(layout.getLocomotiveLocation(loc),
            "the multi-unit sweep took the locomotive off the square it was standing on, because "
            + "isSimultaneousMultiUnitCompatible ends in an address comparison and a locomotive has "
            + "the same address as itself.  Both rename doors call this straight after renaming");
    }

    /**
     * ...and a rename, which is the gesture that reaches it.
     *
     * The sweep is called by the WINDOW rather than by renameLoc, so this does what the window does,
     * in the order it does it - the rename, then the sweep - without needing one.
     */
    @Test
    public void testARenameLeavesTheTrainOnItsStation() throws Exception
    {
        Layout layout = layoutWithOnePlacedTrain();

        MarklinLocomotive loc = model.getLocByName(NAME);

        String was = layout.getLocomotiveLocation(loc).getName();

        assertTrue(model.renameLoc(NAME, RENAMED), "the rename itself failed");

        try
        {
            MarklinLocomotive renamed = model.getLocByName(RENAMED);

            assertNotNull(renamed, "the locomotive is not in the database under its new name");

            // What the window does next, and the whole of the fault.
            layout.sanitizeMultiUnits(renamed);

            assertNotNull(layout.getLocomotiveLocation(renamed),
                "the locomotive left the railway on being renamed - which is what Adam reported, and "
                + "renaming it back does not bring it back because nothing restores a placement");

            assertEquals(layout.getLocomotiveLocation(renamed).getName(), was,
                "the locomotive moved on being renamed");
        }
        finally
        {
            model.renameLoc(RENAMED, NAME);
        }
    }

    /**
     * Renaming a MEMBER of a multi-unit whose head stands on a station leaves the multi-unit on its station (BPV-A1, found
     * by the validator of the 2.8.2 backports, 2026-09-25).
     *
     * The self-skip above covers a train renamed where it stands.  A member is not where it stands - its head is - and the
     * sweep the window runs after a rename asks the head whether it is compatible with the renamed member.  A head is
     * never compatible with its own member (`isLinkedTo`), so the head was cleared from its station: the same loss as
     * MT-149, on the one rename the self-skip does not reach.  A rename changes no placement and no membership, so it
     * cannot make a conflict that was not there.
     *
     * Done as the window does it: the rename, then the sweep.  On a stopped railway - every edit door refuses while autonomy
     * runs, its coast-down and Return Home's planning included (`isAutonomyRunning`).
     *
     * MUTATION: sweep for a locomotive that stands nowhere, and this fails.
     *
     * @throws Exception from the model
     */
    @Test
    public void testRenamingAMemberLeavesItsMultiUnitOnItsStation() throws Exception
    {
        MarklinLocomotive head = model.newMM2Locomotive(HEAD, HEAD_ADDRESS);
        MarklinLocomotive member = model.newMM2Locomotive(MEMBER, MEMBER_ADDRESS);

        try
        {
            java.util.Map<String, Double> consist = new java.util.HashMap<>();

            consist.put(MEMBER, 1.0);

            assertEquals(head.setLinkedLocomotives(consist), 1, "precondition: the member could not be linked to the head");

            assertFalse(head.isSimultaneousMultiUnitCompatible(member), "precondition: a head counts as compatible with"
                + " its own member, so the sweep had nothing to take it off for");

            Layout layout = new Layout(model);

            MarklinFeedback first = model.newFeedback(8392, null);
            MarklinFeedback second = model.newFeedback(8393, null);

            model.setFeedbackState(first.getName(), false);
            model.setFeedbackState(second.getName(), false);

            layout.createPoint("MU A", true, first.getName());
            layout.createPoint("MU B", true, second.getName());
            layout.createEdge("MU A", "MU B");

            layout.getPoint("MU A").setLocomotive(head);

            assertTrue(model.renameLoc(MEMBER, MEMBER_RENAMED), "the rename itself failed");

            MarklinLocomotive renamed = model.getLocByName(MEMBER_RENAMED);

            assertNotNull(renamed, "the member is not in the database under its new name");

            // What the window does next.
            layout.sanitizeMultiUnits(renamed);

            assertNotNull(layout.getLocomotiveLocation(head), "renaming a member of a multi-unit took the multi-unit off"
                + " its station - the platform now reads as empty with the train standing on it (BPV-A1)");

            assertEquals(layout.getLocomotiveLocation(head).getName(), "MU A", "the multi-unit moved when a member was"
                + " renamed");
        }
        finally
        {
            try { model.deleteLoc(MEMBER_RENAMED); } catch (Exception ignored) { }
            try { model.deleteLoc(MEMBER); } catch (Exception ignored) { }
            try { model.deleteLoc(HEAD); } catch (Exception ignored) { }
        }
    }

    /**
     * A saved layout in which a multi-unit's head and one of its members both stand is loaded with only one of them
     * standing: the member is part of the head's train, and one train cannot stand in two places.
     *
     * The loader's half of BPV-A1's fix.  The edit doors now sweep only for a train that stands on the graph; the loader
     * asks the sweep before each train is put down, so it must keep asking regardless - or both would load standing.
     *
     * MUTATION: have the loader ask the edit doors' sweep, which skips a train not yet down, and this fails.
     *
     * @throws Exception from the model
     */
    @Test
    public void testALoadedLayoutDoesNotStandAHeadAndItsMemberBoth() throws Exception
    {
        MarklinLocomotive head = model.newMM2Locomotive(HEAD, HEAD_ADDRESS);
        MarklinLocomotive member = model.newMM2Locomotive(MEMBER, MEMBER_ADDRESS);

        try
        {
            java.util.Map<String, Double> consist = new java.util.HashMap<>();

            consist.put(MEMBER, 1.0);

            assertEquals(head.setLinkedLocomotives(consist), 1, "precondition: the member could not be linked to the head");

            Layout layout = new Layout(model);

            MarklinFeedback first = model.newFeedback(8394, null);
            MarklinFeedback second = model.newFeedback(8395, null);

            model.setFeedbackState(first.getName(), false);
            model.setFeedbackState(second.getName(), false);

            layout.createPoint("MU C", true, first.getName());
            layout.createPoint("MU D", true, second.getName());
            layout.createEdge("MU C", "MU D");

            // BOTH STANDING, as a file written by hand, or by an older build, can have them.
            layout.getPoint("MU C").setLocomotive(head);
            layout.getPoint("MU D").setLocomotive(member);

            layout.setDefaultLocSpeed(30);

            Layout loaded = Layout.fromJSON(layout.toJSON(), model);

            assertTrue(loaded.isValid(), "precondition: the saved layout does not load: " + loaded.getInvalidReason());

            boolean headStands = loaded.getLocomotiveLocation(head) != null;
            boolean memberStands = loaded.getLocomotiveLocation(member) != null;

            assertTrue(headStands || memberStands, "precondition: the load placed neither train, so it says nothing about"
                + " the two of them");

            assertFalse(headStands && memberStands, "a saved layout loaded with a multi-unit's head and one of its members"
                + " both standing - one train in two places");
        }
        finally
        {
            try { model.deleteLoc(MEMBER); } catch (Exception ignored) { }
            try { model.deleteLoc(HEAD); } catch (Exception ignored) { }
        }
    }

    private static final String HEAD = "SM head";
    private static final String MEMBER = "SM member";
    private static final String MEMBER_RENAMED = "SM member renamed";
    private static final int HEAD_ADDRESS = 74;
    private static final int MEMBER_ADDRESS = 75;

    /**
     * One station with one train standing on it.
     */
    private static Layout layoutWithOnePlacedTrain() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback first = model.newFeedback(8390, null);
        MarklinFeedback second = model.newFeedback(8391, null);

        model.setFeedbackState(first.getName(), false);
        model.setFeedbackState(second.getName(), false);

        layout.createPoint("SM A", true, first.getName());
        layout.createPoint("SM B", true, second.getName());
        layout.createEdge("SM A", "SM B");

        layout.getPoint("SM A").setLocomotive(model.getLocByName(NAME) != null
            ? model.getLocByName(NAME) : model.getLocByName(RENAMED));

        return layout;
    }
}
