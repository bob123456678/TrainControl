package regression;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * An arrow on the diagram points at the same page after the page list changes.  A link tile holds a POSITION in the
 * name-sorted page list (Adam: "let's make artikel be the sorted index of the page"), so adding, renaming,
 * duplicating, deleting or combining a page changes what every arrow means, and
 * `LayoutDiagram.writeIndexAndKeepLinksAimed` re-aims them.
 *
 * The first version resolved the destination against the list the CALLER handed in, and four of the five gestures
 * hand over a list that is not sorted (a rename puts the new name in the old slot; an add, a duplicate and a combine
 * append).  So it is driven at `writeIndexAndKeepLinksAimed` directly, where an unsorted list can be handed over.
 */
public class testTheArrowsKeepTheirAim
{
    /**
     * Adding a page whose name sorts into the MIDDLE leaves every arrow on the same page.  The name is the one
     * Combine Linked Pages offers by default (`"<page> and neighbors"`), which sorts right after the page it was made
     * from rather than last, so the caller's appended list differs from the sorted one.
     *
     * MUTATION: re-aim against `layoutList` as handed in, instead of a sorted copy, and this fails with
     * the arrow on "2 - Bottom".
     */
    @Test
    public void testAddingAPageInTheMiddleLeavesTheArrowsAimed() throws Exception
    {
        List<String> was = Arrays.asList("1 - Main", "2 - Bottom", "3 - Top Parking");

        // The caller APPENDS, which is what LayoutPageEdit and combineLinkedPages both do.
        List<String> now = new ArrayList<>(was);

        now.add("1 - Main and neighbors");

        Aimed aimed = repoint(was, now, null, 2);

        assertEquals(aimed.destination, "3 - Top Parking",
            "the arrow pointed at 3 - Top Parking and now points at " + aimed.destination
            + ".  A link holds a position in the NAME-SORTED list, and the list the caller passes is "
            + "appended rather than sorted (FV3-A2)");
    }

    /**
     * A renamed page keeps its arrows, even when the new name sorts somewhere else entirely.
     *
     * MUTATION: drop the rename substitution and the arrow goes to -1, because the old name is in
     * neither list.
     */
    @Test
    public void testARenamedPageKeepsItsArrows() throws Exception
    {
        List<String> was = Arrays.asList("1 - Main", "2 - Bottom", "3 - Top Parking");

        // LayoutPageEdit puts the new name back IN THE OLD SLOT, deliberately.
        List<String> now = Arrays.asList("1 - Main", "9 - Bottom", "3 - Top Parking");

        Map<String, String> renamed = new LinkedHashMap<>();

        renamed.put("2 - Bottom", "9 - Bottom");

        // The arrow points at 2 - Bottom, which is position 1 of the old sorted list.
        Aimed aimed = repoint(was, now, renamed, 1);

        assertEquals(aimed.destination, "9 - Bottom",
            "a renamed page is the same page under another name and its arrows should follow it; this "
            + "one now points at " + aimed.destination);
    }

    /**
     * An arrow whose page is deleted points at nothing, rather than at whatever took its place (Adam: "set the ID to
     * -1.  This shouldn't throw any errors, and simply resolve to nothing when clicked").
     */
    @Test
    public void testAnArrowToADeletedPagePointsAtNothing() throws Exception
    {
        List<String> was = Arrays.asList("1 - Main", "2 - Bottom", "3 - Top Parking");

        List<String> now = Arrays.asList("1 - Main", "3 - Top Parking");

        // Pointing at 2 - Bottom, which is about to go.
        Aimed aimed = repoint(was, now, null, 1);

        assertEquals(aimed.raw, -1,
            "the arrow pointed at a page that has been deleted and now points at " + aimed.destination
            + ", which is whatever slid into its place");

        assertTrue(aimed.tile.linksNowhere(), "and it reports itself as linking nowhere");
    }

