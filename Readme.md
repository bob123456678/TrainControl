# Marklin Central Station 2/3 Train Control

This cross-platform Java program allows you to use your computer to easily control your entire Marklin / Trix digital layout.
It connects to a Central Station 2, 3, or 3 Plus over the network.
It is primarily designed for users with a large layout and many locomotives, as
the standard Marklin UI makes many common tasks (such as quickly switching between locomotives)
overly tedious.  Convenient keyboard hotkeys are available for controlling locomotives, switching between locomotives, 
enabling functions, emergency stop, etc.  You can also open windows with interactive track diagrams.

As such, TrainControl is designed to be a complete replacement for the CS2/CS3 for
operating your layout, with the Central Station serving solely as the track interface
and locomotive database.

Under the hood, this program implements the Marklin CAN protocol and can therefore
also be used to fully automate a layout.  Layout and locomotive information is automatically
downloaded from the CS2/CS3, currently with some layout limitations on the CS3 (see below).

## Overview

**Main UI**

You can assign locomotives to any letter on the keyboard, then quickly switch between them.  Easy keyboard shortcuts let you control locomotives.  Thumbnails are automatically downloaded from the CS2/CS3.

![UI screenshot: locomotive control](assets/ui_main.png?raw=true)

Right-click a locomotive to change it or set additional options, such as preferred speed or functions.

![UI screenshot: locomotive control](assets/ui_right_click.png?raw=true)

![UI screenshot: locomotive control](assets/ui_sel_loc.png?raw=true)

**Layout View**

Layouts are downloaded automatically from the CS2, or configurable manually via a layout file with the CS3.  All components (switches, signals, S88, routes) are clickable and reflect the layout state.  Multiple pages can be opened across unlimited popup windows.

![UI screenshot: layout](assets/ui_layout.png?raw=true)

**Keyboard**

Useful for testing, individual accessories can be directly controlled via their digital address.

![UI screenshot: layout](assets/ui_keyboard.png?raw=true)

**Routes**

Conditional routes can be defined for semi-automatic layout operation, such as setting a switch to guide an incoming train to an unoccupied station track.  Manual routes can also be defined and activated directly or via the layout tab.

![UI screenshot: layout](assets/ui_route.png?raw=true)

**Full Autonomy**

Defined via a special [JSON configuration file](src/examples/Readme.md), represent your layout as a graph and enable complete automation of trains using just S88 sensors and an initial list of locomotive locations.  You can pick destinations for specific trains, or let the system continuously execute random routes.

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
* View S88 feedback
* Progammatic layout control via Java API (uses CAN protocol - [see documentation](src/examples/Readme.md)) 
* (From v1.8.0) [Graph model](src/examples/Readme.md) w/ JSON configuration for dynamic layout modeling and fully autonomous train operation

**Requirements:**

* Requires a Marklin Central Station 2 or Central Station 3 connected to your network
* Must connect to the same network as the CS2/CS3 (Wi-Fi or ethernet)
* CS2/CS3 CAN bus and broadcasting needs to be enabled in the settings

**Limitations:**

* Automatic layout download only works with CS2, not CS3 (static layout files can be used with CS3 if desired)
* Central Station IP address must be manually entered (recommend configuring a static IP in your router)

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

Expanded support for CS3 layouts is planned for the future, and a program to generate/edit CS2 layout files is coming soon!

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

* v1.8.11 [Beta]
    - Points and edges are now sorted alphabetically in generated autonomy JSON
    - 0 train lengths will be excluded from generated autonomy JSON
    - Added right-click option to rename graph nodes
    - Added right-click option to change a station node to a terminus station
    - Added option to toggle the main TrainControl window being always on top
    - Added options to add/delete nodes and edges to/from the graph
    - Added interface to edit lock edges and commands for any edge
    - Added button to load empty JSON to enable building a graph from scratch
    - Improved coordinate accuracy in exported JSON after nodes are moved around
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

