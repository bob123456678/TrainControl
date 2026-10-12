package core;

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

    /**
     * A member whose name contains a comma and a space keeps it, and is still found.
     *
     * The array block of a CS2 file used to be flattened by calling HashMap.toString() and then
     * rewriting its ", " entry separator back to ",".  That rewrite cannot tell the separator from a
     * ", " INSIDE a value, and exactly one array key carries free text: lokname, the name of a
     * multi-unit member.  "BR 50, Ep. III" was stored as "BR 50,Ep. III", matched no locomotive in the
     * database, and was dropped from its consist with a log line for company - so commanding the head
     * moved one engine of two.
     *
     * The twin of this, four lines further up in the same block, was fixed when a member named
     * "BR 50 = Ep.III" was lost the same way.
     *
     * Driven through parseFile rather than a whole sync: the defect is in the flattening, and the
     * recovery below is the exact expression parseLocomotives uses to read it back.
     */
    @Test
    public void testAMemberNameWithACommaSurvivesTheParse() throws Exception
    {
        String awkward = "BR 50, Ep. III";

        String file = "[lokomotive]\n"
            + "lokomotive\n"
            + " .name=Doppeltraktion\n"
            + " .uid=0x4001\n"
            + " .traktion\n"
            + " ..lok=0x400b\n"
            + " ..lokname=" + awkward + "\n"
            + " .traktion\n"
            + " ..lok=0x400a\n"
            + " ..lokname=1043 001-5 OeBB\n"
            + "lokomotive\n"
            + " .name=Something Else\n"
            + " .uid=0x4002\n";

        java.util.List<java.util.Map<String, String>> parsed =
            org.traincontrol.marklin.file.CS2File.parseFile(new java.io.BufferedReader(
                new java.io.StringReader(file)));

        String traktion = null;

        for (java.util.Map<String, String> one : parsed)
        {
            if ("Doppeltraktion".equals(one.get("name"))) traktion = one.get("traktion");
        }

        assertNotNull(traktion, "the multi-unit was not parsed at all");

        // The exact expression parseLocomotives uses to recover the member names
        java.util.List<String> members = new java.util.ArrayList<>();

        for (String part : traktion.replace("{", "").replace("}", "").split("\\|"))
        {
            members.add(part.split(",lok=")[0].replace("lokname=", ""));
        }

        assertTrue(members.contains(awkward),
            "the member came back as " + members + ".  A name with a comma and a space in it is not "
            + "found in the locomotive database, so the member is dropped from its consist and "
            + "commanding the head moves one engine of two");

        assertEquals(members.size(), 2, "the other member went missing: " + members);
    }

    // ---------------------------------------------------------------------------------------------
    // What a reader of the consist can see while it is being rebuilt (S14-B4, raised to A by NSV).
    // ---------------------------------------------------------------------------------------------

    /**
     * Rebuilding a consist does not change the map somebody is already reading.
     *
     * **The save path is the reader that matters.** `MarklinSimpleComponent` reads
     * `getLinkedLocomotives()` with no lock, and `restoreState` rebuilds every consist from exactly
     * that field - so a save that reads the map while a rebuild has emptied it writes a locomotive with
     * NO members into `locdb.data`, and the consist is gone after the next start with the file as the
     * only record.  The rebuild runs off the event thread inside `syncWithCS2`; the save runs on the
     * window-closing path and on Backup Data's own thread, and nothing serialises them.
     *
     * **A race cannot be asserted, so the property that removes it is.** The defect was `clear()` then
     * `putAll()` on the live map: every reader holds the same instance, so there is a window in which it
     * is empty, and no reader can tell that window from a consist with no members.  A map that is
     * replaced rather than edited has no such window - whatever a reader is holding stays as it was, and
     * the assertion below is that exact property, which is deterministic.
     *
     * Rebuilt with DIFFERENT contents on purpose.  Refilling with the same two members leaves the live
     * map equal to what it was, so a test that rebuilds the same consist passes on the broken code.
     *
     * MUTATION: go back to `this.linkedLocomotives.clear(); this.linkedLocomotives.putAll(staged);` and
     * the first assertion fails, reporting 1 member in a map that was read when it had 2.
     */
    @Test
    public void testARebuildDoesNotChangeTheMapAReaderIsHolding()
    {
        MarklinLocomotive head = model.newDCCLocomotive("MU head H", 73);
        MarklinLocomotive m1 = model.newDCCLocomotive("MU member H1", 74);
        MarklinLocomotive m2 = model.newDCCLocomotive("MU member H2", 75);

        try
        {
            link(head, m1, m2);

            // What the save path holds: the map itself, taken once, exactly as MarklinSimpleComponent
            // takes it.
            Map<Locomotive, Double> whatTheSaveSees = head.getLinkedLocomotives();

            assertEquals(whatTheSaveSees.size(), 2, "precondition: both members linked");

            // And now the sync rebuilds the consist with one member, on another thread.
            link(head, m1);

            assertEquals(whatTheSaveSees.size(), 2,
                "the rebuild edited the map the save path was already holding - it now reports "
                + whatTheSaveSees.size() + " members.  Mid-rebuild that value is ZERO, and a save in "
                + "that window writes a consist with no members into locdb.data, which restoreState "
                + "then rebuilds from (S14-B4)");

            // The control: the rebuild did happen, so the assertion above is not passing because
            // nothing moved.
            assertEquals(head.getLinkedLocomotives().size(), 1,
                "control: the rebuild to one member did not take effect, so this test is not "
                + "measuring what it claims to");
        }
        finally
        {
            deleteAll("MU head H", "MU member H1", "MU member H2");
        }
    }

    /**
     * The consist handed out is a snapshot, not the live map.
     *
     * `getLinkedLocomotives()` returned the field itself, so any caller could empty a consist by
     * accident and every caller shared one instance with the rebuild.  Nothing in the tree mutates it -
     * checked across `src/` and `test/` - which is why handing out an unmodifiable view costs nothing
     * and closes the door for the next caller.
     *
     * MUTATION: return `this.linkedLocomotives` unwrapped and this fails.
     */
    @Test
    public void testTheConsistHandedOutCannotBeEdited()
    {
        MarklinLocomotive head = model.newDCCLocomotive("MU head H", 73);
        MarklinLocomotive m1 = model.newDCCLocomotive("MU member H1", 74);

        try
        {
            link(head, m1);

            try
            {
                head.getLinkedLocomotives().clear();

                fail("a caller emptied a consist through getLinkedLocomotives(), which hands back the "
                    + "live map every other reader and the rebuild share");
            }
            catch (UnsupportedOperationException expected)
            {
            }

            assertEquals(head.getLinkedLocomotives().size(), 1, "and the consist is intact");
        }
        finally
        {
            deleteAll("MU head H", "MU member H1");
        }
    }

    /**
     * Applying a list in one call does not touch what another thread has staged (NSV-B2).
     *
     * `preSetLinkedLocomotives` writes an instance field and `setLinkedLocomotives()` reads it back,
     * with nothing between them.  Two threads rebuild consists - `syncWithCS2` off the event thread and
     * the multi-unit dialog on it - so one thread's list could be overwritten before its own apply read
     * it, and the consist was rebuilt from the other thread's list.  Both calls return success either
     * way, which is why nothing noticed.
     *
     * **What is asserted is that the one-call form does not go through the field at all.** A list staged
     * and not yet applied is still there afterwards, which is the property a second thread needs.  The
     * interleaving itself cannot be asserted; this is the invariant that makes it harmless.
     *
     * MUTATION: make `setLinkedLocomotives(Map)` do `preSetLinkedLocomotives(locList); return
     * setLinkedLocomotives();` and the last assertion fails, because the staged member has become the
     * one the other caller passed.
     */
    @Test
    public void testApplyingAListDoesNotDisturbWhatIsStaged()
    {
        MarklinLocomotive head = model.newDCCLocomotive("MU head I", 76);
        MarklinLocomotive m1 = model.newDCCLocomotive("MU member I1", 77);
        MarklinLocomotive m2 = model.newDCCLocomotive("MU member I2", 78);

        try
        {
            // Thread A stages, and has not applied yet.
            Map<String, Double> staged = new HashMap<>();

            staged.put(m1.getName(), 1.0);

            head.preSetLinkedLocomotives(staged);

            // Thread B applies its own list, in one call.
            Map<String, Double> theirs = new HashMap<>();

            theirs.put(m2.getName(), 1.0);

            assertEquals(head.setLinkedLocomotives(theirs), 1, "the one-call form linked the member");

            assertTrue(head.getLinkedLocomotiveNames().containsKey(m2.getName()),
                "and it linked the member it was given");

            // Thread A now applies what IT staged, and must get what it staged.
            assertEquals(head.setLinkedLocomotives(), 1, "the staged list still applies");

            assertTrue(head.getLinkedLocomotiveNames().containsKey(m1.getName()),
                "the one-call form overwrote the list another caller had staged, so that caller's "
                + "consist was rebuilt from somebody else's members (NSV-B2).  The consist is now "
                + head.getLinkedLocomotiveNames().keySet());
        }
        finally
        {
            deleteAll("MU head I", "MU member I1", "MU member I2");
        }
    }

    /**
     * A consist can drive every function any of its members has (Adam, MT-359, 2026-09-11).
     *
     * *"Make the allowed function be the highest possible for the consist - so two MM2 locs mean F0-4
     * do something.  one mm2 loc and one dcc/mfx mean all functions are unlocked."*
     *
     * **This replaces the opposite claim.** `S14-B1` found that an MM2 head passed f6 to an MFX member
     * and recorded it nowhere - no button, `functionsOff` could not clear it, and the next press sent ON
     * again - and fixed it by refusing anything the HEAD could not do. That fixed the real defect and
     * answered the wrong question: the point of a consist is that a member can do what the head cannot.
     * Adam ran it and said so.
     *
     * What must stay true is the part that made it a finding: nothing can be switched on that nothing
     * can switch off. So three things are asserted together - the function reaches the member, the
     * consist can still SEE it afterwards, and `functionsOff` clears it.
     *
     * MUTATION: put `if (this.validF(fNumber))` back around the fan-out and the first claim fails; drop
     * the `getF` override and the second does; leave `functionsOff` walking `getNumF()` and the third.
     */
    @Test
    public void testAConsistCanDriveEveryFunctionItsMembersHave()
    {
        MarklinLocomotive head = model.newMM2Locomotive("MU head J", 79);
        MarklinLocomotive member = model.newMFXLocomotive("MU member J1", 80);

        try
        {
            assertEquals(head.getNumF(), 5, "precondition: an MM2 head has five functions of its own");

            assertTrue(member.getNumF() > 6,
                "precondition: the MFX member has more functions than the head, which is the whole "
                + "configuration under test");

            link(head, member);

            assertEquals(head.drivableFunctionCount(), member.getNumF(),
                "the consist's range is the highest of its members, not the head's");

            head.setF(6, true);

            assertTrue(member.getF(6),
                "f6 did not reach the MFX member.  An MM2 head cannot do f6 itself, but the consist "
                + "can - that is what a consist is for (MT-359)");

            // AND THE CONSIST CAN SEE IT, which is what the original defect was really about: the state
            // went somewhere nothing could read.
            assertTrue(head.getF(6),
                "the consist cannot see a function it has just switched on, so no button shows it and "
                + "nothing can turn it off - which is the defect S14-B1 was filed for");

            // AND CAN TURN IT OFF AGAIN.
            head.functionsOff();

            assertFalse(member.getF(6),
                "functionsOff left f6 on at the member: it walks the head's own function count, so a "
                + "consist could switch on what it could not switch off");

            // A function beyond EVERY member is still refused, so this is a range and not an absence
            // of one.
            head.setF(member.getNumF() + 5, true);

            assertFalse(head.getF(member.getNumF() + 5), "a function no member has was accepted");

            // THE CONTROL: two locomotives of the same kind still have that kind's range.
            MarklinLocomotive plainHead = model.newMM2Locomotive("MU head J2", 81);
            MarklinLocomotive plainMember = model.newMM2Locomotive("MU member J2", 82);

            try
            {
                link(plainHead, plainMember);

                assertEquals(plainHead.drivableFunctionCount(), 5,
                    "two MM2 locomotives give an MM2 range - the rule is the highest of the members, "
                    + "not simply the highest there is");
            }
            finally
            {
                deleteAll("MU head J2", "MU member J2");
            }
        }
        finally
        {
            deleteAll("MU head J", "MU member J1");
        }
    }

    /**
     * `setSpeed` clamps its own argument the way it already clamps its members' (S14-B2, NSV-C4).
     *
     * `_setSpeed` is wrapped in `if (speed >= 0 && speed <= 100)` with no `else`, so an out-of-range
     * value is DISCARDED and `getSpeed()` still reports whatever it was - and the head then
     * re-transmits that old speed while every member is sent `min(speed x multiplier, 100)`. That is
     * exactly the failure the clamp inside the member loop was written for, quoted in its own comment as
     * *"the two engines of one consist pulled against each other"*, one level up from where it was
     * fixed.
     *
     * The member clamp was also one-sided: a negative argument scaled by a multiplier stays negative,
     * `_setSpeed` ignores it for the same reason, and the member re-transmits its old speed too
     * (NSV-C4).
     *
     * `setSpeed` is public and is reached from a route, from autonomy and from the throttle, so this is
     * worth having even with the command's own clamp in place - "ignored, and the previous speed re-sent"
     * is the worst of the three possible answers to a bad number.
     *
     * MUTATION: take the clamp back out of `setSpeed` and both halves fail.
     */
    @Test
    public void testSetSpeedClampsItsOwnArgument()
    {
        MarklinLocomotive head = model.newDCCLocomotive("MU head K", 81);
        MarklinLocomotive member = model.newDCCLocomotive("MU member K1", 82);

        try
        {
            link(head, member);

            head.setSpeed(40);

            assertEquals(head.getSpeed(), 40, "precondition: an ordinary speed is set");

            head.setSpeed(150);

            assertEquals(head.getSpeed(), 100,
                "a speed of 150 was discarded, so the head still reports " + head.getSpeed()
                + " and re-transmits it while every member is sent 100 - the two engines of one consist "
                + "pulling against each other (S14-B2)");

            assertEquals(member.getSpeed(), 100, "and the member is at the clamped speed, not past it");

            head.setSpeed(-20);

            assertEquals(head.getSpeed(), 0,
                "a negative speed was discarded rather than clamped, so the head kept the speed it had "
                + "(NSV-C4).  instantStop is what a route's -1 reaches; setSpeed's argument is a speed");

            assertEquals(member.getSpeed(), 0, "and the member is stopped with it");
        }
        finally
        {
            deleteAll("MU head K", "MU member K1");
        }
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();
    }

    /**
     * A function inside the consist's range is SENT to every locomotive in it, head included.
     *
     * Adam, MT-359, 2026-09-13: *"Pressing F6 on the MM2 head, if it has a MFX/DCC member, should simply
     * propagate a F6 command to all the consist's locomotives as per normal.  The CS will figure out
     * whether that means anything."*  And, asked whether the range stays: **"Send to all, keep the cap."**
     *
     * The first repair of S14-B1 passed the number to each member through the member's own `setF`, whose
     * `validF` refused anything its own decoder does not list - so an MM2 member of an MFX-headed consist
     * never heard f6, and neither did the MM2 head of an MFX member.  Whether a decoder does something
     * with f6 is the station's business, not a guess made from a function count.
     *
     * **Read off what the locomotives asked the station to send**, through the observer on `exec`: in a
     * test the network is off and a discarded command is otherwise visible nowhere, and the recorded
     * function state cannot show a command for a function the decoder does not list.
     */
    @Test
    public void testAFunctionInTheConsistsRangeIsSentToEveryLocomotive()
    {
        MarklinLocomotive head = model.newMFXLocomotive("MU head K", 83);
        MarklinLocomotive member = model.newMM2Locomotive("MU member K1", 84);

        MarklinLocomotive mm2Head = model.newMM2Locomotive("MU head K2", 85);
        MarklinLocomotive mfxMember = model.newMFXLocomotive("MU member K3", 86);

        final java.util.List<int[]> sent = java.util.Collections.synchronizedList(new java.util.ArrayList<int[]>());

        model.setSentMessageObserver(m ->
        {
            byte[] data = m.getData();

            if (m.getCommand() == org.traincontrol.marklin.udp.CS2Message.CMD_LOCO_FUNCTION
                && data != null && data.length >= 6)
            {
                int uid = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
                    | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

                sent.add(new int[] {uid, data[4] & 0xFF, data[5] & 0xFF});
            }
        });

        try
        {
            assertEquals(member.getNumF(), 5, "precondition: an MM2 member has five functions of its own");

            link(head, member);
            link(mm2Head, mfxMember);

            head.setF(6, true);

            assertTrue(wasSent(sent, head, 6),
                "precondition: the MFX head was not sent its own f6, so nothing here is about the members");

            assertTrue(wasSent(sent, member, 6),
                "f6 pressed on an MFX head was not sent to its MM2 member, because the member's own"
                + " function count has no f6. Adam, MT-359: \"should simply propagate a F6 command to all"
                + " the consist's locomotives as per normal. The CS will figure out whether that means"
                + " anything.\"");

            sent.clear();

            mm2Head.setF(6, true);

            assertTrue(wasSent(sent, mfxMember, 6),
                "precondition: the MFX member of an MM2 head was not sent f6");

            assertTrue(wasSent(sent, mm2Head, 6),
                "f6 pressed on an MM2 head with an MFX member was sent to the member and not to the head"
                + " itself. Adam, MT-359: \"propagate a F6 command to ALL the consist's locomotives\"");

            // AND THE CAP STAYS: a function beyond every member is sent to nobody.
            sent.clear();

            int beyond = mfxMember.getNumF() + 5;

            mm2Head.setF(beyond, true);

            assertTrue(sent.isEmpty(),
                "f" + beyond + ", which no locomotive of the consist has, was sent anyway - Adam kept the"
                + " cap: \"Send to all, keep the cap.\" Sent: " + sent.size());
        }
        finally
        {
            model.setSentMessageObserver(null);

            for (String name : new String[] {"MU head K", "MU member K1", "MU head K2", "MU member K3"})
            {
                model.deleteLoc(name);
            }
        }
    }

    /** Whether a function command for this locomotive and number was handed to the station. */
    private static boolean wasSent(java.util.List<int[]> sent, MarklinLocomotive loc, int f)
    {
        synchronized (sent)
        {
            for (int[] one : sent)
            {
                if (one[0] == loc.getIntUID() && one[1] == f) return true;
            }
        }

        return false;
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
}
