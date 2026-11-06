package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.LayoutPopupUI;
import org.traincontrol.gui.layouteditor.LayoutEditorAddressPopup;
import org.traincontrol.util.I18n;
import support.Components;

/**
 * FR-175: pages are SHOWN in the order of the layout file, but an arrow's number stays a place in the NAME-SORTED list,
 * in the file, in memory and in the autonomy graph, as it was and as 3.0.8 reads it.  The user clicks an arrow, picks
 * a page for one, and moves pages with the editor's arrows; what comes of it is the page shown, the number stored, the
 * files, and the graph.
 */
public class testArrowsKeepTheirMeaningInAnyDisplayOrder extends support.PageWindowTest
{
    private static final List<String> NOT_BY_NAME = Arrays.asList("3 - Top Parking", "1 - Main", "5 - Test",
        "2 - Bottom", "4 - Combined");

    @Override
    protected File fixtureFor(java.lang.reflect.Method test)
    {
        // A Central Station layout's index, which is not in name order: Startpackung, Main, Something, Main Layout
        return new File(test.getName().contains("Station") ? "test/cs3_v261" : "test/test_layout");
    }

    /**
     * On a local layout shown in file order, clicking an arrow shows the page the name-sorted list says, in the main
     * window and in a page popup, and the autonomy session is built over the name-sorted pages.
     *
     * MUTATION: `goToLayoutPage` in `TrainControlUI` or `LayoutPopupUI` indexes the shown list instead of the
     * name-sorted one, or `AutonomyLifecycle` feeds the session the shown list, and this fails.
     */
    @Test
    public void testClickingAnArrowOnALocalLayoutShownInFileOrder() throws Exception
    {
        writeIndex(NOT_BY_NAME);

        startTheWindow(5);

        checkTheDoors();
    }

    /**
     * The same on a copy of the Central Station layout in test/cs3_v261, whose index is not in name order, with an
     * arrow added to the first page that leads to the page the name-sorted list puts second.
     *
     * MUTATION: as above.
     */
    @Test
    public void testClickingAnArrowOnAStationLayoutShownInFileOrder() throws Exception
    {
        File page = new File(sandbox.getFolder(), "config/gleisbilder/Startpackung.cs2");

        Files.write(page.toPath(), (new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8)
            + "\nelement\n .id=0x101\n .typ=pfeil\n .drehung=1\n .artikel=1\n").getBytes(StandardCharsets.UTF_8));

        startTheWindow(4);

