package regression;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * A train is not sent by hand while the setup has errors, any more than autonomy is started (MT-263).
 *
 * Adam, 2026-09-24: *"Start autonomy doesn't run autonomy, as expected - I get an error message saying errors must
 * first be fixed.  Good.  But trains can still be moved manually via both the track diagram viewer and the autonomy
 * tab, which should throw an error instead."*  A hand send runs over the railway the same setup built, so the setup
 * that stops autonomy starting stops a hand send too: the same question, `autonomyHasErrors`, at both hand doors - the
 * diagram's right-click destinations and the Auto tab's list of paths.
 *
 * **Asked of the words, then of the doors**, as `testTheRefusalToStartSaysWhichThing` asks Start's: a behavioural
 * claim would have to raise the modal dialog each door shows, which is how a runner was stranded on 2026-09-09.
 *
 * MUTATION: take the refusal out of either door, or ask it after the reversal question, and the second claim fails
 * naming the door; make the rule answer nothing for a broken setup and the first does.
 *
 * @author Adam
 */
public class testAHandSendIsRefusedWhileTheSetupIsBroken
{
    private static final String REFUSAL = "whyAHandSendIsRefused()";

    /**
     * The setup's own words, with however many things there are to deal with - and never autonomy's.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testTheRefusalSaysWhatIsWrongWithTheSetup() throws Exception
    {
        assertEquals(said(2, 0), I18n.f("autosetup.ui.errorCannotBuildDetail", 2),
            "with two error findings a hand send is not refused naming the two things to deal with");

        assertEquals(said(0, 3), I18n.f("autosetup.ui.errorCannotBuildDetail", 3),
            "with three problems that stop the setup being built, a hand send does not name the three");

        assertEquals(said(0, 1), I18n.t("autosetup.ui.errorCannotBuildDetailOne"),
            "one problem is not named in the singular");

        // A SETUP THAT WILL NOT BUILD WITH NO FINDING MADE OF IT is still refused, and in words: `hasErrors()` is wider
        // than either count, which is MT-263's own first finding.
        assertEquals(said(0, 0), I18n.t("autosetup.ui.errorCannotBuildDetailOne"),
            "a broken setup with neither count above zero is not refused in the setup's words");

        for (int errors = 0; errors < 3; errors++)
        {
            String words = said(errors, 0);

            assertNotEquals(words, I18n.f("autolayout.ui.errorCannotStartWithErrors", errors),
                "a hand send is refused in Start's words, which say AUTONOMY cannot start - not what was pressed");

            assertNotEquals(words, I18n.t("autolayout.errorUnableToStartAutonomyWaitForTrains"),
                "a hand send over a broken setup is told to wait for the trains");
        }
    }

    /**
     * Both hand doors ask it, before the reversal question and before anything is dispatched.
     *
     * @throws Exception from the files
     */
    @Test
    public void testBothHandDoorsAskItFirst() throws Exception
    {
        door("src/org/traincontrol/gui/AutoLocomotiveStatus.java", "private void locAvailPathsMouseClicked(",
            "the Auto tab's list of paths");

        door("src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java", "private JMenuItem destinationItem(",
            "the track diagram's right-click destinations");

        String window = read("src/org/traincontrol/gui/TrainControlUI.java");

        int rule = window.indexOf("public String " + REFUSAL);

        assertTrue(rule >= 0, "the window has no " + REFUSAL + " for the doors to ask");

        String body = window.substring(rule, window.indexOf("\n    }", rule));

        assertTrue(body.contains("autonomyHasErrors()"), REFUSAL + " does not ask autonomyHasErrors(), the question"
            + " Start is refused on - so a hand send and Start can disagree about the same setup: " + body);
    }

    private static void door(String file, String handler, String what) throws Exception
    {
        String source = read(file);

        int start = source.indexOf(handler);

        assertTrue(start >= 0, "cannot find " + handler + " in " + file + " - if the door moved, move this");

        int end = source.indexOf("\n    }", start);

        String body = source.substring(start, end < 0 ? source.length() : end);

        int asked = body.indexOf(REFUSAL);

        assertTrue(asked >= 0, what + " sends a train without asking " + REFUSAL + " - MT-263, \"trains can still be"
            + " moved manually ... which should throw an error instead\"");

        int question = body.indexOf("ManualReversalPrompt.forJourney(");
        int dispatch = body.indexOf("executePath(");

        assertTrue(question < 0 || asked < question, what + " asks which way the train should face before it"
            + " refuses the send - a question about a journey that is going to be refused reads as answered");

        assertTrue(dispatch >= 0 && asked < dispatch, what + " dispatches before it asks " + REFUSAL);
    }

    private static String said(int errors, int blocking) throws Exception
    {
        Method rule = TrainControlUI.class.getDeclaredMethod("whyAHandSendIsRefused", int.class, int.class);

        rule.setAccessible(true);

        return (String) rule.invoke(null, errors, blocking);
    }

    private static String read(String path) throws Exception
    {
        File f = new File(path);

        assertTrue(f.isFile(), "cannot find " + f.getAbsolutePath());

        String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);

        return raw.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }
}
