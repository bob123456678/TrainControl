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

    /**
     * A member of a standing multi-unit given, in Change Name or Address, the address of another train that stands takes
     * that other train off the graph - as putting the multi-unit down again would.
     *
     * The member stands nowhere, but it is driven: every command to its head reaches its decoder, and now the other
     * train's too.  BPV-A1's fix swept only for an edited train standing on the graph, so this edit took nothing off,
     * and autonomy would run the other train as a train of its own while the multi-unit's commands moved it (RLA-B1).
     *
     * MUTATION: sweep only for the edited train where it stands, and this fails.
     *
     * @throws Exception from the model
     */
    @Test
    public void testReAddressingAMemberOntoAStandingTrainTakesThatTrainOff() throws Exception
    {
        MarklinLocomotive head = model.newMM2Locomotive(HEAD, HEAD_ADDRESS);
        MarklinLocomotive member = model.newMM2Locomotive(MEMBER, MEMBER_ADDRESS);
        MarklinLocomotive other = model.newMM2Locomotive(OTHER, OTHER_ADDRESS);

        try
        {
            java.util.Map<String, Double> consist = new java.util.HashMap<>();

            consist.put(MEMBER, 1.0);

            assertEquals(head.setLinkedLocomotives(consist), 1, "precondition: the member could not be linked to the head");

            Layout layout = new Layout(model);

            MarklinFeedback first = model.newFeedback(8396, null);
            MarklinFeedback second = model.newFeedback(8397, null);

            model.setFeedbackState(first.getName(), false);
            model.setFeedbackState(second.getName(), false);

            layout.createPoint("MU E", true, first.getName());
            layout.createPoint("MU F", true, second.getName());
            layout.createEdge("MU E", "MU F");

            layout.getPoint("MU E").setLocomotive(head);
            layout.getPoint("MU F").setLocomotive(other);

            model.changeLocAddress(MEMBER, OTHER_ADDRESS, MarklinLocomotive.decoderType.MM2);

            assertTrue(head.isLinkedTo(member), "precondition: the address change took the member out of its multi-unit,"
                + " so the head no longer drives the other train's decoder");

            assertFalse(head.isSimultaneousMultiUnitCompatible(other), "precondition: the head still counts as"
                + " compatible with the train whose address its member now has");

            // What the window does next.
            layout.sanitizeMultiUnits(member);

            assertNotNull(layout.getLocomotiveLocation(head), "re-addressing a member took its own multi-unit off its"
                + " station (BPV-A1)");

            assertNull(layout.getLocomotiveLocation(other), "a member of a standing multi-unit now has the address of a"
                + " train standing on MU F, and both still stand - autonomy would run that train as its own while every"
                + " command to the multi-unit moves it (RLA-B1)");
        }
        finally
        {
            try { model.deleteLoc(OTHER); } catch (Exception ignored) { }
            try { model.deleteLoc(MEMBER); } catch (Exception ignored) { }
            try { model.deleteLoc(HEAD); } catch (Exception ignored) { }
        }
    }

    /**
     * The same for a Central Station multi-unit: a member given, in Change Name or Address, the address of another train
     * that stands takes that train off the graph (RLA-B1, RLD2-C6).
     *
     * A Central Station multi-unit commands its members through the Central Station's own list, not TrainControl's
     * links, so the sweep finds it as a head by that list.  The claim beside this one links in TrainControl and cannot see
     * that half.
     *
     * MUTATION: find heads by TrainControl's links alone, and this fails.
     *
     * @throws Exception from the model
     */
    @Test
    public void testReAddressingAMemberOfACentralStationMultiUnitTakesTheStandingTrainOff() throws Exception
    {
        MarklinLocomotive member = model.newMM2Locomotive(CS_MEMBER, 83);
        MarklinLocomotive other = model.newMM2Locomotive(CS_OTHER, 84);
        MarklinLocomotive head = model.newMM2Locomotive(CS_HEAD, 1);

        try
        {
            model.changeLocAddress(CS_HEAD, 4010, MarklinLocomotive.decoderType.MULTI_UNIT);

            head = model.getLocByName(CS_HEAD);

            java.util.Map<String, Double> members = new java.util.HashMap<>();

            members.put(CS_MEMBER, 1.0);

            head.setModelMultiUnitLocomotives(members);

            assertTrue(head.getModelMultiUnitLocomotives().contains(member), "precondition: the Central Station multi-unit"
                + " does not hold its member");

            assertFalse(head.isLinkedTo(member), "precondition: the member is linked in TrainControl as well, so this says"
                + " nothing about the Central Station's own list");

            Layout layout = new Layout(model);

            MarklinFeedback first = model.newFeedback(8398, null);
            MarklinFeedback second = model.newFeedback(8399, null);

            model.setFeedbackState(first.getName(), false);
            model.setFeedbackState(second.getName(), false);

            layout.createPoint("MU G", true, first.getName());
            layout.createPoint("MU H", true, second.getName());
            layout.createEdge("MU G", "MU H");

            layout.getPoint("MU G").setLocomotive(head);
            layout.getPoint("MU H").setLocomotive(other);

            model.changeLocAddress(CS_MEMBER, 84, MarklinLocomotive.decoderType.MM2);

            // What the window does next.
            layout.sanitizeMultiUnits(model.getLocByName(CS_MEMBER));

            assertNotNull(layout.getLocomotiveLocation(head), "re-addressing a member took its own Central Station"
                + " multi-unit off its station");

            assertNull(layout.getLocomotiveLocation(other), "a member of a standing Central Station multi-unit now has the"
                + " address of a train standing on MU H, and both still stand (RLA-B1)");
        }
        finally
        {
            try { model.deleteLoc(CS_OTHER); } catch (Exception ignored) { }
            try { model.deleteLoc(CS_MEMBER); } catch (Exception ignored) { }
            try { model.deleteLoc(CS_HEAD); } catch (Exception ignored) { }
        }
    }

    private static final String CS_HEAD = "SM cs head";
    private static final String CS_MEMBER = "SM cs member";
    private static final String CS_OTHER = "SM cs other";

    private static final String HEAD = "SM head";
    private static final String MEMBER = "SM member";
    private static final String MEMBER_RENAMED = "SM member renamed";
    private static final String OTHER = "SM other";
    private static final int HEAD_ADDRESS = 74;
    private static final int MEMBER_ADDRESS = 75;
    private static final int OTHER_ADDRESS = 76;

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
