package regression;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.AutonomyOverlayToggle;
import org.traincontrol.util.I18n;

/**
 * The words say what is there, in all eight languages (Adam, 2026-10-02: "Fix defualt and the start says nothing, plus the
 * other wording issues"): a count agrees with its number, and no message names a button, a key or a step that is not
 * where it is read.
 *
 * @author Adam
 */
public class testTheWordingSaysWhatIsThere
{
    static final String[] LANGS = {"", "_da", "_de", "_es", "_fr", "_it", "_nl", "_pl"};

    /** The word each language's One-Way Run message used for the button it does not have. */
    static final String[] BUTTON = {"button", "knappen", "Schaltfl", "bot", "bouton", "pulsante", "knop", "przycisk"};

    static Properties bundle(String lang) throws IOException
    {
        Properties p = new Properties();

        try (InputStream in = new FileInputStream("src/org/traincontrol/resources/messages" + lang + ".properties"))
        {
            p.load(in);
        }

        return p;
    }

    /**
     * One is counted as one: "1 thing must be fixed", "1 warning", "1 error" - they read "1 things", "1 warnings".
     *
     * MUTATION: count one with the plural, and this fails.
     */
    @Test
    public void testOneIsCountedAsOne()
    {
        assertEquals(AutonomyEditorPanel.blockingCountText(1), I18n.t("autosetup.ui.labelBlockingCountOne"),
            "one thing to fix is counted with the plural");
        assertEquals(AutonomyEditorPanel.blockingCountText(3), I18n.f("autosetup.ui.labelBlockingCount", 3),
            "precondition: three things to fix are not counted with the plural");

        // WHOLE STRINGS: "1 warnings" begins with "1 warning", so a prefix could not tell them apart
        assertEquals(AutonomyOverlayToggle.findingsCountText(0, 1, 1), I18n.f("autosetup.ui.labelFindingsCount",
            I18n.t("autosetup.ui.countWarningsOne"), I18n.f("autosetup.ui.countOnThisPage", 1)),
            "one warning is counted with the plural");
        assertEquals(AutonomyOverlayToggle.findingsCountText(1, 2, 3), I18n.f("autosetup.ui.labelFindingsCountErrors",
            I18n.t("autosetup.ui.countErrorsOne"), I18n.f("autosetup.ui.countWarnings", 2),
            I18n.f("autosetup.ui.countOnThisPage", 3)), "one error is counted with the plural");
        assertEquals(AutonomyOverlayToggle.findingsCountText(2, 1, 0), I18n.f("autosetup.ui.labelFindingsCountErrors",
            I18n.f("autosetup.ui.countErrors", 2), I18n.t("autosetup.ui.countWarningsOne"),
            I18n.f("autosetup.ui.countOnThisPage", 0)), "one warning beside two errors is counted with the plural");
    }

    /**
     * No message names what is not there: One-Way Run's button, the Edit button the left-out page's label is clicked
     * instead of, a step of pairing a link that is now an item of its own, a key in the longest train's tooltip where the
     * diagram's menu shows it, and "defualt".
     *
     * MUTATION: put any of them back in any language, and this fails.
     *
     * @throws IOException reading a bundle
     */
    @Test
    public void testNoMessageNamesWhatIsNotThere() throws IOException
    {
        for (int i = 0; i < LANGS.length; i++)
        {
            Properties p = bundle(LANGS[i]);
            String which = "messages" + LANGS[i];

            for (String key : p.stringPropertyNames())
            {
                assertFalse(p.getProperty(key).contains("defualt"), which + " " + key + " says defualt");
            }

            for (String key : new String[] {"autosetup.ui.labelBlockingCountOne", "autosetup.ui.countErrors",
                "autosetup.ui.countErrorsOne", "autosetup.ui.countWarnings", "autosetup.ui.countWarningsOne",
                "autosetup.ui.countOnThisPage", "autosetup.ui.tooltipMaxTrainLengthKey", "autolayout.ui.errorNothingStarted"})
            {
                assertNotNull(p.getProperty(key), which + " has no " + key);
            }

            assertFalse(p.getProperty("autosetup.ui.oneWayDoneAgain").contains(BUTTON[i]), which + ": One-Way Run says to"
                + " press a button it does not have: " + p.getProperty("autosetup.ui.oneWayDoneAgain"));

            assertFalse(p.getProperty("autosetup.ui.labelPageLeftOut").contains(p.getProperty("ui.main.editLayout")),
                which + ": the left-out page's label says to click Edit, where it is the label that is clicked: "
                + p.getProperty("autosetup.ui.labelPageLeftOut"));

            assertTrue(p.getProperty("autosetup.ui.promptLinkName").trim().endsWith("?"), which + ": the link's name"
                + " prompt still says how to pair it, which is an item of its own: "
                + p.getProperty("autosetup.ui.promptLinkName"));

            assertFalse(p.getProperty("autosetup.ui.tooltipMaxTrainLength").contains("Control+"), which + ": the longest"
                + " train's tooltip names its key in itself, so the diagram's menu, where the key does not work, shows it");
            assertTrue(p.getProperty("autosetup.ui.tooltipMaxTrainLengthKey").contains("Control+B"), which
                + ": the longest train's key is not said for the editor");
        }
    }
}
