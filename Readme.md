# Marklin Central Station 2/3 Train Control

This cross-platform Java program allows you to use your computer to easily control your entire Marklin layout.
It connects to a Central Station 2, 3, or 3 Plus over the network.
It is primarily designed for users with a large layout / many locomotives, as
the standard Marklin UI makes many common tasks (such as quickly switching between locomotives)
overly tedious.  Convenient keyboard hotkeys are available for controlling locomotives, switching between locomotives, 
enabling functions, emergency stop, etc.  You can also open windows with interactive track diagrams.

As such, TrainControl is designed to be a complete replacement for the CS2/CS3 for
operating your layout, with the Central Station serving solely as the track interface
and locomotive database.

Under the hood, this program implements the Marklin CAN protocol and can therefore
also be used to fully automate a layout.  Layout and locomotive information is automatically
downloaded from the CS2/CS3, currently with some layout limitations on the CS3 (see below).

![UI screenshot: locomotive control](assets/interface9.png?raw=true)

![UI screenshot: layout](assets/interface4.png?raw=true)

![UI screenshot: keyboard](assets/interface5.png?raw=true)

![UI screenshot: locomotive control](assets/interface8.png?raw=true)

![UI screenshot: locomotive control](assets/interface6.png?raw=true)

![UI screenshot: keyboard](assets/routes2.png?raw=true)

![UI screenshot: keyboard](assets/routes3.png?raw=true)

<img src="assets/graph3.png?raw=true" alt="UI screenshot: autonomous graph visualizer" width="400">

**Features:**

* Easily control locomotives (mm2, mfx, dcc), signals, switches, and routes
* View and interact with layout diagrams, with support for multiple windows
* Configure up to 8 different key mappings for up to 208 locomotives
* Convenient hotkeys for power off, emergency stop, and smooth deceleration
* Set up automatic and conditional routes triggered by S88 feedback modules
* Automate bulk tasks such as turning off all functions
* Download locomotive layout, and route information from the CS2/CS3
* View S88 feedback
* Progammatic layout control via Java API (uses CAN protocol - [see documentation](src/examples/Readme.md)) 
* (Beta) [Graph model](src/examples/Readme.md) w/ JSON configuration for dynamic layout modeling and fully autonomous train operation

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

Expanded support for CS3 layouts is planned for the future.

**Building the project from source:**

Requires JDK 1.8+ and the following libraries:

* org.json (json-20220924.jar) (from v1.6.0)
* org.graphstream (gs-algo-2.0.jar, gs-core-2.0.jar, gs-ui-swing-2.0.jar) (from v1.8.0)

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

* v1.8.0 [Beta]
    - Added JSON-based layout autonomy configuration interface
    - Added ability to run autonomous layouts via the UI
    - Added GraphStream UI to monitor autonomous layouts

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

* v1.7.0 [4/9/23]
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

* v1.6.0 [10/2/22] (Beta)
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

* v1.5.0 [12/13/21]
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

* v1.4.0 [11/5/21]
    - Support for viewing layouts in (unlimited) pop-up windows
    - Added function hotkeys (F1-F16)
    - Mapped `~` to F0
    - `,` and `.` hotkeys for switching between locomotive pages are now active across all tabs
    - Changed keyboard pagination hotkeys to `-` and `+`

* v1.3.2
    - First public release

