import java.awt.GraphicsEnvironment;
import javax.swing.JOptionPane;
import static org.traincontrol.marklin.MarklinControlStation.init;

public class TrainControl
{        
    /**
     * Main method, parses command line arguments and initializes the GUI /
     * client
     * 
     * Ensures that informative error messages are printed in the event that an
     * error occurs
     * 
     * Usage: TrainControl.java [IP [debug [simulate connection]]]
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
            
            if (GraphicsEnvironment.isHeadless())
            {
                throw new Exception("This program cannot be run standalone in headless mode.  See the readme for programmatic examples.");
            }

            init(initIP, simulate, true, false, debug);
        } 
        catch (Exception e)
        {
            System.out.println("Error occurred: " + e.getMessage());

            if (!GraphicsEnvironment.isHeadless())
            {
                // The one failure that has a plain-English cause, said in plain English.
                //
                // "Address already in use: Cannot bind" is what a second copy of TrainControl looks
                // like: the first one holds the port the Central Station talks to.  Told that way it is
                // one sentence and the user closes the other window; told as the exception, it reads
                // like a fault in the program with nothing to be done about it.
                //
                // The exception still goes to the console and the stack trace still prints, so nothing
                // is lost for anybody who needs it.
                String said = isPortInUse(e)
                    ? org.traincontrol.util.I18n.t("error.alreadyRunning")
                    : org.traincontrol.util.I18n.f("error.startupFailed", e.getMessage());

                JOptionPane.showMessageDialog(null, said);
            }

            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Whether this failure is the port already being held.
     *
     * By type where the type says so, and by message where it does not: the bind happens inside the
     * network proxy's constructor and arrives here wrapped, so the BindException is sometimes the cause
     * rather than the exception itself, and sometimes only a sentence.
     *
     * @param failure what went wrong on startup
     * @return true when a second copy of TrainControl is the likely reason
     */
    private static boolean isPortInUse(Throwable failure)
    {
        for (Throwable at = failure; at != null; at = at.getCause())
        {
            if (at instanceof java.net.BindException) return true;

            String said = at.getMessage();

            if (said != null && said.toLowerCase().contains("address already in use")) return true;

            if (at.getCause() == at) break;
        }

        return false;
    }
}