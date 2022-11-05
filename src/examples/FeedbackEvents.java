package examples;

import base.Locomotive;
import static marklin.MarklinControlStation.init;
import javax.swing.JOptionPane;
import marklin.MarklinControlStation;

/**
 * An example of how to set feedback based conditions
 * @author Adam
 */
public class FeedbackEvents 
{
        private static void execCode(MarklinControlStation data) throws Exception
        {
            data.log("Custom code running...");
           
            // Locs currently have all the utility functions, so pick a random loc
            Locomotive loc = data.getLocByName(data.getLocList().get(0));
            
            // Entering bottom main station
            new Thread(() -> {
                while (loc.waitForOccupiedThenClear("1030") != null)
                {
                    data.log("Entering bottom main");
                    
                    if (data.getFeedbackState("9"))
                    {
                        data.getAccessoryByName("Signal 86").red();
                        data.getAccessoryByName("Signal 87").green();
                        
                        if (data.getFeedbackState("10"))
                        {
                            data.getAccessoryByName("Switch 1").straight();
                            data.getAccessoryByName("Switch 60").turn();
                        }
                        else
                        {
                            data.getAccessoryByName("Switch 1").straight();
                            data.getAccessoryByName("Switch 60").straight();
                        }
                    }
                    else
                    {
                        data.getAccessoryByName("Signal 87").red();
                        data.getAccessoryByName("Signal 86").green();
                        data.getAccessoryByName("Switch 1").turn();
                    }
                }    
            }).start();
            
            // Entering bottom secondary station
            new Thread(() -> {
                while (loc.waitForOccupiedThenClear("2015") != null)
                {
                    data.log("Entering bottom secondary");

                    if (data.getFeedbackState("1009"))
                    {
                        data.getAccessoryByName("Switch 12").turn();
                        data.getAccessoryByName("Switch 52").turn();
                        data.getAccessoryByName("Signal 39").red();
                    }
                    else
                    {
                        data.getAccessoryByName("Switch 12").straight();
                        data.getAccessoryByName("Signal 39").green();
                    }
                }    
            }).start();
            
            // Entering top station
            new Thread(() -> {
                while (loc.waitForOccupiedThenClear("2013") != null)
                {
                    data.log("Entering top");

                    if (data.getFeedbackState("1022") || data.getFeedbackState("1012"))
                    {
                        data.getAccessoryByName("Switch 9").straight();
                    }
                    else
                    {
                        data.getAccessoryByName("Switch 9").turn();
                    }
                }    
            }).start();        
        }
    
	/**
	 * Main method initialized control station and executes custom code
	 * 
         * @param args
	 */
	public static void main(String[] args)
	{            
	    // Initialize the central station
            try
            {
                // Initialize and prompt for IP (saved for subsequent sessions)
                MarklinControlStation model = init(); 
                
                // Or, initialized with a predetermined IP
                // MarklinControlStation model = init("192.168.1.10", false, false, false, true); 
                
                // Run custom code
                execCode(model);
	    } 
            catch (Exception e)
	    {
                JOptionPane.showMessageDialog(null, "Error ocurred: " + e.getMessage());
	    	e.printStackTrace();
                System.exit(0);
	    } 
	}
}