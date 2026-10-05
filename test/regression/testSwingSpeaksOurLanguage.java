package regression;

import java.util.Locale;
import javax.swing.UIManager;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
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

    /** TrainControlUI.swingSpeaksOurLanguage, which is the window's. */
    private static void speak() throws Exception
    {
        java.lang.reflect.Method speak = TrainControlUI.class.getDeclaredMethod("swingSpeaksOurLanguage");

        speak.setAccessible(true);

        speak.invoke(null);
    }
}
