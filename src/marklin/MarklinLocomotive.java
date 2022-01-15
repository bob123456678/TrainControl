package marklin;

import base.Locomotive;
import base.RemoteDevice;
import marklin.udp.CS2Message;
import util.Conversion;

/**
 * Marklin locomotive that implements CS2 interfaces/protocols
 * @author Adam
 */
public class MarklinLocomotive extends Locomotive
    implements java.io.Serializable, RemoteDevice<Locomotive, CS2Message>
{        
    public static enum decoderType {MFX, MM2};
    
    /* Constants */
    
    public static int MFX_NUM_FN = 33;
    public static int MM2_NUM_FN = 5;
    public static int MM2_MAX_ADDR = 80;
    public static int MFX_MAX_ADDR = 0x3FFF;
    
    public static int PULSE_FUNCTION_DURATION = 300;
    
    // Function icon colors
    public static final String[] COLOR_YELLOW = {"i_gr", "a_ge"};
    public static final String[] COLOR_WHITE = {"i_we", "a_we"};
    
    // The raw locomotive address
    private final int address;
    
    // Calculated UID
    private final int UID;
    
    // The locomotive's decoder
    private final decoderType type;
    
    // Reference to the network
    private final MarklinControlStation network;    
            
    /**
     * Constructor with name, type, and address
     * @param network
     * @param address
     * @param type
     * @param name
     */
    public MarklinLocomotive(MarklinControlStation network, int address, 
        decoderType type, String name)
    {        
        super(name, getMaxNumF(type));

        this.network = network;
        this.type = type;
        this.address = address;
        this.UID = calculateUID();
    }
    
    /**
     * Constructor with name, type, address, and function types
     * @param network
     * @param address
     * @param type
     * @param name
     * @param functionTypes
     */
    public MarklinLocomotive(MarklinControlStation network, int address, 
        decoderType type, String name, int[] functionTypes)
    {        
        super(name, getMaxNumF(type), functionTypes);

        this.network = network;
        this.type = type;
        this.address = address;
        this.UID = calculateUID();
        
        assert this.functionTypes.length == getMaxNumF(type);
    }
    
    /**
     * Constructor with full state
     * @param network
     * @param address
     * @param type
     * @param dir
     * @param name
     * @param functions
     * @param functionTypes
     */
    public MarklinLocomotive(MarklinControlStation network, int address, 
        decoderType type, String name, Locomotive.locDirection dir, boolean[] functions, int[] functionTypes)
    {
        super(name, 0, dir, functions, functionTypes);

        this.network = network;
        this.type = type;
        this.address = address;
        this.UID = calculateUID();
        
        assert this.functionTypes.length == getMaxNumF(type);
        assert functions.length == getMaxNumF(type);
    }
    
    /**
     * Determines the Marklin UID based on address and protocol
     */
    private int calculateUID()
    {
        // Verify MM2 address range
        if (this.type == decoderType.MM2)
        {
            assert this.address <= 80;
            
            return this.address;
        } 
        // Verify MFX address range
        else if (this.type == decoderType.MFX)
        {
            assert this.address <= 0x3FFF;

            return this.address + 0x4000;            
        }  
        
        return 0;
    }
    
    /**
     * Returns the image URL for a function icon, if any
     * @param fType
     * @param active
     * @param yellow
     * @return 
     */
    public String getFunctionIconUrl(int fType, boolean active, boolean yellow)
    {
        int index = active ? 1 : 0;
        String[] color = yellow ? COLOR_YELLOW : COLOR_WHITE;
        
        // > 128 just means it's a pulse function
        if (fType > 112)
        {
            fType = fType % 128;
        }
        
        return "http://" + this.network.getIP() + "/fcticons/FktIcon_" + color[index] + "_" + (fType < 10 ? "0" : "") + Integer.toString(fType) + ".png";
    }
    
    /**
     * Checks if the given function is supposed to only stay on for a short period
     * @param fNo
     * @return 
     */
    public boolean isFunctionPulse(int fNo)
    {
        return this.getFunctionType(fNo) > 128;
    }
    
    /**
     * Gets the maximum number of functions for a given decoder type
     * @param decoder
     * @return 
     */
    public static int getMaxNumF(decoderType decoder)
    {
        if (decoder == decoderType.MM2)
        {
            return MM2_NUM_FN;
        }
        
        return MFX_NUM_FN;
    }
    
    /**
     * Gets the locomotives UID
     * @return 
     */
    public int getUID()
    {
        return UID;
    }
    
    /**
     * Gets the raw locomotive address
     * @return 
     */
    public int getAddress()
    {
        return this.address;
    }
    
    /**
     * Gets the decoder type
     * @return 
     */
    public decoderType getDecoderType()
    {
        return this.type;
    }
    
    @Override
    public boolean isFeedbackSet(String name)
    {
        return this.network.isFeedbackSet(name);
    }
    
    @Override
    public boolean getAccessoryState(int id)
    {
        return this.network.getAccessoryState(id);
    }

    @Override
    public boolean getFeedbackState(String name)
    {
        return this.network.getFeedbackState(name);
    }
    
    @Override
    synchronized public void parseMessage(CS2Message m)
    {
        // Double-check the UID just in case        
        if (m.extractUID() != UID)
        {
            return;
        }
                      
        if (m.getCommand().equals(CS2Message.CMD_LOCO_DIRECTION))
        {
            if (m.getLength() == 5)
            {
                int direction = CS2Message.mergeBytes(
                    new byte[] {m.getData()[4]}
                );
                                
                if (direction == 1)
                {
                    this._setDirection(locDirection.DIR_FORWARD);
                }
                else if (direction == 2)
                {
                    this._setDirection(locDirection.DIR_BACKWARD);
                }
                
                this.network.log("Setting " + this.getName() 
                    + " loc direction " + (this.goingForward() ? "f" : "b"));
            }
        }
        else if (m.getCommand().equals(CS2Message.CMD_LOCO_FUNCTION))
        {
            if (m.getLength() == 6)
            {
                int fNumber = m.getData()[4];
                boolean fValue = m.getData()[5] != 0;
                
                this._setF(fNumber, fValue);
                
                this.network.log("Setting " + this.getName() 
                    + " loc f" + fNumber + " " + (fValue ? "1" : "0"));
            }
        }
        else if (m.getCommand().equals(CS2Message.CMD_LOCO_VELOCITY))
        {
            if (m.getLength() == 6)
            {
                int speed = CS2Message.mergeBytes(
                    new byte[] {m.getData()[4], m.getData()[5]}
                );
                
                speed /= 10;
                
                this._setSpeed(speed);
                
                this.network.log("Setting " + this.getName() 
                    + " loc speed " + speed);
            }
        }
    }
    
    /**
     * Simulates instant stop functionality for MM2 locomotives
     * @return 
     */
    synchronized public Locomotive instantStop()
    {
        if (this.type == MarklinLocomotive.decoderType.MM2)
        {
            return this.switchDirection().switchDirection().stop();
        }
        else
        {
            return stop();
        }
    }
    
    @Override
    synchronized public Locomotive stop()
    {
        //this._setSpeed(0);
        
        // Send stop command
        this.network.exec(new CS2Message(
            CS2Message.CMD_SYSTEM,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID,
              CS2Message.CMD_SYSSUB_TRAINSTOP
            }
        ));
        
        // Added code to guarantee state sync
        if (this.getSpeed() > 0)
        {
            this.setSpeed(0);
        }
        
        return this;
    }
    
    @Override
    synchronized public Locomotive syncFromNetwork()
    {
        // Query speed
        this.network.exec(new CS2Message(
            CS2Message.CMD_LOCO_VELOCITY,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID
            }
        ));
                
        // Query functions
        for (byte i = 0; i < this.getNumF(); i++)
        {
            this.network.exec(new CS2Message(
                CS2Message.CMD_LOCO_FUNCTION,
                new byte[]
                {
                  (byte) (UID >> 24), 
                  (byte) (UID >> 16), 
                  (byte) (UID >> 8), 
                  (byte) UID,
                  i
                }
            ));
        }
                
        // Query direction
        this.network.exec(new CS2Message(
            CS2Message.CMD_LOCO_DIRECTION,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID
            }
        ));
        
        return this;
    }

    @Override
    synchronized public Locomotive syncFromState()
    {
        // Send out speed command
        this.setSpeed(this.getSpeed());
        
        // Send out function command
        for (int i = 0; i < this.getNumF(); i++)
        {
            this.setF(i, this.getF(i));
        }
        
        // Send out direction command
        this.setDirection(this.getDirection());
        
        return this;
    }
    
    @Override
    synchronized public Locomotive setSpeed(int speed)
    {
        super._setSpeed(speed);
        
        int newSpeed = this.getSpeed() * 10;
     
        this.network.exec(new CS2Message(
            CS2Message.CMD_LOCO_VELOCITY,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID,
              (byte) (newSpeed >> 8),
              (byte) newSpeed
            }
        ));	
        
        return this;
    }

    @Override
    synchronized public Locomotive setDirection(locDirection direction)
    {
        super._setDirection(direction);
        
        int newDirection = (direction == locDirection.DIR_FORWARD ? 1 : 2);
        
        this.network.exec(new CS2Message(
            CS2Message.CMD_LOCO_DIRECTION,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID,
              (byte) newDirection
            }
        ));
        
        return this;
    }
    
    @Override
    synchronized public Locomotive setF(int fNumber, boolean state)
    {
        if (this.validF(fNumber))
        {
            super._setF(fNumber, state);
        
            this.network.exec(new CS2Message(
                CS2Message.CMD_LOCO_FUNCTION,
                new byte[]
                {
                  (byte) (UID >> 24), 
                  (byte) (UID >> 16), 
                  (byte) (UID >> 8), 
                  (byte) UID,
                  (byte) fNumber,
                  (byte) (state ? 1 : 0)
                }
            ));
        }
        
        return this;        
    }
    
    @Override
    synchronized public Locomotive setAccessoryState(int id, boolean state)
    {
        this.network.setAccessoryState(id, state);
        
        return this;
    }
    
    /**
     * Turns a function on for the default pulse function duration, then back off
     * @param f
     * @return 
     */
    @Override
    public Locomotive toggleF(int f)
    {
        return this.toggleF(f, PULSE_FUNCTION_DURATION);
    }
    
    /**
     * Prints a human-readable address (>= 1) based on a raw UID
     * @param UID
     * @return 
     */
    public static String addressFromUID(int UID)
    {
        if (UID > 0x4000)
        {
            return Conversion.intToHex(UID - 0x4000);
        }
        
        return Integer.toString(UID);
    }
    
    @Override
    public String toString()
    {        
        return super.toString() + "\n" +
            "UID: " + Conversion.intToHex(this.UID) + "\n" +
            "Address: " + Conversion.intToHex(this.address) + "\n" +
            "Type: " + (this.type == decoderType.MFX ? "MFX" : "MM2");                
    }
}