    /** An arrow that already pointed at nothing is left alone. */
    @Test
    public void testAnArrowThatPointsNowhereIsNotDisturbed() throws Exception
    {
        List<String> was = Arrays.asList("1 - Main", "2 - Bottom");

        List<String> now = Arrays.asList("1 - Main", "2 - Bottom", "3 - Top Parking");

        Aimed aimed = repoint(was, now, null, -1);

        assertEquals(aimed.raw, -1, "a link to nothing became a link to " + aimed.destination);
    }

    /**
     * Add Page leaves the page it was invoked from aimed correctly, ON DISK.  The tests above drive the rule; these
     * drive the gesture, because the first fix repaired the arithmetic and left the sequence alone: all three of Add,
     * Duplicate and Rename wrote a page file before the arrows were corrected.  Asserted on the FILE, because the
     * in-memory correction is thrown away by the refresh that follows each gesture.
     *
     * Add Page blanks the CURRENT page's object to write it out as the new blank page, so a re-aim that ran after that
     * saw a page with no tiles and the original file was never corrected.
     *
     * MUTATION: move the re-aim back below `page.saveChanges(newLayoutName, duplicate)` and this fails
     * with the arrow still on 2.
     */
    @Test
    public void testAddingAPageCorrectsTheFileItWasInvokedFrom() throws Exception
    {
        assertArrowOnDiskAfterGesture("add", 3);
    }

    /**
     * Duplicate Page writes the COPY with corrected arrows: it is written from the source page and is not in the model
     * when a later re-aim runs.
     *
     * MUTATION: as above.
     */
    @Test
    public void testDuplicatingAPageCorrectsTheCopy() throws Exception
    {
        assertArrowOnDiskAfterGesture("duplicate", 3);
    }

    /**
     * A renamed page's own arrows are corrected in the file the rename writes: the re-aim skipped the renamed page on
     * the ground that the correction travels with the object, which is true of the OBJECT and false of the ORDER.
     *
     * MUTATION: as above.
     */
    @Test
    public void testARenamedPageKeepsItsOwnArrowsOnDisk() throws Exception
    {
        // 1 - Main is renamed, not 2 - Bottom: Bottom's arrow points at Main, which does not move, so that case would
        // pass with the re-aim deleted.  Main's own first arrow points at 3 - Top Parking, which moves from position 2
        // to 1.
        assertArrowOnDiskAfterGesture("rename", 1);
    }

    /** What an arrow ended up aimed at. */
    private static class Aimed
    {
        int raw;
        String destination;
        LayoutDiagramComponent tile;
    }

    /**
     * Builds a one-page layout carrying a single arrow, runs the real re-aim over it, and reports where the arrow
     * ended up.  Built by hand because what is under test is the ARITHMETIC between two page lists, and a fixture
     * would fix one of them.  `was` is the sorted list, `now` the caller's (deliberately unsorted), `pointsAt` indexes
     * into `was`.
     */
    private static Aimed repoint(List<String> was, List<String> now, Map<String, String> renamed,
        int pointsAt) throws Exception
    {
        File folder = java.nio.file.Files.createTempDirectory("tc-arrows").toFile();

        try
        {
            File config = new File(folder, "config");

            assertTrue(config.mkdirs(), "precondition: the config folder has to be made");

            List<LayoutDiagram> pages = new ArrayList<>();

            for (String name : was)
            {
                pages.add(new LayoutDiagram(name, 4, 4, null, null));
            }

            // The arrow goes on the FIRST page, and points where the caller said.
            LayoutDiagram main = pages.get(0);

            main.addComponent(LayoutDiagramComponent.componentType.LINK, 1, 1, 0, 0,
                Math.max(pointsAt, 0), pointsAt, null, null);

            LayoutDiagramComponent tile = main.getComponent(1, 1);

            tile.setLinkedPageIndex(pointsAt);

            assertEquals(tile.getRawAddress(), pointsAt, "precondition: the arrow starts where it says");

            LayoutDiagram.writeIndexAndKeepLinksAimed(folder.getAbsolutePath(), now, renamed, 0, null,
                pages);

            Aimed out = new Aimed();

            out.tile = tile;
            out.raw = tile.getRawAddress();

            // Resolved through the list a link's number actually indexes into, which is the sorted one.
            List<String> sorted = new ArrayList<>(now);

            java.util.Collections.sort(sorted);

            out.destination = out.raw >= 0 && out.raw < sorted.size() ? sorted.get(out.raw) : "(nothing)";

            return out;
        }
        finally
        {
            deleteTree(folder);
        }
    }

