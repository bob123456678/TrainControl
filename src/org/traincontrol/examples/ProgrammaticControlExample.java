package org.traincontrol.examples;

import java.util.List;
import java.util.function.Consumer;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinAccessory;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinLocomotive.decoderType;
import org.traincontrol.marklin.MarklinRoute;

/**
 * This class contains example code showing how to control your layout programmatically
 * @author Adam
 */
public class ProgrammaticControlExample 
{
        private static void execCode(MarklinControlStation mcs)
        {
            mcs.log("Custom code running...");
                        
            Consumer<MarklinControlStation> func = ( (data) ->
            { 
                //
                // Central Station
                //
                
                // data.stop(); // Turns off the power
                // data.stopAllLocs(); // Issues stop command to all locomotives
                // data.allFunctionsOff(); // Turns off all active functions for all locomotives
                // data.syncWithCS2(); // Fetches the latest locomotive state from the Central Station
                
                data.go(); // Turns on the power
                data.lightsOn(data.getLocList()); // Turns on lights of all known locomotives
                
                //
                // Locomotives
                //

                // Retrieve all available locomotive names
                List<String> allLocomotiveNames = data.getLocList();
                
                // Retieve all available locomotives
                List<MarklinLocomotive> allLocomotives = data.getLocomotives();
                
                // Retrieve a locomotive that already exists in the CS2/CS3
                Locomotive myLoc = data.getLocByName("BR 64");

                // Turn on a function (F4)
                myLoc.setF(4, true);

                // Fire pulse function (F3 for 1 second)
                myLoc.toggleF(3, 1000);

                // Turn lights on (same as .setF(0, true)
                myLoc.lightsOn();

                // Sets a speed (0-100%)
                myLoc.setSpeed(50);
                
                // Slow deceleration
                myLoc.setSpeed(0);
                
                // Instant stop for DCC/MFX, slow deceleration for MM2
                myLoc.stop();
                
                // Instant stop for all locomotives, including MM2
                ((MarklinLocomotive) myLoc).instantStop();

                // Sets the direction
                myLoc.setDirection(Locomotive.locDirection.DIR_FORWARD);
                myLoc.setDirection(Locomotive.locDirection.DIR_BACKWARD);
                myLoc.switchDirection();

                // Does something after a 1 second delay
                myLoc.delay(1000).stop();
                
                // Get the locomotive's raw address and decoder type
                int address = ((MarklinLocomotive) myLoc).getAddress();
                decoderType type = ((MarklinLocomotive) myLoc).getDecoderType();
                
                // Define a MM2 locomotive with a specific address, even if it does not yet exist in the CS2/CS3
                // This approach is generally not needed unless you absolutely don't want to interact with the CS2/CS3 :) 
                Locomotive myMM2Loc = data.newMM2Locomotive("BR 86", 60);
                
                boolean f4Status = myMM2Loc.getF(4);

                //
                // Accessories by raw address
                //
                
                // These commands are useful if the accessory does not already exist on any layout
                // Retrieve the current state of an arbitrary accessory by address
                boolean state = data.getAccessoryState(3, Accessory.accessoryDecoderType.MM2);
                
                // Change the state
                data.setAccessoryState(3, Accessory.accessoryDecoderType.MM2, !state);
                
                // The accessory will now automatically be saved in the database
                MarklinAccessory mySwitch3 = data.getAccessoryByName("Switch 3");
                mySwitch3.setSwitched(true);
                
                // Manually add a new signal to the database so that it can be referenced by name
                // When creating an accessory, the MM2 address will be 1 less than the logical address
                data.newSignal(4, Accessory.accessoryDecoderType.MM2, false);
                MarklinAccessory mySignal4 = data.getAccessoryByName("Signal 4");
                
                // Send command to ensure the state is consistent
                mySignal4.green();

                // Manually add a new switch to the database so that it can be referenced by name
                data.newSwitch(5, Accessory.accessoryDecoderType.MM2, false);
                MarklinAccessory mySwitch5 = data.getAccessoryByName("Switch 5");
                
                // Send command to ensure the state is consistent
                mySwitch5.turn();
                
                // DCC addresses are now supported.  DCC must be added to the end of the item name.
                data.newSwitch(400, Accessory.accessoryDecoderType.DCC, false);
                MarklinAccessory mySwitch400 = data.getAccessoryByName("Switch 400 DCC");
                
                // Send command to ensure the state is consistent
                mySwitch400.straight();

                //
                // Accessories that are already on a layout
                //

                // Retrieve a signal by its MM2/DCC address (Note: everything but switches will always start with "Signal")
                // This assumes that the signal exists within the layout
                MarklinAccessory mySignal = data.getAccessoryByName("Signal 1");
                
                // These two are equivalent
                mySignal.red();
                mySignal.setSwitched(true);

                // These two are equivalent
                mySignal.green();
                mySignal.setSwitched(false);

                // = Set signal 1 to green via the Locomotive API
                // Why would you do this?  See the "Chaining Commands" section
                myLoc.setAccessoryState(1, Accessory.accessoryDecoderType.MM2, false);


                // Retrieve a switch by its MM2/DCC address
                // This assumes that the switch exists within the layout
                MarklinAccessory mySwitch = data.getAccessoryByName("Switch 2");

                // These two are equivalent
                mySwitch.straight();
                mySignal.setSwitched(false);

                // These two are equivalent
                mySwitch.turn();
                mySwitch.setSwitched(true);

                // = Set switch 2 to turnout via the Locomotive API
                myLoc.setAccessoryState(2, Accessory.accessoryDecoderType.MM2, true);
                
   
                // 
                // Feedback
                //

                // Query the status of S88 feedback with address 1
                boolean feedbackStatus = data.getFeedbackState("1");
                
                if (feedbackStatus)
                {
                    System.out.println("Feedback 1 shows occupied");
                    data.log("Write a message to the log in the UI.");
                } 

                // = Query the status of S88 feedback via the Locomotive API
                feedbackStatus = myLoc.isFeedbackSet("1");
                feedbackStatus = myLoc.isFeedbackSet(1); // integers are also allowed in the Locomotive API

                if (feedbackStatus)
                {
                    System.out.println("Feedback 1 shows occupied");
                }

                // 
                // Routes
                //

                // Lists all routes stored in the CS2/CS3
                List<String> routes = data.getRouteList();

                // Execute a route by name
                data.execRoute("SomeRoute");

                // = Execute a route via the Locomotive API
                myLoc.execRoute("SomeRoute");
                
                // Routes can be created via the CS2, via the TrainControl UI,
                // or by creating a new MarklinRoute object and then calling data.newRoute

                // 
                // Chaining Commands
                //

                // Commands for a locomotive of accessory can be chained
                // This is an easy way to support event-driven behavior on the layout
                // For example, this locomotive will first wait for Feedback 1 to show as occupied, and then it will toggle a function and start rolling
                myLoc.waitForOccupiedFeedback("1").toggleF(3, 1000).setSpeed(5).delay(1000).setSpeed(0);


                // This can be put in a loop if the same logic can be repeated
                // to enable "hard-coded" autonomous operation

                while (true)
                {
                    // Fetch the locomotive at Station 1
                    data.getLocByName("Loc1")
                            // Flip some signals
                            .setAccessoryState(1, Accessory.accessoryDecoderType.MM2, true)
                            .setAccessoryState(2, Accessory.accessoryDecoderType.MM2, false)
                            // Turnout
                            .setAccessoryState(10, Accessory.accessoryDecoderType.MM2, true)
                            // Wait 2-20 seconds
                            .delay(2,20)
                            // Turn on locomotive sound and lights
                            .setF(3, true)
                            .lightsOn()
                            // Start rolling
                            .setSpeed(40)
                            .waitForOccupiedFeedback("3")
                            // Signal should now be red
                            .setAccessoryState(1, Accessory.accessoryDecoderType.MM2, false)
                            // Slow down
                            .setSpeed(20)
                            // Stop the locomotive when it arrives at the station
                            // (S88 address 1)
                            .waitForOccupiedFeedback("1")
                            .setSpeed(0);

                    // Fetch the locomotive at Station 2
                    data.getLocByName("Loc2")
                            // Do not proceed unless Loc1 is at its station
                            .waitForOccupiedFeedback("1")
                            // Flip some signals
                            .setAccessoryState(1, Accessory.accessoryDecoderType.MM2, false)
                            .setAccessoryState(2, Accessory.accessoryDecoderType.MM2, true)
                            // Go straight
                            .setAccessoryState(10, Accessory.accessoryDecoderType.MM2, false)
                            // Wait 2-20 seconds
                            .delay(2,20)
                            // Turn on locomotive sound and lights
                            .setF(3, true)
                            .lightsOn()
                            // Start rolling
                            .setSpeed(40)
                            .waitForOccupiedFeedback("3")
                            // Signal should now be red
                            .setAccessoryState(2, Accessory.accessoryDecoderType.MM2, false)
                            // Slow down
                            .setSpeed(20)
                            // Stop the locomotive when it arrives at the station
                            // (S88 address 1)
                            .waitForOccupiedFeedback("2")
                            .setSpeed(0);
                }

            });
            
            func.accept(mcs);
        }
    
	public static void main(String[] args)
	{            
	    // Initialize the central station
            try
            {
                // Initialize with no UI
                MarklinControlStation model = init(null, false, false, true, false); 
                
                // Or, initialize with a predetermined IP
                // MarklinControlStation model = init("192.168.1.10", false, false, true, false); 
                
                // Or, initialize with the UI
                // MarklinControlStation model = init(null, false, true, true, false); 
           
                execCode(model);
	    } 
            catch (Exception e)
	    {
                System.out.println("Error ocurred: " + e.getMessage());
	    	e.printStackTrace();
                System.exit(0);
	    } 
	}
}