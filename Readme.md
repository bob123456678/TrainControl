# TrainControl for Marklin Central Station 2 and 3

This powerful cross-platform Java program allows you to use your computer to easily control your entire Marklin / Trix / DCC digital layout.
It connects to a Central Station 2, 3, or 3 Plus over the network.
It is primarily designed for users with a large layout and many locomotives, as
the standard Marklin UI makes many common tasks (such as quickly switching between locomotives)
overly tedious.  Convenient keyboard hotkeys are available for controlling locomotives, switching between locomotives, 
enabling functions, smooth deceleration, emergency stop, etc.  You can also open windows with interactive track diagrams and edit track diagrams.

As such, TrainControl is designed to be a complete replacement for the CS2/CS3 for
operating your layout, with the Central Station serving solely as the track interface
and MFX locomotive database.

Under the hood, this program implements the Marklin CAN protocol and can therefore
also be used to programmatically control the entire layout.  Layout and locomotive information is automatically
downloaded from the CS2/CS3, currently with some layout limitations on the CS3 (see below).

TrainControl also provides a UI for creating a graph model of your layout, 
which enables tracking train locations for *fully autonmous* operation at the push of a single button,
as well as semi-autonmous point-to-point operation between stations. You can of course also set up traditional/conditional routes.

## Overview

**Main UI**

You can assign locomotives to any letter on the keyboard, then quickly switch between them.  Easy keyboard shortcuts let you control locomotives.  Thumbnails are automatically downloaded from the CS2/CS3.

![UI screenshot: locomotive control](assets/ui_main.png?raw=true)

Right-click a locomotive to change it or set additional options, such as preferred speed or functions.

![UI screenshot: locomotive control](assets/ui_right_click.png?raw=true)

![UI screenshot: locomotive control](assets/ui_sel_loc.png?raw=true)

**Layout View**

Track diagrams for your layout are downloaded automatically from the CS2, or configurable manually via local layout files if using the CS3.  All components (switches, signals, S88, routes) are clickable and reflect the layout state.  Multiple pages can be opened across unlimited popup windows.

On Windows, you can also edit local track diagrams via a bundled app.

![UI screenshot: layout](assets/ui_layout.png?raw=true)

**Keyboard**

Useful for testing, individual accessories can be directly controlled via their digital address.

![UI screenshot: layout](assets/ui_keyboard.png?raw=true)

**Routes**

Conditional routes can be defined for semi-automatic layout operation, such as setting a switch to guide an incoming train to an unoccupied station track.  Manual routes can also be defined and activated directly or via the layout tab.

![UI screenshot: layout](assets/ui_route.png?raw=true)

**Full Autonomy**

Defined via a special [JSON configuration file](src/examples/Readme.md), represent your layout as a graph and enable complete automation of trains using just S88 sensors and an initial list of locomotive locations.  TrainControl will automatically keep track of where each train is located at any given time.  You can pick destinations for specific trains, or let the system continuously execute random routes.

![UI screenshot: autonomy](assets/ui_autonomy.png?raw=true)

The graph UI will show you which routes are active, which edges are locked, and where different trains are stationed.  This can also help you debug your graph as you build it.  While trains are not running, you can right-click any station to reassign a train.

<img src="assets/graphview.png?raw=true" alt="UI screenshot: autonomous graph visualizer" width="500">

## Features

* Easily control locomotives (mm2, mfx, dcc), signals, switches, and routes
* View and interact with layout diagrams, with support for multiple windows
* Configure up to 8 different key mappings for up to 208 locomotives
* Convenient hotkeys for power off, emergency stop, and smooth deceleration
* Set up automatic and conditional routes triggered by S88 feedback modules
* Automate bulk tasks such as turning off all functions
* Download locomotive, layout, and route information from the CS2/CS3
* Set function and speed presets for locomotives
* View S88 feedback
* Progammatic layout control via Java API (uses CAN protocol - [see documentation](src/examples/Readme.md)) 
* (From v1.8.0) [Graph model](src/examples/Readme.md) w/ JSON configuration for dynamic layout modeling and fully autonomous train operation
* (From v1.8.0) Semi-autonomously operate trains simply by clicking the destination station (when graph model is enabled)
* (From v1.9.0) Full UI for editing autonomy graph models
* (From v1.10.0) Customize autonomous operation by setting station priority, maximum train lengths, edge lengths, and maximum train idle time
* (From v1.11.0) UI for editing track diagrams (Windows only)

