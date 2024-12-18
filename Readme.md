# TrainControl for Marklin Central Station 2 and 3

This powerful, free, cross-platform Java program allows you to use your computer to *easily* control your entire Marklin / Trix / DCC digital model train layout.
It connects to a Central Station 2, 3, or 3 Plus over the network.
It is primarily designed for users with a large layout and many locomotives, as
the standard Marklin UI makes many common tasks (such as quickly switching between locomotives or triggering functions)
overly tedious.  Convenient keyboard hotkeys are available for controlling locomotives, switching between locomotives, 
enabling functions, function presets, smooth deceleration, emergency stop, etc.  You can also open windows with interactive track diagrams and edit track diagrams.

As such, TrainControl is designed to be a complete replacement for the CS2/CS3 for
operating your layout, with the Central Station serving solely as the track interface
and MFX locomotive database.  If your existing controller is taking the fun out of running your trains, consider trying TrainControl!

Under the hood, this program implements the Marklin CAN protocol and can therefore
also be used to programmatically control the entire layout ([see API](Automation.md)).  Layout and locomotive information is automatically
downloaded from the CS2/CS3, currently with some layout limitations on the CS3 (see below).

TrainControl also provides a UI for creating a graph model of your layout, 
which when paired with S88 sensors, enables tracking train locations for *fully autonomous* operation at the push of a single button,
as well as semi-autonmous point-to-point operation between stations. You can of course also set up traditional/conditional routes to 
automate switches while operating trains manually.

## Overview

**Main UI**

You can assign locomotives to any letter on the keyboard, then quickly switch between them.  Easy keyboard shortcuts let you control locomotives.  Thumbnails are automatically downloaded from the CS2/CS3 or can be set manually.

![UI screenshot: locomotive control](assets/main23_2.png?raw=true)

Right-click a locomotive or function icon to change it or set additional options, such as preferred speed or function presets.

![UI screenshot: locomotive control](assets/ui_right_click.png?raw=true)

![UI screenshot: locomotive control](assets/ui_sel_loc.png?raw=true)

**Layout View**

Track diagrams for your layout are downloaded automatically from the CS2, or configurable manually via local layout files if using the CS3.  All components (switches, signals, S88, routes) are clickable and reflect the layout state.  Multiple pages can be opened across unlimited popup windows.

![UI screenshot: layout viewer](assets/layout23.png?raw=true)

From v2.0.0, on Windows only, you can also edit local track diagrams via a bundled app.

![UI screenshot: layout editor](assets/track_diagram_editor.png?raw=true)

<details>
<summary>Instructions for Managing and Importing Layouts</summary>

**Layouts and the CS3**

This program was originally written to import and display layouts created/configured from within the CS2.

Because the CS3 uses a different layout format than the CS2, this program does not support displaying native CS3 layouts. 
However, from CS3 v2.5.0, the CS3 now exports its Track Board layouts using the CS2 file format.  Support for such layouts is therefore available from TrainControl v2.2.0+, and they are automatically imported by default.
In some cases, you may need to use a double slip switch or a Y-switch from the "manual" menu in the CS3 to get tracks that cross over each other to render correctly.  You may also need to change certain straight tracks as the CS3 has a tendency to incorrectly connect tracks in the CS2 format.

If you have an older CS3 or don't want to use the CS3 Track Board layouts, you can view and edit layouts in this program as follows:

- If you have a CS2/CS3 with a layout, import your layout:
    - Create an empty folder on your PC
    - From your CS2/CS3, export `/config/gleisbild.cs2` and `/config/gleisbilder/*` to the new folder, maintaining the same subdirectory structure
    - Start TrainControl, then from the Layout menu, click on "Choose Local Data Folder", then select the path to your folder
    - The static local layout will now be shown in the Layout tab
- Otherwise
    - Start TrainControl, then from the Layout menu, click on "Initialize New Local Layout"
    - If no Central Station layout is detected and no static layout is manually selected, TrainControl will automatically initialize a demo layout at startup.

