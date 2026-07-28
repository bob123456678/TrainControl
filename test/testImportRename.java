import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.file.CS2File;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * getLocomotivesToRenameFromImport - the list behind "check for renamed locomotives".
 *
 * It compares what the Central Station reports against the local database and proposes renames, and it
 * had no coverage at all.  Two of its rules are not obvious from the outside and are the reason this
 * exists:
 *
 *  - It matches on UID - address and decoder type - not on name.  That is the whole point: it exists to
 *    find locomotives that are the same decoder under a different label.
 *  - It skips any local locomotive that heads a multi-unit.  Nothing else records that rule, and it
 *    would be easy to "tidy away" while refactoring.
 *
 * The parse is driven through CS3TestServer, the same fixture server testParseWebServer uses, so no
 * Central Station is needed.  The model's parser and CS3 flag are injected by reflection - matching
 * testAccessory and testNetworkProxy - because the only production route that sets them is a live sync.
 *
 * SBB RE 4_4 II (MM2, address 76) is used as the reference locomotive: assertions are all about
 * specific names, never counts, because the model restores a saved database whose contents are not
 * known here.
 */
public class testImportRename
{
    private static MarklinControlStation model;
    private static CS3TestServer server;

    private static final String CS_NAME = "SBB RE 4_4 II";
    private static final int CS_ADDRESS = 76;

    /** Everything these tests create.  Cleared before each one - see clearTestLocomotives. */
    private static final String[] TEST_LOCS = {
        "IR mismatch", CS_NAME, "IR head", "IR member", "IR dupe one", "IR dupe two",
        "IR cs dupe" };

