import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.file.CS2File;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
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

        for (String name : new String[] { "IR mismatch", CS_NAME, "IR head", "IR member" })
        {
            model.deleteLoc(name);
        }
    }

    private static void set(String field, Object value) throws Exception
    {
        Field f = MarklinControlStation.class.getDeclaredField(field);

        f.setAccessible(true);
        f.set(model, value);
    }

    /**
     * Only one locomotive can hold a given UID - RemoteDeviceCollection keeps a strict one-to-one
     * mapping and evicts whatever held it before - so each test installs its own and does not depend on
     * what any other test left behind.
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
