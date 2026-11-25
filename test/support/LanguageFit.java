package support;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import org.traincontrol.util.I18n;

/**
 * Whether a window's text fits in every language: the rule (a component's TEXT against the width it was given), the walk
 * over a window's tabs, menus and tables, the pictures, and the ledger that compares each language with English.  Not a
 * test; `ui.testEveryLanguageFits` and its siblings measure with it.  Call {@link #measure} on the event thread.
 */
public final class LanguageFit
{
    /** The eight the bundles carry. */
    public static final String[] LANGUAGES = {"en", "de", "fr", "es", "it", "nl", "da", "pl"};

    /** Where the pictures and reports go: the run's scratch folder when it has one. */
    public static final File OUT = System.getenv("TC_SCRATCH") != null
        ? new File(System.getenv("TC_SCRATCH"), "language-shots")
        : new File(System.getProperty("java.io.tmpdir"), "tc-language-shots");

    /** Adam's real railway, which nothing here may touch. */
    public static final File LIVE = new File("cs2_sample_layout");

    /**
     * How much wider than its space a component may ask to be before it counts as clipped: a border rounding or font
     * hint costs a pixel or two without losing a character.
     */
    public static final int SLACK = 3;

    private LanguageFit()
    {
    }

    /** Sets the language every window built from now on speaks. */
    public static void speak(String language)
    {
        speak(new Locale(language));
    }

    public static void speak(Locale locale)
    {
        Locale.setDefault(locale);
        I18n.setLocale(locale);
    }

    /** What a claim measures in one language, the language already set: everything it found that does not fit. */
    public interface Door
    {
        List<String> measureIn(String language) throws Exception;
    }

    /** Runs the door in each of the eight languages, putting the language back after; what each found, by language. */
    public static Map<String, List<String>> everyLanguage(Door door) throws Exception
    {
        Locale was = Locale.getDefault();
        Locale wasSaying = I18n.getLocale();
        Map<String, List<String>> out = new LinkedHashMap<>();

        try
        {
            for (String language : LANGUAGES)
            {
                speak(language);

                out.put(language, door.measureIn(language));
            }
        }
        finally
        {
            Locale.setDefault(was);
            I18n.setLocale(wasSaying);
        }

        return out;
    }

    /**
     * {@link #measure} on the event thread, for a window on screen: what does not fit, each line naming the window.  At
     * least `least` components with text must have been measured, or the window was never laid out.
     */
    public static List<String> measureNow(Ledger ledger, String window, String language, RootPaneContainer frame,
        int least)
    {
        int[] seen = {0};
        List<String> found = new ArrayList<>();

        for (String each : Components.onTheEventThread(() -> measure(ledger, window, language, frame, seen)))
        {
            found.add(window + ": " + each);
        }

        org.testng.Assert.assertTrue(seen[0] >= least, "only " + seen[0] + " components with text were measured in "
            + window + " (" + language + "), so it was not laid out and nothing was asked");

        return found;
    }

    /** What each language found, a heading with its count over its lines. */
    public static StringBuilder report(Map<String, List<String>> clipped)
    {
        StringBuilder report = new StringBuilder();

        for (Map.Entry<String, List<String>> e : clipped.entrySet())
        {
            report.append(String.format("== %s  (%d)%n", e.getKey(), e.getValue().size()));

            for (String line : e.getValue()) report.append(String.format("   %s%n", line));
        }

        return report;
    }

    /** How many lines every language found. */
    public static int total(Map<String, List<String>> clipped)
    {
        return clipped.values().stream().mapToInt(List::size).sum();
    }

    /** Writes what each language found to `<name>-clipped.txt` and fails if anything does not fit. */
    public static void assertNothingClipped(String name, Map<String, List<String>> clipped) throws Exception
    {
        StringBuilder report = report(clipped);

        OUT.mkdirs();

        File file = new File(OUT, name + "-clipped.txt");

        java.nio.file.Files.write(file.toPath(), report.toString().getBytes("UTF-8"));

        org.testng.Assert.assertEquals(total(clipped), 0, "text is clipped - the full list is in " + file
            + System.lineSeparator() + report);
    }

