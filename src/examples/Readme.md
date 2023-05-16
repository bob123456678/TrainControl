# Automating your layout

Beyond its GUI, the TrainControl software can be used to progammatically control your Marklin layout, and even fully automate it.  This means that you can specify exactly how and when you want your trains, switches, signals, and accessories to behave.

## API

Before jumping into layout automation, it is important to understand the basics of the TrainControl Java API.  
The `MarklinControlStation` class is your gateway to the CS2/CS3's database of locomotives, switches, signals, and accessories. Several examples are provided in [`ProgrammaticControlExample.java`](ProgrammaticControlExample.java), where it is initialized and accessible via the `data` variable.

## Sample Layout

Let's consider a minimal layout with two stations.

![Sample layout](../../assets/sample_layout.gif?raw=true)

# Basic automation

With this simple setup, we could run the trains with the following logic:
* The locomotive at station 1 departs and stops once it arrives back at station 1
* The locomotive at station 2 departs and stops once it arrives back at station 2
* Rinse and repeat

The code below implements this logic, along with some additional commands to turn on light and sound functions (and is itself a great example of using the API):

    while (true)
    {
        // Fetch the locomotive at Station 1
        // Replace "Loc1" with the CS2/CS3 name of your locomotive
        data.getLocByName("Loc1")
                // Flip some signals
                .setAccessoryState(1, true)
                .setAccessoryState(2, false)
                // Turnout on the way back in
                .setAccessoryState(10, true)
                // Wait 2-20 seconds
                .delay(2,20)
                // Turn on locomotive sound and lights
                .setF(3, true)
                .lightsOn()
                // Start rolling
                .setSpeed(40)
                .waitForOccupiedFeedback("3")
                // Destination signal should now be red
                .setAccessoryState(1, false)
                // Slow down
                .setSpeed(20)
                // Stop the locomotive when it arrives at the station
                // (S88 address 1)
                .waitForOccupiedFeedback("1")
                .setSpeed(0);

        // Once Loc1 is done, fetch the locomotive at Station 2
        data.getLocByName("Loc2")
                // Sanity check - do not proceed unless Loc1 is at its station
                .waitForOccupiedFeedback("1")
                // Flip some signals
                .setAccessoryState(1, false)
                .setAccessoryState(2, true)
                // Go straight on the way back in
                .setAccessoryState(10, false)
                // Wait 2-20 seconds
                .delay(2,20)
                // Turn on locomotive sound and lights
                .setF(3, true)
                .lightsOn()
                // Start rolling
                .setSpeed(40)
                .waitForOccupiedFeedback("3")
                // Destination signal should now be red
                .setAccessoryState(2, false)
                // Slow down
                .setSpeed(20)
                // Stop the locomotive when it arrives at the station
                // (S88 address 1)
                .waitForOccupiedFeedback("2")
                .setSpeed(0);
    }

Unfortunately this approach makes the behavior of the trains entirely predictable (and therefore boring!).  
It is also difficult to concurrently operate trains unless they are run in sequence or somehow blocked by signals (which is unnatural).

# Smart automation

How can we make things more dynamic? The `Layout` class enables us to represent the layout as a graph so that we do not need to explicitly define the route or command sequence for any locomotive.  
Instead, each locomotive will figure out what route to follow, and it will continuously execute unoccupied routes.

Before we can use this class, we first need to come up with a graph representation of the layout.  The graph below corresponds to the sample layout from the previous example.
We define a point for each station and for every other S88 feedback sensor, and then we connect them with edges.

Notice the shared edge from "Main Track" to "Pre Arrival": this design ensures that the routes are mutually exclusive (the two trains won't be able to run simultaneously, and therefore can't crash).

![Sample layout represented as a graph](../../assets/graph.png?raw=true)

