package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import support.SourceOf;

/**
 * A refresh arrives all at once rather than in installments.
 *
 * Adam: "Can we get the diagrams to re-render with less flickering?  Same goes for the main UI when the hourglass
 * expires - it seems that some components appear faster than others.  Best to do a single pass."
 *
 * Two causes, neither slowness.  The diagram: `LayoutGrid` emptied the panel at the top of the build and put the
 * replacement in at the bottom, after every tile, image decode and caption; decoding pumps the event queue, so paints
 * landed on a panel with nothing in it.  The main window: several refreshes in a row, each posting its own
 * `invokeLater`, are several events, and Swing lays out and paints between events, so the route list, the locomotive
 * list and the menus came back in three visible steps.
 *
 * Checked differently on purpose: `singlePass` has a behavior that can be run, so it is run; the `LayoutGrid` ordering
 * has no observable behavior short of catching a paint mid-rebuild, which is a race, so it is checked as the ordering
 * it is.
 *
 * @author Adam
 */
public class testTheRebuildIsOnePass
{
    /**
     * `singlePass` runs inline when there is already a pass to join.
     *
     * `invokeLater` posts unconditionally, so a caller ALREADY on the event thread cannot collect several refreshes
     * into one pass by wrapping them: the wrapper is one event and each refresh inside it asks for another.
     *
     * MUTATION: change `singlePass` back to an unconditional `invokeLater` and the inner counter is
     * still zero when the outer call returns.
     */
    @Test
    public void testAPassAlreadyRunningIsJoined() throws Exception
    {
        final AtomicInteger ran = new AtomicInteger();
        final int[] seenByTheOuterCall = {-1};

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            TrainControlUI.singlePass(ran::incrementAndGet);

            // Read the moment the call returns, not afterwards: the question is whether the work is
            // done BY then, which is what "one pass" means.
            seenByTheOuterCall[0] = ran.get();
        });

        assertEquals(seenByTheOuterCall[0], 1,
            "singlePass posted a new event instead of joining the pass it was already on, so a caller "
            + "that wraps several refreshes still gets one event per refresh - and the window is laid "
            + "out and painted between each of them, which is the staggered arrival being fixed");
    }

    /**
     * Several refreshes wrapped in one `singlePass` all happen inside that one pass.
     *
     * The property the fix actually delivers, stated as the caller sees it: `doSync` wraps the route
     * list, the locomotive list and the two menu re-enables, and all four have to land together.
     */
    @Test
    public void testWrappedRefreshesShareThePass() throws Exception
    {
        final StringBuilder order = new StringBuilder();

        javax.swing.SwingUtilities.invokeAndWait(() ->
            TrainControlUI.singlePass(() ->
            {
                TrainControlUI.singlePass(() -> order.append("a"));
                TrainControlUI.singlePass(() -> order.append("b"));

                order.append("|");
            }));

        assertEquals(order.toString(), "ab|",
            "the two inner refreshes did not run inside the pass that wrapped them - they were "
            + "deferred past it, which is exactly the installment-at-a-time arrival the wrapper "
            + "exists to prevent");
    }

    /**
     * Off the event thread, `singlePass` still marshals rather than running where it was called.
     *
     * The other half, and the one that matters for safety: `doSync` calls it from a worker thread, and Swing components
     * must not be touched there.
     */
    @Test
    public void testOffTheEventThreadItStillMarshals() throws Exception
    {
        final AtomicInteger ran = new AtomicInteger();
        final boolean[] onTheEventThread = {false};

        assertFalse(javax.swing.SwingUtilities.isEventDispatchThread(),
            "precondition: this test has to be off the event thread, or it asks nothing");

        TrainControlUI.singlePass(() ->
        {
            onTheEventThread[0] = javax.swing.SwingUtilities.isEventDispatchThread();
            ran.incrementAndGet();
        });

        final int immediately = ran.get();

        // Drain the queue so the body has certainly run by the time it is asked about.
        javax.swing.SwingUtilities.invokeAndWait(() -> {});

        assertEquals(immediately, 0,
            "singlePass ran the body on the calling thread, which is not the event thread - Swing "
            + "components would be touched off it");

        assertEquals(ran.get(), 1, "the body never ran at all");

        assertTrue(onTheEventThread[0], "the body ran, but not on the event thread");
    }

    /**
     * The diagram panel is not emptied until its replacement is ready.
     *
     * `LayoutGrid` builds the whole grid between emptying the panel and adding the new one; emptying FIRST leaves the
     * panel with no children for the entire build, and image decoding inside it pumps the event queue, so paints draw a
     * blank page.  Checked as ordering rather than by catching a paint, which varies with disk and decode speed and
     * would report "fixed" on a bad day: that the panel is never empty while the new grid is built is fully determined
     * by where these two lines sit.
     *
     * MUTATION: move `parent.removeAll()` back above the build and this fails.
     */
    @Test
    public void testTheOldDiagramStaysUpUntilTheNewOneIsReady() throws Exception
    {
        // The statements are looked for in the whole program's code, comments blanked (their line breaks kept), so
        // a statement in a comment is not one.
        final String code = SourceOf.allCode();

        int emptied = code.indexOf("parent.removeAll();");
        int added = code.indexOf("parent.add(container);");

        // COUNTED, not just located: with assignments the LAST match won, so a build that empties the panel at the top
        // AND swaps at the bottom looked clean with the defect fully present.
        int emptiedCount = SourceOf.occurrences("parent.removeAll();");
        int addedCount = SourceOf.occurrences("parent.add(container);");

        // AND THE PANEL'S OTHER NAME: the constructor calls the panel `parent` and the field keeps it as `owner`, and a
        // scan listing only one spelling reports clean about the other.  Both are expected and both pinned: one at the
        // swap, one in discard for a build that threw before reaching it.  A third of either is refused.
        int ownerEmptiedCount = SourceOf.occurrences("owner.removeAll();");

        // How many lines the two statements are apart: the build lies between them
        final int apart = added < emptied ? -1
            : (int) code.substring(emptied, added).chars().filter(c -> c == '\n').count();

        assertTrue(emptied >= 0, "no `parent.removeAll();` statement in LayoutGrid - if the panel is "
            + "now emptied some other way, this test needs to be pointed at it rather than deleted");

        assertTrue(added >= 0, "no `parent.add(container);` statement in LayoutGrid");

        assertEquals(emptiedCount, 1,
            "LayoutGrid empties the diagram panel in " + emptiedCount + " places.  The distance check "
            + "below reads the FIRST of each, so a second copy left at the top of the build would "
            + "otherwise pass while doing exactly the thing this test exists to stop (VLD-C1)");

        assertEquals(addedCount, 1,
            "LayoutGrid adds the container in " + addedCount + " places, so the pairing this test "
            + "asserts is not the only one");

        assertEquals(ownerEmptiedCount, 1,
            "LayoutGrid empties the panel through its other name, `owner`, in " + ownerEmptiedCount
            + " places.  Exactly one is expected - inside discard(), for a build that threw before it reached the swap.  A second would be an emptying this test's distance check cannot see, because "
            + "that check only knows the spelling `parent` (RG5-C2)");

        assertTrue(apart >= 0 && apart <= 4,
            "LayoutGrid empties the diagram panel and puts the "
            + "replacement in " + apart + " lines "
            + "apart, and the entire grid build is in between.  The panel therefore stands EMPTY for "
            + "the whole rebuild, and any paint landing in that window draws a blank page.  Image "
            + "decoding inside the build pumps the event queue, so paints do land there.  That is the "
            + "flicker: not the diagram being redrawn, but nothing being drawn in its place first");
    }
}