A binary program ([TrackDiagramEditor](https://github.com/bob123456678/TrackDiagramEditor), Windows-only) is bundled for complete editing support, and accessible via the "Edit" button within the Layout tab.  This will let you fully customize your layout.

If you change the local files, clicking on "Sync Database w/ Central Station" from the Locomotives menu will update the layouts.  This effectively lets you customize the layout even without a CS2/CS3.  Some users might find this easier than inputting data into the Central Station UI.

Some sample files are included in the `cs2_sample_layout` folder.

As the CS3 has its own web-based UI which can be used as an alternative, native support for CS3 layouts is currently under consideration.

---

</details>

**Routes**

Conditional routes can be defined for semi-automatic layout operation, such as setting a switch to guide an incoming train to an unoccupied station track.  Manual routes can also be defined and activated directly or via the layout tab.

![UI screenshot: layout](assets/ui_route.png?raw=true)

**Keyboard**

Useful for testing, individual accessories can be directly controlled via their digital address.  The cumulative number of actuations of each accessory is shown as a tooltip.

![UI screenshot: layout](assets/ui_keyboard.png?raw=true)

**Full Autonomy**

Defined via a special [JSON configuration file](Automation.md) that can be built using the UI, represent your layout as a graph and enable complete automation of trains using just S88 sensors and an initial list of locomotive locations.  TrainControl will automatically keep track of where each train is located at any given time.  You can pick destinations for specific trains, or let the system continuously execute random routes.  All state is auto-saved on exit.

![UI screenshot: autonomy](assets/ui_autonomy.png?raw=true)

The graph UI will show you which routes are active, which edges are locked, and where different trains are stationed.  This can also help you debug your graph as you build it.  While trains are not running, you can right-click any station to reassign a train and view possible routes.

<img src="assets/graphview.png?raw=true" alt="UI screenshot: autonomous graph visualizer" width="500">

In addition to the continuous automated operation and point-to-point commands, you can also specify timetables and run your trains according to a predefined list of paths, subject to the constraints and protections of the graph model.

<img src="assets/timetable.png?raw=true" alt="UI screenshot: timetable" width="500">

**Statistics**

Monitor the usage of different locomotives.

![UI screenshot: locomotive statistics](assets/stats23.png?raw=true)

## Features

* Easily control locomotives (MM2, MFX, DCC), signals, switches, and routes
* Download locomotive, layout, and route information from the CS2/CS3
* Customize locomotive icons and function icons without needing to set them in the CS2/CS3
* Powerful keyboard interface
    * Configure up to 10 different key mappings for up to 260 locomotives
    * Convenient hotkeys for power off, emergency stop, and smooth deceleration
    * Simultaneous operation across multiple PCs
* Track diagrams
    * View unlimited layout diagrams, with support for multiple windows
    * Toggle signals, switches, lights, uncouplers, and routes
    * View S88 feedback
    * Full UI for editing track diagrams (Windows only)
* Basic automation
    * Set up automatic and conditional routes triggered by S88 feedback modules
    * Automate bulk tasks such as turning off all functions
    * Set function and speed presets for locomotives
* Advanced automation
    * [Graph model](Automation.md) w/ JSON configuration for location tracking and fully autonomous train operation
    * Semi-autonomously operate trains simply by clicking the destination station (when graph model is enabled)
    * Full UI for editing autonomy graph models
    * Customize autonomous operation by setting station priority, maximum train lengths, edge lengths, and maximum train idle time
* Progammatic layout control via Java API (uses CAN protocol - [see documentation](Automation.md)) 
* Monitor locomotive usage stats

## Keyboard Commands / Key Mappings

TrainControl's key mappings are designed to allow you to send any command nearly instantly

* Primary controls
    * A-Z letter keys (select a locomotive)
    * Up/down arrow (speed up/slow down) (hold Alt to double the increment)
    * Left/right arrow (change direction)
    * Control+Left/right arrow (set direction as reverse / set direction as forward)
    * Escape (power off/emergency stop)
    * Alt+G (power on)
    * 1 through 0 (set locomotive speed, 1 is stopped and 0 is max)
    * Numpad 0/backquote/Alt+0 (toggle lights/F0)
    * F1-F24 (toggle functions F1-F24)
    * Numpad 1-9, Alt+1-9 (toggle functions F1-F9)
    * Control+0-9 (toggle functions F10-F19)
    * Control+Alt+0-9 (toggle functions F20-F29)
    * Shift (slow stop)
    * Spacebar (instant stop)
    * Enter (stop all locs)
* Locomotive shortcuts
    * Alt+P (apply saved function preset for current loc)
    * Alt+O (turn off all functions for current loc)
    * Alt+S (save current functions as a preset for current loc)
    * Alt+U (save current speed as a preset for current loc)
    * Alt+V (apply saved speed preset for current loc)
* Locomotive management
    * Comma/period, semicolon/colon, Alt+left/right arrow (cycle to previous/next loc page)
    * Alt+comma/period, Alt+semicolon/colon (jump to first/last loc page)
    * Control+F (quickly jump to/search for any locomotive)
    * Control+C (copy locomotive at currently active button)
    * Control+X (cut locomotive and clear mapping of currently active button)
    * Control+V (paste copied locomotive at currently active button)
    * Control+S (swap copied locomotive with currently active button)
    * Control+A (assign a new locomotive to the currently active button)
    * Control+N (edit locomotive notes)
    * Control+R (edit locomotive name or address)
    * Control+Delete (permanentely delete locomotive from database)
* Autonomy Graph UI
    * Control+V (assign active locomotive, or clipboard locomotive if non-empty, to currently hovered node)
    * Delete/Backspace (remove locomotive from currently hovered node, clear clipboard if non-empty)
    * Control+X (remove locomotive from currently hovered node and place it in the clipboard)
* UI shortcuts
    * Control+M (show menu bar)
    * Backspace/Alt+backspace, CapsLock/Alt+CapsLock (cycle through tabs)
    * Plus/minus, \[/\], '/( (cycle through keyboards and layout pages)
    * Slash/question mark, < (cycle through function tabs on the locomotive panel)

![Key mapping](assets/keyboard.png?raw=true)

## License & Contact

TrainControl was created and is maintained by Adam Oest.

To support development, please feel free to [make a donation via Buy Me a Coffee](
https://www.buymeacoffee.com/traincontrol).

Feedback and suggestions are welcome at [traincontrol@adamoest.com](traincontrol@adamoest.com).

This is free software released under the GNU General Public License v3.

No copyright claim is made to any Central Station icons rendered during the use of this program.

Tab icons provided by Free pik.

## Prerequisites

* Install Java 8 on your computer
* Requires a Marklin Central Station 2 or Central Station 3 connected to your network and layout
* The computer running TrainControl must be on the same network as your CS2/CS3 (Wi-Fi or ethernet)
* Ensure that your firewall allows TrainControl/Java to access the local network
* Important: CS2/CS3 CAN bus and broadcasting needs to be enabled in the settings (TrainControl will show a warning popup after 15 seconds if this is not enabled)
* For fully autonomous operation, your network connection must be reliable (Ethernet or 5Ghz Wi-Fi recommendend)

<details>
<summary>How to enable CAN broadcasting</summary>

**Central Station 2:**

From the upper-left corner of the CS3 main screen, click on the **System** icon.  Then click on **IP** toward the bottom the page that is shown.

![CS3 System page](assets/network1.png?raw=true)

Typically, you would either manually input a specific IP on this page, or have your router assign a static IP based on the CS3's MAC address.

In this example, the network mode is *auto (DHCP)* and the CS3 has been automatically assigned an *IP Address* of 192.168.50.25 on the local network. The *IP Gateway* and *DNS Server* is 192.168.50.1 with an *IP Network Template* of 255.255.255.0.

![CS3 IP settings page](assets/network3.png?raw=true)

Scroll down to **Settings CAN** and select *broadcast* from the dropdown.  Set the *Destination Address* to the highest allowed IP in your subnet, which usually means setting the last octet to 255.  In this case, the *Destination Address* is therefore 192.168.50.255.  You can safely ignore the warning icon shown.

![CS3 CAN settings page](assets/network2.png?raw=true)

Many routers assign addresses within the 192.168.1.x range by default, so most users will need to set **192.168.1.255** here.

**Central Station 2:**

On the CS2, identical settings are found by going to the **Setup** tab in the upper-right of the main screen, then the *IP* and *CAN* sub-tabs, respectively.

---

</details>

**Limitations:**

* Central Station IP address must be manually entered the first time you run TrainControl (recommend configuring a static IP in your router).  An auto-detection feature is available from v2.3.1, but is not guaranteed to find your Central Station.
* Central Station track diagrams require a CS2, or CS3 v2.5+ (local layout files can be created/used with older CS3s if desired)

## Running TrainControl

**Running the application (build or release JAR):**

Download the latest `TrainControl.jar` [JAR file from the releases page](https://github.com/bob123456678/TrainControl/releases).

Some operating systems allow you to simply double-click the JAR file to run it.  On others, you may wish to create a `.sh` or `.bat` file to execute the command below.

To run TrainControl, open a terminal / command prompt window, and from the directory containing TrainControl.jar, execute the following command.

```java -jar TrainControl.jar [CS2_IP_address [debug [simulate]]]```

Examples:

* ```java -jar TrainControl.jar``` (UI will prompt for IP)
* ```java -jar TrainControl.jar 192.168.50.10``` (Will attempt to connect to the Central Station at 192.168.50.10)
* ```java -jar TrainControl.jar 192.168.50.10 debug``` (Same as above, but with debug mode: extra error logging)
* ```java -jar TrainControl.jar 0 debug simulate``` (Same as above, but allows the program to run without any central station)

All state is saved to LocDB.data, UIState.data, and autonomy.json in the same directory, and can be backed up as desired.

**Building the project from source:**

Requires JDK 1.8+ and the following libraries:

* org.json (json-20220924.jar) (from v1.6.0)
* org.graphstream (gs-core-2.0.jar, gs-algo-2.0.jar, gs-ui-swing-2.0.jar) (from v1.8.0)
* com.formdev.flatlaf.FlatLightLaf (flatlaf-3.5.2.jar) (from v2.3.0)

```ant -f /path/to/project/ -Dnb.internal.action.name=rebuild clean jar```

## Changelog

* v2.3.3 [12/17/2024]
    - When editing function icons, added a button to copy icons from an existing locomotive
    - When adding a new locomotive, it will automatically be mapped to the current button if the button has no existing mapping
    - Fixed bug where when changing a locomotive's decoder type, functions outside of the normally allowed range would be accessible
    - Fixed bug where the selected function tab would reset when editing function icons for F20-F31
    - Fixed potential temporary UI freeze when accessing the function icon customization menu

* v2.3.2 [12/10/2024]
    - Code portability enhancements (custom code using TrainControl APIs will need to be updated to use the new package names)
        - Moved all TrainControl code to the `org.traincontrol` package and updated documentation to reflect this 
        - Implemented checks to maintain compability with state files from prior versions (`LocDB.data`, `UIState.data`)
        - Added several new API methods for covenience, improved code comments
        - Expanded [API example code](src/org/traincontrol/examples/ProgrammaticControlExample.java)
        - Added [Java docs](assets/javadoc/index.html)
    - Minor UI and tooltip tweaks
    - Swapped the Route and Autonomy tabs in the UI
    - Fixed bug where locomotives in save files with no operation history would prevent the Stats tab in the UI from rendering
    - Fixed bug at initial startup where the window would always be on top even though the preference was unchecked
    - Note: The saved IP address & window preferences will be reset when switching to this version

* v2.3.1 [12/2/2024]
    - Network enhancements
        - A warning will now be shown if the Central Station IP address manually entered at startup exists but does not appear to be a Central Station
        - Added a button to attempt to auto-detect the Central Station IP at startup
        - Added an option in the File menu to reset the stored Central Station IP preference
        - Central Station version is now shown next to the latency in the main UI
    - API enhancements
        - The Central Station IP will now be prompted for if running the program from a custom script in headless mode, and no IP is passed to `init`
        - Improved standard out logging at startup
    - The Add Locomotive, Locomotive Database, and 30-day Usage Stats pop-up windows will now snap to the main window by default

* v2.3.0 [11/24/2024]
    - UI enhancements
        - Applied a modern look & feel to the whole UI
        - Added a standard menu bar with various options
            - Rearranged the "Tools" tab across the menu bar
            - Moved UI preferences to the menu bar
            - The visibility of the menu bar can be toggled from the keyboard mapping UI / Control+M
            - Added option to display the active locomotive name in the title of popup windows (layouts / autonomy graph)
        - Moved main UI tabs to the left, and replaced text headings with icons
            - Tabs can now also be cycled using the CapsLock key
            - Improvements to UI tooltips
            - Improvements to UI alignment
            - Increased the size of locomotive icons
        - Autonomy 
            - Consolidated start / stop buttons onto the Locomotive Control tab of the autonomy UI
            - Moved option to clear locomotives from the graph to the right-click menu in the graph UI (when right clicking any blank space)
            - Added start and graceful stop shortcuts to the right-click menu in the graph UI
        - Routes
            - The route ID is now pre-filled when editing route IDs
            - The track diagrams can now be focused while editing routes
            - When editing routes, errors will no longer result in the window closing
            - Route names are now shown in track diagram tooltips
            - Export options moved to the menu bar
        - Locomotive Management
            - Locomotives in the locomotive selector are now sorted alphabetically
            - Locomotives can now be added to the database through a separate popup window
            - Alt+comma/period, Alt+semicolon/colon will now jump to the first/last key mapping page
            - QWERTY/QWERTZ/AZERTY keyboard options moved to the menu bar
            - Increased size of the locomotive notes window
            - Added page right-click menu option to fill an entire page with unmapped locomotives
    - Autonomy
        - In autonomous operation, individual locomotives can now be paused on demand
        - Fixed bug where deleted/edited locomotives would persist in the autonomy graph UI
        - When validating the autonomy JSON configuration, fatal error details will now be shown in the popup error message
    - Routes
        - When editing routes, live switch/signal commands can now be captured on demand
        - Added custom route commands for turning off all functions / turning on all lights
    - Keyboard
        - Actuation counts for each accessory are now tracked and displayed as tooltips on the Keyboard page
    - Fixed bug where "Large" track diagrams would not always render correctly 
    - Fixed pop-up window stacking when "always on top" is not selected

* v2.2.7 [9/14/2024]
    - In the graph UI, added a context menu shortcut to add an edge to the last left-clicked node
    - Fixed bug where certain inactive/reversing points would not be hidden when requested

* v2.2.6 [8/19/2024]
    - Improved the S88 active icon for straight and curved track segments
    - Autonomy graphs can now be moved around by dragging
    - Fixed bug where the keyboard shortcuts from v2.2.3 would not refresh the autonomy locomotive list

* v2.2.5 [8/11/2024]
    - Clicking on switches in the layout diagram while the power is off will now trigger a pop-up warning
    - Minor UI performance optimizations

* v2.2.4 [8/1/2024]
    - Fixed UI bug where an "invalid name" warning would always be shown when editing a locomotive address

* v2.2.3 [7/27/2024]
    - Autonomy graph UI convenience enhancements
        - Added Control+X keyboard shortcut: hover over a node to remove the currently assigned locomotive & put it on the clipboard
        - Added Delete/Backspace keyboard shortcut: hover over a node to remove the currently assigned locomotive, and/or clear the clipboard
        - Added Control+V keyboard shortcut: hover over a node and easily assign the active locomotive to it (or the clipboard locomotive if non empty)
        - Added the above option to the right-click menu
    - Improved the locomotive exclusion UI in the autonomy graph UI

* v2.2.2 [7/3/2024]
    - Various minor UI enhancements and helpful messages for first-time users
    - Locomotive selector improvements
        - Added an "add locomotive" shortcut button
        - Added address information to each locomotive tile
        - Mapped locomotives are more clearly highlighted
        - Locomotive addresses are now checked when filtering
    - Added keyboard shortcut to delete locomotives from the database (Control+Delete)

* v2.2.1 [6/29/2024]
    - Added total runtime and number of locomotives to stats histogram chart
    - Autonomy graph UI improvements
        - If closed, the graph UI will now automatically re-open whenever autonomous operation is started
        - Added a setting to show/hide edge lengths and the maximum train lengths at each station
        - The above setting also highlights stations with excluded locomotives and shows the list of locomotives in the log on hover

* v2.2.0 [6/18/2024]
    - Added basic support for parsing and importing routes from the CS3 
        - CS3 routes will now automatically be imported into TrainControl and will always overwrite local routes with the same ID
        - The first S88 will be interpreted as the triggering S88
        - Subsequent S88s will be interpreted as additional mandatory conditions for triggering the route
        - Remaining switch/signal commands are executed sequentially
        - Any S88s after a switch/signal command will be ignored
    - Statistics improvements
        - Added a 30-day usage graph to the Stats tab
        - Exported raw data now has the .csv extension
    - Layout improvements
        - Track Board layouts from CS3 v2.5.0+ can now be automatically imported.  
        - Most combinations of CS3 tracks are supported, except for certain overlapping straight tracks, which should be replaced with crossings.
        - Added new accessory types for special CS3 double slip switches
        - Switches without an address will no longer be clickable
        - Improved left/right/threeway switch icon quality
    - Locomotive management improvements
        - Added notes feature to locomotives (Control+N).  This can be used to save arbitrary information such as the last lubrication date, etc.
        - Consolidated "rename" and "change address" options into a single right-click menu entry.  Added keyboard shortcut (Control+R) 
    - Improved error logging
    - Fixed occasional UI initialization error on startup

* v2.1.5 [6/1/24]
    - Added JSON key `excludedLocs` for `Point`s, which lets you exclude locomotives from certain autonomous paths
        - Locomotives excluded from stations will not be directed there in fully autonomous mode
        - Locomotives excluded form non-stations will never be able to traverse paths that include them
    - Added graph UI menu option to edit excluded locomotives
    - Semi-autonomous paths that end at an excluded station are denoted with a `-`

* v2.1.4 [5/25/24]
    - Parallelized CAN message processing, aimed at improving the reliability of autonomous operation when using slower PCs
    - Fixed bug where the UI log window would always auto scroll to the bottom

* v2.1.3 [5/21/24]
    - Improved CAN message processing performance, aimed at improving the reliability of autonomous operation when using slower PCs

* v2.1.2 [5/20/24]
    - Improved UI performance when adjusting locomotive speeds

* v2.1.1 [5/12/24]
    - Fixed bug where the speed would not be reset in the UI after changing a locomotive's direction via the Central Station
    - Performance improvemens aimed at improving the reliability of autonomous operation when using slower PCs
        - UI performance optimizations
        - Improved logging performance

* v2.1.0 [4/24/24]
    - Added a Timetable feature as a new type of autonomous operation mode
        - Locomotive commands in semi-autonomous or fully-autonomous mode can be captured on demand to create a timetable
        - Paths in the timetable can be replayed sequentally, with progress saved between runs
        - Timetables are saved in the autonomy JSON files so presets can be loaded as needed
        - The Locomotive Commands window will now mark timetable starting stations with a * to simplify the creation of timetables that finish where they started
        - [Java API](Automation.md#timetables) for programmatically creating timetables
    - Locomotive function icon improvements
        - Expanded support to 296 function icons when connected to a CS3. Icons will now match what is shown in the CS3.
        - Improved icon contrast and resolution
        - Icons can now be set to custom (local) images
        - The Reset All Customizations button will now be greyed out if no customizations have been made
    - Added a backup button to the Tools tab to backup all TrainControl state
    - Fixed possible race condition at UI startup
    - Improved UI/general performance in fully autonomous mode
    - Backward-incompatible changes:
        - LocDB.data files from versions older than 2.0.0 are no longer readable.  Convert them with v2.0.0+ first.

* v2.0.17 [4/13/24]
    - Added Control+A shortcut to assign a locomotive to the currently active button

* v2.0.16 [3/18/24]
    - Added Control+F shortcut and right-click menu option to quickly find/jump to any locomotive already mapped
    - Added Control+C/V/S/X shortcuts to copy, paste, swap, and cut/clear the locomotive at the currently active button

* v2.0.15 [3/17/24]
    - Added right-click menu option to move locomotive keybinds (shortcut for copy+clear)
    - Added various helpful tooltips to the UI

* v2.0.14 [3/15/24]
    - Added track diagram UI caching to speed up rendering on slower PCs

* v2.0.13 [1/21/24]
    - Within the Tools tab, added a button to view the locomotive database
    - The autonomy layout state is no longer invalidated upon syncing with the Central Station
    - The autonomy graph window is now larger by default
    - Improved UI performance when operating many locomotives concurrently
    - Fixed display issue when resetting function customization
    - Reduced network latency warning thresholds

* v2.0.12 [1/4/24]
    - In autonomous operation, added a setting to skip turning on the functions on departure (e.g., useful if you want to run with no sound)
    - Added right-click options to copy and paste entire pages of keyboard mappings
    - Moved the "reset mappings" button from the Tools tab to the page right-click menu
    - Fixed minor bug from v2.0.10 where the layout tab would not be correctly focused when initializing a new layout

* v2.0.11 [1/2/24]
    - The autonomy graph is now zoomable with the mouse wheel (click mouse wheel to reset)
    - Function icons are now easier to customize: shown in a 6-column grid instead of a dropdown
    - Improved graph display quality

* v2.0.10 [12/31/23]
    - In routes with locomotive speed commands, a speed value of -1 will now trigger an instant stop
    - When autonomous operation is started and conditional routes are active, the warning will now only be shown one time
    - Fixed locomotive speed command validation bug
    - Fixed minor bug in button shadows when changing between keyboard layouts
    - Fixes for rare UI crashes on startup

* v2.0.9 [12/28/23]
    - Locomotive mapping convenience improvements
        - Added support for custom labels for each locomotive mapping page (right-click the default page name to change)
        - Added right-click menu option to copy a locomotive to the previous mapping page
        - Added two additional locomotive mapping pages, for a total of 10
        - The active mapping page number is now also shown in the tab title
        - The active page and button is now remembered on exit
    - MFX locomotive addresses are no longer displayed in hex in the UI
    - Added addresses 253-256 to the keyboard
    - Keyboard buttons are now color-coded red/green based on the accessory state
    - The Autonomy/Route export windows will now be auto-closed when the JSON file is saved
    - Fixed UI bug where a locomotive icon from another page could temporarily appear on the active page

* v2.0.8 [12/21/23]
    - Fixed bug where the number of days shown in the stats table was not sorted correctly
    - Fixed bug in the stats table where runtimes longer than 24 hours would be truncated
    - Updated locomotive icon URLs to work with CS3 v2.5.0+ update

* v2.0.7 [12/17/23]
    - Locomotive speed and function commands are now fully supported in routes
    - Added locomotive speed/function command fields to the route editing wizard

* v2.0.6 [12/16/23]
    - The decoder type can now be changed when editing the address of an existing locomotive in the database.  This is useful when swapping decoders and you want to keep your locomotive settings.

* v2.0.5 [12/10/23]
    - Bugfix: Semi-autonomous operation is now also possible with conditional routes enabled
    - Control + left/right arrow can now be used to specify reverse or forward direction

* v2.0.4 [12/8/23]
    - Conditional routes may now be active while autonomous operation is running; a warning will be shown instead

* v2.0.3 [11/30/23]
    - Added cumulative runtime statistics to the stats tab
    - Updated the track diagram editor version to v2.2.0 (new feature: merging layout pages & view addresses in use)

* v2.0.2 [11/20/23]
    - The locomotive selector window will now close upon pressing escape
    - Locomotive icons will now appear more consistently on slower networks

* v2.0.1 [11/12/23]
    - Within the Stats tab, added a column with today's runtime for each locomotive
    - Added a shadow to button labels (letters) to make them easier to see against locomotive icons with light backgrounds
    - Fixed minor bug where icons in the locomotive selector window would not immediately reflect local locomotive icons
    - Fixed minor bug where the main UI could shrink below its minimum size

* v2.0.0 [11/10/23] (This version adds a few important features to make it an all-in-one layout controller. Also includes numerous stability enhancements.)
    - Integrated a layout editor app [(TrackDiagramEditor)](https://github.com/bob123456678/TrackDiagramEditor) to allow for the editing of track diagrams (Windows only)
        - Added an edit button to each layout page; this automatically opens the editor
        - A basic starting layout will automatically be loaded if no CS2 is detected and no layout path has been manually specified
        - Added button to the Tools tab to initialize an empty layout on demand
    - Track diagram improvements
        - Added support for page links (pfeil) which change the active diagram page when clicked
        - Updated overpass and turntable track diagram icons
        - Added a button to revert to the CS2 layout when currently using a local layout
        - Fixed UI errors when TrainControl was run without a layout
        - Fixed bug where empty rows/columns in layouts were not rendered correctly
        - Text labels will now be rendered on any tile with a .text property (such text is aligned between track icons)
    - Expanded locomotive customization options
        - Custom icons from your PC can now be chosen for locomotives, even if no icon is selected in the Central Station
        - Function icons can now be assigned to locomotive functions, even if no icon is selected in the Central Station
        - Duration functions are now supported (in addition to toggle/momentary)
        - Locally configured icons will get priority over Central Station icons
        - Accessible by right-clicking any locomotive button, or the icons themselves
    - Autonomy graph: Points can now be marked as active or inactive
        - Inactive points will never be chosen within paths in autonomous operation
        - Locomotives on inactive stations will now be greyed out in the semi-autonomous operation UI
        - Added corresponding `active` JSON key within `points`
        - Added corresponding controls to the right-click menu in the graph UI
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
        - The manual "Add locomotive" option under Tools now accepts MFX addresses as integer or hex
    - Locomotive addresses can now be manually changed in TrainControl (useful for MM2/DCC decoders, does not propagate to Central Station)
    - Improved the autonomy JSON UI
        - Added option to load graph JSON from a file (to make managing presets easier)
        - Added option to save graph JSON to a file
    - Route UI features
        - Conditional routes now support stop commands to enable basic collision detection
        - Added optional conditional accessory criteria to expand route possibilities
        - Added buttons to export/import all routes (useful for backups)
        - Added warnings if route buttons on a layout do not correspond to a valid route
    - Improved locomotive usage statistics
        - New UI viewer
        - Track the number of days each locomotive was run
    - Improved the image quality of locomotive and function icons (they will be less pixelated)
    - Increased size of the active locomotive icon
    - Added support for QWERTZ and AZERTY keyboards
    - Added network latency monitoring
    - Different instances of TrainControl will now use unique track diagram and IP preferences
    - Fixed bug where orphan feedback IDs could become undeletable in TrainControl's database
    - Fixed bug where special characters in locomotive names would sometimes be read incorrectly

* v1.10.11 [10/15/23]
    - New graph nodes are now created near the cursor instead of the lower-left corner of the window
    - Double-clicking a station node is now a shortcut to opening the locomotive assignment window
    - Clicking "mark as terminus station" on a non-station will now automatically convert the point to a station first
    - When adding or editing locomotives on the graph, the locomotive list is now automatically focused for easier selection
    - Added a pop-up error message if an invalid layout file path is chosen via the "Choose Local Data Folder" button within Tools
    - Fixed a bug in the layout UI where wide text labels in the last column would sometimes lead to misaligned tracks

<details>
<summary>View prior versions</summary>

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
    - Added [API & automation readme/tutorial](Automation.md)

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

</details>