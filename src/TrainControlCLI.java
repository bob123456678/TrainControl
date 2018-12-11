import base.Locomotive;
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

public class TrainControlCLI 
{
        private static void execCode(MarklinControlStation data)
        {
            data.log("Custom code running...");
            
            // data.newMFXLocomotive("BR 86", 0x32);
            // data.newMM2Locomotive("BR 10",10);
            
            new Exec<MarklinControlStation>(data)
            {
                @Override
                public void run() {

                    while (true)
                    {
                        this.data.getLocByName("BR 18 112")
                            .delay(2, 60)
                            .toggleF(3)
                            .delay(2, 60);
                    }
                }
             }.start();   
            
            new Exec<MarklinControlStation>(data)
            {
                @Override
                public void run() {

                    while (true)
                    {
                        this.data.getLocByName("BR 39 083")
                            .delay(2, 60)
                            .toggleF(3)
                            .delay(2, 60);
                    }
                }
             }.start();  
            
            new Exec<MarklinControlStation>(data)
            {
                @Override
                public void run() {

                    while (true)
                    {
                        this.data.getLocByName("BR 64 Ole")
                            .delay(2, 60)
                            .toggleF(3)
                            .delay(2, 60);
                    }
                }
             }.start(); 
            
            ///////////////////
            
            new Exec<MarklinControlStation>(data)
            {
                @Override
                public void run() {
                    
                    while (true)
                    {
                        this.data.getLocByName("BR 64 Ole")
                                
                                
                                .setAccessoryState(41, true)
                                .setAccessoryState(15, false)
                                .delay(2,20)
                                .setF(3, true)
                                .lightsOn()
                                .setSpeed(40)
                                
                                .waitForOccupiedFeedback("40")
                                .setSpeed(0)
                                .waitForOccupiedFeedback("36")
                                
                                .setAccessoryState(41, false)
                                .setAccessoryState(15, true)
                                .delay(2, 20)
                                .setSpeed(40)
                                
                                .waitForOccupiedFeedback("24")
                                .setSpeed(0)
                                .setF(3, false)
                                .lightsOff()
                                .delay(2, 20)
                                .waitForOccupiedFeedback("16")
                                ;
                    }
                }
             }.start();   
                        
            new Exec<MarklinControlStation>(data)
            {
                @Override
                public void run() {

                    
                    while (true)
                    {
                        this.data.getLocByName("BR 80 Ole")
                                
                                
                                .setAccessoryState(74, true)
                                .setAccessoryState(72, false)
                                 
                                .delay(2, 20)
                                .setF(2, true)
                                .lightsOn()
                                .setSpeed(50)

                                .waitForOccupiedFeedback("36")
                                .setSpeed(0)
                                .waitForOccupiedFeedback("40")
                                
                                
                                .setAccessoryState(72, true)
                                .setAccessoryState(74, false)
                                
                                .delay(2, 20)
                                .setSpeed(50)
                                
                                .waitForOccupiedFeedback("16")
                                .setSpeed(0)
                                .setF(2, false)
                                .lightsOff()
                                .waitForOccupiedFeedback("24")
                                ;
                    }
                }
             }.start(); 
            
            
            ////////////////
            
            new Exec<MarklinControlStation>(data)
            {
                @Override
                public void run() {

                    while (true)
                    {
                        this.data.getLocByName("BR 39 083")
                                
                                
                                .setAccessoryState(76, true)
                                .setAccessoryState(11, false)
                                .delay(2,20)
                                .setF(2, true)
                                .lightsOn()
                                .setSpeed(50)
                                
                                .waitForOccupiedFeedback("35")
                                .setSpeed(0)
                                .waitForOccupiedFeedback("41")
                                .setAccessoryState(76, false)
                                .setAccessoryState(11, true)
                                .delay(2, 30)
                                
                                
                                .setSpeed(50)
                                
                                .waitForOccupiedFeedback("14")
                                .setSpeed(0)
                                .setF(2, false)
                                .lightsOff()
                                .waitForOccupiedFeedback("23");
                    }
                }
             }.start();   
            
            data.getLocByName("BR 39 083").delay(1000);
            
            new Exec<MarklinControlStation>(data)
            {
                @Override
                public void run() {

                    this.data.getLocByName("BR 18 112").delay(2,20);
                    
                    while (true)
                    {
                        this.data.getLocByName("BR 18 112")   
                                
                            
                            
                            .setAccessoryState(43, true)
                            .setAccessoryState(70, false)
                            .delay(2, 20)
                            .lightsOn()
                            .setF(2, true)
                            .setSpeed(50)
                            .waitForOccupiedFeedback("41")
                            .setSpeed(0)
                            
                            .waitForOccupiedFeedback("35")
                                
                            .setAccessoryState(70, true)
                            .setAccessoryState(43, false)
                            .delay(2, 30)
                            .setSpeed(50)
                            .waitForOccupiedFeedback("23")
                            .setSpeed(0)
                            .setF(2, false)
                            .lightsOff()
                            
                            .waitForOccupiedFeedback("14")
                            ;
                    }
                }
             }.start();
             
            new Exec<MarklinControlStation>(data)
            {
                @Override
                public void run() {

                                                
                    
                    //this.data.getLocByName("BR 86").setDirection(Locomotive.locDirection.DIR_FORWARD);
                    //this.data.getLocByName("BR 86").setDirection(Locomotive.locDirection.DIR_BACKWARD);

                    //this.data.getAccessoryByName("Switch 1").setSwitched(true);
                    
                    // .green() .red() .straight() .turn()
                    // .doSwitch() - reverse state
                    // .isRed() .isGreen() .isStraight() .isTurned()
                    
                    //this.data.getAccessoryByName("Signal 1").green();

                    //this.data.getFeedbackState("16");
                    
                    // waitForClearFeedback
                    // goForward
                    // goBackward
                    
                   // this.data.getLocByName("BR86").setSpeed(40).setF(8, true).setF(2, false).setF(0, true).setF(3, true).delay(1000).setF(3, false);
                  //   this.m.getAccessoryByName("Switch 4").delay(5000).turn().delay(1000).straight();

                    //this.data.getLocByName("BR86").delay(1000).setF(0, true).setF(2, true).setSpeed(40).delay(10000).stop();
                    //this.m.getLocByName("BR86").setF(2, true).delay(50).setF(0, true).delay(50).setSpeed(40).waitForFeedback(15).stop();

                    //        this.getLocByName("BR 80").setSpeed(10);
                    //      this.getLocByName("BR 86").setSpeed(10);

                    
                    
                   // this.data.getLocByName("BR86").setSpeed(40).setF(8, true).setF(2, false).setF(0, true).setF(3, true).delay(1000).setF(3, false);
                  //   this.m.getAccessoryByName("Switch 4").delay(5000).turn().delay(1000).straight();

                    //this.data.getLocByName("BR86").delay(1000).setF(0, true).setF(2, true).setSpeed(40).delay(10000).stop();
                    //this.m.getLocByName("BR86").setF(2, true).delay(50).setF(0, true).delay(50).setSpeed(40).waitForFeedback(15).stop();

                    //        this.getLocByName("BR 80").setSpeed(10);
                    //      this.getLocByName("BR 86").setSpeed(10);

                    // newMFXLocomotive("BR 86", 0x32);
                    //       newMM2Locomotive("BR 10",10);
                    }
             }.start(); 
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