**Requirements:**

* Requires a Marklin Central Station 2 or Central Station 3 connected to your network
* Must connect to the same network as the CS2/CS3 (Wi-Fi or ethernet)
* Important: CS2/CS3 CAN bus and broadcasting needs to be enabled in the settings

**Limitations:**

* Automatic layout download only works with CS2, not CS3 (local layout files can be used with a CS3 if desired)
* Central Station IP address must be manually entered the first time you run TrainControl (recommend configuring a static IP in your router)

**Layouts and the CS3**

This program was originally written to import and display layouts created/configured from within the CS2.
Because the CS3 uses a different layout file format than the CS2, this program does not currently support displaying CS3 layouts.  Also, the CS3 has its own web-based UI which can also be used as an alternative.

However, even when using a CS3, you can view CS2 layouts in this program as follows:
- Create an empty folder on your PC
- From your CS2, export `/config/gleisbild.cs2` and `/config/gleisbilder/*` to the new folder, maintaining the same subdirectory structure
- Start TrainControl and within the Settings tab, click on "Choose Local Data Folder", then select the path to your folder
- The static local layout will now be shown in the Layout tab

If you change the local files, clicking on "Sync with CS2" will update the layouts.  This effectively lets you customize the layout even without a CS2.  Some users might find this easier than inputting data into the CS3 UI.

Some sample files are included in the `cs2_sample_layout` folder.

From v1.11.0, if no CS2 is detected and no static layout is manually selected, TrainControl will automatically initialize a demo layout at startup.  A binary program (Windows-only) is bundled for complete editing support, and accessible via the "Edit" button within the Layout tab.

Expanded support for CS3 layouts is planned for the future.

## Running TrainControl

**Building the project from source:**

Requires JDK 1.8+ and the following libraries:

* org.json (json-20220924.jar) (from v1.6.0)
* org.graphstream (gs-core-2.0.jar, gs-algo-2.0.jar, gs-ui-swing-2.0.jar) (from v1.8.0)

```ant -f /path/to/project/ -Dnb.internal.action.name=rebuild clean jar```

**Running the application (build or release JAR):**

```java -jar TrainControl.jar [CS2 IP address]```

## Keyboard Commands / Key Mappings

* Letter keys (select a locomotive)
* Up/down arrow (speed up/slow down) (hold Alt to double the increment)
* Left/right arrow (change direction)
* Escape (power off/emergency stop)
* Alt+G (power on)
* 1 through 0 (set locomotive speed, 1 is stopped and 0 is max)
* Numpad 0/backquote/Alt+0 (toggle lights/F0)
* F1-F24 (toggle functions F1-F24)
* Numpad 1-9, Alt+1-9 (toggle functions F1-F9)
* Control+0-9 (toggle functions F10-F19)
* Control+Alt+0-9 (toggle functions F20-F19)
* Shift (slow stop)
* Spacebar (instant stop)
* Enter (stop all locs)
* Comma/period, Alt+Left/right arrow (cycle to previous/next loc page)
* Backspace/Alt+backspace (cycle through tabs)
* Plus/minus (cycle through keyboards and layout pages)
* Slash/question mark (cycle through function tabs on the loc panel)
* Alt+P (apply saved function preset for current loc)
* Alt+O (turn off all functions for current loc)
* Alt+S (save current functions as a preset for current loc)
* Alt+U (save current speed as a preset for current loc)
* Alt+V (apply saved speed preset for current loc)


