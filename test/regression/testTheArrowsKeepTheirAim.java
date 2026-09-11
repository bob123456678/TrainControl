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
 * An arrow on the diagram points at the same page after the page list changes.
 *
 * **A link tile holds a POSITION in the name-sorted page list** (Adam, 2026-09-10: *"right now, let's
 * make artikel be the sorted index of the page"*), so adding, renaming, duplicating, deleting or
 * combining a page changes what every arrow means. `LayoutDiagram.writeIndexAndKeepLinksAimed` re-aims
 * them, and this is what says it works.
 *
 * **Written because the fix shipped with no test and was wrong** (FV3-A2). It resolved the destination
 * against the list the CALLER handed in, and four of the five gestures hand over a list that is not
 * sorted: a rename puts the new name back in the old slot, an add, a duplicate and a combine append.
 * So the re-aim was a no-op in the two cases it was written from, and nothing in the suite noticed -
 * `grep` for `repointPageLinks|writeIndexAndKeepLinksAimed` over `test/` returned nothing at all.
 *
 * Driven at `writeIndexAndKeepLinksAimed` rather than through the window: that method is where the rule
 * lives, it is what both page-editing classes call, and it takes the caller's list as an argument - so
 * an unsorted list is exactly the input that has to be pinned, and it can be handed over directly.
 */
public class testTheArrowsKeepTheirAim
{
    /**
     * Adding a page whose name sorts into the MIDDLE leaves every arrow on the same page.
     *
     * The name is the one Combine Linked Pages offers by default - `"<page> and neighbours"` - which
     * sorts immediately after the page it was made from rather than last. That is what makes the
     * caller's appended list differ from the sorted one.
     *
     * MUTATION: re-aim against `layoutList` as handed in, instead of a sorted copy, and this fails with
     * the arrow on "2 - Bottom" - the same wrong answer the finding reported.
     */
    @Test
    public void testAddingAPageInTheMiddleLeavesTheArrowsAimed() throws Exception
    {
        List<String> was = Arrays.asList("1 - Main", "2 - Bottom", "3 - Top Parking");

        // The caller APPENDS, which is what LayoutPageEdit and combineLinkedPages both do.
        List<String> now = new ArrayList<>(was);

        now.add("1 - Main and neighbours");

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
     * An arrow whose page is deleted points at nothing, rather than at whatever took its place.
     *
     * Adam, 2026-09-10: *"set the ID to -1.  This shouldn't throw any errors, and simply resolve to
     * nothing when clicked.  Then, the user can set it to the right page on their next edit."*
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

    /**
     * An arrow that already pointed at nothing is left alone.
     */
    @Test
    public void testAnArrowThatPointsNowhereIsNotDisturbed() throws Exception
    {
        List<String> was = Arrays.asList("1 - Main", "2 - Bottom");

        List<String> now = Arrays.asList("1 - Main", "2 - Bottom", "3 - Top Parking");

        Aimed aimed = repoint(was, now, null, -1);

        assertEquals(aimed.raw, -1, "a link to nothing became a link to " + aimed.destination);
    }

    /**
     * Add Page leaves the page it was invoked from aimed correctly, ON DISK (T10-B1).
     *
     * **The tests above drive the rule; these drive the gesture.** `FV3-A2` fixed the arithmetic of the
     * re-aim and left the sequence alone, and the test it shipped with called
     * `writeIndexAndKeepLinksAimed` directly - so the battery was green while all three of Add, Duplicate
     * and Rename wrote a page file before the arrows were corrected. A rule tested at the rule says
     * nothing about the call, which is the trap this project has hit before.
     *
     * Asserted on the FILE, because that is what survives: the in-memory correction is thrown away by the
     * refresh that follows every one of these gestures, so an assertion on the tile would have passed
     * while the railway kept the old numbers.
     *
     * Add Page blanks the CURRENT page's object to write it out as the new blank page. The re-aim used
     * to run after that, over a page with no tiles - so the original file, belonging to the page the
     * operator had open, was the one page never corrected.
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
     * Duplicate Page writes the COPY with corrected arrows (T10-B2).
     *
     * The copy is written from the source page as it stands and is not in the model when a later re-aim
     * runs, so nothing ever corrected it - the source and its copy disagreed about where the same arrow
     * goes.
     *
     * MUTATION: as above.
     */
    @Test
    public void testDuplicatingAPageCorrectsTheCopy() throws Exception
    {
        assertArrowOnDiskAfterGesture("duplicate", 3);
    }

    /**
     * A renamed page's own arrows are corrected in the file the rename writes (T10-B3).
     *
     * The re-aim deliberately skipped the renamed page, on the stated ground that the rename writes the
     * same object under its new name so the correction travels with it. That is true of the OBJECT and
     * false of the ORDER: the rename's write happened 128 lines earlier. Every page's arrows were
     * corrected except the renamed page's own.
     *
     * MUTATION: as above.
     */
    @Test
    public void testARenamedPageKeepsItsOwnArrowsOnDisk() throws Exception
    {
        // 1 - Main is renamed, not 2 - Bottom: the arrow on Bottom points at Main, which does not
        // move, so that case would pass with the re-aim deleted.  Main's own first arrow points at
        // 3 - Top Parking, which moves from position 2 to position 1 when Main leaves the front of the
        // alphabet.
        assertArrowOnDiskAfterGesture("rename", 1);
    }

    /**
     * What an arrow ended up aimed at.
     */
    private static class Aimed
    {
        int raw;
        String destination;
        LayoutDiagramComponent tile;
    }

    /**
     * Builds a one-page layout carrying a single arrow, runs the real re-aim over it, and reports where
     * the arrow ended up.
     *
     * The pages are built by hand rather than read from a fixture because what is under test is the
     * ARITHMETIC between two page lists, and a fixture would fix one of them.
     *
     * @param was the page list as it stands, name-sorted, as `getLayoutList` gives it
     * @param now the page list the caller passes to the writer - deliberately not sorted
     * @param renamed old name to new name, or null
     * @param pointsAt the arrow's stored number, indexing into `was`
     * @return where the arrow points afterwards
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

    /**
     * Removes a temporary folder and everything in it.
     */
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
     * Runs one real page gesture over a three-page layout and reports where the arrow points in the
     * FILE afterwards.
     *
     * The fixture is `live-snapshot`, a frozen copy of Adam's own five pages, whose `1 - Main` carries
     * three arrows - the first aimed at `3 - Top Parking`, position 2 of the five. Adding a page that
     * sorts second moves that destination to position 3.
     *
     * @param gesture "add", "duplicate" or "rename"
     * @param expected where that arrow must point afterwards
     */
    private static void assertArrowOnDiskAfterGesture(String gesture, int expected) throws Exception
    {
        support.LayoutSandbox sandbox = support.LayoutSandbox.open(
            support.Scenario.folderFor("live-snapshot"));

        try
        {
            org.traincontrol.marklin.MarklinControlStation model =
                org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

            model.stop();

            String from = "1 - Main";

            String made = "rename".equals(gesture) ? "9 - Main" : "1 - Main and neighbours";

            String subject = from;

            java.util.List<String> layoutList = new ArrayList<>(model.getLayoutList());

            org.traincontrol.automationui.LayoutPageEdit.renameOrDuplicate(
                layoutList, model.getLayout(subject), sandbox.getFolder().getAbsolutePath(), subject, made,
                "rename".equals(gesture), !"rename".equals(gesture), "add".equals(gesture), null, model,
                null);

            // The file, not the tile: a refresh throws the in-memory correction away.
            String page = "rename".equals(gesture) ? made : subject;

            assertEquals(arrowInFile(sandbox, page), expected,
                "after " + gesture + ", the arrow on " + page + " points at position "
                + arrowInFile(sandbox, page) + " in its FILE, where " + expected + " is correct.  The "
                + "in-memory correction is discarded by the refresh that follows every one of these "
                + "gestures, so only the file matters (T10-B1, T10-B2, T10-B3)");
        }
        finally
        {
            sandbox.close();
        }
    }

    /**
     * The `.artikel` of the first `pfeil` tile in a page's file.
     */
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
