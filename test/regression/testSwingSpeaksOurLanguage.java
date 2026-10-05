package regression;

import java.util.Locale;
import javax.swing.UIManager;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * Swing's own words - a popup's title and buttons, the file chooser's buttons - are in TrainControl's language (OB-318;
 * Adam, 2026-10-04: *"the popup titles message and input are not always translated, nor are file chooser buttons."*).
 *
 * Swing takes them from its own resources, by the computer's language, and has none for Danish, Dutch or Polish; so a
 * popup with no title of its own said "Message" or "Input", and the file chooser said Open and Cancel, in English.  They
 * are put from TrainControl's bundles as the look and feel is installed.
 *
 * @author Adam
 */
public class testSwingSpeaksOurLanguage
{
    /** Swing's word to TrainControl's message key, for the words every popup and file chooser shows. */
    private static final String[][] SHOWN = {
        {"OptionPane.messageDialogTitle", "swing.optionPane.messageTitle"},
        {"OptionPane.inputDialogTitle", "swing.optionPane.inputTitle"},
        {"OptionPane.okButtonText", "ui.ok"},
        {"OptionPane.cancelButtonText", "ui.cancel"},
        {"OptionPane.yesButtonText", "ui.yes"},
        {"OptionPane.noButtonText", "ui.no"},
        {"FileChooser.openButtonText", "swing.fileChooser.open"},
        {"FileChooser.saveButtonText", "swing.fileChooser.save"},
        {"FileChooser.cancelButtonText", "ui.cancel"},
        {"FileChooser.lookInLabelText", "swing.fileChooser.lookIn"},
        {"FileChooser.fileNameLabelText", "swing.fileChooser.fileName"},
        {"FileChooser.filesOfTypeLabelText", "swing.fileChooser.filesOfType"}};

    /** And the words a file chooser shows only when it makes or renames a folder, with the name it gives a new one. */
    private static final String[][] RARER = {
        {"FileChooser.win32.newFolder", "swing.fileChooser.newFolderName"},
        {"FileChooser.win32.newFolder.subsequent", "swing.fileChooser.newFolderNameNext"},
        {"FileChooser.other.newFolder", "swing.fileChooser.newFolderName"},
        {"FileChooser.other.newFolder.subsequent", "swing.fileChooser.newFolderNameNext"},
        {"FileChooser.newFolderErrorText", "swing.fileChooser.newFolderError"},
        {"FileChooser.newFolderParentDoesntExistTitleText", "swing.fileChooser.newFolderNoParentTitle"},
        {"FileChooser.newFolderParentDoesntExistText", "swing.fileChooser.newFolderNoParent"},
        {"FileChooser.renameErrorTitleText", "swing.fileChooser.renameErrorTitle"},
        {"FileChooser.renameErrorText", "swing.fileChooser.renameError"},
        {"FileChooser.renameErrorFileExistsText", "swing.fileChooser.renameErrorExists"}};

    /**
     * In Danish, which Swing has no words for, a popup and a file chooser say TrainControl's Danish whatever language the
     * computer's is.
     *
     * MUTATION: leave any of these words to Swing, and this fails.
     *
     * @throws Exception from the look and feel
     */
    @Test
    public void testAPopupSpeaksDanishWhereSwingCannot() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("a look and feel needs a display");

        TrainControlUI.installLookAndFeel();

        Locale was = I18n.getLocale();