## Changelog

* v1.11.0 [Beta] (New feature: Layout editor integration on Windows. Activate or deactivate points to customize autonomously operating trains / chosen routes.  Numerous stability enhancements.)
    - Points can now be marked as active or inactive
        - Inactive points will never be chosen within paths in autonomous operation
        - Locomotives on inactive stations will now be greyed out in the semi-autonomous operation UI
        - Added corresponding `active` JSON key within `points`
        - Added corresponding option to the right-click menu in the graph UI
    - Reverted behavior from v1.10.0 where all reversing stations were automatically treated as inactive
    - Graph UI improvements
        - The edge deletion option in the graph UI right-click menu will now prompt for an edge rather than listing all edges
        - Improved semi-autonomous operation UI (larger fonts, less scrolling)
        - Added option to hide all inactive points from the graph UI
    - Improved locomotive database synchronization with CS2/CS3
        - Locomotives with the same name and decoder type, but a different address in TrainControl, will now have their address automatically updated to match the Central Station
        - Changes to locomotive functions are now automatically synchronized
        - Fixed a bug where a shadow copy of a locomotive with the same name (but a different address) could exist in TrainControl's database
        - The "Sync w/ Central Station" right-click option will now also update the locomotive's address/functions/icon
    - Improved the autonomy JSON UI
        - Added option to load graph JSON from a file (to make managing presets easier)
        - Added option to save graph JSON to a file
    - Integrated a layout editor app to allow for the editing of track diagrams (Windows only)
        - Added an edit button to each layout page; this automatically opens the editor
        - A basic starting layout will automatically be loaded if no CS2 is detected and no layout path has been manually specified
        - Added button to the Tools tab to initialize an empty layout on demand
    - Custom icons can now be chosen for locomotives, even if no icon is selected in the Central Station
    - Track diagram improvements
        - Added support for page links (pfeil) which change the active diagram page when clicked
        - Updated overpass track diagram icons
        - Added a button to revert to the CS2 layout when currently using a local layout
        - Fixed UI errors when TrainControl was run without a layout
        - Fixed bug where empty rows/columns in layouts were not rendered correctly
        - Padding at the top/left of the layout is now rendered, consistent with what is shown in CS2 track diagrams
        - Text labels will now be rendered on any tile with a .text property
    - Different instances of TrainControl will now use unique track diagram and IP preferences
    - Fixed bug where orphan feedback IDs could become undeletable in TrainControl's database

* v1.10.11 [10/15/23]
    - New graph nodes are now created near the cursor instead of the lower-left corner of the window
    - Double-clicking a station node is now a shortcut to opening the locomotive assignment window
    - Clicking "mark as terminus station" on a non-station will now automatically convert the point to a station first
    - When adding or editing locomotives on the graph, the locomotive list is now automatically focused for easier selection
    - Added a pop-up error message if an invalid layout file path is chosen via the "Choose Local Data Folder" button within Tools
    - Fixed a bug in the layout UI where wide text labels in the last column would sometimes lead to misaligned tracks

* v1.10.10 [10/9/23]
    - Added a button within the autonomy settings tab to bulk clear all locomotives from the graph
    - The "hide reversing station" option is now remembered after exiting the program
    - The "hide reversing station" option will now also hide all points only connected to/from reversing stations
    - Significantly improved UI startup speed when the CS3 has a large locomotive database
    - Added support for reversing stations without an S88
    - The UI for adding/editing locomotives on the graph will now only show functions available to the chosen locomotive
    - Fixed UI function icon alignment
    - Fixed maximum function counts: up to F28 for DCC and F31 for MFX (down from F32 for both)
    - Fixed incorrect locomotive label background color from v1.10.8
    - Fixed minor UI bug where the keyboard mapping page shown for the currently active locomotive was always the currently selected page, instead of the page with the active button

