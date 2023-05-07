package examples;

import static marklin.MarklinControlStation.init;
import javax.swing.JOptionPane;
import automation.Layout;
import static base.Accessory.accessorySetting.GREEN;
import static base.Accessory.accessorySetting.RED;
import static base.Accessory.accessorySetting.STRAIGHT;
import static base.Accessory.accessorySetting.TURN;
import marklin.MarklinControlStation;


/**
 * An example of how to set up and execute automatic layout control
 * @author Adam
 */
public class FullAutonomyExample 
{
        private static void execCode(MarklinControlStation data) throws Exception
        {
            data.log("Custom code running...");
            
            // Initialize the graph
            Layout layout = new Layout(data);
            
            //
            // Define our stations and shared track segments
            //
            layout.createPoint("Station 1", true, "1");
            layout.createPoint("Station 2", true, "2");
            layout.createPoint("Pre Arrival", true, "3");
            
            // The train cannot stop here, but we create an extra point so that both routes share a common edge
            layout.createPoint("Main Track", false, null);
            
            //
            // Define our edges (stations/points conncted to each other, and switch/signal commands needed to make those connections)
            //
            
            // Note that from v1.8.0 of TrainControl,
            // we can and should use control.getAutoLayout().configure instead of control.getAccessoryByName().turn/straight/red/green 
            // This gives us additional sanity checks for conflicting commands so that a path that includes opposite settings for the same accessory would thus never be chosen
            
            // If an accessory is not yet in the database, use control.newSignal or control.newSwitch
            
            layout.createEdge("Station 2", "Main Track", (control) -> {control.getAutoLayout().configure("Signal 2", GREEN);});
            layout.createEdge("Station 1", "Main Track", (control) -> {control.getAutoLayout().configure("Signal 1", GREEN);});

            layout.createEdge("Main Track", "Pre Arrival", null);

            layout.createEdge("Pre Arrival", "Station 1", (control) -> {control.getAutoLayout().configure("Switch 10", TURN); control.getAutoLayout().configure("Signal 1", RED);});
            layout.createEdge("Pre Arrival", "Station 2", (control) -> {control.getAutoLayout().configure("Switch 10", STRAIGHT); control.getAutoLayout().configure("Signal 2", RED);});

            /*
            // We can force an edge to lock another edge that crosses over it / merges into it
            // This greatly simplifies overall layout graph modeling
            layout.getEdge("Station 1", "Main Track").addLockEdge(
                layout.getEdge("Station 2", "Main Track")
            );
            layout.getEdge("Station 1", "Main Track").addLockEdge(
                layout.getEdge("Station 2", "Main Track")
            );
            */
            
            //
            // Set callbacks for pre departure, pre arrival, and arrival
            // These can be used for setting locomotive functions, turning on lights, etc.
            //
            
            data.getLocByName("SNCF 422365").setCallback(Layout.CB_ROUTE_START, (loc) -> {loc.lightsOn().delay(1, 3);});
            data.getLocByName("140 024-1 DB AG").setCallback(Layout.CB_ROUTE_START, (loc) -> {loc.lightsOn().delay(1, 3).toggleF(11);});

            data.getLocByName("SNCF 422365").setCallback(Layout.CB_PRE_ARRIVAL, (loc) -> {loc.toggleF(3);});
            data.getLocByName("140 024-1 DB AG").setCallback(Layout.CB_PRE_ARRIVAL, (loc) -> {loc.toggleF(3);});
            
            data.getLocByName("SNCF 422365").setCallback(Layout.CB_ROUTE_END, (loc) -> {loc.delay(1, 3).lightsOff();});
            data.getLocByName("140 024-1 DB AG").setCallback(Layout.CB_ROUTE_END, (loc) -> {loc.delay(1, 3).lightsOff();});
           
            layout.getPoint("Station 1").setLocomotive(data.getLocByName("SNCF 422365"));
            layout.getPoint("Station 2").setLocomotive(data.getLocByName("140 024-1 DB AG"));
    
            //
            // Now we can run the locomotives!  This method also specifies the desired speed
            // The layout class will automatically choose and execute an available route
            //
            layout.runLocomotive(data.getLocByName("SNCF 422365"), 30);
            layout.runLocomotive(data.getLocByName("140 024-1 DB AG"), 50);
        }
    
	/**
	 * Main method initializes control station and executes custom code
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
                
                // Or, initialize with a predetermined IP
                // MarklinControlStation model = init("192.168.1.10", false, false, true); 
                
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