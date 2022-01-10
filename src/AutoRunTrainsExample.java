import base.Locomotive;
import gui.TrainControlUI;
import java.io.FileReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import layout.Edge;
import layout.Layout;
import layout.Point;
import marklin.MarklinControlStation;
import marklin.file.CS2File;
import marklin.udp.NetworkProxy;
import util.Exec;

/**
 * An example of how to set up and execute automatic layout control
 * @author Adam
 */
public class AutoRunTrainsExample 
{
        private static void execCode(MarklinControlStation data) throws Exception
        {
            data.log("Custom code running...");
            
            // Initialize the graph
            Layout layout = new Layout(data);
            
            // Define our stations & current location of locomotives
            
            layout.createPoint("ParkingFront", true, "1002");
            layout.createPoint("ParkingBack", true, "2005");

            
            layout.createPoint("BottomInner", true, "1011");
            layout.createPoint("BottomInnerOtherside", false, "2011");
            layout.createPoint("BottomCrossover", false, "14");
            //layout.createPoint("PreStation", true, "8", null);

            layout.createPoint("RampUp", false, "1027");
            layout.createPoint("RampDown", false, "2012");
            layout.createPoint("Tunnel", false, "1032");

            layout.createPoint("BottomSecondary", true, "1009");
            layout.createPoint("BottomMainA", true, "9");
            layout.createPoint("BottomMainB", true, "10");

            layout.createPoint("TopMainR1", true, "1022");
            layout.createPoint("TopMainR2", true, "1023");
            layout.createPoint("TopMainR1Pre", true, "1012");
            layout.createPoint("TopMainR2Pre", true, "1013");
            
            layout.createEdge("BottomInnerOtherside", "BottomCrossover", (control) -> {control.getAccessoryByName("Switch 52").turn(); return true;});

            layout.createEdge("BottomCrossover", "BottomSecondary", null);

            layout.createEdge("RampUp", "TopMainR1Pre", (control) -> {control.getAccessoryByName("Switch 9").turn(); return true;});
            layout.createEdge("RampUp", "TopMainR2Pre", (control) -> {control.getAccessoryByName("Switch 9").straight(); return true;});

            layout.createEdge("TopMainR1Pre", "TopMainR1", (control) -> {control.getAccessoryByName("Switch 71").straight(); control.getAccessoryByName("Signal 63").red(); return true;});
            layout.createEdge("TopMainR2Pre", "TopMainR2", (control) -> {control.getAccessoryByName("Signal 64").red(); return true;});

            layout.createEdge("TopMainR1", "RampDown", (control) -> {control.getAccessoryByName("Signal 63").green(); return true;});
            layout.createEdge("TopMainR2", "RampDown", (control) -> {control.getAccessoryByName("Signal 64").green(); return true;});

            layout.createEdge("RampDown", "BottomSecondary", (control) -> {control.getAccessoryByName("Switch 12").straight(); return true;});
            layout.createEdge("RampDown", "BottomInner", (control) -> {control.getAccessoryByName("Switch 12").turn(); return true;});

            
            // Define our edges (stations/points conncted to each other, and switch/signal commands needed to make those connections)
            layout.createEdge("BottomInner", "BottomInnerOtherside", (control) -> {control.getAccessoryByName("Switch 52").setSwitched(false); return true;});
            layout.createEdge("BottomInnerOtherside", "BottomInner", (control) -> {control.getAccessoryByName("Switch 52").setSwitched(false); return true;});

            layout.createEdge("BottomSecondary", "Tunnel", (control) -> {control.getAccessoryByName("Switch 58").setSwitched(false); control.getAccessoryByName("Signal 62").green(); return true;}   );

            layout.createEdge("Tunnel", "BottomMainA", (control) -> {control.getAccessoryByName("Switch 1").turn(); control.getAccessoryByName("Signal 40").red(); return true;}   );
            layout.createEdge("Tunnel", "BottomMainB", (control) -> {control.getAccessoryByName("Switch 1").straight(); control.getAccessoryByName("Switch 60").straight(); control.getAccessoryByName("Signal 38").red(); return true;}   );
            
            layout.createEdge("BottomMainA", "RampUp", (control) -> {control.getAccessoryByName("Signal 40").green(); return true;});
            layout.createEdge("BottomMainB", "RampUp", (control) -> {control.getAccessoryByName("Signal 38").green(); return true;});

            layout.createEdge("ParkingFront", "ParkingBack", (control) -> {control.getAccessoryByName("Switch 25").straight(); return true;});
            layout.createEdge("ParkingBack", "ParkingFront", (control) -> {control.getAccessoryByName("Switch 10").straight(); control.getAccessoryByName("Switch 32").straight(); return true;});

            
            layout.getPoint("BottomSecondary").setLocomotive(data.getLocByName("SNCF 422365"));
            layout.getPoint("TopMainR2").setLocomotive(data.getLocByName("140 024-1 DB AG"));
            layout.getPoint("ParkingFront").setLocomotive(data.getLocByName("BR182 005-9"));


            //List<Edge> path = layout.bfs(layout.getPoint("Station"), layout.getPoint("OtherSide"));
            //layout.executePath(path, path.get(0).getStart().getCurrentLocomotive(), 45);
            
            // Automatically pick a valid path and continuously execute
            for (int i = 0; i < 3; i++)
            {
                new Thread(() -> {
                    while (true)
                    {
                        layout.pickAndExecutePath(35);
                        try {
                            Thread.sleep((long) (Math.random() * 5000 + 1000));
                        } catch (InterruptedException ex) {
                        }
                    }
                }).start();
            }
        }
    
