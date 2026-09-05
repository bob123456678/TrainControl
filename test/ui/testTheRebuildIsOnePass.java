package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;

/**
 * A refresh arrives all at once rather than in installments.
 *
 * Adam, 2026-09-04: "Can we get the diagrams to re-render with less flickering?  Same goes for the
 * main UI when the hourglass expires - it seems that some components appear faster than others.  Best
 * to do a single pass."
 *
 * Two separate causes, and neither of them is slowness.
 *
 * **The diagram.** `LayoutGrid` emptied the panel at the top of the build and put the replacement in
 * at the bottom, eight hundred lines and one whole grid later - every tile, every image decode, every
 * caption in between. Decoding pumps the event queue, so paints land inside that window, and what they
 * paint is a panel with nothing in it. The flicker was not the diagram being redrawn; it was the blank
 * page shown while it was being built.
 *
 * **The main window.** Several refreshes in a row, each posting its own `invokeLater`, is several
 * events - and Swing lays out and paints between events. The route list, the locomotive list and the
 * menus coming back were three visible steps because they were asked for three separate times.
 *
 * These are checked differently on purpose. `singlePass` has a behaviour that can be run, so it is
 * run. The `LayoutGrid` ordering has no observable behaviour short of catching a paint mid-rebuild -
 * which is a race, and a test that only sometimes reproduces is worse than no test - so it is checked
 * as the ordering it is.
 *
 * @author Adam
 */
public class testTheRebuildIsOnePass
{
    /**
     * `singlePass` runs inline when there is already a pass to join.
     *
     * This is the whole mechanism. `invokeLater` posts unconditionally, so a caller ALREADY on the
     * event thread cannot collect several refreshes into one pass by wrapping them - the wrapper is
     * one event and each refresh inside it asks for another. Running inline is what makes the wrapping
     * do anything at all.
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
     * The other half, and the one that matters for safety rather than appearance: `doSync` calls it
     * from a worker thread, and Swing components must not be touched there. A `singlePass` that simply
     * ran the body would have moved two menu calls onto a background thread while making the window
     * look better.
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
     * `LayoutGrid` builds the whole grid between emptying the panel and adding the new one. Emptying
     * FIRST means the panel stands with no children for the entire build - and image decoding inside
     * that build pumps the event queue, so paints land in the middle of it and draw a blank page.
     *
     * Checked as ordering rather than by catching a paint: reproducing the blank frame needs a paint
     * to land inside a window that varies with disk and decode speed, and a test that reproduces only
     * sometimes reports "fixed" on a bad day. What is actually being asserted - that the panel is
     * never empty while the new grid is built - is fully determined by where these two lines sit.
     *
     * MUTATION: move `parent.removeAll()` back above the build and this fails.
     */
    @Test
    public void testTheOldDiagramStaysUpUntilTheNewOneIsReady() throws Exception
    {
        final File source = new File("src/org/traincontrol/gui/LayoutGrid.java");

        assertTrue(source.exists(), "precondition: " + source + " has to be readable from the test's "
            + "working directory, or this test asks nothing");

        final String[] lines =
            new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8).split("\r?\n");

        int emptied = -1;
        int added = -1;

        for (int i = 0; i < lines.length; i++)
        {
            final String line = lines[i].trim();

            // The statement, not a mention of it in a comment.
            if (line.equals("parent.removeAll();")) emptied = i;
            if (line.equals("parent.add(container);")) added = i;
        }

        assertTrue(emptied >= 0, "no `parent.removeAll();` statement in LayoutGrid - if the panel is "
            + "now emptied some other way, this test needs to be pointed at it rather than deleted");

        assertTrue(added >= 0, "no `parent.add(container);` statement in LayoutGrid");

        assertTrue(added - emptied >= 0 && added - emptied <= 4,
            "LayoutGrid empties the diagram panel at line " + (emptied + 1) + " and puts the "
            + "replacement in at line " + (added + 1) + " - " + Math.abs(added - emptied) + " lines "
            + "apart, and the entire grid build is in between.  The panel therefore stands EMPTY for "
            + "the whole rebuild, and any paint landing in that window draws a blank page.  Image "
            + "decoding inside the build pumps the event queue, so paints do land there.  That is the "
            + "flicker: not the diagram being redrawn, but nothing being drawn in its place first");
    }
}
