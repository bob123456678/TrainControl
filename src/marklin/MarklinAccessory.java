package marklin;

import base.Accessory;
import base.RemoteDevice;
import gui.LayoutLabel;
import java.util.ArrayList;
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
    private LayoutLabel tile;
    
    /**
     * Constructor
     * @param network
     * @param address
     * @param type
     * @param name
     * @param state
     */
    public MarklinAccessory(MarklinControlStation network, int address, accessoryType type, String name, boolean state)
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
    }
    
    public static int UIDfromAddress(int address)
    {
        return address + 0x3000;
    }
    
    /**
     * Adds a UI tile to be updated whenever a CS2 event fires
     * @param l 
     * @param dynamic set to true if the component is being added to a popup window
     */
    public void addTile(LayoutLabel l, boolean dynamic)
    {   
        if (dynamic)
        {
            this.tiles.add(l);
        }
        else
        {
            this.tile = l;
        }
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
        
        if (this.tile != null)
        {
            this.tile.updateImage();
        }
    }
    
    @Override
    public void parseMessage(CS2Message m)
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
                
                this.updateTiles();
                                
                this.network.log("Setting " + this.getName() + " " +
                     (this.isSignal() ? 
                        (this.isSwitched() ? "red" : "green")
                        :
                        (this.isSwitched() ? "sw" : "str")
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
        this.network.getGUI().repaintSwitches(); // dirty workaround
        
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
