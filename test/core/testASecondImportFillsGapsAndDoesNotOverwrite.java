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
     * The import's {4} counts squares that had a name before it, and the old points that shared a square are said apart
     * (RSA44-C3).
     *
     * A second old point on a sensor - a station and its approach guard, the ordinary shape of an old file - found its
     * square already named by the same import, and was counted in {4} as a square that "already had a name and was left
     * alone", its own name dropped unsaid.  On the frozen railway's file, onto setup with no names at all: {4} is 0, and
     * the points the import could not name for sharing a square are listed, each with the name its square kept.
     *
     * MUTATION: count a square this import named as one that had a name, or drop the shared point unsaid, and this fails.
     *
     * @throws Exception from the import
     */
    @Test
    public void testAnImportSaysWhichOldPointsSharedASquare() throws Exception
    {
        File empty = java.nio.file.Files.createTempDirectory("tc-shared-squares").toFile();

        try
        {
            AutonomySession bare = new AutonomySession(empty);

            bare.open(support.LayoutSandbox.wiredPages(model));
            bare.getStore().createConfiguration("Shared", null);
            bare.getStore().setActiveConfiguration("Shared");
            bare.rebuild();

            assertTrue(bare.getStore().getNamedTiles().isEmpty(), "precondition: a square has a name before the import");

            AutonomySession.LegacyImport result = bare.importLegacy(legacy);

            assertTrue(result.matched > 0, "precondition: the import named nothing");

            assertEquals(result.skipped, 0, "no square had a name before the import, and its message says " + result.skipped
                + " squares already had one and were left alone (RSA44-C3)");

            java.util.List<?> notKept = (java.util.List<?>) result.getClass().getField("namesNotKept").get(result);

            org.testng.Assert.assertFalse(notKept.isEmpty(), "the old points that shared a square with another are not said - their names"
                + " dropped unsaid (RSA44-C3)");

            for (Object line : notKept)
            {
                assertTrue(String.valueOf(line).contains(" ("), "a shared point's line does not name the square's kept name: "
                    + line);
            }
        }
        finally
        {
            org.testng.Assert.assertTrue(deleteTree(empty) || !empty.exists(), "could not delete " + empty);
        }
    }

    /**
     * A second import of the same file counts in {4} the squares that had a name, once each, and says again the old points
     * that shared a square (RSA45-C1).
     *
     * Names are shared by every configuration, so an import into a diagram that already has its names - a second import
     * of the same file (MT-298), or an old file brought into a new configuration of a named railway - finds every square
     * named before it.  Such a square was counted once for each old point on it: the frozen railway's file, imported
     * twice, said 18 squares already had a name, of the 16 it had named.
     *
     * MUTATION: count a square named before the import once for each old point on it, or leave its further points unsaid,
     * and this fails.
     *
     * @throws Exception from the import
     */
    @Test
    public void testASecondImportCountsSquaresNotPoints() throws Exception
    {
        File empty = java.nio.file.Files.createTempDirectory("tc-squares-not-points").toFile();

        try
        {
            AutonomySession bare = new AutonomySession(empty);

            bare.open(support.LayoutSandbox.wiredPages(model));
            bare.getStore().createConfiguration("Twice", null);
            bare.getStore().setActiveConfiguration("Twice");
            bare.rebuild();

            AutonomySession.LegacyImport first = bare.importLegacy(legacy);

            org.testng.Assert.assertFalse(first.namesNotKept.isEmpty(), "precondition: no two old points share a square,"
                + " so squares and points count alike");

            AutonomySession.LegacyImport second = bare.importLegacy(legacy);

            assertEquals(second.matched, 0, "precondition: the second import named a square the first left without a name");

            assertEquals(second.skipped, first.matched, "the second import says " + second.skipped + " squares already had"
                + " a name, where the first named " + first.matched + " - a square counted once for each old point on it"
                + " (RSA45-C1)");

            assertEquals(second.namesNotKept, first.namesNotKept, "the second import does not say the old points that"
                + " shared a square as the first did (RSA45-C1)");
        }
        finally
        {
            org.testng.Assert.assertTrue(deleteTree(empty) || !empty.exists(), "could not delete " + empty);
        }
    }

    /**
     * One old point that shared a square, or one thing left behind, is said in the singular - in the form each language
     * takes for the number (`I18n.countForm`).  The import's dialog said "1 old points" and "1 things" (Adam, 2026-10-06:
     * *"Fix them"*, on RSA45's notes).
     *
     * MUTATION: give either sentence one form for every number, and this fails.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testOneIsSaidInTheSingular() throws Exception
    {
        for (String sentence : new String[] {"sharedSquaresSentence", "leftBehindSentence"})
        {
            java.lang.reflect.Method say = org.traincontrol.gui.AutonomyViewerPanel.class.getDeclaredMethod(sentence,
                int.class);

            say.setAccessible(true);

            String one = (String) say.invoke(null, 1);
            String two = (String) say.invoke(null, 2);

            assertTrue(one.contains("1") && two.contains("2"), "precondition: " + sentence + " does not say its number: "
                + one + " / " + two);

            org.testng.Assert.assertNotEquals(one, two.replace("2", "1"), sentence + " says one as it says two, in the"
                + " plural: " + one);
        }
    }

    /** Deletes a folder and everything in it. */
    private static boolean deleteTree(File file)
    {
        File[] inside = file.listFiles();

        if (inside != null) for (File each : inside) deleteTree(each);

        return file.delete();
    }

    /**
     * An old autonomy.json is imported into the configuration named at the prompt, and the one in use is left as it was
     * (Adam, 2026-09-25, choosing between honouring the name and not asking for one: *"(a)"*).
     *
     * The Import asks for a configuration name, and warns when it would replace one - and on a layout that already had
     * configurations the old file's placements, homes and facings were then written into the one in use, the name thrown
     * away.  On the frozen railway: a second session, its own configuration in use, the file imported under a new name.
     *
     * MUTATION: write into the configuration in use, or have the door import before choosing, and this fails.
     *
     * @throws Exception from the import or the reflection
     */
    @Test
    public void testAnImportGoesIntoTheConfigurationNamed() throws Exception
    {
        AutonomySession fresh = new AutonomySession(sandbox.getFolder());

        fresh.open(support.LayoutSandbox.wiredPages(model));

        String inUse = fresh.getStore().getActiveConfiguration();

        assertNotNull(inUse, "precondition: the frozen railway has no configuration in use");

        String before = fresh.getStore().getConfiguration(inUse).toString();

        java.lang.reflect.Method choose = org.traincontrol.gui.AutonomyViewerPanel.class.getDeclaredMethod(
            "activateTheConfigurationNamed", org.traincontrol.automationui.AutonomyCompanionStore.class, String.class,
            String.class);

        choose.setAccessible(true);

        assertEquals(choose.invoke(null, fresh.getStore(), " Imported from 2.7 ", "Autonomy 9"), "Imported from 2.7",
            "the configuration imported into is not the one named at the prompt");

        assertEquals(fresh.getStore().getActiveConfiguration(), "Imported from 2.7", "the configuration named at the prompt"
            + " is not the one the import writes into");

        fresh.importLegacy(legacy);

        assertEquals(fresh.getStore().getConfiguration(inUse).toString(), before, "the import wrote into " + inUse + ", the"
            + " configuration in use, where the name typed was another");

        assertTrue(!fresh.placementsAutonomyWillWrite().isEmpty(), "the configuration named at the prompt got none of"
            + " the file's placements");

        // AND THE DOOR CHOOSES BEFORE THE IMPORT WRITES: the rule above is only as good as the one call that asks it.
        String door = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/AutonomyViewerPanel.java")), java.nio.charset.StandardCharsets.UTF_8);

        int start = door.indexOf("private void importLegacyGraph(");
        int chosen = door.indexOf("activateTheConfigurationNamed(session().getStore(), name", start);
        int writes = door.indexOf("session().importLegacy(", start);

        assertTrue(start > 0 && writes > start, "precondition: the old-file import door is not where it was");

        assertTrue(chosen > start && chosen < writes, "the Import door writes the old file before choosing the"
            + " configuration named at the prompt, or never asks");
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
