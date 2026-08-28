package regression;

import java.io.File;
import java.util.*;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;

/**
 * OB-127: a failed switch to a Central Station layout asked about pages that had just been loaded.
 *
 * Adam: "several offers to init a new layout when I select 'switch to CS layout' and it fails.  then a
 * warning window showing the invalid 5 page list pops up and vanishes on its own."
 *
 * The switch stores an EMPTY STRING in the layout-path preference and clears the layout list. Two
 * methods then read that preference and disagreed about what the empty string meant: `isLocalLayout`
 * read it with an empty-string default and answered "not local", while `getLocalLayoutPath` read it
 * with a null default and handed the empty string straight back. An empty path is not nothing -
 * `new File("")` is the working directory - so the index question was asked about whatever index was
 * lying there, against a layout list that had just been emptied, and every page in it came back
 * absent.
 *
 * Two halves, tested separately because they fail separately: the mechanism, and the agreement.
 *
 * @author Adam
 */
public class testSwitchingToACentralStationLayout
{
    /**
     * An index plus an emptied layout list reports every page in the index as absent.
     *
     * This is the reproduction, and it needs no Central Station and no window: the pages Adam was
     * shown are exactly what this returns. It is not asserting a defect - this function is behaving
     * correctly - it is pinning WHY the wrong caller must never reach it.
     */
    @Test
    public void testAnEmptyLayoutListMakesEveryIndexedPageAbsent() throws Exception
    {
        File folder = java.nio.file.Files.createTempDirectory("ob127").toFile();

        folder.deleteOnExit();

        List<String> five = Arrays.asList("Main", "Yard", "Depot", "Upper", "Branch");

        LayoutDiagram.writeLayoutIndex(folder.getAbsolutePath(), five, null, 0);

        assertEquals(LayoutDiagram.readLayoutIndexIds(folder.getAbsolutePath()).size(), 5,
            "the fixture index was not written, so nothing below is asking anything");

        List<String> absent = LayoutDiagram.pagesTheIndexWouldDrop(
            folder.getAbsolutePath(), new ArrayList<String>(), null, null);

        assertEquals(absent.size(), 5,
            "an index of five pages asked against an empty layout list no longer reports five "
            + "absences - the reproduction for OB-127 has stopped reproducing, so read the ticket "
            + "before assuming this is an improvement: " + absent);

        // And the path the switch actually leaves behind names no index, so the question is empty.
        assertTrue(LayoutDiagram.pagesTheIndexWouldDrop(null, new ArrayList<String>(), null, null)
            .isEmpty(), "a null layout path finds an index somewhere, which is the failure mode "
            + "OB-127 is about - it should find nothing at all");
    }

    /**
     * The two readers of the layout-path preference agree about the empty string.
     *
     * `isLocalLayout` is the one that was right. Whatever `getLocalLayoutPath` hands back has to mean
     * the same thing, because eight callers use it and only three of them defended against the empty
     * string by hand.
     *
     * MUTATION: putting the null default back on `getLocalLayoutPath` fails this.
     */
    @Test
    public void testTheLayoutPathReadersAgreeAboutEmpty() throws Exception
    {
        String ui = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int accessor = ui.indexOf("private String getLocalLayoutPath()");

        assertTrue(accessor >= 0, "getLocalLayoutPath is gone, so this test reads nothing");

        String body = ui.substring(accessor, ui.indexOf("}", ui.indexOf("{", accessor)) + 1);

        assertTrue(body.contains("isEmpty()"),
            "getLocalLayoutPath hands the stored value back without asking whether it is empty. "
            + "Switching to a Central Station layout stores an empty string there, and an empty path "
            + "is the working directory - so the page-index question gets asked about whatever index "
            + "is lying in it (OB-127)");

        // The gate all three page writers come through refuses when there is no local layout.
        int settle = ui.indexOf("private java.util.Collection<String> settleAbsentPages(");

        assertTrue(settle >= 0, "settleAbsentPages is gone");

        String gate = ui.substring(settle, ui.indexOf("final java.util.List<String> absent", settle));

        assertTrue(gate.contains("!isLocalLayout()"),
            "settleAbsentPages no longer refuses when the layout is not a local one, so a failed "
            + "switch to a Central Station layout can reach the index question again and ask the "
            + "operator whether pages they were looking at a moment ago have been deleted - and "
            + "answering wrongly retires the ids their autonomy settings hang off");
    }
}
