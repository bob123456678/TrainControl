# Automating your layout

Beyond its GUI, the TrainControl software can be used to progammatically control your Marklin layout, and even fully automate it.  This means that you can specify exactly how and when you want your trains, switches, signals, and accessories to behave.

There are three types of automation described on this page:
* Manually defining basic automation logic and commands via the Java API (good)
* Using TrainControl's `Layout` class to represent your layout as a graph model and programmatically start autonomous operation (better)
* Using TrainControl's UI to represent your layout as a graph model, make visual edits, and monitor operation (best)

## API

Before jumping into layout automation, it is important to understand the basics of the TrainControl Java API.  
The `MarklinControlStation` class is your gateway to the CS2/CS3's database of locomotives, switches, signals, and accessories. Several examples are provided in [`ProgrammaticControlExample.java`](src/org/traincontrol/examples/ProgrammaticControlExample.java), where it is initialized and accessible via the `data` variable.

Note that as of v2.3.2, all TrainControl code has been moved into the org.traincontrol package.  Custom code using older versions would therefore need to be updated.

Refer to the Java docs for class and method details.

## Sample Layout

Let's consider a minimal layout with two stations.

![Sample layout](assets/sample_layout.gif?raw=true)

# Basic automation

With this simple setup, we could run the trains with the following logic:
* The locomotive at station 1 departs and stops once it arrives back at station 1
* The locomotive at station 2 departs and stops once it arrives back at station 2
* Rinse and repeat

