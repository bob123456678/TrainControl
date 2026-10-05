package regression;

import java.util.Locale;
import javax.swing.UIManager;
import static org.testng.Assert.assertEquals;
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
