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
 * page list (the index file's order since FR-175; Adam: "let's make artikel be the sorted index of the page" when
 * that was name order), so deleting or moving a page changes what an arrow means, and
 * `LayoutDiagram.writeIndexAndKeepLinksAimed` re-aims them.  A rename keeps its slot and an add, a duplicate and a
 * combine append, so they move no position.
 *
 * It is driven at `writeIndexAndKeepLinksAimed` directly, where any list can be handed over, and an arrow's
 * destination is read off the new list as handed in, which is the order the model will list.
 */
public class testTheArrowsKeepTheirAim
{
    /**
     * Adding a page whose name would sort into the MIDDLE leaves every arrow on the same page: the page goes at the
     * end of the list, whatever its name.  The name is the one Combine Linked Pages offers by default
     * (`"<page> and neighbors"`).
     *
     * MUTATION: sort `before` and `after` in `repointLinksForNewOrder` again, and this fails with the arrow on
     * "2 - Bottom".
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
            + ".  A link holds a position in the page list, and a new page is appended to it (FV3-A2)");
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

        // The arrow points at 2 - Bottom, which is position 1 of the old list.
        Aimed aimed = repoint(was, now, renamed, 1);

        assertEquals(aimed.destination, "9 - Bottom",
            "a renamed page is the same page under another name and its arrows should follow it; this "
            + "one now points at " + aimed.destination);
    }

    /**
     * Moving a page re-aims the arrows at it: with 3 - Top Parking moved up one, an arrow that pointed at it (position
     * 2) points at position 1, and the page it now sits beside is not mistaken for it.
     *
     * MUTATION: sort `before` and `after` in `repointLinksForNewOrder` again, and the arrow stays on position 2, which
     * is now "2 - Bottom".
     */
    @Test
    public void testMovingAPageKeepsTheArrowsAimed() throws Exception
    {
        List<String> was = Arrays.asList("1 - Main", "2 - Bottom", "3 - Top Parking");

        List<String> now = Arrays.asList("1 - Main", "3 - Top Parking", "2 - Bottom");

        Aimed aimed = repoint(was, now, null, 2);

        assertEquals(aimed.destination, "3 - Top Parking", "the arrow pointed at 3 - Top Parking and now points at "
            + aimed.destination);

        assertEquals(aimed.raw, 1, "the arrow is not on the moved page's new position");
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
     * Since FR-175 an added page goes at the end, so no position moves and the arrow stays on 2.
     *
     * MUTATION: sort `layoutList` before the write in `LayoutPageEdit.renameOrDuplicate`, and the new page takes a
     * slot before the arrow's page and the file says 3.
     */
    @Test
    public void testAddingAPageCorrectsTheFileItWasInvokedFrom() throws Exception
    {
        assertArrowOnDiskAfterGesture("add", 2);
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
        assertArrowOnDiskAfterGesture("duplicate", 2);
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
        // 1 - Main is renamed, in its own slot, so no position moves: its first arrow still points at 3 - Top Parking,
        // position 2, in the file the rename writes under the new name.
        assertArrowOnDiskAfterGesture("rename", 2);
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
     * would fix one of them.  `was` is the old page list, `now` the new, `pointsAt` indexes into `was`.
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

            // Resolved through the list a link's number actually indexes into: the new page list, in its order.
            out.destination = out.raw >= 0 && out.raw < now.size() ? now.get(out.raw) : "(nothing)";

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
     * position 2 of the five; none of these gestures moves a position (a new page is appended, a rename keeps its slot), so
     * the file written must still say 2.
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
