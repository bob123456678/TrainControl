package org.traincontrol.marklin;

import org.traincontrol.base.Feedback;
import org.traincontrol.base.RemoteDevice;
import org.traincontrol.gui.LayoutLabel;
import java.util.Iterator;
import java.util.Set;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.I18n;

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
    // ConcurrentHashMap-backed set: addTile is called from the EDT as track diagram windows open,
    // while updateTiles iterates and prunes from a Central Station message thread.  A plain HashSet
    // threw ConcurrentModificationException there, silently killing the thread mid-refresh.
    private final Set<LayoutLabel> tiles;
        
    public MarklinFeedback(MarklinControlStation network, int id, CS2Message m)
    { 
        super(Integer.toString(id));
        
        this.network = network;
        this.UID = id;
        this.tiles = java.util.concurrent.ConcurrentHashMap.newKeySet();

        if (m != null)
        {
            this.parseMessage(m);
        }
    }

    /**
     * Adds a UI tile to be updated whenever a CS2 event fires
     * @param l 
     */
    @Override
    public void addTile(LayoutLabel l)
    {   
        // The labels this one replaces go now.  See LayoutLabel.forgetReplaced: nothing else can drop
        // them on the main window, so without this every rebuilt page stayed registered for ever.
        LayoutLabel.forgetReplaced(this.tiles, l);

        this.tiles.add(l);
    }
    
    /**
     * Refreshes tile images on all tiles in the list
     * Deletes tiles that are no longer visible (e.g., from closed windows)
     */
    public void updateTiles()
    {
        // No thread: updateImage already marshals its Swing work to the EDT, so this is cheap and
        // non-blocking.  Spawning one per call - potentially per feedback event - only widened the
        // window for concurrent iteration, which is what made the plain HashSet blow up.  The other
        // device classes have always done this inline.
        Iterator<LayoutLabel> i = this.tiles.iterator();

        while (i.hasNext())
        {
            LayoutLabel nxtTile = i.next();
            nxtTile.updateImage(false);

            if (!nxtTile.isParentVisible())
            {
                i.remove();
            }
        }
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
                    
                    this.network.logf(
                        "acc.feedbackState",
                        this.getName(),
                        "",
                        (state == 1 ? I18n.t("acc.stateSet") : I18n.t("acc.stateNotSet"))
                    );
                }                
            }  
        }
    }
    
    /**
     * Sets the feedback state
     * @param val 
     */
    @Override
    public void setState(boolean val)
    {
        this._setState(val);
        
        if (this.network.isDebug())
        {
            this.network.logf(
                "acc.feedbackState",
                name,
                " " + I18n.t("acc.manually"),
                (val ? I18n.t("acc.stateSet") : I18n.t("acc.stateNotSet"))
            );
            
            // If we want to capture route commands in the future, we could call a method in the model here
        }
        
        this.updateTiles();
    }
    
    /**
     * Returns the feedback identifier
     * @return 
     */
    @Override
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
