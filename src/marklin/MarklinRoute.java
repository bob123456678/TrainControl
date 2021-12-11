package marklin;

import base.Route;
import java.util.Map;

/**
 * Simple route representation
 * 
 * @author Adam
 */
public class MarklinRoute extends Route 
    implements java.io.Serializable
{
    // Control station reference
    private final MarklinControlStation network;
    
    // Extra delay between route commands
    // TODO - make this configurable in the future
    private static final int EXTRA_SLEEP_MS = 150;
    
    /**
     * Simple constructor
     * @param network
     * @param name 
     */
    public MarklinRoute(MarklinControlStation network, String name)
    { 
        super(name);
        
        this.network = network;    
    }
    
    /**
     * Complete constructor
     * @param network
     * @param name 
     * @param route 
     */
    public MarklinRoute(MarklinControlStation network, String name, Map<Integer, Boolean> route)
    { 
        super(name, route);
        
        this.network = network;    
    }
    
    /**
     * Executes the route
     */
    public void execRoute()
    {
        for (Integer id : this.route.keySet())
        {
            Boolean state = this.route.get(id);
            
            this.network.setAccessoryState(id, state);
            
            try
            {
                Thread.sleep(MarklinControlStation.SLEEP_INTERVAL + EXTRA_SLEEP_MS);
            } catch (InterruptedException ex)
            {
                
            }
        }
    }
}