	/**
	 * Main method, parses command line arguments and initializes the GUI /
	 * client
	 * 
	 * Ensures that informative error messages are printed in the event that an
	 * error occurs
	 * 
	 * @param String [] args, command line arguments
	 */
	public static void main(String[] args)
	{
            String IPfile = "ip.txt";
            
            try 
            {
              String initIP = null;
              boolean skipFile = false;
              
              if (args.length >= 1)
              {
                  initIP = args[0];
              }
              
              while (true)
              {
                  try
                  {
                        if (initIP == null && !skipFile)
                        {
                            try 
                            {
                                Scanner in = new Scanner(new FileReader(IPfile));
                                initIP = in.nextLine();
                            }
                            catch (Exception e)
                            {
                                
                            }
                        }
                        
                        if (initIP == null)
                        {
                            initIP = JOptionPane.showInputDialog("Enter CS2 IP Address: ");
                            
                            if (initIP == null)
                            {
                                System.out.println("No IP entered - shutting down.");
                                System.exit(1);
                            }
                        }
                                            
                        if (!CS2File.ping(initIP))
                        {
                            JOptionPane.showMessageDialog(null, "No response from " + initIP);

                            initIP = null;
                            skipFile = true;
                        }
                        else
                        {
                            try (PrintWriter writer = new PrintWriter(IPfile, "UTF-8"))
                            {
                                writer.println(initIP);
                            }
                            
                            break;
                        }
                  }
                  catch (Exception e)
                  {
                      System.out.println("Invalid IP Specified");
                      initIP = null;
                  }
              }
                
	      // Delegate the hard part
	      NetworkProxy proxy = new NetworkProxy(InetAddress.getByName(initIP));
	      
              // User interface
	      TrainControlUI ui = new TrainControlUI();
              
	      // Initialize the central station
	      MarklinControlStation model = 
                new MarklinControlStation(proxy, ui, 0);
              
              // Enables debug mode
              if (args.length >= 2)
              {
                  model.debug(true);
              }
	      	      
              // Set model
	      ui.setViewListener(model);
	      
	      // Start execution
	      proxy.setModel(model);   
	      
              execCode(model);
              
	    } 
            catch (Exception e)
	    {
                JOptionPane.showMessageDialog(null, "Error ocurred: " + e.getMessage());
	    	e.printStackTrace();
                System.exit(0);
	    }
            
	}

	/**
	 * Prints an error message, then exits
	 * 
	 * @param error, the error message
	 */
	public static void die(String error)
	{
            System.err.println(error);
            System.exit(1);
	}
}