# This is an example of how to use TrainControl libraries in Python via Jython
# Download Jython from https://www.jython.org/ first
# Example command line usage:
# java -cp "/path/to/jython.jar;/path/to/TrainControl.jar" org.python.util.jython tc.py
# 
# Or to open an interactive session, run this command and then execute the below import commands
# java -cp "/path/to/jython.jar;/path/to/TrainControl.jar" org.python.util.jython 

# Import necessary Java classes
from java.util import List
from org.traincontrol.marklin import (
    MarklinControlStation,
    MarklinLocomotive,
    MarklinAccessory
)

def execute_code():
    try:
        # Demo code without connecting to a Central station?
        simulate=True
        show_ui=False
    
        # Initialize the Marklin Control Station
        mcs = MarklinControlStation.init(None, simulate, show_ui, True, False)
        print("Control Station Initialized!")

        # Example: Turn on the power
        mcs.go()

        # We have a Marklin MM2 locomotive with address 6
        if "BR 64" not in mcs.getLocList():
            mcs.newMM2Locomotive("BR 64", 6)
            print("Added BR 64")

        # Retrieve locomotive list
        locomotives = mcs.getLocList()
        print("Available Locomotives:", locomotives)
        
        # Get a specific locomotive
        my_loc = mcs.getLocByName("BR 64")
        if my_loc:
            # Set the speed
            my_loc.setSpeed(50)
            print("Speed set to 50%")

            # Toggle a function
            my_loc.toggleF(3, 1000)
            print("Function F3 toggled for 1 second")

            # Stop the locomotive
            my_loc.stop()
            print("Locomotive stopped")

        # Work with accessories
        accessory_state = mcs.getAccessoryState(3, MarklinAccessory.accessoryDecoderType.MM2)
        mcs.setAccessoryState(3, MarklinAccessory.accessoryDecoderType.MM2, not accessory_state)
        print("Accessory state toggled")
        
        # Perform other commands as needed, using the full TrainControl API
        
        # We need to manually invoke this to save the locomotive DB and state
        mcs.saveState(False)
        exit()

    except Exception as e:
        print("Error occurred:", str(e))

# Run the function
execute_code()
