# Marklin Train Control

This program allows you to use your computer to control your Marklin layout.  
It is primarily designed for users with a large layout / many locomotives, as
the standard Marklin UI makes many common tasks (such as quickly switching between locomotives)
overly tedious.

Under the hood, this program implements the Marklin CAN protocol and can therefore
also be used to fully automate a layout.  Layout and locomotive information is automatically
downloaded from the CS2/CS3.

[UI screenshot](interface.png)

Features:

* Configure hotkeys for different locomotives, accessories, and functions
* Control locomotives, signals, and switches
* View S88 feedback
* Download locomotive, layout, and route information from the CS2/CS3
* View and control the entire layout just like on the CS2/CS3
* Progammatic layout control via API (uses CAN protocol)

Requirements:

* Must be on the same Wi-Fi network as the CS2/CS3

Limitations:

* This program has currently only been tested with the CS2
* CS2 IP address must be manually entered