    /** A window built on the event thread by `build`, measured once it has settled, and closed by `close`. */
    public static <T extends RootPaneContainer> List<String> measureBuilt(Ledger ledger, String window, String language,
        java.util.concurrent.Callable<T> build, int least, Closer<T> close) throws Exception
    {
        T built = Components.onTheEventThread(build);

        try
        {
            Components.settle();

            return measureNow(ledger, window, language, built, least);
        }
        finally
        {
            close.close(built);
        }
    }

    public interface Closer<T>
    {
        void close(T window) throws Exception;
    }

    /** Disposes a window on the event thread, for {@link #measureBuilt}. */
    public static void dispose(RootPaneContainer window)
    {
        Components.onTheEventThread(() ->
        {
            ((Window) window).dispose();

            return null;
        });
    }

    /**
     * A scenario's railway in its window over a sandbox, for the classes that measure the windows a railway fills:
     * {@link #close} writes the ledger, closes the sandbox and fails if `cs2_sample_layout` moved meanwhile.
     */
    public static final class Railway
    {
        private final String before;
        public final AskingWindows.Railway opened;
        public final org.traincontrol.marklin.MarklinControlStation model;
        public final org.traincontrol.gui.TrainControlUI ui;
        public final org.traincontrol.automationui.AutonomySession session;
        public final org.json.JSONObject asOpened;

        private Railway(String before, AskingWindows.Railway opened)
        {
            this.before = before;
            this.opened = opened;
            this.model = opened.model;
            this.ui = opened.ui;
            this.session = opened.ui.getAutonomySession();
            this.asOpened = session == null ? null : Components.onTheEventThread(() -> session.snapshotSetup());
        }

        public static Railway open(String scenario) throws Exception
        {
            String before = fingerprint(LIVE);
            Railway out = new Railway(before, AskingWindows.openRailway(scenario, null));

            Components.settle();

            org.testng.Assert.assertNotNull(out.session, "no autonomy setup on this railway");

            return out;
        }

        /** Writes the ledger as `<name>-ratio.txt`, then closes. */
        public void close(Ledger ledger, String name) throws Exception
        {
            try
            {
                ledger.write(name);
            }
            finally
            {
                opened.sandbox.close();

                org.testng.Assert.assertEquals(fingerprint(LIVE), before, "this class wrote to " + LIVE
                    + ", which is the operator's real railway");
            }
        }
    }

    /**
     * How wide a menu may be before it is a problem: half the window's width, where a menu stops being a menu and
     * starts being a page.  The window is its minimum size, the size an operator gets on first run.
     */
    public static int menuCeiling(Window window)
    {
        org.testng.Assert.assertNotNull(window, "the menu is not in a window, so there is no width to hold it to");

        return window.getMinimumSize().width / 2;
    }