        try
        {
            I18n.setLocale(new Locale("da"));

            assertEquals(I18n.t("swing.optionPane.inputTitle"), "Indtastning", "precondition: the Danish messages are not"
                + " the ones on show");

            speak();

            for (String[] word : SHOWN)
            {
                // ASKED IN ENGLISH, as a popup on an English computer asks
                assertEquals(UIManager.getString(word[0], Locale.ENGLISH), I18n.t(word[1]), word[0] + " is Swing's own"
                    + " word rather than TrainControl's Danish (OB-318)");
            }
        }
        finally
        {
            I18n.setLocale(was);

            speak();
        }
    }

    /**
     * The words are put as the look and feel is installed: a word whose English Swing says otherwise - the file chooser's
     * Open tooltip - is TrainControl's after the install.
     *
     * MUTATION: install the look and feel without putting the words, and this fails.
     *
     * @throws Exception from the look and feel
     */
    @Test
    public void testTheInstallPutsTheWords() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("a look and feel needs a display");

        TrainControlUI.installLookAndFeel();

        assertNotEquals(I18n.t("swing.fileChooser.openTip"), "Open selected file", "precondition: TrainControl's words"
            + " for the tooltip are Swing's, so this cannot tell whose are on show");

        assertEquals(UIManager.getString("FileChooser.openButtonToolTipText", I18n.getLocale()),
            I18n.t("swing.fileChooser.openTip"), "after the look and feel is installed, the file chooser's Open tooltip is"
            + " Swing's own (OB-318)");
    }

    /**
     * The words a file chooser shows only when it makes or renames a folder are TrainControl's too (RSA37-C3): the name a
     * new folder is given, "New Folder" and "New Folder (2)", and the new-folder and rename errors - which Swing has in
     * English only, in a Danish, Dutch or Polish window.  The patterns Swing fills in keep their place for the number and
     * the name.
     *
     * MUTATION: leave any of these words to Swing, and this fails.
     *
     * @throws Exception from the look and feel
     */
    @Test
    public void testTheChoosersRarerWordsAreOurs() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("a look and feel needs a display");

        TrainControlUI.installLookAndFeel();

        Locale was = I18n.getLocale();

        try
        {
            I18n.setLocale(new Locale("da"));

            speak();

            for (String[] word : RARER)
            {
                assertEquals(UIManager.getString(word[0], Locale.ENGLISH), I18n.t(word[1]), word[0] + " is Swing's own"
                    + " word rather than TrainControl's Danish (RSA37-C3)");
            }

            assertEquals(java.text.MessageFormat.format(UIManager.getString("FileChooser.win32.newFolder.subsequent"), "2"),
                "Ny mappe (2)", "a second new folder is not numbered");

            assertEquals(java.text.MessageFormat.format(UIManager.getString("FileChooser.renameErrorFileExistsText"),
                "spor.json").indexOf("spor.json"), 0, "the rename error does not name the file");
        }
        finally
        {
            I18n.setLocale(was);

            speak();
        }
    }

    /**
     * Every file chooser TrainControl makes takes its Look In list from the shell's answer asked once ahead of time, not
     * asked again as it is built and at every change of folder (OB-323; Adam, 2026-10-05: *"there is an odd (brief but
     * noticeable) delay before the file chooser opens for Layouts -> Open Layout..."*) - 1.0 to 1.8 s each on Adam's PC,
     * 20 ms after.  And the list is still the shell's: every place Windows gives - Recent Items, Desktop, Documents, This
     * PC and its drives, Network - and the folder the chooser is in (*"we don't want to sacrifice UX"*).  Asked of Swing's
     * own test, `FilePane.usesShellFolder`, of the list, and of the source, which makes every chooser a
     * `QuickFileChooser`.
     *
     * MUTATION: let the chooser ask the shell itself again, leave the shell's places out of its list, or make one chooser a
     * plain JFileChooser, and this fails.
     *
     * @throws Exception from the source tree
     */
    @Test
    public void testEveryFileChooserSkipsTheShellsFolders() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("a look and feel needs a display");

        TrainControlUI.installLookAndFeel();

        final javax.swing.JFileChooser[] made = new javax.swing.JFileChooser[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> made[0] = new org.traincontrol.gui.QuickFileChooser());

        java.lang.reflect.Method usesShellFolder = Class.forName("sun.swing.FilePane")
            .getMethod("usesShellFolder", javax.swing.JFileChooser.class);

        assertEquals(usesShellFolder.invoke(null, made[0]), Boolean.FALSE, "TrainControl's file chooser asks the shell for"
            + " its Look In list itself, which costs a second or more each time it is built or changes folder (OB-323)");

        // THE LIST IS THE SHELL'S, with the folder it is in
        Object shell = Class.forName("sun.awt.shell.ShellFolder").getMethod("get", String.class)
            .invoke(null, "fileChooserComboBoxFolders");

        if (!(shell instanceof java.io.File[]))
        {
            throw new SkipException("the shell gives no Look In list here, so there is nothing to compare it with");
        }

        final java.io.File here = new java.io.File("src").getAbsoluteFile();

        final java.util.List<String> listed = new java.util.ArrayList<>();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JFileChooser chooser = new org.traincontrol.gui.QuickFileChooser(here.getPath());

            javax.swing.JComboBox<?> lookIn = lookInOf(chooser);

            for (int i = 0; lookIn != null && i < lookIn.getItemCount(); i++)
            {
                listed.add(chooser.getFileSystemView().getSystemDisplayName((java.io.File) lookIn.getItemAt(i)));
            }
        });

        java.util.List<String> missing = new java.util.ArrayList<>();

        for (java.io.File place : (java.io.File[]) shell)
        {
            String name = javax.swing.filechooser.FileSystemView.getFileSystemView().getSystemDisplayName(place);

            if (!listed.contains(name)) missing.add(name);
        }

        assertEquals(missing.toString(), "[]", "the chooser's Look In list leaves out places Windows gives (OB-323): "
            + listed);

        assertTrue(listed.contains(here.getName()), "the chooser's Look In list does not name the folder it is in: "
            + listed);

        // AND EVERY CHOOSER IS ONE
        java.util.regex.Pattern plain = java.util.regex.Pattern.compile("new\\s+(?:javax\\.swing\\.)?JFileChooser\\s*\\(");

        java.util.List<String> found = new java.util.ArrayList<>();

        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(java.nio.file.Paths.get("src")))
        {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files::iterator)
            {
                if (!file.toString().endsWith(".java") || file.endsWith("QuickFileChooser.java")) continue;

                String text = new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);

                if (plain.matcher(text).find()) found.add(file.getFileName().toString());
            }
        }

        assertEquals(found.toString(), "[]", "a file chooser made as a plain JFileChooser lists Windows' shell folders"
            + " (OB-323): " + found);
    }

    /**
     * A shortcut to a folder opens the folder, in a TrainControl file chooser as in Swing's own (RSA38-B2): its view of the
     * files is not the shell's, so Swing does not follow shortcuts for it, and the chooser was left "in" the shortcut,
     * still listing the folder before, with an InternalError on the event thread.
     *
     * MUTATION: set the folder as given, without following a shortcut, and this fails.
     *
     * @throws Exception from the shortcut or the chooser
     */
    @Test
    public void testAShortcutToAFolderOpensTheFolder() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("a look and feel needs a display");

        java.nio.file.Path base = java.nio.file.Files.createTempDirectory("tc-shortcut");

        try
        {
            java.nio.file.Path target = java.nio.file.Files.createDirectory(base.resolve("target"));
            java.nio.file.Path link = base.resolve("to target.lnk");

            // A WINDOWS SHORTCUT, made as Windows makes one
            Process made = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                "$s = (New-Object -ComObject WScript.Shell).CreateShortcut('" + link.toString().replace("'", "''") + "');"
                + " $s.TargetPath = '" + target.toString().replace("'", "''") + "'; $s.Save()")
                .redirectErrorStream(true).start();

            made.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);

            if (!java.nio.file.Files.isRegularFile(link)) throw new SkipException("no Windows shortcut could be made here");

            TrainControlUI.installLookAndFeel();

            final java.io.File[] landed = new java.io.File[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                javax.swing.JFileChooser chooser = new org.traincontrol.gui.QuickFileChooser(base.toString());

                // as a double-click on it does
                chooser.setCurrentDirectory(link.toFile());

                landed[0] = chooser.getCurrentDirectory();
            });

            assertEquals(new java.io.File(landed[0].getPath()).getCanonicalFile(), target.toFile().getCanonicalFile(), "a"
                + " shortcut to a folder did not open the folder in TrainControl's file chooser (RSA38-B2)");
        }
        finally
        {
            try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(base))
            {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /**
     * The details view shows the shell's columns for the shell's values, as Swing's own chooser does (RSA38-C1): the
     * chooser's folder was a plain one, so its details view had Swing's three headings - name, size, modified - over the
     * shell's values, the item type under "Modified" and no dates.
     *
     * MUTATION: leave the chooser's folder a plain one, and this fails.
     *
     * @throws Exception from the chooser
     */
    @Test
    public void testTheDetailsViewHasTheShellsColumns() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("a look and feel needs a display");

        TrainControlUI.installLookAndFeel();

        final String here = new java.io.File("src").getAbsolutePath();

        final java.util.List<String> quick = new java.util.ArrayList<>();
        final java.util.List<String> swing = new java.util.ArrayList<>();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            quick.addAll(detailsHeadings(new org.traincontrol.gui.QuickFileChooser(here)));
            swing.addAll(detailsHeadings(new javax.swing.JFileChooser(here)));
        });

        assertFalse(swing.isEmpty(), "precondition: Swing's own chooser shows no details view here");

        assertEquals(quick, swing, "TrainControl's file chooser's details view does not have the columns Swing's own has"
            + " (RSA38-C1)");
    }

    /**
     * The shell is asked for the Look In list once at a time and not again while the drives are the same (RSA38-C2): a
     * chooser opened while the window's own ask was under way asked again beside it, and every chooser that closed asked
     * again - a second each, which the next chooser waited behind.
     *
     * MUTATION: let a chooser ask beside an ask under way, or ask again with the drives the same, and this fails.
     *
     * @throws Exception from the chooser
     */
    @Test
    public void testTheShellIsAskedOnceAtATime() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("a look and feel needs a display");

        TrainControlUI.installLookAndFeel();

        java.lang.reflect.Field counted = org.traincontrol.gui.QuickFileChooser.class.getDeclaredField("asked");

        counted.setAccessible(true);

        // AS THE WINDOW OPENS: nothing asked yet - any ask of the choosers before this one finished first
        java.lang.reflect.Field asking = org.traincontrol.gui.QuickFileChooser.class.getDeclaredField("asking");
        java.lang.reflect.Field places = org.traincontrol.gui.QuickFileChooser.class.getDeclaredField("places");

        asking.setAccessible(true);
        places.setAccessible(true);

        Object under = asking.get(null);

        if (under != null) ((java.util.concurrent.Future<?>) under).get(60, java.util.concurrent.TimeUnit.SECONDS);

        places.set(null, null);

        int before = ((java.util.concurrent.atomic.AtomicInteger) counted.get(null)).get();

        // THE WINDOW'S OWN ASK, and a chooser at once, while it is under way - and two more after
        org.traincontrol.gui.QuickFileChooser.askTheShellAhead();

        for (int i = 0; i < 3; i++)
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> lookInOf(new org.traincontrol.gui.QuickFileChooser()));
        }

        int after = ((java.util.concurrent.atomic.AtomicInteger) counted.get(null)).get();

        assertEquals(after - before, 1, "the shell was asked for the Look In list " + (after - before) + " times for one"
            + " ask ahead and three choosers, with the drives the same (RSA38-C2)");
    }

    /**
     * The Look In list is asked of the shell again when a Desktop folder has changed, as well as when a drive has (RSA39-C1):
     * most of it is the Desktop's own folders, and a folder made on it was not followed while the drives stayed the same.
     * A folder of the test's own stands in for the Desktop, so the real one is never touched.
     *
     * MUTATION: ask again only when a drive comes or goes, and this fails.
     *
     * @throws Exception from the chooser
     */
    @Test
    public void testTheLookInListFollowsTheDesktop() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("a look and feel needs a display");

        TrainControlUI.installLookAndFeel();

        Class<?> quick = org.traincontrol.gui.QuickFileChooser.class;

        java.lang.reflect.Field desktops = quick.getDeclaredField("desktops");
        java.lang.reflect.Field asking = quick.getDeclaredField("asking");
        java.lang.reflect.Field counted = quick.getDeclaredField("asked");

        desktops.setAccessible(true);
        asking.setAccessible(true);
        counted.setAccessible(true);

        Object were = desktops.get(null);

        // AND THE REAL DESKTOP IS FOUND, which the stand-in below takes the place of (RSA40-C4)
        java.lang.reflect.Method found = quick.getDeclaredMethod("theDesktops");

        found.setAccessible(true);

        java.util.List<?> real = (java.util.List<?>) found.invoke(null);

        assertFalse(real.isEmpty(), "TrainControl's file chooser finds no Desktop folder to follow (RSA40-C4)");

        for (Object desktop : real)
        {
            assertTrue(((java.io.File) desktop).isDirectory(), "a Desktop found is no folder: " + desktop);
        }

        java.nio.file.Path standIn = java.nio.file.Files.createTempDirectory("tc-desktop");

        try
        {
            Object under = asking.get(null);

            if (under != null) ((java.util.concurrent.Future<?>) under).get(60, java.util.concurrent.TimeUnit.SECONDS);

            desktops.set(null, java.util.Collections.singletonList(standIn.toFile()));

            // ASKED WITH THE STAND-IN AS THE DESKTOP
            org.traincontrol.gui.QuickFileChooser.askTheShellAhead();

            ((java.util.concurrent.Future<?>) asking.get(null)).get(60, java.util.concurrent.TimeUnit.SECONDS);

            int before = ((java.util.concurrent.atomic.AtomicInteger) counted.get(null)).get();

            javax.swing.SwingUtilities.invokeAndWait(() -> lookInOf(new org.traincontrol.gui.QuickFileChooser()));

            assertEquals(((java.util.concurrent.atomic.AtomicInteger) counted.get(null)).get() - before, 0, "precondition:"
                + " the shell was asked again with nothing changed");

            // A FILE SAVED ON IT, which the list holds none of (RSA40-C3)
            java.nio.file.Files.write(standIn.resolve("saved.txt"), new byte[] {1});

            standIn.toFile().setLastModified(standIn.toFile().lastModified() + 10000);

            javax.swing.SwingUtilities.invokeAndWait(() -> lookInOf(new org.traincontrol.gui.QuickFileChooser()));

            assertEquals(((java.util.concurrent.atomic.AtomicInteger) counted.get(null)).get() - before, 0, "a file saved on"
                + " the Desktop had the shell asked again for a list that holds only folders (RSA40-C3)");

            // A FOLDER MADE ON IT
            java.nio.file.Files.createDirectory(standIn.resolve("made"));

            javax.swing.SwingUtilities.invokeAndWait(() -> lookInOf(new org.traincontrol.gui.QuickFileChooser()));

            assertEquals(((java.util.concurrent.atomic.AtomicInteger) counted.get(null)).get() - before, 1, "a folder made on"
                + " the Desktop did not have the shell asked again for the Look In list (RSA39-C1)");
        }
        finally
        {
            desktops.set(null, were);

            try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(standIn))
            {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** The headings of a chooser's details view, switched to it. */
    private static java.util.List<String> detailsHeadings(javax.swing.JFileChooser chooser)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        java.awt.Component pane = componentOf(chooser, "sun.swing.FilePane");

        if (pane == null) return out;

        try
        {
            pane.getClass().getMethod("setViewType", int.class).invoke(pane, 1);
        }
        catch (ReflectiveOperationException e)
        {
            return out;
        }

        javax.swing.JTable table = tableIn((java.awt.Container) pane);

        if (table == null) return out;

        javax.swing.table.TableColumnModel columns = table.getColumnModel();

        for (int i = 0; i < columns.getColumnCount(); i++) out.add(String.valueOf(columns.getColumn(i).getHeaderValue()));

        return out;
    }

    /** The first table inside a container - Swing's details view is one of its own subclasses. */
    private static javax.swing.JTable tableIn(java.awt.Container container)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JTable) return (javax.swing.JTable) child;

            if (child instanceof java.awt.Container)
            {
                javax.swing.JTable deeper = tableIn((java.awt.Container) child);

                if (deeper != null) return deeper;
            }
        }

        return null;
    }

    /** The first component of a class, by its name, inside a container. */
    private static java.awt.Component componentOf(java.awt.Container container, String className)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child.getClass().getName().equals(className)) return child;

            if (child instanceof java.awt.Container)
            {
                java.awt.Component deeper = componentOf((java.awt.Container) child, className);

                if (deeper != null) return deeper;
            }
        }

        return null;
    }

    /** A chooser's Look In list: the combo box whose items are folders. */
    private static javax.swing.JComboBox<?> lookInOf(java.awt.Container container)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JComboBox && ((javax.swing.JComboBox<?>) child).getItemCount() > 0
                && ((javax.swing.JComboBox<?>) child).getItemAt(0) instanceof java.io.File)
            {
                return (javax.swing.JComboBox<?>) child;
            }

            if (child instanceof java.awt.Container)
            {
                javax.swing.JComboBox<?> deeper = lookInOf((java.awt.Container) child);

                if (deeper != null) return deeper;
            }
        }

        return null;
    }

    /** TrainControlUI.swingSpeaksOurLanguage, which is the window's. */
    private static void speak() throws Exception
    {
        java.lang.reflect.Method speak = TrainControlUI.class.getDeclaredMethod("swingSpeaksOurLanguage");

        speak.setAccessible(true);

        speak.invoke(null);
    }
}