* v1.10.9 [10/5/23]
    - Added an option to copy existing graph edges (to a new start or end point)
    - The "Edit s88" option will now be shown for all types of points in the graph UI, not just stations
    - Paths in the semi-autonomous UI are now sorted alphabetically
    - Locomotives in the semi-autonomous UI are now sorted alphabetically; parked locomotives are always at the end
    - The pause after arriving at a reversing station is now randomly chosen between minDelay and maxDelay (was 1 second)
    - Fixed bug where highlighted edges in edge edit mode would not be cleared after entering an invalid switch/signal command

* v1.10.8 [10/3/23]
    - At locomotive startup, the last-known direction is now re-transmitted to ensure consistent operation
    - The date each locomotive was last run is now tracked in the usage report
    - Fixed alignment of key mapping labels for long locomotive names
    - Fixed bug where the UI would fail to start up when no autonomy.json file existed
    - Fixed bug from v1.10.7 where the locomotive selection window would freeze after a locomotive was deleted from the database
    - Fixed minor bug from v1.10.7 where the locomotive selection tooltips would not update after copy/pasting between the keyboard mappings
    - The "Q" button will no longer default to the first locomotive to the database as long as any other key is mapped

* v1.10.7 [9/30/23]
    - Improvements to the locomotive selector window 
        - Added a tooltip depicting the current key mapping(s) to each locomotive tile
        - Currently mapped locomotives will be shown in bold
    - Added basic tracking of locomotive usage (run time); button available in the Tools tab
    - Added option to change the ID of an existing route
    - The locomotive direction buttons will now resend the direction command when pressed in the locomotive's current direction

* v1.10.6 [9/24/23]
    - If no CAN messages are received within the first 15 seconds after startup, a reminder pop-up will be shown stating that broadcasting must be enabled in CS2/CS3 settings
    - Upon arriving at a terminus, added a delay before locomotives switch direction to allow for smooth deceleration

* v1.10.5 [8/20/23]
    - UI enhancements to eliminate the need for manual JSON edits
        - Added UI tab for changing autonomy settings
        - Made autonomy interface more intuitive when creating a graph for the first time
    - Added setting to show/hide reversing stations in the graph UI

* v1.10.4 [8/16/23]
    - Lock edges are now highlighted when editing the graph in the UI
    - Improved clarity of path error log output in debug mode
    - Fixed issue where locomotives added to the graph via the UI would not fire departure/arrival functions until after JSON reload

* v1.10.3 [8/14/23]
    - The recalculation of possible routes in semi-autonomous operation is now throttled to improve performance
    - Minor UI enhancements
        - The locomotive name field is now automatically focused (for easier filtering) when assigning a locomotive
        - Sped up scrolling of the autonomy tab when more than 6 locomotives are on the graph
        - Fixed intermittent issue where the layout diagram would be blank at application start
    - Fixed bug where non-station points with `"terminus" = false` in the JSON would erroneously be flagged as invalid
    - Fixed bug where edges with nonexistent points in the JSON would not show a clear validation error
    - Fixed bug where points with incoming edges could be deleted in the UI, which would result in orphan edges/invalid JSON

* v1.10.2 [8/11/23]
    - Fixed deadlock issue in autonomous operation (v1.9.5-v1.10.1)
    - Fixed stability issue: semi-automatic operation is no longer possible when s88 triggered routes are enabled
    - Fixed bug where disabling auto layout simulation/debug mode required restarting the application
    - Improved auto layout simulation/debug mode
        - Feedback events are now simulated directly
        - Simulation can now only be enabled when no CS2 is connected

* v1.10.1 (Beta)
    - Improved the display of each locomotive's current station in the autonomy tab
    - The "validate JSON" button will now ask for confirmation in case the graph state has been edited
    - Optimized UI performance (removed several UI actions from the main thread)
    - Fixed bug where the "start autonomous operation" button would remain greyed out after closing the graph window

