package ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * Every language fits in the window it was translated for.
 *
 * `testMessageBundles` checks that strings are CORRECT; nothing else checks their LENGTH, and German and Polish
 * routinely run half again as long as the English. A Swing component narrower than the text it holds draws it clipped
 * with an ellipsis, so this makes one comparison per component, on a window laid out at the size the form declares.
 *
 * ONE sandbox around the whole class, and this test fingerprints `cs2_sample_layout` itself and fails if a single byte
 * moved: a sandbox opened per window let work scheduled by a disposed window write to Adam's real railway after the
 * preference was put back.
 *
 * Cannot see: panels that fill from a railway (the window has no model), and a container that overflows (a panel wider
 * than the window, or a component past its parent's edge; scroll panes legitimately hold wider content). The tab titles
 * are IMAGES, so they read the same in every language and nothing here measures them. Of the text-bearing components
 * inspected, only the ones the locale changes say anything about translation length; `LOCALISED_FLOOR` keeps that count
 * from quietly becoming none.
 *
 * @author Adam
 */
public class testEveryLanguageFits
{
    /** The eight the bundles carry. */
    private static final String[] LANGUAGES = {"en", "de", "fr", "es", "it", "nl", "da", "pl"};

    private static final File OUT = new File(System.getProperty("java.io.tmpdir"), "tc-language-shots");

    /** Adam's real railway, which nothing here may touch. */
    private static final File LIVE = new File("cs2_sample_layout");

    /**
     * How much wider than its space a component may ask to be before it counts as clipped: a border rounding or font
     * hint costs a pixel or two without losing a character.
     */
    private static final int SLACK = 3;

    /**
     * How many text-bearing components a non-English run is expected to change: a FLOOR, to catch the locale failing to
     * reach the window, which would make every clean run a statement about English.
     */
    private static final int LOCALISED_FLOOR = 30;

    /**
     * How wide a menu may be before it is a problem: half the window's width, where a menu stops being a menu and
     * starts being a page. The window is its minimum size, the size an operator gets on first run.
     */
    private static int menuCeiling(javax.swing.JMenuBar bar)
    {
        java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(bar);

        assertNotNull(window, "the menu bar is not in a window, so there is no width to hold its menus to");

        return window.getMinimumSize().width / 2;
    }

    /**
     * The rule can fail: a test whose check cannot be seen to fail tests nothing, and the eight-language walk cannot
     * carry its own control (a real string could only be made too long by editing a bundle). So the RULE is controlled
     * here, on a component this method owns: a short string in 80 pixels fits, the same label with a long one does not.
     *
     * MUTATION: comparing `getPreferredSize().width` against `getWidth()` again fails the second assertion, because
     * `setPreferredSize` is how the fixture fixes the width and that is the case the old rule could not see.
     */
    @Test
    public void testTheClippingRuleCanActuallyFail()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("a font has to be measured");
        }

        JLabel label = new JLabel("OK");

        // The width fixed the way the form fixes it, which is the case that defeated the old rule.
        label.setPreferredSize(new Dimension(80, 20));
        label.setSize(80, 20);

        assertFalse(clips(label),
            "\"OK\" was called too wide for 80 pixels, so the rule refuses text that fits and every "
            + "finding below is suspect");

        label.setText("Eine Konfigurationseinstellungsmoeglichkeit die viel zu lang ist");

        assertTrue(clips(label),
            "a 64-character string was called a fit in 80 pixels, so this test cannot see a clipped "
            + "label at all - which is what it was doing until 2026-09-11. The rule has to measure the "
            + "TEXT, because `setPreferredSize` has already decided what the component will ask for");
    }

    @Test
    public void testNoLabelIsClippedInAnyLanguage() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("a window has to be laid out to be measured");
        }

        OUT.mkdirs();

        String railwayBefore = fingerprint(LIVE);

        Locale was = Locale.getDefault();

        Map<String, List<String>> clipped = new LinkedHashMap<>();
        Map<String, Integer> inspected = new LinkedHashMap<>();

        // What each language puts on the window, so the reach of this test is a number rather than a hope.
        Map<String, Map<String, String>> localised = new LinkedHashMap<>();

        // ONE sandbox for the whole class, opened before any window and closed after the last: a window schedules work
        // that outlives dispose().
        support.LayoutSandbox sandbox = null;

        try
        {
            // Inside the try, so nothing can leave the preference behind.
            sandbox = support.LayoutSandbox.open();

            for (String language : LANGUAGES)
            {
                int[] seen = {0};

                clipped.put(language, measure(language, seen, localised));

                inspected.put(language, seen[0]);
            }
        }
        finally
        {
            Locale.setDefault(was);
            I18n.setLocale(was);

            // Let what the windows posted run first: work scheduled by a disposed window lands some milliseconds after
            // the last dispose, and fingerprinting before draining the queue samples the folder at the one moment that
            // cannot have seen it.
            try
            {
                for (int pass = 0; pass < 5; pass++)
                {
                    javax.swing.SwingUtilities.invokeAndWait(() -> { });
                }
            }
            catch (Exception draining)
            {
                // A queue that will not drain is not a reason to skip the check below.
            }

            if (sandbox != null) sandbox.close();

            // And in the finally, because the run most likely to have written is the one that threw.
            assertEquals(fingerprint(LIVE), railwayBefore,
                "this test wrote to " + LIVE + ", which is the operator's real railway and is not "
                + "recoverable.  Whatever it found is beside the point until that is understood");
        }

        StringBuilder report = new StringBuilder();

        int total = 0;

        for (Map.Entry<String, List<String>> e : clipped.entrySet())
        {
            report.append("== ").append(e.getKey()).append("  (")
                .append(e.getValue().size()).append(")").append(System.lineSeparator());

            for (String line : e.getValue())
            {
                report.append("   ").append(line).append(System.lineSeparator());
            }

            total += e.getValue().size();
        }

        report.append(System.lineSeparator()).append("components with text inspected:")
            .append(System.lineSeparator());

        for (Map.Entry<String, Integer> e : inspected.entrySet())
        {
            report.append("   ").append(e.getKey()).append("  ").append(e.getValue())
                .append(System.lineSeparator());
        }

        // How many components the locale actually moves, the reach of this test: most of this form's text is baked into
        // the generated layout rather than passed through `I18n`, so a German run leaves it in English and it says
        // nothing about translation length.
        Map<String, String> english = localised.get("en");

        Map<String, Integer> moved = new LinkedHashMap<>();

        if (english != null)
        {
            for (Map.Entry<String, Map<String, String>> e : localised.entrySet())
            {
                if ("en".equals(e.getKey())) continue;

                int differs = 0;

                for (Map.Entry<String, String> one : e.getValue().entrySet())
                {
                    String inEnglish = english.get(one.getKey());

                    if (inEnglish != null && !inEnglish.equals(one.getValue())) differs++;
                }

                moved.put(e.getKey(), differs);
            }

            report.append(System.lineSeparator()).append("of those, how many the locale changes:")
                .append(System.lineSeparator());

            for (Map.Entry<String, Integer> e : moved.entrySet())
            {
                report.append("   ").append(e.getKey()).append("  ").append(e.getValue())
                    .append(System.lineSeparator());
            }
        }

        java.nio.file.Files.write(new File(OUT, "clipped.txt").toPath(),
            report.toString().getBytes("UTF-8"));

        // What was looked at, before what was found: a window that is not displayable lays nothing out, so every
        // component has a width of zero and every one is skipped. Both guards below fail on that.
        for (Map.Entry<String, Integer> e : inspected.entrySet())
        {
            assertTrue(e.getValue() >= 40,
                "only " + e.getValue() + " components with text were measured in " + e.getKey()
                + ", which is not a window - the layout did not happen and nothing was asked");
        }

        // The two pictures have to exist first: `sameBytes` answers false when either file is missing and `shoot`
        // swallows a write failure by design, so an unwritable OUT would satisfy the control below by there being
        // nothing to compare.
        assertTrue(new File(OUT, "window-en.png").isFile() && new File(OUT, "window-de.png").isFile(),
            "the screenshots this control compares were never written, so it would pass having "
            + "compared nothing.  Looked in " + OUT);

        assertFalse(sameBytes(new File(OUT, "window-en.png"), new File(OUT, "window-de.png")),
            "the English and German windows are byte-identical, so the locale is not reaching the "
            + "text and all eight measurements are of the same language");

        // And a floor under the reach, which the screenshot control cannot give: two differing pictures prove the
        // locale reached SOMETHING, this says how many of the measured components are in the language being measured. A
        // floor, because translating one more label should not fail a test.
        for (Map.Entry<String, Integer> e : moved.entrySet())
        {
            assertTrue(e.getValue() >= LOCALISED_FLOOR,
                "only " + e.getValue() + " of the components this measures change when the language is "
                + e.getKey() + ", against " + LOCALISED_FLOOR + " when this was measured. Either the "
                + "locale has stopped reaching the window - in which case every clean run below is "
                + "clean about English - or text has moved out of the bundles and into the form");
        }

        assertEquals(total, 0,
            "text is clipped - the full list is in " + new File(OUT, "clipped.txt")
            + System.lineSeparator() + report);
    }

    /**
     * Builds the window in one language, writes its picture, and returns everything that does not fit; `inspected`
     * counts the components looked at so the caller can insist there were some.
     */
    private List<String> measure(String language, int[] inspected,
        Map<String, Map<String, String>> localised) throws Exception
    {
        Locale locale = new Locale(language);

        Locale.setDefault(locale);
        I18n.setLocale(locale);

        final TrainControlUI[] ui = new TrainControlUI[1];

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                ui[0] = new TrainControlUI();

                // PACKED, which makes the window displayable; without it nothing is laid out and eight identical blank
                // pictures come out.
                ui[0].pack();

                // Then the size the form declares, the window an operator gets on first run (its minimum size); pack()
                // would give whatever the longest string in THIS language needs, which is the question rather than the
                // answer.
                ui[0].setSize(ui[0].getMinimumSize());
                ui[0].validate();
            });

            final List<String> found = new ArrayList<>();

            // EVERY COMPONENT ONCE, however many tabs it is walked under.
            final java.util.Set<Component> already =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Component, Boolean>());

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                shoot((JComponent) ui[0].getContentPane(),
                    new File(OUT, "window-" + language + ".png"));

                // EVERY TAB, not just the one showing: only the selected one has been through a layout with real text.
                for (JTabbedPane tabs : tabbedPanes(ui[0].getContentPane()))
                {
                    int wasAt = tabs.getSelectedIndex();

                    for (int at = 0; at < tabs.getTabCount(); at++)
                    {
                        tabs.setSelectedIndex(at);
                        ui[0].validate();

                        inspected[0] += walk(tabs, found, already);
                    }

                    if (wasAt >= 0 && wasAt < tabs.getTabCount()) tabs.setSelectedIndex(wasAt);
                }

                ui[0].validate();

                inspected[0] += walk(ui[0].getContentPane(), found, already);

                // THE MENU BAR, which is not in the content pane and holds the longest single-line strings this
                // application has.
                if (ui[0].getJMenuBar() != null)
                {
                    inspected[0] += walk(ui[0].getJMenuBar(), found, already);

                    inspected[0] += menus(ui[0].getJMenuBar(), found);
                }

                titles(ui[0].getContentPane(), found);

                // No container-level checks: the per-component rule only catches a component whose width is FIXED,
                // since a free layout grants the wider preferred size and the overflow lands elsewhere. Three container
                // rules were tried and all came out: a panel wider than its parent (11 findings in English), the
                // content pane wider than 1110px (never fired), and a component past the content pane's right edge (12
                // findings in English, the function-key labels). They measure this form's own geometry, not translation
                // length, and a check that reports findings every run is one nobody reads.
                localised.put(language, textByPath(ui[0]));
            });

            return found;
        }
        finally
        {
            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
            }
        }
    }

    /** Every component that is narrower than the text it holds; `already` makes each component count once. */
    private int walk(Container parent, List<String> found, java.util.Set<Component> already)
    {
        int seen = 0;

        for (Component child : parent.getComponents())
        {
            String text = textOf(child);

            if (text != null && !text.trim().isEmpty() && child.getWidth() > 0)
            {
                // ONE LINE PER COMPONENT, not one per tab selection: the caller walks the tree again for every tab, so
                // a component outside the tabs is visited once per tab.
                if (already.add(child))
                {
                    seen++;

                    int wants = requiredWidth(child, text);

                    if (wants > child.getWidth() + SLACK)
                    {
                        found.add(name(child) + "  text needs " + wants + "px, has " + child.getWidth()
                            + "px  -  \"" + oneLine(text) + "\"");
                    }
                }
            }

            if (child instanceof Container) seen += walk((Container) child, found, already);
        }

        return seen;
    }

    /**
     * How wide this component's TEXT actually is, rather than how wide the component asked to be. Comparing
     * `getPreferredSize().width` against `getWidth()` cannot see two cases: a component whose preferred size was SET
     * (`TrainControlUI` does so on 100 labels and buttons, so no translation can move the ask), and a free layout that
     * grants the preferred width so the overflow lands on the panel or window.
     *
     * HTML text is left to the old rule: a markup label wraps in a view hierarchy, so the width of its characters is
     * not its required width, and Swing's own preferred size is the best number available.
     *
     * Returns the width the text needs, including insets, icon and gap.
     */
    private static int requiredWidth(Component c, String text)
    {
        if (text.trim().toLowerCase(Locale.ENGLISH).startsWith("<html"))
        {
            return c.getPreferredSize().width;
        }

        java.awt.Font font = c.getFont();

        if (font == null) return c.getPreferredSize().width;

        int width = c.getFontMetrics(font).stringWidth(text);

        if (c instanceof JComponent)
        {
            java.awt.Insets insets = ((JComponent) c).getInsets();

            if (insets != null) width += insets.left + insets.right;
        }

        javax.swing.Icon icon = null;

        int gap = 0;

        if (c instanceof AbstractButton)
        {
            icon = ((AbstractButton) c).getIcon();

            gap = ((AbstractButton) c).getIconTextGap();
        }
        else if (c instanceof JLabel)
        {
            icon = ((JLabel) c).getIcon();

            gap = ((JLabel) c).getIconTextGap();
        }

        if (icon != null) width += icon.getIconWidth() + gap;

        return width;
    }

    /**
     * Whether this component's text is wider than the room it has; the rule in one place, so the control can ask what
     * the walk asks.
     */
    private static boolean clips(Component c)
    {
        String text = textOf(c);

        if (text == null || text.trim().isEmpty() || c.getWidth() <= 0) return false;

        return requiredWidth(c, text) > c.getWidth() + SLACK;
    }

    /**
     * Every text-bearing component's text, keyed by its index path in the tree: the same form in every language, so the
     * path names "this label" across eight windows.
     */
    private static Map<String, String> textByPath(java.awt.Window window)
    {
        Map<String, String> out = new LinkedHashMap<>();

        collect(window, "", out);

        return out;
    }

    private static void collect(Container parent, String path, Map<String, String> into)
    {
        Component[] children = parent.getComponents();

        for (int at = 0; at < children.length; at++)
        {
            Component child = children[at];

            String here = path + "." + at;

            String text = textOf(child);

            if (text != null && !text.trim().isEmpty()) into.put(here, text);

            if (child instanceof Container) collect((Container) child, here, into);
        }

        if (parent instanceof javax.swing.JFrame)
        {
            javax.swing.JMenuBar bar = ((javax.swing.JFrame) parent).getJMenuBar();

            if (bar != null) collect(bar, path + ".menubar", into);
        }
    }

    /** Tab titles, which are drawn by the pane rather than by a component of their own. */
    private void titles(Container parent, List<String> found)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof JTabbedPane)
            {
                JTabbedPane tabs = (JTabbedPane) child;

                int room = tabs.getWidth();
                int wants = 0;

                for (int at = 0; at < tabs.getTabCount(); at++)
                {
                    java.awt.Rectangle bounds = tabs.getBoundsAt(at);

                    if (bounds != null) wants = Math.max(wants, bounds.x + bounds.width);
                }

                if (room > 0 && wants > room + SLACK)
                {
                    found.add("tab strip of " + name(tabs) + "  wants " + wants + "px, has "
                        + room + "px");
                }
            }

            if (child instanceof Container) titles((Container) child, found);
        }
    }

    /** Every tabbed pane under a container, so each of their tabs can be laid out and measured. */
    private static List<JTabbedPane> tabbedPanes(Container parent)
    {
        List<JTabbedPane> out = new ArrayList<>();

        for (Component child : parent.getComponents())
        {
            if (child instanceof JTabbedPane) out.add((JTabbedPane) child);

            if (child instanceof Container) out.addAll(tabbedPanes((Container) child));
        }

        return out;
    }

    /**
     * Menu items, which get no width until their menu is opened, so they are measured against what the popup will be:
     * the widest item in that menu.
     */
    private int menus(javax.swing.JMenuBar bar, List<String> found)
    {
        int seen = 0;

        for (int at = 0; at < bar.getMenuCount(); at++)
        {
            javax.swing.JMenu menu = bar.getMenu(at);

            if (menu == null) continue;

            int widest = 0;

            for (Component item : menu.getMenuComponents())
            {
                if (item instanceof JComponent)
                {
                    widest = Math.max(widest, ((JComponent) item).getPreferredSize().width);
                }

                seen++;
            }

            int ceiling = menuCeiling(bar);

            if (widest > ceiling)
            {
                found.add("menu \"" + oneLine(menu.getText()) + "\" is " + widest
                    + "px wide, over the " + ceiling + "px this window can show");
            }
        }

        return seen;
    }

    /** The text a component draws, or null if it draws none. */
    private static String textOf(Component c)
    {
        if (c instanceof JLabel) return ((JLabel) c).getText();

        if (c instanceof AbstractButton) return ((AbstractButton) c).getText();

        return null;
    }

    /** Something a person can find the component by. */
    private static String name(Component c)
    {
        String simple = c.getClass().getSimpleName();

        return (c.getName() == null ? simple : simple + " " + c.getName());
    }

    private static String oneLine(String text)
    {
        String flat = text.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();

        return flat.length() > 60 ? flat.substring(0, 57) + "..." : flat;
    }

    /**
     * Paints a component into a file. The CONTENT PANE is printed, not the Window painted: a JFrame never shown paints
     * an empty rectangle, while JComponent.printAll works offscreen.
     */
    private static void shoot(JComponent pane, File file)
    {
        BufferedImage shot = new BufferedImage(Math.max(1, pane.getWidth()),
            Math.max(1, pane.getHeight()), BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            pane.printAll(g);
        }
        finally
        {
            g.dispose();
        }

        try
        {
            javax.imageio.ImageIO.write(shot, "png", file);
        }
        catch (java.io.IOException ignored)
        {
            // A missing picture is not a reason to fail the measurement it illustrates
        }
    }

    /** Whether two files hold the same bytes, for the "did the language change anything" guard. */
    private static boolean sameBytes(File a, File b) throws Exception
    {
        if (!a.exists() || !b.exists()) return false;

        return java.util.Arrays.equals(java.nio.file.Files.readAllBytes(a.toPath()),
            java.nio.file.Files.readAllBytes(b.toPath()));
    }

    /**
     * Every file under a folder with its size and modification time, as one string: enough to ask "did anything here
     * move".
     */
    private static String fingerprint(File folder) throws Exception
    {
        if (!folder.isDirectory()) return "";

        final StringBuilder out = new StringBuilder();

        java.nio.file.Files.walk(folder.toPath())
            .filter(java.nio.file.Files::isRegularFile)
            .sorted()
            .forEach(p ->
            {
                try
                {
                    out.append(p).append(':').append(java.nio.file.Files.size(p)).append(':')
                        .append(java.nio.file.Files.getLastModifiedTime(p)).append('\n');
                }
                catch (java.io.IOException e)
                {
                    out.append(p).append(":unreadable\n");
                }
            });

        return out.toString();
    }
}
