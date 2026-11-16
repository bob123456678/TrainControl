# TrainControl for Märklin Central Station 2 & 3

[![Latest release](https://img.shields.io/github/v/release/bob123456678/TrainControl?label=latest%20release&color=success)](https://github.com/bob123456678/TrainControl/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/license-GPL%20v3-blue)](https://www.gnu.org/licenses/gpl-3.0)
![Java 8](https://img.shields.io/badge/Java-8-orange)

**Free, open-source software for controlling and fully automating your Märklin (Marklin), Trix, or DCC model railroad from your computer.**  Runs on Windows, macOS, and Linux.

Available in 🇬🇧 English · 🇩🇪 Deutsch · 🇩🇰 Dansk · 🇵🇱 Polski · 🇫🇷 Français · 🇮🇹 Italiano · 🇪🇸 Español · 🇳🇱 Nederlands

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-Support%20TrainControl-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://www.buymeacoffee.com/traincontrol)

![TrainControl main window, showing locomotive control with keyboard mappings, locomotive thumbnails, and function buttons](assets/main23_2.png?raw=true)

TrainControl connects to a Central Station 2, 3, or 3 Plus over your network.  It is designed for model railways with many locomotives, where the standard Marklin UI makes common tasks (such as quickly switching between locomotives or triggering functions) overly tedious.  It is a complete replacement for the CS2/CS3 when operating your layout, with the Central Station serving solely as the track interface and MFX locomotive database.  If your existing controller is taking the fun out of running your trains, consider trying TrainControl!

**What's new in 3.1.0**

*Station Settings.*  Everything about a station is now in one window: right-click a station on the track diagram and choose `Station Settings...`.

<img src="assets/station_menu.png?raw=true" alt="Right-clicking the station Ashby on the track diagram, with Station Settings... right under its name in the menu" width="300"> <img src="assets/station_settings_ashby.png?raw=true" alt="The Station Settings window for Ashby, with what trains may do there on the left and a picture of the station on the right" width="500">

* Tick whether trains can stop there and whether they can run through, or press a button under `Presets` to set it up in one click.
* Choose which ways trains may leave the station under `May depart`, or click a station in the autonomy editor to open its window.
* Under `Advanced`, `Allowed trains` keeps some trains away or lets only the ones you pick stop there.
* A table beside the picture tells you what trains may do at the station, whether autonomy sends them or you do.

*Locomotive Settings.*  All of a locomotive's settings are now in one window, with tabs for `Basic Settings`, `Functions`, `Notes`, `Multi-Unit` and `Autonomy`: right-click its button and choose `Locomotive Settings...`, or press Ctrl+E.

<img src="assets/ui_right_click.png?raw=true" alt="Right-clicking a locomotive's button, with Locomotive Settings... second in the menu" width="340"> <img src="assets/locomotive_settings.png?raw=true" alt="The Locomotive Settings window on its Basic Settings tab, with the name, decoder type, address and picture of a locomotive, and a gray label saying the picture is a Local File" width="460">

* `Basic Settings` has the name, address and picture, and a small gray label tells you whether the picture came from your Central Station or from a file on your computer.
* `Notes` keeps the years it ran in real life, its railway and your own notes, and `Find Similar Locomotives...` finds others like it in your collection.
* `Autonomy` has what autonomy needs to know about it, such as how long the train is and whether it can change direction.

*Autonomy on your track diagram.*  Since 3.0.0 you set up automation right on the track diagram, with no files to write: name your stations, click the track to show which way trains may run, and drag each train to the station where it stands.  See [Full Autonomy](#full-autonomy) and [the autonomy guide](Automation.md) to get started, and the [Changelog](#changelog) for everything else in 3.1.0.

**Why TrainControl?**

* Conveniently control any locomotive instantly from your keyboard
* Interactive track diagrams that you can edit in the app, in any number of windows
* Fully hands-off, UI-built train automation based on S88 sensors and digital switches (signals optional)
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

Right-click a locomotive button for its quick actions, such as function and speed presets, or choose `Locomotive Settings...` (or right-click the locomotive's name) to change its name, address, picture, functions, notes and multi-unit in one window.  A function button's right-click opens the same window on that function.  `Add Locomotive...` (Ctrl+D) opens it too, ready for a new locomotive.  If another locomotive already uses the same address, a warning triangle shows next to `Address` on the `Basic Settings` tab; click it to see which.  A locomotive can't be edited while autonomy is running or while it is moving with the power on: the window then opens grayed out.  To copy locomotives between TrainControl installations, use `Export to CSV...` and `Import from CSV...` on the `Locomotives` menu.

![Right-click menu on a locomotive button, showing options for function presets, preferred speed, and multi-unit setup](assets/ui_right_click.png?raw=true)

The Locomotive Settings window, on its `Basic Settings` tab, with the locomotive's name, address, decoder and picture.

<img src="assets/locomotive_settings.png?raw=true" alt="The Locomotive Settings window on its Basic Settings tab, with the name, decoder type, address, preset speed and picture of a locomotive" width="720">

![TrainControl locomotive selector, used to assign a locomotive from the database to a keyboard button](assets/ui_sel_loc.png?raw=true)

Multi-units can be configured in TrainControl, which can be simpler than using the Central Station.

![Configuring a Marklin multi-unit in TrainControl by linking two DSB railcars, ER 2035 and MF 5028, into one unit](assets/multiunit.png?raw=true)

**Layout View & Layout Editing**

Track diagrams for your layout are downloaded automatically from the CS2 / CS3 (Track Boards only), or you can customize them in layout files kept on your computer.  All components (switches, signals, S88, routes) are clickable and reflect the layout state.  Multiple pages can be opened across unlimited popup windows.

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
    - From the Layouts menu in TrainControl, switch to the Central Station layout, then select "Download Central Station Layout Files...".
    - The layout will be saved to a folder of your choosing, and TrainControl will switch to it as the local data source.  It will now be shown in the Track Diagram tab and can be edited if desired.
    - If you already have layout files on your computer, use "Open Layout..." in the Layouts menu to select the folder containing them.
- Otherwise, to create a new layout:
    - Start TrainControl, then from the Layouts menu, click on "Create New Layout..."
    - If no Central Station layout is detected and no static layout is manually selected, TrainControl will automatically create an editable demo layout at startup.

Complete editing support is accessible via the "Edit" button within the Track Diagram tab.  In the editor, a strip of icons under `Page` adds, duplicates, renames, deletes and moves pages, and the `Tools` menu has the rest, such as `Sort Pages by Name`.  Pages are shown in the order they have in the layout's files.  This will let you fully customize your layout.

If you change the local files, clicking on "Sync Database w/ Central Station" in the Locomotives menu will update the layouts.  This effectively lets you customize the layout even without a CS2/CS3.  Some users might find this easier than inputting data into the Central Station UI.

Some sample files are included in the `cs2_sample_layout` folder.

---

</details>

<a id="full-autonomy"></a>**Full Autonomy**

TrainControl can run your trains on its own.  From version 3.0, you set this up on the track diagram itself, in the autonomy editor: TrainControl works out how your track connects, and you set up stations, directions and settings by clicking and right-clicking squares.  A station's settings are all in one `Station Settings` window.  The setup is checked before it runs, and each problem it finds takes you to the square it is about.

You need S88 sensors (at least one per station) and digital switches; signals are optional ([what you need](Automation.md#what-you-need)).  Once you have placed your trains, TrainControl keeps track of where every train is, and saves where they stand when you exit.  You can let your trains pick destinations at random, or send a particular train to a station.  If you set up autonomy in an older version, its [JSON configuration file](AutomationAPI.md) can be imported from the Autonomy menu.

**Full guide:** [The autonomy editor: a user guide](Automation.md)

![The autonomy editor: the track diagram with its stations named, a list of things to look at below it, and tools and settings on the right](assets/autonomy_editor3.png?raw=true)

A station's settings are all in one window, beside a picture of the station and a table of what trains may do there.

![The Station Settings window for a station called Bramley, with its name, what trains may do there, which sides trains may arrive from, and a picture of the station](assets/station_settings.png?raw=true)

![A train running under autonomy on the track diagram: its route ahead in blue, the track it has passed and still holds in dark gray, and a second train standing at its station](assets/autonomy3.png?raw=true)

The track diagram shows each running train's route, the track it is holding, and where every train is standing.  While trains are stopped, you can right-click a station to place a train there, or to open the settings of the train standing on it.  In the autonomy editor you can also drag a train from one station to another.

When a train is not moving, `Why not Moving?` in the autonomy editor shows where it can go, with the route it would take to each station, and why it cannot go to the others.

![Why not Moving? on BR 218 at Bramley: it can go to Ashby and Carlton, both marked green with the routes to them drawn in yellow, while Thornbury, where ICE 3 is standing, is marked gray](assets/automation/08-why-not-moving.png?raw=true)

You can set home stations and have TrainControl automatically route all your trains back to where they started. You can also record timetables and play them back, with the same protections as autonomous operation.

<img src="assets/timetable3.png?raw=true" alt="The timetable in TrainControl: a captured sequence of journeys, played back in order" width="600">

To send a train to a station yourself, right-click it on the track diagram.

<img src="assets/easyauto3.png?raw=true" alt="Right-clicking a train on the track diagram to send it to a station" width="500">

**Routes**

Conditional routes can be defined for semi-automatic layout operation, such as setting a switch to guide an incoming train to an unoccupied station track, or triggering an emergency stop.  Manual routes can also be defined and activated directly or from the Track Diagram tab.

In addition to all Central Station functionality, complex logical expressions are supported.

![TrainControl's route editor: each command a row of dropdowns, and its conditions in outlined groups, each with its rule written on its outline](assets/ui_route3.png?raw=true)

**Keyboard**

Useful for testing, the keyboard lets you control individual accessories directly by their digital address.  Each key has a red half and a green half, and clicking either sends that color, even if it is already lit.  The 2,048 addresses are on 16 tabs of 128.  The cumulative number of actuations of each accessory is shown as a tooltip, and `Export Counts` saves them all to a CSV file.

![Accessory keyboard in TrainControl, used to control switches and signals directly by digital address](assets/ui_keyboard.png?raw=true)

**Statistics**

Monitor the usage of different locomotives.

![Locomotive usage statistics in TrainControl, showing runtime per locomotive over the past 30 days](assets/stats23.png?raw=true)

**Log**

The Log tab shows what TrainControl is doing.  To scroll it sideways, hold Shift and turn the mouse wheel; a trackpad's sideways swipe only works on newer versions of Java.

## Features

* Easily control locomotives (MM2, MFX, DCC), multi-units, signals/switches (MM2, DCC), and routes
* Download locomotive, layout, and route information from the CS2/CS3
* Customize locomotive icons and function icons without needing to set them in the CS2/CS3
* Edit a locomotive's name, address, picture, functions, notes, multi-unit and autonomy settings in one window, and add locomotives there
* Export locomotives to a CSV file and import them into another TrainControl
* Powerful keyboard interface
    * Configure up to 50 different key mappings for up to 1,300 locomotives
    * Convenient hotkeys for power off, emergency stop, and smooth deceleration
    * Simultaneous operation across multiple PCs
    * An accessory keyboard of 2,048 addresses on 16 tabs, with a red and a green half on every key, a key number you can click to switch it, and an export of the actuation counts
* Track diagrams
    * View unlimited layout diagrams, with support for multiple windows
    * Toggle signals, switches, lights, uncouplers, and routes
    * View S88 feedback, read from a CS3 when TrainControl connects, including sensors on a Link S88, so the diagram shows each sensor as it really is
    * Full UI for editing track diagrams
    * Add, duplicate, rename, delete and rearrange pages from a strip of icons
* Basic automation
    * Set up automatic and conditional routes triggered by S88 feedback modules
    * Automate bulk tasks such as turning off all functions
    * Set function and speed presets for locomotives
* Advanced automation
    * Fully autonomous train operation, set up on the track diagram itself in the autonomy editor ([user guide](Automation.md)), with every train's location tracked by S88 sensors
    * Several named autonomy configurations per layout; an older JSON configuration can be imported
    * Set up a station in one window, with a picture of it and a table of what trains may do there, or set several stations at once
    * Send a train to a station by right-clicking it on the track diagram
    * Find a point by name, filter the list of things to look at, and drag a train from one station to another in the autonomy editor
    * The setup is checked before it runs, and each problem opens the editor on the square it is about
    * Customize autonomous operation by setting station priority, maximum train lengths, track lengths, speed multipliers, and maximum train idle time
    * Choose how trains pick their route, such as over the shortest track or past the fewest stations
    * Pair a station with signals that turn red while a train is standing there, and back to green when it leaves
    * Restrict which directions a station will accept trains from, or make a station that trains must stop at and can't run through
    * Record and play back timetables, and send every train back home
    * Hide the names of stations autonomy will never send a train to
* Back up everything in one archive (locomotives, keyboard mappings, track diagrams, routes and
  autonomy setups) and restore it on another machine
* Hide the tooltips and shortcut keys in menus, if you don't need them
* Programmatic layout control via Java API (uses CAN protocol; [see documentation](AutomationAPI.md)) 
* Monitor locomotive usage stats

## Keyboard Commands / Key Mappings

TrainControl's key mappings are designed to allow you to send any command nearly instantly.

The primary controls are the up and down arrows for speed, the left and right arrows for direction, the letter keys to pick a locomotive, the number keys to set its speed (functions are on F1 to F24), Spacebar for an instant stop, Enter to stop all locomotives, and Escape for power off.  The rest follow the screenshots below, and the full list is underneath.

![Diagram of TrainControl's main window keyboard mappings, showing the plain, Control and Alt keys for locomotive and function control](assets/keyboard.png?raw=true)

![Diagram of the keyboard shortcuts in the track and autonomy editors](assets/keyboard_editor.png?raw=true)

<details>
<summary>Show all keyboard commands</summary>

* Primary controls
    * Selecting and speed
        * A-Z letter keys (select a locomotive; Alt with a letter that has no Alt shortcut of its own does the same)
        * Up/down arrow (speed up/slow down by 4%) (hold Alt to double the increment or Control to reduce it to 1%)
        * 1 through 0 (set locomotive speed, 1 is stopped and 0 is max)
    * Direction
        * Left/right arrow (switch direction)
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
        * Control+G (start autonomous operation)
        * Control+Q (gracefully stop autonomy)
        * Control+H (return locomotives home, after asking first)
* Locomotive shortcuts
    * Alt+P (apply saved function preset for current loc)
    * Alt+O (turn off all functions for current loc)
    * Alt+S (save active functions as a preset for current loc)
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
        * Control+B (move copied locomotive to currently active button, clearing the button it came from)
        * Control+S (swap copied locomotive with currently active button)
        * Control+A (assign a new locomotive to the currently active button)
    * The locomotive database
        * Control+D (add a new locomotive to the database, in the Locomotive Settings window)
        * Control+E (open Locomotive Settings)
        * Control+N (open Locomotive Settings on the notes)
        * Control+R (open Locomotive Settings to change the name or address)
        * Control+L (open Locomotive Settings on the multi-unit)
        * Control+Delete (permanently delete locomotive from database)
* UI shortcuts
    * Control+M (show or hide the menu bar, where it is not part of the window's title bar, such as on a Mac)
    * Backspace/Alt+backspace, CapsLock/Alt+CapsLock (cycle through tabs)
    * Minus/plus, \[/\], '/( (previous/next accessory keyboard, or previous/next layout page on the Track Diagram tab; Control with these jumps 4 accessory keyboards)
    * Slash/question mark, < (cycle through function tabs on the locomotive panel)
* Track diagram editor
    * Editing tiles
        * Left mouse click (cut hovered tile / paste new tile)
        * Middle-mouse click (rotate hovered tile)
        * Right mouse click (show all options)
        * Control+C (copy the picked squares, or the hovered tile when none are picked)
        * Control+X (cut the picked squares, or the hovered tile when none are picked)
        * Control+V (paste tile)
        * Control+R (rotate the picked squares, or the hovered tile when none are picked)
        * Control+T (edit text of hovered tile)
        * Control+A (edit address of hovered tile)
        * Delete (delete the picked squares, or the hovered tile when none are picked)
        * Control+Z (undo)
        * Control+Y (redo)
        * Control+Enter (save changes)
    * Picking squares
        * Shift+click (pick several squares at once)
        * Shift+drag (pick a box of squares)
        * Control+M (select by dragging a box)
        * Escape (let go of whatever is held; with nothing held, close the editor)
    * What is shown
        * Control+L (show text labels)
        * Control+D (show address labels)
        * Control+K (show the grid)
    * Pages and size
        * Plus/minus, also on the number pad (next / previous page)
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
        * Plus/minus, also on the number pad (next / previous page)
        * Control+F (find a point by its name, on any page)
        * Control+O (open Station Settings for the hovered square, or for the finding chosen in the list)
        * Control+V (place the locomotive selected in the main window on the hovered station)
        * Delete (take the train off the hovered station)
        * Control+Enter (save changes)
        * Double-click a finding (open Station Settings for its station)
        * Shift+click (pick several squares at once)
        * Escape (put down an armed tool; with nothing armed, close the editor)
* Track diagram (with autonomy loaded and stopped, pointer over a station)
    * Control+X (pick up the train standing there)
    * Control+V (put it, or the locomotive selected in the main window, down)
    * Delete (take the train off)

</details>

## Requirements

* Install Java 8 or later on your computer
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
* ```java -jar TrainControl.jar 0 debug simulate``` (Same as above, but allows the program to run without any central station; [how to test autonomy this way](Automation.md#trying-it-out-without-a-central-station))

TrainControl uses your computer's language automatically.  To run it in a different language, add the locale flags shown below.

* ```java -Duser.language=en -Duser.country=US -jar TrainControl.jar``` (Force English locale/language)
* ```java -Duser.language=de -Duser.country=DE -jar TrainControl.jar``` (Force German locale/language)
* ```java -Duser.language=da -Duser.country=DK -jar TrainControl.jar``` (Force Danish locale/language)
* ```java -Duser.language=fr -Duser.country=FR -jar TrainControl.jar``` (Force French locale/language)
* ```java -Duser.language=pl -Duser.country=PL -jar TrainControl.jar``` (Force Polish locale/language)
* ```java -Duser.language=it -Duser.country=IT -jar TrainControl.jar``` (Force Italian locale/language)
* ```java -Duser.language=es -Duser.country=ES -jar TrainControl.jar``` (Force Spanish locale/language)
* ```java -Duser.language=nl -Duser.country=NL -jar TrainControl.jar``` (Force Dutch locale/language)

**Updating TrainControl:**

When a new version is available, the Help menu shows a "*" and a "Download Update" item that can download and install it for you, or you can simply replace the JAR file by hand.  Making a backup first is recommended (File > "Back Up TrainControl Data...").

**Backing up and restoring your data:**

To make a backup, select "Back Up TrainControl Data..." from the File menu.  This saves a single zip file in the `tc_backup` folder with your locomotives and their icons, keyboard mappings, routes, and, for a layout stored on your computer, its track diagrams and autonomy setups.

If your track diagram is on the Central Station, TrainControl offers to download it first so that it is included.  On a CS3, the backup also includes the Central Station's own locomotive, route and accessory data.

To restore, select "Restore from Backup..." from the File menu and pick the zip file.  TrainControl shows what the backup contains and what it will replace, saves your current data first as a backup whose name ends in `before-restore`, puts the backup back, and closes; start it again to use the restored data.  To undo a restore, restore the `before-restore` file.

You can also restore by hand: close TrainControl, unzip the archive next to the JAR file, and start TrainControl again.  `LocDB.data` and `UIState.data` go next to the JAR; the layout folder can go anywhere, and you select it with "Open Layout...".

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

* v3.1.1
    - New Features
        - Autonomy
            - `Test a path` and `Why not Moving?` give one answer for both automatic and by-hand sends: a path only a hand send can take is purple, and the destinations are listed in a table.  The `Path Type` choice is gone.
            - The autonomy editor's list of things to look at now agrees with `Test a path` about which stations can be reached, and both are faster.
            - The `Station` preset lets trains run through, and a small white dot on the track diagram, in the autonomy editor and in `Station Settings` marks a station trains must stop at.  The `Terminus` and `Parking` presets stop trains there, and `Parking` is a terminus full autonomy never chooses.
            - `Station Settings` has a `Grayed-Out Icons` box under `Visible Elements` to leave out squares autonomy ignores, an `*` on `Advanced` when a setting under it has been changed, and arrows on the longest train box, which shows `Any` at 0.
            - Hovering `Some trains` in `Station Settings` explains each condition.
            - The autonomy editor's filter box sits below the list with a gray `Filter` hint inside it.
        - Locomotive Control
            - `Sync` is now in the `Locomotives` menu, and first asks about locomotives renamed at the Central Station.
        - Central Station Protocol
            - After a sync from a local copy, the log lists each accessory square whose protocol the Central Station disagrees with.
        - Interface
            - Ctrl+G starts autonomous operation, Ctrl+Q gracefully stops it, and Ctrl+H sends every locomotive home after asking first.  In the autonomy editor, Ctrl+V places the selected locomotive on a station and Delete takes its train off.  Ctrl+Enter saves in the editors, `Locomotive Settings` and the route editor, and is `Done Editing` in `Station Settings`.
            - On Java 8, the pointer over links and buttons is a smooth, bold hand.
            - To scroll the Log tab sideways, hold Shift and turn the mouse wheel.
    - Bug Fixes
        - Central Station Protocol
            - A train's stop is no longer lost when the connection to a CS3 stalls and resets.
            - A route that syncing replaces stays armed, and an armed S88 route no longer fires on a late sensor answer during a sync.
            - A sync downloads each Central Station file once, and a station picture that never arrives gives up instead of hanging.
        - Autonomy
            - Pressing a train's pause while the autonomy editor is open, then Cancel, keeps that pause, and Cancel after a Save goes back to what was saved.
            - A train whose journey fails partway is recorded where it stopped, and its destination is freed.
            - On a station that allows trains from every side, `Station Settings` no longer shows arrival arrows.
            - `Station Settings` steps aside while you pick a train's tail on the diagram.
        - Locomotive Control
            - `Cancel` in `Locomotive Settings` asks before throwing away what you typed, and the revert and crop marks change together with the picture.
        - Interface
            - The `Start Autonomous Operation` button ends at the track diagram's right edge.
            - On newer Java, a scaled Windows display is no longer scaled twice, the file chooser's `Look In` list works, and settings are kept when the folder path has a non-English letter.
            - A new route item starts at address 0, and a selected row's grayed cells stay readable.
        - Track Diagram
            - The station picture in `Station Settings` shows s88 squares as free.
* v3.1.0 [11/15/2026]
    - Central Station Protocol
        - New Features
            - TrainControl now connects to a CS3 over TCP and falls back to UDP, saying so in the log.  A CS2 still uses UDP.
            - If the connection to a CS3 drops, TrainControl switches to UDP right away, and a warning shows while the Central Station isn't answering.
            - Startup and sync with a CS3 now read the state of every sensor, so the track diagram shows them as they are.  S88 routes wait until every sensor has reported before they can fire.
            - `Download Central Station Layout Files...` asks before replacing a layout and no longer freezes the window.
            - When the Central Station cuts the track power after a short circuit, TrainControl shows the power as off and names the output that tripped.
            - A failed `Sync` says what went wrong in one sentence, with `Retry`, and a CS3 sync skips what it can't read instead of failing.
        - Bug Fixes
            - `Auto-Detect` at start-up shows which networks it is searching, so TrainControl no longer looks stuck.
            - A file that arrives cut off from the Central Station is asked for again, and names with umlauts or accents from older Central Stations show correctly.
            - A sync no longer changes the address of a moving locomotive, and `Check for Renamed Locomotives` handles a locomotive renamed at the Central Station.
    - New Features
        - Locomotive Control
            - `Locomotive Settings` is one window with tabs for `Basic Settings`, `Functions`, `Notes`, `Multi-Unit` and `Autonomy`, in place of the separate dialogs.  Open it with Ctrl+E, from the key menu, or by right-clicking a locomotive's name or function button.
            - The `Functions` tab lists every function in one table, and the `Autonomy` tab has the train length, `Reversible`, and the departure and arrival functions.
            - `Add Locomotive...` (Ctrl+D) opens the same window, and a warning shows when another locomotive uses the address.
            - The window opens grayed out while autonomy runs or the locomotive is moving, though you can still save presets.
            - A locomotive in a multi-unit shows the unit's name as a link on its `Multi-Unit` tab, which opens the unit's settings.
            - With the power off, hovering a locomotive's picture shows a wrench that opens its settings.
            - `Import from CSV...` reads a file saved by `Export to CSV`, adding new locomotives and merging the ones you have, after showing what will change.
            - `Apply Saved Speed Preset` is grayed out, with the reason, when no speed is saved, and `Save Current Speed as Preset` (Alt+U) also sets the speed autonomy uses.
        - Autonomy
            - `Station Settings` puts all of a station's settings in one window, opened from its right-click menu or with Ctrl+O, with the boxes `Trains can stop here` and `Trains can pass through`.  Its `Train Placement` tab holds the train standing there.
            - `Mass Edit...` under `Station Tools` changes several stations at once.
            - A station's right-click menu on the track diagram groups the train items: edit or place a locomotive, `Change Direction`, `Change Arrived From`, and `Is Active` to pause or resume it.  Every square's menu starts with its name and `Station Settings...`, and offers `Go to the Opened Editor` while an editor is open.
            - In the autonomy editor, you can place the selected locomotive from a station's menu, drag a train between stations, filter `Things to look at`, and use `Find a Point...` (Ctrl+F).
            - You can send a standing train while another one is still on its way.  If the CS3 connection drops, no new trips start until it is back.
            - `May depart` is grayed and shows the only way out at a dead end, the setup check warns about directions that leave trains no way out, and `Closed - No Trains` is gone: untick `Trains can stop here` or press `Off`.
        - Track Diagram
            - The track diagram editor has icons to add, duplicate, rename, delete and move pages, `Manage Pages` is now `Tools`, and page links keep working when pages are renamed or moved.
            - Ctrl+R rotates the squares you have picked, and diagrams draw faster and flash less.
        - Interface
            - `Layouts` > `Manage Pages...` is now `Edit/Add Pages...`, `Show Current Layout Folder` is under `Recent Layouts`, and the layout in use is marked `(in use)`.
            - `Hide Hover Tooltips` on the `Preferences` menu hides tooltips and shortcut keys in menus, though a grayed item still says why.
            - The accessory keyboard has 16 tabs of 128 addresses, each key with a red and a green half, and `Export Counts` saves how often each was used.
            - The log shows the time of each line, `File` > `Export Log...` saves it, and `File` > `Change Central Station IP Address...` sets the address for the next start.
            - Click an (i) to read its explanation at once, and menu items show their shortcut keys.
            - Grayed menu items say why when you hover over them.  While autonomy is running, some menu options are grayed out.
            - The Preferences menu was rearranged to flow more logically.  Your settings are kept.
            - The `Help` menu has the guides, `Check for Updates Now`, `View Releases` and `About TrainControl`, and `Routes` has `New Route...`.
            - An `All` button after the Layout tab's S, M and L buttons opens every page in its own window.  Where the menus are in the title bar, `Window Always on Top` is on the Keyboard tab.
        - Documentation
            - The Readme and the autonomy guide describe the new windows and shortcuts.
    - Bug Fixes
        - Autonomy
            - `Return Home` finds the same plan on any computer, and may take up to about 15 seconds on a crowded layout.
            - Cancel in the autonomy editor puts every train back where it stood, names the trains that will move, and no longer loses a paused train or mixes up a timetable entry.
            - While an editor is open, changes Cancel could not undo, such as `Sync` or switching layouts, are refused or grayed out.
            - A command window that stopped showing output no longer holds up sensor reports, which could make trains run past their stops.
            - `Path Integrity Validation` waits up to 30 seconds while the Central Station is busy, instead of stopping the train.
            - A train's destinations in the right-click menu are in the order you read them (ParkingTrack4 before ParkingTrack10).
        - Locomotive Control
            - Ctrl+B moves the copied locomotive to the active button, and saving a typed address changes the decoder type only if you changed it.
        - Track Diagram
            - S88 squares show their tooltip, and page windows redraw after a sync, follow a renamed page, and close when their layout or page is gone.
        - Interface
            - Questions and messages open over the window you were working in.
            - `Restore from Backup` and `Open Layout...` are safer: they refuse a folder that isn't a layout, and keep your current file when a backup holds an empty one.
            - If your data can't be saved when you close TrainControl, it says which file and why, and asks you to `Retry` or `Exit Without Saving`.
            - A backup names any file it couldn't read, and is written whole or not at all.
            - TrainControl no longer sometimes stops with an error at start-up.
    - Notes
        - The first time you open a layout saved by an earlier version, TrainControl updates its page links once and keeps a `.bak` copy of each page it changed.  A layout and a setup saved by 3.1.0 still open in 3.0.8.
        - A station set to `Closed - No Trains` in an earlier version stays closed, and `May depart` shows no choice for it until you pick one.
        - For developers: `MarklinControlStation.saveState(boolean)` and `ViewListener.saveState` now return the reason a save failed, or null, so Java code compiled against an older jar must be recompiled.
* v3.0.8 [11/04/2026]
    - New Features
        - Central Station
            - Syncing with a CS3 reads the position of every two-position switch and signal, so one thrown while TrainControl was closed shows correctly.
    - Bug Fixes
        - Autonomy
            - The autonomy editor can no longer be made so short that `Save Changes` and `Cancel` are cut off.
        - Track Diagram
            - The red arrows on a curved s88 sensor are drawn a little further out, so they stay visible.
            - A switch and a signal that share an address change together, and on a CS3 with an MM2 and a DCC accessory at the same address, each square controls the right one.
        - Central Station
            - Locomotive pictures stored outside the CS3's standard folders now show, and syncing with a CS3 is faster.
        - General
            - TrainControl starts a little faster.
    - Notes
        - Saving a track diagram page now records each accessory's protocol, MM2 included.
* v3.0.7 [11/02/2026]
    - New Features
        - Routes
            - The route editor is rebuilt so commands and conditions work alike: drag rows by their handle, use Alt+Up and Alt+Down, or right-click to insert, move, duplicate or delete.  `Undo` and `Redo` (Ctrl+Z, Ctrl+Y) work in both lists.
            - Conditions sit in outlined groups whose rule, `All of these must be true` or `At least one must be true`, you change by clicking it.  `Group Selected`, `Ungroup` and `Split Here` build the groups.
            - On the Routes tab, with the track power off, each route shows a wrench that opens it for editing, and `Execute Route` still runs it.
        - Autonomy
            - In the autonomy editor, `Test a path` and `Why not Moving?` outline what you can click in orange, and their answers stay on screen until you close them.
            - `Why not Moving?` names every station the train can go to, not just the first four.
    - Bug Fixes
        - Routes
            - A value typed into a cell in the route editor is saved by `Save`, and `Cancel` asks before throwing it away.
        - Autonomy
            - `Why not Moving?` colors a station purple only when the train could be sent there by hand, and no longer lists a reachable station as unreachable.
        - Track Diagram
            - `Save Current Track Diagram as a Picture` asks "What diagram size do you want to save?"
* v3.0.6 [10/30/2026]
    - New Features
        - Autonomy
            - In the autonomy editor, what you can click is marked in blue while `Test a path` or `Why not Moving?` is on.
            - `Why not Moving?` colors stations green where the train can go now, gray where it can't right now, and purple where autonomy will never choose it.
    - Bug Fixes
        - Autonomy
            - A message across the bottom of the autonomy editor's diagram no longer hides its last row.
        - Interface
            - `Recent Layouts` no longer shows long folder paths that make the menu very wide.
            - Right-clicking a locomotive's picture opens the icon file chooser only while the power is off.
* v3.0.5 [10/29/2026]
    - New Features
        - Interface
            - The Stats tab's `Usage Graph` is a new chart: show 7 days, 30 days, 90 days, a year or everything, page back and forth, and hover a bar to see its hours and most-run locomotives.
        - Track Diagram
            - `Add Blank Page` suggests a name, `New Page 1` or the next number not taken.
    - Bug Fixes
        - Autonomy
            - Text labels no longer flash in black when the editor switches to `Autonomy Setup` with `Text Labels` off.
        - Interface
            - Typing a bracket or other symbol in the Stats tab's filter no longer causes an error.
            - The usage graph no longer counts a multi-unit's running time twice.
            - `Quick Find Locomotive` no longer skips a locomotive's key or goes to an old one after you remap keys.
        - Routes
            - `Delete` in the route editor's right-click menu removes every selected condition, not only one.
        - Track Diagram
            - The editors' `Text Labels` box hides text only in the editors, never in the main window, pop-up windows or saved pictures.
* v3.0.4 [10/26/2026]
    - New Features
        - Interface
            - `File` > `Restore from Backup` restores a backup made with `Back Up TrainControl Data...`.  Your current data is saved first, as a backup ending in `before-restore`, and then TrainControl closes; start it again to use the restored data.
            - `Layouts` > `Recent Layouts` lists the last five layouts you opened.
            - Messages and tooltips are shorter in every language, and long lists in popups scroll instead of stretching the window.
        - Routes
            - Right-click a condition, or an `and` or `or` line, to insert, move, indent or delete it, and right-click below the list to add one.
        - Track Diagram
            - The layout editor has a `Manage Pages` menu to add, rename, duplicate, delete or combine pages.
            - `Save Current Track Diagram as a Picture...` offers Small, Medium and Large.
    - Bug Fixes
        - Updates
            - `Help` > `Download Update` works again; GitHub had started refusing it.
        - Autonomy
            - `Test a Path` starts a train facing the way it stands at the station.
    - Notes
        - On 3.0.3 or older, download 3.0.4 by hand from the releases page; `Download Update` works again from 3.0.4 on.
* v3.0.3 [10/25/2026]
    - New Features
        - Locomotive Control
            - The locomotive icon window has a `Clear Local Locomotive Icon` button.
        - Routes
            - Shift-click several conditions to indent or outdent them together, and moving a condition moves it past a whole group.
        - Autonomy
            - Notices float over the bottom of the autonomy editor's diagram, and an X hides each one.
            - While autonomy or a route runs, a refused edit says why, and so does a grayed-out item when you point at it.
            - `Name Everything...` and `Rename...` refuse a name another station or sensor already has.
        - Automation API
            - The API guide links six short example programs, one for each common job.
    - Bug Fixes
        - Autonomy
            - Opening another layout no longer keeps the previous layout's trains in memory.
            - Pasting a train onto a square that is not a station says trains can only be placed at stations.
            - A train placed with `Place` on a station that no train can leave is now saved.
        - Routes
            - The route editor shows switch, signal and sensor settings in your language.
        - Automation API
            - The full autonomy example in the API guide runs as written.
* v3.0.2 [10/18/2026]
    - New Features
        - Autonomy
            - Right-click `Return Home` to choose `Run Trains to Their Homes`, or `Place All Trains at Their Homes`, which puts each train on its home in the setup without moving it.
            - The autonomy editor has a `Text Labels` box, and its `Station Labels` list offers Station Names, Standing Locs, Home Locs and Hide.
            - The setup check warns when two sensors on one page share an address, or when track squares point opposite ways, and the One-Way tool sets a whole stretch.
            - `Create New Layout` starts from a small sample with two stations, ready to try.
        - Routes
            - A turning mark shows which routes are running, and a message that has to wait names the route.
            - A condition can ask whether an autonomy train is not at a sensor.
        - Track Diagram
            - Clearer tunnel, link and signal icons, and new track placed in the editor turns to join the track beside it.
    - Bug Fixes
        - Autonomy
            - Locomotives missing from your database are taken out of the setup when it loads, and a popup names them.
            - Direction arrows on switches and slips fully decide which way trains run, and older setups are corrected when they load.
            - Setup edits, Start, Execute Timetable, Return Home and sending a train wait while a route runs, not only while autonomy does.
            - `Return Home` names trains that are paused instead of saying every train is home.
            - A locomotive turned around during a route is turned around in the setup too.
            - The Track Diagram and the autonomy editor draw the same direction arrows.
* v3.0.1 [10/13/2026]
    - New Features
        - Updates
            - `Download Update` can install the new version: it backs up your data, keeps the old version beside it, and restarts TrainControl.
        - Locomotive Control
            - The active locomotive's key, page and decoder are shown above its name, and `Show Page Numbers` in the Preferences menu shows each page's number on its tab.
        - Autonomy
            - Bulk Tools can activate or deactivate all trains.
            - A station's name can appear on more than one page.
        - Documentation
            - The autonomy guide explains how to try a setup in simulation, without a Central Station.
    - Bug Fixes
        - Autonomy
            - A train leaving a station turns that station's exit guard signals green.
            - A train paused on the Auto tab stays paused when another train's settings change.
            - The autonomy editor's smallest size shows all its controls, up to 175% display scaling.
            - A train on a square with no way out faces along its track, and its tail is drawn over the switches behind it.
        - Central Station
            - If the Central Station's data cannot load at startup, TrainControl says why and offers to try again.
            - Starting in simulation no longer forgets your Central Station's address.
* v3.0.0 [10/12/2026]
    - New Features
        - Autonomy
            - Autonomy is set up directly on your track diagram, with no separate graph or JSON file.  It needs a track diagram stored on this computer, and offers to download one from your Central Station.
            - Setups are saved as named configurations you can copy, rename, export and import, including an older autonomy.json.
            - The setup is checked before it runs: click a finding to go to its square, or let the autonomy button fix the errors.  Anything that would change the setup is grayed out while trains are running.
            - Stations can have exit and entry guard signals, accept trains from one side only, be taken out of service, or allow only chosen locomotives.  You can pause a train from its station's right-click menu (`Is Active`).
            - Choose how trains pick their route: shortest track, fewest stations, or the station that has waited longest.
            - Bulk Tools fill in missing track and train lengths, reopen one-way track, and set or restore every train's home.
            - Set a train's length from its right-click menu, and `Show Trains` hides the editor's trains.  `Atomic Routes` stays on until every track and train has a length.
            - Right-click a train to see where it can be sent.  When a train is not moving, the locomotive list and the `Why not Moving?` tool say why.
            - A running train's route is drawn on the track: blue ahead, gray behind, the train in orange.  The editor can show only the directions trains may run, and the track diagram can draw them in green.
            - Names of stations autonomy never chooses can be hidden (`Preferences` > `Autonomy` > `Show Inactive Labels`).
            - Autonomy pauses when the track power goes off and resumes when it comes back.
            - The autonomy setup is translated into all eight languages.
        - Routes
            - A new route editor builds commands from lists and shows conditions as an indented list.
            - Each route has a play button, and `Find Route` accepts part of a name, and routes from the Central Station open read-only.
            - While autonomy runs, a route asks before changing a switch under a train or turning a signal green at an occupied platform.
            - Imported routes arrive with `Automatically Fire` off, and you are asked whether to turn it back on.
        - Track Diagram
            - Stations are marked with a circle, diamond or square, and each train with a small locomotive.
            - A Medium size, faster scrolling, and new tunnel and overpass icons.
            - Shift+click, or drag a box (Control+M), to select squares to move, copy, rotate or delete together; one undo takes it all back.
            - The editor can add or remove a row and a column, the `+` and `-` keys step through pages, switches without a decoder can be drawn as static switches, and `Layouts` > `Manage Pages` > `Combine Linked Pages...` makes one page from a page and the pages it links to.
            - A page can be saved as a picture.
        - Central Station
            - Backups are a single archive, and can include the Central Station's track diagram.
            - Syncing with the Central Station no longer freezes the window.
        - Interface
            - New flat sidebar icons, and you can crop and pan a locomotive's icon when you pick it.
            - Up to 50 locomotive keyboard pages.
    - Bug Fixes
        - Autonomy
            - Track shorter than a train is no longer released while the train still stands on it.
            - Autonomy no longer gets stuck when every locomotive is skipped at startup.
            - `Return Home` works when home stations share a sensor, and no longer plans impossible moves.
        - Track Diagram
            - One unreadable page no longer stops a layout from loading.
        - Central Station
            - A short message from another device on the CAN bus is no longer taken for an emergency stop.
            - An interrupted layout download no longer leaves a half-written file.
        - Interface
            - Confirmations that delete or overwrite no longer have Yes selected.
    - Notes
        - **Before going back to v2.8.2 or earlier, keep a copy of your settings:** those versions read only the first 10 keyboard pages and discard the rest when they save.
        - A station out of service can no longer be sent to or driven through, even by hand.  To keep a parking track reachable by hand, untick its `Full autonomy` choice in `Station Settings` instead.
        - A route with an emergency stop can no longer contain other commands; older ones are split in two automatically.
        - The "paste entire row" and "paste entire column" options were removed.
* v2.8.3 [11/16/2026]
    - Bug Fixes
        - Central Station
            - Syncing with a CS3 and starting TrainControl now read the real state of its feedback sensors, and a route triggered by an S88 sensor does not fire until the CS3 has confirmed those states.  Previously a route could fire on a sensor state that was out of date.
            - A short circuit at the Central Station now shows the track power as off in TrainControl, with one warning each time the power is cut.
            - When a CS3 has an MM2 accessory and a DCC accessory on the same address, each square on the track diagram now switches the right one, and downloading the layout keeps each square's protocol.
            - Station files that arrive cut off are now fetched again instead of being used, and names with accented letters in files from the Central Station are read correctly.
            - A switch and a signal on the same address now both follow the Central Station on the track diagram.  Previously the signal could stay red there while a route had turned it green.
            - A short message from the Central Station is no longer taken for an emergency stop, and Stop now works while TrainControl is not connected.
            - Locomotive pictures stored outside the CS3's standard folders now show up.
            - A sync no longer gives a locomotive a new address while it is moving.
            - Starting a simulation now keeps the Central Station's address.
            - The page list of a downloaded layout is saved safely, so an interrupted download cannot leave it cut off.
            - A sensor that an S88 route uses is no longer lost when the layout is read, even if no track diagram page shows it.
            - Sensor changes are no longer held up when the command window stops accepting output, and short bursts of messages from the Central Station are no longer dropped.
        - Autonomy
            - `Path Integrity Validation` now waits while a busy CS3 is still switching other accessories, instead of reporting a path as broken.
            - Autonomy, timetables and `Return Home` now wait while the track power is off, and no train leaves while it is off.  If the power is switched off and on again before a train leaves, the train is held back.
        - General
            - `Download Update` works again.
* v2.8.x [8/2026 - 11/2026]
    - Added French, Italian, Spanish, Dutch and Polish translations, Path Integrity Validation (a train runs only once the Central Station confirms its switches and signals), and a "return home" feature.  Later 2.8 versions fixed many autonomy, route and Central Station problems, and 2.8.2 no longer loses page names when you go back to it after using 3.0.

<details>
<summary>Show older versions</summary>

* v2.7.x [5/2026 - 7/2026]
    - Added support for CS3 firmware v2.6.0 (March 2026) alongside older versions, drag and drop of locomotive buttons across pages while the power is off, and a right-click menu on function icons for saving and recalling presets.  Autonomy can be started, stopped and rearranged from the track diagram, update downloads show their progress in the menu bar, and backups are saved to the tc_backup folder.

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

</details>