* v1.10.0 (Beta) (New feature: reversing points for one-click parking & station priority)
    - Added `reversing` as a possible point type.  These points or stations are used for shunting and will reverse arriving trains.  They can be traversed only through a manually triggered path and will never be chosen in autonomous operation.
    - In autonomous operation, locomotives inactive for longer than `maxLocInactiveSeconds` seconds will now be prioritized (set to 0 to disable)
    - In autonomous operation, locomotives placed on non-stations via the UI will no longer be started automatically.  This allows the use of such points as designated parking spots even if they are not reversing stations.
        - Stations can now be converted to non-stations in the UI even when they are occupied
        - JSON with locomotives on non-stations will now be considered valid
    - Added a `priority` setting for stations in JSON and the UI.  In autonomous operation, free stations with a higher priority will always be chosen over ones with lower priority.
    - Improved locomotive semantics via a `loc` object within `points` in the autonomy JSON.  Old keys will now be ignored with a warning.
    - Improved error messages for JSON point validation
    - Improved reliability of saved function presets for certain decoders
    - Fixed minor bug: Newly added pre-arrival functions set via the UI will now fire without the need to reload the autonomy JSON 

* v1.9.5 (Beta)
    - Added verbose logging of auto layout locomotive speed changes
    - Log messages related to occupied/invalid paths will now only be shown in debug mode (pass `debug` after IP address)
    - Added `atomicRoutes` setting in JSON (default of `true` yields same behavior as v1.9.4 and earlier).  When set to `false`, edges will be unlocked as trains pass them, instead of at the end of a path, for a more dynamic operating experience.  
    - Added edge length setting to JSON and UI.  To avoid collisions when `atomicRoutes` is `false`, length values should be set for all edges and trains.
    - Fixed bug where edges without commands could not be edited in the UI

* v1.9.4 [7/25/23]
    - Fixed race condition where multiple locomotives starting at the same time could lead to some switches not being set correctly
        - Java API for setting configuration commands on an edge has been revised: callback lambdas no longer required or supported.
        - Added support for method chaining when programmatically defining Points and Edges
    - Added validation of Signals/Switches with duplicate addresses to autonomy JSON parser
    - Synchronizing with the CS2 will now invalidate the auto layout state as a precaution and require a reload

* v1.9.3 (Beta)
    - Fixed bug where existing edges without any commands would not execute config commands after the first time they were edited in the UI
    - Fixed bug where keyboard events would not be registered when the "always on top" checkbox was unchecked at startup
    - Added 150ms interval between autonomy config commands for better stability
    - Improved accessory event logging

* v1.9.2 (Beta)
    - Manual changes to S88 state via the UI will now dynamically update the displayed autonomous path options
    - S88 events from the Central Station for sensors visible in the layout UI will now dynamically update the displayed autonomous path options 
    - Made path strings in log output more concise
    - Fixed potential issues if a locomotive was renamed during autonomous operation 
    - Fixed potential race condition related to possible paths shown in log output

* v1.9.1 [7/21/23]
    - Autonomous operation can no longer be started if the track power is off (to avoid switch/signal state inconsistencies)
    - On exit, autonomy state auto-save will no longer be attempted if any trains are running.  A confirmation dialog has been added.

* v1.9.0 [7/20/23] (New feature: full UI for editing autonomy graphs)
    - Added button to load empty JSON to enable building an autonomy graph from scratch
    - Made it possible to create / fully edit autonomy graphs via right-click menus in the graph UI
        - Added option to rename graph nodes (changes propagate to edges)
        - Added option to change a station node to a terminus station
        - Added option to toggle station status
        - Added option to set s88 address
        - Added options to add/delete nodes and edges to/from the graph
        - Added interface to edit lock edges and commands for any edge
    - Added checkbox to toggle the main TrainControl window being always on top
    - Added checkbox to auto-save autonomy graph state on exit (applies only if autonomous operation was activated)
    - Improved coordinate accuracy in exported JSON after nodes are manually moved
    - Improved parsing (error handling) of edge configuration commands
    - Points and edges are now sorted alphabetically in the generated autonomy JSON
    - 0 (defualt) train lengths will be excluded from generated autonomy JSON keys, for brevity
    - Fixed bug where terminus station status was not exported in generated autonomy JSON

