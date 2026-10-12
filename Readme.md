# TrainControl for Märklin Central Station 2 & 3

[![Latest release](https://img.shields.io/github/v/release/bob123456678/TrainControl?label=latest%20release&color=success)](https://github.com/bob123456678/TrainControl/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/license-GPL%20v3-blue)](https://www.gnu.org/licenses/gpl-3.0)
![Java 8](https://img.shields.io/badge/Java-8-orange)

**Free, open-source software for controlling and automating your Märklin (Marklin), Trix, or DCC model railroad from your computer.**  Runs on Windows, macOS, and Linux.

Available in 🇬🇧 English · 🇩🇪 Deutsch · 🇩🇰 Dansk · 🇵🇱 Polski · 🇫🇷 Français · 🇮🇹 Italiano · 🇪🇸 Español · 🇳🇱 Nederlands

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-Support%20TrainControl-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://www.buymeacoffee.com/traincontrol)

![TrainControl main window, showing locomotive control with keyboard mappings, locomotive thumbnails, and function buttons](assets/main23_2.png?raw=true)

TrainControl connects to a Central Station 2, 3, or 3 Plus over your network.  It is designed for model railways with many locomotives, where the standard Marklin UI makes common tasks (such as quickly switching between locomotives or triggering functions) overly tedious.  It is a complete replacement for the CS2/CS3 when operating your layout, with the Central Station serving solely as the track interface and MFX locomotive database.  If your existing controller is taking the fun out of running your trains, consider trying TrainControl!

**Why TrainControl?**

* Control any locomotive instantly from your keyboard
* Interactive track diagrams that you can edit in the app, in any number of windows
* Fully autonomous train operation using S88 sensors and digital switches (signals optional)
* Multi-units, function presets, conditional routes, and usage statistics
* Locomotive and layout data downloaded automatically from your Central Station
* Free and open source

**Getting started**

1. Connect your Central Station 2/3 to your network and enable CAN broadcasting in its settings ([details](#requirements))
2. [Download the latest `TrainControl.jar`](https://github.com/bob123456678/TrainControl/releases/latest)
3. Run it, and enter your Central Station's IP address when prompted ([details](#download-and-run-traincontrol))

Under the hood, this program implements the Marklin CAN protocol and can therefore
also be used to programmatically control the entire layout ([see API](AutomationAPI.md)).  Layout and locomotive information is automatically
downloaded from the CS2/CS3 (with minor limitations on the CS3). Track diagrams can also be designed entirely in TrainControl.

For easy scripting or interactive control, you can write [Python (Jython) scripts to call the TrainControl API](src/org/traincontrol/examples/traincontrol_python_example.py).

TrainControl also lets you set up automation on the track diagram itself.  Paired with S88 sensors, it keeps track
of where every train is, for *fully autonomous* operation at the push of a single button, as well as point-to-point
operation between stations.  You can of course also set up traditional/conditional routes to automate switch and signal
commands while operating trains manually.

TrainControl uses your computer's language automatically.  To run it in another language, you can [choose one from the command line](#download-and-run-traincontrol).

## Overview

**Main UI**

As shown above, you can assign locomotives to any letter on the keyboard, then quickly switch between them.  Easy keyboard shortcuts let you control locomotives.  Thumbnails are automatically downloaded from the CS2/CS3 or can be set manually.  Drag-and-drop locomotives while the power is off.

Right-click a locomotive or function icon to change it or set additional options, such as preferred speed or function presets.

![Right-click menu on a locomotive button, showing options for function presets, preferred speed, and multi-unit setup](assets/ui_right_click.png?raw=true)

![TrainControl locomotive selector, used to assign a locomotive from the database to a keyboard button](assets/ui_sel_loc.png?raw=true)

Multi-units can be configured in TrainControl, which can be simpler than using the Central Station.

![Configuring a Marklin multi-unit in TrainControl by linking several locomotives together](assets/multiunit.png?raw=true)

**Layout View & Layout Editing**

Track diagrams for your layout are downloaded automatically from the CS2 / CS3 (Track Boards only), or customizable through locally managed layout files.  All components (switches, signals, S88, routes) are clickable and reflect the layout state.  Multiple pages can be opened across unlimited popup windows.

![Interactive track diagram in TrainControl, with clickable switches, signals, and S88 feedback indicators](assets/layout23.png?raw=true)

Track diagrams can be created and edited directly within TrainControl, on any platform.

![TrainControl's built-in track diagram editor, used to draw and edit a model railway layout](assets/editor.png?raw=true)

<details>
<summary>Instructions for Managing and Importing Layouts</summary>

**Layouts and the CS3**

This program was originally written to import and display layouts created/configured from within the CS2, but you can also customize your own layouts without a Central Station.

Because the CS3 uses a different layout format than the CS2, this program does not support displaying native CS3 layouts. 
However, from CS3 v2.5.0, the CS3 now exports its Track Board layouts using the CS2 file format.  TrainControl supports such layouts, and they are automatically imported by default.
In some cases, you may need to use a double slip switch or a Y-switch from the "manual" menu in the CS3 to get tracks that cross over each other to render correctly.  You may also need to change certain straight tracks as the CS3 has a tendency to incorrectly connect tracks in the CS2 format.

If you have an older CS3 or don't want to use the CS3 Track Board layouts, you can import and edit layouts in TrainControl as follows:

- If you have a CS2/CS3 with a layout, import your layout:
    - From the Layouts menu in TrainControl, switch to the Central Station layout, then select "Download Central Station Layout Files".
    - The layout will be saved to a folder of your choosing, and TrainControl will switch to it as the local data source.  It will now be shown in the Track Diagram tab and can be edited if desired.
    - If you already have layout files on your computer, use "Open Layout..." in the Layouts menu to select the folder containing them.
- Otherwise, to create a new layout:
    - Start TrainControl, then from the Layouts menu, click on "Create New Layout"
    - If no Central Station layout is detected and no static layout is manually selected, TrainControl will automatically create an editable demo layout at startup.

Complete editing support is accessible via the "Edit" button within the Track Diagram tab.  Pages can be managed from the Layouts menu.  This will let you fully customize your layout.

If you change the local files, clicking on "Sync Database w/ Central Station" from the Locomotives menu will update the layouts.  This effectively lets you customize the layout even without a CS2/CS3.  Some users might find this easier than inputting data into the Central Station UI.

Some sample files are included in the `cs2_sample_layout` folder.

---

</details>

**Routes**

Conditional routes can be defined for semi-automatic layout operation, such as setting a switch to guide an incoming train to an unoccupied station track, or triggering an emergency stop.  Manual routes can also be defined and activated directly or from the Track Diagram tab.

In addition to all Central Station functionality, complex logical expressions are supported.

![TrainControl's route editor: each command a row of dropdowns, and its conditions an indented list joined by "and" and "or"](assets/ui_route3.png?raw=true)

**Keyboard**

Useful for testing, individual accessories can be directly controlled via their digital address.  The cumulative number of actuations of each accessory is shown as a tooltip.

![Accessory keyboard in TrainControl, used to control switches and signals directly by digital address](assets/ui_keyboard.png?raw=true)

**Full Autonomy**

TrainControl can run your trains on its own.  From version 3.0, you set this up on the track diagram itself, in the autonomy editor: TrainControl works out how your track connects, and you set up stations, directions and settings by right-clicking a square.  The setup is checked before it runs, and each problem it finds takes you to the square it is about.

You need S88 sensors (at least one per station) and digital switches; signals are optional ([what you need](Automation.md#what-you-need)).  Once you have placed your trains, TrainControl keeps track of where every train is, and saves where they stand when you exit.  You can let your trains pick destinations at random, or send a particular train to a station.  If you set up autonomy in an older version, its [JSON configuration file](AutomationAPI.md) can be imported from the Autonomy menu.

**Full guide:** [The autonomy editor: a user guide](Automation.md)

![The autonomy editor: the track diagram with its stations named, a list of things to look at below it, and tools and settings on the right](assets/autonomy_editor3.png?raw=true)

![A train running under autonomy on the track diagram: its route ahead in blue, the track it has passed and still holds in dark grey, and a second train standing at its station](assets/autonomy3.png?raw=true)

The track diagram shows each running train's route, the track it is holding, and where every train is standing.  While trains are stopped, you can right-click a station to place a train there.

You can also record timetables and play them back, with the same protections as autonomous operation.

<img src="assets/timetable3.png?raw=true" alt="The timetable in TrainControl: a captured sequence of journeys, played back in order" width="600">

To send a train to a station yourself, right-click it on the track diagram.

<img src="assets/easyauto3.png?raw=true" alt="Right-clicking a train on the track diagram to send it to a station" width="500">

**Statistics**

Monitor the usage of different locomotives.

![Locomotive usage statistics in TrainControl, showing runtime per locomotive over the past 30 days](assets/stats23.png?raw=true)

## Features

* Easily control locomotives (MM2, MFX, DCC), multi-units, signals/switches (MM2, DCC), and routes
* Download locomotive, layout, and route information from the CS2/CS3
* Customize locomotive icons and function icons without needing to set them in the CS2/CS3
* Powerful keyboard interface
    * Configure up to 50 different key mappings for up to 1,300 locomotives
    * Convenient hotkeys for power off, emergency stop, and smooth deceleration
    * Simultaneous operation across multiple PCs
* Track diagrams
    * View unlimited layout diagrams, with support for multiple windows
    * Toggle signals, switches, lights, uncouplers, and routes
    * View S88 feedback
    * Full UI for editing track diagrams
* Basic automation
    * Set up automatic and conditional routes triggered by S88 feedback modules
    * Automate bulk tasks such as turning off all functions
    * Set function and speed presets for locomotives
* Advanced automation
    * Fully autonomous train operation, set up on the track diagram itself in the autonomy editor ([user guide](Automation.md)), with every train's location tracked by S88 sensors
    * Several named autonomy configurations per layout; an older JSON configuration can be imported
    * Send a train to a station by right-clicking it on the track diagram
    * The setup is checked before it runs, and each problem opens the editor on the square it is about
    * Customize autonomous operation by setting station priority, maximum train lengths, track lengths, speed multipliers, and maximum train idle time
    * Choose how trains pick their route, such as over the shortest track or past the fewest stations
    * Pair a station with signals that turn red while a train is standing there, and back to green when it leaves
    * Restrict which directions a station will accept trains from
    * Record and play back timetables, and send every train back home
    * Hide the names of stations autonomy never uses
* Back up everything in one archive (locomotives, keyboard mappings, track diagrams, routes and
  autonomy setups) and restore it on another machine
* Programmatic layout control via Java API (uses CAN protocol; [see documentation](AutomationAPI.md)) 
* Monitor locomotive usage stats

## Keyboard Commands / Key Mappings

TrainControl's key mappings are designed to allow you to send any command nearly instantly.

* Primary controls
    * Selecting and speed
        * A-Z letter keys (select a locomotive)
        * Up/down arrow (speed up/slow down) (hold Alt to double the increment or Control to reduce)
        * 1 through 0 (set locomotive speed, 1 is stopped and 0 is max)
    * Direction
        * Left/right arrow (change direction)
        * Control+Left/right arrow (set direction as reverse / set direction as forward)
    * Functions
        * Numpad 0/backquote/Alt+0 (toggle lights/F0)
        * F1-F24 (toggle functions F1-F24)
        * Numpad 1-9, Alt+1-9 (toggle functions F1-F9)
        * Control+0-9 (toggle functions F10-F19, also works with Numpad)
        * Control+Alt+0-9 (toggle functions F20-F29, also works with Numpad)
    * Stopping and power
        * Shift (slow stop)
        * Spacebar (instant stop)
        * Enter (stop all locs)
        * Escape (power off/emergency stop)
        * Alt+G (power on)
* Locomotive shortcuts
    * Alt+P (apply saved function preset for current loc)
    * Alt+O (turn off all functions for current loc)
    * Alt+S (save current functions as a preset for current loc)
    * Alt+U (save current speed as a preset for current loc)
    * Alt+V (apply saved speed preset for current loc)
* Locomotive management
    * Pages
        * Comma/period, semicolon/colon, Alt+left/right arrow (cycle to previous/next loc page)
        * Alt+comma/period, Alt+semicolon/colon (jump to first/last loc page)
    * Key mappings
        * Control+F (quickly jump to/search for any locomotive)
        * Control+C (copy locomotive at currently active button)
        * Delete (clear mapping of currently active button)
        * Control+X (cut locomotive and clear mapping of currently active button)
        * Control+V (paste copied locomotive at currently active button)
        * Control+S (swap copied locomotive with currently active button)
        * Control+A (assign a new locomotive to the currently active button)
    * The locomotive database
        * Control+D (add a new locomotive to the database)
        * Control+N (edit locomotive notes)
        * Control+R (edit locomotive name or address)
        * Control+L (edit multi-unit)
        * Control+Delete (permanently delete locomotive from database)
* UI shortcuts
    * Control+M (show menu bar)
    * Backspace/Alt+backspace, CapsLock/Alt+CapsLock (cycle through tabs)
    * Plus/minus, \[/\], '/( (cycle through keyboards and layout pages, Control+plus/minus jumps 4 keyboards)
    * Slash/question mark, < (cycle through function tabs on the locomotive panel)
* Track diagram editor
    * Editing tiles
        * Left mouse click (cut hovered tile / paste new tile)
        * Middle-mouse click (rotate hovered tile)
        * Right mouse click (show all options)
        * Control+C (copy hovered tile)
        * Control+X (cut hovered tile)
        * Control+V (paste tile)
        * Control+R (rotate hovered tile)
        * Control+T (edit text of hovered tile)
        * Control+A (edit address of hovered tile)
        * Delete (delete hovered tile)
        * Control+Z (undo)
        * Control+Y (redo)
    * Picking squares
        * Shift+click (pick several squares at once)
        * Control+M (select by dragging a box)
        * Escape (let go of whatever is held; with nothing held, close the editor)
    * What is shown
        * Control+L (show text labels)
        * Control+D (show address labels)
        * Control+K (show the grid)
    * Pages and size
        * Plus/minus (next / previous page)
        * Control+I (increase diagram by 1 row and 1 column)
* Autonomy editor
    * A square's settings
        * Control+S (name the hovered square)
        * Control+N (show a station name on the hovered square)
        * Control+E (set the length of the hovered piece of track)
        * Control+B (set the hovered station's longest train)
        * Control+H (set the hovered station's home locomotive)
    * What is shown
        * Control+G (show track lengths)
        * Control+L (step through the station caption options)
        * Control+D (show address labels)
        * Control+K (show the grid)
    * Moving around
        * Plus/minus (next / previous page)
        * Shift+click (pick several squares at once)
        * Escape (put down an armed tool; with nothing armed, close the editor)
* Track diagram (with autonomy loaded and stopped, pointer over a station)
    * Control+X (pick up the train standing there)
    * Control+V (put it, or the locomotive selected in the main window, down)
    * Delete (take the train off)

![Diagram of TrainControl's keyboard mappings for locomotive and function control](assets/keyboard.png?raw=true)

## Requirements

* Install Java 8 on your computer (on Java 17 and later, the file chooser cannot list your drives)
* Requires a Marklin Central Station 2 or Central Station 3 connected to your network and layout
* The computer running TrainControl must be on the same network as your CS2/CS3 (Wi-Fi or Ethernet)
* Ensure that your firewall allows TrainControl/Java to access the local network
* Important: CS2/CS3 CAN bus and broadcasting needs to be enabled in the settings (TrainControl will show a warning popup after 15 seconds if this is not enabled)
* For fully autonomous operation, your network connection must be reliable (Ethernet or 5 GHz Wi-Fi recommended)

<details>
<summary>How to enable CAN broadcasting</summary>

**Central Station 3:**

From the upper-left corner of the CS3 main screen, click on the **System** icon.  Then click on **IP** toward the bottom of the page that is shown.

![Marklin Central Station 3 System page, where network settings are opened](assets/network1.png?raw=true)

Typically, you would either manually input a specific IP on this page, or have your router assign a static IP based on the CS3's MAC address.

In this example, the network mode is *auto (DHCP)* and the CS3 has been automatically assigned an *IP Address* of 192.168.50.25 on the local network. The *IP Gateway* and *DNS Server* are 192.168.50.1 with an *IP Network Template* of 255.255.255.0.

![Central Station 3 IP settings page showing the assigned local network IP address](assets/network3.png?raw=true)

Scroll down to **Settings CAN** and select *broadcast* from the dropdown.  Set the *Destination Address* to the highest allowed IP in your subnet, which usually means setting the last octet to 255.  In this case, the *Destination Address* is therefore 192.168.50.255.  You can safely ignore the warning icon shown.

![Central Station 3 CAN settings page with broadcast enabled and the destination address set](assets/network2.png?raw=true)

Many routers assign addresses within the 192.168.1.x range by default, so most users will need to set **192.168.1.255** here.

**Central Station 2:**

On the CS2, identical settings are found by going to the **Setup** tab in the upper-right of the main screen, then the *IP* and *CAN* sub-tabs, respectively.

---

</details>

**Limitations:**

* Central Station IP address must be manually entered the first time you run TrainControl (recommend configuring a static IP in your router).  Auto-detection is available, but is not guaranteed to find your Central Station.
* Central Station track diagrams can only be viewed with a CS2, or CS3 v2.5+ (local layouts can also be created and edited in TrainControl)

## Download and Run TrainControl

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

TrainControl uses your computer's language automatically.  To run it in a different language, add the locale flags shown below.

* ```java -Duser.language=en -Duser.country=US -jar TrainControl.jar``` (Force English locale/language)
* ```java -Duser.language=de -Duser.country=DE -jar TrainControl.jar``` (Force German locale/language)
* ```java -Duser.language=da -Duser.country=DK -jar TrainControl.jar``` (Force Danish locale/language)
* ```java -Duser.language=fr -Duser.country=FR -jar TrainControl.jar``` (Force French locale/language)
* ```java -Duser.language=pl -Duser.country=PL -jar TrainControl.jar``` (Force Polish locale/language)
* ```java -Duser.language=it -Duser.country=IT -jar TrainControl.jar``` (Force Italian locale/language)
* ```java -Duser.language=es -Duser.country=ES -jar TrainControl.jar``` (Force Spanish locale/language)
* ```java -Duser.language=nl -Duser.country=NL -jar TrainControl.jar``` (Force Dutch locale/language)

**Backing up and restoring your data:**

To make a backup, select "Backup TrainControl Data" from the File menu.  This saves a single zip file in the `tc_backup` folder with your locomotives and their icons, keyboard mappings, routes, and, for a layout stored on your computer, its track diagrams and autonomy setups.

If your track diagram is on the Central Station, TrainControl offers to download it first so that it is included.  On a CS3, the backup also includes the Central Station's own locomotive, route and accessory data.

To restore, close TrainControl, unzip the archive next to the JAR file, and start TrainControl again.  `LocDB.data` and `UIState.data` go next to the JAR; the layout folder can go anywhere, and you select it with "Open Layout...".

## Support TrainControl

TrainControl is free, with no ads or locked features.  I develop it in my spare time, so if you enjoy using it, please consider buying me a coffee.

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-Support%20TrainControl-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://www.buymeacoffee.com/traincontrol)

## Building from Source

Requires JDK 8 and the following libraries:

* org.json (json-20260814.jar)
* com.formdev.flatlaf.FlatLightLaf (flatlaf-3.7.2.jar)
* jcommander-1.69.jar, testng-6.14.3.jar (for unit tests only)

```ant -f /path/to/project/ -Dnb.internal.action.name=rebuild clean jar```

## License & Contact

TrainControl was created and is maintained by Adam Oest.

Feedback and suggestions are welcome at [traincontrol@adamoest.com](mailto:traincontrol@adamoest.com).

This is free software released under the GNU General Public License v3.

No copyright claim is made to any Central Station icons rendered during the use of this program.

Tab icons provided by Freepik.

## Changelog

* v3.0.0 [10/12/2026]
    - Features
        - Autonomy
            - Basics
                - Autonomy is now set up directly on your track diagram, so there is no separate graph to build or JSON file to write.
                - Autonomy needs a track diagram stored on this computer, and offers to download it from your Central Station if there is none.
                - Autonomy setups are saved as named configurations that can be copied, renamed, exported and imported, including an autonomy.json from an older version.
                - Station names typed onto the track diagram in older versions (as "Point:name") are imported automatically.
                - The Autonomy menu has a Documentation item that opens the automation guide.
            - Checking the Setup
                - The setup is checked before it can run, and clicking any finding jumps to the square it is about.
                - When the setup has errors, the autonomy button offers to fix them instead of refusing to start.
                - If an edit cannot be applied yet (such as an unpaired page link), the last working setup stays loaded, and a notice above the track diagram says so.
                - Anything that would change the setup is greyed out while trains are running.
            - Editing the Setup
                - The track diagram's Autonomy Setup menu opens the full editor on the square you right-clicked.
                - Station labels can be dragged onto another square in the autonomy editor.
                - Stations for Unavailable While Occupied can be picked by clicking them on the track diagram.
                - Arrival and departure functions are ticked on the function itself, and a train's length is set from the locomotive's right-click menu.
                - On the track diagram, Control+X, Control+V and Delete pick up, put down and remove the train at the station under the pointer.
                - The autonomy editor can hide its trains (Show Trains), and asks for a name when you make an unnamed sensor a station.
                - Bulk Tools can walk you through every missing track, station and train length, and clear every track length or station's longest train at once.
                - Bulk Tools has Allow Every Path, which reopens every one-way or closed piece of track at once.
                - Bulk Tools can make each train's present station its home, or put every train back on its home without moving anything.
            - Stations and Trains
                - Stations can have exit guard signals, held at red while a train stands there.
                - Stations can also have entry guard signals, set to red when a train arrives.
                - Stations can limit which side trains arrive from, and one-way restrictions are now drawn on the ordinary track diagram too.
                - A station out of service can no longer be sent to or driven through, even by hand.  To keep a parking track reachable by hand, untick Can Be Chosen in Full Autonomy instead.
                - A station's excluded locomotives can instead be the only ones allowed, which keeps out any locomotive added later.
                - A train can be paused from its station's right-click menu (Is Active), and each configuration remembers which trains are paused.
            - Routing Logic
                - You can choose how trains pick their route when more than one will do, such as over the shortest track, past the fewest stations, or to the station that has waited longest.
                - Track with no recorded length now counts as one unit, so the shortest and longest track options still work on an unmeasured layout.
                - Right-clicking a train lists the stations autonomy chooses from first, and parking tracks under More Destinations.
                - Atomic Routes stays on until every track and train autonomy uses has a length.
                - Switches without a decoder can be drawn as Static switches, which autonomy runs through from the legs towards the points only.
            - Running and Watching
                - A running train's route is drawn along the track: blue ahead, grey behind, and the train itself in orange.
                - When a train is not going anywhere, TrainControl now says why, in the locomotive list and with the editor's Why not Moving? tool.
                - If the track power goes off during a run, autonomy pauses and resumes once power is back.
                - A page link switched off for autonomy is greyed out on the track diagram, not only while editing.
                - The editor can show only the directions trains may run, and the track diagram can draw them in green.
                - Names of stations autonomy will never choose can be hidden (Preferences → Autonomy → Show Inactive Labels).
        - Routes
            - Safety
                - While autonomy runs, a route no longer changes a switch under a train or turns a signal green at an occupied platform.
                - A route with an emergency stop can no longer hold other commands, and older ones are split in two automatically.
            - Editing Routes
                - A new route editor builds each command from dropdowns instead of typed text.
                - Route conditions are written as an indented list instead of with brackets.
                - Commands captured from the layout can now also be used as a route's conditions, and routes from the Central Station open read-only.
            - Running and Finding Routes
                - Every route in the Routes tab has a play button that runs it straight away.
                - Find Route on the Routes menu accepts a full or partial name.
                - Imported routes arrive with automatic firing switched off, and you are asked whether to turn it back on.
                - Deleting or saving a route created in TrainControl no longer forces a full sync with the Central Station.
        - Track Diagrams
            - Viewing
                - Stations are marked on the track diagram with a circle, diamond or square, showing whether trains turn around there, and with an orange sign when out of service.
                - A small locomotive marks each train on the track diagram, pointing the way it faces.
                - Station labels are now light grey ovals (or blue, if you untick Grey Station Labels) that no longer cover the square they name.
                - Track diagrams have a Medium size between Small and Large, with S, M and L pop-up buttons.
                - Track diagrams scroll faster, half a square at a time in both directions.
                - Track diagrams show a spinner while they are being drawn or loaded.
                - The tunnel and overpass squares have new, cleaner icons.
            - Editing
                - In the track diagram editor, Shift+click picks several squares to drag, copy, rotate or delete together, and one undo takes it all back.
                - Control+M, or Select by Dragging a Box, picks squares by dragging a box round them.
                - The track diagram editor can add or remove a column and a row with a matching pair of controls.
                - The "paste entire row" and "paste entire column" options were removed.
            - Pages
                - The + and - keys step through the pages in both editors.
                - Layouts → Manage Pages → Combine Linked Pages... makes one page from a page and every page its links lead to.
                - A track diagram page can be saved as a picture, whole and at any size.
        - Central Station
            - Backups are now a single archive of everything, and can download the track diagram from the Central Station first.
            - Syncing with the Central Station no longer freezes the interface.
            - The Layouts menu says where the track diagram comes from: this computer, the Central Station, or nowhere yet.
        - Interface
            - Redrew the sidebar icons in a plain, flat style.
            - Locomotive icons can be cropped and panned when you pick them, without changing your own picture.
            - Locomotive keyboard pages are now capped at 50.
            - **If you go back to v2.8.2 or earlier, keep a copy of your settings first:** those versions read only the first 10 keyboard pages, and discard the rest when they save.
            - The autonomy setup is translated into all eight languages.
    - Bug Fixes
        - Autonomy
            - Fixed a long-standing bug where track shorter than a train was released while the train still stood on it.
            - Fixed bug where a path that failed part way through stopped routes waiting on its sensor from firing.
            - Fixed bug where autonomy could get stuck in a running state when every locomotive was skipped at startup.
            - Fixed bug where a train's right-click menu on the track diagram listed two fewer destinations than it should.
            - Fixed bug where "return home" refused to run when two home stations share a track sensor.
            - Fixed bug where "return home" could plan an impossible move onto track sharing the train's own sensor.
        - Track Diagrams
            - Fixed bug where one unreadable page stopped a whole layout folder from loading.
        - Central Station
            - Fixed bug where a short message from another device on the CAN bus was mistaken for an emergency stop.
            - Fixed bug where an interrupted layout download left a half-written file behind.
            - Fixed bug where an imported MFX locomotive with no address was given an invalid one.
            - Fixed bug where TrainControl ignored the Central Station's report of an accessory set to the same position twice.
            - Pressing Stop while not connected now says so in the log.
        - Interface
            - Confirmations that delete or overwrite something no longer have Yes selected by default.
    - Code
        - Updated the JSON library to json-20260814.jar and FlatLaf to 3.7.2, and dropped the GraphStream libraries the old autonomy graph needed.

* v2.8.2 [9/25/2026]
    - Autonomy Bug Fixes
        - Fixed bug where renaming a locomotive (or one in a multi-unit) took it off its station, so autonomy could send another train there
        - Fixed bug where giving a multi-unit member the address of a train on the graph left that train on the graph, so autonomy and the multi-unit could both move it
        - Clearing a station’s priority no longer stops autonomy from sending trains out, or the graph from being saved
        - Fixed bug where a running train could miss its stop sensor and drive through its station while another train’s route was being set
        - Return Home no longer stops every train when one has no speed set or is not at a station; such a train is now skipped or named before anything moves
        - A locomotive placed on the graph by hand without a speed now gets the default speed, instead of being skipped by Start and Return Home
        - Double-clicking Start no longer starts every train twice
        - One timetable entry that can no longer be loaded, such as after its locomotive was deleted, no longer wipes out the whole timetable
        - If a train’s trip fails part way, autonomy now stops and says why, and no train is sent until the graph is validated again.  Previously the other trains carried on, and the failed train could later be sent off from the wrong place
        - After such a failure, trains are kept where the run left them, and the failed train at the last place it is known to have reached
        - Messages from the track diagram’s right-click autonomy menu no longer open behind the main window when Window Always on Top is ticked
    - Route Bug Fixes
        - Fixed bug where editing a route, or switching it on or off, removed it from the routes autonomy is set to activate
        - Importing routes no longer garbles accented letters in route and locomotive names
        - Routes and autonomy files exported by TrainControl 2.7.3 or earlier also import with their accented letters intact
    - Locomotive Bug Fixes
        - If the locomotive list or keyboard pages cannot be read at startup, a copy of the file is now kept in the tc_backup folder instead of being saved over as empty
        - Page names are no longer lost when going back to 2.8 after using TrainControl 3.0 with fewer than ten pages
    - Central Station Bug Fixes
        - After a short network drop, TrainControl no longer reports the connection as lost until restarted, and autonomy no longer keeps cutting the track power because of it
        - Finding the Central Station automatically no longer fails when it answers slowly, or when one reply on the network is lost

* v2.8.1 [8/17/2026]
    - Autonomy Bug Fixes
        - A locomotive placed on the graph without a speed is no longer dispatched at speed zero, which blocked autonomy until the graph was reloaded
        - Double-clicking the empty space below a short list of available paths no longer starts the last path in the list
        - Fixed bug where a train could be sent down a different path than the one double-clicked, if another locomotive arrived or departed at that moment
        - An error while redrawing the graph can no longer leave a locomotive stuck part way through a route, needing the graph reloaded
        - Feedback sensors are no longer ignored for a while after the computer clock is corrected backwards, such as by an automatic time sync
    - Locomotive Bug Fixes
        - Fixed bug where pressing Go on the Central Station (or clicking a track diagram accessory) while trains were running discarded their running time from the statistics
        - The duplicate address check no longer reports an address as free when one locomotive is already using it
    - Route Bug Fixes
        - Fixed bug where renaming a locomotive stopped every route whose condition named it from firing, although the route still looked enabled
        - Fixed bug where capturing commands into a route that drives more than one locomotive kept only the last one
        - Fixed bug where importing a routes file with two routes of the same name left the rejected one running in the background until TrainControl was restarted
        - Cancelling the bulk enable or disable prompt now works
        - Locomotive names containing a comma or a bracket can no longer be used in route conditions, as they could change which locomotive a route command referred to
    - Track Diagram Bug Fixes
        - Fixed bug where renaming a track diagram page to the same name with different capitalization, such as "Main" to "MAIN", deleted the page instead of renaming it
        - Clicking a tile in the track diagram editor no longer counts as an edit, so the editor stops asking whether to save changes that were never made
        - Fixed an error when clicking a tile in the editor’s component palette rather than dragging it onto the diagram
    - Central Station Sync Bug Fixes
        - Fixed bug where speed and function changes made at the Central Station were no longer shown after a failed initial sync
        - The locomotive database is no longer occasionally left unsaved when a backup or an automatic sync runs at the same moment as an edit

* v2.8.0 [8/2/2026]
    - Added French, Italian, Spanish, Dutch, and Polish translations
    - Locomotive Control Page
        - Removed copy-to-next/-previous page option from button right-click menus (now redundant with 2.7.3's drag and drop)
    - Autonomy
        - Added Path Integrity Validation: a locomotive will not run unless the Central Station confirms its switches and signals (configurable under Preferences)
        - Added a "return home" feature that will return all locomotives back to where they started, if routing is possible.
        - You can now pick which locomotive belongs at each station, so "return home" brings each one back to the station you chose instead of the one it happened to start on
        - A locomotive standing at the station you assigned it to now shows its location highlighted in teal; * marks one standing at its timetable starting point
        - Stations that have a home locomotive are outlined in the graph: solid when that locomotive is standing there, dotted when it is elsewhere.  Can be switched off under Display Options
        - Double-clicking a locomotive in a station's excluded-locomotives window now moves it to the other side
        - Added a Display Options setting to hide the connections leading into reversing points, which tidies up a busy graph
        - Reversing stations are no longer used by autonomy on its own, as destinations or to drive through.  You can still send a train to one yourself, and “return home” can still park trains there
        - The route list now marks stations autonomy will never send a locomotive to with a dash
        - A route you pick yourself can no longer run a train through a point you switched off, though you can still send a train to a switched-off station and drive it away again
    - Central Station Sync Bug Fixes
        - A locomotive whose name contains an equals sign is no longer dropped from its multi-unit when importing from the Central Station
        - Downloading the track diagram from the Central Station no longer fails when a page name contains a slash, colon, or similar character
        - Fixed bug where entering the Central Station's network name instead of its IP address would report it as unreachable, even though it had just responded
        - A locomotive address change from the Central Station is now postponed while trains are running, and the log says so
    - Autonomy Bug Fixes
        - Fixed bug where only one locomotive could be triggered from the track diagram in semi-autonomous mode
        - A path is no longer used if one of its switches or signals is missing from the database
        - Fixed bug where a crossing could stay marked as occupied after a train finished its route (only with lock edges and atomic routes turned off)
        - Path integrity validation now waits for the Central Station to confirm every switch and signal, including ones already in position
        - Reloading or re-validating the graph while locomotives are running now warns first and stops them
        - A locomotive part way through a route when the graph is reloaded now stops at its next point instead of running on untracked
        - Fixed bug where renaming a locomotive stopped its station exclusions from applying, so it could be sent to a station it was set never to visit
        - Changing a locomotive's address, or having the Central Station report a new one, no longer stops its station exclusions from applying
        - Deleting a locomotive now also removes it from any station exclusion lists it was on
    - Timetable Bug Fixes
        - Fixed bug where a recorded timetable played back with its pauses shifted by one route
        - Fixed bug where stopping a timetable and resuming it could forget that some routes had already finished
    - Route Bug Fixes
        - Fixed bug where a route condition would fail to parse if a locomotive's name contained the word AND or OR, such as NORD or MOTOR
        - Fixed bug where a DCC switch or signal used as a route condition was shown with the wrong protocol
        - Fixed bug where a route could stop responding if one of its commands failed
        - Fixed bug where a conditional route stopped firing for the rest of the session if a condition referred to a locomotive not placed on the autonomy graph
        - Fixed bug where a route imported from the Central Station 3 would be silently skipped if it set a locomotive's speed or direction before a switch or signal with a delay
        - Fixed bug where an automatic route loaded from a layout file without a trigger type waited for the opposite s88 change
        - A mistyped or incomplete route command now explains which line could not be read, instead of showing a technical error
        - Fixed bug where a route containing a feedback entry lost the command directly after it when the route was opened in the editor and saved again
        - Fixed bug where 3-way switches created via the route editing wizard, or in routes imported from a Central Station 3, would sometimes fail to switch left
        - Fixed bug where adding a 3-way switch as a route condition made the condition impossible to save
        - Fixed bug where capturing a 3-way switch by clicking it on the track diagram could record its two commands in the wrong order
        - Pauses in routes imported from a Central Station 2 no longer lose their fraction of a second, and pauses shorter than one second are no longer dropped
        - Fixed bug where a pause on a route step was applied to an earlier step instead, if both steps used the same switch or signal
        - Fixed bug where opening and saving a route with certain combinations of AND and OR conditions could change when it fires
    - Locomotive Bug Fixes
        - Fixed bug where changing a locomotive to a decoder type with fewer functions could make it impossible to edit or place on the autonomy graph
        - Fixed bug where clearing a locomotive’s custom icon while not connected to the Central Station left it with no image for the rest of the session
    - Multi-unit Bug Fixes
        - Fixed bug where a multi-unit locomotive set to run faster than its leader stopped keeping pace above a certain speed
        - Fixed bug where deleting a locomotive that was linked to another one left it still being driven by the lead locomotive until TrainControl was restarted
        - Fixed bug where renaming a locomotive in a multi-unit stopped TrainControl from recognizing it as linked
        - Fixed bug where checking for locomotives renamed in the Central Station could delete one of two locomotives sharing an address; such addresses are now reported and left alone
    - Track Diagram Bug Fixes
        - Fixed bug where adding columns to a track diagram could fail on layouts taller than they are wide
        - Track diagram tiles no longer occasionally stop refreshing
        - Fixed bug where track diagram pages whose names contain accented characters could not be loaded from a local layout folder
        - Editing a track diagram, or saving a route that appears on one, no longer freezes TrainControl for several seconds, and cycling between pages while editing is faster
        - Clicking a 3-way switch on a track diagram no longer briefly freezes TrainControl, and neither does clicking any switch or signal while the track power is off
    - Accessory Bug Fixes
        - A route or autonomy command can now refer to an accessory as either a switch or a signal (e.g. "Signal 5" for "Switch 5")
        - A stray space in a hand-typed sensor line no longer flips the state it waits for, and a mistyped direction is now reported as an error
    - General Bug Fixes
        - Update notices now work even if a release name contains extra text after the version number
        - Your locomotive database, window layout, and autonomy graph are now saved safely, so a power loss while saving can no longer leave them unreadable
        - The debug log no longer slows TrainControl down during a long session

* v2.7.4 [7/25/2026]
    - UI
        - Backups are now saved to the tc_backup folder in the current directory
    - Bug fixes
        - Minor UI performance improvements
        - Fixed rare race conditions in autonomy code

* v2.7.3 [7/23/2026]
    - UI
        - Added support to drag-and-drop locomotive mapping buttons (when the power is off), including across pages
        - When downloading an update file, progress is now shown in the menu bar, and the update file is only written once the download completes
        - Fixed bug where the UI would freeze until the connection timed out if the Central Station became unreachable
        - Fixed bug where declining the prompt to reset a locomotive's function customizations would leave the reset button permanently disabled
        - Fixed bug where locomotive icons could report a spurious image loading error if a button was cleared while its icon was still loading
    - Bug fixes
        - Fixed bug where CS2 locomotive names containing = would cause importing to fail
        - A single incomplete route or locomotive in the Central Station database no longer aborts the import of all the others, and the skipped entry is now named in the log
        - Fixed missing error messages that could mask the underlying error when deleting an unknown autonomy point, or when the locomotive statistics page failed to render

* v2.7.2 [5/26/2026]
    - UI
        - Added right-click menu to all function icons (with shortcuts to save/recall presets and the previous functionality of editing functions)

* v2.7.1 [5/10/2026]
    - UI
        - Left-clicking a locomotive on a track diagram autonomy station will now set it as the active locomotive if a key mapping exists
        - Fixed bug where deleting/editing locomotives from the locomotive selector would not automatically refresh the locomotive list

* v2.7.0 [5/1/2026]
    - Added support for new CS3 firmware v2.6.0 (March 2026) and backwards-compatibility with older versions
    - UI
        - Improved the intuitiveness of track diagram station labels in autonomous operation
        - With autonomy enabled, clicking on a blank square in the track diagram will now show a popup menu to start/stop train operation
        - With autonomy enabled, empty stations on the track diagram will now allow locomotives to be moved around via the right-click menu
        - For added clarity, all controls in the route editor for routes imported from the Central Station will now be greyed out
        - Fixed bug where autonomy locomotives could be edited via the track diagram while autonomy was running
        - Fixed bug where track diagrams could be edited while autonomy was running
        - Fixed bug where CS2/3 auto-detection was not working
    - Code
        - Refactored code to make TrainControl classes more generic, suitable for future expansion beyond Marklin's CS3
        - Updated JSON library to json-20251224.jar

* v2.6.x [11/2025 - 3/2026]
    - Added translations, starting with Danish and German, and drag and drop in the track diagram editor.  Made autonomy settings and destinations quicker to reach from the track diagram; these versions support CS3 firmware up to v2.5.x and read save files from v2.4.3 onwards.

* v2.5.x [4/2025 - 9/2025]
    - Added a built-in track diagram editor that also works on Mac and Linux, and support for DCC switches and signals up to address 2048.  Routes can trigger other routes and react to where autonomy trains are, and the locomotive database gained notes on years and railways, a search for similar locomotives, and CSV export.

* v2.4.x [12/2024 - 3/2025]
    - Any locomotive can be made into a multi-unit, and route conditions can combine AND and OR in a clearer editor.  Autonomy gained a limit on how many trains run at once, per-station speed adjustment, a power cut-off when the network is slow, and train names on the track diagram.

* v2.3.x [11/2024 - 12/2024]
    - Gave TrainControl a modern look with a menu bar and icon tabs, and let individual trains be paused during autonomous operation.  The Central Station can be found automatically at startup, and function icons can be copied from one locomotive to another.

* v2.2.x [6/2024 - 9/2024]
    - Routes and Track Board layouts can be imported from the CS3, and locomotives can have notes.  The autonomy graph gained keyboard shortcuts for placing and removing trains and can be dragged around, and the Stats tab gained a 30-day usage chart.

* v2.1.x [4/2024 - 6/2024]
    - Added timetables, which record trains' journeys and play them back in order, and support for all 296 CS3 function icons as well as your own pictures.  Locomotives can be kept away from chosen stations, and autonomy runs more reliably on slower PCs.

* v2.0.x [11/2023 - 4/2024]
    - Made TrainControl an all-in-one layout controller, with a track diagram editor (Windows), custom locomotive and function icons, usage statistics, and better syncing with the Central Station.  Later 2.0 versions added speed and function commands in routes, ten named mapping pages, and shortcuts for copying, moving, and finding locomotives.

* v1.10.x [8/2023 - 10/2023]
    - Added reversing points for one-click parking, station priorities, and a settings tab, so autonomy no longer needs hand-edited files.  Also started tracking how long each locomotive runs.

* v1.9.x [7/2023]
    - Autonomy graphs can be built and edited entirely with the mouse, and the main window can be kept on top.  Later 1.9 versions added an option to free track as soon as a train has passed it, and fixed switches being missed when several trains started at once.

* v1.8.x [5/2023 - 7/2023]
    - Introduced autonomous operation: trains drive themselves between stations on a graph, with a window to watch them and controls to send them by hand.  Later 1.8 versions added smarter route choice, graceful stops, terminus stations, and train lengths.

* v1.7.x [4/2023]
    - Routes can fire automatically from S88 sensors, with conditions on other sensors, and a wizard helps create them.  Route timing, order, and conditions are read from the CS2, and there are now eight locomotive pages.

* v1.6.x [10/2022 - 4/2023]
    - Added CS3 and DCC support, layouts loaded from your computer, speed sliders under each button, and many function hotkeys.  Routes can be added, edited, duplicated, and sorted in TrainControl.

* v1.5.x [12/2021 - 9/2022]
    - Improved CS2 compatibility, with function types and icons read from the Central Station, multi-unit locomotives, and many new layout icons.  Locomotives can be copied between buttons from a right-click menu, and the first programming interface for automating trains was added.

* v1.4.x [11/2021 - 12/2021]
    - Layouts can be shown in any number of pop-up windows, and hotkeys were added for functions F1 to F16.  Also added double slip switches and other less common Marklin layout pieces.

* v1.3.2
    - First public release.