package ui;

import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.RightClickPageMenu;
import org.traincontrol.gui.TrainControlUI;

/**
 * A ceiling of fifty locomotive mapping pages.
 *
 * Adam, 2026-08-27: "add a cap of 50 pages for now (not 99) and associated error message if exceeded."
 * There was no ceiling before this - `addLocMappingPage` incremented and nothing looked.
 *
 * Two halves, and the second is the one worth having. A guard that only the interface consults is a
 * guard the next caller does not have, and a guard nothing consults is a rule with a passing test and
 * no effect - which is the fault that has cost this project four defects, three of them last week. So
 * the predicate is tested, AND the menu that offers the action is asked whether it actually greys.
 *
 * The refusal inside `addLocMappingPage` cannot be driven here: it is a modal dialog, and a modal
 * dialog in a test is a hang rather than a failure. That one is read rather than run.
 *
 * @author Adam
 */
public class testLocMappingPages
{
    /**
     * Fifty is the last page, and a state that already holds more is not talked out of it.
     *
     * The third case is the point of the design and the reason the cap is on ADDING only. Any
     * installation set up before today can hold any number of pages, and clamping on load would
     * silently throw away the mappings on everything past the fiftieth - the same data loss
     * `canDeleteCurrentPage` refuses to allow one page at a time, done wholesale and without asking.
     *
     * MUTATION: `<=` in place of `<` fails the second case (fifty-one pages allowed); reading
     * MIN_LOC_MAPPINGS instead of MAX fails the first.
     */
    @Test
    public void testTheFiftiethPageIsTheLast() throws Exception
    {
        TrainControlUI ui = build();

        assertEquals(TrainControlUI.MAX_LOC_MAPPINGS, 50,
            "Adam asked for fifty, not ninety-nine - the badge can draw more digits than that, but "
            + "what the badge can draw was never the constraint that mattered");

        setPages(ui, TrainControlUI.MAX_LOC_MAPPINGS - 1);

        assertTrue(ui.canAddLocMappingPage(),
            "the last page a person is entitled to is refused, so the ceiling bites one page early");

        setPages(ui, TrainControlUI.MAX_LOC_MAPPINGS);

        assertFalse(ui.canAddLocMappingPage(),
            "a fifty-first page is allowed, so there is no ceiling");

        // A state from before the cap existed. Nothing capped it on the way in, and nothing should.
        setPages(ui, TrainControlUI.MAX_LOC_MAPPINGS + 12);

        assertFalse(ui.canAddLocMappingPage(),
            "an installation that already has more pages than the ceiling is offered another, which "
            + "is the one case where the number can only get worse");
    }

    /**
     * The menu that offers the page stops offering it at the ceiling.
     *
     * Built for real rather than read, because this is where a person meets the rule and because an
     * affordance that offers what the guard will refuse is its own defect - OB-057 and OB-090 were
     * both exactly that, and a third turned up in this morning's review. The button that offers an
     * action asks that action's own question.
     *
     * Greyed rather than removed, which is the shape Delete settled on for the reason written beside
     * it: an item that appears and disappears reads as an interface that cannot make up its mind.
     *
     * MUTATION: dropping the `canAddLocMappingPage` test from the menu leaves the item enabled and
     * fails the second assertion.
     */
    @Test
    public void testTheMenuWillNotOfferAFiftyFirstPage() throws Exception
    {
        TrainControlUI ui = build();

        setPages(ui, TrainControlUI.MAX_LOC_MAPPINGS - 1);

        JMenuItem below = addItemOf(ui);

        assertNotNull(below, "no Add Page item in the page menu at all");

        assertTrue(below.isEnabled(),
            "Add Page is greyed with room to spare, so the ceiling is being read as one page lower "
            + "than it is - or as no room at any count");

        setPages(ui, TrainControlUI.MAX_LOC_MAPPINGS);

        JMenuItem at = addItemOf(ui);

        assertNotNull(at, "no Add Page item in the page menu at all");

        assertFalse(at.isEnabled(),
            "Add Page is still offered at the ceiling, so a person is invited to do something that "
            + "will be refused with a dialog when they try it");

        // And the greying SAYS SO. A dead item with no explanation is the state the delete side was
        // in when Adam found it, and it reads as a bug rather than a rule.
        assertNotNull(at.getToolTipText(),
            "Add Page greys itself at the ceiling and gives no reason, which looks like a fault");

        assertTrue(at.getToolTipText().contains(String.valueOf(TrainControlUI.MAX_LOC_MAPPINGS)),
            "the tooltip on the greyed Add Page does not say what the limit is, got: "
            + at.getToolTipText());
    }