* v1.8.10 [7/17/23]
    - Added `maxTrainLength` setting on `Point`s, and `trainLength` setting on `Locomotive`s, to enable the user to disallow long trains from stopping at short stations during autonomous operation
    - Added JSON keys and graph UI options to edit train length and set an optional maximum train length on any station

* v1.8.9 [7/10/23]
    - Fixed bug from v1.8.0 where clearing a mapped keyboard button would fail

* v1.8.8 [6/10/23]
    - Fields in exported JSON now have a predicatable order
    - Added `preArrivalSpeedReduction` JSON key to control speed reduction prior to arriving to station (default 0.5, or 50% reduction)
    - Added timestamps to standard output log

* v1.8.7 [5/30/23]
    - The "Validate JSON / Stop Locomotives" button will now forcefully terminate all running locomotive commands
    - Added option to completely remove a locomotive from the graph
    - Added option to add an entirely new locomotive to the graph and set its functions
    - Added options edit locomotive arrival/departure functions, speed, and reversible status via the UI (note: change in the JSON keys for reversible locomotives and speed, see documentation)

* v1.8.6 [5/29/23]
    - Added button to export current graph state in JSON format
    - Added button to request a graceful stop of autonomous operation (active locomotives will stop at their next station)
    - Fixed bug from v1.8.5 where execution would fail if any locomotives were removed from the graph via the UI

* v1.8.5 [5/28/23] (Beta)
    - Added right-click menus to graph UI.  Locomotives can now be moved/removed from stations without editing the JSON.
    - Miscellaneous refactoring to support future JSON export / editing of graph via UI

* v1.8.4 [5/27/23]
    - Terminus stations are now drawn as a square
    - Clarified lock edge route debug output

* v1.8.3 [5/25/23]
    - Final edge is now correctly highlighted in green prior to auto route completion
    - Improved autonomy JSON error handling

* v1.8.2 [5/23/23]
    - Improved auto layout appearance (colors and station shape)
    - Made log window focusable
    - Improved logging of conflicting autonomous path information

* v1.8.1 [5/20/23]
    - Improved path selection logic in autonomous operation
        - Alternative paths are now checked if the shortest path is blocked
        - The chosen path is randomized if there are multiple options of the same length
    - Added current station information to autonomy UI when a locomotive has no available paths
    - Autonomy UI bug fixes
    - Added ability to manually specify x/y node coordinates in auto layout JSON
        - Nodes can be moved around using the mouse if all coordinates are specified
        - Pressing the `C` key in the graph UI will list all of the current coordinates

* v1.8.0 [5/17/23] (New feature: autonomous operation)
    - Added JSON-based layout autonomy configuration interface
    - Added ability to run fully autonomous operation via the UI
    - Added GraphStream UI to monitor autonomous operation
    - Added controls to execute locomotive paths based on start and end station
    - Added basic support for terminus stations and reversible trains
    - Autonomous operation: fixed issue where occupied (but not active) lock edges would not properly invalidate a conflicting route

* v1.7.5 [4/30/23]
    - Route conditions are now parsed from the CS2 file
    - Fixed occasional UI bug with right-click route menus
    - Decoder type is now displayed next to the active locomotive name
    - Added button under "Tools" to check for duplicate MM2/DCC addresses

* v1.7.4 [4/22/23]
    - Route command order is now preserved
    - Round command delays are now parsed from the CS2 file and editable in the UI
    - Duplicate accessory commands are now allowed in routes

* v1.7.3 [4/16/23]
    - Added wizard to simplify new route creation
    - Improved value sanitization in routes (negative numbers, etc.)

