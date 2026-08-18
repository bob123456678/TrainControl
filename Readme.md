# TrainControl for Märklin Central Station 2 & 3

[![Latest release](https://img.shields.io/github/v/release/bob123456678/TrainControl?label=latest%20release&color=success)](https://github.com/bob123456678/TrainControl/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/license-GPL%20v3-blue)](https://www.gnu.org/licenses/gpl-3.0)
![Java 8](https://img.shields.io/badge/Java-8-orange)

**Free, open-source software for controlling and automating your Märklin (Marklin), Trix, or DCC model railroad from your computer.**  Runs on Windows, macOS, and Linux.

Available in 🇬🇧 English · 🇩🇪 Deutsch · 🇩🇰 Dansk · 🇵🇱 Polski · 🇫🇷 Français · 🇮🇹 Italiano · 🇪🇸 Español · 🇳🇱 Nederlands

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-Support%20TrainControl-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://www.buymeacoffee.com/traincontrol)

![TrainControl main window, showing locomotive control with keyboard mappings, locomotive thumbnails, and function buttons](assets/main23_2.png?raw=true)

TrainControl connects to a Central Station 2, 3, or 3 Plus over your network.  It is designed for model railways with many locomotives, where the standard Marklin UI makes common tasks — quickly switching between locomotives, or triggering functions — overly tedious.  It is a complete replacement for the CS2/CS3 when operating your layout, with the Central Station serving solely as the track interface and MFX locomotive database.  If your existing controller is taking the fun out of running your trains, consider trying TrainControl!

**Why TrainControl?**

* Control any locomotive instantly from your keyboard — no menu diving
* Interactive track diagrams, fully editable in the app, across unlimited windows
* Fully autonomous train operation using only S88 sensors
* Multi-units, function presets, conditional routes, and usage statistics
* Locomotive and layout data downloaded automatically from your Central Station
* Free, open source, and actively developed

**Getting started**

1. Connect your Central Station 2/3 to your network and enable CAN broadcasting in its settings ([details](#requirements))
2. [Download the latest `TrainControl.jar`](https://github.com/bob123456678/TrainControl/releases/latest)
3. Run it, and enter your Central Station's IP address when prompted ([details](#download-and-run-traincontrol))

Under the hood, this program implements the Marklin CAN protocol and can therefore
also be used to programmatically control the entire layout ([see API](Automation.md)).  Layout and locomotive information is automatically
downloaded from the CS2/CS3 (with minor limitations on the CS3). Track diagrams can also be designed entirely in TrainControl.

For easy scripting or interactive control, you can write [Python (Jython) scripts to call the TrainControl API](src/org/traincontrol/examples/traincontrol_python_example.py).

TrainControl also provides a UI for creating a graph model of your layout, 
which when paired with S88 sensors, enables tracking train locations for *fully autonomous* operation at the push of a single button,
as well as semi-autonomous point-to-point operation between stations. You can of course also set up traditional/conditional routes to 
automate switch and signal commands while operating trains manually.

Translations are now available:
* Jetzt auf Deutsch verfügbar
* Nu tilgængelig på dansk
* Teraz również po polsku
* Désormais disponible en français
* Ora disponibile in italiano
* Ahora disponible en español
* Nu beschikbaar in het Nederlands

TrainControl follows your computer's language automatically, so there is nothing to configure.
If you would rather run it in a different language, you can [force one from the command line](#download-and-run-traincontrol).

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
    - The layout will be saved to a folder of your choosing, and TrainControl will switch to it as the local data source.  It will now be shown in the Layout tab and can be edited if desired.
    - If you already have layout files on your computer, use "Open Layout" in the Layouts menu to select the folder containing them.
- Otherwise, to create a new layout:
    - Start TrainControl, then from the Layout menu, click on "Create New Layout"
    - If no Central Station layout is detected and no static layout is manually selected, TrainControl will automatically create an editable demo layout at startup.

Complete editing support is accessible via the "Edit" button within the Layout tab. Pages can be managed from the Layouts menu item.  This will let you fully customize your layout.

If you change the local files, clicking on "Sync Database w/ Central Station" from the Locomotives menu will update the layouts.  This effectively lets you customize the layout even without a CS2/CS3.  Some users might find this easier than inputting data into the Central Station UI.

Some sample files are included in the `cs2_sample_layout` folder.

---

</details>

**Routes**

Conditional routes can be defined for semi-automatic layout operation, such as setting a switch to guide an incoming train to an unoccupied station track, or triggering an emergency stop.  Manual routes can also be defined and activated directly or via the layout tab.

In addition to all Central Station functionality, complex logical expressions are supported.

![Route editing wizard in TrainControl, showing accessory commands and conditional S88 trigger logic](assets/ui_route2.png?raw=true)

**Keyboard**

Useful for testing, individual accessories can be directly controlled via their digital address.  The cumulative number of actuations of each accessory is shown as a tooltip.

![Accessory keyboard in TrainControl, used to control switches and signals directly by digital address](assets/ui_keyboard.png?raw=true)

**Full Autonomy**

Defined via a special [JSON configuration file](Automation.md) that can be built using the UI, represent your layout as a graph and enable complete automation of trains using just S88 sensors and an initial list of locomotive locations.  TrainControl will automatically keep track of where each train is located at any given time.  You can pick destinations for specific trains, or let the system continuously execute random routes.  All state is auto-saved on exit.

![Autonomy control panel in TrainControl, used to start fully autonomous train operation](assets/ui_autonomy.png?raw=true)

The graph UI will show you which routes are active, which edges are locked, and where different trains are stationed.  This can also help you debug your graph as you build it.  While trains are not running, you can right-click any station to reassign a train and view possible routes.

<img src="assets/graphview.png?raw=true" alt="Autonomy graph view showing stations, locked edges, active routes, and the current location of each train" width="500">

In addition to the continuous automated operation and point-to-point commands, you can also specify timetables and run your trains according to a predefined list of paths, subject to the constraints and protections of the graph model.

<img src="assets/timetable.png?raw=true" alt="Timetable editor in TrainControl, running trains through a predefined sequence of paths" width="500">

Autonomy / point-to-point operation can also be controlled directly from track diagrams through specially named labels.

<img src="assets/easyauto.png?raw=true" alt="Starting point-to-point train operation directly from a track diagram using autonomy station labels" width="500">

**Statistics**

Monitor the usage of different locomotives.

![Locomotive usage statistics in TrainControl, showing runtime per locomotive over the past 30 days](assets/stats23.png?raw=true)

## Features

* Easily control locomotives (MM2, MFX, DCC), multi-units, signals/switches (MM2, DCC), and routes
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
    * Full UI for editing track diagrams
* Basic automation
    * Set up automatic and conditional routes triggered by S88 feedback modules
    * Automate bulk tasks such as turning off all functions
    * Set function and speed presets for locomotives
* Advanced automation
    * [Graph model](Automation.md) w/ JSON configuration for location tracking and fully autonomous train operation
    * Semi-autonomously operate trains simply by clicking the destination station (when graph model is enabled)
    * Full UI for editing autonomy graph models
    * View station information and control trains via track diagrams
    * Customize autonomous operation by setting station priority, maximum train lengths, edge lengths, speed multipliers, and maximum train idle time
    * Record and play back timetables
* Programmatic layout control via Java API (uses CAN protocol - [see documentation](Automation.md)) 
* Monitor locomotive usage stats

All of it is free.  If TrainControl has earned a place in your train room, you can [buy me a coffee](https://www.buymeacoffee.com/traincontrol) to support its continued development.

## Keyboard Commands / Key Mappings

TrainControl's key mappings are designed to allow you to send any command nearly instantly

* Primary controls
    * A-Z letter keys (select a locomotive)
    * Up/down arrow (speed up/slow down) (hold Alt to double the increment or Control to reduce)
    * Left/right arrow (change direction)
    * Control+Left/right arrow (set direction as reverse / set direction as forward)
    * Escape (power off/emergency stop)
    * Alt+G (power on)
    * 1 through 0 (set locomotive speed, 1 is stopped and 0 is max)
    * Numpad 0/backquote/Alt+0 (toggle lights/F0)
    * F1-F24 (toggle functions F1-F24)
    * Numpad 1-9, Alt+1-9 (toggle functions F1-F9)
    * Control+0-9 (toggle functions F10-F19, also works with Numpad)
    * Control+Alt+0-9 (toggle functions F20-F29, also works with Numpad)
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
    * Delete (clear mapping of currently active button)
    * Control+X (cut locomotive and clear mapping of currently active button)
    * Control+V (paste copied locomotive at currently active button)
    * Control+S (swap copied locomotive with currently active button)
    * Control+A (assign a new locomotive to the currently active button)
    * Control+D (add a new locomotive to the database)
    * Control+N (edit locomotive notes)
    * Control+R (edit locomotive name or address)
    * Control+L (edit multi-unit)
    * Control+Delete (permanently delete locomotive from database)
* Autonomy Graph UI
    * Control+V (assign active locomotive, or clipboard locomotive if non-empty, to currently hovered node)
    * Delete/Backspace (remove locomotive from currently hovered node, clear clipboard if non-empty)
    * Control+X (remove locomotive from currently hovered node and place it in the clipboard)
    * Control+E (exclude active locomotive from currently hovered node)
    * Control+U (unexclude active locomotive from currently hovered node)
    * Control+S (change S88 of currently hovered node)
    * Control+H (set the home locomotive of the currently hovered node)
* UI shortcuts
    * Control+M (show menu bar)
    * Backspace/Alt+backspace, CapsLock/Alt+CapsLock (cycle through tabs)
    * Plus/minus, \[/\], '/( (cycle through keyboards and layout pages, Control+plus/minus jumps 4 keyboards)
    * Slash/question mark, < (cycle through function tabs on the locomotive panel)
* Layout editor
    * Control+Z (undo)
    * Control+C (copy hovered tile)
    * Control+X (cut hovered tile)
    * Control+V (paste tile)
    * Shift+R (paste row)
    * Shift+C (paste column)
    * Control+R (rotate hovered tile)
    * Control+T (edit text of hovered tile)
    * Control+A (edit address of hovered tile)
    * Control+L (show text labels)
    * Control+D (show address labels)
    * Control+I (increase diagram by 1 row and 1 column)
    * Control+S (place autonomy station label)
    * Delete (delete hovered tile)
    * Escape (clear clipboard & reset tool)
    * Left mouse click (cut hovered tile / paste new tile)
    * Middle-mouse click (rotate hovered tile)
    * Right mouse click (show all options)

![Diagram of TrainControl's keyboard mappings for locomotive and function control](assets/keyboard.png?raw=true)

## Requirements

* Install Java 8 on your computer
* Requires a Marklin Central Station 2 or Central Station 3 connected to your network and layout
* The computer running TrainControl must be on the same network as your CS2/CS3 (Wi-Fi or ethernet)
* Ensure that your firewall allows TrainControl/Java to access the local network
* Important: CS2/CS3 CAN bus and broadcasting needs to be enabled in the settings (TrainControl will show a warning popup after 15 seconds if this is not enabled)
* For fully autonomous operation, your network connection must be reliable (Ethernet or 5Ghz Wi-Fi recommended)

<details>
<summary>How to enable CAN broadcasting</summary>

**Central Station 3:**

From the upper-left corner of the CS3 main screen, click on the **System** icon.  Then click on **IP** toward the bottom the page that is shown.

![Marklin Central Station 3 System page, where network settings are opened](assets/network1.png?raw=true)

Typically, you would either manually input a specific IP on this page, or have your router assign a static IP based on the CS3's MAC address.

In this example, the network mode is *auto (DHCP)* and the CS3 has been automatically assigned an *IP Address* of 192.168.50.25 on the local network. The *IP Gateway* and *DNS Server* is 192.168.50.1 with an *IP Network Template* of 255.255.255.0.

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

To make a backup, select "Backup TrainControl Data" from the File menu.

All of your data is stored in `LocDB.data`, `UIState.data`, and `autonomy.json`, in the same directory as the JAR file.  To restore a backup, close TrainControl, replace these files with the copies from your backup, then start TrainControl again.

## Support TrainControl

**TrainControl is free — no ads, no paywalls, and no locked features.**  It is built and maintained by one person, in his spare time.  If it has made your layout more fun to run, a coffee helps keep new features coming.

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-Support%20TrainControl-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://www.buymeacoffee.com/traincontrol)

## Building from Source

Requires JDK 1.8+ and the following libraries:

* org.json (json-20251224.jar) (from v1.6.0)
* org.graphstream (gs-core-2.0.jar, gs-algo-2.0.jar, gs-ui-swing-2.0.jar) (from v1.8.0)
* com.formdev.flatlaf.FlatLightLaf (flatlaf-3.5.4.jar) (from v2.3.0)
* jcommander-1.69.jar, testng-6.14.3.jar (for unit tests only)

```ant -f /path/to/project/ -Dnb.internal.action.name=rebuild clean jar```

## License & Contact

TrainControl was created and is maintained by Adam Oest.

Feedback and suggestions are welcome at [traincontrol@adamoest.com](traincontrol@adamoest.com).

This is free software released under the GNU General Public License v3.

No copyright claim is made to any Central Station icons rendered during the use of this program.

Tab icons provided by Freepik.

## Changelog

* v2.8.1 [8/17/2026]
    - Autonomy Bug Fixes
        - A locomotive placed on the graph without a speed being chosen is no longer dispatched at speed zero.  It used to wait forever for a sensor it could never reach, which also blocked starting autonomy until the graph was reloaded
        - Double-clicking the empty space below a short list of available paths no longer starts the last path in the list
        - Fixed bug where a train could be sent down a different path than the one double-clicked, if another locomotive arrived or departed at that moment
        - An error while redrawing the graph can no longer leave a locomotive stuck part way through a route, needing the graph reloaded
        - Feedback sensors are no longer ignored for a while after the computer clock is corrected backwards, such as by an automatic time sync
    - Locomotive Bug Fixes
        - Fixed bug where pressing Go on the Central Station while trains were already running discarded their accumulated running time from the statistics.  This also happened when clicking a track diagram accessory, which turns track power on first
        - The duplicate address check no longer reports an address as free when one locomotive is already using it.  Previously only addresses already shared by two or more locomotives counted
    - Route Bug Fixes
        - Fixed bug where renaming a locomotive stopped every route whose condition named that locomotive from firing again.  The route stayed switched on and looked normal in the list, but its condition could no longer be met, so it quietly never ran
        - Fixed bug where capturing commands into a route that drives more than one locomotive kept only the last one.  Capturing a turnout would make an earlier locomotive’s speed, direction, or function disappear from the middle of the command list, and saving kept the shortened route
        - Fixed bug where importing a routes file containing two routes with the same name left the rejected one running invisibly in the background, still triggering from its s88 and still throwing switches, until TrainControl was restarted
        - Cancelling the bulk enable or disable prompt now cancels, instead of doing nothing at all and leaving the route list unrefreshed
        - A locomotive whose name contains a comma or a bracket can no longer be used in a route condition, and a locomotive can no longer be renamed to such a name.  Routes store locomotives by name in a text format that uses both characters, so such a name silently turned an existing route command into one for a different locomotive, or stopped the route saving at all
    - Track Diagram Bug Fixes
        - Fixed bug where renaming a track diagram page to the same name with different capitalization, such as "Main" to "MAIN", deleted the page instead of renaming it
        - Clicking a tile in the track diagram editor no longer counts as an edit, so the editor stops asking whether to save changes that were never made
        - Fixed an error when clicking a tile in the editor’s component palette rather than dragging it onto the diagram
    - Central Station Sync Bug Fixes
        - Fixed bug where locomotive speeds and functions changed at the Central Station stopped being shown in TrainControl if the initial sync had failed while the Central Station was still reachable over the network
        - The locomotive database is no longer occasionally left unsaved when a backup or an automatic sync runs at the same moment as an edit

* v2.8.0 [8/2/2026]
    - Added French, Italian, Spanish, Dutch, and Polish translations
    - Locomotive Control Page
        - Removed copy-to-next/-previous page option from button right-click menus (now redundant with 2.7.3's drag and drop)
    - Autonomy
        - Added a "return home" feature that will return all locomotives back to where they started, if routing is possible.
        - You can now pick which locomotive belongs at each station, so "return home" brings each one back to the station you chose instead of the one it happened to start on
        - A locomotive standing at the station you assigned it to now shows its location highlighted in teal; * marks one standing at its timetable starting point
        - Stations that have a home locomotive are outlined in the graph: solid when that locomotive is standing there, dotted when it is elsewhere.  Can be switched off under Display Options
        - Double-clicking a locomotive in a station's excluded-locomotives window now moves it to the other side
        - New Display Options setting hides the connections leading into reversing points, which tidies up a busy graph.  Unlike hiding the reversing points themselves, the points stay on the graph and can still be clicked
        - Reversing stations are no longer used when autonomy is running on its own - neither as a destination nor as somewhere to drive through on the way elsewhere.  They are meant for parking and shunting, so trains were being parked there at random, and were stopping and changing direction inside the parking area while on their way somewhere else.  You can still send a train to one yourself from the route menu, and “return home” can still park trains there.  Reversing points that are not stations, such as reversing loops, are unaffected
        - The route list now marks a station autonomy will never send the locomotive to with a dash, whether because the station excludes that locomotive or because it is a reversing station
        - A route you pick yourself can no longer run a train through a point you switched off.  Switching a point off now always keeps trains from crossing it; you can still send a train to a switched-off station, and still drive one away from it, so a deactivated parking track stays reachable by hand
    - Central Station Sync Bug Fixes
        - A locomotive whose name contains an equals sign is no longer dropped from its multi-unit when importing from the Central Station
        - Downloading the track diagram from the Central Station no longer fails part way through when a page name contains a slash, colon, or similar character.  Such pages are saved under a corrected filename, and are found again when the layout is loaded
    - Autonomy Bug Fixes
        - A path is no longer used if one of its switches or signals is missing from the database.  Previously the locomotive would depart anyway, running over an accessory that was never commanded
        - Fixed bug where a crossing could stay marked as occupied after a train finished its route, blocking every later path through it.  Only affected layouts using lock edges with atomic routes turned off
        - Path integrity validation now waits for the Central Station to confirm every switch and signal on the path.  An accessory that was already believed to be in the commanded position used to be accepted without any confirmation at all
        - Reloading or re-validating the graph while locomotives are still running now warns first, and stops them before the new graph takes over.  Previously the layout was replaced underneath them and any train already under way kept going, untracked
        - A locomotive part way through a route when the graph is reloaded now stops at its next point.  Previously it kept running with nothing left to stop it, and the new graph had no record of it
        - Fixed bug where renaming a locomotive stopped its station exclusions from applying, so it could be sent to a station it was set never to visit
        - Changing a locomotive's address, or having the Central Station report a new one, no longer stops its station exclusions from applying
        - Deleting a locomotive now also removes it from any station exclusion lists it was on
    - Timetable Bug Fixes
        - Fixed bug where a recorded timetable played back with its pauses shifted by one route, so the timing did not match what was recorded.  Delays typed in by hand were always correct
        - Fixed bug where stopping a timetable and resuming it could forget that some routes had already finished
    - Route Bug Fixes
        - Fixed bug where a route condition would fail to parse if a locomotive's name contained the word AND or OR, such as NORD or MOTOR
        - Fixed bug where a DCC switch or signal used as a route condition was shown with the wrong protocol
        - Fixed bug where a route could stop responding if one of its commands failed
        - Fixed bug where a conditional route would stop firing for the rest of the session if one of its conditions referred to a locomotive that was not placed on the autonomy graph.  The route still showed as enabled
        - Fixed bug where a route imported from the Central Station 3 would be silently skipped if it set a locomotive's speed or direction before a switch or signal with a delay
        - Fixed bug where an automatic route restored from a layout file that did not record its trigger type would wait for the opposite s88 sensor change, firing at the wrong moment
        - A mistyped or incomplete route command now explains which line could not be read, instead of showing a technical error
        - Fixed bug where a route containing a feedback entry lost the command directly after it when the route was opened in the editor and saved again
        - Fixed bug where 3-way switches created via the route editing wizard, or in routes imported from a Central Station 3, would sometimes fail to switch left
        - Fixed bug where adding a 3-way switch as a route condition made the condition impossible to save
        - Fixed bug where capturing a 3-way switch by clicking it on the track diagram could record its two commands in the wrong order
        - Pauses in routes imported from a Central Station 2 no longer lose their fraction of a second, and pauses shorter than one second are no longer dropped
        - Fixed bug where a pause on a route step was applied to an earlier step instead, if both steps used the same switch or signal
    - Multi-unit Bug Fixes
        - Fixed bug where a locomotive linked to run faster than the one leading it would stop keeping pace above a certain speed, leaving the two engines of one consist pulling against each other
        - Fixed bug where deleting a locomotive that was linked to another one left it still being driven by the lead locomotive until TrainControl was restarted
        - Fixed bug where renaming a locomotive that was part of a multi-unit stopped TrainControl from recognizing it as linked.  It could then be set up as a second multi-unit of its own, and deleting it left it being driven by the lead locomotive
        - Fixed bug where checking for locomotives renamed in the Central Station could delete one of them, if two locomotives in TrainControl shared an address.  Such addresses are now reported and left alone, since there is no way to tell which locomotive the Central Station means
    - Track Diagram Bug Fixes
        - Fixed bug where adding columns to a track diagram could fail on layouts taller than they are wide
        - Track diagram tiles no longer occasionally stop refreshing
        - Fixed bug where track diagram pages whose names contain accented characters could not be loaded from a local layout folder, and the folder setting was silently cleared as a result
        - Editing a track diagram, or saving a route that appears on one, no longer freezes TrainControl for several seconds.  Cycling between pages while editing is faster for the same reason: these actions re-read the track diagrams only, instead of reloading the entire Central Station database
        - Clicking a 3-way switch on a track diagram no longer briefly freezes TrainControl, and neither does clicking any switch or signal while the track power is off
    - Accessory Bug Fixes
        - A switch and a signal at the same address are the same device, so a route or autonomy command may now refer to either.  Previously "Signal 5" would not be recognized if the address was set up as "Switch 5", and the accessory was silently never switched
        - Fixed bug where changing a locomotive to a decoder type with fewer functions could leave its arrival or departure function pointing past the end, which then made the locomotive impossible to edit or place on the autonomy graph
        - Fixed bug where clearing a locomotive’s custom icon while not connected to the Central Station left it with no image for the rest of the session
        - A stray space in a hand-typed sensor line no longer silently flips the state it waits for, and a mistyped direction is now reported as an error instead of quietly running the locomotive backward
        - Fixed bug where opening and saving a route with certain combinations of AND and OR conditions could silently change when the route fires.  Only affected condition logic written directly into the configuration file
    - Central Station Sync Bug Fixes
        - Fixed bug where entering the Central Station's network name instead of its IP address would report it as unreachable, even though it had just responded
        - A locomotive address change picked up from the Central Station is now postponed while trains are running, instead of being applied underneath them.  A message in the log says when this happens
    - General Bug Fixes
        - Update notices now work even if a release name contains extra text after the version number
        - Your locomotive database, window layout, and autonomy graph are now saved safely when TrainControl closes.  Previously, if the computer shut down or lost power at the moment of saving, the file could be left unreadable and its contents lost - the locomotives themselves would come back from the Central Station on the next sync, but their function assignments, notes, and statistics would not
        - The debug log no longer slows TrainControl down during a long session.  It used to grow without limit, making the whole application gradually less responsive

* v2.7.4 [7/25/2026]   
    - Autonomy
        - Added Path Integrity Validation features. If a switch/signal configuration cannot be confirmed by the Central Station, the locomotive will not run.  Configurable via the menu bar preferences.  A warning popup will be shown if configuration fails successively.
    - UI
        - Backups are now saved to the tc_backup folder in the current directory
    - Bug fixes
        - Fixed bug where only one locomotive could be triggered from the track diagram in semi-autonomous mode
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
        - For added clarity, all controls in the route editor for routes imported from the central station will now be greyed out
        - Fixed bug where autonomy locomotives could be edited via the track diagram while autonomy was running
        - Fixed bug where track diagrams could be edited while autonomy was running
        - Fixed bug where CS2/3 auto-detection was not working
    - Code
        - Refactored code to make TrainControl classes more generic, suitable for future expansion beyond Marklin's CS3
        - Updated JSON library to json-20251224.jar

* v2.6.5 [3/29/2026] Note: this and prior versions only support CS3 running firmware v2.5.x or older.
    - Added shortcut to edit autonomy locomotive properties to the right-click menu on track diagram stations
    - Fixed bug where the right-click menu link to more autonomy destinations in track diagram stations ("...") would not activate the correct UI tab

* v2.6.4 [3/23/2026]
    - In the locomotive similarity search, added option to include only autonomy locomotives

* v2.6.3 [2/22/2026]
    - Fixed bug where locomotive icons might be incorrectly repainted when the Central Station latency is high
    - Added a "Clear Button" option to the right-click menu (keyboard shortcut: Delete).  This allows you to clear a locomotive button without putting the locomotive on the clipboard as with Control+X.

* v2.6.2 [12/16/2025]
    - Improved the quality of large track diagram icons
    - Fixed bug where locomotive functions selected in the route editor UI were inconsistent

* v2.6.1 [11/29/2025]
    - Autonomy Graph
        - Moved all display options from the "Autonomy Settings" tab in the main UI to the right-click menu in the graph UI
    - Autonomy
        - Routes can now be enabled together with autonomy configurations via the JSON keys `activateRoutes` and `activateRouteIDs`, and corresponding UI options
        - Moved button to reopen graph UI to the Locomotive Control tab
        - The reopen graph UI button will now reopen minimized graph windows
        - Rearranged the order of autonomy UI tabs
    - Improved translations

* v2.6.0 [11/24/2025]
    - Added internationalizaiton support
        - Available languages: English, Danish, German (contributions welcome - see [resources](https://github.com/bob123456678/TrainControl/tree/master/src/org/traincontrol/resources))
        - Language is automatically set based on system settings, or can be manually overriden via command line
    - Autonomy
        - Toggling signals and switches along an active route now requires confirmation
        - Added button to reopen graph UI to the Autonomy Settings tab
        - Added `Layout.CB_ROUTE_PROG` callback that fires at intermediate stations between the start and end
    - Autonomy Graph
        - Added right-click menu option to test connections between stations
        - Fixed bug where newly added or removed locomotives might not always be shown on the graph labels
    - Track Diagram Editor
        - Added drag-and-drop functionality for moving icons
    - UI
        - Simplified Layout pop-up controls ("all" now in menu bar)
        - Control+plus/minus now cycles keyboards faster
        - Minor tweaks and improvements
    - Updated JSON library to `json-20250517.jar`
    - Backward-incompatible changes:
        - Save files from v2.4.2 and older are no longer compatible. Open and save data from v2.4.3+ prior to upgrading.

<details>
<summary>View prior versions</summary>

* v2.5.16 [9/20/2025]
    - Autonomy graph
        - Fixed bug where adding a new locomotive using the "Add to Node" shortcut on the graph UI would not update the list of running locomotives
    - Keyboard mapping pages can no longer have duplicate names

* v2.5.15 [9/16/2025]
    - Locomotive Database
        - Added a menu option to export key locomotive data to a CSV file
        - In the locomotive notes editor, added fields for years of operation and railway name
        - In the locomotive button and database right-click menus, added a utility button to find locomotives with a similar year or railway name 
        - Similar locomotives can optionally be mapped to a keyboard page

* v2.5.14 [8/29/2025]
    - Locomotive Database
        - Added a right-click menu to modify locomotives from this page
        - When the database is opened from the menu bar, clicking will by default not make an assignment
    - UI Enhancements
        - An error will now be shown if attempting to create a multi-unit with only one locomotive in the database
        - Highlighted "delete" menu options in red
        - Escape will now close the edge editor window
        - Removed redundant OK button from the locomotive function editor
    - Fixed latency issue when opening the locomotive function editor for the first time
    - Fixed issue where the multi-unit editor might appear below the main window

* v2.5.13 [8/25/2025]
    - Locomotives
        - Added a UI menu option to check if any locomotives have been renamed in the Central Station, and update names if so
        - The duplicate address checker under Add Locomotive will now list MFX addresses
    - Routes
        - Route commands can now specify locomotive direction
        - When locomotives are renamed, they will now also be updated across all routes
        - Added support for parsing Locomotive direction and Locomotive speed commands in routes read from the CS3 (note: function commands are deliberately not read because of a current limitation in CS3 data)
    - Fixed UI bug where renamed locomotives might not immediately refresh
    - Fixed UI bug where edits to a route would not be triggered from the track diagram unless TrainControl was restarted

* v2.5.12 [8/17/2025]
    - Added UI tabs that can be used to more quickly view / cycle through the locomotive mappings
    - Added a preference to the menu bar to enable the new tabbed view
    - Improved scrolling on the semi-autonomous control page
    - Simplfied / modernized the design of various UI pages

* v2.5.11 [7/26/2025]
    - Route conditional logic now has an explicit AND operator for improved readability
    - Starting semi-autonomous operation from the track diagram will now also first check that the power is on

* v2.5.10 [6/30/2025]
    - Fixed bug where CS2 locomotives with no functions would fail to be imported

* v2.5.9 [6/18/2025]
    - Routes
        - Route conditions can now specify the location of a locomotive in autonomous operation (e.g., to trigger functions)
    - Track Diagrams
        - In autonomy mode, added a right-click option to remove the current locomotive from the selected station
    - Improved the performance (reduced latency) of S88 events and othre autonomy events
    - Fixed bug where commands that referenced other routes (from 2.5.3) would fail for routes containing the word "Route"
    - Fixed bug where locomotives being run in autonomy mode might start yielding to inactive locomotives that were paused by the user

* v2.5.8 [5/11/2025]
    - Graph UI
        - When editing edges, added a button to highlight all linked tiles in the track diagram
        - Improved the appearance of the graph edge editor

* v2.5.7 [5/9/2025]
    - Track Diagram Editor
        - Improved the organization of right-click menu options
        - The option to rotate text labels and symmetrical components will no longer be shown
        - Added Shift Left and Shift Up options to move the entire track diagram
        - While the right-click menu is open, the selected tile will no longer inadvertently change
    - Fixed UI bug where layout page name prompt popups might have appeared below the main window
    - Upgraded to json-20250107

* v2.5.6 [5/6/2025]
    - Routes
        - When renaming a route, references from other routes will also be updated
        - Route references with empty route names will now throw an error
        - Routes that reference themselves will no longer fire
    - Track Diagram Editor
        - When autonomy is enabled, the editor will no longer appear underneath the graph window

* v2.5.5 [5/3/2025]
    - Track Diagrams
        - In autonomy mode, the origin station is now highlighted in green for the duration of the route
        - Address labels now also have tooltips
    - Track Diagram Editor
        - Improved the phrasing of right-click menu options

* v2.5.4 [4/27/2025]
    - Track Diagrams
        - Added a setting (in the Preferences -> Layout menu) to show address labels in track diagrams
    - Fixed bug where clicking on Cancel in the layout editor would prevent future keyboard commands from working

* v2.5.3 [4/21/2025]
    - Routes
        - Routes can now trigger other routes
        - When a route triggers another route, no further routes will be triggered from the second route

* v2.5.2 [4/19/2025]
    - Track Diagram Editor
        - Added a "Redo" option
        - Increased undo history to 100 actions
    - Track Diagrams
        - Added a "Download Central Station Layout" menu option to easily download all layout files for local editing
        - The "Show Current Data Source" option will prompt whether you want to view the files (was previously always done)
        - Improved error handling/messages when opening an invalid track diagram
    - Added a "Debug" indicator to the window title when in debug mode

* v2.5.1 [4/6/2025]
    - Track Diagram Editor
        - Added a right-click shortcut to route tiles to enable easy editing of the corresponding route
        - Improved the clarity of different address options in the right-click menu
        - Improved the clarity of tile names in tooltips
    - If an emergency stop route automatically fires, a pop-up notification will now be shown in the UI
    - Fixed bug where non-sequential route IDs could not be selected in the track diagram editor

* v2.5.0 [4/5/2025]
    - Track Diagram Editor
        - Added a native track diagram editor, allowing the editing of track diagrams on Mac/Linux in addition to Windows
        - Added a "Modify Layout" entry to the Layouts menu, enabling the management of track diagram pages
        - The "Show Data Source" option in the Layouts menu will now open a file explorer or browser with the layout source files
        - Added shortcuts to edit links, routes, and autonomy station labels based on the respective configuration
    - Track Diagrams
        - When the power is off, route icons can be right-clicked to edit the route
        - Accessory tooltips will now show the decoder type
        - Layouts downloaded from the CS2 and CS3 will now recognize DCC accessories
        - You can now place the active locomotive at an autonomy station by right-clicking the label in the track diagram
    - Accessories
        - Added support for DCC accessories (up to address 2048)
        - Added support for MM2 accessories with addresses 257-320
        - Added additional keyboard pages to cover addresses up to 2048
        - The keyboard page now allows both DCC and MM2 accessories to be controlled
        - `Locomotive`, `Accessory`, and `RouteCommand` APIs have been updated to require the protocol for accessory commands
    - Routes
        - Accessory commands and accessory conditions can now reference DCC accessories, e.g. `Switch 1 DCC,turn` vs `Switch 1,turn`
        - DCC accessories are now recognized in routes imported from the CS3 and CS2
    - Autonomy
        - Edge configurations can now contain DCC accessories
    - Graph UI
        - Enhanced Edge editing / copying
        - When changing the Graph UI options, the graph window will be reopened if it was closed
    - The IP of the Central Station is now printed to the log at startup
    - Backward-incompatible changes:
        - Route JSON files from v2.4.3 and older are no longer compatible.  Export routes from v2.4.4+ prior to upgrading.
        - LocDB.data files from v2.3.x and older are no longer compatible.  Run v2.4.0+ at least once prior to upgrading.

* v2.4.12 [3/24/2025]
    - Graph UI
        - Points can now more easily be converted to station/terminus/reversing
        - Missing S88 addresses will now automatically be prompted
        - If an S88 address is removed, a station will now automatically be reverted to a point
        - Edges are now highlighted while being selected in the Edit dialog
        - Improved the appearance of the Point right-click menu, and added new tooltips
    - Track Diagrams
        - Occupied intermediate stations from autonomy routes are now highlighted more clearly
    - Fixed UI bug where reversing (non-station) points could not have their S88 removed

* v2.4.11 [3/23/2025]
    - Autonomy UI
        - Added a slider to optionally set a maximum number of locomotives allowed to run concurrently
        - Added corresponding `maxActiveTrains` JSON key
    - Graph UI
        - Both incoming and outgoing edges can now be deleted and edited from the Point right-click menu
        - Edges are now highlighted prior to copy/deletion
        - Newly added points are automatically added to the clipboard for easier edge creation
        - Enhanced various tooltips and error messages

* v2.4.10 [3/12/2025]
    - Track Diagrams
        - Added a right-click menu to autonomy labels.  These can invoke locomotive commands and start/stop autonomy.
    - In the preferences menu, added an option to auto load the autonomy graph at startup
    - Bundled TrackDiagramEditor v2.2.2

* v2.4.9 [3/9/2025]
    - Track Diagrams
        - Locomotive autonomy locations can now be shown on the track diagram.  Text labels in the format `Point:StationName` will now display the locomotive at StationName.
        - Autonomy location labels will be hidden when the autonomy graph UI is closed
        - Added an option to immediately turn the power on when attempting to switch an accessory while the power is off
        - Improved the quality of route icons in large diagrams
    - Graph UI
        - The graph UI will now automatically re-open if a semi-automatic route is activated
        - Consolidated advanced options in the right-click menu into a sub-menu
    - Control and Control+Alt combined with numpad numbers will now activate F10-19 and F20-29, respectively

* v2.4.8 [2/28/2025]
    - Graph UI
        - Added a right-click setting to optionally adjust the speed of trains incoming to a point by 1-200% (default of 100% maintains old behavior)
        - Added corresponding `speedMultiplier` JSON key on each `Point`
    - When capturing commands for routes or lock edges, prior duplicates are now automatically removed

* v2.4.7 [2/23/2025]
    - Autonomy UI
        - Added a setting to automatically turn off the power if the network latency is too high
        - Added corresponding `maxLatency` JSON key

* v2.4.6 [2/14/2025]
    - Graph UI
        - Added a "test" button to simulate the configuration commands on an edge
        - Improved the alignment of the buttons in the edge editor
    - Fixed bug from v2.4.1 where routes would not execute when clicked in the route table

* v2.4.5 [2/11/2025]
    - Graph UI
        - Added Control+S hotkey to change the S88 address of a node
    - Autonomy UI
        - Clicking on a locomotive name in the Locomotive Commands tab will now select that locomotive
        - Rearranged buttons on the Autonomy Configuration tab, for a more consistent appearance
        - Improved the phrasing of various error messages and tooltips
        - Fixed bug where inactive locomotives would sometimes be shown in front of active ones in the Locomotive Commands tab

* v2.4.4 [2/2/2025]
    - Routes
        - To minimize collisions with Central Station routes, routes created in TrainControl now start at ID 1000 instead of 1
        - To avoid data being overwritten, routes defined in the Central Station can no longer be edited in TrainControl
        - Routes from the Central Station are now designated with a *
        - Improved the clarity of the route help dialog
        - Improved the performance when editing routes
        - In the Optional Conditions, improved the rendering of AND conditions that follow parentheses
        - Improved the appearance of the route UI
    - Fixed bug from v2.4.3 where saved route data would sometimes be corrupted
    - Fixed bug where a route could be renamed to the same name as an existing route
    - Fixed UI bug where the route list would be temporarily incorrect if imported routes conflicted with routes in the Central Station
    - Minor UI/tooltip enhancements

* v2.4.3 [1/29/2025]
    - Routes
        - Route S88 and accessory conditions are now editable in a single field
        - Added support for complex boolean expressions (parentheses, OR, implicit AND for consecutive lines) in route conditions
        - Added buttons to insert logical operators in the route condition editor UI
        - Added button to test the route condition
    - The position of the route editing window will now be remembered
    - Enhanced Central Station check at startup by checking for VNC connection

* v2.4.2 [1/22/2025]
    - Fixed bug where conditional accessories in routes would be ignored if there was no conditional S88
    - Fixed occasional active locomotive rendering issue at startup
    - Fixed typo in `CSMessage.isOtherCommand` (was isOtherComannd)

* v2.4.1 [1/5/2025]
    - Routes
        - The route ID will now be displayed when sorting by ID
        - Changed the number of columns in the UI from 4 to 3 to make it easier to view long route names
    - UI
        - Added an option to remember window positions between runs.  This will also re-open track diagrams.
        - Replaced function icons with "active" icons on the CS3, for a more intuitive look
        - All possible icons (ones from CS3) will now be shown when in debug mode
    - Fixed bug where a column of function icons was not always visible in the icon customization UI

* v2.4.0 [12/30/2024]
    - Locomotives
        - Any MM2/DCC/MFX locomotive can now be converted to a multi-unit (linked to other locomotives) via right-click or Control+L
            - When active, locomotives customized as multi-units in TrainControl will be designated with a "MU" prefix
            - All commands will be replicated to linked locomotives
            - Linked locomotives can have a speed multiplier
            - Linked locomotives can be forced to run in the opposite direction of the main locomotive
        - The locomotives belonging to multi-units defined in the Central Station can now be viewed
        - Displayed multi-unit addresses now start at 1
        - Corrected multi-unit address validation
        - `MarklinLocomotive.setAddress` will now always require valid addresses
    - Routes
        - Accessory commands are now easier to read (i.e. "Switch 1,turn" instead of "1,1")
        - Tiles affected by routes will now be temporarily highlighted in track diagrams
        - Decluttered the route editor window
    - Autonomy graph
        - Added keyboard shortcuts (Control+E/U) to exclude/unexclude the active locomotive when hovering over a node in the autonomy graph
        - When editing edges, added a new option to capture switch and signal commands from the track diagram
        - When initializing a new graph, added an option to load a sample graph corresponding to the sample layout
        - Locomotives can no longer be edited in the UI while autonomy is running
        - When adding a new locomotive, a warning will now be shown if all locomotives in the database are already on the graph
    - Accessory database
        - Warnings will now be shown in the log if an accessory address is out of range (>= 256)
        - Accessories with invalid addresses will now be automatically removed from the internal database at startup
        - Simplified the `newSwitch` and `newSignal` API methods in `MarklinControlStation` to only require the logical address
    - UI
        - Moved the "quick find locomotive" option to the toolbar Locomotives menu
        - Control+D is now a shortcut to add new locomotives
        - Updated text labels in track diagrams to match the UI font
        - Improved the formatting of the active locomotive button/page indicator
        - Control+Up/Down will now adjust the active locomotive speed by 1
        - Added an automatic new version check, with a download button and info link added to the help menu
        - Added preference to enable/disable automatically checking for updates
        - Added preference to enable/disable auto power on at startup (was previously always forced on)
        - Improved the appearance of labels in the semi-autonomous operation UI
        - The locomotive add window will now close when Escape is pressed
    - Bug fixes
        - Updated libraries (Flatlaf v3.5.4, JSON 2024-03-03)
        - Fixed graph integrity error where S88 sensors could be removed from stations once created
        - Fixed bug where the second address of a 3-way switch could erroneously be named a signal
        - Fixed bug where captured route commands might be duplicated
        - Fixed minor UI alignment issues
        - Fixed bug where emergency stop commands would not work in routes
        - Fixed alignment issue in semi-autonomy UI when locomotives were inactive (from v2.3.0)

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
    - Added a pop-up error message if an invalid layout file path is chosen via the "Open Layout" button within Tools
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