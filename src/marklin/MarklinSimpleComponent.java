package marklin;

import java.util.Map;

/**
 * Serializable class for saving state
 * @author Adam
 */
public class MarklinSimpleComponent implements java.io.Serializable
{
    public enum Type {LOC_MFX, LOC_MM2, SWITCH, SIGNAL, ROUTE, FEEDBACK};
    
    private String name;
    private int address;
    private Type type;
    
    // Switch state or loc directions
    private boolean state;
    private boolean[] functions;
    
    // Route state
    private Map<Integer, Boolean> route;
    
    public MarklinSimpleComponent(MarklinAccessory a)
    {
        if (a.getType() == MarklinAccessory.accessoryType.SIGNAL)
        {
            this.type = Type.SIGNAL;
        }
        else
        {
            this.type = Type.SWITCH;
        }
        
        this.name = a.getName();
        this.address = a.getAddress();
        this.state = a.isSwitched();
    }
    
    public MarklinSimpleComponent(MarklinFeedback a)
    {
        this.type = Type.FEEDBACK;
        
        this.name = a.getName();
        this.address = a.getUID();
        this.state = a.isSet();
    }
    
    public MarklinSimpleComponent(MarklinRoute r)
    {
        this.name = r.getName();
        this.type = Type.ROUTE;
        
        this.route = r.getRoute();
    }
    
    public MarklinSimpleComponent(MarklinLocomotive l)
    {
        if (l.getDecoderType() == MarklinLocomotive.decoderType.MFX)
        {
            this.type = Type.LOC_MFX;
        }
        else
        {
            this.type = Type.LOC_MM2;
        }
        
        this.name = l.getName();
        this.address = l.getAddress();
        this.state = l.goingForward() ? true : false;
        this.functions = l.getFunctionState();
    }
    
    public Map<Integer, Boolean> getRoute()
    {
        return this.route;
    }
    
    public boolean[] getFunctions()
    {
        return functions;
    }
    
    public boolean getState()
    {
        return state;
    }

    public String getName()
    {
        return name;
    }

    public int getAddress()
    {
        return address;
    }

    public Type getType()
    {
        return type;
    }
}
