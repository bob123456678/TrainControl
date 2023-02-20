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
    
    // Internal identifier used by CS2
    private final int id;
    
    // Extra delay between route commands
    // TODO - make this configurable in the future
    private static final int EXTRA_SLEEP_MS = 150;
    
    /**
     * Simple constructor
     * @param network
     * @param name 
     * @param id 
     */
    public MarklinRoute(MarklinControlStation network, String name, int id)
    { 
        super(name);
        
        this.id = id;
        this.network = network;    
    }
    
    /**
     * Complete constructor
     * @param network
     * @param name 
     * @param id 
     * @param route 
     */
    public MarklinRoute(MarklinControlStation network, String name, int id, Map<Integer, Boolean> route)
    { 
        super(name, route);
        
        this.id = id;
        this.network = network;    
    }
    
    /**
     * Returns the CS2 route ID
     * @return 
     */
    public int getId()
    {
        return this.id;
    }
    
    /**
     * Executes the route
     */
    public void execRoute()
    {
        //this.network.log("Executing route " + this.getName());
        
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
    
    @Override
    public String toString()
    {
        return super.toString() + " (ID: " + this.id + ")";
    }
}
