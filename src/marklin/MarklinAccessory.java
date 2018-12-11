package marklin;

import base.Accessory;
import base.RemoteDevice;
import gui.LayoutLabel;
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
    private int UID;
    
    // Raw address
    private int address;
    
    // Network reference
    private MarklinControlStation network;
    
    // Gui reference
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
    }
    
    public static int UIDfromAddress(int address)
    {
        return address + 0x3000;
    }
    
    public void setTile(LayoutLabel l)
    {
        this.tile = l;
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
                
                if (this.tile != null)
                {
                    this.tile.updateImage();
                }
                
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
    public Accessory setSwitched(boolean state)
    {
        this._setSwitched(state);
        
        if (this.tile != null)
        {
            this.tile.updateImage();
            this.network.getGUI().repaintSwitches(); // dirty workaround
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
    public Accessory syncFromState()
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