This graph representation can then easily be translated into code (full example can be found in `FullAutonomyExample.java`.  
Note that the edge definitions also include accessory commands, such as setting turnouts and signals correctly.  
These commands will execute before that edge is traversed by a locomotive.

    // Initialize the graph
    Layout layout = new Layout(data);

    //
    // Define our stations and shared track segments
    //
    layout.createPoint("Station 1", true, "1");
    layout.createPoint("Station 2", true, "2");
    layout.createPoint("Pre Arrival", true, "3");

    // The train cannot stop here, but we create an extra point so that both routes share a common edge
    layout.createPoint("Main Track", false, null);

    //
    // Define our edges (stations/points conncted to each other, and switch/signal commands needed to make those connections)
    //
    // Note that from v1.8.0 of TrainControl,
    // we can and should use control.getAutoLayout().configure instead of control.getAccessoryByName().turn/straight/red/green 
    // This gives us additional sanity checks for conflicting commands so that a path that includes opposite settings for the same accessory would thus never be chosen
    layout.createEdge("Station 2", "Main Track", (control) -> {control.getAutoLayout().configure("Signal 2", GREEN);});
    layout.createEdge("Station 1", "Main Track", (control) -> {control.getAutoLayout().configure("Signal 1", GREEN);});

    layout.createEdge("Main Track", "Pre Arrival", null);

    layout.createEdge("Pre Arrival", "Station 1", (control) -> {control.getAutoLayout().configure("Switch 10", TURN); control.getAutoLayout().configure("Signal 1", RED);});
    layout.createEdge("Pre Arrival", "Station 2", (control) -> {control.getAutoLayout().configure("Switch 10", STRAIGHT); control.getAutoLayout().configure("Signal 2", RED);});

From here, all we need to do is place our locomotives on the layout and tell them to run!

    layout.getPoint("Station 1").setLocomotive(data.getLocByName("SNCF 422365"));
    layout.getPoint("Station 2").setLocomotive(data.getLocByName("140 024-1 DB AG"));

    //
    // Now we can run the locomotives!  This method also specifies the desired speed
    // The layout class will automatically choose and execute an available route
    //
    layout.runLocomotive(data.getLocByName("SNCF 422365"), 30);
    layout.runLocomotive(data.getLocByName("140 024-1 DB AG"), 50);

The `Layout` class also allows *optional* pre-departure, pre-arrival, and post-arrival callbacks to be set to turn on functions and lights as desired.
In these lambda functions, `loc` is a reference to the locomotive itself.
                
    data.getLocByName("SNCF 422365").setCallback(Layout.CB_ROUTE_START, (loc) -> {loc.lightsOn().delay(1, 3);});
    data.getLocByName("140 024-1 DB AG").setCallback(Layout.CB_ROUTE_START, (loc) -> {loc.lightsOn().delay(1, 3).toggleF(11);});

    data.getLocByName("SNCF 422365").setCallback(Layout.CB_PRE_ARRIVAL, (loc) -> {loc.toggleF(3);});
    data.getLocByName("140 024-1 DB AG").setCallback(Layout.CB_PRE_ARRIVAL, (loc) -> {loc.toggleF(3);});

    data.getLocByName("SNCF 422365").setCallback(Layout.CB_ROUTE_END, (loc) -> {loc.delay(1, 3).lightsOff();});
    data.getLocByName("140 024-1 DB AG").setCallback(Layout.CB_ROUTE_END, (loc) -> {loc.delay(1, 3).lightsOff();});

Now we have everything we need to proceed.
When the code in `FullAutonomyExample.java` is executed, the following things will happen:
* For both locomotives, the graph will try to pick a path to an unoccupied destination station, as long as that path is not blocked by other trains
* Since the paths are mutually exclusive, only one locomotive will be allowed to proceed.  All graph edges in that locomotive's path will be locked.
* That locomotive will run from its station to "Pre Arrival".  It will stop there (because in the current graph implementation, cycles are not allowed).  The prior path gets unlocked.
* That same locomotive will then run from "Pre Arrival" back to its original station, since the opposite station is occupied.  Note that the other locomotive cannot go to "Pre Arrival", because it is marked as occupied by the first locomotive.
* Once the first locomotive reaches its original station, its path is unlocked.
* This process will repeat, with one of the two locomotives being chosen to run.

If you want to try this out for yourself, be sure to change the locomotive names to ones on your layout (as configured in the CS2/CS3).

A few additional notes:
* The layout class assumes that each locomotive is already set to go in the direction modeled in the graph (i.e., forward).  This can also be set explicitly in the code by calling `Locomotive.setDirection`.
* Upon reaching the second-to-last point in its path, the layout class will halve each locomotive's speed to make for a more natural stop.  The more S88's you have, the better!

# Improving the layout

How could this layout be modified to be a bit more exciting?  Suppose we wanted to allow trains to arrive at the opposite station.
To accomplish this, we can convert "Main Track" to a valid station (second argument to `true`) and give it an S88 sensor (hypothetically at address 4):

    layout.createPoint("Main Track", true, "4");

With this setup, a train at "Station 1" or "Station 2" could go to "Main Track" after the other train reaches "Pre Arrival".  
But there's a problem!  Since the path from "Station 1" to "Main Track" shares no edges with "Station 2" to "Main Track", the graph might allow two trains to proceed at the same time.
One solution would be to add an imaginary point between the two stations and "Main Track", and then connect them with a shared edge, similar to what we already did with "Pre Arrival".

Alternatively, for any edge, the graph actually allows us to explicitly specify a list of additional edges that should be locked whenever it is chosen. 
This way, we can make the two paths to "Main Track" mutually exclusive without adding any more points or edges to the graph:

    layout.getEdge("Station 1", "Main Track").addLockEdge(
        layout.getEdge("Station 2", "Main Track")
    );
    layout.getEdge("Station 2", "Main Track").addLockEdge(
        layout.getEdge("Station 1", "Main Track")
    );

This functionality is essential when your layout includes crossings, since these cannot easily be modeled with a directed graph.

# Running and Visualizing via TrainControl UI

From v1.8.0, to make execution and modifications easier, the logic above can be expressed in a JSON format and executed via the TrainControl UI's "Autonomy" tab. 
The following example JSON corresponds to the above code/layout and edge locking.

Note that `minDelay` and `maxDelay` specify the minimum and maximum delay, in seconds, between locomotive activations.  
The actual value is randomly chosen in this range, and this replaces the need for manual definitions in callbacks.

TrainControl will enable/disable each locomotive's preferred functions, if any, (as set in the UI) before departure and upon arrival, respectively.  These cannot be specified in the JSON.  
However, you can set `turnOffFunctionsOnArrival` to `false` to skip turning off the function on arrival.

Each locomotive's preferred speed will be used (as set in the UI), unless it is 0, in which case the program will revert to `defaultLocSpeed`.
The optional `locArrivalFunc` and `locDepartureFunc` function numbers will be toggled when the locomotive is about to reach its destination and about to depart, respectively.

To get started, paste the JSON in TrainControl's "autonomy" tab, then click on "Validate JSON".  Any errors (such as non-existing edges or missing points) will be shown in the log.  
If there are no errors, autonomous operation can be activated by clicking on "Start Autonomous Operation".  
Locomotives will then continue running per the specified layout until stopped via the former button.  Chosen paths will be shown in the log.

Note that a path with conflicting accessory commands will never be chosen.

```
{
    "minDelay" : 1,
    "maxDelay" : 5,
    "defaultLocSpeed" : 35,
    "turnOffFunctionsOnArrival": true,
    "reversibleLocs" : [],
    "points": [
        {
            "name": "Station 1",
            "station": true,
            "s88" : 1,
            "loc" : "SNCF 422365",
            "locArrivalFunc" : 3,
            "locDepartureFunc" : 10
        },
        {
            "name": "Station 2",
            "station": true,
            "s88" : 2,
            "loc" : "140 024-1 DB AG",
            "locArrivalFunc" : 3
        },
        {
            "name": "Pre Arrival",
            "station": true,
            "s88" : 3
        },
        {
            "name": "Main Track",
            "station": false
        }
    ],
    "edges": [
       {
            "start": "Station 2",
            "end": "Main Track",
            "commands" : [
                {
                    "acc" : "Signal 2",
                    "state" : "green"  
                }
            ],
            "lockedges" : [
                {
                   "start": "Station 1",
                   "end": "Main Track"  
                }
            ]
        },
        {
            "start": "Station 1",
            "end": "Main Track",
            "commands" : [
                {
                    "acc" : "Signal 1",
                    "state" : "green"  
                }
            ],
            "lockedges" : [
                {
                   "start": "Station 2",
                   "end": "Main Track"  
                }
            ]
        },
        {
            "start": "Main Track",
            "end": "Pre Arrival",
        },
        {
            "start": "Pre Arrival",
            "end": "Station 1",
            "commands" : [
                {
                    "acc" : "Signal 1",
                    "state" : "red"  
                },
                {
                    "acc" : "Switch 10",
                    "state" : "turn"  
                }
            ]
        },
        {
            "start": "Pre Arrival",
            "end": "Station 2",
            "commands" : [
                {
                    "acc" : "Signal 2",
                    "state" : "red"  
                },
                {
                    "acc" : "Switch 10",
                    "state" : "straight"  
                }
            ]
        }
    ]
}

```

When the "Validate JSON" button is pressed, if the layout is valid, a visual representation will also be shown.  This visualization is updated in real time as the paths execute.

Edge colors:
* Red - path is executing along this edge, edges not yet reached by the incoming train
* Green - path is executing along this edge, edges have been reached
* Gray - edges are locked to avoid collisions, per `lockedges` definition
* Black - edges are unoccupied / unlocked with no active path

Point colors:
* Blue - no locomotive stationed or passing through
* Green - locomotive stationed or has passed through

![Sample layout](../../assets/graph2.png?raw=true)

# Terminus Stations

Basic support for terminus stations has been added as of v1.8.0.  For any `Point` that represents a terminus station (`station` must be `true` in the JSON),
also specify `"terminus" : "true"`.  Only locomotives listed in the `reversibleLocs` array will be able to go there, and they will automatically change direction after arrival.
Terminus stations must have a separate set of outgoing edges (without cycles) that only reconnect with the main line after the train has passed through a reversing loop.

If using the Java API, `Point.setTerminus` and `Layout.addReversibleLoc` correspond to the JSON settings above.

# Advanced layouts

This graph model can be used to automate layouts with complex designs and tons of switches.  

A more advanced example (automation JSON plus CS2 layout files) can be found in [cs2_sample_layout](../../cs2_sample_layout/config/)

Planned features include ways to create/edit the graph in the UI and specify/prioritize automation logic.