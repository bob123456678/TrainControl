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

    /** TrainControlUI.swingSpeaksOurLanguage, which is the window's. */
    private static void speak() throws Exception
    {
        java.lang.reflect.Method speak = TrainControlUI.class.getDeclaredMethod("swingSpeaksOurLanguage");

        speak.setAccessible(true);

        speak.invoke(null);
    }
}
