package gui;

/**
 * This dummy UI class allows us to use the program via command line 
 */
public class DummyUI implements model.View
{  
    @Override
    public void repaintLoc()
    {
        
    }

    @Override
    public void repaintSwitches()
    {

    }
    
    @Override
    public void repaintLayout()
    {

    }

    @Override
    public void updatePowerState()
    {

    }
    
    @Override
    public void log(String message)
    {
        System.out.println(message);
    }
    
    @Override
    public void updateLatency(double latency)
    {
        
    }
}
