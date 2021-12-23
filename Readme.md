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
* Progammatic layout control/automation via Java API (uses CAN protocol)

**Requirements:**

* Requires a Marklin Central Station 2 or Central Station 3 connected to your network
* Must connect to the same network as the CS2/CS3 (Wi-Fi or ethernet)
* CS2/CS3 CAN bus and broadcasting must be enabled in the settings

**Limitations:**

* This current version has only been tested with the CS2
* CS2 IP address must be manually entered (recommend configurating a static IP in your router)

**Building the project from source:**

Requires JDK 1.8+.

```ant -f /path/to/project/ -Dnb.internal.action.name=rebuild clean jar```

**Running the application (build or release JAR):**

```java -jar TrainControl.jar [CS2 IP address]```

## Changelog

* v1.5.2 [12/22/21]
    - Connection will no longer fail upon encountering text labels in the layout
    - Support for new layout components
        * Y switches
        * Clickable routes
        * Text labels
    - Alt-G is now mapped to the "go" button (turns on the power)
    - Added forward and reverse labels to loc direction buttons

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