        checkTheDoors();
    }

    private void checkTheDoors() throws Exception
    {
        final List<String> byName = sortedByName();

        assertNotEquals(model.getLayoutList(), byName, "precondition: the layout is shown in name order");

        assertEquals(model.getLayoutList().size(), byName.size());

        AutonomySession session = ui.getAutonomySession();

        if (session != null)
        {
            List<String> sessionPages = new ArrayList<>();

            for (org.traincontrol.base.LayoutDiagram each : session.getPages()) sessionPages.add(each.getName());

            assertEquals(sessionPages, byName, "the autonomy session is not built over the name-sorted pages");
        }

        final javax.swing.JComboBox<?> shown = combo();

        final LayoutPopupUI popup = Components.onTheEventThread(() ->
        {
            LayoutPopupUI made = new LayoutPopupUI(model.getLayout(byName.get(0)), 30, ui, 0);

            made.render();

            return made;
        });

        try
        {
            for (int n = 0; n < byName.size(); n++)
            {
                final int number = n;

                SwingUtilities.invokeAndWait(() -> ui.goToLayoutPage(number));

                assertEquals(String.valueOf(Components.onTheEventThread(shown::getSelectedItem)), byName.get(n),
                    "arrow number " + n + " does not show the page at that place in the name-sorted list");

                SwingUtilities.invokeAndWait(() -> popup.goToLayoutPage(number));

                assertTrue(Components.waitFor(() -> byName.get(number).equals(popupPage(popup)), 5000), "arrow number "
                    + n + " shows " + popupPage(popup) + " in the page popup, not " + byName.get(n));
            }
        }
        finally
        {
            SwingUtilities.invokeAndWait(popup::dispose);
        }
    }

    /**
     * The autonomy graph is the same whichever order the pages are shown in: the same links, the same reduction.
     *
     * MUTATION: `AutonomyLifecycle` feeds the session the shown list, and the links join other pages' squares.
     */
    @Test
    public void testTheGraphDoesNotDependOnTheDisplayOrder() throws Exception
    {
        startTheWindow(5);

        String inNameOrder = graphFingerprint();

        stopTheWindow();

        writeIndex(NOT_BY_NAME);

        startTheWindow(5);

        assertEquals(graphFingerprint(), inNameOrder, "the graph changed with the order the pages are shown in");
    }

    /**
     * Moving a page changes the index and nothing else in the folder, and the graph built before and after is the
     * same.
     *
     * MUTATION: `reorder` re-aims the arrows (`writeIndexAndKeepLinksAimed` in place of `writeLayoutIndex`) after
     * sorting its lists, or rewrites a page, and a page file differs.
     */
    @Test
    public void testMovingAPageChangesOnlyTheIndexAndNotTheGraph() throws Exception
    {
        startTheWindow(5);

        String graph = graphFingerprint();

        List<String> was = namesInTheIndex();

        SwingUtilities.invokeAndWait(() -> ui.openLayoutEditor("3 - Top Parking", false, null));

        assertTrue(Components.waitFor(() -> support.EditorsOpen.editorOn("3 - Top Parking") != null, 15000), "no editor");

        Components.settle();

        // Taken with the editor open: opening it writes its own undo copy and may refresh the configuration
        Map<String, String> before = fingerprint();

        Components.press(Components.all(support.EditorsOpen.editorOn("3 - Top Parking"), javax.swing.AbstractButton.class,
            b -> I18n.t("layout.ui.tooltipMovePageUp").equals(b.getToolTipText())).get(0));

        assertTrue(Components.waitFor(() -> !namesInTheIndex().equals(was), 15000), "the page did not move");

        Components.settle();

        Map<String, String> after = fingerprint();

        assertNotEquals(after.get("config/gleisbild.cs2"), before.get("config/gleisbild.cs2"), "the index did not change");

        after.remove("config/gleisbild.cs2");
        before.remove("config/gleisbild.cs2");

        assertEquals(after.keySet(), before.keySet(), "moving a page added or removed a file");

        List<String> changed = new ArrayList<>();

        for (String file : before.keySet()) if (!before.get(file).equals(after.get(file))) changed.add(file);

        assertEquals(changed.toString(), "[]", "moving a page changed a file other than the index");

        assertEquals(graphFingerprint(), graph, "moving a page changed the autonomy graph");
    }

    /**
     * A reader that lists pages by name (3.0.8) resolves every arrow to the same page after a move as before it: the
     * page files are the ones it had, and its name-sorted list is the same.
     *
     * MUTATION: as for the test above.
     */
    @Test
    public void testAReaderListingByNameResolvesEveryArrowAfterAMove() throws Exception
    {
        startTheWindow(5);

        Map<String, String> resolvedBefore = resolvedArrows();

        SwingUtilities.invokeAndWait(() -> ui.openLayoutEditor("1 - Main", false, null));

        assertTrue(Components.waitFor(() -> support.EditorsOpen.editorOn("1 - Main") != null, 15000), "no editor");

        Components.settle();

        Components.press(Components.all(support.EditorsOpen.editorOn("1 - Main"), javax.swing.AbstractButton.class,
            b -> I18n.t("layout.ui.tooltipMovePageDown").equals(b.getToolTipText())).get(0));

        assertTrue(Components.waitFor(() -> namesInTheIndex().get(0).equals("2 - Bottom"), 15000), "the page did not move");

        Components.settle();

        assertEquals(resolvedArrows(), resolvedBefore, "an arrow leads to another page for a reader listing by name");
    }

    /**
     * The arrow picker lists the pages in the order they are shown and stores the name-sorted position of the page
     * picked, and shows the page at the stored position.
     *
     * MUTATION: `LayoutEditorAddressPopup` indexes `getLayoutList` instead of `getLayoutListByName`, and this fails.
     */
    @Test
    public void testThePickerShowsPagesInFileOrderAndStoresTheNameSortedPlace() throws Exception
    {
        writeIndex(NOT_BY_NAME);

        startTheWindow(5);

        final List<String> byName = sortedByName();

        LayoutDiagramComponent arrow = null;
        String onPage = null;

        for (String page : model.getLayoutList())
        {
            for (LayoutDiagramComponent tile : model.getLayout(page).getAll())
            {
                if (arrow == null && tile.isLink() && !tile.linksNowhere()) { arrow = tile; onPage = page; }
            }
        }

        assertNotNull(arrow, "precondition: the layout has no arrow");

        final LayoutDiagramComponent tile = arrow;
        final String here = onPage;

        final LayoutEditorAddressPopup picker = Components.onTheEventThread(
            () -> new LayoutEditorAddressPopup(tile, ui, here));

        final javax.swing.JComboBox<?> choices = Components.find(picker, javax.swing.JComboBox.class, null);

        List<String> listed = Components.onTheEventThread(() ->
        {
            List<String> out = new ArrayList<>();

            for (int i = 1; i < choices.getItemCount(); i++) out.add(String.valueOf(choices.getItemAt(i)));

            return out;
        });

        List<String> shownOrder = new ArrayList<>(model.getLayoutList());

        shownOrder.remove(here);

        assertEquals(listed, shownOrder, "the picker does not list the pages in the order they are shown");

        final int stored = tile.getRawAddress();

        SwingUtilities.invokeAndWait(() -> picker.setAddress(Integer.toString(stored + 1)));

        assertEquals(String.valueOf(Components.onTheEventThread(choices::getSelectedItem)), byName.get(stored),
            "the picker shows another page than the name-sorted list has at the arrow's number");

        final String other = shownOrder.get(0);

        SwingUtilities.invokeAndWait(() -> choices.setSelectedItem(other));

        assertEquals(Components.onTheEventThread(picker::getAddress), Integer.toString(byName.indexOf(other) + 1),
            "the picker does not store the name-sorted place of the page picked");
    }

    // ------------------------------------------------------------------------------------------------

    /** The shown pages sorted here, not by the model, so a model that sorts wrongly cannot agree with itself. */
    private List<String> sortedByName()
    {
        List<String> sorted = new ArrayList<>(model.getLayoutList());

        Collections.sort(sorted);

        return sorted;
    }

    private static String popupPage(LayoutPopupUI popup)
    {
        try
        {
            java.lang.reflect.Field field = LayoutPopupUI.class.getDeclaredField("layout");

            field.setAccessible(true);

            return ((org.traincontrol.base.LayoutDiagram) field.get(popup)).getName();
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private javax.swing.JComboBox<?> combo() throws Exception
    {
        java.lang.reflect.Field field = org.traincontrol.gui.TrainControlUI.class.getDeclaredField("LayoutList");

        field.setAccessible(true);

        return (javax.swing.JComboBox<?>) field.get(ui);
    }

    /** The links and the reduction of the autonomy graph, as sorted lines. */
    private String graphFingerprint() throws Exception
    {
        final AutonomySession session = ui.getAutonomySession();

        java.lang.reflect.Field portals = TileGraph.class.getDeclaredField("portals");

        portals.setAccessible(true);

        List<String> lines = new ArrayList<>();

        for (Map.Entry<?, ?> link : ((Map<?, ?>) portals.get(session.getGraph())).entrySet())
        {
            lines.add("link " + link.getKey() + " > " + link.getValue());
        }

        for (Map.Entry<TileGraph.TileKey, GraphReducer.ReducedPoint> point : session.getReducer().getPoints().entrySet())
        {
            lines.add("point " + point.getKey() + " " + point.getValue().getName() + " " + point.getValue().getS88());
        }

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges()) lines.add("edge " + edge);

        Collections.sort(lines);

        assertTrue(lines.stream().anyMatch(l -> l.startsWith("link ")), "precondition: the graph has no link");

        return String.join("\n", lines);
    }

    /** Each page's arrows as the page a reader listing by name takes them to. */
    private Map<String, String> resolvedArrows() throws Exception
    {
        List<String> byName = new ArrayList<>(namesInTheIndex());

        Collections.sort(byName);

        Map<String, String> out = new LinkedHashMap<>();

        for (String page : byName)
        {
            List<String> leadsTo = new ArrayList<>();

            boolean arrow = false;

            for (String line : Files.readAllLines(new File(sandbox.getFolder(), "config/gleisbilder/" + page + ".cs2")
                .toPath(), StandardCharsets.UTF_8))
            {
                if (line.trim().equals("element")) arrow = false;
                else if (line.trim().equals(".typ=pfeil")) arrow = true;
                else if (arrow && line.trim().startsWith(".artikel="))
                {
                    int n = Integer.parseInt(line.trim().substring(9));

                    leadsTo.add(n >= 0 && n < byName.size() ? byName.get(n) : "nothing");
                }
            }

            out.put(page, leadsTo.toString());
        }

        return out;
    }

    private List<String> namesInTheIndex()
    {
        List<String> names = new ArrayList<>();

        try
        {
            for (String line : Files.readAllLines(new File(sandbox.getFolder(), "config/gleisbild.cs2").toPath(),
                StandardCharsets.UTF_8))
            {
                if (line.startsWith(" .name=")) names.add(line.substring(7).trim());
            }
        }
        catch (java.io.IOException e)
        {
            throw new IllegalStateException(e);
        }

        return names;
    }

    /** Rewrites the sandbox's index with its pages in this order and the ids they had. */
    private void writeIndex(List<String> order) throws Exception
    {
        File index = new File(sandbox.getFolder(), "config/gleisbild.cs2");

        Map<String, String> ids = new LinkedHashMap<>();

        String id = null;

        for (String line : Files.readAllLines(index.toPath(), StandardCharsets.UTF_8))
        {
            if (line.startsWith(" .id=")) id = line.substring(5).trim();
            else if (line.startsWith(" .name=")) ids.put(line.substring(7).trim(), id);
        }

        StringBuilder out = new StringBuilder("[gleisbild]\nversion\n .major=1\ngroesse\n");

        for (String name : order) out.append("seite\n .id=").append(ids.get(name)).append("\n .name=").append(name).append("\n");

        Files.write(index.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Every file in the layout's folder, by its path there, with a digest of its bytes. */
    private Map<String, String> fingerprint() throws Exception
    {
        Map<String, String> files = new TreeMap<>();

        java.nio.file.Path root = sandbox.getFolder().toPath();

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");

        List<java.nio.file.Path> found = new ArrayList<>();

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(root))
        {
            walk.filter(Files::isRegularFile).forEach(found::add);
        }

        for (java.nio.file.Path file : found)
        {
            files.put(root.relativize(file).toString().replace('\\', '/'),
                java.util.Base64.getEncoder().encodeToString(digest.digest(Files.readAllBytes(file))));
        }

        return files;
    }
}
