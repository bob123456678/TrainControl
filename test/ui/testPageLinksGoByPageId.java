package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.util.I18n;

/**
 * FR-175: a link carries the ID of the page it goes to.  A layout from before that has links that hold a place in the
 * name-sorted list, and they go where they always went; the ID is attached in memory and written when a page is next
 * saved, beside the old number, which stays right for 3.0.8.
 *
 * The layouts are copies of the owner's old one (its IDs 5, 1, 2, 3, 4 sort differently from its names) and of the
 * live snapshot.
 */
public class testPageLinksGoByPageId extends support.PageWindowTest
{
    private static final Pattern ELEMENT = Pattern.compile("element\\n([\\s\\S]*?)(?=\\nelement\\n|$)");

    @Override
    protected File fixtureFor(java.lang.reflect.Method test)
    {
        return test.getName().contains("Snapshot") ? support.Scenario.folderFor("live-snapshot") : super.fixtureFor(test);
    }

    /**
     * Opening an old layout writes nothing, and every link goes to the page its number names in the name-sorted list,
     * which is where it went before links had IDs.  Each of the old layout's seven links would go to another page if
     * its number were read as an ID.
     *
     * MUTATION: `MarklinControlStation.attachLinks` reads a link's number as an ID (`getPageNameForId(raw)` for a
     * local layout), and links go to other pages.
     */
    @Test
    public void testAnOldLayoutIsOpenedWithNothingWrittenAndEveryLinkWhereItWent() throws Exception
    {
        opensUnchanged();
    }

    /** The same for the live snapshot. */
    @Test
    public void testTheLiveSnapshotIsOpenedWithNothingWrittenAndEveryLinkWhereItWent() throws Exception
    {
        opensUnchanged();
    }

    private void opensUnchanged() throws Exception
    {
        Map<String, String> before = layoutFiles();

        startTheWindow(5);

        assertEquals(changedBetween(before, layoutFiles()), "[]", "opening the layout wrote to the index or a page");

        assertEquals(targets(), whereTheFilesSend(), "a link does not go where its number sent it before IDs");

        assertTrue(targets().size() >= 7, "precondition: the layout has no links");
    }

