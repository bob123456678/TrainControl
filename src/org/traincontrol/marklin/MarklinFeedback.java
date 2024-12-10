package org.traincontrol.marklin;

import org.traincontrol.base.Feedback;
import org.traincontrol.base.RemoteDevice;
import org.traincontrol.gui.LayoutLabel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.traincontrol.marklin.udp.CS2Message;

/**
 * Marklin S88 feedback
 * @author Adam
 */
public class MarklinFeedback extends Feedback 
    implements RemoteDevice<MarklinFeedback, CS2Message>, java.io.Serializable
{
    // Feedback identifier
    private final int UID;
        
    // Control station reference
    private final MarklinControlStation network;
    
    // Gui reference
    private final Set<LayoutLabel> tiles;
        
    public MarklinFeedback(MarklinControlStation network, int id, CS2Message m)
    { 
        super(Integer.toString(id));
        
        this.network = network;
        this.UID = id;
        this.tiles = new HashSet<>();

        if (m != null)
        {
            this.parseMessage(m);
        }
    }

    /**
     * Adds a UI tile to be updated whenever a CS2 event fires
     * @param l 
     */
    public void addTile(LayoutLabel l)
    {   
        this.tiles.add(l);
    }
    
    /**
     * Refreshes tile images on all tiles in the list
     * Deletes tiles that are no longer visible (e.g., from closed windows)
     */
    public void updateTiles()
    {        
        new Thread(() ->
        {
            Iterator<LayoutLabel> i = this.tiles.iterator();
            while (i.hasNext())
            {
                LayoutLabel nxtTile = i.next();
                nxtTile.updateImage();

                if (!nxtTile.isParentVisible())
                {
                    i.remove();
                }
            }
        }).start();
    }
        
    @Override
    synchronized public final void parseMessage(CS2Message m)
    {
        if (m.getCommand() == CS2Message.CMD_ACC_SENSOR)
        {
            if (m.getLength() == 8)
            {
                int id = m.extractShortUID();
                
                int state = CS2Message.mergeBytes(
                    new byte[] {m.getData()[5]}
                );
                                   
                if (id == this.UID && this.readyForUpdate(System.currentTimeMillis()))
                {
                    this._setState((state == 1));
                                        
                    this.updateTiles();
                    
                    this.network.log("Feedback " + this.getName() + " to " + (state == 1 ? "Set" : "Not set"));
                }                
            }  
        }
    }
    
    /**
     * Sets the feedback state
     * @param val 
     */
    public void setState(boolean val)
    {
        this._setState(val);
        
        if (this.network.isDebug())
        {
            this.network.log("Feedback " + name + " manually to " + (val ? "Set" : "Not set"));
        }
        
        this.updateTiles();
    }
    
    /**
     * Returns the feedback identifier
     * @return 
     */
    public int getUID()
    {
        return this.UID;
    }
    
    @Override
    public MarklinFeedback syncFromState()
    {
       // Not supported by the protocol
       return this;
    }

    @Override
    public MarklinFeedback syncFromNetwork()
    {
        // Not supported by the protocol
        return this;
    }
}
