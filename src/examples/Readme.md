# Automating your layout

Beyond its GUI, the TrainControl software can be used to progammatically control your Marklin layout, and even fully automate it.  This means that you can specify exactly how and when you want your trains, switches, signals, and accessories to behave.

## API

Before jumping into layout automation, it is important to understand the basics of the TrainControl Java API.  
The `MarklinControlStation` class is your gateway to the CS2/CS3's database of locomotives, switches, signals, and accessories. Several examples are provided in `ProgrammaticControlExample.java`, where it is initialized and accessible via the `data` variable.

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
    layout.createEdge("Station 2", "Main Track", (control) -> {control.getAccessoryByName("Signal 2").green();});
    layout.createEdge("Station 1", "Main Track", (control) -> {control.getAccessoryByName("Signal 1").green();});

    layout.createEdge("Main Track", "Pre Arrival", null);

    layout.createEdge("Pre Arrival", "Station 1", (control) -> {control.getAccessoryByName("Switch 10").turn(); control.getAccessoryByName("Signal 1").red();});
    layout.createEdge("Pre Arrival", "Station 2", (control) -> {control.getAccessoryByName("Switch 10").straight(); control.getAccessoryByName("Signal 2").red();});

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

# Advanced layouts

This graph model can be used to automate layouts with complex designs and tons of switches.  More advanced examples are coming soon!