* v1.7.2 [4/14/23]
    - Added swap option when copy/pasting locomotives
    - Fixed race condition when quickly switching between layouts

* v1.7.1 [4/10/23]
    - Corrected potential route name duplication issue when importing routes from CS2

* v1.7.0 [4/9/23] (New feature: advanced routes)
    - Routes can now automatically trigger when a specified S88 sensor sends feedback
    - Routes can further be configured with required conditions based on one or more other S88 sensors
    - Route S88 sensors and trigger types are now read from the CS2
    - Added bulk enable/disable option for automatic routes
    - Automatic routes are now highlighted
    - Consolidated route editing in a single window
    - Increased number of locomotive pages to 8

* v1.6.14 [4/4/23]
    - Route icons in the layout remain highlighted until the route finished executing
    - Added pointer icon for clickable layout components

* v1.6.13 [4/2/23]
    - Added hotkey ("/" / "?") for cycling through function tabs
    - Added hotkey (Control+0) for F10
    - Added hotkeys (Control+Alt+0-9) for F20-29
    - Fixed race condition / icon rendering when switching between locomotive mapping pages

* v1.6.12 [3/15/23]
    - Fixed tooltips for 3-way switches (addresses were off by 1)
    - Fixed parsing of routes that include 3-way signals (e.g., signal_f_hp012)
    - Added right-click menu to simplify editing/deleting routes
    - Added option to duplicate routes

* v1.6.11 [3/2/23]
    - Added option to sort routes by name
    - Fixed bug preventing the use of route buttons for odd route IDs

* v1.6.10 [2/27/23]
    - Fixed layout rendering issue for long text labels (those exceeding ~3 letters)
    - Added option to edit routes
    - Added tooltips for clickable routes

* v1.6.9 [2/19/23]
    - Routes can now be added manually via the Route tab
    - Routes updated on the CS2 will now automatically be updated in the UI after synchronization
    - Added borders between route labels

* v1.6.8 [1/2/23]
    - New hotkeys: Control+1-9 to control F11 to F19
    - New hotkeys: Alt+left/right arrow to cycle through locomotive mappings (same as comma/period)
    - Improved UI performance on slower systems

* v1.6.7 [12/8/22]
    - Fixed orientation of semaphore signals in CS2 layout files
    - Corrected minor bug in parsing CS2 layout files: element order no longer matters

* v1.6.6 [11/6/22]
    - Added support for DCC locomotives

* v1.6.5 [11/5/22]
    - Added option for sliders to also change the active locomotive
    - Alt+Up/Down now doubles the size of the speed increment
    - Added code example for feedback driven switch/signal events

* v1.6.4 [10/21/22]
    - Added speed sliders below each keyboard mapping button for quick control
    - Double right-click slider to change direction

* v1.6.3 [10/16/22]
    - Locomotive selector now automatically opens when an unassigned button is selected
    - Better CS3 compatibility: locomotive DB is now read from CS3 API instead of `/config/lokomotive.cs2`.  Locomotive function icons and function types will be correct.
    - Hotkey tooltips in right-click menus

* v1.6.2 [10/15/22]
    - Added hotkeys for saving and applying function presets (Alt+P, Alt+S)
    - Added hotkey for turning off current loc's functions (Alt+O)
    - Added option to save/apply a preferred speed for each locomotive (Alt+U, Alt+V)
    - Minor UI bug fixes

* v1.6.1 [10/13/22]
    - Redesigned locomotive selection UI and moved all editing options to right-click menu
    - Larger locomotive icons and more functions visible at once
    - Added option to save/restore a function state preset for each locomotive
    - Added button to query every loc's function status from the Central Station
    - Fixed lag when switching between locomotive pages
    - Fixed loading of locomotive icons with special characters

* v1.6.0 [10/2/22] (Beta, new feature: CS3 and DCC support)
    - Tested basic functionality with CS3
    - Added ability to load layout files from the local filesystem (see further details under "Layouts with the CS3" above)
    - Added option to delete locomotives from the loc control UI