    /** Removes a temporary folder and everything in it. */
    private static void deleteTree(File where)
    {
        File[] children = where.listFiles();

        if (children != null)
        {
            for (File child : children) deleteTree(child);
        }

        where.delete();
    }

    /**
     * Runs one real page gesture over a three-page layout and reports where the arrow points in the FILE afterwards.
     * The fixture is `live-snapshot`, whose `1 - Main` carries three arrows, the first aimed at `3 - Top Parking`,
     * position 2 of the five; adding a page that sorts second moves that destination to position 3.
     */
    private static void assertArrowOnDiskAfterGesture(String gesture, int expected) throws Exception
    {
        // OPENED INSIDE THE TRY: a sandbox takes the machine-global layout preference and gives it back in close(), so
        // anything that throws between the open and the try leaves it pointing at a folder under %TEMP%.
        support.LayoutSandbox sandbox = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            org.traincontrol.marklin.MarklinControlStation model =
                support.TestStation.simulated();

            model.stop();

            String from = "1 - Main";

            String made = "rename".equals(gesture) ? "9 - Main" : "1 - Main and neighbors";

            String subject = from;

            java.util.List<String> layoutList = new ArrayList<>(model.getLayoutList());

            org.traincontrol.automationui.LayoutPageEdit.renameOrDuplicate(
                layoutList, model.getLayout(subject), sandbox.getFolder().getAbsolutePath(), subject, made,
                "rename".equals(gesture), !"rename".equals(gesture), "add".equals(gesture), null, model,
                null);

            // WHICH FILE HOLDS THE ANSWER IS DIFFERENT FOR EACH GESTURE.
            //   Add       the new page is blank, so the file that matters is the ORIGINAL, which the old order never
            //             corrected because Add blanks that object before the re-aim can see it.
            //   Duplicate the COPY, which is written from the source and was never corrected.
            //   Rename    the renamed page's own new file.
            String page = "add".equals(gesture) ? subject : made;

            assertEquals(arrowInFile(sandbox, page), expected,
                "after " + gesture + ", the arrow on " + page + " points at position "
                + arrowInFile(sandbox, page) + " in its FILE, where " + expected + " is correct.  The "
                + "in-memory correction is discarded by the refresh that follows every one of these "
                + "gestures, so only the file matters (T10-B1, T10-B2, T10-B3)");

            // A duplicate leaves TWO files that must agree: the copy and the page it was made from.
            if ("duplicate".equals(gesture))
            {
                assertEquals(arrowInFile(sandbox, subject), expected,
                    "the copy and the page it was made from disagree about where the same arrow goes");
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** The `.artikel` of the first `pfeil` tile in a page's file. */
    private static int arrowInFile(support.LayoutSandbox sandbox, String page) throws Exception
    {
        File file = new File(new File(sandbox.getFolder(), "config/gleisbilder"), page + ".cs2");

        assertTrue(file.isFile(), "no file for " + page + " at " + file);

        String body = new String(java.nio.file.Files.readAllBytes(file.toPath()),
            java.nio.charset.StandardCharsets.UTF_8);

        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\\.typ=pfeil[\\s\\S]*?\\.artikel=(-?\\d+)").matcher(body);

        assertTrue(m.find(), "no arrow tile in " + file);

        return Integer.parseInt(m.group(1));
    }
}
