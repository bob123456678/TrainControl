import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
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
 *    exists; it rebuilds every consist afterwards instead.
 *  - renameLoc does neither.
 *
 * That last one is the subject of the second half of this file.
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
     * EXPECTED TO FAIL until renameLoc rebuilds consists the way changeLocAddress does.
     *
     * renameLoc mutates the locomotive's name in place and re-adds it to the database under the new
     * name, but never touches the consists holding it as a map KEY.  Its hash changes underneath the
     * map, so isLinkedTo - a containsKey - stops finding it.
     *
     * That is the guard the multi-unit dialog uses to refuse making an already-linked locomotive into
     * a multi-unit head.  With it defeated, the nested consist that this arrangement is supposed to
     * make unreachable becomes constructible: rename the member first, and the dialog no longer
     * objects.
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
     * EXPECTED TO FAIL for the same reason.
     *
     * The delete sweep removes a member by map lookup, so a member whose hash has drifted is not
     * found and stays linked - which is the exact defect the sweep was added to fix, reachable again
     * by renaming the locomotive first.
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
}