The code below implements this logic, along with some additional commands to turn on light and sound functions (and is itself a great example of using the API):

    while (true)
    {
        // Fetch the locomotive at Station 1
        data.getLocByName("Loc1")
                // Flip some signals
                .setAccessoryState(1, Accessory.accessoryDecoderType.MM2, true)
                .setAccessoryState(2, Accessory.accessoryDecoderType.MM2, false)
                // Turnout
                .setAccessoryState(10, Accessory.accessoryDecoderType.MM2, true)
                // Wait 2-20 seconds
                .delay(2,20)
                // Turn on locomotive sound and lights
                .setF(3, true)
                .lightsOn()
                // Start rolling
                .setSpeed(40)
                .waitForOccupiedFeedback("3")
                // Signal should now be red
                .setAccessoryState(1, Accessory.accessoryDecoderType.MM2, false)
                // Slow down
                .setSpeed(20)
                // Stop the locomotive when it arrives at the station
                // (S88 address 1)
                .waitForOccupiedFeedback("1")
                .setSpeed(0);

        // Fetch the locomotive at Station 2
        data.getLocByName("Loc2")
                // Do not proceed unless Loc1 is at its station
                .waitForOccupiedFeedback("1")
                // Flip some signals
                .setAccessoryState(1, Accessory.accessoryDecoderType.MM2, false)
                .setAccessoryState(2, Accessory.accessoryDecoderType.MM2, true)
                // Go straight
                .setAccessoryState(10, Accessory.accessoryDecoderType.MM2, false)
                // Wait 2-20 seconds
                .delay(2,20)
                // Turn on locomotive sound and lights
                .setF(3, true)
                .lightsOn()
                // Start rolling
                .setSpeed(40)
                .waitForOccupiedFeedback("3")
                // Signal should now be red
                .setAccessoryState(2, Accessory.accessoryDecoderType.MM2, false)
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

Notice the shared edge from "Main Track" to "Pre Arrival": this design ensures that the routes are mutually exclusive (the two trains won't be able to run simultaneously, and therefore can't crash).  Conceptually (and perhaps more commonly), edges can also be referred to as "Blocks".

![Sample layout represented as a graph](assets/graph.png?raw=true)

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
    // The addConfigCommand API provies sanity checks for conflicting commands so that a path that includes opposite settings for the same accessory would never be chosen
    layout.createEdge("Station 2", "Main Track").addConfigCommand("Signal 2", GREEN);
    layout.createEdge("Station 1", "Main Track").addConfigCommand("Signal 1", GREEN);

    layout.createEdge("Main Track", "Pre Arrival");

    layout.createEdge("Pre Arrival", "Station 1").addConfigCommand("Switch 10", TURN).addConfigCommand("Signal 1", RED);
    layout.createEdge("Pre Arrival", "Station 2").addConfigCommand("Switch 10", STRAIGHT).addConfigCommand("Signal 2", RED);

    // From here, all we need to do is place our locomotives on the layout and tell them to run!

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
                
    // You can also simply use layout.applyDefaultLocCallbacks(data.getLocByName("SNCF 422365")) to use the preferred functions set in the TrainControl UI
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
* Upon reaching the second-to-last point in its path, the layout class will halve each locomotive's speed to make for a more natural stop.  This value can be overriden by `preArrivalSpeedReduction` in the JSON (default 0.5, or 50%. Range 0.01 - 1.00, where 1.00 means no reduction).  The more S88's you have, the better!

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

To make execution and modifications easier, the logic above can be expressed in a JSON format and executed via the TrainControl UI's "Autonomy" tab. 
Moreover, to make it easier to create graphs, all state associated with graphs (Points, Edges, and Locomotives) can be edited via the TrainControl UI.  All locomotive settings can also be edited via the UI, which eliminates the need for you to ever touch the JSON except when backing up a graph.

The following example JSON corresponds to the above code/layout and edge locking.

To get started, paste the JSON in TrainControl's "Autonomy" tab, then click on "Validate Graph".  Any errors (such as non-existing edges or missing points) will be shown in the log.  You can also click on "Initialize New Graph" and let the program load the sample JSON for you.

If there are no errors, autonomous operation can be activated by clicking on "Start Autonomous Operation".  
Locomotives will then continue running per the specified layout until stopped via "Graceful Stop", or reset by the former button.  Graceful Stop is recommended, as this way the state will automatically be saved when you exit the program.   Chosen paths will be shown in the log.

You can also manually specify where each locomotive should go through the "Locomotive Commands" tab.  The list of available paths is automatically calculated based on the graph state and S88 feedback. 

Details on advanced configuration parameters can be found at the end of this document.

```
{
    "minDelay" : 1,
    "maxDelay" : 5,
    "defaultLocSpeed" : 35,
    "preArrivalSpeedReduction" : 0.5,
    "turnOffFunctionsOnArrival": true,
    "turnOnFunctionsOnDeparture": true,
    "atomicRoutes": true,
    "maxLocInactiveSeconds" : 120,
    "points": [
        {
            "name": "Station 1",
            "station": true,
            "s88" : 1,
            "loc" : 
            {
                "name" : "SNCF 422365",
                "reversible" : false,
                "arrivalFunc" : 3,
                "departureFunc" : 2,
                "trainLength" : 3,
            },
            "maxTrainLength": 4,
            "priority": 1,
            "x" : 1521,
            "y" : 291
        },
        {
            "name": "Station 2",
            "station": true,
            "s88" : 2,
            "loc" : 
            {
                "name": "140 024-1 DB AG",
                "reversible" : false,
                "arrivalFunc" : 3,
                "trainLength" : 4
            },
            "maxTrainLength": 0,
            "x" : 1554,
            "y" : 0
        },
        {
            "name": "Pre Arrival",
            "station": true,
            "s88" : 3,
            "x" : 503,
            "y" : 1241
        },
        {
            "name": "Main Track",
            "station": false,
            "x" : 2056,
            "y" : 1274
        }
    ],
    "edges": [
       {
            "start": "Station 2",
            "end": "Main Track",
            "length" : 0,
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
            "length" : 0,
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
            "length" : 0
        },
        {
            "start": "Pre Arrival",
            "end": "Station 1",
            "length" : 0,
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
            "length" : 0,
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
* Orange - shown only while editing the edge; indicates that it is within the list of lock edges

Point colors:
* Blue - no active route.  Label indicates if locomotive is stationed.
* Red - active route - locomotive soon to pass through
* Green - active route - locomotive has passed through
* Orange - point is disabled (inactive).  Autonomous routes will never start/stop/pass through this point. (From v2.0.0)

Point shapes:
* Circle - regular station.  Any train can stop here.
* Square - terminus station. Only reversable trains can stop here.  They will switch direction on arrival.
* Cross - reversing station (large) or reversing point (small).  Any train can stop here and will switch direciton on arrival.  Useful for shunting/parking.
* Diamond - intermediate point that is not a station (trains pass through these while operating between stations; locomotives manually placed here will not be automatically run)

![Sample layout](assets/graph2b.png?raw=true)

# Prettifying the Graph Visualizaton

For each point, you can specify optional, relative `x` and `y` coordinates in the JSON: these will fix the points to a specific location on the graph.  If any point is missing a coordinate, the points on the graph will assume a random layout.

If you want to adjust the graph once created, maximize it, and simply use your mouse to move points around.  The coordinates will automatically be saved on exit, or you can use the "Export Current Graph" button to view the updated JSON file.

# Terminus Stations

TrainControl supports terminus stations.  For any `Point` that represents a terminus station (`station` must be `true` in the JSON),
also specify `"terminus" : "true"`.  For the corresponding point/locomotive, set `"locReversible" : "true"`.  Only such reversible locomotives can travel to a terminus and they will automatically change direction after arrival.
Terminus stations must have a separate set of directed outgoing edges (without cycles) that only reconnect with the main line after the train has passed through a reversing loop.

If using the Java API, `Point.setTerminus` and `Locomotive.setReversible` correspond to the JSON settings above.

# Reversing stations and parking

Stations can optionally be designated as "reversing" by setting `"reversing": true` on the corresponding `Point`. These will never be chosen in autonomous operation, rather only semi-autonomous operation where you pick the route for the locomotive to follow.

Reversing stations are intended for parking/shunting.  For example, if you want to park a train, create a reversing station ahead of the switch to the parking track, and create a reversing station at the parking track.  Then you can simply follow the route to the parking track.  The train will automatically be reversed at each reversing station it reaches.

If you want to designate a parking space without reversing functionality, simply change a station to a non-station. Paths that start from a non-station, or a reversing station, will never automatically be chosen.  When you want the locomotive to run again, manually trigger a path to another station, or change the point back to a station.

# Advanced layouts and settings

TrainControl's graph model can be used to automate layouts with complex designs and tons of switches. A more advanced example (automation JSON plus CS2 layout files) can be found in [cs2_sample_layout](cs2_sample_layout/config/)

Remember that everything described below can now be fully edited via TrainControl's graph UI! 

## Train lengths and non-atomic routes

You can specify the train length for any locomotive (via the optional `trainLength` integer JSON key), and the maximum allowed train length for a station (via the `trainLength` integer JSON key), for any locomotive entry within the `points` list. 
This will force the autonomous operation logic to account for the length of different trains.  When configured correctly, this can prevent long trains from stopping at short stations.  
A value of 0 for `maxTrainLength` is default, and disables length restrictions.  These values can also be set programmatically via the `Locomotive` and `Point` APIs.

If `atomicRoutes` is set to `false`, edges will be unlocked as the active train passes them, rather than at the end of each path.  
This may make operation more fun/fast-paced, as new routes will start earlier, at the expense of a more complex graph configuration.
To ensure that potential collisions are avoided, each edge must be configured with a length.  
Edges will only be unlocked once the cumulative traversed edge length exceeds the current train's length.  A length value of 0 for any edge disables this functionality and will result in instant unlocks.
Note that lock edges, which should be used for any overlapping/crossing tracks, will never be unlocked early.

## Path selection logic

Paths are selected at random from among the possible stations reachable by any given locomotive, with the following conditions:
    - The shortest path is preferred unless it is occupied or locked
    - A path with conflicting accessory commands will never be chosen 
    - You can specify an optional integer `priority` for any station.  Stations with higher priorities will always be chosen over ones with a lower priority unless they are occupied.

You can optionally mark any point as inactive (`"active" : false`).  Automatically chosen paths will never include inactive points.  However, they can still be accessed in semi-autonomous (point-to-point) operation.

## Pace of operation

`minDelay` and `maxDelay` specify the minimum and maximum delay, in seconds, between locomotive activations.  
The actual value is randomly chosen in this range, and this replaces the need for manual definitions in callbacks. 

Locomotives inactive longer than `maxLocInactiveSeconds` seconds will be prioritized until they get a chance to run. Set to 0 to disable.

## Functions (sounds / lighting)

TrainControl will enable/disable each locomotive's preferred functions, if any, before departure and upon arrival, respectively.  

These preferred functons are set by right-clicking on any keyboard button in the Locomotive Control tab of the UI, and are automatically saved.  Therefore, they cannot be specified in the autonomy JSON.
However, if you want to avoid turning on the fuctions on departure / turning off the functions on arrival, respectively, you can set `turnOnFunctionsOnDeparture` / `turnOffFunctionsOnArrival` to `false`.  A good reason to use these settings is to is to keep operating sounds / lights on between paths in the case of the latter,
 or to skip running sound altogethr in the case of the former.

Unless the `speed` is specified within the `loc` array, each locomotive's preferred speed will be used (as set in the TrainControl UI).  If neither is set, the program will revert to `defaultLocSpeed`.
The optional `arrivalFunc` and `departureFunc` function numbers will be toggled when the locomotive is about to reach its destination and about to depart, respectively.  All these settings can be changed
by right-clicking on any point within the graph UI.

## Speed adjustments

From v2.4.8, the `speedMultiplier` setting on any `Point` will adjust the speed of the incoming locomotive by the set value.

The allowed range is 0.1-2.0, with 1.0 (no change to the speed) being the default.

## Ensuring network stability 

From v2.4.7, the `maxLatency` setting can be used to configure a network latency threshold (in milliseconds). 

If set above 0, whenever the measured network latency between your computer and the Central Station exceeds the threshold, the power will automaticlly be turned off.

The lowest allowed nonzero threshold is 100ms.

# Timetables

From v2.1.0, TrainControl provides a timetable feature which can be accessed from within the autonomy tab.  This features allows the capture and subsequent execution of a predetermined sequence of (valid) paths by specific locomotives.  Timetables are stored in the autonomy JSON file and can thus be saved for later use.

To record a timetable path, a valid graph must be loaded.  Then, press the `Capture Locomotive Commands` button and either start autonomous operation, or issue semi-autonomous locomotive commands manually.  Note that in the latter case, you should ensure that all required points are marked as active, since inactive points can be traversed in semi-autonomous mode, but not in timetable/fully autonomous mode.  Once you are finished, press the capture button again to un-toggle, and then begin execution by pressing `Execute Timetable`.  It is recommended to have locomotives end where they started.  This way, timetables can be continuously executed.  The `Graceful Stop` button can be used to safely pause timetable execution.

The time between paths will be recorded and replayed.  Timetables can also be built programmatically via `Layout.addTimetableEntry` or `Layout.setTimetable`.

# Locomotive exclusions

From v2.1.5, you can prevent locomotives from stopping at a given station by adding them to the list within the 
`excludedLocs` JSON key on any `Point`.  This can also be set by right-clicking the station in the graph UI. Note that exclusions on stations only apply in fully autonomous operation, so locomotives can still be directed to these stations in semi-autonomous operation, and they can still pass through them.  

However, if you set an exclusion on a non-station, the excluded locomotives will never be able to traverse such points on any path.

# Displaying locomotive locations on track diagrams

From v2.4.9, if you want locomotives to also show up on the track diagram (not just in the graph UI) as they move around, simply create a text label with the value `"Point:StationName"`, where StationName corresponds to the name of the point at that location.  If a locomotive is present at that point, its name will be shown in the label.

# Locomotive concurrency

From v2.4.11, use the `maxActiveTrains` preference to control the maximum number of trains that will run concurrently in full autonomy mode.  Set to 0 to allow unlimited trains.