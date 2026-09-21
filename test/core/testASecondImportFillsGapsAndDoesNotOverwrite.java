package core;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.marklin.MarklinControlStation;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Importing the same 2.7.4c file twice fills what is missing and leaves what is there (MT-298).
 *
 * **The promise the entry makes is that an import is safe to repeat.**  Somebody who imports, then
 * corrects a station by hand, then imports again - because they are not sure the first one took, which
 * is exactly why they would - must not lose the correction.  `importLegacy` gap-fills by design, and
 * nothing held it to that.
 *
 * The file is Adam's own, shipped beside the entry: `docs/manual-tests/files/MT-298-autonomy-2.7.4c.json`
 * is a byte-for-byte copy of the `config/autonomy_legacy/autonomy.json` in the frozen snapshot of his
 * railway.  This reads the snapshot's copy, so the fixture and the manual test are the same bytes.
 *
 * **Driven at `importLegacy` rather than through the menu**, which is where the gap-filling rule lives;
 * `AutonomyViewerPanel.importLegacyGraph` calls it and saves on the next line.  What the railway is left
 * for is whether the menu item reaches it, which is MT-298's own step 1.
 *
 * MUTATION: make the import overwrite rather than gap-fill - write the file's value unconditionally -
 * and the hand-made change is lost, which is the claim below.
 *
 * @author Adam
 */
public class testASecondImportFillsGapsAndDoesNotOverwrite
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static AutonomySession session;

    private static org.json.JSONObject legacy;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        File file = new File(new File(sandbox.getFolder(),
            "config" + File.separator + "autonomy_legacy"), "autonomy.json");

        if (!file.exists())
        {
            throw new SkipException("the snapshot carries no 2.7.4c file at " + file);
        }

        legacy = new org.json.JSONObject(new String(
            java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));

        model = MarklinControlStation.init(null, true, false, false, false);

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        // A configuration of its own, as the legacy-import test does: the import gap-fills, so
        // importing over the setup the snapshot already carries would prove nothing about gaps.
        session.getStore().createConfiguration("MT298", null);
        session.getStore().setActiveConfiguration("MT298");
        session.rebuild();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (model != null) model.stop();

        if (sandbox != null) sandbox.close();
    }

    /**
     * A change made between two imports of the same file is still there afterwards.
     *
     * @throws Exception from the import
     */
    @Test
    public void testAHandMadeChangeSurvivesASecondImport() throws Exception
    {
        AutonomySession.LegacyImport first = session.importLegacy(legacy);

        assertNotNull(first, "the first import returned nothing at all");

        session.rebuild();

        // SOMETHING THE FILE HAS AN OPINION ABOUT, so a second import has a reason to touch it.
        TileKey named = null;

        // FROM THE REDUCTION, not the diagram: `assignMaxTrainLength` refuses a square the reduction
        // does not carry as a point, and the first square that merely LOOKS like a station is not
        // necessarily one of those.
        for (TileKey square : session.getReducer().getPoints().keySet())
        {
            String station = session.getStore().getPointName(square);

            // AND ONE THE FILE HAS AN OPINION ABOUT, which is the only kind a second import could
            // overwrite.  A square the file says nothing about is skipped by the carry either way, so
            // choosing one of those made the mutation invisible - the first draft of this did, and the
            // overwrite mutation passed it.
            if (station != null && !station.trim().isEmpty() && session.getStore().isStation(square)
                && maxOf(square) > 0)
            {
                named = square;

                break;
            }
        }

        if (named == null)
        {
            throw new SkipException("the import gave no named station a maximum on this diagram, so there"
                + " is nothing a second import could overwrite");
        }

        // THE CORRECTION, of the kind somebody makes between two imports.
        String wasCalled = session.getStore().getPointName(named);
        // THE CORRECTION: a different limit from the file's, set the way the editor sets one.
        //
        // Not through `assignMaxTrainLength`, which is the bulk walk's door and fills only a square that
        // has NO maximum - so it cannot express the case this is about, which is the operator disagreeing
        // with the file.
        int corrected = maxOf(named) + 3;

        session.setPointProperty(named, "maxTrainLength", corrected);
        session.rebuild();

        assertEquals(maxOf(named), corrected,
            "precondition: the square did not keep the maximum it accepted");

        Map<TileKey, Integer> lengthsBefore = new LinkedHashMap<>();

        for (TileKey measured : session.tilesWithALength())
        {
            lengthsBefore.put(measured, session.getStore().getTileLength(measured));
        }

        // AND THE SAME FILE AGAIN, which is what somebody does when they are not sure the first took.
        AutonomySession.LegacyImport second = session.importLegacy(legacy);

        assertNotNull(second, "the second import returned nothing at all");

        session.rebuild();

        assertEquals(maxOf(named), corrected,
            "the second import wrote over a maximum set by hand between the two.  An import fills what"
            + " is missing; it does not replace what is there, and somebody who imports twice because"
            + " they are unsure the first took would silently lose the correction (MT-298)");

        assertEquals(session.getStore().getPointName(named), wasCalled,
            "the second import renamed a station the first had already named");

        // AND IT DID NOT MOVE THE LENGTHS EITHER, which is the other thing an overwrite would show.
        for (Map.Entry<TileKey, Integer> was : lengthsBefore.entrySet())
        {
            assertEquals(session.getStore().getTileLength(was.getKey()), was.getValue().intValue(),
                "the second import changed the measured length of " + was.getKey());
        }
    }

    /**
     * A square's maximum train length, as the session reports it.
     *
     * Read through `getPointProperty`, which is how the session's own rules read it - there is no typed
     * accessor, and a test inventing one would be asking a different question.
     *
     * @param square the station
     * @return its maximum, or 0 where it has none
     */
    private static int maxOf(TileKey square)
    {
        Object value = session.getPointProperty(square, "maxTrainLength");

        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
