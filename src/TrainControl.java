import javax.swing.JOptionPane;
import marklin.MarklinControlStation;
import static marklin.MarklinControlStation.init;

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
        try
        {
            boolean simulate = (args.length >= 3);
            boolean debug = (args.length >= 2);
            String initIP = args.length >= 1 ? args[0] : null;

            MarklinControlStation model = init(initIP, simulate, true, true, debug);
        } 
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(null, "Error ocurred: " + e.getMessage());
            e.printStackTrace();
            System.exit(0);
        }
    }
}