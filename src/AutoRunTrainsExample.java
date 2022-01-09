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
            layout.createPoint("Station", true, "1011", data.getLocByName("BR182 005-9"));
            layout.createPoint("OtherSide", true, "2011", null);
            layout.createPoint("PreStation", true, "8", null);

            // Define our edges (stations/points conncted to each other, and switch/signal commands needed to make those connections)
            layout.createEdge("Station", "OtherSide", (control) -> {control.getAccessoryByName("Switch 52").setSwitched(false); return true;});
            layout.createEdge("OtherSide", "PreStation", (control) -> {control.getAccessoryByName("Switch 52").setSwitched(false); return true;});
            layout.createEdge("PreStation", "Station", null);

            //List<Edge> path = layout.bfs(layout.getPoint("Station"), layout.getPoint("OtherSide"));
            //layout.executePath(path, path.get(0).getStart().getCurrentLocomotive(), 45);
            
            // Automatically pick a valid path and continuously execute
            for (int i = 0; i < 2; i++)
            {
                new Thread(() -> {
                    while (true)
                    {
                        layout.pickAndExecutePath(45);
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