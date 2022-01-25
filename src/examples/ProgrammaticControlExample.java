package examples;

import base.Locomotive;
import java.util.List;
import marklin.MarklinAccessory;
import static marklin.MarklinControlStation.init;
import marklin.MarklinControlStation;
import marklin.MarklinRoute;
import util.Exec;

/**
 * This class contains example code showing how to control your layout programmatically
 * @author Adam
 */
public class ProgrammaticControlExample 
{
        private static void execCode(MarklinControlStation data)
        {
            data.log("Custom code running...");
                        
            new Exec<MarklinControlStation>(data)
            {
                @Override
                public void run()
                {
                    //
                    // Locomotives
                    //
                    
                    // Retrieve a locomotive that already exists in the CS2/CS3
                    Locomotive myLoc = data.getLocByName("BR 64");
                    
                    // Turn on a function (F4)
                    myLoc.setF(4, true);
                    
                    // Fire pulse function (F3 for 1 second)
                    myLoc.toggleF(3, 1000);
                    
                    // Turn lights on (same as .setF(0, true)
                    myLoc.lightsOn();
                    
                    // Sets a speed (0-100%)
                    myLoc.setSpeed(0);
                    myLoc.stop();
                    
                    // Sets the direction
                    myLoc.setDirection(Locomotive.locDirection.DIR_FORWARD);
                    myLoc.setDirection(Locomotive.locDirection.DIR_BACKWARD);
                    myLoc.switchDirection();
                    
                    // Does something after a 1 second delay
                    myLoc.delay(1000).stop();
                    
                    // Define a MM2 locomotive with a specific address, even if it does not yet exist in the CS2/CS3
                    // This approach is generally not needed unless you absolutely don't want to interact with the CS2/CS3 :) 
                    Locomotive myMM2Loc = data.newMM2Locomotive("BR 86", 60);
                    
                    //
                    // Accessories
                    //
                    
                    // Retrieve a signal by its MM2/DCC address (Note: everything but switches will always start with "Signal")
                    MarklinAccessory mySignal = data.getAccessoryByName("Signal 1");
                    
                    // These two are equivalent
                    mySignal.red();
                    mySignal.setSwitched(true);

                    // These two are equivalent
                    mySignal.green();
                    mySignal.setSwitched(false);
                    
                    // = Set signal 1 to green via the Locomotive API
                    // Why would you do this?  See the "Chaining Commands" section
                    myLoc.setAccessoryState(1, false);
                    
                    
                    // Retrieve a signal by its MM2/DCC address
                    MarklinAccessory mySwitch = data.getAccessoryByName("Switch 2");
                    
                    // These two are equivalent
                    mySwitch.straight();
                    mySignal.setSwitched(false);
                    
                    // These two are equivalent
                    mySwitch.turn();
                    mySwitch.setSwitched(true);
                    
                    // = Set switch 2 to turnout via the Locomotive API
                    myLoc.setAccessoryState(2, true);
                    
                    
                    // 
                    // Feedback
                    //
                    
                    // Query the status of S88 feedback with address 1
                    boolean feedbackStatus = data.getFeedbackState("1");
                    
                    // = Query the status of S88 feedback via the Locomotive API
                    feedbackStatus = myLoc.isFeedbackSet("1");
                    
                    if (feedbackStatus)
                    {
                        System.out.println("Feedback 1 shows occupied");
                    }
                    
                    // 
                    // Routes
                    //
                    
                    // Lists all routes stored in the CS2/CS3
                    List<String> routes = data.getRouteList();
                    
                    // Execute a route
                    data.execRoute("SomeRoute");
                    
                    // = Execute a route via the Locomotive API
                    myLoc.execRoute("SomeRoute");
                      
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
                                .setAccessoryState(1, true)
                                .setAccessoryState(2, false)
                                // Turnout
                                .setAccessoryState(10, true)
                                // Wait 2-20 seconds
                                .delay(2,20)
                                // Turn on locomotive sound and lights
                                .setF(3, true)
                                .lightsOn()
                                // Start rolling
                                .setSpeed(40)
                                .waitForOccupiedFeedback("3")
                                // Signal should now be red
                                .setAccessoryState(1, false)
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
                                .setAccessoryState(1, false)
                                .setAccessoryState(2, true)
                                // Go straight
                                .setAccessoryState(10, false)
                                // Wait 2-20 seconds
                                .delay(2,20)
                                // Turn on locomotive sound and lights
                                .setF(3, true)
                                .lightsOn()
                                // Start rolling
                                .setSpeed(40)
                                .waitForOccupiedFeedback("3")
                                // Signal should now be red
                                .setAccessoryState(2, false)
                                // Slow down
                                .setSpeed(20)
                                // Stop the locomotive when it arrives at the station
                                // (S88 address 1)
                                .waitForOccupiedFeedback("2")
                                .setSpeed(0);
                    }
                }
             }.start();
        }
    
	public static void main(String[] args)
	{            
	    // Initialize the central station
            try
            {
                // Initialize with no UI
                MarklinControlStation model = init(null, false, false, false, true); 
                
                // Or, initialize with a predetermined IP
                // MarklinControlStation model = init("192.168.1.10", false, false, false, true); 
           
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