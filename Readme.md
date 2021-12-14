# Marklin Train Control

This program allows you to use your computer to control your Marklin layout.  
It is primarily designed for users with a large layout / many locomotives, as
the standard Marklin UI makes many common tasks (such as quickly switching between locomotives)
overly tedious.

Under the hood, this program implements the Marklin CAN protocol and can therefore
also be used to fully automate a layout.  Layout and locomotive information is automatically
downloaded from the CS2/CS3.

![UI screenshot: locomotive control](assets/interface.png?raw=true)

![UI screenshot: layout](assets/interface2.png?raw=true)

**Features:**

* Configure hotkeys for different locomotives, accessories, and functions
* Control locomotives, signals, and switches
* View S88 feedback
* Download locomotive, layout, and route information from the CS2/CS3
* View and control the entire layout just like on the CS2/CS3
* Progammatic layout control via API (uses CAN protocol)

**Requirements:**

* Must be on the same Wi-Fi network as the CS2/CS3
* CS2/CS2 CAN bus and broadcasting must be enabled

**Limitations:**

* This program has currently only been tested with the CS2
* CS2 IP address must be manually entered (recommend configurating a static IP in your router)

**Building the project from source:**

Requires JDK 1.8+.

```ant -f /path/to/project/ -Dnb.internal.action.name=rebuild clean jar```

**Running the application (build or release JAR):**

```java -jar TrainControl.jar [CS2 IP address]```

## Changelog

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

