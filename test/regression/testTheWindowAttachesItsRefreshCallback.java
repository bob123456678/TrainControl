package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * The window still attaches the callback that redraws the timetable and the locomotive status panel.
 *
 * This is the only shape of test that would have caught the bug it exists for, and it is worth being
 * clear about why. The callback mechanism was never broken. `testTimetableCaptureThroughARealRun`
 * attaches one itself and proves that a real autonomy run fires it - and that test would have passed
 * on every build in which the operator saw nothing, because what had gone was not the mechanism but
 * the CALLER.
 *
 * `d8db4879` deleted the GraphStream graph window. The four lines that registered this callback were
 * inside the method that built it, because that window wanted the notification too and registered it
 * on everyone's behalf. Deleting the window deleted them. Nothing failed to compile, no test went red,
 * and two panels simply stopped being true - the timetable, which Adam reported as capture not working
 * at all, and the locomotive status panel, which he reported separately three days later as OB-097.
 *
 * A behavioural test cannot see that: it can always attach the callback itself, and then it is testing
 * its own wiring. So this reads the source instead and insists the window does it. Crude, and the same
 * device `testStoreCollectionsAreHandledEverywhere` and `testEditorSurfaceRules` use, for the same
 * reason - some invariants are about what the code SAYS, and a deleted call site is one of them.
 */
public class testTheWindowAttachesItsRefreshCallback
{
    @Test
    public void testTrainControlUIAttachesTheRefreshCallback() throws Exception
    {
        File ui = new File("src/org/traincontrol/gui/TrainControlUI.java");

        assertTrue(ui.exists(), "cannot find " + ui.getAbsolutePath()
            + " - this test reads the source, so it has to run from the project root");

        String source = new String(Files.readAllBytes(ui.toPath()), StandardCharsets.UTF_8);

        assertTrue(source.contains("AutonomyRefreshCallback.attach"),
            "TrainControlUI no longer attaches AutonomyRefreshCallback.  The timetable and the "
            + "locomotive status panel redraw ONLY when the layout announces a path start or end, so "
            + "without this they show whatever they last showed: entries are captured into a table "
            + "that is never repainted, and a finished route goes on reading as active.  That is "
            + "exactly what happened when the GraphStream window was deleted in d8db4879, and both "
            + "halves were reported as bugs before anyone found the cause");

        // On every layout the model builds, not once.  parseAuto replaces the Layout object and
        // callbacks live on the object, so a single attachment at start-up would be lost by the first
        // configuration load - which is the state this would be in if somebody "tidied" the second
        // call away as a duplicate.
        int attached = source.split("attachAutonomyRefresh\\(", -1).length - 1;

        assertTrue(attached >= 3,
            "attachAutonomyRefresh appears " + attached + " times: it should be declared once and "
            + "called after EVERY parseAuto, of which there are two.  Callbacks live on the Layout "
            + "object and parseAuto replaces that object, so an attachment that does not follow it is "
            + "an attachment to a layout nobody is running");
    }
}
