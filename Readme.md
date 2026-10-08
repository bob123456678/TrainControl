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
* Fully autonomous train operation using S88 sensors and digital switches (signals optional)
* Multi-units, function presets, conditional routes, and usage statistics
* Locomotive and layout data downloaded automatically from your Central Station
* Free, open source, and actively developed

**Getting started**

1. Connect your Central Station 2/3 to your network and enable CAN broadcasting in its settings ([details](#requirements))
2. [Download the latest `TrainControl.jar`](https://github.com/bob123456678/TrainControl/releases/latest)
3. Run it, and enter your Central Station's IP address when prompted ([details](#download-and-run-traincontrol))

Under the hood, this program implements the Marklin CAN protocol and can therefore
also be used to programmatically control the entire layout ([see API](AutomationAPI.md)).  Layout and locomotive information is automatically
downloaded from the CS2/CS3 (with minor limitations on the CS3). Track diagrams can also be designed entirely in TrainControl.

For easy scripting or interactive control, you can write [Python (Jython) scripts to call the TrainControl API](src/org/traincontrol/examples/traincontrol_python_example.py).

TrainControl also lets you set up automation on your track diagram itself,
which when paired with S88 sensors keeps track of where every train is, for *fully autonomous* operation at the push of a single button,
as well as point-to-point operation between stations. You can of course also set up traditional/conditional routes to 
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
    - Start TrainControl, then from the Layouts menu, click on "Create New Layout"
    - If no Central Station layout is detected and no static layout is manually selected, TrainControl will automatically create an editable demo layout at startup.

Complete editing support is accessible via the "Edit" button within the Layout tab. Pages can be managed from the Layouts menu item.  This will let you fully customize your layout.

If you change the local files, clicking on "Sync Database w/ Central Station" from the Locomotives menu will update the layouts.  This effectively lets you customize the layout even without a CS2/CS3.  Some users might find this easier than inputting data into the Central Station UI.

Some sample files are included in the `cs2_sample_layout` folder.

---

</details>

**Routes**

Conditional routes can be defined for semi-automatic layout operation, such as setting a switch to guide an incoming train to an unoccupied station track, or triggering an emergency stop.  Manual routes can also be defined and activated directly or via the layout tab.

In addition to all Central Station functionality, complex logical expressions are supported.

![TrainControl's route editor: each command a row of dropdowns, and its conditions an indented list joined by "and" and "or"](assets/ui_route3.png?raw=true)

**Keyboard**

Useful for testing, individual accessories can be directly controlled via their digital address.  The cumulative number of actuations of each accessory is shown as a tooltip.

![Accessory keyboard in TrainControl, used to control switches and signals directly by digital address](assets/ui_keyboard.png?raw=true)

**Full Autonomy**

TrainControl can run your trains on its own.  In version 3.0 you set this up on the track diagram itself, in the autonomy editor.  TrainControl works out how your track connects, so there is no graph to build and no file to write.  You set up stations, directions and settings by right-clicking a square.  The setup is checked before it runs, and each problem it finds takes you to the square it is about.

You need S88 sensors, at least one per station, and digital switches.  Signals are optional ([what you need](Automation.md#what-you-need)).  Once you have placed your trains, TrainControl keeps track of where every train is.  You can send a particular train to a station, or let it keep picking destinations at random.  Where your trains stand is saved when you exit; changes in the autonomy editor are kept with its Save Changes button.  If you set up autonomy in an older version, its [JSON configuration file](AutomationAPI.md) can be imported from the Autonomy menu.

**Full guide:** [The autonomy editor: a user guide](Automation.md)

![The autonomy editor: the track diagram with its stations named, a list of things to look at below it, and tools and settings on the right](assets/autonomy_editor3.png?raw=true)

![A train running under autonomy on the track diagram: its route ahead in blue, the track it has passed and still holds in dark grey, and a second train standing at its station](assets/autonomy3.png?raw=true)

The track diagram shows each running train's route, the track it is holding, and where every train is standing.  While trains are not running, you can right-click a station to place a train there, or right-click a train to send it somewhere.

In addition to the continuous automated operation and point-to-point commands, you can also specify timetables and run your trains according to a predefined list of paths, subject to the same protections as autonomous operation.

<img src="assets/timetable3.png?raw=true" alt="The timetable in TrainControl: a captured sequence of journeys, played back in order" width="600">

Point-to-point operation can also be controlled directly from the track diagram: right-click a train to send it to a station.

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
    * Choose how trains pick their route: at random, past the fewest or most stations, over the shortest or longest track, across the fewest or most sensors, by whichever station has gone longest without a train, or by weighing a station's priority against its distance
    * Pair a station with signals that turn red while a train is standing there, and back to green when it leaves
    * Restrict which directions a station will accept trains from
    * Record and play back timetables, and send every train back home
    * Hide the names of stations autonomy will never send a train to, so a busy diagram shows only
      the places trains actually go
* Back up everything in one archive - locomotives, keyboard mappings, track diagrams, routes and
  autonomy setups - and restore it on another machine
* Programmatic layout control via Java API (uses CAN protocol - [see documentation](AutomationAPI.md)) 
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
* UI shortcuts
    * Control+M (show menu bar)
    * Backspace/Alt+backspace, CapsLock/Alt+CapsLock (cycle through tabs)
    * Plus/minus, \[/\], '/( (cycle through keyboards and layout pages, Control+plus/minus jumps 4 keyboards)
    * Slash/question mark, < (cycle through function tabs on the locomotive panel)
* Track diagram editor
    * Control+Z (undo)
    * Control+C (copy hovered tile)
    * Control+X (cut hovered tile)
    * Control+V (paste tile)
    * Control+R (rotate hovered tile)
    * Control+T (edit text of hovered tile)
    * Control+A (edit address of hovered tile)
    * Control+Y (redo)
    * Control+L (show text labels)
    * Control+D (show address labels)
    * Control+K (show the grid)
    * Control+I (increase diagram by 1 row and 1 column)
    * Control+M (select by dragging a box)
    * Plus/minus (next / previous page)
    * Shift+click (pick several squares at once)
    * Delete (delete hovered tile)
    * Escape (let go of whatever is held; with nothing held, close the editor)
    * Left mouse click (cut hovered tile / paste new tile)
    * Middle-mouse click (rotate hovered tile)
    * Right mouse click (show all options)
* Autonomy editor
    * Control+S (name the hovered square)
    * Control+N (show a station name on the hovered square)
    * Control+E (set the length of the hovered piece of track)
    * Control+B (set the hovered station's longest train)
    * Control+H (set the hovered station's home locomotive)
    * Control+G (show track lengths)
    * Control+L (step through the station caption options)
    * Control+D (show address labels)
    * Control+K (show the grid)
    * Plus/minus (next / previous page)
    * Shift+click (pick several squares at once)
    * Escape (put down an armed tool; with nothing armed, close the editor)
* Track diagram (with autonomy loaded and stopped, pointer over a station)
    * Control+X (pick up the train standing there)
    * Control+V (put it, or the locomotive selected in the main window, down)
    * Delete (take the train off)

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

To make a backup, select "Backup TrainControl Data" from the File menu.  It writes a single zip file into the `tc_backup` folder holding everything TrainControl knows: your locomotives and their icons, your keyboard mappings, your routes, and - for a layout stored on this computer - the track diagrams and the autonomy setups that go with them, filed under the layout folder's own name so two backups of two layouts can be told apart.

If your track diagram lives on the Central Station rather than on this computer, TrainControl offers to fetch it and include it, so the archive is complete either way.  On a CS3 it also collects the station's own locomotive, route and accessory data, which the older file format does not carry.

To restore, close TrainControl, unpack the archive beside the JAR file, and start it again.  `LocDB.data` and `UIState.data` belong next to the JAR; a layout folder goes wherever you keep your layout, and is the folder you point TrainControl at with "Open Layout...".

## Support TrainControl

**TrainControl is free — no ads, no paywalls, and no locked features.**  It is built and maintained by one person, in his spare time.  If it has made your layout more fun to run, a coffee helps keep new features coming.

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-Support%20TrainControl-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://www.buymeacoffee.com/traincontrol)

## Building from Source

Requires JDK 1.8+ and the following libraries:

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

* v3.0.0
    - Features
        - Autonomy
            - Automation is now set up on the track diagram itself, and the separate autonomy graph is gone.  TrainControl reads the track you have already drawn and works out for itself which squares connect to which, so there is no graph to build and no JSON file to write: stations, directions and settings are all edited by right-clicking a square.
            - Autonomy setups are saved as named configurations that can be copied with New Configuration - which asks whether where the trains stand and the timetable come too - renamed, deleted, exported and imported, and the one you were last using is loaded when TrainControl starts, unless Load Autonomy has been unticked.  An autonomy.json from an older version can be imported from the same menu.
            - Autonomy needs a track diagram stored on this computer.  Without one, the Autonomy menu's "Autonomy needs a layout stored on this computer" offers to download it from your Central Station, as Layouts → Download Central Station Layout Files does, or to create a new one.  The old Load Autonomy Configuration tab is gone, and TrainControl no longer reads or writes an autonomy.json of its own; the setup is saved in the layout folder.
            - The Autonomy menu has a Documentation item that opens the automation guide.
            - You can now choose how trains pick their route when more than one will do: at random respecting station priority (the default, and how earlier versions behaved), completely at random, past the fewest or the most stations, over the shortest or the longest track, across the fewest or the most sensors, by whichever station has gone longest without a train, or by weighing a station's priority against how far away it is.  The choice is stored with the autonomy configuration, so two configurations can use different rules.
            - Track with no recorded length now counts as one, so the shortest-track and longest-track rules give different answers on a railway where most sections are unmeasured.
            - A station can be paired with signals, which are set to red while a train is standing at that station and back to green once it leaves - "Exit Guard Signal" on the station's right-click menu.  Pick each signal by clicking it on the diagram, or by typing its address.
            - A station can also have entry guard signals, set to red when a train arrives there at the end of its journey - "Entry Guard Signal" on the same menu.  The next route that needs one sets it green again.
            - The autonomy editor's Bulk Tools menu can walk a page asking for each missing track length and each station's missing longest train, and walk every train autonomy would run that has no length of its own - and can clear every track length, or every station's longest train, at once.
            - A switched-off station can no longer be sent to by hand, nor driven through - switched off now means nothing may be sent to it or through it, though a train already standing on one can still be driven away.  To keep a parking track reachable by hand but out of autonomy's choices, leave it switched on and untick Can Be Chosen in Full Autonomy on its right-click menu.
            - Atomic Routes stays switched on while any track autonomy runs over, or any train, has no length recorded, because releasing track behind a train depends on knowing how long both are.  It can be switched off once everything is measured.
            - Stations can say which directions trains are allowed to arrive from, and one-way travel restrictions are now drawn on the ordinary track diagram as well as in the editor.
            - A running train draws its route along the track, following the rails through curves and switches: the stations' blue for the track ahead of it, dark grey for the track it has driven and still holds, white arrows showing which way it is going, and the train itself in orange along the length of track it covers.  Where routes are not atomic, the grey goes as the train gives the track behind it back.
            - When a train is not going anywhere, TrainControl now says why.  Hovering "No available paths" in the locomotive list names every station it might have been sent to and the reason each one was refused - occupied and by whom, switched off, excluded, or no track at all.  The setup editor has a matching "Why not Moving?" tool that draws every route the train could take on the diagram and lists the reasons for the rest underneath.
            - The setup is checked before it can run, and every finding can be clicked to jump to the square it is about.  Among the things now checked for: two pages sharing one s88 address, a page link that track runs into but which is not paired, a place where trains turn round that leads nowhere, and a train or a station with no length set.  A train left facing a way its station no longer has, and a station at the end of a line where trains may not change direction, are errors; track made one way towards a standing train is a warning.  No setup change is refused because a train is standing in the way.
            - The autonomy button on the track diagram offers to Fix a setup that has errors, opening the editor at the first thing to deal with, rather than staying green and refusing every time it is pressed.
            - Station names can be hidden for stations autonomy will never choose - ones switched off, and ones not marked as automatic destinations - under Preferences → Autonomy → Show Inactive Labels.
            - Station labels can be dragged onto another square in the autonomy editor, and the station chooser opens on the nearest station on that page.
            - Station names you had typed onto the track diagram in an earlier version (the ones written as "Point:name") are taken over by the new setup the first time it opens, and removed from the page file so they are not shown twice.  Each page that is changed gets a .bak file beside it holding the page as it stood the first time this version rewrote it, and the log says which pages were changed.  A name that no longer matches any station is left exactly where it is.
            - "Unavailable while occupied" can be answered by clicking the square on the diagram instead of finding it in a list, in the same way a station's signal is paired.
            - The track diagram's Autonomy Setup menu opens the full editor on the page and square you right-clicked, and every item on it names the square it is about.
            - Right-clicking a train on the track diagram puts the stations autonomy chooses from at the top.  The stations you have unticked "Can Be Chosen in Full Autonomy" on, such as parking tracks, are under "More Destinations", which is not shortened.
            - **If you ever go back to v2.8.2 or earlier after using this version, keep a copy of your settings first.**  This version allows up to fifty locomotive keyboard pages and those versions read only the first ten - so opening your settings in the older version and closing it again discards pages 11 and up for good.
            - Which function autonomy fires on departure and on arrival is ticked on the function itself, from its own right-click menu, and a train's length is set from a dropdown on the locomotive's right-click menu.
            - In autonomy mode, a link switched off in autonomy is greyed out on the track diagram, not only while editing.
            - Nothing that changes the setup can be used while trains are running.  The Autonomy and Layouts menus grey whatever would change the setup or the track diagram, saying why - Open CS3 Web App, the pop-up pages and the picture export stay - and the Auto tab's settings, Execute Timetable and Capture Locomotive Commands are greyed until the trains have stopped.
            - When the setup has changes the railway has not taken yet - a link left unpaired while you edit, say - the strip above the track diagram says "Setup changes not applied yet", and the last setup that worked stays loaded until the setup builds again.
            - Bulk Tools has Allow Every Path, which opens every one-way or closed piece of track again in one go, asking first.
            - The autonomy editor's Track Directions can show only the directions trains may run, and the track diagram can draw them in green - Preferences → Autonomy → Display Allowed Directions.
            - Switches without a decoder can be drawn with the Static switch tiles.  Autonomy runs trains through them from the legs towards the points only, and two Static Y switches drawn point to point act as a crossing.
            - With the pointer over a station on the track diagram, Control+X picks up the train standing there, Control+V puts it down, and Delete takes it off.
        - Routes
            - A route will no longer throw a switch on track a train is running over, nor turn a signal green at a platform a train is standing at.  From the Routes tab and from a route tile on the diagram you are asked whether to go ahead anyway, once per route; a route fired by a sensor skips only the switches and signals a train is on or standing at, because there is nobody there to ask - the rest of it, such as speeds, functions and its other switches, still runs.  A route with an emergency stop in it runs without asking: its stop is always sent, and a switch under a train is still left alone.  Each command is checked again immediately before it is sent, so a train dispatched while a route is part way through is not missed.  Routes that touch nothing a train is using run exactly as before, and nothing changes at all when autonomy is stopped.
            - A route with an emergency stop can no longer have other commands in it.  A route that has both is split automatically when it is loaded or imported: it keeps everything else, and fires a new route named after it that is only the emergency stop.
            - A new route editor built from dropdowns rather than typed commands.  Each command is a row - what kind of thing, which one, what to do to it, which decoder it speaks to, and how long to wait afterwards - so a route can be built and read without knowing the command syntax, and rows are added, duplicated, deleted and reordered from marks in the rows themselves.
            - Conditions are written as an indented list instead of with brackets.  Each line is either a condition or the word joining it to the line before, and both can be indented, so "sensor 1 occupied, and either sensor 2 or sensor 3" is written by indenting the two sensors under the word that joins them.  A word that disagrees with the others at its level is shown in red until it is indented or changed.
            - Capturing commands by working the railway still works exactly as before, and can now be pointed at the conditions instead - so "run this route when these points are already set the way I have just set them" can be built by setting them.  Routes that came from the Central Station open read-only.
            - Every route in the Routes tab now has a play button that runs it straight away, while a click elsewhere on the route still asks first and a right-click still opens the menu.
            - Find Route on the Routes menu takes a name or part of one: an exact name goes straight to that route in the list, and a fragment matching several offers the choice.
            - Routes read from a file with Import Routes arrive with automatic firing switched off, so nothing starts firing before you are ready.  If the file has routes that were saved with it on, you are asked whether to turn it back on for those.  Otherwise turn on the ones you want from their right-click menu, or all at once with Bulk Enable.
            - Deleting or saving a route created in TrainControl no longer forces a full sync with the Central Station.
        - Track Diagrams
            - You can now pick out several squares at once in the track diagram editor.  Shift-click picks a square, shift-click again unpicks it, and Escape lets everything go; a picked group can then be dragged, copied, pasted, rotated or deleted as one, and one press of undo takes the whole thing back.  A group dragged or pasted past the edge of the diagram is refused rather than losing the part that would fall off.
            - The editor now has a matching pair of size controls: one adds a column on the right and a row at the bottom, the other takes the same two away.  Shrinking is refused if either of those edges still holds track.
            - Removed the "paste entire row" and "paste entire column" options, which each filled from the pasted tile to the edge of the diagram.  Picking the squares you mean and dragging them does the same job, visibly, and can be corrected before it happens rather than after.
            - The + and - keys step through the pages in both the track diagram editor and the autonomy editor.
            - Station labels are now light grey ovals rather than names in square brackets - blue with white lettering if you prefer (untick Grey Station Labels) - and the direction a train is facing is drawn as an arrow instead of a chevron.  They sit just below an east-west track and read upwards beside a north-south one, so they no longer cover the square they name.
            - A small locomotive marks each train on the track diagram, facing the way it is going: on the sensor a running train is passing, and on the station where a train is standing, laid along the rail on a curve.  A train passing through a station shows an arrow for its direction of travel, and a train standing still on a route it holds is drawn where it stands.
            - A track diagram page can now be saved as a picture.  The Layouts menu offers the page you are looking at in one click, and writes the whole of it at whatever size you choose - not just the part scrolled into view, and without the window around it.
            - Track diagrams now show a spinner while they are being drawn, instead of the text labels appearing about a second before the track did, and loading a layout from disk shows what it is doing rather than appearing to do nothing until the finished diagram arrives.
            - Layouts → Manage Pages → Combine Linked Pages... makes one new page showing this page and every page its links lead to.
            - In the track diagram editor, Control+M, or Select by Dragging a Box on the right-click menu, picks out several squares by dragging a box round them.
            - The tunnel and overpass squares have new, cleaner icons.
        - Central Station
            - Backing up TrainControl now writes a single archive holding everything - the locomotive database, the window layout, the autonomy setups and the routes - and offers to download the track diagram first if it lives on the Central Station rather than on this computer.  The dialog offers to show you the file when it is done, and says so if anything could not be copied.
            - Syncing with the Central Station no longer freezes the interface.  A spinner appears while it works, from every place a sync can start, and a second sync started while one is running is turned away rather than run alongside it.
            - The Layouts menu now says where the track diagram is coming from: a folder on this computer, the Central Station, or nowhere yet.
        - Interface
            - The seven sidebar icons have been redrawn as plain, flat marks in dark grey.  The autonomy tab is now a play symbol rather than a diagram of the old autonomy graph, and the routes tab shows a path with an arrow on it rather than a set of points.  The locomotive is drawn larger so that the keyboard page number sitting on top of it can be read.
            - Local locomotive icons can be cropped and panned when you pick them.  There is a tick box in the file chooser - off until you turn it on - the crop is written as a new file beside the locomotive database rather than over your own picture, and re-cropping an icon reopens at the framing and zoom it was taken with.
            - Locomotive keyboard pages are now capped at fifty.  Add New Page is greyed out with the limit in its tooltip once you get there, and an installation that already holds more than fifty still loads all of them.
            - The autonomy setup is translated into all eight languages.
    - Bug Fixes
        - Autonomy
            - Fixed a long-standing bug in how a train's length is accounted for as it runs.  Track behind the locomotive was released as soon as the edges waiting to be released added up to the train's length - and the newest of those is one edge behind the engine, so any track section shorter than the train was handed back while the train was still standing on it.  With atomic routes off that offered occupied track to another train.  It only bit layouts that have both track lengths and train lengths recorded, which is why it went unnoticed.
            - Fixed bug where a path that failed part way through left the sensor it was heading for waiting for ever, so any route whose condition asked whether a train had reached that sensor quietly stopped firing.
            - Fixed bug where starting autonomy with every locomotive skipped - because its starting point was switched off, or because no speed had been chosen for it - left the layout believing it was running with nothing running, refusing placements, point renames and simulation until it was stopped.  It now says why nothing started, and Start comes straight back.
            - Fixed bug where the list of places a train could be sent to, on the track diagram's right-click menu, showed two fewer than it should.
            - Fixed bug where "return home" refused the entire run, naming two trains that were already standing at home, when their two home stations share a track sensor.
            - Fixed bug where "return home" planned a move onto a piece of track sharing the sensor the train was already standing on, which the railway then refused.
        - Track Diagrams
            - Fixed bug where a layout folder with one page that could not be read was thrown away whole, along with your choice of folder.  The other pages now load, and the one that failed is named.
        - Central Station
            - Fixed bug where a short message from anything else on the CAN bus was read as an emergency stop, so TrainControl believed the power had been cut while the layout was still running.
            - Fixed bug where a layout download interrupted part way through left a half-written diagram file that the next sync then treated as the real one.
            - Fixed bug where importing an MFX locomotive whose record has no address gave it an address no decoder can have, so nothing sent to it arrived.
            - Fixed bug where, when the same accessory was set to the same position twice, TrainControl ignored the Central Station's report of the second command.
            - Pressing Stop while TrainControl is not connected now says so in the log instead of doing nothing silently.
        - Interface
            - Confirmations that delete or overwrite something no longer open with Yes already selected: deleting a route, a locomotive or a track diagram page; clearing or replacing a page's key mappings; clearing the timetable; resetting a locomotive's functions to the Central Station's; and leaving the track diagram editor without saving.
    - Code
        - Updated JSON library to json-20260814.jar and FlatLaf to 3.7.2, and dropped the GraphStream libraries that the old autonomy graph needed.

* v2.8.2 [9/25/2026]
    - Autonomy Bug Fixes
        - Fixed bug where renaming a locomotive, or one of the locomotives in a multi-unit, took the train off the station it was standing at, so autonomy could send another train there
        - Giving a locomotive in a multi-unit the address of a train standing on the graph now takes that train off the graph, as placing the multi-unit does.  Previously autonomy could run it as a train of its own while the multi-unit’s commands also moved it
        - Clearing a station’s priority no longer stops autonomy from sending trains out, or the graph from being saved
        - Fixed bug where a running train could miss its stop sensor and drive through its station while another train’s route was being set
        - Return Home no longer starts and then stops every train when one of them has no speed set or is not at a station.  A train with no speed is now skipped and the others still go home, and a train that is not at a station is named before anything moves
        - A locomotive placed on the graph by hand without a speed now gets the default speed, as it does when the graph is loaded.  Previously Start and Return Home skipped it until the graph was loaded again
        - Double-clicking Start no longer starts every train twice
        - One timetable entry that can no longer be loaded, such as after its locomotive was deleted, no longer wipes out the whole timetable
        - If a train’s trip fails part way, autonomy now stops itself and says why.  Until the graph is validated again no train is sent, and no locomotive can be placed on the graph or taken off it, though a train’s settings, such as its departure function, can still be changed.  Previously the other trains carried on, and the failed train could later be sent off from the wrong place
        - Validating again, or closing TrainControl, then keeps the trains where the run left them, and the failed train at the last place it is known to have reached.  If it is not standing there, move it on the graph before starting autonomy again
        - Messages from the track diagram’s right-click autonomy menu, such as asking for the track power to be turned on, no longer open behind the main window when Window Always on Top is ticked
    - Route Bug Fixes
        - Fixed bug where editing a route, or switching it on or off, removed it from the routes autonomy is set to activate
        - Importing routes no longer garbles accented letters in route and locomotive names, which stopped a route’s commands for such a locomotive from working
        - Routes and autonomy files exported by TrainControl 2.7.3 or earlier also import with their accented letters intact
    - Locomotive Bug Fixes
        - If the locomotive list or the keyboard pages cannot be read when TrainControl starts, a copy of the unreadable file is now kept in the tc_backup folder before it is saved over.  Previously it was saved over as empty when TrainControl closed
        - Page names are no longer lost when going back to 2.8 after using TrainControl 3.0 with fewer than ten pages
    - Central Station Bug Fixes
        - After a short network drop, TrainControl no longer keeps reporting the connection as lost until it is restarted, and autonomy no longer keeps turning the track power off because of it
        - Finding the Central Station automatically no longer fails when it answers slowly, or when one reply on the network is lost

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
        - Added Path Integrity Validation.  If a switch or signal cannot be confirmed by the Central Station, the locomotive will not run.  Configurable under the menu bar preferences, and a warning is shown if confirmation keeps failing
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
        - Fixed bug where entering the Central Station's network name instead of its IP address would report it as unreachable, even though it had just responded
        - A locomotive address change picked up from the Central Station is now postponed while trains are running, instead of being applied underneath them.  A message in the log says when this happens
    - Autonomy Bug Fixes
        - Fixed bug where only one locomotive could be triggered from the track diagram in semi-autonomous mode
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
        - Fixed bug where opening and saving a route with certain combinations of AND and OR conditions could silently change when the route fires.  Only affected condition logic written directly into the configuration file
    - Locomotive Bug Fixes
        - Fixed bug where changing a locomotive to a decoder type with fewer functions could leave its arrival or departure function pointing past the end, which then made the locomotive impossible to edit or place on the autonomy graph
        - Fixed bug where clearing a locomotive’s custom icon while not connected to the Central Station left it with no image for the rest of the session
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
        - A stray space in a hand-typed sensor line no longer silently flips the state it waits for, and a mistyped direction is now reported as an error instead of quietly running the locomotive backward
    - General Bug Fixes
        - Update notices now work even if a release name contains extra text after the version number
        - Your locomotive database, window layout, and autonomy graph are now saved safely when TrainControl closes.  Previously, if the computer shut down or lost power at the moment of saving, the file could be left unreadable and its contents lost - the locomotives themselves would come back from the Central Station on the next sync, but their function assignments, notes, and statistics would not
        - The debug log no longer slows TrainControl down during a long session.  It used to grow without limit, making the whole application gradually less responsive

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