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
     * How wide a menu may be before it is a problem.
     *
     * Half the declared window width, which is where a menu stops being a menu and starts being a
     * page.
     */
    private static final int MENU_CEILING = 555;

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

                clipped.put(language, measure(language, seen));

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
    private List<String> measure(String language, int[] inspected) throws Exception
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
                ui[0].setSize(new Dimension(1110, 619));
                ui[0].validate();
            });

            final List<String> found = new ArrayList<>();

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

                        inspected[0] += walk(tabs, found);
                    }

                    if (wasAt >= 0 && wasAt < tabs.getTabCount()) tabs.setSelectedIndex(wasAt);
                }

                ui[0].validate();

                inspected[0] += walk(ui[0].getContentPane(), found);

                // THE MENU BAR, which is not in the content pane and holds the longest single-line
                // strings this application has.
                if (ui[0].getJMenuBar() != null)
                {
                    inspected[0] += walk(ui[0].getJMenuBar(), found);

                    inspected[0] += menus(ui[0].getJMenuBar(), found);
                }

                titles(ui[0].getContentPane(), found);
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
    private int walk(Container parent, List<String> found)
    {
        int seen = 0;

        for (Component child : parent.getComponents())
        {
            String text = textOf(child);

            if (text != null && !text.trim().isEmpty() && child.getWidth() > 0)
            {
                seen++;

                int wants = child.getPreferredSize().width;

                if (wants > child.getWidth() + SLACK)
                {
                    found.add(name(child) + "  wants " + wants + "px, has " + child.getWidth()
                        + "px  -  \"" + oneLine(text) + "\"");
                }
            }

            if (child instanceof Container) seen += walk((Container) child, found);
        }

        return seen;
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
