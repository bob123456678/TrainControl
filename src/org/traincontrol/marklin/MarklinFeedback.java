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

    // Whether the state was last changed by a correction, not by an edge
    private volatile boolean corrected;
        
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
                    // Until the connect's confirmation is in, a change only puts the state right (D133); during a
                    // later sync's pass only an answer, its old state equal to its new, does.  A pass under way when
                    // the frame was heard counts, as its last answer ends it before this runs (OB-490)
                    boolean asking = m.heardAsking() || this.network.isAskingSensors();
                    boolean answer = m.getData()[4] == m.getData()[5];
                    boolean confirming = m.heardConfirming() || this.network.isConfirming();

                    this.changeTo(state == 1, confirming || (asking && answer));
                                        
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
        this.changeTo(val, false);
        
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
    
    /** Sets the state, noting whether a change of it is a correction rather than an edge, before waiters wake. */
    private void changeTo(boolean set, boolean correction)
    {
        if (set != this.isSet()) this.corrected = correction;

        this._setState(set);
    }

    /** Whether the state was last changed by a correction (OB-418), which no route takes for its trigger. */
    public boolean isCorrected()
    {
        return this.corrected;
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
