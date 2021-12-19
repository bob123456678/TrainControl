import gui.TrainControlUI;
import java.net.InetAddress;
import javax.swing.JOptionPane;
import marklin.MarklinControlStation;
import marklin.file.CS2File;
import marklin.udp.NetworkProxy;

public class TrainControl {
    
    /**
     * Main method, parses command line arguments and initializes the GUI /
     * client
     * 
     * Ensures that informative error messages are printed in the event that an
     * error occurs
     * 
     * Usage: TrainControl.java [IP] [debug [simulate connection]]
     * 
     * @param args, command line arguments
     */
    public static void main(String[] args)
    {            
        // Set to true to test application without a CS2
        boolean simulate = (args.length >= 3);

        try 
        {
            // User interface
            TrainControlUI ui = new TrainControlUI();
            String initIP = args.length >= 1 ? args[0] : ui.getPrefs().get(TrainControlUI.IP_PREF, null);
            
            if (!simulate)
            {
                while (true)
                {
                    try
                    {
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
                        }
                        else
                        {
                            ui.getPrefs().put(TrainControlUI.IP_PREF, initIP);
                            break;
                        }
                    }
                    catch (Exception e)
                    {
                        System.out.println("Invalid IP Specified");
                    }
                    
                    initIP = null;
                }
            }
            else
            {
                initIP = null;
            }

            // Delegate the hard part
            NetworkProxy proxy = new NetworkProxy(InetAddress.getByName(initIP));

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
            
            // Connection failed - ask for IP on next run
            if (!model.getNetworkCommState())
            {
                ui.getPrefs().remove(TrainControlUI.IP_PREF);
            }
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