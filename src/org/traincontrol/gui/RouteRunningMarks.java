package org.traincontrol.gui;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.traincontrol.base.Route;

/**
 * The turning mark beside every route that is running, on the route tab (FR-137).
 *
 * Adam: "in the route tab, show a spinner next to any route that is currently running, similar to the autonomy spinner.
 * 3.0.2 feature".  The mark is Start's and Return Home's (`TrainControlUI.TurningArc`, FR-077, OB-309), turned on the same
 * 60ms frame.  Whether a route has one is `Route.isExecuting()`, asked by the list's renderer as it paints the cell - the
 * route's own flag, so the mark is true for a route whoever started it (the play button, a click on the row, the diagram's
 * route tile, an s88 trigger, another route, autonomy) and gone when `MarklinRoute.execRoute` really ends, not some time
 * after the press.
 *
 * What this holds is only WHAT TO ANIMATE: the names `View.routeStarted` has announced and `View.routeFinished` has not.
 * The timer runs while any of them is still executing and stops the moment none is, so a window with nothing running
 * repaints nothing.  Every check of the routes, and every repaint, is on the event thread; the notices come from the route's
 * thread and are only posted.  Nothing here takes the railway's monitor: `Route.isExecuting` is the route's own.
 */
final class RouteRunningMarks
{
    /** The width of the mark, which is also its height. */
    private static final int SIZE = 14;

    /** What a cell gives up on the text's side of the play strip while its route runs, so the name stops short of the mark. */
    static final int SLOT = SIZE + 6;

    /** The frame, `LoadingSpinner`'s, so the marks turn at one rate. */
    private static final int FRAME_MS = 60;

    private final Supplier<? extends JComponent> list;

    private final Function<String, Route> routeNamed;

    /** The routes announced as started and not yet seen to have ended. */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    private final TrainControlUI.TurningArc arc = new TrainControlUI.TurningArc(SIZE);

    /** The animation, or null while nothing runs.  Event thread only. */
    private Timer timer;

    /**
     * @param list the route list, which is repainted as the mark turns
     * @param routeNamed the route of a name, or null
     */
    RouteRunningMarks(Supplier<? extends JComponent> list, Function<String, Route> routeNamed)
    {
        this.list = list;
        this.routeNamed = routeNamed;
    }

    /**
     * A route has begun running.  May be called from the route's thread.
     *
     * @param name the route's name
     */
    void started(String name)
    {
        if (name == null) return;

        running.add(name);

        SwingUtilities.invokeLater(this::check);
    }

    /**
     * A route has ended.  May be called from the route's thread.
     *
     * The name is not taken out here: `check` takes out every route that is no longer executing, on the event thread.  A
     * route started again between this run's `stopExecuting` and this call is executing once more, and taking its name
     * out would leave its mark drawn and still (RSA75-C2).
     *
     * @param name the route's name
     */
    void finished(String name)
    {
        if (name == null) return;

        SwingUtilities.invokeLater(this::check);
    }

    /**
     * @return whether the mark is being animated - true only while a route that was announced still runs
     */
    boolean isTurning()
    {
        return timer != null && timer.isRunning();
    }

    /**
     * Draws the mark in the slot beside a cell's play strip.
     *
     * @param g where the cell is painted
     * @param strip the play strip of that cell (`TrainControlUI.routePlayButtonBox`)
     */
    void paint(Graphics2D g, Rectangle strip)
    {
        arc.paintIcon(null, g, strip.x - SLOT + (SLOT - SIZE) / 2, strip.y + (strip.height - SIZE) / 2);
    }

    /**
     * Starts or stops the animation to match what still runs, and shows the result.  Event thread only.
     */
    private void check()
    {
        for (Iterator<String> names = running.iterator(); names.hasNext(); )
        {
            Route route = routeNamed.apply(names.next());

            // A route the window no longer has, or one that has ended without anybody here being told
            if (route == null || !route.isExecuting()) names.remove();
        }

        JComponent shown = list.get();

        // The timer lives as long as a route runs, shown or not: a route ends, and a window nobody sees is not repainted
        if (!running.isEmpty())
        {
            if (timer == null)
            {
                timer = new Timer(FRAME_MS, e -> check());

                timer.start();
            }

            arc.advance();
        }
        else if (timer != null)
        {
            timer.stop();

            timer = null;
        }

        // Once more when it stops, which is what takes the last mark off the list
        if (shown != null && shown.isShowing()) shown.repaint();
    }
}