    /**
     * Adding refuses on its own, without the menu's help.
     *
     * `addLocMappingPage` is public, and the greying above is one caller's manners rather than the
     * rule. Read rather than run because the refusal is a modal dialog.
     *
     * Comments are stripped first. The guard in that method has a comment above it that names
     * `canAddLocMappingPage`, and a check that did not strip comments would pass on the strength of
     * the prose describing the code after the code had gone.
     *
     * MUTATION: deleting the guard from the method fails this while both tests above stay green.
     */
    @Test
    public void testAddingRefusesWithoutBeingAsked() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        String add = withoutComments(bodyOf(source, "public void addLocMappingPage()"));

        assertFalse(add.isEmpty(), "cannot find addLocMappingPage - has it been renamed?");

        assertTrue(add.contains("canAddLocMappingPage()"),
            "addLocMappingPage no longer asks whether it may, so anything that calls it other than "
            + "the menu - a keyboard shortcut, a script, the next feature - walks past the ceiling");

        assertTrue(add.contains("errorTooManyPages"),
            "adding is refused at the ceiling without saying why, which is a menu item that does "
            + "nothing when clicked");

        assertTrue(add.indexOf("canAddLocMappingPage()") < add.indexOf("numLocMappings++"),
            "the guard sits after the increment, so the page is added and then complained about");
    }

    /**
     * Loading a state that already has too many pages does NOT throw the extra ones away.
     *
     * The dangerous half of this cap. `setViewListener` grows the page count to fit whatever the saved
     * state holds, past the ceiling and without asking, and it has to: the only way to honour a limit
     * while loading is to drop the mappings above it. Every installation configured before 2026-08-27
     * could be over the line, and the pages above it are somebody's railway.
     *
     * It exists because the two rules look inconsistent. Somebody will notice that adding stops at
     * fifty and loading does not, and they will be right that it is odd; this is what tells them what
     * happens if they make it consistent. Two rules that must never agree need a test saying so, or
     * the next tidy-up is a data loss.
     *
     * Read rather than run: standing this up needs a control station, a saved state and a window, and
     * what can silently break is a ceiling being introduced into that loop - which is visible.
     *
     * MUTATION: adding `&& this.numLocMappings < MAX_LOC_MAPPINGS` to the growth loop fails this.
     */
    @Test
    public void testLoadingIsNotCapped() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        String load = withoutComments(bodyOf(source, "public void setViewListener("));

        assertFalse(load.isEmpty(), "cannot find setViewListener - has it been renamed?");

        assertTrue(load.contains("saveStates.size() - 1 > this.numLocMappings"),
            "the loop that grows the page count to fit a saved state is gone, so a state holding more "
            + "pages than the preference knows about loses the ones above it");

        assertFalse(load.contains("MAX_LOC_MAPPINGS"),
            "the ceiling has been applied while LOADING. Adding a page stops at fifty on purpose; "
            + "loading must not, because the only way to honour a limit here is to throw away pages "
            + "that already have locomotives on them - every installation set up before the cap "
            + "existed may be over it");
    }

    /**
     * A window, on the event thread, as everything in this package builds one.
     *
     * A Swing window put together off the event thread is a race that usually wins, which is the
     * worst kind. No layout and no model: the constructor alone gives `numLocMappings` its default,
     * and nothing here needs a railway.
     */
    private TrainControlUI build() throws Exception
    {
        // The window reads the layout preference in its constructor, so it opens Adam’s railway
        // unless something points it elsewhere first (OB-111). This was the last class building one
        // without a sandbox.
        support.LayoutSandbox sandbox = support.LayoutSandbox.open();

        final TrainControlUI[] built = new TrainControlUI[1];

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> built[0] = new TrainControlUI());
        }
        finally
        {
            sandbox.close();
        }

        return built[0];
    }

    /**
     * Puts the window on a given number of pages.
     *
     * By reflection because the count is private and has no setter - the only ways to move it are to
     * add or delete pages, and adding is the thing under test.
     */
    private void setPages(TrainControlUI ui, int pages) throws Exception
    {
        Field count = TrainControlUI.class.getDeclaredField("numLocMappings");

        count.setAccessible(true);
        count.setInt(ui, pages);
    }

    /**
     * The Add Page item out of a freshly built page menu.
     *
     * The menu is an inner class built inside `showPopup`, which needs a shown component to pop over,
     * so it is constructed directly. Found by ACTION rather than by label: the label is translated
     * and the wording is Adam's to change, while what the item does is the thing being asked about.
     */
    private JMenuItem addItemOf(TrainControlUI ui) throws Exception
    {
        RightClickPageMenu outer = new RightClickPageMenu(ui);

        Class<?> menuClass = Class.forName("org.traincontrol.gui.RightClickPageMenu$RightClickMenu");

        Constructor<?> make = menuClass.getDeclaredConstructors()[0];

        make.setAccessible(true);

        MouseEvent event = new MouseEvent(new javax.swing.JPanel(), MouseEvent.MOUSE_PRESSED,
            0L, 0, 1, 1, 1, true);

        final JPopupMenu[] menu = new JPopupMenu[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                menu[0] = (JPopupMenu) make.newInstance(outer, ui, event);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        JMenuItem found = null;

        for (java.awt.Component c : menu[0].getComponents())
        {
            if (!(c instanceof JMenuItem)) continue;

            JMenuItem item = (JMenuItem) c;

            // The label of the one that adds a page, asked of the same bundle the menu asked.
            if (item.getText().equals(org.traincontrol.util.I18n.t("page.ui.menuAddPage")))
            {
                assertNull(found, "two Add Page items in one menu");

                found = item;
            }
        }

        return found;
    }

    /**
     * Java source with its comments stripped, so a check reads code and not the prose about it.
     */
    private String withoutComments(String body)
    {
        StringBuilder out = new StringBuilder();

        boolean inLine = false, inBlock = false;

        for (int i = 0; i < body.length(); i++)
        {
            char c = body.charAt(i);
            char next = i + 1 < body.length() ? body.charAt(i + 1) : ' ';

            if (inLine)
            {
                if (c == '\n') { inLine = false; out.append(c); }
            }
            else if (inBlock)
            {
                if (c == '*' && next == '/') { inBlock = false; i++; }
            }
            else if (c == '/' && next == '/')
            {
                inLine = true;
            }
            else if (c == '/' && next == '*')
            {
                inBlock = true;
                i++;
            }
            else
            {
                out.append(c);
            }
        }

        return out.toString();
    }

    /**
     * A method, from its declaration to its closing brace.
     */
    private String bodyOf(String source, String declaration)
    {
        int at = source.indexOf(declaration);

        if (at < 0) return "";

        int open = source.indexOf('{', at + declaration.length());

        if (open < 0) return "";

        int depth = 0;

        for (int i = open; i < source.length(); i++)
        {
            char c = source.charAt(i);

            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(at, i + 1);
        }

        return "";
    }
}
