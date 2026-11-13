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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.util.I18n;

/**
 * FR-175: a link carries the ID of the page it goes to.  A local layout from before that is converted when it is
 * opened: every arrow is given the ID of the page its number names in the name-sorted list, where it always went, and
 * the layout is marked, so the number written beside the ID stays right for 3.0.8.
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
     * Opening an old layout converts it once: every link goes to the page its number names in the name-sorted list,
     * which is where it went before links had IDs, the files carry that page's ID and the mark, one line says so, and
     * opening it again writes nothing.  Each of the old layout's seven links would go to another page if its number
     * were read as an ID.
     *
     * MUTATION: `attachLinks` skips `convertLinksToPageIds`, and the layout is not marked.
     */
    @Test
    public void testAnOldLayoutIsConvertedOnceWithEveryLinkWhereItWent() throws Exception
    {
        convertsOnce();
    }

    /** The same for the live snapshot. */
    @Test
    public void testTheLiveSnapshotIsConvertedOnceWithEveryLinkWhereItWent() throws Exception
    {
        convertsOnce();
    }

    private void convertsOnce() throws Exception
    {
        Map<String, String> aimed = whereTheFilesSend();

        assertFalse(LayoutDiagram.linksGoByPageId(folder()), "precondition: the layout is marked already");

        List<String> logged = java.util.Collections.synchronizedList(new ArrayList<String>());

        java.util.logging.Handler tap = support.LogTap.messagesInto(logged);

        java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName()).addHandler(tap);

        try
        {
            startTheWindow(5);
        }
        finally
        {
            java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName()).removeHandler(tap);
        }

        assertEquals(targets(), aimed, "a link does not go where its number sent it before IDs");

        assertTrue(aimed.size() >= 7, "precondition: the layout has no links");

        assertTrue(LayoutDiagram.linksGoByPageId(folder()), "the layout was not marked as converted");

        assertEquals(readAsThe308Would(), aimed, "3.0.8 would send a link elsewhere after the conversion");

        for (Map.Entry<String, Integer> link : idsInTheFiles().entrySet())
        {
            assertEquals(link.getValue(), Integer.valueOf(model.getPageIdOf(aimed.get(link.getKey()))),
                "the ID written for the link at " + link.getKey() + " is not its page's");
        }

        long going = aimed.values().stream().filter(page -> !page.equals("none")).count();

        assertEquals(idsInTheFiles().size(), (int) going, "a link was not given its page's ID");

        String line = I18n.f("layout.linksConvertedToPageIds", (int) going);

        assertEquals(Collections.frequency(logged, line), 1, "the conversion was not logged once as \"" + line + "\": "
            + logged);

        stopTheWindow();

        Map<String, String> converted = layoutFiles();

        startTheWindow(5);

        assertEquals(changedBetween(converted, layoutFiles()), "[]", "a converted layout was written again on opening");

        assertEquals(targets(), aimed, "a link goes elsewhere when the converted layout is opened again");
    }

    /**
     * Every test layout, and a copy of the live layout named by `TC_LIVE_COPY`, is converted with every arrow going to
     * the page 3.0.8 sends it to, before and after a reopen, and 3.0.8's reading of the files is unchanged.
     *
     * MUTATION: `writeLinkPageIdsIntoFile` writes each arrow its own page's ID, and arrows go to the page they are on.
     */
    @Test(timeOut = 400000)
    public void testEveryLayoutIsConvertedWithEveryArrowWhereItWent() throws Exception
    {
        List<File> folders = new ArrayList<>();

        folders.add(new File("test/test_layout"));

        for (String scenario : new String[] {"live-snapshot", "unfolded-route-tiles", "one-page", "single-switch",
            "curve-into-platform", "saved-by-3-1-0"})
        {
            folders.add(support.Scenario.folderFor(scenario));
        }

        if (System.getenv("TC_LIVE_COPY") != null) folders.add(new File(System.getenv("TC_LIVE_COPY")));

        List<String> problems = new ArrayList<>();

        int arrows = 0;

        for (File folder : folders)
        {
            sandbox.close();

            sandbox = null;

            try
            {
                sandbox = support.LayoutSandbox.open(folder);

                Map<String, String> aimed = readAsThe308Would();

                arrows += aimed.size();

                startTheWindow(idsInTheIndex().size());

                if (!targets().equals(aimed)) problems.add(folder + ": " + targets() + " not " + aimed);

                if (!readAsThe308Would().equals(aimed)) problems.add(folder + ": 3.0.8 now reads " + readAsThe308Would());

                if (!LayoutDiagram.linksGoByPageId(folder())) problems.add(folder + ": not marked");

                stopTheWindow();

                startTheWindow(idsInTheIndex().size());

                if (!targets().equals(aimed)) problems.add(folder + " reopened: " + targets() + " not " + aimed);
            }
            finally
            {
                stopTheWindow();
            }
        }

        assertEquals(problems.toString(), "[]", "an arrow changed its page in the conversion");

        assertTrue(arrows > 20, "precondition: the layouts hold " + arrows + " arrows");
    }

    /**
     * On a layout not yet converted an arrow's number decides, so a `.zielseite` written before the conversion that
     * disagrees with it loses, and is rewritten.
     *
     * MUTATION: `attachLinks` reads `.zielseite` on an unmarked layout (`byId` always true), and the arrow follows it.
     */
    @Test
    public void testAnIdWrittenBeforeTheConversionThatDisagreesWithTheNumberLoses() throws Exception
    {
        Map<String, String> aimed = whereTheFilesSend();

        String link = aimed.keySet().iterator().next();

        int wrongId = idsInTheIndex().get(elsewhereThan(aimed.get(link)));

        File page = pageFileOf(link);

        String text = new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8);

        Files.write(page.toPath(), LayoutDiagram.withLinkPageIds(text, keys -> wrongId).getBytes(StandardCharsets.UTF_8));

        assertEquals(idsInTheFiles().get(link), Integer.valueOf(wrongId), "precondition: the stale ID was not written");

        startTheWindow(5);

        assertEquals(targets(), aimed, "an ID written before the conversion beat the arrow's number");

        assertEquals(idsInTheFiles().get(link), Integer.valueOf(model.getPageIdOf(aimed.get(link))),
            "the stale ID was not rewritten");
    }

    /**
     * An arrow re-aimed in 3.0.8 after the conversion, which changes only `.artikel`, goes where 3.0.8 aimed it: its
     * number no longer matches the one saved beside its ID, so the number wins, and the arrow takes its new page's ID.
     *
     * MUTATION: `setLinkPageIdFromFile` ignores `.zielartikel`, and the arrow goes where its old ID says.
     */
    @Test
    public void testAnArrowReAimedIn308AfterTheConversionGoesToItsNewPage() throws Exception
    {
        startTheWindow(5);

        Map<String, String> aimed = targets();

        stopTheWindow();

        String link = aimed.keySet().iterator().next();

        String elsewhere = elsewhereThan(aimed.get(link));

        reAimAs308Does(link, sortedNames().indexOf(elsewhere));

        startTheWindow(5);

        assertEquals(targets().get(link), elsewhere, "the arrow re-aimed in 3.0.8 did not go to its new page");

        saveEveryPage();

        assertEquals(idsInTheFiles().get(link), Integer.valueOf(model.getPageIdOf(elsewhere)),
            "the re-aimed arrow was not saved with its new page's ID");

        assertEquals(readKeys("zielartikel").get(link), numbersInTheFiles().get(link),
            "the re-aimed arrow was not saved with its number beside its ID");
    }

    /**
     * A page deleted in 3.0.8 after the conversion sets its arrows' `.artikel` to -1 and leaves their ID; a page given
     * that ID later does not collect them.
     *
     * MUTATION: `setLinkPageIdFromFile` ignores `.zielartikel`, and the arrows go to the new page.
     */
    @Test
    public void testAPageDeletedIn308DoesNotPassItsArrowsToALaterPageWithItsId() throws Exception
    {
        startTheWindow(5);

        Map<String, String> aimed = targets();

        stopTheWindow();

        int id = idsInTheIndex().get("3 - Top Parking");

        // As 3.0.8 deletes the page: its file and index entry go, and its arrows get -1; no other number moves, since
        // nothing links to the two pages after it
        assertFalse(aimed.containsValue("4 - Combined") || aimed.containsValue("5 - Test"),
            "precondition: a link goes to a page after 3 - Top Parking");

        int toIt = 0;

        for (Map.Entry<String, String> link : aimed.entrySet())
        {
            if (link.getValue().equals("3 - Top Parking") && !link.getKey().startsWith("3 - Top Parking|"))
            {
                reAimAs308Does(link.getKey(), -1);

                toIt++;
            }
        }

        assertTrue(toIt > 0, "precondition: nothing links to 3 - Top Parking");

        Files.delete(new File(sandbox.getFolder(), "config/gleisbilder/3 - Top Parking.cs2").toPath());

        String index = new String(Files.readAllBytes(indexFile().toPath()), StandardCharsets.UTF_8).replace("\r", "");

        String entry = "seite\n .id=" + id + "\n .name=3 - Top Parking\n";

        assertTrue(index.contains(entry), "precondition: the index does not list 3 - Top Parking as " + entry);

        Files.write(indexFile().toPath(), index.replace(entry, "").getBytes(StandardCharsets.UTF_8));

        // Later, a page is given the deleted page's ID
        addAPageByHand("6 - New", id);

        startTheWindow(5);

        for (Map.Entry<String, String> link : aimed.entrySet())
        {
            if (link.getValue().equals("3 - Top Parking") && !link.getKey().startsWith("3 - Top Parking|"))
            {
                assertEquals(targets().get(link.getKey()), "none", "an arrow of the page deleted in 3.0.8 went to the "
                    + "page given its ID");
            }
        }
    }

    /**
     * An arrow nobody re-aimed follows its ID after the conversion, even when a page renamed by hand reorders the
     * names, so that its number now names another page.
     *
     * MUTATION: the conversion saves the page ID as `.zielartikel` in `withLinkPageIds`, and every arrow goes by its
     * number.
     */
    @Test
    public void testAnUnchangedArrowFollowsItsIdWhenThePagesNamesReorder() throws Exception
    {
        startTheWindow(5);

        Map<String, String> aimed = targets();

        stopTheWindow();

        assertTrue(aimed.containsValue("1 - Main"), "precondition: nothing links to 1 - Main");

        renameByHand("1 - Main", "9 - Main");

        startTheWindow(5);

        assertEquals(readAsThe308Would().get(linkTo(aimed, "1 - Main")), "2 - Bottom",
            "precondition: the rename did not move the number's page");

        for (Map.Entry<String, String> link : aimed.entrySet())
        {
            String key = link.getKey().replace("1 - Main|", "9 - Main|");

            assertEquals(targets().get(key), link.getValue().replace("1 - Main", "9 - Main"),
                "an unchanged arrow did not follow its ID when the names reordered");
        }
    }

    /**
     * An arrow saved with its ID but without the number beside it, as a dev build wrote it before the number was
     * saved, still goes by its ID when 3.0.8 re-aims it.
     *
     * MUTATION: `setLinkPageIdFromFile` drops the ID when there is no `.zielartikel`, and the arrow goes where 3.0.8
     * aimed it.
     */
    @Test
    public void testAnArrowWithNoSavedNumberGoesByItsIdAsBefore() throws Exception
    {
        startTheWindow(5);

        Map<String, String> aimed = targets();

        stopTheWindow();

        for (String page : idsInTheIndex().keySet())
        {
            File file = new File(sandbox.getFolder(), "config/gleisbilder/" + page + ".cs2");

            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            Files.write(file.toPath(), text.replaceAll(" \\.zielartikel=-?\\d+\\r?\\n", "").getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(readKeys("zielartikel").toString(), "{}", "precondition: a saved number is left");

        String link = aimed.keySet().iterator().next();

        reAimAs308Does(link, sortedNames().indexOf(elsewhereThan(aimed.get(link))));

        startTheWindow(5);

        assertEquals(targets().get(link), aimed.get(link), "an arrow with no saved number did not go by its ID");
    }

    /** A link ("page|x,y") of these that goes to this page, on another page. */
    private static String linkTo(Map<String, String> links, String page)
    {
        return links.entrySet().stream().filter(link -> link.getValue().equals(page)
            && !link.getKey().startsWith(page + "|")).findFirst().get().getKey();
    }

    /** What 3.0.8 writes when it re-aims an arrow: the number alone, every other key as it was. */
    private void reAimAs308Does(String link, int number) throws Exception
    {
        File page = pageFileOf(link);

        String text = new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8).replace("\r", "");

        String[] at = link.split("\\|")[1].split(",");

        int square = Integer.parseInt(at[1]) * 256 + Integer.parseInt(at[0]);

        Matcher m = ELEMENT.matcher(text);

        while (m.find())
        {
            Matcher id = Pattern.compile("\\.id=0x([0-9a-fA-F]+)").matcher(m.group(1));

            if (!m.group(1).contains(".typ=pfeil") || (id.find() ? Integer.parseInt(id.group(1), 16) : 0) != square) continue;

            text = text.substring(0, m.start(1)) + m.group(1).replaceFirst(" \\.artikel=-?\\d+", " .artikel=" + number)
                + text.substring(m.end(1));

            break;
        }

        Files.write(page.toPath(), text.getBytes(StandardCharsets.UTF_8));

        assertEquals(numbersInTheFiles().get(link), Integer.valueOf(number), "precondition: the arrow was not re-aimed");
    }

    /** Renames a page in the index and its file, touching no arrow. */
    private void renameByHand(String from, String to) throws Exception
    {
        String index = new String(Files.readAllBytes(indexFile().toPath()), StandardCharsets.UTF_8);

        assertTrue(index.contains(" .name=" + from + "\n") || index.contains(" .name=" + from + "\r\n"),
            "precondition: the index does not name " + from);

        Files.write(indexFile().toPath(), index.replace(" .name=" + from + "\n", " .name=" + to + "\n")
            .replace(" .name=" + from + "\r\n", " .name=" + to + "\r\n").getBytes(StandardCharsets.UTF_8));

        Files.move(new File(sandbox.getFolder(), "config/gleisbilder/" + from + ".cs2").toPath(),
            new File(sandbox.getFolder(), "config/gleisbilder/" + to + ".cs2").toPath());
    }

    private String folder()
    {
        return sandbox.getFolder().getAbsolutePath();
    }

    /** Deletes a page the way the window does: its file and its index entry go, and links are re-aimed. */
    private void deletePage(String name) throws Exception
    {
        List<String> remaining = new ArrayList<>(model.getLayoutList());

        remaining.remove(name);

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String each : model.getLayoutList()) pages.add(model.getLayout(each));

        model.getLayout(name).deleteLayoutFile();

        for (LayoutDiagram changed : LayoutDiagram.writeIndexAndKeepLinksAimed(folder(), remaining, null, 0, null, pages))
        {
            changed.saveChanges(null, false);
        }

        model.refreshLayouts();
    }

    /** A page of the test layout other than this one. */
    private static String elsewhereThan(String page)
    {
        return page.equals("5 - Test") ? "4 - Combined" : "5 - Test";
    }

    /** The page file a link ("page|x,y") is in. */
    private File pageFileOf(String link)
    {
        return new File(sandbox.getFolder(), "config/gleisbilder/" + link.split("\\|")[0] + ".cs2");
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
     * a page leaves the links to it going to no page, as in 3.0.8.
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

        deletePage("3 - Top Parking");

        for (Map.Entry<String, String> link : targets().entrySet())
        {
            String before = renamed.get(link.getKey());

            if (before == null) continue;

            assertEquals(link.getValue(), before.equals("3 - Top Parking") ? "none" : before,
                "after the delete the link at " + link.getKey() + " goes to " + link.getValue());
        }
    }

    /**
     * With no setup to hold it, a deleted page's ID can be issued to the next new page, and the deleted page's arrows
     * must not go there: they go to no page, as in 3.0.8.
     *
     * MUTATION: `repointPageLinks` aims a deleted page's arrows at -1 with `setLinkedPageIndex`, keeping their ID, and
     * they go to the page added next.
     */
    @Test
    public void testADeletedPagesLinksDoNotGoToTheNextNewPage() throws Exception
    {
        Files.delete(new File(sandbox.getFolder(), "config/autonomy/setup.json").toPath());

        startTheWindow(5);

        Map<String, String> sent = targets();

        // 1 - Main has the highest ID, 5, and links go to it
        assertTrue(sent.containsValue("1 - Main"), "precondition: nothing links to 1 - Main");

        deletePage("1 - Main");

        org.traincontrol.automationui.LayoutPageEdit.renameOrDuplicate(model.getLayoutList(), model.getLayout("5 - Test"),
            folder(), "5 - Test", "6 - New", false, true, true, null, model);

        model.refreshLayouts();

        assertEquals(model.getPageIdOf("6 - New"), 5, "precondition: the new page did not take the deleted page's ID");

        int checked = 0;

        for (Map.Entry<String, String> link : sent.entrySet())
        {
            if (link.getValue().equals("1 - Main") && !link.getKey().startsWith("1 - Main|"))
            {
                checked++;

                assertEquals(targets().get(link.getKey()), "none", "a link to the deleted page goes to the new page");
            }
        }

        assertTrue(checked > 0, "precondition: no other page links to 1 - Main");
    }

    /**
     * Two local pages that share an ID: the editor's picker aims an arrow at the page that does not keep the ID, and OK
     * on an arrow that goes there leaves it going there.
     *
     * MUTATION: `LayoutEditor.editAddress` aims by the picker's ID (`getPageNameForId(getPageIdOf(page))`), and the
     * arrow goes to the page that keeps the ID.
     */
    @Test
    public void testTheEditorAimsAtAPageThatSharesItsId() throws Exception
    {
        Files.delete(new File(sandbox.getFolder(), "config/autonomy/setup.json").toPath());

        String index = new String(Files.readAllBytes(indexFile().toPath()), StandardCharsets.UTF_8).replace("\r", "");

        // 2 - Bottom and 3 - Top Parking share ID 2; 2 - Bottom, first by name, keeps it
        Files.write(indexFile().toPath(), index.replace(" .id=1\n .name=2 - Bottom", " .id=2\n .name=2 - Bottom")
            .getBytes(StandardCharsets.UTF_8));

        startTheWindow(5);

        assertEquals(model.getPageNameForId(2), "2 - Bottom", "precondition: 2 - Bottom does not keep the shared ID");

        Map<String, String> sent = targets();

        String toTop = null;
        String toOther = null;

        for (Map.Entry<String, String> link : sent.entrySet())
        {
            if (link.getKey().startsWith("1 - Main|") && link.getValue().equals("3 - Top Parking")) toTop = link.getKey();
            else if (link.getKey().startsWith("1 - Main|") && !link.getValue().equals("3 - Top Parking")) toOther = link.getKey();
        }

        assertTrue(toTop != null && toOther != null, "precondition: 1 - Main's links " + sent);

        assertEquals(aimInTheEditor(toTop, null), "3 - Top Parking", "OK on an arrow to the page sharing an ID moved it");

        assertEquals(aimInTheEditor(toOther, "3 - Top Parking"), "3 - Top Parking",
            "the picker aimed the arrow at another page than the one picked");
    }

    /**
     * Opens the editor on the link's page, opens the link's address dialog, picks `page` (null leaves the choice as
     * it opens) and presses OK; returns where the edited arrow goes.
     */
    private String aimInTheEditor(String link, final String page) throws Exception
    {
        final String on = link.split("\\|")[0];
        final int x = Integer.parseInt(link.split("\\|")[1].split(",")[0]);
        final int y = Integer.parseInt(link.split("\\|")[1].split(",")[1]);

        final org.traincontrol.gui.layouteditor.LayoutEditor editor = support.Components.onTheEventThread(() ->
        {
            org.traincontrol.gui.layouteditor.LayoutEditor built =
                new org.traincontrol.gui.layouteditor.LayoutEditor(model.getLayout(on), 30, ui, 0);

            built.render();

            return built;
        });

        try
        {
            final java.util.function.Supplier<org.traincontrol.gui.LayoutLabel> find = () -> support.Components.find(editor,
                org.traincontrol.gui.LayoutLabel.class, label ->
                {
                    try
                    {
                        return editor.getGridX(label) == x && editor.getGridY(label) == y;
                    }
                    catch (NullPointerException notOnTheGrid)
                    {
                        // The palette's squares
                        return false;
                    }
                });

            // The grid's squares are built after the window opens
            assertTrue(support.Components.waitFor(() -> find.get() != null, 30000), "precondition: the editor has no"
                + " square at " + link);

            final org.traincontrol.gui.LayoutLabel square = find.get();

            support.Components.settle();

            try (support.Answering answering = support.Answering.start(message ->
            {
                if (page != null) pickInTheOpenDialog(page);

                return support.Answering.pressAt(0);
            }))
            {
                javax.swing.SwingUtilities.invokeLater(() -> editor.editAddress(square));

                assertTrue(answering.awaitAsked(1, 10000), "the address dialog did not open");
            }

            support.Components.settle();

            String goes = support.Components.onTheEventThread(() -> editor.getMarklinLayout().getComponent(x, y).getLinkPageName());

            return goes;
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(editor::dispose);
        }
    }

    /** Selects the page in the combo box of the dialog on screen. */
    private static void pickInTheOpenDialog(final String page)
    {
        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                    javax.swing.JComboBox<?> box = support.Components.find(window, javax.swing.JComboBox.class, null);

                    if (box != null) box.setSelectedItem(page);
                }
            });
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
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

        Map<String, Integer> before = idsInTheIndex();

        startTheWindow(5);

        assertEquals(idsInTheIndex(), before, "reading a layout with a shared ID changed the IDs in the index");

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

    /**
     * An arrow whose stored number is past the page list, with no page ID, logs that the page does not exist when
     * clicked, and shows no dialog; an arrow that links nowhere stays silent.
     *
     * MUTATION: `followLink` returns without logging when `linksToAPageThatIsGone()` is false.
     */
    @Test
    public void testAnArrowPastThePageListLogsThatThePageDoesNotExist() throws Exception
    {
        startTheWindow(5);

        LayoutDiagramComponent link = null;

        for (String page : model.getLayoutList())
        {
            for (LayoutDiagramComponent tile : model.getLayout(page).getAll())
            {
                if (link == null && tile != null && tile.isLink()) link = tile;
            }
        }

        assertNotNull(link, "precondition: the layout has no link");

        List<String> logged = java.util.Collections.synchronizedList(new ArrayList<String>());

        java.util.logging.Handler tap = support.LogTap.messagesInto(logged);

        java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName()).addHandler(tap);

        try
        {
            link.setLinkTarget(null, null, -1);

            ui.followLink(link);

            assertTrue(logged.isEmpty(), "an arrow that links nowhere logged: " + logged);

            link.setLinkTarget(null, null, 99);

            ui.followLink(link);
        }
        finally
        {
            java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName()).removeHandler(tap);
        }

        assertEquals(logged, Collections.singletonList(I18n.f("layout.ui.errorLayoutPageDoesNotExist", 100)),
            "clicking an arrow past the page list did not log that the page does not exist");
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

                Matcher at = Pattern.compile("\\.id=0x([0-9a-fA-F]+)").matcher(block);

                int coordinate = at.find() ? Integer.parseInt(at.group(1), 16) : 0;

                Matcher value = Pattern.compile("\\." + key + "=(-?\\d+)").matcher(block);

                // An arrow with no number is read as 0, as the parser reads it
                Integer found = value.find() ? Integer.valueOf(value.group(1)) : key.equals("artikel") ? Integer.valueOf(0) : null;

                if (found != null) out.put(page + "|" + (coordinate % 256) + "," + ((coordinate >> 8) % 256), found);
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
