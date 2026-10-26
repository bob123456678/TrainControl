package regression;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import support.SourceOf;

/**
 * The window still attaches the callback that redraws the timetable and the locomotive status panel.
 *
 * This is the only shape of test that would have caught the bug it exists for.  The callback mechanism was never broken
 * (`testTimetableCaptureThroughARealRun` attaches one itself and proves a real autonomy run fires it); what had gone
 * was the CALLER.  Deleting the GraphStream graph window deleted the four lines that registered this callback on
 * everyone's behalf; nothing failed to compile, no test went red, and two panels stopped being true: the timetable
 * (Adam: capture not working at all) and the locomotive status panel (OB-097).
 *
 * A behavioral test can always attach the callback itself and then tests its own wiring, so this reads the source and
 * insists the window does it: the same device `testStoreCollectionsAreHandledEverywhere` and `testEditorSurfaceRules`
 * use, since some invariants are about what the code SAYS.
 */
public class testTheWindowAttachesItsRefreshCallback
{
    @Test
    public void testTrainControlUIAttachesTheRefreshCallback() throws Exception
    {
        // The program's code, comments blanked, wherever the window keeps these calls.
        String source = SourceOf.allCode();

        // IN THE CODE, NOT THE RAW SOURCE: a method name that also appears in the comment explaining the call would
        // leave the test green when the call is deleted.
        assertTrue(source.contains("AutonomyRefreshCallback.attach"),
            "TrainControlUI no longer attaches AutonomyRefreshCallback.  The timetable and the "
            + "locomotive status panel redraw ONLY when the layout announces a path start or end, so "
            + "without this they show whatever they last showed: entries are captured into a table "
            + "that is never repainted, and a finished route goes on reading as active.  That is "
            + "exactly what happened when the GraphStream window was deleted in d8db4879, and both "
            + "halves were reported as bugs before anyone found the cause");

        // On every layout the model builds, not once: parseAuto replaces the Layout object and callbacks live on the
        // object, so a single attachment at start-up would be lost by the first configuration load.  Counted in the
        // CODE, not the raw source, for the reason above: delete a call, keep a comment mentioning
        // attachAutonomyRefresh(, stay green.
        int attached = SourceOf.occurrences("attachAutonomyRefresh(");

        // A locomotive rename repairs the setup and must then say so (MT-153).  repairAutonomyLocomotive rewrites the
        // placements, homes and exclusions that name the locomotive, but the station labels and the locomotive panel
        // are written by methods that only run when something calls them.  Adam: "I renamed MY 1106 to MY Y1106. It
        // vanished from autonomy, with MY 1106 still placed and at location ???? in the UI."  The method itself,
        // comments blanked.
        String body = SourceOf.withoutComments(SourceOf.declaration("    void repairAutonomyLocomotive("));

        // The CALL, with its parentheses, comments stripped first: a body that merely mentions "updateVisiblePoints" in
        // a comment would satisfy a bare `contains`.
        assertTrue(body.contains("updateVisiblePoints()"),
            "repairing a renamed locomotive no longer refreshes the station labels, so the diagram "
            + "will go on showing the old name over a locomotive that no longer answers to it "
            + "(MT-153).  The data repair alone is not the fix - it was never the broken half");

        // HOW MANY LOADS, COUNTED rather than pinned (OB-254 removed one door): a pinned count fails a right window or
        // passes one that later gains a door and no call.  Every call of the model's parseAuto in the program (a call
        // has a receiver; the declarations do not).
        int doors = SourceOf.occurrences(".parseAuto(");

        assertTrue(doors >= 1, "no window code calls parseAuto - if it was renamed, rename it here, or this"
            + " counts nothing and asks nothing");

        assertTrue(attached >= 1 + doors,
            "attachAutonomyRefresh appears " + attached + " times: it should be declared once and "
            + "called after EVERY parseAuto, of which there are " + doors + ".  Callbacks live on the Layout "
            + "object and parseAuto replaces that object, so an attachment that does not follow it is "
            + "an attachment to a layout nobody is running");
    }
}