    /**
     * How wide this component's TEXT actually is, not how wide it asked to be: `getPreferredSize().width` cannot see a
     * component whose preferred size was SET.  HTML text keeps the old rule (a markup label wraps in a view hierarchy).
     * Returns the width the text needs, with insets, icon and gap.
     */
    public static int requiredWidth(Component c, String text)
    {
        if (text.trim().toLowerCase(Locale.ENGLISH).startsWith("<html")) return c.getPreferredSize().width;

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

    /** Whether this component's text is wider than the room it has: the rule in one place. */
    public static boolean clips(Component c)
    {
        String text = textOf(c);

        if (text == null || text.trim().isEmpty() || c.getWidth() <= 0) return false;

        return requiredWidth(c, text) > c.getWidth() + SLACK;
    }

    /** The text a component draws, or null if it draws none. */
    public static String textOf(Component c)
    {
        if (c instanceof JLabel) return ((JLabel) c).getText();

        if (c instanceof AbstractButton) return ((AbstractButton) c).getText();

        return null;
    }

    /** Something a person can find the component by. */
    public static String name(Component c)
    {
        String simple = c.getClass().getSimpleName();

        return c.getName() == null ? simple : simple + " " + c.getName();
    }

    public static String oneLine(String text)
    {
        String flat = text.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();

        return flat.length() > 60 ? flat.substring(0, 57) + "..." : flat;
    }

    /**
     * Measures a window as it stands in one language, on the event thread: a picture of it and of each tab, every
     * component on every tab, its table headings, its menu bar and its tab strips.  Returns what does not fit; adds the
     * components looked at to `inspected[0]` and each one shown to `ledger` (which may be null).
     */
    public static List<String> measure(Ledger ledger, String window, String language, RootPaneContainer frame,
        int[] inspected)
    {
        Window w = (Window) frame;
        JComponent content = (JComponent) frame.getContentPane();
        Walk walk = new Walk(ledger, window, language, content);

        // The first picture keeps the plain name, which the main window's control compares.
        walk.shot = shoot(content, window + "-" + language + ".png");

        // EVERY TAB, not just the one showing: only the selected one has been through a layout with real text.
        for (JTabbedPane tabs : tabbedPanes(content))
        {
            int wasAt = tabs.getSelectedIndex();

            for (int at = 0; at < tabs.getTabCount(); at++)
            {
                tabs.setSelectedIndex(at);
                w.validate();

                walk.shot = shoot(content, window + "-" + language + "-" + (++walk.shots) + ".png");
                inspected[0] += walk.walk(tabs, pathOf(tabs, content));
                inspected[0] += tables(tabs, walk);
            }

            if (wasAt >= 0 && wasAt < tabs.getTabCount()) tabs.setSelectedIndex(wasAt);
        }

        w.validate();

        if (walk.shots > 0) walk.shot = shoot(content, window + "-" + language + "-" + (++walk.shots) + ".png");

        inspected[0] += walk.walk(content, "");
        inspected[0] += tables(content, walk);

        // THE MENU BAR, which is not in the content pane and holds the longest single-line strings this application has.
        JMenuBar bar = frame.getRootPane().getJMenuBar();

        if (bar != null)
        {
            Walk unpictured = new Walk(null, window, language, bar);

            unpictured.already.addAll(walk.already);

            inspected[0] += unpictured.walk(bar, "menubar");
            walk.found.addAll(unpictured.found);

            inspected[0] += menus(bar, walk.found);
        }

        titles(content, walk.found);

        return walk.found;
    }

    /** {@link #measure} for a dialog on screen, which must also fit on the screen it is shown on. */
    public static List<String> measurePopup(Ledger ledger, String window, String language, javax.swing.JDialog dialog,
        int[] inspected)
    {
        List<String> found = measure(ledger, window, language, dialog, inspected);

        Rectangle screen = dialog.getGraphicsConfiguration().getBounds();

        if (dialog.getWidth() > screen.width)
        {
            found.add("dialog \"" + oneLine(String.valueOf(dialog.getTitle())) + "\" is " + dialog.getWidth()
                + "px wide, over the " + screen.width + "px screen");
        }

        return found;
    }

    /** One walk of a window: what it found, each component counted once, and the picture it stands in. */
    private static final class Walk
    {
        final Ledger ledger;
        final String window;
        final String language;
        final Container root;
        final List<String> found = new ArrayList<>();
        final Set<Component> already = Collections.newSetFromMap(new IdentityHashMap<Component, Boolean>());
        final Set<Component> recorded = Collections.newSetFromMap(new IdentityHashMap<Component, Boolean>());
        String shot;
        int shots;

        Walk(Ledger ledger, String window, String language, Container root)
        {
            this.ledger = ledger;
            this.window = window;
            this.language = language;
            this.root = root;
        }

        /** Every component that is narrower than the text it holds; returns how many were looked at. */
        int walk(Container parent, String path)
        {
            int seen = 0;
            Component[] children = parent.getComponents();

            for (int at = 0; at < children.length; at++)
            {
                Component child = children[at];
                String here = path + "." + at;
                String text = textOf(child);

                // The diagram's squares and captions draw the railway's own names across their neighbors by design.
                if (child instanceof org.traincontrol.gui.LayoutLabel
                    || child instanceof org.traincontrol.gui.layout.StationCaption) continue;

                if (text != null && !text.trim().isEmpty() && child.getWidth() > 0)
                {
                    // ONE LINE PER COMPONENT, not one per tab selection: a component outside the tabs is walked once per tab.
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

                    // Into the ledger only once it is in the picture: a hidden tab's component waits for its own tab.
                    if (ledger != null && shownIn(child, root) && recorded.add(child))
                    {
                        ledger.put(window, language, here, new Seen(text, requiredWidth(child, text), child, shot,
                            SwingUtilities.convertRectangle(parent, child.getBounds(), root)));
                    }
                }

                if (child instanceof Container) seen += walk((Container) child, here);
            }

            return seen;
        }
    }

    /** Whether every container from this component up to the root is visible, so the picture shows it. */
    private static boolean shownIn(Component c, Container root)
    {
        for (Component at = c; at != null && at != root; at = at.getParent())
        {
            if (!at.isVisible()) return false;
        }

        return true;
    }

    /** A component's place in the tree as child indexes from the root, the same form in every language. */
    private static String pathOf(Component c, Container root)
    {
        StringBuilder path = new StringBuilder();

        for (Component at = c; at != null && at != root; at = at.getParent())
        {
            Container parent = at.getParent();

            if (parent == null) break;

            path.insert(0, "." + java.util.Arrays.asList(parent.getComponents()).indexOf(at));
        }

        return path.toString();
    }

    /**
     * Table column headings, which the header draws with a renderer rather than a component of their own: each against
     * its column's width.  Returns how many were looked at.
     */
    private static int tables(Container parent, Walk walk)
    {
        int seen = 0;

        for (JTable table : Components.all(parent, JTable.class, null))
        {
            javax.swing.table.JTableHeader header = table.getTableHeader();

            // Once it is on show, as a hidden tab's table can hold a width from an earlier layout.
            if (header == null || table.getWidth() <= 0 || !shownIn(table, walk.root) || !walk.already.add(table)) continue;

            for (int column = 0; column < table.getColumnCount(); column++)
            {
                String text = String.valueOf(table.getColumnModel().getColumn(column).getHeaderValue());
                int has = table.getColumnModel().getColumn(column).getWidth();

                if (text.trim().isEmpty() || has <= 0) continue;

                seen++;

                int wants = headingWidth(table, column, text);

                if (wants > has + SLACK)
                {
                    walk.found.add("heading of " + name(table) + " column " + column + "  text needs " + wants
                        + "px, has " + has + "px  -  \"" + oneLine(text) + "\"");
                }

                if (walk.ledger != null)
                {
                    walk.ledger.put(walk.window, walk.language, pathOf(table, walk.root) + "#" + column,
                        new Seen(text, wants, has, has, has, has, header.getWidth(), walk.shot,
                            SwingUtilities.convertRectangle(header, header.getHeaderRect(column), walk.root), has, true));
                }
            }
        }

        return seen;
    }

    /** How wide a column heading's text is, drawn by the header's own renderer. */
    public static int headingWidth(JTable table, int column, String text)
    {
        javax.swing.table.TableCellRenderer renderer = table.getColumnModel().getColumn(column).getHeaderRenderer();

        if (renderer == null) renderer = table.getTableHeader().getDefaultRenderer();

        Component drawn = renderer.getTableCellRendererComponent(table, text, false, false, -1, column);

        if (drawn instanceof JLabel) ((JLabel) drawn).setIcon(null);

        return requiredWidth(drawn, text);
    }

    /** Tab titles, which are drawn by the pane rather than by a component of their own. */
    private static void titles(Container parent, List<String> found)
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
                    Rectangle bounds = tabs.getBoundsAt(at);

                    if (bounds != null) wants = Math.max(wants, bounds.x + bounds.width);
                }

