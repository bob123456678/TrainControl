package examples;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import static org.traincontrol.base.Accessory.accessorySetting.GREEN;
import static org.traincontrol.base.Accessory.accessorySetting.RED;
import static org.traincontrol.base.Accessory.accessorySetting.STRAIGHT;
import static org.traincontrol.base.Accessory.accessorySetting.TURN;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Builds the small two-station railway of AutomationAPI.md as a graph of points and edges, then asks the graph
 * questions and saves and reloads it.  Nothing runs; see RunTrainsOnAGraph for that.
 *
 * Shows `createPoint`, `createEdge`, `addConfigCommand`, `clearConfigCommand`, `addLockEdge`, `removeLockEdge`,
 * `clearLockEdges`, `setLength`, `getPathLength`, `getPoint`, `getPointById`, `getEdge`, `getEdgeById`,
 * `getIncomingEdges`, `getNeighbors`, `validateConfigCommand`, `hasOnlyReversingIncoming`, `hasOnlyReversingNeighbors`,
 * `hasOnlyInactiveIncoming`, `hasOnlyInactiveNeighbors`, `bfs`, `pathToString`, `setReversing`, `setActive`, `setMinDelay`,
 * `setMaxDelay`, `setDefaultLocSpeed`, `toJSON` and `fromJSON`.
 */
public class BuildAGraph
{
    /**
     * The sensors (S88 feedback) the stations use.  A point that trains may stop at needs one, so that the railway
     * can tell when the train has arrived.
     */
    private static final int[] SENSORS = {911, 912, 913};

    /**
     * Builds the graph: Station 1 and Station 2 both lead out through Main Track to Pre Arrival, and from there back
     * to either station.  Pre Arrival's switch decides which.
     *
     * @param station the Central Station the graph belongs to
     * @return the graph, not yet running anything
     * @throws Exception when a point or edge cannot be added
     */
    public static Layout build(MarklinControlStation station) throws Exception
    {
        // A point that stops trains needs a sensor that exists.  Ask the station to know them first.
        for (int sensor : SENSORS)
        {
            if (!station.isFeedbackSet(Integer.toString(sensor))) station.newFeedback(sensor, null);
        }

        // The settings below name switches and signals.  validateConfigCommand checks that the word is a setting
        // ("green", "turn" and so on) and adds the switch or signal to the station if it does not have it yet; it gives
        // back the setting the word means.
        Edge.validateConfigCommand("Signal 1", "green", station);
        Edge.validateConfigCommand("Signal 2", "green", station);
        Edge.validateConfigCommand("Switch 10", "turn", station);

        Layout layout = new Layout(station);

        // The pace of operation: a train waits between 1 and 3 seconds at each step, and a locomotive that has no
        // preferred speed of its own is given a speed of 40 (percent).  A graph that is saved needs both.  Set the
        // longest wait first, because the least may not be above it.
        layout.setMaxDelay(3);
        layout.setMinDelay(1);
        layout.setDefaultLocSpeed(40);

        // A station: true means trains may stop here, and the text is its sensor.
        layout.createPoint("Station 1", true, "911");
        layout.createPoint("Station 2", true, "912");
        layout.createPoint("Pre Arrival", true, "913");

        // A stretch of track both routes share.  Trains cannot stop here, and it needs no sensor.
        layout.createPoint("Main Track", false, null);

        // An edge goes one way, from one point to the next, and carries the switch and signal settings a train
        // needs to get along it.  Those settings are made just before the train sets out.
        layout.createEdge("Station 2", "Main Track").addConfigCommand("Signal 2", GREEN);
        layout.createEdge("Station 1", "Main Track").addConfigCommand("Signal 1", GREEN);

        layout.createEdge("Main Track", "Pre Arrival");

        layout.createEdge("Pre Arrival", "Station 1")
            .addConfigCommand("Switch 10", TURN)
            .addConfigCommand("Signal 1", RED);

        layout.createEdge("Pre Arrival", "Station 2")
            .addConfigCommand("Switch 10", STRAIGHT)
            .addConfigCommand("Signal 2", RED);

        // The two ways onto Main Track cross, so the railway should never lock one while the other is locked.
        layout.getEdge("Station 1", "Main Track").addLockEdge(layout.getEdge("Station 2", "Main Track"));
        layout.getEdge("Station 2", "Main Track").addLockEdge(layout.getEdge("Station 1", "Main Track"));

        return layout;
    }

