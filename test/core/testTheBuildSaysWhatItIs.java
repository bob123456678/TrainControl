package core;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A pre-release build says which build it is.
 *
 * Adam: "add a flag after RAW_VERSION that says IS_PRE_RELEASE True/False.  If true, put the build ID in the log at
 * startup and main UI title."  Every release candidate calls itself 3.0.0, so a tester reporting a result against one
 * cannot say which, and an MT-273 result carried a provenance line naming a commit three days older than the code
 * actually run.
 *
 * @author Adam
 */
public class testTheBuildSaysWhatItIs
{
    /**
     * The build id describes the artifact being run, not what a build script recorded about it: taken from the code
     * source this class was loaded from (the jar's timestamp or, in an IDE run, the class file's).  The misleading
     * MT-273 stamp was a commit id, which describes an input rather than the thing that ran.
     */
    @Test
    public void testTheBuildIdIsReadFromWhatIsRunning()
    {
        String id = MarklinControlStation.buildId();

        assertNotNull(id,
            "no build id could be established while running from the test harness, which is the same "
            + "kind of classpath a jar run uses - if this cannot answer here it will not answer for a "
            + "tester either, and the flag buys nothing");

        // The shape, not the value: the value is whenever this machine last compiled.
        assertTrue(id.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"),
            "the build id is not a timestamp a person can compare against \"when did I build this\": "
            + id);
    }

    /**
     * The displayed version carries the build while this is a pre-release, and is bare once it is not: the flag has to
     * actually gate something.
     */
    @Test
    public void testTheDisplayedVersionFollowsTheFlag()
    {
        final String shown = MarklinControlStation.versionForDisplay();

        assertTrue(shown.startsWith(MarklinControlStation.RAW_VERSION),
            "the version a person sees no longer begins with the version number: " + shown);

        if (MarklinControlStation.IS_PRE_RELEASE)
        {
            assertTrue(shown.contains("pre-release"),
                "IS_PRE_RELEASE is true and the version does not say so, which is the one thing the "
                + "flag exists to publish: " + shown);

            assertTrue(shown.contains(MarklinControlStation.buildId()),
                "IS_PRE_RELEASE is true and the version does not carry the build id, so two release "
                + "candidates still describe themselves identically: " + shown);
        }
        else
        {
            // THE OTHER HALF, and the one that matters on release day.
            assertEquals(shown, MarklinControlStation.RAW_VERSION,
                "IS_PRE_RELEASE is false and the version still carries a build stamp, so a shipped "
                + "release tells every user when it was compiled: " + shown);
        }
    }
}
