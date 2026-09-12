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
 * Adam, 2026-08-30: "run the app in each language and take screenshots. Check for text that spills
 * over or displaces components due to lengths, and shorten as needed."
 *
 * **Nothing in this project has ever checked a string's LENGTH.** `testMessageBundles` checks that
 * every key is present in all eight bundles, that the escapes are well formed, that the placeholders
 * match and that no straight apostrophe eats a `{0}` - all of which are about a string being CORRECT.
 * Whether it fits is a different question, and German and Polish routinely run half again as long as
 * the English. The routing dropdown has already had to be capped at 230px for exactly that reason.
 *
 * **What "does not fit" means here.** A Swing component narrower than its preferred width draws its
 * text clipped, with an ellipsis - so the operator reads "Konfiguration wird gel..." and the sentence
 * explaining a control is gone. One comparison per component, on a window laid out at the size the
 * form declares.
 *
 * **This test damaged the operator's railway once, and the guard below is why it may run again.**
 * The first version opened and closed a sandbox around each of the eight windows; something a
 * disposed window had already scheduled then wrote after the sandbox had put the layout preference
 * back, and the autonomy configuration of `cs2_sample_layout` - which is Adam's real railway and is
 * not recoverable - was rebuilt against the fixture diagram, losing facings, placements, priorities
 * and an exclusion list. So: ONE sandbox around the whole class, and this test fingerprints that
 * folder itself and fails if a single byte of it moved. The battery has the same guard; a test that
 * can do this should not wait for the harness to notice.
 *
 * **What this cannot see.** A window built here has no model behind it, so panels that fill from a
 * railway are empty. Static text - menus, buttons, the labels beside controls - is what this covers,
 * and it is where length bites.
 *
 * **And what it could not see until 2026-09-11, which is the more interesting half.** Adam asked how
 * thorough it is - *"I would be surprised if everything fits across the board, unless you have made lots
 * of string edits"* - and the measurement says he was right. With all 1937 German strings 39 characters
 * longer, the old rule found FOUR components. It compared what a component ASKED for against what it was
 * GIVEN, and `TrainControlUI` sets an explicit preferred size on 100 of its labels and buttons, so for
 * those the ask is a constant that no translation can move. `requiredWidth` measures the characters now,
 * and finds five - one more, which is honest about how much of the gap that closed.
 *
 * **The reach, measured the same day.** 246 text-bearing components are inspected - the 1408 this used to
 * report was the same components counted once per tab selection - and the locale changes 67 to 73 of them,
 * 69 in German. The rest carry text the generated form hard-codes, so a German run leaves them in English
 * and they can say nothing about translation length. Seventy labels is what a clean run is a statement
 * about, and `LOCALISED_FLOOR` keeps it from quietly becoming none.
 *
 * **A container that overflows is still not measured.** A panel laid out wider than the window, or a
 * component positioned past its parent's edge, is a different check - and scroll panes legitimately hold
 * content wider than their viewport, so it needs a rule about where to stop looking. Said here rather
 * than left for the next person to assume it is covered.
 *
 * **The tab titles are IMAGES**, not text (Adam, 2026-08-30), which is why they read the same in every
 * language and why nothing here measures them. They cannot spill; a picture is the width it is. The
 * tab-strip check below is about the strip running past the pane, which is a different question.
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
     * How much wider than its space a component may ask to be before it counts as clipped.
     *
     * Not zero. A handful of components ask for a pixel or two more than they are given as a matter
     * of course - a border rounding, a font hint - and none of those loses a character.
     */
    private static final int SLACK = 3;

    /**
     * The window the form declares, which is the window an operator gets on first run.
     *
     * `pack()` gives the window whatever the longest string in the language being measured needs, which
     * is the question rather than the answer - so the measurements are taken at this size.
     */
    /**
     * How many text-bearing components a non-English run is expected to change.
     *
     * Set from the measurement rather than chosen, and a FLOOR: the point is to catch the locale failing
     * to reach the window, which would make every clean run above a statement about English.
     */
    private static final int LOCALISED_FLOOR = 30;

    private static final int WINDOW_WIDTH = 1110;

    private static final int WINDOW_HEIGHT = 619;

    /**
     * How wide a menu may be before it is a problem.
     *
     * Half the declared window width, which is where a menu stops being a menu and starts being a
     * page.
     */
    private static final int MENU_CEILING = 555;

    /**
     * The rule can fail, which nothing here used to show (Adam, 2026-09-11).
     *
     * He asked how thorough this test is: *"I would be surprised if everything fits across the board,
     * unless you have made lots of string edits."*  He was right to be.  The test below reported zero
     * clipped components in all eight languages, and the reason was the rule and not the translations:
     * every one of the 1937 German strings was made 39 characters longer and it found FOUR of the 246
     * components it inspects.
     *
     * **A test whose check cannot be seen to fail is a test of nothing**, and the eight-language walk
     * cannot carry its own control - the only way to make a real string too long is to edit a bundle,
     * which is not something a test may do.  So the RULE is controlled here instead, on a component this
     * method owns: a short string in 80 pixels fits, the same label with a long one does not.
     *
     * MUTATION: comparing `getPreferredSize().width` against `getWidth()` again - the rule as it stood
     * until today - fails the second assertion, because `setPreferredSize` is what the fixture uses to
     * fix the width and that is exactly the case the old rule could not see.
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

        // WHAT EACH LANGUAGE PUTS ON THE WINDOW, so the reach of this test is a number rather than a hope.
        Map<String, Map<String, String>> localised = new LinkedHashMap<>();

        // ONE sandbox for the whole class, opened before any window and closed after the last.
        //
        // Not one per language: a window schedules work that outlives dispose(), and with the
        // preference already put back that work wrote to the operator's own layout.
        support.LayoutSandbox sandbox = null;

        try
        {
            // Inside the try, so nothing can leave the preference behind (TSX-B8).
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

            // LET WHAT THE WINDOWS POSTED RUN FIRST (TSX-C2).
            //
            // The failure this check exists for is work scheduled by a disposed window landing after
            // the preference has been put back - which by construction happens some milliseconds after
            // the last dispose.  Fingerprinting before draining the queue samples the folder at the one
            // moment that cannot have seen it.
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

            // AND IN THE FINALLY, because the run most likely to have written is the one that threw
            // (TSX-C2).  This stood after the try, so a window constructor that failed took the
            // railway check with it.
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

        // HOW MANY OF THEM THE LOCALE ACTUALLY MOVES, which is the reach of this test (Adam,
        // 2026-09-11: *"how thorough is the test?  I would be surprised if everything fits across the
        // board."*).
        //
        // The walk visits every component with text, and most of this form's text is baked into the
        // generated layout rather than passed through `I18n` - so a German run leaves much of it in
        // English, and those components cannot tell anybody anything about translation length. The
        // number below is how many labels and buttons this test is really about.
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

        // WHAT WAS LOOKED AT, before what was found.
        //
        // The first version of this test inspected nothing and reported nothing wrong: a window that
        // is not displayable lays nothing out, so every component had a width of zero and every one
        // was skipped.  Both guards below fail on that version.
        for (Map.Entry<String, Integer> e : inspected.entrySet())
        {
            assertTrue(e.getValue() >= 40,
                "only " + e.getValue() + " components with text were measured in " + e.getKey()
                + ", which is not a window - the layout did not happen and nothing was asked");
        }

        // THE TWO PICTURES HAVE TO EXIST FIRST (TSX-C1).
        //
        // `sameBytes` answers false when either file is missing, and `shoot` swallows a write failure
        // by design - so an unwritable OUT, or a JVM with no PNG writer, satisfied the control below
        // by there being nothing to compare.  That control is the one thing here that proves eight
        // measurements are of eight languages rather than eight of one.
        assertTrue(new File(OUT, "window-en.png").isFile() && new File(OUT, "window-de.png").isFile(),
            "the screenshots this control compares were never written, so it would pass having "
            + "compared nothing.  Looked in " + OUT);

        assertFalse(sameBytes(new File(OUT, "window-en.png"), new File(OUT, "window-de.png")),
            "the English and German windows are byte-identical, so the locale is not reaching the "
            + "text and all eight measurements are of the same language");

        // AND A FLOOR UNDER THE REACH, which the screenshot control above cannot give.
        //
        // Two pictures differing proves the locale reached SOMETHING - one word would do it. This says
        // how many of the components the walk measures are actually in the language being measured, and
        // it is the number that says what a clean run is worth. Measured 2026-09-11; a floor rather than
        // the figure, because translating one more label should not fail a test.
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
     * Builds the window in one language, writes its picture, and returns everything that does not fit.
     *
     * @param language the two-letter code
     * @param inspected counts the components looked at, so the caller can insist there were some
     * @return one line per clipped component
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

                // PACKED, which is what makes the window displayable.  Without it nothing is laid out
                // and nothing paints: every component reports a width of zero and eight identical
                // blank pictures come out.
                ui[0].pack();

                // Then the size the form declares, which is the window an operator gets on first run
                // - pack() gives it whatever the longest string in THIS language needs, which is the
                // question rather than the answer.
                ui[0].setSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
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

                // EVERY TAB, not just the one showing: a tabbed pane sizes its children to the
                // content area, but only the selected one has been through a layout with real text.
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

                // THE MENU BAR, which is not in the content pane and holds the longest single-line
                // strings this application has.
                if (ui[0].getJMenuBar() != null)
                {
                    inspected[0] += walk(ui[0].getJMenuBar(), found, already);

                    inspected[0] += menus(ui[0].getJMenuBar(), found);
                }

                titles(ui[0].getContentPane(), found);

                // THREE CONTAINER-LEVEL CHECKS WERE TRIED HERE AND ALL THREE CAME OUT (2026-09-11).
                //
                // The reasoning was sound and is still true: the per-component rule can only catch a
                // component whose width is FIXED, because where the layout is free to grow it grants the
                // wider preferred size - the text then fits inside the component and the overflow lands
                // somewhere else. With every German string 39 characters longer, the component rule finds
                // five of the 246 it inspects. So the gap is real and it is large.
                //
                // What was measured, and why each rule went:
                //
                //   a panel wider than its parent ......... 11 findings in ENGLISH, 4px to 17px over,
                //                                          identical in French, Spanish and Italian
                //   the content pane wider than 1110px .... never fired, even with the planted strings
                //   a component past the content pane's
                //     right edge .......................... 12 findings in ENGLISH, 13px over, the same
                //                                          in all eight languages - the function-key
                //                                          labels on the locomotive panel
                //
                // Two of the three fire on English, which settles it: they measure this form's own
                // geometry at its declared size and not the length of anybody's translation, and a
                // language check that reports twelve findings every run is one nobody reads. The third
                // measures nothing at all.
                //
                // Written down with the figures rather than left out silently, so the next person does
                // not spend the afternoon arriving here. Whether those twelve are a real overflow in the
                // shipped window is a separate question from this test's, and it is Adam's to look at.
                //
                // WHAT THE REACH ACTUALLY IS gets counted instead, below.
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

    /**
     * Every component that is narrower than the text it holds.
     *
     * @param parent where to look
     * @param found where to record
     * @return how many components with text were measured
     */
    private int walk(Container parent, List<String> found, java.util.Set<Component> already)
    {
        int seen = 0;

        for (Component child : parent.getComponents())
        {
            String text = textOf(child);

            if (text != null && !text.trim().isEmpty() && child.getWidth() > 0)
            {
                // ONE LINE PER COMPONENT, not one per tab selection.  The caller walks the tree again
                // for every tab so that each tab gets a layout with real text in it, which means a
                // component outside the tabs is visited seven times; the planted-string run reported
                // the same three labels seven times each and called it 25 findings.
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
     * How wide this component's TEXT actually is, rather than how wide the component asked to be.
     *
     * **This is the whole of what was wrong with this test** (measured 2026-09-11). It used to compare
     * `getPreferredSize().width` against `getWidth()` - "was this component given less room than it
     * asked for" - which is not the same question as "does the text fit". They come apart two ways:
     *
     * - A component whose preferred size was SET loses the connection between its ask and its text.
     *   `TrainControlUI` calls `setPreferredSize(new Dimension(...))` 150 times and 100 of those are on
     *   labels or buttons, so for them the ask is a constant and no translation can move it.
     * - Where the layout is free to grow, it grants the preferred width and what overflows is the panel
     *   or the window - so again the component got what it asked for.
     *
     * The test was run with all 1937 German strings 39 characters longer: the old rule found FOUR of the
     * 246 components it inspects and this one finds five - the extra is a "Strom AUS" button with 200px of
     * room and 325px of text. That is the sensitivity, measured rather than assumed, and it is why zero
     * findings in German and Polish was never evidence that the strings fit.
     *
     * **HTML text is left to the old rule.** A label whose text is markup wraps and is laid out by a
     * view hierarchy, so the width of its characters is not its required width, and `stringWidth` over
     * the tags would be nonsense. Swing computes that preferred size properly, so for those the ask is
     * the best number available.
     *
     * @param c the component
     * @param text what it draws
     * @return the width the text needs, including insets, icon and gap
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
     * Whether this component's text is wider than the room it has.
     *
     * The rule, in one place, so the control below can ask the same question the walk asks.
     *
     * @param c the component, already sized
     * @return true when the text does not fit
     */
    private static boolean clips(Component c)
    {
        String text = textOf(c);

        if (text == null || text.trim().isEmpty() || c.getWidth() <= 0) return false;

        return requiredWidth(c, text) > c.getWidth() + SLACK;
    }

    /**
     * Every text-bearing component's text, keyed by where it sits in the tree.
     *
     * The tree is the same shape in every language - it is the same form - so the index path is a stable
     * name for "this label" across eight separate windows, which is what makes the two runs comparable.
     *
     * @param window the window to read
     * @return the path of every component with text, mapped to that text
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

    /**
     * Tab titles, which are drawn by the pane rather than by a component of their own.
     */
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
     * Menu items, which never get a width until their menu is opened, so they are measured against
     * what the popup will be - the widest item in that menu, which is how Swing sizes it.
     *
     * @param bar the menu bar
     * @param found where to record
     * @return how many items were looked at
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

            if (widest > MENU_CEILING)
            {
                found.add("menu \"" + oneLine(menu.getText()) + "\" is " + widest
                    + "px wide, over the " + MENU_CEILING + "px this window can show");
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
     * Paints a component into a file, which is what "take screenshots" asks for and is repeatable in
     * a way a screen grab is not.
     *
     * THE CONTENT PANE, printed - not the Window, painted. A JFrame that has never been shown paints
     * an empty rectangle; JComponent.printAll works offscreen and draws the lightweight hierarchy.
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
     * Every file under a folder and its size and modification time, as one string.
     *
     * Cheap, and enough: this is asking "did anything here move", not "what changed".
     *
     * @param folder the folder, which need not exist
     * @return a fingerprint that changes when the folder does
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
