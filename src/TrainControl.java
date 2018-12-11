import gui.TrainControlUI;
import java.io.FileReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.Scanner;
import javax.swing.JOptionPane;
import marklin.MarklinControlStation;
import marklin.file.CS2File;
import marklin.udp.NetworkProxy;
import util.Exec;

public class TrainControl {
    
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
            
            // Set to true to test application without a CS2
            boolean simulate = false;
            
            try 
            {
              String initIP = null;
              boolean skipFile = false;
              
              if (args.length >= 1)
              {
                  initIP = args[0];
              }
              
              if (simulate)
              {
                  initIP = null;
                  skipFile = true;
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
                        
                        if (initIP == null && !simulate)
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
                            
                            if (simulate)
                            {
                                break;
                            }
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