* v1.5.11 [09/17/22]
    - Added address info tooltips to the layout UI

* v1.5.10 [08/24/22]
    - Added support for multiple locomotives with the same address (CS2 UID + Name now uniquely identifies a locomotive)
    - Improved layout UI: buttons for small and large layout pop-up
    - Improved layout UI: button for opening all layout pop-ups at once

* v1.5.9 [04/21/22]
    - Moved locomotive sync option to right-click menu
    - Bulk operations no longer lock up the UI
    - Added one click option to copy locomotive to next page

* v1.5.8 [04/20/22]
    - Added right-click menu to all locomotive buttons
    - Locomotives can now be copy-and-pasted between buttons

* v1.5.7 [01/24/22]
    - Added self-contained initialization function
    - Cleaned up examples and documentation
    - Added [API & automation readme/tutorial](src/examples/Readme.md)

* v1.5.6 [01/17/22]
    - Function icons will now correctly be shown for F17-F32
    - Added support for multi-unit locomotives
    - API can now be used without opening UI
    - Central Station UID is now extracted over the network
    - Automation API: added support for mutually exclusive graph edges

* v1.5.5 [01/12/22]
    - Implemented basic classes/graph API for autonomous train operation
    - Improved S88 icons
    - Added support for S88 "double curve" layout icons
    - Fixed "sticky feedback" bug triggered by flickering feedback signals
    - Improved reliability of Locomotive S88 events

* v1.5.4 [01/02/22]
    - Improved layout rendering performance
    - Added (simulated) instant stop support for MM2 locomotives
    - Accessory type changes in the CS2 are now automatically synchronized
    - Added parsing support for locomotives with no "addresse" field in the CS2 file
    - Fixed bug when parsing layouts with a component at 0,0
    - Added alerts for locomotives with duplicate MM addresses
    - Prevent renaming a locomotive to an existing name

* v1.5.3 [12/25/21]
    - Alt-G is now mapped to the "go" button (turns on the power, was F2 prior to v1.4.0)
    - Added forward and reverse labels to loc direction buttons
    - Fixed layout display bug when multiple copies of the same accessory were present
    - Added delay to three-way turnout switch commands
    - Prettified crossing, tunnel, and decoupler icons
    - Added basic support for various new signal and lamp types

* v1.5.2 [12/20/21]
    - Connection will no longer fail upon encountering text labels in the layout
    - Support for new layout components
        * Y switches
        * Clickable routes
        * Text labels

* v1.5.1 [12/19/21]
    - Connection will no longer fail upon encountering unknown layout objects
    - Added turntable layout icon
    - Added lamp layout icon
    - Fixed a possible crash if an S88 event fires without existing on any layout

* v1.5.0 [12/13/21] (New feature: better CS2 compatibility)
    - Function types are now read from the CS2 file
    - Automatic recognition of pulse/momentary functions
    - Added function icons to UI
    - Extra function tabs are now hidden for mm2 locomotives
    - Removed "stop" button in locomotive pane

* v1.4.6 [12/10/21]
    - Added 200ms delay between route commands

* v1.4.5 [12/9/21]
    - Fixed incorrect thumbnails for locomotives with the same MM2 address
    - Added support for double slip switches in layouts
    - Added support for less common Marklin layout components
    - Removed unwanted focusable elements in UI

* v1.4.3 [11/13/21]
    - Fix bug affecting locomotive thumbnail refreshes
    - Swapped mislabeled keyboard buttons 28/29

* v1.4.0 [11/5/21] (New feature: easier layout control)
    - Support for viewing layouts in (unlimited) pop-up windows
    - Added function hotkeys (F1-F16)
    - Mapped `~` to F0
    - `,` and `.` hotkeys for switching between locomotive pages are now active across all tabs
    - Changed keyboard pagination hotkeys to `-` and `+`

* v1.3.2
    - First public release

