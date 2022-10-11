# Marklin Central Station 2/3 Train Control

This program allows you to use your computer to easily control your entire Marklin layout.
It connects to a Central Station 2, 3, or 3 Plus over the network.
It is primarily designed for users with a large layout / many locomotives, as
the standard Marklin UI makes many common tasks (such as quickly switching between locomotives)
overly tedious.  Convenient keyboard hotkeys are available for controlling locomotives, switching between locomotives, 
enabling functions, emergency stop, etc.

Under the hood, this program implements the Marklin CAN protocol and can therefore
also be used to fully automate a layout.  Layout and locomotive information is automatically
downloaded from the CS2/CS3, with some layout limitations on the CS3 (see below).

![UI screenshot: locomotive control](assets/interface7.png?raw=true)

![UI screenshot: layout](assets/interface4.png?raw=true)

![UI screenshot: keyboard](assets/interface5.png?raw=true)

![UI screenshot: locomotive control](assets/interface8.png?raw=true)

![UI screenshot: locomotive control](assets/interface6.png?raw=true)

**Features:**

* Easily control locomotives, signals, switches, and routes
* View and interact with layout diagrams, with support for multiple windows
* Configure key mappings for up to 104 locomotives
* Convenient hotkeys for power off, emergency stop, and smooth deceleration
* Automate bulk tasks such as turning off all functions
* Download locomotive, layout, and route information from the CS2/CS3
* View S88 feedback
* Progammatic layout control via Java API (uses CAN protocol - [see documentation](src/examples/Readme.md)) 
* (Beta) Graph model for dynamic layout modeling and fully autonomous train operation

**Requirements:**

* Requires a Marklin Central Station 2 or Central Station 3 connected to your network
* Must connect to the same network as the CS2/CS3 (Wi-Fi or ethernet)
* CS2/CS3 CAN bus and broadcasting needs to be enabled in the settings

**Limitations:**

* Automatic layout download only works with CS2, not CS3 (static layout files can be used with CS3 if desired)
* Central Station IP address must be manually entered (recommend configurating a static IP in your router)

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

**Building the project from source:**

Requires JDK 1.8+.

```ant -f /path/to/project/ -Dnb.internal.action.name=rebuild clean jar```

**Running the application (build or release JAR):**

```java -jar TrainControl.jar [CS2 IP address]```

## Changelog

* v1.6.1 [10/11/22]
    - Redesigned locomotive selection UI and moved all editing options to right-click menu
    - Larger locomotive icons and more functions visible at once
    - Added button to query every loc's function status from the Central Station
    - Fixed lag when switching between locomotive pages

* v1.6.0 [10/2/22]
    - Tested with CS3, may need to be run as admin when started for the first time
    - Added ability to load layout files from the local filesystem (see further details " Layouts with the CS3" above)
    - Fixed loading of locomotive icons with special characters
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