    /**
     * @param station the Central Station the graph belongs to
     * @return a line of text for each thing the graph answered
     * @throws Exception when the graph cannot be built or reloaded
     */
    public static List<String> run(MarklinControlStation station) throws Exception
    {
        List<String> found = new ArrayList<>();

        Layout layout = build(station);

        Point station1 = layout.getPoint("Station 1");
        Point preArrival = layout.getPoint("Pre Arrival");
        Point mainTrack = layout.getPoint("Main Track");

        //
        // Looking things up.  Names are what you chose; the unique id is the name the graph files use, and it never
        // changes when you rename.
        //
        found.add("points: " + layout.getPoints().size() + ", edges: " + layout.getEdges().size());

        found.add("point by id: " + layout.getPointById(station1.getUniqueId()).getName());

        Edge toMain = layout.getEdge("Station 1", "Main Track");

        found.add("edge by name: " + layout.getEdge(toMain.getName()).getName());

        found.add("edge by id: " + layout.getEdgeById(toMain.getUniqueId()).getName());

        found.add("edge to an unknown point: " + layout.getEdge("Station 1", "Nowhere"));

        //
        // The surroundings of a point
        //
        found.add("leaving Pre Arrival: " + layout.getNeighbors(preArrival).size()
            + ", arriving at Pre Arrival: " + layout.getIncomingEdges(preArrival).size());

        // These four ask whether every neighbor (or every incoming edge) is reversing, or inactive.  Reversing is for
        // a station where the train turns round; inactive switches a point off for the autonomy.
        found.add("every way into Pre Arrival is from a reversing point: " + layout.hasOnlyReversingIncoming(preArrival));

        mainTrack.setReversing(true);

        found.add("after making Main Track reversing: " + layout.hasOnlyReversingIncoming(preArrival)
            + ", and every neighbor of Station 1 reversing: " + layout.hasOnlyReversingNeighbors(station1));

        found.add("every way into Pre Arrival is from an inactive point: " + layout.hasOnlyInactiveIncoming(preArrival));

        mainTrack.setActive(false);

        found.add("after switching Main Track off: " + layout.hasOnlyInactiveIncoming(preArrival)
            + ", and every neighbor of Station 1 inactive: " + layout.hasOnlyInactiveNeighbors(station1));

        mainTrack.setActive(true);
        mainTrack.setReversing(false);

        //
        // Finding a way, and measuring it.  Lengths are in the units you choose (centimeters, say), and are used by
        // the railway to tell whether a train fits.
        //
        layout.getEdge("Station 1", "Main Track").setLength(120);
        layout.getEdge("Main Track", "Pre Arrival").setLength(80);
        layout.getEdge("Pre Arrival", "Station 2").setLength(60);

        List<Edge> way = layout.bfs(station1, layout.getPoint("Station 2"), new LinkedList<List<Edge>>());

        found.add("shortest way: " + layout.pathToString(way));

        found.add("its length: " + layout.getPathLength(way));

        //
        // Changing an edge.  A setting can be taken off again, and so can a lock.
        //
        Edge intoStation1 = layout.getEdge("Pre Arrival", "Station 1");

        found.add("settings on Pre Arrival to Station 1: " + intoStation1.getConfigCommands().size());

        intoStation1.clearConfigCommand("Signal 1");

        found.add("after clearing Signal 1: " + intoStation1.getConfigCommands().size());

        Edge toMain2 = layout.getEdge("Station 2", "Main Track");

        found.add("locks held by the way out of Station 1: " + toMain.getLockEdges().size());

        toMain.removeLockEdge(toMain2);

        found.add("after removing one: " + toMain.getLockEdges().size());

        toMain.addLockEdge(toMain2);
        toMain.clearLockEdges();

        found.add("after clearing them all: " + toMain.getLockEdges().size());

        toMain.addLockEdge(toMain2);

        //
        // Saving and loading.  toJSON gives the whole graph as text, to keep in a file; fromJSON builds it again,
        // for the same station.
        //
        String saved = layout.toJSON();

        Layout again = Layout.fromJSON(saved, station);

        // A graph that could not be read is marked invalid, and says why.
        found.add("reloaded: valid " + again.isValid() + (again.isValid() ? "" : ", because " + again.getInvalidReason())
            + ", points " + again.getPoints().size() + ", edges " + again.getEdges().size());

        Edge reloaded = again.getEdge("Station 1", "Main Track");

        found.add("locks on the way out of Station 1 after reloading: "
            + (reloaded == null ? "none, the edge is missing" : Integer.toString(reloaded.getLockEdges().size())));

        return found;
    }

    public static void main(String[] args) throws Exception
    {
        // Change this to your own station to build the graph on your railway (see SimulatedStation).
        MarklinControlStation station = SimulatedStation.open();

        for (String line : run(station))
        {
            System.out.println(line);
        }

        System.exit(0);
    }
}
