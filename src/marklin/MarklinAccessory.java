package marklin;

import base.Accessory;
import base.RemoteDevice;
import gui.LayoutLabel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import marklin.udp.CS2Message;
import util.Conversion;

/**
 * A marklin switch or signal
 * @author Adam
 */
public class MarklinAccessory extends Accessory
    implements java.io.Serializable, RemoteDevice<Accessory, CS2Message>
{
    // Calculated UID
    private final int UID;
    
    // Raw address
    private final int address;
    
    // Network reference
    private final MarklinControlStation network;
    
    // Gui reference
    private final Set<LayoutLabel> tiles;
    
    // Delay between threeway switches
    public static final int THREEWAY_DELAY_MS = 350;
    
    // Number of times this accessory has been actuated
    private int numActuations;
    private boolean stateAtLastActuation;
    
    /**
     * Constructor
     * @param network
     * @param address
     * @param type
     * @param name
     * @param state
     */
    public MarklinAccessory(MarklinControlStation network, int address, accessoryType type, String name, boolean state, int numActuations)
    {
        super(name, type, false);
        
        // Store raw address
        this.address = address;
        
        // Add 0x3000 for accessory UID
        this.UID = address + 0x3000;
        
        // Set network type
        this.network = network;
        
        // Set state
        this._setSwitched(state);
        
        this.tiles = new HashSet<>();
        
        this.numActuations = numActuations;
        this.stateAtLastActuation = this.switched;
    }
    
    public static int UIDfromAddress(int address)
    {
        return address + 0x3000;
    }
        
    /**
     * Adds a UI tile to be updated whenever a CS2 event fires
     * @param l 
     */
    public void addTile(LayoutLabel l)//, boolean dynamic)
    {   
        this.tiles.add(l);
    }
    
    /**
     * Gets the number of time this accessory has been switched
     * @return 
     */
    public int getNumActuations()
    {
        return numActuations;
    }
        
    /**
     * Refreshes tile images on all tiles in the list
     * Deletes tiles that are no longer visible (e.g., from closed windows)
     */
    public void updateTiles()
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
    }
    
    @Override
    synchronized public void parseMessage(CS2Message m)
    {
        // Double-check the UID just in case        
        if (m.extractUID() != UID)
        {
            return;
        }
                      
        if (m.getCommand().equals(CS2Message.CMD_ACC_SWITCH))
        {
            if (m.getLength() >= 6)
            {
                int setting = CS2Message.mergeBytes(
                    new byte[] {m.getData()[4]}
                );
                                        
                if (setting == 0)
                {
                    this._setSwitched(true);
                }
                else if (setting == 1)
                {
                    this._setSwitched(false);
                }
                                
                // Only increment if the state changed
                if (this.switched != stateAtLastActuation)
                {
                    this.stateAtLastActuation = !stateAtLastActuation;
                    this.numActuations += 1;
                }
                
                this.updateTiles();
                                                
                this.network.log("Setting " + this.getName() + " " +
                    (this.isSignal() ? 
                       (this.isSwitched() ? "red" : "green")
                       :
                       (this.isSwitched() ? "turn" : "straight")
                       )
                    );
            }
        }
    }
    
    @Override
    synchronized public Accessory setSwitched(boolean state)
    {
        this._setSwitched(state);
        
        this.updateTiles();

        if (this.network.getGUI() != null)
        {
            // Dirty workaround to update UI state
            this.network.getGUI().repaintSwitch(this.getAddress() + 1);
            
            // Not necessary because we can update a single keyboard button at a time above
            // this.network.getGUI().repaintSwitches();
        }
        
        this.network.exec(new CS2Message(
            CS2Message.CMD_ACC_SWITCH,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID,
              (byte) (this.switched ? 0 : 1), // The state of the accessory
              1                               // 1 means power on
            }
        ));
                
        return this;
    }

    @Override
    synchronized public Accessory syncFromState()
    {
        return this.setSwitched(this.isSwitched());        
    }

    @Override
    public Accessory syncFromNetwork()
    {
        // Not supported by the protocol
        return this;
    }
    
    /**
     * Gets the raw accessory address
     * @return 
     */
    public int getAddress()
    {
        return this.address;
    }
    
    /**
     * Gets the accessory's UID
     * @return 
     */
    public int getUID()
    {
        return UID;
    }
    
    @Override
    public String toString()
    {        
        return super.toString() + "\n" +
            "UID: " + Conversion.intToHex(this.UID) + "\n" +
            "Address: " + Conversion.intToHex(this.address);
    }
}