                if (room > 0 && wants > room + SLACK)
                {
                    found.add("tab strip of " + name(tabs) + "  wants " + wants + "px, has " + room + "px");
                }
            }

            if (child instanceof Container) titles((Container) child, found);
        }
    }

    /** Every tabbed pane under a container, so each of their tabs can be laid out and measured. */
    public static List<JTabbedPane> tabbedPanes(Container parent)
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
    private static int menus(JMenuBar bar, List<String> found)
    {
        int seen = 0;

        for (int at = 0; at < bar.getMenuCount(); at++)
        {
            javax.swing.JMenu menu = bar.getMenu(at);

            if (menu == null) continue;

            int widest = 0;

            for (Component item : menu.getMenuComponents())
            {
                if (item instanceof JComponent) widest = Math.max(widest, ((JComponent) item).getPreferredSize().width);

                seen++;
            }

            int ceiling = menuCeiling(SwingUtilities.getWindowAncestor(bar));

            if (widest > ceiling)
            {
                found.add("menu \"" + oneLine(menu.getText()) + "\" is " + widest + "px wide, over the " + ceiling
                    + "px this window can show");
            }
        }

        return seen;
    }

    /**
     * A right-click menu held to the menu bar's rule: its widest item that says something, against `ceiling`.  Returns
     * the texts measured, submenus' included, so a caller can name the ones it must have seen.
     */
    public static List<String> popupMenu(JPopupMenu menu, String name, int ceiling, List<String> found)
    {
        List<String> texts = new ArrayList<>();
        int widest = 0;

        for (Component item : menu.getComponents())
        {
            if (!(item instanceof JMenuItem) || textOf(item) == null || textOf(item).trim().isEmpty()) continue;

            texts.add(textOf(item));
            widest = Math.max(widest, item.getPreferredSize().width);

            if (item instanceof javax.swing.JMenu)
            {
                texts.addAll(popupMenu(((javax.swing.JMenu) item).getPopupMenu(), name + " > " + textOf(item), ceiling,
                    found));
            }
        }

        if (widest > ceiling)
        {
            found.add("right-click menu " + name + " is " + widest + "px wide, over the " + ceiling
                + "px this window can show");
        }

        return texts;
    }

    /**
     * Every text-bearing component's text, keyed by its index path in the tree: the same form in every language, so the
     * path names "this label" across eight windows.
     */
    public static Map<String, String> textByPath(Window window)
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
            JMenuBar bar = ((javax.swing.JFrame) parent).getJMenuBar();

            if (bar != null) collect(bar, path + ".menubar", into);
        }
    }

    /**
     * Paints a component into a file under {@link #OUT} and returns the file's name.  The CONTENT PANE is printed, not
     * the Window painted: a JFrame never shown paints an empty rectangle, while JComponent.printAll works offscreen.
     */
    public static String shoot(JComponent pane, String file)
    {
        OUT.mkdirs();

        BufferedImage shot = new BufferedImage(Math.max(1, pane.getWidth()), Math.max(1, pane.getHeight()),
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = shot.createGraphics();

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
            javax.imageio.ImageIO.write(shot, "png", new File(OUT, file));
        }
        catch (java.io.IOException ignored)
        {
            // A missing picture is not a reason to fail the measurement it illustrates
        }

        return file;
    }

    /** Whether two files hold the same bytes, for the "did the language change anything" guard. */
    public static boolean sameBytes(File a, File b) throws Exception
    {
        if (!a.exists() || !b.exists()) return false;

        return java.util.Arrays.equals(java.nio.file.Files.readAllBytes(a.toPath()),
            java.nio.file.Files.readAllBytes(b.toPath()));
    }

    /** Every file under a folder with its size and modification time, as one string: enough to ask "did anything move". */
    public static String fingerprint(File folder) throws Exception
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

    /** One component as one language laid it out: its text's width, its own widths, and where it is in which picture. */
    public static final class Seen
    {
        public final String text;
        public final int wants;
        public final int width;
        public final int minimum;
        public final int preferred;
        public final int maximum;
        public final int room;
        public final String shot;
        public final Rectangle at;

        /** How much of its width the window shows, and whether a scroll pane holds it (where a cut is by design). */
        public final int visible;
        public final boolean scrolled;

        Seen(String text, int wants, Component c, String shot, Rectangle at)
        {
            this(text, wants, c.getWidth(), c.getMinimumSize().width, c.getPreferredSize().width,
                c.getMaximumSize().width, roomOf(c), shot, at, c instanceof JComponent
                ? ((JComponent) c).getVisibleRect().width : c.getWidth(),
                SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, c) != null);
        }

        Seen(String text, int wants, int width, int minimum, int preferred, int maximum, int room, String shot,
            Rectangle at, int visible, boolean scrolled)
        {
            this.visible = visible;
            this.scrolled = scrolled;
            this.text = text;
            this.wants = wants;
            this.width = width;
            this.minimum = minimum;
            this.preferred = preferred;
            this.maximum = maximum;
            this.room = room;
            this.shot = shot;
            this.at = at;
        }

        private static int roomOf(Component c)
        {
            Container parent = c.getParent();

            if (parent == null) return c.getWidth();

            java.awt.Insets in = parent.getInsets();

            return parent.getWidth() - in.left - in.right;
        }
    }

    /**
     * Every component every window showed, by window, language and path, and the comparison with English: a component
     * is flagged where a language's text is wider than its space (clipped), or wider than the space English had (the
     * language needs more than the headroom English leaves, which a layout that grew can hide).
     */
    public static final class Ledger
    {
        private final Map<String, Map<String, Map<String, Seen>>> seen = new LinkedHashMap<>();

        public synchronized void put(String window, String language, String path, Seen one)
        {
            seen.computeIfAbsent(window, w -> new LinkedHashMap<>()).computeIfAbsent(language, l -> new LinkedHashMap<>())
                .put(path, one);
        }

        /** Whether this window showed this text in this language, word for word or as part of a longer text. */
        public synchronized boolean saw(String window, String language, String text)
        {
            Map<String, Seen> shown = seen.getOrDefault(window, Collections.emptyMap()).get(language);

            if (shown == null) return false;

            for (Seen each : shown.values()) if (each.text.contains(text)) return true;

            return false;
        }

        /** One component one language flags. */
        public static final class Flag
        {
            public final String window;
            public final String language;
            public final String path;
            public final Seen english;
            public final Seen here;
            public final boolean clipped;
            public final boolean overEnglish;
            public final boolean pushed;

            Flag(String window, String language, String path, Seen english, Seen here, boolean clipped,
                boolean overEnglish, boolean pushed)
            {
                this.pushed = pushed;
                this.window = window;
                this.language = language;
                this.path = path;
                this.english = english;
                this.here = here;
                this.clipped = clipped;
                this.overEnglish = overEnglish;
            }
        }

        public synchronized List<Flag> flags()
        {
            List<Flag> out = new ArrayList<>();

            for (Map.Entry<String, Map<String, Map<String, Seen>>> window : seen.entrySet())
            {
                Map<String, Seen> english = window.getValue().getOrDefault("en", Collections.emptyMap());

                for (Map.Entry<String, Map<String, Seen>> language : window.getValue().entrySet())
                {
                    for (Map.Entry<String, Seen> one : language.getValue().entrySet())
                    {
                        Seen here = one.getValue();
                        Seen en = english.get(one.getKey());
                        boolean clipped = here.wants > here.width + SLACK;
                        boolean over = en != null && !"en".equals(language.getKey()) && !en.text.equals(here.text)
                            && en.wants > 0 && here.wants > en.width + SLACK;
                        boolean pushed = cut(here) && (en == null || !cut(en))
                            || overlapped(language.getValue(), one.getKey(), english);

                        if (clipped || over || pushed)
                        {
                            out.add(new Flag(window.getKey(), language.getKey(), one.getKey(), en, here, clipped, over,
                                pushed));
                        }
                    }
                }
            }

            return out;
        }

        /** Whether another component overlaps this one by more than the slack in this language, and did not in English. */
        private static boolean overlapped(Map<String, Seen> shown, String path, Map<String, Seen> english)
        {
            Seen here = shown.get(path);

            for (Map.Entry<String, Seen> other : shown.entrySet())
            {
                if (other.getKey().equals(path) || other.getKey().startsWith(path + ".") || path.startsWith(other.getKey() + ".")
                    || other.getValue().shot == null || !other.getValue().shot.equals(here.shot)) continue;

                Rectangle both = here.at.intersection(other.getValue().at);

                if (both.width <= SLACK || both.height <= SLACK) continue;

                Seen a = english.get(path);
                Seen b = english.get(other.getKey());

                if (a == null || b == null) return true;

                Rectangle before = a.at.intersection(b.at);

                if (before.width <= SLACK || before.height <= SLACK) return true;
            }

            return false;
        }

        /** Whether the window's edge or a neighbor cuts a component off, outside any scroll pane. */
        private static boolean cut(Seen one)
        {
            return !one.scrolled && one.visible < Math.min(one.width, one.wants) - SLACK;
        }

        /**
         * Writes `<name>-ratio.txt` (the counts by window and language, then every flag) and, under `flags/`, one sheet
         * per window and language with each flag's English and translated crops one above the other.
         */
        public synchronized void write(String name) throws Exception
        {
            List<Flag> flags = flags();
            StringBuilder report = new StringBuilder();
            String nl = System.lineSeparator();

            report.append("window  language  measured  over-English  clipped  pushed").append(nl);

            for (Map.Entry<String, Map<String, Map<String, Seen>>> window : seen.entrySet())
            {
                for (String language : window.getValue().keySet())
                {
                    int over = 0;
                    int clipped = 0;
                    int pushed = 0;

                    for (Flag f : flags)
                    {
                        if (!f.window.equals(window.getKey()) || !f.language.equals(language)) continue;

                        if (f.overEnglish) over++;
                        if (f.clipped) clipped++;
                        if (f.pushed) pushed++;
                    }

                    report.append(window.getKey()).append("  ").append(language).append("  ")
                        .append(window.getValue().get(language).size()).append("  ").append(over).append("  ")
                        .append(clipped).append("  ").append(pushed).append(nl);
                }
            }

            report.append(nl);

            File sheets = new File(OUT, "flags");

            sheets.mkdirs();

            Map<String, List<Flag>> bySheet = new LinkedHashMap<>();

            for (Flag f : flags) bySheet.computeIfAbsent(f.window + "-" + f.language, k -> new ArrayList<>()).add(f);

            for (Map.Entry<String, List<Flag>> sheet : bySheet.entrySet())
            {
                int n = 0;

                for (Flag f : sheet.getValue())
                {
                    n++;
                    report.append(String.format("%s #%d%s%s%s  \"%s\" wants %d has %d (min %d, preferred %d, max %d, room %d,"
                        + " shown %d)", sheet.getKey(), n, f.clipped ? "  CLIPPED" : "", f.overEnglish ? "  OVER-ENGLISH" : "",
                        f.pushed ? "  PUSHED" : "", oneLine(f.here.text), f.here.wants, f.here.width, f.here.minimum,
                        f.here.preferred, f.here.maximum, f.here.room, f.here.visible));

                    if (f.english != null)
                    {
                        report.append(String.format("; English \"%s\" wants %d has %d", oneLine(f.english.text),
                            f.english.wants, f.english.width));
                    }

                    report.append("  [").append(f.path).append(" in ").append(f.here.shot).append("]").append(nl);
                }

                javax.imageio.ImageIO.write(sheet(sheet.getKey(), sheet.getValue()), "png",
                    new File(sheets, sheet.getKey() + ".png"));
            }

            java.nio.file.Files.write(new File(OUT, name + "-ratio.txt").toPath(), report.toString().getBytes("UTF-8"));
        }

        private static BufferedImage sheet(String title, List<Flag> flags) throws Exception
        {
            Map<String, BufferedImage> pictures = new java.util.HashMap<>();
            List<BufferedImage> rows = new ArrayList<>();
            int n = 0;

            for (Flag f : flags)
            {
                n++;

                BufferedImage en = f.english == null ? null : crop(pictures, f.english, f.here.wants);
                BufferedImage here = crop(pictures, f.here, f.here.wants);
                String caption = title + " #" + n + (f.clipped ? " clipped" : "") + (f.overEnglish ? " over-English" : "")
                    + (f.pushed ? " pushed" : "")
                    + ": wants " + f.here.wants + ", has " + f.here.width + (f.english == null ? ""
                    : "; English had " + f.english.width);

                rows.add(row(caption, f.language, en, here));
            }

            int width = 0;
            int height = 0;

            for (BufferedImage r : rows)
            {
                width = Math.max(width, r.getWidth());
                height += r.getHeight();
            }

            BufferedImage out = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            int y = 0;

            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());

            for (BufferedImage r : rows)
            {
                g.drawImage(r, 0, y, null);
                y += r.getHeight();
            }

            g.dispose();

            return out;
        }

        /** The component and some room around it, out of the picture it stood in. */
        private static BufferedImage crop(Map<String, BufferedImage> pictures, Seen one, int wants) throws Exception
        {
            BufferedImage picture = pictures.get(one.shot);

            if (picture == null && one.shot != null)
            {
                picture = javax.imageio.ImageIO.read(new File(OUT, one.shot));
                pictures.put(one.shot, picture);
            }

            if (picture == null || one.at == null) return null;

            // Wide enough to show its neighbors to the window's edge, where a grown component would push them.
            int x = Math.max(0, one.at.x - 200);
            int y = Math.max(0, one.at.y - 8);
            int right = Math.min(picture.getWidth(), one.at.x + Math.max(one.at.width, wants) + 260);
            int bottom = Math.min(picture.getHeight(), one.at.y + one.at.height + 8);

            if (right <= x || bottom <= y) return null;

            BufferedImage cut = picture.getSubimage(x, y, right - x, bottom - y);

            if (right < picture.getWidth()) return cut;

            // A red line where the window ends, so a crop's own edge is not read as the window's.
            BufferedImage edged = new BufferedImage(cut.getWidth() + 3, cut.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = edged.createGraphics();

            g.drawImage(cut, 0, 0, null);
            g.setColor(java.awt.Color.RED);
            g.fillRect(cut.getWidth(), 0, 3, cut.getHeight());
            g.dispose();

            return edged;
        }

        private static BufferedImage row(String caption, String language, BufferedImage en, BufferedImage here)
        {
            int width = Math.max(600, Math.max(en == null ? 0 : en.getWidth(), here == null ? 0 : here.getWidth()) + 40);
            int height = 22 + (en == null ? 14 : en.getHeight() + 4) + (here == null ? 14 : here.getHeight() + 4) + 10;
            BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();

            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(235, 235, 245));
            g.fillRect(0, 0, width, height);
            g.setColor(java.awt.Color.BLACK);
            g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            g.drawString(caption, 4, 15);

            int y = 22;

            g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
            g.drawString("en", 4, y + 12);

            if (en != null)
            {
                g.drawImage(en, 30, y, null);
                y += en.getHeight() + 4;
            }
            else
            {
                y += 14;
            }

            g.drawString(language, 4, y + 12);

            if (here != null) g.drawImage(here, 30, y, null);

            g.setColor(java.awt.Color.GRAY);
            g.drawLine(0, height - 1, width, height - 1);
            g.dispose();

            return out;
        }
    }
}