    /**
     * An address the FIXTURE holds twice: MM2 60 is both "ALCO UP" and "V 60 706" in CS3_loks.json.
     * Two others exist (MM2 1 and MM2 3) if this one ever needs replacing.
     */
    private static final int CS_DUPE_ADDRESS = 60;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);
        model.stop();

        server = new CS3TestServer(
            testImportRename.class.getResource("CS3_loks.json").toURI().toString(),
            testImportRename.class.getResource("CS3_loks_v260.json").toURI().toString(),
            testImportRename.class.getResource("CS3_mags.json").toURI().toString(),
            testImportRename.class.getResource("CS3_automatics.json").toURI().toString(),
            testImportRename.class.getResource("CS3_automatics_v260.json").toURI().toString()
        );

        server.startServer(260);

        set("fileParser", new CS2File("localhost:8080", model));
        set("isCS3", true);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        if (server != null) server.stopServer();

        deleteTestLocomotives();
    }

    private static void deleteTestLocomotives()
    {
        for (String name : TEST_LOCS)
        {
            model.deleteLoc(name);
        }
    }

    /**
     * Removes every locomotive these tests create, before each one.
     *
     * Necessary because two locomotives CAN share an address: the database is keyed by name *and*
     * address, so installing a second one at the reference address does not replace the first.  Without
     * this, each test would leave its locomotive behind, the reference address would end up shared, and
     * the later tests would see it as ambiguous - which is now, correctly, not proposed for renaming.
     */
    @BeforeMethod
    public void clearTestLocomotives()
    {
        deleteTestLocomotives();
    }

    private static void set(String field, Object value) throws Exception
    {
        Field f = MarklinControlStation.class.getDeclaredField(field);

        f.setAccessible(true);
        f.set(model, value);
    }

    /**
     * Installs a locomotive at the address the fixture's reference locomotive uses.
     *
     * Note this does NOT replace anything already at that address: RemoteDeviceCollection keys
     * locomotives by name and address together (getUID is "name_UID"), so two locomotives on one
     * address coexist quite happily.  clearTestLocomotives is what keeps the tests independent.
     */
    private static MarklinLocomotive installAtReferenceAddress(String name) throws Exception
    {
        return model.newMM2Locomotive(name, CS_ADDRESS);
    }

    private static String renameTargetFor(String currentName) throws Exception
    {
        for (String[] pair : model.getLocomotivesToRenameFromImport())
        {
            if (pair[0].equals(currentName)) return pair[1];
        }

        return null;
    }

    /**
     * Every target proposed for one source.  Separate from renameTargetFor because the defect below is
     * precisely that one source produced more than one proposal - which a first-match lookup hides.
     */
    private static List<String> renameTargetsFor(String currentName) throws Exception
    {
        List<String> targets = new ArrayList<>();

        for (String[] pair : model.getLocomotivesToRenameFromImport())
        {
            if (pair[0].equals(currentName)) targets.add(pair[1]);
        }

        return targets;
    }

    /**
     * The locomotive the Central Station knows under a different name is proposed for renaming, and the
     * match is made on the decoder rather than the label.
     */
    @Test
    public void testLocomotiveWithADifferentNameIsProposed() throws Exception
    {
        installAtReferenceAddress("IR mismatch");

        assertEquals(renameTargetFor("IR mismatch"), CS_NAME,
            "a local locomotive sharing the Central Station's UID but not its name is what this list is "
            + "for - the match is on address and decoder type, not on the name");
    }

    /**
     * Nothing to propose when the names already agree.
     */
    @Test
    public void testLocomotiveWithTheSameNameIsNotProposed() throws Exception
    {
        installAtReferenceAddress(CS_NAME);

        assertNull(renameTargetFor(CS_NAME), "the names already match, so there is nothing to rename");
    }

    /**
     * The Central Station can hold two locomotives at one address too, and then there is no single
     * name to propose.  The mirror image of the local-side rule, and it was missed when that one was
     * fixed - the local side was indexed and de-duplicated, the parsed side went on being iterated
     * straight through.
     *
     * Both fixture locomotives at this address matched the one local locomotive, so the list came back
     * with TWO proposals from a single source.  The consumer precomputes that list and acts on it in
     * order, so once the first rename is applied the second names a locomotive that no longer exists.
     * If some unrelated local locomotive happens to hold the second target name, the flow deletes it
     * and then renames nothing, because its source is gone: a delete with no compensating rename,
     * after two dialogs that both described a rename.
     *
     * **The shape this needs has to be built, not assumed.**  It requires ONE local locomotive against
     * TWO remote ones, and an untouched database does not supply it: init restores the real LocDB.data,
     * and syncWithCS2 auto-adds Central Station locomotives the local side lacks - so on any database
     * that has synced against this fixture, both duplicates are local too, and the local-side refusal
     * fires before the parsed side is ever consulted.  That is exactly the narrowing that makes this
     * finding a B rather than an A, and it also means the first version of this test failed its own
     * precondition on the author's database.  So every other locomotive on this decoder is removed
     * first.
     *
     * In memory only: saveState lives in TrainControlUI, which these tests never construct.
     */
    @Test
    public void testDuplicateCentralStationAddressProducesNoRenameProposal() throws Exception
    {
        MarklinLocomotive mine = model.newMM2Locomotive("IR cs dupe", CS_DUPE_ADDRESS);

        // getLocomotives returns a fresh list, so deleting while iterating it is safe
        for (Locomotive other : model.getLocomotives())
        {
            MarklinLocomotive existing = (MarklinLocomotive) other;

            if (existing != mine && existing.getIntUID() == mine.getIntUID())
            {
                model.deleteLoc(existing.getName());
            }
        }

        assertEquals(localsSharingDecoderWith(mine), 1,
            "precondition: this test's locomotive must be the only local one on this decoder - with "
            + "another there the LOCAL-side refusal fires instead, and this would pass without "
            + "exercising the Central Station side at all");

        List<String> targets = renameTargetsFor("IR cs dupe");

        assertTrue(targets.isEmpty(),
            "the Central Station has two locomotives at this address, so no single name can be "
            + "proposed - but got " + targets);
    }

    /**
     * Locomotives sharing a decoder with the given one, itself included.
     *
     * Counted by UID rather than by raw address: getDuplicateLocAddresses keys on getAddress, so it
     * cannot tell an MM2 60 from an MFX 60 - which are different decoders and do not collide in the
     * index the method under test builds.
     */
    private static int localsSharingDecoderWith(MarklinLocomotive loc)
    {
        int count = 0;

        for (Locomotive other : model.getLocomotives())
        {
            if (((MarklinLocomotive) other).getIntUID() == loc.getIntUID()) count++;
        }

        return count;
    }

    /**
     * A locomotive that heads a multi-unit is never proposed, however its name differs.
     *
     * The rule is one clause of a compound condition in the middle of the method
     * (`&& !existingLoc.hasLinkedLocomotives()`), with no comment, and renaming a consist head is
     * exactly the operation that would be most disruptive to get wrong.
     */
    @Test
    public void testConsistHeadIsNeverProposed() throws Exception
    {
        MarklinLocomotive head = installAtReferenceAddress("IR head");
        MarklinLocomotive member = model.newMM2Locomotive("IR member", CS_ADDRESS - 1);

        Map<String, Double> links = new HashMap<>();
        links.put(member.getName(), 1.0);

        head.preSetLinkedLocomotives(links);
        head.setLinkedLocomotives();

        assertTrue(head.hasLinkedLocomotives(), "precondition: the head really is a multi-unit");

        assertNull(renameTargetFor("IR head"),
            "a locomotive heading a multi-unit is skipped, even though its name differs from the "
            + "Central Station's");
    }

    /**
     * Two local locomotives can share an address - the database is keyed by name AND address - and the
     * Central Station has only one name for that address.  Proposing it for both is incoherent, and
     * acting on the proposals in order used to destroy data: each rename deletes whatever already holds
     * the target name, so the second rename deleted the locomotive the first had just renamed.
     */
    @Test
    public void testDuplicateAddressProducesNoRenameProposal() throws Exception
    {
        MarklinLocomotive first = model.newMM2Locomotive("IR dupe one", CS_ADDRESS);
        MarklinLocomotive second = model.newMM2Locomotive("IR dupe two", CS_ADDRESS);

        assertNotNull(model.getLocByName("IR dupe one"),
            "precondition: two locomotives on one address coexist - the database key is name and address");
        assertNotNull(model.getLocByName("IR dupe two"), "precondition: both are present");

        assertNull(renameTargetFor("IR dupe one"),
            "neither duplicate may be proposed: renaming both to the Central Station name would delete one");
        assertNull(renameTargetFor("IR dupe two"), "and neither may the other");

        for (String[] pair : model.getLocomotivesToRenameFromImport())
        {
            assertNotEquals(pair[1], CS_NAME,
                "nothing may be proposed for the ambiguous address at all");
        }
    }

    /**
     * A locomotive the Central Station has never heard of is left alone.
     */
    @Test
    public void testUnknownLocomotiveIsNotProposed() throws Exception
    {
        List<String[]> candidates = model.getLocomotivesToRenameFromImport();

        for (String[] pair : candidates)
        {
            assertNotNull(model.getLocByName(pair[0]),
                "every proposal must name a locomotive that actually exists locally");
            assertNotEquals(pair[0], pair[1], "and must be an actual change of name");
        }
    }
}
