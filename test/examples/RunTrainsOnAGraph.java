package examples;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * Lets the railway drive a train along a graph, and watches it do so: where it started, where it is heading, which
 * sensors it has passed, and when it has arrived.
 *
 * The graph is three points in a row: Platform A, a Crossing with a sensor, and Platform B.  On a simulated station the
 * layout plays the train past each sensor itself; on a real railway the train trips them.  The sensors, the address and
 * "Example train" are invented: replace them with your own track's before handing `run` a real station.
 *
 * Shows `setLocomotivesToRun`, `runLocomotives`, `stopLocomotives`, `stopEveryTrainWhereItIs`, `isRunning`,
 * `setMinDelay`, `setMaxDelay`, `setSimulate`, the locomotive's `setCallback` with `Layout.CB_ROUTE_START`,
 * `CB_ROUTE_PROG` and `CB_ROUTE_END`, the layout's own `setCallback`, `getActiveLocomotives`, `getStart`, `getDestination`, `getPointsInActivePath`,
 * `getReachedMilestones`, `getLatestMilestoneS88`, `getLastPointsReached`, `waitForS88Reached`,
 * `getLocomotiveLocation` and the locomotive's `getNumPaths`.
 */
public class RunTrainsOnAGraph
{
    /** The locomotive the example uses, made if the station does not have it. */
    public static final String NAME = "Example train";

    private static final int[] SENSORS = {921, 922, 923};

    /**
     * @param station the Central Station to run on
     * @return a line of text for each thing seen along the way
     * @throws Exception when the graph cannot be built
     */
    public static List<String> run(MarklinControlStation station) throws Exception
    {
        // Several threads add to this: the train's own, and the little watchers started below.
        final List<String> found = Collections.synchronizedList(new ArrayList<String>());

        for (int sensor : SENSORS)
        {
            if (!station.isFeedbackSet(Integer.toString(sensor))) station.newFeedback(sensor, null);

            station.setFeedbackState(Integer.toString(sensor), false);
        }

        final Layout layout = new Layout(station);

        layout.createPoint("Platform A", true, "921");
        layout.createPoint("Crossing", false, "922");
        layout.createPoint("Platform B", true, "923");

        layout.createEdge("Platform A", "Crossing");
        layout.createEdge("Crossing", "Platform B");

        // A simulated station has no train to trip the sensors, so let the layout pretend.  Only possible on a
        // simulated station: the layout refuses on a real one.  The pauses between steps (in seconds) are the pace of
        // operation; zero keeps the example quick.
        if (station.isSimulation())
        {
            layout.setSimulate(true);
            layout.setMinDelay(0);
            layout.setMaxDelay(0);
        }

        // Use the locomotive if the station already has one of this name.  Otherwise add a plain Marklin Motorola
        // locomotive.  On your own railway, give the name of one of your locomotives instead.
        MarklinLocomotive train = station.getLocByName(NAME);

        if (train == null) train = station.newMM2Locomotive(NAME, 62);

        // The speed the railway drives it at, 1 to 100.  This changes the locomotive's stored preferred speed.
        train.setPreferredSpeed(40);

        layout.getPoint("Platform A").setLocomotive(train);

        //
        // Callbacks.  The train's own run on the thread that drives it, at the start of a journey, each sensor on the
        // way, and the arrival.
        //
        final CountDownLatch arrived = new CountDownLatch(1);

        final List<Thread> watchers = new ArrayList<>();

        train.setCallback(Layout.CB_ROUTE_START, (loc) ->
        {
            // The journey is the list of edges the layout keeps for each train that is under way.
            found.add("set off from " + layout.getStart(loc).getName() + " for " + layout.getDestination(loc).getName()
                + ", over " + names(layout.getPointsInActivePath(loc)));
        });

        train.setCallback(Layout.CB_ROUTE_PROG, (loc) ->
        {
            final String sensor = layout.getLatestMilestoneS88(loc);

            found.add("passed sensor " + sensor + ", sensors reached so far: " + names(layout.getReachedMilestones(loc))
                + ", last point reached: " + layout.getLastPointsReached().get(loc).getName());

            // waitForS88Reached waits for as long as the train is held at that sensor, and returns once it has moved on.
            // A callback runs on the train's own thread, so wait on another, or the train could never move on.
            Thread watcher = new Thread(() ->
            {
                layout.waitForS88Reached(loc, sensor);

                found.add("moved on from sensor " + sensor);
            });

            watchers.add(watcher);

            watcher.start();
        });

        train.setCallback(Layout.CB_ROUTE_END, (loc) ->
        {
            found.add("arrived at " + layout.getDestination(loc).getName() + ", points reached: "
                + names(layout.getReachedMilestones(loc)));

            arrived.countDown();
        });

        // The layout's own callback hears about every change of the picture: a train setting off or moving on (true)
        // and a train finishing (false).  The first argument is the edges concerned, and the train may be null.
        final int[] changes = {0};

        layout.setCallback("example", (path, loc, running) ->
        {
            synchronized (changes)
            {
                changes[0]++;
            }

            return null;
        });

        //
        // Run.  The layout picks a destination that is free and sends the train, one journey after another, until it
        // is told to stop.
        //
        layout.setLocomotivesToRun(Collections.singletonList(train));
        layout.runLocomotives();

        found.add("running: " + layout.isRunning());

        // A real railway takes a while; give it a minute
        boolean made = arrived.await(60, TimeUnit.SECONDS);

        found.add("the train arrived: " + made);

        // Stop asks the train to finish the journey it is on and then stand.  Wait a while for the layout to finish.
        layout.stopLocomotives();

        for (int waited = 0; waited < 20000 && layout.isRunning(); waited += 50)
        {
            Thread.sleep(50);
        }

        // A train still under way (a sensor that never trips, say) must not be left running when this returns.
        if (layout.isRunning()) layout.stopEveryTrainWhereItIs();

        for (Thread watcher : new ArrayList<>(watchers))
        {
            watcher.join(5000);
        }

        found.add("still running after stop: " + layout.isRunning());

        found.add("the train stands at: " + layout.getLocomotiveLocation(train).getName());

        found.add("journeys run by the train: " + train.getNumPaths());

        synchronized (changes)
        {
            found.add("the layout's callback heard of changes: " + (changes[0] > 0));
        }

        return found;
    }

    /**
     * @param points some points
     * @return their names, in alphabetical order
     */
    private static String names(Collection<Point> points)
    {
        TreeSet<String> sorted = new TreeSet<>();

        for (Point point : points) sorted.add(point.getName());

        return sorted.toString();
    }

    public static void main(String[] args) throws Exception
    {
        // Change this to your own station to run your railway (see SimulatedStation).
        MarklinControlStation station = SimulatedStation.open();

        for (String line : run(station))
        {
            System.out.println(line);
        }

        System.exit(0);
    }
}
