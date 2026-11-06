package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;
import support.Components;

/**
 * FR-174: the page icons down the editor's left side.  The user opens the layout editor in either mode and sees a flat
 * strip under the Page heading: add, rename, duplicate, move up, move down, a gap, and delete.  What the user sees is
 * the strip's order, its place, its width, and which icons are lit.
 */
public class testTheEditorPageStrip extends support.PageWindowTest
{
    private void open(int pages) throws Exception
    {
        if (pages == 1)
        {
            // One page, "1 - Main", and no others: the index names it alone
            Files.write(new File(sandbox.getFolder(), "config/gleisbild.cs2").toPath(),
                "[gleisbild]\nversion\n .major=1\ngroesse\nseite\n .id=5\n .name=1 - Main\n"
                    .getBytes(StandardCharsets.UTF_8));
        }

        startTheWindow(pages);
    }

    private LayoutEditor editorOn(String page, boolean autonomy) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.openLayoutEditor(page, autonomy, null));

        assertTrue(Components.waitFor(() -> editors().size() == 1 && editors().get(0).isShowing()
            && editors().get(0).getTitle().contains(page), 15000), "the editor did not open on " + page);

        Components.settle();

        return editors().get(0);
    }

    private static final String[] ORDER = {"ui.main.toolbar.addBlackPage", "ui.main.toolbar.renameCurrentPage",
        "ui.main.toolbar.duplicateCurrentPage", "layout.ui.tooltipMovePageUp", "layout.ui.tooltipMovePageDown",
        "ui.main.toolbar.deleteCurrentPage"};

    /**
     * In both modes the strip sits under the Page heading and above the list, 150 pixels wide, its flat buttons in the
     * order add, rename, duplicate, up, down, then a gap and delete, each without text, border or focus.
     *
     * MUTATION: put Delete before Down in `buildPageStrip`, or build the buttons without `toolBarButton`, and this
     * fails.
     */
    @Test
    public void testTheStripIsFlatOrderedAndUnderThePageHeadingInTheTrackEditor() throws Exception
    {
        checkTheStrip(false);
    }

    /** The same in Autonomy Setup mode of the same window. */
    @Test
    public void testTheStripIsFlatOrderedAndUnderThePageHeadingInTheSetupEditor() throws Exception
    {
        checkTheStrip(true);
    }

    private void checkTheStrip(boolean autonomy) throws Exception
    {
        open(5);

        {
            LayoutEditor editor = editorOn("3 - Top Parking", autonomy);

            List<AbstractButton> strip = strip(editor);

            assertEquals(strip.size(), 6, "the strip has the wrong number of icons (autonomy " + autonomy + "): "
                + strip.stream().map(b -> b.getToolTipText() + "@" + bounds(b, editor)).collect(
                    java.util.stream.Collectors.toList()));

            List<String> tips = new ArrayList<>();

            for (AbstractButton button : strip) tips.add(button.getToolTipText());

            assertEquals(tips, Arrays.asList(I18n.t(ORDER[0]), I18n.t(ORDER[1]), I18n.t(ORDER[2]), I18n.t(ORDER[3]),
                I18n.t(ORDER[4]), I18n.t(ORDER[5])), "the strip's icons, left to right (autonomy " + autonomy + ")");

            for (AbstractButton button : strip)
            {
                assertFalse(button.isFocusable(), button.getToolTipText() + " takes the keyboard from the window");

                assertTrue(button.getText() == null || button.getText().isEmpty(), button.getToolTipText()
                    + " has text");

                assertNotNull(button.getIcon(), button.getToolTipText() + " has no icon");
            }

            // The gap before delete is wider than the one between the others, and everything is inside the strip
            java.awt.Rectangle[] at = new java.awt.Rectangle[6];

            for (int i = 0; i < 6; i++) at[i] = bounds(strip.get(i), editor);

            int ordinary = at[1].x - (at[0].x + at[0].width);

            assertTrue(at[5].x - (at[4].x + at[4].width) > ordinary + 8, "delete is not set apart from the rest: "
                + Arrays.asList(at));

            javax.swing.JPanel sidebar = (javax.swing.JPanel) strip.get(0).getParent().getParent();

            assertEquals(Components.onTheEventThread(() -> sidebar.getWidth()).intValue(), 150, "the sidebar is not 150 wide");

            assertTrue(at[5].x + at[5].width <= bounds(sidebar, editor).x + 150, "delete is outside the sidebar");

            // Under the Page heading, above the page list
            javax.swing.JLabel heading = Components.find(sidebar, javax.swing.JLabel.class,
                l -> I18n.t("layout.ui.sidebarPages").equals(l.getText()));

            javax.swing.JList<?> list = Components.find(sidebar, javax.swing.JList.class, null);

            assertNotNull(heading, "no Page heading");

            assertTrue(bounds(heading, editor).y < at[0].y && at[0].y < bounds(list, editor).y,
                "the strip is not between the Page heading and the list (autonomy " + autonomy + ")");

            // Flat: no border drawn until pointed at
            assertEquals("toolBarButton", String.valueOf(strip.get(0).getClientProperty("JButton.buttonType")));
        }
    }

    /**
     * Up is grayed on the first page and down on the last, each with the reason as its tooltip; in the middle both are
     * lit, and delete is lit with the page count above one.
     *
     * MUTATION: ask `at < 0` instead of `at <= 0` for the first page in `buildPageStrip`, and the first page's up arrow
     * is lit.
     */
    @Test
    public void testTheUpArrowGraysOnTheFirstPage() throws Exception
    {
        open(5);

        List<AbstractButton> first = strip(editorOn("1 - Main", false));

        assertLit(first.get(3), false, "layout.ui.tooltipPageIsFirst");
        assertLit(first.get(4), true, "layout.ui.tooltipMovePageDown");
        assertLit(first.get(5), true, "ui.main.toolbar.deleteCurrentPage");
    }

    /** Both arrows are lit on a middle page. */
    @Test
    public void testBothArrowsAreLitOnAMiddlePage() throws Exception
    {
        open(5);

        List<AbstractButton> middle = strip(editorOn("3 - Top Parking", false));

        assertTrue(middle.get(3).isEnabled() && middle.get(4).isEnabled(), "an arrow is grayed on a middle page");
    }

    /** Down is grayed on the last page, with its reason. */
    @Test
    public void testTheDownArrowGraysOnTheLastPage() throws Exception
    {
        open(5);

        List<AbstractButton> last = strip(editorOn("5 - Test", false));

        assertLit(last.get(3), true, "layout.ui.tooltipMovePageUp");
        assertLit(last.get(4), false, "layout.ui.tooltipPageIsLast");
    }

    private static void assertLit(AbstractButton icon, boolean lit, String tip)
    {
        assertEquals(icon.isEnabled(), lit, I18n.t(tip) + " is " + (lit ? "grayed" : "lit"));
        assertEquals(icon.getToolTipText(), I18n.t(tip));
    }

    /**
     * A layout of one page, with or without autonomy, still shows the Page heading and the strip, 150 pixels wide;
     * delete is grayed with the handler's own reason as its tooltip, and so are both arrows.
     *
     * MUTATION: hide the strip when there is one page (the early return back in `mountSidebar`), and this fails; ask
     * `pages.size() < 1` for the last page, and delete is lit.
     */
    @Test
    public void testAOnePageLayoutStillShowsTheStripWithDeleteGrayed() throws Exception
    {
        open(1);

        LayoutEditor editor = editorOn("1 - Main", false);

        List<AbstractButton> strip = strip(editor);

        assertEquals(strip.size(), 6, "a one-page layout's editor has no strip");

        assertFalse(strip.get(5).isEnabled(), "delete is lit on the only page");
        assertEquals(strip.get(5).getToolTipText(), I18n.t("layout.ui.errorCannotDeleteOnlyRemainingPage"),
            "delete does not say why it is grayed");
        assertFalse(strip.get(3).isEnabled() || strip.get(4).isEnabled(), "an arrow is lit on the only page");
        assertTrue(strip.get(0).isEnabled() && strip.get(1).isEnabled() && strip.get(2).isEnabled(),
            "add, rename or duplicate is grayed on the only page");

        assertTrue(Components.onTheEventThread(() -> editor.getMinimumSize().width) >= 550 + 150,
            "the window is not 150 wider than its floor without the strip");
    }

    // ------------------------------------------------------------------------------------------------

    /** The strip's buttons, left to right. */
    private static List<AbstractButton> strip(LayoutEditor editor)
    {
        List<AbstractButton> found = Components.all(editor, AbstractButton.class,
            b -> "toolBarButton".equals(b.getClientProperty("JButton.buttonType")) && b.getToolTipText() != null
                && bounds(b, editor).x < 160 && bounds(b, editor).y > 60 && bounds(b, editor).y < 120);

        found.sort((a, b) -> Integer.compare(bounds(a, editor).x, bounds(b, editor).x));

        return found;
    }

    private static java.awt.Rectangle bounds(java.awt.Component part, java.awt.Component in)
    {
        return Components.onTheEventThread(() ->
            SwingUtilities.convertRectangle(part.getParent(), part.getBounds(), in));
    }

    private static List<LayoutEditor> editors()
    {
        return support.EditorsOpen.editors();
    }
}
