package marklin;

import base.Route;
import java.util.Map;

/**
 * Simple route reprenentation
 * 
 * @author Adam
 */
public class MarklinRoute extends Route 
    implements java.io.Serializable
{
    // Control station reference
    private MarklinControlStation network;
    
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
                Thread.sleep(this.network.SLEEP_INTERVAL);
            } catch (InterruptedException ex)
            {
                
            }
        }
    }
}