    /**
     * A saved page writes each link's page ID and keeps the old number, so 3.0.8 reads the same layout: after a save
     * every number is the one it was, and read as 3.0.8 reads it (a place in the name-sorted list) every link goes
     * where it did; after a reload so does every link here.
     *
     * MUTATION: `exportToCS2TextFormat` writes no `.zielseite`, or writes the ID where `.artikel` is, and this fails.
     */
    @Test
    public void testASavedLayoutKeepsEveryAimAndWritesTheIdBesideTheOldNumber() throws Exception
    {
        startTheWindow(5);

        Map<String, String> sent = targets();

        Map<String, Integer> numbers = numbersInTheFiles();

        saveEveryPage();

        assertEquals(numbersInTheFiles(), numbers, "a save changed an old number, which 3.0.8 reads");

        assertEquals(readAsThe308Would(), sent, "3.0.8 would send a link elsewhere after the save");

        for (Map.Entry<String, Integer> link : idsInTheFiles().entrySet())
        {
            assertEquals(link.getValue(), Integer.valueOf(model.getPageIdOf(sent.get(link.getKey()))),
                "the ID written for the link at " + link.getKey() + " is not its page's");
        }

        assertEquals(idsInTheFiles().size(), numbers.size(), "a link was saved without its page ID");

        // For the 3.0.8 run by hand: the saved folder, copied where the environment says
        if (System.getenv("TC_SAVED_COPY") != null)
        {
            try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(sandbox.getFolder().toPath()))
            {
                for (java.nio.file.Path from : (Iterable<java.nio.file.Path>) walk::iterator)
                {
                    java.nio.file.Path to = new File(System.getenv("TC_SAVED_COPY")).toPath()
                        .resolve(sandbox.getFolder().toPath().relativize(from));

                    if (Files.isDirectory(from)) Files.createDirectories(to);
                    else Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        stopTheWindow();

        startTheWindow(5);

        assertEquals(targets(), sent, "a link goes elsewhere after a save and a reload");
    }

    /**
     * A page added to the folder by hand shifts every place in the name-sorted list, and a link that has been saved
     * goes by its ID, so it does not move.
     *
     * MUTATION: links go by their number whatever else they carry (`attachLinks` ignores `getLinkPageId`), and a
     * saved link goes to the page that took its place.
     */
    @Test
    public void testAPageAddedByHandDoesNotMoveASavedLink() throws Exception
    {
        startTheWindow(5);

        Map<String, String> sent = targets();

        saveEveryPage();

        stopTheWindow();

        addAPageByHand("0 - New", 9);

        startTheWindow(6);

        assertEquals(targets(), sent, "a saved link moved when a page was added by hand");
    }

    /**
     * A page taken out of the index by hand leaves the links that went to it saying so, not going to another page and
     * not silent; the others hold; and the page coming back with its ID brings its links back.
     *
     * MUTATION: a link whose page is gone falls back to its number (`attachLinks` drops the ID when no page has it),
     * and this fails.
     */
    @Test
    public void testAPageTakenOutAndBroughtBackTakesItsLinksWithIt() throws Exception
    {
        startTheWindow(5);

        Map<String, String> sent = targets();

        saveEveryPage();

        stopTheWindow();

        String index = new String(Files.readAllBytes(indexFile().toPath()), StandardCharsets.UTF_8).replace("\r", "");

        Files.write(indexFile().toPath(), index.replace("seite\n .id=2\n .name=3 - Top Parking\n", "")
            .getBytes(StandardCharsets.UTF_8));

        startTheWindow(4);

        Map<String, String> without = targets();

        int gone = 0;

        for (Map.Entry<String, String> link : sent.entrySet())
        {
            // The page taken out takes its own links with it
            if (link.getKey().startsWith("3 - Top Parking|")) continue;

            if (link.getValue().equals("3 - Top Parking"))
            {
                assertEquals(without.get(link.getKey()), "gone", "a link to the missing page does not say so");

                gone++;
            }
            else
            {
                assertEquals(without.get(link.getKey()), link.getValue(), "a link to another page moved");
            }
        }

        assertTrue(gone > 0, "precondition: nothing linked to the page taken out");

        assertTrue(someTooltip(I18n.t("layout.linkPageGone")), "no link's tooltip says its page is gone");

        stopTheWindow();

        Files.write(indexFile().toPath(), index.getBytes(StandardCharsets.UTF_8));

        startTheWindow(5);

        assertEquals(targets(), sent, "the links did not come back with their page");
    }

    /**
     * A page that cannot be read keeps its place in the list, and when it is readable again its own links, saved with
     * their IDs, go where they did although a page was added meanwhile.
     *
     * MUTATION: as for a page added by hand.
     */
    @Test
    public void testAnUnreadablePagesOwnLinksGoWhereTheyDidWhenItIsReadable() throws Exception
    {
        startTheWindow(5);

        Map<String, String> sent = targets();

        saveEveryPage();

        stopTheWindow();

        File main = new File(sandbox.getFolder(), "config/gleisbilder/1 - Main.cs2");

        byte[] good = Files.readAllBytes(main.toPath());

        Files.write(main.toPath(), new byte[] {0, 1, 2});

        addAPageByHand("0 - New", 9);

        startTheWindow(6);

        stopTheWindow();

        Files.write(main.toPath(), good);

        startTheWindow(6);

        assertEquals(targets(), sent, "a link of the page that could not be read goes elsewhere now");
    }

    /**
     * Renaming a page and copying one leave every link going where it did, and the copy has an ID of its own; deleting
     * a page leaves the links to it saying so.
     *
     * MUTATION: the copy is written with the original's ID, or a delete re-aims the links to the page that moved up,
     * and this fails.
     */
    @Test
    public void testRenameCopyAndDeleteHoldTheLinks() throws Exception
    {
        startTheWindow(5);

        Map<String, String> sent = targets();

        saveEveryPage();

        String path = sandbox.getFolder().getAbsolutePath();

        List<String> list = model.getLayoutList();

        org.traincontrol.automationui.LayoutPageEdit.renameOrDuplicate(list, model.getLayout("2 - Bottom"), path,
            "2 - Bottom", "2 - Lower", true, false, false, null, model);

        model.refreshLayouts();

        Map<String, String> renamed = new LinkedHashMap<>();

        for (Map.Entry<String, String> link : sent.entrySet())
        {
            renamed.put(link.getKey().replace("2 - Bottom|", "2 - Lower|"),
                link.getValue().equals("2 - Bottom") ? "2 - Lower" : link.getValue());
        }

        assertEquals(targets(), renamed, "a rename moved a link, or one that went to the renamed page");

        list = model.getLayoutList();

        org.traincontrol.automationui.LayoutPageEdit.renameOrDuplicate(list, model.getLayout("1 - Main"), path,
            "1 - Main", "1 - Main copy", false, true, false, null, model);

        model.refreshLayouts();

        assertNotEquals(model.getPageIdOf("1 - Main copy"), model.getPageIdOf("1 - Main"), "the copy kept the ID");

        for (Map.Entry<String, String> link : renamed.entrySet())
        {
            if (link.getKey().startsWith("1 - Main|"))
            {
                assertEquals(targets().get(link.getKey().replace("1 - Main|", "1 - Main copy|")), link.getValue(),
                    "the copy's link goes elsewhere than the original's");
            }
        }

        // Delete 3 - Top Parking the way the window does: its file and its index entry go, and links are re-aimed
        List<String> remaining = new ArrayList<>(model.getLayoutList());

        remaining.remove("3 - Top Parking");

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String name : model.getLayoutList()) pages.add(model.getLayout(name));

        model.getLayout("3 - Top Parking").deleteLayoutFile();

        for (LayoutDiagram changed : LayoutDiagram.writeIndexAndKeepLinksAimed(path, remaining, null, 0, null, pages))
        {
            changed.saveChanges(null, false);
        }

        model.refreshLayouts();

        for (Map.Entry<String, String> link : targets().entrySet())
        {
            String before = renamed.get(link.getKey());

            if (before == null) continue;

            assertEquals(link.getValue(), before.equals("3 - Top Parking") ? "gone" : before,
                "after the delete the link at " + link.getKey() + " goes to " + link.getValue());
        }
    }

    /**
     * Two pages of a local layout that share an ID are left alone on read and told apart at the next page edit: the page
     * the setup records under the ID keeps it, and the other takes a fresh one.  The claim reads the setup as it stands at
     * the edit, as the app rewrites it when the layout opens.
     *
     * MUTATION: `writeLayoutIndex` lets the later page in the list lose again (the tie broken by list order, not by
     * the setup), and the page the setup records loses its ID.
     */
    @Test
    public void testPagesSharingAnIdAreLeftOnReadAndTheSetupsPageKeepsItAtTheNextEdit() throws Exception
    {
        // 3 - Top Parking and 4 - Combined share ID 2
        String index = new String(Files.readAllBytes(indexFile().toPath()), StandardCharsets.UTF_8).replace("\r", "");

        Files.write(indexFile().toPath(), index.replace(" .id=3\n .name=4 - Combined", " .id=2\n .name=4 - Combined")
            .getBytes(StandardCharsets.UTF_8));

        Map<String, String> before = layoutFiles();

        startTheWindow(5);

        assertEquals(changedBetween(before, layoutFiles()), "[]", "reading a layout with a shared ID wrote to the index or a page");

        String recorded = LayoutDiagram.readSetupPageNames(sandbox.getFolder().getAbsolutePath()).get(2);

        assertTrue("3 - Top Parking".equals(recorded) || "4 - Combined".equals(recorded), "precondition: the setup records "
            + recorded + " under the shared ID");

        org.traincontrol.automationui.LayoutPageEdit.renameOrDuplicate(model.getLayoutList(), model.getLayout("5 - Test"),
            sandbox.getFolder().getAbsolutePath(), "5 - Test", "5 - Tested", true, false, false, null, model);

        Map<String, Integer> ids = idsInTheIndex();

        String other = "3 - Top Parking".equals(recorded) ? "4 - Combined" : "3 - Top Parking";

        assertEquals(ids.get(recorded), Integer.valueOf(2), "the page the setup records under the ID lost it: " + ids);

        assertTrue(ids.get(other) > 5, "the other page did not take a fresh ID: " + ids);

        assertEquals(new java.util.HashSet<>(ids.values()).size(), ids.size(), "an ID is still shared: " + ids);
    }

    /**
     * With no setup to say, the page first by name keeps a shared ID, whatever place the pages have in the file.
     *
     * MUTATION: as above, the later page in the file loses, and 2 - Bottom (second in the file) loses its ID.
     */
    @Test
    public void testPagesSharingAnIdWithNoSetupKeepItByName() throws Exception
    {
        Files.delete(new File(sandbox.getFolder(), "config/autonomy/setup.json").toPath());

        // File order: 3 - Top Parking, then 2 - Bottom, both ID 1
        Files.write(indexFile().toPath(), ("[gleisbild]\nversion\n .major=1\ngroesse\nseite\n .id=1\n .name=3 - Top Parking\n"
            + "seite\n .id=1\n .name=2 - Bottom\nseite\n .id=5\n .name=1 - Main\nseite\n .id=3\n .name=4 - Combined\n"
            + "seite\n .id=4\n .name=5 - Test\n").getBytes(StandardCharsets.UTF_8));

        startTheWindow(5);

        org.traincontrol.automationui.LayoutPageEdit.renameOrDuplicate(model.getLayoutList(), model.getLayout("5 - Test"),
            sandbox.getFolder().getAbsolutePath(), "5 - Test", "5 - Tested", true, false, false, null, model);

        Map<String, Integer> ids = idsInTheIndex();

        assertEquals(ids.get("2 - Bottom"), Integer.valueOf(1), "the page first by name lost the shared ID: " + ids);

        assertTrue(ids.get("3 - Top Parking") > 5, "the other page did not take a fresh ID: " + ids);
    }

    // ------------------------------------------------------------------------------------------------

    private void saveEveryPage() throws Exception
    {
        for (String page : model.getLayoutList()) model.getLayout(page).saveChanges(null, false);
    }

    private void addAPageByHand(String name, int id) throws Exception
    {
        Files.copy(new File(sandbox.getFolder(), "config/gleisbilder/5 - Test.cs2").toPath(),
            new File(sandbox.getFolder(), "config/gleisbilder/" + name + ".cs2").toPath());

        Files.write(indexFile().toPath(), (new String(Files.readAllBytes(indexFile().toPath()), StandardCharsets.UTF_8)
            + "seite\n .id=" + id + "\n .name=" + name + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private File indexFile()
    {
        return new File(sandbox.getFolder(), "config/gleisbild.cs2");
    }

    /** Where each link in the model goes: "page|x,y" to the page's name, "gone" or "none". */
    private Map<String, String> targets()
    {
        Map<String, String> out = new TreeMap<>();

        for (String page : model.getLayoutList())
        {
            for (LayoutDiagramComponent tile : model.getLayout(page).getAll())
            {
                if (tile != null && tile.isLink())
                {
                    out.put(page + "|" + tile.getX() + "," + tile.getY(), tile.getLinkPageName() != null
                        ? tile.getLinkPageName() : tile.linksToAPageThatIsGone() ? "gone" : "none");
                }
            }
        }

        return out;
    }

    private boolean someTooltip(String text)
    {
        for (String page : model.getLayoutList())
        {
            for (LayoutDiagramComponent tile : model.getLayout(page).getAll())
            {
                if (tile != null && tile.isLink() && tile.toSimpleString().contains(text)) return true;
            }
        }

        return false;
    }

    /** The page IDs in the index, by name. */
    private Map<String, Integer> idsInTheIndex() throws Exception
    {
        Map<String, Integer> ids = new LinkedHashMap<>();

        Integer id = null;

        for (String line : Files.readAllLines(indexFile().toPath(), StandardCharsets.UTF_8))
        {
            if (line.startsWith(" .id=")) id = Integer.valueOf(line.substring(5).trim());
            else if (line.startsWith(" .name=")) ids.put(line.substring(7).trim(), id);
        }

        return ids;
    }

    /** The page names sorted, as 3.0.8 lists them. */
    private List<String> sortedNames() throws Exception
    {
        List<String> names = new ArrayList<>(idsInTheIndex().keySet());

        Collections.sort(names);

        return names;
    }

    /** Each link's `.artikel` in the page files, by "page|x,y". */
    private Map<String, Integer> numbersInTheFiles() throws Exception
    {
        return readKeys("artikel");
    }

    /** Each link's `.zielseite` in the page files; a link with none is left out. */
    private Map<String, Integer> idsInTheFiles() throws Exception
    {
        return readKeys("zielseite");
    }

    private Map<String, Integer> readKeys(String key) throws Exception
    {
        Map<String, Integer> out = new TreeMap<>();

        for (String page : idsInTheIndex().keySet())
        {
            File file = new File(sandbox.getFolder(), "config/gleisbilder/" + page + ".cs2");

            if (!file.isFile()) continue;

            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r", "");

            Matcher m = ELEMENT.matcher(text);

            while (m.find())
            {
                String block = m.group(1);

                if (!block.contains(".typ=pfeil")) continue;

                Matcher at = Pattern.compile("\\.id=0x([0-9a-f]+)").matcher(block);

                int coordinate = at.find() ? Integer.parseInt(at.group(1), 16) : 0;

                Matcher value = Pattern.compile("\\." + key + "=(-?\\d+)").matcher(block);

                if (value.find()) out.put(page + "|" + (coordinate % 256) + "," + (coordinate >> 8), Integer.valueOf(value.group(1)));
            }
        }

        return out;
    }

    /** Where the files send each link as 3.0.8 reads them: `.artikel` is a place in the name-sorted list. */
    private Map<String, String> whereTheFilesSend() throws Exception
    {
        return readAsThe308Would();
    }

    private Map<String, String> readAsThe308Would() throws Exception
    {
        List<String> sorted = sortedNames();

        Map<String, String> out = new TreeMap<>();

        for (Map.Entry<String, Integer> link : numbersInTheFiles().entrySet())
        {
            int n = link.getValue();

            out.put(link.getKey(), n >= 0 && n < sorted.size() ? sorted.get(n) : "none");
        }

        return out;
    }

    private static String changedBetween(Map<String, String> before, Map<String, String> after)
    {
        List<String> changed = new ArrayList<>();

        for (String file : before.keySet()) if (!before.get(file).equals(after.get(file))) changed.add(file);

        return changed.toString();
    }

    /** What opening a layout must not change: the index and the pages (3.0.8 rewrites the setup and its configuration on open too). */
    private Map<String, String> layoutFiles() throws Exception
    {
        Map<String, String> files = fingerprint();

        files.keySet().removeIf(path -> !path.equals("config/gleisbild.cs2") && !path.startsWith("config/gleisbilder/"));

        return files;
    }
}
