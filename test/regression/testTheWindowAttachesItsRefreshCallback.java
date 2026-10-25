package regression;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import support.SourceOf;

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
 * A behavioral test cannot see that: it can always attach the callback itself, and then it is testing
 * its own wiring. So this reads the source instead and insists the window does it. Crude, and the same
 * device `testStoreCollectionsAreHandledEverywhere` and `testEditorSurfaceRules` use, for the same
 * reason - some invariants are about what the code SAYS, and a deleted call site is one of them.
 */
public class testTheWindowAttachesItsRefreshCallback
{
    @Test
    public void testTrainControlUIAttachesTheRefreshCallback() throws Exception
    {
        // The program's code, comments blanked, wherever the window keeps these calls.
        String source = SourceOf.allCode();

        // IN THE CODE, NOT THE RAW SOURCE.
        //
        // This was `source.contains(...)`, and the sibling assertion twenty lines below was hardened
        // against exactly that and explains why at length - it searched for a method name that also
        // appeared in the comment explaining why the call was there, so deleting the call left it
        // green. That hardening was applied to the count and not to this, which guards the more
        // important half: whether the window attaches the callback at all.
        //
        // It passed by luck, and the luck was checked: the name appears once in that file, at the call.
        // The first comment anybody writes mentioning it would have reopened the hole.
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
        // Counted in the CODE, not the raw source.
        //
        // The other assertion in this test was hardened against exactly this - it searched for a
        // method name that also appeared in the comment explaining why the call was there, so
        // deleting the call left it green - and this one was left counting raw text. It happens to
        // guard today, because the three occurrences are the declaration and its two calls. The first
        // comment anybody writes mentioning attachAutonomyRefresh( reopens the hole this test's own
        // javadoc warns about: delete a call, keep the comment, stay green.
        int attached = SourceOf.occurrences("attachAutonomyRefresh(");

        // A locomotive rename repairs the setup and must then say so (MT-153).
        //
        // Same rule, second site. repairAutonomyLocomotive rewrites the placements, homes and
        // exclusions that name the locomotive and saves them, and the data comes out right - but the
        // station labels and the locomotive panel are written by methods that only run when something
        // calls them, so the window went on naming a locomotive that no longer exists. Adam: "I
        // renamed MY 1106 to MY Y1106. It vanished from autonomy, with MY 1106 still placed and at
        // location ???? in the UI."
        // The method itself, comments blanked.
        String body = SourceOf.withoutComments(SourceOf.declaration("private void repairAutonomyLocomotive("));

        // The CALL, with its parentheses, and with the comments stripped first.
        //
        // The first version of this asked whether the method body contained the string
        // "updateVisiblePoints" anywhere. It did - in the comment I had just written explaining why
        // the call was there - so deleting the call left the test green. An assertion that its own
        // documentation satisfies is not an assertion, and this one was caught only because it was
        // mutation-checked before being trusted.
        assertTrue(body.contains("updateVisiblePoints()"),
            "repairing a renamed locomotive no longer refreshes the station labels, so the diagram "
            + "will go on showing the old name over a locomotive that no longer answers to it "
            + "(MT-153).  The data repair alone is not the fix - it was never the broken half");

        // HOW MANY LOADS, COUNTED rather than pinned.  There were two doors into parseAuto until the
        // old JSON tab's Validate button went with its tab (OB-254), and a pinned two then failed on a
        // window that was right; a pinned one would pass a window that later gains a door and no call.
        // Every call of the model's parseAuto in the program (a call has a receiver; the declarations do not).
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
