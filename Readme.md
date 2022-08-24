# Marklin Train Control

This program allows you to use your computer to easily control your entire Marklin layout.
It connects to a Central Station 2/3 over the network.
It is primarily designed for users with a large layout / many locomotives, as
the standard Marklin UI makes many common tasks (such as quickly switching between locomotives)
overly tedious.

Under the hood, this program implements the Marklin CAN protocol and can therefore
also be used to fully automate a layout.  Layout and locomotive information is automatically
downloaded from the CS2/CS3.

![UI screenshot: locomotive control](assets/interface3.png?raw=true)

![UI screenshot: layout](assets/interface4.png?raw=true)

![UI screenshot: keyboard](assets/interface5.png?raw=true)

**Features:**

* Easily control locomotives, signals, switches, and routes
* View and interact with layout diagrams, with support for multiple windows
* Configure key mappings for up to 104 locomotives
* Convenient hotkeys for power off, emergency stop, and smooth deceleration
* Automate bulk tasks such as turning off all functions
* Download locomotive, layout, and route information from the CS2/CS3
* View S88 feedback
* Progammatic layout control via Java API (uses CAN protocol - [see documentation](src/examples/Readme.md)) 
* Graph model for dynamic layout modeling and fully autonomous train operation

**Requirements:**

* Requires a Marklin Central Station 2 or Central Station 3 connected to your network
* Must connect to the same network as the CS2/CS3 (Wi-Fi or ethernet)
* CS2/CS3 CAN bus and broadcasting must be enabled in the settings

**Limitations:**

* The current version has only been tested with the CS2
* CS2 IP address must be manually entered (recommend configurating a static IP in your router)

**Building the project from source:**

Requires JDK 1.8+.

```ant -f /path/to/project/ -Dnb.internal.action.name=rebuild clean jar```

**Running the application (build or release JAR):**

```java -jar TrainControl.jar [CS2 IP address]```

## Changelog

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

