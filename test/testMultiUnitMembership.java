import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * What happens to a multi-unit when one of its members is deleted, re-addressed, or renamed.
 *
 * A consist stores Locomotive REFERENCES, not names - linkedLocomotives is a
 * Map&lt;Locomotive, Double&gt;.  Every operation that removes or re-keys a locomotive therefore has to
 * decide what to do about consists that point at it, and the three operations do not agree:
 *
 *  - deleteLoc unlinks the locomotive from every consist referencing it.
 *  - changeLocAddress deliberately does NOT, because it is a re-key of a locomotive that still
 *    exists; it rebuilds every consist afterwards instead, which also revalidates them.
 *  - renameLoc does neither, and re-keys the consists in place: a rename cannot invalidate a
 *    membership, it only moves the member into a different hash bucket.
 *
 * That last one is the subject of the second half of this file.  It did nothing at all until the
 * tests there were written.
 *
 * The concurrency half of the delete fix cannot be tested here: it needs a member removed at the
 * exact moment its head is iterating the map in setSpeed.  What is asserted instead is that the
 * removal goes through the synchronized entry point at all - see
 * testUnlinkLocomotiveIsSynchronized, and read its comment before deleting it.
 */
public class testMultiUnitMembership
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();
    }

    /**
     * Links the given members to the head, all at full speed.  Addresses must differ - canBeLinkedTo
     * refuses a member sharing an address with the head or with an existing member.
     */
    private static void link(MarklinLocomotive head, MarklinLocomotive... members)
    {
        Map<String, Double> list = new HashMap<>();

        for (MarklinLocomotive member : members)
        {
            list.put(member.getName(), 1.0);
        }

        head.preSetLinkedLocomotives(list);
        head.setLinkedLocomotives();
    }

    private static void deleteAll(String... names)
    {
        for (String name : names)
        {
            model.deleteLoc(name);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Deleting a member.  These pin behaviour that currently works.
    // ---------------------------------------------------------------------------------------------

    /**
     * The core of the delete fix, which had no coverage at all.
     *
     * Without the sweep the head goes on fanning every speed, direction and function command to the
     * decoder of a locomotive that no longer appears anywhere in the UI - and it stays that way until
     * a restart fails to resolve the saved name and drops the link with nothing but a log line.
     */
    @Test
    public void testDeletingAMemberRemovesItFromTheConsist()
    {
        MarklinLocomotive head = model.newDCCLocomotive("MU head A", 60);
        MarklinLocomotive m1 = model.newDCCLocomotive("MU member A1", 61);
        MarklinLocomotive m2 = model.newDCCLocomotive("MU member A2", 62);

        try
        {
            link(head, m1, m2);
            assertEquals(head.getLinkedLocomotives().size(), 2, "precondition: both members linked");

            assertTrue(model.deleteLoc("MU member A1"));

            assertEquals(head.getLinkedLocomotives().size(), 1,
                "the deleted member must be gone from the consist, not merely gone from the database");
            assertFalse(head.getLinkedLocomotiveNames().containsKey("MU member A1"));
            assertTrue(head.getLinkedLocomotiveNames().containsKey("MU member A2"),
                "the surviving member must be untouched");
        }
        finally
        {
            deleteAll("MU head A", "MU member A1", "MU member A2");
        }
    }

    /**
     * The sweep visits every locomotive in the database, so it has to leave unrelated consists alone.
     */
    @Test
    public void testDeletingANonMemberLeavesTheConsistIntact()
    {
        MarklinLocomotive head = model.newDCCLocomotive("MU head B", 63);
        MarklinLocomotive m1 = model.newDCCLocomotive("MU member B1", 64);

        model.newDCCLocomotive("MU unrelated B", 65);

        try
        {
            link(head, m1);

            assertTrue(model.deleteLoc("MU unrelated B"));

            assertEquals(head.getLinkedLocomotives().size(), 1);
            assertTrue(head.getLinkedLocomotiveNames().containsKey("MU member B1"));
        }
        finally
        {
            deleteAll("MU head B", "MU member B1", "MU unrelated B");
        }
    }

    /**
     * Deleting the head is just a deletion - the members are ordinary locomotives in their own right
     * and must survive it.
     */
    @Test
    public void testDeletingTheHeadLeavesItsMembersInTheDatabase()
    {
        MarklinLocomotive head = model.newDCCLocomotive("MU head C", 66);
        MarklinLocomotive m1 = model.newDCCLocomotive("MU member C1", 67);

        try
        {
            link(head, m1);

            assertTrue(model.deleteLoc("MU head C"));

            assertNotNull(model.getLocByName("MU member C1"),
                "a member is a locomotive too - dissolving the consist must not delete it");
        }
        finally
        {
            deleteAll("MU head C", "MU member C1");
        }
    }

    /**
     * The return value is what drives the log line, so it has to distinguish a real removal from a
     * no-op.
     */
    @Test
    public void testUnlinkLocomotiveReportsWhetherItRemovedAnything()
    {
        MarklinLocomotive head = model.newDCCLocomotive("MU head D", 68);
        MarklinLocomotive m1 = model.newDCCLocomotive("MU member D1", 69);
        MarklinLocomotive unrelated = model.newDCCLocomotive("MU unrelated D", 70);

        try
        {
            link(head, m1);

            assertFalse(head.unlinkLocomotive(unrelated), "never a member");
            assertFalse(head.unlinkLocomotive(head), "a locomotive is not a member of itself");
            assertFalse(head.unlinkLocomotive(null), "must not throw on null");

            assertTrue(head.unlinkLocomotive(m1), "a real member, removed");
            assertFalse(head.unlinkLocomotive(m1), "already removed, so nothing to report the second time");

            assertFalse(head.hasLinkedLocomotives());
        }
        finally
        {
            deleteAll("MU head D", "MU member D1", "MU unrelated D");
        }
    }

    /**
     * A structural assertion, not a behavioural one, and the only kind available here.
     *
     * unlinkLocomotive is a one-line map removal and reads as though the keyword on it is pointless.
     * It is not: setSpeed and setDirection iterate that same plain LinkedHashMap under this lock while
     * fanning a command out to the consist, so removing from it on another thread - deleting a
     * locomotive while its consist is being driven - can throw ConcurrentModificationException
     * part-way through, leaving some members commanded and others not.
     *
     * The race itself needs the removal to land inside that iteration and so cannot be triggered
     * reliably.  This checks the guard is still present rather than the race is absent.
     */
    @Test
    public void testUnlinkLocomotiveIsSynchronized() throws Exception
    {
        Method method = MarklinLocomotive.class.getDeclaredMethod("unlinkLocomotive", Locomotive.class);

        assertTrue(Modifier.isSynchronized(method.getModifiers()),
            "unlinkLocomotive must stay synchronized - it mutates the map setSpeed iterates under the "
            + "locomotive's own lock");
    }

    // ---------------------------------------------------------------------------------------------
    // Re-keying a member.  A locomotive's hashCode is built from its name, address and decoder type,
    // and all three are mutable in place - so a member that is renamed or re-addressed while linked
    // sits in the map under a hash that no longer matches it.  Iteration still finds it; every
    // lookup - containsKey, remove - does not.
    // ---------------------------------------------------------------------------------------------

    /**
     * changeLocAddress must not route through deleteLoc.  It is a re-key of a locomotive that
     * continues to exist, and the sweep would silently drop it from its multi-unit; the revalidation
     * loop at the end of that method could not put it back, because the link would already be gone.
     *
     * This has been broken once already, caught only incidentally by testMultiUnitCreation.
     */
    @Test
    public void testChangingAMemberAddressKeepsItInTheConsist() throws Exception
    {
        MarklinLocomotive head = model.newDCCLocomotive("MU head E", 71);
        MarklinLocomotive m1 = model.newDCCLocomotive("MU member E1", 72);

        try
        {
            link(head, m1);
            assertEquals(head.getLinkedLocomotives().size(), 1);

            model.changeLocAddress("MU member E1", 73, MarklinLocomotive.decoderType.DCC);

            assertEquals(head.getLinkedLocomotives().size(), 1,
                "an address change is not a deletion - the member stays in the consist");
            assertTrue(head.getLinkedLocomotiveNames().containsKey("MU member E1"));

            assertTrue(head.isLinkedTo(model.getLocByName("MU member E1")),
                "and stays reachable by lookup, because changeLocAddress rebuilds every consist "
                + "after re-keying");
        }
        finally
        {
            deleteAll("MU head E", "MU member E1");
        }
    }

    /**
     * Regression guard: renaming a member must not make it invisible to lookups.
     *
     * renameLoc mutates the locomotive's name in place and re-adds it to the database under the new
     * name.  A locomotive's hash is built partly from that name, so the rename moved it out of its
     * bucket in every consist holding it as a map KEY, and isLinkedTo - a containsKey - stopped
     * finding it.  Iteration still did, which is why the consist kept driving and nothing looked
     * wrong.
     *
     * isLinkedTo is what the multi-unit dialog uses to refuse making an already-linked locomotive
     * into a multi-unit head.  With it defeated, the nested consist that guard exists to prevent
     * became constructible: rename the member first, and the dialog no longer objected.
     */
    @Test
    public void testRenamedMemberIsStillRecognisedAsLinked()
    {
        MarklinLocomotive head = model.newDCCLocomotive("MU head F", 40);
        MarklinLocomotive m1 = model.newDCCLocomotive("MU member F1", 41);

        try
        {
            link(head, m1);
            assertTrue(head.isLinkedTo(m1), "precondition: recognised as linked before the rename");

            assertTrue(model.renameLoc("MU member F1", "MU member F1 renamed"));

            assertTrue(head.isLinkedTo(m1),
                "a rename does not dissolve a multi-unit, so the member must still be recognised");

            assertNotNull(model.isLocLinkedToOthers(m1),
                "and the dialog's guard against nesting consists must still fire for it");
        }
        finally
        {
            deleteAll("MU head F", "MU member F1", "MU member F1 renamed");
        }
    }

    /**
     * Regression guard for the same defect, reached through the other lookup.
     *
     * The delete sweep removes a member by map lookup, so a member whose hash had drifted was not
     * found and stayed linked - the exact defect the sweep was added to fix, reachable again by
     * renaming the locomotive first.
     */
    @Test
    public void testDeletingARenamedMemberRemovesItFromTheConsist()
    {
        MarklinLocomotive head = model.newDCCLocomotive("MU head G", 42);
        MarklinLocomotive m1 = model.newDCCLocomotive("MU member G1", 43);

        try
        {
            link(head, m1);

            assertTrue(model.renameLoc("MU member G1", "MU member G1 renamed"));
            assertTrue(model.deleteLoc("MU member G1 renamed"));

            assertFalse(head.hasLinkedLocomotives(),
                "the deleted member must leave the consist even if it was renamed while linked");
        }
        finally
        {
            deleteAll("MU head G", "MU member G1", "MU member G1 renamed");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The multi-unit sweep on the autonomy graph.
    // ---------------------------------------------------------------------------------------------

    /**
     * Renaming a locomotive that stands on a station leaves it on that station.
     *
     * Both rename doors in the window call Layout.sanitizeMultiUnits straight after renameLoc.  That
     * sweep clears every point holding a locomotive that is not isSimultaneousMultiUnitCompatible with
     * the one it was asked about - and that method ends `return !this.hasEquivalentAddress(l)`, so a
     * locomotive compared with ITSELF has an equivalent address and is declared incompatible.  The
     * renamed train was therefore taken off its own station: the station read as empty, the panel
     * showed "?????", and renaming it back brought nothing back, because nothing restores a placement.
     * Worse, autonomy saw a free platform where a train was standing.
     *
     * This does what the window does, in the order it does it - the rename, then the sweep.
     *
     * Ported from the 3.0 branch (66c96736).
     */
    @Test
    public void testRenamingAPlacedLocomotiveLeavesItOnItsStation() throws Exception
    {
        MarklinLocomotive loc = model.newMM2Locomotive("MU placed H", 73);

        try
        {
            Layout layout = new Layout(model);

            MarklinFeedback first = model.newFeedback(8390, null);
            MarklinFeedback second = model.newFeedback(8391, null);

            model.setFeedbackState(first.getName(), false);
            model.setFeedbackState(second.getName(), false);

            layout.createPoint("MU station A", true, first.getName());
            layout.createPoint("MU station B", true, second.getName());
            layout.createEdge("MU station A", "MU station B");

            layout.getPoint("MU station A").setLocomotive(loc);

            assertEquals(layout.getLocomotiveLocation(loc), layout.getPoint("MU station A"),
                "precondition: the locomotive has to be standing on the station for the sweep to "
                + "take it off");

            assertTrue(model.renameLoc("MU placed H", "MU placed H renamed"), "the rename itself failed");

            // What the window does next, and the whole of the fault
            layout.sanitizeMultiUnits(model.getLocByName("MU placed H renamed"));

            assertEquals(layout.getLocomotiveLocation(loc), layout.getPoint("MU station A"),
                "renaming the locomotive took it off the station it was standing on, because the "
                + "multi-unit sweep found it incompatible with itself - so the platform reads as empty "
                + "and autonomy can send another train into it");
        }
        finally
        {
            deleteAll("MU placed H", "MU placed H renamed");
        }
    }

    /**
     * Renaming a MEMBER of a multi-unit whose head stands on a station leaves the multi-unit on its
     * station.
     *
     * The self-skip the test above pins covers a train renamed where it stands.  A member is not where
     * it stands - its head is - and the sweep the window runs after a rename asked the head whether it
     * was compatible with the renamed member.  A head is never compatible with its own member
     * (isLinkedTo), so the head was cleared from its station: the same loss as the test above, on the
     * one rename the self-skip does not reach.  A rename changes no placement and no membership, so it
     * cannot make a conflict that was not there.
     *
     * Done as the window does it: the rename, then the sweep.  On a stopped railway - every locomotive
     * edit door refuses while autonomy runs, its coast-down and Return Home's planning included
     * (isAutonomyRunning).
     *
     * Found by the validator of the 2.8.2 backports (BPV-A1).  Ported from the 3.0 branch (69f1cefe).
     */
    @Test
    public void testRenamingAMemberLeavesItsMultiUnitOnItsStation() throws Exception
    {
        MarklinLocomotive head = model.newMM2Locomotive("MU head J", 78);
        MarklinLocomotive member = model.newMM2Locomotive("MU member J1", 79);

        try
        {
            link(head, member);

            assertTrue(head.isLinkedTo(member), "precondition: the member could not be linked to the head");

            assertFalse(head.isSimultaneousMultiUnitCompatible(member),
                "precondition: a head counts as compatible with its own member, so the sweep had "
                + "nothing to take it off for");

            Layout layout = new Layout(model);

            MarklinFeedback first = model.newFeedback(8392, null);
            MarklinFeedback second = model.newFeedback(8393, null);

            model.setFeedbackState(first.getName(), false);
            model.setFeedbackState(second.getName(), false);

            layout.createPoint("MU station C", true, first.getName());
            layout.createPoint("MU station D", true, second.getName());
            layout.createEdge("MU station C", "MU station D");

            layout.getPoint("MU station C").setLocomotive(head);

            assertTrue(model.renameLoc("MU member J1", "MU member J1 renamed"), "the rename itself failed");

            Locomotive renamed = model.getLocByName("MU member J1 renamed");

            assertNotNull(renamed, "the member is not in the database under its new name");

            // What the window does next
            layout.sanitizeMultiUnits(renamed);

            assertEquals(layout.getLocomotiveLocation(head), layout.getPoint("MU station C"),
                "renaming a member of a multi-unit took the multi-unit off its station, because the "
                + "sweep asked the head whether it was compatible with its own member - so the platform "
                + "reads as empty with the train standing on it (BPV-A1)");
        }
        finally
        {
            deleteAll("MU head J", "MU member J1", "MU member J1 renamed");
        }
    }

    /**
     * A saved layout in which a multi-unit's head and one of its members both stand is loaded with
     * only one of them standing: the member is part of the head's train, and one train cannot stand
     * in two places.
     *
     * The loader's half of BPV-A1's fix.  The window's edit doors sweep only for a train that stands
     * on the graph; the loader asks the sweep before each train is put down, so it must keep asking
     * regardless - or both would load standing.  A control of the fix, not of the defect: it passes
     * with and without it, and fails if the loader is given the edit doors' sweep.
     *
     * Ported from the 3.0 branch (612d9600).
     */
    @Test
    public void testALoadedLayoutDoesNotStandAHeadAndItsMemberBoth() throws Exception
    {
        MarklinLocomotive head = model.newMM2Locomotive("MU head K", 80);
        MarklinLocomotive member = model.newMM2Locomotive("MU member K1", 81);

        try
        {
            link(head, member);

            assertTrue(head.isLinkedTo(member), "precondition: the member could not be linked to the head");

            Layout layout = new Layout(model);

            MarklinFeedback first = model.newFeedback(8394, null);
            MarklinFeedback second = model.newFeedback(8395, null);

            model.setFeedbackState(first.getName(), false);
            model.setFeedbackState(second.getName(), false);

            layout.createPoint("MU station E", true, first.getName());
            layout.createPoint("MU station F", true, second.getName());
            layout.createEdge("MU station E", "MU station F");

            // Both standing, as a file written by hand, or by an older build, can have them
            layout.getPoint("MU station E").setLocomotive(head);
            layout.getPoint("MU station F").setLocomotive(member);

            // A new Layout has no default speed, and the loader refuses a layout without one
            layout.setDefaultLocSpeed(30);

            Layout loaded = Layout.fromJSON(layout.toJSON(), model);

            assertTrue(loaded.isValid(), "precondition: the saved layout does not load: " + Layout.getLastError());

            boolean headStands = loaded.getLocomotiveLocation(head) != null;
            boolean memberStands = loaded.getLocomotiveLocation(member) != null;

            assertTrue(headStands || memberStands,
                "precondition: the load placed neither train, so it says nothing about the two of them");

            assertFalse(headStands && memberStands,
                "a saved layout loaded with a multi-unit's head and one of its members both standing - "
                + "one train in two places");
        }
        finally
        {
            deleteAll("MU head K", "MU member K1");
        }
    }

    /**
     * Placing a member of a multi-unit by hand while its head stands takes the head off: the member is
     * part of the head's train, and one train cannot stand in two places.
     *
     * The placing half of BPV-A1's fix, beside the loader's.  moveLocomotive asks the sweep before it
     * puts the train down, when the train stands nowhere, so it must keep the whole sweep rather than
     * the edit doors' sanitizeMultiUnits, which asks only of a train already standing.  A control of
     * the fix: it passes with and without it, and fails if placing is given the edit doors' sweep.
     */
    @Test
    public void testPlacingAMemberTakesItsStandingHeadOff() throws Exception
    {
        MarklinLocomotive head = model.newMM2Locomotive("MU head L", 82);
        MarklinLocomotive member = model.newMM2Locomotive("MU member L1", 83);

        try
        {
            link(head, member);

            assertTrue(head.isLinkedTo(member), "precondition: the member could not be linked to the head");

            Layout layout = new Layout(model);

            MarklinFeedback first = model.newFeedback(8396, null);
            MarklinFeedback second = model.newFeedback(8397, null);

            model.setFeedbackState(first.getName(), false);
            model.setFeedbackState(second.getName(), false);

            layout.createPoint("MU station G", true, first.getName());
            layout.createPoint("MU station I", true, second.getName());
            layout.createEdge("MU station G", "MU station I");

            layout.getPoint("MU station G").setLocomotive(head);

            // The call every door that puts a locomotive on the graph makes
            assertTrue(layout.moveLocomotive("MU member L1", "MU station I", false),
                "precondition: the member could not be placed");

            assertEquals(layout.getLocomotiveLocation(member), layout.getPoint("MU station I"),
                "precondition: the member is not where it was placed");

            assertNull(layout.getLocomotiveLocation(head),
                "placing a member of a multi-unit left its head standing too - one train in two places");
        }
        finally
        {
            deleteAll("MU head L", "MU member L1");
        }
    }
}
