package marklin;

import base.Feedback;
import base.RemoteDevice;
import gui.LayoutLabel;
import marklin.udp.CS2Message;

/**
 * Marklin S88 feedback
 * @author Adam
 */
public class MarklinFeedback extends Feedback 
    implements RemoteDevice<MarklinFeedback, CS2Message>, java.io.Serializable
{
    // Feedback identifier
    private int UID;
        
    // Control station reference
    private MarklinControlStation network;
    
    // Gui reference
    private LayoutLabel tile;
        
    public MarklinFeedback(MarklinControlStation network, int id, CS2Message m)
    { 
        super(Integer.toString(id));
        
        this.network=network;
        this.UID = id;
        
        if (m != null)
        {
            this.parseMessage(m);
        }
    }
    
    public void setTile(LayoutLabel l)
    {
        this.tile = l;
    }
        
    @Override
    public final void parseMessage(CS2Message m)
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
                    this._setState(state == 1 ? true : false);
                    
                    this.network.log("Feedback " + this.getName() + " to " + (state == 1 ? "Set" : "Not set"));
                    
                    if (this.tile != null)
                    {
                        this.tile.updateImage();
                    }
                }                
            }  
        }
    }
    
    public void setState(boolean val)
    {
        this._setState(val);
        
        if (this.tile != null)
        {
            this.tile.updateImage();
        }
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
